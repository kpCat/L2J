/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.holders.AccessLevel;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Exercises the production monitor boundaries without opening JDBC or starting a game server. */
public final class PhantomMerchantStoreOrderChecks
{
	private static final int OWNER_ID = 268490001;
	private static final String ACCOUNT = "merchantlockfixture";
	private static final long NOW = 1000;
	private static final StoreObserved STORE_OBSERVED = new StoreObserved();

	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || DatabaseFactory.isInitialized()) { throw new IllegalArgumentException("Exact read-only datapack and isolated JVM required."); }
		ServerConfig.DATAPACK_ROOT = Path.of(args[0]).toFile();
		PlayerConfig.load();
		final var scheduler = new ScheduledThreadPoolExecutor(1)
		{
			@Override public java.util.concurrent.ScheduledFuture<?> schedule(Runnable work, long delay, TimeUnit unit)
			{ return super.schedule(work, 1, TimeUnit.DAYS); }
			@Override public java.util.concurrent.ScheduledFuture<?> scheduleAtFixedRate(Runnable work, long delay, long period, TimeUnit unit)
			{ return super.scheduleAtFixedRate(work, 1, 1, TimeUnit.DAYS); }
		};
		final var pool = field(ThreadPool.class, "SCHEDULED_POOL");
		pool.set(null, scheduler);
		try
		{
			int failures = 0;
			for (String mode : List.of("pulse", "shutdown", "trade"))
			{
				try
				{
					verify(mode, true);
					if (mode.equals("trade")) { verify(mode, false); }
					System.out.println("PASS merchant-store-order." + mode);
				}
				catch (AssertionError failure) { failures++; System.out.println("FAIL merchant-store-order." + mode + " " + failure.getMessage()); }
			}
			check(failures == 0 && !DatabaseFactory.isInitialized(), "Merchant lock regression failed or reached real JDBC.");
			check(ManagementFactory.getThreadMXBean().findDeadlockedThreads() == null, "A monitor cycle survived the probe.");
			System.out.println("MERCHANT_STORE_ORDER_PASS cases=3 JDBC=0");
		}
		finally
		{
			scheduler.shutdownNow();
			pool.set(null, null);
		}
	}

	private static void verify(String mode, boolean dedicated) throws Exception
	{
		final Player player = player(OWNER_ID);
		final Player customer = mode.equals("trade") ? player(OWNER_ID + 1) : null;
		final var registry = PhantomIdentityLeaseRegistry.getInstance();
		final var merchant = fixture(player);
		if (!dedicated) { map(PhantomOfflineMerchantService.class, null, "NATIVE_OWNERS").remove(OWNER_ID); }
		final var saveEntered = new CountDownLatch(1);
		final var readRecipe = new CountDownLatch(1);
		final var saves = new AtomicInteger();
		final var tradeLocked = new AtomicBoolean();
		final var readManufacture = new AtomicBoolean();
		try (var owner = registry.tryAcquire(OWNER_ID, OwnerKind.PHANTOM))
		{
			check(owner != null, "Exact fixture lease unavailable.");
			World.getInstance().addObject(player);
			if (customer != null)
			{
				field(Player.class, "_accessLevel").set(customer, new AccessLevel());
				player.setPrivateStoreType(PrivateStoreType.SELL);
			}
			try (var boundary = player.attachOwnedStoreBoundary(new Player.OwnedStoreBoundary()
			{
				@Override public Player.OwnedStoreSnapshot beforeStore()
				{
					check(Thread.holdsLock(player), "Native STORE lost its Player monitor.");
					if (saves.getAndIncrement() == 0) { saveEntered.countDown(); await(readRecipe); }
					if (readManufacture.get())
					{
						synchronized (player.getManufactureItems()) { check(player.getManufactureItems().isEmpty(), "Fixture manufacture list was modified."); }
					}
					// The probe stops at the real synchronized STORE boundary before the SQL writers.
					throw STORE_OBSERVED;
				}
				@Override public void afterStore(boolean complete) { throw new AssertionError("Probe reached a SQL writer."); }
			}); var observer = PrivateStoreService.getInstance().observeOwner(OWNER_ID, new PrivateStoreService.OwnerObserver()
			{
				@Override public boolean beforeTransaction(Player target, org.l2jmobius.gameserver.network.holders.TradeList list)
				{
					tradeLocked.set(Thread.holdsLock(target) && Thread.holdsLock(list));
					return false;
				}
				@Override public void afterTransaction(Player target, org.l2jmobius.gameserver.network.holders.TradeList list, boolean success)
				{ throw new AssertionError("A rejected merchant transaction reached the success callback."); }
			}))
			{
				final var save = new Worker("native-store", () -> probeStore(player::storeMe));
				await(saveEntered);
				final Worker competing;
				final Worker shutdown;
				final boolean playerFirst;
				try
				{
					final Object listing = mode.equals("trade") ? player.getSellList() : player.getManufactureItems();
					synchronized (listing)
					{
						competing = new Worker(mode, switch (mode)
						{
							case "pulse" -> () -> merchant.pulse(NOW);
							case "shutdown" -> () -> probeStore(merchant::beginStop);
							default -> () -> check(PrivateStoreService.getInstance().buy(customer, OWNER_ID, Set.of()) == PrivateStoreService.Result.REJECTED, "Trading rejection was bypassed.");
						});
						playerFirst = competing.blockedOn() == System.identityHashCode(player);
						shutdown = mode.equals("pulse") ? new Worker("shutdown-during-pulse", () -> probeStore(merchant::beginStop)) : null;
						if (shutdown != null)
						{
							check(shutdown.blockedOn() == System.identityHashCode(merchant), "Shutdown bypassed the in-flight pulse.");
						}
						if (playerFirst) { readManufacture.set(true); }
						else
						{
							// The reversed monitor was witnessed. Let STORE release Player before the
							// old operation enters the list, so RED cannot leave blocked test workers.
							readRecipe.countDown();
							save.finish();
						}
					}
				}
				finally { readRecipe.countDown(); }
				save.finish();
				competing.finish();
				if (shutdown != null) { shutdown.finish(); }
				if (!playerFirst)
				{
					throw new AssertionError(mode + " (dedicated=" + dedicated + ") waited for the listing before acquiring the Player used by native STORE.");
				}
				if (mode.equals("trade")) { check(tradeLocked.get(), "Trade admission did not retain Player and native list locks."); }
				if (!mode.equals("trade"))
				{
					check(saves.get() == 2, "Shutdown did not reach the same serialized native STORE boundary.");
					merchant.pulse(NOW + 60_000);
					check(saves.get() == 2 && merchant.characterIds().equals(List.of(OWNER_ID)), "Shutdown reopened work or released the merchant owner.");
				}
			}
		}
		finally
		{
			map(PhantomOfflineMerchantService.class, null, "NATIVE_OWNERS").remove(OWNER_ID);
			World.getInstance().removeObject(player);
			player.stopAllTasks();
			if (customer != null) { customer.stopAllTasks(); }
		}
	}

	private static PhantomOfflineMerchantService fixture(Player player) throws Exception
	{
		final var repository = PhantomProfileRepository.class.getDeclaredConstructor();
		repository.setAccessible(true);
		final var policy = new PhantomMerchantPolicy(1, 1, 1, 1, 15, 1, 0, 0, 0, 1, 1, 0, 0, List.of(0), List.of(new PhantomMerchantPolicy.Site(0, 0, 0, 1, 1, 60)), List.of());
		final var service = new PhantomOfflineMerchantService(repository.newInstance(), policy, () -> { throw new AssertionError("Unexpected pricing in lock probe."); }, List.of());
		final var type = PhantomOfflineMerchantService.class;
		final var role = new PhantomMerchantBootstrap.Role(0, OWNER_ID, ACCOUNT, "A".repeat(43), "LockFixture", 0, 0, 0, 0, 0, 0, Map.of(), PhantomMerchantBootstrap.Stage.READY);
		field(type, "_server").set(service, new PhantomProfileComponent(1, PhantomMerchantBootstrap.COMPONENT, 1, 1, new byte[] { 1 }, Instant.EPOCH, Instant.EPOCH));
		map(type, service, "_roles").put(0, nested("SavedRole", PhantomMerchantBootstrap.Role.class, long.class).newInstance(role, 1L));
		map(type, service, "_owners").put(0, player);
		map(type, null, "NATIVE_OWNERS").put(OWNER_ID, nested("NativeOwner", Player.class, type).newInstance(player, service));
		final var schedule = type.getDeclaredMethod("schedule", int.class, long.class);
		schedule.setAccessible(true);
		schedule.invoke(service, 0, NOW);
		return service;
	}

	private static Player player(int id) throws Exception
	{
		final var constructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class);
		constructor.setAccessible(true);
		final var player = constructor.newInstance(id, PlayerTemplateData.getInstance().getTemplate(0), ACCOUNT, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		player.getManufactureItems();
		return player;
	}

	private static Field field(Class<?> type, String name) throws Exception
	{
		final var field = type.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	@SuppressWarnings("unchecked")
	private static Map<Integer, Object> map(Class<?> type, Object subject, String name) throws Exception
	{
		return (Map<Integer, Object>) field(type, name).get(subject);
	}

	private static java.lang.reflect.Constructor<?> nested(String name, Class<?>... parameters) throws Exception
	{
		final var constructor = Class.forName(PhantomOfflineMerchantService.class.getName() + "$" + name).getDeclaredConstructor(parameters);
		constructor.setAccessible(true);
		return constructor;
	}

	private static void probeStore(Runnable action)
	{
		try { action.run(); }
		catch (StoreObserved seen) { if (seen == STORE_OBSERVED) { return; } throw seen; }
		throw new AssertionError("Native STORE boundary was skipped.");
	}

	private static void await(CountDownLatch latch)
	{
		try { check(latch.await(5, TimeUnit.SECONDS), "Timed out awaiting the native STORE owner."); }
		catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
	}

	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
	private static final class StoreObserved extends RuntimeException { }

	private static final class Worker
	{
		private final FutureTask<Void> task;
		private final Thread thread;
		private Worker(String name, Runnable work)
		{
			task = new FutureTask<>(work, null);
			thread = Thread.ofPlatform().name(name).daemon().start(task);
		}
		private int blockedOn() throws Exception
		{
			final var threads = ManagementFactory.getThreadMXBean();
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
			do
			{
				final var info = threads.getThreadInfo(thread.threadId());
				if (info != null && info.getThreadState() == Thread.State.BLOCKED) { return info.getLockInfo().getIdentityHashCode(); }
				if (task.isDone()) { finish(); throw new AssertionError("Expected a contended monitor in " + thread.getName()); }
				Thread.sleep(1);
			}
			while (System.nanoTime() < deadline);
			throw new AssertionError("No bounded monitor witness in " + thread.getName());
		}
		private void finish() throws Exception { task.get(5, TimeUnit.SECONDS); }
	}
}

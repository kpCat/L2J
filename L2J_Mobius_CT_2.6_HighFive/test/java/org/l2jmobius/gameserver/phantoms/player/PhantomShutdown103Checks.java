package org.l2jmobius.gameserver.phantoms.player;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomOrdinaryDeathRecovery;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Real dispatch owner with DB-free partial actors; callback models the queued earned logout body. */
public final class PhantomShutdown103Checks
{
	private PhantomShutdown103Checks() { }

	public static void main(String[] args) throws Exception
	{
		final Field instant = field(ThreadPool.class, "INSTANT_POOL");
		final Object previous = instant.get(null);
		final var executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
		instant.set(null, executor);
		try
		{
			if (args.length == 1 && args[0].equals("--stop121")) { recoveryFailureDrainsOtherActors(false); return; }
			if (args.length == 1 && args[0].equals("--stop012")) { recoveryFailureDrainsOtherActors(true); return; }
			if (args.length == 1 && args[0].equals("--materialization-retry012")) { materializationDeadlineCanRecover(false); return; }
			if (args.length == 1 && args[0].equals("--materialization-service-retry012")) { materializationDeadlineCanRecover(true); return; }
			queuedCallback(executor);
			expiredDeadline(executor);
			concurrentClose(executor);
			System.out.println("SHUTDOWN103_DISPATCH_PASS 3/3;actualService=true;modelCallback=true;JDBC=0;World=0");
		}
		finally
		{
			executor.shutdown();
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) { throw new AssertionError("TEST cleanup did not return its workers"); }
			instant.set(null, previous);
		}
	}

	private static void queuedCallback(ThreadPoolExecutor executor) throws Exception
	{
		final var fixture = fixture();
		final var callback = new CountDownLatch(1);
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		synchronized (fixture.actors().get(0))
		{
			synchronized (fixture.actors().get(1))
			{
				fixture.service().requestShutdown(deadline);
				ThreadPool.executeOrThrow(callback::countDown);
				if (!callback.await(500, TimeUnit.MILLISECONDS))
				{
					throw new AssertionError("RED: cleanup occupied both instant workers; queued earned callback cannot enter; active=" + executor.getActiveCount() + ";queue=" + executor.getQueue().size());
				}
				for (var actor : fixture.actors())
				{
					if ((boolean) field(PhantomMaterializedPlayer.class, "_actionAdmissionOpen").get(actor)) { throw new AssertionError("STOP left another actor admission open"); }
				}
				// A normal request racing STOP must share the same admission, never add another waiter.
				fixture.service().requestDematerialize(2, deadline);
				if (!executor.getQueue().isEmpty()) { throw new AssertionError("Normal cleanup bypassed the occupied dispatch slot"); }
			}
		}
		while (fixture.service().requestShutdown(deadline).state() == PhantomMaterializationService.ServiceState.STOPPING && System.nanoTime() < deadline) { Thread.sleep(5); }
		if (fixture.service().requestShutdown(deadline).state() != PhantomMaterializationService.ServiceState.STOPPED) { throw new AssertionError("Chained cleanup did not finish on its original deadline"); }
	}

	private static void expiredDeadline(ThreadPoolExecutor executor) throws Exception
	{
		final var fixture = fixture();
		final long submissions = executor.getTaskCount();
		synchronized (fixture.actors().getFirst())
		{
			final var result = fixture.service().requestShutdown(System.nanoTime() - 1);
			if (result.state() != PhantomMaterializationService.ServiceState.FAILED || executor.getTaskCount() != submissions) { throw new AssertionError("Expired STOP published new cleanup instead of retaining owners"); }
			for (var actor : fixture.actors()) { if ((boolean) field(PhantomMaterializedPlayer.class, "_actionAdmissionOpen").get(actor)) { throw new AssertionError("Expired STOP left another actor admission open"); } }
		}
	}

	private static void concurrentClose(ThreadPoolExecutor executor) throws Exception
	{
		final var fixture = fixture();
		// Exercise the existing legacy caller which can already have entered STOPPING.
		field(PhantomMaterializationService.class, "_state").set(fixture.service(), PhantomMaterializationService.ServiceState.STOPPING);
		final Object monitor = field(PhantomMaterializedPlayer.class, "_actionMonitor").get(fixture.actors().getLast());
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		final var first = new Thread(() -> { try { fixture.service().requestShutdown(deadline); } catch (Throwable thrown) { failure.set(thrown); } });
		synchronized (monitor)
		{
			first.start();
			while (first.getState() != Thread.State.BLOCKED && first.isAlive() && System.nanoTime() < deadline) { Thread.sleep(1); }
			if (first.getState() != Thread.State.BLOCKED) { throw new AssertionError("Fixture did not hold initial admission close"); }
			final long submissions = executor.getTaskCount();
			if (fixture.service().requestShutdown(deadline).state() != PhantomMaterializationService.ServiceState.STOPPING || executor.getTaskCount() != submissions) { throw new AssertionError("Concurrent pulse dispatched before all admissions closed"); }
		}
		first.join(3000);
		if (first.isAlive() || failure.get() != null) { throw new AssertionError("Initial close failed", failure.get()); }
		while (fixture.service().requestShutdown(deadline).state() == PhantomMaterializationService.ServiceState.STOPPING && System.nanoTime() < deadline) { Thread.sleep(5); }
		if (fixture.service().requestShutdown(deadline).state() != PhantomMaterializationService.ServiceState.STOPPED) { throw new AssertionError("Existing STOPPING caller lost its finite deadline"); }
	}

	private static void materializationDeadlineCanRecover(boolean serviceDeadlineFirst) throws Exception
	{
		final var fixture = fixture(true);
		final var configure = PhantomSystem.class.getDeclaredMethod("configureOperatorRuntimeForTesting", boolean.class);
		configure.setAccessible(true);
		configure.invoke(null, false);
		final var configured = (PhantomSystem) field(PhantomSystem.class, "_configuredInstance").get(null);
		field(PhantomSystem.class, "_materializationService").set(configured, fixture.service());
		final Field scheduled = field(ThreadPool.class, "SCHEDULED_POOL");
		final Object previous = scheduled.get(null);
		final var executor = new ScheduledThreadPoolExecutor(1);
		scheduled.set(null, executor);
		try
		{
			if (serviceDeadlineFirst)
			{
				fixture.service().requestShutdown(System.nanoTime() - 1);
			}
			if (configured.shutdown() || configured.shutdownProgress().phase() != PhantomSystem.StopPhase.DRAIN_PLAYERS)
			{
				throw new AssertionError("Expected STOP to reach the materialization drain");
			}
			final var progress = configured.shutdownProgress();
			if (serviceDeadlineFirst)
			{
				if (progress.outcome() != PhantomSystem.StopOutcome.FAILED || !progress.blocker().startsWith("materialization:"))
				{
					throw new AssertionError("STOP did not observe the failed materialization drain");
				}
			}
			else
			{
				field(PhantomSystem.class, "_stopProgress").set(configured, new PhantomSystem.StopProgress(progress.phase(), progress.outcome(), progress.blocker(), System.nanoTime() - 1));
				if (configured.shutdown() || configured.shutdownProgress().outcome() != PhantomSystem.StopOutcome.FAILED)
				{
					throw new AssertionError("An expired STOP must report failure");
				}
			}
			if (PhantomSystem.shutdownIfStarted() || !PhantomSystem.hasConfiguredInstance())
			{
				throw new AssertionError("STOP bypassed incomplete materialization");
			}
			fixture.firstMaterialization().countDown();
			final boolean stopped = PhantomSystem.shutdownIfStarted();
			if (fixture.service().snapshot().retainedEntries() != 0 || configured.snapshot().state() != PhantomSystem.State.STOPPED)
			{
				throw new AssertionError("Stock cleanup did not release both actors after materialization completed");
			}
			if (!stopped || PhantomSystem.hasConfiguredInstance() || configured.shutdownProgress().outcome() != PhantomSystem.StopOutcome.COMPLETE)
			{
				throw new AssertionError("STOP012_MATERIALIZATION_RELEASE_UNCONFIRMED");
			}
			System.out.println("STOP012_MATERIALIZATION_RETRY_PASS DB0 World0");
		}
		finally
		{
			fixture.firstMaterialization().countDown();
			executor.shutdown();
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) { throw new AssertionError("TEST scheduled cleanup did not return its worker"); }
			scheduled.set(null, previous);
		}
	}

	private record Fixture(PhantomMaterializationService service, List<PhantomMaterializedPlayer> actors, CountDownLatch firstMaterialization) { }

	@SuppressWarnings("unchecked")
	private static void recoveryFailureDrainsOtherActors(boolean backgroundIncident) throws Exception
	{
		if (DatabaseFactory.isInitialized() || PhantomSystem.hasConfiguredInstance()) { throw new AssertionError("STOP121 requires DB-free isolated runtime"); }
		final var fixture = fixture();
		final var constructor = PhantomProfileRepository.class.getDeclaredConstructor(); constructor.setAccessible(true);
		final var repository = constructor.newInstance();
		final var goals = new PhantomGoalStateStore(repository);
		final var transactions = new PhantomBackgroundTransaction(() -> { throw new AssertionError("STOP121 must not open SQL"); }, new PhantomBackgroundTransaction.ObjectIdAllocator()
		{
			@Override public int reserve() { throw new AssertionError("STOP121 must not create identity"); }
			@Override public void release(int id) { throw new AssertionError("STOP121 must not release fixture identity"); }
		}, PhantomBackgroundTransaction.FaultInjector.none());
		final var authority = (PhantomBackgroundAuthority) java.lang.reflect.Proxy.newProxyInstance(PhantomBackgroundAuthority.class.getClassLoader(), new Class<?>[] { PhantomBackgroundAuthority.class }, (_, method, arguments) -> { throw new AssertionError("STOP121 unexpected authority: " + method.getName()); });
		final var signals = (PhantomRelevanceSignalPort) java.lang.reflect.Proxy.newProxyInstance(PhantomRelevanceSignalPort.class.getClassLoader(), new Class<?>[] { PhantomRelevanceSignalPort.class }, (_, method, arguments) -> { throw new AssertionError("STOP121 unexpected signal: " + method.getName()); });
		final var background = new PhantomBackgroundService(repository, goals, PhantomIdentityLeaseRegistry.getInstance(), transactions, authority, new PhantomBackgroundCompetitionRegistry(), signals, fixture::service);
		final var death = new PhantomOrdinaryDeathRecovery(fixture.service(), goals, background);
		final var failure = (AtomicReference<String>) field(PhantomOrdinaryDeathRecovery.class, "_firstFailure").get(death);
		final var accepted = (AtomicInteger) field(PhantomOrdinaryDeathRecovery.class, "_acceptedReconciliations").get(death);
		final var operations = (AtomicInteger) field(PhantomBackgroundService.class, "_currentOperations").get(background);
		final var configure = PhantomSystem.class.getDeclaredMethod("configureOperatorRuntimeForTesting", boolean.class); configure.setAccessible(true); configure.invoke(null, false);
		final var configured = (PhantomSystem) field(PhantomSystem.class, "_configuredInstance").get(null);
		field(PhantomSystem.class, "_materializationService").set(configured, fixture.service());
		field(PhantomSystem.class, "_backgroundService").set(configured, background);
		field(PhantomSystem.class, "_ordinaryDeathRecovery").set(configured, death);
		accepted.set(1); operations.set(1);
		final long originalDeadline = System.nanoTime() - 1;
		if (backgroundIncident) { recordRecoveryDeadline(background, operations, originalDeadline); }
		else { failure.set("death.control_terminal:TEST121"); }
		try
		{
			configured.shutdown();
			if (configured.snapshot().state() != PhantomSystem.State.STOPPING || fixture.service().snapshot().state() != PhantomMaterializationService.ServiceState.RUNNING) { throw new AssertionError("STOP121_TERMINAL_FAILURE_CUT_OFF_ACCEPTED_CONTROL"); }
			accepted.set(0); configured.shutdown();
			if (configured.snapshot().state() != PhantomSystem.State.STOPPING || fixture.service().snapshot().state() != PhantomMaterializationService.ServiceState.RUNNING) { throw new AssertionError("STOP121_DRAIN_BYPASSED_BACKGROUND_QUIESCENCE"); }
			if (backgroundIncident)
			{
				final var pending = configured.shutdownProgress();
				field(PhantomSystem.class, "_stopProgress").set(configured, new PhantomSystem.StopProgress(pending.phase(), pending.outcome(), pending.blocker(), System.nanoTime() - 1));
				if (configured.shutdown() || configured.snapshot().state() != PhantomSystem.State.FAILED) { throw new AssertionError("STOP012_EXPIRED_WAIT_SUCCEEDED"); }
			}
			operations.set(0);
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			do { configured.shutdown(); Thread.sleep(1); }
			while (configured.snapshot().state() != PhantomSystem.State.FAILED && System.nanoTime() < deadline);
			if (configured.snapshot().state() != PhantomSystem.State.FAILED || fixture.service().snapshot().state() != PhantomMaterializationService.ServiceState.STOPPED || !fixture.service().snapshot().materializations().isEmpty()) { throw new AssertionError("STOP121_OTHER_ACTORS_RETAINED_BEFORE_DEATH_FAILURE"); }
			for (var actor : fixture.actors()) { if ((boolean) field(PhantomMaterializedPlayer.class, "_actionAdmissionOpen").get(actor)) { throw new AssertionError("STOP121_OTHER_ACTOR_ADMISSION_OPEN"); } }
			if (PhantomSystem.shutdownIfStarted() || !PhantomSystem.hasConfiguredInstance()) { throw new AssertionError("STOP121_UNRESOLVED_FAILURE_LOST"); }
			if (backgroundIncident)
			{
				if (background.firstRecoveryFailure().isEmpty()) { throw new AssertionError("STOP012_LOST_UNVERIFIED_STORE"); }
				publishStoreWitness(background, 8, originalDeadline - 1);
				if (PhantomSystem.shutdownIfStarted()) { throw new AssertionError("STOP012_WRONG_EPOCH_RELEASED"); }
				publishStoreWitness(background, 7, originalDeadline);
				if (PhantomSystem.shutdownIfStarted()) { throw new AssertionError("STOP012_AFTER_DEADLINE_RELEASED"); }
				publishStoreWitness(background, 7, originalDeadline - 1);
				if (!PhantomSystem.shutdownIfStarted() || PhantomSystem.hasConfiguredInstance()) { throw new AssertionError("STOP012_VERIFIED_STORE_REMAINED_STICKY"); }
			}
			System.out.println("STOP_RECOVERY_DRAIN_PASS DB0 World0 witnessModel=" + backgroundIncident);
		}
		finally
		{
			accepted.set(0); operations.set(0);
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
			while (fixture.service().requestShutdown(deadline).state() == PhantomMaterializationService.ServiceState.STOPPING && System.nanoTime() < deadline) { Thread.sleep(1); }
			if (DatabaseFactory.isInitialized() || !fixture.service().snapshot().materializations().isEmpty()) { throw new AssertionError("STOP121_FIXTURE_CLEANUP_FAILED"); }
		}
	}

	@SuppressWarnings("unchecked")
	private static void recordRecoveryDeadline(PhantomBackgroundService background, AtomicInteger operations, long deadline) throws Exception
	{
		final var type = Class.forName(PhantomBackgroundService.class.getName() + "$RecoveryControl");
		final var constructor = type.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		final var control = constructor.newInstance(29L, null, null, null, null, deadline, null, true);
		field(type, "objectId").setInt(control, 268486148);
		field(type, "epoch").setLong(control, 7);
		field(type, "storeRequested").setBoolean(control, true);
		((java.util.Map<Long, Object>) field(PhantomBackgroundService.class, "_recoveryControls").get(background)).put(29L, control);
		final var finish = PhantomBackgroundService.class.getDeclaredMethod("finishRecovery", type, PhantomBackgroundService.OperationResult.class);
		finish.setAccessible(true);
		operations.incrementAndGet();
		finish.invoke(background, control, PhantomBackgroundService.OperationResult.inconsistent("recovery.control_deadline:object=268486148:epoch=7"));
	}

	@SuppressWarnings("unchecked")
	private static void publishStoreWitness(PhantomBackgroundService background, long epoch, long storedAt) throws Exception
	{
		final var type = Class.forName(PhantomBackgroundService.class.getName() + "$NativeTownReturn");
		final var constructor = type.getDeclaredConstructors()[0];
		constructor.setAccessible(true);
		final var witness = constructor.newInstance(268486148, epoch, null, storedAt);
		((java.util.Map<Long, Object>) field(PhantomBackgroundService.class, "_nativeTownReturns").get(background)).put(29L, witness);
	}

	private static Fixture fixture() throws Exception
	{
		return fixture(false);
	}

	@SuppressWarnings("unchecked")
	private static Fixture fixture(boolean firstMaterializationPending) throws Exception
	{
		final var repositoryConstructor = PhantomProfileRepository.class.getDeclaredConstructor();
		repositoryConstructor.setAccessible(true);
		final var registry = PhantomIdentityLeaseRegistry.getInstance();
		final var metrics = new PhantomMetrics();
		final var service = new PhantomMaterializationService(repositoryConstructor.newInstance(), registry, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		service.start();
		final var actors = List.of(new PhantomMaterializedPlayer(910103001, registry, new HeadlessPlayerOutboundSession(8, 64)), new PhantomMaterializedPlayer(910103002, registry, new HeadlessPlayerOutboundSession(8, 64)));
		final Class<?> entryType = Class.forName(PhantomMaterializationService.class.getName() + "$Entry");
		final var entryConstructor = entryType.getDeclaredConstructor(long.class, int.class, long.class, boolean.class, PhantomMaterializedPlayer.class);
		entryConstructor.setAccessible(true);
		final var entries = (Map<Long, Object>) field(PhantomMaterializationService.class, "_activeByProfile").get(service);
		CountDownLatch firstMaterialization = null;
		for (int index = 0; index < actors.size(); index++)
		{
			final var actor = actors.get(index);
			field(PhantomMaterializedPlayer.class, "_actionAdmissionOpen").set(actor, true);
			final Object entry = entryConstructor.newInstance(index + 1L, 910103001 + index, 0L, false, actor);
			final var completion = (CountDownLatch) field(entryType, "_materializationCompletion").get(entry);
			if (index == 0) { firstMaterialization = completion; }
			if (index != 0 || !firstMaterializationPending) { completion.countDown(); }
			entries.put(index + 1L, entry);
		}
		return new Fixture(service, actors, firstMaterialization);
	}

	private static Field field(Class<?> type, String name) throws Exception
	{
		final Field result = type.getDeclaredField(name);
		result.setAccessible(true);
		return result;
	}
}

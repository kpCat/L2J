/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.ai.PlayerAI;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.NativeBoundaryOutcome;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Semantics;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.tests.phantoms.PhantomEarnedRecipient109Trace;

/** Actual managed Players/scopes, never published to World or persisted. No native acceptance claims. */
public final class PhantomPickup109Checks
{
	private static int nextId = 90010900;
	private PhantomPickup109Checks() { }

	/** Arms only the stock AI state; the tested cancel/think/pickup implementations are inherited. */
	private static final class PickupAI extends PlayerAI
	{
		PickupAI(Player player) { super(player); }
		void arm(WorldObject target) { changeIntention(Intention.PICK_UP, target, null); setTarget(target); }
		void cancelCallback() { super.onActionCancel(); }
	}

	private static final class Managed implements AutoCloseable
	{
		final PhantomIdentityLeaseRegistry.Lease identity;
		final Player player;
		final PhantomNativeWorkScope scope;
		Managed() throws Exception
		{
			final int id = ++nextId;
			identity = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(id, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
			check(identity != null, "identity lease unavailable");
			final var constructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class, Consumer.class);
			constructor.setAccessible(true);
			final var captured = new PhantomNativeWorkScope[1];
			player = constructor.newInstance(id, PlayerTemplateData.getInstance().getTemplate(0), "DB_FREE_109", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false),
				(Consumer<Player>) value -> { captured[0] = new PhantomNativeWorkScope(new Object(), value, identity, id); value.attachNativeWorkOwner(captured[0]); });
			scope = captured[0];
			final var finish = Player.class.getDeclaredMethod("finishNativeLoad", Throwable.class); finish.setAccessible(true); finish.invoke(player, new Object[] { null });
			player.stopAllTasks();
			check(scope.open() && scope.isCurrent() && player.isNativeWorkManaged() && !DatabaseFactory.isInitialized() && World.getInstance().findObject(id) == null, "fixture is not a real unpublished DB-free managed Player");
		}
		@Override public void close()
		{
			try
			{
				player.stopAllTasks();
				try { scope.drainAndSeal(deadline()); }
				catch (IllegalStateException expected) { check(scope.firstNativeIncident() != null && !scope.open(), "unexpected cleanup failure: " + expected); }
				check(scope.outstanding() == 0 && scope.pendingTimers() == 0, "test leaked actual tickets/timers");
				if (scope.sealed()) { scope.detach(); }
			}
			finally { player.detachNativeWorkOwner(scope); identity.close(); }
		}
	}

	public static void main(String[] args) throws Exception
	{
		check(!DatabaseFactory.isInitialized(), "DB must remain uninitialized");
		// Stock ContactList/BlockList constructor reads fail closed before a connection can exist.
		PlayerConfig.load();
		final var scheduled = new ScheduledThreadPoolExecutor(1); scheduled.setRemoveOnCancelPolicy(true);
		final var instant = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		final Field scheduledField = poolField("SCHEDULED_POOL"), instantField = poolField("INSTANT_POOL");
		final Object oldScheduled = scheduledField.get(null), oldInstant = instantField.get(null);
		scheduledField.set(null, scheduled); instantField.set(null, instant);
		try
		{
			if (args.length == 1 && args[0].equals("delivery-ordinary")) { deliveryOrdinary(); return; }
			if (args.length == 1 && args[0].equals("delivery-window")) { deliveryWindow(); return; }
			if (args.length == 1 && args[0].equals("delivery-mismatch")) { deliveryMismatch(); return; }
			cancelReentry();
			if (args.length == 1 && args[0].equals("trace")) { strictDelayed(); diagnosticIsolation(); }
			else if (args.length == 0 || !args[0].equals("reentry"))
			{
				strictDelayed(); pairedAdmissionAndDrain(); sealedRecipient(); staleEpoch(); competingLoot(); cancelledBeforeThink(); diagnosticIsolation(); writerFailure();
			}
			check(!DatabaseFactory.isInitialized(), "test initialized DB");
			System.out.println("TASK109_PICKUP_PASS DB=false worldPlayersPublished=0 nativeAcceptance=false");
		}
		finally
		{
			scheduled.shutdownNow(); instant.shutdownNow();
			check(scheduled.awaitTermination(3, TimeUnit.SECONDS) && instant.awaitTermination(3, TimeUnit.SECONDS), "executor leaked");
			scheduledField.set(null, oldScheduled); instantField.set(null, oldInstant);
		}
	}

	private static Party party(Managed... members)
	{
		final var party = new Party(members[0].player, PartyDistributionType.RANDOM);
		for (int i = 1; i < members.length; i++) { party.getMembers().add(members[i].player); }
		for (Managed member : members) { member.player.setParty(party); }
		return party;
	}

	private static void cancelReentry() throws Exception
	{
		try (var one = new Managed(); var two = new Managed(); var three = new Managed(); var trace = new PhantomEarnedRecipient109Trace())
		{
			party(one, two, three);
			final var ai = new PickupAI(one.player); one.player.setAI(ai); ai.arm(two.player);
			final var result = PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(), "test-hit-native-write", () ->
			{
				check(PlayerNativeWork.current(one.scope).semantics() == Semantics.EARNED && PlayerNativeWork.current(two.scope) == null && two.scope.isCurrent(), "fresh Party recipient precondition");
				// Same synchronous CANCEL -> stock thinkPickUp -> actual Player.doPickupItem as TEST109i.
				// Non-item target deliberately prevents all inventory/ground/SQL writes.
				ai.cancelCallback();
			});
			check(result == NativeBoundaryOutcome.EXECUTED && ai.getIntention() == Intention.IDLE, "stock cancel reentry did not finish");
			for (Managed member : List.of(one, two, three)) { check(member.scope.open() && member.scope.outstanding() == 0 && member.scope.firstNativeIncident() == null, "legal fresh pickup poisoned an owner"); }
			final String facts = trace.snapshot();
			check(facts.contains("stage=DISCOVER kind=party-pickup actor=" + one.player.getObjectId()) && facts.contains("stage=WRITE") && facts.contains("recipient=" + two.player.getObjectId())
				&& facts.contains(":contextTicket@0") && facts.contains(":semantics=EARNED:running=true:id=") && facts.contains(":parentId=") && !facts.contains("=UNKNOWN"), "actual owner/ticket diagnostics missing");
			System.out.println("PASS stock-cancel-reentry realManagedPlayers=3 freshRecipient=true");
			System.out.print(facts);
		}
	}

	private static void strictDelayed() throws Exception
	{
		try (var one = new Managed(); var two = new Managed(); var trace = new PhantomEarnedRecipient109Trace())
		{
			final var writes = new AtomicInteger();
			try
			{
				PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(), "published-hit", () -> PlayerNativeWork.run(one.player, List.of(two.player), "illegal-delayed-pickup", writes::incrementAndGet));
				throw new AssertionError("illegal delayed newcomer accepted");
			}
			catch (IllegalStateException failure) { check("NATIVE_EARNED_RECIPIENT_NOT_CAPTURED".equals(failure.getMessage()), "strict key changed"); }
			check(writes.get() == 0 && one.scope.firstNativeIncident() != null && two.scope.firstNativeIncident() != null, "strict refusal/failure retention weakened");
			check(trace.snapshot().contains("stage=STRICT_REFUSAL kind=illegal-delayed-pickup") && trace.snapshot().contains(":contextTicket@0"), "strict refusal diagnostic lost fresh recipient");
			System.out.println("PASS illegal-delayed-recipient rejectedBeforeWrite=true");
			System.out.print(trace.snapshot());
		}
	}

	private static void pairedAdmissionAndDrain() throws Exception
	{
		try (var one = new Managed(); var two = new Managed(); var three = new Managed())
		{
			final var roster = List.copyOf(party(one, two, three).getMembers());
			final var entered = new CountDownLatch(1); final var release = new CountDownLatch(1); final var finished = new CountDownLatch(1);
			final var failure = new AtomicReference<Throwable>(); final var credited = new AtomicInteger();
			final var writer = new Thread(() ->
			{
				try { PlayerNativeWork.runAtNativeWriteBoundary(one.player, roster, "party-pickup", () ->
				{
					for (Managed member : List.of(one, two, three)) { check(PlayerNativeWork.current(member.scope).semantics() == Semantics.EARNED, "Party recipient not earned before writer"); }
					entered.countDown(); await(release);
					// A ledger is a contract sentinel, not a claimed native item/reward.
					PlayerNativeWork.run(two.player, roster, "party-item", credited::incrementAndGet);
				}); }
				catch (Throwable thrown) { failure.set(thrown); }
			}, "DB-free-pickup-writer");
			final var drain = new Thread(() -> { try { two.scope.drainAndSeal(deadline()); } catch (Throwable thrown) { failure.set(thrown); } finally { finished.countDown(); } }, "DB-free-pickup-drain");
			writer.start();
			try
			{
				await(entered); drain.start();
				final long until = deadline(); while (two.scope.open() && System.nanoTime() < until) { Thread.onSpinWait(); }
				check(!two.scope.open() && !two.scope.sealed() && finished.getCount() == 1 && two.scope.outstanding() > 0, "drain crossed running Party writer");
			}
			finally { release.countDown(); writer.join(4000); if (drain.getState() != Thread.State.NEW) { drain.join(4000); } }
			check(!writer.isAlive() && !drain.isAlive() && failure.get() == null && credited.get() == 1 && two.scope.sealed() && two.scope.outstanding() == 0, "earned Party transfer/drain failed: " + failure.get());
			System.out.println("PASS party-recipient-transfer drainWaitsForAllEarned=true ledgerOnly=true");
		}
	}

	private static void sealedRecipient() throws Exception
	{
		try (var one = new Managed(); var two = new Managed())
		{
			two.scope.drainAndSeal(deadline()); final var writes = new AtomicInteger();
			PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(), "published-hit", () ->
			{
				check(PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(two.player), "party-pickup", writes::incrementAndGet) == NativeBoundaryOutcome.DEFERRED_BEFORE_WRITE, "sealed peer admitted");
				check(PlayerNativeWork.current(one.scope).isRunning(), "partial refusal cancelled original hit");
			});
			check(writes.get() == 0 && one.scope.open() && two.scope.sealed() && one.scope.firstNativeIncident() == null && one.scope.outstanding() == 0, "refusal wrote or leaked/poisoned origin");
			System.out.println("PASS sealed-recipient partialAdmissionRetired=true writes=0");
		}
	}

	private static void staleEpoch() throws Exception
	{
		try (var one = new Managed(); var two = new Managed())
		{
			final var replacement = new PhantomNativeWorkScope(new Object(), two.player, two.identity, two.scope.epoch() + 1);
			PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(two.player), "captured-hit", () ->
			{
				two.player.detachNativeWorkOwner(two.scope); two.player.attachNativeWorkOwner(replacement);
				try { check(PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(two.player), "party-pickup", () -> { throw new AssertionError("stale writer ran"); }) == NativeBoundaryOutcome.REJECTED_STALE, "stale context borrowed replacement epoch"); }
				finally { two.player.detachNativeWorkOwner(replacement); two.player.attachNativeWorkOwner(two.scope); }
			});
			check(replacement.outstanding() == 0 && one.scope.open() && two.scope.open(), "replacement owner borrowed or origin poisoned");
			System.out.println("PASS stale-epoch rejectedBeforeWrite=true");
		}
	}

	private static void competingLoot() throws Exception
	{
		try (var one = new Managed(); var two = new Managed(); var three = new Managed())
		{
			final var roster = List.copyOf(party(one, two, three).getMembers());
			final var start = new CountDownLatch(1); final var failure = new AtomicReference<Throwable>();
			final var ground = new Object(); final boolean[] available = { true }; final var transfers = new AtomicInteger();
			final List<Thread> workers = new ArrayList<>();
			for (Managed actor : List.of(one, two))
			{
				workers.add(new Thread(() -> { try { await(start); PlayerNativeWork.runAtNativeWriteBoundary(actor.player, roster, "party-pickup", () -> { synchronized (ground) { if (available[0]) { available[0] = false; PlayerNativeWork.run(three.player, roster, "party-item", transfers::incrementAndGet); } } }); } catch (Throwable thrown) { failure.set(thrown); } }, "DB-free-competing-pickup"));
			}
			workers.forEach(Thread::start); start.countDown(); for (Thread worker : workers) { worker.join(4000); check(!worker.isAlive(), "concurrent pickup hung"); }
			check(failure.get() == null && transfers.get() == 1 && one.scope.outstanding() == 0 && two.scope.outstanding() == 0 && three.scope.outstanding() == 0, "competing pickup duplicated or leaked: " + failure.get());
			System.out.println("PASS competing-loot groundMonitorSentinel=true exactlyOneTransfer=true");
		}
	}

	private static void cancelledBeforeThink() throws Exception
	{
		try (var one = new Managed(); var two = new Managed(); var trace = new PhantomEarnedRecipient109Trace())
		{
			party(one, two); final var ai = new PickupAI(one.player); one.player.setAI(ai); ai.arm(two.player);
			check(ai.isPickingUp(two.player) && !ai.isPickingUp(one.player), "exact owned pickup predicate");
			PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(), "published-hit", () ->
			{
				// Same exact-AI monitor/IDLE operation as cancelOwnedAction; no asynchronous retry is added.
				synchronized (ai) { if (ai.isPickingUp(two.player)) { ai.setIntention(Intention.IDLE); one.player.setTarget(null); } }
				ai.cancelCallback();
			});
			check(ai.getIntention() == Intention.IDLE && one.scope.open() && two.scope.open() && one.scope.outstanding() == 0 && two.scope.outstanding() == 0 && !trace.snapshot().contains("kind=party-pickup"), "cancel created a pickup entry or poisoned owners");
			System.out.println("PASS cancel-before-think duringEarned=true queuedRetry=false");
		}
	}

	private static void diagnosticIsolation() throws Exception
	{
		PhantomEarnedRecipient109Trace.requireGain(10, 10);
		for (long total : new long[] { -1, 0, 11 })
		{
			try { PhantomEarnedRecipient109Trace.requireGain(10, total); throw new AssertionError("invalid native source count accepted"); }
			catch (IllegalStateException expected) { check("TASK109_PICKUP_DUPLICATE_COUNT".equals(expected.getMessage()), "count guard changed"); }
		}
		try (var one = new Managed(); var two = new Managed())
		{
			final var observerField = PlayerNativeWork.class.getDeclaredField("_participantObserver"); observerField.setAccessible(true);
			final var failureField = PlayerNativeWork.class.getDeclaredField("_participantObserverFailure"); failureField.setAccessible(true);
			try (var trace = new PhantomEarnedRecipient109Trace())
			{
				for (int sample = 0; sample < 40; sample++) { one.player.doPickupItem(two.player); }
				check(trace.snapshot().contains("maxRows=64 dropped=16"), "diagnostics unbounded or not sampling actual pickup");
			}
			final Object previous = observerField.get(null); final Object previousFailure = failureField.get(null);
			try
			{
				for (Throwable unavailable : List.of(new IllegalStateException("optional sample unavailable"), new AssertionError("optional sample assertion")))
				{
					observerField.set(null, (java.util.function.BiConsumer<String, List<PlayerNativeWork.Ticket>>) (facts, tickets) -> { if (unavailable instanceof Error error) { throw error; } throw (RuntimeException) unavailable; });
					one.player.doPickupItem(two.player);
					check(one.scope.open() && two.scope.open() && failureField.get(null).equals(unavailable.getClass().getName()), "optional observer poisoned gameplay or lost diagnostic failure");
				}
				try
				{
					PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(), "published-hit", () -> PlayerNativeWork.run(one.player, List.of(two.player), "illegal-delayed", () -> { throw new AssertionError("illegal writer ran"); }));
					throw new AssertionError("diagnostic failure bypassed strict guard");
				}
				catch (IllegalStateException expected) { check("NATIVE_EARNED_RECIPIENT_NOT_CAPTURED".equals(expected.getMessage()), "diagnostic replaced primary safety error"); }
				check(one.scope.firstNativeIncident() != null && two.scope.firstNativeIncident() != null, "real ownership failure was suppressed");
			}
			finally { observerField.set(null, previous); failureField.set(null, previousFailure); }
			System.out.println("PASS bounded-diagnostic-isolation strictFailureStillFatal=true");
		}
	}

	private static void writerFailure() throws Exception
	{
		try (var one = new Managed(); var two = new Managed(); var three = new Managed())
		{
			final var failure = new IllegalStateException("DB_FREE_NATIVE_WRITER_FAILURE");
			try
			{
				PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(two.player, three.player), "party-pickup", () -> { throw failure; });
				throw new AssertionError("writer failure swallowed");
			}
			catch (IllegalStateException actual) { check(actual == failure, "primary writer failure replaced"); }
			for (Managed member : List.of(one, two, three))
			{
				check(!member.scope.open() && member.scope.firstNativeIncident() != null && member.scope.outstanding() == 0, "writer failure not retained on exact Party owner");
				try { member.scope.drainAndSeal(deadline()); throw new AssertionError("failed writer became successful STORE/drain"); }
				catch (IllegalStateException expected) { check(!member.scope.sealed(), "failed owner sealed successfully"); }
			}
			System.out.println("PASS writer-failure exactPrimary=true allOwnersFenced=true cleanDrainDenied=true");
		}
	}

	private static void deliveryWindow() throws Exception
	{
		try (var one = new Managed(); var two = new Managed(); var three = new Managed(); var trace = new PhantomEarnedRecipient109Trace())
		{
			final var begin = PhantomEarnedRecipient109Trace.class.getMethod("beginInventoryWindow", List.class);
			final var pendingField = PhantomEarnedRecipient109Trace.class.getDeclaredField("pending"); pendingField.setAccessible(true);
			final var pickupField = PhantomEarnedRecipient109Trace.class.getDeclaredField("pickupObserver"); pickupField.setAccessible(true);
			@SuppressWarnings("unchecked") final var callback = (java.util.function.BiConsumer<String, long[]>) pickupField.get(trace);
			@SuppressWarnings("unchecked") final var pending = (ThreadLocal<java.util.ArrayDeque<?>>) pendingField.get(trace);
			final var roster = List.of(one.player, two.player, three.player);
			final long[] values = { one.player.getObjectId(), 901091, 57, 10, 0, 0, 0, 0 };
			final Object[] proof = new Object[1];
			PlayerNativeWork.runAtNativeWriteBoundary(one.player, roster, "measurement-sentinel", () -> { callback.accept("START", values); proof[0] = pending.get().pop(); });
			final var credit = PhantomEarnedRecipient109Trace.class.getDeclaredMethod("recordInventoryProof", proof[0].getClass()); credit.setAccessible(true);
			begin.invoke(null, roster); credit.invoke(trace, proof[0]);
			check(PhantomEarnedRecipient109Trace.inventoryProofCount() == 0, "pre-window pickup credited to measured Party");
			PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(two.player), "measurement-sentinel", () -> { callback.accept("START", values); proof[0] = pending.get().pop(); });
			credit.invoke(trace, proof[0]); check(PhantomEarnedRecipient109Trace.inventoryProofCount() == 0, "different Party roster credited");
			PlayerNativeWork.runAtNativeWriteBoundary(one.player, roster, "measurement-sentinel", () -> { callback.accept("START", values); proof[0] = pending.get().pop(); });
			credit.invoke(trace, proof[0]); check(PhantomEarnedRecipient109Trace.inventoryProofCount() == 1, "exact current Party/window proof rejected");
			begin.invoke(null, roster); credit.invoke(trace, proof[0]);
			check(PhantomEarnedRecipient109Trace.inventoryProofCount() == 0, "old measurement generation leaked into new window");
			System.out.println("PASS delivery-party-window exactRosterEpochOwner=true oldWindowRejected=true ledgerSentinel=true nativeTransfer=false");
		}
	}

	private static void deliveryMismatch() throws Exception
	{
		try (var one = new Managed(); var two = new Managed(); var trace = new PhantomEarnedRecipient109Trace())
		{
			final var pendingField = PhantomEarnedRecipient109Trace.class.getDeclaredField("pending"); pendingField.setAccessible(true);
			final var pickupField = PhantomEarnedRecipient109Trace.class.getDeclaredField("pickupObserver"); pickupField.setAccessible(true);
			@SuppressWarnings("unchecked") final var callback = (java.util.function.BiConsumer<String, long[]>) pickupField.get(trace);
			@SuppressWarnings("unchecked") final var pending = (ThreadLocal<java.util.ArrayDeque<?>>) pendingField.get(trace);
			final long[] values = { one.player.getObjectId(), 901092, 57, 10, 0, 0, 0, 0 };
			PlayerNativeWork.runAtNativeWriteBoundary(one.player, List.of(two.player), "inventory-mismatch-sentinel", () ->
			{
				callback.accept("START", values); final var proof = pending.get().peek();
				try
				{
					final var beforeField = proof.getClass().getDeclaredField("before"); beforeField.setAccessible(true);
					@SuppressWarnings("unchecked") final var before = (java.util.Map<Integer, Long>) beforeField.get(proof);
					before.put(one.player.getObjectId(), before.get(one.player.getObjectId()) + 1);
				}
				catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
				callback.accept("END", values);
			});
			check(one.scope.firstNativeIncident() == null && two.scope.firstNativeIncident() == null, "observer took owner failure boundary inside callback");
			try { PhantomEarnedRecipient109Trace.assertSafe(); throw new AssertionError("inventory mismatch did not latch unsafe"); }
			catch (IllegalStateException expected) { check("TASK109_PICKUP_UNSAFE".equals(expected.getMessage()) && "TASK109_PICKUP_INVENTORY_MISMATCH".equals(expected.getCause().getMessage()), "wrong mismatch boundary"); }
			check(one.scope.firstNativeIncident() != null && two.scope.firstNativeIncident() != null, "unsafe was not fenced at main boundary");
			System.out.println("PASS delivery-inventory-mismatch unsafeLatched=true ownerFailureOnlyAtMain=true measurementSentinel=true DB=false");
		}
	}

	private static void deliveryOrdinary() throws Exception
	{
		final var constructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class, Consumer.class);
		constructor.setAccessible(true);
		final var ordinary = constructor.newInstance(++nextId, PlayerTemplateData.getInstance().getTemplate(0), "DB_FREE_ORDINARY_109", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false), null);
		try (var managed = new Managed())
		{
			check(!ordinary.isNativeWorkManaged() && ordinary.getNativeWorkOwner() == null, "ordinary player acquired Phantom lifetime");
			ordinary.doPickupItem(managed.player);
			check(ordinary.getAI().getIntention() == Intention.IDLE && ordinary.getNativeWorkOwner() == null, "ordinary stock pickup body did not run");
			final var mixed = new Party(ordinary, PartyDistributionType.RANDOM); mixed.getMembers().add(managed.player); ordinary.setParty(mixed); managed.player.setParty(mixed);
			ordinary.doPickupItem(managed.player);
			check(ordinary.getNativeWorkOwner() == null && managed.scope.open() && managed.scope.outstanding() == 0 && managed.scope.firstNativeIncident() == null, "mixed Party poisoned ordinary player or managed recipient");
			check(!DatabaseFactory.isInitialized(), "ordinary regression initialized DB");
			System.out.println("PASS delivery-ordinary stockBody=true phantomOwner=false mixedPartySafe=true nonItemTarget=true DB=false nativeInventory=false");
		}
		finally { ordinary.stopAllTasks(); }
	}

	private static Field poolField(String name) throws Exception { final var field = ThreadPool.class.getDeclaredField(name); field.setAccessible(true); return field; }
	private static long deadline() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(3); }
	private static void await(CountDownLatch latch) { try { check(latch.await(3, TimeUnit.SECONDS), "bounded latch timeout"); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); } }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

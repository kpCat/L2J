/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Reuses the composed native handoff fixture; recovery precedes production request admission. */
public final class PhantomCheckpointRecovery025Suite implements PhantomTestSuite
{
	private final PhantomNativeContextHandoffSuite _native = new PhantomNativeContextHandoffSuite();
	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("checkpoint-recovery025", new PhantomCheckpointRecovery025Suite(), new PhantomTestContext(25002501, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "checkpoint-recovery025"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception { _native.beforeAll(context); }
	@Override public void afterAll(PhantomTestContext context) throws Exception { _native.afterAll(context); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("R01-cold-pending-before-baseline", _ -> cold(false));
		registry.add("R07-exact-failed-baseline-recovery", _ -> cold(true));
		registry.add("R09-native-combat-capture-defers-before-write", _ -> combatCapture());
		registry.add("R15-proven-no-write-stale-goal-replans", _ -> staleGoal());
		registry.add("R11-inventory-flush-without-receipt-stays-fenced", _ -> interrupted(FaultPoint.BEFORE_OWNED_PREPARE_COMMIT, false));
		registry.add("R12-prepared-receipt-control-resume", _ -> interrupted(FaultPoint.AFTER_OWNED_PREPARE, false));
		registry.add("R13-native-receipt-finalize-once", _ -> interrupted(FaultPoint.AFTER_OWNED_NATIVE_STORE, false));
		registry.add("R14-finalized-publication-without-new-store", _ -> interrupted(null, true));
	}
	private void interrupted(FaultPoint fault, boolean publication) throws Exception
	{
		final var armed = new java.util.concurrent.atomic.AtomicBoolean();
		final var observing = new java.util.concurrent.atomic.AtomicBoolean();
		final var nativeStores = new java.util.concurrent.atomic.AtomicInteger();
		try (var f = _native.new Fixture(true, false, 0, point ->
		{
			if (observing.get() && point == FaultPoint.AFTER_OWNED_NATIVE_STORE) { nativeStores.incrementAndGet(); }
			if (point == fault && armed.compareAndSet(true, false)) { throw new IllegalStateException("TASK025_INTERRUPTED:" + point); }
		}))
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Exact native materialization.");
			final var player = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(f.objectId);
			final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final long quietDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
			while (scope.outstanding() > 0 && System.nanoTime() < quietDeadline) { Thread.sleep(10); }
			PhantomAssertions.assertEquals(0, scope.outstanding(), "Exact setup earned work finished.");
			// A stock scalar change distinguishes receipt BEFORE from AFTER without awarding anything.
			player.setHeading((player.getHeading() + 8192) % 65536);
			if (publication) { player.setCurrentMp(player.getMaxMp() - 0.25); }
			if (publication) { f.background.installCommittedPositionPublisher((id, position) -> { if (armed.compareAndSet(true, false)) { throw new IllegalStateException("TASK025_INDEX_PUBLICATION"); } }); }
			observing.set(true); armed.set(true);
			PhantomAssertions.assertFalse(f.background.captureVisibleArrival(f.id, player, goal, goal.selectedAnchor().key()), "Injected boundary cannot claim completed checkpoint.");
			PhantomAssertions.assertFalse(scope.open(), "Attempted write remains fenced.");
			final var first = f.background.continueVisibleCheckpoint(f.id, goal).orElseThrow();
			if (fault == FaultPoint.BEFORE_OWNED_PREPARE_COMMIT)
			{
				PhantomAssertions.assertEquals(PhantomNativeWorkScope.CheckpointOutcome.VERIFY_WRITE_OUTCOME, first.outcome(), "Inventory flush is not no-write proof.");
				PhantomAssertions.assertFalse(player.hasPendingOwnedStore() || scope.open(), "No pending alone cannot open inventory-flush failure.");
				PhantomAssertions.assertEquals(0, nativeStores.get(), "Native body never started.");
				final var currentGoal = f.goals.load(f.id).orElseThrow();
				final var changed = currentGoal.goal().withStatus(currentGoal.goal().status());
				f.goals.replace(f.id, currentGoal.rowVersion(), changed);
				PhantomAssertions.assertEquals(PhantomNativeWorkScope.CheckpointOutcome.TERMINAL_RETAIN, f.background.continueVisibleCheckpoint(f.id, changed).orElseThrow().outcome(), "New goal cannot discard unknown inventory-flush writes.");
				PhantomAssertions.assertFalse(scope.open(), "Stale unknown-write request remains fenced.");
				return;
			}
			PhantomAssertions.assertEquals(PhantomNativeWorkScope.CheckpointOutcome.RESUME, first.outcome(), "Exact control continuation: " + first);
			PhantomAssertions.assertTrue(scope.open() && scope.isCurrent(), "Original epoch resumed.");
			PhantomAssertions.assertFalse(player.hasPendingOwnedStore(), "Existing receipt completed.");
			PhantomAssertions.assertEquals(1, nativeStores.get(), "Recovery/publication does not repeat completed native body.");
			final var finalized = f.transactions.load(f.id).state();
			PhantomAssertions.assertTrue(f.background.captureVisibleArrival(f.id, player, goal, goal.selectedAnchor().key()), "Caller consumes completed exact request.");
			PhantomAssertions.assertEquals(finalized, f.transactions.load(f.id).state(), "Consumption does not repeat native writes.");
			PhantomAssertions.assertEquals(f.baseline.progress(), finalized.progress(), "Recovery grants no progress.");
		}
	}
	private void combatCapture() throws Exception
	{
		try (var f = _native.new Fixture(true))
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Exact native materialization.");
			final var player = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(f.objectId);
			final var scope = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			player.getAI().clientStartAutoAttack();
			PhantomAssertions.assertTrue(player.isInCombat(), "Stock combat guard precondition.");
			PhantomAssertions.assertFalse(f.background.captureVisibleArrival(f.id, player, goal, goal.selectedAnchor().key()), "Combat guard remains closed.");
			PhantomAssertions.assertTrue(scope.diagnosticScalars().get("nativeCheckpointFirstMessage").contains("firstPredicate=combat"), "First producer remains named.");
			PhantomAssertions.assertFalse(player.hasPendingOwnedStore(), "Capture failed before receipt/store.");
			PhantomAssertions.assertTrue(scope.open(), "Proven no-write temporary checkpoint must retain a native continuation.");
			PhantomAssertions.assertEquals(f.baseline.progress(), f.transactions.load(f.id).state().progress(), "No rewards from recovery.");
		}
	}
	private void staleGoal() throws Exception
	{
		try (var f = _native.new Fixture(true))
		{
			f.removeCatchup();
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS, f.materialization.materialize(f.id).status(), "Exact native materialization.");
			final var player = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(f.objectId);
			final var scope = (PhantomNativeWorkScope) player.getNativeWorkOwner();
			final var stored = f.goals.load(f.id).orElseThrow();
			player.getAI().clientStartAutoAttack();
			PhantomAssertions.assertFalse(f.background.captureVisibleArrival(f.id, player, stored.goal(), stored.goal().selectedAnchor().key()), "Stock combat rejection supplies no-write proof.");
			PhantomAssertions.assertTrue(scope.open(), "No-write request has completed its control claim.");
			final var before = f.transactions.load(f.id).state();
			final var next = stored.goal().withStatus(stored.goal().status());
			f.goals.replace(f.id, stored.rowVersion(), next);
			PhantomAssertions.assertTrue(f.background.continueVisibleCheckpoint(f.id, next).isEmpty(), "Completed no-write old request must permit bounded new-plan admission.");
			PhantomAssertions.assertTrue(scope.open() && scope.isCurrent() && !player.hasPendingOwnedStore(), "Exact current owner preserved.");
			PhantomAssertions.assertEquals(before, f.transactions.load(f.id).state(), "Stale control does not rewrite old snapshot.");
		}
	}
	private void cold(boolean failed) throws Exception
	{
		try (var f = _native.new Fixture())
		{
			final var goal = f.goals.load(f.id).orElseThrow().goal();
			final int points;
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT vitality_points FROM characters WHERE charId=?"))
			{
				statement.setInt(1, f.objectId);
				try (var rows = statement.executeQuery()) { PhantomAssertions.assertTrue(rows.next(), "Exact native fixture character."); points = rows.getInt(1); }
			}
			final var capture = new PhantomNativeContext.Capture(points, points == 1 ? PhantomNativeContext.Eligibility.SUPPORTED : PhantomNativeContext.Eligibility.VITALITY_REQUIRES_NATIVE);
			final var prepared = f.transactions.prepareOwnedStore(f.baseline, goal, 25002501, PhantomBackgroundState.State.MATERIALIZED, capture);
			PhantomAssertions.assertTrue(prepared.successful(), "Existing exact owned prepare: " + prepared.status());
			final var before = f.catchups.load(f.id).orElseThrow();
			if (failed) { f.catchups.replace(f.id, before, before.state().failed("catchup.baseline.conflict")); }
			// This distinct request is rejected after durable recovery, before any simulated interval.
			f.historical.begin(f.id, f.from, f.target, 1);
			PhantomAssertions.assertTrue(PhantomProfileRepositoryAccess.receiptAbsent(f.id), "Cold production advance must finalize the exact owned receipt before baseline/FAILED guards.");
			final var after = f.background.acquisitionSnapshot(f.id).orElseThrow();
			PhantomAssertions.assertEquals(f.baseline.progress(), after.progress(), "Recovery must not award progress.");
			PhantomAssertions.assertEquals(f.baseline.position(), after.position(), "Recovery preserves exact native coordinates.");
			PhantomAssertions.assertEquals(f.baseline.inventory(), after.inventory(), "Recovery preserves full native inventory.");
			final var claim = f.catchups.load(f.id).orElseThrow().state();
			PhantomAssertions.assertEquals(before.state().requestId(), claim.requestId(), "Recovery keeps request identity.");
			final var durable = f.transactions.load(f.id).state();
			f.historical.begin(f.id, f.from, f.target, 1);
			PhantomAssertions.assertTrue(PhantomProfileRepositoryAccess.receiptAbsent(f.id), "Repeated recovery leaves no pending receipt.");
			PhantomAssertions.assertEquals(durable.progress(), f.transactions.load(f.id).state().progress(), "Repeated pending recovery is idempotent.");
		}
	}
	private static final class PhantomProfileRepositoryAccess
	{
		static boolean receiptAbsent(long profileId)
		{
			return org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.open().findComponent(profileId, PhantomOwnedStoreIntent.COMPONENT_TYPE).isEmpty();
		}
	}
}

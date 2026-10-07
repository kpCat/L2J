/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;

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

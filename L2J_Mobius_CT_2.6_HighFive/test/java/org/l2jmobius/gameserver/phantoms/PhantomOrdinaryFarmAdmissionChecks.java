/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms;

import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.Facts;
import org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand;
import org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.Kind;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager.AdmissionProfileSnapshot;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** DB-free regression of the production admission gate reached after visible farm arrival. */
public final class PhantomOrdinaryFarmAdmissionChecks
{
	private static final long PROFILE = 1;

	public static void main(String[] args)
	{
		final long now = System.currentTimeMillis();
		final var ambient = PhantomPhysicalDemand.ambientTarget(PROFILE, 8, 8, true, now);
		final var retention = new PhantomMaterializationRetentionPolicy(_ -> new Facts(true, false, false, false, true), () -> 1, 60_000);
		// Use the original retention-only branch when running against the pinned baseline JAR.
		if (args.length == 1 && "baseline".equals(args[0]))
		{
			PhantomAssertions.assertTrue(retention.permitsNewActions(PROFILE),
				"An admitted ambient Solo farm at its destination is denied without a human observer.");
			return;
		}
		PhantomAssertions.assertTrue(permits(false, retention, admission(true, true), ambient),
			"An admitted ambient Solo farm at its destination is denied without a human observer.");

		PhantomAssertions.assertFalse(permits(true, retention, admission(true, true), ambient), "An active PvP hold must block new farm actions.");
		PhantomAssertions.assertFalse(permits(false, retention, admission(false, true), ambient), "Lost population admission must revoke the ambient grant.");
		PhantomAssertions.assertFalse(permits(false, retention, admission(true, false), ambient), "An offline calendar cannot grant ambient farm.");
		PhantomAssertions.assertFalse(permits(false, retention, admission(true, true), null), "Retention for existing work is insufficient to start farm.");
		PhantomAssertions.assertFalse(permits(false, retention, admission(true, true),
			PhantomPhysicalDemand.ambientTarget(PROFILE + 1, 8, 8, true, now)), "A foreign demand cannot grant farm.");
		PhantomAssertions.assertFalse(permits(false, retention, admission(true, true),
			PhantomPhysicalDemand.ambientTarget(PROFILE, 8, 8, true, now - 20_000)), "An expired demand cannot grant farm.");
		PhantomAssertions.assertFalse(permits(false, retention, admission(true, true),
			new PhantomPhysicalDemand(Kind.LOGICAL_PARTY_WAKE, PROFILE, 1, "party.fixture", now, now + 10_000)), "Party wake cannot grant Solo farm.");

		final var admitted = new AtomicBoolean(true);
		PhantomAssertions.assertFalse(PhantomSystem.permitsNewOrdinaryFarmActions(PROFILE, false, retention,
			_ -> admission(admitted.get(), true), _ ->
			{
				admitted.set(false);
				return ambient;
			}), "Admission revoked during demand lookup must be observed before issuing a grant.");

		final var observed = new PhantomMaterializationRetentionPolicy(_ -> new Facts(true, true, true, false, false), () -> 1, 60_000);
		PhantomAssertions.assertTrue(permits(false, observed, admission(false, true), null), "Ordinary human-local farm must keep its existing permission.");
		System.out.println("ORDINARY_FARM_ADMISSION_PASS; DB_CONNECTIONS=0; ACTORS=0");
	}

	private static AdmissionProfileSnapshot admission(boolean admitted, boolean online)
	{
		return new AdmissionProfileSnapshot(PROFILE, State.READY, PhantomActivityState.ACTIVE, PhantomActivityState.ACTIVE,
			admitted, admitted, "fixture", false, null, online);
	}

	private static boolean permits(boolean pvpHeld, PhantomMaterializationRetentionPolicy retention,
		AdmissionProfileSnapshot profile, PhantomPhysicalDemand demand)
	{
		return PhantomSystem.permitsNewOrdinaryFarmActions(PROFILE, pvpHeld, retention, _ -> profile, _ -> demand);
	}
}

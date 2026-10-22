/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Method;
import java.util.List;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementController;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;

/** Existing controller/debt scalar decisions; no Player, World, DB or timer fixture. */
public final class PhantomCombatDebt068Checks implements PhantomTestSuite
{
	@Override public String id() { return "combat-debt068"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-continuous-native-busy-cannot-hide-deadline", _ -> deadline());
		registry.add("02-damage-only-does-not-renew-useful-farm-debt", _ -> earned());
	}
	public static void main(String[] args) throws Exception
	{
		if (args.length == 0 || args[0].equals("deadline")) { deadline(); }
		if (args.length == 0 || args[0].equals("earned")) { earned(); }
		System.out.println("OWN068_COMBAT_DEBT_PASS; DB_CONNECTIONS=0; ACTORS=0");
	}
	private static void deadline() throws Exception
	{
		final Method gate = PhantomEngagementController.class.getDeclaredMethod("attemptAdmission", long.class, long.class, long.class, boolean.class);
		gate.setAccessible(true);
		for (boolean busy : List.of(false, true))
		{
			PhantomAssertions.assertEquals("EXPIRED", gate.invoke(null, 1L, 121L, 120L, busy).toString(), "Continuous native attack/cast must expire at the same deadline as idle.");
			PhantomAssertions.assertEquals(busy ? "BUSY" : "CONTINUE", gate.invoke(null, 1L, 120L, 120L, busy).toString(), "Before the boundary, native in-flight actions remain admitted.");
		}
		PhantomAssertions.assertEquals("BUSY", gate.invoke(null, 0L, 121L, 120L, true).toString(), "Unstarted attempt is not already overdue.");
	}
	private static void earned() throws Exception
	{
		final Method predicate = PhantomVisibleAutoPlay.class.getDeclaredMethod("earnedProgress", long.class, long.class, PlayerNativeEvidence.Snapshot.class);
		predicate.setAccessible(true);
		for (long damage : List.of(1L, 10L, 100L))
		{
			PhantomAssertions.assertEquals(false, predicate.invoke(null, 0L, 0L, sample(damage, 0, 0, damage, 0, damage)), "Tiny repeated damage/farm-cycle cannot renew useful work or rest debt.");
		}
		PhantomAssertions.assertEquals(false, predicate.invoke(null, 0L, 0L, sample(0, 0, 0, 0, 1, 1)), "Pickup alone is not a farm completion.");
		PhantomAssertions.assertEquals(true, predicate.invoke(null, 0L, 0L, sample(1, 1, 1, 1, 1, 1)), "Native kill/reward survives a concurrent lawful pickup.");
		PhantomAssertions.assertEquals(true, predicate.invoke(null, 0L, 0L, sample(1, 1, 0, 1, 0, 1)), "A real native kill can complete work before the separate reward callback.");
		PhantomAssertions.assertEquals(false, predicate.invoke(null, 1L, 1L, sample(1, 1, 1, 1, 1, 1)), "Duplicate same-epoch evidence must not renew debt.");
	}
	private static PlayerNativeEvidence.Snapshot sample(long damage, long kills, long rewards, long cycles, long loot, long useful)
	{
		return new PlayerNativeEvidence.Snapshot(1, 1, 1, false, damage + kills + rewards + loot, damage, kills, rewards, 1, cycles, rewards, rewards, loot, useful,
			PlayerNativeEvidence.Phase.COMBAT, 0, 120, useful, loot, 1, PlayerNativeEvidence.UnprovenReason.NONE, 0);
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.fishing.Fishing;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Native fishing checks for the campaign-owned ordinary Player fixtures. */
public final class QoLAdventure067FishingChecks
{
	private QoLAdventure067FishingChecks()
	{
	}

	/** Catches bypassing the native MP/reuse path, and choosing the wrong visible action. */
	public static void assertAssistedTick(Player player, int expectedSkillId)
	{
		final Fishing combat = requireReadyCombat(player);
		final Skill skill = player.getKnownSkill(expectedSkillId);
		PhantomAssertions.assertTrue((expectedSkillId == 1313) || (expectedSkillId == 1314), "Expected fishing skill must be Pumping or Reeling.");
		PhantomAssertions.assertTrue(skill != null, "Native fixture has not learned the expected fishing skill.");
		PhantomAssertions.assertFalse(player.isSkillDisabled(skill), "Native fixture skill is still in reuse before the assisted tick.");
		final double previousMp = player.getCurrentMp();
		combat.run();
		if (Boolean.getBoolean("phantom.task067.trace"))
		{
			try
			{
				final var stopped = Fishing.class.getDeclaredField("_assistStopped"); stopped.setAccessible(true);
				System.out.println("FISH067_TRACE stopped=" + stopped.getBoolean(combat) + "; rod=" + player.getActiveWeaponInstance() + "; alive=" + !player.isAlikeDead() + "; online=" + player.isOnline() + "; assist=" + PersonalCharacterQoLService.getInstance().isFishingAssistEnabled(player) + "; static=" + skill.isStatic() + "; hit=" + skill.getHitTime() + "; cool=" + skill.getCoolTime() + "; target=" + skill.getTargetType() + "; MP=" + previousMp + "->" + player.getCurrentMp() + "; casting=" + player.isCastingNow() + "; reuse=" + player.getSkillRemainingReuseTime(skill.getReuseHashCode()));
				Thread.sleep(250);
				System.out.println("FISH067_AFTER250 MP=" + player.getCurrentMp() + "; casting=" + player.isCastingNow() + "; reuse=" + player.getSkillRemainingReuseTime(skill.getReuseHashCode()) + "; spawned=" + player.isSpawned() + "; hasAI=" + player.hasAI() + "; nativeManaged=" + player.isNativeWorkManaged() + "; intention=" + player.getAI().getIntention());
			}
			catch (ReflectiveOperationException | InterruptedException e) { throw new IllegalStateException(e); }
		}
		PhantomAssertions.assertTrue(player.getCurrentMp() <= (previousMp - 2), "Fishing assist did not pay the native two MP cost.");
		PhantomAssertions.assertTrue(player.getSkillRemainingReuseTime(skill.getReuseHashCode()) > 0, "Fishing assist did not use the expected native skill/reuse path.");
		PhantomAssertions.assertTrue(player.getQueuedSkill() == null, "Fishing assist left a queued cast that can outlive this combat.");
	}

	/** Catches automatic casts for OFF, ordinary, headless or invalid-context fixtures. */
	public static void assertUnassistedTick(Player player)
	{
		final Fishing combat = requireReadyCombat(player);
		final double previousMp = player.getCurrentMp();
		combat.run();
		PhantomAssertions.assertTrue(player.getCurrentMp() >= previousMp, "An ineligible fishing tick spent MP on an automatic action.");
		PhantomAssertions.assertTrue(player.getQueuedSkill() == null, "An ineligible fishing tick queued an automatic action.");
	}

	/** Catches resuming an old combat after logout, rod replacement, death, transform or OFF. */
	public static void assertInvalidationIsPermanent(Player player, Runnable invalidate, Runnable restore)
	{
		final Fishing combat = requireReadyCombat(player);
		invalidate.run();
		try
		{
			final double previousMp = player.getCurrentMp();
			combat.run();
			PhantomAssertions.assertTrue(player.getCurrentMp() >= previousMp, "Invalidated fishing context still cast a skill.");
		}
		finally
		{
			restore.run();
		}
		PhantomAssertions.assertTrue(player.getFishCombat() == combat, "Context invalidation unexpectedly ended the native fishing combat.");
		assertUnassistedTick(player);
	}

	private static Fishing requireReadyCombat(Player player)
	{
		PhantomAssertions.assertTrue((player != null) && player.isFishing() && (player.getFishCombat() != null), "A real active fishing combat is required.");
		PhantomAssertions.assertFalse(player.isCastingNow() || player.isCastingSimultaneouslyNow(), "Fixture already has an in-flight cast.");
		return player.getFishCombat();
	}
}

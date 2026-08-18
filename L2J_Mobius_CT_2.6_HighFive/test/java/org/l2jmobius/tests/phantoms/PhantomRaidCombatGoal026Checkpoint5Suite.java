/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RaidTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomRaidCombatRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;

public final class PhantomRaidCombatGoal026Checkpoint5Suite implements PhantomTestSuite
{
	private static final long SEED = 26002651L;
	private static final String HASH = "ab".repeat(32);
	private String _serviceSource;

	@Override
	public String id()
	{
		return "raid-combat-goal026cp5";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Raid combat CP5 used the wrong deterministic seed.");
		_serviceSource = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/combat/PhantomCombatService.java"));
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-exact-epic-authority", _ -> testExactEpicAuthority());
		registry.add("02-raid-identity-and-kind-rejected", _ -> testIdentityAndKindRejection());
		registry.add("03-ordinary-normal-monster-safety-unchanged", _ -> testOrdinarySafety());
		registry.add("04-death-only-victory-and-disappearance-loss", _ -> testTerminalEvidenceContract());
	}

	private static void testExactEpicAuthority()
	{
		final PhantomRaidCombatRequest request = queenRequest();
		final ActorSnapshot actor = actor(0);
		final RaidTargetSnapshot target = raidTarget(7001, 29001, 0, NpcKind.GRAND_BOSS, false);
		PhantomAssertions.assertEquals(HASH.toUpperCase(java.util.Locale.ROOT), request.attemptAuthorityHash(), "Raid authority hash was not canonicalized.");
		PhantomAssertions.assertTrue(target.validFor(actor, request, 2000), "Exact Queen Ant epic authority was rejected.");
	}

	private static void testIdentityAndKindRejection()
	{
		final PhantomRaidCombatRequest request = queenRequest();
		final ActorSnapshot actor = actor(0);
		PhantomAssertions.assertFalse(raidTarget(7002, 29001, 0, NpcKind.GRAND_BOSS, false).validFor(actor, request, 2000), "Different raid object identity was accepted.");
		PhantomAssertions.assertFalse(raidTarget(7001, 29002, 0, NpcKind.GRAND_BOSS, false).validFor(actor, request, 2000), "Different raid NPC identity was accepted.");
		PhantomAssertions.assertFalse(raidTarget(7001, 29001, 0, NpcKind.RAID_BOSS, false).validFor(actor, request, 2000), "RAID_BOSS knowledge was accepted for an EPIC request.");
		PhantomAssertions.assertFalse(raidTarget(7001, 29001, 1, NpcKind.GRAND_BOSS, false).validFor(actor, request, 2000), "Cross-instance raid target was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomRaidCombatRequest(1, 7001, 29001, 0, ContentKind.EPIC, NpcKind.RAID_BOSS, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, false, 48, 60_000, () -> false), "Mismatched content/NPC kind was accepted.");
	}

	private static void testOrdinarySafety()
	{
		final TargetSnapshot raidLike = new TargetSnapshot(7001, 29001, 0, 100, 100, false, false, true, true, false, false, false, 100, false, true);
		PhantomAssertions.assertFalse(raidLike.validFor(actor(0), 2000), "Ordinary Combat accepted a non-normal raid-like monster.");
	}

	private void testTerminalEvidenceContract()
	{
		final int start = _serviceSource.indexOf("private void processRaid(");
		final int end = _serviceSource.indexOf("private void issueRaidAction(", start);
		PhantomAssertions.assertTrue((start >= 0) && (end > start), "Raid processing method is missing.");
		final String method = _serviceSource.substring(start, end);
		PhantomAssertions.assertTrue(method.contains("target.dead() || target.alikeDead()"), "Raid victory lacks actual death evidence.");
		PhantomAssertions.assertTrue(method.contains("PhantomCombatResult.VICTORY"), "Raid death does not terminate as victory.");
		PhantomAssertions.assertTrue(method.contains("target == null") && method.contains("PhantomCombatResult.TARGET_LOST"), "Raid disappearance is not an explicit target loss.");
	}

	private static PhantomRaidCombatRequest queenRequest()
	{
		return new PhantomRaidCombatRequest(1, 7001, 29001, 0, ContentKind.EPIC, NpcKind.GRAND_BOSS, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, false, 48, 60_000, () -> false);
	}

	private static ActorSnapshot actor(int instanceId)
	{
		return new ActorSnapshot(9001, 88, instanceId, 100, 100, 100, 100, 100, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
	}

	private static RaidTargetSnapshot raidTarget(int objectId, int npcId, int instanceId, NpcKind kind, boolean dead)
	{
		return new RaidTargetSnapshot(objectId, npcId, instanceId, dead ? 0 : 100, 100, dead, dead, !dead, !dead, false, true, kind, 100, false, true);
	}
}

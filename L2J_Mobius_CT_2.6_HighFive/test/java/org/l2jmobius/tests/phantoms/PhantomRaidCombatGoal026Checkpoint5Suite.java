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
	private static final long SEED = 26002652L;
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
		registry.add("05-minion-parent-instance-kind-and-curse-authority", _ ->
		{
			final var token = (org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken) () -> false;
			final var request = new PhantomRaidCombatRequest(1, 8001, 0, 0, ContentKind.RAID, NpcKind.MONSTER, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, false, 1000, 60_000, token, 7001, 25199);
			final var child = new RaidTargetSnapshot(8001, 25201, 0, 100, 100, false, false, true, true, false, true, NpcKind.MONSTER, 100, false, true, 7001, 25199, 79, NpcKind.RAID_BOSS);
			PhantomAssertions.assertTrue(child.validFor(actor(0), request, 2000), "Exact stock child of the live declared boss was rejected.");
			PhantomAssertions.assertEquals(79, child.maximumActorLevel(request), "Minion native curse ceiling was lost behind the higher boss ceiling.");
			PhantomAssertions.assertFalse(child.validFor(actor(1), request, 2000), "Cross-instance minion was admitted.");
			final var foreign = new PhantomRaidCombatRequest(1, 8001, 0, 0, ContentKind.RAID, NpcKind.MONSTER, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, false, 79, 60_000, token, 7002, 25199);
			PhantomAssertions.assertFalse(child.validFor(actor(0), foreign, 2000), "A different boss object authorized this minion.");
			final var wrongKind = new PhantomRaidCombatRequest(1, 8001, 0, 0, ContentKind.EPIC, NpcKind.MONSTER, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, false, 79, 60_000, token, 7001, 25199);
			PhantomAssertions.assertFalse(child.validFor(actor(0), wrongKind, 2000), "RAID child was accepted as EPIC evidence.");
			final var absentParent = new RaidTargetSnapshot(8001, 25201, 0, 100, 100, false, false, true, true, false, false, NpcKind.MONSTER, 100, false, true, 7001, 25199, 79, NpcKind.RAID_BOSS);
			PhantomAssertions.assertFalse(absentParent.validFor(actor(0), request, 2000), "Dead/missing/noncanonical parent was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomRaidCombatRequest(1, 8001, 0, 0, ContentKind.RAID, NpcKind.MONSTER, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, true, 79, 60_000, token, 7001, 25199), "Minion requested boss collector/loot ownership.");
		});
		registry.add("06-area-aggro-requires-exact-live-family-authority064", _ ->
		{
			final var authority = new PhantomRaidCombatRequest(1, 7001, 25199, 0, ContentKind.RAID, NpcKind.RAID_BOSS, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, true, 79, 60_000, () -> false);
			final var child = new RaidTargetSnapshot(8001, 25200, 0, 100, 100, false, false, true, true, false, true, NpcKind.MONSTER, 100, false, true, 7001, 25199, 79, NpcKind.RAID_BOSS);
			PhantomAssertions.assertTrue(child.validAggroRecipientFor(actor(0), authority, 2000, 79), "An admitted boss could not authorize its live canonical minion for defense.");
			PhantomAssertions.assertTrue(raidTarget(7001, 25199, 0, NpcKind.RAID_BOSS, false).validAggroRecipientFor(actor(0), authority, 2000, 79), "The same area list lost its admitted boss.");
			PhantomAssertions.assertFalse(child.validAggroRecipientFor(actor(1), authority, 2000, 79), "Area aggression crossed instance identity.");
			PhantomAssertions.assertFalse(child.validAggroRecipientFor(actor(0), authority, 2000, 80), "Area aggression bypassed native minion curse ceiling.");
			PhantomAssertions.assertFalse(child.validAggroRecipientFor(actor(0), null, 2000, 79), "Ordinary tactics admitted raid recipients without authority.");
			final var foreign = new PhantomRaidCombatRequest(1, 7002, 25199, 0, ContentKind.RAID, NpcKind.RAID_BOSS, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, false, 79, 60_000, () -> false);
			PhantomAssertions.assertFalse(child.validAggroRecipientFor(actor(0), foreign, 2000, 79), "Same NPC id authorized another boss object's minion.");
			final var cancelled = new PhantomRaidCombatRequest(1, 7001, 25199, 0, ContentKind.RAID, NpcKind.RAID_BOSS, HASH, PhantomCombatMode.MELEE_PHYSICAL, true, false, 79, 60_000, () -> true);
			PhantomAssertions.assertFalse(child.validAggroRecipientFor(actor(0), cancelled, 2000, 79), "Cancelled raid authority retained area control.");
		});
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

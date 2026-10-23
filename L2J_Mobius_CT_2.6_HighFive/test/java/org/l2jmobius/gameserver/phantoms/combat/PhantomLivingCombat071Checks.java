/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.*;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** DB-free native-capability contracts; this does not claim native damage or geometry. */
public final class PhantomLivingCombat071Checks implements PhantomTestSuite
{
	@Override public String id() { return "living-combat071"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-native-learned-capabilities-survive-authored-catalog-gaps", _ -> learned());
		registry.add("02-native-utility-order-and-current-rank-remain-authoritative", _ -> ranked());
		registry.add("03-pvp-request-cannot-disable-offensive-shot-control", _ -> shots());
	}
	public static void main(String[] args)
	{
		learned(); ranked(); shots();
		System.out.println("OWN071_COMBAT_CONTRACT_PASS cases=3; DB_CONNECTIONS=0; ACTORS=0");
	}
	private static void shots()
	{
		final var request = new PhantomPvpCombatRequest(1, 2, org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source.ACTUAL_ATTACK, "A".repeat(64), PhantomCombatMode.MELEE_PHYSICAL, false, false, 70, 30_000, () -> false);
		PhantomAssertions.assertTrue(request.leaseRequest().useShotsIfAvailable(), "A legacy optional-shot flag disabled mandatory PvP offensive charge control.");
	}
	private static void learned()
	{
		final Lease lease = new Lease();
		final var resolver = new PhantomCombatCapabilityResolver(_ -> List.of());
		final var loadout = resolver.resolvePvp(lease.actorSnapshot(), PhantomCombatMode.RANGED_MAGIC, lease, 4);
		PhantomAssertions.assertTrue(loadout.isPresent(), "Learned native offensive skill was lost solely because authored capability evidence is absent.");
		PhantomAssertions.assertEquals(List.of(new SelectedSkill(900, 7), new SelectedSkill(100, 3)), loadout.orElseThrow().selectedSkills(), "Native learned skill list was replaced by invented catalog levels or numeric-ID order.");
	}
	private static void ranked()
	{
		final Lease lease = new Lease();
		final var evidence = new PhantomCombatCapabilityResolver.CapabilityEvidence(PhantomCombatMode.RANGED_MAGIC.capabilityKey(), 1, List.of(new SelectedSkill(100, 1), new SelectedSkill(999, 1)));
		final var resolver = new PhantomCombatCapabilityResolver(_ -> List.of(evidence));
		final var loadout = resolver.resolvePvp(lease.actorSnapshot(), PhantomCombatMode.RANGED_MAGIC, lease, 1).orElseThrow();
		PhantomAssertions.assertEquals(List.of(new SelectedSkill(900, 7)), loadout.selectedSkills(), "Authored numeric IDs displaced the higher native tactical utility.");
		lease.available = false;
		PhantomAssertions.assertTrue(resolver.resolvePvp(lease.actorSnapshot(), PhantomCombatMode.RANGED_MAGIC, lease, 4).isEmpty(), "Removed native learned skills remained admitted after a class change.");
	}
	private static final class Lease implements PhantomCombatActorLease
	{
		private boolean available = true;
		public List<SelectedSkill> learnedPvpSkills(PhantomCombatMode mode, int limit) { return available && mode.magic() ? List.of(new SelectedSkill(900, 7), new SelectedSkill(100, 3)).stream().limit(limit).toList() : List.of(); }
		@Override public ActorSnapshot actorSnapshot() { return new ActorSnapshot(1, 0, 0, 100, 100, 100, 100, 100, 100, false, false, false, false, false, 0, "IDLE", 0, 0); }
		@Override public int knownSkillLevel(int id) { return !available ? 0 : id == 900 ? 7 : id == 100 ? 3 : 0; }
		@Override public boolean supportsPvpSkill(SelectedSkill skill, PhantomCombatMode mode) { return available && mode.magic() && skill.skillLevel() == knownSkillLevel(skill.skillId()) && knownSkillLevel(skill.skillId()) > 0; }
		@Override public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode) { return false; }
		@Override public TargetSnapshot targetSnapshot(int id) { return null; }
		@Override public List<ThreatObservation> observedAttackers(int limit) { return List.of(); }
		@Override public List<LootCandidate> lootCandidates(int limit, int distance) { return List.of(); }
		@Override public LootObservation observeLoot(LootCandidate candidate) { return LootObservation.INELIGIBLE; }
		@Override public ShotOutcome activateShot(PhantomCombatMode mode) { return ShotOutcome.UNAVAILABLE; }
		@Override public ActionOutcome attack(int id) { return ActionOutcome.REJECTED; }
		@Override public ActionOutcome cast(int id, SelectedSkill skill, PhantomCombatMode mode) { return ActionOutcome.REJECTED; }
		@Override public ActionOutcome pickUp(int id) { return ActionOutcome.REJECTED; }
		@Override public void cancelOwnedAction(PhantomOwnedAction action) { }
		@Override public RespawnOutcome respawnTown() { return RespawnOutcome.REJECTED; }
		@Override public void close() { }
	}
}

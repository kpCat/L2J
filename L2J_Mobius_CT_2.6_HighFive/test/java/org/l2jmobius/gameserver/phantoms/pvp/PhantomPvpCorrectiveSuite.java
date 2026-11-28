/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.util.List;

import org.l2jmobius.gameserver.model.clan.ClanWarService.WarIdentity;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge.MessageKind;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge.Request;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.PvpTacticalView;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpContext.Snapshot;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Candidate;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Counterpart;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.CounterpartKind;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Decision;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Encounter;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.RiskSnapshot;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Stage;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpPersistencePort.StoredEncounter;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomPvpCorrectiveSuite implements PhantomTestSuite
{
	private static final long SEED = 25002511L;
	private static final long SECOND = 1_000_000_000L;
	private static final String HASH_A = "A".repeat(64);
	private static final String HASH_B = "B".repeat(64);
	private static final String HASH_C = "C".repeat(64);
	private static final String HASH_D = "D".repeat(64);

	@Override
	public String id()
	{
		return "pvp-help-pair-cooldown";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 025A used the wrong deterministic seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("131-party-focus-only-reorders-lawful-reactive-sources", _ ->
		{
			final var view = new PvpTacticalView(200, List.of(300), List.of());
			final var focused = pvpTarget(200, true, false, true);
			final var direct = pvpTarget(100, true, false, true);
			final int directPriority = PhantomPvpContext.tacticalPriority(view, Source.ACTUAL_ATTACK, direct, true);
			PhantomAssertions.assertTrue(PhantomPvpContext.tacticalPriority(view, Source.PARTY_DEFENSE, focused, true) < directPriority, "A lawful common focus did not rank ahead of a separate attacker.");
			PhantomAssertions.assertTrue(PhantomPvpContext.tacticalPriority(view, Source.PARTY_DEFENSE, pvpTarget(300, true, false, true), true) > directPriority, "A secondary displaced a current direct attacker.");
			PhantomAssertions.assertTrue(PhantomPvpContext.tacticalPriority(view, Source.PARTY_DEFENSE, focused, false) > directPriority, "Group focus bypassed the canonical admission check.");
			for (var denied : List.of(pvpTarget(200, false, false, true), pvpTarget(200, true, true, true), pvpTarget(200, true, false, false)))
			{
				PhantomAssertions.assertTrue(PhantomPvpContext.tacticalPriority(view, Source.PARTY_DEFENSE, denied, true) > directPriority, "Group focus elevated a neutral, protected or unreachable target.");
			}
			for (var proactive : List.of(Source.FARMING_ESCALATION, Source.REVENGE, Source.CLAN_WAR))
			{
				PhantomAssertions.assertTrue(PhantomPvpContext.tacticalPriority(view, proactive, focused, true) > directPriority, "Group focus changed the priority of a non-reactive cause.");
			}
		});

		registry.add("126-party-defense-cancels-on-lost-exact-authority", _ ->
		{
			final long now = 20 * SECOND;
			final Candidate defense = candidate(Source.PARTY_DEFENSE, counterpart(100), HASH_A, now);
			final Encounter active = PhantomPvpService.initial(defense).withStage(Stage.ENGAGE, "", "", 0, 0, 0, "test.engage");
			final PhantomDomainRef hostile = new PhantomDomainRef("character.object", "100"), member = new PhantomDomainRef("profile", "2");
			PhantomAssertions.assertTrue(PhantomPvpService.needsExactAuthorityCancellation(active, null, now), "Missing protected-member evidence retained a hostile Party lease.");
			PhantomAssertions.assertTrue(PhantomPvpService.needsExactAuthorityCancellation(active, snapshot(candidate(Source.PARTY_DEFENSE, counterpart(100), HASH_B, now), hostile, member), now), "Changed Party generation or manifest retained the previous defense lease.");
			PhantomAssertions.assertTrue(PhantomPvpService.needsExactAuthorityCancellation(active, snapshot(candidate(Source.ACTUAL_ATTACK, counterpart(100), HASH_A, now), hostile, member), now), "A new cause silently reused Party defense authority.");
			PhantomAssertions.assertTrue(PhantomPvpService.needsExactAuthorityCancellation(active, snapshot(defense, hostile, member), defense.expiresLogicalNanos()), "Expired defense evidence retained its action owner.");
			PhantomAssertions.assertFalse(PhantomPvpService.needsExactAuthorityCancellation(active, snapshot(defense, hostile, member), now), "Current exact Party defense was cancelled.");
			PhantomAssertions.assertFalse(PhantomPvpService.needsExactAuthorityCancellation(PhantomPvpService.initial(candidate(Source.ACTUAL_ATTACK, counterpart(100), HASH_A, now)), null, now), "Exact cancellation changed independent actual-attack policy.");
		});
		registry.add("022-clan-war-stops-after-exact-authority-loss", _ ->
		{
			final long now = 20 * SECOND;
			final var outgoing = new WarIdentity(101, 1, 2, "First", "Second");
			final var incoming = new WarIdentity(102, 2, 1, "Second", "First");
			final String authority = PhantomPvpContext.clanWarEvidenceHash(outgoing, incoming);
			final Candidate war = candidate(Source.CLAN_WAR, counterpart(100), authority, now);
			final Encounter active = PhantomPvpService.initial(war).withStage(Stage.ENGAGE, "", "", 1, 0, 0, "test.engage");
			final var hostile = new PhantomDomainRef("character.object", "100");
			final Snapshot exact = snapshot(war, hostile, null);
			final Candidate directAttack = candidate(Source.ACTUAL_ATTACK, counterpart(100), HASH_C, now);
			PhantomAssertions.assertTrue(PhantomPvpService.needsExactAuthorityCancellation(active, null, now), "A ceased clan war retained its action owner.");
			for (String replacement : List.of(
				PhantomPvpContext.clanWarEvidenceHash(new WarIdentity(103, 1, 2, "First", "Second"), incoming),
				PhantomPvpContext.clanWarEvidenceHash(outgoing, new WarIdentity(104, 2, 1, "Second", "First"))))
			{
				final Snapshot replacedWar = snapshot(candidate(Source.CLAN_WAR, counterpart(100), replacement, now), hostile, null);
				PhantomAssertions.assertTrue(PhantomPvpService.needsExactAuthorityCancellation(active, replacedWar, now), "A replacement clan war inherited the revoked action owner.");
			}
			PhantomAssertions.assertFalse(PhantomPvpService.needsExactAuthorityCancellation(active, exact, now), "An unchanged current clan war lost its action owner.");
			PhantomAssertions.assertTrue(PhantomPvpService.needsExactAuthorityCancellation(active, snapshot(directAttack, hostile, null), now), "A direct attack extended a revoked clan-war action owner.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksActivePairCooldown(active, directAttack, now), "Direct defense was treated as proactive clan war.");
		});

		registry.add("01-party-help-selects-only-exact-member-counterpart", _ ->
		{
			final long now = 20 * SECOND;
			final Candidate defense = candidate(Source.PARTY_DEFENSE, counterpart(100), HASH_A, now);
			final StoredEncounter stored = stored(PhantomPvpService.initial(defense));
			final PhantomDomainRef hostile = new PhantomDomainRef("character.object", "100");
			final PhantomDomainRef member = new PhantomDomainRef("profile", "2");
			final Snapshot exact = snapshot(defense, hostile, member);
			final Request help = PhantomPvpService.outboundRequest(stored, exact, MessageKind.HELP_REQUEST, 1);
			PhantomAssertions.assertTrue(help != null, "Exact Goal017 Party-member help evidence was rejected.");
			PhantomAssertions.assertEquals(member, help.counterpart(), "HELP_REQUEST did not use the exact attacked Party member.");
			PhantomAssertions.assertFalse(hostile.equals(help.counterpart()), "Hostile combat target became the PARTY expected counterpart.");
			PhantomAssertions.assertEquals(hostile, PhantomPvpService.outboundRequest(stored, exact, MessageKind.WARNING, 1).counterpart(), "WARNING stopped targeting the hostile counterpart.");
			PhantomAssertions.assertEquals(hostile, PhantomPvpService.outboundRequest(stored, exact, MessageKind.DISENGAGE, 1).counterpart(), "DISENGAGE stopped targeting the hostile counterpart.");

			PhantomAssertions.assertTrue(PhantomPvpService.outboundRequest(stored, snapshot(defense, hostile, null), MessageKind.HELP_REQUEST, 1) == null, "Missing Party-member evidence did not fail closed.");
			PhantomAssertions.assertTrue(PhantomPvpService.outboundRequest(stored, snapshot(defense, hostile, hostile), MessageKind.HELP_REQUEST, 1) == null, "Hostile counterpart substitution did not fail closed.");
			PhantomAssertions.assertTrue(PhantomPvpService.outboundRequest(stored, snapshot(defense, hostile, new PhantomDomainRef("npc", "2")), MessageKind.HELP_REQUEST, 1) == null, "Non-Party-domain help evidence did not fail closed.");
		});

		registry.add("02-different-counterpart-bypasses-cooldown-and-enters-normal-path", context ->
		{
			final PhantomPvpPolicy policy = policy(context);
			final long now = 20 * SECOND;
			final Encounter coolingA = cooling(candidate(Source.FARMING_ESCALATION, counterpart(100), HASH_A, now), now);

			final Candidate attackB = candidate(Source.ACTUAL_ATTACK, counterpart(200), HASH_B, now);
			PhantomAssertions.assertFalse(PhantomPvpService.blocksActivePairCooldown(coolingA, attackB, now), "Cooldown(A) blocked exact ACTUAL_ATTACK B.");
			PhantomAssertions.assertEquals(Decision.ENGAGE, policy.decide(attackB, risk(true), PhantomPvpService.initial(attackB), now).decision(), "Exact ACTUAL_ATTACK B did not remain immediately defensible.");

			final Candidate defenseB = candidate(Source.PARTY_DEFENSE, counterpart(200), HASH_C, now);
			PhantomAssertions.assertFalse(PhantomPvpService.blocksActivePairCooldown(coolingA, defenseB, now), "Cooldown(A) blocked exact PARTY_DEFENSE B.");
			PhantomAssertions.assertEquals(Decision.HELP, policy.decide(defenseB, risk(true), PhantomPvpService.initial(defenseB), now).decision(), "Exact PARTY_DEFENSE B did not remain immediately defensible.");

			final Candidate proactiveB = candidate(Source.REVENGE, counterpart(200), HASH_D, now);
			PhantomAssertions.assertFalse(PhantomPvpService.blocksActivePairCooldown(coolingA, proactiveB, now), "Cooldown(A) blocked a different proactive B.");
			PhantomAssertions.assertEquals(Decision.WARN, policy.decide(proactiveB, risk(false), PhantomPvpService.initial(proactiveB), now).decision(), "Different proactive B did not enter the normal WARN path.");
		});

		registry.add("03-same-pair-proactive-stays-gated-and-reactive-stays-defensible", context ->
		{
			final PhantomPvpPolicy policy = policy(context);
			final long now = 20 * SECOND;
			final Counterpart pairA = counterpart(100);
			final Encounter coolingA = cooling(candidate(Source.FARMING_ESCALATION, pairA, HASH_A, now), now);

			final Candidate proactiveA = candidate(Source.REVENGE, pairA, HASH_B, now);
			PhantomAssertions.assertTrue(PhantomPvpService.blocksActivePairCooldown(coolingA, proactiveA, now), "Same-pair proactive A escaped persisted cooldown.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksActivePairCooldown(coolingA, proactiveA, coolingA.cooldownUntilLogicalNanos()), "Expired pair cooldown continued to block proactive A.");

			final Candidate reactiveA = candidate(Source.ACTUAL_ATTACK, pairA, HASH_C, now);
			PhantomAssertions.assertFalse(PhantomPvpService.blocksActivePairCooldown(coolingA, reactiveA, now), "Fresh same-pair ACTUAL_ATTACK A was treated as proactive revenge.");
			PhantomAssertions.assertEquals(Decision.ENGAGE, policy.decide(reactiveA, risk(true), PhantomPvpService.initial(reactiveA), now).decision(), "Fresh same-pair attack was not immediately defensible.");

			final Candidate partyDefenseA = candidate(Source.PARTY_DEFENSE, pairA, HASH_D, now);
			PhantomAssertions.assertFalse(PhantomPvpService.blocksActivePairCooldown(coolingA, partyDefenseA, now), "Fresh same-pair PARTY_DEFENSE A was cooldown-gated.");
			PhantomAssertions.assertEquals(Decision.HELP, policy.decide(partyDefenseA, risk(true), PhantomPvpService.initial(partyDefenseA), now).decision(), "Fresh same-pair PARTY_DEFENSE A was not immediately defensible.");
		});
		registry.add("04-failed-retreat-does-not-spin-while-risk-is-unchanged", context ->
		{
			final var policy = policy(context);
			final long now = 20 * SECOND;
			final Candidate attack = candidate(Source.ACTUAL_ATTACK, counterpart(100), HASH_A, now);
			final Encounter previous = cooling(attack, now);
			final Encounter failed = previous.withStage(Stage.COOLDOWN, "", "", 0, 0, previous.cooldownUntilLogicalNanos(), "pvp.retreat.route_failed");
			final RiskSnapshot low = new RiskSnapshot(20, 20, 100, 4, 4, 10000, 0, 0, 0, false, false, false, true, true);
			PhantomAssertions.assertTrue(PhantomPvpService.blocksFailedRetreatRetry(policy, failed, attack, low, now), "Unchanged low-resource threat replayed the same failed retreat.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksFailedRetreatRetry(policy, failed, attack, risk(true), now), "Recovered actor lost fresh reactive defense.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksFailedRetreatRetry(policy, failed, candidate(Source.ACTUAL_ATTACK, counterpart(200), HASH_B, now), low, now), "Failed retreat against A suppressed a different attacker B.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksFailedRetreatRetry(policy, failed, attack, low, failed.cooldownUntilLogicalNanos()), "Expired retreat cooldown remained active.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksFailedRetreatRetry(policy, previous, attack, low, now), "Ordinary combat cooldown was misclassified as a failed retreat.");
		});
		registry.add("05-tactical-arrival-does-not-replay-old-damage-or-hide-new-danger", context ->
		{
			final long now = 20 * SECOND; final var attack = candidate(Source.ACTUAL_ATTACK, counterpart(100), HASH_A, now); final var previous = cooling(attack, now);
			final var arrived = previous.withStage(Stage.COOLDOWN, "", "", 0, 0, previous.cooldownUntilLogicalNanos(), "pvp.retreat.tactical_arrived");
			final var arrival = new PhantomPvpContext.RetreatRecovery(counterpart(100), 100, 600);
			final var stationary = new PhantomPvpContext.RetreatRecovery(counterpart(100), 100, 600);
			PhantomAssertions.assertTrue(PhantomPvpService.blocksTacticalRecoveryCooldown(arrived, attack, now, arrival, stationary), "One old native hit repeatedly restarted a successful retreat.");
			PhantomAssertions.assertTrue(PhantomPvpService.blocksTacticalRecoveryCooldown(arrived, attack, now, arrival, new PhantomPvpContext.RetreatRecovery(counterpart(100), 100, 401)), "Less than the existing tactical separation gain was mistaken for renewed pursuit.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksTacticalRecoveryCooldown(arrived, attack, now, arrival, new PhantomPvpContext.RetreatRecovery(counterpart(100), 101, 600)), "A newer canonical incoming-damage witness was suppressed.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksTacticalRecoveryCooldown(arrived, attack, now, arrival, new PhantomPvpContext.RetreatRecovery(counterpart(100), 100, 400)), "A pursuer closing the existing200-unit tactical gain lost defense.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksTacticalRecoveryCooldown(arrived, candidate(Source.ACTUAL_ATTACK, counterpart(200), HASH_B, now), now, arrival, stationary), "Arrival against A suppressed a different attacker B.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksTacticalRecoveryCooldown(arrived, attack, arrived.cooldownUntilLogicalNanos(), arrival, stationary), "Expired native pair cooldown still suppressed defense.");
			PhantomAssertions.assertFalse(PhantomPvpService.blocksTacticalRecoveryCooldown(arrived, attack, now, null, stationary), "Missing native arrival evidence created blanket immunity.");
			final var low = new RiskSnapshot(20, 20, 100, 4, 4, 10000, 0, 0, 0, false, false, false, true, true);
			PhantomAssertions.assertFalse(PhantomPvpService.blocksFailedRetreatRetry(policy(context), arrived, attack, low, now), "Successful tactical arrival incorrectly entered failed-route cooldown and hid fresh damage.");
		});
	}

	private static PhantomPvpPolicy policy(PhantomTestContext context)
	{
		return PhantomPvpPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/pvp/pvp-policy-v1.xml"));
	}

	private static PvpTargetSnapshot pvpTarget(int objectId, boolean autoAttackable, boolean invulnerable, boolean reachable)
	{
		return new PvpTargetSnapshot(objectId, 12, 0, 76, 2, 2, 100, true, true, true, false, false, false, true,
			false, false, false, false, false, false, false, false, false, false, autoAttackable, invulnerable, reachable);
	}

	private static Candidate candidate(Source source, Counterpart counterpart, String authorityHash, long now)
	{
		return new Candidate(1, counterpart, source, authorityHash, now - SECOND, now + (120 * SECOND), true, true, true);
	}

	private static Counterpart counterpart(int objectId)
	{
		return new Counterpart(CounterpartKind.HUMAN_OBJECT, objectId, objectId);
	}

	private static StoredEncounter stored(Encounter encounter)
	{
		return new StoredEncounter(encounter.profileId(), 0, encounter);
	}

	private static Encounter cooling(Candidate candidate, long now)
	{
		return new Encounter(candidate.profileId(), candidate.counterpart(), candidate.source(), candidate.authorityHash(), Stage.COOLDOWN, "", "", 1, candidate.createdLogicalNanos(), now + (600 * SECOND), 0, now + (300 * SECOND), "test.cooldown");
	}

	private static Snapshot snapshot(Candidate candidate, PhantomDomainRef hostile, PhantomDomainRef help)
	{
		return new Snapshot(candidate, risk(true), PhantomCombatMode.MELEE_PHYSICAL, SubjectRef.character(candidate.counterpart().currentObjectId()), hostile, help, HASH_A);
	}

	private static RiskSnapshot risk(boolean autoAttackable)
	{
		return new RiskSnapshot(100, 100, 100, 4, 4, 10000, autoAttackable ? 0 : 1000, 0, 0, false, false, false, true, autoAttackable);
	}
}

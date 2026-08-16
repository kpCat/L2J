/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge.MessageKind;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge.Request;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
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
	}

	private static PhantomPvpPolicy policy(PhantomTestContext context)
	{
		return PhantomPvpPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/pvp/pvp-policy-v1.xml"));
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

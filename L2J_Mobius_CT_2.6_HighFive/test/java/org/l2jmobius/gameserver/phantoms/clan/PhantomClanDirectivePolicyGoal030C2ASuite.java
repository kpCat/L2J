/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.l2jmobius.gameserver.model.chat.ChatObservationService.DeliveredObservation;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchDescriptor;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Definition;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Effect;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Kind;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Outcome;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.ModifierDefinition;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.ModifierWeight;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomClanDirectivePolicyGoal030C2ASuite implements PhantomTestSuite
{
	private static final long SEED = 30003032L;
	private PhantomClanDirectiveCatalog _directives;
	private PhantomSocialCatalog _social;

	@Override
	public String id()
	{
		return "clan-directive-policy-goal030c2a";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030C2A policy suite used the wrong seed.");
		_directives = PhantomClanDirectiveCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/clan/high-five-clan-directives-v1.xml"));
		_social = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-strict-catalog-aliases-and-tuning", this::strictCatalog);
		registry.add("02-bounded-normalization-and-unknown", this::normalization);
		registry.add("03-exact-accept-defer-refuse-thresholds", this::thresholds);
		registry.add("04-loyalty-trust-respect-accept", this::loyaltyAccepts);
		registry.add("05-anger-distrust-hostility-refuse", this::distrustRefuses);
		registry.add("06-competence-reliability-influence", this::competenceInfluence);
		registry.add("07-required-social-events-fail-closed", this::requiredSocialEvents);
		registry.add("08-generated-origin-is-ineligible", this::generatedIgnored);
	}

	private void strictCatalog(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(3, _directives.directives().size(), "Directive kind count changed.");
		assertDefinition(Kind.ASSEMBLE, 600, Effect.ACTIVE, 120_000);
		assertDefinition(Kind.STANDBY, 250, Effect.WARM, 300_000);
		assertDefinition(Kind.DISMISS, 1000, Effect.WITHDRAW, 0);
		assertAliases(Kind.ASSEMBLE, "сбор", "го сбор", "сбор клана", "все на сбор", "онлайн на сбор", "sbor", "go sbor");
		assertAliases(Kind.STANDBY, "готовность", "будьте готовы", "держим онлайн", "standby");
		assertAliases(Kind.DISMISS, "отбой", "расходимся", "сбор окончен", "otboy");
		context.record("goal030c2a.directiveCatalogHash", _directives.hash());
		context.record("goal030c2a.directiveTuning", "ASSEMBLE=600/ACTIVE/120;STANDBY=250/WARM/300;DISMISS=1000/WITHDRAW");
	}

	private void normalization(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(Kind.ASSEMBLE, _directives.parse("  ГО,   СБОР!!! ").orElseThrow().kind(), "Whitespace/case/punctuation normalization changed.");
		PhantomAssertions.assertEquals(Kind.STANDBY, _directives.parse("ГОТОВНОСТЬ!!!").orElseThrow().kind(), "Cyrillic normalization changed.");
		PhantomAssertions.assertEquals(Kind.DISMISS, _directives.parse("ОТБОЙ...").orElseThrow().kind(), "Dismiss punctuation normalization changed.");
		PhantomAssertions.assertTrue(_directives.parse("сбор готовность").isEmpty(), "Unknown combined phrase became a directive.");
		PhantomAssertions.assertTrue(_directives.parse("сбор 🙂").isEmpty(), "Unsupported symbol became a directive.");
		PhantomAssertions.assertTrue(_directives.parse("x".repeat(129)).isEmpty(), "Overlong input became a directive.");
		context.record("goal030c2a.normalization", "trim/lower/ws/punctuation/ё->е/bounded");
	}

	private void thresholds(PhantomTestContext context)
	{
		final Definition assemble = _directives.require(Kind.ASSEMBLE);
		PhantomAssertions.assertEquals(Outcome.ACCEPT, PhantomClanDirectiveModel.decide(assemble, -300).outcome(), "Score +300 did not ACCEPT.");
		PhantomAssertions.assertEquals(Outcome.DEFER, PhantomClanDirectiveModel.decide(assemble, -301).outcome(), "Score +299 did not DEFER.");
		PhantomAssertions.assertEquals(Outcome.DEFER, PhantomClanDirectiveModel.decide(assemble, -899).outcome(), "Score -299 did not DEFER.");
		PhantomAssertions.assertEquals(Outcome.REFUSE, PhantomClanDirectiveModel.decide(assemble, -900).outcome(), "Score -300 did not REFUSE.");
		PhantomAssertions.assertEquals(Outcome.ACCEPT, PhantomClanDirectiveModel.decide(_directives.require(Kind.STANDBY), 50).outcome(), "STANDBY score +300 did not ACCEPT.");
		context.record("goal030c2a.thresholds", "ACCEPT>=300;DEFER=-299..299;REFUSE<=-300");
	}

	private void loyaltyAccepts(PhantomTestContext context)
	{
		final Map<String, Integer> inputs = Map.of(
			"trait.loyalty", -10000,
			"relationship.trust", 10000,
			"relationship.respect", 10000,
			"reputation.competence", 10000,
			"reputation.reliability", 10000);
		final int modifier = modifier(inputs);
		final var decision = PhantomClanDirectiveModel.decide(_directives.require(Kind.ASSEMBLE), modifier);
		PhantomAssertions.assertEquals(1400, modifier, "Positive authority relationship modifier changed.");
		PhantomAssertions.assertEquals(Outcome.ACCEPT, decision.outcome(), "Strong trust/respect did not overcome low loyalty.");
		context.record("goal030c2a.acceptScore", decision.score());
	}

	private void distrustRefuses(PhantomTestContext context)
	{
		final Map<String, Integer> inputs = Map.of(
			"relationship.trust", -10000,
			"relationship.respect", -10000,
			"reputation.competence", -10000,
			"reputation.reliability", -10000,
			"relationship.anger", 10000,
			"relationship.rivalry", 10000,
			"reputation.hostility", 10000);
		final int modifier = modifier(inputs);
		final var decision = PhantomClanDirectiveModel.decide(_directives.require(Kind.ASSEMBLE), modifier);
		PhantomAssertions.assertEquals(-3000, modifier, "Negative social modifier did not clamp at -3000.");
		PhantomAssertions.assertEquals(Outcome.REFUSE, decision.outcome(), "Anger/distrust fixture did not REFUSE.");
		context.record("goal030c2a.refuseScore", decision.score());
	}

	private void competenceInfluence(PhantomTestContext context)
	{
		final int positive = modifier(Map.of("reputation.competence", 10000, "reputation.reliability", 10000));
		final int negative = modifier(Map.of("reputation.competence", -10000, "reputation.reliability", -10000));
		PhantomAssertions.assertEquals(900, positive, "Positive competence/reliability modifier changed.");
		PhantomAssertions.assertEquals(-900, negative, "Negative competence/reliability modifier changed.");
		PhantomAssertions.assertEquals(Outcome.ACCEPT, PhantomClanDirectiveModel.decide(_directives.require(Kind.STANDBY), positive).outcome(), "Competent leader did not gain STANDBY acceptance.");
		PhantomAssertions.assertEquals(Outcome.REFUSE, PhantomClanDirectiveModel.decide(_directives.require(Kind.STANDBY), negative).outcome(), "Unreliable leader did not permit STANDBY refusal.");
		final Map<String, Integer> expectedWeights = new TreeMap<>();
		expectedWeights.put("trait.loyalty", 900);
		expectedWeights.put("relationship.trust", 700);
		expectedWeights.put("relationship.respect", 700);
		expectedWeights.put("reputation.competence", 500);
		expectedWeights.put("reputation.reliability", 400);
		expectedWeights.put("relationship.anger", -700);
		expectedWeights.put("relationship.rivalry", -500);
		expectedWeights.put("reputation.hostility", -600);
		final Map<String, Integer> actualWeights = new TreeMap<>();
		for (ModifierWeight weight : obedience().weights())
		{
			actualWeights.put(weight.sourceKey(), weight.weight());
		}
		PhantomAssertions.assertEquals(expectedWeights, actualWeights, "Obedience modifier weights changed.");
		context.record("goal030c2a.modifierWeights", actualWeights);
	}

	private void requiredSocialEvents(PhantomTestContext context) throws Exception
	{
		final String source = Files.readString(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"), StandardCharsets.UTF_8);
		rejectSocial(context, "expelled", removeEvent(source, "clan.member.expelled"));
		rejectSocial(context, "accepted", removeEvent(source, "clan.directive.accepted"));
		rejectSocial(context, "refused", removeEvent(source, "clan.directive.refused"));
		context.record("goal030c2a.requiredEventNegativeCases", 3);
	}

	private void generatedIgnored(PhantomTestContext context)
	{
		final DispatchDescriptor generated = new DispatchDescriptor(1, Origin.PHANTOM_GENERATED, 100, "Leader", ChatType.CLAN, "", "сбор", 1);
		final DispatchDescriptor clientClan = new DispatchDescriptor(2, Origin.CLIENT_CHAT, 100, "Leader", ChatType.CLAN, "", "сбор", 1);
		final DispatchDescriptor clientGeneral = new DispatchDescriptor(3, Origin.CLIENT_CHAT, 100, "Leader", ChatType.GENERAL, "", "сбор", 1);
		PhantomAssertions.assertFalse(PhantomClanDirectiveService.eligibleDelivery(new DeliveredObservation(generated, 200, "Recipient")), "Generated CLAN delivery became eligible.");
		PhantomAssertions.assertTrue(PhantomClanDirectiveService.eligibleDelivery(new DeliveredObservation(clientClan, 200, "Recipient")), "CLIENT_CHAT CLAN delivery is ineligible.");
		PhantomAssertions.assertFalse(PhantomClanDirectiveService.eligibleDelivery(new DeliveredObservation(clientGeneral, 200, "Recipient")), "Non-CLAN delivery became eligible.");
		context.record("goal030c2a.generatedIgnored", true);
	}

	private void assertDefinition(Kind kind, int baseScore, Effect effect, long ttlMillis)
	{
		final Definition definition = _directives.require(kind);
		PhantomAssertions.assertEquals(baseScore, definition.baseScore(), kind + " base score changed.");
		PhantomAssertions.assertEquals(effect, definition.effect(), kind + " effect changed.");
		PhantomAssertions.assertEquals(ttlMillis, definition.ttlMillis(), kind + " TTL changed.");
	}

	private void assertAliases(Kind kind, String... aliases)
	{
		final Set<String> required = Set.of(aliases);
		PhantomAssertions.assertTrue(_directives.require(kind).aliases().containsAll(required), kind + " aliases are incomplete.");
		for (String alias : aliases)
		{
			PhantomAssertions.assertEquals(kind, _directives.parse(alias).orElseThrow().kind(), "Alias resolves to the wrong directive: " + alias);
		}
	}

	private ModifierDefinition obedience()
	{
		return _social.requireModifier("clan.directive.obedience");
	}

	private int modifier(Map<String, Integer> inputs)
	{
		long total = 0;
		for (ModifierWeight weight : obedience().weights())
		{
			final int input = inputs.getOrDefault(weight.sourceKey(), 0);
			total += ((long) input * weight.weight()) / 10000L;
		}
		return (int) Math.max(obedience().minimum(), Math.min(obedience().maximum(), total));
	}

	private static String removeEvent(String source, String key)
	{
		final String marker = " key=\"" + key + "\"";
		final int markerIndex = uniqueIndex(source, marker);
		final int startIndex = source.lastIndexOf("\t\t<event ", markerIndex);
		final String end = "\t\t</event>";
		final int endIndex = source.indexOf(end, markerIndex);
		if ((startIndex < 0) || (endIndex < 0))
		{
			throw new IllegalArgumentException("Social event mutation anchor is malformed.");
		}
		int after = endIndex + end.length();
		while ((after < source.length()) && ((source.charAt(after) == '\r') || (source.charAt(after) == '\n')))
		{
			after++;
		}
		return source.substring(0, startIndex) + source.substring(after);
	}
	private static int uniqueIndex(String source, String anchor)
	{
		final int index = source.indexOf(anchor);
		if ((index < 0) || (source.indexOf(anchor, index + anchor.length()) >= 0))
		{
			throw new IllegalArgumentException("Test mutation anchor is missing or duplicated.");
		}
		return index;
	}

	private static void rejectSocial(PhantomTestContext context, String name, String content) throws Exception
	{
		final Path path = Files.createTempFile(context.reportsDirectory(), "social-030c2a-invalid-" + name + '-', ".xml");
		try
		{
			Files.writeString(path, content, StandardCharsets.UTF_8);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSocialCatalog.load(path), "Missing required Goal030C2A social event was accepted: " + name);
		}
		finally
		{
			Files.deleteIfExists(path);
		}
	}
}

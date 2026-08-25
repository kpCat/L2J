/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.phantoms.pvp.PhantomKarmaRecoveryPolicy.Decision;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomKarmaRecoveryPolicy.Reason;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomKarmaRecoveryPolicy.Snapshot;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomKarmaRecoveryPolicyGoal030C2BSuite implements PhantomTestSuite
{
	private static final long SEED = 30003034L;

	@Override
	public String id()
	{
		return "karma-recovery-policy-goal030c2b";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030C2B policy seed changed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-clean-is-normal", context -> assertDecision(context, Source.ACTUAL_ATTACK, snapshot(0, 0, 0, false, false), Decision.NORMAL));
		registry.add("02-red-proactive-is-suppressed", context ->
		{
			assertDecision(context, Source.FARMING_ESCALATION, snapshot(800, 0, 0, false, false), Decision.SUPPRESS_PROACTIVE);
			assertDecision(context, Source.REVENGE, snapshot(800, 0, 0, false, false), Decision.SUPPRESS_PROACTIVE);
		});
		registry.add("03-clan-war-proactive-is-normal", context -> assertDecision(context, Source.REVENGE, snapshot(800, 0, 0, false, true), Decision.NORMAL));
		registry.add("04-party-defense-is-normal", context -> assertDecision(context, Source.PARTY_DEFENSE, snapshot(800, 0, 0, false, false), Decision.NORMAL));
		registry.add("05-safe-actual-attack-yields", context -> assertDecision(context, Source.ACTUAL_ATTACK, snapshot(800, 0, 0, false, false), Decision.YIELD));
		registry.add("06-xp-debt-blocks-yield", context ->
		{
			final var result = policy(context).evaluate(Source.ACTUAL_ATTACK, true, snapshot(600, 25, 0, false, false));
			PhantomAssertions.assertEquals(Decision.NORMAL, result.decision(), "XP debt did not block yield.");
			PhantomAssertions.assertEquals(Reason.XP_DEBT, result.reason(), "XP debt reason was lost.");
		});
		registry.add("07-party-is-unsafe", context -> assertUnsafe(context, snapshot(600, 0, 0, true, false)));
		registry.add("08-drop-exposure-is-unsafe", context -> assertUnsafe(context, snapshot(600, 0, 5000, false, false)));
		registry.add("09-native-prediction-is-exact", _ ->
		{
			PhantomAssertions.assertEquals(0, L2jPhantomKarmaRecoveryContext.predictedKarmaAfterNativeDeath(199), "199 karma prediction changed.");
			PhantomAssertions.assertEquals(150, L2jPhantomKarmaRecoveryContext.predictedKarmaAfterNativeDeath(200), "200 karma prediction changed.");
			PhantomAssertions.assertEquals(600, L2jPhantomKarmaRecoveryContext.predictedKarmaAfterNativeDeath(800), "800 karma prediction changed.");
		});
		registry.add("10-strict-xml-fails-closed", context ->
		{
			final Path invalid = context.reportsDirectory().resolve("karma-recovery-invalid.xml");
			Files.createDirectories(invalid.getParent());
			try
			{
				Files.writeString(invalid, "<karmaRecoveryPolicy id=\"bad\" version=\"1\" suppressProactiveNonWar=\"true\" yieldToActualAttack=\"true\" allowYieldInClanWar=\"false\" allowYieldWhileInParty=\"false\" requireExperienceRecovered=\"true\" maxIntentionalDeathDropRiskBasisPoints=\"10001\"/>", StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomKarmaRecoveryPolicy.load(invalid), "Out-of-range policy was accepted.");
				Files.writeString(invalid, "<karmaRecoveryPolicy id=\"bad\" version=\"1\" suppressProactiveNonWar=\"true\" yieldToActualAttack=\"true\" allowYieldInClanWar=\"false\" allowYieldWhileInParty=\"false\" requireExperienceRecovered=\"true\" maxIntentionalDeathDropRiskBasisPoints=\"0\" unknown=\"x\"/>", StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomKarmaRecoveryPolicy.load(invalid), "Unknown policy attribute was accepted.");
				Files.writeString(invalid, "<karmaRecoveryPolicy", StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomKarmaRecoveryPolicy.load(invalid), "Malformed policy was accepted.");
			}
			finally
			{
				Files.deleteIfExists(invalid);
			}
		});
	}

	private static void assertDecision(PhantomTestContext context, Source source, Snapshot snapshot, Decision expected)
	{
		PhantomAssertions.assertEquals(expected, policy(context).evaluate(source, true, snapshot).decision(), "Unexpected karma recovery decision for " + source + ".");
	}

	private static void assertUnsafe(PhantomTestContext context, Snapshot snapshot)
	{
		final var result = policy(context).evaluate(Source.ACTUAL_ATTACK, true, snapshot);
		PhantomAssertions.assertEquals(Decision.NORMAL, result.decision(), "Unsafe recovery context yielded.");
		PhantomAssertions.assertEquals(Reason.UNSAFE, result.reason(), "Unsafe recovery reason was lost.");
	}

	private static PhantomKarmaRecoveryPolicy policy(PhantomTestContext context)
	{
		return PhantomKarmaRecoveryPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/pvp/high-five-karma-recovery-v1.xml"));
	}

	private static Snapshot snapshot(int karma, long debt, int dropRisk, boolean party, boolean war)
	{
		final long current = 1_000_000;
		return new Snapshot(true, karma, 0, current, current + debt, debt, dropRisk, L2jPhantomKarmaRecoveryContext.predictedKarmaAfterNativeDeath(karma), party, war);
	}
}

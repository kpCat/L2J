/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhantomPopulationResetDocumentationGoal032Suite implements PhantomTestSuite
{
	private static final long SEED = 32003203L;
	private static final Pattern CONFIG_KEY = Pattern.compile("^([A-Za-z][A-Za-z0-9]+)\\s*=", Pattern.MULTILINE);
	private static final Set<String> GOAL032_CONFIG_KEYS = Set.of(
		"EnablePhantomSystem",
		"EnablePhantomDiagnostics",
		"MaxMaterializedPhantoms",
		"MaxScheduledPhantomProfiles",
		"PhantomSchedulerPulseMillis",
		"PhantomSchedulerProfilesPerPulse",
		"PhantomPopulationTarget",
		"PhantomPopulationActiveTarget",
		"PhantomPopulationCreationInFlight",
		"PhantomPopulationBoundariesPerPulse",
		"PhantomPartyOperationsPerPulse",
		"PhantomSocialCacheProfiles",
		"PhantomPopulationTimeZone");

	@Override
	public String id()
	{
		return "phantom-population-reset-documentation-goal032";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-config-command-and-roadmap-parity", this::testParity);
	}

	private void testParity(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal032 documentation suite used the wrong seed.");
		final Path root = context.moduleRoot();
		final String shipped = read(root, "dist/game/config/Custom/PhantomPlayers.ini");
		final String preset = read(root, "docs/phantoms/examples/PhantomPlayers.local-play.ini");
		final String parser = read(root, "java/org/l2jmobius/gameserver/config/custom/PhantomPlayersConfig.java");
		final String tuning = read(root, "docs/phantoms/PHANTOM_OPERATOR_TUNING_RU.md");
		final String quickStart = read(root, "docs/phantoms/PHANTOM_QUICKSTART_RU.md");
		final String report = read(root, "docs/phantoms/reports/032-phantom-reset-operator-control.md");
		final String admin = read(root, "dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java");
		final String gameServer = read(root, "java/org/l2jmobius/gameserver/GameServer.java");

		final Set<String> shippedKeys = keys(shipped);
		final Set<String> presetKeys = keys(preset);
		PhantomAssertions.assertTrue(shippedKeys.containsAll(GOAL032_CONFIG_KEYS), "Shipped config lost a Goal032-owned key.");
		PhantomAssertions.assertTrue(presetKeys.containsAll(GOAL032_CONFIG_KEYS), "Local-play preset lost a Goal032-owned key.");
		for (String key : GOAL032_CONFIG_KEYS)
		{
			PhantomAssertions.assertEquals(1, occurrences(shipped, key), "Shipped config duplicates Goal032-owned key " + key + ".");
			PhantomAssertions.assertEquals(1, occurrences(preset, key), "Local-play preset duplicates Goal032-owned key " + key + ".");
			PhantomAssertions.assertTrue(parser.contains("\"" + key + "\""), "Production parser does not reference shipped key " + key + ".");
			PhantomAssertions.assertTrue(tuning.contains("`" + key + "`"), "Tuning guide omits Goal032-owned key " + key + ".");
		}
		PhantomAssertions.assertTrue(shipped.contains("EnablePhantomSystem = False") && shipped.contains("PhantomPopulationTarget = 0") && shipped.contains("PhantomPopulationActiveTarget = 0"), "Shipped fail-closed defaults changed.");
		PhantomAssertions.assertTrue(preset.contains("EnablePhantomSystem = True") && preset.contains("MaxMaterializedPhantoms = 32") && preset.contains("PhantomPopulationTarget = 10") && preset.contains("PhantomPopulationActiveTarget = 5"), "Goal032 local-play core population/cap values drifted.");
		PhantomAssertions.assertTrue(tuning.contains("## Что можно крутить для количества ботов") && tuning.contains("## Что относится только к производительности") && tuning.contains("## Что не надо крутить без причины") && tuning.contains("## Безопасный reset/reseed"), "Required Goal032 tuning/reset sections are missing.");

		for (String command : List.of("//phantom reset preview", "//phantom reset confirm <TOKEN>", "//phantom reset cancel"))
		{
			PhantomAssertions.assertTrue(quickStart.contains(command), "QuickStart omits reset command " + command + ".");
		}
		PhantomAssertions.assertTrue(quickStart.contains("//phantom reset confirm <TOKEN> reseed"), "QuickStart omits reset + reseed.");
		PhantomAssertions.assertTrue(quickStart.contains("не является «машиной времени»") && quickStart.contains("shared ownership") && quickStart.contains("production DB"), "QuickStart omits preservation/blocker/DB semantics.");
		PhantomAssertions.assertTrue(admin.contains("arguments.equals(\"reset preview\")") && admin.contains("arguments.startsWith(\"reset confirm \")") && admin.contains("arguments.equals(\"reset cancel\")"), "AdminPhantom reset routes drifted.");
		PhantomAssertions.assertFalse(gameServer.contains("operatorReset"), "GameServer startup contains an automatic reset path.");

		for (String token : List.of(
			"`SUCCESS`",
			"every one of the 13 keys",
			"deferred to Goal 033",
			"//phantom reset preview",
			"//phantom reset confirm <TOKEN>",
			"//phantom reset confirm <TOKEN> reseed",
			"//phantom reset cancel",
			"Any SQL/runtime failure rolls back",
			"No startup path invokes reset",
			"Production database `l2jmobiush5` was not opened"))
		{
			PhantomAssertions.assertTrue(report.contains(token), "Historical Goal032 SUCCESS report lost accepted token: " + token);
		}

		context.record("goal032.documentation.configKeys", GOAL032_CONFIG_KEYS);
		context.record("goal032.documentation.commands", "preview,confirm,confirm-reseed,cancel");
		context.record("goal032.documentation.history", "SUCCESS,ownedKeys=13,ecology=deferred-goal033");
	}

	private static String read(Path root, String relative) throws Exception
	{
		return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
	}

	private static Set<String> keys(String text)
	{
		final Set<String> result = new LinkedHashSet<>();
		final Matcher matcher = CONFIG_KEY.matcher(text);
		while (matcher.find())
		{
			result.add(matcher.group(1));
		}
		return Set.copyOf(result);
	}

	private static int occurrences(String text, String key)
	{
		int result = 0;
		final Matcher matcher = CONFIG_KEY.matcher(text);
		while (matcher.find())
		{
			if (key.equals(matcher.group(1)))
			{
				result++;
			}
		}
		return result;
	}
}

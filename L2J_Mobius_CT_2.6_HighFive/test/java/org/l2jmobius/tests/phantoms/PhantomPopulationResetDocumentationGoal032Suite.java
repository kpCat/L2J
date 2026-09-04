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
	private static final List<String> ROADMAP_TAIL = List.of("Goal032", "Goal033", "Goal034", "Goal035", "Goal036", "Goal037");

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
		final String roadmap = read(root, "docs/PHANTOM_BOTS_ROADMAP.md");
		final String master = read(root, "PHANTOM_DEVELOPMENT_MASTER_PLAN.md");
		final String status = read(root, "docs/phantoms/PHANTOM_CURRENT_STATUS.md");
		final String handoff = read(root, "docs/phantoms/NEW_DIALOG_START_MESSAGE.txt");
		final String admin = read(root, "dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java");
		final String gameServer = read(root, "java/org/l2jmobius/gameserver/GameServer.java");

		final Set<String> shippedKeys = keys(shipped);
		PhantomAssertions.assertEquals(13, shippedKeys.size(), "Shipped Phantom config key inventory changed.");
		PhantomAssertions.assertEquals(shippedKeys, keys(preset), "Local-play preset key inventory differs from shipped config.");
		for (String key : shippedKeys)
		{
			PhantomAssertions.assertTrue(parser.contains("\"" + key + "\""), "Production parser does not reference shipped key " + key + ".");
			PhantomAssertions.assertTrue(tuning.contains("`" + key + "`"), "Tuning guide omits shipped key " + key + ".");
		}
		PhantomAssertions.assertTrue(shipped.contains("EnablePhantomSystem = False") && shipped.contains("PhantomPopulationTarget = 0") && shipped.contains("PhantomPopulationActiveTarget = 0"), "Shipped fail-closed defaults changed.");
		PhantomAssertions.assertTrue(tuning.contains("## Что можно крутить для количества ботов") && tuning.contains("## Что относится только к производительности") && tuning.contains("## Что не надо крутить без причины") && tuning.contains("## Каких gameplay-настроек пока нет"), "Required tuning guide sections are missing.");
		PhantomAssertions.assertTrue(tuning.contains("Goal033 — Living population ecology"), "Tuning guide does not defer ecology knobs to Goal033.");

		for (String command : List.of("//phantom reset preview", "//phantom reset confirm <TOKEN>", "//phantom reset cancel"))
		{
			PhantomAssertions.assertTrue(quickStart.contains(command), "QuickStart omits reset command " + command + ".");
		}
		PhantomAssertions.assertTrue(quickStart.contains("//phantom reset confirm <TOKEN> reseed"), "QuickStart omits reset + reseed.");
		PhantomAssertions.assertTrue(quickStart.contains("не является «машиной времени»") && quickStart.contains("shared ownership") && quickStart.contains("production DB"), "QuickStart omits preservation/blocker/DB semantics.");
		PhantomAssertions.assertTrue(admin.contains("arguments.equals(\"reset preview\")") && admin.contains("arguments.startsWith(\"reset confirm \")") && admin.contains("arguments.equals(\"reset cancel\")"), "AdminPhantom reset routes drifted.");
		PhantomAssertions.assertFalse(gameServer.contains("operatorReset"), "GameServer startup contains an automatic reset path.");

		assertRoadmapOrder(roadmap);
		assertRoadmapOrder(master);
		assertRoadmapOrder(status);
		assertRoadmapOrder(handoff);
		PhantomAssertions.assertTrue(roadmap.contains("Версия дорожной карты:** 3"), "Canonical roadmap is not v3.");
		PhantomAssertions.assertTrue(roadmap.contains("FEATURE_COMPLETE_FOR_DECLARED_SCOPE") && roadmap.contains("ручная игра"), "Roadmap v3 omits finite freeze or QA philosophy.");
		PhantomAssertions.assertTrue(status.contains("Goal032 Phantom-only reset/reseed") && status.contains("Следующий Goal033 ecology"), "Current status does not identify Goal032/Goal033.");
		PhantomAssertions.assertTrue(handoff.contains("Следующий реальный шаг: **Goal033 — Living population ecology**"), "Handoff does not lead to Goal033.");

		context.record("goal032.documentation.configKeys", shippedKeys);
		context.record("goal032.documentation.commands", "preview,confirm,confirm-reseed,cancel");
		context.record("goal032.documentation.roadmap", "v3:032>033>034>035>036>037,finite=true");
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

	private static void assertRoadmapOrder(String text)
	{
		int previous = -1;
		for (String goal : ROADMAP_TAIL)
		{
			final int current = text.indexOf(goal, previous + 1);
			PhantomAssertions.assertTrue(current > previous, "Roadmap tail order is missing or invalid at " + goal + ".");
			previous = current;
		}
	}
}

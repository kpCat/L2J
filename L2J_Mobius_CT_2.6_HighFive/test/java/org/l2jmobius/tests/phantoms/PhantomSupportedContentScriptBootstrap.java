/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;

import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Test-only bounded native-owner bootstrap shared by Goal036 and production-composed suites. */
public final class PhantomSupportedContentScriptBootstrap
{
	private static int _invocations;

	private PhantomSupportedContentScriptBootstrap()
	{
	}

	public static synchronized void loadGoal036Owners(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(0, _invocations, "Supported-content scripts were bootstrapped more than once in one suite JVM.");
		_invocations++;

		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		ScriptEngine.getInstance().executeScript(Path.of("quests/QuestMasterHandler.java"));
		ScriptEngine.getInstance().executeScript(Path.of("village_master/ElfHumanFighterChange1/ElfHumanFighterChange1.java"));
		ScriptEngine.getInstance().executeScript(Path.of("instances/Kamaloka/Kamaloka.java"));
		ScriptEngine.getInstance().executeScript(Path.of("instances/PailakaSongOfIceAndFire/PailakaSongOfIceAndFire.java"));

		assertQuestOwner(102, "Q00102_SeaOfSporesFever", "quests.Q00102_SeaOfSporesFever.Q00102_SeaOfSporesFever");
		assertQuestOwner(152, "Q00152_ShardsOfGolem", "quests.Q00152_ShardsOfGolem.Q00152_ShardsOfGolem");
		assertQuestOwner(401, "Q00401_PathOfTheWarrior", "quests.Q00401_PathOfTheWarrior.Q00401_PathOfTheWarrior");
		assertQuestOwner(128, "Q00128_PailakaSongOfIceAndFire", "quests.Q00128_PailakaSongOfIceAndFire.Q00128_PailakaSongOfIceAndFire");
		assertScriptOwner("ElfHumanFighterChange1", "village_master.ElfHumanFighterChange1.ElfHumanFighterChange1");
		assertScriptOwner("Kamaloka", "instances.Kamaloka.Kamaloka");
		assertScriptOwner("PailakaSongOfIceAndFire", "instances.PailakaSongOfIceAndFire.PailakaSongOfIceAndFire");

		context.record("supportedContentBootstrap.invocations", _invocations);
		context.record("supportedContentBootstrap.sequence", "MASTER_HANDLER_FILE,quests/QuestMasterHandler.java,village_master/ElfHumanFighterChange1/ElfHumanFighterChange1.java,instances/Kamaloka/Kamaloka.java,instances/PailakaSongOfIceAndFire/PailakaSongOfIceAndFire.java");
		context.record("supportedContentBootstrap.owners", "Q102,Q152,Q401,Q128,ElfHumanFighterChange1,Kamaloka,PailakaSongOfIceAndFire");
	}

	private static void assertQuestOwner(int questId, String scriptName, String className)
	{
		final Quest quest = ScriptManager.getInstance().getQuest(questId);
		PhantomAssertions.assertTrue((quest != null) && (quest == ScriptManager.getInstance().getScript(scriptName)), "Supported quest owner is not loaded with its exact identity: " + scriptName);
		PhantomAssertions.assertEquals(questId, quest.getId(), "Supported quest owner ID drifted: " + scriptName);
		PhantomAssertions.assertEquals(className, quest.getClass().getName(), "Supported quest owner class drifted: " + scriptName);
	}

	private static void assertScriptOwner(String scriptName, String className)
	{
		final Quest script = ScriptManager.getInstance().getScript(scriptName);
		PhantomAssertions.assertTrue(script != null, "Supported script owner is not loaded: " + scriptName);
		PhantomAssertions.assertEquals(className, script.getClass().getName(), "Supported script owner class drifted: " + scriptName);
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.model.script.State;
import org.l2jmobius.gameserver.phantoms.PhantomUtf8SourceHash;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Q02 stock script fixture. The caller owns native combat, barriers, checkpoint and Player cleanup. */
public final class PhantomM1Q266NativeFixture
{
	public static final int MONSTER_ID = 20537;
	public static final int ITEM_ID = 1334;
	public static final long STARTING_COUNT = 98;
	public static final long COMPLETED_COUNT = 100;
	private static final String QUEST_NAME = "Q00266_PleasOfPixies";
	private static final String QUEST_CLASS = "quests.Q00266_PleasOfPixies.Q00266_PleasOfPixies";
	private static final String QUEST_SOURCE = "dist/game/data/scripts/quests/Q00266_PleasOfPixies/Q00266_PleasOfPixies.java";
	private static final String QUEST_SOURCE_SHA256 = "3f1866fe600759728927a7eb7818a72a96484da76d26ff32eabb2b8f9964b009";
	private static Quest _quest;

	private PhantomM1Q266NativeFixture()
	{
	}

	/** Call once after the guarded headless environment and existing MasterHandler bootstrap. */
	public static synchronized void load(PhantomTestContext context) throws Exception
	{
		requireGuardedContext(context);
		if (_quest == null)
		{
			PhantomAssertions.assertEquals(null, ScriptManager.getInstance().getQuest(266), "Q02 would replace an already loaded stock quest owner.");
			final Path source = context.moduleRoot().resolve(QUEST_SOURCE);
			PhantomAssertions.assertEquals(QUEST_SOURCE_SHA256, PhantomUtf8SourceHash.sha256(Files.readAllBytes(source)), "Q02 stock quest source changed; re-audit its deterministic branch.");
			final Path loader = context.moduleRoot().resolve("test/resources/phantoms/M1Q266Bootstrap.java");
			ScriptEngine.getInstance().executeScript(loader);
			_quest = ScriptManager.getInstance().getQuest(266);
			context.record("Q02.bootstrap.loader", loader);
			context.record("Q02.bootstrap.stockSourceSha256", QUEST_SOURCE_SHA256);
		}
		final Quest quest = owner();
		PhantomAssertions.assertTrue(quest.getRegisteredIds(ListenerRegisterType.NPC).contains(MONSTER_ID), "Q02 stock owner did not register its deterministic kill NPC.");
		context.record("Q02.bootstrap.owner", quest.getClass().getName());
	}

	/** Seed the isolated ordinary fixture before its canonical baseline and materialization. */
	public static void prepare(PhantomTestContext context, Player player)
	{
		requireGuardedContext(context);
		requireStockRate();
		PhantomAssertions.assertEquals(null, player.getQuestState(QUEST_NAME), "Q02 requires a new isolated quest state.");
		PhantomAssertions.assertEquals(0L, itemCount(player), "Q02 requires an isolated empty fang inventory.");
		final QuestState state = owner().getQuestState(player, true);
		state.startQuest();
		PhantomAssertions.assertTrue(player.addItem(ItemProcessType.QUEST, ITEM_ID, STARTING_COUNT, null, false) != null, "Q02 native fixture fang seed failed.");
		assertStarted(context, player);
		context.record("Q02.seed.objectId", player.getObjectId());
		context.record("Q02.seed.path", "Quest.getQuestState(true)>QuestState.startQuest>Player.addItem(QUEST)");
	}

	/** Also call on the materialized Player: TEST-only playerEnter must not hide a production restore gap. */
	public static void assertStarted(PhantomTestContext context, Player player)
	{
		requireGuardedContext(context);
		assertQuestState(player, 1);
		PhantomAssertions.assertEquals(STARTING_COUNT, itemCount(player), "Q02 cond1 inventory was not preserved before native kill.");
	}

	public static void assertKillReady(PhantomTestContext context, Player player, Npc monster)
	{
		assertStarted(context, player);
		requireStockRate();
		PhantomAssertions.assertEquals(MONSTER_ID, monster.getId(), "Q02 must kill actual stock NPC 20537.");
		PhantomAssertions.assertFalse(monster.isChampion(), "Q02 deterministic fixture must use a nonchampion stock NPC.");
		PhantomAssertions.assertTrue(player.getInventory().validateCapacityByItemId(ITEM_ID), "Q02 native quest item capacity gate would reject its reward.");
	}

	/** Call after actual Attackable.doDie and full delayed listener traversal, never grant the reward here. */
	public static void assertNativeKillEffect(PhantomTestContext context, Player player, Npc monster)
	{
		requireGuardedContext(context);
		PhantomAssertions.assertEquals(MONSTER_ID, monster.getId(), "Q02 effect came from a different native NPC branch.");
		PhantomAssertions.assertEquals(COMPLETED_COUNT, itemCount(player), "Q02 actual stock onKill did not grant its capped two-fang reward.");
		assertQuestState(player, 2);
		context.record("Q02.nativeKill.npcId", monster.getId());
		context.record("Q02.nativeKill.fangs", itemCount(player));
		context.record("Q02.nativeKill.cond", player.getQuestState(QUEST_NAME).getCond());
	}

	/** Caller must release the old runtime first and finally cleanup the returned ordinary Player. */
	public static Player reloadAndAssert(PhantomTestContext context, Player expected)
	{
		requireGuardedContext(context);
		final Player reloaded = Player.load(expected.getObjectId());
		PhantomAssertions.assertTrue(reloaded != null, "Q02 canonical Player reload failed.");
		try
		{
			Quest.playerEnter(reloaded);
			PhantomAssertions.assertEquals(COMPLETED_COUNT, itemCount(reloaded), "Q02 native quest item was lost after Player.load.");
			assertQuestState(reloaded, 2);
			PhantomAssertions.assertEquals(expected.getExp(), reloaded.getExp(), "Q02 native EXP changed after canonical reload.");
			PhantomAssertions.assertEquals(expected.getSp(), reloaded.getSp(), "Q02 native SP changed after canonical reload.");
			context.record("Q02.reload.questStatePath", "Player.load>Quest.playerEnter(TEST explicit)");
			context.record("Q02.reload.fangs", itemCount(reloaded));
			context.record("Q02.reload.cond", reloaded.getQuestState(QUEST_NAME).getCond());
			return reloaded;
		}
		catch (RuntimeException | Error failure)
		{
			try { reloaded.deleteMe(); }
			catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
			try { reloaded.stopAllTasks(); }
			catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
			throw failure;
		}
	}

	/** Only after persistence assertions, on the ordinary reloaded fixture; never on a sealed managed actor. */
	public static void cleanupQuest(PhantomTestContext context, Player player)
	{
		requireGuardedContext(context);
		PhantomAssertions.assertFalse(player.isNativeWorkManaged(), "Q02 quest cleanup requires the ordinary reloaded fixture, after lifecycle release.");
		final QuestState state = player.getQuestState(QUEST_NAME);
		if (state != null) { state.exitQuest(true, false); }
		PhantomAssertions.assertEquals(null, player.getQuestState(QUEST_NAME), "Q02 native repeatable quest cleanup retained its state.");
		PhantomAssertions.assertEquals(0L, itemCount(player), "Q02 native quest cleanup retained registered quest items.");
	}

	public static synchronized void unload(PhantomTestContext context)
	{
		requireGuardedContext(context);
		if (_quest != null)
		{
			owner().unload();
			_quest = null;
		}
	}

	private static Quest owner()
	{
		PhantomAssertions.assertTrue((_quest != null) && (_quest == ScriptManager.getInstance().getQuest(266)) && (_quest == ScriptManager.getInstance().getScript(QUEST_NAME)), "Q02 stock script owner is missing or was replaced.");
		PhantomAssertions.assertEquals(QUEST_CLASS, _quest.getClass().getName(), "Q02 fixture is not the actual stock script class.");
		return _quest;
	}

	private static void assertQuestState(Player player, int cond)
	{
		final QuestState state = player.getQuestState(QUEST_NAME);
		PhantomAssertions.assertTrue((state != null) && (state.getQuest() == owner()) && (state.getPlayer() == player), "Q02 stock QuestState was not restored for this exact Player.");
		PhantomAssertions.assertEquals(State.STARTED, state.getState(), "Q02 stock quest state must remain STARTED at its item cap.");
		PhantomAssertions.assertEquals(cond, state.getCond(), "Q02 stock quest condition differs.");
	}

	private static long itemCount(Player player)
	{
		return player.getInventory().getInventoryItemCount(ITEM_ID, -1);
	}

	private static void requireStockRate()
	{
		PhantomAssertions.assertEquals(1.0f, RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, "Q02 fixture requires the stock TEST quest item amount multiplier; do not alter PLAY balance.");
	}

	private static void requireGuardedContext(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Q02 fixture requires the initialized allowlisted headless TEST environment.");
		PhantomAssertions.assertEquals(context.moduleRoot().resolve("dist/game").normalize(), Path.of("").toAbsolutePath().normalize(), "Q02 ScriptEngine fixture must execute from the native dist/game working directory.");
	}
}

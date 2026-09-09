/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Goal037 full-corpus audit, production compiler/load and canonical helper matrix. */
public final class PhantomQuestRatesGoal037Suite implements PhantomTestSuite
{
	public enum Mode
	{
		STATIC,
		CORPUS,
		HELPERS
	}

	private static final long STATIC_SEED = 37003700L;
	private static final long CORPUS_SEED = 37003701L;
	private static final long HELPERS_SEED = 37003703L;
	private final Mode _mode;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private Player _player;
	private RateSnapshot _rates;
	private long _compileLoadMillis;

	public PhantomQuestRatesGoal037Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "quest-rates-goal037-" + _mode.name().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final long expectedSeed = switch (_mode)
		{
			case STATIC -> STATIC_SEED;
			case CORPUS -> CORPUS_SEED;
			case HELPERS -> HELPERS_SEED;
		};
		PhantomAssertions.assertEquals(expectedSeed, context.seed(), "Goal037 mode used the wrong deterministic seed.");
		if (_mode != Mode.STATIC)
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			if (_mode == Mode.CORPUS)
			{
				final long started = System.nanoTime();
				ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
				ScriptEngine.getInstance().executeScriptList();
				_compileLoadMillis = (System.nanoTime() - started) / 1_000_000;
			}
			else
			{
				_rates = RateSnapshot.capture();
				_player = Player.load(_environment.primary().objectId());
				PhantomAssertions.assertTrue(_player != null, "Goal037 helper fixture Player did not load.");
			}
			context.record("goal037.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case STATIC:
			{
				registry.add("01-full-ast-inventory-manifest-and-determinism", this::staticAudit);
				registry.add("02-manifest-negative-controls", this::manifestNegativeControls);
				break;
			}
			case CORPUS:
			{
				registry.add("01-production-compiler-and-master-load-full-corpus", this::corpusCompileLoad);
				break;
			}
			case HELPERS:
			{
				registry.add("01-one-x-objective-control-and-reward-helpers", this::oneXHelpers);
				registry.add("02-non-one-x-distinct-category-matrix", this::nonOneXHelpers);
				break;
			}
		}
	}

	private void staticAudit(PhantomTestContext context) throws Exception
	{
		final QuestRatesAstAuditor.Verification verification = QuestRatesAstAuditor.verify(context.moduleRoot());
		verification.requireValid();
		final QuestRatesAstAuditor.Audit repeated = QuestRatesAstAuditor.scan(context.moduleRoot());
		PhantomAssertions.assertEquals(QuestRatesAstAuditor.inventoryText(verification.audit()), QuestRatesAstAuditor.inventoryText(repeated), "Repeated inventory generation is not byte-identical.");
		PhantomAssertions.assertEquals(QuestRatesAstAuditor.sitesText(verification.audit()), QuestRatesAstAuditor.sitesText(repeated), "Repeated rate-site generation is not byte-identical.");
		PhantomAssertions.assertTrue(!verification.audit().sources().isEmpty() && !verification.audit().sites().isEmpty(), "Goal037 full corpus inventory is empty.");
		PhantomAssertions.assertEquals(0L, verification.audit().sites().stream().filter(site -> site.decision() == QuestRatesAstAuditor.Decision.REQUIRES_CORRECTION).count(), "Goal037 retained an unresolved decision.");
		PhantomAssertions.assertEquals(0L, verification.audit().sites().stream().filter(site -> site.semanticClass() == QuestRatesAstAuditor.SemanticClass.UNSUPPORTED_REQUIRES_EVIDENCE).count(), "Goal037 retained an unsupported site.");
		context.record("goal037.sources", verification.audit().sources().size());
		context.record("goal037.corpusBytes", verification.audit().totalBytes());
		context.record("goal037.rateSites", verification.audit().sites().size());
		context.record("goal037.inventory", verification.inventorySha256() + "/" + verification.inventoryBytes());
		context.record("goal037.sites", verification.sitesSha256() + "/" + verification.sitesBytes());
	}

	private void manifestNegativeControls(PhantomTestContext context) throws Exception
	{
		final QuestRatesAstAuditor.Audit audit = QuestRatesAstAuditor.scan(context.moduleRoot());
		final String inventory = QuestRatesAstAuditor.inventoryText(audit);
		final String sites = QuestRatesAstAuditor.sitesText(audit);
		assertRejected(audit, removeDataRow(inventory, 0), sites, "Stale quest inventory", "Missing inventory Java source");
		assertRejected(audit, mutateColumn(inventory, 0, 1, "0".repeat(64)), sites, "Stale quest inventory", "Stale source SHA");
		assertRejected(audit, inventory, removeDataRow(sites, 0), "Stale rate-site manifest", "Missing rate-relevant site");
		assertRejected(audit, inventory, mutateColumns(sites, 0, 7, "UNSUPPORTED_REQUIRES_EVIDENCE", 8, "UNKNOWN", 9, "REQUIRES_CORRECTION"), "Unresolved rate-site decision", "Unsupported decision");
		assertRejected(audit, inventory, mutateColumns(sites, 0, 6, "DIRECT_REWARD_MUTATION", 7, "UNSUPPORTED_REQUIRES_EVIDENCE", 8, "UNKNOWN", 9, "REQUIRES_CORRECTION"), "Unresolved rate-site decision", "Unknown direct mutation");
		assertRejected(audit, inventory, duplicateDataRow(sites, 0), "Duplicate rate-site fingerprint", "Duplicate manifest row");
		assertRejected(audit, inventory, mutateColumns(sites, 0, 7, "MALFORMED_CLASS", 8, "MALFORMED_RATE"), "Malformed semantic/rate/decision value", "Malformed enum values");
		context.record("goal037.negativeControls", 7);
	}

	private void corpusCompileLoad(PhantomTestContext context) throws Exception
	{
		final QuestRatesAstAuditor.Audit audit = QuestRatesAstAuditor.scan(context.moduleRoot());
		final List<String> missing = new ArrayList<>();
		for (QuestRatesAstAuditor.Source source : audit.sources())
		{
			if ((source.questId() > 0) && "QUEST".equals(source.sourceKind()))
			{
				final Quest runtime = ScriptManager.getInstance().getQuest(source.questId());
				if ((runtime == null) || !source.questName().equals(runtime.getName()))
				{
					missing.add(source.path() + " expected " + source.questName());
				}
			}
		}
		PhantomAssertions.assertTrue(missing.isEmpty(), "Production QuestMasterHandler did not load audited sources: " + missing);
		context.record("goal037.corpusCompileLoad", "sources=" + audit.sources().size() + ",millis=" + _compileLoadMillis);
	}

	private void oneXHelpers(PhantomTestContext context)
	{
		applyRates(1, 1, 1, 1, 1, false, 1, 1, 1, 1);
		assertItemDelta(966, 1, () -> Quest.giveQuestItemsUpTo(_player, 966, 1, itemCount(966) + 10), "1x objective amount");
		assertItemDelta(964, 1, () -> Quest.giveItemsWithoutQuestRate(_player, 964, 1), "1x control amount");
		assertItemDelta(1835, 1, () -> Quest.rewardItems(_player, 1835, 1), "1x default reward");
		assertItemDelta(1060, 1, () -> Quest.rewardItems(_player, 1060, 1), "1x potion reward");
		assertItemDelta(736, 1, () -> Quest.rewardItems(_player, 736, 1), "1x scroll reward");
		assertItemDelta(1666, 1, () -> Quest.rewardItems(_player, 1666, 1), "1x recipe reward");
		assertItemDelta(1864, 1, () -> Quest.rewardItems(_player, 1864, 1), "1x material reward");
		context.record("goal037.helperProfile1x", "objective/control/xp/sp/adena/default/potion/scroll/recipe/material");
	}

	private void nonOneXHelpers(PhantomTestContext context)
	{
		applyRates(3, 5, 2, 3, 4, true, 7, 11, 13, 17);
		assertItemDelta(966, 3, () -> Quest.giveQuestItemsUpTo(_player, 966, 1, itemCount(966) + 10), "3x objective amount");
		assertItemDelta(966, 2, () -> Quest.giveQuestItemsUpTo(_player, 966, 1, itemCount(966) + 2), "3x objective cap");
		assertItemDelta(964, 1, () -> Quest.giveItemsWithoutQuestRate(_player, 964, 1), "non-1x control amount");
		assertItemDelta(1835, 5, () -> Quest.rewardItems(_player, 1835, 1), "default reward rate");
		assertItemDelta(1060, 7, () -> Quest.rewardItems(_player, 1060, 1), "potion reward rate");
		assertItemDelta(736, 11, () -> Quest.rewardItems(_player, 736, 1), "scroll reward rate");
		assertItemDelta(1666, 13, () -> Quest.rewardItems(_player, 1666, 1), "recipe reward rate");
		assertItemDelta(1864, 17, () -> Quest.rewardItems(_player, 1864, 1), "material reward rate");
		assertItemDelta(57, 4, () -> Quest.giveAdena(_player, 1, true), "Adena reward rate");
		final long exp = _player.getExp();
		final long sp = _player.getSp();
		Quest.addExpAndSp(_player, 10, 10);
		PhantomAssertions.assertEquals(20L, _player.getExp() - exp, "Quest XP multiplier drifted.");
		PhantomAssertions.assertEquals(30L, _player.getSp() - sp, "Quest SP multiplier drifted.");
		context.record("goal037.helperProfileNon1x", "questItem=3,reward=5,xp=2,sp=3,adena=4,potion=7,scroll=11,recipe=13,material=17");
	}

	private void assertItemDelta(int itemId, long expected, Runnable action, String message)
	{
		final long before = itemCount(itemId);
		action.run();
		PhantomAssertions.assertEquals(expected, itemCount(itemId) - before, message);
	}

	private long itemCount(int itemId)
	{
		return _player.getInventory().getInventoryItemCount(itemId, -1);
	}

	private static void applyRates(float questItem, float reward, float xp, float sp, float adena, boolean useMultipliers, float potion, float scroll, float recipe, float material)
	{
		RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER = questItem;
		RatesConfig.RATE_QUEST_REWARD = reward;
		RatesConfig.RATE_QUEST_REWARD_XP = xp;
		RatesConfig.RATE_QUEST_REWARD_SP = sp;
		RatesConfig.RATE_QUEST_REWARD_ADENA = adena;
		RatesConfig.RATE_QUEST_REWARD_USE_MULTIPLIERS = useMultipliers;
		RatesConfig.RATE_QUEST_REWARD_POTION = potion;
		RatesConfig.RATE_QUEST_REWARD_SCROLL = scroll;
		RatesConfig.RATE_QUEST_REWARD_RECIPE = recipe;
		RatesConfig.RATE_QUEST_REWARD_MATERIAL = material;
	}

	private static void assertRejected(QuestRatesAstAuditor.Audit audit, String inventory, String sites, String expected, String label)
	{
		final List<String> failures = QuestRatesAstAuditor.validateAgainst(audit, inventory, sites);
		PhantomAssertions.assertTrue(failures.stream().anyMatch(value -> value.contains(expected)), label + " negative control did not fail closed: " + failures);
	}

	private static String removeDataRow(String text, int dataIndex)
	{
		final List<String> lines = new ArrayList<>(text.lines().toList());
		lines.remove(dataIndex + 1);
		return String.join("\n", lines) + "\n";
	}

	private static String duplicateDataRow(String text, int dataIndex)
	{
		final List<String> lines = new ArrayList<>(text.lines().toList());
		lines.add(lines.get(dataIndex + 1));
		return String.join("\n", lines) + "\n";
	}

	private static String mutateColumn(String text, int dataIndex, int column, String value)
	{
		return mutateColumns(text, dataIndex, column, value);
	}

	private static String mutateColumns(String text, int dataIndex, Object... mutations)
	{
		final List<String> lines = new ArrayList<>(text.lines().toList());
		final String[] columns = lines.get(dataIndex + 1).split("\t", -1);
		for (int index = 0; index < mutations.length; index += 2)
		{
			columns[(Integer) mutations[index]] = (String) mutations[index + 1];
		}
		lines.set(dataIndex + 1, String.join("\t", columns));
		return String.join("\n", lines) + "\n";
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_rates != null)
		{
			_rates.restore();
			_rates = null;
		}
		if ((_environment != null) && (_player != null))
		{
			_environment.cleanupLoadedPlayer(_player);
			_player = null;
		}
		if (_environment != null)
		{
			_environment.shutdown();
			_environment = null;
		}
	}

	private record RateSnapshot(float questItem, float reward, float xp, float sp, float adena, boolean useMultipliers, float potion, float scroll, float recipe, float material)
	{
		static RateSnapshot capture()
		{
			return new RateSnapshot(RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, RatesConfig.RATE_QUEST_REWARD, RatesConfig.RATE_QUEST_REWARD_XP, RatesConfig.RATE_QUEST_REWARD_SP, RatesConfig.RATE_QUEST_REWARD_ADENA, RatesConfig.RATE_QUEST_REWARD_USE_MULTIPLIERS, RatesConfig.RATE_QUEST_REWARD_POTION, RatesConfig.RATE_QUEST_REWARD_SCROLL, RatesConfig.RATE_QUEST_REWARD_RECIPE, RatesConfig.RATE_QUEST_REWARD_MATERIAL);
		}

		void restore()
		{
			applyRates(questItem, reward, xp, sp, adena, useMultipliers, potion, scroll, recipe, material);
		}
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.QuestBinding;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.ChanceKind;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchMode;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchRequest;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.BatchResult;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DeathPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Drop;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.ExperienceTable;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.LevelForExperience;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.QuestFormula;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.RewardPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Target;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

public final class PhantomAcquisitionQuestSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG_SOURCE,
		BACKGROUND
	}

	private static final long SEED = 21002102L;
	private static final Hashes ACQUISITION_HASHES = new Hashes("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64));
	private static final PhantomBackgroundState.Hashes BACKGROUND_HASHES = new PhantomBackgroundState.Hashes("1".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64));
	private final Mode _mode;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomAcquisitionQuestCatalog _catalog;

	public PhantomAcquisitionQuestSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "acquisition-quest-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 021 Checkpoint 2 quest mode used the wrong seed.");
		_catalog = PhantomAcquisitionQuestCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml"), context.moduleRoot().resolve("dist/game/data/scripts"));
		if (_mode == Mode.CATALOG_SOURCE)
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			ScriptEngine.getInstance().executeScript(Path.of("quests/QuestMasterHandler.java"));
			for (Rule rule : _catalog.rules())
			{
				final var runtime = ScriptManager.getInstance().getQuest(rule.questId());
				PhantomAssertions.assertTrue(runtime != null, "QuestMasterHandler did not load curated quest: " + rule.id());
				PhantomAssertions.assertEquals(rule.questName(), runtime.getName(), "Curated runtime quest name differs.");
				PhantomAssertions.assertEquals(rule.scriptPath().substring(0, rule.scriptPath().length() - ".java".length()).replace('/', '.'), runtime.getClass().getName(), "Curated runtime quest class differs.");
			}
			_catalog.validateRuntime();
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.CATALOG_SOURCE)
		{
			registry.add("01-real-source-hash-script-and-registration", this::testRuntimeCatalog);
			registry.add("02-exact-formula-cond-cap-and-negative-drift", this::testRuleContracts);
			registry.add("03-audited-source-shape-has-no-hidden-kill-side-effects", this::testAuditedSources);
		}
		else
		{
			registry.add("01-deterministic-grant-no-grant-and-ordinary-drop", this::testBackgroundFormula);
			registry.add("02-schema3-quest-restart-transitions", this::testRestartTransitions);
			registry.add("03-exact-row-lock-and-single-transaction-ownership", this::testAtomicSourceContract);
		}
	}

	private void testRuntimeCatalog(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(2, _catalog.rules().size(), "Curated quest catalog script count changed.");
		PhantomAssertions.assertTrue(_catalog.current(), "Loaded curated quest authority is not current.");
		for (Rule rule : _catalog.rules())
		{
			final Path source = context.moduleRoot().resolve("dist/game/data/scripts").resolve(rule.scriptPath());
			PhantomAssertions.assertEquals(rule.scriptHash(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))), "Curated quest source hash differs.");
			final var runtime = ScriptManager.getInstance().getQuest(rule.questId());
			PhantomAssertions.assertTrue((runtime != null) && (runtime == ScriptManager.getInstance().getScript(rule.questName())), "Curated quest runtime identity differs.");
			PhantomAssertions.assertTrue(runtime.getRegisteredIds(ListenerRegisterType.NPC).containsAll(rule.targetNpcIds()), "Curated quest kill registration differs.");
		}
		context.record("quest.curatedScripts", _catalog.rules().size());
		context.record("quest.authorityHash", _catalog.authorityHash());
	}

	private void testRuleContracts(PhantomTestContext context) throws Exception
	{
		for (Rule rule : _catalog.rules())
		{
			PhantomAssertions.assertEquals("STARTED", rule.requiredState(), "Curated rule does not require STARTED.");
			PhantomAssertions.assertTrue(rule.supports(rule.allowedConds().getFirst(), 0, rule.targetNpcIds().getFirst(), false), "Exact curated state/cond/target was rejected.");
			PhantomAssertions.assertFalse(rule.supports(rule.allowedConds().getFirst(), rule.itemCap(), rule.targetNpcIds().getFirst(), false), "Curated conservative cap was admitted.");
			PhantomAssertions.assertFalse(rule.supports(rule.allowedConds().getFirst() + 1, 0, rule.targetNpcIds().getFirst(), false), "Wrong curated cond was admitted.");
			PhantomAssertions.assertTrue((rule.minimumCount() == 1) && (rule.maximumCount() == 1) && (rule.chanceKind() == ChanceKind.RND_LT), "Curated grant formula is not the audited single roll/single item shape.");
		}
		final String source = Files.readString(context.moduleRoot().resolve("dist/game/data/phantoms/acquisition/high-five-quest-collection-v1.xml"), StandardCharsets.UTF_8);
		final Path invalid = Files.createTempFile(context.reportsDirectory(), "quest-script-drift-", ".xml");
		Files.writeString(invalid, source.replace(_catalog.rules().getFirst().scriptHash(), "0".repeat(64)), StandardCharsets.UTF_8);
		try
		{
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomAcquisitionQuestCatalog.load(invalid, context.moduleRoot().resolve("dist/game/data/scripts")), "Curated script drift did not fail closed.");
		}
		finally
		{
			Files.deleteIfExists(invalid);
		}
	}

	private void testAuditedSources(PhantomTestContext context) throws Exception
	{
		for (Rule rule : _catalog.rules())
		{
			final String text = Files.readString(context.moduleRoot().resolve("dist/game/data/scripts").resolve(rule.scriptPath()), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(text.contains("onKill") && text.contains("giveItems"), "Curated source lost kill/grant shape.");
			PhantomAssertions.assertFalse(text.contains("startQuestTimer") || text.contains("getParty().getMembers") || text.contains("addGlobalQuestEvent"), "Curated source gained timer/party/global kill side effects.");
			for (String reference : rule.sourceRefs())
			{
				PhantomAssertions.assertTrue(!reference.isBlank(), "Curated audit source reference is blank.");
			}
		}
		context.record("quest.runtimeScriptScans", 0);
	}

	private void testBackgroundFormula(PhantomTestContext context)
	{
		final PhantomBackgroundModel model = new PhantomBackgroundModel();
		for (Rule rule : _catalog.rules())
		{
			BatchResult grant = null;
			BatchResult noGrant = null;
			for (long rng = 1; (rng < 10000) && ((grant == null) || (noGrant == null)); rng++)
			{
				final QuestFormula formula = new QuestFormula(rule.rollBound(), rule.rollThreshold(), rule.maximumCount(), 0, rule.itemCap());
				final BatchResult result = model.evaluate(request(state(rng, rule.questItemId()), rule, formula));
				if (result.acquisitionTargetDelta() == 1)
				{
					grant = result;
				}
				else
				{
					noGrant = result;
				}
			}
			PhantomAssertions.assertTrue((grant != null) && (noGrant != null), "Deterministic background quest did not produce grant/no-grant branches.");
			PhantomAssertions.assertEquals(1L, grant.inventoryDelta().itemDeltas().get(rule.questItemId()), "Background quest grant count differs from audited rule.");
			PhantomAssertions.assertEquals(1L, grant.inventoryDelta().itemDeltas().get(57), "Ordinary quest-combat drop was not kept separate.");
			PhantomAssertions.assertTrue(!noGrant.inventoryDelta().itemDeltas().containsKey(rule.questItemId()), "No-grant branch credited a quest item.");
		}
		context.record("quest.backgroundRules", _catalog.rules().size());
	}

	private void testRestartTransitions(PhantomTestContext context)
	{
		final PhantomAcquisitionStateCodec codec = new PhantomAcquisitionStateCodec();
		for (Phase phase : List.of(Phase.QUEST_COMBAT_PREPARED, Phase.QUEST_COMBAT_SUBMITTED, Phase.QUEST_COMBAT_TERMINAL, Phase.QUEST_CALLBACK_WAIT, Phase.VERIFYING))
		{
			final PhantomAcquisitionState state = questState(phase, phase == Phase.QUEST_COMBAT_SUBMITTED || phase == Phase.QUEST_CALLBACK_WAIT ? 1 : 0);
			final byte[] payload = codec.encode(state);
			PhantomAssertions.assertEquals(state, codec.decode(payload), "Schema-3 quest restart state did not round-trip: " + phase);
			PhantomAssertions.assertTrue(payload.length <= 4096, "Schema-3 quest state exceeded 4096 bytes.");
		}
		context.record("quest.persistedTransitions", 5);
	}

	private void testAtomicSourceContract(PhantomTestContext context) throws Exception
	{
		final String transaction = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/background/PhantomBackgroundTransaction.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(transaction.contains("ORDER BY var FOR UPDATE") && transaction.contains("lockQuestRows"), "Background quest rows are not exact and locked.");
		PhantomAssertions.assertTrue(transaction.contains("mutateItems") && transaction.contains("AFTER_BACKGROUND_STATE_WRITE") && transaction.contains("AFTER_GOAL_STATE_WRITE") && transaction.contains("AFTER_ACQUISITION_STATE_WRITE") && transaction.contains("connection.commit()"), "Item/background/Goal/acquisition mutation is not owned by one transaction boundary.");
		PhantomAssertions.assertTrue(transaction.indexOf("lockQuestRows", transaction.indexOf("AFTER_ACQUISITION_STATE_WRITE")) > 0, "Quest rows are not revalidated before commit.");
		context.record("quest.atomicMutationFamilies", 4);
	}

	private static PhantomAcquisitionState questState(Phase phase, int attempt)
	{
		final String sourceId = "8".repeat(64);
		final Source source = new Source(sourceId, Method.QUEST_COLLECTION, 20013, 966, "quest:q00102-dryads-tear", "node", "anchor", 0, 0, 0, 0, 0);
		final Candidate candidate = new Candidate(sourceId, Method.QUEST_COLLECTION, 100, 0, 0, "");
		final QuestBinding binding = new QuestBinding("q00102-dryads-tear", "6".repeat(64), 102, "Q00102_SeaOfSporesFever", "7".repeat(64), "STARTED", 2, 966, 9, 20013, 0, phase == Phase.QUEST_CALLBACK_WAIT ? 1000 : 0, "f".repeat(64));
		return new PhantomAcquisitionState(ACQUISITION_HASHES, 1, 0, 966, 5, 0, 0, 0, Status.ACTIVE, source, List.of(candidate), 0, 0, phase, 8001, 20013, 0, null, binding, List.of(), attempt, 1);
	}

	private static PhantomBackgroundState state(long rng, int questItemId)
	{
		return new PhantomBackgroundState(State.READY, new Identity(1, 1, 0, 88, 0), new Progress(20, 1900, 0, 0), new Vitals(100, 100, 100, 100, 10, 10), new Position(0, 1, 2, 3, 0, "anchor"), new CombatFacts(ModelKind.MELEE, 1000, 1000, 1000, 1000, 1000, 1000, 0, 0, 1, 1, 0, 1, 1, 1, 1), Loadout.none(), new InventoryFacts(List.of(57, questItemId), List.of(), "quest", 0, 1_000_000, 0, 100), List.of(), new Clock(rng, 0, 0), Receipt.empty(), BACKGROUND_HASHES);
	}

	private static BatchRequest request(PhantomBackgroundState state, Rule rule, QuestFormula formula)
	{
		final Drop ordinary = new Drop(57, -1, 0, 1_000_000, 1_000_000, 1, 1, 1, null, 1, 100, true, 0);
		final Target target = new Target(rule.targetNpcIds().getFirst(), 20, true, 1, 1, 1, 1, 1, 1, 1000, 1000, 0, 0, List.of(ordinary), 1);
		return new BatchRequest(state, target, new RewardPolicy(11, 1, 1), deathPolicy(), experienceTable(), levelForExperience(), false, BatchMode.ACQUISITION_QUEST_COLLECTION, rule.questItemId(), 1, true, null, formula, 1, true, 0);
	}

	private static DeathPolicy deathPolicy()
	{
		return new DeathPolicy()
		{
			@Override
			public double lossPercent(int level)
			{
				return 0;
			}

			@Override
			public double normalMonsterReductionMultiplier()
			{
				return 1;
			}
		};
	}

	private static ExperienceTable experienceTable()
	{
		return new ExperienceTable()
		{
			@Override
			public long experienceForLevel(int level)
			{
				return Math.max(0, level - 1) * 100L;
			}

			@Override
			public int maximumLevel()
			{
				return 85;
			}
		};
	}

	private static LevelForExperience levelForExperience()
	{
		return experience -> (int) Math.clamp((experience / 100) + 1, 1, 85);
	}
}

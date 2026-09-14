/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.network.ReadableBuffer;
import org.l2jmobius.gameserver.config.FloodProtectorConfig;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalProgressionQoLConfig;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.model.script.State;
import org.l2jmobius.gameserver.network.clientpackets.MultiSellChoose;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** L2-QOL-008 economy and progression closure coverage. */
public final class QoLEconomyProgressionClosureSuite implements PhantomTestSuite
{
	private static final long SEED = 1008001L;
	private static final int ADENA = 57;
	private static final int ANCIENT_ADENA = 5575;
	private static final int LORAINES_CERTIFICATE = 10362;
	private static final int METALLOGRAPH_RESEARCH_REPORT = 10366;
	private static final int LETO_LIZARDMAN_ACCESSORY = 10367;
	private static final int NOBLESSE_TIARA = 7694;
	private static final int PROGRESSION_ENTRY_ONE = 100000;
	private static final int BLOOD_MARK = 1419;
	private static final long BLOOD_MARK_PRICE = 5_000_000L;
	private static final List<Integer> CURATED_ITEMS = List.of(1419, 3874, 3870, 9910, 9911);
	private static final List<Long> CURATED_COUNTS = List.of(1L, 1L, 1L, 150L, 5L);
	private static final List<Long> CURATED_PRICES = List.of(5_000_000L, 15_000_000L, 30_000_000L, 75_000_000L, 100_000_000L);
	private static final List<String> UPPER_LEVEL_QUESTS = List.of(
		"Q00128_PailakaSongOfIceAndFire",
		"Q00129_PailakaDevilsLegacy",
		"Q00134_TempleMissionary",
		"Q00135_TempleExecutor",
		"Q00139_ShadowFoxPart1",
		"Q00140_ShadowFoxPart2",
		"Q00141_ShadowFoxPart3",
		"Q00142_FallenAngelRequestOfDawn",
		"Q00143_FallenAngelRequestOfDusk",
		"Q00144_PailakaInjuredDragon",
		"Q00178_IconicTrinity",
		"Q00179_IntoTheLargeCavern",
		"Q00182_NewRecruits",
		"Q00183_RelicExploration",
		"Q00184_ArtOfPersuasion",
		"Q00185_NikolasCooperation",
		"Q00186_ContractExecution",
		"Q00187_NikolasHeart",
		"Q00188_SealRemoval",
		"Q00189_ContractCompletion",
		"Q00190_LostDream",
		"Q00191_VainConclusion",
		"Q00335_TheSongOfTheHunter",
		"Q00694_BreakThroughTheHallOfSuffering",
		"Q00695_DefendTheHallOfSuffering",
		"Q00698_BlockTheLordsEscape");

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PersonalCharacterQoLConfig.Settings _previousCharacterSettings;
	private PersonalProgressionQoLConfig.Settings _previousProgressionSettings;
	private PersonalPremiumQoLService.RuntimeState _previousPremiumState;
	private PersonalPremiumQoLService.RuntimeState _activePremiumState;
	private RateSnapshot _rates;
	private CommunitySettings _communitySettings;
	private QoLTestClient _network;
	private PhantomMaterializedPlayer _phantom;
	private Player _player;
	private Player _observer;

	@Override
	public String id()
	{
		return "qol-economy-progression-closure";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "L2-QOL-008 suite used the wrong deterministic seed.");
		_environment.initialize(context);
		_rates = RateSnapshot.capture();
		MultisellData.getInstance();
		_activePremiumState = PersonalPremiumQoLService.build(new PersonalPremiumQoLConfig.Settings(true, true, PersonalPremiumQoLConfig.DEFAULT_LEVEL_GAP_ITEMS_FILE, true, "QOL-008 test."), Path.of("."));
		_previousPremiumState = PersonalPremiumQoLService.installForTests(_activePremiumState);
		PersonalProgressionShopService.getInstance().initialize();
		_player = Player.load(_environment.primary().objectId());
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_player != null) && (_observer != null), "L2-QOL-008 fixture Players did not load.");
		_previousCharacterSettings = PersonalCharacterQoLService.getInstance().installForTests(characterSettings(Set.of(_player.getObjectId())));
		_previousProgressionSettings = PersonalProgressionQoLService.getInstance().installForTests(new PersonalProgressionQoLConfig.Settings(true, false, true, "QOL-008 initial test."));
		_communitySettings = CommunitySettings.capture();
		_communitySettings.enableForTest();
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		ScriptEngine.getInstance().executeScript(Path.of("quests/QuestMasterHandler.java"));
		context.record("qol008.database", "l2jmobiush5_phantom_test");
		context.record("qol008.progressionList", PersonalProgressionShopService.SHOP_LIST_ID);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-source-census-native-currency-rates-and-bounded-upper-gates", this::sourceCensus);
		registry.add("02-strict-shipped-off-config-and-real-player-only-relief", this::configAndReliefPolicy);
		registry.add("03-combat-and-quest-rates-apply-exactly-once", this::rateAuthority);
		registry.add("04-q186-leto-start-progress-upper-reward-and-prerequisites", this::letoQuestLifecycle);
		registry.add("05-curated-data-owned-store-native-transaction-and-controls", this::progressionStore);
		registry.add("06-real-player-auto-noblesse-canonical-idempotent-persistence", this::realAutoNoblesse);
		registry.add("07-phantom-materialization-auto-noblesse-and-no-free-grants", this::phantomAutoNoblesse);
	}

	private void sourceCensus(PhantomTestContext context) throws Exception
	{
		final Path module = context.moduleRoot();
		final String rates = Files.readString(module.resolve("dist/game/config/Rates.ini"), StandardCharsets.UTF_8);
		for (String expected : List.of("RateSp = 1", "RatePartySp = 1", "QuestItemDropAmountMultiplier = 1", "RateQuestRewardXP = 1", "RateQuestRewardSP = 1", "RateQuestRewardAdena = 1", "RateQuestReward = 1"))
		{
			PhantomAssertions.assertTrue(rates.contains(expected), "Shipped rate authority drifted: " + expected);
		}

		final String sevenSigns = Files.readString(module.resolve("java/org/l2jmobius/gameserver/model/sevensigns/SevenSigns.java"), StandardCharsets.UTF_8);
		final String inventory = Files.readString(module.resolve("java/org/l2jmobius/gameserver/model/itemcontainer/Inventory.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(inventory.contains("ANCIENT_ADENA_ID = " + ANCIENT_ADENA), "Canonical Ancient Adena item ID changed.");
		PhantomAssertions.assertTrue(sevenSigns.contains("SEAL_STONE_BLUE_VALUE = 3") && sevenSigns.contains("SEAL_STONE_GREEN_VALUE = 5") && sevenSigns.contains("SEAL_STONE_RED_VALUE = 10") && sevenSigns.contains("calcAncientAdenaReward"), "Canonical seal-stone values or Ancient Adena owner changed.");

		final String questRoot = "dist/game/data/scripts/quests/";
		for (String quest : UPPER_LEVEL_QUESTS)
		{
			final String source = Files.readString(module.resolve(questRoot + quest + "/" + quest + ".java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(source.contains("isQuestUpperLevelAllowed(player,"), "Audited upper-level quest lost the bounded policy seam: " + quest);
		}
		final String leto = Files.readString(module.resolve(questRoot + "Q00186_ContractExecution/Q00186_ContractExecution.java"), StandardCharsets.UTF_8);
		for (String expected : List.of("MIN_LEVEL = 41", "MAX_LEVEL_FOR_EXP_SP = 47", "Q00184_ArtOfPersuasion", "LORAINES_CERTIFICATE", "LETO_LIZARDMAN_ACCESSORY", "LocationUtil.checkIfInRange", "giveAdena(player, 105083, true)", "addExpAndSp(player, 285935, 18711)"))
		{
			PhantomAssertions.assertTrue(leto.contains(expected), "Q186 Leto contract drifted: " + expected);
		}

		final String clan = Files.readString(module.resolve("java/org/l2jmobius/gameserver/model/clan/Clan.java"), StandardCharsets.UTF_8);
		for (String expected : List.of("destroyItemByItemId", "1419", "3874", "3870", "9910", "9911", "150", "350000", "1000000", "2500000", "40000"))
		{
			PhantomAssertions.assertTrue(clan.contains(expected), "Canonical clan progression owner drifted: " + expected);
		}

		long collisionCount;
		try (var paths = Files.walk(module.resolve("dist/game/data/multisell")))
		{
			collisionCount = paths.filter(path -> path.getFileName().toString().equals(PersonalProgressionShopService.SHOP_LIST_ID + ".xml")).count();
		}
		PhantomAssertions.assertEquals(1L, collisionCount, "Progression multisell ID is absent or collides.");
		context.record("qol008.census", "ancientAdena=native;upperQuests=" + UPPER_LEVEL_QUESTS.size() + ";multisellCollisionCount=" + collisionCount);
	}

	private void configAndReliefPolicy(PhantomTestContext context) throws Exception
	{
		final PersonalProgressionQoLConfig.Settings shipped = PersonalProgressionQoLConfig.read(Path.of("config/Custom/PersonalProgressionQoL.ini"));
		PhantomAssertions.assertTrue(shipped.valid() && !shipped.questOverLevelReliefEnabled() && !shipped.autoNoblesseEnabled(), "Progression QoL features are not shipped OFF.");
		final Path malformed = context.reportsDirectory().resolve("qol008-malformed.ini");
		Files.writeString(malformed, "EnablePersonalQuestOverLevelRelief=Sometimes\nEnableServerWideAutoNoblesse=True\n", StandardCharsets.UTF_8);
		final PersonalProgressionQoLConfig.Settings rejected = PersonalProgressionQoLConfig.read(malformed);
		PhantomAssertions.assertFalse(rejected.valid() || rejected.questOverLevelReliefEnabled() || rejected.autoNoblesseEnabled(), "Malformed progression config did not fail closed.");

		final PersonalProgressionQoLService service = PersonalProgressionQoLService.getInstance();
		installProgression(true, false);
		installCharacters(Set.of(_player.getObjectId()));
		PhantomAssertions.assertTrue(service.isQuestOverLevelReliefEnabled(_player), "Allowlisted real Player was denied quest relief.");
		PhantomAssertions.assertFalse(service.isQuestOverLevelReliefEnabled(_observer), "Ordinary Player received quest relief.");
		try (Player.OutboundSessionAttachment ignored = _player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32)))
		{
			PhantomAssertions.assertFalse(service.isQuestOverLevelReliefEnabled(_player), "Headless Phantom received personal quest relief.");
		}
		installProgression(false, false);
		PhantomAssertions.assertFalse(service.isQuestOverLevelReliefEnabled(_player), "Feature OFF admitted an allowlisted Player.");
		installProgression(true, false);
		context.record("qol008.relief", "allowlistedReal=true;ordinary=false;phantom=false;shippedOff=true;malformedFailClosed=true");
	}

	private void rateAuthority(PhantomTestContext context)
	{
		final Npc mob = new Npc(NpcData.getInstance().getTemplate(20582));
		try
		{
			RatesConfig.RATE_SP = 1;
			final int oneXSp = mob.getSpReward(_player.getLevel());
			RatesConfig.RATE_SP = 3;
			PhantomAssertions.assertEquals(oneXSp * 3, mob.getSpReward(_player.getLevel()), "Combat RateSp was not applied exactly once.");

			applyQuestRates(1, 1, 1, 1, 1);
			final long exp1 = _player.getExp();
			final long sp1 = _player.getSp();
			final long adena1 = count(_player, ADENA);
			final long item1 = count(_player, 1864);
			Quest.addExpAndSp(_player, 10, 10);
			Quest.giveAdena(_player, 10, true);
			Quest.rewardItems(_player, 1864, 1);
			PhantomAssertions.assertEquals(10L, _player.getExp() - exp1, "Quest XP 1x baseline drifted.");
			PhantomAssertions.assertEquals(10L, _player.getSp() - sp1, "Quest SP 1x baseline drifted.");
			PhantomAssertions.assertEquals(10L, count(_player, ADENA) - adena1, "Quest Adena 1x baseline drifted.");
			PhantomAssertions.assertEquals(1L, count(_player, 1864) - item1, "Quest item 1x baseline drifted.");

			installProgression(false, false);
			applyQuestRates(3, 5, 2, 3, 4);
			final long exp2 = _player.getExp();
			final long sp2 = _player.getSp();
			final long adena2 = count(_player, ADENA);
			final long item2 = count(_player, 1864);
			Quest.addExpAndSp(_player, 10, 10);
			Quest.giveAdena(_player, 10, true);
			Quest.rewardItems(_player, 1864, 1);
			PhantomAssertions.assertEquals(20L, _player.getExp() - exp2, "Quest XP multiplier was not applied exactly once.");
			PhantomAssertions.assertEquals(30L, _player.getSp() - sp2, "Quest SP multiplier was not applied exactly once.");
			PhantomAssertions.assertEquals(40L, count(_player, ADENA) - adena2, "Quest Adena multiplier was not applied exactly once.");
			PhantomAssertions.assertEquals(5L, count(_player, 1864) - item2, "Quest generic item multiplier was not applied exactly once.");
			installProgression(true, false);
			context.record("qol008.rates", "combatSp=1x/3x;questItem=3;reward=5;xp=2;sp=3;adena=4;personalOffNoChange=true");
		}
		finally
		{
			mob.deleteMe();
			remove(_player, 1864);
		}
	}

	private void letoQuestLifecycle(PhantomTestContext context)
	{
		applyQuestRates(3, 5, 2, 3, 4);
		installProgression(true, false);
		installCharacters(Set.of(_player.getObjectId()));
		final Quest q184 = requiredQuest(184);
		final Quest q186 = requiredQuest(186);
		final Npc lorain = new Npc(NpcData.getInstance().getTemplate(30673));
		final Npc nikola = new Npc(NpcData.getInstance().getTemplate(30621));
		final Npc luka = new Npc(NpcData.getInstance().getTemplate(31437));
		final Npc leto = new Npc(NpcData.getInstance().getTemplate(20582));
		try
		{
			_player.getStat().setLevel((byte) 40);
			final QuestState created = q186.getQuestState(_player, true);
			PhantomAssertions.assertFalse(created.isStarted(), "Q186 fixture did not begin CREATED.");
			PhantomAssertions.assertEquals(Quest.getNoQuestMsg(_player), q186.onTalk(lorain, _player), "Missing prerequisite unexpectedly exposed Q186 start.");
			q186.onEvent("30673-03.htm", lorain, _player);
			PhantomAssertions.assertFalse(created.isStarted(), "Missing prerequisite started Q186.");

			complete(q184, _player);
			give(_player, LORAINES_CERTIFICATE, 1);
			PhantomAssertions.assertEquals("30673-02.htm", q186.onTalk(lorain, _player), "Q186 minimum-level denial changed.");
			q186.onEvent("30673-03.htm", lorain, _player);
			PhantomAssertions.assertFalse(created.isStarted(), "Quest relief bypassed Q186 minimum level.");

			_player.getStat().setLevel((byte) 80);
			PhantomAssertions.assertEquals("30673-01.htm", q186.onTalk(lorain, _player), "Otherwise valid over-level Q186 start was unavailable.");
			q186.onEvent("30673-03.htm", lorain, _player);
			PhantomAssertions.assertTrue(created.isStarted() && created.isMemoState(1), "Q186 did not start through its native event.");
			PhantomAssertions.assertEquals(0L, count(_player, LORAINES_CERTIFICATE), "Q186 did not consume its prerequisite certificate.");
			PhantomAssertions.assertEquals(1L, count(_player, METALLOGRAPH_RESEARCH_REPORT), "Q186 did not grant its fixed progress report.");
			q186.onEvent("30621-03.html", nikola, _player);
			PhantomAssertions.assertTrue(created.isMemoState(2), "Q186 did not reach Leto collection state.");
			leto.setXYZ(_player.getX(), _player.getY(), _player.getZ());
			q186.onKill(leto, _player, false);
			PhantomAssertions.assertEquals(3L, count(_player, LETO_LIZARDMAN_ACCESSORY), "Q186 Leto objective did not obey the quest-item amount multiplier exactly once.");
			q186.onEvent("31437-04.html", luka, _player);
			PhantomAssertions.assertTrue(created.isMemoState(3), "Q186 did not preserve its native progress gate.");
			final long exp = _player.getExp();
			final long sp = _player.getSp();
			final long adena = count(_player, ADENA);
			q186.onEvent("31437-06.html", luka, _player);
			PhantomAssertions.assertEquals(285935L * 2, _player.getExp() - exp, "Personal over-level Q186 XP did not retain configured rate authority.");
			PhantomAssertions.assertEquals(18711L * 3, _player.getSp() - sp, "Personal over-level Q186 SP did not retain configured rate authority.");
			PhantomAssertions.assertEquals(105083L * 4, count(_player, ADENA) - adena, "Q186 Adena did not retain configured rate authority.");

			_observer.getStat().setLevel((byte) 41);
			complete(q184, _observer);
			give(_observer, LORAINES_CERTIFICATE, 1);
			final QuestState ordinary = q186.getQuestState(_observer, true);
			PhantomAssertions.assertEquals("30673-01.htm", q186.onTalk(lorain, _observer), "Ordinary normal-level Q186 start behavior changed.");
			_observer.getStat().setLevel((byte) 80);
			q186.onEvent("30673-03.htm", lorain, _observer);
			q186.onEvent("30621-03.html", nikola, _observer);
			give(_observer, LETO_LIZARDMAN_ACCESSORY, 1);
			q186.onEvent("31437-04.html", luka, _observer);
			final long ordinaryExp = _observer.getExp();
			final long ordinarySp = _observer.getSp();
			final long ordinaryAdena = count(_observer, ADENA);
			q186.onEvent("31437-06.html", luka, _observer);
			PhantomAssertions.assertTrue(ordinary.isCompleted(), "Ordinary Q186 did not retain stock completion.");
			PhantomAssertions.assertEquals(0L, _observer.getExp() - ordinaryExp, "Ordinary over-level Player received personal Q186 XP relief.");
			PhantomAssertions.assertEquals(0L, _observer.getSp() - ordinarySp, "Ordinary over-level Player received personal Q186 SP relief.");
			PhantomAssertions.assertEquals(105083L * 4, count(_observer, ADENA) - ordinaryAdena, "Ordinary Q186 lost its stock rate-aware Adena reward.");
			context.record("qol008.leto", "quest=186;min=41;upperRewardExclusive=47;objective=10367;questItemRate=3;xpRate=2;spRate=3;adenaRate=4");
		}
		finally
		{
			lorain.deleteMe();
			nikola.deleteMe();
			luka.deleteMe();
			leto.deleteMe();
			remove(_player, LORAINES_CERTIFICATE);
			remove(_player, METALLOGRAPH_RESEARCH_REPORT);
			remove(_player, LETO_LIZARDMAN_ACCESSORY);
			remove(_observer, LORAINES_CERTIFICATE);
			remove(_observer, METALLOGRAPH_RESEARCH_REPORT);
			remove(_observer, LETO_LIZARDMAN_ACCESSORY);
		}
	}

	private void progressionStore(PhantomTestContext context) throws Exception
	{
		final List<PersonalProgressionShopService.StorefrontOffer> offers = PersonalProgressionShopService.getInstance().storefrontOffers();
		PhantomAssertions.assertEquals(CURATED_ITEMS, offers.stream().map(PersonalProgressionShopService.StorefrontOffer::itemId).toList(), "Progression shop item allowlist drifted.");
		PhantomAssertions.assertEquals(CURATED_COUNTS, offers.stream().map(PersonalProgressionShopService.StorefrontOffer::count).toList(), "Progression shop canonical counts drifted.");
		PhantomAssertions.assertEquals(CURATED_PRICES, offers.stream().map(PersonalProgressionShopService.StorefrontOffer::price).toList(), "Progression shop data-owned prices drifted.");
		PhantomAssertions.assertEquals(List.of(100000L, 500000L, 2000000L, 8000000L), PersonalPremiumQoLService.getInstance().storefrontOffers().stream().map(PersonalPremiumQoLService.StorefrontOffer::price).toList(), "QOL-004 pass prices changed.");

		for (int itemId : CURATED_ITEMS)
		{
			remove(_player, itemId);
		}
		remove(_player, ADENA);
		_network = QoLTestClient.attach(_player);
		_player.setMultiSell(null);
		PhantomAssertions.assertTrue(MultisellData.getInstance().separateAndSendPersonalProgressionQoL(_player), "Dedicated progression route did not prepare its native list.");
		executeProgression(PROGRESSION_ENTRY_ONE, 1);
		PhantomAssertions.assertEquals(0L, count(_player, BLOOD_MARK), "Insufficient Adena produced a progression item.");

		resetNetwork();
		give(_player, ADENA, BLOOD_MARK_PRICE);
		_player.setMultiSell(null);
		CommunityBoardHandler.getInstance().handleParseCommand("_bbsqol;progression-shop", _player);
		awaitProgressionList();
		final long adena = count(_player, ADENA);
		executeProgression(PROGRESSION_ENTRY_ONE, 1);
		PhantomAssertions.assertEquals(adena - BLOOD_MARK_PRICE, count(_player, ADENA), "Native progression transaction did not debit its XML price.");
		PhantomAssertions.assertEquals(1L, count(_player, BLOOD_MARK), "Native progression transaction did not credit its XML product count.");

		resetNetwork();
		give(_player, ADENA, BLOOD_MARK_PRICE);
		PhantomAssertions.assertTrue(MultisellData.getInstance().separateAndSendPersonalProgressionQoL(_player), "Safe repeat could not prepare the progression list.");
		executeProgression(PROGRESSION_ENTRY_ONE, 1);
		PhantomAssertions.assertEquals(2L, count(_player, BLOOD_MARK), "Safe paid repeat did not produce exactly one additional item.");

		_player.setMultiSell(null);
		MultisellData.getInstance().separateAndSend(PersonalProgressionShopService.SHOP_LIST_ID, _player, null, false);
		PhantomAssertions.assertEquals(null, _player.getMultiSell(), "Generic route forged progression-list provenance.");
		context.record("qol008.store", "items=" + CURATED_ITEMS + ";counts=" + CURATED_COUNTS + ";prices=" + CURATED_PRICES + ";preview=transaction");
		_network.close();
		_network = null;
		remove(_player, ADENA);
		for (int itemId : CURATED_ITEMS)
		{
			remove(_player, itemId);
		}
	}

	private void realAutoNoblesse(PhantomTestContext context) throws Exception
	{
		installProgression(true, true);
		PhantomAssertions.assertFalse(PersonalProgressionQoLService.getInstance().tryGrantAutoNoblesse(_player), "A main-class-only character received auto-Noblesse.");
		installProgression(true, false);
		PhantomAssertions.assertFalse(_player.isNoble(), "Real fixture was already Noble.");
		PhantomAssertions.assertTrue(_player.addSubClass(PlayerClass.ARCHMAGE.getId(), 1), "Could not create canonical real subclass fixture.");
		_player.getSubClasses().get(1).setLevel((byte) 75);
		_player.store(false);
		final long tiara = count(_player, NOBLESSE_TIARA);
		final QuestState nobleQuest = _player.getQuestState("Q00247_PossessorOfAPreciousSoul4");
		final PersonalProgressionQoLService service = PersonalProgressionQoLService.getInstance();
		PhantomAssertions.assertFalse(service.tryGrantAutoNoblesse(_player) || _player.isNoble(), "Feature OFF granted auto-Noblesse.");

		installProgression(true, true);
		PhantomAssertions.assertTrue(service.tryGrantAutoNoblesse(_player), "Eligible stored subclass did not grant auto-Noblesse.");
		PhantomAssertions.assertTrue(_player.isNoble(), "Canonical Noble flag was not set.");
		PhantomAssertions.assertFalse(_player.isHero(), "Auto-Noblesse granted Hero.");
		PhantomAssertions.assertEquals(tiara, count(_player, NOBLESSE_TIARA), "Auto-Noblesse duplicated the quest tiara.");
		PhantomAssertions.assertEquals(nobleQuest, _player.getQuestState("Q00247_PossessorOfAPreciousSoul4"), "Auto-Noblesse synthesized quest state.");
		for (var skill : SkillTreeData.getInstance().getNobleSkillTree().values())
		{
			PhantomAssertions.assertTrue(_player.getSkillLevel(skill.getId()) >= skill.getLevel(), "Canonical Noble skill was not granted: " + skill.getId());
		}
		PhantomAssertions.assertFalse(service.tryGrantAutoNoblesse(_player), "Repeated auto-Noblesse was not idempotent.");

		_player.stopAllTasks();
		_player.deleteMe();
		_player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue((_player != null) && _player.isNoble() && (_player.getSubClasses().get(1).getLevel() == 75), "Real auto-Noblesse or stored subclass did not survive relog.");
		context.record("qol008.realNoblesse", "storedSubclass=75;mainActive=true;canonicalSkills=true;persistent=true;idempotent=true;hero=false;tiaraDelta=0");
	}

	private void phantomAutoNoblesse(PhantomTestContext context) throws Exception
	{
		installProgression(true, false);
		PhantomAssertions.assertFalse(_observer.isNoble(), "Phantom fixture was already Noble.");
		PhantomAssertions.assertTrue(_observer.addSubClass(PlayerClass.ARCHMAGE.getId(), 1), "Could not create canonical Phantom subclass fixture.");
		_observer.getSubClasses().get(1).setLevel((byte) 74);
		_observer.store(false);
		PhantomAssertions.assertFalse(PersonalProgressionQoLService.getInstance().hasEligibleStoredSubclass(_observer), "Below-threshold subclass was eligible.");
		_observer.getSubClasses().get(1).setLevel((byte) 75);
		_observer.store(false);
		final long tiara = count(_observer, NOBLESSE_TIARA);
		final long progressionItems = curatedCount(_observer);
		_observer.stopAllTasks();
		_observer.deleteMe();
		_observer = null;

		installProgression(true, true);
		_phantom = new PhantomMaterializedPlayer(_environment.observer().objectId(), PhantomIdentityLeaseRegistry.getInstance(), new HeadlessPlayerOutboundSession(16, 128, 32));
		_phantom.materialize();
		final Player materialized = _phantom.getPlayer();
		PhantomAssertions.assertTrue((materialized != null) && materialized.hasHeadlessOutboundSession() && materialized.isNoble(), "Eligible Phantom materialization did not grant canonical auto-Noblesse.");
		PhantomAssertions.assertFalse(materialized.isHero(), "Phantom auto-Noblesse granted Hero.");
		PhantomAssertions.assertEquals(tiara, count(materialized, NOBLESSE_TIARA), "Phantom auto-Noblesse duplicated the quest tiara.");
		PhantomAssertions.assertEquals(progressionItems, curatedCount(materialized), "Phantom materialization granted progression-shop inventory for free.");
		PhantomAssertions.assertEquals(null, materialized.getQuestState("Q00247_PossessorOfAPreciousSoul4"), "Phantom auto-Noblesse synthesized quest state.");
		PhantomAssertions.assertFalse(PersonalProgressionQoLService.getInstance().tryGrantAutoNoblesse(materialized), "Phantom repeated check was not idempotent.");
		context.record("qol008.phantomNoblesse", "materializationHook=true;storedSubclass=75;canonical=true;idempotent=true;freeProgressionItems=0");
		_phantom.close();
		_phantom = null;
	}

	private void executeProgression(int entryId, long amount) throws Exception
	{
		Thread.sleep(15);
		final ByteBuffer bytes = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN);
		bytes.putInt(PersonalProgressionShopService.SHOP_LIST_ID);
		bytes.putInt(entryId);
		bytes.putLong(amount);
		bytes.putShort((short) 0);
		bytes.putInt(0);
		bytes.putInt(0);
		for (int index = 0; index < 8; index++)
		{
			bytes.putShort((short) 0);
		}
		bytes.flip();
		final MultiSellChoose packet = new MultiSellChoose();
		packet.init(_network.client(), ReadableBuffer.of(bytes));
		PhantomAssertions.assertTrue(packet.read(), "Native progression MultiSellChoose packet did not parse.");
		packet.run();
	}

	private void resetNetwork() throws Exception
	{
		if (_network != null)
		{
			_network.close();
		}
		_network = QoLTestClient.attach(_player);
		final long interval = (long) FloodProtectorConfig.FLOOD_PROTECTOR_MULTISELL.getProtectionInterval() * GameTimeTaskManager.MILLIS_IN_TICK;
		Thread.sleep(interval + GameTimeTaskManager.MILLIS_IN_TICK);
	}

	private void awaitProgressionList() throws InterruptedException
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while ((_player.getMultiSell() == null) && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue((_player.getMultiSell() != null) && (_player.getMultiSell().getListId() == PersonalProgressionShopService.SHOP_LIST_ID) && _player.getMultiSell().isPersonalPremiumQoL(), "Alt+B did not prepare the protected progression list.");
	}

	private static Quest requiredQuest(int questId)
	{
		final Quest quest = ScriptManager.getInstance().getQuest(questId);
		PhantomAssertions.assertTrue(quest != null, "QuestMasterHandler did not load quest " + questId + ".");
		return quest;
	}

	private static void complete(Quest quest, Player player)
	{
		QuestState state = quest.getQuestState(player, false);
		if (state == null)
		{
			state = quest.newQuestState(player);
		}
		state.setState(State.COMPLETED);
	}

	private static void applyQuestRates(float questItem, float reward, float xp, float sp, float adena)
	{
		RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER = questItem;
		RatesConfig.RATE_QUEST_REWARD = reward;
		RatesConfig.RATE_QUEST_REWARD_XP = xp;
		RatesConfig.RATE_QUEST_REWARD_SP = sp;
		RatesConfig.RATE_QUEST_REWARD_ADENA = adena;
		RatesConfig.RATE_QUEST_REWARD_USE_MULTIPLIERS = false;
	}

	private void installCharacters(Set<Integer> ids)
	{
		PersonalCharacterQoLService.getInstance().installForTests(characterSettings(ids));
	}

	private static PersonalCharacterQoLConfig.Settings characterSettings(Set<Integer> ids)
	{
		return new PersonalCharacterQoLConfig.Settings(true, false, false, false, false, ids, Set.of(), true, "QOL-008 allowlist test.");
	}

	private static void installProgression(boolean questRelief, boolean autoNoblesse)
	{
		PersonalProgressionQoLService.getInstance().installForTests(new PersonalProgressionQoLConfig.Settings(questRelief, autoNoblesse, true, "QOL-008 test."));
	}

	private static void give(Player player, int itemId, long amount)
	{
		PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, itemId, amount, player, QoLEconomyProgressionClosureSuite.class) != null, "Could not give fixture item " + itemId + ".");
	}

	private static long count(Player player, int itemId)
	{
		return player.getInventory().getInventoryItemCount(itemId, -1);
	}

	private static long curatedCount(Player player)
	{
		return CURATED_ITEMS.stream().mapToLong(itemId -> count(player, itemId)).sum();
	}

	private static void remove(Player player, int itemId)
	{
		if (player == null)
		{
			return;
		}
		final long count = count(player, itemId);
		if (count > 0)
		{
			player.destroyItemByItemId(ItemProcessType.DESTROY, itemId, count, player, false);
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if (_network != null)
			{
				_network.close();
				_network = null;
			}
			if (_phantom != null)
			{
				_phantom.close();
				_phantom = null;
			}
			if (_player != null)
			{
				_environment.cleanupLoadedPlayer(_player);
				_player = null;
			}
			if (_observer != null)
			{
				_environment.cleanupLoadedPlayer(_observer);
				_observer = null;
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		finally
		{
			if (_rates != null)
			{
				_rates.restore();
			}
			if (_communitySettings != null)
			{
				_communitySettings.restore();
			}
			if (_previousPremiumState != null)
			{
				PersonalPremiumQoLService.installForTests(_previousPremiumState);
			}
			if (_previousCharacterSettings != null)
			{
				PersonalCharacterQoLService.getInstance().installForTests(_previousCharacterSettings);
			}
			if (_previousProgressionSettings != null)
			{
				PersonalProgressionQoLService.getInstance().installForTests(_previousProgressionSettings);
			}
			_environment.shutdown();
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

	private record RateSnapshot(float sp, float partySp, float questItem, float reward, float questXp, float questSp, float questAdena, boolean typed)
	{
		static RateSnapshot capture()
		{
			return new RateSnapshot(RatesConfig.RATE_SP, RatesConfig.RATE_PARTY_SP, RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER, RatesConfig.RATE_QUEST_REWARD, RatesConfig.RATE_QUEST_REWARD_XP, RatesConfig.RATE_QUEST_REWARD_SP, RatesConfig.RATE_QUEST_REWARD_ADENA, RatesConfig.RATE_QUEST_REWARD_USE_MULTIPLIERS);
		}

		void restore()
		{
			RatesConfig.RATE_SP = sp;
			RatesConfig.RATE_PARTY_SP = partySp;
			RatesConfig.QUEST_ITEM_DROP_AMOUNT_MULTIPLIER = questItem;
			RatesConfig.RATE_QUEST_REWARD = reward;
			RatesConfig.RATE_QUEST_REWARD_XP = questXp;
			RatesConfig.RATE_QUEST_REWARD_SP = questSp;
			RatesConfig.RATE_QUEST_REWARD_ADENA = questAdena;
			RatesConfig.RATE_QUEST_REWARD_USE_MULTIPLIERS = typed;
		}
	}

	private record CommunitySettings(boolean enabled, boolean custom, boolean combatDisabled, boolean karmaDisabled, boolean peaceOnly)
	{
		static CommunitySettings capture()
		{
			return new CommunitySettings(GeneralConfig.ENABLE_COMMUNITY_BOARD, CommunityBoardConfig.CUSTOM_CB_ENABLED, CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED, CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED, CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY);
		}

		void enableForTest()
		{
			GeneralConfig.ENABLE_COMMUNITY_BOARD = true;
			CommunityBoardConfig.CUSTOM_CB_ENABLED = true;
			CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED = false;
			CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED = false;
			CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY = false;
		}

		void restore()
		{
			GeneralConfig.ENABLE_COMMUNITY_BOARD = enabled;
			CommunityBoardConfig.CUSTOM_CB_ENABLED = custom;
			CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED = combatDisabled;
			CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED = karmaDisabled;
			CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY = peaceOnly;
		}
	}
}

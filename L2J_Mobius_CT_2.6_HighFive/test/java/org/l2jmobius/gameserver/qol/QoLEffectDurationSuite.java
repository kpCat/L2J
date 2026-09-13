/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.EffectDurationSettings;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Servitor;
import org.l2jmobius.gameserver.model.actor.instance.Trainer;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.SkillOperateType;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.gameserver.model.stats.Formulas;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.qol.PersonalEffectDurationPolicy.Category;
import org.l2jmobius.gameserver.qol.PersonalEffectDurationPolicy.Decision;
import org.l2jmobius.gameserver.qol.PersonalEffectDurationPolicy.Result;
import org.l2jmobius.gameserver.qol.PersonalEffectDurationPolicy.SkillTraits;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** L2-QOL-003 personal positive effect duration coverage. */
public final class QoLEffectDurationSuite implements PhantomTestSuite
{
	private static final long SEED = 1003001L;
	private static final int TRAINER_ID = 30516;
	private static final int MIGHT_ID = 1068;
	private static final int MIGHT_LEVEL = 3;
	private static final int SONG_ID = 269;
	private static final int DANCE_ID = 274;
	private static final int HEX_ID = 122;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private Settings _previousSettings;
	private Player _player;
	private Player _observer;
	private Trainer _trainer;
	private Path _fixtures;
	private boolean _previousStoreSkillCooltime;
	private boolean _previousModifySkillDuration;
	private Map<Integer, Integer> _previousSkillDurationList;

	@Override
	public String id()
	{
		return "qol-effect-duration";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "L2-QOL-003 effect duration suite used the wrong deterministic seed.");
		_environment.initialize(context);
		_player = Player.load(_environment.primary().objectId());
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_player != null) && (_observer != null), "L2-QOL-003 fixture Players did not load.");
		_trainer = new Trainer(NpcData.getInstance().getTemplate(TRAINER_ID));
		_previousSettings = install(allowlisted(2.0, 5.0, 10.0, Map.of()));
		_previousStoreSkillCooltime = PlayerConfig.STORE_SKILL_COOLTIME;
		_previousModifySkillDuration = PlayerConfig.ENABLE_MODIFY_SKILL_DURATION;
		_previousSkillDurationList = PlayerConfig.SKILL_DURATION_LIST;
		_fixtures = context.reportsDirectory().resolve("qol-effect-duration-fixtures");
		Files.createDirectories(_fixtures);
		context.record("qol003.database", "l2jmobiush5_phantom_test");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-config-backward-compatibility-bounds-and-isolation", this::configAndIsolation);
		registry.add("02-pure-policy-categories-overrides-and-exclusions", this::purePolicy);
		registry.add("03-actual-h5-skill-and-reload-aware-music-classification", this::actualH5Classification);
		registry.add("04-per-recipient-stock-order-template-immutability-and-subclass", this::perRecipientIntegration);
		registry.add("05-explicit-time-recast-and-relog-do-not-multiply-twice", this::explicitRecastAndRelog);
	}

	private void configAndIsolation(PhantomTestContext context) throws Exception
	{
		final Settings shipped = PersonalCharacterQoLConfig.read(Path.of("config/Custom/PersonalCharacterQoL.ini"));
		final EffectDurationSettings shippedDuration = shipped.effectDurationSettings();
		PhantomAssertions.assertTrue(shipped.valid() && !shipped.enabled(), "Personal Character QoL is not shipped OFF.");
		PhantomAssertions.assertTrue(shippedDuration.valid() && !shippedDuration.enabled(), "Personal effect durations are not shipped OFF.");
		PhantomAssertions.assertEquals(1.0, shippedDuration.buffMultiplier(), "Shipped buff multiplier is not 1.0.");
		PhantomAssertions.assertEquals(1.0, shippedDuration.danceMultiplier(), "Shipped dance multiplier is not 1.0.");
		PhantomAssertions.assertEquals(1.0, shippedDuration.songMultiplier(), "Shipped song multiplier is not 1.0.");
		PhantomAssertions.assertTrue(shippedDuration.overrides().isEmpty(), "Shipped duration overrides are not empty.");

		final Settings legacy = PersonalCharacterQoLConfig.read(write("legacy-qol002.ini", baseConfig("True", "True", "True", Integer.toString(_player.getObjectId()), "")));
		PhantomAssertions.assertTrue(legacy.valid() && legacy.crossClassSkillsEnabled() && legacy.crystallizationEnabled(), "Legacy QOL-002 configuration was not retained.");
		PhantomAssertions.assertTrue(legacy.effectDurationSettings().valid() && !legacy.effectDurationSettings().enabled(), "Missing QOL-003 keys did not select valid disabled defaults.");
		final Settings masterOff = PersonalCharacterQoLConfig.read(write("duration-master-off.ini", baseConfig("False", "True", "True", Integer.toString(_player.getObjectId()), "") + "EnablePersonalEffectDurations=True\nPersonalBuffDurationMultiplier=2.0\nPersonalDanceDurationMultiplier=5.0\nPersonalSongDurationMultiplier=10.0\nPersonalEffectDurationOverrides=1068,3.0\n"));
		PhantomAssertions.assertTrue(masterOff.valid() && !masterOff.enabled() && !masterOff.effectDurationSettings().enabled(), "Disabled Personal Character QoL master allowed duration settings to activate.");

		final Settings bounds = PersonalCharacterQoLConfig.read(write("duration-bounds.ini", durationConfig("True", "0.01", "100.0", "2.5", "1068,3.0")));
		PhantomAssertions.assertTrue(bounds.valid() && bounds.effectDurationSettings().valid() && bounds.effectDurationSettings().enabled(), "Valid duration bounds were rejected.");
		PhantomAssertions.assertEquals(0.01, bounds.effectDurationSettings().buffMultiplier(), "Lower duration bound changed.");
		PhantomAssertions.assertEquals(100.0, bounds.effectDurationSettings().danceMultiplier(), "Upper duration bound changed.");
		PhantomAssertions.assertEquals(3.0, bounds.effectDurationSettings().overrides().get(MIGHT_ID), "Per-skill override was not parsed.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> bounds.effectDurationSettings().overrides().put(1, 1.0), "Duration override map is mutable.");

		final String[] invalidMultipliers = {"0", "-1", "100.01", "NaN", "Infinity", "1,5", "broken"};
		for (int i = 0; i < invalidMultipliers.length; i++)
		{
			final Settings invalid = PersonalCharacterQoLConfig.read(write("invalid-duration-" + i + ".ini", durationConfig("True", invalidMultipliers[i], "1.0", "1.0", "")));
			assertDurationOnlyInvalid(invalid, "Malformed duration multiplier was accepted: " + invalidMultipliers[i]);
		}
		assertDurationOnlyInvalid(PersonalCharacterQoLConfig.read(write("invalid-duration-switch.ini", durationConfig("maybe", "1.0", "1.0", "1.0", ""))), "Malformed duration switch was accepted.");
		assertDurationOnlyInvalid(PersonalCharacterQoLConfig.read(write("partial-duration.ini", baseConfig("True", "True", "True", Integer.toString(_player.getObjectId()), "") + "EnablePersonalEffectDurations=True\n")), "Partial duration configuration was accepted.");
		assertDurationOnlyInvalid(PersonalCharacterQoLConfig.read(write("duplicate-override.ini", durationConfig("True", "1.0", "1.0", "1.0", "1068,2.0;1068,3.0"))), "Duplicate duration override was accepted.");
		assertDurationOnlyInvalid(PersonalCharacterQoLConfig.read(write("invalid-override.ini", durationConfig("True", "1.0", "1.0", "1.0", "0,2.0"))), "Non-positive override skill identifier was accepted.");
		final StringJoiner tooMany = new StringJoiner(";");
		for (int i = 1; i <= 257; i++)
		{
			tooMany.add(i + ",1.0");
		}
		assertDurationOnlyInvalid(PersonalCharacterQoLConfig.read(write("too-many-overrides.ini", durationConfig("True", "1.0", "1.0", "1.0", tooMany.toString()))), "Unbounded override count was accepted.");
		assertDurationOnlyInvalid(PersonalCharacterQoLConfig.read(write("too-long-overrides.ini", durationConfig("True", "1.0", "1.0", "1.0", "1,1.0;" + "x".repeat(4096)))), "Unbounded override input length was accepted.");

		final Settings isolated = PersonalCharacterQoLConfig.read(write("isolated-duration.ini", durationConfig("True", "broken", "1.0", "1.0", "")));
		install(isolated);
		PhantomAssertions.assertTrue(PersonalCharacterQoLService.getInstance().isCrossClassSkillsEnabled(_player), "Malformed duration settings disabled QOL-002 cross-class skills.");
		PhantomAssertions.assertTrue(PersonalCharacterQoLService.getInstance().isCrystallizationEnabled(_player), "Malformed duration settings disabled QOL-002 crystallization.");
		final PersonalPremiumQoLService.RuntimeState activeQol001 = PersonalPremiumQoLService.build(new PersonalPremiumQoLConfig.Settings(true, false, PersonalPremiumQoLConfig.DEFAULT_LEVEL_GAP_ITEMS_FILE, true, "QOL-003 isolation test."), Path.of("."));
		final PersonalPremiumQoLService.RuntimeState previousQol001 = PersonalPremiumQoLService.installForTests(activeQol001);
		try
		{
			PhantomAssertions.assertTrue(PersonalPremiumQoLService.getInstance().isEnabled(), "Malformed QOL-003 configuration disabled valid QOL-001 state.");
		}
		finally
		{
			PersonalPremiumQoLService.installForTests(previousQol001);
			install(allowlisted(2.0, 5.0, 10.0, Map.of()));
		}
		context.record("qol003.config", "shippedOff=true;legacyQol002=true;bounds=0.01..100;overrideLimit=256;qol001/002=isolated");
	}

	private void purePolicy(PhantomTestContext context)
	{
		final EffectDurationSettings settings = durationSettings(2.0, 5.0, 10.0, Map.of(MIGHT_ID, 3.0));
		assertPolicy(300, Decision.ADJUSTED_BUFF, true, 100, traits(MIGHT_ID, Category.BUFF, false), settings, "Buff override did not replace the category multiplier.");
		assertPolicy(500, Decision.ADJUSTED_DANCE, true, 100, traits(DANCE_ID, Category.DANCE, true), settings, "Dance multiplier changed.");
		assertPolicy(1000, Decision.ADJUSTED_SONG, true, 100, traits(SONG_ID, Category.SONG, true), settings, "Song multiplier changed.");
		assertPolicy(101, Decision.ADJUSTED_BUFF, true, 101, traits(1, Category.BUFF, false), durationSettings(1.0, 1.0, 1.0, Map.of()), "A 1.0 multiplier changed stock duration.");
		assertPolicy(5, Decision.ADJUSTED_BUFF, true, 3, traits(1, Category.BUFF, false), durationSettings(1.5, 1.0, 1.0, Map.of()), "Duration rounding is not deterministic Math.round behavior.");
		assertPolicy(1, Decision.ADJUSTED_BUFF, true, 1, traits(1, Category.BUFF, false), durationSettings(0.01, 1.0, 1.0, Map.of()), "Positive adjusted duration was not clamped to one second.");
		assertPolicy(Integer.MAX_VALUE, Decision.ADJUSTED_BUFF, true, 30_000_000, traits(1, Category.BUFF, false), durationSettings(100.0, 1.0, 1.0, Map.of()), "Duration overflow did not saturate.");
		assertPolicy(100, Decision.DISABLED, true, 100, traits(1, Category.BUFF, false), EffectDurationSettings.disabled("Test."), "Disabled policy changed stock time.");
		assertPolicy(100, Decision.INELIGIBLE_RECIPIENT, false, 100, traits(1, Category.BUFF, false), settings, "Ineligible recipient changed stock time.");
		assertPolicy(0, Decision.NON_POSITIVE_STOCK, true, 0, traits(1, Category.BUFF, false), settings, "Non-positive stock time changed.");
		assertPolicy(100, Decision.MISSING_SKILL, true, 100, new SkillTraits(0, false, false, false, false, false, false, false, false, false, Category.UNKNOWN), settings, "Missing skill changed stock time.");
		assertExcluded(settings, new SkillTraits(1, true, true, false, false, false, false, false, true, false, Category.BUFF), Decision.PASSIVE);
		assertExcluded(settings, new SkillTraits(1, true, false, true, false, false, false, false, true, false, Category.BUFF), Decision.TOGGLE);
		assertExcluded(settings, new SkillTraits(1, true, false, false, true, false, false, false, true, false, Category.BUFF), Decision.TRIGGERED);
		assertExcluded(settings, new SkillTraits(1, true, false, false, false, true, false, false, true, false, Category.BUFF), Decision.ABNORMAL_INSTANT);
		assertExcluded(settings, new SkillTraits(MIGHT_ID, true, false, false, false, false, true, false, true, false, Category.BUFF), Decision.DEBUFF);
		assertExcluded(settings, new SkillTraits(MIGHT_ID, true, false, false, false, false, false, true, true, false, Category.BUFF), Decision.NEGATIVE_EFFECT);
		assertExcluded(settings, new SkillTraits(1, true, false, false, false, false, false, false, false, false, Category.BUFF), Decision.NON_CONTINUOUS);
		assertExcluded(settings, new SkillTraits(1, true, false, false, false, false, false, false, true, true, Category.UNKNOWN), Decision.UNKNOWN_MUSIC);
		context.record("qol003.policy", "buff/dance/song=true;override=replaces;round/min/saturation=true;strictExclusions=10");
	}

	private void actualH5Classification(PhantomTestContext context)
	{
		final Skill might = skill(MIGHT_ID, MIGHT_LEVEL);
		final Skill song = skill(SONG_ID, 1);
		final Skill dance = skill(DANCE_ID, 1);
		final Skill hex = skill(HEX_ID, 15);
		PhantomAssertions.assertTrue(might.isContinuous() && !might.isDance() && !might.isDebuff() && !might.hasNegativeEffect(), "Actual H5 Might metadata changed.");
		PhantomAssertions.assertTrue(song.isContinuous() && song.isDance() && !song.isDebuff(), "Actual H5 Song of Hunter metadata changed.");
		PhantomAssertions.assertTrue(dance.isContinuous() && dance.isDance() && !dance.isDebuff(), "Actual H5 Dance of Fire metadata changed.");
		PhantomAssertions.assertTrue(hex.isContinuous() && hex.isDebuff() && hex.hasNegativeEffect(), "Actual H5 Hex metadata changed.");
		PhantomAssertions.assertTrue(owns(PlayerClass.SWORDSINGER, SONG_ID) && owns(PlayerClass.SWORD_MUSE, SONG_ID), "Song of Hunter is absent from canonical song lineage.");
		PhantomAssertions.assertTrue(owns(PlayerClass.BLADEDANCER, DANCE_ID) && owns(PlayerClass.SPECTRAL_DANCER, DANCE_ID), "Dance of Fire is absent from canonical dance lineage.");

		final PersonalEffectMusicClassifier classifier = PersonalEffectMusicClassifier.getInstance();
		classifier.refresh(SkillTreeData.getInstance());
		PhantomAssertions.assertEquals(Category.SONG, classifier.classify(song), "Actual H5 song was not classified by ownership.");
		PhantomAssertions.assertEquals(Category.DANCE, classifier.classify(dance), "Actual H5 dance was not classified by ownership.");
		PhantomAssertions.assertEquals(0, classifier.conflictCount(), "Canonical H5 song/dance ownership contains conflicts.");
		final Skill unknownMusic = syntheticSkill(900_003, 30, 3);
		PhantomAssertions.assertEquals(Category.UNKNOWN, classifier.classify(unknownMusic), "Unknown magic-type 3 skill was classified as player music.");
		SkillTreeData.getInstance().load();
		PhantomAssertions.assertEquals(Category.SONG, classifier.classify(song), "Song classification remained stale after SkillTreeData reload.");
		PhantomAssertions.assertEquals(Category.DANCE, classifier.classify(dance), "Dance classification remained stale after SkillTreeData reload.");
		context.record("qol003.h5", "might=1068; song=269/SWORDSINGER+SWORD_MUSE; dance=274/BLADEDANCER+SPECTRAL_DANCER; hex=122; conflicts=0;reload=true");
	}

	private void perRecipientIntegration(PhantomTestContext context)
	{
		install(allowlisted(2.0, 5.0, 10.0, Map.of()));
		final Skill might = skill(MIGHT_ID, MIGHT_LEVEL);
		final int templateTime = might.getAbnormalTime();
		final int playerStock = Formulas.calcEffectAbnormalTime(_trainer, _player, might);
		final int observerStock = Formulas.calcEffectAbnormalTime(_trainer, _observer, might);
		PhantomAssertions.assertEquals(scale(playerStock, 2.0), new BuffInfo(_trainer, _player, might).getAbnormalTime(), "Allowed Player did not receive the personal buff duration.");
		final int selfStock = Formulas.calcEffectAbnormalTime(_player, _player, might);
		PhantomAssertions.assertEquals(scale(selfStock, 2.0), new BuffInfo(_player, _player, might).getAbnormalTime(), "Allowed Player self-cast did not receive the personal buff duration.");
		PhantomAssertions.assertEquals(observerStock, new BuffInfo(_trainer, _observer, might).getAbnormalTime(), "The same Skill template changed an ordinary Player duration.");
		PhantomAssertions.assertEquals(templateTime, might.getAbnormalTime(), "Per-recipient duration mutated the shared Skill template.");

		try (Player.OutboundSessionAttachment ignored = _player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32)))
		{
			PhantomAssertions.assertEquals(playerStock, new BuffInfo(_trainer, _player, might).getAbnormalTime(), "Headless Player received personal duration.");
		}
		PhantomAssertions.assertEquals(Formulas.calcEffectAbnormalTime(_trainer, _trainer, might), new BuffInfo(_trainer, _trainer, might).getAbnormalTime(), "NPC recipient duration changed.");
		final Servitor servitor = new Servitor(NpcData.getInstance().getTemplate(TRAINER_ID), _player);
		try
		{
			PhantomAssertions.assertEquals(Formulas.calcEffectAbnormalTime(_trainer, servitor, might), new BuffInfo(_trainer, servitor, might).getAbnormalTime(), "Summon recipient duration changed.");
		}
		finally
		{
			servitor.deleteMe();
		}

		PhantomAssertions.assertTrue(_player.addSubClass(PlayerClass.ARCHMAGE.getId(), 1), "Could not create active-subclass duration fixture.");
		_player.setActiveClass(1);
		PhantomAssertions.assertTrue(_player.isSubClassActive(), "Duration subclass fixture is not active.");
		final int subclassStock = Formulas.calcEffectAbnormalTime(_trainer, _player, might);
		PhantomAssertions.assertEquals(scale(subclassStock, 2.0), new BuffInfo(_trainer, _player, might).getAbnormalTime(), "Allowlisted active subclass did not receive personal duration.");
		_player.setActiveClass(0);

		PlayerConfig.ENABLE_MODIFY_SKILL_DURATION = true;
		PlayerConfig.SKILL_DURATION_LIST = _previousSkillDurationList == null ? new HashMap<>() : new HashMap<>(_previousSkillDurationList);
		PlayerConfig.SKILL_DURATION_LIST.put(900_001, 1500);
		final Skill globallyModified = syntheticSkill(900_001, 30, 1);
		PhantomAssertions.assertEquals(1500, globallyModified.getAbnormalTime(), "Synthetic global SkillDurationList fixture was not applied to the template first.");
		PhantomAssertions.assertEquals(3000, new BuffInfo(_trainer, _player, globallyModified).getAbnormalTime(), "Personal duration was not applied after global template duration.");
		PhantomAssertions.assertEquals(1500, globallyModified.getAbnormalTime(), "Global/personal ordering mutated the synthetic Skill template.");
		PlayerConfig.ENABLE_MODIFY_SKILL_DURATION = _previousModifySkillDuration;
		PlayerConfig.SKILL_DURATION_LIST = _previousSkillDurationList;

		final Skill song = skill(SONG_ID, 1);
		final Skill dance = skill(DANCE_ID, 1);
		final Skill hex = skill(HEX_ID, 15);
		final int songStock = Formulas.calcEffectAbnormalTime(_trainer, _player, song);
		final int danceStock = Formulas.calcEffectAbnormalTime(_trainer, _player, dance);
		final int hexStock = Formulas.calcEffectAbnormalTime(_trainer, _player, hex);
		PhantomAssertions.assertEquals(scale(songStock, 10.0), new BuffInfo(_trainer, _player, song).getAbnormalTime(), "Actual H5 song multiplier was not applied.");
		PhantomAssertions.assertEquals(scale(danceStock, 5.0), new BuffInfo(_trainer, _player, dance).getAbnormalTime(), "Actual H5 dance multiplier was not applied.");
		PhantomAssertions.assertEquals(hexStock, new BuffInfo(_trainer, _player, hex).getAbnormalTime(), "Actual H5 debuff duration changed.");
		context.record("qol003.recipient", "allowed/activeSubclass=scaled;ordinary/headless/npc/summon=stock;template=immutable;globalThenPersonal=true");
	}

	private void explicitRecastAndRelog(PhantomTestContext context)
	{
		install(allowlisted(2.0, 5.0, 10.0, Map.of(MIGHT_ID, 3.0)));
		final Skill might = skill(MIGHT_ID, MIGHT_LEVEL);
		stopMight(_player);
		might.applyEffects(_trainer, _player, true, 37);
		PhantomAssertions.assertEquals(37, activeMight().getAbnormalTime(), "Positive explicit abnormalTime was multiplied.");
		final BuffInfo stolenCopy = new BuffInfo(_observer, _player, might);
		stolenCopy.setAbnormalTime(29);
		PhantomAssertions.assertEquals(29, stolenCopy.getAbnormalTime(), "Steal/copy remaining time was multiplied after its authoritative override.");

		stopMight(_player);
		might.applyEffects(_trainer, _player);
		final int firstRecast = activeMight().getAbnormalTime();
		final int expected = scale(Formulas.calcEffectAbnormalTime(_trainer, _player, might), 3.0);
		PhantomAssertions.assertEquals(expected, firstRecast, "Per-skill override did not replace the buff multiplier at applyEffects.");
		might.applyEffects(_trainer, _player);
		PhantomAssertions.assertEquals(firstRecast, activeMight().getAbnormalTime(), "Effect recast multiplied an already adjusted duration.");

		stopMight(_player);
		might.applyEffects(_trainer, _player, true, 71);
		PhantomAssertions.assertEquals(71, activeMight().getAbnormalTime(), "Relog fixture explicit duration changed before store.");
		PlayerConfig.STORE_SKILL_COOLTIME = true;
		_player.store(true);
		_player.deleteMe();
		_player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_player != null, "Personal duration fixture did not reload.");
		_player.restoreEffects();
		final int restored = activeMight().getAbnormalTime();
		PhantomAssertions.assertTrue((restored > 0) && (restored <= 71), "Stored remaining duration was not restored exactly as an authoritative custom time.");
		PhantomAssertions.assertTrue(restored < 100, "Restored remaining duration was multiplied a second time.");
		context.record("qol003.exactTime", "explicit=37;stolenCopy=29;override=3x;recast=noMultiplierSquared;stored=71;restored=" + restored);
	}

	private void assertDurationOnlyInvalid(Settings settings, String message)
	{
		PhantomAssertions.assertTrue(settings.valid() && settings.enabled() && settings.crossClassSkillsEnabled() && settings.crystallizationEnabled(), message + " Existing QOL-002 state was not retained.");
		PhantomAssertions.assertFalse(settings.effectDurationSettings().valid() || settings.effectDurationSettings().enabled(), message);
	}

	private static void assertPolicy(int expectedTime, Decision expectedDecision, boolean eligible, int stock, SkillTraits traits, EffectDurationSettings settings, String message)
	{
		final Result result = PersonalEffectDurationPolicy.adjust(eligible, stock, traits, settings);
		PhantomAssertions.assertEquals(expectedTime, result.abnormalTime(), message);
		PhantomAssertions.assertEquals(expectedDecision, result.decision(), message + " Diagnostic category changed.");
	}

	private static void assertExcluded(EffectDurationSettings settings, SkillTraits traits, Decision decision)
	{
		assertPolicy(100, decision, true, 100, traits, settings, "Strict exclusion " + decision + " changed stock duration or was bypassed by an override.");
	}

	private static SkillTraits traits(int skillId, Category category, boolean music)
	{
		return new SkillTraits(skillId, true, false, false, false, false, false, false, true, music, category);
	}

	private Settings allowlisted(double buff, double dance, double song, Map<Integer, Double> overrides)
	{
		return new Settings(true, false, false, Set.of(_player.getObjectId()), Set.of(), true, "Test configuration.", durationSettings(buff, dance, song, overrides));
	}

	private static EffectDurationSettings durationSettings(double buff, double dance, double song, Map<Integer, Double> overrides)
	{
		return new EffectDurationSettings(true, buff, dance, song, overrides, true, "Test duration configuration.");
	}

	private Settings install(Settings settings)
	{
		return PersonalCharacterQoLService.getInstance().installForTests(settings);
	}

	private static Skill skill(int id, int level)
	{
		final Skill skill = SkillData.getInstance().getSkill(id, level);
		PhantomAssertions.assertTrue(skill != null, "Required H5 skill " + id + "/" + level + " is absent.");
		return skill;
	}

	private static boolean owns(PlayerClass playerClass, int skillId)
	{
		return SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values().stream().anyMatch(value -> value.getSkillId() == skillId);
	}

	private static Skill syntheticSkill(int id, int abnormalTime, int magicType)
	{
		final StatSet set = new StatSet();
		set.set("skill_id", id);
		set.set("level", 1);
		set.set("name", "QOL-003 synthetic fixture");
		set.set("operateType", SkillOperateType.A2);
		set.set("isMagic", magicType);
		set.set("abnormalTime", abnormalTime);
		return new Skill(set);
	}

	private static int scale(int stock, double multiplier)
	{
		final double product = stock * multiplier;
		return product >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) Math.round(product));
	}

	private BuffInfo activeMight()
	{
		final BuffInfo info = _player.getEffectList().getBuffInfoBySkillId(MIGHT_ID);
		PhantomAssertions.assertTrue(info != null, "Might effect was not applied to the test Player.");
		return info;
	}

	private static void stopMight(Player player)
	{
		if (player != null)
		{
			player.getEffectList().stopSkillEffects(SkillFinishType.REMOVED, MIGHT_ID);
		}
	}

	private Path write(String name, String content) throws Exception
	{
		final Path path = _fixtures.resolve(name);
		Files.writeString(path, content, StandardCharsets.UTF_8);
		return path;
	}

	private String durationConfig(String enabled, String buff, String dance, String song, String overrides)
	{
		return baseConfig("True", "True", "True", Integer.toString(_player.getObjectId()), "") + "EnablePersonalEffectDurations=" + enabled + "\nPersonalBuffDurationMultiplier=" + buff + "\nPersonalDanceDurationMultiplier=" + dance + "\nPersonalSongDurationMultiplier=" + song + "\nPersonalEffectDurationOverrides=" + overrides + "\n";
	}

	private static String baseConfig(String master, String skills, String crystallization, String ids, String accounts)
	{
		return "EnablePersonalCharacterQoL=" + master + "\nEnablePersonalCrossClassSkills=" + skills + "\nEnablePersonalCrystallization=" + crystallization + "\nAllowedCharacterIds=" + ids + "\nAllowedAccounts=" + accounts + "\n";
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			stopMight(_player);
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
			if (_trainer != null)
			{
				_trainer.deleteMe();
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		finally
		{
			PlayerConfig.STORE_SKILL_COOLTIME = _previousStoreSkillCooltime;
			PlayerConfig.ENABLE_MODIFY_SKILL_DURATION = _previousModifySkillDuration;
			PlayerConfig.SKILL_DURATION_LIST = _previousSkillDurationList;
			install(_previousSettings);
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
}

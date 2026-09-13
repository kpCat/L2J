/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.l2jmobius.commons.network.ReadableBuffer;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.handler.BypassHandler;
import org.l2jmobius.gameserver.handler.IBypassHandler;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Trainer;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.skill.enums.AcquireSkillType;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.network.clientpackets.RequestAcquireSkill;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** L2-QOL-002 personal access and native CLASS learning coverage. */
public final class QoLPersonalSkillsSuite implements PhantomTestSuite
{
	private static final long SEED = 1002001L;
	private static final int TRAINER_ID = 30516;
	private static final int REQUIRED_BOOK_ID = 8618;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private Settings _previousSettings;
	private Player _player;
	private Player _observer;
	private Trainer _trainer;
	private QoLTestClient _network;
	private boolean _previousAltSkillLearn;
	private boolean _previousDivineBook;
	private Path _fixtures;

	@Override
	public String id()
	{
		return "qol-personal-skills";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "L2-QOL-002 personal skill suite used the wrong deterministic seed.");
		_environment.initialize(context);
		_player = Player.load(_environment.primary().objectId());
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_player != null) && (_observer != null), "L2-QOL-002 skill fixture Players did not load.");
		_previousAltSkillLearn = PlayerConfig.ALT_GAME_SKILL_LEARN;
		_previousDivineBook = PlayerConfig.DIVINE_SP_BOOK_NEEDED;
		PlayerConfig.ALT_GAME_SKILL_LEARN = false;
		PlayerConfig.DIVINE_SP_BOOK_NEEDED = true;
		_player.setBaseClass(PlayerClass.GLADIATOR);
		_player.setPlayerClass(PlayerClass.GLADIATOR.getId());
		_player.getStat().setLevel((byte) 80);
		_player.setSp(10_000_000);
		_trainer = new Trainer(NpcData.getInstance().getTemplate(TRAINER_ID));
		_trainer.setInstanceId(_player.getInstanceId());
		_trainer.spawnMe(_player.getX() + 10, _player.getY(), _player.getZ());
		_player.setLastFolkNPC(_trainer);
		_previousSettings = install(allowByCharacter(true, true));
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		_fixtures = context.reportsDirectory().resolve("qol-personal-skills-fixtures");
		Files.createDirectories(_fixtures);
		context.record("qol002.database", "l2jmobiush5_phantom_test");
		context.record("qol002.globalAltGameSkillLearn", false);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-shipped-off-strict-config-and-qol001-isolation", this::configAndIsolation);
		registry.add("02-character-account-headless-subclass-and-trainer-policy", this::accessAndTrainerPolicy);
		registry.add("03-native-foreign-class-acquire-sp-items-and-relog", this::nativeAcquireAndRelog);
		registry.add("04-execute-time-stale-denial-and-legacy-price-parity", this::staleAndPriceParity);
	}

	private void configAndIsolation(PhantomTestContext context) throws Exception
	{
		final Settings shipped = PersonalCharacterQoLConfig.read(Path.of("config/Custom/PersonalCharacterQoL.ini"));
		PhantomAssertions.assertTrue(shipped.valid() && !shipped.enabled() && shipped.allowedCharacterIds().isEmpty() && shipped.allowedAccounts().isEmpty(), "Personal Character QoL was not shipped OFF with empty allowlists.");
		PhantomAssertions.assertFalse(PersonalCharacterQoLConfig.read(write("missing.ini", null)).enabled(), "Missing personal configuration did not fail closed.");
		PhantomAssertions.assertFalse(PersonalCharacterQoLConfig.read(write("invalid-switch.ini", config("maybe", "True", "True", "1", "account"))).valid(), "Malformed switch was accepted.");
		PhantomAssertions.assertFalse(PersonalCharacterQoLConfig.read(write("invalid-id.ini", config("True", "True", "True", "1,broken", "account"))).valid(), "Malformed character identifier was accepted.");
		PhantomAssertions.assertFalse(PersonalCharacterQoLConfig.read(write("empty-token.ini", config("True", "True", "True", "1,,2", "account"))).valid(), "Empty allowlist token was accepted.");
		final Settings normalized = PersonalCharacterQoLConfig.read(write("normalized.ini", config("True", "True", "False", "7;7", "Account_A,account_a")));
		PhantomAssertions.assertEquals(Set.of(7), normalized.allowedCharacterIds(), "Duplicate character IDs were not normalized.");
		PhantomAssertions.assertEquals(Set.of("account_a"), normalized.allowedAccounts(), "Account names were not normalized case-insensitively.");
		install(new Settings(true, true, true, Set.of(), Set.of(), true, "Empty allowlist test."));
		PhantomAssertions.assertFalse(PersonalCharacterQoLService.getInstance().isPersonalUser(_player), "An empty allowlist admitted a Player.");
		install(new Settings(false, true, true, Set.of(_player.getObjectId()), Set.of(), true, "Master gate test."));
		PhantomAssertions.assertFalse(PersonalCharacterQoLService.getInstance().isPersonalUser(_player), "Disabled master gate admitted a Player.");
		install(new Settings(true, true, false, Set.of(_player.getObjectId()), Set.of(), true, "Independent subfeature test."));
		PhantomAssertions.assertTrue(PersonalCharacterQoLService.getInstance().isCrossClassSkillsEnabled(_player), "Enabled skill subfeature was denied.");
		PhantomAssertions.assertFalse(PersonalCharacterQoLService.getInstance().isCrystallizationEnabled(_player), "Disabled crystallization subfeature was admitted.");

		final PersonalPremiumQoLService.RuntimeState activeQol001 = PersonalPremiumQoLService.build(new PersonalPremiumQoLConfig.Settings(true, false, PersonalPremiumQoLConfig.DEFAULT_LEVEL_GAP_ITEMS_FILE, true, "QOL-001 isolation test."), Path.of("."));
		final PersonalPremiumQoLService.RuntimeState previousQol001 = PersonalPremiumQoLService.installForTests(activeQol001);
		try
		{
			install(PersonalCharacterQoLConfig.read(write("isolated-invalid.ini", config("True", "broken", "True", "1", "account"))));
			PhantomAssertions.assertFalse(PersonalCharacterQoLService.getInstance().isPersonalUser(_player), "Malformed QOL-002 configuration did not fail closed.");
			PhantomAssertions.assertTrue(PersonalPremiumQoLService.getInstance().isEnabled(), "Malformed QOL-002 configuration disabled valid QOL-001 state.");
		}
		finally
		{
			PersonalPremiumQoLService.installForTests(previousQol001);
			install(allowByCharacter(true, true));
		}
		context.record("qol002.configControls", "shippedOff=true;strict=4;duplicates=normalized;qol001=isolated");
	}

	private void accessAndTrainerPolicy(PhantomTestContext context) throws Exception
	{
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		PhantomAssertions.assertTrue(service.isCrossClassSkillsEnabled(_player), "Allowlisted character ID was denied.");
		PhantomAssertions.assertFalse(service.isCrossClassSkillsEnabled(_observer), "Non-allowlisted character was admitted.");
		PhantomAssertions.assertFalse(service.canOpenAlternativeSkillList(_observer, _trainer), "Non-allowlisted character could open a foreign CLASS list.");
		install(new Settings(true, true, false, Set.of(), Set.of(_player.getAccountNamePlayer().toLowerCase(java.util.Locale.ROOT)), true, "Account test."));
		PhantomAssertions.assertTrue(service.isCrossClassSkillsEnabled(_player), "Normalized account allowlist was denied.");

		try (Player.OutboundSessionAttachment ignored = _player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32)))
		{
			PhantomAssertions.assertFalse(service.isCrossClassSkillsEnabled(_player), "Headless Phantom was admitted to personal cross-class learning.");
		}
		install(allowByCharacter(true, true));
		PhantomAssertions.assertTrue(service.canAcquireClassSkill(_player, _trainer, PlayerClass.ARTISAN), "Production trainer-owned same-tier foreign class was denied.");
		PhantomAssertions.assertFalse(service.canAcquireClassSkill(_player, _trainer, PlayerClass.FORTUNE_SEEKER), "Higher hierarchy class was admitted.");
		final Trainer wrongTrainer = new Trainer(NpcData.getInstance().getTemplate(30006));
		PhantomAssertions.assertFalse(service.canAcquireClassSkill(_player, wrongTrainer, PlayerClass.ARTISAN), "Trainer without the selected production teach class was admitted.");
		wrongTrainer.deleteMe();

		PhantomAssertions.assertTrue(_player.addSubClass(PlayerClass.ARCHMAGE.getId(), 1), "Could not create subclass control fixture.");
		_player.setActiveClass(1);
		PhantomAssertions.assertFalse(service.isCrossClassSkillsEnabled(_player), "Active subclass was admitted.");
		_player.setActiveClass(0);
		_player.setPlayerClass(PlayerClass.GLADIATOR.getId());
		_player.getStat().setLevel((byte) 80);
		_player.setLastFolkNPC(_trainer);

		final IBypassHandler skillList = BypassHandler.getInstance().getHandler("SkillList");
		PhantomAssertions.assertTrue(skillList != null, "MasterHandler did not register the stock SkillList handler.");
		skillList.onCommand("SkillList 56", _player, _trainer);
		PhantomAssertions.assertEquals(PlayerClass.ARTISAN, _player.getLearningClass(), "Personal SkillList did not select the trainer-owned foreign class.");
		final var available = SkillTreeData.getInstance().getAvailableSkills(_player, PlayerClass.ARTISAN, false, false);
		PhantomAssertions.assertTrue(available.stream().allMatch(value -> value.isLearnedByNpc() && !value.isAutoGet() && !value.isLearnedByFS()), "Personal list expanded auto-get or Forgotten Scroll skills.");
		context.record("qol002.accessPolicy", "char/account=true;ordinary/headless/subclass/wrongTrainer/higherTier=false");
	}

	private void nativeAcquireAndRelog(PhantomTestContext context) throws Exception
	{
		_network = QoLTestClient.attach(_player);
		selectClass(PlayerClass.ARTISAN);
		final SkillLearn crystallize = learn(PlayerClass.ARTISAN, 248, 1);
		_player.getStat().setLevel((byte) (crystallize.getGetLevel() - 1));
		final long minimumLevelSp = _player.getSp();
		acquire(crystallize);
		PhantomAssertions.assertEquals(0, _player.getSkillLevel(248), "Personal foreign CLASS ignored the minimum player level.");
		PhantomAssertions.assertEquals(minimumLevelSp, _player.getSp(), "Minimum-level rejection deducted SP.");
		_player.getStat().setLevel((byte) 80);

		selectClass(PlayerClass.BOUNTY_HUNTER);
		final SkillLearn skippedCrystallize = learn(PlayerClass.BOUNTY_HUNTER, 248, 2);
		final long previousLevelSp = _player.getSp();
		acquire(skippedCrystallize);
		PhantomAssertions.assertEquals(0, _player.getSkillLevel(248), "Personal foreign CLASS granted a level without its previous level.");
		PhantomAssertions.assertEquals(previousLevelSp, _player.getSp(), "Previous-level rejection deducted SP.");

		selectClass(PlayerClass.ARTISAN);
		final int displayedCost = crystallize.getCalculatedLevelUpSp(_player.getPlayerClass(), PlayerClass.ARTISAN, true);
		final long spBefore = displayedCost + 123L;
		_player.setSp(spBefore);
		acquire(crystallize);
		PhantomAssertions.assertEquals(1, _player.getSkillLevel(248), "Native RequestAcquireSkill did not learn foreign CRYSTALLIZE.");
		PhantomAssertions.assertEquals(spBefore - displayedCost, _player.getSp(), "Native acquisition SP debit differs from explicit display cost.");

		selectClass(PlayerClass.BOUNTY_HUNTER);
		final SkillLearn divineInspiration = learn(PlayerClass.BOUNTY_HUNTER, 1405, 1);
		PhantomAssertions.assertEquals(1, divineInspiration.getRequiredItems().size(), "Real foreign CLASS required-item fixture changed.");
		final long missingBookSp = _player.getSp();
		acquire(divineInspiration);
		PhantomAssertions.assertEquals(0, _player.getSkillLevel(1405), "Foreign CLASS skill ignored a missing required item.");
		PhantomAssertions.assertEquals(missingBookSp, _player.getSp(), "Missing required-item rejection deducted SP.");
		_player.getInventory().addItem(ItemProcessType.REWARD, REQUIRED_BOOK_ID, 1, _player, this);
		final long bookBefore = _player.getInventory().getInventoryItemCount(REQUIRED_BOOK_ID, -1);
		acquire(divineInspiration);
		PhantomAssertions.assertEquals(1, _player.getSkillLevel(1405), "Native foreign CLASS skill with a required item was not learned.");
		PhantomAssertions.assertEquals(bookBefore - 1, _player.getInventory().getInventoryItemCount(REQUIRED_BOOK_ID, -1), "Native required learning item was not consumed exactly once.");

		_network.close();
		_network = null;
		_player.storeMe();
		_player.deleteMe();
		_player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_player != null, "Personal skill fixture did not reload.");
		PhantomAssertions.assertEquals(1, _player.getSkillLevel(248), "Foreign CRYSTALLIZE did not persist through relog.");
		PhantomAssertions.assertEquals(1, _player.getSkillLevel(1405), "Foreign required-item skill did not persist through relog.");
		_player.setXYZ(0, 0, 0);
		_player.setLastFolkNPC(_trainer);
		_network = QoLTestClient.attach(_player);
		context.record("qol002.nativeAcquire", "crystallize=1;spDebit=" + displayedCost + ";requiredItemDebit=1;relog=true");
	}

	private void staleAndPriceParity(PhantomTestContext context)
	{
		selectClass(PlayerClass.ARTISAN);
		final SkillLearn candidate = SkillTreeData.getInstance().getAvailableSkills(_player, PlayerClass.ARTISAN, false, false).stream().filter(value -> (value.getSkillId() != 248) && (value.getSkillLevel() == 1)).findFirst().orElseThrow();
		final long spBefore = 10_000_000;
		_player.setSp(spBefore);
		install(Settings.disabled("Stale authorization test."));
		sendAcquire(candidate);
		PhantomAssertions.assertEquals(0, _player.getSkillLevel(candidate.getSkillId()), "Stale personal authorization granted a foreign skill.");
		PhantomAssertions.assertEquals(spBefore, _player.getSp(), "Stale personal authorization deducted SP.");

		final int base = candidate.getLevelUpSp();
		PlayerConfig.ALT_GAME_SKILL_LEARN = false;
		PhantomAssertions.assertEquals(base, candidate.getCalculatedLevelUpSp(PlayerClass.GLADIATOR, PlayerClass.ARTISAN), "Legacy 2-arg disabled cost changed.");
		PlayerConfig.ALT_GAME_SKILL_LEARN = true;
		PhantomAssertions.assertTrue(PersonalCharacterQoLService.getInstance().canAcquireClassSkill(_observer, _trainer, PlayerClass.ARTISAN), "Global AltGameSkillLearn no longer preserves its stock admission independently of the personal allowlist.");
		PhantomAssertions.assertEquals(base * 2, candidate.getCalculatedLevelUpSp(PlayerClass.GLADIATOR, PlayerClass.ARTISAN), "Legacy 2-arg same-type cost changed.");
		PhantomAssertions.assertEquals(base * 3, candidate.getCalculatedLevelUpSp(PlayerClass.ARCHMAGE, PlayerClass.ARTISAN, true), "Explicit opposite-type cost is not 3x.");
		PhantomAssertions.assertEquals(base, candidate.getCalculatedLevelUpSp(PlayerClass.ARTISAN, PlayerClass.ARTISAN, true), "Own-class explicit cost is not 1x.");
		PlayerConfig.ALT_GAME_SKILL_LEARN = false;
		install(allowByCharacter(true, true));
		context.record("qol002.priceParity", "own=1x;sameType=2x;oppositeType=3x;stale=noMutation");
	}

	private void selectClass(PlayerClass playerClass)
	{
		_player.setDead(false);
		_player.setFakeDeath(false);
		_player.standUp();
		_player.setPrivateStoreType(org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType.NONE);
		_trainer.setInstanceId(_player.getInstanceId());
		_trainer.setXYZ(_player.getX() + 10, _player.getY(), _player.getZ());
		_player.setLastFolkNPC(_trainer);
		final IBypassHandler skillList = BypassHandler.getInstance().getHandler("SkillList");
		PhantomAssertions.assertTrue(skillList.onCommand("SkillList " + playerClass.getId(), _player, _trainer), "Stock SkillList handler rejected the personal route.");
		PhantomAssertions.assertEquals(playerClass, _player.getLearningClass(), "Stock SkillList did not retain the selected class.");
	}

	private static SkillLearn learn(PlayerClass playerClass, int skillId, int skillLevel)
	{
		return SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values().stream().filter(value -> (value.getSkillId() == skillId) && (value.getSkillLevel() == skillLevel)).findFirst().orElseThrow();
	}

	private void acquire(SkillLearn learn)
	{
		PhantomAssertions.assertEquals(_trainer, _player.getLastFolkNPC(), "Native acquisition lost the selected trainer.");
		PhantomAssertions.assertFalse(_player.isCastingNow() || _player.isCastingSimultaneouslyNow(), "Native acquisition fixture is casting.");
		PhantomAssertions.assertFalse(_player.isDead() || _player.isFakeDeath(), "Native acquisition fixture is dead or fake-dead.");
		PhantomAssertions.assertFalse(_player.isSitting(), "Native acquisition fixture is sitting.");
		PhantomAssertions.assertFalse(_player.isInStoreMode(), "Native acquisition fixture is in store mode.");
		PhantomAssertions.assertEquals(_trainer.getInstanceId(), _player.getInstanceId(), "Native acquisition fixture uses another instance.");
		PhantomAssertions.assertTrue(_trainer.isInsideRadius3D(_player, 250), "Native acquisition fixture is outside the trainer radius.");
		PhantomAssertions.assertTrue(_trainer.canInteract(_player), "Native acquisition fixture cannot interact with its trainer.");
		PhantomAssertions.assertTrue(PersonalCharacterQoLService.getInstance().canAcquireClassSkill(_player, _trainer, _player.getLearningClass()), "Native acquisition policy denied the selected class at execute time.");
		PhantomAssertions.assertTrue(SkillTreeData.getInstance().getSkillLearn(AcquireSkillType.CLASS, learn.getSkillId(), learn.getSkillLevel(), _player) != null, "Native SkillTreeData did not resolve the selected CLASS skill.");
		sendAcquire(learn);
	}

	private void sendAcquire(SkillLearn learn)
	{
		final ByteBuffer bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
		bytes.putInt(learn.getSkillId());
		bytes.putInt(learn.getSkillLevel());
		bytes.putInt(AcquireSkillType.CLASS.ordinal());
		bytes.flip();
		final RequestAcquireSkill packet = new RequestAcquireSkill();
		packet.init(_network.client(), ReadableBuffer.of(bytes));
		PhantomAssertions.assertTrue(packet.read(), "Native RequestAcquireSkill packet did not parse.");
		packet.run();
	}

	private Settings allowByCharacter(boolean skills, boolean crystallization)
	{
		return new Settings(true, skills, crystallization, Set.of(_player.getObjectId()), Set.of(), true, "Test configuration.");
	}

	private Settings install(Settings settings)
	{
		return PersonalCharacterQoLService.getInstance().installForTests(settings);
	}

	private Path write(String name, String content) throws Exception
	{
		final Path path = _fixtures.resolve(name);
		if (content != null)
		{
			Files.writeString(path, content, StandardCharsets.UTF_8);
		}
		return path;
	}

	private static String config(String master, String skills, String crystallization, String ids, String accounts)
	{
		return "EnablePersonalCharacterQoL=" + master + "\nEnablePersonalCrossClassSkills=" + skills + "\nEnablePersonalCrystallization=" + crystallization + "\nAllowedCharacterIds=" + ids + "\nAllowedAccounts=" + accounts + "\n";
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
			PlayerConfig.ALT_GAME_SKILL_LEARN = _previousAltSkillLearn;
			PlayerConfig.DIVINE_SP_BOOK_NEEDED = _previousDivineBook;
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

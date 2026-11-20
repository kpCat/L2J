/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Runs the server-side XML, Player and calculator checks against the isolated 003 fixture. */
public final class SkillEquipmentFreedomNative
{
	private static final String SCHEMA = "l2jmobiush5_phantom_test_gear003";
	private static final int PORT = 13323;
	private static final int[] DANCES = { 271, 272, 273, 274, 275, 276, 277, 307, 309, 310, 311, 365, 366, 367, 530, 765, 915 };

	public static void main(String[] args) throws Exception
	{
		if ((args.length != 1) || (!"on".equals(args[0]) && !"off".equals(args[0])) || !Boolean.getBoolean("phantom.gear003.native"))
		{
			throw new IllegalArgumentException("Explicit isolated Gear Skills 003 mode is required.");
		}
		final Path game = Path.of("").toRealPath();
		final Path config = game.resolve("config/Database.ini");
		final ConfigReader database = new ConfigReader(config.toString());
		final String url = "jdbc:mysql://127.0.0.1:" + PORT + "/" + SCHEMA + "?sslMode=DISABLED";
		if (!game.endsWith(Path.of("gear-skills-003", "dist", "game")) || !url.equals(database.getValue("URL")))
		{
			throw new IllegalArgumentException("Only the exact Gear Skills 003 game root and loopback SQL lane are allowed.");
		}
		final boolean enabled = "on".equals(args[0]);
		Player player = null;
		try
		{
			ConfigLoader.init();
			DatabaseFactory.initFromConfig(config.toString());
			try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var result = query.executeQuery("SELECT DATABASE(), @@port, marker FROM gear003_fixture"))
			{
				if (!result.next() || !SCHEMA.equals(result.getString(1)) || (result.getInt(2) != PORT) || !"native-gear-skills-003".equals(result.getString(3)))
				{
					throw new IllegalStateException("Gear Skills 003 SQL fixture identity mismatch.");
				}
			}
			PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			InitialEquipmentData.getInstance();
			InitialShortcutData.getInstance();
			if (Boolean.getBoolean("phantom.gear003.managedOnly"))
			{
				checkManagedPhantom(enabled);
				if (enabled && Boolean.getBoolean("phantom.gear003.ownerFixture"))
				{
					createOwnerFixture();
				}
				return;
			}
			final String name = "Gear" + Long.toUnsignedString(System.nanoTime(), 36);
			player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "gear003native", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
			if ((player == null) || player.isGM() || (player.getActiveWeaponItem() != null))
			{
				throw new IllegalStateException("Expected an ordinary unequipped Player.");
			}
			player.setOnlineStatus(true, false);
			player.stopAllTasks();
			final Skill dance = SkillData.getInstance().getSkill(307, 1);
			player.addSkill(dance, true);
			final boolean danceAllowed = dance.checkCondition(player, player, false);
			final Skill frenzy = SkillData.getInstance().getSkill(176, 3);
			player.addSkill(frenzy, true);
			player.getStatus().setCurrentHp(player.getMaxHp() * 0.5);
			final double baseAttack = player.calcStat(Stat.POWER_ATTACK, 100);
			final double baseAccuracy = player.calcStat(Stat.ACCURACY_COMBAT, 0);
			frenzy.applyEffects(player, player);
			final double attackRatio = player.calcStat(Stat.POWER_ATTACK, 100) / baseAttack;
			final double accuracyDelta = player.calcStat(Stat.ACCURACY_COMBAT, 0) - baseAccuracy;
			player.getStatus().setCurrentHp(player.getMaxHp() * 0.95);
			final boolean highHpAllowed = frenzy.checkCondition(player, player, false);
			final double highHpRatio = player.calcStat(Stat.POWER_ATTACK, 100) / baseAttack;
			player.stopSkillEffects(SkillFinishType.NORMAL, 176);
			System.out.printf(Locale.ROOT, "GEAR003 enabled=%s dance307=%s frenzy176=%.6f accDelta=%.2f highHpAllowed=%s highHpRatio=%.6f%n", enabled, danceAllowed, attackRatio, accuracyDelta, highHpAllowed, highHpRatio);
			PhantomAssertions.assertEquals(enabled, danceAllowed, "Dance 307 with empty hands");
			near(enabled ? 1.15 * 1.40 * 1.10 : 1.15, attackRatio);
			near(enabled ? 6 : 0, accuracyDelta);
			PhantomAssertions.assertTrue(!highHpAllowed, "Frenzy HP prerequisite must remain in force");
			near(1.15, highHpRatio);
			checkDancesAndMastery(player, enabled);
			checkArmorMasteries(player, enabled);
			checkDanceCast(player, enabled);
			checkManagedPhantom(enabled);
			if (enabled && Boolean.getBoolean("phantom.gear003.ownerFixture"))
			{
				createOwnerFixture();
			}
		}
		finally
		{
			try
			{
				if (player != null)
				{
					player.stopAllTasks();
					player.deleteMe();
				}
			}
			finally
			{
				ThreadPool.shutdown();
				DatabaseFactory.close();
			}
		}
	}

	private static void near(double expected, double actual)
	{
		PhantomAssertions.assertTrue(Math.abs(expected - actual) < 0.0001, "Expected " + expected + ", received " + actual);
	}

	private static void checkDancesAndMastery(Player player, boolean enabled)
	{
		final var inventory = player.getInventory();
		final var stockFists = player.addItem(ItemProcessType.QUEST, 253, 1, null, false);
		final var sword = player.addItem(ItemProcessType.QUEST, 1, 1, null, false);
		final var bow = player.addItem(ItemProcessType.QUEST, 13, 1, null, false);
		final var staff = player.addItem(ItemProcessType.QUEST, 8, 1, null, false);
		final var duals = player.addItem(ItemProcessType.QUEST, 52, 1, null, false);
		final Skill mastery = SkillData.getInstance().getSkill(210, 101);
		PhantomAssertions.assertEquals(101, mastery.getLevel(), "Fist mastery enchant fixture");
		inventory.equipItem(stockFists);
		final double stock = addAndMeasureMastery(player, mastery, Stat.POWER_ATTACK, 100);
		PhantomAssertions.assertTrue(stock > 0, "Enchant must add its own P.Atk bonus with fist weapons");

		int conditions = 0;
		for (Item weapon : new Item[] { null, sword, bow, stockFists, staff, duals })
		{
			if (weapon == null)
			{
				inventory.unEquipItemInBodySlotAndRecord(stockFists.getTemplate().getBodyPart());
			}
			else
			{
				inventory.equipItem(weapon);
			}
			PhantomAssertions.assertEquals(weapon == null ? null : weapon.getTemplate(), player.getActiveWeaponItem(), "Actual weapon is required");
			near(enabled || (weapon == stockFists) ? stock : 0, addAndMeasureMastery(player, mastery, Stat.POWER_ATTACK, 100));
			for (int id : DANCES)
			{
				for (int level : new int[] { 1, 101, 201 })
				{
					if ((level > 1) && !SkillData.getInstance().isEnchantable(id))
					{
						continue;
					}
					final Skill skill = SkillData.getInstance().getSkill(id, level);
					if ((skill == null) || (skill.getLevel() != level) || !skill.hasCastPreconditions())
					{
						continue;
					}
					player.addSkill(skill, false);
					PhantomAssertions.assertEquals(enabled || (weapon == duals), skill.checkCondition(player, player, false), "Dance " + id + "/" + level);
					conditions++;
				}
			}
		}
		System.out.printf(Locale.ROOT, "GEAR003 dance rank/gear conditions=%d enchanted fist P.Atk delta=%.4f%n", conditions, stock);
		final int[] masteries = { 205, 208, 209, 216, 217 };
		final Item[] prescribed = { sword, bow, player.addItem(ItemProcessType.QUEST, 10, 1, null, false), player.addItem(ItemProcessType.QUEST, 15, 1, null, false), sword };
		for (int i = 0; i < masteries.length; i++)
		{
			final Skill weaponMastery = SkillData.getInstance().getSkill(masteries[i], 1);
			inventory.equipItem(prescribed[i]);
			final double expected = addAndMeasureMastery(player, weaponMastery, Stat.POWER_ATTACK, 100);
			PhantomAssertions.assertTrue(expected > 0, "Stock P.Atk mastery contribution " + weaponMastery.getId());
			inventory.equipItem(duals);
			near(enabled ? expected : 0, addAndMeasureMastery(player, weaponMastery, Stat.POWER_ATTACK, 100));
			System.out.printf(Locale.ROOT, "GEAR003 mastery %d/1 native P.Atk delta=%.4f%n", weaponMastery.getId(), expected);
		}
	}

	private static double addAndMeasureMastery(Player player, Skill mastery, Stat stat, double initial)
	{
		final double before = player.calcStat(stat, initial);
		player.addSkill(mastery, false);
		final double added = player.calcStat(stat, initial) - before;
		player.removeSkill(mastery, false, true);
		near(before, player.calcStat(stat, initial));
		return added;
	}

	private static void checkArmorMasteries(Player player, boolean enabled)
	{
		final int[][] armorIds = { { 22, 28 }, { 25, 32 }, { 1100, 1103 } };
		final Item[][] armor = new Item[armorIds.length][2];
		for (int gear = 0; gear < armorIds.length; gear++)
		{
			for (int piece = 0; piece < armorIds[gear].length; piece++)
			{
				armor[gear][piece] = player.addItem(ItemProcessType.QUEST, armorIds[gear][piece], 1, null, false);
			}
		}
		final int[] masteryIds = { 227, 231, 234 };
		final Stat[] stats = { Stat.POWER_DEFENCE, Stat.EVASION_RATE, Stat.REGENERATE_MP_RATE };
		for (int required = 0; required < masteryIds.length; required++)
		{
			final Skill mastery = SkillData.getInstance().getSkill(masteryIds[required], 101);
			PhantomAssertions.assertEquals(101, mastery.getLevel(), "Armor mastery enchant fixture");
			for (Stat stat : stats)
			{
				equipArmor(player, armor[required]);
				final double expected = addAndMeasureMastery(player, mastery, stat, 100);
				if (stat == Stat.POWER_DEFENCE)
				{
					PhantomAssertions.assertTrue(expected > 0, "Stock P.Def armor mastery contribution " + mastery.getId());
				}
				for (int gear = 0; gear < armor.length; gear++)
				{
					equipArmor(player, armor[gear]);
					near(enabled || (gear == required) ? expected : 0, addAndMeasureMastery(player, mastery, stat, 100));
				}
			}
		}
		System.out.println("GEAR003 +1 armor masteries across light, heavy and robe verified");
	}

	private static void equipArmor(Player player, Item[] armor)
	{
		for (Item item : armor)
		{
			player.getInventory().equipItem(item);
		}
	}

	private static void checkDanceCast(Player player, boolean enabled) throws Exception
	{
		final Skill dance = SkillData.getInstance().getSkill(307, 1);
		player.addSkill(dance, false);
		player.getStat().setLevel((byte) 80);
		player.getStatus().setCurrentHp(player.getMaxHp());
		player.spawnMe(-71350, 258050, -3104);
		final String name = "Mate" + Long.toUnsignedString(System.nanoTime(), 36);
		final Player member = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "gear003native", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		PhantomAssertions.assertTrue(member != null && !member.isGM(), "Native party member creation");
		member.setOnlineStatus(true, false);
		member.spawnMe(-71360, 258050, -3104);
		final Party party = new Party(player, PartyDistributionType.FINDERS_KEEPERS);
		player.setParty(party);
		member.setParty(party);
		party.addPartyMember(member);
		try
		{
			final Item duals = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_RHAND);
			player.getInventory().unEquipItemInBodySlotAndRecord(duals.getTemplate().getBodyPart());
			player.setTarget(player);
			player.setCurrentMp(1);
			PhantomAssertions.assertTrue(!player.checkDoCastConditions(dance), "Insufficient MP must reject dance at the native cast gate");
			player.setCurrentMp(player.getMaxMp());
			final double waterBefore = member.calcStat(Stat.WATER_RES, 0);
			PhantomAssertions.assertEquals(enabled, player.useMagic(dance, false, false), "Actual Dance 307 cast without dual swords");
			if (!enabled)
			{
				player.getInventory().equipItem(duals);
				PhantomAssertions.assertTrue(player.useMagic(dance, false, false), "Stock dance cast with dual swords");
			}
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
			while (!member.getEffectList().isAffectedBySkill(dance.getId()) && (System.nanoTime() < deadline))
			{
				Thread.sleep(50);
			}
			final var effect = member.getEffectList().getBuffInfoBySkillId(dance.getId());
			PhantomAssertions.assertTrue(effect != null, "Dance 307 native party effect did not start");
			near(30, member.calcStat(Stat.WATER_RES, 0) - waterBefore);
			PhantomAssertions.assertEquals(120, effect.getAbnormalTime(), "Dance 307 stock duration");
			PhantomAssertions.assertTrue(player.getCurrentMp() < player.getMaxMp(), "Dance must consume MP");
			member.stopSkillEffects(SkillFinishType.NORMAL, dance.getId());
			near(waterBefore, member.calcStat(Stat.WATER_RES, 0));
			System.out.println("GEAR003 native dance effect=+30 Water Resistance, duration=120, MP and removal verified");
			checkDaggerProc(player, member, enabled);
		}
		finally
		{
			member.stopAllTasks();
			member.deleteMe();
		}
	}

	private static void checkDaggerProc(Player player, Player target, boolean enabled)
	{
		final Skill mastery = SkillData.getInstance().getSkill(209, 45);
		final Skill proc = SkillData.getInstance().getSkill(5603, 1);
		player.addSkill(mastery, false);
		final Item sword = player.getInventory().getItemByItemId(1);
		final Item dagger = player.getInventory().getItemByItemId(10);
		final OnCreatureDamageDealt event = new OnCreatureDamageDealt();
		event.setAttacker(player);
		event.setTarget(target);
		event.setDamage(100);
		player.getInventory().equipItem(sword);
		PhantomAssertions.assertEquals(0, countDaggerProcs(player, event, proc, false), "Non-critical hits must not trigger mastery");
		final int wrongType = countDaggerProcs(player, event, proc, true);
		player.getInventory().equipItem(dagger);
		final int prescribedType = countDaggerProcs(player, event, proc, true);
		if (enabled)
		{
			checkProcCount(wrongType);
		}
		else
		{
			PhantomAssertions.assertEquals(0, wrongType, "Stock mastery on a sword must not trigger");
		}
		checkProcCount(prescribedType);
		player.removeSkill(mastery, false, true);
		System.out.printf(Locale.ROOT, "GEAR003 209 native event 5603 procs: sword=%d/256 dagger=%d/256%n", wrongType, prescribedType);
	}

	private static int countDaggerProcs(Player player, OnCreatureDamageDealt event, Skill proc, boolean critical)
	{
		event.setCritical(critical);
		int count = 0;
		for (int i = 0; i < 256; i++)
		{
			EventDispatcher.getInstance().notifyEvent(event, player);
			if (player.getEffectList().isAffectedBySkill(proc.getId()))
			{
				count++;
				player.stopSkillEffects(SkillFinishType.NORMAL, proc.getId());
				player.enableSkill(proc);
			}
		}
		return count;
	}

	private static void checkProcCount(int count)
	{
		PhantomAssertions.assertTrue((count >= 48) && (count <= 128), "Native dagger proc rate outside expected range: " + count + "/256");
	}

	private static void checkManagedPhantom(boolean enabled)
	{
		final String name = "Head" + Long.toUnsignedString(System.nanoTime(), 36);
		final Player fixture = createBladedancer("gear003native", name);
		final int objectId = fixture.getObjectId();
		fixture.deleteMe();

		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final long profileId = repository.create(objectId).profileId();
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService service = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		PhantomAssertions.assertTrue(service.start(), "Managed Phantom service start");
		try
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, service.materialize(profileId).status(), "Real Phantom materialization");
			final Player phantom = World.getInstance().getPlayer(objectId);
			PhantomAssertions.assertTrue((phantom != null) && phantom.hasHeadlessOutboundSession() && !phantom.isGM(), "Managed Phantom must be a real headless Player");
			final Skill dance = phantom.getKnownSkill(307);
			PhantomAssertions.assertTrue(dance != null, "Managed Phantom must retain its learned dance across reload");
			PhantomAssertions.assertTrue(phantom.getActiveWeaponInstance() == null, "Managed Phantom must have an empty weapon slot");
			PhantomAssertions.assertEquals(enabled, dance.checkCondition(phantom, phantom, false), "Managed Phantom unarmed dance");
			System.out.printf(Locale.ROOT, "GEAR003 managed Phantom profile=%d objectId=%d ON=%s%n", profileId, objectId, enabled);
		}
		finally
		{
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, service.shutdown().state(), "Native Phantom shutdown");
		}
	}

	private static Player createBladedancer(String account, String name)
	{
		final Player fixture = Player.create(PlayerTemplateData.getInstance().getTemplate(PlayerClass.BLADEDANCER.getId()), account, name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		PhantomAssertions.assertTrue((fixture != null) && !fixture.isGM(), "Ordinary Bladedancer fixture creation");
		fixture.setOnlineStatus(true, false);
		fixture.getStat().setLevel((byte) 75);
		fixture.getStatus().setCurrentHp(fixture.getMaxHp());
		fixture.setCurrentMp(fixture.getMaxMp());
		fixture.addSkill(SkillData.getInstance().getSkill(307, 1), true);
		fixture.addSkill(SkillData.getInstance().getSkill(223, 49), true);
		fixture.setXYZ(-71350, 258050, -3104);
		fixture.stopAllTasks();
		fixture.storeMe();
		return fixture;
	}

	private static void createOwnerFixture()
	{
		final Player fixture = createBladedancer("gearowner003", "GearOwner003");
		final Item bow = fixture.addItem(ItemProcessType.QUEST, 13, 1, null, false);
		fixture.getInventory().equipItem(bow);
		fixture.deleteMe();
		System.out.println("GEAR003 client fixture GearOwner003 created in the owned TEST");
	}
}

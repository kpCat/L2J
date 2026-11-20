package org.l2jmobius.gameserver.model.script;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.NpcConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.config.custom.ServitorShareConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Chest;
import org.l2jmobius.gameserver.model.actor.status.CreatureStatus;
import org.l2jmobius.gameserver.model.actor.templates.CreatureTemplate;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.model.stats.functions.FuncMul;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** DB-free checks of the data and entry points used by PHANTOM-QOL-QUICK-001. */
public final class PhantomQolQuick001Suite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "qol-quick-001";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		final Path game = context.moduleRoot().resolve("dist/game").toRealPath();
		require(Path.of("").toRealPath().equals(game), "Run the suite from the candidate dist/game");
		ServerConfig.DATAPACK_ROOT = game.toFile();
		ServerConfig.SCRIPT_ROOT = game.resolve("data/scripts").toFile();
		GeneralConfig.load();
		NpcConfig.load();
		ServitorShareConfig.load();
		ScriptEngine.getInstance().executeScript(ScriptEngine.EFFECT_MASTER_HANDLER_FILE);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("mana-potion-keeps-item-reuse-restrictions-and-paid-offer", context ->
		{
			final Path data = context.moduleRoot().resolve("dist/game/data");
			final Path item = data.resolve("stats/items/00700-00799.xml");
			final Path skill = data.resolve("stats/skills/custom/10000-10099.xml");
			final Path offer = data.resolve("multisell/91003.xml");
			final String item728 = "/list/item[@id='728']";
			final String skill10001 = "/list/skill[@id='10001']";
			expect("10001", xml(item, item728 + "/skills/skill/@id"));
			expect("true", xml(item, item728 + "/set[@name='is_oly_restricted']/@val"));
			expect("2000", xml(item, item728 + "/set[@name='price']/@val"));
			expect("1", xml(skill, skill10001 + "/itemConsumeCount"));
			expect("728", xml(skill, skill10001 + "/itemConsumeId"));
			expect("500", xml(skill, skill10001 + "/reuseDelay"));
			expect("1000", xml(offer, "/list/item[production/@id='728' and production/@count='1']/ingredient[@id='57']/@count"));
			expect("300", xml(skill, skill10001 + "/effects/effect[@name='ManaHeal']/power"));
			final var loaded = SkillData.getInstance().getSkill(10001, 1);
			require((loaded != null) && loaded.isStatic() && (loaded.getItemConsumeCount() == 1) && (loaded.getItemConsumeId() == 728) && (loaded.getReuseDelay() == 500), "Native Mana Potion must resolve as a static, single-item skill");
			require(loaded.getEffects(EffectScope.GENERAL).stream().anyMatch(effect -> effect.getClass().getSimpleName().equals("ManaHeal")), "Native Mana Heal handler must be loaded");
			final Creature target = new DetachedCreature();
			final BuffInfo potion = new BuffInfo(target, target, loaded);
			target.setInvul(false);
			final int limit = target.getMaxRecoverableMp();
			require(limit > 600, "Mana fixture must allow both full and capped restoration");
			for (int deficit : new int[] { 500, 150, 0 })
			{
				target.setCurrentMp(limit - deficit);
				loaded.applyEffectScope(EffectScope.GENERAL, potion, true, false);
				near(limit - deficit + Math.min(300, deficit), target.getCurrentMp());
			}
			target.setCurrentMp(limit - 500);
			target.setInvul(true);
			loaded.applyEffectScope(EffectScope.GENERAL, potion, true, false);
			near(limit - 500, target.getCurrentMp());
		});
		registry.add("spirit-sharing-has-a-dedicated-handler", context ->
		{
			final Path skills = context.moduleRoot().resolve("dist/game/data/stats/skills/01500-01599.xml");
			expect("3", xml(skills, "/list/skill[@id='1547']/@levels"));
			expect("SpiritSharing", xml(skills, "/list/skill[@id='1547']/effects/effect/@name"));
			expect("Buff", xml(skills, "/list/skill[@id='1548']/effects/effect/@name"));
			assertSpiritFunctions(context);
		});
		registry.add("personal-dwarven-craft-through-trainer", context ->
		{
			final Path game = context.moduleRoot().resolve("dist/game");
			expect("true", xml(game.resolve("data/stats/players/skillTrees/StartingClass/DwarvenFighter.xml"), "/list/skillTree/skill[@skillId='1321']/@autoGet"));
			expect("OpenDwarfRecipeBook", xml(game.resolve("data/stats/skills/01300-01399.xml"), "/list/skill[@id='1321']/effects/effect/@name"));
			final Path trainer = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/model/actor/instance/Folk.java");
			require(Files.readString(trainer).contains("getAvailableClassSkills(player, npc, playerClass)"), "Trainer must use personal NPC skill eligibility");
		});
		registry.add("chest-switch-and-native-level-boundaries", context ->
		{
			final Map<Integer, Integer> openChestRanks = Map.of(27, 14, 2065, 8, 2229, 8, 2322, 1, 2400, 1, 3155, 10, 22271, 1);
			for (var entry : openChestRanks.entrySet())
			{
				for (int rank = 1; rank <= entry.getValue(); rank++)
				{
					final var skill = SkillData.getInstance().getSkill(entry.getKey(), rank);
					require(skill != null, "Missing opening skill " + entry.getKey() + " rank " + rank);
					require(skill.getEffects(EffectScope.GENERAL).stream().anyMatch(effect -> effect.getClass().getSimpleName().equals("OpenChest") && effect.isInstant() && effect.calcSuccess(null, null, skill)),
						"Opening skill must use the shared binary chest gate: " + entry.getKey() + " rank " + rank);
					if (entry.getKey() == 2065)
					{
						require((skill.getItemConsumeId() == 5196 + rank) && (skill.getItemConsumeCount() == 1),
							"Box Key must consume one item of the matching grade: rank " + rank);
					}
				}
			}
			final boolean enabled = NpcConfig.ALLOW_OPENING_LOWER_LEVEL_CHESTS;
			require(enabled, "Shipped NPC.ini must allow lower-level chests for every key family");
			try
			{
				for (boolean allowLower : new boolean[] { false, true })
				{
					NpcConfig.ALLOW_OPENING_LOWER_LEVEL_CHESTS = allowLower;
					for (int level : new int[] { 77, 78 })
					{
						final int bound = level == 77 ? 6 : 5;
						require(Chest.canOpenAtLevels(level, level - 1), "Lower by one remains legal");
						require(Chest.canOpenAtLevels(level, level - 20) == allowLower, "Lower by 20 follows the flag");
						require(Chest.canOpenAtLevels(level, level - 60) == allowLower, "Lower by 60 follows the flag");
						require(Chest.canOpenAtLevels(level, level), "Equal level remains legal");
						require(Chest.canOpenAtLevels(level, level + bound), "Higher boundary remains legal");
						require(!Chest.canOpenAtLevels(level, level + bound + 1), "Higher outside boundary remains denied");
					}
				}
			}
			finally
			{
				NpcConfig.ALLOW_OPENING_LOWER_LEVEL_CHESTS = enabled;
			}
		});
		registry.add("quest-298-uses-current-level-on-rated-turn-in", context ->
		{
			final Path game = context.moduleRoot().resolve("dist/game");
			final Path quest = game.resolve("data/scripts/quests/Q00298_LizardmensConspiracy/Q00298_LizardmensConspiracy.java");
			require(Files.readString(quest).contains("addExpAndSp(player, 0, 42000, player.getLevel())"), "Only Q298 turn-in must credit a percentage at current level");
			for (int ratedSp : new int[] { 42000, 1470000 })
			{
				require(Quest.applyQuestSpBonus(ratedSp, 25) == (ratedSp * 5 / 4), "Level 25 award must be 125% of existing rated SP");
				require(Quest.applyQuestSpBonus(ratedSp, 35) == (ratedSp * 27 / 20), "Level 35 award must be 135% of existing rated SP");
				require(Quest.applyQuestSpBonus(ratedSp, 0) == ratedSp, "Other quest awards must keep the same rated SP");
			}
			require(Quest.applyQuestSpBonus(3, 35) == 4, "The fractional bonus must truncate");
			require(Quest.applyQuestSpBonus(Integer.MAX_VALUE - 1, 35) == Integer.MAX_VALUE, "The SP award must saturate");
		});
	}

	private static void assertSpiritFunctions(PhantomTestContext context) throws Exception
	{
		final Creature target = new DetachedCreature();
		final Map<Stat, double[]> stock = Map.of(
			Stat.POWER_ATTACK, new double[] { 1.03, 1.04, 1.05 },
			Stat.MAGIC_ATTACK, new double[] { 1.03, 1.04, 1.05 },
			Stat.PHYSICAL_SKILL_POWER, new double[] { 1.05, 1.07, 1.10 },
			Stat.POWER_ATTACK_SPEED, new double[] { 1.02, 1.05, 1.07 },
			Stat.MAGIC_ATTACK_SPEED, new double[] { 1.01, 1.03, 1.05 },
			Stat.CRITICAL_RATE, new double[] { 1.05, 1.07, 1.10 });
		for (int rank = 1; rank <= 3; rank++)
		{
			final var skill = SkillData.getInstance().getSkill(1547, rank);
			final var effect = skill.getEffects(EffectScope.GENERAL).getFirst();
			final var functions = effect.getStatFuncs(null, null, skill);
			require(functions.size() == stock.size(), "Exactly six Spirit Sharing stat functions must attach");
			for (var function : functions)
			{
				final Stat stat = function.getStat();
				final double original = stock.get(stat)[rank - 1];
				require((function instanceof FuncMul) && (function.getFuncOwner() == effect), "Function and owner must support stock removal");
				near(100 * (1 + (original - 1) * 2), function.calc(null, null, skill, 100));
				for (int multiplier = 0; multiplier <= 2; multiplier++)
				{
					final var settings = new ServitorShareConfig.SpiritSettings(true, Map.of(stat, (double) multiplier));
					near(1 + (original - 1) * multiplier, settings.coefficient(1547, stat, original));
					near(original, settings.coefficient(1548, stat, original));
					near(original, settings.coefficient(1557, stat, original));
				}
				near(original, new ServitorShareConfig.SpiritSettings(false, Map.of(stat, 2.0)).coefficient(1547, stat, original));
			}
			final Stat stat = Stat.PHYSICAL_SKILL_POWER;
			final double base = target.calcStat(stat, 100);
			final BuffInfo buff = new BuffInfo(target, target, skill);
			buff.addEffect(effect);
			for (int apply = 0; apply < 2; apply++)
			{
				buff.addStats();
				near(base * (1 + (stock.get(stat)[rank - 1] - 1) * 2), target.calcStat(stat, 100));
				buff.removeStats();
				near(base, target.calcStat(stat, 100));
			}
		}
		final Path invalid = Files.createTempFile(context.reportsDirectory(), "invalid-spirit-sharing-", ".ini");
		try
		{
			Files.writeString(invalid, "EnableSpiritSharingBonusTuning = True\nSpiritSharingPAtkBonusMultiplier = NaN\n");
			require(!ServitorShareConfig.readSpiritSharing(invalid).enabled(), "Invalid Spirit Sharing must retain stock for all stats");
		}
		finally
		{
			Files.deleteIfExists(invalid);
		}
	}

	private static void near(double expected, double actual)
	{
		require(Math.abs(expected - actual) < 1e-9, "Expected " + expected + ", got " + actual);
	}

	/** Uses production stats and vitals without allocating a world ID or scheduling regeneration. */
	private static final class DetachedCreature extends Creature
	{
		DetachedCreature()
		{
			super(1, template());
		}

		private static CreatureTemplate template()
		{
			final StatSet stats = new StatSet();
			stats.set("baseHpMax", 1000);
			stats.set("baseMpMax", 1000);
			return new CreatureTemplate(stats);
		}

		@Override
		public void initCharStatus()
		{
			setStatus(new CreatureStatus(this)
			{
				@Override
				public synchronized void startHpMpRegeneration()
				{
					// The fixture has no game clock.
				}
			});
		}

		@Override public int getLevel() { return 1; }
		@Override public Item getActiveWeaponInstance() { return null; }
		@Override public Weapon getActiveWeaponItem() { return null; }
		@Override public Item getSecondaryWeaponInstance() { return null; }
		@Override public ItemTemplate getSecondaryWeaponItem() { return null; }
		@Override public boolean isAutoAttackable(Creature attacker) { return false; }
		@Override public void updateAbnormalEffect() { }
		@Override public void sendInfo(Player player) { }
	}

	private static String xml(Path file, String expression) throws Exception
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		final var document = factory.newDocumentBuilder().parse(file.toFile());
		final String schema = document.getDocumentElement().getAttribute("xsi:noNamespaceSchemaLocation");
		if (!schema.isEmpty())
		{
			final SchemaFactory schemas = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			schemas.newSchema(file.resolveSibling(schema).normalize().toFile()).newValidator().validate(new StreamSource(file.toFile()));
		}
		return XPathFactory.newInstance().newXPath().evaluate(expression, document);
	}

	private static void expect(String expected, String actual)
	{
		require(expected.equals(actual), "Expected '" + expected + "', got '" + actual + "'");
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}
}

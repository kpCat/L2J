/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig.Settings;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropGroupHolder;
import org.l2jmobius.gameserver.model.actor.holders.npc.DropHolder;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.actor.instance.Servitor;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.actor.enums.npc.DropType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.holders.ItemHolder;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** L2-QOL-001 policy, strict catalog, native loot and inventory lifecycle coverage. */
public final class QoLLevelGapSuite implements PhantomTestSuite
{
	private static final long SEED = 1001001L;
	private static final int TIER_5_ITEM = 22290;
	private static final int TIER_10_ITEM = 22296;
	private static final int TIER_20_ITEM = 22297;
	private static final int TIER_40_ITEM = 22298;
	private static final int GROUPED_ITEM = 1864;
	private static final int UNGROUPED_ITEM = 1865;
	private static final int SPOIL_ITEM = 1866;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PersonalPremiumQoLService.RuntimeState _activeState;
	private PersonalPremiumQoLService.RuntimeState _previousState;
	private RateSnapshot _rates;
	private Player _player;
	private Player _observer;
	private int _originalLevel;
	private Path _fixtures;

	@Override
	public String id()
	{
		return "qol-level-gap";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "L2-QOL-001 level-gap suite used the wrong deterministic seed.");
		_environment.initialize(context);
		_player = Player.load(_environment.primary().objectId());
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_player != null) && (_observer != null), "L2-QOL-001 fixture Players did not load.");
		_originalLevel = _player.getLevel();
		_rates = RateSnapshot.capture();
		_fixtures = context.reportsDirectory().resolve("qol-level-gap-fixtures");
		Files.createDirectories(_fixtures);
		final Settings settings = new Settings(true, false, PersonalPremiumQoLConfig.DEFAULT_LEVEL_GAP_ITEMS_FILE, true, "Test configuration.");
		_activeState = PersonalPremiumQoLService.build(settings, Path.of("."));
		_previousState = PersonalPremiumQoLService.installForTests(_activeState);
		configureDeterministicGapRates();
		context.record("qol001.database", "l2jmobiush5_phantom_test");
		context.record("qol001.itemIds", "22290/22296/22297/22298");
		context.record("qol001.originalItemNames", _activeState.catalog().tiers().stream().map(tier -> tier.itemId() + "=" + ItemData.getInstance().getTemplate(tier.itemId()).getName()).toList());
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-policy-boundaries-and-native-chance-preservation", this::policyBoundaries);
		registry.add("02-public-config-and-strict-catalog-negative-controls", this::strictLoaders);
		registry.add("03-max-tier-duplicates-owner-and-warehouse", this::inventoryAuthority);
		registry.add("04-native-grouped-drop-and-adena", this::nativeGroupedAndAdena);
		registry.add("05-native-ungrouped-drop-and-spoil", this::nativeUngroupedAndSpoil);
		registry.add("06-summon-owner-and-bot-exclusion", this::actorEligibility);
		registry.add("07-relog-reloads-native-inventory-entitlement", this::relogLifecycle);
	}

	private void policyBoundaries(PhantomTestContext context)
	{
		for (int tier : List.of(5, 10, 20, 40))
		{
			for (int gap : List.of(0, tier, -tier))
			{
				final var decision = LevelGapProtectionPolicy.decide(true, true, tier, 60, 60 - gap);
				PhantomAssertions.assertTrue(decision.protectedGap(), "Boundary gap was not protected for tier " + tier + ".");
				PhantomAssertions.assertEquals(100d, LevelGapProtectionPolicy.apply(37.25d, decision), "Protected level-gap gate was not exactly 100%.");
			}
			for (int gap : List.of(tier + 1, -(tier + 1)))
			{
				final var decision = LevelGapProtectionPolicy.decide(true, true, tier, 60, 60 - gap);
				PhantomAssertions.assertFalse(decision.protectedGap(), "Outside gap was protected for tier " + tier + ".");
				PhantomAssertions.assertEquals(37.25d, LevelGapProtectionPolicy.apply(37.25d, decision), "Outside gap changed the native chance.");
			}
		}
		PhantomAssertions.assertEquals(37.25d, LevelGapProtectionPolicy.apply(37.25d, LevelGapProtectionPolicy.decide(false, true, 40, Integer.MIN_VALUE, Integer.MAX_VALUE)), "Disabled extreme signed gap changed native chance.");
		PhantomAssertions.assertEquals(37.25d, LevelGapProtectionPolicy.apply(37.25d, LevelGapProtectionPolicy.decide(true, false, 40, 1, 1)), "Excluded actor changed native chance.");
		PhantomAssertions.assertEquals(37.25d, LevelGapProtectionPolicy.apply(37.25d, LevelGapProtectionPolicy.decide(true, true, 0, 1, 1)), "Zero tier changed native chance.");
		PhantomAssertions.assertEquals(125d, LevelGapProtectionPolicy.apply(125d, LevelGapProtectionPolicy.decide(true, true, 40, 1, 1)), "Protection reduced an already higher native value.");
		context.record("qol001.policyCases", 31);
		context.record("qol001.boundarySample", "native=37.25,inside=100.0,outside=37.25");
	}

	private void strictLoaders(PhantomTestContext context) throws Exception
	{
		final Path missing = _fixtures.resolve("missing.ini");
		PhantomAssertions.assertFalse(PersonalPremiumQoLConfig.read(missing).enabled(), "Missing INI did not fail closed.");
		final Path invalidIni = write("invalid.ini", "EnablePersonalPremiumQoL=maybe\nEnablePersonalPremiumShop=True\n");
		PhantomAssertions.assertFalse(PersonalPremiumQoLConfig.read(invalidIni).valid(), "Invalid master switch was accepted.");
		final Path traversalIni = write("traversal.ini", "EnablePersonalPremiumQoL=True\nEnablePersonalPremiumShop=False\nLevelGapItemsFile=../outside.xml\n");
		PhantomAssertions.assertFalse(PersonalPremiumQoLConfig.read(traversalIni).valid(), "Catalog traversal path was accepted.");

		final Path valid = Path.of("data/custom/personal-qol/level-gap-items.xml");
		final LevelGapItemCatalog shippedCatalog = LevelGapItemCatalog.load(valid);
		PhantomAssertions.assertEquals(List.of(5, 10, 20, 40), shippedCatalog.tiers().stream().map(LevelGapItemCatalog.Tier::maximumGap).toList(), "Public catalog loader changed shipped tiers.");
		assertCatalogRejected("duplicate.xml", catalogRows(22290, 22290, 22297, 22298));
		assertCatalogRejected("wrong-tier.xml", catalogRowsWithTiers(22290, 5, 22296, 10, 22297, 21, 22298, 40));
		assertCatalogRejected("unknown-attribute.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><levelGapItems version=\"1\" extra=\"x\"></levelGapItems>");
		assertCatalogRejected("doctype.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><levelGapItems version=\"1\">&e;</levelGapItems>");
		Files.write(_fixtures.resolve("malformed-utf8.xml"), new byte[] {(byte) 0xC3, (byte) 0x28});
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> LevelGapItemCatalog.load(_fixtures.resolve("malformed-utf8.xml")), "Malformed UTF-8 catalog was accepted.");
		final Path oversized = write("oversized.xml", " ".repeat(65 * 1024));
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> LevelGapItemCatalog.load(oversized), "Oversized catalog was accepted.");
		final Path unknownTemplate = write("unknown-template.xml", catalogRows(22290, 22296, 22297, Integer.MAX_VALUE));
		final LevelGapItemCatalog unknownCatalog = LevelGapItemCatalog.load(unknownTemplate);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PersonalPremiumQoLService.validateTemplates(unknownCatalog), "Unknown item template was accepted by production composition.");
		final LevelGapItemCatalog currencyCatalog = LevelGapItemCatalog.load(write("currency-collision.xml", catalogRows(57, 22296, 22297, 22298)));
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PersonalPremiumQoLService.validateShop(currencyCatalog, Path.of("data/multisell/91001.xml")), "Adena was accepted as a configured carrier item.");
		final String invalidShopXml = Files.readString(Path.of("data/multisell/91001.xml"), StandardCharsets.UTF_8).replaceFirst("<item>", "<item extra=\"x\">");
		final Path invalidShop = write("invalid-shop.xml", invalidShopXml);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PersonalPremiumQoLService.validateShop(shippedCatalog, invalidShop), "Multisell with an unknown entry attribute was accepted.");
		PhantomAssertions.assertFalse(PersonalPremiumQoLService.build(Settings.disabled("Legacy installation."), _fixtures.resolve("missing-runtime-root")).enabled(), "Disabled legacy installation tried to load absent QoL data.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PersonalPremiumQoLService.build(new Settings(true, true, "missing.xml", true, "Invalid active test."), _fixtures), "Active configuration with absent data did not fail closed.");
		PhantomAssertions.assertTrue(PersonalPremiumQoLService.getInstance().isEnabled() && !PersonalPremiumQoLService.getInstance().isShopEnabled(), "Rejected composition changed the installed runtime state.");
		context.record("qol001.loaderNegativeControls", 13);
	}

	private void inventoryAuthority(PhantomTestContext context)
	{
		removeQoLItems(_player);
		removeQoLItems(_observer);
		PhantomAssertions.assertEquals(0, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Empty inventory retained a tier.");
		add(_player, TIER_5_ITEM, 2);
		add(_player, TIER_20_ITEM, 1);
		PhantomAssertions.assertEquals(20, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Tiers or duplicate copies were summed instead of taking maximum.");
		_player.destroyItemByItemId(ItemProcessType.DESTROY, TIER_20_ITEM, 1, _player, false);
		PhantomAssertions.assertEquals(5, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Removing tier 20 did not fall back to tier 5.");

		add(_observer, TIER_40_ITEM, 1);
		PhantomAssertions.assertEquals(5, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Another Player inventory granted an aura.");
		final Item tier = _player.getInventory().getItemByItemId(TIER_5_ITEM);
		PhantomAssertions.assertTrue(tier != null, "Tier 5 fixture item is absent before warehouse transfer.");
		PhantomAssertions.assertTrue(_player.getInventory().transferItem(ItemProcessType.TRANSFER, tier.getObjectId(), tier.getCount(), _player.getWarehouse(), _player, this) != null, "Tier item could not be transferred to native warehouse.");
		PhantomAssertions.assertEquals(0, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Warehouse item granted inventory protection.");
		PhantomAssertions.assertTrue(_player.getWarehouse().transferItem(ItemProcessType.TRANSFER, _player.getWarehouse().getItemByItemId(TIER_5_ITEM).getObjectId(), 1, _player.getInventory(), _player, this) != null, "Tier item could not be returned from native warehouse.");
		PhantomAssertions.assertEquals(5, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Returned inventory item did not restore protection.");
		context.record("qol001.inventoryAuthority", "duplicates=max;otherOwner=none;warehouse=none;returned=5");
	}

	private void nativeGroupedAndAdena(PhantomTestContext context)
	{
		removeQoLItems(_player);
		_player.getStat().setLevel((byte) 80);
		final Monster groupedVictim = monster(910101, 60, false);
		final DropGroupHolder group = new DropGroupHolder(100);
		group.addDrop(new DropHolder(DropType.DROP, GROUPED_ITEM, 3, 3, 100));
		groupedVictim.getTemplate().setDropGroups(List.of(group));
		assertAbsent(groupedVictim.getTemplate().calculateDrops(DropType.DROP, groupedVictim, _player), GROUPED_ITEM, "Stock grouped gate did not suppress a -20 gap at native 0%.");
		add(_player, TIER_20_ITEM, 1);
		assertCount(groupedVictim.getTemplate().calculateDrops(DropType.DROP, groupedVictim, _player), GROUPED_ITEM, 3, "Protected grouped DROP did not retain native amount.");

		final Monster adenaVictim = monster(910102, 60, true);
		adenaVictim.getTemplate().addDrop(new DropHolder(DropType.DROP, 57, 17, 17, 100));
		assertCount(adenaVictim.getTemplate().calculateDrops(DropType.DROP, adenaVictim, _player), 57, 17, "Protected Adena gate did not retain native amount on raid/champion actor.");
		groupedVictim.deleteMe();
		adenaVictim.deleteMe();
		context.record("qol001.nativeGrouped", "gap=-20,nativeGapChance=0.0,protected=100.0,itemAmount=3,adenaAmount=17");
	}

	private void nativeUngroupedAndSpoil(PhantomTestContext context)
	{
		removeQoLItems(_player);
		_player.getStat().setLevel((byte) 80);
		final Monster dropVictim = monster(910103, 60, false);
		dropVictim.getTemplate().addDrop(new DropHolder(DropType.DROP, UNGROUPED_ITEM, 4, 4, 100));
		assertAbsent(dropVictim.getTemplate().calculateDrops(DropType.DROP, dropVictim, _player), UNGROUPED_ITEM, "Stock ungrouped gate did not suppress a -20 gap at native 0%.");
		add(_player, TIER_20_ITEM, 1);
		assertCount(dropVictim.getTemplate().calculateDrops(DropType.DROP, dropVictim, _player), UNGROUPED_ITEM, 4, "Protected ungrouped DROP did not retain native amount.");

		final Monster spoilVictim = monster(910104, 60, false);
		spoilVictim.getTemplate().addSpoil(new DropHolder(DropType.SPOIL, SPOIL_ITEM, 7, 7, 100));
		assertCount(spoilVictim.getTemplate().calculateDrops(DropType.SPOIL, spoilVictim, _player), SPOIL_ITEM, 7, "Protected SPOIL did not retain native chance/amount path.");
		_player.getStat().setLevel((byte) 81);
		assertAbsent(spoilVictim.getTemplate().calculateDrops(DropType.SPOIL, spoilVictim, _player), SPOIL_ITEM, "Tier 20 changed the stock formula outside its absolute boundary.");
		dropVictim.deleteMe();
		spoilVictim.deleteMe();
		context.record("qol001.nativeUngroupedSpoil", "inside=-20,outside=-21,dropAmount=4,spoilAmount=7");
	}

	private void actorEligibility(PhantomTestContext context) throws Exception
	{
		removeQoLItems(_player);
		add(_player, TIER_40_ITEM, 1);
		_player.getStat().setLevel((byte) 80);
		final Monster victim = monster(910105, 60, false);
		PhantomAssertions.assertTrue(PersonalPremiumQoLService.getInstance().snapshot(victim, _player).protectedGap(), "Ordinary transportless Player was incorrectly excluded.");
		try (Player.OutboundSessionAttachment ignored = _player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32)))
		{
			PhantomAssertions.assertFalse(PersonalPremiumQoLService.getInstance().snapshot(victim, _player).protectedGap(), "ACTIVE/WARM headless Phantom received level-gap protection.");
			PhantomAssertions.assertEquals(0, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Headless Phantom exposed an inventory tier.");
		}

		final Servitor summon = new Servitor(monsterTemplate(910106, 60, false), _player);
		PhantomAssertions.assertTrue(PersonalPremiumQoLService.getInstance().snapshot(victim, summon).protectedGap(), "Native Summon.asPlayer owner resolution lost the owner's item.");
		final Monster fakePlayer = monster(910107, 80, false, true);
		PhantomAssertions.assertFalse(PersonalPremiumQoLService.getInstance().snapshot(victim, fakePlayer).protectedGap(), "Legacy NPC Fake Player received level-gap protection.");
		PhantomAssertions.assertFalse(PersonalPremiumQoLService.getInstance().snapshot(victim, victim).protectedGap(), "Non-Player killer received level-gap protection.");
		summon.deleteMe();
		fakePlayer.deleteMe();
		victim.deleteMe();
		context.record("qol001.actorEligibility", "ordinary=true;headlessPhantom=false;summonOwner=true;fakeNpc=false;nonPlayer=false;background=no-native-player-path");
	}

	private void relogLifecycle(PhantomTestContext context)
	{
		removeQoLItems(_player);
		add(_player, TIER_10_ITEM, 1);
		PhantomAssertions.assertEquals(10, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Pre-relog tier was not visible.");
		_player.storeMe();
		_player.deleteMe();
		_player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_player != null, "Player did not reload after persisted inventory store.");
		PhantomAssertions.assertEquals(10, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Relog did not derive protection from restored native inventory.");
		context.record("qol001.relogTier", 10);
	}

	private Path write(String name, String content) throws Exception
	{
		final Path path = _fixtures.resolve(name);
		Files.writeString(path, content, StandardCharsets.UTF_8);
		return path;
	}

	private void assertCatalogRejected(String name, String content) throws Exception
	{
		final Path path = write(name, content);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> LevelGapItemCatalog.load(path), "Invalid catalog " + name + " was accepted.");
	}

	private static String catalogRows(int first, int second, int third, int fourth)
	{
		return catalogRowsWithTiers(first, 5, second, 10, third, 20, fourth, 40);
	}

	private static String catalogRowsWithTiers(int first, int firstGap, int second, int secondGap, int third, int thirdGap, int fourth, int fourthGap)
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<levelGapItems version=\"1\">\n" +
			"<item itemId=\"" + first + "\" maximumGap=\"" + firstGap + "\" label=\"A\" />\n" +
			"<item itemId=\"" + second + "\" maximumGap=\"" + secondGap + "\" label=\"B\" />\n" +
			"<item itemId=\"" + third + "\" maximumGap=\"" + thirdGap + "\" label=\"C\" />\n" +
			"<item itemId=\"" + fourth + "\" maximumGap=\"" + fourthGap + "\" label=\"D\" />\n</levelGapItems>\n";
	}

	private static Monster monster(int id, int level, boolean raidChampion)
	{
		return monster(id, level, raidChampion, false);
	}

	private static Monster monster(int id, int level, boolean raidChampion, boolean fakePlayer)
	{
		final Monster monster = new Monster(monsterTemplate(id, level, fakePlayer));
		if (raidChampion)
		{
			monster.setIsRaid(true);
			monster.setChampion(true);
		}
		return monster;
	}

	private static NpcTemplate monsterTemplate(int id, int level, boolean fakePlayer)
	{
		final StatSet set = new StatSet();
		set.set("id", id);
		set.set("displayId", 18342);
		set.set("level", level);
		set.set("type", "Monster");
		set.set("name", "QoL001Fixture" + id);
		set.set("fakePlayer", fakePlayer);
		set.set("baseHpMax", 1000);
		set.set("baseMpMax", 100);
		set.set("collisionRadius", 8);
		set.set("collisionHeight", 20);
		final NpcTemplate template = new NpcTemplate(set);
		template.setSkills(Map.of());
		return template;
	}

	private static void assertAbsent(List<ItemHolder> drops, int itemId, String message)
	{
		PhantomAssertions.assertTrue((drops == null) || drops.stream().noneMatch(item -> item.getId() == itemId), message);
	}

	private static void assertCount(List<ItemHolder> drops, int itemId, long count, String message)
	{
		final long actual = drops == null ? 0 : drops.stream().filter(item -> item.getId() == itemId).mapToLong(ItemHolder::getCount).sum();
		PhantomAssertions.assertEquals(count, actual, message);
	}

	private static void add(Player player, int itemId, long count)
	{
		PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, itemId, count, player, QoLLevelGapSuite.class) != null, "Could not add QoL fixture item " + itemId + ".");
	}

	private static void removeQoLItems(Player player)
	{
		if (player == null)
		{
			return;
		}
		for (int itemId : List.of(TIER_5_ITEM, TIER_10_ITEM, TIER_20_ITEM, TIER_40_ITEM))
		{
			final long count = player.getInventory().getInventoryItemCount(itemId, -1);
			if (count > 0)
			{
				player.destroyItemByItemId(ItemProcessType.DESTROY, itemId, count, player, false);
			}
			final Item stored = player.getWarehouse().getItemByItemId(itemId);
			if (stored != null)
			{
				player.getWarehouse().destroyItem(ItemProcessType.DESTROY, stored, player, QoLLevelGapSuite.class);
			}
		}
	}

	private static void configureDeterministicGapRates()
	{
		RatesConfig.DROP_MAX_OCCURRENCES_NORMAL = 8;
		RatesConfig.DROP_MAX_OCCURRENCES_RAIDBOSS = 8;
		RatesConfig.DROP_ADENA_MIN_LEVEL_DIFFERENCE = 1;
		RatesConfig.DROP_ADENA_MAX_LEVEL_DIFFERENCE = 2;
		RatesConfig.DROP_ADENA_MIN_LEVEL_GAP_CHANCE = 0;
		RatesConfig.DROP_ITEM_MIN_LEVEL_DIFFERENCE = 1;
		RatesConfig.DROP_ITEM_MAX_LEVEL_DIFFERENCE = 2;
		RatesConfig.DROP_ITEM_MIN_LEVEL_GAP_CHANCE = 0;
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			removeQoLItems(_player);
			removeQoLItems(_observer);
			if (_player != null)
			{
				_player.getStat().setLevel((byte) _originalLevel);
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
			if (_previousState != null)
			{
				PersonalPremiumQoLService.installForTests(_previousState);
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

	private record RateSnapshot(int normalOccurrences, int raidOccurrences, int adenaMinDifference, int adenaMaxDifference, double adenaMinimumChance, int itemMinDifference, int itemMaxDifference, double itemMinimumChance)
	{
		static RateSnapshot capture()
		{
			return new RateSnapshot(RatesConfig.DROP_MAX_OCCURRENCES_NORMAL, RatesConfig.DROP_MAX_OCCURRENCES_RAIDBOSS, RatesConfig.DROP_ADENA_MIN_LEVEL_DIFFERENCE, RatesConfig.DROP_ADENA_MAX_LEVEL_DIFFERENCE, RatesConfig.DROP_ADENA_MIN_LEVEL_GAP_CHANCE, RatesConfig.DROP_ITEM_MIN_LEVEL_DIFFERENCE, RatesConfig.DROP_ITEM_MAX_LEVEL_DIFFERENCE, RatesConfig.DROP_ITEM_MIN_LEVEL_GAP_CHANCE);
		}

		void restore()
		{
			RatesConfig.DROP_MAX_OCCURRENCES_NORMAL = normalOccurrences;
			RatesConfig.DROP_MAX_OCCURRENCES_RAIDBOSS = raidOccurrences;
			RatesConfig.DROP_ADENA_MIN_LEVEL_DIFFERENCE = adenaMinDifference;
			RatesConfig.DROP_ADENA_MAX_LEVEL_DIFFERENCE = adenaMaxDifference;
			RatesConfig.DROP_ADENA_MIN_LEVEL_GAP_CHANCE = adenaMinimumChance;
			RatesConfig.DROP_ITEM_MIN_LEVEL_DIFFERENCE = itemMinDifference;
			RatesConfig.DROP_ITEM_MAX_LEVEL_DIFFERENCE = itemMaxDifference;
			RatesConfig.DROP_ITEM_MIN_LEVEL_GAP_CHANCE = itemMinimumChance;
		}
	}
}

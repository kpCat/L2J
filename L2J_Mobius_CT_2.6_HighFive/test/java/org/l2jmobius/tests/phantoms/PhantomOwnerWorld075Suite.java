package org.l2jmobius.tests.phantoms;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.ServitorShareConfig;
import org.l2jmobius.gameserver.model.stats.Stat;
import java.util.Map;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.EnchantItemGroupsData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.item.enchant.EnchantResultType;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer;

/** DB-free checks of the real owner configuration and canonical enchant route. */
public final class PhantomOwnerWorld075Suite implements PhantomTestSuite
{
	@Override public String id() { return "owner-world075"; }

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PlayerConfig.load();
		ItemData.getInstance();
		EnchantItemGroupsData.getInstance();
		EnchantItemData.getInstance();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registerRecovery(registry);
		if (System.getProperty("phantom.m1.native.focus", "").equals("recovery075")) { return; }
		registry.add("native-store-close-keeps-craft-admission-monitor", context ->
		{
			final String source = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.java"));
			final String close = source.substring(source.indexOf("public Result close("), source.indexOf("public ShutdownResult shutdown("));
			final int sell = close.indexOf("synchronized (player.getSellList())");
			final int buy = close.indexOf("synchronized (player.getBuyList())", Math.max(0, sell));
			final int manufacture = close.indexOf("synchronized (player.getManufactureItems())", Math.max(0, buy));
			final int busy = close.indexOf("RecipeManager.getInstance().isMakingItem(player)", Math.max(0, manufacture));
			final int clear = close.indexOf("player.getManufactureItems().clear()", Math.max(0, busy));
			final int reset = close.indexOf("player.setPrivateStoreType(PrivateStoreType.NONE)", Math.max(0, clear));
			if (sell < 0 || buy <= sell || manufacture <= buy || busy <= manufacture || clear <= busy || reset <= clear) { throw new AssertionError("Native close must recheck queued craft under SELL/BUY/manufacture admission monitors before clearing and releasing its owner."); }
		});
		registry.add("ordinary-retail-reuses-canonical-recipe-reserve", context ->
		{
			final String source = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/economy/PhantomAutonomousMarketProducer.java"));
			if (!source.contains("private static long surplusCount(") || !source.contains("policy.evaluate(owner, item, java.util.Set.of(), false, false)") || !source.contains("role.keyPart()") || !source.contains("decision.count()")) { throw new AssertionError("Ordinary retail must reuse native known-recipe protection and bound every line by canonical surplus above reserve."); }
		});
		registry.add("servitor-eleven-independent-coefficients-and-parity", context ->
		{
			final var doubled = new ServitorShareConfig.Settings(true, Map.of());
			final var off = new ServitorShareConfig.Settings(false, Map.of());
			for (Stat stat : new Stat[] { Stat.POWER_ATTACK, Stat.POWER_DEFENCE, Stat.MAGIC_ATTACK, Stat.MAGIC_DEFENCE, Stat.MAX_HP, Stat.MAX_MP, Stat.MAX_RECOVERABLE_HP, Stat.MAX_RECOVERABLE_MP, Stat.CRITICAL_RATE, Stat.POWER_ATTACK_SPEED, Stat.MAGIC_ATTACK_SPEED })
			{
				near(1.5, doubled.coefficient(1557, stat, 1.25));
				near(1.25, off.coefficient(1557, stat, 1.25));
				near(1.25, doubled.coefficient(1556, stat, 1.25));
				near(1.25, new ServitorShareConfig.Settings(true, Map.of(stat, 1.0)).coefficient(1557, stat, 1.25));
			}
			final var mixed = new ServitorShareConfig.Settings(true, Map.of(Stat.POWER_ATTACK, 2.0, Stat.MAGIC_ATTACK, 1.0));
			near(2.0, mixed.coefficient(1557, Stat.POWER_ATTACK, 1.5)); near(1.25, mixed.coefficient(1557, Stat.MAGIC_ATTACK, 1.25));
			near(.5, mixed.coefficient(1557, Stat.POWER_ATTACK, .5)); near(1.2, mixed.coefficient(1557, Stat.MAX_CP, 1.2));
			final var loaded = ServitorShareConfig.read(java.nio.file.Path.of(ServitorShareConfig.FILE));
			final var expected = profile(context);
			if (loaded.enabled() != Boolean.parseBoolean(expected.get("EnableServitorShareBonusTuning")) || loaded.multipliers().size() != 11) { throw new AssertionError("All eleven owner multipliers must load independently."); }
			final Map<Stat, String> keys = Map.ofEntries(Map.entry(Stat.POWER_ATTACK, "PAtk"), Map.entry(Stat.POWER_DEFENCE, "PDef"), Map.entry(Stat.MAGIC_ATTACK, "MAtk"), Map.entry(Stat.MAGIC_DEFENCE, "MDef"), Map.entry(Stat.MAX_HP, "MaxHp"), Map.entry(Stat.MAX_MP, "MaxMp"), Map.entry(Stat.MAX_RECOVERABLE_HP, "MaxRecoverableHp"), Map.entry(Stat.MAX_RECOVERABLE_MP, "MaxRecoverableMp"), Map.entry(Stat.CRITICAL_RATE, "CritRate"), Map.entry(Stat.POWER_ATTACK_SPEED, "PAtkSpd"), Map.entry(Stat.MAGIC_ATTACK_SPEED, "MAtkSpd"));
			for (var entry : keys.entrySet()) { near(Double.parseDouble(expected.get("ServitorShare" + entry.getValue() + "BonusMultiplier")), loaded.multipliers().get(entry.getKey())); }
		});
		registry.add("owner-player-effective-values", context ->
		{
			final var expected = profile(context);
			final Map<String, Double> actual = Map.ofEntries(
				Map.entry("AltWeightLimit", PlayerConfig.ALT_WEIGHT_LIMIT), Map.entry("MaxPvtStoreSellSlotsDwarf", (double) PlayerConfig.MAX_PVTSTORESELL_SLOTS_DWARF), Map.entry("MaxPvtStoreSellSlotsOther", (double) PlayerConfig.MAX_PVTSTORESELL_SLOTS_OTHER),
				Map.entry("MaxPvtStoreBuySlotsDwarf", (double) PlayerConfig.MAX_PVTSTOREBUY_SLOTS_DWARF), Map.entry("MaxPvtStoreBuySlotsOther", (double) PlayerConfig.MAX_PVTSTOREBUY_SLOTS_OTHER),
				Map.entry("MaximumSlotsForNoDwarf", (double) PlayerConfig.INVENTORY_MAXIMUM_NO_DWARF), Map.entry("MaximumSlotsForDwarf", (double) PlayerConfig.INVENTORY_MAXIMUM_DWARF), Map.entry("MaximumSlotsForGMPlayer", (double) PlayerConfig.INVENTORY_MAXIMUM_GM), Map.entry("MaximumSlotsForQuestItems", (double) PlayerConfig.INVENTORY_MAXIMUM_QUEST_ITEMS),
				Map.entry("MaximumWarehouseSlotsForDwarf", (double) PlayerConfig.WAREHOUSE_SLOTS_DWARF), Map.entry("MaximumWarehouseSlotsForNoDwarf", (double) PlayerConfig.WAREHOUSE_SLOTS_NO_DWARF), Map.entry("MaximumWarehouseSlotsForClan", (double) PlayerConfig.WAREHOUSE_SLOTS_CLAN),
				Map.entry("MaximumFreightSlots", (double) PlayerConfig.ALT_FREIGHT_SLOTS), Map.entry("DwarfRecipeLimit", (double) PlayerConfig.DWARF_RECIPE_LIMIT), Map.entry("CommonRecipeLimit", (double) PlayerConfig.COMMON_RECIPE_LIMIT));
			for (var entry : actual.entrySet()) { near(Double.parseDouble(expected.get(entry.getKey())), entry.getValue()); }
			final var policy = new org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy(1, 1, 1, 1, 30, 20, 0, 0, 0, 1, 1, 0, 0, java.util.List.of(0), java.util.List.of(new org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy.Site(0, 0, 0, 1, 1, 60)), java.util.List.of());
			near(20, policy.linesPerStore());
			final var lines = java.util.stream.IntStream.range(1, 21).mapToObj(id -> new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Line(id, 1871, 1, 1)).toList();
			final var plan = new org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan(org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.Type.SELL, org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.State.OPEN, "20 lines", lines, 1);
			near(20, org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.decode(plan.encode()).lines().size());
			if (PlayerConfig.DISABLE_OVER_ENCHANTING != Boolean.parseBoolean(expected.get("DisableOverEnchanting")) || PlayerConfig.OVER_ENCHANT_PROTECTION != Boolean.parseBoolean(expected.get("OverEnchantProtection"))) { throw new AssertionError("Enchant protections differ from the current owner profile."); }
		});
		registry.add("native-enchant-boundaries-and-bonus-overcap", context ->
		{
			final var groups = EnchantItemGroupsData.getInstance();
			final var expected = profile(context);
			final int maximum = Integer.parseInt(expected.get("standard scroll maxEnchant previously16"));
			for (String name : new String[] { "ARMOR_GROUP", "FULL_ARMOR_GROUP", "FIGHTER_WEAPON_GROUP", "MAGE_WEAPON_GROUP" })
			{
				final var group = groups.getItemGroup(name);
				for (int level : new int[] { 0, 2, 3, 4, 9, 10, 15, 16, maximum - 1, maximum, maximum + 1 })
				{
					near(chance(expected.get(name.equals("FULL_ARMOR_GROUP") ? "FULL_ARMOR_GROUP.current" : "ARMOR_GROUP/FIGHTER_WEAPON_GROUP/MAGE_WEAPON_GROUP.current"), level), group.getChance(level));
				}
			}
			near(maximum, groups.getMaxWeaponEnchant()); near(maximum, groups.getMaxArmorEnchant()); near(maximum, groups.getMaxAccessoryEnchant());
			final var target = ItemData.getInstance().getAllWeapons().stream().filter(item -> item.isEnchantable() && item.getCrystalTypePlus().name().equals("D")).findFirst().orElseThrow();
			final var normal = EnchantItemData.getInstance().getScrolls().stream().filter(scroll -> scroll.getId() == 955).findFirst().orElseThrow();
			near(maximum, normal.getMaxEnchantLevel());
			final double chance = normal.getChance(target, maximum - 1);
			if (chance <= 0 || normal.calculateSuccess(target, maximum - 1, null, chance - .001) != EnchantResultType.SUCCESS || chance < 100 && normal.calculateSuccess(target, maximum - 1, null, chance) != EnchantResultType.FAILURE) { throw new AssertionError("Native chance boundary."); }
			final StatSet bonus = new StatSet(); bonus.set("id", 955); bonus.set("targetGrade", "D"); bonus.set("bonusRate", 100);
			final var unboundedBonus = new EnchantScroll(bonus);
			if (unboundedBonus.calculateSuccess(target, 25, null, 0) != EnchantResultType.ERROR || normal.isValid(target, maximum, null)) { throw new AssertionError("bonusRate100/default65535 bypasses the native hard limit or configured ordinary cap."); }
		});
	}

	private void registerRecovery(PhantomTestRegistry registry)
	{
		registry.add("ordinary-shop-suspends-exact-remaining-plan", context ->
		{
			final var first = new PhantomStorePlan.Line(101, 1865, 8, 40);
			final var second = new PhantomStorePlan.Line(102, 1864, 8, 20);
			final var original = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.OPEN, "Remaining native stock", java.util.List.of(first, second), 1000);
			final var partial = new PhantomStorePlan.Line(101, 1865, 3, 40);
			final var suspended = PhantomStoreService.suspendedPlan(original, java.util.List.of(partial)).orElseThrow();
			final var decoded = PhantomStorePlan.decode(suspended.encode());
			PhantomAssertions.assertEquals(suspended, decoded, "Restart changed the exact remaining native plan.");
			PhantomAssertions.assertEquals(java.util.List.of(partial), decoded.lines(), "Restart restored a sold-out line or increased remaining count.");
			PhantomAssertions.assertEquals(1000L, decoded.expiresEpochMillis(), "Suspension extended the original absolute expiry.");
			PhantomAssertions.assertEquals(PhantomStorePlan.State.REQUESTED, decoded.state(), "Suspended native stock was reported open.");
			PhantomAssertions.assertEquals(suspended, PhantomStoreService.suspendedPlan(decoded, decoded.lines()).orElseThrow(), "Repeated suspension rewrote the plan.");
			PhantomAssertions.assertTrue(PhantomStoreService.suspendedPlan(original, java.util.List.of()).isEmpty(), "Empty native stock became a new listing.");
			for (var invalid : java.util.List.of(new PhantomStorePlan.Line(101, 1865, 9, 40), new PhantomStorePlan.Line(101, 1865, 3, 41), new PhantomStorePlan.Line(103, 1865, 3, 40)))
			{
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomStoreService.suspendedPlan(original, java.util.List.of(invalid)), "Changed count/quote/object entered restart stock.");
			}
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomStoreService.suspendedPlan(original, java.util.List.of(partial, partial)), "Duplicated native line entered restart stock.");
			final String source = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/economy/PhantomStoreService.java"));
			final String shutdown = source.substring(source.indexOf("public ShutdownResult shutdown("), source.indexOf("private ActionLease acquireExclusive("));
			if (!shutdown.contains("suspendForRestart(cursor)")) { throw new AssertionError("Graceful restart still deletes the accepted ordinary store plan."); }
		});
		registry.add("ordinary-shop-recovery-guards-final-world-publication", context ->
		{
			final var plan = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Accepted native need", java.util.List.of(new PhantomStorePlan.Line(1865, 1865, 3, 40)), 1000);
			final var retained = new PhantomStoreService.RetainedPlan(501, 7, plan);
			final var request = new PhantomAutonomousMarketProducer.RecoveryRequest(retained, 21, 2, 1);
			PhantomAssertions.assertTrue(request.validFor(retained, 21, 2, 999), "Exact unexpired recovery binding was rejected.");
			final var noGoal = new PhantomAutonomousMarketProducer.RecoveryRequest(retained, 0, 0, 1);
			PhantomAssertions.assertTrue(noGoal.validFor(retained, 0, 0, 999), "A store without an active farm root lost its accepted finite continuation.");
			PhantomAssertions.assertFalse(request.validFor(retained, 21, 2, 1000), "Restart extended the accepted store lifetime.");
			PhantomAssertions.assertFalse(request.validFor(retained, 21, 3, 999), "A changed root borrowed the old recovery claim.");
			PhantomAssertions.assertFalse(request.validFor(new PhantomStoreService.RetainedPlan(502, 7, plan), 21, 2, 999), "A different character borrowed the old store claim.");
			PhantomAssertions.assertFalse(request.validFor(new PhantomStoreService.RetainedPlan(501, 8, plan), 21, 2, 999), "Recovery overwrote a concurrent durable revision.");
			final var repriced = plan.withLines(java.util.List.of(new PhantomStorePlan.Line(1865, 1865, 3, 41)));
			PhantomAssertions.assertFalse(request.validFor(new PhantomStoreService.RetainedPlan(501, 7, repriced), 21, 2, 999), "Recovery accepted a different quote.");
			final var descriptors = new java.util.HashMap<Long, PhantomAutonomousMarketProducer.RecoveryRequest>();
			descriptors.put(1L, request);
			final var loading = descriptors.get(1L); // Admission passes before the native Entry exists.
			descriptors.remove(1L); // Cancellation deletes its row and releases the current descriptor.
			PhantomAssertions.assertFalse(loading.equals(descriptors.get(1L)), "A cancelled accepted load lost its captured recovery identity.");
			PhantomAssertions.assertFalse(loading.validFor(null, 21, 2, 999), "A deleted row still authorized a captured native load.");
			descriptors.put(1L, new PhantomAutonomousMarketProducer.RecoveryRequest(retained, 21, 2, 2));
			PhantomAssertions.assertFalse(loading.equals(descriptors.get(1L)), "A replacement admission inherited the old accepted load.");
			final var calls = new java.util.concurrent.atomic.AtomicInteger();
			final var bridge = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge();
			final var guard = new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort()
			{
				@Override public void beforeMaterialize(long id, int objectId) { }
				@Override public void afterPlayerLoad(long id, org.l2jmobius.gameserver.model.actor.Player player) { }
				@Override public void beforeWorldSpawn(long id, org.l2jmobius.gameserver.model.actor.Player player) { calls.incrementAndGet(); }
				@Override public void materializeSucceeded(long id, int objectId) { }
				@Override public void materializeAborted(long id, int objectId) { }
				@Override public void beforeStore(long id, org.l2jmobius.gameserver.model.actor.Player player) { }
				@Override public void afterStore(long id, org.l2jmobius.gameserver.model.actor.Player player) { }
			};
			bridge.install(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.chain(org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.none(), guard));
			bridge.beforeWorldSpawn(1, null);
			PhantomAssertions.assertEquals(1, calls.get(), "Existing lifecycle bridge/chain swallowed final spawn admission.");
			final String source = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/player/PhantomMaterializedPlayer.java"));
			final int login = source.indexOf("Quest.playerEnter(_player)");
			final int guardOffset = source.indexOf("_lifecycleSupport.beforeWorldSpawn(_player)", Math.max(0, login));
			final int spawn = source.indexOf("_player.spawnMe()", Math.max(0, login));
			if (login < 0 || guardOffset <= login || spawn <= guardOffset) { throw new AssertionError("Recovery visibility admission must recheck actual post-login coordinates before native World publication."); }
			final String system = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"));
			final int preload = system.indexOf("_autonomousMarketProducer.prepareRecoveryOwners()");
			final int start = system.indexOf("_scheduler.start()", Math.max(0, preload));
			if (preload < 0 || start <= preload) { throw new AssertionError("Pending ordinary store owners were not fenced before scheduler startup."); }
			final String demand = system.substring(system.indexOf("private boolean hasAcceptedNativeJourney("), system.indexOf("private java.util.List", system.indexOf("private boolean hasAcceptedNativeJourney(")));
			if (demand.indexOf("_autonomousMarketProducer.requiresNativeContext") < 0 || demand.indexOf("_autonomousMarketProducer.requiresNativeContext") > demand.indexOf("root.goalStatus()")) { throw new AssertionError("No-goal or completed-root store continuation is incorrectly behind the ACTIVE-only life journey gate."); }
			final String producer = java.nio.file.Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/economy/PhantomAutonomousMarketProducer.java"));
			if (!producer.contains("_stores.discardRecovery(profileId, request.retained())")) { throw new AssertionError("A pending recovery can clear a foreign native store through broad close."); }
			for (boolean abort : new boolean[] { false, true })
			{
				final var order = new java.util.ArrayList<String>();
				final var firstFailure = new IllegalStateException("first terminal failure");
				final var secondFailure = new IllegalStateException("second terminal failure");
				final var first = terminalLifecycle(() -> { order.add("first"); throw firstFailure; });
				final var second = terminalLifecycle(() -> { order.add("second"); throw secondFailure; });
				final var chained = org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.chain(first, second);
				try
				{
					if (abort) { chained.materializeAborted(1, 501); } else { chained.materializeSucceeded(1, 501); }
					throw new AssertionError("Terminal failure was swallowed.");
				}
				catch (IllegalStateException failure)
				{
					PhantomAssertions.assertTrue(failure == (abort ? secondFailure : firstFailure), "Terminal fanout replaced its original failure.");
					PhantomAssertions.assertEquals(java.util.List.of(abort ? firstFailure : secondFailure), java.util.List.of(failure.getSuppressed()), "Terminal fanout lost the secondary failure.");
				}
				PhantomAssertions.assertEquals(abort ? java.util.List.of("second", "first") : java.util.List.of("first", "second"), order, "Terminal fanout skipped cleanup or changed lifecycle order.");
			}
			final String finalGuard = producer.substring(producer.indexOf("@Override public void beforeWorldSpawn("), producer.indexOf("@Override public void afterPlayerLoad("));
			if (!producer.contains("_loadingRecoveries") || finalGuard.indexOf("loading.equals(request)") < 0 || finalGuard.indexOf("loading.equals(request)") > finalGuard.indexOf("if (request == null)")) { throw new AssertionError("Cancellation between admission and Entry registration can bypass final market spawn admission."); }
			final String startup = producer.substring(producer.indexOf("public synchronized void prepareRecoveryOwners("), producer.indexOf("public boolean hasRecovery("));
			if (startup.indexOf("!OfflineTradeConfig.OFFLINE_TRADE_ENABLE") < 0 || startup.indexOf("!OfflineTradeConfig.OFFLINE_TRADE_ENABLE") > startup.indexOf("_stores.planOwnersAfter(")) { throw new AssertionError("Disabled cold startup still scans durable market plans."); }
			final String nativeDemand = producer.substring(producer.indexOf("@Override public boolean requiresNativeContext("), producer.indexOf("public synchronized void beginStop("));
			if (!nativeDemand.contains("OfflineTradeConfig.OFFLINE_TRADE_ENABLE")) { throw new AssertionError("Disabled market recovery still demands native Players."); }
			final String pulse = producer.substring(producer.indexOf("public synchronized void pulse("), producer.indexOf("public synchronized Optional<PhantomStorePlan> consider("));
			final int disabled = pulse.indexOf("if (!OfflineTradeConfig.OFFLINE_TRADE_ENABLE)");
			final int durableScan = pulse.indexOf("_stores.planOwnersAfter(");
			if (disabled < 0 || disabled > durableScan || !pulse.substring(disabled, durableScan).contains("return;")) { throw new AssertionError("Disabled market pulse reaches new durable scans or actor admission."); }
			final String beforeLoad = producer.substring(producer.indexOf("@Override public void beforeMaterialize("), producer.indexOf("@Override public void beforeWorldSpawn("));
			if (!beforeLoad.contains("_recoveryEnabled && OfflineTradeConfig.OFFLINE_TRADE_ENABLE && _stores.retainedPlan") || !finalGuard.contains("_recoveryEnabled && OfflineTradeConfig.OFFLINE_TRADE_ENABLE && _stores.retainedPlan")) { throw new AssertionError("Disabled ordinary materialization performs market repository reads."); }
		});
	}

	private static org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort terminalLifecycle(Runnable terminal)
	{
		return new org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort()
		{
			@Override public void beforeMaterialize(long profileId, int characterObjectId) { }
			@Override public void afterPlayerLoad(long profileId, org.l2jmobius.gameserver.model.actor.Player player) { }
			@Override public void materializeSucceeded(long profileId, int characterObjectId) { terminal.run(); }
			@Override public void materializeAborted(long profileId, int characterObjectId) { terminal.run(); }
			@Override public void beforeStore(long profileId, org.l2jmobius.gameserver.model.actor.Player player) { }
			@Override public void afterStore(long profileId, org.l2jmobius.gameserver.model.actor.Player player) { }
		};
	}

	private static Map<String, String> profile(PhantomTestContext context) throws java.io.IOException
	{
		final Map<String, String> values = new java.util.HashMap<>();
		for (String row : java.nio.file.Files.readAllLines(context.moduleRoot().resolve("dist/game/config/OwnerProfile38.tsv")).subList(1, 39))
		{
			final String[] fields = row.split("\t", -1);
			if (fields.length != 5 || values.putIfAbsent(fields[2], fields[3]) != null) { throw new AssertionError("Invalid owner profile fixture."); }
		}
		return values;
	}

	private static double chance(String ranges, int level)
	{
		for (String segment : ranges.split(";"))
		{
			final String[] pair = segment.split(":"), bounds = pair[0].split("-");
			if (level >= Integer.parseInt(bounds[0]) && level <= Integer.parseInt(bounds[bounds.length - 1])) { return Double.parseDouble(pair[1]); }
		}
		throw new AssertionError("Owner enchant range does not cover " + level);
	}

	private static void near(double expected, double actual)
	{
		if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1e-9) { throw new AssertionError("Expected " + expected + ", got " + actual); }
	}
}

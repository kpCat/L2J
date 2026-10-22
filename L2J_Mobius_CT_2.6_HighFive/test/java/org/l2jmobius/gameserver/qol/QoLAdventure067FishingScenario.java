/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.AdventureSettings;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.data.xml.FishData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.TransformData;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.handler.TargetHandler;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.fishing.Fish;
import org.l2jmobius.gameserver.model.fishing.Fishing;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.enums.ShotType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.FishingZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Three bounded native checks; component fixtures are distinguished from the paid START route. */
public final class QoLAdventure067FishingScenario
{
	private QoLAdventure067FishingScenario()
	{
	}

	public static void run(Player owner, Path taskRoot) throws Exception
	{
		final PersonalCharacterQoLService access = PersonalCharacterQoLService.getInstance();
		final Settings previous = access.settings();
		final Location previousLocation = new Location(owner);
		final int previousInstance = owner.getInstanceId();
		final int previousLevel = owner.getLevel();
		final List<String> results = new ArrayList<>();
		final Path evidence = taskRoot.resolve("reports/fishing067-native.txt");
		Files.createDirectories(evidence.getParent());
		try
		{
			PhantomAssertions.assertTrue(owner.isOnline() && owner.isSpawned() && !owner.isAlikeDead() && !owner.isGM() && owner.getClient() == null && !owner.hasHeadlessOutboundSession(), "Fishing scenario requires the own ordinary spawned native fixture without a synthetic session.");
			PhantomAssertions.assertTrue(access.isFishingAssistEnabled(owner), "Fishing scenario personal policy is disabled.");
			endCombat(owner);
			owner.getStat().setLevel((byte) 10);
			owner.setCurrentHp(owner.getMaxHp());
			owner.setCurrentMp(owner.getMaxMp());
			for (int id : new int[] {1312, 1313, 1314, 1315})
			{
				owner.addSkill(SkillData.getInstance().getSkill(id, 1), false);
			}
			if (TargetHandler.getInstance().getHandler(TargetType.SELF) == null)
			{
				ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			}
			final Item rod = owner.addItem(ItemProcessType.QUEST, 6529, 1, null, false);
			final Item lure = owner.addItem(ItemProcessType.QUEST, 7807, 20, null, false);
			final Item shots = owner.addItem(ItemProcessType.QUEST, 6535, 8, null, false);
			owner.getInventory().equipItem(rod);
			owner.getInventory().equipItem(lure);
			results.add("INPUTS=PREMEASUREMENT_OWN_FIXTURE;rod=6529;lure=7807;shots=6535;level=10;skills=1312/1313/1314/1315:1;actualNetworkSession=false");
			results.add(nativeStartAndOutcome(owner, lure, shots));
			modeTable(owner, lure, shots, results);
			invalidationMatrix(owner, rod, lure, previous, results);
			results.add("CLIENT_QA=DEFERRED_BY_OWNER; native session guards were preserved");
		}
		catch (Exception | AssertionError failure)
		{
			results.add("FAIL=" + failure.getClass().getSimpleName() + ": " + failure.getMessage());
			throw failure;
		}
		finally
		{
			endCombat(owner);
			access.installForTests(previous);
			if (owner.isTransformed()) { owner.untransform(); }
			if (owner.isDead()) { owner.doRevive(); }
			owner.setOnlineStatus(true, false);
			owner.setInstanceId(previousInstance);
			owner.getStat().setLevel((byte) previousLevel);
			relocate(owner, previousLocation);
			Files.write(evidence, results, StandardCharsets.UTF_8);
		}
	}

	private static String nativeStartAndOutcome(Player owner, Item lure, Item shots) throws Exception
	{
		// These coordinates come from fishing_place_7 and 24_14_water5 in the stock zone XML.
		final int x = 156740;
		final int y = -109600;
		final int z = -2500;
		final boolean hookZones = ZoneManager.getInstance().getZones(x + 200, y).stream().anyMatch(FishingZone.class::isInstance)
			&& ZoneManager.getInstance().getZones(x + 200, y).stream().anyMatch(WaterZone.class::isInstance);
		if (!hookZones)
		{
			return "01_NATIVE_START=NOT_RUN;registered stock fishing/water intersection missing;no substitute geometry";
		}
		PhantomAssertions.assertTrue(GeneralConfig.ALLOW_FISHING, "Stock fishing configuration is disabled.");
		owner.setInstanceId(0);
		relocate(owner, new Location(x, y, z, 0));
		PhantomAssertions.assertFalse(owner.isInsideZone(ZoneId.WATER), "Audited shore fixture is underwater.");
		final Set<Integer> priorNpcIds = World.getInstance().getVisibleObjects(owner, Npc.class).stream().map(Npc::getObjectId).collect(Collectors.toSet());
		chargeShot(owner, shots);
		final long baitBefore = lure.getCount();
		final double mpBefore = owner.getCurrentMp();
		PhantomAssertions.assertTrue(owner.useMagic(owner.getKnownSkill(1312), false, true), "Native START skill was rejected.");
		if (!owner.isFishing())
		{
			return "01_NATIVE_START=BLOCKED;native effect refused audited geometry/stock restrictions;no bypass or alternate shore";
		}
		PhantomAssertions.assertEquals(baitBefore - 1, lure.getCount(), "Native START did not consume exactly one owned bait.");
		PhantomAssertions.assertTrue(owner.getCurrentMp() <= mpBefore - 1, "Native START did not consume its MP.");
		if (!await(() -> owner.getFishCombat() != null || !owner.isFishing(), 20000) || owner.getFishCombat() == null)
		{
			endCombat(owner);
			return "01_NATIVE_START=PASS;fee=bait1,MP1;NATIVE_BITE=NOT_RUN_WITHIN_20S;no forced bite";
		}
		final Fishing combat = owner.getFishCombat();
		final int fishItem = intField(combat, "_fishId");
		final long before = owner.getInventory().getInventoryItemCount(fishItem, -1);
		final double beforeCombatMp = owner.getCurrentMp();
		final boolean finished = await(() -> owner.getFishCombat() == null, 35000);
		PhantomAssertions.assertTrue(finished, "Native fish combat did not terminate within its bounded stock duration.");
		final int remainingHp = intField(combat, "_fishCurHp");
		final long reward = owner.getInventory().getInventoryItemCount(fishItem, -1) - before;
		// The stock 5% level-10 outcome may create this real fishing monster instead of an item.
		for (Npc npc : World.getInstance().getVisibleObjects(owner, Npc.class))
		{
			if (npc.getId() == 18319 && !priorNpcIds.contains(npc.getObjectId())) { npc.deleteMe(); }
		}
		PhantomAssertions.assertFalse(owner.isChargedShot(ShotType.FISH_SOULSHOTS), "Native combat did not consume the charged Fishing Shot.");
		PhantomAssertions.assertTrue(reward == 0 || reward == 1, "Native fishing multiplied the item reward.");
		final long completedCount = inventoryCount(owner);
		combat.run();
		combat.run();
		PhantomAssertions.assertEquals(completedCount, inventoryCount(owner), "A completed fishing owner produced another item.");
		return "01_NATIVE_START=PASS;fee=bait1,MP1;NATIVE_COMBAT=" + (remainingHp == 0 ? "WIN" : "LOSS") + ";fishItem=" + fishItem + ";itemDelta=" + reward + ";combatMP=" + beforeCombatMp + "->" + owner.getCurrentMp() + ";stock regen/monster chance retained;lateTickNoOutput=PASS";
	}

	private static void modeTable(Player owner, Item lure, Item shots, List<String> results) throws Exception
	{
		final int[][] cases = {{0, 0, 1313}, {1, 0, 1314}, {0, 1, 1314}, {1, 1, 1313}};
		for (int[] row : cases)
		{
			awaitSkillReady(owner, row[2]);
			final Fishing combat = fixtureCombat(owner, lure, row[0], row[1]);
			try
			{
				chargeShot(owner, shots);
				QoLAdventure067FishingChecks.assertAssistedTick(owner, row[2]);
				PhantomAssertions.assertFalse(owner.isChargedShot(ShotType.FISH_SOULSHOTS), "Native effect did not consume the precharged Fishing Shot.");
				PhantomAssertions.assertTrue(intField(combat, "_goodUse") != 2, "Assist selected the harmful action for the native fish state.");
			}
			finally { endCombat(owner); }
		}
		results.add("02_MODE_TABLE=PASS;states=00:Pump,10:Reel,01:Reel,11:Pump;nativeMP2/reuse2s/FishShots/resist retained;INPUT=DECLARED_PREMEASUREMENT_REAL_FISHDATA_COMPONENT_FIXTURES;no HP/RNG edits during ticks");
	}

	private static void invalidationMatrix(Player owner, Item rod, Item lure, Settings enabled, List<String> results) throws Exception
	{
		final PersonalCharacterQoLService access = PersonalCharacterQoLService.getInstance();
		final Settings childOff = withAccess(enabled, true, false, true);
		final Settings masterOff = withAccess(enabled, false, true, true);
		final Settings ordinary = withAccess(enabled, true, true, false);
		for (Settings denied : List.of(childOff, masterOff, ordinary))
		{
			fixtureCombat(owner, lure, 0, 0);
			try { QoLAdventure067FishingChecks.assertInvalidationIsPermanent(owner, () -> access.installForTests(denied), () -> access.installForTests(enabled)); }
			finally { endCombat(owner); access.installForTests(enabled); }
		}
		fixtureCombat(owner, lure, 0, 0);
		try (var attachment = owner.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32)))
		{
			QoLAdventure067FishingChecks.assertUnassistedTick(owner);
		}
		QoLAdventure067FishingChecks.assertUnassistedTick(owner);
		endCombat(owner);
		final Item replacement = owner.addItem(ItemProcessType.QUEST, 6529, 1, null, false);
		fixtureCombat(owner, lure, 0, 0);
		try { QoLAdventure067FishingChecks.assertInvalidationIsPermanent(owner, () -> owner.getInventory().equipItem(replacement), () -> owner.getInventory().equipItem(rod)); }
		finally { endCombat(owner); owner.getInventory().equipItem(rod); }
		fixtureCombat(owner, lure, 0, 0);
		try { QoLAdventure067FishingChecks.assertInvalidationIsPermanent(owner, () -> owner.setOnlineStatus(false, false), () -> owner.setOnlineStatus(true, false)); }
		finally { endCombat(owner); owner.setOnlineStatus(true, false); }
		final int priorInstance = owner.getInstanceId();
		final var instance = InstanceManager.getInstance().createDynamicInstance(0);
		fixtureCombat(owner, lure, 0, 0);
		try { QoLAdventure067FishingChecks.assertInvalidationIsPermanent(owner, () -> owner.setInstanceId(instance.getId()), () -> owner.setInstanceId(priorInstance)); }
		finally { endCombat(owner); owner.setInstanceId(priorInstance); InstanceManager.getInstance().destroyInstance(instance.getId()); }
		fixtureCombat(owner, lure, 0, 0);
		try
		{
			QoLAdventure067FishingChecks.assertInvalidationIsPermanent(owner, () ->
			{
				PhantomAssertions.assertTrue(TransformData.getInstance().transformPlayer(1, owner), "Native transform fixture is missing.");
				PhantomAssertions.assertTrue(owner.isTransformed(), "Native transformation did not apply.");
			}, owner::untransform);
		}
		finally { endCombat(owner); if (owner.isTransformed()) { owner.untransform(); } }
		fixtureCombat(owner, lure, 0, 0);
		try { QoLAdventure067FishingChecks.assertInvalidationIsPermanent(owner, () -> PhantomAssertions.assertTrue(owner.doDie(owner), "Native death fixture failed."), owner::doRevive); }
		finally { endCombat(owner); if (owner.isDead()) { owner.doRevive(); } }
		results.add("03_INVALIDATION=PASS;childOFF/masterOFF/nonallowlisted/headless/rodObject/onlineFalse/instance/nativeTransform/nativeDeath;permanentUntilNewManualCast=true;INPUT=PREMEASUREMENT_COMPONENT_FIXTURES;onlineFalse is state guard, not full network logout proof");
	}

	private static Fishing fixtureCombat(Player owner, Item lure, int mode, int deceptive) throws Exception
	{
		endCombat(owner);
		awaitSkillReady(owner, 1313);
		awaitSkillReady(owner, 1314);
		owner.setCurrentMp(owner.getMaxMp());
		owner.setLure(lure);
		owner.setFishing(true);
		final List<Fish> fish = FishData.getInstance().getFish(1, deceptive == 1 ? 8 : 1, deceptive == 1 ? 2 : 1);
		PhantomAssertions.assertFalse(fish.isEmpty(), "Canonical ordinary/upper-grade fish fixture is missing.");
		setField(owner, "_fish", fish.getFirst().clone());
		owner.startFishCombat(false, deceptive == 1);
		final Fishing combat = owner.getFishCombat();
		final Future<?> task = (Future<?>) field(combat, "_fishAiTask");
		task.cancel(false);
		setField(combat, "_mode", mode);
		setField(combat, "_deceptiveMode", deceptive);
		setField(combat, "_stop", 1);
		return combat;
	}

	private static void chargeShot(Player owner, Item shots)
	{
		if (owner.isChargedShot(ShotType.FISH_SOULSHOTS)) { return; }
		final var handler = ItemHandler.getInstance().getHandler(shots.getEtcItem());
		PhantomAssertions.assertTrue(handler != null, "MasterHandler did not register native FishShots.");
		final long before = shots.getCount();
		PhantomAssertions.assertTrue(handler.onItemUse(owner, shots, false), "Owned native Fishing Shot could not be charged.");
		PhantomAssertions.assertEquals(before - 1, shots.getCount(), "Native FishShots did not debit one owned item.");
		PhantomAssertions.assertTrue(owner.isChargedShot(ShotType.FISH_SOULSHOTS), "Native Fishing Shot charge is absent.");
	}

	private static Settings withAccess(Settings source, boolean master, boolean fishing, boolean allowlisted)
	{
		final AdventureSettings a = source.adventureSettings();
		return new Settings(master, source.crossClassSkillsEnabled(), source.crystallizationEnabled(), source.sevenSignsAccessEnabled(), source.partySupportEnabled(), allowlisted ? source.allowedCharacterIds() : Set.of(), allowlisted ? source.allowedAccounts() : Set.of(), true, "Own067 negative fixture.", source.effectDurationSettings(), new AdventureSettings(a.soloInstanceEntry(), a.wondrousCubicCooldown(), a.wondrousCubicCooldownMillis(), fishing, a.hennaNegativeMode(), a.positiveAugmentation(), a.positiveAugmentationMultiplier(), ""));
	}

	private static void awaitSkillReady(Player owner, int skillId) throws Exception
	{
		PhantomAssertions.assertTrue(await(() -> !owner.isSkillDisabled(owner.getKnownSkill(skillId)), 2500), "Previous native fishing reuse did not expire.");
	}

	private static boolean await(BooleanSupplier condition, long timeout) throws InterruptedException
	{
		final long deadline = System.nanoTime() + timeout * 1000000;
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(25); }
		return condition.getAsBoolean();
	}

	private static void relocate(Player owner, Location location)
	{
		owner.decayMe();
		owner.setXYZInvisible(location.getX(), location.getY(), location.getZ());
		owner.setHeading(location.getHeading());
		owner.spawnMe();
		owner.revalidateZone(true);
	}

	private static void endCombat(Player owner)
	{
		if (owner.getFishCombat() != null) { owner.getFishCombat().doDie(false); }
		else if (owner.isFishing()) { owner.endFishing(false); }
	}

	private static long inventoryCount(Player owner)
	{
		return owner.getInventory().getItems().stream().mapToLong(Item::getCount).sum();
	}

	private static Object field(Object object, String name) throws ReflectiveOperationException
	{
		final Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(object);
	}

	private static int intField(Object object, String name) throws ReflectiveOperationException
	{
		return ((Number) field(object, name)).intValue();
	}

	private static void setField(Object object, String name, Object value) throws ReflectiveOperationException
	{
		final Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(object, value);
	}
}

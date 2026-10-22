/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.HennaNegativeMode;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.data.xml.HennaData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.holders.creature.TimeStamp;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.instancezone.InstanceWorld;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Integrated component checks on the campaign-owned native TEST lane, without a network substitute. */
public final class QoLAdventure067CoreChecks
{
	private QoLAdventure067CoreChecks()
	{
	}

	public static void run(Player owner, Path taskRoot) throws Exception
	{
		PhantomAssertions.assertTrue("task067native".equals(owner.getAccountName()) && !owner.isGM() && owner.isSpawned() && !owner.isAlikeDead() && owner.getClient() == null && !owner.hasHeadlessOutboundSession(), "Core checks require the campaign-owned ordinary native fixture.");
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		final Settings previous = service.installForTests(configuration(taskRoot, true, Integer.toString(owner.getObjectId()), true, "FLIP_NEGATIVE"));
		final int previousLevel = owner.getLevel();
		final Map<Integer, Long> ownerReuse = new HashMap<>(InstanceManager.getInstance().getAllInstanceTimes(owner.getObjectId()));
		try
		{
			admissionMatrix(owner, taskRoot);
			try (Fixture peer = new Fixture(nativePlayer("Core067Peer", owner)); Fixture leader = new Fixture(nativePlayer("Core067Lead", owner)))
			{
				instanceChecks(owner, peer.player, leader.player, taskRoot);
				itemAndStatsChecks(peer, owner, taskRoot);
			}
			System.out.println("OWN067_CORE G1=PASS; G2=PASS; G4=PASS; G7=PASS; G8=PASS; networkPacketRoute=NOT_RUN; CLIENT_QA=DEFERRED_BY_OWNER");
		}
		finally
		{
			try
			{
				final InstanceWorld remaining = InstanceManager.getInstance().getPlayerWorld(owner);
				if (remaining != null) { destroyAndWait(remaining); }
				restoreInstanceTimes(owner, ownerReuse);
			}
			finally
			{
				owner.getStat().setLevel((byte) previousLevel);
				service.installForTests(previous);
			}
		}
	}

	private static void admissionMatrix(Player owner, Path taskRoot) throws Exception
	{
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		final String id = Integer.toString(owner.getObjectId());
		service.installForTests(configuration(taskRoot, true, id, true, "FLIP_NEGATIVE"));
		assertAdventureFlags(owner, true);
		service.installForTests(configuration(taskRoot, false, id, true, "FLIP_NEGATIVE"));
		assertAdventureFlags(owner, false);
		service.installForTests(configuration(taskRoot, true, id, false, "OFF"));
		assertAdventureFlags(owner, false);
		service.installForTests(configuration(taskRoot, true, "", true, "FLIP_NEGATIVE"));
		assertAdventureFlags(owner, false);
		service.installForTests(configuration(taskRoot, true, "invalid", true, "FLIP_NEGATIVE"));
		assertAdventureFlags(owner, false);
		service.installForTests(configuration(taskRoot, true, id, true, "FLIP_NEGATIVE"));
		for (OwnerKind kind : new OwnerKind[] {OwnerKind.PHANTOM, OwnerKind.BACKGROUND})
		{
			try (var lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(owner.getObjectId(), kind))
			{
				PhantomAssertions.assertTrue(lease != null, "Native fixture identity was already leased before the negative ownership check.");
				assertAdventureFlags(owner, false);
			}
		}
		assertAdventureFlags(owner, true);
		System.out.println("OWN067_G1 nativePolicy masterOFF/childOFF/nonallow/invalid/PHANTOM/BACKGROUND=PASS; noHeadlessOrAuthMutation=true");
	}

	private static void assertAdventureFlags(Player player, boolean expected)
	{
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		PhantomAssertions.assertEquals(expected, service.isSoloInstanceEntryEnabled(player), "Personal solo admission did not follow the common gate.");
		PhantomAssertions.assertEquals(expected, service.isWondrousCubicCooldownEnabled(player), "Personal Cubic did not follow the common gate.");
		PhantomAssertions.assertEquals(expected, service.isFishingAssistEnabled(player), "Personal fishing did not follow the common gate.");
		PhantomAssertions.assertEquals(expected ? HennaNegativeMode.FLIP_NEGATIVE : HennaNegativeMode.OFF, service.getHennaNegativeMode(player), "Personal henna did not follow the common gate.");
		PhantomAssertions.assertEquals(expected ? 2.0 : 1.0, service.getPositiveAugmentationMultiplier(player), "Personal augmentation did not follow the common gate.");
		PhantomAssertions.assertEquals(expected, PersonalInstanceAdmissionService.snapshot(player) != null, "Solo roster admission did not follow the common gate.");
	}

	private static void instanceChecks(Player owner, Player peer, Player leader, Path taskRoot) throws Exception
	{
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		final String allowed = owner.getObjectId() + "," + leader.getObjectId();
		final Settings active = configuration(taskRoot, true, allowed, true, "OFF");
		service.installForTests(active);
		final InstanceManager manager = InstanceManager.getInstance();
		clearMeasuredReuse(owner);
		clearMeasuredReuse(leader);
		clearMeasuredReuse(peer);
		final Quest script = kamaloka();
		final Npc captain = captain(owner);
		InstanceWorld world = null;
		Party party = null;
		try
		{
			owner.getStat().setLevel((byte) 50);
			script.onEvent("0", captain, owner);
			PhantomAssertions.assertTrue(manager.getPlayerWorld(owner) == null && owner.getInstanceId() == 0, "Wrong-level solo entry created a Kamaloka world.");
			peer.getStat().setLevel((byte) 23);
			script.onEvent("0", captain, peer);
			PhantomAssertions.assertTrue(manager.getPlayerWorld(peer) == null && peer.getInstanceId() == 0, "A foreign solo entrant received a Kamaloka world.");
			owner.getStat().setLevel((byte) 23);
			script.onEvent("0", captain, owner);
			world = requireWorld(owner, 57, 1);
			final Npc boss = world.getNpcs(18554).stream().findFirst().orElseThrow(() -> new AssertionError("Native Kamaloka boss18554 was not spawned."));
			// Exercise the native completion callback only; this is not a combat or earned boss-reward claim.
			script.onKill(boss, owner, false);
			PhantomAssertions.assertTrue(manager.getInstanceTime(owner.getObjectId(), 57) > System.currentTimeMillis(), "Native Kamaloka completion callback did not record daily reuse.");
			world.ejectPlayer(owner);
			owner.onTeleported();
			PhantomAssertions.assertEquals(0, owner.getInstanceId(), "Native world ejection did not leave Kamaloka.");
			destroyAndWait(world);
			world = null;
			script.onEvent("0", captain, owner);
			PhantomAssertions.assertTrue(manager.getPlayerWorld(owner) == null, "Native daily reuse allowed another Kamaloka world.");

			owner.getStat().setLevel((byte) 29);
			script.onEvent("2", captain, owner);
			world = requireWorld(owner, 73, 1);
			final Npc exit = world.getNpcs(32496).stream().findFirst().orElseThrow(() -> new AssertionError("Native Labyrinth escape NPC32496 was not spawned."));
			PhantomAssertions.assertEquals("32496.htm", script.onFirstTalk(exit, owner), "Native Labyrinth first-talk rejected its admitted solo owner.");
			script.onTalk(exit, peer);
			PhantomAssertions.assertEquals(0, peer.getInstanceId(), "Foreign escape talk changed the foreign player's instance.");
			PhantomAssertions.assertEquals(world.getInstanceId(), owner.getInstanceId(), "Foreign escape talk ejected the solo owner.");
			service.installForTests(configuration(taskRoot, true, allowed, false, "OFF"));
			script.onTalk(exit, owner);
			owner.onTeleported();
			PhantomAssertions.assertEquals(0, owner.getInstanceId(), "Switching personal entry OFF trapped an already admitted solo owner.");
			destroyAndWait(world);
			world = null;
			service.installForTests(active);

			leader.getStat().setLevel((byte) 23);
			peer.getStat().setLevel((byte) 23);
			leader.teleToLocation(captain.getX(), captain.getY(), captain.getZ());
			peer.teleToLocation(captain.getX() + 20, captain.getY(), captain.getZ());
			leader.onTeleported();
			peer.onTeleported();
			party = new Party(leader, PartyDistributionType.FINDERS_KEEPERS);
			leader.setParty(party);
			peer.joinParty(party);
			PhantomAssertions.assertEquals(2, party.getMemberCount(), "Native party fixture did not contain both real Players.");
			script.onEvent("0", captain, leader);
			peer.onTeleported();
			world = requireWorld(leader, 57, 2);
			PhantomAssertions.assertTrue(world.isAllowed(peer) && peer.getInstanceId() == world.getInstanceId() && manager.getPlayerWorld(peer) == world, "The native two-member party was split across instances.");
			world.ejectPlayer(leader);
			world.ejectPlayer(peer);
			leader.onTeleported();
			peer.onTeleported();
			destroyAndWait(world);
			world = null;
			System.out.println("OWN067_G2 Kamaloka57/23 solo; Labyrinth73/29 solo+nativeEscapeAfterOFF; Party2sameWorld; wrongLevel/foreign/reuse=PASS; completionEvidence=nativeCallbackWithoutCombatRewardClaim");
		}
		finally
		{
			if (world != null) { destroyAndWait(world); }
			if (party != null) { party.disbandParty(); }
			captain.deleteMe();
			service.installForTests(active);
		}
	}

	private static InstanceWorld requireWorld(Player player, int templateId, int allowedCount)
	{
		// Native lifecycle acknowledgement only; the component suite has no network client.
		player.onTeleported();
		final InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(player);
		PhantomAssertions.assertTrue(world != null && world.getTemplateId() == templateId && world.isAllowed(player), "Native instance admission produced the wrong world or owner.");
		PhantomAssertions.assertEquals(world.getInstanceId(), player.getInstanceId(), "Admitted Player was not teleported into its native world.");
		PhantomAssertions.assertEquals(allowedCount, world.getAllowed().size(), "Native world allowed roster has the wrong size.");
		return world;
	}

	private static void clearMeasuredReuse(Player player)
	{
		final InstanceManager manager = InstanceManager.getInstance();
		manager.getAllInstanceTimes(player.getObjectId());
		manager.deleteInstanceTime(player.getObjectId(), 57);
		manager.deleteInstanceTime(player.getObjectId(), 73);
	}

	private static void restoreInstanceTimes(Player player, Map<Integer, Long> previous)
	{
		clearMeasuredReuse(player);
		for (int template : new int[] {57, 73})
		{
			if (previous.containsKey(template)) { InstanceManager.getInstance().setInstanceTime(player.getObjectId(), template, previous.get(template)); }
		}
	}

	private static void destroyAndWait(InstanceWorld world) throws Exception
	{
		final int instanceId = world.getInstanceId();
		world.destroy();
		await(() -> InstanceManager.getInstance().getInstance(instanceId) == null, "Native instance cleanup did not remove instance " + instanceId + ".");
	}

	public static void soloExitRegression(Player owner, Path taskRoot) throws Exception
	{
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		final Settings previous = service.installForTests(configuration(taskRoot, true, Integer.toString(owner.getObjectId()), true, "OFF"));
		final int previousLevel = owner.getLevel();
		InstanceWorld world = null;
		Npc captain = null;
		try
		{
			PhantomAssertions.assertTrue(owner.isSpawned() && !owner.isAlikeDead() && !owner.isInParty(), "Solo exit requires an alive ordinary spawned solo fixture.");
			owner.getStat().setLevel((byte) 29);
			final Quest kamaloka = kamaloka();
			captain = captain(owner);
			world = InstanceManager.getInstance().getPlayerWorld(owner);
			if (world == null)
			{
				kamaloka.onEvent("2", captain, owner);
				world = InstanceManager.getInstance().getPlayerWorld(owner);
			}
			PhantomAssertions.assertTrue(world != null && world.getTemplateId() == 73 && world.isAllowed(owner), "Solo exit fixture did not enter native Labyrinth73.");
			owner.onTeleported();
			PhantomAssertions.assertEquals(world.getInstanceId(), owner.getInstanceId(), "Solo Labyrinth did not teleport its admitted owner.");
			final Npc exit = world.getNpcs(32496).stream().findFirst().orElseThrow(() -> new AssertionError("Native Labyrinth escape teleporter was not spawned."));
			PhantomAssertions.assertEquals("32496.htm", kamaloka.onFirstTalk(exit, owner), "Native escape first-talk rejected its admitted solo owner.");
			kamaloka.onTalk(exit, owner);
			owner.onTeleported();
			PhantomAssertions.assertEquals(0, owner.getInstanceId(), "Native escape talk left its admitted solo owner inside the Labyrinth.");
		}
		finally
		{
			if (world != null) { destroyAndWait(world); }
			if (captain != null) { captain.deleteMe(); }
			owner.getStat().setLevel((byte) previousLevel);
			service.installForTests(previous);
		}
	}

	private static void itemAndStatsChecks(Fixture fixture, Player owner, Path taskRoot) throws Exception
	{
		final PersonalCharacterQoLService service = PersonalCharacterQoLService.getInstance();
		final String allowed = owner.getObjectId() + "," + fixture.player.getObjectId();
		final Settings active = configuration(taskRoot, true, allowed, true, "OFF");
		service.installForTests(active);
		Player actor = fixture.player;
		Item cubic = actor.getInventory().getItemByItemId(10632);
		if (cubic == null) { cubic = actor.addItem(ItemProcessType.QUEST, 10632, 1, null, false); }
		QoLAdventure067CubicChecks.legacyTimestampUsesPersonalHour(actor, cubic);
		QoLAdventure067CubicChecks.recentTimestampBlocksPersonalReuse(actor, cubic);
		service.installForTests(configuration(taskRoot, true, allowed, false, "OFF"));
		QoLAdventure067CubicChecks.disabledTimestampRetainsDailyReuse(actor, cubic);
		service.installForTests(active);
		IItemHandler handler = ItemHandler.getInstance().getHandler(cubic.getEtcItem());
		if (handler == null)
		{
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			handler = ItemHandler.getInstance().getHandler(cubic.getEtcItem());
		}
		PhantomAssertions.assertTrue(handler != null, "Native Cubic item handler did not register.");
		actor.addTimeStampItem(cubic, 86_400_000L, System.currentTimeMillis() + 82_740_000L);
		final long beforeReward = cubicRewards(actor);
		PhantomAssertions.assertTrue(handler.onItemUse(actor, cubic, false), "Native ItemSkills refused an eligible Cubic after sixty-one minutes.");
		// This is the same native successful-handler timestamp API used by UseItem; no packet/auth claim.
		actor.addTimeStampItem(cubic, cubic.getReuseDelay());
		final Player rewarding = actor;
		await(() -> cubicRewards(rewarding) > beforeReward, "Native Cubic skill2510 did not create its stock reward.");
		await(() -> !rewarding.isCastingNow() && !rewarding.isCastingSimultaneouslyNow(), "Native Cubic casting did not finish.");
		final long afterReward = cubicRewards(actor);
		PhantomAssertions.assertFalse(handler.onItemUse(actor, cubic, false), "Native ItemSkills allowed the Cubic to repeat before sixty minutes.");
		PhantomAssertions.assertEquals(afterReward, cubicRewards(actor), "Rejected Cubic use produced another reward.");
		final long canonicalStamp = actor.getItemReuseTimeStamps().get(cubic.getObjectId()).getStamp();
		if (!actor.getSubClasses().containsKey(1))
		{
			PhantomAssertions.assertTrue(actor.addSubClass(PlayerClass.GLADIATOR.getId(), 1), "Native subclass fixture did not create.");
		}
		actor.setActiveClass(1);
		PhantomAssertions.assertEquals(1, actor.getClassIndex(), "Native subclass activation failed.");
		PhantomAssertions.assertTrue(actor.getItemRemainingReuseTime(cubic.getObjectId()) > 0, "Subclass activation cleared Cubic reuse.");
		actor.setActiveClass(0);
		PhantomAssertions.assertEquals(0, actor.getClassIndex(), "Native base-class restoration failed.");

		clearHennas(actor);
		final int baselineStr = actor.getSTR();
		final int baselineCon = actor.getCON();
		PhantomAssertions.assertTrue(actor.addHenna(HennaData.getInstance().getHenna(1)), "Native stock dye1 did not apply.");
		assertHenna(actor, 1, -3, baselineStr, baselineCon);
		clearHennas(actor);
		service.installForTests(configuration(taskRoot, true, allowed, true, "IGNORE_NEGATIVE"));
		PhantomAssertions.assertTrue(actor.addHenna(HennaData.getInstance().getHenna(1)), "Native IGNORE dye did not apply.");
		assertHenna(actor, 1, 0, baselineStr, baselineCon);
		clearHennas(actor);
		service.installForTests(configuration(taskRoot, true, allowed, true, "FLIP_NEGATIVE"));
		PhantomAssertions.assertTrue(actor.addHenna(HennaData.getInstance().getHenna(1)) && actor.addHenna(HennaData.getInstance().getHenna(1)), "Native FLIP dye pair did not apply.");
		assertHenna(actor, 2, 5, baselineStr, baselineCon);
		fixture.reload();
		actor = fixture.player;
		cubic = actor.getInventory().getItemByItemId(10632);
		PhantomAssertions.assertTrue(cubic != null, "Native reload lost the Cubic item.");
		final TimeStamp restored = actor.getItemReuseTimeStamps().get(cubic.getObjectId());
		PhantomAssertions.assertTrue(restored != null && restored.getReuse() == 86_400_000L && restored.getStamp() == canonicalStamp, "Native reload changed the canonical daily Cubic timestamp.");
		PhantomAssertions.assertTrue(actor.getItemRemainingReuseTime(cubic.getObjectId()) > 3_500_000L, "Native reload lost the personal hourly Cubic limit.");
		assertHenna(actor, 2, 5, baselineStr, baselineCon);
		PhantomAssertions.assertTrue(actor.removeHenna(1), "First persisted dye did not remove.");
		assertHenna(actor, 1, 3, baselineStr, baselineCon);
		clearHennas(actor);
		assertHenna(actor, 0, 0, baselineStr, baselineCon);
		PhantomAssertions.assertTrue(actor.addHenna(HennaData.getInstance().getHenna(1)), "Repeated native dye application failed.");
		assertHenna(actor, 1, 3, baselineStr, baselineCon);
		clearHennas(actor);
		assertHenna(actor, 0, 0, baselineStr, baselineCon);
		QoLAdventure067AugmentationChecks.nativePositiveBonuses(actor, 2.0);
		QoLAdventure067AugmentationChecks.ordinaryOptionsKeepStockBonuses(actor);
		QoLAdventure067AugmentationChecks.mixedFunctionsKeepNativeSemantics(actor);
		service.installForTests(configuration(taskRoot, true, allowed, false, "OFF"));
		QoLAdventure067CubicChecks.disabledTimestampRetainsDailyReuse(actor, cubic);
		QoLAdventure067AugmentationChecks.nativePositiveBonuses(actor, 1.0);
		service.installForTests(configuration(taskRoot, true, Integer.toString(owner.getObjectId()), true, "FLIP_NEGATIVE"));
		QoLAdventure067CubicChecks.disabledTimestampRetainsDailyReuse(actor, cubic);
		PhantomAssertions.assertTrue(actor.addHenna(HennaData.getInstance().getHenna(1)), "Ordinary dye fixture did not apply.");
		assertHenna(actor, 1, -3, baselineStr, baselineCon);
		clearHennas(actor);
		System.out.println("OWN067_G4 nativeItemSkills2510Reward/repeatDenied/canonical24h/hourBoundary/subclass/reload/childOFF/foreign=PASS; packetUseItem=NOT_RUN");
		System.out.println("OWN067_G7 nativeDye1 OFF/IGNORE/FLIP/cap5/repeatedRemove/reload/foreign=PASS; acquisitionFeeRoute=NOT_RUN");
		System.out.println("OWN067_G8 stockAugmentation131073/duplicate/remove/reapply/globalTemplate/ordinaryOptions/mixedFunctions/OFF=PASS");
	}

	private static long cubicRewards(Player player)
	{
		return player.getInventory().getItems().stream().filter(item -> ((item.getId() >= 10633) && (item.getId() <= 10646)) || ((item.getId() >= 21096) && (item.getId() <= 21106)) || (item.getId() == 21593)).mapToLong(Item::getCount).sum();
	}

	private static void clearHennas(Player player)
	{
		for (int slot = 1; slot <= 3; slot++)
		{
			if (player.getHenna(slot) != null) { PhantomAssertions.assertTrue(player.removeHenna(slot), "Native dye cleanup failed."); }
		}
	}

	private static void assertHenna(Player player, int str, int con, int baselineStr, int baselineCon)
	{
		PhantomAssertions.assertEquals(str, player.getHennaStatSTR(), "Native henna STR modifier differs.");
		PhantomAssertions.assertEquals(con, player.getHennaStatCON(), "Native henna CON modifier differs.");
		PhantomAssertions.assertEquals(baselineStr + str, player.getSTR(), "Effective STR does not contain the native henna modifier exactly once.");
		PhantomAssertions.assertEquals(baselineCon + con, player.getCON(), "Effective CON does not contain the native henna modifier exactly once.");
	}

	private static Quest kamaloka() throws Exception
	{
		if (ScriptManager.getInstance().getScript("Kamaloka") == null)
		{
			ScriptEngine.getInstance().executeScript(Path.of("instances/Kamaloka/Kamaloka.java"));
		}
		final Quest script = ScriptManager.getInstance().getScript("Kamaloka");
		PhantomAssertions.assertTrue(script != null, "Native Kamaloka script did not load.");
		return script;
	}

	private static Npc captain(Player owner)
	{
		final Npc npc = new Npc(NpcData.getInstance().getTemplate(30332));
		npc.spawnMe(owner.getX() + 20, owner.getY(), owner.getZ());
		return npc;
	}

	private static Settings configuration(Path taskRoot, boolean enabled, String characterIds, boolean children, String hennaMode) throws Exception
	{
		final Path path = taskRoot.resolve("private/core-checks.ini");
		Files.writeString(path, "EnablePersonalCharacterQoL=" + enabled + "\nEnablePersonalCrossClassSkills=False\nEnablePersonalCrystallization=False\nAllowedAccounts=\nAllowedCharacterIds=" + characterIds
			+ "\nEnablePersonalSoloInstanceEntry=" + children + "\nEnablePersonalWondrousCubicCooldown=" + children + "\nPersonalWondrousCubicCooldownMinutes=60\nEnablePersonalFishingAssist=" + children
			+ "\nPersonalHennaNegativeMode=" + hennaMode + "\nEnablePersonalPositiveAugmentationMultiplier=" + children + "\nPersonalPositiveAugmentationMultiplier=2.0\n", StandardCharsets.UTF_8);
		return PersonalCharacterQoLConfig.read(path);
	}

	private static Player nativePlayer(String name, Player owner) throws Exception
	{
		Integer objectId = null;
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task067native' AND char_name=? AND accesslevel=0"))
		{
			query.setString(1, name);
			try (var rows = query.executeQuery())
			{
				if (rows.next())
				{
					objectId = rows.getInt(1);
					PhantomAssertions.assertFalse(rows.next(), "Own native core fixture name is ambiguous.");
				}
			}
		}
		final Player player = objectId == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task067native", name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(objectId);
		PhantomAssertions.assertTrue(player != null && !player.isGM() && player.getClient() == null && !player.hasHeadlessOutboundSession(), "Own core fixture was not an ordinary native Player.");
		prepare(player, owner.getX() + 40, owner.getY(), owner.getZ());
		return player;
	}

	private static void prepare(Player player, int x, int y, int z)
	{
		player.setOnlineStatus(true, false);
		player.stopAllTasks();
		if (player.isDead()) { player.doRevive(); }
		player.getStatus().setCurrentHp(player.getMaxHp());
		player.setCurrentMp(player.getMaxMp());
		player.spawnMe(x, y, z);
	}

	private static void await(BooleanSupplier ready, String message) throws Exception
	{
		final long deadline = System.nanoTime() + 5_000_000_000L;
		while (!ready.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(25); }
		PhantomAssertions.assertTrue(ready.getAsBoolean(), message);
	}

	private static final class Fixture implements AutoCloseable
	{
		private Player player;
		private final Map<Integer, Long> instanceTimes;

		private Fixture(Player value)
		{
			player = value;
			instanceTimes = new HashMap<>(InstanceManager.getInstance().getAllInstanceTimes(value.getObjectId()));
		}

		private void reload()
		{
			final int objectId = player.getObjectId();
			final int x = player.getX();
			final int y = player.getY();
			final int z = player.getZ();
			player.storeMe();
			// Native deletion owns the online -> offline transition and persists it itself.
			PhantomAssertions.assertTrue(player.deleteMe(), "Native reload cleanup failed to delete the online fixture.");
			PhantomAssertions.assertFalse(player.isOnline(), "Native reload cleanup did not mark the fixture offline.");
			player = Player.load(objectId);
			PhantomAssertions.assertTrue(player != null && player.getClient() == null && !player.hasHeadlessOutboundSession(), "Native core fixture failed to reload without a synthetic session.");
			prepare(player, x, y, z);
		}

		@Override
		public void close() throws Exception
		{
			final InstanceWorld world = InstanceManager.getInstance().getPlayerWorld(player);
			if (world != null) { destroyAndWait(world); }
			if (player.isInParty()) { player.getParty().disbandParty(); }
			restoreInstanceTimes(player, instanceTimes);
			player.stopAllTasks();
			player.storeMe();
			PhantomAssertions.assertTrue(player.deleteMe(), "Native final cleanup failed to delete the online fixture.");
			PhantomAssertions.assertFalse(player.isOnline(), "Native final cleanup did not mark the fixture offline.");
		}
	}
}

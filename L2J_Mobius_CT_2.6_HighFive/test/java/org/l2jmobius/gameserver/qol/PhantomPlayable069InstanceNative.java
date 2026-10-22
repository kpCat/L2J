/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.instancezone.InstanceWorld;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.qol.PersonalCharacterQoLService;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** Only the existing Kamaloka/Labyrinth admission/exit/reuse matrix. No encounter reward claim. */
public final class PhantomPlayable069InstanceNative
{
	public static void main(String[] args) throws Exception
	{
		if ((args.length != 0 && (args.length != 1 || !args[0].equals("resume"))) || !Boolean.getBoolean("phantom.task069.native")) { throw new IllegalArgumentException("Own069 native instance lane or explicit retained-fixture resume required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path task = module.getParent().resolve(".phantom-local/task069").toRealPath();
		final var players = new ArrayList<Player>();
		Npc captain = null; InstanceWorld world = null;
		PersonalCharacterQoLConfig.Settings previous = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(69006905L, module, task.resolve("reports/instances-native")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance();
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			if (args.length == 0) { try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task069instance','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); } }
			for (int index = 0; index < 3; index++)
			{
				Player player;
				if (args.length == 0) { player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task069instance", "Instance069" + index, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)); }
				else
				{
					try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name='task069instance' AND char_name=? AND accesslevel=0"))
					{
						query.setString(1, "Instance069" + index); try (var rows = query.executeQuery()) { check(rows.next(), "Retained actor absent."); player = Player.load(rows.getInt(1)); check(!rows.next(), "Ambiguous retained actor."); }
					}
				}
				PhantomAssertions.assertTrue(player != null && !player.isGM() && player.getClient() == null && !player.hasHeadlessOutboundSession(), "Ordinary native instance fixture required.");
				players.add(player); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setOnlineStatus(true, false); player.spawnMe(81700 + index * 20, 148500, -3470);
			}
			final var owner = players.get(0); final var peer = players.get(1); final var leader = players.get(2);
			final Path policy = task.resolve("private/instances069.ini");
			Files.writeString(policy, "EnablePersonalCharacterQoL=True\nEnablePersonalCrossClassSkills=False\nEnablePersonalCrystallization=False\nAllowedAccounts=\nAllowedCharacterIds=" + owner.getObjectId() + "\nEnablePersonalSoloInstanceEntry=True\n");
			final var settings = PersonalCharacterQoLConfig.read(policy); check(settings.enabled() && settings.adventureSettings().soloInstanceEntry(), "Declared test policy was rejected: " + settings);
			previous = PersonalCharacterQoLService.getInstance().installForTests(settings);
			ScriptEngine.getInstance().executeScript(Path.of("instances/Kamaloka/Kamaloka.java"));
			final var script = ScriptManager.getInstance().getScript("Kamaloka"); final var manager = InstanceManager.getInstance();
			captain = new Npc(NpcData.getInstance().getTemplate(30332)); captain.spawnMe(owner.getX() + 20, owner.getY(), owner.getZ());
			check(manager.getPlayerWorld(owner) == null && owner.getInstanceId() == 0, "Retained actor is still admitted; preserve that world instead of rerunning.");
			owner.getStat().setLevel((byte) 1); script.onEvent("0", captain, owner); check(manager.getPlayerWorld(owner) == null, "Wrong level admitted into ordinary Kamaloka.");
			owner.getStat().setLevel((byte) 23); script.onEvent("0", captain, owner); owner.onTeleported(); world = manager.getPlayerWorld(owner);
			check(world != null && world.getTemplateId() == 57 && world.isAllowed(owner) && owner.getInstanceId() == world.getInstanceId(), "Native solo Kamaloka57 admission failed.");
			world.ejectPlayer(owner); owner.onTeleported(); check(owner.getInstanceId() == 0, "Native Kamaloka ejection failed."); retire(world); world = null;
			// A declared existing reuse stamp is a negative precondition, never a fabricated victory/callback.
			manager.setInstanceTime(owner.getObjectId(), 57, System.currentTimeMillis() + 60000); script.onEvent("0", captain, owner); check(manager.getPlayerWorld(owner) == null, "Reuse stamp bypassed.");
			owner.getStat().setLevel((byte) 29); script.onEvent("2", captain, owner); owner.onTeleported(); world = manager.getPlayerWorld(owner);
			check(world != null && world.getTemplateId() == 73 && world.isAllowed(owner), "Native Labyrinth73 admission failed.");
			final Npc exit = world.getNpcs(32496).stream().findFirst().orElseThrow(); script.onTalk(exit, peer); check(peer.getInstanceId() == 0, "Foreign exit moved peer.");
			Files.writeString(policy, "EnablePersonalCharacterQoL=False\nEnablePersonalCrossClassSkills=False\nEnablePersonalCrystallization=False\nAllowedAccounts=\nAllowedCharacterIds=\nEnablePersonalSoloInstanceEntry=False\n");
			PersonalCharacterQoLService.getInstance().installForTests(PersonalCharacterQoLConfig.read(policy)); script.onTalk(exit, owner); owner.onTeleported(); check(owner.getInstanceId() == 0, "OFF trapped admitted owner."); retire(world); world = null;
			leader.getStat().setLevel((byte) 23); peer.getStat().setLevel((byte) 23);
			leader.teleToLocation(captain.getX(), captain.getY(), captain.getZ()); peer.teleToLocation(captain.getX() + 20, captain.getY(), captain.getZ()); leader.onTeleported(); peer.onTeleported();
			final Party party = new Party(leader, PartyDistributionType.FINDERS_KEEPERS); leader.setParty(party); peer.joinParty(party);
			script.onEvent("0", captain, leader); leader.onTeleported(); peer.onTeleported(); world = manager.getPlayerWorld(leader);
			check(world != null && world.getTemplateId() == 57 && world.isAllowed(peer) && peer.getInstanceId() == world.getInstanceId(), "Ordinary native Party2 split or refused.");
			world.ejectPlayer(leader); world.ejectPlayer(peer); leader.onTeleported(); peer.onTeleported(); retire(world); world = null; party.disbandParty();
			System.out.println("OWN069_KAMALOKA_LABYRINTH_PASS; solo57/73+OFFexit+Party2sameWorld; wrongLevel/foreign/reuse refused; no completion callback or victory/reward claim");
		}
		finally
		{
			if (world != null) { retire(world); }
			if (captain != null) { captain.deleteMe(); }
			if (previous != null) { PersonalCharacterQoLService.getInstance().installForTests(previous); }
			for (var player : players) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}
	private static void retire(InstanceWorld world) throws Exception
	{
		final int id = world.getInstanceId(); world.destroy();
		final long end = System.nanoTime() + 3000000000L;
		while (InstanceManager.getInstance().getInstance(id) != null && System.nanoTime() < end) { Thread.sleep(20); }
		check(InstanceManager.getInstance().getInstance(id) == null, "Owned instance did not retire.");
	}
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}

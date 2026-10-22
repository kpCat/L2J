/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.nio.file.Path;
import java.util.Set;
import java.util.TimerTask;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.managers.DimensionalRiftManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.sevensigns.DimensionalRift;
import org.l2jmobius.gameserver.network.clientpackets.RequestRestartPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Only own068 native actors; captures actual production timer actions instead of waiting minutes. */
public final class QoLRift068Native
{
	private QoLRift068Native() { }

	public static void run(Player owner, Player peer, Npc npc, Path taskRoot) throws Exception
	{
		PhantomAssertions.assertTrue(owner.getAccountName().startsWith("task068") && peer.getAccountName().startsWith("task068") && !owner.hasHeadlessOutboundSession() && !peer.hasHeadlessOutboundSession() && !owner.isGM() && !peer.isGM(), "Only own068 ordinary native fixtures may enter this scene.");
		final var service = PersonalCharacterQoLService.getInstance();
		final var previous = service.installForTests(configuration(owner.getObjectId()));
		final var manager = DimensionalRiftManager.getInstance();
		final var waiting = manager.getRoom((byte) 0, (byte) 0).getTeleportCoorinates();
		final var home = new Location(owner.getX(), owner.getY(), owner.getZ());
		try
		{
			PhantomAssertions.assertEquals(2, GeneralConfig.RIFT_MIN_PARTY_SIZE, "Native Party2 control requires the shipped min2 profile; do not change config mid-scene.");
			move(owner, waiting); move(peer, waiting);
			manager.start(owner, (byte) 0, npc); manager.start(owner, (byte) 7, npc);
			PhantomAssertions.assertTrue(owner.getParty() == null, "Unsupported Rift type created a singleton.");
			manager.start(owner, (byte) 1, npc);
			PhantomAssertions.assertTrue(owner.getParty() == null, "Missing fragments created a singleton Party.");
			final int fee = manager.entryReadiness((byte) 1).entryItemCount();
			owner.addItem(ItemProcessType.REWARD, 7079, fee * 8L, null, false);
			peer.addItem(ItemProcessType.REWARD, 7079, fee * 2L, null, false);
			final long pendingBalance = fragments(owner);
			owner.onTransactionRequest(peer);
			try
			{
				manager.start(owner, (byte) 1, npc);
				PhantomAssertions.assertTrue(owner.getParty() == null && fragments(owner) == pendingBalance, "Pending invitation admitted or charged the solo owner.");
			}
			finally { peer.setActiveRequester(null); owner.onTransactionResponse(); }
			manager.start(peer, (byte) 1, npc);
			PhantomAssertions.assertTrue(peer.getParty() == null, "A foreign solo player bypassed min-party.");
			for (OwnerKind kind : new OwnerKind[] {OwnerKind.PHANTOM, OwnerKind.BACKGROUND})
			{
				try (var lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(owner.getObjectId(), kind))
				{
					PhantomAssertions.assertTrue(lease != null, "Fixture has a competing identity owner.");
					manager.start(owner, (byte) 1, npc);
					PhantomAssertions.assertTrue(owner.getParty() == null, "Phantom/Background received a personal singleton.");
				}
			}
			final long before = fragments(owner);
			manager.start(owner, (byte) 1, npc); owner.onTeleported();
			final DimensionalRift rift = requireRift(owner);
			PhantomAssertions.assertEquals(before - fee, fragments(owner), "Native solo fee was not exactly once.");
			final TimerTask oldSpawn = rift.getSpawnTimerTask();
			final TimerTask oldJump = rift.getTeleportTimerTask();
			rift.manualTeleport(owner, npc); owner.onTeleported();
			for (int jump = 2; jump < rift.getMaxJumps(); jump++) { rift.manualTeleport(owner, npc); owner.onTeleported(); }
			final byte room = rift.getCurrentRoom();
			rift.manualTeleport(owner, npc);
			PhantomAssertions.assertEquals(room, rift.getCurrentRoom(), "Personal entry bypassed the native maximum room jumps.");
			oldSpawn.run(); oldJump.run();
			PhantomAssertions.assertEquals(room, rift.getCurrentRoom(), "Replaced callbacks advanced the current session.");
			final TimerTask terminalSpawn = rift.getSpawnTimerTask();
			final TimerTask terminalJump = rift.getTeleportTimerTask();
			terminalSpawn.run();
			final var nativeRoom = manager.getRoom((byte) 1, room);
			final var nativeSpawn = nativeRoom.getSpawns().get(0);
			final Npc retiredNpc = nativeSpawn.getLastSpawn();
			PhantomAssertions.assertTrue(retiredNpc != null && retiredNpc.isSpawned(), "Captured native spawn action did not populate the room.");
			retiredNpc.deleteMe();
			PhantomAssertions.assertTrue(nativeSpawn._scheduledCount > 0, "Native room fixture did not queue a respawn before terminal cleanup.");
			rift.manualExitRift(owner, npc); owner.onTeleported();
			PhantomAssertions.assertEquals(0, nativeSpawn._scheduledCount, "Terminal room cleanup retained a queued native respawn and blocked a later room owner.");
			rift.killRift(); terminalSpawn.run(); terminalJump.run();
			PhantomAssertions.assertTrue(owner.getParty() == null && !manager.getRoom((byte) 1, room).isPartyInside(), "Terminal cleanup left the singleton/room owned.");
			PhantomAssertions.assertTrue(manager.getRoom((byte) 1, room).getSpawns().stream().allMatch(spawn -> spawn.getLastSpawn() == null || !spawn.getLastSpawn().isSpawned()), "A late callback left native room mobs alive.");
			final Object replacementOwner = new Object();
			PhantomAssertions.assertTrue(nativeRoom.tryAcquire(replacementOwner), "Retired room could not be reserved by a new owner.");
			try
			{
				final Npc replacementNpc = nativeSpawn.doSpawn(false);
				nativeSpawn.startRespawn();
				nativeSpawn.respawnNpc(retiredNpc);
				PhantomAssertions.assertTrue(replacementNpc != null && replacementNpc.isSpawned() && !retiredNpc.isSpawned(), "Native pending respawn revived a previous room owner's NPC.");
				replacementNpc.deleteMe();
				PhantomAssertions.assertEquals(1, nativeSpawn._scheduledCount, "A retired owner's pending count blocked native respawn for the replacement owner.");
				nativeSpawn.respawnNpc(replacementNpc);
				PhantomAssertions.assertTrue(replacementNpc.isSpawned(), "The owner guard blocked the current room's native respawn.");
			}
			finally { nativeRoom.release(replacementOwner); }
			System.out.println("OWN068_RIFT_SOLO enter/fee/advance/spawn/exit/replacedAndTerminalTimers=PASS");

			for (String terminal : new String[] {"death", "restart", "logout"})
			{
				move(owner, waiting); manager.start(owner, (byte) 1, npc); owner.onTeleported();
				final DimensionalRift session = requireRift(owner);
				final TimerTask spawn = session.getSpawnTimerTask();
				final TimerTask jump = session.getTeleportTimerTask();
				if ("death".equals(terminal)) { owner.doDie(null); owner.onTeleported(); new RestartFixture().restart(owner); owner.onTeleported(); }
				else if ("restart".equals(terminal)) { new RestartFixture().restart(owner); owner.onTeleported(); }
				else { owner.leaveParty(); }
				spawn.run(); jump.run(); session.killRift();
				PhantomAssertions.assertTrue(owner.getParty() == null, "Terminal solo route retained its owned singleton: " + terminal);
				PhantomAssertions.assertFalse(owner.isAlikeDead(), "Native fixture did not acknowledge teleport/revive after " + terminal);
			}
			System.out.println("OWN068_RIFT_TERMINAL nativeDeath/restartPoint/leaveParty/reentry=PASS; networkLogout=NOT_RUN");

			move(owner, waiting); move(peer, waiting);
			manager.start(owner, (byte) 1, npc); owner.onTeleported();
			final DimensionalRift grownRift = requireRift(owner);
			final Party grown = owner.getParty();
			// Defensive native join proves cleanup cannot dissolve a singleton whose roster grew.
			peer.joinParty(grown);
			grownRift.manualExitRift(owner, npc); owner.onTeleported();
			PhantomAssertions.assertTrue(owner.getParty() == grown && peer.getParty() == grown && grown.getMemberCount() == 2, "Cleanup dissolved an owned singleton after its roster grew.");
			grown.disbandParty();
			System.out.println("OWN068_RIFT_ADMISSION pendingTransaction/noCharge/grownPartyPreserved=PASS");

			move(owner, waiting); move(peer, waiting);
			final Party party = new Party(owner, PartyDistributionType.FINDERS_KEEPERS);
			owner.setParty(party); peer.joinParty(party);
			final long ownerBefore = fragments(owner); final long peerBefore = fragments(peer);
			manager.start(peer, (byte) 1, npc);
			PhantomAssertions.assertFalse(party.isInDimensionalRift(), "Nonleader entered native Rift.");
			manager.start(owner, (byte) 1, npc); owner.onTeleported(); peer.onTeleported();
			final var groupRift = requireRift(owner);
			PhantomAssertions.assertEquals(ownerBefore - fee, fragments(owner), "Party leader native fee differs.");
			PhantomAssertions.assertEquals(peerBefore - fee, fragments(peer), "Party peer native fee differs.");
			peer.doDie(null);
			PhantomAssertions.assertTrue(groupRift.getDeadMemberList().contains(peer), "Native Party2 death did not register the dead member.");
			groupRift.manualTeleport(owner, npc); owner.onTeleported(); peer.onTeleported();
			final var groupRoom = manager.getRoom((byte) 1, groupRift.getCurrentRoom());
			PhantomAssertions.assertTrue(peer.isDead() && groupRoom.checkIfInZone(peer.getX(), peer.getY(), peer.getZ()), "Native room jump stranded a Party2 dead body in the released room.");
			peer.doRevive();
			PhantomAssertions.assertFalse(groupRift.getDeadMemberList().contains(peer), "Native in-room resurrection retained the dead marker.");
			groupRift.manualExitRift(owner, npc); owner.onTeleported(); peer.onTeleported();
			PhantomAssertions.assertTrue(owner.getParty() == party && peer.getParty() == party && party.getMemberCount() == 2 && !party.isInDimensionalRift(), "Solo cleanup dissolved native Party2.");
			party.disbandParty();
			System.out.println("OWN068_RIFT_PARTY2 nativeLeader/fees/exit/rosterPreserved=PASS; clientQA=NOT_RUN");
		}
		finally
		{
			for (Player player : new Player[] {owner, peer})
			{
				if (player.getParty() != null && player.getParty().isInDimensionalRift()) { player.getParty().getDimensionalRift().killRift(); }
				if (player.getParty() != null) { player.getParty().disbandParty(); }
				if (player.isDead()) { player.doRevive(); }
				move(player, home);
			}
			service.installForTests(previous);
		}
	}

	private static long fragments(Player player) { return player.getInventory().getInventoryItemCount(7079, -1); }
	private static void move(Player player, Location loc) { player.teleToLocation(loc); player.onTeleported(); }
	private static DimensionalRift requireRift(Player player)
	{
		PhantomAssertions.assertTrue(player.getParty() != null && player.getParty().isInDimensionalRift(), "Native Rift admission failed.");
		return player.getParty().getDimensionalRift();
	}
	private static PersonalCharacterQoLConfig.Settings configuration(int id)
	{
		return new PersonalCharacterQoLConfig.Settings(true, false, false, false, false, Set.of(id), Set.of(), true, "Own068 native fixture.",
			PersonalCharacterQoLConfig.EffectDurationSettings.disabled("Own068 fixture."),
			new PersonalCharacterQoLConfig.AdventureSettings(false, false, 3600000L, false, PersonalCharacterQoLConfig.HennaNegativeMode.OFF, false, 1.0, "", true));
	}
	private static final class RestartFixture extends RequestRestartPoint
	{
		void restart(Player player) { portPlayer(player); }
	}
}

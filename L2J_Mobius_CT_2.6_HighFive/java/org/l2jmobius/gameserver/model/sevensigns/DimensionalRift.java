/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.model.sevensigns;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.managers.DimensionalRiftManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.model.script.QuestState;
import org.l2jmobius.gameserver.network.serverpackets.Earthquake;

/**
 * @author kombat, BazookaRpm
 */
public class DimensionalRift
{
	// One monitor prevents Party->session/session->Party lock inversion on exit.
	public static final Object LIFECYCLE_LOCK = new Object();
	private final Player _temporaryPartyOwner;
	private final boolean _personalSolo;
	private boolean _terminal;
	private long _generation;
	protected byte _type;
	protected Party _party;
	protected List<Byte> _completedRooms = new ArrayList<>();
	private static final long FIVE_SECONDS = 5000;
	protected byte currentJump = 1; // Initialize to 1 to count the initial room.
	
	private Timer teleporterTimer;
	private TimerTask teleporterTimerTask;
	private Timer spawnTimer;
	private TimerTask spawnTimerTask;
	
	private Future<?> earthQuakeTask;
	
	protected byte _choosenRoom = -1;
	protected Collection<Player> _deadPlayers = ConcurrentHashMap.newKeySet();
	protected Collection<Player> _revivedInWaitingRoom = ConcurrentHashMap.newKeySet();
	private boolean isBossRoom = false;
	
	public DimensionalRift(Party party, byte type, byte room)
	{
		this(party, type, room, null, false);
	}

	public DimensionalRift(Party party, byte type, byte room, Player temporaryPartyOwner, boolean personalSolo)
	{
		_temporaryPartyOwner = temporaryPartyOwner;
		_personalSolo = personalSolo;
		_type = type;
		_party = party;
		_choosenRoom = room;
		synchronized (LIFECYCLE_LOCK)
		{
			final DimensionalRiftRoom first = DimensionalRiftManager.getInstance().getRoom(type, room);
			if ((party == null) || party.isInDimensionalRift() || (first == null) || !first.tryAcquire(this)) { throw new IllegalStateException("Rift admission lost its native Party or room."); }
			try { initialize(party, room); }
			catch (RuntimeException e) { killRift(); throw e; }
		}
	}

	private void initialize(Party party, byte room)
	{
		final Location coords = getRoomCoord(room);
		party.setDimensionalRift(this);
		for (Player p : party.getMembers())
		{
			final Quest riftQuest = ScriptManager.getInstance().getQuest(635);
			if (riftQuest != null)
			{
				QuestState qs = p.getQuestState(riftQuest.getName());
				if (qs == null)
				{
					qs = riftQuest.newQuestState(p);
				}
				
				if (!qs.isStarted())
				{
					qs.startQuest();
				}
			}
			
			p.teleToLocation(coords);
		}
		
		createSpawnTimer(_choosenRoom);
		checkBossRoom(room);
		createTeleporterTimer(true);
	}
	
	public byte getType()
	{
		return _type;
	}
	
	public byte getCurrentRoom()
	{
		return _choosenRoom;
	}
	
	protected void createTeleporterTimer(boolean reasonTP)
	{
		synchronized (LIFECYCLE_LOCK) { createTeleporterTimerLocked(reasonTP); }
	}

	private void createTeleporterTimerLocked(boolean reasonTP)
	{
		if (_terminal || (_party == null))
		{
			return;
		}
		
		if (teleporterTimerTask != null)
		{
			teleporterTimerTask.cancel();
			teleporterTimerTask = null;
		}
		
		if (teleporterTimer != null)
		{
			teleporterTimer.cancel();
			teleporterTimer = null;
		}
		
		if (earthQuakeTask != null)
		{
			earthQuakeTask.cancel(false);
			earthQuakeTask = null;
		}
		
		final long generation = _generation;
		teleporterTimer = new Timer("DimensionalRift-jump", true);
		teleporterTimerTask = new TimerTask()
		{
			@Override
			public void run()
			{
				synchronized (LIFECYCLE_LOCK)
				{
					if (_terminal || (generation != _generation) || (teleporterTimerTask != this)) { return; }
					if (reasonTP && (currentJump < getMaxJumps()) && hasParticipant()) { advanceRoom(); }
					else { killRift(); }
				}
			}
		};
		
		if (reasonTP)
		{
			final long jumpTime = calcTimeToNextJump();
			teleporterTimer.schedule(teleporterTimerTask, jumpTime); // Teleporter task, 8-10 minutes
			
			earthQuakeTask = ThreadPool.schedule(() ->
			{
				synchronized (LIFECYCLE_LOCK)
				{
				if (_terminal || (generation != _generation) || (_party == null)) { return; }
				for (Player p : _party.getMembers())
				{
					if (isParticipant(p))
					{
						p.sendPacket(new Earthquake(p.getX(), p.getY(), p.getZ(), 65, 9));
					}
				}
				}
			}, Math.max(0, jumpTime - 7000));
		}
		else
		{
			teleporterTimer.schedule(teleporterTimerTask, FIVE_SECONDS); // incorrect party member invited.
		}
	}
	
	public void createSpawnTimer(byte room)
	{
		synchronized (LIFECYCLE_LOCK) { createSpawnTimerLocked(room); }
	}

	private void createSpawnTimerLocked(byte room)
	{
		if (_terminal || (_party == null) || (room != _choosenRoom)) { return; }
		if (spawnTimerTask != null)
		{
			spawnTimerTask.cancel();
			spawnTimerTask = null;
		}
		
		if (spawnTimer != null)
		{
			spawnTimer.cancel();
			spawnTimer = null;
		}
		
		final long generation = _generation;
		spawnTimer = new Timer("DimensionalRift-spawn", true);
		spawnTimerTask = new TimerTask()
		{
			@Override
			public void run()
			{
				synchronized (LIFECYCLE_LOCK)
				{
					if (_terminal || (generation != _generation) || (spawnTimerTask != this) || (room != _choosenRoom)) { return; }
					cancelSpawn();
					final DimensionalRiftRoom nativeRoom = DimensionalRiftManager.getInstance().getRoom(_type, room);
					if ((nativeRoom != null) && nativeRoom.isOwnedBy(DimensionalRift.this) && hasParticipant()) { nativeRoom.spawn(); }
				}
			}
		};
		
		spawnTimer.schedule(spawnTimerTask, GeneralConfig.RIFT_SPAWN_DELAY);
	}
	
	public void partyMemberInvited()
	{
		createTeleporterTimer(false);
	}
	
	public void partyMemberExited(Player player)
	{
		synchronized (LIFECYCLE_LOCK) { partyMemberExitedLocked(player); }
	}

	private void partyMemberExitedLocked(Player player)
	{
		if (_terminal || (_party == null)) { return; }
		// Party has already removed this member; the remaining-roster loop cannot return the leaver.
		final DimensionalRiftRoom room = DimensionalRiftManager.getInstance().getRoom(_type, _choosenRoom);
		if ((player.getInstanceId() == 0) && (room != null) && room.checkIfInZone(player.getX(), player.getY(), player.getZ()))
		{
			teleportToWaitingRoom(player);
		}

		if (_deadPlayers.contains(player))
		{
			_deadPlayers.remove(player);
		}
		
		if (_revivedInWaitingRoom.contains(player))
		{
			_revivedInWaitingRoom.remove(player);
		}
		
		if ((_party.getMemberCount() < minimumPartySize()) || (!_personalSolo && (_party.getMemberCount() == 1)))
		{
			killRift();
		}
	}
	
	public void manualTeleport(Player player, Npc npc)
	{
		synchronized (LIFECYCLE_LOCK) { manualTeleportLocked(player, npc); }
	}

	private void manualTeleportLocked(Player player, Npc npc)
	{
		if (_terminal || (_party == null)) { return; }
		final Party party = player.getParty();
		if ((party != _party) || (party.getDimensionalRift() != this))
		{
			return;
		}
		
		if (player.getObjectId() != party.getLeaderObjectId())
		{
			DimensionalRiftManager.getInstance().showHtmlFile(player, "data/html/seven_signs/rift/NotPartyLeader.htm", npc);
			return;
		}
		
		// Check if the maximum jump limit has been reached.
		if (currentJump >= getMaxJumps())
		{
			DimensionalRiftManager.getInstance().showHtmlFile(player, "data/html/seven_signs/rift/AlreadyTeleported.htm", npc);
			return;
		}
		
		if (!hasParticipant()) { killRift(); return; }
		advanceRoom();
	}
	
	public void manualExitRift(Player player, Npc npc)
	{
		synchronized (LIFECYCLE_LOCK) { manualExitRiftLocked(player, npc); }
	}

	private void manualExitRiftLocked(Player player, Npc npc)
	{
		if (_terminal || (_party == null)) { return; }
		final Party party = player.getParty();
		if ((party != _party) || (party.getDimensionalRift() != this))
		{
			return;
		}
		
		if (player.getObjectId() != party.getLeaderObjectId())
		{
			DimensionalRiftManager.getInstance().showHtmlFile(player, "data/html/seven_signs/rift/NotPartyLeader.htm", npc);
			return;
		}
		
		killRift();
	}
	
	private void advanceRoom()
	{
		_generation++;
		cancelTeleporter();
		cancelSpawn();
		final DimensionalRiftManager manager = DimensionalRiftManager.getInstance();
		final byte previous = _choosenRoom;
		_completedRooms.add(previous);
		final List<Byte> free = manager.getFreeRooms(_type);
		final List<Byte> unvisited = new ArrayList<>(free);
		unvisited.removeAll(_completedRooms);
		final List<Byte> candidates = unvisited.isEmpty() ? free : unvisited;
		if (candidates.isEmpty()) { killRift(); return; }
		final byte next = candidates.get(Rnd.get(candidates.size()));
		final DimensionalRiftRoom destination = manager.getRoom(_type, next);
		if (!destination.tryAcquire(this)) { killRift(); return; }
		final List<Player> participants = _party.getMembers().stream().filter(this::canTravel).toList();
		final DimensionalRiftRoom old = manager.getRoom(_type, previous);
		if (old != null) { old.release(this); }
		_choosenRoom = next;
		currentJump++;
		checkBossRoom(next);
		try
		{
			for (Player player : participants) { player.teleToLocation(getRoomCoord(next)); }
			createSpawnTimer(next);
			createTeleporterTimer(true);
		}
		catch (RuntimeException e) { killRift(); throw e; }
	}

	private boolean isInCurrentRoom(Player player)
	{
		final DimensionalRiftRoom room = DimensionalRiftManager.getInstance().getRoom(_type, _choosenRoom);
		return (player != null) && (player.getInstanceId() == 0) && (room != null) && room.isOwnedBy(this) && room.checkIfInZone(player.getX(), player.getY(), player.getZ());
	}

	private boolean isParticipant(Player player)
	{
		return canTravel(player) && !player.isAlikeDead();
	}

	private boolean canTravel(Player player)
	{
		return isInCurrentRoom(player) && player.isOnline() && (player.getParty() == _party) && !_revivedInWaitingRoom.contains(player);
	}

	private boolean hasParticipant() { return (_party != null) && _party.getMembers().stream().anyMatch(this::isParticipant); }
	private int minimumPartySize() { return _personalSolo ? 1 : GeneralConfig.RIFT_MIN_PARTY_SIZE; }
	
	protected void teleportToWaitingRoom(Player player)
	{
		DimensionalRiftManager.getInstance().teleportToWaitingRoom(player);
		final Quest riftQuest = ScriptManager.getInstance().getQuest(635);
		if (riftQuest != null)
		{
			final QuestState qs = player.getQuestState(riftQuest.getName());
			if ((qs != null) && qs.isCond(1))
			{
				qs.exitQuest(true, true);
			}
		}
	}
	
	public void killRift()
	{
		synchronized (LIFECYCLE_LOCK)
		{
			if (_terminal) { return; }
			_terminal = true;
			_generation++;
			cancelTeleporter();
			cancelSpawn();
			final Party party = _party;
			try
			{
				if (party != null)
				{
					for (Player player : party.getMembers()) { if (isInCurrentRoom(player)) { teleportToWaitingRoom(player); } }
				}
			}
			finally
			{
				final DimensionalRiftRoom room = DimensionalRiftManager.getInstance().getRoom(_type, _choosenRoom);
				if (room != null) { room.release(this); }
				if ((party != null) && (party.getDimensionalRift() == this)) { party.setDimensionalRift(null); }
				_party = null;
				_completedRooms.clear();
				_deadPlayers.clear();
				_revivedInWaitingRoom.clear();
				if ((_temporaryPartyOwner != null) && (party != null) && (party.getMemberCount() == 1) && (party.getLeader() == _temporaryPartyOwner) && (_temporaryPartyOwner.getParty() == party) && !party.isInDimensionalRift()) { party.disbandParty(); }
			}
		}
	}

	private void cancelTeleporter()
	{
		if (teleporterTimerTask != null) { teleporterTimerTask.cancel(); teleporterTimerTask = null; }
		if (teleporterTimer != null) { teleporterTimer.cancel(); teleporterTimer = null; }
		if (earthQuakeTask != null) { earthQuakeTask.cancel(false); earthQuakeTask = null; }
	}

	private void cancelSpawn()
	{
		if (spawnTimerTask != null) { spawnTimerTask.cancel(); spawnTimerTask = null; }
		if (spawnTimer != null) { spawnTimer.cancel(); spawnTimer = null; }
	}
	
	public Timer getTeleportTimer()
	{
		return teleporterTimer;
	}
	
	public TimerTask getTeleportTimerTask()
	{
		return teleporterTimerTask;
	}
	
	public Timer getSpawnTimer()
	{
		return spawnTimer;
	}
	
	public TimerTask getSpawnTimerTask()
	{
		return spawnTimerTask;
	}
	
	public void setTeleportTimer(Timer t)
	{
		teleporterTimer = t;
	}
	
	public void setTeleportTimerTask(TimerTask tt)
	{
		teleporterTimerTask = tt;
	}
	
	public void setSpawnTimer(Timer t)
	{
		spawnTimer = t;
	}
	
	public void setSpawnTimerTask(TimerTask st)
	{
		spawnTimerTask = st;
	}
	
	private long calcTimeToNextJump()
	{
		final int time = Rnd.get(GeneralConfig.RIFT_AUTO_JUMPS_TIME_MIN, GeneralConfig.RIFT_AUTO_JUMPS_TIME_MAX) * 1000;
		
		if (isBossRoom)
		{
			return (long) (time * GeneralConfig.RIFT_BOSS_ROOM_TIME_MUTIPLY);
		}
		
		return time;
	}
	
	public void memberDead(Player player)
	{
		synchronized (LIFECYCLE_LOCK)
		{
			if (_terminal || (_party == null) || !_party.containsPlayer(player)) { return; }
			_deadPlayers.add(player);
			if (_personalSolo && (_party.getMemberCount() == 1)) { killRift(); }
		}
	}
	
	public void memberRessurected(Player player)
	{
		synchronized (LIFECYCLE_LOCK)
		{
			if (!_terminal && isInCurrentRoom(player) && !_revivedInWaitingRoom.contains(player)) { _deadPlayers.remove(player); }
		}
	}
	
	public void usedTeleport(Player player)
	{
		synchronized (LIFECYCLE_LOCK)
		{
			if (_terminal || (_party == null) || !_party.containsPlayer(player)) { return; }
			_revivedInWaitingRoom.add(player);
			_deadPlayers.add(player);
			if ((_party.getMemberCount() - _revivedInWaitingRoom.size()) < minimumPartySize()) { killRift(); }
		}
	}
	
	public Collection<Player> getDeadMemberList()
	{
		return List.copyOf(_deadPlayers);
	}
	
	public Collection<Player> getRevivedAtWaitingRoom()
	{
		return List.copyOf(_revivedInWaitingRoom);
	}
	
	public void checkBossRoom(byte room)
	{
		isBossRoom = DimensionalRiftManager.getInstance().getRoom(_type, room).isBossRoom();
	}
	
	public Location getRoomCoord(byte room)
	{
		return DimensionalRiftManager.getInstance().getRoom(_type, room).getTeleportCoorinates();
	}
	
	public byte getMaxJumps()
	{
		if ((GeneralConfig.RIFT_MAX_JUMPS <= 8) && (GeneralConfig.RIFT_MAX_JUMPS >= 1))
		{
			return (byte) GeneralConfig.RIFT_MAX_JUMPS;
		}
		
		return 4;
	}
}

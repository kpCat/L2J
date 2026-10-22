/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius.gameserver.model.sevensigns;

import java.awt.Polygon;
import java.awt.Shape;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.taskmanagers.RespawnTaskManager;

/**
 * Dimensional Rift Room.
 * @author xban1x
 */
public class DimensionalRiftRoom
{
	private final byte _type;
	private final byte _room;
	private final int _xMin;
	private final int _xMax;
	private final int _yMin;
	private final int _yMax;
	private final int _zMin;
	private final int _zMax;
	private final Location _teleportCoords;
	private final Shape _s;
	private final boolean _isBossRoom;
	private final List<Spawn> _roomSpawns = new ArrayList<>();
	private volatile Object _owner;
	
	public DimensionalRiftRoom(byte type, byte room, int xMin, int xMax, int yMin, int yMax, int zMin, int zMax, int xT, int yT, int zT, boolean isBossRoom)
	{
		_type = type;
		_room = room;
		_xMin = (xMin + 128);
		_xMax = (xMax - 128);
		_yMin = (yMin + 128);
		_yMax = (yMax - 128);
		_zMin = zMin;
		_zMax = zMax;
		_teleportCoords = new Location(xT, yT, zT);
		_isBossRoom = isBossRoom;
		_s = new Polygon(new int[]
		{
			xMin,
			xMax,
			xMax,
			xMin
		}, new int[]
		{
			yMin,
			yMin,
			yMax,
			yMax
		}, 4);
	}
	
	public byte getType()
	{
		return _type;
	}
	
	public byte getRoom()
	{
		return _room;
	}
	
	public int getRandomX()
	{
		return Rnd.get(_xMin, _xMax);
	}
	
	public int getRandomY()
	{
		return Rnd.get(_yMin, _yMax);
	}
	
	public Location getTeleportCoorinates()
	{
		return _teleportCoords;
	}
	
	public boolean checkIfInZone(int x, int y, int z)
	{
		return _s.contains(x, y) && (z >= _zMin) && (z <= _zMax);
	}
	
	public boolean isBossRoom()
	{
		return _isBossRoom;
	}
	
	public List<Spawn> getSpawns()
	{
		return _roomSpawns;
	}

	/** A queued native respawn belongs to the room session which created its NPC. */
	public Spawn createSpawn(int npcId) throws Exception
	{
		return new Spawn(npcId)
		{
			private Object _spawnOwner;
			private final Set<Npc> _sessionNpcs = new HashSet<>();

			@Override
			public Npc doSpawn(boolean isSummonSpawn)
			{
				synchronized (DimensionalRift.LIFECYCLE_LOCK)
				{
					if (_owner == null) { return null; }
					if (_spawnOwner != _owner) { _sessionNpcs.clear(); _spawnOwner = _owner; }
					final Npc npc = super.doSpawn(isSummonSpawn);
					if (npc != null) { _sessionNpcs.add(npc); }
					return npc;
				}
			}

			@Override
			public void respawnNpc(Npc oldNpc)
			{
				synchronized (DimensionalRift.LIFECYCLE_LOCK)
				{
					if ((_owner == null) || (_spawnOwner != _owner) || !_sessionNpcs.contains(oldNpc)) { return; }
					super.respawnNpc(oldNpc);
				}
			}

			@Override
			public void stopRespawn()
			{
				synchronized (DimensionalRift.LIFECYCLE_LOCK)
				{
					super.stopRespawn();
					for (Npc npc : _sessionNpcs) { RespawnTaskManager.getInstance().cancel(npc); }
					_sessionNpcs.clear();
					_spawnOwner = null;
				}
			}

			@Override
			public void decreaseCount(Npc oldNpc)
			{
				// Queue publication must not race past the room's cancellation pass.
				synchronized (DimensionalRift.LIFECYCLE_LOCK) { super.decreaseCount(oldNpc); }
			}
		};
	}
	
	public void spawn()
	{
		for (Spawn spawn : _roomSpawns)
		{
			spawn.doSpawn(false);
			spawn.startRespawn();
		}
	}
	
	public DimensionalRiftRoom unspawn()
	{
		for (Spawn spawn : _roomSpawns)
		{
			spawn.stopRespawn();
			if (spawn.getLastSpawn() != null)
			{
				spawn.getLastSpawn().deleteMe();
			}
		}
		
		return this;
	}
	
	/**
	 * Returns if party is inside the room.
	 * @return {@code true} if there is a party inside, {@code false} otherwise
	 */
	public boolean isPartyInside()
	{
		return _owner != null;
	}

	public boolean tryAcquire(Object owner)
	{
		synchronized (DimensionalRift.LIFECYCLE_LOCK)
		{
		if ((owner == null) || (_owner != null)) { return false; }
		_owner = owner;
		return true;
		}
	}

	public boolean isOwnedBy(Object owner)
	{
		return (owner != null) && (_owner == owner);
	}

	/** Unspawn and release are indivisible; an old session cannot clear a reused room. */
	public boolean release(Object owner)
	{
		synchronized (DimensionalRift.LIFECYCLE_LOCK)
		{
		if (!isOwnedBy(owner)) { return false; }
		unspawn();
		_owner = null;
		return true;
		}
	}
}

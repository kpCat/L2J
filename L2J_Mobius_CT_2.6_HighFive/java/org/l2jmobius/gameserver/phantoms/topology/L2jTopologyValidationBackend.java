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
package org.l2jmobius.gameserver.phantoms.topology;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.model.actor.instance.Door;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.spawns.Spawn;

/**
 * Read-only adapter over already loaded High Five factual data.
 */
public final class L2jTopologyValidationBackend implements PhantomTopologyValidationBackend
{
	@Override
	public int mapRegionLocId(int x, int y)
	{
		return MapRegionData.getInstance().getMapRegionLocId(x, y);
	}

	@Override
	public Optional<NpcFact> npc(int npcId)
	{
		final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
		return template == null ? Optional.empty() : Optional.of(new NpcFact(npcId, template.getType(), template.isType("Monster")));
	}

	@Override
	public List<SpawnFact> spawns(int npcId, int maximumResults)
	{
		if (maximumResults < 1)
		{
			return List.of();
		}
		// SpawnTable is populated by the existing datapack loader. Production
		// topology startup is its first consumer when Phantom World is enabled.
		SpawnData.getInstance();
		final ArrayList<SpawnFact> result = new ArrayList<>();
		final ArrayList<Spawn> spawns = new ArrayList<>(SpawnTable.getInstance().getSpawns(npcId));
		spawns.sort(Comparator.comparingInt(Spawn::getInstanceId).thenComparingInt(Spawn::getX).thenComparingInt(Spawn::getY).thenComparingInt(Spawn::getZ));
		for (Spawn spawn : spawns)
		{
			if (result.size() >= maximumResults)
			{
				break;
			}
			result.add(new SpawnFact(npcId, new PhantomTopologyPoint(spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getInstanceId())));
		}
		return List.copyOf(result);
	}

	@Override
	public Optional<DoorFact> door(int doorId)
	{
		final Door door = DoorData.getInstance().getDoor(doorId);
		if (door == null)
		{
			return Optional.empty();
		}
		final ArrayList<PhantomTopologyPoint> vertices = new ArrayList<>(4);
		for (int index = 0; index < 4; index++)
		{
			vertices.add(new PhantomTopologyPoint(door.getX(index), door.getY(index), door.getZMin(), door.getInstanceId()));
		}
		return Optional.of(new DoorFact(doorId, door.getInstanceId(), door.getZMin(), door.getZMax(), vertices));
	}

	@Override
	public DoorState doorState(int doorId)
	{
		final Door door = DoorData.getInstance().getDoor(doorId);
		if (door == null)
		{
			return DoorState.MISSING;
		}
		if (door.isDead())
		{
			return DoorState.DEAD;
		}
		return door.isOpen() ? DoorState.OPEN : DoorState.CLOSED;
	}

	@Override
	public boolean sourceExists(String relativeDatapackPath)
	{
		if ((relativeDatapackPath == null) || relativeDatapackPath.isBlank() || relativeDatapackPath.contains("..") || relativeDatapackPath.startsWith("/") || relativeDatapackPath.startsWith("\\"))
		{
			return false;
		}
		final File root = ServerConfig.DATAPACK_ROOT;
		final File source = new File(root, relativeDatapackPath.replace('/', File.separatorChar));
		try
		{
			return source.isFile() && source.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator);
		}
		catch (Exception exception)
		{
			return false;
		}
	}
}

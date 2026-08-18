/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.Comparator;
import java.util.Optional;

import org.l2jmobius.gameserver.data.SpawnTable;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;

@FunctionalInterface
public interface PhantomRaidEntryNpcLocator
{
	Optional<PhantomNavigationPoint> locate(int exactNpcId);

	static PhantomRaidEntryNpcLocator spawnTable()
	{
		return exactNpcId ->
		{
			if (exactNpcId <= 0)
			{
				return Optional.empty();
			}
			return SpawnTable.getInstance().getSpawns(exactNpcId).stream()
				.filter(spawn -> spawn.getId() == exactNpcId)
				.sorted(Comparator.comparingInt(Spawn::getX).thenComparingInt(Spawn::getY).thenComparingInt(Spawn::getZ))
				.findFirst()
				.map(spawn -> new PhantomNavigationPoint(spawn.getX(), spawn.getY(), spawn.getZ(), 0));
		};
	}
}
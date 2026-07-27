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

import java.util.List;
import java.util.Optional;

/**
 * Narrow factual server-data boundary. No mutable server object crosses it.
 */
public interface PhantomTopologyValidationBackend
{
	enum DoorState
	{
		MISSING,
		DEAD,
		OPEN,
		CLOSED
	}

	record NpcFact(int npcId, String type, boolean monster)
	{
	}

	record SpawnFact(int npcId, PhantomTopologyPoint point)
	{
	}

	record DoorFact(int doorId, int instanceId, int zMin, int zMax, List<PhantomTopologyPoint> vertices)
	{
		public DoorFact
		{
			vertices = List.copyOf(vertices);
		}

		public double distance2D(PhantomTopologyPoint point)
		{
			if ((point == null) || (point.instanceId() != instanceId))
			{
				return Double.POSITIVE_INFINITY;
			}
			double result = Double.POSITIVE_INFINITY;
			for (int index = 0; index < vertices.size(); index++)
			{
				final PhantomTopologyPoint first = vertices.get(index);
				final PhantomTopologyPoint second = vertices.get((index + 1) % vertices.size());
				result = Math.min(result, segmentDistance(point.x(), point.y(), first.x(), first.y(), second.x(), second.y()));
			}
			return result;
		}

		private static double segmentDistance(int px, int py, int ax, int ay, int bx, int by)
		{
			final long dx = (long) bx - ax;
			final long dy = (long) by - ay;
			if ((dx == 0) && (dy == 0))
			{
				return Math.hypot((long) px - ax, (long) py - ay);
			}
			final double projection = Math.max(0, Math.min(1, ((((long) px - ax) * dx) + (((long) py - ay) * dy)) / (double) ((dx * dx) + (dy * dy))));
			return Math.hypot(px - (ax + (projection * dx)), py - (ay + (projection * dy)));
		}
	}

	int mapRegionLocId(int x, int y);

	Optional<NpcFact> npc(int npcId);

	List<SpawnFact> spawns(int npcId, int maximumResults);

	Optional<DoorFact> door(int doorId);

	DoorState doorState(int doorId);

	boolean sourceExists(String relativeDatapackPath);
}

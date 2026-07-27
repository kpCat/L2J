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
import java.util.Objects;
import java.util.Set;

public record PhantomTopologyEdge(String id, String fromNodeId, String toNodeId, PhantomTopologyEdgeMode mode, boolean bidirectional, int baseCost, long baseTravelMillis, boolean backgroundEligible, Set<PhantomPerceptionChannel> perceptionChannels, Integer doorId, String fromAnchorId, String toAnchorId, List<String> sourceRefs)
{
	public PhantomTopologyEdge
	{
		id = PhantomTopologyPolicy.requireId(id, "edge id");
		fromNodeId = PhantomTopologyPolicy.requireId(fromNodeId, "edge from node id");
		toNodeId = PhantomTopologyPolicy.requireId(toNodeId, "edge to node id");
		if (fromNodeId.equals(toNodeId))
		{
			throw new IllegalArgumentException("Topology self-edge is not allowed.");
		}
		Objects.requireNonNull(mode, "mode");
		if ((baseCost < 1) || (baseCost > 1_000_000) || (baseTravelMillis < 0) || (baseTravelMillis > 86_400_000))
		{
			throw new IllegalArgumentException("Invalid topology edge cost or travel time.");
		}
		perceptionChannels = Set.copyOf(perceptionChannels);
		if (perceptionChannels.size() > PhantomPerceptionChannel.values().length)
		{
			throw new IllegalArgumentException("Invalid topology perception channels.");
		}
		if ((doorId != null) && (doorId <= 0))
		{
			throw new IllegalArgumentException("Topology edge doorId must be positive.");
		}
		if (fromAnchorId != null)
		{
			fromAnchorId = PhantomTopologyPolicy.requireId(fromAnchorId, "edge from anchor id");
		}
		if (toAnchorId != null)
		{
			toAnchorId = PhantomTopologyPolicy.requireId(toAnchorId, "edge to anchor id");
		}
		sourceRefs = PhantomTopologyPolicy.immutableSources(sourceRefs);
	}

	public String otherNode(String nodeId)
	{
		if (fromNodeId.equals(nodeId))
		{
			return toNodeId;
		}
		if (bidirectional && toNodeId.equals(nodeId))
		{
			return fromNodeId;
		}
		return null;
	}
}

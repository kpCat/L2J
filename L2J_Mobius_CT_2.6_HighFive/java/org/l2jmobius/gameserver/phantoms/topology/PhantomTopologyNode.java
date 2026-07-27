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

public record PhantomTopologyNode(String id, PhantomTopologyNodeKind kind, int instanceId, PhantomTopologyArea area, String parentId, List<String> tags, List<String> sourceRefs)
{
	public PhantomTopologyNode
	{
		id = PhantomTopologyPolicy.requireId(id, "node id");
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(area, "area");
		if ((instanceId < 0) || (area.instanceId() != instanceId))
		{
			throw new IllegalArgumentException("Topology node instance does not match its area.");
		}
		if (parentId != null)
		{
			parentId = PhantomTopologyPolicy.requireId(parentId, "parent id");
			if (parentId.equals(id))
			{
				throw new IllegalArgumentException("Topology node cannot parent itself.");
			}
		}
		tags = PhantomTopologyPolicy.immutableTags(tags);
		sourceRefs = PhantomTopologyPolicy.immutableSources(sourceRefs);
	}
}

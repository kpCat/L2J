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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Fixed Goal 010 structural limits. These are intentionally not configuration.
 */
public record PhantomTopologyPolicy(int maximumFiles, int maximumNodes, int maximumAnchors, int maximumEdges, int maximumHierarchyDepth, int maximumTags, int maximumSourceReferences, int maximumVertices, int maximumRegisteredProfiles, int maximumConcurrentEvents, int maximumRecipientsPerEvent, int maximumNeighborNodesPerEvent, int maximumEventRadius, int maximumReturnedNodes, int maximumReturnedEdges, int maximumGraphNodes, int spatialCellSize, int maximumSpatialReferencesPerNode, int maximumOversizedSpatialNodes, long defaultLocalChatTtlMillis, long defaultCombatTtlMillis, long defaultTargetabilityTtlMillis)
{
	private static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]{0,95}$");
	private static final Pattern TAG_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");

	public PhantomTopologyPolicy
	{
		if ((maximumFiles < 1) || (maximumFiles > 64) || (maximumNodes < 1) || (maximumNodes > 100_000) || (maximumAnchors < 1) || (maximumAnchors > 100_000) || (maximumEdges < 1) || (maximumEdges > 200_000))
		{
			throw new IllegalArgumentException("Invalid topology entity limits.");
		}
		if ((maximumHierarchyDepth < 1) || (maximumHierarchyDepth > 8) || (maximumTags < 1) || (maximumTags > 16) || (maximumSourceReferences < 1) || (maximumSourceReferences > 8) || (maximumVertices < 3) || (maximumVertices > 32))
		{
			throw new IllegalArgumentException("Invalid topology value limits.");
		}
		if ((maximumRegisteredProfiles < 1) || (maximumRegisteredProfiles > 10_000) || (maximumConcurrentEvents < 1) || (maximumConcurrentEvents > 32) || (maximumRecipientsPerEvent < 1) || (maximumRecipientsPerEvent > 1024) || (maximumNeighborNodesPerEvent < 1) || (maximumNeighborNodesPerEvent > 64))
		{
			throw new IllegalArgumentException("Invalid topology perception limits.");
		}
		if ((maximumEventRadius < 1) || (maximumEventRadius > 100_000) || (maximumReturnedNodes < 1) || (maximumReturnedNodes > 64) || (maximumReturnedEdges < 1) || (maximumReturnedEdges > 1024) || (maximumGraphNodes < 1) || (maximumGraphNodes > 256))
		{
			throw new IllegalArgumentException("Invalid topology query limits.");
		}
		if ((spatialCellSize < 256) || (maximumSpatialReferencesPerNode < 1) || (maximumOversizedSpatialNodes < 1))
		{
			throw new IllegalArgumentException("Invalid topology spatial limits.");
		}
		if ((defaultLocalChatTtlMillis < 1) || (defaultCombatTtlMillis < 1) || (defaultTargetabilityTtlMillis < 1))
		{
			throw new IllegalArgumentException("Invalid topology signal TTL.");
		}
	}

	public static PhantomTopologyPolicy productionDefaults()
	{
		return new PhantomTopologyPolicy(64, 100_000, 100_000, 200_000, 8, 16, 8, 32, 10_000, 32, 1024, 64, 100_000, 64, 1024, 256, 4096, 64, 64, 5000, 3000, 2000);
	}

	public PhantomTopologyPolicy withMaximumRegisteredProfiles(int maximumProfiles)
	{
		return new PhantomTopologyPolicy(maximumFiles, maximumNodes, maximumAnchors, maximumEdges, maximumHierarchyDepth, maximumTags, maximumSourceReferences, maximumVertices, maximumProfiles, maximumConcurrentEvents, maximumRecipientsPerEvent, maximumNeighborNodesPerEvent, maximumEventRadius, maximumReturnedNodes, maximumReturnedEdges, maximumGraphNodes, spatialCellSize, maximumSpatialReferencesPerNode, maximumOversizedSpatialNodes, defaultLocalChatTtlMillis, defaultCombatTtlMillis, defaultTargetabilityTtlMillis);
	}

	public static String requireId(String value, String field)
	{
		if ((value == null) || !ID_PATTERN.matcher(value).matches())
		{
			throw new IllegalArgumentException("Invalid topology " + field + ".");
		}
		return value;
	}

	public static List<String> immutableTags(Collection<String> values)
	{
		Objects.requireNonNull(values, "values");
		if (values.size() > 16)
		{
			throw new IllegalArgumentException("Topology tags exceed 16.");
		}
		final List<String> result = values.stream().peek(value ->
		{
			if ((value == null) || !TAG_PATTERN.matcher(value).matches())
			{
				throw new IllegalArgumentException("Invalid topology tag.");
			}
		}).distinct().sorted().toList();
		if (result.size() != values.size())
		{
			throw new IllegalArgumentException("Duplicate topology tag.");
		}
		return result;
	}

	public static List<String> immutableSources(Collection<String> values)
	{
		Objects.requireNonNull(values, "values");
		if (values.size() > 8)
		{
			throw new IllegalArgumentException("Topology source references exceed 8.");
		}
		final List<String> result = values.stream().peek(value ->
		{
			if ((value == null) || value.isBlank() || (value.length() > 512) || value.contains("..") || value.startsWith("/") || value.startsWith("\\"))
			{
				throw new IllegalArgumentException("Invalid topology source reference.");
			}
		}).distinct().sorted().toList();
		if (result.size() != values.size())
		{
			throw new IllegalArgumentException("Duplicate topology source reference.");
		}
		return result;
	}
}

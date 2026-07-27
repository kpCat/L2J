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
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.util.regex.Pattern;

/**
 * Compile-time policy bounds. Goal 011 intentionally adds no configuration keys.
 */
public record PhantomGameKnowledgePolicy(int maximumSourceFiles, int maximumItems, int maximumNpcTemplates, int maximumDropSpoilFacts, int maximumSpawnFacts, int maximumRecipes, int maximumRecipeIngredients, int maximumManorFacts, int maximumClassCapabilityFacts, int maximumContentEntries, int maximumRequirementsPerContent, int maximumEvidenceReferences, int maximumEvidenceSkills, int maximumQueryPageSize, int maximumTargetLevelWidth, int maximumTopologyNodeResults, int maximumSpawnSamples, int maximumSourceLength, int maximumCuratedKeyLength)
{
	private static final Pattern KEY_PATTERN = Pattern.compile("[a-z][a-z0-9_.-]{0,95}");

	public PhantomGameKnowledgePolicy
	{
		if ((maximumSourceFiles < 1) || (maximumItems < 1) || (maximumNpcTemplates < 1) || (maximumDropSpoilFacts < 1) || (maximumSpawnFacts < 1) || (maximumRecipes < 1) || (maximumRecipeIngredients < 1) || (maximumManorFacts < 1) || (maximumClassCapabilityFacts < 1) || (maximumContentEntries < 1) || (maximumRequirementsPerContent < 1) || (maximumEvidenceReferences < 1) || (maximumEvidenceSkills < 1) || (maximumQueryPageSize < 1) || (maximumTargetLevelWidth < 0) || (maximumTopologyNodeResults < 1) || (maximumSpawnSamples < 1) || (maximumSourceLength < 1) || (maximumCuratedKeyLength < 1))
		{
			throw new IllegalArgumentException("Game Knowledge policy bounds must be positive.");
		}
	}

	public static PhantomGameKnowledgePolicy productionDefaults()
	{
		return new PhantomGameKnowledgePolicy(4096, 100000, 100000, 2000000, 1000000, 100000, 1000000, 100000, 50000, 4096, 64, 16, 32, 256, 100, 64, 256, 512, 96);
	}

	public String requireKey(String value, String label)
	{
		if ((value == null) || (value.length() > maximumCuratedKeyLength) || !KEY_PATTERN.matcher(value).matches())
		{
			throw new PhantomGameKnowledgeValidationException("schema", "Invalid " + label + ".");
		}
		return value;
	}

	public String requireSource(String value)
	{
		if ((value == null) || value.isBlank() || (value.length() > maximumSourceLength) || value.contains("..") || value.startsWith("/") || value.startsWith("\\"))
		{
			throw new PhantomGameKnowledgeValidationException("evidence", "Invalid source evidence path.");
		}
		return value.replace('\\', '/');
	}
}

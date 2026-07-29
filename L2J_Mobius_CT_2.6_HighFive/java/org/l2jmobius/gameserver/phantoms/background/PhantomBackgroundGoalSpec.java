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
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;

/**
 * Exact persisted contract for Goal 015. It never chooses a target or anchor.
 */
public record PhantomBackgroundGoalSpec(int npcId, String anchorId, int shotItemId, int shotsPerEncounter, int summonNpcId, int summonResourceItemId, int summonResourcesPerEncounter)
{
	public static final String GOAL_TYPE = "farm.background";
	public static final String SOURCE_NAMESPACE = "background.farm";
	public static final String CANDIDATE_KEY = "candidate.background.farm";
	public static final String TRAVEL_ACTION = "background.travel";
	public static final String FARM_ACTION = "background.farm";
	public static final String RECOVER_ACTION = "background.recover";
	public static final String ANCHOR_NAMESPACE = "topology.anchor";
	public static final String NPC_NAMESPACE = "npc";
	public static final String SHOT_ITEM = "shot.item_id";
	public static final String SHOT_COUNT = "shot.count";
	public static final String SUMMON_NPC = "summon.npc_id";
	public static final String SUMMON_RESOURCE_ITEM = "summon.resource_item_id";
	public static final String SUMMON_RESOURCE_COUNT = "summon.resource.count";
	private static final List<String> SUPPORTED_CONSTRAINTS = List.of(SHOT_ITEM, SHOT_COUNT, SUMMON_NPC, SUMMON_RESOURCE_ITEM, SUMMON_RESOURCE_COUNT);

	public PhantomBackgroundGoalSpec
	{
		if ((npcId <= 0) || (anchorId == null) || anchorId.isBlank() || (shotItemId < 0) || (shotsPerEncounter < 0) || (summonNpcId < 0) || (summonResourceItemId < 0) || (summonResourcesPerEncounter < 0))
		{
			throw new IllegalArgumentException("Invalid persisted background farm goal.");
		}
		if ((shotItemId == 0) != (shotsPerEncounter == 0))
		{
			throw new IllegalArgumentException("Incomplete persisted shot constraint.");
		}
		if ((summonResourceItemId == 0) != (summonResourcesPerEncounter == 0))
		{
			throw new IllegalArgumentException("Incomplete persisted summon resource constraint.");
		}
	}

	public static PhantomBackgroundGoalSpec parse(PhantomGoal goal)
	{
		if ((goal == null) || !GOAL_TYPE.equals(goal.goalType()) || (goal.status() != PhantomGoalStatus.ACTIVE))
		{
			throw new IllegalArgumentException("Background farming requires an ACTIVE farm.background goal.");
		}
		final List<PhantomDomainRef> sources = goal.validSources().stream().filter(source -> SOURCE_NAMESPACE.equals(source.namespace())).toList();
		if (sources.size() != 1)
		{
			throw new IllegalArgumentException("Background farming requires exactly one background.farm source.");
		}
		final String sourceKey = sources.getFirst().key();
		final int separator = sourceKey.indexOf('@');
		if ((separator < 1) || (separator == (sourceKey.length() - 1)) || (sourceKey.indexOf('@', separator + 1) >= 0))
		{
			throw new IllegalArgumentException("Background farm source key must be <npcId>@<anchorId>.");
		}
		final int npcId;
		try
		{
			npcId = Integer.parseInt(sourceKey.substring(0, separator));
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Background farm source contains an invalid NPC ID.", exception);
		}
		final String anchorId = sourceKey.substring(separator + 1);
		final PhantomDomainRef selectedAnchor = goal.selectedAnchor();
		if ((selectedAnchor == null) || !ANCHOR_NAMESPACE.equals(selectedAnchor.namespace()) || !anchorId.equals(selectedAnchor.key()))
		{
			throw new IllegalArgumentException("Background farm source and selected anchor differ.");
		}
		final PhantomDomainRef target = goal.target();
		if ((target == null) || !NPC_NAMESPACE.equals(target.namespace()) || !Integer.toString(npcId).equals(target.key()))
		{
			throw new IllegalArgumentException("Background farm target must be the source NPC.");
		}
		for (String key : goal.constraints().keySet())
		{
			if (!SUPPORTED_CONSTRAINTS.contains(key))
			{
				throw new IllegalArgumentException("Unsupported background farm constraint: " + key);
			}
		}
		final Map<String, Long> constraints = goal.constraints();
		return new PhantomBackgroundGoalSpec(npcId, anchorId, positiveInt(constraints, SHOT_ITEM), positiveInt(constraints, SHOT_COUNT), positiveInt(constraints, SUMMON_NPC), positiveInt(constraints, SUMMON_RESOURCE_ITEM), positiveInt(constraints, SUMMON_RESOURCE_COUNT));
	}

	private static int positiveInt(Map<String, Long> constraints, String key)
	{
		final long value = constraints.getOrDefault(key, 0L);
		if ((value < 0) || (value > Integer.MAX_VALUE))
		{
			throw new IllegalArgumentException("Invalid numeric background constraint: " + key);
		}
		return (int) value;
	}
}

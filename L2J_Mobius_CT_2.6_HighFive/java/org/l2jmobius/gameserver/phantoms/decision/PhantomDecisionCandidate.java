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
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;

public record PhantomDecisionCandidate(String key, Set<String> supportedGoalTypes, Set<PhantomActivityState> allowedActivityStates, List<PhantomCapabilityRequirement> requirements, List<PhantomWeightedConsideration> considerations, int minimumAcceptedScore, PhantomPlanFactory planFactory)
{
	public static final int MAX_GOAL_TYPES = 16;
	public static final int MAX_REQUIREMENTS = 16;
	public static final int MAX_CONSIDERATIONS = 16;

	public PhantomDecisionCandidate
	{
		key = PhantomDecisionKey.require(key, "Candidate key");
		Objects.requireNonNull(supportedGoalTypes, "Supported goal types must not be null.");
		if (supportedGoalTypes.isEmpty() || (supportedGoalTypes.size() > MAX_GOAL_TYPES))
		{
			throw new IllegalArgumentException("Candidate must support 1..16 goal types.");
		}
		final TreeSet<String> goalTypes = new TreeSet<>();
		for (String goalType : supportedGoalTypes)
		{
			goalTypes.add(PhantomDecisionKey.require(goalType, "Supported goal type"));
		}
		supportedGoalTypes = Set.copyOf(goalTypes);
		Objects.requireNonNull(allowedActivityStates, "Allowed activity states must not be null.");
		if (allowedActivityStates.isEmpty())
		{
			throw new IllegalArgumentException("Candidate must allow at least one activity state.");
		}
		allowedActivityStates = Set.copyOf(allowedActivityStates);
		Objects.requireNonNull(requirements, "Requirements must not be null.");
		if (requirements.size() > MAX_REQUIREMENTS)
		{
			throw new IllegalArgumentException("Candidate requirements must not exceed 16.");
		}
		requirements = requirements.stream().map(requirement -> Objects.requireNonNull(requirement, "Requirement must not be null.")).sorted(Comparator.comparing(PhantomCapabilityRequirement::key)).toList();
		Objects.requireNonNull(considerations, "Considerations must not be null.");
		if (considerations.isEmpty() || (considerations.size() > MAX_CONSIDERATIONS))
		{
			throw new IllegalArgumentException("Candidate must contain 1..16 considerations.");
		}
		considerations = considerations.stream().map(consideration -> Objects.requireNonNull(consideration, "Consideration must not be null.")).sorted(Comparator.comparing(PhantomWeightedConsideration::key)).toList();
		if ((minimumAcceptedScore < 0) || (minimumAcceptedScore > 1000))
		{
			throw new IllegalArgumentException("Minimum accepted score must be between 0 and 1000.");
		}
		Objects.requireNonNull(planFactory, "Plan factory must not be null.");
	}
}

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
import java.util.Objects;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.DeathPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.ExperienceTable;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.LevelForExperience;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.RewardPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.Target;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;

/**
 * Read-only authority boundary for canonical runtime facts and immutable loader
 * generations. Tests may replace it, but production mutations remain owned by
 * {@link PhantomBackgroundTransaction}.
 */
public interface PhantomBackgroundAuthority
{
	Hashes hashes();

	PhantomBackgroundState capture(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous);

	default PhantomBackgroundState captureAcquisition(long profileId, Player player, PhantomGoal goal, PhantomBackgroundState previous, int targetItemId)
	{
		throw new UnsupportedOperationException("Acquisition background capture is unavailable.");
	}

	boolean matchesRuntime(Player player, PhantomBackgroundState state);

	FarmInput farmInput(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal);

	default FarmInput acquisitionInput(PhantomBackgroundState state, Source source)
	{
		throw new UnsupportedOperationException("Acquisition background authority is unavailable.");
	}

	default FarmInput acquisitionInput(PhantomBackgroundState state, Source source, Map<Integer, Integer> learnedSkills)
	{
		return acquisitionInput(state, source);
	}

	TravelAdvance advanceTravel(PhantomBackgroundState state, PhantomBackgroundGoalSpec goal, long elapsedBudgetMillis);

	default TravelAdvance advanceAcquisitionTravel(PhantomBackgroundState state, Source source, long elapsedBudgetMillis)
	{
		throw new UnsupportedOperationException("Acquisition background travel is unavailable.");
	}

	List<AutoGetSkill> autoGetSkills(PhantomBackgroundState.Identity identity, int level);

	record FarmInput(Target target, RewardPolicy rewardPolicy, DeathPolicy deathPolicy, ExperienceTable experienceTable, LevelForExperience levelForExperience, String topologyNodeId, int spawnCapacity)
	{
		public FarmInput
		{
			Objects.requireNonNull(target, "target");
			Objects.requireNonNull(rewardPolicy, "rewardPolicy");
			Objects.requireNonNull(deathPolicy, "deathPolicy");
			Objects.requireNonNull(experienceTable, "experienceTable");
			Objects.requireNonNull(levelForExperience, "levelForExperience");
			if ((topologyNodeId == null) || topologyNodeId.isBlank() || (spawnCapacity < 1))
			{
				throw new IllegalArgumentException("Invalid background farm authority input.");
			}
		}
	}

	record TravelAdvance(Status status, Position position, Clock clock, String edgeId)
	{
		public TravelAdvance
		{
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(position, "position");
			Objects.requireNonNull(clock, "clock");
			edgeId = Objects.requireNonNullElse(edgeId, "");
		}

		public boolean mutated()
		{
			return status == Status.PARTIAL || status == Status.ARRIVED;
		}

		public enum Status
		{
			AT_DESTINATION,
			PARTIAL,
			ARRIVED,
			NO_ROUTE,
			EDGE_NOT_ELIGIBLE,
			EDGE_CLOSED,
			ANCHOR_MISMATCH
		}
	}
}

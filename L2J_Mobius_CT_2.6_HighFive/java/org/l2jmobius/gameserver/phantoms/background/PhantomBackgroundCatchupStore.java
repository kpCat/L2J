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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;

/** Optimistically-versioned persistence boundary for background.catchup. */
public final class PhantomBackgroundCatchupStore
{
	private final PhantomProfileRepository _profiles;
	private final PhantomGoalStateStore _goals;
	private final PhantomBackgroundCatchupStateCodec _codec;

	public PhantomBackgroundCatchupStore(PhantomProfileRepository profiles, PhantomGoalStateStore goals)
	{
		this(profiles, goals, new PhantomBackgroundCatchupStateCodec());
	}

	PhantomBackgroundCatchupStore(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomBackgroundCatchupStateCodec codec)
	{
		_profiles = Objects.requireNonNull(profiles, "profiles");
		_goals = Objects.requireNonNull(goals, "goals");
		_codec = Objects.requireNonNull(codec, "codec");
	}

	public Optional<Snapshot> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE).map(this::decode);
	}

	public Snapshot claim(long profileId, PhantomBackgroundCatchupState initial)
	{
		final Snapshot existing = load(profileId).orElse(null);
		if (existing != null)
		{
			if (!existing.state().owns(initial.requestId()))
			{
				throw new ConcurrentModificationException("A different Background catch-up request already owns the profile.");
			}
			return existing;
		}
		try
		{
			return decode(_profiles.insertComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE, PhantomBackgroundCatchupState.SCHEMA_VERSION, _codec.encode(initial)));
		}
		catch (RuntimeException exception)
		{
			final Snapshot raced = load(profileId).orElse(null);
			if ((raced != null) && raced.state().owns(initial.requestId()))
			{
				return raced;
			}
			throw exception;
		}
	}

	public Snapshot replace(long profileId, Snapshot expected, PhantomBackgroundCatchupState replacement)
	{
		requireSameRequest(expected.state(), replacement);
		return decode(_profiles.updateComponent(profileId, PhantomBackgroundCatchupState.COMPONENT_TYPE, expected.rowVersion(), PhantomBackgroundCatchupState.SCHEMA_VERSION, _codec.encode(replacement)));
	}
	public PlannedSnapshot persistInitialPlan(long profileId, Snapshot expected, PhantomBackgroundCatchupState planned, PhantomGoal goal)
	{
		if (_goals.load(profileId).isPresent())
		{
			throw new ConcurrentModificationException("Initial Background catch-up goal is not absent.");
		}
		return mutatePlan(profileId, expected, planned, goal, -1);
	}

	public PlannedSnapshot replacePlan(long profileId, Snapshot expected, PhantomBackgroundCatchupState planned, StoredGoal expectedGoal, PhantomGoal replacementGoal)
	{
		if ((expectedGoal.goal().goalId() != replacementGoal.goalId()) || (replacementGoal.revision() != Math.addExact(expectedGoal.goal().revision(), 1)))
		{
			throw new IllegalArgumentException("Catch-up replan must preserve goal identity and advance its revision once.");
		}
		return mutatePlan(profileId, expected, planned, replacementGoal, expectedGoal.rowVersion());
	}

	private PlannedSnapshot mutatePlan(long profileId, Snapshot expected, PhantomBackgroundCatchupState planned, PhantomGoal goal, long expectedGoalRowVersion)
	{
		requireSameRequest(expected.state(), planned);
		if ((planned.goalId() != goal.goalId()) || (planned.goalRevision() != goal.revision()))
		{
			throw new IllegalArgumentException("Catch-up state and goal plan identities differ.");
		}
		final List<PhantomProfileComponent> components = _profiles.mutateComponentsAtomically(profileId, List.of(
			new ComponentMutation(PhantomBackgroundCatchupState.COMPONENT_TYPE, expected.rowVersion(), PhantomBackgroundCatchupState.SCHEMA_VERSION, _codec.encode(planned)),
			_goals.componentMutation(expectedGoalRowVersion, goal)));
		if ((components.size() != 2) || !PhantomBackgroundCatchupState.COMPONENT_TYPE.equals(components.get(0).componentType()) || !PhantomGoalStateStore.COMPONENT_TYPE.equals(components.get(1).componentType()))
		{
			throw new IllegalStateException("Atomic catch-up plan returned an unexpected component set.");
		}
		return new PlannedSnapshot(decode(components.get(0)), _goals.decodeComponent(components.get(1)));
	}

	public ComponentMutation componentMutation(Snapshot expected, PhantomBackgroundCatchupState replacement)
	{
		requireSameRequest(expected.state(), replacement);
		return new ComponentMutation(PhantomBackgroundCatchupState.COMPONENT_TYPE, expected.rowVersion(), PhantomBackgroundCatchupState.SCHEMA_VERSION, _codec.encode(replacement));
	}

	private Snapshot decode(PhantomProfileComponent component)
	{
		if (!PhantomBackgroundCatchupState.COMPONENT_TYPE.equals(component.componentType()) || (component.componentSchemaVersion() != PhantomBackgroundCatchupState.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown background.catchup component schema.");
		}
		return new Snapshot(_codec.decode(component.payload()), component.rowVersion());
	}

	private static void requireSameRequest(PhantomBackgroundCatchupState expected, PhantomBackgroundCatchupState replacement)
	{
		if (!expected.requestId().equals(replacement.requestId()) || (expected.generation() != replacement.generation()) || (replacement.cursorEpochMinute() < expected.cursorEpochMinute()))
		{
			throw new IllegalArgumentException("Catch-up replacement changed ownership or regressed its cursor.");
		}
	}

	public record Snapshot(PhantomBackgroundCatchupState state, long rowVersion)
	{
		public Snapshot
		{
			Objects.requireNonNull(state, "state");
			if (rowVersion < 0)
			{
				throw new IllegalArgumentException("Catch-up component row version must be non-negative.");
			}
		}
	}

	public record PlannedSnapshot(Snapshot catchup, StoredGoal goal)
	{
		public PlannedSnapshot
		{
			Objects.requireNonNull(catchup, "catchup");
			Objects.requireNonNull(goal, "goal");
		}
	}
}
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
package org.l2jmobius.gameserver.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeSnapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.CandidateEvaluation;

/**
 * Fixed-capacity structured decision trace for one explicitly selected profile.
 */
public final class PhantomSelectedDecisionTrace implements PhantomDecisionEngine.DecisionObserver
{
	public static final int MAX_CAPACITY = 64;
	private final boolean _enabled;
	private final int _capacity;
	private final DecisionView[] _entries;
	private volatile long _selectedProfileId;
	private long _recorded;
	private long _dropped;
	private int _start;
	private int _size;
	private DecisionView _current;

	public PhantomSelectedDecisionTrace(boolean enabled, int capacity)
	{
		if (enabled && ((capacity <= 0) || (capacity > MAX_CAPACITY)))
		{
			throw new IllegalArgumentException("Enabled selected trace requires capacity between 1 and 64.");
		}
		_enabled = enabled;
		_capacity = enabled ? capacity : 0;
		_entries = enabled ? new DecisionView[capacity] : null;
	}

	@Override
	public boolean interested(long profileId)
	{
		return isSelected(profileId);
	}

	public boolean isSelected(long profileId)
	{
		return _enabled && (profileId > 0) && (_selectedProfileId == profileId);
	}

	@Override
	public void onDecision(PhantomActivityState activityState, RuntimeSnapshot snapshot)
	{
		observe(activityState, snapshot);
	}

	public synchronized SelectionStatus select(long profileId, RuntimeSnapshot current)
	{
		if (!_enabled)
		{
			return SelectionStatus.DISABLED;
		}
		if ((profileId <= 0) || (current == null) || (current.profileId() != profileId))
		{
			return SelectionStatus.NOT_ATTACHED;
		}
		if (_selectedProfileId != profileId)
		{
			clearStorage();
		}
		_selectedProfileId = profileId;
		_current = DecisionView.from(null, current);
		return SelectionStatus.SELECTED;
	}

	public synchronized void clear()
	{
		_selectedProfileId = 0;
		clearStorage();
	}

	public synchronized void observe(PhantomActivityState activityState, RuntimeSnapshot snapshot)
	{
		Objects.requireNonNull(activityState, "Activity state must not be null.");
		Objects.requireNonNull(snapshot, "Runtime snapshot must not be null.");
		if (!_enabled || (_selectedProfileId == 0) || (_selectedProfileId != snapshot.profileId()))
		{
			return;
		}
		final DecisionView entry = DecisionView.from(activityState, snapshot);
		_current = entry;
		_recorded++;
		if (_size < _capacity)
		{
			_entries[(_start + _size) % _capacity] = entry;
			_size++;
		}
		else
		{
			_entries[_start] = entry;
			_start = (_start + 1) % _capacity;
			_dropped++;
		}
	}

	public synchronized Snapshot snapshot()
	{
		if (!_enabled)
		{
			return Snapshot.disabled();
		}
		final List<DecisionView> history = new ArrayList<>(_size);
		for (int index = 0; index < _size; index++)
		{
			history.add(_entries[(_start + index) % _capacity]);
		}
		return new Snapshot(true, _capacity, _selectedProfileId, _recorded, _dropped, _current, List.copyOf(history));
	}

	private void clearStorage()
	{
		if (_entries != null)
		{
			java.util.Arrays.fill(_entries, null);
		}
		_recorded = 0;
		_dropped = 0;
		_start = 0;
		_size = 0;
		_current = null;
	}

	public enum SelectionStatus
	{
		SELECTED,
		NOT_ATTACHED,
		DISABLED
	}

	public record Snapshot(boolean enabled, int capacity, long selectedProfileId, long recorded, long dropped, DecisionView current, List<DecisionView> history)
	{
		public Snapshot
		{
			history = List.copyOf(history);
		}

		public static Snapshot disabled()
		{
			return new Snapshot(false, 0, 0, 0, 0, null, List.of());
		}
	}

	public record DecisionView(PhantomActivityState activityState, long profileId, long goalId, String goalType, PhantomGoalStatus goalStatus, PhantomDecisionEngine.RuntimeState runtimeState, long decisionSequence, String candidateKey, int score, long planId, int step, int attempt, PhantomStepResult.Type lastResult, String reasonKey, List<CandidateEvaluation> topCandidates)
	{
		public DecisionView
		{
			topCandidates = List.copyOf(topCandidates);
		}

		private static DecisionView from(PhantomActivityState activityState, RuntimeSnapshot snapshot)
		{
			return new DecisionView(activityState, snapshot.profileId(), snapshot.goalId(), snapshot.goalType(), snapshot.goalStatus(), snapshot.runtimeState(), snapshot.decisionSequence(), snapshot.selectedCandidateKey(), snapshot.selectedScore(), snapshot.planId(), snapshot.currentStep(), snapshot.attempt(), snapshot.lastResult(), snapshot.reasonKey(), snapshot.topCandidateEvaluations());
		}
	}
}

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
import java.util.function.LongSupplier;

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
	public static final long SLOW_THRESHOLD_MILLIS = 5_000;
	public static final long STUCK_THRESHOLD_MILLIS = 30_000;
	private final boolean _enabled;
	private final int _capacity;
	private final DecisionView[] _entries;
	private final long _slowThresholdMillis;
	private final long _stuckThresholdMillis;
	private final LongSupplier _nanoClock;
	private volatile long _selectedProfileId;
	private long _recorded;
	private long _dropped;
	private int _start;
	private int _size;
	private DecisionView _current;
	private ProgressFingerprint _progressFingerprint;
	private long _progressBaselineNanos;
	private long _lastNowNanos;
	private boolean _hasProgressBaseline;

	public PhantomSelectedDecisionTrace(boolean enabled, int capacity)
	{
		this(enabled, capacity, SLOW_THRESHOLD_MILLIS, STUCK_THRESHOLD_MILLIS, System::nanoTime);
	}

	public PhantomSelectedDecisionTrace(boolean enabled, int capacity, long slowThresholdMillis, long stuckThresholdMillis, LongSupplier nanoClock)
	{
		if (enabled && ((capacity <= 0) || (capacity > MAX_CAPACITY)))
		{
			throw new IllegalArgumentException("Enabled selected trace requires capacity between 1 and 64.");
		}
		if ((slowThresholdMillis <= 0) || (slowThresholdMillis >= stuckThresholdMillis))
		{
			throw new IllegalArgumentException("Selected trace thresholds require 0 < slow < stuck.");
		}
		_enabled = enabled;
		_capacity = enabled ? capacity : 0;
		_entries = enabled ? new DecisionView[capacity] : null;
		_slowThresholdMillis = slowThresholdMillis;
		_stuckThresholdMillis = stuckThresholdMillis;
		_nanoClock = Objects.requireNonNull(nanoClock, "Monotonic clock must not be null.");
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

	public long selectedProfileId()
	{
		return _selectedProfileId;
	}

	@Override
	public void onDecision(PhantomActivityState activityState, RuntimeSnapshot snapshot)
	{
		observe(activityState, snapshot, _nanoClock.getAsLong());
	}

	@Override
	public void onDecision(PhantomActivityState activityState, RuntimeSnapshot snapshot, long logicalNowNanos)
	{
		observe(activityState, snapshot, logicalNowNanos);
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
		final boolean selectionChanged = _selectedProfileId != profileId;
		if (selectionChanged)
		{
			clearStorage();
		}
		_selectedProfileId = profileId;
		_current = DecisionView.from(null, current);
		final long logicalNowNanos = monotonicNow(_nanoClock.getAsLong());
		if (selectionChanged || !_hasProgressBaseline)
		{
			resetProgress(_current, logicalNowNanos);
		}
		else
		{
			updateProgress(_current, logicalNowNanos);
		}
		return SelectionStatus.SELECTED;
	}

	public synchronized void clear()
	{
		_selectedProfileId = 0;
		clearStorage();
	}

	public synchronized void observe(PhantomActivityState activityState, RuntimeSnapshot snapshot)
	{
		observe(activityState, snapshot, _nanoClock.getAsLong());
	}

	public synchronized void observe(PhantomActivityState activityState, RuntimeSnapshot snapshot, long logicalNowNanos)
	{
		Objects.requireNonNull(activityState, "Activity state must not be null.");
		Objects.requireNonNull(snapshot, "Runtime snapshot must not be null.");
		if (!_enabled || (_selectedProfileId == 0) || (_selectedProfileId != snapshot.profileId()))
		{
			return;
		}
		final DecisionView entry = DecisionView.from(activityState, snapshot);
		updateProgress(entry, monotonicNow(logicalNowNanos));
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
		return snapshot(true);
	}

	public synchronized Snapshot snapshot(boolean attached)
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
		final HealthView health = health(attached, monotonicNow(_nanoClock.getAsLong()));
		return new Snapshot(true, _capacity, _selectedProfileId, _selectedProfileId > 0 && attached, _recorded, _dropped, _current, List.copyOf(history), health.health(), health.ageMillis(), _slowThresholdMillis, _stuckThresholdMillis);
	}

	private HealthView health(boolean attached, long nowNanos)
	{
		if ((_selectedProfileId <= 0) || (_current == null) || !attached)
		{
			return new HealthView(Health.IDLE, 0);
		}
		if ((_current.runtimeState() == PhantomDecisionEngine.RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD) || (_current.runtimeState() == PhantomDecisionEngine.RuntimeState.PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD))
		{
			return new HealthView(Health.ATTENTION, ageMillis(nowNanos));
		}
		if ((_current.goalId() <= 0) || (_current.goalStatus() != PhantomGoalStatus.ACTIVE) || (_current.runtimeState() == PhantomDecisionEngine.RuntimeState.NO_GOAL) || (_current.runtimeState() == PhantomDecisionEngine.RuntimeState.TERMINAL))
		{
			return new HealthView(Health.IDLE, 0);
		}
		final long ageMillis = ageMillis(nowNanos);
		if (_current.runtimeState() == PhantomDecisionEngine.RuntimeState.WAITING_RETRY)
		{
			return new HealthView(Health.WAITING, ageMillis);
		}
		if (ageMillis >= _stuckThresholdMillis)
		{
			return new HealthView(Health.STUCK, ageMillis);
		}
		if (ageMillis >= _slowThresholdMillis)
		{
			return new HealthView(Health.SLOW, ageMillis);
		}
		return new HealthView(Health.HEALTHY, ageMillis);
	}

	private long ageMillis(long nowNanos)
	{
		if (!_hasProgressBaseline || (nowNanos < _progressBaselineNanos))
		{
			return 0;
		}
		final long ageNanos = nowNanos - _progressBaselineNanos;
		return ageNanos < 0 ? Long.MAX_VALUE : ageNanos / 1_000_000L;
	}

	private long monotonicNow(long candidateNanos)
	{
		if (!_hasProgressBaseline || (candidateNanos > _lastNowNanos))
		{
			_lastNowNanos = candidateNanos;
		}
		return _lastNowNanos;
	}

	private void updateProgress(DecisionView entry, long logicalNowNanos)
	{
		final ProgressFingerprint fingerprint = ProgressFingerprint.from(entry);
		if (!_hasProgressBaseline || !fingerprint.equals(_progressFingerprint))
		{
			_progressFingerprint = fingerprint;
			_progressBaselineNanos = logicalNowNanos;
			_hasProgressBaseline = true;
		}
	}

	private void resetProgress(DecisionView entry, long logicalNowNanos)
	{
		_progressFingerprint = ProgressFingerprint.from(entry);
		_progressBaselineNanos = logicalNowNanos;
		_lastNowNanos = logicalNowNanos;
		_hasProgressBaseline = true;
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
		_progressFingerprint = null;
		_progressBaselineNanos = 0;
		_lastNowNanos = 0;
		_hasProgressBaseline = false;
	}
	public enum SelectionStatus
	{
		SELECTED,
		NOT_ATTACHED,
		DISABLED
	}

	public enum Health
	{
		IDLE,
		HEALTHY,
		WAITING,
		SLOW,
		STUCK,
		ATTENTION
	}

	public record Snapshot(boolean enabled, int capacity, long selectedProfileId, boolean attached, long recorded, long dropped, DecisionView current, List<DecisionView> history, Health health, long ageMillis, long slowThresholdMillis, long stuckThresholdMillis)
	{
		public Snapshot
		{
			history = List.copyOf(history);
		}

		public static Snapshot disabled()
		{
			return new Snapshot(false, 0, 0, false, 0, 0, null, List.of(), Health.IDLE, 0, SLOW_THRESHOLD_MILLIS, STUCK_THRESHOLD_MILLIS);
		}
	}

	public record DecisionView(PhantomActivityState activityState, long profileId, long goalId, String goalType, long goalRevision, PhantomGoalStatus goalStatus, PhantomDecisionEngine.RuntimeState runtimeState, long decisionSequence, String candidateKey, int score, long planId, int step, int attempt, PhantomStepResult.Type lastResult, String reasonKey, List<CandidateEvaluation> topCandidates)
	{
		public DecisionView
		{
			topCandidates = List.copyOf(topCandidates.subList(0, Math.min(PhantomDecisionEngine.MAX_EXPLANATIONS, topCandidates.size())));
		}

		private static DecisionView from(PhantomActivityState activityState, RuntimeSnapshot snapshot)
		{
			return new DecisionView(activityState, snapshot.profileId(), snapshot.goalId(), snapshot.goalType(), snapshot.goalRevision(), snapshot.goalStatus(), snapshot.runtimeState(), snapshot.decisionSequence(), snapshot.selectedCandidateKey(), snapshot.selectedScore(), snapshot.planId(), snapshot.currentStep(), snapshot.attempt(), snapshot.lastResult(), snapshot.reasonKey(), snapshot.topCandidateEvaluations());
		}
	}

	private record HealthView(Health health, long ageMillis)
	{
	}

	private record ProgressFingerprint(long goalId, long goalRevision, PhantomGoalStatus goalStatus, PhantomDecisionEngine.RuntimeState runtimeState, String candidateKey, int score, long planId, int step, int attempt, PhantomStepResult.Type lastResult, String reasonKey, List<CandidateEvaluation> topCandidates)
	{
		private ProgressFingerprint
		{
			topCandidates = List.copyOf(topCandidates);
		}

		private static ProgressFingerprint from(DecisionView view)
		{
			return new ProgressFingerprint(view.goalId(), view.goalRevision(), view.goalStatus(), view.runtimeState(), view.candidateKey(), view.score(), view.planId(), view.step(), view.attempt(), view.lastResult(), view.reasonKey(), view.topCandidates());
		}
	}
}

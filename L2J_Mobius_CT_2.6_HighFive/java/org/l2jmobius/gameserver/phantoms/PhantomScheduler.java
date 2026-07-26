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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.Outcome;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.TransitionOutcome;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityResultCategory;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivitySnapshot;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityTransitionStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;

/**
 * Single shared, bounded activity scheduler. Slots contain no Player, task,
 * thread, executor or future.
 */
public final class PhantomScheduler
{
	public enum SchedulerState
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum RegistrationStatus
	{
		REGISTERED,
		ALREADY_REGISTERED,
		CAPACITY_REACHED,
		NOT_RUNNING,
		INVALID_PROFILE_ID
	}

	public enum UnregisterStatus
	{
		UNREGISTERED,
		PENDING,
		NOT_REGISTERED,
		NOT_RUNNING,
		BACKPRESSURE,
		INVALID_PROFILE_ID
	}

	public enum SignalStatus
	{
		ACCEPTED,
		COALESCED,
		STALE,
		REJECTED,
		BACKPRESSURE,
		NOT_REGISTERED,
		NOT_RUNNING
	}

	public enum RetryStatus
	{
		SCHEDULED,
		NOT_REQUIRED,
		BACKPRESSURE,
		NOT_REGISTERED,
		NOT_RUNNING
	}

	public enum BeginStopResult
	{
		STARTED,
		ALREADY_STOPPING,
		ALREADY_STOPPED
	}

	private enum BoundaryAction
	{
		NONE,
		MATERIALIZE,
		DEMATERIALIZE,
		RETRY_MATERIALIZATION_CLEANUP,
		RETRY_DEMATERIALIZATION_CLEANUP
	}

	private enum RetainedFailureKind
	{
		NONE,
		MATERIALIZATION,
		DEMATERIALIZATION
	}

	@FunctionalInterface
	interface MonotonicClock
	{
		long nanoTime();
	}

	@FunctionalInterface
	interface PulseDriver
	{
		ScheduledFuture<?> scheduleAtFixedRate(Runnable pulse, long pulseMillis);
	}

	private final Object _monitor = new Object();
	private final int _maximumProfiles;
	private final int _profilesPerPulse;
	private final int _pulseMillis;
	private final PhantomSchedulerPolicy _policy;
	private final MonotonicClock _clock;
	private final PulseDriver _pulseDriver;
	private final boolean _scheduledPulseRequired;
	private final PhantomActivityMaterializationPort _materializationPort;
	private final PhantomActivityWorkSink _workSink;
	private final PhantomMetrics _metrics;
	private final PhantomDiagnosticTrace _trace;
	private final ConcurrentHashMap<Long, Slot> _slots = new ConcurrentHashMap<>();
	private final ArrayBlockingQueue<Long> _readyQueue;
	private final TreeSet<DueEntry> _dueEntries = new TreeSet<>();
	private SchedulerState _state = SchedulerState.NEW;
	private ScheduledFuture<?> _pulseFuture;
	private long _fairnessSequence;
	private long _pulseSequence;
	private boolean _pulseInFlight;
	private PhantomActivityOverloadLevel _overloadLevel = PhantomActivityOverloadLevel.NORMAL;
	private PhantomActivityOverloadLevel _peakOverloadLevel = PhantomActivityOverloadLevel.NORMAL;

	public PhantomScheduler(int maximumProfiles, int pulseMillis, int profilesPerPulse, PhantomMetrics metrics, PhantomDiagnosticTrace trace, PhantomActivityMaterializationPort materializationPort, PhantomActivityWorkSink workSink)
	{
		this(maximumProfiles, pulseMillis, profilesPerPulse, PhantomSchedulerPolicy.productionDefaults(pulseMillis), System::nanoTime, (pulse, period) -> ThreadPool.scheduleAtFixedRate(pulse, period, period), true, metrics, trace, materializationPort, workSink);
	}

	PhantomScheduler(int maximumProfiles, int pulseMillis, int profilesPerPulse, PhantomSchedulerPolicy policy, MonotonicClock clock, PulseDriver pulseDriver, boolean scheduledPulseRequired, PhantomMetrics metrics, PhantomDiagnosticTrace trace, PhantomActivityMaterializationPort materializationPort, PhantomActivityWorkSink workSink)
	{
		if ((maximumProfiles < 1) || (maximumProfiles > 1_000_000))
		{
			throw new IllegalArgumentException("maximumProfiles must be between 1 and 1000000.");
		}
		if ((pulseMillis < 10) || (pulseMillis > 1000))
		{
			throw new IllegalArgumentException("pulseMillis must be between 10 and 1000.");
		}
		if ((profilesPerPulse < 1) || (profilesPerPulse > 10000))
		{
			throw new IllegalArgumentException("profilesPerPulse must be between 1 and 10000.");
		}
		_maximumProfiles = maximumProfiles;
		_pulseMillis = pulseMillis;
		_profilesPerPulse = profilesPerPulse;
		_policy = Objects.requireNonNull(policy, "policy");
		_clock = Objects.requireNonNull(clock, "clock");
		_pulseDriver = Objects.requireNonNull(pulseDriver, "pulseDriver");
		_scheduledPulseRequired = scheduledPulseRequired;
		_metrics = Objects.requireNonNull(metrics, "metrics");
		_trace = Objects.requireNonNull(trace, "trace");
		_materializationPort = Objects.requireNonNull(materializationPort, "materializationPort");
		_workSink = Objects.requireNonNull(workSink, "workSink");
		_readyQueue = new ArrayBlockingQueue<>(maximumProfiles);
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if (_state != SchedulerState.NEW)
			{
				return false;
			}
			_state = SchedulerState.RUNNING;
			try
			{
				_pulseFuture = _pulseDriver.scheduleAtFixedRate(this::pulseSafely, _pulseMillis);
				if (_scheduledPulseRequired && (_pulseFuture == null))
				{
					_state = SchedulerState.STOPPED;
					return false;
				}
			}
			catch (RuntimeException e)
			{
				_state = SchedulerState.STOPPED;
				throw e;
			}
			return true;
		}
	}

	public RegistrationResult register(long profileId)
	{
		synchronized (_monitor)
		{
			if (_state != SchedulerState.RUNNING)
			{
				return new RegistrationResult(RegistrationStatus.NOT_RUNNING, null);
			}
			if (profileId <= 0)
			{
				_metrics.recordActivityRegistrationRejected();
				return new RegistrationResult(RegistrationStatus.INVALID_PROFILE_ID, null);
			}
			final Slot existing = _slots.get(profileId);
			if (existing != null)
			{
				return new RegistrationResult(RegistrationStatus.ALREADY_REGISTERED, snapshotLocked(existing));
			}
			if (_slots.size() >= _maximumProfiles)
			{
				_metrics.recordActivityRegistrationRejected();
				return new RegistrationResult(RegistrationStatus.CAPACITY_REACHED, null);
			}
			final Slot slot = new Slot(profileId);
			_slots.put(profileId, slot);
			_metrics.recordActivityRegistered(PhantomActivityState.SLEEPING);
			return new RegistrationResult(RegistrationStatus.REGISTERED, snapshotLocked(slot));
		}
	}

	public UnregisterResult unregister(long profileId)
	{
		synchronized (_monitor)
		{
			if (_state != SchedulerState.RUNNING)
			{
				return new UnregisterResult(UnregisterStatus.NOT_RUNNING, null);
			}
			if (profileId <= 0)
			{
				return new UnregisterResult(UnregisterStatus.INVALID_PROFILE_ID, null);
			}
			final Slot slot = _slots.get(profileId);
			if (slot == null)
			{
				return new UnregisterResult(UnregisterStatus.NOT_REGISTERED, null);
			}
			if (isTerminalNonMaterializedLocked(slot))
			{
				removeSlotLocked(slot);
				return new UnregisterResult(UnregisterStatus.UNREGISTERED, null);
			}
			if (!reserveReadyLocked(slot))
			{
				return new UnregisterResult(UnregisterStatus.BACKPRESSURE, snapshotLocked(slot));
			}
			slot._unregisterRequested = true;
			slot._generation++;
			for (SourceEntry source : slot._sources.values())
			{
				source._signal = null;
			}
			slot._requestedState = PhantomActivityState.SLEEPING;
			slot._transitionStatus = PhantomActivityTransitionStatus.UNREGISTER_PENDING;
			return new UnregisterResult(UnregisterStatus.PENDING, snapshotLocked(slot));
		}
	}

	public SignalResult submitSignal(long profileId, PhantomRelevanceSignal signal)
	{
		Objects.requireNonNull(signal, "signal");
		synchronized (_monitor)
		{
			if (_state != SchedulerState.RUNNING)
			{
				return new SignalResult(SignalStatus.NOT_RUNNING, null);
			}
			final Slot slot = _slots.get(profileId);
			if (slot == null)
			{
				return new SignalResult(SignalStatus.NOT_REGISTERED, null);
			}
			if (slot._unregisterRequested || (signal.ttlMillis() > _policy.maximumSignalTtlMillis()))
			{
				_metrics.recordActivitySignalRejected();
				return new SignalResult(SignalStatus.REJECTED, snapshotLocked(slot));
			}
			final SourceEntry existing = slot._sources.get(signal.sourceKey());
			if ((existing != null) && (signal.sequence() <= existing._sequence))
			{
				_metrics.recordActivitySignalStale();
				return new SignalResult(SignalStatus.STALE, snapshotLocked(slot));
			}
			if ((existing == null) && (slot._sources.size() >= _policy.maximumSignalSources()))
			{
				_metrics.recordActivitySignalRejected();
				return new SignalResult(SignalStatus.REJECTED, snapshotLocked(slot));
			}
			final boolean coalesced = slot._enqueued;
			if (!reserveReadyLocked(slot))
			{
				_metrics.recordActivityReadyBackpressure();
				return new SignalResult(SignalStatus.BACKPRESSURE, snapshotLocked(slot));
			}
			final long now = _clock.nanoTime();
			final long expiresAt = saturatingAdd(now, millisToNanos(signal.ttlMillis()));
			if (existing == null)
			{
				slot._sources.put(signal.sourceKey(), new SourceEntry(signal.sequence(), signal, expiresAt));
			}
			else
			{
				existing._sequence = signal.sequence();
				existing._signal = signal;
				existing._expiresAtNanos = expiresAt;
			}
			slot._generation++;
			slot._lastResult = PhantomActivityResultCategory.SIGNAL_ACCEPTED;
			if (coalesced)
			{
				_metrics.recordActivitySignalCoalesced();
				return new SignalResult(SignalStatus.COALESCED, snapshotLocked(slot));
			}
			_metrics.recordActivitySignalAccepted();
			return new SignalResult(SignalStatus.ACCEPTED, snapshotLocked(slot));
		}
	}

	public SignalResult withdrawSignal(long profileId, String sourceKey, long sequence)
	{
		synchronized (_monitor)
		{
			if (_state != SchedulerState.RUNNING)
			{
				return new SignalResult(SignalStatus.NOT_RUNNING, null);
			}
			final Slot slot = _slots.get(profileId);
			if (slot == null)
			{
				return new SignalResult(SignalStatus.NOT_REGISTERED, null);
			}
			if (!PhantomRelevanceSignal.isValidSourceKey(sourceKey) || (sequence < 0) || slot._unregisterRequested)
			{
				_metrics.recordActivitySignalRejected();
				return new SignalResult(SignalStatus.REJECTED, snapshotLocked(slot));
			}
			final SourceEntry source = slot._sources.get(sourceKey);
			if ((source == null) || (sequence <= source._sequence))
			{
				_metrics.recordActivitySignalStale();
				return new SignalResult(SignalStatus.STALE, snapshotLocked(slot));
			}
			final boolean coalesced = slot._enqueued;
			if (!reserveReadyLocked(slot))
			{
				_metrics.recordActivityReadyBackpressure();
				return new SignalResult(SignalStatus.BACKPRESSURE, snapshotLocked(slot));
			}
			source._sequence = sequence;
			source._signal = null;
			source._expiresAtNanos = 0;
			slot._generation++;
			slot._lastResult = PhantomActivityResultCategory.SIGNAL_ACCEPTED;
			if (coalesced)
			{
				_metrics.recordActivitySignalCoalesced();
				return new SignalResult(SignalStatus.COALESCED, snapshotLocked(slot));
			}
			_metrics.recordActivitySignalAccepted();
			return new SignalResult(SignalStatus.ACCEPTED, snapshotLocked(slot));
		}
	}

	public RetryResult retryTransition(long profileId)
	{
		synchronized (_monitor)
		{
			if (_state != SchedulerState.RUNNING)
			{
				return new RetryResult(RetryStatus.NOT_RUNNING, null);
			}
			final Slot slot = _slots.get(profileId);
			if (slot == null)
			{
				return new RetryResult(RetryStatus.NOT_REGISTERED, null);
			}
			if (slot._transitionStatus != PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY)
			{
				return new RetryResult(RetryStatus.NOT_REQUIRED, snapshotLocked(slot));
			}
			if (!reserveReadyLocked(slot))
			{
				_metrics.recordActivityReadyBackpressure();
				return new RetryResult(RetryStatus.BACKPRESSURE, snapshotLocked(slot));
			}
			slot._explicitRetry = true;
			slot._generation++;
			_metrics.recordActivityExplicitRetry();
			return new RetryResult(RetryStatus.SCHEDULED, snapshotLocked(slot));
		}
	}

	public Optional<PhantomActivitySnapshot> find(long profileId)
	{
		synchronized (_monitor)
		{
			final Slot slot = _slots.get(profileId);
			return slot == null ? Optional.empty() : Optional.of(snapshotLocked(slot));
		}
	}

	public List<PhantomActivitySnapshot> list()
	{
		synchronized (_monitor)
		{
			final List<PhantomActivitySnapshot> snapshots = new ArrayList<>(_slots.size());
			for (Slot slot : _slots.values())
			{
				snapshots.add(snapshotLocked(slot));
			}
			snapshots.sort(Comparator.comparingLong(PhantomActivitySnapshot::profileId));
			return List.copyOf(snapshots);
		}
	}

	public BeginStopResult beginStop()
	{
		final ScheduledFuture<?> pulseFuture;
		synchronized (_monitor)
		{
			if (_state == SchedulerState.STOPPED)
			{
				return BeginStopResult.ALREADY_STOPPED;
			}
			if (_state == SchedulerState.STOPPING)
			{
				return BeginStopResult.ALREADY_STOPPING;
			}
			_state = SchedulerState.STOPPING;
			pulseFuture = _pulseFuture;
			_pulseFuture = null;
			_metrics.recordActivityBeginStop();
		}
		if (pulseFuture != null)
		{
			pulseFuture.cancel(false);
		}
		return BeginStopResult.STARTED;
	}

	public boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_state == SchedulerState.STOPPED)
			{
				return false;
			}
			if ((_state != SchedulerState.STOPPING) && (_state != SchedulerState.NEW))
			{
				return false;
			}
			if (_pulseInFlight || hasInFlightSlotLocked())
			{
				return false;
			}
			_readyQueue.clear();
			_dueEntries.clear();
			for (Slot slot : _slots.values())
			{
				_metrics.recordActivityUnregistered(slot._effectiveState);
			}
			_slots.clear();
			_state = SchedulerState.STOPPED;
			_metrics.recordActivityFinishStop();
			return true;
		}
	}

	public SchedulerSnapshot snapshot()
	{
		synchronized (_monitor)
		{
			return new SchedulerSnapshot(_state, _slots.size(), _readyQueue.size(), _dueEntries.size(), _maximumProfiles, _pulseFuture != null ? 1 : 0, _pulseSequence, _pulseInFlight, _overloadLevel, _peakOverloadLevel);
		}
	}

	void pulse()
	{
		pulseSafely();
	}

	private void pulseSafely()
	{
		final long logicalNow = _clock.nanoTime();
		final long wallDeadline = saturatingAdd(logicalNow, millisToNanos(_policy.pulseWallBudgetMillis()));
		boolean pulseClaimed = false;
		try
		{
			final PhantomActivityOverloadLevel pulseOverload;
			synchronized (_monitor)
			{
				if ((_state != SchedulerState.RUNNING) || _pulseInFlight)
				{
					return;
				}
				_pulseInFlight = true;
				pulseClaimed = true;
				_pulseSequence++;
				_metrics.recordActivityPulseStarted();
				moveDueProfilesLocked(logicalNow);
				pulseOverload = updateOverloadLocked();
			}

			int processed = 0;
			final Set<Long> processedProfiles = new HashSet<>();
			while (processed < _profilesPerPulse)
			{
				if ((processed > 0) && (_clock.nanoTime() >= wallDeadline))
				{
					_metrics.recordActivityPulseOverrun();
					break;
				}
				final Slot slot;
				synchronized (_monitor)
				{
					if (_state != SchedulerState.RUNNING)
					{
						break;
					}
					slot = pollReadySlotLocked(processedProfiles);
					if (slot == null)
					{
						break;
					}
					slot._processing = true;
				}
				processedProfiles.add(slot._profileId);
				try
				{
					processSlot(slot, logicalNow, pulseOverload);
				}
				catch (Throwable throwable)
				{
					synchronized (_monitor)
					{
						slot._boundaryInFlight = false;
						slot._boundaryGeneration = 0;
						if (_slots.get(slot._profileId) == slot)
						{
							slot._lastResult = PhantomActivityResultCategory.WORK_FAILED;
							slot._processing = false;
							if (_state == SchedulerState.RUNNING)
							{
								scheduleNextDueLocked(slot, logicalNow, pulseOverload);
							}
						}
					}
					_metrics.recordActivityWorkFailure();
				}
				processed++;
			}
		}
		finally
		{
			if (pulseClaimed)
			{
				synchronized (_monitor)
				{
					_pulseInFlight = false;
				}
				_metrics.recordActivityPulseCompleted();
			}
		}
	}

	private Slot pollReadySlotLocked(Set<Long> processedProfiles)
	{
		int rotations = _readyQueue.size();
		while (rotations-- > 0)
		{
			final Long profileId = _readyQueue.poll();
			if (profileId == null)
			{
				return null;
			}
			final Slot slot = _slots.get(profileId);
			if (slot == null)
			{
				continue;
			}
			if (processedProfiles.contains(profileId))
			{
				_readyQueue.offer(profileId);
				continue;
			}
			slot._enqueued = false;
			return slot;
		}
		return null;
	}

	private void moveDueProfilesLocked(long logicalNow)
	{
		while (!_dueEntries.isEmpty())
		{
			final DueEntry due = _dueEntries.first();
			if (due._dueNanos > logicalNow)
			{
				break;
			}
			final Slot slot = _slots.get(due._profileId);
			if ((slot == null) || (slot._dueEntry != due))
			{
				_dueEntries.pollFirst();
				continue;
			}
			if (slot._enqueued || slot._processing)
			{
				_dueEntries.pollFirst();
				slot._dueEntry = null;
				continue;
			}
			if (!_readyQueue.offer(slot._profileId))
			{
				_metrics.recordActivityDueDeferred();
				break;
			}
			_dueEntries.pollFirst();
			slot._dueEntry = null;
			slot._enqueued = true;
			_metrics.recordActivityDueMoved();
			_metrics.recordActivityReadyEnqueued();
		}
	}

	private PhantomActivityOverloadLevel updateOverloadLocked()
	{
		final long occupied = _readyQueue.size();
		final PhantomActivityOverloadLevel next;
		if ((occupied * 100L) >= (_maximumProfiles * 90L))
		{
			next = PhantomActivityOverloadLevel.CRITICAL;
		}
		else if ((occupied * 100L) >= (_maximumProfiles * 75L))
		{
			next = PhantomActivityOverloadLevel.HIGH;
		}
		else if ((occupied * 100L) >= (_maximumProfiles * 50L))
		{
			next = PhantomActivityOverloadLevel.ELEVATED;
		}
		else
		{
			next = PhantomActivityOverloadLevel.NORMAL;
		}
		if (next != _overloadLevel)
		{
			_overloadLevel = next;
			if (next.ordinal() > _peakOverloadLevel.ordinal())
			{
				_peakOverloadLevel = next;
			}
			_metrics.recordActivityOverloadTransition(next);
		}
		return next;
	}

	private void processSlot(Slot slot, long logicalNow, PhantomActivityOverloadLevel overload)
	{
		final TransitionPlan plan;
		synchronized (_monitor)
		{
			if ((_state != SchedulerState.RUNNING) || (_slots.get(slot._profileId) != slot))
			{
				slot._processing = false;
				return;
			}
			expireSignalsLocked(slot, logicalNow);
			final PhantomActivityState requested = slot._unregisterRequested ? PhantomActivityState.SLEEPING : requestedStateLocked(slot);
			slot._requestedState = requested;
			plan = transitionPlanLocked(slot, requested, logicalNow);
			if ((plan != null) && (plan._action != BoundaryAction.NONE))
			{
				slot._boundaryInFlight = true;
				slot._boundaryGeneration = plan._generation;
			}
		}

		if (plan != null)
		{
			final TransitionOutcome outcome = executeBoundary(plan);
			synchronized (_monitor)
			{
				slot._boundaryInFlight = false;
				slot._boundaryGeneration = 0;
				if (_slots.get(slot._profileId) != slot)
				{
					return;
				}
				applyTransitionOutcomeLocked(slot, plan, outcome, logicalNow);
			}
		}

		final PhantomActivityWorkItem workItem;
		synchronized (_monitor)
		{
			if (_slots.get(slot._profileId) != slot)
			{
				return;
			}
			workItem = prepareWorkLocked(slot, logicalNow, overload);
		}
		if (workItem != null)
		{
			boolean succeeded = true;
			try
			{
				_workSink.accept(workItem);
			}
			catch (Throwable throwable)
			{
				succeeded = false;
				_metrics.recordActivityWorkFailure();
			}
			synchronized (_monitor)
			{
				if (_slots.get(slot._profileId) == slot)
				{
					slot._lastResult = succeeded ? PhantomActivityResultCategory.WORK_DELIVERED : PhantomActivityResultCategory.WORK_FAILED;
				}
			}
			if (succeeded)
			{
				_metrics.recordActivityWorkDelivered();
			}
		}

		synchronized (_monitor)
		{
			if (_slots.get(slot._profileId) != slot)
			{
				return;
			}
			slot._processing = false;
			if (slot._unregisterRequested && isTerminalNonMaterializedLocked(slot))
			{
				removeSlotLocked(slot);
				return;
			}
			if (_state == SchedulerState.RUNNING)
			{
				scheduleNextDueLocked(slot, logicalNow, overload);
			}
		}
	}

	private TransitionPlan transitionPlanLocked(Slot slot, PhantomActivityState requested, long logicalNow)
	{
		if (slot._retainedFailureKind != RetainedFailureKind.NONE)
		{
			if (!slot._explicitRetry)
			{
				slot._transitionStatus = PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY;
				return null;
			}
			final BoundaryAction retryAction = slot._retainedFailureKind == RetainedFailureKind.MATERIALIZATION ? BoundaryAction.RETRY_MATERIALIZATION_CLEANUP : BoundaryAction.RETRY_DEMATERIALIZATION_CLEANUP;
			slot._explicitRetry = false;
			return new TransitionPlan(slot._profileId, slot._generation, requested, retryAction);
		}

		if (requested == slot._effectiveState)
		{
			slot._demotionEligibleAtNanos = 0;
			slot._retryDueNanos = 0;
			slot._retryAttempt = 0;
			slot._blockedTarget = null;
			slot._explicitRetry = false;
			slot._transitionStatus = slot._unregisterRequested ? PhantomActivityTransitionStatus.UNREGISTER_PENDING : PhantomActivityTransitionStatus.STABLE;
			return null;
		}

		if ((slot._transitionStatus == PhantomActivityTransitionStatus.TRANSIENTLY_BLOCKED) && (slot._blockedTarget == requested) && (logicalNow < slot._retryDueNanos))
		{
			return null;
		}
		if (slot._blockedTarget != requested)
		{
			slot._retryDueNanos = 0;
			slot._retryAttempt = 0;
			slot._blockedTarget = null;
		}

		final boolean promotion = requested.isHigherDetailThan(slot._effectiveState);
		if (!promotion)
		{
			if (slot._demotionEligibleAtNanos == 0)
			{
				slot._demotionEligibleAtNanos = saturatingAdd(logicalNow, millisToNanos(_policy.demotionGraceMillis()));
			}
			if (logicalNow < slot._demotionEligibleAtNanos)
			{
				slot._transitionStatus = slot._unregisterRequested ? PhantomActivityTransitionStatus.UNREGISTER_PENDING : PhantomActivityTransitionStatus.DEMOTION_GRACE;
				return null;
			}
		}
		else
		{
			slot._demotionEligibleAtNanos = 0;
		}

		slot._transitionStatus = slot._unregisterRequested ? PhantomActivityTransitionStatus.UNREGISTER_PENDING : (promotion ? PhantomActivityTransitionStatus.PROMOTION_PENDING : PhantomActivityTransitionStatus.DEMOTION_PENDING);
		final BoundaryAction action;
		if (!slot._effectiveState.requiresMaterialization() && requested.requiresMaterialization())
		{
			action = BoundaryAction.MATERIALIZE;
		}
		else if (slot._effectiveState.requiresMaterialization() && !requested.requiresMaterialization())
		{
			action = BoundaryAction.DEMATERIALIZE;
		}
		else
		{
			action = BoundaryAction.NONE;
		}
		return new TransitionPlan(slot._profileId, slot._generation, requested, action);
	}

	private TransitionOutcome executeBoundary(TransitionPlan plan)
	{
		try
		{
			return switch (plan._action)
			{
				case NONE -> TransitionOutcome.success();
				case MATERIALIZE -> _materializationPort.materialize(plan._profileTargetId);
				case DEMATERIALIZE -> _materializationPort.dematerialize(plan._profileTargetId);
				case RETRY_MATERIALIZATION_CLEANUP, RETRY_DEMATERIALIZATION_CLEANUP ->
				{
					final TransitionOutcome outcome = _materializationPort.retryCleanup(plan._profileTargetId);
					yield ((outcome != null) && (outcome.outcome() == Outcome.SUCCESS) && _materializationPort.hasLifecycleOwnership(plan._profileTargetId)) ? TransitionOutcome.retainedFailure() : outcome;
				}
			};
		}
		catch (Throwable throwable)
		{
			return TransitionOutcome.transientBlock();
		}
	}

	private void applyTransitionOutcomeLocked(Slot slot, TransitionPlan plan, TransitionOutcome outcome, long logicalNow)
	{
		if (outcome == null)
		{
			outcome = TransitionOutcome.transientBlock();
		}
		if (outcome.outcome() == Outcome.SUCCESS)
		{
			if ((plan._action == BoundaryAction.RETRY_MATERIALIZATION_CLEANUP) || (plan._action == BoundaryAction.RETRY_DEMATERIALIZATION_CLEANUP))
			{
				final PhantomActivityState previous = slot._effectiveState;
				final boolean freshMaterializationRequired = plan._targetState.requiresMaterialization();
				slot._effectiveState = freshMaterializationRequired ? PhantomActivityState.SLEEPING : plan._targetState;
				slot._retainedFailureKind = RetainedFailureKind.NONE;
				slot._transitionStatus = slot._unregisterRequested ? PhantomActivityTransitionStatus.UNREGISTER_PENDING : (freshMaterializationRequired ? PhantomActivityTransitionStatus.PROMOTION_PENDING : PhantomActivityTransitionStatus.STABLE);
				slot._lastResult = PhantomActivityResultCategory.TRANSITION_SUCCEEDED;
				slot._retryAttempt = 0;
				slot._blockedTarget = null;
				slot._retryDueNanos = 0;
				slot._demotionEligibleAtNanos = 0;
				slot._lastTransitionNanos = logicalNow;
				slot._nextWorkDueNanos = slot._effectiveState == PhantomActivityState.SLEEPING ? 0 : logicalNow;
				_metrics.recordActivityTransition(previous, slot._effectiveState);
				if (freshMaterializationRequired || (slot._generation != plan._generation))
				{
					ensureNextOpportunityLocked(slot, logicalNow);
				}
				return;
			}
			final PhantomActivityState previous = slot._effectiveState;
			slot._effectiveState = plan._targetState;
			slot._retainedFailureKind = RetainedFailureKind.NONE;
			slot._retryAttempt = 0;
			slot._retryDueNanos = 0;
			slot._blockedTarget = null;
			slot._demotionEligibleAtNanos = 0;
			slot._transitionStatus = slot._unregisterRequested ? PhantomActivityTransitionStatus.UNREGISTER_PENDING : PhantomActivityTransitionStatus.STABLE;
			slot._lastResult = PhantomActivityResultCategory.TRANSITION_SUCCEEDED;
			slot._lastTransitionNanos = logicalNow;
			slot._nextWorkDueNanos = slot._effectiveState == PhantomActivityState.SLEEPING ? 0 : logicalNow;
			_metrics.recordActivityTransition(previous, slot._effectiveState);
			if (slot._generation != plan._generation)
			{
				slot._transitionStatus = PhantomActivityTransitionStatus.TRANSIENTLY_BLOCKED;
				slot._blockedTarget = null;
				slot._retryDueNanos = logicalNow;
			}
			return;
		}
		if (outcome.outcome() == Outcome.RETAINED_FAILURE)
		{
			slot._retainedFailureKind = ((plan._action == BoundaryAction.MATERIALIZE) || (plan._action == BoundaryAction.RETRY_MATERIALIZATION_CLEANUP)) ? RetainedFailureKind.MATERIALIZATION : RetainedFailureKind.DEMATERIALIZATION;
			slot._transitionStatus = PhantomActivityTransitionStatus.RETAINED_FAILURE_REQUIRES_EXPLICIT_RETRY;
			slot._lastResult = PhantomActivityResultCategory.TRANSITION_RETAINED_FAILURE;
			slot._retryDueNanos = 0;
			_metrics.recordActivityTransitionRetainedFailure();
			return;
		}
		slot._transitionStatus = PhantomActivityTransitionStatus.TRANSIENTLY_BLOCKED;
		slot._lastResult = PhantomActivityResultCategory.TRANSITION_TRANSIENTLY_BLOCKED;
		slot._blockedTarget = plan._targetState;
		slot._retryAttempt = Math.min(31, slot._retryAttempt + 1);
		final long retryMillis = boundedExponentialBackoff(slot._retryAttempt);
		slot._retryDueNanos = saturatingAdd(logicalNow, millisToNanos(retryMillis));
		_metrics.recordActivityTransitionTransientBlock();
	}

	private PhantomActivityWorkItem prepareWorkLocked(Slot slot, long logicalNow, PhantomActivityOverloadLevel overload)
	{
		if ((_state != SchedulerState.RUNNING) || slot._unregisterRequested || (slot._effectiveState == PhantomActivityState.SLEEPING))
		{
			return null;
		}
		if ((slot._nextWorkDueNanos != 0) && (logicalNow < slot._nextWorkDueNanos))
		{
			return null;
		}
		final long tickSequence = ++slot._tickSequence;
		final long cadenceMillis = saturatingMultiply(_policy.cadenceMillis(slot._effectiveState), overload.cadenceMultiplier(slot._effectiveState));
		slot._nextWorkDueNanos = saturatingAdd(logicalNow, millisToNanos(cadenceMillis));
		return new PhantomActivityWorkItem(slot._profileId, slot._effectiveState, tickSequence, logicalNow, overload);
	}

	private void scheduleNextDueLocked(Slot slot, long logicalNow, PhantomActivityOverloadLevel overload)
	{
		if (slot._enqueued || slot._processing || (_slots.get(slot._profileId) != slot))
		{
			return;
		}
		long nextDue = Long.MAX_VALUE;
		if ((slot._transitionStatus == PhantomActivityTransitionStatus.TRANSIENTLY_BLOCKED) && (slot._retryDueNanos > 0))
		{
			nextDue = Math.min(nextDue, slot._retryDueNanos);
		}
		if ((slot._transitionStatus == PhantomActivityTransitionStatus.DEMOTION_GRACE) || ((slot._transitionStatus == PhantomActivityTransitionStatus.UNREGISTER_PENDING) && (slot._demotionEligibleAtNanos > 0)))
		{
			nextDue = Math.min(nextDue, slot._demotionEligibleAtNanos);
		}
		if (!slot._unregisterRequested && (slot._effectiveState != PhantomActivityState.SLEEPING) && (slot._nextWorkDueNanos > 0))
		{
			nextDue = Math.min(nextDue, slot._nextWorkDueNanos);
		}
		for (SourceEntry source : slot._sources.values())
		{
			if (source._signal != null)
			{
				nextDue = Math.min(nextDue, source._expiresAtNanos);
			}
		}
		if (nextDue != Long.MAX_VALUE)
		{
			scheduleDueLocked(slot, Math.max(logicalNow, nextDue));
		}
		else
		{
			removeDueLocked(slot);
		}
	}

	private void scheduleDueLocked(Slot slot, long dueNanos)
	{
		removeDueLocked(slot);
		final DueEntry due = new DueEntry(dueNanos, ++_fairnessSequence, slot._profileId);
		slot._dueEntry = due;
		_dueEntries.add(due);
	}

	private void removeDueLocked(Slot slot)
	{
		if (slot._dueEntry != null)
		{
			_dueEntries.remove(slot._dueEntry);
			slot._dueEntry = null;
		}
	}

	private void ensureNextOpportunityLocked(Slot slot, long logicalNow)
	{
		if (!reserveReadyLocked(slot))
		{
			scheduleDueLocked(slot, logicalNow);
		}
	}

	private boolean reserveReadyLocked(Slot slot)
	{
		if (slot._enqueued)
		{
			return true;
		}
		if (!_readyQueue.offer(slot._profileId))
		{
			return false;
		}
		slot._enqueued = true;
		removeDueLocked(slot);
		_metrics.recordActivityReadyEnqueued();
		return true;
	}

	private void expireSignalsLocked(Slot slot, long logicalNow)
	{
		boolean expired = false;
		for (SourceEntry source : slot._sources.values())
		{
			if ((source._signal != null) && (source._expiresAtNanos <= logicalNow))
			{
				source._signal = null;
				source._expiresAtNanos = 0;
				expired = true;
				_metrics.recordActivitySignalExpired();
			}
		}
		if (expired)
		{
			slot._generation++;
			slot._lastResult = PhantomActivityResultCategory.SIGNAL_EXPIRED;
		}
	}

	private static PhantomActivityState requestedStateLocked(Slot slot)
	{
		PhantomActivityState requested = PhantomActivityState.SLEEPING;
		for (SourceEntry source : slot._sources.values())
		{
			if ((source._signal != null) && source._signal.requiredState().isHigherDetailThan(requested))
			{
				requested = source._signal.requiredState();
			}
		}
		return requested;
	}

	private boolean isTerminalNonMaterializedLocked(Slot slot)
	{
		return !slot._effectiveState.requiresMaterialization() && (slot._retainedFailureKind == RetainedFailureKind.NONE) && !slot._processing && !slot._boundaryInFlight;
	}

	private boolean hasInFlightSlotLocked()
	{
		for (Slot slot : _slots.values())
		{
			if (slot._processing || slot._boundaryInFlight)
			{
				return true;
			}
		}
		return false;
	}

	private void removeSlotLocked(Slot slot)
	{
		if (slot._processing || slot._boundaryInFlight)
		{
			return;
		}
		if (!_slots.remove(slot._profileId, slot))
		{
			return;
		}
		removeDueLocked(slot);
		if (slot._enqueued)
		{
			_readyQueue.remove(slot._profileId);
			slot._enqueued = false;
		}
		slot._lastResult = PhantomActivityResultCategory.UNREGISTERED;
		_metrics.recordActivityUnregistered(slot._effectiveState);
	}

	private PhantomActivitySnapshot snapshotLocked(Slot slot)
	{
		int activeSources = 0;
		for (SourceEntry source : slot._sources.values())
		{
			if (source._signal != null)
			{
				activeSources++;
			}
		}
		return new PhantomActivitySnapshot(slot._profileId, slot._effectiveState, slot._requestedState, slot._transitionStatus, activeSources, slot._enqueued, slot._dueEntry != null, slot._processing, slot._boundaryInFlight, slot._boundaryGeneration, slot._dueEntry != null ? slot._dueEntry._dueNanos : 0, slot._tickSequence, slot._lastResult, slot._lastTransitionNanos);
	}

	private long boundedExponentialBackoff(int attempt)
	{
		long delay = _policy.transitionRetryBaseMillis();
		for (int i = 1; (i < attempt) && (delay < _policy.transitionRetryMaximumMillis()); i++)
		{
			delay = Math.min(_policy.transitionRetryMaximumMillis(), saturatingMultiply(delay, 2));
		}
		return delay;
	}

	private static long millisToNanos(long millis)
	{
		return saturatingMultiply(millis, 1_000_000L);
	}

	private static long saturatingMultiply(long left, long right)
	{
		if ((left == 0) || (right == 0))
		{
			return 0;
		}
		if (left > (Long.MAX_VALUE / right))
		{
			return Long.MAX_VALUE;
		}
		return left * right;
	}

	private static long saturatingAdd(long left, long right)
	{
		if ((right > 0) && (left > (Long.MAX_VALUE - right)))
		{
			return Long.MAX_VALUE;
		}
		if ((right < 0) && (left < (Long.MIN_VALUE - right)))
		{
			return Long.MIN_VALUE;
		}
		return left + right;
	}

	public record RegistrationResult(RegistrationStatus status, PhantomActivitySnapshot snapshot)
	{
	}

	public record UnregisterResult(UnregisterStatus status, PhantomActivitySnapshot snapshot)
	{
	}

	public record SignalResult(SignalStatus status, PhantomActivitySnapshot snapshot)
	{
	}

	public record RetryResult(RetryStatus status, PhantomActivitySnapshot snapshot)
	{
	}

	public record SchedulerSnapshot(SchedulerState state, int registered, int ready, int due, int capacity, int scheduledTaskCount, long pulseSequence, boolean pulseInFlight, PhantomActivityOverloadLevel overloadLevel, PhantomActivityOverloadLevel peakOverloadLevel)
	{
		public static SchedulerSnapshot inactive()
		{
			return new SchedulerSnapshot(SchedulerState.STOPPED, 0, 0, 0, 0, 0, 0, false, PhantomActivityOverloadLevel.NORMAL, PhantomActivityOverloadLevel.NORMAL);
		}

		public boolean running()
		{
			return state == SchedulerState.RUNNING;
		}

		public int queued()
		{
			return ready;
		}
	}

	private static final class Slot
	{
		private final long _profileId;
		private final Map<String, SourceEntry> _sources = new HashMap<>();
		private PhantomActivityState _effectiveState = PhantomActivityState.SLEEPING;
		private PhantomActivityState _requestedState = PhantomActivityState.SLEEPING;
		private PhantomActivityTransitionStatus _transitionStatus = PhantomActivityTransitionStatus.STABLE;
		private PhantomActivityResultCategory _lastResult = PhantomActivityResultCategory.REGISTERED;
		private RetainedFailureKind _retainedFailureKind = RetainedFailureKind.NONE;
		private PhantomActivityState _blockedTarget;
		private boolean _enqueued;
		private boolean _processing;
		private boolean _boundaryInFlight;
		private boolean _unregisterRequested;
		private boolean _explicitRetry;
		private long _generation;
		private long _boundaryGeneration;
		private long _demotionEligibleAtNanos;
		private long _retryDueNanos;
		private int _retryAttempt;
		private long _nextWorkDueNanos;
		private long _tickSequence;
		private long _lastTransitionNanos;
		private DueEntry _dueEntry;

		private Slot(long profileId)
		{
			_profileId = profileId;
		}
	}

	private static final class SourceEntry
	{
		private long _sequence;
		private PhantomRelevanceSignal _signal;
		private long _expiresAtNanos;

		private SourceEntry(long sequence, PhantomRelevanceSignal signal, long expiresAtNanos)
		{
			_sequence = sequence;
			_signal = signal;
			_expiresAtNanos = expiresAtNanos;
		}
	}

	private static final class DueEntry implements Comparable<DueEntry>
	{
		private final long _dueNanos;
		private final long _fairnessSequence;
		private final long _profileId;

		private DueEntry(long dueNanos, long fairnessSequence, long profileId)
		{
			_dueNanos = dueNanos;
			_fairnessSequence = fairnessSequence;
			_profileId = profileId;
		}

		@Override
		public int compareTo(DueEntry other)
		{
			int result = Long.compare(_dueNanos, other._dueNanos);
			if (result == 0)
			{
				result = Long.compare(_fairnessSequence, other._fairnessSequence);
			}
			if (result == 0)
			{
				result = Long.compare(_profileId, other._profileId);
			}
			return result;
		}
	}

	private static final class TransitionPlan
	{
		private final long _profileTargetId;
		private final long _generation;
		private final PhantomActivityState _targetState;
		private final BoundaryAction _action;

		private TransitionPlan(long profileTargetId, long generation, PhantomActivityState targetState, BoundaryAction action)
		{
			_profileTargetId = profileTargetId;
			_generation = generation;
			_targetState = targetState;
			_action = action;
		}
	}
}

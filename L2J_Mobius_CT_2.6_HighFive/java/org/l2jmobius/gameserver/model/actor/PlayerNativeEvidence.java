package org.l2jmobius.gameserver.model.actor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Optional scalar observation. Calls belong after successful native writers, never attempts or aggro. */
public final class PlayerNativeEvidence
{
	public enum Phase { NONE, ROUTE, COMBAT, REGEN, DEATH_RECOVERY }
	public enum UnprovenReason { NONE, OWNER_FAILURE, INVALID_VALUE, INVALID_TARGET, TARGET_CAP, COUNTER_OVERFLOW, TIME_REGRESSION, INVALID_PHASE, PHASE_DEADLINE, COMBAT_ACCOUNTING }
	public record Target(int objectId, int instanceId, long spawnGeneration) { }
	public static final int MAX_TARGETS = 16;
	public static final long MAX_PHASE_NANOS = TimeUnit.SECONDS.toNanos(120);
	private static final ThreadLocal<RewardFrame> REWARD_CONTEXT = new ThreadLocal<>();
	private final int _objectId;
	private final long _epoch;
	private final Map<Target, TargetState> _targets = new LinkedHashMap<>();
	private Target _selected;
	private boolean _overflow;
	private UnprovenReason _firstUnprovenReason = UnprovenReason.NONE;
	private long _firstUnprovenNanos;
	private long _observedNanos;
	private long _sequence;
	private long _damageSequence;
	private long _killSequence;
	private long _rewardSequence;
	private long _targetSequence;
	private long _farmCycleSequence;
	private long _expGained;
	private long _spGained;
	private long _lootSequence;
	private long _lootCount;
	private long _lastProgressNanos;
	private long _usefulProgressNanos;
	private Phase _phase = Phase.NONE;
	private long _phaseSinceNanos;
	private long _phaseDeadlineNanos;
	private boolean _nativeEpisodesRetired;
	private long _regenSinceNanos;
	private CombatEpisode _combatEpisode;
	/** Scalar token; its retired identity is retained by already captured work, never by an unbounded history. */
	public static final class CombatEpisode
	{
		private final long _since;
		private int _pending;
		private boolean _published;
		private boolean _retired;
		private CombatEpisode(long since) { _since = since; }
	}

	public PlayerNativeEvidence(int objectId, long epoch)
	{
		if ((objectId <= 0) || (epoch < 0)) { throw new IllegalArgumentException("NATIVE_EVIDENCE_IDENTITY"); }
		_objectId = objectId;
		_epoch = epoch;
		_observedNanos = epoch;
	}

	public boolean matches(int objectId, long epoch) { return (_objectId == objectId) && (_epoch == epoch); }
	public synchronized void markUnproven() { invalidate(UnprovenReason.OWNER_FAILURE, System.nanoTime()); }
	private void invalidate(UnprovenReason reason, long now)
	{
		if (!_overflow) { _firstUnprovenReason = reason; _firstUnprovenNanos = now; }
		_overflow = true;
	}

	// Capture live time under this monitor so concurrent writers cannot publish reversed timestamps.
	public synchronized void damage(Target target, double actualHpDelta) { damage(target, actualHpDelta, System.nanoTime()); }
	public synchronized void reward(Target target, long actualExp, long actualSp) { reward(target, actualExp, actualSp, System.nanoTime()); }
	public synchronized void killed(Target target) { killed(target, System.nanoTime()); }
	public synchronized void selected(Target target) { selected(target, System.nanoTime()); }
	public synchronized void loot(int itemObjectId, int itemId, long count) { loot(itemObjectId, itemId, count, System.nanoTime()); }
	public synchronized Snapshot snapshot() { return snapshot(System.nanoTime()); }

	public synchronized void damage(Target target, double actualHpDelta, long now)
	{
		if (!Double.isFinite(actualHpDelta)) { invalidate(UnprovenReason.INVALID_VALUE, now); return; }
		if (_overflow || (actualHpDelta <= 0) || !observe(now)) { return; }
		final TargetState state = target(target);
		if (state == null) { return; }
		// A real positive offensive HP write completes the rest gap, even while new
		// casts keep MP below maximum. Repeated regeneration alone never renews it.
		retireRegeneration();
		state._damaged = true;
		completeCycle(target, state);
		_damageSequence = add(_damageSequence, 1);
		event(now, true);
	}

	public synchronized void reward(Target target, long actualExp, long actualSp, long now)
	{
		if ((actualExp < 0) || (actualSp < 0)) { invalidate(UnprovenReason.INVALID_VALUE, now); return; }
		if (_overflow || ((actualExp == 0) && (actualSp == 0)) || !observe(now)) { return; }
		final TargetState state = target(target);
		if (state == null) { return; }
		state._exp |= actualExp > 0;
		state._sp |= actualSp > 0;
		completeCycle(target, state);
		_expGained = add(_expGained, actualExp);
		_spGained = add(_spGained, actualSp);
		_rewardSequence = add(_rewardSequence, 1);
		event(now, state._damaged);
	}

	public synchronized void killed(Target target, long now)
	{
		if (_overflow || !observe(now)) { return; }
		final TargetState state = target(target);
		if ((state == null) || state._killed) { return; }
		state._killed = true;
		completeCycle(target, state);
		_killSequence = add(_killSequence, 1);
		event(now, state._damaged);
	}

	/** Actual NPC death reached under its frozen reward boundary; only proven own HP damage qualifies. */
	public synchronized void killedIfDamaged(Target target)
	{
		final TargetState state = _targets.get(target);
		if ((state != null) && state._damaged) { killed(target); }
	}

	public synchronized void selected(Target target, long now)
	{
		// Clearing/self/rejected targets are filtered by the native bridge and cannot erase a pending cycle.
		if (target == null) { return; }
		if (_overflow) { return; }
		if (!valid(target)) { invalidate(UnprovenReason.INVALID_TARGET, now); return; }
		if (target.equals(_selected) || !observe(now)) { return; }
		final TargetState previous = _targets.get(_selected);
		if (previous != null) { previous._nextSelected = true; completeCycle(_selected, previous); }
		if (target(target) == null) { return; }
		_selected = target;
		_targetSequence = add(_targetSequence, 1);
		event(now, false);
	}

	public synchronized void loot(int itemObjectId, int itemId, long count, long now)
	{
		if (_overflow || (count <= 0)) { return; }
		if ((itemObjectId <= 0) || (itemId <= 0)) { invalidate(UnprovenReason.INVALID_VALUE, now); return; }
		if (!observe(now)) { return; }
		_lootCount = add(_lootCount, count);
		_lootSequence = add(_lootSequence, 1);
		event(now, true);
	}

	public synchronized void phase(Phase phase, long sinceNanos, long deadlineNanos)
	{
		if (phase == null) { invalidate(UnprovenReason.INVALID_PHASE, _observedNanos); return; }
		if (_nativeEpisodesRetired && (phase != Phase.NONE)) { return; }
		if (phase == Phase.NONE)
		{
			if ((sinceNanos != 0) || (deadlineNanos != 0)) { invalidate(UnprovenReason.INVALID_PHASE, _observedNanos); return; }
			_phase = phase;
			_phaseSinceNanos = 0;
			_phaseDeadlineNanos = 0;
			return;
		}
		if ((sinceNanos < _epoch) || (deadlineNanos <= sinceNanos) || ((deadlineNanos - sinceNanos) > MAX_PHASE_NANOS)) { invalidate(UnprovenReason.INVALID_PHASE, _observedNanos); return; }
		if (_phase == phase)
		{
			// Only this episode may shorten its bound; retries or replacements cannot renew it.
			if (_phaseSinceNanos != sinceNanos) { return; }
			_phaseDeadlineNanos = Math.min(_phaseDeadlineNanos, deadlineNanos);
			return;
		}
		if (phasePriority(phase) < phasePriority(_phase)) { return; }
		_phase = phase;
		_phaseSinceNanos = sinceNanos;
		_phaseDeadlineNanos = deadlineNanos;
	}

	/** A stale producer can clear only the exact episode it published. */
	public synchronized boolean clearPhase(Phase expected, long sinceNanos)
	{
		if ((expected == null) || (expected == Phase.NONE) || (_phase != expected) || (_phaseSinceNanos != sinceNanos)) { return false; }
		_phase = Phase.NONE;
		_phaseSinceNanos = 0;
		_phaseDeadlineNanos = 0;
		return true;
	}

	/** Called at the original tagged HP/MP assignment; full generic healing can retire but cannot begin this gap. */
	public synchronized void nativeRegeneration(double hpGain, double mpGain, boolean full, boolean dead)
	{
		if (_nativeEpisodesRetired) { return; }
		if (dead || full) { retireRegeneration(); return; }
		if (!Double.isFinite(hpGain) || !Double.isFinite(mpGain)) { invalidate(UnprovenReason.INVALID_VALUE, System.nanoTime()); return; }
		if ((hpGain <= 0) && (mpGain <= 0)) { return; }
		final long now = System.nanoTime();
		if (_regenSinceNanos == 0) { _regenSinceNanos = now; }
		publishBounded(Phase.REGEN, _regenSinceNanos);
	}

	/** Cancellation hides active attribution without granting another horizon for the same unfinished deficit. */
	public synchronized void pauseRegeneration(boolean full, boolean dead)
	{
		if (full || dead) { retireRegeneration(); }
		else if (_regenSinceNanos != 0) { clearPhase(Phase.REGEN, _regenSinceNanos); }
	}
	private void retireRegeneration()
	{
		if (_regenSinceNanos != 0) { checkLiveHorizon(_regenSinceNanos); clearPhase(Phase.REGEN, _regenSinceNanos); _regenSinceNanos = 0; }
	}

	/** Reserve observation before native publication. Rejected and running-cancelled work follow the real ticket lifecycle. */
	public synchronized CombatEpisode reserveCombat(CombatEpisode inherited)
	{
		if (_nativeEpisodesRetired || ((inherited != null) && ((inherited != _combatEpisode) || inherited._retired))) { return null; }
		if (_combatEpisode == null) { _combatEpisode = new CombatEpisode(System.nanoTime()); }
		if (_combatEpisode._pending >= 1024) { invalidate(UnprovenReason.COMBAT_ACCOUNTING, System.nanoTime()); return null; }
		_combatEpisode._pending++;
		return _combatEpisode;
	}
	public synchronized void combatDamage(CombatEpisode episode, double actualHpDelta)
	{
		if (_nativeEpisodesRetired || (episode == null) || (episode != _combatEpisode) || episode._retired || (episode._pending == 0) || !(actualHpDelta > 0) || !Double.isFinite(actualHpDelta)) { return; }
		episode._published = true;
		publishBounded(Phase.COMBAT, episode._since);
	}
	public synchronized void completeCombat(CombatEpisode episode, boolean passive)
	{
		if ((episode == null) || episode._retired || (episode != _combatEpisode)) { return; }
		if (episode._pending <= 0) { invalidate(UnprovenReason.COMBAT_ACCOUNTING, System.nanoTime()); return; }
		episode._pending--;
		if ((episode._pending == 0) && (!episode._published || passive)) { retireCombat(episode); }
	}
	public synchronized CombatEpisode currentCombatEpisode() { return _combatEpisode; }
	public synchronized void passiveCombat(CombatEpisode expected)
	{
		if ((expected != null) && (_combatEpisode == expected) && (expected._pending == 0)) { retireCombat(expected); }
	}
	private void retireCombat(CombatEpisode episode)
	{
		if (episode._published) { checkLiveHorizon(episode._since); }
		episode._retired = true;
		clearPhase(Phase.COMBAT, episode._since);
		if (_combatEpisode == episode) { _combatEpisode = null; }
	}
	private void publishBounded(Phase phase, long since)
	{
		if ((since < _epoch) || (since > Long.MAX_VALUE - MAX_PHASE_NANOS)) { invalidate(UnprovenReason.INVALID_PHASE, System.nanoTime()); return; }
		checkLiveHorizon(since);
		phase(phase, since, since + MAX_PHASE_NANOS);
	}
	private void checkLiveHorizon(long since)
	{
		if ((since < _epoch) || (since > Long.MAX_VALUE - MAX_PHASE_NANOS) || (since + MAX_PHASE_NANOS <= System.nanoTime())) { invalidate(UnprovenReason.PHASE_DEADLINE, System.nanoTime()); }
	}
	/** Successful permanent native drain only. Sticky UNPROVEN and useful counters are never reset. */
	public synchronized void retireNativeEpisodes()
	{
		if ((_phase != Phase.NONE) && (_phaseDeadlineNanos <= System.nanoTime())) { invalidate(UnprovenReason.PHASE_DEADLINE, System.nanoTime()); }
		_nativeEpisodesRetired = true;
		retireRegeneration();
		if (_combatEpisode != null) { retireCombat(_combatEpisode); }
		_phase = Phase.NONE; _phaseSinceNanos = 0; _phaseDeadlineNanos = 0;
	}

	private static int phasePriority(Phase phase)
	{
		return switch (phase)
		{
			case NONE -> 0;
			case REGEN -> 1;
			case ROUTE -> 2;
			case COMBAT -> 3;
			case DEATH_RECOVERY -> 4;
		};
	}

	public synchronized Snapshot snapshot(long sampleNanos)
	{
		observe(sampleNanos);
		if ((_regenSinceNanos != 0) && ((_regenSinceNanos > Long.MAX_VALUE - MAX_PHASE_NANOS) || (_regenSinceNanos + MAX_PHASE_NANOS <= _observedNanos))) { invalidate(UnprovenReason.PHASE_DEADLINE, _observedNanos); }
		if ((_combatEpisode != null) && _combatEpisode._published && ((_combatEpisode._since > Long.MAX_VALUE - MAX_PHASE_NANOS) || (_combatEpisode._since + MAX_PHASE_NANOS <= _observedNanos))) { invalidate(UnprovenReason.PHASE_DEADLINE, _observedNanos); }
		if ((_phase != Phase.NONE) && ((_phaseSinceNanos > _observedNanos) || (_phaseDeadlineNanos <= _observedNanos))) { invalidate(UnprovenReason.PHASE_DEADLINE, _observedNanos); }
		return new Snapshot(_objectId, _epoch, _observedNanos, _overflow, _sequence, _damageSequence, _killSequence, _rewardSequence, _targetSequence, _farmCycleSequence, _expGained, _spGained, _lootSequence, _lastProgressNanos, _phase, _phaseSinceNanos, _phaseDeadlineNanos, _usefulProgressNanos, _lootCount, _targets.size(), _firstUnprovenReason, _firstUnprovenNanos);
	}

	private boolean observe(long now)
	{
		if ((now < _epoch) || (now < _observedNanos)) { invalidate(UnprovenReason.TIME_REGRESSION, now); return false; }
		_observedNanos = now;
		return true;
	}

	private static boolean valid(Target target)
	{
		return (target != null) && (target.objectId() > 0) && (target.instanceId() >= 0) && (target.spawnGeneration() > 0);
	}

	private TargetState target(Target target)
	{
		if (!valid(target)) { invalidate(UnprovenReason.INVALID_TARGET, _observedNanos); return null; }
		final TargetState existing = _targets.get(target);
		if (existing != null) { return existing; }
		if (_targets.size() >= MAX_TARGETS) { invalidate(UnprovenReason.TARGET_CAP, _observedNanos); return null; }
		final var state = new TargetState();
		_targets.put(target, state);
		return state;
	}

	private long add(long value, long delta)
	{
		if (delta > (Long.MAX_VALUE - value)) { invalidate(UnprovenReason.COUNTER_OVERFLOW, _observedNanos); return Long.MAX_VALUE; }
		return value + delta;
	}

	private void event(long now, boolean useful)
	{
		_sequence = add(_sequence, 1);
		_lastProgressNanos = now;
		if (useful) { _usefulProgressNanos = now; }
	}

	private static final class TargetState
	{
		private boolean _damaged;
		private boolean _killed;
		private boolean _exp;
		private boolean _sp;
		private boolean _nextSelected;
		private boolean _counted;
	}
	private void completeCycle(Target target, TargetState state)
	{
		if (!state._counted && state._nextSelected && state._damaged && state._killed && state._exp && state._sp)
		{
			state._counted = true;
			_farmCycleSequence = add(_farmCycleSequence, 1);
			_targets.remove(target);
		}
	}

	/** The scoped native calculateRewards target survives reward-before-kill ordering, without actor references. */
	public static RewardContext enterReward(Target target)
	{
		if (!valid(target)) { throw new IllegalArgumentException("NATIVE_REWARD_TARGET"); }
		final RewardFrame previous = REWARD_CONTEXT.get();
		final int depth = previous == null ? 1 : previous._depth + 1;
		if (depth > MAX_TARGETS) { throw new IllegalStateException("NATIVE_REWARD_CONTEXT_CAP"); }
		final var frame = new RewardFrame(target, previous, depth);
		REWARD_CONTEXT.set(frame);
		return new RewardContext(frame);
	}

	public static Target currentRewardTarget()
	{
		final RewardFrame frame = REWARD_CONTEXT.get();
		return frame == null ? null : frame._target;
	}

	private record RewardFrame(Target _target, RewardFrame _previous, int _depth) { }

	public static final class RewardContext implements AutoCloseable
	{
		private RewardFrame _frame;
		private RewardContext(RewardFrame frame) { _frame = frame; }
		@Override public void close()
		{
			if (_frame == null) { return; }
			if (REWARD_CONTEXT.get() != _frame) { throw new IllegalStateException("NATIVE_REWARD_CONTEXT_ORDER"); }
			if (_frame._previous == null) { REWARD_CONTEXT.remove(); } else { REWARD_CONTEXT.set(_frame._previous); }
			_frame = null;
		}
	}

	public record Snapshot(int objectId, long epoch, long sampleNanos, boolean overflow, long sequence, long damageSequence, long killSequence, long rewardSequence, long targetSequence, long farmCycleSequence, long expGained, long spGained, long lootSequence, long lastProgressNanos, Phase phase, long phaseSinceNanos, long phaseDeadlineNanos, long usefulProgressNanos, long lootCount, int trackedTargets, UnprovenReason firstUnprovenReason, long firstUnprovenNanos)
	{
		public Map<String, String> scalarMap()
		{
			final Map<String, String> fields = new LinkedHashMap<>();
			fields.put("nativeEvidenceVersion", "1");
			fields.put("nativeEvidenceOwner", "PHANTOM");
			fields.put("nativeEvidenceObjectId", Integer.toString(objectId));
			fields.put("nativeEvidenceEpoch", Long.toString(epoch));
			fields.put("nativeEvidenceSampleNanos", Long.toString(sampleNanos));
			fields.put("nativeEvidenceOverflow", Boolean.toString(overflow));
			fields.put("nativeFirstUnprovenReason", firstUnprovenReason.name());
			fields.put("nativeFirstUnprovenNanos", Long.toString(firstUnprovenNanos));
			fields.put("nativeEvidenceSequence", Long.toString(sequence));
			fields.put("nativeDamageSequence", Long.toString(damageSequence));
			fields.put("nativeKillSequence", Long.toString(killSequence));
			fields.put("nativeRewardSequence", Long.toString(rewardSequence));
			fields.put("nativeTargetSequence", Long.toString(targetSequence));
			fields.put("nativeFarmCycleSequence", Long.toString(farmCycleSequence));
			fields.put("nativeExpGained", Long.toString(expGained));
			fields.put("nativeSpGained", Long.toString(spGained));
			fields.put("nativeLootSequence", Long.toString(lootSequence));
			fields.put("nativeLastProgressNanos", Long.toString(lastProgressNanos));
			fields.put("nativePhase", phase.name());
			fields.put("nativePhaseSinceNanos", Long.toString(phaseSinceNanos));
			fields.put("nativePhaseDeadlineNanos", Long.toString(phaseDeadlineNanos));
			return Collections.unmodifiableMap(fields);
		}
	}
}

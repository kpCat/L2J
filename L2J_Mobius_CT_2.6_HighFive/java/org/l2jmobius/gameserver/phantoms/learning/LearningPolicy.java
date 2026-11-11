/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope;

/** One shared immutable model, with a bounded decision inside the existing combat lease. */
public final class LearningPolicy
{
	public enum Mode { OFF, SHADOW, ON }

	public record Candidate(SelectedSkill skill, float[] features, double mpCost, int castMillis, int itemCost, BooleanSupplier exact)
	{
		public Candidate(SelectedSkill skill, float[] features, double mpCost, int castMillis, int itemCost)
		{
			this(skill, features, mpCost, castMillis, itemCost, () -> true);
		}
	}
	public record Decision(SelectedSkill selected, Candidate observed) { }
	public record Attempt(LearningPolicy policy, Candidate candidate, PhantomCombatRequest request, long generation, long sequence, SelectedSkill baseline, long decisionNanos, List<Candidate> offered)
	{
		public Attempt(LearningPolicy policy, Candidate candidate, PhantomCombatRequest request, long generation, long sequence, SelectedSkill baseline, long decisionNanos)
		{ this(policy, candidate, request, generation, sequence, baseline, decisionNanos, List.of()); }
		public Attempt { offered = List.copyOf(offered); }
	}
	public interface Observer
	{
		void issued(Attempt attempt, Player actor, Monster target);
		void recorded(Attempt attempt, LearningStore.Evidence evidence);
	}
	public record TimingSummary(long count, long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos) { }
	public record Counters(long evaluated, long preferredDifferent, long applied, long overBudget, long unknown) { }

	private static final boolean WITNESS = Boolean.getBoolean("phantom.learning.witness");
	private static final int WITNESS_LOG_LIMIT = 8;
	public static final LearningPolicy OFF = new LearningPolicy(Mode.OFF, 0, null, 4, 0, null);
	private final Mode _mode;
	private final long _version;
	private final NeuralPolicy _model;
	private final int _maxCandidates;
	private final long _seed;
	private final LearningStore.Writer _writer;
	private final BooleanSupplier _catalogCurrent;
	private final LongAdder _evaluated = new LongAdder();
	private final LongAdder _preferredDifferent = new LongAdder();
	private final LongAdder _applied = new LongAdder();
	private final LongAdder _overBudget = new LongAdder();
	private final LongAdder _unknown = new LongAdder();
	private final Witness _soloWitness;
	private final Witness _partyWitness;
	private volatile Observer _observer;

	public LearningPolicy(Mode mode, long version, NeuralPolicy model, int maxCandidates)
	{
		this(mode, version, model, maxCandidates, 0, null);
	}

	LearningPolicy(Mode mode, long version, NeuralPolicy model, int maxCandidates, long seed, LearningStore.Writer writer)
	{
		this(mode, version, model, maxCandidates, seed, writer, () -> true);
	}

	private LearningPolicy(Mode mode, long version, NeuralPolicy model, int maxCandidates, long seed, LearningStore.Writer writer, BooleanSupplier catalogCurrent)
	{
		if (maxCandidates < 2 || maxCandidates > 4 || (model != null && version <= 0))
		{
			throw new IllegalArgumentException("Invalid learning policy");
		}
		_mode = mode;
		_version = version;
		_model = model;
		_maxCandidates = maxCandidates;
		_seed = seed;
		_writer = writer;
		_catalogCurrent = catalogCurrent;
		_soloWitness = WITNESS && mode != Mode.OFF ? new Witness() : null;
		_partyWitness = WITNESS && mode != Mode.OFF ? new Witness() : null;
	}

	public static LearningPolicy configured(Path gameRoot)
	{
		final var config = PhantomPlayersConfig.learningSettings();
		final Mode mode = Mode.valueOf(config.mode());
		if (mode == Mode.OFF || !Boolean.getBoolean("phantom.learning.train"))
		{
			return OFF;
		}
		try
		{
			final var store = new LearningStore(DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_LOGIN, DatabaseConfig.DATABASE_PASSWORD, gameRoot, GeneralConfig.CUSTOM_SKILLS_LOAD);
			final var checkpoint = config.version() == 0 ? null : store.accepted(config.version());
			if ((checkpoint == null) && (mode == Mode.ON || config.version() > 0))
			{
				Logger.getLogger(LearningPolicy.class.getName()).warning("Learning model is unavailable, using legacy combat");
				return OFF;
			}
			return new LearningPolicy(mode, config.version(), checkpoint == null ? null : checkpoint.model(), config.maxCandidates(),
				config.seed(), config.evidence() ? store.new Writer() : null, () -> !SkillData.getInstance().catalogReloaded());
		}
		catch (IOException | SQLException | RuntimeException failure)
		{
			Logger.getLogger(LearningPolicy.class.getName()).warning("Learning initialization rejected, using legacy combat");
			return OFF;
		}
	}

	public boolean active(LearningScope scope)
	{
		return scope != LearningScope.NONE && _mode != Mode.OFF && (_model != null || _writer != null) && _catalogCurrent.getAsBoolean();
	}

	public int maximumCandidates()
	{
		return _maxCandidates;
	}

	public Decision choose(List<Candidate> candidates, SelectedSkill baseline, long sequence)
	{
		if (candidates.size() < 2 || candidates.size() > _maxCandidates)
		{
			return new Decision(baseline, null);
		}
		Candidate selected = null;
		for (Candidate candidate : candidates)
		{
			if (candidate.skill().equals(baseline))
			{
				selected = candidate;
				break;
			}
		}
		if (_model != null)
		{
			final long start = System.nanoTime();
			Candidate best = null;
			float top = Float.NEGATIVE_INFINITY;
			float second = top;
			for (Candidate candidate : candidates)
			{
				final float score = _model.score(candidate.features());
				if (score > top)
				{
					second = top;
					top = score;
					best = candidate;
				}
				else
				{
					second = Math.max(second, score);
				}
			}
			_evaluated.increment();
			if (System.nanoTime() - start > 1_000_000)
			{
				_overBudget.increment();
			}
			else if (best != null && top - second >= .025f)
			{
				if (!best.skill().equals(baseline))
				{
					_preferredDifferent.increment();
				}
				if (_mode == Mode.ON)
				{
					selected = best;
					// Explicit TEST exploration is bounded to one out of 16 eligible decisions.
					if (_writer != null && (_seed != 0) && ((sequence + _seed) & 15) == 0)
					{
						selected = candidates.get(Math.floorMod(Long.hashCode((sequence + _seed) >>> 4), candidates.size()));
					}
				}
			}
		}
		if (_mode == Mode.ON && selected != null && !selected.skill().equals(baseline))
		{
			return new Decision(selected.skill(), selected);
		}
		return new Decision(baseline, selected);
	}

	public boolean collecting()
	{
		return _writer != null;
	}

	public void castIssued()
	{
		_applied.increment();
	}

	long version()
	{
		return _version;
	}

	boolean record(LearningStore.Evidence evidence)
	{
		return _writer.offer(evidence);
	}

	public void observationRejected()
	{
		_unknown.increment();
	}

	String actorKey(long profileId)
	{
		return _writer.actorKey(profileId);
	}

	public Counters counters()
	{
		return new Counters(_evaluated.sum(), _preferredDifferent.sum(), _applied.sum(), _overBudget.sum(), _unknown.sum());
	}

	public boolean witnessing()
	{
		return _soloWitness != null;
	}

	/** Optional bounded observation only; never an authority for executing or crediting actions. */
	public void observer(Observer observer)
	{
		if (observer != null && !Boolean.getBoolean("phantom.learning.train")) { throw new IllegalStateException("Training opt-in required"); }
		_observer = observer;
	}

	void observeIssued(Attempt attempt, Player actor, Monster target)
	{
		final Observer observer = _observer;
		if (observer == null) { return; }
		try { observer.issued(attempt, actor, target); }
		catch (RuntimeException failure) { observationRejected(); }
	}

	void observeRecorded(Attempt attempt, LearningStore.Evidence evidence)
	{
		final Observer observer = _observer;
		if (observer == null) { return; }
		try { observer.recorded(attempt, evidence); }
		catch (RuntimeException failure) { observationRejected(); }
	}

	public TimingSummary timing(LearningScope scope)
	{
		final Witness witness = witness(scope);
		return witness == null ? new TimingSummary(0, 0, 0, 0, 0) : witness.summary();
	}

	public void decisionTiming(LearningScope scope, long nanos)
	{
		final Witness witness = witness(scope);
		if (witness != null)
		{
			witness.timed(nanos);
		}
	}

	boolean recordExactReorder(LearningScope scope)
	{
		final Witness witness = witness(scope);
		return witness != null && witness._exactReordered.incrementAndGet() <= WITNESS_LOG_LIMIT;
	}

	public long exactReordered(LearningScope scope)
	{
		final Witness witness = witness(scope);
		return witness == null ? 0 : witness._exactReordered.get();
	}

	private Witness witness(LearningScope scope)
	{
		return switch (scope)
		{
			case SOLO -> _soloWitness;
			case PARTY -> _partyWitness;
			case NONE -> null;
		};
	}

	public boolean stop()
	{
		if (_writer != null && !_writer.stop())
		{
			return false;
		}
		if (witnessing())
		{
			_soloWitness.report(LearningScope.SOLO, _mode, _version);
			_partyWitness.report(LearningScope.PARTY, _mode, _version);
		}
		return true;
	}

	private static final class Witness
	{
		private final AtomicLong _exactReordered = new AtomicLong();
		private final LongSummaryStatistics _timings = new LongSummaryStatistics();
		private final long[] _samples = new long[2048];
		private boolean _reported;

		synchronized void timed(long nanos)
		{
			_timings.accept(nanos);
			final long count = _timings.getCount();
			if (count <= _samples.length) { _samples[(int) count - 1] = nanos; }
			else
			{
				// A bounded deterministic reservoir, independent of gameplay RNG.
				final long slot = Math.floorMod(Long.rotateLeft(count * 0x9e3779b97f4a7c15L, 23), count);
				if (slot < _samples.length) { _samples[(int) slot] = nanos; }
			}
		}

		synchronized TimingSummary summary()
		{
			final int size = (int) Math.min(_timings.getCount(), _samples.length);
			if (size == 0) { return new TimingSummary(0, 0, 0, 0, 0); }
			final long[] sorted = java.util.Arrays.copyOf(_samples, size);
			java.util.Arrays.sort(sorted);
			return new TimingSummary(_timings.getCount(), sorted[(size - 1) / 2], sorted[(int) Math.ceil(size * .95) - 1],
				sorted[(int) Math.ceil(size * .99) - 1], _timings.getMax());
		}

		synchronized void report(LearningScope scope, Mode mode, long version)
		{
			if (!_reported && _timings.getCount() > 0)
			{
				_reported = true;
				final var timing = summary();
				Logger.getLogger(LearningPolicy.class.getName()).info("LEARNING_DECISION_TIMING scope=" + scope + " mode=" + mode + " version=" + version
					+ " samples=" + _timings.getCount() + " totalNanos=" + _timings.getSum() + " maxNanos=" + _timings.getMax()
					+ " p50Nanos=" + timing.p50Nanos() + " p95Nanos=" + timing.p95Nanos() + " p99Nanos=" + timing.p99Nanos());
			}
		}
	}
}

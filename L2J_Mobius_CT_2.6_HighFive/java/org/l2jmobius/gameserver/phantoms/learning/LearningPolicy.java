/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.SkillData;
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
	public record Attempt(LearningPolicy policy, Candidate candidate, PhantomCombatRequest request, long generation, long sequence) { }
	public record Counters(long evaluated, long preferredDifferent, long applied, long overBudget, long unknown) { }

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

	void record(LearningStore.Evidence evidence)
	{
		_writer.offer(evidence);
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

	public boolean stop()
	{
		return _writer == null || _writer.stop();
	}
}

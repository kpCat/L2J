/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/** TASK131 score and bootstrap for inputs supplied by an independent sealed-evidence verifier. */
public final class TrainingEvaluation131
{
	private static final int BLOCKS_PER_STRATUM = 8;
	private static final int RESAMPLES = 10_000;
	private static final int LOWER_PERCENTILE_INDEX = 249;
	private static final long BOOTSTRAP_SEED = 13120261120L;
	private static final double NONINFERIORITY_MARGIN = -0.05;
	private static final List<Arm> COMPARATORS = List.of(Arm.H, Arm.S, Arm.R);
	private static final Stratum[] STRATA = Stratum.values();
	private static final int ARM_COUNT = Arm.values().length;
	private static final int SIDE_COUNT = Side.values().length;

	public enum Arm { C, H, S, R }
	public enum Side { LEFT, RIGHT }
	public enum Objective { PVE_KILLS, PVP_WIN, ESCAPE }
	public enum Outcome { WIN, LOSS, DRAW, TIMEOUT, ESCAPED }

	public enum Stratum
	{
		SOLO_PVE(true), PARTY_PVE(true), DUEL_PVP(false), GROUP_PVP(false);

		private final boolean _pve;

		Stratum(boolean pve)
		{
			_pve = pve;
		}
	}

	public record Scene(Objective objective, int requiredProgress, int initialRosterSize, double fixedInitialBudget, double deadline)
	{
		public Scene
		{
			require(objective != null && requiredProgress > 0 && initialRosterSize > 0 && positive(fixedInitialBudget) && positive(deadline), "SCENE_DENOMINATOR");
			require(objective != Objective.PVP_WIN || requiredProgress == 1, "WIN_OBJECTIVE");
			require(objective != Objective.ESCAPE || requiredProgress <= initialRosterSize, "ESCAPE_PARTICIPANTS");
		}
	}

	public record Slot(Arm arm, Side side)
	{
		public Slot
		{
			require(arm != null && side != null, "SLOT");
		}
	}

	public record BlockPlan(String id, Stratum stratum, int index, Scene scene, List<Slot> order)
	{
		public BlockPlan
		{
			require(id != null && !id.isBlank() && stratum != null && index >= 0 && index < BLOCKS_PER_STRATUM, "BLOCK_IDENTITY");
			require(scene != null && stratum._pve == (scene.objective() == Objective.PVE_KILLS), "STRATUM_OBJECTIVE");
			require(order != null && order.size() == ARM_COUNT, "BLOCK_ARM_COUNT");
			final var arms = EnumSet.noneOf(Arm.class);
			for (Slot slot : order)
			{
				require(slot != null && arms.add(slot.arm()), "BLOCK_DUPLICATE_ARM");
			}
			order = List.copyOf(order);
		}
	}

	/** Counts and costs belong to the scored arm. Elapsed and the scene deadline use the same unit. */
	public record Episode(String blockId, String episodeId, int position, Slot slot, Outcome outcome,
		int objectiveProgress, int nativeDeaths, double paidConsumedValue, double elapsed)
	{
		public Episode
		{
			require(blockId != null && !blockId.isBlank() && episodeId != null && !episodeId.isBlank(), "EPISODE_IDENTITY");
			require(position >= 0 && position < ARM_COUNT && slot != null && outcome != null, "EPISODE_SLOT_OR_OUTCOME");
			require(objectiveProgress >= 0 && nativeDeaths >= 0 && nonnegative(paidConsumedValue) && nonnegative(elapsed), "EPISODE_COUNTS_OR_COSTS");
		}
	}

	public record Comparison(double mean, double lower, Map<Stratum, Double> meansByStratum)
	{
		public Comparison
		{
			meansByStratum = Collections.unmodifiableMap(new EnumMap<>(meansByStratum));
		}
	}

	public static final class NumericResult
	{
		private final Map<Arm, Comparison> _comparisons;

		private NumericResult(EnumMap<Arm, Comparison> comparisons)
		{
			_comparisons = Collections.unmodifiableMap(new EnumMap<>(comparisons));
		}

		public Map<Arm, Comparison> comparisons()
		{
			return _comparisons;
		}

		/** Native provenance, role coverage and safety checks are required in addition to these thresholds. */
		public boolean meetsNumericThresholds()
		{
			final Comparison heuristic = _comparisons.get(Arm.H);
			return heuristic.lower() > 0 && _comparisons.get(Arm.S).lower() >= NONINFERIORITY_MARGIN
				&& _comparisons.get(Arm.R).lower() >= NONINFERIORITY_MARGIN
				&& heuristic.meansByStratum().values().stream().allMatch(mean -> mean >= NONINFERIORITY_MARGIN);
		}
	}

	private TrainingEvaluation131() { }

	public static double score(Scene scene, Episode episode)
	{
		require(scene != null && episode != null, "SCORE_INPUT");
		final double objective = objectiveScore(scene, episode);
		final double survival = 1 - Math.min(1, (double) episode.nativeDeaths() / scene.initialRosterSize());
		final double resources = 1 - Math.min(1, episode.paidConsumedValue() / scene.fixedInitialBudget());
		final double time = objective == 1 ? Math.max(0, 1 - episode.elapsed() / scene.deadline()) : 0;
		return 0.50 * objective + 0.20 * survival + 0.15 * resources + 0.15 * time;
	}

	private static double objectiveScore(Scene scene, Episode episode)
	{
		final Outcome outcome = episode.outcome();
		final int progress = episode.objectiveProgress();
		final boolean goalReached = progress >= scene.requiredProgress();
		return switch (scene.objective())
		{
			case PVE_KILLS ->
			{
				require(outcome != Outcome.ESCAPED && (outcome == Outcome.WIN) == goalReached, "PVE_OUTCOME");
				yield Math.min(1, (double) progress / scene.requiredProgress());
			}
			case PVP_WIN ->
			{
				require(progress == 0, "PVP_OUTCOME_PROGRESS");
				yield switch (outcome)
				{
					case WIN -> 1;
					case DRAW -> 0.5;
					case LOSS, TIMEOUT -> 0;
					case ESCAPED -> throw new IllegalArgumentException("EVAL131_PVP_OUTCOME");
				};
			}
			case ESCAPE ->
			{
				require(progress <= scene.requiredProgress() && (outcome == Outcome.ESCAPED) == goalReached, "ESCAPE_OUTCOME");
				yield goalReached ? 1 : 0;
			}
		};
	}

	/** Compare a complete bank in the stratum and block-index order of its frozen plan. */
	public static NumericResult compare(List<BlockPlan> plans, List<Episode> episodes)
	{
		final int blockCount = STRATA.length * BLOCKS_PER_STRATUM;
		require(plans != null && plans.size() == blockCount, "PLAN_BLOCK_COUNT");
		require(episodes != null && episodes.size() == blockCount * ARM_COUNT, "BANK_EPISODE_COUNT");
		final var byId = new HashMap<String, MatchedBlock>();
		final var byIndex = new MatchedBlock[STRATA.length][BLOCKS_PER_STRATUM];
		for (BlockPlan plan : plans)
		{
			require(plan != null, "MISSING_BLOCK_PLAN");
			final var block = new MatchedBlock(plan);
			final var blocks = byIndex[plan.stratum().ordinal()];
			require(blocks[plan.index()] == null && byId.putIfAbsent(plan.id(), block) == null, "DUPLICATE_BLOCK");
			blocks[plan.index()] = block;
		}
		validateBalance(byIndex);

		final var episodeIds = new HashSet<String>();
		for (Episode episode : episodes)
		{
			require(episode != null && episodeIds.add(episode.episodeId()), "MISSING_OR_DUPLICATE_EPISODE");
			final MatchedBlock block = byId.get(episode.blockId());
			require(block != null && episode.slot().equals(block.plan.order().get(episode.position())), "EPISODE_DIFFERS_FROM_PLAN");
			final Arm arm = episode.slot().arm();
			require(!block.scores.containsKey(arm), "DUPLICATE_BLOCK_ARM");
			block.scores.put(arm, score(block.plan.scene(), episode));
		}
		return bootstrap(differences(byIndex));
	}

	private static void validateBalance(MatchedBlock[][] byIndex)
	{
		for (MatchedBlock[] stratum : byIndex)
		{
			final int[][] positions = new int[ARM_COUNT][ARM_COUNT];
			final int[][] sides = new int[ARM_COUNT][SIDE_COUNT];
			for (MatchedBlock block : stratum)
			{
				require(block != null, "MISSING_STRATUM_BLOCK");
				for (int position = 0; position < ARM_COUNT; position++)
				{
					final Slot slot = block.plan.order().get(position);
					positions[slot.arm().ordinal()][position]++;
					sides[slot.arm().ordinal()][slot.side().ordinal()]++;
				}
			}
			for (int arm = 0; arm < ARM_COUNT; arm++)
			{
				require(Arrays.stream(positions[arm]).allMatch(count -> count == BLOCKS_PER_STRATUM / ARM_COUNT), "UNBALANCED_ARM_ORDER");
				require(Arrays.stream(sides[arm]).allMatch(count -> count == BLOCKS_PER_STRATUM / SIDE_COUNT), "UNBALANCED_STARTING_SIDE");
			}
		}
	}

	private static double[][][] differences(MatchedBlock[][] byIndex)
	{
		final var differences = new double[STRATA.length][BLOCKS_PER_STRATUM][COMPARATORS.size()];
		for (Stratum stratum : STRATA)
		{
			for (int index = 0; index < BLOCKS_PER_STRATUM; index++)
			{
				final var scores = byIndex[stratum.ordinal()][index].scores;
				require(scores.size() == ARM_COUNT, "INCOMPLETE_BLOCK");
				for (int contrast = 0; contrast < COMPARATORS.size(); contrast++)
				{
					differences[stratum.ordinal()][index][contrast] = scores.get(Arm.C) - scores.get(COMPARATORS.get(contrast));
				}
			}
		}
		return differences;
	}

	private static NumericResult bootstrap(double[][][] differences)
	{
		final int blockCount = STRATA.length * BLOCKS_PER_STRATUM;
		final var rng = new SplittableRandom(BOOTSTRAP_SEED);
		final var resampled = new double[COMPARATORS.size()][RESAMPLES];
		for (int sample = 0; sample < RESAMPLES; sample++)
		{
			for (double[][] stratum : differences)
			{
				for (int draw = 0; draw < BLOCKS_PER_STRATUM; draw++)
				{
					final double[] block = stratum[rng.nextInt(BLOCKS_PER_STRATUM)];
					for (int contrast = 0; contrast < COMPARATORS.size(); contrast++)
					{
						resampled[contrast][sample] += block[contrast];
					}
				}
			}
		}

		final var result = new EnumMap<Arm, Comparison>(Arm.class);
		for (int contrast = 0; contrast < COMPARATORS.size(); contrast++)
		{
			final var perStratum = new EnumMap<Stratum, Double>(Stratum.class);
			double total = 0;
			for (Stratum stratum : STRATA)
			{
				double sum = 0;
				for (double[] block : differences[stratum.ordinal()])
				{
					sum += block[contrast];
				}
				perStratum.put(stratum, sum / BLOCKS_PER_STRATUM);
				total += sum;
			}
			final double[] draws = resampled[contrast];
			Arrays.sort(draws);
			result.put(COMPARATORS.get(contrast), new Comparison(total / blockCount, draws[LOWER_PERCENTILE_INDEX] / blockCount, perStratum));
		}
		return new NumericResult(result);
	}

	private static boolean positive(double value)
	{
		return Double.isFinite(value) && value > 0;
	}

	private static boolean nonnegative(double value)
	{
		return Double.isFinite(value) && value >= 0;
	}

	private static void require(boolean condition, String reason)
	{
		if (!condition)
		{
			throw new IllegalArgumentException("EVAL131_" + reason);
		}
	}

	private static final class MatchedBlock
	{
		private final BlockPlan plan;
		private final Map<Arm, Double> scores = new EnumMap<>(Arm.class);

		private MatchedBlock(BlockPlan plan)
		{
			this.plan = plan;
		}
	}
}

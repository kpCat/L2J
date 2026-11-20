/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Arm;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.BlockPlan;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Episode;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Objective;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Outcome;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Scene;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Side;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Slot;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Stratum;

/** DB-free checks for the TASK131 score and matched-block statistics. */
public final class PhantomLearning131EvaluationChecks
{
	private record Fixtures(List<BlockPlan> plans, List<Episode> episodes) { }

	public static void main(String[] args)
	{
		scoreObjectives();
		matchedBootstrapAndStockControl();
		incompleteAndMisattributedBlocks();
		System.out.println("TRAINING_EVALUATION131_MATH_CHECKS_PASS");
	}

	private static void scoreObjectives()
	{
		final var slot = new Slot(Arm.C, Side.LEFT);
		final var pve = new Scene(Objective.PVE_KILLS, 4, 2, 100, 100);
		final var pvp = new Scene(Objective.PVP_WIN, 1, 2, 100, 100);
		final var escape = new Scene(Objective.ESCAPE, 2, 2, 100, 100);
		near(0.4625, TrainingEvaluation131.score(pve, new Episode("b", "e", 0, slot, Outcome.TIMEOUT, 2, 1, 25, 100)), "PvE timeout retains partial kills");
		near(0.60, TrainingEvaluation131.score(pvp, new Episode("b", "e", 0, slot, Outcome.DRAW, 0, 0, 0, 20)), "PvP draw has no time bonus");
		near(0.35, TrainingEvaluation131.score(pvp, new Episode("b", "e", 0, slot, Outcome.TIMEOUT, 0, 0, 0, 100)), "PvP timeout is not a draw");
		near(0.8875, TrainingEvaluation131.score(escape, new Episode("b", "e", 0, slot, Outcome.ESCAPED, 2, 0, 50, 25)), "escape credits the declared objective");
		near(0.35, TrainingEvaluation131.score(escape, new Episode("b", "e", 0, slot, Outcome.WIN, 1, 0, 0, 10)), "a combat win does not complete the escape objective");
		refuse(() -> TrainingEvaluation131.score(pve, new Episode("b", "e", 0, slot, Outcome.WIN, 2, 0, 0, 25)), "unsubstantiated objective gain");
		refuse(() -> new Scene(Objective.PVE_KILLS, 0, 2, 100, 100), "invalid kill denominator");
		refuse(() -> new Scene(Objective.PVE_KILLS, 1, 0, 100, 100), "invalid roster denominator");
		refuse(() -> new Scene(Objective.PVP_WIN, 1, 2, 100, Double.POSITIVE_INFINITY), "nonfinite deadline");
		refuse(() -> new Scene(Objective.PVP_WIN, 1, 2, 0, 100), "invalid budget denominator");
		refuse(() -> TrainingEvaluation131.score(pvp, new Episode("b", "e", 0, slot, Outcome.LOSS, 0, 0, Double.NaN, 25)), "nonfinite resource value");
	}

	private static void matchedBootstrapAndStockControl()
	{
		final var stock = fixtures(false, false);
		final var nullResult = TrainingEvaluation131.compare(stock.plans(), stock.episodes());
		check(!nullResult.meetsNumericThresholds(), "identical stock policies cannot show improvement");
		for (var comparison : nullResult.comparisons().values())
		{
			near(0, comparison.mean(), "stock mean");
			near(0, comparison.lower(), "stock lower bound");
		}

		final var biased = fixtures(true, false);
		final var result = TrainingEvaluation131.compare(biased.plans(), biased.episodes());
		check(result.meetsNumericThresholds(), "positive matched contrast");
		final var comparison = result.comparisons().get(Arm.H);
		near(0.0625, comparison.mean(), "each of 32 blocks is one unit");
		near(0.015625, comparison.lower(), "2.5th percentile of within-stratum draws");
		near(0.25, comparison.meansByStratum().get(Stratum.SOLO_PVE), "effect belongs to solo PvE only");
		near(0, comparison.meansByStratum().get(Stratum.GROUP_PVP), "no effect in group PvP");
		for (Arm stronger : List.of(Arm.S, Arm.R))
		{
			final var rows = biased.episodes().stream().map(row -> (row.slot().arm() == stronger && row.blockId().startsWith("GROUP_PVP"))
				? new Episode(row.blockId(), row.episodeId(), row.position(), row.slot(), Outcome.WIN, 0, 0, 0, 100) : row).toList();
			final var inferior = TrainingEvaluation131.compare(biased.plans(), rows);
			near(-0.109375, inferior.comparisons().get(stronger).lower(), "lower bound for independent arm " + stronger);
			check(!inferior.meetsNumericThresholds(), "C must also be noninferior to " + stronger);
		}
		final var switched = fixtures(true, true);
		Collections.reverse(switched.plans());
		Collections.reverse(switched.episodes());
		check(result.comparisons().equals(TrainingEvaluation131.compare(switched.plans(), switched.episodes()).comparisons()),
			"relabelled sides and shuffled input cannot change paired differences");
	}

	private static void incompleteAndMisattributedBlocks()
	{
		final var fixtures = fixtures(true, false);
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans().subList(0, 31), fixtures.episodes()), "missing block");
		final var rows = new ArrayList<>(fixtures.episodes());
		final var first = rows.removeFirst();
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans(), rows), "missing arm");
		final var other = rows.getFirst();
		rows.add(new Episode(first.blockId(), other.episodeId(), first.position(), first.slot(), first.outcome(), first.objectiveProgress(), 0, 0, 100));
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans(), rows), "duplicate physical episode");
		rows.set(rows.size() - 1, new Episode(first.blockId(), first.episodeId(), first.position(), new Slot(first.slot().arm(), Side.RIGHT), first.outcome(), first.objectiveProgress(), 0, 0, 100));
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans(), rows), "side differs from frozen plan");
		rows.set(rows.size() - 1, new Episode(first.blockId(), first.episodeId(), first.position(), new Slot(other.slot().arm(), first.slot().side()), first.outcome(), first.objectiveProgress(), 0, 0, 100));
		refuse(() -> TrainingEvaluation131.compare(fixtures.plans(), rows), "arm order differs from frozen plan");
	}

	private static Fixtures fixtures(boolean improvement, boolean flipSides)
	{
		final List<BlockPlan> plans = new ArrayList<>();
		final List<Episode> episodes = new ArrayList<>();
		for (Stratum stratum : Stratum.values())
		{
			final var objective = (stratum == Stratum.SOLO_PVE || stratum == Stratum.PARTY_PVE) ? Objective.PVE_KILLS : Objective.PVP_WIN;
			final int rosterSize = (stratum == Stratum.SOLO_PVE || stratum == Stratum.DUEL_PVP) ? 1 : 2;
			final var scene = new Scene(objective, 1, rosterSize, 100, 100);
			for (int index = 0; index < 8; index++)
			{
				final String blockId = stratum.name() + "-" + index;
				final List<Slot> order = new ArrayList<>();
				for (int position = 0; position < 4; position++)
				{
					final Arm arm = Arm.values()[(position + index) % 4];
					final Side side = ((index < 4) != flipSides) ? Side.LEFT : Side.RIGHT;
					final var slot = new Slot(arm, side);
					order.add(slot);
					final boolean win = improvement && stratum == Stratum.SOLO_PVE && index < 4 && arm == Arm.C;
					episodes.add(new Episode(blockId, blockId + "-" + arm, position, slot, win ? Outcome.WIN : Outcome.LOSS, win ? 1 : 0, 0, 0, 100));
				}
				plans.add(new BlockPlan(blockId, stratum, index, scene, order));
			}
		}
		return new Fixtures(plans, episodes);
	}

	private static void near(double expected, double actual, String reason)
	{
		check(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-12, reason + ": expected " + expected + " got " + actual);
	}

	private static void refuse(Runnable action, String reason)
	{
		try
		{
			action.run();
		}
		catch (IllegalArgumentException expected)
		{
			return;
		}
		throw new AssertionError("Expected refusal: " + reason);
	}

	private static void check(boolean condition, String reason)
	{
		if (!condition)
		{
			throw new AssertionError(reason);
		}
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.*;

/** Pre-CUT relative workload, not a DPS model, win probability or relaxation of native eligibility. */
public final class PhantomEvaluationCurriculumV2
{
	public record Facts(String key, int levelGap, double npcHp, double npcPDef, double npcMDef, double npcPAtk, double npcMAtk,
		double rosterHp, double rosterMp, int alternatives, double minimumActionMp, String sourceFactsSha)
	{
		public Facts
		{
			PhantomEvaluationOutcomeV2.sha(key); PhantomEvaluationOutcomeV2.sha(sourceFactsSha);
			for (double value : new double[] { npcHp, npcPDef, npcMDef, npcPAtk, npcMAtk, rosterHp, rosterMp, minimumActionMp })
			{ if (!Double.isFinite(value) || value < 0) { throw new IllegalArgumentException("EVAL012_CURRICULUM_FACT"); } }
			if (npcHp <= 0 || rosterHp <= 0 || alternatives < 2) { throw new IllegalArgumentException("EVAL012_CURRICULUM_CAPABILITY"); }
		}
		String burden() { return levelGap + "/" + npcHp + "/" + npcPDef + "/" + npcMDef + "/" + npcPAtk + "/" + npcMAtk + "/" + rosterHp + "/" + rosterMp + "/" + alternatives + "/" + minimumActionMp; }
		Map<String, Object> json(String stratum)
		{ return PhantomEvaluationOutcomeV2.fields("key", key, "stratum", stratum, "burdenVector", burden(), "levelGap", levelGap, "npcHp", npcHp, "npcPDef", npcPDef, "npcMDef", npcMDef, "npcPAtk", npcPAtk, "npcMAtk", npcMAtk,
			"rosterHp", rosterHp, "rosterMp", rosterMp, "targetBoundAlternatives", alternatives, "minimumActionMp", minimumActionMp, "sourceFactsSha", sourceFactsSha, "winProbability", null, "dps", null, "mortalityRisk", null); }
	}
	private static final Comparator<Facts> BURDEN = Comparator.comparingInt(Facts::levelGap).thenComparingDouble(Facts::npcHp).thenComparingDouble(Facts::npcMDef)
		.thenComparingDouble(Facts::npcPDef).thenComparingDouble(Facts::npcMAtk).thenComparingDouble(Facts::npcPAtk)
		.thenComparing(Comparator.comparingDouble(Facts::rosterHp).reversed()).thenComparing(Comparator.comparingDouble(Facts::rosterMp).reversed())
		.thenComparing(Comparator.comparingInt(Facts::alternatives).reversed()).thenComparingDouble(Facts::minimumActionMp);
	static Map<String, String> classify(List<Facts> facts)
	{
		if (facts.size() > 12 || facts.stream().map(Facts::key).distinct().count() != facts.size()) { throw new IllegalArgumentException("EVAL012_CURRICULUM_BOUND"); }
		final var levels = facts.stream().sorted(BURDEN).map(Facts::burden).distinct().toList(); final var result = new TreeMap<String, String>();
		for (var fact : facts)
		{
			final int index = levels.indexOf(fact.burden()), size = levels.size();
			result.put(fact.key(), size < 2 ? "UNCLASSIFIED_SINGLE_BURDEN" : index == 0 ? "LOWER_PRECUT_BURDEN" : index == size - 1 ? "HIGHER_PRECUT_BURDEN" : "MIDDLE_PRECUT_BURDEN");
		}
		return Map.copyOf(result);
	}
	static List<Facts> order(List<Facts> facts, int directionOrdinal, long seed)
	{
		final var tiers = classify(facts); final String wanted = directionOrdinal % 2 == 0 ? "LOWER_PRECUT_BURDEN" : "HIGHER_PRECUT_BURDEN";
		return facts.stream().filter(f -> tiers.get(f.key()).equals(wanted)).sorted(Comparator.comparing(f -> PhantomTrainingScenarios.hash(seed + "/curriculum012/" + f.key()))).toList();
	}
	static String catalog(List<Facts> facts, int ordinal, long seed)
	{
		final var tiers = classify(facts);
		return PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_CURRICULUM_V2", "seed", seed, "directionOrdinal", ordinal,
			"requestedStratum", ordinal % 2 == 0 ? "LOWER_PRECUT_BURDEN" : "HIGHER_PRECUT_BURDEN", "relativeToBoundedLawfulList", true, "usesOutcomesOrNeuralScores", false,
			"candidateFacts", facts.stream().sorted(Comparator.comparing(Facts::key)).map(f -> f.json(tiers.get(f.key()))).toList(), "preCutOnly", true));
	}
	static boolean selectedDiverse(List<String> burdens) { return burdens.stream().distinct().count() >= 2; }
	private PhantomEvaluationCurriculumV2() { }
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.HashSet;
import java.util.List;

/** Versioned native-effect envelope; it grants no execution authority and never converts V1 vectors. */
public record TacticalReceiptV3(String eventKey, Attempt attempt, double beforeHp, double afterHp, double actualHp,
	double actualMp, long elapsedNanos, float reward, HealEvidenceV3.SourceOwner owner)
{
	public record Attempt(TacticalObservationV3 observation, List<LegalActionV3> offered, LegalActionV3 selected,
		LegalActionV3 baseline, long modelVersion)
	{
		public Attempt
		{
			offered = List.copyOf(offered);
			if (observation == null || observation.capturedNanos() <= 0 || modelVersion < 0 || offered.size() < 2 || offered.size() > 16
				|| !offered.contains(selected) || !offered.contains(baseline) || new HashSet<>(offered).size() != offered.size())
			{ throw new IllegalArgumentException("Incomplete native V3 choice"); }
			for (var action : offered)
			{
				if (!action.trainable() || action.family() != selected.family() || action.criticalSafety() || action.skillId() <= 0
					|| action.skillLevel() <= 0 || action.targetEpoch() <= 0 || action.generation() != observation.decisionId()
					|| !action.owner().equals(selected.owner()))
				{ throw new IllegalArgumentException("Unlawful or stale V3 alternative"); }
				action.features(observation);
			}
		}
	}
	public TacticalReceiptV3
	{
		if (eventKey == null || !eventKey.matches("[a-f0-9]{64}") || attempt == null || owner == null
			|| !Double.isFinite(beforeHp) || !Double.isFinite(afterHp) || beforeHp < 0 || afterHp < 0
			|| !Double.isFinite(actualHp) || actualHp <= 0 || !Double.isFinite(actualMp) || actualMp <= 0 || elapsedNanos <= 0 || !Float.isFinite(reward))
		{ throw new IllegalArgumentException("Unproven native V3 effect"); }
		final var maximum = attempt.observation().entity(attempt.selected().targetObjectId()).fact(TacticalObservationV3.Field.MAX_HP);
		final boolean heal = attempt.selected().family() == LegalActionV3.Family.HEAL;
		if (!maximum.known() || maximum.value() <= 0 || beforeHp > maximum.value() + 1e-6 || afterHp > maximum.value() + 1e-6
			|| Math.abs(actualHp - (heal ? afterHp - beforeHp : beforeHp - afterHp)) > 1e-6
			|| Math.abs(reward - reward(actualHp, maximum.value(), actualMp, elapsedNanos)) > 1e-6)
		{ throw new IllegalArgumentException("Native V3 effect/reward provenance mismatch"); }
	}
	public String origin() { return attempt.selected().family() == LegalActionV3.Family.HEAL ? "NATIVE_HEAL" : "NATIVE_DAMAGE"; }
	public static TacticalReceiptV3 fromHeal(HealEvidenceV3.Receipt receipt)
	{
		final var a = receipt.attempt();
		return new TacticalReceiptV3(receipt.eventKey(), new Attempt(a.observation(), a.offered(), a.selected(), a.baseline(), a.modelVersion()),
			receipt.beforeHp(), receipt.afterHp(), receipt.actualHp(), receipt.actualMp(), receipt.elapsedNanos(), receipt.reward(), receipt.owner());
	}
	/** Both families use useful HP fraction minus exact native MP/time. Terminal success is a separate fact. */
	public static float reward(double hp, double maximumHp, double mp, long nanos)
	{
		return HealEvidenceV3.reward(hp, maximumHp, mp, nanos);
	}
}

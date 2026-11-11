/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;

/** Fabricated contract inputs never become native SQL; native lawfulness is tested in GameServer. */
public final class Learning004DamageChecks
{
	public static void main(String[] args)
	{
		final var facts = Map.of(Field.HP, Fact.known(.8), Field.MAX_HP, Fact.known(2000), Field.LEVEL, Fact.known(77));
		final var self = new Entity(Role.SELF, 100, 10, 0, 94, facts, List.of(), List.of(), List.of(), List.of());
		final var enemy = new Entity(Role.ENEMY, 200, 20, 0, 0, facts, List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(7, 123, 1000, 100, 10, "solo:100", List.of(self, enemy));
		final var a = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1000, 100, 10, 7, 200, 20, "solo:100", 1230, 28, 60, 4000, 900, false, true);
		final var b = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1000, 100, 10, 7, 200, 20, "solo:100", 1339, 1, 70, 5000, 900, false, true);
		final var attempt = new TacticalReceiptV3.Attempt(observation, List.of(a, b), a, b, 2);
		final float reward = TacticalReceiptV3.reward(500, 2000, 60, 4_000_000_000L);
		check(Math.abs(reward - .15f) < 1e-6, "DAMAGE reward scale differs from useful fractional HEAL scale");
		final var receipt = new TacticalReceiptV3("a".repeat(64), attempt, 1600, 1100, 500, 60, 4_000_000_000L, reward,
			new HealEvidenceV3.SourceOwner(40, "CONTRACT_FIXTURE"));
		final var row = new TacticalStoreV3.Row("TRAIN", "004.damage.solo", receipt);
		final byte[] encoded = TacticalStoreV3.encode(row);
		final var restored = TacticalStoreV3.decode(encoded);
		check(restored.receipt().attempt().selected().family() == LegalActionV3.Family.DAMAGE && restored.receipt().actualHp() == 500
			&& restored.receipt().actualMp() == 60 && Arrays.equals(encoded, TacticalStoreV3.encode(restored)), "DAMAGE provenance/codec changed");
		reject(() -> new TacticalReceiptV3.Attempt(observation, List.of(a, a), a, a, 2), "duplicate alternatives accepted");
		reject(() -> new TacticalReceiptV3.Attempt(observation, List.of(a.withFamily(LegalActionV3.Family.HEAL), b), b, b, 2), "mixed owner families accepted");
		reject(() -> new TacticalReceiptV3("b".repeat(64), attempt, 1600, 1600, 0, 60, 1, 0, new HealEvidenceV3.SourceOwner(40, "CONTRACT_FIXTURE")), "ISSUE without exact effect got reward");
		reject(() -> new TacticalReceiptV3("b".repeat(64), attempt, 1600, 1100, 1500, 60, 4_000_000_000L,
			TacticalReceiptV3.reward(1500, 2000, 60, 4_000_000_000L), new HealEvidenceV3.SourceOwner(40, "CONTRACT_FIXTURE")), "DAMAGE label differs from its exact HP write");
		System.out.println("LEARNING004_DAMAGE codec=PASS provenance=PASS rewardScale=PASS illegalAlternatives=REJECTED native=NOT_RUN");
	}
	private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
	private static void reject(Runnable action, String message)
	{
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;

/** Failed/unknown native-shaped fixtures remain non-trainable and never enter SQL. */
public final class Learning004FailureChecks
{
	public static void main(String[] args)
	{
		final var facts = Map.of(Field.HP, Fact.known(.8), Field.MAX_HP, Fact.known(2000), Field.LEVEL, Fact.known(77));
		final var self = new Entity(Role.SELF, 100, 10, 0, 94, facts, List.of(), List.of(), List.of(), List.of());
		final var target = new Entity(Role.ENEMY, 200, 20, 0, 0, facts, List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(7, 123, 1000, 100, 10, "solo:100", List.of(self, target));
		final var a = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1000, 100, 10, 7, 200, 20, "solo:100", 1230, 28, 60, 4000, 900, false, true);
		final var b = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 1000, 100, 10, 7, 200, 20, "solo:100", 1339, 1, 70, 5000, 900, false, true);
		final var choice = new TacticalReceiptV3.Attempt(observation, List.of(a,b), a, b, 2);
		final var failed = new TacticalFailureV3("a".repeat(64), choice, "NO_EXACT_HP", true, 0.0, 60.0, 4_000_000_000L, new HealEvidenceV3.SourceOwner(40, "CONTRACT_FIXTURE"));
		final var restored = TacticalFailureV3.restore(failed.bytes(), failed.sha256());
		check(Arrays.equals(failed.bytes(), restored.bytes()) && restored.json().contains("\"reward\":null") && restored.json().contains("\"trainable\":false"), "failure fabricated a zero success label");
		reject(() -> TacticalStoreV3.decode(failed.bytes()), "failure decoded as a trainable effect");
		final byte[] changed = failed.bytes(); changed[changed.length-1] ^= 1;
		reject(() -> TacticalFailureV3.restore(changed, failed.sha256()), "forged failure provenance accepted");
		final var unknown = new TacticalFailureV3("b".repeat(64), choice, "CAST_CANCELLED", false, null, null, 1, null);
		check(unknown.json().contains("\"observedHp\":null") && !unknown.nativeCompleted(), "cancelled issue became completed outcome");
		final var window = new TacticalReplayV3.FailureWindow();
		window.offer(new TacticalReplayV3.FailureRow("TRAIN", "004|spot=farmA|scene=first", failed), "FAILED");
		window.offer(new TacticalReplayV3.FailureRow("TUNING", "004|spot=farmB|scene=second", unknown), "INCOMPLETE");
		check(window.samples().size() == 2 && window.samples().stream().map(TacticalReplayV3.ContextSample::spot).distinct().count() == 2, "failure outcome/spot replay collapsed");
		final byte[] context = TacticalReplayV3.packContexts(window.samples(), "c".repeat(64));
		check(TacticalReplayV3.unpackContexts(context, "c".repeat(64)).equals(window.samples()), "replay selected keys changed after cold restore");
		reject(() -> TacticalReplayV3.unpackContexts(context, "d".repeat(64)), "failure replay attached to different frozen dataset");
		System.out.println("LEARNING004_FAILURE codec=PASS reward=NULL trainable=FALSE unknown=PRESERVED outcomeSpotReplay=PASS DB_CONNECTIONS=0");
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
	private static void reject(Runnable action, String message)
	{
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}
}

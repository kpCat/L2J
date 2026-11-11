/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.List;

/** Contract cases, not native encounters. */
public final class Learning004EpisodeChecks
{
	public static void main(String[] args) throws Exception
	{
		final var alive = new TacticalEpisodeV3.Actor(1, 100, 10, 94, 77, 900, 800, false, 0L, 0L);
		final var killed = new TacticalEpisodeV3.Kill(200, 21798, 20, 0, 100, 1);
		final var incomplete = new TacticalEpisodeV3("a".repeat(64), "b".repeat(32), "SOLO", 1, 10, "TIMEOUT", List.of(alive), List.of(), List.of("c".repeat(64)), 500, 0, 60, 0);
		check(incomplete.outcome().equals("INCOMPLETE") && !incomplete.json().contains("\"confirmedKill\":true"), "one hit/timeout became victory");
		final var complete = new TacticalEpisodeV3("d".repeat(64), "b".repeat(32), "SOLO", 1, 10, "NATIVE_KILL", List.of(alive), List.of(killed), List.of("c".repeat(64)), 500, 0, 60, 0);
		check(complete.outcome().equals("COMPLETED") && complete.json().contains("\"confirmedKill\":true") && complete.json().contains("\"loot\":null"), "actual terminal receipt or UNKNOWN lost");
		final var dead = new TacticalEpisodeV3.Actor(1, 100, 10, 94, 77, 0, 800, true, null, null);
		final var failed = new TacticalEpisodeV3("e".repeat(64), "b".repeat(32), "PARTY", 1, 10, "NATIVE_ACTOR_DIED", List.of(dead), List.of(killed), List.of(), 0, 0, 0, 1);
		check(failed.outcome().equals("FAILED"), "party death hidden by target kill");
		final TacticalEpisodeV3.Actor[] finalState = {alive};
		final var drained = TacticalEpisodeV3.afterNativeDrain(() -> finalState[0] = dead, () ->
			new TacticalEpisodeV3("e".repeat(64), "b".repeat(32), "PARTY", 1, 11, "NATIVE_KILL", List.of(finalState[0]), List.of(killed), List.of(), 0, 0, 0, 1));
		check(drained.actors().getFirst().hp() == 0 && drained.outcome().equals("FAILED"), "settlement captured before native drain finished");
		reject(() -> new TacticalEpisodeV3("f".repeat(64), "b".repeat(32), "SOLO", 1, 10, "NATIVE_KILL", List.of(alive), List.of(), List.of(), 0, 0, 0, 0), "fabricated native kill accepted");
		System.out.println("LEARNING004_EPISODE timeout=INCOMPLETE nativeReceipt=REQUIRED death=FAILED unknown=PRESERVED native=NOT_RUN");
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
	private static void reject(Runnable action, String message)
	{
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}
}

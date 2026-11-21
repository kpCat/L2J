/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.decision;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MemoryStore;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MutableClock;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.Ownership;

public final class PhantomOwnerPlay002R1Checks
{
	public static void main(String[] args)
	{
		final var catalog = PhantomPopulationCatalog.load(Path.of(args[0], "dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		final var population = new MemoryStore(catalog.hash());
		population.seed(1, PhantomPopulationState.State.SHELL, PhantomPopulationState.CreationStage.SHELL_DURABLE, 1);
		final var goals = new StagedGoals();
		final var firstOwner = new Ownership();
		final var first = manager(catalog, population, goals, firstOwner);
		PhantomAssertions.assertTrue(first.start(), "A pending bootstrap Goal must not abort startup.");
		for (int i = 0; i < 8; i++) { first.onPulse(); }
		PhantomAssertions.assertEquals(2, goals.pending.size(), "Restored and newly created shells must stage a bootstrap Goal.");
		PhantomAssertions.assertEquals(0, firstOwner.registeredCount(), "Staged intent must not authorize registration.");
		first.beginStop();
		first.finishStop();

		final var restartedOwner = new Ownership();
		final var restarted = manager(catalog, population, goals, restartedOwner);
		PhantomAssertions.assertTrue(restarted.start(), "Restart with two pending publications must succeed.");
		for (int i = 0; i < 8; i++) { restarted.onPulse(); }
		PhantomAssertions.assertEquals(0, restartedOwner.registeredCount(), "Restart must wait for the durable Goal.");
		final PhantomGoal winner = goals.commit(1);
		goals.pending.remove(2L);
		goals.foreignPublication = 2;
		for (int i = 0; i < 32; i++) { restarted.onPulse(); }
		PhantomAssertions.assertTrue(restartedOwner.registered(1), "Registration must resume after publication commits.");
		PhantomAssertions.assertTrue(!restartedOwner.registered(2), "A competing request must not authorize registration.");
		PhantomAssertions.assertEquals(winner, goals.load(1).orElseThrow().goal(), "Registration retries must keep the committed Goal.");
		PhantomAssertions.assertEquals(2, goals.staged, "Same-profile retries and restart must not stage duplicate Goals.");
		restarted.beginStop();
		restarted.finishStop();
		System.out.println("OWNER_PLAY_002R1_BOOTSTRAP_PASS");
	}

	private static PhantomPopulationManager manager(PhantomPopulationCatalog catalog, MemoryStore population, PhantomGoalStore goals, Ownership ownership)
	{
		return new PhantomPopulationManager(population, catalog, goals, ownership, new MutableClock(Instant.parse("2026-07-27T20:00:00Z")), ZoneOffset.UTC, 2, 0, 4, 2, 2, 16);
	}

	private static final class StagedGoals implements PhantomGoalStore
	{
		private final Map<Long, PhantomGoal> pending = new HashMap<>();
		private final Map<Long, StoredGoal> committed = new HashMap<>();
		private int staged;
		private long foreignPublication;

		@Override
		public boolean profileExists(long profileId) { return true; }

		@Override
		public Optional<StoredGoal> load(long profileId) { return Optional.ofNullable(committed.get(profileId)); }

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			if (profileId == foreignPublication) { throw new ConcurrentModificationException("Another Goal publication is pending."); }
			final var previous = pending.putIfAbsent(profileId, goal);
			if (previous == null) { staged++; }
			else { PhantomAssertions.assertEquals(previous, goal, "Retry changed the staged bootstrap Goal."); }
			throw new PhantomGoalStateStore.PublicationPending();
		}

		private PhantomGoal commit(long profileId)
		{
			final var goal = pending.remove(profileId);
			committed.put(profileId, new StoredGoal(goal, 0));
			return goal;
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal) { throw new AssertionError("Unexpected replacement."); }

		@Override
		public void delete(long profileId, long expectedRowVersion) { throw new AssertionError("Unexpected deletion."); }
	}
}

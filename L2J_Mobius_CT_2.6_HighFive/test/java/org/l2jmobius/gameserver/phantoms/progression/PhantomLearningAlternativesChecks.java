/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;

/** Pure selection contract: approach availability is input, never a simulated native path verdict. */
public final class PhantomLearningAlternativesChecks
{
	private PhantomLearningAlternativesChecks() { }

	public static void assertContract(PhantomTestContext context)
	{
		int checked = 0;
		for (int size = 0; size <= 3; size++)
		{
			final List<SpawnFact> trainers = new ArrayList<>();
			for (int index = 0; index < size; index++) { trainers.add(new SpawnFact(30000 + index, index, 0, 100 * index, 0, 0, 1, 0, SpawnPointKind.EXACT, null, null, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)); }
			for (int mask = 0; mask < (1 << size); mask++)
			{
				for (int first = 0; first <= size; first++)
				{
					final int availability = mask;
					final List<Integer> queried = new ArrayList<>();
					final var selected = L2jPhantomLearningJourney.trainerApproach(trainers, first, trainer ->
					{
						final int index = trainer.spawnOrdinal(); queried.add(index);
						return (availability & (1 << index)) == 0 ? List.of() : List.of(new PhantomNavigationPoint(trainer.x(), trainer.y(), trainer.z(), trainer.instanceId()));
					});
					int expected = first;
					while (expected < size && (availability & (1 << expected)) == 0) { expected++; }
					PhantomAssertions.assertEquals(expected == size ? -1 : expected, selected == null ? -1 : selected.index(), "Empty Trainer approaches prevented a later bounded alternative: size=" + size + "; first=" + first + "; mask=" + mask);
					final List<Integer> expectedQueries = new ArrayList<>();
					for (int index = first; index < size && index <= expected; index++) { expectedQueries.add(index); }
					PhantomAssertions.assertEquals(expectedQueries, queried, "Trainer alternatives exceeded or skipped the ordered bounded pool.");
					if (selected != null && first > 0)
					{
						final var point = new PhantomNavigationPoint(0, 0, 0, 0);
						final var request = new L2jPhantomLearningJourney.Request(0, 0, 3, 1, point);
						final long deadline = 123_456_789L;
						final var before = new L2jPhantomLearningJourney.Travel(null, 59, 2, request, false, trainers.get(first - 1).npcId(), List.of(point), 0, deadline, 77, List.of(point), 0, trainers, first - 1, 1, null);
						final var after = before.nextTrainer(selected);
						PhantomAssertions.assertEquals(deadline, after.deadlineNanos(), "Trainer fallback renewed the original deadline.");
						PhantomAssertions.assertTrue(after.trainerIndex() == expected && after.trainerNpcId() == trainers.get(expected).npcId() && after.destinations().equals(selected.points()), "Trainer index/NPC/approaches lost their exact association.");
						PhantomAssertions.assertTrue(after.actor() == before.actor() && after.request() == request && after.goalId() == before.goalId() && after.goalRevision() == before.goalRevision() && after.issuedWalk() == before.issuedWalk(), "Trainer fallback changed actor/intent/walk authority.");
						PhantomAssertions.assertTrue(after.requestId() == 0 && after.waypoints().isEmpty() && after.destinationIndex() == 0 && after.segmentAttempt() == 0, "Trainer fallback retained a stale route.");
					}
					checked++;
				}
			}
		}
		context.record("learningAlternatives.exhaustiveAvailabilityInputs", checked);
	}
}

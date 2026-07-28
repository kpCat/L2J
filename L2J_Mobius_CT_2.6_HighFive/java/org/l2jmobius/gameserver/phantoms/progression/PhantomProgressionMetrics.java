/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.concurrent.atomic.LongAdder;

public final class PhantomProgressionMetrics
{
	private final LongAdder _catalogBuilds = new LongAdder();
	private final LongAdder _catalogFailures = new LongAdder();
	private final LongAdder _classFacts = new LongAdder();
	private final LongAdder _skillLearnFacts = new LongAdder();
	private final LongAdder _skillFacts = new LongAdder();
	private final LongAdder _equipmentFacts = new LongAdder();
	private final LongAdder _summonPetFacts = new LongAdder();
	private final LongAdder _queries = new LongAdder();
	private final LongAdder _emptyQueries = new LongAdder();
	private final LongAdder _actorSnapshotsRequested = new LongAdder();
	private final LongAdder _actorSnapshotsSucceeded = new LongAdder();
	private final LongAdder _actorSnapshotsMissing = new LongAdder();
	private final LongAdder _capabilityEvaluations = new LongAdder();
	private final LongAdder _operationsRequested = new LongAdder();
	private final LongAdder _operationsAccepted = new LongAdder();
	private final LongAdder _operationsRejected = new LongAdder();
	private final LongAdder _learnRequests = new LongAdder();
	private final LongAdder _equipRequests = new LongAdder();
	private final LongAdder _skillLearnSuccess = new LongAdder();
	private final LongAdder _skillLearnIdempotent = new LongAdder();
	private final LongAdder _skillLearnRejected = new LongAdder();
	private final LongAdder _equipSuccess = new LongAdder();
	private final LongAdder _equipIdempotent = new LongAdder();
	private final LongAdder _equipRejected = new LongAdder();
	private final LongAdder _spConsumed = new LongAdder();
	private final LongAdder _itemsConsumed = new LongAdder();
	private final LongAdder _actorLeasesAcquired = new LongAdder();
	private final LongAdder _actorLeasesRejected = new LongAdder();
	private final LongAdder _actorLeasesReleased = new LongAdder();
	private final LongAdder _shutdownFailures = new LongAdder();
	private final LongAdder _canonicalQuestRequired = new LongAdder();
	private final LongAdder _cancellations = new LongAdder();
	private final LongAdder _skillLearnFailures = new LongAdder();
	private final LongAdder _equipFailures = new LongAdder();

	void recordCatalogBuild(PhantomProgressionCatalog.Counts counts)
	{
		_catalogBuilds.increment();
		_classFacts.add(counts.classes());
		_skillLearnFacts.add(counts.skillLearns());
		_skillFacts.add(counts.skills());
		_equipmentFacts.add(counts.equipment());
		_summonPetFacts.add(counts.summons() + counts.pets());
	}

	void recordCatalogFailure()
	{
		_catalogFailures.increment();
	}

	void recordQuery(boolean empty)
	{
		_queries.increment();
		if (empty)
		{
			_emptyQueries.increment();
		}
	}

	void recordActorSnapshot(boolean found)
	{
		_actorSnapshotsRequested.increment();
		if (found)
		{
			_actorSnapshotsSucceeded.increment();
		}
		else
		{
			_actorSnapshotsMissing.increment();
		}
	}

	void recordCapabilityEvaluations(int count)
	{
		_capabilityEvaluations.add(count);
	}

	void recordOperationRequested(boolean learn)
	{
		_operationsRequested.increment();
		if (learn)
		{
			_learnRequests.increment();
		}
		else
		{
			_equipRequests.increment();
		}
	}

	void recordOperationAccepted()
	{
		_operationsAccepted.increment();
	}

	void recordOperationRejected(boolean learn)
	{
		_operationsRejected.increment();
		if (learn)
		{
			_skillLearnRejected.increment();
		}
		else
		{
			_equipRejected.increment();
		}
	}

	void recordLearnResult(boolean idempotent, long spConsumed, long itemsConsumed)
	{
		if (idempotent)
		{
			_skillLearnIdempotent.increment();
		}
		else
		{
			_skillLearnSuccess.increment();
			_spConsumed.add(Math.max(0, spConsumed));
			_itemsConsumed.add(Math.max(0, itemsConsumed));
		}
	}

	void recordEquipResult(boolean idempotent)
	{
		if (idempotent)
		{
			_equipIdempotent.increment();
		}
		else
		{
			_equipSuccess.increment();
		}
	}

	void recordLeaseAcquired()
	{
		_actorLeasesAcquired.increment();
	}

	void recordLeaseRejected()
	{
		_actorLeasesRejected.increment();
	}

	void recordLeaseReleased()
	{
		_actorLeasesReleased.increment();
	}

	void recordShutdownFailure()
	{
		_shutdownFailures.increment();
	}

	void recordCanonicalQuestRequired(int count)
	{
		_canonicalQuestRequired.add(count);
	}

	void recordCancellation()
	{
		_cancellations.increment();
	}

	void recordOperationFailure(boolean learn)
	{
		if (learn)
		{
			_skillLearnFailures.increment();
		}
		else
		{
			_equipFailures.increment();
		}
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_catalogBuilds.sum(), _catalogFailures.sum(), _classFacts.sum(), _skillLearnFacts.sum(), _skillFacts.sum(), _equipmentFacts.sum(), _summonPetFacts.sum(), _queries.sum(), _emptyQueries.sum(), _actorSnapshotsRequested.sum(), _actorSnapshotsSucceeded.sum(), _actorSnapshotsMissing.sum(), _capabilityEvaluations.sum(), _operationsRequested.sum(), _operationsAccepted.sum(), _operationsRejected.sum(), _learnRequests.sum(), _equipRequests.sum(), _skillLearnSuccess.sum(), _skillLearnIdempotent.sum(), _skillLearnRejected.sum(), _skillLearnFailures.sum(), _equipSuccess.sum(), _equipIdempotent.sum(), _equipRejected.sum(), _equipFailures.sum(), _spConsumed.sum(), _itemsConsumed.sum(), _actorLeasesAcquired.sum(), _actorLeasesRejected.sum(), _actorLeasesReleased.sum(), _canonicalQuestRequired.sum(), _cancellations.sum(), _shutdownFailures.sum());
	}

	public record Snapshot(long catalogBuilds, long catalogFailures, long classFacts, long skillLearnFacts, long skillFacts, long equipmentFacts, long summonPetFacts, long queries, long emptyQueries, long actorSnapshotsRequested, long actorSnapshotsSucceeded, long actorSnapshotsMissing, long capabilityEvaluations, long operationsRequested, long operationsAccepted, long operationsRejected, long learnRequests, long equipRequests, long skillLearnSuccess, long skillLearnIdempotent, long skillLearnRejected, long skillLearnFailures, long equipSuccess, long equipIdempotent, long equipRejected, long equipFailures, long spConsumed, long itemsConsumed, long actorLeasesAcquired, long actorLeasesRejected, long actorLeasesReleased, long canonicalQuestRequired, long cancellations, long shutdownFailures)
	{
	}
}

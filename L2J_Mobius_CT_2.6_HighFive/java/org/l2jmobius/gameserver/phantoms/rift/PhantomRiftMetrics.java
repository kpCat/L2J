/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.concurrent.atomic.LongAdder;

public final class PhantomRiftMetrics
{
	private final LongAdder _evaluations = new LongAdder();
	private final LongAdder _candidateSearches = new LongAdder();
	private final LongAdder _inviteRequests = new LongAdder();
	private final LongAdder _inviteAccepted = new LongAdder();
	private final LongAdder _inviteRefused = new LongAdder();
	private final LongAdder _inviteExpired = new LongAdder();
	private final LongAdder _candidateRejected = new LongAdder();
	private final LongAdder _routeRequests = new LongAdder();
	private final LongAdder _ready = new LongAdder();
	private final LongAdder _needsParty = new LongAdder();
	private final LongAdder _needsRole = new LongAdder();
	private final LongAdder _needsMemberReady = new LongAdder();
	private final LongAdder _needsSupplies = new LongAdder();
	private final LongAdder _needsTravel = new LongAdder();
	private final LongAdder _rosterStale = new LongAdder();
	private final LongAdder _sourceStale = new LongAdder();
	private final LongAdder _bindingConflicts = new LongAdder();
	private final LongAdder _persistenceConflicts = new LongAdder();
	private final LongAdder _migrationReplans = new LongAdder();

	void evaluation()
	{
		_evaluations.increment();
	}

	void status(PhantomRiftModel.Status status)
	{
		switch (status)
		{
			case NEEDS_PARTY -> _needsParty.increment();
			case NEEDS_ROLE -> _needsRole.increment();
			case NEEDS_MEMBER_READY -> _needsMemberReady.increment();
			case NEEDS_SUPPLIES -> _needsSupplies.increment();
			case NEEDS_TRAVEL -> _needsTravel.increment();
			default ->
			{
			}
		}
	}

	void candidateSearch() { _candidateSearches.increment(); }
	void inviteRequest() { _inviteRequests.increment(); }
	void inviteAccepted() { _inviteAccepted.increment(); }
	void inviteRefused() { _inviteRefused.increment(); }
	void inviteExpired() { _inviteExpired.increment(); }
	void candidateRejected() { _candidateRejected.increment(); }
	void routeRequest() { _routeRequests.increment(); }
	void ready() { _ready.increment(); }
	void rosterStale() { _rosterStale.increment(); }
	void sourceStale() { _sourceStale.increment(); }
	void bindingConflict() { _bindingConflicts.increment(); }
	void conflict() { _persistenceConflicts.increment(); }
	void migrationReplan() { _migrationReplans.increment(); }

	public Snapshot snapshot()
	{
		return new Snapshot(_evaluations.sum(), _candidateSearches.sum(), _inviteRequests.sum(), _inviteAccepted.sum(), _inviteRefused.sum(), _inviteExpired.sum(), _candidateRejected.sum(), _routeRequests.sum(), _ready.sum(), _needsParty.sum(), _needsRole.sum(), _needsMemberReady.sum(), _needsSupplies.sum(), _needsTravel.sum(), _rosterStale.sum(), _sourceStale.sum(), _bindingConflicts.sum(), _persistenceConflicts.sum(), _migrationReplans.sum());
	}

	public record Snapshot(long evaluations, long candidateSearches, long inviteRequests, long inviteAccepted, long inviteRefused, long inviteExpired, long candidateRejected, long routeRequests, long ready, long needsParty, long needsRole, long needsMemberReady, long needsSupplies, long needsTravel, long rosterStale, long sourceStale, long bindingConflicts, long persistenceConflicts, long migrationReplans)
	{
		public long refusals()
		{
			return inviteRefused;
		}

		public long conflicts()
		{
			return persistenceConflicts;
		}
	}
}
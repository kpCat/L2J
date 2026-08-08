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
	private final LongAdder _refusals = new LongAdder();
	private final LongAdder _routeRequests = new LongAdder();
	private final LongAdder _ready = new LongAdder();
	private final LongAdder _conflicts = new LongAdder();

	void evaluation()
	{
		_evaluations.increment();
	}

	void candidateSearch()
	{
		_candidateSearches.increment();
	}

	void inviteRequest()
	{
		_inviteRequests.increment();
	}

	void refusal()
	{
		_refusals.increment();
	}

	void routeRequest()
	{
		_routeRequests.increment();
	}

	void ready()
	{
		_ready.increment();
	}

	void conflict()
	{
		_conflicts.increment();
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_evaluations.sum(), _candidateSearches.sum(), _inviteRequests.sum(), _refusals.sum(), _routeRequests.sum(), _ready.sum(), _conflicts.sum());
	}

	public record Snapshot(long evaluations, long candidateSearches, long inviteRequests, long refusals, long routeRequests, long ready, long conflicts)
	{
	}
}

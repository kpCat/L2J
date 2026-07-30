/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.concurrent.atomic.LongAdder;

public final class PhantomPartyMetrics
{
	private final LongAdder _pulses = new LongAdder();
	private final LongAdder _operations = new LongAdder();
	private final LongAdder _budgetExhausted = new LongAdder();
	private final LongAdder _invitesDelivered = new LongAdder();
	private final LongAdder _invitesAccepted = new LongAdder();
	private final LongAdder _invitesRefused = new LongAdder();
	private final LongAdder _commits = new LongAdder();
	private final LongAdder _recoveries = new LongAdder();
	private final LongAdder _conflicts = new LongAdder();
	private final LongAdder _failures = new LongAdder();

	void pulse()
	{
		_pulses.increment();
	}

	void operation()
	{
		_operations.increment();
	}

	void budgetExhausted()
	{
		_budgetExhausted.increment();
	}

	void inviteDelivered()
	{
		_invitesDelivered.increment();
	}

	void inviteAccepted()
	{
		_invitesAccepted.increment();
	}

	void inviteRefused()
	{
		_invitesRefused.increment();
	}

	void commit()
	{
		_commits.increment();
	}

	void recovery()
	{
		_recoveries.increment();
	}

	void conflict()
	{
		_conflicts.increment();
	}

	void failure()
	{
		_failures.increment();
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_pulses.sum(), _operations.sum(), _budgetExhausted.sum(), _invitesDelivered.sum(), _invitesAccepted.sum(), _invitesRefused.sum(), _commits.sum(), _recoveries.sum(), _conflicts.sum(), _failures.sum());
	}

	public record Snapshot(long pulses, long operations, long budgetExhausted, long invitesDelivered, long invitesAccepted, long invitesRefused, long commits, long recoveries, long conflicts, long failures)
	{
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.util.concurrent.atomic.LongAdder;

/**
 * Fixed-cardinality aggregate counters for the one-build knowledge service.
 */
public final class PhantomGameKnowledgeMetrics
{
	public enum QueryCategory
	{
		ITEM,
		NPC,
		DROP,
		SPOIL,
		MANOR,
		SPAWN_AREA,
		SPAWN_FACT,
		RECIPE,
		RECIPE_PRODUCT,
		RECIPE_INGREDIENT,
		CLASS,
		CAPABILITY,
		CONTENT,
		CONTENT_CAPABILITY,
		TARGET
	}

	private final LongAdder _buildsStarted = new LongAdder();
	private final LongAdder _buildsCompleted = new LongAdder();
	private final LongAdder _buildsFailed = new LongAdder();
	private final LongAdder[] _queries = new LongAdder[QueryCategory.values().length];
	private final LongAdder _pagesReturned = new LongAdder();
	private final LongAdder _targetCandidatesConsidered = new LongAdder();
	private final LongAdder _targetCandidatesReturned = new LongAdder();
	private final LongAdder _rejectedQueries = new LongAdder();
	private final LongAdder _sourceParityFailures = new LongAdder();

	public PhantomGameKnowledgeMetrics()
	{
		for (int index = 0; index < _queries.length; index++)
		{
			_queries[index] = new LongAdder();
		}
	}

	public void recordBuildStarted()
	{
		_buildsStarted.increment();
	}

	public void recordBuildCompleted()
	{
		_buildsCompleted.increment();
	}

	public void recordBuildFailed()
	{
		_buildsFailed.increment();
	}

	public void recordQuery(QueryCategory category)
	{
		_queries[category.ordinal()].increment();
	}

	public void recordPage()
	{
		_pagesReturned.increment();
	}

	public void recordTargetCandidates(int considered, int returned)
	{
		_targetCandidatesConsidered.add(considered);
		_targetCandidatesReturned.add(returned);
	}

	public void recordRejectedQuery()
	{
		_rejectedQueries.increment();
	}

	public void recordSourceParityFailure()
	{
		_sourceParityFailures.increment();
	}

	public Snapshot snapshot()
	{
		final long[] queries = new long[_queries.length];
		for (int index = 0; index < queries.length; index++)
		{
			queries[index] = _queries[index].sum();
		}
		return new Snapshot(_buildsStarted.sum(), _buildsCompleted.sum(), _buildsFailed.sum(), queries, _pagesReturned.sum(), _targetCandidatesConsidered.sum(), _targetCandidatesReturned.sum(), _rejectedQueries.sum(), _sourceParityFailures.sum());
	}

	public record Snapshot(long buildsStarted, long buildsCompleted, long buildsFailed, long[] queriesByCategory, long pagesReturned, long targetCandidatesConsidered, long targetCandidatesReturned, long rejectedQueries, long sourceParityFailures)
	{
		public Snapshot
		{
			queriesByCategory = queriesByCategory.clone();
		}

		@Override
		public long[] queriesByCategory()
		{
			return queriesByCategory.clone();
		}
	}
}

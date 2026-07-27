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

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Atomic lifecycle owner for a single immutable production generation.
 */
public final class PhantomGameKnowledgeService
{
	private static final PhantomGameKnowledgeSnapshot.Counts ZERO_COUNTS = new PhantomGameKnowledgeSnapshot.Counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	public enum State
	{
		NEW,
		BUILDING,
		RUNNING,
		STOPPED,
		FAILED
	}

	private final Object _monitor = new Object();
	private final Supplier<PhantomGameKnowledgeSnapshot> _builder;
	private final PhantomGameKnowledgeMetrics _metrics = new PhantomGameKnowledgeMetrics();
	private State _state = State.NEW;
	private boolean _stopping;
	private PhantomGameKnowledgeSnapshot _snapshot;
	private PhantomGameKnowledgeQuery _query;
	private String _lastFailureCategory = "none";
	private long _buildDurationMillis;

	public PhantomGameKnowledgeService(PhantomGameKnowledgeBuilder builder)
	{
		this(builder::build);
	}

	public PhantomGameKnowledgeService(Supplier<PhantomGameKnowledgeSnapshot> builder)
	{
		_builder = Objects.requireNonNull(builder, "builder");
	}

	public static PhantomGameKnowledgeService inertForTesting(String topologyHash)
	{
		return new PhantomGameKnowledgeService(() -> PhantomGameKnowledgeSnapshot.empty(topologyHash));
	}

	public boolean start()
	{
		synchronized (_monitor)
		{
			if (_state != State.NEW)
			{
				return false;
			}
			_state = State.BUILDING;
		}
		_metrics.recordBuildStarted();
		final long startedNanos = System.nanoTime();
		final PhantomGameKnowledgeSnapshot candidate;
		try
		{
			candidate = Objects.requireNonNull(_builder.get(), "Game Knowledge builder returned null.");
		}
		catch (RuntimeException exception)
		{
			synchronized (_monitor)
			{
				_state = State.FAILED;
				_lastFailureCategory = exception instanceof PhantomGameKnowledgeValidationException validation ? validation.category() : "build";
				_buildDurationMillis = elapsedMillis(startedNanos);
			}
			_metrics.recordBuildFailed();
			if (exception instanceof PhantomGameKnowledgeValidationException)
			{
				_metrics.recordSourceParityFailure();
			}
			throw exception;
		}
		synchronized (_monitor)
		{
			if ((_state != State.BUILDING) || _stopping)
			{
				_state = State.STOPPED;
				return false;
			}
			_snapshot = candidate;
			_query = new PhantomGameKnowledgeQuery(candidate, _metrics);
			_state = State.RUNNING;
			_buildDurationMillis = elapsedMillis(startedNanos);
		}
		_metrics.recordBuildCompleted();
		return true;
	}

	private static long elapsedMillis(long startedNanos)
	{
		return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	public PhantomGameKnowledgeQuery query()
	{
		synchronized (_monitor)
		{
			if ((_state != State.RUNNING) || _stopping || (_query == null))
			{
				_metrics.recordRejectedQuery();
				throw new IllegalStateException("Game Knowledge has no active query generation.");
			}
			return _query;
		}
	}

	public boolean beginStop()
	{
		synchronized (_monitor)
		{
			if ((_state == State.STOPPED) || _stopping)
			{
				return false;
			}
			_stopping = true;
			return true;
		}
	}

	public boolean finishStop()
	{
		synchronized (_monitor)
		{
			if (_state == State.STOPPED)
			{
				return true;
			}
			if (!_stopping && (_state != State.NEW) && (_state != State.FAILED))
			{
				return false;
			}
			_query = null;
			_snapshot = null;
			_state = State.STOPPED;
			_stopping = true;
			return true;
		}
	}

	public ServiceSnapshot snapshot()
	{
		synchronized (_monitor)
		{
			final PhantomGameKnowledgeSnapshot snapshot = _snapshot;
			return new ServiceSnapshot(_state, _stopping, snapshot == null ? 0 : snapshot.schemaVersion(), snapshot == null ? "none" : snapshot.datasetId(), snapshot == null ? 0 : snapshot.datasetVersion(), snapshot == null ? 0 : snapshot.generation(), snapshot == null ? "none" : snapshot.combinedHash(), snapshot == null ? PhantomGameKnowledgeSnapshot.Hashes.none() : snapshot.hashes(), snapshot == null ? ZERO_COUNTS : snapshot.counts(), _lastFailureCategory, _buildDurationMillis, _metrics.snapshot());
		}
	}

	public record ServiceSnapshot(State state, boolean stopping, int schemaVersion, String datasetId, int datasetVersion, long generation, String combinedHash, PhantomGameKnowledgeSnapshot.Hashes hashes, PhantomGameKnowledgeSnapshot.Counts counts, String lastFailureCategory, long buildDurationMillis, PhantomGameKnowledgeMetrics.Snapshot metrics)
	{
		public static ServiceSnapshot inactive()
		{
			return new ServiceSnapshot(State.STOPPED, true, 0, "none", 0, 0, "none", PhantomGameKnowledgeSnapshot.Hashes.none(), ZERO_COUNTS, "none", 0, new PhantomGameKnowledgeMetrics().snapshot());
		}
	}
}

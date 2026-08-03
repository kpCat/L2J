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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.economy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Audit;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State;

/**
 * Participant-neutral durable reservation kernel. Every mutating path locks
 * profiles, operation and reservation keys in that order.
 */
public final class PhantomEconomyReservationService
{
	private static final String ACTIVE_STATES = "'PREPARED','RESERVED','DISPATCHING','OBSERVING'";
	private static final int WRITER_STRIPES = 64;
	private final ConnectionProvider _connections;
	private final PhantomEconomyPolicy _policy;
	private final ReentrantLock _admissionLock = new ReentrantLock();
	private final ReentrantLock[] _writerLocks = new ReentrantLock[WRITER_STRIPES];
	private final AtomicBoolean _admissionOpen = new AtomicBoolean();
	private final LongAdder _reserved = new LongAdder();
	private final LongAdder _conflicts = new LongAdder();
	private final LongAdder _expired = new LongAdder();
	private final LongAdder _inconsistent = new LongAdder();
	private final LongAdder _prepared = new LongAdder();
	private final LongAdder _dispatched = new LongAdder();
	private final LongAdder _committed = new LongAdder();
	private final LongAdder _aborted = new LongAdder();
	private final AtomicLong _currentOperations = new AtomicLong();
	private final AtomicLong _currentReservations = new AtomicLong();

	public PhantomEconomyReservationService(PhantomEconomyPolicy policy)
	{
		this(DatabaseFactory::getConnection, policy);
	}

	public PhantomEconomyReservationService(ConnectionProvider connections, PhantomEconomyPolicy policy)
	{
		_connections = Objects.requireNonNull(connections);
		_policy = Objects.requireNonNull(policy);
		for (int i = 0; i < _writerLocks.length; i++)
		{
			_writerLocks[i] = new ReentrantLock();
		}
	}

	public boolean start()
	{
		if (!_admissionOpen.compareAndSet(false, true))
		{
			return false;
		}
		try (Connection connection = _connections.open())
		{
			_currentOperations.set(count(connection, "SELECT COUNT(*) FROM phantom_economy_operations WHERE operation_state IN (" + ACTIVE_STATES + ")"));
			_currentReservations.set(count(connection, "SELECT COUNT(*) FROM phantom_economy_reservations"));
		}
		catch (SQLException exception)
		{
			_admissionOpen.set(false);
			throw persistenceFailure("initialize economy metrics", exception);
		}
		for (int page = 0; page < 391; page++)
		{
			final List<StoredOperation> uncertain = findObserving(256);
			if (uncertain.isEmpty())
			{
				return true;
			}
			for (StoredOperation operation : uncertain)
			{
				transition(operation.operationId(), operation.state(), State.INCONSISTENT, System.currentTimeMillis(), new Audit(PhantomEconomyOperation.Result.INCONSISTENT, "dispatch.ambiguous", new byte[0]));
			}
		}
		_admissionOpen.set(false);
		throw new IllegalStateException("Economy restart reconciliation exceeded its retained-operation bound.");
	}

	public ReserveResult reserve(PhantomEconomyOperation operation, List<Reservation> requested)
	{
		Objects.requireNonNull(operation);
		if (!_admissionOpen.get() || (operation.state() != State.PREPARED))
		{
			return ReserveResult.rejected(Status.ADMISSION_CLOSED);
		}
		final List<Reservation> reservations = PhantomEconomyOperation.canonicalReservations(requested, _policy.limits().reservationsPerOperation());
		if (hasSemanticOverlap(reservations))
		{
			_conflicts.increment();
			return ReserveResult.rejected(Status.RESOURCE_CONFLICT);
		}
		if (reservations.stream().map(Reservation::itemId).filter(itemId -> itemId > 0).distinct().count() > _policy.limits().itemIdsPerRead())
		{
			throw new IllegalArgumentException("Economy distinct item ID bound exceeded.");
		}
		final List<Long> profileIds = participantProfiles(operation, reservations);
		final ReentrantLock writerLock = writerLock(operation.identity().profileId());
		_admissionLock.lock();
		writerLock.lock();
		try (Connection connection = _connections.open())
		{
			begin(connection);
			try
			{
				final Map<Long, Integer> participantLinks = lockProfiles(connection, profileIds);
				if (!Objects.equals(participantLinks.get(operation.identity().profileId()), operation.identity().characterObjectId()) || reservations.stream().anyMatch(reservation -> !Objects.equals(participantLinks.get(reservation.profileId()), reservation.ownerObjectId())))
				{
					connection.rollback();
					_conflicts.increment();
					return ReserveResult.rejected(Status.IDENTITY_CONFLICT);
				}
				final StoredOperation existing = lockOperation(connection, operation.operationId());
				if (existing != null)
				{
					if (existing.matches(operation))
					{
						connection.commit();
						return new ReserveResult(Status.IDEMPOTENT, existing.state(), operation.operationId());
					}
					connection.rollback();
					return ReserveResult.rejected(Status.IDENTITY_CONFLICT);
				}
				boolean participantBusy = false;
				for (long profileId : profileIds)
				{
					participantBusy |= hasAnotherActiveOperation(connection, profileId, operation.operationId());
				}
				if (participantBusy)
				{
					connection.rollback();
					_conflicts.increment();
					return ReserveResult.rejected(Status.PROFILE_BUSY);
				}
				if (count(connection, "SELECT COUNT(*) FROM phantom_economy_operations WHERE operation_state IN (" + ACTIVE_STATES + ")") >= _policy.limits().retainedNonterminalOperations())
				{
					connection.rollback();
					return ReserveResult.rejected(Status.LIMIT_REACHED);
				}
				if (hasReservationConflict(connection, operation.operationId(), reservations))
				{
					connection.rollback();
					_conflicts.increment();
					return ReserveResult.rejected(Status.RESOURCE_CONFLICT);
				}
				insertOperation(connection, operation);
				for (int i = 0; i < reservations.size(); i++)
				{
					insertReservation(connection, operation.operationId(), i, reservations.get(i));
				}
				transition(connection, operation.operationId(), State.PREPARED, State.RESERVED, operation.updatedEpochMillis());
				connection.commit();
				_prepared.increment();
				_reserved.increment();
				_currentOperations.incrementAndGet();
				_currentReservations.addAndGet(reservations.size());
				return new ReserveResult(Status.RESERVED, State.RESERVED, operation.operationId());
			}
			catch (SQLIntegrityConstraintViolationException exception)
			{
				rollback(connection, exception);
				_conflicts.increment();
				return ReserveResult.rejected(Status.RESOURCE_CONFLICT);
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				throw persistenceFailure("reserve economy resources", failure);
			}
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("open economy reservation transaction", exception);
		}
		finally
		{
			writerLock.unlock();
			_admissionLock.unlock();
		}
	}

	public TransitionResult transition(String operationId, State expected, State next, long nowEpochMillis, Audit audit)
	{
		if ((operationId == null) || !operationId.matches("[0-9a-f]{64}") || (expected == null) || (next == null) || !expected.canTransitionTo(next))
		{
			throw new IllegalArgumentException("Invalid economy state transition.");
		}
		try (Connection connection = _connections.open())
		{
			begin(connection);
			try
			{
				final StoredOperation snapshot = readOperation(connection, operationId);
				if (snapshot == null)
				{
					connection.rollback();
					return new TransitionResult(Status.NOT_FOUND, null);
				}
				lockProfiles(connection, List.of(snapshot.profileId()));
				final StoredOperation locked = lockOperation(connection, operationId);
				if (locked.state() == next)
				{
					connection.commit();
					return new TransitionResult(Status.IDEMPOTENT, next);
				}
				if (locked.state() != expected)
				{
					connection.rollback();
					return new TransitionResult(Status.STATE_CONFLICT, locked.state());
				}
				if (next.terminal())
				{
					lockReservationKeys(connection, operationId);
				}
				transition(connection, operationId, expected, next, nowEpochMillis);
				int releasedReservations = 0;
				if (next.terminal())
				{
					if (audit == null)
					{
						throw new IllegalArgumentException("Terminal economy transition requires an audit.");
					}
					setTerminal(connection, operationId, audit);
					insertAudit(connection, locked, next, audit);
					releasedReservations = deleteReservations(connection, operationId);
					trimAudit(connection, locked.profileId());
				}
				connection.commit();
				if (next.terminal())
				{
					_currentReservations.addAndGet(-releasedReservations);
					_currentOperations.decrementAndGet();
					recordTerminal(next);
				}
				if (next == State.DISPATCHING)
				{
					_dispatched.increment();
				}
				return new TransitionResult(Status.TRANSITIONED, next);
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				throw persistenceFailure("transition economy operation", failure);
			}
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("open economy transition transaction", exception);
		}
	}

	public TransitionResult renew(String operationId, long nowEpochMillis, long expiresEpochMillis)
	{
		if ((expiresEpochMillis <= nowEpochMillis) || ((expiresEpochMillis - nowEpochMillis) > (_policy.limits().reservationTtlSeconds() * 1000L)))
		{
			throw new IllegalArgumentException("Invalid reservation renewal window.");
		}
		try (Connection connection = _connections.open())
		{
			begin(connection);
			try
			{
				final StoredOperation snapshot = readOperation(connection, operationId);
				if (snapshot == null)
				{
					connection.rollback();
					return new TransitionResult(Status.NOT_FOUND, null);
				}
				lockProfiles(connection, List.of(snapshot.profileId()));
				final StoredOperation locked = lockOperation(connection, operationId);
				if (locked.state() != State.RESERVED)
				{
					connection.rollback();
					return new TransitionResult(Status.STATE_CONFLICT, locked.state());
				}
				try (PreparedStatement statement = connection.prepareStatement("UPDATE phantom_economy_operations SET expires_at=?, updated_at=?, row_version=row_version+1 WHERE operation_id=? AND operation_state='RESERVED'"))
				{
					statement.setTimestamp(1, new Timestamp(expiresEpochMillis));
					statement.setTimestamp(2, new Timestamp(nowEpochMillis));
					statement.setString(3, operationId);
					requireOne(statement.executeUpdate(), "economy reservation renewal");
				}
				connection.commit();
				return new TransitionResult(Status.TRANSITIONED, State.RESERVED);
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				throw persistenceFailure("renew economy reservation", failure);
			}
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("open economy renewal transaction", exception);
		}
	}

	public int expireDue(long nowEpochMillis, int maximum)
	{
		if ((maximum < 1) || (maximum > 256))
		{
			throw new IllegalArgumentException("Invalid economy expiration batch.");
		}
		final List<String> candidates = new ArrayList<>();
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT operation_id FROM phantom_economy_operations WHERE operation_state IN ('PREPARED','RESERVED') AND expires_at<=? ORDER BY profile_id, operation_id LIMIT ?"))
		{
			statement.setTimestamp(1, new Timestamp(nowEpochMillis));
			statement.setInt(2, maximum);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					candidates.add(result.getString(1));
				}
			}
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("read expirable economy reservations", exception);
		}
		int count = 0;
		for (String operationId : candidates)
		{
			final Optional<StoredOperation> stored = find(operationId);
			if (stored.isPresent() && ((stored.get().state() == State.PREPARED) || (stored.get().state() == State.RESERVED)))
			{
				final TransitionResult result = transition(operationId, stored.get().state(), State.EXPIRED, nowEpochMillis, new Audit(PhantomEconomyOperation.Result.ERROR, "operation.expired", new byte[0]));
				if (result.status() == Status.TRANSITIONED)
				{
					count++;
				}
			}
		}
		return count;
	}

	public ReconcileResult reconcile(String operationId, Evidence evidence, long nowEpochMillis, Audit terminalAudit)
	{
		Objects.requireNonNull(evidence);
		final Optional<StoredOperation> stored = find(operationId);
		if (stored.isEmpty())
		{
			return new ReconcileResult(Status.NOT_FOUND, null, false);
		}
		final State state = stored.get().state();
		if ((state != State.DISPATCHING) && (state != State.OBSERVING))
		{
			return new ReconcileResult(Status.STATE_CONFLICT, state, false);
		}
		return switch (evidence)
		{
			case EXACT_AFTER ->
			{
				final TransitionResult result = transition(operationId, state, State.COMMITTED, nowEpochMillis, Objects.requireNonNull(terminalAudit));
				yield new ReconcileResult(result.status(), result.state(), false);
			}
			case AMBIGUOUS, PARTIAL ->
			{
				final TransitionResult result = transition(operationId, state, State.INCONSISTENT, nowEpochMillis, new Audit(PhantomEconomyOperation.Result.INCONSISTENT, "dispatch.ambiguous", new byte[0]));
				yield new ReconcileResult(result.status(), result.state(), false);
			}
			case EXACT_BEFORE_ACTION_NOT_ISSUED -> new ReconcileResult(Status.REDISPATCH_ALLOWED, state, true);
			case EXACT_BEFORE_ACTION_ISSUED ->
			{
				final TransitionResult result = transition(operationId, state, State.INCONSISTENT, nowEpochMillis, new Audit(PhantomEconomyOperation.Result.INCONSISTENT, "dispatch.ambiguous", new byte[0]));
				yield new ReconcileResult(result.status(), result.state(), false);
			}
		};
	}

	public WriterClaim claimWriter(long profileId, String ownOperationId, List<Reservation> resources)
	{
		final List<Reservation> canonical = resources == null || resources.isEmpty() ? List.of() : PhantomEconomyOperation.canonicalReservations(resources, _policy.limits().reservationsPerOperation());
		final ReentrantLock lock = writerLock(profileId);
		lock.lock();
		try (Connection connection = _connections.open())
		{
			begin(connection);
			lockProfiles(connection, List.of(profileId));
			if (hasReservationConflict(connection, ownOperationId, canonical))
			{
				connection.rollback();
				lock.unlock();
				_conflicts.increment();
				return WriterClaim.conflict();
			}
			connection.commit();
			return new WriterClaim(lock, null, true);
		}
		catch (Throwable failure)
		{
			lock.unlock();
			throw persistenceFailure("claim economy writer", failure);
		}
	}

	public void requireNoConflict(Connection connection, String ownOperationId, List<Reservation> resources) throws SQLException
	{
		final List<Reservation> canonical = PhantomEconomyOperation.canonicalReservations(resources, _policy.limits().reservationsPerOperation());
		if (hasReservationConflict(connection, ownOperationId, canonical))
		{
			throw new EconomyConflictException("A requested economy resource is reserved by another operation.");
		}
	}

	public DispatchLock lockDispatchInTransaction(Connection connection, String operationId, long profileId) throws SQLException
	{
		final StoredOperation operation = lockOperation(connection, operationId);
		if ((operation == null) || (operation.profileId() != profileId) || (operation.state() != State.DISPATCHING))
		{
			throw new EconomyConflictException("Economy operation is not in exact DISPATCHING state.");
		}
		final List<String> resources = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("SELECT canonical_resource_key FROM phantom_economy_reservations WHERE operation_id=? ORDER BY canonical_resource_key FOR UPDATE"))
		{
			statement.setString(1, operationId);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					resources.add(rows.getString(1));
				}
			}
		}
		if (resources.isEmpty() || (resources.size() > _policy.limits().reservationsPerOperation()))
		{
			throw new EconomyConflictException("Economy dispatch has invalid reservation evidence.");
		}
		return new DispatchLock(operation, List.copyOf(resources));
	}

	public void commitDispatchInTransaction(Connection connection, DispatchLock dispatch, Audit audit, long nowEpochMillis) throws SQLException
	{
		Objects.requireNonNull(dispatch);
		Objects.requireNonNull(audit);
		transition(connection, dispatch.operation().operationId(), State.DISPATCHING, State.COMMITTED, nowEpochMillis);
		setTerminal(connection, dispatch.operation().operationId(), audit);
		insertAudit(connection, dispatch.operation(), State.COMMITTED, audit);
		deleteReservations(connection, dispatch.operation().operationId());
		trimAudit(connection, dispatch.operation().profileId());
	}

	public void dispatchCommitted(int releasedReservations)
	{
		if ((releasedReservations < 1) || (releasedReservations > _policy.limits().reservationsPerOperation()))
		{
			throw new IllegalArgumentException("Invalid committed reservation count.");
		}
		_currentReservations.addAndGet(-releasedReservations);
		_currentOperations.decrementAndGet();
		_committed.increment();
	}

	public void beforeBoundary(long profileId, long nowEpochMillis)
	{
		try (Connection connection = _connections.open())
		{
			begin(connection);
			try
			{
				lockProfiles(connection, List.of(profileId));
				final List<StoredOperation> active = lockActiveOperations(connection, profileId);
				int releasedReservations = 0;
				for (StoredOperation operation : active)
				{
					if ((operation.state() == State.DISPATCHING) || (operation.state() == State.OBSERVING))
					{
						throw new EconomyConflictException("Economy dispatch is awaiting exact reconciliation.");
					}
					lockReservationKeys(connection, operation.operationId());
					transition(connection, operation.operationId(), operation.state(), State.ABORTED, nowEpochMillis);
					final Audit audit = new Audit(PhantomEconomyOperation.Result.ERROR, "operation.conflict", new byte[0]);
					setTerminal(connection, operation.operationId(), audit);
					insertAudit(connection, operation, State.ABORTED, audit);
					releasedReservations += deleteReservations(connection, operation.operationId());
				}
				trimAudit(connection, profileId);
				connection.commit();
				_currentReservations.addAndGet(-releasedReservations);
				_currentOperations.addAndGet(-active.size());
				_aborted.add(active.size());
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				throw failure;
			}
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw persistenceFailure("guard economy materialization boundary", exception);
		}
	}

	public void shutdown(long nowEpochMillis)
	{
		_admissionOpen.set(false);
		for (int i = 0; i < 391; i++)
		{
			final List<String> candidates = findShutdownCandidates(256);
			if (candidates.isEmpty())
			{
				break;
			}
			for (String operationId : candidates)
			{
				final Optional<StoredOperation> stored = find(operationId);
				if (stored.isPresent() && ((stored.get().state() == State.PREPARED) || (stored.get().state() == State.RESERVED) || (stored.get().state() == State.DISPATCHING)))
				{
					transition(operationId, stored.get().state(), State.ABORTED, nowEpochMillis, new Audit(PhantomEconomyOperation.Result.ERROR, "operation.shutdown", new byte[0]));
				}
				else if (stored.isPresent() && (stored.get().state() == State.OBSERVING))
				{
					transition(operationId, State.OBSERVING, State.INCONSISTENT, nowEpochMillis, new Audit(PhantomEconomyOperation.Result.INCONSISTENT, "operation.shutdown.observing", new byte[0]));
				}
			}
		}
	}

	public Optional<StoredOperation> find(String operationId)
	{
		try (Connection connection = _connections.open())
		{
			return Optional.ofNullable(readOperation(connection, operationId));
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("find economy operation", exception);
		}
	}

	public Optional<StoredOperation> findActive(long profileId)
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("Profile ID must be positive.");
		}
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT operation_id,profile_id,character_object_id,goal_id,goal_revision,operation_kind,operation_state,attempt_no,intent_id,authority_hash,intent_hash,activity_generation,activity_tick,row_version FROM phantom_economy_operations WHERE profile_id=? AND operation_state IN (" + ACTIVE_STATES + ") ORDER BY operation_id"))
		{
			statement.setLong(1, profileId);
			try (ResultSet rows = statement.executeQuery())
			{
				if (!rows.next())
				{
					return Optional.empty();
				}
				final StoredOperation result = storedOperation(rows);
				if (rows.next())
				{
					throw new IllegalStateException("Multiple active economy operations exist for one profile.");
				}
				return Optional.of(result);
			}
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("find active economy operation", exception);
		}
	}

	public List<Reservation> findReservations(String operationId)
	{
		if ((operationId == null) || !operationId.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid economy operation ID.");
		}
		final List<Reservation> result = new ArrayList<>();
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT profile_id,owner_object_id,owner_class_index,resource_kind,object_id,item_id,reserved_count,expected_count,expected_enchant_level,expected_location FROM phantom_economy_reservations WHERE operation_id=? ORDER BY canonical_resource_key"))
		{
			statement.setString(1, operationId);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.add(new Reservation(rows.getLong(1), rows.getInt(2), rows.getInt(3), PhantomEconomyOperation.ResourceKind.valueOf(rows.getString(4)), rows.getInt(5), rows.getInt(6), rows.getLong(7), rows.getLong(8), rows.getInt(9), rows.getString(10)));
				}
			}
			return List.copyOf(result);
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("find economy reservations", exception);
		}
	}

	public int nextAttempt(long profileId, long goalId, PhantomEconomyOperation.Kind kind, int maximum)
	{
		if ((profileId <= 0) || (goalId <= 0) || (kind == null) || (maximum < 1) || (maximum > 32))
		{
			throw new IllegalArgumentException("Invalid economy attempt query.");
		}
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(attempt_no),0) FROM phantom_economy_operations WHERE profile_id=? AND goal_id=? AND operation_kind=?"))
		{
			statement.setLong(1, profileId);
			statement.setLong(2, goalId);
			statement.setString(3, kind.name());
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next())
				{
					throw new IllegalStateException("Economy attempt query returned no aggregate row.");
				}
				final int next = Math.addExact(row.getInt(1), 1);
				if (next > maximum)
				{
					throw new IllegalStateException("Economy attempt limit is exhausted.");
				}
				return next;
			}
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("read economy attempt sequence", exception);
		}
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_admissionOpen.get(), _prepared.sum(), _reserved.sum(), _dispatched.sum(), _committed.sum(), _aborted.sum(), _conflicts.sum(), _expired.sum(), _inconsistent.sum(), _currentOperations.get(), _currentReservations.get());
	}

	private List<Long> participantProfiles(PhantomEconomyOperation operation, List<Reservation> reservations)
	{
		final Set<Long> profiles = new HashSet<>();
		profiles.add(operation.identity().profileId());
		reservations.forEach(reservation -> profiles.add(reservation.profileId()));
		if (profiles.size() > _policy.limits().participantsPerOperation())
		{
			throw new IllegalArgumentException("Economy participant bound exceeded.");
		}
		return profiles.stream().sorted().toList();
	}

	private Map<Long, Integer> lockProfiles(Connection connection, List<Long> profileIds) throws SQLException
	{
		final List<Long> ordered = profileIds.stream().distinct().sorted().toList();
		final Map<Long, Integer> links = new java.util.LinkedHashMap<>();
		for (long profileId : ordered)
		{
			try (PreparedStatement statement = connection.prepareStatement("SELECT character_object_id FROM phantom_profiles WHERE profile_id=? FOR UPDATE"))
			{
				statement.setLong(1, profileId);
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next())
					{
						throw new IllegalStateException("Economy participant profile does not exist.");
					}
					final int characterObjectId = result.getInt(1);
					links.put(profileId, result.wasNull() ? null : characterObjectId);
				}
			}
		}
		return links;
	}

	private StoredOperation readOperation(Connection connection, String operationId) throws SQLException
	{
		return queryOperation(connection, "SELECT operation_id,profile_id,character_object_id,goal_id,goal_revision,operation_kind,operation_state,attempt_no,intent_id,authority_hash,intent_hash,activity_generation,activity_tick,row_version FROM phantom_economy_operations WHERE operation_id=?", operationId);
	}

	private StoredOperation lockOperation(Connection connection, String operationId) throws SQLException
	{
		return queryOperation(connection, "SELECT operation_id,profile_id,character_object_id,goal_id,goal_revision,operation_kind,operation_state,attempt_no,intent_id,authority_hash,intent_hash,activity_generation,activity_tick,row_version FROM phantom_economy_operations WHERE operation_id=? FOR UPDATE", operationId);
	}

	private StoredOperation queryOperation(Connection connection, String sql, String operationId) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, operationId);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() ? storedOperation(result) : null;
			}
		}
	}

	private static StoredOperation storedOperation(ResultSet row) throws SQLException
	{
		return new StoredOperation(row.getString(1), row.getLong(2), row.getInt(3), row.getLong(4), row.getLong(5), PhantomEconomyOperation.Kind.valueOf(row.getString(6)), State.valueOf(row.getString(7)), row.getInt(8), row.getString(9), row.getString(10), row.getString(11), row.getLong(12), row.getLong(13), row.getLong(14));
	}

	private boolean hasAnotherActiveOperation(Connection connection, long profileId, String operationId) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("SELECT o.operation_id FROM phantom_economy_operations o WHERE o.operation_state IN (" + ACTIVE_STATES + ") AND o.operation_id<>? AND (o.profile_id=? OR EXISTS (SELECT 1 FROM phantom_economy_reservations r WHERE r.operation_id=o.operation_id AND r.profile_id=?)) ORDER BY o.operation_id LIMIT 1 FOR UPDATE"))
		{
			statement.setString(1, operationId);
			statement.setLong(2, profileId);
			statement.setLong(3, profileId);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next();
			}
		}
	}

	private List<StoredOperation> lockActiveOperations(Connection connection, long profileId) throws SQLException
	{
		final List<StoredOperation> result = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("SELECT operation_id,profile_id,character_object_id,goal_id,goal_revision,operation_kind,operation_state,attempt_no,intent_id,authority_hash,intent_hash,activity_generation,activity_tick,row_version FROM phantom_economy_operations WHERE profile_id=? AND operation_state IN (" + ACTIVE_STATES + ") ORDER BY operation_id FOR UPDATE"))
		{
			statement.setLong(1, profileId);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.add(storedOperation(rows));
				}
			}
		}
		return result;
	}

	private void insertOperation(Connection connection, PhantomEconomyOperation operation) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO phantom_economy_operations(operation_id,profile_id,character_object_id,goal_id,goal_revision,operation_kind,operation_state,attempt_no,intent_id,authority_hash,intent_hash,activity_generation,activity_tick,before_payload,intent_payload,row_version,expires_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"))
		{
			int index = 0;
			statement.setString(++index, operation.operationId());
			statement.setLong(++index, operation.identity().profileId());
			statement.setInt(++index, operation.identity().characterObjectId());
			statement.setLong(++index, operation.identity().goalId());
			statement.setLong(++index, operation.identity().goalRevision());
			statement.setString(++index, operation.kind().name());
			statement.setString(++index, operation.state().name());
			statement.setInt(++index, operation.identity().attempt());
			statement.setString(++index, operation.identity().intentId());
			statement.setString(++index, operation.authorityHash());
			statement.setString(++index, operation.intentHash());
			statement.setLong(++index, operation.identity().activityGeneration());
			statement.setLong(++index, operation.identity().activityTick());
			statement.setBytes(++index, operation.beforePayload());
			statement.setBytes(++index, operation.intentPayload());
			statement.setLong(++index, operation.rowVersion());
			statement.setTimestamp(++index, new Timestamp(operation.expiresEpochMillis()));
			statement.setTimestamp(++index, new Timestamp(operation.createdEpochMillis()));
			statement.setTimestamp(++index, new Timestamp(operation.updatedEpochMillis()));
			requireOne(statement.executeUpdate(), "economy operation insert");
		}
	}

	private static void insertReservation(Connection connection, String operationId, int ordinal, Reservation reservation) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO phantom_economy_reservations(canonical_resource_key,operation_id,reservation_ordinal,profile_id,owner_object_id,owner_class_index,resource_kind,object_id,item_id,reserved_count,expected_count,expected_enchant_level,expected_location) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)"))
		{
			statement.setString(1, reservation.canonicalKey());
			statement.setString(2, operationId);
			statement.setInt(3, ordinal);
			statement.setLong(4, reservation.profileId());
			statement.setInt(5, reservation.ownerObjectId());
			statement.setInt(6, reservation.ownerClassIndex());
			statement.setString(7, reservation.kind().name());
			statement.setInt(8, reservation.objectId());
			statement.setInt(9, reservation.itemId());
			statement.setLong(10, reservation.count());
			statement.setLong(11, reservation.expectedCount());
			statement.setInt(12, reservation.expectedEnchantLevel());
			statement.setString(13, reservation.expectedLocation());
			requireOne(statement.executeUpdate(), "economy reservation insert");
		}
	}

	private static List<String> lockReservationKeys(Connection connection, String operationId) throws SQLException
	{
		final List<String> keys = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("SELECT canonical_resource_key FROM phantom_economy_reservations WHERE operation_id=? ORDER BY canonical_resource_key FOR UPDATE"))
		{
			statement.setString(1, operationId);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					keys.add(rows.getString(1));
				}
			}
		}
		return List.copyOf(keys);
	}

	private static void transition(Connection connection, String operationId, State expected, State next, long nowEpochMillis) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("UPDATE phantom_economy_operations SET operation_state=?,updated_at=?,row_version=row_version+1 WHERE operation_id=? AND operation_state=?"))
		{
			statement.setString(1, next.name());
			statement.setTimestamp(2, new Timestamp(nowEpochMillis));
			statement.setString(3, operationId);
			statement.setString(4, expected.name());
			requireOne(statement.executeUpdate(), "economy operation transition");
		}
	}

	private static void insertAudit(Connection connection, StoredOperation operation, State terminal, Audit audit) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO phantom_economy_audit(operation_id,profile_id,character_object_id,operation_kind,terminal_state,result_code,reason_key,authority_hash,intent_hash,consequence_payload,items_consumed,items_produced,adena_source,adena_sink,crystals_produced,target_items_destroyed) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"))
		{
			statement.setString(1, operation.operationId());
			statement.setLong(2, operation.profileId());
			statement.setInt(3, operation.characterObjectId());
			statement.setString(4, operation.kind().name());
			statement.setString(5, terminal.name());
			statement.setString(6, audit.result().name());
			statement.setString(7, audit.reason());
			statement.setString(8, operation.authorityHash());
			statement.setString(9, operation.intentHash());
			statement.setBytes(10, audit.consequencePayload());
			statement.setLong(11, audit.itemsConsumed());
			statement.setLong(12, audit.itemsProduced());
			statement.setLong(13, audit.adenaSource());
			statement.setLong(14, audit.adenaSink());
			statement.setLong(15, audit.crystalsProduced());
			statement.setLong(16, audit.targetItemsDestroyed());
			requireOne(statement.executeUpdate(), "economy audit insert");
		}
	}

	private static void setTerminal(Connection connection, String operationId, Audit audit) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("UPDATE phantom_economy_operations SET terminal_result=?,terminal_reason=? WHERE operation_id=?"))
		{
			statement.setString(1, audit.result().name());
			statement.setString(2, audit.reason());
			statement.setString(3, operationId);
			requireOne(statement.executeUpdate(), "economy terminal result");
		}
	}

	private static int deleteReservations(Connection connection, String operationId) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_economy_reservations WHERE operation_id=?"))
		{
			statement.setString(1, operationId);
			return statement.executeUpdate();
		}
	}

	private static long count(Connection connection, String sql) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet row = statement.executeQuery())
		{
			if (!row.next())
			{
				throw new SQLException("Economy count query returned no row.");
			}
			return row.getLong(1);
		}
	}

	private void recordTerminal(State state)
	{
		switch (state)
		{
			case COMMITTED -> _committed.increment();
			case ABORTED -> _aborted.increment();
			case EXPIRED -> _expired.increment();
			case INCONSISTENT -> _inconsistent.increment();
			default -> throw new IllegalArgumentException("State is not terminal.");
		}
	}

	private void trimAudit(Connection connection, long profileId) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_economy_audit WHERE profile_id=? AND audit_id NOT IN (SELECT audit_id FROM (SELECT audit_id FROM phantom_economy_audit WHERE profile_id=? ORDER BY audit_id DESC LIMIT ?) retained)"))
		{
			statement.setLong(1, profileId);
			statement.setLong(2, profileId);
			statement.setInt(3, _policy.limits().auditRowsPerProfile());
			statement.executeUpdate();
		}
	}

	private static boolean hasReservationConflict(Connection connection, String ownOperationId, List<Reservation> resources) throws SQLException
	{
		for (Reservation resource : resources.stream().sorted(Reservation.CANONICAL_ORDER).toList())
		{
			final boolean itemCount = resource.kind() == PhantomEconomyOperation.ResourceKind.ITEM_COUNT;
			final boolean itemObject = resource.kind() == PhantomEconomyOperation.ResourceKind.ITEM_OBJECT;
			final String sql = itemCount ? "SELECT operation_id FROM phantom_economy_reservations WHERE canonical_resource_key=? OR (owner_object_id=? AND item_id=? AND resource_kind='ITEM_OBJECT') ORDER BY canonical_resource_key FOR UPDATE" : itemObject ? "SELECT operation_id FROM phantom_economy_reservations WHERE canonical_resource_key=? OR (owner_object_id=? AND item_id=? AND resource_kind='ITEM_COUNT') ORDER BY canonical_resource_key FOR UPDATE" : "SELECT operation_id FROM phantom_economy_reservations WHERE canonical_resource_key=? FOR UPDATE";
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				statement.setString(1, resource.canonicalKey());
				if (itemCount || itemObject)
				{
					statement.setInt(2, resource.ownerObjectId());
					statement.setInt(3, resource.itemId());
				}
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						if (!Objects.equals(ownOperationId, result.getString(1)))
						{
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static boolean hasSemanticOverlap(List<Reservation> resources)
	{
		for (int left = 0; left < resources.size(); left++)
		{
			final Reservation first = resources.get(left);
			for (int right = left + 1; right < resources.size(); right++)
			{
				final Reservation second = resources.get(right);
				if (first.overlaps(second))
				{
					return true;
				}
			}
		}
		return false;
	}

	private List<String> findShutdownCandidates(int maximum)
	{
		final List<String> result = new ArrayList<>();
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT operation_id FROM phantom_economy_operations WHERE operation_state IN ('PREPARED','RESERVED','DISPATCHING','OBSERVING') ORDER BY profile_id,operation_id LIMIT ?"))
		{
			statement.setInt(1, maximum);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.add(rows.getString(1));
				}
			}
			return result;
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("find shutdown economy operations", exception);
		}
	}

	private List<StoredOperation> findObserving(int maximum)
	{
		final List<StoredOperation> result = new ArrayList<>();
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT operation_id,profile_id,character_object_id,goal_id,goal_revision,operation_kind,operation_state,attempt_no,intent_id,authority_hash,intent_hash,activity_generation,activity_tick,row_version FROM phantom_economy_operations WHERE operation_state='OBSERVING' ORDER BY profile_id,operation_id LIMIT ?"))
		{
			statement.setInt(1, maximum);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.add(storedOperation(rows));
				}
			}
			return List.copyOf(result);
		}
		catch (SQLException exception)
		{
			throw persistenceFailure("find uncertain economy operations", exception);
		}
	}

	private ReentrantLock writerLock(long profileId)
	{
		return _writerLocks[Math.floorMod(Long.hashCode(profileId), _writerLocks.length)];
	}

	private static void begin(Connection connection) throws SQLException
	{
		connection.setAutoCommit(false);
	}

	private static void requireOne(int count, String operation)
	{
		if (count != 1)
		{
			throw new IllegalStateException("Unexpected row count for " + operation + ".");
		}
	}

	private static void rollback(Connection connection, Throwable failure)
	{
		try
		{
			connection.rollback();
		}
		catch (SQLException rollbackFailure)
		{
			failure.addSuppressed(rollbackFailure);
		}
	}

	private static IllegalStateException persistenceFailure(String operation, Throwable failure)
	{
		return new IllegalStateException("Could not " + operation + ".", failure);
	}

	public enum Status
	{
		RESERVED,
		TRANSITIONED,
		IDEMPOTENT,
		ADMISSION_CLOSED,
		IDENTITY_CONFLICT,
		PROFILE_BUSY,
		RESOURCE_CONFLICT,
		LIMIT_REACHED,
		STATE_CONFLICT,
		NOT_FOUND,
		REDISPATCH_ALLOWED
	}

	public enum Evidence
	{
		EXACT_BEFORE_ACTION_NOT_ISSUED,
		EXACT_BEFORE_ACTION_ISSUED,
		EXACT_AFTER,
		PARTIAL,
		AMBIGUOUS
	}

	@FunctionalInterface
	public interface ConnectionProvider
	{
		Connection open() throws SQLException;
	}

	public record ReserveResult(Status status, State state, String operationId)
	{
		public static ReserveResult rejected(Status status)
		{
			return new ReserveResult(status, null, null);
		}
	}

	public record TransitionResult(Status status, State state)
	{
	}

	public record ReconcileResult(Status status, State state, boolean redispatchAllowed)
	{
	}

	public record Snapshot(boolean admissionOpen, long prepared, long reserved, long dispatched, long committed, long aborted, long conflicts, long expired, long inconsistent, long currentOperations, long currentReservations)
	{
	}

	public record StoredOperation(String operationId, long profileId, int characterObjectId, long goalId, long goalRevision, PhantomEconomyOperation.Kind kind, State state, int attempt, String intentId, String authorityHash, String intentHash, long activityGeneration, long activityTick, long rowVersion)
	{
		public boolean matches(PhantomEconomyOperation operation)
		{
			return operationId.equals(operation.operationId()) && (profileId == operation.identity().profileId()) && (characterObjectId == operation.identity().characterObjectId()) && (goalId == operation.identity().goalId()) && (goalRevision == operation.identity().goalRevision()) && (kind == operation.kind()) && (attempt == operation.identity().attempt()) && intentId.equals(operation.identity().intentId()) && authorityHash.equals(operation.authorityHash()) && intentHash.equals(operation.intentHash()) && (activityGeneration == operation.identity().activityGeneration()) && (activityTick == operation.identity().activityTick());
		}
	}

	public record DispatchLock(StoredOperation operation, List<String> canonicalResourceKeys)
	{
	}

	public static final class WriterClaim implements AutoCloseable
	{
		private final ReentrantLock _lock;
		private final Connection _connection;
		private final boolean _acquired;
		private boolean _closed;

		private WriterClaim(ReentrantLock lock, Connection connection, boolean acquired)
		{
			_lock = lock;
			_connection = connection;
			_acquired = acquired;
		}

		private static WriterClaim conflict()
		{
			return new WriterClaim(null, null, false);
		}

		public boolean acquired()
		{
			return _acquired;
		}

		@Override
		public void close()
		{
			if (_closed || !_acquired)
			{
				return;
			}
			_closed = true;
			if (_connection == null)
			{
				_lock.unlock();
				return;
			}
			Throwable failure = null;
			try
			{
				_connection.commit();
			}
			catch (SQLException exception)
			{
				failure = exception;
				rollback(_connection, exception);
			}
			finally
			{
				try
				{
					_connection.close();
				}
				catch (SQLException exception)
				{
					failure = failure == null ? exception : failure;
				}
				_lock.unlock();
			}
			if (failure != null)
			{
				throw persistenceFailure("release economy writer claim", failure);
			}
		}
	}

	public static final class EconomyConflictException extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		public EconomyConflictException(String message)
		{
			super(message);
		}
	}
}

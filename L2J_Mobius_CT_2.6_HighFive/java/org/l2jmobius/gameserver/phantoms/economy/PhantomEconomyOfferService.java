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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOffer.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.ConnectionProvider;

/** Durable bounded lifecycle for immutable economy offer terms. */
public final class PhantomEconomyOfferService
{
	private static final String ACTIVE_STATES = "'DRAFT','OFFERED','ACCEPTED'";
	private final ConnectionProvider _connections;
	private final LongAdder _drafted = new LongAdder();
	private final LongAdder _offered = new LongAdder();
	private final LongAdder _accepted = new LongAdder();
	private final LongAdder _rejected = new LongAdder();
	private final LongAdder _expired = new LongAdder();
	private final LongAdder _cancelled = new LongAdder();

	public PhantomEconomyOfferService()
	{
		this(DatabaseFactory::getConnection);
	}

	public PhantomEconomyOfferService(ConnectionProvider connections)
	{
		_connections = Objects.requireNonNull(connections);
	}

	public Status create(PhantomEconomyOffer offer)
	{
		Objects.requireNonNull(offer);
		try (Connection connection = _connections.open())
		{
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO phantom_economy_offers (offer_id,initiating_profile_id,initiating_character_object_id,operation_kind,counterparty_kind,counterparty_profile_id,counterparty_character_object_id,offer_state,content_hash,offer_payload,initiator_lines,counterparty_lines,goal_id,goal_revision,operation_id,terminal_reason,row_version,expires_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,FROM_UNIXTIME(? / 1000.0),FROM_UNIXTIME(? / 1000.0),FROM_UNIXTIME(? / 1000.0))"))
			{
				int index = 0;
				statement.setString(++index, offer.offerId());
				statement.setLong(++index, offer.initiatingProfileId());
				statement.setInt(++index, offer.initiatingCharacterObjectId());
				statement.setString(++index, offer.operationKind().name());
				statement.setString(++index, offer.counterpartyKind().name());
				statement.setLong(++index, offer.counterpartyProfileId());
				statement.setInt(++index, offer.counterpartyCharacterObjectId());
				statement.setString(++index, offer.state().name());
				statement.setString(++index, offer.contentHash());
				statement.setBytes(++index, offer.payload());
				statement.setInt(++index, offer.initiatorLines());
				statement.setInt(++index, offer.counterpartyLines());
				statement.setLong(++index, offer.goalId());
				statement.setLong(++index, offer.goalRevision());
				statement.setString(++index, offer.operationId());
				statement.setString(++index, offer.terminalReason());
				statement.setLong(++index, offer.rowVersion());
				statement.setLong(++index, offer.expiresEpochMillis());
				statement.setLong(++index, offer.createdEpochMillis());
				statement.setLong(++index, offer.updatedEpochMillis());
				statement.executeUpdate();
			}
			_drafted.increment();
			return Status.TRANSITIONED;
		}
		catch (SQLIntegrityConstraintViolationException exception)
		{
			return find(offer.offerId()).filter(existing -> sameTerms(existing, offer)).isPresent() ? Status.IDEMPOTENT : Status.CONFLICT;
		}
		catch (SQLException exception)
		{
			throw failure("create economy offer", exception);
		}
	}

	public Status offer(String offerId, long rowVersion, long nowEpochMillis)
	{
		final Status result = transition(offerId, State.DRAFT, State.OFFERED, rowVersion, nowEpochMillis, "", "", null);
		if (result == Status.TRANSITIONED)
		{
			_offered.increment();
		}
		return result;
	}

	public Status accept(String offerId, String expectedContentHash, long rowVersion, long nowEpochMillis)
	{
		final PhantomEconomyOffer current = find(offerId).orElse(null);
		if ((current == null) || !current.contentHash().equals(expectedContentHash) || (current.expiresEpochMillis() <= nowEpochMillis))
		{
			return Status.CONFLICT;
		}
		final Status result = transition(offerId, State.OFFERED, State.ACCEPTED, rowVersion, nowEpochMillis, "", "", expectedContentHash);
		if (result == Status.TRANSITIONED)
		{
			_accepted.increment();
		}
		return result;
	}

	public Status reject(String offerId, long rowVersion, long nowEpochMillis, String reason)
	{
		final Status result = transition(offerId, State.OFFERED, State.REJECTED, rowVersion, nowEpochMillis, "", reason, null);
		if (result == Status.TRANSITIONED)
		{
			_rejected.increment();
		}
		return result;
	}

	public Status cancel(String offerId, long rowVersion, long nowEpochMillis, String reason)
	{
		final PhantomEconomyOffer current = find(offerId).orElse(null);
		if ((current == null) || current.state().terminal())
		{
			return current != null && current.state() == State.CANCELLED ? Status.IDEMPOTENT : Status.CONFLICT;
		}
		final Status result = transition(offerId, current.state(), State.CANCELLED, rowVersion, nowEpochMillis, current.operationId(), reason, null);
		if (result == Status.TRANSITIONED)
		{
			_cancelled.increment();
		}
		return result;
	}

	public Status bindOperation(String offerId, String operationId, long rowVersion, long nowEpochMillis)
	{
		if ((operationId == null) || !operationId.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid offer operation identity.");
		}
		return transition(offerId, State.ACCEPTED, State.ACCEPTED, rowVersion, nowEpochMillis, operationId, "", null);
	}

	public Status consume(String offerId, String operationId, long rowVersion, long nowEpochMillis)
	{
		return transition(offerId, State.ACCEPTED, State.CONSUMED, rowVersion, nowEpochMillis, operationId, "result.success", null);
	}

	public Status inconsistent(String offerId, String operationId, long rowVersion, long nowEpochMillis, String reason)
	{
		return transition(offerId, State.ACCEPTED, State.INCONSISTENT, rowVersion, nowEpochMillis, operationId, reason, null);
	}

	public int expireDue(long nowEpochMillis, int limit)
	{
		if ((limit < 1) || (limit > 1000))
		{
			throw new IllegalArgumentException("Invalid economy offer expiry bound.");
		}
		final List<PhantomEconomyOffer> due = findDue(nowEpochMillis, limit);
		int count = 0;
		for (PhantomEconomyOffer offer : due)
		{
			if (transition(offer.offerId(), State.OFFERED, State.EXPIRED, offer.rowVersion(), nowEpochMillis, "", "offer.expired", null) == Status.TRANSITIONED)
			{
				count++;
				_expired.increment();
			}
		}
		return count;
	}

	public Optional<PhantomEconomyOffer> find(String offerId)
	{
		if ((offerId == null) || !offerId.matches("[0-9a-f]{64}"))
		{
			return Optional.empty();
		}
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement(select() + " WHERE offer_id=?"))
		{
			statement.setString(1, offerId);
			try (ResultSet row = statement.executeQuery())
			{
				return row.next() ? Optional.of(read(row)) : Optional.empty();
			}
		}
		catch (SQLException exception)
		{
			throw failure("find economy offer", exception);
		}
	}

	public Optional<PhantomEconomyOffer> findActive(long profileId, long goalId, long goalRevision)
	{
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement(select() + " WHERE initiating_profile_id=? AND goal_id=? AND goal_revision=? AND offer_state IN (" + ACTIVE_STATES + ") ORDER BY updated_at DESC,offer_id LIMIT 1"))
		{
			statement.setLong(1, profileId);
			statement.setLong(2, goalId);
			statement.setLong(3, goalRevision);
			try (ResultSet row = statement.executeQuery())
			{
				return row.next() ? Optional.of(read(row)) : Optional.empty();
			}
		}
		catch (SQLException exception)
		{
			throw failure("find active economy offer", exception);
		}
	}

	public List<PhantomEconomyOffer> findActiveAfter(String afterOfferId, int limit)
	{
		if ((afterOfferId == null) || (!afterOfferId.isEmpty() && !afterOfferId.matches("[0-9a-f]{64}")) || (limit < 1) || (limit > 1000))
		{
			throw new IllegalArgumentException("Invalid active economy offer scan.");
		}
		final java.util.ArrayList<PhantomEconomyOffer> result = new java.util.ArrayList<>();
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement(select() + " WHERE offer_state IN (" + ACTIVE_STATES + ") AND offer_id>? ORDER BY offer_id LIMIT ?"))
		{
			statement.setString(1, afterOfferId);
			statement.setInt(2, limit);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.add(read(rows));
				}
			}
			return List.copyOf(result);
		}
		catch (SQLException exception)
		{
			throw failure("scan active economy offers", exception);
		}
	}

	public boolean blocksMaterialization(long profileId)
	{
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM phantom_economy_offers WHERE offer_state='ACCEPTED' AND (initiating_profile_id=? OR counterparty_profile_id=?) LIMIT 1"))
		{
			statement.setLong(1, profileId);
			statement.setLong(2, profileId);
			try (ResultSet row = statement.executeQuery())
			{
				return row.next();
			}
		}
		catch (SQLException exception)
		{
			throw failure("check active economy offers", exception);
		}
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_drafted.sum(), _offered.sum(), _accepted.sum(), _rejected.sum(), _expired.sum(), _cancelled.sum());
	}

	private Status transition(String offerId, State expected, State next, long rowVersion, long now, String operationId, String reason, String contentHash)
	{
		if ((offerId == null) || !offerId.matches("[0-9a-f]{64}") || (rowVersion < 0) || (reason == null) || (reason.length() > 96))
		{
			throw new IllegalArgumentException("Invalid economy offer transition.");
		}
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				final PhantomEconomyOffer current = lock(connection, offerId);
				if (current == null)
				{
					connection.rollback();
					return Status.NOT_FOUND;
				}
				if ((current.state() == next) && current.operationId().equals(operationId) && current.terminalReason().equals(reason))
				{
					connection.commit();
					return Status.IDEMPOTENT;
				}
				if ((current.state() != expected) || (current.rowVersion() != rowVersion) || ((contentHash != null) && !current.contentHash().equals(contentHash)) || (!current.operationId().isEmpty() && !current.operationId().equals(operationId)))
				{
					connection.rollback();
					return Status.CONFLICT;
				}
				try (PreparedStatement statement = connection.prepareStatement("UPDATE phantom_economy_offers SET offer_state=?,operation_id=?,terminal_reason=?,updated_at=FROM_UNIXTIME(? / 1000.0),row_version=row_version+1 WHERE offer_id=? AND row_version=?"))
				{
					statement.setString(1, next.name());
					statement.setString(2, operationId);
					statement.setString(3, reason);
					statement.setLong(4, now);
					statement.setString(5, offerId);
					statement.setLong(6, rowVersion);
					if (statement.executeUpdate() != 1)
					{
						connection.rollback();
						return Status.CONFLICT;
					}
				}
				connection.commit();
				return Status.TRANSITIONED;
			}
			catch (Throwable failure)
			{
				connection.rollback();
				throw failure;
			}
		}
		catch (SQLException exception)
		{
			throw failure("transition economy offer", exception);
		}
	}

	private List<PhantomEconomyOffer> findDue(long now, int limit)
	{
		final java.util.ArrayList<PhantomEconomyOffer> result = new java.util.ArrayList<>();
		try (Connection connection = _connections.open(); PreparedStatement statement = connection.prepareStatement(select() + " WHERE offer_state='OFFERED' AND expires_at<=FROM_UNIXTIME(? / 1000.0) ORDER BY expires_at,offer_id LIMIT ?"))
		{
			statement.setLong(1, now);
			statement.setInt(2, limit);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.add(read(rows));
				}
			}
			return List.copyOf(result);
		}
		catch (SQLException exception)
		{
			throw failure("find expired economy offers", exception);
		}
	}

	private PhantomEconomyOffer lock(Connection connection, String offerId) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(select() + " WHERE offer_id=? FOR UPDATE"))
		{
			statement.setString(1, offerId);
			try (ResultSet row = statement.executeQuery())
			{
				return row.next() ? read(row) : null;
			}
		}
	}

	private static String select()
	{
		return "SELECT offer_id,initiating_profile_id,initiating_character_object_id,operation_kind,counterparty_kind,counterparty_profile_id,counterparty_character_object_id,offer_state,content_hash,offer_payload,initiator_lines,counterparty_lines,goal_id,goal_revision,operation_id,terminal_reason,UNIX_TIMESTAMP(created_at)*1000,UNIX_TIMESTAMP(updated_at)*1000,UNIX_TIMESTAMP(expires_at)*1000,row_version FROM phantom_economy_offers";
	}

	private static PhantomEconomyOffer read(ResultSet row) throws SQLException
	{
		return new PhantomEconomyOffer(row.getString(1), row.getLong(2), row.getInt(3), PhantomEconomyOperation.Kind.valueOf(row.getString(4)), PhantomEconomyOffer.CounterpartyKind.valueOf(row.getString(5)), row.getLong(6), row.getInt(7), State.valueOf(row.getString(8)), row.getString(9), row.getBytes(10), row.getInt(11), row.getInt(12), row.getLong(13), row.getLong(14), row.getString(15), row.getString(16), row.getLong(17), row.getLong(18), row.getLong(19), row.getLong(20));
	}

	private static boolean sameTerms(PhantomEconomyOffer first, PhantomEconomyOffer second)
	{
		// offerId already commits to the exact expiry; avoid a redundant JDBC TIMESTAMP(3) round-trip comparison.
		return first.offerId().equals(second.offerId()) && (first.initiatingProfileId() == second.initiatingProfileId()) && (first.initiatingCharacterObjectId() == second.initiatingCharacterObjectId()) && (first.operationKind() == second.operationKind()) && (first.counterpartyKind() == second.counterpartyKind()) && (first.counterpartyProfileId() == second.counterpartyProfileId()) && (first.counterpartyCharacterObjectId() == second.counterpartyCharacterObjectId()) && first.contentHash().equals(second.contentHash()) && Arrays.equals(first.payload(), second.payload()) && (first.initiatorLines() == second.initiatorLines()) && (first.counterpartyLines() == second.counterpartyLines()) && (first.goalId() == second.goalId()) && (first.goalRevision() == second.goalRevision());
	}

	private static IllegalStateException failure(String action, Exception cause)
	{
		return new IllegalStateException("Could not " + action + ".", cause);
	}

	public enum Status
	{
		TRANSITIONED,
		IDEMPOTENT,
		CONFLICT,
		NOT_FOUND
	}

	public record Snapshot(long drafted, long offered, long accepted, long rejected, long expired, long cancelled)
	{
	}
}

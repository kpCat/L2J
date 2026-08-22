/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.l2jmobius.commons.database.DatabaseFactory;

/**
 * Narrow durable persistence contract for canonical alliance and clan-war mutations.
 */
interface ClanSocialPersistence
{
	long createAlliance(int leaderClanId, long expectedGeneration, long expectedGenerationCounter, String allianceName) throws SQLException, ClanSocialRepository.StaleStateException;
	void joinAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, String allianceName, int allianceCrestId, long targetExpectedGeneration, long targetExpectedGenerationCounter) throws SQLException, ClanSocialRepository.StaleStateException;
	void leaveAlliance(int clanId, int allianceId, long generation, long expectedGenerationCounter, long penaltyExpiryTime, int penaltyType) throws SQLException, ClanSocialRepository.StaleStateException;
	void expelAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, long leaderExpectedGenerationCounter, long targetExpectedGenerationCounter, long leaderPenaltyExpiryTime, long targetPenaltyExpiryTime, int targetPenaltyType) throws SQLException, ClanSocialRepository.StaleStateException;
	void dissolveAlliance(int leaderClanId, int allianceId, long generation, Map<Integer, Long> memberGenerationCounters, long leaderPenaltyExpiryTime) throws SQLException, ClanSocialRepository.StaleStateException;
	void repairOrphanAlliance(int clanId, int allianceId, long generation, long expectedGenerationCounter) throws SQLException, ClanSocialRepository.StaleStateException;
	void changeAllianceCrest(int allianceId, long generation, List<Integer> memberClanIds, int crestId) throws SQLException, ClanSocialRepository.StaleStateException;
	void clearClanAllianceCrest(int clanId) throws SQLException, ClanSocialRepository.StaleStateException;
	List<ClanSocialRepository.WarRow> loadWars() throws SQLException;
	ClanSocialRepository.WarRow createWar(int sourceClanId, int targetClanId) throws SQLException, ClanSocialRepository.StaleStateException;
	void deleteWar(ClanSocialRepository.WarRow war) throws SQLException, ClanSocialRepository.StaleStateException;
	void deleteWars(Collection<ClanSocialRepository.WarRow> wars) throws SQLException, ClanSocialRepository.StaleStateException;
}
final class ClanSocialRepository implements ClanSocialPersistence
{
	private static final String ALLIANCE_INCARNATION_SEQUENCE = "alliance_incarnation";

	record WarRow(long warId, int sourceClanId, int targetClanId)
	{
		WarRow
		{
			if ((warId <= 0) || (sourceClanId <= 0) || (targetClanId <= 0) || (sourceClanId == targetClanId))
			{
				throw new IllegalArgumentException("Invalid durable clan war row.");
			}
		}
	}

	static final class StaleStateException extends Exception
	{
		private static final long serialVersionUID = 1L;

		StaleStateException(String message)
		{
			super(message);
		}
	}

	private record AllianceRow(int clanId, int allianceId, String allianceName, long generation, long generationCounter, int crestId)
	{
	}

	@FunctionalInterface
	private interface SqlWrite<T>
	{
		T execute(Connection connection) throws SQLException, StaleStateException;
	}

	private static final ClanSocialRepository INSTANCE = new ClanSocialRepository();

	private ClanSocialRepository()
	{
	}

	static ClanSocialRepository getInstance()
	{
		return INSTANCE;
	}
	@Override
	public long createAlliance(int leaderClanId, long expectedGeneration, long expectedGenerationCounter, String allianceName) throws SQLException, StaleStateException
	{
		return write(connection ->
		{
			final AllianceRow current = lockClanRow(connection, leaderClanId);
			if ((current.allianceId() != 0) || (current.generation() != expectedGeneration) || (current.generationCounter() != expectedGenerationCounter))
			{
				throw new StaleStateException("Leader clan alliance state changed before create.");
			}
			final long nextGeneration = allocateAllianceIncarnation(connection);
			final long nextEpoch = Math.addExact(expectedGenerationCounter, 1);
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_id=?, ally_name=?, ally_generation=?, ally_generation_counter=?, ally_penalty_expiry_time=0, ally_penalty_type=0 WHERE clan_id=? AND ally_id=0 AND ally_generation=? AND ally_generation_counter=?"))
			{
				statement.setInt(1, leaderClanId);
				statement.setString(2, allianceName);
				statement.setLong(3, nextGeneration);
				statement.setLong(4, nextEpoch);
				statement.setInt(5, leaderClanId);
				statement.setLong(6, expectedGeneration);
				statement.setLong(7, expectedGenerationCounter);
				requireSingleRow(statement.executeUpdate(), "create alliance");
			}
			return nextGeneration;
		});
	}

	private static long allocateAllianceIncarnation(Connection connection) throws SQLException, StaleStateException
	{
		final long highWater;
		try (PreparedStatement statement = connection.prepareStatement("SELECT high_water FROM clan_social_identity WHERE identity_name=? FOR UPDATE"))
		{
			statement.setString(1, ALLIANCE_INCARNATION_SEQUENCE);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw new SQLException("Alliance incarnation allocator row is missing.");
				}
				highWater = result.getLong("high_water");
			}
		}
		if (highWater == Long.MAX_VALUE)
		{
			throw new SQLException("Alliance incarnation allocator is exhausted.");
		}
		final long next = highWater + 1;
		try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_social_identity SET high_water=? WHERE identity_name=? AND high_water=?"))
		{
			statement.setLong(1, next);
			statement.setString(2, ALLIANCE_INCARNATION_SEQUENCE);
			statement.setLong(3, highWater);
			requireSingleRow(statement.executeUpdate(), "allocate alliance incarnation");
		}
		return next;
	}

	@Override
	public void joinAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, String allianceName, int allianceCrestId, long targetExpectedGeneration, long targetExpectedGenerationCounter) throws SQLException, StaleStateException
	{
		final long nextTargetEpoch = Math.addExact(targetExpectedGenerationCounter, 1);
		write(connection ->
		{
			final List<AllianceRow> rows = lockClanRows(connection, leaderClanId, targetClanId);
			final AllianceRow leader = row(rows, leaderClanId);
			final AllianceRow target = row(rows, targetClanId);
			requireAlliance(leader, allianceId, generation);
			if ((target.allianceId() != 0) || (target.generation() != targetExpectedGeneration) || (target.generationCounter() != targetExpectedGenerationCounter))
			{
				throw new StaleStateException("Target clan alliance state changed before join.");
			}
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_id=?, ally_name=?, ally_generation=?, ally_generation_counter=?, ally_crest_id=?, ally_penalty_expiry_time=0, ally_penalty_type=0 WHERE clan_id=? AND ally_id=0 AND ally_generation=? AND ally_generation_counter=?"))
			{
				statement.setInt(1, allianceId);
				statement.setString(2, allianceName);
				statement.setLong(3, generation);
				statement.setLong(4, nextTargetEpoch);
				statement.setInt(5, allianceCrestId);
				statement.setInt(6, targetClanId);
				statement.setLong(7, targetExpectedGeneration);
				statement.setLong(8, targetExpectedGenerationCounter);
				requireSingleRow(statement.executeUpdate(), "join alliance");
			}
			return null;
		});
	}

	@Override
	public void leaveAlliance(int clanId, int allianceId, long generation, long expectedGenerationCounter, long penaltyExpiryTime, int penaltyType) throws SQLException, StaleStateException
	{
		final long nextEpoch = Math.addExact(expectedGenerationCounter, 1);
		write(connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_id=0, ally_name=NULL, ally_generation=0, ally_generation_counter=?, ally_crest_id=0, ally_penalty_expiry_time=?, ally_penalty_type=? WHERE clan_id=? AND ally_id=? AND ally_generation=? AND ally_generation_counter=?"))
			{
				statement.setLong(1, nextEpoch);
				statement.setLong(2, penaltyExpiryTime);
				statement.setInt(3, penaltyType);
				statement.setInt(4, clanId);
				statement.setInt(5, allianceId);
				statement.setLong(6, generation);
				statement.setLong(7, expectedGenerationCounter);
				requireSingleRow(statement.executeUpdate(), "leave alliance");
			}
			return null;
		});
	}
	@Override
	public void expelAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, long leaderExpectedGenerationCounter, long targetExpectedGenerationCounter, long leaderPenaltyExpiryTime, long targetPenaltyExpiryTime, int targetPenaltyType) throws SQLException, StaleStateException
	{
		final long nextTargetEpoch = Math.addExact(targetExpectedGenerationCounter, 1);
		write(connection ->
		{
			final List<AllianceRow> rows = lockClanRows(connection, leaderClanId, targetClanId);
			final AllianceRow leaderRow = row(rows, leaderClanId);
			final AllianceRow targetRow = row(rows, targetClanId);
			requireAlliance(leaderRow, allianceId, generation);
			requireAlliance(targetRow, allianceId, generation);
			if ((leaderRow.generationCounter() != leaderExpectedGenerationCounter) || (targetRow.generationCounter() != targetExpectedGenerationCounter))
			{
				throw new StaleStateException("Alliance membership epoch changed before expulsion.");
			}
			try (PreparedStatement leader = connection.prepareStatement("UPDATE clan_data SET ally_penalty_expiry_time=?, ally_penalty_type=? WHERE clan_id=? AND ally_id=? AND ally_generation=? AND ally_generation_counter=?");
				PreparedStatement target = connection.prepareStatement("UPDATE clan_data SET ally_id=0, ally_name=NULL, ally_generation=0, ally_generation_counter=?, ally_crest_id=0, ally_penalty_expiry_time=?, ally_penalty_type=? WHERE clan_id=? AND ally_id=? AND ally_generation=? AND ally_generation_counter=?"))
			{
				leader.setLong(1, leaderPenaltyExpiryTime);
				leader.setInt(2, Clan.PENALTY_TYPE_DISMISS_CLAN);
				leader.setInt(3, leaderClanId);
				leader.setInt(4, allianceId);
				leader.setLong(5, generation);
				leader.setLong(6, leaderExpectedGenerationCounter);
				requireSingleRow(leader.executeUpdate(), "persist alliance expulsion leader penalty");

				target.setLong(1, nextTargetEpoch);
				target.setLong(2, targetPenaltyExpiryTime);
				target.setInt(3, targetPenaltyType);
				target.setInt(4, targetClanId);
				target.setInt(5, allianceId);
				target.setLong(6, generation);
				target.setLong(7, targetExpectedGenerationCounter);
				requireSingleRow(target.executeUpdate(), "persist expelled clan");
			}
			return null;
		});
	}
	@Override
	public void dissolveAlliance(int leaderClanId, int allianceId, long generation, Map<Integer, Long> memberGenerationCounters, long leaderPenaltyExpiryTime) throws SQLException, StaleStateException
	{
		write(connection ->
		{
			requireAllianceMembers(lockAllianceRows(connection, allianceId), memberGenerationCounters, generation);
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_id=0, ally_name=NULL, ally_generation=0, ally_generation_counter=?, ally_crest_id=0, ally_penalty_expiry_time=?, ally_penalty_type=? WHERE clan_id=? AND ally_id=? AND ally_generation=? AND ally_generation_counter=?"))
			{
				for (Map.Entry<Integer, Long> member : memberGenerationCounters.entrySet())
				{
					final int clanId = member.getKey();
					final long expectedEpoch = member.getValue();
					statement.setLong(1, Math.addExact(expectedEpoch, 1));
					statement.setLong(2, clanId == leaderClanId ? leaderPenaltyExpiryTime : 0);
					statement.setInt(3, clanId == leaderClanId ? Clan.PENALTY_TYPE_DISSOLVE_ALLY : 0);
					statement.setInt(4, clanId);
					statement.setInt(5, allianceId);
					statement.setLong(6, generation);
					statement.setLong(7, expectedEpoch);
					requireSingleRow(statement.executeUpdate(), "dissolve alliance clan " + clanId);
				}
			}
			return null;
		});
	}
	@Override
	public void repairOrphanAlliance(int clanId, int allianceId, long generation, long expectedGenerationCounter) throws SQLException, StaleStateException
	{
		final long nextEpoch = Math.addExact(expectedGenerationCounter, 1);
		write(connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_id=0, ally_name=NULL, ally_generation=0, ally_generation_counter=?, ally_crest_id=0 WHERE clan_id=? AND ally_id=? AND ally_generation=? AND ally_generation_counter=?"))
			{
				statement.setLong(1, nextEpoch);
				statement.setInt(2, clanId);
				statement.setInt(3, allianceId);
				statement.setLong(4, generation);
				statement.setLong(5, expectedGenerationCounter);
				requireSingleRow(statement.executeUpdate(), "repair orphan alliance");
			}
			return null;
		});
	}
	@Override
	public void changeAllianceCrest(int allianceId, long generation, List<Integer> memberClanIds, int crestId) throws SQLException, StaleStateException
	{
		write(connection ->
		{
			requireAllianceMembers(lockAllianceRows(connection, allianceId), memberClanIds, generation);
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_crest_id=? WHERE ally_id=? AND ally_generation=?"))
			{
				statement.setInt(1, crestId);
				statement.setInt(2, allianceId);
				statement.setLong(3, generation);
				final int affected = statement.executeUpdate();
				if (affected != memberClanIds.size())
				{
					throw new StaleStateException("Alliance crest update affected " + affected + " rows instead of " + memberClanIds.size() + '.');
				}
			}
			return null;
		});
	}

	@Override
	public void clearClanAllianceCrest(int clanId) throws SQLException, StaleStateException
	{
		write(connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_crest_id=0 WHERE clan_id=?"))
			{
				statement.setInt(1, clanId);
				requireSingleRow(statement.executeUpdate(), "clear invalid alliance crest");
			}
			return null;
		});
	}

	@Override
	public List<WarRow> loadWars() throws SQLException
	{
		final List<WarRow> wars = new ArrayList<>();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT war_id, clan1, clan2 FROM clan_wars ORDER BY war_id");
			ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				wars.add(new WarRow(result.getLong("war_id"), result.getInt("clan1"), result.getInt("clan2")));
			}
		}
		return List.copyOf(wars);
	}

	@Override
	public WarRow createWar(int sourceClanId, int targetClanId) throws SQLException, StaleStateException
	{
		return write(connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement("INSERT INTO clan_wars (clan1, clan2, wantspeace1, wantspeace2) VALUES(?,?,0,0)", Statement.RETURN_GENERATED_KEYS))
			{
				statement.setInt(1, sourceClanId);
				statement.setInt(2, targetClanId);
				requireSingleRow(statement.executeUpdate(), "declare clan war");
				try (ResultSet generated = statement.getGeneratedKeys())
				{
					if (!generated.next() || (generated.getLong(1) <= 0))
					{
						throw new SQLException("Clan war insert did not return a generated war_id.");
					}
					return new WarRow(generated.getLong(1), sourceClanId, targetClanId);
				}
			}
		});
	}
	@Override
	public void deleteWar(WarRow war) throws SQLException, StaleStateException
	{
		write(connection ->
		{
			deleteWar(connection, war);
			return null;
		});
	}

	@Override
	public void deleteWars(Collection<WarRow> wars) throws SQLException, StaleStateException
	{
		write(connection ->
		{
			for (WarRow war : wars)
			{
				deleteWar(connection, war);
			}
			return null;
		});
	}

	private static void deleteWar(Connection connection, WarRow war) throws SQLException, StaleStateException
	{
		try (PreparedStatement statement = connection.prepareStatement("DELETE FROM clan_wars WHERE war_id=? AND clan1=? AND clan2=?"))
		{
			statement.setLong(1, war.warId());
			statement.setInt(2, war.sourceClanId());
			statement.setInt(3, war.targetClanId());
			requireSingleRow(statement.executeUpdate(), "end clan war " + war.warId());
		}
	}

	private static List<AllianceRow> lockClanRows(Connection connection, int firstClanId, int secondClanId) throws SQLException, StaleStateException
	{
		final int low = Math.min(firstClanId, secondClanId);
		final int high = Math.max(firstClanId, secondClanId);
		final List<AllianceRow> rows = new ArrayList<>(2);
		rows.add(lockClanRow(connection, low));
		rows.add(lockClanRow(connection, high));
		return rows;
	}

	private static AllianceRow lockClanRow(Connection connection, int clanId) throws SQLException, StaleStateException
	{
		try (PreparedStatement statement = connection.prepareStatement("SELECT clan_id, ally_id, ally_name, ally_generation, ally_generation_counter, ally_crest_id FROM clan_data WHERE clan_id=? FOR UPDATE"))
		{
			statement.setInt(1, clanId);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw new StaleStateException("Clan row " + clanId + " is missing.");
				}
				return allianceRow(result);
			}
		}
	}

	private static List<AllianceRow> lockAllianceRows(Connection connection, int allianceId) throws SQLException
	{
		final List<AllianceRow> rows = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("SELECT clan_id, ally_id, ally_name, ally_generation, ally_generation_counter, ally_crest_id FROM clan_data WHERE ally_id=? ORDER BY clan_id FOR UPDATE"))
		{
			statement.setInt(1, allianceId);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					rows.add(allianceRow(result));
				}
			}
		}
		return rows;
	}
	private static AllianceRow allianceRow(ResultSet result) throws SQLException
	{
		return new AllianceRow(result.getInt("clan_id"), result.getInt("ally_id"), result.getString("ally_name"), result.getLong("ally_generation"), result.getLong("ally_generation_counter"), result.getInt("ally_crest_id"));
	}

	private static AllianceRow row(List<AllianceRow> rows, int clanId) throws StaleStateException
	{
		return rows.stream().filter(value -> value.clanId() == clanId).findFirst().orElseThrow(() -> new StaleStateException("Clan row " + clanId + " was not locked."));
	}

	private static void requireAlliance(AllianceRow row, int allianceId, long generation) throws StaleStateException
	{
		if ((row.allianceId() != allianceId) || (row.generation() != generation))
		{
			throw new StaleStateException("Alliance incarnation changed for clan " + row.clanId() + '.');
		}
	}

	private static void requireAllianceMembers(List<AllianceRow> rows, Map<Integer, Long> expectedGenerationCounters, long generation) throws StaleStateException
	{
		final List<Integer> actual = rows.stream().map(AllianceRow::clanId).sorted().toList();
		final List<Integer> expected = expectedGenerationCounters.keySet().stream().sorted().toList();
		if (!actual.equals(expected) || rows.stream().anyMatch(row -> (row.generation() != generation) || !Long.valueOf(row.generationCounter()).equals(expectedGenerationCounters.get(row.clanId()))))
		{
			throw new StaleStateException("Durable alliance membership or epoch changed before mutation.");
		}
	}
	private static void requireAllianceMembers(List<AllianceRow> rows, List<Integer> expectedClanIds, long generation) throws StaleStateException
	{
		final List<Integer> actual = rows.stream().map(AllianceRow::clanId).sorted().toList();
		final List<Integer> expected = expectedClanIds.stream().sorted().toList();
		if (!actual.equals(expected) || rows.stream().anyMatch(row -> row.generation() != generation))
		{
			throw new StaleStateException("Durable alliance membership changed before mutation.");
		}
	}

	private static void requireSingleRow(int affectedRows, String operation) throws StaleStateException
	{
		if (affectedRows != 1)
		{
			throw new StaleStateException(operation + " affected " + affectedRows + " rows.");
		}
	}

	private static <T> T write(SqlWrite<T> operation) throws SQLException, StaleStateException
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				final T result = operation.execute(connection);
				connection.commit();
				return result;
			}
			catch (SQLException | StaleStateException | RuntimeException failure)
			{
				rollback(connection, failure);
				throw failure;
			}
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
}
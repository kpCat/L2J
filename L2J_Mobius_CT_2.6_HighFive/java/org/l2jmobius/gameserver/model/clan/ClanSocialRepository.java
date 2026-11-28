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
	List<ClanAllianceService.MembershipEpoch> loadAllianceMembership(int allianceId, long generation) throws SQLException;
	ClanAllianceService.DurableMembership loadClanMembership(int clanId) throws SQLException;
	long createAlliance(int leaderClanId, long expectedGeneration, long expectedGenerationCounter, String allianceName) throws SQLException, ClanSocialRepository.StaleStateException;
	boolean joinAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, String allianceName, int allianceCrestId, long targetExpectedGeneration, long targetExpectedGenerationCounter, int maxAllianceClans) throws SQLException, ClanSocialRepository.StaleStateException;
	void leaveAlliance(int clanId, int allianceId, long generation, long expectedGenerationCounter, long penaltyExpiryTime, int penaltyType) throws SQLException, ClanSocialRepository.StaleStateException;
	void expelAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, long leaderExpectedGenerationCounter, long targetExpectedGenerationCounter, long leaderPenaltyExpiryTime, long targetPenaltyExpiryTime, int targetPenaltyType) throws SQLException, ClanSocialRepository.StaleStateException;
	void dissolveAlliance(int leaderClanId, int allianceId, long generation, Map<Integer, Long> memberGenerationCounters, long leaderPenaltyExpiryTime) throws SQLException, ClanSocialRepository.StaleStateException;
	void repairOrphanAlliance(int clanId, int allianceId, long generation, long expectedGenerationCounter) throws SQLException, ClanSocialRepository.StaleStateException;
	void changeAllianceCrest(int allianceId, long generation, List<Integer> memberClanIds, int crestId) throws SQLException, ClanSocialRepository.StaleStateException;
	void clearClanAllianceCrest(int clanId) throws SQLException, ClanSocialRepository.StaleStateException;
	List<ClanSocialRepository.WarRow> loadWars() throws SQLException;
	default List<ClanSocialRepository.WarRow> loadWars(int firstClanId, int secondClanId) throws SQLException
	{
		return loadWars().stream().filter(row -> row.sourceClanId() == firstClanId && row.targetClanId() == secondClanId
			|| row.sourceClanId() == secondClanId && row.targetClanId() == firstClanId).toList();
	}
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

	/** The stock clan row and the founder's membership have one durable commit point. */
	void createFoundedClan(int clanId, String name, int founderId, long now) throws SQLException, StaleStateException
	{
		write(connection ->
		{
			try (PreparedStatement member = connection.prepareStatement("UPDATE characters SET clanid=?, clan_privs=? WHERE charId=? AND (clanid=0 OR clanid IS NULL) AND clan_create_expiry_time<=?"))
			{
				member.setInt(1, clanId);
				member.setInt(2, ClanPrivileges.getCompleteMask());
				member.setInt(3, founderId);
				member.setLong(4, now);
				requireSingleRow(member.executeUpdate(), "Clan founder membership");
			}
			try (PreparedStatement named = connection.prepareStatement("SELECT clan_name FROM clan_data"))
			{
				try (ResultSet rows = named.executeQuery())
				{
					while (rows.next())
					{
						if (name.equalsIgnoreCase(rows.getString(1))) { throw new StaleStateException("Clan name is already durably claimed."); }
					}
				}
			}
			try (PreparedStatement clan = connection.prepareStatement("INSERT INTO clan_data (clan_id,clan_name,clan_level,hasCastle,ally_id,leader_id,crest_id,crest_large_id,ally_crest_id) VALUES (?,?,0,0,0,?,0,0,0)"))
			{
				clan.setInt(1, clanId);
				clan.setString(2, name);
				clan.setInt(3, founderId);
				requireSingleRow(clan.executeUpdate(), "Clan creation");
			}
			return null;
		});
	}

	Clan.FoundingState foundingState(int clanId, String name, int founderId) throws SQLException
	{
		try
		{
			return write(connection ->
			{
				// A locking read waits for any still-running founder transaction on this character row.
				try (PreparedStatement evidence = connection.prepareStatement("SELECT c.clanid, d.clan_id, d.leader_id, d.clan_name FROM characters c LEFT JOIN clan_data d ON d.clan_id=? WHERE c.charId=? FOR UPDATE"))
				{
					evidence.setInt(1, clanId);
					evidence.setInt(2, founderId);
					try (ResultSet rows = evidence.executeQuery())
					{
						if (!rows.next()) { return Clan.FoundingState.CONFLICT; }
						final int memberClanId = rows.getInt(1);
						final int storedClanId = rows.getInt(2);
						if (memberClanId == clanId && storedClanId == clanId && rows.getInt(3) == founderId && name.equals(rows.getString(4)))
						{
							return Clan.FoundingState.COMMITTED;
						}
						return memberClanId == 0 && storedClanId == 0 ? Clan.FoundingState.ABSENT : Clan.FoundingState.CONFLICT;
					}
				}
			});
		}
		catch (StaleStateException unexpected)
		{
			throw new SQLException(unexpected);
		}
	}

	/** Commit only native clan membership. The actor continues to use its existing STORE owner. */
	boolean joinMember(int clanId, int characterId, int pledgeType, int powerGrade, int privileges, int academyLevel) throws SQLException
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE characters SET clanid=?, subpledge=?, power_grade=?, clan_privs=?, lvl_joined_academy=?, clan_join_expiry_time=0 WHERE charId=? AND (clanid=0 OR clanid IS NULL) AND clan_join_expiry_time<=?"))
		{
			statement.setInt(1, clanId);
			statement.setInt(2, pledgeType);
			statement.setInt(3, powerGrade);
			statement.setInt(4, privileges);
			statement.setInt(5, academyLevel);
			statement.setInt(6, characterId);
			statement.setLong(7, System.currentTimeMillis());
			return statement.executeUpdate() == 1;
		}
	}

	boolean leaveOrdinaryMember(ClanDepartureService.Change change) throws SQLException
	{
		try { return write(connection -> stageOrdinaryDeparture(connection, change)); }
		catch (StaleStateException unexpected) { throw new SQLException(unexpected); }
	}

	ClanDepartureService.Readback ordinaryDepartureState(ClanDepartureService.Change change) throws SQLException
	{
		try { return write(connection -> readOrdinaryDeparture(connection, change)); }
		catch (StaleStateException unexpected) { throw new SQLException(unexpected); }
	}

	static boolean stageOrdinaryDeparture(Connection connection, ClanDepartureService.Change change) throws SQLException
	{
		try (var clan = connection.prepareStatement("SELECT clan_id FROM clan_data WHERE clan_id=? AND leader_id=? AND leader_id<>? AND new_leader_id<>? AND clan_level BETWEEN 0 AND 4 AND COALESCE(ally_id,0)=0 AND COALESCE(hasCastle,0)=0 AND dissolving_expiry_time=0 FOR UPDATE"))
		{
			clan.setInt(1, change.clanId());
			clan.setInt(2, change.leaderId());
			clan.setInt(3, change.characterId());
			clan.setInt(4, change.characterId());
			try (var row = clan.executeQuery()) { if (!row.next()) { return false; } }
		}
		try (var member = connection.prepareStatement("UPDATE characters SET clanid=0, title='', clan_privs=0, power_grade=0, clan_join_expiry_time=? WHERE charId=? AND clanid=? AND clan_join_expiry_time=? AND subpledge=0 AND lvl_joined_academy=0 AND apprentice=0 AND sponsor=0 AND deletetime=0"))
		{
			member.setLong(1, change.expiryAfter());
			member.setInt(2, change.characterId());
			member.setInt(3, change.clanId());
			member.setLong(4, change.expiryBefore());
			return member.executeUpdate() == 1;
		}
	}

	static ClanDepartureService.Readback readOrdinaryDeparture(Connection connection, ClanDepartureService.Change change) throws SQLException
	{
		// This cannot see the old row while a possibly committed departure still owns its row lock.
		try (var member = connection.prepareStatement("SELECT clanid, clan_join_expiry_time, (title='' AND clan_privs=0 AND power_grade=0 AND subpledge=0 AND lvl_joined_academy=0 AND apprentice=0 AND sponsor=0) FROM characters WHERE charId=? FOR UPDATE"))
		{
			member.setInt(1, change.characterId());
			try (var row = member.executeQuery())
			{
				return row.next() ? ClanDepartureService.observe(change, row.getInt(1), row.getLong(2), row.getBoolean(3)) : ClanDepartureService.Readback.CONFLICT;
			}
		}
	}

	@Override
	public List<ClanAllianceService.MembershipEpoch> loadAllianceMembership(int allianceId, long generation) throws SQLException
	{
		final List<ClanAllianceService.MembershipEpoch> members = new ArrayList<>();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT clan_id, ally_id, ally_generation, ally_generation_counter FROM clan_data WHERE ally_id=? AND ally_generation=? ORDER BY clan_id"))
		{
			statement.setInt(1, allianceId);
			statement.setLong(2, generation);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					members.add(new ClanAllianceService.MembershipEpoch(result.getInt("clan_id"), result.getInt("ally_id"), result.getLong("ally_generation"), result.getLong("ally_generation_counter")));
				}
			}
		}
		return List.copyOf(members);
	}

	@Override
	public ClanAllianceService.DurableMembership loadClanMembership(int clanId) throws SQLException
	{
		try
		{
			return write(connection ->
			{
				// Wait for a possibly in-flight native transaction on this single clan row.
				try (PreparedStatement statement = connection.prepareStatement("SELECT clan_id,leader_id,ally_id,ally_name,ally_generation,ally_generation_counter,ally_crest_id,ally_penalty_expiry_time,ally_penalty_type FROM clan_data WHERE clan_id=? FOR UPDATE"))
				{
					statement.setInt(1, clanId);
					try (ResultSet result = statement.executeQuery())
					{
						if (!result.next()) { return null; }
						final AllianceRow row = allianceRow(result);
						final var state = new ClanAllianceService.AllianceState(row.clanId(), row.allianceId(), row.allianceName(), row.generation(), row.generationCounter(), row.crestId(), result.getLong("ally_penalty_expiry_time"), result.getInt("ally_penalty_type"));
						return new ClanAllianceService.DurableMembership(state, result.getInt("leader_id"));
					}
				}
			});
		}
		catch (StaleStateException unexpected)
		{
			throw new SQLException(unexpected);
		}
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
			// The existing incarnation allocator serializes durable creations, including a lost acknowledgement.
			try (PreparedStatement statement = connection.prepareStatement("SELECT ally_name FROM clan_data WHERE ally_id=clan_id");
				ResultSet named = statement.executeQuery())
			{
				while (named.next())
				{
					if (allianceName.equalsIgnoreCase(named.getString(1))) { throw new StaleStateException("Alliance name is already durably claimed."); }
				}
			}
			final long nextEpoch = Math.addExact(expectedGenerationCounter, 1);
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_id=?, ally_name=?, ally_generation=?, ally_generation_counter=?, ally_penalty_expiry_time=0, ally_penalty_type=0 WHERE clan_id=? AND (ally_id=0 OR ally_id IS NULL) AND ally_generation=? AND ally_generation_counter=?"))
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
	public boolean joinAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, String allianceName, int allianceCrestId, long targetExpectedGeneration, long targetExpectedGenerationCounter, int maxAllianceClans) throws SQLException, StaleStateException
	{
		final long nextTargetEpoch = Math.addExact(targetExpectedGenerationCounter, 1);
		return write(connection ->
		{
			final List<AllianceRow> rows = lockClanRows(connection, leaderClanId, targetClanId);
			final AllianceRow leader = row(rows, leaderClanId);
			final AllianceRow target = row(rows, targetClanId);
			requireAlliance(leader, allianceId, generation);
			if ((target.allianceId() != 0) || (target.generation() != targetExpectedGeneration) || (target.generationCounter() != targetExpectedGenerationCounter))
			{
				throw new StaleStateException("Target clan alliance state changed before join.");
			}
			// Joins share the leader row lock. Count persisted members even when a prior commit was not acknowledged.
			try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM clan_data WHERE ally_id=? AND ally_generation=?"))
			{
				count.setInt(1, allianceId);
				count.setLong(2, generation);
				try (ResultSet members = count.executeQuery())
				{
					if (!members.next()) { throw new SQLException("Alliance member count is unavailable."); }
					if (members.getLong(1) >= maxAllianceClans) { return false; }
				}
			}
			try (PreparedStatement statement = connection.prepareStatement("UPDATE clan_data SET ally_id=?, ally_name=?, ally_generation=?, ally_generation_counter=?, ally_crest_id=?, ally_penalty_expiry_time=0, ally_penalty_type=0 WHERE clan_id=? AND (ally_id=0 OR ally_id IS NULL) AND ally_generation=? AND ally_generation_counter=?"))
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
			return true;
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
	public List<WarRow> loadWars(int firstClanId, int secondClanId) throws SQLException
	{
		final List<WarRow> wars = new ArrayList<>(2);
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT war_id, clan1, clan2 FROM clan_wars WHERE (clan1=? AND clan2=?) OR (clan1=? AND clan2=?)"))
		{
			statement.setInt(1, firstClanId);
			statement.setInt(2, secondClanId);
			statement.setInt(3, secondClanId);
			statement.setInt(4, firstClanId);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					wars.add(new WarRow(result.getLong("war_id"), result.getInt("clan1"), result.getInt("clan2")));
				}
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

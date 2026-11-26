/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.model.clan.ClanDepartureService.Change;
import org.l2jmobius.gameserver.model.clan.ClanDepartureService.Readback;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

public final class ClanDeparture017Checks
{
	private static final Pattern ISOLATED_URL = Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1:(\\d{1,5})/" + PhantomTestDatabaseGuard.TARGET_DATABASE);
	private static final long BEFORE = 10;
	private static final long AFTER = 1_001 + TimeUnit.DAYS.toMillis(1);
	private static final Change DEPARTURE = new Change(29, 11, 31, BEFORE, AFTER);

	private ClanDeparture017Checks()
	{
	}

	public static void main(String[] args) throws Exception
	{
		require(ClanDepartureService.joinExpiry(1_001, 1) == AFTER, "Native departure deadline changed.");
		require(ClanDepartureService.observe(DEPARTURE, 29, BEFORE, false) == Readback.NOT_WRITTEN, "Unchanged membership did not resolve to no effect.");
		require(ClanDepartureService.observe(DEPARTURE, 0, AFTER, true) == Readback.COMMITTED, "Exact durable departure was not acknowledged.");
		require(ClanDepartureService.observe(DEPARTURE, 0, AFTER + 1, true) == Readback.CONFLICT, "An unrelated departure was accepted as the exact write.");
		System.out.println("PASS C2 voluntary leave deadline and exact acknowledgment");
		if (args.length == 0) { return; }
		final var target = ISOLATED_URL.matcher(args[0]);
		require(args.length == 1 && target.matches(), "Only the isolated loopback test schema may run SQL proof.");
		final int port = Integer.parseInt(target.group(1));
		require(port >= 33332 && port <= 65535, "Isolated test port required.");
		final String user = System.getenv("PHANTOM_C4_TEST_USER");
		final String password = System.getenv("PHANTOM_C4_TEST_PASSWORD");
		require(user != null && !user.isBlank() && password != null, "Explicit isolated test credentials required.");
		try (var connection = DriverManager.getConnection(args[0], user, password))
		{
			try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT DATABASE(), @@port"))
			{
				require(rows.next() && PhantomTestDatabaseGuard.TARGET_DATABASE.equals(rows.getString(1)) && rows.getInt(2) == port, "Connected server differs from the isolated target.");
			}
			verifyDeparture(connection);
		}
		System.out.println("PASS C2 voluntary leave guarded native update, rollback, exact readback and replay prevention");
	}

	private static void verifyDeparture(Connection connection) throws Exception
	{
		try (var sql = connection.createStatement())
		{
			sql.execute("CREATE TEMPORARY TABLE clan_data (clan_id INT PRIMARY KEY, clan_level INT, leader_id INT, ally_id INT, hasCastle INT, dissolving_expiry_time BIGINT, new_leader_id INT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE characters (charId INT PRIMARY KEY, clanid INT, title VARCHAR(21), clan_privs INT, power_grade INT, subpledge INT, lvl_joined_academy INT, apprentice INT, sponsor INT, deletetime BIGINT, clan_join_expiry_time BIGINT, clan_create_expiry_time BIGINT) ENGINE=InnoDB");
			sql.executeUpdate("INSERT INTO clan_data VALUES (29,2,31,0,0,0,0)");
			sql.executeUpdate("INSERT INTO characters VALUES (11,29,'Member',255,5,0,0,0,0,0,10,42)");
		}
		connection.setAutoCommit(false);
		require(!ClanSocialRepository.stageOrdinaryDeparture(connection, new Change(29, 11, 11, BEFORE, AFTER)), "Departure accepted stale leadership.");
		try (var query = connection.createStatement()) { query.executeUpdate("UPDATE clan_data SET new_leader_id=11"); }
		require(!ClanSocialRepository.stageOrdinaryDeparture(connection, DEPARTURE), "Departure accepted pending leadership appointment.");
		connection.rollback();
		try (var query = connection.createStatement()) { query.executeUpdate("UPDATE clan_data SET ally_id=47"); }
		require(!ClanSocialRepository.stageOrdinaryDeparture(connection, DEPARTURE), "Departure accepted an allied clan.");
		connection.rollback();
		require(ClanSocialRepository.stageOrdinaryDeparture(connection, DEPARTURE), "Eligible member did not stage a departure.");
		connection.rollback();
		require(ClanSocialRepository.readOrdinaryDeparture(connection, DEPARTURE) == Readback.NOT_WRITTEN, "Readback accepted rolled-back departure.");
		require(ClanSocialRepository.stageOrdinaryDeparture(connection, DEPARTURE), "Eligible member did not leave after safe rollback.");
		connection.commit();
		require(ClanSocialRepository.readOrdinaryDeparture(connection, DEPARTURE) == Readback.COMMITTED, "Readback lost exact committed departure.");
		require(!ClanSocialRepository.stageOrdinaryDeparture(connection, DEPARTURE), "Native SQL replayed an accepted departure.");
		try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT clan_join_expiry_time, clan_create_expiry_time FROM characters"))
		{
			require(rows.next() && rows.getLong(1) == AFTER && rows.getLong(2) == 42, "Departure changed an unrelated penalty or lost the stock deadline.");
		}
		connection.rollback();
	}

	private static void require(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}

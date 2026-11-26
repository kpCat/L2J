/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment.DurableOutcome;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment.Payment;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** Exact receipt and stock fee staging against isolated, connection-local native row fixtures. */
public final class ClanFoundingDevelopment017Checks
{
	private static final Pattern ISOLATED_URL = Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1:(\\d{1,5})/" + PhantomTestDatabaseGuard.TARGET_DATABASE);
	private static final long BEFORE_ADENA = 1_500_000;
	private static final long BEFORE_SP = 50_000;
	private static final Payment PAYMENT = new Payment(7, 11, 29, 88, 37, BEFORE_ADENA, BEFORE_SP, new UUID(17, 1));

	private ClanFoundingDevelopment017Checks()
	{
	}

	public static void main(String[] args) throws Exception
	{
		final Payment other = new Payment(7, 11, 29, 88, 37, BEFORE_ADENA, BEFORE_SP, new UUID(17, 2));
		require(PAYMENT.adenaAfter() == 850_000 && PAYMENT.spAfter() == 30_000, "Stock costs changed.");
		require(PAYMENT.evidence().length == 56 && Arrays.equals(PAYMENT.evidence(), PAYMENT.evidence()) && !Arrays.equals(PAYMENT.evidence(), other.evidence()), "Receipt does not identify the exact operation.");
		System.out.println("PASS C4 stock fees and exact payment identity");
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
			verifyPaidStaging(connection, other);
		}
		System.out.println("PASS C4 isolated MariaDB stock fees, rollback, readback and replay prevention");
	}

	private static void verifyPaidStaging(Connection connection, Payment other) throws SQLException
	{
		try (var sql = connection.createStatement())
		{
			sql.execute("CREATE TEMPORARY TABLE clan_data (clan_id INT PRIMARY KEY, clan_level INT, leader_id INT, dissolving_expiry_time BIGINT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE characters (charId INT PRIMARY KEY, sp BIGINT, classid INT, base_class INT, clanid INT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE items (object_id INT PRIMARY KEY, count BIGINT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE phantom_profiles (profile_id BIGINT PRIMARY KEY, character_object_id INT) ENGINE=InnoDB");
			sql.execute("CREATE TEMPORARY TABLE phantom_profile_components (profile_id BIGINT, component_type VARCHAR(64), component_schema_version SMALLINT, payload VARBINARY(4096), row_version BIGINT DEFAULT 0, PRIMARY KEY (profile_id,component_type)) ENGINE=InnoDB");
			sql.executeUpdate("INSERT INTO clan_data VALUES (29,0,11,0)");
			sql.executeUpdate("INSERT INTO characters VALUES (11,50000,88,88,29)");
			sql.executeUpdate("INSERT INTO items VALUES (37,1500000)");
			sql.executeUpdate("INSERT INTO phantom_profiles VALUES (7,11)");
			sql.executeUpdate("INSERT INTO phantom_profile_components VALUES (7,'clan.organization',1,X'01',0)");
		}
		connection.setAutoCommit(false);
		debitFixtureCoins(connection, PAYMENT);
		require(ClanFoundingDevelopment.stage(connection, PAYMENT, 1, credit -> false) == BEFORE_SP, "Native SP preimage was lost.");
		connection.rollback();
		assertRows(connection, BEFORE_ADENA, BEFORE_SP, 0);
		require(ClanFoundingDevelopment.readback(connection, PAYMENT, BEFORE_ADENA, BEFORE_SP) == DurableOutcome.ROLLED_BACK, "Exact rolled-back preimage was not recognized.");

		debitFixtureCoins(connection, PAYMENT);
		ClanFoundingDevelopment.stage(connection, PAYMENT, 1, credit -> false);
		connection.commit();
		assertRows(connection, PAYMENT.adenaAfter(), PAYMENT.spAfter(), 1);
		require(ClanFoundingDevelopment.readback(connection, PAYMENT, PAYMENT.adenaAfter(), BEFORE_SP) == DurableOutcome.APPLIED, "Committed receipt did not recover exact fees.");
		require(ClanFoundingDevelopment.readback(connection, other, PAYMENT.adenaAfter(), BEFORE_SP) == DurableOutcome.CONFLICT, "Readback accepted a different operation's receipt.");
		final Payment replay = new Payment(7, 11, 29, 88, 37, PAYMENT.adenaAfter(), PAYMENT.spAfter(), new UUID(17, 3));
		debitFixtureCoins(connection, replay);
		try
		{
			ClanFoundingDevelopment.stage(connection, replay, 1, credit -> false);
			throw new AssertionError("Paid level accepted a second payment.");
		}
		catch (SQLException expected)
		{
			require("Native clan leadership or level changed.".equals(expected.getMessage()), "Replay did not reach the exact native clan level check.");
		}
		finally
		{
			connection.rollback();
		}
		assertRows(connection, PAYMENT.adenaAfter(), PAYMENT.spAfter(), 1);
	}

	private static void debitFixtureCoins(Connection connection, Payment payment) throws SQLException
	{
		try (var sql = connection.prepareStatement("UPDATE items SET count=? WHERE object_id=? AND count=?"))
		{
			sql.setLong(1, payment.adenaAfter());
			sql.setInt(2, payment.coinObjectId());
			sql.setLong(3, payment.adenaBefore());
			require(sql.executeUpdate() == 1, "Native currency preimage was lost.");
		}
	}

	private static void assertRows(Connection connection, long adena, long sp, int level) throws SQLException
	{
		try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT i.count,c.sp,cl.clan_level FROM items i CROSS JOIN characters c CROSS JOIN clan_data cl"))
		{
			require(rows.next() && rows.getLong(1) == adena && rows.getLong(2) == sp && rows.getInt(3) == level, "Stock clan level and fees diverged.");
		}
	}

	private static void require(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}

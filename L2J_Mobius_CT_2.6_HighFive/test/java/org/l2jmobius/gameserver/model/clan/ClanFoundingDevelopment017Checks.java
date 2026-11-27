/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment.DurableOutcome;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment.Payment;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
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
		final Payment secondLevel = new Payment(7, 11, 29, 88, 37, 3_000_000, 120_000, new UUID(21, 21002102), 1);
		require(secondLevel.adenaAfter() == 500_000 && secondLevel.spAfter() == 20_000 && secondLevel.evidence().length != PAYMENT.evidence().length, "L2 costs and receipt must identify the exact transition.");
		verifyRequirements();
		if (args.length == 0) { return; }
		final var target = ISOLATED_URL.matcher(args[0]);
		require((args.length == 1 || args.length == 3) && target.matches(), "Only the isolated loopback test schema may run SQL proof.");
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
			verifySecondLevel(connection, secondLevel);
		}
		System.out.println("PASS C4 isolated MariaDB stock fees, rollback, readback and replay prevention");
		if (args.length == 3) { verifyNativeDebit(args[0], user, password, Path.of(args[1]), Path.of(args[2])); }
	}

	private static void verifyRequirements()
	{
		for (int current = 0; current < 11; current++)
		{
			final var requirement = Clan.levelRequirement(current);
			require(requirement.currentLevel() == current && requirement.nextLevel() == current + 1 && requirement.territory() == (current == 10), "Broken stock clan level boundary.");
		}
		require(Clan.levelRequirement(-1) == null && Clan.levelRequirement(11) == null, "Nonexistent level transition.");
		require(Clan.levelRequirement(2).itemId() == 1419 && Clan.levelRequirement(3).itemId() == 3874 && Clan.levelRequirement(4).itemId() == 3870, "Stock quest or loot item changed.");
		require(Clan.levelRequirement(8).itemCount() == 150 && Clan.levelRequirement(9).itemCount() == 5, "Stock high-level item requirements changed.");
		System.out.println("PASS 021 stock requirement boundaries for levels 0-11");
	}

	private static void verifyNativeDebit(String url, String user, String password, Path config, Path datapack) throws Exception
	{
		final Path scratch = config.toRealPath().getParent();
		require(Files.isRegularFile(scratch.resolve(".codex-owned.json")), "The native fixture requires marked scratch.");
		try (var conn = DriverManager.getConnection(url, user, password); var sql = conn.createStatement(); var rows = sql.executeQuery("SELECT @@datadir"))
		{
			require(rows.next() && Path.of(rows.getString(1)).toRealPath().equals(scratch.resolve("data").toRealPath()), "The native fixture must use the scratch datadir.");
		}
		DatabaseConfig.load(config.toString());
		require(url.equals(DatabaseConfig.DATABASE_URL) && user.equals(DatabaseConfig.DATABASE_LOGIN) && password.equals(DatabaseConfig.DATABASE_PASSWORD),
			"Native pool must use the already validated isolated credentials.");
		ServerConfig.DATAPACK_ROOT = datapack.toRealPath().toFile();
		PlayerConfig.load();
		DatabaseFactory.initFromConfig(config.toString());
		final Payment payment = new Payment(21002102, 21002103, 21002104, 88, 21002105, 3_000_000, 120_000, new UUID(21, 21002102), 1);
		try
		{
			try (var conn = DatabaseFactory.getConnection(); var sql = conn.createStatement())
			{
				sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002102,21002103)");
				sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002102,'clan.organization',2,X'01')");
				sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id) VALUES(21002104,'ClanProof021',1,21002103)");
				sql.executeUpdate("INSERT INTO characters(charId,char_name,sp,classid,base_class,clanid) VALUES(21002103,'ClanProof021',120000,88,88,21002104)");
			}
			final Item adena = new Item(payment.coinObjectId(), Inventory.ADENA_ID);
			adena.setOwnerId(payment.actorId());
			adena.setItemLocation(ItemLocation.INVENTORY, 0, false);
			adena.setCount(payment.adenaBefore());
			try
			{
				adena.transactRecoverableAdenaDebit(payment.requirement().adena(), connection ->
				{
					ClanFoundingDevelopment.stage(connection, payment, credit -> false);
					throw new SQLException("021 controlled failure before commit");
				});
				throw new AssertionError("Injected failure did not propagate.");
			}
			catch (SQLException expected)
			{
				require("021 controlled failure before commit".equals(expected.getMessage()), "Did not reach native transaction after staging.");
			}
			require(adena.getCount() == payment.adenaBefore() && !adena.hasPendingAdenaDebit(), "Failed native debit left a memory effect.");
			try (var conn = DatabaseFactory.getConnection())
			{
				assertRows(conn, payment.adenaBefore(), payment.spBefore(), 1);
			}
			adena.transactRecoverableAdenaDebit(payment.requirement().adena(), conn -> ClanFoundingDevelopment.stage(conn, payment, credit -> false));
			require(adena.getCount() == payment.adenaAfter() && !adena.hasPendingAdenaDebit(), "Committed native debit was not published.");
		}
		finally
		{
			DatabaseFactory.close();
		}
		try (var conn = DriverManager.getConnection(url, user, password))
		{
			conn.setAutoCommit(false);
			assertRows(conn, payment.adenaAfter(), payment.spAfter(), 2);
			require(ClanFoundingDevelopment.readback(conn, payment, payment.adenaAfter(), payment.spBefore()) == DurableOutcome.APPLIED,
				"Reconnect lost the exact receipt, actor, level, Adena or SP.");
			conn.rollback();
		}
		System.out.println("PASS 021 native Item debit with stock DDL, fault rollback, commit and cold SQL readback");
	}

	private static void verifySecondLevel(Connection connection, Payment payment) throws SQLException
	{
		try (var sql = connection.createStatement())
		{
			sql.executeUpdate("UPDATE items SET count=3000000 WHERE object_id=37");
			sql.executeUpdate("UPDATE characters SET sp=120000 WHERE charId=11");
			connection.commit();
		}
		final Payment insufficient = new Payment(7, 11, 29, 88, 37, 3_000_000, 99_999, new UUID(21, 1), 1);
		requireRefused(connection, insufficient, "Native clan payment preimage changed.");

		try (var sql = connection.createStatement())
		{
			sql.executeUpdate("UPDATE clan_data SET dissolving_expiry_time=50 WHERE clan_id=29");
			connection.commit();
		}
		requireRefused(connection, payment, "Native clan leadership or level changed.");
		try (var sql = connection.createStatement())
		{
			sql.executeUpdate("UPDATE clan_data SET dissolving_expiry_time=0 WHERE clan_id=29");
			connection.commit();
		}

		debitFixtureCoins(connection, payment);
		ClanFoundingDevelopment.stage(connection, payment, credit -> false);
		connection.rollback();
		assertRows(connection, payment.adenaBefore(), payment.spBefore(), 1);
		require(ClanFoundingDevelopment.readback(connection, payment, payment.adenaBefore(), payment.spBefore()) == DurableOutcome.ROLLED_BACK, "L2 rollback was not recognized over the previous-version receipt.");

		debitFixtureCoins(connection, payment);
		ClanFoundingDevelopment.stage(connection, payment, credit -> false);
		connection.commit();
		assertRows(connection, payment.adenaAfter(), payment.spAfter(), 2);
		require(ClanFoundingDevelopment.readback(connection, payment, payment.adenaAfter(), payment.spBefore()) == DurableOutcome.APPLIED, "L2 lost-ACK recovery did not confirm the exact effect.");
		requireRefused(connection, payment, "Native clan payment preimage changed.");
		assertRows(connection, payment.adenaAfter(), payment.spAfter(), 2);
		System.out.println("PASS 021 L1-to-L2 stock fees, legacy receipt, refusal, rollback and exact recovery");
	}

	private static void requireRefused(Connection connection, Payment payment, String reason) throws SQLException
	{
		try
		{
			ClanFoundingDevelopment.stage(connection, payment, credit -> false);
			throw new AssertionError("Invalid payment was accepted.");
		}
		catch (SQLException expected)
		{
			require(reason.equals(expected.getMessage()), "Unexpected refusal: " + expected.getMessage());
		}
		finally
		{
			connection.rollback();
		}
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
		require(ClanFoundingDevelopment.stage(connection, PAYMENT, credit -> false) == BEFORE_SP, "Native SP preimage was lost.");
		connection.rollback();
		assertRows(connection, BEFORE_ADENA, BEFORE_SP, 0);
		require(ClanFoundingDevelopment.readback(connection, PAYMENT, BEFORE_ADENA, BEFORE_SP) == DurableOutcome.ROLLED_BACK, "Exact rolled-back preimage was not recognized.");

		debitFixtureCoins(connection, PAYMENT);
		ClanFoundingDevelopment.stage(connection, PAYMENT, credit -> false);
		connection.commit();
		assertRows(connection, PAYMENT.adenaAfter(), PAYMENT.spAfter(), 1);
		require(ClanFoundingDevelopment.readback(connection, PAYMENT, PAYMENT.adenaAfter(), BEFORE_SP) == DurableOutcome.APPLIED, "Committed receipt did not recover exact fees.");
		require(ClanFoundingDevelopment.readback(connection, other, PAYMENT.adenaAfter(), BEFORE_SP) == DurableOutcome.CONFLICT, "Readback accepted a different operation's receipt.");
		final Payment replay = new Payment(7, 11, 29, 88, 37, PAYMENT.adenaAfter(), PAYMENT.spAfter(), new UUID(17, 3));
		debitFixtureCoins(connection, replay);
		try
		{
			ClanFoundingDevelopment.stage(connection, replay, credit -> false);
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

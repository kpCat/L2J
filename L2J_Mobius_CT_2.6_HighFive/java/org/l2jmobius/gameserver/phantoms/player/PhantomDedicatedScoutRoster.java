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
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.player;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;

/**
 * Durable service identities owned by the dedicated scout roster. This class
 * provisions offline character rows. Native admission and STORE own the live player.
 */
public final class PhantomDedicatedScoutRoster
{
	private static final int MAX_SLOTS = 32;
	private static final String ROLE = "DEDICATED_SCOUT";
	private static final byte STARTING_LEVEL = 76;
	private static final int DISABLED_LOGIN = -1;
	private static final int BOUND_VERSION = 1;
	private static final String FAILURE_STATE = "PWS08";
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Pattern SCOUT_CHARACTER_NAME = Pattern.compile("GeoScout(?:0[1-9]|[12][0-9]|3[0-2])", Pattern.CASE_INSENSITIVE);
	private static final String SELECT_SLOT = "SELECT role, account_name, char_name, ownership_token, char_id, row_version FROM phantom_dedicated_scouts WHERE slot=?";
	private static final String FOREIGN_OWNERS = """
		SELECT CASE
			WHEN EXISTS (SELECT 1 FROM phantom_profiles WHERE character_object_id=?) THEN 'scout.ordinary_profile_conflict'
			WHEN EXISTS (SELECT 1 FROM character_offline_trade WHERE charId=?)
				OR EXISTS (SELECT 1 FROM character_offline_trade_items WHERE charId=?)
				OR EXISTS (SELECT 1 FROM character_offline_play WHERE charId=?)
				OR EXISTS (SELECT 1 FROM character_offline_play_group WHERE charId=? OR leaderId=?)
				THEN 'scout.offline_store_conflict'
		END
		""";

	public Identity ensure(int slot) throws SQLException
	{
		if ((slot < 1) || (slot > MAX_SLOTS))
		{
			throw new IllegalArgumentException("Dedicated scout slot must be between 1 and 32.");
		}

		// Same name authority as client CharacterCreate and ordinary population birth.
		final CharInfoTable names = CharInfoTable.getInstance();
		synchronized (names)
		{
			try (Connection connection = beginTransaction())
			{
				requireSchema(connection);
				DurableSlot row = readSlot(connection, slot, true);
				if (row == null)
				{
					insertIntent(connection, slot);
					connection.commit();
					row = readSlot(connection, slot, true);
					if (row == null)
					{
						throw failure("scout.intent_missing");
					}
				}

				verifyOwnership(connection, row);
				if (row.objectId() != 0)
				{
					connection.commit();
					return new Identity(row);
				}

				// The committed intent has no native rows. Account, native character,
				// initial vitals/location and binding are committed or rolled back together.
				final Player created = createCharacter(connection, row);
				final DurableSlot bound = new DurableSlot(slot, row.accountName(), row.characterName(), row.token(), created.getObjectId(), BOUND_VERSION);
				bindCharacter(connection, bound);
				verifyOwnership(connection, bound);
				connection.commit();
				names.addName(created);
				return new Identity(bound);
			}
			catch (SQLException e)
			{
				throw redact(e);
			}
		}
	}

	/** Rechecks the issued capability against a fresh database snapshot before admission. */
	public void verify(Identity identity) throws SQLException
	{
		Objects.requireNonNull(identity);
		try (Connection connection = beginTransaction())
		{
			final DurableSlot row = readSlot(connection, identity.slot(), false);
			if (!identity._row.equals(row))
			{
				throw failure("scout.stale_identity");
			}

			verifyOwnership(connection, row);
			connection.commit();
		}
		catch (SQLException e)
		{
			throw redact(e);
		}
	}

	/** Protects dormant scouts from mail without reserving names that humans already own. */
	public static boolean isMailRestricted(int objectId, String canonicalName) throws SQLException
	{
		if ((objectId <= 0) || (canonicalName == null) || !SCOUT_CHARACTER_NAME.matcher(canonicalName).matches())
		{
			return false;
		}

		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("""
				SELECT 1 FROM phantom_dedicated_scouts scout JOIN characters character_row ON character_row.charId=scout.char_id
				WHERE scout.char_id=? AND scout.role=? AND scout.row_version=?
					AND BINARY character_row.account_name=BINARY scout.account_name
					AND BINARY character_row.char_name=BINARY scout.char_name
				"""))
		{
			statement.setInt(1, objectId);
			statement.setString(2, ROLE);
			statement.setInt(3, BOUND_VERSION);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next();
			}
		}
		catch (SQLException e)
		{
			// Older databases do not yet have the additive migration or any scout identities.
			if ("42S02".equals(e.getSQLState()))
			{
				return false;
			}
			throw redact(e);
		}
	}

	private static Connection beginTransaction() throws SQLException
	{
		final Connection connection;
		try
		{
			connection = DatabaseFactory.getConnection();
		}
		catch (RuntimeException e)
		{
			throw failure("scout.connection_unavailable");
		}

		try
		{
			connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			connection.setAutoCommit(false);
			return connection;
		}
		catch (SQLException e)
		{
			connection.close();
			throw e;
		}
	}

	private static void requireSchema(Connection connection) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("""
			SELECT COUNT(*) FROM information_schema.TABLES
			WHERE TABLE_SCHEMA=DATABASE() AND ENGINE='InnoDB'
				AND TABLE_NAME IN ('phantom_dedicated_scouts', 'accounts', 'characters')
			""");
			ResultSet result = statement.executeQuery())
		{
			if (!result.next() || (result.getInt(1) != 3))
			{
				throw failure("scout.requires_migrated_innodb_schema");
			}
		}

		// The indexed native lookups must detect case-folded collisions. Exact
		// equality is checked on the returned names before issuing an identity.
		try (PreparedStatement statement = connection.prepareStatement("""
			SELECT COUNT(*) FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA=DATABASE() AND RIGHT(COLLATION_NAME, 3)='_ci'
				AND ((TABLE_NAME='accounts' AND COLUMN_NAME='login')
					OR (TABLE_NAME='characters' AND COLUMN_NAME IN ('account_name', 'char_name')))
			""");
			ResultSet result = statement.executeQuery())
		{
			if (!result.next() || (result.getInt(1) != 3))
			{
				throw failure("scout.requires_case_insensitive_native_names");
			}
		}
	}

	private static DurableSlot readSlot(Connection connection, int slot, boolean forUpdate) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(forUpdate ? SELECT_SLOT + " FOR UPDATE" : SELECT_SLOT))
		{
			statement.setInt(1, slot);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}

				final String role = result.getString("role");
				final DurableSlot row = new DurableSlot(slot, result.getString("account_name"), result.getString("char_name"), result.getString("ownership_token"), result.getInt("char_id"), result.getInt("row_version"));
				if (result.next() || !ROLE.equals(role) || !accountName(slot).equals(row.accountName()) || !characterName(slot).equals(row.characterName()) || (row.token() == null) || !row.token().matches("scout:[A-Za-z0-9_-]{32}") || (row.objectId() < 0) || (row.version() != (row.objectId() == 0 ? 0 : BOUND_VERSION)))
				{
					throw failure("scout.invalid_durable_slot");
				}
				return row;
			}
		}
	}

	private static void insertIntent(Connection connection, int slot) throws SQLException
	{
		final byte[] randomBytes = new byte[24];
		RANDOM.nextBytes(randomBytes);
		// A unique ownership marker in the password column. Canonical LoginServer
		// password hashes cannot equal this format and the login is explicitly disabled.
		final String token = "scout:" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO phantom_dedicated_scouts (slot, role, account_name, char_name, ownership_token) VALUES (?, ?, ?, ?, ?)"))
		{
			statement.setInt(1, slot);
			statement.setString(2, ROLE);
			statement.setString(3, accountName(slot));
			statement.setString(4, characterName(slot));
			statement.setString(5, token);
			requireOneRow(statement, "scout.intent_not_inserted");
		}
	}

	private static void verifyOwnership(Connection connection, DurableSlot row) throws SQLException
	{
		final boolean bound = row.objectId() > 0;
		try (PreparedStatement statement = connection.prepareStatement("SELECT login, password, accessLevel FROM accounts WHERE login=?"))
		{
			statement.setString(1, row.accountName());
			try (ResultSet result = statement.executeQuery())
			{
				if ((result.next() != bound) || (bound && (!row.accountName().equals(result.getString("login")) || !row.token().equals(result.getString("password")) || (result.getInt("accessLevel") != DISABLED_LOGIN) || result.next())))
				{
					throw failure("scout.account_ownership_conflict");
				}
			}
		}

		try (PreparedStatement statement = connection.prepareStatement("""
			SELECT charId, account_name, char_name,
				(online=0 AND accesslevel=0 AND clanid=0 AND deletetime=0 AND classid=? AND base_class=? AND level=? AND nobless=0) AS eligible
			FROM characters WHERE account_name=? OR char_name=? OR charId=?
			"""))
		{
			statement.setInt(1, PlayerClass.FIGHTER.getId());
			statement.setInt(2, PlayerClass.FIGHTER.getId());
			statement.setByte(3, STARTING_LEVEL);
			statement.setString(4, row.accountName());
			statement.setString(5, row.characterName());
			statement.setInt(6, row.objectId());
			try (ResultSet result = statement.executeQuery())
			{
				if ((result.next() != bound) || (bound && ((result.getInt("charId") != row.objectId()) || !row.accountName().equals(result.getString("account_name")) || !row.characterName().equals(result.getString("char_name")) || !result.getBoolean("eligible") || result.next())))
				{
					throw failure("scout.character_ownership_conflict");
				}
			}
		}

		try (PreparedStatement statement = connection.prepareStatement(FOREIGN_OWNERS))
		{
			for (int i = 1; i <= 6; i++)
			{
				statement.setInt(i, row.objectId());
			}
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw failure("scout.foreign_ownership_unverified");
				}
				final String conflict = result.getString(1);
				if (conflict != null)
				{
					throw failure(conflict);
				}
			}
		}
	}

	private static Player createCharacter(Connection connection, DurableSlot row) throws SQLException
	{
		final PlayerTemplate template = PlayerTemplateData.getInstance().getTemplate(PlayerClass.FIGHTER);
		if (template == null)
		{
			throw failure("scout.missing_fighter_template");
		}
		final Location location = PlayerCreationInitializer.resolveCreationLocation(template);

		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO accounts (login, password, accessLevel) VALUES (?, ?, ?)"))
		{
			statement.setString(1, row.accountName());
			statement.setString(2, row.token());
			statement.setInt(3, DISABLED_LOGIN);
			requireOneRow(statement, "scout.account_not_inserted");
		}

		final Player player = Player.create(template, row.accountName(), row.characterName(), new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false), STARTING_LEVEL, connection);
		try (PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=maxHp, curMp=maxMp, curCp=0, x=?, y=?, z=? WHERE charId=?"))
		{
			statement.setInt(1, location.getX());
			statement.setInt(2, location.getY());
			statement.setInt(3, location.getZ());
			statement.setInt(4, player.getObjectId());
			requireOneRow(statement, "scout.initial_position_not_saved");
		}
		return player;
	}

	private static void bindCharacter(Connection connection, DurableSlot row) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("UPDATE phantom_dedicated_scouts SET char_id=?, row_version=? WHERE slot=? AND row_version=0 AND char_id IS NULL AND ownership_token=?"))
		{
			statement.setInt(1, row.objectId());
			statement.setInt(2, row.version());
			statement.setInt(3, row.slot());
			statement.setString(4, row.token());
			requireOneRow(statement, "scout.binding_not_saved");
		}
	}

	private static void requireOneRow(PreparedStatement statement, String error) throws SQLException
	{
		if (statement.executeUpdate() != 1)
		{
			throw failure(error);
		}
	}

	private static String accountName(int slot)
	{
		return String.format(Locale.ROOT, "pgs_scout%02d", slot);
	}

	private static String characterName(int slot)
	{
		return String.format(Locale.ROOT, "GeoScout%02d", slot);
	}

	private static SQLException failure(String code)
	{
		return new SQLException(code, FAILURE_STATE);
	}

	private static SQLException redact(SQLException error)
	{
		// Driver diagnostics may include account identifiers or the marker value.
		return FAILURE_STATE.equals(error.getSQLState()) ? error : new SQLException("scout.persistence_failed", error.getSQLState(), error.getErrorCode());
	}

	private record DurableSlot(int slot, String accountName, String characterName, String token, int objectId, int version)
	{
	}

	public static final class Identity
	{
		private final DurableSlot _row;

		private Identity(DurableSlot row)
		{
			_row = row;
		}

		public int slot()
		{
			return _row.slot();
		}

		public int objectId()
		{
			return _row.objectId();
		}

		public String accountName()
		{
			return _row.accountName();
		}

		public String characterName()
		{
			return _row.characterName();
		}

		public long version()
		{
			return _row.version();
		}
	}
}

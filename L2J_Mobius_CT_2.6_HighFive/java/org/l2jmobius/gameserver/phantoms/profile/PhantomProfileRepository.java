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
package org.l2jmobius.gameserver.phantoms.profile;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException.Category;

/**
 * Connection-per-operation persistence boundary for core Phantom profiles.
 */
public final class PhantomProfileRepository
{
	private static final String PROFILE_COLUMNS = "profile_id, character_object_id, schema_version, row_version, created_at, updated_at";
	private static final String COMPONENT_COLUMNS = "profile_id, component_type, component_schema_version, row_version, payload, created_at, updated_at";
	private static final String INSERT_PROFILE = "INSERT INTO phantom_profiles (character_object_id) VALUES (?)";
	private static final String FIND_PROFILE = "SELECT " + PROFILE_COLUMNS + " FROM phantom_profiles WHERE profile_id = ?";
	private static final String FIND_PROFILE_BY_CHARACTER = "SELECT " + PROFILE_COLUMNS + " FROM phantom_profiles WHERE character_object_id = ?";
	private static final String UPDATE_CHARACTER_LINK = "UPDATE phantom_profiles SET character_object_id = ?, row_version = row_version + 1 WHERE profile_id = ? AND row_version = ?";
	private static final String DELETE_PROFILE = "DELETE FROM phantom_profiles WHERE profile_id = ? AND row_version = ?";
	private static final String INSERT_COMPONENT = "INSERT INTO phantom_profile_components (profile_id, component_type, component_schema_version, payload) VALUES (?, ?, ?, ?)";
	private static final String FIND_COMPONENT = "SELECT " + COMPONENT_COLUMNS + " FROM phantom_profile_components WHERE profile_id = ? AND component_type = ?";
	private static final String LIST_COMPONENTS = "SELECT " + COMPONENT_COLUMNS + " FROM phantom_profile_components WHERE profile_id = ? ORDER BY component_type";
	private static final String UPDATE_COMPONENT = "UPDATE phantom_profile_components SET component_schema_version = ?, payload = ?, row_version = row_version + 1 WHERE profile_id = ? AND component_type = ? AND row_version = ?";
	private static final String DELETE_COMPONENT = "DELETE FROM phantom_profile_components WHERE profile_id = ? AND component_type = ? AND row_version = ?";

	private PhantomProfileRepository()
	{
	}

	public static PhantomProfileRepository open()
	{
		try (Connection connection = getConnection("open repository"))
		{
			validateSchema(connection);
			return new PhantomProfileRepository();
		}
		catch (SQLException e)
		{
			throw persistenceFailure("validate Phantom profile schema", e);
		}
	}

	public PhantomProfile create(Integer characterObjectId)
	{
		requireCharacterObjectId(characterObjectId);
		return write("create Phantom profile", connection ->
		{
			final long profileId;
			try (PreparedStatement statement = connection.prepareStatement(INSERT_PROFILE, Statement.RETURN_GENERATED_KEYS))
			{
				if (characterObjectId == null)
				{
					statement.setNull(1, java.sql.Types.INTEGER);
				}
				else
				{
					statement.setInt(1, characterObjectId);
				}
				if (statement.executeUpdate() != 1)
				{
					throw new SQLException("Profile insert did not affect exactly one row.");
				}
				try (ResultSet result = statement.getGeneratedKeys())
				{
					if (!result.next())
					{
						throw new SQLException("Profile insert did not return a generated ID.");
					}
					profileId = result.getLong(1);
					if (result.next())
					{
						throw new SQLException("Profile insert returned multiple generated IDs.");
					}
				}
			}
			return requireProfile(connection, profileId);
		});
	}

	public Optional<PhantomProfile> find(long profileId)
	{
		requireProfileId(profileId);
		try (Connection connection = getConnection("find Phantom profile"))
		{
			return findProfile(connection, profileId);
		}
		catch (SQLException e)
		{
			throw persistenceFailure("find Phantom profile", e);
		}
	}

	public Optional<PhantomProfile> findByCharacterObjectId(int characterObjectId)
	{
		requireCharacterObjectId(characterObjectId);
		try (Connection connection = getConnection("find Phantom profile by character");
			PreparedStatement statement = connection.prepareStatement(FIND_PROFILE_BY_CHARACTER))
		{
			statement.setInt(1, characterObjectId);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return Optional.empty();
				}
				final PhantomProfile profile = readProfile(result);
				if (result.next())
				{
					throw new SQLException("Unique character link returned multiple profiles.");
				}
				return Optional.of(profile);
			}
		}
		catch (SQLException e)
		{
			throw persistenceFailure("find Phantom profile by character", e);
		}
	}

	public PhantomProfile updateCharacterLink(long profileId, long expectedRowVersion, Integer characterObjectId)
	{
		requireProfileId(profileId);
		requireRowVersion(expectedRowVersion);
		requireCharacterObjectId(characterObjectId);
		return write("update Phantom profile character link", connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement(UPDATE_CHARACTER_LINK))
			{
				if (characterObjectId == null)
				{
					statement.setNull(1, java.sql.Types.INTEGER);
				}
				else
				{
					statement.setInt(1, characterObjectId);
				}
				statement.setLong(2, profileId);
				statement.setLong(3, expectedRowVersion);
				requireOptimisticWinner(statement.executeUpdate(), "Phantom profile character link");
			}
			return requireProfile(connection, profileId);
		});
	}

	public void delete(long profileId, long expectedRowVersion)
	{
		requireProfileId(profileId);
		requireRowVersion(expectedRowVersion);
		write("delete Phantom profile", connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement(DELETE_PROFILE))
			{
				statement.setLong(1, profileId);
				statement.setLong(2, expectedRowVersion);
				requireOptimisticWinner(statement.executeUpdate(), "Phantom profile delete");
			}
			return null;
		});
	}

	public PhantomProfileComponent insertComponent(long profileId, String componentType, int componentSchemaVersion, byte[] payload)
	{
		requireProfileId(profileId);
		PhantomProfileComponent.requireValidComponentType(componentType);
		PhantomProfileComponent.requireValidSchemaVersion(componentSchemaVersion);
		final byte[] payloadCopy = PhantomProfileComponent.copyPayload(payload);
		return write("insert Phantom profile component", connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement(INSERT_COMPONENT))
			{
				statement.setLong(1, profileId);
				statement.setString(2, componentType);
				statement.setInt(3, componentSchemaVersion);
				statement.setBytes(4, payloadCopy);
				if (statement.executeUpdate() != 1)
				{
					throw new SQLException("Component insert did not affect exactly one row.");
				}
			}
			return requireComponent(connection, profileId, componentType);
		});
	}

	public Optional<PhantomProfileComponent> findComponent(long profileId, String componentType)
	{
		requireProfileId(profileId);
		PhantomProfileComponent.requireValidComponentType(componentType);
		try (Connection connection = getConnection("find Phantom profile component"))
		{
			return findComponent(connection, profileId, componentType);
		}
		catch (SQLException e)
		{
			throw persistenceFailure("find Phantom profile component", e);
		}
	}

	public List<PhantomProfileComponent> listComponents(long profileId)
	{
		requireProfileId(profileId);
		try (Connection connection = getConnection("list Phantom profile components");
			PreparedStatement statement = connection.prepareStatement(LIST_COMPONENTS))
		{
			statement.setLong(1, profileId);
			try (ResultSet result = statement.executeQuery())
			{
				final List<PhantomProfileComponent> components = new ArrayList<>();
				while (result.next())
				{
					components.add(readComponent(result));
				}
				return List.copyOf(components);
			}
		}
		catch (SQLException e)
		{
			throw persistenceFailure("list Phantom profile components", e);
		}
	}

	public PhantomProfileComponent updateComponent(long profileId, String componentType, long expectedRowVersion, int componentSchemaVersion, byte[] payload)
	{
		requireProfileId(profileId);
		PhantomProfileComponent.requireValidComponentType(componentType);
		requireRowVersion(expectedRowVersion);
		PhantomProfileComponent.requireValidSchemaVersion(componentSchemaVersion);
		final byte[] payloadCopy = PhantomProfileComponent.copyPayload(payload);
		return write("update Phantom profile component", connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement(UPDATE_COMPONENT))
			{
				statement.setInt(1, componentSchemaVersion);
				statement.setBytes(2, payloadCopy);
				statement.setLong(3, profileId);
				statement.setString(4, componentType);
				statement.setLong(5, expectedRowVersion);
				requireOptimisticWinner(statement.executeUpdate(), "Phantom profile component update");
			}
			return requireComponent(connection, profileId, componentType);
		});
	}

	public void deleteComponent(long profileId, String componentType, long expectedRowVersion)
	{
		requireProfileId(profileId);
		PhantomProfileComponent.requireValidComponentType(componentType);
		requireRowVersion(expectedRowVersion);
		write("delete Phantom profile component", connection ->
		{
			try (PreparedStatement statement = connection.prepareStatement(DELETE_COMPONENT))
			{
				statement.setLong(1, profileId);
				statement.setString(2, componentType);
				statement.setLong(3, expectedRowVersion);
				requireOptimisticWinner(statement.executeUpdate(), "Phantom profile component delete");
			}
			return null;
		});
	}

	private static Optional<PhantomProfile> findProfile(Connection connection, long profileId) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(FIND_PROFILE))
		{
			statement.setLong(1, profileId);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() ? Optional.of(readProfile(result)) : Optional.empty();
			}
		}
	}

	private static PhantomProfile requireProfile(Connection connection, long profileId) throws SQLException
	{
		return findProfile(connection, profileId).orElseThrow(() -> new SQLException("Profile row disappeared during its write operation."));
	}

	private static Optional<PhantomProfileComponent> findComponent(Connection connection, long profileId, String componentType) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(FIND_COMPONENT))
		{
			statement.setLong(1, profileId);
			statement.setString(2, componentType);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() ? Optional.of(readComponent(result)) : Optional.empty();
			}
		}
	}

	private static PhantomProfileComponent requireComponent(Connection connection, long profileId, String componentType) throws SQLException
	{
		return findComponent(connection, profileId, componentType).orElseThrow(() -> new SQLException("Component row disappeared during its write operation."));
	}

	private static PhantomProfile readProfile(ResultSet result) throws SQLException
	{
		final int characterObjectId = result.getInt("character_object_id");
		final Integer characterLink = result.wasNull() ? null : characterObjectId;
		return new PhantomProfile(
			result.getLong("profile_id"),
			characterLink,
			result.getInt("schema_version"),
			result.getLong("row_version"),
			toInstant(result.getTimestamp("created_at"), "profile created_at"),
			toInstant(result.getTimestamp("updated_at"), "profile updated_at"));
	}

	private static PhantomProfileComponent readComponent(ResultSet result) throws SQLException
	{
		return new PhantomProfileComponent(
			result.getLong("profile_id"),
			result.getString("component_type"),
			result.getInt("component_schema_version"),
			result.getLong("row_version"),
			result.getBytes("payload"),
			toInstant(result.getTimestamp("created_at"), "component created_at"),
			toInstant(result.getTimestamp("updated_at"), "component updated_at"));
	}

	private static java.time.Instant toInstant(Timestamp timestamp, String column) throws SQLException
	{
		if (timestamp == null)
		{
			throw new SQLException("Required timestamp is null: " + column);
		}
		return timestamp.toInstant();
	}

	private static void validateSchema(Connection connection) throws SQLException
	{
		validateCurrentDatabase(connection);
		validateTable(connection, "phantom_profiles", profileColumns(), Map.of(
			"PRIMARY", List.of("profile_id"),
			"uq_phantom_profiles_character_object_id", List.of("character_object_id")));
		validateTable(connection, "phantom_profile_components", componentColumns(), Map.of(
			"PRIMARY", List.of("profile_id", "component_type")));
		validateForeignKeys(connection);
	}

	private static void validateCurrentDatabase(Connection connection) throws SQLException
	{
		try (Statement statement = connection.createStatement();
			ResultSet result = statement.executeQuery("SELECT DATABASE()"))
		{
			if (!result.next() || (result.getString(1) == null) || result.getString(1).isBlank())
			{
				throw schemaMismatch("Repository connection has no selected database.");
			}
		}
	}

	private static void validateTable(Connection connection, String tableName, Map<String, ColumnExpectation> expectedColumns, Map<String, List<String>> expectedIndexes) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement("SELECT ENGINE, TABLE_COLLATION FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?"))
		{
			statement.setString(1, tableName);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw schemaMismatch("Required table is missing: " + tableName);
				}
				final String engine = result.getString("ENGINE");
				final String collation = result.getString("TABLE_COLLATION");
				if (!"InnoDB".equalsIgnoreCase(engine) || (collation == null) || !collation.toLowerCase(Locale.ROOT).startsWith("utf8mb4_"))
				{
					throw schemaMismatch("Table engine or character set is invalid: " + tableName);
				}
				if (result.next())
				{
					throw schemaMismatch("Duplicate table metadata found: " + tableName);
				}
			}
		}

		final Map<String, ColumnMetadata> actualColumns = new HashMap<>();
		try (PreparedStatement statement = connection.prepareStatement("SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT, EXTRA, CHARACTER_SET_NAME, COLLATION_NAME, DATETIME_PRECISION, CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ORDINAL_POSITION"))
		{
			statement.setString(1, tableName);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					final String name = result.getString("COLUMN_NAME");
					actualColumns.put(name, new ColumnMetadata(
						result.getString("DATA_TYPE"),
						result.getString("COLUMN_TYPE"),
						"YES".equals(result.getString("IS_NULLABLE")),
						result.getString("COLUMN_DEFAULT"),
						result.getString("EXTRA"),
						result.getString("CHARACTER_SET_NAME"),
						result.getString("COLLATION_NAME"),
						integerValue(result.getObject("DATETIME_PRECISION")),
						longValue(result.getObject("CHARACTER_MAXIMUM_LENGTH"))));
				}
			}
		}
		if (!actualColumns.keySet().equals(expectedColumns.keySet()))
		{
			throw schemaMismatch("Table columns are not exact: " + tableName);
		}
		for (Map.Entry<String, ColumnExpectation> entry : expectedColumns.entrySet())
		{
			if (!entry.getValue().matches(actualColumns.get(entry.getKey())))
			{
				throw schemaMismatch("Column definition is invalid: " + tableName + "." + entry.getKey());
			}
		}

		final Map<String, List<IndexColumn>> actualIndexColumns = new HashMap<>();
		try (PreparedStatement statement = connection.prepareStatement("SELECT INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? ORDER BY INDEX_NAME, SEQ_IN_INDEX"))
		{
			statement.setString(1, tableName);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					actualIndexColumns.computeIfAbsent(result.getString("INDEX_NAME"), _ -> new ArrayList<>()).add(new IndexColumn(result.getInt("NON_UNIQUE") == 0, result.getInt("SEQ_IN_INDEX"), result.getString("COLUMN_NAME")));
				}
			}
		}
		if (!actualIndexColumns.keySet().equals(expectedIndexes.keySet()))
		{
			throw schemaMismatch("Table indexes are not exact: " + tableName);
		}
		for (Map.Entry<String, List<String>> entry : expectedIndexes.entrySet())
		{
			final List<IndexColumn> actual = actualIndexColumns.get(entry.getKey());
			if ((actual == null) || (actual.size() != entry.getValue().size()))
			{
				throw schemaMismatch("Index definition is invalid: " + entry.getKey());
			}
			for (int index = 0; index < actual.size(); index++)
			{
				final IndexColumn column = actual.get(index);
				if (!column.unique() || (column.position() != (index + 1)) || !column.name().equals(entry.getValue().get(index)))
				{
					throw schemaMismatch("Index definition is invalid: " + entry.getKey());
				}
			}
		}
	}

	private static void validateForeignKeys(Connection connection) throws SQLException
	{
		final List<ForeignKeyMetadata> foreignKeys = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
			SELECT k.CONSTRAINT_NAME, k.TABLE_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME, r.DELETE_RULE
			FROM information_schema.key_column_usage k
			JOIN information_schema.referential_constraints r
			  ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
			 AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
			 AND r.TABLE_NAME = k.TABLE_NAME
			WHERE k.CONSTRAINT_SCHEMA = DATABASE()
			  AND k.TABLE_NAME IN ('phantom_profiles', 'phantom_profile_components')
			  AND k.REFERENCED_TABLE_NAME IS NOT NULL
			ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION
			"""))
		{
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					foreignKeys.add(new ForeignKeyMetadata(
						result.getString("CONSTRAINT_NAME"),
						result.getString("TABLE_NAME"),
						result.getString("COLUMN_NAME"),
						result.getString("REFERENCED_TABLE_NAME"),
						result.getString("REFERENCED_COLUMN_NAME"),
						result.getString("DELETE_RULE")));
				}
			}
		}
		final List<ForeignKeyMetadata> expected = List.of(new ForeignKeyMetadata(
			"fk_phantom_profile_components_profile",
			"phantom_profile_components",
			"profile_id",
			"phantom_profiles",
			"profile_id",
			"CASCADE"));
		if (!foreignKeys.equals(expected))
		{
			throw schemaMismatch("Phantom profile foreign key definition is invalid.");
		}
	}

	private static Map<String, ColumnExpectation> profileColumns()
	{
		return Map.of(
			"profile_id", ColumnExpectation.unsigned("bigint", false, null, "auto_increment"),
			"character_object_id", ColumnExpectation.plain("int", true, null),
			"schema_version", ColumnExpectation.unsigned("smallint", false, "1", ""),
			"row_version", ColumnExpectation.unsigned("bigint", false, "0", ""),
			"created_at", ColumnExpectation.timestamp(false, false),
			"updated_at", ColumnExpectation.timestamp(false, true));
	}

	private static Map<String, ColumnExpectation> componentColumns()
	{
		return Map.of(
			"profile_id", ColumnExpectation.unsigned("bigint", false, null, ""),
			"component_type", ColumnExpectation.asciiBinaryType(),
			"component_schema_version", ColumnExpectation.unsigned("smallint", false, null, ""),
			"row_version", ColumnExpectation.unsigned("bigint", false, "0", ""),
			"payload", ColumnExpectation.payload(),
			"created_at", ColumnExpectation.timestamp(false, false),
			"updated_at", ColumnExpectation.timestamp(false, true));
	}

	private static void requireProfileId(long profileId)
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("Profile ID must be positive.");
		}
	}

	private static void requireCharacterObjectId(Integer characterObjectId)
	{
		if ((characterObjectId != null) && (characterObjectId <= 0))
		{
			throw new IllegalArgumentException("Character object ID must be positive when present.");
		}
	}

	private static void requireCharacterObjectId(int characterObjectId)
	{
		if (characterObjectId <= 0)
		{
			throw new IllegalArgumentException("Character object ID must be positive.");
		}
	}

	private static void requireRowVersion(long rowVersion)
	{
		if (rowVersion < 0)
		{
			throw new IllegalArgumentException("Expected row version must not be negative.");
		}
	}

	private static void requireOptimisticWinner(int affectedRows, String target)
	{
		if (affectedRows == 0)
		{
			throw new ConcurrentModificationException(target + " lost an optimistic locking race.");
		}
		if (affectedRows != 1)
		{
			throw new PhantomProfilePersistenceException(Category.DATABASE_ERROR, target + " affected an unexpected number of rows.");
		}
	}

	private static Connection getConnection(String operation)
	{
		try
		{
			return DatabaseFactory.getConnection();
		}
		catch (RuntimeException e)
		{
			throw new PhantomProfilePersistenceException(Category.DATABASE_ERROR, "Could not " + operation + ".", e);
		}
	}

	private static <T> T write(String operation, SqlWrite<T> write)
	{
		try (Connection connection = getConnection(operation))
		{
			connection.setAutoCommit(false);
			try
			{
				final T result = write.execute(connection);
				connection.commit();
				return result;
			}
			catch (SQLException e)
			{
				rollback(connection, e);
				throw persistenceFailure(operation, e);
			}
			catch (RuntimeException e)
			{
				rollback(connection, e);
				throw e;
			}
		}
		catch (SQLException e)
		{
			throw persistenceFailure(operation, e);
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

	private static PhantomProfilePersistenceException persistenceFailure(String operation, SQLException cause)
	{
		final String sqlState = cause.getSQLState();
		final Category category = ((sqlState != null) && sqlState.startsWith("23")) ? Category.CONSTRAINT_VIOLATION : Category.DATABASE_ERROR;
		return new PhantomProfilePersistenceException(category, "Could not " + operation + ".", cause);
	}

	private static PhantomProfilePersistenceException schemaMismatch(String message)
	{
		return new PhantomProfilePersistenceException(Category.SCHEMA_MISMATCH, message);
	}

	private static Integer integerValue(Object value)
	{
		return value == null ? null : ((Number) value).intValue();
	}

	private static Long longValue(Object value)
	{
		return value == null ? null : ((Number) value).longValue();
	}

	@FunctionalInterface
	private interface SqlWrite<T>
	{
		T execute(Connection connection) throws SQLException;
	}

	private record ColumnMetadata(String dataType, String columnType, boolean nullable, String defaultValue, String extra, String characterSet, String collation, Integer datetimePrecision, Long maximumLength)
	{
	}

	private record ColumnExpectation(String dataType, boolean unsigned, boolean nullable, String defaultValue, String requiredExtra, String characterSet, String collation, Integer datetimePrecision, Long maximumLength)
	{
		static ColumnExpectation unsigned(String dataType, boolean nullable, String defaultValue, String requiredExtra)
		{
			return new ColumnExpectation(dataType, true, nullable, defaultValue, requiredExtra, null, null, null, null);
		}

		static ColumnExpectation plain(String dataType, boolean nullable, String defaultValue)
		{
			return new ColumnExpectation(dataType, false, nullable, defaultValue, "", null, null, null, null);
		}

		static ColumnExpectation timestamp(boolean nullable, boolean onUpdate)
		{
			return new ColumnExpectation("timestamp", false, nullable, "current_timestamp(3)", onUpdate ? "on update current_timestamp(3)" : "", null, null, 3, null);
		}

		static ColumnExpectation asciiBinaryType()
		{
			return new ColumnExpectation("varchar", false, false, null, "", "ascii", "ascii_bin", null, 64L);
		}

		static ColumnExpectation payload()
		{
			return new ColumnExpectation("varbinary", false, false, null, "", null, null, null, 4096L);
		}

		boolean matches(ColumnMetadata actual)
		{
			if ((actual == null) || !dataType.equalsIgnoreCase(actual.dataType()) || (nullable != actual.nullable()))
			{
				return false;
			}
			final String columnType = normalize(actual.columnType());
			if (unsigned != columnType.contains("unsigned"))
			{
				return false;
			}
			if (!normalizeDefault(defaultValue).equals(normalizeDefault(actual.defaultValue())) || !normalize(actual.extra()).contains(normalize(requiredExtra)))
			{
				return false;
			}
			if ((characterSet != null) && !characterSet.equalsIgnoreCase(actual.characterSet()))
			{
				return false;
			}
			if ((collation != null) && !collation.equalsIgnoreCase(actual.collation()))
			{
				return false;
			}
			if ((datetimePrecision != null) && !datetimePrecision.equals(actual.datetimePrecision()))
			{
				return false;
			}
			return (maximumLength == null) || maximumLength.equals(actual.maximumLength());
		}

		private static String normalize(String value)
		{
			return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		}

		private static String normalizeDefault(String value)
		{
			final String normalized = normalize(value);
			return "null".equals(normalized) ? "" : normalized;
		}
	}

	private record IndexColumn(boolean unique, int position, String name)
	{
	}

	private record ForeignKeyMetadata(String constraintName, String tableName, String columnName, String referencedTableName, String referencedColumnName, String deleteRule)
	{
	}
}

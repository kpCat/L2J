import java.sql.DriverManager;
import java.nio.file.Path;

import org.l2jmobius.commons.config.DatabaseConfig;

/** Read-only operator preflight; credentials remain in the local configuration. */
public final class RuntimeDatabaseCheck
{
	public static void main(String[] args) throws Exception
	{
		if ((args.length != 3) || !args[0].matches("l2jmobiush5_phantom_(?:test_041final|test_058a|release_[A-Za-z0-9_]{1,24})"))
		{
			throw new IllegalArgumentException("Explicit release schema and two configuration paths required.");
		}
		RuntimeCampaign058Guard.validate(Path.of(args[1]).toAbsolutePath().getParent().getParent().getParent(), args[0]);
		for (int i = 1; i < 3; i++)
		{
			DatabaseConfig.load(args[i]);
			if (!DatabaseConfig.DATABASE_URL.matches("jdbc:(?:mysql|mariadb)://(?:127\\.0\\.0\\.1|localhost):[0-9]+/" + args[0] + "(?:\\?.*)?"))
			{
				throw new IllegalStateException("Release JDBC namespace mismatch.");
			}
			try (var connection = DriverManager.getConnection(DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_LOGIN, DatabaseConfig.DATABASE_PASSWORD))
			{
				connection.setReadOnly(true);
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT DATABASE()"))
				{
					if (!rows.next() || !args[0].equals(rows.getString(1)))
					{
						throw new IllegalStateException("Connected catalog mismatch.");
					}
				}
				for (String table : new String[] { "accounts", "characters", "phantom_profiles", "phantom_profile_components", "gameservers" })
				{
					try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 1 FROM " + table + " LIMIT 1"))
					{
						// Existing populated and empty tables are both valid; no writes or reseed.
					}
				}
			}
		}
		System.out.println("DATABASE_PREFLIGHT_OK");
	}
}

import java.sql.DriverManager;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.commons.config.DatabaseConfig;

/** Read-only operator preflight; optional fourth058 label records saved state, never credentials or World claims. */
public final class RuntimeDatabaseCheck
{
	public static void main(String[] args) throws Exception
	{
		if (((args.length != 3) && (args.length != 4)) || !args[0].matches("l2jmobiush5_phantom_(?:test_041final|test_058a|release_[A-Za-z0-9_]{1,24})"))
		{
			throw new IllegalArgumentException("Explicit release schema and two configuration paths required.");
		}
		if ((args.length == 4) && (!Set.of("l2jmobiush5_phantom_test_058a", "l2jmobiush5_phantom_release_058a", "l2jmobiush5_phantom_release_059a", "l2jmobiush5_phantom_release_060a").contains(args[0]) || !args[3].matches("[a-z0-9-]{1,40}"))) { throw new IllegalArgumentException("Snapshot requires an exact campaign schema and bounded label."); }
		RuntimeCampaign058Guard.validate(Path.of(args[1]).toAbsolutePath().getParent().getParent().getParent(), args[0]);
		for (int i = 1; i < 3; i++)
		{
			DatabaseConfig.load(args[i]);
			if ((args.length == 4) && !DatabaseConfig.DATABASE_URL.matches("jdbc:(?:mysql|mariadb)://127\\.0\\.0\\.1:3308/" + args[0] + "\\?useSSL=false(?:&[A-Za-z0-9=:-]+)*")) { throw new IllegalStateException("Snapshot requires the exact local058 JDBC endpoint."); }
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
		if (args.length == 4) { snapshot(args[0], Path.of(args[1]).toAbsolutePath().getParent().getParent().getParent(), args[3]); }
	}

	private static void snapshot(String database, Path root, String label) throws Exception
	{
		final Map<String, String> queries = new LinkedHashMap<>();
		queries.put("identities", "SELECT charId,account_name,char_name,level,classid,base_class,accesslevel FROM characters ORDER BY charId");
		queries.put("stable-identities", "SELECT charId,account_name,char_name,base_class,accesslevel FROM characters ORDER BY charId");
		queries.put("profiles", "SELECT profile_id,character_object_id FROM phantom_profiles ORDER BY profile_id");
		queries.put("skills", "SELECT charId,skill_id,skill_level,class_index FROM character_skills ORDER BY charId,class_index,skill_id");
		queries.put("items", "SELECT owner_id,object_id,item_id,count,enchant_level,loc,loc_data FROM items ORDER BY object_id");
		final String prepared = "(char_name='TestAdmin' OR char_name LIKE 'T058%')";
		queries.put("prepared-state", "SELECT charId,char_name,level,classid,base_class,x,y,z,exp,sp,curHp,curMp FROM characters WHERE " + prepared + " ORDER BY charId");
		queries.put("prepared-skills", "SELECT s.charId,s.skill_id,s.skill_level,s.class_index FROM character_skills s JOIN characters c ON c.charId=s.charId WHERE " + prepared + " ORDER BY s.charId,s.class_index,s.skill_id");
		queries.put("prepared-items", "SELECT i.owner_id,i.object_id,i.item_id,i.count,i.enchant_level,i.loc,i.loc_data FROM items i JOIN characters c ON c.charId=i.owner_id WHERE " + prepared + " ORDER BY i.object_id");
		final StringBuilder output = new StringBuilder("kind\tcount\tsha256\n");
		try (var connection = DriverManager.getConnection(DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_LOGIN, DatabaseConfig.DATABASE_PASSWORD))
		{
			connection.setReadOnly(true);
			try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
			{
				statement.setString(1, database);
				try (var rows = statement.executeQuery()) { if (!rows.next() || (rows.getLong(1) != 0)) { throw new IllegalStateException("Snapshot requires a stopped/quiescent own058 database."); } }
			}
			for (var query : queries.entrySet())
			{
				final MessageDigest digest = MessageDigest.getInstance("SHA-256"); int count = 0;
				try (var statement = connection.createStatement(); var rows = statement.executeQuery(query.getValue()))
				{
					while (rows.next())
					{
						for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { final String value = String.valueOf(rows.getObject(column)); digest.update((value.length() + ":" + value + "|").getBytes(StandardCharsets.UTF_8)); }
						digest.update((byte) '\n'); count++;
					}
				}
				if ((query.getKey().equals("identities") && (count != 33)) || (query.getKey().equals("profiles") && (count != 32)) || (query.getKey().equals("prepared-state") && (count != 7))) { throw new IllegalStateException("Exact058 prepared identity inventory differs."); }
				output.append(query.getKey()).append('\t').append(count).append('\t').append(HexFormat.of().formatHex(digest.digest())).append('\n');
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT charId,char_name,level,classid,x,y,z,exp,sp,curHp,curMp FROM characters WHERE " + prepared + " ORDER BY charId"))
			{
				while (rows.next()) { System.out.println("SAVED_CHARACTER " + rows.getInt(1) + " " + rows.getString(2) + " level=" + rows.getInt(3) + " class=" + rows.getInt(4) + " xyz=" + rows.getInt(5) + ',' + rows.getInt(6) + ',' + rows.getInt(7) + " exp=" + rows.getLong(8) + " sp=" + rows.getLong(9) + " hp=" + rows.getInt(10) + " mp=" + rows.getInt(11)); }
			}
		}
		final Path directory = root.resolve("local-play/checkpoints");
		for (Path part : new Path[] {root.resolve("local-play"), directory}) { if (Files.exists(part) && !part.toRealPath().equals(part.normalize())) { throw new IllegalStateException("Snapshot output alias forbidden."); } }
		Files.createDirectories(directory);
		Files.writeString(directory.resolve(label + ".tsv"), output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.print(output);
		System.out.println("SNAPSHOT_READ_ONLY=true; WORLD_PRESENCE_NOT_CLAIMED; label=" + label);
	}
}

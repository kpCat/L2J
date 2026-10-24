/* Copyright (c) 2013 L2jMobius */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.Set;

/** Read-only OWN074 runtime witness. Never opens historical schemas. */
public final class Runtime074Probe
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2 || !Set.of("status", "quiescent", "snapshot").contains(args[0])) { throw new IllegalArgumentException("OWN074 read-only probe action required."); }
		final var root = Path.of(args[1]).toAbsolutePath().normalize(); final var owner = read(root.resolve("ownership074.properties"));
		final String schema = owner.getProperty("schema", ""); final var module = Path.of(owner.getProperty("sourceModuleRoot", "")).toAbsolutePath().normalize();
		final boolean test = schema.equals("l2jmobiush5_phantom_test_074");
		if (!Set.of("l2jmobiush5_phantom_test_074", "l2jmobiush5_phantom_release_074").contains(schema) || !"TASK074_LIVING_SYSTEMS".equals(owner.getProperty("owner")) || !root.equals(root.toRealPath()) || !root.equals(test ? module.getParent().resolve(".phantom-local/task074/runtime-test") : module.getRoot().resolve("PhantomWorld/Living-Systems-074-candidate")) || !root.equals(Path.of(owner.getProperty("runtimeRoot", ""))) || !module.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive")) { throw new IllegalArgumentException("OWN074 probe root/owner/schema differs."); }
		final var config = read(root.resolve("game/config/Database.ini"));
		if (!config.getProperty("URL", "").matches("jdbc:mysql://127\\.0\\.0\\.1:3308/" + schema + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?")) { throw new IllegalArgumentException("OWN074 probe endpoint differs."); }
		Class.forName(config.getProperty("Driver"));
		try (var connection = DriverManager.getConnection(config.getProperty("URL"), config.getProperty("Login"), config.getProperty("Password")))
		{
			connection.setReadOnly(true); if (!schema.equals(connection.getCatalog())) { throw new IllegalArgumentException("OWN074 probe catalog differs."); }
			try (var statement = connection.prepareStatement("SELECT task_id,installation_id,schema_version,migration_sha256 FROM economy071_authority WHERE schema_name=?"))
			{
				statement.setString(1, schema); try (var rows = statement.executeQuery()) { if (!rows.next() || rows.getInt(1) != 74 || !rows.getString(2).equals(owner.getProperty("installationId")) || rows.getInt(3) != 1 || !rows.getString(4).equals("e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3") || rows.next()) { throw new IllegalArgumentException("OWN074 installed authority differs."); } }
			}
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()")) { rows.next(); final long count = rows.getLong(1); if (!args[0].equals("status") && count != 0) { throw new IllegalStateException("OWN074 connections remain: " + count); } if (!args[0].equals("snapshot")) { System.out.println(count); } }
			if (args[0].equals("snapshot"))
			{
				for (var entry : new String[][] {{"identities", "SELECT charId,account_name,char_name,base_class,accesslevel FROM characters ORDER BY charId"}, {"items", "SELECT owner_id,object_id,item_id,count,loc,loc_data FROM items ORDER BY object_id"}, {"merchantQuota", "SELECT profile_id,HEX(payload) FROM phantom_profile_components WHERE component_type='merchant.server.v1' ORDER BY profile_id"}, {"offlineOwners", "SELECT charId,type,title FROM character_offline_trade ORDER BY charId"}, {"offlineItems", "SELECT charId,item,count,price FROM character_offline_trade_items ORDER BY charId,item"}})
				{
					final var digest = java.security.MessageDigest.getInstance("SHA-256"); int count = 0;
					try (var statement = connection.createStatement(); var rows = statement.executeQuery(entry[1])) { final int columns = rows.getMetaData().getColumnCount(); while (rows.next()) { for (int column = 1; column <= columns; column++) { final String value = rows.getString(column); digest.update((value == null ? "-1:" : value.length() + ":" + value).getBytes(StandardCharsets.UTF_8)); digest.update((byte) '\n'); } count++; } }
					System.out.println(entry[0] + '\t' + count + '\t' + java.util.HexFormat.of().formatHex(digest.digest()));
				}
				try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT p.profile_id,c.charId,c.char_name,c.level,c.exp,c.sp,c.curHp,c.x,c.y,c.z FROM phantom_profiles p JOIN characters c ON c.charId=p.character_object_id WHERE c.account_name NOT LIKE 'phantom_merchant%' ORDER BY p.profile_id LIMIT 24")) { while (rows.next()) { final var values = new java.util.ArrayList<String>(); for (int column = 1; column <= 10; column++) { values.add(rows.getString(column)); } System.out.println("actor\t" + String.join("\t", values)); } }
			}
		}
	}
	private static Properties read(Path file) throws Exception
	{
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.size(file) > 16384) { throw new IllegalArgumentException("OWN074 physical bounded input required."); }
		final var result = new Properties(); try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); } return result;
	}
}

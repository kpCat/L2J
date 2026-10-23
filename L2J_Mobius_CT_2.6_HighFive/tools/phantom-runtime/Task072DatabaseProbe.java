/* Copyright (c) 2013 L2jMobius */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.Set;

/** Own072 read-only connection inventory. No schema creation, data mutation or historical DB access. */
public final class Task072DatabaseProbe
{
    public static void main(String[] args) throws Exception
    {
        if (args.length != 3 || !Set.of("status", "quiescent", "snapshot").contains(args[0]) || !Set.of("l2jmobiush5_phantom_test_072", "l2jmobiush5_phantom_release_072").contains(args[2])) { throw new IllegalArgumentException("Exact own072 root/schema and read-only mode required."); }
        final Path root = Path.of(args[1]).toAbsolutePath().normalize();
        if (!root.equals(root.toRealPath())) { throw new IllegalArgumentException("Physical own072 runtime required."); }
        final Properties owner = read(root.resolve("ownership072.properties"));
        final Path module = Path.of(owner.getProperty("sourceModuleRoot", "")).toAbsolutePath().normalize();
        final boolean test = args[2].equals("l2jmobiush5_phantom_test_072");
        final Path expected = test ? module.getParent().resolve(".phantom-local/task072/runtime-test") : module.getRoot().resolve("PhantomWorld/Sustainable-Life-072-candidate");
        if (!module.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive") || !root.equals(expected) || !"TASK072_SUSTAINABLE_LIFE".equals(owner.getProperty("owner")) || !"1".equals(owner.getProperty("version")) || !args[2].equals(owner.getProperty("schema")) || !root.equals(Path.of(owner.getProperty("runtimeRoot", "")).toAbsolutePath().normalize()) || !(test ? "MUTABLE_FIXTURE" : "RELEASE_CANDIDATE").equals(owner.getProperty("role"))) { throw new IllegalArgumentException("Own072 runtime binding differs."); }
        Properties database = null;
        for (String role : new String[] {"game", "login"})
        {
            final Properties config = read(root.resolve(role + "/config/Database.ini"));
            if (!config.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + args[2] + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?")) { throw new IllegalArgumentException("Own072 JDBC binding differs."); }
            if (database == null) { database = config; }
        }
        Class.forName(database.getProperty("Driver"));
        try (var connection = DriverManager.getConnection(database.getProperty("URL"), database.getProperty("Login"), database.getProperty("Password")))
        {
            connection.setReadOnly(true);
            if (!args[2].equals(connection.getCatalog())) { throw new IllegalStateException("Connected catalog differs."); }
            try (var query = connection.prepareStatement("SELECT task_id,installation_id,schema_version,migration_sha256 FROM economy071_authority WHERE schema_name=?"))
            {
                query.setString(1,args[2]);
                try (var rows = query.executeQuery())
                {
                    if (!rows.next() || rows.getInt(1)!=72 || !rows.getString(2).equals(owner.getProperty("installationId")) || rows.getInt(3)!=1 || !rows.getString(4).equals("e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3") || rows.next()) { throw new IllegalArgumentException("Own072 installed economy authority differs."); }
                }
            }
            for (String table : new String[] {"accounts", "characters", "gameservers", "phantom_profiles", "phantom_profile_components"})
            {
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) { }
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()"))
            {
                rows.next();
                final long otherConnections = rows.getLong(1);
                if (!args[0].equals("status") && otherConnections != 0) { throw new IllegalStateException("Own072 database still has connections: " + otherConnections); }
                if (!args[0].equals("snapshot")) { System.out.println(otherConnections); }
            }
            if (args[0].equals("snapshot")) { snapshot(connection); }
        }
    }

    private static void snapshot(java.sql.Connection connection) throws Exception
    {
        final String[][] inventories = {
            {"identities", "SELECT charId,account_name,char_name,base_class,accesslevel FROM characters ORDER BY charId"},
            {"items", "SELECT owner_id,object_id,item_id,count,loc,loc_data,enchant_level FROM items ORDER BY object_id"},
            {"profiles", "SELECT profile_id,character_object_id,schema_version FROM phantom_profiles ORDER BY profile_id"},
            {"components", "SELECT profile_id,component_type,component_schema_version,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type"},
            {"merchantQuota", "SELECT profile_id,HEX(payload) FROM phantom_profile_components WHERE component_type='merchant.server.v1' ORDER BY profile_id"},
            {"offlineOwners", "SELECT charId,type,title FROM character_offline_trade ORDER BY charId"},
            {"offlineItems", "SELECT charId,item,count,price FROM character_offline_trade_items ORDER BY charId,item"}
        };
        System.out.println("snapshot\trows\tsha256");
        for (var inventory : inventories)
        {
            final var digest = java.security.MessageDigest.getInstance("SHA-256"); long count = 0;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(inventory[1]))
            {
                final int columns = rows.getMetaData().getColumnCount();
                while (rows.next())
                {
                    for (int column = 1; column <= columns; column++)
                    {
                        final String value = rows.getString(column);
                        digest.update((value == null ? "-1:" : value.length() + ":" + value).getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) '\n');
                    }
                    count++;
                }
            }
            System.out.println(inventory[0] + '\t' + count + '\t' + java.util.HexFormat.of().formatHex(digest.digest()));
        }
        System.out.println("profileId\tcharacterId\tname\tlevel\tclassId\tx\ty\tz\thp\tmp\tcp\texp\tsp");
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT p.profile_id,c.charId,c.char_name,c.level,c.classid,c.x,c.y,c.z,c.curHp,c.curMp,c.curCp,c.exp,c.sp FROM phantom_profiles p JOIN characters c ON c.charId=p.character_object_id JOIN phantom_profile_components s ON s.profile_id=p.profile_id AND s.component_type='population.state' ORDER BY p.profile_id LIMIT 32"))
        {
            while (rows.next())
            {
                for (int column = 1; column <= 13; column++) { if (column > 1) { System.out.print('\t'); } System.out.print(rows.getString(column)); }
                System.out.println();
            }
        }
    }

    private static Properties read(Path file) throws Exception
    {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.equals(file.toRealPath()) || Files.size(file) > 16384) { throw new IllegalArgumentException("Bounded physical own072 config required."); }
        final Properties result = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }
}

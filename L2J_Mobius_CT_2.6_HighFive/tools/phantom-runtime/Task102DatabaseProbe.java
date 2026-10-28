/* Copyright (c) 2013 L2jMobius */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import org.l2jmobius.tests.phantoms.PhantomTask102Authority;

/** Exact OWN102 metadata preflight and create-only stock bootstrap. Never adopts/resets a schema. */
public final class Task102DatabaseProbe
{
    private static final String SCHEMA = "l2jmobiush5_phantom_test_102";
    public static void main(String[] args) throws Exception
    {
        if (args.length != 3 || !java.util.Set.of("preflight", "plan", "create", "postflight").contains(args[0])) { throw new IllegalArgumentException("OWN102 exact preflight/plan/create/postflight mode required."); }
        final Path stage = Path.of(args[1]).toAbsolutePath().normalize();
        physical(stage);
        final Properties owner = read(stage.resolve("private/owner.properties"));
        if (!"TASK102_POPULATION_SCALE".equals(owner.getProperty("owner")) || !SCHEMA.equals(owner.getProperty("schema"))
            || !java.util.UUID.fromString(args[2]).toString().equals(owner.getProperty("installationId"))
            || !stage.equals(Path.of(owner.getProperty("stage", "")).toRealPath())
            || !Instant.parse(owner.getProperty("expiresUtc", "")).isAfter(Instant.now())
            || !"feature/phantom-world".equals(owner.getProperty("branch"))) { throw new IllegalArgumentException("OWN102 expired or mismatched authority."); }
        final Path source = Path.of(owner.getProperty("sourceModule", "")).toRealPath();
        if (stage.startsWith(source.getParent()) || !source.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive")) { throw new IllegalArgumentException("OWN102 external source/stage required."); }
        PhantomTask102Authority.verify(stage, source, owner, false);
        final Path inputs = stage.resolve("inputs.tsv");
        if (!hash(inputs).equals(owner.getProperty("inputsSha256"))) { throw new IllegalArgumentException("OWN102 inventory drift."); }
        physical(stage.resolve("prepare.completed"));
        final Path config = source.resolve("dist/game/config/Database.ini");
        if (!hash(config).equals(owner.getProperty("sourceDatabaseSha256"))) { throw new IllegalArgumentException("OWN102 credential origin drift; values withheld."); }
        final Properties database = read(config);
        final String original = database.getProperty("URL", "");
        if (!original.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5(?:\\?[^\\r\\n]*)?")) { throw new IllegalArgumentException("OWN102 source JDBC is not exact loopback origin; values withheld."); }
        final String url = original.replaceFirst("/l2jmobiush5(?=\\?|$)", "/information_schema");
        Class.forName(database.getProperty("Driver"));
        try (var connection = DriverManager.getConnection(url, database.getProperty("Login"), database.getProperty("Password")))
        {
            connection.setReadOnly(true);
            if (!"information_schema".equals(connection.getCatalog())) { throw new IllegalStateException("OWN102 metadata catalog mismatch."); }
            // Full metadata visibility is mandatory; empty filtered views cannot prove absence.
            boolean select = false, process = false, showDatabase = false, create = false;
            int grants = 0;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER"))
            {
                while (rows.next())
                {
                    final String grant = rows.getString(1).toUpperCase(java.util.Locale.ROOT);
                    if (grant.contains(" ON *.* "))
                    {
                        final boolean all = grant.startsWith("GRANT ALL PRIVILEGES ");
                        select |= all || grant.matches(".*\\bSELECT\\b.*");
                        process |= all || grant.matches(".*\\bPROCESS\\b.*");
                        showDatabase |= all || grant.contains("SHOW DATABASES");
                        create |= all || grant.matches(".*\\bCREATE\\b.*");
                    }
                    grants++;
                }
            }
            if (!select || !process || !showDatabase) { throw new IllegalStateException("OWN102_INCOMPLETE_METADATA_VISIBILITY; no DDL/DML."); }
            if (args[0].equals("postflight")) { postflight(connection, stage, owner); return; }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + SCHEMA + "'"))
            {
                rows.next(); if (rows.getLong(1) != 0) { throw new IllegalStateException("OWN102_SCHEMA_ALREADY_EXISTS; no adopt/reset/drop."); }
            }
            long sessions;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID()")) { rows.next(); sessions = rows.getLong(1); }
            long schemas;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.SCHEMATA")) { rows.next(); schemas = rows.getLong(1); }
            System.out.println("OWN102_METADATA_PASS absent=true fullVisibility=true schemas=" + schemas + ";foreignSessions=" + sessions + ";grantsInspected=" + grants + ";DDL_DML=0;World=0");
            if (!args[0].equals("preflight"))
            {
                if (!create || sessions != 0 || Files.exists(stage.resolve("private/db.created"))) { throw new IllegalStateException("OWN102_CREATE_AUTHORITY_OR_QUIESCENCE; no DDL/DML."); }
                bootstrap(connection, stage, owner, args[2], args[0].equals("plan"));
            }
        }
        catch (java.sql.SQLException failure)
        {
            // Drivers may include credentials/URLs in exception text. Only structured codes leave this boundary.
            throw new IllegalStateException("OWN102_METADATA_SQL_FAILURE state=" + failure.getSQLState() + ";code=" + failure.getErrorCode());
        }
    }
    private static void postflight(java.sql.Connection connection, Path stage, Properties owner) throws Exception
    {
        if (!Files.readString(stage.resolve("private/db.created"), StandardCharsets.UTF_8).startsWith(owner.getProperty("installationId") + "\n")) { throw new IllegalStateException("OWN102_CREATED_ORIGIN_MISSING"); }
        connection.setCatalog(SCHEMA);
        if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalStateException("OWN102_POST_CATALOG"); }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT installation_id,source_head,inputs_sha256 FROM own102_authority"))
        {
            if (!rows.next() || !owner.getProperty("installationId").equals(rows.getString(1)) || !owner.getProperty("sourceHead").equals(rows.getString(2)) || !owner.getProperty("inputsSha256").equals(rows.getString(3)) || rows.next()) { throw new IllegalStateException("OWN102_POST_AUTHORITY"); }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()"))
        { rows.next(); if (rows.getLong(1) != 0) { throw new IllegalStateException("OWN102_POST_SESSIONS_REMAIN"); } }
        for (String table : new String[] {"characters", "phantom_profiles", "phantom_profile_components", "character_offline_trade", "character_offline_trade_items"})
        {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { rows.next(); System.out.println("OWN102_POST_ROWS " + table + "=" + rows.getLong(1)); }
        }
        final String[][] slices = {
            {"merchant", "SELECT profile_id,component_type,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type"},
            {"inventory", "SELECT owner_id,object_id,item_id,count,loc FROM items ORDER BY object_id"},
            {"offline", "SELECT charId,item,count,price FROM character_offline_trade_items ORDER BY charId,item"}
        };
        for (var slice : slices)
        {
            final var digest = MessageDigest.getInstance("SHA-256");
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(slice[1]))
            { while (rows.next()) { for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { final String value = rows.getString(column); digest.update((value.length() + ":" + value + "\n").getBytes(StandardCharsets.UTF_8)); } } }
            System.out.println("OWN102_POST_HASH " + slice[0] + "=" + HexFormat.of().formatHex(digest.digest()));
        }
        System.out.println("OWN102_POSTFLIGHT_PASS ownSessions=0;DDL_DML=0;World=0");
    }
    private static void bootstrap(java.sql.Connection connection, Path stage, Properties owner, String uuid, boolean dryRun) throws Exception
    {
        final Path module = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
        final var pins = new java.util.HashMap<String, String>();
        for (String line : Files.readAllLines(stage.resolve("inputs.tsv"), StandardCharsets.UTF_8))
        {
            final String[] fields = line.split("\\t");
            if (fields.length != 2 || pins.put(fields[0], fields[1]) != null) { throw new IllegalStateException("OWN102_SQL_INVENTORY_AMBIGUOUS"); }
        }
        final var statements = new java.util.ArrayList<String>();
        final var manifest = new StringBuilder("path\tsha256\n");
        int files = 0, omittedDrop = 0;
        for (String kind : new String[] {"login", "game"})
        {
            try (var paths = Files.list(module.resolve("dist/db_installer/sql/" + kind)))
            {
                for (Path path : paths.filter(path -> path.toString().endsWith(".sql")).sorted().toList())
                {
                    final String relative = module.relativize(path).toString().replace('\\', '/');
                    final String digest = hash(path);
                    if (!digest.equals(pins.get(relative))) { throw new IllegalStateException("OWN102_STOCK_SQL_DRIFT"); }
                    manifest.append(relative).append('\t').append(digest).append('\n'); files++;
                    for (String sql : splitSql(Files.readString(path, StandardCharsets.UTF_8)))
                    {
                        if (sql.matches("(?is)DROP TABLE IF EXISTS\\s+`?[a-zA-Z0-9_]+`?")) { omittedDrop++; continue; }
                        if (!sql.matches("(?is)(?:CREATE TABLE(?: IF NOT EXISTS)?|CREATE(?: UNIQUE)? INDEX|INSERT(?: IGNORE)? INTO|REPLACE INTO|ALTER TABLE|SET)\\s+.*")
                            || sql.matches("(?is).*\\b(?:GRANT|REVOKE|CREATE DATABASE|DROP DATABASE|TRUNCATE|USE)\\s+.*")) { throw new IllegalStateException("OWN102_UNAPPROVED_STOCK_SQL file=" + relative); }
                        statements.add(sql);
                    }
                }
            }
        }
        if (files != 121) { throw new IllegalStateException("OWN102_STOCK_SQL_COUNT"); }
        if (dryRun) { System.out.println("OWN102_SQL_PLAN_PASS files=" + files + ";statements=" + statements.size() + ";omittedStockDrop=" + omittedDrop + ";DDL_DML=0"); return; }
        Files.writeString(stage.resolve("private/sql-manifest.tsv"), manifest, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        connection.setReadOnly(false);
        try (var statement = connection.createStatement()) { statement.executeUpdate("CREATE DATABASE `" + SCHEMA + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
        Files.writeString(stage.resolve("private/db.created"), uuid + "\n" + Instant.now(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        connection.setCatalog(SCHEMA);
        if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalStateException("OWN102_CREATE_CATALOG"); }
        for (String sql : statements) { try (var statement = connection.createStatement()) { statement.execute(sql); } }
        try (var statement = connection.createStatement()) { statement.executeUpdate("CREATE TABLE own102_authority (installation_id CHAR(36) PRIMARY KEY, source_head CHAR(40) NOT NULL, inputs_sha256 CHAR(64) NOT NULL)"); }
        try (var statement = connection.prepareStatement("INSERT INTO own102_authority VALUES(?,?,?)"))
        {
            statement.setString(1, uuid); statement.setString(2, owner.getProperty("sourceHead")); statement.setString(3, owner.getProperty("inputsSha256")); statement.executeUpdate();
        }
        System.out.println("OWN102_CREATED stockFiles=" + files + ";statements=" + statements.size() + ";omittedStockDrop=" + omittedDrop + ";foreignWrites=0;World=0");
    }
    // Stock installer SQL only; quoted semicolons and line/block comments stay inside their proper boundary.
    private static java.util.List<String> splitSql(String text)
    {
        final var result = new java.util.ArrayList<String>(); final var sql = new StringBuilder();
        char quote = 0; boolean line = false, block = false;
        for (int i = 0; i < text.length(); i++)
        {
            final char c = text.charAt(i), next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (line) { if (c == '\n') { line = false; sql.append(' '); } continue; }
            if (block) { if (c == '*' && next == '/') { block = false; i++; sql.append(' '); } continue; }
            if (quote != 0)
            {
                sql.append(c);
                if (c == '\\' && next != 0) { sql.append(next); i++; }
                else if (c == quote) { if (next == quote) { sql.append(next); i++; } else { quote = 0; } }
                continue;
            }
            if (c == '#' || c == '-' && next == '-') { line = true; if (c == '-') { i++; } continue; }
            if (c == '/' && next == '*') { block = true; i++; continue; }
            if (c == '\'' || c == '"' || c == '`') { quote = c; sql.append(c); continue; }
            if (c == ';') { if (!sql.toString().isBlank()) { result.add(sql.toString().strip()); } sql.setLength(0); }
            else { sql.append(c); }
        }
        if (quote != 0 || block || !sql.toString().isBlank()) { throw new IllegalArgumentException("OWN102_UNTERMINATED_STOCK_SQL"); }
        return result;
    }
    private static void physical(Path file) throws Exception
    {
        if (!file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.isSymbolicLink(file)) { throw new IllegalArgumentException("OWN102 physical path required."); }
        for (Path part = file; part != null; part = part.getParent())
        {
            final var attributes = Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther()) { throw new IllegalArgumentException("OWN102 reparse path forbidden."); }
        }
    }
    private static Properties read(Path file) throws Exception
    {
        physical(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 32768) { throw new IllegalArgumentException("OWN102 bounded input required."); }
        final var result = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
        return result;
    }
    private static String hash(Path file) throws Exception
    {
        physical(file);
        final var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) { final byte[] buffer = new byte[65536]; int length; while ((length = input.read(buffer)) >= 0) { digest.update(buffer, 0, length); } }
        return HexFormat.of().formatHex(digest.digest());
    }
}

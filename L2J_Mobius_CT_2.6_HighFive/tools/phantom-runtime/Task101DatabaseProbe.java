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
import org.l2jmobius.tests.phantoms.PhantomTask101Authority;

/** Audited099 create-only protocol with separate100 schema/authority; never adopts/resets a schema. */
public final class Task101DatabaseProbe
{
    private static final String SCHEMA = "l2jmobiush5_phantom_test_101";
    public static void main(String[] args) throws Exception
    {
        if (args.length != 3 || !java.util.Set.of("preflight", "plan", "create", "postflight", "repaircheck", "continuation", "inventorycheck", "stackproof").contains(args[0])) { throw new IllegalArgumentException("OWN101 exact probe mode required."); }
        final Path stage = Path.of(args[1]).toAbsolutePath().normalize();
        physical(stage);
        final Properties owner = read(stage.resolve("private/owner.properties"));
        if (!"TASK101_CRAFT_MARKET".equals(owner.getProperty("owner")) || !SCHEMA.equals(owner.getProperty("schema"))
            || !java.util.UUID.fromString(args[2]).toString().equals(owner.getProperty("installationId"))
            || !stage.equals(Path.of(owner.getProperty("stage", "")).toRealPath())
            || !Instant.parse(owner.getProperty("expiresUtc", "")).isAfter(Instant.now())
            || !"feature/phantom-world".equals(owner.getProperty("branch"))) { throw new IllegalArgumentException("OWN101 expired or mismatched authority."); }
        final Path source = Path.of(owner.getProperty("sourceModule", "")).toRealPath();
        if (stage.startsWith(source.getParent()) || !source.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive")) { throw new IllegalArgumentException("OWN101 external source/stage required."); }
        PhantomTask101Authority.verify(stage, source, owner, false);
        final Path inputs = stage.resolve("inputs.tsv");
        if (!hash(inputs).equals(owner.getProperty("inputsSha256"))) { throw new IllegalArgumentException("OWN101 inventory drift."); }
        physical(stage.resolve("prepare.completed"));
        final Path config = source.resolve("dist/game/config/Database.ini");
        if (!hash(config).equals(owner.getProperty("sourceDatabaseSha256"))) { throw new IllegalArgumentException("OWN101 credential origin drift; values withheld."); }
        final Properties database = read(config);
        final String original = database.getProperty("URL", "");
        if (!original.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5(?:\\?[^\\r\\n]*)?")) { throw new IllegalArgumentException("OWN101 source JDBC is not exact loopback origin; values withheld."); }
        final String url = original.replaceFirst("/l2jmobiush5(?=\\?|$)", "/information_schema");
        Class.forName(database.getProperty("Driver"));
        try (var connection = DriverManager.getConnection(url, database.getProperty("Login"), database.getProperty("Password")))
        {
            connection.setReadOnly(true);
            if (!"information_schema".equals(connection.getCatalog())) { throw new IllegalStateException("OWN101 metadata catalog mismatch."); }
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
            if (!select || !process || !showDatabase) { throw new IllegalStateException("OWN101_INCOMPLETE_METADATA_VISIBILITY; no DDL/DML."); }
            if (args[0].equals("postflight") || args[0].equals("repaircheck") || args[0].equals("continuation") || args[0].equals("inventorycheck") || args[0].equals("stackproof"))
            {
                postflight(connection, stage, owner);
                if (args[0].equals("inventorycheck") || args[0].equals("stackproof")) { inventoryCheck(connection); compareSnapshot(connection, stage); proveStackMerge(connection, stage, args[0].equals("stackproof")); }
                if (args[0].equals("continuation")) { continuation(connection, stage); }
                if (args[0].equals("repaircheck"))
                {
                    try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM phantom_profile_components WHERE component_type LIKE 'merchant.craft.qualified.v1.%' OR component_type LIKE 'ordinary.craft.recipe.v1.%'"))
                    { rows.next(); if (rows.getLong(1) != 0) { throw new IllegalStateException("OWN101_UNCERTAIN_GRANT_NO_REPAIR"); } }
                }
                return;
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + SCHEMA + "'"))
            {
                rows.next(); if (rows.getLong(1) != 0) { throw new IllegalStateException("OWN101_SCHEMA_ALREADY_EXISTS; no adopt/reset/drop."); }
            }
            long sessions;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID()")) { rows.next(); sessions = rows.getLong(1); }
            long schemas;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.SCHEMATA")) { rows.next(); schemas = rows.getLong(1); }
            System.out.println("OWN101_METADATA_PASS absent=true fullVisibility=true schemas=" + schemas + ";foreignSessions=" + sessions + ";grantsInspected=" + grants + ";DDL_DML=0;World=0");
            if (!args[0].equals("preflight"))
            {
                if (!create || sessions != 0 || Files.exists(stage.resolve("private/db.created"))) { throw new IllegalStateException("OWN101_CREATE_AUTHORITY_OR_QUIESCENCE; no DDL/DML."); }
                bootstrap(connection, stage, owner, args[2], args[0].equals("plan"));
            }
        }
        catch (java.sql.SQLException failure)
        {
            // Drivers may include credentials/URLs in exception text. Only structured codes leave this boundary.
            throw new IllegalStateException("OWN101_METADATA_SQL_FAILURE state=" + failure.getSQLState() + ";code=" + failure.getErrorCode());
        }
    }
    private static void proveStackMerge(java.sql.Connection connection, Path stage, boolean writeProof) throws Exception
    {
        final var prefix = new java.util.ArrayList<String[]>(); final var items = new java.util.ArrayList<String[]>();
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT profile_id,component_type,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type"))
        { while (rows.next()) { prefix.add(new String[] {rows.getString(1), rows.getString(2), rows.getString(3)}); } }
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT owner_id,object_id,item_id,count,loc FROM items WHERE owner_id IN(SELECT charId FROM characters WHERE account_name LIKE 'phm_%') ORDER BY object_id"))
        { while (rows.next()) { items.add(new String[] {rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5)}); } }
        final String wanted = read(stage.resolve("private/receipts/continuation2.properties")).getProperty("durableHash");
        final var merged = items.stream().filter(row -> row[2].equals("57") && row[3].equals("102340")).findFirst().orElseThrow();
        final int maximum;
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT MAX(object_id) FROM items")) { rows.next(); maximum = rows.getInt(1); }
        final var used = items.stream().map(row -> Integer.parseInt(row[1])).collect(java.util.stream.Collectors.toSet());
        for (int candidate = maximum - 2048; candidate <= maximum + 32; candidate++)
        {
            if (used.contains(candidate)) { continue; }
            for (boolean reverse : new boolean[] {false, true})
            {
                final var reconstructed = new java.util.ArrayList<String[]>(items.size() + 1);
                for (var row : items) { final String[] copy = row.clone(); if (row == merged) { copy[3] = reverse ? "2340" : "100000"; } reconstructed.add(copy); }
                reconstructed.add(new String[] {merged[0], Integer.toString(candidate), "57", reverse ? "100000" : "2340", "INVENTORY"});
                reconstructed.sort(java.util.Comparator.comparingInt(row -> Integer.parseInt(row[1])));
                final var digest = MessageDigest.getInstance("SHA-256");
                for (var collection : java.util.List.of(prefix, reconstructed)) { for (var row : collection) { for (var value : row) { digest.update((value.length() + ":" + value + "\n").getBytes(StandardCharsets.UTF_8)); } } }
                if (HexFormat.of().formatHex(digest.digest()).equals(wanted))
                {
                    System.out.println("OWN101_EXACT_OLD_HASH_RECONSTRUCTED nativeStackMerge=true;owner=" + merged[0] + ";retainedObject=" + merged[1] + ";mergedObject=" + candidate + ";Adena=100000+2340->102340;otherRows=IDENTICAL");
                    if (writeProof)
                    {
                        final var canonical = MessageDigest.getInstance("SHA-256");
                        for (String sql : new String[] {"SELECT profile_id,component_type,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type", "SELECT owner_id,item_id,SUM(count),loc FROM items WHERE owner_id IN(SELECT charId FROM characters WHERE account_name LIKE 'phm_%') GROUP BY owner_id,item_id,loc ORDER BY owner_id,item_id,loc"})
                        { try (var query = connection.createStatement(); var rows = query.executeQuery(sql)) { while (rows.next()) { for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) { final String value = rows.getString(i); canonical.update((value.length() + ":" + value + "\n").getBytes(StandardCharsets.UTF_8)); } } } }
                        Files.writeString(stage.resolve("private/receipts/stack-merge-proof.properties"), "source=EXACT_OLD_SHA_RECONSTRUCTED\noldRawHash=" + wanted + "\ncanonicalHash=" + HexFormat.of().formatHex(canonical.digest()) + "\njarSha256=" + hash(stage.resolve("L2J_Mobius_CT_2.6_HighFive/dist/libs/GameServer.jar")) + "\nmergedObject=" + candidate + "\nretainedObject=" + merged[1] + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                    }
                    return;
                }
            }
        }
        System.out.println("OWN101_STACK_MERGE_HYPOTHESIS=NOT_PROVEN;rawHashMismatch=OPEN");
        if (writeProof) { throw new IllegalStateException("OWN101_UNPROVEN_STACK_MERGE_STOP"); }
    }
    private static void compareSnapshot(java.sql.Connection connection, Path stage) throws Exception
    {
        final var digest = MessageDigest.getInstance("SHA-256");
        for (String sql : new String[] {"SELECT profile_id,component_type,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type", "SELECT owner_id,object_id,item_id,count,loc FROM items WHERE owner_id IN (SELECT charId FROM characters WHERE account_name LIKE 'phm_%') ORDER BY object_id"})
        { try (var query = connection.createStatement(); var rows = query.executeQuery(sql)) { while (rows.next()) { for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) { final String value = rows.getString(i); digest.update((value.length() + ":" + value + "\n").getBytes(StandardCharsets.UTF_8)); } } } }
        final String actual = HexFormat.of().formatHex(digest.digest());
        final var prior = read(stage.resolve("private/receipts/continuation2.properties"));
        System.out.println("OWN101_SNAPSHOT_COMPARE expected=" + prior.getProperty("durableHash") + ";current=" + actual);
    }
    private static void inventoryCheck(java.sql.Connection connection) throws Exception
    {
        final var roles = new java.util.TreeMap<Integer, org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Role>();
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT payload FROM phantom_profile_components WHERE component_type LIKE 'merchant.owner.%' ORDER BY component_type"))
        { while (rows.next()) { final var role = org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.decodeRole(rows.getBytes(1)); roles.put(role.ordinal(), role); } }
        final int maker = roles.values().stream().filter(role -> role.storeKind() == 2).findFirst().orElseThrow().characterId();
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT loc,COUNT(*),SUM(count),SUM(count=0) FROM items WHERE owner_id IN(SELECT charId FROM characters WHERE account_name LIKE 'phm_%') GROUP BY loc"))
        { while (rows.next()) { System.out.println("OWN101_ITEM_STRUCTURE loc=" + rows.getString(1) + ";rows=" + rows.getLong(2) + ";count=" + rows.getLong(3) + ";zeroRows=" + rows.getLong(4)); } }
        int checked = 0;
        for (var role : roles.values())
        {
            final var expected = new java.util.TreeMap<Integer, Long>(role.stock()); expected.merge(57, role.capital() + (role.characterId() == maker ? 2340 : 0), Long::sum);
            final var actual = new java.util.TreeMap<Integer, Long>();
            try (var query = connection.prepareStatement("SELECT item_id,SUM(count) FROM items WHERE owner_id=? AND loc='INVENTORY' GROUP BY item_id"))
            { query.setInt(1, role.characterId()); try (var rows = query.executeQuery()) { while (rows.next()) { if (rows.getLong(2) != 0) { actual.put(rows.getInt(1), rows.getLong(2)); } } } }
            if (!actual.equals(expected)) { System.out.println("OWN101_INVENTORY_DELTA ordinal=" + role.ordinal() + ";expected=" + expected + ";actual=" + actual); throw new IllegalStateException("OWN101_INVENTORY_CONSERVATION_REQUIRES_STOP"); }
            checked++;
        }
        System.out.println("OWN101_PER_OWNER_BIRTH_STOCK_PLUS_PAID_FEE_PASS owners=" + checked + ";newCapital=0;newStock=0");
    }
    private static void continuation(java.sql.Connection connection, Path stage) throws Exception
    {
        final Path receipts = stage.resolve("private/receipts");
        final String observed = Files.readString(receipts.resolve("start1-replay.tsv"), StandardCharsets.UTF_8);
        final String red = Files.readString(stage.resolve("private/preimage-rare/native-red.log"), StandardCharsets.UTF_8);
        if (!observed.contains("nativeOwners=200, nonemptyStores=200") || !observed.contains("MANUFACTURE=20") || !observed.contains("drained=true;remainingOwners=0") || !red.contains("Event[type=RARE_PRODUCT, recipeListId=58") || !red.contains("feeTransferred=2340") || !red.contains("ItemDelta[itemId=916, count=2]")) { throw new IllegalStateException("OWN101_RARE_CAUSE_NOT_PROVEN"); }
        final var roles = new java.util.TreeMap<Integer, org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Role>();
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT payload FROM phantom_profile_components WHERE component_type LIKE 'merchant.owner.%' ORDER BY component_type"))
        { while (rows.next()) { final var role = org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.decodeRole(rows.getBytes(1)); if (roles.put(role.ordinal(), role) != null) { throw new IllegalStateException("OWN101_DUPLICATE_ROLE"); } } }
        if (roles.size() != 200) { throw new IllegalStateException("OWN101_NOT_200_DURABLE_ROLES"); }
        final var maker = roles.values().stream().filter(role -> role.storeKind() == 2).findFirst().orElseThrow();
        int customer;
        try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT charId FROM characters WHERE account_name='fixture101' AND char_name='Customer101'"))
        { if (!rows.next()) { throw new IllegalStateException("OWN101_ORIGINAL_CUSTOMER_MISSING"); } customer = rows.getInt(1); if (rows.next()) { throw new IllegalStateException("OWN101_AMBIGUOUS_CUSTOMER"); } }
        for (var expected : java.util.Map.of(maker.characterId(), maker.capital() + 2340, customer, 100000000L - 2340).entrySet())
        {
            try (var query = connection.prepareStatement("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=57 AND loc='INVENTORY'"))
            { query.setInt(1, expected.getKey()); try (var rows = query.executeQuery()) { rows.next(); if (rows.getLong(1) != expected.getValue()) { throw new IllegalStateException("OWN101_RARE_FEE_READBACK_DIFFERS"); } } }
        }
        try (var query = connection.prepareStatement("SELECT item_id,COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND loc='INVENTORY' AND item_id IN(916,1913,1892,1889,1887,1459,2131) GROUP BY item_id"))
        { query.setInt(1, customer); boolean product = false; try (var rows = query.executeQuery()) { while (rows.next()) { if (rows.getInt(1) == 916 && rows.getLong(2) == 2) { product = true; } else if (rows.getLong(2) != 0) { throw new IllegalStateException("OWN101_RARE_BOM_READBACK_DIFFERS"); } } } if (!product) { throw new IllegalStateException("OWN101_RARE_PRODUCT_READBACK_MISSING"); } }
        final var digest = MessageDigest.getInstance("SHA-256");
        for (String sql : new String[] {"SELECT profile_id,component_type,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type", "SELECT owner_id,object_id,item_id,count,loc FROM items WHERE owner_id IN (SELECT charId FROM characters WHERE account_name LIKE 'phm_%') ORDER BY object_id"})
        { try (var query = connection.createStatement(); var rows = query.executeQuery(sql)) { while (rows.next()) { for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) { final String value = rows.getString(i); digest.update((value.length() + ":" + value + "\n").getBytes(StandardCharsets.UTF_8)); } } } }
        final String facts = "source=RECOVERED_PARTIAL_START1\nids=" + roles.values().stream().map(role -> role.characterId()).toList() + "\ndurableHash=" + HexFormat.of().formatHex(digest.digest()) + "\njarSha256=" + hash(stage.resolve("L2J_Mobius_CT_2.6_HighFive/dist/libs/GameServer.jar")) + "\npaidCraft=RARE_PRODUCT916x2;fee2340;MP147;BOM_CONSUMED;SQL_CONFIRMED\n";
        Files.writeString(receipts.resolve("continuation2.properties"), facts, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        System.out.println("OWN101_RARE_PAID_CRAFT_SQL_CONFIRMED sameDB=true newGrants=0 nativeStart1=PARTIAL;plannedRestart=REQUIRED");
    }
    private static void postflight(java.sql.Connection connection, Path stage, Properties owner) throws Exception
    {
        if (!Files.readString(stage.resolve("private/db.created"), StandardCharsets.UTF_8).startsWith(owner.getProperty("installationId") + "\n")) { throw new IllegalStateException("OWN101_CREATED_ORIGIN_MISSING"); }
        connection.setCatalog(SCHEMA);
        if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalStateException("OWN101_POST_CATALOG"); }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT installation_id,source_head,inputs_sha256 FROM own101_authority"))
        {
            if (!rows.next() || !owner.getProperty("installationId").equals(rows.getString(1)) || !owner.getProperty("sourceHead").equals(rows.getString(2)) || !owner.getProperty("inputsSha256").equals(rows.getString(3)) || rows.next()) { throw new IllegalStateException("OWN101_POST_AUTHORITY"); }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=DATABASE() AND ID<>CONNECTION_ID()"))
        { rows.next(); if (rows.getLong(1) != 0) { throw new IllegalStateException("OWN101_POST_SESSIONS_REMAIN"); } }
        for (String table : new String[] {"characters", "phantom_profiles", "phantom_profile_components", "character_offline_trade", "character_offline_trade_items"})
        {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { rows.next(); System.out.println("OWN101_POST_ROWS " + table + "=" + rows.getLong(1)); }
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM phantom_profile_components WHERE component_type LIKE 'merchant.craft.qualified.v1.%'"))
        { rows.next(); System.out.println("OWN101_CRAFT_RECEIPTS=" + rows.getLong(1)); }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT classid,base_class,level,COUNT(*) FROM characters WHERE account_name LIKE 'phm_%' GROUP BY classid,base_class,level ORDER BY classid,level"))
        { while (rows.next()) { System.out.println("OWN101_NATIVE_CLASSES class=" + rows.getInt(1) + ";base=" + rows.getInt(2) + ";level=" + rows.getInt(3) + ";count=" + rows.getLong(4)); } }
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
            System.out.println("OWN101_POST_HASH " + slice[0] + "=" + HexFormat.of().formatHex(digest.digest()));
        }
        System.out.println("OWN101_POSTFLIGHT_PASS ownSessions=0;DDL_DML=0;World=0");
    }
    private static void bootstrap(java.sql.Connection connection, Path stage, Properties owner, String uuid, boolean dryRun) throws Exception
    {
        final Path module = stage.resolve("L2J_Mobius_CT_2.6_HighFive");
        final var pins = new java.util.HashMap<String, String>();
        for (String line : Files.readAllLines(stage.resolve("inputs.tsv"), StandardCharsets.UTF_8))
        {
            final String[] fields = line.split("\\t");
            if (fields.length != 2 || pins.put(fields[0], fields[1]) != null) { throw new IllegalStateException("OWN101_SQL_INVENTORY_AMBIGUOUS"); }
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
                    if (!digest.equals(pins.get(relative))) { throw new IllegalStateException("OWN101_STOCK_SQL_DRIFT"); }
                    manifest.append(relative).append('\t').append(digest).append('\n'); files++;
                    for (String sql : splitSql(Files.readString(path, StandardCharsets.UTF_8)))
                    {
                        if (sql.matches("(?is)DROP TABLE IF EXISTS\\s+`?[a-zA-Z0-9_]+`?")) { omittedDrop++; continue; }
                        if (!sql.matches("(?is)(?:CREATE TABLE(?: IF NOT EXISTS)?|CREATE(?: UNIQUE)? INDEX|INSERT(?: IGNORE)? INTO|REPLACE INTO|ALTER TABLE|SET)\\s+.*")
                            || sql.matches("(?is).*\\b(?:GRANT|REVOKE|CREATE DATABASE|DROP DATABASE|TRUNCATE|USE)\\s+.*")) { throw new IllegalStateException("OWN101_UNAPPROVED_STOCK_SQL file=" + relative); }
                        statements.add(sql);
                    }
                }
            }
        }
        if (files != 121) { throw new IllegalStateException("OWN101_STOCK_SQL_COUNT"); }
        if (dryRun) { System.out.println("OWN101_SQL_PLAN_PASS files=" + files + ";statements=" + statements.size() + ";omittedStockDrop=" + omittedDrop + ";DDL_DML=0"); return; }
        Files.writeString(stage.resolve("private/sql-manifest.tsv"), manifest, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        connection.setReadOnly(false);
        try (var statement = connection.createStatement()) { statement.executeUpdate("CREATE DATABASE `" + SCHEMA + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"); }
        Files.writeString(stage.resolve("private/db.created"), uuid + "\n" + Instant.now(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
        connection.setCatalog(SCHEMA);
        if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalStateException("OWN101_CREATE_CATALOG"); }
        for (String sql : statements) { try (var statement = connection.createStatement()) { statement.execute(sql); } }
        try (var statement = connection.createStatement()) { statement.executeUpdate("CREATE TABLE own101_authority (installation_id CHAR(36) PRIMARY KEY, source_head CHAR(40) NOT NULL, inputs_sha256 CHAR(64) NOT NULL)"); }
        try (var statement = connection.prepareStatement("INSERT INTO own101_authority VALUES(?,?,?)"))
        {
            statement.setString(1, uuid); statement.setString(2, owner.getProperty("sourceHead")); statement.setString(3, owner.getProperty("inputsSha256")); statement.executeUpdate();
        }
        System.out.println("OWN101_CREATED stockFiles=" + files + ";statements=" + statements.size() + ";omittedStockDrop=" + omittedDrop + ";foreignWrites=0;World=0");
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
        if (quote != 0 || block || !sql.toString().isBlank()) { throw new IllegalArgumentException("OWN101_UNTERMINATED_STOCK_SQL"); }
        return result;
    }
    private static void physical(Path file) throws Exception
    {
        if (!file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.isSymbolicLink(file)) { throw new IllegalArgumentException("OWN101 physical path required."); }
        for (Path part = file; part != null; part = part.getParent())
        {
            final var attributes = Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther()) { throw new IllegalArgumentException("OWN101 reparse path forbidden."); }
        }
    }
    private static Properties read(Path file) throws Exception
    {
        physical(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 32768) { throw new IllegalArgumentException("OWN101 bounded input required."); }
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

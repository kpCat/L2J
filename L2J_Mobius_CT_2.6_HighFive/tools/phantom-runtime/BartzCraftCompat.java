/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Role;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Stage;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeCraftRecipes.Receipt;

/** Standalone operator tool, compiled separately from the server. */
public final class BartzCraftCompat
{
    private static final String ARCHIVE_JAR = "3b9a99555fa8b0d14c2df2427f8e28b3d8129f62ab6c89873fd79c99dfd90bb4";
    private static final String ARCHIVE_SQL = "07d00b98ee233acca70b39291d9e9c599ec574741ad9ac8332c4934b7efa08bc";
    private static final int STARTUP_LOG_BYTES = 29289;
    private static final String STARTUP_LOG_SHA = "380fe00ea2648b5e1593585468209dbb49f07fba2e60bdea034c6e77f5a61e84";
    private static final String SOURCE_INPUTS_SHA = "0676e27a911658021d2a97825dcd79aa8f652c79b8654f882c3cdea129502c59";
    private static final List<String> SOURCE_INPUTS = List.of("config/General.ini", "config/Player.ini", "config/Rates.ini",
        "config/Custom/PhantomMarket.ini", "config/Custom/PhantomMerchantCrafting.ini", "config/Custom/PhantomMerchants.ini",
        "config/Custom/PhantomPlayers.ini", "data/Recipes.xml", "data/EnchantItemData.xml", "data/EnchantItemGroups.xml",
        "data/Seeds.xml", "data/Doors.xml", "data/MerchantPriceConfig.xml", "data/buylists", "data/multisell", "data/teleporters",
        "data/stats/items", "data/stats/npcs", "data/stats/skills", "data/stats/players", "data/spawns", "data/mapregion",
        "data/phantoms/knowledge", "data/phantoms/topology", "data/xsd");
    private static final String LEDGER = "merchant.server.v1";
    private static final String PREFIX = "merchant.craft.qualified.v1.";
    private static final Set<Integer> ORDINALS = Set.of(9, 17, 25, 36, 44, 63, 71, 82, 90, 98,
        109, 117, 125, 136, 144, 163, 171, 182, 190, 198);
    private static final Pattern DUMP = Pattern.compile("\\((\\d+),'(merchant\\.(?:owner\\.\\d{4}|craft\\.qualified\\.v1\\.\\d+|server\\.v1))',(\\d+),(\\d+),0x([0-9a-fA-F]+),");
    private static final Pattern ADENA = Pattern.compile("\\((\\d+),\\d+,57,(\\d+),\\d+,'INVENTORY',");
    private static final Pattern OFFER = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+)\\)");
    private static final Pattern DATA_FILE = Pattern.compile("(?i).+\\.(xml|xsd|ini)");
    private static final Pattern FAILURE = Pattern.compile("Merchant restore held ordinal=(\\d+)\\Rjava\\.lang\\.IllegalStateException: INCONSISTENT native craft owner/source; grant will not replay\\.\\R\\s+at org\\.l2jmobius\\.gameserver\\.phantoms\\.progression\\.PhantomNativeCraftRecipes\\.verify\\(");
    private static final Pattern RESTORE = Pattern.compile("Merchant restore held ordinal=(\\d+)");
    private static final String COMPONENT = "SELECT component_schema_version,row_version,payload,created_at,updated_at"
        + " FROM phantom_profile_components WHERE profile_id=? AND component_type=?";

    record Archived(long profile, int schema, long version, byte[] payload)
    {
        boolean matches(PhantomProfileComponent row, int expectedSchema, long increments, byte[] expectedPayload)
        {
            return row.profileId() == profile && row.componentSchemaVersion() == expectedSchema
                && row.rowVersion() == version + increments && Arrays.equals(row.payload(), expectedPayload);
        }
    }

    record Offer(int recipe, long count, long price) { }
    record Target(Role role, Archived archivedRole, Receipt receipt, Archived archivedReceipt, Receipt stable, long adena, Set<Offer> offers) { }
    record Evidence(Archived ledger, List<Target> targets) { }
    record Plan(List<PhantomProfileComponent> guards, List<ComponentMutation> mutations) { }
    record Database(String schema, String pipe, Path datadir) { }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 6 || !Set.of("dry-run", "apply-fixture", "apply-offline-bartz").contains(args[0]))
        {
            throw new IllegalArgumentException("Expected dry-run, apply-fixture or apply-offline-bartz, then archive JAR, archive SQL, startup log, SQL datadir, JDBC options file.");
        }
        final boolean live = args[0].equals("apply-offline-bartz");
        if (live) { requireGameServerAbsent(); }
        final var database = openOptions(Path.of(args[5]), Path.of(args[4]), live);
        final var evidence = evidence(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        DatabaseFactory.initFromConfig(args[5]);
        try
        {
            final var plan = preflight(database, evidence, true);
            if (plan.mutations().isEmpty()) { System.out.println("NOOP 20/20"); return; }
            if (args[0].equals("dry-run")) { System.out.println("DRY_RUN PASS 20/20"); return; }
            if (live) { requireGameServerAbsent(); }
            apply(database, evidence, plan);
            if (!preflight(database, evidence, false).mutations().isEmpty()) { throw new IllegalStateException("Committed postimage not visible."); }
            System.out.println("COMMITTED 20/20 receipt+role; native grants=0");
        }
        finally { DatabaseFactory.close(); }
    }

    static Evidence evidence(Path jar, Path sql, Path startupLog) throws Exception
    {
        require(PhantomMerchantCraftReceipt.digest(Files.readAllBytes(jar)).equals(ARCHIVE_JAR), "Archived issuing JAR SHA-256 differs.");
        final var sqlBytes = Files.readAllBytes(sql);
        require(PhantomMerchantCraftReceipt.digest(sqlBytes).equals(ARCHIVE_SQL), "Archived SQL SHA-256 differs.");
        final byte[] logPrefix;
        try (var input = Files.newInputStream(startupLog)) { logPrefix = input.readNBytes(STARTUP_LOG_BYTES); }
        require(PhantomMerchantCraftReceipt.digest(logPrefix).equals(STARTUP_LOG_SHA), "Attested startup log prefix SHA-256 differs.");
        final var log = new String(logPrefix, StandardCharsets.UTF_8);
        final var failures = FAILURE.matcher(log).results().map(m -> Integer.parseInt(m.group(1))).toList();
        require(failures.size() == ORDINALS.size() && Set.copyOf(failures).equals(ORDINALS)
            && RESTORE.matcher(log).results().filter(m -> ORDINALS.contains(Integer.parseInt(m.group(1)))).count() == ORDINALS.size(),
            "Startup log does not isolate the same twenty craft verification failures.");
        final var nativeCode = oldNativeCode(jar);
        checkSourceInputs();
        final var components = new HashMap<String, Archived>();
        final var sqlText = new String(sqlBytes, StandardCharsets.UTF_8);
        sqlText.lines().filter(line -> line.startsWith("INSERT INTO `phantom_profile_components`"))
            .forEach(line -> DUMP.matcher(line).results().forEach(m ->
            {
                final var entry = new Archived(Long.parseLong(m.group(1)), Integer.parseInt(m.group(3)),
                    Long.parseLong(m.group(4)), HexFormat.of().parseHex(m.group(5)));
                require(components.putIfAbsent(m.group(2), entry) == null, "Duplicate archived merchant component.");
            }));
        final var balances = new HashMap<Integer, Long>();
        sqlText.lines().filter(line -> line.startsWith("INSERT INTO `items`"))
            .forEach(line -> ADENA.matcher(line).results().forEach(m -> balances.merge(Integer.parseInt(m.group(1)), Long.parseLong(m.group(2)), Math::addExact)));
        final var shops = new HashMap<Integer, Set<Offer>>();
        sqlText.lines().filter(line -> line.startsWith("INSERT INTO `character_offline_trade_items`"))
            .forEach(line -> OFFER.matcher(line).results().forEach(m ->
            {
                final var offers = shops.computeIfAbsent(Integer.parseInt(m.group(1)), _ -> new HashSet<>());
                require(offers.add(new Offer(Integer.parseInt(m.group(2)), Long.parseLong(m.group(3)), Long.parseLong(m.group(4)))),
                    "Duplicate archived native manufacture offer.");
            }));
        final var ledger = components.get(LEDGER);
        require(ledger != null && ledger.schema() == 1 && PhantomMerchantBootstrap.decodeLedger(ledger.payload()).nextOrdinal() == 200,
            "Archived merchant ledger differs.");
        require(components.keySet().stream().filter(key -> key.startsWith(PREFIX)).count() == ORDINALS.size(), "Archived receipt count differs.");
        final var targets = new ArrayList<Target>();
        final var characters = new HashSet<Integer>();
        for (int ordinal : new TreeSet<>(ORDINALS))
        {
            final String roleKey = PhantomMerchantBootstrap.ROLE_PREFIX + String.format(java.util.Locale.ROOT, "%04d", ordinal);
            final String receiptKey = PREFIX + ordinal;
            final var roleRow = components.get(roleKey);
            final var receiptRow = components.get(receiptKey);
            require(roleRow != null && receiptRow != null && roleRow.profile() == ledger.profile() && receiptRow.profile() == ledger.profile()
                && roleRow.schema() == 1 && receiptRow.schema() == 1, "Archived scope differs at ordinal " + ordinal);
            final var role = PhantomMerchantBootstrap.decodeRole(roleRow.payload());
            final var receipt = PhantomMerchantCraftReceipt.decode(new PhantomProfileComponent(ledger.profile(), receiptKey, 1,
                receiptRow.version(), receiptRow.payload(), java.time.Instant.EPOCH, java.time.Instant.EPOCH));
            require(role.ordinal() == ordinal && role.stage() == Stage.READY && role.classId() == 53 && role.storeKind() == 2
                && role.account().startsWith("phm_") && role.characterId() == receipt.objectId() && role.account().equals(receipt.account())
                && characters.add(role.characterId()) && Arrays.equals(roleRow.payload(), PhantomMerchantBootstrap.encode(role))
                && receipt.state().equals("READY") && receipt.recipes().size() == 6, "Archived identity differs at ordinal " + ordinal);
            require(PhantomMerchantCraftReceipt.legacyFingerprint(receipt, nativeCode).equals(receipt.fingerprint()),
                "Exact legacy hash differs at ordinal " + ordinal);
            final var stable = PhantomMerchantCraftReceipt.withFingerprint(receipt, PhantomMerchantCraftReceipt.fingerprint(receipt));
            final var offers = shops.getOrDefault(role.characterId(), Set.of());
            require(offers.size() == receipt.recipes().size() && offers.stream().allMatch(offer ->
                offer.count() == 0 && offer.price() > 0 && receipt.recipes().contains(offer.recipe()))
                && offers.stream().map(Offer::recipe).distinct().count() == offers.size(), "Archived priced craft offers differ at ordinal " + ordinal);
            targets.add(new Target(role, roleRow, receipt, receiptRow, stable, balances.getOrDefault(role.characterId(), 0L), Set.copyOf(offers)));
        }
        return new Evidence(ledger, List.copyOf(targets));
    }

    private static String oldNativeCode(Path jar) throws Exception
    {
        final var hash = MessageDigest.getInstance("SHA-256");
        try (var archive = new ZipFile(jar.toFile()))
        {
            for (String name : List.of("model/actor/Player", "data/xml/RecipeData", "data/xml/SkillTreeData", "phantoms/progression/PhantomNativeCraftRecipes"))
            {
                try (var entry = archive.getInputStream(archive.getEntry("org/l2jmobius/gameserver/" + name + ".class")))
                { hash.update(entry.readAllBytes()); }
            }
            // Archived shops were priced by the original authority. Refuse source/code drift instead of reconstructing live NPC quotes offline.
            for (String name : List.of("phantoms/economy/PhantomPrivateMarketPricingAuthority", "phantoms/economy/PhantomEconomyValuation",
                "phantoms/economy/PhantomNpcAcquisitionPrice", "phantoms/economy/PhantomRateAwareLootFairValue", "phantoms/economy/PhantomRecipeExpectedCost",
                "phantoms/commerce/PhantomCommerceCatalogLoader", "phantoms/knowledge/L2jGameKnowledgeBackend", "config/custom/PhantomMarketConfig",
                "data/xml/ItemData", "data/xml/BuyListData", "data/xml/NpcData", "data/MerchantPriceConfigTable"))
            {
                final String path = "org/l2jmobius/gameserver/" + name + ".class";
                try (var old = archive.getInputStream(archive.getEntry(path)); var current = BartzCraftCompat.class.getResourceAsStream('/' + path))
                {
                    require(current != null && Arrays.equals(old.readAllBytes(), current.readAllBytes()), "Pricing authority changed: " + name);
                }
            }
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    private static void checkSourceInputs() throws Exception
    {
        final var files = new TreeSet<String>();
        for (String entry : SOURCE_INPUTS)
        {
            final var path = Path.of(entry);
            if (Files.isRegularFile(path)) { files.add(entry); }
            else
            {
                try (var tree = Files.walk(path))
                {
                    tree.filter(file -> Files.isRegularFile(file) && DATA_FILE.matcher(file.getFileName().toString()).matches())
                        .map(file -> file.toString().replace('\\', '/')).forEach(files::add);
                }
            }
        }
        final var hash = MessageDigest.getInstance("SHA-256");
        for (String file : files)
        {
            final String row = file + '|' + PhantomMerchantCraftReceipt.digest(Files.readAllBytes(Path.of(file))) + '\n';
            hash.update(row.getBytes(StandardCharsets.UTF_8));
        }
        require(HexFormat.of().formatHex(hash.digest()).equals(SOURCE_INPUTS_SHA), "Recipe, skill or archived pricing source inputs changed.");
    }

    static Plan preflight(Database database, Evidence evidence, boolean print) throws SQLException
    {
        try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement())
        {
            statement.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
            try { return readPlan(connection, database, evidence, print); }
            finally { statement.execute("ROLLBACK"); }
        }
    }

    private static Plan readPlan(Connection connection, Database database, Evidence evidence, boolean print) throws SQLException
    {
        checkDatabase(connection, database);
        final long profile = evidence.ledger().profile();
        final var guards = new ArrayList<PhantomProfileComponent>();
        final var mutations = new ArrayList<ComponentMutation>();
        final var ledger = component(connection, profile, LEDGER);
        require(evidence.ledger().matches(ledger, 1, 0, evidence.ledger().payload()), "Merchant ledger bytes/version differ.");
        guards.add(ledger);
        int refused = 0;
        for (var target : evidence.targets())
        {
            final int ordinal = target.role().ordinal();
            try
            {
                final var role = component(connection, profile, target.role().component());
                final var receipt = component(connection, profile, PREFIX + ordinal);
                final var ready = target.archivedRole().payload();
                final var inconsistent = PhantomMerchantBootstrap.encode(target.role().withStage(Stage.INCONSISTENT));
                final var stable = target.stable().encode();
                final boolean pending = target.archivedRole().matches(role, 1, 1, inconsistent)
                    && target.archivedReceipt().matches(receipt, 1, 0, target.archivedReceipt().payload());
                final boolean committed = target.archivedRole().matches(role, 1, 2, ready)
                    && target.archivedReceipt().matches(receipt, PhantomMerchantCraftReceipt.VERSION, 1, stable);
                require(pending || committed, "receipt/role stage, version or exact bytes differ");
                checkNative(connection, target);
                if (pending)
                {
                    guards.add(role);
                    guards.add(receipt);
                    mutations.add(new ComponentMutation(role.componentType(), role.rowVersion(), 1, ready));
                    mutations.add(new ComponentMutation(receipt.componentType(), receipt.rowVersion(), PhantomMerchantCraftReceipt.VERSION, stable));
                }
                if (print)
                {
                    System.out.printf("ordinal=%04d %s reason=v1-class-fingerprint roleVersion=%d receiptVersion=%d old=%s new=%s%n", ordinal,
                        pending ? "PASS" : "NOOP", role.rowVersion(), receipt.rowVersion(), target.receipt().fingerprint(), target.stable().fingerprint());
                }
            }
            catch (IllegalStateException refusedRow)
            {
                refused++;
                if (print) { System.out.printf("ordinal=%04d REFUSE %s%n", ordinal, refusedRow.getMessage()); }
            }
        }
        require(refused == 0, "Native preflight refused " + refused + " role(s).");
        require(mutations.isEmpty() || mutations.size() == 40, "Mixed committed/pending batch; no writes permitted.");
        return new Plan(List.copyOf(guards), mutations.stream().sorted(Comparator.comparing(ComponentMutation::componentType)).toList());
    }

    static void apply(Database database, Evidence evidence, Plan plan)
    {
        final var repository = PhantomProfileRepository.open();
        repository.mutateComponentsAtomically(evidence.ledger().profile(), plan.mutations(), plan.guards(), connection ->
        {
            if (database.schema().equals("l2jmobiush5_phantom_test_131")) { requireGameServerAbsent(); }
            // Under the repository's locked transaction, repeat both the byte checks and the native SQL readback.
            require(readPlan(connection, database, evidence, false).mutations().size() == 40, "CAS preimage changed.");
        });
    }

    static PhantomProfileComponent component(Connection connection, long profile, String name) throws SQLException
    {
        try (var query = connection.prepareStatement(COMPONENT))
        {
            query.setLong(1, profile);
            query.setString(2, name);
            try (var row = query.executeQuery())
            {
                require(row.next(), "required merchant component is missing");
                return new PhantomProfileComponent(profile, name, row.getInt(1), row.getLong(2), row.getBytes(3),
                    row.getTimestamp(4).toInstant(), row.getTimestamp(5).toInstant());
            }
        }
    }

    private static void checkNative(Connection connection, Target target) throws SQLException
    {
        final var role = target.role();
        final var receipt = target.receipt();
        final int character = role.characterId();
        final String identity = "SELECT c.charId,c.account_name,c.char_name,c.classid,c.base_class,c.level,c.accesslevel,COALESCE(c.clanid,0),a.accessLevel,a.password"
            + " FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.account_name=?";
        try (var query = connection.prepareStatement(identity))
        {
            query.setString(1, role.account());
            try (var row = query.executeQuery())
            {
                require(row.next() && row.getInt(1) == character && role.account().equals(row.getString(2)) && role.name().equals(row.getString(3))
                    && row.getInt(4) == receipt.classId() && row.getInt(5) == receipt.classId() && row.getInt(6) >= receipt.level()
                    && row.getInt(7) == 0 && row.getInt(8) == 0 && row.getInt(9) == -1 && role.token().equals(row.getString(10)) && !row.next(),
                    "characters/accounts identity or qualification differs");
            }
        }
        final var skills = new HashMap<Integer, Integer>();
        rows(connection, "SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=0 AND skill_id IN (172,1320)", character,
            row -> skills.put(row.getInt(1), row.getInt(2)));
        require(skills.getOrDefault(172, 0) >= receipt.dwarvenRank() && skills.getOrDefault(1320, 0) >= receipt.commonRank(), "persisted crafting skill missing");
        final var learned = new HashSet<Integer>();
        rows(connection, "SELECT id,type FROM character_recipebook WHERE charId=? AND classIndex=0", character, row ->
        { require(row.getInt(2) == 1, "recipebook contains a non-dwarven recipe"); learned.add(row.getInt(1)); });
        require(learned.equals(Set.copyOf(receipt.recipes())), "persisted recipebook differs");

        final var types = new ArrayList<Integer>();
        rows(connection, "SELECT type FROM character_offline_trade WHERE charId=?", character, row -> types.add(row.getInt(1)));
        require(types.equals(List.of(5)), "native manufacture shop missing");
        final var offers = new HashSet<Offer>();
        rows(connection, "SELECT item,count,price FROM character_offline_trade_items WHERE charId=?", character, row ->
        { require(offers.add(new Offer(row.getInt(1), row.getLong(2), row.getLong(3))), "duplicate native manufacture offer"); });
        require(offers.equals(target.offers()), "native manufacture offers or attested prices differ");
        rows(connection, "SELECT COALESCE(SUM(`count`),0) FROM items WHERE owner_id=? AND item_id=57 AND loc='INVENTORY'", character,
            row -> require(row.getLong(1) == target.adena(), "native adena differs from attested balance"));

        final String conflicts = "SELECT 1 FROM phantom_profiles WHERE character_object_id=? UNION ALL "
            + "SELECT 1 FROM character_offline_play WHERE charId=? UNION ALL "
            + "SELECT 1 FROM phantom_economy_operations WHERE character_object_id=? UNION ALL "
            + "SELECT 1 FROM phantom_economy_reservations WHERE owner_object_id=?";
        try (var query = connection.prepareStatement(conflicts))
        {
            for (int i = 1; i <= 4; i++) { query.setInt(i, character); }
            try (var row = query.executeQuery()) { require(!row.next(), "conflicting native owner/operation/reservation"); }
        }
    }

    private interface ReadRow { void read(ResultSet row) throws SQLException; }

    private static void rows(Connection connection, String sql, int character, ReadRow read) throws SQLException
    {
        try (var query = connection.prepareStatement(sql))
        {
            query.setInt(1, character);
            try (var rows = query.executeQuery()) { while (rows.next()) { read.read(rows); } }
        }
    }

    static Database openOptions(Path options, Path datadir, boolean live) throws Exception
    {
        final var properties = new java.util.Properties();
        try (var reader = Files.newBufferedReader(options)) { properties.load(reader); }
        final String schema = live ? "l2jmobiush5_phantom_test_131" : "bartz_craft_compat_fixture";
        final String base = "jdbc:mysql://localhost/" + schema
            + "?socketFactory=com.mysql.cj.protocol.NamedPipeSocketFactory&path=%5C%5C.%5Cpipe%5C";
        final var matcher = Pattern.compile(Pattern.quote(base) + "(bartz-craft-compat-[a-f0-9]{8})&sslMode=DISABLED&connectTimeout=5000")
            .matcher(properties.getProperty("URL", ""));
        require(matcher.matches() && "com.mysql.cj.jdbc.Driver".equals(properties.getProperty("Driver"))
            && "False".equalsIgnoreCase(properties.getProperty("TestDatabaseConnections", "False")), "Only the dedicated local named-pipe JDBC endpoint is allowed.");
        return new Database(schema, matcher.group(1), datadir.toRealPath());
    }

    static void checkDatabase(Connection connection, Database database) throws SQLException
    {
        try (var statement = connection.createStatement();
            var row = statement.executeQuery("SELECT DATABASE(),@@skip_networking,@@socket,@@datadir,VERSION()"))
        {
            require(row.next() && database.schema().equals(row.getString(1)) && row.getBoolean(2) && database.pipe().equals(row.getString(3))
                && database.datadir().equals(Path.of(row.getString(4)).toAbsolutePath().normalize()) && row.getString(5).contains("MariaDB"),
                "SQL identity/datadir differs or networking is enabled.");
        }
        final String tables = "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND ENGINE='InnoDB'"
            + " AND TABLE_NAME IN ('phantom_profiles','phantom_profile_components','accounts','characters','character_skills','character_recipebook',"
            + "'character_offline_trade','character_offline_trade_items','character_offline_play','phantom_economy_operations','phantom_economy_reservations','items')";
        try (var statement = connection.createStatement(); var row = statement.executeQuery(tables))
        {
            require(row.next() && row.getInt(1) == 12, "Native readback tables are not all InnoDB.");
        }
        // Information_schema hides triggers unless the current account can inspect them.
        final String triggerPrivilege = "WITH account AS (SELECT CONCAT(QUOTE(SUBSTRING_INDEX(CURRENT_USER(),'@',1)),'@',"
            + "QUOTE(SUBSTRING_INDEX(CURRENT_USER(),'@',-1))) AS grantee) "
            + "SELECT 1 FROM information_schema.USER_PRIVILEGES p JOIN account a USING(grantee) WHERE p.PRIVILEGE_TYPE='TRIGGER' UNION ALL "
            + "SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES p JOIN account a USING(grantee) WHERE p.PRIVILEGE_TYPE='TRIGGER' AND p.TABLE_SCHEMA=DATABASE() UNION ALL "
            + "SELECT 1 FROM information_schema.TABLE_PRIVILEGES p JOIN account a USING(grantee) WHERE p.PRIVILEGE_TYPE='TRIGGER'"
            + " AND p.TABLE_SCHEMA=DATABASE() AND p.TABLE_NAME='phantom_profile_components' LIMIT 1";
        try (var statement = connection.createStatement(); var row = statement.executeQuery(triggerPrivilege))
        {
            require(row.next(), "Component trigger metadata visibility is not proven.");
        }
        try (var statement = connection.createStatement(); var row = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TRIGGERS"
            + " WHERE TRIGGER_SCHEMA=DATABASE() AND EVENT_OBJECT_TABLE='phantom_profile_components'"))
        {
            require(row.next() && row.getInt(1) == 0, "Unexpected component trigger; mutation refused.");
        }
    }

    private static void requireGameServerAbsent()
    {
        require(ProcessHandle.of(18792).filter(ProcessHandle::isAlive).isEmpty(), "Protected game PID is present.");
        try
        {
            final var netstat = new ProcessBuilder("netstat.exe", "-ano", "-p", "TCP").redirectErrorStream(true).start();
            final var output = new String(netstat.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            require(netstat.waitFor() == 0 && !Pattern.compile("(?m)^\\s*TCP\\s+\\S+:7777\\s").matcher(output).find(),
                "GameServer port 7777 is present or TCP inspection failed.");
        }
        catch (java.io.IOException failure) { throw new IllegalStateException("GameServer absence could not be checked.", failure); }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GameServer absence check interrupted.", interrupted);
        }
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) { throw new IllegalStateException(message); }
    }
}

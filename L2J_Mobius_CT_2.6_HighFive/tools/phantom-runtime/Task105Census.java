/* Copyright (c) 2013 L2jMobius */
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;

/** Offline inspection of the two immutable CLI preimages. No connection, World or server initialization. */
public final class Task105Census
{
    public static void main(String[] args) throws Exception
    {
        if (args.length == 3 && args[0].equals("snapshot")) { snapshot(Path.of(args[1]), Path.of(args[2])); return; }
        final boolean deltas = (args.length == 4 || args.length == 5) && args[0].equals("diff");
        final Path worldReceipt = deltas && args.length == 5 ? Path.of(args[4]) : null;
        if (deltas) { args = java.util.Arrays.copyOfRange(args, 1, 4); }
        if (args.length != 3) { throw new IllegalArgumentException("Two captured private preimages and original IDs required; SQL=0."); }
        final var first = read(Path.of(args[0]));
        final var second = read(Path.of(args[1]));
        final var expected = new HashSet<>(Files.readAllLines(Path.of(args[2])));
        check(expected.size() == 5000, "Original ordinary identity count differs");
        final var codec = new PhantomPopulationStateCodec();
        for (var frame : List.of(first, second))
        {
            final var profiles = rows(frame.get("profiles"), 0);
            final var chars = rows(frame.get("characters"), column(frame, "characters", "charId"));
            final int accountColumn = column(frame, "characters", "account_name");
            final var actual = new HashSet<String>();
            final var objectIds = new HashSet<Integer>();
            for (String[] component : frame.get("components"))
            {
                if (!component[1].equals("population.state")) { continue; }
                final var state = codec.decode(HexFormat.of().parseHex(component[4]));
                final var profile = profiles.get(component[0]);
                check(profile != null && profile.length == 2, "Ordinary profile absent");
                final int objectId = Integer.parseInt(profile[1]);
                final var character = chars.get(profile[1]);
                check(state.state() == PhantomPopulationState.State.READY && objectIds.add(objectId) && character != null
                    && state.reservedAccount().equals(character[accountColumn]) && !state.reservedAccount().startsWith("phm_"), "Ordinary native READY identity differs");
                check(actual.add(component[0] + "\t" + objectId + "\t" + state.initializationHash()), "Duplicate initialization identity");
            }
            check(expected.equals(actual), "Original5000 IDs/initialization receipts differ");
        }
        System.out.println("DATA105_PREIMAGE ordinaryREADY=5000;distinct=5000;originalIDsAndInit=EXACT;newProfiles=0;SQL=0;World=NOT_INITIALIZED");
        for (String slice : List.of("profiles", "characters", "inventory", "components", "recipebook", "offliners", "offlineItems", "accountAccess"))
        {
            final int key = slice.equals("characters") ? column(first, "characters", "charId") : slice.equals("inventory") ? column(first, "items", "object_id") : 0;
            if (!deltas && (slice.equals("components") || slice.equals("recipebook") || slice.equals("offlineItems")))
            {
                check(first.get(slice).stream().map(java.util.Arrays::toString).toList().equals(second.get(slice).stream().map(java.util.Arrays::toString).toList()), "Receipt/recipe/offline item snapshot changed: " + slice);
                System.out.println("DATA105_SLICE " + slice + ";rows=" + first.get(slice).size() + ";changed=0");
                continue;
            }
            final var before = keyed(first.get(slice), slice, key);
            final var after = keyed(second.get(slice), slice, key);
            if (!deltas || slice.equals("profiles") || slice.equals("characters") || slice.equals("accountAccess")) { check(before.keySet().equals(after.keySet()), "Preimage membership changed: " + slice); }
            int changed = 0;
            int added = 0, removed = 0;
            for (String id : after.keySet()) { if (!before.containsKey(id)) { added++; } }
            for (String id : before.keySet()) { if (!after.containsKey(id)) { removed++; } }
            final Map<String, Integer> fields = new TreeMap<>();
            final Map<String, Integer> negatives = new TreeMap<>();
            final var exact = new ArrayList<String>();
            long expDelta = 0, spDelta = 0;
            for (var row : before.entrySet())
            {
                final var current = after.get(row.getKey());
                if (current == null) { exact.add("REMOVED\t" + row.getKey()); continue; }
                check(row.getValue().length == current.length, "Preimage column count changed");
                if (java.util.Arrays.equals(row.getValue(), current)) { continue; }
                changed++;
                for (int index = 0; index < current.length; index++)
                {
                    if (row.getValue()[index].equals(current[index])) { continue; }
                    final String name = slice.equals("characters") ? columnName(first, "characters", index) : slice.equals("inventory") ? columnName(first, "items", index) : "column" + index;
                    fields.merge(name, 1, Integer::sum);
                    if (slice.equals("characters") && List.of("exp", "sp", "level").contains(name)
                        && Long.parseLong(current[index]) < Long.parseLong(row.getValue()[index])) { negatives.merge(name, 1, Integer::sum); }
                    exact.add(row.getKey() + "\t" + name + "\t" + row.getValue()[index] + "\t" + current[index]);
                }
                if (slice.equals("components")) { check(Long.parseLong(current[3]) >= Long.parseLong(row.getValue()[3]), "Component row version decreased"); }
                if (slice.equals("characters"))
                {
                    expDelta += Long.parseLong(current[column(first, "characters", "exp")]) - Long.parseLong(row.getValue()[column(first, "characters", "exp")]);
                    spDelta += Long.parseLong(current[column(first, "characters", "sp")]) - Long.parseLong(row.getValue()[column(first, "characters", "sp")]);
                }
            }
            for (String id : after.keySet()) { if (!before.containsKey(id)) { exact.add("ADDED\t" + id + "\t" + java.util.Arrays.toString(after.get(id))); } }
            if (deltas) { Files.write(Path.of(args[1]).resolveSibling("delta105-" + slice + ".private.tsv"), exact.stream().sorted().toList(), java.nio.file.StandardOpenOption.CREATE_NEW); }
            System.out.println("DATA105_SLICE " + slice + ";rows=" + before.size() + ";afterRows=" + after.size() + ";added=" + added + ";removed=" + removed + ";changed=" + changed + ";fields=" + fields
                + (slice.equals("characters") ? ";EXPdelta=" + expDelta + ";SPdelta=" + spDelta + ";negativeRows=" + negatives : ""));
        }
        if (deltas) { merchantEvidence(first, second, worldReceipt, expected); }
        System.out.println("LEASE105=NOT_PROVEN;twoQuiescentSamples=NOT_COMPLETED;processExit=NOT_PROVEN");
    }

    private static Map<String, String[]> keyed(List<String[]> values, String slice, int index)
    {
        final Map<String, String[]> result = new HashMap<>();
        for (var row : values)
        {
            final String key = slice.equals("components") || slice.equals("offlineItems") ? row[0] + "\t" + row[1] : slice.equals("recipebook") ? java.util.Arrays.toString(row) : row[index];
            check(result.put(key, row) == null, "Duplicate physical composite key");
        }
        return result;
    }

    private static void merchantEvidence(Map<String, List<String[]>> first, Map<String, List<String[]>> second, Path worldReceipt, java.util.Set<String> expectedIds) throws Exception
    {
        final var before = keyed(first.get("components"), "components", 0);
        final var after = keyed(second.get("components"), "components", 0);
        final var roles = new HashMap<Integer, org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Role>();
        for (var row : second.get("components"))
        {
            if (!row[1].equals("merchant.server.v1") && !row[1].startsWith("merchant.owner.")) { continue; }
            final var old = before.get(row[0] + "\t" + row[1]);
            check(old != null && old[4].equals(row[4]), "Merchant lifetime quota/birth receipt changed");
            if (row[1].startsWith("merchant.owner."))
            {
                final var role = org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.decodeRole(HexFormat.of().parseHex(row[4]));
                check(role.stage() == org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Stage.READY && roles.put(role.characterId(), role) == null, "Merchant READY identity ambiguous");
            }
        }
        for (var row : first.get("components")) { if (row[1].equals("merchant.server.v1") || row[1].startsWith("merchant.owner.")) { check(after.containsKey(row[0] + "\t" + row[1]), "Merchant receipt removed"); } }
        final var offliners = rows(second.get("offliners"), 0);
        final Map<Integer, Integer> open = new TreeMap<>(), noOfflineRow = new TreeMap<>();
        for (var role : roles.values()) { (offliners.containsKey(Integer.toString(role.characterId())) ? open : noOfflineRow).merge(role.storeKind(), 1, Integer::sum); }
        int mapped = 0;
        if (worldReceipt != null)
        {
            final Map<String, String[]> world = new HashMap<>(), service = new HashMap<>();
            for (String line : Files.readAllLines(worldReceipt))
            {
                final var fields = line.split("\t", -1);
                if (fields[0].equals("WORLD")) { check(world.put(fields[1], fields) == null, "Duplicate World witness"); }
                else if (fields[0].equals("NATIVE_MERCHANT")) { check(service.put(fields[1], fields) == null, "Duplicate service witness"); }
            }
            check(service.size() == roles.size(), "Service witnesses differ from durable roles");
            for (var role : roles.values())
            {
                final String id = Integer.toString(role.characterId());
                final var nativeOwner = service.get(id); final var player = world.get(id);
                check(nativeOwner != null && player != null && player[2].equals(nativeOwner[5]) && role.account().equals(player[3])
                    && Integer.parseInt(nativeOwner[2]) == role.ordinal() && nativeOwner[4].equals("READY") && player[4].contains("ownerKind=PHANTOM"), "Native service/World/role differs");
                final var token = java.util.regex.Pattern.compile("token=([0-9]+)").matcher(player[4]);
                check(token.find() && token.group(1).equals(nativeOwner[3]), "Original service lease token differs");
                mapped++;
            }
        }
        System.out.println("MERCHANT105 durableREADY=" + roles.size() + ";preStopWorldServiceTokenMapped=" + mapped + ";offlineRowsByKind=" + open + ";noOfflineRowByKind=" + noOfflineRow + ";lifetimeQuotaAndBirthPayload=UNCHANGED;idleWorldStoreType=NOT_CAPTURED");
        final var ordinary = new HashSet<Integer>();
        for (String id : expectedIds) { ordinary.add(Integer.parseInt(id.split("\t")[1])); }
        final int owner = column(first, "items", "owner_id"), item = column(first, "items", "item_id"), quantity = column(first, "items", "count");
        final Map<String, Long> quantities = new TreeMap<>();
        for (int cut = 0; cut < 2; cut++)
        {
            for (var row : (cut == 0 ? first : second).get("inventory"))
            {
                final int characterId = Integer.parseInt(row[owner]);
                final String family = ordinary.contains(characterId) ? "ORDINARY" : roles.containsKey(characterId) ? "MERCHANT" : "OTHER";
                quantities.merge(family + ";item=" + row[item], (cut == 0 ? -1 : 1) * Long.parseLong(row[quantity]), Long::sum);
            }
        }
        for (var delta : quantities.entrySet()) { if (delta.getValue() != 0) { System.out.println("ITEM105_DELTA " + delta.getKey() + ";quantity=" + delta.getValue()); } }
        final Map<String, Integer> componentUpdates = new TreeMap<>(), componentAdds = new TreeMap<>();
        for (var entry : after.entrySet())
        {
            final var previous = before.get(entry.getKey());
            if (previous == null) { componentAdds.merge(entry.getValue()[1], 1, Integer::sum); }
            else if (!java.util.Arrays.equals(previous, entry.getValue())) { componentUpdates.merge(entry.getValue()[1], 1, Integer::sum); }
        }
        System.out.println("RECEIPT105_COMPONENTS added=" + componentAdds + ";updated=" + componentUpdates + ";decreasedVersion=0;nativeVsBackgroundEarnedSplit=NOT_PROVEN");
    }

    /** Same six canonical104 slices plus the four whole-schema105 slices used by native STOP. */
    private static void snapshot(Path origin, Path output) throws Exception
    {
        Task104Census.main(new String[] {origin.toString(), output.toString()});
        final var owner = new java.util.Properties();
        try (var reader = Files.newBufferedReader(origin.resolve("private/owner.properties"))) { owner.load(reader); }
        final Path config = Path.of(owner.getProperty("sourceModule")).resolve("dist/game/config/Database.ini");
        final var database = new java.util.Properties();
        try (var reader = Files.newBufferedReader(config)) { database.load(reader); }
        final var checkpoint = new StringBuilder(Files.readString(output.resolve("durable.checkpoint")));
        final var queries = List.of("SELECT * FROM characters ORDER BY charId", "SELECT * FROM items ORDER BY object_id",
            "SELECT profile_id,component_type,component_schema_version,row_version,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type",
            "SELECT * FROM character_recipebook ORDER BY charId,id,classIndex");
        final String url = database.getProperty("URL").replaceFirst("/l2jmobiush5(?=\\?|$)", "/l2jmobiush5_phantom_test_102");
        try (var connection = java.sql.DriverManager.getConnection(url, database.getProperty("Login"), database.getProperty("Password")))
        {
            connection.setReadOnly(true); connection.setAutoCommit(false);
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID()"))
            { check(rows.next() && rows.getLong(1) == 0, "OWN105 snapshot requires full process quiescence"); }
            for (int offset = 0; offset < queries.size(); offset++)
            {
                final int index = offset + 6;
                final var digest = java.security.MessageDigest.getInstance("SHA-256");
                long count = 0;
                try (var writer = Files.newBufferedWriter(output.resolve("slice" + index + ".private.txt"), java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
                    var query = connection.createStatement(); var rows = query.executeQuery(queries.get(offset)))
                {
                    while (rows.next())
                    {
                        check(++count <= 200000, "OWN105 bounded whole-schema snapshot exceeded");
                        for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++)
                        {
                            final String value = rows.getString(column);
                            final String frame = value == null ? "NULL\n" : value.length() + ":" + value + "\n";
                            writer.write(frame); digest.update(frame.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                }
                checkpoint.append(index).append('\t').append(count).append('\t').append(HexFormat.of().formatHex(digest.digest())).append('\n');
            }
            connection.rollback();
        }
        catch (java.sql.SQLException failure) { throw new IllegalStateException("OWN105 snapshot SQL failure state=" + failure.getSQLState() + ";code=" + failure.getErrorCode()); }
        Files.writeString(output.resolve("durable105.checkpoint"), checkpoint, java.nio.file.StandardOpenOption.CREATE_NEW);
        System.out.println("OWN105_SNAPSHOT_PASS slices=10;ordinaryIDs=EXACT5000;SQL_WRITES=0;World=NOT_INITIALIZED");
    }

    private static Map<String, List<String[]>> read(Path path) throws Exception
    {
        check(Files.isRegularFile(path) && Files.size(path) <= 64_000_000, "Bounded captured frame required");
        final Map<String, List<String[]>> result = new HashMap<>();
        List<String[]> current = null;
        for (String line : Files.readAllLines(path))
        {
            if (line.startsWith("\uFEFF")) { line = line.substring(1); }
            if (line.startsWith("SLICE:"))
            {
                current = new ArrayList<>();
                check(result.put(line.substring(6), current) == null, "Duplicate frame slice");
            }
            else { check(current != null, "Missing slice boundary"); current.add(line.split("\t", -1)); }
        }
        for (String key : List.of("profiles", "characters", "inventory", "components", "recipebook", "offliners", "offlineItems", "accountAccess", "columns")) { check(result.containsKey(key), "Incomplete captured frame"); }
        return result;
    }
    private static Map<String, String[]> rows(List<String[]> values, int key)
    {
        final Map<String, String[]> result = new HashMap<>();
        for (var value : values) { check(result.put(value[key], value) == null, "Duplicate physical key"); }
        return result;
    }
    private static int column(Map<String, List<String[]>> frame, String table, String name)
    {
        return frame.get("columns").stream().filter(row -> row[0].equals(table) && row[1].equals(name)).mapToInt(row -> Integer.parseInt(row[2]) - 1).findFirst().orElseThrow();
    }
    private static String columnName(Map<String, List<String[]>> frame, String table, int index)
    {
        return frame.get("columns").stream().filter(row -> row[0].equals(table) && Integer.parseInt(row[2]) == index + 1).map(row -> row[1]).findFirst().orElseThrow();
    }
    private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}

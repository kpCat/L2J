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
            if (slice.equals("components") || slice.equals("recipebook") || slice.equals("offlineItems"))
            {
                check(first.get(slice).stream().map(java.util.Arrays::toString).toList().equals(second.get(slice).stream().map(java.util.Arrays::toString).toList()), "Receipt/recipe/offline item snapshot changed: " + slice);
                System.out.println("DATA105_SLICE " + slice + ";rows=" + first.get(slice).size() + ";changed=0");
                continue;
            }
            final var before = rows(first.get(slice), key);
            final var after = rows(second.get(slice), key);
            check(before.keySet().equals(after.keySet()), "Preimage membership changed: " + slice);
            int changed = 0;
            final Map<String, Integer> fields = new TreeMap<>();
            for (var row : before.entrySet())
            {
                final var current = after.get(row.getKey());
                check(row.getValue().length == current.length, "Preimage column count changed");
                if (java.util.Arrays.equals(row.getValue(), current)) { continue; }
                changed++;
                for (int index = 0; index < current.length; index++)
                {
                    if (row.getValue()[index].equals(current[index])) { continue; }
                    final String name = slice.equals("characters") ? columnName(first, "characters", index) : "column" + index;
                    fields.merge(name, 1, Integer::sum);
                }
            }
            System.out.println("DATA105_SLICE " + slice + ";rows=" + before.size() + ";changed=" + changed + ";fields=" + fields);
        }
        System.out.println("LEASE105=NOT_PROVEN;processLocalInventory=UNAVAILABLE_IN_OLD_JAR;preimageCharacterDrift=UNCERTAINTY_NOT_CLEAN_STOP");
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

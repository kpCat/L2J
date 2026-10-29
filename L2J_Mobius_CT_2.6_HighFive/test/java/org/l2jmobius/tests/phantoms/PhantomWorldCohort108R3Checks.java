/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.tests.phantoms.PhantomPopulation102Native.WorldWitness;

/** Test-only cohort outcome. A failed World gate must not preempt an owned STOP witness. */
public final class PhantomWorldCohort108R3Checks
{
    record WorldResult(boolean pass, int first, int second, int stable, String reason) { }
    static WorldResult evaluate(List<WorldWitness> first, List<WorldWitness> second)
    {
        final var current = new TreeMap<Long, WorldWitness>();
        require(first.stream().map(WorldWitness::profileId).distinct().count() == first.size(), "duplicate first World identity");
        for (var value : second) { require(current.put(value.profileId(), value) == null, "duplicate World identity"); }
        int stable = 0;
        for (var before : first)
        {
            final var after = current.get(before.profileId());
            if (after != null && before.objectId() == after.objectId() && before.epoch() == after.epoch()
                && before.playerIdentity() == after.playerIdentity() && before.leaseToken() == after.leaseToken()) { stable++; }
        }
        final boolean pass = first.size() == 16 && second.size() == 16 && stable == 16;
        return new WorldResult(pass, first.size(), second.size(), stable, pass ? "STABLE16" : "SHORTFALL_OR_CHURN");
    }
    @FunctionalInterface
    interface WitnessReceipt { void write(boolean installed, String reason) throws Exception; }
    static void beforeGate(WorldResult result, PhantomLease105Checks.Step witness, WitnessReceipt attempt, PhantomLease105Checks.Step receipt) throws Exception
    {
        // Shortfall is durable evidence, not permission to bypass an earned native witness or STOP.
        try
        {
            try { witness.run(); }
            catch (Exception | AssertionError failure)
            {
                final String reason = failure.getClass().getName() + ":" + String.valueOf(failure.getMessage()).replace('\n', ' ').replace('\r', ' ');
                attempt.write(false, reason.substring(0, Math.min(1024, reason.length())));
                throw failure;
            }
            attempt.write(true, "PlayerNativeWork.run;currentOwned=true;installed=true");
        }
        finally { receipt.run(); }
    }
    static void restart(Path stage, String jar) throws Exception
    {
        final Path receipts = stage.resolve("private/receipts" + (PhantomTask102Lane.recovery108R3C(stage) ? "108R3C" : "108R3"));
        require(Files.readString(receipts.resolve("world6.pass")).startsWith("WORLD_GATE=PASS;first=16;second=16;stable=16;"), "full World16 A required before JDBC");
        require(Files.exists(receipts.resolve("stop6.natural")), "natural STOP A required before JDBC");
        PhantomStop108Checks.restart(receipts, jar);
        final var clean = PhantomTask102Lane.read(receipts.resolve("clean6.properties"));
        require(PhantomTask102Lane.hash(receipts.resolve("postflight6.pass.json")).equals(clean.getProperty("postflightSha256"))
            && PhantomTask102Lane.hash(stage.getParent().resolve("review6.private.json")).equals(clean.getProperty("reviewSha256"))
            && PhantomTask102Lane.hash(receipts.resolve("exit6.clean")).equals(clean.getProperty("exitSha256")), "independent accepted A hash chain required before JDBC");
    }
    static void census(Path stage, String classpath) throws Exception
    {
        final String namespace = Files.exists(stage.resolve("private/smoke108R3C.complete")) ? "108R3C" : "108R3";
        final var smoke = PhantomTask102Lane.read(stage.resolve("private/smoke" + namespace + ".complete"));
        final Path pins = stage.resolve("private/census" + namespace + "-classes.tsv");
        require(classpath.equals(smoke.getProperty("classpath")) && PhantomTask102Lane.hash(pins).equals(smoke.getProperty("classesSha256")), "census classpath/pins drift");
        final var seen = new java.util.HashSet<String>();
        for (String line : Files.readAllLines(pins))
        {
            final var fields = line.split("\t", -1);
            require(fields.length == 2 && java.util.Set.of("Task104Census.class", "Task105Census.class").contains(fields[0]) && seen.add(fields[0]), "both exact census classes required");
            final Path file = stage.resolve("build/phantom-test/bin/" + fields[0]);
            PhantomTask102Authority.physical(file);
            require(Files.size(file) > 0 && PhantomTask102Lane.hash(file).equals(fields[1]), "census bytecode drift");
        }
        require(seen.size() == 2, "both nested census classes required");
        if (Files.exists(stage.resolve("private/native.properties")))
        {
            PhantomTask102Authority.verifyNativeInputs(stage, PhantomTask102Lane.read(stage.resolve("private/native.properties")).getProperty("nativeInputsSha256"));
        }
    }
    static void capture(Path receipts, int phase, String cut, List<Player> observers, List<WorldWitness> cohort, int best, long noProgressMillis, String reason) throws Exception
    {
        require(java.util.Set.of("start", "plateau", "final").contains(cut), "expanded snapshot budget");
        final Object configured = PhantomPopulation102Native.field105(null, PhantomSystem.class, "_configuredInstance");
        require(configured != null, "diagnostic runtime absent");
        final var locality = (PhantomHumanLocalityControl) PhantomPopulation102Native.field105(configured, PhantomSystem.class, "_humanLocality");
        final var physical = locality.physicalSnapshot();
        final var selected = new java.util.LinkedHashSet<Long>();
        for (var value : cohort) { if (selected.size() < 64) { selected.add(value.profileId()); } }
        for (long id : new java.util.TreeSet<>(physical.positionRevisions().keySet())) { if (selected.size() >= 64) { break; } selected.add(id); }
        final var lines = new ArrayList<String>();
        final var now = java.time.Instant.now();
        final var timezone = org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.settings().populationTimeZone();
        lines.add("SUMMARY\tutc=" + now + ";jvmZone=" + java.time.ZoneId.systemDefault() + ";populationZone=" + timezone + ";calendar=" + now.atZone(timezone)
            + ";best=" + best + ";actual=" + cohort.size() + ";noProgressMillis=" + noProgressMillis + ";cutoff=" + reason
            + ";physical=" + physical.positionRevisions().size() + ";facts=" + physical.facts().size() + ";demandAgeMillis=" + Math.max(0, now.toEpochMilli() - physical.timestampMillis()) + ";overflow=" + physical.overflow());
        lines.add("STATUS\t" + PhantomSystem.operatorStatus());
        lines.add("PREPARATION\t" + PhantomSystem.operatorReadinessProgress(selected.isEmpty() ? 0 : selected.iterator().next()));
        lines.add("HUMANS\t" + PhantomSystem.onlineHumanPoints());
        for (var observer : observers)
        {
            lines.add("OBSERVER\t" + observer.getObjectId() + ";reference=" + System.identityHashCode(observer) + ";worldCurrent=" + (World.getInstance().getPlayer(observer.getObjectId()) == observer)
                + ";online=" + observer.isOnline() + ";x=" + observer.getX() + ";y=" + observer.getY() + ";z=" + observer.getZ() + ";instance=" + observer.getInstanceId()
                + ";lease=" + org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(observer.getObjectId()));
        }
        for (long id : selected)
        {
            lines.add("PROFILE\t" + id + "\t" + PhantomSystem.operatorAdmissionProfile(id) + "\tlocality=" + PhantomSystem.operatorLocalityTarget(id)
                + "\tprogress=" + PhantomSystem.operatorReadinessProgress(id));
        }
        for (var value : cohort)
        {
            final var player = (Player) value.playerIdentity();
            lines.add("WORLD\t" + value.profileId() + ";object=" + value.objectId() + ";epoch=" + value.epoch() + ";token=" + value.leaseToken()
                + ";reference=" + System.identityHashCode(player) + ";worldCurrent=" + (World.getInstance().getPlayer(value.objectId()) == player)
                + ";headless=" + player.hasHeadlessOutboundSession() + ";visible=" + observers.stream().map(player::isVisibleFor).toList());
        }
        Files.write(receipts.resolve("cohort108R3-" + phase + "-" + cut + ".private.tsv"), lines, StandardOpenOption.CREATE_NEW);
    }
    private static List<WorldWitness> cohort(int size)
    {
        final var result = new ArrayList<WorldWitness>();
        for (int i = 1; i <= size; i++) { result.add(new WorldWitness(i, i, i, new Object(), i)); }
        return List.copyOf(result);
    }
    private record CharacterFrame(java.util.Map<String, List<String[]>> slices) { }
    private static final java.util.Set<String> CHARACTER_SLICES = java.util.Set.of("profiles", "characters", "inventory", "components", "recipebook", "offliners", "offlineItems", "accountAccess", "columns");
    private static CharacterFrame characterFrame(Path path) throws Exception
    {
        PhantomTask102Authority.physical(path.toAbsolutePath().normalize());
        require(Files.size(path) <= 64_000_000, "bounded character frame required");
        final var slices = new java.util.LinkedHashMap<String, List<String[]>>();
        List<String[]> current = null;
        for (String raw : Files.readAllLines(path))
        {
            final String line = raw.startsWith("\uFEFF") ? raw.substring(1) : raw;
            if (line.startsWith("SLICE:"))
            {
                current = new ArrayList<>();
                require(CHARACTER_SLICES.contains(line.substring(6)) && slices.put(line.substring(6), current) == null, "duplicate/unknown frame");
            }
            else { require(current != null && current.size() < 200000, "unframed/unbounded row"); current.add(line.split("\t", -1)); }
        }
        require(slices.keySet().equals(CHARACTER_SLICES), "incomplete character frame");
        return new CharacterFrame(slices);
    }
    private static int characterColumn(CharacterFrame frame, String name)
    {
        final var columns = frame.slices().get("columns").stream().filter(row -> row.length == 4 && row[0].equals("characters") && row[1].equals(name)).toList();
        require(columns.size() == 1, "character column missing/duplicate");
        return Integer.parseInt(columns.getFirst()[2]) - 1;
    }
    private static java.util.Map<String, String[]> characterRows(CharacterFrame frame)
    {
        final var result = new TreeMap<String, String[]>();
        final int key = characterColumn(frame, "charId");
        final var metadata = frame.slices().get("columns").stream().filter(row -> row.length == 4 && row[0].equals("characters")).toList();
        require(metadata.stream().map(row -> row[1]).distinct().count() == metadata.size()
            && metadata.stream().map(row -> row[2]).distinct().count() == metadata.size(), "duplicate character metadata");
        for (int i = 1; i <= metadata.size(); i++) { final String ordinal = Integer.toString(i); require(metadata.stream().anyMatch(row -> row[2].equals(ordinal)), "character metadata gap"); }
        for (var row : frame.slices().get("characters")) { require(row.length == metadata.size() && result.put(row[key], row) == null, "character membership/width"); }
        return result;
    }
    private static void zeroSessions(String sessions) { require(sessions.isBlank(), "forbidden SQL session"); }
    private static List<String> characterDelta(CharacterFrame before, CharacterFrame after, List<String> witness, long runtimeSeconds, String sessions)
    {
        zeroSessions(sessions);
        require(runtimeSeconds > 0 && runtimeSeconds <= 10800, "exact retained JVM time bound required");
        for (String slice : CHARACTER_SLICES)
        {
            if (slice.equals("characters")) { continue; }
            final var left = before.slices().get(slice); final var right = after.slices().get(slice);
            require(left.size() == right.size(), "protected frame row count changed: " + slice);
            for (int i = 0; i < left.size(); i++) { require(java.util.Arrays.equals(left.get(i), right.get(i)), "protected frame changed: " + slice); }
        }
        final var left = characterRows(before); final var right = characterRows(after);
        require(left.keySet().equals(right.keySet()) && left.size() == 5256, "unknown/missing character ID");
        final int account = characterColumn(before, "account_name"), name = characterColumn(before, "char_name");
        final int vitality = characterColumn(before, "vitality_points"), online = characterColumn(before, "onlinetime");
        final var merchants = new java.util.HashSet<String>(); final var ordinals = new java.util.HashSet<Integer>();
        final var accounts = new TreeMap<String, String>();
        for (var row : before.slices().get("accountAccess")) { require(row.length == 2 && accounts.put(row[0], row[1]) == null, "duplicate account"); }
        for (var row : before.slices().get("components"))
        {
            if (!row[1].startsWith("merchant.owner.")) { continue; }
            final var role = org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.decodeRole(java.util.HexFormat.of().parseHex(row[4]));
            final String id = Integer.toString(role.characterId()); final var character = left.get(id);
            require(row[2].equals("1") && role.component().equals(row[1]) && role.stage() == org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Stage.READY
                && character != null && role.account().equals(character[account]) && "-1".equals(accounts.get(role.account()))
                && merchants.add(id) && ordinals.add(role.ordinal()), "merchant identity drift");
        }
        final var offliners = new java.util.HashSet<String>();
        for (var row : before.slices().get("offliners")) { require(offliners.add(row[0]), "duplicate offliner"); }
        final var nativeOwners = new java.util.HashSet<String>();
        for (String line : witness) { if (line.startsWith("OWNER\t")) { require(nativeOwners.add(line.split("\t", -1)[1]), "duplicate native OWNER"); } }
        require(merchants.size() == 250 && merchants.equals(offliners) && merchants.equals(nativeOwners), "original role/offliner/native owner sets differ");
        final var observers = new java.util.HashSet<String>();
        for (var entry : left.entrySet())
        {
            final var row = entry.getValue();
            for (int i = 0; i < 2; i++)
            {
                if (row[account].equals("own102view" + i)) { require(row[name].equals("Own102View" + i) && "-1".equals(accounts.get(row[account])) && observers.add(entry.getKey()), "labelled observer provenance"); }
            }
        }
        require(observers.size() == 2 && java.util.Collections.disjoint(observers, merchants), "two original observers required");
        final var changedVitality = new java.util.HashSet<String>(); final var changedOnline = new java.util.HashSet<String>();
        final var proof = new ArrayList<String>();
        final long maximumRecovery = Math.multiplyExact(Math.floorDiv(runtimeSeconds + 59, 60) + 1, 5);
        for (var entry : left.entrySet())
        {
            final var old = entry.getValue(); final var current = right.get(entry.getKey());
            for (int column = 0; column < old.length; column++)
            {
                if (old[column].equals(current[column])) { continue; }
                final long from = Long.parseLong(old[column]), to = Long.parseLong(current[column]);
                final long delta = Math.subtractExact(to, from);
                if (column == vitality)
                {
                    require(merchants.contains(entry.getKey()) && from >= 0 && to <= org.l2jmobius.gameserver.model.actor.stat.PlayerStat.MAX_VITALITY_POINTS
                        && delta > 0 && delta % 5 == 0 && delta <= maximumRecovery, "negative/anomalous/nonmerchant vitality delta");
                    changedVitality.add(entry.getKey());
                }
                else if (column == online)
                {
                    require(observers.contains(entry.getKey()) && from >= 0 && delta > 0 && delta <= runtimeSeconds, "negative/anomalous/nonobserver online delta");
                    changedOnline.add(entry.getKey());
                }
                else { throw new AssertionError("OWN108R3 forbidden character field at column " + column); }
                proof.add(entry.getKey() + "\t" + (column == vitality ? "merchant.vitality_points" : "observer.onlinetime") + "\t" + from + "\t" + to + "\t" + delta);
            }
        }
        require(changedVitality.equals(merchants) && changedOnline.equals(observers), "exact250 merchant +2 observer delta required");
        return proof;
    }
    private static CharacterFrame mutated(CharacterFrame frame, String slice, java.util.function.Consumer<List<String[]>> mutation)
    {
        final var slices = new java.util.LinkedHashMap<>(frame.slices());
        final var rows = new ArrayList<String[]>(); for (var row : slices.get(slice)) { rows.add(row.clone()); }
        slices.put(slice, rows); mutation.accept(rows); return new CharacterFrame(slices);
    }
    private static void characterNegatives(CharacterFrame before, CharacterFrame after, List<String> witness, long seconds) throws Exception
    {
        final var changed = characterDelta(before, after, witness, seconds, "");
        final String merchant = changed.stream().filter(row -> row.contains("merchant.vitality_points")).findFirst().orElseThrow().split("\t")[0];
        final int key = characterColumn(after, "charId");
        for (String field : List.of("fame", "exp", "sp", "level"))
        {
            rejects(() -> characterDelta(before, mutated(after, "characters", rows -> { final var row = rows.stream().filter(value -> value[key].equals(merchant)).findFirst().orElseThrow(); final int column = characterColumn(after, field); row[column] = Long.toString(Long.parseLong(row[column]) + 1); }), witness, seconds, ""));
        }
        rejects(() -> characterDelta(before, mutated(after, "characters", rows -> rows.getFirst()[key] = "2147483647"), witness, seconds, ""));
        for (String invalid : List.of("-1", "20001", "9999"))
        {
            rejects(() -> characterDelta(before, mutated(after, "characters", rows -> rows.stream().filter(value -> value[key].equals(merchant)).findFirst().orElseThrow()[characterColumn(after, "vitality_points")] = invalid), witness, seconds, ""));
        }
        rejects(() -> characterDelta(before, after, witness, seconds, "foreign SQL row"));
        for (String slice : List.of("inventory", "offliners", "accountAccess")) { rejects(() -> characterDelta(before, mutated(after, slice, rows -> rows.removeLast()), witness, seconds, "")); }
        for (String prefix : List.of("merchant.owner.", org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.RECEIPT_PREFIX, org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.COMPONENT))
        {
            final var components = after.slices().get("components").stream().filter(row -> row[1].startsWith(prefix)).toList();
            require(!components.isEmpty(), "negative target component absent: " + prefix);
            final String target = components.getFirst()[1];
            rejects(() -> characterDelta(before, mutated(after, "components", rows -> rows.stream().filter(row -> row[1].equals(target)).findFirst().orElseThrow()[4] = "00"), witness, seconds, ""));
        }
        System.out.println("CHARACTER_DELTA_NEGATIVES_PASS field;unknownID;negativeAnomaly;inventory;EXP_SP_level;identity;receipt;ledger;SQLsession");
    }
    private static String iniValue(Path path, String key) throws Exception
    {
        final var pattern = java.util.regex.Pattern.compile("^\\s*" + java.util.regex.Pattern.quote(key) + "\\s*=\\s*([^#;]+?)\\s*$");
        final var values = new ArrayList<String>();
        for (String line : Files.readAllLines(path)) { final var match = pattern.matcher(line); if (match.matches()) { values.add(match.group(1)); } }
        require(values.size() == 1, "source config key missing/duplicate: " + key); return values.getFirst();
    }
    private static void approvedCharacterDelta(String[] args) throws Exception
    {
        final Path beforePath = Path.of(args[1]), afterPath = Path.of(args[2]), witnessPath = Path.of(args[3]);
        final var evidence = PhantomTask102Lane.read(Path.of(args[4]));
        final long seconds = java.time.Duration.between(java.time.Instant.parse(evidence.getProperty("jvmStartUtc")), java.time.Instant.parse(evidence.getProperty("forceUtc"))).getSeconds();
        require("FORCED_UNCLEAN".equals(evidence.getProperty("priorStop")), "exact prior incarnation proof required");
        final Path source = Path.of(args[6]);
        require(iniValue(source.resolve("dist/game/config/Player.ini"), "EnableVitality").equalsIgnoreCase("True")
            && new java.math.BigDecimal(iniValue(source.resolve("dist/game/config/Rates.ini"), "RateRecoveryPeaceZone")).compareTo(java.math.BigDecimal.valueOf(5)) == 0
            && iniValue(source.resolve("dist/game/config/General.ini"), "CharacterDataStoreInterval").equals("15"), "reviewed vitality/autosave config drift");
        final var before = characterFrame(beforePath); final var after = characterFrame(afterPath);
        final var witness = Files.readAllLines(witnessPath);
        final var rows = characterDelta(before, after, witness, seconds, Files.readString(Path.of(args[5])));
        characterNegatives(before, after, witness, seconds);
        require(PhantomTask102Lane.hash(beforePath).equals("711afc78eb949100e9687de23ce1ccee460218847b685e0541985a66f4892450")
            && PhantomTask102Lane.hash(afterPath).equals("e4d3e5d63a570511e3d5794160b1daced3613b2f6b74d349b1299753249198d2"), "only owner-approved exact historical character delta may be adopted");
        final Path output = Path.of(args[7]).toRealPath();
        Files.write(output.resolve("character-delta-mapping.private.tsv"), rows, StandardOpenOption.CREATE_NEW);
        Files.writeString(output.resolve("approved-character-delta.properties"), "status=APPROVED_CHARACTER_DELTA\nconfidence=INDIRECT_MECHANISM_COMPATIBLE_NOT_OBSERVED_SQL_WRITER\nbeforeSha256=" + PhantomTask102Lane.hash(beforePath)
            + "\nafterSha256=" + PhantomTask102Lane.hash(afterPath) + "\nvalidatorSha256=" + PhantomTask102Lane.hash(source.resolve("test/java/org/l2jmobius/tests/phantoms/PhantomWorldCohort108R3Checks.java"))
            + "\nevidenceSha256=" + PhantomTask102Lane.hash(Path.of(args[4])) + "\nwitnessSha256=" + PhantomTask102Lane.hash(witnessPath) + "\nmerchantVitality=250\nobserverOnlineTime=2\nretainedJvmSeconds=" + seconds + "\n", StandardOpenOption.CREATE_NEW);
        require(!org.l2jmobius.commons.database.DatabaseFactory.isInitialized() && !PhantomSystem.hasConfiguredInstance(), "character verifier initialized runtime");
        System.out.println("APPROVED_CHARACTER_DELTA merchants=250;labelledObservers=2;otherFields=EXACT;otherFrames=EXACT;nativeWriter=INDIRECT_COMPATIBLE;runtimeBoundSeconds=" + seconds + ";JDBC=0;NATIVE=NOT_RUN");
    }
    public static void main(String[] args) throws Exception
    {
        if (args.length == 8 && args[0].equals("character-delta")) { approvedCharacterDelta(args); return; }
        if (args.length == 3 && args[0].equals("character-delta-red"))
        {
            require(PhantomTask102Lane.hash(Path.of(args[1])).equals(PhantomTask102Lane.hash(Path.of(args[2]))), "legacy byte-exact G0 rejected retained-JVM character delta"); return;
        }
        if (args.length == 3 && args[0].equals("census")) { census(Path.of(args[1]).toRealPath(), args[2]); System.out.println("COHORT108R3_CENSUS_PASS exactCP;bothHashes;JDBC=0"); return; }
        require(args.length == 0, "exact DB-free command required");
        final var first = cohort(16);
        require(evaluate(first, first).pass(), "actual stable16 rejected");
        final var nine = first.subList(0, 9);
        final var lateTen = first.subList(0, 10);
        require(!evaluate(nine, nine).pass() && !evaluate(nine, lateTen).pass(), "9/9 or late10 accepted");
        final var changed = new ArrayList<>(first);
        final var old = first.getFirst();
        changed.set(0, new WorldWitness(old.profileId(), old.objectId(), old.epoch(), old.playerIdentity(), old.leaseToken() + 1));
        require(!evaluate(first, changed).pass(), "replaced lease accepted");
        changed.set(0, new WorldWitness(old.profileId(), old.objectId(), old.epoch() + 1, new Object(), old.leaseToken()));
        require(!evaluate(first, changed).pass(), "replaced epoch/Player accepted");
        final var transcript = new ArrayList<String>();
        try
        {
            beforeGate(evaluate(nine, lateTen), () -> transcript.add("witness"), (installed, reason) -> transcript.add(installed ? "WITNESS_PASS" : "WITNESS_FAIL"), () -> transcript.add("WORLD_FAIL"));
        }
        finally
        {
            PhantomPopulation102Native.finishBoundary105(true,
                () -> require(transcript.contains("witness"), "shortfall skipped genuine witness attempt"),
                () -> transcript.add("leases"), () -> transcript.add("checkpoint"), () -> transcript.add("pools"),
                () -> transcript.add("database"), () -> transcript.add("blocked"));
        }
        require(transcript.equals(List.of("witness", "WITNESS_PASS", "WORLD_FAIL", "leases", "checkpoint", "pools", "database")), "shortfall did not preserve STOP ordering");
        final var refused = new ArrayList<String>();
        rejects(() -> beforeGate(evaluate(nine, nine), () -> { throw new AssertionError("stale owner"); },
            (installed, reason) -> { require(!installed && reason.equals("java.lang.AssertionError:stale owner"), "precise witness refusal lost"); refused.add("WITNESS_FAIL"); }, () -> refused.add("WORLD_FAIL")));
        require(refused.equals(List.of("WITNESS_FAIL", "WORLD_FAIL")), "refused witness lost World failure");
        authorityChecks();
        require(!org.l2jmobius.commons.database.DatabaseFactory.isInitialized()
            && !org.l2jmobius.gameserver.phantoms.PhantomSystem.hasConfiguredInstance(), "DB-free checks started runtime");
        System.out.println("COHORT108R3_PASS stable16;shortfall9Late10;churn;witnessBeforeWorld;canonicalStop;JDBC=0;NATIVE=NOT_RUN");
    }
    private static void authorityChecks() throws Exception
    {
        final Path stage = Files.createTempDirectory("cohort108R3-guards-").toRealPath();
        final Path privateRoot = Files.createDirectory(stage.resolve("private"));
        final Path selected = privateRoot.resolve("recovery108R3.properties");
        final Path bin = Files.createDirectories(stage.resolve("build/phantom-test/bin"));
        try
        {
            require(!PhantomTask102Lane.recovery108R3(stage), "unselected authority");
            Files.writeString(selected, "fixture");
            require(PhantomTask102Lane.recovery108R3(stage), "108R3 namespace absent");
            for (String prior : List.of("103", "104", "105", "106", "107", "108", "108R", "108R2"))
            {
                final Path marker = privateRoot.resolve("recovery" + prior + ".properties");
                Files.writeString(marker, "fixture");
                try { rejects(() -> PhantomTask102Lane.recovery108R3(stage)); } finally { Files.delete(marker); }
            }
            rejects(() -> restart(stage, "fixture"));
            final String cp = "fixtureCP";
            final Path pins = privateRoot.resolve("census108R3-classes.tsv");
            final Path smoke = privateRoot.resolve("smoke108R3.complete");
            for (String name : List.of("Task104Census.class", "Task105Census.class")) { Files.writeString(bin.resolve(name), "fixture-" + name); }
            Files.writeString(pins, "Task104Census.class\t" + PhantomTask102Lane.hash(bin.resolve("Task104Census.class")) + "\nTask105Census.class\t" + PhantomTask102Lane.hash(bin.resolve("Task105Census.class")));
            Files.writeString(smoke, "classpath=" + cp + "\nclassesSha256=" + PhantomTask102Lane.hash(pins));
            census(stage, cp); rejects(() -> census(stage, "wrongCP"));
            for (String name : List.of("Task104Census.class", "Task105Census.class"))
            {
                final Path file = bin.resolve(name); final byte[] bytes = Files.readAllBytes(file);
                Files.delete(file); rejects(() -> census(stage, cp));
                Files.writeString(file, "wrongSHA"); rejects(() -> census(stage, cp));
                Files.write(file, bytes);
            }
            census(stage, cp);
        }
        finally
        {
            for (String name : List.of("smoke108R3.complete", "census108R3-classes.tsv", "recovery108R3.properties")) { Files.deleteIfExists(privateRoot.resolve(name)); }
            for (String name : List.of("Task104Census.class", "Task105Census.class")) { Files.deleteIfExists(bin.resolve(name)); }
            Files.delete(bin); Files.delete(bin.getParent()); Files.delete(bin.getParent().getParent()); Files.delete(privateRoot); Files.delete(stage);
        }
    }
    private static void rejects(PhantomLease105Checks.Step action) throws Exception
    {
        try { action.run(); } catch (AssertionError | IllegalArgumentException | java.io.IOException expected) { return; }
        throw new AssertionError("OWN108R3 negative accepted");
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError("OWN108R3 " + message); } }
}

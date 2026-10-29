/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.Ledger;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.Receipt;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketLedger.Usage;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;

/** Captured SQL only. A COMMITTED intent alone never attests the physical stock effect. */
public final class PhantomMarketDelta108R2Checks
{
    private static final HexFormat HEX = HexFormat.of();
    private static final Set<String> SLICES = Set.of("profiles", "characters", "inventory", "components", "recipebook", "offliners", "offlineItems", "accountAccess", "columns");
    private record Frame(Map<String, List<String[]>> slices) { }
    private record Market(String owner, String[] ledgerRow, Ledger ledger, Map<Long, String[]> rows, Map<Long, Receipt> receipts) { }
    private record Totals(long before, long after, long fundingDelta, long stockDelta, long stock, int enchanted) { }
    private PhantomMarketDelta108R2Checks() { }

    public static void main(String[] args) throws Exception
    {
        if (args.length == 1 && args[0].equals("guards")) { guards(); return; }
        if (args.length == 3 && args[0].equals("census")) { census(Path.of(args[1]), args[2]); return; }
        require(args.length == 6 && Set.of("market", "historical").contains(args[0]), "exact command before after originalIDs nativeWitness sourcePolicy required");
        final var before = read(Path.of(args[1]));
        final var after = read(Path.of(args[2]));
        final var ids = new HashSet<>(Files.readAllLines(physical(Path.of(args[3]))));
        final var witness = Files.readAllLines(physical(Path.of(args[4])));
        final var policy = org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketPolicy.load(physical(Path.of(args[5])));
        sourcePolicy(before, policy); sourcePolicy(after, policy);
        final var result = compare(before, after, ids, witness);
        if (args[0].equals("historical"))
        {
            require(result.before() == 71 && result.after() == 78 && result.fundingDelta() == 0 && result.stockDelta() == 106_577_038L && result.stock() == 107_344_150L && result.enchanted() == 4, "real historical 71->78 result differs");
            compare(after, after, ids, witness);
            negatives(before, after, ids, witness);
            System.out.println("HIST108R2_PASS real=71->78;empty=78->78;negatives=PASS;old71=BYTE_EXACT;added=7;newEnchanted=4");
        }
        require(!org.l2jmobius.commons.database.DatabaseFactory.isInitialized() && !org.l2jmobius.gameserver.phantoms.PhantomSystem.hasConfiguredInstance(), "offline checker initialized runtime");
        System.out.println("MARKET108R2_PASS before=" + result.before() + ";after=" + result.after() + ";fundingDelta=" + result.fundingDelta() + ";stockDelta=" + result.stockDelta() + ";stock=" + result.stock() + ";finiteLimits=PASS;physical=PASS;JDBC=0;World=NOT_INITIALIZED");
    }
    private static void sourcePolicy(Frame frame, org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketPolicy policy)
    {
        require(market(frame).ledger().policy().equals(policy), "source market policy drift");
    }
    static void restart(Path stage, String jar) throws Exception
    {
        final Path receipts = stage.resolve("private/receipts108R2");
        PhantomStop108Checks.restart(receipts, jar);
        final var clean = PhantomTask102Lane.read(receipts.resolve("clean6.properties"));
        require(PhantomTask102Lane.hash(receipts.resolve("postflight6.pass.json")).equals(clean.getProperty("postflightSha256")) && PhantomTask102Lane.hash(stage.getParent().resolve("review6.private.json")).equals(clean.getProperty("reviewSha256")) && PhantomTask102Lane.hash(receipts.resolve("exit6.clean")).equals(clean.getProperty("exitSha256")), "independent CLEAN A signature/hash required before JDBC");
    }
    private static void guards() throws Exception
    {
        final Path stage = Files.createTempDirectory("market108R2-guards-");
        final Path privateRoot = Files.createDirectory(stage.resolve("private"));
        final Path selected = privateRoot.resolve("recovery108R2.properties");
        try
        {
            require(!PhantomTask102Lane.recovery108R2(stage), "unexpected authority");
            Files.writeString(selected, "fixture"); require(PhantomTask102Lane.recovery108R2(stage), "108R2 selection absent");
            for (String prior : List.of("103", "104", "105", "106", "107", "108", "108R"))
            {
                final Path marker = privateRoot.resolve("recovery" + prior + ".properties");
                Files.writeString(marker, "fixture");
                try { rejects(() -> PhantomTask102Lane.recovery108R2(stage)); } finally { Files.delete(marker); }
            }
            boolean failed = false;
            try { restart(stage, "fixture"); } catch (java.io.IOException | AssertionError | IllegalArgumentException expected) { failed = true; }
            require(failed, "restart without clean accepted");
        }
        finally { Files.deleteIfExists(selected); Files.delete(privateRoot); Files.delete(stage); }
        System.out.println("PASS market108R2.namespace103Through108R;missingIndependentClean;JDBC=0");
    }

    private static void census(Path stage, String classpath) throws Exception
    {
        final var smoke = PhantomTask102Lane.read(stage.resolve("private/smoke108R2.complete"));
        final Path pins = stage.resolve("private/census108R2-classes.tsv");
        require(classpath.equals(smoke.getProperty("classpath")) && PhantomTask102Lane.hash(pins).equals(smoke.getProperty("classesSha256")), "census classpath/pins drift");
        final var seen = new HashSet<String>();
        for (String line : Files.readAllLines(physical(pins)))
        {
            final var row = line.split("\t", -1);
            require(row.length == 2 && Set.of("Task104Census.class", "Task105Census.class").contains(row[0]) && seen.add(row[0]), "census class identity differs");
            final Path file = physical(stage.resolve("build/phantom-test/bin/" + row[0]));
            require(Files.size(file) > 0 && PhantomTask102Lane.hash(file).equals(row[1]), "census bytecode drift");
        }
        require(seen.size() == 2, "both nested census classes required");
        if (Files.exists(stage.resolve("private/native.properties")))
        {
            final var nativePins = PhantomTask102Lane.read(stage.resolve("private/native.properties"));
            PhantomTask102Authority.verifyNativeInputs(stage, nativePins.getProperty("nativeInputsSha256"));
        }
        System.out.println("PASS census108R2.exactClasspathBothHashes;JDBC=0");
    }

    private static Path physical(Path path) throws Exception
    {
        final Path absolute = path.toAbsolutePath().normalize();
        require(Files.isRegularFile(absolute, java.nio.file.LinkOption.NOFOLLOW_LINKS) && absolute.equals(absolute.toRealPath()) && Files.size(absolute) <= 64_000_000, "bounded physical captured input required");
        PhantomTask102Authority.physical(absolute);
        return absolute;
    }
    private static Frame read(Path path) throws Exception
    {
        final Map<String, List<String[]>> slices = new HashMap<>();
        List<String[]> current = null;
        try (var lines = Files.lines(physical(path)))
        {
            for (var iterator = lines.iterator(); iterator.hasNext();)
            {
                String line = iterator.next();
                if (line.startsWith("\uFEFF")) { line = line.substring(1); }
                if (line.startsWith("SLICE:"))
                {
                    current = new ArrayList<>();
                    require(SLICES.contains(line.substring(6)) && slices.put(line.substring(6), current) == null, "missing/duplicate/unexpected frame");
                }
                else { require(current != null && current.size() < 200000, "unframed/unbounded row"); current.add(line.split("\t", -1)); }
            }
        }
        require(slices.keySet().equals(SLICES), "incomplete captured SQL frames");
        final Frame frame = new Frame(slices);
        for (String table : List.of("characters", "items"))
        {
            final var columns = new HashSet<Integer>(); final var names = new HashSet<String>();
            for (var row : slices.get("columns"))
            {
                require(row.length == 4, "invalid column frame");
                if (row[0].equals(table)) { require(columns.add(Integer.parseInt(row[2])) && names.add(row[1]), "duplicate column"); }
            }
            require(!columns.isEmpty() && columns.stream().allMatch(value -> value >= 1 && value <= columns.size()), "column gap");
            for (var row : slices.get(table.equals("items") ? "inventory" : table)) { require(row.length == columns.size(), "row width differs"); }
        }
        return frame;
    }
    private static int column(Frame frame, String table, String name)
    {
        return frame.slices().get("columns").stream().filter(row -> row[0].equals(table) && row[1].equals(name)).mapToInt(row -> Integer.parseInt(row[2]) - 1).findFirst().orElseThrow();
    }
    private static Map<String, String[]> keyed(List<String[]> values, int key)
    {
        final Map<String, String[]> result = new HashMap<>();
        for (var row : values) { require(row.length > key && result.put(row[key], row) == null, "duplicate physical row"); }
        return result;
    }
    private static Map<String, String[]> components(Frame frame)
    {
        final Map<String, String[]> result = new HashMap<>();
        for (var row : frame.slices().get("components"))
        {
            require(row.length == 5 && Integer.parseInt(row[2]) > 0 && Long.parseLong(row[3]) >= 0 && result.put(row[0] + "\t" + row[1], row) == null, "invalid/duplicate component");
        }
        return result;
    }
    private static Market market(Frame frame)
    {
        String owner = null; String[] ledgerRow = null; Ledger ledger = null;
        final Map<Long, String[]> rows = new TreeMap<>(); final Map<Long, Receipt> receipts = new TreeMap<>();
        for (var row : components(frame).values())
        {
            if (!row[1].startsWith("merchant.market.")) { continue; }
            require(row[2].equals("1"), "market component schema differs");
            final byte[] payload = HEX.parseHex(row[4]);
            if (row[1].equals(PhantomMerchantMarketLedger.COMPONENT))
            {
                require(ledger == null, "multiple market ledgers");
                ledger = PhantomMerchantMarketLedger.decodeLedger(payload); owner = row[0]; ledgerRow = row;
                require(Arrays.equals(payload, PhantomMerchantMarketLedger.encode(ledger)), "noncanonical ledger codec");
            }
            else
            {
                require(row[1].startsWith(PhantomMerchantMarketLedger.RECEIPT_PREFIX), "unknown market component");
                final var receipt = PhantomMerchantMarketLedger.decodeReceipt(payload);
                require(!receipt.quarantined() && receipt.deliveredObjectId() > 0 && receipt.component().equals(row[1]) && receipts.put(receipt.sequence(), receipt) == null, "quarantined/duplicate/misnamed grant");
                require(Arrays.equals(payload, PhantomMerchantMarketLedger.encode(receipt)), "noncanonical receipt codec");
                rows.put(receipt.sequence(), row);
            }
        }
        require(ledger != null && ledger.sequence() == receipts.size(), "ledger sequence differs");
        for (long seq = 1; seq <= ledger.sequence(); seq++) { require(rows.containsKey(seq) && rows.get(seq)[0].equals(owner), "receipt sequence gap/foreign owner"); }
        return new Market(owner, ledgerRow, ledger, rows, receipts);
    }
    private static Map<Integer, PhantomMerchantBootstrap.Role> identities(Frame frame, Set<String> ids)
    {
        require(ids.size() == 5000, "original5000 authority required");
        final var chars = keyed(frame.slices().get("characters"), column(frame, "characters", "charId"));
        final var profiles = keyed(frame.slices().get("profiles"), 0);
        final var accounts = keyed(frame.slices().get("accountAccess"), 0);
        final var actual = new HashSet<String>(); final var ordinary = new HashSet<Integer>();
        final Map<Integer, PhantomMerchantBootstrap.Role> roles = new HashMap<>(); final var merchants = new HashSet<Integer>();
        final var codec = new PhantomPopulationStateCodec();
        for (var row : frame.slices().get("components"))
        {
            if (row[1].equals("population.state"))
            {
                final var state = codec.decode(HEX.parseHex(row[4])); final var profile = profiles.get(row[0]);
                require(profile != null && profile.length == 2, "population profile missing");
                final var character = chars.get(profile[1]);
                require(state.state() == PhantomPopulationState.State.READY && character != null && ordinary.add(Integer.parseInt(profile[1])) && state.reservedAccount().equals(character[column(frame, "characters", "account_name")]), "ordinary identity drift");
                require(actual.add(row[0] + "\t" + profile[1] + "\t" + state.initializationHash()), "duplicate original identity");
            }
            if (row[1].startsWith("merchant.owner."))
            {
                final var role = PhantomMerchantBootstrap.decodeRole(HEX.parseHex(row[4]));
                final var character = chars.get(Integer.toString(role.characterId())); final var account = accounts.get(role.account());
                require(row[2].equals("1") && role.stage() == PhantomMerchantBootstrap.Stage.READY && role.component().equals(row[1]) && roles.put(role.ordinal(), role) == null && merchants.add(role.characterId()) && character != null && role.account().equals(character[column(frame, "characters", "account_name")]) && account != null && account.length == 2 && account[1].equals("-1"), "durable merchant owner/account differs");
            }
        }
        require(actual.equals(ids) && roles.size() == 250 && java.util.Collections.disjoint(ordinary, merchants), "original5000/250 roles differ");
        return roles;
    }
    private static void limits(Market market, Map<Integer, PhantomMerchantBootstrap.Role> roles)
    {
        Ledger replay = Ledger.empty(market.ledger().policy());
        final Map<Integer, long[]> owners = new HashMap<>(); final Map<Integer, Long> items = new HashMap<>();
        final Map<Object, Long> grades = new HashMap<>(), gear = new HashMap<>(); final Map<String, Integer> epochs = new HashMap<>();
        final var stockIds = new HashSet<Integer>();
        for (var receipt : market.receipts().values())
        {
            final var role = roles.get(receipt.ordinal());
            require(role != null && role.characterId() == receipt.characterId(), "grant stale role");
            final boolean funding = receipt.itemId() == 57;
            require(funding ? receipt.source().equals("OWNER_OPERATING_CAPITAL;role=" + role.component()) : receipt.source().startsWith("OWNER_PROVISIONED;role=" + role.component() + ";source=") && !receipt.source().endsWith(";source="), "illegal grant cause");
            require(funding || stockIds.add(receipt.deliveredObjectId()), "reused stock object ID");
            final long[] owner = owners.computeIfAbsent(receipt.ordinal(), _ -> new long[4]);
            final var usage = new Usage(owner[0], owner[1], items.getOrDefault(receipt.itemId(), 0L), grades.getOrDefault(receipt.grade(), 0L), gear.getOrDefault(receipt.grade(), 0L), Math.toIntExact(owner[2]), owner[3]);
            replay = replay.reserve(receipt.withState(State.PREPARED, 0), usage);
            owner[0] = Math.addExact(owner[0], funding ? receipt.value() : 0); owner[1] = Math.addExact(owner[1], funding ? 0 : receipt.value()); owner[2]++;
            if (funding) { owner[3] = receipt.createdAt(); }
            else
            {
                items.merge(receipt.itemId(), receipt.quantity(), Math::addExact); grades.merge(receipt.grade(), receipt.quantity(), Math::addExact);
                if (receipt.category().equals("FINISHED_GEAR")) { gear.merge(receipt.grade(), receipt.quantity(), Math::addExact); }
                final String epoch = receipt.ordinal() + ":" + receipt.createdAt() / (market.ledger().policy().assortmentSeconds() * 1000L);
                require(epochs.merge(epoch, 1, Math::addExact) <= 2, "owner stock epoch quota exceeded");
            }
        }
        require(replay.sequence() == market.ledger().sequence() && replay.fundingIssued() == market.ledger().fundingIssued() && replay.stockIssued() == market.ledger().stockIssued(), "ledger sums differ");
        require(replay.enchantedEpochStart() == market.ledger().enchantedEpochStart() && replay.epochEnchanted() == market.ledger().epochEnchanted(), "ledger enchant epoch bookkeeping differs");
    }
    private static Totals compare(Frame before, Frame after, Set<String> ids, List<String> witness)
    {
        final var old = market(before); final var current = market(after);
        require(old.owner().equals(current.owner()) && old.ledger().policyVersion() == current.ledger().policyVersion() && old.ledger().policy().equals(current.ledger().policy()), "policy/ledger owner drift");
        require(current.ledger().sequence() >= old.ledger().sequence() && Long.parseLong(current.ledgerRow()[3]) >= Long.parseLong(old.ledgerRow()[3]), "ledger quota/version reset");
        final var oldComponents = components(before); final var newComponents = components(after);
        for (var entry : oldComponents.entrySet())
        {
            final var row = entry.getValue(); final var next = newComponents.get(entry.getKey());
            require(next != null && Long.parseLong(next[3]) >= Long.parseLong(row[3]), "removed component/decreased CAS version");
            if (row[1].startsWith(PhantomMerchantMarketLedger.RECEIPT_PREFIX) || row[1].startsWith("merchant.owner.") || row[1].equals("merchant.server.v1")) { require(Arrays.equals(row, next), "immutable prior receipt/role changed"); }
        }
        require(identities(before, ids).equals(identities(after, ids)), "merchant identities changed");
        final var roles = identities(after, ids);
        limits(old, roles); limits(current, roles);
        final var oldInventory = keyed(before.slices().get("inventory"), column(before, "items", "object_id"));
        final var newInventory = keyed(after.slices().get("inventory"), column(after, "items", "object_id"));
        final var chars = keyed(after.slices().get("characters"), column(after, "characters", "charId"));
        require(keyed(before.slices().get("profiles"), 0).keySet().equals(keyed(after.slices().get("profiles"), 0).keySet()) && keyed(before.slices().get("characters"), column(before, "characters", "charId")).keySet().equals(chars.keySet()), "profile/character births or removal");
        for (var row : after.slices().get("characters"))
        {
            for (String field : List.of("exp", "sp", "level")) { require(Long.parseLong(row[column(after, "characters", field)]) >= 0, "negative progression"); }
        }
        final var physical = new HashSet<String>(); final var grants = new HashSet<String>();
        for (String line : witness)
        {
            if (line.startsWith("GRANT\t")) { require(grants.add(line.substring(6)), "duplicate native grant attestation"); }
            if (line.startsWith("INVENTORY\t")) { require(physical.add(line), "duplicate native inventory attestation"); }
        }
        require(grants.equals(new HashSet<>(current.receipts().values().stream().map(Object::toString).toList())), "native/SQL grants mismatch");
        long funding = 0, stock = 0; int enchanted = 0;
        for (var receipt : current.receipts().values())
        {
            if (receipt.sequence() <= old.ledger().sequence()) { continue; }
            require(grants.contains(receipt.toString()), "new grant lacks native attestation");
            if (receipt.itemId() == 57) { funding = Math.addExact(funding, receipt.value()); }
            else
            {
                stock = Math.addExact(stock, receipt.value()); if (receipt.enchant() > 0) { enchanted++; }
                require(receipt.beforeCount() == 0 && receipt.beforeObjectId() == 0 && !oldInventory.containsKey(Integer.toString(receipt.deliveredObjectId())), "stock replay/ambiguous origin");
                require(physical.contains("INVENTORY\t" + receipt.characterId() + "\t" + receipt.deliveredObjectId() + "\t" + receipt.itemId() + "\t" + receipt.quantity() + "\t" + receipt.enchant()), "stock lacks physical native origin");
                final var item = newInventory.get(Integer.toString(receipt.deliveredObjectId()));
                // These captured witnesses attest origin, not a later sale. Unknown effects fail closed.
                require(item != null && item[column(after, "items", "item_id")].equals(Integer.toString(receipt.itemId())) && item[column(after, "items", "enchant_level")].equals(Integer.toString(receipt.enchant())) && Long.parseLong(item[column(after, "items", "count")]) == receipt.quantity() && item[column(after, "items", "owner_id")].equals(Integer.toString(receipt.characterId())), "stock SQL effect absent/contradictory; transaction proof required");
                final var owner = chars.get(item[column(after, "items", "owner_id")]);
                require(owner != null && keyed(after.slices().get("accountAccess"), 0).containsKey(owner[column(after, "characters", "account_name")]) && item[column(after, "items", "loc")].equals("INVENTORY"), "contradictory SQL account/stock ownership");
            }
        }
        require(funding == 0 && current.ledger().fundingIssued() == old.ledger().fundingIssued(), "funding replay/new operating capital forbidden in108R2");
        require(current.ledger().stockIssued() == Math.addExact(old.ledger().stockIssued(), stock), "delta ledger mismatch");
        return new Totals(old.ledger().sequence(), current.ledger().sequence(), funding, stock, current.ledger().stockIssued(), enchanted);
    }

    private static Frame changed(Frame frame, String key, java.util.function.Consumer<List<String[]>> mutation)
    {
        final var slices = new HashMap<>(frame.slices()); final List<String[]> rows = new ArrayList<>();
        for (var row : slices.get(key)) { rows.add(row.clone()); }
        mutation.accept(rows); slices.put(key, rows); return new Frame(slices);
    }
    private static void negatives(Frame before, Frame after, Set<String> ids, List<String> witness)
    {
        final var m = market(after); final String target = m.rows().get(72L)[1];
        for (int field : new int[] {0, 1, 2, 3, 4})
        {
            final Frame invalid = changed(after, "components", rows -> { final var row = rows.stream().filter(r -> r[1].equals(m.rows().get(1L)[1])).findFirst().orElseThrow(); row[field] = field == 4 ? m.rows().get(2L)[4] : field == 1 ? target : "0"; });
            rejects(() -> compare(before, invalid, ids, witness));
        }
        rejects(() -> compare(before, changed(after, "components", rows -> rows.removeIf(row -> row[1].equals(target))), ids, witness));
        rejects(() -> compare(before, changed(after, "components", rows -> rows.add(m.rows().get(72L).clone())), ids, witness));
        rejects(() -> compare(before, after, ids, List.of()));
        rejects(() -> compare(before, after, ids, witness.stream().filter(line -> !line.startsWith("INVENTORY\t")).toList()));
        rejects(() -> compare(before, changed(after, "inventory", rows -> rows.removeIf(row -> row[column(after, "items", "object_id")].equals(Integer.toString(m.receipts().get(72L).deliveredObjectId())))), ids, witness));
        for (State state : List.of(State.PREPARED, State.INCONSISTENT))
        {
            rejects(() -> compare(before, changed(after, "components", rows -> rows.stream().filter(row -> row[1].equals(target)).findFirst().orElseThrow()[4] = HEX.formatHex(PhantomMerchantMarketLedger.encode(m.receipts().get(72L).withState(state, 0)))), ids, witness));
        }
        rejects(() -> compare(before, changed(after, "components", rows -> rows.stream().filter(row -> row[1].equals(PhantomMerchantMarketLedger.COMPONENT)).findFirst().orElseThrow()[4] = HEX.formatHex(PhantomMerchantMarketLedger.encode(new Ledger(m.ledger().policyVersion(), m.ledger().policy(), m.ledger().sequence(), m.ledger().fundingIssued(), m.ledger().stockIssued() + 1, m.ledger().enchantedEpochStart(), m.ledger().epochEnchanted())))), ids, witness));
        rejects(() -> compare(before, changed(after, "components", rows -> rows.stream().filter(row -> row[1].equals(PhantomMerchantMarketLedger.COMPONENT)).findFirst().orElseThrow()[2] = "2"), ids, witness));
        rejects(() -> compare(before, changed(after, "components", rows -> rows.stream().filter(row -> row[1].startsWith("merchant.owner.")).findFirst().orElseThrow()[4] = "00"), ids, witness));
        for (String field : List.of("owner_id", "count"))
        {
            rejects(() -> compare(before, changed(after, "inventory", rows -> { final var row = rows.stream().filter(value -> value[column(after, "items", "object_id")].equals(Integer.toString(m.receipts().get(72L).deliveredObjectId()))).findFirst().orElseThrow(); row[column(after, "items", field)] = field.equals("count") ? "0" : after.slices().get("characters").getFirst()[column(after, "characters", "charId")]; }), ids, witness));
        }
        final var p = m.ledger().policy();
        final var roles = identities(after, ids);
        for (String cap : List.of("funding", "stockOwner", "item", "grade", "gear", "epoch", "grants"))
        {
            final var lower = new org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantMarketPolicy(p.assortmentSeconds(), p.gearShare(), p.gearGrades(), p.fundingEnabled(), p.fundingGrant(), p.fundingCooldown(), p.fundingPerOwner(), cap.equals("funding") ? p.fundingGrant() : p.fundingGlobal(), p.stockEnabled(), p.stockGlobal(), cap.equals("stockOwner") ? 1 : p.stockPerOwner(), cap.equals("item") ? 1 : p.stockPerItem(), cap.equals("grade") ? 1 : p.stockPerGrade(), cap.equals("gear") ? 1 : p.gearPerGrade(), p.stockStack(), p.enchantMax(), cap.equals("epoch") ? 1 : p.enchantedPerEpoch(), p.grantsPerOwner(), cap.equals("grants") ? 71 : p.grantsGlobal());
            rejects(() -> sourcePolicy(after, lower));
            rejects(() -> limits(new Market(m.owner(), m.ledgerRow(), new Ledger(m.ledger().policyVersion(), lower, m.ledger().sequence(), m.ledger().fundingIssued(), m.ledger().stockIssued(), m.ledger().enchantedEpochStart(), m.ledger().epochEnchanted()), m.rows(), m.receipts()), roles));
        }
        for (String field : List.of("owner", "ordinal", "item", "quantity", "enchant", "value", "source", "object", "sequence", "funding"))
        {
            final var r = m.receipts().get(72L);
            final var forged = new Receipt(field.equals("sequence") ? 80 : r.sequence(), field.equals("ordinal") ? 511 : r.ordinal(), field.equals("owner") ? 1 : r.characterId(), r.state(), field.equals("item") ? r.itemId() + 1 : field.equals("funding") ? 57 : r.itemId(), field.equals("quantity") ? r.quantity() + 1 : field.equals("funding") ? r.value() : r.quantity(), field.equals("enchant") ? 25 : field.equals("funding") ? 0 : r.enchant(), field.equals("value") ? r.value() + 1 : r.value(), field.equals("funding") ? "FUNDING" : r.category(), r.grade(), r.createdAt(), r.beforeAdena(), r.beforeCount(), r.beforeObjectId(), field.equals("object") ? m.receipts().get(73L).deliveredObjectId() : r.deliveredObjectId(), field.equals("source") ? "FALSE_GRANT" : r.source());
            rejects(() -> compare(before, changed(after, "components", rows -> rows.stream().filter(row -> row[1].equals(target)).findFirst().orElseThrow()[4] = HEX.formatHex(PhantomMerchantMarketLedger.encode(forged))), ids, witness));
        }
        rejects(() -> limits(new Market(m.owner(), m.ledgerRow(), new Ledger(m.ledger().policyVersion(), m.ledger().policy(), m.ledger().sequence(), m.ledger().fundingIssued(), m.ledger().stockIssued(), 0, 0), m.rows(), m.receipts()), identities(after, ids)));
        System.out.println("PASS market108R2.immutableMissingDuplicateGapCodecLedgerVersionRolesPreparedInconsistentFalsePhysicalFundingOwnerItemQuantityEnchantValueSourceObjectEpoch");
    }
    private static void rejects(Runnable action)
    {
        try { action.run(); } catch (AssertionError | IllegalArgumentException | ArithmeticException expected) { return; }
        throw new AssertionError("OWN108R2 negative accepted");
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError("OWN108R2 " + message); } }
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Independent TEST-only skills sidecar and causal SP audit. Never writes SQL. */
public final class PhantomComplete108Checks
{
    record Actor(int id, int classId, int classIndex, long sp, long exp, int level) { }
    record Key(int characterId, int classIndex, int skillId) implements Comparable<Key>
    {
        public int compareTo(Key other)
        {
            int value = Integer.compare(characterId, other.characterId);
            if (value == 0) { value = Integer.compare(classIndex, other.classIndex); }
            return value == 0 ? Integer.compare(skillId, other.skillId) : value;
        }
    }
    record Price(int classId, int skillId, int level, int cost, int getLevel, boolean auto) { }
    record Event(long sequence, int characterId, int classId, int classIndex, int skillId, int previous, int level, long cost,
        long before, long after, String status, long resultBefore, long resultAfter, int resultLevel, boolean earned) { }

    static void audit(Map<Integer, Actor> before, Map<Integer, Actor> after, Map<Key, Integer> oldSkills, Map<Key, Integer> newSkills,
        List<Event> events, Map<String, Price> prices)
    {
        require(before.keySet().equals(after.keySet()), "actor membership drift");
        final var expected = new TreeMap<>(oldSkills);
        final var balances = new HashMap<Integer, Long>();
        final var debits = new HashSet<Integer>();
        final var seen = new HashSet<Long>();
        long previousSequence = 0;
        for (var event : events)
        {
            require(event.sequence() > previousSequence && seen.add(event.sequence()), "duplicate/replayed receipt");
            previousSequence = event.sequence();
            final var actor = before.get(event.characterId());
            require(actor != null && event.classId() == actor.classId() && event.classIndex() == actor.classIndex(), "receipt class/identity mismatch");
            final var end = after.get(actor.id());
            require(actor.classId() == end.classId() && actor.classIndex() == end.classIndex(), "class changed during skill audit");
            if (event.status().equals("CREDIT"))
            {
                require(event.earned() && event.after() > event.before() && event.cost() >= event.after() - event.before(), "unproven native credit");
                require(balances.getOrDefault(actor.id(), actor.sp()) == event.before(), "native credit balance gap");
                balances.put(actor.id(), event.after()); continue;
            }
            require(!event.status().equals("DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED"), "durable commit runtime reconciliation failed");
            if (!event.status().equals("SUCCESS"))
            {
                require(event.before() == event.after(), "failed/idempotent operation mutated SP"); continue;
            }
            final var price = prices.get(priceKey(actor.classId(), event.skillId(), event.level()));
            require(price != null && !price.auto() && price.cost() == event.cost() && price.getLevel() <= end.level(), "canonical skill cost/level mismatch");
            final var key = new Key(actor.id(), actor.classIndex(), event.skillId());
            final int old = expected.getOrDefault(key, 0);
            require(old == event.previous() && event.level() == (old < 1 ? 1 : old + 1), "skill replay/rank mismatch");
            require(event.before() >= event.cost() && event.after() == Math.subtractExact(event.before(), event.cost())
                && event.resultBefore() == event.before() && event.resultAfter() == event.after() && event.resultLevel() == event.level(), "false success/debit receipt");
            require(balances.getOrDefault(actor.id(), actor.sp()) == event.before(), "skill balance gap/unproven earned credits");
            balances.put(actor.id(), event.after()); debits.add(actor.id()); expected.put(key, event.level());
        }
        for (var entry : before.entrySet())
        {
            final var old = entry.getValue(); final var end = after.get(entry.getKey());
            require(end.exp() >= old.exp() && end.level() >= old.level(), "negative EXP/level");
            if (end.sp() < old.sp() || debits.contains(old.id()))
            {
                require(old.classId() == end.classId() && old.classIndex() == end.classIndex() && debits.contains(old.id())
                    && balances.getOrDefault(old.id(), old.sp()) == end.sp(), "unexplained per-character negative SP/equation");
            }
        }
        for (var entry : expected.entrySet()) { require(newSkills.containsKey(entry.getKey()) && newSkills.get(entry.getKey()) >= entry.getValue(), "paid skill missing/durable skill loss"); }
        for (var entry : newSkills.entrySet())
        {
            if (entry.getValue().equals(expected.get(entry.getKey()))) { continue; }
            final var actor = after.get(entry.getKey().characterId());
            require(actor != null, "foreign skill identity");
            final var price = prices.get(priceKey(actor.classId(), entry.getKey().skillId(), entry.getValue()));
            require(entry.getKey().classIndex() == actor.classIndex() && price != null && price.auto() && price.cost() == 0 && price.getLevel() <= actor.level(), "skill without unique committed debit");
        }
    }
    private static String priceKey(int classId, int skillId, int level) { return classId + ":" + skillId + ":" + level; }
    private static Path _events;
    private static long _sequence;
    private static Throwable _failure;
    static void recordNative(Path receipts, int phase) throws Exception
    {
        _events = receipts.resolve("skills108-" + phase + ".events.private.tsv");
        Files.writeString(_events, "", StandardOpenOption.CREATE_NEW);
        org.l2jmobius.gameserver.phantoms.progression.PhantomClassSkillLearningTransaction.observeNativeTest(value -> {
            final var result = value.result();
            append(new Event(0, value.characterId(), value.classId(), value.classIndex(), value.skillId(), value.previousLevel(), value.targetLevel(), value.cost(),
                value.runtimeBefore(), value.runtimeAfter(), result.status().name(), result.spBefore(), result.spAfter(), result.resultingSkillLevel(), false));
        });
        org.l2jmobius.gameserver.model.actor.stat.PlayerStat.observeNativeTestSp(value -> {
            if (value.after() != value.before()) { append(new Event(0, value.characterId(), value.classId(), value.classIndex(), 0, 0, 0, value.requested(), value.before(), value.after(), "CREDIT", 0, 0, 0, value.earned())); }
        });
    }
    private static synchronized void append(Event event)
    {
        try
        {
            require(++_sequence <= 100000, "native evidence bound exceeded");
            Files.writeString(_events, _sequence + "\t" + event.characterId() + "\t" + event.classId() + "\t" + event.classIndex() + "\t" + event.skillId() + "\t" + event.previous()
                + "\t" + event.level() + "\t" + event.cost() + "\t" + event.before() + "\t" + event.after() + "\t" + event.status() + "\t" + event.resultBefore()
                + "\t" + event.resultAfter() + "\t" + event.resultLevel() + "\t" + event.earned() + "\n", StandardOpenOption.APPEND);
        }
        catch (Throwable failure) { _failure = failure; }
    }
    static synchronized void finishNative() throws Exception
    {
        require(_failure == null, "native skills receipt recording failed");
        Files.writeString(_events.resolveSibling(_events.getFileName() + ".complete"), "rows=" + _sequence + "\nsha256=" + PhantomTask102Lane.hash(_events) + "\n", StandardOpenOption.CREATE_NEW);
    }
    private static List<String> lines(Path path, long limit) throws Exception
    {
        PhantomTask102Authority.physical(path.toAbsolutePath().normalize());
        require(Files.size(path) <= limit, "unbounded sidecar/frame");
        final var rows = Files.readAllLines(path);
        if (!rows.isEmpty() && rows.getFirst().startsWith("\uFEFF")) { rows.set(0, rows.getFirst().substring(1)); }
        return rows;
    }
    private static Map<Integer, Actor> actors(Path path, java.util.Set<Integer> ids) throws Exception
    {
        String slice = ""; final var columns = new HashMap<String, Integer>(); final var rows = new ArrayList<String[]>();
        for (String line : lines(path, 64_000_000))
        {
            if (line.startsWith("SLICE:")) { slice = line.substring(6); continue; }
            final var fields = line.split("\t", -1);
            if (slice.equals("columns") && fields.length == 4 && fields[0].equals("characters")) { require(columns.put(fields[1], Integer.parseInt(fields[2]) - 1) == null, "duplicate character metadata"); }
            if (slice.equals("characters")) { rows.add(fields); }
        }
        final var result = new TreeMap<Integer, Actor>();
        for (String column : List.of("charId", "classid", "base_class", "sp", "exp", "level")) { require(columns.containsKey(column), "missing actor column " + column); }
        for (var row : rows)
        {
            final int id = Integer.parseInt(row[columns.get("charId")]); if (!ids.contains(id)) { continue; }
            final int classId = Integer.parseInt(row[columns.get("classid")]);
            require(classId == Integer.parseInt(row[columns.get("base_class")]), "subclass identity requires separate audit");
            require(result.put(id, new Actor(id, classId, 0, Long.parseLong(row[columns.get("sp")]), Long.parseLong(row[columns.get("exp")]), Integer.parseInt(row[columns.get("level")]))) == null, "duplicate actor");
        }
        require(result.keySet().equals(ids), "original cohort actor membership"); return result;
    }
    private static Map<Key, Integer> skills(Path path, java.util.Set<Integer> ids) throws Exception
    {
        final var result = new TreeMap<Key, Integer>(); Key previous = null;
        for (String line : lines(path, 32_000_000))
        {
            if (line.isBlank()) { continue; } final var fields = line.split("\t", -1); require(fields.length == 4, "skills width");
            final var key = new Key(Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Integer.parseInt(fields[2]));
            final int level = Integer.parseInt(fields[3]);
            require(ids.contains(key.characterId()) && key.classIndex() >= 0 && key.skillId() > 0 && level > 0 && (previous == null || previous.compareTo(key) < 0)
                && result.put(key, level) == null, "skills exact cohort/sort/duplicate"); previous = key;
        }
        return result;
    }
    private static Map<String, Price> prices(Path source) throws Exception
    {
        final var own = new HashMap<Integer, Map<String, Price>>(); final var parents = new HashMap<Integer, Integer>();
        final var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var files = Files.walk(source.resolve("dist/game/data/stats/players/skillTrees")))
        {
            for (var path : files.filter(value -> value.toString().endsWith(".xml")).sorted().toList())
            {
                final var trees = factory.newDocumentBuilder().parse(path.toFile()).getElementsByTagName("skillTree");
                for (int t = 0; t < trees.getLength(); t++)
                {
                    final var tree = (org.w3c.dom.Element) trees.item(t); if (!tree.getAttribute("type").equals("classSkillTree")) { continue; }
                    final int classId = Integer.parseInt(tree.getAttribute("classId"));
                    if (tree.hasAttribute("parentClassId")) { parents.put(classId, Integer.parseInt(tree.getAttribute("parentClassId"))); }
                    final var map = own.computeIfAbsent(classId, value -> new HashMap<>()); final var skills = tree.getElementsByTagName("skill");
                    for (int s = 0; s < skills.getLength(); s++)
                    {
                        final var skill = (org.w3c.dom.Element) skills.item(s);
                        final var price = new Price(classId, Integer.parseInt(skill.getAttribute("skillId")), Integer.parseInt(skill.getAttribute("skillLevel")),
                            skill.hasAttribute("levelUpSp") ? Integer.parseInt(skill.getAttribute("levelUpSp")) : 0,
                            Integer.parseInt(skill.getAttribute("getLevel")), Boolean.parseBoolean(skill.getAttribute("autoGet")));
                        map.put(price.skillId() + ":" + price.level(), price);
                    }
                }
            }
        }
        final var result = new HashMap<String, Price>();
        for (int classId : own.keySet())
        {
            final var chain = new ArrayList<Integer>(); final var seen = new HashSet<Integer>(); Integer id = classId;
            while (id != null) { require(seen.add(id), "class parent cycle"); chain.add(id); id = parents.get(id); }
            java.util.Collections.reverse(chain);
            for (int parent : chain) { for (var price : own.getOrDefault(parent, Map.of()).values()) { result.put(priceKey(classId, price.skillId(), price.level()), new Price(classId, price.skillId(), price.level(), price.cost(), price.getLevel(), price.auto())); } }
        }
        return result;
    }
    private static java.util.Set<Integer> ids(Path path) throws Exception
    {
        final var ids = new HashSet<Integer>();
        for (String line : lines(path, 2_000_000)) { final var fields = line.split("\t"); require(fields.length == 3 && ids.add(Integer.parseInt(fields[1])), "original identity format"); }
        require(ids.size() == 5000, "original5000 IDs required"); return ids;
    }
    private static void auditFiles(String[] args) throws Exception
    {
        final var ids = ids(Path.of(args[5])); final var events = new ArrayList<Event>();
        if (!args[6].equals("NONE"))
        {
            final Path path = Path.of(args[6]); final var seal = PhantomTask102Lane.read(path.resolveSibling(path.getFileName() + ".complete"));
            require(PhantomTask102Lane.hash(path).equals(seal.getProperty("sha256")), "native receipt seal drift");
            for (String line : lines(path, 32_000_000))
            {
                final var f = line.split("\t", -1); require(f.length == 15, "native receipt width");
                events.add(new Event(Long.parseLong(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    Integer.parseInt(f[6]), Long.parseLong(f[7]), Long.parseLong(f[8]), Long.parseLong(f[9]), f[10], Long.parseLong(f[11]), Long.parseLong(f[12]), Integer.parseInt(f[13]), Boolean.parseBoolean(f[14])));
            }
            require(events.size() == Long.parseLong(seal.getProperty("rows")), "native receipt rows drift");
        }
        final var before = actors(Path.of(args[1]), ids); final var after = actors(Path.of(args[2]), ids);
        for (String line : lines(Path.of(args[2]).resolveSibling("delta105-characters.private.tsv"), 16_000_000))
        {
            final var fields = line.split("\t", -1);
            require(fields.length == 4, "character delta width");
            if (java.util.Set.of("sp", "exp", "level").contains(fields[1]) && Long.parseLong(fields[3]) < Long.parseLong(fields[2]))
            { require(fields[1].equals("sp") && ids.contains(Integer.parseInt(fields[0])), "negative EXP/level or noncohort SP"); }
        }
        audit(before, after, skills(Path.of(args[3]), ids), skills(Path.of(args[4]), ids), events, prices(Path.of(args[7])));
        final long negatives = ids.stream().filter(id -> after.get(id).sp() < before.get(id).sp()).count();
        System.out.println("SKILLS108_AUDIT_PASS original=5000;negativeRows=" + negatives + ";classification=" + (negatives == 0 ? "ABSENT" : "EXPLAINED_BY_UNIQUE_NATIVE_PAIRINGS") + ";events=" + events.size() + ";SQL_WRITES=0");
    }
    private static void require(boolean value, String message) { if (!value) { throw new AssertionError("OWN108_COMPLETE " + message); } }
    private static void rejects(Runnable action)
    {
        try { action.run(); } catch (AssertionError expected) { return; }
        throw new AssertionError("OWN108_COMPLETE unexplained SP accepted");
    }
    private static void tests()
    {
        final var before = Map.of(1, new Actor(1, 53, 0, 312, 100, 5));
        final var after = Map.of(1, new Actor(1, 53, 0, 2, 100, 5));
        final var skills = Map.of(new Key(1, 0, 141), 1);
        final var prices = Map.of("53:141:1", new Price(53, 141, 1, 310, 5, false));
        final var committed = new Event(1, 1, 53, 0, 141, 0, 1, 310, 312, 2, "SUCCESS", 312, 2, 1, false);
        rejects(() -> audit(before, after, Map.of(), Map.of(), List.of(), prices));
        audit(before, after, Map.of(), skills, List.of(committed), prices);
        final var credited = Map.of(1, new Actor(1, 53, 0, 12, 101, 5));
        final var credit = new Event(1, 1, 53, 0, 0, 0, 0, 10, 312, 322, "CREDIT", 0, 0, 0, true);
        final var paidAfterCredit = new Event(2, 1, 53, 0, 141, 0, 1, 310, 322, 12, "SUCCESS", 322, 12, 1, false);
        audit(before, credited, Map.of(), skills, List.of(credit, paidAfterCredit), prices);
        rejects(() -> audit(before, credited, Map.of(), skills, List.of(new Event(1, 1, 53, 0, 0, 0, 0, 10, 312, 322, "CREDIT", 0, 0, 0, false), paidAfterCredit), prices));
        rejects(() -> audit(before, before, Map.of(), skills, List.of(), prices));
        rejects(() -> audit(before, after, Map.of(), skills, List.of(new Event(1, 1, 53, 0, 141, 0, 1, 300, 312, 2, "SUCCESS", 312, 2, 1, false)), prices));
        rejects(() -> audit(before, after, Map.of(), skills, List.of(new Event(1, 1, 53, 1, 141, 0, 1, 310, 312, 2, "SUCCESS", 312, 2, 1, false)), prices));
        rejects(() -> audit(before, after, Map.of(), skills, List.of(committed, committed), prices));
        rejects(() -> audit(before, after, Map.of(), skills, List.of(new Event(1, 1, 53, 0, 141, 0, 1, 310, 312, 2, "DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED", 0, 0, 0, false)), prices));
        rejects(() -> audit(before, after, Map.of(), skills, List.of(new Event(1, 1, 53, 0, 141, 0, 1, 310, 312, 2, "SUCCESS", 312, 312, 1, false)), prices));
        final var aggregate = Map.of(1, new Actor(1, 53, 0, 312, 100, 5), 2, new Actor(2, 53, 0, 10, 100, 5));
        final var aggregateAfter = Map.of(1, after.get(1), 2, new Actor(2, 53, 0, 1000, 100, 5));
        rejects(() -> audit(aggregate, aggregateAfter, Map.of(), Map.of(), List.of(), prices));
        boolean disabled = false;
        try { org.l2jmobius.gameserver.phantoms.progression.PhantomClassSkillLearningTransaction.observeNativeTest(null); } catch (IllegalStateException expected) { disabled = true; }
        require(disabled, "ordinary GameServer learning observer enabled"); disabled = false;
        try { org.l2jmobius.gameserver.model.actor.stat.PlayerStat.observeNativeTestSp(null); } catch (IllegalStateException expected) { disabled = true; }
        require(disabled, "ordinary GameServer credit observer enabled");
        require(!org.l2jmobius.commons.database.DatabaseFactory.isInitialized(), "DB-free checker initialized JDBC");
        System.out.println("SKILLS108_PASS pairedFixture;nativeZeroRank;earnedCredit;unearnedRejected;ordinaryObserversDisabled;missingSkill;missingDebit;wrongCost;wrongClassIndex;doubleApply;reconcileFailed;falseSuccess;aggregateOnly;SQL=0;NATIVE=NOT_RUN");
    }
    public static void main(String[] args) throws Exception
    {
        if (args.length == 2 && args[0].equals("source"))
        {
            final var prices = prices(Path.of(args[1]));
            for (int skill : List.of(141, 142, 172)) { require(prices.get("53:" + skill + ":1").cost() == 310, "canonical class53 price drift"); }
            tests(); System.out.println("SKILLS108_CANONICAL_PASS class53=141:1,142:1,172:1;eachCost=310;sourceOnly=true"); return;
        }
        if (args.length == 8 && args[0].equals("audit")) { auditFiles(args); return; }
        if (args.length == 4 && args[0].equals("sidecar"))
        {
            final var path = Path.of(args[1]); final var values = skills(path, ids(Path.of(args[2])));
            Files.writeString(Path.of(args[3]), "rows=" + values.size() + "\nsha256=" + PhantomTask102Lane.hash(path) + "\n", StandardOpenOption.CREATE_NEW); return;
        }
        require(args.length == 0, "exact DB-free command required"); tests();
    }
}

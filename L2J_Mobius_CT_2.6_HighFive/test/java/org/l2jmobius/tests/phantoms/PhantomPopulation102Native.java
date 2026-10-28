/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** OWN102 native production composition; no profile seeding, force materialization or Phantom relocation. */
public final class PhantomPopulation102Native
{
    private static final List<Player> OBSERVERS = new ArrayList<>();
    private static final List<PhantomIdentityLeaseRegistry.Lease> LEASES = new ArrayList<>();
    private static Path _receipts;
    private static int _phase;

    public static void main(String[] args) throws Exception
    {
        if (args.length == 2 && args[0].equals("config")) { configChecks(Path.of(args[1]).toRealPath()); return; }
        check(args.length == 1 && args[0].matches("[1-5]"), "OWN102 exact phase required; JDBC=0");
        _phase = Integer.parseInt(args[0]);
        final Path module = Path.of(System.getProperty("phantom.module.root", "")).toRealPath();
        PhantomTask102Lane.validate(module, Path.of(System.getProperty("phantom.test.config", "")));
        _receipts = PhantomTask102Lane.stage().resolve(Files.exists(PhantomTask102Lane.stage().resolve("private/retry1.begun")) ? "private/receipts-retry1" : "private/receipts");
        final Path begun = _receipts.resolve("start" + _phase + ".begun");
        Files.writeString(begun, "utc=" + java.time.Instant.now() + "\n", StandardOpenOption.CREATE_NEW);
        final long start = System.nanoTime();
        try
        {
            PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(10210201L, module, _receipts));
            org.l2jmobius.gameserver.data.xml.InitialEquipmentData.getInstance();
            org.l2jmobius.gameserver.data.xml.InitialShortcutData.getInstance();
            org.l2jmobius.gameserver.data.xml.MapRegionData.getInstance();
            PhantomSupportedContentScriptBootstrap.loadGoal036Owners(new PhantomTestContext(10210201L, module, _receipts));
            final var settings = PhantomPlayersConfig.settings();
            final int target = new int[] {500,1000,3000,5000,5000}[_phase - 1];
            check(settings.populationTarget() == target, "OWN102 phase target mismatch");
            final var catalog = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v3.xml"), settings.populationTimeZone());
            final var predecessor = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), settings.populationTimeZone());
            final var v2 = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v2.xml"), settings.populationTimeZone());
            final var store = new PhantomPopulationStore(PhantomProfileRepository.open(), catalog, predecessor, v2, settings.populationTimeZone());
            final var initial = census(store);
            if (_phase == 1) { check(initial.isEmpty(), "OWN102 first phase ordinary population is not empty"); }
            else { preserve(initial, Files.readAllLines(_receipts.resolve("ids" + (_phase - 1) + ".tsv"))); }
            if (_phase == 5)
            {
                final String prior = Files.readString(_receipts.resolve("stopped4.checkpoint"));
                final String restored = durableCheckpoint();
                verifyCheckpoint(prior, restored);
                Files.writeString(_receipts.resolve("restart5.checkpoint"), restored, StandardOpenOption.CREATE_NEW);
                starterReceipt("before");
            }
            check(PhantomSystem.startConfigured(), "OWN102 production PhantomSystem did not start");
            long lastProgress = System.nanoTime();
            int previousReady = -1;
            List<ManagedSnapshot> rows;
            while (true)
            {
                rows = census(store);
                final int ready = (int) rows.stream().filter(row -> row.state().state() == PhantomPopulationState.State.READY).count();
                sample(rows, start, "ramp");
                check(rows.stream().noneMatch(row -> row.state().state() == PhantomPopulationState.State.INCONSISTENT), "OWN102 durable INCONSISTENT; no advance");
                if (ready > previousReady) { previousReady = ready; lastProgress = System.nanoTime(); }
                if (ready == target && PhantomSystem.operatorPopulationSnapshot().ready() == target
                    && rows.stream().allMatch(row -> PhantomSystem.operatorAdmissionProfile(row.profile().profileId()).map(value -> value.scheduler() != null).orElse(false))) { break; }
                check(System.nanoTime() - lastProgress < 120_000_000_000L, "OWN102 two bounded 60s observation windows without READY progress");
                check(System.nanoTime() - start < 900_000_000_000L, "OWN102 phase 15 minute bound");
                Thread.sleep(10000);
            }
            check(rows.size() == target && new HashSet<>(rows.stream().map(row -> row.profile().characterObjectId()).toList()).size() == target, "OWN102 non-unique durable native identities");
            if (_phase > 1) { preserve(rows, Files.readAllLines(_receipts.resolve("ids" + (_phase - 1) + ".tsv"))); }
            final var ids = rows.stream().map(PhantomPopulation102Native::identity).sorted().toList();
            Files.write(_receipts.resolve("ids" + _phase + ".tsv"), ids, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            if (world(rows) < settings.maxMaterializedPhantoms()) { addObservers(rows); }
            final long worldStart = System.nanoTime();
            int best = 0;
            long worldProgress = worldStart;
            while (true)
            {
                final int actual = world(rows);
                sample(rows, start, "world");
                if (actual > best) { best = actual; worldProgress = System.nanoTime(); }
                if (actual == settings.maxMaterializedPhantoms()) { break; }
                if (System.nanoTime() - worldProgress >= 120_000_000_000L || System.nanoTime() - worldStart >= 300_000_000_000L) { break; }
                Thread.sleep(10000);
            }
            final var firstCohort = worldCohort(rows);
            final int first = firstCohort.size();
            Files.write(_receipts.resolve("world" + _phase + "-first.tsv"), firstCohort.stream().map(value -> value.profileId()+"\t"+value.objectId()+"\t"+value.epoch()).toList(), StandardOpenOption.CREATE_NEW);
            Thread.sleep(30000);
            final var secondCohort = worldCohort(rows);
            final int second = secondCohort.size();
            Files.write(_receipts.resolve("world" + _phase + "-second.tsv"), secondCohort.stream().map(value -> value.profileId()+"\t"+value.objectId()+"\t"+value.epoch()).toList(), StandardOpenOption.CREATE_NEW);
            sample(rows, start, "stable30s");
            if (_phase >= 4)
            {
                check(first == 128 && second == 128, "OWN102 legitimate World128 not reached; first=" + first + ";second=" + second);
                final var repeated = new java.util.HashMap<Long,WorldWitness>(); for(var value : secondCohort) { repeated.put(value.profileId(),value); }
                check(stableCohort(firstCohort,repeated::get).size()==128,"OWN102 World cohort changed during 30s stability interval");
            }
            else { check(first > 0 && second > 0, "OWN102 lower phase has no legitimate native World presence"); }
            if (_phase == 5)
            {
                check(PhantomSystem.operatorPopulationSnapshot().peakCreationClaims()==0,"OWN102 repeat boot replayed the creation saga");
                starterReceipt("after");
            }
            Files.writeString(_receipts.resolve("phase" + _phase + ".complete"), "ready=" + target + ";world1=" + first + ";world2=" + second + ";controlledHumans=" + OBSERVERS.size() + "\n", StandardOpenOption.CREATE_NEW);
        }
        finally
        {
            boolean drained = !PhantomSystem.hasConfiguredInstance();
            final long deadline = System.nanoTime() + 60_000_000_000L;
            while (!drained && System.nanoTime() < deadline) { drained = PhantomSystem.shutdownIfStarted(); if (!drained) { Thread.sleep(100); } }
            Files.writeString(_receipts.resolve("stop" + _phase + ".txt"), "drained=" + drained + ";snapshot=" + PhantomSystem.configuredShutdownSnapshot() + "\n", StandardOpenOption.CREATE_NEW);
            // Do not remove observers while native owners are still draining.
            if (drained)
            {
                for (Player observer : OBSERVERS) { observer.stopAllTasks(); observer.storeMe(); observer.deleteMe(); }
                for (var lease : LEASES) { lease.close(); }
                if (DatabaseFactory.isInitialized()) { Files.writeString(_receipts.resolve("stopped" + _phase + ".checkpoint"), durableCheckpoint(), StandardOpenOption.CREATE_NEW); }
                ThreadPool.shutdown(); DatabaseFactory.close();
            }
            check(drained, "OWN102 graceful STOP failed; no forced kill or pool teardown");
        }
    }

    private static List<ManagedSnapshot> census(PhantomPopulationStore store) throws Exception
    {
        final var result = new ArrayList<ManagedSnapshot>(); long cursor = 0;
        while (true) { final var page = store.loadManagedAfter(cursor, 256); result.addAll(page); if (page.size() < 256) { break; } cursor = page.getLast().profile().profileId(); check(result.size() <= 10000, "OWN102 census bound"); }
        try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT p.profile_id,p.character_object_id,c.account_name FROM phantom_profiles p JOIN phantom_profile_components s ON s.profile_id=p.profile_id AND s.component_type='population.state' LEFT JOIN characters c ON c.charId=p.character_object_id ORDER BY p.profile_id"))
        {
            final var physical = new TreeMap<Long,String>();
            try (var sql = query.executeQuery()) { while (sql.next()) { physical.put(sql.getLong(1), sql.getInt(2) + "\t" + sql.getString(3)); } }
            for (var row : result)
            {
                if (row.state().state() != PhantomPopulationState.State.READY) { continue; }
                check(row.profile().characterObjectId() != null && physical.get(row.profile().profileId()).equals(row.profile().characterObjectId() + "\t" + row.state().reservedAccount()) && !row.state().reservedAccount().startsWith("phm_"), "OWN102 ordinary profile/native character join mismatch");
            }
        }
        return result;
    }

    private static String identity(ManagedSnapshot row) { return row.profile().profileId() + "\t" + row.profile().characterObjectId() + "\t" + row.state().initializationHash(); }
    private static void preserve(List<ManagedSnapshot> rows, List<String> previous)
    { final var current = new HashSet<>(rows.stream().map(PhantomPopulation102Native::identity).toList()); check(current.containsAll(previous), "OWN102 lost/changed prior durable identity or initialization receipt"); }

    private record WorldWitness(long profileId, int objectId, long epoch, Object playerIdentity) { }
    private static int world(List<ManagedSnapshot> rows) { return worldCohort(rows).size(); }
    private static WorldWitness witness(long profileId)
    {
        final var admission = PhantomSystem.operatorAdmissionProfile(profileId).orElse(null);
        final var material = admission == null ? null : admission.materialization();
        if (material == null || material.state() != PhantomMaterializedPlayer.State.ACTIVE || !material.worldPresent() || !material.outboundAttached() || !material.identityLeaseRetained()) { return null; }
        final Player player = World.getInstance().getPlayer(material.characterObjectId());
        if (player == null || player.getClient() != null || !player.hasHeadlessOutboundSession() || !PhantomSystem.isMaterializationManaged(player)
            || PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) != OwnerKind.PHANTOM) { return null; }
        return new WorldWitness(profileId, player.getObjectId(), material.materializedAtNanos(), player);
    }
    private static List<WorldWitness> stableCohort(List<WorldWitness> first, java.util.function.LongFunction<WorldWitness> current)
    {
        final var result = new ArrayList<WorldWitness>();
        for (var before : first)
        {
            final var after = current.apply(before.profileId());
            if (after != null && before.objectId() == after.objectId() && before.epoch() == after.epoch() && before.playerIdentity() == after.playerIdentity()) { result.add(before); }
        }
        return List.copyOf(result);
    }
    private static List<WorldWitness> worldCohort(List<ManagedSnapshot> rows)
    {
        final var objects = new HashSet<Integer>();
        final var first = new ArrayList<WorldWitness>();
        for (var row : rows)
        {
            if (row.state().state() != PhantomPopulationState.State.READY) { continue; }
            final var current = witness(row.profile().profileId());
            if (current == null) { continue; }
            final Player player = (Player)current.playerIdentity();
            check(current.objectId() == row.profile().characterObjectId() && player.getAccountName().equals(row.state().reservedAccount()) && objects.add(player.getObjectId()), "OWN102 ordinary World membership mismatch");
            first.add(current);
        }
        // Unchanged epochs and references across both passes establish an overlapping live cohort.
        final var stable = stableCohort(first, PhantomPopulation102Native::witness);
        check(stable.size() <= PhantomPlayersConfig.settings().maxMaterializedPhantoms(), "OWN102 strict native cap exceeded");
        return stable;
    }

    private static void verifyCheckpoint(String previous, String current) { check(previous.equals(current), "OWN102 restart changed stopped identity/progress/inventory/receipts before production start"); }
    private static void starterReceipt(String cut) throws Exception
    {
        // Current owner policy is zero. Do not enable or rewrite it to manufacture a subsidy test.
        check(PhantomPlayersConfig.settings().starterShotLifetimeLimit()==0,"OWN102 positive lifetime budget requires separate actual tranche accounting; no replay claim");
        try(var connection=DatabaseFactory.getConnection();var query=connection.createStatement();var sql=query.executeQuery("SELECT COUNT(*) FROM phantom_profile_components s JOIN phantom_profile_components membership ON membership.profile_id=s.profile_id AND membership.component_type='population.state' WHERE s.component_type='starter.shots.v1'"))
        {
            check(sql.next() && sql.getLong(1)==0,"OWN102 disabled birth-only starter ledger appeared on restart");
        }
        Files.writeString(_receipts.resolve("starter5-"+cut+".txt"),"sourceEffectiveLifetimeLimit=0;ordinaryStarterLedgers=0;starterSubsidy=DISABLED_BY_OWNER\n",StandardOpenOption.CREATE_NEW);
    }
    private static String durableCheckpoint() throws Exception
    {
        final String membership = " JOIN phantom_profiles p ON p.character_object_id=c.charId JOIN phantom_profile_components membership ON membership.profile_id=p.profile_id AND membership.component_type='population.state' ";
        final String[] queries = {
            "SELECT c.* FROM characters c" + membership + "ORDER BY c.charId",
            "SELECT i.* FROM items i JOIN characters c ON c.charId=i.owner_id" + membership + "ORDER BY i.object_id",
            "SELECT s.profile_id,s.component_type,s.component_schema_version,s.row_version,HEX(s.payload) FROM phantom_profile_components s JOIN phantom_profile_components membership ON membership.profile_id=s.profile_id AND membership.component_type='population.state' ORDER BY s.profile_id,s.component_type"
        };
        final var result = new StringBuilder();
        try (var connection = DatabaseFactory.getConnection())
        {
            for (int index=0;index<queries.length;index++)
            {
                final var digest = java.security.MessageDigest.getInstance("SHA-256"); long count=0;
                try (var query = connection.createStatement(); var sql = query.executeQuery(queries[index]))
                {
                    while (sql.next()) { count++; for(int column=1;column<=sql.getMetaData().getColumnCount();column++) { final String value=sql.getString(column); digest.update((value==null ? "NULL\n" : value.length()+":"+value+"\n").getBytes(StandardCharsets.UTF_8)); } }
                }
                result.append(index).append('\t').append(count).append('\t').append(java.util.HexFormat.of().formatHex(digest.digest())).append('\n');
            }
        }
        return result.toString();
    }

    private static void addObservers(List<ManagedSnapshot> rows) throws Exception
    {
        final var regions = new HashSet<Integer>();
        for (var row : rows)
        {
            if (OBSERVERS.size() >= 16) { break; }
            if (!regions.add(row.state().homeMapRegionId())) { continue; }
            final var target = PhantomSystem.operatorLocalityTarget(row.profile().profileId()).orElse(null);
            if (target == null) { continue; }
            final int index = OBSERVERS.size(); final String account = "own102view" + index; final String name = "Own102View" + index;
            Integer id = null;
            try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId FROM characters WHERE account_name=? AND char_name=? AND accesslevel=0"))
            { query.setString(1, account); query.setString(2, name); try (var sql = query.executeQuery()) { if (sql.next()) { id = sql.getInt(1); check(!sql.next(), "OWN102 observer ambiguous"); } } }
            if (id == null) { try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES(?,'DISABLED_OWN102_TEST_HUMAN',-1,1)")) { insert.setString(1, account); insert.executeUpdate(); } }
            final Player observer = id == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(0), account, name, new PlayerAppearance((byte)0,(byte)0,(byte)0,false)) : Player.load(id);
            check(observer != null && !observer.isGM() && observer.getClient() == null && !observer.hasHeadlessOutboundSession(), "OWN102 labelled observer invalid");
            final var lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(observer.getObjectId(), OwnerKind.LOCALPLAY_TEST_HUMAN);
            check(lease != null, "OWN102 observer lease conflict"); LEASES.add(lease); OBSERVERS.add(observer);
            final var point = target.committedPosition(); observer.setXYZInvisible(point.x(),point.y(),point.z()); observer.setOnlineStatus(true,false); observer.stopAllTasks(); observer.spawnMe();
        }
        check(!PhantomSystem.onlineHumanPoints().isEmpty(), "OWN102 labelled witnesses unavailable to canonical locality");
    }

    private static void sample(List<ManagedSnapshot> rows, long start, String label) throws Exception
    {
        final var runtime = ManagementFactory.getRuntimeMXBean(); final var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(); final var status = PhantomSystem.operatorStatus();
        final var cpu = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean bean ? bean.getProcessCpuTime() : -1;
        long gcCount = 0, gcMillis = 0; for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) { gcCount += gc.getCollectionCount(); gcMillis += gc.getCollectionTime(); }
        final String line = java.time.Instant.now() + "\t" + _phase + "\t" + label + "\telapsedMs=" + (System.nanoTime()-start)/1000000 + ";durable=" + rows.size() + ";actualWorld=" + world(rows)
            + ";controlledHumans=" + OBSERVERS.size() + ";cpuNanos=" + cpu + ";heapUsed=" + heap.getUsed() + ";heapCommitted=" + heap.getCommitted() + ";threads=" + ManagementFactory.getThreadMXBean().getThreadCount()
            + ";gcCount=" + gcCount + ";gcMillis=" + gcMillis + ";uptimeMs=" + runtime.getUptime() + ";population=" + PhantomSystem.operatorPopulationSnapshot() + ";status=" + status + ";merchantExcluded=" + PhantomSystem.offlineMerchantSnapshot() + "\n";
        Files.writeString(_receipts.resolve("samples" + _phase + ".tsv"), line, StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        System.out.println("OWN102_SAMPLE phase=" + _phase + ";durable=" + rows.size() + ";ready=" + PhantomSystem.operatorPopulationSnapshot().ready() + ";world=" + world(rows) + ";label=" + label);
    }

    private static void configChecks(Path module) throws Exception
    {
        final Path file = module.resolve("dist/game/config/Custom/PhantomPlayers.ini"); final String text = Files.readString(file);
        final var settings = PhantomPlayersConfig.read(file);
        check(settings.enabled() && settings.populationTarget()==5000 && settings.populationActiveTarget()==128 && settings.maxMaterializedPhantoms()==128 && settings.materializationBurstSlots()==0 && settings.maxScheduledPhantomProfiles()==10000,"OWN102 effective owner settings mismatch");
        final Path scratch = Files.createTempDirectory(module.getParent().resolve("private"),"config-check-");
        for (String[] mutation : new String[][] {{"PhantomPopulationActiveTarget","129"},{"PhantomPopulationTarget","10001"}})
        { final Path invalid = scratch.resolve(mutation[0]+".ini"); Files.writeString(invalid,text.replaceFirst("(?m)^("+mutation[0]+"[ \\t]*=[ \\t]*)[^\\r\\n]*","$1"+mutation[1])); check(!PhantomPlayersConfig.read(invalid).enabled(),"OWN102 invalid bound did not fail closed"); }
        check(PhantomSystem.operatorPopulationSnapshot().managed()==0 && !DatabaseFactory.isInitialized(),"OWN102 read-only inactive snapshot initialized DB/runtime");
        final var identity = new Object(); final var first = List.of(new WorldWitness(1,10,100,identity),new WorldWitness(2,20,200,new Object()));
        check(stableCohort(first,id -> id==1 ? first.getFirst() : null).size()==1,"OWN102 vanished cohort member counted");
        check(stableCohort(first,id -> new WorldWitness(id,id==1?10:20,id==1?101:200,new Object())).isEmpty(),"OWN102 replaced Player/epoch counted as continuous World");
        final var rotating = new ArrayList<>(first); rotating.add(new WorldWitness(3,30,300,new Object()));
        check(stableCohort(rotating,id -> id<=2 ? first.get((int)id-1) : null).size()==2,"OWN102 historical turnover union mistaken for simultaneous cap violation");
        verifyCheckpoint("EXP=10;SP=2;shots=8;receipt=1","EXP=10;SP=2;shots=8;receipt=1");
        boolean rejected=false;
        try { verifyCheckpoint("EXP=10;SP=2;shots=8;receipt=1","EXP=0;SP=0;shots=16;receipt=2"); } catch(AssertionError expected) { rejected=true; }
        check(rejected,"OWN102 lost progress/duplicate grant checkpoint accepted");
        System.out.println("OWN102_CONFIG_PASS effective=True/5000/128/128;negativeBounds=2;inactiveSnapshotReadOnly=true;cohortChurn=REJECTED;checkpointDrift=REJECTED;JDBC=0;World=0");
    }
    private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}

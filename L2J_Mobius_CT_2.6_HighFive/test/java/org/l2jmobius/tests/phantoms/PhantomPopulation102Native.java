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
import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
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
    private static boolean _recovery103;
    private static boolean _recovery104;
    private static boolean _recovery105;
    private static boolean _recovery106;
    private static boolean _recovery107;
    private static boolean _recovery108;
    private static boolean _recovery108R;
    private static boolean _recovery108R2;
    private static String _boundary105;
    private static final java.util.Map<String, List<String>> _leaseRows106 = new TreeMap<>();
    private static List<Integer> _ordinaryCharacterIds = List.of();

    public static void main(String[] args) throws Exception
    {
        if (args.length == 1 && args[0].equals("logout107-check")) { logout107Checks(); return; }
        if (args.length == 2 && args[0].equals("config")) { configChecks(Path.of(args[1]).toRealPath()); return; }
        check(args.length == 1 && args[0].matches("[1-5]|recovery[4567]|recovery10[45][ABC]|recovery10[678][AB]|recovery108R[AB]|recovery108R2[AB]"), "OWN102 exact phase required; JDBC=0");
        _recovery104 = args[0].startsWith("recovery104");
        _recovery105 = args[0].startsWith("recovery105");
        _recovery106 = args[0].startsWith("recovery106");
        _recovery108 = args[0].startsWith("recovery108");
        _recovery108R2 = args[0].startsWith("recovery108R2");
        _recovery108R = args[0].startsWith("recovery108R") && !_recovery108R2;
        _recovery107 = args[0].startsWith("recovery107") || _recovery108;
        _recovery103 = args[0].startsWith("recovery");
        _phase = _recovery104 || _recovery105 || _recovery106 || _recovery107 ? switch (args[0].charAt(args[0].length() - 1)) { case 'A' -> 6; case 'B' -> 4; default -> 5; } : Integer.parseInt(args[0].substring(args[0].length() - 1));
        final Path module = Path.of(System.getProperty("phantom.module.root", "")).toRealPath();
        check(_recovery108R == PhantomTask102Lane.recovery108R(PhantomTask102Lane.stage()), "OWN108R entrypoint/authority namespace mismatch; JDBC=0");
        check(_recovery108R2 == PhantomTask102Lane.recovery108R2(PhantomTask102Lane.stage()), "OWN108R2 entrypoint/authority namespace mismatch; JDBC=0");
        PhantomTask102Lane.validate(module, Path.of(System.getProperty("phantom.test.config", "")));
        final boolean continuation = Files.exists(PhantomTask102Lane.stage().resolve("private/continuation2.begun"));
        _receipts = PhantomTask102Lane.stage().resolve(_recovery108R2 ? "private/receipts108R2" : _recovery108R ? "private/receipts108R" : _recovery108 ? "private/receipts108" : _recovery107 ? "private/receipts107" : _recovery106 ? "private/receipts106" : _recovery105 ? "private/receipts105" : _recovery104 ? "private/receipts104" : _recovery103 ? "private/receipts103" : continuation ? "private/receipts-continuation2" : Files.exists(PhantomTask102Lane.stage().resolve("private/retry1.begun")) ? "private/receipts-retry1" : "private/receipts");
        if (_recovery103)
        {
            final var authority = PhantomTask102Lane.read(PhantomTask102Lane.stage().resolve(_recovery108R2 ? "private/recovery108R2.properties" : _recovery108R ? "private/recovery108R.properties" : _recovery108 ? "private/recovery108.properties" : _recovery107 ? "private/recovery107.properties" : _recovery106 ? "private/recovery106.properties" : _recovery105 ? "private/recovery105.properties" : _recovery104 ? "private/recovery104.properties" : "private/recovery103.properties"));
            check((_recovery108R2 ? "PHANTOM-MARKET-DELTA-RESTART-108R2" : _recovery108R ? "PHANTOM-MARKET-STOP-RESTART-108R" : _recovery108 ? "PHANTOM-MARKET-STOP-RESTART-108" : _recovery107 ? "PHANTOM-LIVING-MARKET-107" : _recovery106 ? "PHANTOM-MERCHANT-IDLE-RECOVERY-106" : _recovery105 ? "PHANTOM-LEASE-STOP-CLOSURE-105" : _recovery104 ? "PHANTOM-LOCK-ORDER-RECOVERY-104" : "PHANTOM-SHUTDOWN-RECOVERY-103").equals(authority.getProperty("task")) && "true".equals(authority.getProperty("noReseed")), "OWN103/104/105/106/107/108/108R/108R2 recovery authority required");
            check(PhantomTask102Lane.hash(module.resolve("dist/libs/GameServer.jar")).equals(authority.getProperty("jarSha256")), "OWN103 repaired JAR drift");
            if (_recovery108R2 && _phase == 4) { PhantomMarketDelta108R2Checks.restart(PhantomTask102Lane.stage(), authority.getProperty("jarSha256")); }
            else if (_recovery108 && _phase == 4) { PhantomStop108Checks.restart(_receipts, authority.getProperty("jarSha256")); }
            if (_phase == 5) { check(Files.readString(_receipts.resolve("stop4.txt")).startsWith("drained=true;"), "OWN103 prior clean stop required"); }
        }
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
            final int target = _recovery103 ? 5000 : new int[] {500,1000,3000,5000,5000}[_phase - 1];
            check(settings.populationTarget() == target, "OWN102 phase target mismatch");
            if (_recovery103) { check(settings.populationActiveTarget() == (_recovery106 || _recovery107 ? 16 : _phase == 6 ? 16 : _phase == 7 ? 32 : 128) && settings.maxMaterializedPhantoms() == settings.populationActiveTarget(), "OWN103 phase cap mismatch"); }
            final var catalog = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v3.xml"), settings.populationTimeZone());
            final var predecessor = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), settings.populationTimeZone());
            final var v2 = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v2.xml"), settings.populationTimeZone());
            final var store = new PhantomPopulationStore(PhantomProfileRepository.open(), catalog, predecessor, v2, settings.populationTimeZone());
            final var initial = census(store);
            if (_recovery103)
            {
                preserve(initial, Files.readAllLines(PhantomTask102Lane.stage().resolve("private/origin103-ids.tsv")));
                check(initial.size() == 5000 && initial.stream().allMatch(row -> row.state().state() == PhantomPopulationState.State.READY), "OWN103 must reuse exactly5000 existing READY");
                _ordinaryCharacterIds = initial.stream().map(row -> row.profile().characterObjectId()).toList();
                Files.writeString(_receipts.resolve("before" + _phase + ".checkpoint"), durableCheckpoint(), StandardOpenOption.CREATE_NEW);
                if (_phase != 6)
                {
                    final int previous = _phase == 7 ? 6 : _phase == 4 ? (_recovery104 || _recovery105 || _recovery106 || _recovery107 ? 6 : 7) : 4;
                    verifyCheckpoint(Files.readString(_receipts.resolve("stopped" + previous + ".checkpoint")), durableCheckpoint());
                }
            }
            else if (_phase == 1 && continuation)
            {
                final var previous = PhantomTask102Lane.stage().resolve("private/receipts-retry1");
                preserve(initial, Files.readAllLines(previous.resolve("ids1.tsv")));
                check(initial.size()==500,"OWN102 continuation must preserve the actual500 ordinary profiles");
                verifyCheckpoint(Files.readString(previous.resolve("stopped1.checkpoint")),durableCheckpoint());
            }
            else if (_phase == 1) { check(initial.isEmpty(), "OWN102 first phase ordinary population is not empty"); }
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
                // The measured 3000 ramp creates about two identities/second; allow the final2000 delta plus bootstrap.
                // The independent two60s no-progress bound remains unchanged.
                final long phaseBoundNanos = (_phase == 4 ? 1800L : 900L) * 1_000_000_000L;
                check(System.nanoTime() - start < phaseBoundNanos, "OWN102 measured phase time bound");
                Thread.sleep(10000);
            }
            check(rows.size() == target && new HashSet<>(rows.stream().map(row -> row.profile().characterObjectId()).toList()).size() == target, "OWN102 non-unique durable native identities");
            if (_phase > 1 && !_recovery103) { preserve(rows, Files.readAllLines(_receipts.resolve("ids" + (_phase - 1) + ".tsv"))); }
            if (_recovery103) { check(PhantomSystem.operatorPopulationSnapshot().peakCreationClaims() == 0, "OWN103 recovery replayed creation"); }
            final var ids = rows.stream().map(PhantomPopulation102Native::identity).sorted().toList();
            Files.write(_receipts.resolve("ids" + _phase + ".tsv"), ids, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            if (world(rows) < settings.maxMaterializedPhantoms()) { addObservers(rows); }
            final long worldStart = System.nanoTime();
            int best = 0;
            long worldProgress = worldStart;
            while (true)
            {
                final int actual = world(rows);
                if (_recovery103) for (int milestone : new int[] {16,32})
                {
                    final Path receipt = _receipts.resolve("world" + _phase + "-milestone" + milestone + ".tsv");
                    if (actual >= milestone && !Files.exists(receipt)) { Files.write(receipt, worldCohort(rows).stream().limit(milestone).map(value -> value.profileId()+"\t"+value.objectId()+"\t"+value.epoch()).toList(), StandardOpenOption.CREATE_NEW); }
                }
                sample(rows, start, "world");
                if (actual > best) { best = actual; worldProgress = System.nanoTime(); }
                if (actual == settings.maxMaterializedPhantoms()) { break; }
                // Measured3000 convergence is slower than five minutes; final128 uses a bounded thirty-minute window.
                // Net World progress must still occur within each pair of sixty-second observation windows.
                final long worldBoundNanos = (_recovery103 ? 300L : _phase >= 4 ? 1800L : 300L) * 1_000_000_000L;
                if (System.nanoTime() - worldProgress >= 120_000_000_000L || System.nanoTime() - worldStart >= worldBoundNanos) { break; }
                Thread.sleep(10000);
            }
            final var firstCohort = worldCohort(rows);
            final int first = firstCohort.size();
            if (_recovery106) { PhantomMerchant106Native.capture(merchants105(false), _receipts, "first" + _phase, false); }
            if (_recovery107) { PhantomMerchant107Native.capture(merchants105(false), _receipts, "first" + _phase, false); }
            Files.write(_receipts.resolve("world" + _phase + "-first.tsv"), firstCohort.stream().map(PhantomPopulation102Native::worldReceipt).toList(), StandardOpenOption.CREATE_NEW);
            Thread.sleep(30000);
            final var secondCohort = worldCohort(rows);
            final int second = secondCohort.size();
            if (_recovery106) { PhantomMerchant106Native.capture(merchants105(false), _receipts, "second" + _phase, false); }
            if (_recovery107) { PhantomMerchant107Native.capture(merchants105(false), _receipts, "second" + _phase, false); }
            Files.write(_receipts.resolve("world" + _phase + "-second.tsv"), secondCohort.stream().map(PhantomPopulation102Native::worldReceipt).toList(), StandardOpenOption.CREATE_NEW);
            sample(rows, start, "stable30s");
            if (_phase >= 4 && !_recovery103)
            {
                check(first == 128 && second == 128, "OWN102 legitimate World128 not reached; first=" + first + ";second=" + second);
                final var repeated = new java.util.HashMap<Long,WorldWitness>(); for(var value : secondCohort) { repeated.put(value.profileId(),value); }
                check(stableCohort(firstCohort,repeated::get).size()==128,"OWN102 World cohort changed during 30s stability interval");
            }
            else { check(first > 0 && second > 0, "OWN102 lower phase has no legitimate native World presence"); }
            if (_recovery103)
            {
                if (_phase == 6 || _phase == 7 || _recovery104 || _recovery105 || _recovery106 || _recovery107)
                {
                    final var repeated = new java.util.HashMap<Long,WorldWitness>(); for(var value : secondCohort) { repeated.put(value.profileId(),value); }
                    final int stable = stableCohort(firstCohort,repeated::get).size();
                    check(_recovery104 ? stable > 0 : first == settings.maxMaterializedPhantoms() && second == first && stable == first, "OWN103/104/105 native cohort absent/unstable");
                }
                final var logout = logoutWitness(secondCohort, PhantomPopulation102Native::witness);
                final var player = (Player) logout.playerIdentity();
                final var scope = (org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope) player.getNativeWorkOwner();
                final var calls = new java.util.concurrent.atomic.AtomicInteger();
                final var installed = new java.util.concurrent.atomic.AtomicBoolean();
                org.l2jmobius.gameserver.model.actor.PlayerNativeWork.run(player, List.of(), "NATIVE_TEST_LOGOUT_OBSERVER", () ->
                {
                    // A running owned root prevents cleanup from passing its drain while the listener is installed.
                    final var root = scope == null ? null : org.l2jmobius.gameserver.model.actor.PlayerNativeWork.current(scope);
                    check(root != null && root.isRunning() && scope.isCurrent() && !player.isNativeCleanupPrepared()
                        && stableCohort(List.of(logout), PhantomPopulation102Native::witness).size() == 1, "OWN107 logout observer lifecycle changed before registration");
                    if (_recovery108) { PhantomStop108Checks.registration(root.isRunning(), scope.isCurrent(), player.isNativeCleanupPrepared(), stableCohort(List.of(logout), PhantomPopulation102Native::witness).size() == 1); }
                    _logout103Calls = calls; _logout103Scope = scope;
                    player.addListener(new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(player, org.l2jmobius.gameserver.model.events.EventType.ON_PLAYER_LOGOUT, (org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout event) ->
                    {
                        final var ticket = org.l2jmobius.gameserver.model.actor.PlayerNativeWork.current(scope);
                        check(event.getPlayer() == player && ticket != null && ticket.isRunning() && ticket.semantics() == org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Semantics.EARNED, "OWN103 actual logout lost earned owner");
                        check(calls.incrementAndGet() == 1, "OWN103 repeated logout callback");
                        _logout103Evidence = "event=ON_PLAYER_LOGOUT;semantics=" + ticket.semantics() + ";ownerCurrent=" + scope.isCurrent() + ";outstanding=" + scope.outstanding() + ";instant=" + instant103();
                        try { Thread.sleep(100); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("OWN103 listener interrupted", failure); }
                    }, calls));
                    installed.set(true);
                });
                check(installed.get(), "OWN107 logout observer native ownership refused");
                if (_recovery108) { Files.writeString(_receipts.resolve("logout108-prepared" + _phase + ".private.tsv"), worldReceipt(logout) + "\troot=PlayerNativeWork.run;installed=true\n", StandardOpenOption.CREATE_NEW); }
                Files.writeString(_receipts.resolve("world" + _phase + "-status.txt"), "actual=" + first + "/" + second + ";requestedCap=" + settings.maxMaterializedPhantoms() + ";normalACTIVE=NOT_PROVEN;typedNativeOwnerOnly=true\n", StandardOpenOption.CREATE_NEW);
            }
            if (_phase == 5)
            {
                check(PhantomSystem.operatorPopulationSnapshot().peakCreationClaims()==0,"OWN102 repeat boot replayed the creation saga");
                starterReceipt("after");
            }
            if (_recovery105 || _recovery106 || _recovery107) { captureBeforeStop105(); }
            if (_recovery107) { PhantomMerchant107Native.freezeForStop(merchants105(false), _receipts, _phase); }
            Files.writeString(_receipts.resolve("phase" + _phase + ".complete"), "ready=" + target + ";world1=" + first + ";world2=" + second + ";controlledHumans=" + OBSERVERS.size() + "\n", StandardOpenOption.CREATE_NEW);
        }
        finally
        {
            boolean drained = !PhantomSystem.hasConfiguredInstance();
            final long stopStart = System.nanoTime();
            final long deadline = System.nanoTime() + 60_000_000_000L;
            while (!drained && System.nanoTime() < deadline) { drained = PhantomSystem.shutdownIfStarted(); if (!drained) { Thread.sleep(100); } }
            Files.writeString(_receipts.resolve("stop" + _phase + ".txt"), "drained=" + drained + ";snapshot=" + PhantomSystem.configuredShutdownSnapshot() + "\n", StandardOpenOption.CREATE_NEW);
            if (_recovery103) { Files.writeString(_receipts.resolve("stop" + _phase + "-performance.txt"), "elapsedMs=" + ((System.nanoTime() - stopStart) / 1_000_000) + ";logout=" + _logout103Evidence + ";finalInstant=" + instant103() + "\n", StandardOpenOption.CREATE_NEW); }
            // Do not remove observers while native owners are still draining.
            if (_recovery105 || _recovery106 || _recovery107)
            {
                final boolean completedDrain = drained;
                finishBoundary105(completedDrain, PhantomPopulation102Native::proveStop105,
                    () -> {
                        Files.writeString(_receipts.resolve("stop" + _phase + "-world-leases.txt"), _boundary105, StandardOpenOption.CREATE_NEW);
                        if (_recovery106 || _recovery107)
                        {
                            for (var entry : _leaseRows106.entrySet()) { Files.write(_receipts.resolve(entry.getKey()), entry.getValue(), StandardOpenOption.CREATE_NEW); }
                            if (_recovery107) { PhantomMerchant107Native.capture(merchants105(), _receipts, "stopped" + _phase, true); }
                            else { PhantomMerchant106Native.capture(merchants105(), _receipts, "stopped" + _phase, true); }
                        }
                    },
                    () -> Files.writeString(_receipts.resolve("stopped" + _phase + ".checkpoint"), durableCheckpoint(), StandardOpenOption.CREATE_NEW),
                    ThreadPool::shutdown, DatabaseFactory::close,
                    () -> Files.writeString(_receipts.resolve("stop" + _phase + ".blocked"), "status=BLOCKED;noExitClean=true;drained=" + completedDrain + "\n", StandardOpenOption.CREATE_NEW));
            }
            else if (drained)
            {
                if (_recovery103 && _logout103Calls != null) { check(_logout103Calls.get() == 1 && _logout103Scope.outstanding() == 0, "OWN103 actual logout completion not proven"); }
                for (Player observer : OBSERVERS) { observer.stopAllTasks(); observer.storeMe(); observer.deleteMe(); }
                for (var lease : LEASES) { lease.close(); }
                if (_recovery104)
                {
                    for (int objectId : _ordinaryCharacterIds) { check(World.getInstance().getPlayer(objectId) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(objectId) == null, "OWN104 STOP retained ordinary World/lease identity"); }
                    check(PhantomIdentityLeaseRegistry.getInstance().getActiveLeaseCount() == 0, "OWN104 STOP retained leases");
                    Files.writeString(_receipts.resolve("stop" + _phase + "-world-leases.txt"), "ordinaryWorld=0;allLeases=0;observerOwners=0\n", StandardOpenOption.CREATE_NEW);
                }
                if (DatabaseFactory.isInitialized()) { Files.writeString(_receipts.resolve("stopped" + _phase + ".checkpoint"), durableCheckpoint(), StandardOpenOption.CREATE_NEW); }
                ThreadPool.shutdown(); DatabaseFactory.close();
            }
            check(drained, "OWN102 graceful STOP failed; no forced kill or pool teardown");
        }
    }

    static void finishBoundary105(boolean drained, PhantomLease105Checks.Step proof, PhantomLease105Checks.Step receipt,
        PhantomLease105Checks.Step checkpoint, PhantomLease105Checks.Step pools, PhantomLease105Checks.Step database, PhantomLease105Checks.Step blocked) throws Exception
    {
        PhantomLease105Checks.finish(drained, proof, () -> { receipt.run(); checkpoint.run(); }, pools, database, blocked);
    }

    record Merchant105(Player player, org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService service,
        org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Role role, PhantomIdentityLeaseRegistry.Lease lease)
    {
    }

    static Object field105(Object instance, Class<?> type, String name) throws ReflectiveOperationException
    {
        final var field = type.getDeclaredField(name); field.setAccessible(true); return field.get(instance);
    }

    /** Read-only test diagnostics of the actual service, including its original lease token. */
    @SuppressWarnings("unchecked")
    private static List<Merchant105> merchants105() throws Exception
    {
        return merchants105(true);
    }

    @SuppressWarnings("unchecked")
    private static List<Merchant105> merchants105(boolean requireStopped) throws Exception
    {
        final Class<?> type = org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService.class;
        final var nativeOwners = java.util.Map.copyOf((java.util.Map<Integer, ?>) field105(null, type, "NATIVE_OWNERS"));
        check(nativeOwners.size() <= 512, "OWN105 merchant inventory bound");
        final var result = new ArrayList<Merchant105>();
        for (var entry : nativeOwners.entrySet())
        {
            final var nativeOwner = entry.getValue();
            final Player player = (Player) field105(nativeOwner, nativeOwner.getClass(), "player");
            final var service = (org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService) field105(nativeOwner, nativeOwner.getClass(), "service");
            synchronized (service)
            {
                check(!requireStopped || Boolean.TRUE.equals(field105(service, type, "_stopping")), "OWN105 merchant producer not stopped");
                final var owners = (java.util.Map<Integer, Player>) field105(service, type, "_owners");
                final var leases = (java.util.Map<Integer, PhantomIdentityLeaseRegistry.Lease>) field105(service, type, "_leases");
                final var roles = (java.util.Map<Integer, ?>) field105(service, type, "_roles");
                final var ordinals = owners.entrySet().stream().filter(value -> value.getValue() == player).map(java.util.Map.Entry::getKey).toList();
                check(ordinals.size() == 1 && player.getObjectId() == entry.getKey(), "OWN105 ambiguous service Player identity");
                final int ordinal = ordinals.getFirst();
                final var saved = roles.get(ordinal);
                check(saved != null, "OWN105 service role absent");
                final var role = (org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Role) field105(saved, saved.getClass(), "role");
                final var lease = leases.get(ordinal);
                check(lease != null && !lease.isClosed() && lease.objectId() == player.getObjectId() && lease.ownerKind() == OwnerKind.PHANTOM,
                    "OWN105 service lease missing/stale");
                check(role.ordinal() == ordinal && role.characterId() == player.getObjectId() && role.account().equals(player.getAccountName())
                    && role.stage() == org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Stage.READY, "OWN105 merchant role identity not READY");
                final var server = (org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent) field105(service, type, "_server");
                try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT component_schema_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=?"))
                {
                    query.setLong(1, server.profileId()); query.setString(2, role.component());
                    try (var sql = query.executeQuery())
                    {
                        check(sql.next() && sql.getInt(1) == 1 && java.util.Arrays.equals(sql.getBytes(2), org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.encode(role)) && !sql.next(), "OWN105 durable merchant role differs");
                    }
                }
                result.add(new Merchant105(player, service, role, lease));
            }
        }
        return List.copyOf(result);
    }

    private static void captureBeforeStop105() throws Exception
    {
        final var rows = new ArrayList<String>();
        final var registry = PhantomIdentityLeaseRegistry.getInstance();
        for (var player : List.copyOf(World.getInstance().getPlayers()))
        {
            final var owner = registry.getOwnerSnapshot(player.getObjectId());
            rows.add("WORLD\t" + player.getObjectId() + "\t" + System.identityHashCode(player) + "\t" + player.getAccountName() + "\t" + owner
                + "\tordinary=" + _ordinaryCharacterIds.contains(player.getObjectId()) + "\tobserver=" + OBSERVERS.contains(player));
        }
        for (var merchant : merchants105(false))
        {
            rows.add("NATIVE_MERCHANT\t" + merchant.player().getObjectId() + "\t" + merchant.role().ordinal() + "\t" + merchant.lease().token()
                + "\t" + merchant.role().stage() + "\t" + System.identityHashCode(merchant.player()) + "\tquiescent=" + quiescent105(merchant));
        }
        Files.write(_receipts.resolve("before-stop" + _phase + "-owners.private.tsv"), rows.stream().sorted().toList(), StandardOpenOption.CREATE_NEW);
    }

    static boolean quiescent105(Merchant105 merchant)
    {
        final Player player = merchant.player();
        return World.getInstance().getPlayer(player.getObjectId()) == player && player.getClient() == null && !player.hasHeadlessOutboundSession()
            && player.getNativeWorkOwner() == null && player.getAccessLevel().getLevel() == 0 && !player.isAlikeDead()
            && !player.isCrafting() && !org.l2jmobius.gameserver.managers.RecipeManager.getInstance().isMakingItem(player)
            && !player.isProcessingTransaction() && !player.hasPendingOwnedStore()
            && org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService.ownsNative(player);
    }

    static List<String> listing105(Player player)
    {
        final var lines = new ArrayList<String>();
        switch (player.getPrivateStoreType())
        {
            case SELL, PACKAGE_SELL -> { for (var item : player.getSellList().getItems()) { lines.add(item.getObjectId() + "\t" + item.getCount() + "\t" + item.getPrice()); } }
            case BUY -> { for (var item : player.getBuyList().getItems()) { lines.add(item.getItem().getId() + "\t" + item.getCount() + "\t" + item.getPrice()); } }
            case MANUFACTURE -> { for (var item : player.getManufactureItems().values()) { lines.add(item.getRecipeId() + "\t0\t" + item.getCost()); } }
            default -> throw new AssertionError("OWN105 lease owner has no supported native store");
        }
        check(!lines.isEmpty() && new HashSet<>(lines.stream().map(line -> line.split("\t")[0]).toList()).size() == lines.size(), "OWN105 empty/duplicate native listing");
        return lines.stream().sorted().toList();
    }

    static boolean persisted105(Merchant105 merchant) throws Exception
    {
        final Player player = merchant.player();
        synchronized (player.getSellList()) { synchronized (player.getBuyList()) { synchronized (player.getManufactureItems())
        {
            check(quiescent105(merchant), "OWN105 native merchant mutation is pending");
            final var expected = listing105(player);
            try (var connection = DatabaseFactory.getConnection())
            {
                try (var query = connection.prepareStatement("SELECT c.account_name,c.accesslevel,a.accessLevel,a.password FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.charId=?"))
                {
                    query.setInt(1, player.getObjectId());
                    try (var sql = query.executeQuery())
                    {
                        check(sql.next() && merchant.role().account().equals(sql.getString(1)) && sql.getInt(2) == 0 && sql.getInt(3) == -1
                            && (!merchant.role().account().startsWith("phm_") || merchant.role().token().equals(sql.getString(4))) && !sql.next(), "OWN105 native merchant account/access authority differs");
                    }
                }
                try (var query = connection.prepareStatement("SELECT time,type,title FROM character_offline_trade WHERE charId=?"))
                {
                    query.setInt(1, player.getObjectId());
                    try (var sql = query.executeQuery())
                    {
                        final String title = switch (player.getPrivateStoreType()) { case BUY -> player.getBuyList().getTitle(); case MANUFACTURE -> player.getStoreName(); default -> player.getSellList().getTitle(); };
                        check(sql.next() && sql.getLong(1) == player.getOfflineStartTime() && sql.getInt(2) == player.getPrivateStoreType().getId()
                            && java.util.Objects.equals(title, sql.getString(3)) && !sql.next(), "OWN105 native offline metadata not persisted");
                    }
                }
                try (var query = connection.prepareStatement("SELECT item,count,price FROM character_offline_trade_items WHERE charId=? ORDER BY item"))
                {
                    query.setInt(1, player.getObjectId());
                    final var actual = new ArrayList<String>();
                    try (var sql = query.executeQuery()) { while (sql.next()) { actual.add(sql.getInt(1) + "\t" + sql.getLong(2) + "\t" + sql.getLong(3)); } }
                    check(expected.equals(actual.stream().sorted().toList()), "OWN105 native offline listing not persisted exactly");
                }
                if (player.getPrivateStoreType() == PrivateStoreType.SELL || player.getPrivateStoreType() == PrivateStoreType.PACKAGE_SELL)
                {
                    for (var item : player.getSellList().getItems())
                    {
                        try (var query = connection.prepareStatement("SELECT item_id,count FROM items WHERE object_id=? AND owner_id=?"))
                        {
                            query.setInt(1, item.getObjectId()); query.setInt(2, player.getObjectId());
                            try (var sql = query.executeQuery()) { check(sql.next() && sql.getInt(1) == item.getItem().getId() && sql.getLong(2) >= item.getCount() && !sql.next(), "OWN105 offline listing lacks native item ownership/quantity"); }
                        }
                    }
                }
            }
            return true;
        } } }
    }

    private static PhantomLease105Checks.Sample leaseSample105(String cut) throws Exception
    {
        final var registry = PhantomIdentityLeaseRegistry.getInstance();
        final int before = registry.getActiveLeaseCount();
        final var merchants = merchants105();
        final var witnesses = new ArrayList<PhantomLease105Checks.Witness>();
        final var privateRows = new ArrayList<String>();
        for (var merchant : merchants)
        {
            final var player = merchant.player();
            final var owner = registry.getOwnerSnapshot(player.getObjectId());
            check(owner != null, "OWN105 native merchant has no identity lease");
            witnesses.add(new PhantomLease105Checks.Witness(owner, _ordinaryCharacterIds.contains(player.getObjectId()), OBSERVERS.contains(player),
                World.getInstance().getPlayer(player.getObjectId()), player, merchant.lease().token(),
                org.l2jmobius.gameserver.phantoms.economy.PhantomOfflineMerchantService.ownsNative(player), quiescent105(merchant), _recovery107 ? PhantomMerchant107Native.persisted(merchant) : _recovery106 ? PhantomMerchant106Native.persisted(merchant) : persisted105(merchant), player.getAccountName(), merchant.role().account()));
            privateRows.add(owner.objectId() + "\t" + owner.ownerKind() + "\t" + owner.state() + "\t" + owner.token() + "\t" + merchant.lease().token() + "\t" + System.identityHashCode(player) + "\t" + merchant.role().ordinal());
        }
        check(World.getInstance().getPlayers().size() == merchants.size() && World.getInstance().getPlayers().stream().allMatch(player -> merchants.stream().anyMatch(merchant -> merchant.player() == player)), "OWN105 unknown World owner remains");
        check(before == registry.getActiveLeaseCount(), "OWN105 unstable global lease census");
        try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var sql = query.executeQuery("SELECT COUNT(*) FROM character_offline_trade"))
        { check(sql.next() && sql.getInt(1) == (_recovery106 || _recovery107 ? merchants.stream().filter(merchant -> merchant.player().getPrivateStoreType() != PrivateStoreType.NONE).count() : merchants.size()), "OWN105/106/107 persisted offliners differ from native OPEN stores"); }
        final String name = "stop" + _phase + "-leases-" + cut + ".private.tsv";
        if (_recovery106 || _recovery107) { _leaseRows106.put(name, privateRows.stream().sorted().toList()); }
        else { Files.write(_receipts.resolve(name), privateRows.stream().sorted().toList(), StandardOpenOption.CREATE_NEW); }
        return new PhantomLease105Checks.Sample(before, witnesses);
    }

    private static void proveStop105() throws Exception
    {
        check(_ordinaryCharacterIds.size() == 5000 && _logout103Calls != null && _logout103Calls.get() == 1 && _logout103Scope.outstanding() == 0, "OWN105 ordinary census/actual EARNED logout completion not proven");
        if (_recovery108) { PhantomStop108Checks.terminal(_logout103Calls.get(), _logout103Scope.outstanding(), _logout103Evidence); }
        if (_recovery108R || _recovery108R2)
        {
            PhantomStop108RChecks.earned(_logout103Calls.get(), _logout103Scope.outstanding(), _logout103Evidence);
            Files.writeString(_receipts.resolve((_recovery108R2 ? "logout108R2-terminal" : "logout108R-terminal") + _phase + ".txt"), "calls=1;outstanding=0;" + _logout103Evidence, StandardOpenOption.CREATE_NEW);
        }
        for (Player observer : OBSERVERS) { observer.stopAllTasks(); observer.storeMe(); observer.deleteMe(); }
        for (var lease : LEASES) { lease.close(); }
        for (int objectId : _ordinaryCharacterIds) { check(World.getInstance().getPlayer(objectId) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(objectId) == null, "OWN105 ordinary World/lease retained"); }
        for (Player observer : OBSERVERS) { check(World.getInstance().getPlayer(observer.getObjectId()) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(observer.getObjectId()) == null, "OWN105 observer World/lease retained"); }
        final var merchants = merchants105();
        final var registry = PhantomIdentityLeaseRegistry.getInstance();
        check(World.getInstance().getPlayers().size() == merchants.size() && registry.getActiveLeaseCount() == merchants.size(), "UNATTRIBUTED_LEASE_BLOCKED before native save");
        check(World.getInstance().getPlayers().stream().allMatch(player -> merchants.stream().anyMatch(merchant -> merchant.player() == player)), "OWN105 unknown World owner before native save");
        for (var merchant : merchants)
        {
            final var owner = registry.getOwnerSnapshot(merchant.player().getObjectId());
            check(quiescent105(merchant) && owner != null && owner.ownerKind() == OwnerKind.PHANTOM && owner.token() == merchant.lease().token(), "OWN105 pending/stale native merchant blocks persistence/teardown");
        }
        // Follow Shutdown.finishCountdown's stock bulk-save condition; realtime mode is verified without rewriting it.
        if ((OfflineTradeConfig.OFFLINE_TRADE_ENABLE || OfflineTradeConfig.OFFLINE_CRAFT_ENABLE)
            && OfflineTradeConfig.RESTORE_OFFLINERS && !OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME)
        { org.l2jmobius.gameserver.data.sql.OfflineTraderTable.getInstance().storeOffliners(); }
        final var first = leaseSample105("first");
        if (_recovery106 || _recovery107) { Thread.sleep(30000); }
        final var second = leaseSample105("second");
        final int legitimate = PhantomLease105Checks.verify(first, second);
        _boundary105 = "ordinaryWorld=0;ordinaryLeases=0;observerOwners=0;unknown=0;global=" + second.global() + ";attributedMerchants=" + legitimate + ";samples=2;nativePersisted=true\n";
    }

    private static java.util.concurrent.atomic.AtomicInteger _logout103Calls;
    private static org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope _logout103Scope;
    private static volatile String _logout103Evidence = "NOT_OBSERVED";
    private static String instant103()
    {
        try
        {
            final var field = ThreadPool.class.getDeclaredField("INSTANT_POOL"); field.setAccessible(true);
            final var pool = (java.util.concurrent.ThreadPoolExecutor) field.get(null);
            return "core=" + pool.getCorePoolSize() + ",active=" + pool.getActiveCount() + ",queued=" + pool.getQueue().size() + ",completed=" + pool.getCompletedTaskCount();
        }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("OWN103 instant sampling unavailable", failure); }
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

    record WorldWitness(long profileId, int objectId, long epoch, Object playerIdentity, long leaseToken)
    {
        WorldWitness(long profileId, int objectId, long epoch, Object playerIdentity) { this(profileId, objectId, epoch, playerIdentity, 0); }
    }
    private static String worldReceipt(WorldWitness value)
    {
        return value.profileId() + "\t" + value.objectId() + "\t" + value.epoch() + (_recovery104 || _recovery105 || _recovery106 || _recovery107 ? "\t" + value.leaseToken() + "\t" + System.identityHashCode(value.playerIdentity()) : "");
    }
    private static int world(List<ManagedSnapshot> rows) { return worldCohort(rows).size(); }
    private static WorldWitness witness(long profileId)
    {
        final var admission = PhantomSystem.operatorAdmissionProfile(profileId).orElse(null);
        final var material = admission == null ? null : admission.materialization();
        if (material == null || material.state() != PhantomMaterializedPlayer.State.ACTIVE || !material.worldPresent() || !material.outboundAttached() || !material.identityLeaseRetained()) { return null; }
        final Player player = World.getInstance().getPlayer(material.characterObjectId());
        if (player == null || player.getClient() != null || !player.hasHeadlessOutboundSession() || !PhantomSystem.isMaterializationManaged(player)
            || PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) != OwnerKind.PHANTOM) { return null; }
        final var lease = PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(player.getObjectId());
        return lease == null || lease.ownerKind() != OwnerKind.PHANTOM ? null : new WorldWitness(profileId, player.getObjectId(), material.materializedAtNanos(), player, lease.token());
    }
    private static List<WorldWitness> stableCohort(List<WorldWitness> first, java.util.function.LongFunction<WorldWitness> current)
    {
        final var result = new ArrayList<WorldWitness>();
        for (var before : first)
        {
            final var after = current.apply(before.profileId());
            if (after != null && before.objectId() == after.objectId() && before.epoch() == after.epoch() && before.playerIdentity() == after.playerIdentity() && before.leaseToken() == after.leaseToken()) { result.add(before); }
        }
        return List.copyOf(result);
    }
    static WorldWitness logoutWitness(List<WorldWitness> cohort, java.util.function.LongFunction<WorldWitness> current)
    {
        final var live = stableCohort(cohort, current);
        check(!live.isEmpty(), "OWN107 no current logout witness; do not bind a stale lifecycle");
        return live.getFirst();
    }
    private static void logout107Checks()
    {
        final var first = new WorldWitness(1, 10, 100, new Object(), 1);
        final var second = new WorldWitness(2, 20, 200, new Object(), 2);
        final var replacement = new WorldWitness(1, 10, 101, new Object(), 3);
        check(logoutWitness(List.of(first, second), id -> id == 1 ? replacement : second) == second, "OWN107 logout observer selected replaced snapshot Player/token");
        boolean rejected = false;
        try { logoutWitness(List.of(first), id -> replacement); } catch (AssertionError expected) { rejected = true; }
        check(rejected, "OWN107 stale-only logout cohort accepted");
        check(!DatabaseFactory.isInitialized() && !PhantomSystem.hasConfiguredInstance(), "OWN107 logout check started runtime/JDBC");
        System.out.println("OWN107_LOGOUT_WITNESS_PASS replacedFirst=REJECTED;currentSecond=SELECTED;allStale=REJECTED;JDBC=0;NATIVE=NOT_RUN");
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
        final var queries = new ArrayList<String>(List.of(
            "SELECT c.* FROM characters c" + membership + "ORDER BY c.charId",
            "SELECT i.* FROM items i JOIN characters c ON c.charId=i.owner_id" + membership + "ORDER BY i.object_id",
            "SELECT s.profile_id,s.component_type,s.component_schema_version,s.row_version,HEX(s.payload) FROM phantom_profile_components s JOIN phantom_profile_components membership ON membership.profile_id=s.profile_id AND membership.component_type='population.state' ORDER BY s.profile_id,s.component_type"
        ));
        if (_recovery103) { queries.add("SELECT r.* FROM character_recipebook r JOIN characters c ON c.charId=r.charId" + membership + "ORDER BY r.charId,r.id,r.classIndex"); }
        if (_recovery105 || _recovery106 || _recovery107)
        {
            queries.addAll(List.of("SELECT * FROM character_offline_trade ORDER BY charId", "SELECT * FROM character_offline_trade_items ORDER BY charId,item",
                "SELECT * FROM characters ORDER BY charId", "SELECT * FROM items ORDER BY object_id",
                "SELECT profile_id,component_type,component_schema_version,row_version,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type",
                "SELECT * FROM character_recipebook ORDER BY charId,id,classIndex"));
        }
        final var result = new StringBuilder();
        try (var connection = DatabaseFactory.getConnection())
        {
            for (int index=0;index<queries.size();index++)
            {
                final var digest = java.security.MessageDigest.getInstance("SHA-256"); long count=0;
                try (var query = connection.createStatement(); var sql = query.executeQuery(queries.get(index)))
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
            if (_recovery103) { check(id != null, "OWN103 only existing labelled test humans may be reused"); }
            if (_recovery105 || _recovery106 || _recovery107)
            {
                try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT accessLevel,password FROM accounts WHERE login=?"))
                {
                    query.setString(1, account);
                    try (var sql = query.executeQuery()) { check(sql.next() && sql.getInt(1) == -1 && "DISABLED_OWN102_TEST_HUMAN".equals(sql.getString(2)) && !sql.next(), "OWN105 observer provenance differs"); }
                }
            }
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
        check(stableCohort(first,id -> { final var before = first.get((int)id-1); return new WorldWitness(id,before.objectId(),before.epoch(),before.playerIdentity(),1); }).isEmpty(),"OWN104 replaced identity lease counted as continuous World");
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

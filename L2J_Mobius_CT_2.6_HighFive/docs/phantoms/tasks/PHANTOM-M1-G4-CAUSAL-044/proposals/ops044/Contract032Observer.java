import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Task-only immutable native receipt observer. Ordinary hooks have no SQL/I/O/waits or other actor locks. */
public final class Contract032Observer
{
    private record Selection(Map<Long, Long> profiles, Path output, String sha) { }
    private record Witness(Path path, String text, int sqlObjectId)
    {
        Witness(Path path, String text) { this(path, text, 0); }
    }
    private record Prepared(Player player, PhantomNativeWorkScope owner, PhantomOwnedStoreIntent intent, Witness witness) { }
    private record Crash(String mode, Path output, String dumpHash) { }
    private static final ThreadLocal<Prepared> PREPARED = new ThreadLocal<>();
    private static final AtomicBoolean CRASHED = new AtomicBoolean();
    private static volatile Crash crash;
    private static final ArrayBlockingQueue<Witness> RING = new ArrayBlockingQueue<>(512);
    private static final AtomicLong OVERFLOW = new AtomicLong();
    private static final AtomicLong CAPTURED = new AtomicLong();
    private static final AtomicLong EXPORTED = new AtomicLong();
    private static final AtomicLong HOOK_CALLS = new AtomicLong(), HOOK_NANOS = new AtomicLong(), HOOK_MAX_NANOS = new AtomicLong();
    private static volatile Selection selection;
    private static volatile String exporterFailure = "";
    private static final java.util.concurrent.atomic.AtomicReference<String> FIRST_FAILURE = new java.util.concurrent.atomic.AtomicReference<>("");
    private static final AtomicLong BUFFER_BYTES = new AtomicLong(), DISK_BYTES = new AtomicLong(), NATIVE_BIRTHS = new AtomicLong();
    private static final AtomicLong REFUSED_BIRTHS = new AtomicLong(), EXPORT_FAILURES = new AtomicLong(), ACTIVE_REFERENCES = new AtomicLong();
    private static volatile Path streamRoot;
    private static volatile String lifetimeRunId = "";
    private static volatile String witnessDatabase = "", witnessJarSha = "";
    private static final java.util.Set<Long> COMMIT_PROFILES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<Long> CONTINUOUS_COMMIT_PROFILES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static long ledgerSequence;
    private static long archiveSequence;
    private static long lastBatchNanos;
    private static final Map<Path, String> WRITTEN_RAW = new HashMap<>();
    private static void inspectCapture034(Path output, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService service,
        PhantomBackgroundService background) throws Exception
    {
        final var text = new StringBuilder("read=READ_ONLY_CAPTURE_ON_SEALED_EXACT_OWNERS\n");
        final var authority = (org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority) field(background.getClass(), "_authority").get(background);
        final var goals = (org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore) field(background.getClass(), "_goals").get(background);
        final var capabilityMethod = authority.getClass().getDeclaredMethod("capability", Player.class, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.class);
        capabilityMethod.setAccessible(true);
        final var currentTracking = authority.getClass().getDeclaredMethod("tracking", Player.class, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.class, capabilityMethod.getReturnType());
        currentTracking.setAccessible(true);
        final var previousTracking = authority.getClass().getDeclaredMethod("tracking", Player.class, java.util.List.class);
        previousTracking.setAccessible(true);
        final var lifecycleSpec = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.class.getDeclaredMethod("parseLifecycle", org.l2jmobius.gameserver.phantoms.decision.PhantomGoal.class);
        lifecycleSpec.setAccessible(true);
        for (var entry : service.snapshot().materializations())
        {
            final Player player = World.getInstance().getPlayer(entry.characterObjectId());
            if (entry.state() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State.FAILED || player == null
                || player.getClient() != null || !(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner)
                || !owner.isCurrent() || owner.epoch() != entry.materializedAtNanos() || !owner.sealed()
                || owner.outstanding() != 0 || owner.pendingTimers() != 0 || player.hasPendingOwnedStore()) { continue; }
            final var goal = goals.load(entry.profileId()).orElseThrow().goal();
            final var previous = background.acquisitionSnapshot(entry.profileId()).orElse(null);
            final var spec = (org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec) lifecycleSpec.invoke(null, goal);
            final var capability = capabilityMethod.invoke(authority, player, spec);
            final var tracking = previous != null && (previous.state() == PhantomBackgroundState.State.READY || previous.state() == PhantomBackgroundState.State.DEAD)
                ? previousTracking.invoke(authority, player, previous.inventory().mutableItemIds()) : currentTracking.invoke(authority, player, spec, capability);
            final var idsMethod = tracking.getClass().getDeclaredMethod("mutableItemIds"); idsMethod.setAccessible(true);
            final var objectsMethod = tracking.getClass().getDeclaredMethod("objects"); objectsMethod.setAccessible(true);
            final var ids = (java.util.List<?>) idsMethod.invoke(tracking);
            final var objects = (java.util.List<PhantomBackgroundState.ItemObject>) objectsMethod.invoke(tracking);
            text.append("profile=").append(entry.profileId()).append(" object=").append(entry.characterObjectId()).append(" epoch=").append(owner.epoch())
                .append(" goal=").append(goal).append(" previousState=").append(previous == null ? "ABSENT" : previous.state())
                .append(" mutableCount=").append(ids.size()).append(" objectsCount=").append(objects.size())
                .append(" trackedInventory=").append(objects.stream().filter(item -> item.location() == PhantomBackgroundState.ItemLocation.INVENTORY).count())
                .append(" trackedPaperdoll=").append(objects.stream().filter(item -> item.location() == PhantomBackgroundState.ItemLocation.PAPERDOLL).count())
                .append(" usedSlots=").append(player.getInventory().getSize()).append(" maximumSlots=").append(player.getInventoryLimit())
                .append(" currentLoad=").append(player.getCurrentLoad()).append(" maximumLoad=").append(player.getMaxLoad()).append('\n');
            try { authority.captureOwnedNative(entry.profileId(), player, goal, previous); text.append("capture=ACCEPTED\n"); }
            catch (RuntimeException failure) { text.append("capture=").append(failure.getClass().getName()).append(':').append(failure.getMessage()).append('\n'); }
        }
        writeTelemetry(output, text.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static long lastGlobal034;
    private static int globalSamples034;
    private static void sampleGlobal034() throws Exception
    {
        if (streamRoot == null || selection == null || globalSamples034 >= 2048) { return; }
        final long now = System.nanoTime();
        final var system = (org.l2jmobius.gameserver.phantoms.PhantomSystem) field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
        if (system == null) { return; }
        final var progress = system.shutdownProgress();
        if (now - lastGlobal034 < (progress == null ? 1_000_000_000L : 100_000_000L)) { return; }
        lastGlobal034 = now;
        final var service = (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService) field(system.getClass(), "_materializationService").get(system);
        if (service == null) { return; }
        writeGlobal034(streamRoot.resolve("global034-" + (++globalSamples034) + ".txt"), system, service);
    }
    private static void writeGlobal034(Path output, org.l2jmobius.gameserver.phantoms.PhantomSystem system,
        org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService service) throws Exception
    {
        final var snapshot = service.snapshot();
        final var text = new StringBuilder("utc=" + java.time.Instant.now() + "\npid=" + ProcessHandle.current().pid()
            + "\ncodeSha=" + selection.sha() + "\nread=NONATOMIC_READ_ONLY\nprogress=" + system.shutdownProgress()
            + "\nblockers=" + service.cleanupBlockers() + "\nsnapshot=" + snapshot + "\n");
        final var background = (PhantomBackgroundService) field(system.getClass(), "_backgroundService").get(system);
        text.append("background=").append(background.snapshot()).append('\n');
        text.append("quiescence=").append(background.materializationQuiescence()).append('\n');
        final var active = (Map<?, ?>) field(service.getClass(), "_activeByProfile").get(service);
        for (var entry : snapshot.materializations())
        {
            final Object live = active.get(entry.profileId());
            if (live == null) { continue; }
            final var actor = (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer) field(live.getClass(), "_materializedPlayer").get(live);
            final Player player = actor.getPlayer();
            text.append("owner=").append(entry.profileId()).append('/').append(entry.characterObjectId()).append('/').append(entry.materializedAtNanos())
                .append(" identity=").append(org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(entry.characterObjectId()))
                .append(" autoSave=").append(player != null && org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().contains(player))
                .append(" pendingStore=").append(player != null && player.hasPendingOwnedStore());
            if (player != null)
            {
                text.append(" native=").append(player.getNativeWorkOwner()).append(" client=").append(player.getClient() == null ? "NONE" : "REAL")
                    .append(" totalItems=").append(player.getInventory().getSize()).append(" nonQuestItems=").append(player.getInventory().getNonQuestSize())
                    .append(" inventoryLimit=").append(player.getInventoryLimit()).append(" load=").append(player.getCurrentLoad()).append(" maxLoad=").append(player.getMaxLoad());
                if (player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner) { text.append(" scope=").append(owner.diagnosticScalars()); }
            }
            text.append('\n');
        }
        writeTelemetry(output, text.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static final Map<Long, Long> PARENT_EPOCHS = new java.util.concurrent.ConcurrentHashMap<>();
    private static void proofFailure(String value)
    {
        if (FIRST_FAILURE.compareAndSet("", value)) { exporterFailure = value; }
    }
    private static volatile Map<Long, PhantomNativeWorkScope> dispatchOwners = Map.of();
    private record Lifetime(long profileId, int objectId, long epoch) { }
    private static final Map<Lifetime, PhantomNativeWorkScope> RECEIPT_OWNERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Lifetime, Long> REGISTERED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Lifetime, String> LAST_RECEIPT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<Lifetime> TERMINAL = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void enroll(long profile, PhantomNativeWorkScope owner, boolean nativeBirthBoundary)
    {
        if (profile <= 0 || owner == null || owner.player().getObjectId() <= 0 || owner.epoch() <= 0) { throw new IllegalStateException("TASK032_REGISTER_IDENTITY"); }
        final var key = new Lifetime(profile, owner.player().getObjectId(), owner.epoch());
        final var existing = RECEIPT_OWNERS.get(key);
        if (existing != null)
        {
            if (existing != owner) { throw new IllegalStateException("TASK032_REGISTER_CONFLICT"); }
            return;
        }
        if (RECEIPT_OWNERS.size() >= 128) { throw new IllegalStateException("TASK032_ACTIVE_CAPACITY"); }
        if (!owner.isCurrent() || owner.player().getClient() != null) { throw new IllegalStateException("TASK032_REGISTER_OWNER_CHANGED"); }
        long parentEpoch = PARENT_EPOCHS.getOrDefault(profile, 0L);
        for (var prior : RECEIPT_OWNERS.keySet())
        {
            if (prior.profileId() == profile && prior.epoch() != key.epoch())
            {
                if (!terminalFacts(RECEIPT_OWNERS.get(prior))) { throw new IllegalStateException("TASK032_PARENT_NOT_TERMINAL"); }
                parentEpoch = Math.max(parentEpoch, prior.epoch());
            }
        }
        final long now = System.nanoTime();
        if (ACTIVE_REFERENCES.incrementAndGet() > 128) { ACTIVE_REFERENCES.decrementAndGet(); throw new IllegalStateException("TASK032_ACTIVE_CAPACITY"); }
        final var raced = RECEIPT_OWNERS.putIfAbsent(key, owner);
        if (raced != null) { ACTIVE_REFERENCES.decrementAndGet(); if (raced != owner) { throw new IllegalStateException("TASK032_REGISTER_CONFLICT"); } return; }
        REGISTERED.put(key, now);
        COMMIT_PROFILES.add(profile);
        final var chosen = selection;
        enqueue(new Witness(chosen.output().resolve(profile + "-" + key.epoch() + "-register.properties"),
            "kind=REGISTER\nprofileId=" + profile + "\nobjectId=" + key.objectId() + "\nepoch=" + key.epoch()
                + "\nparentEpoch=" + parentEpoch + "\nregisterNanos=" + now + "\nsource=actual-native-owner\n"
                + "initialX=" + owner.player().getX() + "\ninitialY=" + owner.player().getY() + "\ninitialZ=" + owner.player().getZ() + "\ninitialInstance=" + owner.player().getInstanceId() + "\ninitialExp=" + owner.player().getExp() + "\ninitialSp=" + owner.player().getSp() + "\ninitialAdena=" + owner.player().getAdena() + "\ninitialScrolls=" + owner.player().getInventory().getInventoryItemCount(736,-1) + "\nnativeBirthBoundary=" + nativeBirthBoundary + "\nworldAbsentAtRegister=" + (World.getInstance().findObject(key.objectId()) == null && World.getInstance().getPlayer(key.objectId()) == null)
                + "\nparentTerminalVerified=" + (parentEpoch > 0) + "\nownerCurrentAtRegister=" + owner.isCurrent() + "\n" + inventory035(profile, owner.player()) + nativeLoad044(owner.player())));
    }
    private static void enqueue(Witness witness)
    {
        if (streamRoot == null) { throw new IllegalStateException("TASK032_STREAM_NOT_STARTED"); }
        final long bytes = witness.text().getBytes(StandardCharsets.UTF_8).length;
        if (bytes > 2 * 1024 * 1024 || BUFFER_BYTES.addAndGet(bytes) > 16 * 1024 * 1024)
        { BUFFER_BYTES.addAndGet(-bytes); OVERFLOW.incrementAndGet(); proofFailure("TASK032_BUFFER_BUDGET"); throw new IllegalStateException("TASK032_BUFFER_BUDGET"); }
        final var routed = new Witness(streamRoot.resolve(witness.path().getFileName()), witness.text(), witness.sqlObjectId());
        if (!RING.offer(routed))
        { BUFFER_BYTES.addAndGet(-bytes); OVERFLOW.incrementAndGet(); proofFailure("TASK032_WITNESS_RING_OVERFLOW"); throw new IllegalStateException("TASK032_WITNESS_RING_OVERFLOW"); }
        CAPTURED.incrementAndGet();
    }
    private static boolean terminalFacts(PhantomNativeWorkScope owner)
    {
        if (owner == null) { return false; }
        final var facts = owner.diagnosticScalars();
        return "DETACHED".equals(facts.get("nativeOwnerState")) && "true".equals(facts.get("nativeOwnerPermanentSeal"))
            && "0".equals(facts.get("nativeOwnerOutstanding")) && "0".equals(facts.get("nativeOwnerPendingTimers"));
    }
    private static void sampleTerminals()
    {
        final var chosen = selection;
        if (chosen == null) { return; }
        for (var entry : RECEIPT_OWNERS.entrySet())
        {
            if (TERMINAL.contains(entry.getKey())) { continue; }
            final var facts = entry.getValue().diagnosticScalars();
            final var terminalPlayer = entry.getValue().player();
            final boolean worldAbsent = World.getInstance().findObject(entry.getKey().objectId()) == null && World.getInstance().getPlayer(entry.getKey().objectId()) == null;
            final boolean autoSaveAbsent = !org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().contains(terminalPlayer);
            final boolean leaseAbsent = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(entry.getKey().objectId()) == null;
            if ("DETACHED".equals(facts.get("nativeOwnerState")) && "true".equals(facts.get("nativeOwnerPermanentSeal"))
                && "0".equals(facts.get("nativeOwnerOutstanding")) && "0".equals(facts.get("nativeOwnerPendingTimers"))
                && worldAbsent && autoSaveAbsent && leaseAbsent && TERMINAL.add(entry.getKey()))
            {
                final var key = entry.getKey();
                final var text = new StringBuilder("kind=TERMINAL\nprofileId=" + key.profileId() + "\nobjectId=" + key.objectId()
                    + "\nepoch=" + key.epoch() + "\nterminalNanos=" + System.nanoTime() + "\nreceiptSha256=" + LAST_RECEIPT.getOrDefault(key, "") + "\n");
                facts.forEach((name, value) -> put(text, name, value));
                put(text, "terminalOutstanding", facts.get("nativeOwnerOutstanding")); put(text, "terminalPendingTimers", facts.get("nativeOwnerPendingTimers"));
                put(text, "worldAbsent", worldAbsent); put(text, "autoSaveAbsent", autoSaveAbsent); put(text, "leaseAbsent", leaseAbsent);
                text.append(inventory035(key.profileId(), terminalPlayer));
                enqueue(new Witness(chosen.output().resolve(key.profileId() + "-" + key.epoch() + "-terminal.properties"), text.toString()));
            }
        }
    }
    private static final Map<Long, String> dispatchLast = new HashMap<>();
    private static int dispatchLines;
    private static long dispatchSampleNanos;
    private static int targetLines;
    private static long targetSampleNanos;
    private static org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService fullMaterialization;
    private static org.l2jmobius.gameserver.phantoms.PhantomScheduler fullScheduler044;
    private static org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay fullAutoPlay;
    private static org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine fullDecision;
    private static PhantomBackgroundService fullBackground;
    private static org.l2jmobius.gameserver.phantoms.PhantomSystem lifecycleSystem;
    private static String lifecycleLast = "";
    private static String deathLast = "";
    private static int deathLines;
    private static final Map<Long, org.l2jmobius.gameserver.phantoms.decision.PhantomGoal> fullGoals = new HashMap<>();
    private static long fullSampleNanos;
    private static int fullLines;
    private static boolean installed;
    private static String observerRunId = "";
    private static volatile boolean arrivalPoseEnabled;
    private static Object syntheticService;
    private static Field syntheticRun;
    private static Field syntheticState;
    private static org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl locality;
    private static Map<Long, Long> lastHuman = Map.of();
    private record Recovery(long profile, PhantomBackgroundState state, Selection chosen, java.util.concurrent.CountDownLatch release) { }
    private static final ArrayBlockingQueue<Recovery> RECOVERIES = new ArrayBlockingQueue<>(8);
    private static volatile Recovery waitingRecovery;
    private static final Field BOUNDARY = field(Player.class, "_ownedStoreBoundary");
    private static final Field THREAD = field(PhantomNativeWorkScope.class, "_checkpointThread");
    private static final Field STATE = field(PhantomNativeWorkScope.class, "_state");
    private static Field intentField;
    private static Field field(Class<?> type, String name)
    {
        try { var value = type.getDeclaredField(name); value.setAccessible(true); return value; }
        catch (Exception failure) { throw new IllegalStateException(name, failure); }
    }
    private static long lastTravel033R;
    private static void sampleTravel033R() throws Exception
    {
        final long now=System.nanoTime();
        if(streamRoot==null || now-lastTravel033R<100_000_000L){return;}
        lastTravel033R=now;
        final var rows=new StringBuilder();
        for(var entry:RECEIPT_OWNERS.entrySet())
        {
            final var key=entry.getKey(); final var owner=entry.getValue(); final var player=owner.player();
            if(player.getNativeWorkOwner()!=owner || !owner.isCurrent() || World.getInstance().getPlayer(key.objectId())!=player){continue;}
            final var evidence=owner.evidence().snapshot().scalarMap();
            rows.append(now).append('\t').append(key.profileId()).append('\t').append(key.objectId()).append('\t').append(key.epoch())
                .append('\t').append(player.getX()).append('\t').append(player.getY()).append('\t').append(player.getZ()).append('\t').append(player.getInstanceId())
                .append('\t').append(player.isMoving()).append('\t').append(player.isCastingNow()).append('\t').append(player.isTeleporting())
                .append('\t').append(player.hasAI()?player.getAI().getIntention():"NO_AI")
                .append('\t').append(player.getXdestination()).append('\t').append(player.getYdestination())
                .append('\t').append(player.getExp()).append('\t').append(player.getSp()).append('\t').append(player.getAdena())
                .append('\t').append(player.getInventory().getInventoryItemCount(736,-1))
                .append('\t').append(evidence.get("nativePhase")).append('\t').append(evidence.get("nativeFarmCycleSequence"))
                .append('\t').append(evidence.get("nativeKillSequence")).append('\t').append(evidence.get("nativeRewardSequence"))
                .append('\t').append(evidence.get("nativeTargetSequence")).append('\t').append(evidence.get("nativeFirstUnprovenReason")).append('\n');
        }
        if(rows.length()>0){writeTelemetry(streamRoot.resolve("travel-033r.tsv"),rows.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    }
    private static boolean startupEntry;
    public static void premain(String argument, Instrumentation instrumentation) throws Exception
    {
        startupEntry = true;
        try { agentmain(argument, instrumentation); }
        finally { startupEntry = false; }
    }
    public static synchronized void agentmain(String argument, Instrumentation instrumentation) throws Exception
    {
        final Path specPath = Path.of(argument).toRealPath();
        final Properties spec = new Properties();
        try (var reader = Files.newBufferedReader(specPath, StandardCharsets.UTF_8)) { spec.load(reader); }
        final Path runtime = Path.of(spec.getProperty("runtime")).toRealPath();
        if (!runtime.toString().matches(".*[\\\\/]contract044[ab][\\\\/]runtime") || !Path.of("").toRealPath().equals(runtime.resolve("game"))) { throw new IllegalStateException("TASK032_RUNTIME_GUARD"); }
        if (!specPath.startsWith(runtime.getParent()) || (!startupEntry && !spec.getProperty("pid").equals(Long.toString(ProcessHandle.current().pid()))) || !"TASK044_CONTRACT".equals(spec.getProperty("owner"))) { throw new IllegalStateException("TASK032_PROCESS_GUARD"); }
        final var start = ProcessHandle.current().info().startInstant().orElseThrow();
        final long ticks = 621355968000000000L + start.getEpochSecond() * 10000000L + start.getNano() / 100;
        if (!startupEntry && ticks / 10000 != Long.parseLong(spec.getProperty("startTicks")) / 10000) { throw new IllegalStateException("TASK032_START_GUARD"); }
        final Path output = Path.of(spec.getProperty("output")).toAbsolutePath().normalize();
        final Path module = runtime.getParent().getParent().getParent();
        if (!output.startsWith(module.resolve("docs/phantoms/tasks/PHANTOM-M1-G4-CAUSAL-044/evidence"))) { throw new IllegalStateException("TASK032_OUTPUT_GUARD"); }
        Files.createDirectories(output);
        if (witnessDatabase.isEmpty())
        {
            final String expected = "l2jmobiush5_phantom_test_" + runtime.getParent().getFileName().toString().substring("contract".length());
            final String url = Files.readAllLines(runtime.resolve("game/config/Database.ini"), StandardCharsets.UTF_8).stream().filter(line -> line.trim().startsWith("URL")).findFirst().orElseThrow().split("=", 2)[1].trim();
            if (!url.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + expected + "\\?.*")) { throw new IllegalStateException("TASK039_COMMIT_DB_AUTHORITY"); }
            witnessDatabase = expected; witnessJarSha = PhantomBackgroundTransaction.payloadDigest(Files.readAllBytes(runtime.resolve("libs/GameServer.jar")));
        }
        if ("FLUSH".equals(spec.getProperty("mode"))) { drain(); status(output); return; }
        if ("STOP_MONITOR".equals(spec.getProperty("mode")))
        {
            if (selection == null || !selection.sha().equals(spec.getProperty("codeSha"))) { throw new IllegalStateException("TASK032_STOP_SOURCE_GUARD"); }
            final var system = (org.l2jmobius.gameserver.phantoms.PhantomSystem) field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
            if (system == null) { throw new IllegalStateException("TASK032_STOP_SYSTEM_REQUIRED"); }
            final var monitor = new Thread(() ->
            {
                org.l2jmobius.gameserver.phantoms.PhantomSystem.StopProgress prior = null;
                final var samples = new StringBuilder();
                int changes = 0; boolean pollFinal = false;
                final long deadline = System.nanoTime() + 25_000_000_000L;
                try
                {
                    while (System.nanoTime() < deadline)
                    {
                        final var progress = system.shutdownProgress();
                        if (progress != null && !progress.equals(prior))
                        {
                            if (++changes > 32) { throw new IllegalStateException("TASK032_STOP_PHASE_BOUND"); }
                            final long captured = System.nanoTime();
                            final var pools = new StringBuilder();
                            for (String pool : java.util.List.of("SCHEDULED_POOL", "INSTANT_POOL", "HIGH_PRIORITY_SCHEDULED_POOL"))
                            {
                                final var executor = (java.util.concurrent.ThreadPoolExecutor) field(org.l2jmobius.commons.threads.ThreadPool.class, pool).get(null);
                                put(pools, pool + "Shutdown", executor == null ? "ABSENT" : executor.isShutdown());
                            }
                            samples.append(captured).append('\t').append(progress).append('\t').append(pools.toString().replace('\n', ';')).append('\n');
                            prior = progress; pollFinal = progress.toString().contains("phase=FINISH_DEPENDENCIES");
                            if (progress.toString().contains("phase=DONE"))
                            {
                                final var done = new StringBuilder("capturedNanos=" + captured + "\nprogress=" + progress + "\n" + pools);
                                final var materialization = (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService) field(system.getClass(), "_materializationService").get(system);
                                final var background = (PhantomBackgroundService) field(system.getClass(), "_backgroundService").get(system);
                                put(done, "materialization", materialization == null ? "ABSENT" : materialization.shutdownSnapshot());
                                put(done, "background", background == null ? "ABSENT" : background.snapshot());
                                put(done, "configured", org.l2jmobius.gameserver.phantoms.PhantomSystem.configuredShutdownSnapshot());
                                put(done, "activeReferences", RECEIPT_OWNERS.size()); put(done, "proofFailure", exporterFailure);
                                writeTelemetry(output.resolve("typed-stop-progress.tsv"), samples.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                                writeTelemetry(output.resolve("typed-stop-done.properties"), done.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                                return;
                            }
                        }
                        // Narrow final-phase polling has no filesystem/SQL or actor lock.
                        if (pollFinal) { Thread.onSpinWait(); }
                        else { Thread.sleep(1); }
                    }
                    writeTelemetry(output.resolve("typed-stop-progress.tsv"), samples.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                    proofFailure("TASK032_STOP_MONITOR_TIMEOUT");
                }
                catch (Throwable failure) { proofFailure("TASK032_STOP_MONITOR:" + failure); }
            }, "TASK032-stop-monitor");
            monitor.setDaemon(true); monitor.start();
            return;
        }
        final String mode = spec.getProperty("mode");
        if (mode.startsWith("CRASH") || mode.equals("STARTUP_RECOVERY")) { throw new IllegalStateException("TASK044_NORMAL_ONLY"); }
        if (mode.startsWith("CRASH_") || mode.equals("STARTUP_RECOVERY"))
        {
            final String lane = runtime.getParent().getFileName().toString();
            if (!java.util.Set.of("contract044x72", "contract044x73").contains(lane)
                || mode.equals("CRASH_NATIVE") && !lane.equals("contract044x72")
                || mode.equals("CRASH_FINALIZE") && !lane.equals("contract044x73")) { throw new IllegalStateException("TASK044_CRASH_SEPARATE_OWN_LANE_REQUIRED"); }
            if (!spec.getProperty("database", "").equals("l2jmobiush5_phantom_test_" + lane.substring("contract".length()))) { throw new IllegalStateException("TASK044_CRASH_SCHEMA_GUARD"); }
        }
        if (!java.util.Set.of("OBSERVE", "FULL_OBSERVE", "PROBE032", "CENSUS", "CRASH_NATIVE", "CRASH_FINALIZE").contains(mode) && !(startupEntry && java.util.Set.of("STARTUP", "STARTUP_RECOVERY").contains(mode))) { throw new IllegalStateException("TASK032_MODE_GUARD"); }
        final Map<Long, Long> selected = new HashMap<>();
        for (String key : spec.stringPropertyNames()) { if (key.startsWith("profile.")) { selected.put(Long.parseLong(key.substring(8)), Long.parseLong(spec.getProperty(key))); } }
        if (selected.isEmpty() || selected.size() > 8) { throw new IllegalStateException("TASK032_COHORT_GUARD"); }
        // Attach preflight is outside native hooks/critical sections.

        if (selection != null && !selection.sha().equals(spec.getProperty("codeSha"))) { throw new IllegalStateException("TASK032_RUN_SOURCE_CHANGED"); }
        if (streamRoot == null)
        {
            streamRoot = output;
            lifetimeRunId = ProcessHandle.current().pid() + "-" + ticks + "-" + spec.getProperty("codeSha") + "-" + java.util.UUID.randomUUID();
            appendLedger(Map.of("kind", jsonString("RUN_START"), "code_sha", jsonString(spec.getProperty("codeSha")), "incarnation", jsonString(ProcessHandle.current().pid() + ":" + ticks)));
        }
        final Selection nextSelection = new Selection(Map.copyOf(selected), output, spec.getProperty("codeSha"));
        if (startupEntry && java.util.Set.of("STARTUP", "STARTUP_RECOVERY").contains(mode))
        {
            selection = nextSelection;
            // Exact previous cohort is subscribed before boot producers; no synthetic versions are inserted.
            CONTINUOUS_COMMIT_PROFILES.addAll(selected.keySet());
            if(mode.equals("STARTUP_RECOVERY"))
            {
                if(!java.util.Set.of("contract044x72", "contract044x73").contains(runtime.getParent().getFileName().toString()) || selected.size()!=1){throw new IllegalStateException("TASK032_RECOVERY_LANE_GUARD");}
                field(PhantomBackgroundService.class, "_recoveryObserver").set(null, (BiConsumer<Long, PhantomBackgroundState>) Contract032Observer::recovery);
            }
            installHooks();
            write(output.resolve("startup-capture.properties"), "owner=TASK044_CONTRACT\nsource=planned-premain\nrunId=" + lifetimeRunId + "\npid=" + ProcessHandle.current().pid() + "\nstartTicks=" + ticks + "\npreexistingScopes=0\n");
            return;
        }
        for (Player player : World.getInstance().getPlayers()) { if (player.getClient() != null) { throw new IllegalStateException("TASK032_REAL_PRESENT"); } }
        lifecycleSystem = (org.l2jmobius.gameserver.phantoms.PhantomSystem) field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
        if (mode.equals("FULL_OBSERVE") || mode.equals("PROBE032"))
        {
            // Separate bounded export window per explicit session; native scopes/counters remain unchanged.
            fullLines = 0; fullSampleNanos = 0; fullGoals.clear();
            if (!runtime.toString().matches(".*[\\\\/]contract044[ab][\\\\/]runtime") || selected.size() < (mode.equals("PROBE032") ? 1 : 4) || selected.size() > 8) { throw new IllegalStateException("TASK032_FULL_COHORT_LANE_GUARD"); }
            final var configured = field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
            fullMaterialization = (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService) field(configured.getClass(), "_materializationService").get(configured);
            fullScheduler044 = (org.l2jmobius.gameserver.phantoms.PhantomScheduler) field(configured.getClass(), "_scheduler").get(configured);
            fullAutoPlay = (org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay) field(configured.getClass(), "_visibleAutoPlay").get(configured);
            fullDecision = (org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine) field(configured.getClass(), "_decisionEngine").get(configured);
            fullBackground = (PhantomBackgroundService) field(configured.getClass(), "_backgroundService").get(configured);
            observerRunId = spec.getProperty("observerRunId", ""); arrivalPoseEnabled = spec.containsKey("endpoint.x");
            if (!observerRunId.matches("[0-9a-f-]{36}")) { throw new IllegalStateException("TASK032_OBSERVER_RUN_GUARD"); }
            syntheticService = field(org.l2jmobius.gameserver.localplay.LocalPlayPilotService.class, "_synthetic").get(org.l2jmobius.gameserver.localplay.LocalPlayPilotService.getInstance());
            syntheticRun = field(syntheticService.getClass(), "_runId");
            syntheticState = field(syntheticService.getClass(), "_state");
            if (!observerRunId.equals(syntheticRun.get(syntheticService)) || !"RUNNING".equals(syntheticState.get(syntheticService))) { throw new IllegalStateException("TASK032_NATIVE_SESSION_GUARD"); }
            locality = (org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl) field(configured.getClass(), "_humanLocality").get(configured);
            final var retention = field(configured.getClass(), "_materializationRetention").get(configured);
            lastHuman = (Map<Long, Long>) field(retention.getClass(), "_lastHuman").get(retention);
            final var entries = fullMaterialization.snapshot().materializations();
            for (long profile : selected.keySet())
            {
                final var entry = entries.stream().filter(value -> value.profileId() == profile).findFirst().orElse(null);
                if (!(mode.equals("PROBE032") && selected.get(profile) == 0) && (entry == null || entry.materializedAtNanos() != selected.get(profile))) { throw new IllegalStateException("TASK032_ENROLLED_EPOCH_CHANGED:" + profile); }
            }
            // One initial binding; absent profiles remain epoch0 and are never replaced by later incarnations.
            CONTINUOUS_COMMIT_PROFILES.addAll(selected.keySet());
        }
        if (mode.equals("CENSUS"))
        {
            final var players = new StringBuilder("objectId\tonline\tclient\theadless\towner\tidentity\tx\ty\tz\tinstanceId\tinvisible\n");
            for (Player actor : World.getInstance().getPlayers())
            {
                players.append(actor.getObjectId()).append('\t').append(actor.isOnline()).append('\t').append(actor.getClient() == null ? "none" : "REAL")
                    .append('\t').append(actor.hasHeadlessOutboundSession()).append('\t').append(actor.getNativeWorkOwner())
                    .append('\t').append(org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(actor.getObjectId()))
                    .append('\t').append(actor.getX()).append('\t').append(actor.getY()).append('\t').append(actor.getZ())
                    .append('\t').append(actor.getInstanceId()).append('\t').append(actor.isInvisible()).append('\n');
            }
            write(output.resolve("world-identities.tsv"), players.toString());
            final var configured = field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
            final var materialization = (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService) field(configured.getClass(), "_materializationService").get(configured);
            final var entries = materialization.snapshot().materializations();
            write(output.resolve("all-materializations.txt"), entries.toString());
            writeGlobal034(output.resolve("global034-census.txt"), (org.l2jmobius.gameserver.phantoms.PhantomSystem) configured, materialization);
            final var auto = (org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay) field(configured.getClass(), "_visibleAutoPlay").get(configured);
            final var history = (org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService) field(configured.getClass(), "_historicalBackgroundService").get(configured);
            final var decisions = (org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine) field(configured.getClass(), "_decisionEngine").get(configured);
            final var topology = (org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService) field(configured.getClass(), "_topologyService").get(configured);
            final var globalBackground = (PhantomBackgroundService) field(configured.getClass(), "_backgroundService").get(configured);
            inspectCapture034(output.resolve("capture034-census.txt"), materialization, globalBackground);
            final var ecology044 = field(configured.getClass(), "_populationEcology").get(configured);
            final var ecologyCatalog044 = (org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog) field(ecology044.getClass(), "_catalog").get(ecology044);
            final var global = new StringBuilder("profileId\tobjectId\tstate\tcalendarOnline\tadmitted\tx\ty\tz\tcontextPhase\tfarmAllowed\treadinessComplete\tfirstGuard\tsampleNanos\tpopulationState\tinstanceId\tcommittedCursorMinute\trequestedHorizonMinute\tnextBoundary\tproductive540044\tproductiveForecast044\tproductiveForecastUtcMillis044\n");
            for (var profile : topology.listProfiles())
            {
                final var admission = org.l2jmobius.gameserver.phantoms.PhantomSystem.operatorAdmissionProfile(profile.profileId()).orElse(null);
                final var state = globalBackground.acquisitionSnapshot(profile.profileId()).orElse(null);
                if (admission == null) { continue; }
                final var transactions = (PhantomBackgroundTransaction) field(globalBackground.getClass(), "_transactions").get(globalBackground);
                final var proof = state == null ? null : transactions.nativeContext(profile.profileId(), state.identity().characterObjectId());
                final var context = proof == null ? null : proof.context();
                final boolean farm = context != null && context.permits(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundSimulationPolicy.Operation.FARM,
                    org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.configuredSimulationFingerprint());
                final String guard = admission.admission().reason() + ":" + (admission.readiness() == null ? "NO_READINESS" : admission.readiness().reason())
                    + ":" + (proof == null ? "STATE_ABSENT" : proof.status());
                org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState ecologyStored044 = null;
                synchronized (field(ecology044.getClass(), "_monitor").get(ecology044))
                {
                    final var ecologyEntry044 = ((Map<?, ?>) field(ecology044.getClass(), "_entries").get(ecology044)).get(profile.profileId());
                    if (ecologyEntry044 != null) { ecologyStored044 = (org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState) field(ecologyEntry044.getClass(), "_stored").get(ecologyEntry044); }
                }
                final long forecastUtc044 = System.currentTimeMillis();
                final var forecast044 = new StringBuilder(); boolean productive540044 = ecologyStored044 != null;
                for (int minute044 = 0; minute044 <= 9 && ecologyStored044 != null; minute044++)
                {
                    final long epochMinute044 = Math.floorDiv(forecastUtc044 + minute044 * 60_000L, 60_000L);
                    final boolean productive044 = ecologyCatalog044.productive(ecologyStored044.state(), Math.floorDiv(epochMinute044, ecologyStored044.state().productiveBlockMinutes()));
                    productive540044 &= productive044;
                    if (minute044 != 0) { forecast044.append(','); }
                    forecast044.append(epochMinute044).append(':').append(productive044);
                }
                global.append(profile.profileId()).append('\t').append(state == null ? 0 : state.identity().characterObjectId()).append('\t')
                    .append(state == null ? "ABSENT" : state.state()).append('\t').append(admission.admission().calendarOnline()).append('\t').append(admission.admission().admitted()).append('\t')
                    .append(state == null ? 0 : state.position().x()).append('\t').append(state == null ? 0 : state.position().y()).append('\t').append(state == null ? 0 : state.position().z()).append('\t')
                    .append(context == null ? "UNKNOWN" : context.phase()).append('\t').append(farm).append('\t').append(admission.readiness() != null && admission.readiness().complete()).append('\t')
                    .append(guard).append('\t').append(System.nanoTime()).append('\t').append(admission.admission().populationState()).append('\t').append(state == null ? -1 : state.position().instanceId()).append('\t').append(admission.readiness() == null ? -1 : admission.readiness().committedCursorMinute()).append('\t').append(admission.readiness() == null ? -1 : admission.readiness().requestedHorizonMinute()).append('\t').append(admission.admission().nextBoundary()).append('\t').append(productive540044).append('\t').append(forecast044).append('\t').append(forecastUtc044).append('\n');
            }
            write(output.resolve("global-admission.tsv"), global.toString());
            final var admissions = new StringBuilder("profileId\toperatorAdmission\n");
            for (long profile : selected.keySet().stream().sorted().toList())
            {
                admissions.append(profile).append('\t').append(org.l2jmobius.gameserver.phantoms.PhantomSystem.operatorAdmissionProfile(profile)).append('\n');
                final var fields = new java.util.LinkedHashMap<String, String>();
                fields.put("runtime", decisions.find(profile).toString());
                fields.put("historyPermitsDecision", Boolean.toString(history.permitsDecision(profile)));
                fields.put("visibleRecoveryReason", history.visibleRecoveryReason(profile));
                final var catchups = (org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore) field(history.getClass(), "_store").get(history);
                final var planner = (org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner) field(history.getClass(), "_planner").get(history);
                final var background = (PhantomBackgroundService) field(configured.getClass(), "_backgroundService").get(configured);
                fields.put("catchup", catchups.load(profile).toString());
                fields.put("plannerGeneration", planner.generation().toString());
                final var durable032 = background.acquisitionSnapshot(profile).orElse(null);
                fields.put("background", String.valueOf(durable032));
                if (durable032 != null) {
                    final int object032 = durable032.identity().characterObjectId();
                    fields.put("setup.identityOwner", String.valueOf(org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(object032)));
                    fields.put("setup.worldPresent", Boolean.toString(World.getInstance().findObject(object032) != null));
                    fields.put("setup.autoSave", Boolean.toString(org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.getInstance().containsObjectId(object032)));
                }
                for (String name : java.util.List.of("_visiblePublications", "_visibleEpisodes", "_visibleFailures"))
                {
                    final Object value = ((Map<?, ?>) field(history.getClass(), name).get(history)).get(profile);
                    if (value != null)
                    {
                        for (Field member : value.getClass().getDeclaredFields())
                        {
                            if (java.lang.reflect.Modifier.isStatic(member.getModifiers()) || member.getName().equals("player")) { continue; }
                            member.setAccessible(true);
                            fields.put(name + "." + member.getName(), String.valueOf(member.get(value)));
                        }
                    }
                }
                fields.putAll(auto.snapshotContinuation(profile).scalarMap());
                final var entry = entries.stream().filter(value -> value.profileId() == profile).findFirst().orElse(null);
                final var object = entry == null ? null : World.getInstance().findObject(entry.characterObjectId());
                if (object instanceof Player actor)
                {
                    fields.put("moving", Boolean.toString(actor.isMoving())); fields.put("attacking", Boolean.toString(actor.isAttackingNow())); fields.put("casting", Boolean.toString(actor.isCastingNow()));
                    fields.put("inCombat", Boolean.toString(actor.isInCombat())); fields.put("aiAutoAttacking", Boolean.toString(actor.hasAI() && actor.getAI().isAutoAttacking()));
                    final var target = actor.hasAI() ? actor.getAI().getAttackTarget() : null;
                    fields.put("aiAttackTarget", Integer.toString(target == null ? 0 : target.getObjectId())); fields.put("aiAttackTargetDead", Boolean.toString(target != null && target.isAlikeDead()));
                    if (actor.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner) { fields.putAll(owner.diagnosticScalars()); fields.putAll(owner.evidence().snapshot().scalarMap()); }
                }
                write(output.resolve("continuation-" + profile + ".json"), "{" + fields.entrySet().stream().map(value -> jsonString(value.getKey()) + ":" + jsonString(value.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}\n");
            }
            write(output.resolve("operator-admission.tsv"), admissions.toString());
            if (spec.containsKey("endpoint.x"))
            {
                final var control = (org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl) field(configured.getClass(), "_humanLocality").get(configured);
                final var point = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(Integer.parseInt(spec.getProperty("endpoint.x")), Integer.parseInt(spec.getProperty("endpoint.y")), Integer.parseInt(spec.getProperty("endpoint.z")), 0);
                final var endpoint = new StringBuilder("profileId\tworldPresent\tx\ty\tz\tprewarm\tnativeVisible\n");
                for (long profile : selected.keySet().stream().sorted().toList())
                {
                    final var entry = entries.stream().filter(value -> value.profileId() == profile).findFirst().orElse(null);
                    final var object = entry == null ? null : World.getInstance().findObject(entry.characterObjectId());
                    final var actor = object instanceof Player value ? value : null;
                    endpoint.append(profile).append('\t').append(actor != null).append('\t').append(actor == null ? "none" : actor.getX()).append('\t').append(actor == null ? "none" : actor.getY()).append('\t').append(actor == null ? "none" : actor.getZ()).append('\t').append(control.canPrewarmAt(profile, point)).append('\t').append(actor != null && org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(point, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId()))).append('\n');
                }
                write(output.resolve("endpoint-locality.tsv"), endpoint.toString());
            }
            final var text = new StringBuilder("profileId\tstate\tworldPresent\tepoch\thp\tx\ty\tz\tnative\n");
            for (long profile : selected.keySet().stream().sorted().toList())
            {
                final var entry = entries.stream().filter(value -> value.profileId() == profile).findFirst().orElse(null);
                final var object = entry == null ? null : World.getInstance().findObject(entry.characterObjectId());
                final var player = object instanceof Player value ? value : null;
                text.append(profile).append('\t').append(entry == null ? "ABSENT" : entry.state()).append('\t').append(entry != null && entry.worldPresent()).append('\t').append(entry == null ? 0 : entry.materializedAtNanos());
                if (player == null) { text.append("\tnone\tnone\tnone\tnone\tnone\n"); }
                else
                {
                    final var evidence = player.getNativeWorkOwner().evidence();
                    text.append('\t').append(player.getCurrentHp()).append('\t').append(player.getX()).append('\t').append(player.getY()).append('\t').append(player.getZ()).append('\t').append(evidence.snapshot()).append('\n');
                    final var targets = new StringBuilder("target\tdamaged\tkilled\texp\tsp\tnextSelected\tcounted\n");
                    synchronized (evidence)
                    {
                        final var tracked = (Map<?, ?>) field(evidence.getClass(), "_targets").get(evidence);
                        for (var trackedEntry : tracked.entrySet())
                        {
                            targets.append(trackedEntry.getKey());
                            for (String name : java.util.List.of("_damaged", "_killed", "_exp", "_sp", "_nextSelected", "_counted")) { targets.append('\t').append(field(trackedEntry.getValue().getClass(), name).get(trackedEntry.getValue())); }
                            targets.append('\n');
                        }
                    }
                    write(output.resolve("native-target-states-" + profile + ".tsv"), targets.toString());
                }
            }
            write(output.resolve("exact-selected-membership.tsv"), text.toString());
            return;
        }
        if (mode.startsWith("CRASH_"))
        {
            final String crashLane = mode.equals("CRASH_NATIVE") ? "contract044x72" : "contract044x73";
            if (!runtime.getParent().getFileName().toString().equals(crashLane) || selected.size()!=1 || selected.values().stream().anyMatch(value -> value<=0)) { throw new IllegalStateException("TASK032_SEPARATE_CRASH_LANE"); }
            if (!installed || crash != null || !org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.isEnabled()
                || !org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig.isSyntheticEnabled()) { throw new IllegalStateException("TASK032_CRASH_PREFLIGHT"); }
            final var selectedProfile=selected.entrySet().iterator().next();
            if(RECEIPT_OWNERS.entrySet().stream().noneMatch(value -> value.getKey().profileId()==selectedProfile.getKey() && value.getKey().epoch()==selectedProfile.getValue() && value.getValue().isCurrent() && World.getInstance().getPlayer(value.getKey().objectId())==value.getValue().player())){throw new IllegalStateException("TASK032_CRASH_CURRENT_LIFETIME_REQUIRED");}
            final Path dump = Path.of(spec.getProperty("preDump")).toRealPath();
            if (!dump.startsWith(output) || !PhantomBackgroundTransaction.payloadDigest(Files.readAllBytes(dump)).equals(spec.getProperty("preDumpHash"))) { throw new IllegalStateException("TASK032_CRASH_DUMP_GUARD"); }
            final var configured = field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
            if (configured == null) { throw new IllegalStateException("TASK032_SYSTEM_ABSENT"); }
            final var background = field(configured.getClass(), "_backgroundService").get(configured);
            final var transaction = (PhantomBackgroundTransaction) field(background.getClass(), "_transactions").get(background);
            final Field injector = field(PhantomBackgroundTransaction.class, "_faultInjector");
            final var original = (PhantomBackgroundTransaction.FaultInjector) injector.get(transaction);
            crash = new Crash(mode, output, spec.getProperty("preDumpHash"));
            injector.set(transaction, (PhantomBackgroundTransaction.FaultInjector) point -> { original.inject(point); crashWindow(point); });
        }
        selection = new Selection(Map.copyOf(selected), output, spec.getProperty("codeSha"));
        final Map<Long, PhantomNativeWorkScope> exactOwners = new HashMap<>();
        for (Player player : World.getInstance().getPlayers())
        {
            if (!(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner)) { continue; }
            for (var entry : selected.entrySet()) { if (entry.getValue() == owner.epoch() && owner.player() == player && owner.isCurrent()) { exactOwners.put(entry.getKey(), owner); } }
        }
        dispatchOwners = Map.copyOf(exactOwners);
        for (var entry : exactOwners.entrySet()) { enroll(entry.getKey(), entry.getValue(), false); }
        installHooks();
        write(output.resolve("observer-installed-" + System.nanoTime() + ".properties"), "owner=TASK044_CONTRACT\nmode=" + mode + "\npid=" + ProcessHandle.current().pid() + "\ncodeSha=" + selection.sha() + "\nringCapacity=512\nexactArgument=true\n");
        if (mode.equals("FULL_OBSERVE") || mode.equals("PROBE032")) { sampleFullCohort(); }
    }
    private static synchronized void installHooks() throws Exception
    {
        if (!installed)
        {
            field(PhantomNativeWorkScope.class, "_checkpointObserver").set(null, (BiConsumer<Player, String>) ((player, phase) -> { final long began = System.nanoTime(); try { observe(player, phase); } catch (Throwable failure) { proofFailure("TASK032_PASSIVE_CHECKPOINT_CAPTURE:" + failure); } finally { final long elapsed = System.nanoTime() - began; HOOK_CALLS.incrementAndGet(); HOOK_NANOS.addAndGet(elapsed); HOOK_MAX_NANOS.accumulateAndGet(elapsed, Math::max); } }));
            field(PhantomBackgroundService.class, "_commitObserver").set(null, (BiConsumer<PhantomBackgroundTransaction.Command, PhantomBackgroundTransaction.Result>) Contract032Observer::backgroundCommit);
            field(PhantomBackgroundTransaction.class, "_commitWitnessProfiles").set(null, (java.util.function.LongPredicate) Contract032Observer::watchedCommitProfile);
            field(PhantomBackgroundTransaction.class, "_commitWitnessObserver").set(null, (Consumer<Map<String, String>>) Contract032Observer::transactionCommit039);
            field(org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.class, "_commitWitnessProfiles").set(null, (java.util.function.LongPredicate) Contract032Observer::watchedCommitProfile);
            field(org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.class, "_commitWitnessObserver").set(null, (Consumer<Map<String, String>>) Contract032Observer::transactionCommit039);
            field(PhantomBackgroundService.class, "_projectionObserver").set(null, (BiConsumer<PhantomBackgroundService.OrdinaryProjectionCommit, PhantomBackgroundTransaction.Result>) Contract032Observer::projectionCommit);
            field(PhantomBackgroundService.class, "_nativeLifetimeObserver").set(null, (BiConsumer<Long, Player>) Contract032Observer::nativeBirth);
            final Thread exporter = new Thread(() ->
            {
                while (true)
                {
                    try { if (RING.isEmpty()) { Thread.sleep(100); } sampleTerminals(); if (RING.size() >= 64 || System.nanoTime() - lastBatchNanos >= 1_000_000_000L) { drain(512); } else { Thread.sleep(10); } sampleTravel033R(); sampleDispatch(); sampleNativeTarget(); sampleArrivalPose(); sampleFullCohort(); exportRecovery(); sampleLifecycle(); sampleDeath(); sampleGlobal034(); }
                    catch (InterruptedException stopped) { return; }
                    catch (Exception failure) { proofFailure(failure.toString()); }
                }
            }, "TASK032-witness-export");
            exporter.setDaemon(true); exporter.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
            {
                try { finishStream(); sampleLifecycle(); status(selection.output()); lastGlobal034 = 0; sampleGlobal034(); }
                catch (Exception failure) { proofFailure(failure.toString()); System.err.println("TASK032_WITNESS_EXPORT_FAILED:" + failure); }
            }, "TASK032-witness-final-export"));
            installed = true;
        }
    }
    /** Only scalar copies enter the existing bounded ring; all I/O remains on its exporter. */
    private static boolean watchedCommitProfile(long profile)
    {
        return COMMIT_PROFILES.contains(profile) || CONTINUOUS_COMMIT_PROFILES.contains(profile)
            || RECEIPT_OWNERS.keySet().stream().anyMatch(key -> key.profileId() == profile);
    }
    private static void transactionCommit039(Map<String, String> facts)
    {
        try
        {
            if (!facts.getOrDefault("captureFailure", "").isEmpty()) { proofFailure("TASK039_COMMIT_CAPTURE_GAP:" + facts.get("captureFailure")); }
            if (!facts.containsKey("profileId")) { return; }
            final long profile = Long.parseLong(facts.get("profileId"));
            if (!watchedCommitProfile(profile)) { return; }
            final var chosen = selection; if (chosen == null) { throw new IllegalStateException("TASK039_COMMIT_NO_SELECTION"); }
            final var text = new StringBuilder("kind=TRANSACTION_COMMIT039\nowner=TASK044_CONTRACT\nsource=actual-owning-jdbc-commit\n");
            put(text, "database", witnessDatabase); put(text, "runId", lifetimeRunId); put(text, "sourceSha", chosen.sha()); put(text, "sourceJarSha256", witnessJarSha);
            put(text, "epoch", facts.getOrDefault("nativeEpoch", "0"));
            facts.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> put(text, entry.getKey(), entry.getValue().replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")));
            final String name = "commit039-" + facts.get("transactionSequence") + "-" + facts.get("writeIndex") + "-" + profile + ".properties";
            enqueue(new Witness(chosen.output().resolve(name), text.toString()));
        }
        catch (Throwable failure) { proofFailure("TASK039_COMMIT_EXPORT_GAP:" + failure); }
    }
    private static void nativeBirth(Long profile, Player player)
    {
        NATIVE_BIRTHS.incrementAndGet();
        try
        {
            if (!(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner) || owner.player() != player)
            { throw new IllegalStateException("TASK032_BIRTH_EXACT_OWNER"); }
            enroll(profile, owner, true);
        }
        catch (Throwable failure)
        {
            REFUSED_BIRTHS.incrementAndGet(); proofFailure("TASK032_BIRTH_FAILED:" + failure);
        }
    }

    private static void projectionCommit(PhantomBackgroundService.OrdinaryProjectionCommit event, PhantomBackgroundTransaction.Result result)
    {
        try
        {
            if (!result.successful() || result.state() == null || event.lease().isClosed()) { throw new IllegalStateException("TASK032_PROJECTION_BOUNDARY"); }
            final var codec = new PhantomBackgroundStateCodec();
            final var text = new StringBuilder("kind=PROJECTION_COMMIT\nsource=verified-metadata-only\n");
            final var before = event.before(); final var after = result.state();
            put(text, "profileId", before.identity().profileId()); put(text, "objectId", before.identity().characterObjectId());
            put(text, "beforeRowVersion", event.context().stateRowVersion()); put(text, "codeSha", selection.sha());
            put(text, "beforePayloadHex", java.util.HexFormat.of().formatHex(codec.encode(before)));
            put(text, "afterPayloadHex", java.util.HexFormat.of().formatHex(codec.encode(after)));
            put(text, "beforePayloadSha256", PhantomBackgroundTransaction.payloadDigest(codec.encode(before)));
            put(text, "afterPayloadSha256", PhantomBackgroundTransaction.payloadDigest(codec.encode(after)));
            put(text, "expectedContextHex", java.util.HexFormat.of().formatHex(event.context().encode()));
            put(text, "requiredItemIds", event.projection().requiredItemIds()); put(text, "goalId", event.projection().goal().goalId()); put(text, "goalRevision", event.projection().goal().revision());
            enqueue(new Witness(streamRoot.resolve(before.identity().profileId() + "-projection-" + event.context().stateRowVersion() + ".properties"), text.toString()));
        }
        catch (Throwable failure) { proofFailure("TASK032_PROJECTION_OBSERVER:" + failure); }
    }
    private static void backgroundCommit(PhantomBackgroundTransaction.Command command, PhantomBackgroundTransaction.Result result)
    {
        try
        {
            final var chosen = selection; final var policy = command.policy();
            final long profile = command.expectedState().identity().profileId();
            if (chosen == null || policy == null) { return; }
            final var lease = policy.lease(); final int object = command.expectedState().identity().characterObjectId();
            final var owner = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(object);
            if (!result.successful() || result.state() == null || lease.isClosed() || owner == null || owner.token() != lease.token()
                || owner.ownerKind() != org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND
                || World.getInstance().findObject(object) != null || !result.state().receipt().operationKey().equals(command.operationKey().digest()))
            { throw new IllegalStateException("TASK032_BACKGROUND_BOUNDARY"); }
            final var codec = new PhantomBackgroundStateCodec();
            final byte[] before = codec.encode(command.expectedState()), after = codec.encode(result.state());
            final String digest = PhantomBackgroundTransaction.payloadDigest(after), operation = command.operationKey().digest();
            final boolean replay = command.expectedState().receipt().operationKey().equals(operation);
            final var text = new StringBuilder("kind=" + (replay ? "BACKGROUND_REPLAY" : "BACKGROUND_COMMIT") + "\nsource=verified-typed-operation\ncodeSha=" + chosen.sha() + "\n");
            put(text, "profileId", profile); put(text, "objectId", object); put(text, "hookNanos", System.nanoTime());
            put(text, "operationKey", operation); put(text, "actionKind", command.operationKey().actionKind()); put(text, "ownerKind", owner.ownerKind()); put(text, "leaseToken", lease.token());
            put(text, "worldPresent", false); put(text, "beforeRowVersion", policy.expectedContext().stateRowVersion());
            put(text, "beforePayloadSha256", PhantomBackgroundTransaction.payloadDigest(before)); put(text, "afterPayloadSha256", digest);
            put(text, "beforePayloadHex", java.util.HexFormat.of().formatHex(before)); put(text, "afterPayloadHex", java.util.HexFormat.of().formatHex(after));
            put(text, "expectedContextHex", java.util.HexFormat.of().formatHex(policy.expectedContext().encode()));
            put(text, "afterPolicy", policy.proposedPolicy()); put(text, "beforeExp", command.expectedState().progress().experience()); put(text, "afterExp", result.state().progress().experience());
            put(text, "beforeSp", command.expectedState().progress().skillPoints()); put(text, "afterSp", result.state().progress().skillPoints());
            put(text, "beforeVitality", policy.expectedContext().afterPoints()); put(text, "afterVitality", policy.proposedPolicy().canonicalPoints());
            enqueue(new Witness(chosen.output().resolve(profile + "-bg-" + operation + ".properties"), text.toString()));
        }
        catch (Throwable failure) { proofFailure("TASK032_BACKGROUND_OBSERVER:" + failure); }
    }
    private static void sampleLifecycle() throws Exception
    {
        final var system = lifecycleSystem;
        if (system == null || selection == null) { return; }
        final String value = String.valueOf(system.shutdownProgress());
        if (value.equals(lifecycleLast)) { return; }
        lifecycleLast = value;
        writeTelemetry(selection.output().resolve("lifecycle-progress.tsv"), System.nanoTime() + "\t" + value + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private static void sampleDeath() throws Exception
    {
        if (lifecycleSystem == null || selection == null || deathLines >= 1024) { return; }
        final var service = (org.l2jmobius.gameserver.phantoms.background.PhantomOrdinaryDeathRecovery) field(lifecycleSystem.getClass(), "_ordinaryDeathRecovery").get(lifecycleSystem);
        if (service == null) { return; }
        final var deaths = (Map<?, ?>) field(service.getClass(), "_deaths").get(service);
        final var value = new StringBuilder(service.snapshot().toString());
        for (long profile : selection.profiles().keySet().stream().sorted().toList())
        {
            final var death = deaths.get(profile);
            if (death != null) { value.append(";profile=").append(profile).append(':').append(death); }
        }
        final String text = value.toString();
        if (text.equals(deathLast)) { return; }
        deathLast = text; deathLines++;
        writeTelemetry(selection.output().resolve("native-death-control.tsv"), System.nanoTime() + "\t" + text + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    /** Away arrival pose only: immutable native scalar sample on exporter, no actor telemetry or mailbox. */
    private static void sampleArrivalPose() throws Exception
    {
        if (!arrivalPoseEnabled || fullMaterialization == null || syntheticService == null || fullLines >= 525 || !observerRunId.equals(syntheticRun.get(syntheticService)) || !"RUNNING".equals(syntheticState.get(syntheticService))) { return; }
        final long now = System.nanoTime();
        final var object = World.getInstance().findObject(org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig.syntheticObjectId());
        final var player = object instanceof Player human ? human : null;
        final var row = new java.util.LinkedHashMap<String, String>();
        row.put("runId", jsonString((String) syntheticRun.get(syntheticService))); row.put("sessionState", jsonString((String) syntheticState.get(syntheticService)));
        row.put("objectId", Integer.toString(player == null ? 0 : player.getObjectId())); row.put("present", Boolean.toString(player != null)); row.put("online", Boolean.toString(player != null && player.isOnline()));
        row.put("dead", Boolean.toString(player != null && player.isDead())); row.put("moving", Boolean.toString(player != null && player.isMoving()));
        row.put("x", Integer.toString(player == null ? 0 : player.getX())); row.put("y", Integer.toString(player == null ? 0 : player.getY())); row.put("z", Integer.toString(player == null ? 0 : player.getZ())); row.put("instance", Integer.toString(player == null ? 0 : player.getInstanceId()));
        final String json = "{\"proofFailure\":" + jsonString(exporterFailure) + ",\"sampleNanos\":" + now + ",\"observer\":{" + row.entrySet().stream().map(entry -> jsonString(entry.getKey()) + ":" + entry.getValue()).collect(java.util.stream.Collectors.joining(",")) + "}}\n";
        writeTelemetry(selection.output().resolve("arrival-pose-latest.json.tmp"), json, StandardCharsets.UTF_8);
        replaceFrame(selection.output().resolve("arrival-pose-latest.json.tmp"), selection.output().resolve("arrival-pose-latest.json"));
    }
    /** Exact initial scopes, including actors outside Synthetic surrounding regions. No admission or Player mutation. */
    private static synchronized void sampleFullCohort() throws Exception
    {
        final long now = System.nanoTime();
        if (fullMaterialization == null || fullLines >= 525 || now - fullSampleNanos < 1_000_000_000L) { return; }
        fullSampleNanos = now;
        final var entries = fullMaterialization.snapshot().materializations();
        final var observerObject = World.getInstance().findObject(org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig.syntheticObjectId());
        final var observer = observerObject instanceof Player human ? human : null;
        final var observerPoint = observer == null ? null : new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(observer.getX(), observer.getY(), observer.getZ(), observer.getInstanceId());
        final var rows = new java.util.ArrayList<Map<String, String>>();
        for (var initial : selection.profiles().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
        {
            final long profile = initial.getKey();
            final var entry = entries.stream().filter(value -> value.profileId() == profile).findFirst().orElse(null);
            final var retained = dispatchOwners.get(profile);
            final var object = entry == null ? null : World.getInstance().findObject(entry.characterObjectId());
            final var player = object instanceof Player value ? value : retained == null ? null : retained.player();
            final var row = new java.util.LinkedHashMap<String, String>();
            row.put("profileId", Long.toString(profile)); row.put("materializedAtNanos", Long.toString(entry == null ? initial.getValue() : entry.materializedAtNanos()));
            row.put("worldPresent", Boolean.toString(entry != null && entry.worldPresent())); row.put("missing", Boolean.toString(entry == null || !entry.worldPresent()));
            final var decision = fullDecision.find(profile).orElse(null);
            var goal = fullGoals.get(profile);
            if (goal == null || decision != null && (decision.goalId() != goal.goalId() || decision.goalRevision() != goal.revision()))
            {
                goal = fullBackground.ordinaryGoal(profile).orElse(null);
                if (goal != null) { fullGoals.put(profile, goal); }
            }
            final var farm = goal == null ? null : org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.parse(goal.status() == org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE ? goal : goal.withStatus(org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE));
            row.put("eligible", Boolean.toString(entry != null && entry.actionAdmissionOpen() && farm != null && player != null && !player.isDead()));
            row.put("npcId", Integer.toString(farm == null ? 0 : farm.npcId())); row.put("anchor", farm == null ? "" : farm.anchorId());
            row.put("objectId", Integer.toString(player == null ? 0 : player.getObjectId())); row.put("dead", Boolean.toString(player != null && player.isDead()));
            row.put("cleanupPhase", entry == null ? "ABSENT" : entry.cleanupPhase().name()); row.put("pendingOwnedStore", Boolean.toString(player != null && player.hasPendingOwnedStore()));
            row.put("exp", Long.toString(player == null ? 0 : player.getExp())); row.put("sp", Long.toString(player == null ? 0 : player.getSp()));
            row.put("nativeIsMage", Boolean.toString(player != null && player.getPlayerClass().isMage()));
            final var lastSeen = lastHuman.get(profile);
            row.put("lastHumanNanos", Long.toString(lastSeen == null ? 0 : lastSeen));
            row.put("recentHumanAgeSeconds", Double.toString(lastSeen == null ? -1 : (now - lastSeen) / 1_000_000_000.0));
            row.put("xyzSource", object instanceof Player ? "WORLD_CURRENT" : retained != null ? "RETAINED_OLD_PLAYER" : "ABSENT");
            row.put("observerPrewarm", Boolean.toString(observerPoint != null && locality.canPrewarmAt(profile, observerPoint)));
            row.put("observerNativeVisible", Boolean.toString(object instanceof Player && entry != null && entry.worldPresent() && observerPoint != null && org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(observerPoint, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()))));
            row.put("initialEpoch", Long.toString(initial.getValue()));
            if (retained != null) { row.putAll(retained.evidence().snapshot().scalarMap()); row.putAll(retained.diagnosticScalars()); }
            else { row.put("nativeEvidenceEpoch", "0"); for (String key : java.util.List.of("nativeRewardSequence", "nativeKillSequence", "nativeDamageSequence", "nativeTargetSequence", "nativeFarmCycleSequence", "nativeExpGained", "nativeSpGained")) { row.put(key, "0"); } row.put("nativeEvidenceOverflow", "true"); }
            row.putAll(fullAutoPlay.snapshotContinuation(profile).scalarMap());
            if (player != null && player.getNativeWorkOwner() instanceof PhantomNativeWorkScope current)
            {
                current.evidence().snapshot().scalarMap().forEach((key, value) -> row.put("current." + key, value));
                current.diagnosticScalars().forEach((key, value) -> row.put("current." + key, value));
            }
            row.put("runtimeReason", decision == null ? "runtime.absent" : decision.reasonKey());
            row.put("physicalDemand", Boolean.toString(locality.hasPhysicalDemand(profile)));
            row.put("physicalTimestampMillis", Long.toString(locality.physicalSnapshot().timestampMillis()));
            row.put("physicalPositionRevision", String.valueOf(locality.physicalSnapshot().positionRevisions().get(profile)));
            row.put("operatorAdmission", org.l2jmobius.gameserver.phantoms.PhantomSystem.operatorAdmissionProfile(profile).toString());
            if (selection.profiles().size() == 1)
            {
                row.put("schedulerSignalSources044", fullScheduler044.signalSources(profile).toString());
            }
            row.putAll(org.l2jmobius.gameserver.phantoms.PhantomSystem.operatorReadinessProgress(profile));
            final Object ecology032 = field(lifecycleSystem.getClass(), "_populationEcology").get(lifecycleSystem);
            synchronized (field(ecology032.getClass(), "_monitor").get(ecology032))
            {
                final Object entry032 = ((Map<?, ?>) field(ecology032.getClass(), "_entries").get(ecology032)).get(profile);
                for (String key032 : java.util.List.of("_wakeFailure", "_populationPlanApplied", "_inventoryReady", "_metadataDraining", "_stopping", "_batchAdmission")) { row.put("ecology." + key032, String.valueOf(field(ecology032.getClass(), key032).get(ecology032))); }
                if (entry032 != null) { for (String key032 : java.util.List.of("_participating", "_terminal", "_liveOwner", "_nextRetryPulse", "_materializationDemand", "_claimed", "_requestedMinute", "_readinessRevision", "_lastReportedFailure", "_stage", "_stored", "_historicalSnapshot")) { row.put("ecology.entry." + key032, String.valueOf(field(entry032.getClass(), key032).get(entry032))); } }
            }
            if (player != null)
            {
                row.put("hp", Double.toString(player.getCurrentHp())); row.put("mp", Double.toString(player.getCurrentMp()));
                row.put("x", Integer.toString(player.getX())); row.put("y", Integer.toString(player.getY())); row.put("z", Integer.toString(player.getZ()));
                row.put("moving", Boolean.toString(player.isMoving())); row.put("casting", Boolean.toString(player.isCastingNow())); row.put("attacking", Boolean.toString(player.isAttackingNow()));
                row.put("inCombat", Boolean.toString(player.isInCombat())); row.put("aiAutoAttacking", Boolean.toString(player.hasAI() && player.getAI().isAutoAttacking()));
                final var aiTarget = player.hasAI() ? player.getAI().getAttackTarget() : null;
                row.put("aiAttackTarget", Integer.toString(aiTarget == null ? 0 : aiTarget.getObjectId())); row.put("aiAttackTargetDead", Boolean.toString(aiTarget != null && aiTarget.isAlikeDead()));
                row.put("targetObjectId", Integer.toString(player.getTarget() == null ? 0 : player.getTarget().getObjectId()));
                row.put("aiIntention044", player.hasAI() ? player.getAI().getIntention().name() : "NONE");
                final var incoming044 = World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.actor.Creature.class, 2000).stream()
                    .filter(creature -> creature.isMonster() && !creature.isAlikeDead() && creature.hasAI() && (creature.getAI().getAttackTarget() == player || creature.getTarget() == player))
                    .sorted(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.actor.Creature::getObjectId)).toList();
                row.put("incomingTargetCount044", Integer.toString(incoming044.size()));
                row.put("incomingRoster044", incoming044.stream().limit(8).map(creature -> creature.getObjectId() + ":" + creature.asNpc().getId() + ":" + creature.getCurrentHp() + ":" + creature.isAttackingNow()).collect(java.util.stream.Collectors.joining(",")));
                row.put("incomingRosterScope044", "visible_monsters_with_native_target_within2000;first8;exporter_read_only");
            }
            rows.add(row);
        }
        final String json = "[" + rows.stream().map(row -> "{" + row.entrySet().stream().map(entry -> jsonString(entry.getKey()) + ":" + jsonString(entry.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}").collect(java.util.stream.Collectors.joining(",")) + "]";
        writeTelemetry(selection.output().resolve("full-cohort-latest.json.tmp"), json, StandardCharsets.UTF_8);
        replaceFrame(selection.output().resolve("full-cohort-latest.json.tmp"), selection.output().resolve("full-cohort-latest.json"));
        final String observerJson = "{\"runId\":" + jsonString((String) syntheticRun.get(syntheticService)) + ",\"sessionState\":" + jsonString((String) syntheticState.get(syntheticService)) + ",\"objectId\":" + (observer == null ? 0 : observer.getObjectId()) + ",\"present\":" + (observer != null) + ",\"online\":" + (observer != null && observer.isOnline()) + ",\"moving\":" + (observer != null && observer.isMoving()) + ",\"dead\":" + (observer != null && observer.isDead()) + ",\"x\":" + (observer == null ? 0 : observer.getX()) + ",\"y\":" + (observer == null ? 0 : observer.getY()) + ",\"z\":" + (observer == null ? 0 : observer.getZ()) + ",\"instance\":" + (observer == null ? 0 : observer.getInstanceId()) + ",\"running\":" + (observer != null && observer.isRunning())
            + ",\"moveSpeed\":" + (observer == null ? 0 : observer.getMoveSpeed()) + ",\"runSpeed\":" + (observer == null ? 0 : observer.getRunSpeed())
            + ",\"overloaded\":" + (observer != null && observer.isOverloaded()) + ",\"currentLoad\":" + (observer == null ? 0 : observer.getCurrentLoad()) + "}";
        final String frame = "{\"proofFailure\":" + jsonString(exporterFailure) + ",\"sampleNanos\":" + now + ",\"observer\":" + observerJson + ",\"actors\":" + json + "}\n";
        writeTelemetry(selection.output().resolve("full-frame-latest.json.tmp"), frame, StandardCharsets.UTF_8);
        replaceFrame(selection.output().resolve("full-frame-latest.json.tmp"), selection.output().resolve("full-frame-latest.json"));
        writeTelemetry(selection.output().resolve("full-cohort-samples.jsonl"), frame, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        fullLines++;
    }
    // Windows may briefly hold the replaced destination open. Exporter only; never a native hook.
    private static void replaceFrame(Path source, Path target) throws Exception
    {
        for (int attempt = 0; ; attempt++)
        {
            try { Files.move(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); return; }
            catch (java.nio.file.AccessDeniedException transientReader)
            {
                if (attempt == 4) { throw transientReader; }
                Thread.sleep(5);
            }
        }
    }
    private static String jsonString(String value)
    {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
    private static void recovery(Long profile, PhantomBackgroundState state)
    {
        final var chosen = selection;
        if (!chosen.profiles().containsKey(profile)) { return; }
        if (state.identity().profileId() != profile || Thread.holdsLock(state)) { throw new IllegalStateException("TASK032_RECOVERY_ARGUMENT"); }
        final var release = new java.util.concurrent.CountDownLatch(1);
        if (!RECOVERIES.offer(new Recovery(profile, state, chosen, release))) { OVERFLOW.incrementAndGet(); throw new IllegalStateException("TASK032_RECOVERY_RING_FULL"); }
        try { if (!release.await(45, java.util.concurrent.TimeUnit.SECONDS)) { proofFailure("TASK032_RECOVERY_BARRIER_TIMEOUT"); } }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); proofFailure("TASK032_RECOVERY_INTERRUPTED"); }
    }
    private static void exportRecovery() throws Exception
    {
        if (waitingRecovery == null)
        {
            final var captured = RECOVERIES.poll();
            if (captured == null) { return; }
            waitingRecovery = captured;
            final var state = captured.state(); final var position = state.position();
            final byte[] payload = new PhantomBackgroundStateCodec().encode(state);
            final var text = new StringBuilder("source=RECOVERY_COMMIT_BEFORE_ADMISSION\nexactArgument=true\ntransactionReturned=true\ncodeSha=" + captured.chosen().sha() + "\n");
            put(text,"profileId",captured.profile()); put(text,"objectId",state.identity().characterObjectId()); put(text,"state",state.state());
            put(text,"x",position.x()); put(text,"y",position.y()); put(text,"z",position.z()); put(text,"heading",position.heading());
            put(text,"exp",state.progress().experience()); put(text,"sp",state.progress().skillPoints()); put(text,"inventoryHash",state.inventory().canonicalHash());
            put(text,"fullStateSha256",PhantomBackgroundTransaction.payloadDigest(payload)); put(text,"fullStateBase64",java.util.Base64.getEncoder().encodeToString(payload));
            write(captured.chosen().output().resolve("recovery-sql.tsv"), sqlAtWindow(state.identity().characterObjectId()));
            write(captured.chosen().output().resolve("recovery-commit.properties"), text.toString());
            // The exporter releases only after its exact immutable SQL and receipt have been written.
            captured.release().countDown(); waitingRecovery = null;
        }
        final var waiting = waitingRecovery;
        if (waiting != null && Files.exists(waiting.chosen().output().resolve("release-recovery.signal")))
        {
            waiting.release().countDown(); waitingRecovery = null;
        }
    }
    private static void sampleDispatch() throws Exception
    {
        final long now = System.nanoTime();
        if (now - dispatchSampleNanos < 1_000_000_000L || dispatchLines >= 1024) { return; }
        dispatchSampleNanos = now;
        final var chosen = selection;
        for (var entry : dispatchOwners.entrySet())
        {
            final var fields = entry.getValue().diagnosticScalars();
            final String state = fields.get("nativeOwnerState") + "\t" + fields.getOrDefault("nativeEventDispatch", "UNAVAILABLE") + "\t" + fields.getOrDefault("nativeRecentCompletedEvents", fields.getOrDefault("nativeLastCompletedEvent", "UNAVAILABLE"));
            if (!state.equals(dispatchLast.put(entry.getKey(), state)) && dispatchLines++ < 1024)
            {
                writeTelemetry(chosen.output().resolve("event-dispatch.tsv"), now + "\t" + chosen.sha() + "\t" + entry.getKey() + "\t" + entry.getValue().epoch() + "\t" + state + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
    }
    private static void observe(Player player, String phase)
    {
        try { observeActual(player, phase); }
        catch (Throwable failure) { proofFailure("TASK032_CHECKPOINT_OBSERVER:" + failure); }
    }
    private static void observeActual(Player player, String phase)
    {
        if (phase.equals("CAPTURE"))
        {
            PREPARED.remove();
            try
            {
                final var owner = (PhantomNativeWorkScope) player.getNativeWorkOwner();
                final Object checkpoint = field(PhantomNativeWorkScope.class, "_ownedCheckpoint").get(owner);
                if (checkpoint == null)
                {
                    if (owner.player() != player || !owner.isCurrent() || THREAD.get(owner) != Thread.currentThread()
                        || !STATE.get(owner).toString().equals("SEALED")
                        || RECEIPT_OWNERS.entrySet().stream().filter(value -> value.getValue() == owner
                            && value.getKey().objectId() == player.getObjectId() && value.getKey().epoch() == owner.epoch()).count() != 1)
                    { throw new IllegalStateException("TASK032_GENERIC_CAPTURE_UNREGISTERED"); }
                    return;
                }
                final var key = (PhantomNativeWorkScope.CheckpointKey) field(checkpoint.getClass(), "key").get(checkpoint);
                if (key.objectId() != player.getObjectId() || key.epoch() != owner.epoch()
                    || THREAD.get(owner) != Thread.currentThread() || !STATE.get(owner).toString().equals("SEALED")) { throw new IllegalStateException("TASK032_REGISTER_EXACT_ARGUMENT"); }
                enroll(key.profileId(), owner, false);
            }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("TASK032_REGISTER_KEY", failure); }
        }
        if (!phase.equals("PREPARED") && !phase.equals("FINALIZED")) { return; }
        try
        {
            final var chosen = selection;
            final Object boundary = BOUNDARY.get(player);
            if (boundary == null) { return; }
            if (intentField == null) { intentField = field(boundary.getClass(), "_intent"); }
            final var intent = (PhantomOwnedStoreIntent) intentField.get(boundary);
            if (intent == null) { return; }
            final long id = intent.after().identity().profileId();
            final var owner = player.getNativeWorkOwner();
            final long initialEpoch = intent.materializedAtNanos();
            if (!RECEIPT_OWNERS.containsKey(new Lifetime(id, player.getObjectId(), initialEpoch))) { throw new IllegalStateException("TASK032_RECEIPT_NOT_REGISTERED"); }
            if (!(owner instanceof PhantomNativeWorkScope) || !owner.isCurrent() || owner.player() != player || owner.epoch() != intent.materializedAtNanos()
                || THREAD.get(owner) != Thread.currentThread() || !STATE.get(owner).toString().equals("SEALED") || player.getObjectId() != intent.after().identity().characterObjectId()) { throw new IllegalStateException("TASK032_EXACT_SEALED_ARGUMENT"); }
            final String key = id + "-" + intent.materializedAtNanos() + "-" + intent.preparedRowVersion();
            final boolean finalized = phase.equals("FINALIZED");
            final String eventKey = key + "-" + phase;
            final String digest = PhantomBackgroundTransaction.payloadDigest(intent.encode());
            LAST_RECEIPT.put(new Lifetime(id, player.getObjectId(), initialEpoch), digest);
            final String text = snapshot(intent, player, chosen.sha()) + "enrolledInitialEpoch=" + initialEpoch + "\nhookNanos=" + System.nanoTime() + "\ncheckpointStage=" + phase + "\n";
            final Witness witness = new Witness(chosen.output().resolve(key + (finalized ? "-finalized.properties" : ".properties")),
                finalized ? text.replace("source=native-sealed-snapshot", "source=native-finalized-snapshot") : text, finalized ? player.getObjectId() : 0);
            if (crash != null && chosen.profiles().getOrDefault(id, 0L) == initialEpoch && owner.evidence().snapshot(System.nanoTime()).expGained() > 0 && owner.evidence().snapshot(System.nanoTime()).spGained() > 0)
            {
                PREPARED.set(new Prepared(player, (PhantomNativeWorkScope) owner, intent, witness));
                return;
            }
            enqueue(witness);
        }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("TASK032_WITNESS_ARGUMENT", failure); }
    }
    /** Bounded read-only probe for every exact enrolled natural actor; never runs in a native hook. */
    private static void sampleNativeTarget() throws Exception
    {
        final long now = System.nanoTime();
        if (targetLines >= 2000 || now - targetSampleNanos < 200_000_000L) { return; }
        targetSampleNanos = now;
        for (var enrolled : dispatchOwners.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
        {
        if (targetLines >= 2000) { break; }
        final var owner = enrolled.getValue();
        if (!owner.isCurrent() || owner.player().getNativeWorkOwner() != owner) { continue; }
        final Player player = owner.player();
        final var target = player.getTarget();
        final var creature = target instanceof org.l2jmobius.gameserver.model.actor.Creature value ? value : null;
        final var cast = player.getCurrentSkill();
        final var progress = owner.evidence().snapshot();
        final String row = now + "\t" + enrolled.getKey() + "\t" + owner.epoch() + "\t" + player.isCastingNow() + "\t" + player.getCurrentMp()
            + "\t" + (cast == null ? 0 : cast.getSkill().getId()) + "\t" + (target == null ? 0 : target.getObjectId())
            + "\t" + (creature == null ? "none" : creature.getCurrentHp()) + "\t" + (creature == null ? "none" : creature.isAlikeDead())
            + "\t" + (creature == null || creature.getTarget() == null ? 0 : creature.getTarget().getObjectId())
            + "\t" + progress.damageSequence() + "\t" + progress.rewardSequence() + "\t" + player.getAI().getIntention() + "\n";
        writeTelemetry(selection.output().resolve("natural-cohort-targets037.tsv"), row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        targetLines++;
        }
    }
    private static void crashWindow(PhantomBackgroundTransaction.FaultPoint point)
    {
        final var control = crash;
        final var prepared = PREPARED.get();
        if (control == null || prepared == null || CRASHED.get()) { return; }
        final boolean window = control.mode().equals("CRASH_NATIVE") && point == PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_NATIVE_STORE
            || control.mode().equals("CRASH_FINALIZE") && point == PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_FINALIZE_COMMIT;
        if (!window) { return; }
        try
        {
            final var player = prepared.player(); final var owner = prepared.owner(); final var intent = prepared.intent();
            if (player.getNativeWorkOwner() != owner || !owner.isCurrent() || owner.player() != player || owner.epoch() != intent.materializedAtNanos()
                || player.getClient() != null || THREAD.get(owner) != Thread.currentThread() || !STATE.get(owner).toString().equals("SEALED")
                || intentField.get(BOUNDARY.get(player)) != intent || World.getInstance().getPlayer(player.getObjectId()) != player) { throw new IllegalStateException("TASK032_EXACT_WINDOW_OWNER"); }
            // Enabled native login acquires REAL_LOGIN before Player load (GameClient.load). No World/materialization scan or foreign Player locks in the hook.
            final var registry = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance();
            final var owners = (Map<?, ?>) field(registry.getClass(), "_owners").get(registry);
            for (Object objectId : owners.keySet()) { if (registry.getOwnerKind((Integer) objectId) == org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN) { throw new IllegalStateException("TASK032_REAL_LEASE_AT_WINDOW"); } }
            final byte[] bytes = prepared.witness().text().getBytes(StandardCharsets.UTF_8);
            write(prepared.witness().path(), prepared.witness().text());
            final String sql = sqlAtWindow(player.getObjectId());
            write(control.output().resolve("window-sql.tsv"), sql);
            final int exit = control.mode().equals("CRASH_NATIVE") ? 72 : 73;
            write(control.output().resolve("planned-crash.properties"), "owner=TASK044_CONTRACT\nmode=" + control.mode() + "\nfaultPoint=" + point + "\npid=" + ProcessHandle.current().pid()
                + "\nprofileId=" + intent.after().identity().profileId() + "\nobjectId=" + player.getObjectId() + "\nepoch=" + owner.epoch() + "\npreparedRowVersion=" + intent.preparedRowVersion()
                + "\nreceiptSha256=" + PhantomBackgroundTransaction.payloadDigest(intent.encode()) + "\nsnapshotFile=" + prepared.witness().path().getFileName() + "\nsnapshotSha256=" + PhantomBackgroundTransaction.payloadDigest(bytes)
                + "\nsqlSha256=" + PhantomBackgroundTransaction.payloadDigest(sql.getBytes(StandardCharsets.UTF_8)) + "\npreDumpSha256=" + control.dumpHash()
                + "\nREALcount=0\nREALcountSource=enabled-native-identity-registry\nexactArgument=true\nexit=" + exit + "\nstack=" + java.util.Arrays.toString(Thread.currentThread().getStackTrace()) + "\n");
            if (!CRASHED.compareAndSet(false, true)) { throw new IllegalStateException("TASK032_DUPLICATE_CRASH_WINDOW"); }
            Runtime.getRuntime().halt(exit);
        }
        catch (Exception failure) { throw new IllegalStateException("TASK032_PLANNED_CRASH_REFUSED:" + point, failure); }
    }
    private static String timedSqlView(int objectId) throws Exception
    {
        final long begin = System.nanoTime();
        final String sql = sqlAtWindow(objectId);
        return "captureScope=ASYNC_EXPORTER\ncaptureThread=" + Thread.currentThread().getName() + "\ncaptureNanos\t" + begin + "\ncaptureEndNanos\t" + System.nanoTime() + "\n" + sql;
    }
    private static java.sql.ResultSet boundedQuery(java.sql.Statement statement, String query) throws java.sql.SQLException
    {
        statement.setQueryTimeout(2);
        return statement.executeQuery(query);
    }
    private static String sqlAtWindow(int objectId) throws Exception
    {
        final var text = new StringBuilder();
        try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection())
        {
            connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            for (String query : java.util.List.of("SELECT DATABASE() AS database_name", "SELECT c.charId,c.level,c.exp,c.sp,c.expBeforeDeath,c.curHp,c.maxHp,c.curMp,c.maxMp,c.curCp,c.maxCp,c.x,c.y,c.z,c.heading,c.classid,c.race,c.vitality_points,CASE WHEN c.classid=c.base_class THEN 0 ELSE sc.class_index END AS classIndex FROM characters c LEFT JOIN character_subclasses sc ON sc.charId=c.charId AND sc.class_id=c.classid WHERE c.charId=" + objectId,
                "SELECT object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id=" + objectId + " ORDER BY object_id", "SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId=" + objectId + " ORDER BY class_index,skill_id",
                "SELECT c.profile_id,c.component_type,c.row_version,HEX(c.payload) AS payload FROM phantom_profile_components c JOIN phantom_profiles p ON p.profile_id=c.profile_id WHERE p.character_object_id=" + objectId + " AND c.component_type IN ('background.state','background.native-context','background.owned-store') ORDER BY c.component_type"))
            {
                text.append("QUERY\t").append(query).append('\n');
                try (var statement = connection.createStatement(); var result = boundedQuery(statement, query))
                {
                    final var metadata = result.getMetaData();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) { if (i > 1) { text.append('\t'); } text.append(metadata.getColumnLabel(i)); } text.append('\n');
                    while (result.next()) { for (int i = 1; i <= metadata.getColumnCount(); i++) { if (i > 1) { text.append('\t'); } text.append(result.getString(i)); } text.append('\n'); }
                }
            }
            connection.rollback();
        }
        boolean ownTest = false;
        final String testManifest = System.getProperty("phantom.contract044.manifest", "");
        if (!testManifest.isEmpty())
        {
            final Path exact = Path.of("").toRealPath().getParent().getParent().resolve("test/owned.properties").toRealPath();
            if (!Path.of(testManifest).toRealPath().equals(exact)) { throw new IllegalStateException("TASK032_SQL_TEST_MANIFEST_PATH"); }
            final var test = new Properties();
            try (var reader = Files.newBufferedReader(exact, StandardCharsets.UTF_8)) { test.load(reader); }
            ownTest = "TASK044_CONTRACT".equals(test.getProperty("owner"))
                && "l2jmobiush5_phantom_test_044t".equals(test.getProperty("database"))
                && text.toString().matches("(?s).*database_name\\r?\\nl2jmobiush5_phantom_test_044t\\r?\\n.*");
        }
        if (!ownTest && !text.toString().matches("(?s).*l2jmobiush5_phantom_test_044[ab].*")) { throw new IllegalStateException("TASK032_SQL_DB_GUARD"); }
        return text.toString();
    }
    private static String snapshot(PhantomOwnedStoreIntent intent, Player player, String sha)
    {
        final var state = intent.after(); final var id = state.identity(); final var vitals = state.vitals();
        final StringBuilder text = new StringBuilder("source=native-sealed-snapshot\nbarrier=QUIESCENT_NATIVE_PREPARE\nexactArgument=true\ncodeSha=" + sha + "\n");
        put(text,"profileId",id.profileId()); put(text,"objectId",player.getObjectId()); put(text,"epoch",intent.materializedAtNanos()); put(text,"initialEpoch",intent.materializedAtNanos()); put(text,"preparedRowVersion",intent.preparedRowVersion());
        put(text,"beforeState",intent.before().state()); put(text,"beforeHp",intent.before().vitals().currentHp());
        put(text,"afterState",state.state());
        final var codec = new PhantomBackgroundStateCodec();
        final byte[] beforePayload = codec.encode(intent.before()), afterPayload = codec.encode(state);
        put(text,"beforePayloadSha256",PhantomBackgroundTransaction.payloadDigest(beforePayload));
        put(text,"afterPayloadSha256",PhantomBackgroundTransaction.payloadDigest(afterPayload));
        put(text,"beforeStateBase64",java.util.Base64.getEncoder().encodeToString(beforePayload));
        put(text,"fullStateBase64",java.util.Base64.getEncoder().encodeToString(afterPayload));
        put(text,"level",player.getLevel()); put(text,"exp",player.getExp()); put(text,"sp",player.getSp()); put(text,"expBeforeDeath",player.getExpBeforeDeath());
        // These are the exact scalars supplied to Player.OwnedStoreSnapshot, already canonicalized by the existing resolver.
        put(text,"hp",vitals.currentHp()); put(text,"maxHp",vitals.maximumHp()); put(text,"mp",vitals.currentMp()); put(text,"maxMp",vitals.maximumMp()); put(text,"cp",vitals.currentCp()); put(text,"maxCp",vitals.maximumCp());
        put(text,"rawHp",player.getCurrentHp()); put(text,"rawMp",player.getCurrentMp()); put(text,"rawCp",player.getCurrentCp());
        put(text,"x",player.getX()); put(text,"y",player.getY()); put(text,"z",player.getZ()); put(text,"heading",player.getHeading()); put(text,"classIndex",player.getClassIndex()); put(text,"classId",player.getActiveClass()); put(text,"race",player.getRace().ordinal()); put(text,"vitality",player.getVitalityPoints());
        put(text,"inventoryHash",state.inventory().canonicalHash()); put(text,"skillsHash",intent.skillsHash()); put(text,"receiptSha256",PhantomBackgroundTransaction.payloadDigest(intent.encode())); put(text,"nativeOwnerSealed",true);
        text.append(inventory035(id.profileId(), player));
        return text.toString();
    }
    /** Full causal multiset, including unknown objects, at native birth and the sealed store boundary. */
    private static String nativeLoad044(Player player)
    {
        final var items = player.getInventory().getItems().stream().map(item -> new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), PhantomBackgroundState.ItemLocation.valueOf(item.getItemLocation().name()))).sorted(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem::objectId)).toList();
        final String skills = player.getAllSkills().stream().sorted(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.skill.Skill::getId)).map(skill -> skill.getId() + ":" + skill.getLevel() + "\n").collect(java.util.stream.Collectors.joining());
        return "load044Boundary=PRE_CONTEXT_NATIVE_LOAD\nload044Level=" + player.getLevel() + "\nload044ClassIndex=" + player.getClassIndex() + "\nload044ClassId=" + player.getActiveClass() + "\nload044ExpBeforeDeath=" + player.getExpBeforeDeath()
            + "\nload044InventoryHash=" + org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.compute(items)
            + "\nload044Items=" + items.stream().map(item -> item.objectId() + ":" + item.itemId() + ":" + item.count() + ":" + item.location()).collect(java.util.stream.Collectors.joining("|"))
            + "\nload044SkillsHash=" + PhantomBackgroundTransaction.payloadDigest(skills.getBytes(StandardCharsets.US_ASCII))
            + "\nload044Skills=" + skills.replace("\n", "|") + "\n";
    }
    private static String inventory035(long profile, Player player)
    {
        if (profile != 36 && profile != 45) { return ""; }
        final var items = player.getInventory().getItems().stream().map(item -> new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem(item.getObjectId(), item.getId(), item.getCount(), PhantomBackgroundState.ItemLocation.valueOf(item.getItemLocation().name()))).sorted(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem::objectId)).toList();
        return "fullItemCount=" + items.size() + "\nfullInventoryCount=" + items.stream().filter(item -> item.location() == PhantomBackgroundState.ItemLocation.INVENTORY).count()
            + "\nfullPaperdollCount=" + items.stream().filter(item -> item.location() == PhantomBackgroundState.ItemLocation.PAPERDOLL).count()
            + "\nquestCount=" + player.getInventory().getQuestSize() + "\nnonQuestCount=" + player.getInventory().getNonQuestSize() + "\nstockCap=" + player.getInventoryLimit()
            + "\nfullNativeInventoryHash=" + org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.compute(items)
            + "\nfullNativeItems=" + items.stream().map(item -> item.objectId() + ":" + item.itemId() + ":" + item.count() + ":" + item.location()).collect(java.util.stream.Collectors.joining("|")) + "\n";
    }
    private static void put(StringBuilder text, String key, Object value) { text.append(key).append('=').append(value).append('\n'); }
    private static synchronized void drain() throws Exception
    {
        drain(Integer.MAX_VALUE);
    }
    private static synchronized void drain(int maximum) throws Exception
    {
        final long beforeSequence = ledgerSequence;
        final var headers = new java.util.ArrayList<Map<String, String>>();
        final String archiveName = "witnesses-" + (++archiveSequence) + ".zip";
        final Path partial = streamRoot.resolve(archiveName + ".partial");
        java.util.zip.ZipOutputStream archive = null;
        long batchBytes = 0;
        Witness witness;
        int exported = 0;
        try
        {
        while (exported < Math.min(maximum, 512) && (witness = RING.peek()) != null)
        {
            final byte[] record = ("{\"file\":" + jsonString(witness.path().getFileName().toString()) + ",\"text\":" + jsonString(witness.text()) + "}\n").getBytes(StandardCharsets.UTF_8);
            if (!headers.isEmpty() && batchBytes + record.length > 16L * 1024 * 1024) { break; }
            RING.poll(); exported++;
            BUFFER_BYTES.addAndGet(-witness.text().getBytes(StandardCharsets.UTF_8).length);
            try
            {
                final byte[] bytes = witness.text().getBytes(StandardCharsets.UTF_8);
                final var comparison = new Properties(); comparison.load(new java.io.StringReader(witness.text())); comparison.remove("hookNanos");
                final String logicalDigest = PhantomBackgroundTransaction.payloadDigest(new java.util.TreeMap<>(comparison).toString().getBytes(StandardCharsets.UTF_8));
                // The owning JDBC publisher emits each transaction/write index once. These
                // monotonically unique records need no lifetime-long duplicate index.
                final boolean repeatable = !"TRANSACTION_COMMIT039".equals(comparison.getProperty("kind"));
                final String prior = repeatable ? WRITTEN_RAW.get(witness.path()) : null;
                if (prior != null)
                {
                    if (!prior.equals(logicalDigest)) { throw new IllegalStateException("TASK032_CONFLICTING_DUPLICATE"); }
                    EXPORTED.incrementAndGet(); continue;
                }
                if (repeatable && WRITTEN_RAW.size() >= 65536) { throw new IllegalStateException("TASK044_SCALAR_INDEX_BOUND"); }
                if (archive == null) {
                    archive = new java.util.zip.ZipOutputStream(Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW));
                    archive.putNextEntry(new java.util.zip.ZipEntry("records.jsonl"));
                }
                archive.write(record); batchBytes += record.length;
                final String entryName = "record:" + witness.path().getFileName();
                if (repeatable) { WRITTEN_RAW.put(witness.path(), logicalDigest); }
                final var values = new Properties(); values.load(new java.io.StringReader(witness.text()));
                final String kind = values.getProperty("kind", values.getProperty("checkpointStage", "UNKNOWN"));
                final var row = new java.util.LinkedHashMap<String, String>();
                row.put("kind", jsonString(kind));
                row.put("profile_id", values.getProperty("profileId", "0")); row.put("object_id", values.getProperty("objectId", "0"));
                row.put("epoch", values.getProperty("epoch", "0")); row.put("receipt", jsonString(values.getProperty("receiptSha256", "")));
                row.put("raw_file", jsonString(witness.path().getFileName().toString())); row.put("raw_sha256", jsonString(PhantomBackgroundTransaction.payloadDigest(bytes)));
                row.put("raw_archive", jsonString(archiveName)); row.put("raw_entry", jsonString(entryName));
                row.put("raw_format", jsonString("BATCH_PROPERTIES_JSONL044"));
                if (kind.equals("TERMINAL"))
                {
                    row.put("owner_state", jsonString(values.getProperty("nativeOwnerState")));
                    row.put("permanent", values.getProperty("nativeOwnerPermanentSeal"));
                    row.put("outstanding", values.getProperty("terminalOutstanding")); row.put("timers", values.getProperty("terminalPendingTimers"));
                }
                headers.add(row);
                EXPORTED.incrementAndGet();
                if (kind.equals("TERMINAL"))
                {
                    final var key = new Lifetime(Long.parseLong(values.getProperty("profileId")), Integer.parseInt(values.getProperty("objectId")), Long.parseLong(values.getProperty("epoch")));
                    final var removed = RECEIPT_OWNERS.remove(key);
                    REGISTERED.remove(key); LAST_RECEIPT.remove(key); TERMINAL.remove(key);
                    // Keep only scalar profile subscription through the final stopped cut.
                    // The native Player reference still retires at its actual terminal.
                    synchronized (PARENT_EPOCHS)
                    {
                        PARENT_EPOCHS.put(key.profileId(), key.epoch());
                        if (PARENT_EPOCHS.size() > 4096) { PARENT_EPOCHS.remove(PARENT_EPOCHS.keySet().iterator().next()); }
                    }
                    if (removed != null)
                    {
                        ACTIVE_REFERENCES.decrementAndGet();
                        final var remaining = new HashMap<>(dispatchOwners);
                        remaining.entrySet().removeIf(entry -> entry.getValue() == removed); dispatchOwners = Map.copyOf(remaining);
                    }
                }
                if (witness.sqlObjectId() > 0)
                {
                    try { write(witness.path().resolveSibling(witness.path().getFileName() + ".sql.tsv"), timedSqlView(witness.sqlObjectId())); }
                    catch (Exception failure) { EXPORT_FAILURES.incrementAndGet(); proofFailure("TASK032_SQL_VIEW:" + failure); }
                }
            }
            catch (Exception failure) { EXPORT_FAILURES.incrementAndGet(); proofFailure("TASK032_PRIMARY_EXPORT:" + failure); throw failure; }
        }
        }
        finally { if (archive != null) { archive.close(); } }
        if (archive != null)
        {
            final Path closed = streamRoot.resolve(archiveName);
            Files.move(partial, closed, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            reserveDisk(Files.size(closed));
            try (var channel = FileChannel.open(closed, StandardOpenOption.WRITE)) { channel.force(true); }
            final String archiveSha = PhantomBackgroundTransaction.payloadDigest(Files.readAllBytes(closed));
            for (var row : headers) { row.put("raw_archive_sha256", jsonString(archiveSha)); appendLedger(row); }
            lastBatchNanos = System.nanoTime();
        }
        if (ledgerSequence != beforeSequence) { forceLedger(); }
    }
    private static synchronized void appendLedger(Map<String, String> row) throws Exception
    {
        final var values = new java.util.LinkedHashMap<String, String>();
        values.put("run_id", jsonString(lifetimeRunId)); values.put("seq", Long.toString(++ledgerSequence)); values.putAll(row);
        final String line = "{" + values.entrySet().stream().map(entry -> jsonString(entry.getKey()) + ":" + entry.getValue()).collect(java.util.stream.Collectors.joining(",")) + "}\n";
        if (line.getBytes(StandardCharsets.UTF_8).length > 16 * 1024) { throw new IllegalStateException("TASK032_HEADER_BUDGET"); }
        reserveDisk(line.getBytes(StandardCharsets.UTF_8).length);
        try (var channel = FileChannel.open(streamRoot.resolve("lifetimes.jsonl"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND))
        { final var bytes = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)); while (bytes.hasRemaining()) { channel.write(bytes); } }
    }
    private static void forceLedger() throws Exception
    {
        // Ordinary diagnostic export: one durability boundary per existing bounded drain batch.
        try (var channel = FileChannel.open(streamRoot.resolve("lifetimes.jsonl"), StandardOpenOption.WRITE)) { channel.force(true); }
    }
    private static final AtomicBoolean STREAM_ENDED = new AtomicBoolean();
    private static synchronized void finishStream() throws Exception
    {
        if (streamRoot == null || STREAM_ENDED.get()) { return; }
        sampleTerminals(); drain();
        if (!RECEIPT_OWNERS.isEmpty()) { proofFailure("TASK032_MISSING_TERMINAL:" + RECEIPT_OWNERS.size()); }
        if (STREAM_ENDED.compareAndSet(false, true))
        {
            appendLedger(Map.of("kind", jsonString("RUN_END"), "native_births", Long.toString(NATIVE_BIRTHS.get()),
                "dropped", Long.toString(OVERFLOW.get() + REFUSED_BIRTHS.get()), "failure", jsonString(exporterFailure), "exporter_drained", Boolean.toString(RING.isEmpty())));
            forceLedger();
        }
    }
    private static Path writeTelemetry(Path target, CharSequence text, java.nio.charset.Charset charset, java.nio.file.OpenOption... options) throws Exception
    {
        reserveDisk(text.toString().getBytes(charset).length);
        return Files.writeString(target, text, charset, options);
    }
    private static boolean sameImmutableWitness(String first, String second) throws Exception
    {
        final var left = new Properties(); left.load(new java.io.StringReader(first));
        final var right = new Properties(); right.load(new java.io.StringReader(second));
        left.remove("hookNanos"); right.remove("hookNanos");
        return left.equals(right);
    }
    private static void reserveDisk(long bytes)
    {
        if (DISK_BYTES.addAndGet(bytes) > 512L * 1024 * 1024) { proofFailure("TASK032_DISK_BUDGET"); throw new IllegalStateException("TASK032_DISK_BUDGET"); }
    }
    private static void status(Path output) throws Exception
    {
        sampleTerminals(); drain();
        final var owners = new java.util.ArrayList<String>();
        for (var entry : RECEIPT_OWNERS.entrySet().stream().sorted(java.util.Comparator.comparingLong(value -> value.getKey().profileId())).toList())
        {
            final var row = new java.util.LinkedHashMap<String, String>();
            row.put("profileId", Long.toString(entry.getKey().profileId()));
            row.put("objectId", Integer.toString(entry.getKey().objectId()));
            row.put("epoch", Long.toString(entry.getValue().epoch()));
            row.putAll(entry.getValue().diagnosticScalars());
            owners.add("{" + row.entrySet().stream().map(value -> jsonString(value.getKey()) + ":" + jsonString(value.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}");
        }
        write(output.resolve("final-owner-state-" + System.nanoTime() + ".json"), "[" + String.join(",", owners) + "]\n");
        final String text = "owner=TASK044_CONTRACT\nhookCalls=" + HOOK_CALLS.get() + "\nhookNanos=" + HOOK_NANOS.get() + "\nhookMaxNanos=" + HOOK_MAX_NANOS.get() + "\nordinaryHookSQL=false\ncaptured=" + CAPTURED.get() + "\nexported=" + EXPORTED.get() + "\npending=" + RING.size() + "\noverflow=" + OVERFLOW.get() + "\nexporterFailure=" + exporterFailure + "\nactiveReferences=" + RECEIPT_OWNERS.size() + "\nbufferBytes=" + BUFFER_BYTES.get() + "\ndiskBytes=" + DISK_BYTES.get() + "\nnativeBirths=" + NATIVE_BIRTHS.get() + "\nrunId=" + lifetimeRunId + "\n";
        write(output.resolve("observer-status-" + System.nanoTime() + ".properties"), text);
    }
    private static void write(Path target, String text) throws Exception
    {
        reserveDisk(text.getBytes(StandardCharsets.UTF_8).length);
        try (var channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        {
            final var bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)); while (bytes.hasRemaining()) { channel.write(bytes); } channel.force(true);
        }
    }
}

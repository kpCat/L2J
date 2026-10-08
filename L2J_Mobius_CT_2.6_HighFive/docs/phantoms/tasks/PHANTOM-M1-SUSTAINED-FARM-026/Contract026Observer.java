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
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Task-only exact-argument observer. Hook has no World scan, I/O, waits or other actor locks. */
public final class Contract026Observer
{
    private record Selection(Map<Long, Long> profiles, Path output, String sha) { }
    private record Witness(Path path, String text) { }
    private record Prepared(Player player, PhantomNativeWorkScope owner, PhantomOwnedStoreIntent intent, Witness witness) { }
    private record Crash(String mode, Path output, String dumpHash) { }
    private static final ThreadLocal<Prepared> PREPARED = new ThreadLocal<>();
    private static final AtomicBoolean CRASHED = new AtomicBoolean();
    private static volatile Crash crash;
    private static final ArrayBlockingQueue<Witness> RING = new ArrayBlockingQueue<>(32);
    private static final AtomicLong OVERFLOW = new AtomicLong();
    private static final AtomicLong CAPTURED = new AtomicLong();
    private static final AtomicLong EXPORTED = new AtomicLong();
    private static volatile Selection selection;
    private static volatile String exporterFailure = "";
    private static volatile Map<Long, PhantomNativeWorkScope> dispatchOwners = Map.of();
    private static final Map<Long, String> dispatchLast = new HashMap<>();
    private static int dispatchLines;
    private static long dispatchSampleNanos;
    private static int targetLines;
    private static long targetSampleNanos;
    private static org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService fullMaterialization;
    private static org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay fullAutoPlay;
    private static org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine fullDecision;
    private static PhantomBackgroundService fullBackground;
    private static final Map<Long, org.l2jmobius.gameserver.phantoms.decision.PhantomGoal> fullGoals = new HashMap<>();
    private static long fullSampleNanos;
    private static int fullLines;
    private static boolean installed;
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
    public static synchronized void agentmain(String argument, Instrumentation instrumentation) throws Exception
    {
        final Path specPath = Path.of(argument).toRealPath();
        final Properties spec = new Properties();
        try (var reader = Files.newBufferedReader(specPath, StandardCharsets.UTF_8)) { spec.load(reader); }
        final Path runtime = Path.of(spec.getProperty("runtime")).toRealPath();
        if (!runtime.toString().matches(".*[\\\\/]contract026[a-h][\\\\/]runtime") || !Path.of("").toRealPath().equals(runtime.resolve("game"))) { throw new IllegalStateException("TASK026_RUNTIME_GUARD"); }
        if (!specPath.startsWith(runtime.getParent()) || !spec.getProperty("pid").equals(Long.toString(ProcessHandle.current().pid())) || !"TASK026_CONTRACT".equals(spec.getProperty("owner"))) { throw new IllegalStateException("TASK026_PROCESS_GUARD"); }
        final var start = ProcessHandle.current().info().startInstant().orElseThrow();
        final long ticks = 621355968000000000L + start.getEpochSecond() * 10000000L + start.getNano() / 100;
        if (ticks / 10000 != Long.parseLong(spec.getProperty("startTicks")) / 10000) { throw new IllegalStateException("TASK026_START_GUARD"); }
        final Path output = Path.of(spec.getProperty("output")).toAbsolutePath().normalize();
        final Path module = runtime.getParent().getParent().getParent();
        if (!output.startsWith(module.resolve("docs/phantoms/tasks/PHANTOM-M1-SUSTAINED-FARM-026/evidence"))) { throw new IllegalStateException("TASK026_OUTPUT_GUARD"); }
        Files.createDirectories(output);
        if ("FLUSH".equals(spec.getProperty("mode"))) { drain(); status(output); return; }
        final String mode = spec.getProperty("mode");
        if (!java.util.Set.of("OBSERVE", "FULL_OBSERVE", "CENSUS", "RECOVERY", "CRASH_NATIVE", "CRASH_FINALIZE").contains(mode)) { throw new IllegalStateException("TASK026_MODE_GUARD"); }
        final Map<Long, Long> selected = new HashMap<>();
        for (String key : spec.stringPropertyNames()) { if (key.startsWith("profile.")) { selected.put(Long.parseLong(key.substring(8)), Long.parseLong(spec.getProperty(key))); } }
        if (selected.isEmpty() || selected.size() > 8) { throw new IllegalStateException("TASK026_COHORT_GUARD"); }
        // Attach preflight is outside native hooks/critical sections.
        for (Player player : World.getInstance().getPlayers()) { if (player.getClient() != null) { throw new IllegalStateException("TASK026_REAL_PRESENT"); } }
        final Selection nextSelection = new Selection(Map.copyOf(selected), output, spec.getProperty("codeSha"));
        if (mode.equals("FULL_OBSERVE"))
        {
            // Separate bounded export window per explicit session; native scopes/counters remain unchanged.
            fullLines = 0; fullSampleNanos = 0; fullGoals.clear();
            if (!runtime.toString().matches(".*[\\\\/]contract026[c-d][\\\\/]runtime") || selected.size() != 8) { throw new IllegalStateException("TASK026_FULL_COHORT_LANE_GUARD"); }
            final var configured = field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
            fullMaterialization = (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService) field(configured.getClass(), "_materializationService").get(configured);
            fullAutoPlay = (org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay) field(configured.getClass(), "_visibleAutoPlay").get(configured);
            fullDecision = (org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine) field(configured.getClass(), "_decisionEngine").get(configured);
            fullBackground = (PhantomBackgroundService) field(configured.getClass(), "_backgroundService").get(configured);
            final var entries = fullMaterialization.snapshot().materializations();
            for (long profile : selected.keySet())
            {
                final var entry = entries.stream().filter(value -> value.profileId() == profile).findFirst().orElse(null);
                if (entry != null) { selected.put(profile, entry.materializedAtNanos()); }
            }
            // One initial binding; absent profiles remain epoch0 and are never replaced by later incarnations.
        }
        if (mode.equals("CENSUS"))
        {
            final var configured = field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
            final var materialization = (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService) field(configured.getClass(), "_materializationService").get(configured);
            final var entries = materialization.snapshot().materializations();
            write(output.resolve("all-materializations.txt"), entries.toString());
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
        if (mode.equals("RECOVERY"))
        {
            if (!runtime.toString().matches(".*[\\\\/]contract026[e-f][\\\\/]runtime") || selected.size() != 1) { throw new IllegalStateException("TASK026_RECOVERY_LANE_GUARD"); }
            selection = nextSelection;
            field(PhantomBackgroundService.class, "_recoveryObserver").set(null, (BiConsumer<Long, PhantomBackgroundState>) Contract026Observer::recovery);
        }
        if (mode.startsWith("CRASH_"))
        {
            if (!runtime.toString().matches(".*[\\\\/]contract026" + (mode.equals("CRASH_NATIVE") ? "e" : "f") + "[\\\\/]runtime")) { throw new IllegalStateException("TASK026_SEPARATE_CRASH_LANE"); }
            if (installed || crash != null || !org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.isEnabled()
                || !org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig.isSyntheticEnabled()) { throw new IllegalStateException("TASK026_CRASH_PREFLIGHT"); }
            final Path dump = Path.of(spec.getProperty("preDump")).toRealPath();
            if (!dump.startsWith(output) || !PhantomBackgroundTransaction.payloadDigest(Files.readAllBytes(dump)).equals(spec.getProperty("preDumpHash"))) { throw new IllegalStateException("TASK026_CRASH_DUMP_GUARD"); }
            final var configured = field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
            if (configured == null) { throw new IllegalStateException("TASK026_SYSTEM_ABSENT"); }
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
        if (!installed)
        {
            field(PhantomNativeWorkScope.class, "_checkpointObserver").set(null, (BiConsumer<Player, String>) Contract026Observer::observe);
            final Thread exporter = new Thread(() ->
            {
                while (true)
                {
                    try { Thread.sleep(100); drain(); sampleDispatch(); sampleNativeTarget(); sampleFullCohort(); exportRecovery(); }
                    catch (InterruptedException stopped) { return; }
                    catch (Exception failure) { exporterFailure = failure.toString(); }
                }
            }, "TASK026-witness-export");
            exporter.setDaemon(true); exporter.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
            {
                try { drain(); status(selection.output()); }
                catch (Exception failure) { exporterFailure = failure.toString(); System.err.println("TASK026_WITNESS_EXPORT_FAILED:" + failure); }
            }, "TASK026-witness-final-export"));
            installed = true;
        }
        write(output.resolve("observer-installed.properties"), "owner=TASK026_CONTRACT\nmode=" + mode + "\npid=" + ProcessHandle.current().pid() + "\ncodeSha=" + selection.sha() + "\nringCapacity=32\nexactArgument=true\n");
        if (mode.equals("FULL_OBSERVE")) { sampleFullCohort(); }
    }
    /** Exact initial scopes, including actors outside Synthetic surrounding regions. No admission or Player mutation. */
    private static synchronized void sampleFullCohort() throws Exception
    {
        final long now = System.nanoTime();
        if (fullMaterialization == null || fullLines >= 525 || now - fullSampleNanos < 1_000_000_000L) { return; }
        fullSampleNanos = now;
        final var entries = fullMaterialization.snapshot().materializations();
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
            if (retained != null) { row.putAll(retained.evidence().snapshot().scalarMap()); row.putAll(retained.diagnosticScalars()); }
            else { row.put("nativeEvidenceEpoch", "0"); for (String key : java.util.List.of("nativeRewardSequence", "nativeKillSequence", "nativeDamageSequence", "nativeTargetSequence", "nativeFarmCycleSequence", "nativeExpGained", "nativeSpGained")) { row.put(key, "0"); } row.put("nativeEvidenceOverflow", "true"); }
            row.putAll(fullAutoPlay.snapshotContinuation(profile).scalarMap());
            row.put("runtimeReason", decision == null ? "runtime.absent" : decision.reasonKey());
            if (player != null)
            {
                row.put("hp", Double.toString(player.getCurrentHp())); row.put("mp", Double.toString(player.getCurrentMp()));
                row.put("x", Integer.toString(player.getX())); row.put("y", Integer.toString(player.getY())); row.put("z", Integer.toString(player.getZ()));
                row.put("moving", Boolean.toString(player.isMoving())); row.put("casting", Boolean.toString(player.isCastingNow())); row.put("attacking", Boolean.toString(player.isAttackingNow()));
                row.put("targetObjectId", Integer.toString(player.getTarget() == null ? 0 : player.getTarget().getObjectId()));
            }
            rows.add(row);
        }
        final String json = "[" + rows.stream().map(row -> "{" + row.entrySet().stream().map(entry -> jsonString(entry.getKey()) + ":" + jsonString(entry.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}").collect(java.util.stream.Collectors.joining(",")) + "]";
        Files.writeString(selection.output().resolve("full-cohort-latest.json.tmp"), json, StandardCharsets.UTF_8);
        Files.move(selection.output().resolve("full-cohort-latest.json.tmp"), selection.output().resolve("full-cohort-latest.json"), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        Files.writeString(selection.output().resolve("full-cohort-samples.jsonl"), "{\"sampleNanos\":" + now + ",\"actors\":" + json + "}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        fullLines++;
    }
    private static String jsonString(String value)
    {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
    private static void recovery(Long profile, PhantomBackgroundState state)
    {
        final var chosen = selection;
        if (!chosen.profiles().containsKey(profile)) { return; }
        if (state.identity().profileId() != profile || Thread.holdsLock(state)) { throw new IllegalStateException("TASK026_RECOVERY_ARGUMENT"); }
        final var release = new java.util.concurrent.CountDownLatch(1);
        if (!RECOVERIES.offer(new Recovery(profile, state, chosen, release))) { OVERFLOW.incrementAndGet(); throw new IllegalStateException("TASK026_RECOVERY_RING_FULL"); }
        try { if (!release.await(45, java.util.concurrent.TimeUnit.SECONDS)) { exporterFailure = "TASK026_RECOVERY_BARRIER_TIMEOUT"; } }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); exporterFailure = "TASK026_RECOVERY_INTERRUPTED"; }
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
            final String state = fields.get("nativeOwnerState") + "\t" + fields.getOrDefault("nativeEventDispatch", "UNAVAILABLE") + "\t" + fields.getOrDefault("nativeLastCompletedEvent", "UNAVAILABLE");
            if (!state.equals(dispatchLast.put(entry.getKey(), state)) && dispatchLines++ < 1024)
            {
                Files.writeString(chosen.output().resolve("event-dispatch.tsv"), now + "\t" + chosen.sha() + "\t" + entry.getKey() + "\t" + entry.getValue().epoch() + "\t" + state + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
    }
    private static void observe(Player player, String phase)
    {
        if (phase.equals("CAPTURE")) { PREPARED.remove(); }
        if (!phase.equals("PREPARED")) { return; }
        try
        {
            final var chosen = selection;
            final Object boundary = BOUNDARY.get(player);
            if (boundary == null) { return; }
            if (intentField == null) { intentField = field(boundary.getClass(), "_intent"); }
            final var intent = (PhantomOwnedStoreIntent) intentField.get(boundary);
            if (intent == null) { return; }
            final long id = intent.after().identity().profileId();
            final Long initialEpoch = chosen.profiles().get(id);
            if (initialEpoch == null || initialEpoch != intent.materializedAtNanos()) { return; }
            final var owner = player.getNativeWorkOwner();
            if (!(owner instanceof PhantomNativeWorkScope) || !owner.isCurrent() || owner.player() != player || owner.epoch() != initialEpoch
                || THREAD.get(owner) != Thread.currentThread() || !STATE.get(owner).toString().equals("SEALED") || player.getObjectId() != intent.after().identity().characterObjectId()) { throw new IllegalStateException("TASK026_EXACT_SEALED_ARGUMENT"); }
            final String key = id + "-" + initialEpoch + "-" + intent.preparedRowVersion();
            final Witness witness = new Witness(chosen.output().resolve(key + ".properties"), snapshot(intent, player, chosen.sha()));
            if (crash != null && owner.evidence().snapshot(System.nanoTime()).rewardSequence() > 0)
            {
                PREPARED.set(new Prepared(player, (PhantomNativeWorkScope) owner, intent, witness));
                return;
            }
            if (!RING.offer(witness)) { OVERFLOW.incrementAndGet(); throw new IllegalStateException("TASK026_WITNESS_RING_OVERFLOW"); }
            CAPTURED.incrementAndGet();
        }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("TASK026_WITNESS_ARGUMENT", failure); }
    }
    /** Bounded read-only probe for the exact natural actor275; never runs in a native hook. */
    private static void sampleNativeTarget() throws Exception
    {
        final long now = System.nanoTime();
        if (targetLines >= 2000 || now - targetSampleNanos < 200_000_000L) { return; }
        targetSampleNanos = now;
        final var owner = dispatchOwners.get(275L);
        if (owner == null) { return; }
        final Player player = owner.player();
        final var target = player.getTarget();
        final var creature = target instanceof org.l2jmobius.gameserver.model.actor.Creature value ? value : null;
        final var cast = player.getCurrentSkill();
        final var progress = owner.evidence().snapshot();
        final String row = now + "\t275\t" + owner.epoch() + "\t" + player.isCastingNow() + "\t" + player.getCurrentMp()
            + "\t" + (cast == null ? 0 : cast.getSkill().getId()) + "\t" + (target == null ? 0 : target.getObjectId())
            + "\t" + (creature == null ? "none" : creature.getCurrentHp()) + "\t" + (creature == null ? "none" : creature.isAlikeDead())
            + "\t" + (creature == null || creature.getTarget() == null ? 0 : creature.getTarget().getObjectId())
            + "\t" + progress.damageSequence() + "\t" + progress.rewardSequence() + "\t" + player.getAI().getIntention() + "\n";
        Files.writeString(selection.output().resolve("natural275-target.tsv"), row, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        targetLines++;
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
                || intentField.get(BOUNDARY.get(player)) != intent || World.getInstance().getPlayer(player.getObjectId()) != player) { throw new IllegalStateException("TASK026_EXACT_WINDOW_OWNER"); }
            // Enabled native login acquires REAL_LOGIN before Player load (GameClient.load). No World/materialization scan or foreign Player locks in the hook.
            final var registry = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance();
            final var owners = (Map<?, ?>) field(registry.getClass(), "_owners").get(registry);
            for (Object objectId : owners.keySet()) { if (registry.getOwnerKind((Integer) objectId) == org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN) { throw new IllegalStateException("TASK026_REAL_LEASE_AT_WINDOW"); } }
            final byte[] bytes = prepared.witness().text().getBytes(StandardCharsets.UTF_8);
            write(prepared.witness().path(), prepared.witness().text());
            final String sql = sqlAtWindow(player.getObjectId());
            write(control.output().resolve("window-sql.tsv"), sql);
            final int exit = control.mode().equals("CRASH_NATIVE") ? 72 : 73;
            write(control.output().resolve("planned-crash.properties"), "owner=TASK026_CONTRACT\nmode=" + control.mode() + "\nfaultPoint=" + point + "\npid=" + ProcessHandle.current().pid()
                + "\nprofileId=" + intent.after().identity().profileId() + "\nobjectId=" + player.getObjectId() + "\nepoch=" + owner.epoch() + "\npreparedRowVersion=" + intent.preparedRowVersion()
                + "\nreceiptSha256=" + PhantomBackgroundTransaction.payloadDigest(intent.encode()) + "\nsnapshotFile=" + prepared.witness().path().getFileName() + "\nsnapshotSha256=" + PhantomBackgroundTransaction.payloadDigest(bytes)
                + "\nsqlSha256=" + PhantomBackgroundTransaction.payloadDigest(sql.getBytes(StandardCharsets.UTF_8)) + "\npreDumpSha256=" + control.dumpHash()
                + "\nREALcount=0\nREALcountSource=enabled-native-identity-registry\nexactArgument=true\nexit=" + exit + "\nstack=" + java.util.Arrays.toString(Thread.currentThread().getStackTrace()) + "\n");
            if (!CRASHED.compareAndSet(false, true)) { throw new IllegalStateException("TASK026_DUPLICATE_CRASH_WINDOW"); }
            Runtime.getRuntime().halt(exit);
        }
        catch (Exception failure) { throw new IllegalStateException("TASK026_PLANNED_CRASH_REFUSED:" + point, failure); }
    }
    private static String sqlAtWindow(int objectId) throws Exception
    {
        final var text = new StringBuilder();
        try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection())
        {
            for (String query : java.util.List.of("SELECT DATABASE() AS database_name", "SELECT charId,level,exp,sp,expBeforeDeath,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race,vitality_points FROM characters WHERE charId=" + objectId,
                "SELECT object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id=" + objectId + " ORDER BY object_id", "SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId=" + objectId + " ORDER BY class_index,skill_id",
                "SELECT c.profile_id,c.component_type,c.row_version,HEX(c.payload) AS payload FROM phantom_profile_components c JOIN phantom_profiles p ON p.profile_id=c.profile_id WHERE p.character_object_id=" + objectId + " AND c.component_type IN ('background.state','background.native-context','background.owned-store') ORDER BY c.component_type"))
            {
                text.append("QUERY\t").append(query).append('\n');
                try (var statement = connection.createStatement(); var result = statement.executeQuery(query))
                {
                    final var metadata = result.getMetaData();
                    for (int i = 1; i <= metadata.getColumnCount(); i++) { if (i > 1) { text.append('\t'); } text.append(metadata.getColumnLabel(i)); } text.append('\n');
                    while (result.next()) { for (int i = 1; i <= metadata.getColumnCount(); i++) { if (i > 1) { text.append('\t'); } text.append(result.getString(i)); } text.append('\n'); }
                }
            }
        }
        if (!text.toString().matches("(?s).*l2jmobiush5_localplay_contract026[e-f].*")) { throw new IllegalStateException("TASK026_CRASH_DB_GUARD"); }
        return text.toString();
    }
    private static String snapshot(PhantomOwnedStoreIntent intent, Player player, String sha)
    {
        final var state = intent.after(); final var id = state.identity(); final var vitals = state.vitals();
        final StringBuilder text = new StringBuilder("source=native-sealed-snapshot\nbarrier=QUIESCENT_NATIVE_PREPARE\nexactArgument=true\ncodeSha=" + sha + "\n");
        put(text,"profileId",id.profileId()); put(text,"objectId",player.getObjectId()); put(text,"epoch",intent.materializedAtNanos()); put(text,"initialEpoch",intent.materializedAtNanos()); put(text,"preparedRowVersion",intent.preparedRowVersion());
        put(text,"level",player.getLevel()); put(text,"exp",player.getExp()); put(text,"sp",player.getSp()); put(text,"expBeforeDeath",player.getExpBeforeDeath());
        // These are the exact scalars supplied to Player.OwnedStoreSnapshot, already canonicalized by the existing resolver.
        put(text,"hp",vitals.currentHp()); put(text,"maxHp",vitals.maximumHp()); put(text,"mp",vitals.currentMp()); put(text,"maxMp",vitals.maximumMp()); put(text,"cp",vitals.currentCp()); put(text,"maxCp",vitals.maximumCp());
        put(text,"rawHp",player.getCurrentHp()); put(text,"rawMp",player.getCurrentMp()); put(text,"rawCp",player.getCurrentCp());
        put(text,"x",player.getX()); put(text,"y",player.getY()); put(text,"z",player.getZ()); put(text,"heading",player.getHeading()); put(text,"classIndex",player.getClassIndex()); put(text,"classId",player.getActiveClass()); put(text,"race",player.getRace().ordinal()); put(text,"vitality",player.getVitalityPoints());
        put(text,"inventoryHash",state.inventory().canonicalHash()); put(text,"skillsHash",intent.skillsHash()); put(text,"receiptSha256",PhantomBackgroundTransaction.payloadDigest(intent.encode())); put(text,"nativeOwnerSealed",true);
        return text.toString();
    }
    private static void put(StringBuilder text, String key, Object value) { text.append(key).append('=').append(value).append('\n'); }
    private static synchronized void drain() throws Exception
    {
        Witness witness;
        while ((witness = RING.poll()) != null) { write(witness.path(), witness.text()); EXPORTED.incrementAndGet(); }
    }
    private static void status(Path output) throws Exception
    {
        final var owners = new java.util.ArrayList<String>();
        for (var entry : dispatchOwners.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
        {
            final var row = new java.util.LinkedHashMap<String, String>();
            row.put("profileId", Long.toString(entry.getKey()));
            row.put("epoch", Long.toString(entry.getValue().epoch()));
            row.putAll(entry.getValue().diagnosticScalars());
            owners.add("{" + row.entrySet().stream().map(value -> jsonString(value.getKey()) + ":" + jsonString(value.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}");
        }
        write(output.resolve("final-owner-state-" + System.nanoTime() + ".json"), "[" + String.join(",", owners) + "]\n");
        final String text = "owner=TASK026_CONTRACT\ncaptured=" + CAPTURED.get() + "\nexported=" + EXPORTED.get() + "\npending=" + RING.size() + "\noverflow=" + OVERFLOW.get() + "\nexporterFailure=" + exporterFailure + "\n";
        write(output.resolve("observer-status-" + System.nanoTime() + ".properties"), text);
    }
    private static void write(Path target, String text) throws Exception
    {
        try (var channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        {
            final var bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)); while (bytes.hasRemaining()) { channel.write(bytes); } channel.force(true);
        }
    }
}

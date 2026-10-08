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
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;

/** Task-only exact-argument observer. Hook has no World scan, I/O, waits or other actor locks. */
public final class Contract025Observer
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
    private static boolean installed;
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
        if (!runtime.toString().matches(".*[\\\\/]contract025[a-h][\\\\/]runtime") || !Path.of("").toRealPath().equals(runtime.resolve("game"))) { throw new IllegalStateException("TASK025_RUNTIME_GUARD"); }
        if (!specPath.startsWith(runtime.getParent()) || !spec.getProperty("pid").equals(Long.toString(ProcessHandle.current().pid())) || !"TASK025_CONTRACT".equals(spec.getProperty("owner"))) { throw new IllegalStateException("TASK025_PROCESS_GUARD"); }
        final var start = ProcessHandle.current().info().startInstant().orElseThrow();
        final long ticks = 621355968000000000L + start.getEpochSecond() * 10000000L + start.getNano() / 100;
        if (ticks / 10000 != Long.parseLong(spec.getProperty("startTicks")) / 10000) { throw new IllegalStateException("TASK025_START_GUARD"); }
        final Path output = Path.of(spec.getProperty("output")).toAbsolutePath().normalize();
        final Path module = runtime.getParent().getParent().getParent();
        if (!output.startsWith(module.resolve("docs/phantoms/tasks/PHANTOM-M1-CHECKPOINT-RECOVERY-025/evidence"))) { throw new IllegalStateException("TASK025_OUTPUT_GUARD"); }
        Files.createDirectories(output);
        if ("FLUSH".equals(spec.getProperty("mode"))) { drain(); status(output); return; }
        final String mode = spec.getProperty("mode");
        if (!java.util.Set.of("OBSERVE", "CRASH_NATIVE", "CRASH_FINALIZE").contains(mode)) { throw new IllegalStateException("TASK025_MODE_GUARD"); }
        final Map<Long, Long> selected = new HashMap<>();
        for (String key : spec.stringPropertyNames()) { if (key.startsWith("profile.")) { selected.put(Long.parseLong(key.substring(8)), Long.parseLong(spec.getProperty(key))); } }
        if (selected.isEmpty() || selected.size() > 8) { throw new IllegalStateException("TASK025_COHORT_GUARD"); }
        // Attach preflight is outside native hooks/critical sections.
        for (Player player : World.getInstance().getPlayers()) { if (player.getClient() != null) { throw new IllegalStateException("TASK025_REAL_PRESENT"); } }
        final Selection nextSelection = new Selection(Map.copyOf(selected), output, spec.getProperty("codeSha"));
        if (!mode.equals("OBSERVE"))
        {
            if (!runtime.toString().matches(".*[\\\\/]contract025" + (mode.equals("CRASH_NATIVE") ? "e" : "f") + "[\\\\/]runtime")) { throw new IllegalStateException("TASK025_SEPARATE_CRASH_LANE"); }
            if (installed || crash != null || !org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.isEnabled()
                || !org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig.isSyntheticEnabled()) { throw new IllegalStateException("TASK025_CRASH_PREFLIGHT"); }
            final Path dump = Path.of(spec.getProperty("preDump")).toRealPath();
            if (!dump.startsWith(output) || !PhantomBackgroundTransaction.payloadDigest(Files.readAllBytes(dump)).equals(spec.getProperty("preDumpHash"))) { throw new IllegalStateException("TASK025_CRASH_DUMP_GUARD"); }
            final var configured = field(org.l2jmobius.gameserver.phantoms.PhantomSystem.class, "_configuredInstance").get(null);
            if (configured == null) { throw new IllegalStateException("TASK025_SYSTEM_ABSENT"); }
            final var background = field(configured.getClass(), "_backgroundService").get(configured);
            final var transaction = (PhantomBackgroundTransaction) field(background.getClass(), "_transactions").get(background);
            final Field injector = field(PhantomBackgroundTransaction.class, "_faultInjector");
            final var original = (PhantomBackgroundTransaction.FaultInjector) injector.get(transaction);
            crash = new Crash(mode, output, spec.getProperty("preDumpHash"));
            injector.set(transaction, (PhantomBackgroundTransaction.FaultInjector) point -> { original.inject(point); crashWindow(point); });
        }
        selection = nextSelection;
        if (!installed)
        {
            field(PhantomNativeWorkScope.class, "_checkpointObserver").set(null, (BiConsumer<Player, String>) Contract025Observer::observe);
            final Thread exporter = new Thread(() ->
            {
                while (true)
                {
                    try { Thread.sleep(100); drain(); }
                    catch (InterruptedException stopped) { return; }
                    catch (Exception failure) { exporterFailure = failure.toString(); }
                }
            }, "TASK025-witness-export");
            exporter.setDaemon(true); exporter.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
            {
                try { drain(); status(selection.output()); }
                catch (Exception failure) { exporterFailure = failure.toString(); System.err.println("TASK025_WITNESS_EXPORT_FAILED:" + failure); }
            }, "TASK025-witness-final-export"));
            installed = true;
        }
        write(output.resolve("observer-installed.properties"), "owner=TASK025_CONTRACT\nmode=" + mode + "\npid=" + ProcessHandle.current().pid() + "\ncodeSha=" + selection.sha() + "\nringCapacity=32\nexactArgument=true\n");
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
                || THREAD.get(owner) != Thread.currentThread() || !STATE.get(owner).toString().equals("SEALED") || player.getObjectId() != intent.after().identity().characterObjectId()) { throw new IllegalStateException("TASK025_EXACT_SEALED_ARGUMENT"); }
            final String key = id + "-" + initialEpoch + "-" + intent.preparedRowVersion();
            final Witness witness = new Witness(chosen.output().resolve(key + ".properties"), snapshot(intent, player, chosen.sha()));
            if (crash != null && owner.evidence().snapshot(System.nanoTime()).rewardSequence() > 0)
            {
                PREPARED.set(new Prepared(player, (PhantomNativeWorkScope) owner, intent, witness));
                return;
            }
            if (!RING.offer(witness)) { OVERFLOW.incrementAndGet(); throw new IllegalStateException("TASK025_WITNESS_RING_OVERFLOW"); }
            CAPTURED.incrementAndGet();
        }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("TASK025_WITNESS_ARGUMENT", failure); }
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
                || intentField.get(BOUNDARY.get(player)) != intent || World.getInstance().getPlayer(player.getObjectId()) != player) { throw new IllegalStateException("TASK025_EXACT_WINDOW_OWNER"); }
            // Enabled native login acquires REAL_LOGIN before Player load (GameClient.load). No World/materialization scan or foreign Player locks in the hook.
            final var registry = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance();
            final var owners = (Map<?, ?>) field(registry.getClass(), "_owners").get(registry);
            for (Object objectId : owners.keySet()) { if (registry.getOwnerKind((Integer) objectId) == org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.REAL_LOGIN) { throw new IllegalStateException("TASK025_REAL_LEASE_AT_WINDOW"); } }
            final byte[] bytes = prepared.witness().text().getBytes(StandardCharsets.UTF_8);
            write(prepared.witness().path(), prepared.witness().text());
            final String sql = sqlAtWindow(player.getObjectId());
            write(control.output().resolve("window-sql.tsv"), sql);
            final int exit = control.mode().equals("CRASH_NATIVE") ? 72 : 73;
            write(control.output().resolve("planned-crash.properties"), "owner=TASK025_CONTRACT\nmode=" + control.mode() + "\nfaultPoint=" + point + "\npid=" + ProcessHandle.current().pid()
                + "\nprofileId=" + intent.after().identity().profileId() + "\nobjectId=" + player.getObjectId() + "\nepoch=" + owner.epoch() + "\npreparedRowVersion=" + intent.preparedRowVersion()
                + "\nreceiptSha256=" + PhantomBackgroundTransaction.payloadDigest(intent.encode()) + "\nsnapshotFile=" + prepared.witness().path().getFileName() + "\nsnapshotSha256=" + PhantomBackgroundTransaction.payloadDigest(bytes)
                + "\nsqlSha256=" + PhantomBackgroundTransaction.payloadDigest(sql.getBytes(StandardCharsets.UTF_8)) + "\npreDumpSha256=" + control.dumpHash()
                + "\nREALcount=0\nREALcountSource=enabled-native-identity-registry\nexactArgument=true\nexit=" + exit + "\nstack=" + java.util.Arrays.toString(Thread.currentThread().getStackTrace()) + "\n");
            if (!CRASHED.compareAndSet(false, true)) { throw new IllegalStateException("TASK025_DUPLICATE_CRASH_WINDOW"); }
            Runtime.getRuntime().halt(exit);
        }
        catch (Exception failure) { throw new IllegalStateException("TASK025_PLANNED_CRASH_REFUSED:" + point, failure); }
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
        if (!text.toString().matches("(?s).*l2jmobiush5_localplay_contract025[e-f].*")) { throw new IllegalStateException("TASK025_CRASH_DB_GUARD"); }
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
        final String text = "owner=TASK025_CONTRACT\ncaptured=" + CAPTURED.get() + "\nexported=" + EXPORTED.get() + "\npending=" + RING.size() + "\noverflow=" + OVERFLOW.get() + "\nexporterFailure=" + exporterFailure + "\n";
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

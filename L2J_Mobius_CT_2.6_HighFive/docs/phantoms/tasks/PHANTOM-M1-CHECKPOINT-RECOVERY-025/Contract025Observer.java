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
        if (!"OBSERVE".equals(spec.getProperty("mode"))) { throw new IllegalStateException("TASK025_MODE_GUARD"); }
        final Map<Long, Long> selected = new HashMap<>();
        for (String key : spec.stringPropertyNames()) { if (key.startsWith("profile.")) { selected.put(Long.parseLong(key.substring(8)), Long.parseLong(spec.getProperty(key))); } }
        if (selected.isEmpty() || selected.size() > 8) { throw new IllegalStateException("TASK025_COHORT_GUARD"); }
        // Attach preflight is outside native hooks/critical sections.
        for (Player player : World.getInstance().getPlayers()) { if (player.getClient() != null) { throw new IllegalStateException("TASK025_REAL_PRESENT"); } }
        selection = new Selection(Map.copyOf(selected), output, spec.getProperty("codeSha"));
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
        write(output.resolve("observer-installed.properties"), "owner=TASK025_CONTRACT\nmode=OBSERVE\npid=" + ProcessHandle.current().pid() + "\ncodeSha=" + selection.sha() + "\nringCapacity=32\nexactArgument=true\n");
    }
    private static void observe(Player player, String phase)
    {
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
            if (!RING.offer(witness)) { OVERFLOW.incrementAndGet(); throw new IllegalStateException("TASK025_WITNESS_RING_OVERFLOW"); }
            CAPTURED.incrementAndGet();
        }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("TASK025_WITNESS_ARGUMENT", failure); }
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

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal;

/** TASK024 only: delegates the existing fault injector and exports immutable sealed receipts. */
final class Contract024Agent5
{
    private record Selection(Map<Long, Long> profiles, Path output, boolean followCurrentEpochs) {}
    private static final class Binding
    {
        final String mode;
        volatile Selection selection;
        Binding(String mode, Selection selection) { this.mode = mode; this.selection = selection; }
    }
    private static final Map<Object, Binding> INSTALLED = new ConcurrentHashMap<>();
    private static Field field(Class<?> type, String name) throws Exception
    {
        final Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static java.util.Set<Player> nativePlayers(PhantomSystem configured) throws Exception
    {
        final var players = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Player, Boolean>());
        players.addAll(World.getInstance().getPlayers());
        final Object materialization = field(PhantomSystem.class, "_materializationService").get(configured);
        final var entries = (Map<?, ?>) field(materialization.getClass(), "_activeByProfile").get(materialization);
        for (Object entry : entries.values())
        {
            final Object actor = field(entry.getClass(), "_materializedPlayer").get(entry);
            final Player player = (Player) field(actor.getClass(), "_player").get(actor);
            if (player != null) { players.add(player); }
        }
        return players;
    }
    public static void agentmain(String argument, Instrumentation instrumentation) throws Exception
    {
        final Path specPath = Path.of(argument).toRealPath();
        final Properties spec = new Properties();
        try (var reader = Files.newBufferedReader(specPath, StandardCharsets.UTF_8)) { spec.load(reader); }
        final Path runtime = Path.of(spec.getProperty("runtime")).toRealPath();
        if (!runtime.toString().matches(".*[\\\\/]contract024[a-h][\\\\/]runtime") || !Path.of("").toRealPath().equals(runtime.resolve("game"))) { throw new IllegalStateException("TASK024_RUNTIME_GUARD"); }
        if (!specPath.startsWith(runtime.getParent()) || !spec.getProperty("pid").equals(Long.toString(ProcessHandle.current().pid()))) { throw new IllegalStateException("TASK024_PROCESS_GUARD"); }
        final long ticks = Math.addExact(621355968000000000L, ProcessHandle.current().info().startInstant().orElseThrow().getEpochSecond() * 10000000L + ProcessHandle.current().info().startInstant().orElseThrow().getNano() / 100);
        // ProcessHandle exposes milliseconds on Windows; the wrapper checked exact native ticks.
        if (ticks / 10000 != Long.parseLong(spec.getProperty("startTicks")) / 10000) { throw new IllegalStateException("TASK024_START_GUARD"); }
        if (!spec.getProperty("owner").equals("TASK024_CONTRACT")) { throw new IllegalStateException("TASK024_OWNER_GUARD"); }
        final Path output = Path.of(spec.getProperty("output")).toAbsolutePath().normalize();
        final Path module = runtime.getParent().getParent().getParent();
        if (!output.startsWith(module.resolve("docs/phantoms/tasks/PHANTOM-M1-RUNTIME-CONTRACTS-024/evidence"))) { throw new IllegalStateException("TASK024_OUTPUT_GUARD"); }
        Files.createDirectories(output);
        final Map<Long, Long> selected = new HashMap<>();
        for (String key : spec.stringPropertyNames())
        {
            if (key.startsWith("profile.")) { selected.put(Long.parseLong(key.substring(8)), Long.parseLong(spec.getProperty(key))); }
        }
        if (selected.isEmpty() || selected.size() > 8) { throw new IllegalStateException("TASK024_COHORT_GUARD"); }
        for (Player player : World.getInstance().getPlayers()) { if (player.getClient() != null) { throw new IllegalStateException("TASK024_REAL_PRESENT"); } }
        final PhantomSystem configured = (PhantomSystem) field(PhantomSystem.class, "_configuredInstance").get(null);
        if (configured == null) { throw new IllegalStateException("TASK024_SYSTEM_ABSENT"); }
        final Object background = field(PhantomSystem.class, "_backgroundService").get(configured);
        final var topology = (org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService) field(PhantomSystem.class, "_topologyService").get(configured);
        final var population = (org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager) field(PhantomSystem.class, "_populationManager").get(configured);
        final var materialization = (org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService) field(PhantomSystem.class, "_materializationService").get(configured);
        final var history = (org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService) field(PhantomSystem.class, "_historicalBackgroundService").get(configured);
        final var ecology = (org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService) field(PhantomSystem.class, "_populationEcology").get(configured);
        final StringBuilder diagnostics = new StringBuilder("source=READ_ONLY_CURRENT_RUNTIME\n");
        diagnostics.append("ecology=").append(ecology.snapshot()).append('\n');
        for (long id : selected.keySet().stream().sorted().toList())
        {
            diagnostics.append("profile=").append(id).append(";presenceOnline=").append(population.presence().isOnline(id))
                .append(";topology=").append(topology.findProfile(id)).append(";materialization=").append(materialization.find(id))
                .append(";due=").append(ecology.dueSnapshot(id)).append(";progress=").append(ecology.progressSnapshot(id))
                .append(";history=").append(history.status(id)).append('\n');
        }
        write(output.resolve("diagnostics-" + ProcessHandle.current().pid() + ".txt"), diagnostics.toString());
        final PhantomBackgroundTransaction transaction = (PhantomBackgroundTransaction) field(background.getClass(), "_transactions").get(background);
        final String mode = spec.getProperty("mode", "OBSERVE");
        if (!java.util.Set.of("OBSERVE", "CRASH_NATIVE", "CRASH_FINALIZE").contains(mode)) { throw new IllegalStateException("TASK024_MODE_GUARD"); }
        final Binding binding = new Binding(mode, new Selection(Map.copyOf(selected), output, Boolean.parseBoolean(spec.getProperty("followCurrentEpochs", "false"))));
        final Binding previous = INSTALLED.putIfAbsent(transaction, binding);
        if (previous != null)
        {
            if (!previous.mode.equals("OBSERVE") || !mode.equals("OBSERVE")) { throw new IllegalStateException("TASK024_ALREADY_INSTALLED"); }
            previous.selection = binding.selection;
            LocalPlayPhantomStoreJournal.select(selected);
            write(output.resolve("agent-selected-" + ProcessHandle.current().pid() + ".txt"), "owner=TASK024_CONTRACT\nmode=" + mode + "\npid=" + ProcessHandle.current().pid() + "\ncohort=" + selected + "\n");
            return;
        }
        final Field injectorField = field(PhantomBackgroundTransaction.class, "_faultInjector");
        final PhantomBackgroundTransaction.FaultInjector original = (PhantomBackgroundTransaction.FaultInjector) injectorField.get(transaction);
        final AtomicBoolean crashed = new AtomicBoolean();
        final Map<String, Boolean> exported = new ConcurrentHashMap<>();
        LocalPlayPhantomStoreJournal.select(selected);
        injectorField.set(transaction, (PhantomBackgroundTransaction.FaultInjector) point ->
        {
            original.inject(point);
            try
            {
                final Selection selection = binding.selection;
                if (point == PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_PREPARE)
                {
                    for (Player player : nativePlayers(configured))
                    {
                        final Object boundary = field(Player.class, "_ownedStoreBoundary").get(player);
                        if (boundary == null || !boundary.getClass().getName().startsWith(background.getClass().getName() + "$")) { continue; }
                        final PhantomOwnedStoreIntent intent = (PhantomOwnedStoreIntent) field(boundary.getClass(), "_intent").get(boundary);
                        if (intent == null || !selection.profiles().containsKey(intent.after().identity().profileId())) { continue; }
                        final long initialEpoch = selection.profiles().get(intent.after().identity().profileId());
                        if (!selection.followCurrentEpochs() && initialEpoch != intent.materializedAtNanos()) { continue; }
                        final var lifetime = materialization.find(intent.after().identity().profileId()).orElse(null);
                        if (lifetime == null || lifetime.materializedAtNanos() != intent.materializedAtNanos() || lifetime.characterObjectId() != player.getObjectId()) { continue; }
                        if (player.getNativeWorkOwner() == null || !player.getNativeWorkOwner().isCurrent() || !player.getNativeWorkOwner().sealed()) { continue; }
                        if (field(player.getNativeWorkOwner().getClass(), "_checkpointThread").get(player.getNativeWorkOwner()) != Thread.currentThread()) { continue; }
                        if (player.getNativeWorkOwner().epoch() != intent.materializedAtNanos() || player.getObjectId() != intent.after().identity().characterObjectId()) { throw new IllegalStateException("TASK024_SEALED_SNAPSHOT_GUARD"); }
                        final String key = intent.after().identity().profileId() + "-" + intent.materializedAtNanos() + "-" + intent.preparedRowVersion();
                        if (exported.putIfAbsent(key, Boolean.TRUE) == null) { snapshot(selection.output().resolve(key + ".properties"), intent, player, initialEpoch); }
                    }
                }
                // Planned crash handling is added only after its independent exact-owned preflight.
            }
            catch (Exception failure)
            {
                System.err.println("TASK024_COLLECTOR_FAILED:" + point + ":" + failure);
            }
        });
        write(output.resolve("agent-installed-" + ProcessHandle.current().pid() + ".txt"), "owner=TASK024_CONTRACT\nmode=" + mode + "\npid=" + ProcessHandle.current().pid() + "\ncohort=" + selected + "\n");
    }

    private static void snapshot(Path target, PhantomOwnedStoreIntent intent, Player player, long initialEpoch) throws Exception
    {
        final var state = intent.after(); final var p = state.progress(); final var v = state.vitals(); final var xyz = state.position(); final var id = state.identity();
        final StringBuilder text = new StringBuilder("source=native-sealed-snapshot\nbarrier=QUIESCENT_NATIVE_PREPARE\n");
        put(text,"profileId",id.profileId()); put(text,"objectId",id.characterObjectId()); put(text,"epoch",intent.materializedAtNanos()); put(text,"preparedRowVersion",intent.preparedRowVersion());
        put(text,"initialEpoch",initialEpoch); put(text,"epochTransition",initialEpoch != intent.materializedAtNanos()); put(text,"currentLifetimeVerified",true);
        put(text,"level",p.level()); put(text,"exp",p.experience()); put(text,"sp",p.skillPoints()); put(text,"expBeforeDeath",p.experienceBeforeDeath());
        put(text,"hp",v.currentHp()); put(text,"maxHp",v.maximumHp()); put(text,"mp",v.currentMp()); put(text,"maxMp",v.maximumMp()); put(text,"cp",v.currentCp()); put(text,"maxCp",v.maximumCp());
        put(text,"x",xyz.x()); put(text,"y",xyz.y()); put(text,"z",xyz.z()); put(text,"heading",xyz.heading()); put(text,"classIndex",id.classIndex()); put(text,"classId",id.activeClassId()); put(text,"race",id.raceOrdinal());
        put(text,"vitality",player.getVitalityPoints()); put(text,"inventoryHash",state.inventory().canonicalHash()); put(text,"skillsHash",intent.skillsHash());
        put(text,"receiptSha256",PhantomBackgroundTransaction.payloadDigest(intent.encode())); put(text,"nativeOwnerSealed",player.getNativeWorkOwner().sealed());
        write(target,text.toString());
    }
    private static void put(StringBuilder target,String key,Object value) { target.append(key).append('=').append(value).append('\n'); }
    private static void write(Path target,String text) throws Exception
    {
        try (var channel = FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        {
            final var bytes = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)); while (bytes.hasRemaining()) { channel.write(bytes); } channel.force(true);
        }
    }
}

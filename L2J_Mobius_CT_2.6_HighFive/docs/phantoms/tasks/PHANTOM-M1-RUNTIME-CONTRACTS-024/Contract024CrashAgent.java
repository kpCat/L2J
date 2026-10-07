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
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.localplay.LocalPlayPhantomStoreJournal;

/** TASK024 only: delegates the existing fault injector and exports immutable sealed receipts. */
final class Contract024CrashAgent
{
    private record Selection(Map<Long, Long> profiles, Path output) {}
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
        final PhantomBackgroundTransaction transaction = (PhantomBackgroundTransaction) field(background.getClass(), "_transactions").get(background);
        final String mode = spec.getProperty("mode", "OBSERVE");
        if (!java.util.Set.of("OBSERVE", "CRASH_NATIVE", "CRASH_FINALIZE").contains(mode)) { throw new IllegalStateException("TASK024_MODE_GUARD"); }
        final Binding binding = new Binding(mode, new Selection(Map.copyOf(selected), output));
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
        if (mode.equals("OBSERVE")) { throw new IllegalStateException("TASK024_CRASH_AGENT_MODE"); }
        final Path dump = Path.of(spec.getProperty("preDump")).toRealPath();
        if (!dump.startsWith(output) || !PhantomBackgroundTransaction.payloadDigest(Files.readAllBytes(dump)).equals(spec.getProperty("preDumpHash"))) { throw new IllegalStateException("TASK024_DUMP_GUARD"); }
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
                        if (intent == null || !java.util.Objects.equals(selection.profiles().get(intent.after().identity().profileId()), intent.materializedAtNanos())) { continue; }
                        if (player.getNativeWorkOwner() == null || !player.getNativeWorkOwner().sealed()) { continue; }
                        if (field(player.getNativeWorkOwner().getClass(), "_checkpointThread").get(player.getNativeWorkOwner()) != Thread.currentThread()) { continue; }
                        if (player.getNativeWorkOwner().epoch() != intent.materializedAtNanos() || player.getObjectId() != intent.after().identity().characterObjectId()) { throw new IllegalStateException("TASK024_SEALED_SNAPSHOT_GUARD"); }
                        final String key = intent.after().identity().profileId() + "-" + intent.materializedAtNanos() + "-" + intent.preparedRowVersion();
                        if (exported.putIfAbsent(key, Boolean.TRUE) == null) { snapshot(selection.output().resolve(key + ".properties"), intent, player); }
                    }
                }
                final boolean window = mode.equals("CRASH_NATIVE") && point == PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_NATIVE_STORE
                    || mode.equals("CRASH_FINALIZE") && point == PhantomBackgroundTransaction.FaultPoint.AFTER_OWNED_FINALIZE_COMMIT;
                if (window && !crashed.get())
                {
                    for (Player player : nativePlayers(configured))
                    {
                        final var owner = player.getNativeWorkOwner();
                        if (owner == null || !owner.sealed() || field(owner.getClass(), "_checkpointThread").get(owner) != Thread.currentThread()) { continue; }
                        final Object boundary = field(Player.class, "_ownedStoreBoundary").get(player);
                        if (boundary == null || !boundary.getClass().getName().startsWith(background.getClass().getName() + "$")) { continue; }
                        final PhantomOwnedStoreIntent intent = (PhantomOwnedStoreIntent) field(boundary.getClass(), "_intent").get(boundary);
                        if (intent == null || !java.util.Objects.equals(selection.profiles().get(intent.after().identity().profileId()), intent.materializedAtNanos()) || !owner.isCurrent() || owner.epoch() != intent.materializedAtNanos()) { continue; }
                        for (Player human : World.getInstance().getPlayers()) { if (human.getClient() != null) { throw new IllegalStateException("TASK024_REAL_PRESENT_AT_WINDOW"); } }
                        final String key = intent.after().identity().profileId()+"-"+intent.materializedAtNanos()+"-"+intent.preparedRowVersion();
                        final Path snapshotFile = selection.output().resolve(key+".properties");
                        if (!Files.exists(snapshotFile)) { throw new IllegalStateException("TASK024_WINDOW_SNAPSHOT_MISSING"); }
                        final String sql = sqlAtWindow(player.getObjectId());
                        write(selection.output().resolve("window-sql.tsv"), sql);
                        final int exit = mode.equals("CRASH_NATIVE") ? 72 : 73;
                        final String stack = java.util.Arrays.toString(Thread.currentThread().getStackTrace());
                        write(selection.output().resolve("planned-crash.properties"), "owner=TASK024_CONTRACT\nmode="+mode+"\nfaultPoint="+point+"\npid="+ProcessHandle.current().pid()+"\nprofileId="+intent.after().identity().profileId()+"\nobjectId="+player.getObjectId()+"\nepoch="+intent.materializedAtNanos()+"\npreparedRowVersion="+intent.preparedRowVersion()+"\nreceiptSha256="+PhantomBackgroundTransaction.payloadDigest(intent.encode())+"\nsnapshotFile="+snapshotFile.getFileName()+"\nsnapshotSha256="+PhantomBackgroundTransaction.payloadDigest(Files.readAllBytes(snapshotFile))+"\nsqlSha256="+PhantomBackgroundTransaction.payloadDigest(sql.getBytes(StandardCharsets.UTF_8))+"\npreDumpSha256="+spec.getProperty("preDumpHash")+"\nREALcount=0\nexit="+exit+"\nstack="+stack+"\n");
                        if (!crashed.compareAndSet(false, true)) { throw new IllegalStateException("TASK024_DUPLICATE_WINDOW"); }
                        Runtime.getRuntime().halt(exit);
                    }
                }
            }
            catch (Exception failure)
            {
                System.err.println("TASK024_COLLECTOR_FAILED:" + point + ":" + failure);
            }
        });
        write(output.resolve("agent-installed-" + ProcessHandle.current().pid() + ".txt"), "owner=TASK024_CONTRACT\nmode=" + mode + "\npid=" + ProcessHandle.current().pid() + "\ncohort=" + selected + "\n");
    }

    private static String sqlAtWindow(int objectId) throws Exception
    {
        final StringBuilder text = new StringBuilder();
        try (var connection = DatabaseFactory.getConnection())
        {
            for (String query : java.util.List.of("SELECT DATABASE() AS database_name", "SELECT charId,level,exp,sp,expBeforeDeath,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race,vitality_points FROM characters WHERE charId="+objectId,
                "SELECT object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id="+objectId+" ORDER BY object_id",
                "SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId="+objectId+" ORDER BY class_index,skill_id",
                "SELECT c.profile_id,c.component_type,c.row_version,HEX(c.payload) AS payload FROM phantom_profile_components c JOIN phantom_profiles p ON p.profile_id=c.profile_id WHERE p.character_object_id="+objectId+" AND c.component_type IN ('background.state','background.native-context','background.owned-store') ORDER BY c.component_type"))
            {
                text.append("QUERY\t").append(query).append('\n');
                try (var statement = connection.createStatement(); var result = statement.executeQuery(query))
                {
                    final var metadata = result.getMetaData();
                    for (int i=1;i<=metadata.getColumnCount();i++) { if(i>1)text.append('\t'); text.append(metadata.getColumnLabel(i)); } text.append('\n');
                    while (result.next()) { for(int i=1;i<=metadata.getColumnCount();i++) { if(i>1)text.append('\t'); text.append(result.getString(i)); } text.append('\n'); }
                }
            }
        }
        if (!text.toString().matches("(?s).*l2jmobiush5_localplay_contract024[a-h].*")) { throw new IllegalStateException("TASK024_WINDOW_DB_GUARD"); }
        return text.toString();
    }

    private static void snapshot(Path target, PhantomOwnedStoreIntent intent, Player player) throws Exception
    {
        final var state = intent.after(); final var p = state.progress(); final var v = state.vitals(); final var xyz = state.position(); final var id = state.identity();
        final StringBuilder text = new StringBuilder("source=native-sealed-snapshot\nbarrier=QUIESCENT_NATIVE_PREPARE\n");
        put(text,"profileId",id.profileId()); put(text,"objectId",id.characterObjectId()); put(text,"epoch",intent.materializedAtNanos()); put(text,"preparedRowVersion",intent.preparedRowVersion());
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

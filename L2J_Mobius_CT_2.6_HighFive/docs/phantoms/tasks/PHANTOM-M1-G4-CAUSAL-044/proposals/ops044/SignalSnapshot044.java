import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;

/** One passive read in the already-running044a diagnostic; no native control or background worker. */
public final class SignalSnapshot044
{
    public static void agentmain(String argument, Instrumentation instrumentation) throws Exception
    {
        final String[] fields = argument.split("\n", -1);
        if (fields.length != 4 || !fields[0].equals(System.getProperty("phantom.localplay.runtime")) || !"GameServer".equals(System.getProperty("phantom.localplay.role"))) { throw new IllegalArgumentException("Exact own Game identity required."); }
        final Path runtime = Path.of(fields[1]).toRealPath();
        if (!runtime.toString().replace('\\', '/').endsWith("/contract044a/runtime") || !runtime.resolve("game").toRealPath().equals(org.l2jmobius.gameserver.config.ServerConfig.DATAPACK_ROOT.toPath().toRealPath()) || !org.l2jmobius.commons.config.DatabaseConfig.DATABASE_URL.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5_phantom_test_044a\\?.*")) { throw new IllegalArgumentException("Exact044a root/catalog required."); }
        final Path output = Path.of(fields[3]).toAbsolutePath().normalize();
        final Path module = runtime.getParent().getParent().getParent();
        if (!output.startsWith(module.resolve("docs/phantoms/tasks/PHANTOM-M1-G4-CAUSAL-044/evidence")) || Files.exists(output)) { throw new IllegalArgumentException("New own044 evidence path required."); }
        final long profile = Long.parseLong(fields[2]);
        final var instance = PhantomSystem.class.getDeclaredField("_configuredInstance"); instance.setAccessible(true);
        final Object system = instance.get(null);
        final var schedulerField = PhantomSystem.class.getDeclaredField("_scheduler"); schedulerField.setAccessible(true);
        final PhantomScheduler scheduler = (PhantomScheduler) schedulerField.get(system);
        final var admission = PhantomSystem.operatorAdmissionProfile(profile).orElseThrow();
        final var materialization = admission.materialization();
        final var object = materialization == null ? null : World.getInstance().findObject(materialization.characterObjectId());
        final Player player = object instanceof Player value ? value : null;
        final var text = new StringBuilder("scope=PASSIVE_ONE_PROFILE_READ\nnativeReadConsistency=NONATOMIC_VOLATILE\nprofileId=" + profile + "\nsampleNanos=" + System.nanoTime() + "\n");
        text.append("sources=").append(scheduler.signalSources(profile)).append("\nadmission=").append(admission).append('\n');
        if (player != null) { text.append("hp=").append(player.getCurrentHp()).append("\ndead=").append(player.isDead()).append("\nmoving=").append(player.isMoving()).append("\nattacking=").append(player.isAttackingNow()).append("\ncasting=").append(player.isCastingNow()).append("\n"); }
        Files.writeString(output, text);
    }
}

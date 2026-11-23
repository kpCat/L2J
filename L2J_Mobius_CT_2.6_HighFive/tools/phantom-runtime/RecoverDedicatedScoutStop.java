import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

import com.sun.tools.attach.VirtualMachine;
import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;

/** Exact LIVE-001 recovery for the installed 008 GameServer. Submit STOP through the existing launcher. */
public final class RecoverDedicatedScoutStop
{
	private static final String ORIGINAL_GAME_SHA256 = "2d779ef577d857e1d035b89433f449b155c4cd100ffab3b853919ba999d49739";
	private static final String GAME_ROLE = "GameServer";

	private RecoverDedicatedScoutStop() { }

	public static void main(String[] args) throws Exception
	{
		final Target target = Target.parse(args);
		target.verifyProcess(ProcessHandle.of(target.pid()).orElseThrow());
		final Path agent = codeSource(RecoverDedicatedScoutStop.class);
		target.verifyFiles(agent);
		final VirtualMachine vm = VirtualMachine.attach(Long.toString(target.pid()));
		try
		{
			target.verifyJvm(vm.getSystemProperties());
			vm.loadAgent(agent.toString(), String.join("\n", args));
		}
		finally
		{
			vm.detach();
		}
		System.out.println("DEDICATED_SCOUT_STOP_REDEFINED classes=1 pid=" + target.pid());
	}

	public static final class Agent
	{
		private Agent() { }

		public static void agentmain(String arguments, Instrumentation instrumentation) throws Exception
		{
			final Target target = Target.parse(arguments.split("\n", -1));
			target.verifyProcess(ProcessHandle.current());
			target.verifyJvm(System.getProperties());
			target.verifyFiles(codeSource(Agent.class));
			if (!target.root().resolve("game").equals(ServerConfig.DATAPACK_ROOT.toPath().toRealPath())
				|| !DatabaseConfig.DATABASE_URL.startsWith("jdbc:mysql://127.0.0.1:" + target.port() + "/" + target.schema() + "?")
				|| !target.gameJar().equals(codeSource(PhantomSystem.class))
				|| !instrumentation.isRedefineClassesSupported() || !instrumentation.isModifiableClass(PhantomSystem.class))
			{
				throw new IllegalStateException("Recovery runtime, SQL or class source mismatch.");
			}
			final byte[] replacement;
			try (InputStream stream = Agent.class.getResourceAsStream("/recovery/PhantomSystem.class"))
			{
				if (stream == null) { throw new IOException("Recovery class missing."); }
				replacement = stream.readAllBytes();
			}
			// Read the exact legacy incident state under the existing shutdown monitor.
			final var configured = PhantomSystem.class.getDeclaredField("_configuredInstance");
			final var runningStop = PhantomSystem.class.getDeclaredField("_shutdownInProgress");
			configured.setAccessible(true);
			runningStop.setAccessible(true);
			final var system = (PhantomSystem) configured.get(null);
			if (system == null) { throw new IllegalStateException("No configured Phantom system to recover."); }
			final var state = PhantomSystem.configuredShutdownSnapshot();
			synchronized (system)
			{
				final var progress = system.shutdownProgress();
				if (configured.get(null) != system || runningStop.getBoolean(system)
					|| state.systemState() != PhantomSystem.State.FAILED
					|| state.materializationServiceState() != PhantomMaterializationService.ServiceState.RUNNING
					|| progress == null || progress.phase() != PhantomSystem.StopPhase.CLOSE_PRODUCERS
					|| progress.outcome() != PhantomSystem.StopOutcome.FAILED
					|| !"deadline:physical_scout_walk".equals(progress.blocker()))
				{
					throw new IllegalStateException("Recovery requires the exact legacy dedicated STOP deadline.");
				}
				instrumentation.redefineClasses(new ClassDefinition(PhantomSystem.class, replacement));
			}
		}
	}

	private record Target(long pid, long startedMillis, String runtimeMarker, Path root, int port, String schema, String agentSha256)
	{
		static Target parse(String[] args) throws IOException
		{
			if (args.length != 7 || !args[0].matches("[1-9][0-9]*") || !args[1].matches("[1-9][0-9]*")
				|| !args[2].matches("[0-9a-f]{64}") || !args[6].matches("[0-9a-fA-F]{64}"))
			{
				throw new IllegalArgumentException("Expected PID, start epoch milliseconds, runtime marker, root, SQL port, schema and agent SHA-256.");
			}
			if (!("33331".equals(args[4]) && "l2jmobiush5_phantom_test_131".equals(args[5]))
				&& !("33781".equals(args[4]) && "l2jmobiush5_phantom_test_008".equals(args[5])))
			{
				throw new IllegalArgumentException("SQL endpoint is outside the exact LIVE-001 and recovery TEST scopes.");
			}
			final Path root = Path.of(args[3]);
			if (!root.isAbsolute() || !root.equals(root.toRealPath()) || !Files.isRegularFile(root.resolve("game/config/hexid.txt")))
			{
				throw new IOException("Recovery requires the physical runtime root.");
			}
			return new Target(Long.parseLong(args[0]), Long.parseLong(args[1]), args[2], root, Integer.parseInt(args[4]), args[5], args[6]);
		}

		Path gameJar() { return root.resolve("libs/GameServer.jar"); }

		void verifyProcess(ProcessHandle process)
		{
			if (process.pid() != pid || !process.isAlive()
				|| process.info().startInstant().orElseThrow().toEpochMilli() != startedMillis)
			{
				throw new IllegalStateException("Recovery process incarnation changed.");
			}
		}

		void verifyJvm(Properties properties) throws IOException
		{
			if (!runtimeMarker.equals(properties.getProperty("phantom.localplay.runtime"))
				|| !GAME_ROLE.equals(properties.getProperty("phantom.localplay.role"))
				|| !root.resolve("game").equals(Path.of(properties.getProperty("user.dir", "")).toRealPath()))
			{
				throw new IllegalStateException("Recovery JVM is not the expected GameServer.");
			}
		}

		void verifyFiles(Path agent) throws Exception
		{
			if (!agentSha256.equalsIgnoreCase(sha256(agent)) || !ORIGINAL_GAME_SHA256.equals(sha256(gameJar())))
			{
				throw new IllegalStateException("Recovery agent or original GameServer checksum mismatch.");
			}
		}
	}

	private static Path codeSource(Class<?> type) throws Exception
	{
		return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
	}

	private static String sha256(Path path) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		try (InputStream stream = Files.newInputStream(path))
		{
			final byte[] buffer = new byte[8192];
			for (int size; (size = stream.read(buffer)) != -1;) { digest.update(buffer, 0, size); }
		}
		return HexFormat.of().formatHex(digest.digest());
	}
}

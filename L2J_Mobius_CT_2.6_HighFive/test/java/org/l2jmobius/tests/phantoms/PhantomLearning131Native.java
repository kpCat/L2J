/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.GameServer;
import org.l2jmobius.gameserver.Shutdown;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.NpcConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.CastMpDebit;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.ItemDebit;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.ResourceWrite;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.phantoms.PhantomLearning131Factory;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Runs the bounded N01 stock-lifetime observation against an independently commissioned TEST131 boot. */
public final class PhantomLearning131Native
{
	private static final String SCHEMA = "l2jmobiush5_phantom_test_131";
	private static final String JDBC = "jdbc:mysql://127.0.0.1:33331/" + SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=10000";
	private static final int CYCLES_REQUIRED = 3;
	private static final int MAX_DISTANCE = 1200;
	private static final int MAX_ARTIFACT_BYTES = 1_048_576;
	private static final int MAX_RESOURCE_ROWS = 8192;
	private static final String FIXTURE_HEADER = "status\tprofileId\tobjectId\taccount\trole\tclass\tlevel\tskillId\tskillLevel\tweapon\tshotItem\tshots\tadena\tanchor\tnpcId\tspawn\tpreflightObjectId\tx\ty\tz\texp\tsp";
	private static final String SCENE_HEADER = "profileId\tactorObjectId\tgoalId\tgoalRevision\tplanIdentity\tanchor\tx\ty\tz\tnpcId\tnpcLevel\tspawnKey\trespawnMinMs\trespawnMaxMs\tpreflightObjectId\tpreflightInstanceId\tpreflightGeneration\tqualifyingTargets\tknowledgeSha256\ttopologySha256\tprogressionSha256\tcommerceSha256";
	private static final String BIND_HEADER = "manifestSha\tbootId\tprofileId\tactorObjectId\tactorEpoch\tinstanceId\tnpcId\tspawnKey\tstockObjectId\tstockGeneration\tanchor\tgoalId\tgoalRevision\tplanIdentity\tboundAtUtc";
	private static final String CYCLE_HEADER = "profileId\tactorObjectId\tepoch\tcycle\tnpcId\ttargetObjectId\tinstanceId\tspawnGeneration\tnextObjectId\tnextGeneration\tdamageSequence\tkillSequence\trewardSequence\tsettledRewardSequence\texp\tsp\tshotDebitSinceBinding\tshotDebitWhileTargetSelected\tcastHitMpDebit\tstockDropCount\tpickupCount\tlootQuantity";

	private record Scene(long profile, int actor, String account, boolean mage, int profession, int level, int skill, int rank, int weapon, int shot,
		long stockShots, long stockAdena, long initialExp, long initialSp, long goal, long revision, String planIdentity, String anchor, PhantomTopologyPoint point,
		int npc, int npcLevel, String spawn, int respawnMin, int respawnMax, PhantomBackgroundState.Hashes hashes) { }
	private record Scope(Path execution, Path task, Path runtime, Path game, Path jar, Path sql, String campaign, String gameJarSha, String sourceCommit,
		String executionSha, String fixtureSha, String scenesSha, String enabledConfigSha, String jdbcSha, int maxCycles, int seconds, int drainSeconds, List<Scene> scenes) { }
	private record Launch(Path root, String manifestSha, String boot, String nonce, Instant deadline) { }
	private record Bound(Scene scene, Player player, PlayerNativeEvidence sensor, PlayerNativeEvidence.Snapshot initial, Target stock) { }
	private record Award(Target target, Target next, long cycle, long damage, long kill, long reward, long settled, long exp, long sp) { }
	private record Loot(int object, int item, long count) { }
	private record Totals(long exp, long sp, long shots, long wallet) { }
	private enum Status { COMPLETED, PAUSED, STOPPED, UNKNOWN }

	public static void main(String[] args)
	{
		try
		{
			check(args.length == 2 && args[0].equals("--preflight") || args.length == 3 && args[0].equals("--n01"), "MODE");
			final Scope scope = scope(Path.of(args[1]));
			if (args[0].equals("--preflight"))
			{
				System.out.println("TASK131_NATIVE_PREFLIGHT_READY phase=N01 scenes=" + scope.scenes().size() + " live=false");
				return;
			}
			run(scope, launch(scope, Path.of(args[2])));
		}
		catch (Throwable failure)
		{
			System.err.println("TASK131_NATIVE_REFUSED type=" + failure.getClass().getSimpleName());
			if (failure.getMessage() != null && failure.getMessage().matches("TASK131_[A-Z0-9_.-]+")) { System.err.println(failure.getMessage()); }
			System.exit(1);
		}
	}

	private static Scope scope(Path supplied) throws Exception
	{
		final Path execution = physical(supplied, true);
		final Path task = taskRoot(execution);
		final Properties fields = properties(execution);
		check("TRAINING_EXECUTION131_V1".equals(value(fields, "schema")) && "N01".equals(value(fields, "phase")) && n(fields, "maxEpisodes") == 4, "EXECUTION_SCHEMA");
		final String campaign = value(fields, "campaignId");
		final String source = value(fields, "sourceCommit");
		check(campaign.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}") && source.matches("[a-f0-9]{40}"), "CAMPAIGN_SCOPE");
		final String lane = value(fields, "sqlLane");
		check(lane.matches("n01-setup-r[1-9][0-9]*"), "ISOLATED_SQL_LANE");
		final Path sql = physical(task.resolve("sql-lanes").resolve(lane).resolve("sql-task131"), false);
		final Path fixture = physical(sql.resolve("n01-fixture.tsv"), true), scenes = physical(sql.resolve("n01-scenes.tsv"), true);
		final String fixtureSha = pin(fixture, value(fields, "fixtureSha")), sceneSha = pin(scenes, value(fields, "sceneSha"));
		final List<Scene> cohort = scenes(fixture, scenes);
		final Path sceneScope = physical(sql.resolve("n01-scene-scope.properties"), true);
		pin(sceneScope, value(fields, "sceneScopeSha"));
		final var sealed = properties(sceneScope);
		check("SETUP_CANDIDATES".equals(value(sealed, "stage")) && fixtureSha.equalsIgnoreCase(value(sealed, "fixtureSha256"))
			&& sceneSha.equalsIgnoreCase(value(sealed, "sceneSha256")) && "true".equals(value(sealed, "nextBootMustBindNativeTargets"))
			&& String.join(",", cohort.stream().map(scene -> Long.toString(scene.profile())).toList()).equals(value(sealed, "profiles")), "COMPLETE_FIXTURE_SEAL");

		final Path runtime = physical(Path.of(value(fields, "runtimeRoot")), false);
		check(runtime.getParent().equals(task) && runtime.getFileName().toString().matches("native-runtime(?:-r[1-9][0-9]*)?"), "OWN_RUNTIME_ROOT");
		check(!Files.exists(runtime.resolve("BOOT_DISABLED.txt"), LinkOption.NOFOLLOW_LINKS), "RUNTIME_BOOT_DISABLED");
		final Path game = physical(runtime.resolve("game"), false);
		final Path jar = physical(Path.of(value(fields, "gameJar")), true);
		check(jar.equals(runtime.resolve("libs/GameServer.jar")) && jar.equals(codeSource(GameServer.class)), "EXACT_ACTIVE_JAR");
		final String jarSha = pin(jar, value(fields, "gameJarSha"));
		check(!jarSha.equalsIgnoreCase("9CAE9D2382371D096E0BAEA26BB7097C45A449CEC255E31ED8D73D7FE9E2C5F3"), "BOOTSTRAP_JAR_ONLY");
		pin(classFile(PhantomLearning131Native.class), value(fields, "adapterClassSha"));
		pin(classFile(PhantomLearning131Factory.class), value(fields, "factoryClassSha"));
		final String jdbcSha = pin(physical(sql.resolve("Database.ini"), true), value(fields, "jdbcSha"));
		final var jdbc = properties(physical(game.resolve("config/Database.ini"), true));
		check(pin(game.resolve("config/Database.ini"), jdbcSha).equals(jdbcSha) && JDBC.equals(value(jdbc, "URL")) && "task131_runtime".equals(value(jdbc, "Login"))
			&& "com.mysql.cj.jdbc.Driver".equals(value(jdbc, "Driver")) && "false".equals(value(jdbc, "BackupDatabase")), "EXACT_OWN_JDBC");
		final var server = properties(physical(game.resolve("config/Server.ini"), true));
		check("127.0.0.1".equals(value(server, "GameserverHostname")) && n(server, "GameserverPort") == 33332 && "127.0.0.1".equals(value(server, "LoginHost"))
			&& n(server, "LoginPort") == 33333, "OWN_GAME_PORTS");
		final var bootConfig = properties(physical(game.resolve("config/Custom/PhantomPlayers.ini"), true));
		check("false".equalsIgnoreCase(value(bootConfig, "EnablePhantomSystem")), "BOOT_MASTER_OFF");
		final Path enabled = physical(game.resolve("config/Custom/PhantomPlayers.n01.ini"), true);
		final String enabledSha = pin(enabled, value(fields, "enabledPhantomIniSha"));
		final var settings = PhantomPlayersConfig.read(enabled);
		check(settings.enabled() && settings.populationTarget() == 4 && settings.populationActiveTarget() == 4 && settings.maxMaterializedPhantoms() == 4
			&& settings.materializationBurstSlots() == 0 && !settings.partySeeking().enabled() && settings.starterShotLifetimeLimit() == 0
			&& "OFF".equals(value(properties(enabled), "LearningMode")) && !Boolean.getBoolean("phantom.learning.train"), "BOUNDED_N01_SETTINGS");
		final int maxCycles = n(fields, "maxCyclesPerActor"), seconds = n(fields, "observeSeconds"), drainSeconds = n(fields, "drainSeconds");
		check(maxCycles >= CYCLES_REQUIRED && maxCycles <= 12 && seconds > 0 && seconds <= 1200 && drainSeconds > 0 && drainSeconds <= 120, "N01_QUOTAS");
		return new Scope(execution, task, runtime, game, jar, sql, campaign, jarSha, source, sha(execution), fixtureSha, sceneSha, enabledSha, jdbcSha, maxCycles, seconds, drainSeconds, cohort);
	}

	private static Launch launch(Scope scope, Path supplied) throws Exception
	{
		final Path path = physical(supplied, true), root = path.getParent();
		final Path campaign = scope.task().resolve("campaigns/n01");
		check("n01".equals(scope.campaign()) && scope.execution().equals(campaign.resolve("execution.properties"))
			&& path.getFileName().toString().equals("launch131.properties") && root.getParent().equals(campaign.resolve("boots"))
			&& root.getFileName().toString().matches("[0-9]{5}"), "OWN_BOOT_ROOT");
		check(physical(Path.of("").toAbsolutePath().normalize(), false).equals(scope.game()), "OWN_GAME_WORKING_DIRECTORY");
		final var fields = properties(path);
		final String nonce = value(fields, "nonce"), boot = value(fields, "bootId"), manifest = value(fields, "manifestSha");
		check("TRAINING_LAUNCH131_V1".equals(value(fields, "schema")) && "N01".equals(value(fields, "phase")) && scope.campaign().equals(value(fields, "campaignId"))
			&& scope.executionSha().equalsIgnoreCase(value(fields, "executionSha")) && nonce.matches("[0-9a-f]{32}") && nonce.equals(System.getProperty("phantom.learning131.nonce"))
			&& root.getFileName().toString().equals(boot) && root.equals(Path.of(value(fields, "bootRoot"))) && manifest.matches("(?i)[a-f0-9]{64}")
			&& n(fields, "lastCompleted") == 0, "BOOT_LAUNCH_BINDING");
		final Path manifestPath = physical(campaign.resolve("campaign.json"), true);
		pin(manifestPath, manifest);
		final String manifestBody = smallText(manifestPath);
		check(jsonScalar(manifestBody, "schema").equals("TRAINING_COMBAT131_V1") && jsonScalar(manifestBody, "phase").equals("N01") && jsonScalar(manifestBody, "campaignId").equals("n01")
			&& jsonScalar(manifestBody, "executionSha").equalsIgnoreCase(scope.executionSha()) && jsonScalar(manifestBody, "sourceCommit").equals(scope.sourceCommit())
			&& jsonScalar(manifestBody, "fixtureSha").equalsIgnoreCase(scope.fixtureSha()) && jsonScalar(manifestBody, "gameJarSha").equalsIgnoreCase(scope.gameJarSha())
			&& jsonScalar(manifestBody, "jdbcSha").equalsIgnoreCase(scope.jdbcSha()) && jsonScalar(manifestBody, "sqlLane").equals(scope.sql().getParent().getFileName().toString()), "CAMPAIGN_BYTES_SCOPE");
		final var deadline = Instant.parse(value(fields, "deadlineUtc"));
		final var now = Instant.now();
		check(now.isBefore(deadline) && deadline.isBefore(now.plusSeconds(1801)), "BOOT_DEADLINE");
		pin(physical(scope.sql().resolve("attestation.json"), true), value(fields, "sqlAttestationSha"));
		final var process = ProcessHandle.of(Long.parseLong(value(fields, "sqlPid"))).filter(ProcessHandle::isAlive).orElseThrow(() -> failure("SQL_PID_ABSENT"));
		final var info = process.info();
		final var started = Instant.parse(value(fields, "sqlStartedUtc"));
		check(info.startInstant().filter(observed -> Math.abs(java.time.Duration.between(observed, started).toMillis()) < 2).isPresent()
			&& info.command().orElse("").toLowerCase(Locale.ROOT).endsWith("\\mariadbd.exe")
			&& Arrays.equals(info.arguments().orElse(new String[0]), new String[] {"--defaults-file=" + physical(scope.sql().resolve("owned-server.ini"), true), "--console"}), "OWN_SQL_PROCESS");
		final var launch = new Launch(root, manifest.toLowerCase(Locale.ROOT), boot, nonce, deadline);
		verifyLauncherClaim(launch, path);
		return launch;
	}

	private static void verifyLauncherClaim(Launch launch, Path launchFile) throws Exception
	{
		final Path reservation = physical(launch.root().resolve("reserved.json"), true);
		final String declared = smallText(reservation);
		check(jsonScalar(declared, "schema").equals("TRAINING_RESERVED131_V1") && jsonScalar(declared, "nonce").equals(launch.nonce())
			&& jsonScalar(declared, "bootId").equals(launch.boot()) && jsonScalar(declared, "manifestSha").equalsIgnoreCase(launch.manifestSha())
			&& jsonScalar(declared, "launchSha").equalsIgnoreCase(sha(launchFile)), "LAUNCH_RESERVATION");
		final var claim = launch.root().resolve("process.json");
		final Instant awakeUntil = earliest(Instant.now().plusSeconds(8), launch.deadline());
		while (!Files.exists(claim, LinkOption.NOFOLLOW_LINKS) && Instant.now().isBefore(awakeUntil) && request(launch) == null) { Thread.sleep(20); }
		final var process = smallText(physical(claim, true));
		check(jsonScalar(process, "schema").equals("TRAINING_PROCESS131_V1") && jsonScalar(process, "nonce").equals(launch.nonce())
			&& jsonScalar(process, "pid").equals(Long.toString(ProcessHandle.current().pid())) && jsonScalar(process, "reservationSha").equalsIgnoreCase(sha(reservation)), "LAUNCHER_PROCESS_ATTESTATION");
	}

	private static List<Scene> scenes(Path fixture, Path instances) throws IOException
	{
		final var fixtureRows = table(fixture, FIXTURE_HEADER, 22);
		final var sceneRows = table(instances, SCENE_HEADER, 22);
		check(fixtureRows.size() == 4 && sceneRows.size() == 4, "INCOMPLETE_SCENE_SET");
		final List<Scene> cohort = new ArrayList<>(4);
		final Set<String> roles = new HashSet<>(), accounts = new HashSet<>();
		final Set<Long> profiles = new HashSet<>();
		final Set<Integer> actors = new HashSet<>();
		for (int index = 0; index < 4; index++)
		{
			final var actor = fixtureRows.get(index);
			final var scene = sceneRows.get(index);
			final long profile = Long.parseLong(actor[1]);
			final int object = Integer.parseInt(actor[2]), level = Integer.parseInt(actor[6]);
			check("SETUP_STOCK_CANDIDATE".equals(actor[0]) && profile > 0 && object > 0 && profiles.add(profile) && actors.add(object)
				&& actor[3].equals("p" + Long.toString(profile, 36)) && accounts.add(actor[3]) && Set.of("MAGE", "PHYSICAL").contains(actor[4])
				&& Set.of(52, 76).contains(level) && roles.add(actor[4] + level), "EXACT_OWN_ROSTER");
			check(actor[1].equals(scene[0]) && actor[2].equals(scene[1]) && actor[13].equals(scene[5]) && actor[14].equals(scene[9]) && actor[15].equals(scene[11])
				&& actor[16].equals(scene[14]) && actor[17].equals(scene[6]) && actor[18].equals(scene[7]) && actor[19].equals(scene[8]) && scene[15].equals("0")
				&& Long.parseLong(scene[16]) > 0 && Long.parseLong(scene[2]) > 0 && Long.parseLong(scene[3]) >= 0 && Integer.parseInt(scene[17]) > 0
				&& scene[4].matches("(?i)[a-f0-9]{64}") && scene[5].matches("[a-zA-Z0-9_.-]{1,160}") && scene[11].matches("(?:territory|fixed):[^\\r\\n\\t]{1,220}")
				&& !scene[11].contains("NO_GEOMETRY_HASH"), "SCENE_FIXTURE_BINDING");
			final int respawnMin = Integer.parseInt(scene[12]), respawnMax = Integer.parseInt(scene[13]), npcLevel = Integer.parseInt(scene[10]);
			check(respawnMin > 0 && respawnMax >= respawnMin && Math.abs(npcLevel - level) <= 4 && Long.parseLong(actor[11]) > 0 && Long.parseLong(actor[11]) <= 2000
				&& Long.parseLong(actor[12]) >= 0 && Long.parseLong(actor[12]) <= 50000, "FINITE_STOCK_SETUP");
			final var point = new PhantomTopologyPoint(Integer.parseInt(scene[6]), Integer.parseInt(scene[7]), Integer.parseInt(scene[8]), 0);
			check(cohort.stream().noneMatch(other -> other.point().distance3D(point) <= 2 * MAX_DISTANCE), "DISJOINT_SCENES");
			cohort.add(new Scene(profile, object, actor[3], actor[4].equals("MAGE"), Integer.parseInt(actor[5]), level, Integer.parseInt(actor[7]), Integer.parseInt(actor[8]),
				Integer.parseInt(actor[9]), Integer.parseInt(actor[10]), Long.parseLong(actor[11]), Long.parseLong(actor[12]), Long.parseLong(actor[20]), Long.parseLong(actor[21]),
				Long.parseLong(scene[2]), Long.parseLong(scene[3]), scene[4], scene[5], point, Integer.parseInt(scene[9]), npcLevel, scene[11], respawnMin, respawnMax,
				new PhantomBackgroundState.Hashes(scene[18], scene[19], scene[20], scene[21])));
		}
		return List.copyOf(cohort);
	}

	private static void run(Scope scope, Launch launch) throws Exception
	{
		final var config = physical(scope.game().resolve("config/Custom/PhantomPlayers.ini"), true);
		final String disabled = sha(config);
		final var enabled = physical(scope.game().resolve("config/Custom/PhantomPlayers.n01.ini"), true);
		final var settings = PhantomPlayersConfig.read(enabled);
		check(sha(enabled).equals(scope.enabledConfigSha()) && settings.enabled() && request(launch) == null, "BOOT_CANCELLED_OR_CHANGED_CONFIG");
		verifyBeforeBoot(scope);
		writeNew(launch.root().resolve("n01-java.claim"), line("pid", ProcessHandle.current().pid()) + line("nonce", launch.nonce())
			+ line("executionSha", scope.executionSha()) + line("jarSha", scope.gameJarSha()) + line("startedUtc", currentStart()));
		boolean bootAttempted = false, enabledRuntime = false;
		Status terminal = Status.UNKNOWN;
		String reason = "NATIVE_INCOMPLETE";
		int completed = 0;
		final Binding binding = new Binding(scope, launch);
		try
		{
			bootAttempted = true;
			GameServer.main(new String[0]);
			check(!PhantomPlayersConfig.isEnabled() && !PhantomSystem.hasConfiguredInstance() && Instant.now().isBefore(launch.deadline()), "BOOT_OWNER_OR_DEADLINE");
			try (Connection database = DatabaseFactory.getConnection()) { verifyDatabase(scope, database); }
			verifySetup(scope, enabled);
			writeNew(launch.root().resolve("n01-binding.tsv"), BIND_HEADER + "\n");
			writeNew(launch.root().resolve("n01-native-cycles.tsv"), CYCLE_HEADER + "\n");
			try (var drops = new DropAudit(scope.scenes()); var resource = PlayerNativeEvidence.observeResourceWrites(binding::resource))
			{
				terminal = request(launch);
				if (terminal == null)
				{
					try (Connection database = DatabaseFactory.getConnection()) { verifyDatabase(scope, database); }
					check(!PhantomPlayersConfig.isEnabled() && "OFF".equals(PhantomPlayersConfig.learningSettings().mode()) && sha(config).equals(disabled), "DISK_MASTER_STAYS_OFF");
					enabledRuntime = PhantomLearning131Factory.start(settings, binding, inspection -> verifyConfigured(scope, inspection), binding::permitsTarget);
					check(enabledRuntime && PhantomSystem.operatorStatus().runtimeState() == PhantomSystem.State.RUNNING, "PRODUCTION_SCHEDULER_START");
					final var outcome = observe(scope, launch, binding, drops, resource);
					terminal = outcome.status();
					completed = outcome.completed();
				}
				reason = terminal.name();
			}
		}
		catch (Throwable failure)
		{
			terminal = Status.UNKNOWN;
			reason = failure.getMessage() != null && failure.getMessage().matches("TASK131_[A-Z0-9_.-]+") ? failure.getMessage() : failure.getClass().getSimpleName();
			System.err.println("TASK131_N01_ABORT " + reason);
			for (StackTraceElement frame : failure.getStackTrace())
			{
				if (frame.getClassName().startsWith("org.l2jmobius.")) { System.err.println(frame); }
			}
		}
		finally
		{
			binding.closeAdmissions();
			try
			{
				final var stored = drain(scope, launch, binding, enabledRuntime);
				check(!PhantomPlayersConfig.isEnabled() && "OFF".equals(PhantomPlayersConfig.learningSettings().mode()) && sha(config).equals(disabled), "DISK_MASTER_STAYS_OFF");
				final var status = terminal == null ? Status.UNKNOWN : terminal;
				final var seal = launch.root().resolve("n01-drain.properties");
				final String bindingSha = optionalSha(launch.root().resolve("n01-binding.tsv")), cyclesSha = optionalSha(launch.root().resolve("n01-native-cycles.tsv"));
				final String costsSha = optionalSha(launch.root().resolve("n01-native-costs.tsv")), lootSha = optionalSha(launch.root().resolve("n01-native-loot.tsv"));
				check(completed == 0 || java.util.stream.Stream.of(bindingSha, cyclesSha, costsSha, lootSha).noneMatch("NONE"::equals), "COMPLETED_RECEIPTS_ABSENT");
				writeNew(seal, line("schema", "N01_DRAIN131_V1") + line("status", status) + line("reason", reason) + line("worldOwnedPlayers", 0)
					+ line("durableStoredActors", stored) + line("minRequiredCycles", CYCLES_REQUIRED) + line("completed", completed)
					+ line("manifestSha", launch.manifestSha()) + line("bootId", launch.boot()) + line("jarSha", scope.gameJarSha()) + line("bindingSha", bindingSha)
					+ line("cyclesSha", cyclesSha) + line("costsSha", costsSha) + line("lootSha", lootSha));
				writeNew(launch.root().resolve("drain131.properties"), line("schema", "NATIVE_DRAIN131_V1") + line("nonce", launch.nonce())
					+ line("manifestSha", launch.manifestSha()) + line("bootId", launch.boot()) + line("pid", ProcessHandle.current().pid())
					+ line("startedUtc", currentStart()) + line("status", status) + line("completed", completed) + line("sealSha", sha(seal)));
			}
			finally { if (bootAttempted) { Shutdown.getInstance().startShutdown(null, 0, false); } }
		}
	}

	private static void verifyConfigured(Scope scope, PhantomSystem.PlanningInspection inspection)
	{
		final var hashes = inspection.planner().generation().authorityHashes();
		check(inspection.settings().maxMaterializedPhantoms() == scope.scenes().size() && scope.scenes().stream().allMatch(scene ->
				scene.hashes().equals(hashes) && inspection.topology().findAnchor(scene.anchor()).isPresent()), "CONFIGURED_PLANNER_AUTHORITY_DRIFT");
	}

	private static void verifyBeforeBoot(Scope scope) throws Exception
	{
		final Path bootIni = physical(scope.game().resolve("config/Database.ini"), true);
		final var jdbc = properties(bootIni);
		check(pin(bootIni, scope.jdbcSha()).equals(scope.jdbcSha()) && JDBC.equals(value(jdbc, "URL")) && "task131_runtime".equals(value(jdbc, "Login")), "PRE_BOOT_JDBC_PIN");
		try (Connection database = DriverManager.getConnection(JDBC, value(jdbc, "Login"), value(jdbc, "Password")))
		{
			database.setReadOnly(true);
			verifyDatabase(scope, database);
		}
	}

	private static void verifyDatabase(Scope scope, Connection connection) throws Exception
	{
		try (var query = connection.createStatement();
			var rows = query.executeQuery("SELECT DATABASE(),CURRENT_USER(),@@port,@@datadir,@@bind_address,(SELECT COUNT(*) FROM accounts),(SELECT COUNT(*) FROM characters)"))
		{
			check(rows.next() && SCHEMA.equals(rows.getString(1)) && "task131_runtime@127.0.0.1".equals(rows.getString(2)) && rows.getInt(3) == 33331
				&& physical(Path.of(rows.getString(4)), false).equals(physical(scope.sql().resolve("data"), false)) && "127.0.0.1".equals(rows.getString(5))
				&& rows.getInt(6) == 4 && rows.getInt(7) == 4, "OWN_JDBC_AND_EXACT_COHORT");
		}
		try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT charId,account_name,online FROM characters"))
		{
			final var expected = new HashMap<Integer, String>();
			for (Scene scene : scope.scenes()) { expected.put(scene.actor(), scene.account()); }
			while (rows.next()) { check(rows.getInt(3) == 0 && Objects.equals(expected.remove(rows.getInt(1)), rows.getString(2)), "EXACT_OFFLINE_JDBC_ROSTER"); }
			check(expected.isEmpty(), "OWN_JDBC_ROSTER_MISSING");
		}
	}

	private static void verifySetup(Scope scope, Path enabled) throws Exception
	{
		final var settings = PhantomPlayersConfig.read(enabled);
		final var profiles = PhantomProfileRepository.open();
		final var catalog = PhantomPopulationCatalog.load(scope.game().resolve("data/phantoms/population/high-five-population-v3.xml"), settings.populationTimeZone());
		final var entries = new PhantomPopulationStore(profiles, catalog, settings.populationTimeZone()).loadManagedAfter(0, 5);
		check(entries.size() == 4 && entries.stream().map(value -> value.profile().profileId()).collect(java.util.stream.Collectors.toSet())
			.equals(Set.copyOf(scope.scenes().stream().map(Scene::profile).toList())), "MANAGED_ROSTER_SCOPE");
		try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start(scope.game().resolve("data/phantoms/topology"), scope.game()))
		{
			final var goals = new PhantomGoalStateStore(profiles);
			final Instant now = Instant.now();
			for (var scene : scope.scenes())
			{
				final var entry = entries.stream().filter(row -> row.profile().profileId() == scene.profile()).findFirst().orElseThrow();
				check(entry.profile().characterObjectId() == scene.actor() && entry.state().reservedAccount().equals(scene.account()) && entry.state().state() == PhantomPopulationState.State.READY
					&& World.getInstance().findObject(scene.actor()) == null && authority.authority().hashes().equals(scene.hashes()), "READY_AUTHORITY_SCOPE");
			final var window = catalog.evaluate(entry.state().scheduleTemplate(), now, settings.populationTimeZone(), entry.state().schedulePhaseMinutes());
				check(window.state().requiresMaterialization() && window.nextBoundary().isAfter(now.plusSeconds(scope.seconds() + scope.drainSeconds())), "READY_CALENDAR_RANGE");
				final var goal = goals.load(scene.profile()).orElseThrow().goal();
				check(goal.goalId() == scene.goal() && goal.revision() == scene.revision() && goal.validSources().size() == 1
					&& goal.validSources().getFirst().key().equals(scene.npc() + "@" + scene.anchor()), "PERSISTED_SCENE_GOAL");
				final var setup = background(profiles, scene.profile());
				check(setup.hashes().equals(scene.hashes()) && setup.identity().characterObjectId() == scene.actor() && setup.identity().activeClassId() == scene.profession()
					&& setup.state() == PhantomBackgroundState.State.READY && setup.position().committedAnchorId().equals(scene.anchor())
					&& durable(scene, true).equals(new Totals(scene.initialExp(), scene.initialSp(), scene.stockShots(), scene.stockAdena())), "PERSISTED_SCENE_BASELINE");
			}
		}
	}

	private static PhantomBackgroundState background(PhantomProfileRepository profiles, long profile)
	{
		return new PhantomBackgroundStateCodec().decode(profiles.findComponent(profile, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
	}

	private static Totals durable(Scene scene, boolean initial) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement(
			"SELECT c.account_name,c.online,c.classid,c.level,c.exp,c.sp,c.x,c.y,c.z,s.skill_level," +
			"(SELECT COALESCE(SUM(i.count),0) FROM items i WHERE i.owner_id=c.charId AND i.item_id=?)," +
			"(SELECT COALESCE(SUM(i.count),0) FROM items i WHERE i.owner_id=c.charId AND i.item_id=57) " +
			"FROM characters c JOIN character_skills s ON s.charId=c.charId AND s.class_index=0 AND s.skill_id=? WHERE c.charId=?"))
		{
			query.setInt(1, scene.shot());
			query.setInt(2, scene.skill());
			query.setInt(3, scene.actor());
			try (var rows = query.executeQuery())
			{
				check(rows.next() && rows.getString(1).equals(scene.account()) && rows.getInt(2) == 0 && rows.getInt(3) == scene.profession()
					&& rows.getInt(4) >= scene.level() && rows.getInt(10) >= scene.rank(), "DURABLE_ACTOR_STORE");
				if (initial)
				{
					check(rows.getInt(4) == scene.level() && rows.getInt(10) == scene.rank() && rows.getInt(7) == scene.point().x() && rows.getInt(8) == scene.point().y()
						&& rows.getInt(9) == scene.point().z(), "DURABLE_OPENING_POINT");
				}
				return new Totals(rows.getLong(5), rows.getLong(6), rows.getLong(11), rows.getLong(12));
			}
		}
	}

	private static int drain(Scope scope, Launch launch, Binding binding, boolean started) throws Exception
	{
		final FutureTask<Integer> task = new FutureTask<>(() ->
		{
			if (started || PhantomSystem.hasConfiguredInstance())
			{
				check(PhantomSystem.shutdownIfStarted(), "PRODUCTION_DRAIN_FAILED");
				check(!PhantomSystem.operatorDisable().runtimeConfigured() && !PhantomSystem.hasConfiguredInstance(), "TYPED_OPERATOR_OFF");
			}
			check(DatabaseFactory.isInitialized(), "DURABLE_DATABASE_NOT_OPEN");
			final var profiles = PhantomProfileRepository.open();
			for (var scene : scope.scenes())
			{
				check(World.getInstance().getPlayer(scene.actor()) == null && World.getInstance().findObject(scene.actor()) == null
					&& PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(scene.actor()) == null, "OWN_WORLD_OR_IDENTITY_RETAINED");
				final var state = background(profiles, scene.profile());
				final var totals = durable(scene, false);
				check(!binding.bound.containsKey(scene.profile()) || binding.stored.contains(scene.profile()), "NATIVE_STORE_CALLBACK_MISSING");
				final var award = binding.lastOwnedAward.get(scene.profile());
				if (award != null)
				{
					check(totals.exp() >= Math.addExact(scene.initialExp(), award.expGained()) && totals.sp() >= Math.addExact(scene.initialSp(), award.spGained())
						&& totals.shots() <= scene.stockShots(), "DURABLE_NATIVE_REWARD_AND_COST");
				}
				check(state.identity().characterObjectId() == scene.actor() && state.state() != PhantomBackgroundState.State.MATERIALIZED, "DURABLE_BACKGROUND_CLOSURE");
			}
			return scope.scenes().size();
		});
		Thread.startVirtualThread(task);
		final long remaining = Math.max(1, java.time.Duration.between(Instant.now(), launch.deadline()).toMillis());
		return task.get(Math.min(TimeUnit.SECONDS.toMillis(scope.drainSeconds()), remaining), TimeUnit.MILLISECONDS);
	}

	private static final class Binding implements PhantomMaterializationLifecyclePort
	{
		private final Launch launch;
		private final Map<Long, Scene> scenes = new HashMap<>();
		private final Map<Integer, Scene> actors = new HashMap<>();
		private final Map<Long, Bound> bound = new ConcurrentHashMap<>();
		private final Map<Long, PlayerNativeEvidence.Snapshot> lastOwnedAward = new ConcurrentHashMap<>();
		private final Set<Long> stored = ConcurrentHashMap.newKeySet();
		private final AtomicReference<String> refusal = new AtomicReference<>();
		private final ConcurrentLinkedQueue<ResourceWrite> resources = new ConcurrentLinkedQueue<>();
		private final AtomicInteger resourceRows = new AtomicInteger();
		private volatile boolean closed;

		private Binding(Scope scope, Launch launch)
		{
			this.launch = launch;
			for (Scene scene : scope.scenes()) { scenes.put(scene.profile(), scene); actors.put(scene.actor(), scene); }
		}

		@Override public void beforeMaterialize(long profile, int actor)
		{
			if (closed) { throw new AdmissionRejectedException("TASK131_N01_ADMISSION_CLOSED"); }
			final Scene scene = scenes.get(profile);
			if (scene == null || scene.actor() != actor || bound.containsKey(profile)) { reject("UNEXPECTED_OR_REPEAT_PROFILE"); }
		}

		@Override public void afterPlayerLoad(long profile, Player player) { }

		@Override public synchronized void beforeWorldSpawn(long profile, Player player)
		{
			beforeMaterialize(profile, player.getObjectId());
			try
			{
				if (!Instant.now().isBefore(launch.deadline()) || request(launch) != null) { throw new AdmissionRejectedException("TASK131_N01_ADMISSION_CLOSED"); }
				final Scene scene = scenes.get(profile);
				final var owner = player.getNativeWorkOwner();
				check(owner != null && owner.player() == player && owner.isCurrent() && owner.epoch() > 0 && owner.evidence() != null
					&& owner.evidence().matches(scene.actor(), owner.epoch()) && World.getInstance().findObject(scene.actor()) == null, "PRE_ACTION_NATIVE_OWNER");
				final var skill = player.getKnownSkill(scene.skill());
				check(player.getAccountName().equals(scene.account()) && player.getActiveClass() == scene.profession() && player.getLevel() == scene.level()
					&& skill != null && skill.getLevel() == scene.rank() && skill.isMagic() == scene.mage() && player.getActiveWeaponInstance() != null
					&& player.getActiveWeaponInstance().getId() == scene.weapon() && point(player).equals(scene.point()) && !player.isDead(), "PRE_ACTION_ACTOR_SCOPE");
				final var stock = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
					.filter(mob -> matchesStock(scene, mob) && !mob.isAlikeDead() && opening(scene, mob))
					.sorted(Comparator.comparingLong((Monster mob) -> scene.point().distanceSquared2D(point(mob))).thenComparingInt(Monster::getId)
						.thenComparing(PhantomLearning131Native::spawnKey).thenComparingInt(Monster::getObjectId)).findFirst()
					.orElseThrow(() -> failure("PRE_ACTION_STOCK_TARGET"));
				final var target = stock.getNativeEvidenceTarget();
				final var initial = owner.evidence().snapshot();
				check(!initial.overflow() && initial.damageSequence() == 0 && initial.targetSequence() == 0 && initial.killSequence() == 0 && initial.rewardSequence() == 0, "NO_WORK_BEFORE_BIND");
				append(launch.root().resolve("n01-binding.tsv"), row(launch.manifestSha(), launch.boot(), profile, scene.actor(), owner.epoch(), target.instanceId(), scene.npc(),
					scene.spawn(), target.objectId(), target.spawnGeneration(), scene.anchor(), scene.goal(), scene.revision(), scene.planIdentity(), Instant.now()));
				bound.put(profile, new Bound(scene, player, owner.evidence(), initial, target));
			}
			catch (AdmissionRejectedException closed) { throw closed; }
			catch (Exception problem) { reject(problem.getMessage() == null ? problem.getClass().getSimpleName() : problem.getMessage()); }
		}

		@Override public void materializeSucceeded(long profile, int actor) { }
		@Override public void materializeAborted(long profile, int actor) { if (!closed && bound.containsKey(profile)) { refusal.compareAndSet(null, "BOUND_MATERIALIZATION_ABORTED"); } }
		@Override public void beforeStore(long profile, Player player) { }
		@Override public void afterStore(long profile, Player player)
		{
			final var expected = bound.get(profile);
			if (expected != null && expected.player() == player) { stored.add(profile); }
		}

		private void reject(String reason)
		{
			refusal.compareAndSet(null, reason);
			throw new AdmissionRejectedException("TASK131_N01_BINDING_REJECTED");
		}

		private void closeAdmissions() { closed = true; }
		private boolean permitsTarget(Player player, Creature target)
		{
			if (closed || !(target instanceof Monster monster)) { return false; }
			final Scene scene = actors.get(player.getObjectId());
			final var actor = scene == null ? null : bound.get(scene.profile());
			if (actor == null || actor.player() != player || PlayerNativeWork.observationEvidence(player) != actor.sensor() || !matchesStock(scene, monster)
				|| scene.point().distanceSquared2D(point(player)) > (long) MAX_DISTANCE * MAX_DISTANCE
				|| scene.point().distanceSquared2D(point(monster)) > (long) MAX_DISTANCE * MAX_DISTANCE) { return false; }
			final Target selected = actor.sensor().selectedTarget();
			return selected != null || actor.stock().equals(monster.getNativeEvidenceTarget());
		}

		private void resource(ResourceWrite event)
		{
			final var scene = actors.get(event.actorObjectId());
			if (scene == null) { return; }
			final var owner = bound.get(scene.profile());
			if (owner == null || owner.initial().epoch() != event.actorEpoch()) { refusal.compareAndSet(null, "RESOURCE_BEFORE_BIND_OR_STALE"); return; }
			if (resourceRows.incrementAndGet() > MAX_RESOURCE_ROWS) { refusal.compareAndSet(null, "RESOURCE_RECEIPT_CAP"); return; }
			resources.add(event);
		}
	}

	private static boolean matchesStock(Scene scene, Monster mob)
	{
		final var spawn = mob.getSpawn();
		final var clan = mob.getTemplate().getClans();
		return mob.getId() == scene.npc() && mob.getLevel() == scene.npcLevel() && mob.getInstanceId() == scene.point().instanceId() && mob.isSpawned()
			&& World.getInstance().findObject(mob.getObjectId()) == mob && mob.isTargetable() && !mob.isInvul() && !mob.isChampion() && !mob.isFlying() && !mob.isAggressive()
			&& !mob.isRaid() && !mob.isQuestMonster() && !mob.getTemplate().isUndying() && (clan == null || clan.isEmpty()) && !mob.hasMinions() && mob.getLeader() == null
			&& spawn != null && spawn.isRespawnEnabled() && spawn.getRespawnMinDelay() == scene.respawnMin() && spawn.getRespawnMaxDelay() == scene.respawnMax()
			&& spawnKey(mob).equals(scene.spawn()) && mob.getNativeEvidenceTarget().spawnGeneration() > 0;
	}

	private static boolean opening(Scene scene, Monster mob)
	{
		final var point = scene.point();
		final var geo = GeoEngine.getInstance();
		return point.distanceSquared2D(point(mob)) <= (long) MAX_DISTANCE * MAX_DISTANCE && Math.abs((long) point.z() - mob.getZ()) <= 150
			&& geo.hasGeo(point.x(), point.y()) && geo.hasGeo(mob.getX(), mob.getY())
			&& geo.canSeeTarget(point.x(), point.y(), point.z(), mob.getX(), mob.getY(), mob.getZ(), point.instanceId())
			&& geo.canMoveToTarget(point.x(), point.y(), point.z(), mob.getX(), mob.getY(), mob.getZ(), point.instanceId())
			&& geo.canMoveToTarget(mob.getX(), mob.getY(), mob.getZ(), point.x(), point.y(), point.z(), point.instanceId())
			&& PhantomCombatPolicy.farmRewardAvailable(scene.level() - mob.getLevel(), RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE, mob.getExpReward(scene.level()), mob.getSpReward(scene.level()))
			&& World.getInstance().getVisibleObjectsInRange(mob, Monster.class, 2500).stream().noneMatch(other -> other.isSpawned() && !other.isDead() && other.isAggressive()
				&& other.getInstanceId() == point.instanceId() && (Math.abs((long) other.getZ() - mob.getZ()) <= 256 && other.calculateDistance2D(mob) <= other.getAggroRange() + NpcConfig.MAX_DRIFT_RANGE
					|| Math.abs((long) other.getZ() - point.z()) <= 256 && point.distanceSquared2D(point(other)) <= Math.pow(other.getAggroRange() + NpcConfig.MAX_DRIFT_RANGE, 2)));
	}

	private static String spawnKey(Monster mob)
	{
		final var spawn = mob.getSpawn();
		final String name = Objects.toString(spawn.getName(), "");
		final var territory = spawn.getSpawnTerritory();
		if (territory != null)
		{
			return "territory:" + territory.getName() + ":" + territory.geometrySnapshot().map(value -> value.hash()).orElse("NO_GEOMETRY_HASH") + ":" + name;
		}
		final var location = spawn.getSpawnLocation();
		return "fixed:" + name + ":" + location.getX() + ":" + location.getY() + ":" + location.getZ();
	}

	private static PhantomTopologyPoint point(org.l2jmobius.gameserver.model.WorldObject object)
	{
		return new PhantomTopologyPoint(object.getX(), object.getY(), object.getZ(), object.getInstanceId());
	}

	private static final class DropAudit extends Handler implements AutoCloseable
	{
		private static final Pattern CREATION = Pattern.compile("^CREATE:LOOT, item (\\d+):.*, [^,]+, .+\\[(\\d+)\\]$");
		private final Logger logger = Logger.getLogger("item");
		private final Set<Integer> npcs;
		private final Map<Target, List<Loot>> drops = new HashMap<>();
		private final Set<Integer> objects = new HashSet<>();
		private boolean unsafe;

		private DropAudit(List<Scene> scenes)
		{
			check(GeneralConfig.LOG_ITEMS && !GeneralConfig.LOG_ITEMS_SMALL_LOG && !GeneralConfig.LOG_ITEMS_IDS_ONLY && logger.isLoggable(Level.INFO)
				&& !PlayerConfig.AUTO_LOOT && !PlayerConfig.AUTO_LOOT_HERBS && PlayerConfig.AUTO_LOOT_ITEM_IDS.stream().noneMatch(id -> id > 0), "STOCK_DROP_LOG_CONFIG");
			npcs = Set.copyOf(scenes.stream().map(Scene::npc).toList());
			logger.addHandler(this);
		}

		@Override public synchronized void publish(LogRecord row)
		{
			final Target target = PlayerNativeEvidence.currentRewardTarget();
			if (unsafe || target == null || !row.getMessage().startsWith("CREATE:LOOT, item ")) { return; }
			final var world = World.getInstance();
			if (!(world.findObject(target.objectId()) instanceof Monster mob) || !npcs.contains(mob.getId())) { return; }
			final var match = CREATION.matcher(row.getMessage());
			if (!match.matches() || Integer.parseInt(match.group(2)) != target.objectId() || !target.equals(mob.getNativeEvidenceTarget()) || !row.getMessage().endsWith(", " + mob)
				|| !(world.findObject(Integer.parseInt(match.group(1))) instanceof Item item) || item.getCount() <= 0 || objects.size() >= 1024 || !objects.add(item.getObjectId()))
			{
				unsafe = true;
				return;
			}
			drops.computeIfAbsent(target, _ -> new ArrayList<>()).add(new Loot(item.getObjectId(), item.getId(), item.getCount()));
		}

		private synchronized List<Loot> drops(Target target)
		{
			check(!unsafe, "INEXACT_STOCK_DROP_AUDIT");
			return List.copyOf(drops.getOrDefault(target, List.of()));
		}
		@Override public void flush() { }
		@Override public void close() { logger.removeHandler(this); }
	}

	private record Outcome(Status status, int completed) { }
	private record Frame(PlayerNativeEvidence.Snapshot snapshot, PlayerNativeEvidence.TargetReceipts receipts, Target selected) { }
	private static final class Progress
	{
		private final Bound bound;
		private final Set<Target> selected = new HashSet<>();
		private final Map<Target, Double> mp = new HashMap<>();
		private final Map<Target, Long> shotBySelectedTarget = new HashMap<>();
		private long shoots, cycles, rewardSequence, exp, sp, targetSequence, picked;
		private Progress(Bound bound) { this.bound = bound; }
		private boolean done() { return cycles >= CYCLES_REQUIRED && shoots > 0 && picked > 0; }
	}

	private static Outcome observe(Scope scope, Launch launch, Binding binding, DropAudit drops, PlayerNativeEvidence.ResourceObservation resource) throws Exception
	{
		final var deadline = earliest(Instant.now().plusSeconds(scope.seconds()), launch.deadline().minusSeconds(scope.drainSeconds()));
		final var progress = new HashMap<Long, Progress>();
		final Path rows = launch.root().resolve("n01-native-cycles.tsv"), costs = launch.root().resolve("n01-native-costs.tsv"), loot = launch.root().resolve("n01-native-loot.tsv");
		writeNew(costs, "kind\tprofileId\tactorObjectId\tepoch\titemObjectId\titemId\tskillId\tskillLevel\ttargetObjectId\ttargetInstanceId\ttargetGeneration\tbefore\tafter\n");
		writeNew(loot, "profileId\tactorObjectId\tepoch\tcycle\ttargetObjectId\ttargetGeneration\tdropObjectId\titemId\tcreatedQuantity\treceivedQuantity\tpickupSequence\tnativeConsumed\n");
		while (Instant.now().isBefore(deadline))
		{
			final Status requested = request(launch);
			if (requested != null) { return new Outcome(requested, 0); } // No partial N01 prefix is reusable across a changed actor epoch.
			check(resource.failure() == null && binding.refusal.get() == null, "BIND_OR_RESOURCE_OBSERVATION_FAILED");
			check(World.getInstance().getPlayers().stream().allMatch(player -> binding.actors.containsKey(player.getObjectId())), "UNEXPECTED_WORLD_PLAYER");
			for (var scene : scope.scenes())
			{
				final var entry = binding.bound.get(scene.profile());
				if (entry != null) { progress.computeIfAbsent(scene.profile(), _ -> new Progress(entry)); }
			}
			collectCosts(costs, binding, progress);
			for (var scene : scope.scenes())
			{
				final var track = progress.get(scene.profile());
				if (track == null) { continue; }
				observeCycle(track, binding, drops, rows, loot, scope.maxCycles());
			}
			if (progress.size() == scope.scenes().size() && progress.values().stream().allMatch(Progress::done)) { return new Outcome(Status.COMPLETED, 4); }
			Thread.sleep(20);
		}
		return new Outcome(Status.UNKNOWN, 0);
	}

	private static void collectCosts(Path costs, Binding binding, Map<Long, Progress> progress) throws IOException
	{
		final var pending = new StringBuilder();
		for (ResourceWrite value; (value = binding.resources.poll()) != null;)
		{
			final var scene = binding.actors.get(value.actorObjectId());
			final var track = progress.computeIfAbsent(scene.profile(), id -> new Progress(binding.bound.get(id)));
			check(track.bound != null && track.bound.initial().epoch() == value.actorEpoch(), "EXACT_RESOURCE_EPOCH");
			switch (value)
			{
				case ItemDebit debit ->
				{
					final Target selected = debit.selectedTarget();
					if (debit.itemId() == scene.shot())
					{
						final long delta = Math.subtractExact(debit.before(), debit.after());
						track.shoots = Math.addExact(track.shoots, delta);
						if (selected != null) { track.shotBySelectedTarget.merge(selected, delta, Math::addExact); }
						check(track.shoots <= scene.stockShots(), "SETUP_SHOT_COST_EXCEEDED");
					}
					pending.append(row("ITEM_DEBIT", scene.profile(), scene.actor(), value.actorEpoch(), debit.itemObjectId(), debit.itemId(), "", "",
						selected == null ? "" : selected.objectId(), selected == null ? "" : selected.instanceId(), selected == null ? "" : selected.spawnGeneration(), debit.before(), debit.after()));
				}
				case CastMpDebit debit ->
				{
					final var skill = track.bound.player().getKnownSkill(debit.skillId());
					check(skill != null && skill.isDamage() && skill.getLevel() >= debit.skillLevel(), "NATIVE_COST_LEARNED_SKILL");
					if (!track.selected.contains(debit.target())) { stock(scene, debit.target()); }
					track.mp.merge(debit.target(), debit.before() - debit.after(), Double::sum);
					pending.append(row("CAST_HIT_MP", scene.profile(), scene.actor(), value.actorEpoch(), "", "", debit.skillId(), debit.skillLevel(), debit.target().objectId(),
						debit.target().instanceId(), debit.target().spawnGeneration(), debit.before(), debit.after()));
				}
			}
		}
		if (!pending.isEmpty()) { append(costs, pending.toString()); }
	}

	private static void observeCycle(Progress track, Binding binding, DropAudit drops, Path rows, Path lootRows, int maximum) throws IOException
	{
		final Bound bound = track.bound;
		final var actor = bound.player();
		final var scene = bound.scene();
		if (World.getInstance().getPlayer(scene.actor()) != actor) { return; } // Binding happens before spawn; timeout supplies the failure if spawn never closes.
		check(!actor.isAlikeDead() && !actor.isInParty() && actor.getInstanceId() == scene.point().instanceId()
			&& scene.point().distanceSquared2D(point(actor)) <= (long) MAX_DISTANCE * MAX_DISTANCE, "LIVE_ACTOR_SCENE_BOUNDARY");
		check(PlayerNativeWork.observationEvidence(actor) == bound.sensor(), "NATIVE_OWNER_CHANGED");
		final Frame frame;
		synchronized (bound.sensor()) { frame = new Frame(bound.sensor().snapshot(), bound.sensor().targetReceipts(), bound.sensor().selectedTarget()); }
		final var snap = frame.snapshot();
		check(!snap.overflow() && snap.objectId() == scene.actor() && snap.epoch() == bound.initial().epoch() && snap.farmCycleSequence() <= maximum, "NATIVE_CYCLE_EPOCH_OR_QUOTA");
		if (snap.targetSequence() != track.targetSequence)
		{
			check(snap.targetSequence() == track.targetSequence + 1 && frame.selected() != null, "MISSED_NATIVE_TARGET_SELECTION");
			check(track.targetSequence > 0 || frame.selected().equals(bound.stock()), "FIRST_ACTION_TARGET_BINDING_MISMATCH");
			stock(scene, frame.selected());
			check(track.selected.add(frame.selected()), "REPEATED_NATIVE_TARGET_GENERATION");
			track.targetSequence = snap.targetSequence();
		}
		binding.lastOwnedAward.put(scene.profile(), snap);
		if (snap.farmCycleSequence() == track.cycles) { return; }
		check(snap.farmCycleSequence() == track.cycles + 1, "MISSED_NATIVE_FARM_CYCLE");
		final var receipts = frame.receipts();
		final var awardTarget = receipts.rewardTarget();
		if (awardTarget == null || !awardTarget.equals(receipts.killTarget()) || !awardTarget.equals(receipts.settledTarget()) || receipts.rewardSequence() != receipts.settledRewardSequence()) { return; }
		check(receipts.rewardSequence() == track.rewardSequence + 1 && track.selected.contains(awardTarget) && frame.selected() != null && !awardTarget.equals(frame.selected())
			&& snap.damageSequence() > 0 && snap.expGained() > track.exp && snap.spGained() > track.sp, "EXACT_NATIVE_CYCLE_REWARD");
		final var award = new Award(awardTarget, frame.selected(), snap.farmCycleSequence(), snap.damageSequence(), receipts.killSequence(), receipts.rewardSequence(),
			receipts.settledRewardSequence(), snap.expGained() - track.exp, snap.spGained() - track.sp);
		final double mp = track.mp.getOrDefault(awardTarget, 0.0);
		final long shots = track.shotBySelectedTarget.getOrDefault(awardTarget, 0L);
		if (scene.mage() ? mp <= 0 : shots <= 0) { return; }
		final var candidates = drops.drops(awardTarget);
		long pickupCount = 0, quantity = 0;
		final var exactLoot = new StringBuilder();
		for (Loot drop : candidates)
		{
			final var received = bound.sensor().pickupReceipt(drop.object());
			if (received == null) { continue; }
			check(received.itemId() == drop.item() && received.sourceObjectId() == drop.object() && received.actorCount() > 0
				&& received.actorCount() == received.count() && received.count() <= drop.count(), "EXACT_NATIVE_SOLO_PICKUP");
			pickupCount++;
			quantity = Math.addExact(quantity, received.actorCount());
			exactLoot.append(row(scene.profile(), scene.actor(), snap.epoch(), award.cycle(), award.target().objectId(), award.target().spawnGeneration(),
				drop.object(), drop.item(), drop.count(), received.actorCount(), received.sequence(), received.nativeConsumed()));
		}
		// A positive stock creation awaits the native pickup; empty drop lists cannot become loot proof.
		if (!candidates.isEmpty() && pickupCount == 0) { return; }
		if (!exactLoot.isEmpty()) { append(lootRows, exactLoot.toString()); }
		append(rows, row(scene.profile(), scene.actor(), snap.epoch(), award.cycle(), scene.npc(), award.target().objectId(), award.target().instanceId(),
			award.target().spawnGeneration(), award.next().objectId(), award.next().spawnGeneration(), award.damage(), award.kill(), award.reward(), award.settled(), award.exp(), award.sp(),
			track.shoots, shots, mp, candidates.size(), pickupCount, quantity));
		track.cycles = award.cycle();
		track.rewardSequence = award.reward();
		track.exp = snap.expGained();
		track.sp = snap.spGained();
		track.picked = Math.addExact(track.picked, pickupCount);
	}

	private static void stock(Scene scene, Target target)
	{
		final var object = World.getInstance().findObject(target.objectId());
		check(object instanceof Monster mob && matchesStock(scene, mob) && target.equals(mob.getNativeEvidenceTarget())
			&& scene.point().distanceSquared2D(point(mob)) <= (long) MAX_DISTANCE * MAX_DISTANCE, "UNAPPROVED_STOCK_TARGET_OR_GENERATION");
	}

	private static Status request(Launch launch) throws IOException
	{
		for (var entry : List.of(Map.entry("stop131.request", Status.STOPPED), Map.entry("pause131.request", Status.PAUSED)))
		{
			final var path = launch.root().resolve(entry.getKey());
			if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
			{
				check(smallText(physical(path, true)).equals(launch.nonce() + "\n"), "CONTROL_REQUEST_NONCE");
				return entry.getValue();
			}
		}
		return null;
	}

	private static String jsonScalar(String source, String key)
	{
		final var regex = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(?:\"([a-zA-Z0-9_-]+)\"|([0-9]+))(?=\\s*[,}])");
		final var fields = regex.matcher(source);
		check(fields.find(), "LAUNCHER_FIELD_MISSING");
		final String raw = fields.group(1) == null ? fields.group(2) : fields.group(1);
		check(!fields.find(), "LAUNCHER_FIELD_DUPLICATED");
		return raw;
	}

	private static Path taskRoot(Path path)
	{
		for (Path part = path.getParent(); part != null; part = part.getParent())
		{
			if (part.endsWith(Path.of(".phantom-local/logs/TASK131"))) { return part; }
		}
		throw failure("TASK_ROOT");
	}

	private static Path physical(Path input, boolean file) throws IOException
	{
		check(input.isAbsolute() && input.normalize().equals(input), "NONCANONICAL_ARTIFACT_PATH");
		for (Path part = input; part != null; part = part.getParent())
		{
			check(!Files.isSymbolicLink(part) && part.toRealPath().equals(part), "REPARSE_ARTIFACT_PATH");
		}
		check(file ? Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS) : Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS), "ARTIFACT_TYPE");
		return input;
	}

	private static Path codeSource(Class<?> type) throws Exception
	{
		return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
	}

	private static Path classFile(Class<?> type) throws Exception
	{
		return physical(codeSource(type).resolve(type.getName().replace('.', '/') + ".class"), true);
	}

	private static List<String[]> table(Path path, String expected, int size) throws IOException
	{
		final var lines = smallText(path).lines().toList();
		check(!lines.isEmpty() && lines.getFirst().equals(expected) && lines.size() <= 5, "STRICT_FIXTURE_TSV");
		final var rows = lines.subList(1, lines.size()).stream().map(value -> value.split("\t", -1)).toList();
		check(rows.stream().allMatch(row -> row.length == size), "STRICT_FIXTURE_ROW");
		return rows;
	}

	private static Properties properties(Path path) throws IOException
	{
		final var settings = new Properties();
		try (var reader = new java.io.StringReader(smallText(path))) { settings.load(reader); }
		return settings;
	}

	private static String smallText(Path file) throws IOException
	{
		check(Files.size(file) <= MAX_ARTIFACT_BYTES, "ARTIFACT_SIZE");
		return Files.readString(file);
	}

	private static String value(Properties properties, String name)
	{
		final String value = properties.getProperty(name);
		check(value != null && !value.isBlank(), "REQUIRED_PROPERTY_" + name.toUpperCase(Locale.ROOT));
		return value.strip();
	}

	private static int n(Properties fields, String key) { return Integer.parseInt(value(fields, key)); }
	private static String pin(Path file, String expected) throws Exception
	{
		final String actual = sha(file);
		check(actual.equalsIgnoreCase(expected), "PIN_MISMATCH");
		return actual;
	}

	private static String sha(Path file) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		try (var data = Files.newInputStream(file); var stream = new DigestInputStream(data, digest)) { stream.transferTo(java.io.OutputStream.nullOutputStream()); }
		return HexFormat.of().formatHex(digest.digest());
	}

	private static String optionalSha(Path path) throws Exception { return Files.exists(path, LinkOption.NOFOLLOW_LINKS) ? sha(physical(path, true)) : "NONE"; }
	private static String currentStart() { return ProcessHandle.current().info().startInstant().orElseThrow().toString(); }
	private static Instant earliest(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
	private static String row(Object... fields) { return String.join("\t", Arrays.stream(fields).map(Object::toString).toList()) + "\n"; }
	private static String line(String key, Object value) { return key + "=" + value + "\n"; }
	private static void writeNew(Path path, String text) throws IOException { Files.writeString(path, text, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
	private static void append(Path path, String text) throws IOException { Files.writeString(physical(path, true), text, StandardOpenOption.APPEND); }

	private static IllegalStateException failure(String reason) { return new IllegalStateException("TASK131_N01_" + reason); }
	private static void check(boolean condition, String reason) { if (!condition) { throw failure(reason); } }
}

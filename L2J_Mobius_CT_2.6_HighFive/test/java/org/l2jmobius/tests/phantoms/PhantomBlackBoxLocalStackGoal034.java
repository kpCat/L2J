/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.io.Reader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.PhantomPopulationResetService;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlResult;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorMode;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;

/** Real-process, guarded local-stack acceptance for Goal034. */
public final class PhantomBlackBoxLocalStackGoal034
{
	private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();
	private static final Duration OVERALL_TIMEOUT = Duration.ofMinutes(15);
	private static final Duration LOGIN_READY_TIMEOUT = Duration.ofMinutes(2);
	private static final Duration GAME_READY_TIMEOUT = Duration.ofMinutes(5);
	private static final Duration PROCESS_EXIT_GRACE = Duration.ofSeconds(30);
	private static final long PROCESS_ARTIFACT_LIMIT = 32L * 1024L * 1024L;
	private static final long RETAINED_LOG_LIMIT = 2L * 1024L * 1024L;
	private static final long POLL_MILLIS = 500L;
	private static final int EXPECTED_POPULATION = 10;
	private static final int EXPECTED_ACTIVE = 5;
	private static final int GENERATION_ONE_RESTART_LEAD_MINUTES = 7;
	private static final int GENERATION_TWO_RESTART_LEAD_MINUTES = 4;
	private static final Pattern SERVER_ID_PATTERN = Pattern.compile("\\bid=\"(\\d+)\"");
	private static final AtomicInteger PROCESS_SPAWNS = new AtomicInteger();
	private static final DateTimeFormatter RESTART_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

	private PhantomBlackBoxLocalStackGoal034()
	{
	}

	public static void main(String[] args)
	{
		int exitCode = 1;
		try
		{
			exitCode = switch (args.length == 0 ? "" : args[0])
			{
				case "contract" -> runContract();
				case "run" -> runBlackBox();
				case "cleanup" -> runCleanup(args);
				default -> 2;
			};
		}
		catch (Throwable throwable)
		{
			System.err.println("Goal034 harness failed: " + sanitize(throwable.getMessage(), null));
			throwable.printStackTrace(System.err);
		}
		System.exit(exitCode);
	}

	private static int runContract() throws Exception
	{
		final Path moduleRoot = moduleRoot();
		final Path contractRoot = moduleRoot.resolve(".phantom-local/blackbox/goal034-contract-" + ProcessHandle.current().pid());
		Files.createDirectories(contractRoot);
		try
		{
			final String canonicalQuery = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
			final PhantomTestDatabaseGuard.JdbcTarget mysqlTarget = PhantomTestDatabaseGuard.validateJdbcUrl("jdbc:mysql://127.0.0.1:3308/" + PhantomTestDatabaseGuard.TARGET_DATABASE + canonicalQuery);
			require("mysql".equals(mysqlTarget.transport()) && PhantomTestDatabaseGuard.TARGET_DATABASE.equals(mysqlTarget.database()), "Canonical mysql test database URL was rejected or changed.");
			final PhantomTestDatabaseGuard.JdbcTarget mariadbTarget = PhantomTestDatabaseGuard.validateJdbcUrl("jdbc:mariadb://localhost:3308/" + PhantomTestDatabaseGuard.TARGET_DATABASE + canonicalQuery);
			require("mariadb".equals(mariadbTarget.transport()) && PhantomTestDatabaseGuard.TARGET_DATABASE.equals(mariadbTarget.database()), "Canonical mariadb test database URL was rejected or changed.");

			final Path production = contractRoot.resolve("Database.ini");
			writeDatabaseConfig(production, "org.mariadb.jdbc.Driver", "jdbc:mariadb://127.0.0.1:3308/l2jmobiush5", PhantomTestDatabaseGuard.TARGET_USER, "not-used");
			boolean rejected = false;
			try
			{
				PhantomTestDatabaseGuard.validate(moduleRoot, production);
			}
			catch (PhantomTestDatabaseGuard.GuardException expected)
			{
				rejected = true;
			}
			require(rejected, "Production database negative control was accepted.");
			require(PROCESS_SPAWNS.get() == 0, "Production database negative control spawned a process.");

			final Path properties = contractRoot.resolve("Server.ini");
			Files.writeString(properties, "Alpha = one\r\nBeta = two\r\n", StandardCharsets.UTF_8);
			replaceProperty(properties, "Beta", "changed");
			require(Files.readString(properties, StandardCharsets.UTF_8).contains("Beta = changed"), "Exact property replacement failed.");
			verifyRoadmapV4(moduleRoot);
			System.out.println("[PASS] goal034.contract.mysql-test-url-accepted");
			System.out.println("[PASS] goal034.contract.mariadb-test-url-accepted");
			System.out.println("[PASS] goal034.contract.production-db-rejected-before-spawn");
			System.out.println("[PASS] goal034.contract.exact-sandbox-property-update");
			System.out.println("[PASS] goal034.contract.roadmap-v4-consistency");
			System.out.println("SUMMARY: suite=phantom-black-box-local-stack-goal034-contract total=5 passed=5 failed=0");
			return 0;
		}
		finally
		{
			deleteTree(contractRoot);
		}
	}
	private static void verifyRoadmapV4(Path moduleRoot) throws IOException
	{
		final Map<String, List<String>> expectations = Map.of(
			"PHANTOM_DEVELOPMENT_MASTER_PLAN.md", List.of("Roadmap v4", "Goal037", "rates", "Goal038"),
			"docs/PHANTOM_BOTS_ROADMAP.md", List.of("Roadmap v4", "Goal037", "rates", "Goal038"),
			"docs/phantoms/PHANTOM_CURRENT_STATUS.md", List.of("Roadmap v4", "Goal037", "rates", "Goal038"),
			"docs/phantoms/NEW_DIALOG_START_MESSAGE.txt", List.of("Roadmap v4", "Goal037", "rates", "Goal038"));
		for (Map.Entry<String, List<String>> entry : expectations.entrySet())
		{
			final String text = Files.readString(moduleRoot.resolve(entry.getKey()), StandardCharsets.UTF_8);
			for (String token : entry.getValue())
			{
				require(text.contains(token), entry.getKey() + " lacks Roadmap v4 token: " + token);
			}
		}
	}
	private static int runBlackBox() throws Exception
	{
		final long startedNanos = System.nanoTime();
		final long overallDeadline = startedNanos + OVERALL_TIMEOUT.toNanos();
		final Path moduleRoot = moduleRoot();
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)) + "-" + UUID.randomUUID().toString().substring(0, 8);
		final Path runRoot = moduleRoot.resolve(".phantom-local/blackbox/goal034").resolve(runId);
		final Path artifacts = runRoot.resolve("artifacts");
		Files.createDirectories(artifacts);
		final RunState run = new RunState(moduleRoot, runRoot, artifacts, runId, startedNanos);

		try
		{
			final Path sourceConfig = Path.of(System.getProperty("phantom.test.config", "")).toAbsolutePath().normalize();
			final PhantomTestDatabaseGuard.ValidatedSettings sourceSettings = PhantomTestDatabaseGuard.validate(moduleRoot, sourceConfig);
			run.workingHashesBefore.putAll(workingHashes(moduleRoot));
			prepareSandbox(run, sourceSettings);
			run.settings = PhantomTestDatabaseGuard.validate(moduleRoot, run.gameRoot.resolve("config/Database.ini"));
			final PhantomTestDatabaseGuard.ValidatedSettings loginSettings = PhantomTestDatabaseGuard.validate(moduleRoot, run.loginRoot.resolve("config/Database.ini"));
			require(sourceSettings.url().equals(run.settings.url()), "Game sandbox database URL diverged from the guarded source configuration.");
			require(run.settings.url().equals(loginSettings.url()) && run.settings.login().equals(loginSettings.login()), "Login/Game sandbox database settings diverged.");

			// Driver loading and all child process creation occur only after both sandbox configs pass the guard.
			Class.forName(run.settings.driver());
			assertCleanTestDatabase(run);
			createRegistration(run);

			run.login = startServer(run, "login", run.loginRoot, run.artifacts.resolve("login.stdout.log"), "LoginServer.jar", 128, 768);
			waitForLoginReady(run, overallDeadline);

			configureRestart(run.gameRoot.resolve("config/Server.ini"), GENERATION_ONE_RESTART_LEAD_MINUTES);
			run.gameOne = startServer(run, "game-generation-1", run.gameRoot, run.artifacts.resolve("game-generation-1.stdout.log"), "GameServer.jar", 512, 4096);
			waitForGameReady(run, run.gameOne, overallDeadline);
			final PopulationSnapshot generationOne = waitForPopulation(run, run.gameOne, overallDeadline);
			run.generationOne = generationOne;
			waitForNativeRestart(run, run.gameOne, overallDeadline);
			require(run.login.process.isAlive(), "LoginServer died between GameServer generations.");
			require(canConnect(run.loginClientPort) && canConnect(run.loginGamePort), "LoginServer listeners were not retained across GameServer restart.");

			configureRestart(run.gameRoot.resolve("config/Server.ini"), GENERATION_TWO_RESTART_LEAD_MINUTES);
			run.gameTwo = startServer(run, "game-generation-2", run.gameRoot, run.artifacts.resolve("game-generation-2.stdout.log"), "GameServer.jar", 512, 4096);
			waitForGameReady(run, run.gameTwo, overallDeadline);
			final PopulationSnapshot generationTwo = waitForPopulation(run, run.gameTwo, overallDeadline);
			require(generationOne.identities().equals(generationTwo.identities()), "Durable identity or immutable ecology assignment changed across restart.");
			run.generationTwo = generationTwo;
			waitForNativeRestart(run, run.gameTwo, overallDeadline);

			stopExact(run, run.login, "LoginServer exact-PID cleanup");
			run.functionalPass = true;
		}
		catch (Throwable failure)
		{
			run.failure = sanitize(failure.getClass().getSimpleName() + ": " + failure.getMessage(), run.settings);
			System.err.println("Goal034 functional gate failed: " + run.failure);
		}
		finally
		{
			cleanup(run);
			run.elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
			writeManifest(run);
			for (OwnedProcess process : run.processes)
			{
				trimToTail(process.output, RETAINED_LOG_LIMIT);
			}
		}

		final boolean passed = run.functionalPass && run.cleanupPass && !run.forcedCleanup && run.integrityPass && run.orphanFree;
		if (passed)
		{
			System.out.println("Goal034 black-box PASS: real LoginServer/GameServer JVMs, LIVING 10/5, native restart, continuity and exact cleanup.");
		}
		else
		{
			System.err.println("Goal034 black-box FAIL: " + Objects.requireNonNullElse(run.failure, "cleanup-or-integrity-gate"));
		}
		return passed ? 0 : 1;
	}

	private static int runCleanup(String[] args)
	{
		if (args.length != 3)
		{
			return 2;
		}
		final int expectedProfiles = Integer.parseInt(args[2]);
		try
		{
			final Path moduleRoot = Path.of(args[1]).toAbsolutePath().normalize();
			final Path config = Path.of("config/Database.ini").toAbsolutePath().normalize();
			PhantomTestDatabaseGuard.validate(moduleRoot, config);
			DatabaseConfig.load();
			DatabaseFactory.init();
			final PhantomPopulationResetService.Lifecycle lifecycle = new PhantomPopulationResetService.Lifecycle()
			{
				@Override
				public OperatorControlResult drain()
				{
					return new OperatorControlResult(OperatorControlCode.ALREADY_DRAINED, OperatorMode.DRAINED, false, false, State.STOPPED);
				}

				@Override
				public OperatorControlResult reseed()
				{
					return new OperatorControlResult(OperatorControlCode.CONFIG_DISABLED, OperatorMode.DISABLED, false, false, State.STOPPED);
				}
			};
			final PhantomPopulationResetService service = new PhantomPopulationResetService(lifecycle);
			final PhantomPopulationResetService.ResetPreview preview = service.preview();
			require(preview.safe(), "Goal032 cleanup preview blocked: " + preview.blockers());
			require(preview.identities() == expectedProfiles, "Goal032 cleanup owner count changed: expected=" + expectedProfiles + ", actual=" + preview.identities());
			final PhantomPopulationResetService.ResetResult result = service.confirm(preview.confirmationToken(), false);
			require(result.resetCommitted() && (result.identities() == expectedProfiles), "Goal032 cleanup did not commit exact owners: " + result.code());
			System.out.println("Goal034 exact owned-population cleanup PASS: profiles=" + expectedProfiles);
			return 0;
		}
		catch (Throwable failure)
		{
			System.err.println("Goal034 cleanup failed: " + sanitize(failure.getMessage(), null));
			return 1;
		}
		finally
		{
			if (DatabaseFactory.isInitialized())
			{
				DatabaseFactory.close();
			}
		}
	}
	private static void prepareSandbox(RunState run, PhantomTestDatabaseGuard.ValidatedSettings sourceSettings) throws Exception
	{
		run.loginRoot = run.runRoot.resolve("login");
		run.gameRoot = run.runRoot.resolve("game");
		Files.createDirectories(run.loginRoot);
		Files.createDirectories(run.gameRoot);
		run.loginClientPort = freePort(run.ports);
		run.loginGamePort = freePort(run.ports);
		run.gameClientPort = freePort(run.ports);

		final Path workingLogin = run.moduleRoot.resolve("dist/login");
		copyTree(workingLogin, run.loginRoot, workingLogin.resolve("log"));
		deleteTree(run.loginRoot.resolve("log"));
		Files.createDirectories(run.loginRoot.resolve("log"));

		final Path workingGame = run.moduleRoot.resolve("dist/game");
		copyTree(workingGame.resolve("config"), run.gameRoot.resolve("config"), null);
		Files.createDirectories(run.gameRoot.resolve("log"));
		for (String name : List.of("log.cfg", "console.cfg", "banned_ip.cfg"))
		{
			final Path source = workingGame.resolve(name);
			if (Files.isRegularFile(source))
			{
				Files.copy(source, run.gameRoot.resolve(name), StandardCopyOption.REPLACE_EXISTING);
			}
		}

		final String sandboxUrl = sourceSettings.url();
		writeDatabaseConfig(run.loginRoot.resolve("config/Database.ini"), sourceSettings.driver(), sandboxUrl, sourceSettings.login(), sourceSettings.password());
		writeDatabaseConfig(run.gameRoot.resolve("config/Database.ini"), sourceSettings.driver(), sandboxUrl, sourceSettings.login(), sourceSettings.password());

		final Path loginServer = run.loginRoot.resolve("config/Server.ini");
		replaceProperty(loginServer, "LoginserverHostname", "127.0.0.1");
		replaceProperty(loginServer, "LoginserverPort", Integer.toString(run.loginClientPort));
		replaceProperty(loginServer, "LoginHostname", "127.0.0.1");
		replaceProperty(loginServer, "LoginPort", Integer.toString(run.loginGamePort));
		replaceProperty(loginServer, "AcceptNewGameServer", "False");
		replaceProperty(loginServer, "LoginRestartSchedule", "False");
		replaceProperty(run.loginRoot.resolve("config/Interface.ini"), "EnableGUI", "False");

		final Path gameServer = run.gameRoot.resolve("config/Server.ini");
		replaceProperty(gameServer, "LoginHost", "127.0.0.1");
		replaceProperty(gameServer, "LoginPort", Integer.toString(run.loginGamePort));
		replaceProperty(gameServer, "GameserverHostname", "127.0.0.1");
		replaceProperty(gameServer, "GameserverPort", Integer.toString(run.gameClientPort));
		replaceProperty(gameServer, "AcceptAlternateID", "False");
		replaceProperty(gameServer, "DatapackRoot", slash(workingGame));
		replaceProperty(gameServer, "ScriptRoot", slash(workingGame.resolve("data/scripts")));
		replaceProperty(gameServer, "DeadlockWatcher", "False");
		replaceProperty(gameServer, "PrecautionaryRestartEnabled", "False");
		replaceProperty(gameServer, "ServerRestartScheduleEnabled", "True");
		replaceProperty(gameServer, "ServerRestartScheduleMessage", "False");
		replaceProperty(gameServer, "ServerRestartScheduleCountdown", "5");
		replaceProperty(run.gameRoot.resolve("config/Interface.ini"), "EnableGUI", "False");
		Files.writeString(run.gameRoot.resolve("config/ipconfig.xml"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<gameserver address=\"127.0.0.1\">\r\n\t<define subnet=\"127.0.0.0/8\" address=\"127.0.0.1\" />\r\n</gameserver>\r\n", StandardCharsets.UTF_8);

		final Path phantom = run.gameRoot.resolve("config/Custom/PhantomPlayers.ini");
		replaceProperty(phantom, "EnablePhantomSystem", "True");
		replaceProperty(phantom, "EnablePhantomDiagnostics", "False");
		replaceProperty(phantom, "MaxMaterializedPhantoms", Integer.toString(EXPECTED_ACTIVE));
		replaceProperty(phantom, "MaxScheduledPhantomProfiles", Integer.toString(EXPECTED_POPULATION));
		replaceProperty(phantom, "PhantomPopulationTarget", Integer.toString(EXPECTED_POPULATION));
		replaceProperty(phantom, "PhantomPopulationActiveTarget", Integer.toString(EXPECTED_ACTIVE));
		replaceProperty(phantom, "EnablePhantomEcology", "True");
		replaceProperty(phantom, "PhantomEcologyPreset", "LIVING");
		replaceProperty(phantom, "PhantomEcologyWorldAgeDays", "21");
	}

	private static void configureRestart(Path serverConfig, int leadMinutes) throws IOException
	{
		final String restartAt = LocalDateTime.now().plusMinutes(leadMinutes).format(RESTART_TIME);
		replaceProperty(serverConfig, "ServerRestartSchedule", restartAt);
	}

	private static void assertCleanTestDatabase(RunState run) throws SQLException
	{
		try (Connection connection = open(run.settings))
		{
			require(PhantomTestDatabaseGuard.TARGET_DATABASE.equals(connection.getCatalog()), "Connection catalog is not the allowlisted test database.");
			require(scalar(connection, "SELECT COUNT(*) FROM phantom_profiles") == 0, "Guarded DB contains pre-existing Phantom profiles; exact Goal034 ownership is ambiguous.");
		}
	}

	private static void createRegistration(RunState run) throws Exception
	{
		final Set<Integer> configuredIds = new LinkedHashSet<>();
		final String serverNames = Files.readString(run.moduleRoot.resolve("dist/login/data/servername.xml"), StandardCharsets.UTF_8);
		final Matcher matcher = SERVER_ID_PATTERN.matcher(serverNames);
		while (matcher.find())
		{
			configuredIds.add(Integer.parseInt(matcher.group(1)));
		}
		require(!configuredIds.isEmpty(), "Login server-name catalog contains no IDs.");

		try (Connection connection = open(run.settings); Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT server_id FROM gameservers"))
		{
			final Set<Integer> occupied = new LinkedHashSet<>();
			while (result.next())
			{
				occupied.add(result.getInt(1));
			}
			run.serverId = configuredIds.stream().filter(id -> !occupied.contains(id)).findFirst().orElseThrow(() -> new IllegalStateException("No free canonical LoginServer ID is available."));
		}

		final byte[] hexBytes = new byte[16];
		new SecureRandom().nextBytes(hexBytes);
		run.hexId = HexFormat.of().formatHex(hexBytes);
		run.registrationHost = "goal034:" + run.runId;
		try (Connection connection = open(run.settings); PreparedStatement statement = connection.prepareStatement("INSERT INTO gameservers (hexid,server_id,host) VALUES (?,?,?)"))
		{
			statement.setString(1, run.hexId);
			statement.setInt(2, run.serverId);
			statement.setString(3, run.registrationHost);
			require(statement.executeUpdate() == 1, "Test GameServer registration was not inserted.");
			run.registrationCreated = true;
		}
		replaceProperty(run.gameRoot.resolve("config/Server.ini"), "RequestServerID", Integer.toString(run.serverId));
		Files.writeString(run.gameRoot.resolve("config/hexid.txt"), "ServerID = " + run.serverId + "\r\nHexID = " + run.hexId + "\r\n", StandardCharsets.UTF_8);
	}

	private static int freePort(Set<Integer> reserved) throws IOException
	{
		for (int attempt = 0; attempt < 20; attempt++)
		{
			try (ServerSocket socket = new ServerSocket())
			{
				socket.setReuseAddress(false);
				socket.bind(new InetSocketAddress(LOOPBACK, 0));
				final int port = socket.getLocalPort();
				if (reserved.add(port))
				{
					return port;
				}
			}
		}
		throw new IOException("Could not select a distinct free loopback port.");
	}
	private static OwnedProcess startServer(RunState run, String name, Path directory, Path output, String jarName, int minimumMemoryMb, int maximumMemoryMb) throws IOException
	{
		final Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
		final Path jar = run.moduleRoot.resolve("dist/libs").resolve(jarName);
		require(Files.isRegularFile(jar), "Required built jar is missing: " + jarName);
		final List<String> command = List.of(java.toString(), "-Xms" + minimumMemoryMb + "m", "-Xmx" + maximumMemoryMb + "m", "-Djava.awt.headless=true", "-jar", jar.toString());
		PROCESS_SPAWNS.incrementAndGet();
		final Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
		final OwnedProcess owned = new OwnedProcess(name, process, output);
		run.processes.add(owned);
		return owned;
	}

	private static void waitForLoginReady(RunState run, long overallDeadline) throws Exception
	{
		final long deadline = Math.min(overallDeadline, System.nanoTime() + LOGIN_READY_TIMEOUT.toNanos());
		waitUntil("LoginServer readiness", run.login, deadline, () -> canConnect(run.loginClientPort) && canConnect(run.loginGamePort) && outputContains(run.login.output, "Game server listener is listening"));
		run.loginReady = true;
	}

	private static void waitForGameReady(RunState run, OwnedProcess game, long overallDeadline) throws Exception
	{
		final long deadline = Math.min(overallDeadline, System.nanoTime() + GAME_READY_TIMEOUT.toNanos());
		final String registration = "Registered on login as Server " + run.serverId + ":";
		waitUntil(game.name + " readiness", game, deadline, () -> canConnect(run.gameClientPort) && outputContains(game.output, "GameServer: Started") && outputContains(game.output, registration));
		run.registrationObserved = true;
	}

	private static void waitUntil(String label, OwnedProcess owned, long deadline, BooleanSupplier condition) throws Exception
	{
		while (System.nanoTime() < deadline)
		{
			checkProcess(owned);
			if (condition.getAsBoolean())
			{
				return;
			}
			Thread.sleep(POLL_MILLIS);
		}
		throw new AssertionError(label + " timed out.");
	}

	private static PopulationSnapshot waitForPopulation(RunState run, OwnedProcess game, long overallDeadline) throws Exception
	{
		final long deadline = Math.min(overallDeadline, System.nanoTime() + GAME_READY_TIMEOUT.toNanos());
		PopulationSnapshot latest = PopulationSnapshot.empty();
		while (System.nanoTime() < deadline)
		{
			checkProcess(game);
			latest = populationSnapshot(run.settings);
			if (latest.complete())
			{
				run.ownerProfiles.clear();
				run.ownerAccounts.clear();
				for (IdentityEvidence identity : latest.identities())
				{
					run.ownerProfiles.add(identity.profileId());
					run.ownerAccounts.add(identity.accountName());
				}
				return latest;
			}
			Thread.sleep(POLL_MILLIS);
		}
		throw new AssertionError("LIVING 10/5 convergence timed out: " + latest.summary());
	}

	private static PopulationSnapshot populationSnapshot(PhantomTestDatabaseGuard.ValidatedSettings settings) throws Exception
	{
		final PhantomPopulationStateCodec populationCodec = new PhantomPopulationStateCodec();
		final PhantomPopulationEcologyStateCodec ecologyCodec = new PhantomPopulationEcologyStateCodec();
		final List<IdentityEvidence> identities = new ArrayList<>();
		int online = 0;
		boolean terminal = true;
		try (Connection connection = open(settings); PreparedStatement statement = connection.prepareStatement(
			"SELECT p.profile_id,p.character_object_id,ps.payload,pe.payload,c.account_name,c.online " +
				"FROM phantom_profiles p " +
				"JOIN phantom_profile_components ps ON ps.profile_id=p.profile_id AND ps.component_type='population.state' " +
				"JOIN phantom_profile_components pe ON pe.profile_id=p.profile_id AND pe.component_type='population.ecology' " +
				"JOIN characters c ON c.charId=p.character_object_id ORDER BY p.profile_id"); ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				final PhantomPopulationState population = populationCodec.decode(result.getBytes(3));
				final PhantomPopulationEcologyState ecology = ecologyCodec.decode(result.getBytes(4));
				final boolean managed = (population.state() == PhantomPopulationState.State.READY) && (ecology.disposition() == PhantomPopulationEcologyState.Disposition.MANAGED);
				terminal &= managed && ecology.initialCatchupComplete() && !ecology.requestPending();
				online += result.getInt(6) == 0 ? 0 : 1;
				identities.add(new IdentityEvidence(result.getLong(1), result.getInt(2), result.getString(5), immutableFingerprint(population, ecology)));
			}
			terminal &= pendingCatchups(connection) == 0;
		}

		final Set<Long> profileIds = new LinkedHashSet<>();
		final Set<Integer> characterIds = new LinkedHashSet<>();
		final Set<String> accounts = new LinkedHashSet<>();
		for (IdentityEvidence identity : identities)
		{
			profileIds.add(identity.profileId());
			characterIds.add(identity.characterObjectId());
			accounts.add(identity.accountName());
		}
		final boolean unique = (profileIds.size() == identities.size()) && (characterIds.size() == identities.size()) && (accounts.size() == identities.size());
		final boolean complete = (identities.size() == EXPECTED_POPULATION) && unique && terminal && (online == EXPECTED_ACTIVE);
		return new PopulationSnapshot(List.copyOf(identities), online, terminal, unique, complete);
	}

	private static int pendingCatchups(Connection connection) throws Exception
	{
		final PhantomBackgroundCatchupStateCodec codec = new PhantomBackgroundCatchupStateCodec();
		int pending = 0;
		try (PreparedStatement statement = connection.prepareStatement("SELECT payload FROM phantom_profile_components WHERE component_type='background.catchup' ORDER BY profile_id"); ResultSet result = statement.executeQuery())
		{
			while (result.next())
			{
				final PhantomBackgroundCatchupState state = codec.decode(result.getBytes(1));
				if (state.status() != PhantomBackgroundCatchupState.Status.COMPLETE)
				{
					pending++;
				}
			}
		}
		return pending;
	}

	private static String immutableFingerprint(PhantomPopulationState population, PhantomPopulationEcologyState ecology) throws Exception
	{
		final String canonical = population.populationGeneration() + "|" + population.creationOrdinal() + "|" + population.reservedAccount() + "|" + population.ownershipToken() + "|" + population.characterName() + "|" + population.actualCharacterObjectId() + "|" + ecology.catalogHash() + "|" + ecology.preset() + "|" + ecology.ecologyGeneration() + "|" + ecology.assignmentOrdinal() + "|" + ecology.assignedAtEpochMinute() + "|" + ecology.virtualJoinEpochMinute() + "|" + ecology.initialTargetEpochMinute() + "|" + ecology.pace() + "|" + ecology.productiveShareBasisPoints() + "|" + ecology.productiveBlockMinutes() + "|" + ecology.personality() + "|" + new TreeMap<>(ecology.initialSocialTraits()) + "|" + ecology.scheduleTemplate() + "|" + ecology.disposition() + "|" + ecology.turnoverEligibleEpochMinute() + "|" + ecology.replacesProfileId();
		return sha256(canonical.getBytes(StandardCharsets.UTF_8));
	}

	private static void waitForNativeRestart(RunState run, OwnedProcess game, long overallDeadline) throws Exception
	{
		while ((System.nanoTime() < overallDeadline) && game.process.isAlive())
		{
			checkProcessArtifacts(game);
			Thread.sleep(POLL_MILLIS);
		}
		require(!game.process.isAlive(), game.name + " did not reach native scheduled restart before the overall deadline.");
		require(game.process.exitValue() == 2, game.name + " exit code was not native restart code 2: " + game.process.exitValue());
		final String output = tail(game.output, (int) RETAINED_LOG_LIMIT);
		require(output.contains("Phantom World: Initial subsystem drain completed"), game.name + " lacks Phantom drain evidence.");
		require(!output.contains("drain remains incomplete") && !output.contains("drain is incomplete"), game.name + " reported incomplete Phantom drain.");
		run.nativeRestarts++;
	}

	private static void checkProcess(OwnedProcess owned) throws Exception
	{
		if (!owned.process.isAlive())
		{
			throw new AssertionError(owned.name + " exited early with code " + owned.process.exitValue() + ".");
		}
		checkProcessArtifacts(owned);
		final String output = tail(owned.output, 1024 * 1024);
		for (String fatal : List.of("Exception in thread \"main\"", "Phantom World failed to start", "Registration failed:", "FATAL:"))
		{
			if (output.contains(fatal))
			{
				throw new AssertionError(owned.name + " emitted fatal startup evidence: " + fatal);
			}
		}
	}

	private static void checkProcessArtifacts(OwnedProcess owned) throws IOException
	{
		if (Files.exists(owned.output) && (Files.size(owned.output) > PROCESS_ARTIFACT_LIMIT))
		{
			throw new IOException("ARTIFACT_LIMIT_EXCEEDED: " + owned.name);
		}
	}
	private static void cleanup(RunState run)
	{
		run.cleanupPass = true;
		try
		{
			for (int index = run.processes.size() - 1; index >= 0; index--)
			{
				stopExact(run, run.processes.get(index), "failure/final exact-PID cleanup");
			}

			if (run.settings != null)
			{
				final int ownedProfiles;
				try (Connection connection = open(run.settings))
				{
					ownedProfiles = (int) scalar(connection, "SELECT COUNT(*) FROM phantom_profiles");
				}
				if (ownedProfiles > 0)
				{
					runOwnedPopulationCleanup(run, ownedProfiles);
				}
				removeRegistration(run);
				verifyOwnedRowsRemoved(run);
			}
		}
		catch (Throwable cleanupFailure)
		{
			run.cleanupPass = false;
			if (run.failure == null)
			{
				run.failure = "cleanup: " + sanitize(cleanupFailure.getMessage(), run.settings);
			}
			System.err.println("Goal034 cleanup gate failed: " + sanitize(cleanupFailure.getMessage(), run.settings));
		}
		finally
		{
			try
			{
				deleteTree(run.loginRoot);
				deleteTree(run.gameRoot);
			}
			catch (Throwable sandboxFailure)
			{
				run.cleanupPass = false;
				run.failure = Objects.requireNonNullElse(run.failure, "sandbox cleanup failed: " + sandboxFailure.getMessage());
			}
		}

		try
		{
			run.workingHashesAfter.putAll(workingHashes(run.moduleRoot));
			run.integrityPass = run.workingHashesBefore.equals(run.workingHashesAfter);
		}
		catch (Throwable integrityFailure)
		{
			run.integrityPass = false;
			run.failure = Objects.requireNonNullElse(run.failure, "working hash verification failed: " + integrityFailure.getMessage());
		}

		run.orphanFree = true;
		for (OwnedProcess process : run.processes)
		{
			if (process.process.isAlive() || ProcessHandle.of(process.pid).map(ProcessHandle::isAlive).orElse(false))
			{
				run.orphanFree = false;
			}
		}
		for (int port : run.ports)
		{
			if (!canBind(port))
			{
				run.orphanFree = false;
			}
		}
	}

	private static void runOwnedPopulationCleanup(RunState run, int expectedProfiles) throws Exception
	{
		final Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
		final Path output = run.artifacts.resolve("population-cleanup.stdout.log");
		final List<String> command = List.of(java.toString(), "-Xms128m", "-Xmx1536m", "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"), PhantomBlackBoxLocalStackGoal034.class.getName(), "cleanup", run.moduleRoot.toString(), Integer.toString(expectedProfiles));
		PROCESS_SPAWNS.incrementAndGet();
		final Process process = new ProcessBuilder(command).directory(run.gameRoot.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
		final OwnedProcess cleanup = new OwnedProcess("population-cleanup", process, output);
		run.processes.add(cleanup);
		if (!process.waitFor(Duration.ofMinutes(2).toMillis(), TimeUnit.MILLISECONDS))
		{
			stopExact(run, cleanup, "population cleanup timeout");
			throw new AssertionError("Goal032 owned-population cleanup timed out.");
		}
		require(process.exitValue() == 0, "Goal032 owned-population cleanup failed with exit code " + process.exitValue() + ".");
		run.populationCleaned = expectedProfiles;
	}

	private static void removeRegistration(RunState run) throws SQLException
	{
		if (!run.registrationCreated)
		{
			return;
		}
		try (Connection connection = open(run.settings); PreparedStatement statement = connection.prepareStatement("DELETE FROM gameservers WHERE server_id=? AND hexid=? AND host=?"))
		{
			statement.setInt(1, run.serverId);
			statement.setString(2, run.hexId);
			statement.setString(3, run.registrationHost);
			require(statement.executeUpdate() == 1, "Exact Goal034 GameServer registration cleanup did not remove one row.");
			run.registrationRemoved = true;
		}
	}

	private static void verifyOwnedRowsRemoved(RunState run) throws SQLException
	{
		try (Connection connection = open(run.settings))
		{
			require(scalar(connection, "SELECT COUNT(*) FROM phantom_profiles") == 0, "Goal034 cleanup retained Phantom profiles.");
			for (IdentityEvidence identity : run.generationTwo == null ? run.generationOne == null ? List.<IdentityEvidence>of() : run.generationOne.identities() : run.generationTwo.identities())
			{
				try (PreparedStatement character = connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE charId=? OR account_name=?"); PreparedStatement account = connection.prepareStatement("SELECT COUNT(*) FROM accounts WHERE login=?"))
				{
					character.setInt(1, identity.characterObjectId());
					character.setString(2, identity.accountName());
					try (ResultSet result = character.executeQuery())
					{
						result.next();
						require(result.getLong(1) == 0, "Goal034 cleanup retained an owned character.");
					}
					account.setString(1, identity.accountName());
					try (ResultSet result = account.executeQuery())
					{
						result.next();
						require(result.getLong(1) == 0, "Goal034 cleanup retained an owned account.");
					}
				}
			}
		}
	}

	private static void stopExact(RunState run, OwnedProcess owned, String reason) throws Exception
	{
		if ((owned == null) || !owned.process.isAlive())
		{
			return;
		}
		final List<ProcessHandle> descendants = owned.process.descendants().toList();
		owned.process.destroy();
		if (!owned.process.waitFor(PROCESS_EXIT_GRACE.toMillis(), TimeUnit.MILLISECONDS))
		{
			for (ProcessHandle descendant : descendants)
			{
				if (descendant.isAlive())
				{
					descendant.destroyForcibly();
				}
			}
			owned.process.destroyForcibly();
			owned.process.waitFor(PROCESS_EXIT_GRACE.toMillis(), TimeUnit.MILLISECONDS);
			owned.forced = true;
			run.forcedCleanup = true;
			System.err.println("Forced exact-process cleanup used for " + owned.name + ": " + reason);
		}
	}

	private static Connection open(PhantomTestDatabaseGuard.ValidatedSettings settings) throws SQLException
	{
		return DriverManager.getConnection(settings.url(), settings.login(), settings.password());
	}

	private static long scalar(Connection connection, String sql) throws SQLException
	{
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql))
		{
			require(result.next(), "Scalar query returned no row.");
			return result.getLong(1);
		}
	}

	private static Map<String, String> workingHashes(Path moduleRoot) throws Exception
	{
		final Map<String, String> hashes = new LinkedHashMap<>();
		for (String relative : List.of("dist/login/config/Database.ini", "dist/login/config/Server.ini", "dist/login/config/Interface.ini", "dist/game/config/Database.ini", "dist/game/config/Server.ini", "dist/game/config/Custom/PhantomPlayers.ini", "dist/game/config/Interface.ini", "dist/game/config/hexid.txt"))
		{
			final Path path = moduleRoot.resolve(relative);
			hashes.put(relative, Files.isRegularFile(path) ? sha256(Files.readAllBytes(path)) : "MISSING");
		}
		return hashes;
	}
	private static void writeDatabaseConfig(Path path, String driver, String url, String login, String password) throws IOException
	{
		Files.createDirectories(path.getParent());
		final String text = "Driver = " + driver + "\r\n" +
			"URL = " + url + "\r\n" +
			"Login = " + login + "\r\n" +
			"Password = " + password + "\r\n" +
			"MaximumDatabaseConnections = 4\r\n" +
			"TestDatabaseConnections = False\r\n" +
			"BackupDatabase = False\r\n";
		Files.writeString(path, text, StandardCharsets.UTF_8);
	}

	private static void replaceProperty(Path path, String key, String value) throws IOException
	{
		final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		final Pattern pattern = Pattern.compile("^(\\s*)" + Pattern.quote(key) + "\\s*=.*$", Pattern.CASE_INSENSITIVE);
		int replacements = 0;
		for (int index = 0; index < lines.size(); index++)
		{
			final Matcher matcher = pattern.matcher(lines.get(index));
			if (matcher.matches())
			{
				lines.set(index, matcher.group(1) + key + " = " + value);
				replacements++;
			}
		}
		require(replacements == 1, "Expected one config key " + key + " in " + path + " but found " + replacements + ".");
		final Path temporary = path.resolveSibling(path.getFileName() + ".goal034.tmp");
		Files.write(temporary, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		try
		{
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException unsupportedAtomicMove)
		{
			Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void copyTree(Path source, Path target, Path excludedRoot) throws IOException
	{
		try (Stream<Path> stream = Files.walk(source))
		{
			for (Path entry : stream.sorted().toList())
			{
				if ((excludedRoot != null) && entry.toAbsolutePath().normalize().startsWith(excludedRoot.toAbsolutePath().normalize()))
				{
					continue;
				}
				final Path destination = target.resolve(source.relativize(entry).toString());
				if (Files.isDirectory(entry))
				{
					Files.createDirectories(destination);
				}
				else
				{
					Files.createDirectories(destination.getParent());
					Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private static void deleteTree(Path path) throws IOException
	{
		if ((path == null) || !Files.exists(path))
		{
			return;
		}
		final Path absolute = path.toAbsolutePath().normalize();
		require(absolute.toString().contains(".phantom-local"), "Refusing to delete a non-Goal local path: " + absolute);
		try (Stream<Path> stream = Files.walk(absolute))
		{
			for (Path entry : stream.sorted(Comparator.reverseOrder()).toList())
			{
				Files.deleteIfExists(entry);
			}
		}
	}

	private static boolean canConnect(int port)
	{
		try (Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress(LOOPBACK, port), 250);
			return true;
		}
		catch (IOException ignored)
		{
			return false;
		}
	}

	private static boolean canBind(int port)
	{
		try (ServerSocket socket = new ServerSocket())
		{
			socket.setReuseAddress(false);
			socket.bind(new InetSocketAddress(LOOPBACK, port));
			return true;
		}
		catch (IOException ignored)
		{
			return false;
		}
	}

	private static boolean outputContains(Path output, String text)
	{
		try
		{
			return tail(output, 1024 * 1024).contains(text);
		}
		catch (IOException ignored)
		{
			return false;
		}
	}

	private static String tail(Path path, int maximumBytes) throws IOException
	{
		if (!Files.isRegularFile(path))
		{
			return "";
		}
		try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ))
		{
			final int length = (int) Math.min(channel.size(), maximumBytes);
			final ByteBuffer buffer = ByteBuffer.allocate(length);
			channel.position(channel.size() - length);
			while (buffer.hasRemaining() && (channel.read(buffer) >= 0))
			{
				// Read the bounded tail.
			}
			buffer.flip();
			return StandardCharsets.UTF_8.decode(buffer).toString();
		}
	}

	private static void trimToTail(Path path, long maximumBytes)
	{
		try
		{
			if (!Files.isRegularFile(path) || (Files.size(path) <= maximumBytes))
			{
				return;
			}
			final byte[] retained;
			try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ))
			{
				retained = new byte[(int) maximumBytes];
				channel.position(channel.size() - maximumBytes);
				final ByteBuffer buffer = ByteBuffer.wrap(retained);
				while (buffer.hasRemaining() && (channel.read(buffer) >= 0))
				{
					// Read the retained bounded tail.
				}
			}
			Files.write(path, retained, StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch (IOException ignored)
		{
			// Manifest still records the artifact-limit gate; trimming is best effort after process termination.
		}
	}

	private static void writeManifest(RunState run)
	{
		try
		{
			final Properties manifest = new Properties();
			manifest.setProperty("goal", "034");
			manifest.setProperty("run.id", run.runId);
			manifest.setProperty("status", run.functionalPass && run.cleanupPass && run.integrityPass && run.orphanFree && !run.forcedCleanup ? "PASS" : "FAIL");
			manifest.setProperty("database", "127.0.0.1:3308/" + PhantomTestDatabaseGuard.TARGET_DATABASE);
			manifest.setProperty("database.production.used", "false");
			manifest.setProperty("ports.login.client", Integer.toString(run.loginClientPort));
			manifest.setProperty("ports.login.game", Integer.toString(run.loginGamePort));
			manifest.setProperty("ports.game.client", Integer.toString(run.gameClientPort));
			manifest.setProperty("server.id", Integer.toString(run.serverId));
			manifest.setProperty("login.ready", Boolean.toString(run.loginReady));
			manifest.setProperty("registration.observed", Boolean.toString(run.registrationObserved));
			manifest.setProperty("native.restarts", Integer.toString(run.nativeRestarts));
			manifest.setProperty("generation.1", run.generationOne == null ? "absent" : run.generationOne.summary());
			manifest.setProperty("generation.2", run.generationTwo == null ? "absent" : run.generationTwo.summary());
			manifest.setProperty("cleanup.population", Integer.toString(run.populationCleaned));
			manifest.setProperty("cleanup.registration", Boolean.toString(run.registrationRemoved));
			manifest.setProperty("cleanup.forced", Boolean.toString(run.forcedCleanup));
			manifest.setProperty("working.integrity", Boolean.toString(run.integrityPass));
			manifest.setProperty("orphans.none", Boolean.toString(run.orphanFree));
			manifest.setProperty("elapsed.millis", Long.toString(run.elapsedMillis));
			manifest.setProperty("failure", Objects.requireNonNullElse(run.failure, "none"));
			for (OwnedProcess process : run.processes)
			{
				manifest.setProperty("pid." + process.name, Long.toString(process.pid));
			}
			try (var writer = Files.newBufferedWriter(run.artifacts.resolve("manifest.properties"), StandardCharsets.UTF_8))
			{
				manifest.store(writer, "Goal034 bounded black-box evidence");
			}
		}
		catch (IOException manifestFailure)
		{
			System.err.println("Could not write Goal034 manifest: " + manifestFailure.getMessage());
		}
	}

	private static String slash(Path path)
	{
		return path.toAbsolutePath().normalize().toString().replace('\\', '/');
	}

	private static Path moduleRoot() throws IOException
	{
		return Path.of(System.getProperty("phantom.module.root", ".")).toRealPath();
	}

	private static String sha256(byte[] bytes) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static String sanitize(String message, PhantomTestDatabaseGuard.ValidatedSettings settings)
	{
		String sanitized = Objects.requireNonNullElse(message, "no-detail").replace('\r', ' ').replace('\n', ' ');
		if ((settings != null) && (settings.password() != null) && !settings.password().isEmpty())
		{
			sanitized = sanitized.replace(settings.password(), "<redacted>");
		}
		return sanitized.length() <= 512 ? sanitized : sanitized.substring(0, 512);
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new AssertionError(message);
		}
	}

	private static final class OwnedProcess
	{
		private final String name;
		private final Process process;
		private final Path output;
		private final long pid;
		private boolean forced;

		private OwnedProcess(String name, Process process, Path output)
		{
			this.name = name;
			this.process = process;
			this.output = output;
			pid = process.pid();
		}
	}

	private static final class RunState
	{
		private final Path moduleRoot;
		private final Path runRoot;
		private final Path artifacts;
		private final String runId;
		private final long startedNanos;
		private final Set<Integer> ports = new LinkedHashSet<>();
		private final List<OwnedProcess> processes = new ArrayList<>();
		private final Set<Long> ownerProfiles = new LinkedHashSet<>();
		private final Set<String> ownerAccounts = new LinkedHashSet<>();
		private final Map<String, String> workingHashesBefore = new LinkedHashMap<>();
		private final Map<String, String> workingHashesAfter = new LinkedHashMap<>();
		private Path loginRoot;
		private Path gameRoot;
		private PhantomTestDatabaseGuard.ValidatedSettings settings;
		private OwnedProcess login;
		private OwnedProcess gameOne;
		private OwnedProcess gameTwo;
		private PopulationSnapshot generationOne;
		private PopulationSnapshot generationTwo;
		private int loginClientPort;
		private int loginGamePort;
		private int gameClientPort;
		private int serverId;
		private String hexId;
		private String registrationHost;
		private String failure;
		private int nativeRestarts;
		private int populationCleaned;
		private long elapsedMillis;
		private boolean registrationCreated;
		private boolean registrationRemoved;
		private boolean loginReady;
		private boolean registrationObserved;
		private boolean functionalPass;
		private boolean cleanupPass;
		private boolean forcedCleanup;
		private boolean integrityPass;
		private boolean orphanFree;

		private RunState(Path moduleRoot, Path runRoot, Path artifacts, String runId, long startedNanos)
		{
			this.moduleRoot = moduleRoot;
			this.runRoot = runRoot;
			this.artifacts = artifacts;
			this.runId = runId;
			this.startedNanos = startedNanos;
		}
	}

	private record IdentityEvidence(long profileId, int characterObjectId, String accountName, String immutableFingerprint)
	{
	}

	private record PopulationSnapshot(List<IdentityEvidence> identities, int online, boolean terminal, boolean unique, boolean complete)
	{
		private static PopulationSnapshot empty()
		{
			return new PopulationSnapshot(List.of(), 0, false, false, false);
		}

		private String summary()
		{
			return "profiles=" + identities.size() + ",online=" + online + ",terminal=" + terminal + ",unique=" + unique;
		}
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig;

/** Actual native Synthetic START/state/watchdog boundaries in the exact own TEST lane. */
public final class LocalPlayContinuity028Suite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private int _previousObject;
	private String _previousName;

	public static void main(String[] args)
	{
		System.exit(PhantomTestLauncher.runSuite("localplay-continuity028", new LocalPlayContinuity028Suite(), new PhantomTestContext(28002801, Path.of(args[0]), Path.of(args[1]))));
	}
	@Override public String id() { return "localplay-continuity028"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_previousObject = (int) field(LocalPlayPilotConfig.class, "_syntheticObjectId").get(null);
		_previousName = (String) field(LocalPlayPilotConfig.class, "_syntheticName").get(null);
		field(LocalPlayPilotConfig.class, "_syntheticObjectId").set(null, _environment.observer().objectId());
		field(LocalPlayPilotConfig.class, "_syntheticName").set(null, _environment.observer().characterName());
	}
	@Override public void afterAll(PhantomTestContext context) throws Exception
	{
		field(LocalPlayPilotConfig.class, "_syntheticObjectId").set(null, _previousObject);
		field(LocalPlayPilotConfig.class, "_syntheticName").set(null, _previousName);
		_environment.shutdown();
	}
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("T01-native-start-advertises-fixed-525s-expiry", this::expiry);
		registry.add("T05-wrong-heartbeat-identity-closes-native-session", context -> guard(context, false));
		registry.add("T05-expired-monotonic-deadline-closes-despite-fresh-heartbeat", context -> guard(context, true));
		registry.add("T05-real-publisher-keeps-native-heartbeat-readable", this::publisher);
	}
	private static Field field(Class<?> type, String name) throws Exception
	{
		final Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
	}
	private static Method method(Class<?> type, String name, Class<?>... args) throws Exception
	{
		final Method value = type.getDeclaredMethod(name, args); value.setAccessible(true); return value;
	}
	private static Properties read(Path path) throws Exception
	{
		final var result = new Properties();
		try (var input = Files.newInputStream(path)) { result.load(input); }
		return result;
	}
	private final class NativeSession implements AutoCloseable
	{
		final Path runtime;
		final Path mailbox;
		final Object service;
		final Class<?> type;
		final String run = UUID.randomUUID().toString();
		final long startedWall;
		NativeSession(PhantomTestContext context) throws Exception
		{
			runtime = Files.createTempDirectory(context.moduleRoot().resolve(System.getProperty("phantom.contract031.manifest") == null ? ".phantom-local/contract028a/test" : ".phantom-local/contract031t/test"), "synthetic028-");
			final var user = runtime.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
			Files.getFileAttributeView(runtime, AclFileAttributeView.class).setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(user)
				.setPermissions(EnumSet.allOf(AclEntryPermission.class)).setFlags(AclEntryFlag.FILE_INHERIT, AclEntryFlag.DIRECTORY_INHERIT).build()));
			mailbox = runtime.resolve("playtest-synthetic").resolve(run);
			for (String part : List.of("inbox", "processing", "results", "journal")) { Files.createDirectories(mailbox.resolve(part)); }
			type = Class.forName("org.l2jmobius.gameserver.localplay.LocalPlaySyntheticHumanService");
			final var constructor = type.getDeclaredConstructor(Path.class, String.class, long.class, long.class, BooleanSupplier.class, BooleanSupplier.class);
			constructor.setAccessible(true);
			service = constructor.newInstance(runtime, "exact-test-runtime", 1L, 1L, (BooleanSupplier) () -> true, (BooleanSupplier) () -> true);
			final var control = new Properties(); control.setProperty("expiresUtcMillis", Long.toString(System.currentTimeMillis() + 30000));
			startedWall = System.currentTimeMillis();
			try { method(type, "start", String.class, Properties.class).invoke(service, run, control); }
			catch (Exception failure) { close(); throw failure; }
			PhantomAssertions.assertEquals("RUNNING", read(mailbox.resolve("session.properties")).getProperty("state"), "Real native START required.");
		}
		void heartbeat(String identity) throws Exception
		{
			final var value = new Properties(); value.setProperty("version", "1"); value.setProperty("sessionId", identity); value.setProperty("runId", run);
			value.setProperty("updatedUtcMillis", Long.toString(System.currentTimeMillis()));
			try (var output = Files.newOutputStream(mailbox.resolve("heartbeat.properties"))) { value.store(output, "TEST028 heartbeat"); }
		}
		@Override public void close() throws Exception
		{
			method(type, "close").invoke(service);
			try (var paths = Files.walk(runtime)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); } }
		}
	}
	private void expiry(PhantomTestContext context) throws Exception
	{
		try (var session = new NativeSession(context))
		{
			final long first = Long.parseLong(read(session.mailbox.resolve("session.properties")).getProperty("expiresUtcMillis"));
			PhantomAssertions.assertTrue(first >= session.startedWall + 525000 && first <= System.currentTimeMillis() + 525000, "Native START must advertise its actual 525s TTL, observed delta=" + (first - session.startedWall));
			Thread.sleep(20);
			method(session.type, "writeState").invoke(session.service);
			PhantomAssertions.assertEquals(first, Long.parseLong(read(session.mailbox.resolve("session.properties")).getProperty("expiresUtcMillis")), "Metadata publication must not renew expiry.");
		}
	}
	private void guard(PhantomTestContext context, boolean ttl) throws Exception
	{
		try (var session = new NativeSession(context))
		{
			session.heartbeat(ttl ? session.run : UUID.randomUUID().toString());
			if (ttl) { field(session.type, "_deadlineNanos").setLong(session.service, System.nanoTime() - 1); }
			method(session.type, "poll").invoke(session.service);
			PhantomAssertions.assertEquals("STOPPED", read(session.mailbox.resolve("session.properties")).getProperty("state"), "Guard must close the native session.");
			PhantomAssertions.assertTrue(read(session.mailbox.resolve("session.properties")).getProperty("reason").startsWith(ttl ? "SYNTHETIC_TTL" : "SYNTHETIC_HEARTBEAT"), "First failed guard must be recorded without changing its closure.");
			PhantomAssertions.assertEquals(null, org.l2jmobius.gameserver.model.World.getInstance().getPlayer(_environment.observer().objectId()), "Guard must remove only its native observer.");
		}
	}
	private void publisher(PhantomTestContext context) throws Exception
	{
		try (var session = new NativeSession(context))
		{
			session.heartbeat(session.run);
			final String library = context.moduleRoot().resolve("tools/phantom-local-play/LocalPlay-Pilot.ps1").toString().replace("'", "''");
			final String mailbox = session.mailbox.toString().replace("'", "''");
			final String script = ". '" + library + "'; $context028=@{PilotRoot='" + mailbox + "';ActorMode='Synthetic'}; for($i028=0;$i028 -lt 200;$i028++){ Write-PilotHeartbeat $context028 '" + session.run + "' '" + session.run + "'; Start-Sleep -Milliseconds 5 }";
			final var output = session.runtime.resolve("publisher.log");
			final var process = new ProcessBuilder("pwsh", "-NoProfile", "-Command", script).redirectErrorStream(true).redirectOutput(output.toFile()).start();
			String failure = null;
			final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
			try
			{
				while (process.isAlive() && (System.nanoTime() < deadline))
				{
					if (!(boolean) method(session.type, "heartbeatCurrent").invoke(session.service))
					{
						failure = String.valueOf(field(session.type, "_heartbeatReason").get(session.service));
						try { failure += ":postRead=" + read(session.mailbox.resolve("heartbeat.properties")); }
						catch (Exception exact) { failure += ":postRead=" + exact; }
						break;
					}
					Thread.sleep(1);
				}
				PhantomAssertions.assertTrue(failure == null, "Real single heartbeat publisher rejected by native guard: " + failure);
				PhantomAssertions.assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0, "Bounded real publisher failed: " + Files.readString(output));
				Files.delete(session.mailbox.resolve("heartbeat.properties"));
				method(session.type, "poll").invoke(session.service);
				PhantomAssertions.assertEquals("STOPPED", read(session.mailbox.resolve("session.properties")).getProperty("state"), "Missing heartbeat must still close the native session.");
			}
			finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
		}
	}
}

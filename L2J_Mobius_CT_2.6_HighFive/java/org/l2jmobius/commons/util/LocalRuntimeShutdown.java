/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.commons.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.l2jmobius.commons.threads.ThreadPool;

/** Opt-in local operator transport to the stock shutdown; no gameplay or Attach authority. */
public final class LocalRuntimeShutdown
{
	private static final Logger LOGGER = Logger.getLogger(LocalRuntimeShutdown.class.getName());
	private static final long DOTNET_EPOCH_TICKS = 621355968000000000L;

	private LocalRuntimeShutdown() { }

	/** Validate before ordinary Main can open a database or listener. Other runtimes remain disabled. */
	public static void validateLaunch(String role) throws Exception
	{
		if (!Boolean.getBoolean("phantom.task064.control")) { return; }
		final Path root = runtimeRoot(role);
		final Properties owner = read(root.resolve("ownership064.properties"));
		final String schema = owner.getProperty("schema", "");
		if (!"1".equals(owner.getProperty("version")) || !"TASK064_CAMPAIGN".equals(owner.getProperty("owner"))
			|| !schema.matches("l2jmobiush5_phantom_(test_064|release_064a)")
			|| !(schema.endsWith("test_064") ? "MUTABLE_FIXTURE" : "RELEASE_CANDIDATE").equals(owner.getProperty("role"))
			|| !root.equals(Path.of(owner.getProperty("runtimeRoot", "")).toAbsolutePath().normalize()))
		{
			throw new IllegalArgumentException("TASK064 runtime root/schema/owner mismatch.");
		}
		final Properties database = read(Path.of("config/Database.ini").toAbsolutePath());
		if (!database.getProperty("URL", "").matches("jdbc:(?:mysql|mariadb)://127\\.0\\.0\\.1:3308/" + schema + "\\?useSSL=false(?:&[A-Za-z0-9=:-]+)*"))
		{
			throw new IllegalArgumentException("TASK064 JDBC endpoint mismatch.");
		}
		final Path jar = root.resolve("libs/" + role + ".jar");
		final Path loaded = Path.of(LocalRuntimeShutdown.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
		if (!jar.equals(loaded) || !sha256(jar).equalsIgnoreCase(owner.getProperty(role.equals("GameServer") ? "gameJarSha256" : "loginJarSha256", "")))
		{
			throw new IllegalArgumentException("TASK064 whole JAR provenance mismatch.");
		}
	}

	public static void start(String role, Runnable stockShutdown) throws Exception
	{
		if (!Boolean.getBoolean("phantom.task064.control")) { return; }
		validateLaunch(role);
		final Path root = runtimeRoot(role);
		final String runtime = System.getProperty("phantom.localplay.runtime");
		final long pid = ProcessHandle.current().pid();
		final Path recordPath = root.resolve("local-play/pids/" + role + ".json");
		if (!Files.isRegularFile(recordPath, LinkOption.NOFOLLOW_LINKS) || !recordPath.equals(recordPath.toRealPath()) || Files.size(recordPath) > 4096) { throw new IllegalArgumentException("TASK064 bounded own PID record required."); }
		final String record = Files.readString(recordPath, StandardCharsets.UTF_8).replace("\ufeff", "").strip();
		final var identity = Pattern.compile("\\{\"format\":2,\"role\":\"" + role + "\",\"pid\":" + pid + ",\"startTimeUtcTicks\":([0-9]+),\"jar\":\"" + role + "\\.jar\",\"runtime\":\"" + runtime + "\"\\}").matcher(record);
		if (!identity.matches()) { throw new IllegalArgumentException("TASK064 PID record mismatch."); }
		final long ticks = Long.parseLong(identity.group(1));
		if (((ticks - DOTNET_EPOCH_TICKS) / 10000L) != ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli()) { throw new IllegalArgumentException("TASK064 PID incarnation mismatch."); }
		final Path directory = root.resolve("local-play/control");
		if (!directory.equals(directory.toRealPath())) { throw new IllegalArgumentException("TASK064 control alias forbidden."); }
		final AtomicBoolean requested = new AtomicBoolean();
		// One operator poll uses the existing shared pool, independently of Phantom population.
		ThreadPool.scheduleAtFixedRate(() ->
		{
			if (requested.get()) { return; }
			final Path file = directory.resolve("stop-" + role + ".properties");
			if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { return; }
			try
			{
				final boolean accepted = validRequest(read(file), runtime, role, pid, ticks, System.currentTimeMillis());
				Files.move(file, directory.resolve((accepted ? "accepted-" : "rejected-") + role + "-" + System.nanoTime() + ".properties"), StandardCopyOption.ATOMIC_MOVE);
				if (accepted && requested.compareAndSet(false, true))
				{
					LOGGER.info("TASK064 identity-verified stock shutdown requested for " + role + ".");
					ThreadPool.execute(stockShutdown);
				}
			}
			catch (Exception exception) { LOGGER.fine("TASK064 shutdown request rejected: " + exception.getClass().getSimpleName()); }
		}, 500, 500);
	}

	static boolean validRequest(Properties request, String runtime, String role, long pid, long ticks, long now)
	{
		try
		{
			final long deadline = Long.parseLong(request.getProperty("deadlineUtcMillis", ""));
			return "1".equals(request.getProperty("version")) && "TASK064_CAMPAIGN".equals(request.getProperty("owner"))
				&& runtime.equals(request.getProperty("runtime")) && role.equals(request.getProperty("role"))
				&& Long.toString(pid).equals(request.getProperty("pid")) && Long.toString(ticks).equals(request.getProperty("startTimeUtcTicks"))
				&& (deadline >= now) && (deadline <= now + 120000);
		}
		catch (NumberFormatException exception) { return false; }
	}

	private static Path runtimeRoot(String role) throws Exception
	{
		if (!role.equals(System.getProperty("phantom.localplay.role")) || !(role.equals("GameServer") || role.equals("LoginServer"))) { throw new IllegalArgumentException("TASK064 role mismatch."); }
		final Path cwd = Path.of("").toAbsolutePath().normalize();
		if (!cwd.equals(cwd.toRealPath()) || !cwd.getFileName().toString().equals(role.equals("GameServer") ? "game" : "login")) { throw new IllegalArgumentException("TASK064 working root mismatch."); }
		final Path root = cwd.getParent();
		final String normalized = root.toString().replace('/', '\\').replaceAll("\\\\+$", "").toLowerCase(Locale.ROOT);
		final String id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
		if (!id.equals(System.getProperty("phantom.localplay.runtime"))) { throw new IllegalArgumentException("TASK064 runtime marker mismatch."); }
		return root;
	}

	private static Properties read(Path file) throws Exception
	{
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toAbsolutePath().normalize().equals(file.toRealPath()) || Files.size(file) > 8192) { throw new IllegalArgumentException("TASK064 bounded regular control/config file required."); }
		final Properties result = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { result.load(reader); }
		return result;
	}

	private static String sha256(Path file) throws Exception
	{
		try (var input = Files.newInputStream(file))
		{
			final var digest = MessageDigest.getInstance("SHA-256");
			final byte[] buffer = new byte[8192];
			for (int count; (count = input.read(buffer)) >= 0;) { digest.update(buffer, 0, count); }
			return HexFormat.of().formatHex(digest.digest());
		}
	}
}

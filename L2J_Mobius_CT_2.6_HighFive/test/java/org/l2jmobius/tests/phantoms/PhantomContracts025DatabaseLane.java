/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import org.l2jmobius.commons.database.DatabaseFactory;

/** Exact TASK025 clone admission. The shared TEST guard remains unchanged. */
public final class PhantomContracts025DatabaseLane
{
	private PhantomContracts025DatabaseLane() { }
	public static boolean enabled() { return System.getProperty("phantom.contract025.manifest") != null; }
	public static void initialize(Path module, Path config) throws Exception
	{
		final Path root = module.toRealPath();
		final Path manifest = Path.of(System.getProperty("phantom.contract025.manifest")).toRealPath();
		final Path lane = root.resolve(".phantom-local/contract025b").toRealPath();
		PhantomAssertions.assertEquals(lane.resolve("test/owned.properties"), manifest, "Exact owned TASK025 test manifest required.");
		PhantomAssertions.assertEquals(lane.resolve("test/Database.test.ini"), config.toRealPath(), "Exact owned TASK025 test config required.");
		PhantomAssertions.assertEquals(lane.resolve("runtime/game"), Path.of("").toRealPath(), "Owned TASK025 runtime working directory required.");
		final Properties ownership = new Properties(), settings = new Properties();
		try (var reader = Files.newBufferedReader(manifest)) { ownership.load(reader); }
		try (var reader = Files.newBufferedReader(config)) { settings.load(reader); }
		PhantomAssertions.assertEquals("TASK025_CONTRACT", ownership.getProperty("owner"), "Owned clone authority missing.");
		PhantomAssertions.assertEquals("l2jmobiush5_localplay_contract025b", ownership.getProperty("database"), "Owned test database mismatch.");
		final Path dump = lane.resolve("play-snapshot.sql");
		final var digest = MessageDigest.getInstance("SHA-256");
		try (var input = Files.newInputStream(dump)) { final byte[] buffer = new byte[65536]; int count; while ((count = input.read(buffer)) > 0) { digest.update(buffer, 0, count); } }
		PhantomAssertions.assertEquals(ownership.getProperty("exportSha256"), HexFormat.of().formatHex(digest.digest()), "Owned immutable export changed.");
		final String url = settings.getProperty("URL");
		PhantomAssertions.assertTrue(url != null && url.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5_localplay_contract025b\\?.*"), "Only exact TASK025 test clone may be written.");
		PhantomAssertions.assertEquals("4", settings.getProperty("MaximumDatabaseConnections"), "Bounded owned TEST pool required.");
		DatabaseFactory.initFromConfig(config.toString());
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT DATABASE()"))
		{
			PhantomAssertions.assertTrue(rows.next(), "Owned TEST catalog unavailable.");
			PhantomAssertions.assertEquals(ownership.getProperty("database"), rows.getString(1), "Connected TEST catalog differs.");
		}
	}
}

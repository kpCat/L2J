/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.commons.util;

import java.nio.file.Path;
import java.util.Properties;

/** Pure operator boundary checks, without starting or stopping a server. */
public final class LocalRuntimeShutdownTest
{
	public static void main(String[] args) throws Exception
	{
		final String schema = "l2jmobiush5_phantom_release_064a";
		final String jdbc = "jdbc:mysql://127.0.0.1:3308/" + schema + "?useSSL=false";
		if (!LocalRuntimeShutdown.validJdbc(jdbc, schema)) { throw new AssertionError("Own JDBC rejected."); }
		for (String suffix : new String[] {"&DBNAME=l2jmobiush5", "&dbname=l2jmobiush5", "&user=foreign", "&socketFactory=foreign", "&allowPublicKeyRetrieval=true&DBNAME=l2jmobiush5"})
		{
			if (LocalRuntimeShutdown.validJdbc(jdbc + suffix, schema)) { throw new AssertionError("JDBC override accepted: " + suffix); }
		}
		System.out.println("PASS runtime064.jdbc-override-rejected-before-connection");
		final Properties request = new Properties();
		request.setProperty("version", "1"); request.setProperty("owner", "TASK064_CAMPAIGN");
		request.setProperty("runtime", "a".repeat(64)); request.setProperty("role", "GameServer");
		request.setProperty("pid", "64"); request.setProperty("startTimeUtcTicks", "64000");
		request.setProperty("deadlineUtcMillis", "101000");
		if (!LocalRuntimeShutdown.validRequest(request, "a".repeat(64), "GameServer", 64, 64000, 100000)) { throw new AssertionError("Current own shutdown rejected."); }
		System.out.println("PASS runtime064.current-own-incarnation");
		for (String key : new String[] {"owner", "runtime", "role", "pid", "startTimeUtcTicks"})
		{
			final Properties wrong = new Properties(); wrong.putAll(request); wrong.setProperty(key, "FOREIGN");
			if (LocalRuntimeShutdown.validRequest(wrong, "a".repeat(64), "GameServer", 64, 64000, 100000)) { throw new AssertionError("Foreign or stale shutdown accepted: " + key); }
		}
		System.out.println("PASS runtime064.foreign-stale-identity");
		for (String stamp : new String[] {"99999", "221001", "invalid"})
		{
			request.setProperty("deadlineUtcMillis", stamp);
			if (LocalRuntimeShutdown.validRequest(request, "a".repeat(64), "GameServer", 64, 64000, 100000)) { throw new AssertionError("Expired/unbounded request accepted."); }
		}
		System.out.println("PASS runtime064.expiry-and-bound");
		System.setProperty("phantom.task065.control", "true");
		try
		{
			final Path module = Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
			final Path candidate = module.getRoot().resolve("PhantomWorld/Living-Presence-065-candidate");
			if (!LocalRuntimeShutdown.validTask065Root(candidate, module, false)
				|| LocalRuntimeShutdown.validTask065Root(candidate.getParent().resolve("World-Integrated-064-candidate/Living-Presence-065-candidate"), module, false)
				|| LocalRuntimeShutdown.validTask065Root(module.resolve("Living-Presence-065-candidate"), module, false)) { throw new AssertionError("Exact065 release root boundary failed."); }
			final String own = "l2jmobiush5_phantom_release_065a";
			if (!LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/" + own + "?useSSL=false", own)) { throw new AssertionError("Exact065 JDBC rejected."); }
			if (LocalRuntimeShutdown.validJdbc(jdbc, schema)) { throw new AssertionError("TASK065 inherited protected064 JDBC authority."); }
			request.setProperty("deadlineUtcMillis", "101000");
			if (LocalRuntimeShutdown.validRequest(request, "a".repeat(64), "GameServer", 64, 64000, 100000)) { throw new AssertionError("TASK065 inherited064 shutdown owner."); }
			request.setProperty("owner", "TASK065_LIVING_PRESENCE");
			if (!LocalRuntimeShutdown.validRequest(request, "a".repeat(64), "GameServer", 64, 64000, 100000)) { throw new AssertionError("Exact065 shutdown rejected."); }
			System.setProperty("phantom.task064.control", "true");
			try { LocalRuntimeShutdown.validateLaunch("GameServer"); throw new AssertionError("Mixed runtime authority accepted."); }
			catch (IllegalArgumentException expected) { }
			System.out.println("PASS runtime065.exact-jdbc-owner-and-mixed-authority");
		}
		finally { System.clearProperty("phantom.task064.control"); System.clearProperty("phantom.task065.control"); }
		System.setProperty("phantom.task066.control", "true");
		try
		{
			final String own = "l2jmobiush5_phantom_release_066a";
			final Path module = Path.of(System.getProperty("phantom.module.root", ".")).toAbsolutePath().normalize();
			final Path candidate = module.getRoot().resolve("PhantomWorld/Farm-Economy-066-candidate");
			if (!LocalRuntimeShutdown.validTask066Root(candidate, module, false)
				|| !LocalRuntimeShutdown.validTask066Root(module.getParent().resolve(".phantom-local/task066/runtime"), module, true)
				|| LocalRuntimeShutdown.validTask066Root(candidate.getParent().resolve("Living-Presence-065-candidate/Farm-Economy-066-candidate"), module, false)
				|| LocalRuntimeShutdown.validTask066Root(module.resolve("Farm-Economy-066-candidate"), module, false)) { throw new AssertionError("Exact066 root boundary failed."); }
			if (!LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/" + own + "?useSSL=false", own)) { throw new AssertionError("Exact066 JDBC rejected."); }
			for (String historical : new String[] {schema, "l2jmobiush5_phantom_release_065a", "l2jmobiush5_phantom_test_065"})
			{
				if (LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/" + historical + "?useSSL=false", historical)) { throw new AssertionError("TASK066 inherited historical JDBC authority."); }
			}
			if (LocalRuntimeShutdown.validRequest(request, "a".repeat(64), "GameServer", 64, 64000, 100000)) { throw new AssertionError("TASK066 inherited065 shutdown owner."); }
			request.setProperty("owner", "TASK066_FARM_ECONOMY");
			if (!LocalRuntimeShutdown.validRequest(request, "a".repeat(64), "GameServer", 64, 64000, 100000)) { throw new AssertionError("Exact066 shutdown rejected."); }
			System.setProperty("phantom.task065.control", "true");
			try { LocalRuntimeShutdown.validateLaunch("GameServer"); throw new AssertionError("Mixed066/065 authority accepted."); }
			catch (IllegalArgumentException expected) { }
			System.out.println("PASS runtime066.exact-jdbc-owner-and-mixed-authority");
		}
		finally { System.clearProperty("phantom.task065.control"); System.clearProperty("phantom.task066.control"); }
		System.setProperty("phantom.task066r2.control", "true");
		try
		{
			final String own = "l2jmobiush5_phantom_release_066r2";
			if (!LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/" + own + "?useSSL=false", own)) { throw new AssertionError("Exact066R2 JDBC rejected."); }
			for (String historical : new String[] {"l2jmobiush5_phantom_release_066a", "l2jmobiush5_phantom_test_066", schema})
			{
				if (LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/" + historical + "?useSSL=false", historical)) { throw new AssertionError("R2 inherited protected schema authority."); }
			}
			request.setProperty("owner", "TASK066R2_FARM_ECONOMY");
			if (!LocalRuntimeShutdown.validRequest(request, "a".repeat(64), "GameServer", 64, 64000, 100000)) { throw new AssertionError("Exact R2 STOP rejected."); }
			System.setProperty("phantom.task066.control", "true");
			try { LocalRuntimeShutdown.validateLaunch("GameServer"); throw new AssertionError("Mixed R2/066 authority accepted."); }
			catch (IllegalArgumentException expected) { }
			System.out.println("PASS runtime066r2.exact-jdbc-owner-and-mixed-authority");
		}
		finally { System.clearProperty("phantom.task066.control"); System.clearProperty("phantom.task066r2.control"); }
	}
}

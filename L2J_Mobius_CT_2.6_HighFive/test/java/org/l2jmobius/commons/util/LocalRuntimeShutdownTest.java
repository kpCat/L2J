/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.commons.util;

import java.util.Properties;

/** Pure operator boundary checks, without starting or stopping a server. */
public final class LocalRuntimeShutdownTest
{
	public static void main(String[] args)
	{
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
	}
}

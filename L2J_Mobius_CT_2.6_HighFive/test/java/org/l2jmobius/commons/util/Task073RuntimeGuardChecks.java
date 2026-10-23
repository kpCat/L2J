/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.commons.util;

import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** Own073 must reject unbound launch before DB/listeners and every mixed authority. */
public final class Task073RuntimeGuardChecks
{
	public static void main(String[] args) throws Exception
	{
		LocalRuntimeShutdown.validateLaunch("GameServer");
		System.setProperty("phantom.task073.control", "true");
		try
		{
			PhantomAssertions.assertThrows(Exception.class, () -> LocalRuntimeShutdown.validateLaunch("GameServer"), "Own073 control silently accepted an unbound launch.");
			for (String schema : new String[] {"l2jmobiush5_phantom_test_073", "l2jmobiush5_phantom_release_073"})
			{
				PhantomAssertions.assertTrue(LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/" + schema + "?useSSL=false", schema), "Own073 exact JDBC binding refused.");
				PhantomAssertions.assertFalse(LocalRuntimeShutdown.validJdbc("jdbc:mysql://localhost:3308/" + schema + "?useSSL=false", schema), "Own073 accepted a host alias.");
			}
			PhantomAssertions.assertFalse(LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_release_068?useSSL=false", "l2jmobiush5_phantom_release_068"), "Own073 accepted protected068.");
			System.setProperty("phantom.task068.control", "true");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> LocalRuntimeShutdown.validateLaunch("GameServer"), "Mixed068/073 authority accepted.");
			System.out.println("OWN073_RUNTIME_GUARD_PASS; DB_WRITES=0; PROCESS_ACTIONS=0");
		}
		finally { System.clearProperty("phantom.task073.control"); System.clearProperty("phantom.task068.control"); }
	}
}

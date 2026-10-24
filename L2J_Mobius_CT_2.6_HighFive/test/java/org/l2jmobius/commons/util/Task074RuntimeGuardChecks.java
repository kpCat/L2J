/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.commons.util;

import org.l2jmobius.tests.phantoms.PhantomAssertions;

public final class Task074RuntimeGuardChecks
{
	public static void main(String[] args) throws Exception
	{
		System.setProperty("phantom.task074.control", "true");
		try
		{
			PhantomAssertions.assertThrows(Exception.class, () -> LocalRuntimeShutdown.validateLaunch("GameServer"), "OWN074 control accepted an unbound launch.");
			for (String schema : new String[] {"l2jmobiush5_phantom_test_074", "l2jmobiush5_phantom_release_074"})
			{
				PhantomAssertions.assertTrue(LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/" + schema + "?useSSL=false", schema), "OWN074 exact JDBC binding refused.");
				PhantomAssertions.assertFalse(LocalRuntimeShutdown.validJdbc("jdbc:mysql://localhost:3308/" + schema + "?useSSL=false", schema), "OWN074 host alias admitted.");
			}
			PhantomAssertions.assertFalse(LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test_073?useSSL=false", "l2jmobiush5_phantom_test_073"), "OWN074 historical schema admitted.");
			System.setProperty("phantom.task073.control", "true");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> LocalRuntimeShutdown.validateLaunch("GameServer"), "OWN074 mixed authority admitted.");
			System.out.println("OWN074_RUNTIME_GUARD_PASS; DB_WRITES=0; PROCESS_ACTIONS=0");
		}
		finally { System.clearProperty("phantom.task074.control"); System.clearProperty("phantom.task073.control"); }
	}
}

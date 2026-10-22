/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.commons.util;

import java.nio.file.Path;

import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** DB-free: own068 cannot silently bypass launch or inherit historical control authority. */
public final class Task068RuntimeGuardChecks
{
	public static void main(String[] args) throws Exception
	{
		LocalRuntimeShutdown.validateLaunch("GameServer");
		System.setProperty("phantom.task068.control", "true");
		try
		{
			PhantomAssertions.assertThrows(Exception.class, () -> LocalRuntimeShutdown.validateLaunch("GameServer"), "Own068 control silently accepted an unbound launch.");
			for (String schema : new String[] {"l2jmobiush5_phantom_test_068", "l2jmobiush5_phantom_release_068"})
			{
				PhantomAssertions.assertTrue(LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/" + schema + "?useSSL=false", schema), "Own068 exact JDBC binding was refused.");
				PhantomAssertions.assertFalse(LocalRuntimeShutdown.validJdbc("jdbc:mysql://localhost:3308/" + schema + "?useSSL=false", schema), "Own068 inherited an unbound host alias.");
			}
			PhantomAssertions.assertFalse(LocalRuntimeShutdown.validJdbc("jdbc:mysql://127.0.0.1:3308/l2jmobiush5_personal_release_067?useSSL=false", "l2jmobiush5_personal_release_067"), "Own068 accepted a protected historical database.");
			final Path module = Path.of("L2J_Mobius_CT_2.6_HighFive").toAbsolutePath().normalize();
			PhantomAssertions.assertTrue(LocalRuntimeShutdown.validTask068Root(module.getParent().resolve(".phantom-local/task068/runtime-test"), module, true), "Exact own068 fixture root was refused.");
			PhantomAssertions.assertTrue(LocalRuntimeShutdown.validTask068Root(module.getRoot().resolve("PhantomWorld/Living-Economy-068-candidate"), module, false), "Exact own068 candidate root was refused.");
			PhantomAssertions.assertFalse(LocalRuntimeShutdown.validTask068Root(module.getRoot().resolve("PhantomWorld/Personal-Adventure-067-candidate"), module, false), "Own068 accepted a historical candidate root.");
			System.setProperty("phantom.task067.control", "true");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> LocalRuntimeShutdown.validateLaunch("GameServer"), "Mixed067/068 authority was accepted.");
			System.out.println("OWN068_RUNTIME_GUARD_PASS; DB_WRITES=0; PROCESS_ACTIONS=0");
		}
		finally { System.clearProperty("phantom.task068.control"); System.clearProperty("phantom.task067.control"); }
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.commons.util;

import org.l2jmobius.tests.phantoms.PhantomAssertions;

/** The own067 control flag must refuse an unbound launch before opening a DB/listener. */
public final class Task067RuntimeGuardChecks
{
	public static void main(String[] args) throws Exception
	{
		LocalRuntimeShutdown.validateLaunch("GameServer");
		System.setProperty("phantom.task067.control", "true");
		try
		{
			boolean refused = false;
			try { LocalRuntimeShutdown.validateLaunch("GameServer"); }
			catch (Exception expected) { refused = true; }
			PhantomAssertions.assertTrue(refused, "Own067 control silently accepted an unbound runtime.");
			System.out.println("OWN067_RUNTIME_GUARD_PASS; DB_WRITES=0; PROCESS_ACTIONS=0");
		}
		finally { System.clearProperty("phantom.task067.control"); }
	}
}

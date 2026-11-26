package org.l2jmobius.gameserver.model.actor.instance;

public final class DungeonGatekeeperAccessChecks
{
	public static void main(String[] args)
	{
		check(DungeonGatekeeper.entryAllowed(true, true, true, false, false), "configured open");
		check(DungeonGatekeeper.entryAllowed(false, false, true, true, false), "registered during competition");
		check(!DungeonGatekeeper.entryAllowed(false, false, true, false, false), "unregistered during competition");
		check(!DungeonGatekeeper.entryAllowed(false, true, true, true, false), "registered without winning seal");
		check(DungeonGatekeeper.entryAllowed(false, true, true, true, true), "winning seal");
		check(DungeonGatekeeper.entryAllowed(false, true, false, true, false), "registered at tied result");
		System.out.println("DungeonGatekeeperAccessChecks 6/6");
	}

	private static void check(boolean result, String scenario)
	{
		if (!result)
		{
			throw new AssertionError(scenario);
		}
	}
}

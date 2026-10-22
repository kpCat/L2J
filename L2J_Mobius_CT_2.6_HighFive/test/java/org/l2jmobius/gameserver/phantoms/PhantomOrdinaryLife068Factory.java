/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;

/** Same package access to the existing production-composed test factory; no alternate composition. */
public final class PhantomOrdinaryLife068Factory
{
	private PhantomOrdinaryLife068Factory() { }
	public static boolean start(PhantomPlayersConfig.Settings settings) { return PhantomSystem.startConfiguredForTesting(settings); }
}

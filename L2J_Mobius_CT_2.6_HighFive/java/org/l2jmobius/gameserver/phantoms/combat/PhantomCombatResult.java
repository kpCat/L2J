/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

public enum PhantomCombatResult
{
	ACTIVE(false),
	VICTORY(true),
	VICTORY_LOOTED(true),
	VICTORY_LOOT_PARTIAL(true),
	VICTORY_LOOT_BLOCKED(true),
	PLAYER_DEAD(true),
	LOW_HP_STOPPED(true),
	TIMEOUT(true),
	TARGET_LOST(true),
	UNSUPPORTED_LOADOUT(true),
	CANCELLED(true),
	BACKEND_FAILURE(true),
	REJECTED(true);

	private final boolean _terminal;

	PhantomCombatResult(boolean terminal)
	{
		_terminal = terminal;
	}

	public boolean terminal()
	{
		return _terminal;
	}

	public boolean victory()
	{
		return (this == VICTORY) || (this == VICTORY_LOOTED) || (this == VICTORY_LOOT_PARTIAL) || (this == VICTORY_LOOT_BLOCKED);
	}
}

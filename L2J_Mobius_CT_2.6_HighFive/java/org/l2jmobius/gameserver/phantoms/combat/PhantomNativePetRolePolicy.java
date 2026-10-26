/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.combat;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.instance.Pet;
import org.l2jmobius.gameserver.model.actor.instance.Servitor;

/** Phantom-only role admission. Native Pet AI retains support, follow and feeding ownership. */
public final class PhantomNativePetRolePolicy
{
	public static boolean combatPet(int npcId)
	{
		return switch (npcId)
		{
			case 12077, 16025, 16030, 16037, 16041, 16042 -> true;
			default -> false;
		};
	}

	public static boolean allowAttack(Player owner, Summon summon)
	{
		return summon != null && summon.getOwner() == owner && owner.getSummon() == summon
			&& (summon instanceof Servitor || summon instanceof Pet && combatPet(summon.getId()));
	}

	private PhantomNativePetRolePolicy() { }
}

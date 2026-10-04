package org.l2jmobius.gameserver.model.events;

import java.util.List;

import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Semantics;
import org.l2jmobius.gameserver.model.events.holders.IBaseEvent;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureTeleported;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureZoneEnter;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureZoneExit;
import org.l2jmobius.gameserver.model.events.holders.actor.npc.attackable.OnAttackableKill;
import org.l2jmobius.gameserver.model.events.holders.actor.playable.OnPlayableExpChanged;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLevelChanged;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemAdd;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemPickup;
import org.l2jmobius.gameserver.model.events.holders.actor.player.inventory.OnPlayerItemTransfer;

/** Explicit participants supplement inherited work; an NPC listener container is never a Player owner. */
final class NativeEventWork
{
	private NativeEventWork() { }
	private static List<? extends WorldObject> participants(IBaseEvent event)
	{
		if (event instanceof OnAttackableKill kill) { return one(kill.getAttacker()); }
		if (event instanceof OnCreatureDeath death)
		{
			final var values = new java.util.ArrayList<WorldObject>(2);
			if (death.getAttacker() != null) { values.add(death.getAttacker()); }
			if (death.getTarget() != null) { values.add(death.getTarget()); }
			return values;
		}
		if (event instanceof OnCreatureZoneEnter zone) { return one(zone.getCreature()); }
		if (event instanceof OnCreatureZoneExit zone) { return one(zone.getCreature()); }
		if (event instanceof OnCreatureTeleported teleport) { return one(teleport.getCreature()); }
		if (event instanceof OnPlayerLogin login) { return one(login.getPlayer()); }
		if (event instanceof OnPlayerLogout logout) { return one(logout.getPlayer()); }
		if (event instanceof OnPlayerItemAdd item) { return one(item.getPlayer()); }
		if (event instanceof OnPlayerItemPickup item) { return one(item.getPlayer()); }
		if (event instanceof OnPlayerItemTransfer item) { return one(item.getPlayer()); }
		if (event instanceof OnPlayableExpChanged exp) { return one(exp.getActiveChar()); }
		if (event instanceof OnPlayerLevelChanged level) { return one(level.getPlayer()); }
		return PlayerNativeWork.inheritedPlayers();
	}
	private static List<WorldObject> one(WorldObject value) { return value == null ? List.of() : List.of(value); }
	static void schedule(IBaseEvent event, Runnable traversal, long delay)
	{
		PlayerNativeWork.schedule(null, participants(event), "EVENT:" + event.getType(), Semantics.EARNED, traversal, delay);
	}
	static void execute(IBaseEvent event, Runnable traversal)
	{
		PlayerNativeWork.execute(null, participants(event), "EVENT:" + event.getType(), Semantics.EARNED, traversal);
	}
}

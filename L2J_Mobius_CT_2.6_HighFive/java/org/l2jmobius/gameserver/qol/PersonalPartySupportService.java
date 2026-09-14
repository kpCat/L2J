/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.zone.ZoneId;

/**
 * Bounded personal support actions for the actor and current members of the actor's own party.
 */
public final class PersonalPartySupportService
{
	public enum Action
	{
		HEAL,
		RESURRECT,
		REPUTATION
	}

	public enum Status
	{
		SUCCESS,
		FEATURE_DISABLED,
		ACTOR_UNAVAILABLE,
		ACTOR_RESTRICTED,
		TARGET_UNAVAILABLE,
		TARGET_NOT_IN_OWN_PARTY,
		TARGET_RESTRICTED,
		TARGET_STATE_REJECTED,
		NO_CHANGE
	}

	public record Result(Status status, int targetObjectId, String targetName)
	{
	}

	public record TargetSnapshot(int objectId, String name, boolean dead, int karma, boolean restricted)
	{
	}

	private PersonalPartySupportService()
	{
	}

	public boolean isEnabled(Player actor)
	{
		return PersonalCharacterQoLService.getInstance().isPartySupportEnabled(actor);
	}

	/**
	 * Builds display-only state. Every action still resolves and validates the target again.
	 * @param actor requesting player
	 * @return current online self/party targets
	 */
	public List<TargetSnapshot> listTargets(Player actor)
	{
		if (!isCurrentEligibleActor(actor))
		{
			return List.of();
		}

		final List<TargetSnapshot> result = new ArrayList<>();
		addSnapshot(result, actor, actor);
		final Party party = actor.getParty();
		if ((party != null) && party.containsPlayer(actor))
		{
			for (Player member : party.getMembers())
			{
				if ((member != null) && (member != actor))
				{
					final Player currentMember = World.getInstance().getPlayer(member.getObjectId());
					if ((currentMember == member) && isOwnPartyTarget(actor, currentMember))
					{
						addSnapshot(result, actor, currentMember);
					}
				}
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Executes one bounded action after resolving live objects and revalidating authority/membership.
	 * @param action requested action
	 * @param actor requesting player
	 * @param targetObjectId target object identifier supplied by the board
	 * @return deterministic result
	 */
	public Result execute(Action action, Player actor, int targetObjectId)
	{
		if ((action == null) || !isEnabled(actor))
		{
			return result(Status.FEATURE_DISABLED, targetObjectId, null);
		}
		if (!isCurrentEligibleActor(actor))
		{
			return result(Status.ACTOR_UNAVAILABLE, targetObjectId, null);
		}
		if (actor.isAlikeDead() || isRestrictedState(actor))
		{
			return result(Status.ACTOR_RESTRICTED, targetObjectId, actor);
		}

		final Player target = World.getInstance().getPlayer(targetObjectId);
		if (target == null)
		{
			return result(Status.TARGET_UNAVAILABLE, targetObjectId, null);
		}
		if (!isOwnPartyTarget(actor, target))
		{
			return result(Status.TARGET_NOT_IN_OWN_PARTY, targetObjectId, target);
		}
		if (isRestrictedState(target))
		{
			return result(Status.TARGET_RESTRICTED, targetObjectId, target);
		}

		return switch (action)
		{
			case HEAL -> heal(target);
			case RESURRECT -> resurrect(target);
			case REPUTATION -> clearReputation(target);
		};
	}

	private static Result heal(Player target)
	{
		if (target.isAlikeDead())
		{
			return result(Status.TARGET_STATE_REJECTED, target.getObjectId(), target);
		}
		target.fullRestore();
		return result(Status.SUCCESS, target.getObjectId(), target);
	}

	private static Result resurrect(Player target)
	{
		if (!target.isDead())
		{
			return result(Status.TARGET_STATE_REJECTED, target.getObjectId(), target);
		}
		if (target.isResurrectionBlocked())
		{
			return result(Status.TARGET_RESTRICTED, target.getObjectId(), target);
		}
		target.doRevive();
		return result(target.isDead() ? Status.NO_CHANGE : Status.SUCCESS, target.getObjectId(), target);
	}

	private static Result clearReputation(Player target)
	{
		if (target.getKarma() <= 0)
		{
			return result(Status.NO_CHANGE, target.getObjectId(), target);
		}
		target.setKarma(0);
		return result(Status.SUCCESS, target.getObjectId(), target);
	}

	private static boolean isCurrentEligibleActor(Player actor)
	{
		return (actor != null) && PersonalCharacterQoLService.getInstance().isPartySupportEnabled(actor) && (World.getInstance().getPlayer(actor.getObjectId()) == actor);
	}

	private static boolean isOwnPartyTarget(Player actor, Player target)
	{
		if (actor == target)
		{
			return true;
		}
		final Party party = actor.getParty();
		return (party != null) && (target.getParty() == party) && party.containsPlayer(actor) && party.containsPlayer(target);
	}

	private static boolean isRestrictedState(Player player)
	{
		final Party party = player.getParty();
		return player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.isInCombat() || player.isInDuel() || player.isInOlympiadMode() || player.isInsideZone(ZoneId.SIEGE) || player.isInsideZone(ZoneId.PVP) || (player.getPvpFlag() > 0) || player.isOnEvent() || player.isInStoreMode() || player.isTeleporting() || player.inObserverMode() || player.isInVehicle() || player.isJailed() || player.isCursedWeaponEquipped() || (player.getInstanceId() != 0) || ((party != null) && party.isInDimensionalRift());
	}

	private static void addSnapshot(List<TargetSnapshot> result, Player actor, Player target)
	{
		if (isOwnPartyTarget(actor, target))
		{
			result.add(new TargetSnapshot(target.getObjectId(), target.getName(), target.isDead(), target.getKarma(), isRestrictedState(target)));
		}
	}

	private static Result result(Status status, int targetObjectId, Player target)
	{
		return new Result(status, targetObjectId, target == null ? "" : target.getName());
	}

	public static PersonalPartySupportService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalPartySupportService INSTANCE = new PersonalPartySupportService();
	}
}

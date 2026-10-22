/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.qol;

import java.util.List;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;

/** Admission roster only; native scripts retain all content, fee and lifecycle rules. */
public final class PersonalInstanceAdmissionService
{
	private PersonalInstanceAdmissionService() { }

	public static EntryGroup snapshot(Player leader)
	{
		if (leader == null) { return null; }
		final Party party = leader.getParty();
		if (party == null)
		{
			return PersonalCharacterQoLService.getInstance().isSoloInstanceEntryEnabled(leader) ? new EntryGroup(null, List.of(leader)) : null;
		}
		return new EntryGroup(party, party.getMembers());
	}

	public record EntryGroup(Party party, List<Player> members)
	{
		public EntryGroup { members = List.copyOf(members); }

		public boolean isCurrent(Player leader)
		{
			if (party == null)
			{
				return members.size() == 1 && members.get(0) == leader && leader.getParty() == null && PersonalCharacterQoLService.getInstance().isSoloInstanceEntryEnabled(leader);
			}
			return leader.getParty() == party && party.getLeader() == leader && party.getMemberCount() == members.size()
				&& members.contains(leader) && members.stream().allMatch(member -> member.getParty() == party && party.getMembers().contains(member));
		}
	}
}

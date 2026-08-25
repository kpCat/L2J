/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius.gameserver.model.events.holders.actor.player.clan;

import java.util.Objects;

import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanMember;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.IBaseEvent;

/**
 * @author UnAfraid
 */
public class OnPlayerClanLeft implements IBaseEvent
{
	public enum DepartureKind
	{
		UNKNOWN,
		VOLUNTARY,
		EXPELLED,
		CLAN_DISSOLVED
	}

	private final ClanMember _clanMember;
	private final Clan _clan;
	private final DepartureKind _departureKind;
	private final int _initiatorObjectId;
	private final long _happenedEpochMinute;
	
	public OnPlayerClanLeft(ClanMember clanMember, Clan clan)
	{
		this(clanMember, clan, DepartureKind.UNKNOWN, 0);
	}

	public OnPlayerClanLeft(ClanMember clanMember, Clan clan, DepartureKind departureKind, int initiatorObjectId)
	{
		this(clanMember, clan, departureKind, initiatorObjectId, System.currentTimeMillis() / 60000L);
	}

	public OnPlayerClanLeft(ClanMember clanMember, Clan clan, DepartureKind departureKind, int initiatorObjectId, long happenedEpochMinute)
	{
		_clanMember = Objects.requireNonNull(clanMember);
		_clan = Objects.requireNonNull(clan);
		_departureKind = Objects.requireNonNull(departureKind);
		if ((initiatorObjectId < 0) || (happenedEpochMinute < 0))
		{
			throw new IllegalArgumentException("Invalid clan departure metadata.");
		}
		_initiatorObjectId = initiatorObjectId;
		_happenedEpochMinute = happenedEpochMinute;
	}
	
	public ClanMember getClanMember()
	{
		return _clanMember;
	}
	
	public Clan getClan()
	{
		return _clan;
	}

	public DepartureKind getDepartureKind()
	{
		return _departureKind;
	}

	public int getInitiatorObjectId()
	{
		return _initiatorObjectId;
	}

	public long getHappenedEpochMinute()
	{
		return _happenedEpochMinute;
	}
	
	@Override
	public EventType getType()
	{
		return EventType.ON_PLAYER_CLAN_LEFT;
	}
}

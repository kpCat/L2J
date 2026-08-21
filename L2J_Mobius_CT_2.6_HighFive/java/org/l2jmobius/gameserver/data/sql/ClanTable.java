/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.data.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.communitybbs.Manager.ForumsBBSManager;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.managers.CHSiegeManager;
import org.l2jmobius.gameserver.managers.ClanHallAuctionManager;
import org.l2jmobius.gameserver.managers.FortManager;
import org.l2jmobius.gameserver.managers.FortSiegeManager;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.managers.SiegeManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.model.clan.ClanMember;
import org.l2jmobius.gameserver.model.clan.ClanPrivileges;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanCreate;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanDestroy;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.residences.ClanHallAuction;
import org.l2jmobius.gameserver.model.siege.Fort;
import org.l2jmobius.gameserver.model.siege.FortSiege;
import org.l2jmobius.gameserver.model.siege.Siege;
import org.l2jmobius.gameserver.model.siege.clanhalls.SiegableHall;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowInfoUpdate;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListAll;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * This class loads the clan related data.
 */
public class ClanTable
{
	private static final Logger LOGGER = Logger.getLogger(ClanTable.class.getName());
	private final Map<Integer, Clan> _clans = new ConcurrentHashMap<>();
	
	protected ClanTable()
	{
		// forums has to be loaded before clan data, because of last forum id used should have also memo included
		if (GeneralConfig.ENABLE_COMMUNITY_BOARD)
		{
			ForumsBBSManager.getInstance().initRoot();
		}
		
		// Get all clan ids.
		final List<Integer> cids = new ArrayList<>();
		try (Connection con = DatabaseFactory.getConnection();
			Statement s = con.createStatement();
			ResultSet rs = s.executeQuery("SELECT clan_id FROM clan_data"))
		{
			while (rs.next())
			{
				cids.add(rs.getInt("clan_id"));
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error restoring ClanTable.", e);
		}
		
		// Create clans.
		for (int cid : cids)
		{
			final Clan clan = new Clan(cid);
			_clans.put(cid, clan);
			if (clan.getDissolvingExpiryTime() != 0)
			{
				scheduleRemoveClan(clan.getId());
			}
		}
		
		LOGGER.info(getClass().getSimpleName() + ": Restored " + cids.size() + " clans from the database.");
		allianceCheck();
		if (ClanWarService.getInstance().restoreWars(this).status() != ClanWarService.Status.SUCCESS)
		{
			LOGGER.severe(getClass().getSimpleName() + ": Clan wars were not restored from durable state.");
		}
		
		ThreadPool.scheduleAtFixedRate(this::updateClanRanks, 1000, 1200000); // 20 minutes.
	}
	
	/**
	 * Gets the clans.
	 * @return the clans
	 */
	public Collection<Clan> getClans()
	{
		return _clans.values();
	}
	
	/**
	 * Gets the clan count.
	 * @return the clan count
	 */
	public int getClanCount()
	{
		return _clans.size();
	}
	
	/**
	 * @param clanId
	 * @return
	 */
	public Clan getClan(int clanId)
	{
		return _clans.get(clanId);
	}
	
	public Clan getClanByName(String clanName)
	{
		for (Clan clan : _clans.values())
		{
			if (clan.getName().equalsIgnoreCase(clanName))
			{
				return clan;
			}
		}
		
		return null;
	}
	
	/**
	 * Creates a new clan and store clan info to database
	 * @param player
	 * @param clanName
	 * @return NULL if clan with same name already exists
	 */
	public Clan createClan(Player player, String clanName)
	{
		if (null == player)
		{
			return null;
		}
		
		if (10 > player.getLevel())
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_MEET_THE_CRITERIA_IN_ORDER_TO_CREATE_A_CLAN);
			return null;
		}
		
		if (0 != player.getClanId())
		{
			player.sendPacket(SystemMessageId.YOU_HAVE_FAILED_TO_CREATE_A_CLAN);
			return null;
		}
		
		if (System.currentTimeMillis() < player.getClanCreateExpiryTime())
		{
			player.sendPacket(SystemMessageId.YOU_MUST_WAIT_10_DAYS_BEFORE_CREATING_A_NEW_CLAN);
			return null;
		}
		
		if (!StringUtil.isAlphaNumeric(clanName) || (2 > clanName.length()))
		{
			player.sendPacket(SystemMessageId.CLAN_NAME_IS_INVALID);
			return null;
		}
		
		if (16 < clanName.length())
		{
			player.sendPacket(SystemMessageId.CLAN_NAME_S_LENGTH_IS_INCORRECT);
			return null;
		}
		
		if (null != getClanByName(clanName))
		{
			// clan name is already taken
			final SystemMessage sm = new SystemMessage(SystemMessageId.S1_ALREADY_EXISTS);
			sm.addString(clanName);
			player.sendPacket(sm);
			return null;
		}
		
		final Clan clan = new Clan(IdManager.getInstance().getNextId(), clanName);
		final ClanMember leader = new ClanMember(clan, player);
		clan.setLeader(leader);
		leader.setPlayer(player);
		clan.store();
		player.setClan(clan);
		player.setPledgeClass(ClanMember.calculatePledgeClass(player));
		
		final ClanPrivileges privileges = new ClanPrivileges();
		privileges.enableAll();
		player.setClanPrivileges(privileges);
		
		_clans.put(clan.getId(), clan);
		
		// should be update packet only
		player.sendPacket(new PledgeShowInfoUpdate(clan));
		player.sendPacket(new PledgeShowMemberListAll(clan, player));
		player.updateUserInfo();
		player.sendPacket(new PledgeShowMemberListUpdate(player));
		player.sendPacket(SystemMessageId.YOUR_CLAN_HAS_BEEN_CREATED);
		
		// Notify to scripts
		if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_CREATE))
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanCreate(player, clan));
		}
		
		return clan;
	}
	
	public synchronized void destroyClan(int clanId)
	{
		final Clan clan = getClan(clanId);
		if (clan == null)
		{
			return;
		}
		
		final ClanWarService.Result warRemoval = ClanWarService.getInstance().removeAllForClan(clanId);
		if (warRemoval.status() != ClanWarService.Status.SUCCESS)
		{
			LOGGER.severe(getClass().getSimpleName() + ": Clan destruction aborted because clan wars could not be removed durably for clan " + clanId + ".");
			return;
		}

		clan.broadcastToOnlineMembers(new SystemMessage(SystemMessageId.CLAN_HAS_DISPERSED));
		final int castleId = clan.getCastleId();
		if (castleId == 0)
		{
			for (Siege siege : SiegeManager.getInstance().getSieges())
			{
				siege.removeSiegeClan(clan);
			}
		}
		
		final int fortId = clan.getFortId();
		if (fortId == 0)
		{
			for (FortSiege siege : FortSiegeManager.getInstance().getSieges())
			{
				siege.removeAttacker(clan);
			}
		}
		
		final int hallId = clan.getHideoutId();
		if (hallId == 0)
		{
			for (SiegableHall hall : CHSiegeManager.getInstance().getConquerableHalls().values())
			{
				hall.removeAttacker(clan);
			}
		}
		
		final ClanHallAuction auction = ClanHallAuctionManager.getInstance().getAuction(clan.getAuctionBiddedAt());
		if (auction != null)
		{
			auction.cancelBid(clan.getId());
		}
		
		final ClanMember leaderMember = clan.getLeader();
		clan.getWarehouse().destroyAllItems(ItemProcessType.DESTROY, leaderMember == null ? null : clan.getLeader().getPlayer(), null);
		
		for (ClanMember member : clan.getMembers())
		{
			clan.removeClanMember(member.getObjectId(), 0);
		}
		
		_clans.remove(clanId);
		IdManager.getInstance().releaseId(clanId);
		
		try (Connection con = DatabaseFactory.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM clan_data WHERE clan_id=?"))
			{
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM clan_privs WHERE clan_id=?"))
			{
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM clan_skills WHERE clan_id=?"))
			{
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM clan_subpledges WHERE clan_id=?"))
			{
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			try (PreparedStatement ps = con.prepareStatement("DELETE FROM clan_notices WHERE clan_id=?"))
			{
				ps.setInt(1, clanId);
				ps.execute();
			}
			
			if (castleId != 0)
			{
				try (PreparedStatement ps = con.prepareStatement("UPDATE castle SET taxPercent = 0 WHERE id = ?"))
				{
					ps.setInt(1, castleId);
					ps.execute();
				}
			}
			
			if (fortId != 0)
			{
				final Fort fort = FortManager.getInstance().getFortById(fortId);
				if ((fort != null) && (clan == fort.getOwnerClan()))
				{
					fort.removeOwner(true);
				}
			}
			
			if (hallId != 0)
			{
				final SiegableHall hall = CHSiegeManager.getInstance().getSiegableHall(hallId);
				if ((hall != null) && (hall.getOwnerId() == clanId))
				{
					hall.free();
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, getClass().getSimpleName() + ": Error removing clan from DB.", e);
		}
		
		// Notify to scripts
		if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_DESTROY))
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanDestroy(leaderMember, clan));
		}
	}
	
	public void scheduleRemoveClan(int clanId)
	{
		ThreadPool.schedule(() ->
		{
			if (getClan(clanId) == null)
			{
				return;
			}
			
			if (getClan(clanId).getDissolvingExpiryTime() != 0)
			{
				destroyClan(clanId);
			}
		}, Math.max(getClan(clanId).getDissolvingExpiryTime() - System.currentTimeMillis(), 300000));
	}
	
	public boolean isAllyExists(String allyName)
	{
		for (Clan clan : _clans.values())
		{
			if ((clan.getAllyName() != null) && clan.getAllyName().equalsIgnoreCase(allyName))
			{
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Check for nonexistent alliances
	 */
	private void allianceCheck()
	{
		for (Clan clan : _clans.values())
		{
			final ClanAllianceService.Result result = ClanAllianceService.getInstance().repairOrphaned(this, clan);
			if (result.status() == ClanAllianceService.Status.PERSISTENCE_FAILURE)
			{
				LOGGER.severe(getClass().getSimpleName() + ": Failed to repair orphaned alliance for clan " + clan.getId() + ".");
			}
		}
	}
	
	public List<Clan> getClanAllies(int allianceId)
	{
		final List<Clan> clanAllies = new ArrayList<>();
		if (allianceId != 0)
		{
			for (Clan clan : _clans.values())
			{
				if ((clan != null) && (clan.getAllyId() == allianceId))
				{
					clanAllies.add(clan);
				}
			}
		}
		
		return clanAllies;
	}
	
	public void shutdown()
	{
		for (Clan clan : _clans.values())
		{
			clan.updateClanInDB();
		}
	}
	
	private void updateClanRanks()
	{
		for (Clan clan : _clans.values())
		{
			clan.setRank(getClanRank(clan));
		}
	}
	
	public int getClanRank(Clan clan)
	{
		if (clan.getLevel() < 3)
		{
			return 0;
		}
		
		int rank = 1;
		for (Clan c : _clans.values())
		{
			if ((clan != c) && ((clan.getLevel() < c.getLevel()) || ((clan.getLevel() == c.getLevel()) && (clan.getReputationScore() <= c.getReputationScore()))))
			{
				rank++;
			}
		}
		
		return rank;
	}
	
	public static ClanTable getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final ClanTable INSTANCE = new ClanTable();
	}
}

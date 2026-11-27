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
package org.l2jmobius.gameserver.model.clan;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.communitybbs.BB.Forum;
import org.l2jmobius.gameserver.communitybbs.Manager.ForumsBBSManager;
import org.l2jmobius.gameserver.config.FeatureConfig;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.CharInfoTable;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.sql.CrestTable;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.managers.FortManager;
import org.l2jmobius.gameserver.managers.SiegeManager;
import org.l2jmobius.gameserver.managers.TerritoryWarManager;
import org.l2jmobius.gameserver.managers.TerritoryWarManager.Territory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.holders.player.BlockList;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanJoin;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLeaderChange;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLeft;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLeft.DepartureKind;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLvlUp;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.itemcontainer.ClanWarehouse;
import org.l2jmobius.gameserver.model.itemcontainer.ItemContainer;
import org.l2jmobius.gameserver.model.siege.Castle;
import org.l2jmobius.gameserver.model.siege.Fort;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.network.serverpackets.ExSubPledgeSkillAdd;
import org.l2jmobius.gameserver.network.serverpackets.PledgeReceiveSubPledgeCreated;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowInfoUpdate;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListAll;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListDeleteAll;
import org.l2jmobius.gameserver.network.serverpackets.PledgeShowMemberListUpdate;
import org.l2jmobius.gameserver.network.serverpackets.PledgeSkillList;
import org.l2jmobius.gameserver.network.serverpackets.PledgeSkillList.SubPledgeSkill;
import org.l2jmobius.gameserver.network.serverpackets.PledgeSkillListAdd;
import org.l2jmobius.gameserver.network.serverpackets.ServerPacket;
import org.l2jmobius.gameserver.network.serverpackets.StatusUpdate;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

public class Clan
{
	private static final Logger LOGGER = Logger.getLogger(Clan.class.getName());
	
	// SQL queries
	private static final String SELECT_CLAN_DATA = "SELECT * FROM clan_data where clan_id=?";
	
	// Ally Penalty Types
	/** Clan leaved ally */
	public static final int PENALTY_TYPE_CLAN_LEAVED = 1;
	/** Clan was dismissed from ally */
	public static final int PENALTY_TYPE_CLAN_DISMISSED = 2;
	/** Leader clan dismiss clan from ally */
	public static final int PENALTY_TYPE_DISMISS_CLAN = 3;
	/** Leader clan dissolve ally */
	public static final int PENALTY_TYPE_DISSOLVE_ALLY = 4;
	
	// Sub-unit types
	/** Clan subunit type of Academy */
	public static final int SUBUNIT_ACADEMY = -1;
	/** Clan subunit type of Royal Guard A */
	public static final int SUBUNIT_ROYAL1 = 100;
	/** Clan subunit type of Royal Guard B */
	public static final int SUBUNIT_ROYAL2 = 200;
	/** Clan subunit type of Order of Knights A-1 */
	public static final int SUBUNIT_KNIGHT1 = 1001;
	/** Clan subunit type of Order of Knights A-2 */
	public static final int SUBUNIT_KNIGHT2 = 1002;
	/** Clan subunit type of Order of Knights B-1 */
	public static final int SUBUNIT_KNIGHT3 = 2001;
	/** Clan subunit type of Order of Knights B-2 */
	public static final int SUBUNIT_KNIGHT4 = 2002;
	
	private String _name;
	private int _clanId;
	private volatile ClanMember _leader;
	private final Map<Integer, ClanMember> _members = new ConcurrentHashMap<>();
	
	private String _allyName;
	private int _allyId = 0;
	private long _allyGeneration;
	private long _allyGenerationCounter;
	private volatile int _level;
	private int _castleId;
	private int _fortId;
	private int _hideoutId;
	private int _hiredGuards;
	private int _crestId;
	private int _crestLargeId;
	private int _allyCrestId;
	private int _auctionBiddedAt = 0;
	private long _allyPenaltyExpiryTime;
	private int _allyPenaltyType;
	private long _charPenaltyExpiryTime;
	private long _dissolvingExpiryTime;
	private int _bloodAllianceCount;
	private int _bloodOathCount;
	
	private final ItemContainer _warehouse = new ClanWarehouse(this);
	private final Set<Integer> _atWarWith = ConcurrentHashMap.newKeySet();
	private final Set<Integer> _atWarAttackers = ConcurrentHashMap.newKeySet();
	
	private Forum _forum;
	
	/** Map(Integer, Skill) containing all skills of the Clan */
	private final Map<Integer, Skill> _skills = new ConcurrentHashMap<>();
	private final Map<Integer, RankPrivs> _privs = new ConcurrentHashMap<>();
	private final Map<Integer, SubPledge> _subPledges = new ConcurrentHashMap<>();
	private final Map<Integer, Skill> _subPledgeSkills = new ConcurrentHashMap<>();
	
	private volatile int _reputationScore = 0;
	private int _storedReputationScore;
	private Integer _reputationIfCommitted;
	private int _rank = 0;
	
	private String _notice;
	private boolean _noticeEnabled = false;
	private static final int MAX_NOTICE_LENGTH = 8192;
	private int _newLeaderId;
	
	private final AtomicInteger _siegeKills = new AtomicInteger();
	private final AtomicInteger _siegeDeaths = new AtomicInteger();
	
	/**
	 * Called if a clan is referenced only by id. In this case all other data needs to be fetched from db
	 * @param clanId A valid clan Id to create and restore
	 */
	public Clan(int clanId)
	{
		_clanId = clanId;
		initializePrivs();
		restore();
		_warehouse.restore();
	}
	
	/**
	 * Called only if a new clan is created
	 * @param clanId A valid clan Id to create
	 * @param clanName A valid clan name
	 */
	public Clan(int clanId, String clanName)
	{
		_clanId = clanId;
		_name = clanName;
		initializePrivs();
	}
	
	/**
	 * @return Returns the clanId.
	 */
	public int getId()
	{
		return _clanId;
	}
	
	/**
	 * @param clanId The clanId to set.
	 */
	public void setClanId(int clanId)
	{
		_clanId = clanId;
	}
	
	/**
	 * @return Returns the leaderId.
	 */
	public int getLeaderId()
	{
		final ClanMember leader = _leader;
		return leader != null ? leader.getObjectId() : 0;
	}
	
	/**
	 * @return PledgeMember of clan leader.
	 */
	public ClanMember getLeader()
	{
		return _leader;
	}
	
	/**
	 * @param leader the leader to set.
	 */
	public synchronized void setLeader(ClanMember leader)
	{
		_leader = leader;
		_members.put(leader.getObjectId(), leader);
	}
	
	/** Reattachment can refresh a leader's member object while only the native transfer can change its identity. */
	synchronized void refreshLeaderMember(ClanMember member)
	{
		if (getLeaderId() == member.getObjectId()) { setLeader(member); }
	}

	public void setNewLeader(ClanMember member)
	{
		if (member == null) { return; }
		final ClanSocialMutationFence fence = ClanSocialMutationFence.getInstance();
		fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			final ClanMember current = _members.get(member.getObjectId());
			if (!fence.isRetiring(_clanId) && !firstLevelPaymentPending() && (current != null) && (_leader != null)
				&& ClanDepartureService.settleMember(current) && _members.get(current.getObjectId()) == current)
			{
				setNewLeaderLocked(current);
			}
			return null;
		});
	}

	private void setNewLeaderLocked(ClanMember member)
	{
		final Player newLeader = member.getPlayer();
		final ClanMember exMember = _leader;
		final Player exLeader = exMember.getPlayer();
		
		// Notify to scripts
		if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_LEADER_CHANGE))
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanLeaderChange(exMember, member, this));
		}
		
		if (exLeader != null)
		{
			if (exLeader.isFlying())
			{
				exLeader.dismount();
			}
			
			if (getLevel() >= SiegeManager.getInstance().getSiegeClanMinLevel())
			{
				SiegeManager.getInstance().removeSiegeSkills(exLeader);
			}
			
			exLeader.getClanPrivileges().disableAll();
			exLeader.broadcastUserInfo();
		}
		else
		{
			try (Connection con = DatabaseFactory.getConnection();
				PreparedStatement ps = con.prepareStatement("UPDATE characters SET clan_privs = ? WHERE charId = ?"))
			{
				ps.setInt(1, 0);
				ps.setInt(2, getLeaderId());
				ps.execute();
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Couldn't update clan privs for old clan leader", e);
			}
		}
		
		setLeader(member);
		if (_newLeaderId != 0)
		{
			setNewLeaderId(0, true);
		}
		
		updateClanInDB();
		
		if (exLeader != null)
		{
			exLeader.setPledgeClass(ClanMember.calculatePledgeClass(exLeader));
			exLeader.broadcastUserInfo();
			exLeader.checkItemRestriction();
		}
		
		if (newLeader != null)
		{
			newLeader.setPledgeClass(ClanMember.calculatePledgeClass(newLeader));
			newLeader.getClanPrivileges().enableAll();
			
			if (getLevel() >= SiegeManager.getInstance().getSiegeClanMinLevel())
			{
				SiegeManager.getInstance().addSiegeSkills(newLeader);
			}
			
			newLeader.broadcastUserInfo();
		}
		else
		{
			try (Connection con = DatabaseFactory.getConnection();
				PreparedStatement ps = con.prepareStatement("UPDATE characters SET clan_privs = ? WHERE charId = ?"))
			{
				ps.setInt(1, ClanPrivileges.getCompleteMask());
				ps.setInt(2, getLeaderId());
				ps.execute();
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Couldn't update clan privs for new clan leader", e);
			}
		}
		
		broadcastClanStatus();
		broadcastToOnlineMembers(new SystemMessage(SystemMessageId.CLAN_LORD_PRIVILEGES_HAVE_BEEN_TRANSFERRED_TO_C1).addString(member.getName()));
		
		LOGGER.log(Level.INFO, "Leader of Clan: " + getName() + " changed to: " + member.getName() + " ex leader: " + exMember.getName());
	}
	
	/**
	 * @return the clan leader's name.
	 */
	public String getLeaderName()
	{
		if (_leader == null)
		{
			LOGGER.warning(Clan.class.getName() + ": Clan " + getName() + " without clan leader!");
			return "";
		}
		
		return _leader.getName();
	}
	
	/**
	 * @return the clan name.
	 */
	public String getName()
	{
		return _name;
	}
	
	/**
	 * @param name The name to set.
	 */
	public void setName(String name)
	{
		_name = name;
	}
	
	/**
	 * Adds a clan member to the clan.
	 * @param member the clan member.
	 */
	private void addClanMember(ClanMember member)
	{
		_members.put(member.getObjectId(), member);
	}
	
	/**
	 * Adds a clan member to the clan.<br>
	 * Using a different constructor, to make it easier to read.
	 * @param player the clan member
	 */
	public void addClanMember(Player player)
	{
		final ClanMember member = new ClanMember(this, player);
		member.setPlayer(player);
		addClanMember(member);
		
		player.setClan(this);
		player.setPledgeClass(ClanMember.calculatePledgeClass(player));
		player.sendPacket(new PledgeShowMemberListUpdate(player));
		player.sendPacket(new PledgeSkillList(this));
		addSkillEffects(player);
		
		// Notify to scripts
		if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_JOIN))
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanJoin(member, this));
		}
	}
	
	/**
	 * Updates player status in clan.
	 * @param player the player to be updated.
	 */
	public void updateClanMember(Player player)
	{
		synchronized (this)
		{
			if (player.getClan() != this || !_members.containsKey(player.getObjectId())) { return; }
			final ClanMember member = new ClanMember(this, player);
			if (getLeaderId() == member.getObjectId())
			{
				setLeader(member);
			}
			else
			{
				addClanMember(member);
			}
		}
	}
	
	/**
	 * @param name the name of the required clan member.
	 * @return the clan member for a given name.
	 */
	public ClanMember getClanMember(String name)
	{
		for (ClanMember temp : _members.values())
		{
			if (temp.getName().equals(name))
			{
				return temp;
			}
		}
		
		return null;
	}
	
	/**
	 * @param objectId the required clan member object Id.
	 * @return the clan member for a given {@code objectId}.
	 */
	public ClanMember getClanMember(int objectId)
	{
		return _members.get(objectId);
	}
	
	/**
	 * @param objectId the object Id of the member that will be removed.
	 * @param clanJoinExpiryTime time penalty to join a clan.
	 */
	public void removeClanMember(int objectId, long clanJoinExpiryTime)
	{
		removeClanMember(objectId, clanJoinExpiryTime, DepartureKind.UNKNOWN, 0);
	}

	public void removeClanMember(int objectId, long clanJoinExpiryTime, DepartureKind departureKind, int initiatorObjectId)
	{
		final var fence = ClanSocialMutationFence.getInstance();
		fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			final ClanMember member = _members.get(objectId);
			if (member != null && ClanDepartureService.settleMember(member))
			{
				removeClanMemberLocked(objectId, clanJoinExpiryTime, departureKind, initiatorObjectId);
			}
			return null;
		});
	}

	/** Native voluntary withdrawal for the stock client. */
	public void withdrawMember(Player player)
	{
		final var fence = ClanSocialMutationFence.getInstance();
		fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			final int objectId = player.getObjectId();
			final ClanMember member = _members.get(objectId);
			if (player.getClan() == this && member != null && member.getPlayer() == player && !fence.isRetiring(_clanId)
				&& objectId != getLeaderId() && !player.isInCombat() && ClanDepartureService.settleMember(member)
				&& removeClanMemberLocked(objectId, ClanDepartureService.joinExpiry(System.currentTimeMillis(), PlayerConfig.ALT_CLAN_JOIN_DAYS), DepartureKind.VOLUNTARY, objectId))
			{
				ClanDepartureService.announceVoluntary(this, player);
			}
			return null;
		});
	}

	/** Revalidate the stock dismissal and apply its clan penalty only for the winning removal. */
	public boolean expelMember(Player actor, ClanMember expected)
	{
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			final ClanMember member = _members.get(expected.getObjectId());
			if (actor.getClan() != this || !actor.hasAccess(ClanAccess.REMOVE_MEMBER) || actor.getObjectId() == expected.getObjectId() || fence.isRetiring(_clanId)
				|| member != expected || member.getObjectId() == getLeaderId() || !ClanDepartureService.settleMember(member)
				|| (member.isOnline() && member.getPlayer().isInCombat())) { return false; }
			final long expiry = ClanDepartureService.joinExpiry(System.currentTimeMillis(), PlayerConfig.ALT_CLAN_JOIN_DAYS);
			if (!removeClanMemberLocked(member.getObjectId(), expiry, DepartureKind.EXPELLED, actor.getObjectId())) { return false; }
			setCharPenaltyExpiryTime(expiry);
			updateClanInDB();
			return true;
		});
	}

	/** Resolve pending voluntary member departures before retirement starts removing dependent state. */
	public boolean settleMemberDepartures()
	{
		return List.copyOf(_members.values()).stream().allMatch(ClanDepartureService::settleMember);
	}

	/** Keep stock pledge and mentoring changes ordered with departure of either member. */
	public void modifyPledgeMembers(Player actor, ClanAccess access, ClanMember first, ClanMember second, Runnable operation)
	{
		final var fence = ClanSocialMutationFence.getInstance();
		fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			if (actor.getClan() == this && actor.hasAccess(access) && !fence.isRetiring(_clanId)
				&& ClanDepartureService.settleMember(first) && ClanDepartureService.settleMember(second)
				&& _members.get(first.getObjectId()) == first && _members.get(second.getObjectId()) == second)
			{
				operation.run();
			}
			return null;
		});
	}

	boolean finishOrdinaryDeparture(Player player, long clanJoinExpiryTime)
	{
		final ClanMember member;
		synchronized (this)
		{
			member = _members.get(player.getObjectId());
			if (member == null || member.getPlayer() != player || player.getClan() != this || getLeaderId() == player.getObjectId()) { return false; }
			_members.remove(player.getObjectId());
			player.setClan(null);
			player.setClanJoinExpiryTime(clanJoinExpiryTime);
		}
		try
		{
			removeSkillEffects(player);
			player.sendSkillList();
			publishMemberRemoval(player);
			notifyMemberRemoval(member, DepartureKind.VOLUNTARY, player.getObjectId());
		}
		catch (RuntimeException failure)
		{
			LOGGER.log(Level.WARNING, "Committed voluntary clan departure publication failed for " + player.getObjectId(), failure);
		}
		return true;
	}

	private boolean removeClanMemberLocked(int objectId, long clanJoinExpiryTime, DepartureKind departureKind, int initiatorObjectId)
	{
		final ClanMember exMember;
		synchronized (this)
		{
			exMember = _members.remove(objectId);
		}
		if (exMember == null)
		{
			return false;
		}
		
		final int leadssubpledge = getLeaderSubPledge(objectId);
		if (leadssubpledge != 0)
		{
			// Sub-unit leader withdraws, position becomes vacant and leader
			// should appoint new via NPC
			getSubPledge(leadssubpledge).setLeaderId(0);
			updateSubPledgeInDB(leadssubpledge);
		}
		
		if (exMember.getApprentice() != 0)
		{
			final ClanMember apprentice = getClanMember(exMember.getApprentice());
			if (apprentice != null)
			{
				if (apprentice.getPlayer() != null)
				{
					apprentice.getPlayer().setSponsor(0);
				}
				else
				{
					apprentice.setApprenticeAndSponsor(0, 0);
				}
				
				apprentice.saveApprenticeAndSponsor(0, 0);
			}
		}
		
		if (exMember.getSponsor() != 0)
		{
			final ClanMember sponsor = getClanMember(exMember.getSponsor());
			if (sponsor != null)
			{
				if (sponsor.getPlayer() != null)
				{
					sponsor.getPlayer().setApprentice(0);
				}
				else
				{
					sponsor.setApprenticeAndSponsor(0, 0);
				}
				
				sponsor.saveApprenticeAndSponsor(0, 0);
			}
		}
		
		exMember.saveApprenticeAndSponsor(0, 0);
		if (PlayerConfig.REMOVE_CASTLE_CIRCLETS)
		{
			CastleManager.getInstance().removeCirclet(exMember, getCastleId());
		}
		
		final Player player = exMember.getPlayer();
		if (player != null)
		{
			if (!player.isNoble())
			{
				player.setTitle("");
			}
			
			player.setApprentice(0);
			player.setSponsor(0);
			
			if (player.isClanLeader())
			{
				SiegeManager.getInstance().removeSiegeSkills(player);
				player.setClanCreateExpiryTime(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_CLAN_CREATE_DAYS));
			}
			
			// remove Clan skills from Player
			removeSkillEffects(player);
			
			// remove Residential skills
			if (getCastleId() > 0)
			{
				final Castle castle = CastleManager.getInstance().getCastleByOwner(this);
				if (castle != null)
				{
					castle.removeResidentialSkills(player);
				}
			}
			
			if (getFortId() > 0)
			{
				final Fort fort = FortManager.getInstance().getFortByOwner(this);
				if (fort != null)
				{
					fort.removeResidentialSkills(player);
				}
			}
			
			player.sendSkillList();
			player.setClan(null);
			
			// players leaving from clan academy have no penalty
			if (exMember.getPledgeType() != -1)
			{
				player.setClanJoinExpiryTime(clanJoinExpiryTime);
			}
			
			publishMemberRemoval(player);
		}
		else
		{
			removeMemberInDatabase(exMember.getObjectId(), clanJoinExpiryTime, getLeaderId() == objectId ? System.currentTimeMillis() + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_CLAN_CREATE_DAYS) : 0);
		}
		
		notifyMemberRemoval(exMember, departureKind, initiatorObjectId);
		return true;
	}

	private static void publishMemberRemoval(Player player)
	{
		player.setPledgeClass(ClanMember.calculatePledgeClass(player));
		player.broadcastUserInfo();
		player.sendPacket(PledgeShowMemberListDeleteAll.STATIC_PACKET);
	}

	private void notifyMemberRemoval(ClanMember exMember, DepartureKind departureKind, int initiatorObjectId)
	{
		if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_LEFT))
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanLeft(exMember, this, departureKind, initiatorObjectId));
		}
	}
	
	public Collection<ClanMember> getMembers()
	{
		return _members.values();
	}
	
	public int getMembersCount()
	{
		return _members.size();
	}
	
	public int getSubPledgeMembersCount(int subpl)
	{
		int result = 0;
		for (ClanMember temp : _members.values())
		{
			if (temp.getPledgeType() == subpl)
			{
				result++;
			}
		}
		
		return result;
	}
	
	/**
	 * @param pledgeType the Id of the pledge type.
	 * @return the maximum number of members allowed for a given {@code pledgeType}.
	 */
	public int getMaxNrOfMembers(int pledgeType)
	{
		int limit = 0;
		
		switch (pledgeType)
		{
			case 0:
			{
				switch (_level)
				{
					case 3:
					{
						limit = 30;
						break;
					}
					case 2:
					{
						limit = 20;
						break;
					}
					case 1:
					{
						limit = 15;
						break;
					}
					case 0:
					{
						limit = 10;
						break;
					}
					default:
					{
						limit = 40;
						break;
					}
				}
				break;
			}
			case -1:
			{
				limit = 20;
				break;
			}
			case 100:
			case 200:
			{
				switch (_level)
				{
					case 11:
					{
						limit = 30;
						break;
					}
					default:
					{
						limit = 20;
						break;
					}
				}
				break;
			}
			case 1001:
			case 1002:
			case 2001:
			case 2002:
			{
				switch (_level)
				{
					case 9:
					case 10:
					case 11:
					{
						limit = 25;
						break;
					}
					default:
					{
						limit = 10;
						break;
					}
				}
				break;
			}
			default:
			{
				break;
			}
		}
		
		return limit;
	}
	
	/**
	 * @param exclude the object Id to exclude from list.
	 * @return all online members excluding the one with object id {code exclude}.
	 */
	public List<Player> getOnlineMembers(int exclude)
	{
		final List<Player> onlineMembers = new ArrayList<>();
		for (ClanMember temp : _members.values())
		{
			if ((temp != null) && temp.isOnline() && (temp.getObjectId() != exclude))
			{
				onlineMembers.add(temp.getPlayer());
			}
		}
		
		return onlineMembers;
	}
	
	/**
	 * @return the online clan member count.
	 */
	public int getOnlineMembersCount()
	{
		int count = 0;
		for (ClanMember temp : _members.values())
		{
			if ((temp == null) || !temp.isOnline())
			{
				continue;
			}
			
			count++;
		}
		
		return count;
	}
	
	/**
	 * @return the alliance Id.
	 */
	public int getAllyId()
	{
		return _allyId;
	}

	public long getAllyGeneration()
	{
		return _allyGeneration;
	}

	long getAllyGenerationCounter()
	{
		return _allyGenerationCounter;
	}
	
	/**
	 * @return the alliance name.
	 */
	public String getAllyName()
	{
		return _allyName;
	}
	
	/**
	 * @param allyCrestId the alliance crest Id to be set.
	 */
	void setAllyCrestId(int allyCrestId)
	{
		_allyCrestId = allyCrestId;
	}
	
	/**
	 * @return the alliance crest Id.
	 */
	public int getAllyCrestId()
	{
		return _allyCrestId;
	}
	
	/**
	 * @return the clan level.
	 */
	public int getLevel()
	{
		return _level;
	}
	
	/**
	 * Sets the clan level and updates the clan forum if it's needed.
	 * @param level the clan level to be set.
	 */
	private void setLevel(int level)
	{
		_level = level;
		if ((_level >= 2) && (_forum == null) && GeneralConfig.ENABLE_COMMUNITY_BOARD)
		{
			final Forum forum = ForumsBBSManager.getInstance().getForumByName("ClanRoot");
			if (forum != null)
			{
				_forum = forum.getChildByName(_name);
				if (_forum == null)
				{
					_forum = ForumsBBSManager.getInstance().createNewForum(_name, ForumsBBSManager.getInstance().getForumByName("ClanRoot"), Forum.CLAN, Forum.CLANMEMBERONLY, getId());
				}
			}
		}
	}
	
	/**
	 * @return the castle Id for this clan if owns a castle, zero otherwise.
	 */
	public int getCastleId()
	{
		return _castleId;
	}
	
	/**
	 * @return the fort Id for this clan if owns a fort, zero otherwise.
	 */
	public int getFortId()
	{
		return _fortId;
	}
	
	/**
	 * @return the hideout Id for this clan if owns a hideout, zero otherwise.
	 */
	public int getHideoutId()
	{
		return _hideoutId;
	}
	
	/**
	 * @param crestId the Id of the clan crest to be set.
	 */
	public void setCrestId(int crestId)
	{
		_crestId = crestId;
	}
	
	/**
	 * @return Returns the clanCrestId.
	 */
	public int getCrestId()
	{
		return _crestId;
	}
	
	/**
	 * @param crestLargeId The id of pledge LargeCrest.
	 */
	public void setCrestLargeId(int crestLargeId)
	{
		_crestLargeId = crestLargeId;
	}
	
	/**
	 * @return Returns the clan CrestLargeId
	 */
	public int getCrestLargeId()
	{
		return _crestLargeId;
	}
	
	/**
	 * @param allyId The allyId to set.
	 */
	void setAllyId(int allyId)
	{
		_allyId = allyId;
	}

	void setAllyGeneration(long generation)
	{
		_allyGeneration = generation;
	}

	void setAllyGenerationCounter(long generationCounter)
	{
		_allyGenerationCounter = generationCounter;
	}
	
	/**
	 * @param allyName The allyName to set.
	 */
	void setAllyName(String allyName)
	{
		_allyName = allyName;
	}
	
	/**
	 * @param castleId the castle Id to set.
	 */
	public void setCastleId(int castleId)
	{
		_castleId = castleId;
	}
	
	/**
	 * @param fortId the fort Id to set.
	 */
	public void setFortId(int fortId)
	{
		_fortId = fortId;
	}
	
	/**
	 * @param hideoutId the hideout Id to set.
	 */
	public void setHideoutId(int hideoutId)
	{
		_hideoutId = hideoutId;
	}
	
	/**
	 * @param id the Id of the player to be verified.
	 * @return {code true} if the player belongs to the clan.
	 */
	public boolean isMember(int id)
	{
		return (id != 0) && _members.containsKey(id);
	}
	
	/**
	 * @return the Blood Alliance count for this clan
	 */
	public int getBloodAllianceCount()
	{
		return _bloodAllianceCount;
	}
	
	/**
	 * Increase Blood Alliance count by config predefined count and updates the database.
	 */
	public void increaseBloodAllianceCount()
	{
		_bloodAllianceCount += SiegeManager.getInstance().getBloodAllianceReward();
		updateBloodAllianceCountInDB();
	}
	
	/**
	 * Reset the Blood Alliance count to zero and updates the database.
	 */
	public void resetBloodAllianceCount()
	{
		_bloodAllianceCount = 0;
		updateBloodAllianceCountInDB();
	}
	
	/**
	 * Store current Bloood Alliances count in database.
	 */
	public void updateBloodAllianceCountInDB()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_data SET blood_alliance_count=? WHERE clan_id=?"))
		{
			ps.setInt(1, _bloodAllianceCount);
			ps.setInt(2, _clanId);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "Exception on updateBloodAllianceCountInDB(): " + e.getMessage(), e);
		}
	}
	
	/**
	 * @return the Blood Oath count for this clan
	 */
	public int getBloodOathCount()
	{
		return _bloodOathCount;
	}
	
	/**
	 * Increase Blood Oath count by config predefined count and updates the database.
	 */
	public void increaseBloodOathCount()
	{
		_bloodOathCount += FeatureConfig.FS_BLOOD_OATH_COUNT;
		updateBloodOathCountInDB();
	}
	
	/**
	 * Reset the Blood Oath count to zero and updates the database.
	 */
	public void resetBloodOathCount()
	{
		_bloodOathCount = 0;
		updateBloodOathCountInDB();
	}
	
	/**
	 * Store current Bloood Alliances count in database.
	 */
	public void updateBloodOathCountInDB()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_data SET blood_oath_count=? WHERE clan_id=?"))
		{
			ps.setInt(1, _bloodOathCount);
			ps.setInt(2, _clanId);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "Exception on updateBloodAllianceCountInDB(): " + e.getMessage(), e);
		}
	}
	
	/**
	 * Updates in database clan information:
	 * <ul>
	 * <li>Clan leader Id</li>
	 * <li>Alliance Id</li>
	 * <li>Alliance name</li>
	 * <li>Clan's reputation</li>
	 * <li>Alliance's penalty expiration time</li>
	 * <li>Alliance's penalty type</li>
	 * <li>Character's penalty expiration time</li>
	 * <li>Dissolving expiration time</li>
	 * <li>Clan's id</li>
	 * </ul>
	 */
	public void updateClanInDB()
	{
		ClanSocialMutationFence.getInstance().execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			final Player leader = _firstLevelPaymentPending && _leader != null ? _leader.getPlayer() : null;
			if (leader != null) { ClanFoundingDevelopment.settlePending(leader); }
			updateClanInDBLocked();
			return null;
		});
	}

	private synchronized void updateClanInDBLocked()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_data SET leader_id=?,reputation_score=IF(?,reputation_score,?),char_penalty_expiry_time=?,dissolving_expiry_time=?,new_leader_id=? WHERE clan_id=?"))
		{
			ps.setInt(1, getLeaderId());
			ps.setBoolean(2, _reputationIfCommitted != null);
			ps.setInt(3, _reputationScore);
			ps.setLong(4, _charPenaltyExpiryTime);
			ps.setLong(5, _dissolvingExpiryTime);
			ps.setInt(6, _newLeaderId);
			ps.setInt(7, _clanId);
			if (ps.executeUpdate() == 1 && _reputationIfCommitted == null) { _storedReputationScore = _reputationScore; }
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error saving clan: " + e.getMessage(), e);
		}
	}
	
	/**
	 * Stores in database clan information:
	 * <ul>
	 * <li>Clan Id</li>
	 * <li>Clan name</li>
	 * <li>Clan level</li>
	 * <li>Has castle</li>
	 * <li>Alliance Id</li>
	 * <li>Alliance name</li>
	 * <li>Clan leader Id</li>
	 * <li>Clan crest Id</li>
	 * <li>Clan large crest Id</li>
	 * <li>Alliance crest Id</li>
	 * </ul>
	 */
	public boolean persistFounding() throws SQLException
	{
		try
		{
			ClanSocialRepository.getInstance().createFoundedClan(_clanId, _name, getLeaderId(), System.currentTimeMillis());
			return true;
		}
		catch (ClanSocialRepository.StaleStateException stale)
		{
			return false;
		}
	}

	public enum FoundingState
	{
		ABSENT,
		COMMITTED,
		CONFLICT
	}

	public FoundingState observeFounding() throws SQLException
	{
		return ClanSocialRepository.getInstance().foundingState(_clanId, _name, getLeaderId());
	}
	
	/**
	 * Removes a clan member from this clan.
	 * @param playerId the clan member object ID to be removed
	 * @param clanJoinExpiryTime the time penalty for the player to join a new clan
	 * @param clanCreateExpiryTime the time penalty for the player to create a new clan
	 */
	private void removeMemberInDatabase(int playerId, long clanJoinExpiryTime, long clanCreateExpiryTime)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps1 = con.prepareStatement("UPDATE characters SET clanid=0, title=?, clan_join_expiry_time=?, clan_create_expiry_time=?, clan_privs=0, wantspeace=0, subpledge=0, lvl_joined_academy=0, apprentice=0, sponsor=0 WHERE charId=?");
			PreparedStatement ps2 = con.prepareStatement("UPDATE characters SET apprentice=0 WHERE apprentice=?");
			PreparedStatement ps3 = con.prepareStatement("UPDATE characters SET sponsor=0 WHERE sponsor=?"))
		{
			// Remove clan member.
			ps1.setString(1, "");
			ps1.setLong(2, clanJoinExpiryTime);
			ps1.setLong(3, clanCreateExpiryTime);
			ps1.setInt(4, playerId);
			ps1.execute();
			
			// Remove apprentice.
			ps2.setInt(1, playerId);
			ps2.execute();
			
			// Remove sponsor.
			ps3.setInt(1, playerId);
			ps3.execute();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error removing clan member: " + e.getMessage(), e);
		}
	}
	
	private void restore()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_CLAN_DATA))
		{
			ps.setInt(1, _clanId);
			try (ResultSet clanData = ps.executeQuery())
			{
				if (clanData.next())
				{
					setName(clanData.getString("clan_name"));
					setLevel(clanData.getInt("clan_level"));
					setCastleId(clanData.getInt("hasCastle"));
					_bloodAllianceCount = clanData.getInt("blood_alliance_count");
					_bloodOathCount = clanData.getInt("blood_oath_count");
					setAllyId(clanData.getInt("ally_id"));
					setAllyName(clanData.getString("ally_name"));
					setAllyGeneration(clanData.getLong("ally_generation"));
					setAllyGenerationCounter(clanData.getLong("ally_generation_counter"));
					setAllyPenaltyExpiryTime(clanData.getLong("ally_penalty_expiry_time"), clanData.getInt("ally_penalty_type"));
					if (_allyPenaltyExpiryTime < System.currentTimeMillis())
					{
						setAllyPenaltyExpiryTime(0, 0);
					}
					
					setCharPenaltyExpiryTime(clanData.getLong("char_penalty_expiry_time"));
					if ((_charPenaltyExpiryTime + (PlayerConfig.ALT_CLAN_JOIN_DAYS * 86400000)) < System.currentTimeMillis()) // 24*60*60*1000 = 86400000
					{
						setCharPenaltyExpiryTime(0);
					}
					
					setDissolvingExpiryTime(clanData.getLong("dissolving_expiry_time"));
					
					setCrestId(clanData.getInt("crest_id"));
					setCrestLargeId(clanData.getInt("crest_large_id"));
					setAllyCrestId(clanData.getInt("ally_crest_id"));
					
					_storedReputationScore = clanData.getInt("reputation_score");
					setReputationScore(_storedReputationScore);
					setAuctionBiddedAt(clanData.getInt("auction_bid_at"), false);
					setNewLeaderId(clanData.getInt("new_leader_id"), false);
					
					final int leaderId = clanData.getInt("leader_id");
					ps.clearParameters();
					
					try (PreparedStatement select = con.prepareStatement("SELECT char_name,level,classid,charId,title,power_grade,subpledge,apprentice,sponsor,sex,race FROM characters WHERE clanid=?"))
					{
						select.setInt(1, _clanId);
						try (ResultSet clanMember = select.executeQuery())
						{
							ClanMember member = null;
							while (clanMember.next())
							{
								member = new ClanMember(this, clanMember);
								if (member.getObjectId() == leaderId)
								{
									setLeader(member);
								}
								else
								{
									addClanMember(member);
								}
							}
						}
					}
				}
			}
			
			restoreSubPledges();
			restoreRankPrivs();
			restoreSkills();
			restoreNotice();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error restoring clan data: " + e.getMessage(), e);
		}
	}
	
	private void restoreNotice()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT enabled,notice FROM clan_notices WHERE clan_id=?"))
		{
			ps.setInt(1, _clanId);
			try (ResultSet noticeData = ps.executeQuery())
			{
				while (noticeData.next())
				{
					_noticeEnabled = Boolean.parseBoolean(noticeData.getString("enabled"));
					_notice = noticeData.getString("notice");
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error restoring clan notice: " + e.getMessage(), e);
		}
	}
	
	private void storeNotice(String noticeValue, boolean enabled)
	{
		String notice = noticeValue;
		if (notice == null)
		{
			notice = "";
		}
		
		if (notice.length() > MAX_NOTICE_LENGTH)
		{
			notice = notice.substring(0, MAX_NOTICE_LENGTH - 1);
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO clan_notices (clan_id,notice,enabled) values (?,?,?) ON DUPLICATE KEY UPDATE notice=?,enabled=?"))
		{
			ps.setInt(1, _clanId);
			ps.setString(2, notice);
			if (enabled)
			{
				ps.setString(3, "true");
			}
			else
			{
				ps.setString(3, "false");
			}
			
			ps.setString(4, notice);
			if (enabled)
			{
				ps.setString(5, "true");
			}
			else
			{
				ps.setString(5, "false");
			}
			
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "Error could not store clan notice: " + e.getMessage(), e);
		}
		
		_notice = notice;
		_noticeEnabled = enabled;
	}
	
	public void setNoticeEnabled(boolean enabled)
	{
		storeNotice(getNotice(), enabled);
	}
	
	public void setNotice(String notice)
	{
		storeNotice(notice, _noticeEnabled);
	}
	
	public boolean isNoticeEnabled()
	{
		return _noticeEnabled;
	}
	
	public String getNotice()
	{
		if (_notice == null)
		{
			return "";
		}
		
		// Bypass exploit check.
		final String text = _notice.toLowerCase();
		if (text.contains("action") && text.contains("bypass"))
		{
			return "";
		}
		
		// Returns text without tags.
		return _notice.replaceAll("<.*?>", "");
	}
	
	private void restoreSkills()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT skill_id,skill_level,sub_pledge_id FROM clan_skills WHERE clan_id=?"))
		{
			// Retrieve all skills of this Player from the database
			ps.setInt(1, _clanId);
			try (ResultSet rset = ps.executeQuery())
			{
				// Go though the recordset of this SQL query
				while (rset.next())
				{
					final int id = rset.getInt("skill_id");
					final int level = rset.getInt("skill_level");
					
					// Create a Skill object for each record
					final Skill skill = SkillData.getInstance().getSkill(id, level);
					
					// Add the Skill object to the Clan _skills
					final int subType = rset.getInt("sub_pledge_id");
					if (subType == -2)
					{
						_skills.put(skill.getId(), skill);
					}
					else if (subType == 0)
					{
						_subPledgeSkills.put(skill.getId(), skill);
					}
					else
					{
						final SubPledge subunit = _subPledges.get(subType);
						if (subunit != null)
						{
							subunit.addNewSkill(skill);
						}
						else
						{
							LOGGER.info("Missing subpledge " + subType + " for clan " + this + ", skill skipped.");
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error restoring clan skills: " + e.getMessage(), e);
		}
	}
	
	/**
	 * @return all the clan skills.
	 */
	public Collection<Skill> getAllSkills()
	{
		return _skills.values();
	}
	
	/**
	 * @return the map containing this clan skills.
	 */
	public Map<Integer, Skill> getSkills()
	{
		return _skills;
	}
	
	/**
	 * Used to add a skill to skill list of this Pledge
	 * @param newSkill
	 * @return
	 */
	public Skill addSkill(Skill newSkill)
	{
		return newSkill != null ? _skills.put(newSkill.getId(), newSkill) : null;
	}
	
	public Skill addNewSkill(Skill newSkill)
	{
		return addNewSkill(newSkill, -2);
	}
	
	/**
	 * Used to add a new skill to the list, send a packet to all online clan members, update their stats and store it in db
	 * @param newSkill
	 * @param subType
	 * @return
	 */
	public Skill addNewSkill(Skill newSkill, int subType)
	{
		Skill oldSkill = null;
		if (newSkill != null)
		{
			if (subType == -2)
			{
				oldSkill = _skills.put(newSkill.getId(), newSkill);
			}
			else if (subType == 0)
			{
				oldSkill = _subPledgeSkills.put(newSkill.getId(), newSkill);
			}
			else
			{
				final SubPledge subunit = getSubPledge(subType);
				if (subunit == null)
				{
					LOGGER.log(Level.WARNING, "Subpledge " + subType + " does not exist for clan " + this);
					return oldSkill;
				}
				
				oldSkill = subunit.addNewSkill(newSkill);
			}
			
			try (Connection con = DatabaseFactory.getConnection())
			{
				if (oldSkill != null)
				{
					try (PreparedStatement ps = con.prepareStatement("UPDATE clan_skills SET skill_level=? WHERE skill_id=? AND clan_id=?"))
					{
						ps.setInt(1, newSkill.getLevel());
						ps.setInt(2, oldSkill.getId());
						ps.setInt(3, _clanId);
						ps.execute();
					}
				}
				else
				{
					try (PreparedStatement ps = con.prepareStatement("INSERT INTO clan_skills (clan_id,skill_id,skill_level,skill_name,sub_pledge_id) VALUES (?,?,?,?,?)"))
					{
						ps.setInt(1, _clanId);
						ps.setInt(2, newSkill.getId());
						ps.setInt(3, newSkill.getLevel());
						ps.setString(4, newSkill.getName());
						ps.setInt(5, subType);
						ps.execute();
					}
				}
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Error could not store clan skills: " + e.getMessage(), e);
			}
			
			final SystemMessage sm = new SystemMessage(SystemMessageId.THE_CLAN_SKILL_S1_HAS_BEEN_ADDED);
			sm.addSkillName(newSkill.getId());
			
			for (ClanMember temp : _members.values())
			{
				if ((temp != null) && (temp.getPlayer() != null) && temp.isOnline())
				{
					if (subType == -2)
					{
						if (newSkill.getMinPledgeClass() <= temp.getPlayer().getPledgeClass())
						{
							temp.getPlayer().addSkill(newSkill, false); // Skill is not saved to player DB
							temp.getPlayer().sendPacket(new PledgeSkillListAdd(newSkill.getId(), newSkill.getLevel()));
							temp.getPlayer().sendPacket(sm);
							temp.getPlayer().sendSkillList();
						}
					}
					else if (temp.getPledgeType() == subType)
					{
						temp.getPlayer().addSkill(newSkill, false); // Skill is not saved to player DB
						temp.getPlayer().sendPacket(new ExSubPledgeSkillAdd(subType, newSkill.getId(), newSkill.getLevel()));
						temp.getPlayer().sendPacket(sm);
						temp.getPlayer().sendSkillList();
					}
				}
			}
		}
		
		return oldSkill;
	}
	
	public void addSkillEffects()
	{
		for (Skill skill : _skills.values())
		{
			for (ClanMember temp : _members.values())
			{
				try
				{
					if ((temp != null) && temp.isOnline() && (skill.getMinPledgeClass() <= temp.getPlayer().getPledgeClass()))
					{
						temp.getPlayer().addSkill(skill, false);
					}
				}
				catch (NullPointerException e)
				{
					LOGGER.log(Level.WARNING, e.getMessage(), e);
				}
			}
		}
	}
	
	public void addSkillEffects(Player player)
	{
		if (player == null)
		{
			return;
		}
		
		final int playerSocialClass = player.getPledgeClass() + 1;
		for (Skill skill : _skills.values())
		{
			final SkillLearn skillLearn = SkillTreeData.getInstance().getPledgeSkill(skill.getId(), skill.getLevel());
			if ((skillLearn == null) || (skillLearn.getSocialClass() == null) || (playerSocialClass >= skillLearn.getSocialClass().ordinal()))
			{
				player.addSkill(skill, false); // Skill is not saved to player DB
			}
		}
		
		if (player.getPledgeType() == 0)
		{
			for (Skill skill : _subPledgeSkills.values())
			{
				final SkillLearn skillLearn = SkillTreeData.getInstance().getSubPledgeSkill(skill.getId(), skill.getLevel());
				if ((skillLearn == null) || (skillLearn.getSocialClass() == null) || (playerSocialClass >= skillLearn.getSocialClass().ordinal()))
				{
					player.addSkill(skill, false); // Skill is not saved to player DB
				}
			}
		}
		else
		{
			final SubPledge subunit = getSubPledge(player.getPledgeType());
			if (subunit == null)
			{
				return;
			}
			
			for (Skill skill : subunit.getSkills())
			{
				player.addSkill(skill, false); // Skill is not saved to player DB
			}
		}
		
		if (_reputationScore < 0)
		{
			skillsStatus(player, true);
		}
	}
	
	public void removeSkillEffects(Player player)
	{
		if (player == null)
		{
			return;
		}
		
		for (Skill skill : _skills.values())
		{
			player.removeSkill(skill, false); // Skill is not saved to player DB
		}
		
		if (player.getPledgeType() == 0)
		{
			for (Skill skill : _subPledgeSkills.values())
			{
				player.removeSkill(skill, false); // Skill is not saved to player DB
			}
		}
		else
		{
			final SubPledge subunit = getSubPledge(player.getPledgeType());
			if (subunit == null)
			{
				return;
			}
			
			for (Skill skill : subunit.getSkills())
			{
				player.removeSkill(skill, false); // Skill is not saved to player DB
			}
		}
	}
	
	public void skillsStatus(Player player, boolean disable)
	{
		if (player == null)
		{
			return;
		}
		
		for (Skill skill : _skills.values())
		{
			if (disable)
			{
				player.disableSkill(skill, -1);
			}
			else
			{
				player.enableSkill(skill);
			}
		}
		
		if (player.getPledgeType() == 0)
		{
			for (Skill skill : _subPledgeSkills.values())
			{
				if (disable)
				{
					player.disableSkill(skill, -1);
				}
				else
				{
					player.enableSkill(skill);
				}
			}
		}
		else
		{
			final SubPledge subunit = getSubPledge(player.getPledgeType());
			if (subunit != null)
			{
				for (Skill skill : subunit.getSkills())
				{
					if (disable)
					{
						player.disableSkill(skill, -1);
					}
					else
					{
						player.enableSkill(skill);
					}
				}
			}
		}
	}
	
	public void broadcastToOnlineAllyMembers(ServerPacket packet)
	{
		for (Clan clan : ClanTable.getInstance().getClanAllies(getAllyId()))
		{
			clan.broadcastToOnlineMembers(packet);
		}
	}
	
	public void broadcastToOnlineMembers(ServerPacket packet)
	{
		for (ClanMember member : _members.values())
		{
			if ((member != null) && member.isOnline())
			{
				member.getPlayer().sendPacket(packet);
			}
		}
	}
	
	public void broadcastCSToOnlineMembers(CreatureSay packet, Player broadcaster)
	{
		for (ClanMember member : _members.values())
		{
			if ((member != null) && member.isOnline() && !BlockList.isBlocked(member.getPlayer(), broadcaster))
			{
				member.getPlayer().sendPacket(packet);
			}
		}
	}
	
	public void broadcastToOtherOnlineMembers(ServerPacket packet, Player player)
	{
		for (ClanMember member : _members.values())
		{
			if ((member != null) && member.isOnline() && (member.getPlayer() != player))
			{
				member.getPlayer().sendPacket(packet);
			}
		}
	}
	
	@Override
	public String toString()
	{
		return _name + "[" + _clanId + "]";
	}
	
	public ItemContainer getWarehouse()
	{
		return _warehouse;
	}
	
	public boolean isAtWarWith(int id)
	{
		return _atWarWith.contains(id);
	}
	
	public boolean isAtWarWith(Clan clan)
	{
		return _atWarWith.contains(clan.getId());
	}
	
	public boolean isAtWarAttacker(int id)
	{
		return _atWarAttackers.contains(id);
	}
	
	void setEnemyClan(Clan clan)
	{
		_atWarWith.add(clan.getId());
	}
	
	void setEnemyClan(int id)
	{
		_atWarWith.add(id);
	}
	
	void setAttackerClan(Clan clan)
	{
		_atWarAttackers.add(clan.getId());
	}
	
	void setAttackerClan(int clan)
	{
		_atWarAttackers.add(clan);
	}
	
	void deleteEnemyClan(Clan clan)
	{
		_atWarWith.remove(clan.getId());
	}
	
	void deleteAttackerClan(Clan clan)
	{
		_atWarAttackers.remove(clan.getId());
	}
	
	public int getHiredGuards()
	{
		return _hiredGuards;
	}
	
	public void incrementHiredGuards()
	{
		_hiredGuards++;
	}
	
	public boolean isAtWar()
	{
		return (_atWarWith != null) && !_atWarWith.isEmpty();
	}
	
	public Set<Integer> getWarList()
	{
		return _atWarWith;
	}
	
	public Set<Integer> getAttackerList()
	{
		return _atWarAttackers;
	}
	
	public void broadcastClanStatus()
	{
		for (Player member : getOnlineMembers(0))
		{
			member.sendPacket(PledgeShowMemberListDeleteAll.STATIC_PACKET);
			member.sendPacket(new PledgeShowMemberListAll(this, member));
		}
	}
	
	public static class SubPledge
	{
		private final int _id;
		private String _subPledgeName;
		private int _leaderId;
		private final Map<Integer, Skill> _subPledgeSkills = new HashMap<>();
		
		public SubPledge(int id, String name, int leaderId)
		{
			_id = id;
			_subPledgeName = name;
			_leaderId = leaderId;
		}
		
		public int getId()
		{
			return _id;
		}
		
		public String getName()
		{
			return _subPledgeName;
		}
		
		public void setName(String name)
		{
			_subPledgeName = name;
		}
		
		public int getLeaderId()
		{
			return _leaderId;
		}
		
		public void setLeaderId(int leaderId)
		{
			_leaderId = leaderId;
		}
		
		public Skill addNewSkill(Skill skill)
		{
			return _subPledgeSkills.put(skill.getId(), skill);
		}
		
		public Collection<Skill> getSkills()
		{
			return _subPledgeSkills.values();
		}
		
		public Skill getSkill(int id)
		{
			return _subPledgeSkills.get(id);
		}
	}
	
	public static class RankPrivs
	{
		private final int _rankId;
		private final int _party; // TODO: Find out what this stuff means and implement it.
		private final ClanPrivileges _rankPrivs;
		
		public RankPrivs(int rank, int party, int privs)
		{
			_rankId = rank;
			_party = party;
			_rankPrivs = new ClanPrivileges(privs);
		}
		
		public RankPrivs(int rank, int party, ClanPrivileges rankPrivs)
		{
			_rankId = rank;
			_party = party;
			_rankPrivs = rankPrivs;
		}
		
		public int getRank()
		{
			return _rankId;
		}
		
		public int getParty()
		{
			return _party;
		}
		
		public ClanPrivileges getPrivs()
		{
			return _rankPrivs;
		}
		
		public void setPrivs(int privs)
		{
			_rankPrivs.setMask(privs);
		}
	}
	
	private void restoreSubPledges()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT sub_pledge_id,name,leader_id FROM clan_subpledges WHERE clan_id=?"))
		{
			// Retrieve all subpledges of this clan from the database
			ps.setInt(1, _clanId);
			try (ResultSet rset = ps.executeQuery())
			{
				while (rset.next())
				{
					final int id = rset.getInt("sub_pledge_id");
					final String name = rset.getString("name");
					final int leaderId = rset.getInt("leader_id");
					
					// Create a SubPledge object for each record
					final SubPledge pledge = new SubPledge(id, name, leaderId);
					_subPledges.put(id, pledge);
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "Could not restore clan sub-units: " + e.getMessage(), e);
		}
	}
	
	/**
	 * used to retrieve subPledge by type
	 * @param pledgeType
	 * @return
	 */
	public SubPledge getSubPledge(int pledgeType)
	{
		return _subPledges.get(pledgeType);
	}
	
	/**
	 * Used to retrieve subPledge by type
	 * @param pledgeName
	 * @return
	 */
	public SubPledge getSubPledge(String pledgeName)
	{
		for (SubPledge sp : _subPledges.values())
		{
			if (sp.getName().equalsIgnoreCase(pledgeName))
			{
				return sp;
			}
		}
		
		return null;
	}
	
	/**
	 * Used to retrieve all subPledges
	 * @return
	 */
	public Collection<SubPledge> getAllSubPledges()
	{
		return _subPledges.values();
	}
	
	public synchronized SubPledge createSubPledge(Player player, int pledgeTypeValue, int leaderId, String subPledgeName)
	{
		if (_firstLevelPaymentPending) { return null; }
		SubPledge subPledge = null;
		final int pledgeType = getAvailablePledgeTypes(pledgeTypeValue);
		if (pledgeType == 0)
		{
			if (pledgeType == SUBUNIT_ACADEMY)
			{
				player.sendPacket(SystemMessageId.YOUR_CLAN_HAS_ALREADY_ESTABLISHED_A_CLAN_ACADEMY);
			}
			else
			{
				player.sendMessage("You can't create any more sub-units of this type");
			}
			
			return null;
		}
		
		if (_leader.getObjectId() == leaderId)
		{
			player.sendMessage("Leader is not correct");
			return null;
		}
		
		// Royal Guard 5000 points per each
		// Order of Knights 10000 points per each
		if ((pledgeType != -1) && (((_reputationScore < FeatureConfig.ROYAL_GUARD_COST) && (pledgeType < SUBUNIT_KNIGHT1)) || ((_reputationScore < FeatureConfig.KNIGHT_UNIT_COST) && (pledgeType > SUBUNIT_ROYAL2))))
		{
			player.sendPacket(SystemMessageId.THE_CLAN_REPUTATION_SCORE_IS_TOO_LOW);
			return null;
		}
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO clan_subpledges (clan_id,sub_pledge_id,name,leader_id) values (?,?,?,?)"))
		{
			ps.setInt(1, _clanId);
			ps.setInt(2, pledgeType);
			ps.setString(3, subPledgeName);
			ps.setInt(4, pledgeType != -1 ? leaderId : 0);
			ps.execute();
			
			subPledge = new SubPledge(pledgeType, subPledgeName, leaderId);
			_subPledges.put(pledgeType, subPledge);
			
			if (pledgeType != -1)
			{
				// Royal Guard 5000 points per each
				// Order of Knights 10000 points per each
				if (pledgeType < SUBUNIT_KNIGHT1)
				{
					takeReputationScore(FeatureConfig.ROYAL_GUARD_COST);
				}
				else
				{
					takeReputationScore(FeatureConfig.KNIGHT_UNIT_COST);
					// TODO: clan lvl9 or more can reinforce knights cheaper if first knight unit already created, use Config.KNIGHT_REINFORCE_COST
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error saving sub clan data: " + e.getMessage(), e);
		}
		
		broadcastToOnlineMembers(new PledgeShowInfoUpdate(_leader.getClan()));
		broadcastToOnlineMembers(new PledgeReceiveSubPledgeCreated(subPledge, _leader.getClan()));
		return subPledge;
	}
	
	public int getAvailablePledgeTypes(int pledgeType)
	{
		if (_subPledges.get(pledgeType) != null)
		{
			// LOGGER.warning("found sub-unit with id: "+pledgeType);
			switch (pledgeType)
			{
				case SUBUNIT_ACADEMY:
				{
					return 0;
				}
				case SUBUNIT_ROYAL1:
				{
					return getAvailablePledgeTypes(SUBUNIT_ROYAL2);
				}
				case SUBUNIT_ROYAL2:
				{
					return 0;
				}
				case SUBUNIT_KNIGHT1:
				{
					return getAvailablePledgeTypes(SUBUNIT_KNIGHT2);
				}
				case SUBUNIT_KNIGHT2:
				{
					return getAvailablePledgeTypes(SUBUNIT_KNIGHT3);
				}
				case SUBUNIT_KNIGHT3:
				{
					return getAvailablePledgeTypes(SUBUNIT_KNIGHT4);
				}
				case SUBUNIT_KNIGHT4:
				{
					return 0;
				}
			}
		}
		
		return pledgeType;
	}
	
	public void updateSubPledgeInDB(int pledgeType)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_subpledges SET leader_id=?, name=? WHERE clan_id=? AND sub_pledge_id=?"))
		{
			ps.setInt(1, getSubPledge(pledgeType).getLeaderId());
			ps.setString(2, getSubPledge(pledgeType).getName());
			ps.setInt(3, _clanId);
			ps.setInt(4, pledgeType);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error updating subpledge: " + e.getMessage(), e);
		}
	}
	
	private void restoreRankPrivs()
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT privs,`rank`,party FROM clan_privs WHERE clan_id=?"))
		{
			// Retrieve all skills of this Player from the database
			ps.setInt(1, _clanId);
			// LOGGER.warning("clanPrivs restore for ClanId : "+getClanId());
			try (ResultSet rset = ps.executeQuery())
			{
				// Go though the recordset of this SQL query
				while (rset.next())
				{
					final int rank = rset.getInt("rank");
					
					// int party = rset.getInt("party");
					final int privileges = rset.getInt("privs");
					
					// Create a SubPledge object for each record
					if (rank == -1)
					{
						continue;
					}
					
					_privs.get(rank).setPrivs(privileges);
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.log(Level.SEVERE, "Error restoring clan privs by rank: " + e.getMessage(), e);
		}
	}
	
	public void initializePrivs()
	{
		for (int i = 1; i < 10; i++)
		{
			_privs.put(i, new RankPrivs(i, 0, new ClanPrivileges()));
		}
	}
	
	public ClanPrivileges getRankPrivs(int rank)
	{
		return _privs.get(rank) != null ? _privs.get(rank).getPrivs() : new ClanPrivileges();
	}
	
	public void setRankPrivs(int rank, int privs)
	{
		if (_privs.get(rank) != null)
		{
			_privs.get(rank).setPrivs(privs);
			
			try (Connection con = DatabaseFactory.getConnection();
				PreparedStatement ps = con.prepareStatement("REPLACE INTO clan_privs (clan_id,`rank`,party,privs) VALUES (?,?,?,?)"))
			{
				// Retrieve all skills of this Player from the database
				ps.setInt(1, _clanId);
				ps.setInt(2, rank);
				ps.setInt(3, 0);
				ps.setInt(4, privs);
				ps.execute();
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Could not store clan privs for rank: " + e.getMessage(), e);
			}
			
			for (ClanMember cm : getMembers())
			{
				if (cm.isOnline() && (cm.getPowerGrade() == rank) && (cm.getPlayer() != null))
				{
					cm.getPlayer().getClanPrivileges().setMask(privs);
					cm.getPlayer().updateUserInfo();
				}
			}
			
			broadcastClanStatus();
		}
		else
		{
			_privs.put(rank, new RankPrivs(rank, 0, privs));
			
			try (Connection con = DatabaseFactory.getConnection();
				PreparedStatement ps = con.prepareStatement("REPLACE INTO clan_privs (clan_id,`rank`,party,privs) VALUES (?,?,?,?)"))
			{
				// Retrieve all skills of this Player from the database
				ps.setInt(1, _clanId);
				ps.setInt(2, rank);
				ps.setInt(3, 0);
				ps.setInt(4, privs);
				ps.execute();
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Could not create new rank and store clan privs for rank: " + e.getMessage(), e);
			}
		}
	}
	
	/**
	 * @return all RankPrivs.
	 */
	public Collection<RankPrivs> getAllRankPrivs()
	{
		return _privs.values();
	}
	
	public int getLeaderSubPledge(int leaderId)
	{
		int id = 0;
		for (SubPledge sp : _subPledges.values())
		{
			if (sp.getLeaderId() == 0)
			{
				continue;
			}
			
			if (sp.getLeaderId() == leaderId)
			{
				id = sp.getId();
			}
		}
		
		return id;
	}
	
	public synchronized void addReputationScore(int value)
	{
		changeReputation(value);
	}
	
	public synchronized void takeReputationScore(int value)
	{
		changeReputation(-(long) value);
	}

	private void changeReputation(long delta)
	{
		if (_reputationIfCommitted != null)
		{
			_reputationIfCommitted = boundedReputation(_reputationIfCommitted + delta);
		}
		setReputationScore(boundedReputation(_reputationScore + delta));
	}

	private static int boundedReputation(long score)
	{
		return (int) Math.clamp(score, -100_000_000L, 100_000_000L);
	}

	int storedReputationScore() { return _storedReputationScore; }

	/** The caller holds the clan monitor. Keep both outcomes for concurrent native awards and losses. */
	void beginReputationResolution(int before, int after)
	{
		if (_reputationIfCommitted != null || _reputationScore != before)
		{
			throw new IllegalStateException("Clan reputation preimage changed before recovery.");
		}
		_reputationIfCommitted = after;
	}

	void resolveReputationPayment(boolean applied, int durableScore)
	{
		final int published = reputationForResolution(applied, durableScore);
		_reputationIfCommitted = null;
		_storedReputationScore = durableScore;
		if (published != _reputationScore) { setReputationScore(published); }
	}

	int reputationForResolution(boolean applied, int durableScore)
	{
		if (!applied) { return _reputationScore; }
		return _reputationIfCommitted == null ? durableScore : _reputationIfCommitted;
	}

	/** Serializes native rep-priced human operations with clan development and leadership changes. */
	public boolean withReputationSpending(Player player, java.util.function.BooleanSupplier spend)
	{
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			synchronized (player)
			{
				synchronized (this)
				{
					return !fence.isRetiring(_clanId) && !_firstLevelPaymentPending && player.getClan() == this
						&& getLeaderId() == player.getObjectId() && spend.getAsBoolean();
				}
			}
		});
	}

	private void setReputationScore(int value)
	{
		final boolean wasDisabled = _reputationScore < 0;
		_reputationScore = boundedReputation(value);
		final boolean disabled = _reputationScore < 0;
		try
		{
			if (disabled != wasDisabled)
			{
				for (ClanMember member : _members.values())
				{
					final Player player = member.getPlayer();
					if (player != null && player.isOnline()) { skillsStatus(player, disabled); }
				}
				broadcastToOnlineMembers(new SystemMessage(disabled
					? SystemMessageId.SINCE_THE_CLAN_REPUTATION_SCORE_HAS_DROPPED_TO_0_OR_LOWER_YOUR_CLAN_SKILL_S_WILL_BE_DE_ACTIVATED
					: SystemMessageId.CLAN_SKILLS_WILL_NOW_BE_ACTIVATED_SINCE_THE_CLAN_S_REPUTATION_SCORE_IS_0_OR_HIGHER));
			}
			broadcastToOnlineMembers(new PledgeShowInfoUpdate(this));
		}
		catch (RuntimeException publicationFailure)
		{
			LOGGER.log(Level.WARNING, "Could not publish clan reputation for clan " + _clanId, publicationFailure);
		}
	}
	
	public int getReputationScore()
	{
		return _reputationScore;
	}
	
	public synchronized void setRank(int rank)
	{
		_rank = rank;
	}
	
	public int getRank()
	{
		return _rank;
	}
	
	public int getAuctionBiddedAt()
	{
		return _auctionBiddedAt;
	}
	
	public void setAuctionBiddedAt(int id, boolean storeInDb)
	{
		_auctionBiddedAt = id;
		if (storeInDb)
		{
			try (Connection con = DatabaseFactory.getConnection();
				PreparedStatement ps = con.prepareStatement("UPDATE clan_data SET auction_bid_at=? WHERE clan_id=?"))
			{
				ps.setInt(1, id);
				ps.setInt(2, _clanId);
				ps.execute();
			}
			catch (Exception e)
			{
				LOGGER.log(Level.WARNING, "Could not store auction for clan: " + e.getMessage(), e);
			}
		}
	}
	
	/**
	 * @param player the clan inviting player.
	 * @param target the invited player.
	 * @param pledgeType the pledge type to join.
	 * @return {core true} if player and target meet various conditions to join a clan.
	 */
	public boolean checkClanJoinCondition(Player player, Player target, int pledgeType)
	{
		if (player == null)
		{
			return false;
		}
		
		if (!player.hasAccess(ClanAccess.INVITE_MEMBER))
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return false;
		}
		
		if (target == null)
		{
			player.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return false;
		}
		
		if (player.getObjectId() == target.getObjectId())
		{
			player.sendPacket(SystemMessageId.YOU_CANNOT_ASK_YOURSELF_TO_APPLY_TO_A_CLAN);
			return false;
		}
		
		if (_charPenaltyExpiryTime > System.currentTimeMillis())
		{
			player.sendPacket(SystemMessageId.AFTER_A_CLAN_MEMBER_IS_DISMISSED_FROM_A_CLAN_THE_CLAN_MUST_WAIT_AT_LEAST_A_DAY_BEFORE_ACCEPTING_A_NEW_MEMBER);
			return false;
		}
		
		if (target.getClanId() != 0)
		{
			final SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_ALREADY_A_MEMBER_OF_ANOTHER_CLAN);
			sm.addString(target.getName());
			player.sendPacket(sm);
			return false;
		}
		
		if (target.getClanJoinExpiryTime() > System.currentTimeMillis())
		{
			final SystemMessage sm = new SystemMessage(SystemMessageId.C1_CANNOT_JOIN_THE_CLAN_BECAUSE_ONE_DAY_HAS_NOT_YET_PASSED_SINCE_THEY_LEFT_ANOTHER_CLAN);
			sm.addString(target.getName());
			player.sendPacket(sm);
			return false;
		}
		
		if (((target.getLevel() > 40) || (target.getPlayerClass().level() >= 2)) && (pledgeType == -1))
		{
			final SystemMessage sm = new SystemMessage(SystemMessageId.S1_DOES_NOT_MEET_THE_REQUIREMENTS_TO_JOIN_A_CLAN_ACADEMY);
			sm.addString(target.getName());
			player.sendPacket(sm);
			player.sendPacket(SystemMessageId.TO_JOIN_A_CLAN_ACADEMY_CHARACTERS_MUST_BE_LEVEL_40_OR_BELOW_NOT_BELONG_ANOTHER_CLAN_AND_NOT_YET_COMPLETED_THEIR_2ND_CLASS_TRANSFER);
			return false;
		}
		
		if (getSubPledgeMembersCount(pledgeType) >= getMaxNrOfMembers(pledgeType))
		{
			if (pledgeType == 0)
			{
				final SystemMessage sm = new SystemMessage(SystemMessageId.S1_IS_FULL_AND_CANNOT_ACCEPT_ADDITIONAL_CLAN_MEMBERS_AT_THIS_TIME);
				sm.addString(_name);
				player.sendPacket(sm);
			}
			else
			{
				player.sendPacket(SystemMessageId.THE_ACADEMY_ROYAL_GUARD_ORDER_OF_KNIGHTS_IS_FULL_AND_CANNOT_ACCEPT_NEW_MEMBERS_AT_THIS_TIME);
			}
			
			return false;
		}
		
		return true;
	}
	
	public long getAllyPenaltyExpiryTime()
	{
		return _allyPenaltyExpiryTime;
	}
	
	public int getAllyPenaltyType()
	{
		return _allyPenaltyType;
	}
	
	void setAllyPenaltyExpiryTime(long expiryTime, int penaltyType)
	{
		_allyPenaltyExpiryTime = expiryTime;
		_allyPenaltyType = penaltyType;
	}
	
	public long getCharPenaltyExpiryTime()
	{
		return _charPenaltyExpiryTime;
	}
	
	public void setCharPenaltyExpiryTime(long time)
	{
		_charPenaltyExpiryTime = time;
	}
	
	public long getDissolvingExpiryTime()
	{
		return _dissolvingExpiryTime;
	}
	
	public void setDissolvingExpiryTime(long time)
	{
		_dissolvingExpiryTime = time;
	}
	
	private volatile boolean _firstLevelPaymentPending;

	public boolean firstLevelPaymentPending()
	{
		return _firstLevelPaymentPending;
	}

	void setFirstLevelPaymentPending(boolean pending)
	{
		_firstLevelPaymentPending = pending;
	}

	public boolean levelUpClan(Player player)
	{
		final ClanSocialMutationFence fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			synchronized (player)
			{
				synchronized (this)
				{
					return !fence.isRetiring(_clanId) && !_firstLevelPaymentPending && player.getPendingClanDevelopment() == null && levelUpClanLocked(player);
				}
			}
		});
	}

	public record LevelRequirement(int currentLevel, long adena, long sp, int itemId, long itemCount, int reputation, int members, boolean territory)
	{
		public int nextLevel() { return currentLevel + 1; }
	}

	/** The stock fee and eligibility rules shared by the village master and clan planning. */
	public static LevelRequirement levelRequirement(int currentLevel)
	{
		return switch (currentLevel)
		{
			case 0 -> new LevelRequirement(0, ClanFoundingDevelopment.ADENA_COST, ClanFoundingDevelopment.SP_COST, 0, 0, 0, 0, false);
			case 1 -> new LevelRequirement(1, 2_500_000, 100_000, 0, 0, 0, 0, false);
			case 2 -> new LevelRequirement(2, 0, 350_000, 1419, 1, 0, 0, false);
			case 3 -> new LevelRequirement(3, 0, 1_000_000, 3874, 1, 0, 0, false);
			case 4 -> new LevelRequirement(4, 0, 2_500_000, 3870, 1, 0, 0, false);
			case 5 -> new LevelRequirement(5, 0, 0, 0, 0, FeatureConfig.CLAN_LEVEL_6_COST, FeatureConfig.CLAN_LEVEL_6_REQUIREMENT, false);
			case 6 -> new LevelRequirement(6, 0, 0, 0, 0, FeatureConfig.CLAN_LEVEL_7_COST, FeatureConfig.CLAN_LEVEL_7_REQUIREMENT, false);
			case 7 -> new LevelRequirement(7, 0, 0, 0, 0, FeatureConfig.CLAN_LEVEL_8_COST, FeatureConfig.CLAN_LEVEL_8_REQUIREMENT, false);
			case 8 -> new LevelRequirement(8, 0, 0, 9910, 150, FeatureConfig.CLAN_LEVEL_9_COST, FeatureConfig.CLAN_LEVEL_9_REQUIREMENT, false);
			case 9 -> new LevelRequirement(9, 0, 0, 9911, 5, FeatureConfig.CLAN_LEVEL_10_COST, FeatureConfig.CLAN_LEVEL_10_REQUIREMENT, false);
			case 10 -> new LevelRequirement(10, 0, 0, 0, 0, FeatureConfig.CLAN_LEVEL_11_COST, FeatureConfig.CLAN_LEVEL_11_REQUIREMENT, true);
			default -> null;
		};
	}

	public boolean ownsTerritory()
	{
		for (Territory territory : TerritoryWarManager.getInstance().getAllTerritories())
		{
			final Clan owner = territory.getOwnerClan();
			if (owner != null && owner.getId() == getId()) { return true; }
		}
		return false;
	}

	boolean ownsTerritory(int castleId)
	{
		if (castleId <= 0 || _castleId != castleId) { return false; }
		final var territory = TerritoryWarManager.getInstance().getTerritory(castleId);
		return territory != null && territory.getOwnerClan() == this;
	}

	private boolean levelUpClanLocked(Player player)
	{
		if ((player.getClan() != this) || !player.isClanLeader())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return false;
		}
		
		if (System.currentTimeMillis() < _dissolvingExpiryTime)
		{
			player.sendPacket(SystemMessageId.AS_YOU_ARE_CURRENTLY_SCHEDULE_FOR_CLAN_DISSOLUTION_YOUR_CLAN_LEVEL_CANNOT_BE_INCREASED);
			return false;
		}
		
		final LevelRequirement requirement = levelRequirement(_level);
		if (requirement == null) { return false; }
		if ((requirement.territory() && !ownsTerritory()) || getMembersCount() < requirement.members()
			|| (_level >= 5 && _reputationScore < requirement.reputation())
			|| (requirement.sp() > 0 && player.getSp() < requirement.sp())
			|| (requirement.adena() > 0 && (player.getAdena() < requirement.adena()
				|| !player.reduceAdena(ItemProcessType.FEE, requirement.adena(), player.getTarget(), true)))
			|| (requirement.itemId() > 0 && (player.getInventory().getItemByItemId(requirement.itemId()) == null
				|| !player.destroyItemByItemId(ItemProcessType.FEE, requirement.itemId(), requirement.itemCount(), player.getTarget(), false))))
		{
			player.sendPacket(SystemMessageId.THE_CONDITIONS_NECESSARY_TO_INCREASE_THE_CLAN_S_LEVEL_HAVE_NOT_BEEN_MET);
			return false;
		}

		if (requirement.sp() > 0)
		{
			player.setSp(player.getSp() - requirement.sp());
			player.sendPacket(new SystemMessage(SystemMessageId.YOUR_SP_HAS_DECREASED_BY_S1).addInt((int) requirement.sp()));
		}
		if (_level >= 5)
		{
			takeReputationScore(requirement.reputation());
			player.sendPacket(new SystemMessage(SystemMessageId.S1_POINTS_HAVE_BEEN_DEDUCTED_FROM_THE_CLAN_S_REPUTATION_SCORE).addInt(requirement.reputation()));
		}
		if (requirement.itemId() > 0)
		{
			final boolean reputationTier = _level >= 8;
			final SystemMessage message = new SystemMessage(reputationTier ? SystemMessageId.S2_S1_HAS_DISAPPEARED : SystemMessageId.S1_HAS_DISAPPEARED);
			message.addItemName(requirement.itemId());
			if (reputationTier) { message.addLong(requirement.itemCount()); }
			player.sendPacket(message);
		}
		// the player should know that he has less sp now :p
		final StatusUpdate su = new StatusUpdate(player);
		su.addAttribute(StatusUpdate.SP, (int) player.getSp());
		player.sendPacket(su);
		
		player.sendItemList(false);
		changeLevelLocked(_level + 1);
		notifyClanLevelUp(player);
		return true;
	}

	void publishPaidLevel(Player player, LevelRequirement requirement)
	{
		if (_level == requirement.currentLevel())
		{
			publishLevel(requirement.nextLevel());
			notifyClanLevelUp(player);
		}
	}
		
	private void notifyClanLevelUp(Player player)
	{
		if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CLAN_LEVELUP))
		{
			EventDispatcher.getInstance().notifyEventAsync(new OnPlayerClanLvlUp(player, this));
		}
	}
	
	public void changeLevel(int level)
	{
		final ClanSocialMutationFence fence = ClanSocialMutationFence.getInstance();
		fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			if (!fence.isRetiring(_clanId) && !_firstLevelPaymentPending)
			{
				changeLevelLocked(level);
			}
			return null;
		});
	}

	private void changeLevelLocked(int level)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_data SET clan_level = ? WHERE clan_id = ?"))
		{
			ps.setInt(1, level);
			ps.setInt(2, _clanId);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.log(Level.WARNING, "could not increase clan level:" + e.getMessage(), e);
		}
		
		publishLevel(level);
	}

	private void publishLevel(int level)
	{
		setLevel(level);
		setRank(ClanTable.getInstance().getClanRank(this));
		
		if (_leader.isOnline())
		{
			final Player leader = _leader.getPlayer();
			if (level > 4)
			{
				SiegeManager.getInstance().addSiegeSkills(leader);
				leader.sendPacket(SystemMessageId.NOW_THAT_YOUR_CLAN_LEVEL_IS_ABOVE_LEVEL_5_IT_CAN_ACCUMULATE_CLAN_REPUTATION_POINTS);
			}
			else if (level < 5)
			{
				SiegeManager.getInstance().removeSiegeSkills(leader);
			}
		}
		
		// notify all the members about it
		broadcastToOnlineMembers(new SystemMessage(SystemMessageId.YOUR_CLAN_S_LEVEL_HAS_INCREASED));
		broadcastToOnlineMembers(new PledgeShowInfoUpdate(this));
	}
	
	/**
	 * Change the clan crest. If crest id is 0, crest is removed. New crest id is saved to database.
	 * @param crestId if 0, crest is removed, else new crest id is set and saved to database
	 */
	public void changeClanCrest(int crestId)
	{
		if (_crestId != 0)
		{
			CrestTable.getInstance().removeCrest(getCrestId());
		}
		
		setCrestId(crestId);
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_data SET crest_id = ? WHERE clan_id = ?"))
		{
			ps.setInt(1, crestId);
			ps.setInt(2, _clanId);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not update crest for clan " + _name + " [" + _clanId + "] : " + e.getMessage(), e);
		}
		
		for (Player member : getOnlineMembers(0))
		{
			member.broadcastUserInfo();
		}
	}
	
	/**
	 * Change the large crest. If crest id is 0, crest is removed. New crest id is saved to database.
	 * @param crestId if 0, crest is removed, else new crest id is set and saved to database
	 */
	public void changeLargeCrest(int crestId)
	{
		if (_crestLargeId != 0)
		{
			CrestTable.getInstance().removeCrest(getCrestLargeId());
		}
		
		setCrestLargeId(crestId);
		
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement ps = con.prepareStatement("UPDATE clan_data SET crest_large_id = ? WHERE clan_id = ?"))
		{
			ps.setInt(1, crestId);
			ps.setInt(2, _clanId);
			ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Could not update large crest for clan " + _name + " [" + _clanId + "] : " + e.getMessage(), e);
		}
		
		for (Player member : getOnlineMembers(0))
		{
			member.broadcastUserInfo();
		}
	}
	
	/**
	 * Check if this clan can learn the skill for the given skill ID, level.
	 * @param skillId
	 * @param skillLevel
	 * @return {@code true} if skill can be learned.
	 */
	public boolean isLearnableSubSkill(int skillId, int skillLevel)
	{
		Skill current = _subPledgeSkills.get(skillId);
		
		// is next level?
		if ((current != null) && ((current.getLevel() + 1) == skillLevel))
		{
			return true;
		}
		
		// is first level?
		if ((current == null) && (skillLevel == 1))
		{
			return true;
		}
		
		// other sub-pledges
		for (SubPledge subunit : _subPledges.values())
		{
			// disable academy
			if (subunit.getId() == -1)
			{
				continue;
			}
			
			current = subunit.getSkill(skillId);
			
			// is next level?
			if ((current != null) && ((current.getLevel() + 1) == skillLevel))
			{
				return true;
			}
			
			// is first level?
			if ((current == null) && (skillLevel == 1))
			{
				return true;
			}
		}
		
		return false;
	}
	
	public boolean isLearnableSubPledgeSkill(Skill skill, int subType)
	{
		// academy
		if (subType == -1)
		{
			return false;
		}
		
		final int id = skill.getId();
		Skill current;
		if (subType == 0)
		{
			current = _subPledgeSkills.get(id);
		}
		else
		{
			current = _subPledges.get(subType).getSkill(id);
		}
		
		// is next level?
		if ((current != null) && ((current.getLevel() + 1) == skill.getLevel()))
		{
			return true;
		}
		
		// is first level?
		if ((current == null) && (skill.getLevel() == 1))
		{
			return true;
		}
		
		return false;
	}
	
	public Collection<SubPledgeSkill> getAllSubSkills()
	{
		final List<SubPledgeSkill> list = new LinkedList<>();
		for (Skill skill : _subPledgeSkills.values())
		{
			list.add(new SubPledgeSkill(0, skill.getId(), skill.getLevel()));
		}
		
		for (SubPledge subunit : _subPledges.values())
		{
			for (Skill skill : subunit.getSkills())
			{
				list.add(new SubPledgeSkill(subunit.getId(), skill.getId(), skill.getLevel()));
			}
		}
		
		return list;
	}
	
	public boolean setNewLeaderId(int objectId, boolean storeInDb)
	{
		if (!storeInDb)
		{
			_newLeaderId = objectId;
			return true;
		}
		final var fence = ClanSocialMutationFence.getInstance();
		return fence.execute(new long[] { ClanSocialMutationFence.clanKey(_clanId) }, () ->
		{
			if (objectId != 0)
			{
				final ClanMember member = _members.get(objectId);
				if (fence.isRetiring(_clanId) || _newLeaderId != 0 || member == null
					|| !ClanDepartureService.settleMember(member) || _members.get(objectId) != member) { return false; }
			}
			_newLeaderId = objectId;
			updateClanInDB();
			return true;
		});
	}
	
	public int getNewLeaderId()
	{
		return _newLeaderId;
	}
	
	public Player getNewLeader()
	{
		return World.getInstance().getPlayer(_newLeaderId);
	}
	
	public String getNewLeaderName()
	{
		return CharInfoTable.getInstance().getNameById(_newLeaderId);
	}
	
	public int getSiegeKills()
	{
		return _siegeKills.get();
	}
	
	public int getSiegeDeaths()
	{
		return _siegeDeaths.get();
	}
	
	public int addSiegeKill()
	{
		return _siegeKills.incrementAndGet();
	}
	
	public int addSiegeDeath()
	{
		return _siegeDeaths.incrementAndGet();
	}
	
	public void clearSiegeKills()
	{
		_siegeKills.set(0);
	}
	
	public void clearSiegeDeaths()
	{
		_siegeDeaths.set(0);
	}
}

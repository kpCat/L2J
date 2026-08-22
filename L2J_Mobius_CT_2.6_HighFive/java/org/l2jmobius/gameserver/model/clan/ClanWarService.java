/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.clan.OnClanWarFinish;
import org.l2jmobius.gameserver.model.events.holders.clan.OnClanWarStart;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager;

/**
 * Canonical transport-neutral owner of native directed clan-war lifecycle.
 */
public final class ClanWarService
{
	private static final Logger LOGGER = Logger.getLogger(ClanWarService.class.getName());

	public enum Status
	{
		SUCCESS,
		INELIGIBLE,
		STALE,
		PERSISTENCE_FAILURE
	}

	public enum Reason
	{
		NONE,
		ACTOR_NOT_FOUND,
		SOURCE_CLAN_NOT_FOUND,
		SOURCE_REQUIREMENTS,
		NOT_AUTHORIZED,
		TARGET_NOT_FOUND,
		SELF_TARGET,
		ALLIED_TARGET,
		TARGET_REQUIREMENTS,
		TARGET_DISSOLVING,
		CLAN_RETIRING,
		ALREADY_ACTIVE,
		NOT_AT_WAR,
		ATTACK_STANCE,
		CONCURRENT_CHANGE,
		STALE_IDENTITY,
		PERSISTENCE_ERROR
	}

	public record WarIdentity(long warId, int sourceClanId, int targetClanId, String sourceClanName, String targetClanName)
	{
		public WarIdentity
		{
			if ((warId <= 0) || (sourceClanId <= 0) || (targetClanId <= 0) || (sourceClanId == targetClanId) || (sourceClanName == null) || sourceClanName.isBlank() || (targetClanName == null) || targetClanName.isBlank())
			{
				throw new IllegalArgumentException("Invalid clan war incarnation identity.");
			}
		}
	}

	public record Result(Status status, Reason reason, WarIdentity identity)
	{
		public Result
		{
			Objects.requireNonNull(status);
			Objects.requireNonNull(reason);
		}

		public boolean successful()
		{
			return status == Status.SUCCESS;
		}
	}

	record Actor(int objectId, int clanId, boolean warDeclarationAccess)
	{
	}

	record ClanSnapshot(int clanId, String clanName, int level, int memberCount, int allianceId, long dissolvingExpiryTime)
	{
	}

	record WarPair(int sourceClanId, int targetClanId)
	{
		WarPair
		{
			if ((sourceClanId <= 0) || (targetClanId <= 0) || (sourceClanId == targetClanId))
			{
				throw new IllegalArgumentException("Invalid directed clan war pair.");
			}
		}
	}

	interface StateAccess
	{
		ClanSnapshot clan(int clanId);

		ClanSnapshot clanByName(String clanName);

		boolean sourceAtWarWith(int sourceClanId, int targetClanId);

		boolean hasAttackStance(int clanId);

		void startWar(WarIdentity identity);

		void notifyWarStarted(WarIdentity identity);

		void endWar(WarIdentity identity);

		void notifyWarEnded(WarIdentity identity, boolean announce);

		void restoreWar(WarIdentity identity);
	}

	private enum EndMode
	{
		STOP,
		SURRENDER,
		ACCEPTED_REPLY
	}

	private static final ClanWarService INSTANCE = new ClanWarService(ClanSocialRepository.getInstance(), new LiveStateAccess(null), ClanSocialMutationFence.getInstance(), System::currentTimeMillis);
	private final ClanSocialPersistence _persistence;
	private final StateAccess _state;
	private final ClanSocialMutationFence _fence;
	private final LongSupplier _clock;
	private final Map<WarPair, WarIdentity> _wars = new ConcurrentHashMap<>();

	private ClanWarService(ClanSocialPersistence persistence, StateAccess state, ClanSocialMutationFence fence, LongSupplier clock)
	{
		_persistence = Objects.requireNonNull(persistence);
		_state = Objects.requireNonNull(state);
		_fence = Objects.requireNonNull(fence);
		_clock = Objects.requireNonNull(clock);
	}

	ClanWarService(ClanSocialPersistence persistence, StateAccess state, ClanSocialMutationFence fence, LongSupplier clock, boolean testing)
	{
		this(persistence, state, fence, clock);
	}

	public static ClanWarService getInstance()
	{
		return INSTANCE;
	}
	public Optional<WarIdentity> currentWar(Clan sourceClan, Clan targetClan)
	{
		if ((sourceClan == null) || (targetClan == null) || (sourceClan.getId() == targetClan.getId()))
		{
			return Optional.empty();
		}
		return Optional.ofNullable(_wars.get(new WarPair(sourceClan.getId(), targetClan.getId())));
	}

	public Optional<WarIdentity> currentWar(int sourceClanId, int targetClanId)
	{
		if ((sourceClanId <= 0) || (targetClanId <= 0) || (sourceClanId == targetClanId))
		{
			return Optional.empty();
		}
		return Optional.ofNullable(_wars.get(new WarPair(sourceClanId, targetClanId)));
	}

	public Result declare(Player player, String targetClanName)
	{
		return declare(actor(player), targetClanName);
	}

	public Result declare(Player player, Clan targetClan)
	{
		return declare(actor(player), targetClan == null ? null : targetClan.getName());
	}

	Result declare(Actor actor, String targetClanName)
	{
		final ClanSnapshot observedTarget = targetClanName == null ? null : _state.clanByName(targetClanName);
		final int targetClanId = observedTarget == null ? 0 : observedTarget.clanId();
		final long[] keys =
		{
			ClanSocialMutationFence.clanKey(actor.clanId()),
			ClanSocialMutationFence.clanKey(targetClanId)
		};
		return _fence.execute(keys, () -> declareLocked(actor, targetClanName, targetClanId));
	}

	private Result declareLocked(Actor actor, String targetClanName, int expectedTargetClanId)
	{
		if (_fence.isRetiring(actor.clanId()) || _fence.isRetiring(expectedTargetClanId))
		{
			return ineligible(Reason.CLAN_RETIRING);
		}
		if (actor.objectId() <= 0)
		{
			return ineligible(Reason.ACTOR_NOT_FOUND);
		}
		final ClanSnapshot source = _state.clan(actor.clanId());
		if (source == null)
		{
			return ineligible(Reason.SOURCE_CLAN_NOT_FOUND);
		}
		if ((source.level() < 3) || (source.memberCount() < PlayerConfig.ALT_CLAN_MEMBERS_FOR_WAR))
		{
			return ineligible(Reason.SOURCE_REQUIREMENTS);
		}
		if (!actor.warDeclarationAccess())
		{
			return ineligible(Reason.NOT_AUTHORIZED);
		}
		final ClanSnapshot target = targetClanName == null ? null : _state.clanByName(targetClanName);
		if (target == null)
		{
			return ineligible(Reason.TARGET_NOT_FOUND);
		}
		if (target.clanId() != expectedTargetClanId)
		{
			return stale();
		}
		if (source.clanId() == target.clanId())
		{
			return ineligible(Reason.SELF_TARGET);
		}
		if ((source.allianceId() != 0) && (source.allianceId() == target.allianceId()))
		{
			return ineligible(Reason.ALLIED_TARGET);
		}
		if ((target.level() < 3) || (target.memberCount() < PlayerConfig.ALT_CLAN_MEMBERS_FOR_WAR))
		{
			return ineligible(Reason.TARGET_REQUIREMENTS);
		}
		if (target.dissolvingExpiryTime() > _clock.getAsLong())
		{
			return ineligible(Reason.TARGET_DISSOLVING);
		}
		final WarPair pair = new WarPair(source.clanId(), target.clanId());
		if (_wars.containsKey(pair) || _state.sourceAtWarWith(source.clanId(), target.clanId()))
		{
			return ineligible(Reason.ALREADY_ACTIVE);
		}

		return persistWar(source, target, pair);
	}

	public Result declareAcceptedReply(Clan sourceClan, Clan targetClan)
	{
		return declareAcceptedReply(sourceClan == null ? 0 : sourceClan.getId(), targetClan == null ? 0 : targetClan.getId());
	}

	Result declareAcceptedReply(int sourceClanId, int targetClanId)
	{
		final long[] keys =
		{
			ClanSocialMutationFence.clanKey(sourceClanId),
			ClanSocialMutationFence.clanKey(targetClanId)
		};
		return _fence.execute(keys, () ->
		{
			if (_fence.isRetiring(sourceClanId) || _fence.isRetiring(targetClanId))
			{
				return ineligible(Reason.CLAN_RETIRING);
			}
			final ClanSnapshot source = _state.clan(sourceClanId);
			final ClanSnapshot target = _state.clan(targetClanId);
			if (source == null)
			{
				return ineligible(Reason.SOURCE_CLAN_NOT_FOUND);
			}
			if (target == null)
			{
				return ineligible(Reason.TARGET_NOT_FOUND);
			}
			if (sourceClanId == targetClanId)
			{
				return ineligible(Reason.SELF_TARGET);
			}
			final WarPair pair = new WarPair(sourceClanId, targetClanId);
			if (_wars.containsKey(pair) || _state.sourceAtWarWith(sourceClanId, targetClanId))
			{
				return ineligible(Reason.ALREADY_ACTIVE);
			}
			return persistWar(source, target, pair);
		});
	}

	private Result persistWar(ClanSnapshot source, ClanSnapshot target, WarPair pair)
	{
		try
		{
			final ClanSocialRepository.WarRow durable = _persistence.createWar(source.clanId(), target.clanId());
			final WarIdentity identity = new WarIdentity(durable.warId(), durable.sourceClanId(), durable.targetClanId(), source.clanName(), target.clanName());
			_wars.put(pair, identity);
			_state.startWar(identity);
			notifySafely("clan war start", () -> _state.notifyWarStarted(identity));
			return success(identity);
		}
		catch (ClanSocialRepository.StaleStateException e)
		{
			return stale();
		}
		catch (SQLException e)
		{
			return persistenceFailure();
		}
	}
	public Result stop(Player player, Clan targetClan, long expectedWarId)
	{
		return end(actor(player), targetClan == null ? 0 : targetClan.getId(), expectedWarId, EndMode.STOP);
	}

	public Result surrender(Player player, Clan targetClan, long expectedWarId)
	{
		return end(actor(player), targetClan == null ? 0 : targetClan.getId(), expectedWarId, EndMode.SURRENDER);
	}

	public Result endAcceptedReply(Clan sourceClan, Clan targetClan, long expectedWarId)
	{
		final Actor actor = sourceClan == null ? new Actor(0, 0, false) : new Actor(1, sourceClan.getId(), false);
		return end(actor, targetClan == null ? 0 : targetClan.getId(), expectedWarId, EndMode.ACCEPTED_REPLY);
	}

	Result stop(Actor actor, int targetClanId, long expectedWarId)
	{
		return end(actor, targetClanId, expectedWarId, EndMode.STOP);
	}

	Result surrender(Actor actor, int targetClanId, long expectedWarId)
	{
		return end(actor, targetClanId, expectedWarId, EndMode.SURRENDER);
	}

	private Result end(Actor actor, int targetClanId, long expectedWarId, EndMode mode)
	{
		final long[] keys =
		{
			ClanSocialMutationFence.clanKey(actor.clanId()),
			ClanSocialMutationFence.clanKey(targetClanId)
		};
		return _fence.execute(keys, () ->
		{
			if (_fence.isRetiring(actor.clanId()) || _fence.isRetiring(targetClanId))
			{
				return ineligible(Reason.CLAN_RETIRING);
			}
			if (actor.objectId() <= 0)
			{
				return ineligible(Reason.ACTOR_NOT_FOUND);
			}
			final ClanSnapshot source = _state.clan(actor.clanId());
			final ClanSnapshot target = _state.clan(targetClanId);
			if (source == null)
			{
				return ineligible(Reason.SOURCE_CLAN_NOT_FOUND);
			}
			if (target == null)
			{
				return ineligible(Reason.TARGET_NOT_FOUND);
			}
			final WarPair pair = new WarPair(source.clanId(), target.clanId());
			final WarIdentity current = _wars.get(pair);
			if ((current == null) || !_state.sourceAtWarWith(source.clanId(), target.clanId()))
			{
				return ineligible(Reason.NOT_AT_WAR);
			}
			if (current.warId() != expectedWarId)
			{
				return stale();
			}
			if ((mode == EndMode.STOP) && !actor.warDeclarationAccess())
			{
				return ineligible(Reason.NOT_AUTHORIZED);
			}
			if ((mode == EndMode.STOP) && _state.hasAttackStance(source.clanId()))
			{
				return ineligible(Reason.ATTACK_STANCE);
			}
			try
			{
				_persistence.deleteWar(toRow(current));
				_wars.remove(pair, current);
				_state.endWar(current);
				notifySafely("clan war end", () -> _state.notifyWarEnded(current, true));
				return success(current);
			}
			catch (ClanSocialRepository.StaleStateException e)
			{
				return stale();
			}
			catch (SQLException e)
			{
				return persistenceFailure();
			}
		});
	}
	public Result removeAllForClan(ClanSocialMutationFence.Retirement retirement)
	{
		if (retirement == null)
		{
			return stale();
		}
		final int clanId = retirement.clanId();
		for (int attempt = 0; attempt < 3; attempt++)
		{
			final List<WarIdentity> observed = warsForClan(clanId);
			final long[] keys = observed.isEmpty() ? new long[]
			{
				ClanSocialMutationFence.clanKey(clanId)
			} : observed.stream().flatMapToLong(war -> java.util.stream.LongStream.of(ClanSocialMutationFence.clanKey(war.sourceClanId()), ClanSocialMutationFence.clanKey(war.targetClanId()))).distinct().toArray();
			final Result result = _fence.execute(keys, () ->
			{
				if (!_fence.isCurrentRetirement(retirement))
				{
					return stale();
				}
				final List<WarIdentity> current = warsForClan(clanId);
				if (!sameWarIds(observed, current))
				{
					return ineligible(Reason.CONCURRENT_CHANGE);
				}
				try
				{
					_persistence.deleteWars(current.stream().map(ClanWarService::toRow).toList());
					for (WarIdentity war : current)
					{
						_wars.remove(new WarPair(war.sourceClanId(), war.targetClanId()), war);
						_state.endWar(war);
						notifySafely("clan war removal", () -> _state.notifyWarEnded(war, false));
					}
					return success(null);
				}
				catch (ClanSocialRepository.StaleStateException e)
				{
					return stale();
				}
				catch (SQLException e)
				{
					return persistenceFailure();
				}
			});
			if (result.reason() != Reason.CONCURRENT_CHANGE)
			{
				return result;
			}
		}
		return ineligible(Reason.CONCURRENT_CHANGE);
	}
	public Result restoreWars(ClanTable clanTable)
	{
		return restoreWars(new LiveStateAccess(Objects.requireNonNull(clanTable)));
	}

	Result restoreWars()
	{
		return restoreWars(_state);
	}

	private Result restoreWars(StateAccess state)
	{
		final List<ClanSocialRepository.WarRow> rows;
		try
		{
			rows = _persistence.loadWars();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.SEVERE, "Could not restore exact clan war identities.", e);
			return persistenceFailure();
		}
		for (ClanSocialRepository.WarRow row : rows)
		{
			final ClanSnapshot source = state.clan(row.sourceClanId());
			final ClanSnapshot target = state.clan(row.targetClanId());
			if ((source == null) || (target == null) || (source.clanId() == target.clanId()))
			{
				LOGGER.warning("Skipping invalid persisted clan war " + row.warId() + '.');
				continue;
			}
			final WarIdentity identity = new WarIdentity(row.warId(), row.sourceClanId(), row.targetClanId(), source.clanName(), target.clanName());
			final WarPair pair = new WarPair(identity.sourceClanId(), identity.targetClanId());
			final Result result = _fence.execute(new long[]
			{
				ClanSocialMutationFence.clanKey(identity.sourceClanId()),
				ClanSocialMutationFence.clanKey(identity.targetClanId())
			}, () ->
			{
				if (_wars.putIfAbsent(pair, identity) != null)
				{
					return ineligible(Reason.ALREADY_ACTIVE);
				}
				state.restoreWar(identity);
				return success(identity);
			});
			if (!result.successful())
			{
				LOGGER.warning("Duplicate persisted directed clan war pair " + pair + '.');
			}
		}
		return success(null);
	}
	private List<WarIdentity> warsForClan(int clanId)
	{
		return _wars.values().stream().filter(war -> (war.sourceClanId() == clanId) || (war.targetClanId() == clanId)).sorted(Comparator.comparingLong(WarIdentity::warId)).toList();
	}

	private static boolean sameWarIds(Collection<WarIdentity> first, Collection<WarIdentity> second)
	{
		return first.stream().map(WarIdentity::warId).sorted().toList().equals(second.stream().map(WarIdentity::warId).sorted().toList());
	}

	private static ClanSocialRepository.WarRow toRow(WarIdentity identity)
	{
		return new ClanSocialRepository.WarRow(identity.warId(), identity.sourceClanId(), identity.targetClanId());
	}

	private static Actor actor(Player player)
	{
		return player == null ? new Actor(0, 0, false) : new Actor(player.getObjectId(), player.getClanId(), player.hasAccess(ClanAccess.WAR_DECLARATION));
	}

	private static void notifySafely(String operation, Runnable notification)
	{
		try
		{
			notification.run();
		}
		catch (RuntimeException e)
		{
			LOGGER.log(Level.WARNING, "Committed " + operation + " notification failed.", e);
		}
	}

	private static Result success(WarIdentity identity)
	{
		return new Result(Status.SUCCESS, Reason.NONE, identity);
	}

	private static Result ineligible(Reason reason)
	{
		return new Result(Status.INELIGIBLE, reason, null);
	}

	private static Result stale()
	{
		return new Result(Status.STALE, Reason.STALE_IDENTITY, null);
	}

	private static Result persistenceFailure()
	{
		return new Result(Status.PERSISTENCE_FAILURE, Reason.PERSISTENCE_ERROR, null);
	}

	private static final class LiveStateAccess implements StateAccess
	{
		private final ClanTable _clanTable;

		private LiveStateAccess(ClanTable clanTable)
		{
			_clanTable = clanTable;
		}

		private ClanTable table()
		{
			return _clanTable == null ? ClanTable.getInstance() : _clanTable;
		}

		@Override
		public ClanSnapshot clan(int clanId)
		{
			final Clan clan = table().getClan(clanId);
			return clan == null ? null : snapshot(clan);
		}

		@Override
		public ClanSnapshot clanByName(String clanName)
		{
			final Clan clan = table().getClanByName(clanName);
			return clan == null ? null : snapshot(clan);
		}

		@Override
		public boolean sourceAtWarWith(int sourceClanId, int targetClanId)
		{
			final Clan source = table().getClan(sourceClanId);
			return (source != null) && source.isAtWarWith(targetClanId);
		}

		@Override
		public boolean hasAttackStance(int clanId)
		{
			final Clan clan = table().getClan(clanId);
			if (clan == null)
			{
				return false;
			}
			for (ClanMember member : clan.getMembers())
			{
				if ((member != null) && (member.getPlayer() != null) && AttackStanceTaskManager.getInstance().hasAttackStanceTask(member.getPlayer()))
				{
					return true;
				}
			}
			return false;
		}
		@Override
		public void startWar(WarIdentity identity)
		{
			final Clan source = table().getClan(identity.sourceClanId());
			final Clan target = table().getClan(identity.targetClanId());
			if ((source == null) || (target == null))
			{
				return;
			}
			source.setEnemyClan(target);
			target.setAttackerClan(source);
		}

		@Override
		public void notifyWarStarted(WarIdentity identity)
		{
			final Clan source = table().getClan(identity.sourceClanId());
			final Clan target = table().getClan(identity.targetClanId());
			if ((source == null) || (target == null))
			{
				return;
			}
			if (EventDispatcher.getInstance().hasListener(EventType.ON_CLAN_WAR_START))
			{
				EventDispatcher.getInstance().notifyEventAsync(new OnClanWarStart(source, target));
			}
			source.broadcastClanStatus();
			target.broadcastClanStatus();
			SystemMessage message = new SystemMessage(SystemMessageId.A_CLAN_WAR_HAS_BEEN_DECLARED_AGAINST_THE_CLAN_S1_IF_YOU_ARE_KILLED_DURING_THE_CLAN_WAR_BY_MEMBERS_OF_THE_OPPOSING_CLAN_YOU_WILL_ONLY_LOSE_A_QUARTER_OF_THE_NORMAL_EXPERIENCE_FROM_DEATH);
			message.addString(target.getName());
			source.broadcastToOnlineMembers(message);
			message = new SystemMessage(SystemMessageId.S1_HAS_DECLARED_A_CLAN_WAR);
			message.addString(source.getName());
			target.broadcastToOnlineMembers(message);
			broadcastUserInfo(source);
			broadcastUserInfo(target);
		}

		@Override
		public void endWar(WarIdentity identity)
		{
			final Clan source = table().getClan(identity.sourceClanId());
			final Clan target = table().getClan(identity.targetClanId());
			if ((source == null) || (target == null))
			{
				return;
			}
			source.deleteEnemyClan(target);
			target.deleteAttackerClan(source);
		}

		@Override
		public void notifyWarEnded(WarIdentity identity, boolean announce)
		{
			final Clan source = table().getClan(identity.sourceClanId());
			final Clan target = table().getClan(identity.targetClanId());
			if ((source == null) || (target == null))
			{
				return;
			}
			if (announce && EventDispatcher.getInstance().hasListener(EventType.ON_CLAN_WAR_FINISH))
			{
				EventDispatcher.getInstance().notifyEventAsync(new OnClanWarFinish(source, target));
			}
			source.broadcastClanStatus();
			target.broadcastClanStatus();
			if (announce)
			{
				SystemMessage message = new SystemMessage(SystemMessageId.THE_WAR_AGAINST_S1_CLAN_HAS_BEEN_STOPPED);
				message.addString(target.getName());
				source.broadcastToOnlineMembers(message);
				message = new SystemMessage(SystemMessageId.THE_CLAN_S1_HAS_DECIDED_TO_STOP_THE_WAR);
				message.addString(source.getName());
				target.broadcastToOnlineMembers(message);
			}
			broadcastUserInfo(source);
			broadcastUserInfo(target);
		}

		@Override
		public void restoreWar(WarIdentity identity)
		{
			final Clan source = table().getClan(identity.sourceClanId());
			final Clan target = table().getClan(identity.targetClanId());
			if ((source != null) && (target != null))
			{
				source.setEnemyClan(target);
				target.setAttackerClan(source);
			}
		}

		private static void broadcastUserInfo(Clan clan)
		{
			for (Player member : clan.getOnlineMembers(0))
			{
				member.broadcastUserInfo();
			}
		}

		private static ClanSnapshot snapshot(Clan clan)
		{
			return new ClanSnapshot(clan.getId(), clan.getName(), clan.getLevel(), clan.getMembersCount(), clan.getAllyId(), clan.getDissolvingExpiryTime());
		}
	}
}
/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.sql.CrestTable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;

/**
 * Canonical transport-neutral owner of native alliance lifecycle mutations.
 */
public final class ClanAllianceService
{
	private static final Logger LOGGER = Logger.getLogger(ClanAllianceService.class.getName());
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
		CLAN_NOT_FOUND,
		NOT_CLAN_LEADER,
		NOT_ALLIANCE_LEADER,
		ALREADY_ALLIED,
		NOT_ALLIED,
		CLAN_LEVEL_TOO_LOW,
		DISSOLUTION_PENALTY,
		CLAN_DISSOLVING,
		CLAN_RETIRING,
		INVALID_NAME,
		INVALID_NAME_LENGTH,
		NAME_EXISTS,
		LEADER_DISMISS_PENALTY,
		TARGET_NOT_FOUND,
		SELF_TARGET,
		TARGET_NOT_IN_CLAN,
		TARGET_NOT_LEADER,
		TARGET_ALREADY_ALLIED,
		TARGET_LEAVE_PENALTY,
		TARGET_DISMISSED_PENALTY,
		BOTH_IN_SIEGE,
		AT_WAR,
		ALLIANCE_FULL,
		ALLIANCE_LEADER_CANNOT_LEAVE,
		TARGET_IS_ALLIANCE_LEADER,
		DIFFERENT_ALLIANCE,
		ACTOR_IN_SIEGE,
		CONCURRENT_CHANGE,
		STALE_IDENTITY,
		PERSISTENCE_ERROR
	}

	public record AllianceIdentity(int leaderClanId, long generation)
	{
		public AllianceIdentity
		{
			if ((leaderClanId <= 0) || (generation <= 0))
			{
				throw new IllegalArgumentException("Invalid alliance incarnation identity.");
			}
		}
	}

	public record MembershipEpoch(int clanId, int allianceId, long generation, long counter)
	{
		public MembershipEpoch
		{
			if ((clanId <= 0) || (allianceId < 0) || (generation < 0) || (counter < 0) || ((allianceId == 0) != (generation == 0)))
			{
				throw new IllegalArgumentException("Invalid alliance membership epoch.");
			}
		}
	}

	public record Result(Status status, Reason reason, AllianceIdentity identity, MembershipEpoch targetEpoch)
	{
		public Result
		{
			Objects.requireNonNull(status);
			Objects.requireNonNull(reason);
		}

		public Result(Status status, Reason reason, AllianceIdentity identity)
		{
			this(status, reason, identity, null);
		}

		public boolean successful()
		{
			return status == Status.SUCCESS;
		}
	}

	record Actor(int objectId, int clanId, boolean clanLeader, boolean insideSiege)
	{
	}

	record ClanSnapshot(int clanId, String clanName, int level, int allianceId, String allianceName, long allianceGeneration, long allianceGenerationCounter, int allianceCrestId, long alliancePenaltyExpiryTime, int alliancePenaltyType, long dissolvingExpiryTime)
	{
		AllianceIdentity identity()
		{
			return (allianceId > 0) && (allianceGeneration > 0) ? new AllianceIdentity(allianceId, allianceGeneration) : null;
		}
		MembershipEpoch membershipEpoch()
		{
			return new MembershipEpoch(clanId, allianceId, allianceGeneration, allianceGenerationCounter);
		}
	}

	record AllianceState(int clanId, int allianceId, String allianceName, long generation, long generationCounter, int crestId, long penaltyExpiryTime, int penaltyType)
	{
	}

	interface StateAccess
	{
		ClanSnapshot clan(int clanId);

		ClanSnapshot clanByName(String clanName);

		List<ClanSnapshot> allies(int allianceId);

		boolean allianceNameExists(String allianceName);

		boolean atWar(int sourceClanId, int targetClanId);

		void apply(AllianceState state);

		void broadcastUserInfo(int clanId);

		void broadcastDissolved(List<Integer> clanIds);

		void removeCrest(int crestId);
	}

	private static final ClanAllianceService INSTANCE = new ClanAllianceService(ClanSocialRepository.getInstance(), new LiveStateAccess(null), ClanSocialMutationFence.getInstance(), System::currentTimeMillis, PlayerConfig.ALT_MAX_NUM_OF_CLANS_IN_ALLY);
	private final ClanSocialPersistence _persistence;
	private final StateAccess _state;
	private final ClanSocialMutationFence _fence;
	private final LongSupplier _clock;
	private final int _maxAllianceClans;

	private ClanAllianceService(ClanSocialPersistence persistence, StateAccess state, ClanSocialMutationFence fence, LongSupplier clock, int maxAllianceClans)
	{
		_persistence = Objects.requireNonNull(persistence);
		_state = Objects.requireNonNull(state);
		_fence = Objects.requireNonNull(fence);
		_clock = Objects.requireNonNull(clock);
		_maxAllianceClans = maxAllianceClans;
	}

	ClanAllianceService(ClanSocialPersistence persistence, StateAccess state, ClanSocialMutationFence fence, LongSupplier clock, boolean testing)
	{
		this(persistence, state, fence, clock, 3);
	}

	public static ClanAllianceService getInstance()
	{
		return INSTANCE;
	}
	public Optional<AllianceIdentity> currentIdentity(Clan clan)
	{
		if (clan == null)
		{
			return Optional.empty();
		}
		final ClanSnapshot snapshot = _state.clan(clan.getId());
		return Optional.ofNullable(snapshot == null ? null : snapshot.identity());
	}

	public Result create(Player player, String allianceName)
	{
		return create(actor(player), allianceName);
	}

	Result create(Actor actor, String allianceName)
	{
		final String name = allianceName == null ? "" : allianceName;
		final long[] keys =
		{
			ClanSocialMutationFence.clanKey(actor.clanId()),
			ClanSocialMutationFence.allianceNameKey(name)
		};
		return _fence.execute(keys, () -> createLocked(actor, name));
	}

	private Result createLocked(Actor actor, String allianceName)
	{
		if (_fence.isRetiring(actor.clanId()))
		{
			return ineligible(Reason.CLAN_RETIRING);
		}
		if (actor.objectId() <= 0)
		{
			return ineligible(Reason.ACTOR_NOT_FOUND);
		}
		final ClanSnapshot clan = _state.clan(actor.clanId());
		if (clan == null)
		{
			return ineligible(Reason.CLAN_NOT_FOUND);
		}
		if (!actor.clanLeader())
		{
			return ineligible(Reason.NOT_CLAN_LEADER);
		}
		if (clan.allianceId() != 0)
		{
			return ineligible(Reason.ALREADY_ALLIED);
		}
		if (clan.level() < 5)
		{
			return ineligible(Reason.CLAN_LEVEL_TOO_LOW);
		}
		final long now = _clock.getAsLong();
		if ((clan.alliancePenaltyExpiryTime() > now) && (clan.alliancePenaltyType() == Clan.PENALTY_TYPE_DISSOLVE_ALLY))
		{
			return ineligible(Reason.DISSOLUTION_PENALTY);
		}
		if (clan.dissolvingExpiryTime() > now)
		{
			return ineligible(Reason.CLAN_DISSOLVING);
		}
		if (!StringUtil.isAlphaNumeric(allianceName))
		{
			return ineligible(Reason.INVALID_NAME);
		}
		if ((allianceName.length() < 2) || (allianceName.length() > 16))
		{
			return ineligible(Reason.INVALID_NAME_LENGTH);
		}
		if (_state.allianceNameExists(allianceName))
		{
			return ineligible(Reason.NAME_EXISTS);
		}

		try
		{
			final long generation = _persistence.createAlliance(clan.clanId(), clan.allianceGeneration(), clan.allianceGenerationCounter(), allianceName.trim());
			final long nextEpoch = Math.addExact(clan.allianceGenerationCounter(), 1);
			final AllianceIdentity identity = new AllianceIdentity(clan.clanId(), generation);
			_state.apply(new AllianceState(clan.clanId(), clan.clanId(), allianceName.trim(), generation, nextEpoch, clan.allianceCrestId(), 0, 0));
			return success(identity);
		}
		catch (ClanSocialRepository.StaleStateException | ArithmeticException e)
		{
			return stale();
		}
		catch (SQLException e)
		{
			return persistenceFailure();
		}
	}

	public Result checkInvite(Player inviter, Player target)
	{
		return checkInvite(actor(inviter), actor(target));
	}

	Result checkInvite(Actor inviter, Actor target)
	{
		final long[] keys =
		{
			ClanSocialMutationFence.clanKey(inviter.clanId()),
			ClanSocialMutationFence.clanKey(target.clanId())
		};
		return _fence.execute(keys, () -> validateInviteLocked(inviter, target));
	}
	public Result join(Player inviter, Player target, AllianceIdentity expectedIdentity, MembershipEpoch expectedTargetEpoch)
	{
		return join(actor(inviter), actor(target), expectedIdentity, expectedTargetEpoch);
	}

	Result join(Actor inviter, Actor target, AllianceIdentity expectedIdentity, MembershipEpoch expectedTargetEpoch)
	{
		final long[] keys =
		{
			ClanSocialMutationFence.clanKey(inviter.clanId()),
			ClanSocialMutationFence.clanKey(target.clanId())
		};
		return _fence.execute(keys, () ->
		{
			final ClanSnapshot leader = _state.clan(inviter.clanId());
			if ((leader == null) || !Objects.equals(leader.identity(), expectedIdentity))
			{
				return stale();
			}
			final ClanSnapshot targetClan = _state.clan(target.clanId());
			if ((targetClan == null) || !Objects.equals(targetClan.membershipEpoch(), expectedTargetEpoch))
			{
				return stale();
			}
			final Result eligibility = validateInviteLocked(inviter, target);
			if (!eligibility.successful())
			{
				return eligibility;
			}
			try
			{
				final long nextTargetEpoch = Math.addExact(expectedTargetEpoch.counter(), 1);
				_persistence.joinAlliance(leader.clanId(), targetClan.clanId(), leader.allianceId(), leader.allianceGeneration(), leader.allianceName(), leader.allianceCrestId(), expectedTargetEpoch.generation(), expectedTargetEpoch.counter());
				_state.apply(new AllianceState(targetClan.clanId(), leader.allianceId(), leader.allianceName(), leader.allianceGeneration(), nextTargetEpoch, leader.allianceCrestId(), 0, 0));
				notifySafely("alliance join broadcast", () -> _state.broadcastUserInfo(targetClan.clanId()));
				return success(expectedIdentity);
			}
			catch (ClanSocialRepository.StaleStateException | ArithmeticException e)
			{
				return stale();
			}
			catch (SQLException e)
			{
				return persistenceFailure();
			}
		});
	}
	private Result validateInviteLocked(Actor inviter, Actor target)
	{
		if (_fence.isRetiring(inviter.clanId()) || _fence.isRetiring(target.clanId()))
		{
			return ineligible(Reason.CLAN_RETIRING);
		}
		if (inviter.objectId() <= 0)
		{
			return ineligible(Reason.ACTOR_NOT_FOUND);
		}
		final ClanSnapshot leader = _state.clan(inviter.clanId());
		if ((leader == null) || (leader.allianceId() == 0) || !inviter.clanLeader() || (leader.clanId() != leader.allianceId()))
		{
			return ineligible(Reason.NOT_ALLIANCE_LEADER);
		}
		final AllianceIdentity identity = leader.identity();
		if (identity == null)
		{
			return stale();
		}
		if ((leader.alliancePenaltyExpiryTime() > _clock.getAsLong()) && (leader.alliancePenaltyType() == Clan.PENALTY_TYPE_DISMISS_CLAN))
		{
			return ineligible(Reason.LEADER_DISMISS_PENALTY);
		}
		if (target.objectId() <= 0)
		{
			return ineligible(Reason.TARGET_NOT_FOUND);
		}
		if (inviter.objectId() == target.objectId())
		{
			return ineligible(Reason.SELF_TARGET);
		}
		if (target.clanId() <= 0)
		{
			return ineligible(Reason.TARGET_NOT_IN_CLAN);
		}
		final ClanSnapshot targetClan = _state.clan(target.clanId());
		if (targetClan == null)
		{
			return ineligible(Reason.TARGET_NOT_IN_CLAN);
		}
		if (!target.clanLeader())
		{
			return ineligible(Reason.TARGET_NOT_LEADER);
		}
		if (targetClan.allianceId() != 0)
		{
			return ineligible(Reason.TARGET_ALREADY_ALLIED);
		}
		if (targetClan.alliancePenaltyExpiryTime() > _clock.getAsLong())
		{
			if (targetClan.alliancePenaltyType() == Clan.PENALTY_TYPE_CLAN_LEAVED)
			{
				return ineligible(Reason.TARGET_LEAVE_PENALTY);
			}
			if (targetClan.alliancePenaltyType() == Clan.PENALTY_TYPE_CLAN_DISMISSED)
			{
				return ineligible(Reason.TARGET_DISMISSED_PENALTY);
			}
		}
		if (inviter.insideSiege() && target.insideSiege())
		{
			return ineligible(Reason.BOTH_IN_SIEGE);
		}
		if (_state.atWar(leader.clanId(), targetClan.clanId()))
		{
			return ineligible(Reason.AT_WAR);
		}
		if (_state.allies(leader.allianceId()).size() >= _maxAllianceClans)
		{
			return ineligible(Reason.ALLIANCE_FULL);
		}
		return success(identity, targetClan.membershipEpoch());
	}
	public Result leave(Player player, AllianceIdentity expectedIdentity)
	{
		return leave(actor(player), expectedIdentity);
	}

	Result leave(Actor actor, AllianceIdentity expectedIdentity)
	{
		final ClanSnapshot observed = _state.clan(actor.clanId());
		final int observedLeader = observed == null ? 0 : observed.allianceId();
		final long[] keys =
		{
			ClanSocialMutationFence.clanKey(actor.clanId()),
			ClanSocialMutationFence.clanKey(observedLeader)
		};
		return _fence.execute(keys, () ->
		{
			if (_fence.isRetiring(actor.clanId()) || _fence.isRetiring(observedLeader))
			{
				return ineligible(Reason.CLAN_RETIRING);
			}
			if (actor.objectId() <= 0)
			{
				return ineligible(Reason.ACTOR_NOT_FOUND);
			}
			final ClanSnapshot clan = _state.clan(actor.clanId());
			if (clan == null)
			{
				return ineligible(Reason.CLAN_NOT_FOUND);
			}
			if (!actor.clanLeader())
			{
				return ineligible(Reason.NOT_CLAN_LEADER);
			}
			if (clan.allianceId() == 0)
			{
				return ineligible(Reason.NOT_ALLIED);
			}
			if (!Objects.equals(clan.identity(), expectedIdentity))
			{
				return stale();
			}
			if (clan.clanId() == clan.allianceId())
			{
				return ineligible(Reason.ALLIANCE_LEADER_CANNOT_LEAVE);
			}
			final long penalty = _clock.getAsLong() + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_ALLY_JOIN_DAYS_WHEN_LEAVED);
			try
			{
				final long nextEpoch = Math.addExact(clan.allianceGenerationCounter(), 1);
				_persistence.leaveAlliance(clan.clanId(), clan.allianceId(), clan.allianceGeneration(), clan.allianceGenerationCounter(), penalty, Clan.PENALTY_TYPE_CLAN_LEAVED);
				_state.apply(new AllianceState(clan.clanId(), 0, null, 0, nextEpoch, 0, penalty, Clan.PENALTY_TYPE_CLAN_LEAVED));
				notifySafely("alliance leave broadcast", () -> _state.broadcastUserInfo(clan.clanId()));
				return success(expectedIdentity);
			}
			catch (ClanSocialRepository.StaleStateException | ArithmeticException e)
			{
				return stale();
			}
			catch (SQLException e)
			{
				return persistenceFailure();
			}
		});
	}

	public Result expel(Player player, Clan targetClan, AllianceIdentity expectedIdentity)
	{
		return expel(actor(player), targetClan == null ? 0 : targetClan.getId(), expectedIdentity);
	}

	Result expel(Actor actor, int targetClanId, AllianceIdentity expectedIdentity)
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
			final ClanSnapshot leader = _state.clan(actor.clanId());
			if (leader == null)
			{
				return ineligible(Reason.CLAN_NOT_FOUND);
			}
			if (leader.allianceId() == 0)
			{
				return ineligible(Reason.NOT_ALLIED);
			}
			if (!actor.clanLeader() || (leader.clanId() != leader.allianceId()))
			{
				return ineligible(Reason.NOT_ALLIANCE_LEADER);
			}
			if (!Objects.equals(leader.identity(), expectedIdentity))
			{
				return stale();
			}
			final ClanSnapshot target = _state.clan(targetClanId);
			if (target == null)
			{
				return ineligible(Reason.TARGET_NOT_FOUND);
			}
			if (target.clanId() == leader.clanId())
			{
				return ineligible(Reason.TARGET_IS_ALLIANCE_LEADER);
			}
			if (!Objects.equals(target.identity(), expectedIdentity))
			{
				return ineligible(Reason.DIFFERENT_ALLIANCE);
			}
			final long now = _clock.getAsLong();
			final long leaderPenalty = now + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_ACCEPT_CLAN_DAYS_WHEN_DISMISSED);
			final long targetPenalty = now + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_ALLY_JOIN_DAYS_WHEN_DISMISSED);
			try
			{
				final long nextTargetEpoch = Math.addExact(target.allianceGenerationCounter(), 1);
				_persistence.expelAlliance(leader.clanId(), target.clanId(), leader.allianceId(), leader.allianceGeneration(), leader.allianceGenerationCounter(), target.allianceGenerationCounter(), leaderPenalty, targetPenalty, Clan.PENALTY_TYPE_CLAN_DISMISSED);
				_state.apply(new AllianceState(leader.clanId(), leader.allianceId(), leader.allianceName(), leader.allianceGeneration(), leader.allianceGenerationCounter(), leader.allianceCrestId(), leaderPenalty, Clan.PENALTY_TYPE_DISMISS_CLAN));
				_state.apply(new AllianceState(target.clanId(), 0, null, 0, nextTargetEpoch, 0, targetPenalty, Clan.PENALTY_TYPE_CLAN_DISMISSED));
				notifySafely("alliance expel broadcast", () -> _state.broadcastUserInfo(target.clanId()));
				return success(expectedIdentity);
			}
			catch (ClanSocialRepository.StaleStateException | ArithmeticException e)
			{
				return stale();
			}
			catch (SQLException e)
			{
				return persistenceFailure();
			}
		});
	}
	public Result dissolve(Player player, AllianceIdentity expectedIdentity)
	{
		return dissolve(actor(player), expectedIdentity);
	}

	Result dissolve(Actor actor, AllianceIdentity expectedIdentity)
	{
		for (int attempt = 0; attempt < 3; attempt++)
		{
			final List<ClanSnapshot> observed = _state.allies(actor.clanId());
			final long[] keys = observed.stream().mapToLong(clan -> ClanSocialMutationFence.clanKey(clan.clanId())).toArray();
			final long[] effectiveKeys = keys.length == 0 ? new long[]
			{
				ClanSocialMutationFence.clanKey(actor.clanId())
			} : keys;
			final Result result = _fence.execute(effectiveKeys, () -> dissolveLocked(actor, expectedIdentity, observed));
			if (result.reason() != Reason.CONCURRENT_CHANGE)
			{
				return result;
			}
		}
		return ineligible(Reason.CONCURRENT_CHANGE);
	}

	private Result dissolveLocked(Actor actor, AllianceIdentity expectedIdentity, List<ClanSnapshot> observed)
	{
		if (_fence.isRetiring(actor.clanId()))
		{
			return ineligible(Reason.CLAN_RETIRING);
		}
		final ClanSnapshot leader = _state.clan(actor.clanId());
		if (leader == null)
		{
			return ineligible(Reason.CLAN_NOT_FOUND);
		}
		if (leader.allianceId() == 0)
		{
			return ineligible(Reason.NOT_ALLIED);
		}
		if (!actor.clanLeader() || (leader.clanId() != leader.allianceId()))
		{
			return ineligible(Reason.NOT_ALLIANCE_LEADER);
		}
		if (!Objects.equals(leader.identity(), expectedIdentity))
		{
			return stale();
		}
		if (actor.insideSiege())
		{
			return ineligible(Reason.ACTOR_IN_SIEGE);
		}
		final List<ClanSnapshot> current = sorted(_state.allies(leader.allianceId()));
		if (current.stream().anyMatch(member -> _fence.isRetiring(member.clanId())))
		{
			return ineligible(Reason.CLAN_RETIRING);
		}
		if (!sameAllianceMembers(observed, current, expectedIdentity))
		{
			return ineligible(Reason.CONCURRENT_CHANGE);
		}
		final List<Integer> memberIds = current.stream().map(ClanSnapshot::clanId).toList();
		final Map<Integer, Long> memberEpochs = new LinkedHashMap<>();
		for (ClanSnapshot member : current)
		{
			memberEpochs.put(member.clanId(), member.allianceGenerationCounter());
		}
		final long leaderPenalty = _clock.getAsLong() + TimeUnit.DAYS.toMillis(PlayerConfig.ALT_CREATE_ALLY_DAYS_WHEN_DISSOLVED);
		final int oldCrestId = leader.allianceCrestId();
		try
		{
			_persistence.dissolveAlliance(leader.clanId(), leader.allianceId(), leader.allianceGeneration(), memberEpochs, leaderPenalty);
			for (ClanSnapshot member : current)
			{
				final boolean allianceLeader = member.clanId() == leader.clanId();
				_state.apply(new AllianceState(member.clanId(), 0, null, 0, Math.addExact(member.allianceGenerationCounter(), 1), 0, allianceLeader ? leaderPenalty : 0, allianceLeader ? Clan.PENALTY_TYPE_DISSOLVE_ALLY : 0));
				notifySafely("alliance member broadcast", () -> _state.broadcastUserInfo(member.clanId()));
			}
			if (oldCrestId != 0)
			{
				notifySafely("alliance crest cleanup", () -> _state.removeCrest(oldCrestId));
			}
			notifySafely("alliance dissolve broadcast", () -> _state.broadcastDissolved(memberIds));
			return success(expectedIdentity);
		}
		catch (ClanSocialRepository.StaleStateException | ArithmeticException e)
		{
			return stale();
		}
		catch (SQLException e)
		{
			return persistenceFailure();
		}
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
			final ClanSnapshot observedClan = _state.clan(clanId);
			if (observedClan == null)
			{
				return ineligible(Reason.CLAN_NOT_FOUND);
			}
			final List<ClanSnapshot> observed = observedClan.allianceId() == 0 ? List.of(observedClan) : sorted(_state.allies(observedClan.allianceId()));
			final long[] keys = observed.stream().mapToLong(member -> ClanSocialMutationFence.clanKey(member.clanId())).toArray();
			final long[] effectiveKeys = keys.length == 0 ? new long[]
			{
				ClanSocialMutationFence.clanKey(clanId)
			} : keys;
			final Result result = _fence.execute(effectiveKeys, () -> removeAllForClanLocked(retirement, observedClan, observed));
			if (result.reason() != Reason.CONCURRENT_CHANGE)
			{
				return result;
			}
		}
		return ineligible(Reason.CONCURRENT_CHANGE);
	}

	private Result removeAllForClanLocked(ClanSocialMutationFence.Retirement retirement, ClanSnapshot observedClan, List<ClanSnapshot> observed)
	{
		if (!_fence.isCurrentRetirement(retirement))
		{
			return stale();
		}
		final ClanSnapshot currentClan = _state.clan(retirement.clanId());
		if (currentClan == null)
		{
			return ineligible(Reason.CLAN_NOT_FOUND);
		}
		if (currentClan.allianceId() == 0)
		{
			return success(null);
		}
		if (!Objects.equals(currentClan.membershipEpoch(), observedClan.membershipEpoch()))
		{
			return ineligible(Reason.CONCURRENT_CHANGE);
		}
		final AllianceIdentity identity = currentClan.identity();
		try
		{
			if (currentClan.clanId() == currentClan.allianceId())
			{
				final List<ClanSnapshot> current = sorted(_state.allies(currentClan.allianceId()));
				if (!sameAllianceMembers(observed, current, identity))
				{
					return ineligible(Reason.CONCURRENT_CHANGE);
				}
				final Map<Integer, Long> memberEpochs = new LinkedHashMap<>();
				for (ClanSnapshot member : current)
				{
					memberEpochs.put(member.clanId(), member.allianceGenerationCounter());
				}
				_persistence.dissolveAlliance(currentClan.clanId(), currentClan.allianceId(), currentClan.allianceGeneration(), memberEpochs, 0);
				final List<Integer> memberIds = current.stream().map(ClanSnapshot::clanId).toList();
				for (ClanSnapshot member : current)
				{
					final boolean allianceLeader = member.clanId() == currentClan.clanId();
					_state.apply(new AllianceState(member.clanId(), 0, null, 0, Math.addExact(member.allianceGenerationCounter(), 1), 0, 0, allianceLeader ? Clan.PENALTY_TYPE_DISSOLVE_ALLY : 0));
					notifySafely("retiring alliance member broadcast", () -> _state.broadcastUserInfo(member.clanId()));
				}
				if (currentClan.allianceCrestId() != 0)
				{
					notifySafely("retiring alliance crest cleanup", () -> _state.removeCrest(currentClan.allianceCrestId()));
				}
				notifySafely("retiring alliance dissolve broadcast", () -> _state.broadcastDissolved(memberIds));
			}
			else
			{
				final long nextEpoch = Math.addExact(currentClan.allianceGenerationCounter(), 1);
				_persistence.repairOrphanAlliance(currentClan.clanId(), currentClan.allianceId(), currentClan.allianceGeneration(), currentClan.allianceGenerationCounter());
				_state.apply(new AllianceState(currentClan.clanId(), 0, null, 0, nextEpoch, 0, currentClan.alliancePenaltyExpiryTime(), currentClan.alliancePenaltyType()));
				notifySafely("retiring alliance member cleanup", () -> _state.broadcastUserInfo(currentClan.clanId()));
			}
			return success(identity);
		}
		catch (ClanSocialRepository.StaleStateException | ArithmeticException e)
		{
			return stale();
		}
		catch (SQLException e)
		{
			return persistenceFailure();
		}
	}

	public Result changeCrest(Player player, int crestId)
	{
		final Actor actor = actor(player);
		final ClanSnapshot observedClan = _state.clan(actor.clanId());
		final AllianceIdentity expected = observedClan == null ? null : observedClan.identity();
		for (int attempt = 0; attempt < 3; attempt++)
		{
			final List<ClanSnapshot> observed = observedClan == null ? List.of() : _state.allies(observedClan.allianceId());
			final long[] keys = observed.stream().mapToLong(clan -> ClanSocialMutationFence.clanKey(clan.clanId())).toArray();
			final long[] effectiveKeys = keys.length == 0 ? new long[]
			{
				ClanSocialMutationFence.clanKey(actor.clanId())
			} : keys;
			final Result result = _fence.execute(effectiveKeys, () -> changeCrestLocked(actor, crestId, expected, observed));
			if (result.reason() != Reason.CONCURRENT_CHANGE)
			{
				return result;
			}
		}
		return ineligible(Reason.CONCURRENT_CHANGE);
	}

	private Result changeCrestLocked(Actor actor, int crestId, AllianceIdentity expectedIdentity, List<ClanSnapshot> observed)
	{
		if (_fence.isRetiring(actor.clanId()))
		{
			return ineligible(Reason.CLAN_RETIRING);
		}
		final ClanSnapshot leader = _state.clan(actor.clanId());
		if ((leader == null) || !actor.clanLeader() || (leader.clanId() != leader.allianceId()))
		{
			return ineligible(Reason.NOT_ALLIANCE_LEADER);
		}
		if (!Objects.equals(leader.identity(), expectedIdentity))
		{
			return stale();
		}
		final List<ClanSnapshot> current = sorted(_state.allies(leader.allianceId()));
		if (current.stream().anyMatch(member -> _fence.isRetiring(member.clanId())))
		{
			return ineligible(Reason.CLAN_RETIRING);
		}
		if (!sameAllianceMembers(observed, current, expectedIdentity))
		{
			return ineligible(Reason.CONCURRENT_CHANGE);
		}
		final List<Integer> memberIds = current.stream().map(ClanSnapshot::clanId).toList();
		final int oldCrestId = leader.allianceCrestId();
		try
		{
			_persistence.changeAllianceCrest(leader.allianceId(), leader.allianceGeneration(), memberIds, crestId);
			for (ClanSnapshot member : current)
			{
				_state.apply(new AllianceState(member.clanId(), member.allianceId(), member.allianceName(), member.allianceGeneration(), member.allianceGenerationCounter(), crestId, member.alliancePenaltyExpiryTime(), member.alliancePenaltyType()));
				notifySafely("alliance member broadcast", () -> _state.broadcastUserInfo(member.clanId()));
			}
			if ((oldCrestId != 0) && (oldCrestId != crestId))
			{
				notifySafely("alliance crest cleanup", () -> _state.removeCrest(oldCrestId));
			}
			return success(expectedIdentity);
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

	public Result clearInvalidCrest(Clan clan)
	{
		if (clan == null)
		{
			return ineligible(Reason.CLAN_NOT_FOUND);
		}
		return _fence.execute(new long[]
		{
			ClanSocialMutationFence.clanKey(clan.getId())
		}, () ->
		{
			if (_fence.isRetiring(clan.getId()))
			{
				return ineligible(Reason.CLAN_RETIRING);
			}
			final ClanSnapshot current = _state.clan(clan.getId());
			if (current == null)
			{
				return ineligible(Reason.CLAN_NOT_FOUND);
			}
			try
			{
				_persistence.clearClanAllianceCrest(clan.getId());
				_state.apply(new AllianceState(current.clanId(), current.allianceId(), current.allianceName(), current.allianceGeneration(), current.allianceGenerationCounter(), 0, current.alliancePenaltyExpiryTime(), current.alliancePenaltyType()));
				notifySafely("alliance repair broadcast", () -> _state.broadcastUserInfo(current.clanId()));
				return success(current.identity());
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
	public Result repairOrphaned(ClanTable clanTable, Clan clan)
	{
		if ((clanTable == null) || (clan == null))
		{
			return ineligible(Reason.CLAN_NOT_FOUND);
		}
		final LiveStateAccess state = new LiveStateAccess(clanTable);
		return _fence.execute(new long[]
		{
			ClanSocialMutationFence.clanKey(clan.getId()),
			ClanSocialMutationFence.clanKey(clan.getAllyId())
		}, () ->
		{
			if (_fence.isRetiring(clan.getId()) || _fence.isRetiring(clan.getAllyId()))
			{
				return ineligible(Reason.CLAN_RETIRING);
			}
			final ClanSnapshot current = state.clan(clan.getId());
			if ((current == null) || (current.allianceId() == 0) || (current.clanId() == current.allianceId()) || (clanTable.getClan(current.allianceId()) != null))
			{
				return ineligible(Reason.NOT_ALLIED);
			}
			try
			{
				final long nextEpoch = Math.addExact(current.allianceGenerationCounter(), 1);
				_persistence.repairOrphanAlliance(current.clanId(), current.allianceId(), current.allianceGeneration(), current.allianceGenerationCounter());
				state.apply(new AllianceState(current.clanId(), 0, null, 0, nextEpoch, 0, current.alliancePenaltyExpiryTime(), current.alliancePenaltyType()));
				notifySafely("alliance orphan-repair broadcast", () -> state.broadcastUserInfo(current.clanId()));
				return success(current.identity());
			}
			catch (ClanSocialRepository.StaleStateException | ArithmeticException e)
			{
				return stale();
			}
			catch (SQLException e)
			{
				return persistenceFailure();
			}
		});
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

	private static Actor actor(Player player)
	{
		return player == null ? new Actor(0, 0, false, false) : new Actor(player.getObjectId(), player.getClanId(), player.isClanLeader(), player.isInsideZone(ZoneId.SIEGE));
	}

	private static List<ClanSnapshot> sorted(List<ClanSnapshot> clans)
	{
		return clans.stream().sorted(Comparator.comparingInt(ClanSnapshot::clanId)).toList();
	}

	private static boolean sameAllianceMembers(List<ClanSnapshot> observed, List<ClanSnapshot> current, AllianceIdentity identity)
	{
		if ((identity == null) || (observed.size() != current.size()))
		{
			return false;
		}
		final List<ClanSnapshot> sortedObserved = sorted(observed);
		for (int i = 0; i < current.size(); i++)
		{
			if ((sortedObserved.get(i).clanId() != current.get(i).clanId()) || !Objects.equals(current.get(i).identity(), identity))
			{
				return false;
			}
		}
		return true;
	}

	private static Result success(AllianceIdentity identity)
	{
		return new Result(Status.SUCCESS, Reason.NONE, identity);
	}
	private static Result success(AllianceIdentity identity, MembershipEpoch targetEpoch)
	{
		return new Result(Status.SUCCESS, Reason.NONE, identity, targetEpoch);
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
		public List<ClanSnapshot> allies(int allianceId)
		{
			final List<ClanSnapshot> result = new ArrayList<>();
			for (Clan clan : table().getClanAllies(allianceId))
			{
				result.add(snapshot(clan));
			}
			return List.copyOf(result);
		}

		@Override
		public boolean allianceNameExists(String allianceName)
		{
			return table().isAllyExists(allianceName);
		}

		@Override
		public boolean atWar(int sourceClanId, int targetClanId)
		{
			final Clan source = table().getClan(sourceClanId);
			return (source != null) && source.isAtWarWith(targetClanId);
		}

		@Override
		public void apply(AllianceState state)
		{
			final Clan clan = table().getClan(state.clanId());
			if (clan == null)
			{
				return;
			}
			clan.setAllyId(state.allianceId());
			clan.setAllyName(state.allianceName());
			clan.setAllyGeneration(state.generation());
			clan.setAllyGenerationCounter(state.generationCounter());
			clan.setAllyCrestId(state.crestId());
			clan.setAllyPenaltyExpiryTime(state.penaltyExpiryTime(), state.penaltyType());
		}

		@Override
		public void broadcastUserInfo(int clanId)
		{
			final Clan clan = table().getClan(clanId);
			if (clan != null)
			{
				for (Player member : clan.getOnlineMembers(0))
				{
					member.broadcastUserInfo();
				}
			}
		}

		@Override
		public void broadcastDissolved(List<Integer> clanIds)
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.THE_ALLIANCE_HAS_BEEN_DISSOLVED);
			for (int clanId : clanIds)
			{
				final Clan clan = table().getClan(clanId);
				if (clan != null)
				{
					clan.broadcastToOnlineMembers(message);
				}
			}
		}

		@Override
		public void removeCrest(int crestId)
		{
			CrestTable.getInstance().removeCrest(crestId);
		}

		private static ClanSnapshot snapshot(Clan clan)
		{
			return new ClanSnapshot(clan.getId(), clan.getName(), clan.getLevel(), clan.getAllyId(), clan.getAllyName(), clan.getAllyGeneration(), clan.getAllyGenerationCounter(), clan.getAllyCrestId(), clan.getAllyPenaltyExpiryTime(), clan.getAllyPenaltyType(), clan.getDissolvingExpiryTime());
		}
	}
}
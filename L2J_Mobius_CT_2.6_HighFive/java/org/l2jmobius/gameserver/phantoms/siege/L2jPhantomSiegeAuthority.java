/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.siege;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.SiegeScheduleData;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.managers.SiegeManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.WorldRegion;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Door;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanMember;
import org.l2jmobius.gameserver.model.siege.Castle;
import org.l2jmobius.gameserver.model.siege.Siege;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.CastleSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.NativeSide;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.RegistrationOutcome;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Target;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.TargetKind;

/** Native Castle/Siege adapter. It observes facts and performs only non-force registration. */
public final class L2jPhantomSiegeAuthority implements PhantomSiegeAuthority
{
	private static final int GIRAN_CASTLE_ID = 3;
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final Supplier<PhantomProgressionCatalog> _progressionCatalog;

	public L2jPhantomSiegeAuthority(PhantomProfileRepository profiles, PhantomMaterializationService materialization, Supplier<PhantomProgressionCatalog> progressionCatalog)
	{
		_profiles = java.util.Objects.requireNonNull(profiles, "profiles");
		_materialization = java.util.Objects.requireNonNull(materialization, "materialization");
		_progressionCatalog = java.util.Objects.requireNonNull(progressionCatalog, "progressionCatalog");
	}

	@Override
	public Optional<CastleSnapshot> observeCastle(int castleId)
	{
		return Optional.ofNullable(nativeCastle(castleId)).map(this::castleSnapshot);
	}

	@Override
	public Optional<ActorSnapshot> observeActor(long profileId, int castleId)
	{
		final Castle castle = nativeCastle(castleId);
		if (castle == null)
		{
			return Optional.empty();
		}
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			return lease == null ? Optional.empty() : actorSnapshot(profileId, lease.player(), castle);
		}
	}

	@Override
	public RegistrationResult registerAttacker(long profileId, int castleId)
	{
		final Castle castle = nativeCastle(castleId);
		if (castle == null)
		{
			return new RegistrationResult(RegistrationOutcome.STALE, null, null, "siege.native.castle_missing");
		}
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null)
			{
				return new RegistrationResult(RegistrationOutcome.STALE, castleSnapshot(castle), null, "siege.actor.not_materialized");
			}
			final Player player = lease.player();
			final ActorSnapshot beforeActor = actorSnapshot(profileId, player, castle).orElse(null);
			final CastleSnapshot beforeCastle = castleSnapshot(castle);
			if (beforeActor == null)
			{
				return new RegistrationResult(RegistrationOutcome.INELIGIBLE, beforeCastle, null, "siege.actor.no_managed_clan");
			}
			if (!beforeActor.leader())
			{
				return new RegistrationResult(RegistrationOutcome.NOT_LEADER, beforeCastle, beforeActor, "siege.registration.leader_required");
			}
			if (beforeCastle.attacker(beforeActor.clanId()))
			{
				return new RegistrationResult(RegistrationOutcome.ALREADY_REGISTERED, beforeCastle, beforeActor, "siege.registration.already_attacker");
			}
			if (!beforeCastle.registrationOpen())
			{
				return new RegistrationResult(RegistrationOutcome.CLOSED, beforeCastle, beforeActor, "siege.registration.closed");
			}
			final Clan clan = player.getClan();
			if ((clan == null) || (clan.getLeaderId() != player.getObjectId()) || (clan.getLevel() < SiegeManager.getInstance().getSiegeClanMinLevel()) || (clan.getId() == castle.getOwnerId()) || (clan.getCastleId() > 0))
			{
				return new RegistrationResult(RegistrationOutcome.INELIGIBLE, beforeCastle, beforeActor, "siege.registration.native_prerequisite");
			}

			// The native method owns all remaining rules, persistence and list mutation.
			castle.getSiege().registerAttacker(player, false);

			final CastleSnapshot afterCastle = castleSnapshot(castle);
			final ActorSnapshot afterActor = actorSnapshot(profileId, player, castle).orElse(null);
			if ((afterActor != null) && afterCastle.attacker(afterActor.clanId()))
			{
				return new RegistrationResult(RegistrationOutcome.REGISTERED, afterCastle, afterActor, "siege.registration.native_confirmed");
			}
			return new RegistrationResult(RegistrationOutcome.FAILED, afterCastle, afterActor, "siege.registration.native_rejected");
		}
		catch (RuntimeException exception)
		{
			return new RegistrationResult(RegistrationOutcome.FAILED, observeCastle(castleId).orElse(null), observeActor(profileId, castleId).orElse(null), "siege.registration.native_failure");
		}
	}

	@Override
	public List<MemberSnapshot> managedClanMembers(int clanId, int limit)
	{
		if ((clanId <= 0) || (limit < 1) || (limit > 32))
		{
			return List.of();
		}
		final Clan clan = ClanTable.getInstance().getClan(clanId);
		if (clan == null)
		{
			return List.of();
		}
		final PhantomProgressionCatalog catalog = _progressionCatalog.get();
		final List<MemberSnapshot> result = new ArrayList<>();
		for (ClanMember member : clan.getMembers().stream().sorted(Comparator.comparingInt(ClanMember::getObjectId)).toList())
		{
			final PhantomProfile profile = _profiles.findByCharacterObjectId(member.getObjectId()).orElse(null);
			if (profile == null)
			{
				continue;
			}
			final boolean materialized = _materialization.findByCharacterObjectId(member.getObjectId()).filter(snapshot -> snapshot.profileId() == profile.profileId() && snapshot.worldPresent()).isPresent();
			final List<String> capabilities = catalog == null ? List.of() : catalog.capabilities(member.getClassId()).stream().map(rule -> rule.capabilityKey()).distinct().sorted().toList();
			result.add(new MemberSnapshot(profile.profileId(), member.getObjectId(), member.getClassId(), member.getLevel(), member.isOnline(), materialized, capabilities));
			if (result.size() == limit)
			{
				break;
			}
		}
		return List.copyOf(result);
	}

	@Override
	public List<Target> opposingPlayers(long profileId, int castleId, int limit, int maximumDistance)
	{
		return targets(profileId, castleId, limit, maximumDistance, TargetKind.PLAYER);
	}

	@Override
	public List<Target> attackableDoors(long profileId, int castleId, int limit, int maximumDistance)
	{
		return targets(profileId, castleId, limit, maximumDistance, TargetKind.DOOR);
	}

	private List<Target> targets(long profileId, int castleId, int limit, int maximumDistance, TargetKind kind)
	{
		final Castle castle = nativeCastle(castleId);
		if ((castle == null) || (limit < 1) || (limit > 8) || (maximumDistance < 1) || (maximumDistance > 2000))
		{
			return List.of();
		}
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null)
			{
				return List.of();
			}
			final Player actor = lease.player();
			final Siege siege = castle.getSiege();
			final NativeSide actorSide = side(siege, actor);
			if (!siege.isInProgress() || !castle.getZone().isActive() || (actorSide == NativeSide.NONE) || !actor.isInsideZone(ZoneId.SIEGE) || !castle.checkIfInZone(actor.getX(), actor.getY(), actor.getZ()))
			{
				return List.of();
			}
			final List<Target> result = new ArrayList<>();
			if (kind == TargetKind.PLAYER)
			{
				for (Player target : castle.getZone().getPlayersInside().stream().sorted(Comparator.comparingInt(Player::getObjectId)).toList())
				{
					final NativeSide targetSide = side(siege, target);
					if ((target != actor) && (targetSide != NativeSide.NONE) && (targetSide != actorSide) && validPlayerTarget(actor, target, castle, maximumDistance))
					{
						result.add(target(kind, target, castle, target.getObjectId(), targetSide, actor));
					}
					if (result.size() == limit)
					{
						break;
					}
				}
			}
			else if (actorSide == NativeSide.ATTACKER)
			{
				for (Door door : castle.getDoors().stream().sorted(Comparator.comparingInt(Door::getObjectId)).toList())
				{
					if ((door.getCastle() == castle) && !door.isDead() && !door.isAlikeDead() && door.isTargetable() && door.isShowHp() && !door.isInvul() && door.isAutoAttackable(actor) && sameContext(actor, door, maximumDistance) && castle.checkIfInZone(door.getX(), door.getY(), door.getZ()))
					{
						result.add(target(kind, door, castle, door.getId(), NativeSide.NONE, actor));
					}
					if (result.size() == limit)
					{
						break;
					}
				}
			}
			return List.copyOf(result);
		}
	}

	private Castle nativeCastle(int castleId)
	{
		if ((castleId != GIRAN_CASTLE_ID) || (SiegeScheduleData.getInstance().getScheduleDateForCastleId(castleId) == null))
		{
			return null;
		}
		return CastleManager.getInstance().getCastleById(castleId);
	}

	private CastleSnapshot castleSnapshot(Castle castle)
	{
		final Siege siege = castle.getSiege();
		final List<Integer> attackers = siege.getAttackerClans().stream().map(clan -> clan.getClanId()).distinct().sorted().toList();
		final List<Integer> defenders = siege.getDefenderClans().stream().map(clan -> clan.getClanId()).distinct().sorted().toList();
		final List<Integer> pending = siege.getDefenderWaitingClans().stream().map(clan -> clan.getClanId()).distinct().sorted().toList();
		final long siegeDate = Math.max(0, castle.getSiegeDate().getTimeInMillis());
		final long registrationEnd = Math.max(0, castle.getTimeRegistrationOverDate().getTimeInMillis());
		final boolean registrationOpen = !siege.isRegistrationOver() && !siege.isInProgress();
		final boolean zoneActive = (castle.getZone() != null) && castle.getZone().isActive();
		final String facts = castle.getResidenceId() + "|" + castle.getName() + "|" + siegeDate + "|" + registrationEnd + "|" + registrationOpen + "|" + siege.isInProgress() + "|" + zoneActive + "|" + castle.getOwnerId() + "|" + attackers + "|" + defenders + "|" + pending;
		return new CastleSnapshot(castle.getResidenceId(), castle.getName(), siegeDate, registrationEnd, registrationOpen, siege.isInProgress(), zoneActive, castle.getOwnerId(), attackers, defenders, pending, PhantomSiegeModel.sha256(facts));
	}

	private Optional<ActorSnapshot> actorSnapshot(long profileId, Player player, Castle castle)
	{
		final PhantomProfile profile = _profiles.find(profileId).orElse(null);
		final Clan clan = player.getClan();
		if ((profile == null) || (profile.characterObjectId() == null) || (profile.characterObjectId() != player.getObjectId()) || (clan == null) || (clan.getLeaderId() <= 0))
		{
			return Optional.empty();
		}
		final NativeSide side = side(castle.getSiege(), player);
		final double hpPercent = player.getMaxHp() <= 0 ? 0 : Math.max(0, Math.min(100, (player.getCurrentHp() * 100d) / player.getMaxHp()));
		final String facts = profileId + "|" + player.getObjectId() + "|" + clan.getId() + "|" + clan.getLeaderId() + "|" + clan.getLevel() + "|" + clan.getAllyId() + "|" + clan.getCastleId() + "|" + player.getActiveClass() + "|" + player.getLevel() + "|" + player.getInstanceId() + "|" + hpPercent + "|" + player.isDead() + "|" + player.isInSiege() + "|" + side;
		return Optional.of(new ActorSnapshot(profileId, player.getObjectId(), clan.getId(), clan.getLeaderId(), clan.getLevel(), clan.getAllyId(), clan.getCastleId(), player.getActiveClass(), player.getLevel(), player.getInstanceId(), hpPercent, player.isDead() || player.isAlikeDead(), player.isInSiege() && player.isInsideZone(ZoneId.SIEGE), side, PhantomSiegeModel.sha256(facts)));
	}

	private static NativeSide side(Siege siege, Player player)
	{
		if ((player.getClan() != null) && siege.checkIsAttacker(player.getClan()))
		{
			return NativeSide.ATTACKER;
		}
		if ((player.getClan() != null) && siege.checkIsDefender(player.getClan()))
		{
			return NativeSide.DEFENDER;
		}
		return NativeSide.NONE;
	}

	private static boolean validPlayerTarget(Player actor, Player target, Castle castle, int maximumDistance)
	{
		return !target.isDead() && !target.isAlikeDead() && target.isTargetable() && !target.isInvisible() && !target.isInvul() && target.isAutoAttackable(actor) && !actor.isInsideZone(ZoneId.PEACE) && !target.isInsideZone(ZoneId.PEACE) && !actor.isOnEvent() && !target.isOnEvent() && !actor.isInOlympiadMode() && !target.isInOlympiadMode() && !actor.isInDuel() && !target.isInDuel() && !actor.isJailed() && !target.isJailed() && !actor.isFestivalParticipant() && !target.isFestivalParticipant() && !actor.isInBoat() && !actor.isInAirShip() && !target.isInBoat() && !target.isInAirShip() && (actor.getParty() == null || actor.getParty() != target.getParty()) && target.isInsideZone(ZoneId.SIEGE) && castle.checkIfInZone(target.getX(), target.getY(), target.getZ()) && sameContext(actor, target, maximumDistance);
	}

	private static boolean sameContext(Player actor, WorldObject target, int maximumDistance)
	{
		final WorldRegion region = actor.getWorldRegion();
		return (target.getInstanceId() == actor.getInstanceId()) && (region != null) && region.isSurroundingRegion(target.getWorldRegion()) && (actor.calculateDistance2D(target) <= maximumDistance) && (World.getInstance().findObject(target.getObjectId()) == target);
	}

	private static Target target(TargetKind kind, WorldObject object, Castle castle, int nativeId, NativeSide side, Player actor)
	{
		final double distance = actor.calculateDistance2D(object);
		final String facts = kind + "|" + object.getObjectId() + "|" + castle.getResidenceId() + "|" + nativeId + "|" + side + "|" + distance + "|" + castle.getSiege().isInProgress() + "|" + castle.getZone().isActive();
		return new Target(kind, object.getObjectId(), castle.getResidenceId(), nativeId, side, distance, PhantomSiegeModel.sha256(facts));
	}
}

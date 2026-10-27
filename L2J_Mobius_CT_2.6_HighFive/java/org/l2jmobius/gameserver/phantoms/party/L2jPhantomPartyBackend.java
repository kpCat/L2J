/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.groups.CommandChannel;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.item.type.WeaponType;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.stats.Formulas;
import org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PvpProtection;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.Status;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityEvaluation;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;

public final class L2jPhantomPartyBackend implements PhantomPartyBackend
{
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final PhantomProgressionService _progression;
	private final PartyInvitationService _invitations;
	private final CommandChannelInvitationService _commandChannels;

	public L2jPhantomPartyBackend(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomProgressionService progression)
	{
		_profiles = profiles;
		_materialization = materialization;
		_progression = progression;
		_invitations = PartyInvitationService.getInstance();
		_commandChannels = CommandChannelInvitationService.getInstance();
	}

	@Override
	public OptionalLong managedProfileId(int characterObjectId)
	{
		final Optional<PhantomProfile> profile = _profiles.findByCharacterObjectId(characterObjectId);
		return profile.isPresent() ? OptionalLong.of(profile.get().profileId()) : OptionalLong.empty();
	}

	@Override
	public Optional<MemberRef> currentMember(long profileId)
	{
		return _profiles.find(profileId).filter(profile -> profile.characterObjectId() != null).map(profile -> MemberRef.phantom(profileId, profile.characterObjectId()));
	}

	@Override
	public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution)
	{
		try (AcquiredPlayer requesterPlayer = acquire(requester); AcquiredPlayer targetPlayer = acquire(target))
		{
			if ((requesterPlayer == null) || (targetPlayer == null))
			{
				return new InviteResult(PartyInvitationService.InviteOutcome.TARGET_NOT_FOUND, null);
			}
			return _invitations.invite(requesterPlayer.player(), targetPlayer.player(), distribution.getId());
		}
	}

	@Override
	public RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity)
	{
		try (AcquiredPlayer acquired = acquire(invitee))
		{
			return acquired == null ? new RespondResult(PartyInvitationService.RespondOutcome.NO_PENDING_INVITE, identity, null) : _invitations.respond(acquired.player(), response, identity);
		}
	}

	@Override
	public CommandChannelInvitationService.InviteResult inviteCommandChannel(MemberRef requester, MemberRef target)
	{
		try (AcquiredPlayer requesterPlayer = acquire(requester); AcquiredPlayer targetPlayer = acquire(target))
		{
			if ((requesterPlayer == null) || (targetPlayer == null))
			{
				return new CommandChannelInvitationService.InviteResult(CommandChannelInvitationService.InviteOutcome.TARGET_NOT_FOUND, null);
			}
			final Player targetLeader = targetPlayer.player();
			final Party targetParty = targetLeader.getParty();
			if ((targetParty != null) && (targetParty.getLeader() != targetLeader))
			{
				return new CommandChannelInvitationService.InviteResult(CommandChannelInvitationService.InviteOutcome.TARGET_NOT_PARTY_LEADER, null);
			}
			return _commandChannels.invite(requesterPlayer.player(), targetLeader);
		}
	}

	@Override
	public CommandChannelInvitationService.RespondResult respondCommandChannel(MemberRef invitee, CommandChannelInvitationService.Response response, CommandChannelInvitationService.InvitationIdentity identity)
	{
		try (AcquiredPlayer acquired = acquire(invitee))
		{
			return acquired == null ? new CommandChannelInvitationService.RespondResult(CommandChannelInvitationService.RespondOutcome.NO_PENDING_INVITE, identity, false) : _commandChannels.respond(acquired.player(), response, identity);
		}
	}

	@Override
	public CommandChannelInvitationService.DismissOutcome dismissCommandChannel(MemberRef requester, MemberRef target)
	{
		try (AcquiredPlayer requesterPlayer = acquire(requester); AcquiredPlayer targetPlayer = acquire(target))
		{
			if ((requesterPlayer == null) || (targetPlayer == null))
			{
				return CommandChannelInvitationService.DismissOutcome.TARGET_NOT_FOUND;
			}
			final Player targetLeader = targetPlayer.player();
			final Party targetParty = targetLeader.getParty();
			if ((targetParty != null) && (targetParty.getLeader() != targetLeader))
			{
				return CommandChannelInvitationService.DismissOutcome.TARGET_NOT_PARTY_LEADER;
			}
			return _commandChannels.dismiss(requesterPlayer.player(), targetLeader);
		}
	}

	@Override
	public CommandChannelInvitationService.CancelResult cancelCommandChannel(CommandChannelInvitationService.InvitationIdentity identity)
	{
		return _commandChannels.cancel(identity);
	}

	@Override
	public Optional<CommandChannelInvitationService.InvitationSnapshot> observeCommandChannelInvitation(MemberRef invitee)
	{
		try (AcquiredPlayer acquired = acquire(invitee))
		{
			return acquired == null ? Optional.empty() : _commandChannels.observe(acquired.player());
		}
	}

	@Override
	public MembershipOutcome leave(MemberRef member)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			return acquired == null ? MembershipOutcome.INVALID_TARGET : _invitations.leave(acquired.player());
		}
	}

	@Override
	public MembershipOutcome expel(MemberRef requester, MemberRef member)
	{
		try (AcquiredPlayer requestorPlayer = acquire(requester); AcquiredPlayer memberPlayer = acquire(member))
		{
			return (requestorPlayer == null) || (memberPlayer == null) ? MembershipOutcome.INVALID_TARGET : _invitations.expel(requestorPlayer.player(), memberPlayer.player());
		}
	}

	@Override
	public MembershipOutcome transferLeader(MemberRef requester, MemberRef member)
	{
		try (AcquiredPlayer requestorPlayer = acquire(requester); AcquiredPlayer memberPlayer = acquire(member))
		{
			return (requestorPlayer == null) || (memberPlayer == null) ? MembershipOutcome.INVALID_TARGET : _invitations.transferLeader(requestorPlayer.player(), memberPlayer.player());
		}
	}

	@Override
	public Optional<PartySnapshot> observe(MemberRef member)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return Optional.empty();
			}
			final Party party = acquired.player().getParty();
			if (party == null)
			{
				return Optional.empty();
			}
			final List<MemberRef> members = party.getMembers().stream().map(this::reference).toList();
			return Optional.of(new PartySnapshot(reference(party.getLeader()), members, party.getDistributionType()));
		}
	}

	@Override
	public Optional<MemberSnapshot> memberSnapshot(MemberRef member)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return Optional.empty();
			}
			return Optional.of(snapshot(member, acquired.player()));
		}
	}

	@Override
	public FarmPage materializedFarmMembers(long afterProfileId, int limit)
	{
		if (limit < 1 || limit > PhantomAutonomousFarmParty.MAXIMUM_CANDIDATES) { return new FarmPage(List.of(), afterProfileId); }
		final List<MemberSnapshot> result = new ArrayList<>();
		long examined = afterProfileId;
		for (var entry : _materialization.list().stream().filter(entry -> entry.worldPresent() && entry.profileId() > afterProfileId).sorted(java.util.Comparator.comparingLong(entry -> entry.profileId())).limit(limit).toList())
		{
			examined = entry.profileId();
			final var member = currentMember(entry.profileId()).orElse(null);
			if (member == null) { continue; }
			try (var acquired = acquire(member))
			{
				if (acquired != null && farmAvailable(acquired.player())) { result.add(snapshot(member, acquired.player())); }
			}
		}
		return new FarmPage(result, examined);
	}

	@Override
	public boolean autonomousFarmReady(PartySnapshot party)
	{
		if (party.members().size() < 2 || party.members().size() > 5 || party.members().stream().anyMatch(member -> member.kind() != MemberKind.PHANTOM)) { return false; }
		boolean support = false;
		for (MemberRef member : party.members())
		{
			try (var acquired = acquire(member))
			{
				if (acquired == null || !farmAvailable(acquired.player()) || acquired.player().getParty() == null || acquired.player().getParty().getLeader().getObjectId() != party.leader().characterObjectId()) { return false; }
				final var observed = snapshot(member, acquired.player());
				if (observed.hpPercent() < 25) { return false; }
				support |= PhantomAutonomousFarmParty.supportPresent(observed);
			}
		}
		return support;
	}

	private static boolean farmAvailable(Player player)
	{
		return farmPresent(player) && !player.isProcessingTransaction();
	}

	private static boolean farmPresent(Player player)
	{
		return World.getInstance().getPlayer(player.getObjectId()) == player && !player.isAlikeDead() && !player.isInStoreMode() && !player.hasPendingOwnedStore();
	}

	private static boolean farmIdle(Player player)
	{
		return !player.isAttackingNow() && !player.isCastingNow() && !player.isMoving() && player.getQueuedSkill() == null;
	}

	@Override
	public boolean autonomousFarmAvailable(MemberRef member)
	{
		if (member == null || member.kind() != MemberKind.PHANTOM) { return false; }
		try (var acquired = acquire(member))
		{
			if (acquired == null) { return false; }
			final var player = acquired.player();
			return farmAvailable(player) && farmIdle(player);
		}
	}

	@Override
	public boolean autonomousFarmConsentAvailable(MemberRef requester, MemberRef invitee, InvitationIdentity identity)
	{
		if (requester == null || invitee == null || identity == null || requester.kind() != MemberKind.PHANTOM || invitee.kind() != MemberKind.PHANTOM || requester.equals(invitee)
			|| identity.requesterObjectId() != requester.characterObjectId() || identity.inviteeObjectId() != invitee.characterObjectId()) { return false; }
		try (var from = acquire(requester); var to = acquire(invitee))
		{
			if (from == null || to == null) { return false; }
			final var leader = from.player(); final var member = to.player();
			final var pending = _invitations.observe(member).orElse(null);
			// The canonical service installs these request fields before managed delivery.
			// Only its exact published Phantom-to-Phantom invitation owns this exception.
			return pending != null && pending.identity().equals(identity) && pending.managedRequester() && pending.managedInvitee()
				&& farmPresent(leader) && farmPresent(member) && farmIdle(leader) && farmIdle(member)
				&& leader.hasHeadlessOutboundSession() && member.hasHeadlessOutboundSession()
				&& leader.getActiveRequester() == null && member.getActiveRequester() == leader && !leader.isRequestExpired() && member.isRequestExpired()
				&& leader.getActiveTradeList() == null && member.getActiveTradeList() == null;
		}
	}

	@Override
	public OptionalInt autonomousFarmTarget(PartySnapshot party, Set<MemberRef> supportMembers)
	{
		return autonomousFarmTarget(party, supportMembers, null);
	}

	/** Bounded passive admission witness; it does not grant actions or change the guards. */
	public OptionalInt autonomousFarmTarget(PartySnapshot party, Set<MemberRef> supportMembers, java.util.function.Consumer<String> witness)
	{
		if (party.members().size() < 2 || party.members().size() > 5 || supportMembers.isEmpty() || !party.members().containsAll(supportMembers) || party.members().stream().anyMatch(member -> member.kind() != MemberKind.PHANTOM)) { return OptionalInt.empty(); }
		final List<AcquiredPlayer> leases = new ArrayList<>();
		try
		{
			final List<Player> actors = new ArrayList<>();
			final List<MemberSnapshot> members = new ArrayList<>();
			for (var member : party.members())
			{
				final var acquired = acquire(member);
				if (acquired == null) { if (witness != null) { witness.accept("actor.unavailable=" + member.profileId()); } return OptionalInt.empty(); }
				leases.add(acquired);
				final Player player = acquired.player();
				if (!farmAvailable(player) || player.getParty() == null || player.getParty().getLeader().getObjectId() != party.leader().characterObjectId()) { return OptionalInt.empty(); }
				actors.add(player); members.add(snapshot(member, player));
			}
			final Player leader = actors.stream().filter(player -> player.getObjectId() == party.leader().characterObjectId()).findFirst().orElseThrow();
			final var leaderState = members.stream().filter(member -> member.ref().equals(party.leader())).findFirst().orElseThrow();
			if (members.stream().anyMatch(member -> !PhantomAutonomousFarmParty.compatible(leaderState, member)) || actors.stream().anyMatch(player -> Math.abs(player.getLevel() - leader.getLevel()) > 8) || members.stream().noneMatch(PhantomAutonomousFarmParty::support)) { if (witness != null) { witness.accept("roster.compatibility_or_support=" + members.stream().map(member -> member.ref().profileId() + ":compatible=" + PhantomAutonomousFarmParty.compatible(leaderState, member) + ":support=" + PhantomAutonomousFarmParty.support(member) + ":MP=" + member.mpPercent()).toList()); } return OptionalInt.empty(); }
			final List<Creature> candidates = new ArrayList<>();
			if (leader.getTarget() instanceof Monster current && !current.isAlikeDead()) { candidates.add(current); }
			World.getInstance().getVisibleObjectsInRange(leader, Monster.class, PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE).stream().filter(target -> !candidates.contains(target)).sorted(java.util.Comparator.comparingDouble((Monster monster) -> leader.calculateDistance2D(monster)).thenComparingInt(Creature::getObjectId)).limit(8).forEach(candidates::add);
			for (Creature target : candidates)
			{
				if (!target.isMonster() || target.isRaid() || target.isAlikeDead() || target.isInvul() || !target.isTargetable() || !target.isSpawned()) { continue; }
				if (actors.stream().anyMatch(player -> player.getInstanceId() != target.getInstanceId() || !target.isAutoAttackable(player) || !PhantomEngagementPlanner.rewardingFarmTarget(player, target) || Math.abs((long) player.getZ() - target.getZ()) > 200 || !GeoEngine.getInstance().canSeeTarget(player, target) || !GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), target.getX(), target.getY(), target.getZ(), player.getInstanceId()))) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":native_geometry_or_reward"); } continue; }
				if (!farmApproachSafe(actors, members, target)) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":party_approach_unproven"); } continue; }
				if (groupViable(actors, members, leader, target, supportMembers, witness)) { return OptionalInt.of(target.getObjectId()); }
			}
			return OptionalInt.empty();
		}
		finally { for (int index = leases.size() - 1; index >= 0; index--) { leases.get(index).close(); } }
	}

	private static boolean farmApproachSafe(List<Player> actors, List<MemberSnapshot> members, Creature target)
	{
		final var destination = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId());
		for (int index = 0; index < actors.size(); index++)
		{
			final Player actor = actors.get(index);
			if (actor.getWorldRegion() == null || !actor.getWorldRegion().isSurroundingRegion(target.getWorldRegion()) || actor.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || target.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE)) { return false; }
			final var from = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId());
			final List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest.PveThreat> threats;
			try
			{
				threats = actor.calculateDistance2D(target) <= 1200 ? List.of() : org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.pveThreats(from, actor.getLevel(), World.getInstance().getVisibleObjectsInRange(actor, Monster.class, 3500).stream().filter(monster -> monster != target).toList());
			}
			catch (IllegalArgumentException unavailable) { return false; }
			if (!PhantomAutonomousFarmParty.permitsFarmApproach(members.get(index), destination, threats) || !org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend.safePartyApproach(actor, target)) { return false; }
		}
		return true;
	}

	/** Combine factual native damage estimates; never require every member to solo the mob. */
	private static boolean groupViable(List<Player> actors, List<MemberSnapshot> members, Player leader, Creature target, Set<MemberRef> supportMembers, java.util.function.Consumer<String> witness)
	{
		final var projected = L2jPhantomBackgroundAuthority.nativePlanningTarget(target, true);
		final List<Creature> threats = World.getInstance().getVisibleObjectsInRange(leader, Monster.class, 1200).stream().filter(other -> other != target && !other.isAlikeDead() && actors.stream().anyMatch(player -> org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.targetsForCombat(other, player))).limit(5).map(other -> (Creature) other).toList();
		if (threats.size() > 4 || threats.stream().anyMatch(Creature::isRaid)) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":threats=" + threats.size()); } return false; }
		final double[] intervals = new double[actors.size()];
		final double[] costs = new double[actors.size()];
		double damagePerSecond = 0;
		for (int index = 0; index < actors.size(); index++)
		{
			if (supportMembers.contains(members.get(index).ref()) || !PhantomAutonomousFarmParty.damage(members.get(index))) { continue; }
			final Player player = actors.get(index);
			final Skill skill = PhantomEngagementPlanner.offensiveSkill(player, target);
			final var combat = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, target, skill);
			long duration;
			if (skill != null && skill.isMagic())
			{
				final var mechanics = combat.forTarget(projected);
				if (mechanics == null) { continue; }
				final var cast = PhantomBackgroundModel.castEncounter(combat.combat(), combat.vitals(), combat.requiredMp(), projected, mechanics, List.of());
				if (cast.damagePerCast() <= 0 || cast.casts() <= 0 || cast.durationMillis() <= 0) { continue; }
				duration = cast.durationMillis(); intervals[index] = Math.max(1, duration / (double) cast.casts()); costs[index] = combat.requiredMp();
			}
			else { duration = PhantomBackgroundModel.estimateEncounter(combat.combat(), projected).durationMillis(); }
			if (duration > 0) { damagePerSecond += projected.maximumHp() * 1000d / duration; }
		}
		final double effective = damagePerSecond - Math.max(0, Formulas.calcHpRegen(target) / 3d);
		if (!Double.isFinite(effective) || effective <= 0) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":effectiveDPS=" + effective); } return false; }
		final double millis = projected.maximumHp() * 1000d / effective;
		if (!Double.isFinite(millis) || millis > 60_000) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":durationMillis=" + millis + ":DPS=" + effective + ":HP=" + projected.maximumHp()); } return false; }
		for (int index = 0; index < actors.size(); index++)
		{
			final Player player = actors.get(index);
			if (intervals[index] > 0 && Math.ceil(millis / intervals[index]) * costs[index] > player.getCurrentMp() * 0.8) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":actor=" + player.getObjectId() + ":castMPRequired=" + Math.ceil(millis / intervals[index]) * costs[index] + ":MP=" + player.getCurrentMp()); } return false; }
			final var combat = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, target, null);
			final var estimate = PhantomBackgroundModel.estimateEncounter(combat.combat(), projected);
			double incoming = estimate.incomingDamage() * millis / Math.max(1, estimate.durationMillis());
			for (Creature threat : threats)
			{
				final var extra = PhantomBackgroundModel.estimateEncounter(combat.combat(), L2jPhantomBackgroundAuthority.nativePlanningTarget(threat, true));
				incoming += extra.incomingDamage() * millis / Math.max(1, extra.durationMillis());
			}
			if (!Double.isFinite(incoming) || incoming >= player.getCurrentHp() * 0.6) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":actor=" + player.getObjectId() + ":incoming=" + incoming + ":HP=" + player.getCurrentHp() + ":durationMillis=" + millis); } return false; }
		}
		return true;
	}

	@Override
	public OptionalInt currentLevel(MemberRef member)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			return acquired == null ? OptionalInt.empty() : OptionalInt.of(acquired.player().getLevel());
		}
	}

	@Override
	public CurrentForceObservation currentForce(MemberRef actor)
	{
		if (actor == null)
		{
			return CurrentForceObservation.unavailable("party.current_force.actor_missing");
		}
		try (AcquiredPlayer acquired = acquire(actor))
		{
			if ((acquired == null) || !reference(acquired.player()).equals(actor))
			{
				return CurrentForceObservation.unavailable("party.current_force.actor_stale");
			}
			final Player actorPlayer = acquired.player();
			final Party actorParty = actorPlayer.getParty();
			if (actorParty == null)
			{
				if (actor.kind() == MemberKind.PHANTOM)
				{
					final var entry = _materialization.find(actor.profileId()).orElse(null);
					final var owner = actorPlayer.getNativeWorkOwner();
					if (entry == null || !entry.worldPresent() || !actorPlayer.hasHeadlessOutboundSession()
						|| World.getInstance().getPlayer(actorPlayer.getObjectId()) != actorPlayer || World.getInstance().findObject(actorPlayer.getObjectId()) != actorPlayer
						|| owner == null || owner.player() != actorPlayer || !owner.isCurrent() || !owner.nativeObservationHealthy() || owner.epoch() != entry.materializedAtNanos())
					{
						return CurrentForceObservation.unavailable("party.current_force.native_absence_unproven");
					}
				}
				return CurrentForceObservation.partyAbsent();
			}
			final CommandChannel channel = actorParty.getCommandChannel();
			final List<Party> parties;
			final MemberRef channelLeader;
			final String channelIdentity;
			final int channelLevel;
			if (channel == null)
			{
				parties = List.of(actorParty);
				channelLeader = null;
				channelIdentity = "";
				channelLevel = 0;
			}
			else
			{
				final Player leader = channel.getLeader();
				parties = new ArrayList<>(channel.getParties());
				parties.sort(Comparator.comparingInt(Party::getLeaderObjectId));
				if ((leader == null) || !parties.contains(actorParty) || !channel.containsPlayer(actorPlayer))
				{
					return CurrentForceObservation.unavailable("party.current_force.channel_inconsistent");
				}
				channelLeader = reference(leader);
				channelIdentity = "command-channel:" + leader.getObjectId();
				channelLevel = channel.getLevel();
			}
			if (parties.size() > MAX_FORCE_PARTIES)
			{
				return CurrentForceObservation.boundsExceeded();
			}
			int memberCount = 0;
			for (Party party : parties)
			{
				if (party == null)
				{
					return CurrentForceObservation.unavailable("party.current_force.party_missing");
				}
				memberCount += party.getMembers().size();
			}
			if (memberCount > MAX_FORCE_MEMBERS)
			{
				return CurrentForceObservation.boundsExceeded();
			}
			final List<PartySnapshot> partySnapshots = new ArrayList<>(parties.size());
			final List<MemberSnapshot> memberSnapshots = new ArrayList<>(memberCount);
			final HashSet<Integer> objectIds = new HashSet<>();
			final HashSet<Player> copiedPlayers = new HashSet<>();
			for (Party party : parties)
			{
				if (((channel == null) && (party != actorParty)) || ((channel != null) && (party.getCommandChannel() != channel)))
				{
					return CurrentForceObservation.unavailable("party.current_force.party_channel_drift");
				}
				final Player leader = party.getLeader();
				final List<Player> players = List.copyOf(party.getMembers());
				if ((leader == null) || players.isEmpty() || (players.size() > 9))
				{
					return CurrentForceObservation.boundsExceeded();
				}
				final List<MemberRef> references = new ArrayList<>(players.size());
				for (Player player : players)
				{
					if ((player == null) || (player.getParty() != party) || (World.getInstance().getPlayer(player.getObjectId()) != player) || !objectIds.add(player.getObjectId()) || !copiedPlayers.add(player))
					{
						return CurrentForceObservation.unavailable("party.current_force.member_stale");
					}
					final MemberRef member = reference(player);
					references.add(member);
					memberSnapshots.add(snapshot(member, player));
				}
				final MemberRef partyLeader = reference(leader);
				if (!references.contains(partyLeader))
				{
					return CurrentForceObservation.unavailable("party.current_force.leader_stale");
				}
				partySnapshots.add(new PartySnapshot(partyLeader, references, party.getDistributionType()));
			}
			final MemberRef copiedPartyLeader = partySnapshots.stream().filter(party -> party.members().contains(actor)).map(PartySnapshot::leader).findFirst().orElse(null);
			final Player currentPartyLeader = actorParty.getLeader();
			if ((copiedPartyLeader == null) || (currentPartyLeader == null) || !reference(currentPartyLeader).equals(copiedPartyLeader) || (actorPlayer.getParty() != actorParty) || (actorParty.getCommandChannel() != channel))
			{
				return CurrentForceObservation.unavailable("party.current_force.changed_during_copy");
			}
			if (channel == null)
			{
				if (!new HashSet<>(actorParty.getMembers()).equals(copiedPlayers))
				{
					return CurrentForceObservation.unavailable("party.current_force.changed_during_copy");
				}
			}
			else
			{
				final Player currentChannelLeader = channel.getLeader();
				if ((currentChannelLeader == null) || !reference(currentChannelLeader).equals(channelLeader) || (channel.getLevel() != channelLevel) || !new HashSet<>(channel.getParties()).equals(new HashSet<>(parties)) || !new HashSet<>(channel.getMembers()).equals(copiedPlayers))
				{
					return CurrentForceObservation.unavailable("party.current_force.changed_during_copy");
				}
			}
			return CurrentForceObservation.available(new CurrentForceSnapshot(actor, copiedPartyLeader, channelIdentity, channelLeader, channelLevel, memberSnapshots.size(), partySnapshots, memberSnapshots));
		}
	}

	@Override
	public List<PvpProtection> pvpProtection(MemberRef helper, int limit)
	{
		if ((helper == null) || (helper.kind() != MemberKind.PHANTOM) || (limit < 1) || (limit > 8))
		{
			return List.of();
		}
		try (AcquiredPlayer acquired = acquire(helper))
		{
			if ((acquired == null) || (acquired.player().getParty() == null))
			{
				return List.of();
			}
			final Player helperPlayer = acquired.player();
			final Party party = helperPlayer.getParty();
			final TreeMap<String, PvpProtection> evidence = new TreeMap<>();
			for (Player protectedPlayer : party.getMembers())
			{
				if ((protectedPlayer == helperPlayer) || protectedPlayer.isDead() || protectedPlayer.isAlikeDead() || (protectedPlayer.getInstanceId() != helperPlayer.getInstanceId()))
				{
					continue;
				}
				for (int attackerObjectId : protectedPlayer.recentPlayerAttackers(limit))
				{
					final Player attacker = World.getInstance().getPlayer(attackerObjectId);
					if ((attacker == null) || attacker.isDead() || attacker.isAlikeDead() || (attacker.getInstanceId() != helperPlayer.getInstanceId()) || (attacker.getParty() == party))
					{
						continue;
					}
					final boolean currentAttack = (attacker.getTarget() == protectedPlayer) || (attacker.hasAI() && (attacker.getAI().getAttackTarget() == protectedPlayer));
					if (currentAttack)
					{
						final PvpProtection item = new PvpProtection(reference(protectedPlayer), attacker.getObjectId());
						evidence.put(item.protectedMember().stableKey() + ':' + item.attackerObjectId(), item);
						if (evidence.size() > limit)
						{
							evidence.pollLastEntry();
						}
					}
				}
			}
			return List.copyOf(evidence.values());
		}
	}

	@Override
	public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId)
	{
		if (exactTargetObjectId <= 0)
		{
			return List.of();
		}
		if (actor.kind() == MemberKind.PHANTOM)
		{
			try (var acquired = acquire(actor))
			{
				return acquired == null ? List.of() : nativeCapabilities(acquired.player(), phantomCapabilities(actor.profileId(), exactTargetObjectId), exactTargetObjectId);
			}
		}
		try (AcquiredPlayer acquired = acquire(actor))
		{
			return acquired == null ? List.of() : realCapabilities(acquired.player(), exactTargetObjectId);
		}
	}

	@Override
	public boolean protectiveMaintenance(MemberRef actor, MemberCapability capability)
	{
		if (capability == null || !capability.intrinsic() || !capability.learned() || !Set.of("combat.buff", "combat.song", "combat.dance").contains(capability.capabilityKey())) { return false; }
		try (AcquiredPlayer acquired = acquire(actor))
		{
			final Skill skill = acquired == null ? null : acquired.player().getKnownSkill(capability.actionSkillId());
			if (skill == null || skill.getLevel() != capability.actionSkillLevel() || PhantomSupportEffectAuthority.nativeCapability(skill) == null) { return false; }
			return switch (skill.getAbnormalType())
			{
				case PD_UP, MD_UP, MAX_HP_UP, MAX_HP_CP_UP -> true;
				default -> false;
			};
		}
	}

	@Override
	public Status supportEffectStatus(MemberRef actor, int exactTargetObjectId, MemberCapability capability, int rebuffRemainingSeconds)
	{
		if ((exactTargetObjectId <= 0) || (capability == null) || !Set.of("combat.buff", "combat.song", "combat.dance").contains(capability.capabilityKey()))
		{
			return Status.MISSING;
		}
		try (AcquiredPlayer acquired = acquire(actor))
		{
			final Player target = World.getInstance().getPlayer(exactTargetObjectId);
			if ((acquired == null) || (target == null))
			{
				return Status.MISSING;
			}
			final Player player = acquired.player();
			final Skill skill = player.getKnownSkill(capability.actionSkillId());
			if ((skill == null) || (skill.getLevel() != capability.actionSkillLevel()) || (player.getInstanceId() != target.getInstanceId()) || ((player != target) && ((player.getParty() == null) || (player.getParty() != target.getParty()))))
			{
				return Status.MISSING;
			}
			return PhantomSupportEffectAuthority.status(target, skill, rebuffRemainingSeconds);
		}
	}

	@Override
	public boolean materialize(long profileId)
	{
		final var existing = _materialization.find(profileId).orElse(null);
		if (existing != null)
		{
			// An active actor has already passed admission. Re-entering its background admission rejects MATERIALIZED.
			try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (action == null) { return false; }
				final Player player = action.player();
				final var owner = player.getNativeWorkOwner();
				return existing.worldPresent() && player.hasHeadlessOutboundSession() && player.getObjectId() == existing.characterObjectId()
					&& World.getInstance().getPlayer(player.getObjectId()) == player && World.getInstance().findObject(player.getObjectId()) == player
					&& owner != null && owner.player() == player && owner.isCurrent() && owner.nativeObservationHealthy()
					&& owner.epoch() == existing.materializedAtNanos();
			}
		}
		return switch (_materialization.materialize(profileId).status())
		{
			case SUCCESS, ALREADY_ACTIVE -> true;
			default -> false;
		};
	}

	private MemberSnapshot snapshot(MemberRef member, Player player)
	{
		final WorldObject target = player.getTarget();
		final List<Integer> attackers = World.getInstance().getVisibleObjects(player, Monster.class, npc -> !npc.isDead() && (npc.getInstanceId() == player.getInstanceId()) && npc.hasAI() && (npc.getAI().getIntention() == Intention.ATTACK) && (npc.getAI().getAttackTarget() == player)).stream().map(Creature::getObjectId).distinct().sorted().limit(32).toList();
		final List<MemberCapability> capabilities = member.kind() == MemberKind.PHANTOM ? nativeCapabilities(player, phantomCapabilities(member.profileId(), 0), 0) : realCapabilities(player, 0);
		final String progressionHash = _progression.findCatalog().map(catalog -> catalog.combinedHash()).orElse("0".repeat(64));
		return new MemberSnapshot(member, player.getActiveClass(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), percent(player.getCurrentHp(), player.getMaxHp()), percent(player.getCurrentMp(), player.getMaxMp()), percent(player.getCurrentCp(), player.getMaxCp()), player.isDead(), player.isCastingNow(), player.isAttackingNow(), player.isMoving(), target == null ? 0 : target.getObjectId(), attackers, capabilities, progressionHash);
	}

	private List<MemberCapability> phantomCapabilities(long profileId, int targetObjectId)
	{
		return _progression.capabilities(profileId, targetObjectId > 0 ? targetObjectId : null).stream().map(L2jPhantomPartyBackend::capability).toList();
	}

	private static List<MemberCapability> nativeCapabilities(Player player, List<MemberCapability> authored, int targetObjectId)
	{
		final var result = new TreeMap<String, MemberCapability>();
		for (var capability : authored)
		{
			final Skill learned = player.getKnownSkill(capability.actionSkillId());
			final boolean known = capability.learned() && learned != null && learned.getLevel() >= capability.actionSkillLevel();
			final var observed = new MemberCapability(capability.capabilityKey(), capability.variantKey(), capability.rank(), capability.actionSkillId(), known ? learned.getLevel() : capability.actionSkillLevel(), capability.targetScope(), capability.intrinsic(), known, known && capability.readyNow() && !player.isSkillDisabled(learned) && player.checkDoCastConditions(learned), capability.readinessReason(), capability.contextualScore(), capability.provenance());
			result.put(observed.identity(), observed);
		}
		final var weapon = player.getActiveWeaponItem();
		final boolean armed = weapon != null && weapon != player.getFistsWeaponItem() && player.getActiveWeaponInstance() != null && weapon.getCrystalType().getLevel() <= player.getExpertiseLevel();
		if (armed)
		{
			final String key = weapon.getItemType() == WeaponType.BOW || weapon.getItemType() == WeaponType.CROSSBOW ? "combat.ranged_physical_damage" : "combat.melee_damage";
			final var basic = new MemberCapability(key, "native.weapon", 300, 0, 0, "SINGLE_TARGET", true, true, !player.isAlikeDead(), "native.weapon", 300, "native.equipped_weapon+expertise");
			result.put(basic.identity(), basic);
		}
		if (player.getAllSkills().size() <= PhantomNativeCombatCapability.MAXIMUM_SKILLS)
		{
			final WorldObject target = targetObjectId > 0 ? World.getInstance().findObject(targetObjectId) : null;
			for (Skill skill : player.getAllSkills())
			{
				final double mp = Math.max(skill.getMpInitialConsume() + skill.getMpConsume(), player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill));
				final boolean affordable = !player.isAlikeDead() && !player.isSkillDisabled(skill) && player.getCurrentMp() >= mp && player.checkDoCastConditions(skill);
				final String support = PhantomSupportEffectAuthority.nativeCapability(skill);
				if (support != null)
				{
					final String scope = PhantomSupportEffectAuthority.nativeScope(skill);
					final boolean targetReady = targetObjectId <= 0 || target instanceof Player recipient && targetCompatible(player, recipient, scope) && skill.checkCondition(player, recipient, false);
					final var capability = new MemberCapability(support, PhantomSupportEffectAuthority.nativeVariant(skill), 450, skill.getId(), skill.getLevel(), scope, true, true, affordable && targetReady, affordable && targetReady ? "ready" : "native.unavailable", 450, "native.learned_skill+effect+mp+reuse");
					result.put(capability.identity(), capability);
				}
				if (armed && PhantomCombatSkillSafety.supportsNpcAggro(skill, player.isTransformed()))
				{
					final var capability = new MemberCapability("combat.tank", "native.aggro." + skill.getId(), 800, skill.getId(), skill.getLevel(), "SINGLE_TARGET", true, true, affordable, affordable ? "ready" : "native.unavailable", (int) Math.min(10000, Math.max(0, player.getPDef(null))), "native.learned_aggression+equipped_weapon+defense");
					result.put(capability.identity(), capability);
				}
			}
		}
		return result.values().stream().limit(256).toList();
	}

	private List<MemberCapability> realCapabilities(Player player, int targetObjectId)
	{
		final List<MemberCapability> result = new ArrayList<>();
		for (CapabilityRule rule : _progression.findCatalog().map(catalog -> catalog.capabilities(player.getActiveClass())).orElse(List.of()))
		{
			final Skill skill = player.getKnownSkill(rule.actionSkill().skillId());
			final boolean learned = (skill != null) && (skill.getLevel() >= rule.actionSkill().skillLevel());
			final WorldObject targetObject = targetObjectId > 0 ? World.getInstance().findObject(targetObjectId) : null;
			final boolean targetReady = !rule.targetRequired() || (learned && (targetObject instanceof Player target) && targetCompatible(player, target, rule.targetScope().name()) && skill.checkCondition(player, target, false));
			final boolean ready = learned && !player.isDead() && !player.isCastingNow() && targetReady && !player.isSkillDisabled(skill) && player.checkDoCastConditions(skill);
			final String reason = !learned ? "skill.missing" : !targetReady ? (targetObjectId > 0 ? "target.invalid" : "target.required") : ready ? "ready" : "skill.unavailable";
			result.add(new MemberCapability(rule.capabilityKey(), rule.variantKey(), rule.rank(), rule.actionSkill().skillId(), learned ? skill.getLevel() : rule.actionSkill().skillLevel(), rule.targetScope().name(), true, learned, ready, reason, Math.max(1, percent(player.getCurrentMp(), player.getMaxMp()) * 10), "progression.catalog+real.current_player"));
		}
		return result;
	}

	private static boolean targetCompatible(Player actor, Player target, String targetScope)
	{
		if ("SELF".equals(targetScope))
		{
			return actor == target;
		}
		return Set.of("SINGLE_TARGET", "PARTY", "PARTY_MEMBER", "ALLY").contains(targetScope) && ((actor == target) || ((actor.getParty() != null) && (actor.getParty() == target.getParty()))) && (actor.getInstanceId() == target.getInstanceId());
	}

	private static MemberCapability capability(CapabilityEvaluation evaluation)
	{
		return new MemberCapability(evaluation.capabilityKey(), evaluation.variantKey(), evaluation.rank(), evaluation.actionSkill().skillId(), evaluation.actionSkill().skillLevel(), evaluation.targetScope().name(), evaluation.intrinsic(), evaluation.learned(), evaluation.readyNow(), evaluation.reason().name().toLowerCase(Locale.ROOT), evaluation.rank() * 10, "progression.capability_evaluation");
	}

	private MemberRef reference(Player player)
	{
		final Optional<PhantomProfile> profile = _profiles.findByCharacterObjectId(player.getObjectId());
		return profile.isPresent() ? MemberRef.phantom(profile.get().profileId(), player.getObjectId()) : MemberRef.real(player.getObjectId());
	}

	private AcquiredPlayer acquire(MemberRef member)
	{
		if (member.kind() == MemberKind.REAL)
		{
			final Player player = World.getInstance().getPlayer(member.characterObjectId());
			return player == null ? null : new AcquiredPlayer(player, null);
		}
		final Optional<ActionLease> lease = _materialization.tryAcquireAction(member.profileId());
		if (lease.isEmpty() || ((member.characterObjectId() > 0) && (lease.get().player().getObjectId() != member.characterObjectId())))
		{
			lease.ifPresent(ActionLease::close);
			return null;
		}
		return new AcquiredPlayer(lease.get().player(), lease.get());
	}

	private static int percent(double current, double maximum)
	{
		return maximum <= 0 ? 0 : Math.max(0, Math.min(100, (int) Math.round((current * 100.0) / maximum)));
	}

	private record AcquiredPlayer(Player player, ActionLease lease) implements AutoCloseable
	{
		@Override
		public void close()
		{
			if (lease != null)
			{
				lease.close();
			}
		}
	}
}

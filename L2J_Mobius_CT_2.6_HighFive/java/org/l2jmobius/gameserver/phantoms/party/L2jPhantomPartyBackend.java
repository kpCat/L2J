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
	// This is a World census budget, independent of the size of one Party/CommandChannel.
	private static final int MAX_RESTART_ABSENCE_PLAYERS = 256;
	private java.util.function.BiFunction<Long, Integer, List<Long>> _scheduledFarmDiscovery;
	private java.util.function.LongPredicate _farmAdmission = _ -> false;
	private long _scheduledFarmCursor;

	public void installScheduledFarmDiscovery(java.util.function.BiFunction<Long, Integer, List<Long>> discovery, java.util.function.LongPredicate admitted)
	{
		_scheduledFarmDiscovery = java.util.Objects.requireNonNull(discovery);
		_farmAdmission = java.util.Objects.requireNonNull(admitted);
	}
	private java.util.function.LongPredicate _scheduledMaterialize = this::materialize;
	public void installScheduledMaterialization(java.util.function.LongPredicate materialize) { _scheduledMaterialize = java.util.Objects.requireNonNull(materialize); }
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final PhantomProgressionService _progression;
	private final PartyInvitationService _invitations;
	private final CommandChannelInvitationService _commandChannels;
	private java.util.function.BiPredicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef, Creature> _farmSourcePolicy = (_, _) -> false;
	private final java.util.concurrent.ConcurrentMap<Long, String> _farmWaitReasons = new java.util.concurrent.ConcurrentHashMap<>();
	private static final boolean TASK130_EVIDENCE = System.getProperty("phantom.task130.runtime") != null;
	private static final java.util.concurrent.atomic.AtomicInteger TASK130_SAMPLES = new java.util.concurrent.atomic.AtomicInteger();
	public void installFarmSourcePolicy(java.util.function.BiPredicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef, Creature> policy)
	{ _farmSourcePolicy = java.util.Objects.requireNonNull(policy); }
	@Override public String autonomousFarmWaitReason(MemberRef leader) { return _farmWaitReasons.getOrDefault(leader.profileId(), "WAIT_GROUP_TARGET"); }
	private boolean farmWait(PartySnapshot party, String reason)
	{
		if (_farmWaitReasons.containsKey(party.leader().profileId()) || _farmWaitReasons.size() < 1024)
		{
			final var previous = _farmWaitReasons.put(party.leader().profileId(), reason);
			if (TASK130_EVIDENCE && !reason.equals(previous) && TASK130_SAMPLES.getAndIncrement() < 180)
			{
				System.out.println("NATIVE130_COMBAT_GATE_WAIT leader=" + party.leader().profileId() + " reason=" + reason);
			}
		}
		return false;
	}

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
	public boolean withUnmaterializedNativeAbsence(MemberRef actor, java.util.function.BooleanSupplier reconcile)
	{
		if (actor.kind() != MemberKind.PHANTOM) { return false; }
		final var registry = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance();
		try (var lease = registry.tryAcquire(actor.characterObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM))
		{
			return lease != null && unmaterializedNativeAbsence(actor, lease) && reconcile.getAsBoolean();
		}
	}

	private boolean unmaterializedNativeAbsence(MemberRef actor, org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease lease)
	{
		if (actor.kind() != MemberKind.PHANTOM || !actor.equals(currentMember(actor.profileId()).orElse(null))) { return false; }
		final var retained = _materialization.find(actor.profileId()).orElse(null);
		if (retained != null && (retained.characterObjectId() != actor.characterObjectId() || retained.playerRetained() || retained.identityLeaseRetained() || retained.outboundAttached() || retained.actionAdmissionOpen() || retained.admittedActionCount() != 0 || retained.worldPresent())) { return false; }
		final int objectId = actor.characterObjectId();
		final var world = World.getInstance();
		final var owner = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().getOwnerSnapshot(objectId);
		if (world.findObject(objectId) != null || world.getPlayer(objectId) != null || owner == null || owner.token() != lease.token() || lease.isClosed()) { return false; }
		int examined = 0;
		for (Player player : world.getPlayers())
		{
			if (++examined > MAX_RESTART_ABSENCE_PLAYERS) { return false; }
			if (player.getActiveRequester() != null && player.getActiveRequester().getObjectId() == objectId) { return false; }
			final Party party = player.getParty();
			if (party == null) { continue; }
			if (party.getMembers().stream().anyMatch(member -> member.getObjectId() == objectId)) { return false; }
			final CommandChannel channel = party.getCommandChannel();
			if (channel != null && (channel.getParties().size() > MAX_FORCE_PARTIES || channel.getParties().stream().anyMatch(group -> group.getMembers().stream().anyMatch(member -> member.getObjectId() == objectId)))) { return false; }
		}
		return world.findObject(objectId) == null && actor.equals(currentMember(actor.profileId()).orElse(null));
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
		try (AcquiredPlayer acquired = observeActor(member))
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
		try (AcquiredPlayer acquired = observeActor(member))
		{
			if (acquired == null)
			{
				return Optional.empty();
			}
			return Optional.of(snapshot(member, acquired.player()));
		}
	}

	@Override
	public long materializationEpoch(MemberRef member)
	{
		if (member == null || member.kind() != MemberKind.PHANTOM) { return 0; }
		return _materialization.find(member.profileId()).filter(entry -> entry.worldPresent() && entry.characterObjectId() == member.characterObjectId())
			.map(entry -> entry.materializedAtNanos()).orElse(0L);
	}

	@Override
	public FarmPage materializedFarmMembers(long afterProfileId, int limit)
	{
		if (limit < 1 || limit > PhantomAutonomousFarmParty.MAXIMUM_CANDIDATES) { return new FarmPage(List.of(), afterProfileId); }
		if (_scheduledFarmDiscovery != null)
		{
			// One shared durable page, at most one ordinary admitted materialization attempt.
			// No capacity, ecology, historical catchup or visible-human retirement guard is bypassed.
			final var scheduled = _scheduledFarmDiscovery.apply(_scheduledFarmCursor, limit);
			if (scheduled.size() > limit || scheduled.stream().anyMatch(id -> id <= _scheduledFarmCursor)
				|| scheduled.stream().distinct().count() != scheduled.size()) { return new FarmPage(List.of(), afterProfileId); }
			_scheduledFarmCursor = scheduled.isEmpty() ? 0 : scheduled.getLast();
			for (long id : scheduled)
			{
				if (_farmAdmission.test(id) && _materialization.find(id).isEmpty()) { _scheduledMaterialize.test(id); break; }
			}
		}
		final List<MemberSnapshot> result = new ArrayList<>();
		long examined = afterProfileId;
		for (var entry : _materialization.list().stream().filter(entry -> entry.worldPresent() && entry.profileId() > afterProfileId).sorted(java.util.Comparator.comparingLong(entry -> entry.profileId())).limit(limit).toList())
		{
			examined = entry.profileId();
			final var member = currentMember(entry.profileId()).orElse(null);
			if (member == null) { continue; }
			try (var acquired = observeActor(member))
			{
				if (acquired != null && farmAvailable(acquired.player())) { result.add(snapshot(member, acquired.player())); }
			}
		}
		return new FarmPage(result, examined);
	}

	@Override
	public boolean autonomousFarmReady(PartySnapshot party)
	{
		if (party.members().size() < 2 || party.members().size() > 9 || party.members().stream().anyMatch(member -> member.kind() != MemberKind.PHANTOM)) { return false; }
		boolean damage = false;
		for (MemberRef member : party.members())
		{
			try (var acquired = observeActor(member))
			{
				if (acquired == null || !farmAvailable(acquired.player()) || acquired.player().getParty() == null || acquired.player().getParty().getLeader().getObjectId() != party.leader().characterObjectId()) { return false; }
				final var observed = snapshot(member, acquired.player());
				if (observed.hpPercent() < 25) { return false; }
				damage |= PhantomAutonomousFarmParty.farmContributor(observed);
			}
		}
		return damage;
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
		try (var acquired = observeActor(member))
		{
			if (acquired == null) { return false; }
			final var player = acquired.player();
			return farmAvailable(player) && farmIdle(player);
		}
	}

	@Override
	public boolean autonomousFarmReservationAvailable(MemberRef member)
	{
		if (member == null || member.kind() != MemberKind.PHANTOM) { return false; }
		try (var acquired = observeActor(member))
		{
			return acquired != null && farmAvailable(acquired.player()) && !acquired.player().isInParty();
		}
	}

	@Override
	public boolean autonomousFarmConsentAvailable(MemberRef requester, MemberRef invitee, InvitationIdentity identity)
	{
		if (requester == null || invitee == null || identity == null || requester.kind() != MemberKind.PHANTOM || invitee.kind() != MemberKind.PHANTOM || requester.equals(invitee)
			|| identity.requesterObjectId() != requester.characterObjectId() || identity.inviteeObjectId() != invitee.characterObjectId()) { return false; }
		try (var from = observeActor(requester); var to = observeActor(invitee))
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
		return autonomousFarmTarget(party, supportMembers, (org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef) null);
	}
	@Override
	public OptionalInt autonomousFarmTarget(PartySnapshot party, Set<MemberRef> supportMembers, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef source)
	{ return autonomousFarmTarget(party, supportMembers, source, null); }

	/** Bounded passive admission witness; it does not grant actions or change the guards. */
	public OptionalInt autonomousFarmTarget(PartySnapshot party, Set<MemberRef> supportMembers, java.util.function.Consumer<String> witness)
	{ return autonomousFarmTarget(party, supportMembers, null, witness); }
	public OptionalInt autonomousFarmTarget(PartySnapshot party, Set<MemberRef> supportMembers, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef source, java.util.function.Consumer<String> witness)
	{
		if (source == null) { farmWait(party, "WAIT_GROUP_SOURCE"); return OptionalInt.empty(); }
		if (party.members().size() < 2 || party.members().size() > 9 || !party.members().containsAll(supportMembers) || party.members().stream().anyMatch(member -> member.kind() != MemberKind.PHANTOM)) { return OptionalInt.empty(); }
		final List<AcquiredPlayer> leases = new ArrayList<>();
		try
		{
			final List<Player> actors = new ArrayList<>();
			final List<MemberSnapshot> members = new ArrayList<>();
			for (var member : party.members())
			{
				final var acquired = observeActor(member);
				if (acquired == null) { if (witness != null) { witness.accept("actor.unavailable=" + member.profileId()); } return OptionalInt.empty(); }
				leases.add(acquired);
				final Player player = acquired.player();
				if (!farmAvailable(player) || player.getParty() == null || player.getParty().getLeader().getObjectId() != party.leader().characterObjectId()) { return OptionalInt.empty(); }
				actors.add(player); members.add(snapshot(member, player));
			}
			final Player leader = actors.stream().filter(player -> player.getObjectId() == party.leader().characterObjectId()).findFirst().orElseThrow();
			final var leaderState = members.stream().filter(member -> member.ref().equals(party.leader())).findFirst().orElseThrow();
			final var band = PhantomPartyFarmEligibility.forRoster(actors.stream().map(Player::getLevel).toList()).orElse(null);
			if (band == null) { farmWait(party, "WAIT_GROUP_LEVEL_BAND"); return OptionalInt.empty(); }
			if (members.stream().anyMatch(member -> !PhantomAutonomousFarmParty.compatible(leaderState, member)) || actors.stream().anyMatch(player -> Math.abs(player.getLevel() - leader.getLevel()) > 8)) { if (witness != null) { witness.accept("roster.compatibility=" + members.stream().map(member -> member.ref().profileId() + ":compatible=" + PhantomAutonomousFarmParty.compatible(leaderState, member) + ":support=" + PhantomAutonomousFarmParty.support(member) + ":MP=" + member.mpPercent()).toList()); } return OptionalInt.empty(); }
			final var selected = leader.getTarget() instanceof Monster current ? java.util.stream.Stream.<Creature>of(current) : java.util.stream.Stream.<Creature>empty();
			final var nearby = World.getInstance().getVisibleObjectsInRange(leader, Monster.class, PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE).stream()
				.sorted(java.util.Comparator.comparingDouble((Monster monster) -> leader.calculateDistance2D(monster)).thenComparingInt(Creature::getObjectId)).map(monster -> (Creature) monster);
			final var candidates = PhantomPartyFarmEligibility.farmTargets(java.util.stream.Stream.concat(selected, nearby), target ->
				target.isMonster() && !target.isRaid() && !target.isAlikeDead() && !target.isInvul() && target.isTargetable() && target.isSpawned()
					&& band.contains(target.getLevel()) && _farmSourcePolicy.test(source, target));
			String refusal = null;
			for (Creature target : candidates)
			{
				if (!band.contains(target.getLevel()) || !_farmSourcePolicy.test(source, target)) { continue; }
				if (!target.isMonster() || target.isRaid() || target.isAlikeDead() || target.isInvul() || !target.isTargetable() || !target.isSpawned()) { continue; }
				if (actors.stream().anyMatch(player -> player.getInstanceId() != target.getInstanceId() || !target.isAutoAttackable(player) || !PhantomEngagementPlanner.rewardingFarmTarget(player, target) || Math.abs((long) player.getZ() - target.getZ()) > 200 || !GeoEngine.getInstance().canSeeTarget(player, target) || !GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), target.getX(), target.getY(), target.getZ(), player.getInstanceId()))) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":native_geometry_or_reward"); } continue; }
				if (!farmApproachSafe(actors, members, target)) { if (witness != null) { witness.accept("target=" + target.getObjectId() + ":party_approach_unproven"); } continue; }
				final var reasons = new ArrayList<String>();
				if (groupViable(actors, members, target, value -> { if (reasons.size() < 64) { reasons.add(value); } if (witness != null) { witness.accept(value); } }, false))
				{ _farmWaitReasons.remove(party.leader().profileId()); return OptionalInt.of(target.getObjectId()); }
				if (refusal == null) { refusal = reasons.stream().filter(value -> value.startsWith("WAIT_")).findFirst().orElse("WAIT_GROUP_READINESS"); }
			}
			farmWait(party, refusal == null ? "WAIT_GROUP_TARGET" : refusal);
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

	/** Planning HP includes the champion divisor; live HP and regeneration use native units. */
	private static double nativeDamagePerSecond(double rawDamagePerSecond, double nativeHpDivisor)
	{
		if (!Double.isFinite(rawDamagePerSecond) || rawDamagePerSecond <= 0 || !Double.isFinite(nativeHpDivisor) || nativeHpDivisor < 1) { return Double.NaN; }
		return rawDamagePerSecond / nativeHpDivisor;
	}

	/** Combine factual native damage estimates; never require every member to solo the mob. */
	private static boolean groupViable(List<Player> actors, List<MemberSnapshot> members, Creature target, java.util.function.Consumer<String> witness, boolean continuing)
	{
		final var projected = L2jPhantomBackgroundAuthority.nativePlanningTarget(target, true);
		final var observedThreats = new java.util.LinkedHashMap<Creature, ThreatVictims>();
		final List<Monster> threats = PhantomPartyFarmEligibility.farmThreats(actors,
			player -> World.getInstance().getVisibleObjectsInRange(player, Monster.class, 1200).stream(),
			other ->
			{
				if (other == target || other.isAlikeDead()) { return false; }
				if (observedThreats.containsKey(other)) { return true; }
				final var ai = other.hasAI() ? other.getAI() : null;
				final boolean related = actors.stream().anyMatch(player -> ai != null && ai.getIntention() == org.l2jmobius.gameserver.ai.Intention.ATTACK && ai.getAttackTarget() == player
					|| other.getHating(player) > 0 && (player.getAttackByList().contains(other) || other.getTarget() == player));
				if (!related && !other.isCastingNow() && !other.isCastingSimultaneouslyNow()) { return false; }
				final var observation = threatVictims(other, actors, related);
				if (!observation.relevant()) { return false; }
				observedThreats.put(other, observation);
				return true;
			});
		if (threats.size() > 4 || threats.stream().anyMatch(Creature::isRaid)) { if (witness != null) { witness.accept("WAIT_THREAT:target=" + target.getObjectId() + ":threats=" + threats.size()); } return false; }
		final double[] intervals = new double[actors.size()];
		final double[] costs = new double[actors.size()];
		final double[] contributions = new double[actors.size()];
		final double[] exposures = new double[actors.size()];
		final long[] ammunition = new long[actors.size()]; java.util.Arrays.fill(ammunition, Long.MAX_VALUE);
		double damagePerSecond = 0;
		for (int index = 0; index < actors.size(); index++)
		{
			if (!PhantomAutonomousFarmParty.farmContributor(members.get(index))) { continue; }
			final Player player = actors.get(index);
			Skill skill = PhantomEngagementPlanner.offensiveSkill(player, target);
			if (skill != null && ((!continuing && (player.isSkillDisabled(skill) || !player.checkDoCastConditions(skill))) || !skill.checkCondition(player, target, false)
				|| player.getCurrentMp() < Math.max(skill.getMpInitialConsume() + skill.getMpConsume(), player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill))))
			{ skill = null; }
			final boolean weapon = members.get(index).capabilities().stream().anyMatch(capability -> "native.weapon".equals(capability.variantKey()) && capability.learned() && capability.readyNow());
			if (skill == null && !weapon || player.isDisabled() || player.isOutOfControl() || player.isSitting()) { continue; }
			final var combat = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, target, skill);
			if (skill != null)
			{
				final var mechanics = combat.forTarget(projected);
				if (mechanics == null) { continue; }
				final var cast = PhantomBackgroundModel.estimateCastCycle(combat.combat(), projected, mechanics).orElse(null);
				if (cast == null) { continue; }
				intervals[index] = cast.intervalMillis();
				costs[index] = combat.requiredMp();
				contributions[index] = cast.damagePerCast() * 1000d / cast.intervalMillis();
			}
			else
			{
				long duration = PhantomBackgroundModel.estimateEncounter(combat.combat(), projected).durationMillis();
				final var equipped = player.getActiveWeaponItem();
				if (weapon && equipped != null && equipped.isRange() && !player.isTransformed())
				{
					final var ammo = equipped.isBow() ? player.getInventory().findArrowForBow(equipped) : player.getInventory().findBoltForCrossBow(equipped);
					if (ammo == null || ammo.getOwnerId() != player.getObjectId() || ammo.getCount() <= 0) { continue; }
					final int attackMillis = Math.max(1, player.calculateTimeBetweenAttacks());
					intervals[index] = Math.max(1, (long) attackMillis + player.calculateReuseTime(equipped));
					costs[index] = Math.max(0, (int) player.calcStat(org.l2jmobius.gameserver.model.stats.Stat.BOW_MP_CONSUME_RATE, equipped.getMpConsume(), null, null));
					ammunition[index] = ammo.getCount();
					duration = Math.max(1, Math.round(duration * intervals[index] / attackMillis));
				}
				contributions[index] = projected.maximumHp() * 1000d / duration;
			}
			damagePerSecond += contributions[index];
		}
		final double nativeHpDivisor = target.isChampion() ? Math.max(1, org.l2jmobius.gameserver.config.custom.ChampionMonstersConfig.profile(target).hp()) : 1;
		damagePerSecond = nativeDamagePerSecond(damagePerSecond, nativeHpDivisor);
		final double nativeRegenPerSecond = Math.max(0, Formulas.calcHpRegen(target) / 3d);
		final double effective = damagePerSecond - nativeRegenPerSecond;
		if (!Double.isFinite(effective) || effective <= 0) { if (witness != null) { witness.accept("WAIT_GROUP_DAMAGE:target=" + target.getObjectId() + ":effectiveDPS=" + effective); } return false; }
		final double millis = PhantomAutonomousFarmParty.projectedFarmDuration(target.getCurrentHp(), damagePerSecond, nativeRegenPerSecond);
		if (!Double.isFinite(millis) || millis > 60_000) { if (witness != null) { witness.accept("WAIT_GROUP_DAMAGE:target=" + target.getObjectId() + ":durationMillis=" + millis + ":DPS=" + effective + ":HP=" + projected.maximumHp()); } return false; }
		final var initial = PhantomAutonomousFarmParty.farmPuller(members).orElse(null);
		final var victim = threatVictims(target, actors, true);
		if (!victim.refusal().isEmpty())
		{ if (witness != null) { witness.accept("WAIT_THREAT:target=" + target.getObjectId() + ":reason=" + victim.refusal()); } return false; }
		if (victim.objectIds().isEmpty() && (!victim.idle() || initial == null))
		{ if (witness != null) { witness.accept("WAIT_TANK_AGGRO:target=" + target.getObjectId() + ":reason=" + victim.reason()); } return false; }
		final var mainVictims = victim.objectIds().isEmpty() ? Set.of(initial.characterObjectId()) : victim.objectIds();
		final Player recipient = actors.stream().filter(player -> mainVictims.contains(player.getObjectId()))
			.min(java.util.Comparator.comparingDouble((Player player) -> player.getCurrentHp() / player.getMaxHp()).thenComparingInt(Player::getObjectId)).orElseThrow();
		final int mainVictim = recipient.getObjectId();
		boolean healerPresent = false;
		Skill readyHeal = null;
		int healerObjectId = 0;
		long supportCastMillis = 0;
		final boolean singleThreat = mainVictims.size() == 1 && observedThreats.isEmpty();
		for (int index = 0; index < actors.size(); index++)
		{
			if (!PhantomAutonomousFarmParty.healingSupport(members.get(index))) { continue; }
			healerPresent = true;
			final Player healer = actors.get(index);
			readyHeal = readyFarmHeal(healer, recipient, members.get(index));
			if (readyHeal != null)
			{
				healerObjectId = healer.getObjectId();
				if (singleThreat && healer != recipient) { supportCastMillis = farmHealCastMillis(healer, readyHeal); }
				break;
			}
			if (continuing && !victim.idle() && singleThreat && healer != recipient && supportCastMillis == 0)
			{
				final var activeHeal = observedFarmHeal(healer, recipient);
				if (activeHeal != null)
				{
					healerObjectId = healer.getObjectId();
					supportCastMillis = farmHealCastMillis(healer, activeHeal);
				}
			}
		}
		if ((!continuing || victim.idle()) && healerPresent && readyHeal == null)
		{
			if (witness != null) { witness.accept("WAIT_GROUP_HEAL:target=" + target.getObjectId() + ":recipient=" + mainVictim + ":learned_mp_reuse_range_or_target"); }
			return false;
		}
		for (var entry : observedThreats.entrySet())
		{
			final var observed = entry.getValue();
			if (!observed.refusal().isEmpty() || observed.objectIds().isEmpty())
			{ if (witness != null) { witness.accept("WAIT_THREAT:target=" + entry.getKey().getObjectId() + ":reason=" + (observed.refusal().isEmpty() ? observed.reason() : observed.refusal())); } return false; }
		}
		for (int index = 0; index < actors.size(); index++)
		{
			final Player player = actors.get(index);
			final double actions = intervals[index] > 0 ? PhantomPartyFarmEligibility.requiredActions(millis, intervals[index]) : 0;
			if (actions > ammunition[index]) { if (witness != null) { witness.accept("WAIT_GROUP_AMMO:target=" + target.getObjectId() + ":actor=" + player.getObjectId() + ":shots=" + actions + ":ammo=" + ammunition[index]); } return false; }
			if (actions * costs[index] > player.getCurrentMp() * 0.8) { if (witness != null) { witness.accept("WAIT_GROUP_MP:target=" + target.getObjectId() + ":actor=" + player.getObjectId() + ":actionMPRequired=" + actions * costs[index] + ":MP=" + player.getCurrentMp()); } return false; }
			final double exposure = PhantomPartyFarmEligibility.threatExposureMillis(millis, mainVictims.contains(player.getObjectId()), supportCastMillis);
			double incoming = farmIncomingDamage(player, target, exposure);
			for (var entry : observedThreats.entrySet())
			{
				incoming += farmIncomingDamage(player, entry.getKey(), PhantomPartyFarmEligibility.threatExposureMillis(millis, entry.getValue().objectIds().contains(player.getObjectId())));
			}
			exposures[index] = incoming;
			if (!Double.isFinite(incoming) || incoming >= player.getCurrentHp() * 0.6)
			{
				if (witness != null)
				{
					witness.accept((victim.idle() ? "WAIT_TANK_AGGRO" : "WAIT_THREAT") + ":target=" + target.getObjectId() + ":actor=" + player.getObjectId()
						+ ":incoming=" + incoming + ":HP=" + player.getCurrentHp() + ":durationMillis=" + millis + ":exposureMillis=" + exposure
						+ ":supportCastMillis=" + supportCastMillis + ":otherThreats=" + observedThreats.size() + ":heal=" + (readyHeal == null ? 0 : readyHeal.getId()));
				}
				return false;
			}
		}
		final var changedThreat = !victim.current().getAsBoolean() ? target : observedThreats.entrySet().stream()
			.filter(entry -> !entry.getValue().current().getAsBoolean()).map(java.util.Map.Entry::getKey).findFirst().orElse(null);
		if (changedThreat != null)
		{ if (witness != null) { witness.accept("WAIT_THREAT:target=" + changedThreat.getObjectId() + ":reason=native.threat.changed"); } return false; }
		if (TASK130_EVIDENCE && TASK130_SAMPLES.getAndIncrement() < 180)
		{
			System.out.println("NATIVE130_D03 target=" + target.getObjectId() + " level=" + target.getLevel() + " continuing=" + continuing
				+ " effectiveDps=" + effective + " durationMs=" + millis + " contributions=" + java.util.Arrays.toString(contributions)
				+ " intervalsMs=" + java.util.Arrays.toString(intervals) + " mpPerAction=" + java.util.Arrays.toString(costs) + " ammo=" + java.util.Arrays.toString(ammunition)
				+ " roster=" + actors.stream().map(player -> player.getObjectId() + ":class=" + player.getActiveClass() + ":level=" + player.getLevel()
					+ ":hp=" + player.getCurrentHp() + ":mp=" + player.getCurrentMp() + ":pDef=" + player.getPDef(target) + ":mDef=" + player.getMDef(target, null)
					+ ":weapon=" + (player.getActiveWeaponInstance() == null ? 0 : player.getActiveWeaponInstance().getId())).toList()
				+ " capabilities=" + members.stream().map(member -> member.capabilities().stream().filter(MemberCapability::learned)
					.filter(cap -> cap.capabilityKey().startsWith("combat.") || cap.capabilityKey().startsWith("profession.")).limit(24)
					.map(cap -> cap.capabilityKey() + ':' + cap.actionSkillId() + ':' + cap.actionSkillLevel() + ':' + cap.readyNow()).toList()).toList());
			System.out.println("NATIVE130_D04 target=" + target.getObjectId() + " observedVictims=" + victim.objectIds() + " plannedVictims=" + mainVictims
				+ " idle=" + victim.idle() + " threatCount=" + threats.size() + " threatVictims="
				+ observedThreats.entrySet().stream().map(entry -> entry.getKey().getObjectId() + "->" + entry.getValue().objectIds()).toList()
				+ " exposureMsReserve=6000 supportCastMs=" + supportCastMillis + " projectedDamage=" + java.util.Arrays.toString(exposures));
			System.out.println("NATIVE130_D05 target=" + target.getObjectId() + " healerPresent=" + healerPresent + " caster=" + healerObjectId
				+ " recipient=" + mainVictim + " readySkill=" + (readyHeal == null ? "none" : readyHeal.getId() + ":" + readyHeal.getLevel()) + " projectedHpCredit=0");
		}
		return true;
	}

	private static double farmIncomingDamage(Player player, Creature threat, double exposureMillis)
	{
		final var defender = L2jPhantomBackgroundAuthority.nativePlanningCombat(player, threat, null).combat();
		final var attacker = L2jPhantomBackgroundAuthority.nativePlanningTarget(threat, true, player);
		return PhantomBackgroundModel.incomingDamage(defender, attacker, exposureMillis);
	}

	private static long farmHealCastMillis(Player healer, Skill skill)
	{
		if (skill.isChanneling()) { return 0; }
		final long base = (long) skill.getHitTime() + skill.getCoolTime();
		// Use the uncharged cast as a conservative reaction window.
		return Math.max(550, skill.isStatic() ? base : Formulas.calcAtkSpd(healer, skill, base));
	}

	private static Skill observedFarmHeal(Player healer, Player recipient)
	{
		if (healer.isAlikeDead() || healer.isOutOfControl()) { return null; }
		final var observation = healer.observeNativeMagicUse(16);
		for (var cast : new org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask.TargetObservation[] {observation.primary(), observation.secondary()})
		{
			if (cast == null || !cast.nativeLifetimeOwned() || cast.status() != org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask.TargetStatus.READY
				|| cast.targets().stream().noneMatch(target -> target == recipient)) { continue; }
			final var skill = cast.skill();
			if (skill != null && healer.getKnownSkill(skill.getId()) == skill && "combat.heal".equals(PhantomSupportEffectAuthority.nativeCapability(skill))
				&& observation.isCurrent()) { return skill; }
		}
		return null;
	}

	private static Skill readyFarmHeal(Player healer, Player recipient, MemberSnapshot member)
	{
		for (var capability : member.capabilities())
		{
			final var skill = readyFarmHeal(healer, recipient, capability);
			if (skill != null) { return skill; }
		}
		return null;
	}

	private static Skill readyFarmHeal(Player healer, Player recipient, MemberCapability capability)
	{
		if (!"combat.heal".equals(capability.capabilityKey()) || !capability.intrinsic() || !capability.learned()
			|| healer.isAlikeDead() || healer.isCastingNow() || healer.isCastingSimultaneouslyNow() || healer.isOutOfControl()
			|| healer.isSitting() || healer.isStunned() || healer.isSleeping() || healer.isParalyzed() || recipient.isAlikeDead() || recipient.isInvul()) { return null; }
		final Skill skill = healer.getKnownSkill(capability.actionSkillId());
		if (skill == null || skill.getLevel() != capability.actionSkillLevel() || !"combat.heal".equals(PhantomSupportEffectAuthority.nativeCapability(skill))) { return null; }
		final String scope = PhantomSupportEffectAuthority.nativeScope(skill);
		if (!targetCompatible(healer, recipient, scope) || healer.isSkillDisabled(skill) || !healer.checkDoCastConditions(skill)
			|| healer.getCharges() < skill.getChargeConsumeCount() || !skill.checkCondition(healer, recipient, false)) { return null; }
		final double mp = Math.max(skill.getMpInitialConsume() + skill.getMpConsume(), healer.getStat().getMpInitialConsume(skill) + healer.getStat().getMpConsume(skill));
		if (healer.getCurrentMp() < mp || !skill.getTargetList(healer, false, recipient).contains(recipient)) { return null; }
		final boolean casterCentered = skill.getTargetList(healer, true, recipient).contains(healer);
		final int range = casterCentered ? Math.max(200, skill.getCastRange() + 100) : Math.max(0, Math.min(skill.getCastRange(), healer.getMagicalAttackRange(skill)));
		return healer == recipient || healer.calculateDistance3D(recipient) <= range && GeoEngine.getInstance().canSeeTarget(healer, recipient) ? skill : null;
	}

	private record ThreatVictims(Set<Integer> objectIds, boolean idle, String reason, String refusal, boolean relevant,
		java.util.function.BooleanSupplier current) { }
	private static ThreatVictims threatVictims(Creature threat, List<Player> actors, boolean related)
	{
		final var ai = threat.hasAI() ? threat.getAI() : null;
		final Creature attacked = ai == null ? null : ai.getAttackTarget();
		final var intention = ai == null ? null : ai.getIntention();
		final boolean attacking = threat.isAttackingNow() || threat.isRangeAttackingNow();
		final var nativeCasts = threat.observeNativeMagicUse(16);
		final var cast = PhantomPartyFarmEligibility.observedCastVictims(nativeCasts, actors);
		final var recipients = new HashSet<>(cast.recipients());
		final boolean melee = attacking || !cast.hostile() && intention == org.l2jmobius.gameserver.ai.Intention.ATTACK;
		if (melee && attacked != null && actors.stream().anyMatch(player -> player == attacked)) { recipients.add(attacked.getObjectId()); }
		final Creature hated = threat instanceof Monster monster ? monster.getMostHated() : null;
		final boolean idle = !cast.hostile() && !melee && hated == null;
		final boolean relevant = !recipients.isEmpty() || related && (!cast.hostile() || !cast.refusal().isEmpty() || attacking);
		final String reason = idle ? "native.idle.safe_pull_required" : cast.hostile() ? "native.cast.foreign_victim" : "native.attack_target.unknown_or_foreign";
		return new ThreatVictims(Set.copyOf(recipients), idle, reason, cast.refusal(), relevant,
			() -> nativeCasts.isCurrent() && threat.hasAI() == (ai != null) && (ai == null || threat.getAI() == ai && ai.getAttackTarget() == attacked && ai.getIntention() == intention)
				&& (threat.isAttackingNow() || threat.isRangeAttackingNow()) == attacking
				&& (!(threat instanceof Monster monster) || monster.getMostHated() == hated));
	}

	@Override
	public boolean autonomousFarmEncounterReady(PartySnapshot party, Set<MemberRef> supportMembers, org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef source, int targetObjectId)
	{
		if (party == null || source == null) { return false; }
		final var object = World.getInstance().findObject(targetObjectId);
		// A completed native kill retains its own corpse/pickup owner; this is not permission for another pull.
		if (object == null || object instanceof Monster corpse && corpse.isAlikeDead()) { return true; }
		if (!(object instanceof Monster target) || target.isRaid() || !_farmSourcePolicy.test(source, target)) { return farmWait(party, "WAIT_GROUP_SOURCE"); }
		final var leases = new ArrayList<AcquiredPlayer>();
		try
		{
			final var actors = new ArrayList<Player>(); final var members = new ArrayList<MemberSnapshot>();
			for (var member : party.members())
			{
				final var acquired = observeActor(member); if (acquired == null) { return farmWait(party, "WAIT_NATIVE_OWNER"); }
				leases.add(acquired); final var player = acquired.player();
				if (!farmAvailable(player) || player.getParty() == null || player.getParty().getLeader().getObjectId() != party.leader().characterObjectId() || player.getInstanceId() != target.getInstanceId()) { return farmWait(party, "WAIT_NATIVE_OWNER"); }
				actors.add(player); members.add(snapshot(member, player));
			}
			final var band = PhantomPartyFarmEligibility.forRoster(actors.stream().map(Player::getLevel).toList()).orElse(null);
			if (band == null || !band.contains(target.getLevel())) { return farmWait(party, "WAIT_GROUP_LEVEL_BAND"); }
			final var leader = actors.stream().filter(player -> player.getObjectId() == party.leader().characterObjectId()).findFirst().orElse(null);
			if (leader == null) { return farmWait(party, "WAIT_NATIVE_OWNER"); }
			return groupViable(actors, members, target, reason -> { if (reason.startsWith("WAIT_")) { farmWait(party, reason); } }, true);
		}
		finally { for (int index = leases.size() - 1; index >= 0; index--) { leases.get(index).close(); } }
	}

	@Override
	public OptionalInt currentLevel(MemberRef member)
	{
		try (AcquiredPlayer acquired = observeActor(member))
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
		try (AcquiredPlayer acquired = observeActor(actor))
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
		return capabilities(actor, exactTargetObjectId, false);
	}

	@Override
	public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId, boolean exactFarmHeal)
	{
		if (exactTargetObjectId <= 0)
		{
			return List.of();
		}
		if (actor.kind() == MemberKind.PHANTOM)
		{
			try (var acquired = acquire(actor))
			{
				return acquired == null ? List.of() : nativeCapabilities(acquired.player(), phantomCapabilities(actor.profileId(), exactTargetObjectId), exactTargetObjectId, exactFarmHeal);
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
		final List<MemberCapability> capabilities = member.kind() == MemberKind.PHANTOM ? nativeCapabilities(player, phantomCapabilities(member.profileId(), 0), 0, false) : realCapabilities(player, 0);
		final String progressionHash = _progression.findCatalog().map(catalog -> catalog.combinedHash()).orElse("0".repeat(64));
		return new MemberSnapshot(member, player.getActiveClass(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), percent(player.getCurrentHp(), player.getMaxHp()), percent(player.getCurrentMp(), player.getMaxMp()), percent(player.getCurrentCp(), player.getMaxCp()), player.isDead(), player.isCastingNow(), player.isAttackingNow(), player.isMoving(), target == null ? 0 : target.getObjectId(), attackers, capabilities, progressionHash);
	}

	private List<MemberCapability> phantomCapabilities(long profileId, int targetObjectId)
	{
		return _progression.capabilities(profileId, targetObjectId > 0 ? targetObjectId : null).stream().map(L2jPhantomPartyBackend::capability).toList();
	}

	private static List<MemberCapability> nativeCapabilities(Player player, List<MemberCapability> authored, int targetObjectId, boolean exactFarmHeal)
	{
		final var result = new TreeMap<String, MemberCapability>();
		final WorldObject target = targetObjectId > 0 ? World.getInstance().findObject(targetObjectId) : null;
		for (var capability : authored)
		{
			final Skill learned = player.getKnownSkill(capability.actionSkillId());
			final boolean known = capability.learned() && learned != null && learned.getLevel() >= capability.actionSkillLevel()
				&& (!"combat.tank".equals(capability.capabilityKey()) || PhantomCombatSkillSafety.supportsNpcAggro(learned, player.isTransformed()));
			final var observed = new MemberCapability(capability.capabilityKey(), capability.variantKey(), capability.rank(), capability.actionSkillId(), known ? learned.getLevel() : capability.actionSkillLevel(), capability.targetScope(), capability.intrinsic(), known, known && capability.readyNow() && !player.isSkillDisabled(learned) && player.checkDoCastConditions(learned), capability.readinessReason(), capability.contextualScore(), capability.provenance());
			result.put(observed.identity(), observed);
		}
		final var weapon = player.getActiveWeaponItem();
		final boolean armed = weapon != null && weapon != player.getFistsWeaponItem() && player.getActiveWeaponInstance() != null && weapon.getCrystalType().getLevel() <= player.getExpertiseLevel();
		if (armed)
		{
			final String key = weapon.getItemType() == WeaponType.BOW || weapon.getItemType() == WeaponType.CROSSBOW ? "combat.ranged_physical_damage" : "combat.melee_damage";
			final var ammo = !weapon.isRange() || player.isTransformed() ? null : weapon.isBow() ? player.getInventory().findArrowForBow(weapon) : player.getInventory().findBoltForCrossBow(weapon);
			final boolean resources = !weapon.isRange() || player.isTransformed() || ammo != null && ammo.getOwnerId() == player.getObjectId() && ammo.getCount() > 0
				&& player.getCurrentMp() >= Math.max(0, (int) player.calcStat(org.l2jmobius.gameserver.model.stats.Stat.BOW_MP_CONSUME_RATE, weapon.getMpConsume(), null, null));
			final var basic = new MemberCapability(key, "native.weapon", 300, 0, 0, "SINGLE_TARGET", true, true, resources && !player.isAlikeDead() && !player.isDisabled() && !player.isOutOfControl() && !player.isSitting(), resources ? "native.weapon" : "native.weapon.resources", 300, "native.equipped_weapon+expertise+status+resources");
			result.put(basic.identity(), basic);
		}
		if (player.getAllSkills().size() <= PhantomNativeCombatCapability.MAXIMUM_SKILLS)
		{
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
		final var bounded = result.values().stream().limit(256).toList();
		if (!exactFarmHeal || !(target instanceof Player recipient)) { return bounded; }
		final var immediateHeals = bounded.stream().filter(capability -> "combat.heal".equals(capability.capabilityKey()) && capability.readyNow()
			&& readyFarmHeal(player, recipient, capability) != null).collect(java.util.stream.Collectors.toSet());
		// Keep lawful stock approach/cast handling when there is no immediate Heal to prefer.
		return immediateHeals.isEmpty() ? bounded : bounded.stream().filter(capability -> !"combat.heal".equals(capability.capabilityKey())
			|| immediateHeals.contains(capability)).toList();
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

	private final ThreadLocal<Boolean> _executionCompletion = new ThreadLocal<>();
	@Override public <T> T withExecutionCompletion(java.util.function.Supplier<T> action)
	{
		final var previous = _executionCompletion.get(); _executionCompletion.set(Boolean.TRUE);
		try { return action.get(); }
		finally { if (previous == null) { _executionCompletion.remove(); } else { _executionCompletion.set(previous); } }
	}
	private AcquiredPlayer acquire(MemberRef member)
	{ return acquire(member, false); }
	private AcquiredPlayer observeActor(MemberRef member)
	{ return acquire(member, true); }
	private AcquiredPlayer acquire(MemberRef member, boolean observation)
	{
		if (member.kind() == MemberKind.REAL)
		{
			final Player player = World.getInstance().getPlayer(member.characterObjectId());
			return player == null ? null : new AcquiredPlayer(player, null);
		}
		final Optional<ActionLease> lease = observation ? _materialization.tryAcquireObservation(member.profileId()) : Boolean.TRUE.equals(_executionCompletion.get())
			? _materialization.tryAcquireCompletionAction(member.profileId()) : _materialization.tryAcquireAction(member.profileId());
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

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
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
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
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PvpProtection;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
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
				for (Creature creature : protectedPlayer.getAttackByList())
				{
					if (!(creature instanceof Player attacker) || attacker.isDead() || attacker.isAlikeDead() || (attacker.getInstanceId() != helperPlayer.getInstanceId()) || (attacker.getParty() == party))
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
			return phantomCapabilities(actor.profileId(), exactTargetObjectId);
		}
		try (AcquiredPlayer acquired = acquire(actor))
		{
			return acquired == null ? List.of() : realCapabilities(acquired.player(), exactTargetObjectId);
		}
	}

	@Override
	public boolean materialize(long profileId)
	{
		return switch (_materialization.materialize(profileId).status())
		{
			case SUCCESS, ALREADY_ACTIVE -> true;
			default -> false;
		};
	}

	private MemberSnapshot snapshot(MemberRef member, Player player)
	{
		final WorldObject target = player.getTarget();
		final List<Integer> attackers = player.getAttackByList().stream().filter(Creature::isMonster).map(Creature::getObjectId).distinct().sorted().limit(32).toList();
		final List<MemberCapability> capabilities = member.kind() == MemberKind.PHANTOM ? phantomCapabilities(member.profileId(), 0) : realCapabilities(player, 0);
		final String progressionHash = _progression.findCatalog().map(catalog -> catalog.combinedHash()).orElse("0".repeat(64));
		return new MemberSnapshot(member, player.getActiveClass(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), percent(player.getCurrentHp(), player.getMaxHp()), percent(player.getCurrentMp(), player.getMaxMp()), percent(player.getCurrentCp(), player.getMaxCp()), player.isDead(), player.isCastingNow(), player.isAttackingNow(), player.isMoving(), target == null ? 0 : target.getObjectId(), attackers, capabilities, progressionHash);
	}

	private List<MemberCapability> phantomCapabilities(long profileId, int targetObjectId)
	{
		return _progression.capabilities(profileId, targetObjectId > 0 ? targetObjectId : null).stream().map(L2jPhantomPartyBackend::capability).toList();
	}

	private List<MemberCapability> realCapabilities(Player player, int targetObjectId)
	{
		final List<MemberCapability> result = new ArrayList<>();
		for (CapabilityRule rule : _progression.findCatalog().map(catalog -> catalog.capabilities(player.getActiveClass())).orElse(List.of()))
		{
			final Skill skill = player.getKnownSkill(rule.actionSkill().skillId());
			final boolean learned = (skill != null) && (skill.getLevel() == rule.actionSkill().skillLevel());
			final WorldObject targetObject = targetObjectId > 0 ? World.getInstance().findObject(targetObjectId) : null;
			final boolean targetReady = !rule.targetRequired() || (learned && (targetObject instanceof Player target) && targetCompatible(player, target, rule.targetScope().name()) && skill.checkCondition(player, target, false));
			final boolean ready = learned && !player.isDead() && !player.isCastingNow() && targetReady && !player.isSkillDisabled(skill) && player.checkDoCastConditions(skill);
			final String reason = !learned ? "skill.missing" : !targetReady ? (targetObjectId > 0 ? "target.invalid" : "target.required") : ready ? "ready" : "skill.unavailable";
			result.add(new MemberCapability(rule.capabilityKey(), rule.variantKey(), rule.rank(), rule.actionSkill().skillId(), rule.actionSkill().skillLevel(), rule.targetScope().name(), true, learned, ready, reason, Math.max(1, percent(player.getCurrentMp(), player.getMaxMp()) * 10), "progression.catalog+real.current_player"));
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

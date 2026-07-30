/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
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

	public L2jPhantomPartyBackend(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomProgressionService progression)
	{
		_profiles = profiles;
		_materialization = materialization;
		_progression = progression;
		_invitations = PartyInvitationService.getInstance();
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
			final Player player = acquired.player();
			final WorldObject target = player.getTarget();
			final List<Integer> attackers = player.getAttackByList().stream().filter(Creature::isMonster).map(Creature::getObjectId).distinct().sorted().limit(32).toList();
			final List<MemberCapability> capabilities = member.kind() == MemberKind.PHANTOM ? phantomCapabilities(member.profileId()) : realCapabilities(player);
			final String progressionHash = _progression.findCatalog().map(catalog -> catalog.combinedHash()).orElse("0".repeat(64));
			return Optional.of(new MemberSnapshot(member, player.getActiveClass(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), percent(player.getCurrentHp(), player.getMaxHp()), percent(player.getCurrentMp(), player.getMaxMp()), percent(player.getCurrentCp(), player.getMaxCp()), player.isDead(), player.isCastingNow(), player.isMoving(), target == null ? 0 : target.getObjectId(), attackers, capabilities, progressionHash));
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

	private List<MemberCapability> phantomCapabilities(long profileId)
	{
		return _progression.capabilities(profileId, null).stream().map(L2jPhantomPartyBackend::capability).toList();
	}

	private List<MemberCapability> realCapabilities(Player player)
	{
		final List<MemberCapability> result = new ArrayList<>();
		for (CapabilityRule rule : _progression.findCatalog().map(catalog -> catalog.capabilities(player.getActiveClass())).orElse(List.of()))
		{
			final boolean learned = player.getKnownSkill(rule.actionSkill().skillId()) != null;
			result.add(new MemberCapability(rule.capabilityKey(), rule.variantKey(), rule.rank(), rule.actionSkill().skillId(), rule.actionSkill().skillLevel(), rule.targetScope().name(), true, learned, learned && !player.isDead() && !player.isCastingNow(), learned ? "ready" : "skill.missing", Math.max(1, percent(player.getCurrentMp(), player.getMaxMp()) * 10), "progression.catalog+real.current_player"));
		}
		return result;
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

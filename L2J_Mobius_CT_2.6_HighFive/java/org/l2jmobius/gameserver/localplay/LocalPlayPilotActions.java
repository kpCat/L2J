package org.l2jmobius.gameserver.localplay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Attackable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.PlayerChatIngress;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorAdmissionProfile;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorLocalityTarget;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;

/** Narrow native actions on the already connected, consented Player. */
public final class LocalPlayPilotActions
{
	public record Outcome(String status, String reason, Map<String, String> candidate)
	{
		public static Outcome of(String status, String reason)
		{
			return new Outcome(status, reason, null);
		}
	}

	private final Location _origin;
	private Location _candidatePosition;
	private int _candidateObjectId;
	private int _selectedMobObjectId;
	private final Set<Integer> _mobRoster = new HashSet<>();
	private int _ownedPartyLeaderObjectId;
	private InvitationIdentity _pendingOwnInvite;

	public LocalPlayPilotActions(Player actor)
	{
		_origin = actor.getLocation();
	}

	public static Map<String, String> snapshot(Player player)
	{
		final Map<String, String> value = new LinkedHashMap<>();
		value.put("x", Integer.toString(player.getX()));
		value.put("y", Integer.toString(player.getY()));
		value.put("z", Integer.toString(player.getZ()));
		value.put("instanceId", Integer.toString(player.getInstanceId()));
		value.put("sitting", Boolean.toString(player.isSitting()));
		value.put("moving", Boolean.toString(player.isMoving()));
		value.put("teleporting", Boolean.toString(player.isTeleporting()));
		value.put("partyId", Integer.toString(player.getParty() == null ? 0 : player.getParty().getLeaderObjectId()));
		value.put("hp", Double.toString(player.getCurrentHp()));
		value.put("mp", Double.toString(player.getCurrentMp()));
		value.put("targetId", Integer.toString(player.getTargetId()));
		value.put("online", Boolean.toString(player.isOnline()));
		value.put("actorObjectId", Integer.toString(player.getObjectId()));
		value.put("account", player.getAccountName());
		value.put("clientIdentity", player.getClient() == null ? "none" : Integer.toUnsignedString(System.identityHashCode(player.getClient())));
		value.put("identityOwner", String.valueOf(PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId())));
		value.put("worldPresent", Boolean.toString(World.getInstance().findObject(player.getObjectId()) == player));
		return Map.copyOf(value);
	}

	public Outcome execute(Player actor, LocalPlayPilotProtocol.Request request)
	{
		final Map<String, String> args = request.args();
		try
		{
			return switch (request.operation())
			{
				case STATUS, CAPABILITIES -> Outcome.of("SUCCEEDED", request.operation() == LocalPlayPilotProtocol.Operation.CAPABILITIES ? "STATUS,SNAPSHOT_PHANTOMS,SNAPSHOT_TARGETS,TELEPORT_SELF,MOVE_SELF,STOP_MOVE,SIT,STAND,SELECT_TARGET,SAY,PARTY_INVITE,PARTY_RESPOND,PARTY_LEAVE,ATTACK_NPC,CAST_LEARNED_SKILL" : "SNAPSHOT");
				case SNAPSHOT_PHANTOMS -> candidate(actor);
				case SNAPSHOT_TARGETS -> targets(actor);
				case SIT -> sit(actor);
				case STAND -> stand(actor);
				case STOP_MOVE -> stopMove(actor);
				case MOVE_SELF -> move(actor, args);
				case TELEPORT_SELF -> teleport(actor, args);
				case SELECT_TARGET -> selectTarget(actor, args);
				case SAY -> say(actor, args);
				case PARTY_INVITE -> invite(actor, args);
				case PARTY_RESPOND -> respond(actor, args);
				case PARTY_LEAVE -> leave(actor);
				case ATTACK_NPC -> attack(actor, args);
				case CAST_LEARNED_SKILL -> cast(actor, args);
			};
		}
		catch (IllegalArgumentException exception)
		{
			return Outcome.of("REJECTED", "INVALID_ARGUMENT");
		}
	}

	private static Outcome sit(Player actor)
	{
		if (actor.isDead() || actor.isInStoreMode())
		{
			return Outcome.of("REJECTED", "ACTOR_BUSY");
		}
		actor.sitDown();
		return Outcome.of(actor.isSitting() ? "SUCCEEDED" : "ACCEPTED", "NATIVE_SIT");
	}

	private static Outcome stand(Player actor)
	{
		actor.standUp();
		return Outcome.of(!actor.isSitting() ? "SUCCEEDED" : "ACCEPTED", "NATIVE_STAND");
	}

	private static Outcome stopMove(Player actor)
	{
		actor.stopMove(actor.getLocation());
		return Outcome.of(!actor.isMoving() ? "SUCCEEDED" : "ACCEPTED", "NATIVE_STOP_MOVE");
	}

	private Outcome move(Player actor, Map<String, String> args)
	{
		final int x = integer(args, "x");
		final int y = integer(args, "y");
		final int z = integer(args, "z");
		if (actor.isDead() || actor.isOutOfControl() || actor.isOverloaded() || actor.isInStoreMode() || (Math.hypot(x - actor.getX(), y - actor.getY()) > 400) || (Math.abs(z - actor.getZ()) > 200) || !GeoEngine.getInstance().canMoveToTarget(actor.getX(), actor.getY(), actor.getZ(), x, y, z, actor.getInstanceId()))
		{
			return Outcome.of("REJECTED", "MOVE_PRECONDITION");
		}
		actor.getAI().setIntention(Intention.MOVE_TO, new Location(x, y, z));
		actor.onActionRequest();
		return Outcome.of("ACCEPTED", "ARRIVAL_PENDING");
	}

	private Outcome teleport(Player actor, Map<String, String> args)
	{
		final int x = integer(args, "x");
		final int y = integer(args, "y");
		final int z = integer(args, "z");
		final int instanceId = integer(args, "instanceId");
		final boolean origin = samePosition(_origin, x, y, z, instanceId);
		final boolean candidate = (_candidatePosition != null) && samePosition(_candidatePosition, x, y, z, instanceId);
		final boolean nearby = (instanceId == actor.getInstanceId()) && (Math.hypot(x - actor.getX(), y - actor.getY()) <= 2000) && (Math.abs(z - actor.getZ()) <= 300) && GeoEngine.getInstance().canMoveToTarget(actor.getX(), actor.getY(), actor.getZ(), x, y, z, instanceId);
		if (actor.isDead() || actor.isInStoreMode() || actor.isTeleporting() || (instanceId != actor.getInstanceId()) || (!origin && !candidate && !nearby))
		{
			return Outcome.of("REJECTED", "TELEPORT_PRECONDITION");
		}
		actor.teleToLocation(new Location(x, y, z, actor.getHeading(), instanceId), false);
		return Outcome.of("ACCEPTED", "TELEPORT_COMPLETION_PENDING");
	}

	private static boolean samePosition(Location location, int x, int y, int z, int instanceId)
	{
		return (location.getX() == x) && (location.getY() == y) && (location.getZ() == z) && (location.getInstanceId() == instanceId);
	}

	private static int integer(Map<String, String> args, String key)
	{
		return Integer.parseInt(args.getOrDefault(key, ""));
	}

	private Outcome candidate(Player actor)
	{
		final PhantomTopologyPoint here = new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId());
		final OperatorLocalityTarget nearest = PhantomSystem.operatorNearestLocalityTarget(here).orElse(null);
		if (nearest == null)
		{
			_candidatePosition = null;
			_candidateObjectId = 0;
			return Outcome.of("SUCCEEDED", "NO_CANDIDATE");
		}
		final OperatorAdmissionProfile profile = PhantomSystem.operatorAdmissionProfile(nearest.profileId()).orElse(null);
		if ((profile == null) || !profile.admission().admitted() || profile.admission().pendingRebalance() || (nearest.committedPosition().instanceId() != actor.getInstanceId()))
		{
			return Outcome.of("SUCCEEDED", "NO_ADMITTED_CANDIDATE");
		}
		final PhantomTopologyPoint point = nearest.committedPosition();
		_candidatePosition = new Location(point.x(), point.y(), point.z(), actor.getHeading(), point.instanceId());
		_candidateObjectId = 0;
		final Map<String, String> data = new LinkedHashMap<>();
		data.put("profileId", Long.toString(nearest.profileId()));
		data.put("x", Integer.toString(point.x()));
		data.put("y", Integer.toString(point.y()));
		data.put("z", Integer.toString(point.z()));
		data.put("instanceId", Integer.toString(point.instanceId()));
		data.put("admitted", "true");
		data.put("materialized", Boolean.toString((profile.materialization() != null) && profile.materialization().worldPresent()));
		if ((profile.materialization() != null) && profile.materialization().worldPresent())
		{
			final int objectId = profile.materialization().characterObjectId();
			final WorldObject worldObject = World.getInstance().findObject(objectId);
			if ((worldObject instanceof Player target) && target.isOnline() && (target.getInstanceId() == actor.getInstanceId()))
			{
				_candidateObjectId = objectId;
				data.put("objectId", Integer.toString(objectId));
				data.put("name", target.getName());
			}
		}
		return new Outcome("SUCCEEDED", "CANDIDATE_SNAPSHOT", Map.copyOf(data));
	}

	private Outcome targets(Player actor)
	{
		_mobRoster.clear();
		_selectedMobObjectId = 0;
		final Map<String, String> data = new LinkedHashMap<>();
		final var mobs = World.getInstance().getVisibleObjectsInRange(actor, Attackable.class, 600, mob -> mob.isMonster() && !mob.isRaid() && !mob.isRaidMinion() && !mob.isDead() && (mob.getInstanceId() == actor.getInstanceId()) && mob.isVisibleFor(actor));
		mobs.sort(Comparator.comparingInt(Attackable::getObjectId));
		for (int index = 0; (index < mobs.size()) && (index < 3); index++)
		{
			final int objectId = mobs.get(index).getObjectId();
			_mobRoster.add(objectId);
			data.put("mobId" + (index + 1), Integer.toString(objectId));
		}
		return new Outcome("SUCCEEDED", "BOUNDED_TARGET_ROSTER", Map.copyOf(data));
	}

	private Outcome selectTarget(Player actor, Map<String, String> args)
	{
		final int objectId = integer(args, "targetObjectId");
		final WorldObject target = World.getInstance().findObject(objectId);
		if ((target == null) || (target.getInstanceId() != actor.getInstanceId()) || !target.isTargetable() || !target.isVisibleFor(actor) || (actor.calculateDistance3D(target) > 1250))
		{
			return Outcome.of("REJECTED", "TARGET_NOT_AVAILABLE");
		}
		if (objectId == _candidateObjectId)
		{
			actor.setTarget(target);
			return Outcome.of("SUCCEEDED", "SELECTED_CANDIDATE");
		}
		if (!_mobRoster.contains(objectId) || !(target instanceof Attackable mob) || !mob.isMonster() || mob.isRaid() || mob.isRaidMinion() || mob.isDead())
		{
			return Outcome.of("REJECTED", "TARGET_NOT_PERMITTED");
		}
		_selectedMobObjectId = objectId;
		actor.setTarget(target);
		return Outcome.of("SUCCEEDED", "SELECTED_MOB");
	}

	private Outcome say(Player actor, Map<String, String> args)
	{
		final ChatType channel = ChatType.valueOf(args.getOrDefault("channel", ""));
		final String target = args.getOrDefault("target", "");
		final String text = args.getOrDefault("text", "");
		if ((channel == ChatType.WHISPER) && ((_candidateObjectId <= 0) || !(World.getInstance().findObject(_candidateObjectId) instanceof Player candidate) || !candidate.getName().equals(target)))
		{
			return Outcome.of("REJECTED", "WHISPER_TARGET_NOT_CANDIDATE");
		}
		final PlayerChatIngress.Result result = PlayerChatIngress.dispatchPilot(actor, channel, target, text);
		return Outcome.of(result.dispatched() ? "ACCEPTED" : "REJECTED", result.status().name() + ":deliveries=" + result.observedDeliveries());
	}

	private Outcome invite(Player actor, Map<String, String> args)
	{
		if (actor.getParty() != null)
		{
			return Outcome.of("REJECTED", "PREEXISTING_PARTY");
		}
		final int targetId = integer(args, "targetObjectId");
		if ((targetId != _candidateObjectId) || (targetId <= 0) || !(World.getInstance().findObject(targetId) instanceof Player target) || (target.getInstanceId() != actor.getInstanceId()))
		{
			return Outcome.of("REJECTED", "INVITEE_NOT_CANDIDATE");
		}
		final int distributionTypeId = integer(args, "distributionTypeId");
		final PartyInvitationService.InviteResult result = PartyInvitationService.getInstance().invite(actor, target, distributionTypeId);
		if (result.delivered() && (actor.getParty() == null))
		{
			_ownedPartyLeaderObjectId = actor.getObjectId();
			_pendingOwnInvite = result.identity();
		}
		return Outcome.of(result.delivered() ? "SUCCEEDED" : "REJECTED", result.outcome().name() + (result.identity() == null ? "" : ":identity=" + result.identity().sequence() + "/" + result.identity().requesterObjectId() + "/" + result.identity().inviteeObjectId()));
	}

	private Outcome respond(Player actor, Map<String, String> args)
	{
		if (actor.getParty() != null)
		{
			return Outcome.of("REJECTED", "PREEXISTING_PARTY");
		}
		final InvitationIdentity identity = new InvitationIdentity(Long.parseLong(args.getOrDefault("sequence", "")), integer(args, "requesterObjectId"), actor.getObjectId());
		if ((identity.requesterObjectId() != _candidateObjectId) || (_candidateObjectId <= 0))
		{
			return Outcome.of("REJECTED", "INVITER_NOT_CANDIDATE");
		}
		final InvitationIdentity observed = PartyInvitationService.getInstance().observe(actor).map(PartyInvitationService.InvitationSnapshot::identity).orElse(null);
		if (!identity.equals(observed))
		{
			return Outcome.of("REJECTED", "STALE_INVITATION");
		}
		final PartyInvitationService.Response response = PartyInvitationService.Response.valueOf(args.getOrDefault("response", ""));
		final PartyInvitationService.RespondResult result = PartyInvitationService.getInstance().respond(actor, response, identity);
		if (result.accepted() && (actor.getParty() != null))
		{
			_ownedPartyLeaderObjectId = actor.getParty().getLeaderObjectId();
		}
		return Outcome.of(result.accepted() ? "SUCCEEDED" : "REJECTED", result.outcome().name());
	}

	private Outcome leave(Player actor)
	{
		if ((actor.getParty() == null) || (_ownedPartyLeaderObjectId <= 0) || (actor.getParty().getLeaderObjectId() != _ownedPartyLeaderObjectId) || (actor.getParty().getMembers().size() != 2) || !(World.getInstance().findObject(_candidateObjectId) instanceof Player candidate) || !actor.getParty().containsPlayer(candidate))
		{
			return Outcome.of("REJECTED", "PARTY_NOT_OWNED_BY_SCENARIO");
		}
		final PartyInvitationService.MembershipOutcome result = PartyInvitationService.getInstance().leave(actor);
		_ownedPartyLeaderObjectId = 0;
		_pendingOwnInvite = null;
		return Outcome.of(result == PartyInvitationService.MembershipOutcome.COMPLETED ? "SUCCEEDED" : "REJECTED", result.name());
	}

	public void cancelPendingInvitation()
	{
		if (_pendingOwnInvite != null)
		{
			PartyInvitationService.getInstance().cancel(_pendingOwnInvite);
			_pendingOwnInvite = null;
		}
	}

	private Outcome attack(Player actor, Map<String, String> args)
	{
		final int targetId = integer(args, "targetObjectId");
		final WorldObject target = World.getInstance().findObject(targetId);
		if ((targetId != _selectedMobObjectId) || !(target instanceof Attackable mob) || !mob.isMonster() || mob.isRaid() || mob.isRaidMinion() || mob.isDead() || (mob.getInstanceId() != actor.getInstanceId()) || !mob.isVisibleFor(actor))
		{
			return Outcome.of("REJECTED", "ATTACK_TARGET_NOT_PERMITTED");
		}
		mob.onForcedAttack(actor);
		return Outcome.of("ACCEPTED", "NATIVE_ATTACK_REQUEST");
	}

	private Outcome cast(Player actor, Map<String, String> args)
	{
		final Skill skill = actor.getKnownSkill(integer(args, "skillId"));
		final int targetId = integer(args, "targetObjectId");
		if ((skill == null) || actor.isDead() || actor.isSkillDisabled(skill) || ((targetId != actor.getObjectId()) && (targetId != _selectedMobObjectId)))
		{
			return Outcome.of("REJECTED", "SKILL_OR_TARGET_NOT_PERMITTED");
		}
		final TargetType targetType = skill.getTargetType();
		if (skill.isAOE() || ((targetType != TargetType.SELF) && (targetType != TargetType.ONE)) || ((targetType == TargetType.SELF) && (targetId != actor.getObjectId())))
		{
			return Outcome.of("REJECTED", "SKILL_TARGET_TYPE_NOT_PERMITTED");
		}
		final WorldObject target = (targetId == actor.getObjectId()) ? actor : World.getInstance().findObject(targetId);
		if ((target == null) || (target.getInstanceId() != actor.getInstanceId()))
		{
			return Outcome.of("REJECTED", "SKILL_TARGET_UNAVAILABLE");
		}
		actor.setTarget(target);
		return Outcome.of(actor.useMagic(skill, false, false) ? "ACCEPTED" : "REJECTED", "NATIVE_USE_MAGIC");
	}
}

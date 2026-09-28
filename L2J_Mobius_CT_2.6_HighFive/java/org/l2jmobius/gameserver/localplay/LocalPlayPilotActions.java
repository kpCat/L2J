package org.l2jmobius.gameserver.localplay;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding;
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
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;

/** Narrow native actions on the already connected, consented Player. */
public final class LocalPlayPilotActions
{
	private static final int M1_TARGET_SEARCH_RADIUS = 8000;
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
	private long _selectedTraceProfileId;
	private long _envelopeProfileId;
	private final Set<Integer> _mobRoster = new HashSet<>();
	private int _ownedPartyLeaderObjectId;
	private InvitationIdentity _pendingOwnInvite;

	public LocalPlayPilotActions(Player actor)
	{
		_origin = actor.getLocation().clone();
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
				case STATUS -> new Outcome("SUCCEEDED", "SNAPSHOT", Map.of("originX", Integer.toString(_origin.getX()), "originY", Integer.toString(_origin.getY()), "originZ", Integer.toString(_origin.getZ()), "originInstanceId", Integer.toString(_origin.getInstanceId())));
				case CAPABILITIES -> Outcome.of("SUCCEEDED", "STATUS,SNAPSHOT_PHANTOMS,PREPARE_M1_ENVELOPE,SNAPSHOT_M1_ENVELOPE,SELECT_VISIBLE_PHANTOM_TRACE,SNAPSHOT_SELECTED_PHANTOM_TRACE,REPLAY_SELECTED_PHANTOM_TRACE,SNAPSHOT_TARGETS,TELEPORT_SELF,MOVE_SELF,STOP_MOVE,SIT,STAND,SELECT_TARGET,SAY,PARTY_INVITE,PARTY_RESPOND,PARTY_LEAVE,ATTACK_NPC,CAST_LEARNED_SKILL");
				case SNAPSHOT_PHANTOMS -> candidate(actor);
				case PREPARE_M1_ENVELOPE -> prepareM1Envelope(actor, args);
				case SNAPSHOT_M1_ENVELOPE -> snapshotM1Envelope(actor);
				case SELECT_VISIBLE_PHANTOM_TRACE -> selectVisibleTrace(actor);
				case SNAPSHOT_SELECTED_PHANTOM_TRACE -> selectedTrace();
				case REPLAY_SELECTED_PHANTOM_TRACE -> replaySelectedTrace();
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

	private Outcome prepareM1Envelope(Player actor, Map<String, String> args)
	{
		final long afterProfileId = Long.parseLong(args.getOrDefault("afterProfileId", "0"));
		if (afterProfileId < 0)
		{
			return Outcome.of("REJECTED", "INVALID_ARGUMENT");
		}
		if (actor.isDead() || actor.isInStoreMode() || actor.isTeleporting() || actor.isMoving() || (actor.getInstanceId() != 0))
		{
			return Outcome.of("REJECTED", "ACTOR_BUSY");
		}
		final long selectedProfileId = Long.parseLong(args.getOrDefault("profileId", "0"));
		if ((selectedProfileId < 0) || ((selectedProfileId > 0) && (selectedProfileId != _envelopeProfileId)))
		{
			return Outcome.of("REJECTED", "INVALID_ARGUMENT");
		}
		long after = afterProfileId;
		final PhantomTopologyPoint here = new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId());
		for (int attempt = 0; attempt < 8; attempt++)
		{
			final OperatorLocalityTarget target = (selectedProfileId > 0 ? PhantomSystem.operatorLocalityTarget(selectedProfileId) : PhantomSystem.operatorNearestReadyLocalityTarget(here, (long) M1_TARGET_SEARCH_RADIUS * M1_TARGET_SEARCH_RADIUS, after)).orElse(null);
			if (target == null)
			{
				return Outcome.of("REJECTED", "NO_ORDINARY_READY_TARGET");
			}
			after = target.profileId();
			final OperatorAdmissionProfile admission = PhantomSystem.operatorAdmissionProfile(target.profileId()).orElse(null);
			final EnvelopeRoute route = (admission != null) && "READY".equals(admission.admission().populationState().name()) && "none".equals(admission.busyReason()) ? envelopeRoute(target) : null;
			if (route != null)
			{
				_envelopeProfileId = target.profileId();
				final Map<String, String> data = new LinkedHashMap<>();
				data.put("profileId", Long.toString(_envelopeProfileId));
				putPoint(data, "start", route.outside());
				putPoint(data, "prewarm", route.prewarm());
				putPoint(data, "inside", route.inside());
				data.put("route", route.path().stream().map(point -> point.getX() + "," + point.getY() + "," + point.getZ()).collect(java.util.stream.Collectors.joining(";")));
				actor.teleToLocation(route.outside(), false);
				return new Outcome("ACCEPTED", "M1_NATURAL_GEO_PROVEN_ENVELOPE", Map.copyOf(data));
			}
			if (selectedProfileId > 0)
			{
				break;
			}
		}
		return Outcome.of("REJECTED", "NO_NATIVE_PREWARM_ROUTE");
	}

	private record EnvelopeRoute(Location outside, Location prewarm, Location inside, List<Location> path)
	{
	}

	private static void putPoint(Map<String, String> data, String prefix, Location point)
	{
		data.put(prefix + "X", Integer.toString(point.getX()));
		data.put(prefix + "Y", Integer.toString(point.getY()));
		data.put(prefix + "Z", Integer.toString(point.getZ()));
	}

	private static EnvelopeRoute envelopeRoute(OperatorLocalityTarget target)
	{
		final PhantomTopologyPoint point = target.committedPosition();
		if (point.instanceId() != 0)
		{
			return null;
		}
		final int size = 1 << World.SHIFT_BY;
		final Location inside = new Location(point.x(), point.y(), point.z(), 0);
		for (int direction = 0; direction < 4; direction++)
		{
			final boolean horizontal = direction < 2;
			final int sign = (direction & 1) == 0 ? 1 : -1;
			final int axis = horizontal ? point.x() : point.y();
			final int base = ((axis >> World.SHIFT_BY) + (2 * sign)) * size;
			for (int lateral : new int[] {0, 512, -512, 1024, -1024})
			{
				for (int inset : new int[] {256, 448, 768})
				{
					final int candidateAxis = base + (sign > 0 ? inset : size - inset);
					final Location prewarm = grounded(horizontal ? candidateAxis : point.x() + lateral, horizontal ? point.y() + lateral : candidateAxis, point.z());
					final int outsideAxis = sign > 0 ? base + size + 64 : base - 64;
					final Location outside = grounded(horizontal ? outsideAxis : point.x() + lateral, horizontal ? point.y() + lateral : outsideAxis, point.z());
					if ((prewarm == null) || (outside == null) || (Math.hypot(prewarm.getX() - outside.getX(), prewarm.getY() - outside.getY()) > 2000) || couldKnow(prewarm, inside) || couldKnow(outside, inside) || !PhantomSystem.operatorCanPrewarmAt(target.profileId(), topologyPoint(prewarm)) || PhantomSystem.operatorCanPrewarmAt(target.profileId(), topologyPoint(outside)) || !nativeBothWays(outside, prewarm))
					{
						continue;
					}
					final List<Location> path = nativePath(prewarm, inside);
					if (path != null)
					{
						return new EnvelopeRoute(outside, prewarm, inside, path);
					}
				}
			}
		}
		return null;
	}

	private static Location grounded(int x, int y, int sourceZ)
	{
		final GeoEngine geo = GeoEngine.getInstance();
		if (!geo.hasGeo(x, y))
		{
			return null;
		}
		final int z = geo.getHeight(x, y, sourceZ);
		return Math.abs(z - sourceZ) <= 200 ? new Location(x, y, z, 0) : null;
	}

	private static List<Location> nativePath(Location first, Location last)
	{
		final List<Location> points = new ArrayList<>();
		points.add(first);
		if (!nativeBothWays(first, last))
		{
			final var path = PathFinding.getInstance().findPath(first.getX(), first.getY(), first.getZ(), last.getX(), last.getY(), last.getZ(), 0, true);
			if ((path == null) || (path.size() > 62))
			{
				return null;
			}
			path.forEach(point -> points.add(new Location(point.getX(), point.getY(), point.getZ(), 0)));
		}
		points.add(last);
		double distance = 0;
		for (int index = 1; index < points.size(); index++)
		{
			final Location previous = points.get(index - 1);
			final Location point = points.get(index);
			distance += Math.hypot(point.getX() - previous.getX(), point.getY() - previous.getY());
			if ((distance > 10_000) || !nativeBothWays(previous, point))
			{
				return null;
			}
		}
		return List.copyOf(points);
	}

	private static PhantomTopologyPoint topologyPoint(Location point)
	{
		return new PhantomTopologyPoint(point.getX(), point.getY(), point.getZ() + 5, 0);
	}

	private static boolean couldKnow(Location human, Location target)
	{
		final var region = World.getInstance().getRegion(human.getX(), human.getY(), human.getZ());
		return (region != null) && region.isSurroundingRegion(World.getInstance().getRegion(target.getX(), target.getY(), target.getZ()));
	}

	private static boolean nativeBothWays(Location first, Location second)
	{
		final GeoEngine geo = GeoEngine.getInstance();
		return geo.canMoveToTarget(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ(), 0) && geo.canMoveToTarget(second.getX(), second.getY(), second.getZ(), first.getX(), first.getY(), first.getZ(), 0);
	}

	private Outcome snapshotM1Envelope(Player actor)
	{
		if (_envelopeProfileId <= 0)
		{
			return Outcome.of("REJECTED", "ENVELOPE_NOT_PREPARED");
		}
		final OperatorLocalityTarget target = PhantomSystem.operatorLocalityTarget(_envelopeProfileId).orElse(null);
		final OperatorAdmissionProfile profile = PhantomSystem.operatorAdmissionProfile(_envelopeProfileId).orElse(null);
		if ((target == null) || (profile == null))
		{
			return Outcome.of("REJECTED", "ENVELOPE_TARGET_UNAVAILABLE");
		}
		final PhantomTopologyPoint point = target.committedPosition();
		final var materialization = profile.materialization();
		final int objectId = materialization == null ? 0 : materialization.characterObjectId();
		final WorldObject object = objectId <= 0 ? null : World.getInstance().findObject(objectId);
		final Player player = object instanceof Player live ? live : null;
		final boolean worldPresent = (materialization != null) && materialization.worldPresent() && (player != null);
		final var actorRegion = World.getInstance().getRegion(actor);
		final var targetRegion = worldPresent ? World.getInstance().getRegion(player) : World.getInstance().getRegion(point.x(), point.y(), point.z());
		final boolean sameInstance = actor.getInstanceId() == (worldPresent ? player.getInstanceId() : point.instanceId());
		final boolean regionCanKnow = sameInstance && (actorRegion != null) && (targetRegion != null) && actorRegion.isSurroundingRegion(targetRegion);
		final boolean clientVisible = worldPresent && regionCanKnow && player.isOnline() && player.isVisibleFor(actor);
		final Map<String, String> data = new LinkedHashMap<>();
		data.put("profileId", Long.toString(_envelopeProfileId));
		data.put("committedX", Integer.toString(point.x()));
		data.put("committedY", Integer.toString(point.y()));
		data.put("committedZ", Integer.toString(point.z()));
		data.put("committedInstanceId", Integer.toString(point.instanceId()));
		data.put("humanRegionX", Integer.toString(actor.getX() >> World.SHIFT_BY));
		data.put("humanRegionY", Integer.toString(actor.getY() >> World.SHIFT_BY));
		data.put("humanRegionZ", Integer.toString(actorRegion == null ? -1 : actorRegion.getRegionZ()));
		data.put("targetRegionX", Integer.toString((worldPresent ? player.getX() : point.x()) >> World.SHIFT_BY));
		data.put("targetRegionY", Integer.toString((worldPresent ? player.getY() : point.y()) >> World.SHIFT_BY));
		data.put("targetRegionZ", Integer.toString(targetRegion == null ? -1 : targetRegion.getRegionZ()));
		data.put("worldPresent", Boolean.toString(worldPresent));
		data.put("snapshotWorldPresent", Boolean.toString((materialization != null) && materialization.worldPresent()));
		data.put("clientVisible", Boolean.toString(clientVisible));
		data.put("regionCanKnow", Boolean.toString(regionCanKnow));
		data.put("objectId", Integer.toString(objectId));
		data.put("materializationState", materialization == null ? "STORED" : materialization.state().name());
		data.put("materializedAgeMillis", (materialization == null) || (materialization.materializedAtNanos() <= 0) ? "-1" : Long.toString(Math.max(0, (System.nanoTime() - materialization.materializedAtNanos()) / 1_000_000L)));
		data.put("localityCurrent", Boolean.toString(PhantomSystem.operatorHumanLocality(_envelopeProfileId)));
		data.put("presenceReason", profile.busyReason());
		data.put("admitted", Boolean.toString(profile.admission().admitted()));
		data.put("lastMaterializationFailure", String.valueOf(profile.lastMaterializationFailure()));
		data.put("distance2D", Long.toString(Math.round(Math.hypot(actor.getX() - (worldPresent ? player.getX() : point.x()), actor.getY() - (worldPresent ? player.getY() : point.y())))));
		if (worldPresent)
		{
			data.put("liveX", Integer.toString(player.getX()));
			data.put("liveY", Integer.toString(player.getY()));
			data.put("liveZ", Integer.toString(player.getZ()));
		}
		if (profile.scheduler() != null)
		{
			data.put("activityState", profile.scheduler().effectiveState().name());
			data.put("requestedState", profile.scheduler().requestedState().name());
			data.put("activeSignalSources", Integer.toString(profile.scheduler().activeSignalSources()));
			data.put("boundaryInFlight", Boolean.toString(profile.scheduler().boundaryInFlight()));
			data.put("transitionStatus", profile.scheduler().transitionStatus().name());
		}
		data.putAll(PhantomSystem.operatorVisibleLifeCensus(actor));
		return new Outcome("SUCCEEDED", "M1_ENVELOPE_SNAPSHOT", Map.copyOf(data));
	}

	private Outcome candidate(Player actor)
	{
		final PhantomTopologyPoint here = new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId());
		final OperatorLocalityTarget visible = PhantomSystem.operatorNearestVisibleMaterializedTarget(actor).orElse(null);
		final OperatorLocalityTarget nearest = PhantomSystem.operatorNearestLocalityTarget(here).orElse(null);
		if (nearest == null)
		{
			_candidatePosition = null;
			_candidateObjectId = 0;
			final Map<String, String> data = new LinkedHashMap<>();
			addVisibleMaterialized(data, visible, actor);
			return new Outcome("SUCCEEDED", "NO_CANDIDATE", Map.copyOf(data));
		}
		final OperatorAdmissionProfile profile = PhantomSystem.operatorAdmissionProfile(nearest.profileId()).orElse(null);
		if ((profile == null) || !profile.admission().admitted() || profile.admission().pendingRebalance() || (nearest.committedPosition().instanceId() != actor.getInstanceId()))
		{
			final Map<String, String> data = new LinkedHashMap<>();
			addVisibleMaterialized(data, visible, actor);
			return new Outcome("SUCCEEDED", "NO_ADMITTED_CANDIDATE", Map.copyOf(data));
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
		addVisibleMaterialized(data, visible, actor);
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

	private static void addVisibleMaterialized(Map<String, String> data, OperatorLocalityTarget visible, Player actor)
	{
		if (visible != null)
		{
			data.put("visibleProfileId", Long.toString(visible.profileId()));
			data.put("visibleX", Integer.toString(visible.committedPosition().x()));
			data.put("visibleY", Integer.toString(visible.committedPosition().y()));
			data.put("visibleZ", Integer.toString(visible.committedPosition().z()));
			final OperatorAdmissionProfile profile = PhantomSystem.operatorAdmissionProfile(visible.profileId()).orElse(null);
			if ((profile != null) && (profile.materialization() != null) && (World.getInstance().findObject(profile.materialization().characterObjectId()) instanceof Player target) && target.isOnline() && (target.getInstanceId() == actor.getInstanceId()) && target.isVisibleFor(actor))
			{
				data.put("visibleObjectId", Integer.toString(target.getObjectId()));
				data.put("visibleLiveX", Integer.toString(target.getX()));
				data.put("visibleLiveY", Integer.toString(target.getY()));
				data.put("visibleLiveZ", Integer.toString(target.getZ()));
				data.put("visibleDistance", Long.toString(Math.round(actor.calculateDistance3D(target))));
				data.put("visibleTargetObjectId", Integer.toString(target.getTarget() == null ? 0 : target.getTarget().getObjectId()));
				data.put("visibleAiIntention", target.hasAI() ? target.getAI().getIntention().name() : "NONE");
				data.put("visibleMoving", Boolean.toString(target.isMoving()));
				data.put("visibleAttacking", Boolean.toString(target.isAttackingNow()));
				data.put("visibleAutoPlaying", Boolean.toString(target.isAutoPlaying()));
				data.put("visibleDead", Boolean.toString(target.isDead()));
			}
		}
	}

	private Outcome selectVisibleTrace(Player actor)
	{
		final OperatorLocalityTarget visible = PhantomSystem.operatorNearestVisibleMaterializedTarget(actor).orElse(null);
		if (visible == null)
		{
			return Outcome.of("REJECTED", "NO_VISIBLE_MATERIALIZED_PHANTOM");
		}
		final PhantomSelectedDecisionTrace.SelectionStatus status = PhantomSystem.selectOperatorTrace(visible.profileId());
		if (status != PhantomSelectedDecisionTrace.SelectionStatus.SELECTED)
		{
			return Outcome.of("REJECTED", status.name());
		}
		_selectedTraceProfileId = visible.profileId();
		return new Outcome("SUCCEEDED", status.name(), Map.of("profileId", Long.toString(visible.profileId())));
	}

	private Outcome selectedTrace()
	{
		final PhantomSelectedDecisionTrace.Snapshot trace = PhantomSystem.operatorStatus().selectedTrace();
		if ((_selectedTraceProfileId <= 0) || (trace.selectedProfileId() != _selectedTraceProfileId))
		{
			return Outcome.of("REJECTED", "NO_PILOT_TRACE_SELECTION");
		}
		final Map<String, String> data = new LinkedHashMap<>();
		data.put("profileId", Long.toString(trace.selectedProfileId()));
		data.put("attached", Boolean.toString(trace.attached()));
		data.put("recorded", Long.toString(trace.recorded()));
		data.put("dropped", Long.toString(trace.dropped()));
		data.put("health", trace.health().name());
		data.put("ageMillis", Long.toString(trace.ageMillis()));
		if (trace.current() != null)
		{
			final PhantomSelectedDecisionTrace.DecisionView current = trace.current();
			data.put("activityState", String.valueOf(current.activityState()));
			data.put("goalType", String.valueOf(current.goalType()));
			data.put("goalStatus", String.valueOf(current.goalStatus()));
			data.put("runtimeState", String.valueOf(current.runtimeState()));
			data.put("decisionSequence", Long.toString(current.decisionSequence()));
			data.put("candidateKey", String.valueOf(current.candidateKey()));
			data.put("score", Integer.toString(current.score()));
			data.put("step", Integer.toString(current.step()));
			data.put("attempt", Integer.toString(current.attempt()));
			data.put("lastResult", String.valueOf(current.lastResult()));
			data.put("reasonKey", String.valueOf(current.reasonKey()));
			data.put("topCandidates", current.topCandidates().toString());
		}
		final int start = Math.max(0, trace.history().size() - 8);
		for (int index = start; index < trace.history().size(); index++)
		{
			data.put("history" + (index - start), trace.history().get(index).toString());
		}
		return new Outcome("SUCCEEDED", "SELECTED_TRACE_SNAPSHOT", Map.copyOf(data));
	}

	private Outcome replaySelectedTrace()
	{
		final PhantomSelectedDecisionTrace.Snapshot trace = PhantomSystem.operatorStatus().selectedTrace();
		if ((_selectedTraceProfileId <= 0) || (trace.selectedProfileId() != _selectedTraceProfileId))
		{
			return Outcome.of("REJECTED", "NO_PILOT_TRACE_SELECTION");
		}
		final PhantomSystem.OperatorReplayResult capture = PhantomSystem.operatorReplayCapture();
		if (capture.code() != PhantomSystem.OperatorReplayCode.CAPTURED)
		{
			return Outcome.of("REJECTED", capture.code().name());
		}
		final PhantomSystem.OperatorReplayResult replay = PhantomSystem.operatorReplayRun();
		final Map<String, String> data = new LinkedHashMap<>();
		data.put("profileId", Long.toString(replay.profileId()));
		data.put("frameCount", Integer.toString(replay.frameCount()));
		data.put("digest", replay.digest());
		data.put("replay", String.valueOf(replay.replay()));
		return new Outcome(replay.code() == PhantomSystem.OperatorReplayCode.REPLAY_PASS ? "SUCCEEDED" : "REJECTED", replay.code().name(), Map.copyOf(data));
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

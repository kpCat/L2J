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
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorM1TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace;
import org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;

/** Narrow native actions on the already connected, consented Player. */
public final class LocalPlayPilotActions
{
	private static final int M1_TARGET_SEARCH_RADIUS = 8000;
	private static final long M1_SCENE_NANOS = 480_000_000_000L;
	private static final long M1_CLEANUP_SECONDS = 45;
	private static final LocalPlayM1Observation.ApproachNavigation M1_APPROACH_NAVIGATION = new LocalPlayM1Observation.ApproachNavigation()
	{
		@Override
		public boolean forward(PhantomTopologyPoint first, PhantomTopologyPoint last)
		{
			return GeoEngine.getInstance().canMoveToTarget(first.x(), first.y(), first.z(), last.x(), last.y(), last.z(), first.instanceId());
		}

		@Override
		public List<PhantomTopologyPoint> findPath(PhantomTopologyPoint first, PhantomTopologyPoint last)
		{
			final var path = PathFinding.getInstance().findPath(first.x(), first.y(), first.z(), last.x(), last.y(), last.z(), first.instanceId(), true);
			return path == null ? null : path.stream().map(point -> new PhantomTopologyPoint(point.getX(), point.getY(), point.getZ(), first.instanceId())).toList();
		}
	};
	public record Outcome(String status, String reason, Map<String, String> candidate)
	{
		public static Outcome of(String status, String reason)
		{
			return new Outcome(status, reason, null);
		}
	}

	private final Location _origin;
	private String _causalConsent;
	private PhantomRuntimeFlightRecorder.Snapshot _causalSnapshot;
	private Location _candidatePosition;
	private int _candidateObjectId;
	private int _selectedMobObjectId;
	private long _selectedTraceProfileId;
	private long _envelopeProfileId;
	private String _envelopeRunId;
	private String _envelopeSelectionKind;
	private PhantomTopologyPoint _envelopePosition;
	private List<Long> _m1NaturalCohortProfileIds = List.of();
	private Set<Long> _m1ExcludedProfileIds = Set.of();
	private long _m1SceneDeadlineNanos;
	private LocalPlayM1Observation.ApproachEnvelope _m1ApproachEnvelope;
	private int _m1ApproachCursor;
	private int _m1ApproachObjectId;
	private long _m1ApproachEpoch;
	private LocalPlayM1Observation.Ticket _m1Ticket;
	private boolean _m1ApproachStarted;
	private boolean _m1ContactObserved;
	private boolean _m1LeaveUsed;
	private boolean _m1AbsentObserved;
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
				case CAPABILITIES -> Outcome.of("SUCCEEDED", "STATUS,BEGIN_PHANTOM_CAUSAL_TRACE,SNAPSHOT_PHANTOM_CAUSAL_TRACE,END_PHANTOM_CAUSAL_TRACE,SNAPSHOT_PHANTOMS,PREPARE_M1_ENVELOPE,SNAPSHOT_M1_ENVELOPE,SELECT_VISIBLE_PHANTOM_TRACE,SNAPSHOT_SELECTED_PHANTOM_TRACE,REPLAY_SELECTED_PHANTOM_TRACE,SNAPSHOT_TARGETS,TELEPORT_SELF,MOVE_SELF,STOP_MOVE,SIT,STAND,SELECT_TARGET,SAY,PARTY_INVITE,PARTY_RESPOND,PARTY_LEAVE,ATTACK_NPC,CAST_LEARNED_SKILL");
				case SNAPSHOT_PHANTOMS -> snapshotPhantoms(actor, args);
				case BEGIN_PHANTOM_CAUSAL_TRACE, SNAPSHOT_PHANTOM_CAUSAL_TRACE, END_PHANTOM_CAUSAL_TRACE -> causalTrace(request);
				case PREPARE_M1_ENVELOPE -> prepareM1Envelope(actor, request.runId(), args);
				case SNAPSHOT_M1_ENVELOPE -> snapshotM1Envelope(actor, request.runId(), args);
				case SELECT_VISIBLE_PHANTOM_TRACE -> selectVisibleTrace(actor);
				case SNAPSHOT_SELECTED_PHANTOM_TRACE -> selectedTrace();
				case REPLAY_SELECTED_PHANTOM_TRACE -> replaySelectedTrace();
				case SNAPSHOT_TARGETS -> targets(actor);
				case SIT -> sit(actor);
				case STAND -> stand(actor);
				case STOP_MOVE -> stopMove(actor);
				case MOVE_SELF -> move(actor, args);
				case TELEPORT_SELF -> teleport(actor, request.runId(), args);
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

	private Outcome causalTrace(LocalPlayPilotProtocol.Request request)
	{
		final var recorder = PhantomRuntimeFlightRecorder.getInstance();
		final String consent = request.sessionId() + ":" + request.runId();
		if (request.operation() == LocalPlayPilotProtocol.Operation.BEGIN_PHANTOM_CAUSAL_TRACE)
		{
			if (!recorder.begin(consent)) { return Outcome.of("REJECTED", "CAUSAL_TRACE_DISABLED_OR_ACTIVE"); }
			_causalConsent = consent;
			_causalSnapshot = null;
			return new Outcome("SUCCEEDED", "CAUSAL_TRACE_BEGUN", Map.of("startedNanos", Long.toString(recorder.snapshot(consent).startedNanos()), "capacity", "8192", "maxWatched", "8", "durationLimitSeconds", "120"));
		}
		if (!consent.equals(_causalConsent)) { return Outcome.of("REJECTED", "CAUSAL_TRACE_SESSION_MISMATCH"); }
		if (request.operation() == LocalPlayPilotProtocol.Operation.END_PHANTOM_CAUSAL_TRACE) { _causalSnapshot = recorder.end(consent); }
		final var snapshot = _causalSnapshot == null ? recorder.snapshot(consent) : _causalSnapshot;
		final long afterSeq = Long.parseLong(request.args().getOrDefault("afterSeq", "0"));
		final int maximum = Integer.parseInt(request.args().getOrDefault("maxEvents", "64"));
		if ((afterSeq < 0) || (maximum < 1) || (maximum > 128)) { return Outcome.of("REJECTED", "CAUSAL_TRACE_PAGE_BOUND"); }
		final Map<String, String> values = new LinkedHashMap<>();
		values.put("watched", snapshot.watched().toString());
		values.put("attempts", Long.toString(snapshot.attempts()));
		values.put("dropped", Long.toString(snapshot.dropped()));
		values.put("active", Boolean.toString(snapshot.active()));
		values.put("startedNanos", Long.toString(snapshot.startedNanos()));
		values.put("snapshotNanos", Long.toString(System.nanoTime()));
		values.put("header", "seq\tnanoTime\tthreadName\tprofileId\tevent\tstateA\tstateB\treason\tvalue1\tvalue2\tvalue3");
		int bytes = 0;
		int count = 0;
		long last = afterSeq;
		for (var event : snapshot.events())
		{
			if (event.seq() <= afterSeq) { continue; }
			final String value = event.tsv();
			// Conservative XML escaping expansion; leave 24 KiB for protocol/session/actor metadata.
			final int encodedBound = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length * 6 + 64;
			if ((count >= maximum) || (bytes + encodedBound > 40_000)) { break; }
			values.put("event." + event.seq(), value);
			last = event.seq();
			bytes += encodedBound;
			count++;
		}
		final long cursor = last;
		values.put("nextSeq", Long.toString(last));
		values.put("hasMore", Boolean.toString(snapshot.events().stream().anyMatch(event -> event.seq() > cursor)));
		values.put("retained", Integer.toString(snapshot.events().size()));
		return new Outcome("SUCCEEDED", "CAUSAL_TRACE_SNAPSHOT", Map.copyOf(values));
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

	private Outcome teleport(Player actor, String runId, Map<String, String> args)
	{
		final int x = integer(args, "x");
		final int y = integer(args, "y");
		final int z = integer(args, "z");
		final int instanceId = integer(args, "instanceId");
		if (args.containsKey("m1Token"))
		{
			final LocalPlayM1Observation.Ticket ticket = _m1Ticket;
			final OperatorM1TargetSnapshot current = PhantomSystem.operatorM1TargetSnapshot(_envelopeProfileId).orElse(null);
			final PhantomTopologyPoint destination = new PhantomTopologyPoint(x, y, z, instanceId);
			if ((ticket == null) || (current == null) || (current.positionSource() != LocalPlayM1Observation.PositionSource.LIVE) || !_envelopeRunId.equals(runId) || !ticket.valid(args.get("m1Token"), runId, actor.getObjectId(), _envelopeProfileId, current.objectId(), current.materializedAtNanos(), ticket.purpose(), destination, System.nanoTime()))
			{
				return Outcome.of("REJECTED", "M1_TICKET_INVALID");
			}
			final PhantomTopologyPoint live = current.observedPosition();
			final boolean validPurpose = ticket.purpose() == LocalPlayM1Observation.Purpose.LEAVE ? !org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.prewarm(destination, live) && !org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(destination, live) : (Math.hypot((long) x - live.x(), (long) y - live.y()) <= 900) && GeoEngine.getInstance().canSeeTarget(x, y, z, instanceId, live.x(), live.y(), live.z(), live.instanceId());
			if (!validPurpose || actor.isDead() || actor.isInStoreMode() || actor.isTeleporting() || (instanceId != actor.getInstanceId()))
			{
				_m1Ticket = null;
				return Outcome.of("REJECTED", "M1_SCENE_INVALIDATED");
			}
			_m1Ticket = null;
			if (ticket.purpose() == LocalPlayM1Observation.Purpose.LEAVE) { _m1LeaveUsed = true; }
			actor.teleToLocation(new Location(x, y, z, actor.getHeading(), instanceId), false);
			return Outcome.of("ACCEPTED", "TELEPORT_COMPLETION_PENDING");
		}
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

	private Outcome prepareM1Envelope(Player actor, String runId, Map<String, String> args)
	{
		final String stage = args.getOrDefault("stage", "INITIAL");
		if (!Set.of("INITIAL", "APPROACH", "LEAVE", "RETURN").contains(stage))
		{
			return Outcome.of("REJECTED", "INVALID_STAGE");
		}
		if (args.containsKey("excludePreviouslySelectedProfileIds") && !"INITIAL".equals(stage)) { return Outcome.of("REJECTED", "INVALID_ARGUMENT"); }
		if (actor.isDead() || actor.isInStoreMode() || actor.isTeleporting() || (actor.getInstanceId() != 0) || ("INITIAL".equals(stage) && actor.isMoving()))
		{
			return Outcome.of("REJECTED", "ACTOR_BUSY");
		}
		final long selectedProfileId = Long.parseLong(args.getOrDefault("profileId", "0"));
		final boolean reprepareInitial = "INITIAL".equals(stage) && (selectedProfileId > 0);
		if ("INITIAL".equals(stage) && (_m1ApproachStarted || (reprepareInitial && ((selectedProfileId != _envelopeProfileId) || !runId.equals(_envelopeRunId))))) { return Outcome.of("REJECTED", "M1_INITIAL_LOCKED"); }
		if ((selectedProfileId < 0) || (!"INITIAL".equals(stage) && ((selectedProfileId != _envelopeProfileId) || !runId.equals(_envelopeRunId))))
		{
			return Outcome.of("REJECTED", "M1_RUN_OR_PROFILE_MISMATCH");
		}
		if (!"INITIAL".equals(stage))
		{
			if (("LEAVE".equals(stage) && !_m1ContactObserved) || ("RETURN".equals(stage) && !_m1AbsentObserved)) { return Outcome.of("REJECTED", "M1_PHASE_PRECONDITION"); }
			_m1Ticket = null;
			final OperatorM1TargetSnapshot target = PhantomSystem.operatorM1TargetSnapshot(_envelopeProfileId).orElse(null);
			if ((target == null) || (target.observedPosition() == null))
			{
				return Outcome.of("REJECTED", "TARGET_TRANSITION");
			}
			final PhantomTopologyPoint point = target.observedPosition();
			final Map<String, String> data = new LinkedHashMap<>();
			data.put("profileId", Long.toString(_envelopeProfileId));
			data.put("stage", stage);
			data.put("selectionKind", _envelopeSelectionKind);
			data.put("positionSource", target.positionSource().name());
			data.put("committedSequence", Long.toString(target.committedSequence()));
			data.put("materializedAtNanos", Long.toString(target.materializedAtNanos()));
			data.put("objectId", Integer.toString(target.objectId()));
			putPoint(data, "observed", new Location(point.x(), point.y(), point.z(), 0, point.instanceId()));
			if ("APPROACH".equals(stage))
			{
				final Location here = actor.getLocation().clone();
				if ((target.positionSource() == LocalPlayM1Observation.PositionSource.LIVE) && (_m1ApproachObjectId == 0) && (target.objectId() > 0) && (target.materializedAtNanos() > 0))
				{
					_m1ApproachObjectId = target.objectId();
					_m1ApproachEpoch = target.materializedAtNanos();
				}
				final var route = LocalPlayM1Observation.planApproach(_m1ApproachEnvelope, runId, _envelopeProfileId, target.committedSequence(), target.positionSource(), target.objectId(), target.materializedAtNanos(), _m1ApproachObjectId, _m1ApproachEpoch, routePoint(here), point, _m1ApproachCursor, M1_APPROACH_NAVIGATION);
				if (!route.accepted())
				{
					if ((route.reason() == LocalPlayM1Observation.RouteReason.ENVELOPE_STALE) && (_m1ApproachEnvelope != null) && (_m1ApproachEnvelope.committedSequence() != target.committedSequence()) && (target.positionSource() == LocalPlayM1Observation.PositionSource.COMMITTED)) { _m1ApproachEnvelope = null; }
					return Outcome.of("REJECTED", route.reason().name());
				}
				if (target.positionSource() == LocalPlayM1Observation.PositionSource.COMMITTED) { _m1ApproachCursor = route.cursor(); }
				_m1ApproachStarted = true;
				data.put("route", routeTextPoints(route.points()));
				data.put("routeKind", route.reason().name());
				return new Outcome("ACCEPTED", route.reason().name(), Map.copyOf(data));
			}
			if (target.positionSource() != LocalPlayM1Observation.PositionSource.LIVE) { return Outcome.of("REJECTED", "M1_LIVE_TARGET_REQUIRED"); }
			final Location destination;
			final LocalPlayM1Observation.Purpose purpose;
			if ("LEAVE".equals(stage))
			{
				destination = outsidePoint(point);
				if (destination == null) { return Outcome.of("REJECTED", "NO_NATIVE_LEAVE_POINT"); }
				purpose = LocalPlayM1Observation.Purpose.LEAVE;
			}
			else
			{
				destination = returnPoint(point);
				if (destination == null) { return Outcome.of("REJECTED", "NO_NATIVE_RETURN_POINT"); }
				purpose = LocalPlayM1Observation.Purpose.RETURN;
			}
			final String token = java.util.UUID.randomUUID().toString();
			_m1Ticket = new LocalPlayM1Observation.Ticket(token, runId, actor.getObjectId(), _envelopeProfileId, target.objectId(), target.materializedAtNanos(), purpose, new PhantomTopologyPoint(destination.getX(), destination.getY(), destination.getZ(), destination.getInstanceId()), System.nanoTime() + 15_000_000_000L);
			putPoint(data, "destination", destination);
			data.put("destinationInstanceId", Integer.toString(destination.getInstanceId()));
			data.put("m1Token", token);
			return new Outcome("ACCEPTED", "M1_" + stage + "_TICKET", Map.copyOf(data));
		}
		final PhantomTopologyPoint here = new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId());
		final Set<Long> requestedExclusions = m1SelectorExclusions(args);
		final Set<Long> selectorExclusions = reprepareInitial && !args.containsKey("excludePreviouslySelectedProfileIds") ? _m1ExcludedProfileIds : requestedExclusions;
		if (reprepareInitial && !selectorExclusions.equals(_m1ExcludedProfileIds)) { return Outcome.of("REJECTED", "M1_SELECTOR_INPUT_CHANGED"); }
		final long now = System.nanoTime();
		final long sceneDeadline = reprepareInitial ? _m1SceneDeadlineNanos : now + M1_SCENE_NANOS;
		final long remainingNanos = sceneDeadline - now;
		if (remainingNanos <= 0) { return Outcome.of("REJECTED", "M1_SCENE_DEADLINE"); }
		final long horizonSeconds = ((remainingNanos + 999_999_999L) / 1_000_000_000L) + M1_CLEANUP_SECONDS;
		final java.time.Instant calendarHorizon = java.time.Instant.now().plusSeconds(horizonSeconds);
		final List<OperatorM1TargetSnapshot> candidates = reprepareInitial ? List.of() : PhantomSystem.operatorM1CandidateSnapshots(here, (long) M1_TARGET_SEARCH_RADIUS * M1_TARGET_SEARCH_RADIUS, selectorExclusions);
		OperatorM1TargetSnapshot selected = null;
		OperatorAdmissionProfile selectedAdmission = null;
		EnvelopeRoute selectedRoute = null;
		int attempts = 0;
		final List<String> legacySkips = new ArrayList<>();
		if (reprepareInitial)
		{
			selected = PhantomSystem.operatorM1TargetSnapshot(selectedProfileId).orElse(null);
			selectedAdmission = PhantomSystem.operatorAdmissionProfile(selectedProfileId).orElse(null);
			if ((selected == null) || (selected.observedPosition() == null) || (selectedAdmission == null) || !selectedAdmission.admission().calendarOnline() || !selectedAdmission.admission().nextBoundary().isAfter(calendarHorizon)) { return Outcome.of("REJECTED", "M1_SAME_TARGET_INVALIDATED"); }
			selectedRoute = envelopeRoute(selectedProfileId, selected.observedPosition());
			if (selectedRoute == null) { return Outcome.of("REJECTED", "NO_NATIVE_PREWARM_ROUTE"); }
		}
		else
		{
			for (OperatorM1TargetSnapshot candidate : candidates)
			{
				if (attempts >= 8) { break; }
				final OperatorAdmissionProfile admission = PhantomSystem.operatorAdmissionProfile(candidate.profileId()).orElse(null);
				if (selectorExclusions.contains(candidate.profileId()) || (admission == null) || !admission.admission().calendarOnline() || !admission.admission().nextBoundary().isAfter(calendarHorizon) || ((candidate.positionSource() == LocalPlayM1Observation.PositionSource.COMMITTED) && !"none".equals(admission.busyReason()))) { continue; }
				final var quarantine = LocalPlayM1LegacyQuarantine.inspect(candidate.profileId());
				final Outcome quarantineOutcome = quarantineCandidate(candidate.profileId(), quarantine, legacySkips);
				if (quarantineOutcome != null)
				{
					if ("REJECTED".equals(quarantineOutcome.status())) { return quarantineOutcome; }
					continue;
				}
				attempts++;
				final EnvelopeRoute route = envelopeRoute(candidate.profileId(), candidate.observedPosition());
				if (route == null) { continue; }
				selected = candidate; selectedAdmission = admission; selectedRoute = route;
				break;
			}
		}
		if (selected == null) { return Outcome.of("REJECTED", "NO_NATIVE_PREWARM_ROUTE"); }
		if (selectorExclusions.contains(selected.profileId())) { return Outcome.of("REJECTED", "M1_PREVIOUS_TARGET_EXCLUDED"); }
		final PhantomTopologyPoint cohortInside = topologyPoint(selectedRoute.inside());
		final List<Long> cohort = reprepareInitial ? _m1NaturalCohortProfileIds : PhantomSystem.operatorM1NaturalCohortProfileIds(cohortInside);
		if ((cohort == null) || (cohort.size() < 4) || cohort.stream().anyMatch(id -> (id == null) || (id <= 0)) || (new HashSet<>(cohort).size() != cohort.size()) || !cohort.contains(selected.profileId())) { return Outcome.of("REJECTED", "M1_NATURAL_COHORT_UNPROVEN"); }
		_envelopeRunId = runId;
		_envelopeProfileId = selected.profileId();
		if (!reprepareInitial)
		{
			_envelopeSelectionKind = selected.positionSource() == LocalPlayM1Observation.PositionSource.COMMITTED ? "STORED_START" : "EXISTING_START";
			_m1NaturalCohortProfileIds = List.copyOf(cohort);
			_m1ExcludedProfileIds = Set.copyOf(selectorExclusions);
			_m1SceneDeadlineNanos = sceneDeadline;
		}
		_envelopePosition = selected.observedPosition();
		_m1ApproachEnvelope = new LocalPlayM1Observation.ApproachEnvelope(runId, selected.profileId(), selected.committedSequence(), routePoint(selectedRoute.outside()), routePoint(selectedRoute.prewarm()), selectedRoute.path().stream().map(LocalPlayPilotActions::routePoint).toList());
		_m1ApproachCursor = 0;
		_m1ApproachObjectId = selected.positionSource() == LocalPlayM1Observation.PositionSource.LIVE ? selected.objectId() : 0;
		_m1ApproachEpoch = selected.positionSource() == LocalPlayM1Observation.PositionSource.LIVE ? selected.materializedAtNanos() : 0;
		_m1Ticket = null;
		_m1ApproachStarted = false;
		_m1ContactObserved = false;
		_m1LeaveUsed = false;
		_m1AbsentObserved = false;
		final Map<String, String> data = new LinkedHashMap<>();
		data.put("profileId", Long.toString(_envelopeProfileId));
		data.put("selectionKind", _envelopeSelectionKind);
		data.put("positionSource", selected.positionSource().name());
		data.put("committedSequence", Long.toString(selected.committedSequence()));
		data.put("reprepared", Boolean.toString(reprepareInitial));
		data.put("initialWorldPresent", Boolean.toString(selected.worldPresent()));
		data.put("materializedAtNanos", Long.toString(selected.materializedAtNanos()));
		data.put("objectId", Integer.toString(selected.objectId()));
		data.put("naturalCohortPrepared", Integer.toString(_m1NaturalCohortProfileIds.size()));
		data.put("naturalCohortProfileIds", _m1NaturalCohortProfileIds.stream().map(id -> Long.toString(id)).collect(java.util.stream.Collectors.joining(",")));
		data.put("selectorExcludedProfileIds", _m1ExcludedProfileIds.stream().sorted().map(id -> Long.toString(id)).collect(java.util.stream.Collectors.joining(",")));
		data.put("calendarHorizonSeconds", Long.toString(horizonSeconds));
		data.put("sceneDeadlineNanos", Long.toString(_m1SceneDeadlineNanos));
		data.put("calendarState", selectedAdmission.admission().desiredState().name());
		data.put("nextBoundary", selectedAdmission.admission().nextBoundary().toString());
		data.put("readinessReason", selectedAdmission.readiness() == null ? "ecology.disabled" : selectedAdmission.readiness().reason());
		putPoint(data, "start", selectedRoute.outside());
		putPoint(data, "prewarm", selectedRoute.prewarm());
		putPoint(data, "inside", selectedRoute.inside());
		data.put("route", routeText(selectedRoute.path()));
		data.put("legacySkips", String.join(";", legacySkips));
		actor.teleToLocation(selectedRoute.outside(), false);
		return new Outcome("ACCEPTED", "M1_NATURAL_GEO_PROVEN_ENVELOPE", Map.copyOf(data));
	}

	private static Set<Long> m1SelectorExclusions(Map<String, String> args)
	{
		final String value = args.get("excludePreviouslySelectedProfileIds");
		if (value == null) { return Set.of(); }
		// The optional prior receipt is validated by the runner; this is only a bounded selector input.
		if (!value.matches("[0-9]+")) { throw new IllegalArgumentException("M1_SELECTOR_EXCLUSION"); }
		final long profileId = Long.parseLong(value);
		if (profileId <= 0) { throw new IllegalArgumentException("M1_SELECTOR_EXCLUSION"); }
		return Set.of(profileId);
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

	/** Records only exact known witnesses; the caller continues its existing natural candidate order. */
	private static Outcome quarantineCandidate(long profileId, LocalPlayM1LegacyQuarantine.Decision decision, List<String> skips)
	{
		if (decision == LocalPlayM1LegacyQuarantine.Decision.ELIGIBLE) { return null; }
		if (decision == LocalPlayM1LegacyQuarantine.Decision.UNKNOWN_INCONSISTENT) { return new Outcome("REJECTED", "UNKNOWN_INCONSISTENT:" + profileId, Map.of("legacySkips", String.join(";", skips))); }
		if (skips.size() >= 8) { return new Outcome("REJECTED", "KNOWN_PREFIX_SKIP_CAP", Map.of("legacySkips", String.join(";", skips))); }
		skips.add(profileId + ":KNOWN_PREFIX_FAIL_CLOSED");
		return Outcome.of("SKIPPED", "KNOWN_PREFIX_FAIL_CLOSED");
	}

	private static String routeText(List<Location> route)
	{
		return route.stream().map(point -> point.getX() + "," + point.getY() + "," + point.getZ()).collect(java.util.stream.Collectors.joining(";"));
	}

	private static PhantomTopologyPoint routePoint(Location point)
	{
		return new PhantomTopologyPoint(point.getX(), point.getY(), point.getZ(), point.getInstanceId());
	}

	private static String routeTextPoints(List<PhantomTopologyPoint> route)
	{
		return route.stream().map(point -> point.x() + "," + point.y() + "," + point.z()).collect(java.util.stream.Collectors.joining(";"));
	}

	private static Location outsidePoint(PhantomTopologyPoint point)
	{
		if (point.instanceId() != 0) { return null; }
		final int region = 1 << World.SHIFT_BY;
		for (int direction = 0; direction < 4; direction++)
		{
			final int sign = (direction & 1) == 0 ? 1 : -1;
			final boolean horizontal = direction < 2;
			final int axis = horizontal ? point.x() : point.y();
			final int outsideAxis = ((axis >> World.SHIFT_BY) + (sign > 0 ? 3 : -2)) * region + (sign > 0 ? 64 : region - 64);
			for (int lateral : new int[] {0, 512, -512})
			{
				final Location candidate = grounded(horizontal ? outsideAxis : point.x() + lateral, horizontal ? point.y() + lateral : outsideAxis, point.z());
				if ((candidate != null) && !org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.prewarm(topologyPoint(candidate), point) && !couldKnow(candidate, new Location(point.x(), point.y(), point.z(), 0)))
				{
					return candidate;
				}
			}
		}
		return null;
	}

	private static Location returnPoint(PhantomTopologyPoint point)
	{
		if (point.instanceId() != 0) { return null; }
		for (int[] offset : new int[][] {{256, 0}, {-256, 0}, {0, 256}, {0, -256}, {0, 0}})
		{
			final Location candidate = grounded(point.x() + offset[0], point.y() + offset[1], point.z());
			if ((candidate != null) && GeoEngine.getInstance().canSeeTarget(candidate.getX(), candidate.getY(), candidate.getZ(), 0, point.x(), point.y(), point.z(), 0) && nativeBothWays(candidate, new Location(point.x(), point.y(), point.z(), 0)))
			{
				return candidate;
			}
		}
		return null;
	}

	private static EnvelopeRoute envelopeRoute(long profileId, PhantomTopologyPoint point)
	{
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
					if ((prewarm == null) || (outside == null) || (Math.hypot(prewarm.getX() - outside.getX(), prewarm.getY() - outside.getY()) > 2000) || couldKnow(prewarm, inside) || couldKnow(outside, inside) || !PhantomSystem.operatorCanPrewarmAt(profileId, topologyPoint(prewarm)) || PhantomSystem.operatorCanPrewarmAt(profileId, topologyPoint(outside)) || !nativeBothWays(outside, prewarm))
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
		return org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(new PhantomTopologyPoint(human.getX(), human.getY(), human.getZ(), 0), new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), 0));
	}

	private static boolean nativeBothWays(Location first, Location second)
	{
		final GeoEngine geo = GeoEngine.getInstance();
		return geo.canMoveToTarget(first.getX(), first.getY(), first.getZ(), second.getX(), second.getY(), second.getZ(), 0) && geo.canMoveToTarget(second.getX(), second.getY(), second.getZ(), first.getX(), first.getY(), first.getZ(), 0);
	}

	private Outcome snapshotM1Envelope(Player actor, String runId, Map<String, String> args)
	{
		if ((_envelopeProfileId <= 0) || !runId.equals(_envelopeRunId))
		{
			return Outcome.of("REJECTED", "ENVELOPE_NOT_PREPARED");
		}
		final OperatorM1TargetSnapshot target = PhantomSystem.operatorM1TargetSnapshot(_envelopeProfileId, actor).orElse(null);
		final OperatorAdmissionProfile profile = PhantomSystem.operatorAdmissionProfile(_envelopeProfileId).orElse(null);
		if ((target == null) || (profile == null))
		{
			return Outcome.of("REJECTED", "ENVELOPE_TARGET_UNAVAILABLE");
		}
		final PhantomTopologyPoint point = target.observedPosition();
		final PhantomTopologyPoint committed = target.committedPosition();
		final boolean worldPresent = target.worldPresent();
		LocalPlayPhantomStoreJournal.select(worldPresent && (target.materializedAtNanos() > 0) ? Map.of(_envelopeProfileId, target.materializedAtNanos()) : Map.of());
		if (!profile.admission().calendarOnline()) { return Outcome.of("REJECTED", "SCENE_INVALIDATED:CALENDAR_OFFLINE"); }
		if (!"READY".equals(profile.admission().populationState().name())) { return Outcome.of("REJECTED", "SCENE_INVALIDATED:POPULATION_STATE"); }
		final Location actorLocation = actor.getLocation().clone();
		final var actorRegion = World.getInstance().getRegion(actor);
		final var targetRegion = point == null ? null : World.getInstance().getRegion(point.x(), point.y(), point.z());
		final boolean sameInstance = (point != null) && (actorLocation.getInstanceId() == point.instanceId());
		final boolean regionCanKnow = sameInstance && org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.couldKnow(new PhantomTopologyPoint(actorLocation.getX(), actorLocation.getY(), actorLocation.getZ(), actorLocation.getInstanceId()), point);
		final boolean clientVisible = worldPresent && regionCanKnow && target.online() && target.visibleForHuman();
		if (_m1ApproachStarted && LocalPlayM1Observation.contact(worldPresent, clientVisible, point == null ? Long.MAX_VALUE : Math.round(Math.hypot((long) actorLocation.getX() - point.x(), (long) actorLocation.getY() - point.y())))) { _m1ContactObserved = true; }
		if (_m1LeaveUsed && worldPresent && !regionCanKnow && !clientVisible && (point != null) && !org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.prewarm(new PhantomTopologyPoint(actorLocation.getX(), actorLocation.getY(), actorLocation.getZ(), actorLocation.getInstanceId()), point)) { _m1AbsentObserved = true; }
		final Map<String, String> data = new LinkedHashMap<>();
		data.put("profileId", Long.toString(_envelopeProfileId));
		data.put("selectionKind", _envelopeSelectionKind);
		data.put("positionSource", target.positionSource().name());
		data.put("observationChanged", Boolean.toString((point != null) && !_envelopePosition.equals(point)));
		data.put("committedSequence", Long.toString(target.committedSequence()));
		data.put("sampledAtNanos", Long.toString(target.sampledAtNanos()));
		data.put("materializedAtNanos", Long.toString(target.materializedAtNanos()));
		data.put("committedX", Integer.toString(committed.x()));
		data.put("committedY", Integer.toString(committed.y()));
		data.put("committedZ", Integer.toString(committed.z()));
		data.put("committedInstanceId", Integer.toString(committed.instanceId()));
		if (point != null)
		{
			data.put("observedX", Integer.toString(point.x()));
			data.put("observedY", Integer.toString(point.y()));
			data.put("observedZ", Integer.toString(point.z()));
			data.put("observedInstanceId", Integer.toString(point.instanceId()));
		}
		data.put("humanRegionX", Integer.toString(actorLocation.getX() >> World.SHIFT_BY));
		data.put("humanRegionY", Integer.toString(actorLocation.getY() >> World.SHIFT_BY));
		data.put("humanRegionZ", Integer.toString(actorRegion == null ? -1 : actorRegion.getRegionZ()));
		data.put("targetRegionX", Integer.toString(point == null ? -1 : point.x() >> World.SHIFT_BY));
		data.put("targetRegionY", Integer.toString(point == null ? -1 : point.y() >> World.SHIFT_BY));
		data.put("targetRegionZ", Integer.toString(targetRegion == null ? -1 : targetRegion.getRegionZ()));
		data.put("worldPresent", Boolean.toString(worldPresent));
		data.put("snapshotWorldPresent", Boolean.toString(target.snapshotWorldPresent()));
		data.put("clientVisible", Boolean.toString(clientVisible));
		data.put("regionCanKnow", Boolean.toString(regionCanKnow));
		data.put("humanPrewarm", Boolean.toString(sameInstance && (point != null) && org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope.prewarm(new PhantomTopologyPoint(actorLocation.getX(), actorLocation.getY(), actorLocation.getZ(), actorLocation.getInstanceId()), point)));
		data.put("objectId", Integer.toString(target.objectId()));
		data.put("materializationState", target.materializationState());
		data.put("materializedAgeMillis", target.materializedAtNanos() <= 0 ? "-1" : Long.toString(Math.max(0, (target.sampledAtNanos() - target.materializedAtNanos()) / 1_000_000L)));
		data.put("localityCurrent", Boolean.toString(profile.humanLocality()));
		data.put("presenceReason", profile.admission().calendarOnline() ? "calendar.online" : "calendar.offline");
		data.put("busyReason", profile.busyReason());
		data.put("calendarState", profile.admission().desiredState().name());
		data.put("calendarOnline", Boolean.toString(profile.admission().calendarOnline()));
		data.put("nextBoundary", profile.admission().nextBoundary().toString());
		data.put("nativeVisible", Boolean.toString(profile.nativeVisible()));
		data.put("retentionPins", profile.retentionPins().toString());
		final var readiness = profile.readiness();
		data.putAll(PhantomSystem.operatorReadinessProgress(_envelopeProfileId));
		data.put("readinessReason", readiness == null ? "ecology.disabled" : readiness.reason());
		if (readiness != null)
		{
			data.put("committedCursorMinute", Long.toString(readiness.committedCursorMinute()));
			data.put("requestedHorizonMinute", Long.toString(readiness.requestedHorizonMinute()));
			data.put("initialCatchupComplete", Boolean.toString(readiness.initialCatchupComplete()));
			data.put("requestPending", Boolean.toString(readiness.requestPending()));
			data.put("readinessQueued", Boolean.toString(readiness.queued()));
			data.put("readinessRunning", Boolean.toString(readiness.running()));
			data.put("readinessRevision", Long.toString(readiness.revision()));
		}
		data.put("admitted", Boolean.toString(profile.admission().admitted()));
		data.put("lastMaterializationFailure", String.valueOf(profile.lastMaterializationFailure()));
		data.put("distance2D", point == null ? "-1" : Long.toString(Math.round(Math.hypot((long) actorLocation.getX() - point.x(), (long) actorLocation.getY() - point.y()))));
		if (worldPresent)
		{
			data.put("liveX", Integer.toString(point.x()));
			data.put("liveY", Integer.toString(point.y()));
			data.put("liveZ", Integer.toString(point.z()));
		}
		data.put("nativeMoving", Boolean.toString(target.moving()));
		data.put("nativeAttacking", Boolean.toString(target.attacking()));
		data.put("nativeCasting", Boolean.toString(target.casting()));
		data.put("nativeAutoPlay", Boolean.toString(target.autoPlay()));
		data.put("nativeTargetObjectId", Integer.toString(target.targetObjectId()));
		data.put("nativeTargetMonsterAlive", Boolean.toString(target.targetMonsterAlive()));
		data.putAll(target.nativeEvidence());
		if (profile.scheduler() != null)
		{
			data.put("activityState", profile.scheduler().effectiveState().name());
			data.put("requestedState", profile.scheduler().requestedState().name());
			data.put("activeSignalSources", Integer.toString(profile.scheduler().activeSignalSources()));
			data.put("boundaryInFlight", Boolean.toString(profile.scheduler().boundaryInFlight()));
			data.put("transitionStatus", profile.scheduler().transitionStatus().name());
			data.put("lastTransitionReason", profile.scheduler().lastTransitionReason());
		}
		final long censusAfter = Long.parseLong(args.getOrDefault("censusAfterProfileId", "0"));
		if (censusAfter < 0) { return Outcome.of("REJECTED", "INVALID_ARGUMENT"); }
		final String includeCensus = args.getOrDefault("includeCensus", "true");
		if (!"true".equals(includeCensus) && !"false".equals(includeCensus)) { return Outcome.of("REJECTED", "INVALID_ARGUMENT"); }
		if ("true".equals(includeCensus)) { data.putAll(PhantomSystem.operatorVisibleLifeCensus(actor, censusAfter)); }
		return new Outcome("SUCCEEDED", "M1_ENVELOPE_SNAPSHOT", Map.copyOf(data));
	}

	private Outcome snapshotPhantoms(Player actor, Map<String, String> args)
	{
		final String includeCensus = args.getOrDefault("includeCensus", "false");
		final long after = Long.parseLong(args.getOrDefault("censusAfterProfileId", "0"));
		if ((!"true".equals(includeCensus) && !"false".equals(includeCensus)) || (after < 0)) { return Outcome.of("REJECTED", "INVALID_ARGUMENT"); }
		final Outcome snapshot = candidate(actor);
		if (!"true".equals(includeCensus)) { return snapshot; }
		final Map<String, String> data = new LinkedHashMap<>(snapshot.candidate());
		data.putAll(PhantomSystem.operatorVisibleLifeCensus(actor, after));
		return new Outcome(snapshot.status(), snapshot.reason(), Map.copyOf(data));
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
		if (_causalConsent != null) { PhantomRuntimeFlightRecorder.getInstance().stop(_causalConsent); }
		LocalPlayPhantomStoreJournal.select(Map.of());
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

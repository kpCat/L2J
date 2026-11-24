/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.geoengine.util.GridLineIterator2D;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.CastleZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.model.stats.Stat;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission.Stamp;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.combat.PhantomLearnedEscape;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomFarmSwimRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomJourneyObservation;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationProgressTracker.ProgressStatus;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Native movement/GK continuation of the same ordinary farm goal, before AutoPlay. */
public final class PhantomVisibleFarmTravel implements PhantomMaterializationLifecyclePort
{
	private final PhantomMaterializationService _materialization;
	private final PhantomBackgroundService _background;
	private final PhantomNormalGatekeeperTravel _travel;
	private final PhantomNavigationService _navigation;
	private final PhantomFarmRouteBackoff _farmSearches;
	public PhantomNavigationService navigation() { return _navigation; }
	private final PhantomEngagementPlanner _engagementPlanner = new PhantomEngagementPlanner();
	private final LongPredicate _permitsOrdinary;
	private final PhantomRelevanceSignalPort _signals;
	private final Map<Long, Journey> _journeys = new ConcurrentHashMap<>();
	private final Map<Long, Attempt> _attempts = new ConcurrentHashMap<>();
	private final Map<Long, Long> _swimTried = new ConcurrentHashMap<>();
	private final Map<Long, AlternativeWalks> _alternatives = new ConcurrentHashMap<>();
	// The same factual goal spans several Attempts. Guard observations with the Attempt ownership lock.
	private final Map<Long, PhantomJourneyObservation> _journeyObservations = new java.util.HashMap<>();
	private final Map<Long, PendingStore> _pendingStores = new ConcurrentHashMap<>();
	private final Map<Long, TerminalFailure> _terminalReasons = new ConcurrentHashMap<>();
	private final java.util.concurrent.atomic.AtomicLong _failureSequence = new java.util.concurrent.atomic.AtomicLong();
	private final BiConsumer<Long, Failure> _failure;
	private final LongSupplier _clock;
	private final java.util.concurrent.atomic.AtomicLong _signalSequence = new java.util.concurrent.atomic.AtomicLong();
	private volatile java.util.function.LongFunction<java.util.Set<String>> _routeExclusions = _ -> java.util.Set.of();
	private volatile java.util.function.BiPredicate<Long, PhantomGoal> _groupTravelSafety = (_, _) -> false;

	/** Restrictive geometry only; the current Journey still owns its root, epoch and finite budget. */
	public void bindGroupTravelSafety(java.util.function.BiPredicate<Long, PhantomGoal> assembly)
	{
		_groupTravelSafety = Objects.requireNonNull(assembly);
	}

	public void bindRouteExclusions(java.util.function.LongFunction<java.util.Set<String>> exclusions)
	{
		_routeExclusions = Objects.requireNonNull(exclusions);
	}

	boolean permitsPlannedFarmTravel(PhantomGoal goal, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint live, Player player)
	{
		final var anchor = _travel.topology().findAnchor(PhantomBackgroundGoalSpec.parse(goal).anchorId()).orElse(null);
		if (anchor == null || anchor.point().instanceId() != live.instanceId()) { return false; }
		if (Math.hypot((long) live.x()-anchor.point().x(),(long) live.y()-anchor.point().y()) <= _navigation.policy().maximumLocalStraightDistance()) { return true; }
		if (player == null || !PhantomNativeTravelActions.ordinary(player)) { return false; }
		final var lifetime = _materialization.findByCharacterObjectId(player.getObjectId()).orElse(null);
		if (lifetime == null || !currentGoal(lifetime.profileId(), goal)) { return false; }
		final var journey = _journeys.get(lifetime.profileId());
		if (journey != null && journey.goal.equals(goal) && journey.player == player && journey.epoch == lifetime.materializedAtNanos()
			&& _attempts.get(lifetime.profileId()) == journey.attempt && !journey.attempt.terminal && !journey.attempt.transientExhausted)
		{
			// Between factual anchors the same admitted physical segment owns progress.
			// Its original deadline is checked by advance; this does not renew the budget.
			return true;
		}
		final var state = _background.acquisitionSnapshot(lifetime.profileId()).orElse(null);
		if (state == null || state.identity().characterObjectId() != player.getObjectId()) { return false; }
		// A topology itinerary admits work, never movement or arrival. Each segment still
		// needs the existing physical planner, actual native action and owned checkpoint.
		return proposedStep(player, state.position().committedAnchorId(), anchor.id(), true) != null;
	}

	/** Read-only admission before the history owner's atomic predecessor-to-replacement CAS. */
	boolean permitsReplannedFarmTravel(PhantomGoal predecessor, PhantomGoal proposed, PhantomTopologyPoint live, Player player)
	{
		return permitsReplannedFarmTravel(predecessor, proposed, live, player, false);
	}

	boolean permitsReplannedFarmTravel(PhantomGoal predecessor, PhantomGoal proposed, PhantomTopologyPoint live, Player player, boolean verifyWalk)
	{
		if (player == null || !PhantomNativeTravelActions.ordinary(player) || proposed.goalId() != predecessor.goalId()
			|| proposed.revision() != predecessor.revision() + 1 || !Objects.equals(proposed.subject(), predecessor.subject())
			|| live.instanceId() != player.getInstanceId() || live.x() != player.getX() || live.y() != player.getY() || live.z() != player.getZ()) { return false; }
		final var lifetime = _materialization.findByCharacterObjectId(player.getObjectId()).orElse(null);
		if (lifetime == null || !lifetime.actionAdmissionOpen() || !currentGoal(lifetime.profileId(), predecessor)) { return false; }
		final var state = _background.acquisitionSnapshot(lifetime.profileId()).orElse(null);
		final var anchor = _travel.topology().findAnchor(PhantomBackgroundGoalSpec.parse(proposed).anchorId()).orElse(null);
		if (state == null || state.state() != PhantomBackgroundState.State.MATERIALIZED || state.identity().characterObjectId() != player.getObjectId()
			|| anchor == null || anchor.point().instanceId() != live.instanceId()) { return false; }
		final var step = proposedStep(player, state.position().committedAnchorId(), anchor.id(), true);
		return step != null && (!verifyWalk || verifiedRecoveryWalk(lifetime.profileId(), lifetime.materializedAtNanos(), player, step));
	}

	private boolean verifiedRecoveryWalk(long profileId, long epoch, Player player, PhantomNormalGatekeeperTravel.Step step)
	{
		final var first = _travel.topology().findAnchor(step.toAnchorId()).orElse(null);
		if (first == null || step.type() != PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND) { return false; }
		final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(first, player.getHeading()).orElse(null);
		if (canonical == null) { return false; }
		final var point = new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), canonical.instanceId());
		final var arrival = first.role() == PhantomTopologyAnchorRole.FARMING ? farmingStandpoint(profileId, first, point) : point;
		final var origin = PhantomNativeTravelActions.point(player);
		if (arrival == null || origin.instanceId() != arrival.instanceId()
			|| origin.distanceTo(arrival) > _navigation.policy().maximumRouteDistance()) { return false; }
		final var firstRequest = step.id().startsWith("live.approach.") && step.fromAnchorId().equals(step.toAnchorId())
			? PhantomNativeTravelActions.localWalkDestinations(origin, arrival, _navigation.policy().maximumLocalStraightDistance()).stream().findFirst().orElse(null) : arrival;
		return firstRequest != null && origin.distanceTo(firstRequest) <= _navigation.policy().maximumLocalStraightDistance()
			&& !_farmSearches.blocked(new PhantomFarmRouteBackoff.Probe(searchSite(profileId, player, epoch), origin, firstRequest), _clock.getAsLong())
			&& new L2jNavigationBackend().canWalkDirect(origin, firstRequest);
	}

	enum RecoveryWalkAdmission { PENDING, VERIFIED, REJECTED, STALE }

	boolean recoveryAnchorAvailable(Player player, PhantomTopologyAnchor anchor)
	{
		final var lifetime = _materialization.findByCharacterObjectId(player.getObjectId()).orElse(null);
		if (lifetime == null || searchHeldAtAnchor(lifetime.profileId(), player, lifetime.materializedAtNanos(), anchor)) { return false; }
		final var alternatives = _alternatives.get(lifetime.profileId());
		if (alternatives == null || !alternatives.matches(player, lifetime.materializedAtNanos())) { return true; }
		synchronized (alternatives)
		{
			final long now = _clock.getAsLong();
			if (alternatives.proposal != null && now < alternatives.deadline) { return alternatives.proposal.anchor.equals(anchor.id()); }
			if (now >= alternatives.deadline) { return now >= alternatives.nextEpisode; }
			return alternatives.remaining > 0 && (alternatives.retryEpisode || !alternatives.tried.contains(anchor.id()));
		}
	}

	RecoveryWalkAdmission assessRecoveryWalk(PhantomGoal predecessor, PhantomGoal proposed, PhantomTopologyPoint live, Player player)
	{
		final var lifetime = _materialization.findByCharacterObjectId(player.getObjectId()).orElse(null);
		if (lifetime == null) { return RecoveryWalkAdmission.STALE; }
		final long profileId = lifetime.profileId(), epoch = lifetime.materializedAtNanos();
		if (!PhantomNativeTravelActions.ordinary(player) || !lifetime.actionAdmissionOpen()
			|| !currentGoal(profileId, predecessor) || !navigationPoint(live).equals(PhantomNativeTravelActions.point(player))
			|| proposed.goalId() != predecessor.goalId() || proposed.revision() != predecessor.revision() + 1
			|| !Objects.equals(proposed.subject(), predecessor.subject())) { return RecoveryWalkAdmission.STALE; }
		final var state = _background.acquisitionSnapshot(profileId).orElse(null);
		if (state == null || state.state() != PhantomBackgroundState.State.MATERIALIZED
			|| state.identity().characterObjectId() != player.getObjectId()) { return RecoveryWalkAdmission.STALE; }
		final String anchor = PhantomBackgroundGoalSpec.parse(proposed).anchorId();
		final long now = _clock.getAsLong();
		final var alternatives = _alternatives.computeIfAbsent(profileId, _ -> new AlternativeWalks(player, epoch, now, _navigation.policy().maximumAttemptDurationNanos()));
		if (!alternatives.matches(player, epoch)) { return RecoveryWalkAdmission.STALE; }
		synchronized (alternatives)
		{
			if (now >= alternatives.deadline)
			{
				discardProposal(profileId, alternatives);
				if (now < alternatives.nextEpisode) { return RecoveryWalkAdmission.REJECTED; }
				alternatives.retryEpisode = true;
				alternatives.remaining = 1;
				alternatives.deadline = now + _navigation.policy().maximumAttemptDurationNanos();
				alternatives.nextEpisode = alternatives.deadline;
			}
		}
		final var first = _travel.topology().findAnchor(anchor).orElse(null);
		// Verify the replacement's own approach from the live Player. A failed
		// common ingress to the old itinerary cannot veto a different farm.
		final var step = localFarmApproach(first, live, _navigation.policy().maximumRouteDistance());
		if (step == null) { return rejectRecoveryAnchor(profileId, alternatives, anchor); }
		final var canonical = first == null ? null : L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(first, player.getHeading()).orElse(null);
		if (canonical == null) { return rejectRecoveryAnchor(profileId, alternatives, anchor); }
		final var point = new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), canonical.instanceId());
		final var destination = first.role() == PhantomTopologyAnchorRole.FARMING ? farmingStandpoint(profileId, first, point) : point;
		if (destination == null) { return rejectRecoveryAnchor(profileId, alternatives, anchor); }
		final Stamp stamp;
		final PlayerNativeWork.Owner owner;
		final PhantomNavigationPoint origin;
		final PhantomFarmRouteBackoff.Site site;
		final List<PhantomNavigationRequest.PveThreat> threats;
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (action == null || action.player() != player || action.executionStamp() == null || !PhantomNativeTravelActions.ordinary(player)
				|| player.isMoving() || player.hasPendingOwnedStore()) { return RecoveryWalkAdmission.STALE; }
			stamp = action.executionStamp();
			owner = player.getNativeWorkOwner();
			origin = PhantomNativeTravelActions.point(player);
			if (!origin.equals(navigationPoint(live))) { return RecoveryWalkAdmission.STALE; }
			site = searchSite(profileId, player, epoch);
			threats = pveThreats(origin, player.getLevel(), List.copyOf(World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.actor.instance.Monster.class, 3500)));
		}
		catch (IllegalArgumentException invalidThreats) { return rejectRecoveryAnchor(profileId, alternatives, anchor); }
		synchronized (alternatives)
		{
			var proposal = alternatives.proposal;
			final long deadline = alternatives.deadline;
			final var learnedReturn = alternatives.learnedReturn;
			final boolean landingApproach = learnedReturn != null && learnedReturn.checkpointed && !learnedReturn.cancelled
				&& learnedReturn.goal.equals(predecessor) && step.id().startsWith("live.approach.");
			if (now >= deadline || now < alternatives.started)
			{
				discardProposal(profileId, alternatives);
				return RecoveryWalkAdmission.REJECTED;
			}
			if (proposal == null)
			{
				if (now < alternatives.submitAt) { return RecoveryWalkAdmission.PENDING; }
				if (alternatives.remaining <= 0 || !alternatives.retryEpisode && alternatives.tried.contains(anchor)) { return RecoveryWalkAdmission.REJECTED; }
				final var probe = new PhantomFarmRouteBackoff.Probe(site, origin, destination);
				if (_farmSearches.blocked(probe, now)) { return rejectRecoveryAnchor(profileId, alternatives, anchor); }
				final var request = new PhantomNavigationRequest(profileId, origin, destination, now,
					Math.min(deadline, now + _navigation.policy().defaultRequestDeadlineNanos()), _navigation.policy().maximumRouteDistance(), threats);
				final var submission = _navigation.submitSoloWalk(request, landingApproach, Set.of(), Set.of());
				if (submission.requestId() <= 0)
				{
					alternatives.submitAt = now + _navigation.policy().pathfindingCooldownNanos();
					return RecoveryWalkAdmission.PENDING;
				}
				alternatives.remaining--;
				if (!alternatives.retryEpisode) { alternatives.tried.add(anchor); }
				proposal = new FarmWalkProposal(predecessor, proposed, anchor, step, probe, owner, stamp, submission.requestId(), landingApproach);
				alternatives.proposal = proposal;
			}
			if (!proposal.matches(predecessor, proposed, site, origin, owner, stamp))
			{
				discardProposal(profileId, alternatives);
				return RecoveryWalkAdmission.STALE;
			}
			if (proposal.result == null) { proposal.result = _navigation.consume(proposal.requestId).orElse(null); }
			if (proposal.result == null && _navigation.find(proposal.requestId).isPresent()) { return RecoveryWalkAdmission.PENDING; }
			if (proposal.result != null && navigationFailureDisposition(proposal.result.status()) == Disposition.TRANSIENT_SERVICE)
			{
				discardProposal(profileId, alternatives);
				alternatives.remaining++;
				if (!alternatives.retryEpisode) { alternatives.tried.remove(anchor); }
				alternatives.submitAt = now + _navigation.policy().pathfindingCooldownNanos();
				return RecoveryWalkAdmission.PENDING;
			}
			final var route = proposal.result == null ? null : proposal.result.route();
			if (route == null || route.mode() == PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA || !route.origin().equals(origin)
				|| (landingApproach ? route.destination().equals(origin) : !route.destination().equals(destination))
				|| proposal.result.profileId() != profileId || proposal.result.requestId() != proposal.requestId)
			{
				final var proof = proposal.result == null ? null : proposal.result.proof();
				if (proof != null && origin.equals(proof.origin()) && destination.equals(proof.submittedDestination()))
				{
					_farmSearches.failed(proposal.probe, proof.kind(), proof.queries(), now,
						"Alternative farm route profile=" + profileId + ";anchor=" + anchor + ";proof=" + proof.diagnostic());
				}
				discardProposal(profileId, alternatives);
				return RecoveryWalkAdmission.REJECTED;
			}
			return RecoveryWalkAdmission.VERIFIED;
		}
	}

	private RecoveryWalkAdmission rejectRecoveryAnchor(long profileId, AlternativeWalks alternatives, String anchor)
	{
		synchronized (alternatives)
		{
			if (alternatives.remaining > 0 && (alternatives.retryEpisode || alternatives.tried.add(anchor))) { alternatives.remaining--; }
			if (alternatives.proposal != null && alternatives.proposal.anchor.equals(anchor)) { discardProposal(profileId, alternatives); }
		}
		return RecoveryWalkAdmission.REJECTED;
	}

	boolean recoveryWalkCurrent(PhantomGoal predecessor, PhantomGoal proposed, Player player)
	{
		final var lifetime = _materialization.findByCharacterObjectId(player.getObjectId()).orElse(null);
		final var alternatives = lifetime == null ? null : _alternatives.get(lifetime.profileId());
		if (alternatives == null || !alternatives.matches(player, lifetime.materializedAtNanos())
			|| !currentGoal(lifetime.profileId(), predecessor)) { return false; }
		final var proposal = alternatives.proposal;
		if (proposal == null || proposal.result == null || proposal.result.route() == null) { return false; }
		try (var action = _materialization.tryAcquireAction(lifetime.profileId(), proposal.stamp).orElse(null))
		{
			return action != null && action.player() == player && !player.isMoving() && !player.hasPendingOwnedStore()
				&& PhantomNativeTravelActions.ordinary(player) && alternatives.proposal == proposal
				&& proposal.matches(predecessor, proposed, searchSite(lifetime.profileId(), player, lifetime.materializedAtNanos()),
					PhantomNativeTravelActions.point(player), player.getNativeWorkOwner(), action.executionStamp());
		}
	}

	void abandonRecoveryWalk(PhantomGoal predecessor, PhantomGoal proposed, Player player)
	{
		final var lifetime = _materialization.findByCharacterObjectId(player.getObjectId()).orElse(null);
		final var alternatives = lifetime == null ? null : _alternatives.get(lifetime.profileId());
		if (alternatives == null) { return; }
		synchronized (alternatives)
		{
			final var proposal = alternatives.proposal;
			if (proposal != null && proposal.predecessor.equals(predecessor) && proposal.successor.equals(proposed))
			{ discardProposal(lifetime.profileId(), alternatives); }
		}
	}

	private void discardProposal(long profileId, AlternativeWalks alternatives)
	{
		final var proposal = alternatives.proposal;
		if (proposal != null)
		{
			alternatives.proposal = null;
			_navigation.cancel(profileId, proposal.requestId);
			_navigation.consume(proposal.requestId);
		}
	}

	enum RecoveryReturn { NONE, HOLD, LANDED }

	/** Called by the same foreground preparation owner after its eligible WALK proposals are exhausted. */
	boolean queueRecoveryReturn(long profileId, PhantomGoal goal, Player player, long epoch, PhantomTopologyPoint live,
		PhantomBackgroundState baseline, BooleanSupplier currentRoot)
	{
		if (!currentGoal(profileId, goal) || !currentRoot.getAsBoolean() || _groupTravelSafety.test(profileId, goal)
			|| !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType()) || _journeys.containsKey(profileId)) { return false; }
		final long now = _clock.getAsLong();
		final long duration = _navigation.policy().maximumAttemptDurationNanos();
		final var alternatives = _alternatives.computeIfAbsent(profileId, _ -> new AlternativeWalks(player, epoch, now, duration));
		if (!alternatives.matches(player, epoch)) { return false; }
		final LearnedReturn recovery;
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (action == null || action.player() != player || action.executionStamp() == null || !returnIdle(player)
				|| !navigationPoint(live).equals(PhantomNativeTravelActions.point(player))
				|| hasFreshLocalActionUnderLease(profileId, goal, player, baseline)
				|| !(player.getNativeWorkOwner() instanceof PhantomNativeWorkScope owner)) { return false; }
			final var skill = player.getKnownSkill(1050);
			if (skill == null) { return false; }
			final int castMillis = player.calculateSkillCastTime(skill);
			final long required = castMillis * 1_000_000L + _navigation.policy().defaultRequestDeadlineNanos();
			if (castMillis <= 0 || required >= duration) { return false; }
			synchronized (alternatives)
			{
				if (alternatives.learnedReturn != null || alternatives.proposal != null
					|| alternatives.remainingReturnEvaluations == 0 || now < alternatives.nextReturnEvaluation) { return false; }
				// WAIT releases this episode. Its next slice runs Return before restarting WALK.
				final long start = alternatives.deadline - now >= required ? now : Math.max(now, alternatives.nextEpisode);
				final long deadline = start == now && alternatives.deadline - now >= required ? alternatives.deadline : start + duration;
				recovery = new LearnedReturn(goal, player, owner, action.executionStamp(), navigationPoint(live),
					searchSite(profileId, player, epoch), currentRoot, start, deadline);
				alternatives.remainingReturnEvaluations--;
				alternatives.learnedReturn = recovery;
			}
		}
		return continueRecoveryReturn(profileId, goal) != RecoveryReturn.NONE;
	}

	RecoveryReturn continueRecoveryReturn(long profileId, PhantomGoal goal)
	{
		final var incident = _alternatives.get(profileId);
		final var recovery = incident == null ? null : incident.learnedReturn;
		if (recovery == null || !recovery.goal.equals(goal) || recovery.cancelled) { return RecoveryReturn.NONE; }
		if (!recovery.processing.compareAndSet(false, true)) { return RecoveryReturn.HOLD; }
		try
		{
			return advanceRecoveryReturn(profileId, incident, recovery);
		}
		finally { recovery.processing.set(false); }
	}

	private RecoveryReturn advanceRecoveryReturn(long profileId, AlternativeWalks incident, LearnedReturn recovery)
	{
		final var player = recovery.player;
		final long now = _clock.getAsLong();
		if (recovery.unreconciled) { return RecoveryReturn.HOLD; }
		if (recovery.settled && (recovery.receipt == null || recovery.receipt.arrivals().isEmpty())) { return RecoveryReturn.NONE; }
		if (!recovery.currentRoot.getAsBoolean() || !recoveryLifetimeCurrent(profileId, incident, recovery))
		{
			cancelRecoveryReturn(profileId, recovery);
			return RecoveryReturn.NONE;
		}
		if (now < recovery.start) { return RecoveryReturn.HOLD; }
		if (recovery.attempt == null)
		{
			try (var action = _materialization.tryAcquireAction(profileId, recovery.stamp).orElse(null))
			{
				final var state = _background.acquisitionSnapshot(profileId).orElse(null);
				final var skill = player.getKnownSkill(1050);
				if (action == null || action.player() != player || !returnIdle(player) || skill == null || state == null
					|| hasFreshLocalActionUnderLease(profileId, recovery.goal, player, state)
					|| recovery.deadline - now < player.calculateSkillCastTime(skill) * 1_000_000L + _navigation.policy().defaultRequestDeadlineNanos()
					|| !returnCastCurrent(profileId, incident, recovery))
				{
					deferUnissuedReturn(profileId, incident, recovery, now, "travel.return_admission_changed");
					return RecoveryReturn.NONE;
				}
				recovery.attempt = PhantomLearnedEscape.startSolo(player, () -> returnCastCurrent(profileId, incident, recovery));
			}
			if (recovery.attempt == null)
			{
				deferUnissuedReturn(profileId, incident, recovery, now, "travel.return_native_denied");
				return RecoveryReturn.NONE;
			}
			recordReturn(profileId, recovery, "ISSUED", recovery.origin.toString());
		}
		final var status = recovery.attempt.status();
		if (status == PhantomLearnedEscape.Status.PENDING)
		{
			if (now >= recovery.deadline || player.isInParty() || player.isAlikeDead()) { requestReturnCancellation(profileId, recovery); }
			hold(profileId);
			return RecoveryReturn.HOLD;
		}
		if (!recovery.settled)
		{
			recovery.receipt = recovery.attempt.receipt();
			recovery.settled = true;
			recordReturn(profileId, recovery, status.name(), String.valueOf(recovery.receipt));
		}
		final var actual = PhantomNativeTravelActions.point(player);
		if (recovery.receipt == null || recovery.receipt.arrivals().isEmpty())
		{
			if (actual.equals(recovery.origin)) { return RecoveryReturn.NONE; }
			recovery.unreconciled = true;
			recordReturn(profileId, recovery, "WAIT", "travel.return_native_position_unreconciled");
			return RecoveryReturn.HOLD;
		}
		final var landing = verifiedReturnLanding(recovery.receipt, player.getObjectId(), incident.epoch, recovery.origin, actual);
		if (landing == null || !currentGoal(profileId, recovery.goal))
		{
			recovery.unreconciled = true;
			recordReturn(profileId, recovery, "WAIT", "travel.return_landing_mismatch");
			return RecoveryReturn.HOLD;
		}
		if (recovery.checkpointed) { return RecoveryReturn.LANDED; }
		try (var action = _materialization.tryAcquireAction(profileId, recovery.stamp).orElse(null))
		{
			if (action == null || action.player() != player || !returnIdle(player)
				|| !actual.equals(PhantomNativeTravelActions.point(player))) { return RecoveryReturn.HOLD; }
		}
		final var snapshot = _background.acquisitionSnapshot(profileId).orElse(null);
		if (snapshot == null) { return RecoveryReturn.HOLD; }
		final String previousAnchor = snapshot.position().committedAnchorId();
		final var anchor = departure(player, previousAnchor);
		if (anchor != null ? !isUsableLocalFarmPosition(player, anchor)
			: _travel.topology().findAnchor(previousAnchor).isEmpty() || !knownDryReturnLanding(actual))
		{
			recovery.unreconciled = true;
			recordReturn(profileId, recovery, "WAIT", "travel.return_landing_unproven");
			return RecoveryReturn.HOLD;
		}
		// The normal owned checkpoint stores actual XYZ. Off-anchor positions retain lineage only.
		final String checkpointAnchor = anchor == null ? previousAnchor : anchor.id();
		// The native cast action is closed. The existing owned checkpoint drains earned work and attests the exact Player.
		if (!_background.captureVisibleArrival(profileId, player, recovery.goal, checkpointAnchor)) { return RecoveryReturn.HOLD; }
		final var stored = _background.acquisitionSnapshot(profileId).orElse(null);
		if (stored == null || !checkpointAnchor.equals(stored.position().committedAnchorId()) || stored.identity().characterObjectId() != player.getObjectId()
			|| !actual.equals(new PhantomNavigationPoint(stored.position().x(), stored.position().y(), stored.position().z(), stored.position().instanceId())))
		{
			recovery.unreconciled = true;
			recordReturn(profileId, recovery, "WAIT", "travel.return_checkpoint_mismatch");
			return RecoveryReturn.HOLD;
		}
		synchronized (incident)
		{
			// The factual landing opens one new origin-bound proposal when the current slice was spent.
			// Keep the incident exclusions, Return receipt and existing deadline.
			if (incident.remaining == 0 && _clock.getAsLong() < incident.deadline) { incident.remaining = 1; }
			recovery.checkpointed = true;
		}
		return RecoveryReturn.LANDED;
	}

	private static boolean knownDryReturnLanding(PhantomNavigationPoint actual)
	{
		final var geo = GeoEngine.getInstance();
		if (!geo.hasGeo(actual.x(), actual.y())) { return false; }
		final int height = geo.getHeight(actual.x(), actual.y(), actual.z());
		if (height != geo.getHeight(actual.x(), actual.y(), height)) { return false; }
		final var floor = new PhantomNavigationPoint(actual.x(), actual.y(), height, actual.instanceId());
		final var geometry = shoreGeometry();
		return geometry.water(actual) == 0 && geometry.water(floor) == 0 && atWalkDestination(actual, floor, 0, () -> height);
	}

	private boolean recoveryLifetimeCurrent(long profileId, AlternativeWalks incident, LearnedReturn recovery)
	{
		final var player = recovery.player;
		final var current = _materialization.find(profileId).orElse(null);
		return current != null && current.characterObjectId() == player.getObjectId() && incident.epoch == current.materializedAtNanos()
			&& _alternatives.get(profileId) == incident && incident.learnedReturn == recovery && player.getNativeWorkOwner() == recovery.owner
			&& recovery.owner.isCurrent() && !recovery.owner.sealed() && World.getInstance().getPlayer(player.getObjectId()) == player;
	}

	private void deferUnissuedReturn(long profileId, AlternativeWalks incident, LearnedReturn recovery, long now, String reason)
	{
		recovery.settled = true;
		synchronized (incident)
		{
			if (incident.learnedReturn != recovery) { return; }
			// Only an issued native cast consumes the incident's Return. A denial releases the
			// slot for at most one later evaluation without renewing WALK or allowing a pulse retry.
			incident.nextReturnEvaluation = now + _navigation.policy().maximumAttemptDurationNanos();
			incident.learnedReturn = null;
		}
		recordReturn(profileId, recovery, "WAIT", reason);
	}

	private boolean returnCastCurrent(long profileId, AlternativeWalks incident, LearnedReturn recovery)
	{
		final var player = recovery.player;
		return !recovery.cancelled && !recovery.cancelCastRequested && _clock.getAsLong() < recovery.deadline && recovery.currentRoot.getAsBoolean()
			&& recovery.stamp.equals(recovery.owner.currentExecutionStamp()) && recovery.owner.open() && recoveryLifetimeCurrent(profileId, incident, recovery)
			&& recovery.site.equals(searchSite(profileId, player, incident.epoch)) && recovery.origin.equals(PhantomNativeTravelActions.point(player))
			&& player.getParty() == null && !player.isAlikeDead() && !player.isInCombat() && !player.isTeleporting()
			&& !player.hasPendingOwnedStore() && !player.isInsideZone(ZoneId.SIEGE) && !player.isMoving();
	}

	boolean recoveryReturnLandingCurrent(long profileId, PhantomGoal goal, Player player, PhantomTopologyPoint live)
	{
		final var incident = _alternatives.get(profileId);
		final var recovery = incident == null ? null : incident.learnedReturn;
		if (recovery == null || !recovery.checkpointed || recovery.cancelled || !goal.equals(recovery.goal)
			|| !recovery.currentRoot.getAsBoolean() || !recoveryLifetimeCurrent(profileId, incident, recovery)) { return false; }
		try (var action = _materialization.tryAcquireAction(profileId, recovery.stamp).orElse(null))
		{
			final var actual = PhantomNativeTravelActions.point(player);
			return action != null && action.player() == player && returnIdle(player) && actual.equals(navigationPoint(live))
				&& verifiedReturnLanding(recovery.receipt, player.getObjectId(), incident.epoch, recovery.origin, actual) != null;
		}
	}

	private static boolean returnIdle(Player player)
	{
		return PhantomNativeTravelActions.ordinary(player) && !player.isMoving() && !player.isAttackingOrCastingNow()
			&& !player.isMovementDisabled() && !player.isTeleporting() && !player.hasPendingOwnedStore()
			&& !player.isInsideZone(ZoneId.WATER) && !player.isInWater();
	}

	static PhantomNavigationPoint verifiedReturnLanding(PhantomLearnedEscape.Receipt receipt, int objectId, long epoch,
		PhantomNavigationPoint origin, PhantomNavigationPoint actual)
	{
		if (receipt == null || receipt.skillId() != 1050 || receipt.status() != PhantomLearnedEscape.Status.ARRIVED
			|| receipt.casterObjectId() != objectId || receipt.casterEpoch() != epoch || receipt.arrivals().size() != 1
			|| actual.instanceId() != 0 || origin.equals(actual)) { return null; }
		final var arrival = receipt.arrivals().getFirst();
		return arrival.objectId() == objectId && arrival.epoch() == epoch && arrival.before().equals(origin)
			&& arrival.after().equals(actual) ? actual : null;
	}

	private void cancelRecoveryReturn(long profileId, LearnedReturn recovery)
	{
		recovery.cancelled = true;
		requestReturnCancellation(profileId, recovery);
	}

	private void requestReturnCancellation(long profileId, LearnedReturn recovery)
	{
		if (recovery.cancelCastRequested) { return; }
		recovery.cancelCastRequested = true;
		final var attempt = recovery.attempt;
		if (attempt == null || attempt.status() != PhantomLearnedEscape.Status.PENDING) { return; }
		try (var action = _materialization.tryAcquireAction(profileId, recovery.stamp).orElse(null))
		{
			if (action != null && action.player() == recovery.player) { attempt.cancel(); }
		}
	}

	private static void recordReturn(long profileId, LearnedReturn recovery, String phase, String detail)
	{
		final var recorder = org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance();
		if (recorder.isRecording())
		{
			recorder.record(profileId, "VISIBLE_FARM_LEARNED_RETURN", phase, "1050", detail,
				recovery.player.getObjectId(), recovery.owner.epoch(), recovery.goal.revision());
		}
	}

	boolean searchHeld(long profileId, PhantomGoal goal, Player player, long epoch)
	{
		if (player == null || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType()) || !_farmSearches.hasFailures(profileId, _clock.getAsLong())
			|| player.isInParty() || _groupTravelSafety.test(profileId, goal)) { return false; }
		final var site = searchSite(profileId, player, epoch);
		final var origin = PhantomNativeTravelActions.point(player);
		final var attempt = _attempts.get(profileId);
		if (attempt != null && attempt.matches(goal, player, epoch) && searchHeld(attempt, site, origin)) { return true; }
		final var anchor = _travel.topology().findAnchor(PhantomBackgroundGoalSpec.parse(goal).anchorId()).orElse(null);
		return anchor != null && searchHeldAtAnchor(site, origin, anchor);
	}

	boolean searchHeldAtAnchor(long profileId, Player player, long epoch, PhantomTopologyAnchor anchor)
	{
		return searchHeldAtAnchor(searchSite(profileId, player, epoch), PhantomNativeTravelActions.point(player), anchor);
	}

	private boolean searchHeldAtAnchor(PhantomFarmRouteBackoff.Site site, PhantomNavigationPoint origin, PhantomTopologyAnchor anchor)
	{
		return _farmSearches.blocked(new PhantomFarmRouteBackoff.Probe(site, origin, navigationPoint(anchor.point())), _clock.getAsLong());
	}

	private boolean searchHeld(Attempt attempt, PhantomFarmRouteBackoff.Site site, PhantomNavigationPoint origin)
	{
		final var destination = attempt.indeterminateSearch != null ? attempt.indeterminateSearch.destination() : attempt.suppressedSearch;
		return destination != null && _farmSearches.blocked(new PhantomFarmRouteBackoff.Probe(site, origin, destination), _clock.getAsLong());
	}

	private PhantomFarmRouteBackoff.Site searchSite(long profileId, Player player, long epoch)
	{
		return new PhantomFarmRouteBackoff.Site(profileId, player.getObjectId(), epoch, _travel.topology().snapshot().generation(),
			ZoneManager.getInstance().loadedGeneration(), GeoEngine.getInstance().generation());
	}

	private long provenSearchTraversals(PhantomFarmRouteBackoff.Probe probe)
	{
		final var learned = _navigation.routeLearning();
		if (learned == null || probe.origin().instanceId() != 0 || probe.destination().instanceId() != 0) { return 0; }
		final var evidence = learned.find(new PhantomWalkCorridorPlanner.Edge(probe.origin(), probe.destination()));
		final long generation = learned.recoveryProofGeneration(probe.origin(), probe.destination(), _navigation.policy().maximumLocalStraightDistance());
		return generation * 65_536L + (evidence == null ? 0 : evidence.successes());
	}

	private static boolean sameSearchSurface(PhantomNavigationPoint first, PhantomNavigationPoint next)
	{
		final var geo = GeoEngine.getInstance();
		return geo.hasGeo(first.x(), first.y()) && geo.hasGeo(next.x(), next.y())
			&& geo.getHeight(first.x(), first.y(), next.z()) == geo.getHeight(first.x(), first.y(), first.z())
			&& geo.getHeight(next.x(), next.y(), first.z()) == geo.getHeight(next.x(), next.y(), next.z());
	}

	private static PhantomNavigationPoint navigationPoint(PhantomTopologyPoint point)
	{
		return new PhantomNavigationPoint(point.x(), point.y(), point.z(), point.instanceId());
	}

	private PhantomNormalGatekeeperTravel.Step proposedStep(Player player, String committedAnchorId, String destination, boolean recoverOffAnchor)
	{
		final var departure = departure(player, committedAnchorId);
		final var live = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		final var local = localFarmApproach(_travel.topology().findAnchor(destination).orElse(null),
			live, _navigation.policy().maximumLocalStraightDistance());
		if (local != null) { return local; }
		final var committed = _travel.topology().findAnchor(committedAnchorId).orElse(null);
		final String from = departure != null ? departure.id() : committed == null ? null : committed.id();
		final var route = from == null ? null : _travel.route(from, destination).orElse(null);
		final long minute = java.time.Instant.now().toEpochMilli() / 60000;
		final var affordable = route != null && itineraryAffordable(route, player.getClassIndex(), player.getLevel(), minute, player.getAdena()) ? route : null;
		final var canonical = departure == null && committed != null ? L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(committed, player.getHeading()).orElse(null) : null;
		if (!recoverOffAnchor) { return affordable == null ? null : resumeStep(affordable, departure == null ? null : departure.id(), canonical, live, destination); }
		return resumeWithIngress(affordable, departure == null ? null : departure.id(), canonical, live, destination,
			_travel.topology().snapshot().anchors(), _navigation.policy().maximumRouteDistance(), anchor ->
			{
				if (!PhantomNativeTravelActions.ordinary(player) || L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, player.getHeading()).isEmpty()) { return false; }
				final var lifetime = _materialization.findByCharacterObjectId(player.getObjectId()).orElse(null);
				if (lifetime == null || !lifetime.actionAdmissionOpen()) { return false; }
				final var onward = _travel.route(anchor.id(), destination).orElse(null);
				final var excluded = _routeExclusions.apply(lifetime.profileId());
				return onward != null && !onward.isEmpty() && onward.size() <= 64 && itineraryStep(onward, anchor.id(), destination) != null
					&& onward.stream().noneMatch(step -> excluded.contains(step.id()))
					&& itineraryAffordable(onward, player.getClassIndex(), player.getLevel(), minute, player.getAdena());
			});
	}

	static PhantomNormalGatekeeperTravel.Step resumeWithIngress(List<PhantomNormalGatekeeperTravel.Step> route, String factualDeparture,
		PhantomBackgroundState.Position committed, PhantomTopologyPoint live, String destination, List<PhantomTopologyAnchor> anchors, int maximumDistance,
		java.util.function.Predicate<PhantomTopologyAnchor> connected)
	{
		final var resumed = route == null ? null : resumeStep(route, factualDeparture, committed, live, destination);
		if (resumed != null || (factualDeparture == null && committed == null)) { return resumed; }
		final var ingress = idleIngress(anchors, live, destination, maximumDistance, connected);
		// This is a bounded WALK proposal. Its actual arrival/store must establish the new departure.
		return ingress == null ? null : new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND,
			"live.approach." + ingress.id(), ingress.id(), ingress.id(), 0, null);
	}

	/** A local WALK proposal starts at the live Player, without claiming any factual anchor departure. */
	static PhantomNormalGatekeeperTravel.Step localFarmApproach(PhantomTopologyAnchor target, PhantomTopologyPoint live, int maximumDistance)
	{
		if (target == null || target.role() != PhantomTopologyAnchorRole.FARMING || target.point().instanceId() != live.instanceId()
			|| maximumDistance <= 0 || target.point().distance3D(live) > maximumDistance) { return null; }
		return new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "live.approach." + target.id(), target.id(), target.id(), 0, null);
	}
	/** A proposal only: native movement and factual anchor capture remain mandatory. */
	public static PhantomTopologyAnchor idleIngress(List<PhantomTopologyAnchor> anchors, PhantomTopologyPoint live, String destination, int maximumDistance, java.util.function.Predicate<PhantomTopologyAnchor> connected)
	{
		return anchors.stream().filter(anchor -> !anchor.id().equals(destination) && anchor.point().instanceId() == live.instanceId()
			&& anchor.point().distance3D(live) > 0 && anchor.point().distance3D(live) <= maximumDistance)
			.sorted(java.util.Comparator.comparingDouble((PhantomTopologyAnchor anchor) -> anchor.point().distance3D(live)).thenComparing(PhantomTopologyAnchor::id))
			.limit(16).filter(connected).findFirst().orElse(null);
	}
	public static PhantomTopologyAnchor idleIngress(PhantomNormalGatekeeperTravel travel, PhantomTopologyPoint live, String destination, int maximumDistance, int heading)
	{
		final var target = travel.topology().findAnchor(destination).orElse(null);
		if (target == null) { return null; }
		return idleIngress(travel.topology().snapshot().anchors(), live, destination, Math.min(maximumDistance, (int) Math.floor(target.point().distance3D(live))), anchor ->
		{
			if (L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, heading).isEmpty()) { return false; }
			final var route = travel.route(anchor.id(), destination).orElse(null);
			return route != null && !route.isEmpty() && route.size() <= 64 && itineraryStep(route, anchor.id(), destination) != null
				&& route.stream().allMatch(step -> step.type() == PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND);
		});
	}

	static PhantomNormalGatekeeperTravel.Step resumeStep(List<PhantomNormalGatekeeperTravel.Step> route, String factualDeparture,
		PhantomBackgroundState.Position committedTarget, PhantomTopologyPoint live, String destination)
	{
		if (factualDeparture != null) { return itineraryStep(route, factualDeparture, destination); }
		if (committedTarget == null || committedTarget.instanceId() != live.instanceId()
			|| Math.hypot((long) live.x() - committedTarget.x(), (long) live.y() - committedTarget.y()) > 4096
			|| itineraryStep(route, committedTarget.committedAnchorId(), destination) == null) { return null; }
		// A stored lineage is only a bounded WALK destination, never factual departure.
		// The unchanged arrival/checkpoint owner must prove it before another segment.
		final String current = committedTarget.committedAnchorId();
		return new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "live.approach." + current, current, current, 0, null);
	}

	private PhantomTopologyAnchor departure(Player player, String committedAnchor)
	{
		final var topology = _travel.topology();
		final var committed = topology.findAnchor(committedAnchor).orElse(null);
		if (committed != null && L2jPhantomBackgroundAuthority.livePositionAllowed(topology, player, committed)) { return committed; }
		// An assigned historical destination can annotate unchanged native coordinates.
		// Resolve only anchors of areas actually containing the Player, with the same
		// native position authority used by arrival; proximity alone is not an arrival.
		final var live = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		return topology.locate(live).stream().flatMap(node -> topology.snapshot().anchorsByNode().getOrDefault(node.id(), List.of()).stream())
			.filter(anchor -> L2jPhantomBackgroundAuthority.livePositionAllowed(topology, player, anchor))
			.sorted(java.util.Comparator.comparingDouble((PhantomTopologyAnchor anchor) -> anchor.point().distance3D(live)).thenComparing(PhantomTopologyAnchor::id)).findFirst().orElse(null);
	}

	static PhantomNormalGatekeeperTravel.Step itineraryStep(List<PhantomNormalGatekeeperTravel.Step> route, String departure, String destination)
	{
		if (route.isEmpty())
		{
			return departure.equals(destination) ? new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "live.approach." + destination, departure, destination, 0, null) : null;
		}
		String cursor = departure;
		for (var step : route)
		{
			if (!cursor.equals(step.fromAnchorId()) || cursor.equals(step.toAnchorId())) { return null; }
			cursor = step.toAnchorId();
		}
		return cursor.equals(destination) ? route.getFirst() : null;
	}

	static boolean itineraryAffordable(List<PhantomNormalGatekeeperTravel.Step> route, int classIndex, int level, long epochMinute, long adena)
	{
		long remaining = adena;
		if (remaining < 0) { return false; }
		for (var step : route)
		{
			if (step.type() != PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER) { continue; }
			if (step.gatekeeper() == null) { return false; }
			final long fee = PhantomNormalGatekeeperTravel.phantomFee(step.gatekeeper(), classIndex, level, epochMinute);
			if (fee > remaining) { return false; }
			remaining -= fee;
		}
		return true;
	}

	public PhantomVisibleFarmTravel(PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel travel, PhantomNavigationService navigation, LongPredicate permitsOrdinary, PhantomRelevanceSignalPort signals)
	{
		this(materialization, background, travel, navigation, permitsOrdinary, signals, (_id, _failure) -> {}, System::nanoTime);
	}

	public PhantomVisibleFarmTravel(PhantomMaterializationService materialization, PhantomBackgroundService background, PhantomNormalGatekeeperTravel travel, PhantomNavigationService navigation, LongPredicate permitsOrdinary, PhantomRelevanceSignalPort signals, BiConsumer<Long, Failure> failure, LongSupplier clock)
	{
		_materialization = Objects.requireNonNull(materialization);
		_background = Objects.requireNonNull(background);
		_travel = Objects.requireNonNull(travel);
		_navigation = Objects.requireNonNull(navigation);
		_farmSearches = new PhantomFarmRouteBackoff(navigation.policy().maximumTrackedProfiles(), this::provenSearchTraversals, PhantomVisibleFarmTravel::sameSearchSurface);
		_permitsOrdinary = Objects.requireNonNull(permitsOrdinary);
		_signals = Objects.requireNonNull(signals);
		_failure = Objects.requireNonNull(failure);
		_clock = Objects.requireNonNull(clock);
	}

	public boolean arrive(long profileId, PhantomGoal goal)
	{
		if (!currentGoal(profileId, goal)) { discardStale(profileId, goal); return false; }
		PendingStore pending = _pendingStores.get(profileId);
		if (pending == null)
		{
			// A failed checkpoint closes ordinary roots. Preserve its control receipt before advance can discard the Journey.
			final var lifetime = _materialization.find(profileId).orElse(null);
			final Player player = lifetime == null ? null : World.getInstance().getPlayer(lifetime.characterObjectId());
			if (player != null && player.hasPendingOwnedStore() && player.getNativeWorkOwner() != null
				&& player.getNativeWorkOwner().player() == player && player.getNativeWorkOwner().isCurrent()
				&& player.getNativeWorkOwner().epoch() == lifetime.materializedAtNanos())
			{
				_pendingStores.putIfAbsent(profileId, new PendingStore(player, lifetime.materializedAtNanos(), goal, null, _journeys.get(profileId)));
				pending = _pendingStores.get(profileId);
			}
		}
		if (pending == null)
		{
			if (advance(profileId, goal))
			{
				markRecoveryFarmArrival(profileId, goal);
				return true;
			}
			pending = _pendingStores.get(profileId);
		}
		if (!drainArrival(profileId, goal, pending, false)) { return false; }
		markRecoveryFarmArrival(profileId, goal);
		return true;
	}

	private void markRecoveryFarmArrival(long profileId, PhantomGoal goal)
	{
		final var incident = _alternatives.get(profileId);
		if (incident == null || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType())) { return; }
		final var player = incident.player;
		final var owner = player.getNativeWorkOwner();
		final var evidence = owner == null ? null : owner.evidence();
		if (owner == null || owner.player() != player || !owner.isCurrent() || owner.epoch() != incident.epoch
			|| evidence == null || !evidence.matches(player.getObjectId(), incident.epoch)) { return; }
		synchronized (incident)
		{
			final var arrived = incident.farmArrival;
			if (arrived == null || !arrived.goal().equals(goal))
			{
				incident.farmArrival = new RecoveryFarmArrival(goal, owner, evidence.snapshot());
			}
		}
	}

	boolean finishEarnedRecovery(long profileId, PhantomGoal goal, Player player, long epoch)
	{
		final var incident = _alternatives.get(profileId);
		if (incident == null || !incident.matches(player, epoch)) { return false; }
		final var arrived = incident.farmArrival;
		final var owner = player.getNativeWorkOwner();
		final var evidence = owner == null ? null : owner.evidence();
		if (arrived == null || !arrived.goal().equals(goal) || owner != arrived.owner() || !owner.isCurrent()
			|| owner.player() != player || owner.epoch() != epoch || evidence == null
			|| !settledFarmAfter(evidence, arrived.baseline(), player.getInstanceId()) || !currentGoal(profileId, goal)) { return false; }
		synchronized (incident)
		{
			if (incident.farmArrival != arrived || player.getNativeWorkOwner() != owner || !owner.isCurrent()
				|| incident.proposal != null || _journeys.containsKey(profileId) || _pendingStores.containsKey(profileId)
				|| player.hasPendingOwnedStore()) { return false; }
			return _alternatives.remove(profileId, incident);
		}
	}

	static boolean settledFarmAfter(PlayerNativeEvidence evidence, PlayerNativeEvidence.Snapshot baseline, int instanceId)
	{
		if (baseline.overflow() || !evidence.matches(baseline.objectId(), baseline.epoch())) { return false; }
		synchronized (evidence)
		{
			final var sample = evidence.snapshot();
			final var receipts = evidence.targetReceipts();
			final var target = receipts.rewardTarget();
			return !sample.overflow() && receipts.killSequence() > baseline.killSequence() && receipts.rewardSequence() > baseline.rewardSequence()
				&& sample.expGained() > baseline.expGained() && sample.spGained() > baseline.spGained()
				&& target != null && target.instanceId() == instanceId && target.equals(receipts.killTarget())
				&& target.equals(receipts.settledTarget()) && receipts.settledRewardSequence() == receipts.rewardSequence();
		}
	}

	private boolean drainArrival(long profileId, PhantomGoal goal, PendingStore pending, boolean preparation)
	{
		if (!currentGoal(profileId, goal)) { return arrivalRefused(profileId, goal, pending, "drain.current_goal"); }
		if (preparation && ((pending == null) || (pending.anchorId == null))) { return arrivalRefused(profileId, goal, pending, "drain.factual_anchor"); }
		if ((pending == null) || (_pendingStores.get(profileId) != pending) || !pending.goal.equals(goal)) { return arrivalRefused(profileId, goal, pending, "drain.pending_identity"); }
		if ((pending.journey != null) && (_journeys.get(profileId) != pending.journey))
		{
			closeRoutePhase(profileId, pending.journey);
			_pendingStores.remove(profileId, pending);
			return arrivalRefused(profileId, goal, pending, "drain.journey_identity");
		}
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((lifetime == null) || (lifetime.characterObjectId() != pending.player.getObjectId()) || (lifetime.materializedAtNanos() != pending.epoch))
		{
			if (pending.journey != null) { closeRoutePhase(profileId, pending.journey); }
			_pendingStores.remove(profileId, pending);
			return arrivalRefused(profileId, goal, pending, "drain.lifetime");
		}
		final var owner = pending.player.getNativeWorkOwner();
		if (owner == null || owner.player() != pending.player || !owner.isCurrent() || owner.epoch() != pending.epoch
			|| World.getInstance().getPlayer(pending.player.getObjectId()) != pending.player
			|| pending.player.isDead() || pending.player.isInParty() || !pending.player.hasHeadlessOutboundSession())
		{
			if (pending.journey != null) { closeRoutePhase(profileId, pending.journey); }
			return arrivalRefused(profileId, goal, pending, "drain.native_owner");
		}
		if (pending.anchorId != null && walkCombatActive(pending.player) && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType()))
		{
			// Preserve an admitted STORE or a checkpoint whose write outcome still needs reconciliation.
			if (_background.discardUnwrittenVisibleCheckpoint(profileId, pending.player, goal))
			{
				_pendingStores.remove(profileId, pending);
			}
			if (pending.journey != null) { pending.journey.reason = "travel.combat_yield"; }
			return false;
		}
		final PendingStore exactPending = pending;
		if ((pending.anchorId != null) && _travel.topology().findAnchor(pending.anchorId)
			.filter(anchor -> L2jPhantomBackgroundAuthority.livePositionAllowed(_travel.topology(), exactPending.player, anchor) && isUsableLocalFarmPosition(exactPending.player, anchor)).isEmpty()) { return arrivalRefused(profileId, goal, pending, "drain.anchor_position"); }
		if (pending.player.hasPendingOwnedStore())
		{
			final var resumed = _background.resumeVisibleOwnedStore(profileId, pending.player, goal);
			if (resumed.status() != PhantomBackgroundService.VisibleStoreStatus.SUCCESS)
			{
				rememberFailure(profileId, resumed.reason());
				return false;
			}
			if (pending.anchorId == null) { _pendingStores.remove(profileId, pending); return advance(profileId, goal); }
			// captureVisibleArrival also consumes the exact RESUME checkpoint request;
			// a following segment must not inherit the previous anchor's checkpoint key.
		}
		if (pending.anchorId == null) { _pendingStores.remove(profileId, pending); return false; }
		if (!_background.captureVisibleArrival(profileId, pending.player, goal, pending.anchorId))
		{
			final var checkpoint = _background.continueVisibleCheckpoint(profileId, goal).orElse(null);
			if (pending.journey != null) { pending.journey.reason = checkpoint == null ? "travel.arrival_capture_pending" : "travel.checkpoint." + checkpoint.outcome() + ":" + checkpoint.phase() + ":" + checkpoint.reason(); }
			return false;
		}
		final boolean completed = completeArrival(profileId, pending, preparation);
		return preparation && !completed ? arrivalRefused(profileId, goal, pending, "drain.completion") : completed;
	}

	private static boolean arrivalRefused(long profileId, PhantomGoal goal, PendingStore pending, String gate)
	{
		try
		{
			final var recorder = org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance();
			if (recorder.isRecording() && pending != null)
			{
				recorder.record(profileId, "VISIBLE_ARRIVAL_REFUSED", gate, pending == null ? "0" : Integer.toString(pending.player.getObjectId()),
					pending == null ? "" : pending.anchorId, pending == null ? 0 : pending.epoch, goal.goalId(), goal.revision());
			}
		}
		catch (Throwable ignored) { }
		return false;
	}

	/** Only an exact successful store/arrival receipt can start another bounded segment. */
	private boolean completeArrival(long profileId, PendingStore pending, boolean preparation)
	{
		final var state = _background.acquisitionSnapshot(profileId).orElse(null);
		if (state == null || !pending.anchorId.equals(state.position().committedAnchorId())
			|| state.identity().characterObjectId() != pending.player.getObjectId() || !currentGoal(profileId, pending.goal)) { return false; }
		final var anchor = _travel.topology().findAnchor(pending.anchorId).orElse(null);
		if (anchor == null || !L2jPhantomBackgroundAuthority.livePositionAllowed(_travel.topology(), pending.player, anchor)
			|| (pending.journey != null && !currentJourney(profileId, pending.journey))) { return false; }
		final boolean destination = pending.anchorId.equals(travelSpec(profileId, pending.goal).anchorId());
		synchronized (_attempts)
		{
			if (pending.journey != null && (_journeys.get(profileId) != pending.journey
				|| _attempts.get(profileId) != pending.journey.attempt)) { return false; }
			if (!_pendingStores.remove(profileId, pending)) { return false; }
			if (pending.journey != null)
			{
				final var memory = _navigation.routeLearning();
				final var observation = _journeyObservations.get(profileId);
				final var identity = observation == null ? null : observationIdentity(pending.journey.attempt);
				if (observation != null && identity != null)
				{
					if (destination && memory != null && !pending.player.isTeleporting())
					{
						memory.recordCompletedJourney(observation, identity, pending.anchorId, PhantomNativeTravelActions.point(pending.player), _clock.getAsLong());
					}
					else if (!destination) { observation.checkpoint(); }
				}
				_journeys.remove(profileId, pending.journey);
				closeRoutePhase(profileId, pending.journey);
			}
			if (!preparation && !destination && pending.journey != null && _attempts.remove(profileId, pending.journey.attempt))
			{
				clearRoutePhase(pending.journey.attempt);
			}
		}
		// Physical movement was stopped under the original ActionLease before capture.
		// Do not reacquire an ActionLease or touch Player while holding _attempts.
		if (pending.journey != null)
		{
			clearRoute(profileId, pending.journey);
			_signals.withdraw(profileId, "visible.farm.travel", signalSequence());
		}
		return preparation || destination;
	}

	private boolean advance(long profileId, PhantomGoal goal)
	{
		if (!currentGoal(profileId, goal)) { discardStale(profileId, goal); return false; }
		final var incident = _alternatives.get(profileId);
		final var recovery = incident == null ? null : incident.learnedReturn;
		if (recovery != null && recovery.goal.equals(goal) && recovery.holdsTravel()) { return false; }
		if (!_permitsOrdinary.test(profileId))
		{
			remove(profileId);
			return false;
		}
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if ((action == null) || action.player().isDead() || action.player().isInParty())
			{
				remove(profileId);
				return false;
			}
			final Player player = action.player();
			final long epoch = _materialization.find(profileId).map(value -> value.materializedAtNanos()).orElse(0L);
			final var spec = travelSpec(profileId, goal);
			final boolean idle = PhantomBackgroundGoalSpec.canonicalHistoricalIdle(profileId, goal);
			var state = _background.acquisitionSnapshot(profileId).orElse(null);
			if (player.hasPendingOwnedStore())
			{
				_pendingStores.put(profileId, new PendingStore(player, epoch, goal, null, null));
				return false;
			}
			if ((state == null) || (state.state() != PhantomBackgroundState.State.MATERIALIZED) || (state.identity().characterObjectId() != player.getObjectId()))
			{
				return false;
			}
			final var targetAnchor = _travel.topology().findAnchor(spec.anchorId()).orElse(null);
			final var existingAttempt = _attempts.get(profileId);
			final boolean sameAnchor = state.position().committedAnchorId().equals(spec.anchorId());
			Journey journey = _journeys.get(profileId);
			if ((journey != null) && ((journey.goalId != goal.goalId()) || (journey.revision != goal.revision()) || (journey.player != player) || (journey.epoch != epoch)))
			{
				remove(profileId, journey);
				journey = null;
			}
			if (yieldDryWalkForCombat(profileId, goal, player, epoch)) { return false; }
			if (journey != null) { publishNativeArrival(profileId, journey, player); }
			// A stand-point receipt forbids that route, not a fresh independently proven native target.
			if (!idle && sameAnchor && (targetAnchor != null) && hasLocalOpportunity(profileId, player, targetAnchor, spec.npcId()))
			{
				if (journey != null) { remove(profileId, journey); }
				return true;
			}
			if ((existingAttempt != null) && existingAttempt.matches(goal, player, epoch) && existingAttempt.terminal)
			{
				if (existingAttempt.indeterminateSearch == null || searchHeld(existingAttempt, searchSite(profileId, player, epoch), PhantomNativeTravelActions.point(player))) { return false; }
				// This native owner may try again after new physical evidence or expiry.
				// Only the exact terminal attempt is replaced, below, on the normal owned path.
			}
			// Same-anchor local execution can discover a lawful target before its exact action position is ready.
			// Travel releases only its own route; the Session controller then owns any approach.
			if (!idle && sameAnchor && targetAnchor != null && L2jPhantomBackgroundAuthority.livePositionAllowed(_travel.topology(), player, targetAnchor)
				&& isUsableLocalFarmPosition(player, targetAnchor)
				&& _engagementPlanner.inspect(player, spec.npcId(), java.util.Set.of(), _ -> 0).kind() != PhantomEngagementPlanner.Kind.UNAVAILABLE)
			{
				if (journey != null) { remove(profileId, journey); }
				return true;
			}
			if ((journey == null || (journey.dryRecovery == null && !journey.ordinaryWalkEpisodes())) && (targetAnchor != null) && L2jPhantomBackgroundAuthority.livePositionAllowed(_travel.topology(), player, targetAnchor) && isUsableLocalFarmPosition(player, targetAnchor))
			{
				if ((journey == null) && sameAnchor) { return true; }
				if (!idle && walkCombatActive(player)) { return false; }
				if (journey != null) { stopOwnedMove(journey, player); }
				if (player.isMoving()) { return false; }
				if (journey != null) { clearRoute(profileId, journey); closeRoutePhase(profileId, journey); }
				_pendingStores.put(profileId, new PendingStore(player, epoch, goal, targetAnchor.id(), journey));
				return false;
			}
			final Attempt attempt = attempt(profileId, goal, player, epoch);
			if ((attempt == null) || attempt.terminal) { return false; }
			if (journey == null)
			{
				final var alternatives = _alternatives.get(profileId);
				final var proposal = alternatives == null ? null : alternatives.proposal;
				var step = proposal != null && proposal.successor.equals(goal) && proposal.owner == player.getNativeWorkOwner()
					&& proposal.probe.site().equals(searchSite(profileId, player, epoch))
					&& proposal.probe.origin().equals(PhantomNativeTravelActions.point(player))
					? proposal.step : proposedStep(player, state.position().committedAnchorId(), spec.anchorId(), !idle);
				if (idle && step != null && step.type() == PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND)
				{
					// A connected source itinerary is guidance. Native WALK may approach the
					// actual goal without awarding its intermediate anchors offscreen.
					step = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "native.idle.walk." + spec.anchorId(), state.position().committedAnchorId(), spec.anchorId(), 0, null);
				}
				if (step == null && idle && targetAnchor != null && PhantomNativeTravelActions.ordinary(player)
					&& player.getInstanceId() == targetAnchor.point().instanceId() && player.calculateDistance3D(targetAnchor.point().x(), targetAnchor.point().y(), targetAnchor.point().z()) <= _navigation.policy().maximumRouteDistance())
				{
					// An ephemeral native WALK request is not an offscreen graph edge or a factual arrival.
					final var ingress = idleIngress(_travel, new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId()), spec.anchorId(), _navigation.policy().maximumRouteDistance(), player.getHeading());
					final String walkTarget = ingress == null ? spec.anchorId() : ingress.id();
					step = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "native.idle.walk." + walkTarget, state.position().committedAnchorId(), walkTarget, 0, null);
				}
				if (step == null)
				{
					fail(profileId, attempt, "", "travel.route_absent", Disposition.ROUTE_UNUSABLE);
					return false;
				}
				journey = new Journey(goal, player, epoch, step, attempt);
				synchronized (_attempts)
				{
					if ((_attempts.get(profileId) != attempt) || attempt.terminal || (_journeys.putIfAbsent(profileId, journey) != null)) { return false; }
					beginJourneyObservation(profileId, attempt, targetAnchor);
				}
			}
			if ((!journey.nativeIdleWalk && (journey.nativeBudget == null || journey.ordinaryWalkEpisodes())
				&& (_clock.getAsLong() - attempt.startedNanos) >= _navigation.policy().maximumAttemptDurationNanos())
				|| (journey.nativeBudget != null && journey.swim == null && journey.nativeBudget.expired(_clock.getAsLong())))
			{
				fail(profileId, journey, "travel.journey_deadline", Disposition.NATIVE_ACTION_REJECTED);
				return false;
			}
			if (attempt.transientExhausted) { return false; }
			if (_clock.getAsLong() < attempt.retryAtNanos) { return false; }
			final var arrival = _travel.topology().findAnchor(journey.step.toAnchorId()).orElseThrow();
			final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(arrival, player.getHeading()).orElse(null);
			if (canonical == null)
			{
				fail(profileId, journey, "travel.destination_unproven", Disposition.ROUTE_UNUSABLE);
				return false;
			}
			if (journey.arrivalPoint == null)
			{
				final var canonicalPoint = new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), canonical.instanceId());
				journey.arrivalPoint = arrival.role() == PhantomTopologyAnchorRole.FARMING ? farmingStandpoint(profileId, arrival, canonicalPoint) : canonicalPoint;
				if (journey.arrivalPoint == null)
				{
					fail(profileId, journey, "travel.farming_standpoint_unproven", Disposition.ROUTE_UNUSABLE);
					return false;
				}
			}
			if ((journey.nativeIdleWalk || journey.currentAnchorReturn) && journey.nativeBudget == null)
			{
				final var origin = new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
				final double speed = player.getMoveSpeed();
				if (origin.instanceId() != journey.arrivalPoint.instanceId() || !Double.isFinite(speed) || speed <= 0 || origin.distanceTo(journey.arrivalPoint) > _navigation.policy().maximumRouteDistance())
				{
					fail(profileId, journey, "travel.native_walk_budget_rejected", Disposition.ROUTE_UNUSABLE); return false;
				}
				journey.nativeBudget = new NativeWalkBudget(origin, journey.arrivalPoint, _clock.getAsLong(), speed, _navigation.policy());
				attempt.phaseSince = journey.nativeBudget.episodeSince;
			}
			adoptRecoveryWalk(profileId, journey, action.executionStamp());
			if (!executePlan(profileId, player, journey, action.executionStamp()))
			{
				return false;
			}
			stopOwnedMove(journey, player);
			closeRoutePhase(profileId, journey);
			journey.reason = "travel.arrival_capture_pending";
			_pendingStores.put(profileId, new PendingStore(player, epoch, goal, arrival.id(), journey));
			return false;
		}
	}

	/** Read-only fresh action proof; a failed stand-point receipt remains in its existing history. */
	public boolean hasFreshLocalAction(long profileId, PhantomGoal goal, long epoch)
	{
		if (!_permitsOrdinary.test(profileId)) { return false; }
		final var lifetime = _materialization.find(profileId).orElse(null);
		if (lifetime == null || lifetime.materializedAtNanos() != epoch || !lifetime.actionAdmissionOpen()) { return false; }
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (action == null) { return false; }
			final var player = action.player();
			return player.getObjectId() == lifetime.characterObjectId()
				&& hasFreshLocalActionUnderLease(profileId, goal, player, _background.acquisitionSnapshot(profileId).orElse(null));
		}
	}

	/** The caller holds the current actor's action lease and has already read the baseline. */
	boolean hasFreshLocalActionUnderLease(long profileId, PhantomGoal goal, Player player, PhantomBackgroundState state)
	{
		if (!_permitsOrdinary.test(profileId) || player.isDead() || player.isInParty() || player.hasPendingOwnedStore()
			|| state == null || state.state() != PhantomBackgroundState.State.MATERIALIZED || state.identity().characterObjectId() != player.getObjectId()) { return false; }
		final var spec = PhantomBackgroundGoalSpec.parse(goal);
		if (!state.position().committedAnchorId().equals(spec.anchorId())) { return false; }
		final var anchor = _travel.topology().findAnchor(spec.anchorId()).orElse(null);
		return anchor != null && hasLocalOpportunity(profileId, player, anchor, spec.npcId());
	}

	private boolean hasLocalOpportunity(long profileId, Player player, PhantomTopologyAnchor anchor, int npcId)
	{
		if ((anchor.role() != PhantomTopologyAnchorRole.FARMING) || !L2jPhantomBackgroundAuthority.livePositionAllowed(_travel.topology(), player, anchor)) { return false; }
		final var geo = GeoEngine.getInstance();
		if (!geo.hasGeo(player.getX(), player.getY()) || player.isInsideZone(ZoneId.WATER)
			|| (ZoneManager.getInstance().getZone(player.getX(), player.getY(), player.getZ(), WaterZone.class) != null)) { return false; }
		return World.getInstance().getVisibleObjectsInRange(player, Creature.class, AutoPlayConfig.AUTO_PLAY_LONG_RANGE).stream().anyMatch(target ->
		{
			final var opportunity = _engagementPlanner.inspect(player, target, npcId);
			// A fresh stock range-approach is also legal execution. It does not claim ARRIVED at
			// the action position: the Session controller and native PlayerAI still prove that.
			if (!geo.hasGeo(target.getX(), target.getY())) { return false; }
			if (opportunity.kind() == PhantomEngagementPlanner.Kind.READY) { return true; }
			final var nativePoint = new PhantomNavigationPoint(target.getX(), target.getY(), geo.getHeight(target.getX(), target.getY(), target.getZ()), target.getInstanceId());
			return opportunity.nativeDirect() && !_routeExclusions.apply(profileId).contains(localRouteWitness(anchor.id(), nativePoint));
		});
	}

	private static boolean isUsableLocalFarmPosition(Player player, PhantomTopologyAnchor anchor)
	{
		if (anchor.role() != PhantomTopologyAnchorRole.FARMING) { return true; }
		final var geo = GeoEngine.getInstance();
		return geo.hasGeo(player.getX(), player.getY()) && (geo.getHeight(player.getX(), player.getY(), player.getZ()) == player.getZ()) && (ZoneManager.getInstance().getZone(player.getX(), player.getY(), player.getZ(), WaterZone.class) == null);
	}

	/** A bounded local continuation of the factual farm anchor, stable for this Journey. */
	private PhantomNavigationPoint farmingStandpoint(long profileId, PhantomTopologyAnchor anchor, PhantomNavigationPoint canonical)
	{
		final var area = _travel.topology().findNode(anchor.nodeId()).map(node -> node.area()).orElse(null);
		if (area == null) { return null; }
		final var geo = GeoEngine.getInstance();
		for (var proposed : farmingCandidates(profileId, canonical))
		{
			final int x = proposed.x(); final int y = proposed.y();
			if (!geo.hasGeo(x, y)) { continue; }
			final int z = geo.getHeight(x, y, canonical.z());
			final var candidate = new PhantomNavigationPoint(x, y, z, canonical.instanceId());
			if (_routeExclusions.apply(profileId).contains(localRouteWitness(anchor.id(), candidate))) { continue; }
			if (area.contains(new PhantomTopologyPoint(x, y, z, candidate.instanceId())) && (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) == null) && (unsafeSegment(canonical, candidate, false, 0) == null) && geo.canMoveToTarget(canonical.x(), canonical.y(), canonical.z(), x, y, z, canonical.instanceId())) { return candidate; }
		}
		return null;
	}

	static List<PhantomNavigationPoint> farmingCandidates(long profileId, PhantomNavigationPoint canonical)
	{
		final var result = new java.util.ArrayList<PhantomNavigationPoint>();
		result.add(canonical);
		final int[][] directions = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
		for (int index = 0; index < 24; index++)
		{
			final int choice = (int) Math.floorMod(profileId + index, 24L);
			final int distance = 32 * (1 + (choice / directions.length));
			final int x = canonical.x() + (distance * directions[choice % directions.length][0]);
			final int y = canonical.y() + (distance * directions[choice % directions.length][1]);
			result.add(new PhantomNavigationPoint(x, y, canonical.z(), canonical.instanceId()));
		}
		return List.copyOf(result);
	}

	private boolean executePlan(long profileId, Player player, Journey journey, Stamp stamp)
	{
		if (journey.plan == null)
		{
			if (walk(profileId, player, journey, journey.arrivalPoint, 0, stamp)) { return true; }
			return false;
		}
		if (journey.stageIndex >= journey.plan.stages().size()) { return true; }
		final var stage = journey.plan.stages().get(journey.stageIndex);
		if (stage.mode() == PhantomTravelStrategy.Mode.WALK)
		{
			if (walk(profileId, player, journey, stage.to(), 0, stamp)) { journey.stageIndex++; hold(profileId); }
			return false;
		}
		if (stage.mode() == PhantomTravelStrategy.Mode.GK)
		{
			if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
			final var receipt = PhantomNativeTravelActions.gatekeeper(player, stage.gatekeeper());
			journey.reason = "travel.native_gatekeeper_" + receipt.status().name().toLowerCase(java.util.Locale.ROOT) + ";fee=" + receipt.fee() + ";before=" + receipt.before() + ";after=" + receipt.after();
			if (receipt.status() == PhantomNativeTravelActions.Status.ARRIVED)
			{
				observeTransport(profileId, journey);
				clearRoute(profileId, journey);
				journey.stageIndex++;
				hold(profileId);
			}
			else if (receipt.status() == PhantomNativeTravelActions.Status.PAID_WITHOUT_ARRIVAL || receipt.status() == PhantomNativeTravelActions.Status.INSUFFICIENT_RESOURCES) { fail(profileId, journey, journey.reason, Disposition.ROUTE_UNUSABLE); }
			else { retryOrFail(profileId, journey, Disposition.NATIVE_ACTION_REJECTED); }
			return false;
		}
		if (journey.returnOperation == null)
		{
			final int capturedStage=journey.stageIndex;
			journey.returnOperation=PhantomNativeTravelActions.returnToTown(player, stage.to(), () -> currentJourney(profileId, journey) && journey.stageIndex == capturedStage && _permitsOrdinary.test(profileId));
			if (journey.returnOperation == null) { journey.reason="travel.native_return_unavailable"; retryOrFail(profileId, journey, Disposition.NATIVE_ACTION_REJECTED); }
			else { journey.reason="travel.native_return_cast;millis="+stage.millis(); hold(profileId); }
		}
		else if (journey.returnOperation.arrived())
		{
			observeTransport(profileId, journey);
			journey.returnOperation = null;
			journey.stageIndex++;
			journey.reason = "travel.native_return_arrived";
			hold(profileId);
		}
		else { hold(profileId); }
		return false;
	}

	static boolean atWalkDestination(PhantomNavigationPoint current, PhantomNavigationPoint destination, int radius, java.util.function.IntSupplier groundHeight)
	{
		if (current.instanceId() != destination.instanceId() || Math.hypot((long) current.x() - destination.x(), (long) current.y() - destination.y()) > radius) { return false; }
		if (Math.abs((long) current.z() - destination.z()) <= (radius == 0 ? 0 : 100)) { return true; }
		// Native teleports land five units above their proven Geo floor. No move or position write is needed there.
		return radius == 0 && (long) current.z() - destination.z() == 5 && groundHeight.getAsInt() == destination.z();
	}

	private boolean walk(long profileId, Player player, Journey journey, PhantomNavigationPoint destination, int radius, Stamp stamp)
	{
		if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
		final var current = new PhantomNavigationPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		journey.pveSafeWalk |= _groupTravelSafety.test(profileId, journey.goal);
		final boolean pveSafeWalk = journey.pveSafeWalk || PhantomBackgroundGoalSpec.canonicalHistoricalIdle(profileId, journey.goal);
		final boolean dryRecovery = journey.dryRecovery != null;
		if (journey.swim != null) { return advanceSwim(profileId, journey, current, stamp); }
		if (yieldDryWalkForCombat(profileId, journey.goal, player, journey.epoch)) { return false; }
		if (dryRecovery && !currentDryRecovery(profileId, journey)) { remove(profileId, journey); return false; }
		final boolean guardThreats = pveSafeWalk || dryRecovery;
		if (guardThreats && journey.moveIssued && player.isMoving())
		{
			final var issued = new PhantomNavigationPoint(journey.moveX, journey.moveY, player.getZdestination(), player.getInstanceId());
			if (!safeIdlePveSegment(player, issued)) { replanEdge(profileId, player, journey, current, issued, "travel.native_pve_threat"); return false; }
		}
		if (atWalkDestination(current, destination, radius, () -> GeoEngine.getInstance().getHeight(current.x(), current.y(), current.z())))
		{
			if ((dryRecovery || journey.ordinaryWalkEpisodes()) && !destination.equals(journey.recoveryReceipt))
			{
				journey.reason = "travel.recovery_arrival_pending";
				return false;
			}
			if (dryRecovery) { recordDryRecovery(profileId, journey, "ARRIVED", current.toString()); }
			clearRoute(profileId, journey);
			return true;
		}
		if (player.isSitting() && !player.isTeleporting() && !player.isInCombat() && !player.isCastingNow() && player.getMoveSpeed() > 0)
		{
			if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
			player.standUp();
			journey.reason = "travel.native_stand_pending";
			return false;
		}
		if (player.isTeleporting() || player.isInCombat() || player.isCastingNow() || player.isMovementDisabled() || player.getMoveSpeed() <= 0)
		{
			journey.reason = "travel.native_action_busy";
			return false;
		}
		final long now = Math.max(1, _clock.getAsLong());
		if (now < journey.attempt.retryAtNanos)
		{
			return false;
		}
		if (journey.requestId == 0)
		{
			if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
			final boolean ordinarySearch = !pveSafeWalk && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(journey.goal.goalType());
			final var site = ordinarySearch ? searchSite(profileId, player, journey.epoch) : null;
			final var held = journey.heldSearch;
			if (ordinarySearch && held != null && held.site().equals(site) && held.origin().equals(current)
				&& destination.equals(journey.heldWalkDestination) && _farmSearches.blocked(held, now)) { return false; }
			journey.heldSearch = null;
			journey.heldWalkDestination = null;
			journey.deadline = now + _navigation.policy().defaultRequestDeadlineNanos();
			final List<PhantomNavigationRequest.PveThreat> threats;
			try { threats = guardThreats ? pveThreats(current, player.getLevel(), List.copyOf(World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.actor.instance.Monster.class, 3500))) : List.of(); }
			catch (IllegalArgumentException overflow) { fail(profileId, journey, "travel.native_threat_snapshot_limit", Disposition.ROUTE_UNUSABLE); return false; }
			var requestDestination = destination;
			if (journey.currentAnchorReturn && !dryRecovery)
			{
				final var checkpoints = PhantomNativeTravelActions.localWalkDestinations(current, destination, _navigation.policy().maximumLocalStraightDistance());
				if (journey.localApproach >= checkpoints.size()) { fail(profileId, journey, "travel.no_local_approach", Disposition.ROUTE_UNUSABLE); return false; }
				requestDestination = checkpoints.get(journey.localApproach);
			}
			final var request = new PhantomNavigationRequest(profileId, current, requestDestination, now, journey.deadline, 100_000, threats);
			journey.submittedSearch = null;
			if (ordinarySearch)
			{
				final var probe = new PhantomFarmRouteBackoff.Probe(site, current, requestDestination);
				if (_farmSearches.blocked(probe, now))
				{
					journey.heldSearch = probe;
					journey.heldWalkDestination = destination;
					journey.attempt.suppressedSearch = requestDestination;
					journey.reason = "travel.farm_search_backoff";
					return false;
				}
				journey.attempt.suppressedSearch = null;
				journey.submittedSearch = probe;
			}
			final var submission = journey.currentAnchorReturn && (!dryRecovery || journey.landedSwim) ? _navigation.submitSoloWalk(request, false, Set.of(), journey.attempt.blockedEdges)
				: journey.nativeBudget != null ? _navigation.submitSoloWalk(request, true, journey.nativeBudget.visited, journey.attempt.blockedEdges)
				// The stored anchor label does not make a displaced native return a local corridor.
				: journey.plan == null
				? _navigation.submitTravelPlan(request, PhantomNativeTravelActions.snapshot(player, _travel.legs(), Math.max(0, (_navigation.policy().maximumAttemptDurationNanos() - (now - journey.attempt.startedNanos)) / 1_000_000), journey.attempt.blockedEdges))
				: _navigation.submitSoloWalk(request, false, Set.of(), journey.attempt.blockedEdges);
			journey.requestId = submission.requestId();
			if (submission.immediateResult() != null)
			{
				_navigation.consume(journey.requestId);
				if (!terminalResult(profileId, journey, submission.immediateResult(), stamp)) { return false; }
			}
			else if ((submission.status() != PhantomNavigationService.SubmissionStatus.ACCEPTED) || (journey.requestId <= 0))
			{
				journey.reason = "travel.navigation_protocol_failure";
				fail(profileId, journey, journey.reason, Disposition.PROTOCOL_VIOLATION);
				return false;
			}
		}
		if (journey.waypoints == null)
		{
			final var result = _navigation.consume(journey.requestId).orElse(null);
			if (result == null)
			{
				if ((now >= journey.deadline) || _navigation.find(journey.requestId).isEmpty())
				{
					journey.reason = now >= journey.deadline ? "travel.navigation_deadline_expired" : "travel.navigation_terminal_missing";
					if (now >= journey.deadline) { retryOrFail(profileId, journey, Disposition.TRANSIENT_SERVICE); }
					else { fail(profileId, journey, journey.reason, Disposition.PROTOCOL_VIOLATION); }
					return false;
				}
				journey.reason = "travel.navigation_pending";
				hold(profileId);
				return false;
			}
			if (!terminalResult(profileId, journey, result, stamp)) { return false; }
		}
		if (journey.nativeBudget != null && current.equals(journey.route.destination()) && !current.equals(destination))
		{
			if ((dryRecovery || journey.ordinaryWalkEpisodes()) && !current.equals(journey.recoveryReceipt))
			{
				journey.reason = "travel.recovery_checkpoint_pending";
				return false;
			}
			stopOwnedMove(journey, player);
			closeRoutePhase(profileId, journey);
			if (!journey.nativeBudget.checkpoint(current, now)) { fail(profileId, journey, "travel.native_episode_checkpoint_rejected", Disposition.ROUTE_UNUSABLE); return false; }
			clearRoute(profileId, journey);
			journey.attempt.phaseSince = journey.nativeBudget.episodeSince;
			journey.phaseClosed = false;
			journey.localApproach = 0;
			journey.reason = "travel.native_episode_arrived";
			if (dryRecovery) { recordDryRecovery(profileId, journey, "CHECKPOINT", current.toString()); }
			return false;
		}
		if (journey.plan != null && journey.stageIndex < journey.plan.stages().size() && !destination.equals(journey.plan.stages().get(journey.stageIndex).to())) { return false; }
		journey.index = nextWalkWaypoint(current, journey.waypoints, journey.index, _navigation.arrivalRadius(),
			(from, to) -> !dryRecovery || !journey.attempt.blockedEdges.contains(new PhantomWalkCorridorPlanner.Edge(from, to))
				&& unsafeSegment(from, to, false, 0) == null);
		if (journey.trackedIndex != journey.index)
		{
			_navigation.progressTracker().cancel(profileId, journey.requestId);
			final var waypoint = journey.waypoints.get(journey.index);
			final var leg = new PhantomNavigationRoute(journey.route.mode(), current, waypoint, List.of(waypoint), journey.route.geodataCapability(), now, false, 1, _navigation.policy().maximumRouteDistance());
			final var begun = _navigation.progressTracker().begin(profileId, journey.requestId, leg, now);
			if (begun.status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationProgressTracker.BeginStatus.TRACKING)
			{
				journey.reason = "travel.navigation_progress_busy";
				retryOrFail(profileId, journey, Disposition.TRANSIENT_SERVICE);
				return false;
			}
			journey.trackedIndex = journey.index;
		}
		final var progress = _navigation.progressTracker().observe(profileId, journey.requestId, current, now).status();
		if ((progress == ProgressStatus.PROGRESS) || (progress == ProgressStatus.ARRIVED)) { _terminalReasons.remove(profileId); }
		if (progress == ProgressStatus.ARRIVED) { journey.trackedIndex = -1; }
		if ((progress == ProgressStatus.STUCK) || (progress == ProgressStatus.TIMEOUT) || (progress == ProgressStatus.STALE))
		{
			journey.reason = "travel.native_progress_" + progress.name().toLowerCase(java.util.Locale.ROOT);
			if ((progress == ProgressStatus.STALE) && (_navigation.snapshot().state() != PhantomNavigationService.ServiceState.RUNNING)) { retryOrFail(profileId, journey, Disposition.TRANSIENT_SERVICE); }
			else if (progress == ProgressStatus.STALE) { fail(profileId, journey, journey.reason, Disposition.PROTOCOL_VIOLATION); }
			else if (progress == ProgressStatus.STUCK && journey.nativeLeg != null)
			{
				final var edge = journey.nativeLeg.edge();
				replanEdge(profileId, player, journey, edge.from(), edge.to(), journey.reason);
			}
			else { retryOrFail(profileId, journey, Disposition.ROUTE_UNUSABLE); }
			return false;
		}
		if (!player.isMoving())
		{
			if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
			final var waypoint = journey.waypoints.get(journey.index);
			if (guardThreats && !safeIdlePveSegment(player, waypoint)) { replanEdge(profileId, player, journey, current, waypoint, "travel.native_pve_threat"); return false; }
			final boolean waterExit = player.isInsideZone(ZoneId.WATER) || (ZoneManager.getInstance().getZone(current.x(), current.y(), current.z(), WaterZone.class) != null);
			final String unsafe = unsafeSegment(current, waypoint, waterExit, radius, witness -> journey.attempt.segmentWitness = witness);
			if (unsafe != null)
			{
				replanEdge(profileId, player, journey, current, waypoint, unsafe);
				return false;
			}
			player.getAI().setIntention(Intention.MOVE_TO, new Location(waypoint.x(), waypoint.y(), waypoint.z()));
			journey.moveIssued = player.isMoving();
			journey.moveCurrent = player.captureNativeMoveIdentity();
			journey.moveX = player.getXdestination();
			journey.moveY = player.getYdestination();
			journey.moveZ = player.getZdestination();
			journey.moveInstance = player.getInstanceId();
			journey.nativeLeg = null;
			if (journey.moveIssued)
			{
				// Native swimming may shorten a leg; validate the actual native destination too.
				final var issued = new PhantomNavigationPoint(journey.moveX, journey.moveY, player.getZdestination(), player.getInstanceId());
				final var recorder = org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance();
				if (recorder.isRecording())
				{
					recorder.record(profileId, "VISIBLE_NATIVE_MOVE", current.toString(), waypoint.toString(), "issued=" + issued + ";goal=" + journey.arrivalPoint, player.getObjectId(), journey.epoch, journey.revision);
				}
				final String actualUnsafe = unsafeSegment(current, issued, waterExit, waterExit ? 100 : radius, witness -> journey.attempt.segmentWitness = witness);
				if (actualUnsafe != null)
				{
					stopOwnedMove(journey, player);
					replanEdge(profileId, player, journey, current, issued, actualUnsafe);
					return false;
				}
				final var edge = new PhantomWalkCorridorPlanner.Edge(current, issued);
				final var arrived = player.nativeStraightMoveArrival(issued.x(), issued.y(), issued.z());
				journey.nativeLeg = new NativeLeg(edge, arrived, new java.util.concurrent.atomic.AtomicBoolean());
			}
		}
		journey.reason = player.isMoving() ? "travel.native_walking" : "travel.native_move_rejected";
		if (player.isMoving())
		{
			publishRoutePhase(profileId, journey, player);
			hold(profileId);
		}
		else { retryOrFail(profileId, journey, Disposition.NATIVE_ACTION_REJECTED); }
		return false;
	}

	static int nextWalkWaypoint(PhantomNavigationPoint current, List<PhantomNavigationPoint> waypoints, int index, int radius,
		java.util.function.BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> shortcut)
	{
		while (index < waypoints.size() - 1 && current.distanceTo(waypoints.get(index)) <= radius && shortcut.test(current, waypoints.get(index + 1))) { index++; }
		return index;
	}

	private void publishNativeArrival(long profileId, Journey journey, Player player)
	{
		final var leg = journey.nativeLeg;
		final var memory = _navigation.routeLearning();
		if (leg == null || leg.arrived() == null || (memory == null && journey.dryRecovery == null && !journey.ordinaryWalkEpisodes()) || !leg.arrived().getAsBoolean()) { return; }
		journey.nativeLeg = null;
		final var current = PhantomNativeTravelActions.point(player);
		final var from = leg.edge().from();
		final var to = leg.edge().to();
		final var owner = journey.attempt.nativeOwner;
		final int floor = GeoEngine.getInstance().getHeight(from.x(), from.y(), from.z());
		final boolean grounded = floor == from.z();
		final boolean fromReturn = !grounded && from.equals(journey.returnLanding) && knownDryReturnLanding(from);
		final boolean stageLanding = journey.ordinaryWalkEpisodes() && (long) from.z() - floor == 5;
		if (to.instanceId() == 0 && current.equals(to) && !player.isTeleporting()
			&& owner != null && owner == player.getNativeWorkOwner() && owner.isCurrent() && owner.epoch() == journey.epoch
			&& _journeys.get(profileId) == journey && currentJourney(profileId, journey)
			&& (grounded || fromReturn || stageLanding)
			&& unsafeSegment(from, to, false, 0) == null && leg.published().compareAndSet(false, true))
		{
			if (journey.route != null && to.equals(journey.route.destination())
				&& (journey.ordinaryWalkEpisodes() || (journey.dryRecovery != null && currentDryRecovery(profileId, journey))))
			{
				journey.recoveryReceipt = to;
			}
			// The first leg from a native landing can settle the journey. Keep exact grounded learning.
			if (memory == null || !grounded) { return; }
			memory.arrived(leg.edge());
			synchronized (_attempts)
			{
				final var observation = _journeyObservations.get(profileId);
				if (observation != null && _journeys.get(profileId) == journey && _attempts.get(profileId) == journey.attempt
					&& observationIdentity(journey.attempt) != null)
				{
					observation.walked(leg.edge());
				}
			}
		}
	}

	private void beginJourneyObservation(long profileId, Attempt attempt, PhantomTopologyAnchor finalAnchor)
	{
		if (finalAnchor == null || _navigation.routeLearning() == null) { return; }
		final var identity = observationIdentity(attempt);
		if (identity == null) { return; }
		final var existing = _journeyObservations.get(profileId);
		if (existing != null && existing.matches(identity, finalAnchor.id())) { return; }
		if (existing == null && _journeyObservations.size() >= _navigation.policy().maximumTrackedProfiles()) { return; }
		final var from = PhantomNativeTravelActions.point(attempt.player);
		final var target = finalAnchor.point();
		if (from.instanceId() != 0 || target.instanceId() != 0) { return; }
		_journeyObservations.put(profileId, new PhantomJourneyObservation(identity, from,
			new PhantomNavigationPoint(target.x(), target.y(), target.z(), target.instanceId()), finalAnchor.id(), _clock.getAsLong()));
	}

	private void observeTransport(long profileId, Journey journey)
	{
		synchronized (_attempts)
		{
			final var observation = _journeyObservations.get(profileId);
			if (observation != null && _journeys.get(profileId) == journey && _attempts.get(profileId) == journey.attempt
				&& observationIdentity(journey.attempt) != null)
			{
				observation.transported();
			}
		}
	}

	private static PhantomJourneyObservation.Identity observationIdentity(Attempt attempt)
	{
		final var player = attempt.player;
		final var owner = player.getNativeWorkOwner();
		final int objectId = player.getObjectId();
		return owner != null && owner == attempt.nativeOwner && owner.player() == player && owner.isCurrent() && owner.epoch() == attempt.epoch
			&& World.getInstance().getPlayer(objectId) == player && !player.isDead() && !player.isInParty() && player.hasHeadlessOutboundSession()
			? new PhantomJourneyObservation.Identity(objectId, attempt.epoch, attempt.goal.goalId(), attempt.goal.revision()) : null;
	}

	/** Inspect stock native legs; water is allowed only before the first dry cell of an exit. */
	public static List<PhantomNavigationRequest.PveThreat> pveThreats(PhantomNavigationPoint from, int level, List<org.l2jmobius.gameserver.model.actor.instance.Monster> visible)
	{
		final var result = visible.stream().filter(monster -> !monster.isAlikeDead() && monster.getInstanceId() == from.instanceId()
			&& (monster.isAggressive() || monster.getLevel() > level + 3)
			&& Math.hypot(Math.hypot((long) monster.getX() - from.x(), (long) monster.getY() - from.y()), (long) monster.getZ() - from.z()) <= 3500)
			.limit(65).map(monster -> new PhantomNavigationRequest.PveThreat(new PhantomNavigationPoint(monster.getX(), monster.getY(), monster.getZ(), monster.getInstanceId()), Math.max(300, monster.getAggroRange() + 100))).toList();
		if (result.size() > 64) { throw new IllegalArgumentException("Too many observed native threats."); }
		return result;
	}
	private static boolean safeIdlePveSegment(Player player, PhantomNavigationPoint to)
	{
		return PhantomNativeTravelActions.safePveWalkSegment(player, to.x(), to.y(), to.z(), List.copyOf(World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.actor.instance.Monster.class, 3500)), java.util.Set.of());
	}
	private static String unsafeSegment(PhantomNavigationPoint from, PhantomNavigationPoint to, boolean waterExit, int destinationTolerance)
	{
		return unsafeSegment(from, to, waterExit, destinationTolerance, null);
	}

	private static String unsafeSegment(PhantomNavigationPoint from, PhantomNavigationPoint to, boolean waterExit, int destinationTolerance, java.util.function.Consumer<String> witness)
	{
		final var geo = GeoEngine.getInstance();
		if ((from.instanceId() != to.instanceId()) || !geo.hasGeo(to.x(), to.y())) { return "travel.native_segment_missing_geodata"; }
		final boolean targetWater = ZoneManager.getInstance().getZone(to.x(), to.y(), to.z(), WaterZone.class) != null;
		final int targetZ = geo.getHeight(to.x(), to.y(), to.z());
		if ((targetZ != geo.getHeight(to.x(), to.y(), targetZ)) || ((!waterExit || !targetWater) && (Math.abs((long) targetZ - to.z()) > destinationTolerance))) { return "travel.native_segment_height_unproven"; }
		final var cells = new GridLineIterator2D(GeoEngine.getGeoX(from.x()), GeoEngine.getGeoY(from.y()), GeoEngine.getGeoX(to.x()), GeoEngine.getGeoY(to.y()));
		boolean dry = !waterExit;
		int z = from.z();
		while (cells.next())
		{
			final int x = GeoEngine.getWorldX(cells.x());
			final int y = GeoEngine.getWorldY(cells.y());
			if (!geo.hasGeo(x, y)) { return "travel.native_segment_missing_geodata"; }
			z = geo.getHeight(x, y, z);
			if (z != geo.getHeight(x, y, z)) { return "travel.native_segment_height_unproven"; }
			if (ZoneManager.getInstance().getZone(x, y, z, WaterZone.class) != null)
			{
				if (dry)
				{
					if (witness != null) { witness.accept("from=" + from + ";to=" + to + ";cell=" + x + "," + y + "," + z + ";waterExit=" + waterExit); }
					return "travel.native_segment_water_entry";
				}
			}
			else { dry = true; }
		}
		if (!geo.canMoveToTarget(from.x(),from.y(),from.z(),to.x(),to.y(),to.z(),from.instanceId()))
		{
			if (witness != null) { witness.accept("from="+from+";to="+to+";native=NSWE_HEIGHT_DOOR"); }
			return "travel.native_segment_obstructed";
		}
		return null;
	}

	private boolean terminalResult(long profileId, Journey journey, PhantomNavigationResult result, Stamp stamp)
	{
		if (!currentJourney(profileId, journey)) { remove(profileId, journey); return false; }
		if ((result.profileId() != profileId) || (result.requestId() != journey.requestId))
		{
			journey.reason = "travel.navigation_protocol_failure";
			fail(profileId, journey, journey.reason, Disposition.PROTOCOL_VIOLATION);
			return false;
		}
		if (journey.dryRecovery != null)
		{
			if (!currentDryRecovery(profileId, journey)) { remove(profileId, journey); return false; }
			final var probe = journey.submittedSearch;
			final var origin = PhantomNativeTravelActions.point(journey.player);
			if (probe == null || !probe.origin().equals(origin) || !probe.destination().equals(journey.arrivalPoint)
				|| (result.route() != null && !result.route().origin().equals(origin)))
			{
				journey.reason = "travel.recovery_search_origin_changed";
				retryOrFail(profileId, journey, Disposition.TRANSIENT_SERVICE);
				return false;
			}
		}
		journey.attempt.proof = result.proof();
		if ((result.route() == null) || (result.route().mode() == PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA))
		{
			journey.reason = result.status() == PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED ? "travel.search_limit" : result.status() == PhantomNavigationResult.Status.NO_PATH ? "travel.no_route_within_budget" : "travel.navigation_" + result.status().name().toLowerCase(java.util.Locale.ROOT);
			final var proof = result.proof();
			if (journey.submittedSearch != null && PhantomFarmRouteBackoff.indeterminate(proof.kind())
				&& navigationFailureDisposition(result.status()) == Disposition.INDETERMINATE_ROUTE)
			{
				final var site = journey.submittedSearch.site();
				if (journey.submittedSearch.origin().equals(proof.origin()) && journey.submittedSearch.destination().equals(proof.submittedDestination()))
				{
					org.l2jmobius.gameserver.phantoms.navigation.HumanRouteService.observeFarmRefusal(profileId, journey.player,
						journey.epoch, result.requestId(), site.geodata(), site.zones(), proof);
				}
				if (beginOrdinaryWalkEpisodes(profileId, journey, proof)
					|| beginDryRecovery(profileId, journey, proof) || (journey.dryRecovery == null && retryVerifiedCheckpoint(profileId, journey, proof))
					|| beginSwimRecovery(profileId, journey, stamp)) { return false; }
				// Let the historical owner choose a different verified approach or wait.
				// Another checkpoint from this same origin would start a new search episode.
				journey.attempt.indeterminateSearch = journey.submittedSearch;
				fail(profileId, journey, journey.reason, Disposition.INDETERMINATE_ROUTE);
				return false;
			}
			if (journey.submittedSearch != null && navigationFailureDisposition(result.status()) == Disposition.INDETERMINATE_ROUTE
				&& beginOrdinaryWalkEpisodes(profileId, journey, proof)) { return false; }
			if (result.status() == PhantomNavigationResult.Status.ROUTE_OBSTRUCTED && proof.failedSegment() == 0
				&& proof.origin() != null && proof.failedPoint() != null
				&& !journey.attempt.blockedEdges.contains(new PhantomWalkCorridorPlanner.Edge(proof.origin(), proof.failedPoint())))
			{
				replanEdge(profileId, journey.player, journey, proof.origin(), proof.failedPoint(), journey.reason);
				return false;
			}
		if (journey.currentAnchorReturn && journey.dryRecovery == null && Set.of(PhantomNavigationResult.Status.NO_PATH, PhantomNavigationResult.Status.ROUTE_OBSTRUCTED, PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED).contains(result.status()))
			{
			final var checkpoints = PhantomNativeTravelActions.localWalkDestinations(PhantomNativeTravelActions.point(journey.player), journey.arrivalPoint, _navigation.policy().maximumLocalStraightDistance());
			if (++journey.localApproach < checkpoints.size())
				{
					clearRoute(profileId, journey);
					journey.attempt.retryAtNanos = _clock.getAsLong() + _navigation.policy().pathfindingCooldownMillis() * 1_000_000L;
					journey.reason = "travel.local_checkpoint_retry";
					return false;
				}
			}
			clearRoute(profileId, journey);
			final var disposition = navigationFailureDisposition(result.status());
			if (disposition == Disposition.TRANSIENT_SERVICE) { retryOrFail(profileId, journey, disposition); }
			else { fail(profileId, journey, journey.reason, disposition); }
			return false;
		}
		if (journey.nativeBudget != null && !journey.nativeBudget.admit(result.route(), _clock.getAsLong()))
		{
			fail(profileId, journey, "travel.native_episode_route_budget", Disposition.ROUTE_UNUSABLE);
			return false;
		}
		final var planned = _navigation.consumeTravelPlan(journey.requestId).orElse(null);
		if (planned != null)
		{
			if (!planned.stages().getLast().to().equals(journey.arrivalPoint) || !planned.stages().getFirst().from().equals(result.route().origin())) { fail(profileId, journey, "travel.navigation_plan_identity_failure", Disposition.PROTOCOL_VIOLATION); return false; }
			journey.plan = planned; journey.stageIndex = 0;
		}
		if (journey.currentAnchorReturn)
		{
			var point = result.route().origin(); double distance = 0;
			for (var waypoint : result.route().waypoints()) { distance += point.distanceTo(waypoint); point = waypoint; }
			journey.returnWaypoints += result.route().waypoints().size();
			journey.returnDistance += distance;
			if (journey.returnWaypoints > _navigation.policy().maximumWaypoints() || journey.returnDistance > _navigation.policy().maximumRouteDistance())
			{
				fail(profileId, journey, "travel.navigation_route_budget_exceeded", Disposition.ROUTE_UNUSABLE);
				return false;
			}
		}
		journey.route = result.route();
		journey.waypoints = result.route().waypoints();
		if (journey.dryRecovery != null) { recordDryRecovery(profileId, journey, "RELEASED", result.proof().diagnostic()); }
		return true;
	}

	/** A failed itinerary may release one local corridor at a time from the actual Player position. */
	private boolean beginOrdinaryWalkEpisodes(long profileId, Journey journey, PhantomNavigationResult.BudgetProof proof)
	{
		if (!"TRAVEL".equals(proof.requestKind()) || journey.plan != null || journey.nativeBudget != null || journey.pveSafeWalk
			|| journey.player.isInParty() || !_permitsOrdinary.test(profileId) || !PhantomNativeTravelActions.ordinary(journey.player)) { return false; }
		final var origin = PhantomNativeTravelActions.point(journey.player);
		final var destination = journey.arrivalPoint;
		final double distance = origin.distanceTo(destination);
		final double speed = journey.player.getMoveSpeed();
		final var policy = _navigation.policy();
		if (!origin.equals(proof.origin()) || !destination.equals(proof.submittedDestination()) || origin.instanceId() != 0 || destination.instanceId() != 0
			|| distance <= PhantomNativeTravelActions.LOCAL_WALK_DISTANCE || distance > policy.maximumRouteDistance()
			|| !PhantomFarmRouteBackoff.indeterminate(proof.kind()) && distance <= policy.maximumLocalStraightDistance()
			|| !Double.isFinite(speed) || speed <= 0) { return false; }

		final long now = _clock.getAsLong();
		final long remaining = policy.maximumAttemptDurationNanos() - (now - journey.attempt.startedNanos) - policy.pathfindingCooldownNanos();
		if (distance / speed * 1_000_000_000L > remaining) { return false; }
		journey.nativeBudget = new NativeWalkBudget(origin, destination, now, speed, policy);
		clearRoute(profileId, journey);
		journey.attempt.phaseSince = now;
		journey.attempt.retryAtNanos = now + policy.pathfindingCooldownNanos();
		journey.reason = "travel.native_walk_episode_pending";
		hold(profileId);
		return true;
	}

	private boolean beginDryRecovery(long profileId, Journey journey, PhantomNavigationResult.BudgetProof proof)
	{
		final var rejected = journey.submittedSearch;
		final var player = journey.player;
		if (!journey.currentAnchorReturn || journey.attempt.dryRecovery != null || journey.nativeBudget == null
			|| journey.nativeBudget.expired(_clock.getAsLong()) || !dryRecoveryRequestMatches(rejected, proof, journey.arrivalPoint)
			|| !rejected.origin().equals(PhantomNativeTravelActions.point(player)) || !dryRecoveryAuthority(profileId, journey)
			|| !rejected.site().equals(searchSite(profileId, player, journey.epoch)) || player.isMoving()
			|| player.isInsideZone(ZoneId.WATER) || ZoneManager.getInstance().getZone(rejected.origin().x(), rejected.origin().y(), rejected.origin().z(), WaterZone.class) != null)
		{
			return false;
		}
		final var recovery = new DryRecovery(rejected, proof);
		journey.attempt.dryRecovery = recovery;
		journey.dryRecovery = recovery;
		clearRoute(profileId, journey);
		journey.attempt.retryAtNanos = _clock.getAsLong() + _navigation.policy().pathfindingCooldownNanos();
		journey.reason = "travel.recovery_walk_pending";
		recordDryRecovery(profileId, journey, "STARTED", proof.diagnostic());
		return true;
	}

	static boolean dryRecoveryRequestMatches(PhantomFarmRouteBackoff.Probe rejected, PhantomNavigationResult.BudgetProof proof, PhantomNavigationPoint arrival)
	{
		// A refused intermediate corridor may start the same bounded recovery to the journey's arrival.
		return rejected.destination().equals(arrival) || (proof.kind() == PhantomNavigationResult.LimitKind.SEARCH_LIMIT
			&& "CORRIDOR".equals(proof.requestKind()) && rejected.origin().equals(proof.origin())
			&& rejected.destination().equals(proof.destination()) && rejected.destination().equals(proof.submittedDestination()));
	}

	private boolean currentDryRecovery(long profileId, Journey journey)
	{
		return dryRecoveryAuthority(profileId, journey) && journey.dryRecovery == journey.attempt.dryRecovery
			&& journey.dryRecovery.rejected().site().equals(searchSite(profileId, journey.player, journey.epoch));
	}

	private boolean beginSwimRecovery(long profileId, Journey journey, Stamp stamp)
	{
		final var player = journey.player;
		final var rejected = journey.submittedSearch;
		if (journey.dryRecovery == null || journey.nativeBudget == null || journey.swim != null || !currentDryRecovery(profileId, journey)
			|| !org.l2jmobius.gameserver.config.GeneralConfig.ALLOW_WATER || player.isMoving() || player.isInWater()
			|| player.isInsideZone(ZoneId.WATER) || player.isInsideZone(ZoneId.CASTLE)
			|| player.isMovementDisabled() || rejected == null || !rejected.origin().equals(PhantomNativeTravelActions.point(player))
			|| swimSpeed(player) <= 0 || breathMillis(player) <= 0) { return false; }
		if (stamp == null || _swimTried.putIfAbsent(profileId, journey.epoch) != null) { return false; }
		final long now = _clock.getAsLong();
		journey.swim = new SwimRecovery(rejected, journey.attempt.proof, stamp,
			now + _navigation.policy().pathfindingCooldownNanos(),
			Math.min(journey.nativeBudget.deadline, Math.min(journey.nativeBudget.episodeSince + journey.nativeBudget.episodeNanos,
				journey.attempt.startedNanos + _navigation.policy().maximumAttemptDurationNanos())));
		clearRoute(profileId, journey);
		journey.reason = "travel.swim_pending";
		return true;
	}

	private boolean advanceSwim(long profileId, Journey journey, PhantomNavigationPoint current, Stamp stamp)
	{
		final var swim = journey.swim;
		if (swim == null || !swim.advancing.compareAndSet(false, true)) { return false; }
		try
		{
			final var player = journey.player;
			final long now = _clock.getAsLong();
			if (journey.swim != swim || !dryRecoveryAuthority(profileId, journey)
				|| !swim.stamp.equals(stamp) || now >= swim.deadline
				|| now < journey.attempt.startedNanos || !org.l2jmobius.gameserver.config.GeneralConfig.ALLOW_WATER
				|| player.isMovementDisabled() || player.isInsideZone(ZoneId.CASTLE))
			{
				return failSwim(profileId, journey, swim, "travel.swim_authority_or_deadline");
			}
			if (now < swim.submitAt) { return false; }
			final double speed = swimSpeed(player);
			final long available = Math.min(breathMillis(player), (swim.deadline - now) / 1_000_000L);
			final long remaining = swim.wetSince == 0 ? available : Math.min((swim.deadline - now) / 1_000_000L,
				Math.min(swim.initialBreath, breathMillis(player)) - (now - swim.wetSince) / 1_000_000L);
			if (!swim.emergency && !currentDryRecovery(profileId, journey))
			{ return interruptSwim(profileId, journey, swim, current, now, remaining, speed, "travel.swim_geometry_changed"); }
			if (swim.plan == null)
			{
				if (!current.equals(swim.rejected.origin()) || player.isMoving() || player.isInWater() || player.isInsideZone(ZoneId.WATER))
				{ return failSwim(profileId, journey, swim, "travel.swim_origin_changed"); }
				final PhantomNavigationResult result;
				if (journey.requestId == 0)
				{
					final List<PhantomNavigationRequest.PveThreat> threats;
					try { threats = swimThreats(player, current); }
					catch (IllegalArgumentException invalidSnapshot)
					{ return failSwim(profileId, journey, swim, "travel.swim_threat_snapshot_unavailable"); }
					final var request = new PhantomNavigationRequest(profileId, current, journey.arrivalPoint, now,
						Math.min(swim.deadline, now + _navigation.policy().defaultRequestDeadlineNanos()), _navigation.policy().maximumRouteDistance(), threats);
					final int safeFall = player.getBaseTemplate().getSafeFallHeight();
					final var geometry = shoreGeometry(plan -> plan.legs().stream().allMatch(leg ->
						request.permitsWalkSegment(leg.from(), leg.to()) && (leg.kind() != PhantomFarmSwimRoute.Kind.ENTER_WATER
							|| (long) leg.from().z() - leg.to().z() <= safeFall)));
					final var accepted = _navigation.submitFarmSwim(request, geometry, speed, available);
					journey.requestId = accepted.requestId();
					result = accepted.immediateResult();
					if (result != null) { _navigation.consume(journey.requestId); }
				}
				else { result = _navigation.consume(journey.requestId).orElse(null); }
				if (result == null)
				{
					if (_navigation.find(journey.requestId).isEmpty()) { return failSwim(profileId, journey, swim, "travel.swim_search_missing"); }
					hold(profileId);
					return false;
				}
				if (result.swim() == null || result.requestId() != journey.requestId || result.profileId() != profileId
					|| result.swim().legs().isEmpty() || !current.equals(result.swim().legs().getFirst().from())
					|| result.swim().requiredMillis(0, speed) > available || !journey.nativeBudget.canCrossShore(result.swim(), now))
				{ return failSwim(profileId, journey, swim, "travel.swim_search_" + result.status().name().toLowerCase(java.util.Locale.ROOT)); }
				swim.plan = result.swim();
				recordSwim(profileId, journey, "RELEASED", "request=" + journey.requestId + ";legs=" + swim.plan.legs());
			}
			final var leg = swim.plan.legs().get(swim.index);
			final long pendingTravel = swim.arrived != null && leg.kind() != PhantomFarmSwimRoute.Kind.WALK_TO_SHORE && speed > 0
				? (long) Math.ceil(current.distanceTo(leg.to()) * 1000 / speed) : 0;
			final int firstRemaining = swim.index + (swim.arrived == null ? 0 : 1);
			final long budget = swim.emergency ? swim.plan.egressMillis(firstRemaining, speed) : swim.plan.requiredMillis(firstRemaining, speed);
			if (remaining - pendingTravel < budget)
			{ return interruptSwim(profileId, journey, swim, current, now, remaining, speed, "travel.swim_breath_reserve"); }
			if (swim.arrived != null)
			{
				if (!swim.arrived.getAsBoolean())
				{
					final double distance = current.distanceTo(leg.to());
					if (distance + 1 < swim.distanceRemaining)
					{
						swim.distanceRemaining = distance;
						swim.lastProgress = now;
					}
					if (!player.isMoving() || now >= swim.legDeadline || !ownedSwimMove(journey, player)
						|| now - swim.lastProgress >= _navigation.policy().stuckWindowNanos())
					{ return interruptSwim(profileId, journey, swim, current, now, remaining, speed, "travel.swim_interrupted"); }
					hold(profileId);
					return false;
				}
				// The pulse's original XYZ can precede the native arrival write.
				final var arrivedAt = PhantomNativeTravelActions.point(player);
				final boolean wet = leg.kind() == PhantomFarmSwimRoute.Kind.ENTER_WATER || leg.kind() == PhantomFarmSwimRoute.Kind.SWIM;
				if (!arrivedAt.equals(leg.to()) || player.isMoving() || player.isInsideZone(ZoneId.WATER) != wet || player.isInWater() != wet)
				{
					// Stock ARRIVED revalidates the zone after setting movement's receipt.
					if (arrivedAt.equals(leg.to()) && now < swim.legDeadline) { return false; }
					return interruptSwim(profileId, journey, swim, arrivedAt, now, remaining, speed, "travel.swim_arrival_mismatch");
				}
				swim.arrived = null;
				journey.moveIssued = false;
				recordSwim(profileId, journey, leg.kind().name(), arrivedAt.toString());
				if (++swim.index < swim.plan.legs().size()) { hold(profileId); return false; }
				if (swim.emergency) { return failSwim(profileId, journey, swim, "travel.swim_recovered_to_bank"); }
				if (!journey.nativeBudget.crossedShore(swim.plan, arrivedAt, now))
				{ return failSwim(profileId, journey, swim, "travel.swim_journey_budget"); }
				journey.swim = null;
				journey.landedSwim = true;
				clearRoute(profileId, journey);
				journey.attempt.phaseSince = journey.nativeBudget.episodeSince;
				journey.phaseClosed = false;
				journey.reason = "travel.swim_landing_replan";
				hold(profileId);
				return false;
			}
			final boolean wet = leg.kind() == PhantomFarmSwimRoute.Kind.SWIM || leg.kind() == PhantomFarmSwimRoute.Kind.EXIT_WATER;
			if (!current.equals(leg.from()) || player.isMoving() || player.isInsideZone(ZoneId.WATER) != wet || player.isInWater() != wet
				|| leg.kind() == PhantomFarmSwimRoute.Kind.ENTER_WATER && (long) current.z() - leg.to().z() > player.getBaseTemplate().getSafeFallHeight()
				|| !swimLegSafe(player, leg))
			{ return interruptSwim(profileId, journey, swim, current, now, remaining, speed, "travel.swim_segment_obstructed"); }
			if (leg.kind() == PhantomFarmSwimRoute.Kind.ENTER_WATER)
			{
				if (swim.plan.legs().subList(swim.index + 1, swim.plan.legs().size()).stream().anyMatch(segment -> !swimLegSafe(player, segment)))
				{ return failSwim(profileId, journey, swim, "travel.swim_exit_no_longer_proven"); }
				swim.wetSince = now;
				swim.initialBreath = breathMillis(player);
			}
			return issueSwimMove(profileId, journey, swim, leg, now, speed);
		}
		finally { swim.advancing.set(false); }
	}

	private boolean issueSwimMove(long profileId, Journey journey, SwimRecovery swim, PhantomFarmSwimRoute.Leg leg, long now, double speed)
	{
		final var player = journey.player;
		final var to = leg.to();
		final double moveSpeed = Math.min(speed, player.getMoveSpeed());
		if (moveSpeed <= 0 || !Double.isFinite(moveSpeed)) { return failSwim(profileId, journey, swim, "travel.swim_speed"); }
		player.getAI().setIntention(Intention.MOVE_TO, new Location(to.x(), to.y(), to.z()));
		journey.moveIssued = player.isMoving();
		journey.moveX = player.getXdestination();
		journey.moveY = player.getYdestination();
		journey.moveZ = player.getZdestination();
		journey.moveInstance = player.getInstanceId();
		swim.moveCurrent = player.captureNativeMoveIdentity();
		swim.arrived = player.nativeStraightMoveArrival(to.x(), to.y(), to.z());
		if (!journey.moveIssued || swim.arrived == null)
		{ return failSwim(profileId, journey, swim, "travel.swim_native_dispatch_rejected"); }
		swim.legDeadline = now + (long) ((leg.from().distanceTo(to) / moveSpeed + 2) * 1_000_000_000L);
		swim.lastProgress = now;
		swim.distanceRemaining = leg.from().distanceTo(to);
		hold(profileId);
		return false;
	}

	private boolean interruptSwim(long profileId, Journey journey, SwimRecovery swim, PhantomNavigationPoint current,
		long now, long remaining, double speed, String reason)
	{
		final var player = journey.player;
		if (!swim.emergency && swim.plan != null && player.isInsideZone(ZoneId.WATER) && player.isInWater() && dryRecoveryAuthority(profileId, journey))
		{
			final var actual = PhantomNativeTravelActions.point(player);
			final boolean ongoing = ownedSwimMove(journey, player);
			if (actual.equals(current) && !player.isMovementDisabled() && (!player.isMoving() || ongoing))
			{
				final var escape = PhantomFarmSwimRoute.egress(swim.plan, swim.index, actual, shoreGeometry(), speed, remaining);
				if (escape != null && escape.legs().stream().allMatch(leg -> swimLegSafe(player, leg)))
				{
					final var next = escape.legs().getFirst();
					swim.emergency = true;
					swim.plan = escape;
					swim.index = 0;
					recordSwim(profileId, journey, "EMERGENCY_EXIT", reason);
					if (ongoing && swim.arrived != null && now < swim.legDeadline
						&& next.to().equals(new PhantomNavigationPoint(journey.moveX, journey.moveY, journey.moveZ, journey.moveInstance)))
					{
						hold(profileId);
						return false;
					}
					if (ongoing) { stopOwnedMove(journey, player); }
					swim.arrived = null;
					return issueSwimMove(profileId, journey, swim, next, now, speed);
				}
			}
		}
		return failSwim(profileId, journey, swim, reason);
	}

	private boolean failSwim(long profileId, Journey journey, SwimRecovery swim, String reason)
	{
		if (journey.swim != swim) { return false; }
		if (ownedSwimMove(journey, journey.player)) { stopOwnedMove(journey, journey.player); }
		journey.moveIssued = false;
		journey.swim = null;
		journey.attempt.indeterminateSearch = swim.rejected;
		journey.attempt.proof = swim.rejectedProof;
		recordSwim(profileId, journey, "STOPPED", reason);
		fail(profileId, journey, reason, Disposition.INDETERMINATE_ROUTE);
		return false;
	}

	private static boolean ownedSwimMove(Journey journey, Player player)
	{
		return journey.moveIssued && player.getNativeWorkOwner() == journey.attempt.nativeOwner
			&& journey.swim != null && journey.swim.moveCurrent != null && journey.swim.moveCurrent.getAsBoolean()
			&& player.getInstanceId() == journey.moveInstance && player.getXdestination() == journey.moveX
			&& player.getYdestination() == journey.moveY && player.getZdestination() == journey.moveZ
			&& player.hasAI() && player.getAI().getIntention() == Intention.MOVE_TO;
	}

	private static double swimSpeed(Player player)
	{
		return player.isRunning() ? player.getSwimRunSpeed() : player.getSwimWalkSpeed();
	}

	private static long breathMillis(Player player)
	{
		return Math.max(0, Math.min(60_000, (long) (600 * player.calcStat(Stat.BREATH, player.getBaseTemplate().getBaseBreath()))));
	}

	private static boolean swimLegSafe(Player player, PhantomFarmSwimRoute.Leg leg)
	{
		final var from = leg.from();
		final var to = leg.to();
		if (!PhantomFarmSwimRoute.valid(leg, shoreGeometry())
			|| leg.kind() == PhantomFarmSwimRoute.Kind.WALK_TO_SHORE && unsafeSegment(from, to, false, 0) != null) { return false; }
		try { return new PhantomNavigationRequest(1, from, to, 1, 2, 100_000, swimThreats(player, from)).permitsWalkSegment(from, to); }
		catch (IllegalArgumentException invalidSnapshot) { return false; }
	}

	private static List<PhantomNavigationRequest.PveThreat> swimThreats(Player player, PhantomNavigationPoint from)
	{
		final var monsters = List.copyOf(World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.actor.instance.Monster.class, 3500));
		return pveThreats(from, player.getLevel(), monsters);
	}

	private static PhantomFarmSwimRoute.Geometry shoreGeometry()
	{
		return shoreGeometry(plan -> true);
	}

	private static PhantomFarmSwimRoute.Geometry shoreGeometry(Predicate<PhantomFarmSwimRoute.Plan> permitsCrossing)
	{
		return new PhantomFarmSwimRoute.Geometry()
		{
			@Override public PhantomNavigationPoint ground(int x, int y, PhantomNavigationPoint surface)
			{
				final var geo = GeoEngine.getInstance();
				if (!geo.hasGeo(x, y)) { return null; }
				final int z = geo.getHeight(x, y, surface.z());
				return z == geo.getHeight(x, y, z) ? new PhantomNavigationPoint(GeoEngine.getWorldX(GeoEngine.getGeoX(x)),
					GeoEngine.getWorldY(GeoEngine.getGeoY(y)), z, surface.instanceId()) : null;
			}

			@Override public int water(PhantomNavigationPoint point)
			{
				final var zones = ZoneManager.getInstance();
				if (zones.loadedGeneration() < 0 || zones.getZone(point.x(), point.y(), point.z(), CastleZone.class) != null) { return -1; }
				final var water = zones.getZone(point.x(), point.y(), point.z(), WaterZone.class);
				return water == null ? 0 : water.getId();
			}

			@Override public boolean line(PhantomNavigationPoint from, PhantomNavigationPoint to)
			{
				return GeoEngine.getInstance().canMoveToTarget(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), from.instanceId());
			}

			@Override public boolean permitsCrossing(PhantomFarmSwimRoute.Plan plan) { return permitsCrossing.test(plan); }
		};
	}

	private static void recordSwim(long profileId, Journey journey, String phase, String detail)
	{
		final var recorder = org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance();
		if (recorder.isRecording())
		{
			recorder.record(profileId, "VISIBLE_FARM_SWIM", phase, journey.arrivalPoint.toString(), detail,
				journey.objectId, journey.epoch, journey.revision);
		}
	}

	private void adoptRecoveryWalk(long profileId, Journey journey, Stamp successor)
	{
		final var alternatives = _alternatives.get(profileId);
		if (alternatives == null || successor == null || journey.requestId != 0 || journey.dryRecovery != null) { return; }
		final var player = journey.player;
		final FarmWalkProposal proposal;
		synchronized (alternatives)
		{
			proposal = alternatives.proposal;
			if (proposal == null || !proposal.successor.equals(journey.goal)) { return; }
			alternatives.proposal = null;
		}
		final var result = proposal.result;
		if (result == null || result.route() == null || player.isMoving() || !PhantomNativeTravelActions.ordinary(player)
			|| (journey.nativeBudget == null && !result.route().destination().equals(journey.arrivalPoint))
			|| player.getNativeWorkOwner() != proposal.owner || !proposal.step.equals(journey.step)
			|| !proposal.probe.site().equals(searchSite(profileId, player, journey.epoch))
			|| !proposal.probe.origin().equals(PhantomNativeTravelActions.point(player))
			|| !proposal.probe.destination().equals(journey.arrivalPoint)
			|| !currentJourney(profileId, journey)) { return; }
		// The original request is consumed. Its immutable corridor can be used under this successor lease.
		journey.requestId = result.requestId();
		journey.submittedSearch = proposal.probe;
		journey.returnLanding = proposal.landingApproach ? proposal.probe.origin() : null;
		final var recovery = new DryRecovery(proposal.probe, result.proof());
		journey.attempt.dryRecovery = recovery;
		journey.dryRecovery = recovery;
		terminalResult(profileId, journey, result, successor);
	}

	private boolean dryRecoveryAuthority(long profileId, Journey journey)
	{
		return _journeys.get(profileId) == journey && _attempts.get(profileId) == journey.attempt && !journey.attempt.terminal
			&& _permitsOrdinary.test(profileId) && currentJourney(profileId, journey) && !journey.pveSafeWalk
			&& observationIdentity(journey.attempt) != null && PhantomNativeTravelActions.ordinary(journey.player)
			&& !journey.player.hasPendingOwnedStore() && !journey.player.isTeleporting();
	}

	private static void recordDryRecovery(long profileId, Journey journey, String phase, String detail)
	{
		final var recorder = org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance();
		if (recorder.isRecording())
		{
			recorder.record(profileId, "VISIBLE_FARM_DRY_RECOVERY", phase, journey.arrivalPoint.toString(), detail,
				journey.objectId, journey.epoch, journey.revision);
		}
	}

	private boolean retryVerifiedCheckpoint(long profileId, Journey journey, PhantomNavigationResult.BudgetProof proof)
	{
		if (!journey.currentAnchorReturn || journey.verifiedCheckpointRetried) { return false; }
		final var failed = journey.submittedSearch;
		final var origin = PhantomNativeTravelActions.point(journey.player);
		if (!origin.equals(failed.origin())) { return false; }
		final var checkpoints = PhantomNativeTravelActions.localWalkDestinations(origin, journey.arrivalPoint, _navigation.policy().maximumLocalStraightDistance());
		final long now = _clock.getAsLong();
		final var geometry = new L2jNavigationBackend();
		for (int index = journey.localApproach + 1; index < checkpoints.size(); index++)
		{
			final var checkpoint = checkpoints.get(index);
			final var probe = new PhantomFarmRouteBackoff.Probe(failed.site(), origin, checkpoint);
			if (_farmSearches.equivalent(failed, probe) || _farmSearches.blocked(probe, now)
				|| !geometry.canWalkDirect(origin, checkpoint)) { continue; }
			_farmSearches.failed(failed, proof.kind(), proof.queries(), now, searchDiagnostic("Farm checkpoint search", profileId, journey.attempt, journey.step.id(), journey.reason, Disposition.INDETERMINATE_ROUTE));
			journey.localApproach = index;
			journey.verifiedCheckpointRetried = true;
			clearRoute(profileId, journey);
			journey.attempt.retryAtNanos = now + _navigation.policy().pathfindingCooldownMillis() * 1_000_000L;
			journey.reason = "travel.local_checkpoint_retry";
			return true;
		}
		return false;
	}

	private void hold(long profileId)
	{
		_signals.submit(profileId, new PhantomRelevanceSignal("visible.farm.travel", signalSequence(), PhantomActivityState.NEARBY_PERCEPTIBLE, 5_000));
	}

	/** A completed bounded search cannot improve by replaying the exact same attempt under the same budget. */
	static Disposition navigationFailureDisposition(PhantomNavigationResult.Status status)
	{
		return switch (status)
		{
			case COOLDOWN, QUEUE_BACKPRESSURE, PROFILE_BUSY, BACKEND_FAILURE, SERVICE_NOT_RUNNING, CANCELLED, DEADLINE_EXPIRED -> Disposition.TRANSIENT_SERVICE;
			case ROUTE_BUDGET_EXCEEDED, NO_PATH -> Disposition.INDETERMINATE_ROUTE;
			default -> Disposition.ROUTE_UNUSABLE;
		};
	}

	private void retryOrFail(long profileId, Journey journey, Disposition disposition)
	{
		if ((_journeys.get(profileId) != journey) || (_attempts.get(profileId) != journey.attempt)) { return; }
		final var recorder = org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance();
		if (recorder.isRecording())
		{
			final Player player = journey.player;
			final var owner = player.getNativeWorkOwner();
			final boolean exact = owner != null && owner == journey.attempt.nativeOwner && owner.isCurrent() && owner.epoch() == journey.epoch;
			recorder.record(profileId, "VISIBLE_TRAVEL_RETRY", journey.reason, disposition.name(),
				"moving=" + player.isMoving() + ";issued=" + journey.moveIssued + ";owner=" + exact + ";world=" + (World.getInstance().getPlayer(player.getObjectId()) == player), journey.goalId, journey.epoch, journey.revision);
			recorder.record(profileId, "VISIBLE_RETRY_MOVE", player.getX() + "," + player.getY() + "," + player.getZ() + "," + player.getInstanceId(), journey.moveX + "," + journey.moveY + "," + journey.moveZ + "," + journey.moveInstance,
				"actual=" + player.getXdestination() + "," + player.getYdestination() + "," + player.getZdestination(), player.getObjectId(), journey.requestId, journey.index);
			recorder.record(profileId, "VISIBLE_RETRY_ROUTE", journey.route == null ? "NONE" : diagnosticPoint(journey.route.destination()), diagnosticPoint(journey.arrivalPoint),
				"tracked=" + journey.trackedIndex + ";stage=" + journey.stageIndex + ";attemptSince=" + journey.attempt.startedNanos, journey.attempt.failures, journey.deadline, _clock.getAsLong());
		}
		clearRoute(profileId, journey);
		_signals.withdraw(profileId, "visible.farm.travel", signalSequence());
		if (++journey.attempt.failures >= 3)
		{
			if (disposition == Disposition.TRANSIENT_SERVICE)
			{
				// Keep the existing three-attempt bound without converting service load into bad geometry.
				journey.attempt.transientExhausted = true;
			}
			else { fail(profileId, journey, journey.reason, disposition); }
		}
		else
		{
			journey.attempt.retryAtNanos = _clock.getAsLong() + 1_000_000_000L;
		}
	}
	private static String diagnosticPoint(PhantomNavigationPoint point)
	{
		return point == null ? "NONE" : point.x() + "," + point.y() + "," + point.z() + "," + point.instanceId();
	}
	/** A changed native edge invalidates the itinerary, not the farm area or its authoritative goal. */
	private void replanEdge(long profileId, Player player, Journey journey, PhantomNavigationPoint from, PhantomNavigationPoint to, String reason)
	{
		if (!currentJourney(profileId,journey)) { remove(profileId,journey); return; }
		final var issued = journey.nativeLeg;
		final var edge = issued != null && issued.edge().to().equals(to) ? issued.edge() : new PhantomWalkCorridorPlanner.Edge(from, to);
		journey.attempt.segmentWitness="epoch="+journey.epoch+";goal="+journey.goalId+";revision="+journey.revision+";from="+from+";to="+to+";reason="+reason;
		if (journey.attempt.blockedEdges.size() >= 16 && !journey.attempt.blockedEdges.contains(edge)) { fail(profileId,journey,"travel.edge_witness_limit",Disposition.ROUTE_UNUSABLE); return; }
		final boolean firstFailure = journey.attempt.blockedEdges.add(edge);
		if (journey.waypoints != null && journey.route != null)
		{
			final var plannedFrom = journey.index == 0 ? journey.route.origin() : journey.waypoints.get(journey.index - 1);
			final var planned = new PhantomWalkCorridorPlanner.Edge(plannedFrom, journey.waypoints.get(journey.index));
			if (journey.attempt.blockedEdges.size() >= 16 && !journey.attempt.blockedEdges.contains(planned))
			{
				fail(profileId, journey, "travel.edge_witness_limit", Disposition.ROUTE_UNUSABLE);
				return;
			}
			journey.attempt.blockedEdges.add(planned);
		}
		final var memory = _navigation.routeLearning();
		if (firstFailure && from.instanceId() == 0 && memory != null)
		{
			final boolean geometry = reason.equals("travel.native_segment_height_unproven") || reason.equals("travel.native_segment_missing_geodata")
				|| reason.equals("travel.native_segment_water_entry");
			// Native obstruction also includes doors and fences, so it must expire.
			memory.failed(edge, geometry ? org.l2jmobius.gameserver.phantoms.navigation.PhantomRouteLearning.Failure.GEOMETRY
				: org.l2jmobius.gameserver.phantoms.navigation.PhantomRouteLearning.Failure.TRANSIENT, System.currentTimeMillis());
		}
		stopOwnedMove(journey, player);
		journey.plan=null; journey.stageIndex=0; journey.reason=reason;
		retryOrFail(profileId,journey,Disposition.ROUTE_UNUSABLE);
	}

	private Attempt attempt(long profileId, PhantomGoal goal, Player player, long epoch)
	{
		final Attempt previous = _attempts.get(profileId);
		if ((previous != null) && previous.matches(goal, player, epoch) && !retryable(profileId, player, epoch, previous)) { return previous; }
		final var owner = player.getNativeWorkOwner();
		final var evidence = owner == null ? null : owner.evidence();
		synchronized (_attempts)
		{
			final Attempt current = _attempts.get(profileId);
			if ((current != null) && current.matches(goal, player, epoch) && !retryable(profileId, player, epoch, current)) { return current; }
			if ((current == null) && (_attempts.size() >= _navigation.policy().maximumTrackedProfiles()))
			{
				rememberFailure(profileId, "travel.attempt_capacity");
				return null;
			}
			clearRoutePhase(current);
			final var created = new Attempt(goal, player, epoch, _clock.getAsLong(), owner, evidence);
			_attempts.put(profileId, created);
			_terminalReasons.remove(profileId);
			return created;
		}
	}

	private boolean retryable(long profileId, Player player, long epoch, Attempt attempt)
	{
		return attempt.terminal && attempt.indeterminateSearch != null
			&& !searchHeld(attempt, searchSite(profileId, player, epoch), PhantomNativeTravelActions.point(player));
	}

	private void fail(long profileId, Journey journey, String reason, Disposition disposition)
	{
		if (!remove(profileId, journey)) { return; }
		final String witness = journey.step.id().startsWith("live.approach.") && (journey.arrivalPoint != null) ? localRouteWitness(journey.step.toAnchorId(), journey.arrivalPoint) : journey.step.id();
		fail(profileId, journey.attempt, witness, reason, disposition);
	}

	private static String localRouteWitness(String anchorId, PhantomNavigationPoint point)
	{
		return "live.approach." + anchorId + "@" + point.x() + ":" + point.y() + ":" + point.z();
	}

	private void fail(long profileId, Attempt attempt, String stepId, String reason, Disposition disposition)
	{
		final boolean logTerminal = reason.startsWith("travel.navigation_") || reason.equals("travel.search_limit") || reason.equals("travel.no_route_within_budget");
		final String diagnostic = logTerminal ? searchDiagnostic("Terminal farm route", profileId, attempt, stepId, reason, disposition) : "";
		final var outcome = switch (disposition)
		{
			case ROUTE_UNUSABLE -> PhantomJourneyObservation.Failure.ROUTE;
			case NATIVE_ACTION_REJECTED -> PhantomJourneyObservation.Failure.NATIVE;
			case INDETERMINATE_ROUTE -> PhantomJourneyObservation.Failure.BUDGET;
			default -> null;
		};
		final var memory = _navigation.routeLearning();
		final boolean observeFailure = outcome != null && memory != null && !attempt.player.hasPendingOwnedStore()
			&& _materialization.find(profileId).filter(lifetime -> lifetime.characterObjectId() == attempt.player.getObjectId()
				&& lifetime.materializedAtNanos() == attempt.epoch).isPresent()
			&& currentGoal(profileId, attempt.goal);
		synchronized (_attempts)
		{
			if ((_attempts.get(profileId) != attempt) || attempt.terminal) { return; }
			final long now = _clock.getAsLong();
			if (attempt.indeterminateSearch != null) { _farmSearches.failed(attempt.indeterminateSearch, attempt.proof.kind(), attempt.proof.queries(), now, diagnostic); }
			final var recovery = attempt.dryRecovery;
			if (recovery != null && !recovery.rejected().equals(attempt.indeterminateSearch))
			{
				_farmSearches.failed(recovery.rejected(), recovery.proof().kind(), recovery.proof().queries(), now, diagnostic);
			}
			attempt.terminal = true;
			clearRoutePhase(attempt);
			rememberFailure(profileId, reason, disposition, attempt);
			final var observation = _journeyObservations.get(profileId);
			if (observeFailure && observation != null)
			{
				final var identity = observationIdentity(attempt);
				if (identity != null) { memory.recordFailedJourney(observation, identity, outcome, _clock.getAsLong()); }
			}
		}
		if (attempt.indeterminateSearch == null && logTerminal)
		{
			java.util.logging.Logger.getLogger(PhantomVisibleFarmTravel.class.getName()).info(diagnostic);
		}
		_failure.accept(profileId, new Failure(profileId, attempt.player, attempt.epoch, attempt.goal, stepId, reason, disposition, attempt.startedNanos, attempt.failures, attempt.proof, attempt.indeterminateSearch != null));
	}

	private static String searchDiagnostic(String event, long profileId, Attempt attempt, String stepId, String reason, Disposition disposition)
	{
		return event + " profile=" + profileId + ";goal=" + attempt.goal.goalId() + "." + attempt.goal.revision()
			+ ";object=" + attempt.player.getObjectId() + ";epoch=" + attempt.epoch + ";step=" + stepId + ";reason=" + reason
			+ ";disposition=" + disposition + ";proof=" + attempt.proof.diagnostic();
	}

	private synchronized void rememberFailure(long profileId, String reason)
	{
		rememberFailure(profileId, reason, Disposition.STORE_PENDING, _attempts.get(profileId));
	}

	private synchronized void rememberFailure(long profileId, String reason, Disposition disposition, Attempt attempt)
	{
		if (!_terminalReasons.containsKey(profileId) && (_terminalReasons.size() >= _navigation.policy().maximumTrackedProfiles()))
		{
			final var iterator = _terminalReasons.keySet().iterator();
			if (iterator.hasNext()) { _terminalReasons.remove(iterator.next()); }
		}
		_terminalReasons.put(profileId, new TerminalFailure(reason, _failureSequence.incrementAndGet(), disposition, attempt == null ? 0 : attempt.goal.goalId(), attempt == null ? 0 : attempt.goal.revision(), attempt == null ? 0 : attempt.player.getObjectId(), attempt == null ? 0 : attempt.epoch, attempt == null ? "" : attempt.segmentWitness, attempt == null ? PhantomNavigationResult.BudgetProof.unknown() : attempt.proof));
	}

	public enum Disposition { ROUTE_UNUSABLE, TRANSIENT_SERVICE, NATIVE_ACTION_REJECTED, STORE_PENDING, PROTOCOL_VIOLATION, INDETERMINATE_ROUTE }
	public record TerminalFailure(String reason, long sequence, Disposition disposition, long goalId, long revision, int objectId, long epoch, String segmentWitness, PhantomNavigationResult.BudgetProof proof)
	{
		public TerminalFailure(String reason, long sequence, Disposition disposition, long goalId, long revision, int objectId, long epoch, String segmentWitness) { this(reason, sequence, disposition, goalId, revision, objectId, epoch, segmentWitness, PhantomNavigationResult.BudgetProof.unknown()); }
		public TerminalFailure(String reason, long sequence, Disposition disposition, long goalId, long revision, int objectId, long epoch) { this(reason, sequence, disposition, goalId, revision, objectId, epoch, ""); }
	}

	public TerminalFailure lastFailure(long profileId) { return _terminalReasons.get(profileId); }

	/** Passive witness of the existing three-attempt service fence for this exact actor/root. */
	public boolean transientServiceExhausted(long profileId, PhantomGoal goal)
	{
		final var attempt = _attempts.get(profileId); final var lifetime = _materialization.find(profileId).orElse(null);
		return attempt != null && attempt.transientExhausted && attempt.goal.equals(goal) && lifetime != null
			&& lifetime.characterObjectId() == attempt.player.getObjectId() && lifetime.materializedAtNanos() == attempt.epoch
			&& World.getInstance().getPlayer(attempt.player.getObjectId()) == attempt.player;
	}

	public enum ArrivalKind { ARRIVED, PENDING, STALE_GOAL, TERMINAL }
	public record ArrivalResult(ArrivalKind kind, long goalId, long revision, int objectId, long epoch, long failureSequence, String reason, PhantomNavigationResult.BudgetProof proof)
	{
		public ArrivalResult(ArrivalKind kind, long goalId, long revision, int objectId, long epoch, long failureSequence, String reason) { this(kind, goalId, revision, objectId, epoch, failureSequence, reason, PhantomNavigationResult.BudgetProof.unknown()); }
	}

	/** Observe the existing Attempt without resetting its terminal receipt. */
	public ArrivalResult observeArrival(long profileId, PhantomGoal goal)
	{
		final var lifetime = _materialization.find(profileId).orElse(null);
		final int objectId = lifetime == null ? 0 : lifetime.characterObjectId();
		final long epoch = lifetime == null ? 0 : lifetime.materializedAtNanos();
		if (!currentGoal(profileId, goal))
		{
			discardStale(profileId, goal);
			return new ArrivalResult(ArrivalKind.STALE_GOAL, goal.goalId(), goal.revision(), objectId, epoch, 0, "travel.stale_goal");
		}
		final boolean arrived = arrive(profileId, goal);
		if (!currentGoal(profileId, goal))
		{
			discardStale(profileId, goal);
			return new ArrivalResult(ArrivalKind.STALE_GOAL, goal.goalId(), goal.revision(), objectId, epoch, 0, "travel.stale_goal");
		}
		final var failure = lastFailure(profileId);
		final var attempt = _attempts.get(profileId);
		final boolean terminal = (attempt != null) && attempt.terminal && (failure != null) && (failure.goalId() == goal.goalId()) && (failure.revision() == goal.revision()) && (failure.objectId() == objectId) && (failure.epoch() == epoch);
		return new ArrivalResult(arrived ? ArrivalKind.ARRIVED : terminal ? ArrivalKind.TERMINAL : ArrivalKind.PENDING, goal.goalId(), goal.revision(), objectId, epoch, terminal ? failure.sequence() : 0, terminal ? failure.reason() : reason(profileId), terminal ? failure.proof() : PhantomNavigationResult.BudgetProof.unknown());
	}

	private boolean currentGoal(long profileId, PhantomGoal goal)
	{
		return Objects.equals(_background.ordinaryTravelGoal(profileId).orElse(null), goal);
	}

	static PhantomBackgroundGoalSpec travelSpec(long profileId, PhantomGoal goal)
	{
		return PhantomBackgroundGoalSpec.canonicalHistoricalIdle(profileId, goal) ? PhantomBackgroundGoalSpec.parseLifecycle(goal) : PhantomBackgroundGoalSpec.parse(goal);
	}

	private boolean currentJourney(long profileId, Journey journey)
	{
		final var lifetime = _materialization.find(profileId).orElse(null);
		return (lifetime != null) && (lifetime.characterObjectId() == journey.objectId) && (lifetime.materializedAtNanos() == journey.epoch) && currentGoal(profileId, journey.attempt.goal);
	}

	private void discardStale(long profileId, PhantomGoal goal)
	{
		final var journey = _journeys.get(profileId);
		if ((journey != null) && journey.attempt.goal.equals(goal) && (_materialization.find(profileId).map(value -> (value.characterObjectId() == journey.objectId) && (value.materializedAtNanos() == journey.epoch)).orElse(false))) { remove(profileId, journey); }
	}

	/** Recovery stops only the captured current goal's movement, never a foreign lifetime. */
	public boolean yieldForPreparation(long profileId)
	{
		return yieldForPreparation(() -> _pendingStores.containsKey(profileId), () ->
		{
			final var pending = _pendingStores.get(profileId);
			// Finish only an existing factual arrival, outside ActionLease/Player/Attempt locks.
			// Preparation must not advance travel or renew the original Attempt deadline.
			return pending != null && drainArrival(profileId, pending.goal, pending, true);
		}, () -> cancelForPreparation(profileId));
	}

	static boolean yieldForPreparation(BooleanSupplier pending, BooleanSupplier drain, BooleanSupplier cancel)
	{
		if (pending.getAsBoolean() && !drain.getAsBoolean()) { return false; }
		if (pending.getAsBoolean()) { return false; }
		return cancel.getAsBoolean();
	}

	private boolean cancelForPreparation(long profileId)
	{
		if (_pendingStores.containsKey(profileId)) { return false; }
		final var incident = _alternatives.get(profileId);
		final var recovery = incident == null ? null : incident.learnedReturn;
		if (recovery != null && !recovery.settled)
		{
			cancelRecoveryReturn(profileId, recovery);
			if (recovery.attempt != null && recovery.attempt.status() == PhantomLearnedEscape.Status.PENDING) { return false; }
		}
		final var journey = _journeys.get(profileId);
		if (journey == null) { return true; }
		if (journey.swim != null) { return false; }
		if (!currentJourney(profileId, journey) || !currentGoal(profileId, journey.goal) || _pendingStores.containsKey(profileId)) { return false; }
		// End only this physical route phase. The original Attempt and its deadline remain unchanged.
		return remove(profileId, journey);
	}

	/** Recovery stops only the captured current goal's movement, never a foreign lifetime. */
	public boolean stopForRecovery(long profileId, PhantomGoal goal, int objectId, long epoch)
	{
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((lifetime == null) || (lifetime.characterObjectId() != objectId) || (lifetime.materializedAtNanos() != epoch) || !_background.isCurrentRecoveryGoal(profileId, goal)) { return false; }
		final var incident = _alternatives.get(profileId);
		final var recovery = incident == null ? null : incident.learnedReturn;
		if (recovery != null && recovery.goal.equals(goal) && recovery.holdsTravel() && !recovery.checkpointed) { return false; }
		final var journey = _journeys.get(profileId);
		if (journey == null) { return true; }
		if (journey.swim != null) { return false; }
		return journey.attempt.goal.equals(goal) && (journey.objectId == objectId) && (journey.epoch == epoch) && remove(profileId, journey);
	}

	public void cancelSuperseded(long profileId, PhantomGoal current)
	{
		final var alternatives = _alternatives.get(profileId);
		if (alternatives != null)
		{
			final var recovery = alternatives.learnedReturn;
			if (recovery != null && !recovery.goal.equals(current)) { cancelRecoveryReturn(profileId, recovery); }
			synchronized (alternatives)
			{
				final var proposal = alternatives.proposal;
				if (proposal != null && !proposal.predecessor.equals(current) && !proposal.successor.equals(current))
				{ discardProposal(profileId, alternatives); }
			}
		}
		final var journey = _journeys.get(profileId);
		final var lifetime = _materialization.find(profileId).orElse(null);
		if ((journey != null) && (lifetime != null) && (lifetime.characterObjectId() == journey.objectId) && (lifetime.materializedAtNanos() == journey.epoch)
			&& !journey.goal.equals(current) && _background.isCurrentRecoveryGoal(profileId, current)) { remove(profileId, journey); }
	}

	public record Failure(long profileId, Player player, long epoch, PhantomGoal goal, String stepId, String reason, Disposition disposition, long startedNanos, int attempts, PhantomNavigationResult.BudgetProof proof, boolean spatialBackoff)
	{
		public Failure(long profileId, Player player, long epoch, PhantomGoal goal, String stepId, String reason, Disposition disposition, long startedNanos, int attempts, PhantomNavigationResult.BudgetProof proof) { this(profileId, player, epoch, goal, stepId, reason, disposition, startedNanos, attempts, proof, false); }
		public Failure(long profileId, Player player, long epoch, PhantomGoal goal, String stepId, String reason, Disposition disposition, long startedNanos, int attempts) { this(profileId, player, epoch, goal, stepId, reason, disposition, startedNanos, attempts, PhantomNavigationResult.BudgetProof.unknown()); }
		public Failure(PhantomGoal goal, String stepId, String reason)
		{
			this(0, null, 0, goal, stepId, reason, legacyDisposition(reason), 0, 0);
		}

		public boolean routeFailure()
		{
			return disposition == Disposition.ROUTE_UNUSABLE;
		}

		private static Disposition legacyDisposition(String reason)
		{
			return switch (reason)
			{
				case "travel.route_absent", "travel.destination_unproven", "travel.gatekeeper_unproven", "travel.navigation_no_path", "travel.navigation_route_obstructed", "travel.native_progress_stuck", "travel.native_progress_timeout" -> Disposition.ROUTE_UNUSABLE;
				default -> Disposition.PROTOCOL_VIOLATION;
			};
		}
	}

	public String reason(long profileId)
	{
		final Journey journey = _journeys.get(profileId);
		final TerminalFailure failure = _terminalReasons.get(profileId);
		return journey == null ? failure == null ? "" : failure.reason() : journey.reason;
	}

	private void clearRoute(long profileId, Journey journey)
	{
		_navigation.consumeTravelPlan(journey.requestId);
		if (journey.requestId != 0)
		{
			_navigation.progressTracker().cancel(profileId, journey.requestId);
			_navigation.cancel(profileId, journey.requestId);
			_navigation.consume(journey.requestId);
		}
		journey.requestId = 0;
		journey.submittedSearch = null;
		journey.heldSearch = null;
		journey.heldWalkDestination = null;
		journey.waypoints = null;
		journey.nativeLeg = null;
		journey.route = null;
		journey.recoveryReceipt = null;
		journey.trackedIndex = -1;
		journey.index = 0;
	}

	private static boolean walkCombatActive(Player player)
	{
		return player.isInCombat() || player.isAttackingNow();
	}

	boolean yieldDryWalkForCombat(long profileId, PhantomGoal goal, Player player, long epoch)
	{
		if (!walkCombatActive(player) || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType())) { return false; }
		final var journey = _journeys.get(profileId);
		final var incident = _alternatives.get(profileId);
		boolean yielded = false;
		if (incident != null && incident.matches(player, epoch))
		{
			synchronized (incident)
			{
				final var proposal = incident.proposal;
				if (proposal != null && (proposal.predecessor.equals(goal) || proposal.successor.equals(goal)))
				{
					discardProposal(profileId, incident);
					yielded = true;
				}
			}
		}
		if (journey == null || journey.player != player || journey.epoch != epoch || !journey.goal.equals(goal)
			|| journey.swim != null || journey.step.type() != PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND || !currentJourney(profileId, journey)) { return yielded; }
		if (journey.requestId != 0 || journey.moveIssued)
		{
			stopOwnedMove(journey, player);
			journey.moveIssued = false;
			journey.moveCurrent = null;
			clearRoute(profileId, journey);
			closeRoutePhase(profileId, journey);
			journey.phaseClosed = false;
			journey.localApproach = 0;
		}
		journey.reason = "travel.combat_yield";
		return true;
	}

	private void remove(long profileId)
	{
		remove(profileId, _journeys.get(profileId));
	}

	private boolean remove(long profileId, Journey journey)
	{
		synchronized (_attempts)
		{
			if ((journey == null) || !_journeys.remove(profileId, journey)) { return false; }
			closeRoutePhase(profileId, journey);
		}
		final var swim = journey.swim;
		try (var action = (swim == null ? _materialization.tryAcquireAction(profileId) : _materialization.tryAcquireAction(profileId, swim.stamp)).orElse(null))
		{
			if ((action != null) && (action.player() == journey.player))
			{
				if (journey.returnOperation != null) { journey.returnOperation.cancel(); journey.returnOperation=null; }
				stopOwnedMove(journey, action.player());
			}
		}
		clearRoute(profileId, journey);
		_signals.withdraw(profileId, "visible.farm.travel", signalSequence());
		return true;
	}

	private void publishRoutePhase(long profileId, Journey journey, Player player)
	{
		final Attempt attempt = journey.attempt;
		final var owner = attempt.nativeOwner;
		final var evidence = attempt.evidence;
		if (!journey.moveIssued || !player.isMoving() || !player.hasAI() || (player.getAI().getIntention() != Intention.MOVE_TO)
			|| (player.getXdestination() != journey.moveX) || (player.getYdestination() != journey.moveY)
			|| (player != attempt.player) || (owner == null) || (evidence == null) || (owner.player() != player)
			|| (player.getNativeWorkOwner() != owner) || !owner.isCurrent() || (owner.epoch() != attempt.epoch)
			|| (owner.evidence() != evidence) || !evidence.matches(player.getObjectId(), attempt.epoch)
			|| (World.getInstance().getPlayer(player.getObjectId()) != player)) { return; }
		synchronized (_attempts)
		{
			if ((_journeys.get(profileId) != journey) || (_attempts.get(profileId) != attempt) || attempt.terminal || journey.phaseClosed) { return; }
			evidence.phase(PlayerNativeEvidence.Phase.ROUTE, attempt.phaseSince, attempt.phaseSince + (journey.nativeBudget == null ? _navigation.policy().maximumAttemptDurationNanos() : journey.nativeBudget.episodeNanos));
		}
	}

	private void closeRoutePhase(long profileId, Journey journey)
	{
		synchronized (_attempts)
		{
			journey.phaseClosed = true;
			final Journey current = _journeys.get(profileId);
			if ((current == null) || (current == journey) || (current.attempt != journey.attempt)) { clearRoutePhase(journey.attempt); }
		}
	}

	private void clearRoutePhase(Attempt attempt)
	{
		synchronized (_attempts)
		{
			if ((attempt != null) && (attempt.evidence != null) && attempt.evidence.matches(attempt.player.getObjectId(), attempt.epoch))
			{
				attempt.evidence.clearPhase(PlayerNativeEvidence.Phase.ROUTE, attempt.phaseSince);
			}
		}
	}

	private void clearAttempt(long profileId)
	{
		final var alternatives = _alternatives.remove(profileId);
		if (alternatives != null)
		{
			final var recovery = alternatives.learnedReturn;
			if (recovery != null) { cancelRecoveryReturn(profileId, recovery); }
			synchronized (alternatives) { discardProposal(profileId, alternatives); }
		}
		synchronized (_attempts)
		{
			clearRoutePhase(_attempts.remove(profileId));
			_journeyObservations.remove(profileId);
			_farmSearches.clear(profileId);
			_swimTried.remove(profileId);
		}
	}

	private long signalSequence()
	{
		return _signalSequence.updateAndGet(previous -> Math.max(previous + 1, Math.max(1, _clock.getAsLong())));
	}

	private static void stopOwnedMove(Journey journey, Player player)
	{
		if (journey != null && journey.swim != null && !ownedSwimMove(journey, player)) { return; }
		if (journey != null && journey.swim == null && (journey.moveCurrent == null || !journey.moveCurrent.getAsBoolean()
			|| player.getNativeWorkOwner() != journey.attempt.nativeOwner || player.getInstanceId() != journey.moveInstance
			|| player.getZdestination() != journey.moveZ)) { return; }
		if ((journey != null) && journey.moveIssued && (player.getObjectId() == journey.objectId) && player.hasAI() && (player.getAI().getIntention() == Intention.MOVE_TO) && player.isMoving() && (player.getXdestination() == journey.moveX) && (player.getYdestination() == journey.moveY))
		{
			player.stopMove(null);
			player.getAI().setIntention(Intention.IDLE);
		}
	}

	@Override
	public void beforeMaterialize(long profileId, int characterObjectId)
	{
		remove(profileId);
		clearAttempt(profileId);
		_pendingStores.remove(profileId);
		_terminalReasons.remove(profileId);
	}

	@Override
	public void afterPlayerLoad(long profileId, Player player)
	{
	}

	@Override
	public void materializeSucceeded(long profileId, int characterObjectId)
	{
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
		remove(profileId);
		clearAttempt(profileId);
		_pendingStores.remove(profileId);
		_terminalReasons.remove(profileId);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		stopOwnedMove(_journeys.get(profileId), player);
		remove(profileId);
		clearAttempt(profileId);
		_pendingStores.remove(profileId);
		_terminalReasons.remove(profileId);
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
	}

	private static final class Journey
	{
		private final long goalId;
		private final long revision;
		private final int objectId;
		private final Player player;
		private final long epoch;
		private final PhantomNormalGatekeeperTravel.Step step;
		private final PhantomGoal goal;
		private final Attempt attempt;
		private final boolean currentAnchorReturn;
		private final boolean nativeIdleWalk;
		private NativeWalkBudget nativeBudget;
		private DryRecovery dryRecovery;
		private SwimRecovery swim;
		private boolean landedSwim;
		private PhantomNavigationPoint recoveryReceipt;
		private PhantomNavigationPoint returnLanding;
		private int returnWaypoints;
		private double returnDistance;
		private PhantomTravelStrategy.Candidate plan;
		private int stageIndex;
		private PhantomNativeTravelActions.ReturnOperation returnOperation;
		private boolean phaseClosed;
		private boolean pveSafeWalk;
		private boolean moveIssued;
		private BooleanSupplier moveCurrent;
		private NativeLeg nativeLeg;
		private int moveX;
		private int moveY;
		private int moveZ;
		private int moveInstance;
		private long requestId;
		private long deadline;
		private PhantomNavigationPoint arrivalPoint;
		private PhantomFarmRouteBackoff.Probe submittedSearch;
		private PhantomFarmRouteBackoff.Probe heldSearch;
		private PhantomNavigationPoint heldWalkDestination;
		private List<PhantomNavigationPoint> waypoints;
		private PhantomNavigationRoute route;
		private int trackedIndex = -1;
		private int index;
		private int localApproach;
		private boolean verifiedCheckpointRetried;
		private volatile String reason = "travel.started";

		private boolean ordinaryWalkEpisodes() { return nativeBudget != null && !nativeIdleWalk && !currentAnchorReturn; }

		private Journey(PhantomGoal goal, Player player, long epoch, PhantomNormalGatekeeperTravel.Step step, Attempt attempt)
		{
			goalId = goal.goalId();
			revision = goal.revision();
			objectId = player.getObjectId();
			this.player = player;
			this.epoch = epoch;
			this.step = step;
			this.goal = goal;
			this.attempt = attempt;
			currentAnchorReturn = step.id().startsWith("live.approach.") && step.fromAnchorId().equals(step.toAnchorId());
			nativeIdleWalk = step.id().startsWith("native.idle.walk.");
		}
	}

	private record NativeLeg(PhantomWalkCorridorPlanner.Edge edge, BooleanSupplier arrived, java.util.concurrent.atomic.AtomicBoolean published) { }
	private record DryRecovery(PhantomFarmRouteBackoff.Probe rejected, PhantomNavigationResult.BudgetProof proof) { }

	private static final class AlternativeWalks
	{
		final Player player;
		final long epoch, started;
		long submitAt, deadline, nextEpisode, nextReturnEvaluation;
		int remaining = 3;
		int remainingReturnEvaluations = 2;
		boolean retryEpisode;
		final Set<String> tried = new java.util.HashSet<>();
		volatile FarmWalkProposal proposal;
		volatile LearnedReturn learnedReturn;
		volatile RecoveryFarmArrival farmArrival;

		AlternativeWalks(Player player, long epoch, long started, long duration)
		{
			this.player = player;
			this.epoch = epoch;
			this.started = started;
			deadline = started + duration;
			nextEpisode = deadline;
			nextReturnEvaluation = started;
		}

		boolean matches(Player player, long epoch)
		{ return this.player == player && this.epoch == epoch; }
	}

	private record RecoveryFarmArrival(PhantomGoal goal, PlayerNativeWork.Owner owner, PlayerNativeEvidence.Snapshot baseline) { }

	private static final class LearnedReturn
	{
		final PhantomGoal goal;
		final Player player;
		final PhantomNativeWorkScope owner;
		final Stamp stamp;
		final PhantomNavigationPoint origin;
		final PhantomFarmRouteBackoff.Site site;
		final BooleanSupplier currentRoot;
		final long start, deadline;
		final java.util.concurrent.atomic.AtomicBoolean processing = new java.util.concurrent.atomic.AtomicBoolean();
		volatile PhantomLearnedEscape.Attempt attempt;
		volatile PhantomLearnedEscape.Receipt receipt;
		volatile boolean settled, cancelled, cancelCastRequested, checkpointed, unreconciled;

		LearnedReturn(PhantomGoal goal, Player player, PhantomNativeWorkScope owner, Stamp stamp,
			PhantomNavigationPoint origin, PhantomFarmRouteBackoff.Site site, BooleanSupplier currentRoot, long start, long deadline)
		{
			this.goal = goal;
			this.player = player;
			this.owner = owner;
			this.stamp = stamp;
			this.origin = origin;
			this.site = site;
			this.currentRoot = currentRoot;
			this.start = start;
			this.deadline = deadline;
		}

		boolean holdsTravel()
		{
			return !cancelled && (!settled || unreconciled || receipt != null && !receipt.arrivals().isEmpty());
		}
	}

	private static final class FarmWalkProposal
	{
		final PhantomGoal predecessor, successor;
		final String anchor;
		final PhantomNormalGatekeeperTravel.Step step;
		final PhantomFarmRouteBackoff.Probe probe;
		final PlayerNativeWork.Owner owner;
		final Stamp stamp;
		final long requestId;
		final boolean landingApproach;
		volatile PhantomNavigationResult result;

		FarmWalkProposal(PhantomGoal predecessor, PhantomGoal successor, String anchor, PhantomNormalGatekeeperTravel.Step step,
			PhantomFarmRouteBackoff.Probe probe, PlayerNativeWork.Owner owner, Stamp stamp, long requestId, boolean landingApproach)
		{
			this.predecessor = predecessor;
			this.successor = successor;
			this.anchor = anchor;
			this.step = step;
			this.probe = probe;
			this.owner = owner;
			this.stamp = stamp;
			this.requestId = requestId;
			this.landingApproach = landingApproach;
		}

		boolean matches(PhantomGoal predecessor, PhantomGoal proposed, PhantomFarmRouteBackoff.Site site, PhantomNavigationPoint origin,
			PlayerNativeWork.Owner owner, Stamp stamp)
		{
			return this.predecessor.equals(predecessor) && successor.equals(proposed) && probe.site().equals(site)
				&& probe.origin().equals(origin) && this.owner == owner && this.stamp.equals(stamp);
		}
	}

	private static final class SwimRecovery
	{
		final PhantomFarmRouteBackoff.Probe rejected;
		final PhantomNavigationResult.BudgetProof rejectedProof;
		final Stamp stamp;
		final long submitAt, deadline;
		final java.util.concurrent.atomic.AtomicBoolean advancing = new java.util.concurrent.atomic.AtomicBoolean();
		PhantomFarmSwimRoute.Plan plan;
		int index;
		long legDeadline, wetSince, initialBreath;
		long lastProgress;
		double distanceRemaining;
		boolean emergency;
		BooleanSupplier arrived, moveCurrent;

		SwimRecovery(PhantomFarmRouteBackoff.Probe rejected, PhantomNavigationResult.BudgetProof rejectedProof,
			Stamp stamp, long submitAt, long deadline)
		{
			this.rejected = rejected;
			this.rejectedProof = rejectedProof;
			this.stamp = stamp;
			this.submitAt = submitAt;
			this.deadline = deadline;
		}
	}

	private record PendingStore(Player player, long epoch, PhantomGoal goal, String anchorId, Journey journey) { }

	/** Bounded native WALK: retries consume the complete verified local corridors. */
	public static final class NativeWalkBudget
	{
		private final long startedNanos, episodeNanos;
		private final double speed;
		private long deadline;
		private final double maximumDistance;
		private final int maximumWaypoints;
		private final java.util.Set<PhantomNavigationPoint> visited = new java.util.HashSet<>();
		private long episodeSince;
		private double chargedDistance;
		private int routes;
		private PhantomNavigationPoint releasedDestination;

		public NativeWalkBudget(PhantomNavigationPoint origin, PhantomNavigationPoint destination, long now, double speed, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy policy)
		{
			final double distance = origin.distanceTo(destination);
			if (now < 0 || origin.instanceId() != destination.instanceId() || !Double.isFinite(speed) || speed <= 0 || distance > policy.maximumRouteDistance()) { throw new IllegalArgumentException("Invalid native idle WALK budget."); }
			startedNanos = now; this.speed = speed;
			deadline = deadlineFor(distance);
			episodeSince = now;
			episodeNanos = Math.min(policy.maximumAttemptDurationNanos(), PlayerNativeEvidence.MAX_PHASE_NANOS);
			maximumDistance = policy.maximumRouteDistance(); maximumWaypoints = policy.maximumWaypoints(); visited.add(origin);
		}
		private long deadlineFor(double distance)
		{
			// Same bounded formula, always anchored to the original start. A newly
			// admitted detour may cost more than its chord; the hard ceiling stays 30 minutes.
			final long millis = Math.min(java.util.concurrent.TimeUnit.MINUTES.toMillis(30), Math.max(30_000, (long) Math.ceil(2000 * distance / speed) + 5000L * (long) Math.ceil(distance / 3000) + 30_000));
			return Math.addExact(startedNanos, java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(millis));
		}

		public boolean expired(long now) { return now < episodeSince || now >= deadline || now - episodeSince >= episodeNanos; }
		public java.util.Set<PhantomNavigationPoint> visited() { return java.util.Set.copyOf(visited); }

		/** Relative active-time budget, never a persisted System.nanoTime origin or a fresh route allowance. */
		public Snapshot suspend(long now)
		{
			if (now < startedNanos || now < episodeSince) { throw new IllegalArgumentException("WALK checkpoint clock moved backwards."); }
			return new Snapshot(Math.min(java.util.concurrent.TimeUnit.MINUTES.toNanos(30), now - startedNanos), Math.max(0, deadline - now),
				Math.min(episodeNanos, now - episodeSince), episodeNanos, speed, maximumDistance, maximumWaypoints, chargedDistance, routes, List.copyOf(visited), releasedDestination);
		}
		public static NativeWalkBudget resume(Snapshot saved, long now, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy policy)
		{
			if (now < 0 || saved.maximumDistance() > policy.maximumRouteDistance() || saved.maximumWaypoints() > policy.maximumWaypoints()
				|| saved.episodeNanos() > Math.min(policy.maximumAttemptDurationNanos(), PlayerNativeEvidence.MAX_PHASE_NANOS))
			{ throw new IllegalArgumentException("WALK checkpoint exceeds current navigation policy."); }
			return new NativeWalkBudget(saved, now);
		}
		private NativeWalkBudget(Snapshot saved, long now)
		{
			startedNanos = Math.subtractExact(now, saved.elapsedNanos()); deadline = Math.addExact(now, saved.remainingNanos());
			episodeSince = Math.subtractExact(now, saved.episodeElapsedNanos()); episodeNanos = saved.episodeNanos(); speed = saved.speed();
			maximumDistance = saved.maximumDistance(); maximumWaypoints = saved.maximumWaypoints(); chargedDistance = saved.chargedDistance();
			routes = saved.routes(); visited.addAll(saved.visited()); releasedDestination = saved.releasedDestination();
		}
		public record Snapshot(long elapsedNanos, long remainingNanos, long episodeElapsedNanos, long episodeNanos, double speed,
			double maximumDistance, int maximumWaypoints, double chargedDistance, int routes, List<PhantomNavigationPoint> visited, PhantomNavigationPoint releasedDestination)
		{
			public Snapshot
			{
				final var sortedPoints = visited.stream().sorted(java.util.Comparator.comparingInt(PhantomNavigationPoint::x).thenComparingInt(PhantomNavigationPoint::y)
					.thenComparingInt(PhantomNavigationPoint::z).thenComparingInt(PhantomNavigationPoint::instanceId)).toList();
				visited = sortedPoints;
				if (elapsedNanos < 0 || remainingNanos < 0 || elapsedNanos > java.util.concurrent.TimeUnit.MINUTES.toNanos(30) || remainingNanos > java.util.concurrent.TimeUnit.MINUTES.toNanos(30) - elapsedNanos
					|| episodeNanos <= 0 || episodeNanos > PlayerNativeEvidence.MAX_PHASE_NANOS || episodeElapsedNanos < 0 || episodeElapsedNanos > episodeNanos
					|| !Double.isFinite(speed) || speed <= 0 || !Double.isFinite(maximumDistance) || maximumDistance <= 0 || maximumDistance > 100_000
					|| maximumWaypoints < 1 || maximumWaypoints > 64 || !Double.isFinite(chargedDistance) || chargedDistance < 0 || chargedDistance > maximumDistance
					|| routes < 0 || routes > 64 || visited.isEmpty() || visited.size() > 64 || java.util.Set.copyOf(visited).size() != visited.size()
					|| visited.stream().anyMatch(p -> p.instanceId() != sortedPoints.getFirst().instanceId())
					|| releasedDestination != null && (releasedDestination.instanceId() != visited.getFirst().instanceId() || visited.contains(releasedDestination)))
				{ throw new IllegalArgumentException("Invalid bounded WALK checkpoint."); }
			}
			public byte[] encode()
			{
				try
				{
					final var bytes = new java.io.ByteArrayOutputStream();
					try (var out = new java.io.DataOutputStream(bytes))
					{
						out.writeInt(1); out.writeLong(elapsedNanos); out.writeLong(remainingNanos); out.writeLong(episodeElapsedNanos); out.writeLong(episodeNanos);
						out.writeDouble(speed); out.writeDouble(maximumDistance); out.writeInt(maximumWaypoints); out.writeDouble(chargedDistance); out.writeInt(routes);
						out.writeInt(visited.size()); for (var point : visited) { writePoint(out, point); }
						out.writeBoolean(releasedDestination != null); if (releasedDestination != null) { writePoint(out, releasedDestination); }
					}
					return bytes.toByteArray();
				}
				catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
			}
			public Snapshot elapse(long nanos)
			{
				if (nanos < 0) { throw new IllegalArgumentException("WALK checkpoint active time cannot move backwards."); }
				final long delta = Math.min(nanos, java.util.concurrent.TimeUnit.MINUTES.toNanos(30));
				return new Snapshot(Math.min(java.util.concurrent.TimeUnit.MINUTES.toNanos(30), elapsedNanos + delta), Math.max(0, remainingNanos - delta),
					Math.min(episodeNanos, episodeElapsedNanos + delta), episodeNanos, speed, maximumDistance, maximumWaypoints, chargedDistance, routes, visited, releasedDestination);
			}
			public static Snapshot decode(byte[] bytes)
			{
				if (bytes == null || bytes.length > 2048) { throw new IllegalArgumentException("Invalid WALK checkpoint payload bound."); }
				try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes)))
				{
					if (in.readInt() != 1) { throw new IllegalArgumentException("Unknown WALK checkpoint schema."); }
					final long elapsed = in.readLong(), remaining = in.readLong(), episodeElapsed = in.readLong(), episode = in.readLong();
					final double speed = in.readDouble(), distance = in.readDouble(); final int waypoints = in.readInt(); final double charged = in.readDouble(); final int routes = in.readInt(), count = in.readInt();
					if (count < 1 || count > 64) { throw new IllegalArgumentException("Invalid WALK checkpoint point count."); }
					final var points = new java.util.ArrayList<PhantomNavigationPoint>(); for (int i = 0; i < count; i++) { points.add(readPoint(in)); }
					final var released = in.readBoolean() ? readPoint(in) : null;
					if (in.available() != 0) { throw new IllegalArgumentException("Trailing WALK checkpoint data."); }
					return new Snapshot(elapsed, remaining, episodeElapsed, episode, speed, distance, waypoints, charged, routes, points, released);
				}
				catch (java.io.IOException failure) { throw new IllegalArgumentException("Invalid WALK checkpoint payload.", failure); }
			}
			private static void writePoint(java.io.DataOutputStream out, PhantomNavigationPoint point) throws java.io.IOException
			{ out.writeInt(point.x()); out.writeInt(point.y()); out.writeInt(point.z()); out.writeInt(point.instanceId()); }
			private static PhantomNavigationPoint readPoint(java.io.DataInputStream in) throws java.io.IOException
			{ return new PhantomNavigationPoint(in.readInt(), in.readInt(), in.readInt(), in.readInt()); }
		}

		public boolean admit(PhantomNavigationRoute route, long now)
		{
			if (expired(now) || routes >= 64 || route.waypoints().isEmpty() || route.waypoints().size() > maximumWaypoints || visited.contains(route.destination())) { return false; }
			double distance = 0;
			var point = route.origin();
			for (var next : route.waypoints())
			{
				if (point.instanceId() != next.instanceId()) { return false; }
				distance += point.distanceTo(next); point = next;
			}
			if (!point.equals(route.destination()) || !Double.isFinite(distance) || distance <= 0 || chargedDistance + distance > maximumDistance) { return false; }
			chargedDistance += distance; routes++; releasedDestination = route.destination();
			deadline = Math.max(deadline, deadlineFor(chargedDistance));
			return true;
		}

		public boolean checkpoint(PhantomNavigationPoint actual, long now)
		{
			if (expired(now) || releasedDestination == null || !actual.equals(releasedDestination) || visited.size() >= 64 || !visited.add(actual)) { return false; }
			releasedDestination = null; episodeSince = now;
			return true;
		}

		private boolean canCrossShore(PhantomFarmSwimRoute.Plan plan, long now)
		{
			final double distance = plan.legs().stream().mapToDouble(leg -> leg.from().distanceTo(leg.to())).sum();
			return !expired(now) && routes < 64 && visited.size() < 64 && !visited.contains(plan.legs().getLast().to())
				&& chargedDistance + distance <= maximumDistance;
		}

		private boolean crossedShore(PhantomFarmSwimRoute.Plan plan, PhantomNavigationPoint actual, long now)
		{
			if (!canCrossShore(plan, now) || !actual.equals(plan.legs().getLast().to())) { return false; }
			chargedDistance += plan.legs().stream().mapToDouble(leg -> leg.from().distanceTo(leg.to())).sum();
			routes++;
			visited.add(actual);
			deadline = Math.max(deadline, deadlineFor(chargedDistance));
			episodeSince = now;
			return true;
		}
	}

	private static final class Attempt
	{
		private final PhantomGoal goal;
		private final Player player;
		private final long epoch;
		private final long startedNanos;
		private long phaseSince;
		private final PlayerNativeWork.Owner nativeOwner;
		private final PlayerNativeEvidence evidence;
		private int failures;
		private DryRecovery dryRecovery;
		private long retryAtNanos;
		private volatile boolean transientExhausted;
		private volatile boolean terminal;
		private volatile String segmentWitness = "";
		private volatile PhantomNavigationResult.BudgetProof proof = PhantomNavigationResult.BudgetProof.unknown();
		private volatile PhantomFarmRouteBackoff.Probe indeterminateSearch;
		private volatile PhantomNavigationPoint suppressedSearch;
		private final java.util.Set<PhantomWalkCorridorPlanner.Edge> blockedEdges = new java.util.HashSet<>();

		private Attempt(PhantomGoal goal, Player player, long epoch, long startedNanos, PlayerNativeWork.Owner nativeOwner, PlayerNativeEvidence evidence)
		{
			this.goal = goal; this.player = player; this.epoch = epoch; this.startedNanos = startedNanos; phaseSince = startedNanos;
			this.nativeOwner = nativeOwner; this.evidence = evidence;
		}

		private boolean matches(PhantomGoal goal, Player player, long epoch)
		{
			return (this.player == player) && (this.epoch == epoch) && (this.goal.goalId() == goal.goalId()) && (this.goal.revision() == goal.revision());
		}
	}
}

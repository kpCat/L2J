/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.ActionResult;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.ActionStatus;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.Observation;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.QuestStatus;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceBackend.Target;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Content;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Kind;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Step;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Caller-driven bounded coordinator. Native scripts remain the only mutation owners. */
public final class PhantomQuestInstanceService
{
	public static final String QUEST_GOAL_TYPE = "quest.play";
	public static final String CLASS_GOAL_TYPE = "class.transfer";
	public static final String INSTANCE_GOAL_TYPE = "instance.play";
	public static final String TARGET_NAMESPACE = "supported.content";
	public static final String CANDIDATE_KEY = "candidate.questinstance.play";
	public static final String ACTION_KEY = "questinstance.advance";
	private static final String SIGNAL_SOURCE = "questinstance.active";
	private static final int MAXIMUM_OPERATIONS = 128;
	private static final long SIGNAL_TTL_MILLIS = TimeUnit.MINUTES.toMillis(5);
	private static final long NAVIGATION_DEADLINE_NANOS = TimeUnit.MINUTES.toNanos(2);
	private static final double INTERACTION_DISTANCE = 200;
	private static final double COMBAT_DISTANCE = 1000;
	private static final long COMBAT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);
	private final Object _monitor = new Object();
	private final PhantomGoalStore _goals;
	private final PhantomQuestInstanceCatalog _catalog;
	private final PhantomAcquisitionQuestCatalog _acquisitions;
	private final PhantomQuestInstanceBackend _backend;
	private final PhantomCombatService _combat;
	private final PhantomNavigationService _navigation;
	private final PhantomProgressionService _progression;
	private final PhantomRelevanceSignalPort _signals;
	private final Set<Long> _active = new HashSet<>();
	private final Map<Long, Travel> _travels = new HashMap<>();
	private final Map<Long, CombatOperation> _combats = new HashMap<>();
	private final Map<Long, Long> _signalSequences = new HashMap<>();
	private State _state = State.NEW;

	public PhantomQuestInstanceService(PhantomGoalStore goals, PhantomQuestInstanceCatalog catalog, PhantomAcquisitionQuestCatalog acquisitions, PhantomQuestInstanceBackend backend, PhantomCombatService combat, PhantomNavigationService navigation, PhantomProgressionService progression, PhantomRelevanceSignalPort signals)
	{
		_goals = Objects.requireNonNull(goals, "goals");
		_catalog = Objects.requireNonNull(catalog, "catalog");
		_acquisitions = Objects.requireNonNull(acquisitions, "acquisitions");
		_backend = Objects.requireNonNull(backend, "backend");
		_combat = Objects.requireNonNull(combat, "combat");
		_navigation = Objects.requireNonNull(navigation, "navigation");
		_progression = Objects.requireNonNull(progression, "progression");
		_signals = Objects.requireNonNull(signals, "signals");
	}

	public boolean start()
	{
		_catalog.validateRuntime(_acquisitions);
		synchronized (_monitor)
		{
			if (_state != State.NEW)
			{
				return false;
			}
			_state = State.RUNNING;
			return true;
		}
	}

	public AdvanceResult advance(long profileId, long goalId, long goalRevision, PhantomActivityState activityState, long logicalNowNanos, PhantomCancellationToken token)
	{
		Objects.requireNonNull(activityState, "activityState");
		Objects.requireNonNull(token, "token");
		if (!reserve(profileId))
		{
			return retry("questinstance.operation.busy");
		}
		try
		{
			if (token.isCancelled())
			{
				return new AdvanceResult(Status.CANCELLED, "questinstance.cancelled");
			}
			final PhantomGoal goal = validGoal(profileId, goalId, goalRevision);
			if (goal == null)
			{
				cleanup(profileId);
				return fail("questinstance.goal.stale");
			}
			final Content content = _catalog.content(goal.target().key()).orElse(null);
			if ((content == null) || !goalMatches(goal, content))
			{
				cleanup(profileId);
				return fail("questinstance.content.unsupported");
			}
			if ((goal.deadlineEpochMillis() > 0) && (goal.deadlineEpochMillis() <= System.currentTimeMillis()))
			{
				cleanup(profileId);
				return fail("questinstance.goal.expired");
			}
			if (activityState != PhantomActivityState.ACTIVE)
			{
				publishActive(profileId, logicalNowNanos);
				return retry("questinstance.active.required");
			}
			final Observation observation = _backend.observe(profileId, content);
			if (observation == null)
			{
				publishActive(profileId, logicalNowNanos);
				return retry("questinstance.actor.unavailable");
			}
			final AdvanceResult combatSettlement = settleOwnedCombat(profileId, content);
			if (combatSettlement != null)
			{
				return combatSettlement;
			}
			if (observation.dead() || observation.busy())
			{
				return retry("questinstance.actor.busy");
			}
			return switch (content.kind())
			{
				case QUEST -> "quest.102".equals(content.id()) ? q102(profileId, content, observation, logicalNowNanos, token) : q152(profileId, content, observation, logicalNowNanos, token);
				case CLASS_PATH -> warrior(profileId, content, observation, logicalNowNanos, token);
				case KAMALOKA -> kamaloka(profileId, content, observation, logicalNowNanos, token);
				case PAILAKA -> pailaka(profileId, content, observation, logicalNowNanos, token);
			};
		}
		finally
		{
			release(profileId);
		}
	}

	/** Settles a previously started shared Combat before routing by freshly changed native truth. */
	private AdvanceResult settleOwnedCombat(long profileId, Content content)
	{
		final CombatOperation owned;
		synchronized (_monitor)
		{
			owned = _combats.get(profileId);
		}
		if (owned == null)
		{
			return null;
		}
		final var existing = _combat.find(profileId).orElse(null);
		if (existing == null)
		{
			synchronized (_monitor)
			{
				_combats.remove(profileId, owned);
			}
			return null;
		}
		if (!owned.contentId().equals(content.id()) || (owned.targetObjectId() != existing.targetObjectId()) || !_combat.matchesContentSession(profileId, existing.targetObjectId(), combatOwner(content)))
		{
			return retry("questinstance.combat.foreign");
		}
		if (!existing.result().terminal())
		{
			return retry("questinstance.combat.active");
		}
		final var terminal = _combat.consumeTerminal(profileId).orElse(null);
		if (terminal == null)
		{
			return retry("questinstance.combat.cleanup");
		}
		synchronized (_monitor)
		{
			_combats.remove(profileId, owned);
		}
		return terminal.result() == PhantomCombatResult.VICTORY ? progress("questinstance.combat.victory") : terminal.result() == PhantomCombatResult.CANCELLED ? new AdvanceResult(Status.CANCELLED, "questinstance.combat.cancelled") : retry("questinstance.combat.retry");
	}

	private AdvanceResult q102(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		return switch (observation.quest().status())
		{
			case ABSENT -> interact(profileId, content, "prepare.alberius", observation, now);
			case CREATED -> content.eligible(observation.level(), observation.race(), observation.classId()) ? interact(profileId, content, "start.alberius", observation, now) : fail("questinstance.q102.ineligible");
			case COMPLETED -> complete(profileId, "questinstance.q102.complete");
			case STARTED -> switch (observation.quest().cond())
			{
				case 1 -> interact(profileId, content, "talk.cobendell", observation, now);
				case 2 -> questCombat(profileId, content, observation, "collect", 966, now, token);
				case 3 -> interact(profileId, content, "talk.cobendell", observation, now);
				case 4 -> interact(profileId, content, "report.alberius", observation, now);
				case 5 -> observation.itemCount(1131) > 0 ? interact(profileId, content, "sentinel.berros", observation, now) : observation.itemCount(1132) > 0 ? interact(profileId, content, "sentinel.veltress", observation, now) : observation.itemCount(1133) > 0 ? interact(profileId, content, "sentinel.rayen", observation, now) : observation.itemCount(1134) > 0 ? interact(profileId, content, "sentinel.gartrandell", observation, now) : retry("questinstance.q102.native_transition_pending");
				case 6 -> interact(profileId, content, "complete.alberius", observation, now);
				default -> fail("questinstance.q102.cond.unsupported");
			};
		};
	}

	private AdvanceResult q152(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		return switch (observation.quest().status())
		{
			case ABSENT -> interact(profileId, content, "prepare.harris", observation, now);
			case CREATED -> content.eligible(observation.level(), observation.race(), observation.classId()) ? interact(profileId, content, "start.harris", observation, now) : fail("questinstance.q152.ineligible");
			case COMPLETED -> complete(profileId, "questinstance.q152.complete");
			case STARTED -> switch (observation.quest().cond())
			{
				case 1 -> interact(profileId, content, "talk.altran.begin", observation, now);
				case 2 -> questCombat(profileId, content, observation, "collect", 1010, now, token);
				case 3 -> interact(profileId, content, "talk.altran", observation, now);
				case 4 -> interact(profileId, content, "complete.harris", observation, now);
				default -> fail("questinstance.q152.cond.unsupported");
			};
		};
	}

	private AdvanceResult warrior(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		if ((observation.classId() == 1) && (observation.baseClassId() == 1))
		{
			return complete(profileId, "questinstance.warrior.complete");
		}
		if ((observation.classId() != 0) || (observation.baseClassId() != 0))
		{
			return fail("questinstance.warrior.class.invalid");
		}
		return switch (observation.quest().status())
		{
			case ABSENT -> interact(profileId, content, "prepare.auron", observation, now);
			case CREATED -> content.eligible(observation.level(), observation.race(), observation.classId()) ? interact(profileId, content, "start.auron", observation, now) : fail("questinstance.q401.ineligible");
			case COMPLETED ->
			{
				if ((observation.level() < 20) || (observation.itemCount(1145) <= 0))
				{
					yield fail("questinstance.warrior.prerequisite");
				}
				yield interact(profileId, content, "profession.warrior", observation, now);
			}
			case STARTED -> switch (observation.quest().cond())
			{
				case 1 -> interact(profileId, content, "simplon.begin", observation, now);
				case 2 -> combat(profileId, content, observation, "collect.skeleton", now, token);
				case 3 -> interact(profileId, content, "simplon.handin", observation, now);
				case 4 -> interact(profileId, content, "auron.sword", observation, now);
				case 5 -> observation.activeWeaponItemId() == 1142 ? combat(profileId, content, observation, "collect.spider", now, token) : equip(profileId, content, observation, "equip.sword", token);
				case 6 -> interact(profileId, content, "auron.complete", observation, now);
				default -> fail("questinstance.q401.cond.unsupported");
			};
		};
	}

	private AdvanceResult kamaloka(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		if (observation.instanceReuseUntilMillis() > System.currentTimeMillis())
		{
			return complete(profileId, "questinstance.kamaloka.reuse_observed");
		}
		if ((observation.instanceTemplateId() != 0) && (observation.instanceTemplateId() != content.instanceTemplateId()))
		{
			return fail("questinstance.kamaloka.foreign_instance");
		}
		if (observation.instanceTemplateId() == content.instanceTemplateId())
		{
			return combat(profileId, content, observation, "combat.boss", now, token);
		}
		if (!content.eligible(observation.level(), observation.race(), observation.classId()))
		{
			return fail("questinstance.kamaloka.level");
		}
		if ((observation.partySize() == 0) || !observation.partyLeader() || !observation.partyLevelEligible())
		{
			return retry("questinstance.kamaloka.party_required");
		}
		return interact(profileId, content, "enter", observation, now);
	}

	private AdvanceResult pailaka(long profileId, Content content, Observation observation, long now, PhantomCancellationToken token)
	{
		return switch (observation.quest().status())
		{
			case ABSENT -> interact(profileId, content, "prepare.adler", observation, now);
			case CREATED -> content.eligible(observation.level(), observation.race(), observation.classId()) ? interact(profileId, content, "start.adler", observation, now) : fail("questinstance.pailaka.ineligible");
			case COMPLETED -> complete(profileId, "questinstance.pailaka.complete");
			case STARTED ->
			{
				if ((observation.instanceTemplateId() != 0) && (observation.instanceTemplateId() != content.instanceTemplateId()))
				{
					yield fail("questinstance.pailaka.foreign_instance");
				}
				if (observation.instanceTemplateId() == 0)
				{
					yield observation.quest().cond() == 1 ? interact(profileId, content, "enter", observation, now) : fail("questinstance.pailaka.instance_missing");
				}
				yield switch (observation.quest().cond())
				{
					case 1 -> interact(profileId, content, "sinai.arm", observation, now);
					case 2 -> observation.activeWeaponItemId() == 13034 ? combat(profileId, content, observation, "combat.hillas", now, token) : equip(profileId, content, observation, "equip.sprite", token);
					case 3 -> interact(profileId, content, "inspector.water", observation, now);
					case 4 -> observation.activeWeaponItemId() == 13035 ? combat(profileId, content, observation, "combat.papion", now, token) : equip(profileId, content, observation, "equip.enhanced", token);
					case 5 -> combat(profileId, content, observation, "combat.kinsus", now, token);
					case 6 -> interact(profileId, content, "inspector.fire", observation, now);
					case 7 -> observation.activeWeaponItemId() == 13036 ? combat(profileId, content, observation, "combat.gargos", now, token) : equip(profileId, content, observation, "equip.icefire", token);
					case 8 -> combat(profileId, content, observation, "combat.adiantum", now, token);
					case 9 -> interact(profileId, content, "terminal.adler", observation, now);
					default -> fail("questinstance.pailaka.cond.unsupported");
				};
			}
		};
	}

	private AdvanceResult questCombat(long profileId, Content content, Observation observation, String stepId, int itemId, long now, PhantomCancellationToken token)
	{
		final Rule rule = _acquisitions.rule(content.acquisitionRuleId()).orElse(null);
		if ((rule == null) || (rule.questId() != content.questId()) || !rule.allowedConds().contains(observation.quest().cond()) || (observation.itemCount(itemId) < 0) || (observation.itemCount(itemId) > (rule.itemCap() + 1L)))
		{
			return fail("questinstance.acquisition.binding");
		}
		if ((observation.itemCount(itemId) < rule.itemCap()) && !rule.supports(observation.quest().cond(), observation.itemCount(itemId), rule.targetNpcIds().getFirst(), false))
		{
			return fail("questinstance.acquisition.ineligible");
		}
		return combat(profileId, content, observation, stepId, now, token);
	}

	private AdvanceResult interact(long profileId, Content content, String stepId, Observation observation, long now)
	{
		final Step step = content.step(stepId).orElseThrow();
		final AdvanceResult reach = reach(profileId, content, step, observation, now, INTERACTION_DISTANCE);
		if (reach != null)
		{
			return reach;
		}
		final ActionResult result = _backend.invoke(profileId, content, step, observation.fingerprint());
		return switch (result.status())
		{
			case ISSUED -> progress(result.reasonKey());
			case IDEMPOTENT, UNAVAILABLE -> retry(result.reasonKey());
			case STALE -> progress(result.reasonKey());
			case REJECTED, FAILURE -> fail(result.reasonKey());
		};
	}

	private AdvanceResult equip(long profileId, Content content, Observation observation, String stepId, PhantomCancellationToken token)
	{
		final Step step = content.step(stepId).orElseThrow();
		final int objectId = observation.itemObjectId(step.itemId());
		if (objectId <= 0)
		{
			return fail("questinstance.equip.item_missing");
		}
		final var result = _progression.equipOwnedItem(new EquipItemRequest(profileId, objectId, token));
		return switch (result.status())
		{
			case SUCCESS, IDEMPOTENT -> progress("questinstance.equip.observed");
			case OPERATION_IN_PROGRESS, ACTOR_NOT_MATERIALIZED, ACTOR_STATE_REJECTED -> retry("questinstance.equip.retry");
			case CANCELLED -> new AdvanceResult(Status.CANCELLED, "questinstance.equip.cancelled");
			default -> fail("questinstance.equip.rejected");
		};
	}

	private AdvanceResult combat(long profileId, Content content, Observation observation, String stepId, long now, PhantomCancellationToken token)
	{
		final Step step = content.step(stepId).orElseThrow();
		final CombatOperation owned;
		synchronized (_monitor)
		{
			owned = _combats.get(profileId);
		}
		final var existing = _combat.find(profileId).orElse(null);
		if (existing != null)
		{
			final String owner = combatOwner(content);
			if ((owned == null) || !owned.contentId().equals(content.id()) || !owned.stepId().equals(step.id()) || (owned.targetObjectId() != existing.targetObjectId()) || !_combat.matchesContentSession(profileId, existing.targetObjectId(), owner))
			{
				return retry("questinstance.combat.foreign");
			}
			if (!existing.result().terminal())
			{
				return retry("questinstance.combat.active");
			}
			final var terminal = _combat.consumeTerminal(profileId).orElse(null);
			if (terminal == null)
			{
				return retry("questinstance.combat.cleanup");
			}
			synchronized (_monitor)
			{
				_combats.remove(profileId, owned);
			}
			return terminal.result() == PhantomCombatResult.VICTORY ? progress("questinstance.combat.victory") : terminal.result() == PhantomCombatResult.CANCELLED ? new AdvanceResult(Status.CANCELLED, "questinstance.combat.cancelled") : retry("questinstance.combat.retry");
		}
		final Target target = _backend.locate(profileId, content, step);
		if (target == null)
		{
			return retry("questinstance.combat.target_pending");
		}
		final AdvanceResult reach = reach(profileId, content, step, observation, now, COMBAT_DISTANCE);
		if (reach != null)
		{
			return reach;
		}
		if (!target.liveObject())
		{
			return retry("questinstance.combat.target_pending");
		}
		final String owner = combatOwner(content);
		final var started = _combat.startContentSession(new PhantomCombatRequest(profileId, target.objectId(), PhantomCombatMode.MELEE_PHYSICAL, true, false, COMBAT_TIMEOUT_MILLIS, token), owner);
		if ((started.status() == StartStatus.ACCEPTED) || (started.status() == StartStatus.IDEMPOTENT))
		{
			synchronized (_monitor)
			{
				if ((_combats.size() >= MAXIMUM_OPERATIONS) && !_combats.containsKey(profileId))
				{
					_combat.cancel(profileId);
					return retry("questinstance.operation.capacity");
				}
				_combats.put(profileId, new CombatOperation(content.id(), step.id(), target.objectId()));
			}
			return progress("questinstance.combat.started");
		}
		return switch (started.status())
		{
			case REJECTED_ACTOR, REJECTED_CAPACITY, REJECTED_STATE, REJECTED_EXISTING -> retry("questinstance.combat.retry");
			case CANCELLED -> new AdvanceResult(Status.CANCELLED, "questinstance.combat.cancelled");
			default -> fail("questinstance.combat.rejected." + started.status().name());
		};
	}

	/** Returns null when the exact native target is ready for the requested operation. */
	private AdvanceResult reach(long profileId, Content content, Step step, Observation observation, long now, double requiredDistance)
	{
		final Target target = _backend.locate(profileId, content, step);
		if (target == null)
		{
			return retry("questinstance.target.unavailable");
		}
		if (target.liveObject() && (target.distance() <= requiredDistance))
		{
			cancelTravel(profileId);
			return null;
		}
		Travel travel;
		synchronized (_monitor)
		{
			travel = _travels.get(profileId);
		}
		if ((travel != null) && (!travel.contentId().equals(content.id()) || !travel.stepId().equals(step.id()) || !sameDestination(travel.target(), target)))
		{
			cancelTravel(profileId);
			travel = null;
		}
		if (travel == null)
		{
			final PhantomNavigationPoint origin = new PhantomNavigationPoint(observation.x(), observation.y(), observation.z(), observation.instanceId());
			final PhantomNavigationPoint destination = new PhantomNavigationPoint(target.x(), target.y(), target.z(), target.instanceId());
			final long deadline = now > (Long.MAX_VALUE - NAVIGATION_DEADLINE_NANOS) ? Long.MAX_VALUE : now + NAVIGATION_DEADLINE_NANOS;
			final var submission = _navigation.submit(new PhantomNavigationRequest(profileId, origin, destination, Math.max(0, now), Math.max(now + 1, deadline), 100_000));
			if (submission.status() == SubmissionStatus.REJECTED)
			{
				return retry("questinstance.navigation.busy");
			}
			final List<PhantomNavigationPoint> immediate = (submission.status() == SubmissionStatus.COMPLETED) && (submission.immediateResult() != null) && (submission.immediateResult().route() != null) ? submission.immediateResult().route().waypoints() : List.of();
			travel = new Travel(content.id(), step.id(), target, submission.requestId(), immediate, 0);
			synchronized (_monitor)
			{
				if ((_travels.size() >= MAXIMUM_OPERATIONS) && !_travels.containsKey(profileId))
				{
					_navigation.cancel(profileId, submission.requestId());
					return retry("questinstance.operation.capacity");
				}
				_travels.put(profileId, travel);
			}
			return retry("questinstance.navigation.requested");
		}
		if (travel.waypoints().isEmpty())
		{
			final Optional<PhantomNavigationResult> result = _navigation.consume(travel.requestId());
			if (result.isEmpty())
			{
				return retry("questinstance.navigation.pending");
			}
			if (result.orElseThrow().route() == null)
			{
				cancelTravel(profileId);
				return retry("questinstance.navigation.no_path");
			}
			travel = travel.withWaypoints(result.orElseThrow().route().waypoints());
			synchronized (_monitor)
			{
				_travels.put(profileId, travel);
			}
			return progress("questinstance.navigation.ready");
		}
		final PhantomNavigationPoint waypoint = travel.waypoints().get(travel.waypoint());
		final double dx = (double) waypoint.x() - observation.x();
		final double dy = (double) waypoint.y() - observation.y();
		final double dz = (double) waypoint.z() - observation.z();
		if (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)) <= _navigation.arrivalRadius())
		{
			final int next = travel.waypoint() + 1;
			if (next >= travel.waypoints().size())
			{
				cancelTravel(profileId);
				return progress("questinstance.navigation.arrived");
			}
			travel = travel.withWaypoint(next);
			synchronized (_monitor)
			{
				_travels.put(profileId, travel);
			}
			return progress("questinstance.navigation.waypoint");
		}
		final Target waypointTarget = new Target(0, travel.target().npcId(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.instanceId(), Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)), false);
		final ActionResult moved = _backend.move(profileId, content, step, waypointTarget);
		return switch (moved.status())
		{
			case ISSUED, IDEMPOTENT, UNAVAILABLE -> retry(moved.reasonKey());
			case STALE -> progress(moved.reasonKey());
			case REJECTED, FAILURE -> fail(moved.reasonKey());
		};
	}

	public boolean beginStop()
	{
		final List<Long> profiles;
		synchronized (_monitor)
		{
			if (_state == State.STOPPED)
			{
				return true;
			}
			if (_state == State.NEW)
			{
				_state = State.STOPPED;
				return true;
			}
			_state = State.STOPPING;
			profiles = new ArrayList<>(_travels.keySet());
			profiles.addAll(_combats.keySet().stream().filter(profile -> !profiles.contains(profile)).toList());
		}
		profiles.forEach(this::cleanup);
		return true;
	}

	public boolean finishStop()
	{
		beginStop();
		synchronized (_monitor)
		{
			if (!_active.isEmpty() || !_travels.isEmpty() || !_combats.isEmpty())
			{
				return false;
			}
			_state = State.STOPPED;
			return true;
		}
	}

	public Snapshot snapshot()
	{
		synchronized (_monitor)
		{
			return new Snapshot(_state, _active.size(), _travels.size(), _combats.size(), _signalSequences.size());
		}
	}

	private boolean reserve(long profileId)
	{
		if (profileId <= 0)
		{
			return false;
		}
		synchronized (_monitor)
		{
			return (_state == State.RUNNING) && (_active.size() < MAXIMUM_OPERATIONS) && _active.add(profileId);
		}
	}

	private void release(long profileId)
	{
		synchronized (_monitor)
		{
			_active.remove(profileId);
		}
	}

	private PhantomGoal validGoal(long profileId, long goalId, long goalRevision)
	{
		final PhantomGoal goal = _goals.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		return (goal != null) && (goal.goalId() == goalId) && (goal.revision() == goalRevision) && (goal.status() == PhantomGoalStatus.ACTIVE) && (goal.target() != null) && TARGET_NAMESPACE.equals(goal.target().namespace()) ? goal : null;
	}

	private static boolean goalMatches(PhantomGoal goal, Content content)
	{
		return switch (content.kind())
		{
			case QUEST -> QUEST_GOAL_TYPE.equals(goal.goalType());
			case CLASS_PATH -> CLASS_GOAL_TYPE.equals(goal.goalType());
			case KAMALOKA, PAILAKA -> INSTANCE_GOAL_TYPE.equals(goal.goalType());
		};
	}

	private void publishActive(long profileId, long now)
	{
		final long sequence;
		synchronized (_monitor)
		{
			sequence = Math.max(Math.max(0, now), _signalSequences.getOrDefault(profileId, 0L) + 1);
			_signalSequences.put(profileId, sequence);
		}
		_signals.submit(profileId, new PhantomRelevanceSignal(SIGNAL_SOURCE, sequence, PhantomActivityState.ACTIVE, SIGNAL_TTL_MILLIS));
	}

	private void cleanup(long profileId)
	{
		cancelTravel(profileId);
		final CombatOperation combat;
		final Long sequence;
		synchronized (_monitor)
		{
			combat = _combats.remove(profileId);
			sequence = _signalSequences.remove(profileId);
		}
		if (combat != null)
		{
			_combat.cancel(profileId);
			_combat.consumeTerminal(profileId);
		}
		if (sequence != null)
		{
			_signals.withdraw(profileId, SIGNAL_SOURCE, sequence);
		}
	}

	private void cancelTravel(long profileId)
	{
		final Travel travel;
		synchronized (_monitor)
		{
			travel = _travels.remove(profileId);
		}
		if (travel != null)
		{
			_navigation.cancel(profileId, travel.requestId());
			_navigation.consume(travel.requestId());
		}
	}

	private AdvanceResult complete(long profileId, String reason)
	{
		cleanup(profileId);
		return new AdvanceResult(Status.COMPLETE, reason);
	}

	private static String combatOwner(Content content)
	{
		return "questinstance." + content.id();
	}

	private static boolean sameDestination(Target left, Target right)
	{
		return (left.npcId() == right.npcId()) && (left.instanceId() == right.instanceId()) && (left.x() == right.x()) && (left.y() == right.y()) && (left.z() == right.z());
	}

	private static AdvanceResult progress(String reason)
	{
		return new AdvanceResult(Status.PROGRESS, reason);
	}

	private static AdvanceResult retry(String reason)
	{
		return new AdvanceResult(Status.RETRY, reason);
	}

	private static AdvanceResult fail(String reason)
	{
		return new AdvanceResult(Status.FAIL, reason);
	}

	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum Status
	{
		PROGRESS,
		RETRY,
		COMPLETE,
		FAIL,
		CANCELLED
	}

	public record AdvanceResult(Status status, String reasonKey)
	{
		public AdvanceResult
		{
			Objects.requireNonNull(status, "status");
			if ((reasonKey == null) || reasonKey.isBlank())
			{
				throw new IllegalArgumentException("Reason key must not be blank.");
			}
		}
	}

	public record Snapshot(State state, int activeOperations, int travelOperations, int combatOperations, int relevanceSignals)
	{
	}

	private record Travel(String contentId, String stepId, Target target, long requestId, List<PhantomNavigationPoint> waypoints, int waypoint)
	{
		private Travel
		{
			waypoints = List.copyOf(waypoints);
		}

		private Travel withWaypoints(List<PhantomNavigationPoint> replacement)
		{
			return new Travel(contentId, stepId, target, requestId, replacement, 0);
		}

		private Travel withWaypoint(int replacement)
		{
			return new Travel(contentId, stepId, target, requestId, waypoints, replacement);
		}
	}

	private record CombatOperation(String contentId, String stepId, int targetObjectId)
	{
	}
}

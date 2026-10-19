/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;

/** One persistent child selected by the ordinary farm Decision; QuestInstance remains the executor. */
final class PhantomAutonomousCareer
{
	static final String COMPONENT = "career.intent";
	private static final String CANDIDATE = "candidate.autonomous.career";
	private static final String ACTION = "career.autonomous";
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final PhantomQuestInstanceService _service;
	private final PhantomQuestInstanceCatalog _catalog;
	private final PhantomProgressionService _progression;
	private final BiPredicate<Long, Long> _pauseFarm;
	private final LongPredicate _allowed;
	private final LongFunction<CareerArchetype> _preference;
	private final PhantomAutonomousCareerPolicy _policy;
	private final PhantomGoalStateCodec _codec = new PhantomGoalStateCodec();
	private final Map<Long, Cached> _cache = new LinkedHashMap<>();
	private BiPredicate<Long, PhantomGoal> _defer = (_, _) -> false;
	private final org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction _resumeTransactions = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction();
	private long _resumeCursor, _nextResumeMillis;

	PhantomAutonomousCareer(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomQuestInstanceService service,
		PhantomQuestInstanceCatalog catalog, PhantomProgressionService progression, BiPredicate<Long, Long> pauseFarm, LongPredicate allowed,
		LongFunction<CareerArchetype> preference, PhantomAutonomousCareerPolicy policy)
	{
		_profiles = Objects.requireNonNull(profiles); _materialization = Objects.requireNonNull(materialization); _service = service;
		_catalog = catalog; _progression = progression; _pauseFarm = Objects.requireNonNull(pauseFarm); _allowed = Objects.requireNonNull(allowed);
		_preference = Objects.requireNonNull(preference); _policy = Objects.requireNonNull(policy);
		for (var route : policy.routes())
		{
			final var content = catalog.content(route.contentId()).orElseThrow(() -> new IllegalArgumentException("Career content is unsupported."));
			final var from = progression.catalog().classFact(route.fromClass());
			if (content.kind() != PhantomQuestInstanceCatalog.Kind.CLASS_PATH || !content.classIds().contains(route.fromClass()) || from == null || !from.nextClassIds().contains(route.toClass())) { throw new IllegalArgumentException("Career route is not a native supported class transition."); }
		}
	}

	void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate(CANDIDATE, Set.of(PhantomBackgroundGoalSpec.GOAL_TYPE), Set.copyOf(java.util.EnumSet.allOf(PhantomActivityState.class)), List.of(),
			List.of(new PhantomWeightedConsideration("score.autonomous.career", 1, context ->
			{
				final boolean needed = proposal(context.profileId(), context.goal()).isPresent();
				return new PhantomConsideration.Evaluation(needed ? 1000 : 0, needed ? "career.native.need" : proposalReason(context.profileId()));
			})), 1000, context ->
		{
			final var step = new PhantomPlanStep(0, ACTION, null, Map.of(), 60_000, 10, "career.native.advance");
			return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), CANDIDATE, List.of(step), 60_000, context.logicalNowNanos());
		}));
	}

	void registerHandlers(PhantomStepHandlerRegistry registry) { registry.register(ACTION, this::advance); }
	void deferWhen(BiPredicate<Long, PhantomGoal> predicate) { _defer = Objects.requireNonNull(predicate); }
	boolean needsOrOwns(long profileId, PhantomGoal root) { return proposal(profileId, root).isPresent(); }
	Optional<PhantomGoal> intent(long profileId) { return Optional.ofNullable(cached(profileId).goal); }

	private Optional<PhantomGoal> proposal(long profileId, PhantomGoal root)
	{
		if (!_policy.enabled() || !_allowed.test(profileId) || root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())) { return unavailable(profileId, "career.policy_or_owner"); }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null) { return unavailable(profileId, "career.actor_pending"); }
			final Player player = lease.player();
			if (player.isAlikeDead() || player.isInParty() || player.isTeleporting() || player.hasPendingOwnedStore() || player.getInstanceId() != 0 || player.isChangingClass() || player.isTransformed() || player.isMounted() || player.getClassIndex() != 0)
			{
				return unavailable(profileId, "career.native_guard." + (player.isAlikeDead() ? "dead" : player.isInParty() ? "party" : player.isTeleporting() ? "teleport" : player.hasPendingOwnedStore() ? "store" : player.getInstanceId() != 0 ? "instance" : player.isChangingClass() ? "changing" : player.isTransformed() ? "transformed" : player.isMounted() ? "mounted" : "class_index"));
			}
			final var cached = cached(profileId);
			final long now = System.currentTimeMillis();
			// Finish an admitted native combat/leg before yielding to a critical supply child.
			if (!_service.hasOwnedWork(profileId) && _defer.test(profileId, root)) { return unavailable(profileId, "career.supply_priority"); }
			if (bound(cached.goal, root, player))
			{
				if (cached.goal.status() == PhantomGoalStatus.ACTIVE && cached.goal.deadlineEpochMillis() > now) { return Optional.of(cached.goal); }
				if (cached.goal.constraints().getOrDefault("retry.after", 0L) > now) { return unavailable(profileId, "career.retry_after"); }
			}
			final var archetype = _preference.apply(profileId);
			final var route = _policy.routes().stream().filter(value -> value.fromClass() == player.getActiveClass() && value.fromClass() == player.getBaseClass() && value.archetype() == archetype
				&& _catalog.content(value.contentId()).orElseThrow().eligible(player.getLevel(), player.getRace().name(), player.getActiveClass())).findFirst().orElse(null);
			if (route == null) { return unavailable(profileId, "career.route_absent"); }
			final var content = _catalog.content(route.contentId()).orElseThrow();
			final Map<String, Long> constraints = new LinkedHashMap<>();
			constraints.put("owner.revision", root.revision()); constraints.put("owner.object", (long) player.getObjectId());
			constraints.put("class.from", (long) route.fromClass()); constraints.put("class.to", (long) route.toClass()); constraints.put("class.index", (long) player.getClassIndex());
			constraints.put("field.x", (long) player.getX()); constraints.put("field.y", (long) player.getY()); constraints.put("field.z", (long) player.getZ()); constraints.put("retry.after", 0L);
			final var weapon = player.getActiveWeaponInstance(); constraints.put("weapon.object", weapon == null ? 0L : (long) weapon.getObjectId());
			return Optional.of(new PhantomGoal(root.goalId(), PhantomQuestInstanceService.CLASS_GOAL_TYPE, PhantomGoalStatus.ACTIVE, root.subject(),
				new PhantomDomainRef(PhantomQuestInstanceService.TARGET_NAMESPACE, route.contentId()), 1, 0, null, List.of(), root.selectedAnchor(), "career.autonomous", 700, 0, 0,
				now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), constraints, "career.native.eligible", cached.goal == null ? 0 : Math.addExact(cached.goal.revision(), 1), content.contentHash()));
		}
	}

	private PhantomStepResult advance(PhantomStepContext context)
	{
		if (context.cancellationToken().isCancelled() || !_allowed.test(context.profileId())) { return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, "career.native.cancelled"); }
		final var cached = cached(context.profileId());
		PhantomGoal intent = cached.goal;
		try (var lease = _materialization.tryAcquireAction(context.profileId()).orElse(null))
		{
			if (lease == null) { return PhantomStepResult.retry(500, "career.native.actor_pending"); }
			if (!bound(intent, context.goal(), lease.player()) || intent.status() != PhantomGoalStatus.ACTIVE)
			{
				intent = proposal(context.profileId(), context.goal()).orElse(null);
				if (intent == null) { return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "career.native.no_need"); }
				store(context.profileId(), cached, intent);
			}
		}
		if (!_pauseFarm.test(context.profileId(), intent.deadlineEpochMillis())) { return PhantomStepResult.retry(250, "career.native.finish_current_target"); }
		final var result = _service.advanceIntent(context.profileId(), context.goal(), intent, context.effectiveState(), context.logicalNowNanos(), context.cancellationToken());
		// Save an actually earned native class while its original child/root binding is still current.
		// The existing owned boundary attests Q401; Player remains the sole class/base-class writer.
		try (var lease = _materialization.tryAcquireAction(context.profileId()).orElse(null))
		{
			if (lease != null && lease.player().getActiveClass() == intent.constraints().getOrDefault("class.to", -1L)
				&& lease.player().getBaseClass() == lease.player().getActiveClass() && lease.player().hasOwnedStoreBoundary())
			{
				try { lease.player().storeMe(); }
				catch (RuntimeException failure) { return PhantomStepResult.retry(500, "career.native.store_pending"); }
			}
		}
		if (result.status() == PhantomQuestInstanceService.Status.COMPLETE || result.status() == PhantomQuestInstanceService.Status.FAIL)
		{
			final boolean complete = result.status() == PhantomQuestInstanceService.Status.COMPLETE;
			if (complete) { _progression.observeActor(context.profileId()); }
			final var constraints = new LinkedHashMap<>(intent.constraints());
			constraints.put("retry.after", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis((long) _policy.retrySeconds() * (complete ? 1 : 4)));
			store(context.profileId(), cached, new PhantomGoal(intent.goalId(), intent.goalType(), complete ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.FAILED, intent.subject(), intent.target(),
				intent.requiredAmount(), intent.currentAmount(), intent.acquisitionMethod(), intent.validSources(), intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(),
				intent.deadlineEpochMillis(), constraints, result.reasonKey(), Math.addExact(intent.revision(), 1), intent.payloadText()));
			return PhantomStepResult.of(complete ? PhantomStepResult.Type.SUCCESS : PhantomStepResult.Type.REPLAN, complete ? "career.native.returned_to_life" : result.reasonKey());
		}
		return switch (result.status())
		{
			case PROGRESS -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, result.reasonKey());
			case RETRY -> PhantomStepResult.retry(500, result.reasonKey());
			case CANCELLED -> PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, result.reasonKey());
			default -> throw new IllegalStateException("Unexpected career result.");
		};
	}

	boolean owns(long profileId, PhantomGoal root)
	{
		if (!PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())) { return false; }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null) { return false; }
			final var intent = cached(profileId).goal;
			return bound(intent, root, lease.player()) && intent.status() == PhantomGoalStatus.ACTIVE && intent.deadlineEpochMillis() > System.currentTimeMillis();
		}
	}

	/** Native child work outlives a single Decision plan, but never its current owner or HOLD. */
	boolean permitsOwnedCombat(long profileId, PhantomGoal root, PhantomGoal intent)
	{
		if (!_allowed.test(profileId) || intent.status() != PhantomGoalStatus.ACTIVE || intent.deadlineEpochMillis() <= System.currentTimeMillis()) { return false; }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null || !bound(intent, root, lease.player())) { return false; }
			synchronized (this) { final var current = _cache.get(profileId); return current != null && intent.equals(current.goal); }
		}
	}

	private static boolean bound(PhantomGoal intent, PhantomGoal root, Player player)
	{
		return intent != null && "career.autonomous".equals(intent.purposeKey()) && intent.goalId() == root.goalId()
			&& intent.constraints().getOrDefault("owner.revision", -1L) == root.revision() && intent.constraints().getOrDefault("owner.object", -1L) == player.getObjectId()
			&& intent.constraints().getOrDefault("class.index", -1L) == player.getClassIndex()
			&& (intent.constraints().getOrDefault("class.from", -1L) == player.getActiveClass() || intent.constraints().getOrDefault("class.to", -1L) == player.getActiveClass());
	}

	/** Resume only already native-started work; a sleeping fresh proposal is never activated here. */
	synchronized void resumeAccepted(long now)
	{
		if (!_policy.enabled() || now < _nextResumeMillis) { return; }
		_nextResumeMillis = now + TimeUnit.SECONDS.toMillis(30);
		final var page = _profiles.listManagedAfter(COMPONENT, _resumeCursor, 8);
		_resumeCursor = page.size() < 8 ? 0 : page.getLast().profile().profileId();
		for (var row : page)
		{
			final long id = row.profile().profileId();
			if (row.component().componentSchemaVersion() != 1 || row.profile().characterObjectId() == null || !_allowed.test(id) || _materialization.find(id).isPresent()) { continue; }
			try
			{
				final var intent = _codec.decode(row.component().payload());
				if (intent.status() != PhantomGoalStatus.ACTIVE || intent.deadlineEpochMillis() <= now || !"career.autonomous".equals(intent.purposeKey())
					|| intent.constraints().getOrDefault("owner.object", -1L) != row.profile().characterObjectId().longValue()
					|| intent.constraints().getOrDefault("class.from", -1L) != 0 || intent.constraints().getOrDefault("class.to", -1L) != 1 || intent.constraints().getOrDefault("class.index", -1L) != 0) { continue; }
				final var loaded = _resumeTransactions.load(id);
				if (!loaded.successful() || loaded.state().state() == org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.INCONSISTENT) { continue; }
				final var identity = loaded.state().identity();
				if (identity.characterObjectId() != row.profile().characterObjectId() || identity.classIndex() != 0 || identity.raceOrdinal() != 0 || (identity.activeClassId() != 0 && identity.activeClassId() != 1)) { continue; }
				final var witness = _resumeTransactions.readAcquisitionQuestRows(id, identity.characterObjectId(), 0, identity.activeClassId(), List.of("Q00401_PathOfTheWarrior"), loaded.state().hashes());
				if (!witness.successful()) { continue; }
				final var quest = witness.snapshot().rows().get("Q00401_PathOfTheWarrior");
				final boolean started = "Started".equals(quest.get("<state>")) && Integer.parseInt(quest.getOrDefault("cond", "0")) > 0;
				if ("Completed".equals(quest.get("<state>")) || started) { _service.publishAcceptedResume(id, intent, now); }
			}
			catch (IllegalArgumentException invalid) { /* Malformed persisted input cannot activate a Player. */ }
		}
	}

	private synchronized Cached cached(long profileId)
	{
		var cached = _cache.get(profileId);
		if (cached != null) { return cached; }
		final var component = _profiles.findComponent(profileId, COMPONENT).orElse(null);
		if (component != null && component.componentSchemaVersion() != 1) { throw new IllegalArgumentException("Unknown career.intent component version."); }
		cached = new Cached(component == null ? null : _codec.decode(component.payload()), component == null ? -1 : component.rowVersion());
		if (_cache.size() >= _policy.maximumProfiles()) { _cache.remove(_cache.keySet().iterator().next()); }
		_cache.put(profileId, cached); return cached;
	}

	private void store(long profileId, Cached cached, PhantomGoal intent)
	{
		try
		{
			final var saved = cached.row < 0 ? _profiles.insertComponent(profileId, COMPONENT, 1, _codec.encode(intent)) : _profiles.updateComponent(profileId, COMPONENT, cached.row, 1, _codec.encode(intent));
			cached.goal = intent; cached.row = saved.rowVersion();
		}
		catch (RuntimeException exception) { synchronized (this) { _cache.remove(profileId); } throw exception; }
	}

	private Optional<PhantomGoal> unavailable(long profileId, String reason)
	{
		synchronized (this) { final var cached = _cache.get(profileId); if (cached != null) { cached.proposalReason = reason; } }
		return Optional.empty();
	}
	private synchronized String proposalReason(long profileId)
	{
		final var cached = _cache.get(profileId);
		return cached == null ? "career.actor_or_owner_pending" : cached.proposalReason;
	}
	void clear() { synchronized (this) { _cache.clear(); } }
	private static final class Cached
	{
		private volatile PhantomGoal goal;
		private volatile long row;
		private volatile String proposalReason = "career.unobserved";
		private Cached(PhantomGoal goal, long row) { this.goal = goal; this.row = row; }
	}
}

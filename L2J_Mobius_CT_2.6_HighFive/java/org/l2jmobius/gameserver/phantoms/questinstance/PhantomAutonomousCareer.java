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
import org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;

/** One persistent child selected by the ordinary farm Decision; QuestInstance remains the executor. */
final class PhantomAutonomousCareer
{
	static final String COMPONENT = "career.intent";
	private static final String CANDIDATE = "candidate.autonomous.career";
	private static final String ACTION = "career.autonomous";
	private static final String PREPARATION_RETURN = "career.native.preparation_return.";
	private static final Set<String> RISK_KEYS = Set.of("risk.cell", "risk.preparation", "risk.history");
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
	private BiPredicate<PhantomGoal, Player> _fieldReady = (_, _) -> true;
	private java.util.function.BiFunction<PhantomStepContext, PhantomGoal, PhantomStepResult> _fieldReturn;
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
	void requireField(BiPredicate<PhantomGoal, Player> predicate) { _fieldReady = Objects.requireNonNull(predicate); }
	void useFieldReturn(java.util.function.BiFunction<PhantomStepContext, PhantomGoal, PhantomStepResult> action) { _fieldReturn = Objects.requireNonNull(action); }
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
				if (PhantomJourneyLifetime.alive(cached.goal, now)) { return Optional.of(cached.goal); }
				if (cached.goal.constraints().getOrDefault("retry.after", 0L) > now) { return unavailable(profileId, "career.retry_after"); }
			}
			final var archetype = _preference.apply(profileId);
			if (!_fieldReady.test(root, player)) { return unavailable(profileId, "career.preparation_field_pending"); }
			final var route = _policy.routes().stream().filter(value -> value.fromClass() == player.getActiveClass() && value.fromClass() == player.getBaseClass() && value.archetype() == archetype
				&& _catalog.content(value.contentId()).orElseThrow().eligible(player.getLevel(), player.getRace().name(), player.getActiveClass())).findFirst().orElse(null);
			if (route == null) { return unavailable(profileId, "career.route_absent"); }
			final var content = _catalog.content(route.contentId()).orElseThrow();
			final var previous = cached.goal;
			final boolean sameCareer = previous != null && previous.target() != null && route.contentId().equals(previous.target().key())
				&& previous.constraints().getOrDefault("owner.object", -1L) == player.getObjectId() && previous.constraints().getOrDefault("class.from", -1L) == player.getActiveClass()
				&& previous.constraints().keySet().containsAll(RISK_KEYS);
			if (sameCareer)
			{
				final long prepared = preparation(player), failed = previous.constraints().get("risk.preparation");
				final long failures = previous.constraints().get("risk.history") & 255;
				if (prepared <= failed || failures >= 3 && (prepared >>> 16) <= (failed >>> 16)) { return unavailable(profileId, "career.native.preparation_required"); }
			}
			final Map<String, Long> constraints = new LinkedHashMap<>();
			if (sameCareer) { for (String key : RISK_KEYS) { constraints.put(key, previous.constraints().get(key)); } }
			constraints.put("owner.revision", root.revision()); constraints.put("owner.object", (long) player.getObjectId());
			constraints.put("class.from", (long) route.fromClass()); constraints.put("class.to", (long) route.toClass()); constraints.put("class.index", (long) player.getClassIndex());
			constraints.put("field.x", (long) player.getX()); constraints.put("field.y", (long) player.getY()); constraints.put("field.z", (long) player.getZ()); constraints.put("retry.after", 0L);
			final var weapon = player.getActiveWeaponInstance(); constraints.put("weapon.object", weapon == null ? 0L : (long) weapon.getObjectId());
			return Optional.of(PhantomJourneyLifetime.start(new PhantomGoal(root.goalId(), PhantomQuestInstanceService.CLASS_GOAL_TYPE, PhantomGoalStatus.ACTIVE, root.subject(),
				new PhantomDomainRef(PhantomQuestInstanceService.TARGET_NAMESPACE, route.contentId()), 1, 0, null, List.of(), root.selectedAnchor(), "career.autonomous", 700, 0, 0,
				now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), constraints, "career.native.eligible", cached.goal == null ? 0 : Math.addExact(cached.goal.revision(), 1), content.contentHash()), now));
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
			else if (PhantomJourneyLifetime.paused(intent) && !lease.player().isAlikeDead())
			{
				final var resumed = PhantomJourneyLifetime.resume(intent, System.currentTimeMillis());
				if (!resumed.equals(intent)) { store(context.profileId(), cached, resumed); intent = resumed; }
			}
			noticeNativeDeath(context.profileId(), context.goal(), lease.player());
			intent = cached.goal;
		}
		if (!_pauseFarm.test(context.profileId(), intent.deadlineEpochMillis())) { return PhantomStepResult.retry(250, "career.native.finish_current_target"); }
		if (intent.reasonKey().startsWith(PREPARATION_RETURN)) { return returnForPreparation(context, cached, intent); }
		var result = _service.advanceIntent(context.profileId(), context.goal(), intent, context.effectiveState(), context.logicalNowNanos(), context.cancellationToken());
		if (result.combatFailure() != null)
		{
			try (var lease = _materialization.tryAcquireAction(context.profileId()).orElse(null))
			{
				if (lease == null) { return PhantomStepResult.retry(500, "career.native.failure_store_pending"); }
				recordUnsafe(context.profileId(), context.goal(), lease.player(), result.combatFailure());
			}
			return returnForPreparation(context, cached, cached.goal);
		}
		// Save an actually earned native class while its original child/root binding is still current.
		// The existing owned boundary attests Q401; Player remains the sole class/base-class writer.
		Player earnedClass = null;
		org.l2jmobius.gameserver.model.actor.PlayerNativeWork.Owner earnedOwner = null;
		OperationStatus weaponPending = null;
		try (var lease = _materialization.tryAcquireAction(context.profileId()).orElse(null))
		{
			if (lease != null && lease.player().getActiveClass() == intent.constraints().getOrDefault("class.to", -1L)
				&& lease.player().getBaseClass() == lease.player().getActiveClass())
			{
				earnedClass = lease.player(); earnedOwner = earnedClass.getNativeWorkOwner();
				// Native ClassMaster owns the transfer; align only its transient trainer context afterwards.
				if (earnedClass.getLearningClass() != earnedClass.getPlayerClass())
				{
					earnedClass.setLearningClass(earnedClass.getPlayerClass());
				}
				final long weapon = intent.constraints().getOrDefault("weapon.object", 0L);
				if (weapon > 0 && earnedClass.getActiveWeaponInstance() == null)
				{
					final var status = weapon > Integer.MAX_VALUE ? OperationStatus.INVALID_REQUEST
						: _progression.equipOwnedItem(new EquipItemRequest(context.profileId(), (int) weapon, context.cancellationToken())).status();
					switch (status)
					{
						case SUCCESS, IDEMPOTENT -> { }
						case CANCELLED, SERVICE_NOT_RUNNING, OPERATION_IN_PROGRESS, ACTOR_NOT_MATERIALIZED, ACTOR_STATE_REJECTED -> weaponPending = status;
						default -> result = new PhantomQuestInstanceService.AdvanceResult(PhantomQuestInstanceService.Status.FAIL, "career.native.return_weapon_rejected." + status.name().toLowerCase(java.util.Locale.ROOT));
					}
				}
			}
		}
		// A blocking native checkpoint cannot drain its own ACTION ticket.
		if (earnedClass != null)
		{
			if (earnedOwner == null || !earnedOwner.isCurrent() || earnedClass.getNativeWorkOwner() != earnedOwner) { return PhantomStepResult.retry(500, "career.native.store_owner_pending"); }
			try { if ((_fieldReturn == null || intent.currentAmount() == 0) && earnedClass.hasOwnedStoreBoundary()) { earnedClass.storeMe(); } }
			catch (RuntimeException failure) { return PhantomStepResult.retry(500, "career.native.store_pending"); }
			// Always preserve the earned class checkpoint, even if a separate native equip was refused.
			if (weaponPending == OperationStatus.CANCELLED) { return PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, "career.native.return_weapon_cancelled"); }
			if (weaponPending != null) { return PhantomStepResult.retry(500, "career.native.return_weapon_pending." + weaponPending.name().toLowerCase(java.util.Locale.ROOT)); }
		}
		if (_fieldReturn != null && earnedClass != null && result.status() == PhantomQuestInstanceService.Status.COMPLETE)
		{
			if (intent.currentAmount() == 0)
			{
				intent = new PhantomGoal(intent.goalId(), intent.goalType(), intent.status(), intent.subject(), intent.target(), intent.requiredAmount(), 1, intent.acquisitionMethod(), intent.validSources(), intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(), intent.deadlineEpochMillis(), intent.constraints(), "career.native.class_checkpoint", Math.addExact(intent.revision(), 1), intent.payloadText());
				store(context.profileId(), cached, intent);
			}
			_service.publishAcceptedResume(context.profileId(), intent, System.currentTimeMillis());
			final var recovery = _service.prepareReturn(context.profileId(), context.goal(), intent, context.cancellationToken());
			if (recovery != null) { return recovery.status() == PhantomQuestInstanceService.Status.CANCELLED ? PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, recovery.reasonKey()) : PhantomStepResult.retry(500, recovery.reasonKey()); }
			final var returned = _fieldReturn.apply(context, intent);
			if (returned.type() == PhantomStepResult.Type.FAIL_GOAL) { result = new PhantomQuestInstanceService.AdvanceResult(PhantomQuestInstanceService.Status.FAIL, returned.reasonKey()); }
			else if (returned.type() != PhantomStepResult.Type.COMPLETE_GOAL) { return returned; }
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
			_service.finishAutonomousJourney(context.profileId());
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
		// Preserve the existing HOLD contract: this read reports accepted authority, not permission to act.
		// proposal, advance, requiresNativeContext and permitsOwnedCombat separately retain their _allowed gates.
		if (!_policy.enabled() || root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())) { return false; }
		final var intent = cached(profileId).goal;
		if (!boundRoot(intent, root) || !PhantomJourneyLifetime.alive(intent, System.currentTimeMillis())) { return false; }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease != null) { return bound(intent, root, lease.player()); }
			return _profiles.find(profileId).map(profile -> profile.characterObjectId() != null && intent.constraints().get("owner.object").longValue() == profile.characterObjectId().longValue()).orElse(false);
		}
	}

	boolean requiresNativeContext(long profileId, long rootId, long rootRevision)
	{
		final Cached current; synchronized (this) { current = _cache.get(profileId); }
		final var intent = current == null ? null : current.goal;
		return _policy.enabled() && _allowed.test(profileId) && intent != null && "career.autonomous".equals(intent.purposeKey()) && PhantomJourneyLifetime.alive(intent, System.currentTimeMillis())
			&& (rootId == 0 || intent.goalId() == rootId && intent.constraints().getOrDefault("owner.revision", -1L) == rootRevision);
	}

	void pause(long profileId, PhantomGoal root, Player player)
	{
		noticeNativeDeath(profileId, root, player);
		final var cached = cached(profileId); final var goal = cached.goal;
		if (!bound(goal, root, player)) { return; }
		final var paused = PhantomJourneyLifetime.pause(goal, System.currentTimeMillis());
		if (!paused.equals(goal)) { store(profileId, cached, paused); }
	}

	/** A bounded spatial memory stays in the same compatible Goal component. */
	static long cell(int x, int y) { return ((long) Math.floorDiv(x, 2048) << 32) | (Math.floorDiv(y, 2048) & 0xffffffffL); }
	long avoidedCell(long profileId, String contentId)
	{
		final var intent = cached(profileId).goal;
		return intent != null && intent.target() != null && contentId.equals(intent.target().key()) && intent.constraints().containsKey("risk.history")
			? intent.constraints().getOrDefault("risk.cell", Long.MIN_VALUE) : Long.MIN_VALUE;
	}

	private static long preparation(Player player)
	{
		final long skills = player.getAllSkills().stream().filter(skill -> org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety.supports(skill, org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode.MELEE_PHYSICAL, player.isTransformed())).mapToLong(skill -> skill.getLevel()).sum();
		return ((long) Math.min(127, player.getLevel()) << 56) | (Math.min(0xffffffL, Math.max(0, Math.round(player.getPAtk(null) * 10))) << 32)
			| (Math.min(0xffffL, Math.max(0, Math.round(player.getPDef(null) * 10))) << 16) | (Math.min(2047, skills) << 5)
			| Math.min(31, org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend.ownedHealingStock(player));
	}

	void observeUnsafe(long profileId, PhantomQuestInstanceService.CombatFailure failure)
	{
		final var intent = cached(profileId).goal;
		if (intent == null || intent.status() != PhantomGoalStatus.ACTIVE || intent.reasonKey().startsWith(PREPARATION_RETURN)) { return; }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			final var root = _service.currentRoot(profileId);
			if (lease != null && root != null && lease.player().isAlikeDead()) { recordUnsafe(profileId, root, lease.player(), failure); }
		}
	}

	private void noticeNativeDeath(long profileId, PhantomGoal root, Player player)
	{
		final var intent = cached(profileId).goal;
		if (intent != null && player.getExpBeforeDeath() > player.getExp() && player.getExpBeforeDeath() > (intent.constraints().getOrDefault("risk.history", 0L) >>> 8))
		{
			// Native EXP debt proves a prior death, but its position may already be a recovery point.
			recordUnsafe(profileId, root, player, new PhantomQuestInstanceService.CombatFailure(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult.PLAYER_DEAD, Long.MIN_VALUE, 0));
		}
	}

	void recordUnsafe(long profileId, PhantomGoal root, Player player, PhantomQuestInstanceService.CombatFailure failure)
	{
		final var cached = cached(profileId); final var intent = cached.goal;
		if (!bound(intent, root, player) || intent.status() != PhantomGoalStatus.ACTIVE || intent.currentAmount() != 0 || player.getActiveClass() != intent.constraints().getOrDefault("class.from", -1L)
			|| player.getBaseClass() != player.getActiveClass() || intent.reasonKey().startsWith(PREPARATION_RETURN)) { return; }
		final var constraints = new LinkedHashMap<>(intent.constraints());
		constraints.put("risk.cell", failure.cell()); constraints.put("risk.preparation", preparation(player));
		final long witness = Math.min(Long.MAX_VALUE >>> 8, Math.max(player.getExpBeforeDeath(), intent.constraints().getOrDefault("risk.history", 0L) >>> 8));
		constraints.put("risk.history", (witness << 8) | Math.min(255, (intent.constraints().getOrDefault("risk.history", 0L) & 255) + 1));
		store(profileId, cached, new PhantomGoal(intent.goalId(), intent.goalType(), intent.status(), intent.subject(), intent.target(), intent.requiredAmount(), intent.currentAmount(), intent.acquisitionMethod(), intent.validSources(), intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(), intent.deadlineEpochMillis(), constraints,
			PREPARATION_RETURN + failure.result().name().toLowerCase(java.util.Locale.ROOT), Math.addExact(intent.revision(), 1), intent.payloadText()));
	}

	private PhantomStepResult returnForPreparation(PhantomStepContext context, Cached cached, PhantomGoal intent)
	{
		if (intent == null || !intent.reasonKey().startsWith(PREPARATION_RETURN)) { return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "career.native.preparation_owner_changed"); }
		final var recovery = _service.prepareReturn(context.profileId(), context.goal(), intent, context.cancellationToken());
		if (recovery != null) { return recovery.status() == PhantomQuestInstanceService.Status.CANCELLED ? PhantomStepResult.of(PhantomStepResult.Type.CANCELLED, recovery.reasonKey()) : PhantomStepResult.retry(500, recovery.reasonKey()); }
		_service.publishAcceptedResume(context.profileId(), intent, System.currentTimeMillis());
		final var returned = _fieldReturn == null ? PhantomStepResult.of(PhantomStepResult.Type.FAIL_GOAL, "career.native.preparation_return_unavailable") : _fieldReturn.apply(context, intent);
		if (returned.type() != PhantomStepResult.Type.COMPLETE_GOAL && returned.type() != PhantomStepResult.Type.FAIL_GOAL) { return returned; }
		final var constraints = new LinkedHashMap<>(intent.constraints());
		constraints.put("retry.after", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis((long) _policy.retrySeconds() * 4));
		final String reason = returned.type() == PhantomStepResult.Type.COMPLETE_GOAL ? "career.native.preparation_required." + intent.reasonKey().substring(PREPARATION_RETURN.length()) : returned.reasonKey();
		store(context.profileId(), cached, new PhantomGoal(intent.goalId(), intent.goalType(), PhantomGoalStatus.FAILED, intent.subject(), intent.target(), intent.requiredAmount(), intent.currentAmount(), intent.acquisitionMethod(), intent.validSources(), intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(), intent.deadlineEpochMillis(), constraints, reason, Math.addExact(intent.revision(), 1), intent.payloadText()));
		_service.finishAutonomousJourney(context.profileId());
		return PhantomStepResult.of(PhantomStepResult.Type.REPLAN, reason);
	}

	/** Native child work outlives a single Decision plan, but never its current owner or HOLD. */
	boolean permitsOwnedCombat(long profileId, PhantomGoal root, PhantomGoal intent)
	{
		if (!_allowed.test(profileId) || !PhantomJourneyLifetime.alive(intent, System.currentTimeMillis()) || PhantomJourneyLifetime.paused(intent)) { return false; }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null || !bound(intent, root, lease.player())) { return false; }
			synchronized (this) { final var current = _cache.get(profileId); return current != null && intent.equals(current.goal); }
		}
	}

	private static boolean boundRoot(PhantomGoal intent, PhantomGoal root)
	{
		return intent != null && "career.autonomous".equals(intent.purposeKey()) && intent.goalId() == root.goalId()
			&& java.util.Objects.equals(intent.subject(), root.subject()) && intent.constraints().getOrDefault("owner.revision", -1L) == root.revision() && intent.constraints().getOrDefault("owner.object", -1L) > 0;
	}
	private static boolean bound(PhantomGoal intent, PhantomGoal root, Player player)
	{
		return boundRoot(intent, root) && intent.constraints().getOrDefault("owner.object", -1L) == player.getObjectId()
			&& intent.constraints().getOrDefault("class.index", -1L) == player.getClassIndex()
			&& (intent.constraints().getOrDefault("class.from", -1L) == player.getActiveClass() || intent.constraints().getOrDefault("class.to", -1L) == player.getActiveClass());
	}

	/** Resume an accepted compatible child; this never proposes new work for a stored Player. */
	synchronized void resumeAccepted(long now)
	{
		if (!_policy.enabled() || now < _nextResumeMillis) { return; }
		_nextResumeMillis = now + TimeUnit.SECONDS.toMillis(30);
		final var page = _profiles.listManagedAfter(COMPONENT, _resumeCursor, 8);
		_resumeCursor = page.size() < 8 ? 0 : page.getLast().profile().profileId();
		for (var row : page)
		{
			final long id = row.profile().profileId();
			if (row.component().componentSchemaVersion() != 1 || row.profile().characterObjectId() == null || !_allowed.test(id) || _materialization.find(id).map(entry -> entry.worldPresent()).orElse(false)) { continue; }
			try
			{
				final var intent = _codec.decode(row.component().payload());
				if (!PhantomJourneyLifetime.alive(intent, now) || !"career.autonomous".equals(intent.purposeKey())
					|| intent.constraints().getOrDefault("owner.object", -1L) != row.profile().characterObjectId().longValue()
					|| intent.constraints().getOrDefault("class.from", -1L) != 0 || intent.constraints().getOrDefault("class.to", -1L) != 1 || intent.constraints().getOrDefault("class.index", -1L) != 0) { continue; }
				if (!_cache.containsKey(id) && _cache.size() >= _policy.maximumProfiles()) { _cache.remove(_cache.keySet().iterator().next()); }
				_cache.put(id, new Cached(intent, row.component().rowVersion()));
				final var loaded = _resumeTransactions.load(id);
				if (!loaded.successful() || loaded.state().state() == org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.INCONSISTENT) { continue; }
				final var identity = loaded.state().identity();
				if (identity.characterObjectId() != row.profile().characterObjectId() || identity.classIndex() != 0 || identity.raceOrdinal() != 0 || (identity.activeClassId() != 0 && identity.activeClassId() != 1)) { continue; }
				final var witness = _resumeTransactions.readAcquisitionQuestRows(id, identity.characterObjectId(), 0, identity.activeClassId(), List.of("Q00401_PathOfTheWarrior"), loaded.state().hashes());
				if (!witness.successful()) { continue; }
				final var quest = witness.snapshot().rows().get("Q00401_PathOfTheWarrior");
				final boolean started = "Started".equals(quest.get("<state>")) && Integer.parseInt(quest.getOrDefault("cond", "0")) > 0;
				// Legacy intent alone cannot prove a fresh accepted trip. Valid finite acceptance survives
				// the calendar boundary; root/class/object/quest witnesses are rechecked before publication.
				final boolean acceptedTrip = intent.constraints().keySet().containsAll(PhantomJourneyLifetime.KEYS)
					&& (quest.isEmpty() || "Created".equals(quest.get("<state>")));
				if (acceptedTrip || "Completed".equals(quest.get("<state>")) || started) { _service.publishAcceptedResume(id, intent, now); }
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

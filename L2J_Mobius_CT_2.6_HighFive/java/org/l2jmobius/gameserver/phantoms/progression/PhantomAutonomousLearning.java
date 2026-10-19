/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.model.zone.ZoneId;
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
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;

/** One durable child intent in the existing farm Decision; no separate pulse or Player registry. */
final class PhantomAutonomousLearning
{
	static final String COMPONENT = "learning.intent";
	private static final String CANDIDATE = "candidate.autonomous.learning";
	private static final String ACTION = "learning.autonomous";
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final PhantomProgressionService _progression;
	private final L2jPhantomLearningJourney _journey;
	private final BiPredicate<Long, Long> _pauseFarm;
	private final LongPredicate _allowed;
	private final PhantomAutonomousLearningPolicy _policy;
	private final PhantomGoalStateCodec _codec = new PhantomGoalStateCodec();
	private final Map<Long, Cached> _cache = new LinkedHashMap<>();
	private BiPredicate<Long, PhantomGoal> _defer = (_, _) -> false;
	private BiPredicate<PhantomGoal, Player> _fieldReady = (_, _) -> true;
	private final org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction _resumeTransactions = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction();
	private long _resumeCursor, _nextResumeMillis;

	PhantomAutonomousLearning(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomProgressionService progression, L2jPhantomLearningJourney journey, BiPredicate<Long, Long> pauseFarm, LongPredicate allowed, PhantomAutonomousLearningPolicy policy)
	{
		_profiles = Objects.requireNonNull(profiles);
		_materialization = materialization;
		_progression = progression;
		_journey = journey;
		_pauseFarm = Objects.requireNonNull(pauseFarm);
		_allowed = allowed;
		_policy = Objects.requireNonNull(policy);
	}

	void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate(CANDIDATE, Set.of(PhantomBackgroundGoalSpec.GOAL_TYPE), Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.NEARBY_PERCEPTIBLE), List.of(), List.of(new PhantomWeightedConsideration("score.autonomous.learning", 1, context -> new PhantomConsideration.Evaluation(proposal(context.profileId(), context.goal()).isPresent() ? 1000 : 0, "learning.native.need"))), 1000, context ->
		{
			final var step = new PhantomPlanStep(0, ACTION, null, Map.of(), 60_000, 10, "learning.native.advance");
			return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), CANDIDATE, List.of(step), 60_000, context.logicalNowNanos());
		}));
	}

	void registerHandlers(PhantomStepHandlerRegistry registry) { registry.register(ACTION, this::advance); }
	void deferWhen(BiPredicate<Long, PhantomGoal> predicate) { _defer = Objects.requireNonNull(predicate); }
	void requireField(BiPredicate<PhantomGoal, Player> predicate) { _fieldReady = Objects.requireNonNull(predicate); }
	boolean needsOrOwns(long profileId, PhantomGoal root) { return proposal(profileId, root).isPresent(); }

	private Optional<PhantomGoal> proposal(long profileId, PhantomGoal root)
	{
		if (!_policy.enabled() || !_allowed.test(profileId) || root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())) { return Optional.empty(); }
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (action == null) { return Optional.empty(); }
			final Player player = action.player();
			final Cached cached = cached(profileId);
			final long now = System.currentTimeMillis();
			if (bound(cached.goal, root, player))
			{
				if (PhantomJourneyLifetime.continuing(cached.goal)) { return Optional.of(cached.goal); }
				final var returning = _journey.failedReturnOffer(cached.goal, player);
				if (returning.isPresent()) { return returning; }
				if (cached.goal.constraints().getOrDefault("retry.after", 0L) > now) { return Optional.empty(); }
			}
			if (player.isAlikeDead() || player.isInParty() || player.isTeleporting() || player.hasPendingOwnedStore() || player.getInstanceId() != 0 || player.isChangingClass() || player.isTransformed() || player.isMounted()) { return Optional.empty(); }
			// A future need may be selected during farming; its executor still waits for native combat to settle.
			if (_defer.test(profileId, root)) { return Optional.empty(); }
			if (player.isInsideZone(ZoneId.PEACE)) { return Optional.empty(); }
			if (!_fieldReady.test(root, player)) { return Optional.empty(); }
			final String facts = root.goalId() + ":" + root.revision() + ':' + player.getObjectId() + ':' + player.getActiveClass() + ':' + player.getClassIndex() + ':' + player.getLevel() + ':' + player.getSp();
			if (facts.equals(cached.facts) && cached.nextCheck > now) { return Optional.ofNullable(cached.offer); }
			cached.facts = facts;
			cached.nextCheck = now + TimeUnit.SECONDS.toMillis(_policy.retrySeconds());
			cached.offer = null;
			final var learn = SkillTreeData.getInstance().getAvailableSkills(player, player.getLearningClass(), false, false).stream().filter(value -> eligible(player, value)).sorted(Comparator.comparingInt((SkillLearn value) -> usefulness(value)).reversed().thenComparingInt(value -> value.getCalculatedLevelUpSp(player.getPlayerClass(), player.getLearningClass())).thenComparingInt(SkillLearn::getSkillId)).findFirst().orElse(null);
			if (learn == null) { return Optional.empty(); }
			final Map<String, Long> constraints = new LinkedHashMap<>();
			constraints.put("class.id", (long) player.getActiveClass()); constraints.put("class.index", (long) player.getClassIndex());
			constraints.put("field.x", (long) player.getX()); constraints.put("field.y", (long) player.getY()); constraints.put("field.z", (long) player.getZ()); constraints.put("field.instance", 0L);
			constraints.put("owner.revision", root.revision()); constraints.put("owner.object", (long) player.getObjectId()); constraints.put("retry.after", 0L);
			cached.offer = PhantomJourneyLifetime.start(new PhantomGoal(root.goalId(), L2jPhantomLearningJourney.GOAL_TYPE, PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef("skill", learn.getSkillId() + ":" + learn.getSkillLevel()), 1, 0, null, List.of(), root.selectedAnchor(), "learning.autonomous", 600, 0, 0, now + _journey.roundTripBudgetMillis(player, TimeUnit.MINUTES.toMillis(_policy.journeyMinutes())), constraints, "learning.native.affordable", cached.goal == null ? 0 : Math.addExact(cached.goal.revision(), 1)), now);
			return Optional.of(cached.offer);
		}
	}

	private boolean eligible(Player player, SkillLearn learn)
	{
		final int cost = learn.getCalculatedLevelUpSp(player.getPlayerClass(), player.getLearningClass());
		if (!learn.isLearnedByNpc() || cost <= 0 || cost > player.getSp() - _policy.minimumSpReserve() || usefulness(learn) <= 0 || learn.getPreReqSkills().stream().anyMatch(required -> player.getSkillLevel(required.getSkillId()) < required.getSkillLevel())) { return false; }
		return learn.getRequiredItems().size() <= 1 && learn.getRequiredItems().stream().allMatch(required -> player.getInventory().getAllItemsByItemId(required.getId(), false).stream().anyMatch(item -> item.getOwnerId() == player.getObjectId() && item.getItemLocation() == org.l2jmobius.gameserver.model.item.enums.ItemLocation.INVENTORY && item.getCount() >= required.getCount()));
	}

	private int usefulness(SkillLearn learn)
	{
		final var fact = _progression.catalog().skill(new SkillRef(learn.getSkillId(), learn.getSkillLevel()));
		if (fact == null || fact.suicideAttack() || fact.transformation() || fact.pvpOnly() || fact.toggle()) { return 0; }
		return fact.damage() ? 500 : fact.heal() ? 300 : fact.buff() ? 200 : fact.passive() ? 100 : 0;
	}

	private PhantomStepResult advance(PhantomStepContext context)
	{
		if (context.cancellationToken().isCancelled() || !_allowed.test(context.profileId())) { return PhantomStepResult.of(Type.CANCELLED, "learning.native.cancelled"); }
		final Cached cached = cached(context.profileId());
		PhantomGoal intent = cached.goal;
		try (var action = _materialization.tryAcquireAction(context.profileId()).orElse(null))
		{
			if (action == null) { return PhantomStepResult.retry(500, "learning.native.actor_pending"); }
			if (!bound(intent, context.goal(), action.player()) || !PhantomJourneyLifetime.continuing(intent))
			{
				intent = proposal(context.profileId(), context.goal()).orElse(null);
				if (intent == null) { return PhantomStepResult.of(Type.REPLAN, "learning.native.no_need"); }
				if (!bound(intent, context.goal(), action.player())) { return PhantomStepResult.of(Type.REPLAN, "learning.native.stale_offer"); }
				store(context.profileId(), cached, intent);
			}
			else if (PhantomJourneyLifetime.paused(intent) && !action.player().isAlikeDead())
			{
				final var resumed = PhantomJourneyLifetime.resume(intent, System.currentTimeMillis());
				if (!resumed.equals(intent)) { store(context.profileId(), cached, resumed); intent = resumed; }
			}
		}
		final PhantomStepResult result;
		if (!PhantomJourneyLifetime.alive(intent, System.currentTimeMillis())) { result = PhantomStepResult.of(Type.FAIL_GOAL, "learning.native.deadline"); }
		else
		{
			_journey.accepted(context.profileId(), context.goal(), intent);
			if (!_pauseFarm.test(context.profileId(), intent.deadlineEpochMillis())) { return PhantomStepResult.retry(250, "learning.native.finish_current_target"); }
			result = _journey.advanceIntent(context.profileId(), context.goal(), intent, context.cancellationToken(), context.logicalNowNanos());
		}
		if (result.type() == Type.COMPLETE_GOAL || result.type() == Type.FAIL_GOAL)
		{
			final boolean settling = PhantomJourneyLifetime.returningFailure(intent);
			final Map<String, Long> constraints = new LinkedHashMap<>(intent.constraints());
			constraints.put("retry.after", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis((long) _policy.retrySeconds() * (result.type() == Type.COMPLETE_GOAL ? 1 : 4)));
			if (settling) { constraints.put("journey.return", 2L); }
			final var terminal = new PhantomGoal(intent.goalId(), intent.goalType(), !settling && result.type() == Type.COMPLETE_GOAL ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.FAILED, intent.subject(), intent.target(), intent.requiredAmount(), intent.currentAmount(), intent.acquisitionMethod(), intent.validSources(), intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(), intent.deadlineEpochMillis(), constraints, settling ? intent.reasonKey() : result.reasonKey(), Math.addExact(intent.revision(), 1));
			store(context.profileId(), cached, terminal);
			_journey.finished(context.profileId());
			if (!settling && result.type() == Type.FAIL_GOAL)
			{
				try (var action = _materialization.tryAcquireAction(context.profileId()).orElse(null))
				{
					final var returning = action == null ? Optional.<PhantomGoal>empty() : _journey.failedReturnOffer(terminal, action.player());
					if (returning.isPresent()) { store(context.profileId(), cached, returning.orElseThrow()); _journey.accepted(context.profileId(), context.goal(), cached.goal); }
				}
			}
			return PhantomStepResult.of(result.type() == Type.COMPLETE_GOAL ? Type.SUCCESS : Type.REPLAN, result.type() == Type.COMPLETE_GOAL ? settling ? "learning.native.failed_returned_to_farm" : "learning.native.returned_to_farm" : result.reasonKey());
		}
		return result;
	}

	boolean owns(long profileId, PhantomGoal root)
	{
		return _allowed.test(profileId) && protectsRoot(profileId, root);
	}

	/** Observational acceptance survives HOLD; this never permits actions, materialization or payment. */
	boolean protectsRoot(long profileId, PhantomGoal root)
	{
		if (!_policy.enabled() || root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())) { return false; }
		final var goal = cached(profileId).goal;
		if (goal == null || !PhantomJourneyLifetime.alive(goal, System.currentTimeMillis()) || !boundRoot(goal, root)) { return false; }
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (action != null) { return bound(goal, root, action.player()); }
			return _profiles.find(profileId).map(profile -> profile.characterObjectId() != null && goal.constraints().get("owner.object").longValue() == profile.characterObjectId().longValue()).orElse(false);
		}
	}

	boolean permitsSettlement(long profileId, PhantomGoal root)
	{
		if (!_allowed.test(profileId) || root.status() != PhantomGoalStatus.ACTIVE) { return false; }
		final var goal = cached(profileId).goal;
		if (goal == null || !boundRoot(goal, root)) { return false; }
		if (PhantomJourneyLifetime.continuing(goal)) { return true; }
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null)) { return action != null && bound(goal, root, action.player()) && _journey.failedReturnOffer(goal, action.player()).isPresent(); }
	}

	void pause(long profileId, PhantomGoal root, Player player)
	{
		final var cached = cached(profileId); final var goal = cached.goal;
		if (!bound(goal, root, player)) { return; }
		final var paused = PhantomJourneyLifetime.pause(goal, System.currentTimeMillis());
		if (!paused.equals(goal)) { store(profileId, cached, paused); }
	}

	private static boolean boundRoot(PhantomGoal goal, PhantomGoal root)
	{
		return goal != null && "learning.autonomous".equals(goal.purposeKey()) && goal.goalId() == root.goalId() && java.util.Objects.equals(goal.subject(), root.subject())
			&& goal.constraints().getOrDefault("owner.revision", -1L) == root.revision() && goal.constraints().getOrDefault("owner.object", -1L) > 0;
	}

	Optional<PhantomGoal> intent(long profileId) { return Optional.ofNullable(cached(profileId).goal); }

	/** Bounded cursor work on the existing shared scheduler, including already stored native identities. */
	synchronized void resumeAccepted(long now)
	{
		if (!_policy.enabled() || now < _nextResumeMillis) { return; }
		_nextResumeMillis = now + 30_000;
		final var page = _profiles.listManagedAfter(COMPONENT, _resumeCursor, 8);
		_resumeCursor = page.size() < 8 ? 0 : page.getLast().profile().profileId();
		for (var row : page)
		{
			final long id = row.profile().profileId();
			if (row.component().componentSchemaVersion() != 1 || row.profile().characterObjectId() == null || !_allowed.test(id)) { continue; }
			try
			{
				final var intent = cached(id).goal; final var root = _journey.root(id);
				if (root == null || root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType()) || !boundRoot(intent, root)
					|| !PhantomJourneyLifetime.alive(intent, now) || L2jPhantomLearningJourney.request(intent).isEmpty() || intent.constraints().get("owner.object").longValue() != row.profile().characterObjectId().longValue()) { continue; }
				try (var action = _materialization.tryAcquireAction(id).orElse(null))
				{
					if (action != null)
					{
						if (!bound(intent, root, action.player())) { continue; }
						if (action.player().isAlikeDead()) { pause(id, root, action.player()); }
						_journey.accepted(id, root, cached(id).goal); continue;
					}
				}
				final var loaded = _resumeTransactions.load(id);
				if (!loaded.successful() || loaded.state().state() == org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.INCONSISTENT) { continue; }
				final var identity = loaded.state().identity();
				if (identity.characterObjectId() != row.profile().characterObjectId() || identity.activeClassId() != intent.constraints().get("class.id").intValue() || identity.classIndex() != intent.constraints().get("class.index").intValue()) { continue; }
				_journey.accepted(id, root, intent);
			}
			catch (IllegalArgumentException invalid) { /* Invalid saved input never requests a Player or changes canonical state. */ }
		}
	}

	private static boolean bound(PhantomGoal goal, PhantomGoal root, Player player)
	{
		return boundRoot(goal, root) && goal.constraints().getOrDefault("owner.object", -1L) == player.getObjectId() && goal.constraints().getOrDefault("class.id", -1L) == player.getActiveClass() && goal.constraints().getOrDefault("class.index", -1L) == player.getClassIndex();
	}

	private synchronized Cached cached(long profileId)
	{
		Cached cached = _cache.get(profileId);
		if (cached != null) { return cached; }
		final PhantomProfileComponent component = _profiles.findComponent(profileId, COMPONENT).orElse(null);
		if (component != null && component.componentSchemaVersion() != 1) { throw new IllegalArgumentException("Unknown learning.intent component version."); }
		cached = new Cached(component == null ? null : _codec.decode(component.payload()), component == null ? -1 : component.rowVersion());
		if (_cache.size() >= _policy.maximumProfiles()) { _cache.remove(_cache.keySet().iterator().next()); }
		_cache.put(profileId, cached);
		return cached;
	}

	private void store(long profileId, Cached cached, PhantomGoal goal)
	{
		try
		{
			final var stored = cached.row < 0 ? _profiles.insertComponent(profileId, COMPONENT, 1, _codec.encode(goal)) : _profiles.updateComponent(profileId, COMPONENT, cached.row, 1, _codec.encode(goal));
			cached.goal = goal; cached.row = stored.rowVersion(); cached.offer = null;
		}
		catch (RuntimeException exception) { synchronized (this) { _cache.remove(profileId); } throw exception; }
	}

	synchronized void clear() { _cache.clear(); }

	private static final class Cached
	{
		private volatile PhantomGoal goal;
		private volatile PhantomGoal offer;
		private volatile long row;
		private String facts = "";
		private long nextCheck;
		private Cached(PhantomGoal goal, long row) { this.goal = goal; this.row = row; }
	}
}

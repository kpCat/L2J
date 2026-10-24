package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig.AutonomousPolicy;
import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.model.zone.type.PeaceZone;
import org.l2jmobius.gameserver.model.zone.type.TownZone;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityTransitionStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;

/** One shared-scheduler owner for bounded ordinary native stores around a planned rest window. */
public final class PhantomAutonomousMarketProducer implements PhantomSchedulerControlPort
{
	private static final long ATTEMPT_INTERVAL_MILLIS = 15_000;
	private static final long ROTATION_INTERVAL_MILLIS = 900_000;
	private static final int MAXIMUM_SELL_CANDIDATES = 24;
	private static final int MAXIMUM_LINE_COUNT = 8;
	private static final int MAXIMUM_STORE_LINES = 20;
	private static final Comparator<Item> SELL_ORDER = Comparator.comparingInt((Item item) -> item.getTemplate().getItemType() == EtcItemType.MATERIAL ? 0 : 1).thenComparingInt(Item::getId).thenComparingInt(Item::getObjectId);

	private final PhantomMaterializationService _materialization;
	private final PhantomScheduler _scheduler;
	private final PhantomStoreService _stores;
	private final PhantomGoalStateStore _goals;
	private final PhantomAcquisitionStore _acquisition;
	private final PhantomEconomyReservationService _reservations;
	private final Supplier<PhantomDecisionEngine> _decision;
	private final AutonomousPolicy _policy;
	private final Map<Long, Opened> _opened = new HashMap<>();
	private final Map<Long, Long> _lastClosed = new HashMap<>();
	private long _nextAttempt;
	private long _recoveryCursor;
	private int _cursor;
	private boolean _stopping;
	private BiPredicate<Long, Long> _tradingWindow = (_, _) -> true;
	private boolean _windowInstalled;
	private boolean _admissionStarted;

	public PhantomAutonomousMarketProducer(PhantomMaterializationService materialization, PhantomScheduler scheduler, PhantomStoreService stores, PhantomGoalStateStore goals, PhantomAcquisitionStore acquisition, PhantomEconomyReservationService reservations, Supplier<PhantomDecisionEngine> decision, AutonomousPolicy policy)
	{
		_materialization = Objects.requireNonNull(materialization);
		_scheduler = Objects.requireNonNull(scheduler);
		_stores = Objects.requireNonNull(stores);
		_goals = Objects.requireNonNull(goals);
		_acquisition = Objects.requireNonNull(acquisition);
		_reservations = Objects.requireNonNull(reservations);
		_decision = Objects.requireNonNull(decision);
		_policy = Objects.requireNonNull(policy);
	}

	@Override
	public void onPulse()
	{
		pulse(System.currentTimeMillis());
	}

	public synchronized void installTradingWindow(BiPredicate<Long, Long> tradingWindow)
	{
		if (_windowInstalled || _admissionStarted || _stopping) { throw new IllegalStateException("Trading window must be installed once before ordinary market admission."); }
		_tradingWindow = Objects.requireNonNull(tradingWindow);
		_windowInstalled = true;
	}

	/** Waits for an in-flight producer pulse before the StoreService drain begins. */
	public synchronized void beginStop()
	{
		_stopping = true;
	}

	/** The native owner and accepted live state are rechecked on every attempt. */
	public synchronized void pulse(long now)
	{
		_admissionStarted = true;
		if (_stopping)
		{
			return;
		}
		for (long profileId : List.copyOf(_opened.keySet()))
		{
			reconcile(profileId, now);
		}
		if (now < _nextAttempt)
		{
			return;
		}
		_nextAttempt = now + ATTEMPT_INTERVAL_MILLIS;
		for (long profileId : List.copyOf(_opened.keySet()))
		{
			reconcileSlow(profileId, now);
		}
		final List<Long> retainedOwners = _stores.planOwnersAfter(_recoveryCursor, _policy.maximumOpenStores());
		if (retainedOwners.isEmpty())
		{
			_recoveryCursor = 0;
		}
		for (long profileId : retainedOwners)
		{
			_recoveryCursor = profileId;
			if (!_opened.containsKey(profileId))
			{
				consider(profileId, now);
			}
		}
		final var actors = _materialization.list();
		if (actors.isEmpty())
		{
			return;
		}
		final int active = (int) actors.stream().filter(actor -> stableActive(actor.profileId())).count();
		for (int inspected = 0; inspected < actors.size(); inspected++)
		{
			final long profileId = actors.get(Math.floorMod(_cursor++, actors.size())).profileId();
			if (!_opened.containsKey(profileId) && stableActive(profileId) && (Math.floorMod(profileId + (now / ROTATION_INTERVAL_MILLIS), 2) == 0))
			{
				consider(profileId, active, now);
				break;
			}
		}
		_lastClosed.entrySet().removeIf(entry -> (now - entry.getValue()) > (_policy.reopenCooldownSeconds() * 1000L));
	}

	/** Exposed for a focused composed gate; production calls it from the shared pulse. */
	public synchronized Optional<PhantomStorePlan> consider(long profileId, long now)
	{
		_admissionStarted = true;
		if (_stopping)
		{
			return Optional.empty();
		}
		final int active = (int) _materialization.list().stream().filter(actor -> stableActive(actor.profileId())).count();
		return consider(profileId, active, now);
	}

	private Optional<PhantomStorePlan> consider(long profileId, int active, long now)
	{
		if (_stopping || !OfflineTradeConfig.OFFLINE_TRADE_ENABLE || !_tradingWindow.test(profileId, now))
		{
			return Optional.empty();
		}
		final int participationCap = active < 2 ? 0 : Math.max(1, active / 3);
		final Player owner = liveOwner(profileId);
		final var retained = _stores.currentPlan(profileId).orElse(null);
		if (retained != null)
		{
			final PhantomGoal current = goal(profileId);
			final PhantomAcquisitionState state = acquisition(profileId, current);
			if ((retained.expiresEpochMillis() <= now) || (owner == null) || !stableActive(profileId) || !retainedEligible(owner, retained, current, state) || owner.isInStoreMode() || !ownerReady(profileId, owner) || _reservations.findActive(profileId).isPresent())
			{
				close(profileId, now);
				return Optional.empty();
			}
			if (_opened.size() >= Math.min(_policy.maximumOpenStores(), participationCap))
			{
				return Optional.empty();
			}
			if (_stores.restore(profileId, PhantomActivityState.ACTIVE, now) == PhantomStoreService.Result.OPENED)
			{
				track(profileId, retained, current);
				return Optional.of(retained);
			}
			close(profileId, now);
			return Optional.empty();
		}
		if (!stableActive(profileId) || (owner == null) || (_opened.size() >= Math.min(_policy.maximumOpenStores(), participationCap)) || (Math.floorMod(profileId + (now / ROTATION_INTERVAL_MILLIS), 2) != 0) || ((now - _lastClosed.getOrDefault(profileId, Long.MIN_VALUE / 2)) < (_policy.reopenCooldownSeconds() * 1000L)))
		{
			return Optional.empty();
		}
		if (!ownerReady(profileId, owner))
		{
			return Optional.empty();
		}
		if (_reservations.findActive(profileId).isPresent())
		{
			return Optional.empty();
		}
		final PhantomGoal current = goal(profileId);
		final PhantomAcquisitionState state = acquisition(profileId, current);
		final Optional<PhantomStorePlan> buy = buy(profileId, owner, current, state, now);
		if (buy.isPresent() && publish(profileId, owner, current, buy.get(), now))
		{
			return buy;
		}
		final Optional<PhantomStorePlan> manufacture = manufacture(profileId, owner, current, state, now);
		if (manufacture.isPresent() && publish(profileId, owner, current, manufacture.get(), now)) { return manufacture; }
		final Optional<PhantomStorePlan> sell = sell(profileId, owner, current, state, now);
		return sell.isPresent() && publish(profileId, owner, current, sell.get(), now) ? sell : Optional.empty();
	}

	private Optional<PhantomStorePlan> buy(long profileId, Player owner, PhantomGoal goal, PhantomAcquisitionState state, long now)
	{
		if ((goal == null) || (state == null) || (state.selectedSource() == null) || (state.phase() != PhantomAcquisitionState.Phase.NONE) || (state.status() != PhantomAcquisitionState.Status.READY && state.status() != PhantomAcquisitionState.Status.PLANNING_ONLY))
		{
			return Optional.empty();
		}
		final List<PhantomAcquisitionState.Deficit> deficits = state.recipePlan() == null ? List.of() : state.recipePlan().deficits().stream().sorted(Comparator.comparingInt(PhantomAcquisitionState.Deficit::itemId)).limit(1).toList();
		for (PhantomAcquisitionState.Deficit deficit : deficits)
		{
			final long owned = ownedCount(owner, deficit.itemId());
			final Optional<PhantomStorePlan> plan = quoteBuy(profileId, owner, deficit.itemId(), Math.max(0, deficit.count() - owned), now);
			if (plan.isPresent())
			{
				return plan;
			}
		}
		final long owned = ownedCount(owner, state.targetItemId());
		final long remaining = state.requiredAmount() - PhantomAcquisitionState.observedProgress(state.baselineCount(), owned, state.requiredAmount());
		return quoteBuy(profileId, owner, state.targetItemId(), remaining, now);
	}

	private Optional<PhantomStorePlan> quoteBuy(long profileId, Player owner, int itemId, long need, long now)
	{
		if ((need <= 0) || (owner.getAdena() <= 0))
		{
			return Optional.empty();
		}
		final long expiry = now + (_policy.lifetimeSeconds() * 1000L);
		final var raw = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Покупаю ресурсы", List.of(new PhantomStorePlan.Line(itemId, itemId, 1, 0)), expiry);
		final var first = _stores.quotePlan(profileId, PhantomActivityState.ACTIVE, raw, now).orElse(null);
		if (first == null)
		{
			return Optional.empty();
		}
		final long bid = first.lines().get(0).price();
		final long count = bid <= 0 ? 0 : Math.min(Math.min(need, MAXIMUM_LINE_COUNT), owner.getAdena() / bid);
		if (count <= 0)
		{
			return Optional.empty();
		}
		final var requested = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, raw.title(), List.of(new PhantomStorePlan.Line(itemId, itemId, count, bid)), expiry);
		return _stores.quotePlan(profileId, PhantomActivityState.ACTIVE, requested, now);
	}

	private Optional<PhantomStorePlan> sell(long profileId, Player owner, PhantomGoal goal, PhantomAcquisitionState state, long now)
	{
		if (!marketGoalEligible(goal, state))
		{
			return Optional.empty();
		}
		final TreeSet<Item> candidates = new TreeSet<>(SELL_ORDER);
		for (Item item : owner.getInventory().getItems())
		{
			if (surplusCount(owner, item, state) <= 0)
			{
				continue;
			}
			candidates.add(item);
			if (candidates.size() > MAXIMUM_SELL_CANDIDATES)
			{
				candidates.pollLast();
			}
		}
		final var lines = new ArrayList<PhantomStorePlan.Line>();
		final var stackItems = new TreeSet<Integer>();
		final int limit = Math.min(MAXIMUM_STORE_LINES, owner.getPrivateSellStoreLimit());
		final long expiry = now + (_policy.lifetimeSeconds() * 1000L);
		for (Item item : candidates)
		{
			if (lines.size() >= limit) { break; }
			if (item.isStackable() && !stackItems.add(item.getId())) { continue; }
			final long count = surplusCount(owner, item, state);
			if (count <= 0) { continue; }
			final var raw = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Лавка фантома", List.of(new PhantomStorePlan.Line(item.getObjectId(), item.getId(), count, 0)), expiry);
			final var priced = _stores.quotePlan(profileId, PhantomActivityState.ACTIVE, raw, now);
			priced.ifPresent(plan -> lines.add(plan.lines().get(0)));
		}
		return lines.isEmpty() ? Optional.empty() : _stores.quotePlan(profileId, PhantomActivityState.ACTIVE, new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Лавка фантома", lines, expiry), now);
	}

	private Optional<PhantomStorePlan> manufacture(long profileId, Player owner, PhantomGoal goal, PhantomAcquisitionState state, long now)
	{
		if (!OfflineTradeConfig.OFFLINE_CRAFT_ENABLE || !marketGoalEligible(goal, state)) { return Optional.empty(); }
		final var lines = new ArrayList<PhantomStorePlan.Line>();
		final var seen = new TreeSet<Integer>();
		final long expiry = now + (_policy.lifetimeSeconds() * 1000L);
		for (var recipe : java.util.stream.Stream.concat(owner.getDwarvenRecipeBook().stream(), owner.getCommonRecipeBook().stream()).sorted(Comparator.comparingInt(RecipeList::getId)).limit(MAXIMUM_SELL_CANDIDATES).toList())
		{
			if (!seen.add(recipe.getId()) || !learnedRecipe(owner, recipe.getId(), recipe.getItemId())) { continue; }
			final var raw = new PhantomStorePlan(PhantomStorePlan.Type.MANUFACTURE, PhantomStorePlan.State.REQUESTED, "Изготовление по рецептам", List.of(new PhantomStorePlan.Line(recipe.getId(), recipe.getItemId(), 1, 0)), expiry);
			_stores.quotePlan(profileId, PhantomActivityState.ACTIVE, raw, now).filter(plan -> plan.lines().get(0).price() > 0).ifPresent(plan -> lines.add(plan.lines().get(0)));
			if (lines.size() >= MAXIMUM_STORE_LINES) { break; }
		}
		return lines.isEmpty() ? Optional.empty() : _stores.quotePlan(profileId, PhantomActivityState.ACTIVE, new PhantomStorePlan(PhantomStorePlan.Type.MANUFACTURE, PhantomStorePlan.State.REQUESTED, "Изготовление по рецептам", lines, expiry), now);
	}

	private static boolean learnedRecipe(Player owner, int recipeId, int productId)
	{
		final var recipe = RecipeData.getInstance().getRecipeList(recipeId);
		return (recipe != null) && (recipe.getItemId() == productId) && owner.hasRecipeList(recipeId)
			&& (owner.getSkillLevel(recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId()) >= recipe.getLevel());
	}

	private static boolean marketGoalEligible(PhantomGoal goal, PhantomAcquisitionState state)
	{
		return (goal == null) || (goal.status() != PhantomGoalStatus.ACTIVE) || (state != null) || PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType());
	}

	private static long surplusCount(Player owner, Item item, PhantomAcquisitionState state)
	{
		if ((item == null) || (item.getItemLocation() != ItemLocation.INVENTORY) || !item.isAvailable(owner, false, false) || !item.isTradeable() || !item.isSellable() || (item.getTime() != -1))
		{
			return 0;
		}
		final ItemTemplate template = item.getTemplate();
		if (org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy.getInstance().protectedTemplate(template) || item.getEnchantLevel() > 0 || item.isAugmented() || item.getElementals() != null && item.getElementals().length > 0 || ((state != null) && (state.targetItemId() == item.getId() || ((state.recipePlan() != null) && state.recipePlan().nodes().stream().anyMatch(node -> node.itemId() == item.getId())))))
		{
			return 0;
		}
		if (template.getItemType() != EtcItemType.MATERIAL || !item.isStackable() || item.getCount() < 4) { return 0; }
		final var policy = org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy.getInstance();
		final var role = policy.recipeRoles().get(item.getId());
		if (role == null || role.keyPart()) { return 0; }
		// The canonical policy protects learned ingredients, starter subsidies and recipe reserve.
		final var decision = policy.evaluate(owner, item, java.util.Set.of(), false, false);
		return Math.min(MAXIMUM_LINE_COUNT, Math.min(item.getCount() / 2, decision.count()));
	}

	private static boolean retainedEligible(Player owner, PhantomStorePlan plan, PhantomGoal goal, PhantomAcquisitionState state)
	{
		if (plan.lines().size() > MAXIMUM_STORE_LINES) { return false; }
		long exposure = 0;
		final var keys = new TreeSet<Integer>();
		for (var line : plan.lines())
		{
			if ((line.price() <= 0) || !keys.add(line.objectOrRecipeId())) { return false; }
			if (plan.type() == PhantomStorePlan.Type.SELL)
			{
				final Item item = owner.getInventory().getItemByObjectId(line.objectOrRecipeId());
				if (!marketGoalEligible(goal, state) || (item == null) || (item.getId() != line.itemId()) || line.count() > surplusCount(owner, item, state)) { return false; }
			}
			else if (plan.type() == PhantomStorePlan.Type.MANUFACTURE)
			{
				if (!OfflineTradeConfig.OFFLINE_CRAFT_ENABLE || !marketGoalEligible(goal, state) || !learnedRecipe(owner, line.objectOrRecipeId(), line.itemId())) { return false; }
			}
			else if (plan.type() == PhantomStorePlan.Type.BUY)
			{
				if ((state == null) || (state.selectedSource() == null) || (state.phase() != PhantomAcquisitionState.Phase.NONE)
					|| (state.status() != PhantomAcquisitionState.Status.READY && state.status() != PhantomAcquisitionState.Status.PLANNING_ONLY)) { return false; }
				final long owned = ownedCount(owner, line.itemId());
				final boolean needed = line.itemId() == state.targetItemId()
					? line.count() <= (state.requiredAmount() - PhantomAcquisitionState.observedProgress(state.baselineCount(), owned, state.requiredAmount()))
					: (state.recipePlan() != null) && state.recipePlan().deficits().stream().anyMatch(deficit -> (deficit.itemId() == line.itemId()) && (line.count() <= Math.max(0, deficit.count() - owned)));
				if (!needed || line.count() > (owner.getAdena() - exposure) / line.price()) { return false; }
				exposure += line.count() * line.price();
			}
			else { return false; }
		}
		return true;
	}

	private boolean publish(long profileId, Player owner, PhantomGoal goal, PhantomStorePlan plan, long now)
	{
		if (_stopping || !stableActive(profileId) || !ownerReady(profileId, owner) || (_reservations.findActive(profileId).isPresent()))
		{
			return false;
		}
		final PhantomGoal current = goal(profileId);
		if (!sameGoal(goal, current))
		{
			return false;
		}
		if (!retainedEligible(owner, plan, current, acquisition(profileId, current))) { return false; }
		final Map<String, Reservation> resources = new java.util.TreeMap<>();
		if (plan.type() == PhantomStorePlan.Type.BUY)
		{
			long exposure = 0;
			for (var line : plan.lines()) { exposure = Math.addExact(exposure, Math.multiplyExact(line.count(), line.price())); }
			if (exposure > owner.getAdena()) { return false; }
			final var resource = new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), ResourceKind.ADENA, 0, 57, exposure, owner.getAdena(), 0, "INVENTORY");
			resources.put(resource.canonicalKey(), resource);
		}
		else
		{
			for (var line : plan.lines())
			{
				if (plan.type() == PhantomStorePlan.Type.MANUFACTURE)
				{
					final var recipe = RecipeData.getInstance().getRecipeList(line.objectOrRecipeId());
					if (!learnedRecipe(owner, line.objectOrRecipeId(), line.itemId())) { return false; }
					final var recipeResource = new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), ResourceKind.RECIPE, 0, recipe.getId(), 0, 0, 0, "");
					final var skillResource = new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), ResourceKind.SKILL, 0, recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId(), 0, 0, 0, "");
					resources.put(recipeResource.canonicalKey(), recipeResource); resources.put(skillResource.canonicalKey(), skillResource);
				}
				else
				{
					final Item item = owner.getInventory().getItemByObjectId(line.objectOrRecipeId());
					if ((item == null) || surplusCount(owner, item, acquisition(profileId, current)) < line.count()) { return false; }
					final var resource = new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), item.isStackable() ? ResourceKind.ITEM_COUNT : ResourceKind.ITEM_OBJECT, item.isStackable() ? 0 : item.getObjectId(), item.getId(), line.count(), item.getCount(), item.getEnchantLevel(), "INVENTORY");
					if (resources.putIfAbsent(resource.canonicalKey(), resource) != null) { return false; }
				}
			}
		}
		try (PhantomEconomyConflictPort.Claim claim = PhantomEconomyConflictPort.claim(profileId, null, List.copyOf(resources.values())))
		{
			if (!claim.acquired() || (_stores.open(profileId, PhantomActivityState.ACTIVE, plan, now) != PhantomStoreService.Result.OPENED))
			{
				return false;
			}
			track(profileId, plan, goal);
			return true;
		}
	}

	private void reconcile(long profileId, long now)
	{
		final var tracked = _opened.get(profileId);
		final Player owner = liveOwner(profileId);
		if (!_stores.blocksDecision(profileId))
		{
			if ((owner != null) && owner.isInStoreMode())
			{
				return;
			}
			if (owner != null)
			{
				owner.setOfflineStartTime(0);
				owner.standUp();
			}
			_opened.remove(profileId);
			_lastClosed.put(profileId, now);
			return;
		}
		if ((owner == null) || !OfflineTradeConfig.OFFLINE_TRADE_ENABLE || !_tradingWindow.test(profileId, now) || (tracked.plan().expiresEpochMillis() <= now) || !stableActive(profileId) || !sameRuntimeGoal(profileId, tracked) || !listingOwned(owner, tracked.plan()))
		{
			close(profileId, now);
		}
	}

	/** Durable goal and reservation checks run only on the bounded attempt cadence. */
	private void reconcileSlow(long profileId, long now)
	{
		final var tracked = _opened.get(profileId);
		if ((tracked != null) && _stores.blocksDecision(profileId) && (!sameGoal(tracked.goalId(), tracked.goalRevision(), goal(profileId)) || _reservations.findActive(profileId).isPresent()))
		{
			close(profileId, now);
		}
	}

	private boolean sameRuntimeGoal(long profileId, Opened tracked)
	{
		final var decision = _decision.get();
		final var runtime = decision == null ? null : decision.find(profileId).orElse(null);
		return (runtime == null) || (!runtime.persistenceInFlight() && (tracked.goalId() == runtime.goalId()) && ((tracked.goalId() == 0) || (tracked.goalRevision() == runtime.goalRevision())));
	}

	private static boolean listingOwned(Player owner, PhantomStorePlan plan)
	{
		if (owner.isDead() || owner.isAttackingNow() || owner.isCastingNow() || owner.isMoving() || (owner.getParty() != null) || !owner.isInStoreMode())
		{
			return false;
		}
		if (plan.type() == PhantomStorePlan.Type.MANUFACTURE)
		{
			if (!OfflineTradeConfig.OFFLINE_CRAFT_ENABLE || owner.getPrivateStoreType() != PrivateStoreType.MANUFACTURE) { return false; }
			synchronized (owner.getManufactureItems())
			{
				return owner.getManufactureItems().size() == plan.lines().size() && plan.lines().stream().allMatch(line ->
				{
					final var nativeLine = owner.getManufactureItems().get(line.objectOrRecipeId());
					return nativeLine != null && nativeLine.getCost() == line.price() && line.price() > 0 && learnedRecipe(owner, line.objectOrRecipeId(), line.itemId());
				});
			}
		}
		final boolean sell = plan.type() == PhantomStorePlan.Type.SELL;
		if (owner.getPrivateStoreType() != (sell ? PrivateStoreType.SELL : PrivateStoreType.BUY)) { return false; }
		final var list = sell ? owner.getSellList() : owner.getBuyList();
		synchronized (list)
		{
			long exposure = 0;
			final var seen = new TreeSet<Integer>();
			for (TradeItem nativeLine : list.getItems())
			{
				final int key = sell ? nativeLine.getObjectId() : nativeLine.getItem().getId();
				final var line = plan.lines().stream().filter(candidate -> (sell ? candidate.objectOrRecipeId() : candidate.itemId()) == key).findFirst().orElse(null);
				if (!seen.add(key) || (line == null) || nativeLine.getCount() <= 0 || nativeLine.getCount() > line.count() || nativeLine.getPrice() <= 0
					|| nativeLine.getPrice() != line.price() || nativeLine.getItem().getId() != line.itemId()) { return false; }
				if (sell)
				{
					final var item = owner.getInventory().getItemByObjectId(key);
					if (item == null || item.getId() != line.itemId() || item.getCount() < nativeLine.getCount()) { return false; }
				}
				else
				{
					if (nativeLine.getCount() > (owner.getAdena() - exposure) / nativeLine.getPrice()) { return false; }
					exposure += nativeLine.getCount() * nativeLine.getPrice();
				}
			}
			// Completed native transactions may remove any subset of the original quoted lines.
			return true;
		}
	}

	private void close(long profileId, long now)
	{
		if (_stores.close(profileId) == PhantomStoreService.Result.CLOSED)
		{
			_opened.remove(profileId);
			_lastClosed.put(profileId, now);
		}
	}

	private void track(long profileId, PhantomStorePlan plan, PhantomGoal goal)
	{
		_opened.put(profileId, new Opened(plan, goal == null ? 0 : goal.goalId(), goal == null ? 0 : goal.revision()));
	}

	private boolean stableActive(long profileId)
	{
		final var activity = _scheduler.find(profileId).orElse(null);
		final var actor = _materialization.find(profileId).orElse(null);
		if ((activity == null) || (actor == null) || !actor.worldPresent() || !actor.actionAdmissionOpen() || activity.boundaryInFlight() || activity.effectiveState() != PhantomActivityState.ACTIVE) { return false; }
		return activity.requestedState() == PhantomActivityState.ACTIVE && activity.transitionStatus() == PhantomActivityTransitionStatus.STABLE
			|| activity.requestedState() == PhantomActivityState.SLEEPING && _stores.blocksDecision(profileId)
				&& (activity.transitionStatus() == PhantomActivityTransitionStatus.STABLE || activity.transitionStatus() == PhantomActivityTransitionStatus.DEFERRED);
	}

	private Player liveOwner(long profileId)
	{
		final var actor = _materialization.find(profileId).orElse(null);
		return actor == null ? null : World.getInstance().getPlayer(actor.characterObjectId());
	}

	private boolean ownerReady(long profileId, Player owner)
	{
		final var decision = _decision.get();
		final var runtime = decision == null ? null : decision.find(profileId).orElse(null);
		return (runtime == null || (!runtime.inFlight() && !runtime.persistenceInFlight())) && (owner.getParty() == null) && !owner.isInCombat() && !owner.isMoving() && !owner.isAttackingNow() && !owner.isInStoreMode()
			&& !owner.isProcessingTransaction() && !owner.hasPendingOwnedStore() && !RecipeManager.getInstance().isMakingItem(owner) && owner.canMakeSocialAction() && owner.canOpenPrivateStore() && legalLocation(owner);
	}

	private static boolean legalLocation(Player owner)
	{
		if (owner.getInstanceId() != 0 || !owner.isInsideZone(ZoneId.PEACE)) { return false; }
		final var geo = GeoEngine.getInstance();
		final int x = owner.getX(), y = owner.getY(), z = owner.getZ();
		if (!geo.hasGeo(x, y) || Math.abs((long) geo.getHeight(x, y, z) - z) > 64
			|| ZoneManager.getInstance().getZones(x, y, z).stream().noneMatch(zone -> zone instanceof TownZone || zone instanceof PeaceZone)) { return false; }
		return World.getInstance().getVisibleObjects(owner, Player.class).stream().noneMatch(other -> other != owner && other.calculateDistance3D(owner) < 60);
	}

	private PhantomGoal goal(long profileId)
	{
		return _goals.load(profileId).map(PhantomGoalStateStore.StoredGoal::goal).orElse(null);
	}

	private PhantomAcquisitionState acquisition(long profileId, PhantomGoal goal)
	{
		if ((goal == null) || (goal.status() != PhantomGoalStatus.ACTIVE) || !PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType()))
		{
			return null;
		}
		try
		{
			final var spec = PhantomAcquisitionGoalSpec.parse(goal);
			final var state = _acquisition.load(profileId).map(PhantomAcquisitionStore.StoredState::state).orElse(null);
			return (state != null) && (state.goalId() == goal.goalId()) && (state.goalRevision() == goal.revision()) && (state.targetItemId() == spec.itemId()) ? state : null;
		}
		catch (RuntimeException stale)
		{
			return null;
		}
	}

	private static long ownedCount(Player owner, int itemId)
	{
		long total = 0;
		for (Item item : owner.getInventory().getItems())
		{
			if ((item.getId() == itemId) && (item.getItemLocation() == ItemLocation.INVENTORY))
			{
				total = Math.addExact(total, item.getCount());
			}
		}
		return total;
	}

	private static boolean sameGoal(PhantomGoal before, PhantomGoal after)
	{
		return before == null ? after == null : (after != null) && (before.goalId() == after.goalId()) && (before.revision() == after.revision());
	}

	private static boolean sameGoal(long goalId, long revision, PhantomGoal after)
	{
		return goalId == 0 ? after == null : (after != null) && (after.goalId() == goalId) && (after.revision() == revision);
	}

	private record Opened(PhantomStorePlan plan, long goalId, long goalRevision)
	{
	}
}

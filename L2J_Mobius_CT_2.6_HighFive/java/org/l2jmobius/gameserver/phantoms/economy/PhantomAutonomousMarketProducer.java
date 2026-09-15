package org.l2jmobius.gameserver.phantoms.economy;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig.AutonomousPolicy;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityTransitionStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;

/** One shared-scheduler owner for bounded native SELL/BID plans; manufacture is fail-closed. */
public final class PhantomAutonomousMarketProducer implements PhantomSchedulerControlPort
{
	private static final long ATTEMPT_INTERVAL_MILLIS = 15_000;
	private static final long ROTATION_INTERVAL_MILLIS = 900_000;
	private static final int MAXIMUM_SELL_CANDIDATES = 24;
	private static final int MAXIMUM_QUOTE_ATTEMPTS = 2;
	private static final int MAXIMUM_LINE_COUNT = 8;
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

	/** Waits for an in-flight producer pulse before the StoreService drain begins. */
	public synchronized void beginStop()
	{
		_stopping = true;
	}

	/** The native owner and accepted live state are rechecked on every attempt. */
	public synchronized void pulse(long now)
	{
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
		if (_stopping)
		{
			return Optional.empty();
		}
		final int active = (int) _materialization.list().stream().filter(actor -> stableActive(actor.profileId())).count();
		return consider(profileId, active, now);
	}

	private Optional<PhantomStorePlan> consider(long profileId, int active, long now)
	{
		if (_stopping)
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
		if ((goal != null) && (goal.status() == PhantomGoalStatus.ACTIVE) && (state == null))
		{
			return Optional.empty();
		}
		final TreeSet<Item> candidates = new TreeSet<>(SELL_ORDER);
		for (Item item : owner.getInventory().getItems())
		{
			if (!surplus(owner, item, state))
			{
				continue;
			}
			candidates.add(item);
			if (candidates.size() > MAXIMUM_SELL_CANDIDATES)
			{
				candidates.pollLast();
			}
		}
		int attempts = 0;
		for (Item item : candidates)
		{
			if (attempts++ >= MAXIMUM_QUOTE_ATTEMPTS)
			{
				break;
			}
			final long count = item.isStackable() ? Math.min(MAXIMUM_LINE_COUNT, item.getCount() / 2) : 1;
			final var raw = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Лавка фантома", List.of(new PhantomStorePlan.Line(item.getObjectId(), item.getId(), count, 0)), now + (_policy.lifetimeSeconds() * 1000L));
			final var priced = _stores.quotePlan(profileId, PhantomActivityState.ACTIVE, raw, now);
			if (priced.isPresent())
			{
				return priced;
			}
		}
		return Optional.empty();
	}

	private static boolean surplus(Player owner, Item item, PhantomAcquisitionState state)
	{
		if ((item == null) || (item.getItemLocation() != ItemLocation.INVENTORY) || !item.isAvailable(owner, false, false) || !item.isTradeable() || !item.isSellable() || (item.getTime() != -1))
		{
			return false;
		}
		final ItemTemplate template = item.getTemplate();
		if (template.isQuestItem() || template.isHeroItem() || (template.getTime() != -1) || ((state != null) && (state.targetItemId() == item.getId() || ((state.recipePlan() != null) && state.recipePlan().nodes().stream().anyMatch(node -> node.itemId() == item.getId())))))
		{
			return false;
		}
		if (template.getItemType() == EtcItemType.MATERIAL)
		{
			return item.isStackable() && (item.getCount() >= 4);
		}
		final Item equipped = owner.getInventory().getPaperdollItemByItemId(item.getId());
		return (template.isWeapon() || template.isArmor()) && !item.isStackable() && (equipped != null) && (equipped.getObjectId() != item.getObjectId());
	}

	private static boolean retainedEligible(Player owner, PhantomStorePlan plan, PhantomGoal goal, PhantomAcquisitionState state)
	{
		if (plan.lines().size() != 1)
		{
			return false;
		}
		final PhantomStorePlan.Line line = plan.lines().get(0);
		if (plan.type() == PhantomStorePlan.Type.SELL)
		{
			if ((goal != null) && (goal.status() == PhantomGoalStatus.ACTIVE) && (state == null))
			{
				return false;
			}
			final Item item = owner.getInventory().getItemByObjectId(line.objectOrRecipeId());
			return (item != null) && (item.getId() == line.itemId()) && surplus(owner, item, state) && (line.count() <= (item.isStackable() ? Math.min(MAXIMUM_LINE_COUNT, item.getCount() / 2) : 1));
		}
		if ((plan.type() != PhantomStorePlan.Type.BUY) || (state == null) || (state.selectedSource() == null) || (state.phase() != PhantomAcquisitionState.Phase.NONE) || (state.status() != PhantomAcquisitionState.Status.READY && state.status() != PhantomAcquisitionState.Status.PLANNING_ONLY) || (line.price() <= 0) || (line.count() > owner.getAdena() / line.price()))
		{
			return false;
		}
		final long owned = ownedCount(owner, line.itemId());
		if (line.itemId() == state.targetItemId())
		{
			return line.count() <= (state.requiredAmount() - PhantomAcquisitionState.observedProgress(state.baselineCount(), owned, state.requiredAmount()));
		}
		return (state.recipePlan() != null) && state.recipePlan().deficits().stream().anyMatch(deficit -> (deficit.itemId() == line.itemId()) && (line.count() <= Math.max(0, deficit.count() - owned)));
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
		final PhantomStorePlan.Line line = plan.lines().get(0);
		final Reservation resource;
		if (plan.type() == PhantomStorePlan.Type.BUY)
		{
			final long exposure = Math.multiplyExact(line.count(), line.price());
			if (exposure > owner.getAdena())
			{
				return false;
			}
			resource = new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), ResourceKind.ADENA, 0, 57, exposure, owner.getAdena(), 0, "INVENTORY");
		}
		else
		{
			final Item item = owner.getInventory().getItemByObjectId(line.objectOrRecipeId());
			if ((item == null) || !surplus(owner, item, acquisition(profileId, current)) || (item.getCount() < line.count()))
			{
				return false;
			}
			resource = new Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), item.isStackable() ? ResourceKind.ITEM_COUNT : ResourceKind.ITEM_OBJECT, item.isStackable() ? 0 : item.getObjectId(), item.getId(), line.count(), item.getCount(), item.getEnchantLevel(), "INVENTORY");
		}
		try (PhantomEconomyConflictPort.Claim claim = PhantomEconomyConflictPort.claim(profileId, null, List.of(resource)))
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
				owner.standUp();
			}
			_opened.remove(profileId);
			_lastClosed.put(profileId, now);
			return;
		}
		if ((owner == null) || (tracked.plan().expiresEpochMillis() <= now) || !stableActive(profileId) || !sameRuntimeGoal(profileId, tracked) || !listingOwned(owner, tracked.plan()))
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
		if (plan.lines().size() != 1)
		{
			return false;
		}
		final PhantomStorePlan.Line line = plan.lines().get(0);
		final var nativeLines = plan.type() == PhantomStorePlan.Type.SELL ? owner.getSellList().getItems() : owner.getBuyList().getItems();
		if (nativeLines.isEmpty())
		{
			// Native transaction completion can empty the listing before its observer closes.
			return true;
		}
		if (nativeLines.size() != 1)
		{
			return false;
		}
		final TradeItem nativeLine = nativeLines.iterator().next();
		if ((nativeLine.getCount() <= 0) || (nativeLine.getCount() > line.count()) || (nativeLine.getPrice() != line.price()) || (nativeLine.getItem().getId() != line.itemId()))
		{
			return false;
		}
		if (plan.type() == PhantomStorePlan.Type.SELL)
		{
			final Item item = owner.getInventory().getItemByObjectId(line.objectOrRecipeId());
			return (owner.getPrivateStoreType() == PrivateStoreType.SELL) && (nativeLine.getObjectId() == line.objectOrRecipeId()) && (item != null) && (item.getId() == line.itemId()) && (item.getCount() >= nativeLine.getCount());
		}
		return (plan.type() == PhantomStorePlan.Type.BUY) && (owner.getPrivateStoreType() == PrivateStoreType.BUY) && (nativeLine.getCount() <= owner.getAdena() / nativeLine.getPrice());
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
		return (activity != null) && (actor != null) && actor.worldPresent() && actor.actionAdmissionOpen() && (activity.effectiveState() == PhantomActivityState.ACTIVE) && (activity.requestedState() == PhantomActivityState.ACTIVE) && (activity.transitionStatus() == PhantomActivityTransitionStatus.STABLE) && !activity.boundaryInFlight();
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
		return (runtime == null || (!runtime.inFlight() && !runtime.persistenceInFlight())) && (owner.getParty() == null) && !owner.isInCombat() && !owner.isMoving() && !owner.isAttackingNow() && !owner.isInStoreMode() && owner.canMakeSocialAction() && owner.canOpenPrivateStore();
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

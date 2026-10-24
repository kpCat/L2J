/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.ManufactureItem;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.network.holders.TradeList;
import org.l2jmobius.gameserver.network.serverpackets.ExPrivateStoreSetWholeMsg;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgBuy;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgSell;
import org.l2jmobius.gameserver.network.serverpackets.RecipeShopMsg;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.services.PrivateStoreService;
import org.l2jmobius.gameserver.util.Broadcast;

/** Opens/restores/closes only visible materialized Phantom stores. */
public final class PhantomStoreService
{
	private static final String JOURNEY_COMPONENT = "economy.store.journey";
	private final PhantomGoalStateCodec _journeyCodec = new PhantomGoalStateCodec();
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final Supplier<PhantomPrivateMarketPricingAuthority> _pricing;
	private final ConcurrentHashMap<Long, AutoCloseable> _ownerObservers = new ConcurrentHashMap<>();
	private final LongAdder _opened = new LongAdder();
	private final LongAdder _closed = new LongAdder();
	private volatile LongPredicate _recoveryFence = _ -> false;
	private boolean _recoveryFenceInstalled;

	public PhantomStoreService(PhantomProfileRepository profiles, PhantomMaterializationService materialization)
	{
		this(profiles, materialization, null);
	}

	public PhantomStoreService(PhantomProfileRepository profiles, PhantomMaterializationService materialization, Supplier<PhantomPrivateMarketPricingAuthority> pricing)
	{
		_profiles = Objects.requireNonNull(profiles);
		_materialization = Objects.requireNonNull(materialization);
		_pricing = pricing;
	}

	public Result open(long profileId, PhantomActivityState activityState, PhantomStorePlan plan, long now)
	{
		Objects.requireNonNull(activityState);
		Objects.requireNonNull(plan);
		if (!activityState.requiresMaterialization() || (plan.expiresEpochMillis() <= now))
		{
			return Result.ACTIVE_REQUIRED;
		}
		final var journey = restJourney(profileId).orElse(null); // Malformed durable metadata fails before any save/native installation.
		if (journey != null)
		{
			if (currentPlan(profileId).isEmpty() && journey.goal().deadlineEpochMillis() <= now) { _profiles.deleteComponent(profileId, JOURNEY_COMPONENT, journey.rowVersion()); }
			else if (journey.goal().status() != PhantomGoalStatus.COMPLETED || !journey.goal().payloadText().equals(plan.contentHash()) || currentPlan(profileId).isEmpty()) { return Result.REJECTED; }
		}
		VersionedPlan saved = _pricing == null ? save(profileId, plan.withState(PhantomStorePlan.State.REQUESTED)) : null;
		try (ActionLease lease = acquireExclusive(profileId))
		{
			if (lease == null)
			{
				return Result.ACTIVE_REQUIRED;
			}
			final Player player = lease.player();
			if (_pricing != null)
			{
				try
				{
					final PhantomStorePlan canonical = _pricing.get().pricePlan(player, plan);
					if (!canonical.contentHash().equals(plan.contentHash()))
					{
						return Result.REJECTED;
					}
				}
				catch (RuntimeException unsafe)
				{
					return Result.REJECTED;
				}
				saved = save(profileId, plan.withState(PhantomStorePlan.State.REQUESTED));
			}
			if (!installNative(player, plan))
			{
				return Result.REJECTED;
			}
			if (OfflineTradeConfig.OFFLINE_TRADE_ENABLE && player.hasHeadlessOutboundSession()) { player.setOfflineStartTime(now); }
			update(profileId, saved.rowVersion(), plan.withState(PhantomStorePlan.State.OPEN));
			installOwnerObserver(profileId, player.getObjectId());
			_opened.increment();
			return Result.OPENED;
		}
	}

	/** Price before a durable store/goal is proposed; open validates the same quote again. */
	public Optional<PhantomStorePlan> quotePlan(long profileId, PhantomActivityState activityState, PhantomStorePlan requested, long now)
	{
		if ((_pricing == null) || (activityState == null) || (requested == null) || !activityState.requiresMaterialization() || (requested.expiresEpochMillis() <= now))
		{
			return Optional.empty();
		}
		try (ActionLease lease = acquireExclusive(profileId))
		{
			return lease == null ? Optional.empty() : Optional.of(_pricing.get().pricePlan(lease.player(), requested));
		}
		catch (RuntimeException unsafe)
		{
			return Optional.empty();
		}
	}

	public Result restore(long profileId, PhantomActivityState activityState, long now)
	{
		final VersionedPlan stored = find(profileId);
		return stored == null ? Result.REJECTED : open(profileId, activityState, stored.plan(), now);
	}

	public Optional<PhantomStorePlan> currentPlan(long profileId)
	{
		return Optional.ofNullable(find(profileId)).map(VersionedPlan::plan);
	}

	/** Bounded durable owner inventory for crash recovery; no per-Phantom DB scan. */
	public List<Long> planOwnersAfter(long cursor, int limit)
	{
		if ((cursor < 0) || (limit < 1) || (limit > 100))
		{
			throw new IllegalArgumentException("Invalid private-store recovery page.");
		}
		return _profiles.listManagedAfter(PhantomStorePlan.COMPONENT_TYPE, cursor, limit).stream().map(managed -> managed.profile().profileId()).toList();
	}

	/** In-memory native owner fence for the shared Decision admission hot path. */
	public boolean blocksDecision(long profileId)
	{
		return _ownerObservers.containsKey(profileId) || _recoveryFence.test(profileId);
	}

	public synchronized void installRecoveryFence(LongPredicate recoveryFence)
	{
		if (_recoveryFenceInstalled) { throw new IllegalStateException("Store recovery fence must be installed once."); }
		_recoveryFence = Objects.requireNonNull(recoveryFence);
		_recoveryFenceInstalled = true;
	}

	public Optional<RetainedPlan> retainedPlan(long profileId)
	{
		final var profile = _profiles.find(profileId).orElse(null);
		final var stored = find(profileId);
		return profile == null || profile.characterObjectId() == null || stored == null ? Optional.empty()
			: Optional.of(new RetainedPlan(profile.characterObjectId(), stored.rowVersion(), stored.plan(), restJourney(profileId).orElse(null)));
	}

	public Optional<RestJourney> restJourney(long profileId)
	{
		return _profiles.findComponent(profileId, JOURNEY_COMPONENT).map(row ->
		{
			if (row.componentSchemaVersion() != 1) { throw new IllegalArgumentException("Unknown market journey version."); }
			return new RestJourney(row.rowVersion(), _journeyCodec.decode(row.payload()));
		});
	}

	/** Same root and native owner; only the market child and requested quote are inserted. */
	public Optional<RetainedPlan> requestRestJourney(long profileId, Player player, PhantomGoal root, PhantomStorePlan plan, String anchorId, PhantomNavigationPoint destination, long now)
	{
		try (ActionLease lease = acquireExclusive(profileId))
		{
		if (lease == null || lease.player() != player || !matchesQuote(player, plan)) { return Optional.empty(); }
		if (!OfflineTradeConfig.OFFLINE_TRADE_ENABLE || currentPlan(profileId).isPresent()) { return Optional.empty(); }
		final var previous = restJourney(profileId).orElse(null);
		if (previous != null && previous.goal().deadlineEpochMillis() > now) { return Optional.empty(); }
		final var currentRoot = _profiles.findComponent(profileId, PhantomGoalStateStore.COMPONENT_TYPE).orElse(null);
		if (currentRoot == null || !_journeyCodec.decode(currentRoot.payload()).equals(root)) { return Optional.empty(); }
		final var intent = RestJourney.intent(root, player.getObjectId(), player.getActiveClass(), player.getClassIndex(), plan, anchorId, destination, now);
		_profiles.mutateComponentsAtomically(profileId, List.of(
			new PhantomProfileRepository.ComponentMutation(JOURNEY_COMPONENT, previous == null ? -1 : previous.rowVersion(), 1, _journeyCodec.encode(intent)),
			new PhantomProfileRepository.ComponentMutation(PhantomStorePlan.COMPONENT_TYPE, -1, PhantomStorePlan.SCHEMA_VERSION, plan.withState(PhantomStorePlan.State.REQUESTED).encode())));
		return retainedPlan(profileId);
		}
		catch (RuntimeException conflict) { return Optional.empty(); }
	}

	public Optional<RetainedPlan> finishRestJourney(long profileId, RetainedPlan expected, boolean successful)
	{
		return restJourneyStatus(profileId, expected, successful ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.FAILED);
	}

	/** Only after the native arrival checkpoint; a pre-armed payment is otherwise terminal. */
	public Optional<RetainedPlan> resumeRestJourney(long profileId, RetainedPlan expected)
	{
		return restJourneyStatus(profileId, expected, PhantomGoalStatus.ACTIVE);
	}

	private Optional<RetainedPlan> restJourneyStatus(long profileId, RetainedPlan expected, PhantomGoalStatus status)
	{
		if (expected.journey() == null || !retainedPlan(profileId).filter(expected::equals).isPresent()) { return Optional.empty(); }
		final var old = expected.journey().goal();
		final var terminal = new PhantomGoal(old.goalId(), old.goalType(), status, old.subject(), old.target(), old.requiredAmount(), old.currentAmount(), old.acquisitionMethod(), old.validSources(), old.selectedAnchor(), old.purposeKey(), old.priority(), old.riskBudget(), old.expenseBudget(), old.deadlineEpochMillis(), old.constraints(), status == PhantomGoalStatus.FAILED ? "market.rest.native_unknown" : "market.rest.arrived", old.revision(), old.payloadText());
		_profiles.updateComponent(profileId, JOURNEY_COMPONENT, expected.journey().rowVersion(), 1, _journeyCodec.encode(terminal));
		return retainedPlan(profileId);
	}

	private void deleteRestJourney(long profileId, RestJourney expected)
	{
		// An uncertain native payment remains terminal until its original expiry; never replay its stage on restart.
		if (expected != null && expected.goal().status() != PhantomGoalStatus.FAILED) { _profiles.deleteComponent(profileId, JOURNEY_COMPONENT, expected.rowVersion()); }
	}

	public boolean matchesQuote(Player player, PhantomStorePlan plan)
	{
		try { return _pricing != null && _pricing.get().pricePlan(player, plan).contentHash().equals(plan.contentHash()); }
		catch (RuntimeException unavailable) { return false; }
	}

	public Result discardDormant(long profileId, RetainedPlan expected)
	{
		if (!dormant(profileId, expected.characterObjectId())) { return Result.RETRY; }
		final var current = retainedPlan(profileId).orElse(null);
		if (current == null) { return Result.CLOSED; }
		if (!current.equals(expected)) { return Result.RETRY; }
		try { _profiles.deleteComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, expected.rowVersion()); deleteRestJourney(profileId, expected.journey()); return Result.CLOSED; }
		catch (RuntimeException conflict) { return Result.RETRY; }
	}

	/** Pending recovery owns only its exact durable row, never an unrelated native listing. */
	public Result discardRecovery(long profileId, RetainedPlan expected)
	{
		if (_materialization.find(profileId).isEmpty()) { return discardDormant(profileId, expected); }
		try (ActionLease lease = acquireExclusive(profileId))
		{
			if (lease == null || lease.player().getObjectId() != expected.characterObjectId()) { return Result.RETRY; }
			final var player = lease.player();
			synchronized (player.getSellList()) { synchronized (player.getBuyList()) { synchronized (player.getManufactureItems())
			{
				if (_ownerObservers.containsKey(profileId) || player.getPrivateStoreType() != PrivateStoreType.NONE || player.getSellList().getItemCount() != 0
					|| player.getBuyList().getItemCount() != 0 || !player.getManufactureItems().isEmpty() || player.isProcessingTransaction()
					|| player.hasPendingOwnedStore() || RecipeManager.getInstance().isMakingItem(player)) { return Result.RETRY; }
				final var current = retainedPlan(profileId).orElse(null);
				if (current == null) { return Result.CLOSED; }
				if (!current.equals(expected)) { return Result.RETRY; }
				_profiles.deleteComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, expected.rowVersion());
				deleteRestJourney(profileId, expected.journey());
				return Result.CLOSED;
			} } }
		}
		catch (RuntimeException conflict) { return Result.RETRY; }
	}

	private boolean dormant(long profileId, int characterObjectId)
	{
		final var profile = _profiles.find(profileId).orElse(null);
		return profile != null && Objects.equals(profile.characterObjectId(), characterObjectId) && _materialization.find(profileId).isEmpty()
			&& World.getInstance().findObject(characterObjectId) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(characterObjectId) == null;
	}

	/** A native subset may only reduce stock; its original identity, quote and absolute expiry remain fixed. */
	public static Optional<PhantomStorePlan> suspendedPlan(PhantomStorePlan original, List<PhantomStorePlan.Line> remaining)
	{
		Objects.requireNonNull(original); Objects.requireNonNull(remaining);
		final var seen = new java.util.HashSet<Integer>();
		for (var line : remaining)
		{
			final int key = original.type() == PhantomStorePlan.Type.BUY ? line.itemId() : line.objectOrRecipeId();
			final var quoted = original.lines().stream().filter(candidate -> (original.type() == PhantomStorePlan.Type.BUY ? candidate.itemId() : candidate.objectOrRecipeId()) == key).findFirst().orElse(null);
			if (!seen.add(key) || quoted == null || line.itemId() != quoted.itemId() || line.objectOrRecipeId() != quoted.objectOrRecipeId()
				|| line.price() != quoted.price() || line.count() > quoted.count()) { throw new IllegalArgumentException("Native store remainder differs from its accepted quote."); }
		}
		return remaining.isEmpty() ? Optional.empty() : Optional.of(original.withLines(remaining).withState(PhantomStorePlan.State.REQUESTED));
	}

	public Result suspendForRestart(long profileId)
	{
		final var retained = retainedPlan(profileId).orElse(null);
		if (retained == null) { closeOwnerObserver(profileId); return Result.CLOSED; }
		if (_materialization.find(profileId).isEmpty()) { return dormant(profileId, retained.characterObjectId()) ? Result.CLOSED : Result.RETRY; }
		try (ActionLease lease = acquireExclusive(profileId))
		{
			if (lease == null) { return Result.RETRY; }
			final var player = lease.player();
			if (player.getObjectId() != retained.characterObjectId()) { return Result.INCONSISTENT; }
			synchronized (player.getSellList()) { synchronized (player.getBuyList()) { synchronized (player.getManufactureItems())
			{
				if (player.isProcessingTransaction() || player.hasPendingOwnedStore() || RecipeManager.getInstance().isMakingItem(player)) { return Result.RETRY; }
				final var current = retainedPlan(profileId).orElse(null);
				if (current == null) { closeOwnerObserver(profileId); return Result.CLOSED; }
				if (player.getPrivateStoreType() == PrivateStoreType.NONE && current.plan().state() == PhantomStorePlan.State.REQUESTED && !_ownerObservers.containsKey(profileId)) { return Result.CLOSED; }
				final List<PhantomStorePlan.Line> lines = new ArrayList<>();
				if (player.getPrivateStoreType() != PrivateStoreType.NONE)
				{
					final var expectedType = switch (current.plan().type()) { case SELL -> PrivateStoreType.SELL; case PACKAGE_SELL -> PrivateStoreType.PACKAGE_SELL; case BUY -> PrivateStoreType.BUY; case MANUFACTURE -> PrivateStoreType.MANUFACTURE; };
					if (player.getPrivateStoreType() != expectedType || !_ownerObservers.containsKey(profileId)) { return Result.INCONSISTENT; }
					if (current.plan().type() == PhantomStorePlan.Type.MANUFACTURE)
					{
						for (var nativeLine : player.getManufactureItems().values())
						{
							final var quoted = current.plan().lines().stream().filter(line -> line.objectOrRecipeId() == nativeLine.getRecipeId()).findFirst().orElseThrow();
							lines.add(new PhantomStorePlan.Line(nativeLine.getRecipeId(), quoted.itemId(), 1, nativeLine.getCost()));
						}
					}
					else
					{
						final var list = current.plan().type() == PhantomStorePlan.Type.BUY ? player.getBuyList() : player.getSellList();
						for (var line : list.getItems()) { lines.add(new PhantomStorePlan.Line(line.getObjectId() > 0 ? line.getObjectId() : line.getItem().getId(), line.getItem().getId(), line.getCount(), line.getPrice())); }
					}
				}
				final var suspended = suspendedPlan(current.plan(), lines);
				if (suspended.isPresent()) { update(profileId, current.rowVersion(), suspended.get()); }
				else { _profiles.deleteComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, current.rowVersion()); deleteRestJourney(profileId, current.journey()); }
				player.getSellList().clear(); player.getBuyList().clear(); player.getManufactureItems().clear();
				player.setPrivateStoreType(PrivateStoreType.NONE); player.setOfflineStartTime(0);
			} } }
			player.standUp(); player.broadcastUserInfo();
			closeOwnerObserver(profileId);
			return Result.CLOSED;
		}
		catch (RuntimeException failure) { return Result.RETRY; }
	}

	public Result close(long profileId)
	{
		final VersionedPlan current = find(profileId);
		final RestJourney journey = current == null ? null : restJourney(profileId).orElse(null);
		if (current == null)
		{
			closeOwnerObserver(profileId);
			return Result.CLOSED;
		}
		try (ActionLease lease = acquireExclusive(profileId))
		{
			if (lease == null)
			{
				return Result.RETRY;
			}
			final Player player = lease.player();
			synchronized (player.getSellList())
			{
				synchronized (player.getBuyList())
				{
					synchronized (player.getManufactureItems())
					{
						// The native customer can enqueue timed craft under this same monitor.
						if (player.isProcessingTransaction() || player.hasPendingOwnedStore() || RecipeManager.getInstance().isMakingItem(player)) { return Result.RETRY; }
						player.getSellList().clear();
						player.getBuyList().clear();
						player.getManufactureItems().clear();
						player.setPrivateStoreType(PrivateStoreType.NONE);
						player.setOfflineStartTime(0);
					}
				}
			}
			player.standUp();
			player.broadcastUserInfo();
			if ((player.getPrivateStoreType() != PrivateStoreType.NONE) || (player.getSellList().getItemCount() != 0) || (player.getBuyList().getItemCount() != 0) || !player.getManufactureItems().isEmpty())
			{
				return Result.INCONSISTENT;
			}
		}
		catch (RuntimeException exception)
		{
			return Result.INCONSISTENT;
		}
		closeOwnerObserver(profileId);
		_profiles.deleteComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, current.rowVersion());
		deleteRestJourney(profileId, journey);
		_closed.increment();
		return Result.CLOSED;
	}

	public ShutdownResult shutdown()
	{
		final List<Long> retry = new ArrayList<>();
		final List<Long> inconsistent = new ArrayList<>();
		long cursor = 0;
		for (int page = 0; page < 1000; page++)
		{
			final List<PhantomProfileRepository.ManagedProfile> plans = _profiles.listManagedAfter(PhantomStorePlan.COMPONENT_TYPE, cursor, 100);
			if (plans.isEmpty())
			{
				return new ShutdownResult(retry.isEmpty() && inconsistent.isEmpty(), retry, inconsistent);
			}
			for (PhantomProfileRepository.ManagedProfile managed : plans)
			{
				cursor = managed.profile().profileId();
				final Result result = suspendForRestart(cursor);
				if (result == Result.RETRY)
				{
					retry.add(cursor);
				}
				else if (result == Result.INCONSISTENT)
				{
					inconsistent.add(cursor);
				}
			}
		}
		return new ShutdownResult(false, retry, inconsistent);
	}

	private ActionLease acquireExclusive(long profileId)
	{
		final var before = _materialization.find(profileId).orElse(null);
		if ((before == null) || (before.admittedActionCount() != 0))
		{
			return null;
		}
		final ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null);
		final var after = _materialization.find(profileId).orElse(null);
		if ((lease == null) || (after == null) || (after.admittedActionCount() != 1))
		{
			if (lease != null)
			{
				lease.close();
			}
			return null;
		}
		return lease;
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_opened.sum(), _closed.sum(), _ownerObservers.size());
	}

	/** Listing adapter only; the materialized or dedicated offline caller retains its lifecycle fence. */
	static boolean installNative(Player player, PhantomStorePlan plan)
	{
		if ((plan.type() == PhantomStorePlan.Type.SELL || plan.type() == PhantomStorePlan.Type.PACKAGE_SELL) && plan.lines().size() > player.getPrivateSellStoreLimit()
			|| plan.type() == PhantomStorePlan.Type.BUY && plan.lines().size() > player.getPrivateBuyStoreLimit()) { return false; }
		if (player.isInStoreMode() && (player.getPrivateStoreType() != PrivateStoreType.NONE))
		{
			return canonicalMatches(player, plan);
		}
		player.setStoreName(plan.title());
		switch (plan.type())
		{
			case SELL, PACKAGE_SELL ->
			{
				final TradeList list = player.getSellList();
				list.clear();
				list.setPackaged(plan.type() == PhantomStorePlan.Type.PACKAGE_SELL);
				for (PhantomStorePlan.Line line : plan.lines())
				{
					final Item item = player.getInventory().getItemByObjectId(line.objectOrRecipeId());
					if ((item == null) || (item.getId() != line.itemId()) || (item.getCount() < line.count()) || !item.isTradeable()
						|| org.l2jmobius.gameserver.phantoms.commerce.PhantomStarterShotService.protectedStock(player, item.getId()))
					{
						list.clear();
						return false;
					}
					list.addItem(line.objectOrRecipeId(), line.count(), line.price());
				}
				player.setPrivateStoreType(plan.type() == PhantomStorePlan.Type.PACKAGE_SELL ? PrivateStoreType.PACKAGE_SELL : PrivateStoreType.SELL);
			}
			case BUY ->
			{
				final TradeList list = player.getBuyList();
				list.clear();
				long total = 0;
				try
				{
					for (PhantomStorePlan.Line line : plan.lines())
					{
						total = Math.addExact(total, Math.multiplyExact(line.count(), line.price()));
						list.addItemByItemId(line.itemId(), line.count(), line.price());
					}
				}
				catch (ArithmeticException unsafe)
				{
					list.clear();
					return false;
				}
				if (total > player.getAdena())
				{
					list.clear();
					return false;
				}
				player.setPrivateStoreType(PrivateStoreType.BUY);
			}
			case MANUFACTURE ->
			{
				player.getManufactureItems().clear();
				for (PhantomStorePlan.Line line : plan.lines())
				{
					final RecipeList recipe = RecipeData.getInstance().getRecipeList(line.objectOrRecipeId());
					if ((recipe == null) || (recipe.getItemId() != line.itemId()) || !player.hasRecipeList(recipe.getId()) || line.price() <= 0
						|| player.getSkillLevel(recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId()) < recipe.getLevel())
					{
						player.getManufactureItems().clear();
						return false;
					}
					player.getManufactureItems().put(recipe.getId(), new ManufactureItem(recipe.getId(), line.price()));
				}
				player.setPrivateStoreType(PrivateStoreType.MANUFACTURE);
			}
		}
		player.sitDown();
		player.broadcastUserInfo();
		switch (plan.type())
		{
			case SELL -> player.broadcastPacket(new PrivateStoreMsgSell(player));
			case PACKAGE_SELL -> player.broadcastPacket(new ExPrivateStoreSetWholeMsg(player));
			case BUY -> player.broadcastPacket(new PrivateStoreMsgBuy(player));
			case MANUFACTURE -> Broadcast.toSelfAndKnownPlayers(player, new RecipeShopMsg(player));
		}
		return true;
	}

	private static boolean canonicalMatches(Player player, PhantomStorePlan plan)
	{
		if (!player.getStoreName().equals(plan.title()))
		{
			return false;
		}
		return switch (plan.type())
		{
			case SELL, PACKAGE_SELL ->
			{
				final PrivateStoreType expected = plan.type() == PhantomStorePlan.Type.SELL ? PrivateStoreType.SELL : PrivateStoreType.PACKAGE_SELL;
				final List<TradeItem> actual = player.getSellList().getItems().stream().sorted(Comparator.comparingInt(TradeItem::getObjectId)).toList();
				final List<PhantomStorePlan.Line> planned = plan.lines().stream().sorted(Comparator.comparingInt(PhantomStorePlan.Line::objectOrRecipeId)).toList();
				yield (player.getPrivateStoreType() == expected) && (player.getSellList().isPackaged() == (expected == PrivateStoreType.PACKAGE_SELL)) && exactSell(actual, planned);
			}
			case BUY ->
			{
				final List<TradeItem> actual = player.getBuyList().getItems().stream().sorted(Comparator.comparingInt(item -> item.getItem().getId())).toList();
				final List<PhantomStorePlan.Line> planned = plan.lines().stream().sorted(Comparator.comparingInt(PhantomStorePlan.Line::itemId)).toList();
				yield (player.getPrivateStoreType() == PrivateStoreType.BUY) && exactBuy(actual, planned);
			}
			case MANUFACTURE -> (player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE) && (player.getManufactureItems().size() == plan.lines().size()) && plan.lines().stream().allMatch(line ->
			{
				final ManufactureItem item = player.getManufactureItems().get(line.objectOrRecipeId());
				return (item != null) && (item.getCost() == line.price());
			});
		};
	}

	private static boolean exactSell(List<TradeItem> actual, List<PhantomStorePlan.Line> planned)
	{
		if (actual.size() != planned.size())
		{
			return false;
		}
		for (int i = 0; i < actual.size(); i++)
		{
			final TradeItem item = actual.get(i);
			final PhantomStorePlan.Line line = planned.get(i);
			if ((item.getObjectId() != line.objectOrRecipeId()) || (item.getItem().getId() != line.itemId()) || (item.getCount() != line.count()) || (item.getPrice() != line.price()))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean exactBuy(List<TradeItem> actual, List<PhantomStorePlan.Line> planned)
	{
		if (actual.size() != planned.size())
		{
			return false;
		}
		for (int i = 0; i < actual.size(); i++)
		{
			final TradeItem item = actual.get(i);
			final PhantomStorePlan.Line line = planned.get(i);
			if ((item.getItem().getId() != line.itemId()) || (item.getCount() != line.count()) || (item.getPrice() != line.price()))
			{
				return false;
			}
		}
		return true;
	}

	private void installOwnerObserver(long profileId, int ownerObjectId)
	{
		if (_ownerObservers.containsKey(profileId))
		{
			return;
		}
		final AutoCloseable lease = PrivateStoreService.getInstance().observeOwner(ownerObjectId, (owner, list, successful) ->
		{
			if (successful)
			{
				refresh(profileId, owner, list);
			}
		});
		final AutoCloseable previous = _ownerObservers.putIfAbsent(profileId, lease);
		if (previous != null)
		{
			close(lease);
		}
	}

	private void refresh(long profileId, Player owner, TradeList list)
	{
		final VersionedPlan stored = find(profileId);
		if ((stored == null) || (stored.plan().type() == PhantomStorePlan.Type.MANUFACTURE))
		{
			return;
		}
		final List<PhantomStorePlan.Line> remaining = new ArrayList<>();
		for (TradeItem item : list.getItems())
		{
			remaining.add(new PhantomStorePlan.Line(item.getObjectId() > 0 ? item.getObjectId() : item.getItem().getId(), item.getItem().getId(), item.getCount(), item.getPrice()));
		}
		if (remaining.isEmpty())
		{
			final var journey = restJourney(profileId).orElse(null);
			_profiles.deleteComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, stored.rowVersion());
			deleteRestJourney(profileId, journey);
			closeOwnerObserver(profileId);
		}
		else
		{
			update(profileId, stored.rowVersion(), stored.plan().withLines(remaining));
		}
	}

	private VersionedPlan save(long profileId, PhantomStorePlan plan)
	{
		final VersionedPlan existing = find(profileId);
		if (existing == null)
		{
			return decode(_profiles.insertComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, PhantomStorePlan.SCHEMA_VERSION, plan.encode()));
		}
		if (existing.plan().contentHash().equals(plan.contentHash()) && (existing.plan().state() == plan.state()))
		{
			return existing;
		}
		return update(profileId, existing.rowVersion(), plan);
	}

	private VersionedPlan update(long profileId, long rowVersion, PhantomStorePlan plan)
	{
		final var journey = restJourney(profileId).orElse(null);
		if (journey != null && journey.goal().status() == PhantomGoalStatus.COMPLETED && !journey.goal().payloadText().equals(plan.contentHash()))
		{
			final var old = journey.goal();
			final var remaining = new PhantomGoal(old.goalId(), old.goalType(), old.status(), old.subject(), old.target(), old.requiredAmount(), old.currentAmount(), old.acquisitionMethod(), old.validSources(), old.selectedAnchor(), old.purposeKey(), old.priority(), old.riskBudget(), old.expenseBudget(), old.deadlineEpochMillis(), old.constraints(), old.reasonKey(), old.revision(), plan.contentHash());
			return _profiles.mutateComponentsAtomically(profileId, List.of(
				new PhantomProfileRepository.ComponentMutation(JOURNEY_COMPONENT, journey.rowVersion(), 1, _journeyCodec.encode(remaining)),
				new PhantomProfileRepository.ComponentMutation(PhantomStorePlan.COMPONENT_TYPE, rowVersion, PhantomStorePlan.SCHEMA_VERSION, plan.encode())))
				.stream().filter(row -> PhantomStorePlan.COMPONENT_TYPE.equals(row.componentType())).map(PhantomStoreService::decode).findFirst().orElseThrow();
		}
		return decode(_profiles.updateComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, rowVersion, PhantomStorePlan.SCHEMA_VERSION, plan.encode()));
	}

	private VersionedPlan find(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomStorePlan.COMPONENT_TYPE).map(PhantomStoreService::decode).orElse(null);
	}

	private static VersionedPlan decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomStorePlan.SCHEMA_VERSION)
		{
			throw new IllegalStateException("Unsupported Phantom store plan schema.");
		}
		return new VersionedPlan(component.rowVersion(), PhantomStorePlan.decode(component.payload()));
	}

	private void closeOwnerObserver(long profileId)
	{
		close(_ownerObservers.remove(profileId));
	}

	private static void close(AutoCloseable lease)
	{
		if (lease != null)
		{
			try
			{
				lease.close();
			}
			catch (Exception exception)
			{
				throw new IllegalStateException("Could not close Phantom store observer.", exception);
			}
		}
	}

	public enum Result
	{
		OPENED,
		CLOSED,
		ACTIVE_REQUIRED,
		RETRY,
		INCONSISTENT,
		REJECTED
	}

	private record VersionedPlan(long rowVersion, PhantomStorePlan plan)
	{
	}

	public record RetainedPlan(int characterObjectId, long rowVersion, PhantomStorePlan plan, RestJourney journey)
	{
		public RetainedPlan(int characterObjectId, long rowVersion, PhantomStorePlan plan) { this(characterObjectId, rowVersion, plan, null); }
		public RetainedPlan
		{
			if (characterObjectId <= 0 || rowVersion < 0) { throw new IllegalArgumentException("Invalid retained store binding."); }
			Objects.requireNonNull(plan);
		}
	}

	/** One finite market trip inside the existing Goal codec; the ordinary farm root remains unchanged. */
	public record RestJourney(long rowVersion, PhantomGoal goal)
	{
		private static final java.util.Set<String> KEYS = java.util.Set.of("owner.revision", "owner.object", "owner.class", "owner.index", "destination.x", "destination.y", "destination.z", "journey.started", "journey.paused", "journey.remaining");
		public RestJourney
		{
			Objects.requireNonNull(goal);
			if (rowVersion < 0 || !java.util.Set.of(PhantomGoalStatus.ACTIVE, PhantomGoalStatus.COMPLETED, PhantomGoalStatus.FAILED).contains(goal.status()) || !"market.rest.travel".equals(goal.goalType()) || !"market.rest".equals(goal.purposeKey()) || goal.selectedAnchor() == null || !"topology.anchor".equals(goal.selectedAnchor().namespace())
				|| !Objects.equals(goal.target(), goal.selectedAnchor()) || goal.subject() == null || !"profile".equals(goal.subject().namespace()) || !goal.validSources().isEmpty()
				|| !goal.constraints().keySet().equals(KEYS) || !PhantomJourneyLifetime.valid(goal) || goal.payloadText() == null || !goal.payloadText().matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Invalid market journey binding."); }
			final var values = goal.constraints();
			if (values.get("owner.revision") < 0 || values.get("owner.object") <= 0 || values.get("owner.object") > Integer.MAX_VALUE || values.get("owner.class") < 0 || values.get("owner.class") > 255 || values.get("owner.index") < 0 || values.get("owner.index") > 255
				|| values.get("journey.paused") != 0 || goal.deadlineEpochMillis() <= values.get("journey.started") || goal.deadlineEpochMillis() - values.get("journey.started") != values.get("journey.remaining")) { throw new IllegalArgumentException("Invalid fixed market journey lifetime or owner."); }
			new PhantomNavigationPoint(Math.toIntExact(values.get("destination.x")), Math.toIntExact(values.get("destination.y")), Math.toIntExact(values.get("destination.z")), 0);
		}
		public static PhantomGoal intent(PhantomGoal root, int objectId, int classId, int classIndex, PhantomStorePlan plan, String anchorId, PhantomNavigationPoint destination, long now)
		{
			if (!supportsRoot(root) || objectId <= 0 || classId < 0 || classIndex < 0 || destination.instanceId() != 0 || plan.expiresEpochMillis() <= now) { throw new IllegalArgumentException("Market trip requires the unchanged active farm/acquisition owner."); }
			final var anchor = new PhantomDomainRef("topology.anchor", anchorId);
			return PhantomJourneyLifetime.start(new PhantomGoal(root.goalId(), "market.rest.travel", PhantomGoalStatus.ACTIVE, root.subject(), anchor, 1, 0, "travel.native", List.of(), anchor, "market.rest", 0, 0, 0, plan.expiresEpochMillis(),
				java.util.Map.of("owner.revision", root.revision(), "owner.object", (long) objectId, "owner.class", (long) classId, "owner.index", (long) classIndex, "destination.x", (long) destination.x(), "destination.y", (long) destination.y(), "destination.z", (long) destination.z()), "market.rest.accepted", 0, plan.contentHash()), now);
		}
		public boolean travelling() { return goal.status() == PhantomGoalStatus.ACTIVE; }
		public PhantomNavigationPoint destination() { return new PhantomNavigationPoint(Math.toIntExact(goal.constraints().get("destination.x")), Math.toIntExact(goal.constraints().get("destination.y")), Math.toIntExact(goal.constraints().get("destination.z")), 0); }
		public boolean bound(RetainedPlan retained, PhantomGoal root, long now)
		{
			return retained != null && supportsRoot(root) && root.goalId() == goal.goalId() && root.revision() == goal.constraints().get("owner.revision")
				&& Objects.equals(root.subject(), goal.subject()) && retained.characterObjectId() == goal.constraints().get("owner.object") && retained.plan().contentHash().equals(goal.payloadText()) && retained.plan().expiresEpochMillis() == goal.deadlineEpochMillis()
				&& goal.status() != PhantomGoalStatus.FAILED && goal.deadlineEpochMillis() > now;
		}
		public boolean matches(Player player) { return player.getObjectId() == goal.constraints().get("owner.object") && player.getActiveClass() == goal.constraints().get("owner.class") && player.getClassIndex() == goal.constraints().get("owner.index"); }
		public static boolean supportsRoot(PhantomGoal root)
		{
			if (root == null || root.status() != PhantomGoalStatus.ACTIVE) { return false; }
			try
			{
				if (PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())) { PhantomBackgroundGoalSpec.parse(root); return true; }
				if (PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(root.goalType())) { PhantomAcquisitionGoalSpec.parse(root); return true; }
			}
			catch (IllegalArgumentException invalid) { return false; }
			return false;
		}
	}


	public record ShutdownResult(boolean successful, List<Long> retryProfileIds, List<Long> inconsistentProfileIds)
	{
		public ShutdownResult
		{
			retryProfileIds = List.copyOf(retryProfileIds);
			inconsistentProfileIds = List.copyOf(inconsistentProfileIds);
		}
	}

	public record Snapshot(long opened, long closed, int retainedOwnerObservers)
	{
	}
}

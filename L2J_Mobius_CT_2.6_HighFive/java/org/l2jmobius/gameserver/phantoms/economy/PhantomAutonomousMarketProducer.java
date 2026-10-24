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
import java.util.function.Predicate;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig.AutonomousPolicy;
import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Creature;
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
import org.l2jmobius.gameserver.model.zone.type.NoStoreZone;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityTransitionStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomNativeJourneyDemand;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** One shared-scheduler owner for bounded ordinary native stores around a planned rest window. */
public final class PhantomAutonomousMarketProducer implements PhantomSchedulerControlPort, PhantomNativeJourneyDemand, PhantomMaterializationLifecyclePort
{
	private static final long ATTEMPT_INTERVAL_MILLIS = 15_000;
	private static final long ROTATION_INTERVAL_MILLIS = 900_000;
	private static final int MAXIMUM_SELL_CANDIDATES = 24;
	private static final int MAXIMUM_LINE_COUNT = 8;
	private static final int MAXIMUM_STORE_LINES = 20;
	private static final String RECOVERY_SIGNAL_SOURCE = "market.accepted_restore";
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
	private volatile boolean _stopping;
	private final ConcurrentHashMap<Long, RecoveryRequest> _recoveries = new ConcurrentHashMap<>();
	private final Set<Long> _cancelledRecoveries = ConcurrentHashMap.newKeySet();
	private final Set<Long> _admittingRest = ConcurrentHashMap.newKeySet();
	// Materialization callbacks are synchronous on their caller thread; this is admission metadata, not another native lease.
	private final ThreadLocal<Map<Long, RecoveryRequest>> _loadingRecoveries = new ThreadLocal<>();
	private volatile Predicate<PhantomTopologyPoint> _recoveryUnobserved = _ -> false;
	private volatile LongPredicate _recoveryScheduleKnown = _ -> false;
	private boolean _recoveryEnabled;
	private BiPredicate<Long, Long> _tradingWindow = (_, _) -> true;
	private boolean _windowInstalled;
	private boolean _admissionStarted;
	private PhantomNavigationService _navigation;
	private PhantomNormalGatekeeperTravel _travel;
	private PhantomBackgroundService _background;
	private BiPredicate<Long, Long> _yieldFarm = (_, _) -> false;
	private BiPredicate<Long, PhantomGoal> _tripAllowed = (_, _) -> false;
	private final Map<Long, RestRoute> _restRoutes = new HashMap<>();

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

	public synchronized void installRecoveryAdmission(Predicate<PhantomTopologyPoint> unobserved, LongPredicate scheduleKnown)
	{
		if (_recoveryEnabled || _admissionStarted) { throw new IllegalStateException("Recovery admission must be bound before market start."); }
		_recoveryUnobserved = Objects.requireNonNull(unobserved);
		_recoveryScheduleKnown = Objects.requireNonNull(scheduleKnown);
		_recoveryEnabled = true;
	}

	public synchronized void installTravel(PhantomNavigationService navigation, PhantomNormalGatekeeperTravel travel, PhantomBackgroundService background, BiPredicate<Long, Long> yieldFarm, BiPredicate<Long, PhantomGoal> allowed)
	{
		if (_navigation != null || _admissionStarted) { throw new IllegalStateException("Market travel must bind before admission."); }
		_navigation = Objects.requireNonNull(navigation); _travel = Objects.requireNonNull(travel); _background = Objects.requireNonNull(background);
		_yieldFarm = Objects.requireNonNull(yieldFarm); _tripAllowed = Objects.requireNonNull(allowed);
	}

	public boolean ownsRestJourney(long profileId, PhantomGoal root)
	{
		final var request = _recoveries.get(profileId);
		return request != null && request.retained().journey() != null && request.retained().journey().bound(request.retained(), root, System.currentTimeMillis());
	}

	/** Bounded durable fences precede both scheduler and population admission. */
	public synchronized void prepareRecoveryOwners()
	{
		if (!_recoveryEnabled || _admissionStarted || _stopping || !OfflineTradeConfig.OFFLINE_TRADE_ENABLE) { return; }
		for (long profileId : _stores.planOwnersAfter(0, _policy.maximumOpenStores()))
		{
			final var retained = _stores.retainedPlan(profileId).orElse(null);
			if (retained == null) { continue; }
			if (retained.plan().expiresEpochMillis() <= System.currentTimeMillis()) { _stores.discardDormant(profileId, retained); continue; }
			final var current = goal(profileId);
			_recoveries.putIfAbsent(profileId, recoveryRequest(retained, current));
			if (retained.journey() != null && retained.journey().goal().status() == PhantomGoalStatus.FAILED) { _cancelledRecoveries.add(profileId); }
		}
	}

	public boolean hasRecovery(long profileId) { return _recoveries.containsKey(profileId) || _admittingRest.contains(profileId); }
	@Override public Set<Long> acceptedProfiles() { return Set.copyOf(_recoveries.keySet()); }
	@Override public boolean requiresNativeContext(long profileId, long rootId, long rootRevision)
	{
		final var request = _recoveries.get(profileId);
		return OfflineTradeConfig.OFFLINE_TRADE_ENABLE && !_stopping && request != null && !_cancelledRecoveries.contains(profileId) && request.retained().plan().expiresEpochMillis() > System.currentTimeMillis()
			&& (request.retained().journey() == null || request.retained().journey().goal().status() != PhantomGoalStatus.FAILED)
			&& (rootId == 0 || request.goalId() == rootId && request.goalRevision() == rootRevision);
	}

	/** Waits for an in-flight producer pulse before the StoreService drain begins. */
	public synchronized void beginStop()
	{
		_stopping = true;
		for (long profileId : List.copyOf(_restRoutes.keySet())) { final var route = _restRoutes.get(profileId); if (route.paidArrival) { checkpointRestPayment(profileId, route); } stopRestRoute(profileId); }
		final var signals = new PhantomSchedulerRelevanceSignalPort(_scheduler);
		_recoveries.forEach((profileId, request) -> signals.withdraw(profileId, RECOVERY_SIGNAL_SOURCE, request.sequence()));
	}

	/** The native owner and accepted live state are rechecked on every attempt. */
	public synchronized void pulse(long now)
	{
		_admissionStarted = true;
		if (_stopping)
		{
			return;
		}
		if (!OfflineTradeConfig.OFFLINE_TRADE_ENABLE)
		{
			if (now < _nextAttempt) { return; }
			_nextAttempt = now + ATTEMPT_INTERVAL_MILLIS;
			for (long profileId : List.copyOf(_opened.keySet())) { close(profileId, now); }
			for (long profileId : List.copyOf(_recoveries.keySet())) { if (!_opened.containsKey(profileId)) { cancelRecovery(profileId); } }
			return;
		}
		for (long profileId : List.copyOf(_opened.keySet()))
		{
			reconcile(profileId, now);
		}
		for (long profileId : List.copyOf(_restRoutes.keySet())) { advanceRestJourney(profileId, now); }
		if (now < _nextAttempt)
		{
			return;
		}
		_nextAttempt = now + ATTEMPT_INTERVAL_MILLIS;
		for (long profileId : List.copyOf(_opened.keySet()))
		{
			reconcileSlow(profileId, now);
		}
		for (long profileId : List.copyOf(_recoveries.keySet()))
		{
			if (!_opened.containsKey(profileId)) { recoverRetained(profileId, now); }
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
				if (!recoverRetained(profileId, now)) { consider(profileId, now); }
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
			if (_recoveryEnabled && !_recoveries.containsKey(profileId))
			{
				recoverRetained(profileId, now);
				return _opened.containsKey(profileId) ? Optional.of(retained) : Optional.empty();
			}
			final PhantomGoal current = goal(profileId);
			final PhantomAcquisitionState state = acquisition(profileId, current);
			final var recovery = _recoveries.get(profileId);
			if (recovery != null && !recovery.validFor(_stores.retainedPlan(profileId).orElse(null), current == null ? 0 : current.goalId(), current == null ? 0 : current.revision(), now)) { cancelRecovery(profileId); return Optional.empty(); }
			if (recovery != null && (owner == null || !stableActive(profileId))) { return Optional.empty(); }
			if (recovery != null && recovery.retained().journey() != null && recovery.retained().journey().travelling())
			{
				advanceRestJourney(profileId, now); return Optional.empty();
			}
			if (recovery != null && (!recoveryLocation(owner) || !_recoveryUnobserved.test(new PhantomTopologyPoint(owner.getX(), owner.getY(), owner.getZ(), owner.getInstanceId())))) { return Optional.empty(); }
			if ((retained.expiresEpochMillis() <= now) || (owner == null) || !stableActive(profileId) || !retainedEligible(owner, retained, current, state) || owner.isInStoreMode() || !ownerReady(profileId, owner) || _reservations.findActive(profileId).isPresent())
			{
				close(profileId, now);
				return Optional.empty();
			}
			if (_opened.size() >= (recovery == null ? Math.min(_policy.maximumOpenStores(), participationCap) : _policy.maximumOpenStores()))
			{
				return Optional.empty();
			}
			if (publish(profileId, owner, current, retained, now))
			{
				track(profileId, retained, current);
				return Optional.of(retained);
			}
			close(profileId, now);
			return Optional.empty();
		}
		if (!stableActive(profileId) || (owner == null) || (occupiedStoreSlots() >= Math.min(_policy.maximumOpenStores(), participationCap)) || (Math.floorMod(profileId + (now / ROTATION_INTERVAL_MILLIS), 2) != 0) || ((now - _lastClosed.getOrDefault(profileId, Long.MIN_VALUE / 2)) < (_policy.reopenCooldownSeconds() * 1000L)))
		{
			return Optional.empty();
		}
		if (!ownerQuiescent(profileId, owner))
		{
			return Optional.empty();
		}
		if (_reservations.findActive(profileId).isPresent())
		{
			return Optional.empty();
		}
		final PhantomGoal current = goal(profileId);
		final PhantomAcquisitionState state = acquisition(profileId, current);
		if (!legalLocation(owner))
		{
			if (_navigation == null || !PhantomStoreService.RestJourney.supportsRoot(current) || !_tripAllowed.test(profileId, current)) { return Optional.empty(); }
			final var plan = buy(profileId, owner, current, state, now).or(() -> manufacture(profileId, owner, current, state, now)).or(() -> sell(profileId, owner, current, state, now));
			plan.ifPresent(quote -> requestRestJourney(profileId, owner, current, quote, now));
			return Optional.empty();
		}
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

	private int occupiedStoreSlots()
	{
		final var owners = new java.util.HashSet<>(_opened.keySet()); owners.addAll(_recoveries.keySet()); return owners.size();
	}

	private boolean recoverRetained(long profileId, long now)
	{
		if (!_recoveryEnabled) { return false; }
		var request = _recoveries.get(profileId);
		if (request != null && _cancelledRecoveries.contains(profileId)) { cancelRecovery(profileId); return true; }
		final var retained = _stores.retainedPlan(profileId).orElse(null);
		if (retained == null) { releaseRecovery(profileId); return true; }
		if (!_recoveryScheduleKnown.test(profileId)) { return true; }
		final var current = goal(profileId);
		if (retained.plan().expiresEpochMillis() <= now || !OfflineTradeConfig.OFFLINE_TRADE_ENABLE || !_tradingWindow.test(profileId, now)
			|| !marketGoalEligible(current, acquisition(profileId, current)))
		{
			if (request != null) { cancelRecovery(profileId); }
			else { _stores.discardDormant(profileId, retained); }
			return true;
		}
		if (request == null)
		{
			if (occupiedStoreSlots() >= _policy.maximumOpenStores()) { return true; }
			request = recoveryRequest(retained, current);
			_recoveries.put(profileId, request); // Fence ordinary Decision before requesting the existing Player admission queue.
		}
		if (!request.validFor(retained, current == null ? 0 : current.goalId(), current == null ? 0 : current.revision(), now)) { cancelRecovery(profileId); return true; }
		if (retained.journey() != null && !retained.journey().bound(retained, current, now)) { cancelRecovery(profileId); return true; }
		if (_materialization.find(profileId).isPresent()) { consider(profileId, now); return true; }
		final long ttl = Math.max(1, Math.min(PhantomRelevanceSignal.MAXIMUM_TTL_MILLIS, retained.plan().expiresEpochMillis() - now));
		new PhantomSchedulerRelevanceSignalPort(_scheduler).submit(profileId, new PhantomRelevanceSignal(RECOVERY_SIGNAL_SOURCE, request.sequence(), PhantomActivityState.ACTIVE, ttl));
		return true;
	}

	private void cancelRecovery(long profileId)
	{
		final var request = _recoveries.get(profileId);
		if (request == null) { return; }
		_cancelledRecoveries.add(profileId);
		new PhantomSchedulerRelevanceSignalPort(_scheduler).withdraw(profileId, RECOVERY_SIGNAL_SOURCE, request.sequence());
		if (!stopRestRoute(profileId)) { return; }
		final var current = _stores.retainedPlan(profileId).orElse(null);
		if (current == null) { releaseRecovery(profileId); return; }
		if (!_opened.containsKey(profileId) && !current.equals(request.retained())) { return; }
		final var result = _opened.containsKey(profileId) ? _stores.close(profileId) : _stores.discardRecovery(profileId, request.retained());
		if (result == PhantomStoreService.Result.CLOSED) { _lastClosed.put(profileId, System.currentTimeMillis()); releaseRecovery(profileId); }
	}

	private void releaseRecovery(long profileId)
	{
		final var request = _recoveries.remove(profileId);
		_cancelledRecoveries.remove(profileId);
		if (request != null) { new PhantomSchedulerRelevanceSignalPort(_scheduler).withdraw(profileId, RECOVERY_SIGNAL_SOURCE, request.sequence()); }
	}

	@Override public void beforeMaterialize(long profileId, int characterObjectId)
	{
		final var request = _recoveries.get(profileId);
		if (request == null)
		{
			if (_recoveryEnabled && OfflineTradeConfig.OFFLINE_TRADE_ENABLE && _stores.retainedPlan(profileId).isPresent()) { throw new PhantomMaterializationLifecyclePort.AdmissionRejectedException("market.restore.awaiting_bounded_admission"); }
			return;
		}
		if (_stopping || !OfflineTradeConfig.OFFLINE_TRADE_ENABLE || _cancelledRecoveries.contains(profileId) || !_recoveryScheduleKnown.test(profileId) || request.retained().characterObjectId() != characterObjectId || request.retained().plan().expiresEpochMillis() <= System.currentTimeMillis())
		{
			throw new PhantomMaterializationLifecyclePort.AdmissionRejectedException("market.restore.binding_or_expiry");
		}
		var loading = _loadingRecoveries.get();
		if (loading == null) { loading = new HashMap<>(); _loadingRecoveries.set(loading); }
		loading.put(profileId, request);
	}

	@Override public void beforeWorldSpawn(long profileId, Player player)
	{
		final var attempts = _loadingRecoveries.get();
		final var loading = attempts == null ? null : attempts.get(profileId);
		final var request = _recoveries.get(profileId);
		if (loading != null && !loading.equals(request)) { throw new PhantomMaterializationLifecyclePort.AdmissionRejectedException("market.restore.cancelled_loading"); }
		if (request == null)
		{
			if (_recoveryEnabled && OfflineTradeConfig.OFFLINE_TRADE_ENABLE && _stores.retainedPlan(profileId).isPresent()) { throw new PhantomMaterializationLifecyclePort.AdmissionRejectedException("market.restore.missing_admission"); }
			return;
		}
		if (!OfflineTradeConfig.OFFLINE_TRADE_ENABLE) { throw new PhantomMaterializationLifecyclePort.AdmissionRejectedException("market.restore.disabled"); }
		final var current = goal(profileId);
		final var retained = _stores.retainedPlan(profileId).orElse(null);
		final var point = new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), player.getInstanceId());
		final var journey = request.retained().journey();
		if (_stopping || _cancelledRecoveries.contains(profileId) || !OfflineTradeConfig.OFFLINE_TRADE_ENABLE || !_tradingWindow.test(profileId, System.currentTimeMillis())
			|| player.getObjectId() != request.retained().characterObjectId() || !player.hasHeadlessOutboundSession()
			|| !request.validFor(retained, current == null ? 0 : current.goalId(), current == null ? 0 : current.revision(), System.currentTimeMillis())
			|| journey != null && (!journey.bound(retained, current, System.currentTimeMillis()) || !journey.matches(player) || !validRestDestination(journey.goal().selectedAnchor().key(), journey.destination(), player))
			|| !retainedEligible(player, request.retained().plan(), current, acquisition(profileId, current)) || !_stores.matchesQuote(player, request.retained().plan()))
		{
			_cancelledRecoveries.add(profileId);
			throw new PhantomMaterializationLifecyclePort.AdmissionRejectedException("market.restore.changed");
		}
		if (!(journey != null && journey.travelling() ? routeLocation(player) : recoveryLocation(player)) || !_recoveryUnobserved.test(point) || _reservations.findActive(profileId).isPresent())
		{
			throw new PhantomMaterializationLifecyclePort.AdmissionRejectedException("market.restore.observed_or_busy");
		}
	}

	@Override public void afterPlayerLoad(long profileId, Player player) { }
	@Override public void materializeSucceeded(long profileId, int characterObjectId) { finishLoadingRecovery(profileId, characterObjectId); }
	@Override public void materializeAborted(long profileId, int characterObjectId) { finishLoadingRecovery(profileId, characterObjectId); }
	@Override public void beforeStore(long profileId, Player player) { }
	@Override public void afterStore(long profileId, Player player) { }

	private void finishLoadingRecovery(long profileId, int characterObjectId)
	{
		final var attempts = _loadingRecoveries.get();
		if (attempts == null) { return; }
		final var request = attempts.get(profileId);
		if (request != null && request.retained().characterObjectId() == characterObjectId) { attempts.remove(profileId); }
		if (attempts.isEmpty()) { _loadingRecoveries.remove(); }
	}

	private RecoveryRequest recoveryRequest(PhantomStoreService.RetainedPlan retained, PhantomGoal root)
	{
		final var child = retained.journey();
		return new RecoveryRequest(retained, child == null ? root == null ? 0 : root.goalId() : child.goal().goalId(),
			child == null ? root == null ? 0 : root.revision() : child.goal().constraints().get("owner.revision"), Math.max(1, System.nanoTime()));
	}

	private void requestRestJourney(long profileId, Player player, PhantomGoal root, PhantomStorePlan plan, long now)
	{
		if (!_yieldFarm.test(profileId, plan.expiresEpochMillis()) || !ownerQuiescent(profileId, player) || !sameGoal(root, goal(profileId)) || !retainedEligible(player, plan, root, acquisition(profileId, root))) { return; }
		_admittingRest.add(profileId);
		try
		{
		if (!ownerQuiescent(profileId, player) || !_tripAllowed.test(profileId, root) || !sameGoal(root, goal(profileId))) { return; }
		final var origin = PhantomNativeTravelActions.point(player);
		final var sites = _travel.topology().snapshot().anchors().stream().filter(anchor -> anchor.role() == PhantomTopologyAnchorRole.CITY_CENTER)
			.sorted(Comparator.comparingInt((org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor anchor) -> "giran.city.center".equals(anchor.id()) ? 0 : 1)
				.thenComparingDouble(anchor -> Math.hypot((long) anchor.point().x() - origin.x(), (long) anchor.point().y() - origin.y()))).limit(3).toList();
		for (var anchor : sites)
		{
			final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, player.getHeading()).orElse(null);
			if (canonical == null) { continue; }
			for (int[] offset : new int[][] {{0,0},{64,0},{-64,0},{0,64},{0,-64},{64,64},{-64,-64},{64,-64},{-64,64}})
			{
				final var geo = GeoEngine.getInstance(); final int x = canonical.x() + offset[0], y = canonical.y() + offset[1];
				if (!geo.hasGeo(x, y)) { continue; }
				final var destination = new PhantomNavigationPoint(x, y, geo.getHeight(x, y, canonical.z()), 0);
				if (!validRestDestination(anchor.id(), destination, player) || !plausibleRoute(player, origin, destination, plan.expiresEpochMillis() - now, restTravelBudget(plan, player.getAdena()))) { continue; }
				final var saved = _stores.requestRestJourney(profileId, player, root, plan, anchor.id(), destination, now).orElse(null);
				if (saved != null) { _recoveries.put(profileId, recoveryRequest(saved, root)); advanceRestJourney(profileId, now); }
				return;
			}
		}
		}
		finally { _admittingRest.remove(profileId); if (!_recoveries.containsKey(profileId)) { _lastClosed.put(profileId, now); } }
	}

	/** Only complete existing-planner candidates are admitted; no distant straight-line substitute. */
	private boolean plausibleRoute(Player player, PhantomNavigationPoint from, PhantomNavigationPoint to, long remainingMillis, long availableAdena)
	{
		final double maximum = Math.min(_navigation.policy().maximumLocalStraightDistance(), player.getMoveSpeed() * Math.max(0, remainingMillis) / 1000.0);
		if (from.distanceTo(to) <= maximum) { return true; }
		return _travel.legs().stream().anyMatch(leg -> Math.hypot((long) from.x() - leg.sourceX(), (long) from.y() - leg.sourceY()) <= maximum
			&& Math.hypot((long) to.x() - leg.destinationX(), (long) to.y() - leg.destinationY()) <= maximum
			&& PhantomNormalGatekeeperTravel.fee(leg, player.getClassIndex(), player.getLevel(), System.currentTimeMillis() / 60_000) <= availableAdena && PhantomNativeTravelActions.available(leg));
	}

	private boolean freeDestination(Player player, PhantomNavigationPoint point)
	{
		final var zones = ZoneManager.getInstance().getZones(point.x(), point.y(), point.z());
		if (zones.stream().noneMatch(zone -> zone instanceof PeaceZone) || zones.stream().anyMatch(zone -> zone instanceof NoStoreZone)
			|| !_recoveryUnobserved.test(new PhantomTopologyPoint(point.x(), point.y(), point.z(), 0))) { return false; }
		for (var request : _recoveries.values())
		{
			final var child = request.retained().journey();
			if (child != null && request.retained().characterObjectId() != player.getObjectId() && child.destination().distanceTo(point) < 60) { return false; }
		}
		final var region = World.getInstance().getRegion(point.x(), point.y(), point.z());
		if (region == null) { return false; }
		return java.util.Arrays.stream(region.getSurroundingRegions()).flatMap(value -> value.getVisibleObjects().stream()).filter(value -> value != player && value instanceof Creature)
			.map(value -> (Creature) value).noneMatch(other -> other.getInstanceId() == 0 && Math.hypot((long) other.getX() - point.x(), (long) other.getY() - point.y()) < (other instanceof Player ? 60 : Math.max(other.getMinShopDistance(), 60)));
	}

	private boolean validRestDestination(String anchorId, PhantomNavigationPoint destination, Player player)
	{
		if (_travel == null || destination.instanceId() != 0) { return false; }
		final var anchor = _travel.topology().findAnchor(anchorId).filter(value -> value.role() == PhantomTopologyAnchorRole.CITY_CENTER).orElse(null);
		if (anchor == null) { return false; }
		final var canonical = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, player.getHeading()).orElse(null);
		final var geo = GeoEngine.getInstance();
		return canonical != null && geo.hasGeo(destination.x(), destination.y()) && geo.getHeight(destination.x(), destination.y(), destination.z()) == destination.z()
			&& new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z(), 0).distanceTo(destination) <= anchor.validationTolerance() && freeDestination(player, destination);
	}

	private void advanceRestJourney(long profileId, long now)
	{
		final var pending = _restRoutes.get(profileId);
		if (pending != null && pending.paidArrival) { checkpointRestPayment(profileId, pending); return; }
		if (_cancelledRecoveries.contains(profileId)) { cancelRecovery(profileId); return; }
		final var request = _recoveries.get(profileId);
		if (request == null || request.retained().journey() == null || !request.retained().journey().travelling() || _navigation == null) { return; }
		final var child = request.retained().journey(); final var player = liveOwner(profileId);
		if (player == null || !stableActive(profileId)) { return; }
		var route = _restRoutes.get(profileId);
		final long epoch = _materialization.find(profileId).map(value -> value.materializedAtNanos()).orElse(0L);
		if (route != null && (route.player != player || route.epoch != epoch || !route.binding.equals(request.retained()))) { if (!stopRestRoute(profileId)) { return; } route = null; }
		if (route == null)
		{
			if (player.isMoving()) { return; }
			route = new RestRoute(player, epoch, request.retained(), goal(profileId)); _restRoutes.put(profileId, route);
		}
		if (now >= route.nextFresh)
		{
			route.nextFresh = now + ATTEMPT_INTERVAL_MILLIS;
			if (!child.bound(_stores.retainedPlan(profileId).orElse(null), goal(profileId), now) || !sameGoal(route.root, goal(profileId)) || !retainedEligible(player, request.retained().plan(), route.root, acquisition(profileId, route.root)) || _reservations.findActive(profileId).isPresent()) { cancelRecovery(profileId); return; }
		}
		if (_stopping || !OfflineTradeConfig.OFFLINE_TRADE_ENABLE || !_tradingWindow.test(profileId, now) || child.goal().deadlineEpochMillis() <= now || !child.matches(player)
			|| !sameRuntimeGoal(profileId, new Opened(request.retained().plan(), request.goalId(), request.goalRevision())) || !routeLocation(player)
			|| !validRestDestination(child.goal().selectedAnchor().key(), child.destination(), player)) { cancelRecovery(profileId); return; }
		if (route.checkpoint != null)
		{
			if (!_background.captureVisibleArrival(profileId, player, route.root, route.checkpoint)) { return; }
			route.checkpoint = null;
		}
		if (route.plan != null && route.stage >= route.plan.stages().size())
		{
			if (!freeDestination(player, child.destination()) || !ownerReady(profileId, player) || !sameGoal(route.root, goal(profileId))) { cancelRecovery(profileId); return; }
			if (!_background.captureVisibleArrival(profileId, player, route.root, child.goal().selectedAnchor().key())) { return; }
			final var arrived = _stores.finishRestJourney(profileId, request.retained(), true).orElse(null);
			if (arrived == null) { cancelRecovery(profileId); return; }
			stopRestRoute(profileId); _recoveries.put(profileId, recoveryRequest(arrived, route.root)); consider(profileId, now); return;
		}
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null || lease.player() != player) { return; }
			if (!PhantomNativeTravelActions.ordinary(player))
			{
				if (route.walk != null) { PhantomNativeTravelActions.cancelWalk(route.walk); }
				failRestRoute(profileId, request, false); return;
			}
			if (player.isMovementDisabled() || player.getMoveSpeed() <= 0 || player.isAttackingNow() || player.isProcessingTransaction() || player.hasPendingOwnedStore()) { return; }
			if (route.plan == null)
			{
				if (route.requestId == 0)
				{
					final long logical = System.nanoTime();
					final var options = PhantomNativeTravelActions.snapshot(player, _travel.legs(), Math.max(0, child.goal().deadlineEpochMillis() - now));
					final var noReturn = new PhantomTravelStrategy.Options(options.legs(), options.speed(), restTravelBudget(request.retained().plan(), options.adena()), 0, options.classIndex(), options.level(), options.epochMinute(), options.gatekeeperAllowed(), "market.no_return", null, 0, options.remainingMillis(), options.blockedEdges());
					final var submission = _navigation.submitTravelPlan(new PhantomNavigationRequest(profileId, PhantomNativeTravelActions.point(player), child.destination(), logical, logical + _navigation.policy().defaultRequestDeadlineNanos(), _navigation.policy().maximumRouteDistance()), noReturn);
					route.requestId = submission.requestId();
					if (submission.status() == PhantomNavigationService.SubmissionStatus.REJECTED) { route.requestId = 0; return; }
				}
				final var result = _navigation.consume(route.requestId).orElse(null);
				if (result == null) { return; }
				route.plan = _navigation.consumeTravelPlan(route.requestId).orElse(null);
				if (!completeRestRoute(route.plan, PhantomNativeTravelActions.point(player), child.destination(), restTravelBudget(request.retained().plan(), player.getAdena()), child.goal().deadlineEpochMillis() - now)) { failRestRoute(profileId, request, false); return; }
			}
			final var stage = route.plan.stages().get(route.stage);
			if (stage.mode() == PhantomTravelStrategy.Mode.GK)
			{
				if (!_recoveryUnobserved.test(new PhantomTopologyPoint(player.getX(), player.getY(), player.getZ(), 0)) || !_recoveryUnobserved.test(new PhantomTopologyPoint(stage.to().x(), stage.to().y(), stage.to().z(), 0))) { return; }
				if (!sameGoal(route.root, goal(profileId)) || !retainedEligible(player, request.retained().plan(), route.root, acquisition(profileId, route.root))
					|| PhantomNormalGatekeeperTravel.fee(stage.gatekeeper(), player.getClassIndex(), player.getLevel(), now / 60_000) > restTravelBudget(request.retained().plan(), player.getAdena())) { failRestRoute(profileId, request, false); return; }
				// Arm a terminal durable marker before the native side effect. Any ambiguous exception/restart cannot replay a fee.
				final var armed = _stores.finishRestJourney(profileId, request.retained(), false).orElse(null);
				if (armed == null) { failRestRoute(profileId, request, false); return; }
				_recoveries.put(profileId, recoveryRequest(armed, route.root)); route.binding = armed;
				final PhantomNativeTravelActions.GatekeeperResult receipt;
				try { receipt = PhantomNativeTravelActions.gatekeeper(player, stage.gatekeeper()); }
				catch (RuntimeException failure) { _cancelledRecoveries.add(profileId); return; }
				if (!acceptedRestGatekeeper(receipt.status())) { _cancelledRecoveries.add(profileId); return; }
				route.paidArrival = true; route.checkpoint = stage.gatekeeper().toAnchorId(); return;
			}
			if (stage.mode() != PhantomTravelStrategy.Mode.WALK) { failRestRoute(profileId, request, false); return; }
			final var waypoint = stage.points().get(route.waypoint);
			if (PhantomNativeTravelActions.point(player).distanceTo(waypoint) <= Math.min(16, _navigation.arrivalRadius()))
			{
				if (route.waypoint + 1 < stage.points().size()) { route.waypoint++; }
				else { if (route.walk != null) { PhantomNativeTravelActions.cancelWalk(route.walk); route.walk = null; } route.stage++; route.waypoint = 0; }
				return;
			}
			if (player.isMoving()) { return; }
			if (!new L2jNavigationBackend().canWalkDirect(PhantomNativeTravelActions.point(player), waypoint)
				|| !PhantomNativeTravelActions.safePveWalkSegment(player, waypoint.x(), waypoint.y(), waypoint.z(), World.getInstance().getVisibleObjects(player, org.l2jmobius.gameserver.model.actor.instance.Monster.class).stream().toList(), java.util.Set.of())) { failRestRoute(profileId, request, false); return; }
			player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.MOVE_TO, new org.l2jmobius.gameserver.model.Location(waypoint.x(), waypoint.y(), waypoint.z()));
			route.walk = PhantomNativeTravelActions.captureWalk(player);
			if (route.walk == null || !new L2jNavigationBackend().canWalkDirect(PhantomNativeTravelActions.point(player), route.walk.destination()))
			{
				if (route.walk != null) { PhantomNativeTravelActions.cancelWalk(route.walk); }
				failRestRoute(profileId, request, false);
			}
		}
	}

	public static boolean completeRestRoute(PhantomTravelStrategy.Candidate plan, PhantomNavigationPoint origin, PhantomNavigationPoint destination, long adena, long remainingMillis)
	{
		return plan != null && plan.stages().getFirst().from().equals(origin) && plan.stages().getLast().to().equals(destination) && plan.stages().stream().allMatch(stage -> stage.mode() == PhantomTravelStrategy.Mode.WALK || stage.mode() == PhantomTravelStrategy.Mode.GK)
			&& PhantomTravelStrategy.select(List.of(plan), adena, 0, Math.max(0, remainingMillis)).status() == PhantomTravelStrategy.Status.FOUND;
	}

	public static boolean acceptedRestGatekeeper(PhantomNativeTravelActions.Status status) { return status == PhantomNativeTravelActions.Status.ARRIVED; }

	public static long restTravelBudget(PhantomStorePlan plan, long adena)
	{
		if (adena < 0) { throw new IllegalArgumentException("Negative native currency."); }
		long reserved = 0;
		if (plan.type() == PhantomStorePlan.Type.BUY) { for (var line : plan.lines()) { reserved = Math.addExact(reserved, Math.multiplyExact(line.count(), line.price())); } }
		return Math.max(0, adena - reserved);
	}

	private void checkpointRestPayment(long profileId, RestRoute route)
	{
		if (!sameGoal(route.root, goal(profileId)) || route.binding.plan().expiresEpochMillis() <= System.currentTimeMillis()) { _cancelledRecoveries.add(profileId); route.paidArrival = false; return; }
		if (!_background.captureVisibleArrival(profileId, route.player, route.root, route.checkpoint)) { return; }
		final var resumed = _stores.resumeRestJourney(profileId, route.binding).orElse(null);
		if (resumed == null) { _cancelledRecoveries.add(profileId); return; }
		_recoveries.put(profileId, recoveryRequest(resumed, route.root)); route.paidArrival = false; stopRestRoute(profileId);
		// Resume by a fresh complete plan from factual saved coordinates, never a persisted paid stage index.
	}

	private void failRestRoute(long profileId, RecoveryRequest request, boolean uncertainPayment)
	{
		if (uncertainPayment)
		{
			final var failed = _stores.finishRestJourney(profileId, request.retained(), false).orElse(null);
			if (failed != null) { _recoveries.put(profileId, recoveryRequest(failed, goal(profileId))); }
		}
		_cancelledRecoveries.add(profileId); // Cleanup retries only; this route can never issue its next native stage.
	}

	private boolean stopRestRoute(long profileId)
	{
		final var route = _restRoutes.get(profileId);
		if (route == null) { return true; }
		if (route.walk != null && route.player.isMoving())
		{
			try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (lease == null || lease.player() != route.player || !PhantomNativeTravelActions.cancelWalk(route.walk)) { return false; }
			}
		}
		if (route.requestId > 0) { _navigation.cancel(profileId, route.requestId); _navigation.consume(route.requestId); _navigation.consumeTravelPlan(route.requestId); }
		_restRoutes.remove(profileId, route); return true;
	}

	private static boolean routeLocation(Player player)
	{
		final var geo = GeoEngine.getInstance();
		return player.getInstanceId() == 0 && !player.isDead() && !player.isInsideZone(ZoneId.WATER) && geo.hasGeo(player.getX(), player.getY()) && Math.abs((long) geo.getHeight(player.getX(), player.getY(), player.getZ()) - player.getZ()) <= 64;
	}

	private static final class RestRoute
	{
		private final Player player; private final long epoch; private PhantomStoreService.RetainedPlan binding; private final PhantomGoal root;
		private long requestId, nextFresh; private PhantomTravelStrategy.Candidate plan; private int stage, waypoint; private String checkpoint; private boolean paidArrival; private PhantomNativeTravelActions.OwnedWalk walk;
		private RestRoute(Player actor, long lifetime, PhantomStoreService.RetainedPlan retained, PhantomGoal originalRoot) { player = actor; epoch = lifetime; binding = retained; root = originalRoot; }
	}

	public record RecoveryRequest(PhantomStoreService.RetainedPlan retained, long goalId, long goalRevision, long sequence)
	{
		public RecoveryRequest
		{
			Objects.requireNonNull(retained);
			if (goalId < 0 || goalRevision < 0 || sequence < 1) { throw new IllegalArgumentException("Invalid store recovery request."); }
		}
		public boolean validFor(PhantomStoreService.RetainedPlan current, long rootId, long rootRevision, long now)
		{
			return retained.equals(current) && goalId == rootId && goalRevision == rootRevision && now < retained.plan().expiresEpochMillis();
		}
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
			releaseRecovery(profileId);
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
		return ownerQuiescent(profileId, owner) && owner.canOpenPrivateStore() && legalLocation(owner);
	}

	private boolean ownerQuiescent(long profileId, Player owner)
	{
		final var decision = _decision.get();
		final var runtime = decision == null ? null : decision.find(profileId).orElse(null);
		return (runtime == null || (!runtime.inFlight() && !runtime.persistenceInFlight())) && (owner.getParty() == null) && !owner.isInCombat() && !owner.isMoving() && !owner.isAttackingNow() && !owner.isInStoreMode()
			&& !owner.isProcessingTransaction() && !owner.hasPendingOwnedStore() && !RecipeManager.getInstance().isMakingItem(owner) && owner.canMakeSocialAction() && !owner.isTeleporting();
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

	private static boolean recoveryLocation(Player owner)
	{
		if (owner.getInstanceId() != 0 || owner.isDead() || owner.isInStoreMode() || owner.isProcessingTransaction() || owner.hasPendingOwnedStore()) { return false; }
		final var geo = GeoEngine.getInstance();
		final int x = owner.getX(), y = owner.getY(), z = owner.getZ();
		final var zones = ZoneManager.getInstance().getZones(x, y, z);
		if (!geo.hasGeo(x, y) || Math.abs((long) geo.getHeight(x, y, z) - z) > 64 || zones.stream().anyMatch(zone -> zone instanceof NoStoreZone)
			|| zones.stream().noneMatch(zone -> zone instanceof PeaceZone)) { return false; }
		return World.getInstance().getVisibleObjects(owner, Creature.class).stream().noneMatch(other -> other != owner
			&& (other instanceof Player ? other.calculateDistance3D(owner) < 60 : other.getMinShopDistance() > 0 && other.calculateDistance3D(owner) < other.getMinShopDistance()));
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

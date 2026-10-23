/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.item.type.EtcItemType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;

/** Ordinary Decision producer and bounded native NPC trip; payment stays in the existing Commerce owner. */
public final class L2jPhantomSupplyJourney implements PhantomMaterializationLifecyclePort, org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort, org.l2jmobius.gameserver.phantoms.activity.PhantomNativeJourneyDemand
{
	private static final String ACTION = "supplies.autonomous";
	private final PhantomSupplyIntentStore _store;
	private final PhantomGoalStore _roots;
	private final PhantomMaterializationService _materialization;
	private final PhantomCommerceService _commerce;
	private final PhantomCommerceReceiptStore _receipts;
	private final PhantomNavigationService _navigation;
	private final Supplier<PhantomGameKnowledgeQuery> _knowledge;
	private final PhantomNormalGatekeeperTravel _travel;
	private final LongPredicate _allowed;
	private final BiPredicate<Long, Long> _pauseFarm;
	private final BiPredicate<Long, PhantomGoal> _learningOwns;
	private final PhantomAutonomousSupplyPolicy _policy;
	private final Map<Long, Cached> _cache = new LinkedHashMap<>();
	private final Map<Long, Trip> _trips = new LinkedHashMap<>();
	private final Set<Long> _active = new HashSet<>();
	private final L2jNavigationBackend _native = new L2jNavigationBackend();
	private List<SpawnFact> _merchants = List.of();
	private List<SpawnFact> _warehouses = List.of();
	private Map<Integer, Integer> _sellLists = Map.of();
	private LongPredicate _maintenanceAllowed = _ -> false;

	public void useInventoryMaintenance(LongPredicate allowed)
	{
		if (_state != State.NEW) { throw new IllegalStateException("Inventory maintenance must bind before start."); }
		_maintenanceAllowed = java.util.Objects.requireNonNull(allowed);
	}
	private List<ItemTemplate> _healingItems = List.of();
	private List<ItemTemplate> _cpItems = List.of();
	private String _knowledgeHash;
	private volatile State _state = State.NEW;
	private PhantomOfflineSupply _offline;
	private org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService _progression;
	private BiPredicate<Long, PhantomGoal> _equipmentWeaponProtected = (_, _) -> false;
	private List<ItemTemplate> _equipment = List.of();
	public void useEquipment(org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService progression, BiPredicate<Long, PhantomGoal> weaponProtected)
	{
		if (_state != State.NEW || _progression != null) { throw new IllegalStateException("Supply equipment must bind once before start."); }
		_progression = java.util.Objects.requireNonNull(progression); _equipmentWeaponProtected = java.util.Objects.requireNonNull(weaponProtected);
	}
	private java.util.function.BiPredicate<PhantomGoal, Player> _fieldReady = (_, _) -> true;
	public void requireField(java.util.function.BiPredicate<PhantomGoal, Player> predicate)
	{
		if (_state != State.NEW) { throw new IllegalStateException("Supply field must bind before start."); } _fieldReady = java.util.Objects.requireNonNull(predicate);
	}
	private org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository _profiles;
	private org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort _signals;
	private final Map<Long, Long> _demands = new LinkedHashMap<>();
	private final org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction _resumeTransactions = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction();
	private long _resumeCursor, _nextResumeMillis;
	private static final String SIGNAL_SOURCE = "supplies.accepted";

	public void useNativeResume(org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository profiles, org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort signals)
	{
		if (_state != State.NEW || _profiles != null) { throw new IllegalStateException("Supply resume must bind once before start."); }
		_profiles = java.util.Objects.requireNonNull(profiles); _signals = java.util.Objects.requireNonNull(signals);
	}

	private static boolean alive(PhantomGoal goal, long now)
	{
		return goal != null && (org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.alive(goal, now) || PhantomOfflineSupply.returning(goal) && goal.deadlineEpochMillis() > now);
	}

	@Override public synchronized Set<Long> acceptedProfiles() { return Set.copyOf(_demands.keySet()); }
	@Override public synchronized boolean requiresNativeContext(long profileId, long rootId, long rootRevision)
	{
		final var cached = _cache.get(profileId); final var goal = cached == null ? null : cached.goal;
		return _state == State.RUNNING && _demands.containsKey(profileId) && _allowed.test(profileId) && alive(goal, System.currentTimeMillis())
			&& (rootId == 0 || goal.goalId() == rootId && Binding.parse(goal).rootRevision() == rootRevision);
	}
	private synchronized void accepted(long id, PhantomGoal goal)
	{
		if (_signals == null || !alive(goal, System.currentTimeMillis()) || _demands.size() >= _policy.maximumProfiles() && !_demands.containsKey(id)) { return; }
		final long sequence = Math.max(Math.max(1, System.nanoTime()), _demands.getOrDefault(id, 0L) + 1);
		_demands.put(id, sequence);
		_signals.submit(id, new org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal(SIGNAL_SOURCE, sequence, PhantomActivityState.ACTIVE, 300_000));
	}
	private synchronized void releaseDemand(long id)
	{
		final var sequence = _demands.remove(id); if (sequence != null && _signals != null) { _signals.withdraw(id, SIGNAL_SOURCE, sequence); }
	}

	@Override public void onPulse()
	{
		final long now = System.currentTimeMillis();
		if (_state != State.RUNNING || _profiles == null || now < _nextResumeMillis) { return; }
		_nextResumeMillis = now + 30_000;
		final var page = _profiles.listManagedAfter(PhantomSupplyIntentStore.COMPONENT, _resumeCursor, 8);
		_resumeCursor = page.size() < 8 ? 0 : page.getLast().profile().profileId();
		for (var row : page)
		{
			final long id = row.profile().profileId();
			if (row.component().componentSchemaVersion() != 1 || row.profile().characterObjectId() == null || !_allowed.test(id)) { continue; }
			try
			{
				final var intent = cached(id).goal; final var root = _roots.load(id).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
				if (!PhantomSupplyIntentStore.bound(intent, root) || !alive(intent, now) || Binding.parse(intent).actorObjectId() != row.profile().characterObjectId()) { releaseDemand(id); continue; }
				final var binding = Binding.parse(intent);
				try (var lease = _materialization.tryAcquireAction(id).orElse(null))
				{
					if (lease != null)
					{
					if (!matches(intent, lease.player())) { releaseDemand(id); continue; }
						if (lease.player().isAlikeDead()) { pause(id, root, lease.player()); }
						accepted(id, cached(id).goal); continue;
					}
				}
				final var loaded = _resumeTransactions.load(id);
				if (!loaded.successful() || loaded.state().state() == org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.INCONSISTENT) { continue; }
				final var identity = loaded.state().identity();
				if (identity.characterObjectId() == binding.actorObjectId() && identity.activeClassId() == binding.classId() && identity.classIndex() == binding.classIndex()) { accepted(id, intent); }
			}
			catch (IllegalArgumentException invalid) { releaseDemand(id); }
		}
	}

	private void pause(long id, PhantomGoal root, Player player)
	{
		final var cached = cached(id); final var goal = cached.goal;
		if (!PhantomSupplyIntentStore.bound(goal, root) || !matches(goal, player)) { return; }
		final var paused = PhantomOfflineSupply.pause(goal, System.currentTimeMillis());
		if (!paused.equals(goal)) { save(id, root, cached, paused); }
	}

	public void useOfflineSupply(org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService service)
	{
		if (_state != State.NEW || _offline != null) { throw new IllegalStateException("Offline supply must bind before start."); }
		_offline = new PhantomOfflineSupply(service);
	}

	public L2jPhantomSupplyJourney(PhantomSupplyIntentStore store, PhantomGoalStore roots, PhantomMaterializationService materialization,
			PhantomCommerceService commerce, PhantomCommerceReceiptStore receipts, PhantomNavigationService navigation,
			Supplier<PhantomGameKnowledgeQuery> knowledge, PhantomNormalGatekeeperTravel travel, LongPredicate allowed,
			BiPredicate<Long, Long> pauseFarm, BiPredicate<Long, PhantomGoal> learningOwns, PhantomAutonomousSupplyPolicy policy)
	{
		_store = store; _roots = roots; _materialization = materialization; _commerce = commerce; _receipts = receipts; _navigation = navigation;
		_knowledge = knowledge; _travel = travel; _allowed = allowed; _pauseFarm = pauseFarm; _learningOwns = learningOwns; _policy = policy;
	}

	public synchronized boolean start()
	{
		if (_state != State.NEW) { return false; }
		final var facts = _knowledge.get().snapshot();
		_merchants = facts.spawnFactsByNpc().entrySet().stream().filter(entry -> { final var npc = NpcData.getInstance().getTemplate(entry.getKey()); return npc != null && "Merchant".equals(npc.getType()); }).flatMap(entry -> entry.getValue().stream()).filter(spawn -> spawn.pointKind() == SpawnPointKind.EXACT && spawn.instanceId() == 0 && spawn.amount() > 0).toList();
		if (_merchants.isEmpty() || _merchants.size() > 4096) { throw new IllegalStateException("Supply requires bounded authoritative Merchant spawns."); }
		_warehouses = facts.spawnFactsByNpc().entrySet().stream().filter(entry -> { final var npc = NpcData.getInstance().getTemplate(entry.getKey()); return npc != null && "Warehouse".equals(npc.getType()); }).flatMap(entry -> entry.getValue().stream()).filter(spawn -> spawn.pointKind() == SpawnPointKind.EXACT && spawn.instanceId() == 0 && spawn.amount() > 0).toList();
		if (_warehouses.size() > 4096) { throw new IllegalStateException("Warehouse spawn index exceeds the supply owner bound."); }
		final var lists = new java.util.HashMap<Integer, Integer>();
		for (var offer : _commerce.catalog().buyOffers()) { for (int npc : offer.npcIds()) { lists.merge(npc, offer.listId(), Math::min); } }
		_sellLists = Map.copyOf(lists);
		_healingItems = _commerce.catalog().buyOffers().stream().map(PhantomCommerceCatalog.BuyOffer::itemId).distinct().map(id -> ItemData.getInstance().getTemplate(id)).filter(L2jPhantomSupplyJourney::healingPotion).toList();
		_cpItems = _commerce.catalog().buyOffers().stream().map(PhantomCommerceCatalog.BuyOffer::itemId).distinct().map(id -> ItemData.getInstance().getTemplate(id)).filter(L2jPhantomSupplyJourney::cpPotion).toList();
		_equipment = _commerce.catalog().buyOffers().stream().map(PhantomCommerceCatalog.BuyOffer::itemId).distinct().map(id -> ItemData.getInstance().getTemplate(id)).filter(item -> item instanceof org.l2jmobius.gameserver.model.item.Weapon || item instanceof org.l2jmobius.gameserver.model.item.Armor).toList();
		_knowledgeHash = facts.combinedHash(); _state = State.RUNNING; return true;
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate("candidate.autonomous.supplies", Set.of(PhantomBackgroundGoalSpec.GOAL_TYPE), Set.copyOf(java.util.EnumSet.allOf(PhantomActivityState.class).stream().filter(PhantomActivityState::requiresMaterialization).toList()), List.of(), List.of(new PhantomWeightedConsideration("score.autonomous.supplies", 1, context -> new PhantomConsideration.Evaluation(needsOrOwns(context.profileId(), context.goal()) ? 1000 : 0, "supplies.native.need"))), 1000,
			context -> new PhantomPlan(context.decisionSequence(), context.goal().goalId(), "candidate.autonomous.supplies", List.of(new PhantomPlanStep(0, ACTION, null, Map.of(), 60_000, 10, "supplies.native.advance")), 60_000, context.logicalNowNanos())));
	}
	public void registerHandlers(PhantomStepHandlerRegistry registry) { registry.register(ACTION, this::advance); }
	public boolean needsOrOwns(long profileId, PhantomGoal root) { return proposal(profileId, root).isPresent(); }
	public Optional<PhantomGoal> automaticIntent(long profileId) { return Optional.ofNullable(cached(profileId).goal); }

	public boolean ownsAutomaticIntent(long profileId, PhantomGoal root)
	{
		if (_state != State.RUNNING || !_policy.enabled()) { return false; }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			final var goal = cached(profileId).goal;
			if (!PhantomSupplyIntentStore.bound(goal, root) || !alive(goal, System.currentTimeMillis())) { return false; }
			return lease != null ? matches(goal, lease.player()) : _profiles != null && _profiles.find(profileId).map(profile -> profile.characterObjectId() != null && profile.characterObjectId() == Binding.parse(goal).actorObjectId()).orElse(false);
		}
	}

	private Optional<PhantomGoal> proposal(long profileId, PhantomGoal root)
	{
		if (_state != State.RUNNING || !_policy.enabled() || !_allowed.test(profileId) || root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType()) || !_knowledgeHash.equals(_knowledge.get().snapshot().combinedHash())) { return Optional.empty(); }
		final var entry = _materialization.find(profileId).orElse(null);
		final var nativePlayer = entry == null || !entry.actionAdmissionOpen() ? null : World.getInstance().getPlayer(entry.characterObjectId());
		// Quiescence is acquired before an action lease, as in the Commerce native boundary.
		if (nativePlayer != null) { PhantomStarterShotService.grantIfNeeded(_profiles, profileId, nativePlayer); }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null) { return Optional.empty(); }
			final Player player = lease.player();
			if (player.isAlikeDead() || player.isInParty() || player.isTeleporting() || player.hasPendingOwnedStore() || player.getInstanceId() != 0 || player.isChangingClass() || player.isTransformed() || player.isMounted() || player.isInStoreMode()) { return Optional.empty(); }
			final Cached cached = cached(profileId); final long now = System.currentTimeMillis();
			if (PhantomSupplyIntentStore.bound(cached.goal, root) && matches(cached.goal, player))
			{
				// An expired ACTIVE child still needs the existing executor's terminal settlement.
				// This requires the matching live action lease above; it never requests admission.
				if (cached.goal.status() == PhantomGoalStatus.ACTIVE || alive(cached.goal, now)) { return Optional.of(cached.goal); }
				if (Binding.parse(cached.goal).retryAfterEpochMillis() > now) { return Optional.empty(); }
			}
			if (_learningOwns.test(profileId, root)) { return Optional.empty(); }
			if (!_fieldReady.test(root, player)) { return Optional.empty(); }
			final var maintenance = maintenanceProposal(profileId, root, player, cached, now);
			if (maintenance.isPresent()) { return maintenance; }
			if (player.getActiveWeaponItem() == null || player.getActiveWeaponInstance() == null || player.getActiveWeaponItem() == player.getFistsWeaponItem()) { return equipmentProposal(profileId, root, player, cached, now); }
			final var need = supplyNeed(player); if (need == null) { return equipmentProposal(profileId, root, player, cached, now); }
			final int perHit = need.perHit(); var items = need.preferred(); long stock = need.stock();
			final String facts = root.goalId() + ":" + root.revision() + ':' + player.getObjectId() + ':' + player.getActiveClass() + ':' + player.getClassIndex() + ':' + player.getLevel() + ':' + player.getActiveWeaponInstance().getObjectId() + ':' + stock + ':' + player.getAdena();
			if (facts.equals(cached.facts) && cached.nextCheck > now) { return cached.offer == null ? equipmentProposal(profileId, root, player, cached, now) : Optional.of(cached.offer); }
			cached.facts = facts; cached.nextCheck = now + TimeUnit.SECONDS.toMillis(_policy.retrySeconds()); cached.offer = null;
			final long targetStock = need.targetStock();
			var choices = choices(player, items, stock, perHit, targetStock);
			if (choices.isEmpty() && !need.fallback().equals(items))
			{
				final long fallbackStock = need.fallbackStock();
				if (fallbackStock < (long) perHit * _policy.lowWaterHits())
				{
					final var fallback = choices(player, need.fallback(), fallbackStock, perHit, targetStock);
					if (!fallback.isEmpty()) { items = need.fallback(); stock = fallbackStock; choices = fallback; }
				}
			}
			final var sources = new ArrayList<PhantomDomainRef>();
			for (int index = 0; index < choices.size(); index++) { final var choice = choices.get(index); sources.add(new PhantomDomainRef("supply.npc", index + ":" + choice.npcId + ':' + choice.objectId + ':' + choice.listId + ':' + choice.itemId + ':' + choice.count)); }
			final var binding = new Binding(root.revision(), player.getObjectId(), player.getActiveClass(), player.getClassIndex(), player.getActiveWeaponInstance().getObjectId(), PhantomNativeTravelActions.point(player), 0);
			long revision = cached.goal == null ? 0 : Math.addExact(cached.goal.revision(), 1);
			final var receipt = _receipts.find(profileId).orElse(null);
			if (receipt != null && receipt.receipt().state() == PhantomCommerceReceipt.State.INCONSISTENT) { return Optional.empty(); }
			if (receipt != null && receipt.receipt().goalId() == root.goalId()) { revision = Math.max(revision, Math.addExact(receipt.receipt().goalRevision(), 1)); }
			if (receipt != null && !receipt.receipt().state().terminal()) { return Optional.empty(); }
			if (_offline != null)
			{
				final long wanted = targetStock - stock;
				final boolean productive = items.stream().allMatch(L2jPhantomSupplyJourney::productiveSupply);
				cached.offer = _offline.offer(player, root, binding, revision, items, wanted, now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), choices.isEmpty() ? Long.MAX_VALUE : choices.getFirst().price, owner ->
				{
					final var destination = PhantomNativeTravelActions.point(owner);
					final long out = fee(player, binding.field(), destination), back = fee(player, destination, binding.field());
					return out < 0 || back < 0 ? -1 : Math.addExact(out, back);
				}, fees -> _policy.availableBudget(player.getAdena(), fees, 0, productive)).orElse(null);
				if (cached.offer != null) { return Optional.of(cached.offer); }
			}
			if (choices.isEmpty())
			{
				final var equipment = equipmentProposal(profileId, root, player, cached, now);
				if (equipment.isPresent() && !"income.farm".equals(equipment.orElseThrow().acquisitionMethod())) { return equipment; }
				return shortageOffer(root, player, cached, new PhantomDomainRef("item", Integer.toString(items.getFirst().getId())), shortageReason(player, items), now);
			}
			final var first = choices.getFirst();
			cached.offer = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.start(new PhantomGoal(root.goalId(), PhantomCommerceDecision.MAINTAIN_SUPPLIES, PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef("item", Integer.toString(first.itemId)), first.count, 0, "commerce.buy", sources, root.selectedAnchor(), ACTION, 700, 0, player.getAdena() - purchaseReserve(player, ItemData.getInstance().getTemplate(first.itemId)), now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), Map.of("source.index", 0L), "supplies.native.low_stock", revision, binding.encode()), now);
			return Optional.of(cached.offer);
		}
	}

	private record SupplyNeed(List<ItemTemplate> preferred, List<ItemTemplate> fallback, long stock, long fallbackStock, int perHit, long targetStock) { }
	private SupplyNeed supplyNeed(Player player)
	{
		final var weapon = player.getActiveWeaponItem();
		if (weapon == null || player.getActiveWeaponInstance() == null || weapon == player.getFistsWeaponItem()) { return null; }
		final var ammunition = _commerce.catalog().supplies().stream().filter(supply -> supply.kinds().contains(PhantomCommerceCatalog.SupplyKind.AMMUNITION)).map(supply -> ItemData.getInstance().getTemplate(supply.itemId())).filter(item -> compatible(player, item)).toList();
		final boolean ammoNeeded = !ammunition.isEmpty() && ammunition.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum() < _policy.lowWaterHits();
		final var action = neededShotAction(player, weapon);
		final int shotCount = action == ActionType.SPIRITSHOT ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
		final var shots = _commerce.catalog().supplies().stream().filter(supply -> supply.kinds().contains(PhantomCommerceCatalog.SupplyKind.SHOT)).map(supply -> ItemData.getInstance().getTemplate(supply.itemId())).filter(item -> item.getDefaultAction() == action && compatible(player, item)).toList();
		final boolean shotNeeded = shotCount > 0 && shots.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum() < (long) shotCount * _policy.lowWaterHits();
		// Mandatory ammunition and productive amplification must not starve behind an optional potion stock target.
		final boolean healing = !ammoNeeded && !shotNeeded && player.getLevel() >= 18 && _healingItems.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum() < 3;
		final var cpItems = _cpItems.stream().filter(item -> compatible(player, item)).toList();
		final boolean cp = !ammoNeeded && !shotNeeded && !healing && !cpItems.isEmpty() && cpItems.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum() < 3;
		final int perHit = healing || ammoNeeded || cp ? 1 : shotCount;
		if (perHit <= 0 || perHit > 100) { return null; }
		final var all = healing ? _healingItems.stream().filter(item -> compatible(player, item)).toList() : ammoNeeded ? ammunition : cp ? cpItems : shots;
		if (all.isEmpty()) { return null; }
		final long allStock = all.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum();
		// Existing regular amplification is useful stock; optional blessed preference must not duplicate it.
		if (allStock >= (cp ? 3 : (long) perHit * _policy.lowWaterHits())) { return null; }
		final var blessed = !healing && !ammoNeeded && !cp && action == ActionType.SPIRITSHOT ? all.stream().filter(item -> item instanceof EtcItem etc && etc.isBlessed()).toList() : List.<ItemTemplate>of();
		final var preferred = blessed.isEmpty() ? all : blessed;
		return new SupplyNeed(preferred, all, preferred.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum(), allStock, perHit, healing ? 20 : cp ? 10 : Math.min(600, (long) perHit * _policy.targetHits()));
	}

	private Set<Integer> maintenanceNeeds(Player player)
	{
		final var needs = new HashSet<Integer>();
		player.getAllSkills().stream().mapToInt(org.l2jmobius.gameserver.model.skill.Skill::getItemConsumeId).filter(id -> id > 0).forEach(needs::add);
		final var need = supplyNeed(player); if (need != null) { need.fallback().forEach(item -> needs.add(item.getId())); }
		return Set.copyOf(needs);
	}

	private Optional<PhantomGoal> maintenanceProposal(long id, PhantomGoal root, Player player, Cached cached, long now)
	{
		if (now < cached.nextMaintenanceCheck) { return PhantomSupplyIntentStore.bound(cached.maintenanceOffer, root) && matches(cached.maintenanceOffer, player) && alive(cached.maintenanceOffer, now) ? Optional.of(cached.maintenanceOffer) : Optional.empty(); }
		cached.maintenanceOffer = null;
		cached.nextMaintenanceCheck = now + Math.max(30_000, TimeUnit.SECONDS.toMillis(_policy.retrySeconds()));
		if (!_maintenanceAllowed.test(id) || _equipmentWeaponProtected.test(id, root)) { return Optional.empty(); }
		final long slots = player.getInventory().getSize() * 100L / Math.max(1, player.getInventoryLimit()), weight = player.getCurrentLoad() * 100L / Math.max(1, player.getMaxLoad());
		cached.maintenancePressure = cached.maintenancePressure ? slots > 65 || weight > 65 : slots >= 80 || weight >= 80;
		final var origin = PhantomNativeTravelActions.point(player); final var needs = maintenanceNeeds(player);
		final var bank = nearestMaintenanceNpc(player, true); final var merchant = nearestMaintenanceNpc(player, false);
		final var operations = new ArrayList<PhantomInventoryMaintenanceIntent.Operation>();
		final var need = supplyNeed(player);
		final var ownedWeapons = java.util.stream.Stream.concat(player.getInventory().getItems().stream(), player.getWarehouse().getItems().stream())
			.filter(item -> item.getOwnerId() == player.getObjectId() && item.getTemplate() instanceof org.l2jmobius.gameserver.model.item.Weapon && org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(player, item.getTemplate()))
			.map(item -> (org.l2jmobius.gameserver.model.item.Weapon) item.getTemplate()).distinct().sorted(org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.preference(player)).limit(8).toList();
		final var bankNeed = bank == null ? null : player.getWarehouse().getItems().stream().filter(item -> item.getCount() > 0).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).limit(256)
			.filter(item -> need != null && need.fallback().stream().anyMatch(template -> template.getId() == item.getId()) || org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(player, item.getTemplate()) || ownedWeapons.stream().anyMatch(weapon -> neededWeaponResource(player, item.getTemplate(), weapon)))
			.filter(item -> player.getInventory().validateCapacity(item.isStackable() && player.getInventory().getItemByItemId(item.getId()) != null ? 0 : 1) && player.getInventory().validateWeight((long) item.getTemplate().getWeight())).findFirst().orElse(null);
		final var decisions = new LinkedHashMap<org.l2jmobius.gameserver.model.item.instance.Item, PhantomLootDispositionPolicy.Decision>();
		player.getInventory().getItems().stream().sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).limit(256).forEach(item -> decisions.put(item, PhantomLootDispositionPolicy.getInstance().evaluate(player, item, needs, false, cached.maintenancePressure)));
		final boolean housekeeping = cached.maintenancePressure || player.getAdena() < _policy.minimumAdenaReserve() || PhantomNativeTravelActions.safePreparationTown(player)
			|| decisions.values().stream().anyMatch(decision -> decision.disposition() == PhantomLootDispositionPolicy.Disposition.CRYSTALLIZE);
		long funds = player.getAdena();
		if (housekeeping)
		{
			if (merchant != null && !org.l2jmobius.gameserver.config.custom.MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE)
			{
				final var sale = decisions.entrySet().stream().filter(entry -> entry.getValue().disposition() == PhantomLootDispositionPolicy.Disposition.SELL_NPC).max(Comparator.comparingLong(entry -> Math.multiplyExact(entry.getKey().getReferencePrice() / 2L, entry.getValue().count()))).orElse(null);
				if (sale != null)
				{
					final var item = sale.getKey(); operations.add(new PhantomInventoryMaintenanceIntent.Operation(OperationKind.SELL, merchant.getId(), merchant.getObjectId(), _sellLists.get(merchant.getId()), item.getId(), item.getObjectId(), sale.getValue().count()));
					funds = Math.addExact(funds, Math.multiplyExact(item.getReferencePrice() / 2L, sale.getValue().count()));
				}
			}
			if (bank != null && funds >= 30)
			{
				final var deposit = decisions.entrySet().stream().filter(entry -> entry.getValue().disposition() == PhantomLootDispositionPolicy.Disposition.WAREHOUSE || entry.getValue().disposition() == PhantomLootDispositionPolicy.Disposition.MARKET_RESERVE)
					.filter(entry -> entry.getKey().isDepositable(true) && player.getWarehouse().validateCapacity(entry.getKey().isStackable() && player.getWarehouse().getItemByItemId(entry.getKey().getId()) != null ? 0 : 1))
					.max(Comparator.comparingLong(entry -> Math.multiplyExact((long) entry.getKey().getReferencePrice(), entry.getValue().count()))).orElse(null);
				if (deposit != null) { final var item = deposit.getKey(); operations.add(new PhantomInventoryMaintenanceIntent.Operation(OperationKind.WAREHOUSE_DEPOSIT, bank.getId(), bank.getObjectId(), 0, item.getId(), item.getObjectId(), deposit.getValue().count())); }
			}
			if (bankNeed == null && operations.size() < 3)
			{
				final var crystal = decisions.entrySet().stream().filter(entry -> entry.getValue().disposition() == PhantomLootDispositionPolicy.Disposition.CRYSTALLIZE).findFirst().orElse(null);
				if (crystal != null) { final var item = crystal.getKey(); operations.add(new PhantomInventoryMaintenanceIntent.Operation(OperationKind.CRYSTALLIZE, 0, 0, 0, item.getId(), item.getObjectId(), 1)); }
			}
		}
		if (bankNeed != null && operations.size() < 3)
		{
			final boolean futureResource = ownedWeapons.stream().anyMatch(weapon -> neededWeaponResource(player, bankNeed.getTemplate(), weapon));
			final long wanted = need != null && need.fallback().stream().anyMatch(item -> item.getId() == bankNeed.getId()) ? Math.max(1, need.targetStock() - need.fallbackStock()) : futureResource ? 600 : 1;
			operations.add(new PhantomInventoryMaintenanceIntent.Operation(OperationKind.WAREHOUSE_WITHDRAW, bank.getId(), bank.getObjectId(), 0, bankNeed.getId(), bankNeed.getObjectId(), Math.min(600, Math.min(wanted, bankNeed.getCount()))));
		}
		if (operations.isEmpty()) { return Optional.empty(); }
		final var receipt = _receipts.find(id).orElse(null);
		if (receipt != null && (!receipt.receipt().state().terminal() || receipt.receipt().state() == PhantomCommerceReceipt.State.INCONSISTENT)) { return Optional.empty(); }
		long revision = cached.goal == null ? 0 : Math.addExact(cached.goal.revision(), 1);
		if (receipt != null && receipt.receipt().goalId() == root.goalId()) { revision = Math.max(revision, Math.addExact(receipt.receipt().goalRevision(), 1)); }
		final var binding = new Binding(root.revision(), player.getObjectId(), player.getActiveClass(), player.getClassIndex(), player.getActiveWeaponInstance() == null ? 0 : player.getActiveWeaponInstance().getObjectId(), origin, 0);
		cached.maintenanceOffer = PhantomInventoryMaintenanceIntent.create(root, binding, operations, revision, player.getAdena(), now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), now, cached.maintenancePressure);
		return Optional.of(cached.maintenanceOffer);
	}

	private boolean neededWeaponResource(Player player, ItemTemplate item, org.l2jmobius.gameserver.model.item.Weapon weapon)
	{
		if (!compatible(player, item, weapon)) { return false; }
		final boolean ammunition = item instanceof EtcItem etc && (etc.getItemType() == EtcItemType.ARROW || etc.getItemType() == EtcItemType.BOLT);
		final var action = shotActions(player).getFirst();
		if (!ammunition && (player.getLevel() < 20 || item.getDefaultAction() != action)) { return false; }
		final int perHit = ammunition ? 1 : action == ActionType.SPIRITSHOT ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
		return perHit > 0 && player.getInventory().getItems().stream().filter(owned -> compatible(player, owned.getTemplate(), weapon) && (ammunition ? owned.getEtcItem() != null && (owned.getEtcItem().getItemType() == EtcItemType.ARROW || owned.getEtcItem().getItemType() == EtcItemType.BOLT) : owned.getTemplate().getDefaultAction() == action)).mapToLong(owned -> owned.getCount()).sum() < (long) perHit * _policy.lowWaterHits();
	}

	private Npc nearestMaintenanceNpc(Player player, boolean warehouse)
	{
		final var origin = PhantomNativeTravelActions.point(player);
		return (warehouse ? _warehouses : _merchants).stream().filter(spawn -> player.calculateDistance3D(spawn.x(), spawn.y(), spawn.z()) <= _navigation.policy().maximumRouteDistance()).sorted(Comparator.comparingDouble(spawn -> player.calculateDistance3D(spawn.x(), spawn.y(), spawn.z()))).limit(16)
			.map(spawn -> warehouse ? warehouseAt(spawn) : merchantAt(spawn)).filter(java.util.Objects::nonNull).filter(npc -> warehouse || _sellLists.containsKey(npc.getId()))
			.filter(npc -> { final long out = fee(player, origin, new PhantomNavigationPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId())); return out >= 0 && out <= player.getAdena(); }).findFirst().orElse(null);
	}
	private static Npc warehouseAt(SpawnFact spawn)
	{
		final var region = World.getInstance().getRegion(spawn.x(), spawn.y(), spawn.z());
		return region == null ? null : region.getVisibleObjects().stream().filter(object -> object instanceof Npc).map(object -> (Npc) object).filter(npc -> npc.isWarehouse() && npc.getId() == spawn.npcId() && npc.getInstanceId() == 0 && npc.isSpawned() && !npc.isAlikeDead() && Math.hypot((long) npc.getX() - spawn.x(), (long) npc.getY() - spawn.y()) <= 200).findFirst().orElse(null);
	}

	private Optional<PhantomGoal> equipmentProposal(long id, PhantomGoal root, Player player, Cached cached, long now)
	{
		if (_progression == null) { return Optional.empty(); }
		final boolean weaponProtected = _equipmentWeaponProtected.test(id, root);
		final String facts = root.goalId() + ":" + root.revision() + ':' + player.getActiveClass() + ':' + player.getLevel() + ':' + player.getExpertiseLevel() + ':' + (player.getActiveWeaponInstance() == null ? 0 : player.getActiveWeaponInstance().getObjectId()) + ':' + player.getAdena() + ':' + player.getInventory().getSize() + ':' + weaponProtected;
		if (facts.equals(cached.equipmentFacts) && now < cached.nextEquipmentCheck) { return Optional.ofNullable(cached.equipmentOffer); }
		cached.equipmentFacts = facts; cached.nextEquipmentCheck = now + TimeUnit.SECONDS.toMillis(_policy.retrySeconds()); cached.equipmentOffer = null;
		final var owned = player.getInventory().getItems().stream().filter(item -> !item.isEquipped() && item.getOwnerId() == player.getObjectId()).filter(item -> !weaponProtected || !(item.getTemplate() instanceof org.l2jmobius.gameserver.model.item.Weapon))
			.filter(item -> org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(player, item.getTemplate())).sorted(Comparator.comparing(item -> item.getTemplate(), org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.preference(player))).toList();
		final var candidates = new ArrayList<ItemTemplate>();
		owned.forEach(item -> { if (!candidates.contains(item.getTemplate())) { candidates.add(item.getTemplate()); } });
		final var paid = new ArrayList<ItemTemplate>();
		_equipment.stream().filter(item -> !weaponProtected || !(item instanceof org.l2jmobius.gameserver.model.item.Weapon)).filter(item -> org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(player, item))
			.filter(item -> _commerce.catalog().findBuyOffers(item.getId(), 0, 256).values().stream().anyMatch(offer -> !offer.limitedStock() && offer.price() > 0 && offer.price() <= player.getAdena() - _policy.minimumAdenaReserve()))
			.forEach(paid::add);
		if (_offline != null) { _offline.equipmentOffers(player).stream().filter(item -> !weaponProtected || !(item instanceof org.l2jmobius.gameserver.model.item.Weapon)).forEach(paid::add); }
		paid.stream().distinct().sorted(org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.preference(player)).limit(64).forEach(item -> { if (!candidates.contains(item)) { candidates.add(item); } });
		for (var item : candidates)
		{
			final var ownedItem = owned.stream().filter(value -> value.getId() == item.getId()).findFirst().orElse(null);
			final var gearChoices = ownedItem == null ? choices(player, List.of(item), 0, 1, 1) : List.<Choice>of();
			final var offline = ownedItem == null && gearChoices.isEmpty() ? offlineEquipmentOffer(root, player, cached, item, now) : null;
			if (ownedItem == null && gearChoices.isEmpty() && offline == null) { continue; }
			final long reserve = ownedItem != null ? 0 : offline != null ? offline.expenseBudget() : gearChoices.getFirst().price;
			if (item instanceof org.l2jmobius.gameserver.model.item.Weapon weapon && weapon.isRange())
			{
				final var charges = _commerce.catalog().supplies().stream().filter(value -> value.kinds().contains(PhantomCommerceCatalog.SupplyKind.AMMUNITION)).map(value -> ItemData.getInstance().getTemplate(value.itemId())).filter(value -> compatible(player, value, weapon)).toList();
				final long stock = charges.stream().mapToLong(value -> player.getInventory().getInventoryItemCount(value.getId(), -1)).sum();
				if (stock < _policy.lowWaterHits())
				{
					final var supplies = choices(player, charges, stock, 1, Math.min(600, _policy.targetHits()), reserve);
					if (supplies.isEmpty())
					{
						cached.equipmentOffer = offlineSupplyOffer(root, player, cached, charges, _policy.targetHits() - stock, weapon, reserve, now);
						if (cached.equipmentOffer != null) { return Optional.of(cached.equipmentOffer); }
						continue;
					}
					cached.equipmentOffer = equipmentOffer(root, player, cached, supplies, Map.of("equipment.weapon", (long) weapon.getId(), "equipment.reserve", reserve), 0, now);
					return Optional.ofNullable(cached.equipmentOffer);
				}
			}
			if (item instanceof org.l2jmobius.gameserver.model.item.Weapon weapon && player.getLevel() >= 20)
			{
				final var action = shotActions(player).getFirst(); final int perHit = action == ActionType.SPIRITSHOT ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
				if (perHit <= 0 || perHit > 100) { continue; }
				final var shots = _commerce.catalog().supplies().stream().filter(value -> value.kinds().contains(PhantomCommerceCatalog.SupplyKind.SHOT)).map(value -> ItemData.getInstance().getTemplate(value.itemId())).filter(value -> value.getDefaultAction() == action && compatible(player, value, weapon)).toList();
				final long stock = shots.stream().mapToLong(value -> player.getInventory().getInventoryItemCount(value.getId(), -1)).sum();
				if (stock < (long) perHit * _policy.lowWaterHits())
				{
					final var blessed = action == ActionType.SPIRITSHOT ? shots.stream().filter(value -> value instanceof EtcItem etc && etc.isBlessed()).toList() : List.<ItemTemplate>of();
					var supplies = choices(player, blessed.isEmpty() ? shots : blessed, stock, perHit, Math.min(600, (long) perHit * _policy.targetHits()), reserve);
					if (supplies.isEmpty() && !blessed.isEmpty()) { supplies = choices(player, shots, stock, perHit, Math.min(600, (long) perHit * _policy.targetHits()), reserve); }
					if (supplies.isEmpty())
					{
						final long wanted = Math.min(600, (long) perHit * _policy.targetHits()) - stock;
						cached.equipmentOffer = offlineSupplyOffer(root, player, cached, blessed.isEmpty() ? shots : blessed, wanted, weapon, reserve, now);
						if (cached.equipmentOffer == null && !blessed.isEmpty()) { cached.equipmentOffer = offlineSupplyOffer(root, player, cached, shots, wanted, weapon, reserve, now); }
						if (cached.equipmentOffer != null) { return Optional.of(cached.equipmentOffer); }
						continue;
					}
					cached.equipmentOffer = equipmentOffer(root, player, cached, supplies, Map.of("equipment.weapon", (long) weapon.getId(), "equipment.reserve", reserve), 0, now);
					return Optional.ofNullable(cached.equipmentOffer);
				}
			}
			cached.equipmentOffer = offline != null ? offline : equipmentOffer(root, player, cached, gearChoices, ownedItem == null ? Map.of() : Map.of("equipment.owned", (long) ownedItem.getObjectId()), item.getId(), now);
			return Optional.ofNullable(cached.equipmentOffer);
		}
		if (org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.hasGradeDebt(player))
		{
			return shortageOffer(root, player, cached, new PhantomDomainRef("equipment.grade", org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.targetGrade(player.getLevel(), player.getExpertiseLevel()).name().toLowerCase(java.util.Locale.ROOT)), player.getAdena() <= _policy.minimumAdenaReserve() ? "supplies.gear.no_adena.income_farm" : "supplies.gear.unavailable_offer.income_farm", now);
		}
		return Optional.empty();
	}

	private PhantomGoal offlineEquipmentOffer(PhantomGoal root, Player player, Cached cached, ItemTemplate item, long now)
	{
		return offlineSupplyOffer(root, player, cached, List.of(item), 1, null, 0, now);
	}

	private PhantomGoal offlineSupplyOffer(PhantomGoal root, Player player, Cached cached, List<ItemTemplate> items, long wanted, org.l2jmobius.gameserver.model.item.Weapon prospectiveWeapon, long equipmentReserve, long now)
	{
		if (_offline == null || items.isEmpty() || wanted <= 0) { return null; }
		final var receipt = _receipts.find(Long.parseLong(root.subject().key())).orElse(null);
		if (receipt != null && (!receipt.receipt().state().terminal() || receipt.receipt().state() == PhantomCommerceReceipt.State.INCONSISTENT)) { return null; }
		long revision = cached.goal == null ? 0 : Math.addExact(cached.goal.revision(), 1);
		if (receipt != null && receipt.receipt().goalId() == root.goalId()) { revision = Math.max(revision, Math.addExact(receipt.receipt().goalRevision(), 1)); }
		final var binding = new Binding(root.revision(), player.getObjectId(), player.getActiveClass(), player.getClassIndex(), player.getActiveWeaponInstance() == null ? 0 : player.getActiveWeaponInstance().getObjectId(), PhantomNativeTravelActions.point(player), 0, prospectiveWeapon == null ? 0 : prospectiveWeapon.getId(), equipmentReserve);
		final boolean productive = items.stream().allMatch(L2jPhantomSupplyJourney::productiveSupply);
		return _offline.offer(player, root, binding, revision, items, wanted, now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), Long.MAX_VALUE, owner ->
		{
			final var destination = PhantomNativeTravelActions.point(owner); final long out = fee(player, binding.field(), destination), back = fee(player, destination, binding.field());
			return out < 0 || back < 0 ? -1 : Math.addExact(out, back);
		}, fees -> _policy.availableBudget(player.getAdena(), fees, equipmentReserve, productive)).orElse(null);
	}

	private String shortageReason(Player player, List<ItemTemplate> items)
	{
		final var offers = items.stream().flatMap(item -> _commerce.catalog().findBuyOffers(item.getId(), 0, 256).values().stream()).filter(offer -> !offer.limitedStock() && offer.price() > 0).toList();
		if (offers.isEmpty()) { return "supplies.stock.unavailable_offer.income_farm"; }
		if (offers.stream().noneMatch(offer -> offer.price() <= _policy.availableBudget(player.getAdena(), 0, 0, true))) { return "supplies.stock.no_adena.income_farm"; }
		return "supplies.stock.too_far_or_unavailable.income_farm";
	}

	/** A failed child records the continuing economic objective while the existing farm root earns the next budget. */
	private Optional<PhantomGoal> shortageOffer(PhantomGoal root, Player player, Cached cached, PhantomDomainRef target, String reason, long now)
	{
		final var receipt = _receipts.find(Long.parseLong(root.subject().key())).orElse(null);
		if (receipt != null && (!receipt.receipt().state().terminal() || receipt.receipt().state() == PhantomCommerceReceipt.State.INCONSISTENT)) { return Optional.empty(); }
		long revision = cached.goal == null ? 0 : Math.addExact(cached.goal.revision(), 1);
		if (receipt != null && receipt.receipt().goalId() == root.goalId()) { revision = Math.max(revision, Math.addExact(receipt.receipt().goalRevision(), 1)); }
		final var binding = new Binding(root.revision(), player.getObjectId(), player.getActiveClass(), player.getClassIndex(), player.getActiveWeaponInstance() == null ? 0 : player.getActiveWeaponInstance().getObjectId(), PhantomNativeTravelActions.point(player), 0);
		cached.offer = new PhantomGoal(root.goalId(), PhantomCommerceDecision.MAINTAIN_SUPPLIES, PhantomGoalStatus.ACTIVE, root.subject(), target, 1, 0, "income.farm", List.of(), root.selectedAnchor(), ACTION, 700, 0, 0, now + TimeUnit.SECONDS.toMillis(_policy.retrySeconds()), Map.of(), reason, revision, binding.encode());
		return Optional.of(cached.offer);
	}

	private boolean weaponResources(Player player, org.l2jmobius.gameserver.model.item.Weapon weapon)
	{
		if (weapon == null) { return false; }
		if (weapon.isRange() && player.getInventory().getItems().stream().filter(item -> item.getEtcItem() != null && (item.getEtcItem().getItemType() == EtcItemType.ARROW || item.getEtcItem().getItemType() == EtcItemType.BOLT) && compatible(player, item.getTemplate(), weapon)).mapToLong(item -> item.getCount()).sum() < _policy.lowWaterHits()) { return false; }
		if (player.getLevel() < 20) { return true; }
		final var action = shotActions(player).getFirst(); final int perHit = action == ActionType.SPIRITSHOT ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
		return perHit > 0 && player.getInventory().getItems().stream().filter(item -> item.getTemplate().getDefaultAction() == action && compatible(player, item.getTemplate(), weapon)).mapToLong(item -> item.getCount()).sum() >= (long) perHit * _policy.lowWaterHits();
	}

	private PhantomGoal equipmentOffer(PhantomGoal root, Player player, Cached cached, List<Choice> choices, Map<String, Long> preparation, int ownedTemplate, long now)
	{
		final var receipt = _receipts.find(Long.parseLong(root.subject().key())).orElse(null);
		if (receipt != null && (receipt.receipt().state() == PhantomCommerceReceipt.State.INCONSISTENT || !receipt.receipt().state().terminal())) { return null; }
		long revision = cached.goal == null ? 0 : Math.addExact(cached.goal.revision(), 1);
		if (receipt != null && receipt.receipt().goalId() == root.goalId()) { revision = Math.max(revision, Math.addExact(receipt.receipt().goalRevision(), 1)); }
		final var sources = new ArrayList<PhantomDomainRef>();
		for (int index = 0; index < choices.size(); index++) { final var value = choices.get(index); sources.add(new PhantomDomainRef("supply.npc", index + ":" + value.npcId + ':' + value.objectId + ':' + value.listId + ':' + value.itemId + ':' + value.count)); }
		final boolean owned = preparation.containsKey("equipment.owned");
		if (owned) { sources.add(new PhantomDomainRef("supply.owned", Long.toString(preparation.get("equipment.owned")))); }
		final var constraints = new LinkedHashMap<>(preparation); constraints.put("source.index", 0L);
		final int target = owned ? ownedTemplate : choices.getFirst().itemId;
		final var binding = new Binding(root.revision(), player.getObjectId(), player.getActiveClass(), player.getClassIndex(), player.getActiveWeaponInstance() == null ? 0 : player.getActiveWeaponInstance().getObjectId(), PhantomNativeTravelActions.point(player), 0);
		return org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.start(new PhantomGoal(root.goalId(), PhantomCommerceDecision.MAINTAIN_SUPPLIES, PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef("item", Integer.toString(target)), owned ? 1 : choices.getFirst().count, 0, owned ? "equipment.owned" : "commerce.buy", sources, root.selectedAnchor(), ACTION, 700, 0, Math.max(0, player.getAdena() - purchaseReserve(player, ItemData.getInstance().getTemplate(target)) - preparation.getOrDefault("equipment.reserve", 0L)), now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), constraints, "supplies.equipment.prepare", revision, binding.encode()), now);
	}

	private long purchaseReserve(Player player, ItemTemplate item)
	{
		return player.getAdena() - _policy.availableBudget(player.getAdena(), 0, 0, productiveSupply(item));
	}
	private static boolean productiveSupply(ItemTemplate item)
	{
		return item != null && (healingPotion(item) || cpPotion(item) || item.getDefaultAction() == ActionType.SOULSHOT || item.getDefaultAction() == ActionType.SPIRITSHOT || item instanceof EtcItem etc && (etc.getItemType() == EtcItemType.ARROW || etc.getItemType() == EtcItemType.BOLT));
	}

	public static boolean compatible(Player player, ItemTemplate item)
	{
		if (item instanceof org.l2jmobius.gameserver.model.item.Weapon || item instanceof org.l2jmobius.gameserver.model.item.Armor) { return org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.compatible(player, item); }
		return compatible(player, item, player == null ? null : player.getActiveWeaponItem());
	}
	private static boolean compatible(Player player, ItemTemplate item, org.l2jmobius.gameserver.model.item.Weapon weapon)
	{
		if (player == null || weapon == null || !(item instanceof EtcItem etc) || !item.isStackable()) { return false; }
		if (healingPotion(item) || cpPotion(item)) { return ItemHandler.getInstance().getHandler(etc) != null && item.checkCondition(player, player, false); }
		if (etc.getItemType() == EtcItemType.ARROW || etc.getItemType() == EtcItemType.BOLT)
		{
			return item.getCrystalTypePlus() == weapon.getCrystalTypePlus() && (weapon.isBow() && etc.getItemType() == EtcItemType.ARROW || weapon.isCrossBow() && etc.getItemType() == EtcItemType.BOLT);
		}
		return item.getCrystalType() == weapon.getCrystalTypePlus() && (item.getDefaultAction() == ActionType.SOULSHOT || item.getDefaultAction() == ActionType.SPIRITSHOT) && ItemHandler.getInstance().getHandler(etc) != null;
	}

	private static List<ActionType> shotActions(Player player)
	{
		if (player.isMageClass()) { return List.of(ActionType.SPIRITSHOT); }
		final boolean magic = player.getAllSkills().stream().anyMatch(skill -> !skill.isPassive() && skill.isMagic() && (skill.isDamage() || skill.hasNegativeEffect()));
		return magic ? List.of(ActionType.SOULSHOT, ActionType.SPIRITSHOT) : List.of(ActionType.SOULSHOT);
	}

	private ActionType neededShotAction(Player player, org.l2jmobius.gameserver.model.item.Weapon weapon)
	{
		final var actions = shotActions(player);
		for (ActionType action : actions)
		{
			final int consumed = action == ActionType.SPIRITSHOT ? weapon.getSpiritShotCount() : weapon.getSoulShotCount();
			final var items = _commerce.catalog().supplies().stream().filter(supply -> supply.kinds().contains(PhantomCommerceCatalog.SupplyKind.SHOT)).map(supply -> ItemData.getInstance().getTemplate(supply.itemId())).filter(item -> item.getDefaultAction() == action && compatible(player, item, weapon)).toList();
			final var preferred = action == ActionType.SPIRITSHOT ? items.stream().filter(item -> item instanceof EtcItem etc && etc.isBlessed()).toList() : List.<ItemTemplate>of();
			if ((preferred.isEmpty() ? items : preferred).stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum() < (long) consumed * _policy.lowWaterHits()) { return action; }
		}
		return actions.getFirst();
	}

	private static boolean healingPotion(ItemTemplate item)
	{
		return item instanceof EtcItem etc && item.isStackable() && "ItemSkills".equals(etc.getHandlerName()) && item.getSkills() != null
			&& java.util.Arrays.stream(item.getSkills()).allMatch(holder -> holder.getSkillLevel() > 0 && holder.getSkillLevel() <= org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getMaxLevel(holder.getSkillId()))
			&& java.util.Arrays.stream(item.getSkills()).anyMatch(holder -> holder.getSkill() != null && holder.getSkill().isHealingPotionSkill());
	}

	private static boolean cpPotion(ItemTemplate item)
	{
		if (!(item instanceof EtcItem etc) || (item.getId() != 5591 && item.getId() != 5592) || !item.isStackable() || !"ItemSkills".equals(etc.getHandlerName())) { return false; }
		final var skills = item.getSkills(); final int expectedLevel = item.getId() == 5591 ? 1 : 2;
		return skills != null && skills.length == 1 && skills[0].getSkillId() == 2166 && skills[0].getSkillLevel() == expectedLevel && skills[0].getSkill() != null && skills[0].getSkill().hasEffectType(org.l2jmobius.gameserver.model.effects.EffectType.CPHEAL);
	}

	private List<Choice> choices(Player player, List<ItemTemplate> items, long stock, int perHit, long targetStock)
	{
		return choices(player, items, stock, perHit, targetStock, 0);
	}
	private List<Choice> choices(Player player, List<ItemTemplate> items, long stock, int perHit, long targetStock, long equipmentReserve)
	{
		final var choices = new ArrayList<Choice>();
		final long wanted = targetStock - stock;
		for (var item : items)
		{
			for (var offer : _commerce.catalog().findBuyOffers(item.getId(), 0, 256).values())
			{
				if (offer.limitedStock() || offer.price() <= 0) { continue; }
				for (var spawn : _merchants.stream().filter(value -> offer.npcIds().contains(value.npcId())).limit(32).toList())
				{
					final Merchant merchant = merchantAt(spawn);
					if (merchant == null || merchant.getMpc() == null || merchant.getMpc().getCastleTaxRate() != 0) { continue; }
					final var destination = new PhantomNavigationPoint(merchant.getX(), merchant.getY(), merchant.getZ(), merchant.getInstanceId());
					final long outbound = fee(player, PhantomNativeTravelActions.point(player), destination), returning = fee(player, destination, PhantomNativeTravelActions.point(player));
					if (outbound < 0 || returning < 0) { continue; }
					final long budget = _policy.availableBudget(player.getAdena(), Math.addExact(outbound, returning), equipmentReserve, productiveSupply(item));
					final long price = (long) (offer.price() * (1 + merchant.getMpc().getBaseTaxRate()));
					if (price <= 0 || budget < price) { continue; }
					final long count = Math.min(wanted, budget / price);
					final boolean optionalAmplifier = item.getDefaultAction() == ActionType.SOULSHOT || item.getDefaultAction() == ActionType.SPIRITSHOT;
					if (!_policy.usefulPurchase(optionalAmplifier, stock, count, perHit, player.calculateDistance3D(merchant), player.getMoveSpeed()) || !mobilePurchase(player, item, count)) { continue; }
					choices.add(new Choice(merchant.getId(), merchant.getObjectId(), offer.listId(), item.getId(), count, player.calculateDistance3D(merchant), price));
				}
			}
		}
		final var ordered = choices.stream().distinct().sorted(Comparator.comparingInt((Choice choice) -> healingPotency(ItemData.getInstance().getTemplate(choice.itemId))).reversed().thenComparingDouble(Choice::distance).thenComparingLong(Choice::price).thenComparingInt(Choice::npcId).thenComparingInt(Choice::listId)).toList();
		if (ordered.isEmpty()) { return List.of(); }
		final int itemId = ordered.getFirst().itemId;
		final var unique = new LinkedHashMap<Integer, Choice>();
		for (var choice : ordered) { if (choice.itemId == itemId) { unique.putIfAbsent(choice.objectId, choice); } }
		return unique.values().stream().limit(3).toList();
	}
	private static int healingPotency(ItemTemplate item)
	{
		return !healingPotion(item) ? 0 : java.util.Arrays.stream(item.getSkills()).map(holder -> holder.getSkill()).filter(skill -> skill != null && skill.isHealingPotionSkill()).mapToInt(skill -> skill.getAbnormalLevel()).max().orElse(0);
	}

	private static Merchant merchantAt(SpawnFact spawn)
	{
		final var region = World.getInstance().getRegion(spawn.x(), spawn.y(), spawn.z());
		if (region == null) { return null; }
		return region.getVisibleObjects().stream().filter(object -> object instanceof Merchant).map(object -> (Merchant) object).filter(npc -> npc.getId() == spawn.npcId() && npc.getInstanceId() == 0 && npc.isSpawned() && !npc.isAlikeDead() && Math.hypot((long) npc.getX() - spawn.x(), (long) npc.getY() - spawn.y()) <= 200).findFirst().orElse(null);
	}

	public static boolean mobilePurchase(Player player, ItemTemplate item, long count)
	{
		if (count <= 0 || count > 600 || item == null || player.getMoveSpeed() <= 0 || player.isMovementDisabled()) { return false; }
		final long weight = Math.multiplyExact((long) item.getWeight(), count);
		return player.getInventory().validateWeight(weight) && (player.getInventory().getItemByItemId(item.getId()) != null || player.getInventory().validateCapacity(1)) && (long) player.getCurrentLoad() + weight <= (long) (player.getMaxLoad() * .60);
	}

	private long fee(Player player, PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		final double speed = player.getMoveSpeed();
		if (from.instanceId() != 0 || to.instanceId() != 0 || !Double.isFinite(speed) || speed <= 0) { return -1; }
		// Admission estimates a whole finite round trip, not one Navigation segment.
		// This lower bound grants no route authority: every checkpoint still needs native Geo/PathFinding.
		final long oneWayMillis = Math.max(0, (TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()) - 30_000) / 2);
		final double maximumWalkingDistance = Math.min(_navigation.policy().maximumRouteDistance(), speed * oneWayMillis / 1000.0);
		if (from.distanceTo(to) <= maximumWalkingDistance) { return 0; }
		return _travel.legs().stream().filter(leg ->
		{
			final double walking = Math.hypot((long) from.x() - leg.sourceX(), (long) from.y() - leg.sourceY()) + Math.hypot((long) to.x() - leg.destinationX(), (long) to.y() - leg.destinationY());
			return walking <= maximumWalkingDistance && PhantomNativeTravelActions.available(leg);
		}).mapToLong(leg -> PhantomNormalGatekeeperTravel.fee(leg, player.getClassIndex(), player.getLevel(), System.currentTimeMillis() / 60_000)).min().orElse(-1);
	}

	private PhantomStepResult advance(PhantomStepContext context)
	{
		final long profileId = context.profileId();
		synchronized (this) { if (_state != State.RUNNING || _active.size() >= _policy.maximumProfiles() || !_active.add(profileId)) { return retry("supplies.busy"); } }
		try
		{
			if (context.cancellationToken().isCancelled() || !_allowed.test(profileId) || !_roots.load(profileId).map(value -> value.goal().equals(context.goal())).orElse(false)) { cleanup(profileId); return PhantomStepResult.of(Type.CANCELLED, "supplies.owner.changed"); }
			final Cached cached = cached(profileId);
			PhantomGoal intent = cached.goal;
			if (!PhantomSupplyIntentStore.bound(intent, context.goal()) || (intent.status() != PhantomGoalStatus.ACTIVE && !PhantomOfflineSupply.returning(intent)))
			{
				intent = proposal(profileId, context.goal()).orElse(null);
				if (intent == null) { return PhantomStepResult.of(Type.REPLAN, "supplies.no_need"); }
				save(profileId, context.goal(), cached, intent);
			}
			if (!alive(intent, System.currentTimeMillis()))
			{
				if (PhantomInventoryMaintenanceIntent.isMaintenance(intent) && _receipts.find(profileId).map(value -> !value.receipt().state().terminal() || value.receipt().state() == PhantomCommerceReceipt.State.INCONSISTENT).orElse(false)) { return retry("maintenance.deadline.receipt_requires_resolution"); }
				return terminal(profileId, context.goal(), cached, false, "supplies.deadline");
			}
			try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (lease != null && matches(intent, lease.player()))
				{
					if (lease.player().isAlikeDead()) { pause(profileId, context.goal(), lease.player()); accepted(profileId, cached.goal); return retry("supplies.native.recovery_pending"); }
					final var resumed = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.resume(intent, System.currentTimeMillis());
					if (!resumed.equals(intent)) { save(profileId, context.goal(), cached, resumed); intent = resumed; }
				}
			}
			accepted(profileId, intent);
			final PhantomGoal currentIntent = intent;
			if (!_store.intent(profileId).map(value -> value.goal().equals(currentIntent)).orElse(false)) { cleanup(profileId); synchronized (this) { _cache.remove(profileId); } return PhantomStepResult.of(Type.REPLAN, "supplies.intent.changed"); }
			if ("income.farm".equals(intent.acquisitionMethod())) { return terminal(profileId, context.goal(), cached, false, intent.reasonKey()); }
			if (!_pauseFarm.test(profileId, intent.deadlineEpochMillis())) { return retry("supplies.finish_current_target"); }
			final var binding = Binding.parse(intent);
			if (PhantomInventoryMaintenanceIntent.isMaintenance(intent)) { return advanceMaintenance(context, cached, intent, binding); }
			if (PhantomSupplyIntentStore.social(intent)) { return advanceOffline(context, cached, intent, binding); }
			try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (lease == null) { return retry("supplies.actor.pending"); }
				final Player player = lease.player();
				if (!matches(intent, player) || !_knowledgeHash.equals(_knowledge.get().snapshot().combinedHash())) { return terminal(profileId, context.goal(), cached, false, "supplies.actor_or_knowledge.changed"); }
				if (player.isSitting() && !player.isInCombat() && !player.isCastingNow() && !player.isInStoreMode()) { player.standUp(); return retry("supplies.native.stand_pending"); }
				if (!PhantomNativeTravelActions.ordinary(player) || player.hasPendingOwnedStore() || player.isChangingClass() || player.isTransformed() || player.isMounted()) { cleanup(profileId); return retry("supplies.native.busy"); }
				if (intent.constraints().containsKey("equipment.owned"))
				{
					final long objectId = intent.constraints().get("equipment.owned");
					final var keys = new HashSet<>(org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.KEYS); keys.addAll(Set.of("source.index", "equipment.owned")); if (intent.constraints().containsKey("equipment.object")) { keys.add("equipment.object"); }
					if (!intent.constraints().keySet().equals(keys) || intent.constraints().get("source.index") != 0 || intent.validSources().size() != 1 || !intent.validSources().getFirst().equals(new PhantomDomainRef("supply.owned", Long.toString(objectId))) || intent.requiredAmount() != 1 || !"equipment.owned".equals(intent.acquisitionMethod())) { return terminal(profileId, context.goal(), cached, false, "supplies.equipment.invalid_owned"); }
					return equip(profileId, context, cached, player, objectId, true);
				}
				final var source = source(intent);
				final var receipt = _receipts.find(profileId).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
				final boolean sameReceipt = receipt != null && receipt.goalId() == intent.goalId() && receipt.goalRevision() == intent.revision();
				if ((receipt != null && receipt.state() == PhantomCommerceReceipt.State.INCONSISTENT) || (sameReceipt && receipt.state() == PhantomCommerceReceipt.State.ABORTED)) { return terminal(profileId, context.goal(), cached, false, "supplies.commerce.fail_stop"); }
				if (sameReceipt && (receipt.request().kind() != OperationKind.BUY || receipt.request().npcTemplateId() != source.npcId || receipt.request().npcObjectId() != source.objectId || receipt.request().listId() != source.listId || receipt.request().itemId() != source.itemId || receipt.request().count() != source.count)) { return terminal(profileId, context.goal(), cached, false, "supplies.commerce.receipt_identity"); }
				if (!sameReceipt && receipt != null && !receipt.state().terminal()) { return retry("supplies.commerce.preexisting_pending"); }
				final boolean returning = sameReceipt && receipt.state() == PhantomCommerceReceipt.State.COMMITTED;
				if (returning && (ItemData.getInstance().getTemplate(source.itemId) instanceof org.l2jmobius.gameserver.model.item.Weapon || ItemData.getInstance().getTemplate(source.itemId) instanceof org.l2jmobius.gameserver.model.item.Armor))
				{
					final var item = player.getInventory().getItems().stream().filter(value -> value.getId() == source.itemId && value.getOwnerId() == player.getObjectId()).min(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).orElse(null);
					if (item == null) { return terminal(profileId, context.goal(), cached, false, "supplies.equipment.purchased_item_missing"); }
					final var equipped = equip(profileId, context, cached, player, item.getObjectId(), false);
					if (equipped != null) { return equipped; }
				}
				if (returning && returnDestination(binding.field()).map(point -> player.calculateDistance3D(point.x(), point.y(), point.z()) <= _navigation.arrivalRadius()).orElse(false))
				{
					if (player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && !PhantomNativeTravelActions.safePreparationTown(player)) { return terminal(profileId, context.goal(), cached, false, "supplies.field.peace"); }
					return terminal(profileId, context.goal(), cached, true, PhantomNativeTravelActions.safePreparationTown(player) ? "supplies.native.returned_to_base" : "supplies.native.returned_to_farm");
				}
				final Merchant merchant = returning ? null : merchant(source);
				if (!returning && merchant == null)
				{
					final var replacement = !sameReceipt && (receipt == null || receipt.state().terminal()) ? replacementCounter(player, source.npcId, source.listId, OperationKind.BUY, source.itemId) : null;
					if (replacement != null)
					{
						final var sources = new ArrayList<>(intent.validSources()); final int index = Math.toIntExact(intent.constraints().get("source.index"));
						sources.set(index, new PhantomDomainRef("supply.npc", index + ":" + source.npcId + ':' + replacement.getObjectId() + ':' + source.listId + ':' + source.itemId + ':' + source.count));
						final var rebound = new PhantomGoal(intent.goalId(), intent.goalType(), intent.status(), intent.subject(), intent.target(), intent.requiredAmount(), intent.currentAmount(), intent.acquisitionMethod(), sources, intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(), intent.deadlineEpochMillis(), intent.constraints(), "supplies.merchant.rebound_after_restart", Math.addExact(intent.revision(), 1), intent.payloadText());
						save(profileId, context.goal(), cached, rebound); cleanup(profileId); return retry("supplies.merchant.rebound_after_restart");
					}
					return alternative(profileId, context.goal(), cached, "supplies.merchant.unavailable", false);
				}
				if (!returning && merchant.canInteract(player))
				{
					cleanup(profileId);
					if (player.isMoving()) { return retry("supplies.counter.movement_pending"); }
					final var item = ItemData.getInstance().getTemplate(source.itemId);
					final long returnFee = fee(player, PhantomNativeTravelActions.point(player), binding.field());
					final long budget = Math.min(intent.expenseBudget(), returnFee < 0 ? 0 : _policy.availableBudget(player.getAdena(), returnFee, intent.constraints().getOrDefault("equipment.reserve", 0L), productiveSupply(item)));
					final var future = intent.constraints().containsKey("equipment.weapon") ? ItemData.getInstance().getTemplate(intent.constraints().get("equipment.weapon").intValue()) : null;
					final boolean suitable = intent.constraints().containsKey("equipment.weapon") ? future instanceof org.l2jmobius.gameserver.model.item.Weapon weapon && org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.compatible(player, weapon) && compatible(player, item, weapon) : compatible(player, item);
					if (!suitable || !mobilePurchase(player, item, source.count) || budget <= 0 || returnFee < 0) { return terminal(profileId, context.goal(), cached, false, "supplies.counter.mobility_or_reserve"); }
					if (context.cancellationToken().isCancelled() || !_allowed.test(profileId)) { return PhantomStepResult.of(Type.CANCELLED, "supplies.cancelled"); }
					if (player.getTarget() != merchant) { merchant.onAction(player, false); } merchant.onAction(player);
					final var request = new PhantomCommerceService.OperationIntent(OperationKind.BUY, source.npcId, source.objectId, source.listId, source.itemId, 0, source.count, 0, "", budget);
					final long operationDeadline = intent.deadlineEpochMillis();
					final var outcome = _commerce.execute(profileId, intent.goalId(), intent.revision(), request, () -> cancelled(context, binding, player, operationDeadline));
					return switch (outcome.status())
					{
						case SUCCESS, IDEMPOTENT -> retry("supplies.native.purchase_committed");
						case RETRY -> retry("supplies.commerce." + outcome.reason().name().toLowerCase(java.util.Locale.ROOT));
						case CANCELLED -> PhantomStepResult.of(Type.CANCELLED, "supplies.cancelled");
						case INCONSISTENT -> terminal(profileId, context.goal(), cached, false, "supplies.commerce.inconsistent");
						case REPLAN -> alternative(profileId, context.goal(), cached, "supplies.commerce." + outcome.reason().name().toLowerCase(java.util.Locale.ROOT), false);
					};
				}
				return travel(profileId, context, cached, intent, binding, player, merchant, returning);
			}
		}
		finally { synchronized (this) { _active.remove(profileId); } }
	}

	private PhantomStepResult advanceMaintenance(PhantomStepContext context, Cached cached, PhantomGoal intent, Binding binding)
	{
		final long id = context.profileId(); final var spec = PhantomInventoryMaintenanceIntent.parse(intent);
		Player actor; PhantomCommerceService.OperationIntent request;
		try (var lease = _materialization.tryAcquireAction(id).orElse(null))
		{
			if (lease == null) { return retry("maintenance.actor.pending"); }
			actor = lease.player();
			if (!matches(intent, actor) || !_knowledgeHash.equals(_knowledge.get().snapshot().combinedHash())) { return maintenanceReturn(id, context.goal(), cached, "maintenance.actor_or_catalog.changed"); }
			if (actor.isSitting() && !actor.isInCombat() && !actor.isCastingNow()) { actor.standUp(); return retry("maintenance.stand_pending"); }
			if (!PhantomNativeTravelActions.ordinary(actor) || actor.hasPendingOwnedStore() || actor.isChangingClass() || actor.isTransformed() || actor.isMounted()) { return retry("maintenance.native.busy"); }
			if (spec.returning())
			{
				if (!spec.failed() && !spec.buyChecked())
				{
					final var operations = new ArrayList<>(spec.operations()); final var need = supplyNeed(actor);
					if (need != null && operations.size() < PhantomInventoryMaintenanceIntent.MAXIMUM_OPERATIONS)
					{
						var offers = choices(actor, need.preferred(), need.stock(), need.perHit(), need.targetStock());
						if (offers.isEmpty() && !need.fallback().equals(need.preferred())) { offers = choices(actor, need.fallback(), need.fallbackStock(), need.perHit(), need.targetStock()); }
						if (!offers.isEmpty()) { final var offer = offers.getFirst(); operations.add(new PhantomInventoryMaintenanceIntent.Operation(OperationKind.BUY, offer.npcId(), offer.objectId(), offer.listId(), offer.itemId(), 0, offer.count())); }
					}
					save(id, context.goal(), cached, PhantomInventoryMaintenanceIntent.advance(intent, new PhantomInventoryMaintenanceIntent.Spec(operations, spec.index(), false, true), actor.getAdena(), "maintenance.supplies.rechecked_after_bank"));
					cleanup(id); return retry("maintenance.supplies.rechecked_after_bank");
				}
				if (returnDestination(binding.field()).map(point -> actor.calculateDistance3D(point.x(), point.y(), point.z()) <= _navigation.arrivalRadius()).orElse(false)) { return terminal(id, context.goal(), cached, !spec.failed(), spec.failed() ? intent.reasonKey() : "maintenance.native.returned_to_farm"); }
				return travel(id, context, cached, intent, binding, actor, null, true);
			}
			final var operation = spec.current(); final var receipt = _receipts.find(id).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
			final boolean same = receipt != null && receipt.goalId() == intent.goalId() && receipt.goalRevision() == intent.revision();
			if (same && !operation.matches(receipt.request())) { return retry("maintenance.receipt.identity_changed"); }
			if (receipt != null && receipt.state() == PhantomCommerceReceipt.State.INCONSISTENT) { return retry("maintenance.receipt.fail_stop"); }
			if (same && receipt.state() == PhantomCommerceReceipt.State.COMMITTED)
			{
				save(id, context.goal(), cached, PhantomInventoryMaintenanceIntent.advance(intent, new PhantomInventoryMaintenanceIntent.Spec(spec.operations(), spec.index() + 1, spec.failed(), spec.buyChecked()), actor.getAdena(), "maintenance.native.operation_confirmed"));
				cleanup(id); return retry("maintenance.native.operation_confirmed");
			}
			if (!same && receipt != null && !receipt.state().terminal()) { return retry("maintenance.preexisting_receipt.pending"); }
			final var counterObject = operation.npcObjectId() == 0 ? null : World.getInstance().findObject(operation.npcObjectId());
			final Npc counter = counterObject instanceof Npc npc && npc.getId() == operation.npcId() && npc.isSpawned() && !npc.isAlikeDead() && npc.getInstanceId() == 0 ? npc : null;
			if (operation.kind() != OperationKind.CRYSTALLIZE && counter == null)
			{
				final var replacement = !same && (receipt == null || receipt.state().terminal()) ? replacementCounter(actor, operation.npcId(), operation.listId(), operation.kind(), operation.itemId()) : null;
				if (replacement != null)
				{
					save(id, context.goal(), cached, PhantomInventoryMaintenanceIntent.rebindCounter(intent, replacement.getObjectId(), receipt));
					cleanup(id); return retry("maintenance.counter.rebound_after_restart");
				}
				return maintenanceReturn(id, context.goal(), cached, "maintenance.counter.unavailable");
			}
			if (counter != null && !counter.canInteract(actor)) { return travel(id, context, cached, intent, binding, actor, counter, false); }
			cleanup(id); if (actor.isMoving()) { return retry("maintenance.counter.movement_pending"); }
			if (!same)
			{
				if (!_maintenanceAllowed.test(id) || _equipmentWeaponProtected.test(id, context.goal())) { return maintenanceReturn(id, context.goal(), cached, "maintenance.owner.protected"); }
				if (operation.kind() == OperationKind.SELL || operation.kind() == OperationKind.WAREHOUSE_DEPOSIT || operation.kind() == OperationKind.CRYSTALLIZE)
				{
					final var item = actor.getInventory().getItemByObjectId(operation.itemObjectId());
					final var decision = PhantomLootDispositionPolicy.getInstance().evaluate(actor, item, maintenanceNeeds(actor), false, intent.constraints().get("maintenance.pressure") == 1);
					final boolean allowed = switch (operation.kind()) { case SELL -> decision.disposition() == PhantomLootDispositionPolicy.Disposition.SELL_NPC; case WAREHOUSE_DEPOSIT -> decision.disposition() == PhantomLootDispositionPolicy.Disposition.WAREHOUSE || decision.disposition() == PhantomLootDispositionPolicy.Disposition.MARKET_RESERVE; case CRYSTALLIZE -> decision.disposition() == PhantomLootDispositionPolicy.Disposition.CRYSTALLIZE; default -> false; };
					if (!allowed || operation.count() > decision.count()) { return maintenanceReturn(id, context.goal(), cached, "maintenance.item.became_protected"); }
				}
			}
			if (counter != null) { if (actor.getTarget() != counter) { counter.onAction(actor, false); } counter.onAction(actor); }
			final long returnFee = fee(actor, PhantomNativeTravelActions.point(actor), binding.field());
			if (returnFee < 0) { return maintenanceReturn(id, context.goal(), cached, "maintenance.return.unproven"); }
			request = operation.intent(Math.max(0, actor.getAdena() - returnFee));
		}
		// Release ACTION before acquiring the existing quiescent checkpoint; never drain one's own work.
		final long deadline = intent.deadlineEpochMillis(); final var outcome = _commerce.execute(id, intent.goalId(), intent.revision(), request, () -> cancelled(context, binding, actor, deadline));
		return switch (outcome.status())
		{
			case SUCCESS, IDEMPOTENT -> retry("maintenance.native.receipt_committed");
			case RETRY -> retry("maintenance.commerce." + outcome.reason().name().toLowerCase(java.util.Locale.ROOT));
			case INCONSISTENT -> retry("maintenance.commerce.inconsistent");
			case CANCELLED -> PhantomStepResult.of(Type.CANCELLED, "maintenance.cancelled");
			case REPLAN -> maintenanceReturn(id, context.goal(), cached, "maintenance.commerce." + outcome.reason().name().toLowerCase(java.util.Locale.ROOT));
		};
	}

	private PhantomStepResult maintenanceReturn(long id, PhantomGoal root, Cached cached, String reason)
	{
		final var receipt = _receipts.find(id).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
		if (receipt != null && !receipt.state().terminal()) { return retry("maintenance.receipt.pending_requires_resolution"); }
		cleanup(id); final var spec = PhantomInventoryMaintenanceIntent.parse(cached.goal);
		save(id, root, cached, PhantomInventoryMaintenanceIntent.advance(cached.goal, new PhantomInventoryMaintenanceIntent.Spec(spec.operations(), spec.operations().size(), true, true), cached.goal.expenseBudget(), reason));
		return retry(reason);
	}

	/** Runtime object IDs are not durable NPC identities. Requote only an uncommitted step at an authoritative spawn. */
	private Npc replacementCounter(Player player, int npcId, int listId, OperationKind kind, int itemId)
	{
		final boolean warehouse = kind == OperationKind.WAREHOUSE_DEPOSIT || kind == OperationKind.WAREHOUSE_WITHDRAW;
		if (!warehouse)
		{
			final var offer = _commerce.catalog().findBuyOffer(listId, itemId);
			if (kind == OperationKind.BUY ? offer == null || !offer.npcIds().contains(npcId) : _sellLists.getOrDefault(npcId, 0) != listId) { return null; }
		}
		return (warehouse ? _warehouses : _merchants).stream().filter(spawn -> spawn.npcId() == npcId && player.calculateDistance3D(spawn.x(), spawn.y(), spawn.z()) <= _navigation.policy().maximumRouteDistance())
			.sorted(Comparator.comparingDouble(spawn -> player.calculateDistance3D(spawn.x(), spawn.y(), spawn.z()))).limit(16)
			.map(spawn -> warehouse ? warehouseAt(spawn) : merchantAt(spawn)).filter(java.util.Objects::nonNull)
			.filter(npc -> fee(player, PhantomNativeTravelActions.point(player), new PhantomNavigationPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId())) >= 0).findFirst().orElse(null);
	}

	private PhantomStepResult travel(long profileId, PhantomStepContext context, Cached cached, PhantomGoal intent, Binding binding, Player player, WorldObject merchant, boolean returning)
	{
		Trip trip;
		synchronized (this) { trip = _trips.get(profileId); }
		if (trip != null && (trip.player != player || trip.owner != player.getNativeWorkOwner() || trip.revision != intent.revision() || trip.returning != returning)) { cleanup(profileId); trip = null; }
		if (trip == null)
		{
			if (player.isMoving()) { return retry("supplies.foreign_movement"); }
			final var destinations = returning ? returnDestination(binding.field()).map(List::of).orElse(List.of()) : approaches(player, merchant);
			if (destinations.isEmpty()) { return alternative(profileId, context.goal(), cached, "supplies.destination.no_approach", returning); }
			final long remaining = PhantomOfflineSupply.remaining(intent, System.currentTimeMillis());
			trip = new Trip(player, intent.revision(), returning, destinations, context.logicalNowNanos() + TimeUnit.MILLISECONDS.toNanos(remaining), intent.deadlineEpochMillis());
			synchronized (this) { if (_state != State.RUNNING || _trips.size() >= _policy.maximumProfiles()) { return retry("supplies.trip.capacity"); } _trips.put(profileId, trip); }
		}
		if (trip.plan != null && trip.stage >= trip.plan.stages().size() && !trip.plan.stages().getLast().to().equals(trip.destination()))
		{
			clearRoute(profileId, trip); trip.localAttempt = -1;
			trip.retryAt = context.logicalNowNanos() + TimeUnit.MILLISECONDS.toNanos(_navigation.policy().pathfindingCooldownMillis());
			return retry("supplies.native.checkpoint_reached");
		}
		if (!returning && trip.plan != null && trip.stage >= trip.plan.stages().size())
		{
			if (trip.destinationIndex + 1 >= trip.destinations.size()) { return alternative(profileId, context.goal(), cached, "supplies.counter.interaction_unavailable", false); }
			clearRoute(profileId, trip); trip.destinationIndex++; trip.localAttempt = -1; return retry("supplies.counter.next_approach");
		}
		if (context.logicalNowNanos() >= trip.deadline) { return alternative(profileId, context.goal(), cached, "supplies.trip.deadline", returning); }
		if (cancelled(context, binding, player, trip.intentDeadline)) { cleanup(profileId); return PhantomStepResult.of(Type.CANCELLED, "supplies.cancelled"); }
		final String failure = navigate(profileId, player, trip, context, binding);
		if (failure == null) { return retry(trip.reason); }
		if (!returning && trip.destinationIndex + 1 < trip.destinations.size())
		{
			clearRoute(profileId, trip); trip.destinationIndex++; trip.localAttempt = -1; return retry("supplies.merchant.next_approach");
		}
		return alternative(profileId, context.goal(), cached, failure, returning);
	}

	private PhantomStepResult advanceOffline(PhantomStepContext context, Cached cached, PhantomGoal intent, Binding binding)
	{
		final long profileId = context.profileId();
		if (_offline == null) { return terminal(profileId, context.goal(), cached, false, "supplies.offline.unavailable"); }
		final var spec = PhantomOfflineSupply.spec(intent);
		final boolean returning = PhantomOfflineSupply.returning(intent);
		final var owner = returning ? null : World.getInstance().getPlayer(spec.counterpartyCharacterObjectId());
		if (!returning && (!PhantomOfflineSupply.offline(owner) || owner.getInstanceId() != binding.field().instanceId() || World.getInstance().getPlayer(owner.getObjectId()) != owner)) { return terminal(profileId, context.goal(), cached, false, "supplies.offline.owner_unavailable"); }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null) { return retry("supplies.offline.actor_pending"); }
			final var player = lease.player();
			if (!binding.matches(player) || player.isAlikeDead() || !_knowledgeHash.equals(_knowledge.get().snapshot().combinedHash())) { return retry("supplies.offline.actor_changed"); }
			if (player.isSitting() && !player.isInCombat() && !player.isCastingNow() && !player.isInStoreMode()) { player.standUp(); return retry("supplies.native.stand_pending"); }
			if (!PhantomNativeTravelActions.ordinary(player) || player.hasPendingOwnedStore() || player.isChangingClass() || player.isTransformed() || player.isMounted()) { cleanup(profileId); return retry("supplies.native.busy"); }
			if (returning && returnDestination(binding.field()).map(point -> player.calculateDistance3D(point.x(), point.y(), point.z()) <= _navigation.arrivalRadius()).orElse(false))
			{
				if (player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && !PhantomNativeTravelActions.safePreparationTown(player)) { return terminal(profileId, context.goal(), cached, false, "supplies.field.peace"); }
				return terminal(profileId, context.goal(), cached, true, PhantomNativeTravelActions.safePreparationTown(player) ? "supplies.offline.returned_to_base" : "supplies.offline.returned_to_farm");
			}
			if (!returning && (!PhantomOfflineSupply.offline(owner) || owner.getInstanceId() != player.getInstanceId())) { return retry("supplies.offline.owner_unavailable"); }
			final int interactionDistance = spec instanceof org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.Manufacture ? 150 : Npc.INTERACTION_DISTANCE;
			if (returning || player.calculateDistance3D(owner) > interactionDistance) { return travel(profileId, context, cached, intent, binding, player, owner, returning); }
			cleanup(profileId);
			if (player.isMoving()) { return retry("supplies.offline.counter_movement_pending"); }
			final long returnFee = fee(player, PhantomNativeTravelActions.point(player), binding.field());
			final boolean productive = spec instanceof org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.StoreBuy buy
				? buy.lines().stream().allMatch(line -> productiveSupply(ItemData.getInstance().getTemplate(line.itemId())))
				: spec instanceof org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.Manufacture craft && productiveSupply(ItemData.getInstance().getTemplate(craft.productItemId()));
			if (binding.prospectiveWeaponTemplateId() > 0)
			{
				final var future = ItemData.getInstance().getTemplate(binding.prospectiveWeaponTemplateId());
				if (!(future instanceof org.l2jmobius.gameserver.model.item.Weapon weapon) || !org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.compatible(player, weapon)
					|| (spec instanceof org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.StoreBuy buy ? buy.lines().stream().anyMatch(line -> !compatible(player, ItemData.getInstance().getTemplate(line.itemId()), weapon)) : !(spec instanceof org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec.Manufacture craft) || !compatible(player, ItemData.getInstance().getTemplate(craft.productItemId()), weapon))) { return terminal(profileId, context.goal(), cached, false, "supplies.offline.future_weapon_changed"); }
			}
			if (returnFee < 0 || _policy.availableBudget(player.getAdena(), returnFee, binding.equipmentReserve(), productive) < intent.expenseBudget()) { return terminal(profileId, context.goal(), cached, false, "supplies.offline.reserve_changed"); }
		}
		// Social economy requires an exclusive action lease, so release this movement lease first.
		if (context.cancellationToken().isCancelled() || !_allowed.test(profileId)) { return retry("supplies.offline.owner_changed"); }
		final var result = _offline.advance(profileId, intent, context.activityGeneration(), context.tickSequence());
		final var refreshed = cached(profileId);
		if (PhantomOfflineSupply.returning(refreshed.goal)) { return retry("supplies.offline.native_committed"); }
		if (result.status() == org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService.StepStatus.REPLAN) { return terminal(profileId, context.goal(), refreshed, false, result.reason()); }
		return retry(result.reason());
	}

	private boolean matches(PhantomGoal goal, Player player)
	{
		final var binding = Binding.parse(goal);
		if (binding.matches(player)) { return true; }
		if (player == null || binding.actorObjectId() != player.getObjectId() || binding.classId() != player.getActiveClass() || binding.classIndex() != player.getClassIndex() || player.getInstanceId() != 0 || player.getActiveWeaponInstance() == null) { return false; }
		final long object = goal.constraints().getOrDefault("equipment.owned", goal.constraints().getOrDefault("equipment.object", 0L));
		if (object <= 0 || object != player.getActiveWeaponInstance().getObjectId() || goal.target() == null || !Integer.toString(player.getActiveWeaponInstance().getId()).equals(goal.target().key())) { return false; }
		if (goal.constraints().containsKey("equipment.owned")) { return goal.validSources().equals(List.of(new PhantomDomainRef("supply.owned", Long.toString(object)))) && "equipment.owned".equals(goal.acquisitionMethod()); }
		final var receipt = _receipts.find(Long.parseLong(goal.subject().key())).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
		return receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED && receipt.goalId() == goal.goalId() && receipt.goalRevision() == goal.revision() && receipt.request().kind() == OperationKind.BUY && receipt.request().count() == 1 && receipt.request().itemId() == player.getActiveWeaponInstance().getId();
	}

	private PhantomStepResult equip(long id, PhantomStepContext context, Cached cached, Player player, long object, boolean owned)
	{
		if (_progression == null || object <= 0 || object > Integer.MAX_VALUE) { return terminal(id, context.goal(), cached, false, "supplies.equipment.owner_unavailable"); }
		final var item = player.getInventory().getItemByObjectId((int) object);
		if (item == null || item.getOwnerId() != player.getObjectId() || !Integer.toString(item.getId()).equals(cached.goal.target().key()) || !org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.compatible(player, item.getTemplate())) { return terminal(id, context.goal(), cached, false, "supplies.equipment.item_or_expertise_changed"); }
		if (item.getTemplate() instanceof org.l2jmobius.gameserver.model.item.Weapon && _equipmentWeaponProtected.test(id, context.goal())) { return retry("supplies.equipment.quest_weapon_retained"); }
		if (item.getTemplate() instanceof org.l2jmobius.gameserver.model.item.Weapon weapon && !weaponResources(player, weapon)) { return terminal(id, context.goal(), cached, false, "supplies.equipment.charges_changed"); }
		if (!item.isEquipped())
		{
			if (!org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(player, item.getTemplate())) { return terminal(id, context.goal(), cached, false, "supplies.equipment.no_longer_upgrade"); }
			final var values = new LinkedHashMap<>(cached.goal.constraints()); values.put("equipment.object", object);
			save(id, context.goal(), cached, equipmentState(cached.goal, values, cached.goal.payloadText()));
			final var result = _progression.equipOwnedItem(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest(id, (int) object, context.cancellationToken()));
			if (result.status() != org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus.SUCCESS && result.status() != org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus.IDEMPOTENT) { return retry("supplies.equipment." + result.status().name().toLowerCase(java.util.Locale.ROOT)); }
			if (!item.isEquipped()) { return terminal(id, context.goal(), cached, false, "supplies.equipment.native_equip_unconfirmed"); }
		}
		final var old = Binding.parse(cached.goal);
		if (!old.matches(player))
		{
			final var updated = new Binding(old.rootRevision(), old.actorObjectId(), old.classId(), old.classIndex(), player.getActiveWeaponInstance().getObjectId(), old.field(), old.retryAfterEpochMillis());
			save(id, context.goal(), cached, equipmentState(cached.goal, cached.goal.constraints(), updated.encode()));
		}
		return owned ? terminal(id, context.goal(), cached, true, "supplies.equipment.owned_equipped") : null;
	}

	private static PhantomGoal equipmentState(PhantomGoal goal, Map<String, Long> constraints, String binding)
	{
		return new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), constraints, goal.reasonKey(), goal.revision(), binding);
	}

	private boolean cancelled(PhantomStepContext context, Binding binding, Player player, long deadline)
	{
		return _state != State.RUNNING || context.cancellationToken().isCancelled() || System.currentTimeMillis() >= deadline || !matches(cached(context.profileId()).goal, player) || player.isAlikeDead() || player.isInParty() || player.isChangingClass() || player.isTeleporting();
	}

	private PhantomStepResult alternative(long profileId, PhantomGoal root, Cached cached, String reason, boolean returning)
	{
		if (PhantomInventoryMaintenanceIntent.isMaintenance(cached.goal)) { return returning ? terminal(profileId, root, cached, false, reason) : maintenanceReturn(profileId, root, cached, reason); }
		cleanup(profileId);
		if (PhantomSupplyIntentStore.social(cached.goal)) { return terminal(profileId, root, cached, false, reason); }
		final var intent = cached.goal;
		final var receipt = _receipts.find(profileId).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
		if (receipt != null && !receipt.state().terminal()) { return terminal(profileId, root, cached, false, "supplies.commerce.pending_refuses_alternative"); }
		final long next = intent.constraints().getOrDefault("source.index", 0L) + 1;
		if (!returning && next < intent.validSources().size())
		{
			final long amount = Long.parseLong(intent.validSources().get((int) next).key().split(":", -1)[5]);
			final var constraints = new LinkedHashMap<>(intent.constraints()); constraints.put("source.index", next);
			final var replacement = new PhantomGoal(intent.goalId(), intent.goalType(), intent.status(), intent.subject(), intent.target(), amount, 0, intent.acquisitionMethod(), intent.validSources(), intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(), intent.deadlineEpochMillis(), constraints, reason, Math.addExact(intent.revision(), 1), intent.payloadText());
			save(profileId, root, cached, replacement);
			return retry("supplies.native.alternative_source");
		}
		return terminal(profileId, root, cached, false, reason);
	}

	private PhantomStepResult terminal(long profileId, PhantomGoal root, Cached cached, boolean complete, String reason)
	{
		cleanup(profileId);
		if (!complete && _offline != null && PhantomSupplyIntentStore.social(cached.goal) && cached.goal.status() == PhantomGoalStatus.ACTIVE)
		{
			final var cancellation = _offline.cancel(profileId, cached.goal);
			if (cancellation.status() != org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService.StepStatus.SUCCESS) { return retry("supplies.offline.cancel_pending"); }
		}
		final var goal = cached.goal; final var old = Binding.parse(goal);
		final var binding = new Binding(old.rootRevision(), old.actorObjectId(), old.classId(), old.classIndex(), old.weaponObjectId(), old.field(), System.currentTimeMillis() + TimeUnit.SECONDS.toMillis((long) _policy.retrySeconds() * (complete ? 1 : 4)));
		final var terminal = new PhantomGoal(goal.goalId(), goal.goalType(), complete ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.FAILED, goal.subject(), goal.target(), goal.requiredAmount(), complete ? goal.requiredAmount() : 0, goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), goal.constraints(), reason, Math.addExact(goal.revision(), 1), binding.encode());
		save(profileId, root, cached, terminal);
		releaseDemand(profileId);
		return PhantomStepResult.of(complete ? Type.SUCCESS : Type.REPLAN, reason);
	}

	private synchronized Cached cached(long profileId)
	{
		var cached = _cache.get(profileId);
		if (cached != null && PhantomSupplyIntentStore.social(cached.goal))
		{
			final var current = _store.intent(profileId).orElse(null);
			if (current != null && current.rowVersion() != cached.row) { cached.goal = current.goal(); cached.row = current.rowVersion(); cached.offer = null; }
		}
		if (cached == null)
		{
			final var stored = _store.intent(profileId).orElse(null);
			cached = new Cached(stored == null ? null : stored.goal(), stored == null ? -1 : stored.rowVersion());
			if (_cache.size() >= _policy.maximumProfiles()) { _cache.remove(_cache.keySet().iterator().next()); }
			_cache.put(profileId, cached);
		}
		return cached;
	}
	private void save(long profileId, PhantomGoal root, Cached cached, PhantomGoal goal)
	{
		try { final var stored = _store.saveIntent(profileId, root, cached.row, goal); cached.goal = stored.goal(); cached.row = stored.rowVersion(); cached.offer = null; }
		catch (RuntimeException exception) { synchronized (this) { _cache.remove(profileId); } throw exception; }
	}
	private static PhantomStepResult retry(String reason) { return PhantomStepResult.retry(500, reason); }

	/** Returns a terminal route reason; pending native work remains in this same Decision step. */
	private String navigate(long profileId, Player player, Trip trip, PhantomStepContext context, Binding binding)
	{
		final long now = context.logicalNowNanos();
		if (now < trip.retryAt) { trip.reason = "supplies.navigation.cooldown"; return null; }
		if (trip.plan == null)
		{
			if (trip.requestId == 0)
			{
				trip.origin = PhantomNativeTravelActions.point(player);
				trip.requestDeadline = now + _navigation.policy().defaultRequestDeadlineNanos();
				final var checkpoints = trip.localAttempt < 0 ? List.<PhantomNavigationPoint>of() : PhantomNativeTravelActions.localWalkDestinations(trip.origin, trip.destination(), _navigation.policy().maximumLocalStraightDistance());
				if (trip.localAttempt >= checkpoints.size() && trip.localAttempt >= 0) { return "supplies.navigation.no_local_approach"; }
				trip.requestDestination = trip.localAttempt < 0 ? trip.destination() : checkpoints.get(trip.localAttempt);
				final var nativeOptions = PhantomNativeTravelActions.snapshot(player, _travel.legs(), Math.max(0, (trip.deadline - now) / 1_000_000));
				final var options = new PhantomTravelStrategy.Options(nativeOptions.legs(), nativeOptions.speed(), nativeOptions.adena(), 0, nativeOptions.classIndex(), nativeOptions.level(), nativeOptions.epochMinute(), nativeOptions.gatekeeperAllowed(), "supplies.no_return_scroll", null, 0, nativeOptions.remainingMillis(), nativeOptions.blockedEdges());
				final var request = new PhantomNavigationRequest(profileId, trip.origin, trip.requestDestination, now, trip.requestDeadline, _navigation.policy().maximumRouteDistance());
				final var submission = trip.localAttempt < 0 ? _navigation.submitTravelPlan(request, options) : _navigation.submitWalkCorridor(request);
				trip.requestId = submission.requestId();
				if (submission.immediateResult() != null)
				{
					_navigation.consume(trip.requestId);
					return acceptRoute(profileId, trip, submission.immediateResult(), now);
				}
				if (submission.status() != PhantomNavigationService.SubmissionStatus.ACCEPTED || trip.requestId <= 0) { clearRoute(profileId, trip); trip.retryAt = now + TimeUnit.MILLISECONDS.toNanos(500); }
				trip.reason = "supplies.navigation.requested"; return null;
			}
			final var result = _navigation.consume(trip.requestId).orElse(null);
			if (result == null) { trip.reason = "supplies.navigation.pending"; return now >= trip.requestDeadline ? "supplies.navigation.deadline" : null; }
			final String failure = acceptRoute(profileId, trip, result, now);
			if (failure != null || trip.plan == null) { return failure; }
		}
		if (trip.stage >= trip.plan.stages().size()) { trip.reason = "supplies.navigation.arrived"; return null; }
		final var stage = trip.plan.stages().get(trip.stage);
		if (cancelled(context, binding, player, trip.intentDeadline)) { return "supplies.navigation.cancelled"; }
		if (stage.mode() == PhantomTravelStrategy.Mode.GK)
		{
			final var receipt = PhantomNativeTravelActions.gatekeeper(player, stage.gatekeeper());
			final String evidence = "supplies.native_gatekeeper_" + receipt.status().name().toLowerCase(java.util.Locale.ROOT) + ";fee=" + receipt.fee() + ";adena=" + receipt.adenaBefore() + "->" + receipt.adenaAfter() + ";from=" + receipt.before() + ";to=" + receipt.after();
			cached(profileId).gatekeeper = evidence; trip.reason = evidence;
			if (receipt.status() != PhantomNativeTravelActions.Status.ARRIVED) { return evidence; }
			trip.stage++; trip.waypoint = 0; trip.issued = null; return null;
		}
		if (stage.mode() != PhantomTravelStrategy.Mode.WALK) { return "supplies.navigation.unsupported_native_stage"; }
		final var current = PhantomNativeTravelActions.point(player);
		while (trip.waypoint + 1 < stage.points().size() && current.distanceTo(stage.points().get(trip.waypoint)) <= _navigation.arrivalRadius() && _native.canWalkDirect(current, stage.points().get(trip.waypoint + 1))) { trip.waypoint++; }
		final var waypoint = stage.points().get(trip.waypoint);
		final boolean lastWaypoint = trip.waypoint + 1 == stage.points().size();
		if (current.distanceTo(waypoint) <= (lastWaypoint ? _navigation.arrivalRadius() : Math.min(4, _navigation.arrivalRadius())))
		{
			if (lastWaypoint) { stopOwnedMove(trip); trip.stage++; trip.waypoint = 0; }
			else { trip.waypoint++; }
			trip.reason = "supplies.native.waypoint"; return null;
		}
		if (player.isMovementDisabled() || player.getMoveSpeed() <= 0) { return "supplies.native.movement_disabled"; }
		if (!player.isMoving())
		{
			if (!_native.canWalkDirect(current, waypoint))
			{
				cached(profileId).navigationRefusal = "from=" + current + ";to=" + waypoint + ";stage=" + trip.stage + ";waypoint=" + trip.waypoint + ";replan=" + trip.segmentReplans;
				if (++trip.segmentReplans > 3) { return "supplies.native.segment_changed"; }
				clearRoute(profileId, trip); trip.retryAt = now + _navigation.policy().pathfindingCooldownMillis() * 1_000_000L; trip.reason = "supplies.native.segment_replan"; return null;
			}
			if (cancelled(context, binding, player, trip.intentDeadline)) { return "supplies.navigation.cancelled"; }
			player.getAI().setIntention(Intention.MOVE_TO, new Location(waypoint.x(), waypoint.y(), waypoint.z()));
			if (!player.isMoving()) { return "supplies.native.move_rejected"; }
			trip.issued = new PhantomNavigationPoint(player.getXdestination(), player.getYdestination(), player.getZdestination(), 0);
			if (!_native.canWalkDirect(current, trip.issued)) { stopOwnedMove(trip); return "supplies.native.issued_segment_unproven"; }
		}
		trip.reason = "supplies.native.walking"; return null;
	}

	private String acceptRoute(long profileId, Trip trip, PhantomNavigationResult result, long now)
	{
		if (Set.of(PhantomNavigationResult.Status.COOLDOWN, PhantomNavigationResult.Status.PROFILE_BUSY, PhantomNavigationResult.Status.QUEUE_BACKPRESSURE).contains(result.status()))
		{
			clearRoute(profileId, trip); trip.retryAt = now + TimeUnit.MILLISECONDS.toNanos(500); return null;
		}
		if (result.route() == null)
		{
			if ((trip.localAttempt >= 0 || trip.origin.distanceTo(trip.destination()) > PhantomNativeTravelActions.LOCAL_WALK_DISTANCE)
				&& Set.of(PhantomNavigationResult.Status.NO_PATH, PhantomNavigationResult.Status.ROUTE_OBSTRUCTED, PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED).contains(result.status()))
			{
				final var checkpoints = PhantomNativeTravelActions.localWalkDestinations(PhantomNativeTravelActions.point(trip.player), trip.destination(), _navigation.policy().maximumLocalStraightDistance());
				if (trip.localAttempt + 1 < checkpoints.size())
				{
					clearRoute(profileId, trip); trip.localAttempt++;
					trip.retryAt = now + TimeUnit.MILLISECONDS.toNanos(_navigation.policy().pathfindingCooldownMillis());
					trip.reason = "supplies.navigation.local_checkpoint_retry"; return null;
				}
			}
			return "supplies.navigation." + result.status().name().toLowerCase(java.util.Locale.ROOT);
		}
		if (result.route().mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA) { return "supplies.navigation.unverified"; }
		PhantomTravelStrategy.Candidate plan;
		if (trip.localAttempt < 0) { plan = _navigation.consumeTravelPlan(trip.requestId).orElse(null); }
		else
		{
			if (!result.route().origin().equals(trip.origin) || !result.route().destination().equals(trip.requestDestination)) { return "supplies.navigation.plan_identity"; }
			if (trip.player.getMoveSpeed() <= 0) { return "supplies.native.movement_disabled"; }
			final long millis = (long) Math.ceil(1000 * result.route().totalDistance() / trip.player.getMoveSpeed());
			if (millis > Math.max(0, (trip.deadline - now) / 1_000_000)) { return "supplies.navigation.travel_budget"; }
			plan = new PhantomTravelStrategy.Candidate("walk.checkpoint", List.of(new PhantomTravelStrategy.Stage(PhantomTravelStrategy.Mode.WALK, trip.origin, trip.requestDestination, result.route().waypoints(), null, millis, 0, 0)));
		}
		if (plan == null || !plan.stages().getFirst().from().equals(trip.origin) || !plan.stages().getLast().to().equals(trip.requestDestination)) { return "supplies.navigation.plan_identity"; }
		trip.plan = plan; trip.stage = 0; trip.waypoint = 0; trip.reason = "supplies.navigation.ready"; return null;
	}

	private Optional<PhantomNavigationPoint> returnDestination(PhantomNavigationPoint field)
	{
		final var geo = GeoEngine.getInstance();
		if (!geo.hasGeo(field.x(), field.y())) { return Optional.empty(); }
		final int z = geo.getHeight(field.x(), field.y(), field.z());
		return Math.abs((long) z - field.z()) <= _navigation.arrivalRadius() ? Optional.of(new PhantomNavigationPoint(field.x(), field.y(), z, 0)) : Optional.empty();
	}

	private List<PhantomNavigationPoint> approaches(Player player, WorldObject merchant)
	{
		final var geo = GeoEngine.getInstance(); final var result = new ArrayList<PhantomNavigationPoint>();
		final int interactionDistance = merchant instanceof Player owner && owner.getPrivateStoreType() == org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType.MANUFACTURE ? 150 : Npc.INTERACTION_DISTANCE;
		final int interactionMargin = interactionDistance - _navigation.arrivalRadius() - 16;
		if (interactionMargin < 0) { return List.of(); }
		for (int radius : new int[] {0, Math.min(64, interactionMargin), Math.min(112, interactionMargin)})
		{
			for (int direction = 0; direction < (radius == 0 ? 1 : 8); direction++)
			{
				final double angle = direction * Math.PI / 4;
				final int x = merchant.getX() + (int) Math.round(radius * Math.cos(angle)), y = merchant.getY() + (int) Math.round(radius * Math.sin(angle));
				if (!geo.hasGeo(x, y)) { continue; }
				final int z = geo.getHeight(x, y, merchant.getZ());
				if (merchant.calculateDistance3D(x, y, z) <= interactionMargin) { final var point = new PhantomNavigationPoint(x, y, z, 0); if (!result.contains(point)) { result.add(point); } }
			}
		}
		result.sort(Comparator.comparingDouble((PhantomNavigationPoint point) -> player.calculateDistance3D(point.x(), point.y(), point.z())).thenComparingInt(PhantomNavigationPoint::x).thenComparingInt(PhantomNavigationPoint::y));
		return List.copyOf(result);
	}

	private static Source source(PhantomGoal goal)
	{
		final long index = goal.constraints().getOrDefault("source.index", -1L);
		final var expected = new HashSet<>(Set.of("source.index"));
		if (goal.constraints().keySet().containsAll(org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.KEYS)) { expected.addAll(org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.KEYS); }
		if (goal.constraints().containsKey("equipment.weapon"))
		{
			if (goal.constraints().get("equipment.weapon") <= 0 || goal.constraints().get("equipment.weapon") > Integer.MAX_VALUE || goal.constraints().getOrDefault("equipment.reserve", -1L) < 0) { throw new IllegalArgumentException("Invalid prospective equipment supply."); }
			expected.addAll(Set.of("equipment.weapon", "equipment.reserve"));
		}
		if (goal.constraints().containsKey("equipment.object")) { if (goal.constraints().get("equipment.object") <= 0 || goal.constraints().get("equipment.object") > Integer.MAX_VALUE) { throw new IllegalArgumentException("Invalid equipment witness."); } expected.add("equipment.object"); }
		if (!PhantomCommerceDecision.MAINTAIN_SUPPLIES.equals(goal.goalType()) || !ACTION.equals(goal.purposeKey()) || goal.target() == null || !"item".equals(goal.target().namespace()) || goal.validSources().isEmpty() || goal.validSources().size() > 3 || !goal.constraints().keySet().equals(expected) || !org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.valid(goal) || index < 0 || index >= goal.validSources().size()) { throw new IllegalArgumentException("Invalid bounded NPC supply intent."); }
		final var ref = goal.validSources().get((int) index); final var fields = ref.key().split(":", -1);
		if (!"supply.npc".equals(ref.namespace()) || fields.length != 6 || Integer.parseInt(fields[0]) != index) { throw new IllegalArgumentException("Invalid selected NPC supply source."); }
		final var source = new Source(Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Long.parseLong(fields[5]));
		if (source.npcId <= 0 || source.objectId <= 0 || source.listId <= 0 || source.itemId <= 0 || source.count <= 0 || source.count > 600 || source.itemId != Integer.parseInt(goal.target().key())) { throw new IllegalArgumentException("Invalid NPC supply source facts."); }
		return source;
	}
	private static Merchant merchant(Source source)
	{
		return World.getInstance().findObject(source.objectId) instanceof Merchant npc && npc.getId() == source.npcId && npc.getInstanceId() == 0 && npc.isSpawned() && !npc.isAlikeDead() ? npc : null;
	}

	private void clearRoute(long profileId, Trip trip)
	{
		if (trip.requestId > 0) { _navigation.cancel(profileId, trip.requestId); }
		stopOwnedMove(trip); trip.requestId = 0; trip.plan = null; trip.stage = 0; trip.waypoint = 0;
	}
	private void cleanup(long profileId)
	{
		final Trip trip; synchronized (this) { trip = _trips.remove(profileId); }
		if (trip != null) { clearRoute(profileId, trip); }
	}
	private static void stopOwnedMove(Trip trip)
	{
		final Player player = trip.player; final var issued = trip.issued;
		if (issued != null) { org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.cancelWalk(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.OwnedWalk(player, trip.owner, issued)); }
		trip.issued = null;
	}

	public String reason(long profileId) { synchronized (this) { final var trip = _trips.get(profileId); final var cached = cached(profileId); final String reason = trip == null ? Optional.ofNullable(cached.goal).map(PhantomGoal::reasonKey).orElse("") : trip.reason; return cached.navigationRefusal.isEmpty() ? reason : reason + ";native_refusal=" + cached.navigationRefusal; } }
	public String gatekeeperEvidence(long profileId) { return cached(profileId).gatekeeper; }
	public void beginStop()
	{
		final List<Map.Entry<Long, Trip>> trips;
		synchronized (this) { if (_state == State.STOPPED) { return; } _state = State.STOPPING; trips = new ArrayList<>(_trips.entrySet()); _trips.clear(); _cache.clear(); }
		for (var entry : trips) { clearRoute(entry.getKey(), entry.getValue()); }
		for (long id : acceptedProfiles()) { releaseDemand(id); }
	}
	public boolean finishStop() { beginStop(); synchronized (this) { if (!_active.isEmpty()) { return false; } _state = State.STOPPED; return true; } }
	@Override public void beforeMaterialize(long profileId, int characterObjectId) { cleanup(profileId); synchronized (this) { _cache.remove(profileId); } }
	@Override public void afterPlayerLoad(long profileId, Player player) { }
	@Override public void materializeSucceeded(long profileId, int characterObjectId)
	{
		final var player = World.getInstance().getPlayer(characterObjectId);
		if (player != null)
		{
			PhantomStarterShotService.afterLoad(_profiles, profileId, player);
			PhantomStarterShotService.grantIfNeeded(_profiles, profileId, player);
		}
	}
	@Override public void materializeAborted(long profileId, int characterObjectId) { beforeMaterialize(profileId, characterObjectId); }
	@Override public void beforeStore(long profileId, Player player)
	{
		final var root = _roots.load(profileId).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		if (root != null) { pause(profileId, root, player); }
		cleanup(profileId); releaseDemand(profileId); synchronized (this) { _cache.remove(profileId); }
	}
	@Override public void afterStore(long profileId, Player player) { }

	private static final class Cached
	{
		private volatile PhantomGoal goal, offer;
		private volatile long row;
		private String facts = "";
		private volatile String gatekeeper = "";
		private volatile String navigationRefusal = "";
		private String equipmentFacts = "";
		private long nextEquipmentCheck;
		private PhantomGoal equipmentOffer;
		private long nextCheck;
		private long nextMaintenanceCheck;
		private PhantomGoal maintenanceOffer;
		private boolean maintenancePressure;
		private Cached(PhantomGoal value, long version) { goal = value; row = version; }
	}
	private static final class Trip
	{
		private final Player player;
		private final PlayerNativeWork.Owner owner;
		private final long revision, deadline, intentDeadline;
		private final boolean returning;
		private final List<PhantomNavigationPoint> destinations;
		private int destinationIndex, stage, waypoint, segmentReplans, localAttempt = -1;
		private long requestId, requestDeadline, retryAt;
		private PhantomNavigationPoint origin, issued, requestDestination;
		private PhantomTravelStrategy.Candidate plan;
		private String reason = "supplies.navigation.new";
		private Trip(Player actor, long childRevision, boolean returnTrip, List<PhantomNavigationPoint> points, long legDeadline, long durableDeadline)
		{
			player = actor; owner = actor.getNativeWorkOwner(); revision = childRevision; returning = returnTrip; destinations = points; deadline = legDeadline; intentDeadline = durableDeadline;
		}
		private PhantomNavigationPoint destination() { return destinations.get(destinationIndex); }
	}
	private record Source(int npcId, int objectId, int listId, int itemId, long count) { }

	private record Choice(int npcId, int objectId, int listId, int itemId, long count, double distance, long price) { }
	private enum State { NEW, RUNNING, STOPPING, STOPPED }
}

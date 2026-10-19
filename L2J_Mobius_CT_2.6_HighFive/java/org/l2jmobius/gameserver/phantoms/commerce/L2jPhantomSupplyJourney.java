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
	private List<ItemTemplate> _healingItems = List.of();
	private String _knowledgeHash;
	private volatile State _state = State.NEW;
	private PhantomOfflineSupply _offline;
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
						if (!binding.matches(lease.player())) { releaseDemand(id); continue; }
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
		if (!PhantomSupplyIntentStore.bound(goal, root) || !Binding.parse(goal).matches(player)) { return; }
		final var paused = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.pause(goal, System.currentTimeMillis());
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
		_healingItems = _commerce.catalog().buyOffers().stream().map(PhantomCommerceCatalog.BuyOffer::itemId).distinct().map(id -> ItemData.getInstance().getTemplate(id)).filter(L2jPhantomSupplyJourney::healingPotion).toList();
		_knowledgeHash = facts.combinedHash(); _state = State.RUNNING; return true;
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate("candidate.autonomous.supplies", Set.of(PhantomBackgroundGoalSpec.GOAL_TYPE), Set.of(PhantomActivityState.ACTIVE), List.of(), List.of(new PhantomWeightedConsideration("score.autonomous.supplies", 1, context -> new PhantomConsideration.Evaluation(needsOrOwns(context.profileId(), context.goal()) ? 1000 : 0, "supplies.native.need"))), 1000,
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
			return lease != null ? Binding.parse(goal).matches(lease.player()) : _profiles != null && _profiles.find(profileId).map(profile -> profile.characterObjectId() != null && profile.characterObjectId() == Binding.parse(goal).actorObjectId()).orElse(false);
		}
	}

	private Optional<PhantomGoal> proposal(long profileId, PhantomGoal root)
	{
		if (_state != State.RUNNING || !_policy.enabled() || !_allowed.test(profileId) || root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType()) || !_knowledgeHash.equals(_knowledge.get().snapshot().combinedHash())) { return Optional.empty(); }
		try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null) { return Optional.empty(); }
			final Player player = lease.player();
			if (player.isAlikeDead() || player.isInParty() || player.isTeleporting() || player.hasPendingOwnedStore() || player.getInstanceId() != 0 || player.isChangingClass() || player.isTransformed() || player.isMounted() || player.isInStoreMode()) { return Optional.empty(); }
			final Cached cached = cached(profileId); final long now = System.currentTimeMillis();
			if (PhantomSupplyIntentStore.bound(cached.goal, root) && Binding.parse(cached.goal).matches(player))
			{
				if (alive(cached.goal, now)) { return Optional.of(cached.goal); }
				if (Binding.parse(cached.goal).retryAfterEpochMillis() > now) { return Optional.empty(); }
			}
			if (_learningOwns.test(profileId, root) || player.getActiveWeaponItem() == null || player.getActiveWeaponInstance() == null || player.getActiveWeaponItem() == player.getFistsWeaponItem()) { return Optional.empty(); }
			if (!_fieldReady.test(root, player)) { return Optional.empty(); }
			final var ammunition = _commerce.catalog().supplies().stream().filter(supply -> supply.kinds().contains(PhantomCommerceCatalog.SupplyKind.AMMUNITION)).map(supply -> ItemData.getInstance().getTemplate(supply.itemId())).filter(item -> compatible(player, item)).toList();
			final boolean ammunitionNeeded = !ammunition.isEmpty() && ammunition.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum() < _policy.lowWaterHits();
			final long healingStock = _healingItems.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum();
			final boolean healingNeeded = player.getLevel() >= 18 && healingStock < 3;
			final int perHit = healingNeeded || ammunitionNeeded ? 1 : player.isMageClass() ? player.getActiveWeaponItem().getSpiritShotCount() : player.getActiveWeaponItem().getSoulShotCount();
			if (perHit <= 0 || perHit > 100) { return Optional.empty(); }
			final var items = healingNeeded ? _healingItems.stream().filter(item -> compatible(player, item)).toList() : ammunitionNeeded ? ammunition : _commerce.catalog().supplies().stream().filter(supply -> supply.kinds().contains(PhantomCommerceCatalog.SupplyKind.SHOT)).map(supply -> ItemData.getInstance().getTemplate(supply.itemId())).filter(item -> compatible(player, item)).toList();
			final long stock = items.stream().mapToLong(item -> player.getInventory().getInventoryItemCount(item.getId(), -1)).sum();
			if (stock >= (long) perHit * _policy.lowWaterHits()) { return Optional.empty(); }
			final String facts = root.goalId() + ":" + root.revision() + ':' + player.getObjectId() + ':' + player.getActiveClass() + ':' + player.getClassIndex() + ':' + player.getLevel() + ':' + player.getActiveWeaponInstance().getObjectId() + ':' + stock + ':' + player.getAdena();
			if (facts.equals(cached.facts) && cached.nextCheck > now) { return Optional.ofNullable(cached.offer); }
			cached.facts = facts; cached.nextCheck = now + TimeUnit.SECONDS.toMillis(_policy.retrySeconds()); cached.offer = null;
			final long targetStock = healingNeeded ? 20 : Math.min(600, (long) perHit * _policy.targetHits());
			final var choices = choices(player, items, stock, perHit, targetStock);
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
				cached.offer = _offline.offer(player, root, binding, revision, items, wanted, _policy.minimumAdenaReserve(), now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), choices.isEmpty() ? Long.MAX_VALUE : choices.getFirst().price, owner ->
				{
					final var destination = PhantomNativeTravelActions.point(owner);
					final long out = fee(player, binding.field(), destination), back = fee(player, destination, binding.field());
					return out < 0 || back < 0 ? -1 : Math.addExact(out, back);
				}).orElse(null);
				if (cached.offer != null) { return Optional.of(cached.offer); }
			}
			if (choices.isEmpty()) { return Optional.empty(); }
			final var first = choices.getFirst();
			cached.offer = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.start(new PhantomGoal(root.goalId(), PhantomCommerceDecision.MAINTAIN_SUPPLIES, PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef("item", Integer.toString(first.itemId)), first.count, 0, "commerce.buy", sources, root.selectedAnchor(), ACTION, 700, 0, player.getAdena() - _policy.minimumAdenaReserve(), now + TimeUnit.MINUTES.toMillis(_policy.journeyMinutes()), Map.of("source.index", 0L), "supplies.native.low_stock", revision, binding.encode()), now);
			return Optional.of(cached.offer);
		}
	}

	public static boolean compatible(Player player, ItemTemplate item)
	{
		if (player == null || player.getActiveWeaponItem() == null || !(item instanceof EtcItem etc) || !item.isStackable()) { return false; }
		if (healingPotion(item)) { return ItemHandler.getInstance().getHandler(etc) != null && item.checkCondition(player, player, false); }
		final var weapon = player.getActiveWeaponItem();
		if (etc.getItemType() == EtcItemType.ARROW || etc.getItemType() == EtcItemType.BOLT)
		{
			return item.getCrystalTypePlus() == weapon.getCrystalTypePlus() && (weapon.isBow() && etc.getItemType() == EtcItemType.ARROW || weapon.isCrossBow() && etc.getItemType() == EtcItemType.BOLT);
		}
		return item.getCrystalType() == weapon.getCrystalTypePlus() && item.getDefaultAction() == (player.isMageClass() ? ActionType.SPIRITSHOT : ActionType.SOULSHOT) && ItemHandler.getInstance().getHandler(etc) != null;
	}

	private static boolean healingPotion(ItemTemplate item)
	{
		return item instanceof EtcItem etc && item.isStackable() && "ItemSkills".equals(etc.getHandlerName()) && item.getSkills() != null
			&& java.util.Arrays.stream(item.getSkills()).allMatch(holder -> holder.getSkillLevel() > 0 && holder.getSkillLevel() <= org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getMaxLevel(holder.getSkillId()))
			&& java.util.Arrays.stream(item.getSkills()).anyMatch(holder -> holder.getSkill() != null && holder.getSkill().isHealingPotionSkill());
	}

	private List<Choice> choices(Player player, List<ItemTemplate> items, long stock, int perHit, long targetStock)
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
					final long budget = player.getAdena() - _policy.minimumAdenaReserve() - outbound - returning;
					final long price = (long) (offer.price() * (1 + merchant.getMpc().getBaseTaxRate()));
					if (price <= 0 || budget < price) { continue; }
					final long count = Math.min(wanted, budget / price);
					if (count < perHit || !mobilePurchase(player, item, count)) { continue; }
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
		if (from.instanceId() != 0 || to.instanceId() != 0) { return -1; }
		if (from.distanceTo(to) <= _navigation.policy().maximumLocalStraightDistance()) { return 0; }
		return _travel.legs().stream().filter(leg -> Math.hypot((long) from.x() - leg.sourceX(), (long) from.y() - leg.sourceY()) <= _navigation.policy().maximumLocalStraightDistance() && Math.hypot((long) to.x() - leg.destinationX(), (long) to.y() - leg.destinationY()) <= _navigation.policy().maximumLocalStraightDistance() && PhantomNativeTravelActions.available(leg)).mapToLong(leg -> PhantomNormalGatekeeperTravel.fee(leg, player.getClassIndex(), player.getLevel(), System.currentTimeMillis() / 60_000)).min().orElse(-1);
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
			if (!alive(intent, System.currentTimeMillis())) { return terminal(profileId, context.goal(), cached, false, "supplies.deadline"); }
			try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (lease != null && Binding.parse(intent).matches(lease.player()))
				{
					if (lease.player().isAlikeDead()) { pause(profileId, context.goal(), lease.player()); accepted(profileId, cached.goal); return retry("supplies.native.recovery_pending"); }
					final var resumed = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.resume(intent, System.currentTimeMillis());
					if (!resumed.equals(intent)) { save(profileId, context.goal(), cached, resumed); intent = resumed; }
				}
			}
			accepted(profileId, intent);
			final PhantomGoal currentIntent = intent;
			if (!_store.intent(profileId).map(value -> value.goal().equals(currentIntent)).orElse(false)) { cleanup(profileId); synchronized (this) { _cache.remove(profileId); } return PhantomStepResult.of(Type.REPLAN, "supplies.intent.changed"); }
			if (!_pauseFarm.test(profileId, intent.deadlineEpochMillis())) { return retry("supplies.finish_current_target"); }
			final var binding = Binding.parse(intent);
			if (PhantomSupplyIntentStore.social(intent)) { return advanceOffline(context, cached, intent, binding); }
			try (var lease = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if (lease == null) { return retry("supplies.actor.pending"); }
				final Player player = lease.player();
				if (!binding.matches(player) || !_knowledgeHash.equals(_knowledge.get().snapshot().combinedHash())) { return terminal(profileId, context.goal(), cached, false, "supplies.actor_or_knowledge.changed"); }
				if (player.isSitting() && !player.isInCombat() && !player.isCastingNow() && !player.isInStoreMode()) { player.standUp(); return retry("supplies.native.stand_pending"); }
				if (!PhantomNativeTravelActions.ordinary(player) || player.hasPendingOwnedStore() || player.isChangingClass() || player.isTransformed() || player.isMounted()) { cleanup(profileId); return retry("supplies.native.busy"); }
				final var source = source(intent);
				final var receipt = _receipts.find(profileId).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
				final boolean sameReceipt = receipt != null && receipt.goalId() == intent.goalId() && receipt.goalRevision() == intent.revision();
				if ((receipt != null && receipt.state() == PhantomCommerceReceipt.State.INCONSISTENT) || (sameReceipt && receipt.state() == PhantomCommerceReceipt.State.ABORTED)) { return terminal(profileId, context.goal(), cached, false, "supplies.commerce.fail_stop"); }
				if (sameReceipt && (receipt.request().kind() != OperationKind.BUY || receipt.request().npcTemplateId() != source.npcId || receipt.request().npcObjectId() != source.objectId || receipt.request().listId() != source.listId || receipt.request().itemId() != source.itemId || receipt.request().count() != source.count)) { return terminal(profileId, context.goal(), cached, false, "supplies.commerce.receipt_identity"); }
				if (!sameReceipt && receipt != null && !receipt.state().terminal()) { return retry("supplies.commerce.preexisting_pending"); }
				final boolean returning = sameReceipt && receipt.state() == PhantomCommerceReceipt.State.COMMITTED;
				if (returning && returnDestination(binding.field()).map(point -> player.calculateDistance3D(point.x(), point.y(), point.z()) <= _navigation.arrivalRadius()).orElse(false))
				{
					if (player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && !PhantomNativeTravelActions.safePreparationTown(player)) { return terminal(profileId, context.goal(), cached, false, "supplies.field.peace"); }
					return terminal(profileId, context.goal(), cached, true, PhantomNativeTravelActions.safePreparationTown(player) ? "supplies.native.returned_to_base" : "supplies.native.returned_to_farm");
				}
				final Merchant merchant = returning ? null : merchant(source);
				if (!returning && merchant == null) { return alternative(profileId, context.goal(), cached, "supplies.merchant.unavailable", false); }
				if (!returning && merchant.canInteract(player))
				{
					cleanup(profileId);
					if (player.isMoving()) { return retry("supplies.counter.movement_pending"); }
					final var item = ItemData.getInstance().getTemplate(source.itemId);
					final long returnFee = fee(player, PhantomNativeTravelActions.point(player), binding.field());
					final long budget = Math.min(intent.expenseBudget(), returnFee < 0 ? 0 : player.getAdena() - _policy.minimumAdenaReserve() - returnFee);
					if (!compatible(player, item) || !mobilePurchase(player, item, source.count) || budget <= 0 || returnFee < 0) { return terminal(profileId, context.goal(), cached, false, "supplies.counter.mobility_or_reserve"); }
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
			trip = new Trip(player, intent.revision(), returning, destinations, context.logicalNowNanos() + _navigation.policy().maximumAttemptDurationNanos(), intent.deadlineEpochMillis());
			synchronized (this) { if (_state != State.RUNNING || _trips.size() >= _policy.maximumProfiles()) { return retry("supplies.trip.capacity"); } _trips.put(profileId, trip); }
		}
		if (!returning && trip.plan != null && trip.stage >= trip.plan.stages().size())
		{
			if (trip.destinationIndex + 1 >= trip.destinations.size()) { return alternative(profileId, context.goal(), cached, "supplies.counter.interaction_unavailable", false); }
			clearRoute(profileId, trip); trip.destinationIndex++; return retry("supplies.counter.next_approach");
		}
		if (context.logicalNowNanos() >= trip.deadline) { return alternative(profileId, context.goal(), cached, "supplies.trip.deadline", returning); }
		if (cancelled(context, binding, player, trip.intentDeadline)) { cleanup(profileId); return PhantomStepResult.of(Type.CANCELLED, "supplies.cancelled"); }
		final String failure = navigate(profileId, player, trip, context, binding);
		if (failure == null) { return retry(trip.reason); }
		if (!returning && trip.destinationIndex + 1 < trip.destinations.size())
		{
			clearRoute(profileId, trip); trip.destinationIndex++; return retry("supplies.merchant.next_approach");
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
			if (returnFee < 0 || player.getAdena() - returnFee - _policy.minimumAdenaReserve() < intent.expenseBudget()) { return retry("supplies.offline.reserve_changed"); }
		}
		// Social economy requires an exclusive action lease, so release this movement lease first.
		if (context.cancellationToken().isCancelled() || !_allowed.test(profileId)) { return retry("supplies.offline.owner_changed"); }
		final var result = _offline.advance(profileId, intent, context.activityGeneration(), context.tickSequence());
		final var refreshed = cached(profileId);
		if (PhantomOfflineSupply.returning(refreshed.goal)) { return retry("supplies.offline.native_committed"); }
		if (result.status() == org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService.StepStatus.REPLAN) { return terminal(profileId, context.goal(), refreshed, false, result.reason()); }
		return retry(result.reason());
	}

	private boolean cancelled(PhantomStepContext context, Binding binding, Player player, long deadline)
	{
		return _state != State.RUNNING || context.cancellationToken().isCancelled() || System.currentTimeMillis() >= deadline || !binding.matches(player) || player.isAlikeDead() || player.isInParty() || player.isChangingClass() || player.isTeleporting();
	}

	private PhantomStepResult alternative(long profileId, PhantomGoal root, Cached cached, String reason, boolean returning)
	{
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
				final var nativeOptions = PhantomNativeTravelActions.snapshot(player, _travel.legs(), Math.max(0, (trip.deadline - now) / 1_000_000));
				final var options = new PhantomTravelStrategy.Options(nativeOptions.legs(), nativeOptions.speed(), nativeOptions.adena(), 0, nativeOptions.classIndex(), nativeOptions.level(), nativeOptions.epochMinute(), nativeOptions.gatekeeperAllowed(), "supplies.no_return_scroll", null, 0, nativeOptions.remainingMillis(), nativeOptions.blockedEdges());
				final var submission = _navigation.submitTravelPlan(new PhantomNavigationRequest(profileId, trip.origin, trip.destination(), now, trip.requestDeadline, _navigation.policy().maximumRouteDistance()), options);
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
		if (result.route() == null || result.route().mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA) { return "supplies.navigation." + result.status().name().toLowerCase(java.util.Locale.ROOT); }
		final var plan = _navigation.consumeTravelPlan(trip.requestId).orElse(null);
		if (plan == null || !plan.stages().getFirst().from().equals(trip.origin) || !plan.stages().getLast().to().equals(trip.destination())) { return "supplies.navigation.plan_identity"; }
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
	@Override public void materializeSucceeded(long profileId, int characterObjectId) { }
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
		private long nextCheck;
		private Cached(PhantomGoal value, long version) { goal = value; row = version; }
	}
	private static final class Trip
	{
		private final Player player;
		private final PlayerNativeWork.Owner owner;
		private final long revision, deadline, intentDeadline;
		private final boolean returning;
		private final List<PhantomNavigationPoint> destinations;
		private int destinationIndex, stage, waypoint, segmentReplans;
		private long requestId, requestDeadline, retryAt;
		private PhantomNavigationPoint origin, issued;
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

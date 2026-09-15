package org.l2jmobius.gameserver.phantoms;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.holders.RequestTrade;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSink;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomAutonomousMarketProducer;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.services.PrivateStoreService;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** Composed shared-scheduler, accepted H5 quote, durable native store and transfer gate. */
public final class PhantomAutonomousMarketSuite implements PhantomTestSuite
{
	private static final int MATERIAL_ID = 1865;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomProfileRepository _profiles;
	private PhantomProfile _firstProfile;
	private PhantomProfile _secondProfile;
	private PhantomMaterializationService _materialization;
	private PhantomScheduler _scheduler;
	private PhantomStoreService _stores;
	private PhantomEconomyReservationService _reservations;
	private PhantomGoalStateStore _goals;
	private PhantomAcquisitionStore _acquisition;
	private PhantomAutonomousMarketProducer _producer;
	private AtomicReference<PhantomSchedulerControlPort> _control;
	private AtomicReference<PhantomPrivateMarketPricingAuthority> _pricing;
	private org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot _knowledge;
	private PhantomRateAwareLootFairValue.Rates _rates;
	private Player _first;
	private Player _second;
	private long _firstAdena;
	private long _secondAdena;

	@Override
	public String id()
	{
		return "post001-autonomous-market";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		MapRegionData.getInstance();
		SpawnData.getInstance();
		DoorData.getInstance();
		final var topologyBackend = new L2jTopologyValidationBackend();
		final var topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
		final var topology = new PhantomTopologyQuery(topologySnapshot, topologyBackend, new PhantomTopologyMetrics());
		final var knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults();
		final var knowledgeBackend = new L2jGameKnowledgeBackend();
		_knowledge = new PhantomGameKnowledgeBuilder(knowledgeBackend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), knowledgeBackend, knowledgePolicy), topology, knowledgePolicy).build();
		_rates = PhantomRateAwareLootFairValue.Rates.capture();
		_profiles = PhantomProfileRepository.open();
		_firstProfile = _profiles.create(_environment.primary().objectId());
		_secondProfile = _profiles.create(_environment.observer().objectId());
		_goals = new PhantomGoalStateStore(_profiles);
		_acquisition = new PhantomAcquisitionStore(_profiles, _goals);
		final var metrics = new PhantomMetrics();
		_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		PhantomAssertions.assertTrue(_materialization.start(), "Market materialization did not start.");
		_control = new AtomicReference<>(PhantomSchedulerControlPort.noop());
		_scheduler = new PhantomScheduler(2, 10, 2, PhantomSchedulerPolicy.productionDefaults(10), System::nanoTime, (pulse, period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), new PhantomMaterializationServiceActivityPort(_materialization), PhantomActivityWorkSink.noop());
		PhantomAssertions.assertTrue(_scheduler.installControlPort(() -> _control.get().onPulse()), "Shared market scheduler port was not installed.");
		PhantomAssertions.assertTrue(_scheduler.start(), "Market scheduler did not start.");
		for (long profileId : List.of(_firstProfile.profileId(), _secondProfile.profileId()))
		{
			_scheduler.register(profileId);
			_scheduler.submitSignal(profileId, new PhantomRelevanceSignal("market.test", 1, PhantomActivityState.ACTIVE, 86_400_000));
		}
		for (int pulse = 0; pulse < 4 && ((_scheduler.find(_firstProfile.profileId()).orElseThrow().effectiveState() != PhantomActivityState.ACTIVE) || (_scheduler.find(_secondProfile.profileId()).orElseThrow().effectiveState() != PhantomActivityState.ACTIVE)); pulse++)
		{
			_scheduler.pulse();
		}
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, _scheduler.find(_firstProfile.profileId()).orElseThrow().effectiveState(), "First actual scheduler activity is not ACTIVE.");
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, _scheduler.find(_secondProfile.profileId()).orElseThrow().effectiveState(), "Second actual scheduler activity is not ACTIVE.");
		_first = World.getInstance().getPlayer(_environment.primary().objectId());
		_second = World.getInstance().getPlayer(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_first != null) && (_second != null), "Real materialized Player owners are absent.");
		// The new-character creation point is inside a native NPC shop exclusion radius.
		// Use the accepted Giran city-center anchor, away from the factual merchant/gatekeeper.
		_first.teleToLocation(82480, 149087, -3350, false);
		_second.teleToLocation(82520, 149087, -3350, false);
		_firstAdena = _first.getAdena();
		_secondAdena = _second.getAdena();
		_pricing = new AtomicReference<>(new PhantomPrivateMarketPricingAuthority(_knowledge, new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of()), _rates, new PhantomPrivateMarketQuote.Policy(500, 500), World.getInstance().getVisibleObjects()));
		_stores = new PhantomStoreService(_profiles, _materialization, _pricing::get);
		final var economyPolicy = PhantomEconomyPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/economy/high-five-economy-v1.xml"));
		_reservations = new PhantomEconomyReservationService(economyPolicy);
		PhantomAssertions.assertTrue(_reservations.start(), "Market reservation owner did not start.");
		PhantomEconomyConflictPort.install(_reservations);
		final var autonomous = PhantomMarketConfig.readAutonomous(context.moduleRoot().resolve("dist/game/config/Custom/PhantomMarket.ini"));
		PhantomAssertions.assertTrue(autonomous != null, "Shipped autonomous market policy is disabled or invalid.");
		_producer = new PhantomAutonomousMarketProducer(_materialization, _scheduler, _stores, _goals, _acquisition, _reservations, () -> null, autonomous);
		_control.set(_producer);
		context.record("market.producer.owner", "shared-scheduler:materialized=2:policyMax=" + autonomous.maximumOpenStores());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_control != null)
		{
			_control.set(PhantomSchedulerControlPort.noop());
		}
		if (_stores != null)
		{
			_stores.close(_firstProfile.profileId());
			_stores.close(_secondProfile.profileId());
		}
		if (_first != null)
		{
			clearItem(_first, MATERIAL_ID);
			clearGear(_first);
			restoreAdena(_first, _firstAdena);
		}
		if (_second != null)
		{
			clearItem(_second, MATERIAL_ID);
			clearGear(_second);
			restoreAdena(_second, _secondAdena);
		}
		if (_scheduler != null)
		{
			_scheduler.beginStop();
			_scheduler.finishStop();
		}
		if (_materialization != null)
		{
			_materialization.shutdown();
		}
		if (_reservations != null)
		{
			PhantomEconomyConflictPort.uninstall(_reservations);
			_reservations.shutdown(System.currentTimeMillis());
		}
		if (_profiles != null)
		{
			deleteProfile(_firstProfile);
			deleteProfile(_secondProfile);
		}
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("shared-scheduler-sell-buy-native-conservation-and-lifecycle", this::nativeMarket);
		registry.add("autonomous-manufacture-fail-closed-and-policy-bounds", this::policyBounds);
		registry.add("z-reservation-goal-stale-restore-and-enchanted-object", this::safety);
	}

	private void nativeMarket(PhantomTestContext context) throws Exception
	{
		fund(_first, MATERIAL_ID, 8);
		fund(_second, MATERIAL_ID, 8);
		fund(_first, 57, 100_000);
		fund(_second, 57, 100_000);
		final long now = System.currentTimeMillis();
		_scheduler.pulse();
		final PhantomStorePlan firstPlan = _stores.currentPlan(_firstProfile.profileId()).orElse(null);
		final PhantomStorePlan secondPlan = _stores.currentPlan(_secondProfile.profileId()).orElse(null);
		PhantomAssertions.assertTrue((firstPlan == null) != (secondPlan == null), "Bounded shared scheduler opened zero or every ACTIVE Phantom shop.");
		final long ownerProfile = firstPlan != null ? _firstProfile.profileId() : _secondProfile.profileId();
		final long otherProfile = firstPlan == null ? _firstProfile.profileId() : _secondProfile.profileId();
		final Player owner = firstPlan != null ? _first : _second;
		final Player other = firstPlan == null ? _first : _second;
		final PhantomStorePlan sell = firstPlan != null ? firstPlan : secondPlan;
		PhantomAssertions.assertEquals(PhantomStorePlan.Type.SELL, sell.type(), "Real SELL surplus did not become a native store.");
		PhantomAssertions.assertTrue(_stores.blocksDecision(ownerProfile) && !_stores.blocksDecision(otherProfile), "Only the native shop owner should pause Decision actions.");
		PhantomAssertions.assertEquals(PrivateStoreType.SELL, owner.getPrivateStoreType(), "Native SELL type drifted.");
		PhantomAssertions.assertTrue(owner.isSitting(), "Native visible store owner did not sit.");
		final var line = sell.lines().get(0);
		final Item owned = owner.getInventory().getItemByObjectId(line.objectOrRecipeId());
		PhantomAssertions.assertTrue((owned != null) && (owned.getCount() >= line.count()) && (owned.getId() == MATERIAL_ID), "SELL line was not real owned inventory.");
		final var nativeLine = owner.getSellList().getItems().iterator().next();
		PhantomAssertions.assertTrue((nativeLine.getObjectId() == line.objectOrRecipeId()) && (nativeLine.getCount() == line.count()) && (nativeLine.getPrice() == line.price()), "Canonical quoted SELL object/count/price did not reach native TradeList.");
		PhantomAssertions.assertTrue(line.price() > PhantomPrivateMarketQuote.npcLiquidation(owned.getTemplate()), "SELL to NPC liquidation arbitrage was allowed.");
		PhantomAssertions.assertTrue(_producer.consider(otherProfile, now).isEmpty(), "A second shop exceeded bounded ACTIVE participation.");
		final long itemsBefore = count(owner, MATERIAL_ID) + count(other, MATERIAL_ID);
		final long ownerItemsBefore = count(owner, MATERIAL_ID);
		final long otherItemsBefore = count(other, MATERIAL_ID);
		final long adenaBefore = owner.getAdena() + other.getAdena();
		final long ownerAdenaBefore = owner.getAdena();
		final long otherAdenaBefore = other.getAdena();
		final String listingHash = PrivateStoreService.listingHash(owner.getSellList());
		final Set<RequestTrade> purchase = Set.of(new RequestTrade(line.objectOrRecipeId(), line.itemId(), 1, line.price()));
		PhantomAssertions.assertEquals(PrivateStoreService.Result.COMMITTED, PrivateStoreService.getInstance().buyExact(other, owner.getObjectId(), purchase, listingHash, PrivateStoreService.requestHash(purchase)), "Real native Player purchase did not commit.");
		PhantomAssertions.assertEquals(itemsBefore, count(owner, MATERIAL_ID) + count(other, MATERIAL_ID), "SELL purchase minted or lost items.");
		PhantomAssertions.assertEquals(ownerItemsBefore - 1, count(owner, MATERIAL_ID), "Native SELL removed a non-requested owner quantity.");
		PhantomAssertions.assertEquals(otherItemsBefore + 1, count(other, MATERIAL_ID), "Native SELL did not give buyer the exact requested quantity.");
		context.record("market.sell.transfer", "ownerBefore=" + ownerItemsBefore + ":ownerAfter=" + count(owner, MATERIAL_ID) + ":otherBefore=" + otherItemsBefore + ":otherAfter=" + count(other, MATERIAL_ID));
		PhantomAssertions.assertEquals(adenaBefore, owner.getAdena() + other.getAdena(), "SELL purchase minted or lost Adena.");
		PhantomAssertions.assertEquals(ownerAdenaBefore + line.price(), owner.getAdena(), "Native SELL did not pay exact quoted Adena to owner.");
		PhantomAssertions.assertEquals(otherAdenaBefore - line.price(), other.getAdena(), "Native SELL charged buyer wrong Adena.");
		PhantomAssertions.assertEquals(PrivateStoreService.Result.REJECTED, PrivateStoreService.getInstance().buyExact(other, owner.getObjectId(), purchase, listingHash, PrivateStoreService.requestHash(purchase)), "Stale native request transferred twice.");
		final var overdraw = Set.of(new RequestTrade(line.objectOrRecipeId(), line.itemId(), line.count() + 1, line.price()));
		PhantomAssertions.assertEquals(PrivateStoreService.Result.REJECTED, PrivateStoreService.getInstance().buyExact(other, owner.getObjectId(), overdraw, PrivateStoreService.listingHash(owner.getSellList()), PrivateStoreService.requestHash(overdraw)), "Native SELL overdraw exceeded owned stock.");
		// Player sit/stand is animated asynchronously by the native ThreadPool.
		Thread.sleep(2_600);
		_producer.pulse(now + 121_000);
		PhantomAssertions.assertEquals(PrivateStoreType.NONE, owner.getPrivateStoreType(), "Expired SELL listing did not close.");
		PhantomAssertions.assertTrue(_stores.currentPlan(ownerProfile).isEmpty(), "Expired SELL durable plan survived.");
		awaitNativeIdle(owner);
		context.record("market.sell.afterExpiry", "ownerCount=" + count(owner, MATERIAL_ID) + ":worldPresent=" + _materialization.find(ownerProfile).orElseThrow().worldPresent());
		PhantomAssertions.assertTrue((World.getInstance().getPlayer(owner.getObjectId()) == owner) && _materialization.find(ownerProfile).orElseThrow().worldPresent(), "Closing a Phantom store disconnected its active headless Player.");
		PhantomAssertions.assertEquals(ownerItemsBefore - 1, count(owner, MATERIAL_ID), "Closing SELL destroyed authoritative owner inventory.");
		if (_stores.currentPlan(otherProfile).isPresent())
		{
			PhantomAssertions.assertEquals(PhantomStoreService.Result.CLOSED, _stores.close(otherProfile), "Rotated peer store did not close for the isolated BUY transaction.");
			_producer.pulse(now + 122_000);
			Thread.sleep(2_600);
			other.standUp();
			awaitNativeIdle(other);
		}
		final long baseline = count(owner, MATERIAL_ID);
		final long goalId = 1001001001L;
		final var sourceId = org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.sha256("accepted-market-need:" + ownerProfile);
		final var source = new Source(sourceId, Method.DEATH_DROP, 100, MATERIAL_ID, "drop:1865", "town", "town", 0, 0, 0, 0, 0);
		final var goal = new PhantomGoal(goalId, PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", Integer.toString(MATERIAL_ID)), 4, 0, Method.DEATH_DROP.key(), List.of(new PhantomDomainRef(PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, Method.DEATH_DROP.key())), new PhantomDomainRef(PhantomAcquisitionGoalSpec.ANCHOR_NAMESPACE, "town"), PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, baseline, PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "market.accepted.need", 0);
		_goals.insert(ownerProfile, goal);
		final String hash = "0".repeat(64);
		final var state = new PhantomAcquisitionState(new Hashes(hash, hash, hash, hash, hash), goalId, 0, MATERIAL_ID, 4, baseline, baseline, 0, Status.READY, source, List.of(new Candidate(sourceId, Method.DEATH_DROP, 1, 0, 0, "")), 0, 0, Phase.NONE, 0, 0, 0, null, List.of(), 0);
		_acquisition.insert(ownerProfile, state);
		long buyNow = now + 1_800_000;
		if (Math.floorMod(ownerProfile + (buyNow / 900_000), 2) != 0)
		{
			buyNow += 900_000;
		}
		context.record("market.buy.route", "rotation=" + Math.floorMod(ownerProfile + (buyNow / 900_000), 2) + ":peerDurable=" + _stores.currentPlan(otherProfile).isPresent() + ":ownerSocial=" + owner.canMakeSocialAction());
		final var rawBid = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "Покупаю ресурсы", List.of(new PhantomStorePlan.Line(MATERIAL_ID, MATERIAL_ID, 1, 0)), buyNow + 120_000);
		final PhantomStorePlan expectedBid = _stores.quotePlan(ownerProfile, PhantomActivityState.ACTIVE, rawBid, buyNow).orElseThrow();
		final PhantomStorePlan buy = _producer.consider(ownerProfile, buyNow).orElseThrow(() -> new AssertionError("Accepted real acquisition need did not produce BUY BID."));
		PhantomAssertions.assertEquals(PhantomStorePlan.Type.BUY, buy.type(), "Need published a non-BUY store.");
		final var bidLine = buy.lines().get(0);
		PhantomAssertions.assertEquals(expectedBid.lines().get(0).price(), bidLine.price(), "Producer BUY BID did not use canonical quotePlan price.");
		PhantomAssertions.assertTrue((bidLine.price() >= PhantomPrivateMarketQuote.npcLiquidation(owned.getTemplate())) && (bidLine.count() * bidLine.price() <= owner.getAdena()), "BUY violated NPC payout or real Adena budget.");
		PhantomAssertions.assertEquals(bidLine.price(), owner.getBuyList().getItems().iterator().next().getPrice(), "Canonical BUY BID drifted in native TradeList.");
		final Item supplied = other.getInventory().getItemByItemId(MATERIAL_ID);
		final long buyItemsBefore = count(owner, MATERIAL_ID) + count(other, MATERIAL_ID);
		final long buyAdenaBefore = owner.getAdena() + other.getAdena();
		final long buyOwnerItemsBefore = count(owner, MATERIAL_ID);
		final long buyOtherItemsBefore = count(other, MATERIAL_ID);
		final long buyOwnerAdenaBefore = owner.getAdena();
		final long buyOtherAdenaBefore = other.getAdena();
		final String buyHash = PrivateStoreService.listingHash(owner.getBuyList());
		final RequestTrade[] sale =
		{
			new RequestTrade(supplied.getObjectId(), MATERIAL_ID, 1, bidLine.price())
		};
		PhantomAssertions.assertEquals(PrivateStoreService.Result.COMMITTED, PrivateStoreService.getInstance().sellExact(other, owner.getObjectId(), sale, buyHash, PrivateStoreService.requestHash(sale)), "Real native Player sale to Phantom BUY did not commit.");
		PhantomAssertions.assertEquals(buyItemsBefore, count(owner, MATERIAL_ID) + count(other, MATERIAL_ID), "BUY transaction minted or lost items.");
		PhantomAssertions.assertEquals(buyAdenaBefore, owner.getAdena() + other.getAdena(), "BUY transaction minted or lost Adena.");
		PhantomAssertions.assertEquals(buyOwnerItemsBefore + 1, count(owner, MATERIAL_ID), "Native BUY did not receive exact requested quantity.");
		PhantomAssertions.assertEquals(buyOtherItemsBefore - 1, count(other, MATERIAL_ID), "Native BUY removed a non-requested seller quantity.");
		PhantomAssertions.assertEquals(buyOwnerAdenaBefore - bidLine.price(), owner.getAdena(), "Native BUY spent wrong Adena from owner.");
		PhantomAssertions.assertEquals(buyOtherAdenaBefore + bidLine.price(), other.getAdena(), "Native BUY paid seller wrong Adena.");
		PhantomAssertions.assertEquals(PrivateStoreService.Result.REJECTED, PrivateStoreService.getInstance().sellExact(other, owner.getObjectId(), sale, buyHash, PrivateStoreService.requestHash(sale)), "BUY stale replay transferred twice.");
		PhantomAssertions.assertEquals(PhantomStoreService.Result.CLOSED, _stores.close(ownerProfile), "BUY store did not safely close.");
		_producer.pulse(buyNow + 1_000);
		context.record("market.native", "sellAsk=" + line.price() + ":buyBid=" + bidLine.price() + ":sellAndBuyConserved=true:replaysRejected=true");
	}

	private void policyBounds(PhantomTestContext context)
	{
		final var policy = PhantomMarketConfig.readAutonomous(context.moduleRoot().resolve("dist/game/config/Custom/PhantomMarket.ini"));
		PhantomAssertions.assertTrue((policy != null) && (policy.maximumOpenStores() == 2) && (policy.lifetimeSeconds() == 120) && (policy.reopenCooldownSeconds() == 600), "Shipped market frequency/TTL/participation differs from bounded policy.");
		PhantomAssertions.assertTrue(_stores.currentPlan(_firstProfile.profileId()).isEmpty() && _stores.currentPlan(_secondProfile.profileId()).isEmpty(), "Autonomous manufacture or old native plan was durably published.");
		context.record("market.manufacture", "autonomousDisabled=true:authorityFeeWithoutSource=0");
	}

	private void safety(PhantomTestContext context) throws Exception
	{
		final long profileId = _goals.load(_firstProfile.profileId()).isEmpty() ? _firstProfile.profileId() : _secondProfile.profileId();
		final Player owner = profileId == _firstProfile.profileId() ? _first : _second;
		long now = System.currentTimeMillis() + 900_000;
		if (Math.floorMod(profileId + (now / 900_000), 2) != 0)
		{
			now += 900_000;
		}
		final long goalId = 1001001002L;
		final var identity = new PhantomEconomyOperation.Identity(profileId, owner.getObjectId(), goalId, 0, 1, "market.test.reservation", 1, 1);
		final var operation = new PhantomEconomyOperation(identity, PhantomEconomyOperation.Kind.SELF_CRAFT, PhantomEconomyOperation.State.PREPARED, PhantomEconomyOperation.sha256("authority:" + goalId), PhantomEconomyOperation.sha256("intent:" + goalId), PhantomEconomyOperation.utf8Payload("before"), PhantomEconomyOperation.utf8Payload("intent"), now, now, now + 120_000, 0);
		final var resource = new PhantomEconomyOperation.Reservation(profileId, owner.getObjectId(), owner.getClassIndex(), PhantomEconomyOperation.ResourceKind.ITEM_COUNT, 0, MATERIAL_ID, 1, count(owner, MATERIAL_ID), 0, "INVENTORY");
		PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, _reservations.reserve(operation, List.of(resource)).status(), "Real material reservation did not enter accepted economy owner.");
		try
		{
			PhantomAssertions.assertTrue(_producer.consider(profileId, now).isEmpty(), "Reserved material produced an autonomous store.");
		}
		finally
		{
			_reservations.transition(operation.operationId(), PhantomEconomyOperation.State.RESERVED, PhantomEconomyOperation.State.ABORTED, now + 1, new PhantomEconomyOperation.Audit(PhantomEconomyOperation.Result.ERROR, "market.test.release", new byte[0]));
		}
		final var supply = new PhantomGoal(goalId, "maintain.supplies", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", Integer.toString(MATERIAL_ID)), 1, 0, null, List.of(), null, "supply", 500, 0, 0, 0, Map.of(), "market.safety", 0);
		final var storedSupply = _goals.insert(profileId, supply);
		try
		{
			PhantomAssertions.assertTrue(_producer.consider(profileId, now).isEmpty(), "Active non-acquisition resource Goal released its inventory to SELL.");
		}
		finally
		{
			_goals.delete(profileId, storedSupply.rowVersion());
		}
		final Player partner = profileId == _firstProfile.profileId() ? _second : _first;
		partner.onTransactionRequest(owner);
		try
		{
			PhantomAssertions.assertTrue(_producer.consider(profileId, now).isEmpty(), "Active native trade requester published a competing Phantom shop.");
		}
		finally
		{
			owner.setActiveRequester(null);
			partner.onTransactionResponse();
		}
		owner.setInsideZone(ZoneId.NO_STORE, true);
		try
		{
			PhantomAssertions.assertTrue(_producer.consider(profileId, now).isEmpty(), "Native NO_STORE corridor published a Phantom shop.");
		}
		finally
		{
			owner.setInsideZone(ZoneId.NO_STORE, false);
		}
		final Item material = owner.getInventory().getItemByItemId(MATERIAL_ID);
		final var requested = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Лавка фантома", List.of(new PhantomStorePlan.Line(material.getObjectId(), MATERIAL_ID, 1, 0)), now + 120_000);
		final PhantomStorePlan canonical = _stores.quotePlan(profileId, PhantomActivityState.ACTIVE, requested, now).orElseThrow();
		_profiles.insertComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, PhantomStorePlan.SCHEMA_VERSION, canonical.withState(PhantomStorePlan.State.OPEN).encode());
		final var originalPricing = _pricing.get();
		try
		{
			_pricing.set(new PhantomPrivateMarketPricingAuthority(_knowledge, new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of()), _rates, new PhantomPrivateMarketQuote.Policy(500, 2000), World.getInstance().getVisibleObjects()));
			PhantomAssertions.assertTrue(_pricing.get().pricePlan(owner, canonical).lines().get(0).price() != canonical.lines().get(0).price(), "Authority spread fixture did not change canonical ASK.");
			PhantomAssertions.assertTrue(_producer.consider(profileId, now).isEmpty(), "Stale durable quote silently reopened after authority spread changed.");
			PhantomAssertions.assertTrue(_stores.currentPlan(profileId).isEmpty() && (owner.getPrivateStoreType() == PrivateStoreType.NONE), "Stale durable store survived canonical re-quote rejection.");
		}
		finally
		{
			_pricing.set(originalPricing);
			_stores.close(profileId);
		}
		_profiles.insertComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, PhantomStorePlan.SCHEMA_VERSION, canonical.withState(PhantomStorePlan.State.OPEN).encode());
		final PhantomStorePlan restored = _producer.consider(profileId, now).orElseThrow(() -> new AssertionError("Exact retained canonical ASK did not safely restore native SELL."));
		PhantomAssertions.assertEquals(canonical.lines().get(0).price(), restored.lines().get(0).price(), "Exact retained ASK changed during native restore.");
		PhantomAssertions.assertEquals(restored.lines().get(0).price(), owner.getSellList().getItems().iterator().next().getPrice(), "Native restore changed canonical ASK.");
		Thread.sleep(2_600);
		PhantomAssertions.assertEquals(PhantomStoreService.Result.CLOSED, _stores.close(profileId), "Exact restored SELL did not safely close.");
		_producer.pulse(now + 1_000);
		awaitNativeIdle(owner);
		PhantomAssertions.assertTrue(_producer.consider(profileId, now + 2_000).isEmpty(), "Closed native SELL ignored deterministic reopen cooldown.");
		clearItem(owner, MATERIAL_ID);
		final Item equipped = owner.getInventory().addItem(ItemProcessType.REWARD, 70, 1, owner, null);
		final Item duplicate = owner.getInventory().addItem(ItemProcessType.REWARD, 70, 1, owner, null);
		PhantomAssertions.assertTrue((equipped != null) && (duplicate != null), "Accepted source-backed weapon fixture was absent.");
		owner.getInventory().equipItem(equipped);
		duplicate.setEnchantLevel(4);
		final long gearNow = now + 1_800_000;
		final var gearRaw = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "Лавка фантома", List.of(new PhantomStorePlan.Line(duplicate.getObjectId(), 70, 1, 0)), gearNow + 120_000);
		final PhantomStorePlan expectedGear = _stores.quotePlan(profileId, PhantomActivityState.ACTIVE, gearRaw, gearNow).orElseThrow();
		final PhantomStorePlan enchanted = _producer.consider(profileId, gearNow).orElseThrow(() -> new AssertionError("Owned +4 duplicate gear failed to produce a SELL store."));
		PhantomAssertions.assertEquals(PhantomStorePlan.Type.SELL, enchanted.type(), "Enchanted duplicate changed native store type.");
		PhantomAssertions.assertEquals(duplicate.getObjectId(), enchanted.lines().get(0).objectOrRecipeId(), "Autonomous enchanted SELL lost exact object identity.");
		PhantomAssertions.assertEquals(expectedGear.lines().get(0).price(), enchanted.lines().get(0).price(), "Expected +N quote drifted before native publication.");
		PhantomAssertions.assertEquals(enchanted.lines().get(0).price(), owner.getSellList().getItems().iterator().next().getPrice(), "Expected +N quote drifted in native TradeList.");
		Thread.sleep(2_600);
		PhantomAssertions.assertEquals(PhantomStoreService.Result.CLOSED, _stores.close(profileId), "Enchanted object store did not close.");
		awaitNativeIdle(owner);
		context.record("market.safety", "reservationAndGoalExcluded=true:staleRequoteClosed=true:enchantedObjectId=" + duplicate.getObjectId() + ":plus4Ask=" + enchanted.lines().get(0).price());
	}

	private static void fund(Player owner, int itemId, long count)
	{
		PhantomAssertions.assertTrue(owner.getInventory().addItem(ItemProcessType.REWARD, itemId, count, owner, null) != null, "Isolated fixture funding failed.");
	}

	private static void awaitNativeIdle(Player owner) throws InterruptedException
	{
		final long deadline = System.nanoTime() + 4_000_000_000L;
		while (!owner.canMakeSocialAction() && (System.nanoTime() < deadline))
		{
			Thread.sleep(50);
		}
		PhantomAssertions.assertTrue(owner.canMakeSocialAction(), "Native stand-up animation did not return Player AI to IDLE.");
	}

	private static long count(Player owner, int itemId)
	{
		return owner.getInventory().getInventoryItemCount(itemId, -1);
	}

	private static void clearItem(Player owner, int itemId)
	{
		final long amount = count(owner, itemId);
		if (amount > 0)
		{
			owner.getInventory().destroyItemByItemId(ItemProcessType.DESTROY, itemId, amount, owner, null);
		}
	}

	private static void clearGear(Player owner)
	{
		final Item weapon = owner.getInventory().getItemByItemId(70);
		if ((weapon != null) && weapon.isEquipped())
		{
			owner.getInventory().unEquipItemInBodySlot(weapon.getTemplate().getBodyPart());
		}
		clearItem(owner, 70);
	}

	private static void restoreAdena(Player owner, long baseline)
	{
		final long current = owner.getAdena();
		if (current > baseline)
		{
			owner.getInventory().destroyItemByItemId(ItemProcessType.DESTROY, 57, current - baseline, owner, null);
		}
		else if (current < baseline)
		{
			fund(owner, 57, baseline - current);
		}
	}

	private void deleteProfile(PhantomProfile profile)
	{
		if (profile != null)
		{
			final var current = _profiles.find(profile.profileId()).orElse(null);
			if (current != null)
			{
				_profiles.delete(profile.profileId(), current.rowVersion());
			}
		}
	}
}

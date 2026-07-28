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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.BuyListData;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.BuyOffer;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.CatalogPage;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.MultisellOffer;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.SupplyFact;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.SupplyKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.TeleportRoute;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader.CommerceFixtures;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader.LoadResult;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceDecision;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.ConservationFacts;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationRequest;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.Reconciliation;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.State;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore.VersionedReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.ActorFacts;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.ActorLease;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.Backend;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.OperationIntent;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.OperationStatus;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.Quote;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.ReceiptPersistence;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

public final class PhantomCommerceSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG("commerce-catalog", true),
		SUPPLY("commerce-supply", true),
		QUOTE("commerce-quote", false),
		RECEIPT("commerce-receipt", false),
		DECISION("commerce-decision", false),
		SERVER_INTEGRATION("commerce-server-integration", true),
		PERFORMANCE("commerce-performance", true);

		private final String _id;
		private final boolean _productionData;

		Mode(String id, boolean productionData)
		{
			_id = id;
			_productionData = productionData;
		}
	}

	private final Mode _mode;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private LoadResult _production;

	public PhantomCommerceSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return _mode._id;
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(140014L, context.seed(), "Goal 014 seed changed.");
		if (_mode._productionData)
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			_production = new PhantomCommerceCatalogLoader(Path.of(".")).load();
			context.record("commerce.buyOffers", _production.catalog().buyOffers().size());
			context.record("commerce.multisellOffers", _production.catalog().multisellOffers().size());
			context.record("commerce.teleportRoutes", _production.catalog().teleportRoutes().size());
			context.record("commerce.supplies", _production.catalog().supplies().size());
			context.record("commerce.hash", _production.catalog().hashes().combined());
			context.record("commerce.buyHash", _production.catalog().hashes().buy());
			context.record("commerce.multisellHash", _production.catalog().hashes().multisell());
			context.record("commerce.teleportHash", _production.catalog().hashes().teleport());
			context.record("commerce.supplyHash", _production.catalog().hashes().supply());
			if (_production.fixtures().buy() != null)
			{
				context.record("commerce.buyFixture", _production.fixtures().buy().listId() + ":" + _production.fixtures().buy().itemId() + ":" + _production.fixtures().buy().price());
			}
			if (_production.fixtures().teleport() != null)
			{
				context.record("commerce.teleportFixture", _production.fixtures().teleport().npcId() + ":" + _production.fixtures().teleport().listName() + ":" + _production.fixtures().teleport().ordinal() + ":" + _production.fixtures().teleport().feeItemId() + ":" + _production.fixtures().teleport().feeCount());
			}
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CATALOG -> registerCatalog(registry);
			case SUPPLY -> registerSupply(registry);
			case QUOTE -> registerQuote(registry);
			case RECEIPT -> registerReceipt(registry);
			case DECISION -> registerDecision(registry);
			case SERVER_INTEGRATION -> registerServerIntegration(registry);
			case PERFORMANCE -> registerPerformance(registry);
		}
	}

	private void registerCatalog(PhantomTestRegistry registry)
	{
		registry.add("01-buy-loader-parity", _ -> testBuyParity());
		registry.add("02-teleporter-loader-parity", _ -> testTeleportParity());
		registry.add("03-query-bounds-and-immutability", _ -> testQueryBounds());
		registry.add("04-component-and-combined-hashes", _ -> testHashes());
		registry.add("05-deterministic-fixtures", _ -> testFixtures());
		registry.add("06-multisell-query-only", _ -> testMultisellQuery());
	}

	private void registerSupply(PhantomTestRegistry registry)
	{
		registry.add("01-cp-5591-mechanics", _ -> testCp(5591, 25));
		registry.add("02-cp-5592-mechanics", _ -> testCp(5592, 100));
		registry.add("03-cp-current-buy-source", _ -> testCpBuySources());
		registry.add("04-cp-current-multisell-source", _ -> testCpMultisellSources());
		registry.add("05-supply-mechanical-classes", _ -> testSupplyClasses());
	}

	private void registerQuote(PhantomTestRegistry registry)
	{
		registry.add("01-buy-conservation", _ -> testOperation(OperationKind.BUY));
		registry.add("02-sell-conservation", _ -> testOperation(OperationKind.SELL));
		registry.add("03-teleport-conservation", _ -> testOperation(OperationKind.TELEPORT));
		registry.add("04-lifecycle-no-worker", _ -> testLifecycle());
	}

	private void registerReceipt(PhantomTestRegistry registry)
	{
		registry.add("01-codec-round-trip", _ -> testReceiptCodec());
		registry.add("02-prepared-restart", _ -> testPreparedRestart());
		registry.add("03-committing-restart", _ -> testCommittingRestart());
		registry.add("04-after-first-effect", _ -> testAfterFirstEffect());
		registry.add("05-after-final-effect", _ -> testAfterFinalEffect());
		registry.add("06-concurrent-delta-inconsistent", _ -> testAmbiguousDelta());
		registry.add("07-same-key-and-new-revision", _ -> testSameKeyAndRevision());
	}

	private void registerDecision(PhantomTestRegistry registry)
	{
		registry.add("01-exact-registrations", _ -> testDecisionRegistrations());
		registry.add("02-explicit-buy-plan", _ -> testExplicitPlan("candidate.commerce.buy", acquireGoal()));
		registry.add("03-explicit-sell-plan", _ -> testExplicitPlan("candidate.commerce.sell", sellGoal()));
		registry.add("04-explicit-teleport-plan", _ -> testExplicitPlan("candidate.commerce.teleport", teleportGoal()));
		registry.add("05-no-learn-skill-candidate", _ -> testNoLearnSkill());
	}

	private void registerServerIntegration(PhantomTestRegistry registry)
	{
		registry.add("01-durable-component-restart", this::testDurableComponentRestart);
		registry.add("02-component-payload-bound", _ -> PhantomAssertions.assertTrue(receipt(OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY)).encode().length <= 4096, "Receipt payload exceeded component limit."));
	}

	private void registerPerformance(PhantomTestRegistry registry)
	{
		registry.add("01-100k-static-queries", this::testStaticQueryPerformance);
		registry.add("02-10k-reconciliations", this::testReconciliationPerformance);
		registry.add("03-deterministic-rebuild", _ -> PhantomAssertions.assertEquals(_production.catalog().hashes(), new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog().hashes(), "Production commerce rebuild is nondeterministic."));
	}

	private void testBuyParity()
	{
		for (BuyOffer offer : _production.catalog().buyOffers())
		{
			final var list = BuyListData.getInstance().getBuyList(offer.listId());
			PhantomAssertions.assertTrue(list != null, "Catalog retained missing buy list " + offer.listId());
			final var product = list.getProductByItemId(offer.itemId());
			PhantomAssertions.assertTrue(product != null, "Catalog retained missing buy product " + offer.itemId());
			PhantomAssertions.assertEquals(product.getPrice(), offer.price(), "Buy price diverged from loader.");
			PhantomAssertions.assertEquals(product.hasLimitedStock(), offer.limitedStock(), "Limited-stock truth diverged.");
		}
	}

	private void testTeleportParity()
	{
		for (TeleportRoute route : _production.catalog().teleportRoutes())
		{
			final var holder = TeleporterData.getInstance().getHolder(route.npcId(), route.listName());
			PhantomAssertions.assertTrue(holder != null, "Catalog retained missing teleporter holder.");
			final var location = holder.getLocations().get(route.ordinal());
			PhantomAssertions.assertEquals(location.getX(), route.destination().getX(), "Teleport X diverged.");
			PhantomAssertions.assertEquals(location.getFeeId(), route.feeItemId(), "Teleport fee item diverged.");
			PhantomAssertions.assertEquals(location.getFeeCount(), route.feeCount(), "Teleport fee count diverged.");
		}
	}

	private void testQueryBounds()
	{
		final BuyOffer fixture = _production.fixtures().buy();
		PhantomAssertions.assertTrue(fixture != null, "No deterministic unlimited positive supply buy fixture.");
		final CatalogPage<BuyOffer> page = _production.catalog().findBuyOffers(fixture.itemId(), 0, 1);
		PhantomAssertions.assertTrue(page.values().size() <= 1, "Buy query ignored page size.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> page.values().clear(), "Catalog page is mutable.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> _production.catalog().findBuyOffers(fixture.itemId(), 0, 257), "Oversized page was accepted.");
	}

	private void testHashes()
	{
		final var hashes = _production.catalog().hashes();
		PhantomAssertions.assertEquals(64, hashes.buy().length(), "Buy hash width changed.");
		PhantomAssertions.assertEquals(64, hashes.multisell().length(), "Multisell hash width changed.");
		PhantomAssertions.assertEquals(64, hashes.teleport().length(), "Teleport hash width changed.");
		PhantomAssertions.assertEquals(64, hashes.supply().length(), "Supply hash width changed.");
		PhantomAssertions.assertEquals(64, hashes.combined().length(), "Combined hash width changed.");
	}

	private void testFixtures()
	{
		final CommerceFixtures fixtures = _production.fixtures();
		PhantomAssertions.assertTrue(fixtures.buy() != null, "Buy fixture was not discovered.");
		PhantomAssertions.assertEquals(fixtures.buy().itemId(), fixtures.sellItemId(), "Sell fixture did not derive from buy fixture.");
		PhantomAssertions.assertTrue(fixtures.teleport() != null, "NORMAL Gatekeeper fixture was not discovered.");
		PhantomAssertions.assertEquals(0, fixtures.teleport().destination().getInstanceId(), "Teleport fixture is instanced.");
	}

	private void testMultisellQuery()
	{
		final List<MultisellOffer> offers = _production.catalog().findMultisellOffers(5591, 0, 256).values();
		PhantomAssertions.assertTrue(!offers.isEmpty(), "CP multisell query returned no source.");
		PhantomAssertions.assertTrue(offers.stream().allMatch(offer -> offer.listId() > 0), "Multisell query invented an invalid list.");
	}

	private void testCp(int itemId, long weight)
	{
		final SupplyFact fact = _production.catalog().findSupply(itemId);
		PhantomAssertions.assertTrue(fact != null, "Missing CP supply " + itemId);
		PhantomAssertions.assertTrue(fact.kinds().contains(SupplyKind.CP_RESTORE), "CP supply lacks CP_RESTORE mechanic.");
		PhantomAssertions.assertEquals(Set.of(2166), fact.boundSkillIds(), "CP supply skill binding changed.");
		PhantomAssertions.assertEquals(500L, fact.reuseDelay(), "CP supply reuse changed.");
		PhantomAssertions.assertTrue(fact.olympiadRestricted(), "CP supply Olympiad restriction disappeared.");
		PhantomAssertions.assertEquals(weight, fact.weight(), "CP supply weight changed.");
		PhantomAssertions.assertTrue(fact.stackable(), "CP supply ceased to be stackable.");
	}

	private void testCpBuySources()
	{
		for (int itemId : List.of(5591, 5592))
		{
			final List<BuyOffer> offers = _production.catalog().findBuyOffers(itemId, 0, 256).values();
			PhantomAssertions.assertEquals(1, offers.size(), "Unexpected CP buy source count.");
			PhantomAssertions.assertEquals(9928, offers.get(0).listId(), "CP buy list changed.");
			PhantomAssertions.assertEquals(0L, offers.get(0).price(), "CP zero-price current buy source changed.");
			PhantomAssertions.assertTrue(offers.get(0).npcIds().isEmpty(), "CP buy source unexpectedly gained a vendor.");
		}
	}

	private void testCpMultisellSources()
	{
		assertCpMultisell(5591, 240);
		assertCpMultisell(5592, 600);
	}

	private void assertCpMultisell(int itemId, long expectedCount)
	{
		final List<MultisellOffer> offers = _production.catalog().findMultisellOffers(itemId, 0, 256).values();
		final MultisellOffer source = offers.stream().filter(offer -> offer.listId() == 500).findFirst().orElseThrow();
		PhantomAssertions.assertEquals(1, source.ingredients().size(), "CP multisell ingredient count changed.");
		PhantomAssertions.assertEquals(5575, source.ingredients().get(0).itemId(), "CP current multisell currency changed.");
		PhantomAssertions.assertEquals(expectedCount, source.ingredients().get(0).count(), "CP current multisell price changed.");
		PhantomAssertions.assertTrue(source.npcIds().contains(31078), "CP current vendor set changed.");
	}

	private void testSupplyClasses()
	{
		final Set<SupplyKind> present = java.util.EnumSet.noneOf(SupplyKind.class);
		_production.catalog().supplies().forEach(fact -> present.addAll(fact.kinds()));
		PhantomAssertions.assertTrue(present.contains(SupplyKind.SHOT), "Shot classification is empty.");
		PhantomAssertions.assertTrue(present.contains(SupplyKind.CP_RESTORE), "CP classification is empty.");
		PhantomAssertions.assertTrue(present.contains(SupplyKind.PET_FOOD), "Pet-food classification is empty.");
		PhantomAssertions.assertTrue(present.contains(SupplyKind.SUMMON_RESOURCE), "Summon-resource classification is empty.");
	}

	private void testOperation(OperationKind kind)
	{
		final FakeStore store = new FakeStore();
		final FakeActor actor = new FakeActor(kind, before(kind));
		final PhantomCommerceService service = service(store, actor);
		PhantomAssertions.assertTrue(service.start(), "Commerce service did not start.");
		final var result = service.execute(1, 7, 0, intent(kind), () -> false);
		PhantomAssertions.assertEquals(OperationStatus.SUCCESS, result.status(), kind + " did not complete.");
		PhantomAssertions.assertEquals(after(kind), actor.current, kind + " conservation result changed.");
		PhantomAssertions.assertEquals(State.COMMITTED, store.value.receipt().state(), kind + " receipt was not committed.");
	}

	private void testLifecycle()
	{
		final PhantomCommerceService service = service(new FakeStore(), new FakeActor(OperationKind.BUY, before(OperationKind.BUY)));
		PhantomAssertions.assertEquals(0, service.snapshot().workers(), "Commerce created a worker before start.");
		PhantomAssertions.assertTrue(service.start(), "Commerce start failed.");
		PhantomAssertions.assertTrue(service.beginStop(), "Commerce beginStop failed.");
		PhantomAssertions.assertTrue(service.finishStop(), "Commerce finishStop failed.");
		PhantomAssertions.assertEquals(0, service.snapshot().workers(), "Commerce created a worker.");
	}

	private void testReceiptCodec()
	{
		final PhantomCommerceReceipt receipt = receipt(OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY));
		PhantomAssertions.assertEquals(receipt, PhantomCommerceReceipt.decode(receipt.encode()), "Receipt codec changed facts.");
		PhantomAssertions.assertTrue(receipt.encode().length <= 4096, "Receipt exceeds durable component bound.");
	}

	private void testPreparedRestart()
	{
		final FakeStore store = new FakeStore();
		store.save(-1, receipt(OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY)));
		assertRestartCompletes(store, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)));
	}

	private void testCommittingRestart()
	{
		final FakeStore store = new FakeStore();
		final PhantomCommerceReceipt committing = receipt(OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY)).withState(State.COMMITTING);
		store.save(-1, committing);
		assertRestartCompletes(store, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)));
		PhantomAssertions.assertEquals(1, store.value.receipt().resumeCount(), "COMMITTING restart was not bounded to one resume.");
	}

	private void testAfterFirstEffect()
	{
		final FakeStore store = new FakeStore();
		store.save(-1, receipt(OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY)).withState(State.COMMITTING));
		final FakeActor actor = new FakeActor(OperationKind.BUY, partial(OperationKind.BUY));
		assertRestartCompletes(store, actor);
		PhantomAssertions.assertEquals(0, actor.firstCalls.get(), "Paid buy was charged twice.");
		PhantomAssertions.assertEquals(1, actor.secondCalls.get(), "Missing buy output was not completed exactly once.");
	}

	private void testAfterFinalEffect()
	{
		final FakeStore store = new FakeStore();
		store.save(-1, receipt(OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY)).withState(State.COMMITTING));
		final FakeActor actor = new FakeActor(OperationKind.BUY, after(OperationKind.BUY));
		final PhantomCommerceService service = service(store, actor);
		service.start();
		PhantomAssertions.assertEquals(OperationStatus.IDEMPOTENT, service.execute(1, 7, 0, intent(OperationKind.BUY), () -> false).status(), "Final-effect restart was not idempotent.");
		PhantomAssertions.assertEquals(0, actor.firstCalls.get() + actor.secondCalls.get(), "Final-effect restart replayed a side effect.");
	}

	private void testAmbiguousDelta()
	{
		final FakeStore store = new FakeStore();
		store.save(-1, receipt(OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY)).withState(State.COMMITTING));
		final FakeActor actor = new FakeActor(OperationKind.BUY, new ConservationFacts(89, 20, 20, 0, 0, 0, 0));
		final PhantomCommerceService service = service(store, actor);
		service.start();
		PhantomAssertions.assertEquals(OperationStatus.INCONSISTENT, service.execute(1, 7, 0, intent(OperationKind.BUY), () -> false).status(), "Concurrent adena delta did not fail stop.");
		PhantomAssertions.assertEquals(State.INCONSISTENT, store.value.receipt().state(), "Ambiguous receipt was not durable INCONSISTENT.");
	}

	private void testSameKeyAndRevision()
	{
		final FakeStore store = new FakeStore();
		final FakeActor actor = new FakeActor(OperationKind.BUY, before(OperationKind.BUY));
		final PhantomCommerceService first = service(store, actor);
		first.start();
		PhantomAssertions.assertEquals(OperationStatus.SUCCESS, first.execute(1, 7, 0, intent(OperationKind.BUY), () -> false).status(), "Initial same-key operation failed.");
		PhantomAssertions.assertEquals(OperationStatus.IDEMPOTENT, first.execute(1, 7, 0, intent(OperationKind.BUY), () -> false).status(), "Same key was not idempotent.");
		PhantomAssertions.assertEquals(OperationStatus.SUCCESS, first.execute(1, 7, 1, intent(OperationKind.BUY), () -> false).status(), "New goal revision did not create a new operation.");
	}

	private void testDecisionRegistrations()
	{
		final PhantomCommerceDecision decision = new PhantomCommerceDecision(service(new FakeStore(), new FakeActor(OperationKind.BUY, before(OperationKind.BUY))));
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		decision.registerCandidates(candidates);
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		decision.registerHandlers(handlers);
		handlers.seal();
		PhantomAssertions.assertEquals(3, candidates.snapshot().size(), "Commerce candidate count changed.");
		PhantomAssertions.assertEquals(Set.of("commerce.buy", "commerce.observe", "commerce.sell", "commerce.teleport"), handlers.snapshot().keySet(), "Commerce action registrations changed.");
	}

	private void testExplicitPlan(String candidateKey, PhantomGoal goal)
	{
		final PhantomCommerceDecision decision = new PhantomCommerceDecision(service(new FakeStore(), new FakeActor(OperationKind.BUY, before(OperationKind.BUY))));
		final PhantomCandidateRegistry registry = new PhantomCandidateRegistry();
		decision.registerCandidates(registry);
		registry.seal();
		final PhantomDecisionCandidate candidate = registry.snapshot().stream().filter(value -> candidateKey.equals(value.key())).findFirst().orElseThrow();
		final PhantomPlan plan = candidate.planFactory().create(new PhantomPlanningContext(1, goal, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, 0, 1));
		PhantomAssertions.assertEquals(1, plan.steps().size(), "Commerce plan is not one bounded mutating step.");
		PhantomAssertions.assertTrue(plan.steps().get(0).actionKey().startsWith("commerce."), "Commerce plan emitted a foreign action.");
	}

	private void testNoLearnSkill()
	{
		final PhantomCandidateRegistry registry = new PhantomCandidateRegistry();
		new PhantomCommerceDecision(service(new FakeStore(), new FakeActor(OperationKind.BUY, before(OperationKind.BUY)))).registerCandidates(registry);
		registry.seal();
		PhantomAssertions.assertTrue(registry.snapshot().stream().noneMatch(candidate -> candidate.key().contains("learn_skill") || candidate.supportedGoalTypes().contains("progression.learn_skill")), "Commerce exposed progression.learn_skill.");
	}

	private void testDurableComponentRestart(PhantomTestContext context) throws Exception
	{
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(null);
		try
		{
			final PhantomCommerceReceiptStore store = new PhantomCommerceReceiptStore(repository);
			VersionedReceipt value = store.save(-1, receipt(profile.profileId(), OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY)));
			value = store.save(value.rowVersion(), value.receipt().withState(State.COMMITTING));
			final PhantomCommerceReceiptStore restarted = new PhantomCommerceReceiptStore(PhantomProfileRepository.open());
			final VersionedReceipt loaded = restarted.find(profile.profileId()).orElseThrow();
			PhantomAssertions.assertEquals(value.receipt(), loaded.receipt(), "Durable receipt did not survive repository restart.");
			context.record("commerce.receiptBytes", loaded.receipt().encode().length);
		}
		finally
		{
			deleteProfile(profile.profileId());
		}
	}

	private void testStaticQueryPerformance(PhantomTestContext context)
	{
		final PhantomCommerceCatalog catalog = _production.catalog();
		final int itemId = _production.fixtures().buy().itemId();
		final long started = System.nanoTime();
		for (int index = 0; index < 100_000; index++)
		{
			catalog.findBuyOffers(itemId, 0, 16);
			catalog.findSupply(itemId);
		}
		final long elapsed = System.nanoTime() - started;
		context.record("commerce.staticQueries", 100_000);
		context.record("commerce.staticQueryNanos", elapsed);
		PhantomAssertions.assertTrue(elapsed < 10_000_000_000L, "100k static queries exceeded 10 seconds.");
	}

	private void testReconciliationPerformance(PhantomTestContext context)
	{
		final PhantomCommerceReceipt receipt = receipt(OperationKind.BUY, before(OperationKind.BUY), after(OperationKind.BUY)).withState(State.COMMITTING);
		final long started = System.nanoTime();
		for (int index = 0; index < 10_000; index++)
		{
			PhantomAssertions.assertEquals(Reconciliation.FIRST_EFFECT_ONLY, receipt.reconcile(partial(OperationKind.BUY)), "Receipt reconciliation changed.");
		}
		final long elapsed = System.nanoTime() - started;
		context.record("commerce.reconciliations", 10_000);
		context.record("commerce.reconciliationNanos", elapsed);
		PhantomAssertions.assertTrue(elapsed < 2_000_000_000L, "10k receipt reconciliations exceeded 2 seconds.");
	}

	private void assertRestartCompletes(FakeStore store, FakeActor actor)
	{
		final PhantomCommerceService service = service(store, actor);
		service.start();
		PhantomAssertions.assertEquals(OperationStatus.SUCCESS, service.execute(1, 7, 0, intent(OperationKind.BUY), () -> false).status(), "Restart reconciliation did not complete.");
		PhantomAssertions.assertEquals(State.COMMITTED, store.value.receipt().state(), "Restart receipt was not committed.");
	}

	private static PhantomCommerceService service(FakeStore store, FakeActor actor)
	{
		final PhantomCommerceCatalog catalog = new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of());
		final LoadResult load = new LoadResult(catalog, new CommerceFixtures(null, 0, null, List.of(), List.of(), List.of(), List.of()));
		return new PhantomCommerceService(load, store, profileId -> profileId == 1 ? Optional.of(actor) : Optional.empty());
	}

	private static PhantomCommerceReceipt receipt(OperationKind kind, ConservationFacts before, ConservationFacts after)
	{
		return receipt(1, kind, before, after);
	}

	private static PhantomCommerceReceipt receipt(long profileId, OperationKind kind, ConservationFacts before, ConservationFacts after)
	{
		return PhantomCommerceReceipt.prepared(profileId, 7, 0, request(kind), before, after);
	}

	private static OperationRequest request(OperationKind kind)
	{
		return switch (kind)
		{
			case BUY -> new OperationRequest(kind, 1, 2, 3, 4, 0, 5, 10, 0, 0, 0, "", 0, 0, 0);
			case SELL -> new OperationRequest(kind, 1, 2, 3, 4, 6, 5, 10, 0, 0, 0, "", 0, 0, 0);
			case TELEPORT -> new OperationRequest(kind, 1, 2, 0, 0, 0, 0, 0, 57, 10, 0, "NORMAL", 100, 200, 300);
		};
	}

	private static OperationIntent intent(OperationKind kind)
	{
		return switch (kind)
		{
			case BUY -> new OperationIntent(kind, 1, 2, 3, 4, 0, 5, 0, "", 100);
			case SELL -> new OperationIntent(kind, 1, 2, 3, 4, 6, 5, 0, "", 0);
			case TELEPORT -> new OperationIntent(kind, 1, 2, 0, 0, 0, 0, 0, "NORMAL", 100);
		};
	}

	private static ConservationFacts before(OperationKind kind)
	{
		return switch (kind)
		{
			case BUY -> new ConservationFacts(100, 20, 20, 0, 0, 0, 0);
			case SELL -> new ConservationFacts(100, 20, 5, 0, 0, 0, 0);
			case TELEPORT -> new ConservationFacts(100, 0, 0, 0, 0, 0, 0);
		};
	}

	private static ConservationFacts partial(OperationKind kind)
	{
		return switch (kind)
		{
			case BUY -> new ConservationFacts(90, 20, 20, 0, 0, 0, 0);
			case SELL -> new ConservationFacts(100, 15, 0, 0, 0, 0, 0);
			case TELEPORT -> new ConservationFacts(90, 0, 0, 0, 0, 0, 0);
		};
	}

	private static ConservationFacts after(OperationKind kind)
	{
		return switch (kind)
		{
			case BUY -> new ConservationFacts(90, 25, 25, 0, 0, 0, 0);
			case SELL -> new ConservationFacts(110, 15, 0, 0, 0, 0, 0);
			case TELEPORT -> new ConservationFacts(90, 0, 0, 0, 100, 200, 300);
		};
	}

	private static PhantomGoal acquireGoal()
	{
		return goal("acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"));
	}

	private static PhantomGoal sellGoal()
	{
		return goal("sell.item", new PhantomDomainRef("commerce.sell", "1:2:3:4:6:5"));
	}

	private static PhantomGoal teleportGoal()
	{
		final String encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("NORMAL".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		return goal("travel.teleport", new PhantomDomainRef("commerce.teleport", "1:2:0:" + encoded));
	}

	private static PhantomGoal goal(String type, PhantomDomainRef source)
	{
		return new PhantomGoal(7, type, PhantomGoalStatus.ACTIVE, null, null, 5, 0, null, List.of(source), null, "commerce.test", 500, 0, 100, 0, Map.of(), "commerce.test", 0);
	}

	private static void deleteProfile(long profileId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals("l2jmobiush5_phantom_test", connection.getCatalog(), "Commerce integration touched a non-test database.");
			try (PreparedStatement components = connection.prepareStatement("DELETE FROM phantom_profile_components WHERE profile_id = ?");
				PreparedStatement profiles = connection.prepareStatement("DELETE FROM phantom_profiles WHERE profile_id = ?"))
			{
				components.setLong(1, profileId);
				components.executeUpdate();
				profiles.setLong(1, profileId);
				profiles.executeUpdate();
			}
		}
	}

	private static final class FakeStore implements ReceiptPersistence
	{
		private VersionedReceipt value;

		@Override
		public Optional<VersionedReceipt> find(long profileId)
		{
			return Optional.ofNullable(value).filter(stored -> stored.receipt().profileId() == profileId);
		}

		@Override
		public VersionedReceipt save(long expectedRowVersion, PhantomCommerceReceipt receipt)
		{
			if (value == null)
			{
				if (expectedRowVersion != -1)
				{
					throw new java.util.ConcurrentModificationException();
				}
				value = new VersionedReceipt(0, receipt);
			}
			else
			{
				if (expectedRowVersion != value.rowVersion())
				{
					throw new java.util.ConcurrentModificationException();
				}
				value = new VersionedReceipt(value.rowVersion() + 1, receipt);
			}
			return value;
		}
	}

	private static final class FakeActor implements ActorLease
	{
		private final OperationKind kind;
		private final AtomicInteger firstCalls = new AtomicInteger();
		private final AtomicInteger secondCalls = new AtomicInteger();
		private ConservationFacts current;

		private FakeActor(OperationKind kind, ConservationFacts current)
		{
			this.kind = kind;
			this.current = current;
		}

		@Override
		public Quote quote(OperationIntent intent)
		{
			final OperationRequest request = request(kind);
			final ConservationFacts expected = switch (kind)
			{
				case BUY -> new ConservationFacts(current.primaryCount() - 10, current.secondaryCount() + 5, current.objectCount() + 5, current.instanceId(), current.x(), current.y(), current.z());
				case SELL -> new ConservationFacts(current.primaryCount() + 10, current.secondaryCount() - 5, current.objectCount() - 5, current.instanceId(), current.x(), current.y(), current.z());
				case TELEPORT -> new ConservationFacts(current.primaryCount() - 10, 0, 0, 0, 100, 200, 300);
			};
			return Quote.accepted(request, current, expected, new ActorFacts(100, 20, 5, 0, 1000, 0, false, 0, false, false, false, false, false, 0, 0, 0, 0, 2, 2));
		}

		@Override
		public ConservationFacts snapshot(OperationRequest request)
		{
			return current;
		}

		@Override
		public boolean applyFirst(OperationRequest request)
		{
			firstCalls.incrementAndGet();
			current = switch (kind)
			{
				case BUY -> new ConservationFacts(current.primaryCount() - 10, current.secondaryCount(), current.objectCount(), current.instanceId(), current.x(), current.y(), current.z());
				case SELL -> new ConservationFacts(current.primaryCount(), current.secondaryCount() - 5, current.objectCount() - 5, current.instanceId(), current.x(), current.y(), current.z());
				case TELEPORT -> new ConservationFacts(current.primaryCount() - 10, 0, 0, current.instanceId(), current.x(), current.y(), current.z());
			};
			return true;
		}

		@Override
		public boolean applySecond(OperationRequest request)
		{
			secondCalls.incrementAndGet();
			current = switch (kind)
			{
				case BUY -> new ConservationFacts(current.primaryCount(), current.secondaryCount() + 5, current.objectCount() + 5, current.instanceId(), current.x(), current.y(), current.z());
				case SELL -> new ConservationFacts(current.primaryCount() + 10, current.secondaryCount(), current.objectCount(), current.instanceId(), current.x(), current.y(), current.z());
				case TELEPORT -> new ConservationFacts(current.primaryCount(), 0, 0, 0, 100, 200, 300);
			};
			return true;
		}

		@Override
		public void close()
		{
		}
	}
}

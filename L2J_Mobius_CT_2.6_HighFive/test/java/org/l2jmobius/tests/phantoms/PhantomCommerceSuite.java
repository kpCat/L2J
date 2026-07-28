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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.custom.MerchantZeroSellPriceConfig;
import org.l2jmobius.gameserver.data.MerchantPriceConfigTable;
import org.l2jmobius.gameserver.data.xml.BuyListData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportType;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.actor.instance.Teleporter;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.commerce.L2jCommerceBackend;
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
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
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
		HARDENING("commerce-hardening", true),
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
		final long expectedSeed = _mode == Mode.HARDENING ? 14001401L : 140014L;
		PhantomAssertions.assertEquals(expectedSeed, context.seed(), "Commerce suite seed changed.");
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
			case HARDENING -> registerHardening(registry);
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

	private void registerHardening(PhantomTestRegistry registry)
	{
		registry.add("01-exact-catalog-identity-beyond-256", _ -> testExactCatalogIdentity());
		registry.add("02-goal-authority-and-terminal-rollover", _ -> testGoalAuthorityAndRollover());
		registry.add("03-lifecycle-drain-and-counters", _ -> testLifecycleDrain());
		registry.add("04-system-shutdown-respects-commerce-claim", this::testSystemShutdownDrain);
		registry.add("05-real-player-buy-sell-teleport-reload", this::testRealBackendIntegration);
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
		final FakeGoalStore goals = new FakeGoalStore(1, goal(7, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5")));
		final PhantomCommerceService first = service(store, actor, goals);
		first.start();
		PhantomAssertions.assertEquals(OperationStatus.SUCCESS, first.execute(1, 7, 0, intent(OperationKind.BUY), () -> false).status(), "Initial same-key operation failed.");
		PhantomAssertions.assertEquals(OperationStatus.IDEMPOTENT, first.execute(1, 7, 0, intent(OperationKind.BUY), () -> false).status(), "Same key was not idempotent.");
		goals.replace(1, 0, goal(7, 1, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5")));
		PhantomAssertions.assertEquals(OperationStatus.SUCCESS, first.execute(1, 7, 1, intent(OperationKind.BUY), () -> false).status(), "New goal revision did not create a new operation.");
	}

	private void testExactCatalogIdentity()
	{
		final ArrayList<BuyOffer> buyOffers = new ArrayList<>();
		for (int index = 0; index < 300; index++)
		{
			buyOffers.add(new BuyOffer(index + 1, 900001, Set.of(1), index + 1L, false, "decoy-buy-" + index));
		}
		final BuyOffer exactBuy = new BuyOffer(1001, 900001, Set.of(1), 1001, false, "exact-buy");
		buyOffers.add(exactBuy);
		final Location destination = _production.catalog().teleportRoutes().getFirst().destination();
		final ArrayList<TeleportRoute> teleportRoutes = new ArrayList<>();
		for (int index = 0; index < 300; index++)
		{
			teleportRoutes.add(new TeleportRoute(900001, "NORMAL", TeleportType.NORMAL, index, destination, 57, index, Set.of(), "decoy-teleport-" + index));
		}
		final TeleportRoute exactTeleport = teleportRoutes.get(299);
		final PhantomCommerceCatalog catalog = new PhantomCommerceCatalog(buyOffers, List.of(), teleportRoutes, List.of());
		PhantomAssertions.assertEquals(exactBuy, catalog.findBuyOffer(exactBuy.listId(), exactBuy.itemId()), "Exact buy identity beyond page 0 was not found.");
		PhantomAssertions.assertEquals(exactTeleport, catalog.findTeleportRoute(exactTeleport.npcId(), exactTeleport.listName(), exactTeleport.ordinal()), "Exact teleport identity beyond page 0 was not found.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomCommerceCatalog(List.of(exactBuy, new BuyOffer(exactBuy.listId(), exactBuy.itemId(), Set.of(2), exactBuy.price(), false, "duplicate-buy")), List.of(), List.of(), List.of()), "Duplicate exact buy identity was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomCommerceCatalog(List.of(), List.of(), List.of(exactTeleport, new TeleportRoute(exactTeleport.npcId(), exactTeleport.listName(), exactTeleport.type(), exactTeleport.ordinal(), exactTeleport.destination(), exactTeleport.feeItemId(), exactTeleport.feeCount() + 1, exactTeleport.castleIds(), "duplicate-teleport")), List.of()), "Conflicting exact teleport identity was accepted.");
	}

	private void testGoalAuthorityAndRollover()
	{
		final OperationIntent changed = new OperationIntent(OperationKind.BUY, 1, 2, 3, 4, 0, 6, 0, "", 100);

		final FakeStore conflictStore = new FakeStore();
		conflictStore.save(-1, terminalReceipt(1, 7, 0, State.COMMITTED));
		final long conflictVersion = conflictStore.value.rowVersion();
		final PhantomCommerceService conflict = service(conflictStore, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)), new FakeGoalStore(1, goal(7, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"))));
		conflict.start();
		final var conflictResult = conflict.execute(1, 7, 0, changed, () -> false);
		PhantomAssertions.assertEquals(OperationStatus.REPLAN, conflictResult.status(), "Same-revision changed request did not replan.");
		PhantomAssertions.assertEquals(PhantomCommerceService.Reason.GOAL_REVISION_CONFLICT, conflictResult.reason(), "Same-revision changed request was not typed as a goal conflict.");
		PhantomAssertions.assertEquals(conflictVersion, conflictStore.value.rowVersion(), "Same-revision conflict overwrote the terminal receipt.");

		final FakeStore lowerStore = new FakeStore();
		lowerStore.save(-1, terminalReceipt(1, 7, 0, State.COMMITTED));
		final PhantomCommerceService lower = service(lowerStore, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)), new FakeGoalStore(1, goal(7, 2, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"))));
		lower.start();
		final var lowerResult = lower.execute(1, 7, 1, intent(OperationKind.BUY), () -> false);
		PhantomAssertions.assertEquals(PhantomCommerceService.Reason.STALE_GOAL_REVISION, lowerResult.reason(), "Lower revision was not rejected as stale.");
		PhantomAssertions.assertEquals(7L, lowerStore.value.receipt().goalId(), "Lower revision replaced a committed receipt.");

		final FakeStore staleGoalStore = new FakeStore();
		staleGoalStore.save(-1, terminalReceipt(1, 7, 0, State.COMMITTED));
		final PhantomCommerceService staleGoal = service(staleGoalStore, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)), new FakeGoalStore(1, goal(9, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"))));
		staleGoal.start();
		final var staleGoalResult = staleGoal.execute(1, 8, 0, intent(OperationKind.BUY), () -> false);
		PhantomAssertions.assertEquals(PhantomCommerceService.Reason.STALE_GOAL, staleGoalResult.reason(), "Stale different goal was not rejected.");
		PhantomAssertions.assertEquals(7L, staleGoalStore.value.receipt().goalId(), "Stale different goal replaced a committed receipt.");

		final FakeStore authorityRaceStore = new FakeStore();
		final FakeActor authorityRaceActor = new FakeActor(OperationKind.BUY, before(OperationKind.BUY));
		final PhantomGoal authorityRaceInitial = goal(7, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"));
		final PhantomGoal authorityRaceReplacement = goal(7, 1, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"));
		final PhantomCommerceService authorityRace = service(authorityRaceStore, authorityRaceActor, new FakeGoalStore(1, authorityRaceInitial, authorityRaceReplacement));
		authorityRace.start();
		final var authorityRaceResult = authorityRace.execute(1, 7, 0, intent(OperationKind.BUY), () -> false);
		PhantomAssertions.assertEquals(OperationStatus.REPLAN, authorityRaceResult.status(), "Authority change before PREPARED did not replan.");
		PhantomAssertions.assertEquals(PhantomCommerceService.Reason.STALE_GOAL_REVISION, authorityRaceResult.reason(), "Authority change before PREPARED was not typed as stale revision.");
		PhantomAssertions.assertTrue(authorityRaceStore.value == null, "Authority race persisted a receipt.");
		PhantomAssertions.assertEquals(0, authorityRaceActor.firstCalls.get(), "Authority race invoked applyFirst.");
		PhantomAssertions.assertEquals(0, authorityRaceActor.secondCalls.get(), "Authority race invoked applySecond.");

		final FakeStore rolloverStore = new FakeStore();
		rolloverStore.save(-1, terminalReceipt(1, 7, 0, State.COMMITTED));
		final PhantomCommerceService rollover = service(rolloverStore, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)), new FakeGoalStore(1, goal(9, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"))));
		rollover.start();
		PhantomAssertions.assertEquals(OperationStatus.SUCCESS, rollover.execute(1, 9, 0, intent(OperationKind.BUY), () -> false).status(), "Current new goal did not replace a committed receipt.");
		PhantomAssertions.assertEquals(9L, rolloverStore.value.receipt().goalId(), "Current new goal rollover stored the wrong authority.");

		final FakeStore abortedStore = new FakeStore();
		abortedStore.save(-1, terminalReceipt(1, 7, 0, State.ABORTED));
		final PhantomCommerceService aborted = service(abortedStore, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)), new FakeGoalStore(1, goal(7, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"))));
		aborted.start();
		PhantomAssertions.assertEquals(OperationStatus.CANCELLED, aborted.execute(1, 7, 0, intent(OperationKind.BUY), () -> false).status(), "Exact ABORTED retry did not stay typed cancelled.");
		PhantomAssertions.assertEquals(State.ABORTED, abortedStore.value.receipt().state(), "Exact ABORTED retry mutated the receipt.");

		final FakeStore busyStore = new FakeStore();
		busyStore.save(-1, terminalReceipt(1, 7, 0, State.COMMITTING));
		final long busyVersion = busyStore.value.rowVersion();
		final PhantomCommerceService busy = service(busyStore, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)), new FakeGoalStore(1, goal(9, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"))));
		busy.start();
		PhantomAssertions.assertEquals(PhantomCommerceService.Reason.OPERATION_BUSY, busy.execute(1, 9, 0, intent(OperationKind.BUY), () -> false).reason(), "Nonterminal mismatch was not rejected as busy.");
		PhantomAssertions.assertEquals(busyVersion, busyStore.value.rowVersion(), "Nonterminal mismatch overwrote the receipt.");

		final FakeStore inconsistentStore = new FakeStore();
		inconsistentStore.save(-1, terminalReceipt(1, 7, 0, State.INCONSISTENT));
		final PhantomCommerceService inconsistent = service(inconsistentStore, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)), new FakeGoalStore(1, goal(9, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"))));
		inconsistent.start();
		PhantomAssertions.assertEquals(OperationStatus.INCONSISTENT, inconsistent.execute(1, 9, 0, intent(OperationKind.BUY), () -> false).status(), "INCONSISTENT receipt did not permanently fail stop the profile.");
	}

	private void testLifecycleDrain() throws Exception
	{
		final BlockingStore store = new BlockingStore();
		final PhantomCommerceService service = service(store, new FakeActor(OperationKind.BUY, before(OperationKind.BUY)));
		PhantomAssertions.assertTrue(service.start(), "Blocking commerce service did not start.");
		final AtomicReference<PhantomCommerceService.OperationResult> result = new AtomicReference<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final Thread operation = new Thread(() ->
		{
			try
			{
				result.set(service.execute(1, 7, 0, intent(OperationKind.BUY), () -> false));
			}
			catch (Throwable throwable)
			{
				failure.set(throwable);
			}
		}, "goal014a-commerce-drain");
		operation.start();
		PhantomAssertions.assertTrue(store.entered.await(5, TimeUnit.SECONDS), "Commerce operation did not reach the controlled persistence seam.");
		final PhantomCommerceService.Snapshot blocked = service.snapshot();
		PhantomAssertions.assertEquals(1, blocked.currentOperations(), "Blocked operation was not owned.");
		PhantomAssertions.assertEquals(1, blocked.currentActorLeases(), "Blocked actor lease was not owned.");
		PhantomAssertions.assertEquals(1, blocked.currentPersistenceClaims(), "Blocked persistence claim was not owned.");
		PhantomAssertions.assertTrue(service.beginStop(), "Commerce beginStop failed while work was accepted.");
		PhantomAssertions.assertFalse(service.finishStop(), "Commerce finishStop crossed active ownership.");
		PhantomAssertions.assertEquals(PhantomCommerceService.Reason.SERVICE_NOT_RUNNING, service.execute(2, 7, 0, intent(OperationKind.BUY), () -> false).reason(), "STOPPING commerce admitted new work.");
		store.release.countDown();
		operation.join(5000);
		PhantomAssertions.assertFalse(operation.isAlive(), "Blocked commerce operation did not drain.");
		if (failure.get() != null)
		{
			throw new AssertionError("Blocked commerce operation failed.", failure.get());
		}
		PhantomAssertions.assertEquals(OperationStatus.SUCCESS, result.get().status(), "Accepted commerce operation did not finish during STOPPING.");
		final PhantomCommerceService.Snapshot drained = service.snapshot();
		PhantomAssertions.assertEquals(0, drained.currentOperations(), "Commerce operation counter leaked.");
		PhantomAssertions.assertEquals(0, drained.currentActorLeases(), "Commerce actor lease counter leaked.");
		PhantomAssertions.assertEquals(0, drained.currentPersistenceClaims(), "Commerce persistence claim counter leaked.");
		PhantomAssertions.assertEquals(1, drained.peakOperations(), "Commerce operation peak is not exact.");
		PhantomAssertions.assertEquals(1, drained.peakActorLeases(), "Commerce actor lease peak is not exact.");
		PhantomAssertions.assertEquals(1, drained.peakPersistenceClaims(), "Commerce persistence peak is not exact.");
		PhantomAssertions.assertTrue(service.finishStop(), "Commerce service did not stop after ownership drained.");
	}

	private void testSystemShutdownDrain(PhantomTestContext context) throws Exception
	{
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		final BlockingStore store = new BlockingStore();
		final FakeGoalStore goals = new FakeGoalStore(profile.profileId(), goal(7, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5")));
		final PhantomCommerceCatalog catalog = new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of());
		final LoadResult load = new LoadResult(catalog, new CommerceFixtures(null, 0, null, List.of(), List.of(), List.of(), List.of()));
		PhantomSystem system = null;
		Thread operation = null;
		try
		{
			PhantomAssertions.assertTrue(materialization.start(), "Shutdown proof materialization did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Shutdown proof actor did not materialize.");
			final Backend backend = profileId -> materialization.tryAcquireAction(profileId).map(action -> new FakeActor(OperationKind.BUY, before(OperationKind.BUY), action::close));
			final PhantomCommerceService service = new PhantomCommerceService(load, store, goals, backend);
			PhantomAssertions.assertTrue(service.start(), "Shutdown proof commerce did not start.");
			final AtomicReference<Throwable> failure = new AtomicReference<>();
			operation = new Thread(() ->
			{
				try
				{
					service.execute(profile.profileId(), 7, 0, intent(OperationKind.BUY), () -> false);
				}
				catch (Throwable throwable)
				{
					failure.set(throwable);
				}
			}, "goal014a-system-shutdown");
			operation.start();
			PhantomAssertions.assertTrue(store.entered.await(5, TimeUnit.SECONDS), "Shutdown proof did not reach the persistence seam.");
			system = new PhantomSystem(new org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.Settings(true, false, 1));
			PhantomAssertions.assertTrue(system.start(), "Shutdown proof PhantomSystem did not start.");
			setField(system, "_commerceService", service);
			setField(system, "_materializationService", materialization);
			PhantomAssertions.assertFalse(system.shutdown(), "PhantomSystem crossed an active commerce claim.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.RUNNING, materialization.snapshot().state(), "Materialization shutdown started while commerce owned a claim.");
			store.release.countDown();
			operation.join(5000);
			PhantomAssertions.assertFalse(operation.isAlive(), "Shutdown proof operation did not drain.");
			if (failure.get() != null)
			{
				throw new AssertionError("Shutdown proof operation failed.", failure.get());
			}
			PhantomAssertions.assertTrue(system.shutdown(), "PhantomSystem did not finish after commerce drained.");
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.snapshot().state(), "Materialization did not stop after commerce drained.");
			context.record("commerceHardening.shutdownBlocked", true);
		}
		finally
		{
			store.release.countDown();
			if ((operation != null) && operation.isAlive())
			{
				operation.join(5000);
			}
			if ((system != null) && (system.snapshot().state() != PhantomSystem.State.STOPPED))
			{
				system.shutdown();
			}
			if (materialization.snapshot().state() != PhantomMaterializationService.ServiceState.STOPPED)
			{
				materialization.shutdown();
			}
			deleteProfile(profile.profileId());
		}
	}

	private void testRealBackendIntegration(PhantomTestContext context) throws Exception
	{
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		final CanonicalBuy buy = selectCanonicalBuy();
		final TeleportRoute route = selectCanonicalTeleport();
		Merchant merchant = null;
		Teleporter teleporter = null;
		PhantomCommerceService service = null;
		Player player = null;
		long baselineAdena = -1;
		long baselineBuyItems = -1;
		long baselineFeeItems = -1;
		int baselineLevel = -1;
		int baselineInstanceId = 0;
		int baselineX = 0;
		int baselineY = 0;
		int baselineZ = 0;
		final AtomicReference<Throwable> backendFailure = new AtomicReference<>();
		final boolean allowRefund = GeneralConfig.ALLOW_REFUND;
		final boolean zeroSellPrice = MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE;
		try
		{
			PhantomAssertions.assertTrue(materialization.start(), "Commerce integration materialization did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Commerce integration Player did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Exact materialized commerce Player is absent from World.");
			if (player.isTeleporting())
			{
				player.onTeleported();
			}
			baselineAdena = player.getAdena();
			baselineBuyItems = player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1);
			baselineFeeItems = route.feeItemId() <= 0 ? 0 : player.getInventory().getInventoryItemCount(route.feeItemId(), -1);
			baselineLevel = player.getLevel();
			baselineInstanceId = player.getInstanceId();
			baselineX = player.getX();
			baselineY = player.getY();
			baselineZ = player.getZ();
			player.abortAttack();
			player.abortCast();
			player.getStat().setLevel((byte) 85);
			GeneralConfig.ALLOW_REFUND = false;
			MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE = false;

			final MerchantPriceConfigTable merchantPrices = MerchantPriceConfigTable.getInstance();
			merchantPrices.loadInstances();
			merchantPrices.updateReferences();
			merchant = new Merchant(NpcData.getInstance().getTemplate(buy.npcId()));
			merchant.setInstanceId(player.getInstanceId());
			merchant.spawnMe(player.getX() + 10, player.getY(), player.getZ());
			teleporter = new Teleporter(NpcData.getInstance().getTemplate(route.npcId()));
			teleporter.setInstanceId(player.getInstanceId());
			teleporter.spawnMe(player.getX() + 20, player.getY(), player.getZ());

			final long adenaFloor = Math.max(1_000_000L, Math.addExact(Math.multiplyExact(buy.offer().price(), 10L), route.feeItemId() == 57 ? Math.multiplyExact(route.feeCount(), 2L) : 0));
			if (player.getAdena() < adenaFloor)
			{
				player.addAdena(ItemProcessType.REWARD, adenaFloor - player.getAdena(), player, false);
			}
			if ((route.feeItemId() > 0) && (route.feeItemId() != 57))
			{
				final long feeFloor = Math.max(1, Math.multiplyExact(route.feeCount(), 2));
				final long currentFee = player.getInventory().getInventoryItemCount(route.feeItemId(), -1);
				if (currentFee < feeFloor)
				{
					PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, route.feeItemId(), feeFloor - currentFee, player, this) != null, "Could not fund the canonical teleport fee item.");
				}
			}
			player.storeMe();

			final PhantomGoalStateStore goalStore = new PhantomGoalStateStore(repository);
			final long buyGoalId = 1400140101L;
			final long sellGoalId = 1400140102L;
			final long teleportGoalId = 1400140103L;
			final long buyCount = 2;
			final PhantomDomainRef buySource = new PhantomDomainRef("commerce.buy", buy.npcId() + ":" + merchant.getObjectId() + ":" + buy.offer().listId() + ":" + buy.offer().itemId() + ":" + buyCount);
			StoredGoal currentGoal = goalStore.insert(profile.profileId(), goal(buyGoalId, 0, "acquire.item", buySource));
			final PhantomCommerceReceiptStore receiptStore = new PhantomCommerceReceiptStore(repository);
			final Clock clock = Clock.fixed(Instant.parse("2026-07-29T12:00:00Z"), ZoneOffset.UTC);
			final OperationIntent buyIntent = new OperationIntent(OperationKind.BUY, buy.npcId(), merchant.getObjectId(), buy.offer().listId(), buy.offer().itemId(), 0, buyCount, 0, "", adenaFloor);
			engage(player, merchant);
			service = new PhantomCommerceService(_production, receiptStore, goalStore, new CapturingBackend(new L2jCommerceBackend(materialization, _production.catalog(), clock), backendFailure));
			PhantomAssertions.assertTrue(service.start(), "Real commerce service did not start.");
			final long buyAdenaBefore = player.getAdena();
			final long buyItemsBefore = player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1);
			final var buyResult = service.execute(profile.profileId(), buyGoalId, 0, buyIntent, () -> false);
			if (buyResult.status() != OperationStatus.SUCCESS)
			{
				throw new AssertionError("Real Player unlimited buy failed: " + buyResult + " backendCause=" + backendFailure.get(), backendFailure.get());
			}
			final PhantomCommerceReceipt buyReceipt = receiptStore.find(profile.profileId()).orElseThrow().receipt();
			PhantomAssertions.assertEquals(buyReceipt.expectedAfter().primaryCount(), player.getAdena(), "Runtime buy adena does not match its receipt.");
			PhantomAssertions.assertEquals(buyReceipt.expectedAfter().secondaryCount(), player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1), "Runtime buy item count does not match its receipt.");
			PhantomAssertions.assertTrue(player.getAdena() < buyAdenaBefore, "Real buy did not consume adena.");
			PhantomAssertions.assertEquals(buyItemsBefore + buyCount, player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1), "Real buy did not add the exact item count.");
			player.storeMe();
			PhantomAssertions.assertTrue(service.beginStop() && service.finishStop(), "First real commerce service did not stop for reconstruction.");

			final PhantomProfileRepository reloadedRepository = PhantomProfileRepository.open();
			final PhantomGoalStateStore reloadedGoalStore = new PhantomGoalStateStore(reloadedRepository);
			final PhantomCommerceReceiptStore reloadedReceiptStore = new PhantomCommerceReceiptStore(reloadedRepository);
			service = new PhantomCommerceService(new PhantomCommerceCatalogLoader(Path.of(".")).load(), reloadedReceiptStore, reloadedGoalStore, new CapturingBackend(new L2jCommerceBackend(materialization, _production.catalog(), clock), backendFailure));
			PhantomAssertions.assertTrue(service.start(), "Reconstructed real commerce service did not start.");
			engage(player, merchant);
			final long idempotentAdena = player.getAdena();
			final long idempotentItems = player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1);
			PhantomAssertions.assertEquals(OperationStatus.IDEMPOTENT, service.execute(profile.profileId(), buyGoalId, 0, buyIntent, () -> false).status(), "Reconstructed same-key buy was not idempotent.");
			PhantomAssertions.assertEquals(idempotentAdena, player.getAdena(), "Idempotent buy changed runtime adena.");
			PhantomAssertions.assertEquals(idempotentItems, player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1), "Idempotent buy changed runtime inventory.");

			final Item sellItem = player.getInventory().getItemByItemId(buy.offer().itemId());
			PhantomAssertions.assertTrue(sellItem != null, "Bought item is absent before real sell.");
			final long sellCount = 1;
			final PhantomDomainRef sellSource = new PhantomDomainRef("commerce.sell", buy.npcId() + ":" + merchant.getObjectId() + ":" + buy.offer().listId() + ":" + buy.offer().itemId() + ":" + sellItem.getObjectId() + ":" + sellCount);
			currentGoal = reloadedGoalStore.replace(profile.profileId(), currentGoal.rowVersion(), goal(sellGoalId, 0, "sell.item", sellSource));
			final OperationIntent sellIntent = new OperationIntent(OperationKind.SELL, buy.npcId(), merchant.getObjectId(), buy.offer().listId(), buy.offer().itemId(), sellItem.getObjectId(), sellCount, 0, "", 0);
			engage(player, merchant);
			final long sellAdenaBefore = player.getAdena();
			final long sellItemsBefore = player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1);
			final var sellResult = service.execute(profile.profileId(), sellGoalId, 0, sellIntent, () -> false);
			PhantomAssertions.assertEquals(OperationStatus.SUCCESS, sellResult.status(), "Real Player sell failed: " + sellResult);
			final PhantomCommerceReceipt sellReceipt = reloadedReceiptStore.find(profile.profileId()).orElseThrow().receipt();
			PhantomAssertions.assertEquals(sellReceipt.expectedAfter().primaryCount(), player.getAdena(), "Runtime sell adena does not match its receipt.");
			PhantomAssertions.assertEquals(sellReceipt.expectedAfter().secondaryCount(), player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1), "Runtime sell item count does not match its receipt.");
			PhantomAssertions.assertTrue(player.getAdena() > sellAdenaBefore, "Real sell did not refund adena.");
			PhantomAssertions.assertEquals(sellItemsBefore - sellCount, player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1), "Real sell removed the wrong count.");

			final String encodedList = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(route.listName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			final PhantomDomainRef teleportSource = new PhantomDomainRef("commerce.teleport", route.npcId() + ":" + teleporter.getObjectId() + ":" + route.ordinal() + ":" + encodedList);
			currentGoal = reloadedGoalStore.replace(profile.profileId(), currentGoal.rowVersion(), goal(teleportGoalId, 0, "travel.teleport", teleportSource));
			final OperationIntent teleportIntent = new OperationIntent(OperationKind.TELEPORT, route.npcId(), teleporter.getObjectId(), 0, 0, 0, 0, route.ordinal(), route.listName(), Math.max(adenaFloor, route.feeCount()));
			engage(player, teleporter);
			var teleportResult = service.execute(profile.profileId(), teleportGoalId, 0, teleportIntent, () -> false);
			if (!((teleportResult.status() == OperationStatus.SUCCESS) || ((teleportResult.status() == OperationStatus.RETRY) && (teleportResult.reason() == PhantomCommerceService.Reason.TELEPORT_PENDING))))
			{
				final PhantomCommerceReceipt failedReceipt = reloadedReceiptStore.find(profile.profileId()).orElseThrow().receipt();
				throw new AssertionError("Real NORMAL teleport was rejected: " + teleportResult + " before=" + failedReceipt.before() + " expected=" + failedReceipt.expectedAfter() + " current=" + currentFacts(player, failedReceipt.request()));
			}
			final PhantomCommerceReceipt pendingTeleportReceipt = reloadedReceiptStore.find(profile.profileId()).orElseThrow().receipt();
			final ConservationFacts expectedTeleport = pendingTeleportReceipt.expectedAfter();
			final Player teleportedPlayer = player;
			await(() -> !teleportedPlayer.isTeleporting() && (teleportedPlayer.getX() == expectedTeleport.x()) && (teleportedPlayer.getY() == expectedTeleport.y()) && (teleportedPlayer.getZ() == expectedTeleport.z()), "Real NORMAL teleport did not reach its receipt target.");
			if (teleportResult.status() == OperationStatus.RETRY)
			{
				teleportResult = service.execute(profile.profileId(), teleportGoalId, 0, teleportIntent, () -> false);
			}
			PhantomAssertions.assertTrue((teleportResult.status() == OperationStatus.SUCCESS) || (teleportResult.status() == OperationStatus.IDEMPOTENT), "Real NORMAL teleport did not become terminal: " + teleportResult);
			final PhantomCommerceReceipt teleportReceipt = reloadedReceiptStore.find(profile.profileId()).orElseThrow().receipt();
			PhantomAssertions.assertEquals(State.COMMITTED, teleportReceipt.state(), "Real NORMAL teleport receipt is not committed.");
			PhantomAssertions.assertEquals(0, service.snapshot().currentOperations(), "Real integration leaked a commerce operation.");
			PhantomAssertions.assertEquals(0, service.snapshot().currentActorLeases(), "Real integration leaked an actor lease.");
			PhantomAssertions.assertEquals(0, service.snapshot().currentPersistenceClaims(), "Real integration leaked a persistence claim.");
			final long durableAdena = player.getAdena();
			final long durableItems = player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1);
			final int durableX = player.getX();
			final int durableY = player.getY();
			final int durableZ = player.getZ();
			player.storeMe();
			PhantomAssertions.assertTrue(service.beginStop() && service.finishStop(), "Real commerce service did not drain before reload.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.dematerialize(profile.profileId()).status(), "Commerce Player did not dematerialize.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Commerce Player did not rematerialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Rematerialized commerce Player is absent.");
			PhantomAssertions.assertEquals(durableAdena, player.getAdena(), "DB/reload adena conservation failed.");
			PhantomAssertions.assertEquals(durableItems, player.getInventory().getInventoryItemCount(buy.offer().itemId(), -1), "DB/reload item conservation failed.");
			PhantomAssertions.assertEquals(durableX, player.getX(), "DB/reload X position conservation failed.");
			PhantomAssertions.assertEquals(durableY, player.getY(), "DB/reload Y position conservation failed.");
			PhantomAssertions.assertEquals(durableZ, player.getZ(), "DB/reload Z position conservation failed.");
			PhantomAssertions.assertEquals(teleportGoalId, new PhantomGoalStateStore(PhantomProfileRepository.open()).load(profile.profileId()).orElseThrow().goal().goalId(), "Reloaded current goal authority changed.");
			PhantomAssertions.assertEquals(State.COMMITTED, new PhantomCommerceReceiptStore(PhantomProfileRepository.open()).find(profile.profileId()).orElseThrow().receipt().state(), "Reloaded terminal receipt changed.");
			context.record("commerceHardening.buy", buy.offer().listId() + ":" + buy.offer().itemId() + ":" + buy.npcId());
			context.record("commerceHardening.teleport", route.npcId() + ":" + route.listName() + ":" + route.ordinal());
			context.record("commerceHardening.receiptKey", teleportReceipt.operationKey());
		}
		finally
		{
			GeneralConfig.ALLOW_REFUND = allowRefund;
			MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE = zeroSellPrice;
			if (player != null)
			{
				player.setTarget(null);
				player.setLastFolkNPC(null);
			}
			if ((merchant != null) && merchant.isSpawned())
			{
				merchant.deleteMe();
			}
			if ((teleporter != null) && teleporter.isSpawned())
			{
				teleporter.deleteMe();
			}
			if ((service != null) && (service.snapshot().state() == PhantomCommerceService.StateSnapshot.RUNNING))
			{
				service.beginStop();
			}
			if ((service != null) && (service.snapshot().state() == PhantomCommerceService.StateSnapshot.STOPPING))
			{
				PhantomAssertions.assertTrue(service.finishStop(), "Real commerce cleanup retained ownership.");
			}
			if ((player != null) && (baselineAdena >= 0))
			{
				restoreItemCount(player, buy.offer().itemId(), baselineBuyItems);
				if ((route.feeItemId() > 0) && (route.feeItemId() != 57) && (route.feeItemId() != buy.offer().itemId()))
				{
					restoreItemCount(player, route.feeItemId(), baselineFeeItems);
				}
				if (player.getAdena() < baselineAdena)
				{
					player.addAdena(ItemProcessType.REWARD, baselineAdena - player.getAdena(), player, false);
				}
				else if (player.getAdena() > baselineAdena)
				{
					PhantomAssertions.assertTrue(player.reduceAdena(ItemProcessType.DESTROY, player.getAdena() - baselineAdena, player, false), "Could not restore fixture adena baseline.");
				}
				player.getStat().setLevel((byte) baselineLevel);
				player.setInstanceId(baselineInstanceId);
				player.setXYZ(baselineX, baselineY, baselineZ);
				player.storeMe();
			}
			if (materialization.snapshot().state() != PhantomMaterializationService.ServiceState.STOPPED)
			{
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.shutdown().state(), "Commerce integration materialization did not stop.");
			}
			deleteProfile(profile.profileId());
			if (player != null)
			{
				_environment.assertClean(_environment.primary(), player);
			}
		}
	}

	private static void restoreItemCount(Player player, int itemId, long expected)
	{
		final long current = player.getInventory().getInventoryItemCount(itemId, -1);
		if (current < expected)
		{
			PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, itemId, expected - current, player, PhantomCommerceSuite.class) != null, "Could not restore fixture item baseline.");
		}
		else if (current > expected)
		{
			final Item item = player.getInventory().getItemByItemId(itemId);
			PhantomAssertions.assertTrue((item != null) && (player.getInventory().destroyItem(ItemProcessType.DESTROY, item, current - expected, player, PhantomCommerceSuite.class) != null), "Could not restore fixture item baseline.");
		}
	}

	private static ConservationFacts currentFacts(Player player, OperationRequest request)
	{
		final long primary = request.kind() == OperationKind.TELEPORT ? player.getInventory().getInventoryItemCount(request.feeItemId(), -1) : player.getAdena();
		final long secondary = request.itemId() == 0 ? 0 : player.getInventory().getInventoryItemCount(request.itemId(), -1);
		final Item object = request.itemObjectId() == 0 ? null : player.getInventory().getItemByObjectId(request.itemObjectId());
		final long objectCount = request.kind() == OperationKind.BUY ? secondary : object == null ? 0 : object.getCount();
		return new ConservationFacts(primary, secondary, objectCount, player.getInstanceId(), player.getX(), player.getY(), player.getZ());
	}

	private CanonicalBuy selectCanonicalBuy()
	{
		final BuyOffer preferred = _production.catalog().findBuyOffer(382, 1463);
		if ((preferred != null) && !preferred.limitedStock() && preferred.npcIds().contains(31380))
		{
			return new CanonicalBuy(preferred, 31380);
		}
		for (BuyOffer offer : _production.catalog().buyOffers())
		{
			if (offer.limitedStock() || (BuyListData.getInstance().getBuyList(offer.listId()) == null))
			{
				continue;
			}
			for (int npcId : offer.npcIds())
			{
				final NpcTemplate template = NpcData.getInstance().getTemplate(npcId);
				if ((template != null) && "Merchant".equals(template.getType()))
				{
					return new CanonicalBuy(offer, npcId);
				}
			}
		}
		throw new AssertionError("No deterministic unlimited Merchant buy fixture is available.");
	}

	private TeleportRoute selectCanonicalTeleport()
	{
		for (TeleportRoute route : _production.catalog().teleportRoutes())
		{
			if ((route.npcId() == 30006) && (route.ordinal() == 0) && (route.type() == TeleportType.NORMAL))
			{
				return route;
			}
		}
		for (TeleportRoute route : _production.catalog().teleportRoutes())
		{
			final NpcTemplate template = NpcData.getInstance().getTemplate(route.npcId());
			if ((route.type() == TeleportType.NORMAL) && (template != null) && "Teleporter".equals(template.getType()))
			{
				return route;
			}
		}
		throw new AssertionError("No deterministic NORMAL Teleporter fixture is available.");
	}

	private static void engage(Player player, Npc npc)
	{
		player.setTarget(npc);
		player.setLastFolkNPC(npc);
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), message);
	}

	private static void setField(Object target, String name, Object value) throws Exception
	{
		final var field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
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
		return service(store, actor, new FakeGoalStore(1, goal(7, 0, "acquire.item", new PhantomDomainRef("commerce.buy", "1:2:3:4:5"))));
	}

	private static PhantomCommerceService service(FakeStore store, FakeActor actor, PhantomGoalStore goalStore)
	{
		final PhantomCommerceCatalog catalog = new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of());
		final LoadResult load = new LoadResult(catalog, new CommerceFixtures(null, 0, null, List.of(), List.of(), List.of(), List.of()));
		return new PhantomCommerceService(load, store, goalStore, profileId -> profileId == 1 ? Optional.of(actor) : Optional.empty());
	}

	private static PhantomCommerceReceipt receipt(OperationKind kind, ConservationFacts before, ConservationFacts after)
	{
		return receipt(1, kind, before, after);
	}

	private static PhantomCommerceReceipt receipt(long profileId, OperationKind kind, ConservationFacts before, ConservationFacts after)
	{
		return PhantomCommerceReceipt.prepared(profileId, 7, 0, request(kind), before, after);
	}

	private static PhantomCommerceReceipt terminalReceipt(long profileId, long goalId, long revision, State state)
	{
		PhantomCommerceReceipt receipt = PhantomCommerceReceipt.prepared(profileId, goalId, revision, request(OperationKind.BUY), before(OperationKind.BUY), after(OperationKind.BUY));
		return switch (state)
		{
			case PREPARED -> receipt;
			case COMMITTING -> receipt.withState(State.COMMITTING);
			case COMMITTED -> receipt.withState(State.COMMITTING).withState(State.COMMITTED);
			case ABORTED -> receipt.withState(State.ABORTED);
			case INCONSISTENT -> receipt.withState(State.INCONSISTENT);
		};
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
		return goal(7, 0, type, source);
	}

	private static PhantomGoal goal(long goalId, long revision, String type, PhantomDomainRef source)
	{
		return new PhantomGoal(goalId, type, PhantomGoalStatus.ACTIVE, null, null, 5, 0, null, List.of(source), null, "commerce.test", 500, 0, 100, 0, Map.of(), "commerce.test", revision);
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

	private static class FakeStore implements ReceiptPersistence
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

	private static final class BlockingStore extends FakeStore
	{
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicInteger saves = new AtomicInteger();

		@Override
		public VersionedReceipt save(long expectedRowVersion, PhantomCommerceReceipt receipt)
		{
			if (saves.getAndIncrement() == 0)
			{
				entered.countDown();
				try
				{
					release.await();
				}
				catch (InterruptedException e)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Controlled commerce persistence wait was interrupted.", e);
				}
			}
			return super.save(expectedRowVersion, receipt);
		}
	}

	private static final class FakeGoalStore implements PhantomGoalStore
	{
		private final long profileId;
		private final PhantomGoal goalOnSecondLoad;
		private StoredGoal value;
		private int loads;

		private FakeGoalStore(long profileId, PhantomGoal goal)
		{
			this(profileId, goal, null);
		}

		private FakeGoalStore(long profileId, PhantomGoal goal, PhantomGoal goalOnSecondLoad)
		{
			this.profileId = profileId;
			this.goalOnSecondLoad = goalOnSecondLoad;
			value = new StoredGoal(goal, 0);
		}

		@Override
		public boolean profileExists(long requestedProfileId)
		{
			return requestedProfileId == profileId;
		}

		@Override
		public Optional<StoredGoal> load(long requestedProfileId)
		{
			if (requestedProfileId != profileId)
			{
				return Optional.empty();
			}
			loads++;
			if ((loads == 2) && (goalOnSecondLoad != null))
			{
				value = new StoredGoal(goalOnSecondLoad, value.rowVersion() + 1);
			}
			return Optional.ofNullable(value);
		}

		@Override
		public StoredGoal insert(long requestedProfileId, PhantomGoal goal)
		{
			if ((requestedProfileId != profileId) || (value != null))
			{
				throw new java.util.ConcurrentModificationException();
			}
			value = new StoredGoal(goal, 0);
			return value;
		}

		@Override
		public StoredGoal replace(long requestedProfileId, long expectedRowVersion, PhantomGoal goal)
		{
			if ((requestedProfileId != profileId) || (value == null) || (value.rowVersion() != expectedRowVersion))
			{
				throw new java.util.ConcurrentModificationException();
			}
			value = new StoredGoal(goal, value.rowVersion() + 1);
			return value;
		}

		@Override
		public void delete(long requestedProfileId, long expectedRowVersion)
		{
			if ((requestedProfileId != profileId) || (value == null) || (value.rowVersion() != expectedRowVersion))
			{
				throw new java.util.ConcurrentModificationException();
			}
			value = null;
		}
	}

	private static final class CapturingBackend implements Backend
	{
		private final Backend delegate;
		private final AtomicReference<Throwable> failure;

		private CapturingBackend(Backend delegate, AtomicReference<Throwable> failure)
		{
			this.delegate = delegate;
			this.failure = failure;
		}

		@Override
		public Optional<ActorLease> tryAcquire(long profileId)
		{
			return capture(failure, () -> delegate.tryAcquire(profileId)).map(actor -> new CapturingActor(actor, failure));
		}
	}

	private static final class CapturingActor implements ActorLease
	{
		private final ActorLease delegate;
		private final AtomicReference<Throwable> failure;

		private CapturingActor(ActorLease delegate, AtomicReference<Throwable> failure)
		{
			this.delegate = delegate;
			this.failure = failure;
		}

		@Override
		public Quote quote(OperationIntent intent)
		{
			return capture(failure, () -> delegate.quote(intent));
		}

		@Override
		public ConservationFacts snapshot(OperationRequest request)
		{
			return capture(failure, () -> delegate.snapshot(request));
		}

		@Override
		public boolean applyFirst(OperationRequest request)
		{
			return capture(failure, () -> delegate.applyFirst(request));
		}

		@Override
		public boolean applySecond(OperationRequest request)
		{
			return capture(failure, () -> delegate.applySecond(request));
		}

		@Override
		public void close()
		{
			capture(failure, () ->
			{
				delegate.close();
				return null;
			});
		}
	}

	private static <T> T capture(AtomicReference<Throwable> failure, java.util.function.Supplier<T> action)
	{
		try
		{
			return action.get();
		}
		catch (RuntimeException | Error throwable)
		{
			failure.compareAndSet(null, throwable);
			throw throwable;
		}
	}

	private static final class FakeActor implements ActorLease
	{
		private final OperationKind kind;
		private final Runnable closeAction;
		private final AtomicInteger firstCalls = new AtomicInteger();
		private final AtomicInteger secondCalls = new AtomicInteger();
		private ConservationFacts current;

		private FakeActor(OperationKind kind, ConservationFacts current)
		{
			this(kind, current, () ->
			{
			});
		}

		private FakeActor(OperationKind kind, ConservationFacts current, Runnable closeAction)
		{
			this.kind = kind;
			this.current = current;
			this.closeAction = closeAction;
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
			closeAction.run();
		}
	}

	private record CanonicalBuy(BuyOffer offer, int npcId)
	{
	}
}

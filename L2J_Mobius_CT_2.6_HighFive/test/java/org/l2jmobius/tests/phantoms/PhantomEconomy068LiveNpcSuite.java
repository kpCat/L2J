/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.MerchantPriceConfigTable;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Policy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue.Rates;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Own068 regression for a changing supplier and stale native instances; no inventory/fund fixtures. */
public final class PhantomEconomy068LiveNpcSuite implements PhantomTestSuite
{
	private PhantomGameKnowledgeSnapshot _knowledge;
	private PhantomCommerceCatalog _commerce;
	@Override public String id() { return "economy068-live-npc"; }
	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		if (!PhantomTask068Lane.enabled()) { throw new IllegalArgumentException("Exact own068 native lane required."); }
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(context);
		MapRegionData.getInstance(); MerchantPriceConfigTable.getInstance().loadInstances(); MerchantPriceConfigTable.getInstance().updateReferences();
		final var validation = new L2jTopologyValidationBackend();
		final var topology = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), validation, PhantomTopologyPolicy.productionDefaults()).load(1);
		final var policy = PhantomGameKnowledgePolicy.productionDefaults(); final var backend = new L2jGameKnowledgeBackend();
		_knowledge = new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, policy), new PhantomTopologyQuery(topology, validation, new PhantomTopologyMetrics()), policy).build();
		_commerce = new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
	}
	@Override public void afterAll(PhantomTestContext context) { ThreadPool.shutdown(); DatabaseFactory.close(); }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-supplier-arrival-despawn-and-world-identity", context ->
		{
			// Stock Stem has paired spoil value and an ordinary Mammon buylist. No synthetic price or tax.
			final int itemId = 1864;
			final var offer = _commerce.buyOffers().stream().filter(row -> row.itemId() == itemId && !row.npcIds().isEmpty() && row.price() > 0).findFirst().orElseThrow();
			final var merchant = new Merchant(NpcData.getInstance().getTemplate(offer.npcIds().stream().sorted().findFirst().orElseThrow()));
			final var objects = new AtomicReference<Collection<WorldObject>>(List.of());
			final var authority = new PhantomPrivateMarketPricingAuthority(_knowledge, _commerce, Rates.capture(), new Policy(500, 500), objects::get);
			try
			{
				PhantomAssertions.assertFalse(authority.quoteItem(itemId).bidAllowed(), "No visible ordinary merchant must not permit a guessed NPC ceiling.");
				objects.set(List.of(merchant));
				PhantomAssertions.assertFalse(authority.quoteItem(itemId).bidAllowed(), "An unspawned merchant supplied as visible was accepted.");
				merchant.spawnMe(81700, 148500, -3470);
				PhantomAssertions.assertTrue(merchant.getMpc() != null && authority.quoteItem(itemId).bidAllowed(), "A new real Merchant was absent from the existing authority.");
				World.getInstance().removeObject(merchant);
				PhantomAssertions.assertFalse(authority.quoteItem(itemId).bidAllowed(), "Spawned flag without the current World instance was accepted.");
				World.getInstance().addObject(merchant);
				merchant.setSpawned(false);
				PhantomAssertions.assertFalse(authority.quoteItem(itemId).bidAllowed(), "World identity without a spawned flag was accepted.");
				merchant.setSpawned(true);
				PhantomAssertions.assertTrue(authority.quoteItem(itemId).bidAllowed(), "Current restored native instance was not accepted.");
				objects.set(List.of());
				PhantomAssertions.assertFalse(authority.quoteItem(itemId).bidAllowed(), "Authority captured the supplier's previous collection.");
				context.record("economy068.liveNpc", "same authority: absent/unspawned/current/wrongWorld/despawned/current/removed; no item or Adena mutation");
			}
			finally { merchant.setSpawned(true); World.getInstance().addObject(merchant); merchant.deleteMe(); }
		});
	}
}

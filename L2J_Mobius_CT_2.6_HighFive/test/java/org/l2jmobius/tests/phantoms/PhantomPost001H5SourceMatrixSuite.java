package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.data.MerchantPriceConfigTable;
import org.l2jmobius.gameserver.data.xml.BuyListData;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStoreService;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.economy.PhantomNpcAcquisitionPrice;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRecipeExpectedCost;
import org.l2jmobius.gameserver.phantoms.economy.PhantomCanonicalEnchantRoutes;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantExpectedCost;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Inputs;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Policy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue.Rates;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemCategory;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeSnapshot;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Read-only representative evidence from the accepted stock H5 knowledge generation. */
public final class PhantomPost001H5SourceMatrixSuite implements PhantomTestSuite
{
	private PhantomGameKnowledgeSnapshot _knowledge;
	private Rates _stock;
	private Set<Integer> _eligibleNpcIds;
	private final Set<Integer> _enchantInputs = new HashSet<>();
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomCommerceCatalog _stockCommerce;

	@Override
	public String id()
	{
		return "post001-h5-source-matrix";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		MapRegionData.getInstance();
		SpawnData.getInstance();
		DoorData.getInstance();
		final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
		final var topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
		final PhantomTopologyQuery topology = new PhantomTopologyQuery(topologySnapshot, topologyBackend, new PhantomTopologyMetrics());
		final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
		final L2jGameKnowledgeBackend backend = new L2jGameKnowledgeBackend();
		_knowledge = new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, policy), topology, policy).build();
		_stock = Rates.capture();
		_stockCommerce = new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog();
		_eligibleNpcIds = PhantomRateAwareLootFairValue.eligibleNpcIds(_knowledge);
		EnchantItemData.getInstance().getScrolls().forEach(scroll -> _enchantInputs.add(scroll.getId()));
		_knowledge.itemById().keySet().forEach(id ->
		{
			if (EnchantItemData.getInstance().getSupportItemById(id) != null)
			{
				_enchantInputs.add(id);
			}
		});
		context.record("post001.knowledgeHash", _knowledge.hashes().combinedHash());
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
		registry.add("production-store-executor-rejects-raw-price-and-opens-quoted-price", context ->
		{
			final var profiles = PhantomProfileRepository.open();
			final var profile = profiles.create(_environment.primary().objectId());
			final var metrics = new PhantomMetrics();
			final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			PhantomStoreService stores = null;
			Player owner = null;
			long funded = 0;
			try
			{
				if (!materialization.start() || (materialization.materialize(profile.profileId()).status() != org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus.SUCCESS))
				{
					throw new AssertionError("Guarded production store owner did not materialize.");
				}
				owner = World.getInstance().getPlayer(_environment.primary().objectId());
				if (owner == null)
				{
					throw new AssertionError("Materialized POST-001 owner absent from World.");
				}
				final var authority = new PhantomPrivateMarketPricingAuthority(_knowledge, new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of()), _stock, new Policy(500, 500), World.getInstance().getVisibleObjects());
				stores = new PhantomStoreService(profiles, materialization, () -> authority);
				final long now = System.currentTimeMillis();
				final var raw = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "POST-001 visible BUY", List.of(new PhantomStorePlan.Line(1865, 1865, 1, 1)), now + 60000);
				if ((stores.open(profile.profileId(), PhantomActivityState.ACTIVE, raw, now) != PhantomStoreService.Result.REJECTED) || profiles.findComponent(profile.profileId(), PhantomStorePlan.COMPONENT_TYPE).isPresent())
				{
					throw new AssertionError("Raw arbitrary Phantom price was installed or durably saved.");
				}
				final var priced = stores.quotePlan(profile.profileId(), PhantomActivityState.ACTIVE, raw, now).orElseThrow();
				funded = Math.max(0, priced.lines().get(0).price() - owner.getAdena());
				if (funded > 0)
				{
					owner.addAdena(ItemProcessType.REWARD, funded, owner, false);
					if (owner.getAdena() < priced.lines().get(0).price())
					{
						throw new AssertionError("Test-only fixture Adena could not cover native BUY conservation.");
					}
				}
				if ((stores.open(profile.profileId(), PhantomActivityState.ACTIVE, priced, now + 1) != PhantomStoreService.Result.OPENED) || (owner.getBuyList().getItems().iterator().next().getPrice() != priced.lines().get(0).price()))
				{
					throw new AssertionError("Quoted Phantom BUY did not reach exact native TradeList price.");
				}
				context.record("post001.executor", "rawRejected=true:quotedBid=" + priced.lines().get(0).price() + ":nativeExact=true:stockAdenaGuard=true");
			}
			finally
			{
				if (stores != null)
				{
					stores.close(profile.profileId());
				}
				if ((owner != null) && (funded > 0))
				{
					owner.reduceAdena(ItemProcessType.DESTROY, funded, owner, false);
				}
				materialization.shutdown();
				profiles.find(profile.profileId()).ifPresent(current -> profiles.delete(current.profileId(), current.rowVersion()));
			}
		});
		registry.add("stock-taxed-ordinary-npc-purchase-boundary", context ->
		{
			final var offer = _stockCommerce.findBuyOffer(382, 1463);
			if ((offer == null) || offer.npcIds().isEmpty())
			{
				throw new AssertionError("Real H5 Merchant buylist 382/1463 is unavailable.");
			}
			final int npcId = offer.npcIds().stream().sorted().findFirst().orElseThrow();
			final var npcTemplate = NpcData.getInstance().getTemplate(npcId);
			if (npcTemplate == null)
			{
				throw new AssertionError("Real Merchant NPC template is unavailable.");
			}
			Merchant merchant = null;
			try
			{
				MerchantPriceConfigTable.getInstance().loadInstances();
				MerchantPriceConfigTable.getInstance().updateReferences();
				merchant = new Merchant(npcTemplate);
				merchant.spawnMe(0, 0, 0);
				if (merchant.getMpc() == null)
				{
					throw new AssertionError("Spawned stock Merchant has no canonical price/tax authority.");
				}
				final var buyList = BuyListData.getInstance().getBuyList(offer.listId());
				final var product = buyList.getProductByItemId(offer.itemId());
				final long expected = (long) (product.getPrice() * (1 + merchant.getMpc().getCastleTaxRate() + merchant.getMpc().getBaseTaxRate()));
				final var actual = PhantomNpcAcquisitionPrice.quote(offer.itemId(), _stockCommerce, List.of(merchant));
				if (!actual.exists() || !actual.exact() || (actual.price() != expected))
				{
					throw new AssertionError("Phantom NPC P boundary differs from actual taxed stock Merchant purchase: " + actual + "/" + expected);
				}
				final long liquidation = PhantomPrivateMarketQuote.npcLiquidation(ItemData.getInstance().getTemplate(offer.itemId()));
				final var corridor = PhantomPrivateMarketQuote.quote(new Inputs(Math.max(expected, liquidation), liquidation, true, expected, 0, 0, org.l2jmobius.gameserver.config.PlayerConfig.MAX_ADENA), new Policy(500, 500));
				if (corridor.buyAllowed() && ((corridor.bid() >= expected) || (corridor.bid() < liquidation) || (corridor.ask() <= liquidation)))
				{
					throw new AssertionError("Actual H5 taxed NPC price permits NPC/Phantom arbitrage.");
				}
				context.record("post001.npc.1463", "npc=" + npcId + ":buyList=382:taxedP=" + actual.price() + ":L=" + liquidation + ":bid=" + corridor.bid() + ":ask=" + corridor.ask());
			}
			finally
			{
				if (merchant != null)
				{
					merchant.deleteMe();
				}
			}
		});
		registry.add("accepted-snapshot-plan-price-producer-conserves-items-and-adena", context ->
		{
			final Player owner = Player.load(_environment.primary().objectId());
			if (owner == null)
			{
				throw new AssertionError("Guarded headless owner could not be loaded.");
			}
			try
			{
				final long adenaBefore = owner.getAdena();
				final long fixtureBefore = owner.getInventory().getInventoryItemCount(57, -1);
				final var authority = new PhantomPrivateMarketPricingAuthority(_knowledge, new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of()), _stock, new Policy(500, 500), List.of());
				final long now = System.currentTimeMillis();
				final var buy = new PhantomStorePlan(PhantomStorePlan.Type.BUY, PhantomStorePlan.State.REQUESTED, "POST-001 BUY", List.of(new PhantomStorePlan.Line(1865, 1865, 1, 1)), now + 60000);
				final var pricedBuy = authority.pricePlan(owner, buy);
				if ((pricedBuy.lines().get(0).price() <= 1) || !pricedBuy.contentHash().equals(authority.pricePlan(owner, pricedBuy).contentHash()))
				{
					throw new AssertionError("Accepted H5 BUY plan quote was not source-backed/deterministic.");
				}
				final var manufacture = new PhantomStorePlan(PhantomStorePlan.Type.MANUFACTURE, PhantomStorePlan.State.REQUESTED, "POST-001 craft", List.of(new PhantomStorePlan.Line(178, 70, 1, 10)), now + 60000);
				if (authority.pricePlan(owner, manufacture).lines().get(0).price() != 0)
				{
					throw new AssertionError("Stock self-craft has no unavoidable Adena service fee.");
				}
				try
				{
					final var stolen = new PhantomStorePlan(PhantomStorePlan.Type.SELL, PhantomStorePlan.State.REQUESTED, "POST-001 SELL", List.of(new PhantomStorePlan.Line(123456, 70, 1, 1)), now + 60000);
					authority.pricePlan(owner, stolen);
					throw new AssertionError("Unowned item was priced for SELL.");
				}
				catch (IllegalArgumentException expected)
				{
				}
				if ((owner.getAdena() != adenaBefore) || (owner.getInventory().getInventoryItemCount(57, -1) != fixtureBefore))
				{
					throw new AssertionError("Market quotation minted or consumed Adena/items.");
				}
				context.record("post001.authority.plan", "buy1865=" + pricedBuy.lines().get(0).price() + ":manufacture178=0:inventoryConserved=true:adenaConserved=true");
			}
			finally
			{
				_environment.cleanupLoadedPlayer(owner);
			}
		});
		registry.add("real-scroll-acquisition-anchors", context ->
		{
			int anchored = 0;
			for (int scrollId : List.of(955, 956, 951, 952, 947, 948, 729, 730, 959, 960))
			{
				if (EnchantItemData.getInstance().getScrolls().stream().noneMatch(scroll -> scroll.getId() == scrollId))
				{
					throw new AssertionError("Representative scroll missing from stock H5: " + scrollId);
				}
				try
				{
					final var priced = PhantomRateAwareLootFairValue.quote(scrollId, _knowledge, _stock, _eligibleNpcIds);
					context.record("post001.scroll." + scrollId, priced.npcId() + ":" + priced.source() + ":" + priced.fairValue());
					anchored++;
				}
				catch (IllegalArgumentException unsafe)
				{
					context.record("post001.scroll." + scrollId, "UNSAFE:no paired eligible Adena/drop source");
				}
			}
			if (anchored < 4)
			{
				throw new AssertionError("Too few source-backed scrolls for H5 enchanted gear evidence: " + anchored);
			}
			if (safeSource(6575, _stock) != 0)
			{
				throw new AssertionError("Raid-only blessed scroll cannot inherit a reference/liquidation scarcity price.");
			}
			context.record("post001.scroll.6575", "UNSAFE:raid-only/no ordinary source; excluded from real gear routes");
		});
		registry.add("real-material-relative-rate-matrix", context ->
		{
			final List<Integer> materials = new ArrayList<>();
			for (int itemId : List.of(1865, 1867, 1871, 1872, 1873, 17))
			{
				final var fact = _knowledge.itemById().get(itemId);
				final var template = ItemData.getInstance().getTemplate(itemId);
				if ((fact == null) || (fact.category() != ItemCategory.ETC) || (template == null) || !template.isTradeable() || template.isQuestItem() || _enchantInputs.contains(itemId))
				{
					continue;
				}
				try
				{
					PhantomRateAwareLootFairValue.quote(itemId, _knowledge, _stock, _eligibleNpcIds);
					materials.add(itemId);
				}
				catch (IllegalArgumentException unsafe)
				{
				}
				if (materials.size() == 2)
				{
					break;
				}
			}
			if (materials.size() < 2)
			{
				for (var item : _knowledge.itemById().values().stream().filter(item -> item.category() == ItemCategory.ETC).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact::itemId)).toList())
				{
					final int id = item.itemId();
					final var template = ItemData.getInstance().getTemplate(id);
					if ((template == null) || !template.isTradeable() || template.isQuestItem() || _enchantInputs.contains(id) || (id == 57))
					{
						continue;
					}
					try
					{
						PhantomRateAwareLootFairValue.quote(id, _knowledge, _stock, _eligibleNpcIds);
						materials.add(id);
					}
					catch (IllegalArgumentException unsafe)
					{
					}
					if (materials.size() == 2)
					{
						break;
					}
				}
			}
			if (materials.size() < 2)
			{
				throw new AssertionError("No two ordinary source-backed H5 materials.");
			}
			for (int itemId : materials)
			{
				final double x1 = price(itemId, _stock);
				final double supply5 = price(itemId, supply(5));
				final double adena5 = price(itemId, adena(5));
				final double both5 = price(itemId, both(5));
				context.record("post001.material." + itemId, x1 + "," + supply5 + "," + adena5 + "," + both5);
				if (!Double.isFinite(x1) || !Double.isFinite(supply5) || !Double.isFinite(adena5) || !Double.isFinite(both5) || (x1 <= 0) || (supply5 > x1) || (adena5 < x1))
				{
					throw new AssertionError("Real H5 source ratio moved opposite to supply/Adena rate intent for item " + itemId);
				}
			}
		});
		registry.add("real-craftable-gear-and-enchant", context ->
		{
			int weapon = 0;
			int armor = 0;
			int eligible = 0;
			int craftable = 0;
			int routed = 0;
			int rejected = 0;
			for (var item : _knowledge.itemById().values().stream().filter(item -> (item.category() == ItemCategory.WEAPON) || (item.category() == ItemCategory.ARMOR)).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact::itemId)).toList())
			{
				if (((item.category() == ItemCategory.WEAPON) && (weapon > 0)) || ((item.category() == ItemCategory.ARMOR) && (armor > 0)))
				{
					continue;
				}
				final var template = ItemData.getInstance().getTemplate(item.itemId());
				if ((template == null) || !template.isTradeable() || !template.isEnchantable() || !_knowledge.recipesByProduct().containsKey(item.itemId()))
				{
					continue;
				}
				eligible++;
				try
				{
					final var craft = PhantomRecipeExpectedCost.floor(item.itemId(), _knowledge, id -> safeSource(id, _stock), id -> 0, org.l2jmobius.gameserver.config.PlayerConfig.CRAFT_MASTERWORK_CHANCE_RATE);
					craftable++;
					final var steps = PhantomCanonicalEnchantRoutes.steps(template, 16, _enchantInputs, id -> safeSource(id, _stock));
					routed++;
					final double base = Math.max(craft.perItemCost(), safeSource(item.itemId(), _stock));
					if ((base <= 0) || !Double.isFinite(base))
					{
						continue;
					}
					final var plus16 = PhantomEnchantExpectedCost.quote(base, steps);
					context.record("post001.gear." + item.itemId() + ".recipe", craft.recipeListId() + ":inputs=" + craft.inputsPerAttempt() + ":yield=" + craft.ordinaryYieldPerAttempt() + ":floor=" + craft.perItemCost());
					context.record("post001.gear." + item.itemId() + ".bom", _knowledge.recipesByProduct().get(item.itemId()).stream().filter(recipe -> recipe.recipeListId() == craft.recipeListId()).findFirst().orElseThrow().ingredients().toString());
					final int safeLevel = item.category() == ItemCategory.WEAPON ? 3 : template.getBodyPart() == org.l2jmobius.gameserver.model.item.enums.BodyPart.FULL_ARMOR ? 4 : 3;
					for (int level : List.of(safeLevel, 4, 6, 10, 16).stream().distinct().toList())
					{
						final var quote = level == 16 ? plus16 : PhantomEnchantExpectedCost.quote(base, steps.subList(0, level));
						final var risks = risk(base, quote.chosenRoutes(), 10000, 1005L + (31L * item.itemId()) + level);
						context.record("post001.gear." + item.itemId() + ".plus" + level, "fair=" + quote.fairValue() + ":enchantCost=" + (quote.fairValue() - base) + ":bases=" + quote.baseItemsConsumed() + ":destroyed=" + quote.itemsDestroyed() + ":resets=" + quote.resetsToZero() + ":scrolls=" + quote.scrollCounts() + ":supports=" + quote.supportCounts() + ":steps=" + quote.chosenRoutes().stream().map(route -> route.scrollId() + "/" + route.supportId() + "/" + route.chance() + "/" + route.failure()).distinct().toList() + ":riskP50=" + risks[0] + ":riskP90=" + risks[1]);
						if ((quote.fairValue() < craft.perItemCost()) || (risks[1] < risks[0]))
						{
							throw new AssertionError("Expected enchanted gear floor or risk ordering violated.");
						}
					}
					final var stock10 = PhantomEnchantExpectedCost.quote(base, steps.subList(0, 10));
					final var fixture10 = PhantomEnchantExpectedCost.quote(base, chanceFixture(steps.subList(0, 10), safeLevel, 0.75, false));
					final var extendedSafe10 = PhantomEnchantExpectedCost.quote(base, chanceFixture(steps.subList(0, 10), safeLevel, 1, true));
					if ((fixture10.fairValue() <= stock10.fairValue()) || (extendedSafe10.fairValue() > stock10.fairValue()))
					{
						throw new AssertionError("Test-only canonical chance/safe boundary did not move +10 as expected.");
					}
					context.record("post001.gear." + item.itemId() + ".chanceFixture", "stock10=" + stock10.fairValue() + ":riskier75pct=" + fixture10.fairValue() + ":extendedSafe=" + extendedSafe10.fairValue());
					context.record("post001.gear." + item.itemId() + ".market", market(item.itemId(), _stock));
					if (item.category() == ItemCategory.WEAPON)
					{
						weapon = item.itemId();
					}
					else
					{
						armor = item.itemId();
					}
				}
				catch (IllegalArgumentException unsafe)
				{
					if (rejected++ < 8)
					{
						context.record("post001.gear.rejected." + item.itemId(), unsafe.getMessage());
						context.record("post001.gear.ingredients." + item.itemId(), _knowledge.recipesByProduct().get(item.itemId()).stream().map(recipe -> recipe.recipeListId() + ":" + recipe.ingredients().stream().map(ingredient -> ingredient.itemId() + "=" + safeSource(ingredient.itemId(), _stock)).toList()).toList().toString());
					}
				}
				if ((weapon > 0) && (armor > 0))
				{
					break;
				}
			}
			context.record("post001.gear.selection", "eligible=" + eligible + ":craftable=" + craftable + ":routed=" + routed + ":rejected=" + rejected);
			if ((weapon == 0) || (armor == 0))
			{
				throw new AssertionError("Missing source-backed craftable enchanted H5 weapon/armor: " + weapon + "/" + armor);
			}
			int intermediate = 0;
			int spoilResource = 0;
			int control = 0;
			for (var item : _knowledge.itemById().values().stream().filter(item -> item.category() == ItemCategory.ETC).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ItemFact::itemId)).toList())
			{
				final int id = item.itemId();
				final var template = ItemData.getInstance().getTemplate(id);
				if ((template == null) || !template.isTradeable() || template.isQuestItem() || _enchantInputs.contains(id) || (id == 57))
				{
					continue;
				}
				if ((control == 0) && !_knowledge.recipesByProduct().containsKey(id) && (safeSource(id, _stock) > 0))
				{
					control = id;
				}
				if ((intermediate == 0) && _knowledge.recipesByProduct().containsKey(id))
				{
					try
					{
						PhantomRecipeExpectedCost.floor(id, _knowledge, input -> safeSource(input, _stock), fee -> 0, org.l2jmobius.gameserver.config.PlayerConfig.CRAFT_MASTERWORK_CHANCE_RATE);
						intermediate = id;
					}
					catch (IllegalArgumentException unsafe)
					{
					}
				}
				if (spoilResource == 0)
				{
					try
					{
						if (PhantomRateAwareLootFairValue.quote(id, _knowledge, _stock, _eligibleNpcIds).source() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind.SPOIL)
						{
							spoilResource = id;
						}
					}
					catch (IllegalArgumentException unsafe)
					{
					}
				}
				if ((intermediate > 0) && (spoilResource > 0) && (control > 0))
				{
					break;
				}
			}
			if ((intermediate == 0) || (spoilResource == 0) || (control == 0))
			{
				throw new AssertionError("Missing real H5 intermediate/spoil/control matrix representative.");
			}
			for (int id : List.of(1865, 1867, spoilResource, intermediate, weapon, armor, control).stream().distinct().toList())
			{
				context.record("post001.rateMatrix." + id, "x1=" + market(id, _stock) + ":supply5=" + market(id, supply(5)) + ":adena5=" + market(id, adena(5)) + ":both5=" + market(id, both(5)) + ":high10=" + market(id, both(10)));
			}
			context.record("post001.rateMatrix.roles", "raw=1865/1867:spoil=" + spoilResource + ":intermediate=" + intermediate + ":weapon=" + weapon + ":armor=" + armor + ":control=" + control);
		});
	}

	private double price(int itemId, Rates rates)
	{
		return PhantomRateAwareLootFairValue.quote(itemId, _knowledge, rates, _eligibleNpcIds).fairValue();
	}

	private double safeSource(int itemId, Rates rates)
	{
		final var template = ItemData.getInstance().getTemplate(itemId);
		final double opportunity = template == null ? 0 : PhantomPrivateMarketQuote.npcLiquidation(template);
		try
		{
			return Math.max(price(itemId, rates), opportunity);
		}
		catch (IllegalArgumentException unsafe)
		{
			return _enchantInputs.contains(itemId) ? 0 : opportunity;
		}
	}

	private String market(int itemId, Rates rates)
	{
		final var template = ItemData.getInstance().getTemplate(itemId);
		long floor = 0;
		try
		{
			floor = (long) Math.ceil(PhantomRecipeExpectedCost.floor(itemId, _knowledge, id -> safeSource(id, rates), id -> 0, org.l2jmobius.gameserver.config.PlayerConfig.CRAFT_MASTERWORK_CHANCE_RATE).perItemCost());
		}
		catch (IllegalArgumentException noRecipe)
		{
		}
		final long fair = (long) Math.ceil(Math.max(safeSource(itemId, rates), floor));
		final var market = PhantomPrivateMarketQuote.quote(new Inputs(fair, PhantomPrivateMarketQuote.npcLiquidation(template), false, 0, floor, 0, org.l2jmobius.gameserver.config.PlayerConfig.MAX_ADENA), new Policy(500, 500));
		return "fair=" + fair + ":craftFloor=" + floor + ":bid=" + market.bid() + ":ask=" + market.ask() + ":pass=" + (market.ask() >= floor);
	}

	private static double[] risk(double base, List<PhantomEnchantExpectedCost.Route> routes, int trials, long seed)
	{
		final Random random = new Random(seed);
		final double[] values = new double[trials];
		for (int trial = 0; trial < trials; trial++)
		{
			double spent = base;
			int level = 0;
			int attempts = 0;
			while (level < routes.size())
			{
				if (++attempts > 100000)
				{
					throw new IllegalArgumentException("Bounded enchant percentile trial did not finish.");
				}
				final var route = routes.get(level);
				spent += route.scrollValue() + route.supportValue();
				if (random.nextDouble() < route.chance())
				{
					level++;
				}
				else if (route.failure() == PhantomEnchantExpectedCost.Failure.DESTROY)
				{
					spent += base - route.crystalRecoveryValue();
					level = 0;
				}
				else if (route.failure() == PhantomEnchantExpectedCost.Failure.RESET_TO_ZERO)
				{
					level = 0;
				}
			}
			values[trial] = spent;
		}
		java.util.Arrays.sort(values);
		return new double[]
		{
			values[(int) Math.floor((trials - 1) * 0.5)], values[(int) Math.floor((trials - 1) * 0.9)]
		};
	}

	private static List<PhantomEnchantExpectedCost.Step> chanceFixture(List<PhantomEnchantExpectedCost.Step> stock, int safeLevel, double uncertainChanceFactor, boolean extendSafe)
	{
		return stock.stream().map(step -> new PhantomEnchantExpectedCost.Step(step.level(), step.routes().stream().map(route -> new PhantomEnchantExpectedCost.Route(route.scrollId(), route.supportId(), (extendSafe && (step.level() == safeLevel)) ? 1 : (step.level() >= safeLevel ? route.chance() * uncertainChanceFactor : route.chance()), route.scrollValue(), route.supportValue(), route.crystalRecoveryValue(), route.failure())).toList())).toList();
	}

	private Rates supply(double factor)
	{
		return new Rates(_stock.deathChance() * factor, _stock.deathAmount(), _stock.spoilChance() * factor, _stock.spoilAmount(), _stock.chanceByItem(), _stock.amountByItem());
	}

	private Rates adena(double factor)
	{
		final Map<Integer, Double> amounts = new java.util.HashMap<>(_stock.amountByItem());
		amounts.put(57, amounts.getOrDefault(57, _stock.deathAmount()) * factor);
		return new Rates(_stock.deathChance(), _stock.deathAmount(), _stock.spoilChance(), _stock.spoilAmount(), _stock.chanceByItem(), amounts);
	}

	private Rates both(double factor)
	{
		final Rates supplied = supply(factor);
		final Map<Integer, Double> amounts = new java.util.HashMap<>(supplied.amountByItem());
		amounts.put(57, amounts.getOrDefault(57, _stock.deathAmount()) * factor);
		return new Rates(supplied.deathChance(), supplied.deathAmount(), supplied.spoilChance(), supplied.spoilAmount(), supplied.chanceByItem(), amounts);
	}
}

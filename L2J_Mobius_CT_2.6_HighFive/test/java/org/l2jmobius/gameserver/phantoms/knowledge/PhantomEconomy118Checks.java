/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply;
import org.l2jmobius.gameserver.phantoms.economy.PhantomLivingMarket107Checks;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.*;

/** Read-only XML opportunity comparison. No Player, JDBC, World spawn or paid trade. */
public final class PhantomEconomy118Checks
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2) { throw new IllegalArgumentException("Exact source game root and fresh TSV output required."); }
		final Path game = Path.of(args[0]).toRealPath(), output = Path.of(args[1]).toAbsolutePath();
		check(game.equals(Path.of(".").toRealPath()) && !Files.exists(output), "Exact game cwd/fresh output required");
		check(!DatabaseFactory.isInitialized(), "DB-free rate audit cannot adopt JDBC");
		ServerConfig.DATAPACK_ROOT = game.toFile(); ThreadConfig.THREADS_FOR_LOADING = false;
		GeneralConfig.load(); PlayerConfig.load(); RatesConfig.load(); PhantomMarketConfig.load();
		ItemData.getInstance();
		final var current = PhantomRateAwareLootFairValue.Rates.capture();
		check(current.amountByItem().get(57) == 5d && current.chanceByItem().get(57) == 1d, "Final owner override must be amount5/chance1");
		final var oldAmount = new HashMap<>(current.amountByItem()); oldAmount.put(57, 1d);
		final var old = new PhantomRateAwareLootFairValue.Rates(current.deathChance(), current.deathAmount(), current.spoilChance(), current.spoilAmount(), current.chanceByItem(), oldAmount);
		final var declared = new HashSet<Integer>();
		final var xml = DocumentBuilderFactory.newInstance(); xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		try (var files = Files.walk(game.resolve("data/spawns")))
		{
			for (var file : files.filter(path -> path.toString().endsWith(".xml")).sorted().toList())
			{
				final var root = xml.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
				if (!root.getTagName().equals("list") || !root.getAttribute("enabled").equals("true")) { continue; }
				final var rows = root.getElementsByTagName("npc");
				for (int index = 0; index < rows.getLength(); index++)
				{
					final var row = (org.w3c.dom.Element) rows.item(index);
					if (row.getParentNode().getNodeName().equals("spawn") && row.hasAttribute("id")) { declared.add(Integer.parseInt(row.getAttribute("id"))); }
				}
			}
		}
		final var templates = NpcData.getInstance().getTemplates(template -> template.isType("Monster") && template.isAttackable() && template.isTargetable() && !template.isFakePlayer() && declared.contains(template.getId())).stream().sorted(java.util.Comparator.comparingInt(NpcTemplate::getId)).toList();
		// Same read-only native copier seam already exercised by LivingMarket107CatalogAudit.
		final var copier = L2jGameKnowledgeBackend.class.getDeclaredMethod("copyDrops", List.class, PhantomGameKnowledgePolicy.class);
		copier.setAccessible(true);
		final var drops = (List<DropFact>) copier.invoke(null, templates, PhantomGameKnowledgePolicy.productionDefaults());
		final var npcs = templates.stream().map(template -> new NpcFact(template.getId(), template.getLevel(), NpcKind.MONSTER, true, true, false, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT)).toList();
		// Coordinates are fixture-only: admission is enabled XML membership, never a route/Geo assertion.
		final var spawns = templates.stream().map(template -> new SpawnFact(template.getId(), 0, 0, 0, 0, 0, 1, 1, SpawnPointKind.EXACT, "fixture118.enabled_source", null, PhantomGameKnowledgeAuthority.TOPOLOGY_SNAPSHOT_FACT)).toList();
		final var knowledge = new PhantomGameKnowledgeSnapshot("economy118-xml-quotation", 1, "fixture118.not_navigation", PhantomGameKnowledgePolicy.productionDefaults(), List.of(), npcs, drops, spawns, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
		final var policy = PhantomMarketConfig.policy().orElseThrow();
		// No guessed native NPC tax or World identity: supplier-free quotation is compared separately to the NPC corridor.
		final var commerce = new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of());
		final var before = new PhantomPrivateMarketPricingAuthority(knowledge, commerce, old, policy, List.of());
		final var after = new PhantomPrivateMarketPricingAuthority(knowledge, commerce, current, policy, List.of());
		// Native NPC18342: certain7..13 Adena, amount5, configured4kills/min and0 author cost ->200/min.
		check(after.farmingOpportunityCost(18342, 60_000).orElseThrow() == 200, "119 Current native Adena opportunity was not priced from the selected NPC/current rates");
		check(before.farmingOpportunityCost(18342, 60_000).orElseThrow() == 40, "119 Adena override compounded the global death rate");
		check(after.farmingOpportunityCost(18342, 1).orElseThrow() == 1, "119 Fractional opportunity was rounded down to free travel");
		check(after.farmingOpportunityCost(18342, 0).orElseThrow() == 0, "119 Zero travel created an opportunity charge");
		check(after.farmingOpportunityCost(-1, 60_000).isEmpty() && after.farmingOpportunityCost(18342, -1).isEmpty()
			&& after.farmingOpportunityCost(18342, Long.MAX_VALUE).isEmpty(), "119 Unknown or unbounded opportunity became a safe sale");
		System.out.println("TASK119_NATIVE_XML_OPPORTUNITY_PASS npc18342=200/min source=Adena5/chance1 throughput=AUTHOR_CONFIG_NOT_MEASURED");
		final var oldQuotes = new ArrayList<PhantomPrivateMarketPricingAuthority.ItemQuote>();
		final var newQuotes = new ArrayList<PhantomPrivateMarketPricingAuthority.ItemQuote>();
		final var rows = new StringBuilder("itemId\tnpcLiquidation\toldRawFair\tnewRawFair\toldFair\tnewFair\toldBid\tnewBid\toldAsk\tnewAsk\tnewReason\n");
		int paired = 0;
		for (int itemId : new int[] {1, 1835, 1864, 1865, 1872, 1917})
		{
			final var template = ItemData.getInstance().getTemplate(itemId);
			final long liquidation = template.isSellable() ? template.getReferencePrice() / 2L : 0;
			double first = 0, second = 0;
			try
			{
				first = PhantomRateAwareLootFairValue.quote(itemId, knowledge, old).fairValue();
				second = PhantomRateAwareLootFairValue.quote(itemId, knowledge, current).fairValue();
				check(Math.abs(second - first * 5) <= Math.max(1, second) * 1e-8, "Adena override was compounded with the global rate or changed material yield"); paired++;
			}
			catch (IllegalArgumentException noPairedSource) { }
			final var left = before.quoteItem(itemId); final var right = after.quoteItem(itemId);
			oldQuotes.add(left); newQuotes.add(right);
			if (left.priced() && right.priced())
			{
				check(right.fair() >= left.fair() && right.ask() > right.bid(), "Rate-aware quotation reversed value or its no-cross spread");
				final long npcBaseCeiling = template.getReferencePrice();
				if (npcBaseCeiling > liquidation)
				{
					final var corridor = PhantomPrivateMarketQuote.quote(new PhantomPrivateMarketQuote.Inputs(right.fair(), liquidation, true, npcBaseCeiling, 0, 0, PlayerConfig.MAX_ADENA), policy);
					check(corridor.bid() < npcBaseCeiling && corridor.bid() >= liquidation, "Known NPC ceiling allowed buy-NPC/sell-offline arbitrage");
				}
				if (right.bidAllowed()) { check(PhantomOfflineSupply.saleTerms(itemId, 10, 10, 10, right.bid(), right, 0, 100, liquidation, 0).isEmpty(), "Unfunded offline BUY became income"); }
			}
			rows.append(itemId).append('\t').append(liquidation).append('\t').append(first).append('\t').append(second).append('\t').append(left.fair()).append('\t').append(right.fair()).append('\t').append(left.bid()).append('\t').append(right.bid()).append('\t').append(left.ask()).append('\t').append(right.ask()).append('\t').append(right.sourceReason()).append('\n');
		}
		check(paired > 0, "No actual paired material/Adena source was compared");
		final var merchants = PhantomMerchantPolicy.load(Path.of(PhantomMerchantPolicy.FILE));
		check(merchants != null, "Current finite merchant policy unavailable");
		final long budget = Math.min(100000, merchants.capitalPerIdentity());
		final long oldReserve = PhantomLivingMarket107Checks.funded118(oldQuotes, budget), newReserve = PhantomLivingMarket107Checks.funded118(newQuotes, budget);
		Files.writeString(output, rows);
		check(!DatabaseFactory.isInitialized(), "XML quotation initialized JDBC");
		System.out.println("ECONOMY118_DB_FREE_PASS paired=" + paired + ";capitalPerIdentity=" + merchants.capitalPerIdentity() + ";globalCapital=" + merchants.globalCapital() + ";comparisonWallet=" + budget + ";reserve1=" + oldReserve + ";reserve5=" + newReserve + ";NPC_CORRIDOR=PASS;NATIVE_SUPPLIERS=NOT_RUN;PAID_SALE=NOT_RUN;GEOMETRY=FIXTURE_ONLY");
	}
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError("TASK118 " + reason); } }
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.l2jmobius.commons.config.ThreadConfig;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;

/** Read-only native XML classification. Never initializes a database, World actors or server threads. */
public final class PhantomLivingMarket107CatalogAudit
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2) { throw new IllegalArgumentException("Exact build-only game and fresh output directories required."); }
		final Path game = Path.of(args[0]).toRealPath(), output = Path.of(args[1]).toAbsolutePath();
		if (Files.exists(game.resolve("config/Database.ini"))) { throw new IllegalArgumentException("DB-free audit requires Database.ini absent."); }
		if (Files.exists(output)) { throw new IllegalArgumentException("Fresh audit output required."); }
		ServerConfig.DATAPACK_ROOT = game.toFile();
		ThreadConfig.THREADS_FOR_LOADING = false;
		GeneralConfig.load(); PlayerConfig.load();
		org.l2jmobius.gameserver.config.RatesConfig.load();
		final var recipes = PhantomMerchantCatalog.nativeRecipes();
		final Map<Integer, List<RecipeList>> uses = new HashMap<>(), scrolls = new HashMap<>();
		final var gear = new java.util.HashSet<Integer>();
		for (var recipe : recipes)
		{
			scrolls.computeIfAbsent(recipe.getRecipeId(), _ -> new ArrayList<>()).add(recipe);
			final var target = ItemData.getInstance().getTemplate(recipe.getItemId());
			if (target != null && target.isEquipable() && (target.isWeapon() || target.isArmor())) { gear.add(target.getId()); }
			for (var ingredient : recipe.getRecipes()) { uses.computeIfAbsent(ingredient.getItemId(), _ -> new ArrayList<>()).add(recipe); }
		}
		final Map<Integer, PhantomMerchantCatalog.Mapping> mappings = new HashMap<>();
		for (String line : Files.readAllLines(game.resolve(PhantomMerchantCatalog.CLASSIFICATION_FILE), StandardCharsets.UTF_8))
		{
			if (line.isBlank() || line.startsWith("#")) { continue; }
			final String[] cells = line.split("\t", -1);
			final int id = Integer.parseInt(cells[0]);
			final var value = new PhantomMerchantCatalog.Mapping(cells[1], cells[2], Arrays.stream(cells[3].split(",")).map(Integer::parseInt).sorted().toList(), cells[4]);
			if (mappings.put(id, value) != null) { throw new AssertionError("Duplicate explicit classification ID=" + id); }
		}
		final var classifications = new TreeMap<Integer, PhantomMerchantCatalog.Classification>();
		final var sources = sourceEvidence(game);
		final var rows = new StringBuilder("itemId\tcategory\ttargetGrade\tprovenance\trecipeId\tchance\tsourceQuote\tstatus\texclusionReason\n");
		final Map<String, long[]> counts = new TreeMap<>();
		int unresolvedUsed = 0;
		for (var item : Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull).sorted(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.item.ItemTemplate::getId)).toList())
		{
			final var classification = PhantomMerchantCatalog.classify(item, uses.getOrDefault(item.getId(), List.of()), scrolls.getOrDefault(item.getId(), List.of()), mappings.get(item.getId()), gear.contains(item.getId()));
			if (classification == null) { continue; }
			classifications.put(item.getId(), classification);
			if (uses.containsKey(item.getId()) && classification.exclusionReason().equals("UNRESOLVED_CLASSIFICATION")) { unresolvedUsed++; }
			final String chances = scrolls.getOrDefault(item.getId(), List.of()).stream().map(recipe -> Integer.toString(recipe.getSuccessRate())).distinct().sorted().collect(java.util.stream.Collectors.joining(","));
			final String source = classification.eligible() ? sources.getOrDefault(item.getId(), uses.containsKey(item.getId()) ? "UNRESOLVED_ACQUISITION_ANCHOR" : gear.contains(item.getId()) ? "RECIPE_BOM_PRESENT;INPUT_ANCHORS_RUNTIME_UNRESOLVED" : "NO_CONFIRMED_STATIC_ACQUISITION_ANCHOR") : "DENIED";
			rows.append(item.getId()).append('\t').append(classification.category()).append('\t').append(classification.targetGrade()).append('\t').append(classification.provenance()).append('\t').append(classification.recipes().toString().replace(" ", "")).append('\t').append(chances).append('\t').append(source).append('\t').append(classification.eligible() ? "CLASS_ELIGIBLE_RUNTIME_CORRIDOR_UNRESOLVED" : "DENIED").append('\t').append(classification.exclusionReason()).append('\n');
			final var count = counts.computeIfAbsent(classification.category() + '\t' + classification.targetGrade(), _ -> new long[2]);
			count[0]++; if (classification.eligible()) { count[1]++; }
		}
		check(classifications.get(1864).eligible() && classifications.get(1864).category().equals("MATERIAL_RESOURCE"), "Common low-grade craft material excluded");
		check(!classifications.get(1903).eligible() && !classifications.get(1910).eligible(), "D/C piece admitted");
		check(classifications.get(1917).eligible() && classifications.get(1917).targetGrade().equals("B"), "B piece missing");
		check(classifications.get(10397).eligible() && classifications.get(10397).targetGrade().equals("S80"), "S80 piece collapsed");
		check(classifications.get(15634).eligible() && classifications.get(15634).targetGrade().equals("S84"), "S84 piece collapsed");
		check(classifications.get(1).eligible() && classifications.get(1).category().equals("FINISHED_GEAR"), "Ordinary source-backed NPC gear without a craft recipe excluded");
		check(unresolvedUsed == 0, "Canonical ingredient IDs unresolved=" + unresolvedUsed);
		for (String grade : List.of("B", "A", "S", "S80", "S84"))
		{
			check(classifications.values().stream().anyMatch(value -> value.eligible() && value.category().equals("RECIPE_SCROLL60") && value.targetGrade().equals(grade)), "No actual60% equipment scroll for " + grade);
		}
		for (var entry : classifications.entrySet())
		{
			final var classification = entry.getValue();
			if (classification.eligible() && classification.category().equals("RECIPE_SCROLL60"))
			{
				check(scrolls.get(entry.getKey()).stream().allMatch(recipe -> recipe.getSuccessRate() == 60), "100% recipe bypassed60% contract");
				check(Arrays.stream(classification.targetGrade().split(",")).noneMatch(grade -> List.of("NONE", "D", "C").contains(grade)), "D/C recipe bypassed grade deny");
			}
		}
		Files.createDirectory(output);
		Files.writeString(output.resolve("market107-universe.tsv"), rows, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		final var coverage = new StringBuilder("category\tgrade\tsourceCandidates\tclassEligible\tpriced\tfunded\tinStock\teverOffered\tcurrentlyOpen\n");
		counts.forEach((key, count) -> coverage.append(key).append('\t').append(count[0]).append('\t').append(count[1]).append("\tNOT_RUN\tNOT_RUN\tNOT_RUN\tNOT_RUN\tNOT_RUN\n"));
		Files.writeString(output.resolve("market107-coverage.tsv"), coverage, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		System.out.println("OWN107_NATIVE_XML_CLASSIFICATION_PASS recipes=" + recipes.size() + ";ingredientIds=" + uses.size() + ";explicitMappings=" + mappings.size() + ";candidateIds=" + classifications.size() + ";classEligible=" + classifications.values().stream().filter(PhantomMerchantCatalog.Classification::eligible).count() + ";unresolvedCraftIds=" + unresolvedUsed + ";sourceEvidenceIds=" + sources.size() + ";RUNTIME_PRICE=NOT_RUN;SQL=NOT_RUN;NATIVE_STORES=NOT_RUN");
	}
	@SuppressWarnings("unchecked")
	private static Map<Integer, String> sourceEvidence(Path game) throws Exception
	{
		final var result = new HashMap<Integer, String>();
		final var declared = new java.util.HashSet<Integer>();
		try (var files = Files.walk(game.resolve("data/spawns")))
		{
			for (var file : files.filter(path -> path.toString().endsWith(".xml")).sorted().toList())
			{
				final var root = xml(file).getDocumentElement();
				if (!root.getTagName().equals("list") || !root.getAttribute("enabled").equals("true")) { continue; }
				final var npcs = root.getElementsByTagName("npc");
				for (int index = 0; index < npcs.getLength(); index++)
				{
					final var node = (org.w3c.dom.Element) npcs.item(index);
					if (node.getParentNode().getNodeName().equals("spawn") && node.hasAttribute("id")) { declared.add(Integer.parseInt(node.getAttribute("id"))); }
				}
			}
		}
		final var templates = org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplates(_ -> true).stream().sorted(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.actor.templates.NpcTemplate::getId)).toList();
		// Reuse only the pure native drop copier. Never call backend.load(), SpawnData or initialize World.
		final var method = org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend.class.getDeclaredMethod("copyDrops", List.class, org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy.class);
		method.setAccessible(true);
		final var drops = (List<org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact>) method.invoke(null, templates, org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy.productionDefaults());
		final var eligible = templates.stream().filter(template -> template.isAttackable() && template.isTargetable() && !template.isType("GrandBoss") && !template.isType("RaidBoss") && !template.isType("SiegeGuard") && !template.isFakePlayer() && declared.contains(template.getId())).map(org.l2jmobius.gameserver.model.actor.templates.NpcTemplate::getId).collect(java.util.stream.Collectors.toUnmodifiableSet());
		final var deathByItem = drops.stream().filter(drop -> drop.sourceKind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind.DEATH_DROP).collect(java.util.stream.Collectors.groupingBy(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact::itemId));
		final var spoilByItem = drops.stream().filter(drop -> drop.sourceKind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind.SPOIL).collect(java.util.stream.Collectors.groupingBy(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact::itemId));
		final var deathByNpc = drops.stream().filter(drop -> drop.sourceKind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind.DEATH_DROP).collect(java.util.stream.Collectors.groupingBy(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact::npcId));
		final var rates = PhantomRateAwareLootFairValue.Rates.capture();
		boolean scarcityResponse = false;
		for (int id : drops.stream().mapToInt(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropFact::itemId).distinct().sorted().toArray())
		{
			if (id == 57) { continue; }
			try
			{
				final var quote = PhantomRateAwareLootFairValue.quote(id, deathByItem, spoilByItem, deathByNpc, eligible, rates);
				result.put(id, "SOURCE_CANDIDATE_FAIR=" + quote.fairValue() + ";loot." + quote.npcId() + '.' + quote.source() + ";DECLARED_ENABLED_XML;RUNTIME_SPAWN_TAX_UNRESOLVED");
				if (!scarcityResponse && quote.source() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.DropSourceKind.SPOIL && rates.spoilAmount() <= 500)
				{
					final var richer = new PhantomRateAwareLootFairValue.Rates(rates.deathChance(), rates.deathAmount(), rates.spoilChance(), rates.spoilAmount() * 2, rates.chanceByItem(), rates.amountByItem());
					final var changed = PhantomRateAwareLootFairValue.quote(id, deathByItem, spoilByItem, deathByNpc, eligible, richer);
					check(changed.fairValue() < quote.fairValue(), "Native spoil availability did not affect source scarcity value");
					scarcityResponse = true;
				}
			}
			catch (IllegalArgumentException noSource) { result.put(id, "UNRESOLVED_ACQUISITION_ANCHOR:" + noSource.getMessage()); }
		}
		try (var files = Files.walk(game.resolve("data/buylists")))
		{
			for (var file : files.filter(path -> path.toString().endsWith(".xml")).sorted().toList())
			{
				final var list = xml(file).getDocumentElement().getElementsByTagName("item");
				for (int index = 0; index < list.getLength(); index++)
				{
					final var node = (org.w3c.dom.Element) list.item(index); if (!node.hasAttribute("id")) { continue; }
					final int id = Integer.parseInt(node.getAttribute("id"));
					result.merge(id, "NPC_BUYLIST=" + file.getFileName() + ";UNRESOLVED_TAX_MERCHANT", (old, next) -> old.contains("NPC_BUYLIST=") ? old : old + ';' + next);
				}
			}
		}
		check(scarcityResponse, "No source-backed spoil sensitivity witness");
		check(!org.l2jmobius.commons.database.DatabaseFactory.isInitialized(), "Source audit initialized JDBC");
		check(org.l2jmobius.gameserver.model.World.getInstance().getPlayers().isEmpty(), "Source audit created native Player actors");
		System.out.println("OWN107_SOURCE_AUDIT declaredNpcIds=" + declared.size() + ";nativeDropFacts=" + drops.size() + ";evidenceIds=" + result.size() + ";rates=" + rates.deathChance() + '/' + rates.deathAmount() + '/' + rates.spoilChance() + '/' + rates.spoilAmount() + ";spoilScarcityResponse=PASS;RUNTIME_CORRIDOR=UNRESOLVED;ACTORS=0;SQL=0");
		return result;
	}
	private static org.w3c.dom.Document xml(Path file) throws Exception
	{
		final var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); factory.setExpandEntityReferences(false); factory.setXIncludeAware(false);
		return factory.newDocumentBuilder().parse(file.toFile());
	}
	private static void check(boolean condition, String reason) { if (!condition) { throw new AssertionError(reason); } }
}

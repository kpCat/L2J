/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.MerchantPriceConfigTable;
import org.l2jmobius.gameserver.data.xml.ArmorSetData;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.managers.RecipeCraftObserver;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.recipe.ManufactureItem;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.network.holders.RequestTrade;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketPricingAuthority;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Policy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomRateAwareLootFairValue.Rates;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Own068 component scene. All initial levels/skills/funds/finite seller stock are declared TEST inputs. */
public final class PhantomEquipmentCraft068Native
{
	private static final List<Player> ACTORS = new ArrayList<>();
	private static final StringBuilder RECEIPT = new StringBuilder("stage\tvalue\n");
	private static final java.util.Properties INPUTS = new java.util.Properties();
	private static Path inputState;
	public static void main(String[] args) throws Exception
	{
		if (!PhantomTask068Lane.enabled() || args.length != 0) { throw new IllegalArgumentException("Exact own068 native lane required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath();
		final Path report = module.getParent().resolve(".phantom-local/task068/reports/equipment-craft068.tsv");
		if (Files.exists(report)) { int attempt = 1; while (Files.exists(report.resolveSibling("equipment-craft068-attempt-" + attempt + ".tsv")) && attempt < 10) { attempt++; } Files.copy(report, report.resolveSibling("equipment-craft068-attempt-" + attempt + ".tsv")); }
		inputState = report.resolveSibling("equipment-craft068-inputs.properties");
		if (Files.exists(inputState)) { try (var reader = Files.newBufferedReader(inputState)) { INPUTS.load(reader); } }
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(68006804L, module, report.getParent()));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance(); ArmorSetData.getInstance();
			OfflineTradeConfig.OFFLINE_DISCONNECT_FINISHED = false;
			RECEIPT.append("fixture\tordinary client-null vendor stays online between finite listings: OFFLINE_DISCONNECT_FINISHED=false for this JVM; no rates/chance override\n");
			MerchantPriceConfigTable.getInstance().loadInstances(); MerchantPriceConfigTable.getInstance().updateReferences(); SpawnData.getInstance();
			final var pricing = pricing();
			for (int item : new int[] {1864, 1865, 1866, 1458, 1785}) { RECEIPT.append("source_quote\t").append(pricing.quoteItem(item)).append('\n'); }
			Files.createDirectories(report.getParent()); Files.writeString(report, RECEIPT);
			final var classes = Arrays.stream(PlayerClass.values()).filter(value -> value.level() == 3 && PlayerTemplateData.getInstance().getTemplate(value) != null).toList();
			final var skills = classes.stream().collect(java.util.stream.Collectors.toMap(value -> value, PhantomEquipmentCraft068Native::classSkills));
			final var spoiler = classes.stream().filter(value -> hasSpoil(skills.get(value))).min(Comparator.comparingInt(PlayerClass::getId)).orElseThrow();
			final var fighter = classes.stream().filter(value -> !value.isMage() && !hasSpoil(skills.get(value))).max(Comparator.comparingLong(value -> count(skills.get(value), EffectType.PHYSICAL_ATTACK))).orElseThrow();
			final var mage = classes.stream().filter(value -> value.isMage() && !value.isSummoner()).max(Comparator.comparingLong(value -> count(skills.get(value), EffectType.MAGICAL_ATTACK))).orElseThrow();
			final var support = classes.stream().filter(PlayerClass::isMage).max(Comparator.comparingLong(value -> count(skills.get(value), EffectType.HEAL) + count(skills.get(value), EffectType.RESURRECTION))).orElseThrow();
			final var vendor = actor("Vendor", fighter);
			for (var role : List.of(Map.entry("Fighter", fighter), Map.entry("Mage", mage), Map.entry("Spoiler", spoiler), Map.entry("Support", support)))
			{
				final var player = actor(role.getKey(), role.getValue());
				for (var grade : List.of(CrystalType.NONE, CrystalType.D, CrystalType.C, CrystalType.B, CrystalType.A, CrystalType.S, CrystalType.S80))
				{
					final var weapon = candidate(player, grade, true, null);
					final var armor = candidate(player, grade, false, null);
					RECEIPT.append("coverage\t").append(role.getKey()).append(';').append(role.getValue()).append(';').append(grade).append(";weapon=").append(weapon.getId()).append(";armor=").append(armor.getId()).append(";actualSkills=").append(player.getAllSkills().size()).append('\n');
				}
				for (var grade : List.of(CrystalType.B, CrystalType.S80))
				{
					buyEquip(player, vendor, candidate(player, grade, true, pricing), pricing);
					final boolean chestChanged = buyEquip(player, vendor, candidate(player, grade, false, pricing), pricing);
					if (chestChanged && role.getKey().equals("Fighter")) { completeArmor(player, vendor, grade, pricing); }
				}
			}
			final var craftClass = classes.stream().max(Comparator.comparingInt(value -> skills.get(value).stream().filter(skill -> skill.getId() == CommonSkill.CREATE_DWARVEN.getId()).mapToInt(Skill::getLevel).max().orElse(0))).orElseThrow();
			final var crafter = actor("Crafter", craftClass);
			final var customer = actor("Client", fighter);
			final var recipe = Arrays.stream(RecipeData.getInstance().getAllItemIds()).mapToObj(id -> RecipeData.getInstance().getRecipeByItemId(id)).filter(value -> value.isDwarvenRecipe() && value.getLevel() <= crafter.getDwarvenCraft() && value.getSuccessRate() == 100 && value.getRecipes().length > 0 && Arrays.stream(value.getStatUse()).anyMatch(stat -> stat.getType().name().equals("MP") && stat.getValue() > 0)).filter(value -> { try { return pricing.manufactureFee(value.getId(), value.getItemId()) > 0; } catch (RuntimeException unavailable) { return false; } }).min(Comparator.comparingInt(RecipeList::getLevel).thenComparingInt(RecipeList::getId)).orElseThrow();
			crafter.registerDwarvenRecipeList(recipe, true);
			RECEIPT.append("fixture\tTEST_PREMEASUREMENT;native classes/skills/85;known recipe=").append(recipe.getId()).append(";finite ingredients and customer funds;not earned income\n");
			craft(crafter, crafter, recipe, 0);
			final long fee = pricing.manufactureFee(recipe.getId(), recipe.getItemId());
			crafter.getManufactureItems().put(recipe.getId(), new ManufactureItem(recipe.getId(), fee)); crafter.setPrivateStoreType(PrivateStoreType.MANUFACTURE);
			craft(crafter, customer, recipe, fee);
			RECEIPT.append("result\tPASS native class/grade matrix, paid acquisition/equip, self-craft and paid manufacture\n");
		}
		catch (Throwable failure) { RECEIPT.append("failure\t").append(failure).append('\n'); throw failure; }
		finally
		{
			Files.createDirectories(report.getParent()); Files.writeString(report, RECEIPT);
			for (var player : ACTORS) { player.setPrivateStoreType(PrivateStoreType.NONE); player.stopAllTasks(); player.storeMe(); if (World.getInstance().getPlayer(player.getObjectId()) == player) { player.deleteMe(); } }
			ThreadPool.shutdown(); DatabaseFactory.close();
		}
	}
	private static List<Skill> classSkills(PlayerClass value)
	{
		return SkillTreeData.getInstance().getCompleteClassSkillTree(value).values().stream().filter(learn -> learn.getGetLevel() <= 85).map(learn -> SkillData.getInstance().getSkill(learn.getSkillId(), learn.getSkillLevel())).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toMap(Skill::getId, skill -> skill, (first, second) -> first.getLevel() >= second.getLevel() ? first : second)).values().stream().toList();
	}
	private static long count(List<Skill> skills, EffectType effect) { return skills.stream().filter(skill -> skill.hasEffectType(effect)).count(); }
	private static boolean hasSpoil(List<Skill> skills) { return skills.stream().anyMatch(skill -> Arrays.stream(EffectScope.values()).anyMatch(scope -> skill.hasEffects(scope) && skill.getEffects(scope).stream().anyMatch(effect -> effect.getClass().getSimpleName().equals("Spoil")))); }
	private static Player actor(String role, PlayerClass value) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES('task068craft','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
		Integer existing = null;
		try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId,base_class,accesslevel FROM characters WHERE account_name='task068craft' AND char_name=?"))
		{
			query.setString(1, "D068" + role); try (var rows = query.executeQuery()) { if (rows.next()) { existing = rows.getInt(1); check(rows.getInt(2) == value.getId() && rows.getInt(3) == 0 && !rows.next(), "Stable D fixture identity differs."); } }
		}
		final var player = existing == null ? Player.create(PlayerTemplateData.getInstance().getTemplate(value), "task068craft", "D068" + role, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)) : Player.load(existing);
		check(player != null && !player.isGM(), "Native component actor absent."); ACTORS.add(player); player.stopAllTasks(); player.setBaseClass(value.getId()); player.setPlayerClass(value.getId());
		player.getStat().setExp(player.getStat().getExpForLevel(85)); player.getStat().setLevel((byte) 85);
		for (var skill : classSkills(value)) { player.addSkill(skill, false); }
		player.giveAvailableAutoGetSkills(); player.refreshExpertisePenalty(); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
		if (existing == null) { player.addAdena(ItemProcessType.REWARD, Math.min(PlayerConfig.MAX_ADENA, 10_000_000_000L), player, false); }
		for (var equipped : player.getInventory().getItems().stream().filter(item -> item.isEquipped()).toList()) { player.useEquippableItem(equipped, false); }
		player.setOnlineStatus(true, false); player.spawnMe(81700, 148500, -3470);
		return player;
	}
	private static ItemTemplate candidate(Player player, CrystalType grade, boolean weapon, PhantomPrivateMarketPricingAuthority pricing)
	{
		return Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull).filter(item -> item.getCrystalType() == grade && item.isTradeable() && item.getDefaultEnchantLevel() == 0 && (weapon ? item instanceof Weapon : item.getBodyPart() == BodyPart.CHEST || item.getBodyPart() == BodyPart.FULL_ARMOR)).filter(item -> PhantomNativeEquipmentPolicy.compatible(player, item) && PhantomNativeEquipmentPolicy.power(player, item) > 0).filter(item -> pricing == null || pricing.quoteItem(item.getId()).priced()).sorted(Comparator.comparing((ItemTemplate item) -> pricing == null || !INPUTS.containsKey("gear." + player.getObjectId() + '.' + item.getId())).thenComparing(PhantomNativeEquipmentPolicy.preference(player))).findFirst().orElseThrow(() -> new AssertionError("No legal data candidate: " + player.getPlayerClass() + '/' + grade + '/' + weapon));
	}
	private static boolean buyEquip(Player buyer, Player seller, ItemTemplate template, PhantomPrivateMarketPricingAuthority pricing) throws Exception
	{
		final long price = pricing.quoteItem(template.getId()).ask();
		check(price > 0, "No safe candidate price.");
		if (buyer.getAdena() < price || !PhantomNativeEquipmentPolicy.upgrade(buyer, template))
		{
			RECEIPT.append(buyer.getAdena() < price ? "SKIPPED_AFFORDABILITY\t" : "SKIPPED_NOT_UPGRADE\t").append(buyer.getPlayerClass()).append(';').append(template.getId()).append(';').append(template.getBodyPart()).append(";power=").append(PhantomNativeEquipmentPolicy.power(buyer, template)).append(";price=").append(price).append(";remaining=").append(buyer.getAdena()).append(";nativeOwned=").append(buyer.getInventory().getItems().stream().filter(item -> item.isEquipped()).map(item -> item.getId() + ":" + PhantomNativeEquipmentPolicy.power(buyer, item.getTemplate())).toList()).append('\n'); return false;
		}
		final String key = "gear." + buyer.getObjectId() + '.' + template.getId();
		if (INPUTS.containsKey(key) && !INPUTS.getProperty(key).equals("PENDING"))
		{
			final String[] paid = INPUTS.getProperty(key).split(":");
			check(paid.length == 3 && paid[0].equals("PAID") && Long.parseLong(paid[2]) == price, "Uncertain previous gear input; no refill/replay.");
			final var owned = buyer.getInventory().getItemByObjectId(Integer.parseInt(paid[1])); check(owned != null && owned.getId() == template.getId(), "Previously paid gear ownership changed."); buyer.useEquippableItem(owned, false); check(owned.isEquipped(), "Resume native equip failed.");
			RECEIPT.append("resume_paid_equip\t").append(key).append(';').append(INPUTS.getProperty(key)).append(";no input grant repeated\n"); return true;
		}
		final org.l2jmobius.gameserver.model.item.instance.Item stock;
		if (INPUTS.getProperty(key, "").equals("PENDING")) { stock = seller.getInventory().getItemByItemId(template.getId()); check(stock != null && stock.getCount() == 1 && !stock.isEquipped(), "Uncertain previous gear input; no refill/replay."); }
		else { INPUTS.setProperty(key, "PENDING"); saveInputs(); stock = seller.getInventory().addItem(ItemProcessType.REWARD, template.getId(), 1, seller, "TEST_PREMEASUREMENT_FINITE_SELLER_STOCK"); }
		seller.getSellList().clear(); check(seller.getSellList().addItem(stock.getObjectId(), 1, price) != null, "Native listing failed."); seller.setPrivateStoreType(PrivateStoreType.SELL);
		final long beforeBuyer = buyer.getAdena(), beforeSeller = seller.getAdena();
		check(PrivateStoreService.getInstance().buy(buyer, seller.getObjectId(), Set.of(new RequestTrade(stock.getObjectId(), stock.getId(), 1, price))) == PrivateStoreService.Result.COMMITTED, "Native paid equipment purchase failed.");
		check(buyer.getAdena() == beforeBuyer - price && seller.getAdena() == beforeSeller + price, "Equipment payment was not conserved.");
		final var owned = buyer.getInventory().getItemByItemId(template.getId()); check(owned != null, "Bought native equipment not owned."); buyer.useEquippableItem(owned, false); check(owned.isEquipped(), "Native equip failed.");
		buyer.getInventory().updateDatabase(); seller.getInventory().updateDatabase(); buyer.storeMe(); seller.storeMe();
		INPUTS.setProperty(key, "PAID:" + owned.getObjectId() + ':' + price); saveInputs();
		RECEIPT.append("paid_equip\t").append(buyer.getPlayerClass()).append(';').append(template.getCrystalType()).append(';').append(template.getId()).append(";price=").append(price).append(";source=paid_private_buy;finiteTestSeller=true;conserved=true\n");
		return true;
	}
	private static void completeArmor(Player player, Player seller, CrystalType grade, PhantomPrivateMarketPricingAuthority pricing) throws Exception
	{
		final var chest = player.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
		final var set = ArmorSetData.getInstance().getSet(chest.getId());
		for (var body : List.of(BodyPart.HEAD, BodyPart.LEGS, BodyPart.GLOVES, BodyPart.FEET))
		{
			if (body == BodyPart.LEGS && chest.getTemplate().getBodyPart() == BodyPart.FULL_ARMOR) { continue; }
			final int slot = switch (body) { case HEAD -> Inventory.PAPERDOLL_HEAD; case LEGS -> Inventory.PAPERDOLL_LEGS; case GLOVES -> Inventory.PAPERDOLL_GLOVES; case FEET -> Inventory.PAPERDOLL_FEET; default -> throw new AssertionError(); };
			final var extra = Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull).filter(item -> item.getBodyPart() == body && item.getCrystalType() == grade && item.isTradeable() && item.getDefaultEnchantLevel() == 0 && PhantomNativeEquipmentPolicy.compatible(player, item) && PhantomNativeEquipmentPolicy.power(player, item) > 0 && pricing.quoteItem(item.getId()).priced()).sorted(Comparator.comparing((ItemTemplate item) -> set == null || !set.containItem(slot, item.getId())).thenComparing(PhantomNativeEquipmentPolicy.preference(player))).findFirst().orElseThrow();
			buyEquip(player, seller, extra, pricing);
		}
		RECEIPT.append("native_kit\t").append(player.getPlayerClass()).append(';').append(grade).append(";chest=").append(chest.getId()).append(";setComplete=").append(set != null && set.containAll(player)).append(";allPiecesPaidPrivate=true\n");
	}
	private static void craft(Player crafter, Player customer, RecipeList recipe, long fee) throws Exception
	{
		final String key = "craft." + customer.getObjectId() + '.' + recipe.getId() + '.' + (fee > 0 ? "paid" : "self");
		if (INPUTS.containsKey(key)) { check(INPUTS.getProperty(key).startsWith("DONE:"), "Uncertain craft input; no ingredient refill/replay."); RECEIPT.append("resume_craft\t").append(key).append(';').append(INPUTS.getProperty(key)).append('\n'); return; }
		INPUTS.setProperty(key, "PENDING"); saveInputs();
		final var before = new java.util.HashMap<Integer, Long>();
		for (var ingredient : recipe.getRecipes()) { customer.getInventory().addItem(ItemProcessType.REWARD, ingredient.getItemId(), ingredient.getQuantity(), customer, "TEST_PREMEASUREMENT_FINITE_INGREDIENTS"); before.put(ingredient.getItemId(), customer.getInventory().getInventoryItemCount(ingredient.getItemId(), -1)); }
		crafter.setCurrentMp(crafter.getMaxMp()); final double mp = crafter.getCurrentMp(); final long crafterAdena = crafter.getAdena(), customerAdena = customer.getAdena();
		final List<RecipeCraftObserver.Event> events = new CopyOnWriteArrayList<>();
		if (crafter == customer) { RecipeManager.getInstance().requestMakeItem(crafter, recipe.getId(), events::add); }
		else { RecipeManager.getInstance().requestManufactureItem(crafter, recipe.getId(), customer, events::add); }
		final long deadline = System.currentTimeMillis() + 10000;
		while (events.stream().noneMatch(event -> Set.of(RecipeCraftObserver.Type.SUCCESS_PRODUCT, RecipeCraftObserver.Type.RARE_PRODUCT, RecipeCraftObserver.Type.CRAFT_FAILED, RecipeCraftObserver.Type.ABORTED).contains(event.type())) && System.currentTimeMillis() < deadline) { Thread.sleep(25); }
		check(events.stream().anyMatch(event -> event.type() == RecipeCraftObserver.Type.SUCCESS_PRODUCT || event.type() == RecipeCraftObserver.Type.RARE_PRODUCT), "Canonical craft did not produce a product: " + events);
		for (var ingredient : recipe.getRecipes()) { check(customer.getInventory().getInventoryItemCount(ingredient.getItemId(), -1) == before.get(ingredient.getItemId()) - ingredient.getQuantity(), "Ingredients consumed twice or not consumed."); }
		check(events.stream().anyMatch(event -> event.mpConsumed() > 0) && crafter.getCurrentMp() < mp, "Canonical craft MP expense missing.");
		if (fee > 0) { check(customer.getAdena() == customerAdena - fee && crafter.getAdena() == crafterAdena + fee && events.stream().anyMatch(event -> event.type() == RecipeCraftObserver.Type.FEE_TRANSFERRED && event.feeTransferred() == fee), "Manufacture fee transfer missing."); }
		else { check(customer.getAdena() == customerAdena, "Self craft charged a service fee."); }
		for (var event : events) { RECEIPT.append(fee == 0 ? "self_craft\t" : "paid_manufacture\t").append(event).append('\n'); }
		customer.getInventory().updateDatabase(); crafter.getInventory().updateDatabase(); customer.storeMe(); crafter.storeMe();
		INPUTS.setProperty(key, "DONE:" + events); saveInputs();
	}
	private static void saveInputs() throws Exception
	{
		Files.createDirectories(inputState.getParent()); final Path temporary = inputState.resolveSibling(inputState.getFileName() + ".new");
		try (var writer = Files.newBufferedWriter(temporary)) { INPUTS.store(writer, "Own068 finite TEST input receipts; uncertain PENDING never refills."); }
		Files.move(temporary, inputState, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
	}
	private static PhantomPrivateMarketPricingAuthority pricing() throws Exception
	{
		final var topologyBackend = new L2jTopologyValidationBackend(); final var snapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
		final var topology = new PhantomTopologyQuery(snapshot, topologyBackend, new PhantomTopologyMetrics()); final var policy = PhantomGameKnowledgePolicy.productionDefaults(); final var backend = new L2jGameKnowledgeBackend();
		final var knowledge = new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, policy), topology, policy).build();
		return new PhantomPrivateMarketPricingAuthority(knowledge, new PhantomCommerceCatalogLoader(Path.of(".")).load().catalog(), Rates.capture(), new Policy(500, 500), World.getInstance().getVisibleObjects());
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.l2jmobius.gameserver.data.xml.*;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.multisell.Entry;
import org.l2jmobius.gameserver.network.clientpackets.MultiSellChoose;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.MultisellOffer;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.*;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy;

/** Exact stock blacksmith SA exchange. EconomyService owns its reservations, dispatch and durable audit. */
public final class PhantomReadySa
{
	public static final String TYPE = "sa.install";
	private static final Set<String> KEYS = Set.of("sa.list", "sa.entry", "sa.product", "sa.npc", "sa.npc_object", "sa.reserve", "sa.attempts", org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.ATTEMPT_REVISION);
	private PhantomReadySa() { }

	public record Spec(int objectId, int listId, int entryId, int productId, int npcId, int npcObjectId, long reserve, int attempts, Map<Integer, Long> inputs) { }
	public static Spec parse(PhantomGoal goal, boolean active)
	{
		if (goal == null || !TYPE.equals(goal.goalType()) || !TYPE.equals(goal.purposeKey()) || active && goal.status() != PhantomGoalStatus.ACTIVE
			|| goal.target() == null || !"item.object".equals(goal.target().namespace()) || goal.requiredAmount() != 1 || goal.expenseBudget() <= 0 || goal.riskBudget() <= 0 || !PhantomJourneyLifetime.valid(goal)) { throw new IllegalArgumentException("Invalid ready SA goal."); }
		var keys = new HashSet<>(goal.constraints().keySet()); keys.removeAll(PhantomJourneyLifetime.KEYS);
		if (!keys.equals(KEYS)) { throw new IllegalArgumentException("Unknown ready SA constraint."); }
		var c = goal.constraints(); var inputs = new TreeMap<Integer, Long>();
		for (var source : goal.validSources())
		{
			var parts = source.key().split(":", -1);
			if (!"sa.input".equals(source.namespace()) || parts.length != 2) { throw new IllegalArgumentException("Invalid ready SA source."); }
			int id = Integer.parseInt(parts[0]); long count = Long.parseLong(parts[1]);
			if (id <= 0 || count <= 0 || inputs.put(id, count) != null) { throw new IllegalArgumentException("Ambiguous ready SA input."); }
		}
		int object = Integer.parseInt(goal.target().key());
		if (object <= 0 || inputs.size() < 3 || inputs.size() > 8 || !inputs.containsKey(57) || c.get("sa.reserve") < 0 || c.get("sa.attempts") < 0 || c.get("sa.attempts") > 1) { throw new IllegalArgumentException("Unsafe ready SA bounds."); }
		for (String key : List.of("sa.list", "sa.entry", "sa.product", "sa.npc", "sa.npc_object")) { if (c.get(key) <= 0 || c.get(key) > Integer.MAX_VALUE) { throw new IllegalArgumentException("Invalid ready SA identity."); } }
		Binding.parse(goal);
		org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.attemptRevision(goal);
		return new Spec(object, c.get("sa.list").intValue(), c.get("sa.entry").intValue(), c.get("sa.product").intValue(), c.get("sa.npc").intValue(), c.get("sa.npc_object").intValue(), c.get("sa.reserve"), c.get("sa.attempts").intValue(), Map.copyOf(inputs));
	}

	public static PhantomGoal project(PhantomGoal goal)
	{
		var spec = parse(goal, true); if (spec.attempts() != 0) { throw new IllegalArgumentException("SA attempt already consumed."); }
		var constraints = new TreeMap<>(goal.constraints()); constraints.put("sa.attempts", 1L);
		return new PhantomGoal(goal.goalId(), goal.goalType(), PhantomGoalStatus.COMPLETED, goal.subject(), goal.target(), 1, 1, goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), constraints, "result.success", Math.addExact(goal.revision(), 1), goal.payloadText());
	}

	/** Loaded stock only; bounded per-weapon shortlist, no new merchant catalogue. */
	public static Map<Integer, List<MultisellOffer>> offers(PhantomCommerceCatalog catalog)
	{
		var result = new TreeMap<Integer, List<MultisellOffer>>();
		for (var offer : catalog.multisellOffers())
		{
			if (!offer.flags().maintainEnchantment() || offer.flags().useRate() != 1 || offer.products().size() != 1 || offer.products().getFirst().count() != 1 || offer.npcIds().isEmpty() || offer.ingredients().size() > 8
				|| offer.ingredients().stream().anyMatch(i -> i.itemId() <= 0 || ItemData.getInstance().getTemplate(i.itemId()) == null)) { continue; }
			var product = ItemData.getInstance().getTemplate(offer.products().getFirst().itemId());
			if (!(product instanceof Weapon) || product.getSkills() == null || product.getSkills().length == 0 || Arrays.stream(product.getSkills()).anyMatch(h -> h.getSkill() == null) || product.isQuestItem() || product.isPvpItem() || product.isHeroItem()) { continue; }
			var weapons = offer.ingredients().stream().filter(i -> ItemData.getInstance().getTemplate(i.itemId()) instanceof Weapon).toList();
			if (weapons.size() != 1 || weapons.getFirst().count() != 1 || offer.ingredients().stream().noneMatch(i -> LevelUpCrystalData.getInstance().getSoulCrystal(i.itemId()) != null) || offer.ingredients().stream().noneMatch(i -> i.itemId() == 57 && i.count() > 0)) { continue; }
			var base = ItemData.getInstance().getTemplate(weapons.getFirst().itemId());
			if (PhantomNativeEquipmentPolicy.protectedTemplateEffects(base) || base.getCrystalType() != product.getCrystalType()) { continue; }
			var list = result.computeIfAbsent(base.getId(), _ -> new ArrayList<>());
			if (list.size() < 16) { list.add(offer); }
		}
		result.replaceAll((_, values) -> List.copyOf(values));
		return Map.copyOf(result);
	}

	/** Opportunistic native interaction only; travel never fabricates an unavailable blacksmith. */
	public static Optional<PhantomGoal> proposal(Player player, PhantomGoal root, Binding binding, long revision, long deadline, long reserve,
		Map<Integer, List<MultisellOffer>> offers, List<Npc> counters)
	{
		for (var item : player.getInventory().getItems().stream().filter(i -> i.getItemLocation() == ItemLocation.INVENTORY && !i.isEquipped() && i.getTemplate() instanceof Weapon && i.getEnchantLevel() == 0 && !PhantomNativeEquipmentPolicy.protectedEffects(i))
			.sorted(Comparator.comparingInt(Item::getObjectId)).limit(64).toList())
		{
			for (var offer : offers.getOrDefault(item.getId(), List.of()))
			{
				var product = ItemData.getInstance().getTemplate(offer.products().getFirst().itemId());
				if (!PhantomNativeEquipmentPolicy.upgrade(player, product) || player.getInventory().getItemByItemId(product.getId()) != null) { continue; }
				var inputs = new TreeMap<Integer, Long>(); for (var input : offer.ingredients()) { inputs.merge(input.itemId(), input.count(), Math::addExact); }
				if (inputs.size() != offer.ingredients().size() || inputs.entrySet().stream().anyMatch(i -> player.getInventory().getInventoryItemCount(i.getKey(), -1) < i.getValue())) { continue; }
				long fee = inputs.get(57), cost = fee;
				for (var input : inputs.entrySet()) { if (input.getKey() != 57 && input.getKey() != item.getId()) { cost = Math.addExact(cost, Math.multiplyExact((long) Math.max(0, ItemData.getInstance().getTemplate(input.getKey()).getReferencePrice()), input.getValue())); } }
				if (cost > item.getReferencePrice() || player.getAdena() - fee < reserve) { continue; }
				var npc = counters.stream().filter(n -> offer.npcIds().contains(n.getId()) && n.getInstanceId() == player.getInstanceId() && player.isInsideRadius3D(n, Npc.INTERACTION_DISTANCE)).min(Comparator.comparingInt(Npc::getObjectId)).orElse(null);
				if (npc == null) { continue; }
				var sources = inputs.entrySet().stream().map(i -> new PhantomDomainRef("sa.input", i.getKey() + ":" + i.getValue())).toList();
				var constraints = Map.of("sa.list", (long) offer.listId(), "sa.entry", (long) offer.entryId(), "sa.product", (long) product.getId(), "sa.npc", (long) npc.getId(), "sa.npc_object", (long) npc.getObjectId(), "sa.reserve", reserve, "sa.attempts", 0L, org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.ATTEMPT_REVISION, revision);
				return Optional.of(new PhantomGoal(root.goalId(), TYPE, PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef("item.object", Integer.toString(item.getObjectId())), 1, 0, "native.ready_sa", sources, root.selectedAnchor(), TYPE, 750, item.getReferencePrice(), Math.min(player.getAdena() - reserve, Math.multiplyExact(fee, 2)), deadline, constraints, "gear.native.ready_sa", revision, binding.encode()));
			}
		}
		return Optional.empty();
	}

	public static Map<Integer, Long> ingredientCounts(Entry entry)
	{
		var result = new TreeMap<Integer, Long>();
		for (var ingredient : entry.getIngredients())
		{
			if (ingredient.getItemId() <= 0 || ingredient.getItemCount() <= 0 || ingredient.getMaintainIngredient()) { throw new IllegalArgumentException("Unsupported stock SA ingredient."); }
			result.merge(ingredient.getItemId(), ingredient.getItemCount(), Math::addExact);
		}
		return Map.copyOf(result);
	}

	public record Fact(int objectId, int itemId, long count, String location, int slot, int enchant, String effects)
	{
		public Fact(int objectId, int itemId, long count, String location, int slot, int enchant) { this(objectId, itemId, count, location, slot, enchant, ""); }
	}
	private static Map<Integer, Fact> inventory(Player player)
	{
		var result = new TreeMap<Integer, Fact>();
		for (var item : player.getInventory().getItems())
		{
			String effects = item.isAugmented() ? "aug=" + item.getAugmentation().getAugmentationId() : "";
			if (item.getElementals() != null && item.getElementals().length > 0) { effects += Arrays.stream(item.getElementals()).map(e -> e.getElement() + ":" + e.getValue()).sorted().toList(); }
			result.put(item.getObjectId(), new Fact(item.getObjectId(), item.getId(), item.getCount(), item.getItemLocation().name(), item.getLocationSlot(), item.getEnchantLevel(), effects));
		}
		return Map.copyOf(result);
	}
	public record Quote(Spec spec, Player player, Npc npc, int preparedEntry, long epoch, Map<Integer, Long> inputs, Map<Integer, Fact> before, String authority, byte[] payload, List<Reservation> resources) { }

	public static Quote quote(Identity identity, Player player, PhantomGoal goal)
	{
		var spec = parse(goal, true); var binding = Binding.parse(goal);
		if (spec.attempts() != 0 || !binding.matches(player) || player.isAlikeDead() || player.isMoving() || player.isInCombat() || player.isAttackingNow() || player.isCastingNow() || player.isInStoreMode() || player.isInventoryDisabled() || player.isProcessingTransaction() || player.hasPendingOwnedStore()
			|| !(player.getNativeWorkOwner() instanceof org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope owner) || !owner.isCurrent() || !owner.nativeObservationHealthy()) { return null; }
		var base = player.getInventory().getItemByObjectId(spec.objectId()); var object = World.getInstance().findObject(spec.npcObjectId());
		if (base == null || base.getOwnerId() != player.getObjectId() || base.isEquipped() || base.getItemLocation() != ItemLocation.INVENTORY || base.getEnchantLevel() != 0 || PhantomNativeEquipmentPolicy.protectedEffects(base)
			|| !(object instanceof Npc npc) || npc.getId() != spec.npcId() || !npc.isSpawned() || npc.isAlikeDead() || npc.getInstanceId() != player.getInstanceId() || !player.isInsideRadius3D(npc, Npc.INTERACTION_DISTANCE)) { return null; }
		player.setLastFolkNPC(npc); MultisellData.getInstance().separateAndSend(spec.listId(), player, npc, true);
		var list = player.getMultiSell();
		if (list == null || list.getListId() != spec.listId() || !list.getMaintainEnchantment() || !list.isNpcAllowed(npc.getId()) || !list.checkNpcObjectId(npc.getObjectId())) { return null; }
		var entry = list.getEntries().stream().filter(e -> e.getEntryId() == Math.multiplyExact(spec.entryId(), 100000) + base.getEnchantLevel()).findFirst().orElse(null);
		if (entry == null || entry.getProducts().size() != 1 || entry.getProducts().getFirst().getItemId() != spec.productId() || entry.getProducts().getFirst().getItemCount() != 1 || entry.getProducts().getFirst().getEnchantLevel() != 0) { return null; }
		var product = ItemData.getInstance().getTemplate(spec.productId());
		if (!(product instanceof Weapon) || product.getSkills() == null || product.getSkills().length == 0 || !PhantomNativeEquipmentPolicy.upgrade(player, product) || !player.getInventory().validateCapacity(1) || !player.getInventory().validateWeight(product.getWeight())) { return null; }
		var inputs = ingredientCounts(entry);
		if (!inputs.keySet().equals(spec.inputs().keySet()) || !inputs.containsKey(base.getId()) || inputs.get(base.getId()) != 1 || !inputs.entrySet().stream().allMatch(i -> i.getKey() == 57 ? i.getValue() >= spec.inputs().get(57) && i.getValue() <= goal.expenseBudget() : i.getValue().equals(spec.inputs().get(i.getKey()))) || player.getAdena() - inputs.get(57) < spec.reserve()) { return null; }
		var before = inventory(player); var resources = new ArrayList<Reservation>();
		if (player.getInventory().getItems().stream().anyMatch(i -> i.getOwnerId() != player.getObjectId())) { return null; }
		long actualCost = inputs.get(57);
		for (var input : inputs.entrySet()) { if (input.getKey() != 57 && input.getKey() != base.getId()) { actualCost = Math.addExact(actualCost, Math.multiplyExact((long) Math.max(0, ItemData.getInstance().getTemplate(input.getKey()).getReferencePrice()), input.getValue())); } }
		if (actualCost > goal.riskBudget()) { return null; }
		for (var input : inputs.entrySet())
		{
			var actual = before.values().stream().filter(f -> f.itemId() == input.getKey()).toList();
			if (actual.size() != 1 || actual.getFirst().count() < input.getValue() || !actual.getFirst().location().equals("INVENTORY")) { return null; }
			resources.add(new Reservation(identity.profileId(), player.getObjectId(), player.getClassIndex(), input.getKey() == 57 ? ResourceKind.ADENA : ResourceKind.ITEM_COUNT, 0, input.getKey(), input.getValue(), actual.getFirst().count(), 0, "INVENTORY"));
		}
		if (before.values().stream().anyMatch(f -> f.itemId() == spec.productId())) { return null; }
		resources.add(new Reservation(identity.profileId(), player.getObjectId(), player.getClassIndex(), ResourceKind.ITEM_COUNT, 0, spec.productId(), 1, 0, 0, "INVENTORY"));
		resources.add(new Reservation(identity.profileId(), player.getObjectId(), player.getClassIndex(), ResourceKind.CAPACITY, 0, 0, 0, 0, 0, ""));
		String text = new TreeMap<>(before) + "|" + new TreeMap<>(inputs) + "|" + entry.getEntryId() + "|" + npc.getObjectId() + "|" + owner.epoch() + "|" + goal.payloadText();
		byte[] payload = text.getBytes(StandardCharsets.US_ASCII);
		if (payload.length > PhantomEconomyOperation.MAX_PAYLOAD_BYTES) { return null; }
		return new Quote(spec, player, npc, entry.getEntryId(), owner.epoch(), inputs, before, PhantomEconomyOperation.sha256(goal.validSources() + "|" + text), payload, List.copyOf(resources));
	}

	public record Outcome(Result result, int outputObjectId, long consumed, long fee, byte[] consequence) { }
	/** Full object conservation, including unrelated inventory; zero denotes ambiguity. */
	public static int conservedOutput(Map<Integer, Fact> before, Map<Integer, Long> inputs, int productId, Map<Integer, Fact> after)
	{
		var outputs = after.values().stream().filter(f -> f.itemId() == productId && !before.containsKey(f.objectId()) && f.count() == 1 && f.enchant() == 0 && f.location().equals("INVENTORY")).toList();
		if (outputs.size() != 1) { return 0; }
		var expected = new TreeMap<>(before);
		for (var input : inputs.entrySet())
		{
			var facts = before.values().stream().filter(f -> f.itemId() == input.getKey()).toList();
			if (input.getValue() <= 0 || facts.size() != 1 || facts.getFirst().count() < input.getValue()) { return 0; }
			var fact = facts.getFirst(); long remaining = fact.count() - input.getValue();
			if (remaining == 0) { expected.remove(fact.objectId()); } else { expected.put(fact.objectId(), new Fact(fact.objectId(), fact.itemId(), remaining, fact.location(), fact.slot(), fact.enchant(), fact.effects())); }
		}
		var output = outputs.getFirst(); expected.put(output.objectId(), output);
		return expected.equals(after) ? output.objectId() : 0;
	}

	public static Outcome execute(Quote quote)
	{
		var player = quote.player();
		if (player.getNativeWorkOwner() == null || !player.getNativeWorkOwner().isCurrent() || player.getNativeWorkOwner().epoch() != quote.epoch() || !inventory(player).equals(quote.before())) { return new Outcome(Result.STALE_AUTHORITY, 0, 0, 0, new byte[0]); }
		MultiSellChoose.executePreparedExchange(player, quote.spec().listId(), quote.preparedEntry(), 1);
		var after = inventory(player);
		if (after.equals(quote.before())) { return new Outcome(Result.ERROR, 0, 0, 0, new byte[0]); }
		int objectId = conservedOutput(quote.before(), quote.inputs(), quote.spec().productId(), after);
		if (objectId == 0) { return new Outcome(Result.INCONSISTENT, 0, 0, 0, new byte[0]); }
		var output = after.get(objectId); long consumed = quote.inputs().entrySet().stream().filter(i -> i.getKey() != 57).mapToLong(Map.Entry::getValue).reduce(0, Math::addExact);
		player.getInventory().updateDatabase();
		return new Outcome(Result.SUCCESS, output.objectId(), consumed, quote.inputs().get(57), ("SA|" + quote.spec().objectId() + "|" + output.objectId() + "|" + output.itemId() + "|" + quote.inputs()).getBytes(StandardCharsets.US_ASCII));
	}
}

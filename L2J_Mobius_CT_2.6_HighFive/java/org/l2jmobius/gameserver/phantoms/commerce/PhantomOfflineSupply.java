/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;

import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore.Binding;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService.StepResult;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService.StepStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.services.PrivateStoreService;

/** Bounded discovery of published offline supplies; all effects stay in the existing social economy owner. */
public final class PhantomOfflineSupply
{
	private final PhantomMultipartyEconomyService _service;

	public PhantomOfflineSupply(PhantomMultipartyEconomyService service) { _service = java.util.Objects.requireNonNull(service); }

	public Optional<PhantomGoal> offer(Player buyer, PhantomGoal root, Binding binding, long revision,
			List<ItemTemplate> items, long wanted, long reserve, long deadline, long maximumUnitPrice, ToLongFunction<Player> roundTripFee)
	{
		final var choices = new ArrayList<Choice>();
		final var owners = World.getInstance().getVisibleObjectsInRange(buyer, Player.class, 6000).stream()
			.filter(owner -> owner != buyer && offline(owner) && owner.getInstanceId() == buyer.getInstanceId())
			.sorted(Comparator.comparingDouble((Player owner) -> buyer.calculateDistance3D(owner)).thenComparingInt(Player::getObjectId)).limit(16).toList();
		for (var owner : owners)
		{
			final long fees = roundTripFee.applyAsLong(owner);
			final long budget = fees < 0 ? 0 : buyer.getAdena() - reserve - fees;
			if (budget <= 0) { continue; }
			if (owner.getPrivateStoreType() == PrivateStoreType.SELL)
			{
				final String hash = PrivateStoreService.listingHash(owner.getSellList());
				for (var line : owner.getSellList().getItems().stream().limit(64).toList())
				{
					if (line.getPrice() <= 0 || line.getPrice() > maximumUnitPrice || items.stream().noneMatch(item -> item.getId() == line.getItem().getId())) { continue; }
					final long count = Math.min(Math.min(wanted, line.getCount()), budget / line.getPrice());
					if (!L2jPhantomSupplyJourney.mobilePurchase(buyer, line.getItem(), count)) { continue; }
					final long total = Math.multiplyExact(count, line.getPrice());
					final String key = owner.getObjectId() + ";0;" + hash + ";0;" + total + ";B:" + line.getObjectId() + ':' + count + ':' + line.getPrice() + ':' + line.getItem().getId();
					choices.add(new Choice(PhantomSocialEconomyGoalSpec.STORE_BUY_GOAL, key, total, count, buyer.calculateDistance3D(owner)));
				}
			}
			else if (owner.getPrivateStoreType() == PrivateStoreType.MANUFACTURE && owner.getRace() == Race.DWARF)
			{
				for (var listing : owner.getManufactureItems().values().stream().sorted(Comparator.comparingInt(value -> value.getRecipeId())).limit(64).toList())
				{
					final var recipe = RecipeData.getInstance().getRecipeList(listing.getRecipeId());
					if (recipe == null || !recipe.isDwarvenRecipe() || recipe.getCount() <= 0 || recipe.getCount() > 600 || listing.getCost() <= 0 || listing.getCost() > budget || items.stream().noneMatch(item -> item.getId() == recipe.getItemId()) || !owner.hasRecipeList(recipe.getId()) || owner.getSkillLevel(CommonSkill.CREATE_DWARVEN.getId()) < recipe.getLevel()) { continue; }
					if (listing.getCost() / (double) recipe.getCount() > maximumUnitPrice || !L2jPhantomSupplyJourney.mobilePurchase(buyer, org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(recipe.getItemId()), recipe.getCount())) { continue; }
					final var ingredients = new java.util.TreeMap<Integer, Long>();
					for (var ingredient : recipe.getRecipes()) { ingredients.merge(ingredient.getItemId(), (long) ingredient.getQuantity(), Math::addExact); }
					if (ingredients.isEmpty() || ingredients.entrySet().stream().anyMatch(entry -> buyer.getInventory().getInventoryItemCount(entry.getKey(), -1) < entry.getValue())) { continue; }
					final String key = owner.getObjectId() + ";0;" + recipe.getId() + ';' + listing.getCost() + ';' + recipe.getItemId() + ';' + recipe.getCount() + ";1;" + listing.getCost();
					choices.add(new Choice(PhantomSocialEconomyGoalSpec.MANUFACTURE_GOAL, key, listing.getCost(), recipe.getCount(), buyer.calculateDistance3D(owner)));
				}
			}
		}
		return choices.stream().sorted(Comparator.comparingDouble((Choice choice) -> choice.cost / (double) choice.count).thenComparingDouble(Choice::distance).thenComparing(Choice::key)).findFirst().map(choice ->
		{
			final var goal = new PhantomGoal(root.goalId(), choice.type, PhantomGoalStatus.ACTIVE, root.subject(), new PhantomDomainRef(PhantomSocialEconomyGoalSpec.TARGET_NAMESPACE, choice.key), 1, 0, null, List.of(), null, choice.type, 700, 0, choice.cost, deadline, Map.of(), "supplies.offline.low_stock", revision, binding.encode());
			PhantomSocialEconomyGoalSpec.parse(goal);
			return goal;
		});
	}

	public StepResult advance(long profileId, PhantomGoal goal, long generation, long tick)
	{
		final long now = System.currentTimeMillis();
		StepResult result = _service.discoverOrLoad(profileId, goal, now);
		if (result.status() != StepStatus.SUCCESS) { return result; }
		result = _service.offerOrAccept(profileId, goal, PhantomActivityState.ACTIVE, now);
		if (result.status() != StepStatus.SUCCESS) { return result; }
		result = _service.reserve(profileId, goal, generation, tick, now);
		if (result.status() != StepStatus.SUCCESS) { return result; }
		result = _service.dispatch(profileId, goal, now);
		if (result.status() != StepStatus.SUCCESS) { return result; }
		return _service.observeReconcile(profileId, goal, PhantomActivityState.ACTIVE, now);
	}

	public StepResult cancel(long profileId, PhantomGoal goal) { return _service.cancel(profileId, goal, System.currentTimeMillis()); }
	public static boolean offline(Player owner) { return owner != null && owner.isOnline() && owner.getOfflineStartTime() > 0 && (owner.getClient() == null || owner.getClient().isDetached()) && !owner.isAlikeDead(); }
	public static PhantomSocialEconomyGoalSpec spec(PhantomGoal goal)
	{
		return PhantomSocialEconomyGoalSpec.parse(new PhantomGoal(goal.goalId(), goal.goalType(), PhantomGoalStatus.ACTIVE, goal.subject(), goal.target(), 1, 0, null, List.of(), null, goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), goal.deadlineEpochMillis(), Map.of(), goal.reasonKey(), goal.revision(), goal.payloadText()));
	}
	public static boolean returning(PhantomGoal goal) { return PhantomSupplyIntentStore.social(goal) && goal.status() == PhantomGoalStatus.COMPLETED && Binding.parse(goal).retryAfterEpochMillis() == 0; }
	private record Choice(String type, String key, long cost, long count, double distance) { }
}

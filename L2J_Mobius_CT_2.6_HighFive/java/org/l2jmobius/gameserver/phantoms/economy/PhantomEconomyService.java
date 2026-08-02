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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.holders.RecipeHolder;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.managers.RecipeCraftObserver;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enchant.EnchantSupportItem;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction.CraftCommand;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction.CraftQuote;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction.EnchantCommand;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction.EnchantQuote;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction.TransactionResult;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Audit;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Identity;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Kind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Result;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.StoredOperation;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.services.EnchantItemService;
import org.l2jmobius.gameserver.services.EnchantItemService.Event;
import org.l2jmobius.gameserver.services.EnchantItemService.Outcome;

/** Checkpoint 1 admission, active dispatch and background atomic orchestration. */
public final class PhantomEconomyService
{
	private final PhantomEconomyPolicy _policy;
	private final PhantomEconomyReservationService _reservations;
	private final PhantomEconomyBackgroundTransaction _background;
	private final PhantomMaterializationService _materialization;
	private final PhantomAcquisitionStore _acquisition;
	private final PhantomGoalStateStore _goals;
	private final PhantomProfileRepository _profiles;
	private final PhantomBackgroundStateCodec _backgroundCodec = new PhantomBackgroundStateCodec();
	private final LongAdder _committed = new LongAdder();
	private final LongAdder _activeRequired = new LongAdder();
	private final LongAdder _conflicts = new LongAdder();
	private final LongAdder _craftSuccess = new LongAdder();
	private final LongAdder _craftFailure = new LongAdder();
	private final LongAdder _craftRare = new LongAdder();
	private final LongAdder _enchantSuccess = new LongAdder();
	private final LongAdder _enchantSafeFailure = new LongAdder();
	private final LongAdder _enchantBlessedReset = new LongAdder();
	private final LongAdder _enchantDestroyed = new LongAdder();
	private final LongAdder _reconciled = new LongAdder();

	public PhantomEconomyService(PhantomEconomyPolicy policy, PhantomEconomyReservationService reservations, PhantomEconomyBackgroundTransaction background, PhantomMaterializationService materialization, PhantomAcquisitionStore acquisition, PhantomGoalStateStore goals, PhantomProfileRepository profiles)
	{
		_policy = Objects.requireNonNull(policy);
		_reservations = Objects.requireNonNull(reservations);
		_background = Objects.requireNonNull(background);
		_materialization = Objects.requireNonNull(materialization);
		_acquisition = Objects.requireNonNull(acquisition);
		_goals = Objects.requireNonNull(goals);
		_profiles = Objects.requireNonNull(profiles);
	}

	public boolean supports(long profileId, PhantomGoal goal, PhantomActivityState state)
	{
		if ((goal == null) || (goal.status() != PhantomGoalStatus.ACTIVE) || !supportedState(state))
		{
			return false;
		}
		if (PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType()))
		{
			final var stored = _acquisition.load(profileId).orElse(null);
			return (stored != null) && validCraftHandoff(stored.state());
		}
		if (PhantomEnchantGoalSpec.GOAL_TYPE.equals(goal.goalType()))
		{
			try
			{
				PhantomEnchantGoalSpec.parse(goal);
				return true;
			}
			catch (IllegalArgumentException exception)
			{
				return false;
			}
		}
		return false;
	}

	public StepResult reserve(long profileId, PhantomGoal goal, PhantomActivityState state, long activityGeneration, long activityTick, long nowEpochMillis)
	{
		if (!supports(profileId, goal, state))
		{
			return StepResult.replan(Result.STALE_AUTHORITY, "economy.quote.stale");
		}
		final Optional<StoredOperation> active = _reservations.findActive(profileId);
		if (active.isPresent())
		{
			return matches(active.get(), goal, activityGeneration) && (active.get().state() == State.RESERVED) ? StepResult.success(active.get().operationId(), "economy.reserve.idempotent") : StepResult.replan(Result.CONFLICT, "economy.reserve.conflict");
		}
		final PhantomProfile profile = _profiles.find(profileId).orElse(null);
		if ((profile == null) || (profile.characterObjectId() == null))
		{
			return StepResult.replan(Result.STALE_AUTHORITY, "economy.profile.unlinked");
		}
		final Identity identity;
		try
		{
			identity = identity(profile, goal, activityGeneration, activityTick);
		}
		catch (IllegalArgumentException | IllegalStateException exception)
		{
			return StepResult.replan(Result.STALE_AUTHORITY, "economy.attempt.invalid");
		}
		final Quote quote = state.requiresMaterialization() ? quoteActive(identity, goal, nowEpochMillis) : quoteBackground(identity, goal, nowEpochMillis);
		if (quote.operation() == null)
		{
			if (quote.result() == Result.ACTIVE_REQUIRED)
			{
				_activeRequired.increment();
			}
			return quote.result() == Result.ACTIVE_REQUIRED ? StepResult.replan(quote.result(), "economy.active.required") : StepResult.replan(quote.result(), "economy.quote.rejected");
		}
		final PhantomEconomyReservationService.ReserveResult result = _reservations.reserve(quote.operation(), quote.resources());
		return switch (result.status())
		{
			case RESERVED, IDEMPOTENT -> StepResult.success(quote.operation().operationId(), "economy.reserve.complete");
			case ADMISSION_CLOSED -> StepResult.retry(Result.CONFLICT, "economy.admission.closed");
			default -> StepResult.replan(Result.CONFLICT, "economy.reserve.conflict");
		};
	}

	public StepResult dispatch(long profileId, PhantomGoal goal, long activityGeneration, long nowEpochMillis)
	{
		final StoredOperation operation = _reservations.findActive(profileId).orElse(null);
		if ((operation == null) || !matches(operation, goal, activityGeneration))
		{
			return StepResult.replan(Result.STALE_AUTHORITY, "economy.dispatch.stale");
		}
		if (operation.state() == State.DISPATCHING)
		{
			return StepResult.success(operation.operationId(), "economy.dispatch.idempotent");
		}
		if (operation.state() != State.RESERVED)
		{
			return StepResult.replan(Result.CONFLICT, "economy.dispatch.state");
		}
		final var transition = _reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, nowEpochMillis, null);
		return transition.status() == PhantomEconomyReservationService.Status.TRANSITIONED ? StepResult.success(operation.operationId(), "economy.dispatch.ready") : StepResult.replan(Result.CONFLICT, "economy.dispatch.conflict");
	}

	public StepResult reconcile(long profileId, PhantomGoal goal, PhantomActivityState state, long activityGeneration, long nowEpochMillis)
	{
		final StoredOperation operation = _reservations.findActive(profileId).orElse(null);
		if ((operation == null) || !matches(operation, goal, activityGeneration) || (operation.state() != State.DISPATCHING))
		{
			return StepResult.replan(Result.STALE_AUTHORITY, "economy.reconcile.stale");
		}
		final Result result;
		if (state.requiresMaterialization())
		{
			final Identity identity = identity(operation);
			if (operation.kind() == Kind.SELF_CRAFT)
			{
				result = executeActiveCraft(identity, nowEpochMillis).result();
			}
			else
			{
				final PhantomEnchantGoalSpec spec;
				try
				{
					spec = PhantomEnchantGoalSpec.parse(goal);
				}
				catch (IllegalArgumentException exception)
				{
					return failStop(operation, nowEpochMillis, "economy.enchant.stale");
				}
				final List<Reservation> resources = _reservations.findReservations(operation.operationId());
				final Reservation target = resources.stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_OBJECT) && (resource.objectId() == spec.targetObjectId())).findFirst().orElse(null);
				final Reservation scroll = resources.stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_OBJECT) && spec.allowedScrollItemIds().contains(resource.itemId())).findFirst().orElse(null);
				final Reservation support = resources.stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_OBJECT) && spec.allowedSupportItemIds().contains(resource.itemId())).findFirst().orElse(null);
				if ((target == null) || (scroll == null))
				{
					return failStop(operation, nowEpochMillis, "economy.enchant.reservation_stale");
				}
				result = executeActiveEnchant(identity, target.objectId(), scroll.objectId(), support == null ? 0 : support.objectId(), spec.replacementReserve(), nowEpochMillis).result();
			}
		}
		else if (state == PhantomActivityState.BACKGROUND)
		{
			result = reconcileBackground(operation, goal, nowEpochMillis);
		}
		else
		{
			return failStop(operation, nowEpochMillis, "economy.activity.changed");
		}
		final Optional<StoredOperation> after = _reservations.find(operation.operationId());
		if (after.isPresent() && after.get().state().terminal())
		{
			_reconciled.increment();
			return result == Result.INCONSISTENT ? StepResult.replan(result, "economy.reconcile.inconsistent") : StepResult.success(operation.operationId(), "economy.reconcile.complete");
		}
		return failStop(operation, nowEpochMillis, "economy.reconcile.ambiguous");
	}

	private Quote quoteActive(Identity identity, PhantomGoal goal, long nowEpochMillis)
	{
		try (ActionLease lease = _materialization.tryAcquireAction(identity.profileId()).orElse(null))
		{
			if ((lease == null) || (lease.player().getObjectId() != identity.characterObjectId()))
			{
				return Quote.rejected(Result.ACTIVE_REQUIRED);
			}
			final Player player = lease.player();
			if (PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType()))
			{
				final var acquisition = _acquisition.load(identity.profileId()).orElse(null);
				if ((acquisition == null) || !validCraftHandoff(acquisition.state()))
				{
					return Quote.rejected(Result.STALE_AUTHORITY);
				}
				final RecipeList recipe = RecipeData.getInstance().getRecipeList(acquisition.state().recipePlan().recipeListId());
				if ((recipe == null) || PlayerConfig.ALT_GAME_CREATION || !PlayerConfig.IS_CRAFTING_ENABLED || !player.hasRecipeList(recipe.getId()) || (player.getSkillLevel(acquisition.state().recipePlan().craftSkillId()) != acquisition.state().recipePlan().craftSkillLevel()))
				{
					return Quote.rejected(Result.STALE_AUTHORITY);
				}
				final List<Reservation> resources = craftReservations(identity, player, recipe);
				if (resources == null)
				{
					return Quote.rejected(Result.CONFLICT);
				}
				final byte[] before = craftBefore(player, recipe);
				final PhantomEconomyOperation operation = operation(identity, Kind.SELF_CRAFT, PhantomEconomyProjection.craftAuthority(acquisition.state(), recipe, _policy), PhantomEconomyOperation.sha256(identity.intentId() + "|" + recipe.getId() + "|" + java.util.HexFormat.of().formatHex(before)), before, ("recipe=" + recipe.getId()).getBytes(StandardCharsets.US_ASCII), nowEpochMillis);
				return new Quote(operation, resources, Result.SUCCESS);
			}
			final PhantomEnchantGoalSpec spec;
			try
			{
				spec = PhantomEnchantGoalSpec.parse(goal);
			}
			catch (IllegalArgumentException exception)
			{
				return Quote.rejected(Result.STALE_AUTHORITY);
			}
			final Item target = player.getInventory().getItemByObjectId(spec.targetObjectId());
			if (target == null)
			{
				return Quote.rejected(Result.CONFLICT);
			}
			final List<Item> scrolls = player.getInventory().getItems().stream().filter(item -> spec.allowedScrollItemIds().contains(item.getId())).sorted(Comparator.comparingInt(Item::getObjectId)).limit(_policy.limits().scrollCandidates()).toList();
			final List<Item> supports = player.getInventory().getItems().stream().filter(item -> spec.allowedSupportItemIds().contains(item.getId())).sorted(Comparator.comparingInt(Item::getObjectId)).limit(_policy.limits().supportCandidates()).toList();
			for (Item scrollItem : scrolls)
			{
				final List<Item> supportChoices = supports.isEmpty() ? java.util.Collections.singletonList(null) : supports;
				for (Item supportItem : supportChoices)
				{
					final EnchantScroll scroll = EnchantItemData.getInstance().getEnchantScroll(scrollItem);
					final EnchantSupportItem support = supportItem == null ? null : EnchantItemData.getInstance().getSupportItem(supportItem);
					if ((scroll == null) || !scroll.isValid(target, support))
					{
						continue;
					}
					final long expense = Math.addExact(Math.max(0, scrollItem.getReferencePrice()), supportItem == null ? 0 : Math.max(0, supportItem.getReferencePrice()));
					if (!spec.accepts(target.getEnchantLevel(), scrollItem.getId(), supportItem == null ? 0 : supportItem.getId(), expense) || ((spec.expenseUsed() + expense) > goal.expenseBudget()) || (!scroll.isSafe() && !scroll.isBlessed() && (!spec.destructionPermitted() || (spec.replacementReserve() < requiredReplacement(target)))))
					{
						continue;
					}
					final List<Reservation> resources = enchantReservations(identity, player, target, scrollItem, supportItem);
					final byte[] before = (target.getObjectId() + "|" + target.getId() + "|" + target.getEnchantLevel() + "|" + scrollItem.getObjectId() + "|" + scrollItem.getCount() + "|" + (supportItem == null ? 0 : supportItem.getObjectId()) + "|" + (supportItem == null ? 0 : supportItem.getCount())).getBytes(StandardCharsets.US_ASCII);
					final PhantomEconomyOperation operation = operation(identity, Kind.ITEM_ENCHANT, PhantomEconomyProjection.enchantAuthority(target.getTemplate(), target.getEnchantLevel(), scroll, support, _policy), PhantomEconomyOperation.sha256(identity.intentId() + "|" + new String(before, StandardCharsets.US_ASCII)), before, ("target=" + target.getObjectId()).getBytes(StandardCharsets.US_ASCII), nowEpochMillis);
					return new Quote(operation, resources, Result.SUCCESS);
				}
			}
			return Quote.rejected(Result.CONFLICT);
		}
	}

	private Quote quoteBackground(Identity identity, PhantomGoal goal, long nowEpochMillis)
	{
		final StoredBackground stored = loadBackground(identity.profileId());
		if ((stored == null) || (stored.state().identity().characterObjectId() != identity.characterObjectId()) || !stored.state().acceptsBackgroundWork())
		{
			return Quote.rejected(Result.STALE_AUTHORITY);
		}
		if (PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType()))
		{
			final var acquisition = _acquisition.load(identity.profileId()).orElse(null);
			if ((acquisition == null) || !validCraftHandoff(acquisition.state()))
			{
				return Quote.rejected(Result.STALE_AUTHORITY);
			}
			final CraftQuote quote = _background.quoteCraft(stored.state(), acquisition.state());
			if (!quote.executable())
			{
				return Quote.rejected(quote.result());
			}
			final byte[] before = (stored.state().inventory().canonicalHash() + "|" + stored.state().vitals().currentHp() + "|" + stored.state().vitals().currentMp() + "|" + stored.rowVersion()).getBytes(StandardCharsets.US_ASCII);
			final PhantomEconomyOperation operation = operation(identity, Kind.SELF_CRAFT, quote.authorityHash(), PhantomEconomyOperation.sha256(identity.intentId() + "|" + java.util.HexFormat.of().formatHex(before)), before, ("recipe=" + acquisition.state().recipePlan().recipeListId()).getBytes(StandardCharsets.US_ASCII), nowEpochMillis);
			return new Quote(operation, quote.reservations(), quote.result());
		}
		final EnchantQuote quote = _background.quoteEnchant(stored.state(), goal, PhantomEnchantGoalSpec.parse(goal).replacementReserve());
		if (!quote.executable())
		{
			return Quote.rejected(quote.result());
		}
		final byte[] before = (stored.state().inventory().canonicalHash() + "|" + quote.targetObjectId() + "|" + quote.scrollObjectId() + "|" + quote.supportObjectId()).getBytes(StandardCharsets.US_ASCII);
		final PhantomEconomyOperation operation = operation(identity, Kind.ITEM_ENCHANT, quote.authorityHash(), PhantomEconomyOperation.sha256(identity.intentId() + "|" + java.util.HexFormat.of().formatHex(before)), before, ("target=" + quote.targetObjectId()).getBytes(StandardCharsets.US_ASCII), nowEpochMillis);
		return new Quote(operation, quote.reservations(), quote.result());
	}

	public ActiveResult executeActiveCraft(Identity identity, long nowEpochMillis)
	{
		final var storedAcquisition = _acquisition.load(identity.profileId()).orElse(null);
		final var storedGoal = _goals.load(identity.profileId()).orElse(null);
		if ((storedAcquisition == null) || (storedGoal == null) || !matches(identity, storedGoal.goal()) || !validCraftHandoff(storedAcquisition.state()))
		{
			return ActiveResult.rejected(Result.STALE_AUTHORITY);
		}
		final RecipeList recipe = RecipeData.getInstance().getRecipeList(storedAcquisition.state().recipePlan().recipeListId());
		if ((recipe == null) || PlayerConfig.ALT_GAME_CREATION || !PlayerConfig.IS_CRAFTING_ENABLED)
		{
			return ActiveResult.rejected(Result.STALE_AUTHORITY);
		}
		try (ActionLease lease = _materialization.tryAcquireAction(identity.profileId()).orElse(null))
		{
			if (lease == null)
			{
				_activeRequired.increment();
				return ActiveResult.rejected(Result.ACTIVE_REQUIRED);
			}
			final Player player = lease.player();
			if ((player.getObjectId() != identity.characterObjectId()) || !player.hasRecipeList(recipe.getId()) || (player.getSkillLevel(storedAcquisition.state().recipePlan().craftSkillId()) != storedAcquisition.state().recipePlan().craftSkillLevel()))
			{
				return ActiveResult.rejected(Result.STALE_AUTHORITY);
			}
			final List<Reservation> resources = craftReservations(identity, player, recipe);
			if (resources == null)
			{
				return ActiveResult.rejected(Result.CONFLICT);
			}
			final String authority = PhantomEconomyProjection.craftAuthority(storedAcquisition.state(), recipe, _policy);
			final byte[] before = craftBefore(player, recipe);
			final PhantomEconomyOperation operation = operation(identity, Kind.SELF_CRAFT, authority, PhantomEconomyOperation.sha256(identity.intentId() + "|" + recipe.getId() + "|" + java.util.HexFormat.of().formatHex(before)), before, ("recipe=" + recipe.getId()).getBytes(StandardCharsets.US_ASCII), nowEpochMillis);
			final PhantomEconomyReservationService.ReserveResult reservation = _reservations.reserve(operation, resources);
			if ((reservation.status() != PhantomEconomyReservationService.Status.RESERVED) && !((reservation.status() == PhantomEconomyReservationService.Status.IDEMPOTENT) && (reservation.state() == State.DISPATCHING)))
			{
				_conflicts.increment();
				return ActiveResult.rejected(Result.CONFLICT);
			}
			if ((reservation.state() == State.RESERVED) && (_reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, nowEpochMillis, null).status() != PhantomEconomyReservationService.Status.TRANSITIONED))
			{
				return ActiveResult.rejected(Result.CONFLICT);
			}
			final List<RecipeCraftObserver.Event> events = new ArrayList<>();
			RecipeManager.getInstance().requestMakeItem(player, recipe.getId(), events::add);
			final RecipeCraftObserver.Event terminal = events.stream().filter(event -> (event.type() == RecipeCraftObserver.Type.SUCCESS_PRODUCT) || (event.type() == RecipeCraftObserver.Type.RARE_PRODUCT) || (event.type() == RecipeCraftObserver.Type.CRAFT_FAILED) || (event.type() == RecipeCraftObserver.Type.ABORTED)).findFirst().orElse(null);
			if (terminal == null)
			{
				return ActiveResult.rejected(Result.INCONSISTENT);
			}
			final Result result = switch (terminal.type())
			{
				case SUCCESS_PRODUCT, RARE_PRODUCT -> Result.SUCCESS;
				case CRAFT_FAILED -> Result.CRAFT_FAILED;
				case ABORTED -> Result.ERROR;
				default -> throw new IllegalStateException("Unexpected craft terminal event.");
			};
			try
			{
				final long afterCount = player.getInventory().getInventoryItemCount(storedAcquisition.state().targetItemId(), -1);
				final var receipt = new PhantomAcquisitionState.Receipt(operation.operationId(), storedAcquisition.state().selectedSource().sourceId(), ReceiptKind.ACTIVE_SELF_CRAFT, storedAcquisition.state().lastObservedCount(), afterCount, TerminalResult.COMMITTED, Math.max(0, nowEpochMillis / 60000));
				final PhantomAcquisitionState nextAcquisition = storedAcquisition.state().observe(afterCount, result == Result.CRAFT_FAILED ? PhantomAcquisitionState.Status.BLOCKED : PhantomAcquisitionState.Status.READY, Phase.NONE, receipt, Math.max(0, nowEpochMillis / 60000));
				final PhantomGoal nextGoal = PhantomAcquisitionGoalSpec.project(storedGoal.goal(), nextAcquisition.progress(), nextAcquisition.status() == PhantomAcquisitionState.Status.COMPLETED ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.ACTIVE, nextAcquisition.selectedSource());
				_acquisition.mutateWithGoal(identity.profileId(), storedAcquisition.rowVersion(), nextAcquisition, storedGoal.rowVersion(), nextGoal);
				final String reason = result == Result.SUCCESS ? "result.success" : result == Result.CRAFT_FAILED ? "result.craft_failed" : "operation.conflict";
				final byte[] consequence = (terminal.type() + "|" + terminal.items() + "|" + terminal.hpConsumed() + "|" + terminal.mpConsumed()).getBytes(StandardCharsets.US_ASCII);
				final long consumed = events.stream().filter(event -> event.type() == RecipeCraftObserver.Type.INGREDIENTS_CONSUMED).flatMap(event -> event.items().stream()).mapToLong(RecipeCraftObserver.ItemDelta::count).sum();
				final long produced = terminal.items().stream().mapToLong(RecipeCraftObserver.ItemDelta::count).sum();
				_reservations.transition(operation.operationId(), State.DISPATCHING, State.COMMITTED, nowEpochMillis, new Audit(result, reason, consequence, consumed, produced, 0, 0, 0, 0));
				_committed.increment();
				recordResult(result, Kind.SELF_CRAFT, terminal.type() == RecipeCraftObserver.Type.RARE_PRODUCT);
				return new ActiveResult(result, operation.operationId());
			}
			catch (RuntimeException exception)
			{
				_reservations.transition(operation.operationId(), State.DISPATCHING, State.INCONSISTENT, nowEpochMillis, new Audit(Result.INCONSISTENT, "dispatch.ambiguous", new byte[0]));
				return ActiveResult.rejected(Result.INCONSISTENT);
			}
		}
	}

	public ActiveResult executeActiveEnchant(Identity identity, int targetObjectId, int scrollObjectId, int supportObjectId, long replacementEvidence, long nowEpochMillis)
	{
		final var storedGoal = _goals.load(identity.profileId()).orElse(null);
		if ((storedGoal == null) || !matches(identity, storedGoal.goal()))
		{
			return ActiveResult.rejected(Result.STALE_AUTHORITY);
		}
		final PhantomEnchantGoalSpec goal;
		try
		{
			goal = PhantomEnchantGoalSpec.parse(storedGoal.goal());
		}
		catch (IllegalArgumentException exception)
		{
			return ActiveResult.rejected(Result.STALE_AUTHORITY);
		}
		try (ActionLease lease = _materialization.tryAcquireAction(identity.profileId()).orElse(null))
		{
			if (lease == null)
			{
				_activeRequired.increment();
				return ActiveResult.rejected(Result.ACTIVE_REQUIRED);
			}
			final Player player = lease.player();
			final Item target = player.getInventory().getItemByObjectId(targetObjectId);
			final Item scrollItem = player.getInventory().getItemByObjectId(scrollObjectId);
			final Item supportItem = supportObjectId == 0 ? null : player.getInventory().getItemByObjectId(supportObjectId);
			final EnchantScroll scroll = EnchantItemData.getInstance().getEnchantScroll(scrollItem);
			final EnchantSupportItem support = supportItem == null ? null : EnchantItemData.getInstance().getSupportItem(supportItem);
			if ((target == null) || (scroll == null) || ((supportObjectId != 0) && (support == null)) || (goal.targetObjectId() != targetObjectId) || !scroll.isValid(target, support))
			{
				return ActiveResult.rejected(Result.STALE_AUTHORITY);
			}
			final long expense = Math.addExact(Math.max(0, scrollItem.getReferencePrice()), supportItem == null ? 0 : Math.max(0, supportItem.getReferencePrice()));
			if (!goal.accepts(target.getEnchantLevel(), scrollItem.getId(), supportItem == null ? 0 : supportItem.getId(), expense) || ((goal.expenseUsed() + expense) > storedGoal.goal().expenseBudget()) || (!scroll.isSafe() && !scroll.isBlessed() && (!goal.destructionPermitted() || (goal.replacementReserve() < requiredReplacement(target)) || (goal.replacementReserve() > replacementEvidence))))
			{
				return ActiveResult.rejected(Result.CONFLICT);
			}
			final String authority = PhantomEconomyProjection.enchantAuthority(target.getTemplate(), target.getEnchantLevel(), scroll, support, _policy);
			final List<Reservation> resources = enchantReservations(identity, player, target, scrollItem, supportItem);
			final byte[] before = (targetObjectId + "|" + target.getId() + "|" + target.getEnchantLevel() + "|" + scrollObjectId + "|" + scrollItem.getCount() + "|" + supportObjectId + "|" + (supportItem == null ? 0 : supportItem.getCount())).getBytes(StandardCharsets.US_ASCII);
			final PhantomEconomyOperation operation = operation(identity, Kind.ITEM_ENCHANT, authority, PhantomEconomyOperation.sha256(identity.intentId() + "|" + new String(before, StandardCharsets.US_ASCII)), before, ("target=" + targetObjectId).getBytes(StandardCharsets.US_ASCII), nowEpochMillis);
			final PhantomEconomyReservationService.ReserveResult reservation = _reservations.reserve(operation, resources);
			if ((reservation.status() != PhantomEconomyReservationService.Status.RESERVED) && !((reservation.status() == PhantomEconomyReservationService.Status.IDEMPOTENT) && (reservation.state() == State.DISPATCHING)))
			{
				return ActiveResult.rejected(Result.CONFLICT);
			}
			if ((reservation.state() == State.RESERVED) && (_reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, nowEpochMillis, null).status() != PhantomEconomyReservationService.Status.TRANSITIONED))
			{
				return ActiveResult.rejected(Result.CONFLICT);
			}
			final List<Event> events = new ArrayList<>(1);
			final Outcome canonical = EnchantItemService.getInstance().execute(new EnchantItemService.Request(player, targetObjectId, scrollObjectId, supportObjectId, false, events::add));
			final Event event = events.isEmpty() ? null : events.getFirst();
			if ((event == null) || (canonical == Outcome.ERROR))
			{
				_reservations.transition(operation.operationId(), State.DISPATCHING, State.INCONSISTENT, nowEpochMillis, new Audit(Result.INCONSISTENT, "dispatch.ambiguous", new byte[0]));
				return ActiveResult.rejected(Result.INCONSISTENT);
			}
			final Result result = switch (canonical)
			{
				case SUCCESS -> Result.SUCCESS;
				case SAFE_FAILURE -> Result.SAFE_FAILURE;
				case BLESSED_RESET -> Result.BLESSED_RESET;
				case DESTROYED_WITH_CRYSTALS -> Result.DESTROYED_WITH_CRYSTALS;
				case ERROR -> throw new IllegalStateException("Error enchant outcome was not rejected.");
			};
			final String reason = switch (result)
			{
				case SUCCESS -> "result.success";
				case SAFE_FAILURE -> "result.safe_failure";
				case BLESSED_RESET -> "result.blessed_reset";
				case DESTROYED_WITH_CRYSTALS -> "result.destroyed";
				default -> throw new IllegalStateException("Unexpected active enchant result.");
			};
			try
			{
				final PhantomGoal nextGoal = goal.project(storedGoal.goal(), event.afterEnchantLevel(), result != Result.DESTROYED_WITH_CRYSTALS, expense, reason);
				_goals.replace(identity.profileId(), storedGoal.rowVersion(), nextGoal);
				final byte[] consequence = (result + "|" + event.beforeEnchantLevel() + "|" + event.afterEnchantLevel() + "|" + event.crystalItemId() + "|" + event.crystalCount()).getBytes(StandardCharsets.US_ASCII);
				_reservations.transition(operation.operationId(), State.DISPATCHING, State.COMMITTED, nowEpochMillis, new Audit(result, reason, consequence, supportItem == null ? 1 : 2, event.crystalCount(), 0, 0, event.crystalCount(), result == Result.DESTROYED_WITH_CRYSTALS ? 1 : 0));
				_committed.increment();
				recordResult(result, Kind.ITEM_ENCHANT, false);
				return new ActiveResult(result, operation.operationId());
			}
			catch (RuntimeException exception)
			{
				_reservations.transition(operation.operationId(), State.DISPATCHING, State.INCONSISTENT, nowEpochMillis, new Audit(Result.INCONSISTENT, "dispatch.ambiguous", new byte[0]));
				return ActiveResult.rejected(Result.INCONSISTENT);
			}
		}
	}

	public TransactionResult executeBackgroundCraft(PhantomEconomyOperation operation, List<Reservation> resources, CraftCommand command)
	{
		if ((_reservations.reserve(operation, resources).status() != PhantomEconomyReservationService.Status.RESERVED) || (_reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, command.nowEpochMillis(), null).status() != PhantomEconomyReservationService.Status.TRANSITIONED))
		{
			return new TransactionResult(PhantomEconomyBackgroundTransaction.Status.CONFLICT, Result.CONFLICT, null, null, null, false);
		}
		final TransactionResult result = _background.executeCraft(command);
		if (result.status() == PhantomEconomyBackgroundTransaction.Status.COMMITTED)
		{
			_committed.increment();
			recordResult(result.result(), Kind.SELF_CRAFT, result.rareCraft());
		}
		return result;
	}

	public TransactionResult executeBackgroundEnchant(PhantomEconomyOperation operation, List<Reservation> resources, EnchantCommand command)
	{
		if ((_reservations.reserve(operation, resources).status() != PhantomEconomyReservationService.Status.RESERVED) || (_reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, command.nowEpochMillis(), null).status() != PhantomEconomyReservationService.Status.TRANSITIONED))
		{
			return new TransactionResult(PhantomEconomyBackgroundTransaction.Status.CONFLICT, Result.CONFLICT, null, null, null, false);
		}
		final TransactionResult result = _background.executeEnchant(command);
		if (result.status() == PhantomEconomyBackgroundTransaction.Status.COMMITTED)
		{
			_committed.increment();
			recordResult(result.result(), Kind.ITEM_ENCHANT, false);
		}
		return result;
	}

	private Result reconcileBackground(StoredOperation operation, PhantomGoal expectedGoal, long nowEpochMillis)
	{
		final StoredBackground background = loadBackground(operation.profileId());
		final var storedGoal = _goals.load(operation.profileId()).orElse(null);
		if ((background == null) || (storedGoal == null) || (storedGoal.goal().goalId() != expectedGoal.goalId()) || (storedGoal.goal().revision() != expectedGoal.revision()))
		{
			return Result.STALE_AUTHORITY;
		}
		final TransactionResult result;
		if (operation.kind() == Kind.SELF_CRAFT)
		{
			final var acquisition = _acquisition.load(operation.profileId()).orElse(null);
			if ((acquisition == null) || !validCraftHandoff(acquisition.state()))
			{
				return Result.STALE_AUTHORITY;
			}
			result = _background.executeCraft(new CraftCommand(operation.operationId(), background.state(), background.rowVersion(), acquisition.state(), acquisition.rowVersion(), storedGoal.goal(), storedGoal.rowVersion(), Math.max(0, nowEpochMillis / 60000), nowEpochMillis));
		}
		else
		{
			final PhantomEnchantGoalSpec spec;
			try
			{
				spec = PhantomEnchantGoalSpec.parse(storedGoal.goal());
			}
			catch (IllegalArgumentException exception)
			{
				return Result.STALE_AUTHORITY;
			}
			final List<Reservation> resources = _reservations.findReservations(operation.operationId());
			final Reservation target = resources.stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_OBJECT) && (resource.objectId() == spec.targetObjectId())).findFirst().orElse(null);
			final Reservation scroll = resources.stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_OBJECT) && spec.allowedScrollItemIds().contains(resource.itemId())).findFirst().orElse(null);
			final Reservation support = resources.stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_OBJECT) && spec.allowedSupportItemIds().contains(resource.itemId())).findFirst().orElse(null);
			if ((target == null) || (scroll == null))
			{
				return Result.STALE_AUTHORITY;
			}
			result = _background.executeEnchant(new EnchantCommand(operation.operationId(), background.state(), background.rowVersion(), storedGoal.goal(), storedGoal.rowVersion(), target.objectId(), target.itemId(), scroll.objectId(), scroll.itemId(), support == null ? 0 : support.objectId(), support == null ? 0 : support.itemId(), spec.replacementReserve(), nowEpochMillis));
		}
		if (result.status() == PhantomEconomyBackgroundTransaction.Status.COMMITTED)
		{
			_committed.increment();
			recordResult(result.result(), operation.kind(), result.rareCraft());
			return result.result();
		}
		return result.status() == PhantomEconomyBackgroundTransaction.Status.CONFLICT ? Result.CONFLICT : Result.INCONSISTENT;
	}

	private StepResult failStop(StoredOperation operation, long nowEpochMillis, String reason)
	{
		final Optional<StoredOperation> current = _reservations.find(operation.operationId());
		if (current.isPresent() && ((current.get().state() == State.DISPATCHING) || (current.get().state() == State.OBSERVING)))
		{
			_reservations.reconcile(operation.operationId(), PhantomEconomyReservationService.Evidence.AMBIGUOUS, nowEpochMillis, null);
		}
		return StepResult.replan(Result.INCONSISTENT, reason);
	}

	private StoredBackground loadBackground(long profileId)
	{
		final PhantomProfileComponent component = _profiles.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).orElse(null);
		if ((component == null) || (component.componentSchemaVersion() != PhantomBackgroundState.SCHEMA_VERSION))
		{
			return null;
		}
		try
		{
			return new StoredBackground(_backgroundCodec.decode(component.payload()), component.rowVersion());
		}
		catch (IllegalArgumentException exception)
		{
			return null;
		}
	}

	private Identity identity(PhantomProfile profile, PhantomGoal goal, long activityGeneration, long activityTick)
	{
		final int attempt;
		if (PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType()))
		{
			attempt = _reservations.nextAttempt(profile.profileId(), goal.goalId(), Kind.SELF_CRAFT, 32);
		}
		else
		{
			attempt = Math.addExact(PhantomEnchantGoalSpec.parse(goal).attemptsUsed(), 1);
		}
		return new Identity(profile.profileId(), profile.characterObjectId(), goal.goalId(), goal.revision(), attempt, "economy:" + goal.goalId() + ":" + goal.revision() + ":" + attempt, activityGeneration, activityTick);
	}

	private static Identity identity(StoredOperation operation)
	{
		return new Identity(operation.profileId(), operation.characterObjectId(), operation.goalId(), operation.goalRevision(), operation.attempt(), operation.intentId(), operation.activityGeneration(), operation.activityTick());
	}

	private static boolean matches(StoredOperation operation, PhantomGoal goal, long activityGeneration)
	{
		return (operation.goalId() == goal.goalId()) && (operation.goalRevision() == goal.revision()) && (operation.activityGeneration() == activityGeneration) && ((PhantomAcquisitionGoalSpec.GOAL_TYPE.equals(goal.goalType()) && (operation.kind() == Kind.SELF_CRAFT)) || (PhantomEnchantGoalSpec.GOAL_TYPE.equals(goal.goalType()) && (operation.kind() == Kind.ITEM_ENCHANT)));
	}

	private static boolean supportedState(PhantomActivityState state)
	{
		return (state == PhantomActivityState.ACTIVE) || (state == PhantomActivityState.NEARBY_PERCEPTIBLE) || (state == PhantomActivityState.BACKGROUND);
	}

	private long requiredReplacement(Item target)
	{
		return Math.floorDiv(Math.addExact(Math.multiplyExact((long) Math.max(0, target.getReferencePrice()), _policy.risk().replacementReservePercent()), 99), 100);
	}

	private void recordResult(Result result, Kind kind, boolean rareCraft)
	{
		switch (result)
		{
			case SUCCESS ->
			{
				if (kind == Kind.SELF_CRAFT)
				{
					_craftSuccess.increment();
					if (rareCraft)
					{
						_craftRare.increment();
					}
				}
				else
				{
					_enchantSuccess.increment();
				}
			}
			case CRAFT_FAILED -> _craftFailure.increment();
			case SAFE_FAILURE -> _enchantSafeFailure.increment();
			case BLESSED_RESET -> _enchantBlessedReset.increment();
			case DESTROYED_WITH_CRYSTALS -> _enchantDestroyed.increment();
			default ->
			{
			}
		}
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_committed.sum(), _activeRequired.sum(), _conflicts.sum(), _craftSuccess.sum(), _craftFailure.sum(), _craftRare.sum(), _enchantSuccess.sum(), _enchantSafeFailure.sum(), _enchantBlessedReset.sum(), _enchantDestroyed.sum(), _reconciled.sum(), _reservations.snapshot());
	}

	private PhantomEconomyOperation operation(Identity identity, Kind kind, String authority, String intentHash, byte[] before, byte[] intent, long now)
	{
		return new PhantomEconomyOperation(identity, kind, State.PREPARED, authority, intentHash, before, intent, now, now, Math.addExact(now, _policy.limits().reservationTtlSeconds() * 1000L), 0);
	}

	private static boolean matches(Identity identity, PhantomGoal goal)
	{
		return (identity.goalId() == goal.goalId()) && (identity.goalRevision() == goal.revision());
	}

	private static boolean validCraftHandoff(PhantomAcquisitionState acquisition)
	{
		return (acquisition.status() == PhantomAcquisitionState.Status.PLANNING_ONLY) && (acquisition.selectedSource() != null) && (acquisition.selectedSource().method() == Method.RECIPE_PREPARATION) && (acquisition.recipePlan() != null) && acquisition.recipePlan().deficits().isEmpty() && acquisition.receipts().isEmpty() && (acquisition.progress() == 0);
	}

	private static List<Reservation> craftReservations(Identity identity, Player player, RecipeList recipe)
	{
		final List<Reservation> result = new ArrayList<>();
		final Map<Integer, Long> ingredientCounts = new java.util.TreeMap<>();
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			ingredientCounts.merge(ingredient.getItemId(), (long) ingredient.getQuantity(), Math::addExact);
		}
		for (Map.Entry<Integer, Long> ingredient : ingredientCounts.entrySet())
		{
			final long count = player.getInventory().getInventoryItemCount(ingredient.getKey(), -1);
			if (count < ingredient.getValue())
			{
				return null;
			}
			result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.ITEM_COUNT, 0, ingredient.getKey(), ingredient.getValue(), count, 0, "INVENTORY"));
		}
		result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.RECIPE, 0, recipe.getId(), 0, 0, 0, ""));
		result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.SKILL, 0, recipe.isDwarvenRecipe() ? org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId() : org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON.getId(), 0, 0, 0, ""));
		result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.CAPACITY, 0, 0, 0, 0, 0, ""));
		return result;
	}

	private static byte[] craftBefore(Player player, RecipeList recipe)
	{
		final StringBuilder value = new StringBuilder(recipe.getId()).append('|').append(player.getCurrentHp()).append('|').append(player.getCurrentMp());
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			value.append('|').append(ingredient.getItemId()).append(':').append(player.getInventory().getInventoryItemCount(ingredient.getItemId(), -1));
		}
		return value.toString().getBytes(StandardCharsets.US_ASCII);
	}

	private static List<Reservation> enchantReservations(Identity identity, Player player, Item target, Item scroll, Item support)
	{
		final List<Reservation> result = new ArrayList<>();
		result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.ITEM_OBJECT, target.getObjectId(), target.getId(), 0, target.getCount(), target.getEnchantLevel(), target.getItemLocation().name()));
		result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.ITEM_OBJECT, scroll.getObjectId(), scroll.getId(), 0, scroll.getCount(), scroll.getEnchantLevel(), scroll.getItemLocation().name()));
		if (support != null)
		{
			result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.ITEM_OBJECT, support.getObjectId(), support.getId(), 0, support.getCount(), support.getEnchantLevel(), support.getItemLocation().name()));
		}
		final int crystalItemId = target.getTemplate().getCrystalItemId();
		if (crystalItemId != 0)
		{
			result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.ITEM_COUNT, 0, crystalItemId, 1, player.getInventory().getInventoryItemCount(crystalItemId, -1), 0, "INVENTORY"));
			result.add(new Reservation(identity.profileId(), identity.characterObjectId(), player.getClassIndex(), ResourceKind.CAPACITY, 0, 0, 0, 0, 0, ""));
		}
		return result;
	}

	public record ActiveResult(Result result, String operationId)
	{
		public static ActiveResult rejected(Result result)
		{
			return new ActiveResult(result, null);
		}
	}

	public enum StepStatus
	{
		SUCCESS,
		RETRY,
		REPLAN
	}

	public record StepResult(StepStatus status, Result result, String operationId, String reason)
	{
		public static StepResult success(String operationId, String reason)
		{
			return new StepResult(StepStatus.SUCCESS, Result.SUCCESS, operationId, reason);
		}

		public static StepResult retry(Result result, String reason)
		{
			return new StepResult(StepStatus.RETRY, result, null, reason);
		}

		public static StepResult replan(Result result, String reason)
		{
			return new StepResult(StepStatus.REPLAN, result, null, reason);
		}
	}

	private record Quote(PhantomEconomyOperation operation, List<Reservation> resources, Result result)
	{
		private static Quote rejected(Result result)
		{
			return new Quote(null, List.of(), result);
		}
	}

	private record StoredBackground(PhantomBackgroundState state, long rowVersion)
	{
	}

	public record Snapshot(long committed, long activeRequired, long conflicts, long craftSuccess, long craftFailure, long craftRare, long enchantSuccess, long enchantSafeFailure, long enchantBlessedReset, long enchantDestroyed, long reconciled, PhantomEconomyReservationService.Snapshot reservations)
	{
	}
}

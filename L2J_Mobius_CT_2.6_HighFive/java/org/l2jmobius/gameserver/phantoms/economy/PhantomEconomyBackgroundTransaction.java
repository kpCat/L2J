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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.ObjectIdAllocator;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Audit;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Kind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Result;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection.CraftOutcome;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection.CraftRequest;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection.EnchantOutcome;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection.EnchantRequest;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.DispatchLock;

/** Atomic background craft/enchant writer over current canonical High Five rows. */
public final class PhantomEconomyBackgroundTransaction
{
	private static final int QUERY_TIMEOUT_SECONDS = 5;
	private final ConnectionProvider _connections;
	private final ObjectIdAllocator _ids;
	private final PhantomEconomyReservationService _reservations;
	private final PhantomEconomyPolicy _policy;
	private final FaultInjector _faultInjector;
	private final PhantomBackgroundStateCodec _backgroundCodec = new PhantomBackgroundStateCodec();
	private final PhantomGoalStateCodec _goalCodec = new PhantomGoalStateCodec();
	private final PhantomAcquisitionStateCodec _acquisitionCodec = new PhantomAcquisitionStateCodec();

	public PhantomEconomyBackgroundTransaction(PhantomEconomyReservationService reservations, PhantomEconomyPolicy policy)
	{
		this(DatabaseFactory::getConnection, ObjectIdAllocator.production(), reservations, policy);
	}

	public PhantomEconomyBackgroundTransaction(ConnectionProvider connections, ObjectIdAllocator ids, PhantomEconomyReservationService reservations, PhantomEconomyPolicy policy)
	{
		this(connections, ids, reservations, policy, FaultInjector.none());
	}

	public PhantomEconomyBackgroundTransaction(ConnectionProvider connections, ObjectIdAllocator ids, PhantomEconomyReservationService reservations, PhantomEconomyPolicy policy, FaultInjector faultInjector)
	{
		_connections = Objects.requireNonNull(connections);
		_ids = Objects.requireNonNull(ids);
		_reservations = Objects.requireNonNull(reservations);
		_policy = Objects.requireNonNull(policy);
		_faultInjector = Objects.requireNonNull(faultInjector);
	}

	public TransactionResult executeCraft(CraftCommand command)
	{
		Objects.requireNonNull(command);
		final List<Integer> reservedIds = new ArrayList<>();
		final List<Integer> releasedIds = new ArrayList<>();
		boolean committed = false;
		boolean completionPublished = false;
		DispatchLock committedDispatch = null;
		TransactionResult committedResult = null;
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				final long profileId = command.background().identity().profileId();
				lockProfile(connection, profileId, command.background().identity().characterObjectId());
				_faultInjector.inject(FaultPoint.AFTER_PROFILE_LOCK);
				final DispatchLock dispatch = _reservations.lockDispatchInTransaction(connection, command.operationId(), profileId);
				requireOperation(dispatch, Kind.SELF_CRAFT, command.goal());
				_faultInjector.inject(FaultPoint.AFTER_DISPATCH_LOCK);
				final Component acquisitionComponent = lockComponent(connection, profileId, PhantomAcquisitionState.COMPONENT_TYPE);
				final Component backgroundComponent = lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE);
				final Component goalComponent = lockComponent(connection, profileId, PhantomGoalStateStore.COMPONENT_TYPE);
				_faultInjector.inject(FaultPoint.AFTER_COMPONENT_LOCKS);
				validateComponent(acquisitionComponent, PhantomAcquisitionState.SCHEMA_VERSION, command.acquisitionRowVersion(), _acquisitionCodec.encode(command.acquisition()));
				validateComponent(backgroundComponent, PhantomBackgroundState.SCHEMA_VERSION, command.backgroundRowVersion(), _backgroundCodec.encode(command.background()));
				validateComponent(goalComponent, PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION, command.goalRowVersion(), _goalCodec.encode(command.goal()));
				if (!command.background().acceptsBackgroundWork())
				{
					throw new Conflict("Background state is not ready for craft.");
				}

				final CharacterFacts character = lockCharacter(connection, command.background());
				final RecipeList recipe = RecipeData.getInstance().getRecipeList(command.acquisition().recipePlan().recipeListId());
				if (recipe == null)
				{
					throw new Conflict("Craft recipe authority is missing.");
				}
				final boolean knownRecipe = lockKnownRecipe(connection, command.background(), recipe);
				final int craftSkillLevel = lockSkill(connection, command.background(), command.acquisition().recipePlan().craftSkillId());
				_faultInjector.inject(FaultPoint.AFTER_CHARACTER_RECIPE_SKILL_LOCKS);
				final List<ItemRow> items = lockItems(connection, command.background().identity().characterObjectId());
				_faultInjector.inject(FaultPoint.AFTER_ITEM_LOCKS);
				validateBackgroundInventory(command.background(), items);
				final CraftOutcome outcome = PhantomEconomyProjection.craft(new CraftRequest(command.acquisition(), recipe, knownRecipe, craftSkillLevel, itemCounts(items), character.currentHp(), character.currentMp(), command.background().clock().rngState(), _policy));
				if (!outcome.executable() || !dispatch.operation().authorityHash().equals(outcome.authorityHash()))
				{
					throw new Conflict("Craft authority or resources changed before dispatch.");
				}
				requireCraftReservations(dispatch, command.background(), recipe);
				final List<ItemRow> nextRows = applyCountDeltas(connection, command.background().identity().characterObjectId(), items, outcome.itemDeltas(), reservedIds, releasedIds);
				_faultInjector.inject(FaultPoint.AFTER_ITEM_WRITES);
				final Vitals nextVitals = new Vitals(character.currentHp() - outcome.hpConsumed(), command.background().vitals().maximumHp(), character.currentMp() - outcome.mpConsumed(), command.background().vitals().maximumMp(), character.currentCp(), command.background().vitals().maximumCp());
				updateVitals(connection, command.background(), nextVitals);
				_faultInjector.inject(FaultPoint.AFTER_VITAL_WRITES);
				final InventoryFacts nextInventory = inventoryFacts(command.background().inventory(), nextRows, outcome.itemDeltas().keySet());
				final Receipt backgroundReceipt = new Receipt(command.operationId(), dispatch.operation().activityGeneration(), dispatch.operation().activityTick(), dispatch.operation().intentHash());
				final PhantomBackgroundState nextBackground = command.background().after(command.background().progress(), nextVitals, command.background().position(), nextInventory, command.background().autoGetSkills(), new Clock(outcome.nextRngState(), command.background().clock().residualTravelMillis(), command.background().clock().residualEncounterMillis()), backgroundReceipt);
				final long afterProduct = nextInventory.itemCount(command.acquisition().targetItemId());
				final boolean targetAttributed = (outcome.result() == Result.SUCCESS) && (outcome.productItemId() == command.acquisition().targetItemId()) && (outcome.productCount() > 0);
				final String sourceFailure = outcome.result() == Result.CRAFT_FAILED ? "craft.canonical_failure" : targetAttributed ? "" : "craft.target_not_produced";
				if (!targetAttributed && (afterProduct != command.acquisition().lastObservedCount()))
				{
					throw new Conflict("Craft without target attribution changed the target count.");
				}
				final org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Receipt acquisitionReceipt = new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Receipt(command.operationId(), command.acquisition().selectedSource().sourceId(), ReceiptKind.BACKGROUND_SELF_CRAFT, command.acquisition().lastObservedCount(), afterProduct, targetAttributed ? TerminalResult.COMMITTED : TerminalResult.FAILED, command.logicalMinute());
				PhantomAcquisitionState nextAcquisition = command.acquisition().observe(afterProduct, PhantomAcquisitionState.Status.PLANNING_ONLY, Phase.NONE, acquisitionReceipt, command.logicalMinute());
				if (!sourceFailure.isEmpty())
				{
					nextAcquisition = nextAcquisition.failSource(sourceFailure, command.logicalMinute());
				}
				final PhantomGoal nextGoal = PhantomAcquisitionGoalSpec.project(command.goal(), nextAcquisition.progress(), nextAcquisition.status() == PhantomAcquisitionState.Status.COMPLETED ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.ACTIVE, nextAcquisition.selectedSource());
				writeComponent(connection, backgroundComponent, profileId, PhantomBackgroundState.COMPONENT_TYPE, PhantomBackgroundState.SCHEMA_VERSION, _backgroundCodec.encode(nextBackground));
				_faultInjector.inject(FaultPoint.AFTER_BACKGROUND_WRITE);
				writeComponent(connection, acquisitionComponent, profileId, PhantomAcquisitionState.COMPONENT_TYPE, PhantomAcquisitionState.SCHEMA_VERSION, _acquisitionCodec.encode(nextAcquisition));
				writeComponent(connection, goalComponent, profileId, PhantomGoalStateStore.COMPONENT_TYPE, PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION, _goalCodec.encode(nextGoal));
				_faultInjector.inject(FaultPoint.AFTER_ACQUISITION_OR_GOAL_WRITE);
				final String reason = outcome.result() == Result.SUCCESS ? targetAttributed ? "result.success" : sourceFailure : "result.craft_failed";
				final long consumed = outcome.itemDeltas().values().stream().filter(value -> value < 0).mapToLong(value -> -value).sum();
				final long produced = outcome.itemDeltas().values().stream().filter(value -> value > 0).mapToLong(Long::longValue).sum();
				_reservations.commitDispatchInTransaction(connection, dispatch, new Audit(outcome.result(), reason, consequence("result=" + outcome.result(), "productItemId=" + outcome.productItemId(), "productCount=" + outcome.productCount(), "rare=" + outcome.rare(), "sourceFailure=" + sourceFailure, "hpConsumed=" + outcome.hpConsumed(), "mpConsumed=" + outcome.mpConsumed()), consumed, produced, 0, 0, 0, 0), command.nowEpochMillis());
				_faultInjector.inject(FaultPoint.AFTER_OPERATION_AUDIT_WRITE);
				committedDispatch = dispatch;
				committedResult = new TransactionResult(Status.COMMITTED, outcome.result(), nextBackground, nextAcquisition, nextGoal, outcome.rare());
				_faultInjector.inject(FaultPoint.BEFORE_COMMIT);
				connection.commit();
				committed = true;
				_faultInjector.inject(FaultPoint.AFTER_COMMIT);
				_reservations.dispatchCommitted(dispatch.canonicalResourceKeys().size());
				releasedIds.forEach(_ids::release);
				completionPublished = true;
				return committedResult;
			}
			catch (Throwable failure)
			{
				if (committed)
				{
					_reservations.dispatchCommitted(committedDispatch.canonicalResourceKeys().size());
					releasedIds.forEach(_ids::release);
					completionPublished = true;
					return committedResult;
				}
				rollback(connection, failure);
				reservedIds.forEach(_ids::release);
				return failed(failure);
			}
		}
		catch (SQLException exception)
		{
			if (committed)
			{
				if (!completionPublished)
				{
					_reservations.dispatchCommitted(committedDispatch.canonicalResourceKeys().size());
					releasedIds.forEach(_ids::release);
				}
				return committedResult;
			}
			else
			{
				reservedIds.forEach(_ids::release);
			}
			return failed(exception);
		}
	}

	public CraftQuote quoteCraft(PhantomBackgroundState background, PhantomAcquisitionState acquisition)
	{
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				lockProfile(connection, background.identity().profileId(), background.identity().characterObjectId());
				final CharacterFacts character = lockCharacter(connection, background);
				final RecipeList recipe = acquisition.recipePlan() == null ? null : RecipeData.getInstance().getRecipeList(acquisition.recipePlan().recipeListId());
				if (recipe == null)
				{
					connection.rollback();
					return CraftQuote.rejected(Result.STALE_AUTHORITY);
				}
				final boolean known = lockKnownRecipe(connection, background, recipe);
				final int skill = lockSkill(connection, background, acquisition.recipePlan().craftSkillId());
				final List<ItemRow> items = lockItems(connection, background.identity().characterObjectId());
				validateBackgroundInventory(background, items);
				final CraftOutcome outcome = PhantomEconomyProjection.craft(new CraftRequest(acquisition, recipe, known, skill, itemCounts(items), character.currentHp(), character.currentMp(), background.clock().rngState(), _policy));
				if (!outcome.executable())
				{
					connection.rollback();
					return CraftQuote.rejected(outcome.result());
				}
				final Map<Integer, Long> counts = itemCounts(items);
				final List<Reservation> resources = new ArrayList<>();
				final Map<Integer, Long> ingredientCounts = new java.util.TreeMap<>();
				for (org.l2jmobius.gameserver.data.holders.RecipeHolder ingredient : recipe.getRecipes())
				{
					ingredientCounts.merge(ingredient.getItemId(), (long) ingredient.getQuantity(), Math::addExact);
				}
				for (Map.Entry<Integer, Long> ingredient : ingredientCounts.entrySet())
				{
					resources.add(new Reservation(background.identity().profileId(), background.identity().characterObjectId(), background.identity().classIndex(), ResourceKind.ITEM_COUNT, 0, ingredient.getKey(), ingredient.getValue(), counts.getOrDefault(ingredient.getKey(), 0L), 0, "INVENTORY"));
				}
				resources.add(reservation(background, ResourceKind.RECIPE, 0, recipe.getId()));
				resources.add(reservation(background, ResourceKind.SKILL, 0, acquisition.recipePlan().craftSkillId()));
				resources.add(reservation(background, ResourceKind.CAPACITY, 0, 0));
				connection.rollback();
				return new CraftQuote(outcome.result(), outcome.authorityHash(), List.copyOf(resources));
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return CraftQuote.rejected(failure instanceof Conflict ? Result.CONFLICT : Result.ERROR);
			}
		}
		catch (SQLException exception)
		{
			return CraftQuote.rejected(Result.ERROR);
		}
	}

	public TransactionResult executeEnchant(EnchantCommand command)
	{
		Objects.requireNonNull(command);
		final List<Integer> reservedIds = new ArrayList<>();
		final List<Integer> releasedIds = new ArrayList<>();
		boolean committed = false;
		boolean completionPublished = false;
		DispatchLock committedDispatch = null;
		TransactionResult committedResult = null;
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				final long profileId = command.background().identity().profileId();
				lockProfile(connection, profileId, command.background().identity().characterObjectId());
				_faultInjector.inject(FaultPoint.AFTER_PROFILE_LOCK);
				final DispatchLock dispatch = _reservations.lockDispatchInTransaction(connection, command.operationId(), profileId);
				requireOperation(dispatch, Kind.ITEM_ENCHANT, command.goal());
				_faultInjector.inject(FaultPoint.AFTER_DISPATCH_LOCK);
				final Component backgroundComponent = lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE);
				final Component goalComponent = lockComponent(connection, profileId, PhantomGoalStateStore.COMPONENT_TYPE);
				_faultInjector.inject(FaultPoint.AFTER_COMPONENT_LOCKS);
				validateComponent(backgroundComponent, PhantomBackgroundState.SCHEMA_VERSION, command.backgroundRowVersion(), _backgroundCodec.encode(command.background()));
				validateComponent(goalComponent, PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION, command.goalRowVersion(), _goalCodec.encode(command.goal()));
				if (!command.background().acceptsBackgroundWork())
				{
					throw new Conflict("Background state is not ready for enchant.");
				}
				lockCharacter(connection, command.background());
				_faultInjector.inject(FaultPoint.AFTER_CHARACTER_RECIPE_SKILL_LOCKS);
				final List<ItemRow> items = lockItems(connection, command.background().identity().characterObjectId());
				_faultInjector.inject(FaultPoint.AFTER_ITEM_LOCKS);
				validateBackgroundInventory(command.background(), items);
				final ItemRow target = exactItem(items, command.targetObjectId(), command.targetItemId());
				final ItemRow scroll = exactItem(items, command.scrollObjectId(), command.scrollItemId());
				final ItemRow support = command.supportObjectId() == 0 ? null : exactItem(items, command.supportObjectId(), command.supportItemId());
				final ItemTemplate targetTemplate = ItemData.getInstance().getTemplate(target.itemId());
				final boolean augmented = hasRow(connection, "SELECT 1 FROM item_attributes WHERE itemId=? LIMIT 1 FOR UPDATE", target.objectId());
				final boolean elemented = hasRow(connection, "SELECT 1 FROM item_elementals WHERE itemId=? ORDER BY elemType LIMIT 1 FOR UPDATE", target.objectId());
				final PhantomEnchantGoalSpec goal = PhantomEnchantGoalSpec.parse(command.goal());
				final EnchantOutcome outcome = PhantomEconomyProjection.enchant(new EnchantRequest(goal, targetTemplate, target.objectId(), target.enchantLevel(), target.location(), scroll.objectId(), scroll.itemId(), support == null ? 0 : support.objectId(), support == null ? 0 : support.itemId(), augmented, elemented, (target.time() > 0) || (target.timeOfUse() > 0), (target.customType1() != 0) || (target.customType2() != 0) || (target.manaLeft() >= 0), command.replacementEvidence(), command.goal().expenseBudget(), command.background().clock().rngState(), _policy));
				if (!outcome.executable() || !dispatch.operation().authorityHash().equals(outcome.authorityHash()))
				{
					throw new Conflict("Enchant authority, risk or resources changed before dispatch.");
				}
				requireEnchantReservations(dispatch, command.background(), target, scroll, support, outcome);
				final List<ItemRow> nextRows = applyEnchant(connection, command.background().identity().characterObjectId(), items, target, scroll, support, outcome, reservedIds, releasedIds);
				_faultInjector.inject(FaultPoint.AFTER_ITEM_WRITES);
				_faultInjector.inject(FaultPoint.AFTER_VITAL_WRITES);
				final TreeSet<Integer> changedItemIds = new TreeSet<>(Set.of(target.itemId(), scroll.itemId()));
				if (outcome.crystalItemId() != 0)
				{
					changedItemIds.add(outcome.crystalItemId());
				}
				final InventoryFacts nextInventory = inventoryFacts(command.background().inventory(), nextRows, changedItemIds);
				final Receipt receipt = new Receipt(command.operationId(), dispatch.operation().activityGeneration(), dispatch.operation().activityTick(), dispatch.operation().intentHash());
				final PhantomBackgroundState nextBackground = command.background().after(command.background().progress(), command.background().vitals(), command.background().position(), nextInventory, command.background().autoGetSkills(), new Clock(outcome.nextRngState(), command.background().clock().residualTravelMillis(), command.background().clock().residualEncounterMillis()), receipt);
				final String reason = switch (outcome.result())
				{
					case SUCCESS -> "result.success";
					case SAFE_FAILURE -> "result.safe_failure";
					case BLESSED_RESET -> "result.blessed_reset";
					case DESTROYED_WITH_CRYSTALS -> "result.destroyed";
					default -> throw new Conflict("Unsupported enchant terminal result.");
				};
				final PhantomGoal nextGoal = goal.project(command.goal(), outcome.nextEnchantLevel(), outcome.targetSurvives(), outcome.expense(), reason);
				writeComponent(connection, backgroundComponent, profileId, PhantomBackgroundState.COMPONENT_TYPE, PhantomBackgroundState.SCHEMA_VERSION, _backgroundCodec.encode(nextBackground));
				_faultInjector.inject(FaultPoint.AFTER_BACKGROUND_WRITE);
				writeComponent(connection, goalComponent, profileId, PhantomGoalStateStore.COMPONENT_TYPE, PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION, _goalCodec.encode(nextGoal));
				_faultInjector.inject(FaultPoint.AFTER_ACQUISITION_OR_GOAL_WRITE);
				_reservations.commitDispatchInTransaction(connection, dispatch, new Audit(outcome.result(), reason, consequence(outcome.result(), target.objectId(), outcome.nextEnchantLevel(), outcome.crystalItemId(), outcome.crystalCount()), support == null ? 1 : 2, outcome.crystalCount(), 0, 0, outcome.crystalCount(), outcome.targetSurvives() ? 0 : 1), command.nowEpochMillis());
				_faultInjector.inject(FaultPoint.AFTER_OPERATION_AUDIT_WRITE);
				committedDispatch = dispatch;
				committedResult = new TransactionResult(Status.COMMITTED, outcome.result(), nextBackground, null, nextGoal, false);
				_faultInjector.inject(FaultPoint.BEFORE_COMMIT);
				connection.commit();
				committed = true;
				_faultInjector.inject(FaultPoint.AFTER_COMMIT);
				_reservations.dispatchCommitted(dispatch.canonicalResourceKeys().size());
				releasedIds.forEach(_ids::release);
				completionPublished = true;
				return committedResult;
			}
			catch (Throwable failure)
			{
				if (committed)
				{
					_reservations.dispatchCommitted(committedDispatch.canonicalResourceKeys().size());
					releasedIds.forEach(_ids::release);
					completionPublished = true;
					return committedResult;
				}
				rollback(connection, failure);
				reservedIds.forEach(_ids::release);
				return failed(failure);
			}
		}
		catch (SQLException exception)
		{
			if (committed)
			{
				if (!completionPublished)
				{
					_reservations.dispatchCommitted(committedDispatch.canonicalResourceKeys().size());
					releasedIds.forEach(_ids::release);
				}
				return committedResult;
			}
			else
			{
				reservedIds.forEach(_ids::release);
			}
			return failed(exception);
		}
	}

	public EnchantQuote quoteEnchant(PhantomBackgroundState background, PhantomGoal goal, long replacementEvidence)
	{
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				lockProfile(connection, background.identity().profileId(), background.identity().characterObjectId());
				lockCharacter(connection, background);
				final List<ItemRow> items = lockItems(connection, background.identity().characterObjectId());
				validateBackgroundInventory(background, items);
				final PhantomEnchantGoalSpec spec = PhantomEnchantGoalSpec.parse(goal);
				final ItemRow target = items.stream().filter(row -> row.objectId() == spec.targetObjectId()).findFirst().orElse(null);
				if (target == null)
				{
					connection.rollback();
					return EnchantQuote.rejected(Result.CONFLICT);
				}
				final List<ItemRow> scrolls = items.stream().filter(row -> (row.location() == ItemLocation.INVENTORY) && spec.allowedScrollItemIds().contains(row.itemId())).sorted(Comparator.comparingInt(ItemRow::objectId)).limit(_policy.limits().scrollCandidates()).toList();
				final List<ItemRow> supports = items.stream().filter(row -> (row.location() == ItemLocation.INVENTORY) && spec.allowedSupportItemIds().contains(row.itemId())).sorted(Comparator.comparingInt(ItemRow::objectId)).limit(_policy.limits().supportCandidates()).toList();
				EnchantOutcome selected = null;
				ItemRow selectedScroll = null;
				ItemRow selectedSupport = null;
				final ItemTemplate template = ItemData.getInstance().getTemplate(target.itemId());
				final boolean augmented = hasRow(connection, "SELECT 1 FROM item_attributes WHERE itemId=? LIMIT 1 FOR UPDATE", target.objectId());
				final boolean elemented = hasRow(connection, "SELECT 1 FROM item_elementals WHERE itemId=? ORDER BY elemType LIMIT 1 FOR UPDATE", target.objectId());
				for (ItemRow scroll : scrolls)
				{
					final List<ItemRow> supportChoices = supports.isEmpty() ? java.util.Collections.singletonList(null) : supports;
					for (ItemRow support : supportChoices)
					{
						final EnchantOutcome candidate = PhantomEconomyProjection.enchant(new EnchantRequest(spec, template, target.objectId(), target.enchantLevel(), target.location(), scroll.objectId(), scroll.itemId(), support == null ? 0 : support.objectId(), support == null ? 0 : support.itemId(), augmented, elemented, (target.time() > 0) || (target.timeOfUse() > 0), (target.customType1() != 0) || (target.customType2() != 0) || (target.manaLeft() >= 0), replacementEvidence, goal.expenseBudget(), background.clock().rngState(), _policy));
						if (candidate.executable())
						{
							selected = candidate;
							selectedScroll = scroll;
							selectedSupport = support;
							break;
						}
					}
					if (selected != null)
					{
						break;
					}
				}
				if (selected == null)
				{
					connection.rollback();
					return EnchantQuote.rejected(target.location() == ItemLocation.PAPERDOLL ? Result.ACTIVE_REQUIRED : Result.CONFLICT);
				}
				final List<Reservation> resources = new ArrayList<>();
				resources.add(new Reservation(background.identity().profileId(), background.identity().characterObjectId(), background.identity().classIndex(), ResourceKind.ITEM_OBJECT, target.objectId(), target.itemId(), 0, target.count(), target.enchantLevel(), target.location().name()));
				resources.add(new Reservation(background.identity().profileId(), background.identity().characterObjectId(), background.identity().classIndex(), ResourceKind.ITEM_OBJECT, selectedScroll.objectId(), selectedScroll.itemId(), 0, selectedScroll.count(), selectedScroll.enchantLevel(), selectedScroll.location().name()));
				if (selectedSupport != null)
				{
					resources.add(new Reservation(background.identity().profileId(), background.identity().characterObjectId(), background.identity().classIndex(), ResourceKind.ITEM_OBJECT, selectedSupport.objectId(), selectedSupport.itemId(), 0, selectedSupport.count(), selectedSupport.enchantLevel(), selectedSupport.location().name()));
				}
				if (selected.crystalItemId() != 0)
				{
					resources.add(new Reservation(background.identity().profileId(), background.identity().characterObjectId(), background.identity().classIndex(), ResourceKind.ITEM_COUNT, 0, selected.crystalItemId(), selected.crystalCount(), itemCounts(items).getOrDefault(selected.crystalItemId(), 0L), 0, "INVENTORY"));
					resources.add(reservation(background, ResourceKind.CAPACITY, 0, 0));
				}
				connection.rollback();
				return new EnchantQuote(selected.result(), selected.authorityHash(), target.objectId(), target.itemId(), selectedScroll.objectId(), selectedScroll.itemId(), selectedSupport == null ? 0 : selectedSupport.objectId(), selectedSupport == null ? 0 : selectedSupport.itemId(), List.copyOf(resources));
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return EnchantQuote.rejected(failure instanceof Conflict ? Result.CONFLICT : Result.ERROR);
			}
		}
		catch (SQLException exception)
		{
			return EnchantQuote.rejected(Result.ERROR);
		}
	}

	private static void requireOperation(DispatchLock dispatch, Kind kind, PhantomGoal goal)
	{
		if ((dispatch.operation().kind() != kind) || (dispatch.operation().goalId() != goal.goalId()) || (dispatch.operation().goalRevision() != goal.revision()))
		{
			throw new Conflict("Economy operation does not match exact Goal authority.");
		}
	}

	private static void lockProfile(Connection connection, long profileId, int characterObjectId) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "SELECT character_object_id FROM phantom_profiles WHERE profile_id=? FOR UPDATE"))
		{
			statement.setLong(1, profileId);
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next() || (row.getInt(1) != characterObjectId))
				{
					throw new Conflict("Phantom profile link changed.");
				}
			}
		}
	}

	private static Component lockComponent(Connection connection, long profileId, String componentType) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "SELECT component_schema_version,row_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
		{
			statement.setLong(1, profileId);
			statement.setString(2, componentType);
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next())
				{
					throw new Conflict("Required Phantom component is absent: " + componentType);
				}
				return new Component(row.getInt(1), row.getLong(2), row.getBytes(3));
			}
		}
	}

	private static void validateComponent(Component component, int schema, long rowVersion, byte[] payload)
	{
		if ((component.schemaVersion() != schema) || (component.rowVersion() != rowVersion) || !Arrays.equals(component.payload(), payload))
		{
			throw new Conflict("Phantom component authority changed.");
		}
	}

	private static CharacterFacts lockCharacter(Connection connection, PhantomBackgroundState background) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "SELECT level,exp,sp,expBeforeDeath,curHp,maxHp,curMp,maxMp,curCp,maxCp,classid FROM characters WHERE charId=? FOR UPDATE"))
		{
			statement.setInt(1, background.identity().characterObjectId());
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next())
				{
					throw new Conflict("Canonical character is absent.");
				}
				final CharacterFacts main = new CharacterFacts(row.getInt(1), row.getLong(2), row.getLong(3), row.getLong(4), row.getDouble(5), row.getDouble(6), row.getDouble(7), row.getDouble(8), row.getDouble(9), row.getDouble(10), row.getInt(11));
				if (background.identity().classIndex() == 0)
				{
					validateCharacter(background, main);
					return main;
				}
				try (PreparedStatement subclass = prepare(connection, "SELECT level,exp,sp,class_id FROM character_subclasses WHERE charId=? AND class_index=? FOR UPDATE"))
				{
					subclass.setInt(1, background.identity().characterObjectId());
					subclass.setInt(2, background.identity().classIndex());
					try (ResultSet sub = subclass.executeQuery())
					{
						if (!sub.next() || (sub.getInt(1) != background.progress().level()) || (sub.getLong(2) != background.progress().experience()) || (sub.getLong(3) != background.progress().skillPoints()) || (sub.getInt(4) != background.identity().activeClassId()))
						{
							throw new Conflict("Canonical subclass authority changed.");
						}
					}
				}
				validateVitals(background, main);
				return main;
			}
		}
	}

	private static void validateCharacter(PhantomBackgroundState background, CharacterFacts facts)
	{
		if ((facts.level() != background.progress().level()) || (facts.experience() != background.progress().experience()) || (facts.skillPoints() != background.progress().skillPoints()) || (facts.experienceBeforeDeath() != background.progress().experienceBeforeDeath()) || (facts.classId() != background.identity().activeClassId()))
		{
			throw new Conflict("Canonical character progression changed.");
		}
		validateVitals(background, facts);
	}

	private static void validateVitals(PhantomBackgroundState background, CharacterFacts facts)
	{
		if (!close(facts.currentHp(), background.vitals().currentHp()) || !close(facts.maximumHp(), background.vitals().maximumHp()) || !close(facts.currentMp(), background.vitals().currentMp()) || !close(facts.maximumMp(), background.vitals().maximumMp()) || !close(facts.currentCp(), background.vitals().currentCp()) || !close(facts.maximumCp(), background.vitals().maximumCp()))
		{
			throw new Conflict("Canonical character vitals changed.");
		}
	}

	private static boolean lockKnownRecipe(Connection connection, PhantomBackgroundState background, RecipeList recipe) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "SELECT type,classIndex FROM character_recipebook WHERE charId=? AND id=? ORDER BY type,classIndex FOR UPDATE"))
		{
			statement.setInt(1, background.identity().characterObjectId());
			statement.setInt(2, recipe.getId());
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					final int type = rows.getInt(1);
					final int classIndex = rows.getInt(2);
					if ((!recipe.isDwarvenRecipe() && (type == 0) && (classIndex == 0)) || (recipe.isDwarvenRecipe() && (type == 1) && (classIndex == background.identity().classIndex())))
					{
						return true;
					}
				}
				return false;
			}
		}
	}

	private static int lockSkill(Connection connection, PhantomBackgroundState background, int skillId) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "SELECT skill_level FROM character_skills WHERE charId=? AND class_index=? AND skill_id=? FOR UPDATE"))
		{
			statement.setInt(1, background.identity().characterObjectId());
			statement.setInt(2, background.identity().classIndex());
			statement.setInt(3, skillId);
			try (ResultSet row = statement.executeQuery())
			{
				return row.next() ? row.getInt(1) : 0;
			}
		}
	}

	private static List<ItemRow> lockItems(Connection connection, int characterObjectId) throws SQLException
	{
		final List<ItemRow> result = new ArrayList<>();
		try (PreparedStatement statement = prepare(connection, "SELECT object_id,item_id,count,loc,loc_data,enchant_level,custom_type1,custom_type2,mana_left,time,time_of_use FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id LIMIT " + (PhantomBackgroundState.MAX_TRACKED_ITEMS + 1) + " FOR UPDATE"))
		{
			statement.setInt(1, characterObjectId);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.add(new ItemRow(rows.getInt(1), rows.getInt(2), rows.getLong(3), ItemLocation.valueOf(rows.getString(4)), rows.getInt(5), rows.getInt(6), rows.getInt(7), rows.getInt(8), rows.getInt(9), rows.getLong(10), rows.getInt(11)));
				}
			}
			if (result.size() > PhantomBackgroundState.MAX_TRACKED_ITEMS)
			{
				throw new Conflict("Background inventory exceeds its bounded canonical model.");
			}
		}
		return result;
	}

	private static void validateBackgroundInventory(PhantomBackgroundState background, List<ItemRow> items)
	{
		final String hash = PhantomBackgroundInventoryHash.compute(items.stream().map(ItemRow::canonical).toList());
		if (!hash.equals(background.inventory().canonicalHash()))
		{
			throw new Conflict("Canonical inventory changed before economy dispatch.");
		}
	}

	private List<ItemRow> applyCountDeltas(Connection connection, int ownerId, List<ItemRow> current, Map<Integer, Long> deltas, List<Integer> reservedIds, List<Integer> releasedIds) throws SQLException
	{
		final List<ItemRow> rows = new ArrayList<>(current);
		for (Map.Entry<Integer, Long> mutation : deltas.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
		{
			long delta = mutation.getValue();
			if (delta < 0)
			{
				long remaining = -delta;
				for (int index = 0; (index < rows.size()) && (remaining > 0); index++)
				{
					final ItemRow row = rows.get(index);
					if ((row.itemId() != mutation.getKey()) || (row.location() != ItemLocation.INVENTORY))
					{
						continue;
					}
					final long consumed = Math.min(remaining, row.count());
					if (consumed == row.count())
					{
						deleteItem(connection, ownerId, row);
						rows.remove(index--);
						releasedIds.add(row.objectId());
					}
					else
					{
						updateCount(connection, ownerId, row, row.count() - consumed);
						rows.set(index, row.withCount(row.count() - consumed));
					}
					remaining -= consumed;
				}
				if (remaining != 0)
				{
					throw new Conflict("Craft ingredient count changed.");
				}
			}
			else if (delta > 0)
			{
				addItem(connection, ownerId, rows, mutation.getKey(), delta, reservedIds);
			}
		}
		rows.sort(Comparator.comparingInt(ItemRow::objectId));
		return List.copyOf(rows);
	}

	private List<ItemRow> applyEnchant(Connection connection, int ownerId, List<ItemRow> current, ItemRow target, ItemRow scroll, ItemRow support, EnchantOutcome outcome, List<Integer> reservedIds, List<Integer> releasedIds) throws SQLException
	{
		final List<ItemRow> rows = new ArrayList<>(current);
		consumeExact(connection, ownerId, rows, scroll, releasedIds);
		if (support != null)
		{
			consumeExact(connection, ownerId, rows, support, releasedIds);
		}
		if (outcome.targetSurvives())
		{
			try (PreparedStatement statement = prepare(connection, "UPDATE items SET enchant_level=? WHERE owner_id=? AND object_id=? AND item_id=? AND loc='INVENTORY' AND enchant_level=?"))
			{
				statement.setInt(1, outcome.nextEnchantLevel());
				statement.setInt(2, ownerId);
				statement.setInt(3, target.objectId());
				statement.setInt(4, target.itemId());
				statement.setInt(5, target.enchantLevel());
				requireOne(statement.executeUpdate(), "background target enchant update");
			}
			rows.set(rows.indexOf(target), target.withEnchant(outcome.nextEnchantLevel()));
		}
		else
		{
			deleteItem(connection, ownerId, target);
			rows.remove(target);
			releasedIds.add(target.objectId());
			if ((outcome.crystalItemId() != 0) && (outcome.crystalCount() > 0))
			{
				addItem(connection, ownerId, rows, outcome.crystalItemId(), outcome.crystalCount(), reservedIds);
			}
		}
		rows.sort(Comparator.comparingInt(ItemRow::objectId));
		return List.copyOf(rows);
	}

	private static void consumeExact(Connection connection, int ownerId, List<ItemRow> rows, ItemRow item, List<Integer> releasedIds) throws SQLException
	{
		if ((item.location() != ItemLocation.INVENTORY) || (item.count() < 1))
		{
			throw new Conflict("Exact enchant consumable changed.");
		}
		final int index = rows.indexOf(item);
		if (item.count() == 1)
		{
			deleteItem(connection, ownerId, item);
			rows.remove(index);
			releasedIds.add(item.objectId());
		}
		else
		{
			updateCount(connection, ownerId, item, item.count() - 1);
			rows.set(index, item.withCount(item.count() - 1));
		}
	}

	private void addItem(Connection connection, int ownerId, List<ItemRow> rows, int itemId, long count, List<Integer> reservedIds) throws SQLException
	{
		final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
		if ((template == null) || (template.getTime() != -1))
		{
			throw new Conflict("Unsupported economy output item.");
		}
		if (template.isStackable())
		{
			final ItemRow stack = rows.stream().filter(row -> (row.itemId() == itemId) && (row.location() == ItemLocation.INVENTORY)).min(Comparator.comparingInt(ItemRow::objectId)).orElse(null);
			if (stack != null)
			{
				updateCount(connection, ownerId, stack, Math.addExact(stack.count(), count));
				rows.set(rows.indexOf(stack), stack.withCount(Math.addExact(stack.count(), count)));
				return;
			}
		}
		final long objects = template.isStackable() ? 1 : count;
		if (objects > 8)
		{
			throw new Conflict("Economy output object bound exceeded.");
		}
		for (long index = 0; index < objects; index++)
		{
			final int objectId = _ids.reserve();
			reservedIds.add(objectId);
			final long objectCount = template.isStackable() ? count : 1;
			try (PreparedStatement statement = prepare(connection, "INSERT INTO items(owner_id,item_id,count,loc,loc_data,enchant_level,object_id,custom_type1,custom_type2,mana_left,time) VALUES(?,?,?,'INVENTORY',0,0,?,0,0,?,-1)"))
			{
				statement.setInt(1, ownerId);
				statement.setInt(2, itemId);
				statement.setLong(3, objectCount);
				statement.setInt(4, objectId);
				statement.setInt(5, template.getDuration());
				requireOne(statement.executeUpdate(), "background economy item insert");
			}
			rows.add(new ItemRow(objectId, itemId, objectCount, ItemLocation.INVENTORY, 0, 0, 0, 0, template.getDuration(), -1, 0));
		}
	}

	private static void updateCount(Connection connection, int ownerId, ItemRow row, long count) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "UPDATE items SET count=? WHERE owner_id=? AND object_id=? AND item_id=? AND loc=? AND count=?"))
		{
			statement.setLong(1, count);
			statement.setInt(2, ownerId);
			statement.setInt(3, row.objectId());
			statement.setInt(4, row.itemId());
			statement.setString(5, row.location().name());
			statement.setLong(6, row.count());
			requireOne(statement.executeUpdate(), "background economy item count update");
		}
	}

	private static void deleteItem(Connection connection, int ownerId, ItemRow row) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "DELETE FROM items WHERE owner_id=? AND object_id=? AND item_id=? AND loc=? AND count=? AND enchant_level=?"))
		{
			statement.setInt(1, ownerId);
			statement.setInt(2, row.objectId());
			statement.setInt(3, row.itemId());
			statement.setString(4, row.location().name());
			statement.setLong(5, row.count());
			statement.setInt(6, row.enchantLevel());
			requireOne(statement.executeUpdate(), "background economy item delete");
		}
	}

	private static void updateVitals(Connection connection, PhantomBackgroundState background, Vitals vitals) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "UPDATE characters SET curHp=?,curMp=? WHERE charId=? AND curHp=? AND curMp=?"))
		{
			statement.setDouble(1, vitals.currentHp());
			statement.setDouble(2, vitals.currentMp());
			statement.setInt(3, background.identity().characterObjectId());
			statement.setDouble(4, background.vitals().currentHp());
			statement.setDouble(5, background.vitals().currentMp());
			requireOne(statement.executeUpdate(), "background craft vital update");
		}
	}

	private static InventoryFacts inventoryFacts(InventoryFacts previous, List<ItemRow> rows, Set<Integer> additions)
	{
		final TreeSet<Integer> mutable = new TreeSet<>(previous.mutableItemIds());
		mutable.addAll(additions);
		if (mutable.size() > PhantomBackgroundState.MAX_MUTABLE_ITEM_IDS)
		{
			throw new Conflict("Background mutable item bound exceeded.");
		}
		long load = 0;
		int usedSlots = 0;
		final List<ItemObject> tracked = new ArrayList<>();
		final Set<Integer> paperdollProofs = previous.objects().stream().filter(item -> item.location() == ItemLocation.PAPERDOLL).map(ItemObject::objectId).collect(java.util.stream.Collectors.toUnmodifiableSet());
		for (ItemRow row : rows)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(row.itemId());
			if (template == null)
			{
				throw new Conflict("Unknown canonical item template.");
			}
			load = Math.addExact(load, Math.multiplyExact(row.count(), template.getWeight()));
			if (row.location() == ItemLocation.INVENTORY)
			{
				usedSlots++;
			}
			if (((row.location() == ItemLocation.INVENTORY) && mutable.contains(row.itemId())) || ((row.location() == ItemLocation.PAPERDOLL) && paperdollProofs.contains(row.objectId())))
			{
				tracked.add(new ItemObject(row.objectId(), row.itemId(), row.count(), template.isStackable(), row.location()));
			}
		}
		if ((load > previous.maximumLoad()) || (usedSlots > previous.maximumSlots()) || (tracked.size() > PhantomBackgroundState.MAX_TRACKED_ITEMS))
		{
			throw new Conflict("Background inventory capacity changed.");
		}
		final String hash = PhantomBackgroundInventoryHash.compute(rows.stream().map(ItemRow::canonical).toList());
		return InventoryFacts.sorted(List.copyOf(mutable), tracked, hash, load, previous.maximumLoad(), usedSlots, previous.maximumSlots());
	}

	private static Map<Integer, Long> itemCounts(List<ItemRow> rows)
	{
		final Map<Integer, Long> result = new LinkedHashMap<>();
		rows.stream().filter(row -> row.location() == ItemLocation.INVENTORY).forEach(row -> result.merge(row.itemId(), row.count(), Math::addExact));
		return Map.copyOf(result);
	}

	private static ItemRow exactItem(List<ItemRow> rows, int objectId, int itemId)
	{
		return rows.stream().filter(row -> (row.objectId() == objectId) && (row.itemId() == itemId)).findFirst().orElseThrow(() -> new Conflict("Exact economy item object changed."));
	}

	private static boolean hasRow(Connection connection, String sql, int objectId) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, sql))
		{
			statement.setInt(1, objectId);
			try (ResultSet row = statement.executeQuery())
			{
				return row.next();
			}
		}
	}

	private static void requireCraftReservations(DispatchLock dispatch, PhantomBackgroundState background, RecipeList recipe)
	{
		final Set<String> keys = Set.copyOf(dispatch.canonicalResourceKeys());
		for (org.l2jmobius.gameserver.data.holders.RecipeHolder ingredient : recipe.getRecipes())
		{
			requireKey(keys, reservation(background, ResourceKind.ITEM_COUNT, 0, ingredient.getItemId()).canonicalKey());
		}
		requireKey(keys, reservation(background, ResourceKind.RECIPE, 0, recipe.getId()).canonicalKey());
		requireKey(keys, reservation(background, ResourceKind.SKILL, 0, recipe.isDwarvenRecipe() ? org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_DWARVEN.getId() : org.l2jmobius.gameserver.model.skill.CommonSkill.CREATE_COMMON.getId()).canonicalKey());
		requireKey(keys, reservation(background, ResourceKind.CAPACITY, 0, 0).canonicalKey());
	}

	private static void requireEnchantReservations(DispatchLock dispatch, PhantomBackgroundState background, ItemRow target, ItemRow scroll, ItemRow support, EnchantOutcome outcome)
	{
		final Set<String> keys = Set.copyOf(dispatch.canonicalResourceKeys());
		requireKey(keys, reservation(background, ResourceKind.ITEM_OBJECT, target.objectId(), target.itemId()).canonicalKey());
		requireKey(keys, reservation(background, ResourceKind.ITEM_OBJECT, scroll.objectId(), scroll.itemId()).canonicalKey());
		if (support != null)
		{
			requireKey(keys, reservation(background, ResourceKind.ITEM_OBJECT, support.objectId(), support.itemId()).canonicalKey());
		}
		if (outcome.crystalItemId() != 0)
		{
			requireKey(keys, reservation(background, ResourceKind.ITEM_COUNT, 0, outcome.crystalItemId()).canonicalKey());
			requireKey(keys, reservation(background, ResourceKind.CAPACITY, 0, 0).canonicalKey());
		}
	}

	private static Reservation reservation(PhantomBackgroundState background, ResourceKind kind, int objectId, int itemId)
	{
		return new Reservation(background.identity().profileId(), background.identity().characterObjectId(), background.identity().classIndex(), kind, objectId, itemId, kind == ResourceKind.ITEM_COUNT ? 1 : 0, 0, 0, "");
	}

	private static void requireKey(Set<String> keys, String required)
	{
		if (!keys.contains(required))
		{
			throw new Conflict("Economy dispatch reservation evidence is incomplete.");
		}
	}

	private static void writeComponent(Connection connection, Component component, long profileId, String type, int schema, byte[] payload) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "UPDATE phantom_profile_components SET component_schema_version=?,payload=?,row_version=row_version+1 WHERE profile_id=? AND component_type=? AND row_version=?"))
		{
			statement.setInt(1, schema);
			statement.setBytes(2, payload);
			statement.setLong(3, profileId);
			statement.setString(4, type);
			statement.setLong(5, component.rowVersion());
			requireOne(statement.executeUpdate(), "economy component update");
		}
	}

	private static byte[] consequence(Object... values)
	{
		return java.util.Arrays.stream(values).map(String::valueOf).collect(java.util.stream.Collectors.joining("|")).getBytes(StandardCharsets.US_ASCII);
	}

	private static PreparedStatement prepare(Connection connection, String sql) throws SQLException
	{
		final PreparedStatement statement = connection.prepareStatement(sql);
		statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
		return statement;
	}

	private static void requireOne(int count, String operation)
	{
		if (count != 1)
		{
			throw new Conflict("Unexpected row count for " + operation + ".");
		}
	}

	private static void rollback(Connection connection, Throwable failure)
	{
		try
		{
			connection.rollback();
		}
		catch (SQLException rollbackFailure)
		{
			failure.addSuppressed(rollbackFailure);
		}
	}

	private static boolean close(double left, double right)
	{
		return Math.abs(left - right) <= 0.000001d;
	}

	private static TransactionResult failed(Throwable failure)
	{
		return new TransactionResult(failure instanceof Conflict ? Status.CONFLICT : Status.BACKEND_FAILURE, null, null, null, null, false);
	}

	@FunctionalInterface
	public interface ConnectionProvider
	{
		Connection open() throws SQLException;
	}

	public enum FaultPoint
	{
		AFTER_PROFILE_LOCK,
		AFTER_DISPATCH_LOCK,
		AFTER_COMPONENT_LOCKS,
		AFTER_CHARACTER_RECIPE_SKILL_LOCKS,
		AFTER_ITEM_LOCKS,
		AFTER_ITEM_WRITES,
		AFTER_VITAL_WRITES,
		AFTER_BACKGROUND_WRITE,
		AFTER_ACQUISITION_OR_GOAL_WRITE,
		AFTER_OPERATION_AUDIT_WRITE,
		BEFORE_COMMIT,
		AFTER_COMMIT
	}

	@FunctionalInterface
	public interface FaultInjector
	{
		void inject(FaultPoint point);

		static FaultInjector none()
		{
			return _ ->
			{
			};
		}
	}

	public enum Status
	{
		COMMITTED,
		CONFLICT,
		BACKEND_FAILURE
	}

	public record CraftCommand(String operationId, PhantomBackgroundState background, long backgroundRowVersion, PhantomAcquisitionState acquisition, long acquisitionRowVersion, PhantomGoal goal, long goalRowVersion, long logicalMinute, long nowEpochMillis)
	{
	}

	public record EnchantCommand(String operationId, PhantomBackgroundState background, long backgroundRowVersion, PhantomGoal goal, long goalRowVersion, int targetObjectId, int targetItemId, int scrollObjectId, int scrollItemId, int supportObjectId, int supportItemId, long replacementEvidence, long nowEpochMillis)
	{
	}

	public record TransactionResult(Status status, Result result, PhantomBackgroundState background, PhantomAcquisitionState acquisition, PhantomGoal goal, boolean rareCraft)
	{
	}

	public record CraftQuote(Result result, String authorityHash, List<Reservation> reservations)
	{
		public static CraftQuote rejected(Result result)
		{
			return new CraftQuote(result, "", List.of());
		}

		public boolean executable()
		{
			return (result == Result.SUCCESS) || (result == Result.CRAFT_FAILED);
		}
	}

	public record EnchantQuote(Result result, String authorityHash, int targetObjectId, int targetItemId, int scrollObjectId, int scrollItemId, int supportObjectId, int supportItemId, List<Reservation> reservations)
	{
		public static EnchantQuote rejected(Result result)
		{
			return new EnchantQuote(result, "", 0, 0, 0, 0, 0, 0, List.of());
		}

		public boolean executable()
		{
			return (result == Result.SUCCESS) || (result == Result.SAFE_FAILURE) || (result == Result.BLESSED_RESET) || (result == Result.DESTROYED_WITH_CRYSTALS);
		}
	}

	private record Component(int schemaVersion, long rowVersion, byte[] payload)
	{
	}

	private record CharacterFacts(int level, long experience, long skillPoints, long experienceBeforeDeath, double currentHp, double maximumHp, double currentMp, double maximumMp, double currentCp, double maximumCp, int classId)
	{
	}

	private record ItemRow(int objectId, int itemId, long count, ItemLocation location, int locationData, int enchantLevel, int customType1, int customType2, int manaLeft, long time, int timeOfUse)
	{
		private CanonicalItem canonical()
		{
			return new CanonicalItem(objectId, itemId, count, location);
		}

		private ItemRow withCount(long value)
		{
			return new ItemRow(objectId, itemId, value, location, locationData, enchantLevel, customType1, customType2, manaLeft, time, timeOfUse);
		}

		private ItemRow withEnchant(int value)
		{
			return new ItemRow(objectId, itemId, count, location, locationData, value, customType1, customType2, manaLeft, time, timeOfUse);
		}
	}

	private static final class Conflict extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		private Conflict(String message)
		{
			super(message);
		}
	}
}

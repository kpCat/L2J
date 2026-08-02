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
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.enums.StatType;
import org.l2jmobius.gameserver.data.holders.RecipeHolder;
import org.l2jmobius.gameserver.data.xml.EnchantItemData;
import org.l2jmobius.gameserver.data.xml.EnchantItemGroupsData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.managers.RecipeCraftObserver;
import org.l2jmobius.gameserver.managers.RecipeManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Candidate;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Hashes;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipeNode;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.RecipePlan;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Source;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Audit;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Identity;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Kind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Result;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.State;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection.CraftOutcome;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection.CraftRequest;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection.EnchantOutcome;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyProjection.EnchantRequest;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.EconomyConflictException;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.ReserveResult;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantGoalSpec;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.services.EnchantItemService;
import org.l2jmobius.gameserver.services.EnchantItemService.Event;

public final class PhantomEconomySuite implements PhantomTestSuite
{
	public enum Mode
	{
		RESERVATION_SCHEMA(false),
		RESERVATION_CONCURRENCY(false),
		SELF_CRAFT_ACTIVE(true),
		SELF_CRAFT_BACKGROUND(true),
		ENCHANT_ACTIVE(true),
		ENCHANT_BACKGROUND(true),
		RESTART_TRANSITION(false),
		LIFECYCLE_PERFORMANCE(true);

		private final boolean _productionData;

		Mode(boolean productionData)
		{
			_productionData = productionData;
		}
	}

	private static final long SEED = 22002201L;
	private static final String TEST_DATABASE = "l2jmobiush5_phantom_test";
	private static final Hashes HASHES = new Hashes("a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), "e".repeat(64));
	private final Mode _mode;
	private PhantomEconomyPolicy _policy;
	private PhantomHeadlessPlayerTestEnvironment _environment;

	public PhantomEconomySuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "economy-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 022 Checkpoint 1 mode used the wrong seed.");
		_policy = PhantomEconomyPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/economy/high-five-economy-v1.xml"));
		if (_mode._productionData)
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			EnchantItemGroupsData.getInstance();
			EnchantItemData.getInstance();
		}
		else
		{
			final String config = System.getProperty("phantom.test.config");
			PhantomAssertions.assertTrue((config != null) && !config.isBlank(), "Economy test DB config is missing.");
			PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(config));
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_environment != null)
		{
			_environment.shutdown();
		}
		else
		{
			DatabaseFactory.close();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case RESERVATION_SCHEMA ->
			{
				registry.add("migration-bounds", this::testSchemaAndPolicy);
				registry.add("operation-contract", this::testOperationContract);
			}
			case RESERVATION_CONCURRENCY ->
			{
				registry.add("duplicate-and-replay", this::testReservationConflict);
				registry.add("multi-owner-order", this::testMultiOwnerOrder);
			}
			case SELF_CRAFT_ACTIVE -> registry.add("recipe-manager-observer", this::testActiveCraft);
			case SELF_CRAFT_BACKGROUND -> registry.add("exact-recipe-projection", this::testBackgroundCraft);
			case ENCHANT_ACTIVE -> registry.add("canonical-service", this::testActiveEnchant);
			case ENCHANT_BACKGROUND -> registry.add("deterministic-branches", this::testBackgroundEnchant);
			case RESTART_TRANSITION -> registry.add("expiry-and-fail-stop", this::testRestartAndExpiry);
			case LIFECYCLE_PERFORMANCE ->
			{
				registry.add("materialization-boundary", this::testBoundary);
				registry.add("bounded-volume", this::testPerformance);
			}
		}
	}

	private void testSchemaAndPolicy(PhantomTestContext context) throws Exception
	{
		final String migration = Files.readString(context.moduleRoot().resolve("dist/db_installer/sql/game/phantom_reservations.sql"));
		PhantomAssertions.assertEquals(3L, migration.lines().filter(line -> line.startsWith("CREATE TABLE IF NOT EXISTS")).count(), "Economy migration is not idempotent by construction.");
		PhantomAssertions.assertTrue(migration.contains("ENGINE=InnoDB") && migration.contains("ON DELETE CASCADE"), "Economy migration lost transactional cleanup semantics.");
		try (Connection connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(TEST_DATABASE, connection.getCatalog(), "Economy schema test touched a non-test database.");
			for (String table : List.of("phantom_economy_operations", "phantom_economy_reservations", "phantom_economy_audit"))
			{
				try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=? AND table_name=?"))
				{
					statement.setString(1, TEST_DATABASE);
					statement.setString(2, table);
					try (ResultSet row = statement.executeQuery())
					{
						PhantomAssertions.assertTrue(row.next() && (row.getInt(1) == 1), "Missing economy table " + table + ".");
					}
				}
			}
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement("UPDATE phantom_economy_operations SET row_version=row_version+1 WHERE operation_id=?"))
			{
				statement.setString(1, "0".repeat(64));
				PhantomAssertions.assertEquals(0, statement.executeUpdate(), "Rollback control found an unexpected operation.");
			}
			connection.rollback();
		}
		PhantomAssertions.assertEquals(4096, _policy.limits().payloadBytes(), "Payload bound drifted.");
		PhantomAssertions.assertEquals(32, _policy.limits().reservationsPerOperation(), "Reservation bound drifted.");
		PhantomAssertions.assertEquals(24, _policy.limits().itemIdsPerRead(), "Item read bound drifted.");
		PhantomAssertions.assertEquals(4, _policy.limits().participantsPerOperation(), "Participant bound drifted.");
		PhantomAssertions.assertEquals(100000, _policy.limits().retainedNonterminalOperations(), "Retained operation bound drifted.");
		context.record("economy.policyHash", _policy.hash());
	}

	private void testOperationContract(PhantomTestContext context)
	{
		PhantomAssertions.assertTrue(State.PREPARED.canTransitionTo(State.RESERVED), "PREPARED must reserve.");
		PhantomAssertions.assertTrue(State.RESERVED.canTransitionTo(State.DISPATCHING), "RESERVED must dispatch.");
		PhantomAssertions.assertFalse(State.DISPATCHING.canTransitionTo(State.EXPIRED), "Dispatched work must never expire.");
		PhantomAssertions.assertFalse(State.OBSERVING.canTransitionTo(State.ABORTED), "Observed work must fail stop.");
		final Reservation classZero = new Reservation(1, 7, 0, ResourceKind.RECIPE, 0, 100, 0, 0, 0, "");
		final Reservation classOne = new Reservation(1, 7, 1, ResourceKind.RECIPE, 0, 100, 0, 0, 0, "");
		PhantomAssertions.assertFalse(classZero.canonicalKey().equals(classOne.canonicalKey()), "Class-specific recipe locks collided.");
		final PhantomEconomyOperation first = operation(1, 7, 1, Kind.SELF_CRAFT, 1, 1000);
		final PhantomEconomyOperation replay = operation(1, 7, 1, Kind.SELF_CRAFT, 1, 1000);
		PhantomAssertions.assertEquals(first.operationId(), replay.operationId(), "Operation identity is not deterministic.");
		context.record("economy.operationId", first.operationId());
	}

	private void testReservationConflict(PhantomTestContext context) throws Exception
	{
		final PhantomProfile firstProfile = createProfile();
		final PhantomProfile secondProfile = createProfile();
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		final AtomicReference<ReserveResult> firstResult = new AtomicReference<>();
		final AtomicReference<ReserveResult> secondResult = new AtomicReference<>();
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final CountDownLatch ready = new CountDownLatch(2);
		final CountDownLatch start = new CountDownLatch(1);
		try
		{
			PhantomAssertions.assertTrue(service.start(), "Economy reservation service did not start.");
			final PhantomEconomyOperation first = operation(firstProfile.profileId(), 910001, 11, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			final PhantomEconomyOperation second = operation(secondProfile.profileId(), 910002, 12, Kind.ITEM_ENCHANT, 1, System.currentTimeMillis());
			final List<Reservation> forward = List.of(itemCount(firstProfile.profileId(), 990001, 57), itemCount(firstProfile.profileId(), 990001, 58));
			final List<Reservation> reverse = List.of(itemCount(secondProfile.profileId(), 990001, 58), itemCount(secondProfile.profileId(), 990001, 57));
			final Thread left = reservationThread("economy-test-left", ready, start, failure, () -> firstResult.set(service.reserve(first, forward)));
			final Thread right = reservationThread("economy-test-right", ready, start, failure, () -> secondResult.set(service.reserve(second, reverse)));
			left.start();
			right.start();
			PhantomAssertions.assertTrue(ready.await(5, TimeUnit.SECONDS), "Reservation stress threads did not become ready.");
			start.countDown();
			left.join(10000);
			right.join(10000);
			PhantomAssertions.assertFalse(left.isAlive() || right.isAlive(), "Reservation stress retained test threads.");
			if (failure.get() != null)
			{
				throw new AssertionError("Reservation stress failed.", failure.get());
			}
			final long winners = List.of(firstResult.get(), secondResult.get()).stream().filter(result -> result.status() == PhantomEconomyReservationService.Status.RESERVED).count();
			PhantomAssertions.assertEquals(1L, winners, "Duplicate resource reservation did not select exactly one owner.");
			final ReserveResult winner = firstResult.get().status() == PhantomEconomyReservationService.Status.RESERVED ? firstResult.get() : secondResult.get();
			final PhantomEconomyOperation winnerOperation = winner == firstResult.get() ? first : second;
			final List<Reservation> winnerReservations = winnerOperation == first ? forward : reverse;
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.IDEMPOTENT, service.reserve(winnerOperation, winnerReservations).status(), "Same operation replay was not idempotent.");
			PhantomEconomyConflictPort.install(service);
			try (PhantomEconomyConflictPort.Claim foreign = PhantomEconomyConflictPort.claim(winnerOperation.identity().profileId(), null, winnerReservations); PhantomEconomyConflictPort.Claim owner = PhantomEconomyConflictPort.claim(winnerOperation.identity().profileId(), winnerOperation.operationId(), winnerReservations))
			{
				PhantomAssertions.assertFalse(foreign.acquired(), "Accepted writer bypassed an economy reservation conflict.");
				PhantomAssertions.assertTrue(owner.acquired(), "Owning operation could not cross its own reservation boundary.");
			}
			service.transition(winner.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), audit(Result.ERROR, "operation.conflict"));
			context.record("economy.reservationConflicts", service.snapshot().conflicts());
		}
		finally
		{
			PhantomEconomyConflictPort.uninstall(service);
			service.shutdown(System.currentTimeMillis());
			deleteProfile(firstProfile.profileId());
			deleteProfile(secondProfile.profileId());
		}
	}

	private void testMultiOwnerOrder(PhantomTestContext context) throws Exception
	{
		final PhantomProfile primary = createProfile();
		final PhantomProfile participant = createProfile();
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(primary.profileId(), 910003, 13, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			final Reservation secondary = new Reservation(participant.profileId(), 990003, ResourceKind.ITEM_COUNT, 0, 61, 2, 2, 0, "INVENTORY");
			final Reservation owner = new Reservation(primary.profileId(), 990002, ResourceKind.ITEM_COUNT, 0, 62, 3, 3, 0, "INVENTORY");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(operation, List.of(secondary, owner)).status(), "Multi-owner reservation was rejected.");
			final List<Reservation> stored = service.findReservations(operation.operationId());
			PhantomAssertions.assertEquals(List.of(owner.canonicalKey(), secondary.canonicalKey()).stream().sorted().toList(), stored.stream().map(Reservation::canonicalKey).toList(), "Multi-owner locks are not stored canonically.");
			service.transition(operation.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), audit(Result.ERROR, "operation.conflict"));
			context.record("economy.participants", 2);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(primary.profileId());
			deleteProfile(participant.profileId());
		}
	}

	private void testActiveCraft(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertFalse(PlayerConfig.ALT_GAME_CREATION, "Active craft smoke requires the current shipped non-ALT path.");
		final RecipeList recipe = selectRecipe();
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		Player player = null;
		final Map<Integer, Long> baselines = new HashMap<>();
		boolean recipeAdded = false;
		Skill addedSkill = null;
		try
		{
			PhantomAssertions.assertTrue(materialization.start(), "Craft materialization did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Craft Phantom did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Materialized craft Player is absent.");
			final int skillId = recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId();
			final Skill skill = recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getSkill() : CommonSkill.CREATE_COMMON.getSkill();
			PhantomAssertions.assertTrue(skill != null, "Canonical craft skill is unavailable.");
			if (player.getKnownSkill(skillId) == null)
			{
				player.addSkill(skill, false);
				addedSkill = skill;
			}
			if (!player.hasRecipeList(recipe.getId()))
			{
				if (recipe.isDwarvenRecipe())
				{
					player.registerDwarvenRecipeList(recipe, false);
				}
				else
				{
					player.registerCommonRecipeList(recipe, false);
				}
				recipeAdded = true;
			}
			for (RecipeHolder ingredient : recipe.getRecipes())
			{
				baselines.putIfAbsent(ingredient.getItemId(), player.getInventory().getInventoryItemCount(ingredient.getItemId(), -1));
				PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, ingredient.getItemId(), ingredient.getQuantity(), player, this) != null, "Could not fund active craft ingredient.");
			}
			baselines.putIfAbsent(recipe.getItemId(), player.getInventory().getInventoryItemCount(recipe.getItemId(), -1));
			if (recipe.getRareItemId() > 0)
			{
				baselines.putIfAbsent(recipe.getRareItemId(), player.getInventory().getInventoryItemCount(recipe.getRareItemId(), -1));
			}
			final List<RecipeCraftObserver.Event> events = new ArrayList<>();
			RecipeManager.getInstance().requestMakeItem(player, recipe.getId(), events::add);
			final int playerObjectId = player.getObjectId();
			PhantomAssertions.assertTrue(events.stream().anyMatch(event -> event.type() == RecipeCraftObserver.Type.ACCEPTED), "RecipeManager observer missed ACCEPTED.");
			PhantomAssertions.assertTrue(events.stream().anyMatch(event -> event.type() == RecipeCraftObserver.Type.INGREDIENTS_CONSUMED), "RecipeManager observer missed ingredient consumption.");
			PhantomAssertions.assertEquals(1L, events.stream().filter(event -> (event.type() == RecipeCraftObserver.Type.SUCCESS_PRODUCT) || (event.type() == RecipeCraftObserver.Type.RARE_PRODUCT) || (event.type() == RecipeCraftObserver.Type.CRAFT_FAILED) || (event.type() == RecipeCraftObserver.Type.ABORTED)).count(), "RecipeManager observer emitted an invalid terminal count.");
			PhantomAssertions.assertTrue(events.stream().allMatch(event -> (event.crafterObjectId() == playerObjectId) && (event.targetObjectId() == playerObjectId) && (event.recipeListId() == recipe.getId())), "Craft observer changed exact identities.");
			context.record("economy.activeCraftEvents", events.stream().map(event -> event.type().name()).toList());
		}
		finally
		{
			if (player != null)
			{
				for (Map.Entry<Integer, Long> entry : baselines.entrySet())
				{
					restoreItemCount(player, entry.getKey(), entry.getValue());
				}
				if (recipeAdded)
				{
					player.unregisterRecipeList(recipe.getId());
				}
				if (addedSkill != null)
				{
					player.removeSkill(addedSkill, false, true);
				}
				player.setCrafting(false);
			}
			if (materialization.snapshot().state() != PhantomMaterializationService.ServiceState.STOPPED)
			{
				materialization.shutdown();
			}
			deleteProfile(profile.profileId());
			if (player != null)
			{
				_environment.assertClean(_environment.primary(), player);
			}
		}
	}

	private void testBackgroundCraft(PhantomTestContext context)
	{
		final RecipeList recipe = selectRecipe();
		final PhantomAcquisitionState acquisition = acquisition(recipe);
		final Map<Integer, Long> inventory = ingredientInventory(recipe);
		CraftOutcome success = null;
		CraftOutcome failure = null;
		for (long state = 0; (state < 10000) && ((success == null) || (failure == null)); state++)
		{
			final CraftOutcome outcome = PhantomEconomyProjection.craft(new CraftRequest(acquisition, recipe, true, recipe.getLevel(), inventory, 100000, 100000, state, _policy));
			if (outcome.result() == Result.SUCCESS)
			{
				success = outcome;
			}
			else if (outcome.result() == Result.CRAFT_FAILED)
			{
				failure = outcome;
			}
		}
		PhantomAssertions.assertTrue(success != null, "Background craft did not expose deterministic success.");
		if (recipe.getSuccessRate() < 100)
		{
			PhantomAssertions.assertTrue(failure != null, "Background craft did not expose deterministic failure.");
		}
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			PhantomAssertions.assertEquals(-(long) ingredient.getQuantity(), success.itemDeltas().get(ingredient.getItemId()), "Background craft ingredient conservation drifted.");
		}
		PhantomAssertions.assertTrue(success.itemDeltas().getOrDefault(success.productItemId(), 0L) == success.productCount(), "Background craft product conservation drifted.");
		PhantomAssertions.assertEquals(PhantomEconomyProjection.craftAuthority(acquisition, recipe, _policy), success.authorityHash(), "Background craft authority drifted.");
		final boolean alt = PlayerConfig.ALT_GAME_CREATION;
		try
		{
			PlayerConfig.ALT_GAME_CREATION = true;
			PhantomAssertions.assertEquals(Result.ACTIVE_REQUIRED, PhantomEconomyProjection.craft(new CraftRequest(acquisition, recipe, true, recipe.getLevel(), inventory, 100000, 100000, 1, _policy)).result(), "ALT craft did not fail closed to active.");
		}
		finally
		{
			PlayerConfig.ALT_GAME_CREATION = alt;
		}
		context.record("economy.backgroundCraftAuthority", success.authorityHash());
	}

	private void testActiveEnchant(PhantomTestContext context) throws Exception
	{
		final EnchantCandidate candidate = selectEnchantCandidate(null, false);
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		Player player = null;
		long targetBaseline = 0;
		long scrollBaseline = 0;
		long crystalBaseline = 0;
		try
		{
			materialization.start();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Enchant Phantom did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Materialized enchant Player is absent.");
			targetBaseline = player.getInventory().getInventoryItemCount(candidate.target().getId(), -1);
			scrollBaseline = player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1);
			final int crystalId = candidate.target().getCrystalItemId();
			crystalBaseline = crystalId == 0 ? 0 : player.getInventory().getInventoryItemCount(crystalId, -1);
			final Item target = player.getInventory().addItem(ItemProcessType.REWARD, candidate.target().getId(), 1, player, this);
			final Item scroll = player.getInventory().addItem(ItemProcessType.REWARD, candidate.scroll().getId(), 1, player, this);
			PhantomAssertions.assertTrue((target != null) && (scroll != null), "Could not create exact enchant objects.");
			player.setActiveEnchantItemId(target.getObjectId());
			final AtomicReference<Event> event = new AtomicReference<>();
			final EnchantItemService.Outcome outcome = EnchantItemService.getInstance().execute(new EnchantItemService.Request(player, target.getObjectId(), scroll.getObjectId(), 0, false, event::set));
			PhantomAssertions.assertFalse(outcome == EnchantItemService.Outcome.ERROR, "Canonical active enchant rejected compatible objects.");
			PhantomAssertions.assertTrue((event.get() != null) && (event.get().targetObjectId() == target.getObjectId()) && (event.get().scrollObjectId() == scroll.getObjectId()) && (event.get().supportObjectId() == 0), "Canonical enchant observer changed exact object identity.");
			PhantomAssertions.assertEquals(Player.ID_NONE, player.getActiveEnchantItemId(), "Canonical enchant did not clear active target.");
			PhantomAssertions.assertEquals(scrollBaseline, player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1), "Enchant scroll was not consumed exactly once.");
			context.record("economy.activeEnchantOutcome", outcome);
		}
		finally
		{
			if (player != null)
			{
				restoreItemCount(player, candidate.target().getId(), targetBaseline);
				restoreItemCount(player, candidate.scroll().getId(), scrollBaseline);
				if (candidate.target().getCrystalItemId() != 0)
				{
					restoreItemCount(player, candidate.target().getCrystalItemId(), crystalBaseline);
				}
				player.setActiveEnchantItemId(Player.ID_NONE);
			}
			if (materialization.snapshot().state() != PhantomMaterializationService.ServiceState.STOPPED)
			{
				materialization.shutdown();
			}
			deleteProfile(profile.profileId());
			if (player != null)
			{
				_environment.assertClean(_environment.primary(), player);
			}
		}
	}

	private void testBackgroundEnchant(PhantomTestContext context)
	{
		final EnchantCandidate safe = selectEnchantCandidate(Boolean.TRUE, false);
		final EnchantCandidate blessed = selectEnchantCandidate(Boolean.FALSE, true);
		final EnchantCandidate ordinary = selectEnchantCandidate(Boolean.FALSE, false);
		assertEnchantBranch(safe, Result.SAFE_FAILURE);
		assertEnchantBranch(blessed, Result.BLESSED_RESET);
		final EnchantOutcome destroyed = assertEnchantBranch(ordinary, Result.DESTROYED_WITH_CRYSTALS);
		PhantomAssertions.assertEquals(ordinary.target().getCrystalItemId(), destroyed.crystalItemId(), "Background enchant crystal identity drifted.");
		final PhantomEnchantGoalSpec goal = enchantGoal(ordinary);
		final EnchantRequest equipped = enchantRequest(goal, ordinary, ItemLocation.PAPERDOLL, 1);
		PhantomAssertions.assertEquals(Result.ACTIVE_REQUIRED, PhantomEconomyProjection.enchant(equipped).result(), "Equipped background target did not require active execution.");
		final EnchantOutcome success = findEnchantOutcome(ordinary, Result.SUCCESS);
		PhantomAssertions.assertTrue(success != null && (success.nextEnchantLevel() == (ordinary.enchantLevel() + 1)), "Background enchant success did not increment exactly once.");
		context.record("economy.backgroundEnchant", List.of(success.result(), Result.SAFE_FAILURE, Result.BLESSED_RESET, destroyed.result()));
	}

	private void testRestartAndExpiry(PhantomTestContext context) throws Exception
	{
		final PhantomProfile dispatchedProfile = createProfile();
		final PhantomProfile expiringProfile = createProfile();
		final PhantomEconomyReservationService first = new PhantomEconomyReservationService(_policy);
		final PhantomEconomyReservationService restarted = new PhantomEconomyReservationService(_policy);
		try
		{
			final long now = System.currentTimeMillis();
			first.start();
			final PhantomEconomyOperation dispatched = operation(dispatchedProfile.profileId(), 920001, 21, Kind.ITEM_ENCHANT, 1, now);
			first.reserve(dispatched, List.of(itemObject(dispatchedProfile.profileId(), 991001, 881001, 1)));
			first.transition(dispatched.operationId(), State.RESERVED, State.DISPATCHING, now + 1, null);
			PhantomAssertions.assertTrue(restarted.start(), "Restarted economy service did not start.");
			PhantomAssertions.assertEquals(State.INCONSISTENT, restarted.find(dispatched.operationId()).orElseThrow().state(), "Ambiguous dispatch was redispatched instead of failing stop.");
			final PhantomEconomyOperation expiring = operation(expiringProfile.profileId(), 920002, 22, Kind.SELF_CRAFT, 1, now);
			restarted.reserve(expiring, List.of(itemCount(expiringProfile.profileId(), 991002, 77)));
			PhantomAssertions.assertEquals(1, restarted.expireDue(now + 1_000_000, 8), "Predispatch reservation did not expire exactly once.");
			PhantomAssertions.assertEquals(State.EXPIRED, restarted.find(expiring.operationId()).orElseThrow().state(), "Expired operation retained a nonterminal state.");
			PhantomAssertions.assertTrue(restarted.findReservations(expiring.operationId()).isEmpty(), "Expiry retained reservations.");
			context.record("economy.restartInconsistent", restarted.snapshot().inconsistent());
		}
		finally
		{
			first.shutdown(System.currentTimeMillis());
			restarted.shutdown(System.currentTimeMillis());
			deleteProfile(dispatchedProfile.profileId());
			deleteProfile(expiringProfile.profileId());
		}
	}

	private void testBoundary(PhantomTestContext context) throws Exception
	{
		final PhantomProfile reservedProfile = createProfile();
		final PhantomProfile dispatchedProfile = createProfile();
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			final long now = System.currentTimeMillis();
			service.start();
			final PhantomEconomyOperation reserved = operation(reservedProfile.profileId(), 930001, 31, Kind.SELF_CRAFT, 1, now);
			service.reserve(reserved, List.of(itemCount(reservedProfile.profileId(), 992001, 88)));
			service.beforeBoundary(reservedProfile.profileId(), now + 1);
			PhantomAssertions.assertEquals(State.ABORTED, service.find(reserved.operationId()).orElseThrow().state(), "Predispatch boundary did not release the operation.");
			final PhantomEconomyOperation dispatched = operation(dispatchedProfile.profileId(), 930002, 32, Kind.ITEM_ENCHANT, 1, now);
			service.reserve(dispatched, List.of(itemObject(dispatchedProfile.profileId(), 992002, 882002, 0)));
			service.transition(dispatched.operationId(), State.RESERVED, State.DISPATCHING, now + 1, null);
			PhantomAssertions.assertThrows(EconomyConflictException.class, () -> service.beforeBoundary(dispatchedProfile.profileId(), now + 2), "Dispatch boundary did not fail closed.");
			service.transition(dispatched.operationId(), State.DISPATCHING, State.INCONSISTENT, now + 3, audit(Result.INCONSISTENT, "dispatch.ambiguous"));
			PhantomAssertions.assertEquals(0L, service.snapshot().currentReservations(), "Lifecycle retained reservations.");
			context.record("economy.boundaryDispatchBlocked", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(reservedProfile.profileId());
			deleteProfile(dispatchedProfile.profileId());
		}
	}

	private void testPerformance(PhantomTestContext context)
	{
		final RecipeList recipe = selectRecipe();
		final PhantomAcquisitionState acquisition = acquisition(recipe);
		final Map<Integer, Long> inventory = ingredientInventory(recipe);
		final EnchantCandidate enchant = selectEnchantCandidate(null, false);
		final PhantomEnchantGoalSpec goal = enchantGoal(enchant);
		final EnchantRequest enchantRequest = enchantRequest(goal, enchant, ItemLocation.INVENTORY, 1);
		final Reservation first = itemCount(1, 999001, 91);
		final Reservation second = itemCount(2, 999001, 91);
		long checksum = 0;
		final long started = System.nanoTime();
		for (int i = 0; i < 100000; i++)
		{
			checksum += first.canonicalKey().equals(second.canonicalKey()) ? 1 : 0;
			checksum += PhantomEconomyProjection.craft(new CraftRequest(acquisition, recipe, true, recipe.getLevel(), inventory, 100000, 100000, i, _policy)).nextRngState();
			checksum += PhantomEconomyProjection.enchant(new EnchantRequest(enchantRequest.goal(), enchantRequest.target(), enchantRequest.targetObjectId(), enchantRequest.enchantLevel(), enchantRequest.targetLocation(), enchantRequest.scrollObjectId(), enchantRequest.scrollItemId(), enchantRequest.supportObjectId(), enchantRequest.supportItemId(), enchantRequest.augmented(), enchantRequest.elemented(), enchantRequest.timeLimited(), enchantRequest.leased(), enchantRequest.replacementEvidence(), enchantRequest.expenseBudget(), i, _policy)).nextRngState();
		}
		for (int i = 0; i < 10000; i++)
		{
			checksum += PhantomEconomyProjection.craft(new CraftRequest(acquisition, recipe, true, recipe.getLevel(), inventory, 100000, 100000, i, _policy)).nextRngState();
			checksum += PhantomEconomyProjection.enchant(new EnchantRequest(enchantRequest.goal(), enchantRequest.target(), enchantRequest.targetObjectId(), enchantRequest.enchantLevel(), enchantRequest.targetLocation(), enchantRequest.scrollObjectId(), enchantRequest.scrollItemId(), enchantRequest.supportObjectId(), enchantRequest.supportItemId(), enchantRequest.augmented(), enchantRequest.elemented(), enchantRequest.timeLimited(), enchantRequest.leased(), enchantRequest.replacementEvidence(), enchantRequest.expenseBudget(), i, _policy)).nextRngState();
			final PhantomEconomyOperation replay = operation(1, 999001, 100 + i, Kind.SELF_CRAFT, 1, i);
			checksum += replay.operationId().equals(operation(1, 999001, 100 + i, Kind.SELF_CRAFT, 1, i).operationId()) ? 1 : 0;
		}
		final long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
		PhantomAssertions.assertTrue(checksum != 0, "Economy performance smoke was optimized away.");
		context.record("economy.performanceMillis", elapsedMillis);
		context.record("economy.performanceChecksum", checksum);
	}

	private EnchantOutcome assertEnchantBranch(EnchantCandidate candidate, Result expected)
	{
		final EnchantOutcome outcome = findEnchantOutcome(candidate, expected);
		PhantomAssertions.assertTrue(outcome != null, "Production enchant data did not expose deterministic " + expected + ".");
		PhantomAssertions.assertEquals(PhantomEconomyProjection.enchantAuthority(candidate.target(), candidate.enchantLevel(), candidate.scroll(), null, _policy), outcome.authorityHash(), "Enchant authority drifted.");
		return outcome;
	}

	private EnchantOutcome findEnchantOutcome(EnchantCandidate candidate, Result expected)
	{
		final PhantomEnchantGoalSpec goal = enchantGoal(candidate);
		for (long state = 0; state < 10000; state++)
		{
			final EnchantOutcome outcome = PhantomEconomyProjection.enchant(enchantRequest(goal, candidate, ItemLocation.INVENTORY, state));
			if (outcome.result() == expected)
			{
				return outcome;
			}
		}
		return null;
	}

	private PhantomEnchantGoalSpec enchantGoal(EnchantCandidate candidate)
	{
		final long reserve = Math.max(0, candidate.target().getReferencePrice());
		return new PhantomEnchantGoalSpec(880001, candidate.enchantLevel() + 1, 16, 0, 0, true, reserve, Set.of(candidate.scroll().getId()), Set.of());
	}

	private EnchantRequest enchantRequest(PhantomEnchantGoalSpec goal, EnchantCandidate candidate, ItemLocation location, long rngState)
	{
		return new EnchantRequest(goal, candidate.target(), goal.targetObjectId(), candidate.enchantLevel(), location, 880002, candidate.scroll().getId(), 0, 0, false, false, false, false, Long.MAX_VALUE, Long.MAX_VALUE, rngState, _policy);
	}

	private static EnchantCandidate selectEnchantCandidate(Boolean safe, boolean blessed)
	{
		final List<EnchantScroll> scrolls = EnchantItemData.getInstance().getScrolls().stream().sorted(Comparator.comparingInt(EnchantScroll::getId)).toList();
		for (EnchantScroll scroll : scrolls)
		{
			if (((safe != null) && (scroll.isSafe() != safe.booleanValue())) || (scroll.isBlessed() != blessed) || ((safe == null) && blessed))
			{
				continue;
			}
			for (ItemTemplate target : ItemData.getInstance().getAllItems())
			{
				if ((target == null) || target.isStackable())
				{
					continue;
				}
				for (int enchantLevel = 0; enchantLevel <= 15; enchantLevel++)
				{
					final double chance = scroll.getChance(target, enchantLevel);
					if (scroll.isValid(target, enchantLevel, null) && (chance >= 0) && (chance < 100))
					{
						return new EnchantCandidate(target, scroll, enchantLevel);
					}
				}
			}
		}
		throw new AssertionError("No compatible production enchant candidate for safe=" + safe + ", blessed=" + blessed + ".");
	}

	private static RecipeList selectRecipe()
	{
		return Arrays.stream(RecipeData.getInstance().getAllItemIds()).mapToObj(RecipeData.getInstance()::getRecipeByItemId).filter(recipe -> (recipe != null) && (recipe.getLevel() > 0) && (recipe.getLevel() <= 1) && (recipe.getSuccessRate() > 0) && (recipe.getRecipes().length > 0) && (recipe.getRecipes().length <= 8) && Arrays.stream(recipe.getRecipes()).allMatch(ingredient -> (ingredient.getQuantity() > 0) && (ingredient.getQuantity() <= 1000) && (ingredient.getItemId() != recipe.getItemId())) && Arrays.stream(recipe.getRecipes()).map(RecipeHolder::getItemId).distinct().count() == recipe.getRecipes().length && Arrays.stream(recipe.getStatUse()).allMatch(stat -> (stat.getType() == StatType.HP) || (stat.getType() == StatType.MP)) && (ItemData.getInstance().getTemplate(recipe.getItemId()) != null) && (ItemData.getInstance().getTemplate(recipe.getItemId()).getTime() == -1)).sorted(Comparator.comparingInt(RecipeList::getId)).findFirst().orElseThrow(() -> new AssertionError("No bounded production recipe is available."));
	}

	private static PhantomAcquisitionState acquisition(RecipeList recipe)
	{
		final String sourceId = PhantomEconomyOperation.sha256("recipe:" + recipe.getId());
		final Source source = new Source(sourceId, Method.RECIPE_PREPARATION, 0, recipe.getItemId(), "recipe:" + recipe.getId(), "self", "self", 0, 0, 0, 0, 0);
		final Candidate candidate = new Candidate(sourceId, Method.RECIPE_PREPARATION, 100, 0, 0, "");
		final List<RecipeNode> nodes = new ArrayList<>();
		nodes.add(new RecipeNode(recipe.getItemId(), recipe.getCount(), 0, recipe.getCount(), recipe.getId(), 0, false));
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			nodes.add(new RecipeNode(ingredient.getItemId(), ingredient.getQuantity(), ingredient.getQuantity(), 0, 0, 1, true));
		}
		final int skillId = recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId();
		final RecipePlan plan = new RecipePlan(recipe.getId(), recipe.getItemId(), recipe.getCount(), 1, recipe.getCount(), recipe.getSuccessRate(), recipe.isDwarvenRecipe(), skillId, recipe.getLevel(), nodes, List.of(), "recipe.ready");
		return new PhantomAcquisitionState(HASHES, 7001, 3, recipe.getItemId(), recipe.getCount(), 0, 0, 0, Status.PLANNING_ONLY, source, List.of(candidate), 0, 0, Phase.NONE, 0, 0, 0, plan, List.of(), 1);
	}

	private static Map<Integer, Long> ingredientInventory(RecipeList recipe)
	{
		final Map<Integer, Long> inventory = new HashMap<>();
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			inventory.merge(ingredient.getItemId(), (long) ingredient.getQuantity(), Math::addExact);
		}
		return Map.copyOf(inventory);
	}

	private static PhantomEconomyOperation operation(long profileId, int characterObjectId, long goalId, Kind kind, int attempt, long now)
	{
		final String authority = PhantomEconomyOperation.sha256("authority:" + goalId);
		final String intent = PhantomEconomyOperation.sha256("intent:" + goalId + ":" + attempt);
		return new PhantomEconomyOperation(new Identity(profileId, characterObjectId, goalId, 0, attempt, "economy.test:" + goalId, 1, 1), kind, State.PREPARED, authority, intent, PhantomEconomyOperation.utf8Payload("before"), PhantomEconomyOperation.utf8Payload("intent"), now, now, Math.addExact(now, 120000), 0);
	}

	private static Reservation itemCount(long profileId, int ownerObjectId, int itemId)
	{
		return new Reservation(profileId, ownerObjectId, ResourceKind.ITEM_COUNT, 0, itemId, 1, 1, 0, "INVENTORY");
	}

	private static Reservation itemObject(long profileId, int ownerObjectId, int objectId, int enchantLevel)
	{
		return new Reservation(profileId, ownerObjectId, ResourceKind.ITEM_OBJECT, objectId, 0, 0, 1, enchantLevel, "INVENTORY");
	}

	private static Audit audit(Result result, String reason)
	{
		return new Audit(result, reason, new byte[0]);
	}

	private static PhantomProfile createProfile()
	{
		return PhantomProfileRepository.open().create(null);
	}

	private static void deleteProfile(long profileId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM phantom_profiles WHERE profile_id=?"))
		{
			PhantomAssertions.assertEquals(TEST_DATABASE, connection.getCatalog(), "Economy cleanup touched a non-test database.");
			statement.setLong(1, profileId);
			statement.executeUpdate();
		}
	}

	private static Thread reservationThread(String name, CountDownLatch ready, CountDownLatch start, AtomicReference<Throwable> failure, ThrowingAction action)
	{
		return new Thread(() ->
		{
			try
			{
				ready.countDown();
				start.await();
				action.run();
			}
			catch (Throwable throwable)
			{
				failure.compareAndSet(null, throwable);
			}
		}, name);
	}

	private static void restoreItemCount(Player player, int itemId, long expected)
	{
		final long current = player.getInventory().getInventoryItemCount(itemId, -1);
		if (current < expected)
		{
			PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, itemId, expected - current, player, PhantomEconomySuite.class) != null, "Could not restore economy item baseline.");
		}
		else if (current > expected)
		{
			final Item item = player.getInventory().getItemByItemId(itemId);
			PhantomAssertions.assertTrue((item != null) && (player.getInventory().destroyItem(ItemProcessType.DESTROY, item, current - expected, player, PhantomEconomySuite.class) != null), "Could not restore economy item baseline.");
		}
	}

	@FunctionalInterface
	private interface ThrowingAction
	{
		void run() throws Exception;
	}

	private record EnchantCandidate(ItemTemplate target, EnchantScroll scroll, int enchantLevel)
	{
	}
}

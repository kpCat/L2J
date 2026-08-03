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
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.enchant.EnchantScroll;
import org.l2jmobius.gameserver.model.item.enchant.EnchantSupportItem;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
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
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStore;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash.CanonicalItem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Loadout;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction.FaultPoint;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyBackgroundTransaction.TransactionResult;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyDecision;
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
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyService;
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
				registry.add("initiator-link-mismatch", this::testInitiatorLinkMismatch);
				registry.add("participant-link-mismatch", this::testParticipantLinkMismatch);
				registry.add("unlinked-profile-rejected", this::testUnlinkedProfileRejected);
				registry.add("participant-busy-from-reservation", this::testParticipantBusyFromReservation);
				registry.add("participant-busy-as-initiator", this::testParticipantBusyAsInitiator);
				registry.add("item-count-object-overlap", this::testItemCountObjectOverlap);
				registry.add("item-object-overlap-matrix", this::testItemObjectOverlapMatrix);
				registry.add("adena-overlap-and-item-disjoint", this::testAdenaItemOverlap);
				registry.add("recipe-class-isolation", this::testRecipeClassIsolation);
				registry.add("skill-class-isolation", this::testSkillClassIsolation);
				registry.add("participant-bound", this::testParticipantBound);
			}
			case SELF_CRAFT_ACTIVE ->
			{
				registry.add("recipe-manager-observer", this::testActiveCraft);
				registry.add("decision-service-repeatable-lifecycle", this::testActiveCraftServiceLifecycle);
			}
			case SELF_CRAFT_BACKGROUND ->
			{
				registry.add("exact-recipe-projection", this::testBackgroundCraft);
				registry.add("craft-authority-facts", this::testCraftAuthorityFacts);
				registry.add("decision-service-atomic-transaction", this::testBackgroundCraftServiceLifecycle);
				registry.add("actual-outcome-attribution", this::testBackgroundCraftOutcomeAttribution);
				registry.add("atomic-fault-matrix", this::testBackgroundCraftFaultMatrix);
			}
			case ENCHANT_ACTIVE ->
			{
				registry.add("canonical-service", this::testActiveEnchant);
				registry.add("canonical-actor-guards", this::testEnchantActorGuards);
				registry.add("decision-service-full-chain", this::testActiveEnchantServiceLifecycle);
				registry.add("non-atomic-restart-windows", this::testActiveEnchantRestartWindows);
				registry.add("ordinary-packet-parity-matrix", this::testPacketParityMatrix);
			}
			case ENCHANT_BACKGROUND ->
			{
				registry.add("deterministic-branches", this::testBackgroundEnchant);
				registry.add("enchant-authority-and-risk", this::testEnchantAuthorityAndRisk);
				registry.add("decision-service-atomic-transaction", this::testBackgroundEnchantServiceLifecycle);
				registry.add("actual-outcome-matrix", this::testBackgroundEnchantOutcomeMatrix);
				registry.add("atomic-fault-matrix", this::testBackgroundEnchantFaultMatrix);
			}
			case RESTART_TRANSITION ->
			{
				registry.add("expiry-and-fail-stop", this::testRestartAndExpiry);
				registry.add("shutdown-terminalizes-claims", this::testShutdownTerminalization);
			}
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
		PhantomAssertions.assertTrue(State.OBSERVING.canTransitionTo(State.ABORTED), "Exactly observed pre-effect rejection must be abortable.");
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
		final PhantomProfile firstProfile = createProfile(910001);
		final PhantomProfile secondProfile = createProfile(910002);
		final PhantomProfile participant = createProfile(990001);
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
			final List<Reservation> forward = List.of(itemCount(participant.profileId(), 990001, 57), itemCount(participant.profileId(), 990001, 58));
			final List<Reservation> reverse = List.of(itemCount(participant.profileId(), 990001, 58), itemCount(participant.profileId(), 990001, 57));
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
			deleteProfile(participant.profileId());
		}
	}

	private void testMultiOwnerOrder(PhantomTestContext context) throws Exception
	{
		final PhantomProfile primary = createProfile(990002);
		final PhantomProfile participant = createProfile(990003);
		final PhantomProfile third = createProfile(990004);
		final PhantomProfile fourth = createProfile(990005);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(primary.profileId(), 990002, 13, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			final Reservation secondary = new Reservation(participant.profileId(), 990003, ResourceKind.ITEM_COUNT, 0, 61, 2, 2, 0, "INVENTORY");
			final Reservation owner = new Reservation(primary.profileId(), 990002, ResourceKind.ITEM_COUNT, 0, 62, 3, 3, 0, "INVENTORY");
			final Reservation thirdOwner = new Reservation(third.profileId(), 990004, ResourceKind.ITEM_COUNT, 0, 63, 4, 4, 0, "INVENTORY");
			final Reservation fourthOwner = new Reservation(fourth.profileId(), 990005, ResourceKind.ITEM_COUNT, 0, 64, 5, 5, 0, "INVENTORY");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(operation, List.of(fourthOwner, secondary, thirdOwner, owner)).status(), "Four-participant reservation was rejected.");
			final List<Reservation> stored = service.findReservations(operation.operationId());
			PhantomAssertions.assertEquals(List.of(owner.canonicalKey(), secondary.canonicalKey(), thirdOwner.canonicalKey(), fourthOwner.canonicalKey()).stream().sorted().toList(), stored.stream().map(Reservation::canonicalKey).toList(), "Multi-owner locks are not stored canonically.");
			service.transition(operation.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), audit(Result.ERROR, "operation.conflict"));
			context.record("economy.participants", 4);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(primary.profileId());
			deleteProfile(participant.profileId());
			deleteProfile(third.profileId());
			deleteProfile(fourth.profileId());
		}
	}

	private void testInitiatorLinkMismatch(PhantomTestContext context) throws Exception
	{
		final PhantomProfile profile = createProfile(940001);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(profile.profileId(), 940002, 40, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.IDENTITY_CONFLICT, service.reserve(operation, List.of(itemCount(profile.profileId(), 940001, 57))).status(), "Initiator profile link mismatch was admitted.");
			PhantomAssertions.assertTrue(service.find(operation.operationId()).isEmpty(), "Rejected initiator identity created an operation row.");
			context.record("economy.initiatorMismatch", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(profile.profileId());
		}
	}

	private void testParticipantLinkMismatch(PhantomTestContext context) throws Exception
	{
		final PhantomProfile initiator = createProfile(940011);
		final PhantomProfile participant = createProfile(940012);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(initiator.profileId(), 940011, 41, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.IDENTITY_CONFLICT, service.reserve(operation, List.of(itemCount(participant.profileId(), 940013, 57))).status(), "Participant profile link mismatch was admitted.");
			context.record("economy.participantMismatch", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(initiator.profileId());
			deleteProfile(participant.profileId());
		}
	}

	private void testUnlinkedProfileRejected(PhantomTestContext context) throws Exception
	{
		final PhantomProfile profile = createProfile();
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(profile.profileId(), 940021, 42, Kind.ITEM_ENCHANT, 1, System.currentTimeMillis());
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.IDENTITY_CONFLICT, service.reserve(operation, List.of(itemCount(profile.profileId(), 940021, 57))).status(), "Unlinked participant profile was admitted.");
			context.record("economy.unlinkedRejected", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(profile.profileId());
		}
	}

	private void testParticipantBusyFromReservation(PhantomTestContext context) throws Exception
	{
		testParticipantBusy(context, false);
	}

	private void testParticipantBusyAsInitiator(PhantomTestContext context) throws Exception
	{
		testParticipantBusy(context, true);
	}

	private void testParticipantBusy(PhantomTestContext context, boolean participantInitiates) throws Exception
	{
		final PhantomProfile first = createProfile(participantInitiates ? 940031 : 940041);
		final PhantomProfile participant = createProfile(participantInitiates ? 940032 : 940042);
		final PhantomProfile other = participantInitiates ? null : createProfile(940043);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation held = operation(first.profileId(), participantInitiates ? 940031 : 940041, participantInitiates ? 43 : 44, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(held, List.of(itemCount(participant.profileId(), participantInitiates ? 940032 : 940042, 57))).status(), "Participant busy fixture was not reserved.");
			final PhantomProfile challenger = participantInitiates ? participant : other;
			final int challengerObjectId = participantInitiates ? 940032 : 940043;
			final PhantomEconomyOperation competing = operation(challenger.profileId(), challengerObjectId, participantInitiates ? 45 : 46, Kind.ITEM_ENCHANT, 1, System.currentTimeMillis());
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.PROFILE_BUSY, service.reserve(competing, List.of(itemCount(participant.profileId(), participantInitiates ? 940032 : 940042, 58))).status(), "Active participant exclusivity was bypassed.");
			service.transition(held.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), audit(Result.ERROR, "operation.conflict"));
			context.record(participantInitiates ? "economy.participantInitiatorBusy" : "economy.participantReservationBusy", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(first.profileId());
			deleteProfile(participant.profileId());
			if (other != null)
			{
				deleteProfile(other.profileId());
			}
		}
	}

	private void testItemCountObjectOverlap(PhantomTestContext context) throws Exception
	{
		final PhantomProfile profile = createProfile(940051);
		final PhantomProfile reverseProfile = createProfile(940052);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(profile.profileId(), 940051, 47, Kind.ITEM_ENCHANT, 1, System.currentTimeMillis());
			final Reservation object = new Reservation(profile.profileId(), 940051, ResourceKind.ITEM_OBJECT, 840051, 57, 0, 1, 0, "INVENTORY");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESOURCE_CONFLICT, service.reserve(operation, List.of(itemCount(profile.profileId(), 940051, 57), object)).status(), "ITEM_COUNT/ITEM_OBJECT semantic overlap was admitted.");
			final PhantomEconomyOperation reverseOperation = operation(reverseProfile.profileId(), 940052, 48, Kind.ITEM_ENCHANT, 1, System.currentTimeMillis());
			final Reservation reverseObject = new Reservation(reverseProfile.profileId(), 940052, ResourceKind.ITEM_OBJECT, 840052, 57, 0, 1, 0, "INVENTORY");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESOURCE_CONFLICT, service.reserve(reverseOperation, List.of(reverseObject, itemCount(reverseProfile.profileId(), 940052, 57))).status(), "ITEM_OBJECT/ITEM_COUNT reverse semantic overlap was admitted.");
			context.record("economy.itemSemanticOverlap", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(profile.profileId());
			deleteProfile(reverseProfile.profileId());
		}
	}

	private void testAdenaItemOverlap(PhantomTestContext context) throws Exception
	{
		final PhantomProfile profile = createProfile(940061);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(profile.profileId(), 940061, 48, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			final Reservation adena = new Reservation(profile.profileId(), 940061, ResourceKind.ADENA, 0, 57, 1, 1, 0, "INVENTORY");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(operation, List.of(adena)).status(), "ADENA overlap fixture was not reserved.");
			final Reservation equivalentAdena = new Reservation(profile.profileId(), 940061, ResourceKind.ADENA, 0, 999, 1, 1, 0, "INVENTORY");
			try (var conflict = service.claimWriter(profile.profileId(), null, List.of(equivalentAdena)))
			{
				PhantomAssertions.assertFalse(conflict.acquired(), "ADENA semantic overlap bypassed the durable reservation.");
			}
			service.transition(operation.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), audit(Result.ERROR, "operation.conflict"));
			final PhantomEconomyOperation disjoint = operation(profile.profileId(), 940061, 49, Kind.SELF_CRAFT, 2, System.currentTimeMillis());
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(disjoint, List.of(adena, itemCount(profile.profileId(), 940061, 57))).status(), "ADENA and ITEM_COUNT were incorrectly treated as cross-kind overlap.");
			service.transition(disjoint.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), audit(Result.ERROR, "operation.conflict"));
			context.record("economy.adenaSemanticOverlap", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(profile.profileId());
		}
	}

	private void testItemObjectOverlapMatrix(PhantomTestContext context) throws Exception
	{
		final PhantomProfile profile = createProfile(940071);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final Reservation first = new Reservation(profile.profileId(), 940071, ResourceKind.ITEM_OBJECT, 840071, 57, 0, 1, 0, "INVENTORY");
			final Reservation second = new Reservation(profile.profileId(), 940071, ResourceKind.ITEM_OBJECT, 840072, 57, 0, 1, 0, "INVENTORY");
			final Reservation duplicate = new Reservation(profile.profileId(), 940071, ResourceKind.ITEM_OBJECT, 840071, 58, 0, 1, 0, "INVENTORY");
			final Reservation count = itemCount(profile.profileId(), 940071, 57);
			PhantomAssertions.assertFalse(first.overlaps(second) || second.overlaps(first), "Distinct non-stackable object IDs collided by template ID.");
			PhantomAssertions.assertTrue(first.overlaps(duplicate) && duplicate.overlaps(first), "Duplicate object identity was not symmetric.");
			PhantomAssertions.assertTrue(first.overlaps(count) && count.overlaps(first), "ITEM_OBJECT/ITEM_COUNT overlap was not symmetric.");
			final PhantomEconomyOperation operation = operation(profile.profileId(), 940071, 50, Kind.ITEM_ENCHANT, 1, System.currentTimeMillis());
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(operation, List.of(second, first)).status(), "Distinct object IDs for one item were rejected within an operation.");
			service.transition(operation.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), audit(Result.ERROR, "operation.conflict"));
			context.record("economy.itemObjectOverlapMatrix", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(profile.profileId());
		}
	}

	private void testRecipeClassIsolation(PhantomTestContext context) throws Exception
	{
		testClassIsolation(context, ResourceKind.RECIPE, 49);
	}

	private void testSkillClassIsolation(PhantomTestContext context) throws Exception
	{
		testClassIsolation(context, ResourceKind.SKILL, 50);
	}

	private void testClassIsolation(PhantomTestContext context, ResourceKind kind, long goalId) throws Exception
	{
		final int objectId = kind == ResourceKind.RECIPE ? 940071 : 940072;
		final PhantomProfile profile = createProfile(objectId);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(profile.profileId(), objectId, goalId, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			final Reservation mainClass = new Reservation(profile.profileId(), objectId, 0, kind, 0, 172, 0, 0, 0, "");
			final Reservation subclass = new Reservation(profile.profileId(), objectId, 1, kind, 0, 172, 0, 0, 0, "");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(operation, List.of(mainClass, subclass)).status(), kind + " class isolation was rejected.");
			service.transition(operation.operationId(), State.RESERVED, State.ABORTED, System.currentTimeMillis(), audit(Result.ERROR, "operation.conflict"));
			context.record("economy." + kind.name().toLowerCase(java.util.Locale.ROOT) + "ClassIsolation", true);
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(profile.profileId());
		}
	}

	private void testParticipantBound(PhantomTestContext context) throws Exception
	{
		final List<PhantomProfile> profiles = List.of(createProfile(940081), createProfile(940082), createProfile(940083), createProfile(940084), createProfile(940085));
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			service.start();
			final PhantomEconomyOperation operation = operation(profiles.getFirst().profileId(), 940081, 51, Kind.SELF_CRAFT, 1, System.currentTimeMillis());
			final List<Reservation> resources = new ArrayList<>();
			for (int index = 1; index < profiles.size(); index++)
			{
				resources.add(itemCount(profiles.get(index).profileId(), 940081 + index, 57 + index));
			}
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> service.reserve(operation, resources), "Fifth economy participant was admitted.");
			context.record("economy.participantBound", _policy.limits().participantsPerOperation());
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			for (PhantomProfile profile : profiles)
			{
				deleteProfile(profile.profileId());
			}
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

	private void testActiveCraftServiceLifecycle(PhantomTestContext context) throws Exception
	{
		final RecipeList recipe = selectRepeatableRecipe();
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(repository);
		final PhantomAcquisitionStore acquisitions = new PhantomAcquisitionStore(repository, goals);
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		final List<PhantomEconomyReservationService> reservationServices = new ArrayList<>();
		Player player = null;
		final Map<Integer, Long> baselines = new HashMap<>();
		boolean recipeAdded = false;
		Skill addedSkill = null;
		double hpBaseline = 0;
		double mpBaseline = 0;
		try
		{
			materialization.start();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Repeatable craft Phantom did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Repeatable craft Player is absent.");
			final Player activePlayer = player;
			hpBaseline = player.getCurrentHp();
			mpBaseline = player.getCurrentMp();
			final int skillId = recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId();
			final Skill skill = recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getSkill() : CommonSkill.CREATE_COMMON.getSkill();
			PhantomAssertions.assertTrue(skill != null, "Repeatable craft skill is unavailable.");
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
				PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, ingredient.getItemId(), Math.multiplyExact((long) ingredient.getQuantity(), 3), player, this) != null, "Could not fund three exact craft attempts.");
			}
			final long targetBaseline = player.getInventory().getInventoryItemCount(recipe.getItemId(), -1);
			baselines.putIfAbsent(recipe.getItemId(), targetBaseline);
			final long required = Math.multiplyExact(3L, recipe.getCount());
			final long goalId = 2200220101L;
			final long goalRevision = 3;
			final PhantomGoal initialGoal = acquisitionGoal(goalId, goalRevision, recipe.getItemId(), targetBaseline, required);
			goals.insert(profile.profileId(), initialGoal);
			acquisitions.insert(profile.profileId(), acquisition(recipe, goalId, goalRevision, targetBaseline, required, player.getSkillLevel(skillId)));

			PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
			reservations.start();
			reservationServices.add(reservations);
			PhantomEconomyService service = economyService(reservations, materialization, acquisitions, goals, repository);

			final PhantomGoal cancellationGoal = goals.load(profile.profileId()).orElseThrow().goal();
			final DecisionHarness cancelled = decisionHarness(service, profile.profileId(), cancellationGoal, PhantomActivityState.ACTIVE, 1, 1, 1);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, cancelled.execute(0, false).type(), "Cancellation fixture did not reserve.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, cancelled.execute(1, false).type(), "Cancellation fixture did not dispatch.");
			final String cancelledOperationId = reservations.findActive(profile.profileId()).orElseThrow().operationId();
			PhantomAssertions.assertEquals(PhantomStepResult.Type.CANCELLED, cancelled.execute(2, true).type(), "DISPATCHING cancellation was not terminalized.");
			PhantomAssertions.assertEquals(State.ABORTED, reservations.find(cancelledOperationId).orElseThrow().state(), "DISPATCHING cancellation did not abort before the canonical action.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "DISPATCHING cancellation retained claims.");

			final DecisionHarness observingCancelled = decisionHarness(service, profile.profileId(), cancellationGoal, PhantomActivityState.ACTIVE, 1, 2, 2);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, observingCancelled.execute(0, false).type(), "OBSERVING cancellation fixture did not reserve.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, observingCancelled.execute(1, false).type(), "OBSERVING cancellation fixture did not dispatch.");
			final String observingOperationId = reservations.findActive(profile.profileId()).orElseThrow().operationId();
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, reservations.transition(observingOperationId, State.DISPATCHING, State.OBSERVING, System.currentTimeMillis(), null).status(), "Cancellation fixture did not cross the action-issued boundary.");
			final Map<Integer, Long> beforeObservingCancel = reservations.findReservations(observingOperationId).stream().filter(resource -> resource.kind() == ResourceKind.ITEM_COUNT).collect(java.util.stream.Collectors.toMap(Reservation::itemId, resource -> activePlayer.getInventory().getInventoryItemCount(resource.itemId(), -1)));
			PhantomAssertions.assertEquals(PhantomStepResult.Type.CANCELLED, observingCancelled.execute(2, true).type(), "OBSERVING cancellation did not use service-owned fail-stop.");
			PhantomAssertions.assertEquals(State.INCONSISTENT, reservations.find(observingOperationId).orElseThrow().state(), "OBSERVING cancellation was blindly aborted.");
			beforeObservingCancel.forEach((itemId, count) -> PhantomAssertions.assertEquals(count.longValue(), activePlayer.getInventory().getInventoryItemCount(itemId, -1), "OBSERVING cancellation invoked the canonical craft action."));
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "OBSERVING cancellation retained claims.");

			final DecisionHarness staleAuthority = decisionHarness(service, profile.profileId(), cancellationGoal, PhantomActivityState.ACTIVE, 1, 3, 3);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, staleAuthority.execute(0, false).type(), "Craft authority-drift fixture did not reserve.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, staleAuthority.execute(1, false).type(), "Craft authority-drift fixture did not dispatch.");
			final String staleOperationId = reservations.findActive(profile.profileId()).orElseThrow().operationId();
			final Map<Integer, Long> beforeAuthorityDrift = reservations.findReservations(staleOperationId).stream().filter(resource -> resource.kind() == ResourceKind.ITEM_COUNT).collect(java.util.stream.Collectors.toMap(Reservation::itemId, resource -> activePlayer.getInventory().getInventoryItemCount(resource.itemId(), -1)));
			final boolean masterwork = PlayerConfig.CRAFT_MASTERWORK;
			try
			{
				PlayerConfig.CRAFT_MASTERWORK = !masterwork;
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, staleAuthority.execute(2, false).type(), "Craft authority drift did not reach a stale terminal result.");
			}
			finally
			{
				PlayerConfig.CRAFT_MASTERWORK = masterwork;
			}
			PhantomAssertions.assertEquals(State.ABORTED, reservations.find(staleOperationId).orElseThrow().state(), "Craft authority drift was not fail-stopped before action.");
			beforeAuthorityDrift.forEach((itemId, count) -> PhantomAssertions.assertEquals(count.longValue(), activePlayer.getInventory().getInventoryItemCount(itemId, -1), "Craft authority drift mutated canonical inventory."));
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Craft authority drift retained claims.");

			final DecisionHarness outputDrift = decisionHarness(service, profile.profileId(), cancellationGoal, PhantomActivityState.ACTIVE, 1, 4, 4);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, outputDrift.execute(0, false).type(), "Craft output-drift fixture did not reserve.");
			final String outputDriftOperationId = reservations.findActive(profile.profileId()).orElseThrow().operationId();
			final long outputBeforeExternalWrite = player.getInventory().getInventoryItemCount(recipe.getItemId(), -1);
			PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, recipe.getItemId(), 1, player, this) != null, "Could not inject an external craft target delta.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, outputDrift.execute(1, false).type(), "Craft output-drift fixture did not dispatch.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, outputDrift.execute(2, false).type(), "Craft output drift did not reach a stale terminal result.");
			PhantomAssertions.assertEquals(State.ABORTED, reservations.find(outputDriftOperationId).orElseThrow().state(), "External craft output delta was attributed to the reserved operation.");
			PhantomAssertions.assertEquals(0L, acquisitions.load(profile.profileId()).orElseThrow().state().progress(), "External craft output delta advanced acquisition progress.");
			restoreItemCount(player, recipe.getItemId(), outputBeforeExternalWrite);
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Craft output drift retained claims.");

			final List<String> committedOperationIds = new ArrayList<>();
			for (int attempt = 0; attempt < 3; attempt++)
			{
				player.setCurrentHp(player.getMaxHp());
				player.setCurrentMp(player.getMaxMp());
				final PhantomGoal currentGoal = goals.load(profile.profileId()).orElseThrow().goal();
				DecisionHarness harness = decisionHarness(service, profile.profileId(), currentGoal, PhantomActivityState.ACTIVE, 1, attempt + 5, attempt + 5);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(0, false).type(), "Repeatable craft reserve failed at attempt " + attempt + ".");
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(0, false).type(), "Repeatable craft reserve was not idempotent at attempt " + attempt + ".");
				if (attempt == 0)
				{
					final PhantomEconomyReservationService restarted = new PhantomEconomyReservationService(_policy);
					PhantomAssertions.assertTrue(restarted.start(), "Predispatch economy restart did not reopen admission.");
					reservationServices.add(restarted);
					reservations = restarted;
					service = economyService(reservations, materialization, acquisitions, goals, repository);
					harness = decisionHarness(service, profile.profileId(), currentGoal, PhantomActivityState.ACTIVE, 1, attempt + 5, attempt + 5);
				}
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(1, false).type(), "Repeatable craft dispatch failed at attempt " + attempt + ".");
				harness = decisionHarness(service, profile.profileId(), currentGoal, PhantomActivityState.ACTIVE, 1, attempt + 5, attempt + 5);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(0, false).type(), "New craft plan did not resume DISPATCHING at attempt " + attempt + ".");
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(1, false).type(), "New craft plan did not dispatch idempotently at attempt " + attempt + ".");
				committedOperationIds.add(reservations.findActive(profile.profileId()).orElseThrow().operationId());
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(2, false).type(), "Repeatable craft reconciliation failed at attempt " + attempt + ".");
				if (attempt == 0)
				{
					final Map<Integer, Long> afterEffect = new HashMap<>();
					for (int itemId : baselines.keySet())
					{
						afterEffect.put(itemId, player.getInventory().getInventoryItemCount(itemId, -1));
					}
					PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, harness.execute(2, false).type(), "Process-local retry after craft effect did not terminate without redispatch.");
					afterEffect.forEach((itemId, count) -> PhantomAssertions.assertEquals(count.longValue(), activePlayer.getInventory().getInventoryItemCount(itemId, -1), "Process-local craft retry consumed or produced an item twice."));
				}
				final PhantomAcquisitionState current = acquisitions.load(profile.profileId()).orElseThrow().state();
				PhantomAssertions.assertEquals(Math.multiplyExact(attempt + 1L, recipe.getCount()), current.progress(), "Repeatable craft progress did not advance exactly once.");
				PhantomAssertions.assertEquals(attempt + 1, current.receipts().size(), "Repeatable craft receipt count drifted.");
				PhantomAssertions.assertEquals(attempt == 2 ? Status.COMPLETED : Status.PLANNING_ONLY, current.status(), "Repeatable RecipePlan lifecycle entered the wrong status.");
			}
			PhantomAssertions.assertEquals(3L, committedOperationIds.stream().distinct().count(), "Repeatable craft reused an operation identity.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, goals.load(profile.profileId()).orElseThrow().goal().status(), "Three successful craft attempts did not complete the Goal.");
			PhantomAssertions.assertEquals(targetBaseline + required, player.getInventory().getInventoryItemCount(recipe.getItemId(), -1), "Repeatable craft target accumulation drifted.");
			for (RecipeHolder ingredient : recipe.getRecipes())
			{
				PhantomAssertions.assertEquals(baselines.get(ingredient.getItemId()).longValue(), player.getInventory().getInventoryItemCount(ingredient.getItemId(), -1), "Repeatable craft ingredient conservation drifted.");
			}
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Repeatable craft retained reservation claims.");
			PhantomAssertions.assertEquals(3L, scalarLong("SELECT COUNT(*) FROM phantom_economy_audit WHERE profile_id=? AND terminal_state='COMMITTED'", profile.profileId()), "Repeatable craft audit count drifted.");
			context.record("economy.repeatableCraftOperations", committedOperationIds);
		}
		finally
		{
			for (PhantomEconomyReservationService reservations : reservationServices)
			{
				reservations.shutdown(System.currentTimeMillis());
			}
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
				player.setCurrentHp(Math.min(hpBaseline, player.getMaxHp()));
				player.setCurrentMp(Math.min(mpBaseline, player.getMaxMp()));
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

	private void testCraftAuthorityFacts(PhantomTestContext context)
	{
		final RecipeList recipe = selectAuthorityRecipeWithStatUse();
		final PhantomAcquisitionState acquisition = acquisition(recipe);
		final PhantomEconomyProjection.AuthorityFacts facts = PhantomEconomyProjection.craftAuthorityFacts(acquisition, recipe, _policy);
		PhantomAssertions.assertEquals(PhantomEconomyProjection.craftAuthority(acquisition, recipe, _policy), facts.hash(), "Craft authority facts and public hash diverged.");
		final Set<String> keys = facts.facts().stream().map(PhantomEconomyProjection.AuthorityFact::key).collect(java.util.stream.Collectors.toSet());
		for (String required : List.of("policy.hash", "acquisition.catalog_hash", "acquisition.knowledge_hash", "acquisition.progression_hash", "acquisition.selected_source_id", "plan.recipe_list_id", "plan.node_count", "plan.deficit_count", "recipe.recipe_id", "recipe.level", "recipe.dwarven", "recipe.product_item_id", "recipe.product_count", "recipe.success_rate", "recipe.rare_item_id", "recipe.rare_count", "recipe.rarity", "recipe.ingredient_count", "recipe.stat_count", "recipe.stat.0.type", "recipe.stat.0.value", "recipe.craft_skill_id", "recipe.craft_skill_level", "recipe.normal_output.stackable", "recipe.normal_output.time", "recipe.normal_output.weight", "config.alt_game_creation", "config.crafting_enabled", "config.craft_masterwork", "config.craft_masterwork_chance_rate"))
		{
			PhantomAssertions.assertTrue(keys.contains(required), "Craft authority omitted " + required + ".");
		}
		PhantomAssertions.assertFalse(facts.canonical().contains("RecipePlan[") || facts.canonical().contains("RecipeHolder@"), "Craft authority used default object serialization.");
		assertEveryAuthorityFactChangesHash(facts);
		final RecipeList rareRecipe = selectCraftOutcomeRecipe(true);
		final PhantomEconomyProjection.AuthorityFacts rareFacts = PhantomEconomyProjection.craftAuthorityFacts(acquisition(rareRecipe), rareRecipe, _policy);
		final Set<String> rareKeys = rareFacts.facts().stream().map(PhantomEconomyProjection.AuthorityFact::key).collect(java.util.stream.Collectors.toSet());
		for (String required : List.of("recipe.rare_output.present", "recipe.rare_output.item_id", "recipe.rare_output.stackable", "recipe.rare_output.time", "recipe.rare_output.weight"))
		{
			PhantomAssertions.assertTrue(rareKeys.contains(required), "Rare craft authority omitted " + required + ".");
		}
		assertEveryAuthorityFactChangesHash(rareFacts);
		context.record("economy.craftAuthorityFacts", facts.facts().size() + rareFacts.facts().size());
	}

	private void testBackgroundCraftServiceLifecycle(PhantomTestContext context) throws Exception
	{
		try (BackgroundCraftFixture fixture = createBackgroundCraftFixture(3))
		{
			final List<PhantomEconomyReservationService> reservationServices = new ArrayList<>();
			try
			{
				PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
				reservations.start();
				reservationServices.add(reservations);
				final List<String> operationIds = new ArrayList<>();
				for (int attempt = 0; attempt < 3; attempt++)
				{
					final PhantomEconomyService service = economyService(reservations, fixture.materialization(), fixture.acquisitions(), fixture.goals(), fixture.repository());
					final PhantomGoal goal = fixture.goals().load(fixture.profile().profileId()).orElseThrow().goal();
					final DecisionHarness harness = decisionHarness(service, fixture.profile().profileId(), goal, PhantomActivityState.BACKGROUND, 3, attempt + 1, attempt + 1);
					final PhantomStepResult reserveStep = harness.execute(0, false);
					PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, reserveStep.type(), "Background craft reserve failed at attempt " + attempt + ": " + reserveStep + ".");
					PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(1, false).type(), "Background craft dispatch failed at attempt " + attempt + ".");
					final String operationId = reservations.findActive(fixture.profile().profileId()).orElseThrow().operationId();
					operationIds.add(operationId);
					PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(2, false).type(), "Background craft reconcile failed at attempt " + attempt + ".");
					PhantomAssertions.assertEquals(State.COMMITTED, reservations.find(operationId).orElseThrow().state(), "Background craft operation was not committed at attempt " + attempt + ".");
					final PhantomAcquisitionState acquisition = fixture.acquisitions().load(fixture.profile().profileId()).orElseThrow().state();
					PhantomAssertions.assertEquals(Math.multiplyExact(attempt + 1L, fixture.recipe().getCount()), acquisition.progress(), "Background craft progress did not advance exactly once.");
					PhantomAssertions.assertEquals(attempt + 1, acquisition.receipts().size(), "Background craft receipt count drifted.");
					PhantomAssertions.assertEquals(attempt == 2 ? Status.COMPLETED : Status.PLANNING_ONLY, acquisition.status(), "Background repeatable RecipePlan entered the wrong status.");
					if (attempt < 2)
					{
						final PhantomEconomyReservationService restarted = new PhantomEconomyReservationService(_policy);
						PhantomAssertions.assertTrue(restarted.start(), "Background economy restart did not reopen admission after attempt " + attempt + ".");
						reservationServices.add(restarted);
						reservations = restarted;
					}
				}
				PhantomAssertions.assertEquals(3L, operationIds.stream().distinct().count(), "Background repeatable craft reused an operation identity.");
				PhantomAssertions.assertEquals(Status.COMPLETED, fixture.acquisitions().load(fixture.profile().profileId()).orElseThrow().state().status(), "Background craft acquisition did not complete.");
				PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, fixture.goals().load(fixture.profile().profileId()).orElseThrow().goal().status(), "Background craft Goal did not complete.");
				PhantomAssertions.assertEquals(fixture.targetBaseline() + Math.multiplyExact(3L, fixture.recipe().getCount()), inventoryCount(fixture.characterObjectId(), fixture.recipe().getItemId()), "Background craft canonical target count drifted.");
				for (RecipeHolder ingredient : fixture.recipe().getRecipes())
				{
					PhantomAssertions.assertEquals(fixture.baselines().get(ingredient.getItemId()).longValue(), inventoryCount(fixture.characterObjectId(), ingredient.getItemId()), "Background craft ingredient conservation drifted.");
				}
				PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Background craft retained claims.");
				PhantomAssertions.assertEquals(3L, scalarLong("SELECT COUNT(*) FROM phantom_economy_audit WHERE profile_id=? AND terminal_state='COMMITTED'", fixture.profile().profileId()), "Background repeatable craft audit count drifted.");
				context.record("economy.backgroundCraftOperations", operationIds);
			}
			finally
			{
				for (PhantomEconomyReservationService reservations : reservationServices)
				{
					reservations.shutdown(System.currentTimeMillis());
				}
			}
		}
	}

	private void testBackgroundCraftFaultMatrix(PhantomTestContext context) throws Exception
	{
		try (BackgroundCraftFixture fixture = createBackgroundCraftFixture(1))
		{
			final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
			try
			{
				reservations.start();
				int attempt = 1;
				for (FaultPoint faultPoint : FaultPoint.values())
				{
					final PhantomBackgroundState background = loadBackground(fixture.repository(), fixture.profile().profileId());
					final long backgroundRowVersion = fixture.repository().findComponent(fixture.profile().profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().rowVersion();
					final var acquisition = fixture.acquisitions().load(fixture.profile().profileId()).orElseThrow();
					final var goal = fixture.goals().load(fixture.profile().profileId()).orElseThrow();
					final PhantomEconomyBackgroundTransaction quoting = new PhantomEconomyBackgroundTransaction(reservations, _policy);
					final var quote = quoting.quoteCraft(background, acquisition.state());
					PhantomAssertions.assertTrue(quote.executable(), "Fault fixture craft quote was not executable: " + faultPoint);
					final long now = System.currentTimeMillis();
					final PhantomEconomyOperation operation = backgroundOperation(fixture.profile().profileId(), fixture.characterObjectId(), goal.goal(), Kind.SELF_CRAFT, attempt++, quote.authorityHash(), now);
					PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, reservations.reserve(operation, quote.reservations()).status(), "Fault fixture reservation failed: " + faultPoint);
					PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, now, null).status(), "Fault fixture dispatch failed: " + faultPoint);
					final EconomyDurableSnapshot before = economySnapshot(fixture);
					final PhantomEconomyBackgroundTransaction faulting = new PhantomEconomyBackgroundTransaction(DatabaseFactory::getConnection, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.ObjectIdAllocator.production(), reservations, _policy, actual ->
					{
						if (actual == faultPoint)
						{
							throw new InjectedEconomyFailure(faultPoint);
						}
					});
					final TransactionResult result = faulting.executeCraft(new PhantomEconomyBackgroundTransaction.CraftCommand(operation.operationId(), background, backgroundRowVersion, acquisition.state(), acquisition.rowVersion(), goal.goal(), goal.rowVersion(), 1, now));
					if (faultPoint == FaultPoint.AFTER_COMMIT)
					{
						PhantomAssertions.assertEquals(PhantomEconomyBackgroundTransaction.Status.COMMITTED, result.status(), "Post-commit craft fault lost the committed result.");
						PhantomAssertions.assertEquals(State.COMMITTED, reservations.find(operation.operationId()).orElseThrow().state(), "Post-commit craft fault did not retain the terminal operation.");
					}
					else
					{
						PhantomAssertions.assertEquals(PhantomEconomyBackgroundTransaction.Status.BACKEND_FAILURE, result.status(), "Precommit craft fault did not fail atomically: " + faultPoint);
						PhantomAssertions.assertEquals(before, economySnapshot(fixture), "Precommit craft fault changed durable authority: " + faultPoint);
						PhantomAssertions.assertEquals(State.DISPATCHING, reservations.find(operation.operationId()).orElseThrow().state(), "Precommit craft fault changed the operation outside its transaction: " + faultPoint);
						reservations.transition(operation.operationId(), State.DISPATCHING, State.INCONSISTENT, now, audit(Result.INCONSISTENT, "dispatch.ambiguous"));
					}
				}
				PhantomAssertions.assertEquals(12, FaultPoint.values().length, "Economy fault boundary set drifted.");
				PhantomAssertions.assertEquals(fixture.targetBaseline() + fixture.recipe().getCount(), inventoryCount(fixture.characterObjectId(), fixture.recipe().getItemId()), "Fault matrix committed the craft more than once.");
				context.record("economy.backgroundFaultPoints", FaultPoint.values().length);
			}
			finally
			{
				reservations.shutdown(System.currentTimeMillis());
			}
		}
	}

	private void testBackgroundCraftOutcomeAttribution(PhantomTestContext context) throws Exception
	{
		final RecipeList failureRecipe = selectCraftOutcomeRecipe(false);
		final long failureRng = findCraftRngState(failureRecipe, Result.CRAFT_FAILED, false);
		PhantomAssertions.assertTrue(failureRng >= 0, "No deterministic canonical craft failure fixture is available.");
		testBackgroundCraftOutcome(failureRecipe, failureRng, Result.CRAFT_FAILED, false);

		final RecipeList rareRecipe = selectCraftOutcomeRecipe(true);
		final long rareRng = findCraftRngState(rareRecipe, Result.SUCCESS, true);
		PhantomAssertions.assertTrue(rareRng >= 0, "No deterministic rare different-ID craft fixture is available.");
		testBackgroundCraftOutcome(rareRecipe, rareRng, Result.SUCCESS, true);
		context.record("economy.backgroundCraftAttribution", List.of(Result.CRAFT_FAILED, "rare-different-id"));
	}

	private void testBackgroundCraftOutcome(RecipeList recipe, long rngState, Result expected, boolean rare) throws Exception
	{
		try (BackgroundCraftFixture fixture = createBackgroundCraftFixture(recipe, 1, recipe.getCount(), rngState))
		{
			final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
			try
			{
				reservations.start();
				final PhantomBackgroundState background = loadBackground(fixture.repository(), fixture.profile().profileId());
				final long backgroundRowVersion = fixture.repository().findComponent(fixture.profile().profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().rowVersion();
				final var acquisition = fixture.acquisitions().load(fixture.profile().profileId()).orElseThrow();
				final var goal = fixture.goals().load(fixture.profile().profileId()).orElseThrow();
				final PhantomEconomyBackgroundTransaction transaction = new PhantomEconomyBackgroundTransaction(reservations, _policy);
				final var quote = transaction.quoteCraft(background, acquisition.state());
				PhantomAssertions.assertTrue(quote.executable(), "Actual background craft outcome quote was rejected.");
				PhantomAssertions.assertEquals(1L, quote.reservations().stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_COUNT) && (resource.itemId() == recipe.getItemId())).count(), "Craft normal output was not reserved exactly once or was not merged with its ingredient lock.");
				if ((recipe.getRareItemId() > 0) && (recipe.getRareItemId() != recipe.getItemId()))
				{
					PhantomAssertions.assertEquals(1L, quote.reservations().stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_COUNT) && (resource.itemId() == recipe.getRareItemId())).count(), "Craft rare output was not reserved exactly once.");
				}
				final long now = System.currentTimeMillis();
				final PhantomEconomyOperation operation = backgroundOperation(fixture.profile().profileId(), fixture.characterObjectId(), goal.goal(), Kind.SELF_CRAFT, 1, quote.authorityHash(), now);
				PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, reservations.reserve(operation, quote.reservations()).status(), "Actual background craft outcome reservation failed.");
				final Reservation output = quote.reservations().stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_COUNT) && (resource.itemId() == (rare ? recipe.getRareItemId() : recipe.getItemId()))).findFirst().orElseThrow();
				try (var conflict = reservations.claimWriter(fixture.profile().profileId(), null, List.of(output)))
				{
					PhantomAssertions.assertFalse(conflict.acquired(), "External writer bypassed a possible craft output reservation.");
				}
				PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, now, null).status(), "Actual background craft outcome dispatch failed.");
				final TransactionResult result = transaction.executeCraft(new PhantomEconomyBackgroundTransaction.CraftCommand(operation.operationId(), background, backgroundRowVersion, acquisition.state(), acquisition.rowVersion(), goal.goal(), goal.rowVersion(), 1, now));
				PhantomAssertions.assertEquals(PhantomEconomyBackgroundTransaction.Status.COMMITTED, result.status(), "Actual background craft outcome transaction did not commit.");
				PhantomAssertions.assertEquals(expected, result.result(), "Actual background craft outcome result drifted.");
				PhantomAssertions.assertEquals(rare, result.rareCraft(), "Actual background craft rare attribution drifted.");
				final PhantomAcquisitionState after = fixture.acquisitions().load(fixture.profile().profileId()).orElseThrow().state();
				PhantomAssertions.assertEquals(0L, after.progress(), "Non-target craft outcome advanced target progress.");
				PhantomAssertions.assertEquals(rare ? PhantomAcquisitionState.TerminalResult.COMMITTED : PhantomAcquisitionState.TerminalResult.FAILED, after.receipts().getLast().result(), "Craft receipt did not preserve the canonical terminal result.");
				PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, fixture.goals().load(fixture.profile().profileId()).orElseThrow().goal().status(), "Non-target craft outcome completed the Goal.");
				PhantomAssertions.assertEquals(fixture.targetBaseline(), inventoryCount(fixture.characterObjectId(), recipe.getItemId()), "Non-target craft outcome changed the target item count.");
				for (RecipeHolder ingredient : recipe.getRecipes())
				{
					PhantomAssertions.assertEquals(fixture.baselines().get(ingredient.getItemId()).longValue(), inventoryCount(fixture.characterObjectId(), ingredient.getItemId()), "Actual background craft outcome ingredient consumption drifted.");
				}
				final String expectedReason = rare ? "result.rare_product" : "result.craft_failed";
				PhantomAssertions.assertEquals(expectedReason, scalarString("SELECT reason_key FROM phantom_economy_audit WHERE operation_id=?", operation.operationId()), "Craft outcome audit reason drifted.");
				final String consequence = scalarString("SELECT CAST(consequence_payload AS CHAR) FROM phantom_economy_audit WHERE operation_id=?", operation.operationId());
				PhantomAssertions.assertTrue(consequence.contains("rare=" + rare) && consequence.contains("sourceFailure=" + (rare ? "" : "craft.canonical_failure")), "Craft outcome audit attribution is incomplete.");
				if (rare)
				{
					PhantomAssertions.assertTrue(recipe.getRareItemId() != recipe.getItemId(), "Rare attribution fixture did not use a different product ID.");
					PhantomAssertions.assertEquals(fixture.baselines().get(recipe.getRareItemId()) + recipe.getRareCount(), inventoryCount(fixture.characterObjectId(), recipe.getRareItemId()), "Rare different-ID product was not preserved.");
				}
			}
			finally
			{
				reservations.shutdown(System.currentTimeMillis());
			}
		}
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

	private void testActiveEnchantServiceLifecycle(PhantomTestContext context) throws Exception
	{
		final EnchantCandidate candidate = selectEnchantCandidate(Boolean.TRUE, false);
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(repository);
		final PhantomAcquisitionStore acquisitions = new PhantomAcquisitionStore(repository, goals);
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
		Player player = null;
		long targetBaseline = 0;
		long scrollBaseline = 0;
		long adenaBaseline = 0;
		try
		{
			materialization.start();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Full-chain enchant Phantom did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Full-chain enchant Player is absent.");
			targetBaseline = player.getInventory().getInventoryItemCount(candidate.target().getId(), -1);
			scrollBaseline = player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1);
			adenaBaseline = player.getAdena();
			final long replacementReserve = Math.max(0, candidate.target().getReferencePrice());
			if (adenaBaseline < replacementReserve)
			{
				player.getInventory().addItem(ItemProcessType.REWARD, Inventory.ADENA_ID, replacementReserve - adenaBaseline, player, this);
			}
			final Item target = player.getInventory().addItem(ItemProcessType.REWARD, candidate.target().getId(), 1, player, this);
			PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, candidate.scroll().getId(), 1, player, this) != null, "Could not fund full-chain enchant scroll.");
			PhantomAssertions.assertTrue(target != null, "Could not create full-chain enchant target.");
			final PhantomGoal goal = enchantGoal(2200220201L, 1, target.getObjectId(), target.getEnchantLevel() + 1, candidate.scroll().getId(), false, replacementReserve);
			goals.insert(profile.profileId(), goal);
			reservations.start();
			final PhantomEconomyService service = economyService(reservations, materialization, acquisitions, goals, repository);

			final DecisionHarness cancelled = decisionHarness(service, profile.profileId(), goal, PhantomActivityState.ACTIVE, 2, 1, 1);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, cancelled.execute(0, false).type(), "Enchant cancellation fixture did not reserve.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, cancelled.execute(1, false).type(), "Enchant cancellation fixture did not dispatch.");
			final String cancelledOperationId = reservations.findActive(profile.profileId()).orElseThrow().operationId();
			PhantomAssertions.assertEquals(PhantomStepResult.Type.CANCELLED, cancelled.execute(2, true).type(), "DISPATCHING enchant cancellation was not terminalized.");
			PhantomAssertions.assertEquals(State.ABORTED, reservations.find(cancelledOperationId).orElseThrow().state(), "DISPATCHING enchant cancellation did not abort.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "DISPATCHING enchant cancellation retained claims.");

			final DecisionHarness observingCancelled = decisionHarness(service, profile.profileId(), goal, PhantomActivityState.ACTIVE, 2, 2, 2);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, observingCancelled.execute(0, false).type(), "OBSERVING enchant cancellation fixture did not reserve.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, observingCancelled.execute(1, false).type(), "OBSERVING enchant cancellation fixture did not dispatch.");
			final String observingOperationId = reservations.findActive(profile.profileId()).orElseThrow().operationId();
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, reservations.transition(observingOperationId, State.DISPATCHING, State.OBSERVING, System.currentTimeMillis(), null).status(), "Enchant cancellation fixture did not cross OBSERVING.");
			final long scrollBeforeObservingCancel = player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1);
			final String targetBeforeObservingCancel = activeItemEvidence(player, target.getObjectId(), candidate.target().getId());
			PhantomAssertions.assertEquals(PhantomStepResult.Type.CANCELLED, observingCancelled.execute(2, true).type(), "OBSERVING enchant cancellation did not fail stop.");
			PhantomAssertions.assertEquals(State.INCONSISTENT, reservations.find(observingOperationId).orElseThrow().state(), "OBSERVING enchant cancellation was blindly aborted.");
			PhantomAssertions.assertEquals(scrollBeforeObservingCancel, player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1), "OBSERVING cancellation consumed a scroll.");
			PhantomAssertions.assertEquals(targetBeforeObservingCancel, activeItemEvidence(player, target.getObjectId(), candidate.target().getId()), "OBSERVING cancellation mutated the target.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "OBSERVING enchant cancellation retained claims.");

			final DecisionHarness staleAuthority = decisionHarness(service, profile.profileId(), goal, PhantomActivityState.ACTIVE, 2, 3, 3);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, staleAuthority.execute(0, false).type(), "Enchant authority-drift fixture did not reserve.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, staleAuthority.execute(1, false).type(), "Enchant authority-drift fixture did not dispatch.");
			final String staleOperationId = reservations.findActive(profile.profileId()).orElseThrow().operationId();
			final long scrollBeforeAuthorityDrift = player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1);
			final String targetBeforeAuthorityDrift = activeItemEvidence(player, target.getObjectId(), candidate.target().getId());
			final boolean disableOverEnchanting = PlayerConfig.DISABLE_OVER_ENCHANTING;
			try
			{
				PlayerConfig.DISABLE_OVER_ENCHANTING = !disableOverEnchanting;
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, staleAuthority.execute(2, false).type(), "Enchant authority drift did not reach a stale terminal result.");
			}
			finally
			{
				PlayerConfig.DISABLE_OVER_ENCHANTING = disableOverEnchanting;
			}
			PhantomAssertions.assertEquals(State.ABORTED, reservations.find(staleOperationId).orElseThrow().state(), "Enchant authority drift was not fail-stopped before action.");
			PhantomAssertions.assertEquals(scrollBeforeAuthorityDrift, player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1), "Enchant authority drift consumed a scroll.");
			PhantomAssertions.assertEquals(targetBeforeAuthorityDrift, activeItemEvidence(player, target.getObjectId(), candidate.target().getId()), "Enchant authority drift mutated the target.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Enchant authority drift retained claims.");

			DecisionHarness harness = decisionHarness(service, profile.profileId(), goal, PhantomActivityState.ACTIVE, 2, 4, 4);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(0, false).type(), "Full-chain enchant reserve failed.");
			harness = decisionHarness(service, profile.profileId(), goal, PhantomActivityState.ACTIVE, 2, 4, 4);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(0, false).type(), "New enchant plan did not resume RESERVED.");
			final var active = reservations.findActive(profile.profileId()).orElseThrow();
			final List<Reservation> exact = reservations.findReservations(active.operationId());
			PhantomAssertions.assertTrue(exact.stream().anyMatch(resource -> (resource.kind() == ResourceKind.ITEM_OBJECT) && (resource.objectId() == target.getObjectId())), "Full-chain enchant did not reserve the exact target object.");
			PhantomAssertions.assertEquals(1L, exact.stream().filter(resource -> (resource.kind() == ResourceKind.ITEM_OBJECT) && (resource.itemId() == candidate.scroll().getId())).count(), "Full-chain enchant did not reserve one exact scroll object.");
			final Reservation adena = exact.stream().filter(resource -> resource.kind() == ResourceKind.ADENA).findFirst().orElseThrow();
			PhantomAssertions.assertTrue((adena.itemId() == Inventory.ADENA_ID) && (adena.count() == replacementReserve) && (adena.expectedCount() == player.getAdena()), "Enchant ADENA reservation did not bind the Goal reserve to canonical inventory evidence.");
			try (var npcBuyConflict = reservations.claimWriter(profile.profileId(), null, List.of(new Reservation(profile.profileId(), player.getObjectId(), player.getClassIndex(), ResourceKind.ADENA, 0, Inventory.ADENA_ID, 1, player.getAdena(), 0, "INVENTORY"))))
			{
				PhantomAssertions.assertFalse(npcBuyConflict.acquired(), "Accepted NPC BUY Adena writer bypassed the enchant replacement reservation.");
			}
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(1, false).type(), "Full-chain enchant dispatch failed.");
			harness = decisionHarness(service, profile.profileId(), goal, PhantomActivityState.ACTIVE, 2, 4, 4);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(0, false).type(), "New enchant plan did not resume DISPATCHING.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(1, false).type(), "New enchant plan did not dispatch idempotently.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(2, false).type(), "Full-chain enchant reconcile failed.");
			PhantomAssertions.assertEquals(State.COMMITTED, reservations.find(active.operationId()).orElseThrow().state(), "Full-chain enchant operation was not committed.");
			PhantomAssertions.assertEquals(1L, scalarLong("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", active.operationId()), "Full-chain enchant audit row is absent.");
			PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Full-chain enchant retained claims.");
			final long scrollAfterEffect = player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1);
			final String targetAfterEffect = activeItemEvidence(player, target.getObjectId(), candidate.target().getId());
			PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, harness.execute(2, false).type(), "Process-local retry after enchant effect did not terminate without redispatch.");
			PhantomAssertions.assertEquals(scrollAfterEffect, player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1), "Process-local enchant retry consumed a second scroll.");
			PhantomAssertions.assertEquals(targetAfterEffect, activeItemEvidence(player, target.getObjectId(), candidate.target().getId()), "Process-local enchant retry mutated the target twice.");
			context.record("economy.activeEnchantOperation", active.operationId());
		}
		finally
		{
			reservations.shutdown(System.currentTimeMillis());
			if (player != null)
			{
				restoreItemCount(player, candidate.target().getId(), targetBaseline);
				restoreItemCount(player, candidate.scroll().getId(), scrollBaseline);
				restoreItemCount(player, Inventory.ADENA_ID, adenaBaseline);
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

	private void testEnchantActorGuards(PhantomTestContext context) throws Exception
	{
		final EnchantCandidate candidate = selectEnchantCandidate(Boolean.TRUE, false);
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		Player player = null;
		long targetBaseline = 0;
		long scrollBaseline = 0;
		try
		{
			materialization.start();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Actor-guard Phantom did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Actor-guard Player is absent.");
			targetBaseline = player.getInventory().getInventoryItemCount(candidate.target().getId(), -1);
			scrollBaseline = player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1);
			final Item target = player.getInventory().addItem(ItemProcessType.REWARD, candidate.target().getId(), 1, player, this);
			final Item scroll = player.getInventory().addItem(ItemProcessType.REWARD, candidate.scroll().getId(), 2, player, this);
			PhantomAssertions.assertTrue((target != null) && (scroll != null), "Actor-guard resources are absent.");
			final long targetCount = target.getCount();
			final long scrollCount = scroll.getCount();
			player.onTransactionRequest(player);
			PhantomAssertions.assertEquals(EnchantItemService.Outcome.ERROR, EnchantItemService.getInstance().execute(new EnchantItemService.Request(player, target.getObjectId(), scroll.getObjectId(), 0, false, null)), "Direct canonical enchant bypassed transaction state.");
			PhantomAssertions.assertEquals(scrollCount, scroll.getCount(), "Transaction-state rejection consumed a scroll.");
			player.setActiveRequester(null);
			player.onTransactionResponse();
			player.setPrivateStoreType(PrivateStoreType.SELL);
			PhantomAssertions.assertEquals(EnchantItemService.Outcome.ERROR, EnchantItemService.getInstance().execute(new EnchantItemService.Request(player, target.getObjectId(), scroll.getObjectId(), 0, false, null)), "Direct canonical enchant bypassed store state.");
			PhantomAssertions.assertEquals(scrollCount, scroll.getCount(), "Store-state rejection consumed a scroll.");
			player.setPrivateStoreType(PrivateStoreType.NONE);
			PhantomAssertions.assertEquals(EnchantItemService.Outcome.ERROR, EnchantItemService.getInstance().execute(new EnchantItemService.Request(player, target.getObjectId(), scroll.getObjectId(), 999999999, false, null)), "Missing support ownership was accepted.");
			PhantomAssertions.assertEquals(scrollCount, scroll.getCount(), "Missing-support rejection consumed a scroll.");
			PhantomAssertions.assertEquals(EnchantItemService.Outcome.ERROR, EnchantItemService.getInstance().execute(new EnchantItemService.Request(player, target.getObjectId(), target.getObjectId(), 0, false, null)), "Invalid target/scroll identity was accepted.");
			final int previousEnchant = target.getEnchantLevel();
			target.setEnchantLevel(candidate.scroll().getMaxEnchantLevel());
			PhantomAssertions.assertEquals(EnchantItemService.Outcome.ERROR, EnchantItemService.getInstance().execute(new EnchantItemService.Request(player, target.getObjectId(), scroll.getObjectId(), 0, false, null)), "Over-enchant guard was bypassed.");
			target.setEnchantLevel(previousEnchant);
			PhantomAssertions.assertEquals(targetCount, target.getCount(), "Actor validation mutated the target.");
			PhantomAssertions.assertEquals(scrollCount, scroll.getCount(), "Actor validation consumed resources.");
			context.record("economy.enchantActorGuards", 5);
		}
		finally
		{
			if (player != null)
			{
				player.setActiveRequester(null);
				player.onTransactionResponse();
				player.setPrivateStoreType(PrivateStoreType.NONE);
				restoreItemCount(player, candidate.target().getId(), targetBaseline);
				restoreItemCount(player, candidate.scroll().getId(), scrollBaseline);
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

	private void testPacketParityMatrix(PhantomTestContext context) throws Exception
	{
		final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/network/clientpackets/RequestEnchantItem.java"));
		final List<String> orderedContract = List.of(
			"if ((player == null) || (_objectId == 0))",
			"if (!player.isOnline() || getClient().isDetached())",
			"if (player.isProcessingTransaction() || player.isInStoreMode())",
			"if ((item == null) || (scroll == null))",
			"if (scrollTemplate == null)",
			"if (support != null)",
			"if (!scrollTemplate.isValid(item, supportTemplate)",
			"if ((player.getActiveEnchantTimestamp() == 0)",
			"EnchantItemService.getInstance().execute(new Request(player, _objectId, scrollObjectId, supportObjectId, true, EnchantItemService.Observer.NONE))");
		int previous = -1;
		for (String token : orderedContract)
		{
			final int current = source.indexOf(token);
			PhantomAssertions.assertTrue(current > previous, "Ordinary enchant packet parity branch moved or disappeared: " + token);
			previous = current;
		}
		PhantomAssertions.assertTrue(source.contains("YOU_HAVE_CANCELLED_THE_ENCHANTING_PROCESS") && source.contains("INAPPROPRIATE_ENCHANT_CONDITIONS") && source.contains("use autoenchant program"), "Ordinary enchant rejection messages or punishment drifted.");
		PhantomAssertions.assertFalse(source.contains("destroyItem("), "Ordinary enchant packet retained a second mutation path.");
		context.record("economy.packetParityBranches", orderedContract.size());
	}

	private void testActiveEnchantRestartWindows(PhantomTestContext context) throws Exception
	{
		testActiveEnchantRestartWindow(false);
		testActiveEnchantRestartWindow(true);
		context.record("economy.activeRestartWindows", List.of("effect-before-goal", "goal-before-audit"));
	}

	private void testActiveEnchantRestartWindow(boolean writeGoalBeforeRestart) throws Exception
	{
		final EnchantCandidate candidate = selectEnchantCandidate(Boolean.TRUE, false);
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(repository);
		final PhantomAcquisitionStore acquisitions = new PhantomAcquisitionStore(repository, goals);
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		final PhantomEconomyReservationService first = new PhantomEconomyReservationService(_policy);
		final PhantomEconomyReservationService restarted = new PhantomEconomyReservationService(_policy);
		Player player = null;
		long targetBaseline = 0;
		long scrollBaseline = 0;
		long adenaBaseline = 0;
		try
		{
			materialization.start();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Restart-window enchant Phantom did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Restart-window enchant Player is absent.");
			targetBaseline = player.getInventory().getInventoryItemCount(candidate.target().getId(), -1);
			scrollBaseline = player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1);
			adenaBaseline = player.getAdena();
			final long replacementReserve = Math.max(0, candidate.target().getReferencePrice());
			if (adenaBaseline < replacementReserve)
			{
				player.getInventory().addItem(ItemProcessType.REWARD, Inventory.ADENA_ID, replacementReserve - adenaBaseline, player, this);
			}
			final Item target = player.getInventory().addItem(ItemProcessType.REWARD, candidate.target().getId(), 1, player, this);
			final Item scroll = player.getInventory().addItem(ItemProcessType.REWARD, candidate.scroll().getId(), 1, player, this);
			PhantomAssertions.assertTrue((target != null) && (scroll != null), "Restart-window enchant resources could not be created.");
			target.setEnchantLevel(candidate.enchantLevel());
			final PhantomGoal goal = enchantGoal(writeGoalBeforeRestart ? 2200220502L : 2200220501L, 1, target.getObjectId(), target.getEnchantLevel() + 1, candidate.scroll().getId(), false, replacementReserve);
			goals.insert(profile.profileId(), goal);
			first.start();
			final PhantomEconomyService service = economyService(first, materialization, acquisitions, goals, repository);
			final DecisionHarness harness = decisionHarness(service, profile.profileId(), goal, PhantomActivityState.ACTIVE, 6, 1, 1);
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(0, false).type(), "Restart-window enchant reserve failed.");
			PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(1, false).type(), "Restart-window enchant dispatch failed.");
			final String operationId = first.findActive(profile.profileId()).orElseThrow().operationId();
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, first.transition(operationId, State.DISPATCHING, State.OBSERVING, System.currentTimeMillis(), null).status(), "Restart-window enchant did not cross the action-issued boundary.");
			final AtomicReference<EnchantItemService.Event> observed = new AtomicReference<>();
			final EnchantItemService.Outcome outcome = EnchantItemService.getInstance().execute(new EnchantItemService.Request(player, target.getObjectId(), scroll.getObjectId(), 0, false, observed::set));
			PhantomAssertions.assertFalse(outcome == EnchantItemService.Outcome.ERROR, "Restart-window canonical enchant did not commit its effect.");
			final EnchantItemService.Event event = observed.get();
			PhantomAssertions.assertTrue(event != null, "Restart-window canonical enchant emitted no immutable evidence.");
			final long scrollAfterEffect = player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1);
			final String targetAfterEffect = activeItemEvidence(player, target.getObjectId(), candidate.target().getId());
			if (writeGoalBeforeRestart)
			{
				final var storedGoal = goals.load(profile.profileId()).orElseThrow();
				final PhantomEnchantGoalSpec spec = PhantomEnchantGoalSpec.parse(storedGoal.goal());
				final boolean targetSurvived = event.outcome() != EnchantItemService.Outcome.DESTROYED_WITH_CRYSTALS;
				final String reason = "restart.window." + event.outcome().name().toLowerCase(java.util.Locale.ROOT);
				goals.replace(profile.profileId(), storedGoal.rowVersion(), spec.project(storedGoal.goal(), event.afterEnchantLevel(), targetSurvived, Math.max(0, scroll.getReferencePrice()), reason));
			}
			final PhantomGoalStatus goalStatusBeforeRestart = goals.load(profile.profileId()).orElseThrow().goal().status();
			PhantomAssertions.assertTrue(restarted.start(), "Restart-window economy service did not reopen admission.");
			PhantomAssertions.assertEquals(State.INCONSISTENT, restarted.find(operationId).orElseThrow().state(), "Restart-window dispatched operation did not fail stop.");
			final PhantomGoal currentGoal = goals.load(profile.profileId()).orElseThrow().goal();
			final PhantomEconomyService restartedService = economyService(restarted, materialization, acquisitions, goals, repository);
			PhantomAssertions.assertEquals(PhantomEconomyService.StepStatus.REPLAN, restartedService.reconcile(profile.profileId(), currentGoal, PhantomActivityState.ACTIVE, 6, System.currentTimeMillis()).status(), "Restart-window reconciliation attempted to redispatch.");
			PhantomAssertions.assertEquals(scrollAfterEffect, player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1), "Restart-window reconciliation consumed a second scroll.");
			PhantomAssertions.assertEquals(targetAfterEffect, activeItemEvidence(player, target.getObjectId(), candidate.target().getId()), "Restart-window reconciliation mutated the target twice.");
			PhantomAssertions.assertEquals(goalStatusBeforeRestart, goals.load(profile.profileId()).orElseThrow().goal().status(), "Restart-window reconciliation applied Goal progress twice.");
			if (!writeGoalBeforeRestart)
			{
				PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, goalStatusBeforeRestart, "Effect-before-Goal window wrote Goal state unexpectedly.");
			}
		}
		finally
		{
			first.shutdown(System.currentTimeMillis());
			restarted.shutdown(System.currentTimeMillis());
			if (player != null)
			{
				restoreItemCount(player, candidate.target().getId(), targetBaseline);
				restoreItemCount(player, candidate.scroll().getId(), scrollBaseline);
				restoreItemCount(player, Inventory.ADENA_ID, adenaBaseline);
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

	private void testBackgroundEnchantServiceLifecycle(PhantomTestContext context) throws Exception
	{
		try (BackgroundEnchantFixture fixture = createBackgroundEnchantFixture())
		{
			final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
			try
			{
				reservations.start();
				final PhantomEconomyService service = economyService(reservations, fixture.materialization(), fixture.acquisitions(), fixture.goals(), fixture.repository());
				final DecisionHarness harness = decisionHarness(service, fixture.profile().profileId(), fixture.goal(), PhantomActivityState.BACKGROUND, 4, 1, 1);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(0, false).type(), "Background enchant reserve failed.");
				final var active = reservations.findActive(fixture.profile().profileId()).orElseThrow();
				final List<Reservation> exact = reservations.findReservations(active.operationId());
				PhantomAssertions.assertTrue(exact.stream().anyMatch(resource -> resource.objectId() == fixture.targetObjectId()), "Background enchant did not reserve the exact target.");
				PhantomAssertions.assertTrue(exact.stream().anyMatch(resource -> resource.objectId() == fixture.scrollObjectId()), "Background enchant did not reserve the exact scroll.");
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(1, false).type(), "Background enchant dispatch failed.");
				PhantomAssertions.assertEquals(PhantomStepResult.Type.SUCCESS, harness.execute(2, false).type(), "Background enchant reconcile failed.");
				PhantomAssertions.assertEquals(State.COMMITTED, reservations.find(active.operationId()).orElseThrow().state(), "Background enchant operation was not committed.");
				PhantomAssertions.assertEquals(0L, reservations.snapshot().currentReservations(), "Background enchant retained claims.");
				PhantomAssertions.assertEquals(1L, scalarLong("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", active.operationId()), "Background enchant audit row is absent.");
				PhantomAssertions.assertFalse(fixture.goals().load(fixture.profile().profileId()).orElseThrow().goal().status() == PhantomGoalStatus.ACTIVE, "Single-attempt background enchant Goal stayed active.");
				context.record("economy.backgroundEnchantOperation", active.operationId());
			}
			finally
			{
				reservations.shutdown(System.currentTimeMillis());
			}
		}
	}

	private void testEnchantAuthorityAndRisk(PhantomTestContext context)
	{
		final EnchantCandidate ordinary = selectEnchantCandidate(Boolean.FALSE, false);
		final EnchantSupportItem support = selectEnchantSupport(ordinary);
		PhantomAssertions.assertTrue(support != null, "Production enchant data exposes no valid support authority fixture.");
		final PhantomEconomyProjection.AuthorityFacts facts = PhantomEconomyProjection.enchantAuthorityFacts(ordinary.target(), ordinary.enchantLevel(), ordinary.scroll(), support, _policy);
		final Set<String> keys = facts.facts().stream().map(PhantomEconomyProjection.AuthorityFact::key).collect(java.util.stream.Collectors.toSet());
		for (String required : List.of("policy.hash", "target.item_id", "target.enchant_level", "target.type2", "target.crystal_grade", "target.enchantable", "target.crystallizable", "target.crystal_item_id", "target.crystal_count", "target.crystal_count_at_level", "target.crystal_destruction_consequence", "target.reference_price", "scroll.item_id", "scroll.grade", "scroll.maximum_enchant", "scroll.bonus_rate", "scroll.weapon", "scroll.safe", "scroll.blessed", "scroll.base_chance", "support.item_id", "support.grade", "support.maximum_enchant", "support.bonus_rate", "support.weapon", "combination.valid", "config.disable_over_enchanting"))
		{
			PhantomAssertions.assertTrue(keys.contains(required), "Enchant authority omitted " + required + ".");
		}
		assertEveryAuthorityFactChangesHash(facts);
		final String withoutSupport = PhantomEconomyProjection.enchantAuthority(ordinary.target(), ordinary.enchantLevel(), ordinary.scroll(), null, _policy);
		PhantomAssertions.assertFalse(withoutSupport.equals(facts.hash()), "Support bonus/stat family did not change enchant authority.");
		final Identity identity = new Identity(1, 1, 1, 1, 1, "authority.enchant", 1, 1);
		PhantomAssertions.assertFalse(identity.operationId(Kind.ITEM_ENCHANT, withoutSupport, "a".repeat(64)).equals(identity.operationId(Kind.ITEM_ENCHANT, facts.hash(), "a".repeat(64))), "Support authority did not change the operation ID.");

		final PhantomEnchantGoalSpec goal = enchantGoal(ordinary);
		final long reserve = goal.replacementReserve();
		final long targetPrice = Math.max(0, ordinary.target().getReferencePrice());
		final long expense = Math.addExact(Math.max(0, ordinary.scroll().getItem().getReferencePrice()), 0);
		final EnchantRequest accepted = enchantRequest(goal, ordinary, ItemLocation.INVENTORY, 1);
		PhantomAssertions.assertTrue(PhantomEconomyProjection.enchant(accepted).executable(), "Fully funded ordinary enchant was rejected.");
		PhantomAssertions.assertEquals(Result.CONFLICT, PhantomEconomyProjection.enchant(enchantRequest(accepted, Math.max(0, reserve - 1), targetPrice, Long.MAX_VALUE, _policy)).result(), "Insufficient current Adena evidence was accepted.");
		PhantomAssertions.assertEquals(Result.CONFLICT, PhantomEconomyProjection.enchant(enchantRequest(accepted, reserve, Math.max(0, targetPrice - 1), Long.MAX_VALUE, _policy)).result(), "Low Goal risk budget was accepted.");
		PhantomAssertions.assertEquals(Result.CONFLICT, PhantomEconomyProjection.enchant(enchantRequest(accepted, reserve, targetPrice, Math.max(0, expense - 1), _policy)).result(), "Low remaining expense budget was accepted.");
		final PhantomEnchantGoalSpec noDestruction = new PhantomEnchantGoalSpec(goal.targetObjectId(), goal.desiredLevel(), goal.maximumAttempts(), goal.attemptsUsed(), goal.expenseUsed(), false, goal.replacementReserve(), goal.allowedScrollItemIds(), goal.allowedSupportItemIds());
		PhantomAssertions.assertEquals(Result.CONFLICT, PhantomEconomyProjection.enchant(new EnchantRequest(noDestruction, accepted.target(), accepted.targetObjectId(), accepted.enchantLevel(), accepted.targetLocation(), accepted.scrollObjectId(), accepted.scrollItemId(), 0, 0, false, false, false, false, reserve, targetPrice, Long.MAX_VALUE, accepted.rngState(), _policy)).result(), "Ordinary destruction without permission was accepted.");
		final PhantomEconomyPolicy restrictive = new PhantomEconomyPolicy("0".repeat(64), _policy.limits(), _policy.craft(), _policy.enchant(), new PhantomEconomyPolicy.Risk(0, _policy.risk().replacementReservePercent()), _policy.reasonKeys());
		PhantomAssertions.assertEquals(Result.CONFLICT, PhantomEconomyProjection.enchant(enchantRequest(accepted, reserve, targetPrice, Long.MAX_VALUE, restrictive)).result(), "Maximum expense percentage was bypassed.");
		for (EnchantCandidate protectedCandidate : List.of(selectEnchantCandidate(Boolean.TRUE, false), selectEnchantCandidate(Boolean.FALSE, true)))
		{
			final PhantomEnchantGoalSpec protectedGoal = enchantGoal(protectedCandidate);
			final EnchantRequest protectedRequest = enchantRequest(protectedGoal, protectedCandidate, ItemLocation.INVENTORY, 1);
			PhantomAssertions.assertTrue(PhantomEconomyProjection.enchant(enchantRequest(protectedRequest, protectedGoal.replacementReserve(), Long.MAX_VALUE, Long.MAX_VALUE, restrictive)).executable(), "Safe/blessed enchant was incorrectly subjected to destructive risk gates.");
		}
		context.record("economy.enchantAuthorityFacts", facts.facts().size());
	}

	private void testBackgroundEnchantOutcomeMatrix(PhantomTestContext context) throws Exception
	{
		final List<EnchantOutcomeFixture> outcomes = List.of(
			new EnchantOutcomeFixture(selectEnchantCandidate(null, false), Result.SUCCESS),
			new EnchantOutcomeFixture(selectEnchantCandidate(Boolean.TRUE, false), Result.SAFE_FAILURE),
			new EnchantOutcomeFixture(selectEnchantCandidate(Boolean.FALSE, true), Result.BLESSED_RESET),
			new EnchantOutcomeFixture(selectEnchantCandidate(Boolean.FALSE, false), Result.DESTROYED_WITH_CRYSTALS));
		for (EnchantOutcomeFixture requested : outcomes)
		{
			try (BackgroundEnchantFixture fixture = createBackgroundEnchantFixture(requested.candidate(), requested.result()))
			{
				final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
				try
				{
					reservations.start();
					final PhantomBackgroundState background = loadBackground(fixture.repository(), fixture.profile().profileId());
					final long backgroundRowVersion = fixture.repository().findComponent(fixture.profile().profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().rowVersion();
					final var goal = fixture.goals().load(fixture.profile().profileId()).orElseThrow();
					final PhantomEconomyBackgroundTransaction transaction = new PhantomEconomyBackgroundTransaction(reservations, _policy);
					final var quote = transaction.quoteEnchant(background, goal.goal());
					PhantomAssertions.assertTrue(quote.executable(), "Actual background enchant quote was rejected for " + requested.result() + ".");
					final long now = System.currentTimeMillis();
					final PhantomEconomyOperation operation = backgroundOperation(fixture.profile().profileId(), fixture.characterObjectId(), goal.goal(), Kind.ITEM_ENCHANT, 1, quote.authorityHash(), now);
					PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, reservations.reserve(operation, quote.reservations()).status(), "Actual background enchant reservation failed for " + requested.result() + ".");
					PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, now, null).status(), "Actual background enchant dispatch failed for " + requested.result() + ".");
					final TransactionResult result = transaction.executeEnchant(enchantCommand(fixture, operation.operationId(), background, backgroundRowVersion, goal.goal(), goal.rowVersion(), now));
					PhantomAssertions.assertEquals(PhantomEconomyBackgroundTransaction.Status.COMMITTED, result.status(), "Actual background enchant transaction failed for " + requested.result() + ".");
					PhantomAssertions.assertEquals(requested.result(), result.result(), "Actual background enchant reached the wrong canonical branch.");
					PhantomAssertions.assertEquals(State.COMMITTED, reservations.find(operation.operationId()).orElseThrow().state(), "Actual background enchant operation was not committed.");
					PhantomAssertions.assertEquals(0L, inventoryObjectCount(fixture.characterObjectId(), fixture.scrollObjectId()), "Actual background enchant did not consume the exact scroll once.");
					if (requested.result() == Result.DESTROYED_WITH_CRYSTALS)
					{
						PhantomAssertions.assertEquals(0L, inventoryObjectCount(fixture.characterObjectId(), fixture.targetObjectId()), "Ordinary background enchant did not destroy the exact target.");
						PhantomAssertions.assertTrue(inventoryCount(fixture.characterObjectId(), requested.candidate().target().getCrystalItemId()) > fixture.baselines().getOrDefault(requested.candidate().target().getCrystalItemId(), 0L), "Ordinary background enchant did not produce crystals.");
					}
					else
					{
						final int expectedLevel = requested.result() == Result.SUCCESS ? requested.candidate().enchantLevel() + 1 : requested.result() == Result.BLESSED_RESET ? 0 : requested.candidate().enchantLevel();
						PhantomAssertions.assertEquals(expectedLevel, inventoryEnchantLevel(fixture.characterObjectId(), fixture.targetObjectId()), "Surviving background enchant target level drifted.");
					}
					PhantomAssertions.assertEquals(1L, scalarLong("SELECT COUNT(*) FROM phantom_economy_audit WHERE operation_id=?", operation.operationId()), "Actual background enchant audit row is absent.");
				}
				finally
				{
					reservations.shutdown(System.currentTimeMillis());
				}
			}
		}
		context.record("economy.backgroundEnchantActualOutcomes", outcomes.stream().map(EnchantOutcomeFixture::result).toList());
	}

	private void testBackgroundEnchantFaultMatrix(PhantomTestContext context) throws Exception
	{
		try (BackgroundEnchantFixture fixture = createBackgroundEnchantFixture())
		{
			final PhantomEconomyReservationService reservations = new PhantomEconomyReservationService(_policy);
			try
			{
				reservations.start();
				int attempt = 1;
				for (FaultPoint faultPoint : FaultPoint.values())
				{
					final PhantomBackgroundState background = loadBackground(fixture.repository(), fixture.profile().profileId());
					final long backgroundRowVersion = fixture.repository().findComponent(fixture.profile().profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().rowVersion();
					final var goal = fixture.goals().load(fixture.profile().profileId()).orElseThrow();
					final PhantomEconomyBackgroundTransaction quoting = new PhantomEconomyBackgroundTransaction(reservations, _policy);
					final var quote = quoting.quoteEnchant(background, goal.goal());
					PhantomAssertions.assertTrue(quote.executable(), "Enchant fault quote was not executable: " + faultPoint);
					final long now = System.currentTimeMillis();
					final PhantomEconomyOperation operation = backgroundOperation(fixture.profile().profileId(), fixture.characterObjectId(), goal.goal(), Kind.ITEM_ENCHANT, attempt++, quote.authorityHash(), now);
					PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, reservations.reserve(operation, quote.reservations()).status(), "Enchant fault reservation failed: " + faultPoint);
					PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, reservations.transition(operation.operationId(), State.RESERVED, State.DISPATCHING, now, null).status(), "Enchant fault dispatch failed: " + faultPoint);
					final EnchantDurableSnapshot before = enchantSnapshot(fixture);
					final PhantomEconomyBackgroundTransaction faulting = new PhantomEconomyBackgroundTransaction(DatabaseFactory::getConnection, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.ObjectIdAllocator.production(), reservations, _policy, actual ->
					{
						if (actual == faultPoint)
						{
							throw new InjectedEconomyFailure(faultPoint);
						}
					});
					final TransactionResult result = faulting.executeEnchant(enchantCommand(fixture, operation.operationId(), background, backgroundRowVersion, goal.goal(), goal.rowVersion(), now));
					if (faultPoint == FaultPoint.AFTER_COMMIT)
					{
						PhantomAssertions.assertEquals(PhantomEconomyBackgroundTransaction.Status.COMMITTED, result.status(), "Post-commit enchant fault lost the committed result.");
						PhantomAssertions.assertEquals(State.COMMITTED, reservations.find(operation.operationId()).orElseThrow().state(), "Post-commit enchant fault did not retain the terminal operation.");
					}
					else
					{
						PhantomAssertions.assertEquals(PhantomEconomyBackgroundTransaction.Status.BACKEND_FAILURE, result.status(), "Precommit enchant fault did not fail atomically: " + faultPoint);
						PhantomAssertions.assertEquals(before, enchantSnapshot(fixture), "Precommit enchant fault changed durable authority: " + faultPoint);
						PhantomAssertions.assertEquals(State.DISPATCHING, reservations.find(operation.operationId()).orElseThrow().state(), "Precommit enchant fault changed the operation outside its transaction: " + faultPoint);
						reservations.transition(operation.operationId(), State.DISPATCHING, State.INCONSISTENT, now, audit(Result.INCONSISTENT, "dispatch.ambiguous"));
					}
				}
				PhantomAssertions.assertEquals(12, FaultPoint.values().length, "Enchant fault boundary set drifted.");
				context.record("economy.backgroundEnchantFaultPoints", FaultPoint.values().length);
			}
			finally
			{
				reservations.shutdown(System.currentTimeMillis());
			}
		}
	}

	private void testRestartAndExpiry(PhantomTestContext context) throws Exception
	{
		final PhantomProfile dispatchedProfile = createProfile(920001);
		final PhantomProfile expiringProfile = createProfile(920002);
		final PhantomEconomyReservationService first = new PhantomEconomyReservationService(_policy);
		final PhantomEconomyReservationService restarted = new PhantomEconomyReservationService(_policy);
		final PhantomEconomyReservationService observingRestart = new PhantomEconomyReservationService(_policy);
		try
		{
			final long now = System.currentTimeMillis();
			first.start();
			final PhantomEconomyOperation dispatched = operation(dispatchedProfile.profileId(), 920001, 21, Kind.ITEM_ENCHANT, 1, now);
			first.reserve(dispatched, List.of(itemObject(dispatchedProfile.profileId(), 920001, 881001, 1)));
			first.transition(dispatched.operationId(), State.RESERVED, State.DISPATCHING, now + 1, null);
			PhantomAssertions.assertTrue(restarted.start(), "Restarted economy service did not start.");
			PhantomAssertions.assertEquals(State.DISPATCHING, restarted.find(dispatched.operationId()).orElseThrow().state(), "Pre-action dispatch was not resumable after restart.");
			restarted.transition(dispatched.operationId(), State.DISPATCHING, State.OBSERVING, now + 2, null);
			PhantomAssertions.assertTrue(observingRestart.start(), "Observing restart economy service did not start.");
			PhantomAssertions.assertEquals(State.INCONSISTENT, observingRestart.find(dispatched.operationId()).orElseThrow().state(), "Action-issued observing state did not fail stop after restart.");
			final PhantomEconomyOperation expiring = operation(expiringProfile.profileId(), 920002, 22, Kind.SELF_CRAFT, 1, now);
			observingRestart.reserve(expiring, List.of(itemCount(expiringProfile.profileId(), 920002, 77)));
			PhantomAssertions.assertEquals(1, observingRestart.expireDue(now + 1_000_000, 8), "Predispatch reservation did not expire exactly once.");
			PhantomAssertions.assertEquals(State.EXPIRED, observingRestart.find(expiring.operationId()).orElseThrow().state(), "Expired operation retained a nonterminal state.");
			PhantomAssertions.assertTrue(observingRestart.findReservations(expiring.operationId()).isEmpty(), "Expiry retained reservations.");
			context.record("economy.restartInconsistent", observingRestart.snapshot().inconsistent());
		}
		finally
		{
			first.shutdown(System.currentTimeMillis());
			restarted.shutdown(System.currentTimeMillis());
			observingRestart.shutdown(System.currentTimeMillis());
			deleteProfile(dispatchedProfile.profileId());
			deleteProfile(expiringProfile.profileId());
		}
	}

	private void testShutdownTerminalization(PhantomTestContext context) throws Exception
	{
		final PhantomProfile reservedProfile = createProfile(925001);
		final PhantomProfile dispatchingProfile = createProfile(925002);
		final PhantomProfile observingProfile = createProfile(925003);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			final long now = System.currentTimeMillis();
			service.start();
			final PhantomEconomyOperation reserved = operation(reservedProfile.profileId(), 925001, 25, Kind.SELF_CRAFT, 1, now);
			final PhantomEconomyOperation dispatching = operation(dispatchingProfile.profileId(), 925002, 26, Kind.ITEM_ENCHANT, 1, now);
			final PhantomEconomyOperation observing = operation(observingProfile.profileId(), 925003, 27, Kind.ITEM_ENCHANT, 1, now);
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(reserved, List.of(itemCount(reservedProfile.profileId(), 925001, 925101))).status(), "Shutdown RESERVED fixture failed.");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(dispatching, List.of(itemObject(dispatchingProfile.profileId(), 925002, 925102, 0))).status(), "Shutdown DISPATCHING fixture failed.");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, service.transition(dispatching.operationId(), State.RESERVED, State.DISPATCHING, now + 1, null).status(), "Shutdown DISPATCHING fixture did not dispatch.");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.RESERVED, service.reserve(observing, List.of(itemObject(observingProfile.profileId(), 925003, 925103, 0))).status(), "Shutdown OBSERVING fixture failed.");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, service.transition(observing.operationId(), State.RESERVED, State.DISPATCHING, now + 1, null).status(), "Shutdown OBSERVING fixture did not dispatch.");
			PhantomAssertions.assertEquals(PhantomEconomyReservationService.Status.TRANSITIONED, service.transition(observing.operationId(), State.DISPATCHING, State.OBSERVING, now + 2, null).status(), "Shutdown OBSERVING fixture did not cross the action-issued boundary.");
			service.shutdown(now + 3);
			PhantomAssertions.assertEquals(State.ABORTED, service.find(reserved.operationId()).orElseThrow().state(), "Shutdown did not abort RESERVED.");
			PhantomAssertions.assertEquals(State.ABORTED, service.find(dispatching.operationId()).orElseThrow().state(), "Shutdown did not abort pre-action DISPATCHING.");
			PhantomAssertions.assertEquals(State.INCONSISTENT, service.find(observing.operationId()).orElseThrow().state(), "Shutdown did not fail stop OBSERVING.");
			PhantomAssertions.assertEquals(0L, service.snapshot().currentOperations(), "Shutdown retained executable economy operations.");
			PhantomAssertions.assertEquals(0L, service.snapshot().currentReservations(), "Shutdown retained economy claims.");
			context.record("economy.shutdownTerminalStates", List.of(State.ABORTED, State.ABORTED, State.INCONSISTENT));
		}
		finally
		{
			service.shutdown(System.currentTimeMillis());
			deleteProfile(reservedProfile.profileId());
			deleteProfile(dispatchingProfile.profileId());
			deleteProfile(observingProfile.profileId());
		}
	}

	private void testBoundary(PhantomTestContext context) throws Exception
	{
		final PhantomProfile reservedProfile = createProfile(930001);
		final PhantomProfile dispatchedProfile = createProfile(930002);
		final PhantomEconomyReservationService service = new PhantomEconomyReservationService(_policy);
		try
		{
			final long now = System.currentTimeMillis();
			service.start();
			final PhantomEconomyOperation reserved = operation(reservedProfile.profileId(), 930001, 31, Kind.SELF_CRAFT, 1, now);
			service.reserve(reserved, List.of(itemCount(reservedProfile.profileId(), 930001, 88)));
			service.beforeBoundary(reservedProfile.profileId(), now + 1);
			PhantomAssertions.assertEquals(State.ABORTED, service.find(reserved.operationId()).orElseThrow().state(), "Predispatch boundary did not release the operation.");
			final PhantomEconomyOperation dispatched = operation(dispatchedProfile.profileId(), 930002, 32, Kind.ITEM_ENCHANT, 1, now);
			service.reserve(dispatched, List.of(itemObject(dispatchedProfile.profileId(), 930002, 882002, 0)));
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
			checksum += PhantomEconomyProjection.enchant(new EnchantRequest(enchantRequest.goal(), enchantRequest.target(), enchantRequest.targetObjectId(), enchantRequest.enchantLevel(), enchantRequest.targetLocation(), enchantRequest.scrollObjectId(), enchantRequest.scrollItemId(), enchantRequest.supportObjectId(), enchantRequest.supportItemId(), enchantRequest.augmented(), enchantRequest.elemented(), enchantRequest.timeLimited(), enchantRequest.leased(), enchantRequest.replacementEvidence(), enchantRequest.riskBudget(), enchantRequest.expenseBudget(), i, _policy)).nextRngState();
		}
		for (int i = 0; i < 10000; i++)
		{
			checksum += PhantomEconomyProjection.craft(new CraftRequest(acquisition, recipe, true, recipe.getLevel(), inventory, 100000, 100000, i, _policy)).nextRngState();
			checksum += PhantomEconomyProjection.enchant(new EnchantRequest(enchantRequest.goal(), enchantRequest.target(), enchantRequest.targetObjectId(), enchantRequest.enchantLevel(), enchantRequest.targetLocation(), enchantRequest.scrollObjectId(), enchantRequest.scrollItemId(), enchantRequest.supportObjectId(), enchantRequest.supportItemId(), enchantRequest.augmented(), enchantRequest.elemented(), enchantRequest.timeLimited(), enchantRequest.leased(), enchantRequest.replacementEvidence(), enchantRequest.riskBudget(), enchantRequest.expenseBudget(), i, _policy)).nextRngState();
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
		final long state = findEnchantRngState(candidate, expected);
		return state < 0 ? null : PhantomEconomyProjection.enchant(enchantRequest(enchantGoal(candidate), candidate, ItemLocation.INVENTORY, state));
	}

	private long findEnchantRngState(EnchantCandidate candidate, Result expected)
	{
		final PhantomEnchantGoalSpec goal = enchantGoal(candidate);
		for (long state = 0; state < 10000; state++)
		{
			if (PhantomEconomyProjection.enchant(enchantRequest(goal, candidate, ItemLocation.INVENTORY, state)).result() == expected)
			{
				return state;
			}
		}
		return -1;
	}

	private PhantomEnchantGoalSpec enchantGoal(EnchantCandidate candidate)
	{
		final long reserve = Math.max(0, candidate.target().getReferencePrice());
		return new PhantomEnchantGoalSpec(880001, candidate.enchantLevel() + 1, 16, 0, 0, true, reserve, Set.of(candidate.scroll().getId()), Set.of());
	}

	private EnchantRequest enchantRequest(PhantomEnchantGoalSpec goal, EnchantCandidate candidate, ItemLocation location, long rngState)
	{
		return new EnchantRequest(goal, candidate.target(), goal.targetObjectId(), candidate.enchantLevel(), location, 880002, candidate.scroll().getId(), 0, 0, false, false, false, false, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, rngState, _policy);
	}

	private static EnchantRequest enchantRequest(EnchantRequest source, long replacementEvidence, long riskBudget, long expenseBudget, PhantomEconomyPolicy policy)
	{
		return new EnchantRequest(source.goal(), source.target(), source.targetObjectId(), source.enchantLevel(), source.targetLocation(), source.scrollObjectId(), source.scrollItemId(), source.supportObjectId(), source.supportItemId(), source.augmented(), source.elemented(), source.timeLimited(), source.leased(), replacementEvidence, riskBudget, expenseBudget, source.rngState(), policy);
	}

	private static EnchantSupportItem selectEnchantSupport(EnchantCandidate candidate)
	{
		for (int itemId = 1; itemId <= 50000; itemId++)
		{
			final EnchantSupportItem support = EnchantItemData.getInstance().getSupportItemById(itemId);
			if ((support != null) && candidate.scroll().isValid(candidate.target(), candidate.enchantLevel(), support))
			{
				return support;
			}
		}
		return null;
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

	private static RecipeList selectAuthorityRecipeWithStatUse()
	{
		return Arrays.stream(RecipeData.getInstance().getAllItemIds()).mapToObj(RecipeData.getInstance()::getRecipeByItemId).filter(recipe -> (recipe != null) && (recipe.getRecipes().length > 0) && (recipe.getStatUse().length > 0) && Arrays.stream(recipe.getStatUse()).allMatch(stat -> (stat.getType() == StatType.HP) || (stat.getType() == StatType.MP)) && (ItemData.getInstance().getTemplate(recipe.getItemId()) != null)).sorted(Comparator.comparingInt(RecipeList::getId)).findFirst().orElseThrow(() -> new AssertionError("No production recipe with stat-use authority is available."));
	}

	private static RecipeList selectRepeatableRecipe()
	{
		return Arrays.stream(RecipeData.getInstance().getAllItemIds()).mapToObj(RecipeData.getInstance()::getRecipeByItemId).filter(recipe -> (recipe != null) && (recipe.getLevel() == 1) && (recipe.getSuccessRate() == 100) && (recipe.getCount() == 1) && (recipe.getRareItemId() <= 0) && (recipe.getRecipes().length > 0) && (recipe.getRecipes().length <= 8) && Arrays.stream(recipe.getRecipes()).allMatch(ingredient -> (ingredient.getQuantity() > 0) && (ingredient.getQuantity() <= 1000) && (ingredient.getItemId() != recipe.getItemId())) && Arrays.stream(recipe.getRecipes()).map(RecipeHolder::getItemId).distinct().count() == recipe.getRecipes().length && Arrays.stream(recipe.getStatUse()).allMatch(stat -> (((stat.getType() == StatType.HP) || (stat.getType() == StatType.MP)) && (stat.getValue() <= 10))) && (ItemData.getInstance().getTemplate(recipe.getItemId()) != null) && (ItemData.getInstance().getTemplate(recipe.getItemId()).getTime() == -1)).sorted(Comparator.comparingInt(RecipeList::getId)).findFirst().orElseThrow(() -> new AssertionError("No exact one-item, 100-percent repeatable recipe is available."));
	}

	private static RecipeList selectCraftOutcomeRecipe(boolean rare)
	{
		return Arrays.stream(RecipeData.getInstance().getAllItemIds()).mapToObj(RecipeData.getInstance()::getRecipeByItemId).filter(recipe ->
		{
			if ((recipe == null) || (recipe.getLevel() <= 0) || (recipe.getLevel() > 8) || (recipe.getSuccessRate() <= 0) || (recipe.getSuccessRate() >= 100) || (recipe.getCount() <= 0) || (recipe.getRecipes().length == 0) || (recipe.getRecipes().length > 8) || Arrays.stream(recipe.getRecipes()).anyMatch(ingredient -> (ingredient.getQuantity() <= 0) || (ingredient.getQuantity() > 1000) || (ingredient.getItemId() == recipe.getItemId())) || (Arrays.stream(recipe.getRecipes()).map(RecipeHolder::getItemId).distinct().count() != recipe.getRecipes().length) || Arrays.stream(recipe.getStatUse()).anyMatch(stat -> (stat.getType() != StatType.HP) && (stat.getType() != StatType.MP)))
			{
				return false;
			}
			final ItemTemplate target = ItemData.getInstance().getTemplate(recipe.getItemId());
			if ((target == null) || (target.getTime() != -1))
			{
				return false;
			}
			if (!rare)
			{
				return true;
			}
			final ItemTemplate rareTarget = ItemData.getInstance().getTemplate(recipe.getRareItemId());
			return PlayerConfig.CRAFT_MASTERWORK && (recipe.getRareItemId() > 0) && (recipe.getRareItemId() != recipe.getItemId()) && (recipe.getRareCount() > 0) && (recipe.getRarity() > 0) && (rareTarget != null) && (rareTarget.getTime() == -1);
		}).sorted(Comparator.comparingInt(RecipeList::getId)).findFirst().orElseThrow(() -> new AssertionError("No factual " + (rare ? "rare different-ID" : "canonical failure") + " recipe is available."));
	}

	private long findCraftRngState(RecipeList recipe, Result expected, boolean rare)
	{
		final PhantomAcquisitionState acquisition = acquisition(recipe);
		final Map<Integer, Long> inventory = ingredientInventory(recipe);
		for (long state = 0; state < 10000; state++)
		{
			final CraftOutcome outcome = PhantomEconomyProjection.craft(new CraftRequest(acquisition, recipe, true, recipe.getLevel(), inventory, 100000, 100000, state, _policy));
			if ((outcome.result() == expected) && (outcome.rare() == rare))
			{
				return state;
			}
		}
		return -1;
	}

	private static PhantomAcquisitionState acquisition(RecipeList recipe)
	{
		return acquisition(recipe, 7001, 3, 0, recipe.getCount(), recipe.getLevel());
	}

	private static PhantomAcquisitionState acquisition(RecipeList recipe, long goalId, long goalRevision, long baseline, long required, int skillLevel)
	{
		final String sourceId = PhantomEconomyOperation.sha256("recipe:" + recipe.getId());
		final Source source = new Source(sourceId, Method.RECIPE_PREPARATION, 0, recipe.getItemId(), "recipe:" + recipe.getId(), "self", "self", 0, 0, 0, 0, 0);
		final Candidate candidate = new Candidate(sourceId, Method.RECIPE_PREPARATION, 100, 0, 0, "");
		final long batches = Math.floorDiv(Math.addExact(required, recipe.getCount() - 1L), recipe.getCount());
		final List<RecipeNode> nodes = new ArrayList<>();
		nodes.add(new RecipeNode(recipe.getItemId(), required, 0, required, recipe.getId(), 0, false));
		for (RecipeHolder ingredient : recipe.getRecipes())
		{
			final long count = Math.multiplyExact(batches, ingredient.getQuantity());
			nodes.add(new RecipeNode(ingredient.getItemId(), count, count, 0, 0, 1, true));
		}
		final int skillId = recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId();
		final RecipePlan plan = new RecipePlan(recipe.getId(), recipe.getItemId(), required, batches, Math.multiplyExact(batches, recipe.getCount()), recipe.getSuccessRate(), recipe.isDwarvenRecipe(), skillId, skillLevel, nodes, List.of(), "recipe.ready");
		return new PhantomAcquisitionState(HASHES, goalId, goalRevision, recipe.getItemId(), required, baseline, baseline, 0, Status.PLANNING_ONLY, source, List.of(candidate), 0, 0, Phase.NONE, 0, 0, 0, plan, List.of(), 1);
	}

	private static PhantomGoal acquisitionGoal(long goalId, long revision, int itemId, long baseline, long required)
	{
		return new PhantomGoal(goalId, org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef("item", Integer.toString(itemId)), required, 0, Method.RECIPE_PREPARATION.key(), List.of(new PhantomDomainRef(org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec.SOURCE_NAMESPACE, Method.RECIPE_PREPARATION.key())), null, org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec.PURPOSE_KEY, 500, 0, 0, 0, Map.of(org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec.BASELINE_CONSTRAINT, baseline, org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec.MAXIMUM_SWITCHES_CONSTRAINT, 4L), "economy.craft.test", revision);
	}

	private static PhantomGoal enchantGoal(long goalId, long revision, int targetObjectId, int desiredLevel, int scrollItemId, boolean destructionPermitted, long replacementReserve)
	{
		return new PhantomGoal(goalId, PhantomEnchantGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "self"), new PhantomDomainRef(PhantomEnchantGoalSpec.TARGET_NAMESPACE, Integer.toString(targetObjectId)), 1, 0, null, List.of(new PhantomDomainRef(PhantomEnchantGoalSpec.SCROLL_NAMESPACE, Integer.toString(scrollItemId))), null, PhantomEnchantGoalSpec.PURPOSE_KEY, 500, replacementReserve, Long.MAX_VALUE, 0, Map.of(PhantomEnchantGoalSpec.TARGET_CONSTRAINT, (long) targetObjectId, PhantomEnchantGoalSpec.DESIRED_CONSTRAINT, (long) desiredLevel, PhantomEnchantGoalSpec.MAXIMUM_ATTEMPTS_CONSTRAINT, 1L, PhantomEnchantGoalSpec.ATTEMPTS_USED_CONSTRAINT, 0L, PhantomEnchantGoalSpec.EXPENSE_USED_CONSTRAINT, 0L, PhantomEnchantGoalSpec.DESTRUCTION_CONSTRAINT, destructionPermitted ? 1L : 0L, PhantomEnchantGoalSpec.REPLACEMENT_RESERVE_CONSTRAINT, replacementReserve), "economy.enchant.test", revision);
	}

	private BackgroundCraftFixture createBackgroundCraftFixture(int fundedAttempts) throws Exception
	{
		final RecipeList recipe = selectRepeatableRecipe();
		return createBackgroundCraftFixture(recipe, fundedAttempts, Math.multiplyExact((long) fundedAttempts, recipe.getCount()), SEED);
	}

	private BackgroundCraftFixture createBackgroundCraftFixture(RecipeList recipe, int fundedAttempts, long required, long rngState) throws Exception
	{
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(repository);
		final PhantomAcquisitionStore acquisitions = new PhantomAcquisitionStore(repository, goals);
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		Player player = null;
		final Map<Integer, Long> baselines = new HashMap<>();
		boolean recipeAdded = false;
		Skill addedSkill = null;
		double hpBaseline = 0;
		double mpBaseline = 0;
		try
		{
			materialization.start();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Background craft fixture did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Background craft fixture Player is absent.");
			hpBaseline = player.getCurrentHp();
			mpBaseline = player.getCurrentMp();
			player.setCurrentHp(player.getMaxHp());
			player.setCurrentMp(player.getMaxMp());
			final int skillId = recipe.isDwarvenRecipe() ? CommonSkill.CREATE_DWARVEN.getId() : CommonSkill.CREATE_COMMON.getId();
			final Skill skill = org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(skillId, recipe.getLevel());
			PhantomAssertions.assertTrue(skill != null, "Background craft fixture skill level is unavailable.");
			if (player.getKnownSkill(skillId) == null)
			{
				player.addSkill(skill, true);
				addedSkill = skill;
			}
			if (!player.hasRecipeList(recipe.getId()))
			{
				if (recipe.isDwarvenRecipe())
				{
					player.registerDwarvenRecipeList(recipe, true);
				}
				else
				{
					player.registerCommonRecipeList(recipe, true);
				}
				recipeAdded = true;
			}
			final Set<Integer> mutable = new java.util.TreeSet<>();
			for (RecipeHolder ingredient : recipe.getRecipes())
			{
				mutable.add(ingredient.getItemId());
				baselines.putIfAbsent(ingredient.getItemId(), player.getInventory().getInventoryItemCount(ingredient.getItemId(), -1));
				PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, ingredient.getItemId(), Math.multiplyExact((long) ingredient.getQuantity(), fundedAttempts), player, this) != null, "Could not fund background craft fixture.");
			}
			mutable.add(recipe.getItemId());
			final long targetBaseline = player.getInventory().getInventoryItemCount(recipe.getItemId(), -1);
			baselines.putIfAbsent(recipe.getItemId(), targetBaseline);
			if ((recipe.getRareItemId() > 0) && (recipe.getRareItemId() != recipe.getItemId()))
			{
				mutable.add(recipe.getRareItemId());
				baselines.putIfAbsent(recipe.getRareItemId(), player.getInventory().getInventoryItemCount(recipe.getRareItemId(), -1));
			}
			final int maximumLoad = Math.max(player.getMaxLoad(), 1);
			final int maximumSlots = Math.max(player.getInventoryLimit(), 1);
			final int skillLevel = player.getSkillLevel(skillId);
			materialization.shutdown();
			ensureCraftVitals(_environment.primary().objectId(), recipe);
			final long goalId = 2200220301L;
			final long revision = 1;
			final PhantomGoal goal = acquisitionGoal(goalId, revision, recipe.getItemId(), targetBaseline, required);
			goals.insert(profile.profileId(), goal);
			acquisitions.insert(profile.profileId(), acquisition(recipe, goalId, revision, targetBaseline, required, skillLevel));
			final PhantomBackgroundState background = backgroundState(profile.profileId(), _environment.primary().objectId(), mutable, rngState, maximumLoad, maximumSlots);
			repository.insertComponent(profile.profileId(), PhantomBackgroundState.COMPONENT_TYPE, PhantomBackgroundState.SCHEMA_VERSION, new PhantomBackgroundStateCodec().encode(background));
			return new BackgroundCraftFixture(_environment, profile, repository, goals, acquisitions, materialization, goal, recipe, targetBaseline, Map.copyOf(baselines), recipeAdded, addedSkill, hpBaseline, mpBaseline, _environment.primary().objectId());
		}
		catch (Throwable failure)
		{
			if (materialization.snapshot().state() != PhantomMaterializationService.ServiceState.STOPPED)
			{
				materialization.shutdown();
			}
			deleteProfile(profile.profileId());
			throw failure;
		}
	}

	private BackgroundEnchantFixture createBackgroundEnchantFixture() throws Exception
	{
		return createBackgroundEnchantFixture(selectEnchantCandidate(Boolean.TRUE, false), Result.SUCCESS);
	}

	private BackgroundEnchantFixture createBackgroundEnchantFixture(EnchantCandidate candidate, Result expected) throws Exception
	{
		final PhantomProfileRepository repository = PhantomProfileRepository.open();
		final PhantomProfile profile = repository.create(_environment.primary().objectId());
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(repository);
		final PhantomAcquisitionStore acquisitions = new PhantomAcquisitionStore(repository, goals);
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		Player player = null;
		final Map<Integer, Long> baselines = new HashMap<>();
		double hpBaseline = 0;
		double mpBaseline = 0;
		try
		{
			final long rngState = findEnchantRngState(candidate, expected);
			PhantomAssertions.assertTrue(rngState >= 0, "No deterministic background enchant RNG state for " + expected + ".");
			materialization.start();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Background enchant fixture did not materialize.");
			player = World.getInstance().getPlayer(_environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Background enchant fixture Player is absent.");
			hpBaseline = player.getCurrentHp();
			mpBaseline = player.getCurrentMp();
			baselines.put(candidate.target().getId(), player.getInventory().getInventoryItemCount(candidate.target().getId(), -1));
			baselines.put(candidate.scroll().getId(), player.getInventory().getInventoryItemCount(candidate.scroll().getId(), -1));
			baselines.put(Inventory.ADENA_ID, player.getAdena());
			if (candidate.target().getCrystalItemId() > 0)
			{
				baselines.put(candidate.target().getCrystalItemId(), player.getInventory().getInventoryItemCount(candidate.target().getCrystalItemId(), -1));
			}
			final Item target = player.getInventory().addItem(ItemProcessType.REWARD, candidate.target().getId(), 1, player, this);
			PhantomAssertions.assertTrue(target != null, "Could not create background enchant target.");
			target.setEnchantLevel(candidate.enchantLevel());
			PhantomAssertions.assertTrue(player.getInventory().addItem(ItemProcessType.REWARD, candidate.scroll().getId(), 1, player, this) != null, "Could not fund background enchant scroll.");
			final long replacementEvidence = Math.max(0, candidate.target().getReferencePrice());
			if (player.getAdena() < replacementEvidence)
			{
				player.getInventory().addItem(ItemProcessType.REWARD, Inventory.ADENA_ID, replacementEvidence - player.getAdena(), player, this);
			}
			final int maximumLoad = Math.max(player.getMaxLoad(), 1);
			final int maximumSlots = Math.max(player.getInventoryLimit(), 1);
			materialization.shutdown();
			final Set<Integer> mutable = new java.util.TreeSet<>(Set.of(candidate.target().getId(), candidate.scroll().getId(), Inventory.ADENA_ID));
			final PhantomBackgroundState background = backgroundState(profile.profileId(), _environment.primary().objectId(), mutable, rngState, maximumLoad, maximumSlots);
			repository.insertComponent(profile.profileId(), PhantomBackgroundState.COMPONENT_TYPE, PhantomBackgroundState.SCHEMA_VERSION, new PhantomBackgroundStateCodec().encode(background));
			final int scrollObjectId = inventoryObjectId(_environment.primary().objectId(), candidate.scroll().getId());
			final PhantomGoal goal = enchantGoal(2200220401L, 1, target.getObjectId(), target.getEnchantLevel() + 1, candidate.scroll().getId(), true, replacementEvidence);
			goals.insert(profile.profileId(), goal);
			return new BackgroundEnchantFixture(_environment, profile, repository, goals, acquisitions, materialization, goal, candidate.target().getId(), candidate.scroll().getId(), target.getObjectId(), scrollObjectId, replacementEvidence, Map.copyOf(baselines), hpBaseline, mpBaseline, _environment.primary().objectId());
		}
		catch (Throwable failure)
		{
			if (materialization.snapshot().state() != PhantomMaterializationService.ServiceState.STOPPED)
			{
				materialization.shutdown();
			}
			deleteProfile(profile.profileId());
			throw failure;
		}
	}

	private static PhantomBackgroundState backgroundState(long profileId, int characterObjectId, Set<Integer> mutableItemIds, long rngState, int maximumLoad, int maximumSlots) throws Exception
	{
		final BackgroundCanonical canonical = backgroundCanonical(characterObjectId);
		final List<BackgroundItem> rows = backgroundItems(characterObjectId);
		final List<CanonicalItem> canonicalItems = rows.stream().map(row -> new CanonicalItem(row.objectId(), row.itemId(), row.count(), row.location())).toList();
		long currentLoad = 0;
		int usedSlots = 0;
		final List<ItemObject> tracked = new ArrayList<>();
		for (BackgroundItem row : rows)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(row.itemId());
			currentLoad = Math.addExact(currentLoad, Math.multiplyExact(row.count(), template.getWeight()));
			if (row.location() == ItemLocation.INVENTORY)
			{
				usedSlots++;
				if (mutableItemIds.contains(row.itemId()))
				{
					tracked.add(new ItemObject(row.objectId(), row.itemId(), row.count(), template.isStackable(), row.location()));
				}
			}
		}
		final InventoryFacts inventory = InventoryFacts.sorted(List.copyOf(mutableItemIds), tracked, PhantomBackgroundInventoryHash.compute(canonicalItems), currentLoad, Math.max(maximumLoad, currentLoad), usedSlots, Math.max(maximumSlots, usedSlots));
		return new PhantomBackgroundState(PhantomBackgroundState.State.READY, new PhantomBackgroundState.Identity(profileId, characterObjectId, 0, canonical.classId(), canonical.race()), new Progress(canonical.level(), canonical.experience(), canonical.skillPoints(), canonical.experienceBeforeDeath()), new Vitals(canonical.currentHp(), canonical.maximumHp(), canonical.currentMp(), canonical.maximumMp(), canonical.currentCp(), canonical.maximumCp()), new Position(0, canonical.x(), canonical.y(), canonical.z(), canonical.heading(), "economy.test"), new CombatFacts(ModelKind.MELEE, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1), Loadout.none(), inventory, List.of(), new Clock(rngState, 0, 0), Receipt.empty(), new PhantomBackgroundState.Hashes("1".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64)));
	}

	private static BackgroundCanonical backgroundCanonical(int characterObjectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT level,exp,expBeforeDeath,sp,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race FROM characters WHERE charId=?"))
		{
			statement.setInt(1, characterObjectId);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "Background economy character is absent.");
				return new BackgroundCanonical(row.getInt(1), row.getLong(2), row.getLong(3), row.getLong(4), row.getDouble(5), row.getDouble(6), row.getDouble(7), row.getDouble(8), row.getDouble(9), row.getDouble(10), row.getInt(11), row.getInt(12), row.getInt(13), row.getInt(14), row.getInt(15), row.getInt(16));
			}
		}
	}

	private static List<BackgroundItem> backgroundItems(int characterObjectId) throws Exception
	{
		final List<BackgroundItem> result = new ArrayList<>();
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
		{
			statement.setInt(1, characterObjectId);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					result.add(new BackgroundItem(rows.getInt(1), rows.getInt(2), rows.getLong(3), ItemLocation.valueOf(rows.getString(4))));
				}
			}
		}
		return List.copyOf(result);
	}

	private static PhantomBackgroundState loadBackground(PhantomProfileRepository repository, long profileId)
	{
		return new PhantomBackgroundStateCodec().decode(repository.findComponent(profileId, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
	}

	private static void ensureCraftVitals(int characterObjectId, RecipeList recipe) throws Exception
	{
		double requiredHp = 1;
		double requiredMp = 0;
		for (var stat : recipe.getStatUse())
		{
			if (stat.getType() == StatType.HP)
			{
				requiredHp += stat.getValue();
			}
			else if (stat.getType() == StatType.MP)
			{
				requiredMp += stat.getValue();
			}
		}
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE characters SET curHp=GREATEST(curHp,?),maxHp=GREATEST(maxHp,?),curMp=GREATEST(curMp,?),maxMp=GREATEST(maxMp,?) WHERE charId=?"))
		{
			PhantomAssertions.assertEquals(TEST_DATABASE, connection.getCatalog(), "Craft vital fixture touched a non-test database.");
			statement.setDouble(1, requiredHp);
			statement.setDouble(2, requiredHp);
			statement.setDouble(3, requiredMp);
			statement.setDouble(4, requiredMp);
			statement.setInt(5, characterObjectId);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Craft vital fixture character is absent.");
		}
	}

	private static PhantomEconomyOperation backgroundOperation(long profileId, int characterObjectId, PhantomGoal goal, Kind kind, int attempt, String authorityHash, long now)
	{
		final Identity identity = new Identity(profileId, characterObjectId, goal.goalId(), goal.revision(), attempt, "economy.fault:" + attempt, 5, attempt);
		return new PhantomEconomyOperation(identity, kind, State.PREPARED, authorityHash, PhantomEconomyOperation.sha256("fault:" + attempt), PhantomEconomyOperation.utf8Payload("before"), PhantomEconomyOperation.utf8Payload("intent"), now, now, Math.addExact(now, 120000), 0);
	}

	private static EconomyDurableSnapshot economySnapshot(BackgroundCraftFixture fixture) throws Exception
	{
		final var background = fixture.repository().findComponent(fixture.profile().profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
		final var acquisition = fixture.acquisitions().load(fixture.profile().profileId()).orElseThrow();
		final var goal = fixture.goals().load(fixture.profile().profileId()).orElseThrow();
		final BackgroundCanonical canonical = backgroundCanonical(fixture.characterObjectId());
		return new EconomyDurableSnapshot(background.rowVersion(), Arrays.hashCode(background.payload()), acquisition.rowVersion(), acquisition.state().toString(), goal.rowVersion(), goal.goal().toString(), PhantomBackgroundInventoryHash.compute(backgroundItems(fixture.characterObjectId()).stream().map(row -> new CanonicalItem(row.objectId(), row.itemId(), row.count(), row.location())).toList()), canonical.currentHp(), canonical.currentMp(), scalarLong("SELECT COUNT(*) FROM phantom_economy_audit WHERE profile_id=?", fixture.profile().profileId()));
	}

	private static EnchantDurableSnapshot enchantSnapshot(BackgroundEnchantFixture fixture) throws Exception
	{
		final var background = fixture.repository().findComponent(fixture.profile().profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
		final var goal = fixture.goals().load(fixture.profile().profileId()).orElseThrow();
		return new EnchantDurableSnapshot(background.rowVersion(), Arrays.hashCode(background.payload()), goal.rowVersion(), goal.goal().toString(), PhantomBackgroundInventoryHash.compute(backgroundItems(fixture.characterObjectId()).stream().map(row -> new CanonicalItem(row.objectId(), row.itemId(), row.count(), row.location())).toList()), scalarLong("SELECT COUNT(*) FROM phantom_economy_audit WHERE profile_id=?", fixture.profile().profileId()));
	}

	private static PhantomEconomyBackgroundTransaction.EnchantCommand enchantCommand(BackgroundEnchantFixture fixture, String operationId, PhantomBackgroundState background, long backgroundRowVersion, PhantomGoal goal, long goalRowVersion, long now)
	{
		return new PhantomEconomyBackgroundTransaction.EnchantCommand(operationId, background, backgroundRowVersion, goal, goalRowVersion, fixture.targetObjectId(), fixture.targetItemId(), fixture.scrollObjectId(), fixture.scrollItemId(), 0, 0, now);
	}

	private static long inventoryCount(int characterObjectId, int itemId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=? AND loc='INVENTORY'"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, itemId);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "Background inventory count returned no row.");
				return row.getLong(1);
			}
		}
	}

	private static int inventoryObjectId(int characterObjectId, int itemId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT object_id FROM items WHERE owner_id=? AND item_id=? AND loc='INVENTORY' ORDER BY object_id LIMIT 1"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, itemId);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "Background exact inventory object is absent.");
				return row.getInt(1);
			}
		}
	}

	private static long inventoryObjectCount(int characterObjectId, int objectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND object_id=? AND loc='INVENTORY'"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, objectId);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "Background exact object count returned no row.");
				return row.getLong(1);
			}
		}
	}

	private static int inventoryEnchantLevel(int characterObjectId, int objectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT enchant_level FROM items WHERE owner_id=? AND object_id=? AND loc='INVENTORY'"))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, objectId);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "Surviving background enchant target is absent.");
				return row.getInt(1);
			}
		}
	}

	private static String activeItemEvidence(Player player, int objectId, int itemId)
	{
		final Item item = player.getInventory().getItemByObjectId(objectId);
		return item == null ? "absent:" + itemId : item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.getEnchantLevel() + ":" + item.getItemLocation().name();
	}

	private PhantomEconomyService economyService(PhantomEconomyReservationService reservations, PhantomMaterializationService materialization, PhantomAcquisitionStore acquisitions, PhantomGoalStateStore goals, PhantomProfileRepository repository)
	{
		return new PhantomEconomyService(_policy, reservations, new PhantomEconomyBackgroundTransaction(reservations, _policy), materialization, acquisitions, goals, repository);
	}

	private static DecisionHarness decisionHarness(PhantomEconomyService service, long profileId, PhantomGoal goal, PhantomActivityState state, long generation, long tick, long sequence)
	{
		final PhantomEconomyDecision decision = new PhantomEconomyDecision(service);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		decision.registerCandidates(candidates);
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		decision.registerHandlers(handlers);
		handlers.seal();
		final PhantomPlanningContext planning = new PhantomPlanningContext(profileId, goal, PhantomCapabilitySet.empty(), state, generation, tick, sequence, sequence);
		final PhantomPlan plan = candidates.snapshot().getFirst().planFactory().create(planning);
		return new DecisionHarness(profileId, goal, state, generation, tick, sequence, plan, handlers);
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
		return new Reservation(profileId, ownerObjectId, ResourceKind.ITEM_OBJECT, objectId, objectId, 0, 1, enchantLevel, "INVENTORY");
	}

	private static void assertEveryAuthorityFactChangesHash(PhantomEconomyProjection.AuthorityFacts authority)
	{
		for (int index = 0; index < authority.facts().size(); index++)
		{
			final List<PhantomEconomyProjection.AuthorityFact> changed = new ArrayList<>(authority.facts());
			final PhantomEconomyProjection.AuthorityFact original = changed.get(index);
			changed.set(index, new PhantomEconomyProjection.AuthorityFact(original.key(), original.value() + "#"));
			PhantomAssertions.assertFalse(authority.hash().equals(new PhantomEconomyProjection.AuthorityFacts(changed).hash()), "Authority fact did not affect the hash: " + original.key());
		}
	}

	private static Audit audit(Result result, String reason)
	{
		return new Audit(result, reason, new byte[0]);
	}

	private static PhantomProfile createProfile()
	{
		return PhantomProfileRepository.open().create(null);
	}

	private static PhantomProfile createProfile(int characterObjectId)
	{
		return PhantomProfileRepository.open().create(characterObjectId);
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

	private static long scalarLong(String sql, Object parameter) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(sql))
		{
			PhantomAssertions.assertEquals(TEST_DATABASE, connection.getCatalog(), "Economy scalar query touched a non-test database.");
			statement.setObject(1, parameter);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "Economy scalar query returned no row.");
				return row.getLong(1);
			}
		}
	}

	private static String scalarString(String sql, Object parameter) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(sql))
		{
			PhantomAssertions.assertEquals(TEST_DATABASE, connection.getCatalog(), "Economy scalar query touched a non-test database.");
			statement.setObject(1, parameter);
			try (ResultSet row = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(row.next(), "Economy scalar query returned no row.");
				return row.getString(1);
			}
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

	private static void cleanupBackgroundFixture(PhantomHeadlessPlayerTestEnvironment environment, PhantomProfile profile, PhantomProfileRepository repository, Map<Integer, Long> baselines, RecipeList recipe, boolean recipeAdded, Skill addedSkill, double hpBaseline, double mpBaseline) throws Exception
	{
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService cleanup = new PhantomMaterializationService(repository, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		Player player = null;
		try
		{
			cleanup.start();
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, cleanup.materialize(profile.profileId()).status(), "Background economy cleanup did not materialize.");
			player = World.getInstance().getPlayer(environment.primary().objectId());
			PhantomAssertions.assertTrue(player != null, "Background economy cleanup Player is absent.");
			for (Map.Entry<Integer, Long> entry : baselines.entrySet())
			{
				restoreItemCount(player, entry.getKey(), entry.getValue());
			}
			if (recipeAdded && (recipe != null) && player.hasRecipeList(recipe.getId()))
			{
				player.unregisterRecipeList(recipe.getId());
			}
			if (addedSkill != null)
			{
				player.removeSkill(addedSkill, true, true);
			}
			player.setCurrentHp(Math.min(hpBaseline, player.getMaxHp()));
			player.setCurrentMp(Math.min(mpBaseline, player.getMaxMp()));
		}
		finally
		{
			if (cleanup.snapshot().state() != PhantomMaterializationService.ServiceState.STOPPED)
			{
				cleanup.shutdown();
			}
			deleteProfile(profile.profileId());
			if (player != null)
			{
				environment.assertClean(environment.primary(), player);
			}
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

	private record EnchantOutcomeFixture(EnchantCandidate candidate, Result result)
	{
	}

	private record DecisionHarness(long profileId, PhantomGoal goal, PhantomActivityState state, long generation, long tick, long sequence, PhantomPlan plan, PhantomStepHandlerRegistry handlers)
	{
		private PhantomStepResult execute(int stepIndex, boolean cancelled)
		{
			final var step = plan.steps().get(stepIndex);
			return handlers.snapshot().get(step.actionKey()).execute(new PhantomStepContext(profileId, goal, plan, step, state, generation, tick, sequence, 1, () -> cancelled));
		}
	}

	private record BackgroundCanonical(int level, long experience, long experienceBeforeDeath, long skillPoints, double currentHp, double maximumHp, double currentMp, double maximumMp, double currentCp, double maximumCp, int x, int y, int z, int heading, int classId, int race)
	{
	}

	private record BackgroundItem(int objectId, int itemId, long count, ItemLocation location)
	{
	}

	private record EconomyDurableSnapshot(long backgroundRowVersion, int backgroundPayloadHash, long acquisitionRowVersion, String acquisition, long goalRowVersion, String goal, String inventoryHash, double currentHp, double currentMp, long auditCount)
	{
	}

	private record EnchantDurableSnapshot(long backgroundRowVersion, int backgroundPayloadHash, long goalRowVersion, String goal, String inventoryHash, long auditCount)
	{
	}

	private record BackgroundCraftFixture(PhantomHeadlessPlayerTestEnvironment environment, PhantomProfile profile, PhantomProfileRepository repository, PhantomGoalStateStore goals, PhantomAcquisitionStore acquisitions, PhantomMaterializationService materialization, PhantomGoal goal, RecipeList recipe, long targetBaseline, Map<Integer, Long> baselines, boolean recipeAdded, Skill addedSkill, double hpBaseline, double mpBaseline, int characterObjectId) implements AutoCloseable
	{
		@Override
		public void close() throws Exception
		{
			cleanupBackgroundFixture(environment, profile, repository, baselines, recipe, recipeAdded, addedSkill, hpBaseline, mpBaseline);
		}
	}

	private record BackgroundEnchantFixture(PhantomHeadlessPlayerTestEnvironment environment, PhantomProfile profile, PhantomProfileRepository repository, PhantomGoalStateStore goals, PhantomAcquisitionStore acquisitions, PhantomMaterializationService materialization, PhantomGoal goal, int targetItemId, int scrollItemId, int targetObjectId, int scrollObjectId, long replacementEvidence, Map<Integer, Long> baselines, double hpBaseline, double mpBaseline, int characterObjectId) implements AutoCloseable
	{
		@Override
		public void close() throws Exception
		{
			cleanupBackgroundFixture(environment, profile, repository, baselines, null, false, null, hpBaseline, mpBaseline);
		}
	}

	private static final class InjectedEconomyFailure extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		private InjectedEconomyFailure(FaultPoint point)
		{
			super(point.name());
		}
	}
}

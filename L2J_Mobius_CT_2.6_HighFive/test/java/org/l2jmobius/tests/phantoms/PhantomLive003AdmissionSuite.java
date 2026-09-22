/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.HistoricalPort;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.ArchivedResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Personality;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MemoryStore;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MutableClock;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.Ownership;

public final class PhantomLive003AdmissionSuite implements PhantomTestSuite
{
	private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
	private static final long SEED = 30030002L;
	private PhantomPopulationCatalog _population;
	private PhantomPopulationEcologyCatalog _ecology;

	@Override
	public String id()
	{
		return "live003-admission";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "LIVE-003 admission seed changed.");
		_population = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		final PhantomSocialCatalog social = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		_ecology = PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), _population, social);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("A01-eligible-profile-receives-active-admission", this::testEligibleAdmission);
		registry.add("A02-large-fenced-cohort-keeps-one-active-owner", this::testLargeCohort);
		registry.add("A03-permission-edge-rebalances-without-clock-or-restart", this::testPermissionEdge);
		registry.add("A04-native-ecology-predicate-rejects-non-cursor-fences", this::testNativePredicate);
		registry.add("A05-all-fenced-then-permitted-fills-bounded-target", this::testAllFencedThenPermitted);
		registry.add("A06-empty-region-quota-moves-to-eligible-region", this::testRegionalCapacity);
		registry.add("A07-A08-no-ecology-stays-deterministic-and-cap-bounded", this::testNoEcologyCapacity);
		registry.add("A09-ready-after-bootstrap-enters-eligible-admission", this::testReadyAfterBootstrap);
		registry.add("A10-minute-boundary-rebalances-without-duplicate-owner", this::testMinuteBoundary);
		registry.add("C09-C11-personal-status-read-only-voiced-route", this::testPersonalStatusRoute);
		registry.add("A13-non-admin-phantom-command-gets-explicit-denial", this::testAdminDenial);
	}

	private void testPersonalStatusRoute(PhantomTestContext context) throws Exception
	{
		final String relative = "dist/game/data/scripts/handlers/chat/commands/voiced/PhantomStatus.java";
		PhantomAssertions.assertTrue(Files.isRegularFile(context.moduleRoot().resolve(relative)), "Personal voiced Phantom status handler is missing.");
		final String handler = Files.readString(context.moduleRoot().resolve(relative));
		final String master = Files.readString(context.moduleRoot().resolve("dist/game/data/scripts/handlers/MasterHandler.java"));
		PhantomAssertions.assertTrue(master.contains("PhantomStatus.class"), "Personal status is not registered in canonical voiced dispatch.");
		PhantomAssertions.assertTrue(handler.contains("isPersonalUser(player)") && handler.contains("operatorStatus()") && handler.contains("operatorAdmissionProfile("), "Personal status must authorize before reading native snapshots.");
		for (String forbidden : java.util.List.of("operatorEnable(", "operatorDisable(", "operatorDrain(", "operatorReset", "operatorReplay", "operatorTrace"))
		{
			PhantomAssertions.assertFalse(handler.contains(forbidden), "Personal status contains mutating operator route " + forbidden);
		}
	}

	private void testReadyAfterBootstrap(PhantomTestContext context)
	{
		final MemoryStore store = new MemoryStore(_population.hash());
		seedMorning(store, 1, 1);
		for (long id = 2; id <= 3; id++)
		{
			final ManagedSnapshot pending = store.seed(id, PhantomPopulationState.State.INITIALIZING, PhantomPopulationState.CreationStage.VERIFIED, 1);
			final PhantomPopulationState state = pending.state();
			store.updateState(pending, new PhantomPopulationState(state.state(), state.populationGeneration(), state.creationOrdinal(), state.catalogHash(), state.initializationAuthorityHash(), state.deterministicSeed(), state.nameAttempt(), state.reservedAccount(), state.ownershipToken(), state.characterName(), state.classId(), state.female(), state.face(), state.hairColor(), state.hairStyle(), "morning", state.schedulePhaseMinutes(), state.homeMapRegionId(), state.creationX(), state.creationY(), state.creationZ(), state.expectedCharacterObjectId(), state.actualCharacterObjectId(), state.creationStage(), state.initializationHash(), state.lastFailure()));
		}
		final EcologyStore ecologyStore = new EcologyStore();
		ecologyStore._states.put(1L, new StoredState(ecologyState(minute(NOW)), 0));
		ecologyStore._states.put(2L, new StoredState(ecologyState(minute(NOW)), 0));
		ecologyStore._states.put(3L, new StoredState(ecologyState(minute(NOW) - 1), 0));
		final PhantomPopulationEcologyService ecology = ecology(ecologyStore, new IdleHistoricalPort());
		final Ownership ownership = new Ownership();
		final PhantomPopulationManager manager = manager(store, ownership, ecology, 3, 3, 8, 8);
		PhantomAssertions.assertTrue(manager.start(), "A09 manager did not start.");
		pulseUntil(manager, () -> ownership.activeIds().equals(Set.of(1L)));
		PhantomAssertions.assertEquals(CreationOutcome.READY, manager.advanceCreation(2).outcome(), "A09 permitted bootstrap did not become READY.");
		PhantomAssertions.assertEquals(CreationOutcome.READY, manager.advanceCreation(3).outcome(), "A09 fenced bootstrap did not become READY.");
		pulseUntil(manager, () -> ownership.activeIds().equals(Set.of(1L, 2L)));
		PhantomAssertions.assertEquals(2, manager.admissionSnapshot().eligibleActive(), "A09 fenced READY consumed eligible admission.");
		PhantomAssertions.assertEquals(2, manager.admissionSnapshot().admittedActive(), "A09 READY-after-bootstrap missed admission.");
		stop(manager);
	}

	private void testMinuteBoundary(PhantomTestContext context)
	{
		final MemoryStore store = new MemoryStore(_population.hash());
		seedMorning(store, 1, 1);
		seedMorning(store, 2, 1);
		final EcologyStore ecologyStore = new EcologyStore();
		ecologyStore._states.put(1L, new StoredState(ecologyState(minute(NOW) + 1), 0));
		ecologyStore._states.put(2L, new StoredState(ecologyState(minute(NOW)), 0));
		final MutableClock clock = new MutableClock(NOW);
		final PhantomPopulationEcologyService ecology = new PhantomPopulationEcologyService(_ecology, _population, ecologyStore, new IdleHistoricalPort(), id -> false, id -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 2);
		final Ownership ownership = new Ownership();
		final PhantomPopulationManager manager = new PhantomPopulationManager(store, _population, null, ownership, clock, ZoneOffset.UTC, 2, 2, 8, 8, 2, 32);
		manager.installEcology(ecology);
		PhantomAssertions.assertTrue(manager.start(), "A10 manager did not start.");
		pulseUntil(manager, () -> ownership.activeIds().equals(Set.of(1L, 2L)));
		clock.set(NOW.plusSeconds(60));
		pulseUntil(manager, () -> ownership.activeIds().equals(Set.of(1L)));
		PhantomAssertions.assertEquals(1, manager.admissionSnapshot().eligibleActive(), "A10 previous-minute eligibility survived the boundary.");
		PhantomAssertions.assertEquals(1, manager.admissionSnapshot().admittedActive(), "A10 previous-minute owner retained admission.");
		for (int pulse = 0; pulse < 10; pulse++)
		{
			manager.onPulse();
		}
		PhantomAssertions.assertEquals(Set.of(1L), ownership.activeIds(), "A10 repeated minute-boundary pulses duplicated or restored a fenced owner.");
		stop(manager);
	}

	private void testNoEcologyCapacity(PhantomTestContext context)
	{
		final MemoryStore store = new MemoryStore(_population.hash());
		for (long id = 1; id <= 100; id++)
		{
			seedMorning(store, id, 1);
		}
		final Ownership firstOwnership = new Ownership();
		final PhantomPopulationManager first = manager(store, firstOwnership, null);
		PhantomAssertions.assertTrue(first.start(), "No-ecology manager did not start.");
		pulseUntil(first, () -> firstOwnership.activeIds().size() == 64);
		final Set<Long> firstIds = firstOwnership.activeIds();
		first.reconcileTarget(100, 0);
		pulseUntil(first, () -> firstOwnership.activeIds().isEmpty());
		PhantomAssertions.assertEquals(0, first.admissionSnapshot().admittedActive(), "Zero ACTIVE target retained admission.");
		first.reconcileTarget(100, 32);
		pulseUntil(first, () -> firstOwnership.activeIds().size() == 32);
		PhantomAssertions.assertEquals(32, first.admissionSnapshot().admittedActive(), "Reduced ACTIVE target exceeded physical capacity.");
		stop(first);
		final Ownership restoredOwnership = new Ownership();
		final PhantomPopulationManager restored = manager(store, restoredOwnership, null);
		PhantomAssertions.assertTrue(restored.start(), "No-ecology restart did not start.");
		pulseUntil(restored, () -> restoredOwnership.activeIds().size() == 64);
		PhantomAssertions.assertEquals(firstIds, restoredOwnership.activeIds(), "No-ecology deterministic selection changed across restart.");
		stop(restored);
	}

	private void testNativePredicate(PhantomTestContext context)
	{
		final MemoryStore populationStore = new MemoryStore(_population.hash());
		final ManagedSnapshot population = seedMorning(populationStore, 1, 1);
		final PhantomPopulationEcologyState valid = ecologyState(minute(NOW));
		final PhantomPopulationEcologyState pending = new PhantomPopulationEcologyState(valid.catalogHash(), valid.preset(), valid.ecologyGeneration(), valid.assignmentOrdinal(), valid.assignedAtEpochMinute(), valid.virtualJoinEpochMinute(), valid.calendarCursorEpochMinute(), valid.initialTargetEpochMinute(), valid.pace(), valid.productiveShareBasisPoints(), valid.productiveBlockMinutes(), valid.personality(), valid.initialSocialTraits(), valid.scheduleTemplate(), valid.disposition(), valid.turnoverEligibleEpochMinute(), valid.replacesProfileId(), "a".repeat(64), minute(NOW) + 1, 0, 0, "");
		final PhantomPopulationEcologyState incomplete = new PhantomPopulationEcologyState(valid.catalogHash(), valid.preset(), valid.ecologyGeneration(), valid.assignmentOrdinal(), valid.assignedAtEpochMinute(), valid.virtualJoinEpochMinute(), valid.calendarCursorEpochMinute(), minute(NOW) + 1, valid.pace(), valid.productiveShareBasisPoints(), valid.productiveBlockMinutes(), valid.personality(), valid.initialSocialTraits(), valid.scheduleTemplate(), valid.disposition(), valid.turnoverEligibleEpochMinute(), valid.replacesProfileId(), "", 0, 0, 0, "");
		for (PhantomPopulationEcologyState state : java.util.List.of(pending, valid.archived(1, minute(NOW), "ecology.turnover"), incomplete))
		{
			final EcologyStore store = new EcologyStore();
			store._states.put(1L, new StoredState(state, 0));
			final PhantomPopulationEcologyService ecology = ecology(store, new IdleHistoricalPort());
			ecology.installRuntime(id -> Optional.of(population), noEvents());
			ecology.register(population);
			ecology.onPopulationPulse();
			PhantomAssertions.assertFalse(ecology.permitsScheduling(1), "Native ecology predicate accepted a non-cursor fence: " + state.disposition() + "/" + state.requestPending() + "/" + state.initialCatchupComplete());
		}
		final EcologyStore missing = new EcologyStore();
		final PhantomPopulationEcologyService ecology = ecology(missing, new IdleHistoricalPort());
		ecology.installRuntime(id -> Optional.of(population), noEvents());
		ecology.register(population);
		PhantomAssertions.assertFalse(ecology.permitsScheduling(1), "Missing stored assignment unexpectedly passed native ecology predicate.");
	}

	private void testAllFencedThenPermitted(PhantomTestContext context)
	{
		final MemoryStore populationStore = new MemoryStore(_population.hash());
		final EcologyStore ecologyStore = new EcologyStore();
		for (long id = 1; id <= 100; id++)
		{
			seedMorning(populationStore, id, 1);
			ecologyStore._states.put(id, new StoredState(ecologyState(minute(NOW) - 1), 0));
		}
		final AtomicBoolean release = new AtomicBoolean();
		final PhantomPopulationEcologyService ecology = ecology(ecologyStore, new CausalHistoricalPort(release));
		final Ownership ownership = new Ownership();
		final PhantomPopulationManager manager = manager(populationStore, ownership, ecology);
		PhantomAssertions.assertTrue(manager.start(), "All-fenced manager did not start.");
		pulseUntil(manager, () -> ecology.inventoryReady() && manager.snapshot().retryActions() == 0);
		PhantomAssertions.assertEquals(Set.of(), ownership.activeIds(), "Fenced population produced an ACTIVE owner.");
		PhantomAssertions.assertEquals(0, manager.admissionSnapshot().admittedActive(), "Fenced population consumed admission.");
		release.set(true);
		pulseUntil(manager, () -> ownership.activeIds().size() == 64);
		PhantomAssertions.assertEquals(64, manager.admissionSnapshot().admittedActive(), "Permitted population did not fill the bounded ACTIVE target.");
		stop(manager);
	}

	private void testPermissionEdge(PhantomTestContext context)
	{
		final MemoryStore populationStore = new MemoryStore(_population.hash());
		seedMorning(populationStore, 1, 1);
		seedMorning(populationStore, 2, 1);
		final EcologyStore ecologyStore = new EcologyStore();
		final PhantomPopulationEcologyState first = ecologyState(minute(NOW));
		ecologyStore._states.put(1L, new StoredState(new PhantomPopulationEcologyState(first.catalogHash(), first.preset(), first.ecologyGeneration(), first.assignmentOrdinal(), first.assignedAtEpochMinute(), first.virtualJoinEpochMinute(), first.calendarCursorEpochMinute(), first.initialTargetEpochMinute(), first.pace(), first.productiveShareBasisPoints(), first.productiveBlockMinutes(), first.personality(), first.initialSocialTraits(), first.scheduleTemplate(), first.disposition(), minute(NOW), first.replacesProfileId(), first.currentRequestId(), first.currentWindowTargetEpochMinute(), first.archiveGeneration(), first.archivedAtEpochMinute(), first.archiveReason()), 0));
		ecologyStore._states.put(2L, new StoredState(ecologyState(minute(NOW) - 1), 0));
		final AtomicBoolean release = new AtomicBoolean();
		final CausalHistoricalPort historical = new CausalHistoricalPort(release);
		final MutableClock clock = new MutableClock(NOW);
		final PhantomPopulationEcologyService ecology = new PhantomPopulationEcologyService(_ecology, _population, ecologyStore, historical, id -> false, id -> id == 1 && !release.get() ? "hold" : "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 2);
		final Ownership ownership = new Ownership();
		final PhantomPopulationManager manager = new PhantomPopulationManager(populationStore, _population, null, ownership, clock, ZoneOffset.UTC, 2, 1, 4, 2, 2, 32);
		manager.installEcology(ecology);
		PhantomAssertions.assertTrue(manager.start(), "Permission-edge manager did not start.");
		pulseUntil(manager, () -> ownership.activeIds().equals(Set.of(1L)));
		PhantomAssertions.assertEquals(Set.of(1L), ownership.activeIds(), "Initial permitted owner was not ACTIVE.");
		release.set(true);
		pulseUntil(manager, () -> ownership.activeIds().equals(Set.of(2L)));
		PhantomAssertions.assertEquals(NOW, clock.instant(), "Permission-edge fixture advanced the clock.");
		PhantomAssertions.assertEquals(1, manager.admissionSnapshot().admittedActive(), "Permission-edge admission did not move to the new owner.");
		PhantomAssertions.assertEquals(PhantomPopulationState.State.RETIRED, manager.find(1).orElseThrow().state().state(), "Revoked owner remained READY after ecology archive.");
		stop(manager);
	}

	private void testLargeCohort(PhantomTestContext context)
	{
		final MemoryStore populationStore = new MemoryStore(_population.hash());
		final EcologyStore ecologyStore = new EcologyStore();
		for (long id = 1; id <= 384; id++)
		{
			seedMorning(populationStore, id, 1);
			ecologyStore._states.put(id, new StoredState(ecologyState(id == 384 ? minute(NOW) : minute(NOW) - 1), 0));
		}
		final MutableClock clock = new MutableClock(NOW);
		final PhantomPopulationEcologyService ecology = new PhantomPopulationEcologyService(_ecology, _population, ecologyStore, new IdleHistoricalPort(), id -> false, id -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 512);
		final Ownership ownership = new Ownership();
		final PhantomPopulationManager manager = manager(populationStore, ownership, ecology, 384, 64, 512, 128);
		PhantomAssertions.assertTrue(manager.start(), "Large-cohort manager did not start.");
		pulseUntil(manager, () -> ecology.inventoryReady() && (manager.snapshot().retryActions() == 0));
		PhantomAssertions.assertEquals(Set.of(384L), ownership.activeIds(), "Large fenced cohort displaced its sole eligible ACTIVE owner.");
		PhantomAssertions.assertEquals(1, manager.admissionSnapshot().eligibleActive(), "Large-cohort eligibility count drifted.");
		stop(manager);
	}

	private void testRegionalCapacity(PhantomTestContext context)
	{
		final MemoryStore populationStore = new MemoryStore(_population.hash());
		final EcologyStore ecologyStore = new EcologyStore();
		for (long id = 1; id <= 102; id++)
		{
			seedMorning(populationStore, id, id <= 100 ? 1 : 2);
			ecologyStore._states.put(id, new StoredState(ecologyState(id > 100 ? minute(NOW) : minute(NOW) - 1), 0));
		}
		final MutableClock clock = new MutableClock(NOW);
		final PhantomPopulationEcologyService ecology = new PhantomPopulationEcologyService(_ecology, _population, ecologyStore, new IdleHistoricalPort(), id -> false, id -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 128);
		final Ownership ownership = new Ownership();
		final PhantomPopulationManager manager = manager(populationStore, ownership, ecology, 102, 64, 128, 64);
		PhantomAssertions.assertTrue(manager.start(), "Regional manager did not start.");
		pulseUntil(manager, () -> ecology.inventoryReady() && (manager.snapshot().retryActions() == 0));
		PhantomAssertions.assertEquals(Set.of(101L, 102L), ownership.activeIds(), "Empty fenced region retained quota that belonged to eligible region.");
		stop(manager);
	}

	private void testAdminDenial(PhantomTestContext context) throws Exception
	{
		final String dispatcher = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/handler/AdminCommandHandler.java"));
		final int guard = dispatcher.indexOf("if (!player.isGM())");
		final int handler = dispatcher.indexOf("final String command = fullCommand.split", guard);
		PhantomAssertions.assertTrue((guard >= 0) && (handler > guard) && dispatcher.substring(guard, handler).contains("admin_phantom") && dispatcher.substring(guard, handler).contains("player.sendMessage"), "Non-admin Phantom command is silently discarded before the native access check.");
		final String access = Files.readString(context.moduleRoot().resolve("dist/game/config/AdminCommands.xml"));
		PhantomAssertions.assertTrue(access.contains("command=\"phantom\"") && access.contains("accessLevel=\"100\""), "Phantom operator access level was weakened.");
	}

	private void testEligibleAdmission(PhantomTestContext context)
	{
		final MemoryStore populationStore = new MemoryStore(_population.hash());
		for (long id = 1; id <= 100; id++)
		{
			seedMorning(populationStore, id, 1);
		}
		final Ownership baselineOwnership = new Ownership();
		final PhantomPopulationManager baseline = manager(populationStore, baselineOwnership, null);
		PhantomAssertions.assertTrue(baseline.start(), "Baseline manager did not start.");
		pulseUntil(baseline, () -> baselineOwnership.activeIds().size() == 64);
		final Set<Long> oldSelection = baselineOwnership.activeIds();
		stop(baseline);
		final long permitted = java.util.stream.LongStream.rangeClosed(1, 100).filter(id -> !oldSelection.contains(id)).findFirst().orElseThrow();

		final MutableClock clock = new MutableClock(NOW);
		final EcologyStore ecologyStore = new EcologyStore();
		for (long id = 1; id <= 100; id++)
		{
			ecologyStore._states.put(id, new StoredState(ecologyState(id == permitted ? minute(NOW) : minute(NOW) - 1), 0));
		}
		final PhantomPopulationEcologyService ecology = new PhantomPopulationEcologyService(_ecology, _population, ecologyStore, new IdleHistoricalPort(), id -> false, id -> "", clock, ZoneOffset.UTC, Preset.LIVING, 0, 100);
		final Ownership ownership = new Ownership();
		final PhantomPopulationManager manager = manager(populationStore, ownership, ecology);
		PhantomAssertions.assertTrue(manager.start(), "Ecology manager did not start.");
		pulseUntil(manager, () -> ecology.inventoryReady() && (manager.snapshot().retryActions() == 0));
		PhantomAssertions.assertEquals(Set.of(permitted), ownership.activeIds(), "Ecology-fenced READY profiles consumed ACTIVE admission capacity.");
		PhantomAssertions.assertEquals(100, manager.admissionSnapshot().desiredActive(), "Desired ACTIVE count lost fenced READY profiles.");
		PhantomAssertions.assertEquals(1, manager.admissionSnapshot().eligibleActive(), "Admission eligibility did not use the full ecology fence.");
		PhantomAssertions.assertEquals(1, manager.admissionSnapshot().admittedActive(), "Admitted ACTIVE count did not match the selected owner.");
		stop(manager);
		final Ownership restoredOwnership = new Ownership();
		final PhantomPopulationEcologyService restoredEcology = new PhantomPopulationEcologyService(_ecology, _population, ecologyStore, new IdleHistoricalPort(), id -> false, id -> "", new MutableClock(NOW), ZoneOffset.UTC, Preset.LIVING, 0, 100);
		final PhantomPopulationManager restored = manager(populationStore, restoredOwnership, restoredEcology);
		PhantomAssertions.assertTrue(restored.start(), "Durable ecology restart did not start.");
		pulseUntil(restored, () -> restoredEcology.inventoryReady() && restored.snapshot().retryActions() == 0);
		PhantomAssertions.assertEquals(Set.of(permitted), restoredOwnership.activeIds(), "Restart failed to restore eligibility and admission from existing assignments.");
		stop(restored);
		context.record("live003.permitted", permitted);
	}

	private PhantomPopulationManager manager(MemoryStore store, Ownership ownership, PhantomPopulationEcologyService ecology)
	{
		return manager(store, ownership, ecology, 100, 64, 128, 64);
	}

	private PhantomPopulationManager manager(MemoryStore store, Ownership ownership, PhantomPopulationEcologyService ecology, int target, int activeTarget, int maximumScheduled, int maximumMaterialized)
	{
		final PhantomPopulationManager manager = new PhantomPopulationManager(store, _population, null, ownership, new MutableClock(NOW), ZoneOffset.UTC, target, activeTarget, maximumScheduled, maximumMaterialized, 8, 512);
		if (ecology != null)
		{
			manager.installEcology(ecology);
		}
		return manager;
	}

	private static ManagedSnapshot seedMorning(MemoryStore store, long id, int region)
	{
		final ManagedSnapshot seeded = store.seedReady(id, region);
		final PhantomPopulationState state = seeded.state();
		return store.updateState(seeded, new PhantomPopulationState(state.state(), state.populationGeneration(), state.creationOrdinal(), state.catalogHash(), state.initializationAuthorityHash(), state.deterministicSeed(), state.nameAttempt(), state.reservedAccount(), state.ownershipToken(), state.characterName(), state.classId(), state.female(), state.face(), state.hairColor(), state.hairStyle(), "morning", state.schedulePhaseMinutes(), state.homeMapRegionId(), state.creationX(), state.creationY(), state.creationZ(), state.expectedCharacterObjectId(), state.actualCharacterObjectId(), state.creationStage(), state.initializationHash(), state.lastFailure()));
	}

	private PhantomPopulationEcologyService ecology(EcologyStore store, HistoricalPort historical)
	{
		return new PhantomPopulationEcologyService(_ecology, _population, store, historical, id -> false, id -> "", new MutableClock(NOW), ZoneOffset.UTC, Preset.LIVING, 0, 128);
	}

	private static PhantomPopulationEcologyService.PopulationEvents noEvents()
	{
		return new PhantomPopulationEcologyService.PopulationEvents()
		{
			@Override
			public void requestArchive(long profileId)
			{
			}

			@Override
			public void reconcilePopulation()
			{
			}

			@Override
			public void ecologyFenceChanged(long profileId)
			{
			}
		};
	}

	private PhantomPopulationEcologyState ecologyState(long cursor)
	{
		final long now = minute(NOW);
		return new PhantomPopulationEcologyState(_ecology.hash(), Preset.LIVING, 1, 1, now - 1, now - 1, cursor, now - 1, Pace.OUTLIER, 10000, _ecology.limits().productiveBlockMinutes(), Personality.BALANCED, Map.of(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0), "morning", Disposition.MANAGED, now + 10000, 0, "", 0, 0, 0, "");
	}

	private static long minute(Instant instant)
	{
		return instant.toEpochMilli() / 60000;
	}

	private static void pulseUntil(PhantomPopulationManager manager, java.util.function.BooleanSupplier done)
	{
		for (int pulse = 0; (pulse < 1000) && !done.getAsBoolean(); pulse++)
		{
			manager.onPulse();
		}
		PhantomAssertions.assertTrue(done.getAsBoolean(), "Population control did not converge within 1000 pulses.");
	}

	private static void stop(PhantomPopulationManager manager)
	{
		manager.beginStop();
		PhantomAssertions.assertTrue(manager.finishStop(), "Population manager did not stop.");
	}

	private static final class EcologyStore implements PersistencePort
	{
		private final Map<Long, StoredState> _states = new HashMap<>();

		@Override
		public Optional<StoredState> load(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}

		@Override
		public StoredState insert(long profileId, PhantomPopulationEcologyState state)
		{
			return _states.computeIfAbsent(profileId, id -> new StoredState(state, 0));
		}

		@Override
		public StoredState save(long profileId, StoredState expected, PhantomPopulationEcologyState replacement)
		{
			if (_states.get(profileId) != expected)
			{
				throw new java.util.ConcurrentModificationException("Ecology test state changed unexpectedly.");
			}
			final StoredState saved = new StoredState(replacement, expected.rowVersion() + 1);
			_states.put(profileId, saved);
			return saved;
		}

		@Override
		public ArchivedResult archive(ManagedSnapshot population, StoredState ecology, long archiveGeneration, long nowEpochMinute)
		{
			final StoredState saved = save(population.profile().profileId(), ecology, ecology.state().archived(archiveGeneration, nowEpochMinute, "ecology.turnover"));
			return new ArchivedResult(new ManagedSnapshot(population.profile(), population.component(), population.state().retired()), saved);
		}
	}

	private static final class CausalHistoricalPort implements HistoricalPort
	{
		private final AtomicBoolean _release;
		private final Map<Long, PhantomBackgroundCatchupStore.Snapshot> _states = new HashMap<>();

		private CausalHistoricalPort(AtomicBoolean release)
		{
			_release = release;
		}

		@Override
		public Optional<PhantomBackgroundCatchupStore.Snapshot> status(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}

		@Override
		public PhantomHistoricalBackgroundService.Result begin(long profileId, long fromEpochMinute, long targetEpochMinute, long deterministicSeed)
		{
			if (!_release.get())
			{
				return PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED, "fixture.waiting", null);
			}
			final PhantomBackgroundCatchupStore.Snapshot existing = _states.get(profileId);
			if (existing != null)
			{
				return PhantomHistoricalBackgroundService.Result.success(existing, 0);
			}
			final PhantomBackgroundState.Hashes hashes = new PhantomBackgroundState.Hashes("knowledge", "topology", "progression", "commerce");
			final PhantomBackgroundCatchupState state = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING, "a".repeat(64), deterministicSeed, fromEpochMinute, targetEpochMinute, fromEpochMinute, 0, 0, 1, 1, 1, 1, 0, "b".repeat(64), PhantomBackgroundState.MODEL_VERSION, hashes, "").running();
			final PhantomBackgroundCatchupStore.Snapshot created = new PhantomBackgroundCatchupStore.Snapshot(state, 0);
			_states.put(profileId, created);
			return PhantomHistoricalBackgroundService.Result.success(created, 0);
		}

		@Override
		public PhantomHistoricalBackgroundService.Result advance(long profileId, int maximumIntervals, int maximumMinutes)
		{
			final PhantomBackgroundCatchupStore.Snapshot current = _states.get(profileId);
			final PhantomBackgroundCatchupStore.Snapshot saved = new PhantomBackgroundCatchupStore.Snapshot(current.state().advanceTo(current.state().targetEpochMinute()), current.rowVersion() + 1);
			_states.put(profileId, saved);
			return PhantomHistoricalBackgroundService.Result.success(saved, 1);
		}
	}

	private static final class IdleHistoricalPort implements HistoricalPort
	{
		@Override
		public Optional<PhantomBackgroundCatchupStore.Snapshot> status(long profileId)
		{
			return Optional.empty();
		}

		@Override
		public PhantomHistoricalBackgroundService.Result begin(long profileId, long fromEpochMinute, long targetEpochMinute, long deterministicSeed)
		{
			return PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED, "fixture.fenced", null);
		}

		@Override
		public PhantomHistoricalBackgroundService.Result advance(long profileId, int maximumIntervals, int maximumMinutes)
		{
			throw new AssertionError("Admission fixture unexpectedly advanced catch-up.");
		}
	}
}

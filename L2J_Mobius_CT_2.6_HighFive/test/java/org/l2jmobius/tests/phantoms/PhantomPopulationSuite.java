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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.PlayerOutboundSession;
import org.l2jmobius.gameserver.network.serverpackets.ServerPacket;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.UnregisterStatus;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSinkBridge;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.DetachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationDecision;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.AuthorityFailure;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.FaultInjectedException;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.FaultPoint;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.PopulationAuthorityException;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ManagedProfile;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.MemoryStore;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.Ownership;

public final class PhantomPopulationSuite implements PhantomTestSuite
{
	private static final long SEED = 16_001_601L;
	private final Mode _mode;
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomProfileRepository _profiles;
	private PhantomPopulationCatalog _catalog;

	public PhantomPopulationSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "population-" + _mode.name().toLowerCase().replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 016 suites require the exact deterministic seed.");
		_catalog = PhantomPopulationCatalog.load(catalogPath(context), ZoneOffset.UTC);
		if (_mode.requiresDatabase())
		{
			_environment = new PhantomHeadlessPlayerTestEnvironment();
			_environment.initialize(context);
			InitialEquipmentData.getInstance();
			InitialShortcutData.getInstance();
			MapRegionData.getInstance();
			_profiles = PhantomProfileRepository.open();
			cleanupManaged();
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		if (_mode.requiresDatabase())
		{
			try
			{
				cleanupManaged();
			}
			catch (Throwable throwable)
			{
				failure = throwable;
			}
			try
			{
				_environment.shutdown();
			}
			catch (Throwable throwable)
			{
				if (failure == null)
				{
					failure = throwable;
				}
				else
				{
					failure.addSuppressed(throwable);
				}
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CATALOG ->
			{
				registry.add("01-production-catalog-determinism-and-coverage", this::testCatalog);
				registry.add("02-invalid-xxe-and-overlap-controls", this::testInvalidCatalogs);
				registry.add("03-state-codec-canonical-bounds", this::testStateCodec);
			}
			case SCHEDULE ->
			{
				registry.add("01-midnight-gap-and-latest-state", this::testScheduleStates);
				registry.add("02-dst-gap-overlap-and-clock-direction", this::testDst);
				registry.add("03-active-admission-region-quota-and-rotation", this::testAdmission);
			}
			case CREATION ->
			{
				registry.add("01-two-real-classes-restart-safe-level-one", this::testRealCreation);
				registry.add("02-name-and-account-collision-controls", this::testCollisions);
				registry.add("03-every-durable-creation-stage-restarts-without-duplicates", this::testCreationStageRestarts);
				registry.add("04-transport-neutral-initializer-and-client-delivery-parity", this::testTransportNeutralInitialization);
				registry.add("05-authority-drift-legacy-and-writer-fault-restarts", this::testAuthorityAndWriterFaults);
				registry.add("06-exact-projection-negative-and-subset-controls", this::testProjectionControls);
			}
			case RECONCILIATION ->
			{
				registry.add("01-target-zero-three-one-three-return-before-create", this::testReconciliation);
				registry.add("02-bounded-ownership-retry-status-machine", this::testOwnershipRetries);
				registry.add("03-target-reduction-and-return-every-creation-stage", this::testEveryStageRetirementReturn);
			}
			case LIFECYCLE ->
			{
				registry.add("01-target-zero-inert-and-bounded-lifecycle", this::testLifecycle);
				registry.add("02-config-and-static-initializer-parity", this::testStaticParity);
				registry.add("03-shutdown-publication-barrier-and-system-observability", this::testShutdownPublicationAndSnapshot);
			}
			case SERVER_INTEGRATION -> registry.add("01-real-create-materialize-sleep-retire", this::testServerIntegration);
			case PERFORMANCE -> throw new IllegalArgumentException("Performance mode uses PhantomPopulationPerformanceSuite.");
		}
	}

	private void testCatalog(PhantomTestContext context)
	{
		final PhantomPopulationCatalog second = PhantomPopulationCatalog.load(catalogPath(context), ZoneOffset.UTC);
		PhantomAssertions.assertEquals(_catalog.hash(), second.hash(), "Population catalog hash is not deterministic.");
		PhantomAssertions.assertEquals(List.of("morning", "evening", "late"), List.copyOf(_catalog.templates().keySet()), "Population schedule declaration order is not canonical.");
		for (long value = SEED; value < (SEED + 32); value++)
		{
			PhantomAssertions.assertEquals(_catalog.chooseSchedule(value).id(), second.chooseSchedule(value).id(), "Weighted schedule selection changed after catalog reload.");
		}
		PhantomAssertions.assertEquals(11, _catalog.classes().size(), "Population catalog does not cover all canonical starting classes.");
		final Set<Integer> ids = new HashSet<>();
		_catalog.classes().forEach(entry -> ids.add(entry.classId()));
		PhantomAssertions.assertEquals(Set.of(0, 10, 18, 25, 31, 38, 44, 49, 53, 123, 124), ids, "Starting-class coverage mismatch.");
		for (int attempt = 0; attempt < 32; attempt++)
		{
			PhantomAssertions.assertTrue(_catalog.name(SEED, attempt).matches("[A-Za-z0-9]{1,16}"), "Generated population name is invalid.");
		}
		context.record("population.catalogHash", _catalog.hash());
		context.record("population.startingClasses", ids.size());
	}

	private void testInvalidCatalogs(PhantomTestContext context) throws Exception
	{
		final String production = Files.readString(catalogPath(context), StandardCharsets.UTF_8);
		final Path xxe = Files.createTempFile("population-xxe-", ".xml");
		final Path overlap = Files.createTempFile("population-overlap-", ".xml");
		try
		{
			Files.writeString(xxe, production.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE populationCatalog [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"), StandardCharsets.UTF_8);
			assertRejected(xxe, "XXE catalog was accepted.");
			Files.writeString(overlap, production.replace("<window days=\"MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY\" start=\"08:00\" end=\"11:30\" state=\"ACTIVE\"/>", "<window days=\"MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY\" start=\"07:00\" end=\"11:30\" state=\"ACTIVE\"/>"), StandardCharsets.UTF_8);
			assertRejected(overlap, "Overlapping schedule windows were accepted.");
		}
		finally
		{
			Files.deleteIfExists(xxe);
			Files.deleteIfExists(overlap);
		}
	}

	private void testStateCodec(PhantomTestContext context)
	{
		final PhantomPopulationState state = sampleState();
		final PhantomPopulationStateCodec codec = new PhantomPopulationStateCodec();
		final byte[] first = codec.encode(state);
		final byte[] second = codec.encode(codec.decode(first));
		PhantomAssertions.assertTrue(java.util.Arrays.equals(first, second), "Population state codec is not canonical.");
		PhantomAssertions.assertTrue(first.length <= 4096, "Population state exceeds component capacity.");
		final byte[] trailing = java.util.Arrays.copyOf(first, first.length + 1);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(trailing), "Population state trailing bytes were accepted.");
	}

	private void testScheduleStates(PhantomTestContext context)
	{
		final var active = _catalog.evaluate("evening", Instant.parse("2026-07-27T20:00:00Z"), ZoneOffset.UTC, 0);
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, active.state(), "Monday evening did not evaluate ACTIVE.");
		final var sleeping = _catalog.evaluate("evening", Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC, 0);
		PhantomAssertions.assertEquals(PhantomActivityState.SLEEPING, sleeping.state(), "Schedule gap did not evaluate SLEEPING.");
		final var beforeMidnight = _catalog.evaluate("late", Instant.parse("2026-07-31T23:30:00Z"), ZoneOffset.UTC, 0);
		final var afterMidnight = _catalog.evaluate("late", Instant.parse("2026-08-01T01:30:00Z"), ZoneOffset.UTC, 0);
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, beforeMidnight.state(), "Midnight-wrap start state mismatch.");
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, afterMidnight.state(), "Midnight-wrap continuation state mismatch.");
		final var jumped = _catalog.evaluate("evening", Instant.parse("2026-07-28T21:00:00Z"), ZoneOffset.UTC, 0);
		PhantomAssertions.assertEquals(PhantomActivityState.ACTIVE, jumped.state(), "Forward jump did not apply only the latest state.");
		PhantomAssertions.assertTrue(jumped.nextBoundary().isAfter(Instant.parse("2026-07-28T21:00:00Z")), "Next schedule boundary is not future.");
	}

	private void testDst(PhantomTestContext context)
	{
		final ZoneId kyiv = ZoneId.of("Europe/Kyiv");
		final var spring = _catalog.evaluate("morning", Instant.parse("2026-03-29T00:30:00Z"), kyiv, 20);
		final var autumn = _catalog.evaluate("morning", Instant.parse("2026-10-25T00:30:00Z"), kyiv, -20);
		PhantomAssertions.assertTrue(spring.nextBoundary().isAfter(Instant.parse("2026-03-29T00:30:00Z")), "DST gap produced a non-future boundary.");
		PhantomAssertions.assertTrue(autumn.nextBoundary().isAfter(Instant.parse("2026-10-25T00:30:00Z")), "DST overlap produced a non-future boundary.");
		final var backwardLatest = _catalog.evaluate("evening", Instant.parse("2026-10-24T18:00:00Z"), kyiv, 0);
		PhantomAssertions.assertTrue(backwardLatest.nextBoundary().isAfter(Instant.parse("2026-10-24T18:00:00Z")), "Backward-clock recomputation exposed an old boundary.");
	}

	private void testAdmission(PhantomTestContext context)
	{
		final List<PhantomPopulationManager.AdmissionProfile> profiles = new ArrayList<>();
		for (long id = 1; id <= 10; id++)
		{
			profiles.add(new PhantomPopulationManager.AdmissionProfile(id, id <= 6 ? 1 : 2, SEED + id, PhantomActivityState.ACTIVE));
		}
		final Set<Long> first = PhantomPopulationManager.selectActiveProfiles(profiles, 5, 100);
		final Set<Long> same = PhantomPopulationManager.selectActiveProfiles(profiles, 5, 100);
		final Set<Long> nextDay = PhantomPopulationManager.selectActiveProfiles(profiles, 5, 101);
		PhantomAssertions.assertEquals(5, first.size(), "ACTIVE admission cap mismatch.");
		PhantomAssertions.assertEquals(first, same, "Same-day ACTIVE rotation is unstable.");
		PhantomAssertions.assertTrue(!first.equals(nextDay), "Daily ACTIVE rotation did not change.");
		final long regionOne = first.stream().filter(id -> id <= 6).count();
		PhantomAssertions.assertEquals(3L, regionOne, "Largest-remainder regional quota mismatch.");
	}

	private void testRealCreation(PhantomTestContext context) throws Exception
	{
		final PhantomPopulationStore store = new PhantomPopulationStore(_profiles, _catalog);
		final Set<Integer> classes = new HashSet<>();
		for (int ordinal = 1; (ordinal <= 6) && (classes.size() < 2); ordinal++)
		{
			ManagedSnapshot snapshot = store.createShell(1, ordinal, SEED);
			for (int stage = 0; (stage < 10) && snapshot.state().state() != PhantomPopulationState.State.READY; stage++)
			{
				final var result = store.advanceCreation(snapshot);
				PhantomAssertions.assertTrue(result.outcome() != CreationOutcome.INCONSISTENT, "Real population creation became inconsistent at " + snapshot.state().creationStage() + ": " + result.snapshot().state().lastFailure() + ".");
				snapshot = store.reload(snapshot.profile().profileId());
			}
			PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, snapshot.state().state(), "Population character did not reach READY.");
			PhantomAssertions.assertEquals(snapshot.profile().characterObjectId(), snapshot.state().actualCharacterObjectId(), "Profile link and verified object ID differ.");
			classes.add(snapshot.state().classId());
			assertDurableCharacter(snapshot);
			final long beforeItems = count("SELECT COUNT(*) FROM items WHERE owner_id=?", snapshot.state().actualCharacterObjectId());
			final long beforeSkills = count("SELECT COUNT(*) FROM character_skills WHERE charId=?", snapshot.state().actualCharacterObjectId());
			final long beforeShortcuts = count("SELECT COUNT(*) FROM character_shortcuts WHERE charId=?", snapshot.state().actualCharacterObjectId());
			PhantomAssertions.assertEquals(CreationOutcome.READY, store.advanceCreation(snapshot).outcome(), "READY creation retry was not idempotent.");
			PhantomAssertions.assertEquals(beforeItems, count("SELECT COUNT(*) FROM items WHERE owner_id=?", snapshot.state().actualCharacterObjectId()), "READY retry duplicated items.");
			PhantomAssertions.assertEquals(beforeSkills, count("SELECT COUNT(*) FROM character_skills WHERE charId=?", snapshot.state().actualCharacterObjectId()), "READY retry duplicated skills.");
			PhantomAssertions.assertEquals(beforeShortcuts, count("SELECT COUNT(*) FROM character_shortcuts WHERE charId=?", snapshot.state().actualCharacterObjectId()), "READY retry duplicated shortcuts.");
		}
		PhantomAssertions.assertTrue(classes.size() >= 2, "Deterministic fixtures did not create two different starting classes.");
		context.record("population.realClasses", classes);
	}

	private void testCollisions(PhantomTestContext context) throws Exception
	{
		final PhantomPopulationStore store = new PhantomPopulationStore(_profiles, _catalog);
		ManagedSnapshot nameCollision = store.createShell(1, 20, SEED);
		nameCollision = store.updateState(nameCollision, nameCollision.state().withName(0, _environment.primary().characterName()));
		for (int stage = 0; (stage < 10) && nameCollision.state().state() != PhantomPopulationState.State.READY; stage++)
		{
			final var result = store.advanceCreation(nameCollision);
			PhantomAssertions.assertTrue(result.outcome() != CreationOutcome.INCONSISTENT, "Name collision creation became inconsistent: " + result.snapshot().state().lastFailure() + ".");
			nameCollision = result.snapshot();
		}
		PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, nameCollision.state().state(), "Bounded name collision did not resolve.");
		PhantomAssertions.assertTrue(!_environment.primary().characterName().equals(nameCollision.state().characterName()), "Name collision retained the occupied name.");

		final long nextProfileId = nextProfileId();
		final String account = "p" + Long.toString(nextProfileId, 36);
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("INSERT INTO accounts (login,password,accessLevel) VALUES (?,?,?)"))
		{
			statement.setString(1, account);
			statement.setString(2, "foreign-owner");
			statement.setInt(3, -1);
			statement.executeUpdate();
		}
		final ManagedSnapshot accountCollision = store.createShell(1, 21, SEED);
		PhantomAssertions.assertEquals(nextProfileId, accountCollision.profile().profileId(), "Collision fixture did not reserve the predicted profile ID.");
		final var result = store.advanceCreation(accountCollision);
		PhantomAssertions.assertEquals(CreationOutcome.INCONSISTENT, result.outcome(), "Foreign reserved account was not rejected.");
		PhantomAssertions.assertEquals("account.ownership_mismatch", result.snapshot().state().lastFailure(), "Account collision failure key mismatch.");
	}

	private void testCreationStageRestarts(PhantomTestContext context) throws Exception
	{
		ManagedSnapshot snapshot = new PhantomPopulationStore(_profiles, _catalog).createShell(1, 30, SEED);
		final Set<PhantomPopulationState.CreationStage> durableStages = new HashSet<>();
		for (int step = 0; (step < 10) && (snapshot.state().state() != PhantomPopulationState.State.READY); step++)
		{
			final PhantomPopulationStore restartedStore = new PhantomPopulationStore(_profiles, _catalog);
			snapshot = restartedStore.reload(snapshot.profile().profileId());
			final var result = restartedStore.advanceCreation(snapshot);
			PhantomAssertions.assertTrue(result.outcome() != CreationOutcome.INCONSISTENT, "Restarted creation failed at " + snapshot.state().creationStage() + ": " + result.snapshot().state().lastFailure() + ".");
			snapshot = result.snapshot();
			durableStages.add(snapshot.state().creationStage());
			PhantomAssertions.assertEquals(1L, count("SELECT COUNT(*) FROM phantom_profiles WHERE profile_id=?", snapshot.profile().profileId()), "Restart duplicated the profile.");
			PhantomAssertions.assertTrue(count("SELECT COUNT(*) FROM accounts WHERE login=?", snapshot.state().reservedAccount()) <= 1, "Restart duplicated the reserved account.");
			PhantomAssertions.assertTrue(count("SELECT COUNT(*) FROM characters WHERE account_name=?", snapshot.state().reservedAccount()) <= 1, "Restart duplicated the character.");
		}
		PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, snapshot.state().state(), "Restarted saga did not reach READY.");
		PhantomAssertions.assertTrue(durableStages.containsAll(Set.of(PhantomPopulationState.CreationStage.ACCOUNT_VERIFIED, PhantomPopulationState.CreationStage.CHARACTER_CREATED, PhantomPopulationState.CreationStage.VERIFIED, PhantomPopulationState.CreationStage.LINKED)), "Restart test did not cross every durable creation boundary.");
		assertDurableCharacter(snapshot);
	}

	private void testTransportNeutralInitialization(PhantomTestContext context) throws Exception
	{
		final PhantomPopulationStore store = new PhantomPopulationStore(_profiles, _catalog);
		final ManagedSnapshot population = advanceToCharacterPresent(store, 40);
		final CountingOutboundSession populationOutput = new CountingOutboundSession(false);
		initializeWithOutbound(population, PlayerCreationInitializer.Mode.POPULATION, populationOutput);
		PhantomAssertions.assertEquals(0, populationOutput.count(), "POPULATION initialization invoked outbound packets: " + populationOutput.packetTypes() + ".");

		final ManagedSnapshot client = advanceToCharacterPresent(store, 41);
		final CountingOutboundSession clientOutput = new CountingOutboundSession(false);
		initializeWithOutbound(client, PlayerCreationInitializer.Mode.CLIENT, clientOutput);
		PhantomAssertions.assertTrue(clientOutput.count() > 0, "CLIENT initialization no longer preserves shortcut packet delivery.");
		context.record("population.transportPackets", populationOutput.count());
		context.record("population.clientPackets", clientOutput.count());
	}

	private void testAuthorityAndWriterFaults(PhantomTestContext context) throws Exception
	{
		final PhantomPopulationStore store = new PhantomPopulationStore(_profiles, _catalog);
		final ManagedSnapshot timeZoneShell = store.createShell(1, 50, SEED);
		try
		{
			new PhantomPopulationStore(_profiles, _catalog, ZoneId.of("Europe/Kyiv")).reload(timeZoneShell.profile().profileId());
			throw new AssertionError("Time-zone authority drift was accepted.");
		}
		catch (PopulationAuthorityException e)
		{
			PhantomAssertions.assertEquals(AuthorityFailure.CONTRACT_DRIFT, e.failure(), "Time-zone authority drift returned the wrong typed result.");
		}

		final ManagedSnapshot catalogShell = store.createShell(1, 51, SEED);
		final ManagedSnapshot catalogDrift = store.updateState(catalogShell, withCatalogHash(catalogShell.state(), "f".repeat(64)));
		try
		{
			store.reload(catalogDrift.profile().profileId());
			throw new AssertionError("Catalog authority drift was accepted.");
		}
		catch (PopulationAuthorityException e)
		{
			PhantomAssertions.assertEquals(AuthorityFailure.CATALOG_DRIFT, e.failure(), "Catalog drift returned the wrong typed result.");
		}

		final ManagedProfile legacy = _profiles.createWithComponent(PhantomPopulationState.COMPONENT_TYPE, 1, encodeLegacyV1(sampleState()));
		try
		{
			try
			{
				store.reload(legacy.profile().profileId());
				throw new AssertionError("Managed schema-v1 authority was accepted.");
			}
			catch (PopulationAuthorityException e)
			{
				PhantomAssertions.assertEquals(AuthorityFailure.LEGACY_AUTHORITY_V1, e.failure(), "Legacy authority returned the wrong typed result.");
			}
		}
		finally
		{
			_profiles.delete(legacy.profile().profileId(), legacy.profile().rowVersion());
		}

		final Set<FaultEvent> injected = new HashSet<>();
		final AtomicReference<ManagedSnapshot> current = new AtomicReference<>();
		final PhantomPopulationStore faulting = new PhantomPopulationStore(_profiles, _catalog, ZoneOffset.UTC, (point, profileId, writerOrdinal) ->
		{
			assertFaultAutosaveBoundary(current.get());
			if (injected.add(new FaultEvent(point, writerOrdinal)))
			{
				throw new FaultInjectedException("population.test_fault");
			}
		});
		ManagedSnapshot snapshot = faulting.createShell(1, 52, SEED);
		for (int step = 0; (step < 64) && (snapshot.state().state() != PhantomPopulationState.State.READY) && (snapshot.state().state() != PhantomPopulationState.State.INCONSISTENT); step++)
		{
			current.set(snapshot);
			snapshot = faulting.advanceCreation(snapshot).snapshot();
		}
		PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, snapshot.state().state(), "Durable-writer fault sequence did not become READY: " + snapshot.state().lastFailure() + ".");
		final var authority = store.validateAuthority(timeZoneShell);
		PhantomAssertions.assertTrue((authority.adena() == 0) || injected.stream().anyMatch(event -> event.point() == FaultPoint.ADENA), "Applicable Adena durable-writer boundary was not faulted.");
		PhantomAssertions.assertTrue(injected.stream().anyMatch(event -> event.point() == FaultPoint.INITIAL_ITEM), "Initial-item durable-writer boundaries were not faulted.");
		PhantomAssertions.assertTrue(injected.stream().anyMatch(event -> event.point() == FaultPoint.SKILLS), "Skill durable-writer boundary was not faulted.");
		PhantomAssertions.assertTrue(injected.stream().anyMatch(event -> event.point() == FaultPoint.SHORTCUTS), "Shortcut durable-writer boundaries were not faulted.");
		PhantomAssertions.assertTrue(injected.stream().anyMatch(event -> event.point() == FaultPoint.CHARACTER_STORE), "Character-store boundary was not faulted.");
		PhantomAssertions.assertTrue(injected.stream().anyMatch(event -> event.point() == FaultPoint.FRESH_VERIFICATION), "Fresh-verification boundary was not faulted.");
		PhantomAssertions.assertTrue(injected.stream().anyMatch(event -> event.point() == FaultPoint.PROFILE_LINK), "Profile-link boundary was not faulted.");
		PhantomAssertions.assertTrue(injected.stream().anyMatch(event -> event.point() == FaultPoint.READY_COMPONENT_UPDATE), "READY component boundary was not faulted.");
		PhantomAssertions.assertTrue(authority.initialPlan().macros().isEmpty() || injected.stream().anyMatch(event -> event.point() == FaultPoint.MACROS), "Applicable macro durable-writer boundaries were not faulted.");
		final PhantomPopulationStore restarted = new PhantomPopulationStore(_profiles, _catalog);
		snapshot = restarted.reload(snapshot.profile().profileId());
		final String before = projectionFingerprint(snapshot.state().actualCharacterObjectId());
		PhantomAssertions.assertEquals(CreationOutcome.READY, restarted.advanceCreation(snapshot).outcome(), "Recovered READY state was not idempotent.");
		PhantomAssertions.assertEquals(before, projectionFingerprint(snapshot.state().actualCharacterObjectId()), "Recovered writer faults duplicated or changed canonical rows.");
		context.record("population.faultBoundaries", injected.size());
	}

	private void testProjectionControls(PhantomTestContext context) throws Exception
	{
		int ordinal = 100;
		ManagedSnapshot snapshot = canonicalVerified(new PhantomPopulationStore(_profiles, _catalog), ordinal++);
		execute("INSERT INTO items (owner_id,item_id,count,loc,loc_data,enchant_level,object_id,custom_type1,custom_type2,mana_left,time) VALUES (?,?,1,'INVENTORY',0,0,?,0,0,-1,-1)", snapshot.state().actualCharacterObjectId(), 4037, nextItemObjectId());
		assertProjectionInconsistent(snapshot, "An unexpected item was accepted.");

		snapshot = canonicalVerified(new PhantomPopulationStore(_profiles, _catalog), ordinal++);
		execute("INSERT INTO character_skills (charId,skill_id,skill_level,class_index) VALUES (?,?,1,0)", snapshot.state().actualCharacterObjectId(), 50000);
		assertProjectionInconsistent(snapshot, "An unexpected skill was accepted.");

		snapshot = canonicalVerified(new PhantomPopulationStore(_profiles, _catalog), ordinal++);
		execute("INSERT INTO character_shortcuts (charId,slot,page,type,shortcut_id,level,class_index) VALUES (?,99,9,0,1,'1',0)", snapshot.state().actualCharacterObjectId());
		assertProjectionInconsistent(snapshot, "An unexpected shortcut was accepted.");

		snapshot = canonicalVerified(new PhantomPopulationStore(_profiles, _catalog), ordinal++);
		final int deleted = execute("DELETE FROM character_shortcuts WHERE charId=? ORDER BY page,slot LIMIT 1", snapshot.state().actualCharacterObjectId());
		PhantomAssertions.assertEquals(1, deleted, "Missing-shortcut fixture removed no row.");
		final PhantomPopulationStore subsetStore = new PhantomPopulationStore(_profiles, _catalog);
		snapshot = completeCreation(subsetStore, snapshot);
		PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, snapshot.state().state(), "Missing expected shortcut subset did not repair to READY.");

		snapshot = canonicalVerified(new PhantomPopulationStore(_profiles, _catalog), ordinal++);
		final int changed = execute("UPDATE items SET loc=IF(loc='PAPERDOLL','INVENTORY','PAPERDOLL') WHERE owner_id=? ORDER BY object_id LIMIT 1", snapshot.state().actualCharacterObjectId());
		PhantomAssertions.assertEquals(1, changed, "Equipped-conflict fixture changed no item.");
		assertProjectionInconsistent(snapshot, "A conflicting equipped flag was accepted.");

		snapshot = canonicalVerified(new PhantomPopulationStore(_profiles, _catalog), ordinal++);
		final String before = projectionFingerprint(snapshot.state().actualCharacterObjectId());
		final ManagedSnapshot ready = completeCreation(new PhantomPopulationStore(_profiles, _catalog), snapshot);
		PhantomAssertions.assertEquals(before, projectionFingerprint(ready.state().actualCharacterObjectId()), "Read-only verification changed canonical character rows.");
		context.record("population.projectionNegativeControls", 5);
	}

	private void testReconciliation(PhantomTestContext context) throws Exception
	{
		final RunningManager fixture = startManager(0, 0, false);
		try
		{
			PhantomAssertions.assertEquals(0, fixture.manager.snapshot().managed(), "Target-zero startup created a shell.");
			fixture.manager.reconcileTarget(3, 0);
			await(() -> fixture.manager.snapshot().ready() == 3, 30_000, "Target three did not become READY.");
			final Set<Long> original = readyIds(fixture.manager);
			fixture.manager.reconcileTarget(1, 0);
			await(() -> fixture.manager.snapshot().retired() == 2, 15_000, "Target reduction did not retire highest IDs.");
			fixture.manager.reconcileTarget(3, 0);
			await(() -> fixture.manager.snapshot().ready() == 3, 15_000, "Retired profiles did not return.");
			PhantomAssertions.assertEquals(original, readyIds(fixture.manager), "Return created replacement profiles instead of reusing retired identities.");
			PhantomAssertions.assertTrue(fixture.manager.snapshot().peakCreationClaims() <= 2, "Creation in-flight limit was exceeded.");
		}
		finally
		{
			fixture.shutdown();
		}
	}

	private void testOwnershipRetries(PhantomTestContext context)
	{
		final MemoryStore store = new MemoryStore(_catalog.hash());
		store.seedReady(1, 1);
		store.resetWrites();
		final Ownership ownership = new Ownership();
		ownership.throwNextRegister();
		ownership.registerOutcomes(RegistrationStatus.NOT_RUNNING, RegistrationStatus.CAPACITY_REACHED, RegistrationStatus.REGISTERED);
		ownership.attachOutcomes(AttachResult.NOT_RUNNING, AttachResult.CANCELLED_BY_STOP, AttachResult.PERSISTENCE_FAILED, AttachResult.CAPACITY_REJECTED, AttachResult.ATTACHED);
		ownership.submitOutcomes(SignalStatus.NOT_RUNNING, SignalStatus.BACKPRESSURE, SignalStatus.NOT_REGISTERED, SignalStatus.COALESCED);
		final PhantomPopulationManager manager = syntheticManager(store, ownership, 1, 1, 8);
		PhantomAssertions.assertTrue(manager.start(), "Retry fixture manager did not start.");
		pulseUntil(manager, () -> ownership.registered(1) && (manager.snapshot().retryActions() == 0), 10_000, "Transient ownership statuses did not recover.");
		PhantomAssertions.assertEquals(1, store.size(), "Transient ownership retry created a replacement shell.");
		PhantomAssertions.assertEquals(PhantomPopulationState.State.READY, manager.find(1).orElseThrow().state().state(), "Transient ownership retry changed durable READY state.");

		ownership.withdrawOutcomes(SignalStatus.BACKPRESSURE, SignalStatus.ACCEPTED);
		ownership.unregisterOutcomes(UnregisterStatus.PENDING, UnregisterStatus.BACKPRESSURE, UnregisterStatus.NOT_RUNNING, UnregisterStatus.UNREGISTERED);
		ownership.detachOutcomes(DetachResult.PENDING, DetachResult.DETACHED);
		manager.reconcileTarget(0, 0);
		pulseUntil(manager, () -> manager.snapshot().retired() == 1, 10_000, "RETIRE_REQUESTED retries did not reach RETIRED.");
		PhantomAssertions.assertTrue(!ownership.registered(1), "Retired profile remained registered.");

		ownership.registerOutcomes(RegistrationStatus.CAPACITY_REACHED, RegistrationStatus.ALREADY_REGISTERED);
		ownership.attachOutcomes(AttachResult.CAPACITY_REJECTED, AttachResult.ALREADY_ATTACHED);
		ownership.submitOutcomes(SignalStatus.BACKPRESSURE, SignalStatus.STALE);
		manager.reconcileTarget(1, 1);
		pulseUntil(manager, () -> manager.snapshot().ready() == 1, 10_000, "RETIRED return retries did not reach READY.");
		PhantomAssertions.assertEquals(1, store.size(), "RETIRED return created a replacement shell.");
		final long sequenceBefore = ownership.lastSequence(1);
		for (int pulse = 0; pulse < 8; pulse++)
		{
			manager.onPulse();
		}
		PhantomAssertions.assertTrue(ownership.lastSequence(1) >= sequenceBefore, "Ownership signal sequence moved backwards.");
		stopSynthetic(manager);

		assertPermanentOwnershipFailure(RegistrationStatus.INVALID_PROFILE_ID, null, null, "Invalid registration did not fail closed.");
		assertPermanentOwnershipFailure(null, AttachResult.PROFILE_NOT_FOUND, null, "Missing decision profile did not fail closed.");
		assertPermanentOwnershipFailure(null, AttachResult.INVALID_PROFILE_ID, null, "Invalid decision profile did not fail closed.");
		assertPermanentOwnershipFailure(null, null, SignalStatus.REJECTED, "Rejected ownership signal did not fail closed.");
		context.record("population.retryOwnershipCalls", ownership.calls());
	}

	private void testEveryStageRetirementReturn(PhantomTestContext context)
	{
		long profileId = 1;
		for (PhantomPopulationState.CreationStage stage : PhantomPopulationState.CreationStage.values())
		{
			final long currentProfileId = profileId;
			final MemoryStore store = new MemoryStore(_catalog.hash());
			final PhantomPopulationState.State initialState = stateForStage(stage);
			final ManagedSnapshot initial = store.seed(currentProfileId, initialState, stage, (int) currentProfileId);
			final Ownership ownership = new Ownership();
			final PhantomPopulationManager manager = syntheticManager(store, ownership, 0, 0, 8);
			PhantomAssertions.assertTrue(manager.start(), "Stage retirement fixture did not start: " + stage + ".");
			pulseUntil(manager, () -> manager.snapshot().retired() == 1, 1000, "Target zero did not retire stage " + stage + ".");
			final ManagedSnapshot retired = manager.find(currentProfileId).orElseThrow();
			PhantomAssertions.assertEquals(stage, retired.state().creationStage(), "Retirement lost creation stage " + stage + ".");
			PhantomAssertions.assertEquals(initial.state().expectedCharacterObjectId(), retired.state().expectedCharacterObjectId(), "Retirement changed expected object ID at " + stage + ".");
			PhantomAssertions.assertEquals(initial.state().actualCharacterObjectId(), retired.state().actualCharacterObjectId(), "Retirement changed actual object ID at " + stage + ".");

			manager.reconcileTarget(1, 0);
			final PhantomPopulationState.State expectedReturn = returnedState(stage);
			pulseUntil(manager, () -> manager.find(currentProfileId).orElseThrow().state().state() == expectedReturn, 1000, "Return stage mapping failed for " + stage + ".");
			PhantomAssertions.assertEquals(1, store.size(), "Return created a replacement identity at " + stage + ".");
			stopSynthetic(manager);
			profileId++;
		}
		context.record("population.retirementStages", PhantomPopulationState.CreationStage.values().length);
	}

	private void testLifecycle(PhantomTestContext context) throws Exception
	{
		final RunningManager fixture = startManager(0, 0, false);
		try
		{
			PhantomAssertions.assertEquals(0, fixture.manager.snapshot().managed(), "Inert target created managed population.");
			PhantomAssertions.assertEquals(1, fixture.scheduler.snapshot().scheduledTaskCount(), "Population introduced a second scheduled task.");
			for (long profileId = 900_001; profileId <= 900_016; profileId++)
			{
				PhantomAssertions.assertEquals(PhantomScheduler.RegistrationStatus.REGISTERED, fixture.scheduler.register(profileId).status(), "Backpressure fixture could not fill scheduler capacity.");
			}
			fixture.manager.reconcileTarget(1, 0);
			PhantomAssertions.assertEquals(0, fixture.manager.snapshot().managed(), "Scheduler backpressure created an extra shell.");
			for (long profileId = 900_001; profileId <= 900_016; profileId++)
			{
				fixture.scheduler.unregister(profileId);
			}
		}
		finally
		{
			fixture.shutdown();
		}
		PhantomAssertions.assertEquals(PhantomPopulationManager.LifecycleState.STOPPED, fixture.manager.snapshot().state(), "Population lifecycle did not stop.");
		PhantomAssertions.assertEquals(0L, fixture.manager.snapshot().controlClaims(), "Population control claims leaked.");
		PhantomAssertions.assertEquals(0L, fixture.manager.snapshot().creationClaims(), "Population creation claims leaked.");
		PhantomAssertions.assertEquals(0L, fixture.manager.snapshot().persistenceClaims(), "Population persistence claims leaked.");
	}

	private void testStaticParity(PhantomTestContext context) throws Exception
	{
		final String create = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/network/clientpackets/CharacterCreate.java"));
		final String initializer = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/model/actor/PlayerCreationInitializer.java"));
		final String population = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationStore.java"));
		PhantomAssertions.assertTrue(create.contains("PlayerCreationInitializer.initialize(newChar, Mode.CLIENT)"), "CharacterCreate does not delegate canonical initialization.");
		PhantomAssertions.assertTrue(!create.contains("InitialEquipmentData.getInstance()") && !create.contains("SkillTreeData.getInstance()") && !create.contains("InitialShortcutData.getInstance()"), "CharacterCreate retains a duplicate initialization loop.");
		PhantomAssertions.assertTrue(initializer.contains("POPULATION") && initializer.contains("Location creationLocation") && population.contains("Mode.POPULATION"), "Population mode does not use the shared initializer.");
		PhantomAssertions.assertTrue(!population.contains("GameClient") && !population.contains("CharacterCreate") && !population.contains("OnPlayerCreate") && !population.contains("sendPacket"), "Population creation references a forbidden client or packet path.");

		final PhantomPlayersConfig.Settings production = PhantomPlayersConfig.read(context.moduleRoot().resolve("dist/game/config/Custom/PhantomPlayers.ini"));
		PhantomAssertions.assertEquals(0, production.populationTarget(), "Production population target is not zero.");
		PhantomAssertions.assertEquals(0, production.populationActiveTarget(), "Production ACTIVE target is not zero.");
		final Path legacy = Files.createTempFile("phantom-population-legacy-", ".ini");
		final Path invalid = Files.createTempFile("phantom-population-invalid-", ".ini");
		try
		{
			Files.writeString(legacy, "EnablePhantomSystem=True\nEnablePhantomDiagnostics=False\nMaxMaterializedPhantoms=4\nMaxScheduledPhantomProfiles=16\nPhantomSchedulerPulseMillis=100\nPhantomSchedulerProfilesPerPulse=8\n", StandardCharsets.UTF_8);
			final PhantomPlayersConfig.Settings legacySettings = PhantomPlayersConfig.read(legacy);
			PhantomAssertions.assertTrue(legacySettings.enabled(), "Legacy enabled config without Goal 016 keys did not remain compatible.");
			PhantomAssertions.assertEquals(0, legacySettings.populationTarget(), "Legacy config did not default target to zero.");
			PhantomAssertions.assertEquals(0, legacySettings.populationActiveTarget(), "Legacy config did not default ACTIVE target to zero.");
			Files.writeString(invalid, Files.readString(legacy, StandardCharsets.UTF_8) + "PhantomPopulationTarget=1\nPhantomPopulationActiveTarget=2\n", StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(!PhantomPlayersConfig.read(invalid).enabled(), "Invalid ACTIVE target did not fail closed.");
		}
		finally
		{
			Files.deleteIfExists(legacy);
			Files.deleteIfExists(invalid);
		}
	}

	private void testShutdownPublicationAndSnapshot(PhantomTestContext context) throws Exception
	{
		final MemoryStore store = new MemoryStore(_catalog.hash());
		final Ownership ownership = new Ownership();
		final PhantomPopulationManager manager = syntheticManager(store, ownership, 0, 0, 8);
		PhantomAssertions.assertTrue(manager.start(), "Publication-barrier manager did not start.");
		final CountDownLatch committed = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		store.afterCreate(() ->
		{
			committed.countDown();
			try
			{
				if (!release.await(10, TimeUnit.SECONDS))
				{
					throw new IllegalStateException("Synthetic publication barrier timed out.");
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Synthetic publication barrier was interrupted.", e);
			}
		});
		final AtomicReference<Throwable> backgroundFailure = new AtomicReference<>();
		final Thread reconcile = new Thread(() ->
		{
			try
			{
				manager.reconcileTarget(1, 0);
			}
			catch (Throwable throwable)
			{
				backgroundFailure.set(throwable);
			}
		}, "population-publication-barrier-test");
		reconcile.start();
		PhantomAssertions.assertTrue(committed.await(10, TimeUnit.SECONDS), "Synthetic DB shell did not reach committed barrier.");
		manager.beginStop();
		release.countDown();
		reconcile.join(10_000);
		PhantomAssertions.assertTrue(!reconcile.isAlive(), "Publication-barrier reconcile thread did not finish.");
		PhantomAssertions.assertTrue(backgroundFailure.get() == null, "Publication-barrier reconcile failed: " + backgroundFailure.get());
		PhantomAssertions.assertTrue(manager.finishStop(), "Publication-barrier manager did not finish stop.");
		PhantomAssertions.assertEquals(0, manager.snapshot().managed(), "Committed shell was published after STOPPING.");
		PhantomAssertions.assertEquals(0L, manager.snapshot().persistenceClaims(), "Publication persistence claim leaked.");
		PhantomAssertions.assertEquals(1, store.size(), "Committed shell was lost or duplicated.");

		store.afterCreate(() ->
		{
		});
		final PhantomPopulationManager restarted = syntheticManager(store, new Ownership(), 1, 0, 8);
		PhantomAssertions.assertTrue(restarted.start(), "Publication-barrier restart did not start.");
		PhantomAssertions.assertEquals(1, restarted.snapshot().managed(), "Restart did not restore exactly one committed shell.");
		PhantomAssertions.assertEquals(1, store.size(), "Restart duplicated the committed shell.");
		stopSynthetic(restarted);

		final PhantomSystem inert = new PhantomSystem(new PhantomPlayersConfig.Settings(false, false));
		PhantomAssertions.assertEquals(PhantomPopulationManager.LifecycleState.STOPPED, inert.snapshot().population().state(), "Inert PhantomSystem snapshot did not expose inactive population.");
		PhantomAssertions.assertEquals(0, inert.snapshot().population().managed(), "Inert PhantomSystem snapshot created managed population.");
		PhantomAssertions.assertEquals(PhantomPopulationManager.LifecycleState.STOPPED, PhantomSystem.configuredShutdownSnapshot().population().state(), "Unconfigured shutdown evidence did not expose inactive population.");
		context.record("population.publicationRestoredShells", store.size());
	}

	private void testServerIntegration(PhantomTestContext context) throws Exception
	{
		RunningManager fixture = startManager(1, 1, true);
		RunningManager restarted = null;
		RunningManager retirementRecovery = null;
		boolean fixtureStopped = false;
		boolean restartedStopped = false;
		try
		{
			await(() -> fixture.manager.snapshot().ready() == 1, 30_000, "Real integration character did not become READY.");
			final long profileId = readyIds(fixture.manager).iterator().next();
			final ManagedSnapshot snapshot = fixture.manager.find(profileId).orElseThrow();
			final ScheduleInstants instants = activeInstants(snapshot.state().scheduleTemplate());
			fixture.shutdown();
			fixtureStopped = true;

			restarted = startManager(1, 1, true);
			final RunningManager restartedFixture = restarted;
			await(() -> restartedFixture.manager.snapshot().ready() == 1, 15_000, "READY population did not survive manager restart.");
			PhantomAssertions.assertTrue(readyIds(restartedFixture.manager).contains(profileId), "Manager restart replaced the durable population identity.");
			restartedFixture.clock.set(instants.firstActive());
			await(() -> restartedFixture.materialization.find(profileId).isPresent(), 15_000, "ACTIVE schedule did not materialize the real created Player.");
			PhantomAssertions.assertTrue(restartedFixture.materialization.find(profileId).orElseThrow().playerRetained(), "Materialized population entry does not retain a real Player.");
			restartedFixture.clock.set(instants.sleeping());
			await(() -> restartedFixture.materialization.find(profileId).isEmpty(), 15_000, "SLEEPING schedule did not dematerialize the real Player.");
			restartedFixture.clock.set(instants.secondActive());
			await(() -> restartedFixture.materialization.find(profileId).isPresent(), 15_000, "Schedule wakeup did not rematerialize the real Player.");

			final PhantomPopulationStore store = new PhantomPopulationStore(_profiles, _catalog);
			final ManagedSnapshot ready = restartedFixture.manager.find(profileId).orElseThrow();
			store.updateState(ready, ready.state().retireRequested());
			restartedFixture.shutdown();
			restartedStopped = true;

			retirementRecovery = startManager(0, 0, false);
			final RunningManager retirementFixture = retirementRecovery;
			await(() -> retirementFixture.manager.snapshot().retired() == 1, 15_000, "RETIRE_REQUESTED did not recover after restart.");
			PhantomAssertions.assertTrue(retirementFixture.scheduler.find(profileId).isEmpty(), "Restarted retirement left the profile registered.");
		}
		finally
		{
			if (retirementRecovery != null)
			{
				retirementRecovery.shutdown();
			}
			if ((restarted != null) && !restartedStopped)
			{
				restarted.shutdown();
			}
			if (!fixtureStopped)
			{
				fixture.shutdown();
			}
		}
	}

	private ManagedSnapshot advanceToCharacterPresent(PhantomPopulationStore store, long ordinal)
	{
		ManagedSnapshot snapshot = store.createShell(1, ordinal, SEED);
		while (snapshot.state().state() != PhantomPopulationState.State.CHARACTER_PRESENT)
		{
			final var result = store.advanceCreation(snapshot);
			PhantomAssertions.assertTrue(result.outcome() != CreationOutcome.INCONSISTENT, "Transport fixture could not reach CHARACTER_PRESENT.");
			snapshot = result.snapshot();
		}
		return snapshot;
	}

	private void initializeWithOutbound(ManagedSnapshot snapshot, PlayerCreationInitializer.Mode mode, CountingOutboundSession output)
	{
		final int objectId = snapshot.state().expectedCharacterObjectId();
		if (mode == PlayerCreationInitializer.Mode.POPULATION)
		{
			final var fullPlan = PlayerCreationInitializer.resolvePlan(org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(snapshot.state().classId()), mode, new org.l2jmobius.gameserver.model.Location(snapshot.state().creationX(), snapshot.state().creationY(), snapshot.state().creationZ()));
			PlayerCreationInitializer.preparePopulationCharacterRow(objectId, fullPlan);
		}
		try (PlayerAutoSaveTaskManager.PopulationLoadSuppression ignored = PlayerAutoSaveTaskManager.suppressPopulationLoad(objectId))
		{
			final Player player = Player.load(objectId);
			PhantomAssertions.assertTrue(player != null, "Transport fixture Player.load failed.");
			PhantomAssertions.assertTrue(!PlayerAutoSaveTaskManager.getInstance().containsObjectId(objectId), "Transport fixture entered autosave under the exact suppression guard.");
			try (Player.OutboundSessionAttachment attachment = player.attachOutboundSession(output))
			{
				if (mode == PlayerCreationInitializer.Mode.POPULATION)
				{
					final var fullPlan = PlayerCreationInitializer.resolvePlan(player.getPlayerClass(), mode, new org.l2jmobius.gameserver.model.Location(snapshot.state().creationX(), snapshot.state().creationY(), snapshot.state().creationZ()));
					final var packetFreePlan = new PlayerCreationInitializer.CreationPlan(mode, fullPlan.level(), fullPlan.sp(), fullPlan.adena(), fullPlan.creationLocation(), fullPlan.title(), fullPlan.vitalityEnabled(), fullPlan.vitalityPoints(), fullPlan.configuredStartingLevel(), fullPlan.configuredStartingSp(), fullPlan.items(), List.of(), fullPlan.shortcuts());
					PlayerCreationInitializer.initializePopulation(player, packetFreePlan, PlayerCreationInitializer.PopulationInitializationObserver.noop());
				}
				else
				{
					PlayerCreationInitializer.initialize(player, mode);
				}
			}
			finally
			{
				PlayerAutoSaveTaskManager.getInstance().remove(player);
				player.stopAllTasks();
				player.deleteMe();
			}
		}
	}

	private ManagedSnapshot completeCreation(PhantomPopulationStore store, ManagedSnapshot initial)
	{
		ManagedSnapshot snapshot = initial;
		for (int step = 0; (step < 20) && (snapshot.state().state() != PhantomPopulationState.State.READY) && (snapshot.state().state() != PhantomPopulationState.State.INCONSISTENT); step++)
		{
			snapshot = store.advanceCreation(snapshot).snapshot();
		}
		return snapshot;
	}

	private ManagedSnapshot canonicalVerified(PhantomPopulationStore store, long ordinal)
	{
		ManagedSnapshot snapshot = store.createShell(1, ordinal, SEED);
		for (int step = 0; (step < 20) && (snapshot.state().creationStage() != PhantomPopulationState.CreationStage.VERIFIED); step++)
		{
			final var result = store.advanceCreation(snapshot);
			PhantomAssertions.assertTrue(result.outcome() != CreationOutcome.INCONSISTENT, "Canonical projection fixture became inconsistent.");
			snapshot = result.snapshot();
		}
		PhantomAssertions.assertEquals(PhantomPopulationState.CreationStage.VERIFIED, snapshot.state().creationStage(), "Canonical projection fixture did not reach VERIFIED.");
		return snapshot;
	}

	private void assertProjectionInconsistent(ManagedSnapshot snapshot, String failure)
	{
		final var result = new PhantomPopulationStore(_profiles, _catalog).advanceCreation(snapshot);
		PhantomAssertions.assertEquals(CreationOutcome.INCONSISTENT, result.outcome(), failure);
		PhantomAssertions.assertEquals(PhantomPopulationState.State.INCONSISTENT, result.snapshot().state().state(), failure);
	}

	private static void assertFaultAutosaveBoundary(ManagedSnapshot snapshot)
	{
		if ((snapshot == null) || (snapshot.state().expectedCharacterObjectId() == null))
		{
			return;
		}
		PhantomAssertions.assertTrue(!PlayerAutoSaveTaskManager.getInstance().containsObjectId(snapshot.state().expectedCharacterObjectId()), "Population durable-writer boundary entered autosave.");
	}

	private PhantomPopulationManager syntheticManager(MemoryStore store, Ownership ownership, int target, int activeTarget, int budget)
	{
		return new PhantomPopulationManager(store, _catalog, null, ownership, new PhantomPopulationTestDoubles.MutableClock(Instant.parse("2026-07-27T20:00:00Z")), ZoneOffset.UTC, target, activeTarget, 128, 128, 8, budget);
	}

	private static void pulseUntil(PhantomPopulationManager manager, BooleanSupplier condition, int maximumPulses, String failure)
	{
		for (int pulse = 0; !condition.getAsBoolean() && (pulse < maximumPulses); pulse++)
		{
			manager.onPulse();
			PhantomAssertions.assertTrue(manager.snapshot().lastPulseOperations() <= 8, "Synthetic population pulse exceeded its configured budget.");
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), failure);
	}

	private static void stopSynthetic(PhantomPopulationManager manager)
	{
		manager.beginStop();
		PhantomAssertions.assertTrue(manager.finishStop(), "Synthetic population manager did not stop.");
	}

	private void assertPermanentOwnershipFailure(RegistrationStatus registration, AttachResult attachment, SignalStatus signal, String failure)
	{
		final MemoryStore store = new MemoryStore(_catalog.hash());
		store.seedReady(1, 1);
		final Ownership ownership = new Ownership();
		if (registration != null)
		{
			ownership.registerOutcomes(registration);
		}
		if (attachment != null)
		{
			ownership.attachOutcomes(attachment);
		}
		if (signal != null)
		{
			ownership.submitOutcomes(signal);
		}
		final PhantomPopulationManager manager = syntheticManager(store, ownership, 1, 1, 8);
		PhantomAssertions.assertTrue(manager.start(), "Permanent ownership failure fixture did not start.");
		pulseUntil(manager, () -> manager.snapshot().inconsistent() == 1, 64, failure);
		stopSynthetic(manager);
	}

	private static PhantomPopulationState.State stateForStage(PhantomPopulationState.CreationStage stage)
	{
		return switch (stage)
		{
			case SHELL_DURABLE -> PhantomPopulationState.State.SHELL;
			case ACCOUNT_INTENT, ACCOUNT_VERIFIED -> PhantomPopulationState.State.ACCOUNT_PREPARED;
			case CHARACTER_INTENT, CHARACTER_CREATED -> PhantomPopulationState.State.CHARACTER_PRESENT;
			case INITIALIZATION_INTENT, INITIALIZATION_STORED, VERIFIED -> PhantomPopulationState.State.INITIALIZING;
			case LINKED -> PhantomPopulationState.State.READY;
		};
	}

	private static PhantomPopulationState.State returnedState(PhantomPopulationState.CreationStage stage)
	{
		return switch (stage)
		{
			case SHELL_DURABLE -> PhantomPopulationState.State.SHELL;
			case ACCOUNT_INTENT, ACCOUNT_VERIFIED -> PhantomPopulationState.State.ACCOUNT_PREPARED;
			case CHARACTER_INTENT, CHARACTER_CREATED -> PhantomPopulationState.State.CHARACTER_PRESENT;
			case INITIALIZATION_INTENT, INITIALIZATION_STORED, VERIFIED -> PhantomPopulationState.State.INITIALIZING;
			case LINKED -> PhantomPopulationState.State.READY;
		};
	}

	private RunningManager startManager(int target, int activeTarget, boolean realMaterialization)
	{
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomDiagnosticTrace trace = new PhantomDiagnosticTrace(false, 64, 16, metrics);
		final PhantomActivityWorkSinkBridge bridge = new PhantomActivityWorkSinkBridge();
		final PhantomMaterializationService materialization;
		final PhantomActivityMaterializationPort materializationPort;
		if (realMaterialization)
		{
			materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, trace, 4);
			PhantomAssertions.assertTrue(materialization.start(), "Population test materialization service did not start.");
			materializationPort = new PhantomMaterializationServiceActivityPort(materialization);
		}
		else
		{
			materialization = null;
			materializationPort = PhantomActivityMaterializationPort.noop();
		}
		final PhantomScheduler scheduler = new PhantomScheduler(16, 20, 16, metrics, trace, materializationPort, bridge);
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
		final MutableClock clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"), ZoneOffset.UTC);
		final PhantomPopulationManager manager = new PhantomPopulationManager(new PhantomPopulationStore(_profiles, _catalog), _catalog, goals, scheduler, profileId -> (materialization != null) && materialization.find(profileId).isPresent(), clock, ZoneOffset.UTC, target, activeTarget, 16, 4, 2, 64);
		final PhantomPopulationDecision decision = new PhantomPopulationDecision(manager);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		decision.registerCandidates(candidates);
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		decision.registerHandlers(handlers);
		handlers.seal();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 16);
		engine.start();
		manager.installDecisionEngine(engine);
		PhantomAssertions.assertTrue(scheduler.installControlPort(manager), "Population control port installation failed.");
		bridge.install(engine);
		PhantomAssertions.assertTrue(scheduler.start(), "Population test scheduler did not start.");
		PhantomAssertions.assertTrue(manager.start(), "Population manager did not start.");
		return new RunningManager(manager, scheduler, engine, materialization, clock);
	}

	private static ScheduleInstants activeInstants(String scheduleTemplate)
	{
		return switch (scheduleTemplate)
		{
			case "morning" -> new ScheduleInstants(Instant.parse("2026-08-04T09:00:00Z"), Instant.parse("2026-08-04T14:00:00Z"), Instant.parse("2026-08-05T09:00:00Z"));
			case "evening" -> new ScheduleInstants(Instant.parse("2026-08-04T20:00:00Z"), Instant.parse("2026-08-05T12:00:00Z"), Instant.parse("2026-08-05T20:00:00Z"));
			case "late" -> new ScheduleInstants(Instant.parse("2026-08-07T23:00:00Z"), Instant.parse("2026-08-08T12:00:00Z"), Instant.parse("2026-08-08T23:00:00Z"));
			default -> throw new IllegalArgumentException("Unknown test schedule template: " + scheduleTemplate);
		};
	}

	private void assertDurableCharacter(ManagedSnapshot snapshot) throws Exception
	{
		final int objectId = snapshot.state().actualCharacterObjectId();
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT level,base_class,online FROM characters WHERE charId=?"))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Created population character row is absent.");
				PhantomAssertions.assertEquals(1, result.getInt("level"), "Created population character is not exact level one.");
				PhantomAssertions.assertEquals(snapshot.state().classId(), result.getInt("base_class"), "Created population class mismatch.");
				PhantomAssertions.assertEquals(0, result.getInt("online"), "Created population character remained online.");
			}
		}
		PhantomAssertions.assertEquals(1L, count("SELECT COUNT(*) FROM characters WHERE account_name=?", snapshot.state().reservedAccount()), "Reserved account owns duplicate characters.");
		PhantomAssertions.assertEquals(-1L, scalar("SELECT accessLevel FROM accounts WHERE login=?", snapshot.state().reservedAccount()), "Reserved account is accessible.");
		PhantomAssertions.assertTrue(World.getInstance().getPlayer(objectId) == null, "Created population character entered World.");
		PhantomAssertions.assertTrue(!PhantomHeadlessPlayerTestEnvironment.isAutosaveMember(objectId), "Created population character remained in autosave.");
	}

	private Set<Long> readyIds(PhantomPopulationManager manager)
	{
		final Set<Long> ids = new HashSet<>();
		for (ManagedSnapshot snapshot : _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 256).stream().map(managed -> manager.find(managed.profile().profileId()).orElse(null)).filter(java.util.Objects::nonNull).toList())
		{
			if (snapshot.state().state() == PhantomPopulationState.State.READY)
			{
				ids.add(snapshot.profile().profileId());
			}
		}
		return ids;
	}

	private void cleanupManaged() throws Exception
	{
		if (_profiles == null)
		{
			return;
		}
		final PhantomPopulationStateCodec codec = new PhantomPopulationStateCodec();
		final List<ManagedProfile> managed = new ArrayList<>();
		long cursor = 0;
		do
		{
			final List<ManagedProfile> page = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, cursor, 256);
			managed.addAll(page);
			if (page.isEmpty())
			{
				break;
			}
			cursor = page.get(page.size() - 1).profile().profileId();
			if (page.size() < 256)
			{
				break;
			}
		}
		while (true);
		for (ManagedProfile row : managed)
		{
			final PhantomPopulationState state = codec.decode(row.component().payload());
			PhantomProfile profile = row.profile();
			final Integer objectId = profile.characterObjectId() != null ? profile.characterObjectId() : state.actualCharacterObjectId();
			if (profile.characterObjectId() != null)
			{
				profile = _profiles.updateCharacterLink(profile.profileId(), profile.rowVersion(), null);
			}
			_profiles.delete(profile.profileId(), profile.rowVersion());
			if (objectId != null)
			{
				final Player world = World.getInstance().getPlayer(objectId);
				if (world != null)
				{
					_environment.cleanupLoadedPlayer(world);
				}
				GameClient.deleteCharByObjId(objectId);
			}
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
			{
				statement.setString(1, state.reservedAccount());
				statement.executeUpdate();
			}
		}
	}

	private long nextProfileId() throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phantom_profiles'");
			ResultSet result = statement.executeQuery())
		{
			PhantomAssertions.assertTrue(result.next(), "Could not inspect next profile ID.");
			return result.getLong(1);
		}
	}

	private long count(String sql, Object value) throws Exception
	{
		return scalar(sql, value);
	}

	private long scalar(String sql, Object value) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setObject(1, value);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Scalar population query returned no row.");
				return result.getLong(1);
			}
		}
	}

	private int nextItemObjectId() throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(MAX(object_id),0)+1 FROM items");
			ResultSet result = statement.executeQuery())
		{
			PhantomAssertions.assertTrue(result.next(), "Could not allocate synthetic item object ID.");
			return result.getInt(1);
		}
	}

	private int execute(String sql, Object... values) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int index = 0; index < values.length; index++)
			{
				statement.setObject(index + 1, values[index]);
			}
			return statement.executeUpdate();
		}
	}

	private String projectionFingerprint(int objectId) throws Exception
	{
		final StringBuilder rows = new StringBuilder(4096);
		appendRows(rows, "SELECT account_name,charId,char_name,level,maxHp,curHp,maxCp,curCp,maxMp,curMp,face,hairStyle,hairColor,sex,x,y,z,exp,sp,classid,base_class,title,online,vitality_points FROM characters WHERE charId=? ORDER BY charId", objectId);
		appendRows(rows, "SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? ORDER BY object_id", objectId);
		appendRows(rows, "SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId=? ORDER BY skill_id,skill_level,class_index", objectId);
		appendRows(rows, "SELECT slot,page,type,shortcut_id,level,class_index FROM character_shortcuts WHERE charId=? ORDER BY page,slot,class_index", objectId);
		appendRows(rows, "SELECT id,icon,name,descr,acronym,commands FROM character_macroses WHERE charId=? ORDER BY id", objectId);
		return rows.toString();
	}

	private void appendRows(StringBuilder rows, String sql, int objectId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				final int columns = result.getMetaData().getColumnCount();
				while (result.next())
				{
					for (int column = 1; column <= columns; column++)
					{
						rows.append(result.getString(column)).append('\u001f');
					}
					rows.append('\n');
				}
			}
		}
	}

	private static byte[] encodeLegacyV1(PhantomPopulationState state) throws Exception
	{
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
		try (DataOutputStream output = new DataOutputStream(bytes))
		{
			output.writeInt(0x50505731);
			output.writeByte(state.state().ordinal());
			output.writeLong(state.populationGeneration());
			output.writeLong(state.creationOrdinal());
			writeLegacyText(output, state.catalogHash());
			output.writeLong(state.deterministicSeed());
			output.writeByte(state.nameAttempt());
			writeLegacyText(output, state.reservedAccount());
			writeLegacyText(output, state.ownershipToken());
			writeLegacyText(output, state.characterName());
			output.writeShort(state.classId());
			output.writeBoolean(state.female());
			output.writeByte(state.face());
			output.writeByte(state.hairColor());
			output.writeByte(state.hairStyle());
			writeLegacyText(output, state.scheduleTemplate());
			output.writeShort(state.schedulePhaseMinutes());
			output.writeInt(state.homeMapRegionId());
			output.writeInt(state.creationX());
			output.writeInt(state.creationY());
			output.writeInt(state.creationZ());
			writeNullableInt(output, state.expectedCharacterObjectId());
			writeNullableInt(output, state.actualCharacterObjectId());
			output.writeByte(state.creationStage().ordinal());
			writeLegacyText(output, state.initializationHash());
			writeLegacyText(output, state.lastFailure());
		}
		return bytes.toByteArray();
	}

	private static void writeLegacyText(DataOutputStream output, String value) throws Exception
	{
		final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		output.writeShort(encoded.length);
		output.write(encoded);
	}

	private static void writeNullableInt(DataOutputStream output, Integer value) throws Exception
	{
		output.writeBoolean(value != null);
		if (value != null)
		{
			output.writeInt(value);
		}
	}

	private static PhantomPopulationState withCatalogHash(PhantomPopulationState source, String catalogHash)
	{
		return new PhantomPopulationState(source.state(), source.populationGeneration(), source.creationOrdinal(), catalogHash, source.initializationAuthorityHash(), source.deterministicSeed(), source.nameAttempt(), source.reservedAccount(), source.ownershipToken(), source.characterName(), source.classId(), source.female(), source.face(), source.hairColor(), source.hairStyle(), source.scheduleTemplate(), source.schedulePhaseMinutes(), source.homeMapRegionId(), source.creationX(), source.creationY(), source.creationZ(), source.expectedCharacterObjectId(), source.actualCharacterObjectId(), source.creationStage(), source.initializationHash(), source.lastFailure());
	}

	private void assertRejected(Path path, String message)
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomPopulationCatalog.load(path, ZoneOffset.UTC), message);
	}

	private static Path catalogPath(PhantomTestContext context)
	{
		return context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml");
	}

	private static void await(BooleanSupplier condition, long timeoutMillis, String failure) throws Exception
	{
		final long deadline = System.nanoTime() + (timeoutMillis * 1_000_000L);
		while (!condition.getAsBoolean() && (System.nanoTime() < deadline))
		{
			Thread.sleep(20);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), failure);
	}

	private static PhantomPopulationState sampleState()
	{
		return new PhantomPopulationState(PhantomPopulationState.State.SHELL, 1, 1, "0".repeat(64), "1".repeat(64), SEED, 0, "p1", "A".repeat(43), "AriDor1", 0, false, 0, 0, 0, "evening", 0, 1, -71338, 258271, -3104, null, null, PhantomPopulationState.CreationStage.SHELL_DURABLE, "", "");
	}

	public enum Mode
	{
		CATALOG(false),
		SCHEDULE(false),
		CREATION(true),
		RECONCILIATION(true),
		LIFECYCLE(true),
		SERVER_INTEGRATION(true),
		PERFORMANCE(false);

		private final boolean _requiresDatabase;

		Mode(boolean requiresDatabase)
		{
			_requiresDatabase = requiresDatabase;
		}

		boolean requiresDatabase()
		{
			return _requiresDatabase;
		}
	}

	private record ScheduleInstants(Instant firstActive, Instant sleeping, Instant secondActive)
	{
	}

	private record FaultEvent(FaultPoint point, int ordinal)
	{
	}

	private static final class CountingOutboundSession implements PlayerOutboundSession
	{
		private final boolean _throwOnSend;
		private final AtomicInteger _count = new AtomicInteger();
		private final List<String> _packetTypes = new ArrayList<>();

		private CountingOutboundSession(boolean throwOnSend)
		{
			_throwOnSend = throwOnSend;
		}

		@Override
		public SessionKind kind()
		{
			return SessionKind.HEADLESS;
		}

		@Override
		public void send(Player player, ServerPacket packet)
		{
			_count.incrementAndGet();
			_packetTypes.add(packet.getClass().getSimpleName());
			if (_throwOnSend)
			{
				throw new AssertionError("POPULATION initialization invoked packet delivery: " + packet.getClass().getName());
			}
		}

		private int count()
		{
			return _count.get();
		}

		private List<String> packetTypes()
		{
			return List.copyOf(_packetTypes);
		}
	}

	private static final class MutableClock extends Clock
	{
		private volatile Instant _instant;
		private final ZoneId _zone;

		private MutableClock(Instant instant, ZoneId zone)
		{
			_instant = instant;
			_zone = zone;
		}

		void set(Instant instant)
		{
			_instant = instant;
		}

		@Override
		public ZoneId getZone()
		{
			return _zone;
		}

		@Override
		public Clock withZone(ZoneId zone)
		{
			return new MutableClock(_instant, zone);
		}

		@Override
		public Instant instant()
		{
			return _instant;
		}
	}

	private record RunningManager(PhantomPopulationManager manager, PhantomScheduler scheduler, PhantomDecisionEngine engine, PhantomMaterializationService materialization, MutableClock clock)
	{
		void shutdown() throws Exception
		{
			manager.beginStop();
			scheduler.beginStop();
			engine.beginStop();
			final long deadline = System.nanoTime() + 10_000_000_000L;
			while (!manager.finishStop() && (System.nanoTime() < deadline))
			{
				Thread.sleep(10);
			}
			while (!scheduler.finishStop() && (System.nanoTime() < deadline))
			{
				Thread.sleep(10);
			}
			while (!engine.finishStop() && (System.nanoTime() < deadline))
			{
				Thread.sleep(10);
			}
			if (materialization != null)
			{
				materialization.shutdown();
			}
		}
	}
}

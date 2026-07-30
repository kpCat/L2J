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
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkSinkBridge;
import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationServiceActivityPort;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
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
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ManagedProfile;

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
			}
			case RECONCILIATION -> registry.add("01-target-zero-three-one-three-return-before-create", this::testReconciliation);
			case LIFECYCLE ->
			{
				registry.add("01-target-zero-inert-and-bounded-lifecycle", this::testLifecycle);
				registry.add("02-config-and-static-initializer-parity", this::testStaticParity);
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
		return new PhantomPopulationState(PhantomPopulationState.State.SHELL, 1, 1, "0".repeat(64), SEED, 0, "p1", "A".repeat(43), "AriDor1", 0, false, 0, 0, 0, "evening", 0, 1, -71338, 258271, -3104, null, null, PhantomPopulationState.CreationStage.SHELL_DURABLE, "", "");
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

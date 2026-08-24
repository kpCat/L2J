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
package org.l2jmobius.gameserver.phantoms;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.RegistrationStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SchedulerSnapshot;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles.Ownership;
import org.l2jmobius.tests.phantoms.PhantomTestConfigurationException;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap.BootstrapResult;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/**
 * Short environment scale checkpoint. Admin credentials are consumed only by
 * the closed read-only status probe and are never retained in measurements.
 */
public final class PhantomScaleEnvironmentGoal029Checkpoint2Suite implements PhantomTestSuite
{
	private static final long SEED = 29_002_902L;
	private static final int SCALE = 10_000;
	private static final int PAGE_SIZE = 256;
	private static final int POPULATION_BUDGET = 64;
	private static final int SCHEDULER_BUDGET = 128;
	private static final int MAXIMUM_MATERIALIZED = 32;
	private static final long MIB = 1024L * 1024L;
	private static final long POPULATION_LOADED_BUDGET = 256L * MIB;
	private static final long POPULATION_RECOVERED_BUDGET = 64L * MIB;
	private static final long SCHEDULER_TRANSIENT_BUDGET = 128L * MIB;
	private static final long SCHEDULER_RECOVERY_RATCHET = 32L * MIB;
	private static final long SCHEDULER_FINAL_BUDGET = 64L * MIB;
	private static final long SIGNAL_TTL_MILLIS = 60_000L;
	private static final String SIGNAL_SOURCE = "goal029cp2.pressure";
	private static final String BLOCKED_ADMIN_ENV = "BLOCKED_029CP2_ADMIN_STATUS_ENV_REQUIRED";
	private static final String BLOCKED_DB_ISOLATION = "BLOCKED_029CP2_DB_INSTANCE_NOT_ISOLATED";
	private static final String BLOCKED_DB_EVIDENCE = "BLOCKED_029CP2_DB_RATE_EVIDENCE_UNAVAILABLE";

	private final List<CreatedProfile> _createdProfiles = new ArrayList<>(SCALE);
	private AdminStatusProbe _admin;
	private PhantomProfileRepository _profiles;
	private PhantomPopulationCatalog _catalog;
	private PhantomPopulationStore _store;
	private PhantomPopulationManager _manager;
	private Ownership _ownership;
	private PhantomScheduler _scheduler;
	private JvmSnapshot _populationBaseline;
	private JvmSnapshot _populationLoaded;
	private int _populationQueuePeak;
	private int _populationProductivePulses;
	private int _populationTotalPulses;

	@Override
	public String id()
	{
		return "scale-environment-goal029cp2";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal029 CP2 requires the exact deterministic seed.");
		final Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		final Path expectedWorkingDirectory = context.moduleRoot().resolve("dist/game").normalize();
		PhantomAssertions.assertEquals(expectedWorkingDirectory, workingDirectory, "Goal029 CP2 JVM must run from dist/game.");

		ConfigLoader.init();
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		final BootstrapResult bootstrap = PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_USER, bootstrap.settings().login(), "CP2 did not use the dedicated test DB user.");
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.MAX_TEST_POOL_SIZE, bootstrap.settings().maximumConnections(), "CP2 test DB pool is not the exact guarded maximum.");

		SkillTreeData.getInstance();
		InitialEquipmentData.getInstance();
		InitialShortcutData.getInstance();
		PlayerTemplateData.getInstance();
		MapRegionData.getInstance();
		_catalog = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		_profiles = PhantomProfileRepository.open();
		_store = new PhantomPopulationStore(_profiles, _catalog, ZoneOffset.UTC);
		_admin = AdminStatusProbe.open();
		_admin.requireProductionDatabaseIdle();
		PhantomAssertions.assertEquals(List.of(), _store.loadManagedAfter(0, 1), "Guarded test DB is not fresh: managed population residue exists.");

		final DbStatus seedBefore = _admin.status();
		final long seedStarted = System.nanoTime();
		long previousProfileId = 0;
		for (int ordinal = 1; ordinal <= SCALE; ordinal++)
		{
			final ManagedSnapshot created = _store.createShell(1, ordinal, SEED);
			PhantomAssertions.assertEquals(PhantomPopulationState.State.SHELL, created.state().state(), "Seed did not create a canonical SHELL.");
			PhantomAssertions.assertEquals(null, created.profile().characterObjectId(), "Seed linked a character to a SHELL profile.");
			PhantomAssertions.assertEquals(null, created.state().expectedCharacterObjectId(), "Seed prepared a character identity.");
			PhantomAssertions.assertEquals(null, created.state().actualCharacterObjectId(), "Seed created a character identity.");
			if (previousProfileId != 0)
			{
				PhantomAssertions.assertEquals(previousProfileId + 1, created.profile().profileId(), "CP2 profile IDs are not a contiguous exact-owned range.");
			}
			previousProfileId = created.profile().profileId();
			_createdProfiles.add(new CreatedProfile(created.profile().profileId(), created.profile().rowVersion(), created.state().reservedAccount()));
		}
		final long seedMillis = (System.nanoTime() - seedStarted) / 1_000_000L;
		final DbStatus seedDelta = _admin.status().minus(seedBefore);
		PhantomAssertions.assertEquals(SCALE, countManagedProfiles(), "Seed did not produce exactly 10000 durable managed profiles.");
		assertNoFixtureAccountsOrCharacters();

		context.record("environment.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("environment.schemaAggregateSha256", bootstrap.schemaSnapshot().aggregateSha256());
		context.record("environment.adminProbe", "jdbc:mysql local:3308 no-schema read-only-status/processlist");
		context.record("environment.jvmMaxHeapBytes", Runtime.getRuntime().maxMemory());
		context.record("population.seedCount", _createdProfiles.size());
		context.record("population.seedMillisDiagnostic", seedMillis);
		context.record("population.seedDbDelta", seedDelta.compact());
	}
	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			stopManager();
			stopScheduler();
			if (DatabaseFactory.isInitialized() && !_createdProfiles.isEmpty())
			{
				assertNoFixtureAccountsOrCharacters();
				for (int index = _createdProfiles.size() - 1; index >= 0; index--)
				{
					final CreatedProfile created = _createdProfiles.get(index);
					_profiles.delete(created.profileId(), created.rowVersion());
				}
				assertOwnedProfileRangeEmpty();
				assertNoFixtureAccountsOrCharacters();
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		finally
		{
			if (_admin != null)
			{
				try
				{
					_admin.close();
				}
				catch (Throwable throwable)
				{
					failure = combine(failure, throwable);
				}
			}
			DatabaseFactory.close();
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
		registry.add("01-guarded-environment-probe-contract", this::testEnvironmentProbe);
		registry.add("02-ten-thousand-durable-bootstrap-db-memory-budget", this::testPopulationBootstrap);
		registry.add("03-bootstrap-queue-recovery-zero-db", this::testPopulationDrainAndRecovery);
		registry.add("04-two-wave-scheduler-memory-overload-recovery", this::testSchedulerWaves);
	}

	private void testEnvironmentProbe(PhantomTestContext context) throws Exception
	{
		_admin.requireProductionDatabaseIdle();
		final DbStatus status = _admin.status();
		PhantomAssertions.assertTrue(status.nonNegative(), "MariaDB status counters are unavailable.");
		final HikariSnapshot hikari = HikariSnapshot.capture();
		assertHikariBounds(hikari, false);
		final JvmSnapshot jvm = JvmSnapshot.capture();
		PhantomAssertions.assertTrue((jvm.heapUsed() >= 0) && (jvm.heapCommitted() > 0) && (jvm.heapMax() > 0), "JVM heap probe is invalid.");
		PhantomAssertions.assertTrue((jvm.liveThreads() > 0) && (jvm.peakThreads() >= jvm.liveThreads()), "JVM thread probe is invalid.");
		assertNoPerProfileRuntimeOwnerFields(PhantomPopulationManager.class, "Entry");
		assertNoPerProfileRuntimeOwnerFields(PhantomScheduler.class, "Slot");
		context.record("hikari.initial", hikari.compact());
		context.record("jvm.initial", jvm.compact());
	}

	private void testPopulationBootstrap(PhantomTestContext context) throws Exception
	{
		_admin.requireProductionDatabaseIdle();
		_ownership = new Ownership();
		_manager = new PhantomPopulationManager(_store, _catalog, null, _ownership, Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC, SCALE, 0, SCALE, MAXIMUM_MATERIALIZED, 2, POPULATION_BUDGET);
		_populationBaseline = settleHeap();
		final DbStatus before = _admin.status();
		final HikariSnapshot hikariBefore = HikariSnapshot.capture();
		final HikariSnapshot hikariPeak;
		try (HikariSampler sampler = new HikariSampler())
		{
			PhantomAssertions.assertTrue(_manager.start(), "Real population manager did not start.");
			hikariPeak = sampler.stopAndPeak();
		}
		final DbStatus bootstrapDelta = _admin.status().minus(before);
		_admin.requireProductionDatabaseIdle();
		if (bootstrapDelta.select() != 40)
		{
			throw new PhantomTestConfigurationException(BLOCKED_DB_EVIDENCE + ": expected exact Com_select=40 but observed " + bootstrapDelta.select() + ".");
		}
		PhantomAssertions.assertEquals(0L, bootstrapDelta.insert(), "Population bootstrap performed INSERT.");
		PhantomAssertions.assertEquals(0L, bootstrapDelta.update(), "Population bootstrap performed UPDATE.");
		PhantomAssertions.assertEquals(0L, bootstrapDelta.delete(), "Population bootstrap performed DELETE.");

		final PhantomPopulationManager.Snapshot manager = _manager.snapshot();
		PhantomAssertions.assertEquals(SCALE, manager.managed(), "Population manager did not restore exactly 10000 profiles.");
		PhantomAssertions.assertEquals(SCALE, manager.retryActions(), "Population bootstrap queue is not one bounded action per profile.");
		_populationQueuePeak = manager.retryActions();
		assertHikariBounds(hikariBefore.maximum(hikariPeak), true);
		_populationLoaded = settleHeap();
		assertHeapWithin(_populationLoaded, _populationBaseline, POPULATION_LOADED_BUDGET, "Population loaded heap exceeded baseline +256 MiB.");

		context.record("population.bootstrapDbDelta", bootstrapDelta.compact());
		context.record("population.bootstrapPages", 40);
		context.record("population.hikariBefore", hikariBefore.compact());
		context.record("population.hikariPeak", hikariPeak.compact());
		context.record("population.heapBaseline", _populationBaseline.compact());
		context.record("population.heapLoaded", _populationLoaded.compact());
	}

	private void testPopulationDrainAndRecovery(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertTrue(_manager != null, "Population bootstrap scenario did not publish its manager.");
		final long ownershipCallsBefore = _ownership.calls();
		final DbStatus before = _admin.status();
		while (_manager.snapshot().retryActions() > 0)
		{
			_populationTotalPulses++;
			PhantomAssertions.assertTrue(_populationTotalPulses <= 470, "Population bootstrap queue exceeded the allowed pulse envelope.");
			_manager.onPulse();
			final PhantomPopulationManager.Snapshot snapshot = _manager.snapshot();
			_populationQueuePeak = Math.max(_populationQueuePeak, snapshot.retryActions());
			PhantomAssertions.assertTrue(snapshot.lastPulseOperations() <= POPULATION_BUDGET, "Population pulse exceeded 64 operations.");
			if (snapshot.lastPulseOperations() > 0)
			{
				_populationProductivePulses++;
			}
		}
		final DbStatus drainDelta = _admin.status().minus(before);
		PhantomAssertions.assertEquals(DbStatus.zero(), drainDelta, "Population ownership drain touched MariaDB.");
		PhantomAssertions.assertEquals(30_000L, _ownership.calls() - ownershipCallsBefore, "Population bootstrap did not execute exact register/attach/signal work.");
		PhantomAssertions.assertTrue(_populationProductivePulses <= 469, "Population bootstrap exceeded 469 productive pulses.");
		PhantomAssertions.assertTrue(_populationTotalPulses <= 470, "Population bootstrap exceeded one optional bookkeeping pulse.");
		PhantomAssertions.assertEquals(SCALE, _ownership.registeredCount(), "Ownership port did not retain exact registered population.");
		final HikariSnapshot afterDrain = HikariSnapshot.capture();
		assertHikariBounds(afterDrain, false);
		PhantomAssertions.assertEquals(0, afterDrain.active(), "Hikari retained an active connection after population drain.");
		PhantomAssertions.assertEquals(0, afterDrain.awaiting(), "Hikari retained a waiter after population drain.");

		stopManager();
		_ownership = null;
		final JvmSnapshot recovered = settleHeap();
		assertHeapWithin(recovered, _populationBaseline, POPULATION_RECOVERED_BUDGET, "Population stop did not recover to baseline +64 MiB.");
		context.record("population.queuePeak", _populationQueuePeak);
		context.record("population.productivePulses", _populationProductivePulses);
		context.record("population.totalPulses", _populationTotalPulses);
		context.record("population.drainDbDelta", drainDelta.compact());
		context.record("population.hikariAfterDrain", afterDrain.compact());
		context.record("population.heapRecovered", recovered.compact());
		context.record("population.gcLoadedDelta", _populationLoaded.gcDelta(_populationBaseline));
		context.record("population.gcRecoveredDelta", recovered.gcDelta(_populationLoaded));
	}
	private void testSchedulerWaves(PhantomTestContext context) throws Exception
	{
		_admin.requireProductionDatabaseIdle();
		final ManualClock clock = new ManualClock();
		final long[] delivered = new long[1];
		final PhantomMetrics metrics = new PhantomMetrics();
		_scheduler = new PhantomScheduler(SCALE, 100, SCHEDULER_BUDGET, PhantomSchedulerPolicy.productionDefaults(100), clock, (pulse, period) -> null, false, metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), PhantomActivityMaterializationPort.noop(), item -> delivered[0]++);
		PhantomAssertions.assertTrue(_scheduler.start(), "Manual real scheduler did not start.");
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			PhantomAssertions.assertEquals(RegistrationStatus.REGISTERED, _scheduler.register(profileId).status(), "Scheduler registration failed before capacity.");
		}
		final JvmSnapshot registeredBaseline = settleHeap();
		final DbStatus before = _admin.status();
		final WaveResult first = runWave(1, 1, clock, delivered, registeredBaseline);
		final WaveResult second = runWave(2, 3, clock, delivered, registeredBaseline);
		final DbStatus schedulerDelta = _admin.status().minus(before);
		PhantomAssertions.assertEquals(DbStatus.zero(), schedulerDelta, "Scheduler spike touched MariaDB.");
		PhantomAssertions.assertTrue(first.criticalReached() && second.criticalReached(), "Both scheduler waves did not reach CRITICAL.");
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.NORMAL, first.recoveredLevel(), "Scheduler wave 1 did not recover NORMAL.");
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.NORMAL, second.recoveredLevel(), "Scheduler wave 2 did not recover NORMAL.");
		PhantomAssertions.assertTrue(first.peakHeapUsed() <= (registeredBaseline.heapUsed() + SCHEDULER_TRANSIENT_BUDGET), "Scheduler wave 1 transient heap exceeded baseline +128 MiB.");
		PhantomAssertions.assertTrue(second.peakHeapUsed() <= (registeredBaseline.heapUsed() + SCHEDULER_TRANSIENT_BUDGET), "Scheduler wave 2 transient heap exceeded baseline +128 MiB.");
		PhantomAssertions.assertTrue(second.recovered().heapUsed() <= (first.recovered().heapUsed() + SCHEDULER_RECOVERY_RATCHET), "Scheduler second recovery ratcheted by more than 32 MiB.");

		stopScheduler();
		final JvmSnapshot finalHeap = settleHeap();
		assertHeapWithin(finalHeap, registeredBaseline, SCHEDULER_FINAL_BUDGET, "Scheduler final heap exceeded registered baseline +64 MiB.");
		context.record("scheduler.databaseDelta", schedulerDelta.compact());
		context.record("scheduler.registeredBaseline", registeredBaseline.compact());
		context.record("scheduler.wave1", first.compact());
		context.record("scheduler.wave2", second.compact());
		context.record("scheduler.final", finalHeap.compact());
		context.record("scheduler.gcWave1Delta", first.recovered().gcDelta(registeredBaseline));
		context.record("scheduler.gcWave2Delta", second.recovered().gcDelta(first.recovered()));
	}

	private WaveResult runWave(int wave, long submitSequence, ManualClock clock, long[] delivered, JvmSnapshot baseline) throws Exception
	{
		long peakHeap = JvmSnapshot.capture().heapUsed();
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			final SignalStatus status = _scheduler.submitSignal(profileId, new PhantomRelevanceSignal(SIGNAL_SOURCE, submitSequence, PhantomActivityState.WARM, SIGNAL_TTL_MILLIS)).status();
			PhantomAssertions.assertTrue((status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED), "Scheduler wave signal was not accepted.");
		}
		peakHeap = Math.max(peakHeap, JvmSnapshot.capture().heapUsed());
		boolean critical = false;
		int pulses = 0;
		while (_scheduler.snapshot().ready() > 0)
		{
			final long before = delivered[0];
			_scheduler.pulse();
			final long work = delivered[0] - before;
			PhantomAssertions.assertTrue(work <= SCHEDULER_BUDGET, "Scheduler delivered more than 128 work items in one pulse.");
			final SchedulerSnapshot snapshot = _scheduler.snapshot();
			assertSchedulerBounds(snapshot);
			critical |= snapshot.overloadLevel() == PhantomActivityOverloadLevel.CRITICAL;
			peakHeap = Math.max(peakHeap, JvmSnapshot.capture().heapUsed());
			PhantomAssertions.assertTrue(++pulses <= 100, "Scheduler pressure wave did not drain within bounded pulses.");
		}
		_scheduler.pulse();
		assertSchedulerBounds(_scheduler.snapshot());
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.NORMAL, _scheduler.snapshot().overloadLevel(), "Scheduler overload did not recover after pressure drain.");

		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			final SignalStatus status = _scheduler.withdrawSignal(profileId, SIGNAL_SOURCE, submitSequence + 1).status();
			PhantomAssertions.assertTrue((status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED), "Scheduler pressure withdrawal was not accepted.");
		}
		pulses = 0;
		while (_scheduler.snapshot().ready() > 0)
		{
			final long before = delivered[0];
			_scheduler.pulse();
			PhantomAssertions.assertTrue((delivered[0] - before) <= SCHEDULER_BUDGET, "Scheduler withdrawal pulse exceeded work budget.");
			assertSchedulerBounds(_scheduler.snapshot());
			peakHeap = Math.max(peakHeap, JvmSnapshot.capture().heapUsed());
			PhantomAssertions.assertTrue(++pulses <= 100, "Scheduler pressure withdrawal did not drain within bounded pulses.");
		}
		clock.advanceMillis(PhantomSchedulerPolicy.productionDefaults(100).demotionGraceMillis());
		pulses = 0;
		do
		{
			final long before = delivered[0];
			_scheduler.pulse();
			PhantomAssertions.assertTrue((delivered[0] - before) <= SCHEDULER_BUDGET, "Scheduler demotion pulse exceeded work budget.");
			assertSchedulerBounds(_scheduler.snapshot());
			peakHeap = Math.max(peakHeap, JvmSnapshot.capture().heapUsed());
			PhantomAssertions.assertTrue(++pulses <= 100, "Scheduler demotion recovery did not drain within bounded pulses.");
		}
		while ((_scheduler.snapshot().ready() > 0) || (_scheduler.snapshot().due() > 0));
		_scheduler.pulse();
		final SchedulerSnapshot recoveredScheduler = _scheduler.snapshot();
		assertSchedulerBounds(recoveredScheduler);
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.NORMAL, recoveredScheduler.overloadLevel(), "Scheduler pressure removal did not recover NORMAL.");
		final JvmSnapshot recovered = settleHeap();
		peakHeap = Math.max(peakHeap, recovered.heapUsed());
		PhantomAssertions.assertTrue(peakHeap <= (baseline.heapUsed() + SCHEDULER_TRANSIENT_BUDGET), "Scheduler wave transient heap exceeded budget.");
		return new WaveResult(wave, critical, recoveredScheduler.overloadLevel(), peakHeap, recovered);
	}

	private int countManagedProfiles()
	{
		int count = 0;
		long cursor = 0;
		while (true)
		{
			final List<ManagedSnapshot> page = _store.loadManagedAfter(cursor, PAGE_SIZE);
			count += page.size();
			if (page.isEmpty())
			{
				return count;
			}
			cursor = page.getLast().profile().profileId();
			if (page.size() < PAGE_SIZE)
			{
				return count;
			}
		}
	}
	private void assertNoFixtureAccountsOrCharacters() throws SQLException
	{
		for (int start = 0; start < _createdProfiles.size(); start += PAGE_SIZE)
		{
			final int end = Math.min(_createdProfiles.size(), start + PAGE_SIZE);
			final String placeholders = String.join(",", Collections.nCopies(end - start, "?"));
			PhantomAssertions.assertEquals(0L, countByAccounts("SELECT COUNT(*) FROM accounts WHERE login IN (" + placeholders + ")", start, end), "CP2 SHELL fixture created an account.");
			PhantomAssertions.assertEquals(0L, countByAccounts("SELECT COUNT(*) FROM characters WHERE account_name IN (" + placeholders + ")", start, end), "CP2 SHELL fixture created a character.");
		}
	}

	private long countByAccounts(String sql, int start, int end) throws SQLException
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int index = start; index < end; index++)
			{
				statement.setString((index - start) + 1, _createdProfiles.get(index).reservedAccount());
			}
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Exact fixture residue count returned no row.");
				return result.getLong(1);
			}
		}
	}

	private void assertOwnedProfileRangeEmpty() throws SQLException
	{
		final long first = _createdProfiles.getFirst().profileId();
		final long last = _createdProfiles.getLast().profileId();
		try (Connection connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(0L, countRange(connection, "SELECT COUNT(*) FROM phantom_profiles WHERE profile_id BETWEEN ? AND ?", first, last), "CP2 profile cleanup left owned profiles.");
			PhantomAssertions.assertEquals(0L, countRange(connection, "SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id BETWEEN ? AND ?", first, last), "CP2 profile cleanup left owned components.");
		}
	}

	private static long countRange(Connection connection, String sql, long first, long last) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setLong(1, first);
			statement.setLong(2, last);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Owned range count returned no row.");
				return result.getLong(1);
			}
		}
	}

	private void stopManager()
	{
		if (_manager != null)
		{
			_manager.beginStop();
			PhantomAssertions.assertTrue(_manager.finishStop(), "Population manager did not stop cleanly.");
			PhantomAssertions.assertEquals(0, _manager.snapshot().managed(), "Population manager retained managed entries after stop.");
			_manager = null;
		}
	}

	private void stopScheduler()
	{
		if (_scheduler != null)
		{
			_scheduler.beginStop();
			PhantomAssertions.assertTrue(_scheduler.finishStop(), "Scheduler did not stop cleanly.");
			final SchedulerSnapshot stopped = _scheduler.snapshot();
			PhantomAssertions.assertEquals(0, stopped.registered(), "Scheduler retained registrations after stop.");
			PhantomAssertions.assertEquals(0, stopped.ready(), "Scheduler retained ready work after stop.");
			PhantomAssertions.assertEquals(0, stopped.due(), "Scheduler retained due work after stop.");
			_scheduler = null;
		}
	}

	private static void assertHikariBounds(HikariSnapshot snapshot, boolean duringScan)
	{
		PhantomAssertions.assertTrue(snapshot.total() <= PhantomTestDatabaseGuard.MAX_TEST_POOL_SIZE, "Hikari total connections exceeded 4.");
		PhantomAssertions.assertEquals(0, snapshot.awaiting(), "Hikari recorded a waiting connection request.");
		if (duringScan)
		{
			PhantomAssertions.assertTrue(snapshot.active() <= 1, "Sequential population scan used more than one active connection.");
		}
	}

	private static void assertSchedulerBounds(SchedulerSnapshot snapshot)
	{
		PhantomAssertions.assertTrue(snapshot.registered() <= SCALE, "Scheduler registered count exceeded 10000.");
		PhantomAssertions.assertTrue(snapshot.ready() <= SCALE, "Scheduler ready count exceeded 10000.");
		PhantomAssertions.assertTrue(snapshot.due() <= SCALE, "Scheduler due count exceeded 10000.");
	}

	private static void assertHeapWithin(JvmSnapshot actual, JvmSnapshot baseline, long allowance, String message)
	{
		PhantomAssertions.assertTrue(actual.heapUsed() <= (baseline.heapUsed() + allowance), message + " baseline=" + baseline.heapUsed() + " actual=" + actual.heapUsed() + ".");
	}

	private static JvmSnapshot settleHeap() throws InterruptedException
	{
		final long deadline = System.nanoTime() + 1_900_000_000L;
		long previous = Long.MAX_VALUE;
		JvmSnapshot current = JvmSnapshot.capture();
		for (int attempt = 0; (attempt < 4) && (System.nanoTime() < deadline); attempt++)
		{
			System.gc();
			System.runFinalization();
			Thread.sleep(25);
			current = JvmSnapshot.capture();
			if (Math.abs(previous - current.heapUsed()) <= MIB)
			{
				break;
			}
			previous = current.heapUsed();
		}
		return current;
	}

	private static void assertNoPerProfileRuntimeOwnerFields(Class<?> owner, String nestedName)
	{
		Class<?> nested = null;
		for (Class<?> candidate : owner.getDeclaredClasses())
		{
			if (nestedName.equals(candidate.getSimpleName()))
			{
				nested = candidate;
				break;
			}
		}
		PhantomAssertions.assertTrue(nested != null, owner.getSimpleName() + " per-profile holder was not found.");
		for (Field field : Objects.requireNonNull(nested).getDeclaredFields())
		{
			PhantomAssertions.assertFalse(Thread.class.isAssignableFrom(field.getType()), owner.getSimpleName() + " per-profile holder stores a Thread.");
			PhantomAssertions.assertFalse(Future.class.isAssignableFrom(field.getType()), owner.getSimpleName() + " per-profile holder stores a Future.");
			PhantomAssertions.assertFalse(Executor.class.isAssignableFrom(field.getType()), owner.getSimpleName() + " per-profile holder stores an Executor.");
		}
	}

	private static Throwable combine(Throwable first, Throwable second)
	{
		if (first == null)
		{
			return second;
		}
		first.addSuppressed(second);
		return first;
	}

	private record CreatedProfile(long profileId, long rowVersion, String reservedAccount)
	{
	}

	private record DbStatus(long select, long insert, long update, long delete)
	{
		private static DbStatus zero()
		{
			return new DbStatus(0, 0, 0, 0);
		}

		private DbStatus minus(DbStatus before)
		{
			return new DbStatus(select - before.select, insert - before.insert, update - before.update, delete - before.delete);
		}

		private boolean nonNegative()
		{
			return (select >= 0) && (insert >= 0) && (update >= 0) && (delete >= 0);
		}

		private String compact()
		{
			return "select=" + select + ",insert=" + insert + ",update=" + update + ",delete=" + delete;
		}
	}
	private record HikariSnapshot(int active, int idle, int total, int awaiting)
	{
		private static final String OBJECT_NAME = "com.zaxxer.hikari:type=Pool (L2JMobiusPool)";

		private static HikariSnapshot capture() throws Exception
		{
			final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
			final ObjectName objectName = new ObjectName(OBJECT_NAME);
			if (!server.isRegistered(objectName))
			{
				throw new IllegalStateException("Hikari pool MBean L2JMobiusPool is not registered.");
			}
			return new HikariSnapshot(
				((Number) server.getAttribute(objectName, "ActiveConnections")).intValue(),
				((Number) server.getAttribute(objectName, "IdleConnections")).intValue(),
				((Number) server.getAttribute(objectName, "TotalConnections")).intValue(),
				((Number) server.getAttribute(objectName, "ThreadsAwaitingConnection")).intValue());
		}

		private HikariSnapshot maximum(HikariSnapshot other)
		{
			return new HikariSnapshot(Math.max(active, other.active), Math.max(idle, other.idle), Math.max(total, other.total), Math.max(awaiting, other.awaiting));
		}

		private String compact()
		{
			return "active=" + active + ",idle=" + idle + ",total=" + total + ",awaiting=" + awaiting;
		}
	}

	private static final class HikariSampler implements AutoCloseable
	{
		private final AtomicBoolean _running = new AtomicBoolean(true);
		private final Thread _thread;
		private volatile HikariSnapshot _peak;
		private volatile Throwable _failure;

		private HikariSampler() throws Exception
		{
			_peak = HikariSnapshot.capture();
			_thread = new Thread(this::sample, "PhantomGoal029CP2-HikariSampler");
			_thread.setDaemon(true);
			_thread.start();
		}

		private void sample()
		{
			try
			{
				while (_running.get())
				{
					_peak = _peak.maximum(HikariSnapshot.capture());
					LockSupport.parkNanos(100_000L);
				}
			}
			catch (Throwable throwable)
			{
				_failure = throwable;
			}
		}

		private HikariSnapshot stopAndPeak() throws Exception
		{
			close();
			if (_failure instanceof Exception exception)
			{
				throw exception;
			}
			if (_failure != null)
			{
				throw new RuntimeException(_failure);
			}
			return _peak.maximum(HikariSnapshot.capture());
		}

		@Override
		public void close() throws InterruptedException
		{
			_running.set(false);
			_thread.join(2_000);
			PhantomAssertions.assertFalse(_thread.isAlive(), "Bounded Hikari sampler thread did not stop.");
		}
	}

	private record JvmSnapshot(long heapUsed, long heapCommitted, long heapMax, long gcCount, long gcMillis, int liveThreads, int peakThreads)
	{
		private static JvmSnapshot capture()
		{
			final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
			final MemoryUsage heap = memory.getHeapMemoryUsage();
			long collections = 0;
			long collectionMillis = 0;
			for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans())
			{
				collections += Math.max(0, collector.getCollectionCount());
				collectionMillis += Math.max(0, collector.getCollectionTime());
			}
			final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
			return new JvmSnapshot(heap.getUsed(), heap.getCommitted(), heap.getMax(), collections, collectionMillis, threads.getThreadCount(), threads.getPeakThreadCount());
		}

		private String gcDelta(JvmSnapshot before)
		{
			return "count=" + (gcCount - before.gcCount) + ",millis=" + (gcMillis - before.gcMillis);
		}

		private String compact()
		{
			return "used=" + heapUsed + ",committed=" + heapCommitted + ",max=" + heapMax + ",gcCount=" + gcCount + ",gcMillis=" + gcMillis + ",liveThreads=" + liveThreads + ",peakThreads=" + peakThreads;
		}
	}

	private record WaveResult(int wave, boolean criticalReached, PhantomActivityOverloadLevel recoveredLevel, long peakHeapUsed, JvmSnapshot recovered)
	{
		private String compact()
		{
			return "wave=" + wave + ",critical=" + criticalReached + ",recovered=" + recoveredLevel + ",peakHeap=" + peakHeapUsed + ",recoveredHeap=" + recovered.heapUsed();
		}
	}

	private static final class ManualClock implements PhantomScheduler.MonotonicClock
	{
		private long _now;

		@Override
		public long nanoTime()
		{
			return _now;
		}

		private void advanceMillis(long millis)
		{
			_now += millis * 1_000_000L;
		}
	}
	private static final class AdminStatusProbe implements AutoCloseable
	{
		private static final String STATUS_SQL = "SHOW GLOBAL STATUS WHERE Variable_name IN ('Com_select','Com_insert','Com_update','Com_delete')";
		private static final String PROCESSLIST_SQL = "SELECT ID, USER, DB, COMMAND FROM information_schema.PROCESSLIST WHERE DB = 'l2jmobiush5' AND COMMAND <> 'Sleep' LIMIT 16";
		private final Connection _connection;

		private AdminStatusProbe(Connection connection)
		{
			_connection = connection;
		}

		private static AdminStatusProbe open() throws Exception
		{
			final String url = requireEnvironment("PHANTOM_DB_ADMIN_URL");
			final String user = requireEnvironment("PHANTOM_DB_ADMIN_USER");
			final String password = requireEnvironment("PHANTOM_DB_ADMIN_PASSWORD");
			validateUrl(url);
			return new AdminStatusProbe(DriverManager.getConnection(url, user, password));
		}

		private DbStatus status() throws SQLException
		{
			final Map<String, Long> counters = new HashMap<>();
			try (Statement statement = _connection.createStatement(); ResultSet result = statement.executeQuery(STATUS_SQL))
			{
				while (result.next())
				{
					counters.put(result.getString(1).toLowerCase(Locale.ROOT), Long.parseLong(result.getString(2)));
				}
			}
			if (counters.size() != 4)
			{
				throw new SQLException(BLOCKED_DB_EVIDENCE + ": required MariaDB counters are missing.");
			}
			return new DbStatus(requireCounter(counters, "com_select"), requireCounter(counters, "com_insert"), requireCounter(counters, "com_update"), requireCounter(counters, "com_delete"));
		}

		private void requireProductionDatabaseIdle() throws SQLException, PhantomTestConfigurationException
		{
			try (Statement statement = _connection.createStatement(); ResultSet result = statement.executeQuery(PROCESSLIST_SQL))
			{
				if (result.next())
				{
					throw new PhantomTestConfigurationException(BLOCKED_DB_ISOLATION + ": active production DB session detected.");
				}
			}
		}

		@Override
		public void close() throws SQLException
		{
			_connection.close();
		}

		private static long requireCounter(Map<String, Long> counters, String name) throws SQLException
		{
			final Long value = counters.get(name);
			if (value == null)
			{
				throw new SQLException(BLOCKED_DB_EVIDENCE + ": missing " + name + ".");
			}
			return value;
		}

		private static String requireEnvironment(String name) throws PhantomTestConfigurationException
		{
			final String value = System.getenv(name);
			if ((value == null) || value.isBlank())
			{
				throw new PhantomTestConfigurationException(BLOCKED_ADMIN_ENV + ": required admin status environment is missing.");
			}
			return value;
		}

		private static void validateUrl(String value) throws PhantomTestConfigurationException
		{
			if (!value.startsWith("jdbc:mysql://"))
			{
				throw invalidAdminUrl();
			}
			final URI uri;
			try
			{
				uri = new URI(value.substring("jdbc:".length()));
			}
			catch (URISyntaxException e)
			{
				throw new PhantomTestConfigurationException(BLOCKED_ADMIN_ENV + ": admin status URL is malformed.", e);
			}
			final String path = uri.getRawPath();
			final String authority = uri.getRawAuthority();
			if (!"mysql".equals(uri.getScheme()) || (!"127.0.0.1".equals(uri.getHost()) && !"localhost".equals(uri.getHost())) || (uri.getPort() != PhantomTestDatabaseGuard.TARGET_PORT) || ((path != null) && !path.isEmpty() && !"/".equals(path)) || (uri.getRawQuery() != null) || (uri.getFragment() != null) || (uri.getUserInfo() != null) || (authority == null) || authority.contains("@") || authority.contains(","))
			{
				throw invalidAdminUrl();
			}
		}

		private static PhantomTestConfigurationException invalidAdminUrl()
		{
			return new PhantomTestConfigurationException(BLOCKED_ADMIN_ENV + ": admin status URL must be credential-free jdbc:mysql local port 3308 with no schema/query/fragment.");
		}
	}
}
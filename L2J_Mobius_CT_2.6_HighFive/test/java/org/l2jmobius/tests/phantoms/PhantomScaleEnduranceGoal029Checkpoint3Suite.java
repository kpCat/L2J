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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
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
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend.CapabilitySnapshot;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
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
 * Exact bounded 30-minute scale endurance and owner recovery checkpoint.
 */
public final class PhantomScaleEnduranceGoal029Checkpoint3Suite implements PhantomTestSuite
{
	private static final long SEED = 29_002_903L;
	private static final int SCALE = 10_000;
	private static final int PAGE_SIZE = 256;
	private static final int POPULATION_BUDGET = 64;
	private static final int SCHEDULER_BUDGET = 128;
	private static final int MAXIMUM_MATERIALIZED = 32;
	private static final long MIB = 1024L * 1024L;
	private static final long POPULATION_LOADED_BUDGET = 256L * MIB;
	private static final long POPULATION_RECOVERED_BUDGET = 64L * MIB;
	private static final long SCHEDULER_COMMITTED_BUDGET = 256L * MIB;
	private static final long SCHEDULER_MINIMUM_HEADROOM = 512L * MIB;
	private static final long SCHEDULER_FINAL_BUDGET = 64L * MIB;
	private static final long POPULATION_POST_RATCHET_BUDGET = 64L * MIB;
	private static final long ENDURANCE_NANOS = 30L * 60L * 1_000_000_000L;
	private static final long MAXIMUM_ENDURANCE_NANOS = 31L * 60L * 1_000_000_000L;
	private static final long PULSE_NANOS = 100_000_000L;
	private static final long SAMPLE_NANOS = 25_000_000_000L;
	private static final long SPIKE_HOLD_NANOS = 30_000_000_000L;
	private static final long RECOVERY_LIMIT_NANOS = 60_000_000_000L;
	private static final long SIGNAL_TTL_MILLIS = 86_400_000L;
	private static final long[] SPIKE_OFFSETS_NANOS = { 2L * 60L * 1_000_000_000L, 7L * 60L * 1_000_000_000L, 12L * 60L * 1_000_000_000L, 17L * 60L * 1_000_000_000L, 22L * 60L * 1_000_000_000L, 27L * 60L * 1_000_000_000L };
	private static final PhantomNavigationPoint NAVIGATION_ORIGIN = new PhantomNavigationPoint(0, 0, 0, 0);
	private static final String BACKGROUND_SOURCE = "goal029cp3.background";
	private static final String WARM_SOURCE = "goal029cp3.warm";
	private static final int MAXIMUM_REPLACEMENT_CONNECTIONS = 6;
	private static final String BLOCKED_ADMIN_ENV = "BLOCKED_029CP3_ADMIN_STATUS_ENV_REQUIRED";
	private static final String BLOCKED_DB_ISOLATION = "BLOCKED_029C_DB_INSTANCE_NOT_ISOLATED";
	private static final String BLOCKED_DB_EVIDENCE = "BLOCKED_029CP3_DB_RATE_EVIDENCE_UNAVAILABLE";
	private static final String BLOCKED_SQL_ATTRIBUTION = "BLOCKED_029D_SQL_ATTRIBUTION_LOG_UNAVAILABLE";
	private static final String BLOCKED_UNATTRIBUTED_STATEMENT = "BLOCKED_029D_UNATTRIBUTED_DRIVER_STATEMENT";
	private static final String BLOCKED_UNCLASSIFIED_STATEMENT = "BLOCKED_029D_UNCLASSIFIED_SQL";
	private static final String BLOCKED_APPLICATION_TRAFFIC = "BLOCKED_029D_APPLICATION_DB_TRAFFIC";

	private final List<CreatedProfile> _createdProfiles = new ArrayList<>(SCALE);
	private AdminStatusProbe _admin;
	private PhantomProfileRepository _profiles;
	private PhantomPopulationCatalog _catalog;
	private PhantomPopulationStore _store;
	private PhantomPopulationManager _manager;
	private Ownership _ownership;
	private PhantomScheduler _scheduler;
	private PhantomNavigationService _navigation;
	private PhantomMetrics _metrics;
	private PopulationResult _prePopulation;
	private long _deliveredWork;

	@Override
	public String id()
	{
		return "scale-endurance-goal029cp3";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal029 CP3 requires the exact deterministic seed.");
		final Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		final Path expectedWorkingDirectory = context.moduleRoot().resolve("dist/game").normalize();
		PhantomAssertions.assertEquals(expectedWorkingDirectory, workingDirectory, "Goal029 CP3 JVM must run from dist/game.");

		ConfigLoader.init();
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		final BootstrapResult bootstrap = PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_USER, bootstrap.settings().login(), "CP3 did not use the dedicated test DB user.");
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.MAX_TEST_POOL_SIZE, bootstrap.settings().maximumConnections(), "CP3 test DB pool is not the exact guarded maximum.");

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
				PhantomAssertions.assertEquals(previousProfileId + 1, created.profile().profileId(), "CP3 profile IDs are not a contiguous exact-owned range.");
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
			stopNavigation();
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
				context.record("cleanup.ownedRows", "profiles=0,components=0,accounts=0,characters=0");
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
		registry.add("01-pre-soak-ten-thousand-population-restart", this::testPreSoakPopulationRestart);
		registry.add("02-exact-thirty-minute-scheduler-navigation-endurance", this::testSchedulerNavigationEndurance);
		registry.add("03-post-soak-ten-thousand-population-restart", this::testPostSoakPopulationRestart);
	}

	private void testPreSoakPopulationRestart(PhantomTestContext context) throws Exception
	{
		_prePopulation = runPopulationRestart("pre", context);
		context.record("population.pre", _prePopulation.compact());
	}

	private void testPostSoakPopulationRestart(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertTrue(_prePopulation != null, "Pre-soak population proof was not published.");
		final PopulationResult post = runPopulationRestart("post", context);
		PhantomAssertions.assertTrue(post.recovered().heapUsed() <= (_prePopulation.recovered().heapUsed() + POPULATION_POST_RATCHET_BUDGET), "Post-soak recovered heap exceeded pre-soak recovered heap +64 MiB.");
		context.record("population.post", post.compact());
		context.record("population.recoveredRatchetingBytes", post.recovered().heapUsed() - _prePopulation.recovered().heapUsed());
	}

	private PopulationResult runPopulationRestart(String phase, PhantomTestContext context) throws Exception
	{
		_admin.requireProductionDatabaseIdle();
		_ownership = new Ownership();
		_manager = new PhantomPopulationManager(_store, _catalog, null, _ownership, Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC, SCALE, 0, SCALE, MAXIMUM_MATERIALIZED, 2, POPULATION_BUDGET);
		final JvmSnapshot baseline = settleHeap();
		final HikariSnapshot hikariBefore = HikariSnapshot.capture();
		final DbStatus bootstrapBefore = _admin.status();
		final HikariSnapshot hikariPeak;
		try (HikariSampler sampler = new HikariSampler())
		{
			PhantomAssertions.assertTrue(_manager.start(), "Real population manager did not start during " + phase + "-soak restart.");
			hikariPeak = sampler.stopAndPeak();
		}
		final DbStatus bootstrapDelta = _admin.status().minus(bootstrapBefore);
		_admin.requireProductionDatabaseIdle();
		if (bootstrapDelta.select() != 40)
		{
			throw new PhantomTestConfigurationException(BLOCKED_DB_EVIDENCE + ": expected exact Com_select=40 during " + phase + " restart but observed " + bootstrapDelta.select() + ".");
		}
		PhantomAssertions.assertEquals(0L, bootstrapDelta.insert(), "Population restart performed INSERT.");
		PhantomAssertions.assertEquals(0L, bootstrapDelta.update(), "Population restart performed UPDATE.");
		PhantomAssertions.assertEquals(0L, bootstrapDelta.delete(), "Population restart performed DELETE.");
		final PhantomPopulationManager.Snapshot started = _manager.snapshot();
		PhantomAssertions.assertEquals(SCALE, started.managed(), "Population manager did not restore exactly 10000 profiles.");
		PhantomAssertions.assertEquals(SCALE, started.retryActions(), "Population restart queue is not one bounded action per profile.");
		assertHikariBounds(hikariBefore.maximum(hikariPeak), true);
		final JvmSnapshot loaded = settleHeap();
		assertHeapWithin(loaded, baseline, POPULATION_LOADED_BUDGET, "Population loaded heap exceeded baseline +256 MiB.");

		final long ownershipCallsBefore = _ownership.calls();
		final DbStatus drainBefore = _admin.status();
		int productivePulses = 0;
		int totalPulses = 0;
		int maximumOperations = 0;
		while (_manager.snapshot().retryActions() > 0)
		{
			PhantomAssertions.assertTrue(++totalPulses <= 469, "Population restart exceeded 469 pulses.");
			_manager.onPulse();
			final PhantomPopulationManager.Snapshot snapshot = _manager.snapshot();
			maximumOperations = Math.max(maximumOperations, (int) snapshot.lastPulseOperations());
			PhantomAssertions.assertTrue(snapshot.lastPulseOperations() <= POPULATION_BUDGET, "Population pulse exceeded 64 operations.");
			if (snapshot.lastPulseOperations() > 0)
			{
				productivePulses++;
			}
		}
		final DbStatus drainDelta = _admin.status().minus(drainBefore);
		PhantomAssertions.assertEquals(DbStatus.zero(), drainDelta, "Population ownership drain touched MariaDB.");
		PhantomAssertions.assertEquals(30_000L, _ownership.calls() - ownershipCallsBefore, "Population restart did not execute exact register/attach/signal work.");
		PhantomAssertions.assertEquals(469, productivePulses, "Population restart did not require exactly 469 productive pulses.");
		PhantomAssertions.assertEquals(469, totalPulses, "Population restart did not drain in exactly 469 pulses.");
		PhantomAssertions.assertEquals(SCALE, _ownership.registeredCount(), "Ownership port did not retain exact registered population.");
		final HikariSnapshot afterDrain = HikariSnapshot.capture();
		assertHikariBounds(afterDrain, false);
		PhantomAssertions.assertEquals(0, afterDrain.active(), "Hikari retained an active connection after population drain.");
		stopManager();
		_ownership = null;
		final JvmSnapshot recovered = settleHeap();
		assertHeapWithin(recovered, baseline, POPULATION_RECOVERED_BUDGET, "Population stop did not recover to baseline +64 MiB.");
		final HikariSnapshot afterStop = HikariSnapshot.capture();
		assertHikariBounds(afterStop, false);
		PhantomAssertions.assertEquals(0, afterStop.active(), "Hikari retained an active connection after population stop.");
		return new PopulationResult(phase, bootstrapDelta, drainDelta, 30_000L, productivePulses, totalPulses, maximumOperations, baseline, loaded, recovered, hikariBefore.maximum(hikariPeak), afterStop);
	}
	private void testSchedulerNavigationEndurance(PhantomTestContext context) throws Exception
	{
		_admin.requireProductionDatabaseIdle();
		_metrics = new PhantomMetrics();
		final PhantomSchedulerPolicy schedulerPolicy = PhantomSchedulerPolicy.productionDefaults(100);
		_scheduler = new PhantomScheduler(SCALE, 100, SCHEDULER_BUDGET, schedulerPolicy, System::nanoTime, (pulse, period) -> null, false, _metrics, new PhantomDiagnosticTrace(false, 0, 0, _metrics), PhantomActivityMaterializationPort.noop(), item -> _deliveredWork++);
		PhantomAssertions.assertTrue(_scheduler.start(), "Foreground real scheduler did not start.");
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			PhantomAssertions.assertEquals(RegistrationStatus.REGISTERED, _scheduler.register(profileId).status(), "Scheduler registration failed before capacity.");
			final SignalStatus status = _scheduler.submitSignal(profileId, new PhantomRelevanceSignal(BACKGROUND_SOURCE, 1, PhantomActivityState.BACKGROUND, SIGNAL_TTL_MILLIS)).status();
			PhantomAssertions.assertTrue((status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED), "Persistent BACKGROUND signal was not accepted.");
		}
		drainReadySetup();

		final PhantomNavigationPolicy navigationPolicy = PhantomNavigationPolicy.productionDefaults();
		final ManualDispatcher dispatcher = new ManualDispatcher();
		_navigation = new PhantomNavigationService(navigationPolicy, new NavigationBackend(), dispatcher, () -> 0, _metrics);
		PhantomAssertions.assertTrue(_navigation.start(), "Real navigation service did not start.");
		final JvmSnapshot registeredBaseline = settleHeap();
		final HikariSnapshot hikariBaseline = HikariSnapshot.capture();
		assertHikariSoakSample(hikariBaseline);
		assertNoPerProfileRuntimeOwnerFields(PhantomPopulationManager.class, "Entry");
		assertNoPerProfileRuntimeOwnerFields(PhantomScheduler.class, "Slot");
		final List<EnduranceSample> samples = new ArrayList<>();
		final List<SpikeResult> spikes = new ArrayList<>(6);
		final List<NavigationCycleResult> navigationCycles = new ArrayList<>(6);
		long maximumPulseExecutionNanos = 0;
		long maximumSchedulingLatenessNanos = 0;
		long maximumWorkPerPulse = 0;
		int maximumRegistered = SCALE;
		int maximumReady = 0;
		int maximumDue = 0;
		int spikeIndex = 0;
		boolean spikeSubmitted = false;
		boolean spikeWithdrawn = false;
		boolean spikeCritical = false;
		long spikeStartedNanos = 0;
		long spikeCriticalNanos = 0;
		long spikeWithdrawnNanos = 0;

		SqlAttribution sqlAttribution = null;
		DbStatus soakDelta = null;
		long durationNanos = 0;
		final SqlAttributionWindow attributionWindow = _admin.openSqlAttribution();
		try
		{
			final String soakStartedAt = attributionWindow.markTimestamp();
			final DbStatus soakBefore = _admin.status();
			final long soakStartedNanos = System.nanoTime();
			long nextPulseNanos = soakStartedNanos + PULSE_NANOS;
			long nextSampleNanos = soakStartedNanos;
			long nowNanos = soakStartedNanos;
			while ((nowNanos - soakStartedNanos) < ENDURANCE_NANOS)
			{
				nowNanos = System.nanoTime();
				final long elapsedNanos = nowNanos - soakStartedNanos;
				if (nowNanos >= nextSampleNanos)
				{
					samples.add(captureEnduranceSample(elapsedNanos, registeredBaseline, "periodic"));
					nextSampleNanos += SAMPLE_NANOS;
				}

				if ((spikeIndex < SPIKE_OFFSETS_NANOS.length) && !spikeSubmitted && (elapsedNanos >= SPIKE_OFFSETS_NANOS[spikeIndex]))
				{
					submitWarmSpike(spikeIndex);
					spikeSubmitted = true;
					spikeStartedNanos = System.nanoTime();
					spikeCritical = false;
					spikeCriticalNanos = 0;
					samples.add(captureEnduranceSample(spikeStartedNanos - soakStartedNanos, registeredBaseline, "spike" + (spikeIndex + 1) + "-submitted"));
				}

				if (nowNanos >= nextPulseNanos)
				{
					maximumSchedulingLatenessNanos = Math.max(maximumSchedulingLatenessNanos, nowNanos - nextPulseNanos);
					final long workBefore = _deliveredWork;
					final long pulseStarted = System.nanoTime();
					_scheduler.pulse();
					final long pulseCompleted = System.nanoTime();
					maximumPulseExecutionNanos = Math.max(maximumPulseExecutionNanos, pulseCompleted - pulseStarted);
					final long pulseWork = _deliveredWork - workBefore;
					maximumWorkPerPulse = Math.max(maximumWorkPerPulse, pulseWork);
					PhantomAssertions.assertTrue(pulseWork <= SCHEDULER_BUDGET, "Scheduler delivered more than 128 work items in one pulse.");
					final SchedulerSnapshot snapshot = _scheduler.snapshot();
					assertSchedulerBounds(snapshot);
					maximumRegistered = Math.max(maximumRegistered, snapshot.registered());
					maximumReady = Math.max(maximumReady, snapshot.ready());
					maximumDue = Math.max(maximumDue, snapshot.due());
					if (spikeSubmitted && !spikeWithdrawn && (snapshot.overloadLevel() == PhantomActivityOverloadLevel.CRITICAL))
					{
						spikeCritical = true;
						if (spikeCriticalNanos == 0)
						{
							spikeCriticalNanos = pulseCompleted;
						}
					}
					nextPulseNanos += PULSE_NANOS;

					if (spikeWithdrawn && (snapshot.overloadLevel() == PhantomActivityOverloadLevel.NORMAL))
					{
						final long recoveredNanos = pulseCompleted;
						final long recoveryNanos = recoveredNanos - spikeWithdrawnNanos;
						PhantomAssertions.assertTrue(recoveryNanos <= RECOVERY_LIMIT_NANOS, "Scheduler spike did not recover NORMAL within 60 seconds.");
						final NavigationCycleResult navigation = runNavigationCycle(spikeIndex + 1, dispatcher, navigationPolicy);
						navigationCycles.add(navigation);
						spikes.add(new SpikeResult(spikeIndex + 1, SPIKE_OFFSETS_NANOS[spikeIndex] / 1_000_000L, (spikeStartedNanos - soakStartedNanos) / 1_000_000L, (spikeCriticalNanos - soakStartedNanos) / 1_000_000L, (spikeWithdrawnNanos - soakStartedNanos) / 1_000_000L, (recoveredNanos - soakStartedNanos) / 1_000_000L, recoveryNanos / 1_000_000L));
						samples.add(captureEnduranceSample(recoveredNanos - soakStartedNanos, registeredBaseline, "spike" + (spikeIndex + 1) + "-recovered"));
						spikeIndex++;
						spikeSubmitted = false;
						spikeWithdrawn = false;
						spikeCritical = false;
						if (spikeIndex == SPIKE_OFFSETS_NANOS.length)
						{
							stopNavigation();
						}
					}
				}

				if (spikeSubmitted && !spikeWithdrawn && ((System.nanoTime() - spikeStartedNanos) >= SPIKE_HOLD_NANOS))
				{
					PhantomAssertions.assertTrue(spikeCritical, "Scheduler WARM spike did not reach CRITICAL during its 30-second hold.");
					withdrawWarmSpike(spikeIndex);
					spikeWithdrawn = true;
					spikeWithdrawnNanos = System.nanoTime();
				}
				if (spikeWithdrawn && ((System.nanoTime() - spikeWithdrawnNanos) > RECOVERY_LIMIT_NANOS))
				{
					throw new AssertionError("Scheduler WARM spike recovery exceeded 60 seconds.");
				}

				final long nextEvent = Math.min(nextPulseNanos, nextSampleNanos);
				final long waitNanos = nextEvent - System.nanoTime();
				if (waitNanos > 0)
				{
					LockSupport.parkNanos(Math.min(waitNanos, 50_000_000L));
				}
			}
			final long soakCompletedNanos = System.nanoTime();
			durationNanos = soakCompletedNanos - soakStartedNanos;
			samples.add(captureEnduranceSample(durationNanos, registeredBaseline, "endurance-end"));
			soakDelta = _admin.status().minus(soakBefore);
			final String soakCompletedAt = attributionWindow.markTimestamp();
			sqlAttribution = attributionWindow.capture(soakStartedAt, soakCompletedAt);
		}
		finally
		{
			attributionWindow.close();
		}
		_admin.requireProductionDatabaseIdle();
		final DbStatus measuredSoakDelta = Objects.requireNonNull(soakDelta);
		final SqlAttribution measuredSqlAttribution = Objects.requireNonNull(sqlAttribution);

		drainSchedulerToNormal();
		final SchedulerSnapshot finalScheduler = _scheduler.snapshot();
		final PhantomMetrics.ActivitySnapshot activity = _metrics.snapshot().activity();

		final long[] epochMinima = epochMinima(samples);
		final long rawHeapPeak = samples.stream().mapToLong(sample -> sample.jvm().heapUsed()).max().orElseThrow();
		final long maximumHeapCommitted = samples.stream().mapToLong(sample -> sample.jvm().heapCommitted()).max().orElseThrow();
		final long minimumHeapHeadroom = samples.stream().mapToLong(sample -> sample.jvm().heapMax() - sample.jvm().heapUsed()).min().orElseThrow();
		final int liveThreadPeak = samples.stream().mapToInt(sample -> sample.jvm().liveThreads()).max().orElseThrow();
		long maximumSampleGapNanos = 0;
		for (int index = 1; index < samples.size(); index++)
		{
			maximumSampleGapNanos = Math.max(maximumSampleGapNanos, samples.get(index).elapsedNanos() - samples.get(index - 1).elapsedNanos());
		}
		HikariSnapshot hikariPeak = hikariBaseline;
		for (EnduranceSample sample : samples)
		{
			hikariPeak = hikariPeak.maximum(sample.hikari());
		}
		stopScheduler();
		final JvmSnapshot finalSettled = settleHeap();
		final HikariSnapshot hikariFinal = HikariSnapshot.capture();

		context.record("endurance.durationMillis", durationNanos / 1_000_000L);
		context.record("endurance.samples", samples.size());
		context.record("endurance.maximumSampleGapMillis", maximumSampleGapNanos / 1_000_000L);
		context.record("endurance.spikes", spikes.stream().map(SpikeResult::compact).toList());
		context.record("endurance.navigationCycles", navigationCycles.stream().map(NavigationCycleResult::compact).toList());
		context.record("endurance.databaseDelta", measuredSoakDelta.compact());
		context.record("endurance.globalComSelect", measuredSoakDelta.select());
		context.record("endurance.globalDml", "insert=" + measuredSoakDelta.insert() + ",update=" + measuredSoakDelta.update() + ",delete=" + measuredSoakDelta.delete());
		context.record("endurance.sqlAttributionWindow", measuredSqlAttribution.windowCompact());
		context.record("endurance.generalLogLifecycle", attributionWindow.lifecycleCompact());
		context.record("endurance.connectCount", measuredSqlAttribution.connectCount());
		context.record("endurance.threadTimeAttribution", measuredSqlAttribution.threadTimeAttribution());
		context.record("endurance.maintenanceSqlCount", measuredSqlAttribution.maintenanceCount());
		context.record("endurance.maintenanceSelectCount", measuredSqlAttribution.maintenanceSelectCount());
		context.record("endurance.maintenanceSetCount", measuredSqlAttribution.maintenanceSetCount());
		context.record("endurance.maintenanceSqlCounts", measuredSqlAttribution.maintenanceStatements());
		context.record("endurance.applicationSqlCount", measuredSqlAttribution.applicationCount());
		context.record("endurance.applicationSqlCounts", measuredSqlAttribution.applicationStatements());
		context.record("endurance.unattributedSqlCount", measuredSqlAttribution.unattributedCount());
		context.record("endurance.unattributedSqlCounts", measuredSqlAttribution.unattributedStatements());
		context.record("endurance.unclassifiedSqlCount", measuredSqlAttribution.unclassifiedCount());
		context.record("endurance.unclassifiedSqlCounts", measuredSqlAttribution.unclassifiedStatements());
		context.record("endurance.scheduler", "pulsesStarted=" + activity.pulsesStarted() + ",pulsesCompleted=" + activity.pulsesCompleted() + ",overruns=" + activity.pulsesOverrun() + ",workDelivered=" + activity.workDelivered() + ",maxWorkPerPulse=" + maximumWorkPerPulse + ",workFailures=" + activity.workFailures() + ",readyBackpressure=" + activity.readyBackpressure());
		context.record("endurance.schedulerBounds", "registered=" + maximumRegistered + ",ready=" + maximumReady + ",due=" + maximumDue);
		context.record("endurance.pulseMaxExecutionNanos", maximumPulseExecutionNanos);
		context.record("endurance.maxSchedulingLatenessNanos", maximumSchedulingLatenessNanos);
		context.record("endurance.heapRegisteredBaseline", registeredBaseline.compact());
		context.record("endurance.heapEpochMinima", Arrays.toString(epochMinima));
		context.record("endurance.rawHeapPeak", rawHeapPeak);
		context.record("endurance.maximumHeapCommitted", maximumHeapCommitted);
		context.record("endurance.minimumHeapHeadroom", minimumHeapHeadroom);
		context.record("endurance.finalSettled", finalSettled.compact());
		context.record("endurance.gcDelta", finalSettled.gcDelta(registeredBaseline));
		context.record("endurance.liveThreadPeak", liveThreadPeak);
		context.record("endurance.hikariPeak", hikariPeak.compact());
		context.record("endurance.hikariFinal", hikariFinal.compact());

		if (measuredSqlAttribution.applicationCount() != 0)
		{
			throw new PhantomTestConfigurationException(BLOCKED_APPLICATION_TRAFFIC + ": dedicated test user issued application/table SQL " + measuredSqlAttribution.applicationStatements() + ".");
		}
		if (measuredSqlAttribution.unattributedCount() != 0)
		{
			throw new PhantomTestConfigurationException(BLOCKED_UNATTRIBUTED_STATEMENT + ": dedicated test user issued driver-shaped SQL without exact new-connection attribution " + measuredSqlAttribution.unattributedStatements() + ".");
		}
		if (measuredSqlAttribution.unclassifiedCount() != 0)
		{
			throw new PhantomTestConfigurationException(BLOCKED_UNCLASSIFIED_STATEMENT + ": dedicated test user issued unknown non-table SQL " + measuredSqlAttribution.unclassifiedStatements() + ".");
		}
		if ((measuredSoakDelta.insert() != 0) || (measuredSoakDelta.update() != 0) || (measuredSoakDelta.delete() != 0))
		{
			throw new PhantomTestConfigurationException(BLOCKED_APPLICATION_TRAFFIC + ": global scheduler/navigation DML must be zero but was " + measuredSoakDelta.compact() + ".");
		}
		PhantomAssertions.assertTrue(measuredSqlAttribution.connectCount() <= MAXIMUM_REPLACEMENT_CONNECTIONS, "Dedicated-user replacement Connect count exceeded 6.");
		PhantomAssertions.assertTrue(measuredSqlAttribution.maintenanceSelectCount() <= (2 * measuredSqlAttribution.connectCount()), "Dedicated-user maintenance SELECT count exceeded 2*Connects: " + measuredSqlAttribution.maintenanceStatements() + ".");
		PhantomAssertions.assertTrue(measuredSqlAttribution.maintenanceSetCount() <= (2 * measuredSqlAttribution.connectCount()), "Dedicated-user maintenance SET count exceeded 2*Connects: " + measuredSqlAttribution.maintenanceStatements() + ".");
		PhantomAssertions.assertTrue(measuredSqlAttribution.maintenanceStatements().getOrDefault("SET autocommit=1", 0) <= measuredSqlAttribution.connectCount(), "SET autocommit=1 count exceeded Connects.");
		PhantomAssertions.assertTrue(measuredSqlAttribution.maintenanceStatements().getOrDefault("SET character_set_results = NULL", 0) <= measuredSqlAttribution.connectCount(), "SET character_set_results = NULL count exceeded Connects.");
		PhantomAssertions.assertTrue(durationNanos >= ENDURANCE_NANOS, "Scheduler/navigation soak was shorter than 30 minutes.");
		PhantomAssertions.assertTrue(durationNanos < MAXIMUM_ENDURANCE_NANOS, "Scheduler/navigation soak reached 31 minutes.");
		PhantomAssertions.assertEquals(6, spikes.size(), "Scheduler did not complete exact six spikes.");
		PhantomAssertions.assertEquals(6, navigationCycles.size(), "Navigation did not complete exact six saturation cycles.");
		PhantomAssertions.assertTrue(_navigation == null, "Navigation service was not stopped after cycle 6.");
		assertSchedulerBounds(finalScheduler);
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.NORMAL, finalScheduler.overloadLevel(), "Scheduler final overload is not NORMAL.");
		PhantomAssertions.assertEquals(activity.pulsesStarted(), activity.pulsesCompleted(), "Scheduler pulse start/completion counters diverged.");
		PhantomAssertions.assertEquals(0L, activity.workFailures(), "Scheduler recorded work failures.");
		PhantomAssertions.assertEquals(0L, activity.readyBackpressure(), "Scheduler recorded ready backpressure.");
		PhantomAssertions.assertTrue(activity.pulsesOverrun() <= Math.max(5L, activity.pulsesCompleted() / 100L), "Scheduler pulse overrun ratio exceeded one percent.");
		PhantomAssertions.assertTrue(maximumWorkPerPulse <= SCHEDULER_BUDGET, "Scheduler maximum work per pulse exceeded 128.");
		PhantomAssertions.assertTrue((maximumRegistered <= SCALE) && (maximumReady <= SCALE) && (maximumDue <= SCALE), "Scheduler structural counts exceeded 10000.");
		for (int epoch = 0; epoch < epochMinima.length; epoch++)
		{
			PhantomAssertions.assertTrue(epochMinima[epoch] != Long.MAX_VALUE, "Missing JVM heap sample for 5-minute epoch " + epoch + ".");
		}

		PhantomAssertions.assertTrue(maximumHeapCommitted <= (registeredBaseline.heapCommitted() + SCHEDULER_COMMITTED_BUDGET), "Maximum committed heap exceeded registered baseline committed +256 MiB.");
		for (EnduranceSample sample : samples)
		{
			PhantomAssertions.assertTrue(sample.jvm().heapUsed() <= (sample.jvm().heapMax() - SCHEDULER_MINIMUM_HEADROOM), "Endurance heap sample had less than 512 MiB remaining headroom: elapsedNanos=" + sample.elapsedNanos() + ",used=" + sample.jvm().heapUsed() + ",max=" + sample.jvm().heapMax() + ".");
		}
		PhantomAssertions.assertTrue(liveThreadPeak <= (registeredBaseline.liveThreads() + 4), "Endurance live threads exceeded baseline +4.");
		PhantomAssertions.assertTrue(maximumSampleGapNanos <= 30_000_000_000L, "JVM/Hikari sampling gap exceeded 30 seconds.");
		assertHikariSoakSample(hikariPeak);
		assertHeapWithin(finalSettled, registeredBaseline, SCHEDULER_FINAL_BUDGET, "Scheduler/navigation final heap exceeded registered baseline +64 MiB.");
		PhantomAssertions.assertTrue(finalSettled.liveThreads() <= (registeredBaseline.liveThreads() + 2), "Final live threads exceeded registered baseline +2.");
		assertHikariSoakSample(hikariFinal);

	}

	private void drainReadySetup()
	{
		int pulses = 0;
		while (_scheduler.snapshot().ready() > 0)
		{
			final long before = _deliveredWork;
			_scheduler.pulse();
			PhantomAssertions.assertTrue((_deliveredWork - before) <= SCHEDULER_BUDGET, "Scheduler setup pulse exceeded 128 work items.");
			assertSchedulerBounds(_scheduler.snapshot());
			PhantomAssertions.assertTrue(++pulses <= 100, "Scheduler BACKGROUND setup did not drain within 100 pulses.");
		}
		_scheduler.pulse();
		PhantomAssertions.assertEquals(PhantomActivityOverloadLevel.NORMAL, _scheduler.snapshot().overloadLevel(), "Scheduler setup did not settle NORMAL.");
	}

	private void drainSchedulerToNormal()
	{
		final long deadline = System.nanoTime() + RECOVERY_LIMIT_NANOS;
		do
		{
			final long before = _deliveredWork;
			_scheduler.pulse();
			PhantomAssertions.assertTrue((_deliveredWork - before) <= SCHEDULER_BUDGET, "Scheduler final recovery pulse exceeded 128 work items.");
			assertSchedulerBounds(_scheduler.snapshot());
			if (_scheduler.snapshot().overloadLevel() == PhantomActivityOverloadLevel.NORMAL)
			{
				return;
			}
			LockSupport.parkNanos(PULSE_NANOS);
		}
		while (System.nanoTime() < deadline);
		throw new AssertionError("Scheduler final overload did not recover NORMAL within 60 seconds.");
	}

	private void submitWarmSpike(int spikeIndex)
	{
		final long sequence = (spikeIndex * 2L) + 1L;
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			final SignalStatus status = _scheduler.submitSignal(profileId, new PhantomRelevanceSignal(WARM_SOURCE, sequence, PhantomActivityState.WARM, SIGNAL_TTL_MILLIS)).status();
			PhantomAssertions.assertTrue((status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED), "Scheduler WARM spike signal was not accepted.");
		}
	}

	private void withdrawWarmSpike(int spikeIndex)
	{
		final long sequence = (spikeIndex * 2L) + 2L;
		for (long profileId = 1; profileId <= SCALE; profileId++)
		{
			final SignalStatus status = _scheduler.withdrawSignal(profileId, WARM_SOURCE, sequence).status();
			PhantomAssertions.assertTrue((status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED), "Scheduler WARM spike withdrawal was not accepted.");
		}
	}
	private NavigationCycleResult runNavigationCycle(int cycle, ManualDispatcher dispatcher, PhantomNavigationPolicy policy)
	{
		PhantomAssertions.assertTrue(_navigation != null, "Navigation service is not running for saturation cycle.");
		final long firstProfileId = 1L + ((cycle - 1L) * 300L);
		for (int offset = 0; offset < policy.maximumQueuedRequests(); offset++)
		{
			final long profileId = firstProfileId + offset;
			PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, _navigation.submit(navigationRequest(profileId, navigationDestination((int) profileId))).status(), "Bounded navigation request was not accepted.");
			assertNavigationBounds(_navigation.snapshot(), policy);
		}
		final PhantomNavigationService.ServiceSnapshot saturated = _navigation.snapshot();
		PhantomAssertions.assertEquals(256, saturated.queuedRequests(), "Navigation queue did not saturate at 256.");
		PhantomAssertions.assertEquals(2, saturated.currentWorkers(), "Navigation worker claims did not stop at two.");
		PhantomAssertions.assertEquals(2, dispatcher.size(), "Manual dispatcher did not retain exact two worker claims.");
		final var rejected = _navigation.submit(navigationRequest(firstProfileId + 256, navigationDestination((int) (firstProfileId + 256))));
		PhantomAssertions.assertEquals(SubmissionStatus.REJECTED, rejected.status(), "Navigation request beyond queue capacity was accepted.");
		PhantomAssertions.assertEquals(Status.QUEUE_BACKPRESSURE, rejected.immediateResult().status(), "Navigation saturation did not return QUEUE_BACKPRESSURE.");
		dispatcher.runAll();
		final PhantomNavigationService.ServiceSnapshot drained = _navigation.snapshot();
		PhantomAssertions.assertEquals(0, drained.queuedRequests(), "Navigation cycle retained queued requests after drain.");
		PhantomAssertions.assertEquals(0, drained.currentWorkers(), "Navigation cycle retained workers after drain.");
		PhantomAssertions.assertEquals(0, drained.activeRequests(), "Navigation cycle retained active requests after drain.");
		final long recoveryProfileId = firstProfileId + 257;
		PhantomAssertions.assertEquals(SubmissionStatus.ACCEPTED, _navigation.submit(navigationRequest(recoveryProfileId, navigationDestination((int) recoveryProfileId))).status(), "Navigation did not accept a new request after recovery.");
		dispatcher.runAll();
		final PhantomNavigationService.ServiceSnapshot recovered = _navigation.snapshot();
		assertNavigationBounds(recovered, policy);
		PhantomAssertions.assertEquals(0, recovered.queuedRequests(), "Navigation recovery request did not drain.");
		PhantomAssertions.assertEquals(0, recovered.currentWorkers(), "Navigation recovery retained workers.");
		PhantomAssertions.assertEquals(0, recovered.activeRequests(), "Navigation recovery retained active request ownership.");
		return new NavigationCycleResult(cycle, saturated.queuedRequests(), saturated.currentWorkers(), rejected.immediateResult().status(), recovered.cacheEntries(), recovered.completedResults());
	}

	private EnduranceSample captureEnduranceSample(long elapsedNanos, JvmSnapshot baseline, String reason) throws Exception
	{
		return new EnduranceSample(Math.max(0, elapsedNanos), reason, JvmSnapshot.capture(), HikariSnapshot.capture());
	}

	private static void assertHikariSoakSample(HikariSnapshot snapshot)
	{
		PhantomAssertions.assertTrue(snapshot.total() <= PhantomTestDatabaseGuard.MAX_TEST_POOL_SIZE, "Hikari total connections exceeded 4 during soak.");
		PhantomAssertions.assertEquals(0, snapshot.active(), "Hikari retained an active connection during scheduler/navigation soak.");
		PhantomAssertions.assertEquals(0, snapshot.awaiting(), "Hikari recorded a waiting connection during scheduler/navigation soak.");
	}

	private static long[] epochMinima(List<EnduranceSample> samples)
	{
		final long[] minima = new long[6];
		Arrays.fill(minima, Long.MAX_VALUE);
		for (EnduranceSample sample : samples)
		{
			final int epoch = (int) Math.min(5L, sample.elapsedNanos() / (5L * 60L * 1_000_000_000L));
			minima[epoch] = Math.min(minima[epoch], sample.jvm().heapUsed());
		}
		return minima;
	}

	private static PhantomNavigationRequest navigationRequest(long profileId, PhantomNavigationPoint destination)
	{
		return new PhantomNavigationRequest(profileId, NAVIGATION_ORIGIN, destination, 0, 1_000_000_000L, 100_000);
	}

	private static PhantomNavigationPoint navigationDestination(int index)
	{
		return new PhantomNavigationPoint(1000 + (index % 10_000), index / 10_000, 0, 0);
	}

	private static void assertNavigationBounds(PhantomNavigationService.ServiceSnapshot snapshot, PhantomNavigationPolicy policy)
	{
		PhantomAssertions.assertTrue(snapshot.queuedRequests() <= policy.maximumQueuedRequests(), "Navigation queue exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.currentWorkers() <= policy.maximumConcurrentPathfinders(), "Navigation workers exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.activeRequests() <= policy.maximumTrackedProfiles(), "Navigation active tracking exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.completedResults() <= policy.maximumTrackedProfiles(), "Navigation completed tracking exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.cooldownProfiles() <= policy.maximumTrackedProfiles(), "Navigation cooldown tracking exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.trackedProgressAttempts() <= policy.maximumTrackedProfiles(), "Navigation progress tracking exceeded policy.");
		PhantomAssertions.assertTrue(snapshot.cacheEntries() <= policy.maximumCacheEntries(), "Navigation cache exceeded policy.");
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
			PhantomAssertions.assertEquals(0L, countByAccounts("SELECT COUNT(*) FROM accounts WHERE login IN (" + placeholders + ")", start, end), "CP3 SHELL fixture created an account.");
			PhantomAssertions.assertEquals(0L, countByAccounts("SELECT COUNT(*) FROM characters WHERE account_name IN (" + placeholders + ")", start, end), "CP3 SHELL fixture created a character.");
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
			PhantomAssertions.assertEquals(0L, countRange(connection, "SELECT COUNT(*) FROM phantom_profiles WHERE profile_id BETWEEN ? AND ?", first, last), "CP3 profile cleanup left owned profiles.");
			PhantomAssertions.assertEquals(0L, countRange(connection, "SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id BETWEEN ? AND ?", first, last), "CP3 profile cleanup left owned components.");
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

	private void stopNavigation()
	{
		if (_navigation != null)
		{
			_navigation.beginStop();
			PhantomAssertions.assertTrue(_navigation.finishStop(), "Navigation service did not stop cleanly.");
			final PhantomNavigationService.ServiceSnapshot stopped = _navigation.snapshot();
			PhantomAssertions.assertEquals(0, stopped.queuedRequests(), "Navigation stop retained queued requests.");
			PhantomAssertions.assertEquals(0, stopped.currentWorkers(), "Navigation stop retained workers.");
			PhantomAssertions.assertEquals(0, stopped.activeRequests(), "Navigation stop retained active requests.");
			PhantomAssertions.assertEquals(0, stopped.cacheEntries(), "Navigation stop retained cache entries.");
			PhantomAssertions.assertEquals(0, stopped.completedResults(), "Navigation stop retained completed results.");
			PhantomAssertions.assertEquals(0, stopped.cooldownProfiles(), "Navigation stop retained cooldown profiles.");
			PhantomAssertions.assertEquals(0, stopped.trackedProgressAttempts(), "Navigation stop retained progress attempts.");
			_navigation = null;
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
			_thread = new Thread(this::sample, "PhantomGoal029CP3-HikariSampler");
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

	private record PopulationResult(String phase, DbStatus bootstrapDelta, DbStatus drainDelta, long ownershipCalls, int productivePulses, int totalPulses, int maximumOperations, JvmSnapshot baseline, JvmSnapshot loaded, JvmSnapshot recovered, HikariSnapshot hikariPeak, HikariSnapshot hikariAfterStop)
	{
		private String compact()
		{
			return "phase=" + phase + ",bootstrap=" + bootstrapDelta.compact() + ",drain=" + drainDelta.compact() + ",ownershipCalls=" + ownershipCalls + ",productivePulses=" + productivePulses + ",totalPulses=" + totalPulses + ",maxOperations=" + maximumOperations + ",baselineHeap=" + baseline.heapUsed() + ",loadedHeap=" + loaded.heapUsed() + ",recoveredHeap=" + recovered.heapUsed() + ",hikariPeak={" + hikariPeak.compact() + "},hikariAfterStop={" + hikariAfterStop.compact() + "}";
		}
	}

	private record SpikeResult(int spike, long scheduledOffsetMillis, long submittedOffsetMillis, long criticalOffsetMillis, long withdrawnOffsetMillis, long recoveredOffsetMillis, long recoveryMillis)
	{
		private String compact()
		{
			return "spike=" + spike + ",scheduledMs=" + scheduledOffsetMillis + ",submittedMs=" + submittedOffsetMillis + ",criticalMs=" + criticalOffsetMillis + ",withdrawnMs=" + withdrawnOffsetMillis + ",recoveredMs=" + recoveredOffsetMillis + ",recoveryMs=" + recoveryMillis;
		}
	}

	private record NavigationCycleResult(int cycle, int queuePeak, int workerPeak, Status extraStatus, int cacheEntries, int completedResults)
	{
		private String compact()
		{
			return "cycle=" + cycle + ",queuePeak=" + queuePeak + ",workerPeak=" + workerPeak + ",extra=" + extraStatus + ",drained=0/0/0,recoveryAccepted=true,cache=" + cacheEntries + ",completed=" + completedResults;
		}
	}

	private record EnduranceSample(long elapsedNanos, String reason, JvmSnapshot jvm, HikariSnapshot hikari)
	{
	}

	private record SqlAttribution(String startedAt, String completedAt, int connectCount, int maintenanceSelectCount, int maintenanceSetCount, Map<String, Integer> maintenanceStatements, Map<String, Integer> applicationStatements, Map<String, Integer> unattributedStatements, Map<String, Integer> unclassifiedStatements, List<String> threadTimeAttribution)
	{
		private SqlAttribution
		{
			maintenanceStatements = Collections.unmodifiableMap(new TreeMap<>(maintenanceStatements));
			applicationStatements = Collections.unmodifiableMap(new TreeMap<>(applicationStatements));
			unattributedStatements = Collections.unmodifiableMap(new TreeMap<>(unattributedStatements));
			unclassifiedStatements = Collections.unmodifiableMap(new TreeMap<>(unclassifiedStatements));
			threadTimeAttribution = List.copyOf(threadTimeAttribution);
		}

		private int maintenanceCount()
		{
			return count(maintenanceStatements);
		}

		private int applicationCount()
		{
			return count(applicationStatements);
		}

		private int unattributedCount()
		{
			return count(unattributedStatements);
		}

		private int unclassifiedCount()
		{
			return count(unclassifiedStatements);
		}

		private String windowCompact()
		{
			return "startedAt=" + startedAt + ",completedAt=" + completedAt;
		}

		private static int count(Map<String, Integer> statements)
		{
			return statements.values().stream().mapToInt(Integer::intValue).sum();
		}
	}

	private record GeneralLogRow(Instant eventTime, long threadId, String commandType, String userHost, String argument)
	{
	}

	private static final class ManualDispatcher implements PhantomNavigationService.Dispatcher
	{
		private final Deque<Runnable> _workers = new ArrayDeque<>();

		@Override
		public boolean dispatch(Runnable worker)
		{
			_workers.addLast(worker);
			return true;
		}

		private int size()
		{
			return _workers.size();
		}

		private void runAll()
		{
			while (!_workers.isEmpty())
			{
				_workers.removeFirst().run();
			}
		}
	}

	private static final class NavigationBackend implements PhantomNavigationBackend
	{
		private boolean _initialDirect;

		@Override
		public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
		{
			_initialDirect = true;
			return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_PATHFINDING, 1);
		}

		@Override
		public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
		{
			if (_initialDirect)
			{
				_initialDirect = false;
				return false;
			}
			return true;
		}

		@Override
		public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
		{
			return List.of(request.origin(), request.destination());
		}
	}

	private static final class SqlAttributionWindow implements AutoCloseable
	{
		private static final String LOG_VARIABLES_SQL = "SHOW GLOBAL VARIABLES WHERE Variable_name IN ('general_log','log_output')";
		private static final String TIMESTAMP_SQL = "SELECT DATE_FORMAT(CURRENT_TIMESTAMP(6), '%Y-%m-%d %H:%i:%s.%f')";
		private static final String ATTRIBUTION_SQL = "SELECT event_time, thread_id, command_type, user_host, argument FROM mysql.general_log WHERE event_time >= ? AND event_time <= ? AND command_type IN ('Connect','Query') AND SUBSTRING_INDEX(SUBSTRING_INDEX(user_host, ']', 1), '[', -1) = ? ORDER BY event_time, thread_id";
		private static final String APPLICATION_BOUNDARY_REGEX = ".*\\b(FROM|JOIN|INTO|INSERT|UPDATE|DELETE|REPLACE|MERGE|CREATE|ALTER|DROP|TRUNCATE|RENAME|GRANT|REVOKE|ANALYZE|OPTIMIZE|REPAIR|CALL|LOAD|LOCK|UNLOCK)\\b.*";

		private final Connection _connection;
		private String _savedGeneralLog;
		private String _savedLogOutput;
		private String _enabledGeneralLog;
		private String _enabledLogOutput;
		private String _restoredGeneralLog;
		private String _restoredLogOutput;
		private boolean _closed;

		private SqlAttributionWindow(Connection connection)
		{
			_connection = connection;
		}

		private static SqlAttributionWindow open(Connection connection) throws PhantomTestConfigurationException
		{
			final SqlAttributionWindow window = new SqlAttributionWindow(connection);
			window.enable();
			return window;
		}

		private void enable() throws PhantomTestConfigurationException
		{
			try
			{
				final Map<String, String> saved = logVariables();
				_savedGeneralLog = requireGeneralLog(saved);
				_savedLogOutput = requireLogOutput(saved);
				execute("SET GLOBAL log_output = 'TABLE'");
				execute("SET GLOBAL general_log = 'ON'");
				final Map<String, String> enabled = logVariables();
				_enabledGeneralLog = requireGeneralLog(enabled);
				_enabledLogOutput = requireLogOutput(enabled);
				if (!"ON".equals(_enabledGeneralLog) || !containsTable(_enabledLogOutput))
				{
					throw new SQLException("MariaDB general log did not enable with TABLE output.");
				}
				try (Statement statement = _connection.createStatement(); ResultSet ignored = statement.executeQuery("SELECT event_time FROM mysql.general_log WHERE 1 = 0"))
				{
					// Access itself is the required capability probe; no log rows are read or removed.
				}
			}
			catch (Throwable throwable)
			{
				Throwable failure = throwable;
				if ((_savedGeneralLog != null) && (_savedLogOutput != null))
				{
					try
					{
						restoreSavedValues();
					}
					catch (Throwable restoreFailure)
					{
						failure = combine(failure, restoreFailure);
					}
				}
				_closed = true;
				throw unavailable("general-log setup failed", failure);
			}
		}

		private String markTimestamp() throws PhantomTestConfigurationException
		{
			ensureOpen();
			try (Statement statement = _connection.createStatement(); ResultSet result = statement.executeQuery(TIMESTAMP_SQL))
			{
				if (!result.next())
				{
					throw new SQLException("MariaDB timestamp query returned no row.");
				}
				final String value = result.getString(1);
				if ((value == null) || value.isBlank())
				{
					throw new SQLException("MariaDB timestamp query returned no value.");
				}
				return value;
			}
			catch (SQLException e)
			{
				throw unavailable("soak timestamp could not be recorded", e);
			}
		}

		private SqlAttribution capture(String startedAt, String completedAt) throws PhantomTestConfigurationException
		{
			ensureOpen();
			final Map<Long, GeneralLogRow> connections = new HashMap<>();
			final Map<Long, Map<String, Integer>> connectionSetCounts = new HashMap<>();
			final Map<String, Integer> maintenance = new TreeMap<>();
			final Map<String, Integer> application = new TreeMap<>();
			final Map<String, Integer> unattributed = new TreeMap<>();
			final Map<String, Integer> unclassified = new TreeMap<>();
			final List<String> threadTimeAttribution = new ArrayList<>();
			int connectCount = 0;
			int maintenanceSelectCount = 0;
			int maintenanceSetCount = 0;
			try (PreparedStatement statement = _connection.prepareStatement(ATTRIBUTION_SQL))
			{
				statement.setString(1, startedAt);
				statement.setString(2, completedAt);
				statement.setString(3, PhantomTestDatabaseGuard.TARGET_USER);
				try (ResultSet result = statement.executeQuery())
				{
					while (result.next())
					{
						final GeneralLogRow row = new GeneralLogRow(result.getTimestamp(1).toInstant(), result.getLong(2), result.getString(3), result.getString(4), normalizeSql(result.getString(5)));
						if ("Connect".equals(row.commandType()))
						{
							connectCount++;
							connections.put(row.threadId(), row);
							threadTimeAttribution.add("connectThread=" + row.threadId() + ",connectAt=" + row.eventTime());
							continue;
						}
						if (!"Query".equals(row.commandType()))
						{
							unclassified.merge(row.argument(), 1, Integer::sum);
							continue;
						}

						final String normalized = row.argument();
						if (isApplicationSql(normalized))
						{
							application.merge(normalized, 1, Integer::sum);
							continue;
						}

						final boolean maintenanceSelect = isMaintenanceSelect(normalized);
						final boolean maintenanceSet = isMaintenanceSet(normalized);
						if (!maintenanceSelect && !maintenanceSet)
						{
							unclassified.merge(normalized, 1, Integer::sum);
							continue;
						}

						final GeneralLogRow connect = connections.get(row.threadId());
						if (connect == null)
						{
							unattributed.merge(normalized, 1, Integer::sum);
							threadTimeAttribution.add("unattributedThread=" + row.threadId() + ",connectAt=missing,queryAt=" + row.eventTime() + ",sql=" + normalized);
							continue;
						}
						final Duration delay = Duration.between(connect.eventTime(), row.eventTime());
						if (!row.eventTime().isAfter(connect.eventTime()) || (delay.compareTo(Duration.ofSeconds(5)) > 0))
						{
							unattributed.merge(normalized, 1, Integer::sum);
							threadTimeAttribution.add("unattributedThread=" + row.threadId() + ",connectAt=" + connect.eventTime() + ",queryAt=" + row.eventTime() + ",delayMillis=" + delay.toMillis() + ",sql=" + normalized);
							continue;
						}

						if (maintenanceSet)
						{
							final int perConnectionCount = connectionSetCounts.computeIfAbsent(row.threadId(), ignored -> new HashMap<>()).merge(normalized, 1, Integer::sum);
							if (perConnectionCount > 1)
							{
								unattributed.merge(normalized, 1, Integer::sum);
								threadTimeAttribution.add("duplicateSetThread=" + row.threadId() + ",connectAt=" + connect.eventTime() + ",queryAt=" + row.eventTime() + ",delayMillis=" + delay.toMillis() + ",sql=" + normalized);
								continue;
							}
							maintenanceSetCount++;
						}
						else
						{
							maintenanceSelectCount++;
						}
						maintenance.merge(normalized, 1, Integer::sum);
						threadTimeAttribution.add("attributedThread=" + row.threadId() + ",connectAt=" + connect.eventTime() + ",queryAt=" + row.eventTime() + ",delayMillis=" + delay.toMillis() + ",sql=" + normalized);
					}
				}
			}
			catch (SQLException e)
			{
				throw unavailable("dedicated-user Connect/Query rows could not be read", e);
			}
			return new SqlAttribution(startedAt, completedAt, connectCount, maintenanceSelectCount, maintenanceSetCount, maintenance, application, unattributed, unclassified, threadTimeAttribution);
		}

		private String lifecycleCompact()
		{
			if (!_closed)
			{
				throw new IllegalStateException("General-log lifecycle was requested before restoration.");
			}
			return "savedGeneralLog=" + _savedGeneralLog + ",savedLogOutput=" + _savedLogOutput + ",enabledGeneralLog=" + _enabledGeneralLog + ",enabledLogOutput=" + _enabledLogOutput + ",restoredGeneralLog=" + _restoredGeneralLog + ",restoredLogOutput=" + _restoredLogOutput;
		}

		@Override
		public void close() throws PhantomTestConfigurationException
		{
			if (_closed)
			{
				return;
			}
			try
			{
				restoreSavedValues();
			}
			catch (SQLException e)
			{
				throw unavailable("general-log settings could not be restored", e);
			}
			finally
			{
				_closed = true;
			}
		}

		private void restoreSavedValues() throws SQLException
		{
			SQLException failure = null;
			try
			{
				execute("SET GLOBAL general_log = 'OFF'");
			}
			catch (SQLException e)
			{
				failure = append(failure, e);
			}
			try
			{
				execute("SET GLOBAL log_output = '" + _savedLogOutput + "'");
			}
			catch (SQLException e)
			{
				failure = append(failure, e);
			}
			try
			{
				execute("SET GLOBAL general_log = '" + _savedGeneralLog + "'");
			}
			catch (SQLException e)
			{
				failure = append(failure, e);
			}
			try
			{
				final Map<String, String> restored = logVariables();
				_restoredGeneralLog = requireGeneralLog(restored);
				_restoredLogOutput = requireLogOutput(restored);
				if (!_savedGeneralLog.equals(_restoredGeneralLog) || !_savedLogOutput.equals(_restoredLogOutput))
				{
					throw new SQLException("MariaDB general-log settings differ after restoration.");
				}
			}
			catch (SQLException e)
			{
				failure = append(failure, e);
			}
			if (failure != null)
			{
				throw failure;
			}
		}

		private Map<String, String> logVariables() throws SQLException
		{
			final Map<String, String> values = new HashMap<>();
			try (Statement statement = _connection.createStatement(); ResultSet result = statement.executeQuery(LOG_VARIABLES_SQL))
			{
				while (result.next())
				{
					values.put(result.getString(1).toLowerCase(Locale.ROOT), result.getString(2));
				}
			}
			if (values.size() != 2)
			{
				throw new SQLException("Required MariaDB general-log variables are missing.");
			}
			return values;
		}

		private void execute(String sql) throws SQLException
		{
			try (Statement statement = _connection.createStatement())
			{
				statement.executeUpdate(sql);
			}
		}

		private static String requireGeneralLog(Map<String, String> variables) throws SQLException
		{
			final String value = variables.get("general_log");
			final String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
			if (!"ON".equals(normalized) && !"OFF".equals(normalized))
			{
				throw new SQLException("MariaDB general_log value is not ON/OFF.");
			}
			return normalized;
		}

		private static String requireLogOutput(Map<String, String> variables) throws SQLException
		{
			final String value = variables.get("log_output");
			final String normalized = value == null ? "" : value.replace(" ", "").toUpperCase(Locale.ROOT);
			if (!"NONE".equals(normalized) && !"FILE".equals(normalized) && !"TABLE".equals(normalized) && !"FILE,TABLE".equals(normalized) && !"TABLE,FILE".equals(normalized))
			{
				throw new SQLException("MariaDB log_output value is outside the bounded allowlist.");
			}
			return normalized;
		}

		private static boolean containsTable(String logOutput)
		{
			return Arrays.asList(logOutput.split(",")).contains("TABLE");
		}

		private static String normalizeSql(String sql)
		{
			return sql == null ? "" : sql.strip().replaceAll("\\s+", " ");
		}

		private static boolean isMaintenanceSelect(String sql)
		{
			String classified = sql;
			if (classified.startsWith("/* mysql-connector-j-"))
			{
				final int commentEnd = classified.indexOf("*/");
				if (commentEnd < 0)
				{
					return false;
				}
				classified = classified.substring(commentEnd + 2).stripLeading();
			}
			final String upper = classified.toUpperCase(Locale.ROOT);
			if (!upper.startsWith("SELECT "))
			{
				return false;
			}
			for (String expression : upper.substring("SELECT ".length()).split(","))
			{
				if (!expression.strip().matches("@@(?:SESSION\\.)?[A-Z0-9_]+(?:\\s+AS\\s+[A-Z0-9_]+)?"))
				{
					return false;
				}
			}
			return true;
		}

		private static boolean isMaintenanceSet(String sql)
		{
			return "SET autocommit=1".equals(sql) || "SET character_set_results = NULL".equals(sql);
		}

		private static boolean isApplicationSql(String sql)
		{
			final String upper = sql.toUpperCase(Locale.ROOT);
			return upper.matches(APPLICATION_BOUNDARY_REGEX) || upper.contains("L2JMOBIUSH5") || upper.contains("PHANTOM_") || upper.contains("`") || upper.contains(";");
		}

		private static SQLException append(SQLException first, SQLException next)
		{
			if (first == null)
			{
				return next;
			}
			first.addSuppressed(next);
			return first;
		}

		private static PhantomTestConfigurationException unavailable(String reason, Throwable cause)
		{
			return new PhantomTestConfigurationException(BLOCKED_SQL_ATTRIBUTION + ": " + reason + ".", cause);
		}

		private void ensureOpen() throws PhantomTestConfigurationException
		{
			if (_closed)
			{
				throw new PhantomTestConfigurationException(BLOCKED_SQL_ATTRIBUTION + ": general-log window is already closed.");
			}
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

		private SqlAttributionWindow openSqlAttribution() throws PhantomTestConfigurationException
		{
			return SqlAttributionWindow.open(_connection);
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
			if (!"mysql".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || (uri.getPort() != PhantomTestDatabaseGuard.TARGET_PORT) || !"/".equals(path) || (uri.getRawQuery() != null) || (uri.getFragment() != null) || (uri.getUserInfo() != null) || (authority == null) || authority.contains("@") || authority.contains(","))
			{
				throw invalidAdminUrl();
			}
		}

		private static PhantomTestConfigurationException invalidAdminUrl()
		{
			return new PhantomTestConfigurationException(BLOCKED_ADMIN_ENV + ": admin status URL must be exact credential-free jdbc:mysql://127.0.0.1:3308/ with no schema/query/fragment.");
		}
	}
}
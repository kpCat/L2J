/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog.PresetDefinition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.HistoricalPort;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Personality;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.ArchivedResult;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStore.StoredState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;

/** Focused deterministic Goal033 ecology contract gate. It does not touch a DB. */
public final class PhantomPopulationEcologyGoal033Suite implements PhantomTestSuite
{
	private static final long SEED = 33003300L;
	private static final long MINUTE_MILLIS = 60_000L;
	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	private PhantomPopulationCatalog _population;
	private PhantomSocialCatalog _social;
	private PhantomPopulationEcologyCatalog _ecology;

	@Override
	public String id()
	{
		return "population-ecology-goal033";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal033 deterministic seed changed.");
		_population = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
		_social = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		_ecology = PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), _population, _social);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-strict-catalog-config-and-codec", this::testCatalogConfigAndCodec);
		registry.add("02-low-discrepancy-and-human-independence", this::testAssignmentAndHumanIndependence);
		registry.add("03-pace-and-schedule-causal-time", this::testPaceAndSchedule);
		registry.add("04-bounded-calendar-restart-and-ongoing", this::testCalendarRestartAndOngoing);
		registry.add("05-safe-turnover-replacement-and-cap", this::testTurnoverAndCap);
		registry.add("06-personality-new-state-only", this::testPersonality);
		registry.add("07-production-architecture-static-fences", this::testStaticFences);
		registry.add("08-idle-calendar-catchup-reopens-schedule-fence", this::testIdleCalendarCatchupFence);
		registry.add("09-restart-existing-ecology-reopens-schedule-fences", this::testRestartExistingEcologyFences);
	}

	private void testCatalogConfigAndCodec(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(3, _ecology.presets().size(), "Ecology catalog does not contain three presets.");
		PhantomAssertions.assertEquals(6, _ecology.personalities().size(), "Ecology catalog does not contain six personalities.");
		PhantomAssertions.assertTrue(_ecology.requirePreset(Preset.FRESH).defaultWorldAgeDays() < _ecology.requirePreset(Preset.LIVING).defaultWorldAgeDays(), "FRESH is not younger than LIVING.");
		PhantomAssertions.assertTrue(_ecology.requirePreset(Preset.LIVING).defaultWorldAgeDays() < _ecology.requirePreset(Preset.MATURE).defaultWorldAgeDays(), "LIVING is not younger than MATURE.");
		final Path ecologyPath = context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml");
		PhantomAssertions.assertEquals(_ecology.hash(), PhantomPopulationEcologyCatalog.load(ecologyPath, _population, _social).hash(), "Ecology authority hash changed across reload.");

		final PhantomPlayersConfig.Settings shipped = PhantomPlayersConfig.read(context.moduleRoot().resolve("dist/game/config/Custom/PhantomPlayers.ini"));
		final PhantomPlayersConfig.Settings local = PhantomPlayersConfig.read(context.moduleRoot().resolve("docs/phantoms/examples/PhantomPlayers.local-play.ini"));
		PhantomAssertions.assertFalse(shipped.enabled(), "Shipped Phantom system is enabled.");
		PhantomAssertions.assertFalse(shipped.ecologyEnabled(), "Shipped Phantom ecology is enabled.");
		PhantomAssertions.assertTrue(local.enabled() && local.ecologyEnabled(), "Local-play ecology is not enabled.");
		PhantomAssertions.assertEquals("LIVING", local.ecologyPreset(), "Local-play preset is not LIVING.");

		final Path invalidConfig = Files.createTempFile("phantom-goal033-invalid-", ".ini");
		final Path invalidCatalog = Files.createTempFile("phantom-goal033-invalid-", ".xml");
		try
		{
			Files.writeString(invalidConfig, enabledConfig("UNKNOWN"), StandardCharsets.UTF_8);
			PhantomAssertions.assertFalse(PhantomPlayersConfig.read(invalidConfig).enabled(), "Unknown ecology preset did not fail closed.");
			Files.writeString(invalidCatalog, Files.readString(ecologyPath, StandardCharsets.UTF_8).replace("<schedule id=\"evening\" weight=\"50\"/>", "<schedule id=\"unknown\" weight=\"50\"/>"), StandardCharsets.UTF_8);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomPopulationEcologyCatalog.load(invalidCatalog, _population, _social), "Unknown ecology schedule reference was accepted.");
		}
		finally
		{
			Files.deleteIfExists(invalidConfig);
			Files.deleteIfExists(invalidCatalog);
		}

		final PhantomPopulationEcologyState assigned = _ecology.assign(Preset.LIVING, 3, 17, SEED, minute(CREATED), -1, 0, null);
		final PhantomPopulationEcologyStateCodec codec = new PhantomPopulationEcologyStateCodec();
		PhantomAssertions.assertEquals(assigned, codec.decode(codec.encode(assigned)), "population.ecology v1 codec did not round-trip.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(java.util.Arrays.copyOf(codec.encode(assigned), codec.encode(assigned).length + 1)), "Ecology codec accepted trailing bytes.");
		context.record("goal033.catalogHash", _ecology.hash());
		context.record("goal033.codecBytes", codec.encode(assigned).length);
	}

	private void testAssignmentAndHumanIndependence(PhantomTestContext context)
	{
		final long now = minute(Instant.parse("2026-09-01T00:00:00Z"));
		for (Preset preset : Preset.values())
		{
			final PresetDefinition definition = _ecology.requirePreset(preset);
			final Map<Pace, Integer> paces = new EnumMap<>(Pace.class);
			final Map<Personality, Integer> personalities = new EnumMap<>(Personality.class);
			final Map<String, Integer> schedules = new TreeMap<>();
			final Map<Integer, Integer> ages = new TreeMap<>();
			final List<PhantomPopulationEcologyState> firstTen = new ArrayList<>();
			for (long ordinal = 1; ordinal <= 100; ordinal++)
			{
				final PhantomPopulationEcologyState first = _ecology.assign(preset, 7, ordinal, SEED, now, -1, 0, null);
				final PhantomPopulationEcologyState restarted = _ecology.assign(preset, 7, ordinal, SEED, now, -1, 0, null);
				PhantomAssertions.assertEquals(first, restarted, "Ecology assignment changed across deterministic restart.");
				paces.merge(first.pace(), 1, Integer::sum);
				personalities.merge(first.personality(), 1, Integer::sum);
				schedules.merge(first.scheduleTemplate(), 1, Integer::sum);
				ages.merge((int) ((now - first.virtualJoinEpochMinute()) / 1440L), 1, Integer::sum);
				if (ordinal <= 10)
				{
					firstTen.add(first);
				}
			}
			definition.paces().forEach(value -> PhantomAssertions.assertEquals(value.weight(), paces.get(value.pace()), "Pace 100-slot allocation differs from configured weight."));
			definition.personalities().forEach(value -> PhantomAssertions.assertEquals(value.weight(), personalities.get(value.personality()), "Personality 100-slot allocation differs from configured weight."));
			definition.schedules().forEach(value -> PhantomAssertions.assertEquals(value.weight(), schedules.get(value.id()), "Schedule 100-slot allocation differs from configured weight."));
			PhantomAssertions.assertTrue(firstTen.stream().map(PhantomPopulationEcologyState::pace).distinct().count() >= 2, "Small population pace assignment lacks diversity.");
			PhantomAssertions.assertTrue(firstTen.stream().map(PhantomPopulationEcologyState::personality).distinct().count() >= 3, "Small population personality assignment lacks diversity.");
			PhantomAssertions.assertTrue(firstTen.stream().map(PhantomPopulationEcologyState::scheduleTemplate).distinct().count() >= 2, "Small population schedule assignment lacks diversity.");
			context.record("goal033." + preset.name().toLowerCase() + ".pace", paces);
			context.record("goal033." + preset.name().toLowerCase() + ".personality", personalities);
			context.record("goal033." + preset.name().toLowerCase() + ".schedule", schedules);
			context.record("goal033." + preset.name().toLowerCase() + ".ageDays", ages);
		}

		final List<String> canonical = assignments(Preset.LIVING, now);
		for (int humanLevel : List.of(1, 20, 40, 60, 85))
		{
			PhantomAssertions.assertEquals(canonical, assignments(Preset.LIVING, now), "Human level fixture changed independent ecology at level " + humanLevel + '.');
		}
		context.record("goal033.humanFixtures", "1,20,40,60,85 identical");
	}

	private void testPaceAndSchedule(PhantomTestContext context) throws Exception
	{
		final Map<Pace, Long> selected = new EnumMap<>(Pace.class);
		for (Pace pace : Pace.values())
		{
			final PhantomPopulationEcologyState state = findPace(pace);
			long productive = 0;
			for (long block = 0; block < 10_000; block++)
			{
				productive += _ecology.productive(state, block) ? 1 : 0;
			}
			PhantomAssertions.assertEquals((long) state.productiveShareBasisPoints(), productive, "Pace share is not exact over its deterministic cycle.");
			selected.put(pace, productive);
		}
		PhantomAssertions.assertTrue(selected.get(Pace.CASUAL) < selected.get(Pace.REGULAR) && selected.get(Pace.REGULAR) < selected.get(Pace.FAST) && selected.get(Pace.FAST) < selected.get(Pace.OUTLIER), "Pace productive exposure is not strictly ordered.");

		final EcologyMemoryStore store = new EcologyMemoryStore(null);
		final HistoricalMemoryPort historical = new HistoricalMemoryPort();
		final PhantomPopulationEcologyService service = service(store, historical, new AtomicBoolean(), new AtomicReference<>(""), new PhantomPopulationTestDoubles.MutableClock(CREATED), Preset.LIVING, 0, 10);
		final long monday = minute(Instant.parse("2026-01-05T00:00:00Z"));
		final PhantomPopulationEcologyState calendar = stateAt(monday, Pace.OUTLIER, 9500, "evening");
		final var window = service.nextProductiveWindow(calendar, 0, monday + 1440);
		PhantomAssertions.assertTrue(window.productive() && (window.startEpochMinute() > monday), "Schedule-aware search did not skip sleeping time.");
		final var evaluation = _population.evaluate("evening", Instant.ofEpochSecond(window.startEpochMinute() * 60L), ZoneOffset.UTC, 0);
		PhantomAssertions.assertTrue((evaluation.state() == PhantomActivityState.ACTIVE) || (evaluation.state() == PhantomActivityState.BACKGROUND), "Productive window escaped ACTIVE/BACKGROUND schedule authority.");

		final Map<Preset, Simulation> simulations = new EnumMap<>(Preset.class);
		for (Preset preset : Preset.values())
		{
			simulations.put(preset, simulate(preset, 128));
		}
		PhantomAssertions.assertTrue(simulations.get(Preset.FRESH).medianAgeMinutes() < simulations.get(Preset.LIVING).medianAgeMinutes(), "FRESH median virtual age is not below LIVING.");
		PhantomAssertions.assertTrue(simulations.get(Preset.LIVING).medianAgeMinutes() < simulations.get(Preset.MATURE).medianAgeMinutes(), "LIVING median virtual age is not below MATURE.");
		PhantomAssertions.assertTrue(simulations.get(Preset.FRESH).medianProductiveMinutes() < simulations.get(Preset.LIVING).medianProductiveMinutes(), "FRESH productive history is not below LIVING.");
		PhantomAssertions.assertTrue(simulations.get(Preset.LIVING).medianProductiveMinutes() < simulations.get(Preset.MATURE).medianProductiveMinutes(), "LIVING productive history is not below MATURE.");
		PhantomAssertions.assertTrue(simulations.get(Preset.MATURE).newcomers() > 0, "MATURE lost its newcomer floor.");
		context.record("goal033.paceCycle", selected);
		context.record("goal033.simulation128", simulations);
		assertNoRewardShortcut(context);
	}

	private void testCalendarRestartAndOngoing(PhantomTestContext context)
	{
		final Reconciliation uninterrupted = reconcile(false);
		final Reconciliation restarted = reconcile(true);
		PhantomAssertions.assertEquals(uninterrupted.cursor(), restarted.cursor(), "Restart changed ecology calendar cursor.");
		PhantomAssertions.assertEquals(uninterrupted.productiveMinutes(), restarted.productiveMinutes(), "Restart changed causal productive minutes.");
		PhantomAssertions.assertEquals(uninterrupted.requestIds(), restarted.requestIds(), "Restart changed deterministic Goal033A request identities.");
		PhantomAssertions.assertTrue(uninterrupted.productiveMinutes() > 0 && uninterrupted.productiveMinutes() < uninterrupted.elapsedMinutes(), "Calendar reconciliation farmed no time or all 24/7 time.");
		PhantomAssertions.assertTrue(uninterrupted.pendingFenceObserved(), "Normal scheduling was not fenced during an owned Goal033A request.");
		PhantomAssertions.assertTrue((uninterrupted.maximumProfilesPerPulse() <= _ecology.limits().maximumProfilesPerPulse()) && (uninterrupted.maximumIntervalsPerPulse() <= _ecology.limits().maximumIntervalsPerPulse()), "Ecology pulse exceeded configured bounds.");
		PhantomAssertions.assertTrue(uninterrupted.ongoingProductiveMinutes() > 0, "Future offline schedule reconciliation did not use causal productive time.");
		PhantomAssertions.assertEquals(uninterrupted.beforeMaterializedMinutes(), uninterrupted.afterMaterializedMinutes(), "Materialized normal work overlapped historical catch-up.");
		context.record("goal033.restartProductiveMinutes", restarted.productiveMinutes());
		context.record("goal033.restartRequests", restarted.requestIds().size());
		context.record("goal033.maximumPulse", restarted.maximumProfilesPerPulse() + "/" + restarted.maximumIntervalsPerPulse());
	}

	private void testTurnoverAndCap(PhantomTestContext context)
	{
		final PhantomPopulationTestDoubles.MemoryStore populationStore = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final PhantomPopulationTestDoubles.Ownership ownership = new PhantomPopulationTestDoubles.Ownership();
		final EcologyMemoryStore ecologyStore = new EcologyMemoryStore(populationStore);
		final HistoricalMemoryPort historical = new HistoricalMemoryPort();
		final AtomicBoolean materialized = new AtomicBoolean();
		final AtomicReference<String> safety = new AtomicReference<>("party");
		final PhantomPopulationTestDoubles.MutableClock clock = new PhantomPopulationTestDoubles.MutableClock(CREATED);
		PhantomPopulationEcologyService ecologyService = service(ecologyStore, historical, materialized, safety, clock, Preset.FRESH, 0, 1);
		final PhantomPopulationEcologyService firstEcology = ecologyService;
		PhantomPopulationManager manager = new PhantomPopulationManager(populationStore, _population, null, ownership, clock, ZoneOffset.UTC, 1, 0, 16, 4, 2, 64);
		manager.installEcology(ecologyService);
		PhantomAssertions.assertTrue(manager.start(), "Synthetic ecology PopulationManager did not start.");
		final long veteran = populationStore.loadManagedAfter(0, 16).get(0).profile().profileId();
		advanceToReady(manager, veteran);
		manager.onPulse();
		final long eligible = ecologyStore.require(veteran).state().turnoverEligibleEpochMinute();
		clock.set(Instant.ofEpochSecond((eligible + 1) * 60L));
		pulseUntil(manager, () -> firstEcology.permitsScheduling(veteran), 10_000, "Veteran did not converge causally before turnover safety checks.");
		PhantomAssertions.assertEquals(0, firstEcology.snapshot().archived(), "Party-blocked profile archived.");
		materialized.set(true);
		safety.set("");
		for (int index = 0; index < 8; index++)
		{
			manager.onPulse();
		}
		PhantomAssertions.assertEquals(0, firstEcology.snapshot().archived(), "Materialized profile archived.");
		materialized.set(false);
		pulseUntil(manager, () -> firstEcology.snapshot().archived() == 1, 256, "Safe eligible veteran did not archive.");
		pulseUntil(manager, () -> populationStore.size() == 2, 256, "PopulationManager did not create a replacement shell.");
		final long replacement = populationStore.loadManagedAfter(veteran, 16).get(0).profile().profileId();
		PhantomAssertions.assertEquals(Disposition.ARCHIVED, ecologyStore.require(veteran).state().disposition(), "Veteran ecology disposition was not durable ARCHIVED.");
		PhantomAssertions.assertEquals(PhantomPopulationState.State.RETIRED, populationStore.reload(veteran).state().state(), "Archived population was not durable RETIRED.");
		PhantomAssertions.assertEquals(veteran, ecologyStore.require(replacement).state().replacesProfileId(), "Replacement did not retain archived predecessor identity.");
		PhantomAssertions.assertEquals("archive_limit", firstEcology.snapshot().turnoverPaused(), "Archive cap did not pause turnover.");
		stop(manager);

		ecologyService = service(ecologyStore, historical, materialized, safety, clock, Preset.MATURE, -1, 1);
		manager = new PhantomPopulationManager(populationStore, _population, null, ownership, clock, ZoneOffset.UTC, 1, 0, 16, 4, 2, 64);
		manager.installEcology(ecologyService);
		PhantomAssertions.assertTrue(manager.start(), "Restarted synthetic ecology PopulationManager did not start.");
		for (int index = 0; index < 12; index++)
		{
			manager.onPulse();
		}
		PhantomAssertions.assertEquals(2, populationStore.size(), "Archived profile was returned or duplicated after restart.");
		PhantomAssertions.assertEquals(Disposition.ARCHIVED, ecologyStore.require(veteran).state().disposition(), "Restart lost archived disposition.");
		PhantomAssertions.assertEquals(Preset.FRESH, ecologyStore.require(veteran).state().preset(), "Config change rerolled existing ecology assignment.");
		stop(manager);
		context.record("goal033.turnover", "managed=1 archived=1 replacement=" + replacement);
	}

	private void testPersonality(PhantomTestContext context)
	{
		final long now = minute(CREATED);
		PhantomPopulationEcologyState assignment = null;
		for (long ordinal = 1; ordinal <= 100; ordinal++)
		{
			final PhantomPopulationEcologyState candidate = _ecology.assign(Preset.LIVING, 1, ordinal, SEED, now, 0, 0, null);
			if (candidate.personality() == Personality.SOCIAL)
			{
				assignment = candidate;
				break;
			}
		}
		PhantomAssertions.assertTrue(assignment != null, "LIVING cycle did not contain SOCIAL personality.");
		final long profileId = 91;
		final Map<Integer, Integer> expected = _ecology.personalityTraits(assignment, profileId);
		PhantomAssertions.assertEquals(assignment.initialSocialTraits(), expected, "Durable ecology assignment did not freeze the initial Social trait vector.");
		PhantomAssertions.assertEquals(6, expected.size(), "Ecology personality did not initialize every Social trait.");
		PhantomAssertions.assertTrue(expected.values().stream().allMatch(value -> (value >= PhantomSocialModel.MIN_VALUE) && (value <= PhantomSocialModel.MAX_VALUE)), "Ecology personality escaped Social trait bounds.");

		final PhantomSocialTestDoubles.MemoryStore socialStore = new PhantomSocialTestDoubles.MemoryStore();
		socialStore.addProfile(profileId);
		PhantomSocialService service = new PhantomSocialService(_social, socialStore, 1, 16);
		PhantomAssertions.assertTrue(service.start(), "Social service did not start for ecology personality test.");
		service.installPersonalityInitializer(id -> expected);
		final var initialized = service.ensurePersonality(profileId);
		PhantomAssertions.assertTrue(initialized.available(), "Ecology-influenced Social personality was not created.");
		final Map<String, Integer> first = initialized.value().traits();
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Social service did not stop after ecology initialization.");

		service = new PhantomSocialService(_social, socialStore, 1, 16);
		PhantomAssertions.assertTrue(service.start(), "Restarted Social service did not start.");
		service.installPersonalityInitializer(id -> Map.of(1, -9999, 2, -9999, 3, -9999, 4, -9999, 5, -9999, 6, -9999));
		final var restarted = service.ensurePersonality(profileId);
		PhantomAssertions.assertTrue(restarted.available(), "Existing durable Social personality was not available after restart.");
		PhantomAssertions.assertEquals(first, restarted.value().traits(), "Existing durable Social state rerolled after initializer/config change.");
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Restarted Social service did not stop.");
		context.record("goal033.socialTraits", first);
	}

	private void testStaticFences(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final String service = Files.readString(root.resolve("java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationEcologyService.java"), StandardCharsets.UTF_8);
		final String system = Files.readString(root.resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"), StandardCharsets.UTF_8);
		final String manager = Files.readString(root.resolve("java/org/l2jmobius/gameserver/phantoms/population/PhantomPopulationManager.java"), StandardCharsets.UTF_8);
		final String progression = Files.readString(root.resolve("java/org/l2jmobius/gameserver/phantoms/progression/PhantomProgressionModel.java"), StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(service.contains("onPopulationPulse()") && manager.contains("_ecology.onPopulationPulse();"), "Ecology is not driven by the existing PopulationManager pulse.");
		PhantomAssertions.assertTrue(manager.contains("READY_RELOAD") && manager.contains("_ownership.reload(action.profileId())"), "Decision runtime is not reloaded before a completed ecology fence reopens scheduling.");
		PhantomAssertions.assertFalse(service.contains("ScheduledExecutor") || service.contains("new Thread") || service.contains("TimerTask") || service.contains("CompletableFuture"), "Ecology added an independent scheduler/thread.");
		PhantomAssertions.assertTrue(system.contains("new PhantomPopulationEcologyStore(productionProfiles)") && system.contains("_historicalBackgroundService"), "Production ecology is not composed over Goal033A and profile components.");
		PhantomAssertions.assertTrue(system.contains("installPersonalityInitializer(_populationEcology::initialPersonalityTraits)"), "Production Social initialization seam is not ecology-aware.");
		PhantomAssertions.assertTrue(progression.contains("CANONICAL_QUEST_REQUIRED"), "Goal036 profession boundary disappeared.");
		context.record("goal033.productionComposition", "population pulse + Goal033A + Social initializer");
	}

	private void testIdleCalendarCatchupFence(PhantomTestContext context)
	{
		final Instant now = Instant.parse("2026-01-05T03:01:00Z");
		final long previousMinute = minute(now) - 1;
		final PhantomPopulationTestDoubles.MemoryStore populationStore = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final ManagedSnapshot population = populationStore.seedReady(1, 1);
		final EcologyMemoryStore store = new EcologyMemoryStore(null);
		store.insert(1, stateAt(previousMinute, Pace.CASUAL, 1, population.state().scheduleTemplate()));
		final PhantomPopulationTestDoubles.MutableClock clock = new PhantomPopulationTestDoubles.MutableClock(now);
		final PhantomPopulationEcologyService service = service(store, new HistoricalMemoryPort(), new AtomicBoolean(), new AtomicReference<>(""), clock, Preset.LIVING, 0, 10);
		final AtomicInteger fenceChanges = new AtomicInteger();
		service.installRuntime(id -> id == 1 ? Optional.of(population) : Optional.empty(), new PhantomPopulationEcologyService.PopulationEvents()
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
				PhantomAssertions.assertEquals(1L, profileId, "Idle calendar catch-up reported the wrong profile.");
				fenceChanges.incrementAndGet();
			}
		});
		service.register(population);
		PhantomAssertions.assertFalse(service.permitsScheduling(1), "Stale idle ecology cursor unexpectedly permitted scheduling.");
		service.onPopulationPulse();
		PhantomAssertions.assertTrue(service.permitsScheduling(1), "Idle ecology calendar did not catch up to the current minute.");
		PhantomAssertions.assertEquals(1, fenceChanges.get(), "Idle ecology permit transition did not reopen the population schedule fence exactly once.");
		context.record("goal034.idleCalendarFenceChanges", fenceChanges.get());
	}

	private void testRestartExistingEcologyFences(PhantomTestContext context)
	{
		final Instant now = Instant.parse("2026-01-05T20:30:00Z");
		final long nowMinute = minute(now);
		final PhantomPopulationTestDoubles.MemoryStore populationStore = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final EcologyMemoryStore store = new EcologyMemoryStore(null);
		final List<ManagedSnapshot> populations = new ArrayList<>();
		final List<Long> eligible = new ArrayList<>();
		for (long profileId = 1; profileId <= 10; profileId++)
		{
			final ManagedSnapshot population = populationStore.seedReady(profileId, Math.toIntExact(profileId));
			populations.add(population);
			store.insert(profileId, stateAt(nowMinute, Pace.OUTLIER, 10_000, population.state().scheduleTemplate()));
			if (_population.evaluate(population.state().scheduleTemplate(), now, ZoneOffset.UTC, population.state().schedulePhaseMinutes()).state() == PhantomActivityState.ACTIVE)
			{
				eligible.add(profileId);
			}
		}
		PhantomAssertions.assertTrue(eligible.size() >= 5, "Restart fixture does not contain at least five ACTIVE durable schedules.");

		final PhantomPopulationTestDoubles.MutableClock clock = new PhantomPopulationTestDoubles.MutableClock(now);
		final PhantomPopulationEcologyService service = service(store, new HistoricalMemoryPort(), new AtomicBoolean(), new AtomicReference<>(""), clock, Preset.LIVING, 0, 10);
		final List<Long> fenceChanges = new ArrayList<>();
		final AtomicInteger reconciliations = new AtomicInteger();
		service.installRuntime(profileId -> populations.stream().filter(population -> population.profile().profileId() == profileId).findFirst(), new PhantomPopulationEcologyService.PopulationEvents()
		{
			@Override
			public void requestArchive(long profileId)
			{
			}

			@Override
			public void reconcilePopulation()
			{
				reconciliations.incrementAndGet();
			}

			@Override
			public void ecologyFenceChanged(long profileId)
			{
				fenceChanges.add(profileId);
			}
		});
		populations.forEach(service::register);
		PhantomAssertions.assertFalse(service.inventoryReady(), "Restart ecology inventory was ready before durable rows loaded.");
		for (int pulse = 0; (pulse < 32) && !service.inventoryReady(); pulse++)
		{
			service.onPopulationPulse();
		}
		PhantomAssertions.assertTrue(service.inventoryReady(), "Restart ecology inventory did not load through bounded population pulses.");
		for (long profileId : eligible)
		{
			PhantomAssertions.assertTrue(service.permitsScheduling(profileId), "Loaded eligible ecology row did not permit scheduling: " + profileId);
		}
		final List<Long> distinctFenceChanges = fenceChanges.stream().distinct().sorted().toList();
		PhantomAssertions.assertEquals(eligible, distinctFenceChanges, "Restart restore did not reopen every eligible READY schedule fence.");
		final int changesAfterRestore = fenceChanges.size();
		service.onPopulationPulse();
		PhantomAssertions.assertEquals(changesAfterRestore, fenceChanges.size(), "No-op ecology pulse repeated scheduling-permission refreshes.");

		clock.set(now.plusSeconds(60));
		for (int pulse = 0; (pulse < 32) && eligible.stream().anyMatch(profileId -> fenceChanges.stream().filter(profileId::equals).count() < 2); pulse++)
		{
			service.onPopulationPulse();
		}
		for (long profileId : eligible)
		{
			PhantomAssertions.assertTrue(fenceChanges.stream().filter(profile -> profile == profileId).count() >= 2, "Beginning catch-up did not publish a closing schedule-fence edge: " + profileId);
		}
		for (int pulse = 0; (pulse < 32) && eligible.stream().anyMatch(profileId -> fenceChanges.stream().filter(profileId::equals).count() < 3); pulse++)
		{
			service.onPopulationPulse();
		}
		for (long profileId : eligible)
		{
			PhantomAssertions.assertTrue(service.permitsScheduling(profileId), "Completing catch-up did not reopen the schedule fence: " + profileId);
			PhantomAssertions.assertEquals(3L, fenceChanges.stream().filter(profile -> profile == profileId).count(), "Catch-up did not publish exactly one close/reopen edge pair: " + profileId);
		}
		PhantomAssertions.assertEquals(changesAfterRestore * 3, fenceChanges.size(), "Catch-up completion did not publish exactly one reopening permission edge per eligible profile.");
		final int changesAfterCatchup = fenceChanges.size();
		service.onPopulationPulse();
		PhantomAssertions.assertEquals(changesAfterCatchup, fenceChanges.size(), "Post-catch-up no-op pulse repeated scheduling-permission refreshes.");
		context.record("goal034.restartExistingFenceChanges", fenceChanges);
		context.record("goal034.restartExistingReconciliations", reconciliations.get());
	}

	private Reconciliation reconcile(boolean restart)
	{
		final PhantomPopulationTestDoubles.MemoryStore populationStore = new PhantomPopulationTestDoubles.MemoryStore(_population.hash());
		final ManagedSnapshot population = populationStore.seedReady(1, 1);
		final EcologyMemoryStore store = new EcologyMemoryStore(null);
		final HistoricalMemoryPort historical = new HistoricalMemoryPort();
		final AtomicBoolean materialized = new AtomicBoolean();
		final AtomicReference<String> safety = new AtomicReference<>("");
		final PhantomPopulationTestDoubles.MutableClock clock = new PhantomPopulationTestDoubles.MutableClock(Instant.parse("2026-01-03T00:00:00Z"));
		PhantomPopulationEcologyService service = service(store, historical, materialized, safety, clock, Preset.LIVING, 0, 10);
		service.installRuntime(id -> id == 1 ? Optional.of(population) : Optional.empty(), noEvents());
		service.register(population);
		boolean pendingFence = false;
		for (int pulse = 0; pulse < 10_000 && !service.permitsScheduling(1); pulse++)
		{
			service.onPopulationPulse();
			pendingFence |= store.optional(1).map(StoredState::state).map(PhantomPopulationEcologyState::requestPending).orElse(false) && !service.permitsScheduling(1);
			if (restart && store.optional(1).map(StoredState::state).map(PhantomPopulationEcologyState::requestPending).orElse(false))
			{
				service = service(store, historical, materialized, safety, clock, Preset.MATURE, -1, 10);
				service.installRuntime(id -> id == 1 ? Optional.of(population) : Optional.empty(), noEvents());
				service.register(population);
				restart = false;
			}
		}
		PhantomAssertions.assertTrue(service.permitsScheduling(1), "Two-day ecology reconciliation did not converge.");
		final long initialMinutes = historical.advancedMinutes();
		clock.set(Instant.parse("2026-01-04T23:59:00Z"));
		for (int pulse = 0; pulse < 10_000 && !service.permitsScheduling(1); pulse++)
		{
			service.onPopulationPulse();
		}
		PhantomAssertions.assertTrue(service.permitsScheduling(1), "Ongoing future reconciliation did not converge.");
		final long ongoing = historical.advancedMinutes() - initialMinutes;
		final long beforeMaterialized = historical.advancedMinutes();
		materialized.set(true);
		clock.set(Instant.parse("2026-01-05T12:00:00Z"));
		service.onPopulationPulse();
		final long afterMaterialized = historical.advancedMinutes();
		final var snapshot = service.snapshot();
		return new Reconciliation(store.require(1).state().calendarCursorEpochMinute(), initialMinutes, minute(Instant.parse("2026-01-03T00:00:00Z")) - minute(CREATED), List.copyOf(historical.requestIds()), pendingFence, snapshot.maximumPulseProfiles(), snapshot.maximumPulseIntervals(), ongoing, beforeMaterialized, afterMaterialized);
	}

	private Simulation simulate(Preset preset, int profiles)
	{
		final long now = minute(Instant.parse("2026-09-01T00:00:00Z"));
		final List<Long> ages = new ArrayList<>();
		final List<Long> productive = new ArrayList<>();
		int newcomers = 0;
		long total = 0;
		for (long ordinal = 1; ordinal <= profiles; ordinal++)
		{
			final PhantomPopulationEcologyState state = _ecology.assign(preset, 9, ordinal, SEED, now, -1, 0, null);
			final long age = now - state.virtualJoinEpochMinute();
			long minutes = 0;
			for (long cursor = state.virtualJoinEpochMinute(); cursor < now; cursor += state.productiveBlockMinutes())
			{
				final long duration = Math.min(state.productiveBlockMinutes(), now - cursor);
				final var evaluation = _population.evaluate(state.scheduleTemplate(), Instant.ofEpochSecond(cursor * 60L), ZoneOffset.UTC, 0);
				if (((evaluation.state() == PhantomActivityState.ACTIVE) || (evaluation.state() == PhantomActivityState.BACKGROUND)) && _ecology.productive(state, Math.floorDiv(cursor, state.productiveBlockMinutes())))
				{
					minutes += duration;
				}
			}
			ages.add(age);
			productive.add(minutes);
			total += minutes;
			newcomers += state.newcomer(now, _ecology.requirePreset(preset).newcomerDays()) ? 1 : 0;
		}
		ages.sort(Long::compare);
		productive.sort(Long::compare);
		return new Simulation(profiles, ages.get(profiles / 2), productive.get(profiles / 2), total, newcomers);
	}

	private List<String> assignments(Preset preset, long now)
	{
		final List<String> values = new ArrayList<>();
		for (long ordinal = 1; ordinal <= 32; ordinal++)
		{
			final PhantomPopulationEcologyState state = _ecology.assign(preset, 7, ordinal, SEED, now, -1, 0, null);
			values.add(state.virtualJoinEpochMinute() + ":" + state.pace() + ":" + state.personality() + ":" + state.scheduleTemplate() + ":" + state.turnoverEligibleEpochMinute());
		}
		return List.copyOf(values);
	}

	private PhantomPopulationEcologyState findPace(Pace pace)
	{
		for (long ordinal = 1; ordinal <= 100; ordinal++)
		{
			final PhantomPopulationEcologyState state = _ecology.assign(Preset.LIVING, 1, ordinal, SEED, minute(CREATED), 0, 0, null);
			if (state.pace() == pace)
			{
				return state;
			}
		}
		throw new AssertionError("Missing pace in deterministic cycle: " + pace);
	}

	private PhantomPopulationEcologyState stateAt(long minute, Pace pace, int share, String schedule)
	{
		return new PhantomPopulationEcologyState(_ecology.hash(), Preset.LIVING, 1, 1, minute, minute, minute, minute, pace, share, _ecology.limits().productiveBlockMinutes(), Personality.BALANCED, Map.of(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0), schedule, Disposition.MANAGED, minute + 10_000, 0, "", 0, 0, 0, "");
	}

	private PhantomPopulationEcologyService service(PersistencePort store, HistoricalPort historical, AtomicBoolean materialized, AtomicReference<String> safety, PhantomPopulationTestDoubles.MutableClock clock, Preset preset, int worldAgeDays, int archiveLimit)
	{
		return new PhantomPopulationEcologyService(_ecology, _population, store, historical, id -> materialized.get(), id -> safety.get(), clock, ZoneOffset.UTC, preset, worldAgeDays, archiveLimit);
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

	private static void advanceToReady(PhantomPopulationManager manager, long profileId)
	{
		for (int step = 0; step < 8; step++)
		{
			final ManagedSnapshot current = manager.find(profileId).orElseThrow();
			if (current.state().state() == PhantomPopulationState.State.READY)
			{
				return;
			}
			manager.advanceCreation(profileId);
		}
		throw new AssertionError("Synthetic replacement did not reach READY.");
	}

	private static void pulseUntil(PhantomPopulationManager manager, java.util.function.BooleanSupplier condition, int maximumPulses, String failure)
	{
		for (int pulse = 0; (pulse < maximumPulses) && !condition.getAsBoolean(); pulse++)
		{
			manager.onPulse();
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), failure);
	}

	private static void stop(PhantomPopulationManager manager)
	{
		manager.beginStop();
		PhantomAssertions.assertTrue(manager.finishStop(), "Synthetic ecology PopulationManager did not stop.");
	}

	private static void assertNoRewardShortcut(PhantomTestContext context) throws Exception
	{
		final Path directory = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/population");
		final List<String> banned = List.of("setLevel(", "addExp", "setExp(", "teleToLocation(", "exp *=", "drop *=", "rewardMultiplier", "humanLevel");
		try (var paths = Files.list(directory))
		{
			for (Path path : paths.filter(value -> value.getFileName().toString().startsWith("PhantomPopulationEcology") && value.getFileName().toString().endsWith(".java")).toList())
			{
				final String source = Files.readString(path, StandardCharsets.UTF_8);
				for (String token : banned)
				{
					PhantomAssertions.assertFalse(source.contains(token), "Ecology source contains forbidden reward/rubber-band shortcut: " + token);
				}
			}
		}
	}

	private static String enabledConfig(String preset)
	{
		return """
			EnablePhantomSystem = True
			EnablePhantomDiagnostics = False
			MaxMaterializedPhantoms = 32
			MaxScheduledPhantomProfiles = 10000
			PhantomSchedulerPulseMillis = 100
			PhantomSchedulerProfilesPerPulse = 128
			PhantomPopulationTarget = 10
			PhantomPopulationActiveTarget = 5
			PhantomPopulationCreationInFlight = 2
			PhantomPopulationBoundariesPerPulse = 64
			PhantomPartyOperationsPerPulse = 64
			PhantomSocialCacheProfiles = 1024
			PhantomPopulationTimeZone = UTC
			EnablePhantomEcology = True
			PhantomEcologyPreset = %s
			PhantomEcologyWorldAgeDays = -1
			PhantomEcologyArchiveLimit = 1000
			""".formatted(preset);
	}

	private static long minute(Instant instant)
	{
		return instant.toEpochMilli() / MINUTE_MILLIS;
	}

	private record Simulation(int profiles, long medianAgeMinutes, long medianProductiveMinutes, long totalProductiveMinutes, int newcomers)
	{
	}

	private record Reconciliation(long cursor, long productiveMinutes, long elapsedMinutes, List<String> requestIds, boolean pendingFenceObserved, int maximumProfilesPerPulse, int maximumIntervalsPerPulse, long ongoingProductiveMinutes, long beforeMaterializedMinutes, long afterMaterializedMinutes)
	{
	}

	private static final class EcologyMemoryStore implements PersistencePort
	{
		private final Map<Long, StoredState> _states = new LinkedHashMap<>();
		private final PhantomPopulationTestDoubles.MemoryStore _population;

		private EcologyMemoryStore(PhantomPopulationTestDoubles.MemoryStore population)
		{
			_population = population;
		}

		@Override
		public synchronized Optional<StoredState> load(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}

		@Override
		public synchronized StoredState insert(long profileId, PhantomPopulationEcologyState state)
		{
			return _states.computeIfAbsent(profileId, id -> new StoredState(state, 0));
		}

		@Override
		public synchronized StoredState save(long profileId, StoredState expected, PhantomPopulationEcologyState replacement)
		{
			if (_states.get(profileId) != expected)
			{
				throw new java.util.ConcurrentModificationException("Synthetic ecology row version changed.");
			}
			final StoredState saved = new StoredState(replacement, expected.rowVersion() + 1);
			_states.put(profileId, saved);
			return saved;
		}

		@Override
		public synchronized ArchivedResult archive(ManagedSnapshot population, StoredState ecology, long archiveGeneration, long nowEpochMinute)
		{
			final PhantomPopulationEcologyState archived = ecology.state().archived(archiveGeneration, nowEpochMinute, "ecology.turnover");
			final StoredState saved = save(population.profile().profileId(), ecology, archived);
			final ManagedSnapshot retired;
			if (_population != null)
			{
				retired = _population.updateState(population, population.state().retired());
			}
			else
			{
				final PhantomProfileComponent component = new PhantomProfileComponent(population.profile().profileId(), PhantomPopulationState.COMPONENT_TYPE, PhantomPopulationState.SCHEMA_VERSION, population.component().rowVersion() + 1, new byte[0], CREATED, CREATED);
				retired = new ManagedSnapshot(population.profile(), component, population.state().retired());
			}
			return new ArchivedResult(retired, saved);
		}

		private synchronized StoredState require(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId)).orElseThrow();
		}

		private synchronized Optional<StoredState> optional(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}
	}

	private static final class HistoricalMemoryPort implements HistoricalPort
	{
		private static final PhantomBackgroundState.Hashes HASHES = new PhantomBackgroundState.Hashes("knowledge", "topology", "progression", "commerce");
		private final Map<Long, Snapshot> _states = new HashMap<>();
		private final List<String> _requestIds = new ArrayList<>();
		private long _advancedMinutes;

		@Override
		public synchronized Optional<Snapshot> status(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}

		@Override
		public synchronized PhantomHistoricalBackgroundService.Result begin(long profileId, long fromEpochMinute, long targetEpochMinute, long deterministicSeed)
		{
			final String requestId = digest(profileId + ":" + fromEpochMinute + ":" + targetEpochMinute + ":" + deterministicSeed);
			final Snapshot existing = _states.get(profileId);
			if ((existing != null) && existing.state().requestId().equals(requestId))
			{
				return PhantomHistoricalBackgroundService.Result.success(existing, 0);
			}
			if ((existing != null) && (existing.state().status() != Status.COMPLETE))
			{
				return PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.CONFLICT, "synthetic.conflict", existing);
			}
			final PhantomBackgroundCatchupState state = new PhantomBackgroundCatchupState(Status.PENDING, requestId, deterministicSeed, fromEpochMinute, targetEpochMinute, fromEpochMinute, 0, 0, 1, 1, 1, 1, 0, digest("plan"), PhantomBackgroundState.MODEL_VERSION, HASHES, "").running();
			final Snapshot created = new Snapshot(state, existing == null ? 0 : existing.rowVersion() + 1);
			_states.put(profileId, created);
			_requestIds.add(requestId);
			return PhantomHistoricalBackgroundService.Result.success(created, 0);
		}

		@Override
		public synchronized PhantomHistoricalBackgroundService.Result advance(long profileId, int maximumIntervals, int maximumMinutes)
		{
			final Snapshot current = Optional.ofNullable(_states.get(profileId)).orElseThrow();
			final long remaining = current.state().targetEpochMinute() - current.state().cursorEpochMinute();
			final int advanced = Math.toIntExact(Math.min(remaining, Math.min(maximumIntervals, maximumMinutes)));
			final Snapshot saved = new Snapshot(current.state().advanceTo(current.state().cursorEpochMinute() + advanced), current.rowVersion() + 1);
			_states.put(profileId, saved);
			_advancedMinutes += advanced;
			return PhantomHistoricalBackgroundService.Result.success(saved, advanced);
		}

		private synchronized long advancedMinutes()
		{
			return _advancedMinutes;
		}

		private synchronized List<String> requestIds()
		{
			return List.copyOf(_requestIds);
		}

		private static String digest(String value)
		{
			try
			{
				return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
			}
			catch (Exception exception)
			{
				throw new IllegalStateException(exception);
			}
		}
	}
}

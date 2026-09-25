package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.NameStyle;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry.Presence;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.PopulationAuthorityException;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.ActionKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundOperationKey.HistoricalIdentity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;

/** DB-free LIVE-003 code evidence at the accepted 1,280-profile scale. */
public final class PhantomLive003CoreLife1280Suite implements PhantomTestSuite
{
	private static final long SEED = 30031280L;
	private static final int SAMPLE_SIZE = 10_000;
	private PhantomPopulationCatalog _catalog;

	@Override
	public String id()
	{
		return "live003-core-life-1280";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "LIVE-003 seed changed.");
		_catalog = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("G-10000-adversarial-nickname-code-gate", this::nicknameGate);
		registry.add("F-168-hour-v2-calendar-capacity-proof", this::weeklySchedule);
		registry.add("F-exact-v1-predecessor-authority-selection", this::predecessorAuthority);
		registry.add("A-schedule-presence-and-exclusive-busy", this::logicalPresence);
		registry.add("I-10000-pure-due-rate-reference", this::dueRateReference);
		registry.add("C-existing-minute-cursor-operation-identity", this::existingCursorIdentity);
	}

	private void existingCursorIdentity(PhantomTestContext context)
	{
		final var hashes = new PhantomBackgroundState.Hashes("knowledge", "topology", "progression", "commerce");
		final String requestId = "a".repeat(64);
		final String planId = "b".repeat(64);
		final var pending = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING, requestId, SEED, 100, 102, 100, 0, 0, 1, 1, 1, 0, 0, "", PhantomBackgroundState.MODEL_VERSION, hashes, "");
		final var running = pending.withPlan(1, 0, 0, planId, 1, 1).running();
		final var first = running.advanceTo(101);
		final var restored = new PhantomBackgroundCatchupStateCodec().decode(new PhantomBackgroundCatchupStateCodec().encode(first));
		PhantomAssertions.assertEquals(first, restored, "Existing cursor is not restart-stable.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> first.advanceTo(101), "Existing cursor accepted duplicate advance.");
		PhantomAssertions.assertEquals(102L, first.advanceTo(102).cursorEpochMinute(), "Existing cursor did not progress monotonically.");
		final var interval = new HistoricalIdentity(requestId, 1, 0, 100, 101, planId);
		final var replay = new HistoricalIdentity(restored.requestId(), restored.generation(), restored.intervalOrdinal() - 1, restored.cursorEpochMinute() - 1, restored.cursorEpochMinute(), restored.planIdentity());
		final var key = new PhantomBackgroundOperationKey(1, 1, 1, 0, 0, 0, ActionKind.HISTORICAL_FARM, 1, "anchor", PhantomBackgroundState.MODEL_VERSION, hashes, null, interval);
		final var same = new PhantomBackgroundOperationKey(1, 1, 1, 0, 0, 0, ActionKind.HISTORICAL_FARM, 1, "anchor", PhantomBackgroundState.MODEL_VERSION, hashes, null, replay);
		PhantomAssertions.assertEquals(key.digest(), same.digest(), "Existing operation identity changed after cursor restore.");
		context.record("live003.reusedHistoricalOperationDigest", key.digest());
	}

	private void dueRateReference(PhantomTestContext context)
	{
		final PriorityQueue<Due> due = new PriorityQueue<>();
		for (long profileId = 1; profileId <= SAMPLE_SIZE; profileId++)
		{
			due.add(new Due(300 + Math.floorMod(mix(SEED, profileId), 601), profileId, 0));
		}
		final int[] perSecond = new int[86_400];
		long updates = 0;
		int maximumQueue = due.size();
		while (!due.isEmpty() && (due.peek().second() < perSecond.length))
		{
			final Due current = due.remove();
			perSecond[(int) current.second()]++;
			updates++;
			final long interval = 300 + Math.floorMod(mix(SEED ^ current.ordinal(), current.profileId()), 601);
			due.add(new Due(current.second() + interval, current.profileId(), current.ordinal() + 1));
			maximumQueue = Math.max(maximumQueue, due.size());
		}
		final int maximumPerSecond = java.util.Arrays.stream(perSecond).max().orElseThrow();
		PhantomAssertions.assertEquals(SAMPLE_SIZE, maximumQueue, "Reference due queue exceeded 10k slots.");
		PhantomAssertions.assertTrue((updates > 1_000_000) && (updates < 2_000_000), "Reference due update volume is implausible.");
		PhantomAssertions.assertTrue(maximumPerSecond < 100, "Reference due scheduling has an unbounded one-second burst.");
		context.record("live003.pureReferenceDueUpdates24h", updates);
		context.record("live003.pureReferenceMeanDuePerSecond", String.format(Locale.ROOT, "%.3f", updates / 86_400d));
		context.record("live003.pureReferenceMaxDuePerSecond", maximumPerSecond);
		context.record("live003.pureReferenceMaxQueue", maximumQueue);
		context.record("live003.currentProductionBackgroundCadenceMillis", PhantomSchedulerPolicy.productionDefaults(100).backgroundCadenceMillis());
	}

	private void weeklySchedule(PhantomTestContext context) throws Exception
	{
		final PhantomPopulationCatalog v2 = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v2.xml"), ZoneOffset.UTC);
		PhantomAssertions.assertEquals(_catalog.templates().keySet(), v2.templates().keySet(), "V2 schedule IDs changed.");
		for (String templateId : _catalog.templates().keySet())
		{
			PhantomAssertions.assertEquals(_catalog.templates().get(templateId).maximumPhaseMinutes(), v2.templates().get(templateId).maximumPhaseMinutes(), "V2 schedule phase bounds changed.");
		}
		final PhantomSocialCatalog social = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		final PhantomPopulationEcologyCatalog ecology = PhantomPopulationEcologyCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), v2, social);
		final String first = weeklyRows(v2, ecology);
		final String second = weeklyRows(v2, ecology);
		PhantomAssertions.assertEquals(first, second, "Weekly schedule rerun differs.");
		PhantomAssertions.assertEquals(169L, first.lines().count(), "Weekly schedule must contain exactly 168 hours.");
		Files.writeString(context.moduleRoot().resolve("docs/phantoms/live-world/LIVE003_WEEKLY_SCHEDULE_1280.tsv"), first, StandardCharsets.UTF_8);
	}

	private void predecessorAuthority(PhantomTestContext context)
	{
		final PhantomPopulationCatalog v2 = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v2.xml"), ZoneOffset.UTC);
		PhantomAssertions.assertEquals(_catalog.hash(), PhantomPopulationStore.authorityCatalog(_catalog.hash(), "morning", 20, v2, _catalog).hash(), "Exact v1 predecessor was rejected.");
		PhantomAssertions.assertEquals(v2.hash(), PhantomPopulationStore.authorityCatalog(v2.hash(), "morning", 20, v2, _catalog).hash(), "New v2 authority was rejected.");
		PhantomAssertions.assertThrows(PopulationAuthorityException.class, () -> PhantomPopulationStore.authorityCatalog("0".repeat(64), "morning", 20, v2, _catalog), "Unknown predecessor hash was accepted.");
		PhantomAssertions.assertThrows(PopulationAuthorityException.class, () -> PhantomPopulationStore.authorityCatalog(_catalog.hash(), "morning", 21, v2, _catalog), "Out-of-range v1 phase was accepted.");
		PhantomAssertions.assertThrows(PopulationAuthorityException.class, () -> PhantomPopulationStore.authorityCatalog(_catalog.hash(), "missing", 0, v2, _catalog), "Missing v2 schedule ID was accepted.");
		PhantomAssertions.assertThrows(PopulationAuthorityException.class, () -> PhantomPopulationStore.authorityCatalog(_catalog.hash(), "morning", 0, v2, v2), "Non-v1 predecessor was accepted.");
	}

	private void logicalPresence(PhantomTestContext context)
	{
		final PhantomPresenceRegistry presence = new PhantomPresenceRegistry(2);
		PhantomAssertions.assertEquals(Presence.OFFLINE, presence.state(1), "Unknown profile was not OFFLINE.");
		PhantomAssertions.assertTrue(presence.schedule(1, PhantomActivityState.BACKGROUND), "BACKGROUND schedule did not publish presence.");
		PhantomAssertions.assertEquals(Presence.AVAILABLE, presence.state(1), "BACKGROUND schedule was not AVAILABLE.");
		PhantomAssertions.assertTrue(presence.claimBusy(1, "party"), "Exclusive BUSY claim failed.");
		PhantomAssertions.assertFalse(presence.claimBusy(1, "trade"), "Second exclusive BUSY claim succeeded.");
		PhantomAssertions.assertFalse(presence.permitsOrdinaryFarm(1), "BUSY permitted ordinary farm.");
		PhantomAssertions.assertTrue(presence.schedule(1, PhantomActivityState.SLEEPING), "SLEEPING schedule was not accepted.");
		PhantomAssertions.assertEquals(Presence.OFFLINE, presence.state(1), "SLEEPING did not become OFFLINE.");
		PhantomAssertions.assertFalse(presence.permitsOrdinaryFarm(1), "OFFLINE permitted ordinary farm.");
		PhantomAssertions.assertTrue(presence.schedule(1, PhantomActivityState.WARM), "WARM schedule was not accepted.");
		PhantomAssertions.assertEquals(Presence.BUSY, presence.state(1), "Schedule transition dropped independent BUSY claim.");
		PhantomAssertions.assertFalse(presence.releaseBusy(1, "trade"), "Unrelated owner released BUSY.");
		PhantomAssertions.assertTrue(presence.releaseBusy(1, "party"), "Exact owner could not release BUSY.");
		PhantomAssertions.assertTrue(presence.permitsOrdinaryFarm(1), "AVAILABLE did not permit ordinary farm.");
		PhantomAssertions.assertTrue(presence.schedule(2, PhantomActivityState.ACTIVE), "Second profile was rejected.");
		PhantomAssertions.assertFalse(presence.schedule(3, PhantomActivityState.ACTIVE), "Presence exceeded bounded capacity.");
		PhantomAssertions.assertEquals(2, presence.snapshot().available(), "Presence count lost AVAILABLE profiles.");
		presence.remove(1);
		PhantomAssertions.assertTrue(presence.schedule(3, PhantomActivityState.BACKGROUND), "Removal did not free bounded capacity.");
	}

	private String weeklyRows(PhantomPopulationCatalog catalog, PhantomPopulationEcologyCatalog ecology)
	{
		final String[] templates = new String[1280];
		final int[] phases = new int[1280];
		final long seed = PhantomPopulationManager.DETERMINISTIC_SEED;
		final long initialMinute = Instant.parse("2026-09-21T00:00:00Z").getEpochSecond() / 60;
		for (int index = 0; index < templates.length; index++)
		{
			final long profileId = index + 1L;
			final String template = ecology.assign(Preset.LIVING, 1, profileId, seed, initialMinute, -1, 0, null).scheduleTemplate();
			templates[index] = template;
			final int maximumPhase = catalog.templates().get(template).maximumPhaseMinutes();
			phases[index] = maximumPhase == 0 ? 0 : (int) Math.floorMod(mix(seed, profileId) >>> 23, (maximumPhase * 2L) + 1L) - maximumPhase;
		}
		final StringBuilder rows = new StringBuilder("hour_utc\tactive\tbackground\twarm\tsleeping\tactive_target_shortfall_64\n");
		int minimumActive = Integer.MAX_VALUE;
		int maximumActive = 0;
		for (int hour = 0; hour < 168; hour++)
		{
			final Instant instant = Instant.ofEpochSecond((initialMinute + (hour * 60L)) * 60L);
			int active = 0;
			int background = 0;
			int warm = 0;
			int sleeping = 0;
			for (int index = 0; index < templates.length; index++)
			{
				final PhantomActivityState state = catalog.evaluate(templates[index], instant, ZoneOffset.UTC, phases[index]).state();
				switch (state)
				{
					case ACTIVE -> active++;
					case BACKGROUND -> background++;
					case WARM -> warm++;
					case SLEEPING -> sleeping++;
					default -> throw new AssertionError("Unexpected calendar state.");
				}
			}
			PhantomAssertions.assertEquals(1280, active + background + warm + sleeping, "Weekly schedule lost a synthetic profile.");
			minimumActive = Math.min(minimumActive, active);
			maximumActive = Math.max(maximumActive, active);
			rows.append(instant).append('\t').append(active).append('\t').append(background).append('\t').append(warm).append('\t').append(sleeping).append('\t').append(Math.max(0, 64 - active)).append('\n');
		}
		PhantomAssertions.assertTrue(minimumActive >= 64, "V2 calendar has an ACTIVE capacity gap.");
		PhantomAssertions.assertTrue(maximumActive > minimumActive, "V2 calendar has no daypart variation.");
		return rows.toString();
	}

	private void nicknameGate(PhantomTestContext context) throws Exception
	{
		final List<String> first = simulateNicknames();
		final List<String> second = simulateNicknames();
		PhantomAssertions.assertEquals(first, second, "Nickname dry runs are not byte-identical.");
		final Path output = context.moduleRoot().resolve("docs/phantoms/live-world/LIVE003_NICKNAME_10000_DRYRUN.tsv");
		Files.writeString(output, String.join("\n", first) + "\n", StandardCharsets.UTF_8);
		context.record("live003.nicknameRows", first.size() - 1);
	}

	private List<String> simulateNicknames()
	{
		final Set<String> occupied = new HashSet<>();
		for (long profileId = 1; profileId <= 256; profileId++)
		{
			final long identitySeed = mix(SEED, profileId);
			for (int attempt = 0; attempt <= 8; attempt++)
			{
				occupied.add(_catalog.chooseName(identitySeed, attempt).value().toLowerCase(Locale.ROOT));
			}
		}
		for (long profileId = 1; profileId <= 32; profileId++)
		{
			occupied.add(_catalog.fallbackName(profileId, 9).toLowerCase(Locale.ROOT));
		}
		final Map<NameStyle, Integer> categories = new EnumMap<>(NameStyle.class);
		final Map<Integer, Integer> attempts = new java.util.TreeMap<>();
		int fallback = 0;
		int exhausted = 0;
		for (long profileId = 1; profileId <= SAMPLE_SIZE; profileId++)
		{
			final long identitySeed = mix(SEED, profileId);
			boolean accepted = false;
			for (int attempt = 0; attempt <= 32; attempt++)
			{
				final String candidate = attempt <= 8 ? _catalog.chooseName(identitySeed, attempt).value() : _catalog.fallbackName(profileId, attempt);
				if (!occupied.add(candidate.toLowerCase(Locale.ROOT)))
				{
					continue;
				}
				PhantomAssertions.assertTrue(candidate.matches("[A-Za-z0-9]{1,16}"), "Invalid native-compatible nickname.");
				final String lowered = candidate.toLowerCase(Locale.ROOT);
				PhantomAssertions.assertTrue(_catalog.reservedTokens().stream().noneMatch(lowered::contains), "Nickname contains a reserved token.");
				attempts.merge(attempt, 1, Integer::sum);
				if (attempt <= 8)
				{
					categories.merge(_catalog.chooseName(identitySeed, attempt).style(), 1, Integer::sum);
				}
				else
				{
					fallback++;
				}
				accepted = true;
				break;
			}
			if (!accepted)
			{
				exhausted++;
			}
		}
		PhantomAssertions.assertEquals(0, exhausted, "Adversarial nickname fixture exhausted attempts.");
		PhantomAssertions.assertEquals(SAMPLE_SIZE, attempts.values().stream().mapToInt(Integer::intValue).sum(), "Nickname dry run did not allocate 10k unique names.");
		PhantomAssertions.assertTrue(fallback >= 256, "Adversarial fixture did not exercise numeric fallback.");
		final List<String> rows = new ArrayList<>();
		rows.add("metric\tvalue");
		rows.add("seed\t" + SEED);
		rows.add("requested\t" + SAMPLE_SIZE);
		rows.add("fixture_reserved\t" + ((256 * 9) + 32));
		rows.add("duplicates\t0");
		rows.add("exhausted\t" + exhausted);
		for (NameStyle style : NameStyle.values())
		{
			rows.add("category_" + style.name() + "\t" + categories.getOrDefault(style, 0));
		}
		rows.add("category_NUMERIC_FALLBACK\t" + fallback);
		for (var attempt : attempts.entrySet())
		{
			rows.add("attempt_" + attempt.getKey() + "\t" + attempt.getValue());
		}
		return rows;
	}

	private static long mix(long seed, long profileId)
	{
		long value = seed ^ (profileId + 0x9e3779b97f4a7c15L);
		value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
		value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
		return value ^ (value >>> 31);
	}

	private record Due(long second, long profileId, long ordinal) implements Comparable<Due>
	{
		@Override
		public int compareTo(Due other)
		{
			final int time = Long.compare(second, other.second);
			return time == 0 ? Long.compare(profileId, other.profileId) : time;
		}
	}
}

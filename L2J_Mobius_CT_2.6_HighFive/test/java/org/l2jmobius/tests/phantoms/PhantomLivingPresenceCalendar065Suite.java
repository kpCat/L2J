/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;

/** DB-free calendar coverage and exact persisted-identity compatibility gate. */
public final class PhantomLivingPresenceCalendar065Suite implements PhantomTestSuite
{
	private static final long SEED = 65_006_500L;
	private static final String V2_HASH = "d555ab8dda0783d0736d3aadb0bd3d1b76481c73f0a34656cdf5c70fb88fbbc6";
	private static final Instant MONDAY = Instant.parse("2026-10-19T00:00:00Z");
	private PhantomPopulationCatalog _current;
	private PhantomPopulationCatalog _v1;
	private PhantomPopulationCatalog _v2;
	private PhantomPopulationEcologyCatalog _ecology;

	@Override
	public String id()
	{
		return "living-presence-calendar065";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "TASK065 calendar seed changed.");
		final Path data = context.moduleRoot().resolve("dist/game/data/phantoms");
		// Follow the ordinary Main binding, so the RED run exercises its actual v2 calendar.
		final String system = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"));
		final var binding = Pattern.compile("data/phantoms/population/high-five-population-v[0-9]+\\.xml").matcher(system);
		PhantomAssertions.assertTrue(binding.find(), "Ordinary Main population catalog binding is absent.");
		_current = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game").resolve(binding.group()), ZoneOffset.UTC);
		_v1 = PhantomPopulationCatalog.load(data.resolve("population/high-five-population-v1.xml"), ZoneOffset.UTC);
		_v2 = PhantomPopulationCatalog.load(data.resolve("population/high-five-population-v2.xml"), ZoneOffset.UTC);
		_ecology = PhantomPopulationEcologyCatalog.load(data.resolve("population/high-five-ecology-v1.xml"), _current, PhantomSocialCatalog.load(data.resolve("social/high-five-social-v1.xml")));
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-new-cohorts-cover-7x1440-with-12-to-16-hour-shifts", this::testNewCohorts);
		registry.add("02-exact-predecessor-state-and-assignment-survive-restart", this::testCompatibility);
		registry.add("03-sunday-midnight-phase-and-dst-boundaries", this::testBoundaries);
	}

	private Map<String, Integer> assignments(Preset preset)
	{
		final Map<String, Integer> result = new LinkedHashMap<>();
		for (long ordinal = 1; ordinal <= 100; ordinal++)
		{
			final var state = _ecology.assign(preset, 1, ordinal, SEED, MONDAY.getEpochSecond() / 60, 0, 0, null);
			result.merge(state.scheduleTemplate(), 1, Integer::sum);
		}
		return result;
	}

	private void testNewCohorts(PhantomTestContext context)
	{
		for (Preset preset : Preset.values())
		{
			final Map<String, Integer> assigned = assignments(preset);
			PhantomAssertions.assertEquals(3, assigned.size(), "A full deterministic assignment cycle must contain three cohorts.");
			PhantomAssertions.assertEquals(List.of(20, 30, 50), assigned.values().stream().sorted().toList(), "Existing 30/50/20 ecology cohort proportions changed.");
			for (long value = SEED; value < SEED + 100; value++)
			{
				PhantomAssertions.assertTrue(assigned.containsKey(_current.chooseSchedule(value).id()), "Direct new-shell assignment selected a retained legacy cohort.");
			}
			for (int day = 0; day < 7; day++)
			{
				final Map<String, Integer> activeMinutes = new LinkedHashMap<>();
				final Map<String, Integer> sleepingMinutes = new LinkedHashMap<>();
				for (int minute = 0; minute < 1440; minute++)
				{
					final Instant now = MONDAY.plusSeconds(((day * 1440L) + minute) * 60L);
					int active = 0;
					for (String cohort : assigned.keySet())
					{
						final var state = _current.evaluate(cohort, now, ZoneOffset.UTC, 0).state();
						if (state == PhantomActivityState.ACTIVE)
						{
							active++;
							activeMinutes.merge(cohort, 1, Integer::sum);
						}
						if (state == PhantomActivityState.SLEEPING) { sleepingMinutes.merge(cohort, 1, Integer::sum); }
					}
					PhantomAssertions.assertTrue(active > 0, "No new cohort is ACTIVE at " + now + " for " + preset + ".");
					PhantomAssertions.assertTrue(active < assigned.size(), "All new cohorts are ACTIVE together at " + now + ".");
				}
				for (String cohort : assigned.keySet())
				{
					final int minutes = activeMinutes.getOrDefault(cohort, 0);
					PhantomAssertions.assertTrue((minutes >= 720) && (minutes <= 960), "New cohort " + cohort + " has " + minutes + " ACTIVE minutes on day " + day + "; expected 720..960.");
					PhantomAssertions.assertTrue(sleepingMinutes.getOrDefault(cohort, 0) > 0, "A new cohort lost its daily sleep.");
				}
			}
			context.record("calendar065." + preset + ".distribution", assigned);
		}
		context.record("calendar065.minuteChecks", 3 * 7 * 1440);
	}

	private void testCompatibility(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(V2_HASH, _v2.hash(), "The committed v2 catalog was modified.");
		for (PhantomPopulationCatalog predecessor : List.of(_v1, _v2))
		{
			for (String template : predecessor.templates().keySet())
			{
				final int phase = predecessor.templates().get(template).maximumPhaseMinutes();
				PhantomAssertions.assertEquals(predecessor.hash(), PhantomPopulationStore.authorityCatalog(predecessor.hash(), template, phase, _current, predecessor).hash(), "Upgrade rejected a pinned persisted catalog authority.");
				PhantomAssertions.assertEquals(_v2.templates().get(template), _current.templates().get(template), "Upgrade changed the effective legacy template.");
				final var ecology = _ecology.assign(Preset.LIVING, 1, 7, SEED, MONDAY.getEpochSecond() / 60, 0, 0, template);
				PhantomAssertions.assertEquals(template, ecology.scheduleTemplate(), "Restore reassigned a persisted legacy cohort.");
			}
		}
		final var codec = new PhantomPopulationStateCodec();
		final var store = new PhantomPopulationTestDoubles.MemoryStore(_v2.hash());
		final var initial = store.seedReady(17, 1);
		final byte[] payload = codec.encode(initial.state());
		for (int restart = 0; restart < 2; restart++)
		{
			final var manager = new PhantomPopulationManager(store, _current, null, new PhantomPopulationTestDoubles.Ownership(), new PhantomPopulationTestDoubles.MutableClock(MONDAY.plusSeconds(20 * 3600)), ZoneOffset.UTC, 1, 1, 8, 4, 1, 8);
			PhantomAssertions.assertTrue(manager.start(), "Restored population did not start.");
			for (int pulse = 0; pulse < 16; pulse++) { manager.onPulse(); }
			manager.beginStop();
			PhantomAssertions.assertTrue(manager.finishStop(), "Restored population did not stop.");
			PhantomAssertions.assertEquals(initial.profile(), store.reload(17).profile(), "Restart changed persisted profile identity or epoch.");
			PhantomAssertions.assertTrue(Arrays.equals(payload, codec.encode(store.reload(17).state())), "Restart rewrote persisted schedule, authority, identity or creation state.");
		}
		PhantomAssertions.assertEquals(0L, store.writes(), "Calendar upgrade performed a durable migration.");
		PhantomAssertions.assertThrows(PhantomPopulationStore.PopulationAuthorityException.class, () -> PhantomPopulationStore.authorityCatalog("0".repeat(64), "evening", 0, _current, _v2), "Unpinned predecessor authority was accepted.");
		final Path driftPath = Files.createTempFile("calendar065-legacy-drift-", ".xml");
		try
		{
			final String current = Files.readString(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v3.xml"));
			final String changed = current.replace("start=\"04:30\"", "start=\"04:31\"");
			PhantomAssertions.assertFalse(current.equals(changed), "Legacy-window drift control did not change the catalog.");
			Files.writeString(driftPath, changed);
			final var drift = PhantomPopulationCatalog.load(driftPath, ZoneOffset.UTC);
			PhantomAssertions.assertThrows(PhantomPopulationStore.PopulationAuthorityException.class, () -> PhantomPopulationStore.authorityCatalog(_v2.hash(), "morning", 0, drift, _v2), "Upgrade silently reinterpreted a pinned v2 legacy window.");
		}
		finally
		{
			Files.deleteIfExists(driftPath);
		}
		context.record("calendar065.legacyPayloadStable", true);
	}

	private void testBoundaries(PhantomTestContext context)
	{
		final Map<String, Integer> assigned = assignments(Preset.LIVING);
		final ZoneId kyiv = ZoneId.of("Europe/Kyiv");
		for (String cohort : assigned.keySet())
		{
			final var template = _current.templates().get(cohort);
			for (int phase : new int[] { -template.maximumPhaseMinutes(), 0, template.maximumPhaseMinutes() })
			{
				for (Instant now : List.of(Instant.parse("2026-10-25T23:59:00Z"), Instant.parse("2026-10-26T00:00:00Z"), Instant.parse("2026-03-29T00:59:00Z"), Instant.parse("2026-03-29T01:00:00Z"), Instant.parse("2026-10-25T00:59:00Z"), Instant.parse("2026-10-25T01:00:00Z")))
				{
					final var evaluation = _current.evaluate(cohort, now, kyiv, phase);
					PhantomAssertions.assertTrue(evaluation.nextBoundary().isAfter(now), "DST/week boundary is not strictly future.");
				}
			}
			final var fixed = _ecology.assign(Preset.LIVING, 1, 9, SEED, MONDAY.getEpochSecond() / 60, 0, 0, cohort);
			PhantomAssertions.assertEquals(cohort, fixed.scheduleTemplate(), "Restart rejected or remapped a new persisted cohort.");
		}
		for (int day = 0; day < 7; day++)
		{
			for (int minute = 0; minute < 1440; minute++)
			{
				final Instant now = MONDAY.plusSeconds(((day * 1440L) + minute) * 60L);
				// A cohort that is ACTIVE at both phase extremes covers every intermediate phase.
				final boolean robust = assigned.keySet().stream().anyMatch(cohort ->
				{
					final int phase = _current.templates().get(cohort).maximumPhaseMinutes();
					return (_current.evaluate(cohort, now, ZoneOffset.UTC, -phase).state() == PhantomActivityState.ACTIVE) && (_current.evaluate(cohort, now, ZoneOffset.UTC, phase).state() == PhantomActivityState.ACTIVE);
				});
				PhantomAssertions.assertTrue(robust, "Allowed profile phases leave an uncovered minute at " + now + ".");
			}
		}
		context.record("calendar065.phaseRobustMinuteChecks", 7 * 1440);
	}
}

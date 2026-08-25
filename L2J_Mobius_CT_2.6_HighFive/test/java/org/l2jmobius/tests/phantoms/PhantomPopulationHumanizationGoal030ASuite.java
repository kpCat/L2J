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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.ArchetypeEntry;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.CareerArchetype;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.ClassEntry;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.NameCandidate;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.NameStyle;

public final class PhantomPopulationHumanizationGoal030ASuite implements PhantomTestSuite
{
	private static final long SEED = 30003010L;
	private static final int SAMPLE_SIZE = 10_000;
	private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9]{1,16}");
	private PhantomPopulationCatalog _catalog;

	@Override
	public String id()
	{
		return "population-humanization-goal030a";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 030A requires the exact deterministic seed.");
		_catalog = PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-name-policy-corpus-and-safety", this::testNamePolicy);
		registry.add("02-ten-thousand-legacy-name-distribution", this::testNameDistribution);
		registry.add("03-role-ecology-canonical-feasibility", this::testEcologyFeasibility);
		registry.add("04-ten-thousand-role-ecology-distribution", this::testEcologyDistribution);
		registry.add("05-russian-config-comment-contract", this::testRussianConfig);
	}

	private void testNamePolicy(PhantomTestContext context)
	{
		final Map<NameStyle, Integer> expected = Map.of(NameStyle.CLEAN, 35, NameStyle.COMPOUND, 25, NameStyle.TRANSLIT_SLANG, 18, NameStyle.DECORATED, 10, NameStyle.DIGITS, 8, NameStyle.LEET, 4);
		final Map<NameStyle, Integer> actual = new EnumMap<>(NameStyle.class);
		_catalog.nameStyles().forEach(entry -> actual.put(entry.style(), entry.weight()));
		PhantomAssertions.assertEquals(expected, actual, "Legacy nickname style weights changed.");
		PhantomAssertions.assertTrue(_catalog.primaryRoots().size() >= 96, "Primary nickname corpus is too small.");
		PhantomAssertions.assertTrue(_catalog.secondaryRoots().size() >= 32, "Secondary nickname corpus is too small.");
		PhantomAssertions.assertTrue(_catalog.slangRoots().size() >= 24, "Translit/slang nickname corpus is too small.");
		PhantomAssertions.assertTrue(_catalog.reservedTokens().containsAll(Set.of("admin", "administrator", "gm", "gamemaster", "npc", "server", "l2j", "phantom")), "Reserved nickname tokens are incomplete.");
		for (int attempt = 0; attempt <= 8; attempt++)
		{
			final NameCandidate first = _catalog.chooseName(SEED, attempt);
			final NameCandidate second = _catalog.chooseName(SEED, attempt);
			PhantomAssertions.assertEquals(first, second, "Nickname selection is not deterministic.");
			assertSafeName(first.value());
		}
		context.record("goal030a.nameCorpus", _catalog.primaryRoots().size() + "/" + _catalog.secondaryRoots().size() + "/" + _catalog.slangRoots().size());
	}
	private void testNameDistribution(PhantomTestContext context)
	{
		final NameSimulation first = simulateNames();
		final NameSimulation second = simulateNames();
		PhantomAssertions.assertEquals(first.names(), second.names(), "10k nickname rerun changed ordered names.");
		PhantomAssertions.assertEquals(first.styles(), second.styles(), "10k nickname rerun changed ordered style labels.");
		PhantomAssertions.assertEquals(SAMPLE_SIZE, new HashSet<>(first.names()).size(), "10k nickname simulation is not unique.");
		PhantomAssertions.assertTrue(percent(first.lengthFourToThirteen()) >= 95.0, "Too many nicknames are outside length 4..13.");
		PhantomAssertions.assertTrue(percent(first.withDigits()) <= 20.0, "Digit-bearing nickname share is too high.");
		PhantomAssertions.assertTrue(percent(first.styleCounts().get(NameStyle.DECORATED)) <= 15.0, "Decorated nickname share is too high.");
		PhantomAssertions.assertTrue(percent(first.styleCounts().get(NameStyle.LEET)) <= 10.0, "Leet nickname share is too high.");
		assertShare(first, NameStyle.CLEAN, 31, 39);
		assertShare(first, NameStyle.COMPOUND, 21, 29);
		assertShare(first, NameStyle.TRANSLIT_SLANG, 14, 22);
		assertShare(first, NameStyle.DECORATED, 7, 13);
		assertShare(first, NameStyle.DIGITS, 5, 11);
		assertShare(first, NameStyle.LEET, 2, 7);
		PhantomAssertions.assertTrue(first.rootCounts().values().stream().mapToInt(Integer::intValue).max().orElse(0) <= 300, "A nickname source root exceeds 3%.");
		final Map.Entry<String, Integer> maximumSuffix = first.suffixCounts().entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
		PhantomAssertions.assertTrue(maximumSuffix.getValue() <= 300, "A deterministic 2-3 character suffix exceeds 3%: " + maximumSuffix);
		final List<String> samples = exactSamples(first);
		PhantomAssertions.assertEquals(40, samples.size(), "Goal030A report sample count is not exact40.");
		PhantomAssertions.assertEquals(Set.of(NameStyle.values()), new HashSet<>(samples.stream().map(value -> NameStyle.valueOf(value.substring(0, value.indexOf(':')))).toList()), "Exact40 samples do not span all styles.");
		context.record("goal030a.nameStyles", first.styleCounts());
		context.record("goal030a.nameLength4to13", first.lengthFourToThirteen());
		context.record("goal030a.nameWithDigits", first.withDigits());
		context.record("goal030a.nameDecorated", first.styleCounts().get(NameStyle.DECORATED));
		context.record("goal030a.nameLeet", first.styleCounts().get(NameStyle.LEET));
		context.record("goal030a.collisionHistogram", first.attemptHistogram());
		context.record("goal030a.samples40", String.join(", ", samples));
	}

	private NameSimulation simulateNames()
	{
		final Set<String> occupied = new HashSet<>();
		final List<String> names = new ArrayList<>(SAMPLE_SIZE);
		final List<NameStyle> styles = new ArrayList<>(SAMPLE_SIZE);
		final Map<NameStyle, Integer> styleCounts = new EnumMap<>(NameStyle.class);
		final Map<Integer, Integer> attemptHistogram = new LinkedHashMap<>();
		final Map<String, Integer> rootCounts = new HashMap<>();
		final Map<String, Integer> suffixCounts = new HashMap<>();
		int lengthFourToThirteen = 0;
		int withDigits = 0;
		for (int ordinal = 1; ordinal <= SAMPLE_SIZE; ordinal++)
		{
			final long identitySeed = mix(SEED, ordinal);
			NameCandidate accepted = null;
			int acceptedAttempt = -1;
			for (int attempt = 0; attempt <= 8; attempt++)
			{
				final NameCandidate candidate = _catalog.chooseName(identitySeed, attempt);
				if (occupied.add(candidate.value().toLowerCase(Locale.ROOT)))
				{
					accepted = candidate;
					acceptedAttempt = attempt;
					break;
				}
			}
			PhantomAssertions.assertTrue(accepted != null, "Nickname collision attempts were exhausted at ordinal " + ordinal + ".");
			assertSafeName(accepted.value());
			names.add(accepted.value());
			styles.add(accepted.style());
			styleCounts.merge(accepted.style(), 1, Integer::sum);
			attemptHistogram.merge(acceptedAttempt, 1, Integer::sum);
			accepted.sourceRoots().forEach(root -> rootCounts.merge(root.toLowerCase(Locale.ROOT), 1, Integer::sum));
			if ((accepted.value().length() >= 4) && (accepted.value().length() <= 13))
			{
				lengthFourToThirteen++;
			}
			if (accepted.value().chars().anyMatch(Character::isDigit))
			{
				withDigits++;
			}
			for (int length = 2; length <= 3; length++)
			{
				if (accepted.value().length() >= length)
				{
					final String suffix = accepted.value().substring(accepted.value().length() - length).toLowerCase(Locale.ROOT);
					suffixCounts.merge(length + ":" + suffix, 1, Integer::sum);
				}
			}
		}
		return new NameSimulation(List.copyOf(names), List.copyOf(styles), Map.copyOf(styleCounts), Map.copyOf(attemptHistogram), Map.copyOf(rootCounts), Map.copyOf(suffixCounts), lengthFourToThirteen, withDigits);
	}

	private static List<String> exactSamples(NameSimulation simulation)
	{
		final List<String> samples = new ArrayList<>(40);
		final Set<NameStyle> covered = new HashSet<>();
		final Set<Integer> indexes = new HashSet<>();
		for (int i = 0; i < simulation.names().size() && covered.size() < NameStyle.values().length; i++)
		{
			if (covered.add(simulation.styles().get(i)))
			{
				samples.add(simulation.styles().get(i) + ":" + simulation.names().get(i));
				indexes.add(i);
			}
		}
		for (int i = 0; samples.size() < 40; i++)
		{
			if (indexes.add(i))
			{
				samples.add(simulation.styles().get(i) + ":" + simulation.names().get(i));
			}
		}
		return List.copyOf(samples);
	}
	private void testEcologyFeasibility(PhantomTestContext context)
	{
		final Map<CareerArchetype, Integer> expected = Map.of(CareerArchetype.DAMAGE, 55, CareerArchetype.TANK, 8, CareerArchetype.HEALER, 8, CareerArchetype.ENHANCEMENT, 12, CareerArchetype.CONTROL, 7, CareerArchetype.ECONOMY, 10);
		final Map<CareerArchetype, Integer> actual = new EnumMap<>(CareerArchetype.class);
		_catalog.archetypes().forEach((key, value) -> actual.put(key, value.weight()));
		PhantomAssertions.assertEquals(expected, actual, "Career ecology weights changed.");
		final Map<CareerArchetype, Set<PlayerClass>> endpoints = canonicalEndpoints();
		final Set<Integer> reachable = new HashSet<>();
		for (ArchetypeEntry entry : _catalog.archetypes().values())
		{
			PhantomAssertions.assertTrue(!entry.classes().isEmpty(), "Career archetype has no canonical class lineage: " + entry.archetype());
			entry.classes().forEach(reference ->
			{
				final PlayerClass starting = PlayerClass.getPlayerClass(reference.classId());
				PhantomAssertions.assertTrue((starting != null) && (starting.level() == 0), "Ecology references a non-starting class.");
				PhantomAssertions.assertTrue(endpoints.get(entry.archetype()).stream().anyMatch(endpoint -> endpoint.equalsOrChildOf(starting)), "Infeasible class/archetype pair: " + reference.classId() + "/" + entry.archetype());
				reachable.add(reference.classId());
			});
		}
		final Set<Integer> canonical = new HashSet<>();
		for (PlayerClass playerClass : PlayerClass.values())
		{
			if (playerClass.level() == 0)
			{
				canonical.add(playerClass.getId());
			}
		}
		PhantomAssertions.assertEquals(canonical, reachable, "Not every canonical level-zero class is reachable in career ecology.");
		PhantomAssertions.assertEquals(Set.of(53), _catalog.archetypes().get(CareerArchetype.ECONOMY).classes().stream().map(value -> value.classId()).collect(java.util.stream.Collectors.toSet()), "Dwarven economy lineage is not exact.");
		context.record("goal030a.feasiblePairs", _catalog.archetypes().values().stream().mapToInt(value -> value.classes().size()).sum());
	}

	private void testEcologyDistribution(PhantomTestContext context)
	{
		final List<String> first = ecologySequence();
		final List<String> second = ecologySequence();
		PhantomAssertions.assertEquals(first, second, "10k career ecology rerun changed.");
		final Map<CareerArchetype, Integer> counts = new EnumMap<>(CareerArchetype.class);
		final Map<Integer, Integer> classes = new LinkedHashMap<>();
		final List<CareerArchetype> sequence = new ArrayList<>(SAMPLE_SIZE);
		for (String value : first)
		{
			final String[] parts = value.split(":");
			final CareerArchetype archetype = CareerArchetype.valueOf(parts[0]);
			sequence.add(archetype);
			counts.merge(archetype, 1, Integer::sum);
			classes.merge(Integer.parseInt(parts[1]), 1, Integer::sum);
		}
		assertPercent(counts, CareerArchetype.DAMAGE, 53, 57);
		assertPercent(counts, CareerArchetype.TANK, 7, 9);
		assertPercent(counts, CareerArchetype.HEALER, 7, 9);
		assertPercent(counts, CareerArchetype.ENHANCEMENT, 11, 13);
		assertPercent(counts, CareerArchetype.CONTROL, 6, 8);
		assertPercent(counts, CareerArchetype.ECONOMY, 9, 11);
		final int globalSupport = counts.get(CareerArchetype.HEALER) + counts.get(CareerArchetype.ENHANCEMENT) + counts.get(CareerArchetype.CONTROL);
		PhantomAssertions.assertTrue(percent(globalSupport) <= 30.0, "Global support share exceeds 30%.");
		final Map<CareerArchetype, Integer> rolling = new EnumMap<>(CareerArchetype.class);
		for (CareerArchetype archetype : CareerArchetype.values())
		{
			rolling.put(archetype, 0);
		}
		for (int i = 0; i < 500; i++)
		{
			rolling.merge(sequence.get(i), 1, Integer::sum);
		}
		double worstSupport = 0;
		for (int start = 0; start <= SAMPLE_SIZE - 500; start++)
		{
			if (start > 0)
			{
				rolling.merge(sequence.get(start - 1), -1, Integer::sum);
				rolling.merge(sequence.get(start + 499), 1, Integer::sum);
			}
			for (CareerArchetype archetype : CareerArchetype.values())
			{
				PhantomAssertions.assertTrue(rolling.get(archetype) > 0, "A contiguous 500 window lacks archetype " + archetype + ".");
			}
			final double support = ((rolling.get(CareerArchetype.HEALER) + rolling.get(CareerArchetype.ENHANCEMENT) + rolling.get(CareerArchetype.CONTROL)) * 100.0) / 500.0;
			worstSupport = Math.max(worstSupport, support);
			PhantomAssertions.assertTrue(support <= 34.0, "A contiguous 500 support share exceeds 34%.");
		}
		PhantomAssertions.assertTrue(classes.values().stream().mapToInt(Integer::intValue).max().orElse(0) <= 1500, "A starting class exceeds preferred 15% concentration.");
		context.record("goal030a.archetypes", counts);
		context.record("goal030a.classes", classes);
		context.record("goal030a.worstRolling500SupportPercent", String.format(Locale.ROOT, "%.2f", worstSupport));
	}

	private List<String> ecologySequence()
	{
		final List<String> values = new ArrayList<>(SAMPLE_SIZE);
		for (int ordinal = 1; ordinal <= SAMPLE_SIZE; ordinal++)
		{
			final CareerArchetype archetype = _catalog.chooseArchetype(SEED, ordinal);
			final ClassEntry classEntry = _catalog.chooseClass(mix(SEED, ordinal), archetype);
			PhantomAssertions.assertTrue(_catalog.supports(classEntry, archetype), "Selected class/archetype pair is not catalog-compatible.");
			values.add(archetype + ":" + classEntry.classId());
		}
		return List.copyOf(values);
	}

	private static Map<CareerArchetype, Set<PlayerClass>> canonicalEndpoints()
	{
		return Map.of(
			CareerArchetype.DAMAGE, Set.of(PlayerClass.DUELIST, PlayerClass.DREADNOUGHT, PlayerClass.SAGITTARIUS, PlayerClass.ADVENTURER, PlayerClass.ARCHMAGE, PlayerClass.SOULTAKER, PlayerClass.ARCANA_LORD, PlayerClass.WIND_RIDER, PlayerClass.MOONLIGHT_SENTINEL, PlayerClass.MYSTIC_MUSE, PlayerClass.ELEMENTAL_MASTER, PlayerClass.GHOST_HUNTER, PlayerClass.GHOST_SENTINEL, PlayerClass.STORM_SCREAMER, PlayerClass.SPECTRAL_MASTER, PlayerClass.TITAN, PlayerClass.GRAND_KHAVATARI, PlayerClass.DOMINATOR, PlayerClass.DOOMCRYER, PlayerClass.DOOMBRINGER, PlayerClass.MALE_SOUL_HOUND, PlayerClass.FEMALE_SOUL_HOUND, PlayerClass.TRICKSTER),
			CareerArchetype.TANK, Set.of(PlayerClass.PHOENIX_KNIGHT, PlayerClass.HELL_KNIGHT, PlayerClass.EVA_TEMPLAR, PlayerClass.SHILLIEN_TEMPLAR),
			CareerArchetype.HEALER, Set.of(PlayerClass.CARDINAL, PlayerClass.EVA_SAINT, PlayerClass.SHILLIEN_SAINT),
			CareerArchetype.ENHANCEMENT, Set.of(PlayerClass.HIEROPHANT, PlayerClass.SWORD_MUSE, PlayerClass.SPECTRAL_DANCER, PlayerClass.SHILLIEN_SAINT, PlayerClass.DOMINATOR, PlayerClass.DOOMCRYER, PlayerClass.JUDICATOR),
			CareerArchetype.CONTROL, Set.of(PlayerClass.SOULTAKER, PlayerClass.DOMINATOR, PlayerClass.MALE_SOUL_HOUND, PlayerClass.FEMALE_SOUL_HOUND, PlayerClass.TRICKSTER, PlayerClass.JUDICATOR),
			CareerArchetype.ECONOMY, Set.of(PlayerClass.FORTUNE_SEEKER, PlayerClass.MAESTRO));
	}
	private void testRussianConfig(PhantomTestContext context) throws Exception
	{
		final Path configPath = context.moduleRoot().resolve("dist/game/config/Custom/PhantomPlayers.ini");
		final byte[] bytes = Files.readAllBytes(configPath);
		PhantomAssertions.assertTrue((bytes.length < 3) || ((bytes[0] & 0xff) != 0xef) || ((bytes[1] & 0xff) != 0xbb) || ((bytes[2] & 0xff) != 0xbf), "PhantomPlayers.ini contains a UTF-8 BOM.");
		final String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
		final Map<String, String> expected = new LinkedHashMap<>();
		expected.put("EnablePhantomSystem", "False");
		expected.put("EnablePhantomDiagnostics", "False");
		expected.put("MaxMaterializedPhantoms", "32");
		expected.put("MaxScheduledPhantomProfiles", "10000");
		expected.put("PhantomSchedulerPulseMillis", "100");
		expected.put("PhantomSchedulerProfilesPerPulse", "128");
		expected.put("PhantomPopulationTarget", "0");
		expected.put("PhantomPopulationActiveTarget", "0");
		expected.put("PhantomPopulationCreationInFlight", "2");
		expected.put("PhantomPopulationBoundariesPerPulse", "64");
		expected.put("PhantomPartyOperationsPerPulse", "64");
		expected.put("PhantomSocialCacheProfiles", "1024");
		expected.put("PhantomPopulationTimeZone", "UTC");
		final Map<String, String> actual = new LinkedHashMap<>();
		boolean russianComment = false;
		for (String line : text.split("\\R"))
		{
			final String trimmed = line.trim();
			if (trimmed.startsWith("#"))
			{
				russianComment |= trimmed.matches(".*[А-Яа-яЁёІіЇїЄє].*");
				continue;
			}
			if (trimmed.isEmpty())
			{
				continue;
			}
			final String[] pair = trimmed.split("\\s*=\\s*", 2);
			PhantomAssertions.assertEquals(2, pair.length, "Malformed PhantomPlayers.ini setting.");
			PhantomAssertions.assertTrue(russianComment, "A PhantomPlayers.ini key lacks a nearby Russian explanation: " + pair[0]);
			actual.put(pair[0], pair[1]);
			russianComment = false;
		}
		PhantomAssertions.assertEquals(expected, actual, "PhantomPlayers.ini keys, values or defaults changed.");
		PhantomAssertions.assertFalse(text.contains("Production materialization remains explicit"), "Legacy English config comments remain.");
		final Path guidePath = context.moduleRoot().resolve("dist/game/data/phantoms/README.ru.md");
		final String guide = Files.readString(guidePath, StandardCharsets.UTF_8);
		PhantomAssertions.assertTrue(guide.contains("CLEAN") && guide.contains("CareerArchetype") && guide.contains("SHA-256") && guide.contains("phantom-population-humanization-goal030a-test"), "Russian population guide lacks required name/ecology/hash/test guidance.");
		PhantomAssertions.assertFalse(guide.matches("(?s).*[A-Za-z]:\\\\.*"), "Russian population guide contains a local absolute path.");
		PhantomAssertions.assertFalse(guide.toLowerCase(Locale.ROOT).contains("root/root"), "Russian population guide contains credentials.");
		context.record("goal030a.configKeys", actual.size());
	}

	private void assertSafeName(String value)
	{
		PhantomAssertions.assertTrue(VALID_NAME.matcher(value).matches(), "Generated nickname violates ASCII/length contract: " + value);
		final String lower = value.toLowerCase(Locale.ROOT);
		PhantomAssertions.assertFalse(_catalog.reservedTokens().stream().anyMatch(lower::contains), "Generated nickname contains a reserved token: " + value);
	}

	private static void assertShare(NameSimulation simulation, NameStyle style, int minimum, int maximum)
	{
		final double value = percent(simulation.styleCounts().getOrDefault(style, 0));
		PhantomAssertions.assertTrue((value >= minimum) && (value <= maximum), "Nickname style share is outside its acceptance band: " + style + "=" + value);
	}

	private static void assertPercent(Map<CareerArchetype, Integer> counts, CareerArchetype archetype, int minimum, int maximum)
	{
		final double value = percent(counts.getOrDefault(archetype, 0));
		PhantomAssertions.assertTrue((value >= minimum) && (value <= maximum), "Career archetype share is outside its acceptance band: " + archetype + "=" + value);
	}

	private static double percent(int count)
	{
		return (count * 100.0) / SAMPLE_SIZE;
	}

	private static long mix(long seed, long ordinal)
	{
		long value = seed ^ (ordinal * 0x9e3779b97f4a7c15L);
		value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
		value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
		return value ^ (value >>> 31);
	}

	private record NameSimulation(List<String> names, List<NameStyle> styles, Map<NameStyle, Integer> styleCounts, Map<Integer, Integer> attemptHistogram, Map<String, Integer> rootCounts, Map<String, Integer> suffixCounts, int lengthFourToThirteen, int withDigits)
	{
	}
}
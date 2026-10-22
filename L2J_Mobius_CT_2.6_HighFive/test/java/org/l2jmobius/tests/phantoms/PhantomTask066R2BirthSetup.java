/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomStarterShotBudget;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Declared native births before ordinary measurement; legacy identities are never edited. */
public final class PhantomTask066R2BirthSetup
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0) { throw new IllegalArgumentException("R2 birth preparation has no arbitrary input."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath(), runtime = PhantomTask066R2PersistentLane.runtime();
		PhantomTask066R2PersistentLane.validate(module, Path.of(System.getProperty("phantom.test.config")));
		final Path receipt = runtime.resolve("private/main-births066r2.properties");
		if (Files.exists(receipt) || Files.exists(runtime.resolve("private/main-births066r2.started"))) { throw new IllegalArgumentException("R2 births already prepared or ambiguous; never repeat."); }
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(66006641, module, module.getParent().resolve(".phantom-local/task066r2/reports/main-births")));
			PhantomPlayersConfig.load();
			if (PhantomPlayersConfig.settings().starterShotLifetimeLimit() != 5000) { throw new IllegalArgumentException("Declare R2 lifetime5000 before native births."); }
			ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			final var profiles = PhantomProfileRepository.open(); final var zone = ZoneId.of("UTC");
			final var catalog = PhantomPopulationCatalog.load(Path.of("data/phantoms/population/high-five-population-v3.xml"), zone);
			final var store = new PhantomPopulationStore(profiles, catalog, zone);
			final var legacy = store.loadManagedAfter(0, 64);
			if (legacy.size() != 48 || legacy.stream().anyMatch(value -> value.state().state() != PhantomPopulationState.State.READY || profiles.findComponent(value.profile().profileId(), PhantomStarterShotBudget.COMPONENT).isPresent())) { throw new IllegalArgumentException("Exact legacy48 without retroactive entitlements required."); }
			for (var value : legacy) { store.validateAuthority(value); }
			final long generation = legacy.stream().mapToLong(value -> value.state().populationGeneration()).max().orElseThrow();
			long ordinal = legacy.stream().mapToLong(value -> value.state().creationOrdinal()).max().orElseThrow();
			final Instant now = Instant.now();
			final String schedule = catalog.templates().keySet().stream().filter(value -> value.endsWith(".v3"))
				.filter(value -> catalog.evaluate(value, now, zone, 0).state() == PhantomActivityState.ACTIVE && catalog.evaluate(value, now.plusSeconds(3600), zone, 0).state() == PhantomActivityState.ACTIVE)
				.sorted(Comparator.reverseOrder()).findFirst().orElseThrow();
			Files.writeString(runtime.resolve("private/main-births066r2.started"), "legacyAuthorityValidated=48\ninputs=TEST_PREIMAGE_NATIVE_BIRTH_ONLY\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			final var output = new ArrayList<String>(List.of("inputs=TEST_PREIMAGE_NATIVE_BIRTH_ONLY", "legacyAuthorityValidated=48", "legacyStarterEntitlements=0", "schedule=" + schedule));
			for (int wantedClass : new int[] {0, 10, 53})
			{
				final long expectedId;
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phantom_profiles'"); var rows = query.executeQuery())
				{ if (!rows.next() || (expectedId = rows.getLong(1)) <= 0) { throw new IllegalStateException("R2 next profile identity unavailable."); } }
				ordinal++;
				long seed = -1;
				for (long candidate = 660066410; candidate < 660067410; candidate++)
				{
					final var archetype = catalog.chooseArchetype(candidate, ordinal);
					if (catalog.chooseClass(identitySeed(candidate, expectedId), archetype).classId() == wantedClass
						&& (wantedClass != 53 || org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.choose(expectedId, 53, false, archetype).terminalClass() == 117)) { seed = candidate; break; }
				}
				if (seed < 0) { throw new IllegalArgumentException("Bounded deterministic birth selection did not cover the declared native class."); }
				var birth = store.createShell(generation, ordinal, seed, schedule);
				if (birth.profile().profileId() != expectedId || birth.state().classId() != wantedClass) { throw new IllegalStateException("Birth preselection raced; preserve the shell without replacement."); }
				for (int stage = 0; stage < 12 && birth.state().state() != PhantomPopulationState.State.READY; stage++)
				{
					final var result = store.advanceCreation(birth); birth = result.snapshot();
					if (result.outcome() == PhantomPopulationStore.CreationOutcome.INCONSISTENT) { throw new IllegalStateException("Native R2 birth is inconsistent; preserve its durable diagnostics."); }
				}
				if (birth.state().state() != PhantomPopulationState.State.READY || catalog.evaluate(schedule, now, zone, birth.state().schedulePhaseMinutes()).state() != PhantomActivityState.ACTIVE) { throw new IllegalStateException("Native R2 birth is not READY/calendar ACTIVE."); }
				final var budget = PhantomStarterShotBudget.decode(profiles.findComponent(expectedId, PhantomStarterShotBudget.COMPONENT).orElseThrow().payload());
				if (budget.limit() != 5000 || budget.issued() != 0) { throw new IllegalStateException("Background birth credited physical shots."); }
				output.add("class" + wantedClass + ".profileId=" + expectedId); output.add("class" + wantedClass + ".objectId=" + birth.profile().characterObjectId()); output.add("class" + wantedClass + ".seed=" + seed);
				if (wantedClass == 53) { output.addAll(PhantomTask066R2DwarfInput.prepare(profiles, birth)); }
			}
			Files.write(receipt, output, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("R2_NATIVE_BIRTHS_PREPARED legacy48 authority unchanged; new fighter0+mage10 native level1; dwarf53->54 declared TEST input; ordinary target51");
		}
		finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
	}

	// Exact PopulationStore identity mixing, used only to choose declared TEST inputs before any birth write.
	private static long identitySeed(long seed, long id)
	{
		long mixed = seed ^ (id + 0x9E3779B97F4A7C15L);
		mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
		return mixed ^ (mixed >>> 31);
	}
}

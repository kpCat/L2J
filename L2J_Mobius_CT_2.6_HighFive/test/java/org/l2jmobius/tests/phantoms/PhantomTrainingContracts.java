/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.learning.TrainingCollector;
import org.l2jmobius.gameserver.phantoms.learning.TrainingConfig;

public final class PhantomTrainingContracts implements PhantomTestSuite
{
	public static void main(String[] args) throws Exception
	{
		final var suite = new PhantomTrainingContracts();
		final var registry = new PhantomTestRegistry(suite.id());
		suite.register(registry);
		final Path module = Path.of(".").toAbsolutePath().normalize();
		final var context = new PhantomTestContext(183, module, module.resolve("../build/phantom-test/reports"));
		for (var test : registry.orderedTests()) { test.testCase().run(context); }
		System.out.println("TRAIN002_CONTRACTS_PASS tests=" + registry.orderedTests().size());
	}

	@Override public String id() { return "training-contracts"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("versioned-config-and-readable-cyrillic", _ ->
		{
			final var config = read("schemaVersion=1\ntitle=Тренировка фантомов\nseed=42\n");
			PhantomAssertions.assertEquals("Тренировка фантомов", config.text("title", ""), "UTF-8 config must preserve Russian text");
			PhantomAssertions.assertEquals(42L, config.seed(), "Seed changed");
			PhantomAssertions.assertEquals(List.of(94), config.classes(), "Existing supported default class changed");
			PhantomAssertions.assertTrue(config.sha256().matches("[0-9a-f]{64}"), "Config must be pinned");
		});
		registry.add("reject-unsafe-endpoints-and-unsupported-mode", _ ->
		{
			for (String invalid : List.of("schemaVersion=2", "modes=PVP", "maxActiveActors=10", "sqlPort=3308",
				"gamePort=33327", "seed=1\nseed=2", "levels=0,86", "grades=FAKE", "episodeSeconds=0"))
			{
				boolean rejected = false;
				try { read(invalid + '\n'); }
				catch (IllegalArgumentException expected) { rejected = true; }
				PhantomAssertions.assertTrue(rejected, "Unsafe config accepted: " + invalid);
			}
		});
		registry.add("episode-admission-does-not-create-native-credit", _ ->
		{
			final var collector = new TrainingCollector();
			collector.begin("first");
			boolean rejected = false;
			try { collector.begin("second"); }
			catch (IllegalStateException expected) { rejected = true; }
			PhantomAssertions.assertTrue(rejected, "Overlapping episode admitted");
			PhantomAssertions.assertTrue(collector.size() == 0 && collector.end().isEmpty(), "Empty episode manufactured native facts");
			collector.begin("second");
			PhantomAssertions.assertTrue(collector.end().isEmpty(), "Previous episode leaked into next epoch");
		});
		registry.add("bounded-natural-preparation-budget", _ ->
		{
			PhantomAssertions.assertEquals(180, read("schemaVersion=1\n").preparationSeconds(), "Natural recovery needs a separate preparation budget.");
			PhantomAssertions.assertEquals(240, read("preparationSeconds=240\n").preparationSeconds(), "Preparation budget must reload from config.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> read("preparationSeconds=0\n"), "Zero preparation budget must be rejected.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> read("preparationSeconds=601\n"), "Unbounded recovery wait must be rejected.");
		});
	}

	private static TrainingConfig read(String text) throws Exception
	{
		final Path path = Files.createTempFile("phantom-training-contract-", ".ini");
		try
		{
			Files.writeString(path, text, StandardCharsets.UTF_8);
			return TrainingConfig.load(path);
		}
		finally { Files.deleteIfExists(path); }
	}
}

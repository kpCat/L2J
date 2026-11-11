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
		registry.add("learning004-existing-episode-budget-and-independent-splits", _ ->
		{
			final var catalog = new java.util.ArrayList<PhantomTrainingScenarios.Scene>();
			for (String split : List.of("TRAIN","TUNING","AUDIT"))
			{
				for (int i=0; i<(split.equals("TRAIN") ? 16 : split.equals("TUNING") ? 8 : 4); i++)
				{
					final String id=PhantomTrainingScenarios.hash(split+":"+i);
					catalog.add(new PhantomTrainingScenarios.Scene(id,"21798@CONTRACT_FIXTURE",21798,94,77,"A",0,0,0,0,List.of(1230,1339),split));
				}
			}
			final var segment = PhantomLearning004Native.segment(catalog,12);
			PhantomAssertions.assertEquals(6,segment.size(),"Twelve native episodes must be six independent Solo/Party pairs, not56 episodes.");
			PhantomAssertions.assertEquals(List.of("TRAIN","TUNING","AUDIT","AUDIT","AUDIT","AUDIT"),segment.stream().map(PhantomTrainingScenarios.Scene::split).toList(),"Four fresh audit contexts support minimum4 real Party labels even when one exact receipt occurs per fight.");
			PhantomAssertions.assertEquals(4L,segment.stream().filter(scene -> scene.split().equals("AUDIT")).map(PhantomTrainingScenarios.Scene::id).distinct().count(),"Fresh independent audit contexts must not be duplicated.");
			PhantomAssertions.assertEquals(28,catalog.size(),"Bounded segment must not trim retained catalog.");
			PhantomAssertions.assertEquals(segment,PhantomLearning004Native.segment(catalog,12),"Segment selection must be deterministic.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class,() -> PhantomLearning004Native.segment(catalog,6),"Insufficient independent audit budget must fail before native.");
		});
		registry.add("learning004-lawful-precut-refusal-is-not-a-fake-episode", _ ->
		{
			PhantomAssertions.assertTrue(PhantomLearning004Native.preCutRejection(new IllegalStateException("LEARNING001_STOCK_PARTY_PREFLIGHT_TARGET")),"Lawful native Party admission refusal must be retained as SKIP.");
			PhantomAssertions.assertFalse(PhantomLearning004Native.preCutRejection(new IllegalStateException("TRAIN_SOURCE_CHANGED")),"Source corruption must remain a fatal defect.");
			PhantomAssertions.assertFalse(PhantomLearning004Native.preCutRejection(new java.sql.SQLException()),"SQL failures must not be hidden as curriculum skips.");
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

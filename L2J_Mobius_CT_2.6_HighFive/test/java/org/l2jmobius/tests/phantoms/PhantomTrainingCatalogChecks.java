/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;

/** Recovery must never activate a partial cohort or silently replay the old generation. */
public final class PhantomTrainingCatalogChecks implements PhantomTestSuite
{
	@Override public String id() { return "training-catalogs"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("sealed-generation-and-payload-required", context ->
		{
			Path root = Files.createTempDirectory("phantom-training-catalog-");
			String owner = "LEARNING002:" + "0".repeat(32), selectors = "a".repeat(64);
			Files.writeString(root.resolve("owner.txt"), owner);
			Path prior = fixture(root, 0, owner, selectors, "SEALED");
			Path partial = fixture(root, 1, owner, selectors, "PREPARING");
			PhantomAssertions.assertEquals(prior, PhantomTrainingActors.findSealedCatalog(root, 0, selectors), "A verified immutable generation must be reusable.");
			PhantomAssertions.assertEquals(null, PhantomTrainingActors.findSealedCatalog(root, 1, selectors), "Incomplete preparation must not activate old or partial actors.");
			Files.writeString(partial.resolve("journal.properties"), journal(owner, 1, selectors, "SEALED"));
			PhantomAssertions.assertEquals(partial, PhantomTrainingActors.findSealedCatalog(root, 1, selectors), "Seal publication must recover without recreating actors.");
			Files.writeString(partial.resolve("training-actors.tsv"), "tampered");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> PhantomTrainingActors.findSealedCatalog(root, 1, selectors), "A marker alone cannot attest a changed actor cohort.");
		});
		registry.add("selector-change-does-not-reuse-credited-actors", context ->
		{
			Path root = Files.createTempDirectory("phantom-training-selector-");
			String owner = "LEARNING002:" + "1".repeat(32);
			Files.writeString(root.resolve("owner.txt"), owner);
			fixture(root, 2, owner, "b".repeat(64), "SEALED");
			PhantomAssertions.assertEquals(null, PhantomTrainingActors.findSealedCatalog(root, 2, "c".repeat(64)), "Changed selectors require a new actual cohort, never rewriting old actors.");
		});
		registry.add("partial-initial-journal-quarantined-foreign-owner-refused", context ->
		{
			Path root = Files.createTempDirectory("phantom-training-pending-");
			String owner = "LEARNING002:" + "5".repeat(32);
			Files.writeString(root.resolve("owner.txt"), owner);
			Path partial = root.resolve("catalogs/0-" + "6".repeat(32));
			Files.createDirectories(partial);
			String pendingName = ".journal.properties.pending-" + "7".repeat(32);
			byte[] raw = "Generation=0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
			Files.write(partial.resolve(pendingName), raw);
			PhantomTrainingActors.recoverCatalogJournals(root);
			Path quarantined = partial.resolve("quarantine").resolve(pendingName + ".raw");
			PhantomAssertions.assertEquals(NeuralPolicy.sha256(raw), NeuralPolicy.sha256(Files.readAllBytes(quarantined)), "Partial journal bytes must be preserved exactly.");
			PhantomAssertions.assertEquals(NeuralPolicy.sha256(raw), Files.readString(quarantined.resolveSibling(quarantined.getFileName() + ".sha256")).trim(), "Quarantine must retain the original SHA before recovery.");
			var recovered = new java.util.Properties();
			try (var reader = Files.newBufferedReader(partial.resolve("journal.properties"))) { recovered.load(reader); }
			PhantomAssertions.assertEquals("INCOMPLETE", recovered.getProperty("phase"), "Ownerless initial write cannot become an active catalog.");
			PhantomAssertions.assertTrue(Files.isRegularFile(partial.resolve(pendingName)), "Original pending bytes must also remain available.");
			Path foreign = root.resolve("catalogs/1-" + "8".repeat(32));
			Files.createDirectory(foreign);
			Files.writeString(foreign.resolve(pendingName), "owner=LEARNING002:" + "9".repeat(32) + "\ngeneration=1\nphase=PREPARING\n");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> PhantomTrainingActors.recoverCatalogJournals(root), "A complete foreign owner must remain fail-closed.");
			PhantomAssertions.assertFalse(Files.exists(foreign.resolve("journal.properties")), "Recovery must not overwrite foreign ownership.");
		});
	}
	private static Path fixture(Path root, long generation, String owner, String selectors, String phase) throws Exception
	{
		Path catalog = root.resolve("catalogs").resolve(generation + "-" + "2".repeat(32));
		Files.createDirectories(catalog);
		Files.writeString(catalog.resolve("training-scenes.tsv"), "actual-scenes");
		Files.writeString(catalog.resolve("training-actors.tsv"), "actual-actor-ids");
		Files.writeString(catalog.resolve("training-manifest.sha256"), PhantomTrainingActors.catalogSeal(selectors, catalog));
		Files.writeString(catalog.resolve("journal.properties"), journal(owner, generation, selectors, phase));
		return catalog;
	}
	private static String journal(String owner, long generation, String selectors, String phase)
	{ return "owner=" + owner + "\ngeneration=" + generation + "\nselectors=" + selectors + "\nphase=" + phase + "\n"; }
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;

/** Negative FIT ingress checks with no native run and no optimizer credit. */
public final class PhantomLearning131FitChecks
{
	private static final String HASH = "1".repeat(64);

	private PhantomLearning131FitChecks() { }

	public static void main(String[] args) throws Exception
	{
		final Path reports = Path.of(System.getProperty("phantom.test.reports", ".")).toRealPath();
		final Path scratch = Files.createTempDirectory(reports, "fit131-contract-");
		try
		{
			final Path task = scratch.resolve(".phantom-local/logs/TASK131");
			final Path campaign = task.resolve("campaigns/train-01");
			final Path boot = Files.createDirectories(campaign.resolve("boots/00001"));
			final Path origin = Files.createDirectories(task.resolve("fit-pins")).resolve("origin.json");
			final var pins = new HashMap<String, Object>();
			pins.put("schema", "TRAINING_FIT_ORIGIN131_V1");
			pins.put("phase", "AUDIT");
			final String heldout = write(origin, pins);
			refuse(boot, origin, heldout, "TRAIN_ORIGIN_SCHEMA");
			pins.put("phase", "TRAIN");
			write(origin, pins);
			refuse(boot, origin, heldout, "ORIGINAL_SHA_OR_SIZE");
			final String manifestSha = write(campaign.resolve("campaign.json"), Map.of("schema", "TRAINING_COMBAT131_V1", "phase", "TRAIN",
				"campaignId", "train-01", "cycle", 1, "auditBank", 0, "sourceCommit", "a".repeat(40), "gameJarSha", HASH, "modelSha", HASH));
			final String closeSha = write(boot.resolve("closed.json"), Map.of("schema", "TRAINING_CLOSED131_V1", "status", "PAUSED", "reservationSha", HASH));
			pins.putAll(Map.of("manifestSha", manifestSha, "closedSha", closeSha, "expectedHead", HASH, "learnerSha", HASH,
				"opponentSha", HASH, "rollbackHead", HASH, "rollbackModelSha", HASH, "scenes", Map.of(HASH, Map.of("sourceSha", HASH, "policySha", HASH))));
			refuse(boot, origin, write(origin, pins), "TRAIN_NOT_DRAINED");
			pins.put("rollbackModelSha", "2".repeat(64));
			refuse(boot, origin, write(origin, pins), "ROLLBACK_PIN");
			if (Files.exists(task.resolve("fit-ledger"))) { throw new AssertionError("Rejected evidence created FIT state"); }
			System.out.println("TASK131_FIT_INGRESS_OFFLINE_CHECKS_PASS GS=0 LS=0 SQL=0 FIT=0");
		}
		finally
		{
			try (var paths = Files.walk(scratch))
			{
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
			}
		}
	}

	private static String write(Path path, Object value) throws Exception
	{
		Files.writeString(path, PhantomEvaluationOutcomeV2.jsonValue(value));
		return TacticalObservationV3.hash(Files.readAllBytes(path));
	}

	private static void refuse(Path boot, Path origin, String sha, String reason) throws Exception
	{
		try
		{
			PhantomLearning131Fit.run(new String[] { "prepare", boot.toString(), origin.toString(), sha });
		}
		catch (IllegalArgumentException rejected)
		{
			if (("FIT131_" + reason).equals(rejected.getMessage())) { return; }
			throw rejected;
		}
		throw new AssertionError("Accepted input without " + reason);
	}
}

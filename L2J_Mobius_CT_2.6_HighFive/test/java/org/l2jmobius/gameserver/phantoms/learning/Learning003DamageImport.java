/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;

/** TEST-only import of one historical immutable checkpoint, never a new training/audit claim. */
public final class Learning003DamageImport
{
	public static final String MODEL_SHA = "e0691f728847cebea7293d87e45acebbd3db5bc2d71b76c7c5e40549e13c43d2";
	public static final String ZIP_SHA = "10fc9c6c8c450e4974ca7abed861805a7640e2c67c8474ac5a4c96e003b493da";
	public static final String FEATURE_SCHEMA = "53a0b97cd4c771a2d2a93b46829273f312e0dfd77626989e92b9b52ae2dc1d34";
	public static final String CATALOG = "e46709db95ce8e92f4b4f392b5c468d147c6ddb11ab6c773cc48f3702bc6474a";
	private static final String DATASET = "202623c277aec76aadafdf3b203a48660461f0e0789b926ef37d102e121a2d77";
	private static final double INITIAL_LOSS = 1.577168254638786;
	private static final double AUDIT_LOSS = .05248381189002416;
	public record Imported(LearningPolicy policy, LearningStore.Checkpoint checkpoint, String manifest) { }

	public static Imported load(Path root, Path archive, TrainingConfig config) throws Exception
	{
		if (!Boolean.getBoolean("phantom.learning.train") || !root.toRealPath().equals(root) || !archive.toRealPath().equals(archive)
			|| !config.sha256().equals(TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini")).sha256()))
		{ throw new IllegalStateException("Exact own DAMAGE regression root/config required"); }
		final var ledger = new TrainingStore(root);
		final var store = ledger.learning();
		if (!FEATURE_SCHEMA.equals(LearningFeatures.SCHEMA) || !CATALOG.equals(store.catalogFingerprint()))
		{ throw new IllegalStateException("Historical DAMAGE schema/catalog mismatch; relabeling is forbidden"); }
		if (Files.size(archive) != 69127 || !ZIP_SHA.equals(NeuralPolicy.sha256(Files.readAllBytes(archive))))
		{ throw new IllegalStateException("Tracked accepted002 archive changed"); }
		final byte[] weights;
		final String manifest;
		try (var zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8))
		{
			final var names = zip.stream().map(entry -> entry.getName()).toList();
			if (names.size() != 4 || !Set.copyOf(names).equals(Set.of("weights.bin", "manifest.json", "dataset.jsonl", "episodes.jsonl"))
				|| zip.getEntry("weights.bin").getSize() != 12500 || zip.getEntry("manifest.json").getSize() != 1315)
			{ throw new IllegalStateException("Historical DAMAGE archive format changed"); }
			try (var input = zip.getInputStream(zip.getEntry("weights.bin"))) { weights = input.readNBytes(12501); }
			try (var input = zip.getInputStream(zip.getEntry("manifest.json"))) { manifest = new String(input.readNBytes(65537), StandardCharsets.UTF_8); }
		}
		if (weights.length != 12500 || NeuralPolicy.restore(weights, MODEL_SHA).algorithm() != NeuralPolicy.RESIDUAL_ALGORITHM)
		{ throw new IllegalStateException("Historical DAMAGE codec changed"); }
		TrainingStore.validateObservation(manifest);
		requireString(manifest, "origin", "NATIVE"); requireString(manifest, "status", "ACCEPTED_TEST");
		requireString(manifest, "weightsSha256", MODEL_SHA); requireString(manifest, "featureSchema", FEATURE_SCHEMA);
		requireString(manifest, "skillCatalog", CATALOG); requireString(manifest, "datasetHash", DATASET);
		requireNumber(manifest, "schemaVersion", 1); requireNumber(manifest, "modelVersion", 1); requireNumber(manifest, "algorithm", 2);
		requireNumber(manifest, "step", 47); requireNumber(manifest, "train", 33); requireNumber(manifest, "tuning", 45); requireNumber(manifest, "audit", 9);
		requireNumber(manifest, "randomInitialMse", INITIAL_LOSS); requireNumber(manifest, "auditMse", AUDIT_LOSS);
		if (!manifest.contains("\"productionPromotion\":false") || !manifest.contains("\"auditFresh\":true"))
		{ throw new IllegalStateException("Historical DAMAGE provenance changed"); }
		final LearningStore.Checkpoint checkpoint;
		try (var connection = DatabaseFactory.getConnection())
		{
			requireDatabase(connection, root, config);
			connection.setAutoCommit(false);
			try
			{
				boolean exists;
				try (var sql = connection.prepareStatement("SELECT model_id FROM phantom_ai_model_versions WHERE model_id=1 FOR UPDATE"); var rows = sql.executeQuery()) { exists = rows.next(); }
				if (!exists)
				{
					try (var sql = connection.prepareStatement("INSERT INTO phantom_ai_model_versions (model_id,algo_version,schema_fingerprint,skill_catalog,step,training_hash,weights_blob,sha256,training_count,holdout_count,initial_mse,holdout_mse,origin,status) VALUES (1,2,?,?,47,?,?,?,33,9,?,?,'NATIVE','ACCEPTED')"))
					{
						sql.setString(1, FEATURE_SCHEMA); sql.setString(2, CATALOG); sql.setString(3, DATASET); sql.setBytes(4, weights);
						sql.setString(5, MODEL_SHA); sql.setDouble(6, INITIAL_LOSS); sql.setDouble(7, AUDIT_LOSS);
						if (sql.executeUpdate() != 1) { throw new IllegalStateException("Historical DAMAGE model insert failed"); }
					}
				}
				checkpoint = store.read(connection, 1, "ACCEPTED", "NATIVE");
				requireCheckpoint(checkpoint);
				if (!Arrays.equals(weights, checkpoint.model().bytes())) { throw new IllegalStateException("Historical DAMAGE bytes differ"); }
				connection.commit();
			}
			catch (Exception failure) { connection.rollback(); throw failure; }
		}
		// This second connection is the same cold loader used by configured Combat policy.
		requireCheckpoint(store.accepted(1));
		final var policy = new LearningPolicy(LearningPolicy.Mode.ON, 1, checkpoint.model(), 4, 0, store.new Writer());
		return new Imported(policy, checkpoint, manifest);
	}

	public static void verifyUnchanged(Path root, TrainingConfig config) throws Exception
	{
		final var store = new TrainingStore(root).learning();
		try (var connection = DatabaseFactory.getConnection())
		{
			requireDatabase(connection, root, config);
			requireCheckpoint(store.read(connection, 1, "ACCEPTED", "NATIVE"));
		}
	}

	private static void requireDatabase(Connection connection, Path root, TrainingConfig config) throws Exception
	{
		final int port = config.integer("sqlPort", 33327, 33000, 65535);
		TrainingStore.requireEndpoint(DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_LOGIN, port);
		try (var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT DATABASE(),@@port,VERSION(),CURRENT_USER(),@@datadir"))
		{
			if (!rows.next() || !TrainingStore.SCHEMA.equals(rows.getString(1)) || rows.getInt(2) != port || !rows.getString(3).contains("MariaDB")
				|| !rows.getString(4).startsWith("learning001@") || !Path.of(rows.getString(5)).toRealPath().equals(root.resolve("sql/data").toRealPath()))
			{ throw new IllegalStateException("Current DAMAGE SQL connection is not the owned TEST003 installation"); }
		}
	}

	private static void requireCheckpoint(LearningStore.Checkpoint checkpoint)
	{
		if (checkpoint == null || checkpoint.id() != 1 || checkpoint.model().algorithm() != 2 || checkpoint.model().bytes().length != 12500
			|| !MODEL_SHA.equals(checkpoint.model().sha256()) || !DATASET.equals(checkpoint.trainingHash()) || checkpoint.step() != 47
			|| checkpoint.trainCount() != 33 || checkpoint.holdoutCount() != 9 || checkpoint.initialLoss() != INITIAL_LOSS || checkpoint.loss() != AUDIT_LOSS)
		{ throw new IllegalStateException("Conflicting historical DAMAGE checkpoint; overwrite is forbidden"); }
	}

	private static void requireString(String json, String field, String expected)
	{
		final var matches = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
		boolean found = false;
		while (matches.find()) { found = true; if (!expected.equals(matches.group(1))) { throw new IllegalStateException("Historical manifest field changed: " + field); } }
		if (!found) { throw new IllegalStateException("Historical manifest field missing: " + field); }
	}

	private static void requireNumber(String json, String field, double expected)
	{
		final var matches = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)").matcher(json);
		boolean found = false;
		while (matches.find()) { found = true; if (Double.parseDouble(matches.group(1)) != expected) { throw new IllegalStateException("Historical manifest value changed: " + field); } }
		if (!found) { throw new IllegalStateException("Historical manifest value missing: " + field); }
	}

	private Learning003DamageImport() { }
}

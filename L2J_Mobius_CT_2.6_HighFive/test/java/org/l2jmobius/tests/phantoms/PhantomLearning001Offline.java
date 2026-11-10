/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;

import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.phantoms.learning.LearningFeatures;
import org.l2jmobius.gameserver.phantoms.learning.LearningStore;
import org.l2jmobius.gameserver.phantoms.learning.LearningStore.Checkpoint;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy.Example;

/** Offline operator phases for the owned native cycle, after a clean GameServer stop. */
public final class PhantomLearning001Offline
{
	private static final String URL = "jdbc:mysql://127.0.0.1:33317/phantom_learning001_native";
	private static final String LOGIN = "learning001";
	private static final String NATIVE_WHERE = "origin='NATIVE' AND evidence_kind='EXACT_CAST_HP' AND damage>0 AND schema_fingerprint=?";
	private static final Pattern EXACT_REORDER = Pattern.compile("LEARNING_EXACT_REORDER scope=(SOLO|PARTY) version=([0-9]+)"
		+ " baseline=([0-9]+):([0-9]+) actual=([0-9]+):([0-9]+) .* eventKey=([0-9a-f]{64}) hp=([^ ]+) decisionNanos=([0-9]+)");
	// Diagnostic projection mirrored from LearningStore.contextKey. Training and promotion always use LearningStore.
	private static final int[] CONTEXT_SLOTS = {0, 3, 4, 5, 6, 7, 8, 12, 13, 14, 15, 19, 20, 28, 30, 31, 32, 41, 43, 44, 45, 46};

	private record Sample(Example example, String actor, String scope, int skill, String context) { }

	private record Coverage(String catalog, String hash, List<Sample> train, List<Sample> tuning, List<Sample> audit, List<Sample> independent)
	{
		void report(int limit)
		{
			System.out.printf(Locale.ROOT, "LEARNING001_OFFLINE_INPUT schema=%s catalog=%s limit=%d trainingHash=%s rows=%d auditOverlapRows=%d%n",
				LearningFeatures.SCHEMA, catalog, limit, hash, train.size() + tuning.size() + audit.size(), audit.size() - independent.size());
			reportSplit("TRAIN", train);
			reportSplit("TUNING", tuning);
			reportSplit("AUDIT_RAW", audit);
			reportSplit("AUDIT_INDEPENDENT", independent);
		}
	}

	public static void main(String[] args) throws Exception
	{
		final int length = args.length == 0 ? 0 : switch (args[0])
		{
			case "coverage" -> 4;
			case "train", "promote", "read" -> 5;
			case "verify-on" -> 6;
			default -> 0;
		};
		if (length == 0 || args.length != length || !args[2].matches("cycle(?:1[2-9]|[2-9][0-9]|[1-9][0-9]{2})") || !Boolean.getBoolean("phantom.learning.train"))
		{
			throw new IllegalArgumentException("With -Dphantom.learning.train=true, use coverage <module> <lane> <limit> | train <module> <lane> <limit> <seed> | promote/read <module> <lane> <version> <sha256> | verify-on <module> <lane> <version> <sha256> <attempt>");
		}
		final Path lane = PhantomLearning001FixtureSql.resolveLane(args[1], args[2]);
		final Path game = lane.resolve("game");
		require(game.equals(game.toRealPath()) && Files.readString(lane.resolve("owner.txt")).matches("LEARNING001:[0-9a-f]{32}"), "OWNED_CYCLE");
		final Path db = lane.resolve("Database.ini");
		require(Files.mismatch(db, game.resolve("config/Database.ini")) == -1, "OWNED_DATABASE_CONFIG");
		final Path jar = game.resolve("libs/GameServer.jar");
		for (Class<?> type : List.of(LearningStore.class, LearningFeatures.class, NeuralPolicy.class))
		{
			require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar), "JAR_CLASS_ORIGIN");
		}
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(db)) { settings.load(reader); }
		final String password = settings.getProperty("Password");
		require(URL.equals(settings.getProperty("URL")) && LOGIN.equals(settings.getProperty("Login")) && password != null && !password.isBlank(), "OWNED_ENDPOINT");
		final var credentials = new Properties();
		credentials.setProperty("user", LOGIN);
		credentials.setProperty("password", password);
		credentials.setProperty("connectTimeout", "2000");
		credentials.setProperty("socketTimeout", "3000");
		try (var connection = DriverManager.getConnection(URL, credentials))
		{
			connection.setReadOnly(true);
			PhantomLearning001FixtureSql.requireActorIdentity(connection, lane);
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND DB=DATABASE()"))
			{
				require(rows.next() && rows.getInt(1) == 0, "GAME_SERVER_MUST_BE_STOPPED");
			}
			final var store = new LearningStore(URL, LOGIN, password, game);
			if ("coverage".equals(args[0]) || "train".equals(args[0]))
			{
				final int configuredLimit = new ConfigReader(game.resolve("config/Custom/PhantomPlayers.ini").toString()).getInt("LearningMaxExamplesPerBatch", NeuralPolicy.MAX_EXAMPLES);
				final int limit = Math.clamp(Math.min(Integer.parseInt(args[3]), configuredLimit), 32, NeuralPolicy.MAX_EXAMPLES);
				final Coverage coverage = coverage(connection, limit);
				coverage.report(limit);
				if ("train".equals(args[0])) { train(connection, store, limit, Long.parseLong(args[4]), coverage); }
				return;
			}
			final long version = Long.parseLong(args[3]);
			final String sha = args[4];
			require(version > 0 && sha.matches("[0-9a-f]{64}"), "MODEL_ID_AND_SHA");
			if ("promote".equals(args[0]) && store.accepted(version) == null)
			{
				reportModel(connection, version, sha, "DRAFT", null, null);
				require(store.promote(version), "PROMOTION_REFUSED");
			}
			final Checkpoint accepted = store.accepted(version);
			require(accepted != null && sha.equals(accepted.model().sha256()), "ACCEPTED_READBACK");
			reportModel(connection, version, sha, "ACCEPTED", accepted, null);
			if ("verify-on".equals(args[0])) { verifyOn(connection, lane, args[5], version, sha); }
		}
	}

	private static void verifyOn(Connection connection, Path lane, String id, long version, String sha) throws Exception
	{
		require(id.matches("[0-9a-f]{32}"), "ATTEMPT_ID");
		final Path attempt = lane.resolve("attempts").resolve(id);
		require(attempt.toRealPath().equals(attempt) && !Files.exists(attempt.resolve("on.failed")), "SUCCESSFUL_ATTEMPT");
		final var completion = new Properties();
		try (var reader = Files.newBufferedReader(attempt.resolve("on.complete"))) { completion.load(reader); }
		require(Long.toString(version).equals(completion.getProperty("modelVersion")) && sha.equals(completion.getProperty("modelSha")), "EXACT_ATTEMPT_MODEL");
		final var keys = new java.util.HashSet<String>();
		final var scopes = new java.util.HashSet<String>();
		try (var sql = connection.prepareStatement("SELECT scope,skill_id,skill_level,damage FROM phantom_ai_training_examples WHERE " + NATIVE_WHERE
			+ " AND event_key=? AND policy_version=? AND skill_catalog=(SELECT skill_catalog FROM phantom_ai_model_versions WHERE model_id=?)"))
		{
			for (String name : List.of("game.out", "game.err"))
			{
				try (var lines = Files.lines(attempt.resolve(name), StandardCharsets.ISO_8859_1))
				{
					for (String line : (Iterable<String>) lines::iterator)
					{
						final var match = EXACT_REORDER.matcher(line);
						if (!match.find() || !keys.add(match.group(7))) { continue; }
						final String scope = match.group(1);
						final int skill = Integer.parseInt(match.group(5));
						final int level = Integer.parseInt(match.group(6));
						final double hp = Double.parseDouble(match.group(8));
						require(Long.parseLong(match.group(2)) == version && hp > 0
							&& (!match.group(3).equals(match.group(5)) || !match.group(4).equals(match.group(6))), "CHANGED_NATIVE_CAST");
						sql.setString(1, LearningFeatures.SCHEMA);
						sql.setString(2, match.group(7));
						sql.setLong(3, version);
						sql.setLong(4, version);
						try (var row = sql.executeQuery())
						{
							require(row.next() && scope.equals(row.getString(1)) && skill == row.getInt(2) && level == row.getInt(3)
								&& Double.compare(hp, row.getDouble(4)) == 0 && !row.next(), "SAME_NATIVE_EVENT_SQL_READBACK");
						}
						scopes.add(scope);
						System.out.println("LEARNING001_ON_EVENT_SQL_PASS scope=" + scope + " version=" + version + " eventKey=" + match.group(7)
							+ " baseline=" + match.group(3) + ":" + match.group(4) + " skill=" + skill + ":" + level + " hp=" + hp);
					}
				}
			}
		}
		require(scopes.equals(java.util.Set.of("SOLO", "PARTY")), "BOTH_NATIVE_SCOPES_IN_SQL");
	}

	private static Coverage coverage(Connection connection, int limit) throws Exception
	{
		final String catalog;
		try (var sql = connection.prepareStatement("SELECT DISTINCT skill_catalog FROM phantom_ai_training_examples WHERE " + NATIVE_WHERE))
		{
			sql.setString(1, LearningFeatures.SCHEMA);
			try (var rows = sql.executeQuery())
			{
				if (rows.next())
				{
					catalog = rows.getString(1);
					require(!rows.next(), "MULTIPLE_NATIVE_CATALOGS");
				}
				else
				{
					catalog = "none";
				}
			}
		}
		final var provenance = MessageDigest.getInstance("SHA-256");
		final List<Sample> train = new ArrayList<>();
		final List<Sample> tuning = new ArrayList<>();
		final List<Sample> audit = new ArrayList<>();
		try (var sql = connection.prepareStatement("SELECT event_key,actor_group,skill_id,scope,features,reward FROM phantom_ai_training_examples WHERE "
			+ NATIVE_WHERE + " AND skill_catalog=? ORDER BY created_at DESC,event_key LIMIT ?"))
		{
			sql.setString(1, LearningFeatures.SCHEMA);
			sql.setString(2, catalog);
			sql.setInt(3, limit);
			try (var rows = sql.executeQuery())
			{
				while (rows.next())
				{
					final String actor = rows.getString(2);
					final byte[] features = rows.getBytes(5);
					final float reward = rows.getFloat(6);
					final var digest = MessageDigest.getInstance("SHA-256");
					for (int slot : CONTEXT_SLOTS) { digest.update(features, slot * 2 * Float.BYTES, 2 * Float.BYTES); }
					final var sample = new Sample(new Example(LearningFeatures.decode(features), reward), actor, rows.getString(4), rows.getInt(3), HexFormat.of().formatHex(digest.digest()));
					switch (Math.floorMod(actor.hashCode(), 5))
					{
						case 0 -> audit.add(sample);
						case 1 -> tuning.add(sample);
						default -> train.add(sample);
					}
					provenance.update(rows.getString(1).getBytes(StandardCharsets.US_ASCII));
					provenance.update(features);
					provenance.update(ByteBuffer.allocate(Float.BYTES).putFloat(reward).array());
				}
			}
		}
		final var fitted = Stream.concat(train.stream(), tuning.stream()).map(Sample::context).collect(Collectors.toSet());
		final var independent = audit.stream().filter(row -> !fitted.contains(row.context())).toList();
		return new Coverage(catalog, HexFormat.of().formatHex(provenance.digest()), train, tuning, audit, independent);
	}

	private static void reportSplit(String name, List<Sample> rows)
	{
		final var counts = rows.stream().collect(Collectors.groupingBy(row -> row.scope() + "/" + row.skill(), TreeMap::new, Collectors.counting()));
		System.out.printf(Locale.ROOT, "LEARNING001_OFFLINE_SPLIT split=%s rows=%d actors=%d scopes=%d skills=%d contexts=%d byScopeSkill=%s%n",
			name, rows.size(), rows.stream().map(Sample::actor).distinct().count(), rows.stream().map(Sample::scope).distinct().count(),
			rows.stream().map(Sample::skill).distinct().count(), rows.stream().map(Sample::context).distinct().count(), counts);
	}

	private static void train(Connection connection, LearningStore store, int limit, long seed, Coverage coverage) throws Exception
	{
		System.out.println("LEARNING001_OFFLINE_TRAIN seed=" + seed);
		final Checkpoint checkpoint;
		try
		{
			checkpoint = store.trainNative(limit, seed);
		}
		catch (IllegalArgumentException failure)
		{
			if ("Checkpoint did not meet validation threshold".equals(failure.getMessage()))
			{
				final var tuned = NeuralPolicy.train(coverage.train().stream().map(Sample::example).toList(), coverage.tuning().stream().map(Sample::example).toList(), seed);
				final var audit = NeuralPolicy.audit(tuned, coverage.independent().stream().map(Sample::example).toList(), seed);
				System.out.printf(Locale.ROOT, "LEARNING001_OFFLINE_QUALITY status=REFUSED step=%d tuningInitialMse=%s tuningMse=%s auditInitialMse=%s auditMse=%s%n",
					audit.step(), tuned.initialLoss(), tuned.loss(), audit.initialLoss(), audit.loss());
			}
			throw failure;
		}
		require(checkpoint.trainingHash().equals(coverage.hash()) && checkpoint.trainCount() == coverage.train().size()
			&& checkpoint.holdoutCount() == coverage.independent().size(), "TRAINING_COVERAGE_READBACK");
		reportModel(connection, checkpoint.id(), checkpoint.model().sha256(), "DRAFT", checkpoint, coverage.catalog());
	}

	private static void reportModel(Connection connection, long id, String sha, String status, Checkpoint checkpoint, String catalog) throws Exception
	{
		try (var sql = connection.prepareStatement("SELECT status,origin,algo_version,schema_fingerprint,skill_catalog,training_hash,sha256,OCTET_LENGTH(weights_blob),SHA2(weights_blob,256),"
			+ "step,training_count,holdout_count,initial_mse,holdout_mse FROM phantom_ai_model_versions WHERE model_id=?"))
		{
			sql.setLong(1, id);
			try (var rows = sql.executeQuery())
			{
				require(rows.next() && status.equals(rows.getString(1)) && "NATIVE".equals(rows.getString(2))
					&& NeuralPolicy.ALGORITHM == rows.getInt(3) && LearningFeatures.SCHEMA.equals(rows.getString(4))
					&& sha.equals(rows.getString(7)) && NeuralPolicy.BYTE_SIZE == rows.getInt(8) && sha.equals(rows.getString(9)), "MODEL_SQL_IDENTITY");
				final String actualCatalog = rows.getString(5);
				final String trainingHash = rows.getString(6);
				final int step = rows.getInt(10);
				final int train = rows.getInt(11);
				final int audit = rows.getInt(12);
				final double initial = rows.getDouble(13);
				final double loss = rows.getDouble(14);
				require(catalog == null || catalog.equals(actualCatalog), "MODEL_CATALOG_READBACK");
				require(checkpoint == null || (checkpoint.trainingHash().equals(trainingHash) && checkpoint.step() == step && checkpoint.trainCount() == train
					&& checkpoint.holdoutCount() == audit && checkpoint.initialLoss() == initial && checkpoint.loss() == loss), "MODEL_METRICS_READBACK");
				System.out.printf(Locale.ROOT, "LEARNING001_OFFLINE_MODEL version=%d status=%s origin=NATIVE algo=%d sha256=%s schema=%s catalog=%s trainingHash=%s bytes=%d step=%d train=%d audit=%d initialMse=%s auditMse=%s sqlDigest=PASS%n",
					id, status, NeuralPolicy.ALGORITHM, sha, LearningFeatures.SCHEMA, actualCatalog, trainingHash, NeuralPolicy.BYTE_SIZE, step, train, audit, initial, loss);
			}
		}
	}

	private static void require(boolean ok, String message)
	{
		if (!ok) { throw new IllegalStateException("LEARNING001_OFFLINE_" + message); }
	}
}

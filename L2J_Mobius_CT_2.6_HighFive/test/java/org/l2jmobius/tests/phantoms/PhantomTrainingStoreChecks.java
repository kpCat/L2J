/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.l2jmobius.gameserver.phantoms.learning.LearningFeatures;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TrainingCollector;
import org.l2jmobius.gameserver.phantoms.learning.TrainingEvaluator;
import org.l2jmobius.gameserver.phantoms.learning.TrainingStore;

/** Focused DB-free rules; optional integration runs only against an attested owned installation. */
public final class PhantomTrainingStoreChecks
{
	public static void main(String[] args) throws Exception
	{
		endpoint();
		quality();
		observations();
		auditCohorts();
		coverageFacts();
		if (args.length == 1) { incomplete(Path.of(args[0])); }
		else if (args.length != 0) { throw new IllegalArgumentException("Expected an optional owned installation path"); }
		System.out.println("Проверки изоляции, качества и контракта эпизодов завершены.");
	}

	private static void endpoint()
	{
		TrainingStore.requireEndpoint("jdbc:mysql://127.0.0.1:33327/phantom_learning001_train002", "learning001", 33327);
		for (String url : List.of("jdbc:mysql://localhost:33327/phantom_learning001_train002",
			"jdbc:mysql://127.0.0.1:33317/phantom_learning001_train002",
			"jdbc:mysql://127.0.0.1:33327/phantom_learning001_native",
			"jdbc:mysql://127.0.0.1:33327/phantom_learning001_train002?allowMultiQueries=true"))
		{
			reject(() -> TrainingStore.requireEndpoint(url, "learning001", 33327), "Foreign SQL endpoint accepted");
		}
		reject(() -> TrainingStore.requireEndpoint("jdbc:mysql://127.0.0.1:33327/phantom_learning001_train002", "root", 33327), "Administrator accepted");
	}

	private static void quality()
	{
		check(TrainingEvaluator.qualityPasses(.04, .10, .08, .01), "Independent improvement rejected");
		check(!TrainingEvaluator.qualityPasses(.09, .10, .08, .01), "Worse than prior accepted");
		check(!TrainingEvaluator.qualityPasses(.11, .10, null, .01), "Worse than constant accepted");
		check(!TrainingEvaluator.qualityPasses(Double.NaN, .10, .08, .01), "NaN accepted");
		check(!TrainingEvaluator.qualityPasses(0, 0, null, .01), "Zero-information audit accepted");
	}

	private static void auditCohorts()
	{
		final String actor = "b".repeat(64);
		final String first = TrainingStore.auditCohort(actor, "actual-scene", 7);
		final String ordinalOnly = TrainingStore.auditCohort(actor, "actual-scene", 8);
		check(first.equals(ordinalOnly), "Changing an ordinal-only seed made the same actual audit cohort fresh");
		check(java.util.Set.of(first).contains(ordinalOnly), "Consumed actual cohort became reusable with seed metadata");
		check(!first.equals(TrainingStore.auditCohort("c".repeat(64), "actual-scene", 7)), "A genuinely new actor kept the consumed cohort");
		check(!first.equals(TrainingStore.auditCohort(actor, "different-actual-scene", 7)), "A genuinely different scene kept the consumed cohort");
	}

	private static void coverageFacts()
	{
		final String observed = "{\"npcId\":21798,\"learnerClass\":94,\"level\":77,\"grade\":\"S\",\"catalogGeneration\":2,\"observed\":{\"helpers\":[{\"learnerClass\":97}]}}";
		final var actual = TrainingStore.coverageFacts(observed, true);
		check(actual.npcId().equals("21798") && actual.learnerClass().equals("94") && actual.grade().equals("S"), "Actual learner coverage was lost or replaced by helper class");
		final var older = TrainingStore.coverageFacts("{\"observed\":{\"helpers\":[{\"learnerClass\":97}]}}", true);
		check(older.npcId().equals("UNKNOWN") && older.learnerClass().equals("UNKNOWN") && older.grade().equals("UNKNOWN"), "Missing old facts were inferred from helper/reward data");
		check(TrainingStore.coverageFacts(observed, false).learnerClass().equals("UNKNOWN"), "Uncommitted metadata claimed native learned-class coverage");
		check(TrainingStore.coverageFacts("{\"provenance\":\"SIMULATED\",\"learnerClass\":97}", true).learnerClass().equals("UNKNOWN"), "Simulated facts claimed native learned-class coverage");
	}

	private static void incomplete(Path root) throws Exception
	{
		final var store = new TrainingStore(root);
		store.initialize();
		store.requireStopped();
		coverageUtc(root, store);
		final String run = UUID.randomUUID().toString().replace("-", "");
		store.recover(run, run);
		final long ordinal = store.nextOrdinal();
		final String episode = store.begin(run, run, ordinal, "contract-empty", 11, "SOLO", "AUDIT", "{}");
		store.finish(episode, "{}", List.of(), "NO_EXACT_EVIDENCE", 1);
		final var count = store.counts();
		check(count.incomplete() > 0 && count.nextOrdinal() > ordinal, "Incomplete episode lost its durable ordinal");
		rejectChecked(() -> store.begin(run, run, ordinal, "contract-empty", 11, "SOLO", "AUDIT", "{}"), "Consumed ordinal replay accepted");
		store.abort(episode, "repeat");
		check(store.counts().committed() == count.committed() && store.counts().events() == count.events(), "Incomplete episode gained learning credit");
		final String failed = store.begin(run, run, store.nextOrdinal(), "contract-aborted-facts", 14, "PARTY", "AUDIT", "{}");
		final String afterFacts = "{\"schemaVersion\":1,\"provenance\":\"UNKNOWN\",\"diagnostic\":true,\"hp\":null}";
		store.abort(failed, "CONTRACT_ABORTED", afterFacts, 137);
		store.abort(failed, "repeat");
		final var failedCount = store.counts();
		check(failedCount.events() == count.events() && failedCount.committed() == count.committed(), "Aborted facts gained native event credit");
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve("game/config/Database.ini"))) { settings.load(reader); }
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"));
			var sql = connection.prepareStatement("SELECT state,after_json,elapsed_millis,outcome FROM phantom_training_episodes WHERE episode_id=?"))
		{
			sql.setString(1, failed);
			try (var rows = sql.executeQuery())
			{ check(rows.next() && "INCOMPLETE".equals(rows.getString(1)) && afterFacts.equals(rows.getString(2)) && rows.getLong(3) == 137 && "CONTRACT_ABORTED".equals(rows.getString(4)), "Aborted episode lost observed after facts or elapsed time"); }
		}
		synthetic(root, store, run);
		nativeReplay(root, store, run);
	}

	private static void coverageUtc(Path root, TrainingStore store) throws Exception
	{
		final var coverage = store.coverage();
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve("game/config/Database.ini"))) { settings.load(reader); }
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"));
			var sql = connection.prepareStatement("SELECT MIN(UNIX_TIMESTAMP(created_at)*1000) FROM phantom_training_episodes WHERE ordinal>=? AND ordinal<?"))
		{
			sql.setLong(1, coverage.firstOrdinal()); sql.setLong(2, coverage.nextOrdinal());
			try (var rows = sql.executeQuery())
			{
				if (!rows.next()) { throw new AssertionError("Missing SQL coverage epoch result"); }
				final long expectedEpochMillis = rows.getLong(1);
				if (rows.wasNull()) { System.out.println("COVERAGE_UTC_NOT_RUN: сохранённых эпизодов пока нет."); return; }
				check(coverage.windowStartUtc() != null && java.time.Instant.parse(coverage.windowStartUtc()).toEpochMilli() == expectedEpochMillis,
					"Coverage UTC window differs from authoritative SQL epoch and depends on JVM timezone");
				System.out.println("COVERAGE_UTC_PASS expectedEpochMillis=" + expectedEpochMillis + " actualStartUtc=" + coverage.windowStartUtc() + " windowMillis=" + coverage.windowMillis());
			}
		}
	}

	private static void nativeReplay(Path root, TrainingStore store, String run) throws Exception
	{
		store.requireStopped();
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve("game/config/Database.ini"))) { settings.load(reader); }
		final String key;
		final String scope;
		final String split;
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"));
			var sql = connection.createStatement(); var rows = sql.executeQuery("SELECT e.event_key,p.scope,p.split FROM phantom_training_events e JOIN phantom_training_episodes p ON p.episode_id=e.episode_id WHERE p.state='COMMITTED' AND e.origin='NATIVE' AND e.evidence_kind='EXACT_CAST_HP' AND e.damage>0 ORDER BY p.ordinal DESC,e.event_key LIMIT 1"))
		{
			if (!rows.next()) { System.out.println("NATIVE_REPLAY_NOT_RUN: подтверждённого реального native-события пока нет."); return; }
			key = rows.getString(1); scope = rows.getString(2); split = rows.getString(3);
		}
		final var before = store.counts();
		final String episode = store.begin(run, run, store.nextOrdinal(), "contract-native-replay", 13, scope, split, "{\"test\":\"REAL_EVENT_REPLAY_NEGATIVE\",\"provenance\":\"UNKNOWN\"}");
		final boolean committed = store.finish(episode, "{}", List.of(new TrainingCollector.Observation(key,
			"{\"test\":\"REAL_EVENT_REPLAY_NEGATIVE\",\"provenance\":\"UNKNOWN\",\"sourceEventKey\":\"" + key + "\"}")), "NATIVE_REPLAY_REFUSED", 1);
		check(!committed, "An already committed real native event was assigned to a new epoch episode");
		final var after = store.counts();
		check(after.events() == before.events() && after.committed() == before.committed(), "Native replay increased ledger credit");
		check(after.incomplete() == before.incomplete() + 1, "Rejected native replay did not remain INCOMPLETE");
		check(after.attempts() == before.attempts() && after.accepted() == before.accepted(), "Native replay changed model attempt history");
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"));
			var sql = connection.prepareStatement("SELECT p.state,COUNT(e.event_key) FROM phantom_training_episodes p LEFT JOIN phantom_training_events e ON e.episode_id=p.episode_id WHERE p.episode_id=? GROUP BY p.state"))
		{
			sql.setString(1, episode);
			try (var rows = sql.executeQuery())
			{ check(rows.next() && "INCOMPLETE".equals(rows.getString(1)) && rows.getLong(2) == 0, "Native replay episode has an unexpected SQL state or event membership"); }
		}
		System.out.println("NATIVE_REPLAY_PASS eventKey=" + key + " replayEpisode=" + episode + " eventsBefore=" + before.events() + " eventsAfter=" + after.events()
			+ " committedBefore=" + before.committed() + " committedAfter=" + after.committed());
	}

	private static void synthetic(Path root, TrainingStore store, String run) throws Exception
	{
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve("game/config/Database.ini"))) { settings.load(reader); }
		final String key = NeuralPolicy.sha256(UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		final String episode = store.begin(run, run, store.nextOrdinal(), "contract-synthetic", 12, "SOLO", "AUDIT", "{}");
		final long before = store.counts().events();
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			try
			{
				try (var sql = connection.prepareStatement("INSERT INTO phantom_ai_training_examples (event_key,actor_group,scope,origin,evidence_kind,policy_version,schema_fingerprint,skill_catalog,skill_id,skill_level,damage,reward,features) VALUES (?,?,'SOLO','SYNTHETIC','FIXTURE',0,?,?,1230,1,1,0,?)"))
				{
					sql.setString(1, key); sql.setString(2, "b".repeat(64)); sql.setString(3, LearningFeatures.SCHEMA); sql.setString(4, "0".repeat(64)); sql.setBytes(5, LearningFeatures.encode(new float[LearningFeatures.SIZE])); sql.executeUpdate();
				}
				check(!store.finish(episode, "{}", List.of(new TrainingCollector.Observation(key, "{\"provenance\":\"SYNTHETIC\"}")), "SYNTHETIC_REFUSED", 1), "Synthetic row committed as native");
				check(store.counts().events() == before, "Synthetic observation gained training credit");
			}
			finally
			{
				try (var sql = connection.prepareStatement("DELETE FROM phantom_ai_training_examples WHERE event_key=? AND origin='SYNTHETIC'"))
				{ sql.setString(1, key); sql.executeUpdate(); }
			}
		}
	}

	private static void observations()
	{
		TrainingStore.validateObservation("{\"origin\":\"UNKNOWN\",\"hp\":null,\"mask\":false,\"allies\":[{\"level\":77}]}");
		for (String invalid : List.of("{broken}", "{\"hp\":NaN}", "{\"hp\":1,}", "{\"hp\":01}", "{\"hp\":1} {}"))
		{ reject(() -> TrainingStore.validateObservation(invalid), "Malformed observation accepted"); }
	}

	private static void reject(Runnable operation, String message)
	{
		try { operation.run(); }
		catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}

	@FunctionalInterface
	private interface Checked { void run() throws Exception; }

	private static void rejectChecked(Checked operation, String message) throws Exception
	{
		try { operation.run(); }
		catch (IllegalArgumentException | IllegalStateException expected) { return; }
		throw new AssertionError(message);
	}

	private static void check(boolean ok, String message)
	{
		if (!ok) { throw new AssertionError(message); }
	}
}

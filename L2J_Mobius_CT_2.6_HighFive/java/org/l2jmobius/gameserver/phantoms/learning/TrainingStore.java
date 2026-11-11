/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/** Durable TEST episode authority. No instance is used from a native Combat tick. */
public final class TrainingStore
{
	public static final String SCHEMA = "phantom_learning001_train002";
	private static final int MAX_JSON = 262144;
	private final Path _installation;
	private final Path _database;
	private final String _databaseHash;
	private final String _owner;
	private final String _identity;
	private final String _url;
	private final int _port;
	private final Properties _credentials = new Properties();
	private final TrainingConfig _config;
	private final LearningStore _learning;

	public record Counts(long committed, long incomplete, long events, long attempts, long accepted, long nextOrdinal) { }
	public record CoverageFacts(String npcId, String learnerClass, String level, String grade, String catalogGeneration) { }
	record Control(long ordinal, String run, String epoch, long checkpointEpoch, long model, String sha, String evaluating) { }
	record Sample(String key, String episode, String actor, String scope, String split, String scenario,
		long seed, long policyVersion, int skill, int level, double damage, float reward, byte[] features, String context)
	{
		Sample { features = features.clone(); }
		@Override public byte[] features() { return features.clone(); }
		NeuralPolicy.Example example() { return new NeuralPolicy.Example(LearningFeatures.decode(features), reward); }
		String cohort() { return auditCohort(actor, scenario, seed); }
	}
	record Snapshot(String id, long seed, String datasetHash, long expectedEpoch, long priorModel, String priorSha, List<Sample> samples)
	{
		Snapshot { samples = List.copyOf(samples); }
		List<NeuralPolicy.Example> examples(String split)
		{ return samples.stream().filter(row -> split.equals(row.split())).map(Sample::example).toList(); }
	}

	public TrainingStore(Path installation) throws IOException
	{
		_installation = installation.toAbsolutePath().normalize();
		physical(_installation);
		final Path owner = _installation.resolve("owner.txt");
		physical(owner);
		_owner = Files.readString(owner, StandardCharsets.UTF_8);
		if (!_owner.matches("LEARNING002:[0-9a-f]{32}")) { throw new IllegalArgumentException("Не подтверждён владелец тренировочной установки."); }
		_identity = hash(_owner);
		final Path config = _installation.resolve("TRAIN_CONFIG.ini");
		physical(config);
		_config = TrainingConfig.load(config);
		_port = _config.integer("sqlPort", 33327, 33000, 65535);
		_database = _installation.resolve("game/config/Database.ini");
		physical(_database);
		final byte[] bytes = Files.readAllBytes(_database);
		_databaseHash = NeuralPolicy.sha256(bytes);
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(_database, StandardCharsets.UTF_8)) { settings.load(reader); }
		_url = settings.getProperty("URL");
		requireEndpoint(_url, settings.getProperty("Login"), _port);
		final String password = settings.getProperty("Password");
		if (password == null || password.isBlank()) { throw new IllegalArgumentException("Отсутствует пароль собственной тренировочной схемы."); }
		_credentials.setProperty("user", "learning001");
		_credentials.setProperty("password", password);
		_credentials.setProperty("connectTimeout", "2000");
		_credentials.setProperty("socketTimeout", "5000");
		_learning = new LearningStore(_url, "learning001", password, _installation.resolve("game"));
	}

	public static void requireEndpoint(String url, String username, int configuredPort)
	{
		if (configuredPort < 33000 || configuredPort > 65535 || !"learning001".equals(username)
			|| !("jdbc:mysql://127.0.0.1:" + configuredPort + "/" + SCHEMA).equals(url))
		{ throw new IllegalArgumentException("Разрешена только собственная loopback-схема тренажёра и учётная запись learning001."); }
	}

	public static String auditCohort(String actorGroup, String actualScenario, long recordedSeed)
	{
		// An ordinal-derived seed describes an episode; it does not create a new observed cohort.
		return hash(actorGroup + ":" + actualScenario);
	}

	/** Only committed, explicitly observed top-level learner facts contribute category coverage. */
	public static CoverageFacts coverageFacts(String beforeJson, boolean nativeCommitted)
	{
		final var unknown = new CoverageFacts("UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN");
		if (!nativeCommitted || beforeJson == null) { return unknown; }
		try
		{
			if (beforeJson.length() > MAX_JSON) { return unknown; }
			final var parser = new Json(beforeJson);
			parser.validate();
			final var facts = parser._facts;
			if (facts.containsKey("provenance") && !"NATIVE".equals(facts.get("provenance"))) { return unknown; }
			final Object grade = facts.get("grade");
			return new CoverageFacts(integerFact(facts.get("npcId"), 1, 1000000), integerFact(facts.get("learnerClass"), 0, 200),
				integerFact(facts.get("level"), 1, 85), grade instanceof String text && Set.of("NONE", "D", "C", "B", "A", "S").contains(text) ? text : "UNKNOWN",
				integerFact(facts.get("catalogGeneration"), 0, Long.MAX_VALUE));
		}
		catch (IllegalArgumentException failure) { return unknown; }
	}

	private static String integerFact(Object value, long minimum, long maximum)
	{
		if (!(value instanceof java.math.BigDecimal number)) { return "UNKNOWN"; }
		try
		{
			final long integer = number.longValueExact();
			return integer >= minimum && integer <= maximum ? Long.toString(integer) : "UNKNOWN";
		}
		catch (ArithmeticException failure) { return "UNKNOWN"; }
	}

	public Path installation() { return _installation; }
	TrainingConfig config() { return _config; }
	LearningStore learning() { return _learning; }
	String catalog() { return _learning.catalogFingerprint(); }

	private static void physical(Path path) throws IOException
	{
		if (!path.equals(path.toRealPath()) || Files.isSymbolicLink(path))
		{ throw new IllegalArgumentException("Тренировочный путь должен быть физическим и не содержать перенаправлений."); }
	}

	private Connection connect() throws SQLException
	{ return connect(false); }
	private Connection connect(boolean binaryParameters) throws SQLException
	{
		try
		{
			physical(_database);
			if (!Files.readString(_installation.resolve("owner.txt"), StandardCharsets.UTF_8).equals(_owner)
				|| !NeuralPolicy.sha256(Files.readAllBytes(_database)).equals(_databaseHash))
			{ throw new SQLException("Владелец или конфигурация тренировочной схемы изменились."); }
		}
		catch (IOException failure) { throw new SQLException("Не удалось подтвердить файлы собственной установки."); }
		final var settings = (Properties) _credentials.clone();
		// V3 Adam envelope is14.2MB. Binary parameters avoid doubling zero bytes in text SQL;
		// same pinned driver, endpoint and ownership checks. Legacy/V1 connections retain their prior settings.
		if (binaryParameters) { settings.setProperty("useServerPrepStmts", "true"); }
		final Connection connection = DriverManager.getConnection(_url, settings);
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT DATABASE(),@@port,VERSION(),CURRENT_USER(),@@datadir"))
		{
			if (!rows.next() || !SCHEMA.equals(rows.getString(1)) || rows.getInt(2) != _port || !rows.getString(3).contains("MariaDB")
				|| !rows.getString(4).startsWith("learning001@")
				|| !Path.of(rows.getString(5)).toRealPath().equals(_installation.resolve("sql/data").toRealPath()))
			{ throw new SQLException("Идентичность собственной MariaDB не подтверждена."); }
			return connection;
		}
		catch (SQLException | IOException | RuntimeException failure)
		{
			connection.close();
			throw new SQLException("Идентичность собственной MariaDB не подтверждена.");
		}
	}

	/** V3 offline durability reuses the exact existing TEST installation and SQL identity guards. */
	Connection openTacticalConnection() throws SQLException { return connect(true); }

	@FunctionalInterface
	private interface Transaction<T> { T run(Connection connection) throws SQLException; }

	private <T> T transaction(Transaction<T> operation) throws SQLException
	{
		try (Connection connection = connect())
		{
			connection.setAutoCommit(false);
			try
			{
				final T result = operation.run(connection);
				connection.commit();
				return result;
			}
			catch (SQLException | RuntimeException failure)
			{
				connection.rollback();
				throw failure;
			}
		}
	}

	/** The full stock installer installs phantom_training.sql before this identity claim. */
	public void initialize() throws SQLException
	{
		transaction(connection ->
		{
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*),MIN(version),MAX(version) FROM phantom_training_schema"))
			{
				if (!rows.next() || rows.getInt(1) != 1 || rows.getInt(2) != 1 || rows.getInt(3) != 1)
				{ throw new IllegalStateException("Версия SQL-контракта тренажёра не поддерживается."); }
			}
			try (var sql = connection.prepareStatement("INSERT INTO phantom_training_control (singleton,installation_hash) VALUES (1,?) ON DUPLICATE KEY UPDATE singleton=singleton"))
			{ sql.setString(1, _identity); sql.executeUpdate(); }
			control(connection, true);
			return null;
		});
	}

	private Control control(Connection connection, boolean lock) throws SQLException
	{
		try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT installation_hash,next_ordinal,run_id,epoch,checkpoint_epoch,active_model,active_sha,evaluating FROM phantom_training_control WHERE singleton=1" + (lock ? " FOR UPDATE" : "")))
		{
			if (!rows.next() || !_identity.equals(rows.getString(1))) { throw new IllegalStateException("SQL-журнал принадлежит другой установке."); }
			return new Control(rows.getLong(2), rows.getString(3), rows.getString(4), rows.getLong(5), rows.getLong(6), rows.getString(7), rows.getString(8));
		}
	}

	public void recover(String runId, String epoch) throws SQLException
	{
		identity(runId, 32); identity(epoch, 64);
		transaction(connection ->
		{
			control(connection, true);
			try (var statement = connection.createStatement())
			{
				statement.executeUpdate("UPDATE phantom_training_episodes SET state='INCOMPLETE',reason='Прервано до подтверждения эпизода',finished_at=CURRENT_TIMESTAMP(6) WHERE state IN ('ACTIVE','SETTLING')");
				statement.executeUpdate("UPDATE phantom_training_attempts SET state='FAILED',reason='Прервано до фиксации кандидата',finished_at=CURRENT_TIMESTAMP(6) WHERE state='PREPARING'");
				statement.executeUpdate("UPDATE phantom_training_attempts SET reason='Кандидат сохранён до прерывания; автоматическое применение не подтверждено',finished_at=CURRENT_TIMESTAMP(6) WHERE state='DRAFT' AND finished_at IS NULL");
			}
			try (var sql = connection.prepareStatement("UPDATE phantom_training_control SET run_id=?,epoch=?,evaluating='' WHERE singleton=1"))
			{ sql.setString(1, runId); sql.setString(2, epoch); sql.executeUpdate(); }
			return null;
		});
	}

	public long nextOrdinal() throws SQLException
	{
		try (Connection connection = connect()) { return control(connection, false).ordinal(); }
	}

	public String begin(String runId, String epoch, long ordinal, String scenario, long seed, String scope, String split, String beforeJson) throws SQLException
	{
		identity(runId, 32); identity(epoch, 64);
		text(scenario, 160); json(beforeJson);
		if (ordinal < 0 || !Set.of("SOLO", "PARTY").contains(scope) || !Set.of("TRAIN", "TUNING", "AUDIT").contains(split))
		{ throw new IllegalArgumentException("Неверный номер, режим или раздел эпизода."); }
		return transaction(connection ->
		{
			final Control current = control(connection, true);
			if (current.ordinal() != ordinal) { throw new IllegalStateException("Номер эпизода уже использован или устарел."); }
			if (!current.run().equals(runId) || !current.epoch().equals(epoch))
			{ throw new IllegalStateException("Поколение эпизода не принадлежит текущему запуску."); }
			if (scalar(connection, "SELECT COUNT(*) FROM phantom_training_episodes") >= _config.integer("maxLedgerEpisodes", 1000000, 100, 10000000))
			{ throw new IllegalStateException("Достигнут предел журнала. Сохраните экспорт и подготовьте новую установку."); }
			final String id = id();
			try (var sql = connection.prepareStatement("INSERT INTO phantom_training_episodes (episode_id,run_id,epoch,ordinal,scenario,scenario_seed,scope,split,state,before_json) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',?)"))
			{
				sql.setString(1, id); sql.setString(2, runId); sql.setString(3, epoch); sql.setLong(4, ordinal);
				sql.setString(5, scenario); sql.setLong(6, seed); sql.setString(7, scope); sql.setString(8, split); sql.setString(9, beforeJson); sql.executeUpdate();
			}
			try (var sql = connection.prepareStatement("UPDATE phantom_training_control SET next_ordinal=? WHERE singleton=1"))
			{ sql.setLong(1, Math.addExact(ordinal, 1)); sql.executeUpdate(); }
			return id;
		});
	}

	public boolean finish(String episodeId, String afterJson, List<TrainingCollector.Observation> actions, String outcome, long elapsedMillis) throws SQLException
	{
		identity(episodeId, 32); json(afterJson); text(outcome, 160);
		if (elapsedMillis < 0 || actions.size() > 256) { throw new IllegalArgumentException("Превышены границы наблюдения эпизода."); }
		final var exact = new LinkedHashMap<String, String>();
		final var actionJson = new StringBuilder("[");
		for (var action : actions)
		{
			json(action.json());
			if (actionJson.length() > 1) { actionJson.append(','); }
			actionJson.append(action.json());
			if (action.eventKey() == null || action.eventKey().isBlank()) { continue; }
			if (!action.eventKey().matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Неверный ключ точного события."); }
			final String previous = exact.putIfAbsent(action.eventKey(), action.json());
			if (previous != null && !previous.equals(action.json())) { throw new IllegalArgumentException("Несовместимые повторные наблюдения одного события."); }
		}
		actionJson.append(']');
		if (actionJson.length() > 4194304) { throw new IllegalArgumentException("Наблюдения эпизода превышают допустимый размер."); }
		final String finishHash = hash(afterJson + "\n" + actionJson + "\n" + outcome + "\n" + elapsedMillis);
		awaitEvidence(exact.keySet());
		return transaction(connection ->
		{
			final Control current = control(connection, true);
			final String scope;
			final String split;
			try (var sql = connection.prepareStatement("SELECT run_id,epoch,scope,split,state,finish_hash FROM phantom_training_episodes WHERE episode_id=? FOR UPDATE"))
			{
				sql.setString(1, episodeId);
				try (var rows = sql.executeQuery())
				{
					if (!rows.next()) { throw new IllegalStateException("Эпизод не найден."); }
					if ("COMMITTED".equals(rows.getString(5)))
					{
						if (!finishHash.equals(rows.getString(6))) { throw new IllegalStateException("Завершённый эпизод неизменяем."); }
						return true;
					}
					if ("INCOMPLETE".equals(rows.getString(5))) { return false; }
					if (!current.run().equals(rows.getString(1)) || !current.epoch().equals(rows.getString(2)))
					{ throw new IllegalStateException("Наблюдение относится к устаревшему поколению."); }
					scope = rows.getString(3); split = rows.getString(4);
				}
			}
			final List<Sample> verified = new ArrayList<>();
			String rejection = exact.isEmpty() ? "Нет точного подтверждённого native-события" : "";
			for (var entry : exact.entrySet())
			{
				final Sample row = verified(connection, episodeId, entry.getKey(), scope, split);
				if (row == null) { rejection = "Событие отсутствует, не является точным native или уже принадлежит эпизоду"; break; }
				verified.add(row);
			}
			if (rejection.isEmpty() && scalar(connection, "SELECT COUNT(*) FROM phantom_training_events") + verified.size() > _config.integer("maxLedgerEvents", 262144, 4096, 1000000))
			{ rejection = "Достигнут предел хранения точных событий"; }
			if (rejection.isEmpty())
			{
				for (Sample row : verified) { insertEvent(connection, row, exact.get(row.key())); }
			}
			try (var sql = connection.prepareStatement("UPDATE phantom_training_episodes SET state=?,after_json=?,actions_json=?,outcome=?,reason=?,finish_hash=?,elapsed_millis=?,finished_at=CURRENT_TIMESTAMP(6) WHERE episode_id=?"))
			{
				sql.setString(1, rejection.isEmpty() ? "COMMITTED" : "INCOMPLETE"); sql.setString(2, afterJson); sql.setString(3, actionJson.toString());
				sql.setString(4, outcome); sql.setString(5, rejection); sql.setString(6, finishHash); sql.setLong(7, elapsedMillis); sql.setString(8, episodeId); sql.executeUpdate();
			}
			pruneRich(connection, current.ordinal());
			return rejection.isEmpty();
		});
	}

	private Sample verified(Connection connection, String episode, String key, String scope, String split) throws SQLException
	{
		try (var sql = connection.prepareStatement("SELECT actor_group,policy_version,skill_id,skill_level,damage,reward,features FROM phantom_ai_training_examples WHERE event_key=? AND scope=? AND origin='NATIVE' AND evidence_kind='EXACT_CAST_HP' AND damage>0 AND schema_fingerprint=? AND skill_catalog=? AND NOT EXISTS (SELECT 1 FROM phantom_training_events WHERE event_key=?) AND created_at>=(SELECT created_at FROM phantom_training_episodes WHERE episode_id=?)"))
		{
			sql.setString(1, key); sql.setString(2, scope); sql.setString(3, LearningFeatures.SCHEMA); sql.setString(4, catalog()); sql.setString(5, key);
			sql.setString(6, episode);
			try (var rows = sql.executeQuery())
			{
				if (!rows.next()) { return null; }
				final String actor = rows.getString(1);
				final byte[] features = rows.getBytes(7);
				final double damage = rows.getDouble(5);
				final float reward = rows.getFloat(6);
				if (!actor.matches("[0-9a-f]{64}") || !Double.isFinite(damage) || damage <= 0 || !Float.isFinite(reward) || Math.abs(reward) > 1) { return null; }
				final int actorSplit = Math.floorMod(actor.hashCode(), 5);
				if (!split.equals(actorSplit == 0 ? "AUDIT" : actorSplit == 1 ? "TUNING" : "TRAIN")) { return null; }
				try { LearningFeatures.decode(features); }
				catch (IllegalArgumentException failure) { return null; }
				try (var actorSql = connection.prepareStatement("SELECT split FROM phantom_training_actors WHERE actor_group=?"))
				{
					actorSql.setString(1, actor);
					try (var assigned = actorSql.executeQuery()) { if (assigned.next() && !split.equals(assigned.getString(1))) { return null; } }
				}
				return new Sample(key, episode, actor, scope, split, "", 0, rows.getLong(2), rows.getInt(3), rows.getInt(4), damage, reward, features, LearningStore.contextKey(features));
			}
		}
	}

	private void awaitEvidence(Set<String> keys) throws SQLException
	{
		if (keys.isEmpty()) { return; }
		final String placeholders = String.join(",", java.util.Collections.nCopies(keys.size(), "?"));
		final long deadline = System.nanoTime() + _config.integer("evidenceFlushMillis", 5000, 100, 30000) * 1000000L;
		do
		{
			try (Connection connection = connect(); var sql = connection.prepareStatement("SELECT COUNT(*) FROM phantom_ai_training_examples WHERE event_key IN (" + placeholders + ")"))
			{
				int index = 1;
				for (String key : keys) { sql.setString(index++, key); }
				try (var rows = sql.executeQuery()) { if (rows.next() && rows.getInt(1) == keys.size()) { return; } }
			}
			try { Thread.sleep(100); }
			catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new SQLException("Ожидание записи native-событий прервано."); }
		} while (System.nanoTime() < deadline);
	}

	private void insertEvent(Connection connection, Sample row, String observation) throws SQLException
	{
		try (var actor = connection.prepareStatement("INSERT INTO phantom_training_actors (actor_group,split) VALUES (?,?) ON DUPLICATE KEY UPDATE actor_group=actor_group"))
		{ actor.setString(1, row.actor()); actor.setString(2, row.split()); actor.executeUpdate(); }
		try (var sql = connection.prepareStatement("INSERT INTO phantom_training_events (event_key,episode_id,actor_group,scope,origin,evidence_kind,policy_version,schema_fingerprint,skill_catalog,skill_id,skill_level,damage,reward,features,observation_json) VALUES (?,?,?,?,'NATIVE','EXACT_CAST_HP',?,?,?,?,?,?,?,?,?)"))
		{
			sql.setString(1, row.key()); sql.setString(2, row.episode()); sql.setString(3, row.actor()); sql.setString(4, row.scope()); sql.setLong(5, row.policyVersion());
			sql.setString(6, LearningFeatures.SCHEMA); sql.setString(7, catalog()); sql.setInt(8, row.skill()); sql.setInt(9, row.level());
			sql.setDouble(10, row.damage()); sql.setFloat(11, row.reward()); sql.setBytes(12, row.features()); sql.setString(13, observation); sql.executeUpdate();
		}
	}

	public void abort(String episodeId, String reason) throws SQLException
	{
		abort(episodeId, reason, "{}", 0);
	}

	public void abort(String episodeId, String reason, String afterJson, long elapsedMillis) throws SQLException
	{
		identity(episodeId, 32); text(reason, 256); json(afterJson);
		if (elapsedMillis < 0) { throw new IllegalArgumentException("Время неполного эпизода не может быть отрицательным."); }
		final String outcome = reason.matches("[A-Za-z0-9_.:-]{1,64}") ? reason : null;
		transaction(connection ->
		{
			final Control current = control(connection, true);
			try (var sql = connection.prepareStatement("UPDATE phantom_training_episodes SET state='INCOMPLETE',reason=?,after_json=?,elapsed_millis=?,outcome=COALESCE(?,outcome),finished_at=CURRENT_TIMESTAMP(6) WHERE episode_id=? AND state IN ('ACTIVE','SETTLING') AND run_id=? AND epoch=?"))
			{
				sql.setString(1, reason); sql.setString(2, afterJson); sql.setLong(3, elapsedMillis); sql.setString(4, outcome);
				sql.setString(5, episodeId); sql.setString(6, current.run()); sql.setString(7, current.epoch()); sql.executeUpdate();
			}
			return null;
		});
	}

	private void pruneRich(Connection connection, long nextOrdinal) throws SQLException
	{
		final long cutoff = nextOrdinal - _config.retentionEpisodes();
		if (cutoff <= 0) { return; }
		try (var sql = connection.prepareStatement("UPDATE phantom_training_events e JOIN phantom_training_episodes p ON p.episode_id=e.episode_id SET e.observation_json=NULL WHERE p.ordinal<? AND p.state IN ('COMMITTED','INCOMPLETE')"))
		{ sql.setLong(1, cutoff); sql.executeUpdate(); }
		try (var sql = connection.prepareStatement("UPDATE phantom_training_episodes SET before_json=NULL,after_json=NULL,actions_json=NULL,rich_pruned=TRUE WHERE ordinal<? AND state IN ('COMMITTED','INCOMPLETE') AND rich_pruned=FALSE"))
		{ sql.setLong(1, cutoff); sql.executeUpdate(); }
	}

	public Counts counts() throws SQLException
	{
		try (Connection connection = connect())
		{
			final Control current = control(connection, false);
			return new Counts(scalar(connection, "SELECT COUNT(*) FROM phantom_training_episodes WHERE state='COMMITTED'"),
				scalar(connection, "SELECT COUNT(*) FROM phantom_training_episodes WHERE state='INCOMPLETE'"), scalar(connection, "SELECT COUNT(*) FROM phantom_training_events"),
				scalar(connection, "SELECT COUNT(*) FROM phantom_training_attempts"), scalar(connection, "SELECT COUNT(*) FROM phantom_training_attempts WHERE state='ACCEPTED'"), current.ordinal());
		}
	}

	public record ScopeCoverage(long committed, long incomplete, long active, long exactEvents, long distinctActors,
		long distinctSkills, long distinctScenarios, long distinctSeeds, double exactHp, long elapsedMillis,
		double episodesPerHour, double eventsPerHour, Map<String, Long> outcomes, Map<String, Long> rejectionReasons,
		Map<String, Long> npcTemplates, Map<String, Long> learnerClasses, Map<String, Long> levels,
		Map<String, Long> equipmentGrades, Map<String, Long> catalogGenerations)
	{
		String json()
		{
			return "{\"committed\":" + committed + ",\"incomplete\":" + incomplete + ",\"active\":" + active + ",\"exactEvents\":" + exactEvents
				+ ",\"distinctActors\":" + distinctActors + ",\"distinctSkills\":" + distinctSkills + ",\"distinctActualScenarios\":" + distinctScenarios
				+ ",\"distinctRecordedSeeds\":" + distinctSeeds + ",\"verifiedExactHp\":" + exactHp + ",\"episodeElapsedMillis\":" + elapsedMillis
				+ ",\"committedEpisodesPerHour\":" + episodesPerHour + ",\"exactEventsPerHour\":" + eventsPerHour + ",\"outcomes\":" + countsJson(outcomes)
				+ ",\"rejectionReasons\":" + countsJson(rejectionReasons) + ",\"committedEpisodesByNpcTemplate\":" + countsJson(npcTemplates)
				+ ",\"committedEpisodesByLearnerClass\":" + countsJson(learnerClasses) + ",\"committedEpisodesByLearnerLevel\":" + countsJson(levels)
				+ ",\"committedEpisodesByEquipmentGrade\":" + countsJson(equipmentGrades) + ",\"committedEpisodesByCatalogGeneration\":" + countsJson(catalogGenerations) + "}";
		}
	}

	public record Coverage(String windowStartUtc, String windowEndUtc, long windowMillis, long firstOrdinal, long nextOrdinal,
		int boundedToEpisodes, boolean olderEpisodesExcluded, Map<String, ScopeCoverage> scopes)
	{
		public String json()
		{
			return "{\"windowStartUtc\":" + quote(windowStartUtc) + ",\"windowEndUtc\":" + quote(windowEndUtc) + ",\"windowMillis\":" + windowMillis
				+ ",\"firstOrdinal\":" + firstOrdinal + ",\"nextOrdinal\":" + nextOrdinal + ",\"boundedToEpisodes\":" + boundedToEpisodes
				+ ",\"olderEpisodesExcluded\":" + olderEpisodesExcluded + ",\"throughputIncludesIdleTime\":true,\"scopes\":{\"SOLO\":"
				+ scopes.get("SOLO").json() + ",\"PARTY\":" + scopes.get("PARTY").json() + "}}";
		}
	}

	private static final class CoverageScope
	{
		long committed, incomplete, active, events, actors, skills, elapsed;
		double hp;
		final Set<String> scenarios = new HashSet<>();
		final Set<Long> seeds = new HashSet<>();
		final Map<String, Long> outcomes = new java.util.TreeMap<>(), rejections = new java.util.TreeMap<>(), npcs = new java.util.TreeMap<>(), classes = new java.util.TreeMap<>(),
			levels = new java.util.TreeMap<>(), grades = new java.util.TreeMap<>(), generations = new java.util.TreeMap<>();

		ScopeCoverage result(long windowMillis)
		{
			final double hours = windowMillis / 3600000.0;
			return new ScopeCoverage(committed, incomplete, active, events, actors, skills, scenarios.size(), seeds.size(), hp, elapsed,
				hours > 0 ? committed / hours : 0, hours > 0 ? events / hours : 0, Map.copyOf(outcomes), Map.copyOf(rejections), Map.copyOf(npcs),
				Map.copyOf(classes), Map.copyOf(levels), Map.copyOf(grades), Map.copyOf(generations));
		}
	}

	/** Bounded settled-history inspection; this route is never called from Combat. */
	public Coverage coverage() throws SQLException
	{
		final int maximum = _config.integer("coverageWindowEpisodes", 512, 1, 4096);
		final var scopes = new LinkedHashMap<String, CoverageScope>();
		scopes.put("SOLO", new CoverageScope()); scopes.put("PARTY", new CoverageScope());
		java.time.Instant start = null;
		try (Connection connection = connect())
		{
			connection.setReadOnly(true);
			connection.setAutoCommit(false);
			final long next = control(connection, false).ordinal();
			final long first = Math.max(0, next - maximum);
			long cursor = first - 1;
			while (true)
			{
				boolean any = false;
				try (var sql = connection.prepareStatement("SELECT ordinal,scope,state,scenario,scenario_seed,before_json,outcome,reason,elapsed_millis,UNIX_TIMESTAMP(created_at)*1000 FROM phantom_training_episodes WHERE ordinal>? AND ordinal<? ORDER BY ordinal LIMIT 8"))
				{
					sql.setLong(1, cursor); sql.setLong(2, next);
					try (var rows = sql.executeQuery())
					{
						while (rows.next())
						{
							any = true; cursor = rows.getLong(1);
							final var scope = scopes.get(rows.getString(2));
							final String state = rows.getString(3);
							final boolean committed = "COMMITTED".equals(state);
							if (committed) { scope.committed++; scope.scenarios.add(rows.getString(4)); scope.seeds.add(rows.getLong(5)); }
							else if ("INCOMPLETE".equals(state)) { scope.incomplete++; }
							else { scope.active++; }
							if (committed || "INCOMPLETE".equals(state))
							{
								scope.elapsed += rows.getLong(9);
								increment(scope.outcomes, state + ":" + category(rows.getString(7)));
							}
							if ("INCOMPLETE".equals(state)) { increment(scope.rejections, category(rows.getString(8))); }
							if (committed)
							{
								final var facts = coverageFacts(rows.getString(6), true);
								increment(scope.npcs, facts.npcId()); increment(scope.classes, facts.learnerClass()); increment(scope.levels, facts.level());
								increment(scope.grades, facts.grade()); increment(scope.generations, facts.catalogGeneration());
							}
							final var created = java.time.Instant.ofEpochMilli(rows.getLong(10));
							if (start == null || created.isBefore(start)) { start = created; }
						}
					}
				}
				if (!any) { break; }
			}
			try (var sql = connection.prepareStatement("SELECT p.scope,COUNT(*),COUNT(DISTINCT e.actor_group),COUNT(DISTINCT e.skill_id),SUM(e.damage) FROM phantom_training_events e JOIN phantom_training_episodes p ON p.episode_id=e.episode_id WHERE p.ordinal>=? AND p.ordinal<? AND p.state='COMMITTED' AND e.origin='NATIVE' AND e.evidence_kind='EXACT_CAST_HP' AND e.damage>0 GROUP BY p.scope"))
			{
				sql.setLong(1, first); sql.setLong(2, next);
				try (var rows = sql.executeQuery())
				{
					while (rows.next())
					{
						final var scope = scopes.get(rows.getString(1));
						scope.events = rows.getLong(2); scope.actors = rows.getLong(3); scope.skills = rows.getLong(4); scope.hp = rows.getDouble(5);
					}
				}
			}
			connection.rollback();
			final java.time.Instant end = java.time.Instant.now();
			final long duration = start == null ? 0 : Math.max(0, java.time.Duration.between(start, end).toMillis());
			return new Coverage(start == null ? null : start.toString(), end.toString(), duration, first, next, maximum, first > 0,
				Map.of("SOLO", scopes.get("SOLO").result(duration), "PARTY", scopes.get("PARTY").result(duration)));
		}
	}

	private static void increment(Map<String, Long> counts, String key) { counts.merge(key, 1L, Long::sum); }
	private static String category(String value) { return value == null || value.isBlank() ? "UNKNOWN" : value; }

	private static String countsJson(Map<String, Long> counts)
	{
		final var json = new StringBuilder("{");
		for (var entry : new java.util.TreeMap<>(counts).entrySet())
		{
			if (json.length() > 1) { json.append(','); }
			json.append(quote(entry.getKey())).append(':').append(entry.getValue());
		}
		return json.append('}').toString();
	}

	Control active() throws SQLException
	{
		try (Connection connection = connect()) { return control(connection, false); }
	}

	public void requireStopped() throws SQLException
	{
		try (Connection connection = connect())
		{
			control(connection, false);
			if (scalar(connection, "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() AND DB=DATABASE()") != 0)
			{ throw new IllegalStateException("Обучение разрешено только после штатной остановки GameServer и его соединений SQL."); }
		}
	}

	record Preparation(Snapshot snapshot, String reason) { }

	Preparation freeze() throws SQLException
	{
		return transaction(connection ->
		{
			final Control current = control(connection, true);
			if (!current.evaluating().isEmpty()) { return new Preparation(null, "Предыдущая попытка ещё выполняется; сначала подтвердите её завершение или восстановление владельца."); }
			if (scalar(connection, "SELECT COUNT(*) FROM phantom_training_attempts") >= _config.integer("maxTrainingAttempts", 256, 1, 4096))
			{ return new Preparation(null, "Достигнут предел истории обучения; сохраните экспорт перед новой кампанией."); }
			final List<Sample> available = new ArrayList<>();
			try (var sql = connection.prepareStatement("SELECT e.event_key,e.episode_id,e.actor_group,e.scope,p.split,p.scenario,p.scenario_seed,e.policy_version,e.skill_id,e.skill_level,e.damage,e.reward,e.features FROM phantom_training_events e JOIN phantom_training_episodes p ON p.episode_id=e.episode_id WHERE p.state='COMMITTED' AND e.schema_fingerprint=? AND e.skill_catalog=? AND (p.split<>'AUDIT' OR (NOT EXISTS (SELECT 1 FROM phantom_training_dataset d WHERE d.actor_group=e.actor_group AND d.scenario=p.scenario AND d.split='AUDIT') AND NOT EXISTS (SELECT 1 FROM phantom_training_dataset d WHERE d.scenario=p.scenario AND d.split IN ('TRAIN','TUNING')))) AND (p.split='AUDIT' OR NOT EXISTS (SELECT 1 FROM phantom_training_dataset d WHERE d.scenario=p.scenario AND d.split='AUDIT')) ORDER BY p.ordinal DESC,e.event_key LIMIT ?"))
			{
				sql.setString(1, LearningFeatures.SCHEMA); sql.setString(2, catalog()); sql.setInt(3, _config.integer("maxExamples", NeuralPolicy.MAX_EXAMPLES, 32, NeuralPolicy.MAX_EXAMPLES));
				try (var rows = sql.executeQuery())
				{
					while (rows.next())
					{
						final byte[] features = rows.getBytes(13);
						available.add(new Sample(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5), rows.getString(6),
							rows.getLong(7), rows.getLong(8), rows.getInt(9), rows.getInt(10), rows.getDouble(11), rows.getFloat(12), features, LearningStore.contextKey(features)));
					}
				}
			}
			final var fitted = new HashSet<String>();
			final var fittedScenarios = new HashSet<String>();
			for (Sample row : available)
			{
				if (!"AUDIT".equals(row.split())) { fitted.add(row.context()); fittedScenarios.add(row.scenario()); }
			}
			final var samples = available.stream().filter(row -> !"AUDIT".equals(row.split())
				|| (!fitted.contains(row.context()) && !fittedScenarios.contains(row.scenario()))).toList();
			final String insufficient = readiness(samples);
			if (!insufficient.isEmpty()) { return new Preparation(null, insufficient); }
			final String id = id();
			final String datasetHash = datasetHash(samples);
			try (var sql = connection.prepareStatement("INSERT INTO phantom_training_attempts (attempt_id,state,seed,config_hash,dataset_hash,schema_fingerprint,skill_catalog,expected_epoch,prior_model,prior_sha) VALUES (?,'PREPARING',?,?,?,?,?,?,?,?)"))
			{
				sql.setString(1, id); sql.setLong(2, _config.seed()); sql.setString(3, _config.sha256()); sql.setString(4, datasetHash); sql.setString(5, LearningFeatures.SCHEMA);
				sql.setString(6, catalog()); sql.setLong(7, current.checkpointEpoch()); sql.setLong(8, current.model()); sql.setString(9, current.sha()); sql.executeUpdate();
			}
			try (var sql = connection.prepareStatement("INSERT INTO phantom_training_dataset (attempt_id,event_key,episode_id,actor_group,scope,split,scenario,scenario_seed,policy_version,skill_id,skill_level,damage,reward,features,context_key) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"))
			{
				for (Sample row : samples)
				{
					sql.setString(1, id); sql.setString(2, row.key()); sql.setString(3, row.episode()); sql.setString(4, row.actor()); sql.setString(5, row.scope()); sql.setString(6, row.split());
					sql.setString(7, row.scenario()); sql.setLong(8, row.seed()); sql.setLong(9, row.policyVersion()); sql.setInt(10, row.skill()); sql.setInt(11, row.level());
					sql.setDouble(12, row.damage()); sql.setFloat(13, row.reward()); sql.setBytes(14, row.features()); sql.setString(15, row.context()); sql.addBatch();
				}
				sql.executeBatch();
			}
			final var cohorts = new HashSet<String>();
			try (var sql = connection.prepareStatement("INSERT INTO phantom_training_audit_claims (cohort_key,attempt_id) VALUES (?,?)"))
			{
				for (Sample row : samples)
				{
					if (!"AUDIT".equals(row.split()) || !cohorts.add(row.cohort())) { continue; }
					sql.setString(1, row.cohort()); sql.setString(2, id); sql.addBatch();
				}
				sql.executeBatch();
			}
			try (var sql = connection.prepareStatement("UPDATE phantom_training_control SET evaluating=? WHERE singleton=1"))
			{ sql.setString(1, id); sql.executeUpdate(); }
			return new Preparation(new Snapshot(id, _config.seed(), datasetHash, current.checkpointEpoch(), current.model(), current.sha(), samples), "");
		});
	}

	private String readiness(List<Sample> samples)
	{
		if (samples.size() < _config.trainingThreshold()) { return "Продолжается сбор независимых подтверждённых событий: " + samples.size() + "/" + _config.trainingThreshold() + "."; }
		for (String split : List.of("TRAIN", "TUNING", "AUDIT"))
		{
			final var rows = samples.stream().filter(row -> split.equals(row.split())).toList();
			if (rows.size() < ("TRAIN".equals(split) ? 16 : 8)) { return "Недостаточно независимых событий в разделе " + split + "."; }
			if ("AUDIT".equals(split) && rows.stream().map(Sample::actor).distinct().count() < 2) { return "Нужны разные независимые актёры AUDIT."; }
			for (String scope : List.of("SOLO", "PARTY"))
			{
				final var scoped = rows.stream().filter(row -> scope.equals(row.scope())).toList();
				if (scoped.isEmpty() || (!"TUNING".equals(split) && scoped.stream().map(Sample::skill).distinct().count() < 2))
				{ return "Нужно покрытие двух навыков и обоих PvE-режимов: " + split + "/" + scope + "."; }
				if ("AUDIT".equals(split) && scoped.size() < _config.integer("minAuditPerScope", 4, 1, 2048))
				{ return "Недостаточно независимых событий AUDIT в режиме " + scope + "."; }
			}
		}
		return "";
	}

	void draft(Snapshot snapshot, NeuralPolicy.Training result, String metrics) throws SQLException
	{
		json(metrics);
		transaction(connection ->
		{
			final Control current = control(connection, true);
			if (!snapshot.id().equals(current.evaluating())) { throw new IllegalStateException("Попытка обучения утратила владельца."); }
			try (var sql = connection.prepareStatement("UPDATE phantom_training_attempts SET state='DRAFT',algo_version=?,step=?,weights_blob=?,sha256=?,metrics_json=? WHERE attempt_id=? AND state='PREPARING'"))
			{
				sql.setInt(1, result.model().algorithm()); sql.setInt(2, result.step()); sql.setBytes(3, result.model().bytes()); sql.setString(4, result.model().sha256()); sql.setString(5, metrics); sql.setString(6, snapshot.id());
				if (sql.executeUpdate() != 1) { throw new IllegalStateException("Кандидат уже зафиксирован или отменён."); }
			}
			return null;
		});
	}

	boolean accept(Snapshot snapshot, LearningStore.Checkpoint checkpoint) throws SQLException
	{
		return transaction(connection ->
		{
			final Control current = control(connection, true);
			if (!snapshot.id().equals(current.evaluating()) || snapshot.expectedEpoch() != current.checkpointEpoch()
				|| snapshot.priorModel() != current.model() || !snapshot.priorSha().equals(current.sha())) { return false; }
			try (var sql = connection.prepareStatement("SELECT sha256 FROM phantom_training_attempts WHERE attempt_id=? AND state='DRAFT' FOR UPDATE"))
			{
				sql.setString(1, snapshot.id());
				try (var row = sql.executeQuery()) { if (!row.next() || !checkpoint.model().sha256().equals(row.getString(1))) { return false; } }
			}
			if (!_learning.promote(connection, checkpoint.id())) { return false; }
			try (var sql = connection.prepareStatement("UPDATE phantom_training_control SET checkpoint_epoch=checkpoint_epoch+1,active_model=?,active_sha=?,evaluating='' WHERE singleton=1 AND checkpoint_epoch=? AND active_model=? AND active_sha=?"))
			{
				sql.setLong(1, checkpoint.id()); sql.setString(2, checkpoint.model().sha256()); sql.setLong(3, snapshot.expectedEpoch()); sql.setLong(4, snapshot.priorModel()); sql.setString(5, snapshot.priorSha());
				if (sql.executeUpdate() != 1) { throw new IllegalStateException("Версия активного checkpoint изменилась."); }
			}
			try (var sql = connection.prepareStatement("UPDATE phantom_training_attempts SET state='ACCEPTED',model_id=?,reason='Принят только для собственной TEST',finished_at=CURRENT_TIMESTAMP(6) WHERE attempt_id=?"))
			{ sql.setLong(1, checkpoint.id()); sql.setString(2, snapshot.id()); sql.executeUpdate(); }
			return true;
		});
	}

	void decline(Snapshot snapshot, String reason, boolean failed) throws SQLException
	{
		text(reason, 256);
		transaction(connection ->
		{
			control(connection, true);
			try (var sql = connection.prepareStatement("UPDATE phantom_training_attempts SET state=?,reason=?,finished_at=CURRENT_TIMESTAMP(6) WHERE attempt_id=? AND state IN ('PREPARING','DRAFT')"))
			{ sql.setString(1, failed ? "FAILED" : "DRAFT"); sql.setString(2, reason); sql.setString(3, snapshot.id()); sql.executeUpdate(); }
			try (var sql = connection.prepareStatement("UPDATE phantom_training_control SET evaluating='' WHERE singleton=1 AND evaluating=?"))
			{ sql.setString(1, snapshot.id()); sql.executeUpdate(); }
			return null;
		});
	}

	record ModelExport(LearningStore.Checkpoint checkpoint, String attempt, String datasetHash, String metrics) { }

	ModelExport exportModel() throws SQLException
	{
		final Control current = active();
		final var checkpoint = current.model() == 0 ? null : _learning.accepted(current.model());
		if (checkpoint == null || !current.sha().equals(checkpoint.model().sha256()))
		{ throw new IllegalStateException("Экспорт недоступен: подтверждённой принятой TEST-модели пока нет."); }
		try (Connection connection = connect(); var sql = connection.prepareStatement("SELECT attempt_id,dataset_hash,metrics_json FROM phantom_training_attempts WHERE model_id=? AND state='ACCEPTED' AND sha256=?"))
		{
			sql.setLong(1, current.model()); sql.setString(2, current.sha());
			try (var row = sql.executeQuery())
			{
				if (!row.next()) { throw new IllegalStateException("Отсутствует независимая оценка принятого checkpoint."); }
				return new ModelExport(checkpoint, row.getString(1), row.getString(2), row.getString(3));
			}
		}
	}

	void writeDataset(java.io.Writer output, String attempt) throws SQLException, IOException
	{
		identity(attempt, 32);
		try (Connection connection = connect(); var sql = connection.prepareStatement("SELECT event_key,episode_id,actor_group,scope,split,scenario,scenario_seed,policy_version,skill_id,skill_level,damage,reward,features,context_key FROM phantom_training_dataset WHERE attempt_id=? ORDER BY event_key"))
		{
			sql.setString(1, attempt);
			try (var rows = sql.executeQuery())
			{
				while (rows.next())
				{
					output.write("{\"schemaVersion\":1,\"origin\":\"NATIVE\",\"eventKey\":" + quote(rows.getString(1)) + ",\"episodeId\":" + quote(rows.getString(2))
						+ ",\"actor\":" + quote(rows.getString(3)) + ",\"scope\":" + quote(rows.getString(4)) + ",\"split\":" + quote(rows.getString(5))
						+ ",\"scenario\":" + quote(rows.getString(6)) + ",\"seed\":" + rows.getLong(7) + ",\"policyVersion\":" + rows.getLong(8)
						+ ",\"skill\":" + rows.getInt(9) + ",\"skillLevel\":" + rows.getInt(10) + ",\"damage\":" + rows.getDouble(11) + ",\"reward\":" + rows.getFloat(12)
						+ ",\"featuresF32beBase64\":" + quote(java.util.Base64.getEncoder().encodeToString(rows.getBytes(13))) + ",\"context\":" + quote(rows.getString(14)) + "}\n");
				}
			}
		}
	}

	/** Fixed-size pages keep the export independent of the total campaign history. */
	void writeEpisodes(java.io.Writer output) throws SQLException, IOException
	{
		long cursor = -1;
		while (true)
		{
			boolean any = false;
			try (Connection connection = connect(); var sql = connection.prepareStatement("SELECT episode_id,ordinal,scenario,scenario_seed,scope,split,state,before_json,after_json,actions_json,outcome,reason,elapsed_millis,rich_pruned FROM phantom_training_episodes WHERE ordinal>? ORDER BY ordinal LIMIT 8"))
			{
				sql.setLong(1, cursor);
				try (var rows = sql.executeQuery())
				{
					while (rows.next())
					{
						any = true; cursor = rows.getLong(2);
						output.write("{\"schemaVersion\":1,\"episodeId\":" + quote(rows.getString(1)) + ",\"ordinal\":" + cursor + ",\"scenario\":" + quote(rows.getString(3))
							+ ",\"seed\":" + rows.getLong(4) + ",\"scope\":" + quote(rows.getString(5)) + ",\"split\":" + quote(rows.getString(6)) + ",\"state\":" + quote(rows.getString(7))
							+ ",\"before\":" + raw(rows.getString(8)) + ",\"after\":" + raw(rows.getString(9)) + ",\"observations\":" + raw(rows.getString(10))
							+ ",\"outcome\":" + quote(rows.getString(11)) + ",\"reason\":" + quote(rows.getString(12)) + ",\"elapsedMillis\":" + rows.getLong(13)
							+ ",\"richPruned\":" + rows.getBoolean(14) + "}\n");
					}
				}
			}
			if (!any) { return; }
		}
	}

	String historyMarkdown() throws SQLException
	{
		final var result = new StringBuilder("| Попытка | Статус | Модель | Причина |\n|---|---|---|---|\n");
		try (Connection connection = connect(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT attempt_id,state,model_id,reason FROM phantom_training_attempts ORDER BY created_at LIMIT 4096"))
		{
			while (rows.next())
			{ result.append('|').append(rows.getString(1)).append('|').append(rows.getString(2)).append('|').append(rows.getLong(3)).append('|').append(rows.getString(4).replace('|', '/').replace('\n', ' ')).append("|\n"); }
		}
		return result.toString();
	}

	static String quote(String value)
	{
		if (value == null) { return "null"; }
		final var result = new StringBuilder("\"");
		for (int i = 0; i < value.length(); i++)
		{
			final char c = value.charAt(i);
			switch (c)
			{
				case '"' -> result.append("\\\"");
				case '\\' -> result.append("\\\\");
				case '\n' -> result.append("\\n");
				case '\r' -> result.append("\\r");
				case '\t' -> result.append("\\t");
				default -> { if (c < 32) { result.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c)); } else { result.append(c); } }
			}
		}
		return result.append('"').toString();
	}

	private static String raw(String value) { return value == null ? "null" : value; }

	private static long scalar(Connection connection, String query) throws SQLException
	{
		try (var statement = connection.createStatement(); var rows = statement.executeQuery(query))
		{ if (!rows.next()) { throw new SQLException("Отсутствует результат SQL-журнала."); } return rows.getLong(1); }
	}

	private static String datasetHash(List<Sample> samples)
	{
		final var bytes = new java.io.ByteArrayOutputStream();
		for (Sample row : samples)
		{
			bytes.writeBytes((row.key() + ":" + row.split() + ":" + row.actor() + ":" + row.scenario() + ":" + row.seed() + "\n").getBytes(StandardCharsets.UTF_8));
			bytes.writeBytes(row.features());
			bytes.writeBytes(ByteBuffer.allocate(Float.BYTES).putFloat(row.reward()).array());
		}
		return NeuralPolicy.sha256(bytes.toByteArray());
	}

	private static void identity(String value, int maximum)
	{
		if (value == null || !value.matches(maximum == 32 ? "[0-9a-f]{32}" : "[A-Za-z0-9_-]{1,64}"))
		{ throw new IllegalArgumentException("Неверная идентичность запуска или эпизода."); }
	}

	private static void text(String value, int maximum)
	{
		if (value == null || value.isBlank() || value.length() > maximum || value.indexOf('\0') >= 0)
		{ throw new IllegalArgumentException("Недопустимый размер текстового поля тренировочного журнала."); }
	}

	public static void validateObservation(String value)
	{
		if (value == null || value.length() > MAX_JSON || value.indexOf('\0') >= 0 || !value.stripLeading().startsWith("{") || !value.stripTrailing().endsWith("}"))
		{ throw new IllegalArgumentException("Ожидается ограниченная структурная запись наблюдения."); }
		new Json(value).validate();
	}

	private static void json(String value) { validateObservation(value); }

	/** Strict data validation without an additional runtime JSON dependency. */
	private static final class Json
	{
		private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
		private final String _body;
		private int _position;
		private int _tokens;
		private final Map<String, Object> _facts = new LinkedHashMap<>();

		Json(String body) { _body = body; }

		void validate()
		{
			value(0); whitespace();
			if (_position != _body.length()) { invalid(); }
		}

		private Object value(int depth)
		{
			if (depth > 32 || ++_tokens > 16384) { invalid(); }
			whitespace();
			if (_position >= _body.length()) { invalid(); }
			final char c = _body.charAt(_position);
			if (c == '{' || c == '[')
			{
				_position++;
				whitespace();
				final char close = c == '{' ? '}' : ']';
				if (take(close)) { return null; }
				do
				{
					whitespace();
					String key = null;
					if (c == '{') { key = string(); whitespace(); if (!take(':')) { invalid(); } }
					final Object fact = value(depth + 1); whitespace();
					if (depth == 0 && key != null && Set.of("npcId", "learnerClass", "level", "grade", "catalogGeneration", "provenance").contains(key))
					{
						if (_facts.containsKey(key)) { invalid(); }
						_facts.put(key, fact);
					}
					if (take(close)) { return null; }
				} while (take(','));
				invalid();
			}
			else if (c == '"') { return string(); }
			else if (c == 't' || c == 'f' || c == 'n')
			{
				final String expected = c == 't' ? "true" : c == 'f' ? "false" : "null";
				if (!_body.startsWith(expected, _position)) { invalid(); }
				_position += expected.length();
				return c == 'n' ? null : c == 't';
			}
			else
			{
				final var number = NUMBER.matcher(_body).region(_position, _body.length());
				if (!number.lookingAt()) { invalid(); }
				final Object result = new java.math.BigDecimal(_body.substring(_position, number.end()));
				_position = number.end();
				return result;
			}
			return null;
		}

		private String string()
		{
			if (!take('"')) { invalid(); }
			final var decoded = new StringBuilder();
			while (_position < _body.length())
			{
				final char c = _body.charAt(_position++);
				if (c == '"') { return decoded.toString(); }
				if (c < 32) { invalid(); }
				if (c == '\\')
				{
					if (_position >= _body.length()) { invalid(); }
					final char escape = _body.charAt(_position++);
					if (escape == 'u')
					{
						if (_position + 4 > _body.length()) { invalid(); }
						int code = 0;
						for (int i = 0; i < 4; i++)
						{
							final int digit = Character.digit(_body.charAt(_position++), 16);
							if (digit < 0) { invalid(); }
							code = code * 16 + digit;
						}
						decoded.append((char) code);
					}
					else if ("\"\\/bfnrt".indexOf(escape) < 0) { invalid(); }
					else { decoded.append(switch (escape) { case 'b' -> '\b'; case 'f' -> '\f'; case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> escape; }); }
				}
				else { decoded.append(c); }
			}
			invalid();
			return null;
		}

		private boolean take(char expected)
		{
			if (_position >= _body.length() || _body.charAt(_position) != expected) { return false; }
			_position++; return true;
		}

		private void whitespace()
		{
			while (_position < _body.length() && " \t\r\n".indexOf(_body.charAt(_position)) >= 0) { _position++; }
		}

		private static void invalid() { throw new IllegalArgumentException("Наблюдение содержит неверный или слишком сложный JSON."); }
	}

	static String id() { return UUID.randomUUID().toString().replace("-", ""); }
	static String hash(String value) { return NeuralPolicy.sha256(value.getBytes(StandardCharsets.UTF_8)); }
}

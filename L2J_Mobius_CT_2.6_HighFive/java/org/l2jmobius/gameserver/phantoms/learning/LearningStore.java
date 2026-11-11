/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy.Example;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy.Training;

/** Isolated MariaDB storage. No database method is called from the Combat decision loop. */
public final class LearningStore
{
	private static final Pattern OWN_URL = Pattern.compile("jdbc:mysql://127\\.0\\.0\\.1:([0-9]{4,5})/(phantom_learning001_[a-z0-9]{4,24})");
	private static final int RETENTION = 32_768;
	private final String _url;
	private final String _schema;
	private final String _catalog;
	private final String _actorSalt;
	private final int _port;
	private final Properties _credentials = new Properties();

	public record Evidence(String eventKey, String actorGroup, String scope, long version, int skill, int level, double damage, float reward, float[] features) { }
	public record Checkpoint(long id, NeuralPolicy model, String trainingHash, int step, int trainCount, int holdoutCount, double initialLoss, double loss) { }

	public LearningStore(String url, String username, String password, Path gameRoot) throws IOException
	{
		this(url, username, password, gameRoot, new ConfigReader(gameRoot.resolve("config/General.ini").toString()).getBoolean("CustomSkillsLoad", false));
	}

	LearningStore(String url, String username, String password, Path gameRoot, boolean customSkills) throws IOException
	{
		final var address = OWN_URL.matcher(url == null ? "" : url);
		if (!address.matches() || !"learning001".equals(username) || password == null || password.isBlank())
		{
			throw new IllegalArgumentException("Learning requires an isolated loopback MariaDB and learning001 account");
		}
		_port = Integer.parseInt(address.group(1));
		if (_port < 33000 || _port > 65535)
		{
			throw new IllegalArgumentException("Invalid isolated learning port");
		}
		_url = url;
		_schema = address.group(2);
		_credentials.setProperty("user", username);
		_credentials.setProperty("password", password);
		_credentials.setProperty("connectTimeout", "2000");
		_credentials.setProperty("socketTimeout", "3000");
		_actorSalt = NeuralPolicy.sha256(("learning001/actor/" + _schema + "/" + password).getBytes(StandardCharsets.UTF_8));
		_catalog = skillCatalog(gameRoot.resolve("data/stats/skills"), customSkills);
	}

	private Connection connect() throws SQLException
	{
		final Connection connection = DriverManager.getConnection(_url, _credentials);
		try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT DATABASE(), @@port, VERSION(), CURRENT_USER()"))
		{
			if (result.next() && _schema.equals(result.getString(1)) && _port == result.getInt(2)
				&& result.getString(3).contains("MariaDB") && result.getString(4).startsWith("learning001@"))
			{
				return connection;
			}
		}
		catch (SQLException failure)
		{
			connection.close();
			throw failure;
		}
		connection.close();
		throw new SQLException("Learning database identity mismatch");
	}

	private static String skillCatalog(Path directory, boolean customSkills) throws IOException
	{
		final MessageDigest digest = digest();
		digest.update((byte) (customSkills ? 1 : 0));
		final List<Path> files = new ArrayList<>();
		addSkillFiles(directory, files);
		if (customSkills && Files.isDirectory(directory.resolve("custom")))
		{
			addSkillFiles(directory.resolve("custom"), files);
		}
		if (files.isEmpty()) { throw new IOException("Learning skill catalog is empty"); }
		for (Path path : files)
		{
			digest.update(directory.relativize(path).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			digest.update(NeuralPolicy.sha256(Files.readAllBytes(path)).getBytes(StandardCharsets.US_ASCII));
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private static void addSkillFiles(Path directory, List<Path> files) throws IOException
	{
		try (var paths = Files.list(directory))
		{
			files.addAll(paths.filter(path -> path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".xml") && Files.isRegularFile(path)).sorted().toList());
		}
	}

	public Checkpoint accepted(long id) throws SQLException
	{
		try (Connection connection = connect())
		{
			return read(connection, id, "ACCEPTED", "NATIVE");
		}
	}

	String catalogFingerprint()
	{
		return _catalog;
	}

	Checkpoint fixture(long id) throws SQLException
	{
		try (Connection connection = connect())
		{
			return read(connection, id, "DRAFT", "SYNTHETIC");
		}
	}

	Checkpoint read(Connection connection, long id, String status, String origin) throws SQLException
	{
		try (var sql = connection.prepareStatement("SELECT step,training_hash,weights_blob,sha256,training_count,holdout_count,initial_mse,holdout_mse,algo_version "
			+ "FROM phantom_ai_model_versions WHERE model_id=? AND status=? AND origin=? AND algo_version IN (?,?) AND schema_fingerprint=? AND skill_catalog=?"))
		{
			sql.setLong(1, id);
			sql.setString(2, status);
			sql.setString(3, origin);
			sql.setInt(4, NeuralPolicy.ALGORITHM);
			sql.setInt(5, NeuralPolicy.RESIDUAL_ALGORITHM);
			sql.setString(6, LearningFeatures.SCHEMA);
			sql.setString(7, _catalog);
			try (var rows = sql.executeQuery())
			{
				if (!rows.next()) { return null; }
				final int step = rows.getInt(1);
				final int train = rows.getInt(5);
				final int holdout = rows.getInt(6);
				final double initial = rows.getDouble(7);
				final double loss = rows.getDouble(8);
				if (!qualified(step, train, holdout, initial, loss))
				{
					return null;
				}
				final var model = NeuralPolicy.restore(rows.getBytes(3), rows.getString(4));
				return model.algorithm() == rows.getInt(9) ? new Checkpoint(id, model, rows.getString(2), step, train, holdout, initial, loss) : null;
			}
		}
	}

	private static boolean qualified(int step, int train, int holdout, double initial, double loss)
	{
		return step > 0 && train >= 16 && holdout >= 8 && train + holdout <= NeuralPolicy.MAX_EXAMPLES
			&& Double.isFinite(initial) && loss >= 0 && loss < initial * .9;
	}

	public boolean promote(long id) throws SQLException
	{
		try (Connection connection = connect())
		{
			connection.setAutoCommit(false);
			final boolean changed = promote(connection, id);
			if (changed) { connection.commit(); }
			else { connection.rollback(); }
			return changed;
		}
	}

	/** The training ledger includes promotion and its active-checkpoint CAS in one transaction. */
	boolean promote(Connection connection, long id) throws SQLException
	{
		// Lock this version before checking the bytes and status; no old accepted row is overwritten.
		try (var lock = connection.prepareStatement("SELECT model_id FROM phantom_ai_model_versions WHERE model_id=? FOR UPDATE"))
		{
			lock.setLong(1, id);
			try (var rows = lock.executeQuery()) { if (!rows.next()) { return false; } }
		}
		if (read(connection, id, "DRAFT", "NATIVE") == null) { return false; }
		try (var update = connection.prepareStatement("UPDATE phantom_ai_model_versions SET status='ACCEPTED' WHERE model_id=? AND status='DRAFT'"))
		{
			update.setLong(1, id);
			return update.executeUpdate() == 1;
		}
	}

	public Checkpoint trainNative(int limit, long seed) throws SQLException
	{
		return trainNative(limit, seed, false);
	}

	public Checkpoint trainNative(int limit, long seed, boolean shadowOnly) throws SQLException
	{
		record AuditRow(Example example, String actor, int skill, String scope, String context) { }
		final List<Example> train = new ArrayList<>();
		final List<Example> tuning = new ArrayList<>();
		final List<Example> holdout = new ArrayList<>();
		final List<AuditRow> auditRows = new ArrayList<>();
		final var fittedContexts = new HashSet<String>();
		final var trainSkills = new HashSet<Integer>();
		final var holdoutSkills = new HashSet<Integer>();
		final var trainScopes = new HashSet<String>();
		final var holdoutScopes = new HashSet<String>();
		final var holdoutActors = new HashSet<String>();
		final var provenance = digest();
		try (Connection connection = connect(); var sql = connection.prepareStatement("SELECT event_key,actor_group,skill_id,scope,features,reward FROM phantom_ai_training_examples "
			+ "WHERE origin='NATIVE' AND evidence_kind='EXACT_CAST_HP' AND damage>0 AND schema_fingerprint=? AND skill_catalog=?"
			+ (shadowOnly ? " AND policy_version=0" : "") + " ORDER BY created_at DESC,event_key LIMIT ?"))
		{
			sql.setString(1, LearningFeatures.SCHEMA);
			sql.setString(2, _catalog);
			sql.setInt(3, Math.clamp(limit, 32, NeuralPolicy.MAX_EXAMPLES));
			try (var rows = sql.executeQuery())
			{
				while (rows.next())
				{
					final String actorGroup = rows.getString(2);
					final int split = Math.floorMod(actorGroup.hashCode(), 5);
					final byte[] features = rows.getBytes(5);
					final float reward = rows.getFloat(6);
					final Example example = new Example(LearningFeatures.decode(features), reward);
					final String context = contextKey(features);
					switch (split)
					{
						case 0 -> auditRows.add(new AuditRow(example, actorGroup, rows.getInt(3), rows.getString(4), context));
						case 1 ->
						{
							tuning.add(example);
							fittedContexts.add(context);
						}
						default ->
						{
							train.add(example);
							fittedContexts.add(context);
							trainSkills.add(rows.getInt(3));
							trainScopes.add(rows.getString(4));
						}
					}
					provenance.update(rows.getString(1).getBytes(StandardCharsets.US_ASCII));
					provenance.update(features);
					provenance.update(java.nio.ByteBuffer.allocate(Float.BYTES).putFloat(reward).array());
				}
			}
		}
		for (AuditRow row : auditRows)
		{
			if (fittedContexts.contains(row.context())) { continue; }
			holdout.add(row.example());
			holdoutSkills.add(row.skill());
			holdoutScopes.add(row.scope());
			holdoutActors.add(row.actor());
		}
		if (trainScopes.size() != 2 || holdoutScopes.size() != 2 || holdoutActors.size() < 2 || trainSkills.size() < 2 || holdoutSkills.size() < 2)
		{
			throw new IllegalStateException("Native training requires both PvE scopes and distinct skills in training and independent audit, with distinct audit actors");
		}
		final var result = NeuralPolicy.audit(NeuralPolicy.trainResidual(train, tuning, seed), holdout, seed);
		return save(result, train.size(), holdout.size(), HexFormat.of().formatHex(provenance.digest()), "NATIVE");
	}

	private static final int[] CONTEXT_SLOTS = {0, 3, 4, 5, 6, 7, 8, 12, 13, 14, 15, 19, 20, 28, 30, 31, 32, 41, 43, 44, 45, 46};

	static String contextKey(byte[] features)
	{
		final MessageDigest digest = digest();
		for (int slot : CONTEXT_SLOTS) { digest.update(features, slot * 2 * Float.BYTES, 2 * Float.BYTES); }
		return HexFormat.of().formatHex(digest.digest());
	}

	Checkpoint save(Training training, int train, int holdout, String trainingHash, String origin) throws SQLException
	{
		if (!qualified(training.step(), train, holdout, training.initialLoss(), training.loss()))
		{
			throw new IllegalArgumentException("Checkpoint did not meet validation threshold");
		}
		try (Connection connection = connect(); var insert = connection.prepareStatement(
			"INSERT INTO phantom_ai_model_versions (algo_version,schema_fingerprint,skill_catalog,step,training_hash,weights_blob,sha256,training_count,holdout_count,initial_mse,holdout_mse,origin) "
				+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS))
		{
			insert.setInt(1, training.model().algorithm());
			insert.setString(2, LearningFeatures.SCHEMA);
			insert.setString(3, _catalog);
			insert.setInt(4, training.step());
			insert.setString(5, trainingHash);
			insert.setBytes(6, training.model().bytes());
			insert.setString(7, training.model().sha256());
			insert.setInt(8, train);
			insert.setInt(9, holdout);
			insert.setDouble(10, training.initialLoss());
			insert.setDouble(11, training.loss());
			insert.setString(12, origin);
			insert.executeUpdate();
			try (var keys = insert.getGeneratedKeys())
			{
				if (!keys.next()) { throw new SQLException("Missing model version"); }
				return new Checkpoint(keys.getLong(1), training.model(), trainingHash, training.step(), train, holdout, training.initialLoss(), training.loss());
			}
		}
	}

	void persistFixture(List<Evidence> batch) throws SQLException
	{
		persist(batch, false);
	}

	private void persist(List<Evidence> batch, boolean nativeEvidence) throws SQLException
	{
		try (Connection connection = connect())
		{
			connection.setAutoCommit(false);
			try (var insert = connection.prepareStatement("INSERT INTO phantom_ai_training_examples (event_key,actor_group,scope,origin,evidence_kind,policy_version,schema_fingerprint,skill_catalog,skill_id,skill_level,damage,reward,features) "
				+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE event_key=event_key"))
			{
				for (Evidence evidence : batch)
				{
					insert.setString(1, evidence.eventKey());
					insert.setString(2, evidence.actorGroup());
					insert.setString(3, evidence.scope());
					insert.setString(4, nativeEvidence ? "NATIVE" : "SYNTHETIC");
					insert.setString(5, nativeEvidence ? "EXACT_CAST_HP" : "FIXTURE");
					insert.setLong(6, evidence.version());
					insert.setString(7, LearningFeatures.SCHEMA);
					insert.setString(8, _catalog);
					insert.setInt(9, evidence.skill());
					insert.setInt(10, evidence.level());
					insert.setDouble(11, evidence.damage());
					insert.setFloat(12, evidence.reward());
					insert.setBytes(13, LearningFeatures.encode(evidence.features()));
					insert.addBatch();
				}
				insert.executeBatch();
			}
			try (var statement = connection.createStatement(); var count = statement.executeQuery("SELECT COUNT(*) FROM phantom_ai_training_examples"))
			{
				count.next();
				final int excess = count.getInt(1) - RETENTION;
				if (excess > 0)
				{
					statement.executeUpdate("DELETE FROM phantom_ai_training_examples ORDER BY created_at,event_key LIMIT " + excess);
				}
			}
			connection.commit();
		}
	}

	private static MessageDigest digest()
	{
		try { return MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
	}

	/** Offline CLI. Credentials are supplied via process environment in an isolated operator shell. */
	public static void main(String[] args) throws IOException, SQLException
	{
		if (args.length < 3 || !Boolean.getBoolean("phantom.learning.train"))
		{
			throw new IllegalArgumentException("Usage: train <gameRoot> <limit> <seed> | promote <gameRoot> <version> | read <gameRoot> <version>");
		}
		final Path gameRoot = Path.of(args[1]);
		final var store = new LearningStore(System.getenv("PHANTOM_LEARNING_JDBC"), "learning001", System.getenv("PHANTOM_LEARNING_PASSWORD"), gameRoot);
		if ("promote".equals(args[0]) && args.length == 3)
		{
			if (!store.promote(Long.parseLong(args[2]))) { throw new IllegalStateException("Model promotion refused"); }
			System.out.println("Accepted model " + args[2]);
			return;
		}
		final Checkpoint checkpoint = switch (args[0])
		{
			case "train" -> args.length == 4 ? store.trainNative(Math.min(Integer.parseInt(args[2]), new ConfigReader(gameRoot.resolve("config/Custom/PhantomPlayers.ini").toString()).getInt("LearningMaxExamplesPerBatch", NeuralPolicy.MAX_EXAMPLES)), Long.parseLong(args[3])) : null;
			case "read" -> args.length == 3 ? store.accepted(Long.parseLong(args[2])) : null;
			default -> null;
		};
		if (checkpoint == null) { throw new IllegalStateException("Learning checkpoint is unavailable"); }
		System.out.printf(java.util.Locale.ROOT, "model=%d sha256=%s schema=%s step=%d train=%d holdout=%d initialMse=%.6f holdoutMse=%.6f%n",
			checkpoint.id(), checkpoint.model().sha256(), LearningFeatures.SCHEMA, checkpoint.step(), checkpoint.trainCount(), checkpoint.holdoutCount(), checkpoint.initialLoss(), checkpoint.loss());
	}

	final class Writer
	{
		private final ArrayBlockingQueue<Evidence> _queue = new ArrayBlockingQueue<>(512);
		private final LongAdder _dropped = new LongAdder();
		private final LongAdder _persistedBatches = new LongAdder();
		private final LongAdder _persistedExamples = new LongAdder();
		private long _totalSqlNanos;
		private long _maxSqlNanos;
		private final Thread _thread;
		private volatile boolean _accepting = true;

		Writer()
		{
			_thread = Thread.ofPlatform().daemon().name("PhantomLearningEvidence").start(this::run);
		}

		String actorKey(long profileId)
		{
			return NeuralPolicy.sha256((_actorSalt + ":" + profileId).getBytes(StandardCharsets.UTF_8));
		}

		synchronized boolean offer(Evidence evidence)
		{
			if (!_accepting || !_queue.offer(evidence))
			{
				_dropped.increment();
				return false;
			}
			return true;
		}

		synchronized boolean stop()
		{
			_accepting = false;
			return !_thread.isAlive();
		}

		private void run()
		{
			final List<Evidence> batch = new ArrayList<>(64);
			try
			{
				while (_accepting || !_queue.isEmpty())
				{
					final Evidence first = _queue.poll(200, TimeUnit.MILLISECONDS);
					if (first == null) { continue; }
					batch.add(first);
					_queue.drainTo(batch, 63);
					try
					{
						final long started = System.nanoTime();
						persist(batch, true);
						final long nanos = System.nanoTime() - started;
						_persistedBatches.increment(); _persistedExamples.add(batch.size());
						_totalSqlNanos += nanos; _maxSqlNanos = Math.max(_maxSqlNanos, nanos);
					}
					catch (SQLException | RuntimeException failure) { _dropped.add(batch.size()); }
					batch.clear();
				}
			}
			catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
			finally
			{
				java.util.logging.Logger.getLogger(LearningStore.class.getName()).info("Learning evidence writer stopped, dropped=" + (_dropped.sum() + _queue.size())
					+ " sqlBatches=" + _persistedBatches.sum() + " persistedOffers=" + _persistedExamples.sum() + " totalSqlNanos=" + _totalSqlNanos + " maxSqlNanos=" + _maxSqlNanos);
			}
		}
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStateCodec;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/** Guarded TEST clone with durable before-images and exact-row restoration; PLAY is SELECT-only. */
public final class PhantomM1PopulationFixture implements AutoCloseable
{
	public static final Path SOURCE_CONFIG = Path.of("C:/Users/ZBook/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/artifacts/local-play/runtime/game/config/Database.ini");
	private static final String SOURCE_DATABASE = "l2jmobiush5_localplay3";
	private static final int MAGIC = 0x4D315738;
	private static final int MAX_TABLES = 256;
	private static final int MAX_ROWS = 1_000_000;
	private static final int MAX_CELL_BYTES = 4_194_304;
	private static final long MAX_JOURNAL_BYTES = 536_870_912L;
	private static final Pattern CREATE_TABLE = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-z][a-z0-9_]*)`?\\s*\\(");
	private static final List<String> IMPORT_ORDER = List.of("accounts", "characters", "items", "character_skills", "character_shortcuts", "character_reco_bonus", "character_skills_save", "character_variables", "phantom_profiles", "phantom_profile_components");
	private static final Set<String> COMPONENTS = Set.of("population.state", "population.ecology", "background.state", "background.catchup", "goal.runtime");
	private static final Set<String> GLOBAL_WRITERS = Set.of("itemsonground", "seven_signs_status", "territories", "territory_registrations", "castle", "clanhall", "fort", "auction");
	private static final String IDS = "SELECT character_object_id FROM phantom_profiles";
	private static final String ITEM_IDS = "SELECT object_id FROM items WHERE owner_id IN (" + IDS + ")";
	private static final String ACCOUNTS = "SELECT account_name FROM characters WHERE charId IN (" + IDS + ")";
	private static final Map<String, String> PRIVATE_COLUMNS = Map.ofEntries(
		Map.entry("auction_watch", "charObjId"), Map.entry("bbs_favorites", "playerId"), Map.entry("buffer_schemes", "object_id"),
		Map.entry("character_hennas", "charId"), Map.entry("character_instance_time", "charId"), Map.entry("character_item_reuse_save", "charId"),
		Map.entry("character_macroses", "charId"), Map.entry("character_offline_play", "charId"), Map.entry("character_offline_trade", "charId"),
		Map.entry("character_offline_trade_items", "charId"), Map.entry("character_premium_items", "charId"), Map.entry("character_quests", "charId"),
		Map.entry("character_raid_points", "charId"), Map.entry("character_recipebook", "charId"), Map.entry("character_recipeshoplist", "charId"),
		Map.entry("character_subclasses", "charId"), Map.entry("character_summon_skills_save", "ownerId"), Map.entry("character_summons", "ownerId"),
		Map.entry("character_tpbookmark", "charId"), Map.entry("heroes", "charId"), Map.entry("merchant_lease", "player_id"),
		Map.entry("olympiad_nobles", "charId"), Map.entry("olympiad_nobles_eom", "charId"), Map.entry("seven_signs", "charId"));
	private final PhantomTestContext _context;
	private final PhantomTestDatabaseGuard.ValidatedSettings _target;
	private final PhantomTestSchemaManifest.Snapshot _manifest;
	private final PhantomProvisioningLock _lock;
	private final Path _journal;
	private final Image _before;
	private final Image _source;
	private final List<String> _order;
	private final List<Identity> _ready;
	private final Set<Long> _profileIds;
	private boolean _closed;
	private boolean _lockReleased;

	private PhantomM1PopulationFixture(PhantomTestContext context, PhantomTestDatabaseGuard.ValidatedSettings target, PhantomTestSchemaManifest.Snapshot manifest, PhantomProvisioningLock lock, Path journal, Image before, Image source, List<String> order) throws Exception
	{
		_context = context; _target = target; _manifest = manifest; _lock = lock; _journal = journal;
		_before = before; _source = source; _order = order; _ready = validateSource(source);
		_profileIds = new HashSet<>(); final Table profiles = source.tables().get("phantom_profiles");
		for (byte[][] row : profiles.rows()) { _profileIds.add(number(profiles, row, "profile_id")); }
	}

	/** Runs before native DatabaseFactory/IdManager; no target schema or source writes. */
	public static PhantomM1PopulationFixture apply(PhantomTestContext context, Path sourceConfig, Path testConfig) throws Exception
	{
		requireStopped();
		final var target = PhantomTestDatabaseGuard.validate(context.moduleRoot(), testConfig);
		final var manifest = manifest(context);
		Class.forName(target.driver());
		final Path journal = context.moduleRoot().resolve(".phantom-local/m1-007-population-restore.bin");
		final var lock = PhantomProvisioningLock.acquire(context.moduleRoot().resolve(".phantom-local/test-db.lock"));
		try (Connection connection = targetConnection(target, manifest))
		{
			connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			connection.setAutoCommit(false);
			final Set<String> allowlist = schemaTables(context);
			final List<String> order = tableOrder(connection, allowlist);
			final Image current = snapshot(connection, allowlist);
			context.record("w.fixture.restoreTableAllowlist", String.join(",", allowlist));
			context.record("w.fixture.restoreTableCount", allowlist.size());
			context.record("w.fixture.schemaSha256", manifest.aggregateSha256());
			for (var table : current.tables().values()) { context.record("w.fixture.beforeTable." + table.name(), "rows=" + table.rows().size() + ",hash=" + table.hash() + ",columns=" + table.columns() + ",keys=" + table.keys()); }
			final PhantomM1PopulationFixture fixture;
			if (Files.exists(journal))
			{
				final var saved = readJournal(journal, manifest.aggregateSha256());
				fixture = new PhantomM1PopulationFixture(context, target, manifest, lock, journal, saved.get(0), saved.get(1), order);
				require(fixture._before.tables().keySet().equals(allowlist), "M1_FIXTURE_JOURNAL_TABLE_ALLOWLIST");
				if (current.hash().equals(merged(fixture._before, fixture._source).hash()))
				{
					context.record("w.fixture.recovery", "APPLIED_EXACT_IMAGE");
					fixture.recordEvidence();
					return fixture;
				}
				require(current.hash().equals(fixture._before.hash()), "M1_FIXTURE_UNKNOWN_IMAGE_RESTORE_REQUIRED");
				context.record("w.fixture.recovery", "BEFORE_EXACT_IMAGE");
			}
			else
			{
				final Table accounts = current.tables().get("accounts");
				require(accounts.rows().stream().noneMatch(row -> ("phantom_t004_" + context.seed()).equals(value(accounts, row, "login"))), "M1_FIXTURE_TARGET_HUMAN_ACCOUNT_COLLISION");
				final Image source = sourceSnapshot(sourceConfig);
				validateSource(source);
				validateCollision(connection, current, source);
				writeJournal(journal, manifest.aggregateSha256(), current, source);
				fixture = new PhantomM1PopulationFixture(context, target, manifest, lock, journal, current, source, order);
			}
			fixture.insert(connection);
			fixture.recordEvidence();
			return fixture;
		}
		catch (Throwable failure) { try { lock.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); } throw failure; }
	}

	/** Interrupted fixture recovery never captures or reapplies a different live source. */
	public static void restoreInterrupted(PhantomTestContext context, Path testConfig) throws Exception
	{
		requireStopped();
		final var target = PhantomTestDatabaseGuard.validate(context.moduleRoot(), testConfig);
		final var manifest = manifest(context);
		Class.forName(target.driver());
		final Path journal = context.moduleRoot().resolve(".phantom-local/m1-007-population-restore.bin");
		try (var lock = PhantomProvisioningLock.acquire(context.moduleRoot().resolve(".phantom-local/test-db.lock")); Connection connection = targetConnection(target, manifest))
		{
			final var saved = readJournal(journal, manifest.aggregateSha256());
			final Set<String> allowlist = schemaTables(context);
			require(saved.get(0).tables().keySet().equals(allowlist), "M1_FIXTURE_JOURNAL_TABLE_ALLOWLIST");
			new PhantomM1PopulationFixture(context, target, manifest, lock, journal, saved.get(0), saved.get(1), tableOrder(connection, allowlist)).restore(connection);
		}
	}

	public List<Identity> readyIdentities() { return _ready; }

	/** Q14 only: captures the complete guarded TEST image without reading PLAY or importing rows. */
	public static NativeSnapshot snapshotOnly(PhantomTestContext context, Path testConfig, Path journal, long profileId, int objectId) throws Exception
	{
		require(!PhantomSystem.hasConfiguredInstance(), "Q14_SNAPSHOT_CONFIGURED_OWNER_PRESENT");
		final var target = PhantomTestDatabaseGuard.validate(context.moduleRoot(), testConfig);
		final var manifest = manifest(context); Class.forName(target.driver());
		final Path path = nativeJournalPath(context, journal);
		final var lock = PhantomProvisioningLock.acquire(context.moduleRoot().resolve(".phantom-local/test-db.lock"));
		try (Connection connection = targetConnection(target, manifest))
		{
			require(!Files.exists(path), "Q14_SNAPSHOT_EXISTING_JOURNAL_RESTORE_REQUIRED");
			connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); connection.setAutoCommit(false);
			final Set<String> allowlist = schemaTables(context); require(allowlist.size() <= 200, "Q14_SNAPSHOT_TABLE_BOUND");
			final List<String> order = tableOrder(connection, allowlist); final Image before = snapshot(connection, allowlist);
			nativeOwners(context, before, before, profileId, objectId);
			writeJournal(path, manifest.aggregateSha256(), before, before);
			makeNativeJournalPrivate(path); connection.rollback();
			context.record("q14.snapshot.beforeHash", before.hash()); context.record("q14.snapshot.tableCount", allowlist.size());
			context.record("q14.snapshot.tableAllowlist", String.join(",", allowlist));
			context.record("q14.snapshot.schemaHash", manifest.aggregateSha256());
			return new NativeSnapshot(context, target, manifest, lock, path, before, order, profileId, objectId);
		}
		catch (Throwable failure) { try { lock.close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); } throw failure; }
	}

	/** Exact interrupted Q14 restore; retained journals are never replaced with a new before-image. */
	public static void restoreNativeSnapshot(PhantomTestContext context, Path testConfig, Path journal, long profileId, int objectId) throws Exception
	{
		final var target = PhantomTestDatabaseGuard.validate(context.moduleRoot(), testConfig); final var manifest = manifest(context); Class.forName(target.driver());
		final Path path = nativeJournalPath(context, journal); final var saved = readJournal(path, manifest.aggregateSha256());
		require(saved.get(0).hash().equals(saved.get(1).hash()), "Q14_SNAPSHOT_JOURNAL_KIND");
		final var lock = PhantomProvisioningLock.acquire(context.moduleRoot().resolve(".phantom-local/test-db.lock"));
		try
		{
			require(saved.get(0).tables().keySet().equals(schemaTables(context)), "Q14_SNAPSHOT_TABLE_ALLOWLIST");
			final NativeSnapshot restore;
			try (Connection connection = targetConnection(target, manifest))
			{
				restore = new NativeSnapshot(context, target, manifest, lock, path, saved.get(0), tableOrder(connection, saved.get(0).tables().keySet()), profileId, objectId);
			}
			restore.close();
		}
		finally { lock.close(); }
	}

	public static final class NativeSnapshot implements AutoCloseable
	{
		private final PhantomTestContext _context;
		private final PhantomTestDatabaseGuard.ValidatedSettings _target;
		private final PhantomTestSchemaManifest.Snapshot _manifest;
		private final Path _journal;
		private final Image _before;
		private final List<String> _order;
		private final long _profileId;
		private final int _objectId;
		private PhantomProvisioningLock _lock;
		private boolean _closed;

		private NativeSnapshot(PhantomTestContext context, PhantomTestDatabaseGuard.ValidatedSettings target, PhantomTestSchemaManifest.Snapshot manifest, PhantomProvisioningLock lock, Path journal, Image before, List<String> order, long profileId, int objectId)
		{ _context = context; _target = target; _manifest = manifest; _lock = lock; _journal = journal; _before = before; _order = order; _profileId = profileId; _objectId = objectId; }

		/** An unconfirmed child termination must retain the journal without touching live rows. */
		public void retainJournal() throws Exception
		{
			if (_lock != null) { _lock.close(); _lock = null; }
		}

		@Override public void close() throws Exception
		{
			if (_closed) { return; }
			require(!PhantomSystem.hasConfiguredInstance(), "Q14_SNAPSHOT_CONFIGURED_OWNER_PRESENT");
			if (_lock == null) { _lock = PhantomProvisioningLock.acquire(_context.moduleRoot().resolve(".phantom-local/test-db.lock")); }
			try (Connection connection = targetConnection(_target, _manifest))
			{
				tableOrder(connection, _before.tables().keySet()); connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); connection.setAutoCommit(false);
				try
				{
					final Image current = snapshot(connection, _before.tables().keySet());
					require(current.tables().get(PhantomTestSchemaManifest.METADATA_TABLE).hash().equals(_before.tables().get(PhantomTestSchemaManifest.METADATA_TABLE).hash()), "Q14_SNAPSHOT_MANIFEST_CHANGED");
					final RestoreOwners owners = nativeOwners(_context, _before, current, _profileId, _objectId);
					final Map<String, Delta> deltas = new TreeMap<>();
					for (String name : _order)
					{
						final Table before = _before.tables().get(name); final Table after = current.tables().get(name);
						require(before.columns().equals(after.columns()) && before.keys().equals(after.keys()), "Q14_SNAPSHOT_METADATA_CHANGED:" + name);
						final Delta changes = delta(before, after); validateRestoreDelta(name, before, changes, owners, Set.of(_profileId)); deltas.put(name, changes);
						if (!changes.remove().isEmpty() || !changes.add().isEmpty() || !changes.update().isEmpty()) { _context.record("q14.snapshot.restoreDelta." + name, "remove=" + changes.remove().size() + ",add=" + changes.add().size() + ",update=" + changes.update().size()); }
					}
					final List<String> reverse = new ArrayList<>(_order); Collections.reverse(reverse);
					for (String name : reverse) { exactDeleteRows(connection, current.tables().get(name), deltas.get(name).remove()); }
					for (String name : _order) { final Table table = _before.tables().get(name); exactUpdateRows(connection, table, deltas.get(name).update()); insertRows(connection, table, deltas.get(name).add()); }
					require(snapshot(connection, _before.tables().keySet()).hash().equals(_before.hash()), "Q14_SNAPSHOT_RESTORE_HASH_MISMATCH");
					connection.commit(); connection.setAutoCommit(true);
					require(snapshot(connection, _before.tables().keySet()).hash().equals(_before.hash()), "Q14_SNAPSHOT_RESTORE_COMMIT_HASH_MISMATCH");
					_context.record("q14.snapshot.restoredHash", _before.hash()); Files.delete(_journal); _closed = true;
				}
				catch (Throwable failure) { rollback(connection, failure); throw failure; }
			}
			finally { _lock.close(); _lock = null; }
		}
	}

	private static RestoreOwners nativeOwners(PhantomTestContext context, Image before, Image current, long profileId, int objectId)
	{
		require(profileId > 0 && objectId > 0, "Q14_SNAPSHOT_IDENTITY_REQUIRED");
		final String account = "phantom_t004_" + context.seed();
		for (Image image : List.of(before, current))
		{
			final Table characters = image.tables().get("characters");
			final var rows = characters.rows().stream().filter(row -> number(characters, row, "charId") == objectId).toList();
			require(rows.size() == 1 && account.equals(value(characters, rows.getFirst(), "account_name")), "Q14_SNAPSHOT_FOREIGN_CHARACTER");
			final Table profiles = image.tables().get("phantom_profiles");
			final var linked = profiles.rows().stream().filter(row -> number(profiles, row, "profile_id") == profileId).toList();
			require(linked.size() == 1 && number(profiles, linked.getFirst(), "character_object_id") == objectId, "Q14_SNAPSHOT_FOREIGN_PROFILE");
		}
		final Set<Long> items = new HashSet<>();
		for (Image image : List.of(before, current)) { final Table table = image.tables().get("items"); for (byte[][] row : table.rows()) { if (number(table, row, "owner_id") == objectId) { items.add(number(table, row, "object_id")); } } }
		return new RestoreOwners(Set.of((long) objectId), Set.of(account), items);
	}

	private static Path nativeJournalPath(PhantomTestContext context, Path journal) throws Exception
	{
		final Path root = context.moduleRoot().resolve(".phantom-local").toRealPath(); final Path path = journal.toAbsolutePath().normalize();
		require(path.getParent().toRealPath().startsWith(root) && path.getFileName().toString().startsWith("m1-007-q14-") && !Files.isSymbolicLink(path), "Q14_SNAPSHOT_PRIVATE_JOURNAL_PATH"); return path;
	}

	private static void makeNativeJournalPrivate(Path path) throws Exception
	{
		final var principal = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
		final var acl = java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(principal).setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class)).build();
		Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class).setAcl(List.of(acl));
	}
	public List<Integer> characterObjectIds() { final Table table = _source.tables().get("characters"); return table.rows().stream().map(row -> Math.toIntExact(number(table, row, "charId"))).toList(); }
	private void recordEvidence() throws Exception
	{
		_context.record("w.fixture.sourceHash", _source.hash());
		_context.record("w.fixture.beforeHash", _before.hash());
		_context.record("w.fixture.expectedHash", merged(_before, _source).hash());
		_context.record("w.fixture.sourceCapturedAt", _source.capturedAt());
		for (var table : _source.tables().values()) { _context.record("w.fixture.sourceTable." + table.name(), "rows=" + table.rows().size() + ",hash=" + table.hash()); }
		_context.record("w.fixture.population", "profiles=10000,components=50000,READY=1280,RETIRED=8720");
	}

	@Override
	public void close() throws Exception
	{
		if (_closed) { return; }
		requireStopped();
		final var lock = _lockReleased ? PhantomProvisioningLock.acquire(_context.moduleRoot().resolve(".phantom-local/test-db.lock")) : _lock;
		try (Connection connection = targetConnection(_target, _manifest)) { restore(connection); _closed = true; }
		finally { _lockReleased = true; lock.close(); }
	}

	private void insert(Connection connection) throws Exception
	{
		connection.setAutoCommit(false);
		try
		{
			for (String name : IMPORT_ORDER) { insertRows(connection, _source.tables().get(name), _source.tables().get(name).rows()); }
			require(snapshot(connection, _before.tables().keySet()).hash().equals(merged(_before, _source).hash()), "M1_FIXTURE_APPLY_HASH_MISMATCH");
			connection.commit();
			connection.setAutoCommit(true);
			require(snapshot(connection, _before.tables().keySet()).hash().equals(merged(_before, _source).hash()), "M1_FIXTURE_APPLY_COMMIT_HASH_MISMATCH");
		}
		catch (Throwable failure) { rollback(connection, failure); throw failure; }
	}

	private void restore(Connection connection) throws Exception
	{
		// Per-row CAS operations only: unchanged ordinary/native world rows are never deleted.
		tableOrder(connection, _before.tables().keySet());
		connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
		connection.setAutoCommit(false);
		try
		{
			final Image current = snapshot(connection, _before.tables().keySet());
			require(current.tables().get(PhantomTestSchemaManifest.METADATA_TABLE).hash().equals(_before.tables().get(PhantomTestSchemaManifest.METADATA_TABLE).hash()), "M1_FIXTURE_MANIFEST_ROW_CHANGED");
			final RestoreOwners owners = restoreOwners(current);
			final Map<String, Delta> deltas = new TreeMap<>();
			for (String name : _order)
			{
				final Table before = _before.tables().get(name); final Table after = current.tables().get(name);
				require(before.columns().equals(after.columns()) && before.keys().equals(after.keys()), "M1_FIXTURE_RESTORE_METADATA_CHANGED:" + name);
				final Delta change = delta(before, after);
				validateRestoreDelta(name, before, change, owners);
				if (!change.remove().isEmpty() || !change.add().isEmpty() || !change.update().isEmpty()) { _context.record("w.fixture.restoreDelta." + name, "remove=" + change.remove().size() + ",add=" + change.add().size() + ",update=" + change.update().size()); }
				deltas.put(name, change);
			}
			final List<String> reverse = new ArrayList<>(_order); Collections.reverse(reverse);
			for (String name : reverse) { exactDeleteRows(connection, current.tables().get(name), deltas.get(name).remove()); }
			for (String name : _order)
			{
				final Table table = _before.tables().get(name); final Delta delta = deltas.get(name);
				exactUpdateRows(connection, table, delta.update());
				insertRows(connection, table, delta.add());
			}
			require(snapshot(connection, _before.tables().keySet()).hash().equals(_before.hash()), "M1_FIXTURE_RESTORE_HASH_MISMATCH");
			connection.commit();
			connection.setAutoCommit(true);
			require(snapshot(connection, _before.tables().keySet()).hash().equals(_before.hash()), "M1_FIXTURE_RESTORE_COMMIT_HASH_MISMATCH");
			_context.record("w.fixture.restoredHash", _before.hash());
			Files.delete(_journal);
		}
		catch (Throwable failure) { rollback(connection, failure); throw failure; }
	}

	private static Delta delta(Table before, Table current) throws Exception
	{
		final List<byte[][]> remove = new ArrayList<>(); final List<byte[][]> add = new ArrayList<>(); final List<Change> update = new ArrayList<>();
		if (!before.keys().isEmpty())
		{
			final Map<String, byte[][]> original = keyed(before); final Map<String, byte[][]> after = keyed(current);
			for (var row : after.entrySet()) { final byte[][] old = original.get(row.getKey()); if (old == null) { remove.add(row.getValue()); } else if (!rowHash(old).equals(rowHash(row.getValue()))) { update.add(new Change(row.getValue(), old)); } }
			for (var row : original.entrySet()) { if (!after.containsKey(row.getKey())) { add.add(row.getValue()); } }
		}
		else
		{
			final Map<String, List<byte[][]>> original = bag(before); final Map<String, List<byte[][]>> after = bag(current);
			final Set<String> hashes = new HashSet<>(original.keySet()); hashes.addAll(after.keySet());
			for (String hash : hashes) { final var old = original.getOrDefault(hash, List.of()); final var now = after.getOrDefault(hash, List.of()); if (now.size() > old.size()) { remove.addAll(now.subList(old.size(), now.size())); } if (old.size() > now.size()) { add.addAll(old.subList(now.size(), old.size())); } }
		}
		return new Delta(remove, add, update);
	}

	private static Map<String, byte[][]> keyed(Table table) throws Exception { final Map<String, byte[][]> result = new HashMap<>(); for (byte[][] row : table.rows()) { require(result.put(key(table, row, table.keys(), false), row) == null, "M1_FIXTURE_DUPLICATE_ROW_KEY"); } return result; }
	private static Map<String, List<byte[][]>> bag(Table table) throws Exception { final Map<String, List<byte[][]>> result = new HashMap<>(); for (byte[][] row : table.rows()) { result.computeIfAbsent(rowHash(row), _ -> new ArrayList<>()).add(row); } return result; }
	private static String exactPredicate(Table table) { return String.join(" AND ", table.columns().stream().map(column -> (text(column.type()) ? "BINARY " : "") + quote(column.name()) + " <=> " + (text(column.type()) ? "BINARY " : "") + "?").toList()); }
	private static void exactDeleteRows(Connection connection, Table table, List<byte[][]> rows) throws Exception
	{
		if (rows.isEmpty()) { return; }
		try (var statement = connection.prepareStatement("DELETE FROM " + quote(table.name()) + " WHERE " + exactPredicate(table) + " LIMIT 1"))
		{
			statement.setQueryTimeout(30);
			int pending = 0;
			for (byte[][] row : rows) { bind(statement, table, row, 1); statement.addBatch(); if (++pending == 250) { exactBatch(statement, pending, table.name()); pending = 0; } }
			if (pending > 0) { exactBatch(statement, pending, table.name()); }
		}
	}
	private static void exactUpdateRows(Connection connection, Table table, List<Change> changes) throws Exception
	{
		if (changes.isEmpty()) { return; }
		final String assignments = String.join(",", table.columns().stream().map(column -> quote(column.name()) + "=?").toList());
		try (var statement = connection.prepareStatement("UPDATE " + quote(table.name()) + " SET " + assignments + " WHERE " + exactPredicate(table) + " LIMIT 1"))
		{
			statement.setQueryTimeout(30);
			int pending = 0;
			for (Change change : changes) { final int next = bind(statement, table, change.before(), 1); bind(statement, table, change.current(), next); statement.addBatch(); if (++pending == 250) { exactBatch(statement, pending, table.name()); pending = 0; } }
			if (pending > 0) { exactBatch(statement, pending, table.name()); }
		}
	}
	private static void exactBatch(PreparedStatement statement, int pending, String table) throws Exception { final int[] counts = statement.executeBatch(); require(counts.length == pending, "M1_FIXTURE_CAS_BATCH_COUNT:" + table); for (int count : counts) { require(count == 1, "M1_FIXTURE_RESTORE_ROW_CHANGED:" + table); } }

	private static PhantomTestSchemaManifest.Snapshot manifest(PhantomTestContext context) throws Exception { final var manifest = PhantomTestSchemaManifest.current(context.moduleRoot()); PhantomTestSchemaManifest.requireExact(manifest, PhantomTestSchemaManifest.read(PhantomTestSchemaManifest.localPath(context.moduleRoot()))); return manifest; }
	private static Connection targetConnection(PhantomTestDatabaseGuard.ValidatedSettings settings, PhantomTestSchemaManifest.Snapshot manifest) throws Exception
	{
		final Connection connection = DriverManager.getConnection(settings.url(), settings.login(), settings.password());
		try
		{
			try (var statement = connection.prepareStatement("SELECT DATABASE(), CURRENT_USER()"); var rows = statement.executeQuery()) { require(rows.next() && PhantomTestDatabaseGuard.TARGET_DATABASE.equals(rows.getString(1)) && rows.getString(2).startsWith(PhantomTestDatabaseGuard.TARGET_USER + "@"), "M1_FIXTURE_TARGET_IDENTITY"); }
			PhantomTestSchemaManifest.requireExactDatabaseMetadata(connection, manifest); return connection;
		}
		catch (Throwable failure) { connection.close(); throw failure; }
	}

	private static Image sourceSnapshot(Path config) throws Exception
	{
		require(config.toRealPath().equals(SOURCE_CONFIG.toRealPath()), "M1_FIXTURE_SOURCE_CONFIG_PATH");
		final Properties properties = new Properties(); try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) { properties.load(reader); }
		final String configuredUrl = properties.getProperty("URL", "").trim();
		require(configuredUrl.startsWith("jdbc:mysql://127.0.0.1:3308/" + SOURCE_DATABASE + "?") || configuredUrl.equals("jdbc:mysql://127.0.0.1:3308/" + SOURCE_DATABASE), "M1_FIXTURE_SOURCE_URL");
		// A dropped source connection must fail, never reconnect into a different snapshot.
		final String url = configuredUrl.replace("autoReconnect=true", "autoReconnect=false");
		Class.forName(properties.getProperty("Driver", "").trim());
		try (Connection connection = DriverManager.getConnection(url, properties.getProperty("Login", "").trim(), properties.getProperty("Password", "").trim()))
		{
			connection.setReadOnly(true); connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); connection.setAutoCommit(false);
			try
			{
				require(scalar(connection, "SELECT DATABASE()").equals(SOURCE_DATABASE), "M1_FIXTURE_SOURCE_IDENTITY");
				for (String name : IMPORT_ORDER) { try (var statement = connection.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?")) { statement.setString(1, name); try (var rows = statement.executeQuery()) { require(rows.next() && "InnoDB".equalsIgnoreCase(rows.getString(1)), "M1_FIXTURE_SOURCE_NONTRANSACTIONAL:" + name); } } }
				final Map<String, Table> tables = new TreeMap<>();
				for (String name : IMPORT_ORDER)
				{
					final String predicate = switch (name) { case "accounts" -> " WHERE login IN (" + ACCOUNTS + ")"; case "characters" -> " WHERE charId IN (" + IDS + ")"; case "items" -> " WHERE owner_id IN (" + IDS + ")"; case "phantom_profiles", "phantom_profile_components" -> ""; default -> " WHERE charId IN (" + IDS + ")"; };
					tables.put(name, readTable(connection, name, predicate));
				}
				validateZeroClosure(connection); return new Image(Instant.now().toString(), tables);
			}
			finally { connection.rollback(); }
		}
	}

	private RestoreOwners restoreOwners(Image current)
	{
		final Set<Long> owners = new HashSet<>();
		final Table characters = _source.tables().get("characters");
		for (byte[][] row : characters.rows()) { owners.add(number(characters, row, "charId")); }
		final Set<String> accounts = new HashSet<>();
		final Table sourceAccounts = _source.tables().get("accounts");
		for (byte[][] row : sourceAccounts.rows()) { accounts.add(value(sourceAccounts, row, "login")); }
		final String humanAccount = "phantom_t004_" + _context.seed();
		accounts.add(humanAccount);
		final Table allCharacters = current.tables().get("characters");
		for (byte[][] row : allCharacters.rows()) { if (humanAccount.equals(value(allCharacters, row, "account_name"))) { owners.add(number(allCharacters, row, "charId")); } }
		final Set<Long> itemIds = new HashSet<>();
		for (Image image : List.of(_source, current)) { final Table items = image.tables().get("items"); for (byte[][] row : items.rows()) { if (owners.contains(number(items, row, "owner_id"))) { itemIds.add(number(items, row, "object_id")); } } }
		return new RestoreOwners(owners, accounts, itemIds);
	}

	private void validateRestoreDelta(String name, Table before, Delta delta, RestoreOwners owned) throws Exception
	{
		validateRestoreDelta(name, before, delta, owned, _profileIds);
	}

	private static void validateRestoreDelta(String name, Table before, Delta delta, RestoreOwners owned, Set<Long> profileIds) throws Exception
	{
		if (exactSevenSignsFestivalStartupDelta(name, before, delta)) { return; }
		final Set<Long> owners = owned.characters(); final Set<String> accounts = owned.accounts(); final Set<Long> itemIds = owned.items();
		final List<byte[][]> removedOrAdded = new ArrayList<>(delta.remove());
		// IdManager may delete expired unrelated native rows; only that missing before-row may be restored.
		for (byte[][] row : delta.add()) { if (!expiredNativeRow(name, before, row)) { removedOrAdded.add(row); } }
		for (Change change : delta.update())
		{
			// Stock IdManager globally changes only online for unrelated ordinary characters.
			if (name.equals("characters") && !owners.contains(number(before, change.before(), "charId")))
			{
				for (int i = 0; i < before.columns().size(); i++) { if (!before.columns().get(i).name().equalsIgnoreCase("online")) { require(java.util.Arrays.equals(change.before()[i], change.current()[i]), "M1_FIXTURE_FOREIGN_CHARACTER_CHANGED"); } }
				continue;
			}
			removedOrAdded.add(change.current()); removedOrAdded.add(change.before());
		}
		for (byte[][] row : removedOrAdded)
		{
			final Table table = before;
			final boolean allowed;
			if (GLOBAL_WRITERS.contains(name)) { allowed = true; }
			else if (name.equals("characters")) { allowed = owners.contains(number(table, row, "charId")); }
			else if (name.equals("accounts")) { allowed = accounts.contains(value(table, row, "login")); }
			else if (name.equals("items")) { allowed = owners.contains(number(table, row, "owner_id")); }
			else if (IMPORT_ORDER.contains(name) && name.startsWith("character_")) { allowed = owners.contains(number(table, row, "charId")); }
			else if (name.equals("phantom_profiles") || name.equals("phantom_profile_components") || name.startsWith("phantom_economy_")) { allowed = profileOwned(table, row, profileIds); }
			else if (PRIVATE_COLUMNS.containsKey(name)) { allowed = owners.contains(number(table, row, PRIVATE_COLUMNS.get(name))); }
			else if (name.equals("character_friends")) { allowed = owners.contains(number(table, row, "charId")) && owners.contains(number(table, row, "friendId")); }
			else if (name.equals("character_contacts")) { allowed = owners.contains(number(table, row, "charId")) && owners.contains(number(table, row, "contactId")); }
			else if (name.equals("character_offline_play_group")) { allowed = owners.contains(number(table, row, "leaderId")) && owners.contains(number(table, row, "charId")); }
			else if (name.equals("item_attributes") || name.equals("item_elementals")) { allowed = itemIds.contains(number(table, row, "itemId")); }
			else if (name.equals("item_variables")) { allowed = itemIds.contains(number(table, row, "id")); }
			else if (name.equals("account_gsdata") || name.equals("account_premium")) { allowed = accounts.contains(value(table, row, "account_name")); }
			else { allowed = false; }
			require(allowed, "M1_FIXTURE_UNKNOWN_OR_FOREIGN_MUTATION:" + name);
		}
	}

	private static boolean exactSevenSignsFestivalStartupDelta(String name, Table before, Delta delta)
	{
		// Reuse the proven 012R exact startup-only delta; unrelated festival changes still fail closed.
		if (!name.equals("seven_signs_festival") || delta.remove().size() != 10 || !delta.add().isEmpty() || !delta.update().isEmpty()
			|| before.rows().stream().anyMatch(row -> number(before, row, "cycle") == 4)) { return false; }
		final Set<String> expected = new HashSet<>();
		for (int festival = 0; festival < 5; festival++) { for (String cabal : List.of("dawn", "dusk")) { expected.add(festival + ":" + cabal); } }
		for (byte[][] row : delta.remove())
		{
			if (number(before, row, "cycle") != 4 || number(before, row, "date") != 0 || number(before, row, "score") != 0
				|| !"".equals(value(before, row, "members")) || !expected.remove(number(before, row, "festivalId") + ":" + value(before, row, "cabal"))) { return false; }
		}
		return expected.isEmpty();
	}

	private static boolean profileOwned(Table table, byte[][] row, Set<Long> profileIds)
	{
		String column = "profile_id";
		if (table.name().equals("phantom_economy_offers")) { column = "initiating_profile_id"; }
		final String field = value(table, row, column);
		if (field == null) { return false; }
		return profileIds.contains(Long.parseLong(field));
	}

	private static boolean expiredNativeRow(String name, Table table, byte[][] row)
	{
		if (name.equals("character_instance_time")) { return number(table, row, "time") <= System.currentTimeMillis(); }
		if (name.equals("character_skills_save")) { return number(table, row, "restore_type") == 1 && number(table, row, "systime") <= System.currentTimeMillis(); }
		return false;
	}

	private static void validateZeroClosure(Connection connection) throws Exception
	{
		for (var rule : PRIVATE_COLUMNS.entrySet()) { zero(connection, rule.getKey(), quote(rule.getValue()) + " IN (" + IDS + ")"); }
		final Map<String, String> additional = Map.ofEntries(
			Map.entry("character_friends", "charId IN (" + IDS + ") OR friendId IN (" + IDS + ")"), Map.entry("character_contacts", "charId IN (" + IDS + ") OR contactId IN (" + IDS + ")"),
			Map.entry("character_offline_play_group", "leaderId IN (" + IDS + ") OR charId IN (" + IDS + ")"),
			Map.entry("item_attributes", "itemId IN (" + ITEM_IDS + ")"), Map.entry("item_elementals", "itemId IN (" + ITEM_IDS + ")"), Map.entry("item_variables", "id IN (" + ITEM_IDS + ")"),
			Map.entry("pets", "ownerId IN (" + IDS + ") OR item_obj_id IN (" + ITEM_IDS + ")"), Map.entry("character_pet_skills_save", "petObjItemId IN (" + ITEM_IDS + ")"),
			Map.entry("account_gsdata", "account_name IN (" + ACCOUNTS + ")"), Map.entry("account_premium", "account_name IN (" + ACCOUNTS + ")"),
			Map.entry("character_transmogs", "owner IN (SELECT char_name FROM characters WHERE charId IN (" + IDS + "))"),
			Map.entry("airships", "owner_id IN (" + IDS + ")"), Map.entry("clan_data", "leader_id IN (" + IDS + ") OR new_leader_id IN (" + IDS + ")"),
			Map.entry("clan_subpledges", "leader_id IN (" + IDS + ")"), Map.entry("cursed_weapons", "charId IN (" + IDS + ")"), Map.entry("grandboss_list", "player_id IN (" + IDS + ")"),
			Map.entry("item_auction_bid", "playerObjId IN (" + IDS + ")"), Map.entry("siegable_hall_flagwar_attackers_members", "object_id IN (" + IDS + ")"),
			Map.entry("mods_wedding", "player1Id IN (" + IDS + ") OR player2Id IN (" + IDS + ")"), Map.entry("custom_mail", "receiver IN (" + IDS + ")"),
			Map.entry("messages", "senderId IN (" + IDS + ") OR receiverId IN (" + IDS + ")"), Map.entry("forums", "forum_owner_id IN (" + IDS + ")"),
			Map.entry("posts", "post_ownerid IN (" + IDS + ")"), Map.entry("topic", "topic_ownerid IN (" + IDS + ")"), Map.entry("heroes_diary", "charId IN (" + IDS + ")"),
			Map.entry("olympiad_fights", "charOneId IN (" + IDS + ") OR charTwoId IN (" + IDS + ")"), Map.entry("prime_shop_transactions", "charId IN (" + IDS + ")"));
		for (var rule : additional.entrySet()) { zero(connection, rule.getKey(), rule.getValue()); }
		zero(connection, "characters", "charId IN (" + IDS + ") AND clanid<>0");
		zero(connection, "characters", "account_name IN (" + ACCOUNTS + ") AND charId NOT IN (" + IDS + ")");
		for (String name : List.of("phantom_economy_operations", "phantom_economy_reservations", "phantom_economy_audit", "phantom_economy_offers")) { zero(connection, name, "1=1"); }
	}

	private static List<Identity> validateSource(Image source) throws Exception
	{
		require(source.tables().keySet().equals(new HashSet<>(IMPORT_ORDER)), "M1_FIXTURE_SOURCE_TABLE_SET");
		final Table profiles = source.tables().get("phantom_profiles"); final Table components = source.tables().get("phantom_profile_components");
		require(profiles.rows().size() == 10000 && components.rows().size() == 50000, "M1_FIXTURE_SOURCE_POPULATION_COUNT");
		final Map<Long, Integer> links = new HashMap<>();
		for (byte[][] row : profiles.rows())
		{
			final long id = number(profiles, row, "profile_id"); final int objectId = Math.toIntExact(number(profiles, row, "character_object_id"));
			new PhantomProfile(id, objectId, Math.toIntExact(number(profiles, row, "schema_version")), number(profiles, row, "row_version"), instant(profiles, row, "created_at"), instant(profiles, row, "updated_at"));
			require(links.put(id, objectId) == null, "M1_FIXTURE_DUPLICATE_PROFILE");
		}
		final Table characters = source.tables().get("characters"); final Table accounts = source.tables().get("accounts");
		require(characters.rows().size() == 10000 && accounts.rows().size() == 10000, "M1_FIXTURE_SOURCE_NATIVE_IDENTITY_COUNT");
		final Map<Integer, byte[][]> nativeCharacters = new HashMap<>(); final Map<String, byte[][]> nativeAccounts = new HashMap<>();
		for (byte[][] row : characters.rows()) { require(nativeCharacters.put(Math.toIntExact(number(characters, row, "charId")), row) == null, "M1_FIXTURE_DUPLICATE_CHARACTER"); }
		for (byte[][] row : accounts.rows()) { require(nativeAccounts.put(value(accounts, row, "login"), row) == null, "M1_FIXTURE_DUPLICATE_ACCOUNT"); }
		final Map<Long, Set<String>> seen = new HashMap<>(); final List<Identity> ready = new ArrayList<>(); int retired = 0;
		for (byte[][] row : components.rows())
		{
			final long id = number(components, row, "profile_id"); final String type = value(components, row, "component_type"); final byte[] payload = row[components.index("payload")];
			new PhantomProfileComponent(id, type, Math.toIntExact(number(components, row, "component_schema_version")), number(components, row, "row_version"), payload, instant(components, row, "created_at"), instant(components, row, "updated_at"));
			require(links.containsKey(id) && COMPONENTS.contains(type) && seen.computeIfAbsent(id, _ -> new HashSet<>()).add(type), "M1_FIXTURE_COMPONENT_IDENTITY");
			switch (type)
			{
				case "population.state" ->
				{
					final var state = new PhantomPopulationStateCodec().decode(payload); final int objectId = links.get(id);
					require(Integer.valueOf(objectId).equals(state.expectedCharacterObjectId()) && Integer.valueOf(objectId).equals(state.actualCharacterObjectId()), "M1_FIXTURE_NATIVE_LINK");
					require(state.reservedAccount().equals("p" + Long.toString(id, 36)), "M1_FIXTURE_RESERVED_ACCOUNT");
					final byte[][] character = nativeCharacters.get(objectId); final byte[][] account = nativeAccounts.get(state.reservedAccount());
					require(character != null && account != null && state.characterName().equals(value(characters, character, "char_name")) && state.reservedAccount().equals(value(characters, character, "account_name")) && state.ownershipToken().equals(value(accounts, account, "password")) && number(accounts, account, "accessLevel") == -1, "M1_FIXTURE_NATIVE_OWNERSHIP");
					if (state.state() == PhantomPopulationState.State.READY) { ready.add(new Identity(id, objectId, Math.toIntExact(number(characters, character, "level")), Math.toIntExact(number(characters, character, "classid")))); }
					else { require(state.state() == PhantomPopulationState.State.RETIRED, "M1_FIXTURE_UNEXPECTED_POPULATION_STATE"); retired++; }
				}
				case "population.ecology" -> new PhantomPopulationEcologyStateCodec().decode(payload);
				case "background.state" -> { final var state = new PhantomBackgroundStateCodec().decode(payload); require(state.identity().profileId() == id && state.identity().characterObjectId() == links.get(id), "M1_FIXTURE_BACKGROUND_IDENTITY"); }
				case "background.catchup" -> new PhantomBackgroundCatchupStateCodec().decode(payload);
				case "goal.runtime" -> new PhantomGoalStateCodec().decode(payload);
				default -> throw new IllegalStateException("M1_FIXTURE_UNKNOWN_COMPONENT");
			}
		}
		require(seen.size() == 10000 && seen.values().stream().allMatch(types -> types.equals(COMPONENTS)) && ready.size() == 1280 && retired == 8720, "M1_FIXTURE_READY_RETIRED_COUNT");
		ready.sort(java.util.Comparator.comparingLong(Identity::profileId)); return List.copyOf(ready);
	}

	private static void validateCollision(Connection connection, Image before, Image source) throws Exception
	{
		require(before.tables().get("phantom_profiles").rows().isEmpty() && before.tables().get("phantom_profile_components").rows().isEmpty(), "M1_FIXTURE_TARGET_PHANTOMS_PRESENT");
		for (String name : IMPORT_ORDER)
		{
			final Table original = before.tables().get(name); final Table incoming = source.tables().get(name);
			require(original != null && original.columns().equals(incoming.columns()) && original.keys().equals(incoming.keys()), "M1_FIXTURE_SOURCE_TARGET_COLUMNS:" + name);
			final Map<String, List<String>> indexes = new TreeMap<>();
			try (var rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, name, true, false)) { while (rows.next()) { final String column = rows.getString("COLUMN_NAME"); if (column != null) { indexes.computeIfAbsent(rows.getString("INDEX_NAME"), _ -> new ArrayList<>()).add(column); } } }
			for (var columns : indexes.values())
			{
				final Set<String> keys = new HashSet<>();
				for (byte[][] row : original.rows()) { final String key = key(original, row, columns, true); if (key != null) { keys.add(key); } }
				for (byte[][] row : incoming.rows()) { final String key = key(incoming, row, columns, true); require(key == null || keys.add(key), "M1_FIXTURE_KEY_COLLISION:" + name); }
			}
		}
		final Set<Long> used = new HashSet<>();
		for (var entry : Map.of("characters", "charId", "items", "object_id", "clan_data", "clan_id", "itemsonground", "object_id", "messages", "messageId").entrySet()) { final Table table = before.tables().get(entry.getKey()); for (byte[][] row : table.rows()) { used.add(number(table, row, entry.getValue())); } }
		for (var entry : Map.of("characters", "charId", "items", "object_id").entrySet()) { final Table table = source.tables().get(entry.getKey()); for (byte[][] row : table.rows()) { require(used.add(number(table, row, entry.getValue())), "M1_FIXTURE_WORLD_OBJECT_ID_COLLISION"); } }
	}

	private static Set<String> schemaTables(PhantomTestContext context) throws Exception
	{
		final Set<String> tables = new TreeSet<>();
		for (var script : PhantomTestSchemaManifest.inventory(context.moduleRoot())) { for (String sql : script.statements()) { final var matcher = CREATE_TABLE.matcher(sql); if (matcher.find()) { tables.add(matcher.group(1)); } } }
		tables.add(PhantomTestSchemaManifest.METADATA_TABLE); require(!tables.isEmpty() && tables.size() <= MAX_TABLES && tables.containsAll(IMPORT_ORDER), "M1_FIXTURE_SCHEMA_ALLOWLIST"); return tables;
	}

	private static List<String> tableOrder(Connection connection, Set<String> allowlist) throws Exception
	{
		final Set<String> actual = new TreeSet<>();
		require("0".equals(scalar(connection, "SELECT COUNT(*) FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()")), "M1_FIXTURE_UNKNOWN_DATABASE_TRIGGER");
		try (var statement = connection.prepareStatement("SELECT TABLE_NAME, ENGINE, TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"); var rows = statement.executeQuery()) { while (rows.next()) { require("BASE TABLE".equals(rows.getString(3)) && "InnoDB".equalsIgnoreCase(rows.getString(2)), "M1_FIXTURE_NONTRANSACTIONAL_OR_VIEW:" + rows.getString(1)); actual.add(rows.getString(1)); } }
		require(actual.equals(allowlist), "M1_FIXTURE_DATABASE_TABLE_ALLOWLIST_MISMATCH");
		final Map<String, Set<String>> dependencies = new TreeMap<>();
		for (String table : allowlist) { final Set<String> parents = new HashSet<>(); try (var rows = connection.getMetaData().getImportedKeys(connection.getCatalog(), null, table)) { while (rows.next()) { final String parent = rows.getString("PKTABLE_NAME"); require(allowlist.contains(parent), "M1_FIXTURE_EXTERNAL_FOREIGN_KEY"); parents.add(parent); } } dependencies.put(table, parents); }
		final List<String> order = new ArrayList<>();
		while (order.size() < allowlist.size()) { final int previous = order.size(); for (var entry : dependencies.entrySet()) { if (!order.contains(entry.getKey()) && order.containsAll(entry.getValue())) { order.add(entry.getKey()); } } require(order.size() > previous, "M1_FIXTURE_CYCLIC_FOREIGN_KEY"); }
		return List.copyOf(order);
	}

	private static Image snapshot(Connection connection, Set<String> allowlist) throws Exception { final Map<String, Table> tables = new TreeMap<>(); for (String name : allowlist) { tables.put(name, readTable(connection, name, "")); } return new Image(Instant.now().toString(), tables); }
	private static Table readTable(Connection connection, String name, String predicate) throws Exception
	{
		final List<String> keys = new ArrayList<>();
		try (var statement = connection.prepareStatement("SELECT COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_NAME='PRIMARY' ORDER BY ORDINAL_POSITION")) { statement.setString(1, name); try (var rows = statement.executeQuery()) { while (rows.next()) { keys.add(rows.getString(1)); } } }
		try (var statement = connection.prepareStatement("SELECT * FROM " + quote(name) + predicate))
		{
			statement.setQueryTimeout(30);
			try (var results = statement.executeQuery())
			{
				final var metadata = results.getMetaData(); final List<Column> columns = new ArrayList<>();
				for (int i = 1; i <= metadata.getColumnCount(); i++) { columns.add(new Column(metadata.getColumnName(i), metadata.getColumnType(i), metadata.getColumnTypeName(i), metadata.getPrecision(i), metadata.getScale(i))); }
				final List<byte[][]> rows = new ArrayList<>();
				while (results.next()) { require(rows.size() < MAX_ROWS, "M1_FIXTURE_ROW_BOUND:" + name); final byte[][] row = new byte[columns.size()][]; for (int i = 0; i < row.length; i++) { row[i] = binary(columns.get(i).type()) ? results.getBytes(i + 1) : bytes(results.getString(i + 1)); require(row[i] == null || row[i].length <= MAX_CELL_BYTES, "M1_FIXTURE_CELL_BOUND"); } rows.add(row); }
				return new Table(name, List.copyOf(columns), List.copyOf(keys), rows);
			}
		}
	}

	private static void insertRows(Connection connection, Table table, List<byte[][]> rows) throws Exception
	{
		if (rows.isEmpty()) { return; }
		final String columns = String.join(",", table.columns().stream().map(column -> quote(column.name())).toList());
		try (var statement = connection.prepareStatement("INSERT INTO " + quote(table.name()) + " (" + columns + ") VALUES (" + String.join(",", Collections.nCopies(table.columns().size(), "?")) + ")")) { statement.setQueryTimeout(30); int pending = 0; for (byte[][] row : rows) { bind(statement, table, row, 1); statement.addBatch(); if (++pending == 250) { statement.executeBatch(); pending = 0; } } if (pending > 0) { statement.executeBatch(); } }
	}
	private static int bind(PreparedStatement statement, Table table, byte[][] row, int start) throws Exception { for (int i = 0; i < row.length; i++) { final int index = start + i; final int type = table.columns().get(i).type(); if (row[i] == null) { statement.setNull(index, type); } else if (binary(type)) { statement.setBytes(index, row[i]); } else if (type == Types.BIT || type == Types.BOOLEAN) { final String value = new String(row[i], StandardCharsets.UTF_8); require(Set.of("0", "1", "true", "false").contains(value), "M1_FIXTURE_BOOLEAN_ENCODING"); statement.setBoolean(index, value.equals("1") || value.equals("true")); } else { statement.setString(index, new String(row[i], StandardCharsets.UTF_8)); } } return start + row.length; }
	private static Image merged(Image before, Image source) { final Map<String, Table> tables = new TreeMap<>(before.tables()); for (var entry : source.tables().entrySet()) { final Table old = tables.get(entry.getKey()); final List<byte[][]> rows = new ArrayList<>(old.rows()); rows.addAll(entry.getValue().rows()); tables.put(entry.getKey(), new Table(old.name(), old.columns(), old.keys(), rows)); } return new Image(source.capturedAt(), tables); }

	private static void writeJournal(Path path, String schemaHash, Image before, Image source) throws Exception
	{
		Files.createDirectories(path.getParent()); final Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try { try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW)))) { output.writeInt(MAGIC); output.writeUTF(schemaHash); writeImage(output, before); writeImage(output, source); } require(Files.size(temporary) <= MAX_JOURNAL_BYTES, "M1_FIXTURE_JOURNAL_BOUND"); try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); } Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE); }
		finally { Files.deleteIfExists(temporary); }
	}
	private static List<Image> readJournal(Path path, String schemaHash) throws Exception
	{
		require(Files.size(path) <= MAX_JOURNAL_BYTES, "M1_FIXTURE_JOURNAL_BOUND");
		try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) { require(input.readInt() == MAGIC && input.readUTF().equals(schemaHash), "M1_FIXTURE_JOURNAL_SCHEMA"); final Image before = readImage(input); final Image source = readImage(input); require(input.read() == -1, "M1_FIXTURE_JOURNAL_TRAILING_BYTES"); return List.of(before, source); }
	}
	private static void writeImage(DataOutputStream output, Image image) throws Exception
	{
		output.writeUTF(image.capturedAt()); output.writeUTF(image.hash()); output.writeInt(image.tables().size());
		for (Table table : image.tables().values()) { output.writeUTF(table.name()); output.writeInt(table.columns().size()); for (Column column : table.columns()) { output.writeUTF(column.name()); output.writeInt(column.type()); output.writeUTF(column.typeName()); output.writeInt(column.precision()); output.writeInt(column.scale()); } output.writeInt(table.keys().size()); for (String key : table.keys()) { output.writeUTF(key); } output.writeInt(table.rows().size()); for (byte[][] row : table.rows()) { for (byte[] value : row) { output.writeInt(value == null ? -1 : value.length); if (value != null) { output.write(value); } } } }
	}
	private static Image readImage(DataInputStream input) throws Exception
	{
		final String capturedAt = input.readUTF(); final String expectedHash = input.readUTF(); final int count = input.readInt(); require(count > 0 && count <= MAX_TABLES, "M1_FIXTURE_JOURNAL_TABLE_BOUND"); final Map<String, Table> tables = new TreeMap<>();
		for (int i = 0; i < count; i++) { final String name = input.readUTF(); final int columnCount = input.readInt(); require(columnCount > 0 && columnCount <= 256, "M1_FIXTURE_JOURNAL_COLUMN_BOUND"); final List<Column> columns = new ArrayList<>(); for (int j = 0; j < columnCount; j++) { columns.add(new Column(input.readUTF(), input.readInt(), input.readUTF(), input.readInt(), input.readInt())); } final int keyCount = input.readInt(); require(keyCount >= 0 && keyCount <= columnCount, "M1_FIXTURE_JOURNAL_KEY_BOUND"); final List<String> keys = new ArrayList<>(); for (int j = 0; j < keyCount; j++) { keys.add(input.readUTF()); } final int rowCount = input.readInt(); require(rowCount >= 0 && rowCount <= MAX_ROWS, "M1_FIXTURE_JOURNAL_ROW_BOUND"); final List<byte[][]> rows = new ArrayList<>(); for (int j = 0; j < rowCount; j++) { final byte[][] row = new byte[columnCount][]; for (int k = 0; k < columnCount; k++) { final int length = input.readInt(); require(length >= -1 && length <= MAX_CELL_BYTES, "M1_FIXTURE_JOURNAL_CELL_BOUND"); if (length >= 0) { row[k] = input.readNBytes(length); require(row[k].length == length, "M1_FIXTURE_JOURNAL_TRUNCATED"); } } rows.add(row); } require(tables.put(name, new Table(name, columns, keys, rows)) == null, "M1_FIXTURE_JOURNAL_DUPLICATE_TABLE"); }
		final Image image = new Image(capturedAt, tables); require(image.hash().equals(expectedHash), "M1_FIXTURE_JOURNAL_HASH"); return image;
	}

	private static String scalar(Connection connection, String sql) throws Exception { try (var statement = connection.prepareStatement(sql)) { statement.setQueryTimeout(30); try (var rows = statement.executeQuery()) { require(rows.next(), "M1_FIXTURE_SCALAR_MISSING"); return rows.getString(1); } } }
	private static void zero(Connection connection, String name, String predicate) throws Exception { require("0".equals(scalar(connection, "SELECT COUNT(*) FROM " + quote(name) + " WHERE " + predicate)), "M1_FIXTURE_SOURCE_EXTRA_CLOSURE:" + name); }
	private static String quote(String value) { require(value.matches("[A-Za-z][A-Za-z0-9_]*"), "M1_FIXTURE_SQL_IDENTIFIER"); return "`" + value + "`"; }
	private static boolean binary(int type) { return type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY || type == Types.BLOB; }
	private static boolean text(int type) { return type == Types.CHAR || type == Types.VARCHAR || type == Types.LONGVARCHAR || type == Types.NCHAR || type == Types.NVARCHAR || type == Types.LONGNVARCHAR || type == Types.CLOB; }
	private static byte[] bytes(String value) { return value == null ? null : value.getBytes(StandardCharsets.UTF_8); }
	private static String value(Table table, byte[][] row, String column) { final byte[] value = row[table.index(column)]; return value == null ? null : new String(value, StandardCharsets.UTF_8); }
	private static long number(Table table, byte[][] row, String column) { return Long.parseLong(value(table, row, column)); }
	private static Instant instant(Table table, byte[][] row, String column) { return java.sql.Timestamp.valueOf(value(table, row, column)).toInstant(); }
	private static String key(Table table, byte[][] row, List<String> columns, boolean folded) { final StringBuilder key = new StringBuilder(); for (String column : columns) { final String value = value(table, row, column); if (value == null) { return null; } key.append(value.length()).append(':').append(folded ? value.toLowerCase(Locale.ROOT) : value); } return key.toString(); }
	private static void require(boolean value, String reason) { if (!value) { throw new IllegalStateException(reason); } }
	private static void requireStopped() { require(!DatabaseFactory.isInitialized() && !PhantomSystem.hasConfiguredInstance(), "M1_FIXTURE_NATIVE_RUNTIME_NOT_STOPPED"); }
	private static void rollback(Connection connection, Throwable failure) { try { connection.rollback(); } catch (Throwable rollback) { failure.addSuppressed(rollback); } }
	private static String digest(byte[] bytes) throws Exception { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
	private static String rowHash(byte[][] row) throws Exception { final MessageDigest digest = MessageDigest.getInstance("SHA-256"); for (byte[] cell : row) { digest.update(ByteBuffer.allocate(4).putInt(cell == null ? -1 : cell.length).array()); if (cell != null) { digest.update(cell); } } return HexFormat.of().withUpperCase().formatHex(digest.digest()); }
	public record Identity(long profileId, int characterObjectId, int level, int classId) { }
	private record Column(String name, int type, String typeName, int precision, int scale) { }
	private record Change(byte[][] current, byte[][] before) { }
	private record RestoreOwners(Set<Long> characters, Set<String> accounts, Set<Long> items) { }
	private record Delta(List<byte[][]> remove, List<byte[][]> add, List<Change> update) { }
	private record Table(String name, List<Column> columns, List<String> keys, List<byte[][]> rows)
	{
		int index(String column) { for (int i = 0; i < columns.size(); i++) { if (columns.get(i).name().equalsIgnoreCase(column)) { return i; } } throw new IllegalStateException("M1_FIXTURE_COLUMN_MISSING:" + name + "." + column); }
		String hash() throws Exception { final List<String> hashes = new ArrayList<>(); for (byte[][] row : rows) { hashes.add(rowHash(row)); } hashes.sort(String::compareTo); return digest((name + "\n" + columns + "\n" + keys + "\n" + String.join("\n", hashes)).getBytes(StandardCharsets.UTF_8)); }
	}
	private record Image(String capturedAt, Map<String, Table> tables)
	{
		String hash() throws Exception { final List<String> hashes = new ArrayList<>(); for (var entry : new TreeMap<>(tables).entrySet()) { hashes.add(entry.getKey() + "=" + entry.getValue().hash()); } return digest(String.join("\n", hashes).getBytes(StandardCharsets.UTF_8)); }
	}
}

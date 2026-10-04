/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService.MetadataObservation;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomM1PopulationFixture;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;

/** Passive actual TEST DB witness; no actor selection, state setter or SQL mutation. */
final class PhantomM1InitialMetadataChecks
{
	private static final int POPULATION = 10000;
	private static final int MAXIMUM_QUERY_ROWS = 2_000_000;
	private static final String IDS = "SELECT character_object_id FROM phantom_profiles";
	private static final String ITEM_IDS = "SELECT object_id FROM items WHERE owner_id IN (" + IDS + ")";
	private static final Map<String, String> PROTECTED_QUERIES = Map.ofEntries(
		Map.entry("characters", "SELECT * FROM characters WHERE charId IN (" + IDS + ") ORDER BY charId"),
		Map.entry("subclasses", "SELECT * FROM character_subclasses WHERE charId IN (" + IDS + ") ORDER BY charId,class_id"),
		Map.entry("skills", "SELECT * FROM character_skills WHERE charId IN (" + IDS + ") ORDER BY charId,class_index,skill_id"),
		Map.entry("items", "SELECT * FROM items WHERE owner_id IN (" + IDS + ") ORDER BY object_id"),
		Map.entry("itemAttributes", "SELECT * FROM item_attributes WHERE itemId IN (" + ITEM_IDS + ") ORDER BY itemId"),
		Map.entry("itemElementals", "SELECT * FROM item_elementals WHERE itemId IN (" + ITEM_IDS + ") ORDER BY itemId,elemType"),
		Map.entry("itemVariables", "SELECT * FROM item_variables WHERE id IN (" + ITEM_IDS + ") ORDER BY id,var"),
		Map.entry("background", "SELECT profile_id,component_schema_version,row_version,payload FROM phantom_profile_components WHERE component_type='background.state' ORDER BY profile_id"),
		Map.entry("goals", "SELECT profile_id,component_schema_version,row_version,payload FROM phantom_profile_components WHERE component_type='goal.runtime' ORDER BY profile_id"),
		Map.entry("catchup", "SELECT profile_id,component_schema_version,row_version,payload FROM phantom_profile_components WHERE component_type='background.catchup' ORDER BY profile_id"),
		Map.entry("nativeContext", "SELECT profile_id,component_schema_version,row_version,payload FROM phantom_profile_components WHERE component_type='background.native-context' ORDER BY profile_id"));
	private final Set<Integer> _objectIds;
	private final int _configuredMetadataAllowance;
	private final Census _before;
	private final AtomicReference<MetadataObservation> _latest = new AtomicReference<>();
	private final AtomicReference<Witness> _witness = new AtomicReference<>();
	private final AtomicReference<String> _failure = new AtomicReference<>();
	private final AtomicBoolean _captureStarted = new AtomicBoolean();
	private volatile long _runtimeStartedNanos;

	private PhantomM1InitialMetadataChecks(Set<Integer> objectIds, int allowance, Census before)
	{
		_objectIds = Set.copyOf(objectIds); _configuredMetadataAllowance = allowance; _before = before;
	}

	static PhantomM1InitialMetadataChecks beforeRuntime(PhantomTestContext context, PhantomM1PopulationFixture fixture, int allowance) throws Exception
	{
		final Set<Integer> ids = new HashSet<>(fixture.characterObjectIds());
		PhantomAssertions.assertEquals(POPULATION, ids.size(), "BOOTSTRAP fixture lacks the full10000 distinct imported identities.");
		PhantomAssertions.assertTrue(allowance >= 1 && allowance <= 256, "BOOTSTRAP original configured metadata allowance is outside1..256.");
		final Census before = capture(ids);
		recordCensus(context, "before", before);
		context.record("bootstrap.configuredAllowance", allowance);
		context.record("bootstrap.route", "ORIGINAL_PRODUCTION_FACTORY; passive existing-worker observation; original full10000 census and60s; no scene/config/catalog/population mutation");
		return new PhantomM1InitialMetadataChecks(ids, allowance, before);
	}

	void runtimeStarting() { _runtimeStartedNanos = System.nanoTime(); }

	/** Invoked by the original ecology worker after its monitor is released. */
	void observe(MetadataObservation observation)
	{
		_latest.set(observation);
		if (Thread.holdsLock(PhantomSystem.class)) { _failure.compareAndSet(null, "BOOTSTRAP_OBSERVER_SYSTEM_MONITOR_HELD"); return; }
		if (observation.observerFailure() != null) { _failure.compareAndSet(null, "BOOTSTRAP_OBSERVER_FAILED:" + observation.observerFailure()); return; }
		if (!observation.populationPlanApplied() || observation.registeredProfiles() != POPULATION || observation.stopping()) { return; }
		final boolean historyObserved = observation.historicalIntervals() != 0 || observation.productiveMinutes() != 0 || observation.calendarMinutes() != 0;
		if (!historyObserved && !observation.inventoryReady()) { return; }
		if (!_captureStarted.compareAndSet(false, true)) { return; }
		final long edgeElapsedNanos = System.nanoTime() - _runtimeStartedNanos;
		try
		{
			final Census after = capture(_objectIds);
			_witness.set(new Witness(observation, after, edgeElapsedNanos, System.nanoTime() - _runtimeStartedNanos, historyObserved));
		}
		catch (Exception | Error failure) { _failure.compareAndSet(null, "BOOTSTRAP_PASSIVE_CENSUS_FAILED:" + failure.getClass().getName() + ":" + failure.getMessage()); }
	}

	void assertBootstrap(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertTrue(_runtimeStartedNanos > 0, "BOOTSTRAP runtime observation did not start.");
		final long deadline = _runtimeStartedNanos + TimeUnit.SECONDS.toNanos(60);
		while (_witness.get() == null && _failure.get() == null && System.nanoTime() < deadline) { Thread.sleep(25L); }
		final Witness witness = _witness.get();
		context.record("bootstrap.lastOriginalWorkerObservation", _latest.get() == null ? "NONE" : _latest.get());
		context.record("bootstrap.passiveFailure", _failure.get() == null ? "NONE" : _failure.get());
		if (witness != null)
		{
			context.record("bootstrap.originalBoundary", witness.observation());
			context.record("bootstrap.boundaryElapsedNanos", witness.elapsedNanos());
			context.record("bootstrap.captureCompletedElapsedNanos", witness.captureCompletedElapsedNanos());
			context.record("bootstrap.historyObservedAtBoundary", witness.historyObserved());
			recordCensus(context, "atBoundary", witness.after());
			context.record("bootstrap.changedProtectedFamilies", _before.protectedRows().entrySet().stream().filter(entry -> !entry.getValue().equals(witness.after().protectedRows().get(entry.getKey()))).map(Map.Entry::getKey).sorted().toList());
		}
		PhantomAssertions.assertEquals(null, _failure.get(), "BOOTSTRAP passive original DB observation failed; no native GREEN claim.");
		PhantomAssertions.assertTrue(witness != null, "BOOTSTRAP_FULL10000_INCOMPLETE_60S: original worker did not publish the complete strict census; no profile/limit/config override attempted.");
		PhantomAssertions.assertTrue(witness.elapsedNanos() >= 0 && witness.elapsedNanos() <= TimeUnit.SECONDS.toNanos(60), "BOOTSTRAP_BOUNDARY_LATE_60S: the actual original worker edge exceeded the unchanged startup bound.");
		PhantomAssertions.assertFalse(witness.historyObserved(), "BOOTSTRAP_HISTORY_BEFORE_FULL_CENSUS: original historical/calendar/model work occurred before the metadata-only phase completed.");
		final MetadataObservation edge = witness.observation();
		PhantomAssertions.assertTrue(edge.inventoryReady() && edge.unloadedProfiles() == 0 && edge.registeredProfiles() == POPULATION && edge.publishedProfiles() == POPULATION, "BOOTSTRAP incomplete/duplicate metadata publication cannot satisfy the full10000 denominator.");
		PhantomAssertions.assertTrue(edge.maximumMetadataBatch() <= _configuredMetadataAllowance, "BOOTSTRAP metadata allowance exceeded the original configured bound.");
		PhantomAssertions.assertTrue(edge.maximumOrdinaryProfiles() <= 4 && edge.maximumOrdinaryIntervals() <= 16, "BOOTSTRAP original ordinary4/16 budgets changed.");
		PhantomAssertions.assertEquals(_before.identityHash(), witness.after().identityHash(), "BOOTSTRAP changed the complete profile/object/population identity census.");
		PhantomAssertions.assertEquals(_before.protectedRows(), witness.after().protectedRows(), "BOOTSTRAP metadata work changed canonical/full inventory/B4/goal/catchup/native-context rows.");
		PhantomAssertions.assertEquals(POPULATION, witness.after().ecologyRows().size(), "BOOTSTRAP metadata publication lacks a real component for one of10000 profiles.");
		for (var entry : _before.ecologyRows().entrySet())
		{
			PhantomAssertions.assertEquals(entry.getValue(), witness.after().ecologyRows().get(entry.getKey()), "BOOTSTRAP changed an existing ecology assignment/version before productive history: " + entry.getKey());
		}
		context.record("bootstrap.acceptance", "full10000 exact identities/metadata components; zero historical/productive/calendar effects before completion; original protected rows unchanged; native farm/cohort acceptance remains REQUIRED");
	}

	private static Census capture(Set<Integer> expectedObjectIds) throws Exception
	{
		PhantomAssertions.assertFalse(Thread.holdsLock(PhantomSystem.class), "BOOTSTRAP SELECT under System monitor is forbidden.");
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setReadOnly(true); connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); connection.setAutoCommit(false);
			try
			{
				try (var statement = connection.prepareStatement("SELECT DATABASE(),CURRENT_USER()"))
				{
					statement.setQueryTimeout(15);
					try (var rows = statement.executeQuery()) { PhantomAssertions.assertTrue(rows.next() && PhantomTestDatabaseGuard.TARGET_DATABASE.equals(rows.getString(1)) && rows.getString(2).startsWith(PhantomTestDatabaseGuard.TARGET_USER + "@"), "BOOTSTRAP requires the exact guarded TEST DB/user."); }
				}
				final var populationCodec = new PhantomPopulationStateCodec();
				final Set<Integer> objectIds = new HashSet<>(); final Set<Long> profiles = new HashSet<>();
				final MessageDigest identities = MessageDigest.getInstance("SHA-256");
				int ready = 0; int retired = 0;
				try (var statement = connection.prepareStatement("SELECT p.profile_id,p.character_object_id,c.component_schema_version,c.payload FROM phantom_profiles p LEFT JOIN phantom_profile_components c ON c.profile_id=p.profile_id AND c.component_type=? ORDER BY p.profile_id"))
				{
					statement.setString(1, PhantomPopulationState.COMPONENT_TYPE); statement.setQueryTimeout(15);
					try (var rows = statement.executeQuery())
					{
						while (rows.next())
						{
							final long profileId = rows.getLong(1); final int objectId = rows.getInt(2);
							PhantomAssertions.assertTrue(!rows.wasNull() && profiles.add(profileId) && objectIds.add(objectId) && profiles.size() <= POPULATION, "BOOTSTRAP duplicate/null/extra imported identity.");
							PhantomAssertions.assertEquals(PhantomPopulationState.SCHEMA_VERSION, rows.getInt(3), "BOOTSTRAP imported population schema changed.");
							final byte[] payload = rows.getBytes(4); final var state = populationCodec.decode(payload);
							if (state.state() == PhantomPopulationState.State.READY) { ready++; } else if (state.state() == PhantomPopulationState.State.RETIRED) { retired++; }
							cell(identities, Long.toString(profileId).getBytes(StandardCharsets.UTF_8)); cell(identities, Integer.toString(objectId).getBytes(StandardCharsets.UTF_8)); cell(identities, payload);
						}
					}
				}
				PhantomAssertions.assertTrue(profiles.size() == POPULATION && ready == 1280 && retired == 8720 && objectIds.equals(expectedObjectIds), "BOOTSTRAP original full10000/1280READY/8720RETIRED census changed.");
				final Map<Long, RowFact> ecology = new LinkedHashMap<>(); final MessageDigest ecologyCensus = MessageDigest.getInstance("SHA-256");
				try (var statement = connection.prepareStatement("SELECT p.profile_id,c.profile_id,c.component_schema_version,c.row_version,c.payload FROM phantom_profiles p LEFT JOIN phantom_profile_components c ON c.profile_id=p.profile_id AND c.component_type='population.ecology' ORDER BY p.profile_id"))
				{
					statement.setQueryTimeout(15);
					try (var rows = statement.executeQuery())
					{
						int accounted = 0;
						while (rows.next())
						{
							final long id = rows.getLong(1); PhantomAssertions.assertTrue(profiles.contains(id) && ++accounted <= POPULATION, "BOOTSTRAP ecology census contains an extra identity.");
							cell(ecologyCensus, Long.toString(id).getBytes(StandardCharsets.UTF_8));
							if (rows.getObject(2) == null) { cell(ecologyCensus, null); continue; }
							final var fact = new RowFact(rows.getInt(3), rows.getLong(4), digest(rows.getBytes(5))); ecology.put(id, fact);
							cell(ecologyCensus, fact.toString().getBytes(StandardCharsets.UTF_8));
						}
						PhantomAssertions.assertEquals(POPULATION, accounted, "BOOTSTRAP ecology existence/absence census is incomplete.");
					}
				}
				final Map<String, QueryFact> protectedRows = new LinkedHashMap<>();
				for (var entry : PROTECTED_QUERIES.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) { protectedRows.put(entry.getKey(), query(connection, entry.getValue())); }
				connection.rollback();
				return new Census(profiles.size(), ready, retired, hex(identities.digest()), Map.copyOf(ecology), hex(ecologyCensus.digest()), Map.copyOf(protectedRows));
			}
			finally { connection.rollback(); }
		}
	}

	private static QueryFact query(Connection connection, String sql) throws Exception
	{
		final MessageDigest digest = MessageDigest.getInstance("SHA-256"); int count = 0;
		final var rowHashes = new ArrayList<String>();
		try (var statement = connection.prepareStatement(sql))
		{
			statement.setQueryTimeout(15);
			try (var rows = statement.executeQuery())
			{
				final var metadata = rows.getMetaData();
				for (int column = 1; column <= metadata.getColumnCount(); column++) { cell(digest, (metadata.getColumnName(column) + ":" + metadata.getColumnType(column)).getBytes(StandardCharsets.UTF_8)); }
				while (rows.next())
				{
					PhantomAssertions.assertTrue(++count <= MAXIMUM_QUERY_ROWS, "BOOTSTRAP protected query exceeds the bounded original fixture census.");
					final MessageDigest row = MessageDigest.getInstance("SHA-256");
					for (int column = 1; column <= metadata.getColumnCount(); column++)
					{
						final byte[] bytes = rows.getBytes(column);
						PhantomAssertions.assertTrue(bytes == null || bytes.length <= 4_194_304, "BOOTSTRAP protected cell exceeds the original Q14 oracle bound.");
						cell(row, bytes);
					}
					rowHashes.add(hex(row.digest()));
				}
			}
		}
		// Original Q14 oracle pattern: exact rows as a sorted multiset, retaining duplicates.
		rowHashes.sort(String::compareTo); cell(digest, Integer.toString(count).getBytes(StandardCharsets.UTF_8));
		for (String rowHash : rowHashes) { cell(digest, rowHash.getBytes(StandardCharsets.UTF_8)); }
		return new QueryFact(count, hex(digest.digest()));
	}

	private static void cell(MessageDigest digest, byte[] bytes) { digest.update(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array()); if (bytes != null) { digest.update(bytes); } }
	private static String digest(byte[] bytes) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
	private static String hex(byte[] bytes) { return HexFormat.of().withUpperCase().formatHex(bytes); }
	private static void recordCensus(PhantomTestContext context, String name, Census census)
	{
		context.record("bootstrap." + name + ".population", "profiles=" + census.profiles() + ",READY=" + census.ready() + ",RETIRED=" + census.retired() + ",identityHash=" + census.identityHash());
		context.record("bootstrap." + name + ".ecology", "present=" + census.ecologyRows().size() + ",absent=" + (POPULATION - census.ecologyRows().size()) + ",exactExistenceSchemaVersionsPayloadHash=" + census.ecologyCensusHash());
		context.record("bootstrap." + name + ".protectedRows", census.protectedRows());
	}

	private record RowFact(int schema, long version, String payloadHash) { }
	private record QueryFact(int rows, String hash) { }
	private record Census(int profiles, int ready, int retired, String identityHash, Map<Long, RowFact> ecologyRows, String ecologyCensusHash, Map<String, QueryFact> protectedRows) { }
	private record Witness(MetadataObservation observation, Census after, long elapsedNanos, long captureCompletedElapsedNanos, boolean historyObserved) { }
}

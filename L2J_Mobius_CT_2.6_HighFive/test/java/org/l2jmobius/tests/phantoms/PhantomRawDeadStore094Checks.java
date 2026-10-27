/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundInventoryHash;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.*;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/** Rollback-only SQL/lease/CAS rehearsal. This is not native gameplay or a process-crash proof. */
final class PhantomRawDeadStore094Checks
{
	private static final List<String> TYPES = List.of(PhantomBackgroundState.COMPONENT_TYPE, PhantomNativeContext.COMPONENT_TYPE, PhantomOwnedStoreIntent.COMPONENT_TYPE);
	private static final String EXACT_DELETE = "DELETE FROM phantom_profile_components WHERE profile_id=? AND component_type=? AND row_version=?";
	private PhantomRawDeadStore094Checks() { }

	static void verify(PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomProfileRepository profiles, List<Long> ids, Path candidate) throws Exception
	{
		final long id = ids.get(3); final int actor = profiles.find(id).orElseThrow().characterObjectId(), otherActor = profiles.find(ids.getFirst()).orElseThrow().characterObjectId();
		final var registry = PhantomIdentityLeaseRegistry.getInstance();
		try (var lease = registry.tryAcquire(actor, OwnerKind.BACKGROUND); var connection = DatabaseFactory.getConnection())
		{
			check(lease != null && World.getInstance().findObject(actor) == null && World.getInstance().getPlayer(actor) == null && !PlayerAutoSaveTaskManager.getInstance().containsObjectId(actor), "ABSENT_LEASE");
			check(PhantomTask094Lane.SCHEMA.equals(connection.getCatalog()), "SCHEMA");
			final String canonicalBefore = canonical(connection, actor), componentsBefore = components(connection, id, false);
			final var prepared = exactMetadata(connection, source, profiles, id, actor);
			if (prepared == null) { return; }
			check(components(connection, id, true).isEmpty(), "ORIGINAL_METADATA_ABSENT");
			connection.setAutoCommit(false);
			try
			{
				final var original = connection.setSavepoint();
				for (String mode : List.of("NO_LEASE", "WRONG_OBJECT", "CLOSED", "FAULT", "SUCCESS"))
				{
					connection.rollback(original);
					for (int index = 0; index < TYPES.size(); index++)
					{
						try (var insert = connection.prepareStatement("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(?,?,?,?)"))
						{
							insert.setLong(1, id); insert.setString(2, TYPES.get(index)); insert.setInt(3, index == 0 ? PhantomBackgroundState.SCHEMA_VERSION : index == 1 ? PhantomNativeContext.SCHEMA_VERSION : PhantomOwnedStoreIntent.SCHEMA_VERSION); insert.setBytes(4, prepared.get(index)); check(insert.executeUpdate() == 1, "INSERT_METADATA");
						}
					}
					final String pending = components(connection, id, true);
					final var checkpoint = connection.setSavepoint();
					// Same public Connection proxy seam as BackgroundSuite's owned-store fault tests.
					final Connection probe = (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (_, method, arguments) ->
					{
						if (method.getName().equals("commit") || method.getName().equals("close")) { return null; }
						if (method.getName().equals("setAutoCommit")) { check(Boolean.FALSE.equals(arguments[0]), "AUTOCOMMIT"); return null; }
						if (method.getName().equals("rollback")) { connection.rollback(checkpoint); return null; }
						if (method.getName().equals("prepareStatement") && arguments[0] instanceof String sql && !sql.startsWith("SELECT ") && !sql.equals(EXACT_DELETE)) { throw new java.sql.SQLException("TASK094_REHEARSAL_WRITER_SCOPE"); }
						try { return method.invoke(connection, arguments); } catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
					});
					final var faultReached = new java.util.concurrent.atomic.AtomicBoolean();
					final var transaction = new PhantomBackgroundTransaction(() -> probe, PhantomBackgroundTransaction.ObjectIdAllocator.production(), point ->
					{
						if (mode.equals("FAULT") && point == PhantomBackgroundTransaction.FaultPoint.BEFORE_OWNED_FINALIZE_COMMIT) { faultReached.set(true); throw new IllegalStateException("TASK094_REHEARSAL_PRECOMMIT"); }
					});
					try (var other = registry.tryAcquire(otherActor, OwnerKind.BACKGROUND))
					{
						check(other != null, "OTHER_OWN_LEASE");
						if (mode.equals("CLOSED")) { other.close(); }
						final var passed = mode.equals("NO_LEASE") ? null : mode.equals("WRONG_OBJECT") || mode.equals("CLOSED") ? other : lease;
						final var result = transaction.reconcileVerifyPending(id, actor, passed);
						final var expected = mode.equals("SUCCESS") ? PhantomBackgroundTransaction.Status.STATE_ABSENT : mode.equals("NO_LEASE") ? PhantomBackgroundTransaction.Status.NATIVE_CONTEXT_REQUIRED : mode.equals("FAULT") ? PhantomBackgroundTransaction.Status.BACKEND_FAILURE : PhantomBackgroundTransaction.Status.STATE_CONFLICT;
						check(result.status() == expected, "RESULT_" + mode);
						check(faultReached.get() == mode.equals("FAULT"), "EXACT_FAULT_REACHED_" + mode);
						check(mode.equals("SUCCESS") ? components(connection, id, true).isEmpty() : pending.equals(components(connection, id, true)), "ATOMIC_METADATA_" + mode);
						check(canonicalBefore.equals(canonical(connection, actor)), "CANONICAL_UNCHANGED_" + mode);
					}
				}
			}
			finally { connection.rollback(); }
			check(canonicalBefore.equals(canonical(connection, actor)) && componentsBefore.equals(components(connection, id, false)), "OUTER_ROLLBACK_EXACT");
			Files.writeString(candidate.resolve("raw-cold-rehearsal.complete"), "rollbackOnly=true\nrealReconcile=true\nnoWrongClosedLeaseRejected=true\nprecommitFaultRestoresThreeRows=true\nexactAbsentRestore=true\ncharactersItemsSkillsAndOriginalMetadataUnchanged=true\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("TASK094_RAW_COLD_REHEARSAL_PASS actor=" + actor + " cases=5 realReconcile=true metadataRows=3 outerRollback=true canonicalWrites=0 processCrashProof=false");
		}
	}

	/** Synthetic pending metadata derives every compared scalar/hash from this exact canonical row. */
	private static List<byte[]> exactMetadata(Connection connection, PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomProfileRepository profiles, long id, int actor) throws Exception
	{
		final List<PhantomBackgroundInventoryHash.CanonicalItem> items = new ArrayList<>(); long load = 0; int slots = 0;
		try (var query = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id"))
		{
			query.setInt(1, actor);
			try (var rows = query.executeQuery())
			{
				while (rows.next())
				{
					final var location = ItemLocation.valueOf(rows.getString(4));
					items.add(new PhantomBackgroundInventoryHash.CanonicalItem(rows.getInt(1), rows.getInt(2), rows.getLong(3), location));
					load = Math.addExact(load, Math.multiplyExact(rows.getLong(3), ItemData.getInstance().getTemplate(rows.getInt(2)).getWeight()));
					if (location == ItemLocation.INVENTORY) { slots++; }
				}
			}
		}
		final var skills = new StringBuilder();
		try (var query = connection.prepareStatement("SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=0 ORDER BY skill_id"))
		{
			query.setInt(1, actor); try (var rows = query.executeQuery()) { while (rows.next()) { skills.append(rows.getInt(1)).append(':').append(rows.getInt(2)).append('\n'); } }
		}
		try (var query = connection.prepareStatement("SELECT level,exp,sp,expBeforeDeath,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,classid,race,vitality_points FROM characters WHERE charId=? AND account_name='task094farm'"))
		{
			query.setInt(1, actor);
			try (var rows = query.executeQuery())
			{
				check(rows.next(), "OWN_CORPSE");
				if (rows.getDouble(7) <= rows.getDouble(8)) { return null; }
				check(rows.getDouble(5) == 0 && rows.getDouble(8) > 0, "RAW_CORPSE");
				final var state = new PhantomBackgroundState(State.DEAD, new Identity(id, actor, 0, rows.getInt(15), rows.getInt(16)), new Progress(rows.getInt(1), rows.getLong(2), rows.getLong(3), rows.getLong(4)), new Vitals(0, rows.getDouble(6), Math.min(rows.getDouble(7), rows.getDouble(8)), rows.getDouble(8), Math.min(rows.getDouble(9), rows.getDouble(10)), rows.getDouble(10)), new Position(0, rows.getInt(11), rows.getInt(12), rows.getInt(13), rows.getInt(14), new PhantomGoalStateStore(profiles).load(id).orElseThrow().goal().selectedAnchor().key()), new CombatFacts(ModelKind.MELEE, 1, 1, 1, 1, 100, 100, 0, 0, 1, 1, 1, 1, 1, 1, 1), Loadout.none(), new InventoryFacts(List.of(), List.of(), PhantomBackgroundInventoryHash.compute(items), load, Math.max(1, load), slots, 160), List.of(), new Clock(1, 0, 0), Receipt.empty(), source.authority().hashes());
				final var raw = new PhantomOwnedStoreIntent.RawDeadVitals(rows.getDouble(7), rows.getDouble(9));
				final var intent = new PhantomOwnedStoreIntent(0, 940094, State.DEAD, "ABSENT", "ABSENT", PhantomBackgroundTransaction.payloadDigest(skills.toString().getBytes(StandardCharsets.US_ASCII)), state, state, raw);
				final byte[] pending = new PhantomBackgroundStateCodec().encode(state.withState(State.VERIFY_PENDING));
				final var context = PhantomNativeContext.pending(state.identity(), rows.getInt(17), PhantomNativeContext.Eligibility.UNKNOWN, new PhantomNativeContext.Capture(rows.getInt(17), PhantomNativeContext.Eligibility.UNKNOWN), 0, pending, intent.materializedAtNanos(), intent.encode());
				check(!rows.next(), "SINGLE_CORPSE");
				return List.of(pending, context.encode(), intent.encode());
			}
		}
	}

	private static String canonical(Connection connection, int actor) throws Exception
	{
		return rows(connection, "SELECT * FROM characters WHERE charId=?", actor) + rows(connection, "SELECT * FROM items WHERE owner_id=? ORDER BY object_id", actor) + rows(connection, "SELECT * FROM character_skills WHERE charId=? ORDER BY class_index,skill_id", actor);
	}
	private static String components(Connection connection, long id, boolean onlyPending) throws Exception
	{
		return rows(connection, "SELECT component_type,component_schema_version,row_version,payload FROM phantom_profile_components WHERE profile_id=?" + (onlyPending ? " AND component_type IN ('" + String.join("','", TYPES) + "')" : "") + " ORDER BY component_type", id);
	}
	private static String rows(Connection connection, String sql, long id) throws Exception
	{
		final var result = new StringBuilder();
		try (var query = connection.prepareStatement(sql))
		{
			query.setLong(1, id); try (var rows = query.executeQuery())
			{
				while (rows.next()) { for (int index = 1; index <= rows.getMetaData().getColumnCount(); index++) { final byte[] bytes = rows.getBytes(index); result.append(bytes == null ? "NULL" : Base64.getEncoder().encodeToString(bytes)).append('|'); } result.append('\n'); }
			}
		}
		return result.toString();
	}
	private static void check(boolean value, String key) { if (!value) { throw new IllegalStateException("TASK094_RAW_REHEARSAL_" + key); } }
}

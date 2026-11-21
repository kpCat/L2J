/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.phantoms.economy.PhantomMerchantBootstrap.Stage;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfilePersistenceException;

/** Exercises only the named-pipe fixture. The operator tool has no fixture-seeding path. */
public final class BartzCraftCompatNativeChecks
{
    public static void main(String[] args) throws Exception
    {
        final var db = BartzCraftCompat.openOptions(Path.of(args[4]), Path.of(args[3]), false);
        final var evidence = BartzCraftCompat.evidence(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        DatabaseFactory.initFromConfig(args[4]);
        try
        {
            try (var connection = DatabaseFactory.getConnection())
            {
                BartzCraftCompat.checkDatabase(connection, db);
                seedFixture(connection, evidence);
            }
            final var first = evidence.targets().getFirst();
            final int character = first.role().characterId();
            final int recipe = first.receipt().recipes().getFirst();
            final var plan = BartzCraftCompat.preflight(db, evidence, true);
            final var completeBefore = componentDigest();
            final var nativeBefore = nativeDigest();
            final String untouched = untouchedComponents(evidence);

            try (var connection = DatabaseFactory.getConnection())
            {
                update(connection, "UPDATE characters SET account_name=? WHERE charId=?", "phm_fixture_wrong", character);
                reject(() -> BartzCraftCompat.preflight(db, evidence, false), "wrong account passed");
                update(connection, "UPDATE characters SET account_name=? WHERE charId=?", first.role().account(), character);
                final int unusedId = 1;
                update(connection, "UPDATE characters SET charId=? WHERE charId=?", unusedId, character);
                reject(() -> BartzCraftCompat.preflight(db, evidence, false), "wrong charId passed");
                update(connection, "UPDATE characters SET charId=? WHERE charId=?", character, unusedId);
                update(connection, "DELETE FROM character_recipebook WHERE charId=? AND id=? AND classIndex=0", character, recipe);
                reject(() -> BartzCraftCompat.preflight(db, evidence, false), "missing recipe passed");
                reject(() -> BartzCraftCompat.apply(db, evidence, plan), "native drift between preflight and CAS passed");
                update(connection, "INSERT INTO character_recipebook(charId,id,classIndex,type) VALUES (?,?,0,1)", character, recipe);
                update(connection, "UPDATE character_offline_trade_items SET price=price+1 WHERE charId=? AND item=?", character, recipe);
                reject(() -> BartzCraftCompat.preflight(db, evidence, false), "different manufacture price passed");
                update(connection, "UPDATE character_offline_trade_items SET price=price-1 WHERE charId=? AND item=?", character, recipe);
                final String adena = " WHERE owner_id=? AND item_id=57 AND loc='INVENTORY' ORDER BY object_id LIMIT 1";
                update(connection, "UPDATE items SET `count`=`count`+1" + adena, character);
                reject(() -> BartzCraftCompat.apply(db, evidence, plan), "unattested adena drift passed");
                update(connection, "UPDATE items SET `count`=`count`-1" + adena, character);
                check(componentDigest().equals(completeBefore), "failed native CAS wrote a component");

                final long profile = evidence.ledger().profile();
                final var role = BartzCraftCompat.component(connection, profile, first.role().component());
                update(connection, "UPDATE phantom_profile_components SET updated_at=updated_at,row_version=row_version+1 WHERE profile_id=? AND component_type=?",
                    profile, role.componentType());
                final var stale = componentDigest();
                reject(() -> BartzCraftCompat.apply(db, evidence, plan), "stale CAS won");
                check(componentDigest().equals(stale), "stale CAS changed persisted rows");
                update(connection, "UPDATE phantom_profile_components SET updated_at=updated_at,row_version=? WHERE profile_id=? AND component_type=?",
                    role.rowVersion(), profile, role.componentType());
                update(connection, "UPDATE phantom_profile_components SET updated_at=updated_at,payload=? WHERE profile_id=? AND component_type=?",
                    new byte[] {1}, profile, role.componentType());
                reject(() -> BartzCraftCompat.apply(db, evidence, plan), "different bytes at the same version won");
                update(connection, "UPDATE phantom_profile_components SET updated_at=updated_at,payload=? WHERE profile_id=? AND component_type=?",
                    role.payload(), profile, role.componentType());

                final var last = BartzCraftCompat.component(connection, profile, evidence.targets().getLast().role().component());
                // Force the final ordered update to fail in MariaDB after the other 39 updates.
                try (var statement = connection.createStatement())
                {
                    statement.execute("ALTER TABLE phantom_profile_components ADD CONSTRAINT fixture_cas_failure CHECK (component_type <> 'merchant.owner.0198' OR row_version <> " + (last.rowVersion() + 1) + ")");
                    try { rejectLastUpdate(() -> BartzCraftCompat.apply(db, evidence, plan)); }
                    finally { statement.execute("ALTER TABLE phantom_profile_components DROP CONSTRAINT fixture_cas_failure"); }
                }
                check(componentDigest().equals(completeBefore), "SQL failure left a partially migrated pair or batch");
            }
            final var gate = new CountDownLatch(1);
            try (var threads = Executors.newFixedThreadPool(2))
            {
                final var left = threads.submit(() -> attempt(gate, db, evidence, plan));
                final var right = threads.submit(() -> attempt(gate, db, evidence, plan));
                gate.countDown();
                check(left.get(20, TimeUnit.SECONDS) != right.get(20, TimeUnit.SECONDS), "parallel CAS did not have exactly one winner");
            }
            final String after = componentDigest();
            check(!after.equals(completeBefore) && BartzCraftCompat.preflight(db, evidence, true).mutations().isEmpty(), "committed pair did not read as NOOP");
            check(after.equals(componentDigest()) && nativeBefore.equals(nativeDigest()) && untouched.equals(untouchedComponents(evidence)),
                "retry or migration changed native grants/state or an out-of-scope component");
            System.out.println("BARTZ_CRAFT_COMPAT_NATIVE_PASS historical=20/20 identity=REFUSE recipe=REFUSE price=REFUSE adena=REFUSE stale=REFUSE bytes=REFUSE rollback=40/40 concurrent=1 noop=20/20 nativeGrantDelta=0");
        }
        finally { DatabaseFactory.close(); }
    }

    private static void seedFixture(Connection connection, BartzCraftCompat.Evidence evidence) throws SQLException
    {
        connection.setAutoCommit(false);
        try
        {
            for (var target : evidence.targets())
            {
                final var archived = target.archivedRole();
                final var role = target.role();
                final var actual = BartzCraftCompat.component(connection, archived.profile(), role.component());
                check(actual.rowVersion() == archived.version() && Arrays.equals(actual.payload(), archived.payload()), "fixture needs pristine archived roles");
                update(connection, "UPDATE phantom_profile_components SET payload=?,row_version=row_version+1 WHERE profile_id=? AND component_type=? AND row_version=?",
                    PhantomMerchantBootstrap.encode(role.withStage(Stage.INCONSISTENT)), actual.profileId(), actual.componentType(), actual.rowVersion());
            }
            connection.commit();
        }
        catch (SQLException | RuntimeException | AssertionError failure) { connection.rollback(); throw failure; }
    }

    private static boolean attempt(CountDownLatch gate, BartzCraftCompat.Database db, BartzCraftCompat.Evidence evidence, BartzCraftCompat.Plan plan) throws Exception
    {
        gate.await();
        try { BartzCraftCompat.apply(db, evidence, plan); return true; }
        catch (java.util.ConcurrentModificationException lost) { return false; }
    }

    private static void update(Connection connection, String sql, Object... args) throws SQLException
    {
        try (var update = connection.prepareStatement(sql))
        {
            for (int i = 0; i < args.length; i++) { update.setObject(i + 1, args[i]); }
            check(update.executeUpdate() == 1, "fixture mutation did not affect exactly one row");
        }
    }

    private static String componentDigest() throws Exception
    {
        return digest(List.of("SELECT * FROM phantom_profile_components ORDER BY profile_id,component_type"));
    }

    private static String untouchedComponents(BartzCraftCompat.Evidence evidence) throws Exception
    {
        final String names = evidence.targets().stream().flatMap(target -> java.util.stream.Stream.of(
            "'" + target.role().component() + "'", "'merchant.craft.qualified.v1." + target.role().ordinal() + "'"))
            .collect(java.util.stream.Collectors.joining(","));
        return digest(List.of("SELECT * FROM phantom_profile_components WHERE profile_id<>" + evidence.ledger().profile()
            + " OR component_type NOT IN (" + names + ") ORDER BY profile_id,component_type"));
    }

    private static String nativeDigest() throws Exception
    {
        return digest(List.of("SELECT * FROM accounts ORDER BY login", "SELECT * FROM characters ORDER BY charId", "SELECT * FROM items ORDER BY object_id",
            "SELECT * FROM character_skills ORDER BY charId,skill_id,class_index", "SELECT * FROM character_recipebook ORDER BY charId,id,classIndex",
            "SELECT * FROM character_offline_trade ORDER BY charId", "SELECT * FROM character_offline_trade_items ORDER BY charId,item,count,price",
            "SELECT * FROM phantom_profiles ORDER BY profile_id"));
    }

    private static String digest(List<String> statements) throws Exception
    {
        final var hash = MessageDigest.getInstance("SHA-256");
        try (var connection = DatabaseFactory.getConnection(); var sql = connection.createStatement())
        {
            for (String statement : statements)
            {
                try (var rows = sql.executeQuery(statement))
                {
                    final int columns = rows.getMetaData().getColumnCount();
                    while (rows.next())
                    {
                        for (int i = 1; i <= columns; i++)
                        {
                            final byte[] value = rows.getBytes(i);
                            hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(value == null ? -1 : value.length).array());
                            if (value != null) { hash.update(value); }
                        }
                    }
                }
                hash.update(statement.getBytes(StandardCharsets.UTF_8));
            }
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    private interface Trial { void run() throws SQLException; }

    private static void reject(Trial action, String failure) throws SQLException
    {
        try { action.run(); }
        catch (IllegalStateException | java.util.ConcurrentModificationException expected) { return; }
        throw new AssertionError(failure);
    }

    private static void rejectLastUpdate(Trial action) throws SQLException
    {
        try { action.run(); }
        catch (PhantomProfilePersistenceException expected)
        {
            if (expected.getCause() instanceof SQLException sql && sql.getErrorCode() == 4025
                && sql.getMessage().contains("fixture_cas_failure")) { return; }
            throw expected;
        }
        throw new AssertionError("Forced last-row SQL failure unexpectedly committed.");
    }

    private static void check(boolean condition, String failure)
    {
        if (!condition) { throw new AssertionError(failure); }
    }
}

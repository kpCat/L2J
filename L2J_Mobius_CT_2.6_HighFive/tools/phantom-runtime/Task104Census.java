/* Copyright (c) 2013 L2jMobius */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;

/** Read-only OWN102 authority, sessions and durable preimage. Never starts server infrastructure. */
public final class Task104Census
{
    public static void main(String[] args) throws Exception
    {
        if (args.length != 2) { throw new IllegalArgumentException("Exact original stage and new private output directory required."); }
        final Path origin = physical(Path.of(args[0]));
        final Path output = physical(Path.of(args[1]));
        final Properties owner = read(origin.resolve("private/owner.properties"));
        if (!"TASK102_POPULATION_SCALE".equals(owner.getProperty("owner")) || !"l2jmobiush5_phantom_test_102".equals(owner.getProperty("schema"))
            || !Files.readString(origin.resolve("private/db.created")).startsWith(owner.getProperty("installationId") + "\n")
            || !hash(origin.resolve("inputs.tsv")).equals(owner.getProperty("inputsSha256"))) { throw new IllegalStateException("OWN104_ORIGINAL_AUTHORITY"); }
        final Path source = physical(Path.of(owner.getProperty("sourceModule")));
        final Path config = source.resolve("dist/game/config/Database.ini");
        if (!hash(config).equals(owner.getProperty("sourceDatabaseSha256"))) { throw new IllegalStateException("OWN104_CREDENTIAL_ORIGIN_DRIFT"); }
        final Properties database = read(config);
        final String url = database.getProperty("URL", "");
        if (!url.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5(?:\\?[^\\r\\n]*)?")) { throw new IllegalArgumentException("OWN104_LOOPBACK_ORIGIN_REQUIRED"); }
        Class.forName(database.getProperty("Driver"));
        try (var connection = DriverManager.getConnection(url.replaceFirst("/l2jmobiush5(?=\\?|$)", "/l2jmobiush5_phantom_test_102"), database.getProperty("Login"), database.getProperty("Password")))
        {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            boolean visible = false;
            try (var query = connection.createStatement(); var rows = query.executeQuery("SHOW GRANTS FOR CURRENT_USER"))
            {
                while (rows.next()) { final String grant = rows.getString(1).toUpperCase(java.util.Locale.ROOT); visible |= grant.startsWith("GRANT ALL PRIVILEGES ") && grant.contains(" ON *.* "); }
            }
            if (!visible) { throw new IllegalStateException("OWN104_FULL_VISIBILITY_REQUIRED"); }
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT installation_id,source_head,inputs_sha256 FROM own102_authority"))
            {
                if (!rows.next() || !owner.getProperty("installationId").equals(rows.getString(1)) || !owner.getProperty("sourceHead").equals(rows.getString(2))
                    || !owner.getProperty("inputsSha256").equals(rows.getString(3)) || rows.next()) { throw new IllegalStateException("OWN104_DURABLE_AUTHORITY_MISMATCH"); }
            }
            long own = 0, foreign = 0;
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT DB,COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID() GROUP BY DB"))
            {
                while (rows.next()) { if (owner.getProperty("schema").equals(rows.getString(1))) { own += rows.getLong(2); } else { foreign += rows.getLong(2); } }
            }
            final var ids = new java.util.ArrayList<String>();
            final var characters = new java.util.HashSet<Integer>();
            final var codec = new PhantomPopulationStateCodec();
            try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT p.profile_id,p.character_object_id,c.account_name,s.payload FROM phantom_profiles p JOIN phantom_profile_components s ON s.profile_id=p.profile_id AND s.component_type='population.state' LEFT JOIN characters c ON c.charId=p.character_object_id ORDER BY p.profile_id"))
            {
                while (rows.next())
                {
                    final var state = codec.decode(rows.getBytes(4));
                    if (state.state() != PhantomPopulationState.State.READY || !characters.add(rows.getInt(2)) || !state.reservedAccount().equals(rows.getString(3)) || state.reservedAccount().startsWith("phm_")) { throw new IllegalStateException("OWN104_ORDINARY_READY_IDENTITY"); }
                    ids.add(rows.getLong(1) + "\t" + rows.getInt(2) + "\t" + state.initializationHash());
                }
            }
            final var expected = Files.readAllLines(origin.resolve("private/receipts-continuation2/ids4.tsv"));
            if (ids.size() != 5000 || expected.size() != 5000 || !new java.util.HashSet<>(ids).equals(new java.util.HashSet<>(expected))) { throw new IllegalStateException("OWN104_EXACT_PRIOR5000_REQUIRED"); }
            Files.write(output.resolve("ids.tsv"), ids.stream().sorted().toList(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            final String membership = " JOIN phantom_profiles p ON p.character_object_id=c.charId JOIN phantom_profile_components membership ON membership.profile_id=p.profile_id AND membership.component_type='population.state' ";
            final var queries = List.of(
                "SELECT c.* FROM characters c" + membership + "ORDER BY c.charId",
                "SELECT i.* FROM items i JOIN characters c ON c.charId=i.owner_id" + membership + "ORDER BY i.object_id",
                "SELECT s.profile_id,s.component_type,s.component_schema_version,s.row_version,HEX(s.payload) FROM phantom_profile_components s JOIN phantom_profile_components membership ON membership.profile_id=s.profile_id AND membership.component_type='population.state' ORDER BY s.profile_id,s.component_type",
                "SELECT r.* FROM character_recipebook r JOIN characters c ON c.charId=r.charId" + membership + "ORDER BY r.charId,r.id,r.classIndex",
                "SELECT * FROM character_offline_trade ORDER BY charId",
                "SELECT * FROM character_offline_trade_items ORDER BY charId,item");
            final var checkpoint = new StringBuilder();
            for (int index = 0; index < queries.size(); index++)
            {
                long count = 0;
                final Path dump = output.resolve("slice" + index + ".private.txt");
                try (var writer = Files.newBufferedWriter(dump, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); var query = connection.createStatement(); var rows = query.executeQuery(queries.get(index)))
                {
                    while (rows.next())
                    {
                        if (++count > 200000) { throw new IllegalStateException("OWN104_CENSUS_BOUND"); }
                        for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++)
                        {
                            final String value = rows.getString(column);
                            writer.write(value == null ? "NULL\n" : value.length() + ":" + value + "\n");
                        }
                    }
                }
                checkpoint.append(index).append('\t').append(count).append('\t').append(hash(dump)).append('\n');
            }
            Files.writeString(output.resolve("durable.checkpoint"), checkpoint, StandardOpenOption.CREATE_NEW);
            Files.writeString(output.resolve("sessions.txt"), "utc=" + java.time.Instant.now() + ";own=" + own + ";foreign=" + foreign + ";fullVisibility=true\n", StandardOpenOption.CREATE_NEW);
            connection.rollback();
            System.out.println("OWN104_CENSUS_PASS READY=5000;distinctCharacters=5000;priorIDs=EXACT;ownSessions=" + own + ";foreignSessions=" + foreign + ";fullVisibility=true;DDL_DML=0;World=NOT_INITIALIZED");
            System.out.print(checkpoint);
        }
        catch (SQLException failure) { throw new IllegalStateException("OWN104_SQL_FAILURE state=" + failure.getSQLState() + ";code=" + failure.getErrorCode()); }
    }

    private static Path physical(Path path) throws Exception
    {
        final Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.equals(absolute.toRealPath())) { throw new IllegalArgumentException("OWN104_PHYSICAL_PATH_REQUIRED"); }
        for (Path part = absolute; part != null; part = part.getParent())
        {
            final var attributes = Files.readAttributes(part, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther()) { throw new IllegalArgumentException("OWN104_REPARSE_FORBIDDEN"); }
        }
        return absolute;
    }
    private static Properties read(Path path) throws Exception { final var result = new Properties(); try (var reader = Files.newBufferedReader(physical(path))) { result.load(reader); } return result; }
    private static String hash(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(physical(path)))); }
}

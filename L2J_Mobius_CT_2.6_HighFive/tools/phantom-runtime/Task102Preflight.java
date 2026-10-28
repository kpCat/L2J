/* Copyright (c) 2013 L2jMobius */
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

/** Metadata-only OWN102 G0, following Task101Preflight; never creates or adopts a schema. */
public final class Task102Preflight
{
    public static void main(String[] args) throws Exception
    {
        if (args.length != 1) { throw new IllegalArgumentException("OWN102 exact source module required; JDBC=0."); }
        final Path module = Path.of(args[0]).toAbsolutePath().normalize();
        if (!module.equals(module.toRealPath()) || !module.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive")) { throw new IllegalArgumentException("OWN102 physical source required; JDBC=0."); }
        final Path file = module.resolve("dist/game/config/Database.ini");
        for (Path part = file; part != null; part = part.getParent())
        {
            if (Files.isSymbolicLink(part) || !part.equals(part.toRealPath())) { throw new IllegalArgumentException("OWN102 linked source forbidden; JDBC=0."); }
        }
        final var database = new Properties();
        try (var reader = Files.newBufferedReader(file)) { database.load(reader); }
        final String origin = database.getProperty("URL", "");
        if (!origin.matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/l2jmobiush5(?:\\?[^\\r\\n]*)?")) { throw new IllegalArgumentException("OWN102 source JDBC origin mismatch; values withheld; JDBC=0."); }
        Class.forName(database.getProperty("Driver"));
        try (var connection = DriverManager.getConnection(origin.replaceFirst("/l2jmobiush5(?=\\?|$)", "/information_schema"), database.getProperty("Login"), database.getProperty("Password")))
        {
            connection.setReadOnly(true);
            if (!"information_schema".equals(connection.getCatalog())) { throw new IllegalStateException("OWN102 metadata catalog mismatch."); }
            boolean select = false, process = false, show = false;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER"))
            {
                while (rows.next())
                {
                    final String grant = rows.getString(1).toUpperCase(java.util.Locale.ROOT);
                    if (!grant.contains(" ON *.* ")) { continue; }
                    final boolean all = grant.startsWith("GRANT ALL PRIVILEGES ");
                    select |= all || grant.matches(".*\\bSELECT\\b.*");
                    process |= all || grant.matches(".*\\bPROCESS\\b.*");
                    show |= all || grant.contains("SHOW DATABASES");
                }
            }
            if (!select || !process || !show) { throw new IllegalStateException("OWN102_INCOMPLETE_METADATA_VISIBILITY;DDL_DML=0"); }
            long schemas, present, sessions;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*),COALESCE(SUM(SCHEMA_NAME='l2jmobiush5_phantom_test_102'),0) FROM information_schema.SCHEMATA"))
            { rows.next(); schemas = rows.getLong(1); present = rows.getLong(2); }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID<>CONNECTION_ID()"))
            { rows.next(); sessions = rows.getLong(1); }
            System.out.println("OWN102_G0 fullVisibility=true;schemas=" + schemas + ";schema102=" + present + ";foreignSessions=" + sessions + ";DDL_DML=0;World=0");
            if (present != 0 || sessions != 0) { throw new IllegalStateException("OWN102_FRESH_ABSENT_QUIESCENT_SCHEMA_REQUIRED;no adopt/reset/drop"); }
        }
        catch (java.sql.SQLException failure)
        {
            throw new IllegalStateException("OWN102_METADATA_SQL_FAILURE state=" + failure.getSQLState() + ";code=" + failure.getErrorCode());
        }
    }
}

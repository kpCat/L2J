import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

/** Final SELECT-only quiescence check for the two already installed OWN071 schemas. */
public final class Final071Connections
{
    public static void main(String[] args) throws Exception
    {
        if (args.length != 1) { throw new IllegalArgumentException("Exact module root required"); }
        final Path module = Path.of(args[0]).toRealPath();
        if (!module.getFileName().toString().equals("L2J_Mobius_CT_2.6_HighFive")) { throw new IllegalArgumentException("High Five module required"); }
        final Path task = module.getParent().resolve(".phantom-local/task071").toRealPath();
        final Properties owner = read(task.resolve("ownership071.properties"));
        if (!"TASK071_LIVING_COMBAT".equals(owner.getProperty("owner")) || !task.equals(Path.of(owner.getProperty("taskRoot")).toRealPath())) { throw new IllegalArgumentException("Own071 authority required"); }
        System.out.println("schema\totherConnections");
        for (String kind : new String[] {"test", "release"})
        {
            final String schema = "l2jmobiush5_phantom_" + kind + "_071";
            final Properties config = read(task.resolve("private/Database." + kind + ".ini"));
            if (!schema.equals(owner.getProperty(kind + "Schema")) || !config.getProperty("URL", "").matches("jdbc:(mysql|mariadb)://127\\.0\\.0\\.1:3308/" + schema + "\\?useSSL=false(?:&allowPublicKeyRetrieval=true)?(?:&serverTimezone=UTC)?(?:&characterEncoding=UTF-8)?")) { throw new IllegalArgumentException("Exact own071 JDBC required"); }
            Class.forName(config.getProperty("Driver"));
            try (var connection = DriverManager.getConnection(config.getProperty("URL"), config.getProperty("Login"), config.getProperty("Password")))
            {
                connection.setReadOnly(true);
                if (!schema.equals(connection.getCatalog())) { throw new IllegalStateException("Catalog differs"); }
                try (var statement = connection.prepareStatement("SELECT task_id,installation_id,schema_version,migration_sha256 FROM economy071_authority WHERE schema_name=?"))
                {
                    statement.setString(1, schema);
                    try (var rows = statement.executeQuery()) { if (!rows.next() || rows.getInt(1) != 71 || !rows.getString(2).equals(owner.getProperty("installationId")) || rows.getInt(3) != 1 || !rows.getString(4).equals("e6c39363c6ca288196f90f0808d062d7e50ca9acace56f2e63e1242b7dd212e3") || rows.next()) { throw new IllegalStateException("Installed own071 authority differs"); } }
                }
                try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
                {
                    statement.setString(1, schema);
                    try (var rows = statement.executeQuery()) { rows.next(); final long count = rows.getLong(1); System.out.println(schema + '\t' + count); if (count != 0) { throw new IllegalStateException("Own071 still has connections"); } }
                }
            }
        }
    }
    private static Properties read(Path path) throws Exception
    {
        if (!path.toAbsolutePath().normalize().equals(path.toRealPath()) || Files.size(path) > 8192) { throw new IllegalArgumentException("Physical bounded config required"); }
        final Properties result = new Properties(); try (var reader = Files.newBufferedReader(path)) { result.load(reader); } return result;
    }
}

import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.LinkedHashMap;

import com.sun.tools.attach.VirtualMachine;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyStateCodec;

/** Read-only078 observations. No fixture, reward, lifecycle, profile or SQL mutation. */
public final class Owner078Probe
{
    private static final String SCHEMA = "l2jmobiush5_phantom_release_078";

    private static Path root(String argument) throws Exception
    {
        final Path path = Path.of(argument).toAbsolutePath().normalize();
        if (!path.equals(path.toRealPath()) || !path.equals(path.getRoot().resolve("PhantomWorld/Playable-078-owner"))) { throw new IllegalArgumentException("OWN078 exact physical root required."); }
        return path;
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length < 2) { throw new IllegalArgumentException("status/quiescent/world and exact root required."); }
        final Path root = root(args[1]);
        if (args[0].equals("world"))
        {
            if (args.length != 4 || !args[2].matches("[1-9][0-9]*") || !args[3].matches("boot[12]")) { throw new IllegalArgumentException("Exact game PID and boot label required."); }
            final VirtualMachine vm = VirtualMachine.attach(args[2]);
            try
            {
                final var props = vm.getSystemProperties();
                final String runtime = props.getProperty("phantom.localplay.runtime", "");
                if (!runtime.matches("[a-f0-9]{64}") || !"GameServer".equals(props.getProperty("phantom.localplay.role")) || !root.resolve("game").equals(Path.of(props.getProperty("user.dir")).toRealPath())) { throw new IllegalStateException("Wrong JVM; no probe loaded."); }
                vm.loadAgent(root.resolve("tools/owner078-probe.jar").toString(), root + "\n" + runtime + "\n" + args[3]);
            }
            finally { vm.detach(); }
            System.out.print(Files.readString(root.resolve("local-play/world078-" + args[3] + ".properties")));
            return;
        }
        if (args.length != 2 || (!args[0].equals("status") && !args[0].equals("quiescent"))) { throw new IllegalArgumentException("Read-only action required."); }
        DatabaseConfig.load(root.resolve("game/config/Database.ini").toString());
        endpoint();
        try (var connection = DriverManager.getConnection(DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_LOGIN, DatabaseConfig.DATABASE_PASSWORD))
        {
            connection.setReadOnly(true);
            if (!SCHEMA.equals(connection.getCatalog())) { throw new IllegalStateException("Catalog mismatch."); }
            System.out.println("databaseServer=" + connection.getMetaData().getDatabaseProductVersion());
            try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB=? AND ID<>CONNECTION_ID()"))
            {
                statement.setString(1, SCHEMA);
                try (var rows = statement.executeQuery())
                {
                    rows.next(); final long count = rows.getLong(1);
                    System.out.println("ownDatabaseConnections=" + count);
                    if (args[0].equals("quiescent") && count != 0) { throw new IllegalStateException("Own DB still in use."); }
                }
            }
            final var queries = new LinkedHashMap<String, String>();
            queries.put("schemas", "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA ORDER BY SCHEMA_NAME");
            queries.put("identities", "SELECT charId,account_name,char_name,base_class,accesslevel FROM characters ORDER BY charId");
            queries.put("profiles", "SELECT profile_id,character_object_id FROM phantom_profiles ORDER BY profile_id");
            queries.put("components", "SELECT profile_id,component_type,HEX(payload) FROM phantom_profile_components ORDER BY profile_id,component_type");
            queries.put("items", "SELECT owner_id,object_id,item_id,count,enchant_level,loc,loc_data FROM items ORDER BY object_id");
            queries.put("progress", "SELECT charId,level,exp,sp,curHp,x,y,z FROM characters ORDER BY charId");
            queries.put("registration", "SELECT server_id,hexid FROM gameservers ORDER BY server_id");
            queries.put("gmCharacters", "SELECT charId FROM characters WHERE accesslevel>0");
            queries.put("gmAccounts", "SELECT login FROM accounts WHERE accessLevel>0");
            queries.put("disabledAccounts", "SELECT login FROM accounts WHERE accessLevel<0");
            for (var query : queries.entrySet())
            {
                final var digest = MessageDigest.getInstance("SHA-256"); int count = 0;
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(query.getValue()))
                {
                    while (rows.next())
                    {
                        for (int col = 1; col <= rows.getMetaData().getColumnCount(); col++)
                        {
                            final String value = String.valueOf(rows.getObject(col));
                            digest.update((value.length() + ":" + value + "|").getBytes(StandardCharsets.UTF_8));
                        }
                        digest.update((byte)'\n'); count++;
                    }
                }
                System.out.println(query.getKey() + "=" + count + "," + HexFormat.of().formatHex(digest.digest()));
            }
            if (args[0].equals("quiescent"))
            {
                final var codec = new PhantomPopulationEcologyStateCodec();
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT profile_id,payload FROM phantom_profile_components WHERE component_type='population.ecology' ORDER BY profile_id"))
                {
                    while (rows.next())
                    {
                        final var state = codec.decode(rows.getBytes(2));
                        System.out.println("cursor=" + rows.getLong(1) + "," + state.calendarCursorEpochMinute() + "," + state.assignmentOrdinal() + "," + state.ecologyGeneration());
                    }
                }
            }
        }
    }

    private static void endpoint()
    {
        if (!DatabaseConfig.DATABASE_URL.matches("jdbc:mysql://127\\.0\\.0\\.1:3308/" + SCHEMA + "(?:\\?.*)?")) { throw new IllegalStateException("Wrong endpoint."); }
    }

    public static void agentmain(String argument, Instrumentation instrumentation) throws Exception
    {
        final String[] fields = argument.split("\n", -1);
        if (fields.length != 3 || !fields[1].equals(System.getProperty("phantom.localplay.runtime")) || !"GameServer".equals(System.getProperty("phantom.localplay.role")) || !fields[2].matches("boot[12]")) { throw new IllegalArgumentException("OWN078 identity mismatch."); }
        final Path root = root(fields[0]); endpoint();
        if (!root.resolve("game").equals(ServerConfig.DATAPACK_ROOT.toPath().toRealPath())) { throw new IllegalStateException("Wrong datapack."); }
        final var players = World.getInstance().getPlayers();
        final long offline = players.stream().filter(p -> p.isInOfflineMode()).count();
        final String result = "pid=" + ProcessHandle.current().pid() + "\nworld=" + players.size() + "\noffline=" + offline + "\nnonOffline=" + (players.size() - offline) + "\ngm=" + players.stream().filter(p -> p.isGM()).count() + "\n";
        Files.writeString(root.resolve("local-play/world078-" + fields[2] + ".properties"), result, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}

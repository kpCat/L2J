import java.lang.instrument.Instrumentation;
import java.nio.file.Path;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.gameserver.Shutdown;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.loginserver.LoginServer;
import org.l2jmobius.loginserver.config.LoginConfig;

/** TASK043 test tooling: no startup instrumentation; invokes the stock owned shutdown. */
public final class RuntimeShutdownAgent
{
	public static void agentmain(String argument, Instrumentation instrumentation) throws Exception
	{
		final String[] fields = argument.split("\n", -1);
		if ((fields.length != 4) || !fields[0].equals(System.getProperty("phantom.localplay.runtime")) || !fields[1].equals(System.getProperty("phantom.localplay.role")))
		{
			throw new IllegalArgumentException("Owned runtime identity mismatch.");
		}
		final Path root = Path.of(fields[2]).toRealPath();
		final boolean game = "GameServer".equals(fields[1]);
		if (!game && !"LoginServer".equals(fields[1]))
		{
			throw new IllegalArgumentException("Unknown role.");
		}
		final Path actual = (game ? ServerConfig.DATAPACK_ROOT : LoginConfig.DATAPACK_ROOT).toPath().toRealPath();
		if (!root.resolve(game ? "game" : "login").toRealPath().equals(actual) || !fields[3].matches("l2jmobiush5_phantom_test_043[ab]")
			|| !DatabaseConfig.DATABASE_URL.matches("jdbc:(?:mysql|mariadb)://(?:127\\.0\\.0\\.1|localhost):[0-9]+/" + fields[3] + "(?:\\?.*)?"))
		{
			throw new IllegalStateException("Datapack/database mismatch; shutdown not requested.");
		}
		if (game)
		{
			Shutdown.getInstance().startShutdown(null, 0, false);
		}
		else
		{
			new Thread(() -> LoginServer.getInstance().shutdown(false), "operator-stock-login-shutdown").start();
		}
	}
}

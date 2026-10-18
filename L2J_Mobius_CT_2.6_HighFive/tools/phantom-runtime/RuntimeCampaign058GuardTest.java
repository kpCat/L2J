import java.nio.file.Path;
import java.util.Properties;

/** Pure preflight contracts; never opens JDBC or attaches to a process. */
public final class RuntimeCampaign058GuardTest
{
	public static void main(String[] args)
	{
		final Properties owner = new Properties();
		owner.setProperty("version", "1"); owner.setProperty("owner", "TASK058_CAMPAIGN"); owner.setProperty("role", "CUMULATIVE_INTEGRATION");
		owner.setProperty("schema", RuntimeCampaign058Guard.CUMULATIVE); owner.setProperty("runtimeRoot", RuntimeCampaign058Guard.CUMULATIVE_ROOT.toString());
		owner.setProperty("clientLoginPort", "2138"); owner.setProperty("gamePort", "7838"); owner.setProperty("controlPort", "9138");
		RuntimeCampaign058Guard.validateIdentity(RuntimeCampaign058Guard.CUMULATIVE_ROOT, RuntimeCampaign058Guard.CUMULATIVE, owner);
		int count = 1;
		for (String variant : new String[] { "root", "schema", "role", "owner", "port" })
		{
			final Properties changed = new Properties(); changed.putAll(owner);
			Path root = RuntimeCampaign058Guard.CUMULATIVE_ROOT; String schema = RuntimeCampaign058Guard.CUMULATIVE;
			switch (variant)
			{
				case "root" -> root = Path.of("D:/PhantomWorld/M1-057-candidate");
				case "schema" -> schema = "l2jmobiush5_phantom_release_057a";
				case "role" -> changed.setProperty("role", "MUTABLE_FIXTURE");
				case "owner" -> changed.setProperty("owner", "TASK057");
				case "port" -> changed.setProperty("gamePort", "7792");
			}
			boolean rejected = false;
			try { RuntimeCampaign058Guard.validateIdentity(root, schema, changed); } catch (IllegalArgumentException expected) { rejected = true; }
			if (!rejected) { throw new AssertionError("TASK058 ownership negative failed: " + variant); }
			count++;
		}
		owner.setProperty("role", "RELEASE_CANDIDATE"); owner.setProperty("schema", RuntimeCampaign058Guard.RELEASE); owner.setProperty("runtimeRoot", RuntimeCampaign058Guard.RELEASE_ROOT.toString());
		RuntimeCampaign058Guard.validateIdentity(RuntimeCampaign058Guard.RELEASE_ROOT, RuntimeCampaign058Guard.RELEASE, owner);
		System.out.println("TASK058_JAVA_OWNERSHIP_PASS=" + (++count) + "; DB_WRITES=0; PROCESS_ACTIONS=0");
		int count059 = 0;
		for (String login : new String[] {"2106", "2139"})
		{
			final Path root059 = Path.of("D:/PhantomWorld/M3-M7-059-candidate");
			final String schema059 = "l2jmobiush5_phantom_release_059a";
			final Properties next = new Properties(); next.putAll(owner);
			next.setProperty("owner", "TASK059_CAMPAIGN"); next.setProperty("schema", schema059); next.setProperty("runtimeRoot", root059.toString());
			next.setProperty("layout", "LOGIN" + login); next.setProperty("clientLoginPort", login); next.setProperty("gamePort", "7839"); next.setProperty("controlPort", "9139");
			RuntimeCampaign058Guard.validateIdentity(root059, schema059, next); count059++;
			for (String variant : new String[] {"root", "schema", "role", "owner", "layout", "login", "game", "control"})
			{
				final Properties changed = new Properties(); changed.putAll(next); Path root = root059; String schema = schema059;
				switch (variant)
				{
					case "root" -> root = RuntimeCampaign058Guard.RELEASE_ROOT;
					case "schema" -> schema = RuntimeCampaign058Guard.RELEASE;
					case "role" -> changed.setProperty("role", "CUMULATIVE_INTEGRATION");
					case "owner" -> changed.setProperty("owner", "TASK058_CAMPAIGN");
					case "layout" -> changed.setProperty("layout", "LOGIN2138");
					case "login" -> changed.setProperty("clientLoginPort", login.equals("2106") ? "2139" : "2106");
					case "game" -> changed.setProperty("gamePort", "7838");
					case "control" -> changed.setProperty("controlPort", "9138");
				}
				boolean rejected = false;
				try { RuntimeCampaign058Guard.validateIdentity(root, schema, changed); } catch (IllegalArgumentException expected) { rejected = true; }
				if (!rejected) { throw new AssertionError("TASK059 layout negative failed: " + variant + '/' + login); }
				count059++;
			}
		}
		System.out.println("TASK059_JAVA_OWNERSHIP_PASS=" + count059 + "; DB_WRITES=0; PROCESS_ACTIONS=0");
		int count060 = 0;
		for (String login : new String[] {"2106", "2140"})
		{
			final Path root060 = Path.of("D:/PhantomWorld/Living-060-candidate");
			final String schema060 = "l2jmobiush5_phantom_release_060a";
			final Properties next = new Properties(); next.putAll(owner);
			next.setProperty("owner", "TASK060_CAMPAIGN"); next.setProperty("schema", schema060); next.setProperty("runtimeRoot", root060.toString());
			next.setProperty("layout", "LOGIN" + login); next.setProperty("clientLoginPort", login); next.setProperty("gamePort", "7840"); next.setProperty("controlPort", "9140");
			RuntimeCampaign058Guard.validateIdentity(root060, schema060, next); count060++;
			for (String variant : new String[] {"root", "schema", "role", "owner", "layout", "login", "game", "control"})
			{
				final Properties changed = new Properties(); changed.putAll(next); Path root = root060; String schema = schema060;
				switch (variant)
				{
					case "root" -> root = RuntimeCampaign058Guard.RELEASE_ROOT;
					case "schema" -> schema = RuntimeCampaign058Guard.RELEASE;
					case "role" -> changed.setProperty("role", "CUMULATIVE_INTEGRATION");
					case "owner" -> changed.setProperty("owner", "TASK058_CAMPAIGN");
					case "layout" -> changed.setProperty("layout", "LOGIN2138");
					case "login" -> changed.setProperty("clientLoginPort", login.equals("2106") ? "2140" : "2106");
					case "game" -> changed.setProperty("gamePort", "7838");
					case "control" -> changed.setProperty("controlPort", "9138");
				}
				boolean rejected = false;
				try { RuntimeCampaign058Guard.validateIdentity(root, schema, changed); } catch (IllegalArgumentException expected) { rejected = true; }
				if (!rejected) { throw new AssertionError("TASK060 layout negative failed: " + variant + '/' + login); }
				count060++;
			}
		}
		System.out.println("TASK060_JAVA_OWNERSHIP_PASS=" + count060 + "; DB_WRITES=0; PROCESS_ACTIONS=0");
	}
}

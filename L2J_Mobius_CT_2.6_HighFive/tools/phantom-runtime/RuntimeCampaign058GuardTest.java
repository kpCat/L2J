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
	}
}

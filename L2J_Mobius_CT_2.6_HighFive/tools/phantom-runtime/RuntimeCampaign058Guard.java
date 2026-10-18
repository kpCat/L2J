import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Exact TASK058 runtime binding; no database connection or process action. */
public final class RuntimeCampaign058Guard
{
	static final String CUMULATIVE = "l2jmobiush5_phantom_test_058a";
	static final String RELEASE = "l2jmobiush5_phantom_release_058a";
	static final Path CUMULATIVE_ROOT = Path.of("D:/Codex/worktrees/phantom-dev/L2J_Mobius/L2J_Mobius_CT_2.6_HighFive/.phantom-local/contract058a/runtime");
	static final Path RELEASE_ROOT = Path.of("D:/PhantomWorld/M2-M7-058-candidate");
	static final String RELEASE059 = "l2jmobiush5_phantom_release_059a";
	static final Path RELEASE059_ROOT = Path.of("D:/PhantomWorld/M3-M7-059-candidate");

	static final String RELEASE060 = "l2jmobiush5_phantom_release_060a";
	static final Path RELEASE060_ROOT = Path.of("D:/PhantomWorld/Living-060-candidate");

	private RuntimeCampaign058Guard() { }

	public static void validate(Path root, String schema) throws Exception
	{
		if (!CUMULATIVE.equals(schema) && !RELEASE.equals(schema) && !RELEASE059.equals(schema) && !RELEASE060.equals(schema)) { return; }
		final Properties owner = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve(RELEASE060.equals(schema) ? "ownership060.properties" : RELEASE059.equals(schema) ? "ownership059.properties" : "ownership058.properties"), StandardCharsets.UTF_8)) { owner.load(reader); }
		validateIdentity(root.toRealPath(), schema, owner);
	}

	static void validateIdentity(Path root, String schema, Properties owner)
	{
		if (RELEASE060.equals(schema) || "TASK060_CAMPAIGN".equals(owner.getProperty("owner"))) { validate060(root, schema, owner); return; }
		if (RELEASE059.equals(schema) || "TASK059_CAMPAIGN".equals(owner.getProperty("owner"))) { validate059(root, schema, owner); return; }
		final boolean cumulative = CUMULATIVE.equals(schema);
		final Path expected = cumulative ? CUMULATIVE_ROOT : RELEASE_ROOT;
		final String role = cumulative ? "CUMULATIVE_INTEGRATION" : "RELEASE_CANDIDATE";
		if ((!cumulative && !RELEASE.equals(schema)) || !root.toAbsolutePath().normalize().equals(expected) || !"1".equals(owner.getProperty("version")) || !"TASK058_CAMPAIGN".equals(owner.getProperty("owner")) || !role.equals(owner.getProperty("role")) || !schema.equals(owner.getProperty("schema")) || !expected.equals(Path.of(owner.getProperty("runtimeRoot", "")).toAbsolutePath().normalize()) || !"2138".equals(owner.getProperty("clientLoginPort")) || !"7838".equals(owner.getProperty("gamePort")) || !"9138".equals(owner.getProperty("controlPort")))
		{
			throw new IllegalArgumentException("TASK058 runtime root/schema/owner/role/ports mismatch.");
		}
	}

	private static void validate059(Path root, String schema, Properties owner)
	{
		final String login = switch (owner.getProperty("layout", "")) { case "LOGIN2106" -> "2106"; case "LOGIN2139" -> "2139"; default -> throw new IllegalArgumentException("Unknown TASK059 exact layout."); };
		if (!RELEASE059.equals(schema) || !RELEASE059_ROOT.equals(root.toAbsolutePath().normalize()) || !"1".equals(owner.getProperty("version")) || !"TASK059_CAMPAIGN".equals(owner.getProperty("owner")) || !"RELEASE_CANDIDATE".equals(owner.getProperty("role")) || !schema.equals(owner.getProperty("schema")) || !RELEASE059_ROOT.equals(Path.of(owner.getProperty("runtimeRoot", "")).toAbsolutePath().normalize()) || !login.equals(owner.getProperty("clientLoginPort")) || !"7839".equals(owner.getProperty("gamePort")) || !"9139".equals(owner.getProperty("controlPort")))
		{
			throw new IllegalArgumentException("TASK059 runtime root/schema/owner/role/layout/ports mismatch.");
		}
	}
	private static void validate060(Path root, String schema, Properties owner)
	{
		final String login = switch (owner.getProperty("layout", "")) { case "LOGIN2106" -> "2106"; case "LOGIN2140" -> "2140"; default -> throw new IllegalArgumentException("Unknown TASK060 exact layout."); };
		if (!RELEASE060.equals(schema) || !RELEASE060_ROOT.equals(root.toAbsolutePath().normalize()) || !"1".equals(owner.getProperty("version")) || !"TASK060_CAMPAIGN".equals(owner.getProperty("owner")) || !"RELEASE_CANDIDATE".equals(owner.getProperty("role")) || !schema.equals(owner.getProperty("schema")) || !RELEASE060_ROOT.equals(Path.of(owner.getProperty("runtimeRoot", "")).toAbsolutePath().normalize()) || !login.equals(owner.getProperty("clientLoginPort")) || !"7840".equals(owner.getProperty("gamePort")) || !"9140".equals(owner.getProperty("controlPort")))
		{
			throw new IllegalArgumentException("TASK060 runtime root/schema/owner/role/layout/ports mismatch.");
		}
	}
}
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

	private RuntimeCampaign058Guard() { }

	public static void validate(Path root, String schema) throws Exception
	{
		if (!CUMULATIVE.equals(schema) && !RELEASE.equals(schema)) { return; }
		final Properties owner = new Properties();
		try (var reader = Files.newBufferedReader(root.resolve("ownership058.properties"), StandardCharsets.UTF_8)) { owner.load(reader); }
		validateIdentity(root.toRealPath(), schema, owner);
	}

	static void validateIdentity(Path root, String schema, Properties owner)
	{
		final boolean cumulative = CUMULATIVE.equals(schema);
		final Path expected = cumulative ? CUMULATIVE_ROOT : RELEASE_ROOT;
		final String role = cumulative ? "CUMULATIVE_INTEGRATION" : "RELEASE_CANDIDATE";
		if ((!cumulative && !RELEASE.equals(schema)) || !root.toAbsolutePath().normalize().equals(expected) || !"1".equals(owner.getProperty("version")) || !"TASK058_CAMPAIGN".equals(owner.getProperty("owner")) || !role.equals(owner.getProperty("role")) || !schema.equals(owner.getProperty("schema")) || !expected.equals(Path.of(owner.getProperty("runtimeRoot", "")).toAbsolutePath().normalize()) || !"2138".equals(owner.getProperty("clientLoginPort")) || !"7838".equals(owner.getProperty("gamePort")) || !"9138".equals(owner.getProperty("controlPort")))
		{
			throw new IllegalArgumentException("TASK058 runtime root/schema/owner/role/ports mismatch.");
		}
	}
}

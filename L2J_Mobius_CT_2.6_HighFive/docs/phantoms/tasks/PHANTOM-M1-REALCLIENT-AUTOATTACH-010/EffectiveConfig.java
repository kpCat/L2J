import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig;

/** Task-local witness using the actual runtime JAR and config loader. */
public class EffectiveConfig
{
	public static void main(String[] args)
	{
		PhantomPlayersConfig.load();
		LocalPlayPilotConfig.load();
		var s = PhantomPlayersConfig.settings();
		System.out.println("PHANTOM_CONFIG_ENABLED=" + s.enabled() + " target=" + s.populationTarget() + " active=" + s.populationActiveTarget() + " cap=" + s.maxMaterializedPhantoms() + " scheduled=" + s.maxScheduledPhantomProfiles());
		if (!s.enabled() || (s.populationTarget() != 1280) || (s.populationActiveTarget() != 8) || (s.maxMaterializedPhantoms() != 8) || (s.maxScheduledPhantomProfiles() != 10000) || !LocalPlayPilotConfig.isEnabled() || !LocalPlayPilotConfig.isAutoAttachEnabled() || !LocalPlayPilotConfig.isAutoAttachCharacter("TestAdmin") || LocalPlayPilotConfig.isAutoAttachCharacter("Unlisted") || LocalPlayPilotConfig.isSyntheticEnabled())
		{
			throw new IllegalStateException("Task010 effective config mismatch.");
		}
	}
}

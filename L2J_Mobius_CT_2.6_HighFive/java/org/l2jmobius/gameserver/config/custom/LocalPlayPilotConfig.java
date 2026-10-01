package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.commons.util.ConfigReader;

/** Local-only single-client playtest switch. Missing or malformed means OFF. */
public final class LocalPlayPilotConfig
{
	private static final String CONFIG_FILE = "./config/Custom/LocalPlayPilot.ini";
	private static volatile boolean _enabled;
	private static volatile boolean _syntheticEnabled;
	private static int _syntheticObjectId;
	private static String _syntheticName = "";

	private LocalPlayPilotConfig()
	{
	}

	public static void load()
	{
		_enabled = false;
		_syntheticEnabled = false;
		final Path path = Path.of(CONFIG_FILE);
		if (!Files.isRegularFile(path))
		{
			return;
		}
		final ConfigReader reader = new ConfigReader(CONFIG_FILE);
		final String value = reader.getValue("EnableLocalPlayPilot");
		_enabled = "True".equalsIgnoreCase(value);
		try
		{
			_syntheticObjectId = Integer.parseInt(reader.getValue("LocalPlaySyntheticCharacterObjectId"));
			_syntheticName = reader.getValue("LocalPlaySyntheticCharacterName");
			_syntheticEnabled = "True".equalsIgnoreCase(reader.getValue("EnableLocalPlaySyntheticHuman")) && (_syntheticObjectId > 0) && (_syntheticName != null) && !_syntheticName.isBlank();
		}
		catch (RuntimeException exception) { _syntheticEnabled = false; }
	}

	public static boolean isEnabled()
	{
		return _enabled;
	}

	public static boolean isSyntheticEnabled() { return _syntheticEnabled; }
	public static int syntheticObjectId() { return _syntheticObjectId; }
	public static String syntheticName() { return _syntheticName; }
}

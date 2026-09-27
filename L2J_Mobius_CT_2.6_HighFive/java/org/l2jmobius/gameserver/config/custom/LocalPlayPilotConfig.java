package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.commons.util.ConfigReader;

/** Local-only single-client playtest switch. Missing or malformed means OFF. */
public final class LocalPlayPilotConfig
{
	private static final String CONFIG_FILE = "./config/Custom/LocalPlayPilot.ini";
	private static volatile boolean _enabled;

	private LocalPlayPilotConfig()
	{
	}

	public static void load()
	{
		_enabled = false;
		final Path path = Path.of(CONFIG_FILE);
		if (!Files.isRegularFile(path))
		{
			return;
		}
		final String value = new ConfigReader(CONFIG_FILE).getValue("EnableLocalPlayPilot");
		_enabled = "True".equalsIgnoreCase(value);
	}

	public static boolean isEnabled()
	{
		return _enabled;
	}
}

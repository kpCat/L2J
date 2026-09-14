/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.l2jmobius.commons.util.ConfigReader;

/**
 * Loads fail-closed progression QoL switches.
 */
public final class PersonalProgressionQoLConfig
{
	private static final Logger LOGGER = Logger.getLogger(PersonalProgressionQoLConfig.class.getName());
	public static final String PERSONAL_PROGRESSION_QOL_CONFIG_FILE = "./config/Custom/PersonalProgressionQoL.ini";

	private static volatile Settings _settings = Settings.disabled("Configuration has not been loaded.");

	private PersonalProgressionQoLConfig()
	{
	}

	public static void load()
	{
		_settings = read(Path.of(PERSONAL_PROGRESSION_QOL_CONFIG_FILE));
		if (!_settings.valid())
		{
			LOGGER.warning("Personal progression QoL is disabled: " + _settings.diagnostic());
		}
	}

	public static Settings read(Path path)
	{
		if ((path == null) || !Files.isRegularFile(path))
		{
			return Settings.disabled("Configuration file is absent.");
		}

		try
		{
			final ConfigReader config = new ConfigReader(path.toString());
			final Boolean questOverLevelReliefEnabled = strictBoolean(config.getValue("EnablePersonalQuestOverLevelRelief"));
			final Boolean autoNoblesseEnabled = strictBoolean(config.getValue("EnableServerWideAutoNoblesse"));
			if ((questOverLevelReliefEnabled == null) || (autoNoblesseEnabled == null))
			{
				return Settings.invalid("All personal progression QoL switches must be True or False.");
			}
			return new Settings(questOverLevelReliefEnabled, autoNoblesseEnabled, true, "Configuration accepted.");
		}
		catch (RuntimeException e)
		{
			return Settings.invalid("Configuration could not be parsed: " + e.getClass().getSimpleName() + ".");
		}
	}

	public static Settings settings()
	{
		return _settings;
	}

	private static Boolean strictBoolean(String value)
	{
		if (value == null)
		{
			return null;
		}
		if (value.trim().equalsIgnoreCase("true"))
		{
			return true;
		}
		if (value.trim().equalsIgnoreCase("false"))
		{
			return false;
		}
		return null;
	}

	public record Settings(boolean questOverLevelReliefEnabled, boolean autoNoblesseEnabled, boolean valid, String diagnostic)
	{
		public Settings
		{
			if (!valid)
			{
				questOverLevelReliefEnabled = false;
				autoNoblesseEnabled = false;
			}
		}

		public static Settings disabled(String diagnostic)
		{
			return new Settings(false, false, true, diagnostic);
		}

		public static Settings invalid(String diagnostic)
		{
			return new Settings(false, false, false, diagnostic);
		}
	}
}

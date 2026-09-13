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
 * Loads the fail-closed Personal/Premium QoL switches.
 */
public final class PersonalPremiumQoLConfig
{
	private static final Logger LOGGER = Logger.getLogger(PersonalPremiumQoLConfig.class.getName());
	public static final String PERSONAL_PREMIUM_QOL_CONFIG_FILE = "./config/Custom/PersonalPremiumQoL.ini";
	public static final String DEFAULT_LEVEL_GAP_ITEMS_FILE = "data/custom/personal-qol/level-gap-items.xml";

	private static volatile Settings _settings = Settings.disabled("Configuration has not been loaded.");

	private PersonalPremiumQoLConfig()
	{
	}

	public static void load()
	{
		_settings = read(Path.of(PERSONAL_PREMIUM_QOL_CONFIG_FILE));
		if (!_settings.valid())
		{
			LOGGER.warning("Personal/Premium QoL is disabled: " + _settings.diagnostic());
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
			final Boolean enabled = strictBoolean(config.getValue("EnablePersonalPremiumQoL"));
			if (enabled == null)
			{
				return Settings.invalid("EnablePersonalPremiumQoL must be True or False.");
			}
			if (!enabled)
			{
				return Settings.disabled("Disabled by configuration.");
			}

			final Boolean shopEnabled = strictBoolean(config.getValue("EnablePersonalPremiumShop"));
			if (shopEnabled == null)
			{
				return Settings.invalid("EnablePersonalPremiumShop must be True or False.");
			}

			final String catalogPath = strictRelativePath(config.getValue("LevelGapItemsFile"));
			if (catalogPath == null)
			{
				return Settings.invalid("LevelGapItemsFile must be a bounded relative path without '..'.");
			}
			return new Settings(true, shopEnabled, catalogPath, true, "Configuration accepted.");
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

	private static String strictRelativePath(String value)
	{
		final String candidate = value == null ? DEFAULT_LEVEL_GAP_ITEMS_FILE : value.trim().replace('\\', '/');
		if (candidate.isEmpty() || (candidate.length() > 160))
		{
			return null;
		}
		final Path path;
		try
		{
			path = Path.of(candidate);
		}
		catch (RuntimeException e)
		{
			return null;
		}
		if (path.isAbsolute() || candidate.startsWith("/") || candidate.matches("^[A-Za-z]:.*"))
		{
			return null;
		}
		for (Path part : path)
		{
			if ("..".equals(part.toString()))
			{
				return null;
			}
		}
		return path.normalize().toString().replace('\\', '/');
	}

	public record Settings(boolean enabled, boolean shopEnabled, String catalogPath, boolean valid, String diagnostic)
	{
		public Settings
		{
			if (!enabled)
			{
				shopEnabled = false;
				catalogPath = DEFAULT_LEVEL_GAP_ITEMS_FILE;
			}
		}

		public static Settings disabled(String diagnostic)
		{
			return new Settings(false, false, DEFAULT_LEVEL_GAP_ITEMS_FILE, true, diagnostic);
		}

		public static Settings invalid(String diagnostic)
		{
			return new Settings(false, false, DEFAULT_LEVEL_GAP_ITEMS_FILE, false, diagnostic);
		}
	}
}

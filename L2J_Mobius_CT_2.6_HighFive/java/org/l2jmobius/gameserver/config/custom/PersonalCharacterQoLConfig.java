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
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

import org.l2jmobius.commons.util.ConfigReader;

/**
 * Loads the fail-closed per-character Personal QoL access policy.
 */
public final class PersonalCharacterQoLConfig
{
	private static final Logger LOGGER = Logger.getLogger(PersonalCharacterQoLConfig.class.getName());
	public static final String PERSONAL_CHARACTER_QOL_CONFIG_FILE = "./config/Custom/PersonalCharacterQoL.ini";
	private static final int MAX_LIST_LENGTH = 4096;
	private static final int MAX_LIST_ENTRIES = 256;
	private static final int MAX_ACCOUNT_LENGTH = 45;

	private static volatile Settings _settings = Settings.disabled("Configuration has not been loaded.");

	private PersonalCharacterQoLConfig()
	{
	}

	public static void load()
	{
		_settings = read(Path.of(PERSONAL_CHARACTER_QOL_CONFIG_FILE));
		if (!_settings.valid())
		{
			LOGGER.warning("Personal Character QoL is disabled: " + _settings.diagnostic());
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
			final Boolean enabled = strictBoolean(config.getValue("EnablePersonalCharacterQoL"));
			final Boolean crossClassSkillsEnabled = strictBoolean(config.getValue("EnablePersonalCrossClassSkills"));
			final Boolean crystallizationEnabled = strictBoolean(config.getValue("EnablePersonalCrystallization"));
			if ((enabled == null) || (crossClassSkillsEnabled == null) || (crystallizationEnabled == null))
			{
				return Settings.invalid("All Personal Character QoL switches must be True or False.");
			}

			final Set<Integer> characterIds = parseCharacterIds(config.getValue("AllowedCharacterIds"));
			final Set<String> accounts = parseAccounts(config.getValue("AllowedAccounts"));
			if (!enabled)
			{
				return Settings.disabled("Disabled by configuration.");
			}
			return new Settings(true, crossClassSkillsEnabled, crystallizationEnabled, characterIds, accounts, true, "Configuration accepted.");
		}
		catch (IllegalArgumentException e)
		{
			return Settings.invalid(e.getMessage());
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

	private static Set<Integer> parseCharacterIds(String value)
	{
		final Set<Integer> result = new HashSet<>();
		for (String token : splitList(value, "AllowedCharacterIds"))
		{
			if (!token.matches("[1-9][0-9]{0,9}"))
			{
				throw new IllegalArgumentException("AllowedCharacterIds contains an invalid positive decimal identifier.");
			}
			try
			{
				result.add(Integer.valueOf(token));
			}
			catch (NumberFormatException e)
			{
				throw new IllegalArgumentException("AllowedCharacterIds contains an out-of-range identifier.");
			}
		}
		return Set.copyOf(result);
	}

	private static Set<String> parseAccounts(String value)
	{
		final Set<String> result = new HashSet<>();
		for (String token : splitList(value, "AllowedAccounts"))
		{
			final String normalized = token.toLowerCase(Locale.ROOT);
			if ((normalized.length() > MAX_ACCOUNT_LENGTH) || !normalized.matches("[a-z0-9_-]+"))
			{
				throw new IllegalArgumentException("AllowedAccounts contains an invalid account name.");
			}
			result.add(normalized);
		}
		return Set.copyOf(result);
	}

	private static String[] splitList(String value, String key)
	{
		final String candidate = value == null ? "" : value.trim();
		if (candidate.isEmpty())
		{
			return new String[0];
		}
		if (candidate.length() > MAX_LIST_LENGTH)
		{
			throw new IllegalArgumentException(key + " exceeds the bounded list length.");
		}
		final String[] tokens = candidate.split("[;,]", -1);
		if (tokens.length > MAX_LIST_ENTRIES)
		{
			throw new IllegalArgumentException(key + " exceeds the bounded entry count.");
		}
		for (int i = 0; i < tokens.length; i++)
		{
			tokens[i] = tokens[i].trim();
			if (tokens[i].isEmpty())
			{
				throw new IllegalArgumentException(key + " contains an empty token.");
			}
		}
		return tokens;
	}

	public record Settings(boolean enabled, boolean crossClassSkillsEnabled, boolean crystallizationEnabled, Set<Integer> allowedCharacterIds, Set<String> allowedAccounts, boolean valid, String diagnostic)
	{
		public Settings
		{
			allowedCharacterIds = Set.copyOf(allowedCharacterIds == null ? Set.of() : allowedCharacterIds);
			allowedAccounts = Set.copyOf(allowedAccounts == null ? Set.of() : allowedAccounts);
			if (!enabled || !valid)
			{
				enabled = false;
				crossClassSkillsEnabled = false;
				crystallizationEnabled = false;
				allowedCharacterIds = Set.of();
				allowedAccounts = Set.of();
			}
		}

		public static Settings disabled(String diagnostic)
		{
			return new Settings(false, false, false, Set.of(), Set.of(), true, diagnostic);
		}

		public static Settings invalid(String diagnostic)
		{
			return new Settings(false, false, false, Set.of(), Set.of(), false, diagnostic);
		}
	}
}

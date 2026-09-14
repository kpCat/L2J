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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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
	private static final double MIN_DURATION_MULTIPLIER = 0.01;
	private static final double MAX_DURATION_MULTIPLIER = 100.0;
	private static final String SEVEN_SIGNS_ACCESS_ENABLED_KEY = "EnablePersonalSevenSignsAccess";
	private static final String PARTY_SUPPORT_ENABLED_KEY = "EnablePersonalPartySupport";
	private static final String DURATION_ENABLED_KEY = "EnablePersonalEffectDurations";
	private static final String BUFF_MULTIPLIER_KEY = "PersonalBuffDurationMultiplier";
	private static final String DANCE_MULTIPLIER_KEY = "PersonalDanceDurationMultiplier";
	private static final String SONG_MULTIPLIER_KEY = "PersonalSongDurationMultiplier";
	private static final String DURATION_OVERRIDES_KEY = "PersonalEffectDurationOverrides";

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
		else if (!_settings.effectDurationSettings().valid())
		{
			LOGGER.warning("Personal effect durations are disabled: " + _settings.effectDurationSettings().diagnostic());
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
			final Boolean sevenSignsAccessEnabled = config.containsKey(SEVEN_SIGNS_ACCESS_ENABLED_KEY) ? strictBoolean(config.getValue(SEVEN_SIGNS_ACCESS_ENABLED_KEY)) : false;
			final Boolean partySupportEnabled = config.containsKey(PARTY_SUPPORT_ENABLED_KEY) ? strictBoolean(config.getValue(PARTY_SUPPORT_ENABLED_KEY)) : false;
			if ((enabled == null) || (crossClassSkillsEnabled == null) || (crystallizationEnabled == null) || (sevenSignsAccessEnabled == null) || (partySupportEnabled == null))
			{
				return Settings.invalid("All Personal Character QoL switches must be True or False.");
			}

			final Set<Integer> characterIds = parseCharacterIds(config.getValue("AllowedCharacterIds"));
			final Set<String> accounts = parseAccounts(config.getValue("AllowedAccounts"));
			final EffectDurationSettings effectDurationSettings = readEffectDurationSettings(config);
			if (!enabled)
			{
				return Settings.disabled("Disabled by configuration.");
			}
			return new Settings(true, crossClassSkillsEnabled, crystallizationEnabled, sevenSignsAccessEnabled, partySupportEnabled, characterIds, accounts, true, "Configuration accepted.", effectDurationSettings);
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

	private static EffectDurationSettings readEffectDurationSettings(ConfigReader config)
	{
		final boolean hasDurationSettings = config.containsKey(DURATION_ENABLED_KEY) || config.containsKey(BUFF_MULTIPLIER_KEY) || config.containsKey(DANCE_MULTIPLIER_KEY) || config.containsKey(SONG_MULTIPLIER_KEY) || config.containsKey(DURATION_OVERRIDES_KEY);
		if (!hasDurationSettings)
		{
			return EffectDurationSettings.disabled("Duration settings are absent; backward-compatible defaults apply.");
		}
		if (!config.containsKey(DURATION_ENABLED_KEY) || !config.containsKey(BUFF_MULTIPLIER_KEY) || !config.containsKey(DANCE_MULTIPLIER_KEY) || !config.containsKey(SONG_MULTIPLIER_KEY) || !config.containsKey(DURATION_OVERRIDES_KEY))
		{
			return EffectDurationSettings.invalid("All personal effect duration settings must be present when any one is configured.");
		}

		try
		{
			final Boolean enabled = strictBoolean(config.getValue(DURATION_ENABLED_KEY));
			if (enabled == null)
			{
				throw new IllegalArgumentException(DURATION_ENABLED_KEY + " must be True or False.");
			}
			final double buffMultiplier = parseDurationMultiplier(config.getValue(BUFF_MULTIPLIER_KEY), BUFF_MULTIPLIER_KEY);
			final double danceMultiplier = parseDurationMultiplier(config.getValue(DANCE_MULTIPLIER_KEY), DANCE_MULTIPLIER_KEY);
			final double songMultiplier = parseDurationMultiplier(config.getValue(SONG_MULTIPLIER_KEY), SONG_MULTIPLIER_KEY);
			final Map<Integer, Double> overrides = parseDurationOverrides(config.getValue(DURATION_OVERRIDES_KEY));
			return new EffectDurationSettings(enabled, buffMultiplier, danceMultiplier, songMultiplier, overrides, true, enabled ? "Duration configuration accepted." : "Disabled by configuration.");
		}
		catch (IllegalArgumentException e)
		{
			return EffectDurationSettings.invalid(e.getMessage());
		}
		catch (RuntimeException e)
		{
			return EffectDurationSettings.invalid("Duration configuration could not be parsed: " + e.getClass().getSimpleName() + ".");
		}
	}

	private static double parseDurationMultiplier(String value, String key)
	{
		final String candidate = value == null ? "" : value.trim();
		if (!candidate.matches("(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?"))
		{
			throw new IllegalArgumentException(key + " must be a decimal number using '.' as the separator.");
		}
		final double multiplier;
		try
		{
			multiplier = Double.parseDouble(candidate);
		}
		catch (NumberFormatException e)
		{
			throw new IllegalArgumentException(key + " is outside the supported decimal range.");
		}
		if (!Double.isFinite(multiplier) || (multiplier < MIN_DURATION_MULTIPLIER) || (multiplier > MAX_DURATION_MULTIPLIER))
		{
			throw new IllegalArgumentException(key + " must be between 0.01 and 100.0.");
		}
		return multiplier;
	}

	private static Map<Integer, Double> parseDurationOverrides(String value)
	{
		final String candidate = value == null ? "" : value.trim();
		if (candidate.isEmpty())
		{
			return Map.of();
		}
		if (candidate.length() > MAX_LIST_LENGTH)
		{
			throw new IllegalArgumentException(DURATION_OVERRIDES_KEY + " exceeds the bounded list length.");
		}
		final String[] entries = candidate.split(";", -1);
		if (entries.length > MAX_LIST_ENTRIES)
		{
			throw new IllegalArgumentException(DURATION_OVERRIDES_KEY + " exceeds the bounded entry count.");
		}
		final Map<Integer, Double> result = new HashMap<>();
		for (String entry : entries)
		{
			final String[] pair = entry.trim().split(",", -1);
			if ((pair.length != 2) || !pair[0].trim().matches("[1-9][0-9]{0,9}"))
			{
				throw new IllegalArgumentException(DURATION_OVERRIDES_KEY + " entries must use positiveSkillId,multiplier.");
			}
			final int skillId;
			try
			{
				skillId = Integer.parseInt(pair[0].trim());
			}
			catch (NumberFormatException e)
			{
				throw new IllegalArgumentException(DURATION_OVERRIDES_KEY + " contains an out-of-range skill identifier.");
			}
			if (result.putIfAbsent(skillId, parseDurationMultiplier(pair[1], DURATION_OVERRIDES_KEY)) != null)
			{
				throw new IllegalArgumentException(DURATION_OVERRIDES_KEY + " contains a duplicate skill identifier.");
			}
		}
		return Map.copyOf(result);
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

	public record EffectDurationSettings(boolean enabled, double buffMultiplier, double danceMultiplier, double songMultiplier, Map<Integer, Double> overrides, boolean valid, String diagnostic)
	{
		public EffectDurationSettings
		{
			overrides = Map.copyOf(overrides == null ? Map.of() : overrides);
			if (!enabled || !valid)
			{
				enabled = false;
				buffMultiplier = 1.0;
				danceMultiplier = 1.0;
				songMultiplier = 1.0;
				overrides = Map.of();
			}
		}

		public static EffectDurationSettings disabled(String diagnostic)
		{
			return new EffectDurationSettings(false, 1.0, 1.0, 1.0, Map.of(), true, diagnostic);
		}

		public static EffectDurationSettings invalid(String diagnostic)
		{
			return new EffectDurationSettings(false, 1.0, 1.0, 1.0, Map.of(), false, diagnostic);
		}
	}

	public record Settings(boolean enabled, boolean crossClassSkillsEnabled, boolean crystallizationEnabled, boolean sevenSignsAccessEnabled, boolean partySupportEnabled, Set<Integer> allowedCharacterIds, Set<String> allowedAccounts, boolean valid, String diagnostic, EffectDurationSettings effectDurationSettings)
	{
		public Settings(boolean enabled, boolean crossClassSkillsEnabled, boolean crystallizationEnabled, Set<Integer> allowedCharacterIds, Set<String> allowedAccounts, boolean valid, String diagnostic)
		{
			this(enabled, crossClassSkillsEnabled, crystallizationEnabled, false, false, allowedCharacterIds, allowedAccounts, valid, diagnostic, EffectDurationSettings.disabled("Duration settings are absent; backward-compatible defaults apply."));
		}

		public Settings(boolean enabled, boolean crossClassSkillsEnabled, boolean crystallizationEnabled, boolean sevenSignsAccessEnabled, Set<Integer> allowedCharacterIds, Set<String> allowedAccounts, boolean valid, String diagnostic)
		{
			this(enabled, crossClassSkillsEnabled, crystallizationEnabled, sevenSignsAccessEnabled, false, allowedCharacterIds, allowedAccounts, valid, diagnostic, EffectDurationSettings.disabled("Duration settings are absent; backward-compatible defaults apply."));
		}

		public Settings(boolean enabled, boolean crossClassSkillsEnabled, boolean crystallizationEnabled, boolean sevenSignsAccessEnabled, boolean partySupportEnabled, Set<Integer> allowedCharacterIds, Set<String> allowedAccounts, boolean valid, String diagnostic)
		{
			this(enabled, crossClassSkillsEnabled, crystallizationEnabled, sevenSignsAccessEnabled, partySupportEnabled, allowedCharacterIds, allowedAccounts, valid, diagnostic, EffectDurationSettings.disabled("Duration settings are absent; backward-compatible defaults apply."));
		}

		public Settings(boolean enabled, boolean crossClassSkillsEnabled, boolean crystallizationEnabled, Set<Integer> allowedCharacterIds, Set<String> allowedAccounts, boolean valid, String diagnostic, EffectDurationSettings effectDurationSettings)
		{
			this(enabled, crossClassSkillsEnabled, crystallizationEnabled, false, false, allowedCharacterIds, allowedAccounts, valid, diagnostic, effectDurationSettings);
		}

		public Settings(boolean enabled, boolean crossClassSkillsEnabled, boolean crystallizationEnabled, boolean sevenSignsAccessEnabled, Set<Integer> allowedCharacterIds, Set<String> allowedAccounts, boolean valid, String diagnostic, EffectDurationSettings effectDurationSettings)
		{
			this(enabled, crossClassSkillsEnabled, crystallizationEnabled, sevenSignsAccessEnabled, false, allowedCharacterIds, allowedAccounts, valid, diagnostic, effectDurationSettings);
		}

		public Settings
		{
			allowedCharacterIds = Set.copyOf(allowedCharacterIds == null ? Set.of() : allowedCharacterIds);
			allowedAccounts = Set.copyOf(allowedAccounts == null ? Set.of() : allowedAccounts);
			effectDurationSettings = effectDurationSettings == null ? EffectDurationSettings.invalid("Duration settings are absent from the runtime snapshot.") : effectDurationSettings;
			if (!enabled || !valid)
			{
				enabled = false;
				crossClassSkillsEnabled = false;
				crystallizationEnabled = false;
				sevenSignsAccessEnabled = false;
				partySupportEnabled = false;
				allowedCharacterIds = Set.of();
				allowedAccounts = Set.of();
			}
		}

		public static Settings disabled(String diagnostic)
		{
			return new Settings(false, false, false, false, false, Set.of(), Set.of(), true, diagnostic, EffectDurationSettings.disabled("Master Personal Character QoL is disabled."));
		}

		public static Settings invalid(String diagnostic)
		{
			return new Settings(false, false, false, false, false, Set.of(), Set.of(), false, diagnostic, EffectDurationSettings.disabled("Master Personal Character QoL configuration is invalid."));
		}
	}
}

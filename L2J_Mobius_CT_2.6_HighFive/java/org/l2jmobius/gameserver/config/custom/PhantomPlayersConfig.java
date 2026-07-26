/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.commons.util.ConfigReader;

/**
 * Loads the disabled-by-default Phantom World skeleton configuration.
 */
public final class PhantomPlayersConfig
{
	public static final String PHANTOM_PLAYERS_CONFIG_FILE = "./config/Custom/PhantomPlayers.ini";
	public static final int DEFAULT_MAX_MATERIALIZED_PHANTOMS = 32;

	private static volatile Settings _settings = Settings.disabled();

	private PhantomPlayersConfig()
	{
	}

	public static void load()
	{
		_settings = read(Path.of(PHANTOM_PLAYERS_CONFIG_FILE));
	}

	public static Settings read(Path path)
	{
		try
		{
			if ((path == null) || !Files.isRegularFile(path))
			{
				return Settings.disabled();
			}

			final ConfigReader config = new ConfigReader(path.toString());
			final boolean enabled = strictBoolean(config.getValue("EnablePhantomSystem"));
			if (!enabled)
			{
				return Settings.disabled();
			}
			final Integer maximumMaterialized = strictCap(config.getValue("MaxMaterializedPhantoms"));
			if (maximumMaterialized == null)
			{
				return Settings.disabled();
			}
			final boolean diagnosticsEnabled = enabled && strictBoolean(config.getValue("EnablePhantomDiagnostics"));
			return new Settings(true, diagnosticsEnabled, maximumMaterialized);
		}
		catch (RuntimeException e)
		{
			return Settings.disabled();
		}
	}

	public static Settings settings()
	{
		return _settings;
	}

	public static boolean isEnabled()
	{
		return _settings.enabled();
	}

	private static boolean strictBoolean(String value)
	{
		if (value == null)
		{
			return false;
		}

		final String normalized = value.trim();
		if (normalized.equalsIgnoreCase("true"))
		{
			return true;
		}
		if (normalized.equalsIgnoreCase("false"))
		{
			return false;
		}
		return false;
	}

	private static Integer strictCap(String value)
	{
		if (value == null)
		{
			return null;
		}
		final String normalized = value.trim();
		if (!normalized.matches("[0-9]+"))
		{
			return null;
		}
		try
		{
			final int parsed = Integer.parseInt(normalized, 10);
			return ((parsed >= 1) && (parsed <= 10000)) ? parsed : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	public record Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms)
	{
		public Settings
		{
			diagnosticsEnabled = enabled && diagnosticsEnabled;
			maxMaterializedPhantoms = enabled ? maxMaterializedPhantoms : 0;
			if (enabled && ((maxMaterializedPhantoms < 1) || (maxMaterializedPhantoms > 10000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a materialization cap between 1 and 10000.");
			}
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled)
		{
			this(enabled, diagnosticsEnabled, enabled ? DEFAULT_MAX_MATERIALIZED_PHANTOMS : 0);
		}

		public static Settings disabled()
		{
			return new Settings(false, false, 0);
		}
	}
}

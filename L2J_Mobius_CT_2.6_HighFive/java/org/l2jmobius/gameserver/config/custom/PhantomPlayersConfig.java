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

	private static volatile Settings _settings = new Settings(false, false);

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
				return new Settings(false, false);
			}

			final ConfigReader config = new ConfigReader(path.toString());
			final boolean enabled = strictBoolean(config.getValue("EnablePhantomSystem"));
			final boolean diagnosticsEnabled = enabled && strictBoolean(config.getValue("EnablePhantomDiagnostics"));
			return new Settings(enabled, diagnosticsEnabled);
		}
		catch (RuntimeException e)
		{
			return new Settings(false, false);
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

	public record Settings(boolean enabled, boolean diagnosticsEnabled)
	{
		public Settings
		{
			diagnosticsEnabled = enabled && diagnosticsEnabled;
		}
	}
}

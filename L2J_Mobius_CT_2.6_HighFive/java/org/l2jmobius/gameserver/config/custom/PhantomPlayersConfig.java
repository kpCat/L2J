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
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;

import org.l2jmobius.commons.util.ConfigReader;

/**
 * Loads the disabled-by-default Phantom World skeleton configuration.
 */
public final class PhantomPlayersConfig
{
	public static final String PHANTOM_PLAYERS_CONFIG_FILE = "./config/Custom/PhantomPlayers.ini";
	public static final int DEFAULT_MAX_MATERIALIZED_PHANTOMS = 32;
	public static final int DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES = 10000;
	public static final int DEFAULT_SCHEDULER_PULSE_MILLIS = 100;
	public static final int DEFAULT_SCHEDULER_PROFILES_PER_PULSE = 128;
	public static final int DEFAULT_POPULATION_TARGET = 0;
	public static final int DEFAULT_POPULATION_ACTIVE_TARGET = 0;
	public static final int DEFAULT_POPULATION_CREATION_IN_FLIGHT = 2;
	public static final int DEFAULT_POPULATION_BOUNDARIES_PER_PULSE = 64;
	public static final int DEFAULT_PARTY_OPERATIONS_PER_PULSE = 64;
	public static final ZoneId DEFAULT_POPULATION_TIME_ZONE = ZoneId.of("UTC");

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
			final Integer maximumScheduled = strictInteger(config.getValue("MaxScheduledPhantomProfiles"), 1, 1_000_000);
			final Integer pulseMillis = strictInteger(config.getValue("PhantomSchedulerPulseMillis"), 10, 1000);
			final Integer profilesPerPulse = strictInteger(config.getValue("PhantomSchedulerProfilesPerPulse"), 1, 10000);
			if ((maximumMaterialized == null) || (maximumScheduled == null) || (pulseMillis == null) || (profilesPerPulse == null) || (maximumScheduled < maximumMaterialized))
			{
				return Settings.disabled();
			}
			final Integer populationTarget = strictInteger(config.getValue("PhantomPopulationTarget"), 0, maximumScheduled, DEFAULT_POPULATION_TARGET);
			final Integer populationActiveTarget = strictInteger(config.getValue("PhantomPopulationActiveTarget"), 0, Math.min(populationTarget != null ? populationTarget : 0, maximumMaterialized), DEFAULT_POPULATION_ACTIVE_TARGET);
			final Integer populationCreationInFlight = strictInteger(config.getValue("PhantomPopulationCreationInFlight"), 1, 64, DEFAULT_POPULATION_CREATION_IN_FLIGHT);
			final Integer populationBoundariesPerPulse = strictInteger(config.getValue("PhantomPopulationBoundariesPerPulse"), 1, 10000, DEFAULT_POPULATION_BOUNDARIES_PER_PULSE);
			final Integer partyOperationsPerPulse = strictInteger(config.getValue("PhantomPartyOperationsPerPulse"), 1, 10000, DEFAULT_PARTY_OPERATIONS_PER_PULSE);
			final ZoneId populationTimeZone = strictZoneId(config.getValue("PhantomPopulationTimeZone"));
			if ((populationTarget == null) || (populationActiveTarget == null) || (populationCreationInFlight == null) || (populationBoundariesPerPulse == null) || (partyOperationsPerPulse == null) || (populationTimeZone == null))
			{
				return Settings.disabled();
			}
			final boolean diagnosticsEnabled = enabled && strictBoolean(config.getValue("EnablePhantomDiagnostics"));
			return new Settings(true, diagnosticsEnabled, maximumMaterialized, maximumScheduled, pulseMillis, profilesPerPulse, populationTarget, populationActiveTarget, populationCreationInFlight, populationBoundariesPerPulse, partyOperationsPerPulse, populationTimeZone);
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
		return strictInteger(value, 1, 10000);
	}

	private static Integer strictInteger(String value, int minimum, int maximum)
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
			return ((parsed >= minimum) && (parsed <= maximum)) ? parsed : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static Integer strictInteger(String value, int minimum, int maximum, int defaultValue)
	{
		return value == null ? defaultValue : strictInteger(value, minimum, maximum);
	}

	private static ZoneId strictZoneId(String value)
	{
		if (value == null)
		{
			return DEFAULT_POPULATION_TIME_ZONE;
		}
		final String normalized = value.trim();
		if (normalized.isEmpty() || (normalized.length() > 64))
		{
			return null;
		}
		try
		{
			return ZoneId.of(normalized);
		}
		catch (DateTimeException e)
		{
			return null;
		}
	}

	public record Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse, int populationTarget, int populationActiveTarget, int populationCreationInFlight, int populationBoundariesPerPulse, int partyOperationsPerPulse, ZoneId populationTimeZone)
	{
		public Settings
		{
			diagnosticsEnabled = enabled && diagnosticsEnabled;
			maxMaterializedPhantoms = enabled ? maxMaterializedPhantoms : 0;
			maxScheduledPhantomProfiles = enabled ? maxScheduledPhantomProfiles : 0;
			schedulerPulseMillis = enabled ? schedulerPulseMillis : 0;
			schedulerProfilesPerPulse = enabled ? schedulerProfilesPerPulse : 0;
			populationTarget = enabled ? populationTarget : 0;
			populationActiveTarget = enabled ? populationActiveTarget : 0;
			populationCreationInFlight = enabled ? populationCreationInFlight : 0;
			populationBoundariesPerPulse = enabled ? populationBoundariesPerPulse : 0;
			partyOperationsPerPulse = enabled ? partyOperationsPerPulse : 0;
			populationTimeZone = enabled ? populationTimeZone : DEFAULT_POPULATION_TIME_ZONE;
			if (enabled && ((maxMaterializedPhantoms < 1) || (maxMaterializedPhantoms > 10000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a materialization cap between 1 and 10000.");
			}
			if (enabled && ((maxScheduledPhantomProfiles < 1) || (maxScheduledPhantomProfiles > 1_000_000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a scheduled profile cap between 1 and 1000000.");
			}
			if (enabled && ((schedulerPulseMillis < 10) || (schedulerPulseMillis > 1000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a scheduler pulse between 10 and 1000 milliseconds.");
			}
			if (enabled && ((schedulerProfilesPerPulse < 1) || (schedulerProfilesPerPulse > 10000)))
			{
				throw new IllegalArgumentException("Enabled Phantom settings require a scheduler profile budget between 1 and 10000.");
			}
			if (enabled && (maxScheduledPhantomProfiles < maxMaterializedPhantoms))
			{
				throw new IllegalArgumentException("Scheduled Phantom profile capacity must cover materialization capacity.");
			}
			if (enabled && ((populationTarget < 0) || (populationTarget > maxScheduledPhantomProfiles)))
			{
				throw new IllegalArgumentException("Population target must be between zero and scheduled profile capacity.");
			}
			if (enabled && ((populationActiveTarget < 0) || (populationActiveTarget > Math.min(populationTarget, maxMaterializedPhantoms))))
			{
				throw new IllegalArgumentException("Population ACTIVE target must fit both population and materialization capacity.");
			}
			if (enabled && ((populationCreationInFlight < 1) || (populationCreationInFlight > 64)))
			{
				throw new IllegalArgumentException("Population creation in-flight limit must be between 1 and 64.");
			}
			if (enabled && ((populationBoundariesPerPulse < 1) || (populationBoundariesPerPulse > 10000)))
			{
				throw new IllegalArgumentException("Population boundary budget must be between 1 and 10000.");
			}
			if (enabled && ((partyOperationsPerPulse < 1) || (partyOperationsPerPulse > 10000)))
			{
				throw new IllegalArgumentException("Party operation budget must be between 1 and 10000.");
			}
			Objects.requireNonNull(populationTimeZone, "Population time zone must not be null.");
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled)
		{
			this(enabled, diagnosticsEnabled, enabled ? DEFAULT_MAX_MATERIALIZED_PHANTOMS : 0, enabled ? DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES : 0, enabled ? DEFAULT_SCHEDULER_PULSE_MILLIS : 0, enabled ? DEFAULT_SCHEDULER_PROFILES_PER_PULSE : 0, DEFAULT_POPULATION_TARGET, DEFAULT_POPULATION_ACTIVE_TARGET, enabled ? DEFAULT_POPULATION_CREATION_IN_FLIGHT : 0, enabled ? DEFAULT_POPULATION_BOUNDARIES_PER_PULSE : 0, enabled ? DEFAULT_PARTY_OPERATIONS_PER_PULSE : 0, DEFAULT_POPULATION_TIME_ZONE);
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, enabled ? DEFAULT_MAX_SCHEDULED_PHANTOM_PROFILES : 0, enabled ? DEFAULT_SCHEDULER_PULSE_MILLIS : 0, enabled ? DEFAULT_SCHEDULER_PROFILES_PER_PULSE : 0, DEFAULT_POPULATION_TARGET, DEFAULT_POPULATION_ACTIVE_TARGET, enabled ? DEFAULT_POPULATION_CREATION_IN_FLIGHT : 0, enabled ? DEFAULT_POPULATION_BOUNDARIES_PER_PULSE : 0, enabled ? DEFAULT_PARTY_OPERATIONS_PER_PULSE : 0, DEFAULT_POPULATION_TIME_ZONE);
		}

		public Settings(boolean enabled, boolean diagnosticsEnabled, int maxMaterializedPhantoms, int maxScheduledPhantomProfiles, int schedulerPulseMillis, int schedulerProfilesPerPulse)
		{
			this(enabled, diagnosticsEnabled, maxMaterializedPhantoms, maxScheduledPhantomProfiles, schedulerPulseMillis, schedulerProfilesPerPulse, DEFAULT_POPULATION_TARGET, DEFAULT_POPULATION_ACTIVE_TARGET, enabled ? DEFAULT_POPULATION_CREATION_IN_FLIGHT : 0, enabled ? DEFAULT_POPULATION_BOUNDARIES_PER_PULSE : 0, enabled ? DEFAULT_PARTY_OPERATIONS_PER_PULSE : 0, DEFAULT_POPULATION_TIME_ZONE);
		}

		public static Settings disabled()
		{
			return new Settings(false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, DEFAULT_POPULATION_TIME_ZONE);
		}
	}
}

package org.l2jmobius.gameserver.phantoms.navigation;

import java.time.Instant;
import java.util.Properties;

public final class PhantomDedicatedScoutChecks
{
	public static void main(String[] args) throws Exception
	{
		final var ini = new Properties();
		ini.setProperty("PhysicalGeoScoutMode", "DEDICATED");
		ini.setProperty("PhysicalGeoScoutSchedule", "ALWAYS");
		ini.setProperty("PhysicalGeoScoutCount", "32");
		final var settings = PhantomGeoTrainer.Settings.readScoutSettings(ini);
		final var schedule = new PhantomPhysicalGeoScout.SessionSchedule(settings);
		if (settings.count() != 32 || !"ALWAYS".equals(schedule.mode(Instant.parse("2026-11-23T10:00:00Z"))))
		{
			throw new AssertionError("Dedicated 32-scout service must be open in daytime.");
		}
		ini.remove("PhysicalGeoScoutMode");
		ini.remove("PhysicalGeoScoutSchedule");
		try
		{
			PhantomGeoTrainer.Settings.readScoutSettings(ini);
			throw new AssertionError("Legacy settings must not admit 32 ordinary-profile scouts.");
		}
		catch (IllegalArgumentException expected)
		{
			System.out.println("PASS dedicated 32-scout ALWAYS and legacy size fence");
		}
	}
}

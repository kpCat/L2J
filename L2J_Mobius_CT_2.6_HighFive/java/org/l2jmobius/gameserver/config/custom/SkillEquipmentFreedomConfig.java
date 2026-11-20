/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import org.l2jmobius.commons.util.ConfigReader;

public final class SkillEquipmentFreedomConfig
{
	private static final Logger LOGGER = Logger.getLogger(SkillEquipmentFreedomConfig.class.getName());
	private static final Path FILE = Path.of("config", "Custom", "SkillEquipmentFreedom.ini");
	private static final String KEY = "EnableSkillsIndependentOfEquipment";
	private static boolean _enabled;

	private SkillEquipmentFreedomConfig()
	{
	}

	public static void load()
	{
		_enabled = false;
		try
		{
			if (!Files.isRegularFile(FILE))
			{
				LOGGER.warning(FILE + " is absent. Using stock skill equipment rules.");
				return;
			}

			final String value = new ConfigReader(FILE.toString()).getValue(KEY);
			if ("true".equalsIgnoreCase(value))
			{
				_enabled = true;
			}
			else if (!"false".equalsIgnoreCase(value))
			{
				LOGGER.warning(KEY + " must be True or False. Using stock skill equipment rules.");
			}
		}
		catch (RuntimeException e)
		{
			LOGGER.warning("Could not load " + FILE + ". Using stock skill equipment rules.");
		}
	}

	public static boolean isEnabled()
	{
		return _enabled;
	}
}

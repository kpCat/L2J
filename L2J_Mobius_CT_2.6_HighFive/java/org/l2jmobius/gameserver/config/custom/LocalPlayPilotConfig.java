package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

import org.l2jmobius.commons.util.ConfigReader;

/** Local-only single-client playtest switch. Missing or malformed means OFF. */
public final class LocalPlayPilotConfig
{
	private static final String CONFIG_FILE = "./config/Custom/LocalPlayPilot.ini";
	private static volatile boolean _enabled;
	private static volatile boolean _autoAttachEnabled;
	private static volatile Set<String> _autoAttachCharacters = Set.of();
	private static volatile boolean _syntheticEnabled;
	private static int _syntheticObjectId;
	private static String _syntheticName = "";
	private static volatile List<Long> _observerProfiles = List.of();

	private LocalPlayPilotConfig()
	{
	}

	public static void load()
	{
		_enabled = false;
		loadAutoAttach("False", "");
		_syntheticEnabled = false;
		_observerProfiles = List.of();
		final Path path = Path.of(CONFIG_FILE);
		if (!Files.isRegularFile(path))
		{
			return;
		}
		final ConfigReader reader = new ConfigReader(CONFIG_FILE);
		final String value = reader.getValue("EnableLocalPlayPilot");
		_enabled = "True".equalsIgnoreCase(value);
		loadAutoAttach(reader.getValue("EnableLocalPlayPilotAutoAttach"), reader.getValue("LocalPlayPilotAutoAttachCharacters"));
		try
		{
			final String selected = reader.getValue("LocalPlayObserverProfileIds");
			if (selected != null && !selected.isBlank())
			{
				final var ids = java.util.Arrays.stream(selected.strip().split("[,\\s]+")).map(Long::parseLong).toList();
				if (ids.size() > 8 || ids.stream().anyMatch(id -> id <= 0) || ids.stream().distinct().count() != ids.size()) { throw new IllegalArgumentException("Invalid bounded observer selection."); }
				_observerProfiles = List.copyOf(ids);
			}
		}
		catch (RuntimeException exception) { _observerProfiles = List.of(); }
		try
		{
			_syntheticObjectId = Integer.parseInt(reader.getValue("LocalPlaySyntheticCharacterObjectId"));
			_syntheticName = reader.getValue("LocalPlaySyntheticCharacterName");
			_syntheticEnabled = "True".equalsIgnoreCase(reader.getValue("EnableLocalPlaySyntheticHuman")) && (_syntheticObjectId > 0) && (_syntheticName != null) && !_syntheticName.isBlank();
		}
		catch (RuntimeException exception) { _syntheticEnabled = false; }
	}

	public static boolean isEnabled()
	{
		return _enabled;
	}

	/** Explicit read-only diagnostic addresses; never an admission or materialization request. */
	public static List<Long> observerProfileIds() { return _observerProfiles; }

	private static void loadAutoAttach(String enabled, String names)
	{
		_autoAttachEnabled = false;
		_autoAttachCharacters = Set.of();
		if (!"True".equalsIgnoreCase(enabled) || (names == null))
		{
			return;
		}
		final Set<String> characters = new HashSet<>();
		int count = 0;
		for (String fragment : names.split(","))
		{
			final String name = fragment.trim();
			if (name.isEmpty())
			{
				continue;
			}
			if ((++count > 32) || (name.length() > 32))
			{
				return;
			}
			characters.add(name.toLowerCase(Locale.ROOT));
		}
		_autoAttachCharacters = Set.copyOf(characters);
		_autoAttachEnabled = !characters.isEmpty();
	}

	public static boolean isAutoAttachEnabled()
	{
		return _autoAttachEnabled;
	}

	public static boolean isAutoAttachCharacter(String name)
	{
		return _autoAttachEnabled && (name != null) && _autoAttachCharacters.contains(name.toLowerCase(Locale.ROOT));
	}

	public static boolean isSyntheticEnabled() { return _syntheticEnabled; }
	public static int syntheticObjectId() { return _syntheticObjectId; }
	public static String syntheticName() { return _syntheticName; }
}

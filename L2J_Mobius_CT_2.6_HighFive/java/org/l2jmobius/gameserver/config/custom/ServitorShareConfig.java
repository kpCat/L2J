package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.model.stats.Stat;

/** Startup tuning of positive native coefficients for Servitor Share and Spirit Sharing. */
public final class ServitorShareConfig
{
	public static final String FILE = "./config/Custom/ServitorShare.ini";
	private static final Map<Stat, String> KEYS = Map.ofEntries(
		Map.entry(Stat.POWER_ATTACK, "PAtk"), Map.entry(Stat.POWER_DEFENCE, "PDef"),
		Map.entry(Stat.MAGIC_ATTACK, "MAtk"), Map.entry(Stat.MAGIC_DEFENCE, "MDef"),
		Map.entry(Stat.MAX_HP, "MaxHp"), Map.entry(Stat.MAX_MP, "MaxMp"),
		Map.entry(Stat.MAX_RECOVERABLE_HP, "MaxRecoverableHp"), Map.entry(Stat.MAX_RECOVERABLE_MP, "MaxRecoverableMp"),
		Map.entry(Stat.CRITICAL_RATE, "CritRate"), Map.entry(Stat.POWER_ATTACK_SPEED, "PAtkSpd"), Map.entry(Stat.MAGIC_ATTACK_SPEED, "MAtkSpd"));
	private static final Map<Stat, String> SPIRIT_KEYS = Map.of(
		Stat.POWER_ATTACK, "PAtk", Stat.MAGIC_ATTACK, "MAtk",
		Stat.PHYSICAL_SKILL_POWER, "PhysicalSkillPower",
		Stat.POWER_ATTACK_SPEED, "PAtkSpd", Stat.MAGIC_ATTACK_SPEED, "MAtkSpd",
		Stat.CRITICAL_RATE, "CritRate");
	private static final SpiritSettings SPIRIT_STOCK = new SpiritSettings(false, Map.of());
	private static volatile Settings _settings = new Settings(false, Map.of());
	private static volatile SpiritSettings _spiritSettings = SPIRIT_STOCK;

	private ServitorShareConfig() { }

	public static void load()
	{
		final Path path = Path.of(FILE);
		_settings = read(path);
		_spiritSettings = readSpiritSharing(path);
	}
	public static Settings settings() { return _settings; }

	public static SpiritSettings spiritSharingSettings()
	{
		return _spiritSettings;
	}

	public static SpiritSettings readSpiritSharing(Path path)
	{
		if (!Files.isRegularFile(path))
		{
			return SPIRIT_STOCK;
		}

		try
		{
			final var config = new ConfigReader(path.toString());
			final String enabled = config.getString("EnableSpiritSharingBonusTuning", "False");
			if (enabled.equalsIgnoreCase("false"))
			{
				return SPIRIT_STOCK;
			}
			if (!enabled.equalsIgnoreCase("true"))
			{
				throw new IllegalArgumentException("Invalid EnableSpiritSharingBonusTuning switch.");
			}

			final Map<Stat, Double> values = new EnumMap<>(Stat.class);
			for (var entry : SPIRIT_KEYS.entrySet())
			{
				final String key = "SpiritSharing" + entry.getValue() + "BonusMultiplier";
				final double value = Double.parseDouble(config.getString(key, "2.0"));
				if (!Double.isFinite(value) || (value < 0) || (value > 100))
				{
					throw new IllegalArgumentException("Invalid " + key + " multiplier (expected 0 to 100).");
				}
				values.put(entry.getKey(), value);
			}
			return new SpiritSettings(true, values);
		}
		catch (RuntimeException invalid)
		{
			Logger.getLogger(ServitorShareConfig.class.getName()).warning("Spirit Sharing tuning rejected; native coefficients retained: " + invalid.getMessage());
			return SPIRIT_STOCK;
		}
	}

	public record SpiritSettings(boolean enabled, Map<Stat, Double> multipliers)
	{
		public SpiritSettings
		{
			multipliers = Map.copyOf(multipliers);
		}

		public double coefficient(int skillId, Stat stat, double stock)
		{
			if (!enabled || (skillId != 1547) || (stock <= 1))
			{
				return stock;
			}
			final double multiplier = multipliers.getOrDefault(stat, 1.0);
			return multiplier == 1.0 ? stock : 1 + (stock - 1) * multiplier;
		}
	}


	public static Settings read(Path path)
	{
		try
		{
			if (!Files.isRegularFile(path)) { return new Settings(false, Map.of()); }
			final var config = new ConfigReader(path.toString());
			final String enabled = config.getString("EnableServitorShareBonusTuning", "True");
			if (!enabled.equalsIgnoreCase("true") && !enabled.equalsIgnoreCase("false")) { throw new IllegalArgumentException("Invalid switch."); }
			final Map<Stat, Double> values = new EnumMap<>(Stat.class);
			for (var entry : KEYS.entrySet())
			{
				final double value = config.getDouble("ServitorShare" + entry.getValue() + "BonusMultiplier", 2.0);
				if (!Double.isFinite(value) || value < 0 || value > 100) { throw new IllegalArgumentException("Invalid multiplier for " + entry.getValue()); }
				values.put(entry.getKey(), value);
			}
			return new Settings(Boolean.parseBoolean(enabled), values);
		}
		catch (RuntimeException invalid)
		{
			Logger.getLogger(ServitorShareConfig.class.getName()).warning("Servitor Share tuning rejected; native coefficients retained: " + invalid.getMessage());
			return new Settings(false, Map.of());
		}
	}

	public record Settings(boolean enabled, Map<Stat, Double> multipliers)
	{
		public Settings { multipliers = Map.copyOf(multipliers); }
		public double coefficient(int skillId, Stat stat, double stock)
		{
			return enabled && skillId == 1557 && stock > 1 && KEYS.containsKey(stat) ? 1 + (stock - 1) * multipliers.getOrDefault(stat, 2.0) : stock;
		}
	}
}

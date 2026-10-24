package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.model.stats.Stat;

/** Startup-only tuning of positive native coefficients belonging to skill 1557. */
public final class ServitorShareConfig
{
	public static final String FILE = "./config/Custom/ServitorShare.ini";
	private static final Map<Stat, String> KEYS = Map.ofEntries(
		Map.entry(Stat.POWER_ATTACK, "PAtk"), Map.entry(Stat.POWER_DEFENCE, "PDef"),
		Map.entry(Stat.MAGIC_ATTACK, "MAtk"), Map.entry(Stat.MAGIC_DEFENCE, "MDef"),
		Map.entry(Stat.MAX_HP, "MaxHp"), Map.entry(Stat.MAX_MP, "MaxMp"),
		Map.entry(Stat.MAX_RECOVERABLE_HP, "MaxRecoverableHp"), Map.entry(Stat.MAX_RECOVERABLE_MP, "MaxRecoverableMp"),
		Map.entry(Stat.CRITICAL_RATE, "CritRate"), Map.entry(Stat.POWER_ATTACK_SPEED, "PAtkSpd"), Map.entry(Stat.MAGIC_ATTACK_SPEED, "MAtkSpd"));
	private static volatile Settings _settings = new Settings(false, Map.of());

	private ServitorShareConfig() { }

	public static void load() { _settings = read(Path.of(FILE)); }
	public static Settings settings() { return _settings; }

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

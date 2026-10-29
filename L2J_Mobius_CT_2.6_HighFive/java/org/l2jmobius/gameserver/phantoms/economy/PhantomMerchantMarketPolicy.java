/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.model.item.type.CrystalType;

/** Startup-only public merchant settings. Native slots, source prices and birth quotas remain independent. */
public record PhantomMerchantMarketPolicy(int assortmentSeconds, int gearShare, Set<CrystalType> gearGrades,
	boolean fundingEnabled, long fundingGrant, int fundingCooldown, long fundingPerOwner, long fundingGlobal,
	boolean stockEnabled, long stockGlobal, long stockPerOwner, long stockPerItem, long stockPerGrade,
	int gearPerGrade, int stockStack, int enchantMax, int enchantedPerEpoch, int grantsPerOwner, int grantsGlobal)
{
	public PhantomMerchantMarketPolicy
	{
		gearGrades = Set.copyOf(gearGrades);
		if (assortmentSeconds < 300 || assortmentSeconds > 86400 || gearShare < 0 || gearShare > 50 || gearGrades.isEmpty()
			|| fundingGrant < 1 || fundingGrant > 1_000_000_000L || fundingCooldown < 300 || fundingCooldown > 86400
			|| fundingPerOwner < fundingGrant || fundingPerOwner > 10_000_000_000L || fundingGlobal < fundingGrant || fundingGlobal > 1_000_000_000_000L
			|| stockGlobal < 1 || stockGlobal > 1_000_000_000_000L || stockPerOwner < 1 || stockPerOwner > stockGlobal
			|| stockPerItem < 1 || stockPerItem > 1_000_000 || stockPerGrade < 1 || stockPerGrade > 1_000_000
			|| gearPerGrade < 1 || gearPerGrade > 1000 || stockStack < 1 || stockStack > 100 || enchantMax < 1 || enchantMax > 25
			|| enchantedPerEpoch < 1 || enchantedPerEpoch > 32 || grantsPerOwner < 1 || grantsPerOwner > 256 || grantsGlobal < 1 || grantsGlobal > 32768)
		{ throw new IllegalArgumentException("Merchant market policy outside finite hard bounds."); }
	}
	public static PhantomMerchantMarketPolicy load(Path file)
	{
		final var config = new ConfigReader(file.toString());
		final var grades = Arrays.stream(text(config, "MerchantMarketGearGrades", "NONE,D,C,B,A,S,S80,S84").split(",", -1)).map(String::trim).map(CrystalType::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
		return new PhantomMerchantMarketPolicy(integer(config, "MerchantAssortmentRefreshSeconds", 1800), integer(config, "MerchantGearRotationSharePercent", 20), grades,
			flag(config, "MerchantBuyFundingEnabled", true), number(config, "MerchantBuyFundingGrantAdena", 50_000_000), integer(config, "MerchantBuyFundingCooldownSeconds", 1800), number(config, "MerchantBuyFundingMaxLifetimePerIdentity", 250_000_000), number(config, "MerchantBuyFundingGlobalLifetimeCap", 10_000_000_000L),
			flag(config, "MerchantMarketStockProvisionEnabled", true), number(config, "MerchantMarketStockGlobalValueCap", 10_000_000_000L), number(config, "MerchantMarketStockPerIdentityValueCap", 500_000_000), number(config, "MerchantMarketStockPerItemLifetimeCap", 1000), number(config, "MerchantMarketStockPerGradeLifetimeCap", 50000),
			integer(config, "MerchantMarketGearPerGradeLifetimeCap", 32), integer(config, "MerchantMarketStockStackCount", 10), integer(config, "MerchantMarketStockMaxEnchant", 6), integer(config, "MerchantMarketStockMaxEnchantedPerEpoch", 4), integer(config, "MerchantMarketMaxGrantsPerIdentity", 64), integer(config, "MerchantMarketMaxLifetimeGrants", 16384));
	}
	private static String text(ConfigReader config, String key, String fallback) { final String value = config.getValue(key); return value == null ? fallback : value.trim(); }
	private static long number(ConfigReader config, String key, long fallback)
	{
		final String value = text(config, key, Long.toString(fallback));
		if (!value.matches("[0-9]{1,13}")) { throw new IllegalArgumentException("Invalid merchant market number: " + key); }
		return Long.parseLong(value);
	}
	private static int integer(ConfigReader config, String key, int fallback) { return Math.toIntExact(number(config, key, fallback)); }
	private static boolean flag(ConfigReader config, String key, boolean fallback)
	{
		return switch (text(config, key, fallback ? "True" : "False")) { case "True" -> true; case "False" -> false; default -> throw new IllegalArgumentException("Invalid merchant market boolean: " + key); };
	}
}

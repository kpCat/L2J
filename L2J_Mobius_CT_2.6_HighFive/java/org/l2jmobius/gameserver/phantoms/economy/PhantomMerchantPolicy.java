/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.l2jmobius.commons.util.ConfigReader;

/** Startup-only bounded offline role; unrelated to battle materialization caps. */
public record PhantomMerchantPolicy(int targetStores, int hardCap, int lifetimeIdentities, int batchSize, int rotationSeconds, int linesPerStore, long capitalPerIdentity, long globalCapital, long globalStockValue, long perItemQuota, int stackCount, int buyPercent, int manufacturePercent, List<Integer> classIds, List<Site> sites, List<Integer> preparedOfflineIds)
{
	public static final String FILE = "config/Custom/PhantomMerchants.ini";
	public PhantomMerchantPolicy
	{
		classIds = List.copyOf(classIds); sites = List.copyOf(sites); preparedOfflineIds = List.copyOf(preparedOfflineIds);
		if (preparedOfflineIds.size() > 32 || preparedOfflineIds.stream().anyMatch(id -> id <= 0) || preparedOfflineIds.stream().distinct().count() != preparedOfflineIds.size()) { throw new IllegalArgumentException("Invalid prepared offline owner allowlist."); }
		if (targetStores < 1 || targetStores > hardCap || hardCap > 300 || lifetimeIdentities < hardCap || lifetimeIdentities > 512 || batchSize < 1 || batchSize > 10 || rotationSeconds < 15 || rotationSeconds > 3600 || linesPerStore < 1 || linesPerStore > 8 || capitalPerIdentity < 0 || capitalPerIdentity > 1_000_000_000L || globalCapital < capitalPerIdentity || globalCapital > 1_000_000_000_000L || globalStockValue < 0 || globalStockValue > 1_000_000_000_000L || perItemQuota < 1 || perItemQuota > 1_000_000 || stackCount < 1 || stackCount > 1000 || buyPercent < 0 || manufacturePercent < 0 || buyPercent + manufacturePercent > 80 || classIds.isEmpty() || classIds.size() > 8 || classIds.stream().anyMatch(id -> id < 0 || id > 136) || sites.isEmpty() || sites.size() > 8 || sites.stream().mapToInt(site -> site.columns() * site.rows()).sum() > 1000) { throw new IllegalArgumentException("Offline merchant policy outside hard bounds."); }
	}
	public record Site(int x, int y, int z, int columns, int rows, int spacing)
	{
		public Site { if (Math.abs((long) x) > 500000 || Math.abs((long) y) > 500000 || Math.abs((long) z) > 50000 || columns < 1 || columns > 20 || rows < 1 || rows > 20 || spacing < 60 || spacing > 200) { throw new IllegalArgumentException("Invalid offline merchant site."); } }
	}
	public static PhantomMerchantPolicy load(Path file)
	{
		if (!Files.isRegularFile(file)) { return null; }
		final var config = new ConfigReader(file.toString());
		if (!"True".equals(config.getValue("EnableOfflinePhantomMerchants"))) { return null; }
		final var classes = java.util.Arrays.stream(config.getValue("MerchantClassIds").split(",")).map(String::trim).map(Integer::parseInt).toList();
		final var sites = new ArrayList<Site>();
		for (String site : config.getValue("MerchantSites").split(";"))
		{
			final var parts = java.util.Arrays.stream(site.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
			if (parts.length != 6) { throw new IllegalArgumentException("Merchant site needs x,y,z,columns,rows,spacing."); }
			sites.add(new Site(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]));
		}
		final String prepared = config.getValue("MerchantPreparedOfflineCharacterIds");
		final var preparedIds = prepared == null || prepared.isBlank() ? List.<Integer>of() : java.util.Arrays.stream(prepared.split(",")).map(String::trim).map(Integer::parseInt).toList();
		return new PhantomMerchantPolicy(number(config, "MerchantTargetStores"), number(config, "MerchantHardCap"), number(config, "MerchantLifetimeIdentityLimit"), number(config, "MerchantAdmissionBatch"), number(config, "MerchantRotationSeconds"), number(config, "MerchantLinesPerStore"), value(config, "MerchantBootstrapCapitalPerIdentity"), value(config, "MerchantBootstrapGlobalCapital"), value(config, "MerchantBootstrapGlobalStockValue"), value(config, "MerchantBootstrapPerItemQuota"), number(config, "MerchantBootstrapStackCount"), number(config, "MerchantBuyPercent"), number(config, "MerchantManufacturePercent"), classes, sites, preparedIds);
	}
	private static long value(ConfigReader config, String key) { final var text = config.getValue(key); if (text == null || !text.trim().matches("[0-9]{1,13}")) { throw new IllegalArgumentException("Invalid merchant key: " + key); } return Long.parseLong(text.trim()); }
	private static int number(ConfigReader config, String key) { return Math.toIntExact(value(config, key)); }
}

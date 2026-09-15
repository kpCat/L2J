package org.l2jmobius.gameserver.config.custom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.l2jmobius.commons.util.ConfigReader;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Policy;

/** Strict startup-only operator policy for Phantom private-market price spread. */
public final class PhantomMarketConfig
{
	public static final String FILE = "./config/Custom/PhantomMarket.ini";
	private static volatile Policy _policy;

	private PhantomMarketConfig()
	{
	}

	public static void load()
	{
		_policy = read(Path.of(FILE));
	}

	public static Optional<Policy> policy()
	{
		return Optional.ofNullable(_policy);
	}

	public static Policy read(Path file)
	{
		if ((file == null) || !Files.isRegularFile(file))
		{
			return null;
		}
		try
		{
			final ConfigReader config = new ConfigReader(file.toString());
			return new Policy(integer(config.getValue("PhantomMarketBuyDiscountBasisPoints")), integer(config.getValue("PhantomMarketAskPremiumBasisPoints")));
		}
		catch (RuntimeException invalid)
		{
			return null;
		}
	}

	private static int integer(String text)
	{
		if ((text == null) || !text.trim().matches("[0-9]{1,4}"))
		{
			throw new IllegalArgumentException("Invalid Phantom market basis points.");
		}
		return Integer.parseInt(text.trim());
	}
}

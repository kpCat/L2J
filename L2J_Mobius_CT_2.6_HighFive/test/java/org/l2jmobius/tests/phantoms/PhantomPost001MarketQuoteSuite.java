package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.custom.MerchantZeroSellPriceConfig;
import org.l2jmobius.gameserver.config.custom.PhantomMarketConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Inputs;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Policy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomPrivateMarketQuote.Quote;

public final class PhantomPost001MarketQuoteSuite implements PhantomTestSuite
{
	private static final Policy POLICY = new Policy(500, 500);

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		ConfigLoader.init();
		ItemData.getInstance();
	}

	@Override
	public String id()
	{
		return "post001-market-quote";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("npc-liquidation-and-acquisition-corridor", context ->
		{
			final Quote normal = PhantomPrivateMarketQuote.quote(new Inputs(100, 50, true, 100, 0, 0, 99900000000L), POLICY);
			if (!normal.buyAllowed() || (normal.bid() < 50) || (normal.bid() >= 100) || (normal.ask() <= 50) || (normal.ask() <= normal.bid()))
			{
				throw new AssertionError("Stock NPC and Phantom corridor violated.");
			}
			final Quote capped = PhantomPrivateMarketQuote.quote(new Inputs(100, 50, true, 90, 0, 0, 99900000000L), POLICY);
			if (capped.bid() != 89)
			{
				throw new AssertionError("Integer NPC acquisition ceiling must be strict.");
			}
			final Quote conflict = PhantomPrivateMarketQuote.quote(new Inputs(100, 90, true, 90, 0, 0, 99900000000L), POLICY);
			if (conflict.buyAllowed() || (conflict.bid() != 0) || !"npc.corridor.conflict".equals(conflict.reason()))
			{
				throw new AssertionError("Impossible BUY corridor must fail closed.");
			}
			final Quote freeNpc = PhantomPrivateMarketQuote.quote(new Inputs(100, 0, true, 0, 0, 0, 99900000000L), POLICY);
			if (freeNpc.buyAllowed())
			{
				throw new AssertionError("Free ordinary NPC source cannot feed a Phantom BUY order.");
			}
		});
		registry.add("distributed-operator-spread-policy", context ->
		{
			final Policy shipped = PhantomMarketConfig.read(context.moduleRoot().resolve("dist/game/config/Custom/PhantomMarket.ini"));
			if ((shipped == null) || (shipped.buyDiscountBasisPoints() != 500) || (shipped.askPremiumBasisPoints() != 500))
			{
				throw new AssertionError("Shipped Phantom market policy differs from tested defaults.");
			}
		});
		registry.add("invalid-operator-spread-disables-quotation", context ->
		{
			final Path file = Files.createTempFile("post001-market-policy-", ".ini");
			try
			{
				Files.writeString(file, "PhantomMarketBuyDiscountBasisPoints = 2501\nPhantomMarketAskPremiumBasisPoints = 500\n");
				if (PhantomMarketConfig.read(file) != null)
				{
					throw new AssertionError("Out-of-range operator spread was accepted.");
				}
				Files.writeString(file, "PhantomMarketBuyDiscountBasisPoints = 500\n");
				if (PhantomMarketConfig.read(file) != null)
				{
					throw new AssertionError("Missing operator ASK spread silently defaulted.");
				}
			}
			finally
			{
				Files.deleteIfExists(file);
			}
		});
		registry.add("zero-liquidation-craft-enchant-floors", context ->
		{
			final Quote zero = PhantomPrivateMarketQuote.quote(new Inputs(10, 0, false, 0, 0, 0, 99900000000L), POLICY);
			if (!zero.buyAllowed() || (zero.bid() != 9) || (zero.ask() != 11))
			{
				throw new AssertionError("Zero NPC payout must not create a positive floor.");
			}
			final Quote gear = PhantomPrivateMarketQuote.quote(new Inputs(100, 50, false, 0, 200, 175, 99900000000L), POLICY);
			if ((gear.ask() < 200) || (gear.ask() <= gear.bid()))
			{
				throw new AssertionError("Craft and enchant expected inputs must anchor SELL.");
			}
		});
		registry.add("unsafe-value-and-policy-fail-closed", context ->
		{
			final Quote large = PhantomPrivateMarketQuote.quote(new Inputs(1_000_000_000L, 500_000_000L, false, 0, 0, 0, 99_900_000_000L), POLICY);
			if ((large.bid() != 950_000_000L) || (large.ask() != 1_050_000_000L))
			{
				throw new AssertionError("Bps arithmetic overflowed a legal large Adena quote.");
			}
			try
			{
				PhantomPrivateMarketQuote.quote(new Inputs(100, 50, false, 0, 100000, 0, 999), POLICY);
				throw new AssertionError("Ask beyond Adena cap must fail closed.");
			}
			catch (IllegalArgumentException expected)
			{
			}
			try
			{
				new Policy(2501, 0);
				throw new AssertionError("Undocumented spread must fail closed.");
			}
			catch (IllegalArgumentException expected)
			{
			}
		});
		registry.add("loaded-stock-merchant-payout-enchant-independent", context ->
		{
			final ItemTemplate template = ItemData.getInstance().getAllWeapons().stream().filter(candidate -> candidate.isSellable() && candidate.isEnchantable() && (candidate.getReferencePrice() > 0) && (candidate.getTime() == -1)).findFirst().orElseThrow();
			final Item item = new Item(900001, template);
			final long ordinary = item.getReferencePrice() / 2;
			if (PhantomPrivateMarketQuote.npcLiquidation(template) != ordinary)
			{
				throw new AssertionError("Phantom NPC boundary differs from stock RequestSellItem payout.");
			}
			item.setEnchantLevel(10);
			if ((item.getReferencePrice() / 2) != ordinary)
			{
				throw new AssertionError("Stock Merchant liquidation unexpectedly varies with enchant.");
			}
			final boolean old = MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE;
			try
			{
				MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE = true;
				if (PhantomPrivateMarketQuote.npcLiquidation(template) != 0)
				{
					throw new AssertionError("Custom zero-payout Merchant mode must remove positive liquidation floor.");
				}
			}
			finally
			{
				MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE = old;
			}
		});
	}
}

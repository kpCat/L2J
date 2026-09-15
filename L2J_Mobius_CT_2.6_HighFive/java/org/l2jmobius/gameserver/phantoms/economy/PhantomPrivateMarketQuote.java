package org.l2jmobius.gameserver.phantoms.economy;

import org.l2jmobius.gameserver.config.custom.MerchantZeroSellPriceConfig;
import org.l2jmobius.gameserver.model.item.ItemTemplate;

/** Integer market corridor after an independent, source-backed fair-value calculation. */
public final class PhantomPrivateMarketQuote
{
	private PhantomPrivateMarketQuote()
	{
	}

	/** Same per-unit reference-price and zero-payout boundary as stock RequestSellItem. */
	public static long npcLiquidation(ItemTemplate item)
	{
		return (item == null) || !item.isSellable() || MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE ? 0 : Math.max(0, item.getReferencePrice() / 2);
	}

	public static Quote quote(Inputs input, Policy policy)
	{
		if ((input == null) || (policy == null) || (input.fairValue() <= 0) || (input.fairValue() > input.maxAdena()) || (input.npcLiquidation() < 0) || (input.npcLiquidation() > input.maxAdena()) || (input.npcAcquisition() < 0) || (input.npcAcquisition() > input.maxAdena()) || (input.craftFloor() < 0) || (input.craftFloor() > input.maxAdena()) || (input.enchantFloor() < 0) || (input.enchantFloor() > input.maxAdena()) || (input.maxAdena() < 2))
		{
			throw new IllegalArgumentException("Unsafe private-market input.");
		}
		final long bidByFair = Math.max(1, ratioFloor(input.fairValue(), 10000 - policy.buyDiscountBasisPoints()));
		final long floor = Math.max(input.npcLiquidation(), bidByFair);
		final boolean buyAllowed = !input.npcAcquisitionExists() || (input.npcLiquidation() < input.npcAcquisition());
		final long bid = buyAllowed ? (!input.npcAcquisitionExists() ? floor : Math.min(floor, input.npcAcquisition() - 1)) : 0;
		if (buyAllowed && (bid < input.npcLiquidation()))
		{
			throw new IllegalArgumentException("Liquidation corridor violated.");
		}
		final long askByFair = ratioCeil(input.fairValue(), 10000 + policy.askPremiumBasisPoints());
		final long askFloor = Math.max(Math.max(askByFair, input.craftFloor()), Math.max(input.enchantFloor(), Math.max(input.npcLiquidation() + 1, bid + 1)));
		if ((askFloor <= 0) || (askFloor > input.maxAdena()))
		{
			throw new IllegalArgumentException("No safe private-market ask.");
		}
		return new Quote(buyAllowed, bid, askFloor, buyAllowed ? "priced" : "npc.corridor.conflict");
	}

	private static long ratioFloor(long value, int basisPoints)
	{
		return Math.addExact(Math.multiplyExact(value / 10000, basisPoints), Math.multiplyExact(value % 10000, basisPoints) / 10000);
	}

	private static long ratioCeil(long value, int basisPoints)
	{
		return Math.addExact(Math.multiplyExact(value / 10000, basisPoints), Math.addExact(Math.multiplyExact(value % 10000, basisPoints), 9999) / 10000);
	}

	public record Inputs(long fairValue, long npcLiquidation, boolean npcAcquisitionExists, long npcAcquisition, long craftFloor, long enchantFloor, long maxAdena)
	{
	}

	public record Policy(int buyDiscountBasisPoints, int askPremiumBasisPoints)
	{
		public Policy
		{
			if ((buyDiscountBasisPoints < 0) || (buyDiscountBasisPoints > 2500) || (askPremiumBasisPoints < 0) || (askPremiumBasisPoints > 2500))
			{
				throw new IllegalArgumentException("Invalid private-market spread policy.");
			}
		}
	}

	public record Quote(boolean buyAllowed, long bid, long ask, String reason)
	{
	}
}

package org.l2jmobius.gameserver.phantoms.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.data.xml.BuyListData;
import org.l2jmobius.gameserver.model.buylist.BuyListHolder;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.buylist.Product;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.BuyOffer;

/** Actual stock NPC per-unit purchase quote over visible eligible Merchant instances. */
public final class PhantomNpcAcquisitionPrice
{
	private PhantomNpcAcquisitionPrice()
	{
	}

	public static Quote quote(int itemId, PhantomCommerceCatalog commerce, Collection<WorldObject> visibleObjects)
	{
		if ((itemId <= 0) || (commerce == null) || (visibleObjects == null))
		{
			throw new IllegalArgumentException("Unsafe NPC acquisition request.");
		}
		final List<BuyOffer> offers = new ArrayList<>();
		for (int offset = 0; offset < 1024; offset += PhantomCommerceCatalog.MAX_PAGE_SIZE)
		{
			final var page = commerce.findBuyOffers(itemId, offset, PhantomCommerceCatalog.MAX_PAGE_SIZE);
			offers.addAll(page.values());
			if (!page.hasMore())
			{
				break;
			}
			if (offset + PhantomCommerceCatalog.MAX_PAGE_SIZE >= 1024)
			{
				return new Quote(true, false, 0, "npc.offer.page.limit");
			}
		}
		if (offers.isEmpty())
		{
			return new Quote(false, true, 0, "npc.source.absent");
		}
		long minimum = Long.MAX_VALUE;
		boolean plausible = false;
		for (BuyOffer offer : offers)
		{
			final BuyListHolder buyList = BuyListData.getInstance().getBuyList(offer.listId());
			final Product product = buyList == null ? null : buyList.getProductByItemId(itemId);
			if ((product == null) || (product.hasLimitedStock() && (product.getCount() <= 0)))
			{
				continue;
			}
			long raw = product.getPrice();
			if ((raw == 0) && GeneralConfig.ONLY_GM_ITEMS_FREE)
			{
				continue;
			}
			if (raw < 0)
			{
				continue;
			}
			plausible = true;
			if ((itemId >= 3960) && (itemId <= 4026))
			{
				raw = (long) (raw * RatesConfig.RATE_SIEGE_GUARDS_PRICE);
			}
			for (WorldObject object : visibleObjects)
			{
				if ((object instanceof Merchant merchant) && offer.npcIds().contains(merchant.getId()) && buyList.isNpcAllowed(merchant.getId()) && (merchant.getMpc() != null))
				{
					final double tax = 1 + merchant.getMpc().getCastleTaxRate() + merchant.getMpc().getBaseTaxRate();
					if (Double.isFinite(tax) && (tax >= 0) && (tax <= 10) && (raw <= (Long.MAX_VALUE / tax)))
					{
						minimum = Math.min(minimum, (long) (raw * tax));
					}
				}
			}
		}
		if (minimum != Long.MAX_VALUE)
		{
			return new Quote(true, true, minimum, "npc.stock.merchant");
		}
		return plausible ? new Quote(true, false, 0, "npc.visible.merchant.unresolved") : new Quote(false, true, 0, "npc.ordinary.source.absent");
	}

	public record Quote(boolean exists, boolean exact, long price, String reason)
	{
	}
}

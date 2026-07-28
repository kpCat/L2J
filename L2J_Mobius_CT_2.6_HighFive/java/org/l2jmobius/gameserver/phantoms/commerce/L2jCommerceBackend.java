/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.MerchantZeroSellPriceConfig;
import org.l2jmobius.gameserver.data.xml.BuyListData;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportType;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.buylist.BuyListHolder;
import org.l2jmobius.gameserver.model.buylist.Product;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.teleporter.TeleportHolder;
import org.l2jmobius.gameserver.model.teleporter.TeleportLocation;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.BuyOffer;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.TeleportRoute;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.ConservationFacts;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationRequest;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.ActorFacts;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.ActorLease;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.OperationIntent;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.Quote;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.Reason;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;

/**
 * Canonical High Five Player/inventory/teleport side effects. The action lease
 * protects Phantom materialization lifecycle, but does not claim to serialize
 * unrelated server writers.
 */
public final class L2jCommerceBackend implements PhantomCommerceService.Backend
{
	private final PhantomMaterializationService _materializationService;
	private final PhantomCommerceCatalog _catalog;
	private final Clock _clock;

	public L2jCommerceBackend(PhantomMaterializationService materializationService, PhantomCommerceCatalog catalog, Clock clock)
	{
		_materializationService = Objects.requireNonNull(materializationService);
		_catalog = Objects.requireNonNull(catalog);
		_clock = Objects.requireNonNull(clock);
	}

	@Override
	public Optional<ActorLease> tryAcquire(long profileId)
	{
		return _materializationService.tryAcquireAction(profileId).map(lease -> new L2jActorLease(lease, _catalog, _clock));
	}

	private static final class L2jActorLease implements ActorLease
	{
		private final ActionLease _lease;
		private final Player _player;
		private final PhantomCommerceCatalog _catalog;
		private final Clock _clock;

		private L2jActorLease(ActionLease lease, PhantomCommerceCatalog catalog, Clock clock)
		{
			_lease = lease;
			_player = lease.player();
			_catalog = catalog;
			_clock = clock;
		}

		@Override
		public Quote quote(OperationIntent intent)
		{
			return switch (intent.kind())
			{
				case BUY -> quoteBuy(intent);
				case SELL -> quoteSell(intent);
				case TELEPORT -> quoteTeleport(intent);
			};
		}

		private Quote quoteBuy(OperationIntent intent)
		{
			final Merchant merchant = merchant(intent);
			if (merchant == null)
			{
				return Quote.rejected(npcReason(intent, Merchant.class));
			}
			final Reason actorReason = validateActor(merchant, false);
			if (actorReason != Reason.ACCEPTED)
			{
				return Quote.rejected(actorReason);
			}
			if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_SHOP && (_player.getKarma() > 0))
			{
				return Quote.rejected(Reason.INVALID_ACTOR_STATE);
			}
			final BuyListHolder list = BuyListData.getInstance().getBuyList(intent.listId());
			if ((list == null) || !list.isNpcAllowed(merchant.getId()))
			{
				return Quote.rejected(Reason.OFFER_NOT_FOUND);
			}
			final Product product = list.getProductByItemId(intent.itemId());
			final BuyOffer offer = findBuyOffer(intent);
			if ((product == null) || (offer == null))
			{
				return Quote.rejected(Reason.OFFER_NOT_FOUND);
			}
			if (product.hasLimitedStock() || offer.limitedStock())
			{
				return Quote.rejected(Reason.LIMITED_STOCK_UNSUPPORTED);
			}
			if (product.getPrice() != offer.price())
			{
				return Quote.rejected(Reason.PRICE_CHANGED);
			}
			if ((intent.count() <= 0) || (!product.getItem().isStackable() && (intent.count() != 1)))
			{
				return Quote.rejected(Reason.INVALID_REQUEST);
			}
			if ((product.getPrice() == 0) && GeneralConfig.ONLY_GM_ITEMS_FREE)
			{
				return Quote.rejected(Reason.PRICE_CHANGED);
			}
			final double castleTaxRate = merchant.getMpc().getCastleTaxRate();
			if (castleTaxRate != 0)
			{
				return Quote.rejected(Reason.CASTLE_TREASURY_UNSUPPORTED);
			}
			long price = product.getPrice();
			if ((product.getItemId() >= 3960) && (product.getItemId() <= 4026))
			{
				price = (long) (price * RatesConfig.RATE_SIEGE_GUARDS_PRICE);
			}
			price = (long) (price * (1 + merchant.getMpc().getBaseTaxRate()));
			final long total = Math.multiplyExact(price, intent.count());
			if ((total < 0) || (total > Inventory.MAX_ADENA) || ((intent.expenseBudget() > 0) && (total > intent.expenseBudget())))
			{
				return Quote.rejected(Reason.BUDGET_EXCEEDED);
			}
			if (_player.getAdena() < total)
			{
				return Quote.rejected(Reason.INSUFFICIENT_FUNDS);
			}
			final long weight = Math.multiplyExact((long) product.getItem().getWeight(), intent.count());
			if (!_player.getInventory().validateWeight(weight))
			{
				return Quote.rejected(Reason.WEIGHT_LIMIT);
			}
			if ((_player.getInventory().getItemByItemId(product.getItemId()) == null) && !_player.getInventory().validateCapacity(1))
			{
				return Quote.rejected(Reason.CAPACITY_LIMIT);
			}
			final OperationRequest request = new OperationRequest(OperationKind.BUY, merchant.getId(), merchant.getObjectId(), intent.listId(), intent.itemId(), 0, intent.count(), total, 0, 0, 0, "", 0, 0, 0);
			final ConservationFacts before = snapshot(request);
			final ConservationFacts after = new ConservationFacts(Math.subtractExact(before.primaryCount(), total), Math.addExact(before.secondaryCount(), intent.count()), Math.addExact(before.objectCount(), intent.count()), before.instanceId(), before.x(), before.y(), before.z());
			return Quote.accepted(request, before, after, actorFacts(intent.itemId(), 0));
		}

		private Quote quoteSell(OperationIntent intent)
		{
			final Merchant merchant = merchant(intent);
			if (merchant == null)
			{
				return Quote.rejected(npcReason(intent, Merchant.class));
			}
			final Reason actorReason = validateActor(merchant, false);
			if (actorReason != Reason.ACCEPTED)
			{
				return Quote.rejected(actorReason);
			}
			if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_SHOP && (_player.getKarma() > 0))
			{
				return Quote.rejected(Reason.INVALID_ACTOR_STATE);
			}
			if (GeneralConfig.ALLOW_REFUND)
			{
				return Quote.rejected(Reason.REFUND_UNSUPPORTED);
			}
			if (MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE)
			{
				return Quote.rejected(Reason.ZERO_SELL_PRICE_UNSUPPORTED);
			}
			final BuyListHolder list = BuyListData.getInstance().getBuyList(intent.listId());
			if ((list == null) || !list.isNpcAllowed(merchant.getId()))
			{
				return Quote.rejected(Reason.OFFER_NOT_FOUND);
			}
			final Item item = _player.checkItemManipulation(intent.itemObjectId(), intent.count(), "phantom-sell");
			if ((item == null) || (item.getId() != intent.itemId()))
			{
				return Quote.rejected(Reason.ITEM_NOT_OWNED);
			}
			if (!item.isSellable())
			{
				return Quote.rejected(Reason.ITEM_NOT_SELLABLE);
			}
			final long unitPrice = item.getReferencePrice() / 2L;
			if (unitPrice <= 0)
			{
				return Quote.rejected(Reason.ZERO_SELL_PRICE_UNSUPPORTED);
			}
			final long refund = Math.multiplyExact(unitPrice, intent.count());
			if ((refund > Inventory.MAX_ADENA) || (_player.getAdena() > (Inventory.MAX_ADENA - refund)))
			{
				return Quote.rejected(Reason.BUDGET_EXCEEDED);
			}
			final OperationRequest request = new OperationRequest(OperationKind.SELL, merchant.getId(), merchant.getObjectId(), intent.listId(), intent.itemId(), intent.itemObjectId(), intent.count(), refund, 0, 0, 0, "", 0, 0, 0);
			final ConservationFacts before = snapshot(request);
			final ConservationFacts after = new ConservationFacts(Math.addExact(before.primaryCount(), refund), Math.subtractExact(before.secondaryCount(), intent.count()), Math.subtractExact(before.objectCount(), intent.count()), before.instanceId(), before.x(), before.y(), before.z());
			return Quote.accepted(request, before, after, actorFacts(intent.itemId(), intent.itemObjectId()));
		}

		private Quote quoteTeleport(OperationIntent intent)
		{
			final Npc npc = npc(intent);
			if (npc == null)
			{
				return Quote.rejected(npcReason(intent, Npc.class));
			}
			final Reason actorReason = validateActor(npc, true);
			if (actorReason != Reason.ACCEPTED)
			{
				return Quote.rejected(actorReason);
			}
			final TeleportHolder holder = TeleporterData.getInstance().getHolder(npc.getId(), intent.listName());
			final TeleportRoute route = findTeleportRoute(intent);
			if ((holder == null) || (route == null) || (intent.ordinal() >= holder.getLocations().size()))
			{
				return Quote.rejected(Reason.TELEPORT_NOT_FOUND);
			}
			if ((holder.getType() != TeleportType.NORMAL) || (route.type() != TeleportType.NORMAL))
			{
				return Quote.rejected(Reason.TELEPORT_TYPE_UNSUPPORTED);
			}
			final TeleportLocation location = holder.getLocations().get(intent.ordinal());
			final Reason restriction = teleportRestriction(_player, npc, holder, location);
			if (restriction != Reason.ACCEPTED)
			{
				return Quote.rejected(restriction);
			}
			final long fee = teleportFee(_player, holder, location, _clock);
			if ((intent.expenseBudget() > 0) && (fee > intent.expenseBudget()))
			{
				return Quote.rejected(Reason.BUDGET_EXCEEDED);
			}
			if ((fee > 0) && (itemCount(location.getFeeId()) < fee))
			{
				return Quote.rejected(Reason.TELEPORT_FEE_UNAVAILABLE);
			}
			final OperationRequest request = new OperationRequest(OperationKind.TELEPORT, npc.getId(), npc.getObjectId(), 0, 0, 0, 0, 0, location.getFeeId(), fee, intent.ordinal(), intent.listName(), location.getX(), location.getY(), location.getZ());
			final ConservationFacts before = snapshot(request);
			final ConservationFacts after = new ConservationFacts(Math.subtractExact(before.primaryCount(), fee), before.secondaryCount(), before.objectCount(), location.getInstanceId(), location.getX(), location.getY(), location.getZ());
			return Quote.accepted(request, before, after, actorFacts(0, 0));
		}

		@Override
		public ConservationFacts snapshot(OperationRequest request)
		{
			final long primary = request.kind() == OperationKind.TELEPORT ? itemCount(request.feeItemId()) : _player.getAdena();
			final long secondary = request.itemId() == 0 ? 0 : itemCount(request.itemId());
			final Item object = request.itemObjectId() == 0 ? null : _player.getInventory().getItemByObjectId(request.itemObjectId());
			final long objectCount = request.kind() == OperationKind.BUY ? secondary : object == null ? 0 : object.getCount();
			return new ConservationFacts(primary, secondary, objectCount, _player.getInstanceId(), _player.getX(), _player.getY(), _player.getZ());
		}

		@Override
		public boolean applyFirst(OperationRequest request)
		{
			return switch (request.kind())
			{
				case BUY -> applyBuyPayment(request);
				case SELL -> applySellRemoval(request);
				case TELEPORT -> applyTeleportFee(request);
			};
		}

		@Override
		public boolean applySecond(OperationRequest request)
		{
			return switch (request.kind())
			{
				case BUY -> applyBuyOutput(request);
				case SELL -> applySellRefund(request);
				case TELEPORT -> applyTeleportDestination(request);
			};
		}

		private boolean applyBuyPayment(OperationRequest request)
		{
			final Merchant merchant = merchant(request);
			final Product product = buyProduct(request, merchant);
			if ((product == null) || product.hasLimitedStock() || !buyAmountMatches(request, merchant, product) || (merchant.getMpc().getCastleTaxRate() != 0))
			{
				return false;
			}
			return _player.reduceAdena(ItemProcessType.BUY, request.amount(), merchant, false);
		}

		private boolean applyBuyOutput(OperationRequest request)
		{
			final Merchant merchant = merchant(request);
			final Product product = buyProduct(request, merchant);
			if ((product == null) || product.hasLimitedStock() || !buyAmountMatches(request, merchant, product))
			{
				return false;
			}
			final long weight = Math.multiplyExact((long) product.getItem().getWeight(), request.count());
			if (!_player.getInventory().validateWeight(weight) || ((_player.getInventory().getItemByItemId(request.itemId()) == null) && !_player.getInventory().validateCapacity(1)))
			{
				return false;
			}
			return _player.getInventory().addItem(ItemProcessType.BUY, request.itemId(), request.count(), _player, merchant) != null;
		}

		private boolean applySellRemoval(OperationRequest request)
		{
			final Merchant merchant = merchant(request);
			if ((merchant == null) || GeneralConfig.ALLOW_REFUND || MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE)
			{
				return false;
			}
			final BuyListHolder list = BuyListData.getInstance().getBuyList(request.listId());
			final Item item = _player.checkItemManipulation(request.itemObjectId(), request.count(), "phantom-sell");
			if ((list == null) || !list.isNpcAllowed(merchant.getId()) || (item == null) || (item.getId() != request.itemId()) || !item.isSellable() || (Math.multiplyExact(item.getReferencePrice() / 2L, request.count()) != request.amount()))
			{
				return false;
			}
			return _player.getInventory().destroyItem(ItemProcessType.SELL, request.itemObjectId(), request.count(), _player, merchant) != null;
		}

		private boolean applySellRefund(OperationRequest request)
		{
			final Merchant merchant = merchant(request);
			if ((merchant == null) || (request.amount() <= 0) || (_player.getAdena() > (Inventory.MAX_ADENA - request.amount())))
			{
				return false;
			}
			final BuyListHolder list = BuyListData.getInstance().getBuyList(request.listId());
			if ((list == null) || !list.isNpcAllowed(merchant.getId()))
			{
				return false;
			}
			_player.addAdena(ItemProcessType.SELL, request.amount(), merchant, false);
			return true;
		}

		private boolean applyTeleportFee(OperationRequest request)
		{
			final Npc npc = npc(request);
			final TeleportLocation location = teleportLocation(request, npc);
			if ((location == null) || (teleportRestriction(_player, npc, TeleporterData.getInstance().getHolder(npc.getId(), request.listName()), location) != Reason.ACCEPTED) || (teleportFee(_player, TeleporterData.getInstance().getHolder(npc.getId(), request.listName()), location, _clock) != request.feeCount()))
			{
				return false;
			}
			return (request.feeCount() == 0) || _player.destroyItemByItemId(ItemProcessType.FEE, request.feeItemId(), request.feeCount(), npc, true);
		}

		private boolean applyTeleportDestination(OperationRequest request)
		{
			final Npc npc = npc(request);
			final TeleportLocation location = teleportLocation(request, npc);
			if ((location == null) || (teleportRestriction(_player, npc, TeleporterData.getInstance().getHolder(npc.getId(), request.listName()), location) != Reason.ACCEPTED))
			{
				return false;
			}
			_player.teleToLocation(location);
			return true;
		}

		private BuyOffer findBuyOffer(OperationIntent intent)
		{
			return _catalog.findBuyOffers(intent.itemId(), 0, PhantomCommerceCatalog.MAX_PAGE_SIZE).values().stream().filter(offer -> (offer.listId() == intent.listId()) && offer.npcIds().contains(intent.npcTemplateId())).findFirst().orElse(null);
		}

		private TeleportRoute findTeleportRoute(OperationIntent intent)
		{
			return _catalog.findTeleportRoutes(intent.npcTemplateId(), 0, PhantomCommerceCatalog.MAX_PAGE_SIZE).values().stream().filter(route -> route.listName().equals(intent.listName()) && (route.ordinal() == intent.ordinal())).findFirst().orElse(null);
		}

		private Product buyProduct(OperationRequest request, Merchant merchant)
		{
			if ((merchant == null) || (validateActor(merchant, false) != Reason.ACCEPTED))
			{
				return null;
			}
			final BuyListHolder list = BuyListData.getInstance().getBuyList(request.listId());
			if ((list == null) || !list.isNpcAllowed(merchant.getId()))
			{
				return null;
			}
			final Product product = list.getProductByItemId(request.itemId());
			if (product == null)
			{
				return null;
			}
			final BuyOffer offer = _catalog.findBuyOffers(request.itemId(), 0, PhantomCommerceCatalog.MAX_PAGE_SIZE).values().stream().filter(value -> (value.listId() == request.listId()) && value.npcIds().contains(request.npcTemplateId())).findFirst().orElse(null);
			return (offer != null) && (offer.price() == product.getPrice()) ? product : null;
		}

		private static boolean buyAmountMatches(OperationRequest request, Merchant merchant, Product product)
		{
			long price = product.getPrice();
			if ((product.getItemId() >= 3960) && (product.getItemId() <= 4026))
			{
				price = (long) (price * RatesConfig.RATE_SIEGE_GUARDS_PRICE);
			}
			price = (long) (price * (1 + merchant.getMpc().getBaseTaxRate()));
			return Math.multiplyExact(price, request.count()) == request.amount();
		}

		private Merchant merchant(OperationIntent intent)
		{
			final Npc npc = npc(intent);
			return npc instanceof Merchant value ? value : null;
		}

		private Merchant merchant(OperationRequest request)
		{
			final Npc npc = npc(request);
			return npc instanceof Merchant value ? value : null;
		}

		private Npc npc(OperationIntent intent)
		{
			return npc(intent.npcTemplateId(), intent.npcObjectId());
		}

		private Npc npc(OperationRequest request)
		{
			return npc(request.npcTemplateId(), request.npcObjectId());
		}

		private Npc npc(int templateId, int objectId)
		{
			final WorldObject target = _player.getTarget();
			if (!(target instanceof Npc npc) || (npc.getId() != templateId) || (npc.getObjectId() != objectId))
			{
				return null;
			}
			final Npc lastFolk = _player.getLastFolkNPC();
			return (lastFolk != null) && (lastFolk.getObjectId() == objectId) ? npc : null;
		}

		private Reason npcReason(OperationIntent intent, Class<?> expectedClass)
		{
			final WorldObject target = _player.getTarget();
			if (!(target instanceof Npc))
			{
				return Reason.NPC_NOT_FOUND;
			}
			if ((target.getObjectId() != intent.npcObjectId()) || (((Npc) target).getId() != intent.npcTemplateId()))
			{
				return Reason.NPC_IDENTITY_MISMATCH;
			}
			return expectedClass.isInstance(target) ? Reason.NPC_NOT_FOUND : Reason.NPC_TYPE_MISMATCH;
		}

		private Reason validateActor(Npc npc, boolean teleport)
		{
			if ((_player.getInstanceId() != npc.getInstanceId()))
			{
				return Reason.INSTANCE_MISMATCH;
			}
			if (!_player.isInsideRadius3D(npc, Npc.INTERACTION_DISTANCE))
			{
				return Reason.NPC_OUT_OF_RANGE;
			}
			if (_player.isAlikeDead() || _player.isCastingNow() || _player.isMoving() || _player.isTeleporting() || (!teleport && _player.isInCombat()))
			{
				return Reason.INVALID_ACTOR_STATE;
			}
			return Reason.ACCEPTED;
		}

		private static Reason teleportRestriction(Player player, Npc npc, TeleportHolder holder, TeleportLocation location)
		{
			if ((holder == null) || (holder.getType() != TeleportType.NORMAL))
			{
				return Reason.TELEPORT_TYPE_UNSUPPORTED;
			}
			if (holder.isNoblesse() && !player.isNoble())
			{
				return Reason.TELEPORT_RESTRICTED;
			}
			if (!PlayerConfig.TELEPORT_WHILE_SIEGE_IN_PROGRESS)
			{
				for (int castleId : location.getCastleId())
				{
					final var castle = CastleManager.getInstance().getCastleById(castleId);
					if ((castle != null) && castle.getSiege().isInProgress())
					{
						return Reason.TELEPORT_RESTRICTED;
					}
				}
				if (npc.getCastle().getSiege().isInProgress())
				{
					return Reason.TELEPORT_RESTRICTED;
				}
			}
			if ((!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_USE_GK && (player.getKarma() > 0)) || player.isCombatFlagEquipped())
			{
				return Reason.TELEPORT_RESTRICTED;
			}
			return Reason.ACCEPTED;
		}

		private static long teleportFee(Player player, TeleportHolder holder, TeleportLocation location, Clock clock)
		{
			if ((holder.getType() == TeleportType.NORMAL) && !player.isSubClassActive() && (player.getLevel() <= PlayerConfig.MAX_FREE_TELEPORT_LEVEL))
			{
				return 0;
			}
			if ((location.getFeeId() == 0) || (location.getFeeCount() <= 0))
			{
				return 0;
			}
			final ZonedDateTime now = ZonedDateTime.now(clock);
			final DayOfWeek day = now.getDayOfWeek();
			if ((holder.getType() == TeleportType.NORMAL) && (now.getHour() >= 20) && ((day == DayOfWeek.MONDAY) || (day == DayOfWeek.TUESDAY)))
			{
				return location.getFeeCount() / 2;
			}
			return location.getFeeCount();
		}

		private TeleportLocation teleportLocation(OperationRequest request, Npc npc)
		{
			if (npc == null)
			{
				return null;
			}
			final TeleportHolder holder = TeleporterData.getInstance().getHolder(npc.getId(), request.listName());
			if ((holder == null) || (holder.getType() != TeleportType.NORMAL) || (request.ordinal() >= holder.getLocations().size()))
			{
				return null;
			}
			final TeleportLocation location = holder.getLocations().get(request.ordinal());
			return (location.getX() == request.destinationX()) && (location.getY() == request.destinationY()) && (location.getZ() == request.destinationZ()) && (location.getFeeId() == request.feeItemId()) ? location : null;
		}

		private long itemCount(int itemId)
		{
			return itemId <= 0 ? 0 : _player.getInventory().getInventoryItemCount(itemId, -1);
		}

		private ActorFacts actorFacts(int itemId, int objectId)
		{
			final Item object = objectId <= 0 ? null : _player.getInventory().getItemByObjectId(objectId);
			final WorldObject target = _player.getTarget();
			final Npc lastFolk = _player.getLastFolkNPC();
			return new ActorFacts(_player.getAdena(), itemCount(itemId), object == null ? 0 : object.getCount(), _player.getCurrentLoad(), _player.getMaxLoad(), _player.getClassIndex(), _player.isNoble(), _player.getKarma(), _player.isAlikeDead(), _player.isInCombat(), _player.isCastingNow(), _player.isMoving(), _player.isTeleporting(), _player.getInstanceId(), _player.getX(), _player.getY(), _player.getZ(), target == null ? 0 : target.getObjectId(), lastFolk == null ? 0 : lastFolk.getObjectId());
		}

		@Override
		public void close()
		{
			_lease.close();
		}
	}
}

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.config.custom.MerchantZeroSellPriceConfig;
import org.l2jmobius.gameserver.data.xml.BuyListData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.World;
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
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
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
	private final ThreadLocal<BoundaryActor> _boundary = new ThreadLocal<>();
	private record BoundaryActor(long profileId, Player player, org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope owner) { }

	public L2jCommerceBackend(PhantomMaterializationService materializationService, PhantomCommerceCatalog catalog, Clock clock)
	{
		_materializationService = Objects.requireNonNull(materializationService);
		_catalog = Objects.requireNonNull(catalog);
		_clock = Objects.requireNonNull(clock);
	}

	@Override
	public Optional<ActorLease> tryAcquire(long profileId)
	{
		final var boundary = _boundary.get();
		if (boundary != null && boundary.profileId() == profileId && boundary.owner().isCurrent() && boundary.owner().temporaryCheckpoint() && boundary.owner().nativeObservationHealthy()
			&& World.getInstance().getPlayer(boundary.player().getObjectId()) == boundary.player())
		{
			return Optional.of(new L2jActorLease(profileId, boundary.player(), boundary.owner(), _catalog, _clock));
		}
		return _materializationService.tryAcquireAction(profileId).map(lease -> new L2jActorLease(profileId, lease, _catalog, _clock));
	}

	@Override
	public PhantomCommerceService.OperationResult withNativeBoundary(long profileId, OperationIntent intent, java.util.function.Supplier<PhantomCommerceService.OperationResult> operation)
	{
		if (intent.kind() == OperationKind.TELEPORT) { return operation.get(); }
		final var entry = _materializationService.find(profileId).orElse(null);
		final var player = entry == null ? null : World.getInstance().getPlayer(entry.characterObjectId());
		if (player == null || !entry.actionAdmissionOpen() || !(player.getNativeWorkOwner() instanceof org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope owner)
			|| !owner.isCurrent() || !owner.open() || !owner.nativeObservationHealthy()) { return PhantomCommerceService.OperationResult.retry(Reason.ACTOR_NOT_MATERIALIZED); }
		if (org.l2jmobius.gameserver.model.actor.PlayerNativeWork.current(owner) != null) { return intent.kind() == OperationKind.BUY ? operation.get() : PhantomCommerceService.OperationResult.retry(Reason.OPERATION_BUSY); }
		return owner.checkpoint(() ->
		{
			final var previous = _boundary.get();
			_boundary.set(new BoundaryActor(profileId, player, owner));
			try { return operation.get(); }
			finally { if (previous == null) { _boundary.remove(); } else { _boundary.set(previous); } }
		});
	}

	@Override
	public void failClosed(long profileId, Reason reason)
	{
		final var boundary = _boundary.get();
		if (boundary != null && boundary.profileId() == profileId && boundary.owner().isCurrent())
		{
			boundary.owner().recordFailure(new IllegalStateException("COMMERCE_LEDGER_INCONSISTENT:" + reason));
		}
	}

	private static final class L2jActorLease implements ActorLease
	{
		private final ActionLease _lease;
		private final Player _player;
		private final PhantomCommerceCatalog _catalog;
		private final Clock _clock;
		private final long _profileId;
		private PhantomEconomyConflictPort.Claim _economyClaim;
		private org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope _checkpointOwner;

		private L2jActorLease(long profileId, ActionLease lease, PhantomCommerceCatalog catalog, Clock clock)
		{
			_profileId = profileId;
			_lease = lease;
			_player = lease.player();
			_catalog = catalog;
			_clock = clock;
		}

		private L2jActorLease(long profileId, Player player, org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope owner, PhantomCommerceCatalog catalog, Clock clock)
		{
			_profileId = profileId; _lease = null; _player = player; _checkpointOwner = owner; _catalog = catalog; _clock = clock;
		}

		@Override
		public Quote quote(OperationIntent intent)
		{
			return switch (intent.kind())
			{
				case BUY -> quoteBuy(intent);
				case SELL -> quoteSell(intent);
				case TELEPORT -> quoteTeleport(intent);
				case WAREHOUSE_DEPOSIT, WAREHOUSE_WITHDRAW -> quoteWarehouse(intent);
				case CRYSTALLIZE -> quoteCrystallize(intent);
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
			if ((product == null) || (offer == null) || !offer.npcIds().contains(intent.npcTemplateId()))
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
			if (_checkpointOwner != null && !claimEconomyWriter(request)) { return Quote.rejected(Reason.OPERATION_BUSY); }
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
			final OperationRequest request = new OperationRequest(OperationKind.SELL, merchant.getId(), merchant.getObjectId(), intent.listId(), intent.itemId(), intent.itemObjectId(), intent.count(), refund, 0, GeneralConfig.ALLOW_REFUND ? 1 : 0, 0, "", 0, 0, 0);
			if (_checkpointOwner != null && !claimEconomyWriter(request)) { return Quote.rejected(Reason.OPERATION_BUSY); }
			final ConservationFacts before = snapshot(request);
			final ConservationFacts after = new ConservationFacts(Math.addExact(before.primaryCount(), refund), Math.subtractExact(before.secondaryCount(), intent.count()), Math.subtractExact(before.objectCount(), intent.count()), before.instanceId(), before.x(), before.y(), before.z());
			return Quote.accepted(request, before, after, actorFacts(intent.itemId(), intent.itemObjectId()));
		}

		private Quote quoteWarehouse(OperationIntent intent)
		{
			final var manager = npc(intent);
			if (manager == null || !manager.isWarehouse()) { return Quote.rejected(Reason.NPC_TYPE_MISMATCH); }
			final var actorReason = validateActor(manager, false);
			if (actorReason != Reason.ACCEPTED || warehouseBusy()) { return Quote.rejected(actorReason == Reason.ACCEPTED ? Reason.INVALID_ACTOR_STATE : actorReason); }
			final boolean deposit = intent.kind() == OperationKind.WAREHOUSE_DEPOSIT;
			final var source = deposit ? _player.getInventory() : _player.getWarehouse();
			final var item = source.getItemByObjectId(intent.itemObjectId());
			if (item == null || item.getId() != intent.itemId() || intent.count() <= 0 || item.getCount() < intent.count() || item.isEquipped() || item.getOwnerId() != _player.getObjectId()) { return Quote.rejected(Reason.ITEM_NOT_OWNED); }
			if (deposit && (!item.isDepositable(true) || !item.isAvailable(_player, true, true))) { return Quote.rejected(Reason.ITEM_NOT_SELLABLE); }
			if (!warehouseFits(item, intent.count(), deposit)) { return Quote.rejected(Reason.CAPACITY_LIMIT); }
			final long fee = deposit ? 30 : 0;
			if (deposit && (_player.getAdena() < fee || item.getId() == Inventory.ADENA_ID && _player.getAdena() - intent.count() < fee)) { return Quote.rejected(Reason.INSUFFICIENT_FUNDS); }
			// Maintenance never banks Adena: the receipt keeps fee and stock independently conserved.
			if (item.getId() == Inventory.ADENA_ID) { return Quote.rejected(Reason.INVALID_REQUEST); }
			final var request = new OperationRequest(intent.kind(), manager.getId(), manager.getObjectId(), 0, item.getId(), item.getObjectId(), intent.count(), fee, 0, 0, 0, "", 0, 0, 0);
			if (_checkpointOwner != null && !claimEconomyWriter(request)) { return Quote.rejected(Reason.OPERATION_BUSY); }
			final var before = snapshot(request);
			final var after = new ConservationFacts(before.primaryCount() - fee, deposit ? before.secondaryCount() - intent.count() : Math.addExact(before.secondaryCount(), intent.count()), deposit ? Math.addExact(before.objectCount(), intent.count()) : before.objectCount() - intent.count(), before.instanceId(), before.x(), before.y(), before.z());
			return Quote.accepted(request, before, after, actorFacts(item.getId(), item.getObjectId()));
		}

		private boolean warehouseBusy()
		{
			return _player.isProcessingTransaction() || _player.isInStoreMode() || _player.hasPendingOwnedStore() || _player.isCrafting() || _player.isInCrystallize() || _player.getActiveEnchantItemId() != Player.ID_NONE
				|| !PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE && _player.getKarma() > 0;
		}

		private boolean warehouseFits(Item item, long count, boolean deposit)
		{
			final var target = deposit ? _player.getWarehouse() : _player.getInventory();
			final long slots = item.isStackable() ? target.getItemByItemId(item.getId()) == null ? 1 : 0 : count;
			return target.validateCapacity(slots) && (deposit || target.validateWeight(Math.multiplyExact((long) item.getTemplate().getWeight(), count)));
		}

		private Quote quoteCrystallize(OperationIntent intent)
		{
			if (intent.npcTemplateId() != 0 || intent.npcObjectId() != 0 || intent.count() != 1 || warehouseBusy() || _player.isAlikeDead() || _player.isInCombat() || _player.isMoving() || _player.isCastingNow()) { return Quote.rejected(Reason.INVALID_ACTOR_STATE); }
			final var item = _player.getInventory().getItemByObjectId(intent.itemObjectId());
			if (item == null || item.getId() != intent.itemId() || PhantomLootDispositionPolicy.getInstance().evaluate(_player, item, Set.of(), false).disposition() != PhantomLootDispositionPolicy.Disposition.CRYSTALLIZE) { return Quote.rejected(Reason.ITEM_NOT_SELLABLE); }
			final var inspection = org.l2jmobius.gameserver.qol.CrystallizationService.getInstance().inspect(_player, item.getObjectId(), 1);
			if (!inspection.eligible()) { return Quote.rejected(Reason.ITEM_NOT_SELLABLE); }
			final var preview = inspection.preview();
			if (!_player.getInventory().validateWeight(Math.max(0, Math.multiplyExact((long) ItemData.getInstance().getTemplate(preview.crystalItemId()).getWeight(), preview.crystalCount()) - item.getTemplate().getWeight()))) { return Quote.rejected(Reason.WEIGHT_LIMIT); }
			if (_player.getInventory().getItemByItemId(preview.crystalItemId()) == null && !_player.getInventory().validateCapacity(1)) { return Quote.rejected(Reason.CAPACITY_LIMIT); }
			final var request = new OperationRequest(OperationKind.CRYSTALLIZE, 0, 0, 0, item.getId(), item.getObjectId(), 1, preview.crystalCount(), preview.crystalItemId(), 0, 0, "", 0, 0, 0);
			if (_checkpointOwner != null && !claimEconomyWriter(request)) { return Quote.rejected(Reason.OPERATION_BUSY); }
			final var before = snapshot(request);
			return Quote.accepted(request, before, new ConservationFacts(Math.addExact(before.primaryCount(), request.amount()), before.secondaryCount() - 1, before.objectCount() - 1, before.instanceId(), before.x(), before.y(), before.z()), actorFacts(item.getId(), item.getObjectId()));
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
			final int destinationZ = teleportDestinationZ(location);
			final ConservationFacts after = new ConservationFacts(Math.subtractExact(before.primaryCount(), fee), before.secondaryCount(), before.objectCount(), location.getInstanceId(), location.getX(), location.getY(), destinationZ);
			return Quote.accepted(request, before, after, actorFacts(0, 0));
		}

		@Override
		public ConservationFacts snapshot(OperationRequest request)
		{
			final long primary = request.kind() == OperationKind.TELEPORT || request.kind() == OperationKind.CRYSTALLIZE ? itemCount(request.feeItemId()) : _player.getAdena();
			final long secondary = request.itemId() == 0 ? 0 : itemCount(request.itemId());
			final Item object = request.itemObjectId() == 0 ? null : _player.getInventory().getItemByObjectId(request.itemObjectId());
			final long objectCount = warehouse(request) ? warehouseCount(request.itemId()) : request.kind() == OperationKind.BUY ? secondary : object == null ? 0 : object.getCount();
			return new ConservationFacts(primary, secondary, objectCount, _player.getInstanceId(), _player.getX(), _player.getY(), _player.getZ());
		}

		@Override
		public boolean applyFirst(OperationRequest request)
		{
			if ((warehouse(request) || request.kind() == OperationKind.CRYSTALLIZE || request.kind() == OperationKind.SELL && request.feeCount() == 1) && !scoped()) { return false; }
			if ((request.kind() != OperationKind.TELEPORT) && !claimEconomyWriter(request))
			{
				return false;
			}
			final boolean applied = switch (request.kind())
			{
				case BUY -> applyBuyPayment(request);
				case SELL -> applySellRemoval(request);
				case TELEPORT -> applyTeleportFee(request);
				case WAREHOUSE_DEPOSIT -> applyWarehouseFee(request);
				case WAREHOUSE_WITHDRAW -> applyWarehouseTransfer(request);
				case CRYSTALLIZE -> applyCrystal(request);
			};
			if (applied && scoped()) { flushAndVerify(request); }
			return applied;
		}

		private boolean claimEconomyWriter(OperationRequest request)
		{
			if (_economyClaim != null)
			{
				return _economyClaim.acquired();
			}
			final List<Reservation> resources = new ArrayList<>();
			if (request.kind() == OperationKind.BUY)
			{
				resources.add(new Reservation(_profileId, _player.getObjectId(), _player.getClassIndex(), ResourceKind.ADENA, 0, Inventory.ADENA_ID, request.amount(), _player.getAdena(), 0, "INVENTORY"));
				resources.add(new Reservation(_profileId, _player.getObjectId(), _player.getClassIndex(), ResourceKind.ITEM_COUNT, 0, request.itemId(), request.count(), itemCount(request.itemId()), 0, "INVENTORY"));
			}
			else
			{
				final Item item = request.kind() == OperationKind.WAREHOUSE_WITHDRAW ? _player.getWarehouse().getItemByObjectId(request.itemObjectId()) : _player.getInventory().getItemByObjectId(request.itemObjectId());
				resources.add(new Reservation(_profileId, _player.getObjectId(), _player.getClassIndex(), ResourceKind.ITEM_OBJECT, request.itemObjectId(), request.itemId(), 0, item == null ? 0 : item.getCount(), item == null ? 0 : item.getEnchantLevel(), item == null ? "INVENTORY" : item.getItemLocation().name()));
				resources.add(new Reservation(_profileId, _player.getObjectId(), _player.getClassIndex(), ResourceKind.ADENA, 0, Inventory.ADENA_ID, request.kind() == OperationKind.WAREHOUSE_DEPOSIT ? request.amount() : 0, _player.getAdena(), 0, "INVENTORY"));
				if (warehouse(request) || request.kind() == OperationKind.CRYSTALLIZE)
				{
					resources.add(new Reservation(_profileId, _player.getObjectId(), _player.getClassIndex(), ResourceKind.CAPACITY, 0, 0, 1, _player.getInventory().getSize(), 0, "INVENTORY"));
				}
				if (request.kind() == OperationKind.CRYSTALLIZE) { resources.add(new Reservation(_profileId, _player.getObjectId(), _player.getClassIndex(), ResourceKind.ITEM_COUNT, 0, request.feeItemId(), request.amount(), itemCount(request.feeItemId()), 0, "INVENTORY")); }
			}
			_economyClaim = PhantomEconomyConflictPort.claim(_profileId, null, resources);
			return _economyClaim.acquired();
		}

		@Override
		public boolean applySecond(OperationRequest request)
		{
			if (request.kind() != OperationKind.TELEPORT && !claimEconomyWriter(request)) { return false; }
			if ((warehouse(request) || request.kind() == OperationKind.CRYSTALLIZE || request.kind() == OperationKind.SELL && request.feeCount() == 1) && !scoped()) { return false; }
			final boolean applied = switch (request.kind())
			{
				case BUY -> applyBuyOutput(request);
				case SELL -> applySellRefund(request);
				case TELEPORT -> applyTeleportDestination(request);
				case WAREHOUSE_DEPOSIT -> applyWarehouseTransfer(request);
				case WAREHOUSE_WITHDRAW, CRYSTALLIZE -> false;
			};
			if (applied && scoped()) { flushAndVerify(request); }
			return applied;
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
			if ((merchant == null) || (GeneralConfig.ALLOW_REFUND ? request.feeCount() != 1 : request.feeCount() != 0) || MerchantZeroSellPriceConfig.MERCHANT_ZERO_SELL_PRICE || validateActor(merchant, false) != Reason.ACCEPTED || _player.isProcessingTransaction() || _player.isInStoreMode())
			{
				return false;
			}
			final BuyListHolder list = BuyListData.getInstance().getBuyList(request.listId());
			final Item item = _player.checkItemManipulation(request.itemObjectId(), request.count(), "phantom-sell");
			if ((list == null) || !list.isNpcAllowed(merchant.getId()) || (item == null) || (item.getId() != request.itemId()) || !item.isSellable() || (Math.multiplyExact(item.getReferencePrice() / 2L, request.count()) != request.amount()))
			{
				return false;
			}
			return GeneralConfig.ALLOW_REFUND
				? _player.getInventory().transferItem(ItemProcessType.TRANSFER, request.itemObjectId(), request.count(), _player.getRefund(), _player, merchant) != null
				: _player.getInventory().destroyItem(ItemProcessType.SELL, request.itemObjectId(), request.count(), _player, merchant) != null;
		}

		private static boolean warehouse(OperationRequest request) { return request.kind() == OperationKind.WAREHOUSE_DEPOSIT || request.kind() == OperationKind.WAREHOUSE_WITHDRAW; }
		private boolean scoped() { return _checkpointOwner != null && _checkpointOwner.isCurrent() && _checkpointOwner.temporaryCheckpoint() && _checkpointOwner.nativeObservationHealthy(); }
		private long warehouseCount(int itemId) { return _player.getWarehouse().getItems().stream().filter(item -> item.getId() == itemId).mapToLong(Item::getCount).sum(); }

		private boolean applyWarehouseFee(OperationRequest request)
		{
			final var manager = npc(request);
			final var item = _player.getInventory().getItemByObjectId(request.itemObjectId());
			return manager != null && manager.isWarehouse() && validateActor(manager, false) == Reason.ACCEPTED && !warehouseBusy()
				&& item != null && item.getId() == request.itemId() && item.getCount() >= request.count() && item.isDepositable(true) && item.isAvailable(_player, true, true)
				&& request.amount() == 30 && warehouseFits(item, request.count(), true) && _player.reduceAdena(ItemProcessType.FEE, 30, manager, false);
		}

		private boolean applyWarehouseTransfer(OperationRequest request)
		{
			final var manager = npc(request); final boolean deposit = request.kind() == OperationKind.WAREHOUSE_DEPOSIT;
			if (manager == null || !manager.isWarehouse() || validateActor(manager, false) != Reason.ACCEPTED || warehouseBusy()) { return false; }
			final var source = deposit ? _player.getInventory() : _player.getWarehouse(); final var target = deposit ? _player.getWarehouse() : _player.getInventory();
			final var item = source.getItemByObjectId(request.itemObjectId());
			if (item == null || item.getId() != request.itemId() || item.getCount() < request.count() || item.isEquipped() || !warehouseFits(item, request.count(), deposit)
				|| deposit && (!item.isDepositable(true) || !item.isAvailable(_player, true, true))) { return false; }
			return source.transferItem(ItemProcessType.TRANSFER, item.getObjectId(), request.count(), target, _player, manager) != null;
		}

		private boolean applyCrystal(OperationRequest request)
		{
			if (warehouseBusy() || _player.isAlikeDead() || _player.isInCombat() || _player.isMoving() || _player.isCastingNow()) { return false; }
			final var item = _player.getInventory().getItemByObjectId(request.itemObjectId());
			if (item == null || item.getId() != request.itemId() || PhantomLootDispositionPolicy.getInstance().evaluate(_player, item, Set.of(), false).disposition() != PhantomLootDispositionPolicy.Disposition.CRYSTALLIZE) { return false; }
			final var service = org.l2jmobius.gameserver.qol.CrystallizationService.getInstance(); final var inspection = service.inspect(_player, item.getObjectId(), request.count());
			if (!inspection.eligible() || inspection.preview().crystalItemId() != request.feeItemId() || inspection.preview().crystalCount() != request.amount()) { return false; }
			return service.crystallize(_player, item.getObjectId(), request.count(), inspection.preview()).status() == org.l2jmobius.gameserver.qol.CrystallizationService.Status.SUCCESS;
		}

		/** Exact accepted-operation persistence, outside item/status monitors and before receipt COMMITTED. */
		private void flushAndVerify(OperationRequest request)
		{
			try
			{
			_player.getInventory().updateDatabase(); _player.getWarehouse().updateDatabase(); _player.getRefund().updateDatabase();
			final var nativeFacts = snapshot(request);
			long primary = 0, secondary = 0, objects = 0;
			try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? AND item_id IN (57,?,?)"))
			{
				query.setInt(1, _player.getObjectId()); query.setInt(2, request.itemId()); query.setInt(3, request.feeItemId());
				try (var rows = query.executeQuery())
				{
					while (rows.next())
					{
						final int id = rows.getInt(2); final long count = rows.getLong(3); final String location = rows.getString(4);
						final boolean inventory = location.equals("INVENTORY") || location.equals("PAPERDOLL");
						if (inventory && id == (request.kind() == OperationKind.CRYSTALLIZE ? request.feeItemId() : Inventory.ADENA_ID)) { primary = Math.addExact(primary, count); }
						if (inventory && id == request.itemId()) { secondary = Math.addExact(secondary, count); }
						if (warehouse(request) ? location.equals("WAREHOUSE") && id == request.itemId() : request.kind() == OperationKind.BUY ? inventory && id == request.itemId() : inventory && rows.getInt(1) == request.itemObjectId()) { objects = Math.addExact(objects, count); }
					}
				}
				if (nativeFacts.primaryCount() != primary || nativeFacts.secondaryCount() != secondary || nativeFacts.objectCount() != objects) { throw new IllegalStateException("Native commerce persistence differs from exact inventory/warehouse counts."); }
			}
			}
			catch (java.sql.SQLException | RuntimeException failure)
			{
				_checkpointOwner.recordCheckpointFailure(failure); _checkpointOwner.recordFailure(failure);
				throw new IllegalStateException("Native commerce persistence not confirmed; receipt remains unresolved.", failure);
			}
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
			_player.onTeleported();
			// Headless actors have no client re-entry, so keep runtime coordinates equal to durable server coordinates.
			_player.setXYZ(location.getX(), location.getY(), teleportDestinationZ(location));
			return true;
		}

		private int teleportDestinationZ(TeleportLocation location)
		{
			return _player.isFlying() ? location.getZ() : GeoEngine.getInstance().getHeight(location.getX(), location.getY(), location.getZ());
		}

		private BuyOffer findBuyOffer(OperationIntent intent)
		{
			return _catalog.findBuyOffer(intent.listId(), intent.itemId());
		}

		private TeleportRoute findTeleportRoute(OperationIntent intent)
		{
			return _catalog.findTeleportRoute(intent.npcTemplateId(), intent.listName(), intent.ordinal());
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
			final BuyOffer offer = _catalog.findBuyOffer(request.listId(), request.itemId());
			return (offer != null) && offer.npcIds().contains(request.npcTemplateId()) && (offer.price() == product.getPrice()) ? product : null;
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
			try
			{
				if (_economyClaim != null)
				{
					_economyClaim.close();
				}
			}
			finally
			{
				if (_lease != null) { _lease.close(); }
			}
		}
	}
}

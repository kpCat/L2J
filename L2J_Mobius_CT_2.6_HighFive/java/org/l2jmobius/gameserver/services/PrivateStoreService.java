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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.services;

import static org.l2jmobius.gameserver.model.actor.Npc.INTERACTION_DISTANCE;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.sql.OfflineTraderTable;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.network.PacketLogger;
import org.l2jmobius.gameserver.network.holders.RequestTrade;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.network.holders.TradeList;
import org.l2jmobius.gameserver.network.holders.TradeList.MutationMode;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;

/** Packet-independent owner of canonical private-store transactions. */
public final class PrivateStoreService
{
	private static final int MAX_OBSERVERS = 10000;
	private final ConcurrentHashMap<Key, Observer> _observers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Integer, OwnerObserver> _ownerObservers = new ConcurrentHashMap<>();

	private PrivateStoreService()
	{
	}

	public static PrivateStoreService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	public Result buy(Player buyer, int ownerObjectId, Set<RequestTrade> items)
	{
		return buy(buyer, ownerObjectId, items, MutationMode.ORDINARY_COMPATIBLE, null, null);
	}

	public Result buyExact(Player buyer, int ownerObjectId, Set<RequestTrade> items, String expectedListingHash, String expectedRequestHash)
	{
		return buy(buyer, ownerObjectId, items, MutationMode.STRICT_EXACT_OBJECT, Objects.requireNonNull(expectedListingHash), Objects.requireNonNull(expectedRequestHash));
	}

	private Result buy(Player buyer, int ownerObjectId, Set<RequestTrade> items, MutationMode mode, String expectedListingHash, String expectedRequestHash)
	{
		Objects.requireNonNull(buyer);
		Objects.requireNonNull(items);
		final Player owner = World.getInstance().getPlayer(ownerObjectId);
		if ((owner == null) || buyer.isCursedWeaponEquipped() || !buyer.isInsideRadius3D(owner, INTERACTION_DISTANCE) || ((buyer.getInstanceId() != owner.getInstanceId()) && (buyer.getInstanceId() != -1)) || ((owner.getPrivateStoreType() != PrivateStoreType.SELL) && (owner.getPrivateStoreType() != PrivateStoreType.PACKAGE_SELL)))
		{
			return Result.REJECTED;
		}
		final TradeList list = owner.getSellList();
		if (list == null)
		{
			return Result.REJECTED;
		}
		if (!buyer.getAccessLevel().allowTransaction())
		{
			buyer.sendMessage("Transactions are disabled for your Access Level.");
			buyer.sendPacket(ActionFailed.STATIC_PACKET);
			return Result.REJECTED;
		}
		if ((owner.getPrivateStoreType() == PrivateStoreType.PACKAGE_SELL) && (list.getItemCount() > items.size()))
		{
			return Result.PACKAGE_VIOLATION;
		}
		return mutate(Direction.BUY_FROM_SELL_STORE, buyer, owner, list, requestHash(items), mode, expectedListingHash, expectedRequestHash, () -> list.privateStoreBuy(buyer, items, mode) == 0);
	}

	public Result sell(Player seller, int ownerObjectId, RequestTrade[] items)
	{
		return sell(seller, ownerObjectId, items, MutationMode.ORDINARY_COMPATIBLE, null, null);
	}

	public Result sellExact(Player seller, int ownerObjectId, RequestTrade[] items, String expectedListingHash, String expectedRequestHash)
	{
		return sell(seller, ownerObjectId, items, MutationMode.STRICT_EXACT_OBJECT, Objects.requireNonNull(expectedListingHash), Objects.requireNonNull(expectedRequestHash));
	}

	private Result sell(Player seller, int ownerObjectId, RequestTrade[] items, MutationMode mode, String expectedListingHash, String expectedRequestHash)
	{
		Objects.requireNonNull(seller);
		Objects.requireNonNull(items);
		final Player owner = World.getInstance().getPlayer(ownerObjectId);
		if ((owner == null) || !seller.isInsideRadius3D(owner, INTERACTION_DISTANCE) || ((seller.getInstanceId() != owner.getInstanceId()) && (seller.getInstanceId() != -1)) || (owner.getPrivateStoreType() != PrivateStoreType.BUY) || seller.isCursedWeaponEquipped())
		{
			return Result.REJECTED;
		}
		final TradeList list = owner.getBuyList();
		if (list == null)
		{
			return Result.REJECTED;
		}
		if (!seller.getAccessLevel().allowTransaction())
		{
			seller.sendMessage("Transactions are disabled for your Access Level.");
			seller.sendPacket(ActionFailed.STATIC_PACKET);
			return Result.REJECTED;
		}
		return mutate(Direction.SELL_TO_BUY_STORE, seller, owner, list, requestHash(items), mode, expectedListingHash, expectedRequestHash, () -> list.privateStoreSell(seller, items, mode));
	}

	private Result mutate(Direction direction, Player actor, Player owner, TradeList list, String requestHash, MutationMode mode, String expectedListingHash, String expectedRequestHash, Mutation mutation)
	{
		synchronized (list)
		{
			final String beforeHash = listingHash(list);
			if ((expectedListingHash != null) && (!expectedListingHash.equals(beforeHash) || !expectedRequestHash.equals(requestHash)))
			{
				return Result.REJECTED;
			}
			final Observer observer = _observers.get(new Key(direction, actor.getObjectId(), owner.getObjectId()));
			if ((observer != null) && !observer.beforeMutation(direction, actor, owner, list, beforeHash, requestHash, mode))
			{
				return Result.REJECTED;
			}
			boolean success = false;
			boolean observerCompleted = false;
			try
			{
				success = mutation.execute();
				if (!success)
				{
					actor.sendPacket(ActionFailed.STATIC_PACKET);
					PacketLogger.warning("PrivateStore transaction failed due to invalid list or request. Player: " + actor.getName() + ", private store of: " + owner.getName());
					return Result.REJECTED;
				}
				if (observer != null)
				{
					// Record the exact canonical transfer before closing an empty headless store can dematerialize its owner.
					observerCompleted = true;
					observer.afterMutation(direction, actor, owner, list, beforeHash, listingHash(list), true);
				}
				if (OfflineTradeConfig.OFFLINE_TRADE_ENABLE && OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME && !owner.hasHeadlessOutboundSession() && ((owner.getClient() == null) || owner.getClient().isDetached()))
				{
					OfflineTraderTable.getInstance().onTransaction(owner, list.getItemCount() == 0, false);
				}
				if (list.getItemCount() == 0)
				{
					owner.setPrivateStoreType(PrivateStoreType.NONE);
					owner.broadcastUserInfo();
				}
				return Result.COMMITTED;
			}
			finally
			{
				if ((observer != null) && !observerCompleted)
				{
					observer.afterMutation(direction, actor, owner, list, beforeHash, listingHash(list), success);
				}
				final OwnerObserver ownerObserver = _ownerObservers.get(owner.getObjectId());
				if (ownerObserver != null)
				{
					ownerObserver.afterTransaction(owner, list, success);
				}
			}
		}
	}

	public AutoCloseable observe(Direction direction, int actorObjectId, int ownerObjectId, Observer observer)
	{
		if (_observers.size() >= MAX_OBSERVERS)
		{
			throw new IllegalStateException("Private-store observer bound exceeded.");
		}
		final Key key = new Key(direction, actorObjectId, ownerObjectId);
		if (_observers.putIfAbsent(key, Objects.requireNonNull(observer)) != null)
		{
			throw new IllegalStateException("Private-store transaction already has an observer.");
		}
		return () -> _observers.remove(key, observer);
	}

	public AutoCloseable observeOwner(int ownerObjectId, OwnerObserver observer)
	{
		if ((ownerObjectId <= 0) || ((_ownerObservers.size() + _observers.size()) >= MAX_OBSERVERS))
		{
			throw new IllegalStateException("Private-store owner observer bound exceeded.");
		}
		if (_ownerObservers.putIfAbsent(ownerObjectId, Objects.requireNonNull(observer)) != null)
		{
			throw new IllegalStateException("Private-store owner already has an observer.");
		}
		return () -> _ownerObservers.remove(ownerObjectId, observer);
	}

	public static String listingHash(TradeList list)
	{
		if (list == null)
		{
			return PhantomEconomyOperation.sha256("absent");
		}
		final StringBuilder canonical = new StringBuilder(256).append(list.getOwner().getObjectId()).append('|').append(list.isPackaged()).append('|');
		list.getItems().stream().sorted(Comparator.comparingInt(TradeItem::getObjectId).thenComparingInt(item -> item.getItem().getId()).thenComparingLong(TradeItem::getPrice)).forEach(item -> canonical.append(item.getObjectId()).append(':').append(item.getItem().getId()).append(':').append(item.getCount()).append(':').append(item.getPrice()).append(';'));
		return PhantomEconomyOperation.sha256(canonical.toString());
	}

	public static String requestHash(Set<RequestTrade> items)
	{
		return requestHash(items.toArray(RequestTrade[]::new));
	}

	public static String requestHash(RequestTrade[] items)
	{
		final StringBuilder canonical = new StringBuilder(256);
		Arrays.stream(items).sorted(Comparator.comparingInt(RequestTrade::getObjectId).thenComparingInt(RequestTrade::getItemId).thenComparingLong(RequestTrade::getPrice)).forEach(item -> canonical.append(item.getObjectId()).append(':').append(item.getItemId()).append(':').append(item.getCount()).append(':').append(item.getPrice()).append(';'));
		return PhantomEconomyOperation.sha256(canonical.toString());
	}

	public interface Observer
	{
		boolean beforeMutation(Direction direction, Player actor, Player owner, TradeList list, String listingHash);

		default boolean beforeMutation(Direction direction, Player actor, Player owner, TradeList list, String listingHash, String requestHash, MutationMode mode)
		{
			return beforeMutation(direction, actor, owner, list, listingHash);
		}

		void afterMutation(Direction direction, Player actor, Player owner, TradeList list, String beforeListingHash, String afterListingHash, boolean successful);
	}

	@FunctionalInterface
	public interface OwnerObserver
	{
		void afterTransaction(Player owner, TradeList list, boolean successful);
	}

	public enum Direction
	{
		BUY_FROM_SELL_STORE,
		SELL_TO_BUY_STORE
	}

	public enum Result
	{
		COMMITTED,
		PACKAGE_VIOLATION,
		REJECTED
	}

	@FunctionalInterface
	private interface Mutation
	{
		boolean execute();
	}

	private record Key(Direction direction, int actorObjectId, int ownerObjectId)
	{
		private Key
		{
			Objects.requireNonNull(direction);
			if ((actorObjectId <= 0) || (ownerObjectId <= 0) || (actorObjectId == ownerObjectId))
			{
				throw new IllegalArgumentException("Invalid private-store owners.");
			}
		}
	}

	private static final class SingletonHolder
	{
		private static final PrivateStoreService INSTANCE = new PrivateStoreService();
	}
}

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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.BotReportTable;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.holders.player.BlockList;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.skill.AbnormalType;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.network.PacketLogger;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.network.holders.TradeList;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.SendTradeRequest;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.network.serverpackets.TradeOtherAdd;
import org.l2jmobius.gameserver.network.serverpackets.TradeOwnAdd;
import org.l2jmobius.gameserver.network.serverpackets.TradeUpdate;

/** Packet-independent owner of the canonical direct-trade lifecycle. */
public final class DirectTradeService
{
	private static final int MAX_OBSERVERS = 10000;
	private final ConcurrentHashMap<Pair, Entry> _observers = new ConcurrentHashMap<>();

	private DirectTradeService()
	{
	}

	public static DirectTradeService getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	public Result request(Player player, int objectId)
	{
		Objects.requireNonNull(player);
		if (!player.getAccessLevel().allowTransaction())
		{
			player.sendMessage("Transactions are disabled for your current Access Level.");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return Result.REJECTED;
		}
		if (tradeBlocked(player))
		{
			player.sendPacket(SystemMessageId.YOU_HAVE_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_SO_YOUR_ACTIONS_HAVE_BEEN_RESTRICTED);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return Result.REJECTED;
		}
		final WorldObject target = World.getInstance().findObject(objectId);
		if ((target == null) || !player.isInSurroundingRegion(target) || ((target.getInstanceId() != player.getInstanceId()) && (player.getInstanceId() != -1)))
		{
			return Result.REJECTED;
		}
		if (target.getObjectId() == player.getObjectId())
		{
			player.sendPacket(SystemMessageId.THAT_IS_AN_INCORRECT_TARGET);
			return Result.REJECTED;
		}
		if (!target.isPlayer())
		{
			player.sendPacket(SystemMessageId.INVALID_TARGET);
			return Result.REJECTED;
		}
		final Player partner = target.asPlayer();
		if (partner.isInOlympiadMode() || player.isInOlympiadMode())
		{
			player.sendMessage("A user currently participating in the Olympiad cannot accept or request a trade.");
			return Result.REJECTED;
		}
		if (tradeBlocked(partner))
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_HAS_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_AND_IS_CURRENTLY_BEING_INVESTIGATED);
			message.addString(partner.getName());
			player.sendPacket(message);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return Result.REJECTED;
		}
		if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_TRADE && (player.getKarma() > 0))
		{
			player.sendMessage("You cannot trade while you are in a chaotic state.");
			return Result.REJECTED;
		}
		if (!PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_TRADE && (partner.getKarma() > 0))
		{
			player.sendMessage("You cannot request a trade while your target is in a chaotic state.");
			return Result.REJECTED;
		}
		if (GeneralConfig.JAIL_DISABLE_TRANSACTION && (player.isJailed() || partner.isJailed()))
		{
			player.sendMessage("You cannot trade while you are in in Jail.");
			return Result.REJECTED;
		}
		if (player.isInStoreMode() || partner.isInStoreMode())
		{
			player.sendPacket(SystemMessageId.WHILE_OPERATING_A_PRIVATE_STORE_OR_WORKSHOP_YOU_CANNOT_DISCARD_DESTROY_OR_TRADE_AN_ITEM);
			return Result.REJECTED;
		}
		if (player.isProcessingTransaction())
		{
			player.sendPacket(SystemMessageId.YOU_ARE_ALREADY_TRADING_WITH_SOMEONE);
			return Result.REJECTED;
		}
		if (partner.isProcessingRequest() || partner.isProcessingTransaction())
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_IS_ALREADY_TRADING_WITH_ANOTHER_PERSON_PLEASE_TRY_AGAIN_LATER);
			message.addString(partner.getName());
			player.sendPacket(message);
			return Result.REJECTED;
		}
		if (partner.getTradeRefusal())
		{
			player.sendMessage("That person is in trade refusal mode.");
			return Result.REJECTED;
		}
		if (BlockList.isBlocked(partner, player))
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.S1_HAS_PLACED_YOU_ON_HIS_HER_IGNORE_LIST);
			message.addString(partner.getName());
			player.sendPacket(message);
			return Result.REJECTED;
		}
		if (player.calculateDistance3D(partner) > 150)
		{
			player.sendPacket(SystemMessageId.YOUR_TARGET_IS_OUT_OF_RANGE);
			return Result.REJECTED;
		}
		player.onTransactionRequest(partner);
		partner.sendPacket(new SendTradeRequest(player.getObjectId()));
		final SystemMessage message = new SystemMessage(SystemMessageId.YOU_HAVE_REQUESTED_A_TRADE_WITH_C1);
		message.addString(partner.getName());
		player.sendPacket(message);
		return Result.REQUESTED;
	}

	public Result answer(Player player, boolean accepted)
	{
		Objects.requireNonNull(player);
		final Player partner = player.getActiveRequester();
		if ((partner == null) || (World.getInstance().getPlayer(partner.getObjectId()) == null))
		{
			player.sendPacket(new org.l2jmobius.gameserver.network.serverpackets.TradeDone(0));
			player.sendPacket(SystemMessageId.THAT_PLAYER_IS_NOT_ONLINE);
			player.setActiveRequester(null);
			return Result.REJECTED;
		}
		if (accepted && !partner.isRequestExpired())
		{
			player.startTrade(partner);
		}
		else
		{
			final SystemMessage message = new SystemMessage(SystemMessageId.C1_HAS_DENIED_YOUR_REQUEST_TO_TRADE);
			message.addString(player.getName());
			partner.sendPacket(message);
		}
		player.setActiveRequester(null);
		partner.onTransactionResponse();
		return accepted ? Result.ACCEPTED : Result.REJECTED;
	}

	public Result addItem(Player player, int tradeId, int objectId, long count)
	{
		Objects.requireNonNull(player);
		if (count < 1)
		{
			return Result.REJECTED;
		}
		final TradeList trade = player.getActiveTradeList();
		if (trade == null)
		{
			PacketLogger.warning("Character: " + player.getName() + " requested item:" + objectId + " add without active tradelist:" + tradeId);
			return Result.REJECTED;
		}
		final Player partner = trade.getPartner();
		if ((partner == null) || (World.getInstance().getPlayer(partner.getObjectId()) == null) || (partner.getActiveTradeList() == null))
		{
			if (partner != null)
			{
				PacketLogger.warning("Character:" + player.getName() + " requested invalid trade object: " + objectId);
			}
			player.sendPacket(SystemMessageId.THAT_PLAYER_IS_NOT_ONLINE);
			player.cancelActiveTrade();
			return Result.REJECTED;
		}
		if (trade.isConfirmed() || partner.getActiveTradeList().isConfirmed())
		{
			player.sendPacket(SystemMessageId.YOU_MAY_NO_LONGER_ADJUST_ITEMS_IN_THE_TRADE_BECAUSE_THE_TRADE_HAS_BEEN_CONFIRMED);
			return Result.REJECTED;
		}
		if (!player.getAccessLevel().allowTransaction())
		{
			player.sendMessage("Transactions are disabled for your Access Level.");
			player.cancelActiveTrade();
			return Result.REJECTED;
		}
		if (!player.validateItemManipulation(objectId, ItemProcessType.TRANSFER))
		{
			player.sendPacket(SystemMessageId.NOTHING_HAPPENED);
			return Result.REJECTED;
		}
		final TradeItem item = trade.addItem(objectId, count);
		if (item != null)
		{
			player.sendPacket(new TradeOwnAdd(item));
			player.sendPacket(new TradeUpdate(player, item));
			partner.sendPacket(new TradeOtherAdd(item));
			return Result.UPDATED;
		}
		return Result.REJECTED;
	}

	public Result finish(Player player, boolean accepted)
	{
		Objects.requireNonNull(player);
		final TradeList trade = player.getActiveTradeList();
		if ((trade == null) || trade.isLocked())
		{
			return Result.REJECTED;
		}
		if (!accepted)
		{
			cancel(player);
			return Result.CANCELLED;
		}
		final Player partner = trade.getPartner();
		if ((partner == null) || (World.getInstance().getPlayer(partner.getObjectId()) == null))
		{
			player.cancelActiveTrade();
			player.sendPacket(SystemMessageId.THAT_PLAYER_IS_NOT_ONLINE);
			return Result.REJECTED;
		}
		if ((trade.getOwner().getActiveEnchantItemId() != Player.ID_NONE) || (partner.getActiveEnchantItemId() != Player.ID_NONE))
		{
			return Result.REJECTED;
		}
		if (!player.getAccessLevel().allowTransaction())
		{
			player.cancelActiveTrade();
			player.sendPacket(SystemMessageId.YOU_ARE_NOT_AUTHORIZED_TO_DO_THAT);
			return Result.REJECTED;
		}
		if ((player.getInstanceId() != partner.getInstanceId()) && (player.getInstanceId() != -1))
		{
			player.cancelActiveTrade();
			return Result.REJECTED;
		}
		if (player.calculateDistance3D(partner) > 150)
		{
			player.cancelActiveTrade();
			return Result.REJECTED;
		}
		final Entry entry = _observers.get(Pair.of(player.getObjectId(), partner.getObjectId()));
		if (entry == null)
		{
			return trade.confirm() ? Result.CONFIRMED : Result.REJECTED;
		}
		synchronized (entry)
		{
			final TradeList partnerList = partner.getActiveTradeList();
			if ((partnerList == null) || (partnerList.getPartner() != player))
			{
				entry.observer.cancel("trade.partner.lost");
				return Result.REJECTED;
			}
			if (!partnerList.isConfirmed())
			{
				return trade.confirm() ? Result.CONFIRMED : Result.REJECTED;
			}
			final Bridge bridge = new Bridge(entry);
			try
			{
				final boolean confirmed = trade.confirm(bridge);
				if (bridge.beforeAttempted && !bridge.accepted)
				{
					player.cancelActiveTrade();
					entry.observer.cancel("trade.before_execute_rejected");
					return Result.REJECTED;
				}
				if (bridge.accepted)
				{
					entry.observer.afterExecute(trade, partnerList, bridge.completed && bridge.successful);
				}
				return confirmed && bridge.successful ? Result.COMMITTED : Result.REJECTED;
			}
			catch (RuntimeException exception)
			{
				if (bridge.accepted && !bridge.completed)
				{
					try
					{
						entry.observer.afterExecute(trade, partnerList, false);
					}
					finally
					{
						cancel(player, partner);
					}
				}
				throw exception;
			}
		}
	}

	public void cancel(Player player)
	{
		final TradeList trade = player.getActiveTradeList();
		final Player partner = trade == null ? null : trade.getPartner();
		final Entry entry = partner == null ? null : _observers.get(Pair.of(player.getObjectId(), partner.getObjectId()));
		player.cancelActiveTrade();
		if (entry != null)
		{
			entry.observer.cancel("trade.cancelled");
		}
	}

	/** Clears only the expected canonical pair and reports exact request/list cleanup. */
	public boolean cancel(Player player, Player expectedPartner)
	{
		Objects.requireNonNull(player);
		Objects.requireNonNull(expectedPartner);
		if (player == expectedPartner)
		{
			return false;
		}
		final Pair pair = Pair.of(player.getObjectId(), expectedPartner.getObjectId());
		final Entry entry = _observers.get(pair);
		final TradeList playerList = player.getActiveTradeList();
		final TradeList partnerList = expectedPartner.getActiveTradeList();
		if ((playerList != null) && (playerList.getPartner() != expectedPartner))
		{
			return false;
		}
		if ((partnerList != null) && (partnerList.getPartner() != player))
		{
			return false;
		}
		if (playerList != null)
		{
			player.cancelActiveTrade();
		}
		else if (partnerList != null)
		{
			expectedPartner.cancelActiveTrade();
		}
		if (player.getActiveRequester() == expectedPartner)
		{
			player.setActiveRequester(null);
		}
		if (expectedPartner.getActiveRequester() == player)
		{
			expectedPartner.setActiveRequester(null);
		}
		player.onTransactionResponse();
		expectedPartner.onTransactionResponse();
		final boolean cleared = canonicalPairCleared(player, expectedPartner);
		if (cleared && (entry != null))
		{
			entry.observer.cancel("trade.cancelled");
		}
		return cleared;
	}

	public boolean canonicalPairCleared(Player first, Player second)
	{
		Objects.requireNonNull(first);
		Objects.requireNonNull(second);
		return (first.getActiveTradeList() == null) && (second.getActiveTradeList() == null) && (first.getActiveRequester() != second) && (second.getActiveRequester() != first) && !first.isProcessingTransaction() && !second.isProcessingTransaction();
	}

	public AutoCloseable observe(int firstObjectId, int secondObjectId, Observer observer)
	{
		if (_observers.size() >= MAX_OBSERVERS)
		{
			throw new IllegalStateException("Direct-trade observer bound exceeded.");
		}
		final Pair key = Pair.of(firstObjectId, secondObjectId);
		final Entry entry = new Entry(Objects.requireNonNull(observer));
		if (_observers.putIfAbsent(key, entry) != null)
		{
			throw new IllegalStateException("Direct trade already has an observer.");
		}
		return () -> _observers.remove(key, entry);
	}

	private static boolean tradeBlocked(Player player)
	{
		final BuffInfo info = player.getEffectList().getBuffInfoByAbnormalType(AbnormalType.BOT_PENALTY);
		if (info != null)
		{
			for (AbstractEffect effect : info.getEffects())
			{
				if (!effect.checkCondition(BotReportTable.TRADE_ACTION_BLOCK_ID))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static final class Bridge implements TradeList.ExchangeObserver
	{
		private final Entry _entry;
		private boolean beforeAttempted;
		private boolean accepted;
		private boolean completed;
		private boolean successful;

		private Bridge(Entry entry)
		{
			_entry = entry;
		}

		@Override
		public boolean beforeExchange(TradeList first, TradeList second)
		{
			beforeAttempted = true;
			accepted = !_entry.started && _entry.observer.beforeExecute(first, second);
			_entry.started = accepted;
			return accepted;
		}

		@Override
		public void afterTransfer(int ownerObjectId, int receiverObjectId, int objectId, int itemId, long count)
		{
			_entry.observer.afterTransfer(ownerObjectId, receiverObjectId, objectId, itemId, count);
		}

		@Override
		public void afterExchange(TradeList first, TradeList second, boolean success)
		{
			completed = true;
			successful = success;
		}
	}

	public interface Observer
	{
		boolean beforeExecute(TradeList first, TradeList second);

		default void afterTransfer(int ownerObjectId, int receiverObjectId, int objectId, int itemId, long count)
		{
		}

		void afterExecute(TradeList first, TradeList second, boolean successful);

		default void cancel(String reason)
		{
		}
	}

	public enum Result
	{
		REQUESTED,
		ACCEPTED,
		UPDATED,
		CONFIRMED,
		COMMITTED,
		CANCELLED,
		REJECTED
	}

	private static final class Entry
	{
		private final Observer observer;
		private boolean started;

		private Entry(Observer observer)
		{
			this.observer = observer;
		}
	}

	private record Pair(int first, int second)
	{
		private static Pair of(int first, int second)
		{
			if ((first <= 0) || (second <= 0) || (first == second))
			{
				throw new IllegalArgumentException("Invalid direct-trade owners.");
			}
			return first < second ? new Pair(first, second) : new Pair(second, first);
		}
	}

	private static final class SingletonHolder
	{
		private static final DirectTradeService INSTANCE = new DirectTradeService();
	}
}

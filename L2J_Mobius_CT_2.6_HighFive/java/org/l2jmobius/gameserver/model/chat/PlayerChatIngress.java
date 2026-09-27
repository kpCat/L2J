/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.chat;

import java.util.logging.Logger;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.config.custom.WalkerBotProtectionConfig;
import org.l2jmobius.gameserver.handler.ChatHandler;
import org.l2jmobius.gameserver.handler.IChatHandler;
import org.l2jmobius.gameserver.managers.PunishmentManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchHandle;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.events.EventDispatcher;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerChat;
import org.l2jmobius.gameserver.model.events.returns.ChatFilterReturn;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.network.PacketLogger;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.enums.ChatType;

/**
 * Server-side player chat validation and native channel dispatch shared by Say2 and the local pilot.
 * A dispatched result means the handler ran; channel-specific acceptance and delivery remain separate evidence.
 */
public final class PlayerChatIngress
{
	public enum Status
	{
		DISPATCHED,
		REJECTED_INPUT,
		REJECTED_CHANNEL,
		REJECTED_LENGTH,
		REJECTED_WALKER,
		REJECTED_CURSED_WEAPON,
		REJECTED_CHAT_BAN,
		REJECTED_JAIL,
		REJECTED_ITEM,
		REJECTED_FILTER,
		NO_HANDLER
	}

	public record Result(Status status, String finalText, int observedDeliveries)
	{
		public boolean dispatched()
		{
			return status == Status.DISPATCHED;
		}
	}

	private static final Logger LOGGER_CHAT = Logger.getLogger("chat");
	private static final String[] WALKER_COMMAND_LIST =
	{
		"USESKILL", "USEITEM", "BUYITEM", "SELLITEM", "SAVEITEM", "LOADITEM", "MSG", "DELAY", "LABEL", "JMP", "CALL", "RETURN", "MOVETO", "NPCSEL", "NPCDLG", "DLGSEL", "CHARSTATUS", "POSOUTRANGE", "POSINRANGE", "GOHOME", "SAY", "EXIT", "PAUSE", "STRINDLG", "STRNOTINDLG", "CHANGEWAITTYPE", "FORCEATTACK", "ISMEMBER", "REQUESTJOINPARTY", "REQUESTOUTPARTY", "QUITPARTY", "MEMBERSTATUS", "CHARBUFFS", "ITEMCOUNT", "FOLLOWTELEPORT"
	};

	private PlayerChatIngress()
	{
	}

	public static Result dispatchClient(Player player, ChatType type, String target, String text)
	{
		return dispatch(player, type, target, text, false);
	}

	/** The caller must validate the pilot lease immediately before calling this method. */
	public static Result dispatchPilot(Player player, ChatType type, String target, String text)
	{
		return dispatch(player, type, target, text, true);
	}

	private static Result dispatch(Player player, ChatType type, String target, String text, boolean pilot)
	{
		if ((player == null) || (type == null) || (text == null) || text.isEmpty())
		{
			return rejected(Status.REJECTED_INPUT);
		}
		if (pilot && ((type != ChatType.GENERAL) && (type != ChatType.WHISPER) && (type != ChatType.PARTY)))
		{
			return rejected(Status.REJECTED_CHANNEL);
		}
		if (pilot && (player.isGM() || (text.charAt(0) == '.') || (text.charAt(0) == '/') || (text.indexOf(8) >= 0) || ((type == ChatType.WHISPER) && ((target == null) || target.isBlank()))))
		{
			return rejected(Status.REJECTED_INPUT);
		}
		// The client permits longer item-link text. The pilot deliberately accepts plain chat only.
		if (!player.isGM() && (((text.indexOf(8) >= 0) && (text.length() > 500)) || ((text.indexOf(8) < 0) && (text.length() > 105))))
		{
			player.sendPacket(SystemMessageId.WHEN_A_USER_S_KEYBOARD_INPUT_EXCEEDS_A_CERTAIN_CUMULATIVE_SCORE_A_CHAT_BAN_WILL_BE_APPLIED_THIS_IS_DONE_TO_DISCOURAGE_SPAMMING_PLEASE_AVOID_POSTING_THE_SAME_MESSAGE_MULTIPLE_TIMES_DURING_A_SHORT_PERIOD);
			return rejected(Status.REJECTED_LENGTH);
		}
		if (WalkerBotProtectionConfig.L2WALKER_PROTECTION && (type == ChatType.WHISPER) && checkBot(text))
		{
			if (!pilot)
			{
				PunishmentManager.handleIllegalPlayerAction(player, "Client Emulator Detect: " + player + " using L2Walker.", GeneralConfig.DEFAULT_PUNISH);
			}
			return rejected(Status.REJECTED_WALKER);
		}
		if (player.isCursedWeaponEquipped() && ((type == ChatType.TRADE) || (type == ChatType.SHOUT)))
		{
			player.sendPacket(SystemMessageId.SHOUT_AND_TRADE_CHATTING_CANNOT_BE_USED_WHILE_POSSESSING_A_CURSED_WEAPON);
			return rejected(Status.REJECTED_CURSED_WEAPON);
		}
		if (player.isChatBanned() && (text.charAt(0) != '.'))
		{
			if (player.getEffectList().getFirstEffect(EffectType.CHAT_BLOCK) != null)
			{
				player.sendPacket(SystemMessageId.YOU_HAVE_BEEN_REPORTED_AS_AN_ILLEGAL_PROGRAM_USER_SO_CHATTING_IS_NOT_ALLOWED);
			}
			else if (GeneralConfig.BAN_CHAT_CHANNELS.contains(type))
			{
				player.sendPacket(SystemMessageId.CHATTING_IS_CURRENTLY_PROHIBITED);
			}
			return rejected(Status.REJECTED_CHAT_BAN);
		}
		if (player.isJailed() && GeneralConfig.JAIL_DISABLE_CHAT && ((type == ChatType.WHISPER) || (type == ChatType.SHOUT) || (type == ChatType.TRADE) || (type == ChatType.HERO_VOICE)))
		{
			player.sendMessage("You can not chat with players outside of the jail.");
			return rejected(Status.REJECTED_JAIL);
		}
		if ((type == ChatType.PETITION_PLAYER) && player.isGM())
		{
			type = ChatType.PETITION_GM;
		}
		if (GeneralConfig.LOG_CHAT)
		{
			final StringBuilder sb = new StringBuilder();
			sb.append(type.name()).append(" [").append(player);
			if (type == ChatType.WHISPER)
			{
				sb.append(" to ").append(target);
			}
			sb.append("] ").append(text);
			LOGGER_CHAT.info(sb.toString());
		}
		if ((text.indexOf(8) >= 0) && !parseAndPublishItem(player, text))
		{
			return rejected(Status.REJECTED_ITEM);
		}
		if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CHAT))
		{
			final ChatFilterReturn filter = EventDispatcher.getInstance().notifyEvent(new OnPlayerChat(player, World.getInstance().getPlayer(target), text, type), ChatFilterReturn.class);
			if (filter != null)
			{
				text = filter.getFilteredText();
			}
		}
		if (pilot && ((text == null) || text.isEmpty() || (text.charAt(0) == '.') || (text.charAt(0) == '/') || (text.indexOf(8) >= 0) || (text.length() > 105)))
		{
			return rejected(Status.REJECTED_FILTER);
		}
		if (GeneralConfig.USE_SAY_FILTER)
		{
			for (String pattern : ServerConfig.FILTER_LIST)
			{
				text = text.replaceAll("(?i)" + pattern, GeneralConfig.CHAT_FILTER_CHARS);
			}
		}
		if (pilot && ((text == null) || text.isEmpty() || (text.charAt(0) == '.') || (text.charAt(0) == '/') || (text.indexOf(8) >= 0) || (text.length() > 105)))
		{
			return rejected(Status.REJECTED_FILTER);
		}
		final IChatHandler handler = ChatHandler.getInstance().getHandler(type);
		if (handler == null)
		{
			PacketLogger.info("No handler registered for ChatType: " + type.getClientId() + " Player: " + player);
			return rejected(Status.NO_HANDLER);
		}
		return dispatchFinalFiltered(handler, type, player, target, text, System.currentTimeMillis(), pilot);
	}

	private static Result dispatchFinalFiltered(IChatHandler handler, ChatType type, Player player, String target, String text, long epochMillis, boolean pilot)
	{
		final ChatObservationService observations = ChatObservationService.getInstance();
		final DispatchHandle scope = pilot ? observations.openPilotDispatch(player.getObjectId(), player.getName(), type, target, text, epochMillis) : observations.openClientDispatch(player.getObjectId(), player.getName(), type, target, text, epochMillis);
		try
		{
			handler.onChat(type, player, target, text);
			return new Result(Status.DISPATCHED, text, scope.deliveries());
		}
		finally
		{
			scope.close();
		}
	}

	private static Result rejected(Status status)
	{
		return new Result(status, "", 0);
	}

	private static boolean checkBot(String text)
	{
		for (String command : WALKER_COMMAND_LIST)
		{
			if (text.startsWith(command))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean parseAndPublishItem(Player owner, String text)
	{
		int pos1 = -1;
		while ((pos1 = text.indexOf(8, pos1)) > -1)
		{
			int pos = text.indexOf("ID=", pos1);
			if (pos == -1)
			{
				return false;
			}
			final StringBuilder result = new StringBuilder(9);
			pos += 3;
			while (Character.isDigit(text.charAt(pos)))
			{
				result.append(text.charAt(pos++));
			}
			final int id = Integer.parseInt(result.toString());
			final WorldObject item = World.getInstance().findObject(id);
			if (item instanceof Item)
			{
				if (owner.getInventory().getItemByObjectId(id) == null)
				{
					PacketLogger.info(owner.getClient() + " trying publish item which does not own! ID:" + id);
					return false;
				}
				((Item) item).publish();
			}
			else
			{
				return false;
			}
			pos1 = text.indexOf(8, pos) + 1;
			if (pos1 == 0)
			{
				PacketLogger.info(owner.getClient() + " sent invalid publish item msg! ID:" + id);
				return false;
			}
		}
		return true;
	}
}

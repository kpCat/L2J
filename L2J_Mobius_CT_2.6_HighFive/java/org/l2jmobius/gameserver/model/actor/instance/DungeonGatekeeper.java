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
package org.l2jmobius.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import org.l2jmobius.gameserver.config.FeatureConfig;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.creature.InstanceType;
import org.l2jmobius.gameserver.model.actor.enums.player.TeleportType;
import org.l2jmobius.gameserver.model.actor.templates.NpcTemplate;
import org.l2jmobius.gameserver.model.sevensigns.SevenSigns;
import org.l2jmobius.gameserver.model.teleporter.TeleportHolder;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ActionFailed;
import org.l2jmobius.gameserver.network.serverpackets.NpcHtmlMessage;
import org.l2jmobius.gameserver.qol.PersonalCharacterQoLService;

public class DungeonGatekeeper extends Npc
{
	/**
	 * Creates a dungeon gatekeeper.
	 * @param template the dungeon gatekeeper NPC template
	 */
	public DungeonGatekeeper(NpcTemplate template)
	{
		super(template);
		setInstanceType(InstanceType.DungeonGatekeeper);
	}

	/** Read-only admission shared by the stock bypass and conditional travel planning. */
	public static boolean mayEnter(Player player, int objectId, boolean necropolis)
	{
		return entryDenial(player, objectId, necropolis) == null;
	}

	static boolean entryAllowed(boolean open, boolean validation, boolean winnerDecided, boolean registered, boolean winningSeal)
	{
		return open || (validation && winnerDecided ? winningSeal : registered);
	}

	private static EntryDenial entryDenial(Player player, int objectId, boolean necropolis)
	{
		final boolean open = necropolis ? FeatureConfig.ALT_SEVENSIGNS_OPEN_NECROPOLIS : FeatureConfig.ALT_SEVENSIGNS_OPEN_CATACUMBS;
		if (open)
		{
			return null;
		}
		final var signs = SevenSigns.getInstance();
		final var qol = PersonalCharacterQoLService.getInstance();
		final int cabal = signs.getPlayerCabal(objectId);
		final boolean validation = signs.isSealValidationPeriod();
		final int winner = signs.getCabalHighestScore();
		final boolean decided = winner == SevenSigns.CABAL_DAWN || winner == SevenSigns.CABAL_DUSK;
		final int seal = signs.getSealOwner(necropolis ? SevenSigns.SEAL_AVARICE : SevenSigns.SEAL_GNOSIS);
		final boolean registered = player == null ? cabal != SevenSigns.CABAL_NULL : qol.isSevenSignsRegistered(player, cabal);
		final boolean winningSeal = decided && (player == null ? seal == winner && cabal == winner : qol.isSevenSignsWinningSealEligible(player, cabal, winner, seal));
		if (entryAllowed(false, validation, decided, registered, winningSeal))
		{
			return null;
		}
		if (validation && decided)
		{
			return winner == SevenSigns.CABAL_DAWN ? EntryDenial.DAWN : EntryDenial.DUSK;
		}
		return EntryDenial.REGISTRATION;
	}

	private enum EntryDenial { DAWN, DUSK, REGISTRATION }
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		
		final StringTokenizer st = new StringTokenizer(command, " ");
		final String actualCommand = st.nextToken(); // Get actual command
		if (actualCommand.startsWith("necro") || actualCommand.startsWith("cata"))
		{
			final boolean necropolis = actualCommand.startsWith("necro");
			final var denial = entryDenial(player, player.getObjectId(), necropolis);
			if (denial != null)
			{
				if (denial == EntryDenial.DAWN) { player.sendPacket(SystemMessageId.ONLY_A_LORD_OF_DAWN_MAY_USE_THIS); }
				if (denial == EntryDenial.DUSK) { player.sendPacket(SystemMessageId.ONLY_A_REVOLUTIONARY_OF_DUSK_MAY_USE_THIS); }
				final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
				html.setFile(player, SevenSigns.SEVEN_SIGNS_HTML_PATH + (necropolis ? "necro_no.htm" : "cata_no.htm"));
				player.sendPacket(html);
			}
			else
			{
				doTeleport(player, Integer.parseInt(st.nextToken()));
				player.setIn7sDungeon(true);
			}
		}
		else if (actualCommand.startsWith("exit"))
		{
			doTeleport(player, Integer.parseInt(st.nextToken()));
			player.setIn7sDungeon(false);
		}
		else if (actualCommand.startsWith("goto"))
		{
			doTeleport(player, Integer.parseInt(st.nextToken()));
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	private void doTeleport(Player player, int value)
	{
		final TeleportHolder holder = TeleporterData.getInstance().getHolder(getId(), TeleportType.OTHER.name());
		if (holder != null)
		{
			holder.doTeleport(player, this, value);
		}
		else
		{
			LOGGER.warning("No teleport destination with id:" + value);
		}
		
		player.sendPacket(ActionFailed.STATIC_PACKET);
	}
	
	@Override
	public String getHtmlPath(int npcId, int value)
	{
		String pom = "";
		if (value == 0)
		{
			pom = Integer.toString(npcId);
		}
		else
		{
			pom = npcId + "-" + value;
		}
		
		return "data/html/teleporter/" + pom + ".htm";
	}
}

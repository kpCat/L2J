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
package org.l2jmobius.gameserver.network;

import java.util.Objects;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.serverpackets.ServerPacket;

/**
 * Owns outbound packet dispatch for a Player without exposing transport details
 * to alternate session implementations.
 */
public interface PlayerOutboundSession
{
	enum SessionKind
	{
		CLIENT_BOUND,
		HEADLESS
	}

	SessionKind kind();

	void send(Player player, ServerPacket packet);

	static PlayerOutboundSession clientBound()
	{
		return ClientBoundHolder.INSTANCE;
	}

	final class ClientBoundHolder
	{
		private static final PlayerOutboundSession INSTANCE = new PlayerOutboundSession()
		{
			@Override
			public SessionKind kind()
			{
				return SessionKind.CLIENT_BOUND;
			}

			@Override
			public void send(Player player, ServerPacket packet)
			{
				Objects.requireNonNull(player, "player");
				Objects.requireNonNull(packet, "packet");

				final GameClient client = player.getClient();
				if (client != null)
				{
					client.sendPacket(packet);
				}
			}
		};

		private ClientBoundHolder()
		{
		}
	}
}

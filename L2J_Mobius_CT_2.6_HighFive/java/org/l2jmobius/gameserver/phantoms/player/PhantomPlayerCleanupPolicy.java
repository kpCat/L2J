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
package org.l2jmobius.gameserver.phantoms.player;

import java.util.Objects;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/**
 * Read-only postcondition shared by real-login and Phantom Player cleanup.
 */
public final class PhantomPlayerCleanupPolicy
{
	private PhantomPlayerCleanupPolicy()
	{
	}

	public static boolean isComplete(Player player)
	{
		Objects.requireNonNull(player, "player");
		final World world = World.getInstance();
		return !player.isOnline() //
			&& (world.getPlayer(player.getObjectId()) != player) //
			&& (world.findObject(player.getObjectId()) != player) //
			&& !PlayerAutoSaveTaskManager.getInstance().contains(player) //
			&& (player.getClient() == null);
	}
}

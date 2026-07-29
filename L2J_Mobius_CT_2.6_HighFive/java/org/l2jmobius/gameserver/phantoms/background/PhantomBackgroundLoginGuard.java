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
package org.l2jmobius.gameserver.phantoms.background;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/**
 * Read-only pre-load guard. A real login may not bypass a durable background
 * state that still owns canonical offline progress.
 */
public final class PhantomBackgroundLoginGuard
{
	private PhantomBackgroundLoginGuard()
	{
	}

	public static Decision inspect(int characterObjectId)
	{
		try
		{
			final PhantomProfile profile = PhantomProfileRepository.open().findByCharacterObjectId(characterObjectId).orElse(null);
			if (profile == null)
			{
				return Decision.ALLOW_ABSENT;
			}
			final PhantomBackgroundTransaction.Result loaded = new PhantomBackgroundTransaction().load(profile.profileId());
			if (loaded.status() == PhantomBackgroundTransaction.Status.STATE_ABSENT)
			{
				return Decision.ALLOW_ABSENT;
			}
			if (!loaded.successful() || (loaded.state() == null))
			{
				return Decision.REJECT_UNVERIFIED;
			}
			return loaded.state().state() == State.MATERIALIZED ? Decision.ALLOW_MATERIALIZED : Decision.REJECT_BACKGROUND_OWNED;
		}
		catch (RuntimeException exception)
		{
			return Decision.REJECT_UNVERIFIED;
		}
	}

	public enum Decision
	{
		ALLOW_ABSENT,
		ALLOW_MATERIALIZED,
		REJECT_BACKGROUND_OWNED,
		REJECT_UNVERIFIED;

		public boolean allowed()
		{
			return (this == ALLOW_ABSENT) || (this == ALLOW_MATERIALIZED);
		}

		public boolean requiresArbitration()
		{
			return this != ALLOW_ABSENT;
		}
	}
}

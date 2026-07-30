/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.Objects;

/**
 * Read-only ownership boundary used by background simulation.
 */
@FunctionalInterface
public interface PhantomPartyParticipationPort
{
	boolean blocksBackground(long profileId);

	static PhantomPartyParticipationPort noop()
	{
		return _ -> false;
	}

	static Bridge bridge()
	{
		return new Bridge();
	}

	final class Bridge implements PhantomPartyParticipationPort
	{
		private volatile PhantomPartyParticipationPort _delegate = PhantomPartyParticipationPort.noop();

		public void install(PhantomPartyParticipationPort delegate)
		{
			_delegate = Objects.requireNonNull(delegate, "Party participation delegate must not be null.");
		}

		@Override
		public boolean blocksBackground(long profileId)
		{
			return _delegate.blocksBackground(profileId);
		}
	}
}

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
package org.l2jmobius.gameserver.phantoms.topology;

/**
 * Fixed per-profile ownership for the three topology scheduler sources.
 */
final class PhantomTopologySignalLedger
{
	enum SourceState
	{
		NEVER_SUBMITTED,
		POSSIBLY_ACTIVE,
		INACTIVE_CONFIRMED,
		OWNERSHIP_UNCERTAIN
	}

	private final long _profileId;
	private long _localChatSequence;
	private long _combatSequence;
	private long _targetabilitySequence;
	private SourceState _localChatState = SourceState.NEVER_SUBMITTED;
	private SourceState _combatState = SourceState.NEVER_SUBMITTED;
	private SourceState _targetabilityState = SourceState.NEVER_SUBMITTED;
	private boolean _cleanupPending;
	private boolean _cleanupInFlight;

	PhantomTopologySignalLedger(long profileId)
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("Topology signal ledger profile ID must be positive.");
		}
		_profileId = profileId;
	}

	long profileId()
	{
		return _profileId;
	}

	Long allocateSequence(String sourceKey)
	{
		return switch (sourceKey)
		{
			case PhantomPerceptionProvider.LOCAL_CHAT_SOURCE ->
			{
				if (_localChatSequence == Long.MAX_VALUE)
				{
					yield null;
				}
				yield ++_localChatSequence;
			}
			case PhantomPerceptionProvider.COMBAT_SOURCE ->
			{
				if (_combatSequence == Long.MAX_VALUE)
				{
					yield null;
				}
				yield ++_combatSequence;
			}
			case PhantomPerceptionProvider.TARGETABILITY_SOURCE ->
			{
				if (_targetabilitySequence == Long.MAX_VALUE)
				{
					yield null;
				}
				yield ++_targetabilitySequence;
			}
			default -> throw new IllegalArgumentException("Unsupported topology signal source: " + sourceKey);
		};
	}

	SourceState sourceState(String sourceKey)
	{
		return switch (sourceKey)
		{
			case PhantomPerceptionProvider.LOCAL_CHAT_SOURCE -> _localChatState;
			case PhantomPerceptionProvider.COMBAT_SOURCE -> _combatState;
			case PhantomPerceptionProvider.TARGETABILITY_SOURCE -> _targetabilityState;
			default -> throw new IllegalArgumentException("Unsupported topology signal source: " + sourceKey);
		};
	}

	void sourceState(String sourceKey, SourceState state)
	{
		switch (sourceKey)
		{
			case PhantomPerceptionProvider.LOCAL_CHAT_SOURCE -> _localChatState = state;
			case PhantomPerceptionProvider.COMBAT_SOURCE -> _combatState = state;
			case PhantomPerceptionProvider.TARGETABILITY_SOURCE -> _targetabilityState = state;
			default -> throw new IllegalArgumentException("Unsupported topology signal source: " + sourceKey);
		}
	}

	boolean cleanupPending()
	{
		return _cleanupPending;
	}

	void cleanupPending(boolean cleanupPending)
	{
		_cleanupPending = cleanupPending;
	}

	boolean cleanupInFlight()
	{
		return _cleanupInFlight;
	}

	void cleanupInFlight(boolean cleanupInFlight)
	{
		_cleanupInFlight = cleanupInFlight;
	}

	boolean isEmptyReservation()
	{
		return (_localChatSequence == 0) && (_combatSequence == 0) && (_targetabilitySequence == 0) && (_localChatState == SourceState.NEVER_SUBMITTED) && (_combatState == SourceState.NEVER_SUBMITTED) && (_targetabilityState == SourceState.NEVER_SUBMITTED) && !_cleanupPending && !_cleanupInFlight;
	}
}

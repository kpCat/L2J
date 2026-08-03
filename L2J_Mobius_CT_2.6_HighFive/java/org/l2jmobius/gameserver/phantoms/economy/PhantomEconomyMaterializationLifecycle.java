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
package org.l2jmobius.gameserver.phantoms.economy;

import java.time.Clock;
import java.util.Objects;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;

/** Blocks lifecycle crossings while canonical economy dispatch is uncertain. */
public final class PhantomEconomyMaterializationLifecycle implements PhantomMaterializationLifecyclePort
{
	private final PhantomEconomyReservationService _reservations;
	private final PhantomEconomyOfferService _offers;
	private final Clock _clock;

	public PhantomEconomyMaterializationLifecycle(PhantomEconomyReservationService reservations, Clock clock)
	{
		this(reservations, null, clock);
	}

	public PhantomEconomyMaterializationLifecycle(PhantomEconomyReservationService reservations, PhantomEconomyOfferService offers, Clock clock)
	{
		_reservations = Objects.requireNonNull(reservations);
		_offers = offers;
		_clock = Objects.requireNonNull(clock);
	}

	@Override
	public void beforeMaterialize(long profileId, int characterObjectId)
	{
		_reservations.beforeBoundary(profileId, _clock.millis());
	}

	@Override
	public void afterPlayerLoad(long profileId, Player player)
	{
	}

	@Override
	public void materializeSucceeded(long profileId, int characterObjectId)
	{
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		if (((_offers != null) && _offers.blocksMaterialization(profileId)) || (player.getPrivateStoreType() != PrivateStoreType.NONE))
		{
			throw new PhantomEconomyReservationService.EconomyConflictException("A visible economy interaction blocks dematerialization.");
		}
		_reservations.beforeBoundary(profileId, _clock.millis());
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
	}
}

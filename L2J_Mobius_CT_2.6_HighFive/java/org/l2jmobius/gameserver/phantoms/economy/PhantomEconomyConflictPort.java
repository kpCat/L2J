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

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService.WriterClaim;

/** Installed narrow seam used by accepted Phantom-owned item writers. */
public final class PhantomEconomyConflictPort
{
	private static final Port EMPTY = new Port()
	{
		@Override
		public Claim claim(long profileId, String ownOperationId, List<Reservation> resources)
		{
			return Claim.ACQUIRED;
		}

		@Override
		public void requireNoConflict(Connection connection, String ownOperationId, List<Reservation> resources)
		{
		}

		@Override
		public PhantomEconomyReservationService owner()
		{
			return null;
		}
	};
	private static final AtomicReference<Port> PORT = new AtomicReference<>(EMPTY);

	private PhantomEconomyConflictPort()
	{
	}

	public static void install(PhantomEconomyReservationService service)
	{
		Objects.requireNonNull(service);
		final Port installed = new Port()
		{
			@Override
			public Claim claim(long profileId, String ownOperationId, List<Reservation> resources)
			{
				final WriterClaim claim = service.claimWriter(profileId, ownOperationId, resources);
				return claim.acquired() ? new Claim(true, claim) : Claim.CONFLICT;
			}

			@Override
			public void requireNoConflict(Connection connection, String ownOperationId, List<Reservation> resources) throws SQLException
			{
				service.requireNoConflict(connection, ownOperationId, resources);
			}

			@Override
			public PhantomEconomyReservationService owner()
			{
				return service;
			}
		};
		if (!PORT.compareAndSet(EMPTY, installed))
		{
			throw new IllegalStateException("Economy conflict port is already installed.");
		}
	}

	public static Claim claim(long profileId, String ownOperationId, List<Reservation> resources)
	{
		return PORT.get().claim(profileId, ownOperationId, resources);
	}

	public static boolean isInstalled()
	{
		return PORT.get() != EMPTY;
	}

	public static void requireNoConflict(Connection connection, String ownOperationId, List<Reservation> resources) throws SQLException
	{
		PORT.get().requireNoConflict(connection, ownOperationId, resources);
	}

	public static void resetForTesting()
	{
		PORT.set(EMPTY);
	}

	public static void uninstall(PhantomEconomyReservationService service)
	{
		final Port current = PORT.get();
		if ((current.owner() == service) && !PORT.compareAndSet(current, EMPTY))
		{
			throw new IllegalStateException("Economy conflict port changed concurrently.");
		}
	}

	private interface Port
	{
		Claim claim(long profileId, String ownOperationId, List<Reservation> resources);

		void requireNoConflict(Connection connection, String ownOperationId, List<Reservation> resources) throws SQLException;

		PhantomEconomyReservationService owner();
	}

	public static final class Claim implements AutoCloseable
	{
		private static final Claim ACQUIRED = new Claim(true, null);
		private static final Claim CONFLICT = new Claim(false, null);
		private final boolean _acquired;
		private final AutoCloseable _delegate;

		private Claim(boolean acquired, AutoCloseable delegate)
		{
			_acquired = acquired;
			_delegate = delegate;
		}

		public boolean acquired()
		{
			return _acquired;
		}

		@Override
		public void close()
		{
			if (_delegate == null)
			{
				return;
			}
			try
			{
				_delegate.close();
			}
			catch (RuntimeException exception)
			{
				throw exception;
			}
			catch (Exception exception)
			{
				throw new IllegalStateException("Could not close economy writer claim.", exception);
			}
		}
	}
}

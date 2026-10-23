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
package org.l2jmobius.gameserver.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.events.Containers;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.ListenersContainer;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogin;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerLogout;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;

/**
 * @author Mobius
 */
public class PremiumManager
{
	private static final Logger LOGGER = Logger.getLogger(PremiumManager.class.getName());
	
	private static final String LOAD_SQL = "SELECT account_name,enddate FROM account_premium WHERE account_name = ?";
	private static final String INSERT_SQL = "INSERT IGNORE INTO account_premium (account_name,enddate) VALUES (?,0)";
	private static final String LOCK_SQL = "SELECT enddate FROM account_premium WHERE account_name=? FOR UPDATE";
	private static final String UPDATE_SQL = "UPDATE account_premium SET enddate=? WHERE account_name=?";
	private static final String DELETE_SQL = "DELETE FROM account_premium WHERE account_name = ?";
	
	class PremiumExpireTask implements Runnable
	{
		final String _accountName;
		final long _expiration;
		
		PremiumExpireTask(String accountName, long expiration)
		{
			_accountName = accountName;
			_expiration = expiration;
		}
		
		@Override
		public void run()
		{
			if ((getPremiumExpiration(_accountName) != _expiration) || (System.currentTimeMillis() < _expiration))
			{
				return;
			}
			for (Player player : World.getInstance().getPlayers())
			{
				if (_accountName.equalsIgnoreCase(player.getAccountName()))
				{
					player.setPremiumStatus(false);
				}
			}
		}
	}
	
	// Data Cache
	private final Map<String, Long> _premiumData = new ConcurrentHashMap<>();
	
	// expireTasks
	private final Map<String, ScheduledFuture<?>> _expiretasks = new ConcurrentHashMap<>();
	
	// Listeners
	private final ListenersContainer _listenerContainer = Containers.Players();
	
	private final Consumer<OnPlayerLogin> _playerLoginEvent = event ->
	{
		final Player player = event.getPlayer();
		final String accountName = player.getAccountName();
		loadPremiumData(accountName);
		final long now = System.currentTimeMillis();
		final long premiumExpiration = getPremiumExpiration(accountName);
		player.setPremiumStatus((premiumExpiration > now) && !player.hasHeadlessOutboundSession() && !player.isNativeWorkManaged());
		if (player.hasPremiumStatus())
		{
			startExpireTask(player, premiumExpiration - now);
		}
	};
	
	private final Consumer<OnPlayerLogout> _playerLogoutEvent = event ->
	{
		stopExpireTask(event.getPlayer());
	};
	
	protected PremiumManager()
	{
		_listenerContainer.addListener(new ConsumerEventListener(_listenerContainer, EventType.ON_PLAYER_LOGIN, _playerLoginEvent, this));
		_listenerContainer.addListener(new ConsumerEventListener(_listenerContainer, EventType.ON_PLAYER_LOGOUT, _playerLogoutEvent, this));
	}
	
	/**
	 * @param player
	 * @param delay
	 */
	private void startExpireTask(Player player, long delay)
	{
		final String accountName = player.getAccountName().toLowerCase(Locale.ROOT);
		final ScheduledFuture<?> previous = _expiretasks.put(accountName, ThreadPool.schedule(new PremiumExpireTask(accountName, getPremiumExpiration(accountName)), delay));
		if (previous != null)
		{
			previous.cancel(false);
		}
	}
	
	/**
	 * @param player
	 */
	private void stopExpireTask(Player player)
	{
		if (World.getInstance().getPlayers().stream().anyMatch(other -> (other != player) && other.isOnline() && player.getAccountName().equalsIgnoreCase(other.getAccountName())))
		{
			return;
		}
		ScheduledFuture<?> task = _expiretasks.remove(player.getAccountName().toLowerCase(Locale.ROOT));
		if (task != null)
		{
			task.cancel(false);
			task = null;
		}
	}
	
	private void loadPremiumData(String accountName)
	{
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement stmt = con.prepareStatement(LOAD_SQL))
		{
			stmt.setString(1, accountName.toLowerCase(Locale.ROOT));
			try (ResultSet rset = stmt.executeQuery())
			{
				while (rset.next())
				{
					_premiumData.merge(rset.getString(1).toLowerCase(Locale.ROOT), rset.getLong(2), Math::max);
				}
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("Problem with PremiumManager: " + e.getMessage());
		}
	}
	
	public long getPremiumExpiration(String accountName)
	{
		return _premiumData.getOrDefault(accountName.toLowerCase(Locale.ROOT), 0L);
	}

	/** Extends the account row inside the caller's native currency transaction; publishes no in-memory benefit. */
	public long extendPremium(Connection connection, String accountName, int days, long now) throws SQLException
	{
		if ((days != 1) && (days != 3) && (days != 7) && (days != 15) && (days != 20) && (days != 30))
		{
			throw new IllegalArgumentException("Unsupported premium duration.");
		}
		return extendPremiumMillis(connection, accountName, TimeUnit.DAYS.toMillis(days), now);
	}

	private long extendPremiumMillis(Connection connection, String accountName, long duration, long now) throws SQLException
	{
		if (connection.getAutoCommit() || (accountName == null) || accountName.isBlank() || (accountName.length() > 45) || (duration <= 0) || (now < 0))
		{
			throw new IllegalArgumentException("Premium requires an explicit account transaction and positive real-time duration.");
		}
		final String account = accountName.toLowerCase(Locale.ROOT);
		try (PreparedStatement insert = connection.prepareStatement(INSERT_SQL))
		{
			insert.setString(1, account);
			insert.executeUpdate();
		}
		final long expiration;
		try (PreparedStatement lock = connection.prepareStatement(LOCK_SQL))
		{
			lock.setString(1, account);
			try (ResultSet row = lock.executeQuery())
			{
				if (!row.next())
				{
					throw new SQLException("Premium account row was not locked.");
				}
				expiration = Math.addExact(Math.max(now, row.getLong(1)), duration);
			}
		}
		try (PreparedStatement update = connection.prepareStatement(UPDATE_SQL))
		{
			update.setLong(1, expiration);
			update.setString(2, account);
			if (update.executeUpdate() != 1)
			{
				throw new SQLException("Premium account row was not updated.");
			}
		}
		return expiration;
	}

	/** Call only after the durable transaction commits. Concurrent paid extensions cannot shorten the cache. */
	public void publishPremiumExpiration(String accountName, long expiration)
	{
		final String account = accountName.toLowerCase(Locale.ROOT);
		final long currentExpiration = _premiumData.merge(account, expiration, Math::max);
		final long now = System.currentTimeMillis();
		for (Player player : World.getInstance().getPlayers())
		{
			if (account.equalsIgnoreCase(player.getAccountName()))
			{
				player.setPremiumStatus((currentExpiration > now) && !player.hasHeadlessOutboundSession() && !player.isNativeWorkManaged());
				if (player.hasPremiumStatus())
				{
					startExpireTask(player, currentExpiration - now);
				}
			}
		}
	}
	
	public void addPremiumTime(String accountName, int timeValue, TimeUnit timeUnit)
	{
		try (Connection con = DatabaseFactory.getConnection())
		{
			con.setAutoCommit(false);
			try
			{
				final long expiration = extendPremiumMillis(con, accountName, timeUnit.toMillis(timeValue), System.currentTimeMillis());
				con.commit();
				publishPremiumExpiration(accountName, expiration);
			}
			catch (SQLException | RuntimeException e)
			{
				con.rollback();
				throw e;
			}
		}
		catch (SQLException e)
		{
			LOGGER.warning("Problem with PremiumManager: " + e.getMessage());
		}

	}

	public void removePremiumStatus(String accountName, boolean checkOnline)
	{
		if (checkOnline)
		{
			for (Player player : World.getInstance().getPlayers())
			{
				if (accountName.equalsIgnoreCase(player.getAccountName()) && player.hasPremiumStatus())
				{
					player.setPremiumStatus(false);
					stopExpireTask(player);
					break;
				}
			}
		}
		
		// UPDATE CACHE
		_premiumData.remove(accountName.toLowerCase());
		
		// UPDATE DATABASE
		try (Connection con = DatabaseFactory.getConnection();
			PreparedStatement stmt = con.prepareStatement(DELETE_SQL))
		{
			stmt.setString(1, accountName.toLowerCase());
			stmt.execute();
		}
		catch (SQLException e)
		{
			LOGGER.warning("Problem with PremiumManager: " + e.getMessage());
		}
	}
	
	public static PremiumManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final PremiumManager INSTANCE = new PremiumManager();
	}
}

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
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.zaxxer.hikari.HikariDataSource;

import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.managers.ItemManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;

/** An ordinary native duplicate-stack restore with one original pool entry available. */
public final class PhantomM1PoolRestoreChecks
{
	private static final int STACK_ITEM_ID = 57;
	
	private PhantomM1PoolRestoreChecks()
	{
	}
	
	/** The caller owns the temporary third character and its canonical deletion. */
	public static void ordinaryDuplicate(PhantomTestContext context, Player third, int primaryId, int observerId, Consumer<Player> disposeLoaded) throws Exception
	{
		PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, context.measurements().get("headless.database"), "Pool restore requires initialized guarded TEST.");
		PhantomAssertions.assertTrue(primaryId > 0 && observerId > 0 && third.getObjectId() > 0 && third.getObjectId() != primaryId && third.getObjectId() != observerId, "Pool restore requires an exact temporary third identity.");
		PhantomAssertions.assertEquals(4, Math.min(Math.max(DatabaseConfig.DATABASE_MAX_CONNECTIONS, 4), 1000), "Pool restore requires the original four-entry pool.");
		final var poolField = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
		poolField.setAccessible(true); // Same temporary forwarding seam as the existing native SQL diagnostic.
		final HikariDataSource pool = (HikariDataSource) poolField.get(null);
		PhantomAssertions.assertTrue(pool != null, "Original native pool is not initialized.");
		PhantomAssertions.assertEquals(4, pool.getMaximumPoolSize(), "Actual original pool capacity differs from the focused control.");
		context.record("poolRestore.actualPoolCapacity", pool.getMaximumPoolSize());
		PhantomAssertions.assertEquals(0L, canonical(third.getObjectId())[0], "Temporary third already owns the duplicate fixture stack.");
		final List<Item> seeds = new ArrayList<>();
		final AtomicReference<Player> loaded = new AtomicReference<>();
		final AtomicReference<Throwable> loadFailure = new AtomicReference<>();
		final AtomicReference<Thread> workerIdentity = new AtomicReference<>();
		final CountDownLatch completed = new CountDownLatch(1);
		Thread worker = null;
		InventorySelectGate selectGate = null;
		Throwable primary = null;
		try
		{
			for (long count : new long[] { 2, 3 })
			{
				final Item item = ItemManager.createItem(ItemProcessType.REWARD, STACK_ITEM_ID, count, third, PhantomM1PoolRestoreChecks.class);
				seeds.add(item);
				PhantomAssertions.assertTrue(item.isStackable(), "Native duplicate fixture item is not stackable.");
				item.setOwnerId(ItemProcessType.REWARD, third.getObjectId(), third, PhantomM1PoolRestoreChecks.class);
				item.setItemLocation(ItemLocation.INVENTORY);
				item.updateDatabase(true);
				World.getInstance().removeObject(item);
			}
			final long[] before = canonical(third.getObjectId());
			PhantomAssertions.assertEquals(2L, before[0], "Native duplicate seed did not persist two distinct canonical objects.");
			PhantomAssertions.assertEquals(5L, before[1], "Native duplicate seed count is not exact.");
			third.stopAllTasks();
			third.deleteMe();
			boolean completedWithLeases;
			List<String> blockedStack = List.of();
			selectGate = new InventorySelectGate(poolField, pool, third.getObjectId(), workerIdentity);
			worker = new Thread(() ->
			{
				try { loaded.set(Player.load(third.getObjectId())); }
				catch (Throwable failure) { loadFailure.set(failure); }
				finally { completed.countDown(); }
			}, "TEST-native-pool-restore");
			workerIdentity.set(worker);
			worker.start();
			final boolean selectEntered = selectGate.await(3, TimeUnit.SECONDS);
			context.record("poolRestore.inventorySelectEntered", selectEntered);
			context.record("poolRestore.inventorySelectOriginalStack", selectGate.enteredStack());
			if (!selectEntered) { context.record("poolRestore.beforeSelectWorkerStack", frames(worker.getStackTrace())); }
			if (loadFailure.get() instanceof Error error) { throw error; }
			if (loadFailure.get() instanceof Exception failure) { throw failure; }
			PhantomAssertions.assertTrue(selectEntered, "INVALID fixture: original third Player.load did not reach its native Inventory SELECT before parent leases.");
			try (Connection first = DatabaseFactory.getConnection();
				Connection second = DatabaseFactory.getConnection();
				Connection thirdLease = DatabaseFactory.getConnection())
			{
				context.record("poolRestore.originalParentLeases", 3);
				selectGate.release();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
				while (completed.getCount() != 0 && System.nanoTime() < deadline)
				{
					final StackTraceElement[] frames = worker.getStackTrace();
					if (blockedStack.isEmpty() && nativeMergeWait(frames))
					{
						blockedStack = frames(frames);
					}
					completed.await(10, TimeUnit.MILLISECONDS);
				}
				completedWithLeases = completed.getCount() == 0;
				context.record("poolRestore.completedBeforeLeaseRelease", completedWithLeases);
				context.record("poolRestore.originalMergeAcquireStack", blockedStack);
				context.record("poolRestore.beforeLeaseReleaseWorkerStack", frames(worker.getStackTrace()));
				if (completedWithLeases)
				{
					PhantomAssertions.assertTrue(loadFailure.get() == null && loaded.get() != null, "Original native Player.load failed.");
					PhantomAssertions.assertEquals(5L, loaded.get().getInventory().getInventoryItemCount(STACK_ITEM_ID, -1, true), "Original duplicate merge runtime count changed.");
					PhantomAssertions.assertEquals(1L, canonical(third.getObjectId())[0], "Original duplicate merge did not delete exactly one canonical object.");
				}
			}
			PhantomAssertions.assertTrue(completed.await(10, TimeUnit.SECONDS), "Native load did not finish after original leases were released.");
			if (loadFailure.get() instanceof Error error) { throw error; }
			if (loadFailure.get() instanceof Exception failure) { throw failure; }
			PhantomAssertions.assertTrue(loaded.get() != null, "Original native Player.load returned no Player.");
			PhantomAssertions.assertEquals(5L, loaded.get().getInventory().getInventoryItemCount(STACK_ITEM_ID, -1, true), "Original duplicate merge runtime count changed after release.");
			PhantomAssertions.assertEquals(1L, canonical(third.getObjectId())[0], "Original duplicate merge did not delete exactly one canonical object after release.");
			loaded.get().storeMe();
			final long[] afterStore = canonical(third.getObjectId());
			PhantomAssertions.assertEquals(1L, afterStore[0], "Native store recreated a duplicate canonical object.");
			PhantomAssertions.assertEquals(5L, afterStore[1], "Native store did not persist exact merged count.");
			context.record("poolRestore.nativeMergeAndStore", "runtimeCount=5 canonicalRows=1 canonicalCount=5 originalNative=true");
			PhantomAssertions.assertTrue(completedWithLeases || !blockedStack.isEmpty(), "Incomplete native load lacks original duplicate SQL acquisition evidence; fixture is invalid.");
			PhantomAssertions.assertTrue(completedWithLeases, "Original duplicate merge retained the SELECT connection while borrowing another pool entry.");
		}
		catch (Throwable failure)
		{
			primary = failure;
			throw failure;
		}
		finally
		{
			try (InventorySelectGate restoreGate = selectGate)
			{
				if (selectGate != null) { selectGate.release(); }
				if (worker != null) { worker.join(10000); PhantomAssertions.assertTrue(!worker.isAlive(), "Original native pool restore worker remained active."); }
				if (loaded.get() != null) { disposeLoaded.accept(loaded.get()); }
				for (Item item : seeds)
				{
					if (World.getInstance().findObject(item.getObjectId()) == item) { World.getInstance().removeObject(item); }
				}
			}
			catch (Throwable cleanup)
			{
				if (primary != null) { if (cleanup != primary) { primary.addSuppressed(cleanup); } }
				else { throw cleanup; }
			}
		}
	}
	
	/** Forwards every original JDBC operation; only this exact worker/owner/statement waits before executeQuery. */
	private static final class InventorySelectGate implements AutoCloseable
	{
		private static final String INVENTORY_SELECT = "SELECT object_id, item_id, count, enchant_level, loc, loc_data, custom_type1, custom_type2, mana_left, time FROM items WHERE owner_id=? AND (loc=? OR loc=?) ORDER BY loc_data";
		private final java.lang.reflect.Field _field;
		private final HikariDataSource _original;
		private final HikariDataSource _forwarding;
		private final int _ownerId;
		private final AtomicReference<Thread> _worker;
		private final CountDownLatch _entered = new CountDownLatch(1);
		private final CountDownLatch _release = new CountDownLatch(1);
		private final AtomicBoolean _claimed = new AtomicBoolean();
		private volatile List<String> _enteredStack = List.of();

		private InventorySelectGate(java.lang.reflect.Field field, HikariDataSource original, int ownerId, AtomicReference<Thread> worker) throws Exception
		{
			_field = field; _original = original; _ownerId = ownerId; _worker = worker;
			_forwarding = new HikariDataSource()
			{
				@Override public Connection getConnection() throws java.sql.SQLException { return connection(_original.getConnection()); }
				@Override public boolean isClosed() { return _original.isClosed(); }
				@Override public void close() { _original.close(); }
			};
			PhantomAssertions.assertTrue(_field.get(null) == original && !original.isClosed(), "INVALID fixture: original pool changed before SELECT gate install.");
			_field.set(null, _forwarding);
		}

		private Connection connection(Connection delegate)
		{
			return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, (_, method, args) ->
			{
				try
				{
					final Object value = method.invoke(delegate, args);
					if (Thread.currentThread() == _worker.get() && method.getName().equals("prepareStatement") && args != null && args.length > 0 && INVENTORY_SELECT.equals(args[0]) && value instanceof PreparedStatement statement) { return statement(delegate, statement); }
					return value;
				}
				catch (java.lang.reflect.InvocationTargetException wrapped) { throw wrapped.getCause(); }
			});
		}

		private PreparedStatement statement(Connection connection, PreparedStatement delegate)
		{
			final AtomicInteger boundOwner = new AtomicInteger();
			return (PreparedStatement) java.lang.reflect.Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] { PreparedStatement.class }, (_, method, args) ->
			{
				if (Thread.currentThread() == _worker.get() && method.getName().equals("executeQuery") && boundOwner.get() == _ownerId && _claimed.compareAndSet(false, true))
				{
					final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
					boolean inventory = false; boolean load = false;
					for (var frame : stack)
					{
						inventory |= frame.getClassName().equals("org.l2jmobius.gameserver.model.itemcontainer.Inventory") && frame.getMethodName().equals("restore");
						load |= frame.getClassName().equals(Player.class.getName()) && frame.getMethodName().equals("restore");
					}
					PhantomAssertions.assertTrue(inventory && load && !connection.isClosed(), "INVALID fixture: exact Inventory SELECT lacks original load stack/live connection.");
					_enteredStack = frames(stack); _entered.countDown();
					PhantomAssertions.assertTrue(_release.await(5, TimeUnit.SECONDS), "INVALID fixture: original Inventory SELECT barrier timed out before lease acquisition.");
				}
				try
				{
					final Object value = method.invoke(delegate, args);
					if (method.getName().equals("setInt") && args != null && args.length == 2 && Integer.valueOf(1).equals(args[0])) { boundOwner.set((Integer) args[1]); }
					return value;
				}
				catch (java.lang.reflect.InvocationTargetException wrapped) { throw wrapped.getCause(); }
			});
		}

		private boolean await(long time, TimeUnit unit) throws InterruptedException { return _entered.await(time, unit); }
		private List<String> enteredStack() { return _enteredStack; }
		private void release() { _release.countDown(); }
		@Override public void close() throws Exception { release(); if (_field.get(null) == _forwarding) { _field.set(null, _original); } }
	}

	private static List<String> frames(StackTraceElement[] frames)
	{
		final List<String> result = new ArrayList<>();
		for (int i = 0; i < Math.min(32, frames.length); i++) { result.add(frames[i].getClassName() + "." + frames[i].getMethodName() + ":" + frames[i].getLineNumber()); }
		return List.copyOf(result);
	}

	private static boolean nativeMergeWait(StackTraceElement[] frames)
	{
		boolean acquire = false;
		boolean remove = false;
		boolean merge = false;
		boolean restore = false;
		for (StackTraceElement frame : frames)
		{
			acquire |= frame.getClassName().equals("com.zaxxer.hikari.pool.HikariPool") && frame.getMethodName().equals("getConnection");
			remove |= frame.getClassName().equals(Item.class.getName()) && frame.getMethodName().equals("removeFromDb");
			merge |= frame.getClassName().equals("org.l2jmobius.gameserver.model.itemcontainer.ItemContainer") && frame.getMethodName().equals("addItem");
			restore |= frame.getClassName().equals("org.l2jmobius.gameserver.model.itemcontainer.Inventory") && frame.getMethodName().equals("restore");
		}
		return acquire && remove && merge && restore;
	}
	
	private static long[] canonical(int ownerId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*),COALESCE(SUM(count),0) FROM items WHERE owner_id=? AND item_id=? AND loc='INVENTORY'"))
		{
			statement.setInt(1, ownerId);
			statement.setInt(2, STACK_ITEM_ID);
			try (ResultSet rows = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(rows.next(), "Native duplicate canonical receipt is missing.");
				return new long[] { rows.getLong(1), rows.getLong(2) };
			}
		}
	}
}

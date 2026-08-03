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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.l2jmobius.gameserver.data.xml.RecipeData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.recipe.ManufactureItem;
import org.l2jmobius.gameserver.model.item.recipe.RecipeList;
import org.l2jmobius.gameserver.network.holders.TradeItem;
import org.l2jmobius.gameserver.network.holders.TradeList;
import org.l2jmobius.gameserver.network.serverpackets.ExPrivateStoreSetWholeMsg;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgBuy;
import org.l2jmobius.gameserver.network.serverpackets.PrivateStoreMsgSell;
import org.l2jmobius.gameserver.network.serverpackets.RecipeShopMsg;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.services.PrivateStoreService;
import org.l2jmobius.gameserver.util.Broadcast;

/** Opens/restores/closes only visible materialized Phantom stores. */
public final class PhantomStoreService
{
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final ConcurrentHashMap<Long, AutoCloseable> _ownerObservers = new ConcurrentHashMap<>();
	private final LongAdder _opened = new LongAdder();
	private final LongAdder _closed = new LongAdder();

	public PhantomStoreService(PhantomProfileRepository profiles, PhantomMaterializationService materialization)
	{
		_profiles = Objects.requireNonNull(profiles);
		_materialization = Objects.requireNonNull(materialization);
	}

	public Result open(long profileId, PhantomActivityState activityState, PhantomStorePlan plan, long now)
	{
		Objects.requireNonNull(activityState);
		Objects.requireNonNull(plan);
		if (!activityState.requiresMaterialization() || (plan.expiresEpochMillis() <= now))
		{
			return Result.ACTIVE_REQUIRED;
		}
		final VersionedPlan saved = save(profileId, plan.withState(PhantomStorePlan.State.REQUESTED));
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease == null)
			{
				return Result.ACTIVE_REQUIRED;
			}
			final Player player = lease.player();
			if (!install(player, plan))
			{
				return Result.REJECTED;
			}
			update(profileId, saved.rowVersion(), plan.withState(PhantomStorePlan.State.OPEN));
			installOwnerObserver(profileId, player.getObjectId());
			_opened.increment();
			return Result.OPENED;
		}
	}

	public Result restore(long profileId, PhantomActivityState activityState, long now)
	{
		final VersionedPlan stored = find(profileId);
		return stored == null ? Result.REJECTED : open(profileId, activityState, stored.plan(), now);
	}

	public Result close(long profileId)
	{
		try (ActionLease lease = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if (lease != null)
			{
				final Player player = lease.player();
				player.getSellList().clear();
				player.getBuyList().clear();
				player.getManufactureItems().clear();
				player.setPrivateStoreType(PrivateStoreType.NONE);
				player.broadcastUserInfo();
			}
		}
		closeOwnerObserver(profileId);
		final VersionedPlan current = find(profileId);
		if (current != null)
		{
			_profiles.deleteComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, current.rowVersion());
		}
		_closed.increment();
		return Result.CLOSED;
	}

	public void shutdown()
	{
		long cursor = 0;
		for (int page = 0; page < 1000; page++)
		{
			final List<PhantomProfileRepository.ManagedProfile> plans = _profiles.listManagedAfter(PhantomStorePlan.COMPONENT_TYPE, cursor, 100);
			if (plans.isEmpty())
			{
				return;
			}
			for (PhantomProfileRepository.ManagedProfile managed : plans)
			{
				cursor = managed.profile().profileId();
				close(cursor);
			}
		}
		throw new IllegalStateException("Phantom store shutdown exceeded its bounded profile scan.");
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_opened.sum(), _closed.sum(), _ownerObservers.size());
	}

	private boolean install(Player player, PhantomStorePlan plan)
	{
		if (player.isInStoreMode() && (player.getPrivateStoreType() != PrivateStoreType.NONE))
		{
			return canonicalMatches(player, plan);
		}
		player.setStoreName(plan.title());
		switch (plan.type())
		{
			case SELL, PACKAGE_SELL ->
			{
				final TradeList list = player.getSellList();
				list.clear();
				list.setPackaged(plan.type() == PhantomStorePlan.Type.PACKAGE_SELL);
				for (PhantomStorePlan.Line line : plan.lines())
				{
					final Item item = player.getInventory().getItemByObjectId(line.objectOrRecipeId());
					if ((item == null) || (item.getId() != line.itemId()) || (item.getCount() < line.count()) || !item.isTradeable())
					{
						list.clear();
						return false;
					}
					list.addItem(line.objectOrRecipeId(), line.count(), line.price());
				}
				player.setPrivateStoreType(plan.type() == PhantomStorePlan.Type.PACKAGE_SELL ? PrivateStoreType.PACKAGE_SELL : PrivateStoreType.SELL);
			}
			case BUY ->
			{
				final TradeList list = player.getBuyList();
				list.clear();
				long total = 0;
				for (PhantomStorePlan.Line line : plan.lines())
				{
					total = Math.addExact(total, Math.multiplyExact(line.count(), line.price()));
					list.addItemByItemId(line.itemId(), line.count(), line.price());
				}
				if (total > player.getAdena())
				{
					list.clear();
					return false;
				}
				player.setPrivateStoreType(PrivateStoreType.BUY);
			}
			case MANUFACTURE ->
			{
				player.getManufactureItems().clear();
				for (PhantomStorePlan.Line line : plan.lines())
				{
					final RecipeList recipe = RecipeData.getInstance().getRecipeList(line.objectOrRecipeId());
					if ((recipe == null) || (recipe.getItemId() != line.itemId()) || (!player.getDwarvenRecipeBook().contains(recipe) && !player.getCommonRecipeBook().contains(recipe)))
					{
						player.getManufactureItems().clear();
						return false;
					}
					player.getManufactureItems().put(recipe.getId(), new ManufactureItem(recipe.getId(), line.price()));
				}
				player.setPrivateStoreType(PrivateStoreType.MANUFACTURE);
			}
		}
		player.sitDown();
		player.broadcastUserInfo();
		switch (plan.type())
		{
			case SELL -> player.broadcastPacket(new PrivateStoreMsgSell(player));
			case PACKAGE_SELL -> player.broadcastPacket(new ExPrivateStoreSetWholeMsg(player));
			case BUY -> player.broadcastPacket(new PrivateStoreMsgBuy(player));
			case MANUFACTURE -> Broadcast.toSelfAndKnownPlayers(player, new RecipeShopMsg(player));
		}
		return true;
	}

	private static boolean canonicalMatches(Player player, PhantomStorePlan plan)
	{
		if (!player.getStoreName().equals(plan.title()))
		{
			return false;
		}
		return switch (plan.type())
		{
			case SELL, PACKAGE_SELL ->
			{
				final PrivateStoreType expected = plan.type() == PhantomStorePlan.Type.SELL ? PrivateStoreType.SELL : PrivateStoreType.PACKAGE_SELL;
				final List<TradeItem> actual = player.getSellList().getItems().stream().sorted(Comparator.comparingInt(TradeItem::getObjectId)).toList();
				final List<PhantomStorePlan.Line> planned = plan.lines().stream().sorted(Comparator.comparingInt(PhantomStorePlan.Line::objectOrRecipeId)).toList();
				yield (player.getPrivateStoreType() == expected) && (player.getSellList().isPackaged() == (expected == PrivateStoreType.PACKAGE_SELL)) && exactSell(actual, planned);
			}
			case BUY ->
			{
				final List<TradeItem> actual = player.getBuyList().getItems().stream().sorted(Comparator.comparingInt(item -> item.getItem().getId())).toList();
				final List<PhantomStorePlan.Line> planned = plan.lines().stream().sorted(Comparator.comparingInt(PhantomStorePlan.Line::itemId)).toList();
				yield (player.getPrivateStoreType() == PrivateStoreType.BUY) && exactBuy(actual, planned);
			}
			case MANUFACTURE -> (player.getPrivateStoreType() == PrivateStoreType.MANUFACTURE) && (player.getManufactureItems().size() == plan.lines().size()) && plan.lines().stream().allMatch(line ->
			{
				final ManufactureItem item = player.getManufactureItems().get(line.objectOrRecipeId());
				return (item != null) && (item.getCost() == line.price());
			});
		};
	}

	private static boolean exactSell(List<TradeItem> actual, List<PhantomStorePlan.Line> planned)
	{
		if (actual.size() != planned.size())
		{
			return false;
		}
		for (int i = 0; i < actual.size(); i++)
		{
			final TradeItem item = actual.get(i);
			final PhantomStorePlan.Line line = planned.get(i);
			if ((item.getObjectId() != line.objectOrRecipeId()) || (item.getItem().getId() != line.itemId()) || (item.getCount() != line.count()) || (item.getPrice() != line.price()))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean exactBuy(List<TradeItem> actual, List<PhantomStorePlan.Line> planned)
	{
		if (actual.size() != planned.size())
		{
			return false;
		}
		for (int i = 0; i < actual.size(); i++)
		{
			final TradeItem item = actual.get(i);
			final PhantomStorePlan.Line line = planned.get(i);
			if ((item.getItem().getId() != line.itemId()) || (item.getCount() != line.count()) || (item.getPrice() != line.price()))
			{
				return false;
			}
		}
		return true;
	}

	private void installOwnerObserver(long profileId, int ownerObjectId)
	{
		if (_ownerObservers.containsKey(profileId))
		{
			return;
		}
		final AutoCloseable lease = PrivateStoreService.getInstance().observeOwner(ownerObjectId, (owner, list, successful) ->
		{
			if (successful)
			{
				refresh(profileId, owner, list);
			}
		});
		final AutoCloseable previous = _ownerObservers.putIfAbsent(profileId, lease);
		if (previous != null)
		{
			close(lease);
		}
	}

	private void refresh(long profileId, Player owner, TradeList list)
	{
		final VersionedPlan stored = find(profileId);
		if ((stored == null) || (stored.plan().type() == PhantomStorePlan.Type.MANUFACTURE))
		{
			return;
		}
		final List<PhantomStorePlan.Line> remaining = new ArrayList<>();
		for (TradeItem item : list.getItems())
		{
			remaining.add(new PhantomStorePlan.Line(item.getObjectId() > 0 ? item.getObjectId() : item.getItem().getId(), item.getItem().getId(), item.getCount(), item.getPrice()));
		}
		if (remaining.isEmpty())
		{
			_profiles.deleteComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, stored.rowVersion());
			closeOwnerObserver(profileId);
		}
		else
		{
			update(profileId, stored.rowVersion(), stored.plan().withLines(remaining));
		}
	}

	private VersionedPlan save(long profileId, PhantomStorePlan plan)
	{
		final VersionedPlan existing = find(profileId);
		if (existing == null)
		{
			return decode(_profiles.insertComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, PhantomStorePlan.SCHEMA_VERSION, plan.encode()));
		}
		if (existing.plan().contentHash().equals(plan.contentHash()) && (existing.plan().state() == plan.state()))
		{
			return existing;
		}
		return update(profileId, existing.rowVersion(), plan);
	}

	private VersionedPlan update(long profileId, long rowVersion, PhantomStorePlan plan)
	{
		return decode(_profiles.updateComponent(profileId, PhantomStorePlan.COMPONENT_TYPE, rowVersion, PhantomStorePlan.SCHEMA_VERSION, plan.encode()));
	}

	private VersionedPlan find(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomStorePlan.COMPONENT_TYPE).map(PhantomStoreService::decode).orElse(null);
	}

	private static VersionedPlan decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomStorePlan.SCHEMA_VERSION)
		{
			throw new IllegalStateException("Unsupported Phantom store plan schema.");
		}
		return new VersionedPlan(component.rowVersion(), PhantomStorePlan.decode(component.payload()));
	}

	private void closeOwnerObserver(long profileId)
	{
		close(_ownerObservers.remove(profileId));
	}

	private static void close(AutoCloseable lease)
	{
		if (lease != null)
		{
			try
			{
				lease.close();
			}
			catch (Exception exception)
			{
				throw new IllegalStateException("Could not close Phantom store observer.", exception);
			}
		}
	}

	public enum Result
	{
		OPENED,
		CLOSED,
		ACTIVE_REQUIRED,
		REJECTED
	}

	private record VersionedPlan(long rowVersion, PhantomStorePlan plan)
	{
	}

	public record Snapshot(long opened, long closed, int retainedOwnerObservers)
	{
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.model.clan;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;

import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.instance.Item.WarehouseRow;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;

/** Exact native settlement for a single clan warehouse line. */
public final class ClanWarehouseTransfer
{
	static final long DEPOSIT_FEE = 30;
	private static final String COMPONENT = "clan.warehouse.transfer";
	private static final int VERSION = 1;

	enum Outcome { APPLIED, ROLLED_BACK, CONFLICT }

	private record Change(Item item, WarehouseRow before, WarehouseRow after)
	{
	}

	record Destination(Item item, boolean detached)
	{
		Destination { Objects.requireNonNull(item); }
	}

	private ClanWarehouseTransfer() { }

	static Move prepare(long profileId, int actorId, int clanId, int leaderId, UUID operation, Item source, Destination destination, Item adena, long count, boolean deposit)
	{
		final Item target = destination == null ? null : destination.item();
		final ItemLocation from = deposit ? ItemLocation.INVENTORY : ItemLocation.CLANWH;
		final ItemLocation to = deposit ? ItemLocation.CLANWH : ItemLocation.INVENTORY;
		final int fromOwner = deposit ? actorId : clanId;
		final int toOwner = deposit ? clanId : actorId;
		if (profileId <= 0 || actorId <= 0 || clanId <= 0 || leaderId <= 0 || actorId == clanId || operation == null || count <= 0
			|| !storedAt(source, fromOwner, from) || source.getCount() < count || source == target)
		{
			throw new IllegalArgumentException("Invalid clan warehouse source.");
		}
		if (deposit ? !storedAt(adena, actorId, ItemLocation.INVENTORY) || adena.getId() != Inventory.ADENA_ID
			|| !adena.isPlainWarehouseStack() || adena.getCount() < DEPOSIT_FEE : adena != null)
		{
			throw new IllegalArgumentException("Clan warehouse deposit fee unavailable.");
		}
		final long debit = source == adena ? Math.addExact(count, DEPOSIT_FEE) : count;
		final WarehouseRow sourceRow = source.warehouseRow();
		final List<Change> changes = new ArrayList<>(3);
		if (target == null)
		{
			if (source == adena || count != source.getCount() || source.isTimeLimitedItem() || source.isShadowItem()
				|| source.isStackable() && !source.isPlainWarehouseStack())
			{
				throw new IllegalArgumentException("Only a complete transferable warehouse object can retain its identity.");
			}
			changes.add(new Change(source, sourceRow, sourceRow.at(toOwner, to, count)));
		}
		else
		{
			if (!source.isPlainWarehouseStack() || !target.isPlainWarehouseStack() || target == adena || target.getObjectId() <= 0
				|| source.getId() != target.getId() || target.getOwnerId() != toOwner || target.getItemLocation() != to
				|| target.blocksInventoryDebit() || target.getObjectId() == source.getObjectId() || debit > source.getCount()
				|| destination.detached() == target.hasStoredWarehouseRow() || destination.detached() && target.getCount() != count)
			{
				throw new IllegalArgumentException("Invalid clan warehouse stack transfer.");
			}
			final WarehouseRow targetRow = target.warehouseRow();
			final WarehouseRow beforeTarget = destination.detached() ? null : targetRow;
			final long targetAfter = beforeTarget == null ? count : Math.addExact(target.getCount(), count);
			if (target.getId() == Inventory.ADENA_ID && targetAfter > Inventory.MAX_ADENA)
			{
				throw new IllegalArgumentException("Native Adena limit reached.");
			}
			changes.add(new Change(source, sourceRow, remaining(sourceRow, debit)));
			changes.add(new Change(target, beforeTarget, targetRow.at(toOwner, to, targetAfter)));
		}
		if (deposit && adena != source)
		{
			final WarehouseRow fee = adena.warehouseRow();
			changes.add(new Change(adena, fee, remaining(fee, DEPOSIT_FEE)));
		}
		changes.sort(Comparator.comparingInt(change -> change.item().getObjectId()));
		for (int index = 1; index < changes.size(); index++)
		{
			if (changes.get(index - 1).item().getObjectId() == changes.get(index).item().getObjectId())
			{
				throw new IllegalArgumentException("Duplicate native warehouse object identity.");
			}
		}
		return new Move(profileId, actorId, clanId, leaderId, operation, deposit, List.copyOf(changes));
	}

	private static boolean storedAt(Item item, int owner, ItemLocation location)
	{
		return item != null && item.getObjectId() > 0 && item.hasStoredWarehouseRow() && item.getOwnerId() == owner
			&& item.getItemLocation() == location && !item.blocksInventoryDebit() && item.getCount() > 0;
	}

	private static WarehouseRow remaining(WarehouseRow row, long debit)
	{
		final long count = row.item().count() - debit;
		return count == 0 ? null : row.at(row.ownerId(), row.item().loc(), count);
	}

	static final class Move
	{
		private final long _profileId;
		private final int _actorId;
		private final int _clanId;
		private final int _leaderId;
		private final List<Change> _changes;
		private final List<Item> _items;
		private final byte[] _evidence;
		private volatile boolean _pending;

		private Move(long profileId, int actorId, int clanId, int leaderId, UUID operation, boolean deposit, List<Change> changes)
		{
			_profileId = profileId;
			_actorId = actorId;
			_clanId = clanId;
			_leaderId = leaderId;
			_changes = changes;
			_items = changes.stream().map(Change::item).toList();
			final ByteBuffer evidence = ByteBuffer.allocate(64 + changes.size() * 112);
			evidence.putLong(operation.getMostSignificantBits()).putLong(operation.getLeastSignificantBits())
				.putLong(profileId).putInt(actorId).putInt(clanId).putInt(leaderId).put((byte) (deposit ? 1 : 0)).putInt(changes.size());
			for (Change change : changes)
			{
				evidence.putInt(change.item().getObjectId());
				append(evidence, change.before());
				append(evidence, change.after());
			}
			_evidence = Arrays.copyOf(evidence.array(), evidence.position());
		}

		boolean pending() { return _pending; }

		/** The caller holds native clan/container authority. No external reads in the commit predicate. */
		Outcome execute(BooleanSupplier stillAuthorized, Runnable publishContainers)
		{
			return Item.withWarehouseLocks(_items, () ->
			{
				if (_pending) { return settleLocked(publishContainers); }
				for (Change change : _changes)
				{
					final Item item = change.item();
					final WarehouseRow expected = change.before() == null ? change.after() : change.before();
					if (item.blocksInventoryDebit() || item.hasStoredWarehouseRow() != (change.before() != null)
						|| !expected.equals(item.warehouseRow())) { return Outcome.CONFLICT; }
				}
				try (Connection connection = DatabaseFactory.getConnection())
				{
					connection.setAutoCommit(false);
					try
					{
						stage(connection);
						if (!stillAuthorized.getAsBoolean())
						{
							connection.rollback();
							return Outcome.ROLLED_BACK;
						}
						setPending(true);
						connection.commit();
					}
					catch (SQLException failure)
					{
						try { connection.rollback(); } catch (SQLException rollback) { failure.addSuppressed(rollback); }
						throw failure;
					}
				}
				catch (SQLException failure)
				{
					return _pending ? settleLocked(publishContainers) : Outcome.ROLLED_BACK;
				}
				return finish(Outcome.APPLIED, publishContainers);
			});
		}

		Outcome settle(Runnable publishContainers)
		{
			return Item.withWarehouseLocks(_items, () -> _pending ? settleLocked(publishContainers) : Outcome.CONFLICT);
		}

		private Outcome settleLocked(Runnable publishContainers)
		{
			final Outcome outcome;
			try (Connection connection = DatabaseFactory.getConnection())
			{
				connection.setAutoCommit(false);
				outcome = readback(connection);
				connection.rollback();
			}
			catch (SQLException unavailable) { return Outcome.CONFLICT; }
			return finish(outcome, publishContainers);
		}

		private Outcome finish(Outcome outcome, Runnable publishContainers)
		{
			if (outcome == Outcome.CONFLICT) { return outcome; }
			if (outcome == Outcome.APPLIED)
			{
				try
				{
					publish();
					publishContainers.run();
				}
				catch (RuntimeException failure) { return Outcome.CONFLICT; }
			}
			setPending(false);
			return outcome;
		}

		private void setPending(boolean pending)
		{
			_items.forEach(item -> item.setWarehouseTransferPending(pending));
			_pending = pending;
		}

		void stage(Connection connection) throws SQLException
		{
			ClanFoundingDevelopment.requireOrganizationOwner(connection, _profileId, _actorId);
			if (hasReceipt(connection)) { throw new SQLException("Clan warehouse receipt already committed."); }
			try (var membership = connection.prepareStatement("SELECT clanid FROM characters WHERE charId=? FOR UPDATE"))
			{
				membership.setInt(1, _actorId);
				try (var row = membership.executeQuery())
				{
					if (!row.next() || row.getInt(1) != _clanId) { throw new SQLException("Clan warehouse member changed."); }
				}
			}
			try (var clan = connection.prepareStatement("SELECT clan_level FROM clan_data WHERE clan_id=? AND leader_id=? AND dissolving_expiry_time=0 FOR UPDATE"))
			{
				clan.setInt(1, _clanId);
				clan.setInt(2, _leaderId);
				try (var row = clan.executeQuery())
				{
					if (!row.next() || row.getInt(1) < 1) { throw new SQLException("Native clan warehouse is unavailable."); }
				}
			}
			for (Change change : _changes)
			{
				change.item().stageWarehouseRow(connection, change.before(), change.after());
			}
			try (var receipt = connection.prepareStatement("INSERT INTO phantom_profile_components (profile_id,component_type,component_schema_version,payload) VALUES (?,?,?,?) "
				+ "ON DUPLICATE KEY UPDATE component_schema_version=VALUES(component_schema_version),payload=VALUES(payload),row_version=row_version+1"))
			{
				receipt.setLong(1, _profileId);
				receipt.setString(2, COMPONENT);
				receipt.setInt(3, VERSION);
				receipt.setBytes(4, _evidence);
				receipt.executeUpdate();
			}
		}

		Outcome readback(Connection connection) throws SQLException
		{
			final boolean withReceipt = hasReceipt(connection);
			for (Change change : _changes)
			{
				final var liveRow = Item.readWarehouseRow(connection, change.item().getObjectId());
				if (!Objects.equals(liveRow, withReceipt ? change.after() : change.before())) { return Outcome.CONFLICT; }
			}
			return withReceipt ? Outcome.APPLIED : Outcome.ROLLED_BACK;
		}

		void publish()
		{
			for (Change change : _changes)
			{
				change.item().publishWarehouseRow(change.after());
			}
		}

		private boolean hasReceipt(Connection connection) throws SQLException
		{
			try (var receipt = connection.prepareStatement("SELECT component_schema_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
			{
				receipt.setLong(1, _profileId);
				receipt.setString(2, COMPONENT);
				try (var row = receipt.executeQuery())
				{
					if (!row.next()) { return false; }
					if (row.getInt(1) != VERSION) { throw new SQLException("Unsupported clan warehouse receipt schema."); }
					return Arrays.equals(row.getBytes(2), _evidence);
				}
			}
		}
	}

	private static void append(ByteBuffer bytes, WarehouseRow row)
	{
		bytes.put((byte) (row == null ? 0 : 1));
		if (row == null) { return; }
		final var item = row.item();
		bytes.putInt(row.ownerId()).putInt(item.objectId()).putInt(item.itemId()).putLong(item.count()).putInt(item.loc() == ItemLocation.INVENTORY ? 1 : 2)
			.putInt(item.locData()).putInt(item.enchantLevel()).putInt(item.customType1()).putInt(item.customType2()).putInt(item.manaLeft()).putLong(item.time());
	}
}

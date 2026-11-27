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
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.managers.ItemManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Warehouse;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.instance.Item.WarehouseRow;
import org.l2jmobius.gameserver.model.itemcontainer.ClanWarehouse;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.model.itemcontainer.ItemContainer;
import org.l2jmobius.gameserver.network.serverpackets.InventoryUpdate;
import org.l2jmobius.gameserver.network.serverpackets.StatusUpdate;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;

/** Exact native settlement for a single clan warehouse line. */
public final class ClanWarehouseTransfer
{
	static final long DEPOSIT_FEE = 30;
	private static final String COMPONENT = "clan.warehouse.transfer";
	private static final int VERSION = 1;

	enum Outcome { APPLIED, ROLLED_BACK, CONFLICT }

	public enum TransferOutcome { APPLIED, NOT_READY, UNCONFIRMED }

	public record Request(int clanId, int leaderId, int objectId, long count, boolean deposit, UUID operation, long expiresAt)
	{
		public Request
		{
			if (clanId <= 0 || leaderId <= 0 || objectId <= 0 || count <= 0 || operation == null || expiresAt <= 0)
			{
				throw new IllegalArgumentException("Incomplete clan warehouse request.");
			}
		}
	}

	private record Change(Item item, WarehouseRow before, WarehouseRow after, WarehouseRow nativeBefore)
	{
		Change(Item item, WarehouseRow before, WarehouseRow after)
		{
			this(item, before, after, before == null ? item.warehouseRow() : before);
		}
	}

	record Destination(Item item, boolean detached, boolean emptyWallet)
	{
		Destination { Objects.requireNonNull(item); }
		Destination(Item item, boolean detached) { this(item, detached, false); }
	}

	private ClanWarehouseTransfer() { }

	public static boolean canVisit(Player player, Warehouse npc)
	{
		return npc != null && npc.isSpawned() && !npc.isAlikeDead() && World.getInstance().findObject(npc.getObjectId()) == npc
			&& npc.canInteract(player) && GeoEngine.getInstance().canSeeTarget(player, npc);
	}

	public static boolean canWithdraw(Player player, Clan clan)
	{
		return PlayerConfig.ALT_MEMBERS_CAN_WITHDRAW_FROM_CLANWH
			? player.hasAccess(ClanAccess.ACCESS_WAREHOUSE) : clan.getLeaderId() == player.getObjectId();
	}

	private static boolean hasMembership(Player player, Clan clan, ClanWarehouse warehouse)
	{
		return clan != null && player.getClan() == clan && clan.getWarehouse() == warehouse
			&& ClanTable.getInstance().getClan(clan.getId()) == clan && clan.getLevel() > 0 && clan.getDissolvingExpiryTime() == 0
			&& clan.getClanMember(player.getObjectId()) != null;
	}

	/** Stock manual packets keep their existing transfer, serialized with the autonomous writer. */
	public static void withClientAccess(Player player, ClanWarehouse warehouse, Npc npc, boolean deposit, Runnable action)
	{
		final Clan clan = player.getClan();
		if (clan == null) { return; }
		final var fence = ClanSocialMutationFence.getInstance();
		fence.execute(new long[] { ClanSocialMutationFence.clanKey(clan.getId()) }, () ->
		{
			synchronized (player)
			{
				synchronized (warehouse)
				{
					if (!warehouse.hasPendingTransfer() && !fence.isRetiring(clan.getId()) && hasMembership(player, clan, warehouse)
						&& player.getActiveWarehouse() == warehouse && player.getActiveTradeList() == null
						&& (player.isGM() || npc != null && npc.isWarehouse() && npc.canInteract(player))
						&& (deposit || canWithdraw(player, clan)))
					{
						action.run();
					}
				}
			}
			return null;
		});
	}

	public static TransferOutcome transfer(long profileId, Player player, Warehouse npc, Request request, long reserve,
		BooleanSupplier current, BooleanSupplier localExecutionCurrent)
	{
		if (profileId <= 0 || player == null || reserve < 0 || !player.hasHeadlessOutboundSession() || player.isDedicatedGeoScout()
			|| !ClanFoundingDevelopment.authorizedAction(player, profileId) || !current.getAsBoolean()) { return TransferOutcome.NOT_READY; }
		if (player.getPendingClanWarehouse() != null) { return settlePending(player) ? TransferOutcome.NOT_READY : TransferOutcome.UNCONFIRMED; }
		final Clan clan = player.getClan();
		if (clan == null || clan.getId() != request.clanId() || !(clan.getWarehouse() instanceof ClanWarehouse warehouse)
			|| !canVisit(player, npc)) { return TransferOutcome.NOT_READY; }
		final var sourceContainer = request.deposit() ? player.getInventory() : warehouse;
		final Item source = sourceContainer.getItemByObjectId(request.objectId());
		if (source == null || source.getCount() < request.count()) { return TransferOutcome.NOT_READY; }
		final var resources = transferResources(profileId, player, source, request);
		try (var claim = PhantomEconomyConflictPort.claim(profileId, null, resources))
		{
			if (!claim.acquired() || !current.getAsBoolean()) { return TransferOutcome.NOT_READY; }
			// Claim the stock empty purse before taking the actor lock. Native inventory additions use the same purse allocator.
			if (!request.deposit() && source.getId() == Inventory.ADENA_ID && player.getInventory().getAdenaInstance() == null
				&& player.getInventory().validateCapacity(1) && mayAct(player, npc) && canWithdraw(player, clan))
			{
				player.getInventory().getAdenaForCountTransaction();
			}
			final var fence = ClanSocialMutationFence.getInstance();
			return fence.execute(new long[] { ClanSocialMutationFence.clanKey(clan.getId()) }, () ->
			{
				synchronized (player)
				{
					synchronized (warehouse)
					{
						final BooleanSupplier eligible = () -> System.currentTimeMillis() < request.expiresAt()
							&& !fence.isRetiring(clan.getId()) && hasMembership(player, clan, warehouse)
							&& clan.getLeaderId() == request.leaderId() && ClanFoundingDevelopment.authorizedAction(player, profileId)
							&& localExecutionCurrent.getAsBoolean() && mayAct(player, npc) && (request.deposit() || canWithdraw(player, clan));
						if (!eligible.getAsBoolean() || sourceContainer.getItemByObjectId(request.objectId()) != source) { return TransferOutcome.NOT_READY; }
						return transferLocked(profileId, player, clan, warehouse, request, reserve, resources, eligible);
					}
				}
			});
		}
	}

	private static boolean mayAct(Player player, Warehouse npc)
	{
		return !player.isGM() && player.getAccessLevel() != null && player.getAccessLevel().allowTransaction()
			&& (PlayerConfig.ALT_GAME_KARMA_PLAYER_CAN_USE_WAREHOUSE || player.getKarma() == 0) && !player.isAlikeDead()
			&& !player.isInCombat() && !player.isAttackingNow() && !player.isCastingNow() && !player.isMoving() && !player.isTeleporting()
			&& player.getParty() == null && !player.isInStoreMode() && !player.isProcessingTransaction() && !player.isInventoryDisabled()
			&& player.getActiveEnchantItemId() == Player.ID_NONE && player.getClassIndex() == 0 && !player.isChangingClass()
			&& !player.hasPendingOwnedStore() && canVisit(player, npc);
	}

	private static List<Reservation> transferResources(long profileId, Player player, Item source, Request request)
	{
		final int actorId = player.getObjectId();
		final List<Reservation> resources = new ArrayList<>(4);
		resources.add(new Reservation(profileId, source.getOwnerId(), ResourceKind.ITEM_OBJECT, source.getObjectId(), source.getId(),
			request.count(), source.getCount(), source.getEnchantLevel(), source.getItemLocation().name()));
		if (request.deposit() || source.getId() == Inventory.ADENA_ID)
		{
			resources.add(new Reservation(profileId, actorId, ResourceKind.ADENA, 0, Inventory.ADENA_ID, request.deposit() ? DEPOSIT_FEE : request.count(),
				player.getAdena(), 0, ItemLocation.INVENTORY.name()));
		}
		if (!request.deposit())
		{
			resources.add(new Reservation(profileId, actorId, ResourceKind.ITEM_COUNT, 0, source.getId(), request.count(),
				player.getInventory().getInventoryItemCount(source.getId(), -1), 0, ItemLocation.INVENTORY.name()));
			resources.add(new Reservation(profileId, actorId, ResourceKind.CAPACITY, 0, 0, 1, player.getInventory().getSize(), 0, ItemLocation.INVENTORY.name()));
		}
		return List.copyOf(resources);
	}

	static TransferOutcome transferLocked(long profileId, Player player, Clan clan, ClanWarehouse warehouse, Request request, long reserve,
		List<Reservation> resources, BooleanSupplier stillAuthorized)
	{
		if (!Thread.holdsLock(player) || !Thread.holdsLock(warehouse)) { throw new IllegalStateException("Native actor and warehouse locks are required."); }
		if (warehouse.hasPendingTransfer() || player.getPendingClanWarehouse() != null || player.getPendingClanPurchase() != null
			|| player.getPendingClanDevelopment() != null || player.getPendingClanDeparture() != null) { return TransferOutcome.NOT_READY; }
		final boolean deposit = request.deposit();
		final ItemContainer from = deposit ? player.getInventory() : warehouse;
		final ItemContainer to = deposit ? warehouse : player.getInventory();
		final Item source = from.getItemByObjectId(request.objectId());
		if (source == null || source.getCount() < request.count() || source.isQuestItem() || !source.isDepositable(false)
			|| source.getId() == Inventory.ANCIENT_ADENA_ID
			|| deposit && !source.isAvailable(player, true, false)) { return TransferOutcome.NOT_READY; }
		// Treasury upkeep assumes an available Adena stack while the residence or bid is active.
		final BooleanSupplier treasuryAvailable = () -> source.getId() != Inventory.ADENA_ID
			|| clan.getCastleId() == 0 && clan.getFortId() == 0 && clan.getHideoutId() == 0 && clan.getAuctionBiddedAt() == 0;
		if (!treasuryAvailable.getAsBoolean()) { return TransferOutcome.NOT_READY; }
		final Item fee = deposit ? player.getInventory().getAdenaInstance() : null;
		final long transferredAdena = deposit && source.getId() == Inventory.ADENA_ID ? request.count() : 0;
		final long protectedAdena = Math.max(1, reserve);
		final BooleanSupplier keepsReserve = () -> !deposit || fee != null && fee.getCount() >= protectedAdena
			&& fee.getCount() - protectedAdena >= DEPOSIT_FEE && fee.getCount() - protectedAdena - DEPOSIT_FEE >= transferredAdena;
		if (!keepsReserve.getAsBoolean()) { return TransferOutcome.NOT_READY; }
		Item target = null;
		if (source.isStackable())
		{
			final ItemLocation expected = deposit ? ItemLocation.CLANWH : ItemLocation.INVENTORY;
			target = to.getAllItemsByItemId(source.getId()).stream().filter(item -> item.getItemLocation() == expected)
				.min(Comparator.comparingInt(Item::getObjectId)).orElse(null);
		}
		if (!deposit && source.getId() == Inventory.ADENA_ID && (target == null || target != player.getInventory().getAdenaInstance()))
		{
			return TransferOutcome.NOT_READY;
		}
		final boolean emptyWallet = !deposit && target != null && target.getId() == Inventory.ADENA_ID
			&& !target.hasStoredWarehouseRow() && target.getCount() == 0;
		final int extraSlots = target == null ? 1 : 0;
		final BooleanSupplier canFit = () -> to.validateCapacity(extraSlots)
			&& (deposit || to.validateWeightByItemId(source.getId(), request.count()));
		if (!canFit.getAsBoolean()) { return TransferOutcome.NOT_READY; }
		Item detached = null;
		if (target == null && source.isStackable() && (request.count() < source.getCount() || source == fee))
		{
			detached = new Item(IdManager.getInstance().getNextId(), source.getTemplate());
			detached.setOwnerId(to.getOwnerId());
			detached.setItemLocation(deposit ? ItemLocation.CLANWH : ItemLocation.INVENTORY, 0, false);
			detached.setCount(request.count());
			target = detached;
		}
		final Move move;
		try
		{
			move = prepare(profileId, player.getObjectId(), clan.getId(), request.leaderId(), request.operation(), source,
				target == null ? null : new Destination(target, target == detached, emptyWallet), fee, request.count(), deposit);
		}
		catch (IllegalArgumentException | ArithmeticException failure)
		{
			if (detached != null) { IdManager.getInstance().releaseId(detached.getObjectId()); }
			return TransferOutcome.NOT_READY;
		}
		final var pending = new PendingTransfer(warehouse, move, source, target == null ? source : target, detached, request);
		player.setPendingClanWarehouse(pending);
		warehouse.setPendingTransfer(true);
		try
		{
			return finishTransfer(player, pending, move.execute(() -> stillAuthorized.getAsBoolean() && canFit.getAsBoolean()
				&& keepsReserve.getAsBoolean() && treasuryAvailable.getAsBoolean(),
				() -> pending.publishContainers(player), resources));
		}
		finally
		{
			if (!move.pending() && player.getPendingClanWarehouse() == pending) { clearTransfer(player, pending, false); }
		}
	}

	public static final class PendingTransfer
	{
		private final ClanWarehouse _warehouse;
		private final Move _move;
		private final Item _source;
		private final Item _target;
		private final Item _detached;
		private final Request _request;

		private PendingTransfer(ClanWarehouse warehouse, Move move, Item source, Item target, Item detached, Request request)
		{
			_warehouse = warehouse;
			_move = move;
			_source = source;
			_target = target;
			_detached = detached;
			_request = request;
		}

		private ItemContainer container(Player player, WarehouseRow row)
		{
			return row.item().loc() == ItemLocation.CLANWH ? _warehouse : player.getInventory();
		}

		private void publishContainers(Player player)
		{
			for (Change change : _move._changes)
			{
				final WarehouseRow before = change.before();
				final WarehouseRow after = change.after();
				if (before != null && (after == null || before.item().loc() != after.item().loc()))
				{
					container(player, before).publishClanWarehouseItem(change.item(), false);
				}
				if (after != null) { container(player, after).publishClanWarehouseItem(change.item(), true); }
			}
			player.getInventory().refreshClanWarehouseLoad();
		}
	}

	private static void clearTransfer(Player player, PendingTransfer pending, boolean applied)
	{
		if (!applied && pending._detached != null) { IdManager.getInstance().releaseId(pending._detached.getObjectId()); }
		pending._warehouse.setPendingTransfer(false);
		player.setPendingClanWarehouse(null);
	}

	private static TransferOutcome finishTransfer(Player player, PendingTransfer pending, Outcome outcome)
	{
		if (pending._move.pending()) { return TransferOutcome.UNCONFIRMED; }
		final boolean applied = outcome == Outcome.APPLIED;
		clearTransfer(player, pending, applied);
		if (!applied) { return TransferOutcome.NOT_READY; }
		final var inventory = new InventoryUpdate();
		for (Change change : pending._move._changes)
		{
			final Item item = change.item();
			final boolean before = change.before() != null && change.before().item().loc() == ItemLocation.INVENTORY;
			final boolean after = change.after() != null && change.after().item().loc() == ItemLocation.INVENTORY;
			if (before && after) { inventory.addModifiedItem(item); }
			else if (before) { inventory.addRemovedItem(item); }
			else if (after) { inventory.addNewItem(item); }
			if (change.after() == null) { ItemManager.destroyItem(ItemProcessType.TRANSFER, item, player, null); }
		}
		player.sendInventoryUpdate(inventory);
		final StatusUpdate status = new StatusUpdate(player);
		status.addAttribute(StatusUpdate.CUR_LOAD, player.getCurrentLoad());
		player.sendPacket(status);
		if (pending._request.deposit()) { pending._warehouse.notifyCommittedDeposit(player, pending._target); }
		else { pending._warehouse.notifyCommittedWithdrawal(player, pending._source, pending._request.count(), player.getInventory()); }
		return TransferOutcome.APPLIED;
	}

	/** STORE already owns the actor monitor. Settling needs no clan stripe or renewed NPC authority. */
	public static boolean settlePending(Player player)
	{
		synchronized (player)
		{
			final PendingTransfer pending = player.getPendingClanWarehouse();
			if (pending == null) { return true; }
			synchronized (pending._warehouse)
			{
				return finishTransfer(player, pending, pending._move.settle(() -> pending.publishContainers(player))) != TransferOutcome.UNCONFIRMED;
			}
		}
	}

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
				|| (destination.detached() || destination.emptyWallet()) == target.hasStoredWarehouseRow()
				|| destination.detached() && target.getCount() != count
				|| destination.emptyWallet() && (destination.detached() || deposit || target.getId() != Inventory.ADENA_ID || target.getCount() != 0))
			{
				throw new IllegalArgumentException("Invalid clan warehouse stack transfer.");
			}
			final WarehouseRow targetRow = target.warehouseRow();
			final WarehouseRow beforeTarget = destination.detached() || destination.emptyWallet() ? null : targetRow;
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
			return execute(stillAuthorized, publishContainers, List.of());
		}

		Outcome execute(BooleanSupplier stillAuthorized, Runnable publishContainers, List<Reservation> reservations)
		{
			return Item.withWarehouseLocks(_items, () ->
			{
				if (_pending) { return settleLocked(publishContainers); }
				for (Change change : _changes)
				{
					final Item item = change.item();
					if (item.blocksInventoryDebit() || item.hasStoredWarehouseRow() != (change.before() != null)
						|| !change.nativeBefore().equals(item.warehouseRow())) { return Outcome.CONFLICT; }
				}
				try (Connection connection = DatabaseFactory.getConnection())
				{
					connection.setAutoCommit(false);
					try
					{
						stage(connection, reservations);
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
			for (Change change : _changes)
			{
				final boolean keepsAdena = change.item().getId() == Inventory.ADENA_ID && change.after() != null
					&& change.nativeBefore().item().loc() == ItemLocation.INVENTORY && change.after().item().loc() == ItemLocation.INVENTORY;
				change.item().setWarehouseTransferPending(pending, keepsAdena);
			}
			_pending = pending;
		}

		void stage(Connection connection) throws SQLException
		{
			stage(connection, List.of());
		}

		private void stage(Connection connection, List<Reservation> reservations) throws SQLException
		{
			ClanFoundingDevelopment.requireOrganizationOwner(connection, _profileId, _actorId);
			final byte[] receiptPayload = readReceipt(connection);
			if (sameOperation(receiptPayload)) { throw new SQLException("Clan warehouse receipt already committed."); }
			if (!reservations.isEmpty()) { PhantomEconomyConflictPort.requireNoConflict(connection, null, reservations); }
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
			final byte[] receipt = readReceipt(connection);
			final boolean withReceipt = Arrays.equals(receipt, _evidence);
			if (!withReceipt && sameOperation(receipt)) { return Outcome.CONFLICT; }
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

		private boolean sameOperation(byte[] receipt)
		{
			return receipt != null && Arrays.equals(receipt, 0, 16, _evidence, 0, 16);
		}

		private byte[] readReceipt(Connection connection) throws SQLException
		{
			try (var receipt = connection.prepareStatement("SELECT component_schema_version,payload FROM phantom_profile_components WHERE profile_id=? AND component_type=? FOR UPDATE"))
			{
				receipt.setLong(1, _profileId);
				receipt.setString(2, COMPONENT);
				try (var row = receipt.executeQuery())
				{
					if (!row.next()) { return null; }
					if (row.getInt(1) != VERSION) { throw new SQLException("Unsupported clan warehouse receipt schema."); }
					final byte[] payload = row.getBytes(2);
					if (payload == null || payload.length < 41) { throw new SQLException("Invalid clan warehouse receipt evidence."); }
					return payload;
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

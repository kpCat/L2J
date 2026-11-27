/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.model.clan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.IdManagerConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.instance.Warehouse;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.model.clan.ClanWarehouseTransfer.Destination;
import org.l2jmobius.gameserver.model.clan.ClanWarehouseTransfer.DonationPolicy;
import org.l2jmobius.gameserver.model.clan.ClanWarehouseTransfer.Request;
import org.l2jmobius.gameserver.model.clan.ClanWarehouseTransfer.TransferOutcome;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.ClanWarehouse;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Exact durable item/fee outcomes against the 021 isolated MariaDB stock schema. */
public final class ClanWarehouse021Checks
{
	private static final int ACTOR = 21002163;
	private static final int CLAN = 21002164;
	private static final long PROFILE = 21002162L;
	private static final int CONTEST_CLAN = 21002194;
	private static final int FIRST_CONTENDER = 21002193;
	private static final int SECOND_CONTENDER = 21002197;

	private ClanWarehouse021Checks() { }

	public static void verify(Path scratch) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.createStatement())
		{
			sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002162,21002163)");
			sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002162,'clan.organization',2,X'01')");
			sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id) VALUES(21002164,'ClanWarehouseProof021',5,21002163)");
			sql.executeUpdate("INSERT INTO characters(charId,char_name,sp,classid,base_class,clanid) VALUES(21002163,'ClanWarehouseProof021',450,88,88,21002164)");
			sql.executeUpdate("INSERT INTO phantom_profiles(profile_id,character_object_id) VALUES(21002192,21002193),(21002196,21002197)");
			sql.executeUpdate("INSERT INTO phantom_profile_components(profile_id,component_type,component_schema_version,payload) VALUES(21002192,'clan.organization',2,X'01'),(21002196,'clan.organization',2,X'01')");
			sql.executeUpdate("INSERT INTO clan_data(clan_id,clan_name,clan_level,leader_id) VALUES(21002194,'ContestedStock021',5,21002193)");
			sql.executeUpdate("INSERT INTO characters(charId,char_name,classid,base_class,clanid) VALUES(21002193,'StockFirst021',0,0,21002194),(21002197,'StockSecond021',0,0,21002194)");
		}
		IdManagerConfig.load();
		final int firstNativeObject = IdManagerConfig.FIRST_OBJECT_ID + 21021;
		final Item sword = item(firstNativeObject, 1, ACTOR, ItemLocation.INVENTORY, 1);
		sword.setEnchantLevel(4);
		final Item adena = item(firstNativeObject + 1, Inventory.ADENA_ID, ACTOR, ItemLocation.INVENTORY, 200);
		final Item stems = item(firstNativeObject + 2, 1864, ACTOR, ItemLocation.INVENTORY, 11);
		for (Item item : new Item[] { sword, adena, stems }) { item.updateDatabase(true); }
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.prepareStatement("UPDATE items SET time_of_use=7 WHERE object_id=?"))
		{
			sql.setInt(1, sword.getObjectId());
			check(sql.executeUpdate() == 1, "Isolated gear metadata fixture was not created.");
		}
		final var deposit = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 51), sword, null, adena, 1, true);
		final var staleLeader = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR + 1, new UUID(21, 56), sword, null, adena, 1, true);
		reject(staleLeader, "Native clan warehouse is unavailable.");
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			deposit.stage(connection);
			connection.rollback();
		}
		check(read(deposit) == ClanWarehouseTransfer.Outcome.ROLLED_BACK, "A rolled-back object transfer left the fee, ownership or receipt changed.");
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			deposit.stage(connection);
			connection.commit();
		}
		check(read(deposit) == ClanWarehouseTransfer.Outcome.APPLIED, "Reconnection lost the exact transfer or fee.");
		reject(deposit, "Clan warehouse receipt already committed.");
		deposit.publish();
		check(sword.getOwnerId() == CLAN && sword.getItemLocation() == ItemLocation.CLANWH && sword.getEnchantLevel() == 4 && adena.getCount() == 170,
			"Native memory lost the gear object, enchantment or deposit fee.");
		final var withdraw = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 52), sword, null, null, 1, false);
		commit(withdraw);
		check(sword.getOwnerId() == ACTOR && sword.getItemLocation() == ItemLocation.INVENTORY && sword.getEnchantLevel() == 4 && adena.getCount() == 170,
			"Withdrawal did not restore the original object without another fee.");
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.prepareStatement("SELECT time_of_use FROM items WHERE object_id=?"))
		{
			sql.setInt(1, sword.getObjectId());
			try (var row = sql.executeQuery()) { check(row.next() && row.getInt(1) == 7, "Whole-object movement rewrote native usage metadata."); }
		}

		final Item newStack = item(firstNativeObject + 3, 1864, CLAN, ItemLocation.CLANWH, 4);
		try
		{
			ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 57), stems, new Destination(newStack, false), adena, 4, true);
			throw new AssertionError("An unsaved existing stack was treated as a fresh object.");
		}
		catch (IllegalArgumentException expected)
		{
			check("Invalid clan warehouse stack transfer.".equals(expected.getMessage()), "Existing unsaved target was rejected for the wrong reason.");
		}
		final var split = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 53), stems, new Destination(newStack, true), adena, 4, true);
		check(split.execute(() -> false, () -> { throw new AssertionError("Cancelled transfer published."); }) == ClanWarehouseTransfer.Outcome.ROLLED_BACK
			&& read(split) == ClanWarehouseTransfer.Outcome.ROLLED_BACK && !split.pending(), "Stale native authority left a staged write.");
		commit(split);
		check(stems.getCount() == 7 && newStack.getCount() == 4 && adena.getCount() == 140, "Native stack split or fee differs.");
		final var merge = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 54), newStack, new Destination(stems, false), null, 4, false);
		commit(merge);
		check(newStack.getCount() == 0 && stems.getCount() == 11 && adena.getCount() == 140, "Consumed stack or merged count differs.");

		final var sameAdena = item(firstNativeObject + 4, Inventory.ADENA_ID, CLAN, ItemLocation.CLANWH, 20);
		final var money = ClanWarehouseTransfer.prepare(PROFILE, ACTOR, CLAN, ACTOR, new UUID(21, 55), adena, new Destination(sameAdena, true), adena, 20, true);
		check(money.execute(() -> true, () -> { throw new IllegalStateException("TEST settlement interruption"); }) == ClanWarehouseTransfer.Outcome.CONFLICT
			&& money.pending() && adena.blocksInventoryDebit() && sameAdena.blocksInventoryDebit(),
			"Confirmed SQL with interrupted native publication lost its item fences.");
		check(adena.tryCreditContainerStack(ItemProcessType.NONE, 7, null, null, () -> true),
			"An earned native credit was diverted to a second Adena stack while settlement was pending.");
		check(money.settle(() -> { }) == ClanWarehouseTransfer.Outcome.APPLIED && !money.pending() && !adena.blocksInventoryDebit(),
			"Exact receipt did not complete interrupted native publication.");
		check(adena.getCount() == 97 && sameAdena.getCount() == 20, "Adena deposit did not preserve the fee, transferred quantity and late credit.");
		adena.updateDatabase(true);
		final Clan restored = new Clan(CLAN);
		final Item cold = restored.getWarehouse().getItemByObjectId(sameAdena.getObjectId());
		check(cold != null && cold.getOwnerId() == CLAN && cold.getItemLocation() == ItemLocation.CLANWH && cold.getCount() == 20,
			"Native clan warehouse did not restore the exact deposited currency object.");
		System.out.println("PASS 021 native warehouse exact gear, split, merge, Adena, fee and cold transaction readback");
		final boolean originalWithdrawalPolicy = PlayerConfig.ALT_MEMBERS_CAN_WITHDRAW_FROM_CLANWH;
		try
		{
			PlayerConfig.ALT_MEMBERS_CAN_WITHDRAW_FROM_CLANWH = true;
			verifyNativeContainers(restored, sword.getObjectId(), cold.getObjectId());
			verifyCompetingWithdrawals(scratch, firstNativeObject + 7);
		}
		finally { PlayerConfig.ALT_MEMBERS_CAN_WITHDRAW_FROM_CLANWH = originalWithdrawalPolicy; }
	}

	private static Player fixturePlayer(int objectId) throws Exception
	{
		final var constructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class);
		constructor.setAccessible(true);
		return constructor.newInstance(objectId, PlayerTemplateData.getInstance().getTemplate(0), "test021",
			new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
	}

	private static void verifyNativeContainers(Clan clan, int swordId, int warehouseAdenaId) throws Exception
	{
		final Player player = fixturePlayer(ACTOR);
		try
		{
			clan.getClanMember(ACTOR).setPlayer(player);
			player.setClan(clan);
			player.getInventory().restore();
			final ClanWarehouse warehouse = (ClanWarehouse) clan.getWarehouse();
			final Item sword = player.getInventory().getItemByObjectId(swordId);
			check(sword != null && sword.getEnchantLevel() == 4 && player.getAdena() == 97, "Native inventory did not load the durable warehouse fixture.");
			final var deposit = new Request(CLAN, ACTOR, swordId, 1, true, new UUID(21, 61), Long.MAX_VALUE);
			final var withdrawal = new Request(CLAN, ACTOR, warehouseAdenaId, 5, false, new UUID(21, 62), Long.MAX_VALUE);
			synchronized (player)
			{
				synchronized (warehouse)
				{
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, deposit, 68, List.of(), () -> true) == TransferOutcome.NOT_READY,
						"Deposit crossed the protected Adena reserve.");
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, deposit, 67, List.of(), () -> warehouse.hasPendingTransfer()
						&& warehouse.transferItem(ItemProcessType.TRANSFER, warehouseAdenaId, 1, player.getInventory(), player, null) == null) == TransferOutcome.APPLIED,
						"The native container deposit did not safely exclude a stock withdrawal.");
					check(warehouse.getItemByObjectId(swordId) == sword && player.getInventory().getItemByObjectId(swordId) == null && player.getAdena() == 67,
						"Native gear custody or player Adena cache differs after deposit.");
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, withdrawal, 0, List.of(), () -> true) == TransferOutcome.APPLIED,
						"The native warehouse currency withdrawal did not complete.");
					check(warehouse.getItemByObjectId(warehouseAdenaId).getCount() == 15 && player.getAdena() == 72
						&& !warehouse.hasPendingTransfer() && player.getPendingClanWarehouse() == null && ClanWarehouseTransfer.settlePending(player),
						"Native withdrawal left a retired item, stale Adena cache or unresolved STORE guard.");
				}
			}
			check(player.getInventory().destroyItemByItemId(ItemProcessType.NONE, Inventory.ADENA_ID, 72, player, null) != null
				&& player.getInventory().getAdenaInstance() == null, "Could not prepare the isolated empty-wallet case.");
			final int occupied = player.getInventory().getNonQuestSize();
			final Item wallet = player.getInventory().getAdenaForCountTransaction();
			final var toEmptyWallet = new Request(CLAN, ACTOR, warehouseAdenaId, 15, false, new UUID(21, 63), Long.MAX_VALUE);
			synchronized (player)
			{
				synchronized (warehouse)
				{
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, toEmptyWallet, 0, List.of(), () -> false) == TransferOutcome.NOT_READY
						&& player.getInventory().getNonQuestSize() == occupied && player.getAdena() == 0 && !wallet.hasStoredWarehouseRow(),
						"An aborted first Adena withdrawal occupied a real inventory slot or created currency.");
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, toEmptyWallet, 0, List.of(), () -> true) == TransferOutcome.APPLIED
						&& player.getInventory().getAdenaInstance() == wallet && player.getAdena() == 15
						&& player.getInventory().getAllItemsByItemId(Inventory.ADENA_ID).size() == 1 && warehouse.getItemByObjectId(warehouseAdenaId) == null,
						"Empty-wallet withdrawal did not publish the one native Adena object.");
				}
			}
			player.setClanPrivileges(new ClanPrivileges());
			check(!ClanWarehouseTransfer.canWithdraw(player, clan), "A member without native warehouse permission could withdraw.");
			player.setClanPrivileges(new ClanPrivileges(ClanAccess.ACCESS_WAREHOUSE.getMask()));
			check(ClanWarehouseTransfer.canWithdraw(player, clan), "The stock warehouse privilege was ignored.");
			player.getInventory().addAdena(ItemProcessType.NONE, 985, player, null);
			player.getInventory().updateDatabase();
			final DonationPolicy policy = new DonationPolicy(10, 600_000);
			final UUID operation = new UUID(21, 71);
			final var excessive = new Request(CLAN, ACTOR, wallet.getObjectId(), 48, true, new UUID(21, 70), Long.MAX_VALUE, 300, policy);
			final var voluntary = new Request(CLAN, ACTOR, wallet.getObjectId(), 40, true, operation, Long.MAX_VALUE, 300, policy);
			final var cooldown = new Request(CLAN, ACTOR, wallet.getObjectId(), 1, true, new UUID(21, 72), Long.MAX_VALUE, 300, policy);
			synchronized (player)
			{
				synchronized (warehouse)
				{
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, excessive, 500, List.of(), () -> true) == TransferOutcome.NOT_READY,
						"Donation exceeded ten percent of protected surplus after the deposit fee.");
					check(ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, voluntary, 500, List.of(), () -> true) == TransferOutcome.APPLIED
						&& ClanWarehouseTransfer.committed(PROFILE, ACTOR, CLAN, operation, true), "Eligible contribution did not commit its exact native receipt.");
					check(!ClanWarehouseTransfer.donationDue(PROFILE, System.currentTimeMillis(), policy)
						&& ClanWarehouseTransfer.transferLocked(PROFILE, player, clan, warehouse, cooldown, 500, List.of(), () -> true) == TransferOutcome.NOT_READY
						&& player.getAdena() == 930 && warehouse.getAdena() == 40, "Duplicate spending was possible inside the durable contribution cooldown.");
				}
			}
		}
		finally { player.stopAllTasks(); }
		System.out.println("PASS 021 native warehouse containers, stock exclusion, rights, protected surplus and durable contribution cooldown");
	}

	private static void verifyCompetingWithdrawals(Path scratch, int itemObjectId) throws Exception
	{
		// Load just the stock Warehouse NPC's region in the isolated TEST JVM.
		final Path geo = Files.createDirectories(scratch.resolve("warehouse-geo"));
		final Path stockRegion = ServerConfig.DATAPACK_ROOT.toPath().resolve("data/geodata/17_25.l2j");
		if (Files.isRegularFile(stockRegion) && !Files.exists(geo.resolve(stockRegion.getFileName())))
		{
			Files.copy(stockRegion, geo.resolve(stockRegion.getFileName()));
		}
		GeoEngineConfig.GEODATA_PATH = geo;
		final var stock = item(itemObjectId, 1, CONTEST_CLAN, ItemLocation.CLANWH, 1);
		stock.setEnchantLevel(4);
		stock.updateDatabase(true);
		final var reservations = new PhantomEconomyReservationService(PhantomEconomyPolicy.load(
			ServerConfig.DATAPACK_ROOT.toPath().resolve("data/phantoms/economy/high-five-economy-v1.xml")));
		try
		{
			ThreadPool.init();
			check(reservations.start(), "The TEST economy reservation service did not start.");
			PhantomEconomyConflictPort.install(reservations);
			final Clan clan = ClanTable.getInstance().getClan(CONTEST_CLAN);
			check(clan != null && clan.getWarehouse().getItemByObjectId(itemObjectId) != null, "The canonical clan table did not load the contested item.");
			final Warehouse npc = new Warehouse(NpcData.getInstance().getTemplate(30005));
			final var execution = new PhantomExecutionAdmission(2);
			try (var first = new Contender(clan, FIRST_CONTENDER, execution); var second = new Contender(clan, SECOND_CONTENDER, execution))
			{
				npc.spawnMe(-81512, 243424, -3712);
				final var firstRequest = new Request(CONTEST_CLAN, FIRST_CONTENDER, itemObjectId, 1, false, new UUID(21, 81), Long.MAX_VALUE);
				final var secondRequest = new Request(CONTEST_CLAN, FIRST_CONTENDER, itemObjectId, 1, false, new UUID(21, 82), Long.MAX_VALUE);
				final var foreign = new Request(CLAN, ACTOR, itemObjectId, 1, false, new UUID(21, 83), Long.MAX_VALUE);
				check(first.run(() -> ClanWarehouseTransfer.transfer(first.profile, first.player, npc, foreign, 0, () -> true, () -> true)) == TransferOutcome.NOT_READY,
					"A foreign clan request was admitted.");
				final var authorityChecks = new AtomicInteger();
				check(second.run(() -> ClanWarehouseTransfer.transfer(second.profile, second.player, npc, secondRequest, 0, () -> true, () ->
				{
					if (authorityChecks.incrementAndGet() == 2) { second.player.setClanPrivileges(new ClanPrivileges()); }
					return true;
				})) == TransferOutcome.NOT_READY && authorityChecks.get() == 2
					&& clan.getWarehouse().getItemByObjectId(itemObjectId) != null
					&& !ClanWarehouseTransfer.committed(second.profile, SECOND_CONTENDER, CONTEST_CLAN, secondRequest.operation(), false),
					"Revoked withdrawal permission did not roll back the staged transfer.");
				second.grantWarehouseAccess();

				final var firstInsideFence = new CountDownLatch(1);
				final var secondReadSource = new CountDownLatch(1);
				final var secondPreflight = new AtomicInteger();
				final var workers = Executors.newVirtualThreadPerTaskExecutor();
				try
				{
					final var winner = workers.submit(() -> first.run(() -> ClanWarehouseTransfer.transfer(first.profile, first.player, npc, firstRequest, 0, () -> true, () ->
					{
						firstInsideFence.countDown();
						await(secondReadSource);
						return true;
					})));
					await(firstInsideFence);
					final var loser = workers.submit(() -> second.run(() -> ClanWarehouseTransfer.transfer(second.profile, second.player, npc, secondRequest, 0, () ->
					{
						if (secondPreflight.incrementAndGet() == 2) { secondReadSource.countDown(); }
						return true;
					}, () -> true)));
					check(winner.get(10, TimeUnit.SECONDS) == TransferOutcome.APPLIED && loser.get(10, TimeUnit.SECONDS) == TransferOutcome.NOT_READY,
						"Contending native withdrawals did not produce one winner.");
				}
				finally
				{
					workers.shutdownNow();
					check(workers.awaitTermination(5, TimeUnit.SECONDS), "The native withdrawal workers did not drain.");
				}
				final Item owned = first.player.getInventory().getItemByObjectId(itemObjectId);
				final var stored = PhantomProfileRepository.open();
				check(owned != null && owned.getEnchantLevel() == 4 && owned.getCount() == 1
					&& second.player.getInventory().getItemByObjectId(itemObjectId) == null && clan.getWarehouse().getItemByObjectId(itemObjectId) == null
					&& ClanWarehouseTransfer.committed(first.profile, FIRST_CONTENDER, CONTEST_CLAN, firstRequest.operation(), false)
					&& stored.findComponent(second.profile, "clan.warehouse.transfer").isEmpty(),
					"The sealed race left duplicate or missing gear or incorrect receipts.");
				try (var connection = DatabaseFactory.getConnection(); var sql = connection.prepareStatement(
					"SELECT object_id,owner_id,loc,count,enchant_level FROM items WHERE item_id=? AND owner_id IN (?,?,?)"))
				{
					sql.setInt(1, owned.getId());
					sql.setInt(2, FIRST_CONTENDER);
					sql.setInt(3, SECOND_CONTENDER);
					sql.setInt(4, CONTEST_CLAN);
					try (var row = sql.executeQuery())
					{
						check(row.next() && row.getInt(1) == itemObjectId && row.getInt(2) == FIRST_CONTENDER && "INVENTORY".equals(row.getString(3))
							&& row.getLong(4) == 1 && row.getInt(5) == 4 && !row.next(), "Cold SQL readback lost or duplicated the winning object.");
					}
				}
				first.player.getInventory().addAdena(ItemProcessType.NONE, 100, first.player, null);
				final long now = System.currentTimeMillis();
				final var reservation = new PhantomEconomyOperation(
					new PhantomEconomyOperation.Identity(first.profile, FIRST_CONTENDER, 21002192, 0, 1, "warehouse.test021", 1, 1),
					PhantomEconomyOperation.Kind.DIRECT_TRADE, PhantomEconomyOperation.State.PREPARED,
					PhantomEconomyOperation.sha256("warehouse.before021"), PhantomEconomyOperation.sha256("warehouse.trade021"),
					new byte[0], new byte[0], now, now, now + 120_000, 0);
				final var reserved = new Reservation(first.profile, FIRST_CONTENDER, ResourceKind.ITEM_OBJECT, itemObjectId, owned.getId(), 1, 1, 4, "INVENTORY");
				check(reservations.reserve(reservation, List.of(reserved)).status() == PhantomEconomyReservationService.Status.RESERVED,
					"The native economy reservation did not claim the winning item.");
				final var forbiddenDeposit = new Request(CONTEST_CLAN, FIRST_CONTENDER, itemObjectId, 1, true, new UUID(21, 84), Long.MAX_VALUE);
				check(first.run(() -> ClanWarehouseTransfer.transfer(first.profile, first.player, npc, forbiddenDeposit, 1, () -> true, () -> true)) == TransferOutcome.NOT_READY
					&& first.player.getInventory().getItemByObjectId(itemObjectId) == owned && first.player.getAdena() == 100
					&& ClanWarehouseTransfer.committed(first.profile, FIRST_CONTENDER, CONTEST_CLAN, firstRequest.operation(), false),
					"A warehouse deposit bypassed an active economy reservation.");
			}
			finally { npc.deleteMe(); }
		}
		finally
		{
			PhantomEconomyConflictPort.uninstall(reservations);
			try { reservations.shutdown(System.currentTimeMillis()); }
			finally { ThreadPool.shutdown(); }
		}
		System.out.println("PASS 021 simultaneous full native withdrawals, revoked authority, reserved item and single durable owner");
	}

	private static void await(CountDownLatch latch)
	{
		try { check(latch.await(5, TimeUnit.SECONDS), "The other native withdrawal did not reach the expected boundary."); }
		catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError("Warehouse competition interrupted.", interrupted); }
	}

	private static final class Contender implements AutoCloseable
	{
		final long profile;
		final Player player;
		private final PhantomExecutionAdmission execution;
		private final Lease identity;
		private final PhantomNativeWorkScope scope;
		private final Player.OutboundSessionAttachment outbound;

		Contender(Clan clan, int objectId, PhantomExecutionAdmission execution) throws Exception
		{
			this.execution = execution;
			profile = objectId - 1;
			player = fixturePlayer(objectId);
			player.setXYZInvisible(-81512, 243424, -3712);
			clan.getClanMember(objectId).setPlayer(player);
			player.setClan(clan);
			grantWarehouseAccess();
			identity = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
			check(identity != null, "The native contender identity was unavailable.");
			final var constructor = PhantomNativeWorkScope.class.getDeclaredConstructor(Object.class, Player.class, Lease.class, long.class, PhantomExecutionAdmission.class, long.class);
			constructor.setAccessible(true);
			scope = constructor.newInstance(new Object(), player, identity, System.nanoTime(), execution, profile);
			player.attachNativeWorkOwner(scope);
			outbound = player.attachOutboundSession(new HeadlessPlayerOutboundSession(16, 128));
			execution.publish(profile, 0, PhantomExecutionHandoff.initial(PhantomExecutionHandoff.solo(profile)));
		}

		void grantWarehouseAccess()
		{
			player.setClanPrivileges(new ClanPrivileges(ClanAccess.ACCESS_WAREHOUSE.getMask()));
		}

		TransferOutcome run(Supplier<TransferOutcome> action)
		{
			try (var permit = execution.tryAcquire(profile, PhantomExecutionHandoff.solo(profile)))
			{
				check(permit != null, "The contender lost its live execution permit.");
				try (var captured = permit.enterScope())
				{
					final var ticket = scope.reserve(null, "ACTION", PlayerNativeWork.Semantics.CANCELLABLE);
					check(ticket != null && ticket.tryStart(), "The contender did not start the native action.");
					try (var active = PlayerNativeWork.enter(ticket)) { return action.get(); }
					finally { ticket.complete(null); }
				}
			}
		}

		@Override public void close()
		{
			check(scope.outstanding() == 0, "Warehouse test leaked native work.");
			player.stopAllTasks();
			player.detachNativeWorkOwner(scope);
			outbound.close();
			identity.close();
		}
	}

	private static void reject(ClanWarehouseTransfer.Move move, String reason) throws SQLException
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				move.stage(connection);
				throw new AssertionError("A stale warehouse transfer was staged.");
			}
			catch (SQLException rejected)
			{
				check(reason.equals(rejected.getMessage()), "Warehouse transfer was rejected for the wrong reason.");
			}
			finally { connection.rollback(); }
		}
	}

	private static void commit(ClanWarehouseTransfer.Move move) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			move.stage(connection);
			connection.commit();
		}
		check(read(move) == ClanWarehouseTransfer.Outcome.APPLIED, "Committed warehouse receipt and objects did not match on reconnect.");
		move.publish();
	}

	private static ClanWarehouseTransfer.Outcome read(ClanWarehouseTransfer.Move move) throws SQLException
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			final var outcome = move.readback(connection);
			connection.rollback();
			return outcome;
		}
	}

	private static Item item(int objectId, int itemId, int ownerId, ItemLocation location, long count)
	{
		final Item item = new Item(objectId, ItemData.getInstance().getTemplate(itemId));
		item.setOwnerId(ownerId);
		item.setItemLocation(location, 0, false);
		item.setCount(count);
		return item;
	}

	private static void check(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}

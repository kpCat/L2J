/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.network.Connection;
import org.l2jmobius.commons.network.ConnectionConfig;
import org.l2jmobius.commons.network.PacketExecutor;
import org.l2jmobius.commons.network.ReadHandler;
import org.l2jmobius.commons.network.ReadableBuffer;
import org.l2jmobius.commons.network.WriteHandler;
import org.l2jmobius.gameserver.config.FloodProtectorConfig;
import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalPremiumQoLConfig.Settings;
import org.l2jmobius.gameserver.data.xml.MultisellData;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.multisell.PreparedListContainer;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.clientpackets.MultiSellChoose;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** L2-QOL-001 registered board and native multisell transaction coverage. */
public final class QoLShopSuite implements PhantomTestSuite
{
	private static final long SEED = 1001002L;
	private static final int TIER_5_ITEM = 22290;
	private static final int TIER_10_ITEM = 22296;
	private static final int TIER_20_ITEM = 22297;
	private static final int TIER_40_ITEM = 22298;
	private static final int WEIGHT_ITEM = 1865;
	private static final long TIER_5_PRICE = 1000;
	private static final int TIER_5_ENTRY = 100000;
	private static final int TIER_10_ENTRY = 200000;
	private static final int TIER_40_ENTRY = 400000;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PersonalPremiumQoLService.RuntimeState _activeState;
	private PersonalPremiumQoLService.RuntimeState _previousState;
	private CommunitySettings _communitySettings;
	private NetworkBackedClient _network;
	private Player _player;

	@Override
	public String id()
	{
		return "qol-shop";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "L2-QOL-001 shop suite used the wrong deterministic seed.");
		_environment.initialize(context);
		MultisellData.getInstance();
		final Settings settings = new Settings(true, true, PersonalPremiumQoLConfig.DEFAULT_LEVEL_GAP_ITEMS_FILE, true, "Test configuration.");
		_activeState = PersonalPremiumQoLService.build(settings, Path.of("."));
		_previousState = PersonalPremiumQoLService.installForTests(_activeState);
		_communitySettings = CommunitySettings.capture();
		_communitySettings.enableForTest();
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		PhantomAssertions.assertTrue(CommunityBoardHandler.getInstance().getHandler("_bbsqol") != null, "Registered MasterHandler did not expose _bbsqol.");
		_player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_player != null, "L2-QOL-001 shop fixture Player did not load.");
		_network = NetworkBackedClient.attach(_player);
		context.record("qol001.shopList", PersonalPremiumQoLService.SHOP_LIST_ID);
		context.record("qol001.shopPrices", _activeState.prices());
		context.record("qol001.shopTransport", "real-local-async-socket/GameClient/MultiSellChoose");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-registered-board-native-debit-credit-and-relog", this::positivePurchaseAndRelog);
		registry.add("02-invalid-list-entry-and-amount-controls", this::invalidPacketControls);
		registry.add("03-disabled-stale-generic-forged-and-npc-controls", this::admissionControls);
		registry.add("04-insufficient-capacity-and-weight-controls", this::nativeInventoryControls);
		registry.add("05-community-board-combat-and-store-controls", this::communityStateControls);
		registry.add("06-repeated-concurrent-paid-and-unrelated-native-list", this::concurrencyAndNativeIsolation);
	}

	private void positivePurchaseAndRelog(PhantomTestContext context) throws Exception
	{
		resetEconomy();
		resetFloodState();
		fund(TIER_5_PRICE);
		final long adenaBefore = count(57);
		final long itemBefore = count(TIER_5_ITEM);
		prepareViaBoard();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, 1);
		PhantomAssertions.assertEquals(adenaBefore - TIER_5_PRICE, count(57), "Native multisell did not debit the exact DEMO price.");
		PhantomAssertions.assertEquals(itemBefore + 1, count(TIER_5_ITEM), "Native multisell did not credit exactly one carrier.");
		PhantomAssertions.assertEquals(5, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Purchased carrier did not update the inventory-derived tier.");

		_network.close();
		_network = null;
		_player.storeMe();
		_player.deleteMe();
		_player = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_player != null, "Purchased fixture did not reload.");
		PhantomAssertions.assertEquals(itemBefore + 1, count(TIER_5_ITEM), "Purchased carrier was not persisted by native inventory.");
		PhantomAssertions.assertEquals(5, PersonalPremiumQoLService.getInstance().maximumGap(_player), "Reloaded purchase did not restore tier 5.");
		_network = NetworkBackedClient.attach(_player);
		context.record("qol001.shopPositive", "adenaDebit=1000,itemCredit=1,relogTier=5");
	}

	private void invalidPacketControls(PhantomTestContext context) throws Exception
	{
		resetEconomy();
		resetFloodState();
		fund(200000);
		final long adena = count(57);
		final long products = productCount();
		prepareDirect();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID + 1, TIER_5_ENTRY, 1);
		assertNoEconomyDelta(adena, products, "unknown list");
		prepareDirect();
		resetFloodState();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID, 9999, 1);
		assertNoEconomyDelta(adena, products, "unknown entry");
		for (long amount : List.of(0L, -1L, Long.MAX_VALUE, (long) GeneralConfig.MULTISELL_AMOUNT_LIMIT + 1))
		{
			prepareDirect();
			resetFloodState();
			execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, amount);
			assertNoEconomyDelta(adena, products, "invalid amount " + amount);
		}
		context.record("qol001.invalidPacketControls", 6);
	}

	private void admissionControls(PhantomTestContext context) throws Exception
	{
		resetEconomy();
		resetFloodState();
		fund(100000);
		final long adena = count(57);
		final long products = productCount();

		final PersonalPremiumQoLService.RuntimeState shopDisabled = new PersonalPremiumQoLService.RuntimeState(true, false, _activeState.catalog(), java.util.Map.of(), "Shop disabled test.");
		PersonalPremiumQoLService.installForTests(shopDisabled);
		PhantomAssertions.assertFalse(MultisellData.getInstance().separateAndSendPersonalPremiumQoL(_player), "Disabled shop prepared a list.");
		PhantomAssertions.assertEquals(null, _player.getMultiSell(), "Disabled shop retained a prepared list.");
		PersonalPremiumQoLService.installForTests(_activeState);

		prepareDirect();
		PersonalPremiumQoLService.installForTests(shopDisabled);
		resetFloodState();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, 1);
		assertNoEconomyDelta(adena, products, "stale prepared list after disable");
		PhantomAssertions.assertEquals(null, _player.getMultiSell(), "Stale disabled prepared list was not cleared.");
		PersonalPremiumQoLService.installForTests(_activeState);

		MultisellData.getInstance().separateAndSend(PersonalPremiumQoLService.SHOP_LIST_ID, _player, null, false);
		PhantomAssertions.assertEquals(null, _player.getMultiSell(), "Generic _bbsmultisell-equivalent route prepared the QoL list.");
		prepareDirect();
		final PreparedListContainer forged = new PreparedListContainer(_player.getMultiSell(), false, _player, null);
		_player.setMultiSell(forged);
		resetFloodState();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, 1);
		assertNoEconomyDelta(adena, products, "forged prepared list");

		final Npc npc = new Npc(30006);
		MultisellData.getInstance().separateAndSend(PersonalPremiumQoLService.SHOP_LIST_ID, _player, npc, false);
		PhantomAssertions.assertEquals(null, _player.getMultiSell(), "NPC multisell bypass prepared the QoL list.");
		npc.deleteMe();
		assertNoEconomyDelta(adena, products, "generic/NPC admission");
		context.record("qol001.admissionControls", "disabledBefore=true;disabledAfter=true;generic=true;forged=true;npc=true");
	}

	private void nativeInventoryControls(PhantomTestContext context) throws Exception
	{
		resetEconomy();
		resetFloodState();
		prepareDirect();
		final long products = productCount();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_40_ENTRY, 1);
		PhantomAssertions.assertEquals(products, productCount(), "Insufficient currency granted a product.");

		fund(100000);
		final long adena = count(57);
		final int inventoryLimit = PlayerConfig.INVENTORY_MAXIMUM_NO_DWARF;
		try
		{
			PlayerConfig.INVENTORY_MAXIMUM_NO_DWARF = 0;
			prepareDirect();
			resetFloodState();
			execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_10_ENTRY, 1);
			assertNoEconomyDelta(adena, products, "full inventory");
		}
		finally
		{
			PlayerConfig.INVENTORY_MAXIMUM_NO_DWARF = inventoryLimit;
		}

		final long heavyCount = Math.max(1, (_player.getMaxLoad() / 2L) + 1000);
		PhantomAssertions.assertTrue(_player.getInventory().addItem(ItemProcessType.REWARD, WEIGHT_ITEM, heavyCount, _player, this) != null, "Could not create overweight native inventory fixture.");
		PhantomAssertions.assertTrue(_player.getCurrentLoad() > _player.getMaxLoad(), "Overweight fixture did not exceed native maximum load.");
		prepareDirect();
		resetFloodState();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, 1);
		assertNoEconomyDelta(adena, products, "overweight inventory");
		remove(WEIGHT_ITEM);
		context.record("qol001.nativeInventoryControls", "insufficient/full/overweight=noDebitNoCredit");
	}

	private void communityStateControls(PhantomTestContext context) throws Exception
	{
		resetEconomy();
		resetFloodState();
		fund(10000);
		final long adena = count(57);
		final long products = productCount();
		_player.getAI().setAutoAttacking(true);
		try
		{
			CommunityBoardHandler.getInstance().handleParseCommand("_bbsqol;shop", _player);
			Thread.sleep(150);
			PhantomAssertions.assertEquals(null, _player.getMultiSell(), "Combat state prepared the QoL shop.");
		}
		finally
		{
			_player.getAI().setAutoAttacking(false);
		}
		_player.setPrivateStoreType(PrivateStoreType.SELL);
		try
		{
			CommunityBoardHandler.getInstance().handleParseCommand("_bbsqol;shop", _player);
			Thread.sleep(150);
			PhantomAssertions.assertEquals(null, _player.getMultiSell(), "Private store state prepared the QoL shop.");
		}
		finally
		{
			_player.setPrivateStoreType(PrivateStoreType.NONE);
		}
		assertNoEconomyDelta(adena, products, "combat/store Community Board guards");
		context.record("qol001.communityStateControls", "combat=true;store=true");
	}

	private void concurrencyAndNativeIsolation(PhantomTestContext context) throws Exception
	{
		resetEconomy();
		resetFloodState();
		fund(4000);
		final long adenaBeforeRepeated = count(57);
		final long itemBeforeRepeated = count(TIER_5_ITEM);
		prepareDirect();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, 1);
		awaitFloodInterval();
		prepareDirect();
		execute(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, 1);
		PhantomAssertions.assertEquals(2L, count(TIER_5_ITEM) - itemBeforeRepeated, "Two legitimate paid purchases were artificially rejected.");
		PhantomAssertions.assertEquals(2 * TIER_5_PRICE, adenaBeforeRepeated - count(57), "Repeated purchases did not debit twice.");

		final long adenaBeforeRace = count(57);
		final long itemBeforeRace = count(TIER_5_ITEM);
		prepareDirect();
		awaitFloodInterval();
		final MultiSellChoose first = packet(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, 1);
		final MultiSellChoose second = packet(PersonalPremiumQoLService.SHOP_LIST_ID, TIER_5_ENTRY, 1);
		final CountDownLatch start = new CountDownLatch(1);
		final Thread firstThread = new Thread(() -> runAfter(start, first), "qol001-multisell-race-1");
		final Thread secondThread = new Thread(() -> runAfter(start, second), "qol001-multisell-race-2");
		firstThread.start();
		secondThread.start();
		start.countDown();
		firstThread.join(5000);
		secondThread.join(5000);
		PhantomAssertions.assertFalse(firstThread.isAlive() || secondThread.isAlive(), "Concurrent native multisell requests did not terminate.");
		final long credited = count(TIER_5_ITEM) - itemBeforeRace;
		final long debited = adenaBeforeRace - count(57);
		PhantomAssertions.assertTrue((credited >= 0) && (credited <= 1), "Native flood protection admitted more than one concurrent purchase.");
		PhantomAssertions.assertEquals(credited * TIER_5_PRICE, debited, "Concurrent request produced a free carrier or excess debit.");

		final Npc nativeNpc = new Npc(30006);
		nativeNpc.setXYZ(0, 0, 0);
		_player.setXYZ(0, 0, 0);
		_player.setLastFolkNPC(nativeNpc);
		MultisellData.getInstance().separateAndSend(2, _player, nativeNpc, false);
		PhantomAssertions.assertTrue((_player.getMultiSell() != null) && (_player.getMultiSell().getListId() == 2) && !_player.getMultiSell().isPersonalPremiumQoL(), "Unrelated native multisell preparation was changed.");
		_player.setMultiSell(null);
		_player.setLastFolkNPC(null);
		nativeNpc.deleteMe();
		context.record("qol001.concurrent", "credited=" + credited + ",debited=" + debited + ",free=0");
		context.record("qol001.unrelatedNativeList", "list=2,prepared=true,qolProvenance=false");
	}

	private static void runAfter(CountDownLatch start, MultiSellChoose packet)
	{
		try
		{
			start.await(5, TimeUnit.SECONDS);
			packet.run();
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private void prepareViaBoard() throws Exception
	{
		_player.setMultiSell(null);
		CommunityBoardHandler.getInstance().handleParseCommand("_bbsqol;shop", _player);
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while ((_player.getMultiSell() == null) && (System.nanoTime() < deadline))
		{
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue((_player.getMultiSell() != null) && _player.getMultiSell().isPersonalPremiumQoL(), "Registered board did not prepare an admitted QoL list.");
	}

	private void prepareDirect()
	{
		_player.setMultiSell(null);
		PhantomAssertions.assertTrue(MultisellData.getInstance().separateAndSendPersonalPremiumQoL(_player), "Dedicated QoL route did not prepare the native list.");
		PhantomAssertions.assertTrue((_player.getMultiSell() != null) && _player.getMultiSell().isPersonalPremiumQoL(), "Dedicated route lost prepared-list provenance.");
	}

	private void execute(int listId, int entryId, long amount) throws Exception
	{
		Thread.sleep(15);
		packet(listId, entryId, amount).run();
	}

	private void resetFloodState() throws Exception
	{
		if (_network != null)
		{
			_network.close();
		}
		_network = NetworkBackedClient.attach(_player);
	}

	private static void awaitFloodInterval() throws InterruptedException
	{
		final long interval = (long) FloodProtectorConfig.FLOOD_PROTECTOR_MULTISELL.getProtectionInterval() * GameTimeTaskManager.MILLIS_IN_TICK;
		Thread.sleep(interval + GameTimeTaskManager.MILLIS_IN_TICK);
	}

	private MultiSellChoose packet(int listId, int entryId, long amount)
	{
		final ByteBuffer bytes = ByteBuffer.allocate(42).order(ByteOrder.LITTLE_ENDIAN);
		bytes.putInt(listId);
		bytes.putInt(entryId);
		bytes.putLong(amount);
		bytes.putShort((short) 0);
		bytes.putInt(0);
		bytes.putInt(0);
		for (int index = 0; index < 8; index++)
		{
			bytes.putShort((short) 0);
		}
		bytes.flip();
		final MultiSellChoose packet = new MultiSellChoose();
		packet.init(_network.client(), ReadableBuffer.of(bytes));
		PhantomAssertions.assertTrue(packet.read(), "Native MultiSellChoose packet did not parse.");
		return packet;
	}

	private void fund(long amount)
	{
		PhantomAssertions.assertTrue(_player.getInventory().addItem(ItemProcessType.REWARD, 57, amount, _player, this) != null, "Could not fund native Adena fixture.");
	}

	private long count(int itemId)
	{
		return _player.getInventory().getInventoryItemCount(itemId, -1);
	}

	private long productCount()
	{
		return count(TIER_5_ITEM) + count(TIER_10_ITEM) + count(TIER_20_ITEM) + count(TIER_40_ITEM);
	}

	private void assertNoEconomyDelta(long adena, long products, String label)
	{
		PhantomAssertions.assertEquals(adena, count(57), label + " changed currency.");
		PhantomAssertions.assertEquals(products, productCount(), label + " changed QoL products.");
	}

	private void resetEconomy()
	{
		_player.setMultiSell(null);
		remove(57);
		remove(TIER_5_ITEM);
		remove(TIER_10_ITEM);
		remove(TIER_20_ITEM);
		remove(TIER_40_ITEM);
		remove(WEIGHT_ITEM);
	}

	private void remove(int itemId)
	{
		final long count = count(itemId);
		if (count > 0)
		{
			_player.destroyItemByItemId(ItemProcessType.DESTROY, itemId, count, _player, false);
		}
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if (_player != null)
			{
				_player.setPrivateStoreType(PrivateStoreType.NONE);
				_player.getAI().setAutoAttacking(false);
				resetEconomy();
			}
			if (_network != null)
			{
				_network.close();
				_network = null;
			}
			if (_player != null)
			{
				_environment.cleanupLoadedPlayer(_player);
				_player = null;
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		finally
		{
			if (_communitySettings != null)
			{
				_communitySettings.restore();
			}
			if (_previousState != null)
			{
				PersonalPremiumQoLService.installForTests(_previousState);
			}
			_environment.shutdown();
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}

	private record CommunitySettings(boolean enabled, boolean custom, boolean combatDisabled, boolean karmaDisabled, boolean peaceOnly)
	{
		static CommunitySettings capture()
		{
			return new CommunitySettings(GeneralConfig.ENABLE_COMMUNITY_BOARD, CommunityBoardConfig.CUSTOM_CB_ENABLED, CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED, CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED, CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY);
		}

		void enableForTest()
		{
			GeneralConfig.ENABLE_COMMUNITY_BOARD = true;
			CommunityBoardConfig.CUSTOM_CB_ENABLED = true;
			CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED = true;
			CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED = true;
			CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY = false;
		}

		void restore()
		{
			GeneralConfig.ENABLE_COMMUNITY_BOARD = enabled;
			CommunityBoardConfig.CUSTOM_CB_ENABLED = custom;
			CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED = combatDisabled;
			CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED = karmaDisabled;
			CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY = peaceOnly;
		}
	}

	private static final class NetworkBackedClient implements AutoCloseable
	{
		private final Player _player;
		private final Connection<GameClient> _connection;
		private final AsynchronousSocketChannel _peer;
		private final AsynchronousServerSocketChannel _server;
		private final GameClient _client;

		private NetworkBackedClient(Player player, Connection<GameClient> connection, AsynchronousSocketChannel peer, AsynchronousServerSocketChannel server, GameClient client)
		{
			_player = player;
			_connection = connection;
			_peer = peer;
			_server = server;
			_client = client;
		}

		static NetworkBackedClient attach(Player player) throws Exception
		{
			final AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress("127.0.0.1", 0));
			final var accepted = server.accept();
			final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
			channel.connect(server.getLocalAddress()).get(5, TimeUnit.SECONDS);
			final AsynchronousSocketChannel peer = accepted.get(5, TimeUnit.SECONDS);
			final ConnectionConfig config = new ConnectionConfig(server.getLocalAddress());
			final PacketExecutor<GameClient> executor = new PacketExecutor<>(config);
			final ReadHandler<GameClient> reader = new ReadHandler<>((buffer, client) -> null, executor);
			final Connection<GameClient> connection = new Connection<>(channel, reader, new WriteHandler<>(), config);
			final GameClient client = new GameClient(connection);
			connection.setClient(client);
			client.setPlayer(player);
			player.setClient(client);
			return new NetworkBackedClient(player, connection, peer, server, client);
		}

		GameClient client()
		{
			return _client;
		}

		@Override
		public void close() throws Exception
		{
			_player.setClient(null);
			_connection.close();
			_peer.close();
			_server.close();
		}
	}
}

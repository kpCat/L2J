/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.Comparator;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.OfflineTradeConfig;
import org.l2jmobius.gameserver.data.sql.OfflineTraderTable;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomOfflineSupply;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService;
import org.l2jmobius.gameserver.phantoms.economy.PhantomSocialEconomyGoalSpec;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;

/** One finite TEST seller, restored by the ordinary native offline table before measurement. */
final class PhantomOfflineSupply060Fixture implements AutoCloseable
{
	private final PhantomHeadlessPlayerTestEnvironment _environment;
	private final int _objectId;
	private final boolean _realtime;
	private final int _offlineCount;
	private final long _sellerAdena, _sellerStock;
	private final boolean _manufacture;
	private final double _sellerMp;
	private boolean _paid;

	private PhantomOfflineSupply060Fixture(PhantomHeadlessPlayerTestEnvironment environment, int objectId, boolean realtime, int offlineCount, Player seller, boolean manufacture)
	{
		_environment = environment; _objectId = objectId; _realtime = realtime; _offlineCount = offlineCount;
		_sellerAdena = seller.getAdena(); _sellerStock = seller.getInventory().getInventoryItemCount(1835, -1);
		_manufacture = manufacture; _sellerMp = seller.getCurrentMp();
	}

	static PhantomOfflineSupply060Fixture create(PhantomTestContext context, PhantomHeadlessPlayerTestEnvironment environment, Player buyer, PhantomGameKnowledgeQuery knowledge) throws Exception
	{
		return create(context, environment, buyer, knowledge, false);
	}
	static PhantomOfflineSupply060Fixture create(PhantomTestContext context, PhantomHeadlessPlayerTestEnvironment environment, Player buyer, PhantomGameKnowledgeQuery knowledge, boolean manufacture) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		PhantomAssertions.assertEquals(0L, offlineRows("SELECT COUNT(*) FROM character_offline_trade"), "Offline fixture refuses pre-existing native traders.");
		PhantomAssertions.assertEquals(0L, offlineRows("SELECT COUNT(*) FROM character_offline_trade_items"), "Offline fixture refuses pre-existing native listings.");
		final var merchant = knowledge.snapshot().spawnFactsByNpc().values().stream().flatMap(java.util.List::stream)
			.filter(spawn -> spawn.instanceId() == 0 && spawn.pointKind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind.EXACT)
			.filter(spawn -> { final var template = org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(spawn.npcId()); return template != null && "Merchant".equals(template.getType()); })
			.sorted(Comparator.comparingDouble(spawn -> Math.hypot((long) spawn.x() - buyer.getX(), (long) spawn.y() - buyer.getY()))).limit(16)
			.map(spawn -> World.getInstance().getRegion(spawn.x(), spawn.y(), spawn.z()).getVisibleObjects().stream().filter(object -> object instanceof Merchant npc && npc.getId() == spawn.npcId()).map(object -> (Merchant) object).findFirst().orElse(null))
			.filter(java.util.Objects::nonNull).filter(npc -> npc.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE)).findFirst().orElseThrow(() -> new AssertionError("BLOCKED_OFFLINE_INPUT: no native peace-zone Merchant counter near the selected field."));
		PhantomAssertions.assertTrue(buyer.calculateDistance3D(merchant) < 6000, "Offline fixture counter exceeds declared local range.");
		final var fixture = environment.createCampaign058Companion(context, 2);
		final var setup = Player.load(fixture.objectId());
		PhantomAssertions.assertTrue(setup != null, "Native offline seller load failed.");
		final boolean realtime = OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME;
		final int count = World.OFFLINE_TRADE_COUNT;
		try
		{
			OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME = true;
			final int makerClass = manufacture ? 56 : 53, makerLevel = manufacture ? 20 : 5;
			setup.setBaseClass(makerClass); setup.setPlayerClass(makerClass); setup.setLearningClass(setup.getPlayerClass());
			setup.getStat().setLevel((byte) makerLevel); setup.getStat().setExp(setup.getStat().getExpForLevel(makerLevel));
			setup.setXYZInvisible(merchant.getX() + 64, merchant.getY(), GeoEngine.getInstance().getHeight(merchant.getX() + 64, merchant.getY(), merchant.getZ())); setup.revalidateZone(true);
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(setup.getX(), setup.getY(), setup.getZ(), org.l2jmobius.gameserver.model.zone.type.PeaceZone.class) != null, "Offline seller point must remain in a native peace zone.");
			int stockObjectId = 0;
			if (manufacture)
			{
				final var recipe = org.l2jmobius.gameserver.data.xml.RecipeData.getInstance().getRecipeList(20);
				final var skill = org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(172, 2);
				PhantomAssertions.assertTrue(recipe != null && recipe.getLevel() == 2 && recipe.getItemId() == 1463 && recipe.getCount() == 468 && recipe.getSuccessRate() == 100 && skill != null, "Native D-shot recipe/Artisan skill differs from declared finite input.");
				setup.rewardSkills(); setup.addSkill(skill, true); setup.registerDwarvenRecipeList(recipe, true);
				setup.getManufactureItems().put(20, new org.l2jmobius.gameserver.model.item.recipe.ManufactureItem(20, 10));
				setup.setStoreName("TEST060 finite D-shot craft"); setup.setPrivateStoreType(PrivateStoreType.MANUFACTURE);
				setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.setCurrentCp(setup.getMaxCp());
			}
			else
			{
				final var stock = setup.getInventory().addItem(ItemProcessType.REWARD, 1835, 300, null, PhantomOfflineSupply060Fixture.class);
				PhantomAssertions.assertTrue(stock != null && setup.getSellList().addItem(stock.getObjectId(), 300, 6) != null, "Finite offline sell listing failed.");
				stockObjectId = stock.getObjectId(); setup.getSellList().setTitle("TEST060 finite shots"); setup.setPrivateStoreType(PrivateStoreType.SELL);
			}
			setup.sitDown(); setup.setOfflineStartTime(System.currentTimeMillis());
			setup.storeMe(); OfflineTraderTable.getInstance().onTransaction(setup, false, true);
			PhantomAssertions.assertEquals(1L, offlineRows("SELECT COUNT(*) FROM character_offline_trade WHERE charId=" + fixture.objectId()), "Native offline row was not saved.");
			environment.cleanupLoadedPlayer(setup);
			OfflineTraderTable.getInstance().restoreOfflineTraders();
			final var restored = World.getInstance().getPlayer(fixture.objectId());
			PhantomAssertions.assertTrue(restored != null && restored.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE), "Native restored seller is outside its peace zone.");
			final int expectedStockObject = stockObjectId;
			PhantomAssertions.assertTrue(PhantomOfflineSupply.offline(restored) && restored.getClient() == null && (manufacture ? restored.getPrivateStoreType() == PrivateStoreType.MANUFACTURE && restored.getManufactureItems().get(20) != null && restored.hasRecipeList(20) && restored.getSkillLevel(172) == 2 : restored.getPrivateStoreType() == PrivateStoreType.SELL && restored.getSellList().getItems().stream().anyMatch(line -> line.getObjectId() == expectedStockObject)), "Native offline restoration did not restore the exact published listing.");
			context.record("life060.offlineInput", "PREMEASUREMENT one finite dwarf=" + restored.getObjectId() + "; native OfflineTraderTable save/deleteMe/restore; client=NULL/online/offlineStartTime=" + restored.getOfflineStartTime() + (manufacture ? "; PREPARED Artisan56/20/172:2/recipe20; one craft fee10/ingredients9ore+3Dcrystals/customer; nativeMP=" + restored.getCurrentMp() + "; nativechance100; not earned profession" : "; 300NG1835 @6 Adena") + "; actual peace-zone counter=" + merchant.getId() + "; XYZ=" + restored.getX() + ',' + restored.getY() + ',' + restored.getZ() + "; no live socket logout claim; no later test listing/resource/XYZ writes");
			return new PhantomOfflineSupply060Fixture(environment, fixture.objectId(), realtime, count, restored, manufacture);
		}
		catch (Throwable failure)
		{
			final var current = World.getInstance().getPlayer(fixture.objectId());
			OfflineTraderTable.getInstance().onTransaction(current == null ? setup : current, true, false);
			environment.cleanupLoadedPlayer(current == null ? setup : current);
			OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME = realtime; World.OFFLINE_TRADE_COUNT = count;
			throw failure;
		}
	}
	Player actor() { return World.getInstance().getPlayer(_objectId); }

	boolean observePaid(PhantomTestContext context, long profileId, Player buyer, PhantomGoal goal, PhantomEconomyReservationService reservations)
	{
		if (_paid) { return true; }
		final var spec = PhantomOfflineSupply.spec(goal);
		PhantomAssertions.assertTrue(spec instanceof PhantomSocialEconomyGoalSpec.StoreBuy && spec.counterpartyCharacterObjectId() == _objectId && spec.counterpartyProfileId() == 0, "Offline chain selected a different or non-offline supply channel.");
		final var buy = (PhantomSocialEconomyGoalSpec.StoreBuy) spec;
		final var audit = reservations.findAudit(profileId, 16).stream().filter(value -> value.state() == PhantomEconomyOperation.State.COMMITTED).findFirst().orElseThrow(() -> new AssertionError("Offline purchase has no COMMITTED native operation audit."));
		final var operation = reservations.find(audit.operationId()).orElseThrow();
		PhantomAssertions.assertTrue(operation.goalId() == goal.goalId() && operation.goalRevision() + 1 == goal.revision() && operation.state() == PhantomEconomyOperation.State.COMMITTED && audit.result() == PhantomEconomyOperation.Result.SUCCESS, "Offline operation identity/result drifted.");
		final var seller = World.getInstance().getPlayer(_objectId);
		final long amount = buy.lines().stream().mapToLong(line -> Math.multiplyExact(line.count(), line.price())).sum(), quantity = buy.lines().stream().mapToLong(line -> line.count()).sum();
		PhantomAssertions.assertTrue(seller != null && seller.getAdena() - _sellerAdena == amount && _sellerStock - seller.getInventory().getInventoryItemCount(1835, -1) == quantity && buyer.getInventory().getInventoryItemCount(1835, -1) >= quantity, "Offline native seller fee/stock or buyer product conservation failed.");
		PhantomAssertions.assertTrue(reservations.findReservations(audit.operationId()).isEmpty(), "Committed offline purchase retained reservations.");
		context.record("life060.offlinePaid", "native operation=" + operation + "; audit=" + audit + "; exact seller Adena=" + _sellerAdena + "->" + seller.getAdena() + "; stock=" + _sellerStock + "->" + seller.getInventory().getInventoryItemCount(1835, -1) + "; buyer stock=" + buyer.getInventory().getInventoryItemCount(1835, -1) + "; published quantity=" + quantity + "; fee=" + amount);
		_paid = true; return true;
	}

	@Override public void close() throws Exception
	{
		final var seller = World.getInstance().getPlayer(_objectId);
		if (seller != null)
		{
			PhantomAssertions.assertTrue(!seller.isCrafting(), "Offline fixture refuses cleanup of an active native craft.");
			OfflineTraderTable.getInstance().onTransaction(seller, true, false);
			seller.setOfflineStartTime(0); seller.setPrivateStoreType(PrivateStoreType.NONE);
			if (World.getInstance().getPlayer(_objectId) == seller && seller.isOnline()) { _environment.cleanupLoadedPlayer(seller); }
		}
		OfflineTradeConfig.STORE_OFFLINE_TRADE_IN_REALTIME = _realtime; World.OFFLINE_TRADE_COUNT = _offlineCount;
		PhantomAssertions.assertEquals(0L, offlineRows("SELECT COUNT(*) FROM character_offline_trade WHERE charId=" + _objectId), "Own offline status residue remains.");
		PhantomAssertions.assertEquals(0L, offlineRows("SELECT COUNT(*) FROM character_offline_trade_items WHERE charId=" + _objectId), "Own offline listing residue remains.");
	}
	private static long offlineRows(String sql) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) { PhantomAssertions.assertTrue(rows.next(), "Offline guard query returned no row."); return rows.getLong(1); }
	}
}

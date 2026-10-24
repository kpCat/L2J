/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.Set;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy.Disposition;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.qol.CrystallizationService;

/** Native owner positive control; it does not stand in for an autonomous paid procurement chain. */
final class PhantomCrystal074Native
{
	static boolean run(StringBuilder report) throws Exception
	{
		Player buyer = null, seller = null;
		try
		{
			final var merchant = World.getInstance().getVisibleObjects().stream().filter(object -> object instanceof Merchant npc && npc.getId() == 30063 && npc.isSpawned() && npc.getInstanceId() == 0).map(object -> (Merchant) object).findFirst().orElseThrow();
			final var counter = PhantomOfflineSupply060Fixture.counterPoint(new PhantomNavigationPoint(merchant.getX() + 1000, merchant.getY(), merchant.getZ(), 0), merchant.getX(), merchant.getY(), merchant.getZ()); check(counter != null, "CRYST074 lawful premeasurement point unavailable.");
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task074crystal','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { statement.executeUpdate(); }
			buyer = Player.create(PlayerTemplateData.getInstance().getTemplate(53), "task074crystal", "Crystal074", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)); check(buyer != null && !buyer.isGM(), "CRYST074 native buyer fixture absent.");
			buyer.setPlayerClass(57); buyer.setBaseClass(57); buyer.getStat().setExp(buyer.getStat().getExpForLevel(52)); buyer.getStat().setLevel((byte) 52); buyer.giveAvailableAutoGetSkills(); final var skill = SkillData.getInstance().getSkill(248, 3); check(skill != null, "CRYST074 native crystallize248:3 absent."); buyer.addSkill(skill, true);
			final var equipped = buyer.addItem(ItemProcessType.REWARD, 2487, 1, buyer, false); buyer.getInventory().equipItem(equipped); final var owned = buyer.addItem(ItemProcessType.REWARD, 12200, 1, buyer, false);
			buyer.setXYZ(counter.x() + 80, counter.y(), counter.z()); buyer.setCurrentHp(buyer.getMaxHp()); buyer.setCurrentMp(buyer.getMaxMp()); buyer.setOnlineStatus(true, false); buyer.spawnMe(); buyer.refreshExpertisePenalty();
			seller = Player.create(PlayerTemplateData.getInstance().getTemplate(53), "task074crystal", "CrystalQuote074", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)); check(seller != null, "CRYST074 actual foreign item owner absent."); final var foreign = seller.addItem(ItemProcessType.REWARD, 12200, 1, seller, false); seller.setXYZ(counter.x(), counter.y(), counter.z()); seller.setCurrentHp(seller.getMaxHp()); seller.setCurrentMp(seller.getMaxMp()); seller.setOnlineStatus(true, false); seller.spawnMe(); check(seller.canOpenPrivateStore() && seller.getSellList().addItem(foreign.getObjectId(), 1, 25000) != null, "CRYST074 finite foreign listing unavailable."); seller.setOfflineStartTime(System.currentTimeMillis()); seller.sitDown(); seller.setPrivateStoreType(PrivateStoreType.SELL);
			final var policy = PhantomLootDispositionPolicy.getInstance(); final var service = CrystallizationService.getInstance(); final long crystalsBefore = buyer.getInventory().getInventoryItemCount(1460, -1); final long buyerMoney = buyer.getAdena(), sellerMoney = seller.getAdena();
			report.append("CRYST074_MEASUREMENT\tbuyer=").append(buyer.getObjectId()).append(";seller=").append(seller.getObjectId()).append(";declaredPREMEASUREMENTWarsmith52/248:3/equipped2487/nativeCommon12200x1;foreign12200x1@25000;crystalsBefore=").append(crystalsBefore).append(";fixtureitems not paid/earned here;no grants/skill/gear/HP/position writes after marker;native positive control only\n");
			final var quote = policy.prospectiveCrystallization(buyer, foreign, Set.of()).orElseThrow(() -> new AssertionError("CRYST074 safe real foreign Common item had no prospective native quote."));
			check(quote.crystalItemId() == 1460 && quote.crystalCount() == 5 && quote.itemObjectId() == foreign.getObjectId() && quote.playerObjectId() == buyer.getObjectId(), "CRYST074 prospective quote changed native identity/output.");
			check(service.inspect(buyer, foreign.getObjectId(), 1).status() == CrystallizationService.Status.ITEM_NOT_FOUND && service.crystallize(buyer, foreign.getObjectId(), 1, quote).status() == CrystallizationService.Status.ITEM_NOT_FOUND && foreign.getCount() == 1, "CRYST074 read-only prospective quote bypassed actual ownership.");
			check(policy.evaluate(buyer, owned, Set.of(12200), false).disposition() == Disposition.KEEP && policy.evaluate(buyer, equipped, Set.of(), false).disposition() == Disposition.KEEP, "CRYST074 needed/equipped gear lost protection.");
			check(policy.evaluate(buyer, owned, Set.of(), false).disposition() == Disposition.CRYSTALLIZE, "CRYST074 unused Common item was not admitted through native inspection.");
			final var inspection = service.inspect(buyer, owned.getObjectId(), 1); check(inspection.eligible() && inspection.preview().crystalCount() == 5 && inspection.preview().crystalItemId() == 1460, "CRYST074 actual owned native preview differs."); final int destroyed = owned.getObjectId();
			final var result = service.crystallize(buyer, destroyed, 1, inspection.preview());
			check(result.success() && buyer.getInventory().getItemByObjectId(destroyed) == null && buyer.getInventory().getInventoryItemCount(1460, -1) == crystalsBefore + 5 && buyer.getInventory().getItemByObjectId(equipped.getObjectId()) == equipped && equipped.isEquipped() && foreign.getCount() == 1 && buyer.getAdena() == buyerMoney && seller.getAdena() == sellerMoney, "CRYST074 native destruction/output or protected stock conservation failed.");
			check(service.crystallize(buyer, destroyed, 1, inspection.preview()).status() == CrystallizationService.Status.ITEM_NOT_FOUND && buyer.getInventory().getInventoryItemCount(1460, -1) == crystalsBefore + 5, "CRYST074 stale object replay duplicated output."); buyer.getInventory().updateDatabase(); buyer.storeMe();
			report.append("CRYST074_NATIVE_EFFECT\tprospective=").append(quote).append(";ownedInspection=").append(inspection).append(";result=").append(result).append(";crystals1460=").append(crystalsBefore).append("->").append(buyer.getInventory().getInventoryItemCount(1460, -1)).append(";exactdestroyedObject=").append(destroyed).append(";foreign/equipped/needed/replay negatives PASS\nCRYST074_RESULT\tPASS native Common12200->5B1460 positive control;unowned planning read-only;paid equipmentBUY->CRYST receipt->ore/MFG/restart remains separate chain evidence\n"); return true;
		}
		catch (Exception | AssertionError failure) { report.append("CRYST074_RESULT\tPARTIAL;").append(failure.getClass().getSimpleName()).append(':').append(failure.getMessage()).append('\n'); return false; }
		finally { try { if (seller != null) { seller.setOfflineStartTime(0); seller.stopAllTasks(); seller.storeMe(); seller.deleteMe(); } } finally { if (buyer != null) { buyer.stopAllTasks(); buyer.storeMe(); buyer.deleteMe(); } } }
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

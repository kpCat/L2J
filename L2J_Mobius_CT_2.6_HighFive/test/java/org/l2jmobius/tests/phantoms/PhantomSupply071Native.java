/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.Arrays;
import java.util.Comparator;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.enums.BodyPart;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeShotControl;
import org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy;

/** Called only with a dedicated own071 fixture: native thresholds/equip/shot debit, never earned acquisition. */
public final class PhantomSupply071Native
{
	private PhantomSupply071Native() { }

	public static void verifyGrades(Player player, StringBuilder report)
	{
		verifyGrades(player, report, 19);
	}

	public static void resumeGrades(Player player, StringBuilder report)
	{
		final String actual = loadedGradeState(player); report.append("gear071.loaded_before_resume\t").append(actual).append('\n');
		check(player != null && player.getObjectId() == 268435468 && "task071combat".equals(player.getAccountName()) && "Supply071Grade".equals(player.getName()) && player.getLevel() == 40 && player.getActiveClass() == 0 && player.getExpertiseLevel() == 2, "Only the retained failed40 own071 grade fixture may resume; no replay/refill; actual=" + actual);
		report.append("gear071.resume\tRead-only checkpoint confirmed retained actor268435468 level40/class0/expertiseSQL2 after native Player.restore rewardSkills; earlier19/20 reports remain authoritative; continue40+ with stock profession trees, no earlier gear/shot replay\n");
		verifyGrades(player, report, 40);
	}

	/** Read-only persisted checkpoint diagnostics; does not load Player or reward/adjust a skill. */
	public static void inspectStoredGrades(StringBuilder report) throws Exception
	{
		check(PhantomTask071Lane.enabled(), "Explicit own071 native inspection required.");
		try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT c.charId,c.account_name,c.char_name,c.classid,c.base_class,c.level,c.exp,s.skill_level FROM characters c LEFT JOIN character_skills s ON s.charId=c.charId AND s.skill_id=239 AND s.class_index=0 WHERE c.account_name='task071combat' AND c.char_name='Supply071Grade' ORDER BY c.charId"); var rows = query.executeQuery())
		{
			while (rows.next()) { report.append("gear071.stored_checkpoint\tactor=").append(rows.getInt(1)).append(";account=").append(rows.getString(2)).append(";name=").append(rows.getString(3)).append(";class=").append(rows.getInt(4)).append(";baseClass=").append(rows.getInt(5)).append(";level=").append(rows.getInt(6)).append(";EXP=").append(rows.getLong(7)).append(";expertiseSQL=").append(rows.getObject(8)).append('\n'); }
		}
	}

	private static String loadedGradeState(Player player)
	{
		return player == null ? "player=null" : "actor=" + player.getObjectId() + ";account=" + player.getAccountName() + ";name=" + player.getName() + ";class=" + player.getActiveClass() + ";baseClass=" + player.getBaseClass() + ";classIndex=" + player.getClassIndex() + ";level=" + player.getLevel() + ";EXP=" + player.getExp() + ";expertise=" + player.getExpertiseLevel();
	}

	private static void verifyGrades(Player player, StringBuilder report, int firstLevel)
	{
		check(PhantomTask071Lane.enabled() && player != null && !player.isGM() && player.getAccountName().startsWith("task071"), "Dedicated access0 own071 fixture required.");
		report.append("gear071.fixture\tTEST_SETUP_ONLY native level/class0->1at20->2at40->88at76/autoGet expertise/finite candidate gear+shots; no earned profession, currency, market or complete loadout claim\n");
		final int[] levels = {19, 20, 40, 52, 61, 76, 80, 84};
		final CrystalType[] grades = CrystalType.values();
		for (int index = 0; index < levels.length; index++)
		{
			final int level = levels[index]; final CrystalType grade = grades[index];
			if (level < firstLevel) { continue; }
			for (var current : player.getInventory().getItems().stream().filter(item -> item.isEquipped()).toList()) { player.useEquippableItem(current, false); }
			player.getStat().setExp(player.getStat().getExpForLevel(level)); player.getStat().setLevel((byte) level);
			final int profession = level >= 76 ? 88 : level >= 40 ? 2 : level >= 20 ? 1 : 0;
			player.setPlayerClass(profession); player.setBaseClass(profession); player.setLearningClass(player.getPlayerClass());
			if (firstLevel == 19 && level == 19) { player.removeSkill(239); }
			player.giveAvailableAutoGetSkills(); player.refreshExpertisePenalty();
			check(PhantomNativeEquipmentPolicy.targetGrade(level, player.getExpertiseLevel()) == grade && Math.max(0, player.getExpertiseLevel()) == grade.getLevel(), "Native autoGet expertise differs at level " + level + ": " + player.getExpertiseLevel());
			final var weaponTemplate = candidate(player, grade, true);
			final var armorTemplate = candidate(player, grade, false);
			final var weapon = player.getInventory().addItem(ItemProcessType.REWARD, weaponTemplate.getId(), 1, player, PhantomSupply071Native.class);
			final var armor = player.getInventory().addItem(ItemProcessType.REWARD, armorTemplate.getId(), 1, player, PhantomSupply071Native.class);
			check(weapon != null && armor != null, "Finite native fixture item creation failed.");
			player.useEquippableItem(weapon, false); player.useEquippableItem(armor, false);
			check(weapon.isEquipped() && armor.isEquipped() && player.getActiveWeaponInstance() == weapon, "Native equip did not confirm legal target grade " + grade);
			final var actualWeapon = player.getActiveWeaponItem(); final int perHit = player.isMageClass() ? actualWeapon.getSpiritShotCount() : actualWeapon.getSoulShotCount();
			final ActionType action = player.isMageClass() ? ActionType.SPIRITSHOT : ActionType.SOULSHOT;
			final var shotTemplate = Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull)
				.filter(item -> item.getDefaultAction() == action && item.getCrystalType() == actualWeapon.getCrystalTypePlus() && L2jPhantomSupplyJourney.compatible(player, item))
				.min(Comparator.comparingInt(ItemTemplate::getId)).orElseThrow(() -> new AssertionError("No actual compatible shot template for " + grade));
			check(perHit > 0, "Actual weapon has no native shot cost.");
			final long initialStock = player.getInventory().getInventoryItemCount(shotTemplate.getId(), -1);
			player.getInventory().addItem(ItemProcessType.REWARD, shotTemplate.getId(), (long) perHit * 5, player, PhantomSupply071Native.class);
			final long before = player.getInventory().getInventoryItemCount(shotTemplate.getId(), -1);
			check(PhantomNativeShotControl.chargeOffensive(player, player, player.isMageClass()) == ShotOutcome.ACTIVATED, "Native shot handler did not charge " + grade);
			final long after = player.getInventory().getInventoryItemCount(shotTemplate.getId(), -1);
			check(before - after == perHit, "Native shot handler did not consume exact current weapon cost " + grade + ": " + before + "->" + after);
			report.append("gear071.native\tlevel=").append(level).append(";nativeClass=").append(player.getActiveClass()).append(";expertise=").append(player.getExpertiseLevel()).append(";target=").append(grade)
				.append(";weapon=").append(weapon.getId()).append(";armor=").append(armor.getId()).append(";shotGrade=").append(actualWeapon.getCrystalTypePlus())
				.append(";shot=").append(shotTemplate.getId()).append(";stock=").append(before).append("->").append(after).append(";perHit=").append(perHit).append(";preexistingStock=").append(initialStock).append('\n');
			player.getInventory().unEquipItemInSlot(weapon.getLocationSlot()); player.getInventory().unEquipItemInSlot(armor.getLocationSlot());
			check(player.getInventory().destroyItem(ItemProcessType.DESTROY, weapon, 1, player, PhantomSupply071Native.class) != null && player.getInventory().destroyItem(ItemProcessType.DESTROY, armor, 1, player, PhantomSupply071Native.class) != null, "Declared finite gear input cleanup failed.");
		}
	}

	private static ItemTemplate candidate(Player player, CrystalType grade, boolean weapon)
	{
		return Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull)
			.filter(item -> item.getCrystalType() == grade && item.getReferencePrice() > 0 && item.getDefaultEnchantLevel() == 0 && item.isTradeable()
				&& (weapon ? item instanceof Weapon value && !value.isRange() : item.getBodyPart() == BodyPart.CHEST || item.getBodyPart() == BodyPart.FULL_ARMOR))
			.filter(item -> PhantomNativeEquipmentPolicy.compatible(player, item) && PhantomNativeEquipmentPolicy.power(player, item) > 0)
			.min(Comparator.comparingLong(ItemTemplate::getReferencePrice).thenComparingInt(ItemTemplate::getId)).orElseThrow(() -> new AssertionError("No actual legal " + grade + (weapon ? " weapon" : " armor")));
	}

	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
}

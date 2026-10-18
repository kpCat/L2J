/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.BabyPet;
import org.l2jmobius.gameserver.model.actor.instance.Pet;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;

/** Finite native control item and initial injury; native BabyPet alone chooses and casts support. */
final class PhantomPet060NativeSupport
{
	private static void preparePetField(PhantomTestContext context, Player owner)
	{
		final var geo = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
		final int originX = owner.getX(), originY = owner.getY(), originZ = owner.getZ();
		for (int dx : new int[] {0, 128, -128, 256, -256, 512, -512, 1024, -1024})
		{
			for (int dy : new int[] {0, 128, -128, 256, -256, 512, -512, 1024, -1024})
			{
				final int x = originX + dx, y = originY + dy, z = geo.getHeight(x, y, originZ) + 5;
				final int petZ = geo.getHeight(x + 50, y + 100, z);
				final boolean clear = geo.hasGeo(x, y) && geo.hasGeo(x + 50, y + 100) && Math.abs((long) petZ + 5 - z) <= 8
					&& geo.canMoveToTarget(x, y, z, x + 50, y + 100, z, 0) && geo.canMoveToTarget(x + 50, y + 100, z, x, y, z, 0)
					&& geo.canSeeTarget(x, y, z + 25, x + 50, y + 100, z + 25, 0);
				if (!clear) { continue; }
				owner.decayMe(); owner.setXYZInvisible(x, y, z); owner.spawnMe(); owner.revalidateZone(true);
				PhantomAssertions.assertTrue(!owner.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.WATER) && !owner.isInCombat() && !owner.isDead(), "Pet field is not a declared dry ordinary input.");
				context.record("pet060.clearance", "PREMEASUREMENT bounded81 centres; actual native summon offset50/100 bidirectional WALK/LOS/height<=8; ground+5 centre=" + x + ',' + y + ',' + z + "; invul=" + owner.isInvul() + "; teleportProtected=" + owner.isTeleportProtected());
				return;
			}
		}
		throw new AssertionError("BLOCKED_NATIVE_PET_FIELD: bounded81 premeasurement centres lack native summon offset WALK/LOS clearance.");
	}
	private static String observed(Pet pet, Player owner)
	{
		return "skill=" + (pet.getLastSkillCast() == null ? 0 : pet.getLastSkillCast().getId()) + "; casting=" + pet.isCastingNow() + "; AI=" + pet.getAI().getIntention() + "; LOS=" + org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().canSeeTarget(pet, owner) + "; range=" + (int) pet.calculateDistance3D(owner) + "; invul=" + owner.isInvul() + "; ownerCombat=" + owner.isInCombat();
	}
	private static boolean nativeBuffsReady(Pet pet, Player owner)
	{
		final var data = org.l2jmobius.gameserver.data.xml.PetDataTable.getInstance().getPetData(pet.getId());
		int expected = 0;
		for (var learn : data.getAvailableSkills())
		{
			final int level = data.getAvailableLevel(learn.getSkillId(), pet.getLevel());
			final var skill = level <= 0 ? null : org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(learn.getSkillId(), level);
			if (skill == null || skill.getId() == 5771 || skill.getId() == 5753 || !skill.isContinuous() || skill.isDebuff() || skill.hasEffectType(EffectType.HEAL) || skill.hasEffectType(EffectType.MANAHEAL_BY_LEVEL)) { continue; }
			expected++;
			final var active = owner.getEffectList().getBuffInfoByAbnormalType(skill.getAbnormalType());
			if (active == null || active.getSkill().getAbnormalLevel() < skill.getAbnormalLevel()) { return false; }
		}
		return expected > 0;
	}
	static void automaticMana(PhantomTestContext context, Player owner, org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend backend, long profileId) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		PhantomAssertions.assertTrue(!owner.hasSummon() && owner.getClient() == null, "Pet060 automatic input retained another summon/client.");
		owner.setBaseClass(10); owner.setPlayerClass(10); owner.setLearningClass(owner.getPlayerClass()); owner.getStat().setLevel((byte) 55);
		owner.setCurrentHp(owner.getMaxHp()); owner.setCurrentMp(owner.getMaxMp()); owner.setTarget(null);
		preparePetField(context, owner);
		final var control = owner.getInventory().addItem(ItemProcessType.REWARD, 10313, 1, owner, PhantomPet060NativeSupport.class);
		PhantomAssertions.assertTrue(control != null, "Native Kookaburra control input unavailable.");
		final Pet prepared = Pet.spawnPet(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(16035), owner, control);
		PhantomAssertions.assertTrue(prepared instanceof BabyPet && prepared.getLevel() == 55, "Native minimum55 Kookaburra input differs.");
		owner.setPet(prepared); prepared.fullRestore(); prepared.setCurrentFed(prepared.getMaxFed() * 40 / 100);
		prepared.getInventory().addItem(ItemProcessType.REWARD, 10425, 4, owner, PhantomPet060NativeSupport.class);
		prepared.spawnMe(owner.getX() + 50, owner.getY() + 100, owner.getZ()); prepared.unSummon(owner);
		owner.setCurrentMp(owner.getMaxMp() * .2);
		final var spawn = new org.l2jmobius.gameserver.model.spawns.Spawn(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(20120));
		spawn.setXYZ(owner.getX() + 100, owner.getY(), owner.getZ());
		final var target = spawn.doSpawn(false); spawn.stopRespawn();
		context.record("pet060.manaInput", "PREMEASUREMENT HumanMystic10/55/HPfull/MP20%; own10313/native Kookaburra16035/55; native prepared petMP=" + prepared.getCurrentMp() + "/" + prepared.getMaxMp() + "; saved food4x10425/feed40%; one full stock Wolf20120; no earned pet evolution/class claim; one declared native combat-stance event after auto summon");
		try
		{
			try (var lease = backend.tryAcquireActor(profileId))
			{
				PhantomAssertions.assertTrue(lease != null, "Native pet producer lacked its actor lease.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome.UNAVAILABLE, lease.attack(target.getObjectId()), "Combat owner did not defer its attack to its owned native support pet item.");
			}
			final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (!owner.hasSummon() && System.nanoTime() < deadline) { Thread.sleep(100); }
			PhantomAssertions.assertTrue(owner.getSummon() instanceof BabyPet && owner.getSummon().getId() == 16035 && owner.getSummon().getControlObjectId() == control.getObjectId(), "Production did not select/summon the actual owned native Kookaburra.");
			final Pet pet = (Pet) owner.getSummon();
			final long food = pet.getInventory().getInventoryItemCount(10425, -1);
			PhantomAssertions.assertEquals(4L, food, "Native pet restore lost its finite prepared food before the feed tick.");
			final long buffDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
			while (!nativeBuffsReady(pet, owner) && System.nanoTime() < buffDeadline) { Thread.sleep(100); }
			context.record("pet060.nativeBuffReadiness", "all actual available native buffs=" + nativeBuffsReady(pet, owner) + "; " + observed(pet, owner));
			PhantomAssertions.assertTrue(nativeBuffsReady(pet, owner), "Native pet buffs did not take effect before the separate declared combat event.");
			PhantomAssertions.assertTrue(pet.getLastSkillCast() == null || !pet.getLastSkillCast().hasEffectType(EffectType.MANAHEAL_BY_LEVEL), "Native pet recharged a noncombat owner.");
			owner.getAI().clientStartAutoAttack();
			PhantomAssertions.assertTrue(owner.isInCombat(), "Declared native combat-stance input did not start.");
			boolean mana = false;
			double previousMp = owner.getCurrentMp(), previousMax = owner.getMaxMp(), petMp = pet.getCurrentMp();
			final double inputMp = previousMp;
			final var trace = new java.util.ArrayList<String>(); String last = "";
			final long supportDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while ((!mana || pet.getInventory().getInventoryItemCount(10425, -1) >= food) && System.nanoTime() < supportDeadline)
			{
				Thread.sleep(100);
				final var cast = pet.getLastSkillCast();
				final String current = observed(pet, owner); if (!current.equals(last) && trace.size() < 16) { trace.add(current); last = current; }
				mana |= cast != null && cast.hasEffectType(EffectType.MANAHEAL_BY_LEVEL) && owner.getMaxMp() == previousMax && owner.getCurrentMp() > previousMp + 30 && pet.getCurrentMp() < petMp;
				previousMp = owner.getCurrentMp(); previousMax = owner.getMaxMp();
			}
			context.record("pet060.mana", "production selected native item; pet=" + pet.getObjectId() + "; owner=" + owner.getObjectId() + "; ownerMP=" + inputMp + "->" + owner.getCurrentMp() + "; petMP=" + petMp + "->" + pet.getCurrentMp() + "; actualRecharge=" + mana + "; lastSkill=" + pet.getLastSkillCast() + "; food=" + food + "->" + pet.getInventory().getInventoryItemCount(10425, -1) + "; feed=" + pet.getCurrentFed());
			context.record("pet060.manaTrace", trace);
			PhantomAssertions.assertTrue(mana && pet.getOwner() == owner && pet.getInstanceId() == owner.getInstanceId() && pet.getInventory().getInventoryItemCount(10425, -1) < food, "Native Kookaburra did not actually recharge its owner/consume its own finite food.");
		}
		finally
		{
			org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(owner); owner.getAI().clientStopAutoAttack(); owner.abortAttack(); owner.abortCast();
			final var nativePet = World.getInstance().getPet(owner.getObjectId());
			if (nativePet != null && nativePet.getControlObjectId() == control.getObjectId()) { nativePet.deleteMe(owner); }
			if (owner.getInventory().getItemByObjectId(control.getObjectId()) == control) { owner.getInventory().destroyItem(ItemProcessType.DESTROY, control, owner, PhantomPet060NativeSupport.class); }
			if (target != null && target.isSpawned()) { target.deleteMe(); }
			owner.setTarget(null);
		}
	}

	static void directHeal(PhantomTestContext context, Player owner) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		PhantomAssertions.assertTrue(owner.getClient() == null && !owner.hasSummon() && owner.isSpawned(), "Pet060 requires its ordinary materialized own Player.");
		preparePetField(context, owner);
		owner.getStat().setLevel((byte) 55);
		owner.setCurrentMp(owner.getMaxMp());
		owner.setCurrentHp(owner.getMaxHp() * .2);
		final var control = owner.getInventory().addItem(ItemProcessType.REWARD, 10311, 1, owner, PhantomPet060NativeSupport.class);
		PhantomAssertions.assertTrue(control != null && control.getEtcItem() != null, "Native Buffalo control input unavailable.");
		final var handler = ItemHandler.getInstance().getHandler(control.getEtcItem());
		PhantomAssertions.assertTrue(handler != null && handler.getClass().getSimpleName().equals("SummonItems"), "Actual native pet item handler unavailable.");
		context.record("pet060.input", "PREMEASUREMENT owned finite10311; owner55/HP20%; native Buffalo16034 minimum55; no learned evolution/resources injected during support; same native item handler and BabyPet timer");
		try
		{
			PhantomAssertions.assertTrue(handler.onItemUse(owner, control, false), "Ordinary own headless native pet item refused.");
			final long summonDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			while (!owner.hasSummon() && System.nanoTime() < summonDeadline) { Thread.sleep(100); }
			PhantomAssertions.assertTrue(owner.getSummon() instanceof BabyPet && owner.getSummon().getId() == 16034 && owner.getSummon().getControlObjectId() == control.getObjectId(), "Native summon effect did not create the exact owned Buffalo.");
			final var pet = (Pet) owner.getSummon();
			final double initialHp = owner.getCurrentHp(), initialMp = pet.getCurrentMp();
			boolean actualHeal = false;
			final var trace = new java.util.ArrayList<String>(); String last = "";
			final long supportDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
			double previousHp = initialHp, previousMp = initialMp;
			while (!actualHeal && System.nanoTime() < supportDeadline)
			{
				Thread.sleep(100);
				final var cast = pet.getLastSkillCast();
				final String current = observed(pet, owner); if (!current.equals(last) && trace.size() < 16) { trace.add(current); last = current; }
				actualHeal = owner.getCurrentHp() > previousHp + 100 && cast != null && cast.hasEffectType(EffectType.HEAL) && pet.getCurrentMp() < initialMp;
				previousHp = owner.getCurrentHp(); previousMp = pet.getCurrentMp();
			}
			context.record("pet060.heal", "native Pet=" + pet.getObjectId() + "; control=" + control.getObjectId() + "; owner=" + owner.getObjectId() + "; level=" + pet.getLevel() + "; HP=" + initialHp + "->" + owner.getCurrentHp() + "; petMP=" + initialMp + "->" + previousMp + "; lastSkill=" + pet.getLastSkillCast() + "; actualHeal=" + actualHeal);
			context.record("pet060.healTrace", trace);
			PhantomAssertions.assertTrue(actualHeal && pet.getOwner() == owner && World.getInstance().getPet(owner.getObjectId()) == pet, "Native Buffalo produced no actual owner heal with its own MP cost.");
			PhantomAssertions.assertFalse(handler.onItemUse(owner, control, false), "Existing native pet duplicated through its control item.");
			final int oldObject = pet.getObjectId();
			pet.unSummon(owner);
			PhantomAssertions.assertTrue(!owner.hasSummon() && !pet.isSpawned() && World.getInstance().findObject(oldObject) == null, "Ordinary native unSummon retained the old pet in World.");
			final long reloadDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
			while (!owner.hasSummon() && System.nanoTime() < reloadDeadline) { if (!owner.isCastingNow()) { handler.onItemUse(owner, control, false); } Thread.sleep(200); }
			PhantomAssertions.assertTrue(owner.getSummon() instanceof Pet restored && restored.isRespawned() && restored.getControlObjectId() == control.getObjectId() && World.getInstance().getPet(owner.getObjectId()) == restored, "Native saved pet reload lost control/identity or created a second owner pet.");
			context.record("pet060.lifecycle", "ordinary native control item->cast->Buffalo support->unSummon/store->same item native restore; existing pet duplicate refused; no whole-JVM or TP claim");
		}
		finally
		{
			final var nativePet = World.getInstance().getPet(owner.getObjectId());
			if (nativePet != null && nativePet.getControlObjectId() == control.getObjectId()) { nativePet.deleteMe(owner); }
			owner.abortCast();
			if (owner.getInventory().getItemByObjectId(control.getObjectId()) == control) { owner.getInventory().destroyItem(ItemProcessType.DESTROY, control, owner, PhantomPet060NativeSupport.class); }
		}
	}
}

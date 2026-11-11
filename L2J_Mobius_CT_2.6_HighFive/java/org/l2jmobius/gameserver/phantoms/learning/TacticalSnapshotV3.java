/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.*;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;

/** Bounded read-only native facts. Hostile players never expose inventory, true stats or cooldowns. */
public final class TacticalSnapshotV3
{
	public static String partyIdentity(Player actor)
	{
		final var party = actor.getParty();
		if (party == null) { return "solo:" + actor.getObjectId(); }
		return party.getLeader().getObjectId() + ":" + party.getMembers().stream().sorted(Comparator.comparingInt(Player::getObjectId))
			.map(member -> member.getObjectId() + "@" + epoch(member)).collect(java.util.stream.Collectors.joining(","));
	}
	public static long epoch(Creature actor)
	{
		if (actor instanceof Player player) { final var owner = player.getNativeWorkOwner(); return owner == null ? 0 : owner.epoch(); }
		if (actor instanceof Monster monster) { return monster.getNativeEvidenceTarget().spawnGeneration(); }
		if (actor instanceof Summon summon) { return epoch(summon.getOwner()); }
		return 0;
	}
	public static TacticalObservationV3 collect(Player actor, long profile, long decision, int maximumEntities)
	{
		if (maximumEntities < 3 || maximumEntities > TacticalObservationV3.MAX_ENTITIES || actor.getNativeWorkOwner() == null || !actor.getNativeWorkOwner().isCurrent())
		{ throw new IllegalArgumentException("Current native actor required"); }
		final var entities = new ArrayList<Entity>();
		entities.add(entity(actor, actor, Role.SELF));
		final var party = actor.getParty();
		final List<Player> members = party == null ? List.of(actor) : List.copyOf(party.getMembers());
		for (Player ally : members.stream().filter(v -> v != actor).sorted(Comparator.comparingInt(Player::getObjectId)).limit(8).toList())
		{ if (entities.size() < maximumEntities) { entities.add(entity(actor, ally, Role.ALLY)); } }
		int summons = 0;
		for (Player member : members)
		{
			if (member.getSummon() != null && summons < 2 && entities.size() < maximumEntities) { entities.add(entity(actor, member.getSummon(), Role.SUMMON)); summons++; }
		}
		for (Monster enemy : World.getInstance().getVisibleObjectsInRange(actor, Monster.class, 2000).stream().filter(v -> !v.isAlikeDead())
			.sorted(Comparator.comparingDouble((Monster v) -> actor.calculateDistance3D(v)).thenComparingInt(Monster::getObjectId)).limit(8).toList())
		{ if (entities.size() < maximumEntities) { entities.add(entity(actor, enemy, Role.ENEMY)); } }
		return new TacticalObservationV3(decision, System.nanoTime(), profile, actor.getObjectId(), epoch(actor), partyIdentity(actor), entities);
	}
	public static Entity entity(Player self, Creature actor, Role role)
	{
		final var facts = new EnumMap<Field, Fact>(Field.class);
		final boolean own = role == Role.SELF || actor instanceof Player player && player.isNativeWorkManaged() && role == Role.ALLY;
		final boolean nativeNpc = actor instanceof Monster || actor instanceof Summon;
		if (actor instanceof Monster npc) { fact(facts, Field.NPC_ID, npc.getId()); }
		if (own || nativeNpc) { fact(facts, Field.LEVEL, actor.getLevel()); }
		fact(facts, Field.HP, actor.getCurrentHp() / Math.max(1, actor.getMaxHp()));
		if (role != Role.ENEMY || nativeNpc)
		{
			fact(facts, Field.MAX_HP, actor.getMaxHp()); fact(facts, Field.MP, actor.getCurrentMp() / Math.max(1, actor.getMaxMp())); fact(facts, Field.MAX_MP, actor.getMaxMp());
		}
		if (own || nativeNpc)
		{
			fact(facts, Field.P_ATK, actor.getPAtk(self)); fact(facts, Field.M_ATK, actor.getMAtk(self, null));
			fact(facts, Field.P_DEF, actor.getPDef(self)); fact(facts, Field.M_DEF, actor.getMDef(self, null));
			fact(facts, Field.CAST_SPEED, actor.getMAtkSpd()); fact(facts, Field.ATTACK_SPEED, actor.getPAtkSpd());
			fact(facts, Field.MOVE_SPEED, actor.getMoveSpeed()); fact(facts, Field.CRITICAL, actor.getCriticalHit(self, null)); fact(facts, Field.EVASION, actor.getEvasionRate(self));
			fact(facts, Field.ELEMENT_ATTACK, actor.getAttackElementValue(actor.getAttackElement()));
			fact(facts, Field.ELEMENT_DEFENSE, actor.getDefenseElementValue(self.getAttackElement()));
		}
		fact(facts, Field.CASTING, actor.isCastingNow() ? 1 : 0); fact(facts, Field.MOVING, actor.isMoving() ? 1 : 0);
		fact(facts, Field.STUNNED, actor.isStunned() ? 1 : 0); fact(facts, Field.MUTED, actor.isMuted() ? 1 : 0);
		fact(facts, Field.DISTANCE, self.calculateDistance3D(actor)); fact(facts, Field.HEIGHT, (long) actor.getZ() - self.getZ());
		final double bearing = Math.atan2((long) self.getY() - actor.getY(), (long) self.getX() - actor.getX());
		final double heading = actor.getHeading() * (Math.PI * 2 / 65536.0);
		final double facing = Math.atan2(Math.sin(bearing - heading), Math.cos(bearing - heading));
		fact(facts, Field.FACING, facing); fact(facts, Field.BACK, Math.abs(facing) > Math.PI * .75 ? 1 : 0); fact(facts, Field.FLANK, Math.abs(facing) >= Math.PI * .25 && Math.abs(facing) <= Math.PI * .75 ? 1 : 0);
		final var geo = GeoEngine.getInstance(); final boolean hasGeo = geo.hasGeo(self.getX(), self.getY()) && geo.hasGeo(actor.getX(), actor.getY());
		fact(facts, Field.GEO, hasGeo ? 1 : 0); if (hasGeo) { fact(facts, Field.LOS, geo.canSeeTarget(self, actor) ? 1 : 0); }
		final var gear = new ArrayList<Gear>(); final var effects = new ArrayList<Effect>(); final var abilities = new ArrayList<Ability>(); final var past = new ArrayList<PastAction>();
		int classId = 0, ownerId = 0;
		if (actor instanceof Summon summon) { ownerId = summon.getOwner().getObjectId(); }
		if (actor instanceof Player player && own)
		{
			classId = player.getActiveClass(); fact(facts, Field.CLASS_ID, classId); fact(facts, Field.CP, player.getCurrentCp() / Math.max(1, player.getMaxCp())); fact(facts, Field.MAX_CP, player.getMaxCp());
			for (int slot = 0; slot < Inventory.PAPERDOLL_TOTALSLOTS; slot++)
			{
				final var item = player.getInventory().getPaperdollItem(slot);
				if (item != null) { gear.add(new Gear(slot, item.getId(), item.getTemplate().getCrystalType().getLevel(), item.getEnchantLevel(), item.getTemplate().getItemType().toString(), item.getAugmentation() == null ? 0 : item.getAugmentation().getAugmentationId(), true)); }
			}
			int gearHash = 1;
			for (Gear item : gear)
			{
				gearHash = 31 * gearHash + item.hashCode();
				if (item.slot() == Inventory.PAPERDOLL_RHAND) { fact(facts, Field.WEAPON_GRADE, item.grade()); fact(facts, Field.WEAPON_ENCHANT, item.enchant()); }
				if (item.slot() == Inventory.PAPERDOLL_CHEST) { fact(facts, Field.ARMOR_GRADE, item.grade()); }
			}
			fact(facts, Field.JEWEL_GRADE, gear.stream().filter(v -> Set.of(Inventory.PAPERDOLL_NECK, Inventory.PAPERDOLL_REAR, Inventory.PAPERDOLL_LEAR, Inventory.PAPERDOLL_RFINGER, Inventory.PAPERDOLL_LFINGER).contains(v.slot())).mapToInt(Gear::grade).average().orElse(0));
			fact(facts, Field.GEAR_SIGNATURE, gearHash / (double) Integer.MAX_VALUE);
			for (var info : player.getEffectList().getBuffs().stream().sorted(Comparator.comparingInt(v -> v.getSkill().getId())).limit(64).toList())
			{ effects.add(new Effect(info.getSkill().getId(), info.getSkill().getLevel(), Math.max(0, info.getTime()), true, false)); }
			for (var info : player.getEffectList().getDebuffs().stream().sorted(Comparator.comparingInt(v -> v.getSkill().getId())).limit(64 - effects.size()).toList())
			{ effects.add(new Effect(info.getSkill().getId(), info.getSkill().getLevel(), Math.max(0, info.getTime()), true, true)); }
			fact(facts, Field.EFFECT_COUNT, effects.size()); fact(facts, Field.DEBUFF_COUNT, effects.stream().filter(Effect::debuff).count()); fact(facts, Field.EFFECT_SIGNATURE, effects.hashCode() / (double) Integer.MAX_VALUE);
			if (role == Role.SELF)
			{
				for (var skill : player.getAllSkills().stream().filter(v -> !v.isPassive()).sorted(Comparator.comparingInt(v -> v.getId())).limit(128).toList())
				{
					final var timing = TacticalTimingV3.observe(player,skill);
					abilities.add(new Ability(skill.getId(), skill.getLevel(), Math.max(0, skill.getMagicLevel()), timing.mpInitial() + timing.mpFinal(),
						Math.max(0, skill.getHitTime()), timing.remainingReuseMillis().known() ? timing.remainingReuseMillis().value() : 0,
						Math.max(0, skill.getCastRange()), timing.readyNow(), timing));
				}
				fact(facts, Field.SKILL_COUNT, abilities.size());
				// Native lastSkill stores no historical target/time; current target cannot supply them.
				if (player.getLastSkillCast() != null) { fact(facts, Field.RECENT_SKILL, player.getLastSkillCast().getId()); }
			}
		}
		return new Entity(role, actor.getObjectId(), epoch(actor), ownerId, classId, facts, gear, effects, abilities, past);
	}
	private static void fact(Map<Field, Fact> facts, Field key, double value) { if (Double.isFinite(value)) { facts.put(key, Fact.known(value)); } }
	private TacticalSnapshotV3() { }
}

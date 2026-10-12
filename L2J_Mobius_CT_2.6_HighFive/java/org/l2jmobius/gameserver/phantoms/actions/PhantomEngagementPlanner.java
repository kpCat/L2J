/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.actions;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;

/** Bounded factual discovery; no movement, path search, ownership grant or combat execution. */
public final class PhantomEngagementPlanner
{
	public enum Kind { READY, NEED_APPROACH, UNAVAILABLE }
	public record Opportunity(Kind kind, PlayerNativeEvidence.Target target, PhantomNavigationPoint targetPosition, int skillId, int range, boolean nativeDirect, List<PhantomNavigationPoint> positions) { }
	private static final int[][] DIRECTIONS={{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
	public boolean eligible(Player player, Creature target, int npcId)
	{
		if(target==null||!target.isMonster()||target.isRaid()||target.isAlikeDead()||!target.isTargetable()||target.isInvul()||!target.asNpc().isShowName()||!target.isAutoAttackable(player)||target.getInstanceId()!=player.getInstanceId()||target.asNpc().getId()!=npcId){return false;}
		if(player.getAutoPlaySettings().isRespectfulHunting()&&target.getTarget()!=null&&target.getTarget()!=player&&!(player.hasSummon()&&player.getSummon().getObjectId()==target.getTarget().getObjectId())){return false;}
		return Math.abs((long)player.getZ()-target.getZ())<800 && player.calculateDistance2D(target)<=localRange(player);
	}
	private static int localRange(Player player) { return player.getAutoPlaySettings().isShortRange()?AutoPlayConfig.AUTO_PLAY_SHORT_RANGE:AutoPlayConfig.AUTO_PLAY_LONG_RANGE; }
	public Opportunity inspect(Player player, int npcId, Set<PlayerNativeEvidence.Target> excluded, ToIntFunction<Creature> load)
	{
		final var selected=player.getTarget();
		if(selected instanceof Creature target&&eligible(player,target,npcId)&&!excluded.contains(key(target))) {return inspect(player,target,npcId);}
		final var candidates=World.getInstance().getVisibleObjectsInRange(player,Creature.class,localRange(player)).stream()
			.filter(target->eligible(player,target,npcId)&&!excluded.contains(key(target)))
			.sorted(Comparator.comparingInt(load).thenComparingDouble(player::calculateDistance2D).thenComparingInt(Creature::getObjectId)).limit(8).toList();
		Opportunity approach=null;
		for(var target:candidates)
		{
			final var result=inspect(player,target,npcId);
			if(result.kind()==Kind.READY){return result;}
			if(approach==null&&result.kind()==Kind.NEED_APPROACH){approach=result;}
		}
		return approach==null?unavailable():approach;
	}
	public Opportunity inspect(Player player, Creature target, int npcId)
	{
		if(!eligible(player,target,npcId)){return unavailable();}
		final var geo=GeoEngine.getInstance();final var position=new PhantomNavigationPoint(target.getX(),target.getY(),target.getZ(),target.getInstanceId());
		// Stock PlayerAI.maybeMoveToPawn adds both native collision radii to the actual capability range.
		final Skill skill=offensiveSkill(player,target);final int range=Math.max(1,(skill==null?player.getPhysicalAttackRange():player.getMagicalAttackRange(skill))+player.getTemplate().getCollisionRadius()+target.getTemplate().getCollisionRadius());
		final boolean visible=geo.canSeeTarget(player,target);
		final boolean direct=visible&&geo.canMoveToTarget(player.getX(),player.getY(),player.getZ(),target.getX(),target.getY(),target.getZ(),player.getInstanceId());
		if(visible&&player.calculateDistance3D(target)<=range){return new Opportunity(Kind.READY,key(target),position,skill==null?0:skill.getId(),range,direct,List.of());}
		final var points=new java.util.ArrayList<PhantomNavigationPoint>(8);
		final int radius=Math.max(1,(int)(range*0.75));
		for(var direction:DIRECTIONS)
		{
			final double length=Math.hypot(direction[0],direction[1]);
			final int x=target.getX()+(int)(radius*direction[0]/length),y=target.getY()+(int)(radius*direction[1]/length);
			if(!geo.hasGeo(x,y)){continue;}
			final int z=geo.getHeight(x,y,target.getZ());final var point=new PhantomNavigationPoint(x,y,z,target.getInstanceId());
			if(point.distanceTo(position)>range||geo.getHeight(x,y,z)!=z||ZoneManager.getInstance().getZone(x,y,z,WaterZone.class)!=null){continue;}
			if(geo.canSeeTarget(x,y,z,point.instanceId(),target.getX(),target.getY(),target.getZ(),target.getInstanceId())&&geo.canMoveToTarget(x,y,z,target.getX(),target.getY(),target.getZ(),point.instanceId())){points.add(point);}
		}
		points.sort(Comparator.comparingDouble(point->Math.hypot((long)point.x()-player.getX(),(long)point.y()-player.getY())));
		return new Opportunity(direct||!points.isEmpty()?Kind.NEED_APPROACH:Kind.UNAVAILABLE,key(target),position,skill==null?0:skill.getId(),range,direct,List.copyOf(points));
	}
	private static Skill offensiveSkill(Player player, Creature target)
	{
		final var skills=List.copyOf(player.getAutoUseSettings().getAutoSkills());
		if(skills.isEmpty()){return null;}
		// AutoUse rotates from its current cursor; class labels and numeric IDs do not select its next action.
		final int start=Math.max(0,skills.indexOf(player.getAutoUseSettings().getNextSkillId()));
		for(int offset=0;offset<skills.size();offset++)
		{
			final Skill skill=player.getKnownSkill(skills.get((start+offset)%skills.size()));
			if(skill==null||!skill.isActive()||skill.isPassive()||skill.isToggle()||!skill.hasNegativeEffect()||player.isSkillDisabled(skill)){continue;}
			if(player.getCurrentMp()<Math.max(skill.getMpInitialConsume()+skill.getMpConsume(),player.getStat().getMpInitialConsume(skill)+player.getStat().getMpConsume(skill))||player.getCharges()<skill.getChargeConsumeCount()){continue;}
			if(skill.getItemConsumeCount()>0&&player.getInventory().getInventoryItemCount(skill.getItemConsumeId(),-1)<skill.getItemConsumeCount()){continue;}
			if(!skill.checkCondition(player,target,false)){continue;}
			return skill;
		}
		return null;
	}
	public static PlayerNativeEvidence.Target key(Creature target){return target!=null&&target.isAttackable()?target.asAttackable().getNativeEvidenceTarget():null;}
	private static Opportunity unavailable(){return new Opportunity(Kind.UNAVAILABLE,null,null,0,0,false,List.of());}
}

/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.taskmanagers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.data.xml.PetSkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.IItemHandler;
import org.l2jmobius.gameserver.handler.ItemHandler;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Playable;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target;
import org.l2jmobius.gameserver.model.actor.Summon;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Guard;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.EtcItem;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.AbnormalType;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillHolder;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.StockSoloSelection;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.PhantomPolicy;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.ScopedOffenseChoice;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickLease;

/**
 * @author Mobius
 */
public class AutoUseTaskManager
{
	private static final Set<Set<Player>> POOLS = ConcurrentHashMap.newKeySet();
	private static final Map<Player, PhantomPolicy> PHANTOM_POLICIES = new ConcurrentHashMap<>();
	private static final int POOL_SIZE = 200;
	private static final int TASK_DELAY = 300;
	private static final int REUSE_MARGIN_TIME = 3;
	private static final int MAX_SCOPED_SKILLS = 4;
	
	protected AutoUseTaskManager()
	{
	}

	static boolean submitScoped(ScopedOffenseChoice choice, BooleanSupplier current, Runnable nativeCast)
	{
		final Thread writer = Thread.currentThread();
		final var open = new AtomicBoolean(true);
		final var submitted = new AtomicBoolean();
		try
		{
			choice.aroundCast().accept(() ->
			{
				if (Thread.currentThread() != writer || !open.get() || submitted.get() || !current.getAsBoolean()) { return; }
				submitted.set(true);
				nativeCast.run();
			});
		}
		finally { open.set(false); }
		return submitted.get();
	}

	static List<Skill> scopedOffenseCandidates(Skill baseline, Collection<Skill> learned, Predicate<Skill> allowed, Predicate<Skill> ready)
	{
		if (baseline == null || !allowed.test(baseline) || !ready.test(baseline)) { return List.of(); }
		final var result = new ArrayList<Skill>(MAX_SCOPED_SKILLS);
		result.add(baseline);
		learned.stream().filter(skill -> skill != baseline && allowed.test(skill))
			.sorted(Comparator.comparingInt(Skill::getId).thenComparingInt(Skill::getLevel))
			.filter(ready).limit(MAX_SCOPED_SKILLS - 1).forEachOrdered(result::add);
		return result.size() < 2 ? List.of() : List.copyOf(result);
	}

	static boolean submitArchivedSolo(ScopedOffenseChoice choice, Skill stock, BooleanSupplier current, Runnable nativeCast)
	{
		return choice != null && choice.skill() == stock && submitScoped(choice, current, nativeCast);
	}
	
	private class AutoUse implements Runnable
	{
		private final Set<Player> _players;
		
		public AutoUse(Set<Player> players)
		{
			_players = players;
		}
		
		@Override
		public void run()
		{
			if (_players.isEmpty())
			{
				return;
			}
			
			for (Player player : _players)
			{
				final PhantomPolicy phantomPolicy = PHANTOM_POLICIES.get(player);
				if (player.isPhantomAutoPlayManaged() && (phantomPolicy == null)) { continue; }
				try
				{
				if (!player.isOnline() || (player.isInOfflineMode() && !player.isOfflinePlay()))
				{
					if (phantomPolicy == null) { stopAutoUseTask(player); } else { stopPhantomAutoUse(player, phantomPolicy); }
					continue;
				}
				final var admission = phantomPolicy == null ? null : phantomPolicy.acquireTick(player, "AutoUse");
				if ((admission != null) && (admission.status() == AutoPlayTaskManager.TickStatus.PAUSED)) { continue; }
				final TickLease lease = phantomPolicy == null ? () -> {} : admission.lease();
				if (lease == null)
				{
					phantomPolicy.stopObserved("AutoUse:" + admission.reason());
					stopPhantomAutoUse(player, phantomPolicy);
					continue;
				}
				try (lease)
				{
				final boolean archivedSolo = phantomPolicy != null && phantomPolicy.archivesSoloOffense(player);
				if (archivedSolo && !phantomPolicy.usesScopedOffense(player)) { continue; }
				if ((phantomPolicy != null) && (player.getTarget() != null) && player.getTarget().isCreature() && !phantomPolicy.permitsTarget(player.getTarget().asCreature()))
				{
					player.setTarget(null);
				}
				
				if (player.isSitting() || player.isStunned() || player.isSleeping() || player.isParalyzed() || player.isAfraid() || player.isAlikeDead() || player.isMounted() || (player.isTransformed() && player.getTransformation().isRiding()))
				{
					continue;
				}
				
				final boolean isInPeaceZone = player.isInsideZone(ZoneId.PEACE);
				
				if (AutoPlayConfig.ENABLE_AUTO_ITEM && !isInPeaceZone)
				{
					ITEMS: for (Integer itemId : player.getAutoUseSettings().getAutoSupplyItems())
					{
						if (player.isTeleporting())
						{
							break ITEMS;
						}
						
						final Item item = player.getInventory().getItemByItemId(itemId.intValue());
						if (item == null)
						{
							player.getAutoUseSettings().getAutoSupplyItems().remove(itemId);
							continue ITEMS;
						}
						
						final ItemTemplate template = item.getTemplate();
						if ((template == null) || !template.checkCondition(player, player, false))
						{
							continue ITEMS;
						}
						
						final SkillHolder[] skills = item.getTemplate().getSkills();
						if (skills != null)
						{
							ITEM_SKILL: for (SkillHolder itemSkillHolder : skills)
							{
								final Skill skill = itemSkillHolder.getSkill();
								if (!skill.isActive())
								{
									continue ITEM_SKILL;
								}
								
								if (player.isAffectedBySkill(skill.getId()) || player.hasSkillReuse(skill.getReuseHashCode()) || !skill.checkCondition(player, player, false))
								{
									continue ITEMS;
								}
							}
						}
						
						final int reuseDelay = item.getReuseDelay();
						if ((reuseDelay <= 0) || (player.getItemRemainingReuseTime(item.getObjectId()) <= 0))
						{
							final IItemHandler handler = ItemHandler.getInstance().getHandler(item.getEtcItem());
							if ((handler != null) && handler.onItemUse(player, item, false) && (reuseDelay > 0))
							{
								player.addTimeStampItem(item, reuseDelay);
							}
						}
					}
				}
				
				if (AutoPlayConfig.ENABLE_AUTO_POTION && !isInPeaceZone && (player.getCurrentHpPercent() < player.getAutoPlaySettings().getAutoPotionPercent()))
				{
					final int itemId = player.getAutoUseSettings().getAutoPotionItem();
					if (itemId > 0)
					{
						final Item item = player.getInventory().getItemByItemId(itemId);
						if (item == null)
						{
							player.getAutoUseSettings().setAutoPotionItem(0);
						}
						else
						{
							final int reuseDelay = item.getReuseDelay();
							if ((reuseDelay <= 0) || (player.getItemRemainingReuseTime(item.getObjectId()) <= 0))
							{
								final EtcItem etcItem = item.getEtcItem();
								final IItemHandler handler = ItemHandler.getInstance().getHandler(etcItem);
								if ((handler != null) && handler.onItemUse(player, item, false) && (reuseDelay > 0))
								{
									player.addTimeStampItem(item, reuseDelay);
								}
							}
						}
					}
				}
				
				if (AutoPlayConfig.ENABLE_AUTO_SKILL)
				{
					BUFFS: for (Integer skillId : player.getAutoUseSettings().getAutoBuffs())
					{
						// Fixes start area issue.
						if (isInPeaceZone)
						{
							break BUFFS;
						}
						
						// Already casting.
						if (player.isCastingNow())
						{
							break BUFFS;
						}
						
						// Attacking.
						if (player.isAttackingNow())
						{
							break BUFFS;
						}
						
						// Player is teleporting.
						if (player.isTeleporting())
						{
							break BUFFS;
						}
						
						Playable pet = null;
						Skill skill = player.getKnownSkill(skillId);
						if (skill == null)
						{
							if (player.hasServitor() || player.hasPet())
							{
								final Summon summon = player.getSummon();
								skill = summon.getKnownSkill(skillId);
								if (skill == null)
								{
									skill = PetSkillData.getInstance().getKnownSkill(summon, skillId);
								}
								
								if (skill != null)
								{
									pet = summon;
								}
							}
							
							if (skill == null)
							{
								player.getAutoUseSettings().getAutoBuffs().remove(skillId);
								continue BUFFS;
							}
						}
						
						// Buff use check.
						final WorldObject target = player.getTarget();
						if (!canCastBuff(player, target, skill))
						{
							continue BUFFS;
						}
						
						// Playable target cast.
						final Playable caster = pet != null ? pet : player;
						if ((target != null) && (target.isPlayable()))
						{
							final Player targetPlayer = target.asPlayer();
							if (((targetPlayer.getPvpFlag() == 0) && (targetPlayer.getKarma() <= 0)) || (targetPlayer.getParty() == caster.getParty()))
							{
								caster.doCast(skill);
							}
							else
							{
								if (!caster.getEffectList().isAffectedBySkill(skill.getId()))
								{
									final WorldObject savedTarget = target;
									caster.setTarget(caster);
									caster.doCast(skill);
									caster.setTarget(savedTarget);
								}
							}
						}
						else // Target self, cast and re-target.
						{
							final WorldObject savedTarget = target;
							caster.setTarget(caster);
							caster.doCast(skill);
							caster.setTarget(savedTarget);
						}
					}
					
					// Continue when auto play is not enabled.
					if (!player.isAutoPlaying())
					{
						continue;
					}
					
					final int count = player.getAutoUseSettings().getAutoSkills().size();
					if (phantomPolicy != null)
					{
						final var directive = phantomPolicy.targetDirective(player);
						if (directive.mode() == AutoPlayTaskManager.TargetDirective.Mode.HOLD) { continue; }
						if (directive.mode() == AutoPlayTaskManager.TargetDirective.Mode.USE_EXACT_TARGET)
						{
							if (directive.target() == null || directive.target().isAlikeDead() || !phantomPolicy.permitsTarget(directive.target())) { continue; }
							player.setTarget(directive.target());
						}
						if (!archivedSolo && count > 0 && phantomPolicy.usesScopedOffense(player) && player.getTarget() instanceof Monster target
							&& useScopedOffense(player, target, phantomPolicy)) { continue; }
					}
					final var settings = player.getAutoUseSettings();
					if (archivedSolo && count > TacticalObservationV3.STOCK_SOLO_SKILL_LIMIT) { continue; }
					final var archivedOrder = archivedSolo ? List.copyOf(settings.getAutoSkills()) : List.<Integer>of();
					if (archivedSolo && archivedOrder.size() != count) { continue; }
					final int cursor = archivedSolo && settings.getSkillOrder() < count ? settings.getSkillOrder() : 0;
					SKILLS: for (int i = 0; i < count; i++)
					{
						// Already casting.
						if (player.isCastingNow())
						{
							break SKILLS;
						}
						
						// Player is teleporting.
						if (player.isTeleporting())
						{
							break SKILLS;
						}
						
						// Acquire next skill.
						Playable pet = null;
						final WorldObject target = player.getTarget();
						final Integer skillId = player.getAutoUseSettings().getNextSkillId();
						if (archivedSolo && !archivedOrder.get((cursor + i) % count).equals(skillId)) { break SKILLS; }
						Skill skill = player.getKnownSkill(skillId);
						if (skill == null)
						{
							if (player.hasServitor() || player.hasPet())
							{
								final Summon summon = player.getSummon();
								skill = summon.getKnownSkill(skillId);
								if (skill == null)
								{
									skill = PetSkillData.getInstance().getKnownSkill(summon, skillId);
								}
								
								if (skill != null)
								{
									pet = summon;
									pet.setTarget(target);
								}
							}
							
							if (skill == null)
							{
								player.getAutoUseSettings().getAutoSkills().remove(skillId);
								player.getAutoUseSettings().resetSkillOrder();
								break SKILLS;
							}
						}
						
						// Casting on self stops movement.
						if (target == player)
						{
							break SKILLS;
						}
						
						// Check negative effect skill target.
						if ((target == null) || target.asCreature().isDead())
						{
							// Remove queued skill.
							if (player.getQueuedSkill() != null)
							{
								player.setQueuedSkill(null, false, false);
							}
							break SKILLS;
						}
						
						// Peace zone and auto attackable checks.
						if (target.isInsideZone(ZoneId.PEACE) || !target.isAutoAttackable(player))
						{
							break SKILLS;
						}
						
						// Do not attack guards.
						if (target instanceof Guard)
						{
							final int targetMode = player.getAutoPlaySettings().getNextTargetMode();
							if ((targetMode != 3 /* NPC */) && (targetMode != 0 /* Any Target */))
							{
								break SKILLS;
							}
						}
						
						// Increment skill order.
						player.getAutoUseSettings().incrementSkillOrder();
						
						// Skill use check.
						final Playable caster = pet != null ? pet : player;
						final boolean phantomOffense = phantomPolicy != null && (skill.isDamage() || skill.hasNegativeEffect() || skill.isDebuff());
						final Target stockLifetime = phantomOffense && target instanceof Monster stock ? stock.getNativeEvidenceTarget() : null;
						if (!canUseMagic(caster, target, skill))
						{
							continue SKILLS;
						}
						
						if (phantomOffense && !stockOffenseCurrent(player, caster, target, phantomPolicy, stockLifetime)) { break SKILLS; }
						if (archivedSolo && caster == player && skill.isDamage() && target instanceof Monster monster)
						{
							final Skill stock = skill;
							final var selection = new StockSoloSelection(cursor, archivedOrder, i, new SelectedSkill(stock.getId(), stock.getLevel()));
							final var choice = phantomPolicy.observeArchivedSolo(player, monster, stock, selection);
							submitArchivedSolo(choice, stock, () -> stockOffenseCurrent(player, caster, target, phantomPolicy, stockLifetime)
								&& settings.getAutoSkills().equals(archivedOrder) && player.getKnownSkill(stock.getId()) == stock
								&& canUseMagic(caster, target, stock), () ->
								{
									if (!player.isCastingNow() && !player.isCastingSimultaneouslyNow() && !player.isMoving()
										&& player.getQueuedSkill() == null && readyOffense(player, monster, stock))
									{
										caster.useMagic(stock, true, true);
									}
								});
						}
						else
						{
							caster.useMagic(skill, true, false);
						}
						
						break SKILLS;
					}
				}
				}
				}
				catch (RuntimeException failure)
				{
					if (phantomPolicy == null) { throw failure; }
					phantomPolicy.stopObserved("AutoUse:exception:" + failure.getClass().getName());
					stopPhantomAutoUse(player, phantomPolicy);
					AutoPlayTaskManager.getInstance().stopPhantomAutoPlay(player, phantomPolicy);
					java.util.logging.Logger.getLogger(AutoUseTaskManager.class.getName()).log(java.util.logging.Level.WARNING, "Phantom AutoUse actor failed: " + player.getObjectId(), failure);
				}
			}
		}
		
		private boolean canCastBuff(Player player, WorldObject target, Skill skill)
		{
			if ((target != null) && target.isCreature() && target.asCreature().isAlikeDead() && (skill.getTargetType() != TargetType.SELF) && (skill.getTargetType() != TargetType.CORPSE) && (skill.getTargetType() != TargetType.PC_BODY))
			{
				return false;
			}
			
			final Playable playableTarget = (target == null) || !target.isPlayable() || (skill.getTargetType() == TargetType.SELF) ? player : target.asPlayable();
			if ((player != playableTarget) && (player.calculateDistance3D(playableTarget) > skill.getCastRange()))
			{
				return false;
			}
			
			if (!canUseMagic(player, playableTarget, skill))
			{
				return false;
			}
			
			final BuffInfo buffInfo = playableTarget.getEffectList().getBuffInfoBySkillId(skill.getId());
			final BuffInfo abnormalBuffInfo = playableTarget.getEffectList().getBuffInfoByAbnormalType(skill.getAbnormalType());
			if (abnormalBuffInfo != null)
			{
				if (buffInfo != null)
				{
					return (abnormalBuffInfo.getSkill().getId() == buffInfo.getSkill().getId()) && ((buffInfo.getTime() <= REUSE_MARGIN_TIME) || (buffInfo.getSkill().getLevel() < skill.getLevel()));
				}
				
				return (abnormalBuffInfo.getSkill().getAbnormalLevel() < skill.getAbnormalLevel()) || abnormalBuffInfo.isAbnormalType(AbnormalType.NONE);
			}
			
			return buffInfo == null;
		}
		
		private boolean canUseMagic(Playable playable, WorldObject target, Skill skill)
		{
			if ((skill.getItemConsumeCount() > 0) && (playable.getInventory().getInventoryItemCount(skill.getItemConsumeId(), -1) < skill.getItemConsumeCount()))
			{
				return false;
			}
			
			if (playable.isPlayer() && (playable.asPlayer().getCharges() < skill.getChargeConsumeCount()))
			{
				return false;
			}
			
			final int mpConsume = skill.getMpInitialConsume() + skill.getMpConsume();
			if ((mpConsume > 0) && (playable.getCurrentMp() < mpConsume))
			{
				return false;
			}
			
			// Check if monster is spoiled to avoid Spoil (254) skill recast.
			if ((skill.getId() == 254) && (target != null) && target.isMonster() && target.asMonster().isSpoiled())
			{
				return false;
			}
			
			return !playable.isSkillDisabled(skill) && skill.checkCondition(playable, target, false);
		}

		private boolean useScopedOffense(Player player, Monster target, PhantomPolicy policy)
		{
			final var owner = player.getNativeWorkOwner();
			final var lifetime = target.getNativeEvidenceTarget();
			if (!currentOffense(player, target, policy, owner, lifetime)) { return true; }
			final var baseline = player.getKnownSkill(player.getAutoUseSettings().getNextSkillId());
			if (player.getSkills().size() > PhantomNativeCombatCapability.MAXIMUM_SKILLS) { return false; }
			final var ready = scopedOffenseCandidates(baseline, player.getAllSkills(), skill -> policy.permitsScopedOffenseSkill(player, skill), skill -> readyOffense(player, target, skill));
			if (ready.isEmpty()) { return false; }
			final var choice = policy.chooseScopedOffense(player, target, ready, baseline);
			// A revoked scoped tick cannot use the result or fall back into a different native scene.
			if (!currentOffense(player, target, policy, owner, lifetime)) { return true; }
			if (choice == null || ready.stream().noneMatch(skill -> skill == choice.skill())) { return false; }
			return submitScoped(choice, () -> currentOffense(player, target, policy, owner, lifetime) && policy.permitsScopedOffenseSkill(player, choice.skill())
				&& readyOffense(player, target, choice.skill()), () ->
				{
					if (player.useMagic(choice.skill(), true, true)) { player.getAutoUseSettings().incrementSkillOrder(); }
				}) || !currentOffense(player, target, policy, owner, lifetime);
		}

		private boolean stockOffenseCurrent(Player player, Playable caster, WorldObject target, PhantomPolicy policy, Target lifetime)
		{
			return PHANTOM_POLICIES.get(player) == policy && player.getTarget() == target && caster.getTarget() == target && World.getInstance().findObject(target.getObjectId()) == target
				&& target.isCreature() && !target.asCreature().isAlikeDead() && target.getInstanceId() == player.getInstanceId()
				&& (lifetime == null || lifetime.spawnGeneration() > 0 && lifetime.equals(((Monster) target).getNativeEvidenceTarget()))
				&& policy.permitsTarget(target.asCreature());
		}

		private boolean currentOffense(Player player, Monster target, PhantomPolicy policy, PlayerNativeWork.Owner owner,
			Target lifetime)
		{
			return owner != null && owner.player() == player && owner.isCurrent() && player.getNativeWorkOwner() == owner && !player.isInParty()
				&& !player.isTeleporting() && !player.isCastingNow() && !player.isCastingSimultaneouslyNow() && !player.isAlikeDead() && !player.isSitting()
				&& player.isOnline() && player.isAutoPlaying() && player.getTarget() == target && !target.isAlikeDead() && target.isTargetable() && !target.isInvul()
				&& !target.isRaid() && !target.isQuestMonster() && lifetime.spawnGeneration() > 0 && lifetime.equals(target.getNativeEvidenceTarget())
				&& target.getInstanceId() == player.getInstanceId() && World.getInstance().findObject(target.getObjectId()) == target
				&& !target.isInsideZone(ZoneId.PEACE) && target.isAutoAttackable(player) && policy.usesScopedOffense(player) && policy.permitsTarget(target);
		}

		private boolean readyOffense(Player player, Monster target, Skill skill)
		{
			if (skill == null || !skill.isDamage() || skill.getAffectRange() != 0 || skill.isChanneling() || skill.isSimultaneousCast() || skill.getFlyType() != null
				|| skill.getHpConsume() != 0 || skill.getItemConsumeCount() != 0 || skill.getChargeConsumeCount() != 0 || skill.getMaxSoulConsumeCount() != 0
				|| AutoPlayConfig.DISABLED_AUTO_SKILLS.contains(skill.getId())) { return false; }
			final var mode = skill.isMagic() ? PhantomCombatMode.RANGED_MAGIC : PhantomCombatMode.MELEE_PHYSICAL;
			if (!L2jCombatBackend.supportsPveSkill(player, skill, mode)) { return false; }
			final var learned = SkillTreeData.getInstance().getClassSkill(skill.getId(), skill.getLevel(), player.getPlayerClass());
			if (learned == null || learned.getGetLevel() > player.getLevel()) { return false; }
			final int range = player.getMagicalAttackRange(skill);
			final var geo = GeoEngine.getInstance();
			return range > 0 && skill.getCastRange() > 0 && player.calculateDistance3D(target) <= Math.min(range, skill.getCastRange())
				&& geo.hasGeo(player.getX(), player.getY()) && geo.hasGeo(target.getX(), target.getY())
				&& geo.canSeeTarget(player, target) && player.getStat().getMpConsume(skill) > 0 && L2jCombatBackend.nativePveSkillReady(player, target, skill)
				&& canUseMagic(player, target, skill);
		}
	}
	
	public void startPhantomAutoUse(Player player, PhantomPolicy policy)
	{
		startPhantomAutoUse(player, policy, null);
	}

	public synchronized boolean startPhantomAutoUse(Player player, PhantomPolicy policy, java.util.concurrent.atomic.AtomicBoolean sessionCurrent)
	{
		java.util.Objects.requireNonNull(policy, "policy");
		if ((sessionCurrent != null) && !sessionCurrent.get()) { return false; }
		player.markPhantomAutoPlayManaged();
		PHANTOM_POLICIES.put(player, policy);
		startAutoUseTask(player);
		return true;
	}

	public synchronized void startAutoUseTask(Player player)
	{
		for (Set<Player> pool : POOLS)
		{
			if (pool.contains(player))
			{
				return;
			}
		}
		
		for (Set<Player> pool : POOLS)
		{
			if (pool.size() < POOL_SIZE)
			{
				pool.add(player);
				return;
			}
		}
		
		final Set<Player> pool = ConcurrentHashMap.newKeySet(POOL_SIZE);
		pool.add(player);
		ThreadPool.schedulePriorityTaskAtFixedRate(new AutoUse(pool), TASK_DELAY, TASK_DELAY);
		POOLS.add(pool);
	}
	
	public synchronized void stopAutoUseTask(Player player)
	{
		PHANTOM_POLICIES.remove(player);
		player.getAutoUseSettings().resetSkillOrder();
		for (Set<Player> pool : POOLS)
		{
			if (pool.remove(player))
			{
				return;
			}
		}
	}

	public synchronized boolean stopPhantomAutoUse(Player player, PhantomPolicy expected)
	{
		if ((expected == null) || (PHANTOM_POLICIES.get(player) != expected)) { return false; }
		expected.stopObserved("AutoUse:exact_stop");
		stopAutoUseTask(player);
		return true;
	}

	public boolean hasPhantomRegistration(Player player, PhantomPolicy expected)
	{
		return (expected != null) && (PHANTOM_POLICIES.get(player) == expected) && POOLS.stream().anyMatch(pool -> pool.contains(player));
	}
	
	public static AutoUseTaskManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final AutoUseTaskManager INSTANCE = new AutoUseTaskManager();
	}
}

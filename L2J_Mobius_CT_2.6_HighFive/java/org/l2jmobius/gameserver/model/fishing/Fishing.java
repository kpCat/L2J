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
package org.l2jmobius.gameserver.model.fishing;

import java.util.concurrent.Future;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.commons.util.Rnd;
import org.l2jmobius.gameserver.data.xml.FishingMonstersData;
import org.l2jmobius.gameserver.data.xml.FishingRodsData;
import org.l2jmobius.gameserver.managers.FishingChampionshipManager;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.WeaponType;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillUseHolder;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.network.SystemMessageId;
import org.l2jmobius.gameserver.network.serverpackets.ExFishingHpRegen;
import org.l2jmobius.gameserver.network.serverpackets.ExFishingStartCombat;
import org.l2jmobius.gameserver.network.serverpackets.PlaySound;
import org.l2jmobius.gameserver.network.serverpackets.SystemMessage;
import org.l2jmobius.gameserver.qol.PersonalCharacterQoLService;
import org.l2jmobius.gameserver.taskmanagers.GameTimeTaskManager;

public class Fishing implements Runnable
{
	private Player _fisher;
	private int _time;
	private int _stop = 0;
	private int _goodUse = 0;
	private int _anim = 0;
	private int _mode = 0;
	private int _deceptiveMode = 0;
	private Future<?> _fishAiTask;
	private boolean _thinking;
	private final int _assistInstanceId;
	private final Item _assistRod;
	private final Item _assistLure;
	private boolean _assistStopped;
	
	// Fish datas
	private final int _fishId;
	private final int _fishMaxHp;
	private int _fishCurHp;
	private final double _regenHp;
	private final boolean _isUpperGrade;
	private final int _lureId;
	
	@Override
	public synchronized void run()
	{
		if (_fisher == null)
		{
			return;
		}
		
		if (_fishCurHp >= (_fishMaxHp * 2))
		{
			// The fish got away
			_fisher.sendPacket(SystemMessageId.YOUR_BAIT_WAS_STOLEN_BY_THAT_FISH);
			doDie(false);
		}
		else if (_time <= 0)
		{
			// Time is up, so that fish got away
			_fisher.sendPacket(SystemMessageId.THAT_FISH_IS_MORE_DETERMINED_THAN_YOU_ARE_IT_SPIT_THE_HOOK);
			doDie(false);
		}
		else
		{
			aiTask();
			assistFishing();
		}
	}
	
	public Fishing(Player fisher, Fish fish, boolean isNoob, boolean isUpperGrade, int lureId)
	{
		_fisher = fisher;
		_assistInstanceId = fisher.getInstanceId();
		_assistRod = fisher.getActiveWeaponInstance();
		_assistLure = fisher.getLure();
		_assistStopped = !PersonalCharacterQoLService.getInstance().isFishingAssistEnabled(fisher);
		_fishMaxHp = fish.getFishHp();
		_fishCurHp = _fishMaxHp;
		_regenHp = fish.getHpRegen();
		_fishId = fish.getItemId();
		_time = fish.getCombatDuration();
		_isUpperGrade = isUpperGrade;
		_lureId = lureId;
		final int lureType;
		if (isUpperGrade)
		{
			_deceptiveMode = (Rnd.get(100) >= 90) ? 1 : 0;
			lureType = 2;
		}
		else
		{
			_deceptiveMode = 0;
			lureType = isNoob ? 0 : 1;
		}
		
		_mode = (Rnd.get(100) >= 80) ? 1 : 0;
		_fisher.broadcastPacket(new ExFishingStartCombat(_fisher, _time, _fishMaxHp, _mode, lureType, _deceptiveMode));
		_fisher.sendPacket(new PlaySound(1, "SF_S_01", 0, 0, 0, 0, 0));
		
		// Succeeded in getting a bite
		_fisher.sendPacket(SystemMessageId.YOU_VE_GOT_A_BITE);
		
		if (_fishAiTask == null)
		{
			_fishAiTask = ThreadPool.scheduleAtFixedRate(this, 1000, 1000);
		}
	}
	
	public synchronized void changeHp(int hp, int pen)
	{
		if (_fisher == null)
		{
			return;
		}

		_fishCurHp -= hp;
		if (_fishCurHp < 0)
		{
			_fishCurHp = 0;
		}
		
		_fisher.broadcastPacket(new ExFishingHpRegen(_fisher, _time, _fishCurHp, _mode, _goodUse, _anim, pen, _deceptiveMode));
		_anim = 0;
		if (_fishCurHp > (_fishMaxHp * 2))
		{
			_fishCurHp = _fishMaxHp * 2;
			doDie(false);
		}
		else if (_fishCurHp == 0)
		{
			doDie(true);
		}
	}
	
	public synchronized void doDie(boolean win)
	{
		_assistStopped = true;
		if (_fishAiTask != null)
		{
			_fishAiTask.cancel(false);
			_fishAiTask = null;
		}
		
		if (_fisher == null)
		{
			return;
		}
		
		if (win)
		{
			final FishingMonster fishingMonster = FishingMonstersData.getInstance().getFishingMonster(_fisher.getLevel());
			if (fishingMonster != null)
			{
				if (Rnd.get(100) <= fishingMonster.getProbability())
				{
					_fisher.sendPacket(SystemMessageId.YOU_CAUGHT_SOMETHING_SMELLY_AND_SCARY_MAYBE_YOU_SHOULD_THROW_IT_BACK);
					final Npc monster = Quest.addSpawn(fishingMonster.getFishingMonsterId(), _fisher);
					monster.setTarget(_fisher);
				}
				else
				{
					_fisher.sendPacket(SystemMessageId.YOU_CAUGHT_SOMETHING);
					_fisher.addItem(ItemProcessType.PICKUP, _fishId, 1, null, true);
					FishingChampionshipManager.getInstance().newFish(_fisher, _lureId);
				}
			}
		}
		
		_fisher.endFishing(win);
		_fisher = null;
	}
	
	protected void aiTask()
	{
		if (_thinking)
		{
			return;
		}
		
		_thinking = true;
		_time--;
		
		try
		{
			if (_mode == 1)
			{
				if (_deceptiveMode == 0)
				{
					_fishCurHp += (int) _regenHp;
				}
			}
			else if (_deceptiveMode == 1)
			{
				_fishCurHp += (int) _regenHp;
			}
			
			if (_stop == 0)
			{
				_stop = 1;
				int check = Rnd.get(100);
				if (check >= 70)
				{
					_mode = _mode == 0 ? 1 : 0;
				}
				
				if (_isUpperGrade)
				{
					check = Rnd.get(100);
					if (check >= 90)
					{
						_deceptiveMode = _deceptiveMode == 0 ? 1 : 0;
					}
				}
			}
			else
			{
				_stop--;
			}
		}
		finally
		{
			_thinking = false;
			final Player fisher = _fisher;
			if (fisher != null)
			{
				final ExFishingHpRegen efhr = new ExFishingHpRegen(fisher, _time, _fishCurHp, _mode, 0, _anim, 0, _deceptiveMode);
				if (_anim != 0)
				{
					fisher.broadcastPacket(efhr);
				}
				else
				{
					fisher.sendPacket(efhr);
				}
			}
		}
	}

	private void assistFishing()
	{
		if (_assistStopped)
		{
			return;
		}

		final Player fisher = _fisher;
		if ((fisher == null) || !PersonalCharacterQoLService.getInstance().isFishingAssistEnabled(fisher)
			|| !fisher.isOnline() || fisher.isAlikeDead() || fisher.isTransformed() || !fisher.isFishing()
			|| (fisher.getFishCombat() != this) || (fisher.getInstanceId() != _assistInstanceId)
			|| (_assistRod == null) || (fisher.getActiveWeaponInstance() != _assistRod)
			|| (fisher.getActiveWeaponItem() == null) || (fisher.getActiveWeaponItem().getItemType() != WeaponType.FISHINGROD)
			|| (FishingRodsData.getInstance().getFishingRod(_assistRod.getId()) == null)
			|| (_assistLure == null) || (fisher.getLure() != _assistLure) || (_assistLure.getId() != _lureId))
		{
			// Утрата контекста прекращает только помощь до следующего ручного заброса.
			_assistStopped = true;
			return;
		}

		if (fisher.isCastingNow() || fisher.isCastingSimultaneouslyNow() || (fisher.getQueuedSkill() != null) || fisher.isAllSkillsDisabled() || (fisher.getBowAttackEndTime() > GameTimeTaskManager.getInstance().getGameTicks()))
		{
			return;
		}

		final Skill skill = fisher.getKnownSkill(_mode == _deceptiveMode ? 1313 : 1314);
		if ((skill == null) || fisher.isSkillDisabled(skill))
		{
			return;
		}

		// Ненулевой cast time или иной target допускает отложенное действие вне этого боя.
		if (!skill.isStatic() || (skill.getTargetType() != TargetType.SELF) || (skill.getHitTime() != 0) || (skill.getCoolTime() != 0) || !skill.hasEffectType(EffectType.FISHING))
		{
			_assistStopped = true;
			return;
		}

		// Штатные эффекты сохраняют MP, reuse, FishShots, resist и выдачу улова.
		if (!fisher.useMagic(skill, false, true))
		{
			final SkillUseHolder queued = fisher.getQueuedSkill();
			if ((queued != null) && (queued.getSkill() == skill) && !queued.isCtrlPressed() && queued.isShiftPressed())
			{
				fisher.setQueuedSkill(null, false, false);
			}
		}
	}
	
	public synchronized void useReeling(int dmg, int pen)
	{
		if (_fisher == null)
		{
			return;
		}

		_anim = 2;
		if (Rnd.get(100) > 90)
		{
			_fisher.sendPacket(SystemMessageId.THE_FISH_HAS_RESISTED_YOUR_ATTEMPT_TO_BRING_IT_IN);
			_goodUse = 0;
			changeHp(0, pen);
			return;
		}
		
		if (_mode == 1)
		{
			if (_deceptiveMode == 0)
			{
				// Reeling is successful, Damage: $s1
				SystemMessage sm = new SystemMessage(SystemMessageId.YOU_REEL_THAT_FISH_IN_CLOSER_AND_CAUSE_S1_DAMAGE);
				sm.addInt(dmg);
				_fisher.sendPacket(sm);
				if (pen > 0)
				{
					sm = new SystemMessage(SystemMessageId.YOUR_REELING_WAS_SUCCESSFUL_MASTERY_PENALTY_S1);
					sm.addInt(pen);
					_fisher.sendPacket(sm);
				}
				
				_goodUse = 1;
				changeHp(dmg, pen);
			}
			else
			{
				// Reeling failed, Damage: $s1
				final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FAILED_TO_REEL_THAT_FISH_IN_FURTHER_AND_IT_REGAINS_S1_HP);
				sm.addInt(dmg);
				_fisher.sendPacket(sm);
				_goodUse = 2;
				changeHp(-dmg, pen);
			}
		}
		else if (_deceptiveMode == 0)
		{
			// Reeling failed, Damage: $s1
			final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FAILED_TO_REEL_THAT_FISH_IN_FURTHER_AND_IT_REGAINS_S1_HP);
			sm.addInt(dmg);
			_fisher.sendPacket(sm);
			_goodUse = 2;
			changeHp(-dmg, pen);
		}
		else
		{
			// Reeling is successful, Damage: $s1
			SystemMessage sm = new SystemMessage(SystemMessageId.YOU_REEL_THAT_FISH_IN_CLOSER_AND_CAUSE_S1_DAMAGE);
			sm.addInt(dmg);
			_fisher.sendPacket(sm);
			if (pen > 0)
			{
				sm = new SystemMessage(SystemMessageId.YOUR_REELING_WAS_SUCCESSFUL_MASTERY_PENALTY_S1);
				sm.addInt(pen);
				_fisher.sendPacket(sm);
			}
			
			_goodUse = 1;
			changeHp(dmg, pen);
		}
	}
	
	public synchronized void usePumping(int dmg, int pen)
	{
		if (_fisher == null)
		{
			return;
		}

		_anim = 1;
		if (Rnd.get(100) > 90)
		{
			_fisher.sendPacket(SystemMessageId.THE_FISH_HAS_RESISTED_YOUR_ATTEMPT_TO_BRING_IT_IN);
			_goodUse = 0;
			changeHp(0, pen);
			return;
		}
		
		if (_mode == 0)
		{
			if (_deceptiveMode == 0)
			{
				// Pumping is successful. Damage: $s1
				SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PUMPING_IS_SUCCESSFUL_CAUSING_S1_DAMAGE);
				sm.addInt(dmg);
				_fisher.sendPacket(sm);
				if (pen > 0)
				{
					sm = new SystemMessage(SystemMessageId.YOUR_PUMPING_WAS_SUCCESSFUL_MASTERY_PENALTY_S1);
					sm.addInt(pen);
					_fisher.sendPacket(sm);
				}
				
				_goodUse = 1;
				changeHp(dmg, pen);
			}
			else
			{
				// Pumping failed, Regained: $s1
				final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FAILED_TO_DO_ANYTHING_WITH_THE_FISH_AND_IT_REGAINS_S1_HP);
				sm.addInt(dmg);
				_fisher.sendPacket(sm);
				_goodUse = 2;
				changeHp(-dmg, pen);
			}
		}
		else if (_deceptiveMode == 0)
		{
			// Pumping failed, Regained: $s1
			final SystemMessage sm = new SystemMessage(SystemMessageId.YOU_FAILED_TO_DO_ANYTHING_WITH_THE_FISH_AND_IT_REGAINS_S1_HP);
			sm.addInt(dmg);
			_fisher.sendPacket(sm);
			_goodUse = 2;
			changeHp(-dmg, pen);
		}
		else
		{
			// Pumping is successful. Damage: $s1
			SystemMessage sm = new SystemMessage(SystemMessageId.YOUR_PUMPING_IS_SUCCESSFUL_CAUSING_S1_DAMAGE);
			sm.addInt(dmg);
			_fisher.sendPacket(sm);
			if (pen > 0)
			{
				sm = new SystemMessage(SystemMessageId.YOUR_PUMPING_WAS_SUCCESSFUL_MASTERY_PENALTY_S1);
				sm.addInt(pen);
				_fisher.sendPacket(sm);
			}
			
			_goodUse = 1;
			changeHp(dmg, pen);
		}
	}
}

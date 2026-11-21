/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package handlers.skill.effects;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.holders.creature.EffectList;
import org.l2jmobius.gameserver.model.conditions.Condition;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.skill.AbnormalType;
import org.l2jmobius.gameserver.model.skill.BuffInfo;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.enums.SkillFinishType;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.combat.PhantomAreaControl131;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.DebuffRemoval;

/**
 * Dispel By Slot effect implementation.
 * @author Gnacik, Zoey76, Adry_85
 */
public class DispelBySlot extends AbstractEffect implements DebuffRemoval
{
	private final String _dispel;
	private final Map<AbnormalType, Short> _dispelAbnormals;
	
	public DispelBySlot(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
		
		_dispel = params.getString("dispel", null);
		if ((_dispel != null) && !_dispel.isEmpty())
		{
			_dispelAbnormals = new EnumMap<>(AbnormalType.class);
			for (String ngtStack : _dispel.split(";"))
			{
				final String[] ngt = ngtStack.split(",");
				_dispelAbnormals.put(AbnormalType.getAbnormalType(ngt[0]), Short.parseShort(ngt[1]));
			}
		}
		else
		{
			_dispelAbnormals = Collections.<AbnormalType, Short> emptyMap();
		}
	}
	
	@Override
	public EffectType getEffectType()
	{
		return EffectType.DISPEL_BY_SLOT;
	}
	
	@Override
	public boolean isInstant()
	{
		return true;
	}

	@Override
	public boolean supportsDebuffRemoval()
	{
		return !_dispelAbnormals.isEmpty() && !_dispelAbnormals.containsKey(AbnormalType.TRANSFORM);
	}

	@Override
	public java.util.List<BuffInfo> possibleRemovals(Player recipient)
	{
		final java.util.List<BuffInfo> candidates = new java.util.ArrayList<>();
		if (!supportsDebuffRemoval())
		{
			return candidates;
		}
		for (Entry<AbnormalType, Short> entry : _dispelAbnormals.entrySet())
		{
			final BuffInfo current = recipient.getEffectList().getBuffInfoByAbnormalType(entry.getKey());
			if (current != null && entry.getKey() == current.getSkill().getAbnormalType()
				&& (entry.getValue() < 0 || entry.getValue() >= current.getSkill().getAbnormalLevel()))
			{
				candidates.add(current);
			}
		}
		return candidates;
	}
	
	@Override
	public void onStart(Creature effector, Creature effected, Skill skill)
	{
		if (_dispelAbnormals.isEmpty() || (effected == null) || effected.isRaid())
		{
			return;
		}
		if (PhantomAreaControl131.scopedSupportEffect(effector, effected, skill))
		{
			if (effected instanceof Player)
			{
				possibleRemovals((Player) effected).forEach(effected.getEffectList()::removeDispelledDebuff);
			}
			return;
		}
		
		final EffectList effectList = effected.getEffectList();
		
		// There is no need to iterate over all buffs,
		// Just iterate once over all slots to dispel and get the buff with that abnormal if exists,
		// Operation of O(n) for the amount of slots to dispel (which is usually small) and O(1) to get the buff.
		for (Entry<AbnormalType, Short> entry : _dispelAbnormals.entrySet())
		{
			// Dispel transformations (buff and by GM)
			if ((entry.getKey() == AbnormalType.TRANSFORM) && (effected.isTransformed() || (effected.isPlayer() || (entry.getValue() == effected.asPlayer().getTransformationId()) || (entry.getValue() < 0))))
			{
				effected.stopTransformation(true);
				continue;
			}
			
			final BuffInfo toDispel = effectList.getBuffInfoByAbnormalType(entry.getKey());
			if (toDispel == null)
			{
				continue;
			}
			
			if ((entry.getKey() == toDispel.getSkill().getAbnormalType()) && ((entry.getValue() < 0) || (entry.getValue() >= toDispel.getSkill().getAbnormalLevel())))
			{
				effectList.stopSkillEffects(SkillFinishType.REMOVED, entry.getKey());
			}
		}
	}
}

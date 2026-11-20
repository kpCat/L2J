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

import java.util.List;

import org.l2jmobius.gameserver.config.custom.ServitorShareConfig;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.conditions.Condition;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.stats.functions.AbstractFunction;
import org.l2jmobius.gameserver.model.stats.functions.FuncMul;

public class SpiritSharing extends Buff
{
	public SpiritSharing(Condition attachCond, Condition applyCond, StatSet set, StatSet params)
	{
		super(attachCond, applyCond, set, params);
	}

	@Override
	public List<AbstractFunction> getStatFuncs(Creature caster, Creature target, Skill skill)
	{
		final List<AbstractFunction> functions = super.getStatFuncs(caster, target, skill);
		final ServitorShareConfig.SpiritSettings settings = ServitorShareConfig.spiritSharingSettings();
		if (settings.enabled() && (skill.getId() == 1547))
		{
			for (int i = 0; i < functions.size(); i++)
			{
				final AbstractFunction function = functions.get(i);
				if (function instanceof FuncMul)
				{
					final double coefficient = settings.coefficient(skill.getId(), function.getStat(), function.getValue());
					if (coefficient != function.getValue())
					{
						functions.set(i, new FuncMul(function.getStat(), function.getOrder(), function.getFuncOwner(), coefficient, function.getApplyCond()));
					}
				}
			}
		}
		return functions;
	}
}

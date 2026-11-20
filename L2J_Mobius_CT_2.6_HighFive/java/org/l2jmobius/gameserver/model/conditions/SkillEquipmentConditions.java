/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.model.conditions;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.config.custom.SkillEquipmentFreedomConfig;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.skill.Skill;

/** The equipment-independent form of a skill condition. Original trees remain available to item selection. */
public final class SkillEquipmentConditions
{
	private static final Condition TRUE = new ConstantCondition(true);
	private static final Condition FALSE = new ConstantCondition(false);

	private SkillEquipmentConditions()
	{
	}

	public static boolean appliesTo(Creature caster, Skill skill)
	{
		return SkillEquipmentFreedomConfig.isEnabled() && (caster instanceof Player) && (skill != null) && (skill.getReferenceItemId() == 0) && (caster.getKnownSkill(skill.getId()) == skill);
	}

	public static Condition relax(Condition condition)
	{
		if ((condition instanceof ConditionUsingItemType using && using.isSkillEquipmentRestriction()) || (condition instanceof ConditionUsingSlotType))
		{
			return TRUE;
		}

		if (condition instanceof ConditionLogicAnd and)
		{
			return relaxGroup(and, and.conditions, false);
		}

		if (condition instanceof ConditionLogicOr or)
		{
			return relaxGroup(or, or.conditions, true);
		}

		if (condition instanceof ConditionLogicNot not)
		{
			final Condition child = relax(not.getCondition());
			if (child == TRUE)
			{
				return FALSE;
			}
			if (child == FALSE)
			{
				return TRUE;
			}
			if (child != not.getCondition())
			{
				return new ConditionLogicNot(child);
			}
		}

		return condition;
	}

	private static Condition relaxGroup(Condition original, Condition[] children, boolean any)
	{
		final List<Condition> remaining = new ArrayList<>(children.length);
		boolean changed = false;
		boolean hadEquipmentAlternative = false;
		for (Condition child : children)
		{
			final Condition relaxed = relax(child);
			changed |= relaxed != child;
			if (relaxed == TRUE)
			{
				hadEquipmentAlternative = true;
			}
			else if (relaxed == FALSE)
			{
				if (!any)
				{
					return FALSE;
				}
			}
			else
			{
				remaining.add(relaxed);
			}
		}

		if (!changed)
		{
			return original;
		}
		if (remaining.isEmpty())
		{
			return (!any || hadEquipmentAlternative) ? TRUE : FALSE;
		}
		if (remaining.size() == 1)
		{
			return remaining.getFirst();
		}

		if (any)
		{
			final ConditionLogicOr result = new ConditionLogicOr();
			remaining.forEach(result::add);
			return result;
		}

		final ConditionLogicAnd result = new ConditionLogicAnd();
		remaining.forEach(result::add);
		return result;
	}

	private static final class ConstantCondition extends Condition
	{
		private final boolean _result;

		private ConstantCondition(boolean result)
		{
			_result = result;
		}

		@Override
		public boolean testImpl(Creature caster, Creature target, Skill skill, ItemTemplate item)
		{
			return _result;
		}
	}
}

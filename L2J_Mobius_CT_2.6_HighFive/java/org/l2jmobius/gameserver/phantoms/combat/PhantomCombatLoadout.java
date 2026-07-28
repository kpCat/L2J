/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.List;
import java.util.Objects;

public record PhantomCombatLoadout(PhantomCombatMode mode, String capabilityKey, int capabilityRank, List<SelectedSkill> selectedSkills, boolean normalAttackFallback)
{
	public PhantomCombatLoadout
	{
		Objects.requireNonNull(mode, "mode");
		if (!mode.capabilityKey().equals(capabilityKey) || (capabilityRank < 1) || (selectedSkills == null) || (selectedSkills.size() > 4) || (mode == PhantomCombatMode.RANGED_MAGIC && normalAttackFallback))
		{
			throw new IllegalArgumentException("Invalid combat loadout.");
		}
		selectedSkills = List.copyOf(selectedSkills);
	}

	public record SelectedSkill(int skillId, int skillLevel)
	{
		public SelectedSkill
		{
			if ((skillId <= 0) || (skillLevel <= 0))
			{
				throw new IllegalArgumentException("Invalid selected skill.");
			}
		}
	}
}

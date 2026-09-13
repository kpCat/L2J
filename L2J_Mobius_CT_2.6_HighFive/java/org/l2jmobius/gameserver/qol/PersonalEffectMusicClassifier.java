/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.qol.PersonalEffectDurationPolicy.Category;

/**
 * Immutable H5 song/dance classification derived from canonical class-tree ownership.
 */
public final class PersonalEffectMusicClassifier
{
	private static final Logger LOGGER = Logger.getLogger(PersonalEffectMusicClassifier.class.getName());
	private volatile Snapshot _snapshot;
	private boolean _conflictWarningLogged;

	private PersonalEffectMusicClassifier()
	{
	}

	public synchronized void refresh(SkillTreeData skillTrees)
	{
		final Set<Integer> songs = new HashSet<>();
		final Set<Integer> dances = new HashSet<>();
		collectMusic(skillTrees, PlayerClass.SWORDSINGER, songs);
		collectMusic(skillTrees, PlayerClass.SWORD_MUSE, songs);
		collectMusic(skillTrees, PlayerClass.BLADEDANCER, dances);
		collectMusic(skillTrees, PlayerClass.SPECTRAL_DANCER, dances);

		final Set<Integer> conflicts = new HashSet<>(songs);
		conflicts.retainAll(dances);
		final Map<Integer, Category> categories = new HashMap<>();
		for (int skillId : songs)
		{
			if (!conflicts.contains(skillId))
			{
				categories.put(skillId, Category.SONG);
			}
		}
		for (int skillId : dances)
		{
			if (!conflicts.contains(skillId))
			{
				categories.put(skillId, Category.DANCE);
			}
		}
		_snapshot = new Snapshot(Map.copyOf(categories), conflicts.size());
		if (!conflicts.isEmpty() && !_conflictWarningLogged)
		{
			_conflictWarningLogged = true;
			LOGGER.warning("Personal effect duration music classification found " + conflicts.size() + " conflicting skill identifiers; those skills remain stock.");
		}
	}

	public void invalidate()
	{
		_snapshot = null;
	}

	public Category classify(Skill skill)
	{
		if ((skill == null) || !skill.isDance())
		{
			return Category.BUFF;
		}
		Snapshot snapshot = _snapshot;
		if (snapshot == null)
		{
			refresh(SkillTreeData.getInstance());
			snapshot = _snapshot;
		}
		return snapshot.categories().getOrDefault(skill.getId(), Category.UNKNOWN);
	}

	int conflictCount()
	{
		Snapshot snapshot = _snapshot;
		if (snapshot == null)
		{
			refresh(SkillTreeData.getInstance());
			snapshot = _snapshot;
		}
		return snapshot.conflictCount();
	}

	private static void collectMusic(SkillTreeData skillTrees, PlayerClass playerClass, Set<Integer> result)
	{
		for (SkillLearn skillLearn : skillTrees.getCompleteClassSkillTree(playerClass).values())
		{
			final Skill skill = SkillData.getInstance().getSkill(skillLearn.getSkillId(), skillLearn.getSkillLevel());
			if ((skill != null) && skill.isDance())
			{
				result.add(skill.getId());
			}
		}
	}

	private record Snapshot(Map<Integer, Category> categories, int conflictCount)
	{
	}

	public static PersonalEffectMusicClassifier getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		private static final PersonalEffectMusicClassifier INSTANCE = new PersonalEffectMusicClassifier();
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;

/** Exact learned-skill evidence for the existing spoil and sweep capability rules. */
public final class PhantomOrdinarySpoilEvidence
{
	private PhantomOrdinarySpoilEvidence()
	{
	}

	public static List<Integer> candidateSkillIds(List<CapabilityRule> classRules)
	{
		final List<CapabilityRule> rules = eligibleRules(classRules);
		if (!hasBoth(rules))
		{
			return List.of();
		}
		final List<Integer> ids = rules.stream().flatMap(rule -> rule.evidenceSkills().stream()).map(skill -> skill.skillId()).distinct().sorted().toList();
		return ids.size() <= 8 ? ids : List.of();
	}

	public static boolean eligible(List<CapabilityRule> classRules, Map<Integer, Integer> learnedSkills)
	{
		final List<CapabilityRule> rules = eligibleRules(classRules);
		if (!hasBoth(rules) || candidateSkillIds(classRules).isEmpty())
		{
			return false;
		}
		return hasLearnedRule(rules, "profession.spoil", learnedSkills) && hasLearnedRule(rules, "profession.sweep", learnedSkills);
	}

	private static List<CapabilityRule> eligibleRules(List<CapabilityRule> classRules)
	{
		return classRules.stream().filter(rule -> ("profession.spoil".equals(rule.capabilityKey()) || "profession.sweep".equals(rule.capabilityKey())) && rule.requiredItems().isEmpty() && rule.requiredEquipmentFamilies().isEmpty()).toList();
	}

	private static boolean hasBoth(List<CapabilityRule> rules)
	{
		return rules.stream().anyMatch(rule -> "profession.spoil".equals(rule.capabilityKey())) && rules.stream().anyMatch(rule -> "profession.sweep".equals(rule.capabilityKey()));
	}

	private static boolean hasLearnedRule(List<CapabilityRule> rules, String key, Map<Integer, Integer> learnedSkills)
	{
		return rules.stream().filter(rule -> key.equals(rule.capabilityKey())).anyMatch(rule -> (learnedSkills.getOrDefault(rule.actionSkill().skillId(), 0) >= rule.actionSkill().skillLevel()) && rule.evidenceSkills().stream().allMatch(skill -> learnedSkills.getOrDefault(skill.skillId(), 0) >= skill.skillLevel()));
	}
}

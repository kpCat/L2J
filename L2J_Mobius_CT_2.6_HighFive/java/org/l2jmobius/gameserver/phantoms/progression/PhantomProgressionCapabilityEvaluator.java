/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionBackend.ActorLease;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorProgressionSnapshot;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityEvaluation;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ReadinessReason;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.RequiredItem;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillReadinessProbe;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;

/**
 * Evaluates separate catalog, learned and momentary readiness truths.
 */
public final class PhantomProgressionCapabilityEvaluator
{
	public List<CapabilityEvaluation> evaluate(PhantomProgressionCatalog catalog, ActorProgressionSnapshot actor, ActorLease lease, Integer targetObjectId)
	{
		final List<CapabilityEvaluation> result = new ArrayList<>();
		for (CapabilityRule rule : catalog.capabilities(actor.activeClassId()))
		{
			final boolean intrinsic = true;
			final SkillRef learnedEvidence = rule.evidenceSkills().stream().filter(actor::knows).findFirst().orElse(null);
			final boolean learned = learnedEvidence != null;
			final ReadinessReason reason = readiness(catalog, actor, lease, rule, learnedEvidence, targetObjectId);
			result.add(new CapabilityEvaluation(rule.capabilityKey(), rule.rank(), rule.targetScope(), intrinsic, learned, reason == ReadinessReason.READY, reason, rule.evidenceSkills()));
		}
		result.sort(Comparator.comparing(CapabilityEvaluation::stableKey));
		return List.copyOf(result);
	}

	private static ReadinessReason readiness(PhantomProgressionCatalog catalog, ActorProgressionSnapshot actor, ActorLease lease, CapabilityRule rule, SkillRef learnedEvidence, Integer targetObjectId)
	{
		if (actor.dead())
		{
			return ReadinessReason.DEAD;
		}
		if (actor.transformed())
		{
			return ReadinessReason.TRANSFORMED;
		}
		if (actor.mounted())
		{
			return ReadinessReason.MOUNTED;
		}
		if (learnedEvidence == null)
		{
			return ReadinessReason.SKILL_NOT_LEARNED;
		}
		if (rule.targetRequired() && ((targetObjectId == null) || (targetObjectId <= 0)))
		{
			return ReadinessReason.TARGET_REQUIRED;
		}
		if (!hasRequiredEquipment(catalog, actor, rule.requiredEquipmentFamilies()))
		{
			return ReadinessReason.WEAPON_OR_EQUIPMENT_MISMATCH;
		}
		for (RequiredItem item : rule.requiredItems())
		{
			if (actor.resourceItemCounts().getOrDefault(item.itemId(), 0L) < item.count())
			{
				return ReadinessReason.REQUIRED_ITEM_MISSING;
			}
		}
		if (rule.servitorRequired() && actor.controlledActors().stream().noneMatch(fact -> fact.actorKind() == ActorKind.SERVITOR))
		{
			return ReadinessReason.SERVITOR_NOT_PRESENT;
		}
		if (rule.summonRequired() && actor.controlledActors().isEmpty())
		{
			return ReadinessReason.SUMMON_REQUIRED;
		}
		final SkillReadinessProbe probe = lease.canonicalSkillReadiness(learnedEvidence, targetObjectId);
		if (!probe.dynamicConditionSatisfied())
		{
			return ReadinessReason.DYNAMIC_CONDITION_FAILED;
		}
		if (!probe.sufficientMpAndHp())
		{
			return ReadinessReason.INSUFFICIENT_MP_OR_HP;
		}
		if (!probe.enabled())
		{
			return ReadinessReason.SKILL_DISABLED_OR_REUSE;
		}
		return ReadinessReason.READY;
	}

	private static boolean hasRequiredEquipment(PhantomProgressionCatalog catalog, ActorProgressionSnapshot actor, Set<String> families)
	{
		if (families.isEmpty())
		{
			return true;
		}
		for (var equipped : actor.equippedItems())
		{
			final EquipmentFact fact = catalog.equipment(equipped.itemId());
			if ((fact != null) && families.contains(fact.family()))
			{
				return true;
			}
		}
		return false;
	}
}

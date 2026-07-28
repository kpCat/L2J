/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;

public final class PhantomCombatCapabilityResolver
{
	@FunctionalInterface
	public interface CapabilitySource
	{
		List<CapabilityEvidence> capabilities(int classId);
	}

	public record CapabilityEvidence(String capabilityKey, int rank, List<SelectedSkill> skills)
	{
		public CapabilityEvidence
		{
			if ((capabilityKey == null) || capabilityKey.isBlank() || (rank < 1) || (skills == null))
			{
				throw new IllegalArgumentException("Invalid capability evidence.");
			}
			skills = List.copyOf(skills);
		}
	}

	private static final Comparator<CapabilityEvidence> CAPABILITY_ORDER = Comparator.comparingInt(CapabilityEvidence::rank).reversed().thenComparing(CapabilityEvidence::capabilityKey);
	private static final Comparator<SelectedSkill> SKILL_ORDER = Comparator.comparingInt(SelectedSkill::skillId).thenComparingInt(SelectedSkill::skillLevel);
	private final CapabilitySource _source;

	public PhantomCombatCapabilityResolver(CapabilitySource source)
	{
		_source = Objects.requireNonNull(source, "source");
	}

	public static PhantomCombatCapabilityResolver fromGameKnowledge(Supplier<PhantomGameKnowledgeQuery> querySupplier)
	{
		Objects.requireNonNull(querySupplier, "querySupplier");
		return new PhantomCombatCapabilityResolver(classId ->
		{
			final PhantomGameKnowledgeQuery query = querySupplier.get();
			if (query == null)
			{
				return List.of();
			}
			return query.classCapabilities(classId, PageRequest.first(256)).values().stream().map(PhantomCombatCapabilityResolver::copy).toList();
		});
	}

	public static PhantomCombatCapabilityResolver fromProgression(Supplier<PhantomProgressionCatalog> catalogSupplier)
	{
		Objects.requireNonNull(catalogSupplier, "catalogSupplier");
		return new PhantomCombatCapabilityResolver(classId ->
		{
			final PhantomProgressionCatalog catalog = catalogSupplier.get();
			if (catalog == null)
			{
				return List.of();
			}
			return catalog.capabilities(classId).stream().map(fact -> new CapabilityEvidence(fact.capabilityKey(), fact.rank(), fact.evidenceSkills().stream().map(skill -> new SelectedSkill(skill.skillId(), skill.skillLevel())).sorted(SKILL_ORDER).toList())).toList();
		});
	}

	private static CapabilityEvidence copy(ClassCapabilityFact fact)
	{
		return new CapabilityEvidence(fact.capabilityKey(), fact.rank(), fact.evidenceSkills().stream().map(skill -> new SelectedSkill(skill.skillId(), skill.skillLevel())).sorted(SKILL_ORDER).toList());
	}

	public Optional<PhantomCombatLoadout> resolve(ActorSnapshot actor, PhantomCombatMode mode, PhantomCombatActorLease lease, int maximumSkills)
	{
		Objects.requireNonNull(actor, "actor");
		Objects.requireNonNull(mode, "mode");
		Objects.requireNonNull(lease, "lease");
		if ((maximumSkills < 1) || (maximumSkills > 4))
		{
			throw new IllegalArgumentException("Invalid selected skill bound.");
		}

		final List<CapabilityEvidence> capabilities = new ArrayList<>(_source.capabilities(actor.classId()));
		capabilities.sort(CAPABILITY_ORDER);
		for (CapabilityEvidence capability : capabilities)
		{
			if (!mode.capabilityKey().equals(capability.capabilityKey()))
			{
				continue;
			}

			final List<SelectedSkill> selected = capability.skills().stream().sorted(SKILL_ORDER).filter(skill -> lease.supportsSkill(skill, mode)).limit(maximumSkills).toList();
			if ((mode == PhantomCombatMode.RANGED_MAGIC) && selected.isEmpty())
			{
				return Optional.empty();
			}
			return Optional.of(new PhantomCombatLoadout(mode, capability.capabilityKey(), capability.rank(), selected, mode != PhantomCombatMode.RANGED_MAGIC));
		}
		return Optional.empty();
	}
}

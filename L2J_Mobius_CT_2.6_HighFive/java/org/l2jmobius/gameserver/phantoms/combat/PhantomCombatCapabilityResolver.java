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

	public record CapabilityEvidence(String capabilityKey, String variantKey, int rank, List<SelectedSkill> skills)
	{
		public CapabilityEvidence
		{
			if ((capabilityKey == null) || capabilityKey.isBlank() || (variantKey == null) || variantKey.isBlank() || (rank < 1) || (skills == null))
			{
				throw new IllegalArgumentException("Invalid capability evidence.");
			}
			skills = List.copyOf(skills);
		}

		public CapabilityEvidence(String capabilityKey, int rank, List<SelectedSkill> skills)
		{
			this(capabilityKey, "legacy", rank, skills);
		}
	}

	private static final Comparator<CapabilityEvidence> CAPABILITY_ORDER = Comparator.comparing(CapabilityEvidence::capabilityKey).thenComparing(CapabilityEvidence::variantKey);
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
			return query.classCapabilities(classId, PageRequest.first(256)).values().stream().flatMap(fact -> copy(fact).stream()).toList();
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
			return catalog.capabilities(classId).stream().map(fact -> new CapabilityEvidence(fact.capabilityKey(), fact.variantKey(), fact.rank(), List.of(new SelectedSkill(fact.actionSkill().skillId(), fact.actionSkill().skillLevel())))).toList();
		});
	}

	private static List<CapabilityEvidence> copy(ClassCapabilityFact fact)
	{
		return fact.evidenceSkills().stream().map(skill -> new CapabilityEvidence(fact.capabilityKey(), "knowledge-s" + skill.skillId() + "-l" + skill.skillLevel(), fact.rank(), List.of(new SelectedSkill(skill.skillId(), skill.skillLevel())))).toList();
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
		final List<SelectedSkill> selected = capabilities.stream().filter(capability -> mode.capabilityKey().equals(capability.capabilityKey())).flatMap(capability -> capability.skills().stream()).sorted(SKILL_ORDER).distinct().filter(skill -> lease.supportsSkill(skill, mode)).limit(maximumSkills).toList();
		final List<CapabilityEvidence> matching = capabilities.stream().filter(capability -> mode.capabilityKey().equals(capability.capabilityKey())).toList();
		if (matching.isEmpty() || ((mode == PhantomCombatMode.RANGED_MAGIC) && selected.isEmpty()))
		{
			return Optional.empty();
		}
		final int rankMetadata = matching.stream().mapToInt(CapabilityEvidence::rank).max().orElseThrow();
		return Optional.of(new PhantomCombatLoadout(mode, mode.capabilityKey(), rankMetadata, selected, mode != PhantomCombatMode.RANGED_MAGIC));
	}
}

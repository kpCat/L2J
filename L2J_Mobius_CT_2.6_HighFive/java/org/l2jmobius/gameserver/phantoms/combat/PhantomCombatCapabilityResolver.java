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
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

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

	record SkillSelection(PhantomCombatLoadout loadout, List<SelectedSkill> readySkills)
	{
	}

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
		return resolve(actor.classId(), mode, lease::knownSkillLevel, skill -> lease.supportsSkill(skill, mode),
			lease.supportsNormalAttack(mode), maximumSkills);
	}

	/** The same catalog and learned-skill selection, usable by a read-only native planning owner. */
	public Optional<PhantomCombatLoadout> resolve(int classId, PhantomCombatMode mode, java.util.function.IntUnaryOperator learnedLevel,
		java.util.function.Predicate<SelectedSkill> nativeSkill, boolean normalFallback, int maximumSkills)
	{
		return resolveOrdinaryPve(classId, mode, learnedLevel, List::of, nativeSkill, normalFallback, maximumSkills);
	}

	public Optional<PhantomCombatLoadout> resolveOrdinaryPve(int classId, PhantomCombatMode mode, java.util.function.IntUnaryOperator learnedLevel,
		Supplier<List<SelectedSkill>> acquired, java.util.function.Predicate<SelectedSkill> nativeSkill, boolean normalFallback, int maximumSkills)
	{
		return resolveSkills(classId, mode, learnedLevel, acquired, nativeSkill, null, null, normalFallback, maximumSkills).map(SkillSelection::loadout);
	}

	Optional<PhantomCombatLoadout> resolveOrdinaryPve(ActorSnapshot actor, PhantomCombatMode mode, PhantomCombatActorLease lease, int maximumSkills)
	{
		return resolveOrdinaryPve(actor.classId(), mode, lease::knownSkillLevel, () -> lease.learnedPveSkills(mode),
			skill -> lease.supportsSkill(skill, mode), lease.supportsNormalAttack(mode), maximumSkills);
	}

	Optional<SkillSelection> resolvePveAction(ActorSnapshot actor, PhantomCombatMode mode, PhantomCombatActorLease lease, int targetObjectId, boolean canSpendMp, int maximumSkills)
	{
		return resolveSkills(actor.classId(), mode, lease::knownSkillLevel, () -> lease.learnedPveSkills(mode), skill -> lease.supportsSkill(skill, mode),
			canSpendMp ? skill -> lease.skillReady(targetObjectId, skill, mode) : null,
			skills -> lease.rankOffensiveSkills(targetObjectId, false, skills), lease.supportsNormalAttack(mode), maximumSkills);
	}

	private Optional<SkillSelection> resolveSkills(int classId, PhantomCombatMode mode, java.util.function.IntUnaryOperator learnedLevel,
		Supplier<List<SelectedSkill>> acquired, java.util.function.Predicate<SelectedSkill> nativeSkill, java.util.function.Predicate<SelectedSkill> readyForTarget,
		UnaryOperator<List<SelectedSkill>> rankReady, boolean normalFallback, int maximumSkills)
	{
		Objects.requireNonNull(mode, "mode");
		if ((maximumSkills < 1) || (maximumSkills > 4))
		{
			throw new IllegalArgumentException("Invalid selected skill bound.");
		}

		final List<CapabilityEvidence> capabilities = new ArrayList<>(_source.capabilities(classId));
		capabilities.sort(CAPABILITY_ORDER);
		final List<CapabilityEvidence> matching = capabilities.stream().filter(capability -> mode.capabilityKey().equals(capability.capabilityKey())).toList();
		final var authored = matching.stream().flatMap(capability -> capability.skills().stream())
			.map(skill -> currentLearnedSkill(skill, learnedLevel)).sorted(SKILL_ORDER);
		final var discovered = Stream.of(acquired).flatMap(source -> source.get().stream())
			.filter(skill -> learnedLevel.applyAsInt(skill.skillId()) == skill.skillLevel());
		final var candidates = Stream.concat(authored, discovered).distinct().filter(nativeSkill).iterator();
		final var supported = new ArrayList<SelectedSkill>(maximumSkills);
		final var ready = new ArrayList<SelectedSkill>(maximumSkills);
		while (candidates.hasNext())
		{
			final SelectedSkill candidate = candidates.next();
			if (supported.size() < maximumSkills)
			{
				supported.add(candidate);
			}
			if (readyForTarget != null && readyForTarget.test(candidate))
			{
				ready.add(candidate);
			}
			if (readyForTarget == null && supported.size() == maximumSkills)
			{
				break;
			}
		}
		final List<SelectedSkill> ranked = ready.isEmpty() ? List.of() : rankReady.apply(List.copyOf(ready)).stream().limit(maximumSkills).toList();
		final List<SelectedSkill> selected = ranked.isEmpty() ? supported : ranked;
		if (!normalFallback && selected.isEmpty())
		{
			return Optional.empty();
		}
		// Authored skill metadata ranks learned actions; it does not grant the native ordinary attack.
		final int rankMetadata = matching.stream().mapToInt(CapabilityEvidence::rank).max().orElse(1);
		return Optional.of(new SkillSelection(new PhantomCombatLoadout(mode, mode.capabilityKey(), rankMetadata, selected, normalFallback), ranked));
	}

	private static SelectedSkill currentLearnedSkill(SelectedSkill evidence, java.util.function.IntUnaryOperator learnedLevel)
	{
		final int currentLevel = learnedLevel.applyAsInt(evidence.skillId());
		// Catalog evidence is a minimum. Exact native supportsSkill still rejects missing/lower skills.
		return currentLevel >= evidence.skillLevel() ? new SelectedSkill(evidence.skillId(), currentLevel) : evidence;
	}

	public Optional<PhantomCombatLoadout> resolvePvp(ActorSnapshot actor, PhantomCombatMode mode, PhantomCombatActorLease lease, int maximumSkills)
	{
		return resolvePvpAction(actor, mode, lease, 0, 0, false, maximumSkills).map(SkillSelection::loadout);
	}

	Optional<SkillSelection> resolvePvpAction(ActorSnapshot actor, PhantomCombatMode mode, PhantomCombatActorLease lease,
		int opponentId, int tacticalTargetId, boolean canSpendMp, int maximumSkills)
	{
		return resolveSkills(actor.classId(), mode, lease::knownSkillLevel, () -> lease.learnedPvpSkills(mode, PhantomNativeCombatCapability.MAXIMUM_SKILLS),
			skill -> lease.supportsPvpSkill(skill, mode), canSpendMp ? skill -> lease.pvpSkillReady(opponentId, tacticalTargetId, skill, mode) : null,
			skills -> lease.rankOffensiveSkills(tacticalTargetId, true, skills), lease.supportsNormalAttack(mode), maximumSkills);
	}
}

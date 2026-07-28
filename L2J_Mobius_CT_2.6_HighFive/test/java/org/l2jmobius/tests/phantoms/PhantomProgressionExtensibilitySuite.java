/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionBackend.BackendData;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCapabilityEvaluator;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ActorProgressionSnapshot;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.Authority;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityEvaluation;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityRule;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ControlledActorBody;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ControlledActorFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OwnedEquipmentFilter;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.Page;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ReadinessReason;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.RequiredItem;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillLearningItemPlan;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillReadinessProbe;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SubclassEligibility;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SummonActorFact;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.TargetScope;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;

public final class PhantomProgressionExtensibilitySuite implements PhantomTestSuite
{
	private PhantomProgressionCatalog _variantCatalog;
	private CapabilityEvaluation _readyVariant;
	private CapabilityEvaluation _unlearnedVariant;
	private CapabilityEvaluation _resourceReadyVariant;
	private CapabilityEvaluation _resourceMissingVariant;
	private CapabilityEvaluation _chargeMissingVariant;
	private int _pagedEquipmentCount;
	private boolean _lowGradeReached;

	@Override
	public String id()
	{
		return "progression-extensibility";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		final BackendData data = variantData();
		_variantCatalog = new PhantomProgressionCatalogBuilder().build(data, PhantomProgressionPolicy.productionDefaults());
		final PhantomProgressionSyntheticBackend backend = new PhantomProgressionSyntheticBackend();
		final PhantomProgressionCapabilityEvaluator evaluator = new PhantomProgressionCapabilityEvaluator();
		backend.actor(actor(Map.of(1, 1), Map.of(57, 10L), 2));
		try (var lease = backend.lease())
		{
			final List<CapabilityEvaluation> values = evaluator.evaluate(_variantCatalog, lease.snapshot(_variantCatalog.combinedHash(), _variantCatalog.referencedResourceItemIds(), _variantCatalog.certificationSkillIds()), lease, null);
			_readyVariant = variant(values, "plain");
			_unlearnedVariant = variant(values, "resource");
		}
		backend.actor(actor(Map.of(1, 1, 6, 1), Map.of(57, 10L), 2));
		try (var lease = backend.lease())
		{
			_resourceReadyVariant = variant(evaluator.evaluate(_variantCatalog, lease.snapshot(_variantCatalog.combinedHash(), _variantCatalog.referencedResourceItemIds(), _variantCatalog.certificationSkillIds()), lease, null), "resource");
		}
		backend.actor(actor(Map.of(1, 1, 6, 1), Map.of(57, 9L), 2));
		try (var lease = backend.lease())
		{
			_resourceMissingVariant = variant(evaluator.evaluate(_variantCatalog, lease.snapshot(_variantCatalog.combinedHash(), _variantCatalog.referencedResourceItemIds(), _variantCatalog.certificationSkillIds()), lease, null), "resource");
		}
		backend.actor(actor(Map.of(1, 1, 6, 1), Map.of(57, 10L), 1));
		try (var lease = backend.lease())
		{
			_chargeMissingVariant = variant(evaluator.evaluate(_variantCatalog, lease.snapshot(_variantCatalog.combinedHash(), _variantCatalog.referencedResourceItemIds(), _variantCatalog.certificationSkillIds()), lease, null), "resource");
		}
		exerciseEquipmentPaging();
		context.record("progressionExtensibility.semanticCases", 15);
		context.record("progressionExtensibility.variantHash", _variantCatalog.hashes().capabilityRulesHash());
		context.record("progressionExtensibility.pagedEquipment", _pagedEquipmentCount);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-same-group-variants-survive", _ -> PhantomAssertions.assertEquals(2L, _variantCatalog.capabilities(0).stream().filter(rule -> rule.capabilityKey().equals("combat.variant")).count(), "Same-group variants collapsed."));
		registry.add("02-variant-stable-keys-differ", _ -> PhantomAssertions.assertFalse(_variantCatalog.capabilities(0).get(0).stableKey().equals(_variantCatalog.capabilities(0).get(1).stableKey()), "Variant identity did not affect stable key."));
		registry.add("03-exact-action-readiness-is-independent", _ -> assertReadiness());
		registry.add("04-skill-item-consumption-propagates", _ -> PhantomAssertions.assertTrue(_variantCatalog.referencedResourceItemIds().contains(57), "Skill item consumption is absent from actor snapshot resource IDs."));
		registry.add("05-curated-and-skill-item-merge-without-double-count", _ -> PhantomAssertions.assertTrue(_resourceReadyVariant.readyNow(), "Same item requirement was double-counted."));
		registry.add("06-exact-item-shortage-blocks", _ -> PhantomAssertions.assertEquals(ReadinessReason.REQUIRED_ITEM_MISSING, _resourceMissingVariant.reason(), "Exact skill item shortage was not observed."));
		registry.add("07-charge-shortage-blocks", _ -> PhantomAssertions.assertEquals(ReadinessReason.INSUFFICIENT_CHARGES_OR_SOULS, _chargeMissingVariant.reason(), "Exact charge requirement was ignored."));
		registry.add("08-unknown-resource-item-rejected", _ -> assertUnknownItemRejected());
		registry.add("09-duplicate-variant-triple-rejected", _ -> assertDuplicateVariantRejected());
		registry.add("10-cubic-has-no-body-contract", _ -> assertCubicContract());
		registry.add("11-controlled-body-has-no-fabricated-cp", _ -> assertNoControlledActorCp());
		registry.add("12-over-64-owned-items-are-pageable", _ -> PhantomAssertions.assertEquals(130, _pagedEquipmentCount, "Owned equipment paging lost or duplicated objects."));
		registry.add("13-low-grade-owned-item-remains-reachable", _ -> PhantomAssertions.assertTrue(_lowGradeReached, "Stable paging hid a lower-grade matching item."));
		registry.add("14-multi-item-learning-fails-before-effects", _ -> assertLearningItemPlan());
		registry.add("15-maximum-soul-consume-is-not-invented-minimum", _ -> assertSoulConsumptionFact());
	}

	private void assertReadiness()
	{
		PhantomAssertions.assertTrue(_readyVariant.readyNow(), "Learned exact action variant was not ready.");
		PhantomAssertions.assertEquals(ReadinessReason.SKILL_NOT_LEARNED, _unlearnedVariant.reason(), "Unlearned sibling variant borrowed another evidence skill.");
	}

	private void assertUnknownItemRejected()
	{
		final BackendData base = variantData();
		final CapabilityRule invalid = rule("invalid-resource", 6, List.of(new RequiredItem(999_999, 1)));
		final BackendData invalidData = new BackendData(base.classes(), base.skillLearns(), base.skills(), base.equipment(), base.summons(), base.pets(), List.of(invalid), base.knownItemIds());
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> new PhantomProgressionCatalogBuilder().build(invalidData, PhantomProgressionPolicy.productionDefaults()), "Unknown positive item reference was accepted.");
	}

	private void assertDuplicateVariantRejected()
	{
		final BackendData base = variantData();
		final CapabilityRule duplicate = rule("plain", 1, List.of());
		final BackendData duplicateData = new BackendData(base.classes(), base.skillLearns(), base.skills(), base.equipment(), base.summons(), base.pets(), List.of(duplicate, duplicate), base.knownItemIds());
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> new PhantomProgressionCatalogBuilder().build(duplicateData, PhantomProgressionPolicy.productionDefaults()), "Duplicate (classId, capabilityKey, variantKey) was accepted.");
	}

	private static void assertCubicContract()
	{
		new SummonActorFact(List.of(0), 4, 1, 1, ActorKind.CUBIC, 0, 0, 0, 0, 0, 0, 0, Set.of(), 0, 0, false, false, false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false, false, false, false, Authority.STATIC_DATAPACK_FACT, List.of("SummonCubic.java"));
		new ControlledActorFact(1, ActorKind.CUBIC, 0, null);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new SummonActorFact(List.of(0), 4, 1, 1, ActorKind.CUBIC, 0, 0, 0, 0, 0, 0, 0, Set.of(), 0, 0, false, false, false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true, false, false, false, Authority.STATIC_DATAPACK_FACT, List.of("SummonCubic.java")), "Cubic body command was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new ControlledActorFact(1000, ActorKind.SERVITOR, 0, null), "Body-capable controlled actor accepted an absent body.");
	}

	private static void assertNoControlledActorCp()
	{
		final Set<String> names = java.util.Arrays.stream(ControlledActorBody.class.getRecordComponents()).map(RecordComponent::getName).collect(java.util.stream.Collectors.toUnmodifiableSet());
		PhantomAssertions.assertFalse(names.contains("currentCp") || names.contains("maximumCp"), "Controlled actor body fabricated Player CP.");
	}

	private static void assertLearningItemPlan()
	{
		final SkillLearningItemPlan duplicate = SkillLearningItemPlan.from(List.of(new RequiredItem(57, 2), new RequiredItem(57, 3)));
		PhantomAssertions.assertEquals(List.of(new RequiredItem(57, 5)), duplicate.aggregatedItems(), "Duplicate required item IDs were not aggregated.");
		PhantomAssertions.assertTrue(duplicate.canonicalAtomicMutationSupported(), "One aggregated item was incorrectly blocked.");
		final SkillLearningItemPlan multi = SkillLearningItemPlan.from(List.of(new RequiredItem(57, 2), new RequiredItem(5575, 1)));
		final AtomicInteger sideEffects = new AtomicInteger();
		if (multi.canonicalAtomicMutationSupported())
		{
			sideEffects.incrementAndGet();
			throw new IllegalStateException("Injected second item mutation failure.");
		}
		PhantomAssertions.assertEquals(0, sideEffects.get(), "Unsupported multi-item mutation consumed a prefix.");
	}

	private void assertSoulConsumptionFact()
	{
		final SkillFact resourceSkill = _variantCatalog.skill(new SkillRef(6, 1));
		PhantomAssertions.assertEquals(3, resourceSkill.maximumSoulConsumeCount(), "Maximum soul consumption was not copied as an exact skill fact.");
		PhantomAssertions.assertTrue(_resourceReadyVariant.readyNow(), "A maximum soul consumption ceiling was incorrectly treated as an invented minimum.");
	}

	private void exerciseEquipmentPaging()
	{
		final PhantomProgressionService service = new PhantomProgressionService(new PagedEquipmentBackend(), PhantomProgressionPolicy.productionDefaults());
		service.start();
		final Set<Integer> seen = new HashSet<>();
		String cursor = null;
		do
		{
			final Page<OwnedEquipmentFact> page = service.equipmentCandidates(1, OwnedEquipmentFilter.all(), new PageRequest(cursor, 17));
			for (OwnedEquipmentFact fact : page.values())
			{
				PhantomAssertions.assertTrue(seen.add(fact.objectId()), "Owned equipment page repeated object identity.");
				_lowGradeReached |= (fact.objectId() == 1129) && fact.grade().equals("NONE");
			}
			cursor = page.nextCursor();
		}
		while (cursor != null);
		_pagedEquipmentCount = seen.size();
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Paged equipment service did not stop.");
		PhantomAssertions.assertEquals(0, service.snapshot().currentActorLeases(), "Paged equipment query leaked actor lease.");
	}

	private static BackendData variantData()
	{
		final BackendData base = PhantomProgressionSyntheticBackend.data();
		final ArrayList<SkillFact> skills = new ArrayList<>(base.skills());
		final SkillFact original = skills.stream().filter(skill -> skill.skillId() == 6).findFirst().orElseThrow();
		skills.remove(original);
		skills.add(new SkillFact(original.skillId(), original.skillLevel(), original.active(), original.passive(), original.toggle(), original.physical(), original.magic(), original.targetType(), original.damage(), original.negative(), original.heal(), original.resurrection(), original.buff(), original.debuff(), original.control(), 57, 10, 2, 3, original.mpConsume(), original.hpConsume(), original.reuseDelay(), original.conditionPresence(), original.blockedInOlympiad(), original.pvpOnly(), original.suicideAttack(), original.removedOnActionExceptMove(), original.transformation(), original.authority(), original.sourcePaths()));
		final List<CapabilityRule> variants = List.of(rule("plain", 1, List.of()), rule("resource", 6, List.of(new RequiredItem(57, 7))));
		return new BackendData(base.classes(), base.skillLearns(), skills, base.equipment(), base.summons(), base.pets(), variants, base.knownItemIds());
	}

	private static CapabilityRule rule(String variant, int skillId, List<RequiredItem> requiredItems)
	{
		final SkillRef skill = new SkillRef(skillId, 1);
		return new CapabilityRule("combat.variant", variant, 500, List.of(0), skill, List.of(skill), TargetScope.SELF, Set.of(), requiredItems, false, false, false, Authority.CURATED_CAPABILITY_RULE, List.of("variant.xml"));
	}

	private static ActorProgressionSnapshot actor(Map<Integer, Integer> learned, Map<Integer, Long> resources, int charges)
	{
		return new ActorProgressionSnapshot(1, 2, 0, 0, 0, 0, 1, 0, 100, false, false, false, List.of(), learned, List.of(), resources, charges, 0, List.of(), false, false, false, false, false, false, 3, Set.of(), "A".repeat(64));
	}

	private static CapabilityEvaluation variant(List<CapabilityEvaluation> values, String variantKey)
	{
		return values.stream().filter(value -> value.variantKey().equals(variantKey)).findFirst().orElseThrow();
	}

	private static final class PagedEquipmentBackend implements PhantomProgressionBackend
	{
		private final List<OwnedEquipmentFact> _items;

		private PagedEquipmentBackend()
		{
			final ArrayList<OwnedEquipmentFact> items = new ArrayList<>();
			for (int index = 0; index < 130; index++)
			{
				final boolean sword = (index & 1) == 0;
				items.add(new OwnedEquipmentFact(1000 + index, sword ? 100 : 101, "LR_HAND", sword ? "SWORD" : "BOW", index == 129 ? "NONE" : "S", index % 10, false, true, List.of()));
			}
			_items = List.copyOf(items);
		}

		@Override
		public BackendData load(PhantomProgressionPolicy policy)
		{
			return PhantomProgressionSyntheticBackend.data();
		}

		@Override
		public Optional<ActorLease> tryAcquireActor(long profileId)
		{
			return profileId == 1 ? Optional.of(new ActorLease()
			{
				@Override
				public ActorProgressionSnapshot snapshot(String catalogHash, Set<Integer> referencedResourceItemIds, Set<Integer> certificationSkillIds)
				{
					return actor(Map.of(), Map.of(), 0);
				}

				@Override
				public Page<OwnedEquipmentFact> ownedEquipment(OwnedEquipmentFilter filter, PageRequest page)
				{
					final List<OwnedEquipmentFact> matching = _items.stream().filter(fact -> ((filter.bodyPart() == null) || filter.bodyPart().equals(fact.bodyPart())) && ((filter.family() == null) || filter.family().equals(fact.family())) && ((filter.canonicalCompatibility() == null) || (filter.canonicalCompatibility() == fact.canonicalCompatibility())) && ((page.afterKey() == null) || (fact.stableKey().compareTo(page.afterKey()) > 0))).toList();
					final List<OwnedEquipmentFact> values = matching.stream().limit(page.limit()).toList();
					final boolean hasMore = matching.size() > values.size();
					return new Page<>(values, hasMore ? values.getLast().stableKey() : null, hasMore);
				}

				@Override
				public SkillReadinessProbe canonicalSkillReadiness(SkillRef skill, Integer targetObjectId)
				{
					return new SkillReadinessProbe(true, true, true);
				}

				@Override
				public List<SubclassEligibility> subclassEligibility(List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ClassFact> classes)
				{
					return List.of();
				}

				@Override
				public OperationResult learnClassSkill(LearnSkillRequest request, BooleanSupplier ownershipCurrent)
				{
					return OperationResult.rejected(OperationStatus.INVALID_REQUEST);
				}

				@Override
				public OperationResult equipOwnedItem(EquipItemRequest request, BooleanSupplier ownershipCurrent)
				{
					return OperationResult.rejected(OperationStatus.INVALID_REQUEST);
				}

				@Override
				public void close()
				{
				}
			}) : Optional.empty();
		}
	}
}

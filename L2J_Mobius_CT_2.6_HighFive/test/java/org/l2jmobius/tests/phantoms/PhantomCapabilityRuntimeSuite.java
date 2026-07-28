/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCapabilityEvaluator;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.CapabilityEvaluation;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.ReadinessReason;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillReadinessProbe;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.TargetScope;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;

public final class PhantomCapabilityRuntimeSuite implements PhantomTestSuite
{
	private static final int CASES = 40;
	private final PhantomProgressionSyntheticBackend _backend = new PhantomProgressionSyntheticBackend();
	private final PhantomProgressionCapabilityEvaluator _evaluator = new PhantomProgressionCapabilityEvaluator();
	private PhantomProgressionCatalog _catalog;

	@Override
	public String id()
	{
		return "capability-runtime";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		_catalog = new PhantomProgressionCatalogBuilder().build(PhantomProgressionSyntheticBackend.data(), PhantomProgressionPolicy.productionDefaults());
		context.record("capabilityRuntime.cases", CASES);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		for (int i = 0; i < CASES; i++)
		{
			final int test = i;
			registry.add(String.format("%02d-runtime-truth", i + 1), _ -> assertCase(test));
		}
	}

	private void assertCase(int test)
	{
		reset();
		final int variant = test % 16;
		switch (variant)
		{
			case 0 -> assertEvaluation("melee_damage", null, true, true, true, ReadinessReason.READY);
			case 1 -> assertEvaluation("ranged_damage", null, true, true, false, ReadinessReason.TARGET_REQUIRED);
			case 2 -> assertEvaluation("ranged_damage", 99, true, true, true, ReadinessReason.READY);
			case 3 -> assertEvaluation("bow_attack", 99, true, true, false, ReadinessReason.WEAPON_OR_EQUIPMENT_MISMATCH);
			case 4 -> assertEvaluation("summon", null, true, true, false, ReadinessReason.SERVITOR_NOT_PRESENT);
			case 5 -> assertEvaluation("resource", null, true, true, false, ReadinessReason.REQUIRED_ITEM_MISSING);
			case 6 -> assertEvaluation("unlearned", null, true, false, false, ReadinessReason.SKILL_NOT_LEARNED);
			case 7 ->
			{
				_backend.actor(PhantomProgressionSyntheticBackend.actor(true, false, false, Map.of(1, 1), List.of(), Map.of()));
				assertEvaluation("melee_damage", null, true, true, false, ReadinessReason.DEAD);
			}
			case 8 ->
			{
				_backend.actor(PhantomProgressionSyntheticBackend.actor(false, true, false, Map.of(1, 1), List.of(), Map.of()));
				assertEvaluation("melee_damage", null, true, true, false, ReadinessReason.TRANSFORMED);
			}
			case 9 ->
			{
				_backend.actor(PhantomProgressionSyntheticBackend.actor(false, false, true, Map.of(1, 1), List.of(), Map.of()));
				assertEvaluation("melee_damage", null, true, true, false, ReadinessReason.MOUNTED);
			}
			case 10 ->
			{
				_backend.probe(new SkillReadinessProbe(false, true, true));
				assertEvaluation("melee_damage", null, true, true, false, ReadinessReason.DYNAMIC_CONDITION_FAILED);
			}
			case 11 ->
			{
				_backend.probe(new SkillReadinessProbe(true, false, true));
				assertEvaluation("melee_damage", null, true, true, false, ReadinessReason.INSUFFICIENT_MP_OR_HP);
			}
			case 12 ->
			{
				_backend.probe(new SkillReadinessProbe(true, true, false));
				assertEvaluation("melee_damage", null, true, true, false, ReadinessReason.SKILL_DISABLED_OR_REUSE);
			}
			case 13 -> PhantomAssertions.assertEquals(TargetScope.SINGLE_TARGET, evaluation("ranged_damage", 99).targetScope(), "Target scope was inferred or lost.");
			case 14 -> PhantomAssertions.assertTrue(_catalog.capabilities(0).stream().noneMatch(rule -> rule.capabilityKey().contains("HUMAN_FIGHTER")), "Capability was inferred from class name.");
			case 15 -> PhantomAssertions.assertEquals(6, evaluate(null).size(), "Runtime did not evaluate each explicit rule once.");
		}
	}

	private void reset()
	{
		_backend.actor(PhantomProgressionSyntheticBackend.actor(false, false, false, Map.of(1, 1, 2, 1, 3, 1, 4, 1, 5, 1), List.of(), Map.of(57, 5L)));
		_backend.probe(new SkillReadinessProbe(true, true, true));
	}

	private void assertEvaluation(String key, Integer target, boolean intrinsic, boolean learned, boolean ready, ReadinessReason reason)
	{
		final CapabilityEvaluation value = evaluation(key, target);
		PhantomAssertions.assertEquals(intrinsic, value.intrinsic(), "INTRINSIC truth changed.");
		PhantomAssertions.assertEquals(learned, value.learned(), "LEARNED truth changed.");
		PhantomAssertions.assertEquals(ready, value.readyNow(), "READY_NOW truth changed.");
		PhantomAssertions.assertEquals(reason, value.reason(), "Readiness reason changed.");
	}

	private CapabilityEvaluation evaluation(String key, Integer target)
	{
		return evaluate(target).stream().filter(value -> value.capabilityKey().equals(key)).findFirst().orElseThrow();
	}

	private List<CapabilityEvaluation> evaluate(Integer target)
	{
		try (var lease = _backend.lease())
		{
			return _evaluator.evaluate(_catalog, lease.snapshot(_catalog.combinedHash(), _catalog.referencedResourceItemIds(), _catalog.certificationSkillIds(), 64), lease, target);
		}
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.Map;

import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;

public final class PhantomProgressionOperationSuite implements PhantomTestSuite
{
	private static final int CASES = 36;

	@Override
	public String id()
	{
		return "progression-operations";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		context.record("progressionOperations.cases", CASES);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		for (int i = 0; i < CASES; i++)
		{
			final int test = i;
			registry.add(String.format("%02d-operation-contract", i + 1), _ -> assertCase(test));
		}
	}

	private void assertCase(int test)
	{
		final PhantomProgressionSyntheticBackend backend = new PhantomProgressionSyntheticBackend();
		final PhantomProgressionService service = new PhantomProgressionService(backend, PhantomProgressionPolicy.productionDefaults());
		service.start();
		try
		{
			if (test < OperationStatus.values().length)
			{
				final OperationStatus status = OperationStatus.values()[test];
				final OperationResult configured = status == OperationStatus.SUCCESS ? new OperationResult(status, 100, 90, Map.of(57, 10L), Map.of(57, 8L), 1, true) : OperationResult.rejected(status);
				if ((test & 1) == 0)
				{
					backend.learnResult(configured);
					PhantomAssertions.assertEquals(status, service.learnClassSkill(new LearnSkillRequest(1, 2, AcquireKind.CLASS, 1, 1, () -> false)).status(), "Learn status mapping changed.");
				}
				else
				{
					backend.equipResult(configured);
					PhantomAssertions.assertEquals(status, service.equipOwnedItem(new EquipItemRequest(1, 1000, () -> false)).status(), "Equip status mapping changed.");
				}
			}
			else
			{
				assertAdditional(test - OperationStatus.values().length, backend, service);
			}
		}
		finally
		{
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Progression operations service did not drain.");
		}
	}

	private static void assertAdditional(int variant, PhantomProgressionSyntheticBackend backend, PhantomProgressionService service)
	{
		switch (variant)
		{
			case 0 ->
			{
				final int calls = backend.learnCalls();
				PhantomAssertions.assertEquals(OperationStatus.CANCELLED, service.learnClassSkill(new LearnSkillRequest(1, 2, AcquireKind.CLASS, 1, 1, () -> true)).status(), "Cancelled learn was accepted.");
				PhantomAssertions.assertEquals(calls, backend.learnCalls(), "Cancelled learn reached backend.");
			}
			case 1 ->
			{
				backend.actorPresent(false);
				PhantomAssertions.assertEquals(OperationStatus.ACTOR_NOT_MATERIALIZED, service.equipOwnedItem(new EquipItemRequest(1, 1000, () -> false)).status(), "Missing actor equip status changed.");
			}
			case 2 ->
			{
				backend.learnResult(new OperationResult(OperationStatus.SUCCESS, 100, 90, Map.of(57, 10L), Map.of(57, 8L), 1, false));
				final OperationResult result = service.learnClassSkill(new LearnSkillRequest(1, 2, AcquireKind.CLASS, 1, 1, () -> false));
				PhantomAssertions.assertEquals(10L, result.spBefore() - result.spAfter(), "Exact SP conservation changed.");
				PhantomAssertions.assertEquals(2L, result.itemCountsBefore().get(57) - result.itemCountsAfter().get(57), "Exact item conservation changed.");
			}
			case 3 ->
			{
				backend.equipResult(new OperationResult(OperationStatus.SUCCESS, 0, 0, Map.of(), Map.of(), 0, true));
				PhantomAssertions.assertTrue(service.equipOwnedItem(new EquipItemRequest(1, 1000, () -> false)).equipped(), "Canonical equip result was lost.");
			}
			case 4 -> PhantomAssertions.assertEquals(0, service.snapshot().currentOperations(), "Synchronous operation slot leaked.");
			case 5 -> PhantomAssertions.assertEquals(0, service.snapshot().currentActorLeases(), "Actor lease leaked.");
			default -> PhantomAssertions.assertTrue(service.snapshot().peakOperations() <= 1, "Per-profile operation serialization changed.");
		}
	}
}

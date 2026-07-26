/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;

public final class PhantomDecisionPerformanceSuite implements PhantomTestSuite
{
	private static final int PROFILE_COUNT = 1000;
	private static final int CANDIDATE_COUNT = 64;
	private static final int CONSIDERATION_COUNT = 8;
	private static final int DISPATCH_BUDGET = 32;
	private final AtomicInteger _handlerCalls = new AtomicInteger();
	private InMemoryGoalStore _store;
	private PhantomDecisionEngine _engine;

	@Override
	public String id()
	{
		return "decision-performance";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		_store = new InMemoryGoalStore(PROFILE_COUNT);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		for (int candidateIndex = 0; candidateIndex < CANDIDATE_COUNT; candidateIndex++)
		{
			final String candidateKey = String.format(java.util.Locale.ROOT, "candidate.%02d", candidateIndex);
			final List<PhantomWeightedConsideration> considerations = new ArrayList<>();
			for (int considerationIndex = 0; considerationIndex < CONSIDERATION_COUNT; considerationIndex++)
			{
				final int score = 1000 - candidateIndex;
				considerations.add(new PhantomWeightedConsideration(String.format(java.util.Locale.ROOT, "score.%02d", considerationIndex), considerationIndex + 1, _ -> new PhantomConsideration.Evaluation(score, "score.performance")));
			}
			candidates.register(new PhantomDecisionCandidate(candidateKey, Set.of("goal.performance"), Set.of(PhantomActivityState.WARM), List.of(), considerations, 0, planning -> new PhantomPlan(planning.decisionSequence(), planning.goal().goalId(), candidateKey, List.of(new PhantomPlanStep(0, "action.performance", null, Map.of(), 1000, 1, "reason.performance")), 1000, planning.logicalNowNanos())));
		}
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.register("action.performance", _ ->
		{
			_handlerCalls.incrementAndGet();
			return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success");
		});
		handlers.seal();
		_engine = new PhantomDecisionEngine(_store, candidates, handlers, new PhantomMetrics(), PROFILE_COUNT);
		_engine.start();
		for (long profileId = 1; profileId <= PROFILE_COUNT; profileId++)
		{
			PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, _engine.attach(profileId), "Performance profile attach failed.");
			PhantomAssertions.assertEquals(PhantomDecisionEngine.MutationResult.APPLIED, _engine.insertGoal(profileId, goal(profileId)), "Performance goal insert failed.");
		}
		context.record("decisionPerformance.profiles", PROFILE_COUNT);
		context.record("decisionPerformance.candidates", CANDIDATE_COUNT);
		context.record("decisionPerformance.considerations", CONSIDERATION_COUNT);
		context.record("decisionPerformance.dispatchBudget", DISPATCH_BUDGET);
	}

	@Override
	public void afterAll(PhantomTestContext context)
	{
		if (_engine != null)
		{
			_engine.beginStop();
			PhantomAssertions.assertTrue(_engine.finishStop(), "Performance engine did not stop.");
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-bounded-1000-runtime-structure", this::testStructure);
		registry.add("02-deterministic-32-profile-dispatch", this::testDispatch);
	}

	private void testStructure(PhantomTestContext context) throws Exception
	{
		final PhantomDecisionEngine.EngineSnapshot snapshot = _engine.snapshot();
		PhantomAssertions.assertEquals(PROFILE_COUNT, snapshot.attached(), "Performance engine did not retain exactly 1000 attached runtimes.");
		PhantomAssertions.assertEquals(CANDIDATE_COUNT, snapshot.registeredCandidates(), "Performance registry did not retain 64 candidates.");
		PhantomAssertions.assertEquals(1, snapshot.registeredHandlers(), "Performance registry retained an unexpected handler count.");
		for (Class<?> type : List.of(PhantomDecisionEngine.class, Class.forName(PhantomDecisionEngine.class.getName() + "$RuntimeSlot")))
		{
			for (Field field : type.getDeclaredFields())
			{
				final Class<?> fieldType = field.getType();
				PhantomAssertions.assertFalse(Thread.class.isAssignableFrom(fieldType) || Future.class.isAssignableFrom(fieldType) || Executor.class.isAssignableFrom(fieldType), "Decision runtime owns a per-profile execution primitive: " + field.getName());
			}
		}
		context.record("decisionPerformance.attached", snapshot.attached());
		context.record("decisionPerformance.storeReadsAfterAttach", _store.reads);
	}

	private void testDispatch(PhantomTestContext context)
	{
		final int readsBefore = _store.reads;
		for (long profileId = 1; profileId <= DISPATCH_BUDGET; profileId++)
		{
			_engine.accept(new PhantomActivityWorkItem(profileId, PhantomActivityState.WARM, 1, 1, 0, PhantomActivityOverloadLevel.NORMAL));
			final PhantomDecisionEngine.RuntimeSnapshot runtime = _engine.find(profileId).orElseThrow();
			PhantomAssertions.assertEquals("candidate.00", runtime.selectedCandidateKey(), "Performance selection was not deterministic.");
		}
		PhantomAssertions.assertEquals(DISPATCH_BUDGET, _handlerCalls.get(), "Bounded dispatch did not invoke exactly 32 handlers.");
		PhantomAssertions.assertEquals(readsBefore, _store.reads, "Performance ticks queried the goal store.");
		context.record("decisionPerformance.handlerCalls", _handlerCalls.get());
		context.record("decisionPerformance.tickReads", _store.reads - readsBefore);
		context.record("decisionPerformance.selection", "candidate.00");
	}

	private static PhantomGoal goal(long profileId)
	{
		return new PhantomGoal(profileId, "goal.performance", PhantomGoalStatus.ACTIVE, null, null, 0, 0, null, List.of(), null, "purpose.performance", 500, 0, 0, 0, Map.of(), "reason.performance", 0);
	}

	private static final class InMemoryGoalStore implements PhantomGoalStore
	{
		private final int _profileCount;
		private final Map<Long, StoredGoal> _goals = new HashMap<>();
		private int reads;

		private InMemoryGoalStore(int profileCount)
		{
			_profileCount = profileCount;
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return (profileId > 0) && (profileId <= _profileCount);
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			reads++;
			return Optional.ofNullable(_goals.get(profileId));
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			final StoredGoal stored = new StoredGoal(goal, 0);
			_goals.put(profileId, stored);
			return stored;
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			final StoredGoal stored = new StoredGoal(goal, expectedRowVersion + 1);
			_goals.put(profileId, stored);
			return stored;
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			_goals.remove(profileId);
		}
	}
}

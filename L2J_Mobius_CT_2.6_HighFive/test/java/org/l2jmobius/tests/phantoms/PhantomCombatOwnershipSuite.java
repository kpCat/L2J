/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.DetachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.MutationResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandler;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;

public final class PhantomCombatOwnershipSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "combat-ownership";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-same-plan-step-preserves-token", _ -> samePlan());
		registry.add("02-final-plan-completion-cancels", _ -> finalCompletion());
		registry.add("03-handler-replan-cancels", _ -> resultCancels(Type.REPLAN));
		registry.add("04-retry-exhaustion-cancels", _ -> retryExhaustion());
		registry.add("05-total-timeout-cancels", _ -> totalTimeout());
		registry.add("06-step-timeout-cancels", _ -> stepTimeout());
		registry.add("07-handler-cancelled-cancels", _ -> resultCancels(Type.CANCELLED));
		registry.add("08-complete-goal-cancels", _ -> resultCancels(Type.COMPLETE_GOAL));
		registry.add("09-fail-goal-cancels", _ -> resultCancels(Type.FAIL_GOAL));
		registry.add("10-detach-cancels", _ -> detach());
		registry.add("11-runtime-stop-cancels", _ -> stop());
		registry.add("12-activity-generation-cancels", _ -> activityGeneration());
		registry.add("13-goal-replacement-cancels", _ -> goalReplacement());
		registry.add("14-goal-reload-cancels", _ -> goalReload());
		registry.add("15-stale-result-does-not-own-new-plan", _ -> staleResult());
		registry.add("16-final-combat-start-self-cancels", _ -> finalCombatStart());
		registry.add("17-synchronous-retry-remains-current", _ -> synchronousRetry());
	}

	private static void samePlan()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.of(Type.SUCCESS, "step.success"));
		final Fixture fixture = fixture(handler, 2, 1000, 1000, 2, "action.test");
		fixture.work(1, 0);
		PhantomAssertions.assertFalse(handler.tokens.get(0).isCancelled(), "Same-plan next step cancelled the plan token.");
		fixture.stop();
	}

	private static void finalCompletion()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.of(Type.SUCCESS, "step.success"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 2, "action.test");
		fixture.work(1, 0);
		PhantomAssertions.assertTrue(handler.tokens.get(0).isCancelled(), "Final plan completion retained token ownership.");
		fixture.stop();
	}

	private static void resultCancels(Type type)
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.of(type, "step.result"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 2, "action.test");
		fixture.work(1, 0);
		PhantomAssertions.assertTrue(handler.tokens.get(0).isCancelled(), type + " retained plan ownership.");
		fixture.stop();
	}

	private static void retryExhaustion()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(0, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 2, "action.test");
		fixture.work(1, 0);
		final PhantomCancellationToken token = handler.tokens.get(0);
		PhantomAssertions.assertFalse(token.isCancelled(), "First retry cancelled a current plan.");
		fixture.work(2, 1);
		PhantomAssertions.assertTrue(token.isCancelled(), "Retry exhaustion retained plan ownership.");
		fixture.stop();
	}

	private static void totalTimeout()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(0, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1, 1000, 10, "action.test");
		fixture.work(1, 0);
		final PhantomCancellationToken token = handler.tokens.get(0);
		fixture.work(2, 2_000_000);
		PhantomAssertions.assertTrue(token.isCancelled(), "Total timeout retained plan ownership.");
		fixture.stop();
	}

	private static void stepTimeout()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(0, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1000, 1, 10, "action.test");
		fixture.work(1, 0);
		final PhantomCancellationToken token = handler.tokens.get(0);
		fixture.work(2, 2_000_000);
		PhantomAssertions.assertTrue(token.isCancelled(), "Step timeout retained plan ownership.");
		fixture.stop();
	}

	private static void detach()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(100, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 10, "action.test");
		fixture.work(1, 0);
		final PhantomCancellationToken token = handler.tokens.get(0);
		PhantomAssertions.assertEquals(DetachResult.DETACHED, fixture.engine.detach(1), "Quiescent detach failed.");
		PhantomAssertions.assertTrue(token.isCancelled(), "Detach retained plan ownership.");
		fixture.stopDetached();
	}

	private static void stop()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(100, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 10, "action.test");
		fixture.work(1, 0);
		final PhantomCancellationToken token = handler.tokens.get(0);
		fixture.engine.beginStop();
		PhantomAssertions.assertTrue(token.isCancelled(), "Runtime stop retained plan ownership.");
		PhantomAssertions.assertTrue(fixture.engine.finishStop(), "Stopped ownership fixture did not quiesce.");
	}

	private static void activityGeneration()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(100, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 10, "action.test");
		fixture.work(1, 0);
		final PhantomCancellationToken token = handler.tokens.get(0);
		fixture.engine.accept(work(1, 2, 2, 1));
		PhantomAssertions.assertTrue(token.isCancelled(), "Activity generation replacement retained old plan ownership.");
		fixture.stop();
	}

	private static void goalReplacement()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(100, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 10, "action.test");
		fixture.work(1, 0);
		final PhantomCancellationToken token = handler.tokens.get(0);
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.setGoal(1, goal(1)), "Goal replacement failed.");
		PhantomAssertions.assertTrue(token.isCancelled(), "Goal replacement retained old plan ownership.");
		fixture.stop();
	}

	private static void goalReload()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(100, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 10, "action.test");
		fixture.work(1, 0);
		final PhantomCancellationToken token = handler.tokens.get(0);
		PhantomAssertions.assertEquals(PhantomDecisionEngine.ReloadResult.RELOADED, fixture.engine.reload(1), "Goal reload failed.");
		PhantomAssertions.assertTrue(token.isCancelled(), "Goal reload retained old plan ownership.");
		fixture.stop();
	}

	private static void staleResult() throws Exception
	{
		final BlockingHandler handler = new BlockingHandler();
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 10, "action.test");
		final Thread worker = new Thread(() -> fixture.work(1, 0), "combat-ownership-stale");
		worker.start();
		handler.awaitEntered();
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.setGoal(1, goal(1)), "Concurrent goal replacement failed.");
		handler.release();
		worker.join(TimeUnit.SECONDS.toMillis(2));
		PhantomAssertions.assertFalse(worker.isAlive(), "Stale handler did not quiesce.");
		PhantomAssertions.assertTrue(handler.firstToken.get().isCancelled(), "Stale token remained current.");
		final TokenHandler next = handler.next;
		fixture.work(2, 1);
		PhantomAssertions.assertFalse(next.tokens.get(0).isCancelled(), "Stale result cancelled the newer plan.");
		fixture.stop();
	}

	private static void finalCombatStart()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.of(Type.SUCCESS, "combat.start.accepted"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 2, "combat.start");
		fixture.work(1, 0);
		PhantomAssertions.assertTrue(handler.tokens.get(0).isCancelled(), "Final combat.start did not self-cancel after plan completion.");
		fixture.stop();
	}

	private static void synchronousRetry()
	{
		final TokenHandler handler = new TokenHandler(PhantomStepResult.retry(100, "step.retry"));
		final Fixture fixture = fixture(handler, 1, 1000, 1000, 2, "action.test");
		fixture.work(1, 0);
		PhantomAssertions.assertFalse(handler.tokens.get(0).isCancelled(), "Ordinary synchronous RETRY lost current plan ownership.");
		fixture.stop();
	}

	private static Fixture fixture(PhantomStepHandler handler, int steps, long planTimeout, long stepTimeout, int maximumAttempts, String actionKey)
	{
		final Store store = new Store();
		store.profiles.add(1L);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		candidates.register(new PhantomDecisionCandidate("candidate.test", Set.of("goal.test"), Set.of(PhantomActivityState.WARM), List.of(), List.of(new PhantomWeightedConsideration("score.test", 1, _ -> new PhantomConsideration.Evaluation(1000, "score.test"))), 0, context ->
		{
			final List<PhantomPlanStep> planSteps = new ArrayList<>();
			for (int index = 0; index < steps; index++)
			{
				planSteps.add(new PhantomPlanStep(index, actionKey, null, Map.of(), stepTimeout, maximumAttempts, "reason.test"));
			}
			return new PhantomPlan(1, context.goal().goalId(), "candidate.test", planSteps, planTimeout, context.logicalNowNanos());
		}));
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.register(actionKey, handler);
		handlers.seal();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(store, candidates, handlers, new PhantomMetrics(), 4);
		engine.start();
		PhantomAssertions.assertEquals(AttachResult.ATTACHED, engine.attach(1), "Ownership fixture did not attach.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, engine.insertGoal(1, goal(0)), "Ownership fixture goal insert failed.");
		return new Fixture(engine);
	}

	private static PhantomGoal goal(long revision)
	{
		return new PhantomGoal(1, "goal.test", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("subject", "A"), null, 1, 0, null, List.of(), null, "purpose.test", 1, 0, 0, 0, Map.of(), "reason.test", revision);
	}

	private static PhantomActivityWorkItem work(long profileId, long activityGeneration, long tick, long now)
	{
		return new PhantomActivityWorkItem(profileId, PhantomActivityState.WARM, activityGeneration, tick, now, PhantomActivityOverloadLevel.NORMAL);
	}

	private record Fixture(PhantomDecisionEngine engine)
	{
		private void work(long tick, long now)
		{
			engine.accept(PhantomCombatOwnershipSuite.work(1, 1, tick, now));
		}

		private void stop()
		{
			engine.beginStop();
			PhantomAssertions.assertTrue(engine.finishStop(), "Ownership fixture did not stop.");
		}

		private void stopDetached()
		{
			engine.beginStop();
			PhantomAssertions.assertTrue(engine.finishStop(), "Detached ownership fixture did not stop.");
		}
	}

	private static final class TokenHandler implements PhantomStepHandler
	{
		private final PhantomStepResult result;
		private final List<PhantomCancellationToken> tokens = new ArrayList<>();

		private TokenHandler(PhantomStepResult result)
		{
			this.result = result;
		}

		@Override
		public PhantomStepResult execute(org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext context)
		{
			tokens.add(context.cancellationToken());
			return result;
		}
	}

	private static final class BlockingHandler implements PhantomStepHandler
	{
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicReference<PhantomCancellationToken> firstToken = new AtomicReference<>();
		private final TokenHandler next = new TokenHandler(PhantomStepResult.retry(100, "step.retry"));
		private boolean first = true;

		@Override
		public synchronized PhantomStepResult execute(org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext context)
		{
			if (!first)
			{
				return next.execute(context);
			}
			first = false;
			firstToken.set(context.cancellationToken());
			entered.countDown();
			try
			{
				if (!release.await(2, TimeUnit.SECONDS))
				{
					throw new AssertionError("Timed out waiting for stale handler release.");
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError(e);
			}
			return PhantomStepResult.of(Type.SUCCESS, "step.success");
		}

		private void awaitEntered() throws InterruptedException
		{
			PhantomAssertions.assertTrue(entered.await(2, TimeUnit.SECONDS), "Blocking ownership handler did not enter.");
		}

		private void release()
		{
			release.countDown();
		}
	}

	private static final class Store implements PhantomGoalStore
	{
		private final Set<Long> profiles = new HashSet<>();
		private final Map<Long, StoredGoal> goals = new HashMap<>();

		@Override
		public synchronized boolean profileExists(long profileId)
		{
			return profiles.contains(profileId);
		}

		@Override
		public synchronized Optional<StoredGoal> load(long profileId)
		{
			return Optional.ofNullable(goals.get(profileId));
		}

		@Override
		public synchronized StoredGoal insert(long profileId, PhantomGoal goal)
		{
			if (goals.containsKey(profileId))
			{
				throw new ConcurrentModificationException("duplicate");
			}
			final StoredGoal stored = new StoredGoal(goal, 0);
			goals.put(profileId, stored);
			return stored;
		}

		@Override
		public synchronized StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			final StoredGoal current = goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion))
			{
				throw new ConcurrentModificationException("stale");
			}
			final StoredGoal stored = new StoredGoal(goal, expectedRowVersion + 1);
			goals.put(profileId, stored);
			return stored;
		}

		@Override
		public synchronized void delete(long profileId, long expectedRowVersion)
		{
			final StoredGoal current = goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion))
			{
				throw new ConcurrentModificationException("stale");
			}
			goals.remove(profileId);
		}
	}
}

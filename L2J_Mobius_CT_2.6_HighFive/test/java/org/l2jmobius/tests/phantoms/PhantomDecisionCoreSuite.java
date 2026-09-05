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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.AttachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.DetachResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.MutationResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandler;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;

public final class PhantomDecisionCoreSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "decision-core";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-domain-ref-valid-and-ordered", _ -> testDomainRef());
		registry.add("02-domain-ref-rejects-invalid", _ -> testDomainRefInvalid());
		registry.add("03-capability-set-sorted-and-bounded", _ -> testCapabilities());
		registry.add("04-capability-requirement-gate", _ -> testCapabilityRequirement());
		registry.add("05-goal-immutable-canonical-fields", _ -> testGoalCanonical());
		registry.add("06-goal-invariants-reject-invalid", _ -> testGoalInvalid());
		registry.add("07-plan-contiguous-and-immutable", _ -> testPlan());
		registry.add("08-plan-bounds-reject-invalid", _ -> testPlanInvalid());
		registry.add("09-candidate-registry-seal-and-order", _ -> testCandidateRegistry());
		registry.add("10-candidate-registry-rejects-duplicate-and-late", _ -> testCandidateRegistryRejects());
		registry.add("11-handler-registry-seal-and-order", _ -> testHandlerRegistry());
		registry.add("12-integer-weighted-floor", _ -> testIntegerScore());
		registry.add("13-deterministic-ascii-tie", _ -> testTieBreak());
		registry.add("14-capability-block-before-consideration", _ -> testCapabilityBlocksFirst());
		registry.add("15-threshold-rejects-candidate", _ -> testThreshold());
		registry.add("16-consideration-exception-isolated", _ -> testConsiderationException());
		registry.add("17-invalid-score-isolated", _ -> testInvalidScore());
		registry.add("18-explanations-bounded-and-ordered", _ -> testExplanations());
		registry.add("19-attach-load-once-and-revision-gate", _ -> testAttachAndRevision());
		registry.add("20-one-handler-per-work-and-step-order", _ -> testOneStepPerWork());
		registry.add("21-retry-delay-and-attempt-bound", _ -> testRetry());
		registry.add("22-replan-discards-plan", _ -> testReplan());
		registry.add("23-logical-total-timeout", _ -> testTimeout());
		registry.add("24-activity-generation-cancels-stale-result", _ -> testActivityGenerationCancellation());
		registry.add("25-goal-replacement-cancels-stale-result", _ -> testGoalReplacementCancellation());
		registry.add("26-detach-pending-and-cooperative-token", _ -> testDetachCancellation());
		registry.add("27-stop-waits-for-handler-quiescence", _ -> testStopQuiescence());
		registry.add("28-persistence-conflict-requires-reload", _ -> testPersistenceConflict());
		registry.add("29-terminal-result-persists-goal-only", _ -> testTerminalPersistence());
		registry.add("30-ordinary-work-performs-no-store-read", _ -> testNoReadsOnTicks());
		registry.add("31-logical-zero-step-timeout", _ -> testLogicalZeroStepTimeout());
		registry.add("32-final-success-plan-is-nonterminal-goal", _ -> testFinalSuccessNonTerminalGoal());
		registry.add("33-goal-boundaries-reset-snapshot-evidence", _ -> testGoalReplacementResetsEvidence());
		registry.add("34-activity-generation-resets-snapshot-evidence", _ -> testActivityGenerationResetsEvidence());
		registry.add("35-stop-resets-snapshot-evidence", _ -> testStopResetsEvidence());
		registry.add("36-admission-fence-blocks-work", _ -> testAdmissionFence());
	}

	private void testDomainRef()
	{
		final PhantomDomainRef first = new PhantomDomainRef("domain", "A");
		final PhantomDomainRef second = new PhantomDomainRef("domain", "B");
		PhantomAssertions.assertTrue(first.compareTo(second) < 0, "DomainRef ordering was not ordinal.");
		PhantomAssertions.assertEquals("domain", first.namespace(), "DomainRef namespace changed.");
	}

	private void testDomainRefInvalid()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomDomainRef("Bad", "A"), "Uppercase namespace was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomDomainRef("domain", " A"), "Surrounding whitespace was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomDomainRef("domain", "А"), "Non-ASCII key was accepted.");
	}

	private void testCapabilities()
	{
		final PhantomCapabilitySet capabilities = new PhantomCapabilitySet(Map.of("capability.z", 2, "capability.a", 1));
		PhantomAssertions.assertEquals(List.of("capability.a", "capability.z"), new ArrayList<>(capabilities.ranks().keySet()), "Capabilities were not immutable ordinal sorted.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> capabilities.ranks().put("capability.x", 1), "Capability map remained mutable.");
		final Map<String, Integer> oversized = new HashMap<>();
		for (int index = 0; index < 129; index++)
		{
			oversized.put("capability." + index, 1);
		}
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomCapabilitySet(oversized), "Capability set exceeded 128 entries.");
	}

	private void testCapabilityRequirement()
	{
		final PhantomCapabilitySet capabilities = new PhantomCapabilitySet(Map.of("capability.a", 2));
		PhantomAssertions.assertTrue(capabilities.satisfies(new PhantomCapabilityRequirement("capability.a", 2)), "Exact capability rank did not satisfy requirement.");
		PhantomAssertions.assertFalse(capabilities.satisfies(new PhantomCapabilityRequirement("capability.a", 3)), "Insufficient capability rank passed.");
	}

	private void testGoalCanonical()
	{
		final PhantomGoal goal = goal(0);
		PhantomAssertions.assertEquals(List.of(new PhantomDomainRef("source", "A"), new PhantomDomainRef("source", "B")), goal.validSources(), "Goal sources were not canonical.");
		PhantomAssertions.assertEquals(List.of("constraint.a", "constraint.z"), new ArrayList<>(goal.constraints().keySet()), "Goal constraints were not sorted.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> goal.constraints().put("constraint.x", 1L), "Goal constraints remained mutable.");
	}

	private void testGoalInvalid()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomGoal(1, "goal.test", PhantomGoalStatus.ACTIVE, null, null, 1, 2, null, List.of(), null, "purpose.test", 1, 0, 0, 0, Map.of(), "reason.test", 0), "currentAmount above requiredAmount was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomGoal(1, "goal.test", PhantomGoalStatus.ACTIVE, null, null, 0, 0, null, List.of(), null, "purpose.test", 1001, 0, 0, 0, Map.of(), "reason.test", 0), "Priority above 1000 was accepted.");
	}

	private void testPlan()
	{
		final PhantomPlan plan = plan("candidate.a", 1, 0, 2);
		PhantomAssertions.assertEquals(2, plan.steps().size(), "Plan lost steps.");
		PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> plan.steps().add(step(2)), "Plan steps remained mutable.");
	}

	private void testPlanInvalid()
	{
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomPlan(1, 1, "candidate.a", List.of(new PhantomPlanStep(1, "action.test", null, Map.of(), 1, 1, "reason.test")), 1, 0), "Non-contiguous plan was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomPlanStep(0, "action.test", null, Map.of(), 0, 1, "reason.test"), "Zero step timeout was accepted.");
		final List<PhantomPlanStep> oversized = new ArrayList<>();
		for (int index = 0; index < 33; index++)
		{
			oversized.add(step(index));
		}
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomPlan(1, 1, "candidate.a", oversized, 1, 0), "Plan exceeded 32 steps.");
	}

	private void testCandidateRegistry()
	{
		final PhantomCandidateRegistry registry = new PhantomCandidateRegistry();
		registry.register(candidate("candidate.z", 1, context -> plan("candidate.z", context.goal().goalId(), context.logicalNowNanos(), 1)));
		registry.register(candidate("candidate.a", 1, context -> plan("candidate.a", context.goal().goalId(), context.logicalNowNanos(), 1)));
		registry.seal();
		PhantomAssertions.assertEquals(List.of("candidate.a", "candidate.z"), registry.snapshot().stream().map(PhantomDecisionCandidate::key).toList(), "Candidate snapshot was not lexicographic.");
	}

	private void testCandidateRegistryRejects()
	{
		final PhantomCandidateRegistry registry = new PhantomCandidateRegistry();
		final PhantomDecisionCandidate candidate = candidate("candidate.a", 1, context -> plan("candidate.a", context.goal().goalId(), context.logicalNowNanos(), 1));
		registry.register(candidate);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> registry.register(candidate), "Duplicate candidate was accepted.");
		registry.seal();
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> registry.register(candidate("candidate.b", 1, context -> plan("candidate.b", context.goal().goalId(), context.logicalNowNanos(), 1))), "Late candidate registration was accepted.");
		final PhantomCandidateRegistry full = new PhantomCandidateRegistry();
		for (int index = 0; index < PhantomCandidateRegistry.MAX_CANDIDATES; index++)
		{
			final String key = "candidate.capacity." + index;
			full.register(candidate(key, 1, context -> plan(key, context.goal().goalId(), context.logicalNowNanos(), 1)));
		}
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> full.register(candidate("candidate.overflow", 1, context -> plan("candidate.overflow", context.goal().goalId(), context.logicalNowNanos(), 1))), "Candidate registry exceeded 256 entries.");
	}

	private void testHandlerRegistry()
	{
		final PhantomStepHandlerRegistry registry = new PhantomStepHandlerRegistry();
		registry.register("action.z", _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"));
		registry.register("action.a", _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"));
		registry.seal();
		PhantomAssertions.assertEquals(List.of("action.a", "action.z"), new ArrayList<>(registry.snapshot().keySet()), "Handler snapshot was not lexicographic.");
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> registry.register("action.x", _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success")), "Late handler registration was accepted.");
		final PhantomStepHandlerRegistry full = new PhantomStepHandlerRegistry();
		for (int index = 0; index < PhantomStepHandlerRegistry.MAX_HANDLERS; index++)
		{
			full.register("action.capacity." + index, _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"));
		}
		PhantomAssertions.assertThrows(IllegalStateException.class, () -> full.register("action.overflow", _ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success")), "Handler registry exceeded 256 entries.");
	}

	private void testIntegerScore()
	{
		final PhantomDecisionCandidate candidate = new PhantomDecisionCandidate("candidate.a", Set.of("goal.test"), Set.of(PhantomActivityState.WARM), List.of(), List.of(
			new PhantomWeightedConsideration("score.a", 2, _ -> new PhantomConsideration.Evaluation(1, "score.a")),
			new PhantomWeightedConsideration("score.b", 1, _ -> new PhantomConsideration.Evaluation(2, "score.b"))), 0, context -> plan("candidate.a", context.goal().goalId(), context.logicalNowNanos(), 1));
		final PhantomUtilitySelector.Selection selection = new PhantomUtilitySelector().select(List.of(candidate), planning());
		PhantomAssertions.assertEquals(1, selection.score(), "Weighted normalized score did not use floor integer arithmetic.");
	}

	private void testTieBreak()
	{
		final PhantomUtilitySelector.Selection selection = new PhantomUtilitySelector().select(List.of(
			candidate("candidate.z", 500, context -> plan("candidate.z", context.goal().goalId(), context.logicalNowNanos(), 1)),
			candidate("candidate.a", 500, context -> plan("candidate.a", context.goal().goalId(), context.logicalNowNanos(), 1))), planning());
		PhantomAssertions.assertEquals("candidate.a", selection.candidate().key(), "Exact utility tie did not use ASCII key ascending.");
	}

	private void testCapabilityBlocksFirst()
	{
		final AtomicInteger calls = new AtomicInteger();
		final PhantomDecisionCandidate candidate = new PhantomDecisionCandidate("candidate.a", Set.of("goal.test"), Set.of(PhantomActivityState.WARM), List.of(new PhantomCapabilityRequirement("capability.required", 1)), List.of(new PhantomWeightedConsideration("score.a", 1, _ ->
		{
			calls.incrementAndGet();
			return new PhantomConsideration.Evaluation(1000, "score.a");
		})), 0, context -> plan("candidate.a", context.goal().goalId(), context.logicalNowNanos(), 1));
		final PhantomUtilitySelector.Selection selection = new PhantomUtilitySelector().select(List.of(candidate), planning());
		PhantomAssertions.assertEquals(null, selection.candidate(), "Missing capability did not block candidate.");
		PhantomAssertions.assertEquals(0, calls.get(), "Consideration ran before capability gate.");
	}

	private void testThreshold()
	{
		final PhantomDecisionCandidate candidate = candidate("candidate.a", 499, 500, context -> plan("candidate.a", context.goal().goalId(), context.logicalNowNanos(), 1));
		PhantomAssertions.assertEquals(null, new PhantomUtilitySelector().select(List.of(candidate), planning()).candidate(), "Below-threshold candidate was selected.");
	}

	private void testConsiderationException()
	{
		final PhantomDecisionCandidate failed = weightedCandidate("candidate.a", _ ->
		{
			throw new IllegalStateException("injected");
		});
		final PhantomDecisionCandidate healthy = candidate("candidate.b", 10, context -> plan("candidate.b", context.goal().goalId(), context.logicalNowNanos(), 1));
		final PhantomUtilitySelector.Selection selection = new PhantomUtilitySelector().select(List.of(failed, healthy), planning());
		PhantomAssertions.assertEquals("candidate.b", selection.candidate().key(), "One consideration exception escaped candidate isolation.");
		PhantomAssertions.assertEquals(1, selection.failed(), "Failed candidate was not counted.");
	}

	private void testInvalidScore()
	{
		final PhantomDecisionCandidate invalid = weightedCandidate("candidate.a", _ -> new PhantomConsideration.Evaluation(1001, "score.invalid"));
		final PhantomUtilitySelector.Selection selection = new PhantomUtilitySelector().select(List.of(invalid), planning());
		PhantomAssertions.assertEquals(null, selection.candidate(), "Invalid consideration score was selected.");
		PhantomAssertions.assertEquals(1, selection.failed(), "Invalid score was not isolated as candidate failure.");
	}

	private void testExplanations()
	{
		final List<PhantomDecisionCandidate> candidates = new ArrayList<>();
		for (int index = 0; index < 12; index++)
		{
			final String key = "candidate." + (char) ('a' + index);
			candidates.add(candidate(key, 100, context -> plan(key, context.goal().goalId(), context.logicalNowNanos(), 1)));
		}
		final List<PhantomUtilitySelector.CandidateEvaluation> explanations = new PhantomUtilitySelector().select(candidates, planning()).explanations();
		PhantomAssertions.assertEquals(8, explanations.size(), "Candidate explanations were not bounded to eight.");
		PhantomAssertions.assertEquals("candidate.a", explanations.get(0).candidateKey(), "Explanation tie order was not ASCII ascending.");
	}

	private void testAdmissionFence()
	{
		final AtomicBoolean permitted = new AtomicBoolean();
		final AtomicInteger calls = new AtomicInteger();
		final EngineFixture fixture = fixture(_ ->
		{
			calls.incrementAndGet();
			return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success");
		}, 1, 1000, 1000, _ -> permitted.get());
		PhantomAssertions.assertEquals(AttachResult.ATTACHED, fixture.engine.attach(1), "Admission fixture profile did not attach.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.insertGoal(1, goal(0)), "Admission fixture goal did not insert.");
		fixture.engine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals(0, calls.get(), "Denied admission executed a Decision handler.");
		permitted.set(true);
		fixture.engine.accept(work(1, 1, 2, 1));
		PhantomAssertions.assertEquals(1, calls.get(), "Reopened admission did not execute normal Decision work.");
		fixture.stop();
	}
	private void testAttachAndRevision()
	{
		final EngineFixture fixture = fixture(_ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 1);
		PhantomAssertions.assertEquals(AttachResult.ATTACHED, fixture.engine.attach(1), "Profile did not attach.");
		PhantomAssertions.assertEquals(1, fixture.store.reads, "Attach did not perform exactly one component load.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.insertGoal(1, goal(0)), "Initial goal insert failed.");
		PhantomAssertions.assertEquals(MutationResult.REVISION_REJECTED, fixture.engine.setGoal(1, goal(0)), "Equal revision replacement was accepted.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.setGoal(1, goal(1)), "Strictly newer goal revision was rejected.");
		fixture.stop();
	}

	private void testOneStepPerWork()
	{
		final AtomicInteger calls = new AtomicInteger();
		final EngineFixture fixture = startedFixture(_ ->
		{
			calls.incrementAndGet();
			return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success");
		}, 2);
		fixture.engine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals(1, calls.get(), "One work item invoked more than one handler.");
		PhantomAssertions.assertEquals(1, fixture.engine.find(1).orElseThrow().currentStep(), "First success did not advance exactly one step.");
		fixture.engine.accept(work(1, 1, 2, 1_000_000));
		PhantomAssertions.assertEquals(2, calls.get(), "Second work did not invoke exactly one handler.");
		fixture.stop();
	}

	private void testRetry()
	{
		final AtomicInteger calls = new AtomicInteger();
		final EngineFixture fixture = startedFixture(_ -> calls.incrementAndGet() == 1 ? PhantomStepResult.retry(10, "step.retry") : PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 1);
		fixture.engine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals(RuntimeState.WAITING_RETRY, fixture.engine.find(1).orElseThrow().runtimeState(), "RETRY did not enter bounded wait.");
		fixture.engine.accept(work(1, 1, 2, 5_000_000));
		PhantomAssertions.assertEquals(1, calls.get(), "Retry ran before logical delay.");
		fixture.engine.accept(work(1, 1, 3, 10_000_000));
		PhantomAssertions.assertEquals(2, calls.get(), "Retry did not run at logical due time.");
		fixture.stop();
	}

	private void testReplan()
	{
		final EngineFixture fixture = startedFixture(_ -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "step.replan"), 1);
		fixture.engine.accept(work(1, 1, 1, 0));
		final PhantomDecisionEngine.RuntimeSnapshot snapshot = fixture.engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(RuntimeState.NEEDS_REPLAN, snapshot.runtimeState(), "REPLAN did not discard plan.");
		PhantomAssertions.assertEquals(0L, snapshot.planId(), "REPLAN retained plan data.");
		fixture.stop();
	}

	private void testTimeout()
	{
		final AtomicInteger calls = new AtomicInteger();
		final EngineFixture fixture = startedFixture(_ ->
		{
			calls.incrementAndGet();
			return PhantomStepResult.retry(10, "step.retry");
		}, 1, 1);
		fixture.engine.accept(work(1, 1, 1, 0));
		fixture.engine.accept(work(1, 1, 2, 10_000_000));
		PhantomAssertions.assertEquals(1, calls.get(), "Timed-out plan invoked a second handler.");
		PhantomAssertions.assertEquals(RuntimeState.NEEDS_REPLAN, fixture.engine.find(1).orElseThrow().runtimeState(), "Logical total timeout did not request replan.");
		fixture.stop();
	}

	private void testActivityGenerationCancellation() throws Exception
	{
		final BlockingHandler blocking = new BlockingHandler();
		final EngineFixture fixture = startedFixture(blocking, 1);
		final Thread worker = new Thread(() -> fixture.engine.accept(work(1, 1, 1, 0)), "t008-activity-generation");
		worker.start();
		blocking.awaitEntered();
		fixture.engine.accept(work(1, 2, 2, 1));
		blocking.release();
		join(worker);
		PhantomAssertions.assertTrue(blocking.cancelled.get(), "Activity generation change was not visible through cancellation token.");
		PhantomAssertions.assertEquals(RuntimeState.NEEDS_REPLAN, fixture.engine.find(1).orElseThrow().runtimeState(), "Stale activity result mutated current plan state.");
		fixture.stop();
	}

	private void testGoalReplacementCancellation() throws Exception
	{
		final BlockingHandler blocking = new BlockingHandler();
		final EngineFixture fixture = startedFixture(blocking, 1);
		final Thread worker = new Thread(() -> fixture.engine.accept(work(1, 1, 1, 0)), "t008-goal-generation");
		worker.start();
		blocking.awaitEntered();
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.setGoal(1, goal(1)), "Concurrent goal replacement failed.");
		blocking.release();
		join(worker);
		final PhantomDecisionEngine.RuntimeSnapshot snapshot = fixture.engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(1L, snapshot.goalRevision(), "Stale handler restored old goal revision.");
		PhantomAssertions.assertEquals(RuntimeState.NEEDS_REPLAN, snapshot.runtimeState(), "Stale handler result mutated replacement goal.");
		fixture.stop();
	}

	private void testDetachCancellation() throws Exception
	{
		final BlockingHandler blocking = new BlockingHandler();
		final EngineFixture fixture = startedFixture(blocking, 1);
		final Thread worker = new Thread(() -> fixture.engine.accept(work(1, 1, 1, 0)), "t008-detach-generation");
		worker.start();
		blocking.awaitEntered();
		PhantomAssertions.assertEquals(DetachResult.PENDING, fixture.engine.detach(1), "In-flight detach did not remain pending.");
		blocking.release();
		join(worker);
		PhantomAssertions.assertTrue(blocking.cancelled.get(), "Detach was not visible through cooperative token.");
		PhantomAssertions.assertTrue(fixture.engine.find(1).isEmpty(), "Pending detach retained runtime after handler quiescence.");
		fixture.engine.beginStop();
		PhantomAssertions.assertTrue(fixture.engine.finishStop(), "Detached engine did not stop.");
	}

	private void testStopQuiescence() throws Exception
	{
		final BlockingHandler blocking = new BlockingHandler();
		final EngineFixture fixture = startedFixture(blocking, 1);
		final Thread worker = new Thread(() -> fixture.engine.accept(work(1, 1, 1, 0)), "t008-stop-generation");
		worker.start();
		blocking.awaitEntered();
		fixture.engine.beginStop();
		PhantomAssertions.assertFalse(fixture.engine.finishStop(), "finishStop cleared an in-flight handler.");
		blocking.release();
		join(worker);
		PhantomAssertions.assertTrue(fixture.engine.finishStop(), "Quiescent decision engine did not stop.");
	}

	private void testPersistenceConflict()
	{
		final EngineFixture fixture = startedFixture(_ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 1);
		fixture.store.conflictNextReplace = true;
		PhantomAssertions.assertEquals(MutationResult.PERSISTENCE_CONFLICT, fixture.engine.setGoal(1, goal(1)), "Optimistic conflict was not surfaced.");
		PhantomAssertions.assertEquals(RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD, fixture.engine.find(1).orElseThrow().runtimeState(), "Conflict did not require explicit reload.");
		PhantomAssertions.assertEquals(PhantomDecisionEngine.ReloadResult.RELOADED, fixture.engine.reload(1), "Explicit reload did not recover conflict state.");
		fixture.stop();
	}

	private void testTerminalPersistence()
	{
		final EngineFixture fixture = startedFixture(_ -> PhantomStepResult.of(PhantomStepResult.Type.COMPLETE_GOAL, "step.complete"), 1);
		fixture.engine.accept(work(1, 1, 1, 0));
		final PhantomDecisionEngine.RuntimeSnapshot snapshot = fixture.engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, snapshot.goalStatus(), "Terminal handler result was not persisted.");
		PhantomAssertions.assertEquals(RuntimeState.TERMINAL, snapshot.runtimeState(), "Terminal goal did not stop planning.");
		PhantomAssertions.assertEquals(0L, snapshot.planId(), "Terminal persistence retained a plan.");
		fixture.stop();
	}

	private void testNoReadsOnTicks()
	{
		final EngineFixture fixture = startedFixture(_ -> PhantomStepResult.of(PhantomStepResult.Type.REPLAN, "step.replan"), 1);
		final int readsAfterAttach = fixture.store.reads;
		for (int index = 0; index < 5; index++)
		{
			fixture.engine.accept(work(1, 1, index + 1, index));
		}
		PhantomAssertions.assertEquals(readsAfterAttach, fixture.store.reads, "Ordinary decision work queried persistence.");
		fixture.stop();
	}

	private void testLogicalZeroStepTimeout()
	{
		final AtomicInteger calls = new AtomicInteger();
		final EngineFixture fixture = startedFixture(_ ->
		{
			calls.incrementAndGet();
			return PhantomStepResult.retry(0, "step.retry");
		}, 1, 1000, 5);
		fixture.engine.accept(work(1, 1, 1, 0));
		fixture.engine.accept(work(1, 1, 2, 6_000_000));
		final PhantomDecisionEngine.RuntimeSnapshot snapshot = fixture.engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(1, calls.get(), "Logical-zero step timeout invoked a retry handler after timeout.");
		PhantomAssertions.assertEquals(RuntimeState.NEEDS_REPLAN, snapshot.runtimeState(), "Logical-zero step timeout did not request replan.");
		PhantomAssertions.assertEquals("plan.step_timeout", snapshot.reasonKey(), "Logical-zero timeout was not classified as a step timeout.");
		fixture.stop();
	}

	private void testFinalSuccessNonTerminalGoal()
	{
		final EngineFixture fixture = startedFixture(_ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 1);
		fixture.engine.accept(work(1, 1, 1, 0));
		final PhantomDecisionEngine.RuntimeSnapshot snapshot = fixture.engine.find(1).orElseThrow();
		PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, snapshot.goalStatus(), "Final ordinary SUCCESS terminally changed the goal.");
		PhantomAssertions.assertEquals(RuntimeState.NEEDS_REPLAN, snapshot.runtimeState(), "Final ordinary SUCCESS did not complete only the plan.");
		PhantomAssertions.assertEquals(0L, snapshot.planId(), "Final ordinary SUCCESS retained a completed plan.");
		fixture.stop();
	}

	private void testGoalReplacementResetsEvidence()
	{
		final EngineFixture fixture = startedFixture(_ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 2);
		fixture.engine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals("candidate.test", fixture.engine.find(1).orElseThrow().selectedCandidateKey(), "Fixture did not publish decision evidence.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.setGoal(1, goal(1)), "Goal replacement failed.");
		assertEvidenceReset(fixture.engine.find(1).orElseThrow(), "Goal replacement");
		fixture.engine.accept(work(1, 1, 2, 1));
		PhantomAssertions.assertEquals("candidate.test", fixture.engine.find(1).orElseThrow().selectedCandidateKey(), "Replacement goal did not publish fresh evidence.");
		PhantomAssertions.assertEquals(PhantomDecisionEngine.ReloadResult.RELOADED, fixture.engine.reload(1), "Explicit reload failed.");
		assertEvidenceReset(fixture.engine.find(1).orElseThrow(), "Goal reload");
		fixture.engine.accept(work(1, 1, 3, 2));
		PhantomAssertions.assertEquals("candidate.test", fixture.engine.find(1).orElseThrow().selectedCandidateKey(), "Reloaded goal did not publish fresh evidence.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.clearGoal(1), "Goal clear failed.");
		assertEvidenceReset(fixture.engine.find(1).orElseThrow(), "Goal clear");
		fixture.stop();
	}

	private void testActivityGenerationResetsEvidence() throws Exception
	{
		final BlockingHandler blocking = new BlockingHandler();
		final EngineFixture fixture = startedFixture(blocking, 1);
		final Thread worker = new Thread(() -> fixture.engine.accept(work(1, 1, 1, 0)), "t008a-evidence-activity");
		worker.start();
		blocking.awaitEntered();
		PhantomAssertions.assertEquals("candidate.test", fixture.engine.find(1).orElseThrow().selectedCandidateKey(), "Fixture did not publish in-flight decision evidence.");
		fixture.engine.accept(work(1, 2, 2, 1));
		assertEvidenceReset(fixture.engine.find(1).orElseThrow(), "Activity generation change");
		blocking.release();
		join(worker);
		fixture.stop();
	}

	private void testStopResetsEvidence()
	{
		final EngineFixture fixture = startedFixture(_ -> PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success"), 2);
		fixture.engine.accept(work(1, 1, 1, 0));
		PhantomAssertions.assertEquals("candidate.test", fixture.engine.find(1).orElseThrow().selectedCandidateKey(), "Fixture did not publish decision evidence.");
		fixture.engine.beginStop();
		assertEvidenceReset(fixture.engine.find(1).orElseThrow(), "Stop cancellation");
		PhantomAssertions.assertTrue(fixture.engine.finishStop(), "Evidence fixture did not stop.");
	}

	private static void assertEvidenceReset(PhantomDecisionEngine.RuntimeSnapshot snapshot, String boundary)
	{
		PhantomAssertions.assertEquals(null, snapshot.selectedCandidateKey(), boundary + " retained selected candidate evidence.");
		PhantomAssertions.assertEquals(-1, snapshot.selectedScore(), boundary + " retained selected score evidence.");
		PhantomAssertions.assertTrue(snapshot.topCandidateEvaluations().isEmpty(), boundary + " retained candidate explanations.");
		PhantomAssertions.assertEquals(null, snapshot.lastResult(), boundary + " retained the previous step result.");
	}

	private static PhantomGoal goal(long revision)
	{
		return new PhantomGoal(1, "goal.test", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("subject", "A"), new PhantomDomainRef("target", "B"), 10, 2, "method.test", List.of(new PhantomDomainRef("source", "B"), new PhantomDomainRef("source", "A")), new PhantomDomainRef("anchor", "C"), "purpose.test", 500, 20, 30, 0, Map.of("constraint.z", 2L, "constraint.a", 1L), "reason.test", revision);
	}

	private static PhantomPlanStep step(int index)
	{
		return new PhantomPlanStep(index, "action.test", null, Map.of("argument.test", 1L), 1000, 2, "reason.test");
	}

	private static PhantomPlan plan(String candidateKey, long goalId, long logicalNowNanos, int steps)
	{
		return plan(candidateKey, goalId, logicalNowNanos, steps, 1000);
	}

	private static PhantomPlan plan(String candidateKey, long goalId, long logicalNowNanos, int steps, long timeoutMillis)
	{
		final List<PhantomPlanStep> planSteps = new ArrayList<>();
		for (int index = 0; index < steps; index++)
		{
			planSteps.add(step(index));
		}
		return new PhantomPlan(1, goalId, candidateKey, planSteps, timeoutMillis, logicalNowNanos);
	}

	private static PhantomDecisionCandidate candidate(String key, int score, org.l2jmobius.gameserver.phantoms.decision.PhantomPlanFactory factory)
	{
		return candidate(key, score, 0, factory);
	}

	private static PhantomDecisionCandidate candidate(String key, int score, int threshold, org.l2jmobius.gameserver.phantoms.decision.PhantomPlanFactory factory)
	{
		return new PhantomDecisionCandidate(key, Set.of("goal.test"), Set.of(PhantomActivityState.WARM), List.of(), List.of(new PhantomWeightedConsideration("score.test", 1, _ -> new PhantomConsideration.Evaluation(score, "score.test"))), threshold, factory);
	}

	private static PhantomDecisionCandidate weightedCandidate(String key, PhantomConsideration consideration)
	{
		return new PhantomDecisionCandidate(key, Set.of("goal.test"), Set.of(PhantomActivityState.WARM), List.of(), List.of(new PhantomWeightedConsideration("score.test", 1, consideration)), 0, context -> plan(key, context.goal().goalId(), context.logicalNowNanos(), 1));
	}

	private static PhantomPlanningContext planning()
	{
		return new PhantomPlanningContext(1, goal(0), PhantomCapabilitySet.empty(), PhantomActivityState.WARM, 0, 1);
	}

	private static PhantomActivityWorkItem work(long profileId, long activityGeneration, long tickSequence, long logicalNowNanos)
	{
		return new PhantomActivityWorkItem(profileId, PhantomActivityState.WARM, activityGeneration, tickSequence, logicalNowNanos, PhantomActivityOverloadLevel.NORMAL);
	}

	private static EngineFixture fixture(PhantomStepHandler handler, int steps)
	{
		return fixture(handler, steps, 1000);
	}

	private static EngineFixture fixture(PhantomStepHandler handler, int steps, long planTimeoutMillis)
	{
		return fixture(handler, steps, planTimeoutMillis, 1000);
	}

	private static EngineFixture fixture(PhantomStepHandler handler, int steps, long planTimeoutMillis, long stepTimeoutMillis)
	{
		return fixture(handler, steps, planTimeoutMillis, stepTimeoutMillis, PhantomDecisionEngine.DecisionAdmission.allowAll());
	}

	private static EngineFixture fixture(PhantomStepHandler handler, int steps, long planTimeoutMillis, long stepTimeoutMillis, PhantomDecisionEngine.DecisionAdmission admission)
	{
		final InMemoryGoalStore store = new InMemoryGoalStore();
		store.profiles.add(1L);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		candidates.register(candidate("candidate.test", 1000, context ->
		{
			final List<PhantomPlanStep> planSteps = new ArrayList<>();
			for (int index = 0; index < steps; index++)
			{
				planSteps.add(new PhantomPlanStep(index, "action.test", null, Map.of("argument.test", 1L), stepTimeoutMillis, 2, "reason.test"));
			}
			return new PhantomPlan(1, context.goal().goalId(), "candidate.test", planSteps, planTimeoutMillis, context.logicalNowNanos());
		}));
		candidates.seal();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		handlers.register("action.test", handler);
		handlers.seal();
		final PhantomDecisionEngine engine = new PhantomDecisionEngine(store, candidates, handlers, new PhantomMetrics(), 4, null, admission);
		engine.start();
		return new EngineFixture(engine, store);
	}

	private static EngineFixture startedFixture(PhantomStepHandler handler, int steps)
	{
		return startedFixture(handler, steps, 1000);
	}

	private static EngineFixture startedFixture(PhantomStepHandler handler, int steps, long planTimeoutMillis)
	{
		return startedFixture(handler, steps, planTimeoutMillis, 1000);
	}

	private static EngineFixture startedFixture(PhantomStepHandler handler, int steps, long planTimeoutMillis, long stepTimeoutMillis)
	{
		final EngineFixture fixture = fixture(handler, steps, planTimeoutMillis, stepTimeoutMillis);
		PhantomAssertions.assertEquals(AttachResult.ATTACHED, fixture.engine.attach(1), "Fixture profile did not attach.");
		PhantomAssertions.assertEquals(MutationResult.APPLIED, fixture.engine.insertGoal(1, goal(0)), "Fixture goal did not insert.");
		return fixture;
	}

	private static void join(Thread thread) throws InterruptedException
	{
		thread.join(TimeUnit.SECONDS.toMillis(2));
		PhantomAssertions.assertFalse(thread.isAlive(), "Decision worker thread did not quiesce.");
	}

	private record EngineFixture(PhantomDecisionEngine engine, InMemoryGoalStore store)
	{
		private void stop()
		{
			engine.beginStop();
			PhantomAssertions.assertTrue(engine.finishStop(), "Decision fixture did not stop.");
		}
	}

	private static final class BlockingHandler implements PhantomStepHandler
	{
		private final CountDownLatch entered = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicBoolean cancelled = new AtomicBoolean();

		@Override
		public PhantomStepResult execute(org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext context)
		{
			entered.countDown();
			try
			{
				if (!release.await(2, TimeUnit.SECONDS))
				{
					throw new AssertionError("Timed out waiting to release handler.");
				}
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
				throw new AssertionError(e);
			}
			final PhantomCancellationToken token = context.cancellationToken();
			cancelled.set(token.isCancelled());
			return PhantomStepResult.of(PhantomStepResult.Type.SUCCESS, "step.success");
		}

		private void awaitEntered() throws InterruptedException
		{
			PhantomAssertions.assertTrue(entered.await(2, TimeUnit.SECONDS), "Blocking handler did not start.");
		}

		private void release()
		{
			release.countDown();
		}
	}

	private static final class InMemoryGoalStore implements PhantomGoalStore
	{
		private final Set<Long> profiles = new HashSet<>();
		private final Map<Long, StoredGoal> goals = new HashMap<>();
		private int reads;
		private int writes;
		private boolean conflictNextReplace;

		@Override
		public synchronized boolean profileExists(long profileId)
		{
			return profiles.contains(profileId);
		}

		@Override
		public synchronized Optional<StoredGoal> load(long profileId)
		{
			reads++;
			return Optional.ofNullable(goals.get(profileId));
		}

		@Override
		public synchronized StoredGoal insert(long profileId, PhantomGoal goal)
		{
			if (goals.containsKey(profileId))
			{
				throw new ConcurrentModificationException("duplicate");
			}
			writes++;
			final StoredGoal stored = new StoredGoal(goal, 0);
			goals.put(profileId, stored);
			return stored;
		}

		@Override
		public synchronized StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			if (conflictNextReplace)
			{
				conflictNextReplace = false;
				throw new ConcurrentModificationException("injected");
			}
			final StoredGoal current = goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion))
			{
				throw new ConcurrentModificationException("stale");
			}
			writes++;
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
			writes++;
			goals.remove(profileId);
		}
	}
}

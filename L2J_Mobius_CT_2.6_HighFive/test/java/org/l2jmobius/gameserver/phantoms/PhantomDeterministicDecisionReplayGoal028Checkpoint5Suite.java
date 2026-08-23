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
package org.l2jmobius.gameserver.phantoms;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.phantoms.PhantomDecisionReplay.Bundle;
import org.l2jmobius.gameserver.phantoms.PhantomDecisionReplay.Frame;
import org.l2jmobius.gameserver.phantoms.PhantomDecisionReplay.ReplayStatus;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.DecisionView;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.Health;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorReplayCode;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.CandidateEvaluation;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.EvaluationStatus;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomDeterministicDecisionReplayGoal028Checkpoint5Suite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "deterministic-decision-replay-goal028cp5";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-bounds-ring-eviction-and-first-age", _ -> testBoundsAndEviction());
		registry.add("02-shared-health-replay-and-tamper", _ -> testHealthReplay());
		registry.add("03-candidate-tri-state", _ -> testCandidateTriState());
		registry.add("04-canonical-digest-stability", this::testDigestStability);
		registry.add("05-capture-drain-run-clear", _ -> testCaptureDrainRun());
		registry.add("06-failed-capture-retention-and-replace", _ -> testFailedCaptureRetention());
		registry.add("07-admin-and-no-action-contract", this::testAdminAndNoAction);
	}

	private static void testBoundsAndEviction()
	{
		final AtomicLong now = new AtomicLong();
		final PhantomSelectedDecisionTrace trace = trace(now);
		trace.select(101, runtime(view(101, 0, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, "reason.a", eligible("candidate.a", 700))));
		for (int sequence = 1; sequence <= 70; sequence++)
		{
			now.set(sequence * 1_000_000_000L);
			trace.observe(PhantomActivityState.ACTIVE, runtime(view(101, sequence, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, "reason.a", eligible("candidate.a", 700))), now.get());
		}
		final Bundle frozen = trace.captureReplay().bundle();
		PhantomAssertions.assertEquals(64, frozen.frames().size(), "Replay capture did not retain exactly the capacity-64 window.");
		PhantomAssertions.assertEquals(0L, frozen.frames().get(0).relativeLogicalNanos(), "First retained frame was not normalized to zero.");
		PhantomAssertions.assertEquals(7_000_000_000L, frozen.frames().get(0).capturedUnchangedAgeNanos(), "Evicted prefix age evidence was lost.");
		PhantomAssertions.assertEquals(70_000_000_000L, frozen.frames().get(63).capturedUnchangedAgeNanos(), "Newest retained unchanged age drifted.");
		PhantomAssertions.assertTrue(frozen.frames().stream().allMatch(frame -> (frame.decision().profileId() == 101) && (frame.decision().topCandidates().size() <= 8)), "Replay bundle exceeded profile or explanation bounds.");
		trace.select(202, runtime(view(202, 1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.b", 600, 0, "reason.b", eligible("candidate.b", 600))));
		PhantomAssertions.assertEquals(0, trace.snapshot().history().size(), "Selection switch retained live replay metadata/history.");
		PhantomAssertions.assertEquals(64, frozen.frames().size(), "Immutable frozen bundle changed after live selection switch.");
	}
	private static void testHealthReplay()
	{
		final AtomicLong now = new AtomicLong();
		final PhantomSelectedDecisionTrace trace = trace(now);
		trace.select(1, runtime(view(1, 0, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, "reason.a", eligible("candidate.a", 700))));
		observe(trace, now, 4_999_000_000L, view(1, 1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, "reason.a", eligible("candidate.a", 700)));
		observe(trace, now, 5_000_000_000L, view(1, 2, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, "reason.a", eligible("candidate.a", 700)));
		observe(trace, now, 30_000_000_000L, view(1, 3, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, "reason.a", eligible("candidate.a", 700)));
		observe(trace, now, 31_000_000_000L, view(1, 4, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 1, "reason.a", eligible("candidate.a", 700)));
		observe(trace, now, 151_000_000_000L, view(1, 5, RuntimeState.WAITING_RETRY, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 1, "reason.retry", eligible("candidate.a", 700)));
		observe(trace, now, 152_000_000_000L, view(1, 6, RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD, PhantomGoalStatus.ACTIVE, null, -1, -1, "persistence.conflict", List.of()));
		final Bundle bundle = trace.captureReplay().bundle();
		final var replay = PhantomDecisionReplay.replay(bundle);
		PhantomAssertions.assertEquals(ReplayStatus.PASS, replay.status(), "Live health evidence did not replay.");
		PhantomAssertions.assertEquals(1, replay.firstSlowFrame(), "Exact slow threshold frame mismatch.");
		PhantomAssertions.assertEquals(2, replay.firstStuckFrame(), "Exact stuck threshold frame mismatch.");
		PhantomAssertions.assertEquals(5, replay.firstAttentionFrame(), "Persistence attention frame mismatch.");
		PhantomAssertions.assertEquals(Health.ATTENTION, replay.finalHealth(), "Final shared health mismatch.");
		PhantomAssertions.assertEquals(Health.WAITING, bundle.frames().get(4).capturedHealth(), "WAITING_RETRY escalated in captured metadata.");

		final List<Frame> ageTampered = new ArrayList<>(bundle.frames());
		final Frame slow = ageTampered.get(1);
		ageTampered.set(1, new Frame(slow.relativeLogicalNanos(), slow.capturedUnchangedAgeNanos() + 1, slow.capturedHealth(), slow.decision()));
		final var ageFailure = PhantomDecisionReplay.replay(copy(bundle, ageTampered));
		PhantomAssertions.assertEquals(ReplayStatus.FAIL, ageFailure.status(), "Tampered age replay passed.");
		PhantomAssertions.assertEquals(1, ageFailure.firstFailureFrame(), "Tampered age did not report first failing frame.");

		final List<Frame> healthTampered = new ArrayList<>(bundle.frames());
		final Frame stuck = healthTampered.get(2);
		healthTampered.set(2, new Frame(stuck.relativeLogicalNanos(), stuck.capturedUnchangedAgeNanos(), Health.SLOW, stuck.decision()));
		final var healthFailure = PhantomDecisionReplay.replay(copy(bundle, healthTampered));
		PhantomAssertions.assertEquals(ReplayStatus.FAIL, healthFailure.status(), "Tampered health replay passed.");
		PhantomAssertions.assertEquals(2, healthFailure.firstFailureFrame(), "Tampered health did not report first failing frame.");
	}

	private static void testCandidateTriState()
	{
		final var verified = PhantomDecisionReplay.replay(single(view(1, 1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, "reason.a", List.of(candidate("candidate.a", 700, EvaluationStatus.ELIGIBLE), candidate("candidate.b", 600, EvaluationStatus.ELIGIBLE)))));
		PhantomAssertions.assertEquals(ReplayStatus.PASS, verified.status(), "Visible consistent winner did not verify.");
		PhantomAssertions.assertEquals(1, verified.candidateVerified(), "Verified candidate count mismatch.");

		final var outranked = PhantomDecisionReplay.replay(single(view(1, 1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.b", 600, 0, "reason.a", List.of(candidate("candidate.a", 700, EvaluationStatus.ELIGIBLE), candidate("candidate.b", 600, EvaluationStatus.ELIGIBLE)))));
		PhantomAssertions.assertEquals(ReplayStatus.FAIL, outranked.status(), "Visible outranker did not fail replay.");
		PhantomAssertions.assertEquals(1, outranked.candidateMismatch(), "Candidate mismatch count drifted.");

		final var outsideTop = PhantomDecisionReplay.replay(single(view(1, 1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.z", 900, 0, "reason.a", eligible("candidate.a", 700))));
		PhantomAssertions.assertEquals(ReplayStatus.PASS, outsideTop.status(), "Selected candidate outside top8 was treated as mismatch.");
		PhantomAssertions.assertEquals(1, outsideTop.candidateUnverifiable(), "Outside-top8 evidence was not unverifiable.");

		final var nullEligible = PhantomDecisionReplay.replay(single(view(1, 1, RuntimeState.NO_CANDIDATE, PhantomGoalStatus.ACTIVE, null, -1, -1, "candidate.none", eligible("candidate.a", 700))));
		PhantomAssertions.assertEquals(ReplayStatus.FAIL, nullEligible.status(), "Null selection with visible eligible candidate did not fail.");
		final var nullBlocked = PhantomDecisionReplay.replay(single(view(1, 1, RuntimeState.NO_CANDIDATE, PhantomGoalStatus.ACTIVE, null, -1, -1, "candidate.none", List.of(candidate("candidate.a", -1, EvaluationStatus.BLOCKED)))));
		PhantomAssertions.assertEquals(ReplayStatus.PASS, nullBlocked.status(), "Null selection without visible eligible candidate was treated as mismatch.");
		PhantomAssertions.assertEquals(1, nullBlocked.candidateUnverifiable(), "Null/no-eligible evidence was not unverifiable.");

		final var noncanonical = PhantomDecisionReplay.replay(single(view(1, 1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, "reason.a", List.of(candidate("candidate.b", 600, EvaluationStatus.ELIGIBLE), candidate("candidate.a", 700, EvaluationStatus.ELIGIBLE)))));
		PhantomAssertions.assertEquals(ReplayStatus.FAIL, noncanonical.status(), "Noncanonical explanation order passed replay.");
	}
	private void testDigestStability(PhantomTestContext context) throws Exception
	{
		final Bundle bundle = single(view(7, 3, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 2, "reason.a", eligible("candidate.a", 700)));
		final var first = PhantomDecisionReplay.replay(bundle);
		final var second = PhantomDecisionReplay.replay(bundle);
		PhantomAssertions.assertEquals(first, second, "Same frozen bundle did not replay identically twice.");
		PhantomAssertions.assertTrue(Pattern.matches("[0-9a-f]{64}", first.digest()), "Digest is not canonical lowercase SHA-256.");
		final Bundle changed = single(view(7, 3, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 2, "reason.changed", eligible("candidate.a", 700)));
		PhantomAssertions.assertFalse(first.digest().equals(PhantomDecisionReplay.replay(changed).digest()), "Changed structural field did not change canonical digest.");
		final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomDecisionReplay.java"));
		PhantomAssertions.assertTrue(source.contains("DataOutputStream") && source.contains("SHA-256") && source.contains("StandardCharsets.UTF_8"), "Canonical fixed-order binary digest primitives are missing.");
		for (String forbidden : List.of("ObjectOutputStream", "ObjectInputStream", ".hashCode(", ".toString(", "Json", "Map<"))
		{
			PhantomAssertions.assertFalse(source.contains(forbidden), "Replay digest admitted forbidden encoding primitive " + forbidden);
		}
	}

	private static void testCaptureDrainRun()
	{
		resetProcess();
		PhantomSystem.configureOperatorReplayForTesting();
		try
		{
			populateConfiguredTrace(41, "reason.capture.a");
			final var capture = PhantomSystem.operatorReplayCapture();
			PhantomAssertions.assertEquals(OperatorReplayCode.CAPTURED, capture.code(), "Operator capture failed.");
			final String digest = capture.digest();
			PhantomAssertions.assertEquals(OperatorControlCode.DRAINED, PhantomSystem.operatorDrain().code(), "Capture fixture did not drain.");
			PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Drain retained configured owner.");
			final var replay = PhantomSystem.operatorReplayRun();
			PhantomAssertions.assertEquals(OperatorReplayCode.REPLAY_PASS, replay.code(), "Frozen replay did not run after configured-instance removal.");
			PhantomAssertions.assertEquals(digest, replay.digest(), "Capture->drain->run digest drifted.");
			PhantomAssertions.assertEquals(41L, replay.profileId(), "Capture->drain->run profile drifted.");
			PhantomAssertions.assertEquals(OperatorReplayCode.CLEARED, PhantomSystem.operatorReplayClear().code(), "Replay clear failed after drain.");
			PhantomAssertions.assertEquals(OperatorReplayCode.NO_CAPTURE, PhantomSystem.operatorReplayRun().code(), "Cleared replay slot remained runnable.");
		}
		finally
		{
			resetProcess();
		}
	}

	private static void testFailedCaptureRetention()
	{
		resetProcess();
		PhantomSystem.configureOperatorReplayForTesting();
		try
		{
			populateConfiguredTrace(51, "reason.capture.a");
			final var captureA = PhantomSystem.operatorReplayCapture();
			PhantomAssertions.assertEquals(OperatorReplayCode.CAPTURED, captureA.code(), "Initial replay capture failed.");
			PhantomSystem.operatorDrain();
			PhantomSystem.resetOperatorModeForTesting();
			PhantomSystem.configureOperatorReplayForTesting();
			PhantomAssertions.assertEquals(OperatorReplayCode.NO_SELECTION, PhantomSystem.operatorReplayCapture().code(), "Missing-selection capture returned the wrong status.");
			final var retained = PhantomSystem.operatorReplayRun();
			PhantomAssertions.assertEquals(captureA.digest(), retained.digest(), "Failed capture replaced prior valid bundle.");
			populateConfiguredTrace(52, "reason.capture.b");
			final var captureB = PhantomSystem.operatorReplayCapture();
			PhantomAssertions.assertEquals(OperatorReplayCode.CAPTURED, captureB.code(), "Replacement replay capture failed.");
			PhantomAssertions.assertFalse(captureA.digest().equals(captureB.digest()), "Successful capture did not replace prior bundle.");
			PhantomAssertions.assertEquals(52L, PhantomSystem.operatorReplayRun().profileId(), "Replay slot did not expose replacement profile.");
			PhantomSystem.operatorReplayClear();
			PhantomAssertions.assertEquals(OperatorReplayCode.NO_CAPTURE, PhantomSystem.operatorReplayRun().code(), "Clear did not remove replacement bundle.");
		}
		finally
		{
			resetProcess();
		}
	}
	private void testAdminAndNoAction(PhantomTestContext context) throws Exception
	{
		final String admin = Files.readString(context.moduleRoot().resolve("dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java"));
		final String replay = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomDecisionReplay.java"));
		final String system = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"));
		final String engine = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java"));
		PhantomAssertions.assertTrue(admin.contains("arguments.equals(\"replay capture\")") && admin.contains("arguments.equals(\"replay run\")") && admin.contains("arguments.equals(\"replay clear\")"), "Exact replay admin commands are missing.");
		PhantomAssertions.assertTrue(admin.contains("arguments.equals(\"enable\")") && admin.contains("arguments.equals(\"drain\")") && admin.contains("arguments.equals(\"disable\")") && admin.contains("arguments.equals(\"status\")") && admin.contains("arguments.startsWith(\"trace \")") && admin.contains("arguments.startsWith(\"economy \")"), "Existing operator commands drifted.");
		final int renderStart = admin.indexOf("private static void sendReplay");
		final int renderEnd = admin.indexOf("private static void sendControl", renderStart);
		final String replayRender = admin.substring(renderStart, renderEnd);
		PhantomAssertions.assertFalse(replayRender.contains("for (") || replayRender.contains("history()") || replayRender.contains("frames()"), "Replay admin rendering dumps frame evidence.");
		for (String forbidden : List.of(".plan(", ".execute(", "UtilitySelector.select(", ".reload(", ".setGoal(", ".clearGoal(", "operatorEnable(", "operatorDrain(", "operatorDisable(", "Economy", "Navigation", "Combat", "Chat", "Repository", "new Thread", "Timer", "Scheduled", ".poll("))
		{
			PhantomAssertions.assertFalse(replay.contains(forbidden), "Pure replay source contains forbidden action/runtime token " + forbidden);
		}
		final int operatorStart = system.indexOf("public static synchronized OperatorReplayResult operatorReplayCapture()");
		final int operatorEnd = system.indexOf("public static synchronized PhantomSelectedDecisionTrace.SelectionStatus selectOperatorTrace", operatorStart);
		final String operatorReplay = system.substring(operatorStart, operatorEnd);
		for (String forbidden : List.of(".plan(", ".execute(", ".reload(", ".setGoal(", ".clearGoal(", "operatorEnable(", "operatorDrain(", "operatorDisable(", "_economic", "_navigation", "_combat", "_conversation", "Repository", "Thread", "Timer", "Scheduled", ".poll("))
		{
			PhantomAssertions.assertFalse(operatorReplay.contains(forbidden), "Operator replay facade contains forbidden action token " + forbidden);
		}
		final int prefilter = engine.indexOf("_observer.interested(workItem.profileId())");
		final int snapshot = engine.indexOf("snapshot = snapshotLocked(slot)", prefilter);
		PhantomAssertions.assertTrue((prefilter >= 0) && (snapshot > prefilter), "Goal028A prefilter no longer precedes RuntimeSnapshot allocation.");
	}

	private static PhantomSelectedDecisionTrace trace(AtomicLong now)
	{
		return new PhantomSelectedDecisionTrace(true, 64, 5_000, 30_000, now::get);
	}

	private static void observe(PhantomSelectedDecisionTrace trace, AtomicLong now, long logicalNanos, DecisionView view)
	{
		now.set(logicalNanos);
		trace.observe(PhantomActivityState.ACTIVE, runtime(view), logicalNanos);
	}

	private static void populateConfiguredTrace(long profileId, String reason)
	{
		final PhantomSelectedDecisionTrace trace = PhantomSystem.configuredSelectedTraceForTesting();
		final DecisionView view = view(profileId, 1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, "candidate.a", 700, 0, reason, eligible("candidate.a", 700));
		trace.select(profileId, runtime(view));
		trace.observe(PhantomActivityState.ACTIVE, runtime(view), System.nanoTime());
	}

	private static org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeSnapshot runtime(DecisionView view)
	{
		return new org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeSnapshot(view.profileId(), view.goalId(), view.goalType(), view.goalRevision(), view.goalStatus(), view.runtimeState(), view.decisionSequence(), view.candidateKey(), view.score(), view.planId(), view.step(), view.attempt(), view.lastResult(), view.reasonKey(), view.topCandidates(), false, false, 0, null, 4, 5, 6);
	}

	private static Bundle single(DecisionView view)
	{
		final Health health = PhantomDecisionHealthModel.classify(view, 0, true, 5_000, 30_000);
		return new Bundle(PhantomDecisionReplay.SCHEMA_VERSION, view.profileId(), 5_000, 30_000, List.of(new Frame(0, 0, health, view)));
	}

	private static Bundle copy(Bundle source, List<Frame> frames)
	{
		return new Bundle(source.schemaVersion(), source.profileId(), source.slowThresholdMillis(), source.stuckThresholdMillis(), frames);
	}

	private static DecisionView view(long profileId, long sequence, RuntimeState state, PhantomGoalStatus goalStatus, String candidateKey, int score, int step, String reason, List<CandidateEvaluation> candidates)
	{
		return new DecisionView(PhantomActivityState.ACTIVE, profileId, goalStatus == null ? 0 : 11, goalStatus == null ? null : "goal.test", goalStatus == null ? -1 : 3, goalStatus, state, sequence, candidateKey, score, candidateKey == null ? 0 : 21, step, 0, null, reason, candidates);
	}

	private static List<CandidateEvaluation> eligible(String key, int score)
	{
		return List.of(candidate(key, score, EvaluationStatus.ELIGIBLE));
	}

	private static CandidateEvaluation candidate(String key, int score, EvaluationStatus status)
	{
		return new CandidateEvaluation(key, score, status, status == EvaluationStatus.ELIGIBLE ? "candidate.ready" : "candidate.blocked");
	}

	private static void resetProcess()
	{
		PhantomSystem.releaseOperatorShutdownFailureForTesting();
		if (PhantomSystem.hasConfiguredInstance())
		{
			PhantomSystem.operatorDisable();
		}
		if (PhantomSystem.hasConfiguredInstance())
		{
			throw new AssertionError("Replay fixture retained a configured owner.");
		}
		PhantomSystem.operatorReplayClear();
		PhantomSystem.resetOperatorModeForTesting();
	}
}
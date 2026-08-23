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

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.Health;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeSnapshot;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.RuntimeState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.CandidateEvaluation;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.EvaluationStatus;

public final class PhantomSelectedSlowStuckDiagnosticsSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "selected-slow-stuck-diagnostics";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-default-thresholds-and-source-compatible-observer", this::testDefaultsAndObserverCompatibility);
		registry.add("02-rising-sequence-does-not-fake-progress", _ -> testSlowAndStuckAging());
		registry.add("03-structural-progress-resets-baseline", _ -> testStructuralProgressReset());
		registry.add("04-waiting-and-persistence-attention", _ -> testWaitingAndAttention());
		registry.add("05-idle-selection-reset-and-detach", _ -> testIdleSelectionAndDetach());
		registry.add("06-bounds-admin-and-no-remediation-contract", this::testStaticContracts);
	}

	private void testDefaultsAndObserverCompatibility(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(5_000L, PhantomSelectedDecisionTrace.SLOW_THRESHOLD_MILLIS, "Default slow threshold changed.");
		PhantomAssertions.assertEquals(30_000L, PhantomSelectedDecisionTrace.STUCK_THRESHOLD_MILLIS, "Default stuck threshold changed.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomSelectedDecisionTrace(true, 64, 0, 30_000, () -> 0), "Zero slow threshold was accepted.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomSelectedDecisionTrace(true, 64, 30_000, 30_000, () -> 0), "Non-ordered thresholds were accepted.");
		final AtomicInteger calls = new AtomicInteger();
		final PhantomDecisionEngine.DecisionObserver legacy = (activity, snapshot) -> calls.incrementAndGet();
		legacy.onDecision(PhantomActivityState.WARM, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 1, 1, "candidate.a", 0, "reason.a"), 123_000_000L);
		PhantomAssertions.assertEquals(1, calls.get(), "Default richer callback did not delegate to the existing abstract two-argument SAM.");
		final String engine = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/decision/PhantomDecisionEngine.java"));
		PhantomAssertions.assertTrue(engine.contains("_observer.onDecision(workItem.effectiveState(), snapshot, workItem.logicalNowNanos())"), "DecisionEngine did not forward work-item logical time.");
		final int prefilter = engine.indexOf("_observer.interested(workItem.profileId())");
		final int snapshotBuild = engine.indexOf("snapshot = snapshotLocked(slot)", prefilter);
		PhantomAssertions.assertTrue((prefilter >= 0) && (snapshotBuild > prefilter), "Goal028A interested prefilter no longer precedes RuntimeSnapshot allocation.");
	}

	private static void testSlowAndStuckAging()
	{
		final AtomicLong now = new AtomicLong();
		final PhantomSelectedDecisionTrace trace = trace(now);
		trace.select(1, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 1, 1, "candidate.a", 0, "reason.a"));
		now.set(4_999_000_000L);
		trace.observe(PhantomActivityState.ACTIVE, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 2, 1, "candidate.a", 0, "reason.a"), now.get());
		PhantomAssertions.assertEquals(Health.HEALTHY, trace.snapshot().health(), "Unchanged structure became slow before the threshold.");
		now.set(5_000_000_000L);
		trace.observe(PhantomActivityState.WARM, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 3, 1, "candidate.a", 0, "reason.a"), now.get());
		PhantomAssertions.assertEquals(Health.SLOW, trace.snapshot().health(), "Rising decisionSequence incorrectly reset the slow baseline.");
		trace.select(1, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 3, 1, "candidate.a", 0, "reason.a"));
		PhantomAssertions.assertEquals(Health.SLOW, trace.snapshot().health(), "Repeated selection of the same profile reset unchanged progress age.");
		now.set(30_000_000_000L);
		trace.observe(PhantomActivityState.BACKGROUND, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 4, 1, "candidate.a", 0, "reason.a"), now.get());
		PhantomAssertions.assertEquals(Health.STUCK, trace.snapshot().health(), "Rising decisionSequence incorrectly prevented stuck aging.");
		PhantomAssertions.assertEquals(30_000L, trace.snapshot().ageMillis(), "Logical monotonic age did not reach the exact stuck threshold.");
	}

	private static void testStructuralProgressReset()
	{
		final AtomicLong now = new AtomicLong();
		final PhantomSelectedDecisionTrace trace = trace(now);
		trace.select(1, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 1, 1, "candidate.a", 0, "reason.a"));
		now.set(8_000_000_000L);
		PhantomAssertions.assertEquals(Health.SLOW, trace.snapshot().health(), "Fixture did not age to slow before structural progress.");
		trace.observe(PhantomActivityState.ACTIVE, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 2, 1, "candidate.a", 1, "reason.a"), now.get());
		PhantomAssertions.assertEquals(Health.HEALTHY, trace.snapshot().health(), "Step progress did not reset the baseline.");
		PhantomAssertions.assertEquals(0L, trace.snapshot().ageMillis(), "Structural progress retained the old age.");
		now.set(12_000_000_000L);
		trace.observe(PhantomActivityState.ACTIVE, snapshot(1, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 3, 2, "candidate.a", 1, "reason.a"), now.get());
		PhantomAssertions.assertEquals(0L, trace.snapshot().ageMillis(), "Goal revision progress did not reset the baseline.");
	}

	private static void testWaitingAndAttention()
	{
		final AtomicLong now = new AtomicLong();
		final PhantomSelectedDecisionTrace waiting = trace(now);
		waiting.select(1, snapshot(1, RuntimeState.WAITING_RETRY, PhantomGoalStatus.ACTIVE, 1, 1, "candidate.a", 0, "reason.retry"));
		now.set(120_000_000_000L);
		PhantomAssertions.assertEquals(Health.WAITING, waiting.snapshot().health(), "WAITING_RETRY escalated with age.");
		PhantomAssertions.assertEquals(120_000L, waiting.snapshot().ageMillis(), "Expected waiting age was hidden.");
		final PhantomSelectedDecisionTrace conflict = trace(now);
		conflict.select(2, snapshot(2, RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD, PhantomGoalStatus.ACTIVE, 1, 1, null, -1, "persistence.conflict"));
		PhantomAssertions.assertEquals(Health.ATTENTION, conflict.snapshot().health(), "Persistence conflict did not require immediate attention.");
		final PhantomSelectedDecisionTrace failure = trace(now);
		failure.select(3, snapshot(3, RuntimeState.PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD, PhantomGoalStatus.ACTIVE, 1, 1, null, -1, "persistence.failure"));
		PhantomAssertions.assertEquals(Health.ATTENTION, failure.snapshot().health(), "Persistence failure did not require immediate attention.");
	}

	private static void testIdleSelectionAndDetach()
	{
		final AtomicLong now = new AtomicLong();
		final PhantomSelectedDecisionTrace trace = trace(now);
		trace.select(1, snapshot(1, RuntimeState.NO_GOAL, null, 1, 0, null, -1, "goal.none"));
		now.set(60_000_000_000L);
		PhantomAssertions.assertEquals(Health.IDLE, trace.snapshot().health(), "No-goal profile escalated.");
		trace.select(1, snapshot(1, RuntimeState.TERMINAL, PhantomGoalStatus.COMPLETED, 2, 1, null, -1, "goal.completed"));
		PhantomAssertions.assertEquals(Health.IDLE, trace.snapshot().health(), "Terminal goal escalated.");
		trace.select(2, snapshot(2, RuntimeState.EXECUTING, PhantomGoalStatus.ACTIVE, 1, 1, "candidate.b", 0, "reason.b"));
		PhantomAssertions.assertEquals(Health.HEALTHY, trace.snapshot().health(), "Selection switch did not reset the baseline.");
		now.set(100_000_000_000L);
		PhantomAssertions.assertEquals(Health.IDLE, trace.snapshot(false).health(), "Detached selected profile remained live STUCK.");
		PhantomAssertions.assertFalse(trace.snapshot(false).attached(), "Detached selected profile was reported attached.");
		trace.clear();
		PhantomAssertions.assertEquals(Health.IDLE, trace.snapshot().health(), "Clear did not reset health.");
		PhantomAssertions.assertEquals(0L, trace.snapshot().ageMillis(), "Clear retained progress age.");
	}

	private void testStaticContracts(PhantomTestContext context) throws Exception
	{
		final String trace = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSelectedDecisionTrace.java"));
		final String healthModel = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomDecisionHealthModel.java"));
		final String admin = Files.readString(context.moduleRoot().resolve("dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java"));
		final int fingerprintStart = healthModel.indexOf("public record ProgressFingerprint");
		final String fingerprint = healthModel.substring(fingerprintStart);
		PhantomAssertions.assertTrue(fingerprintStart >= 0, "Bounded structural fingerprint is missing from the shared health model.");
		PhantomAssertions.assertFalse(fingerprint.contains("decisionSequence") || fingerprint.contains("activityState") || fingerprint.contains("goalType"), "Fingerprint admitted non-progress sequence/activity/type fields.");
		PhantomAssertions.assertTrue(fingerprint.contains("goalRevision") && fingerprint.contains("runtimeState") && fingerprint.contains("topCandidates"), "Fingerprint omitted required structural reason-view fields.");
		PhantomAssertions.assertTrue(trace.contains("PhantomDecisionHealthModel.fingerprint") && trace.contains("PhantomDecisionHealthModel.classify"), "Live trace does not use the shared replay health model.");
		PhantomAssertions.assertTrue(trace.contains("MAX_CAPACITY = 64") && trace.contains("Math.min(PhantomDecisionEngine.MAX_EXPLANATIONS"), "Selected trace or candidate explanation bounds drifted.");
		PhantomAssertions.assertFalse(trace.contains("new Thread") || trace.contains("Timer") || trace.contains("Scheduled") || trace.contains("poll(") || trace.contains("Thread.sleep"), "Diagnostics introduced active execution or sleeps.");
		for (String forbidden : List.of(".reload(", ".replan(", ".setGoal(", ".clearGoal(", ".operatorEnable(", ".operatorDrain(", ".operatorDisable("))
		{
			PhantomAssertions.assertFalse(trace.contains(forbidden), "Diagnostics contain forbidden remediation call " + forbidden);
		}
		PhantomAssertions.assertTrue(admin.contains("health=") && admin.contains("ageMs=") && admin.contains("slowMs=") && admin.contains("stuckMs="), "Status/trace compact output omitted health, age or thresholds.");
		PhantomAssertions.assertTrue(admin.contains("arguments.equals(\"enable\")") && admin.contains("arguments.equals(\"drain\")") && admin.contains("arguments.equals(\"disable\")"), "CP2 controls semantics surface drifted.");
	}

	private static PhantomSelectedDecisionTrace trace(AtomicLong now)
	{
		return new PhantomSelectedDecisionTrace(true, 64, 5_000, 30_000, now::get);
	}

	private static RuntimeSnapshot snapshot(long profileId, RuntimeState runtimeState, PhantomGoalStatus goalStatus, long decisionSequence, long goalRevision, String candidate, int step, String reason)
	{
		final List<CandidateEvaluation> candidates = candidate == null ? List.of() : List.of(new CandidateEvaluation(candidate, 700, EvaluationStatus.ELIGIBLE, "candidate.ready"));
		return new RuntimeSnapshot(profileId, goalStatus == null ? 0 : 11, goalStatus == null ? null : "goal.test", goalStatus == null ? -1 : goalRevision, goalStatus, runtimeState, decisionSequence, candidate, candidate == null ? -1 : 700, candidate == null ? 0 : 21, step, 0, null, reason, candidates, false, false, 0, null, 4, 5, 6);
	}
}

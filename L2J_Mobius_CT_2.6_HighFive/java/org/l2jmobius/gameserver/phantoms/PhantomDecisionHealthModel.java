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

import java.util.List;

import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.DecisionView;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.Health;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector.CandidateEvaluation;

/**
 * Pure structural progress and health rules shared by live diagnostics and replay.
 */
public final class PhantomDecisionHealthModel
{
	private PhantomDecisionHealthModel()
	{
	}

	public static void validateThresholds(long slowThresholdMillis, long stuckThresholdMillis)
	{
		if ((slowThresholdMillis <= 0) || (slowThresholdMillis >= stuckThresholdMillis))
		{
			throw new IllegalArgumentException("Decision health thresholds require 0 < slow < stuck.");
		}
	}

	public static ProgressFingerprint fingerprint(DecisionView view)
	{
		return new ProgressFingerprint(view.goalId(), view.goalRevision(), view.goalStatus(), view.runtimeState(), view.candidateKey(), view.score(), view.planId(), view.step(), view.attempt(), view.lastResult(), view.reasonKey(), view.topCandidates());
	}

	public static Health classify(DecisionView view, long unchangedAgeNanos, boolean attached, long slowThresholdMillis, long stuckThresholdMillis)
	{
		validateThresholds(slowThresholdMillis, stuckThresholdMillis);
		if ((view == null) || !attached)
		{
			return Health.IDLE;
		}
		if ((view.runtimeState() == PhantomDecisionEngine.RuntimeState.PERSISTENCE_CONFLICT_REQUIRES_EXPLICIT_RELOAD) || (view.runtimeState() == PhantomDecisionEngine.RuntimeState.PERSISTENCE_FAILURE_REQUIRES_EXPLICIT_RELOAD))
		{
			return Health.ATTENTION;
		}
		if ((view.goalId() <= 0) || (view.goalStatus() != PhantomGoalStatus.ACTIVE) || (view.runtimeState() == PhantomDecisionEngine.RuntimeState.NO_GOAL) || (view.runtimeState() == PhantomDecisionEngine.RuntimeState.TERMINAL))
		{
			return Health.IDLE;
		}
		if (view.runtimeState() == PhantomDecisionEngine.RuntimeState.WAITING_RETRY)
		{
			return Health.WAITING;
		}
		final long ageMillis = unchangedAgeNanos / 1_000_000L;
		if (ageMillis >= stuckThresholdMillis)
		{
			return Health.STUCK;
		}
		if (ageMillis >= slowThresholdMillis)
		{
			return Health.SLOW;
		}
		return Health.HEALTHY;
	}

	public record ProgressFingerprint(long goalId, long goalRevision, PhantomGoalStatus goalStatus, PhantomDecisionEngine.RuntimeState runtimeState, String candidateKey, int score, long planId, int step, int attempt, PhantomStepResult.Type lastResult, String reasonKey, List<CandidateEvaluation> topCandidates)
	{
		public ProgressFingerprint
		{
			topCandidates = List.copyOf(topCandidates);
		}
	}
}
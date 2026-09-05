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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;

/** Durable bounded ownership and interval cursor for causal Background catch-up. */
public record PhantomBackgroundCatchupState(Status status, String requestId, long deterministicSeed, long fromEpochMinute, long targetEpochMinute, long cursorEpochMinute, long planOrdinal, long intervalOrdinal, long generation, long knowledgeGeneration, long topologyGeneration, long goalId, long goalRevision, String planIdentity, int modelVersion, Hashes authorityHashes, String failureReason)
{
	public static final String COMPONENT_TYPE = "background.catchup";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_REQUEST_ID_LENGTH = 64;
	public static final int MAX_PLAN_IDENTITY_LENGTH = 64;
	public static final int MAX_FAILURE_REASON_LENGTH = 96;

	public PhantomBackgroundCatchupState
	{
		Objects.requireNonNull(status, "status");
		requestId = requireBounded(requestId, MAX_REQUEST_ID_LENGTH, "requestId");
		planIdentity = Objects.requireNonNullElse(planIdentity, "");
		failureReason = Objects.requireNonNullElse(failureReason, "");
		Objects.requireNonNull(authorityHashes, "authorityHashes");
		if ((fromEpochMinute < 0) || (targetEpochMinute <= fromEpochMinute) || (cursorEpochMinute < fromEpochMinute) || (cursorEpochMinute > targetEpochMinute) || (planOrdinal < 0) || (intervalOrdinal < 0) || (generation < 1) || (knowledgeGeneration < 1) || (topologyGeneration < 1) || (goalId < 0) || (goalRevision < 0) || (modelVersion < 1) || (planIdentity.length() > MAX_PLAN_IDENTITY_LENGTH) || (failureReason.length() > MAX_FAILURE_REASON_LENGTH))
		{
			throw new IllegalArgumentException("Invalid Background catch-up state.");
		}
		final boolean planned = goalId > 0;
		if (planned != !planIdentity.isEmpty())
		{
			throw new IllegalArgumentException("Catch-up goal and plan identity must be present together.");
		}
		if ((status == Status.PENDING) && (cursorEpochMinute != fromEpochMinute))
		{
			throw new IllegalArgumentException("Pending catch-up cannot have an advanced cursor.");
		}
		if ((status == Status.RUNNING) && (!planned || (cursorEpochMinute >= targetEpochMinute)))
		{
			throw new IllegalArgumentException("Running catch-up requires a plan and remaining intervals.");
		}
		if ((status == Status.COMPLETE) && (!planned || (cursorEpochMinute != targetEpochMinute)))
		{
			throw new IllegalArgumentException("Complete catch-up must end exactly at its target cursor.");
		}
		if ((status == Status.FAILED_REPLAN_REQUIRED) != !failureReason.isEmpty())
		{
			throw new IllegalArgumentException("Only replan-required catch-up may contain a failure reason.");
		}
	}

	public PhantomBackgroundCatchupState withPlan(long replacementGoalId, long replacementGoalRevision, long replacementPlanOrdinal, String replacementPlanIdentity, long replacementKnowledgeGeneration, long replacementTopologyGeneration)
	{
		return new PhantomBackgroundCatchupState(status == Status.PENDING ? Status.PENDING : Status.RUNNING, requestId, deterministicSeed, fromEpochMinute, targetEpochMinute, cursorEpochMinute, replacementPlanOrdinal, intervalOrdinal, generation, replacementKnowledgeGeneration, replacementTopologyGeneration, replacementGoalId, replacementGoalRevision, replacementPlanIdentity, modelVersion, authorityHashes, "");
	}

	public PhantomBackgroundCatchupState running()
	{
		if ((status != Status.PENDING) || (goalId <= 0))
		{
			throw new IllegalStateException("Only a planned pending catch-up can enter RUNNING.");
		}
		return new PhantomBackgroundCatchupState(Status.RUNNING, requestId, deterministicSeed, fromEpochMinute, targetEpochMinute, cursorEpochMinute, planOrdinal, intervalOrdinal, generation, knowledgeGeneration, topologyGeneration, goalId, goalRevision, planIdentity, modelVersion, authorityHashes, "");
	}

	public PhantomBackgroundCatchupState advanceTo(long nextCursorEpochMinute)
	{
		if ((nextCursorEpochMinute <= cursorEpochMinute) || (nextCursorEpochMinute > targetEpochMinute))
		{
			throw new IllegalArgumentException("Catch-up cursor must advance positively and remain bounded.");
		}
		final Status nextStatus = nextCursorEpochMinute == targetEpochMinute ? Status.COMPLETE : Status.RUNNING;
		return new PhantomBackgroundCatchupState(nextStatus, requestId, deterministicSeed, fromEpochMinute, targetEpochMinute, nextCursorEpochMinute, planOrdinal, Math.addExact(intervalOrdinal, 1), generation, knowledgeGeneration, topologyGeneration, goalId, goalRevision, planIdentity, modelVersion, authorityHashes, "");
	}

	public PhantomBackgroundCatchupState failed(String reason)
	{
		return new PhantomBackgroundCatchupState(Status.FAILED_REPLAN_REQUIRED, requestId, deterministicSeed, fromEpochMinute, targetEpochMinute, cursorEpochMinute, planOrdinal, intervalOrdinal, generation, knowledgeGeneration, topologyGeneration, goalId, goalRevision, planIdentity, modelVersion, authorityHashes, requireBounded(reason, MAX_FAILURE_REASON_LENGTH, "failureReason"));
	}

	public boolean owns(String candidateRequestId)
	{
		return requestId.equals(candidateRequestId);
	}

	public boolean blocksNormalOperation()
	{
		return status != Status.COMPLETE;
	}

	private static String requireBounded(String value, int maximumLength, String name)
	{
		if ((value == null) || value.isBlank() || (value.length() > maximumLength) || value.chars().anyMatch(Character::isISOControl))
		{
			throw new IllegalArgumentException(name + " is invalid.");
		}
		return value;
	}

	public enum Status
	{
		PENDING,
		RUNNING,
		COMPLETE,
		FAILED_REPLAN_REQUIRED
	}
}
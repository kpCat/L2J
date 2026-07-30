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
package org.l2jmobius.gameserver.phantoms.population;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.CreationResult;

/**
 * One explicit WARM-only semantic creation action for population.bootstrap.
 */
public final class PhantomPopulationDecision
{
	public static final String CANDIDATE_KEY = "candidate.population.bootstrap";
	public static final String ACTION_KEY = "population.create_character";
	private static final long STEP_TIMEOUT_MILLIS = 60_000;
	private static final long RETRY_MILLIS = 25;
	private final PhantomPopulationManager _manager;

	public PhantomPopulationDecision(PhantomPopulationManager manager)
	{
		_manager = Objects.requireNonNull(manager, "Population manager must not be null.");
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		registry.register(new PhantomDecisionCandidate(
			CANDIDATE_KEY,
			Set.of(PhantomPopulationManager.BOOTSTRAP_GOAL_TYPE),
			Set.of(PhantomActivityState.WARM),
			List.of(),
			List.of(new PhantomWeightedConsideration("score.population.bootstrap", 1, context -> new PhantomConsideration.Evaluation(_manager.find(context.profileId()).filter(snapshot -> snapshot.state().creationPending()).isPresent() ? 1000 : 0, "population.bootstrap.explicit"))),
			1000,
			this::plan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		registry.register(ACTION_KEY, context ->
		{
			if (context.cancellationToken().isCancelled())
			{
				return PhantomStepResult.of(Type.CANCELLED, "population.creation.cancelled");
			}
			if ((context.step().target() == null) || !"population.profile".equals(context.step().target().namespace()) || !Long.toString(context.profileId()).equals(context.step().target().key()))
			{
				return PhantomStepResult.of(Type.FAIL_GOAL, "population.creation.stale");
			}
			final CreationResult result = _manager.advanceCreation(context.profileId());
			if (result.snapshot() == null)
			{
				return PhantomStepResult.of(Type.FAIL_GOAL, "population.creation.absent");
			}
			return switch (result.outcome())
			{
				case PROGRESSED, RETRY -> PhantomStepResult.retry(RETRY_MILLIS, "population.creation.retry");
				case READY -> PhantomStepResult.of(Type.SUCCESS, "population.creation.ready");
				case INCONSISTENT -> PhantomStepResult.of(Type.FAIL_GOAL, "population.creation.inconsistent");
				case NOT_PENDING -> result.snapshot().state().state() == PhantomPopulationState.State.READY ? PhantomStepResult.of(Type.SUCCESS, "population.creation.idempotent") : PhantomStepResult.of(Type.REPLAN, "population.creation.not_pending");
			};
		});
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		final PhantomDomainRef target = new PhantomDomainRef("population.profile", Long.toString(context.profileId()));
		final PhantomPlanStep step = new PhantomPlanStep(0, ACTION_KEY, target, Map.of("profile", context.profileId()), STEP_TIMEOUT_MILLIS, 10, "population.creation.explicit");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), CANDIDATE_KEY, List.of(step), STEP_TIMEOUT_MILLIS, context.logicalNowNanos());
	}
}

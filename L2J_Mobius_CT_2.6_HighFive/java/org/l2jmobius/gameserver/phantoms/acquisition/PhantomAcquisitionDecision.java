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
package org.l2jmobius.gameserver.phantoms.acquisition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.Directive;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.OperationResult;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;

/** One bounded persisted acquisition transition per Decision step. */
public final class PhantomAcquisitionDecision
{
	private static final Set<PhantomActivityState> ALLOWED_STATES = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.WARM, PhantomActivityState.BACKGROUND);
	private static final String SOURCE_NAMESPACE = "acquisition.source";
	private static final int STEP_TIMEOUT_MILLIS = 30_000;
	private static final int MAXIMUM_ATTEMPTS = 8;
	private static final long RETRY_DELAY_MILLIS = 250;
	private final PhantomAcquisitionService _service;

	public PhantomAcquisitionDecision(PhantomAcquisitionService service)
	{
		_service = Objects.requireNonNull(service, "service");
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		Objects.requireNonNull(registry, "registry");
		registry.register(new PhantomDecisionCandidate(
			PhantomAcquisitionService.CANDIDATE_KEY,
			Set.of(PhantomAcquisitionGoalSpec.GOAL_TYPE),
			ALLOWED_STATES,
			List.of(),
			List.of(new PhantomWeightedConsideration("score.acquisition.item", 1, context ->
			{
				final Directive directive = _service.directive(context.profileId(), context.goal(), context.effectiveState());
				final boolean executable = executable(directive.kind());
				return new PhantomConsideration.Evaluation(executable ? 1000 : 0, executable ? directive.reasonKey() : "acquisition.blocked");
			})),
			1000,
			this::plan));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		Objects.requireNonNull(registry, "registry");
		registry.register(PhantomAcquisitionService.PLAN_ACTION, context -> execute(context, DirectiveKind.PLAN));
		registry.register(PhantomAcquisitionService.TRAVEL_ACTION, context -> execute(context, DirectiveKind.TRAVEL));
		registry.register(PhantomAcquisitionService.ACTIVE_ACTION, context -> execute(context, DirectiveKind.ACTIVE));
		registry.register(PhantomAcquisitionService.BACKGROUND_ACTION, context -> execute(context, DirectiveKind.BACKGROUND));
		registry.register(PhantomAcquisitionService.VERIFY_ACTION, context -> execute(context, DirectiveKind.VERIFY));
		registry.register(PhantomAcquisitionService.SWITCH_ACTION, context -> execute(context, DirectiveKind.SWITCH));
	}

	private PhantomPlan plan(PhantomPlanningContext context)
	{
		final Directive directive = _service.directive(context.profileId(), context.goal(), context.effectiveState());
		if (!executable(directive.kind()))
		{
			throw new IllegalStateException("Acquisition candidate became non-executable.");
		}
		final String action = action(directive.kind());
		final PhantomDomainRef target = directive.sourceId().isEmpty() ? context.goal().target() : new PhantomDomainRef(SOURCE_NAMESPACE, directive.sourceId());
		final Map<String, Long> arguments = Map.of("goal", context.goal().goalId(), "revision", context.goal().revision(), "generation", directive.generation());
		final PhantomPlanStep step = new PhantomPlanStep(0, action, target, arguments, STEP_TIMEOUT_MILLIS, MAXIMUM_ATTEMPTS, directive.reasonKey());
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), PhantomAcquisitionService.CANDIDATE_KEY, List.of(step), STEP_TIMEOUT_MILLIS, context.logicalNowNanos());
	}

	private PhantomStepResult execute(PhantomStepContext context, DirectiveKind expected)
	{
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "acquisition.cancelled");
		}
		final Directive current = _service.directive(context.profileId(), context.goal(), context.effectiveState());
		final PhantomDomainRef expectedTarget = current.sourceId().isEmpty() ? context.goal().target() : new PhantomDomainRef(SOURCE_NAMESPACE, current.sourceId());
		final Map<String, Long> expectedArguments = Map.of("goal", context.goal().goalId(), "revision", context.goal().revision(), "generation", current.generation());
		if (((current.kind() != expected) && !((expected == DirectiveKind.VERIFY) && (current.kind() == DirectiveKind.COMPLETE))) || !Objects.equals(expectedTarget, context.step().target()) || !expectedArguments.equals(context.step().numericArguments()))
		{
			return PhantomStepResult.of(Type.REPLAN, "acquisition.step.stale");
		}
		final long logicalMinute = Math.max(0, context.logicalNowNanos() / 60_000_000_000L);
		final OperationResult result = switch (expected)
		{
			case PLAN -> _service.plan(context.profileId(), context.goal(), context.effectiveState(), context.logicalNowNanos(), logicalMinute, context.cancellationToken());
			case TRAVEL -> _service.travel(context.profileId(), context.goal(), context.effectiveState(), context.activityGeneration(), context.tickSequence(), context.logicalNowNanos(), logicalMinute, context.cancellationToken());
			case ACTIVE -> _service.activeAdvance(context.profileId(), context.goal(), context.effectiveState(), context.activityGeneration(), context.tickSequence(), context.logicalNowNanos(), logicalMinute, context.cancellationToken());
			case BACKGROUND -> _service.backgroundAdvance(context.profileId(), context.goal(), context.effectiveState(), context.activityGeneration(), context.tickSequence(), context.logicalNowNanos(), logicalMinute, context.cancellationToken());
			case VERIFY -> current.kind() == DirectiveKind.COMPLETE ? OperationResult.complete("acquisition.complete") : _service.verify(context.profileId(), context.goal(), context.effectiveState(), context.logicalNowNanos(), logicalMinute, context.cancellationToken());
			case SWITCH -> _service.switchSource(context.profileId(), context.goal(), context.effectiveState(), context.logicalNowNanos(), logicalMinute, context.cancellationToken());
			case COMPLETE -> OperationResult.complete("acquisition.complete");
			default -> OperationResult.replan("acquisition.directive.invalid");
		};
		return switch (result.status())
		{
			case SUCCESS -> PhantomStepResult.of(Type.SUCCESS, result.reasonKey());
			case RETRY -> PhantomStepResult.retry(RETRY_DELAY_MILLIS, result.reasonKey());
			case REPLAN -> PhantomStepResult.of(Type.REPLAN, result.reasonKey());
			case COMPLETE_GOAL -> PhantomStepResult.of(Type.COMPLETE_GOAL, result.reasonKey());
			case FAIL_GOAL -> PhantomStepResult.of(Type.FAIL_GOAL, result.reasonKey());
		};
	}

	private static boolean executable(DirectiveKind kind)
	{
		return (kind == DirectiveKind.PLAN) || (kind == DirectiveKind.TRAVEL) || (kind == DirectiveKind.ACTIVE) || (kind == DirectiveKind.BACKGROUND) || (kind == DirectiveKind.VERIFY) || (kind == DirectiveKind.SWITCH) || (kind == DirectiveKind.COMPLETE);
	}

	private static String action(DirectiveKind kind)
	{
		return switch (kind)
		{
			case PLAN -> PhantomAcquisitionService.PLAN_ACTION;
			case TRAVEL -> PhantomAcquisitionService.TRAVEL_ACTION;
			case ACTIVE -> PhantomAcquisitionService.ACTIVE_ACTION;
			case BACKGROUND -> PhantomAcquisitionService.BACKGROUND_ACTION;
			case VERIFY, COMPLETE -> PhantomAcquisitionService.VERIFY_ACTION;
			case SWITCH -> PhantomAcquisitionService.SWITCH_ACTION;
			default -> throw new IllegalArgumentException("Unsupported acquisition directive.");
		};
	}
}

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
package org.l2jmobius.gameserver.phantoms.commerce;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.OperationKind;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.OperationIntent;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService.OperationResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomConsideration;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionCandidate;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.decision.PhantomWeightedConsideration;

/**
 * Explicit-source candidates and handlers. No candidate creates goals or
 * exposes progression mutation actions.
 */
public final class PhantomCommerceDecision
{
	public static final String OBSERVE = "commerce.observe";
	public static final String BUY = "commerce.buy";
	public static final String SELL = "commerce.sell";
	public static final String TELEPORT = "commerce.teleport";
	public static final String ACQUIRE_ITEM = "acquire.item";
	public static final String MAINTAIN_SUPPLIES = "maintain.supplies";
	public static final String SELL_ITEM = "sell.item";
	public static final String TRAVEL_TELEPORT = "travel.teleport";

	private static final Set<PhantomActivityState> MATERIALIZED_STATES = Set.of(PhantomActivityState.ACTIVE, PhantomActivityState.NEARBY_PERCEPTIBLE);
	private static final int STEP_TIMEOUT_MILLIS = 5000;
	private static final int MAXIMUM_ATTEMPTS = 2;
	private static final long RETRY_DELAY_MILLIS = 250;

	private final PhantomCommerceService _service;

	public PhantomCommerceDecision(PhantomCommerceService service)
	{
		_service = Objects.requireNonNull(service);
	}

	public void registerCandidates(PhantomCandidateRegistry registry)
	{
		Objects.requireNonNull(registry);
		registry.register(candidate("candidate.commerce.buy", Set.of(ACQUIRE_ITEM, MAINTAIN_SUPPLIES), BUY, "commerce.buy", 5));
		registry.register(candidate("candidate.commerce.sell", Set.of(SELL_ITEM), SELL, "commerce.sell", 6));
		registry.register(candidate("candidate.commerce.teleport", Set.of(TRAVEL_TELEPORT), TELEPORT, "commerce.teleport", 4));
	}

	public void registerHandlers(PhantomStepHandlerRegistry registry)
	{
		Objects.requireNonNull(registry);
		registry.register(OBSERVE, this::observe);
		registry.register(BUY, context -> mutate(context, OperationKind.BUY));
		registry.register(SELL, context -> mutate(context, OperationKind.SELL));
		registry.register(TELEPORT, context -> mutate(context, OperationKind.TELEPORT));
	}

	private PhantomDecisionCandidate candidate(String key, Set<String> goalTypes, String actionKey, String sourceNamespace, int fields)
	{
		return new PhantomDecisionCandidate(
			key,
			goalTypes,
			MATERIALIZED_STATES,
			List.of(),
			List.of(new PhantomWeightedConsideration("score." + actionKey, 1, context ->
			{
				final PhantomDomainRef source = source(context.goal(), sourceNamespace, fields);
				return new PhantomConsideration.Evaluation(source == null ? 0 : 1000, source == null ? "commerce.source.invalid" : "commerce.source.explicit");
			})),
			1000,
			context -> plan(context, key, actionKey, sourceNamespace, fields));
	}

	private static PhantomPlan plan(PhantomPlanningContext context, String candidateKey, String actionKey, String sourceNamespace, int fields)
	{
		final PhantomDomainRef source = source(context.goal(), sourceNamespace, fields);
		if (source == null)
		{
			throw new IllegalArgumentException("Commerce candidate requires one valid explicit source.");
		}
		final PhantomPlanStep step = new PhantomPlanStep(0, actionKey, source, numericArguments(source), STEP_TIMEOUT_MILLIS, MAXIMUM_ATTEMPTS, actionKey + ".explicit");
		return new PhantomPlan(context.decisionSequence(), context.goal().goalId(), candidateKey, List.of(step), STEP_TIMEOUT_MILLIS, context.logicalNowNanos());
	}

	private PhantomStepResult observe(PhantomStepContext context)
	{
		if ((context.step().target() != null) || !context.step().numericArguments().isEmpty())
		{
			return PhantomStepResult.of(Type.REPLAN, "commerce.observe.invalid");
		}
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "commerce.observe.cancelled");
		}
		_service.catalog().hashes();
		return PhantomStepResult.of(Type.SUCCESS, "commerce.observe.complete");
	}

	private PhantomStepResult mutate(PhantomStepContext context, OperationKind expectedKind)
	{
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "commerce.mutation.cancelled");
		}
		final OperationIntent intent;
		try
		{
			intent = intent(context.step().target(), context.step().numericArguments(), context.goal().expenseBudget(), expectedKind);
		}
		catch (IllegalArgumentException e)
		{
			return PhantomStepResult.of(Type.REPLAN, "commerce.mutation.invalid");
		}
		final OperationResult result = _service.execute(context.profileId(), context.goal().goalId(), context.goal().revision(), intent, context.cancellationToken()::isCancelled);
		final String reason = "commerce." + result.reason().name().toLowerCase(Locale.ROOT);
		return switch (result.status())
		{
			case SUCCESS, IDEMPOTENT -> PhantomStepResult.of(Type.SUCCESS, reason);
			case RETRY -> PhantomStepResult.retry(RETRY_DELAY_MILLIS, reason);
			case REPLAN, INCONSISTENT -> PhantomStepResult.of(Type.REPLAN, reason);
			case CANCELLED -> PhantomStepResult.of(Type.CANCELLED, reason);
		};
	}

	private static PhantomDomainRef source(PhantomGoal goal, String namespace, int fields)
	{
		for (PhantomDomainRef source : goal.validSources())
		{
			if (namespace.equals(source.namespace()) && (source.key().split(":", -1).length == fields))
			{
				try
				{
					final Map<String, Long> arguments = numericArguments(source);
					if (!arguments.isEmpty())
					{
						return source;
					}
				}
				catch (IllegalArgumentException e)
				{
					// An invalid persisted source cannot become an executable plan.
				}
			}
		}
		return null;
	}

	private static Map<String, Long> numericArguments(PhantomDomainRef source)
	{
		final String[] values = source.key().split(":", -1);
		return switch (source.namespace())
		{
			case "commerce.buy" -> Map.of(
				"npc", positive(values[0]),
				"npc_object", positive(values[1]),
				"list", positive(values[2]),
				"item", positive(values[3]),
				"count", positive(values[4]));
			case "commerce.sell" -> Map.of(
				"npc", positive(values[0]),
				"npc_object", positive(values[1]),
				"list", positive(values[2]),
				"item", positive(values[3]),
				"item_object", positive(values[4]),
				"count", positive(values[5]));
			case "commerce.teleport" -> Map.of(
				"npc", positive(values[0]),
				"npc_object", positive(values[1]),
				"ordinal", nonnegative(values[2]));
			default -> throw new IllegalArgumentException("Unsupported commerce source namespace.");
		};
	}

	private static OperationIntent intent(PhantomDomainRef source, Map<String, Long> arguments, long expenseBudget, OperationKind expectedKind)
	{
		if (source == null)
		{
			throw new IllegalArgumentException("Missing commerce source.");
		}
		final Map<String, Long> exact = numericArguments(source);
		if (!exact.equals(arguments))
		{
			throw new IllegalArgumentException("Commerce step arguments do not match its persisted source.");
		}
		return switch (expectedKind)
		{
			case BUY ->
				{
					requireNamespace(source, "commerce.buy");
					yield new OperationIntent(OperationKind.BUY, intValue(exact, "npc"), intValue(exact, "npc_object"), intValue(exact, "list"), intValue(exact, "item"), 0, exact.get("count"), 0, "", expenseBudget);
				}
			case SELL ->
				{
					requireNamespace(source, "commerce.sell");
					yield new OperationIntent(OperationKind.SELL, intValue(exact, "npc"), intValue(exact, "npc_object"), intValue(exact, "list"), intValue(exact, "item"), intValue(exact, "item_object"), exact.get("count"), 0, "", expenseBudget);
				}
			case TELEPORT ->
				{
					requireNamespace(source, "commerce.teleport");
					final String[] values = source.key().split(":", -1);
					final String listName = new String(Base64.getUrlDecoder().decode(values[3]), StandardCharsets.UTF_8);
					yield new OperationIntent(OperationKind.TELEPORT, intValue(exact, "npc"), intValue(exact, "npc_object"), 0, 0, 0, 0, intValue(exact, "ordinal"), listName, expenseBudget);
				}
		};
	}

	private static void requireNamespace(PhantomDomainRef source, String expected)
	{
		if (!expected.equals(source.namespace()))
		{
			throw new IllegalArgumentException("Commerce source namespace mismatch.");
		}
	}

	private static int intValue(Map<String, Long> values, String key)
	{
		return Math.toIntExact(values.get(key));
	}

	private static long positive(String value)
	{
		final long parsed = Long.parseLong(value);
		if (parsed <= 0)
		{
			throw new IllegalArgumentException("Commerce source value must be positive.");
		}
		return parsed;
	}

	private static long nonnegative(String value)
	{
		final long parsed = Long.parseLong(value);
		if (parsed < 0)
		{
			throw new IllegalArgumentException("Commerce source value must be nonnegative.");
		}
		return parsed;
	}
}

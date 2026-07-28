/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.combat;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.CancelStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;

public final class PhantomCombatStepHandlers
{
	public static final String START = "combat.start";
	public static final String AWAIT = "combat.await";
	public static final String CANCEL = "combat.cancel";
	public static final String RESPAWN_TOWN = "combat.respawn_town";
	private static final Set<String> START_ARGUMENTS = Set.of("mode", "shots", "loot", "timeout");
	private final PhantomCombatService _service;
	private final PhantomCombatPolicy _policy;

	public PhantomCombatStepHandlers(PhantomCombatService service, PhantomCombatPolicy policy)
	{
		_service = Objects.requireNonNull(service, "service");
		_policy = Objects.requireNonNull(policy, "policy");
	}

	public void register(PhantomStepHandlerRegistry registry)
	{
		Objects.requireNonNull(registry, "registry");
		registry.register(START, this::start);
		registry.register(AWAIT, this::await);
		registry.register(CANCEL, this::cancel);
		registry.register(RESPAWN_TOWN, this::respawnTown);
	}

	private PhantomStepResult start(PhantomStepContext context)
	{
		final PhantomDomainRef target = context.step().target();
		final Map<String, Long> arguments = context.step().numericArguments();
		if ((target == null) || !"world.object".equals(target.namespace()) || !START_ARGUMENTS.containsAll(arguments.keySet()) || arguments.keySet().stream().anyMatch(key -> !START_ARGUMENTS.contains(key)) || !arguments.containsKey("mode") || !arguments.containsKey("shots") || !arguments.containsKey("loot"))
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.start.invalid");
		}
		final int targetObjectId;
		try
		{
			targetObjectId = Integer.parseInt(target.key());
		}
		catch (NumberFormatException e)
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.start.target");
		}
		if (targetObjectId <= 0)
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.start.target");
		}
		final long shots = arguments.get("shots");
		final long loot = arguments.get("loot");
		final long timeout = arguments.getOrDefault("timeout", _policy.defaultTimeoutMillis());
		if (((shots != 0) && (shots != 1)) || ((loot != 0) && (loot != 1)) || (timeout < 1000) || (timeout > _policy.maximumTimeoutMillis()))
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.start.arguments");
		}
		final PhantomCombatMode mode;
		try
		{
			mode = PhantomCombatMode.fromCode(arguments.get("mode"));
		}
		catch (IllegalArgumentException e)
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.start.mode");
		}

		final StartResult result = _service.startSession(new PhantomCombatRequest(context.profileId(), targetObjectId, mode, shots == 1, loot == 1, timeout, context.cancellationToken()));
		return switch (result.status())
		{
			case ACCEPTED, IDEMPOTENT -> PhantomStepResult.of(Type.SUCCESS, "combat.start.accepted");
			case CANCELLED -> PhantomStepResult.of(Type.CANCELLED, "combat.start.cancelled");
			case REJECTED_ACTOR, REJECTED_CAPACITY, REJECTED_STATE -> PhantomStepResult.retry(retryDelay(context), "combat.start.retry");
			case REJECTED_TARGET, REJECTED_EXISTING, UNSUPPORTED_LOADOUT -> PhantomStepResult.of(Type.REPLAN, "combat.start.replan");
			case BACKEND_FAILURE -> PhantomStepResult.of(Type.REPLAN, "combat.start.backend");
		};
	}

	private PhantomStepResult await(PhantomStepContext context)
	{
		if (!emptyStep(context))
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.await.invalid");
		}
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "combat.await.cancelled");
		}
		final var snapshot = _service.find(context.profileId());
		if (snapshot.isEmpty())
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.await.missing");
		}
		if (!snapshot.orElseThrow().result().terminal())
		{
			return PhantomStepResult.retry(retryDelay(context), "combat.await.active");
		}
		final var consumed = _service.consumeTerminal(context.profileId());
		if (consumed.isEmpty())
		{
			return PhantomStepResult.retry(retryDelay(context), "combat.await.cleanup");
		}
		final PhantomCombatResult result = consumed.orElseThrow().result();
		if (result.victory())
		{
			return PhantomStepResult.of(Type.SUCCESS, "combat.await.victory");
		}
		if (result == PhantomCombatResult.CANCELLED)
		{
			return PhantomStepResult.of(Type.CANCELLED, "combat.await.cancelled");
		}
		return PhantomStepResult.of(Type.REPLAN, "combat.await.replan");
	}

	private PhantomStepResult cancel(PhantomStepContext context)
	{
		if (!emptyStep(context))
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.cancel.invalid");
		}
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "combat.cancel.cancelled");
		}
		final CancelStatus result = _service.cancel(context.profileId());
		return switch (result)
		{
			case CANCELLED_CLEAN, NOT_FOUND, ALREADY_TERMINAL -> PhantomStepResult.of(Type.SUCCESS, "combat.cancel.complete");
			case CLEANUP_PENDING -> PhantomStepResult.retry(retryDelay(context), "combat.cancel.cleanup");
			case CLEANUP_FAILED, NOT_RUNNING -> PhantomStepResult.of(Type.REPLAN, "combat.cancel.failed");
		};
	}

	private PhantomStepResult respawnTown(PhantomStepContext context)
	{
		if (!emptyStep(context))
		{
			return PhantomStepResult.of(Type.REPLAN, "combat.respawn.invalid");
		}
		final RespawnOutcome result = _service.respawnTown(new PhantomRespawnRequest(context.profileId(), context.cancellationToken()));
		return switch (result)
		{
			case COMPLETED -> PhantomStepResult.of(Type.SUCCESS, "combat.respawn.complete");
			case RETRY -> PhantomStepResult.retry(retryDelay(context), "combat.respawn.retry");
			case REJECTED -> PhantomStepResult.of(Type.REPLAN, "combat.respawn.rejected");
			case CANCELLED -> PhantomStepResult.of(Type.CANCELLED, "combat.respawn.cancelled");
		};
	}

	private static boolean emptyStep(PhantomStepContext context)
	{
		return (context.step().target() == null) && context.step().numericArguments().isEmpty();
	}

	private static long retryDelay(PhantomStepContext context)
	{
		return Math.max(250, Math.min(30_000, context.step().timeoutMillis() / Math.max(1, context.step().maximumAttempts())));
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult.Type;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.AcquireKind;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.EquipItemRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.LearnSkillRequest;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SnapshotStatus;

/**
 * Strict plan action adapters. They do not create candidates or simulate client
 * packets.
 */
public final class PhantomProgressionStepHandlers
{
	public static final String OBSERVE = "progression.observe";
	public static final String AWAIT_LEVEL = "progression.await_level";
	public static final String AWAIT_PROFESSION = "progression.await_profession";
	public static final String LEARN_SKILL = "progression.learn_skill";
	public static final String EQUIP_ITEM = "progression.equip_item";
	private static final Set<String> LEVEL_ARGUMENTS = Set.of("level");
	private static final Set<String> LEARN_ARGUMENTS = Set.of("skill_id", "skill_level");
	private final PhantomProgressionService _service;

	public PhantomProgressionStepHandlers(PhantomProgressionService service)
	{
		_service = Objects.requireNonNull(service);
	}

	public void register(PhantomStepHandlerRegistry registry)
	{
		Objects.requireNonNull(registry);
		registry.register(OBSERVE, this::observe);
		registry.register(AWAIT_LEVEL, this::awaitLevel);
		registry.register(AWAIT_PROFESSION, this::awaitProfession);
		registry.register(LEARN_SKILL, this::learnSkill);
		registry.register(EQUIP_ITEM, this::equipItem);
	}

	private PhantomStepResult observe(PhantomStepContext context)
	{
		if (!empty(context))
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.observe.invalid");
		}
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "progression.observe.cancelled");
		}
		return switch (_service.observeActor(context.profileId()).result().status())
		{
			case FOUND -> PhantomStepResult.of(Type.SUCCESS, "progression.observe.complete");
			case ACTOR_NOT_MATERIALIZED -> PhantomStepResult.retry(retryDelay(context), "progression.observe.actor");
			case CANCELLED -> PhantomStepResult.of(Type.CANCELLED, "progression.observe.cancelled");
			case SERVICE_NOT_RUNNING, BACKEND_FAILURE -> PhantomStepResult.of(Type.REPLAN, "progression.observe.failed");
		};
	}

	private PhantomStepResult awaitLevel(PhantomStepContext context)
	{
		if ((context.step().target() != null) || !exactArguments(context.step().numericArguments(), LEVEL_ARGUMENTS))
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.await_level.invalid");
		}
		final long expected = context.step().numericArguments().get("level");
		if ((expected < 1) || (expected > 85))
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.await_level.invalid");
		}
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "progression.await_level.cancelled");
		}
		final var result = _service.observeActor(context.profileId()).result();
		if (result.status() == SnapshotStatus.ACTOR_NOT_MATERIALIZED)
		{
			return PhantomStepResult.retry(retryDelay(context), "progression.await_level.actor");
		}
		if (result.status() != SnapshotStatus.FOUND)
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.await_level.failed");
		}
		return result.snapshot().level() >= expected ? PhantomStepResult.of(Type.SUCCESS, "progression.await_level.complete") : PhantomStepResult.retry(retryDelay(context), "progression.await_level.pending");
	}

	private PhantomStepResult awaitProfession(PhantomStepContext context)
	{
		final Integer expected = targetId(context.step().target(), "player.class");
		if ((expected == null) || !context.step().numericArguments().isEmpty())
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.await_profession.invalid");
		}
		if (context.cancellationToken().isCancelled())
		{
			return PhantomStepResult.of(Type.CANCELLED, "progression.await_profession.cancelled");
		}
		final var result = _service.observeActor(context.profileId()).result();
		if (result.status() == SnapshotStatus.ACTOR_NOT_MATERIALIZED)
		{
			return PhantomStepResult.retry(retryDelay(context), "progression.await_profession.actor");
		}
		if (result.status() != SnapshotStatus.FOUND)
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.await_profession.failed");
		}
		return result.snapshot().activeClassId() == expected ? PhantomStepResult.of(Type.SUCCESS, "progression.await_profession.complete") : PhantomStepResult.retry(retryDelay(context), "progression.await_profession.pending");
	}

	private PhantomStepResult learnSkill(PhantomStepContext context)
	{
		final Integer trainer = targetId(context.step().target(), "world.object");
		if ((trainer == null) || !exactArguments(context.step().numericArguments(), LEARN_ARGUMENTS))
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.learn_skill.invalid");
		}
		final long skillId = context.step().numericArguments().get("skill_id");
		final long skillLevel = context.step().numericArguments().get("skill_level");
		if ((skillId < 1) || (skillId > Integer.MAX_VALUE) || (skillLevel < 1) || (skillLevel > Integer.MAX_VALUE))
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.learn_skill.invalid");
		}
		final var result = _service.learnClassSkill(new LearnSkillRequest(context.profileId(), trainer, AcquireKind.CLASS, (int) skillId, (int) skillLevel, context.cancellationToken()));
		return operationResult(context, result.status(), "progression.learn_skill");
	}

	private PhantomStepResult equipItem(PhantomStepContext context)
	{
		final Integer itemObjectId = targetId(context.step().target(), "inventory.object");
		if ((itemObjectId == null) || !context.step().numericArguments().isEmpty())
		{
			return PhantomStepResult.of(Type.REPLAN, "progression.equip_item.invalid");
		}
		final var result = _service.equipOwnedItem(new EquipItemRequest(context.profileId(), itemObjectId, context.cancellationToken()));
		return operationResult(context, result.status(), "progression.equip_item");
	}

	private static PhantomStepResult operationResult(PhantomStepContext context, OperationStatus status, String prefix)
	{
		return switch (status)
		{
			case SUCCESS, IDEMPOTENT -> PhantomStepResult.of(Type.SUCCESS, prefix + ".complete");
			case CANCELLED -> PhantomStepResult.of(Type.CANCELLED, prefix + ".cancelled");
			case OPERATION_IN_PROGRESS, ACTOR_NOT_MATERIALIZED, TRAINER_REQUIRED -> PhantomStepResult.retry(retryDelay(context), prefix + ".retry");
			case SERVICE_NOT_RUNNING, INVALID_REQUEST, UNSUPPORTED_ACQUIRE_TYPE, TRAINER_MISMATCH, TRAINER_CANNOT_TEACH, ACTOR_STATE_REJECTED, SKILL_NOT_FOUND, SKILL_LEARN_NOT_FOUND, PREVIOUS_SKILL_MISSING, LEVEL_TOO_LOW, SP_TOO_LOW, PREREQUISITE_MISSING, REQUIRED_ITEM_MISSING, ITEM_NOT_OWNED, ITEM_NOT_EQUIPPABLE, ITEM_CONDITION_FAILED, RECONCILIATION_FAILED, DURABLE_SKILL_STATE_CONFLICT, DURABLE_SP_STATE_CONFLICT, DURABLE_ITEM_STATE_CONFLICT, DURABLE_SCHEMA_OR_ROW_MISSING, DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED, BLOCKED_CANONICAL_SKILL_LEARNING, BLOCKED_CANONICAL_EQUIP_FACADE, BACKEND_FAILURE -> PhantomStepResult.of(Type.REPLAN, prefix + ".rejected");
		};
	}

	private static boolean empty(PhantomStepContext context)
	{
		return (context.step().target() == null) && context.step().numericArguments().isEmpty();
	}

	private static boolean exactArguments(Map<String, Long> arguments, Set<String> expected)
	{
		return arguments.keySet().equals(expected);
	}

	private static Integer targetId(PhantomDomainRef target, String namespace)
	{
		if ((target == null) || !namespace.equals(target.namespace()))
		{
			return null;
		}
		try
		{
			final int value = Integer.parseInt(target.key());
			return value > 0 ? value : null;
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static long retryDelay(PhantomStepContext context)
	{
		return Math.max(250, Math.min(30_000, context.step().timeoutMillis() / Math.max(1, context.step().maximumAttempts())));
	}
}

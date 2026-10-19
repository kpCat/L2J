/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Compatible active-time accounting inside an existing child Goal, never a second goal store or timer. */
public final class PhantomJourneyLifetime
{
	public static final Set<String> KEYS = Set.of("journey.started", "journey.paused", "journey.remaining");
	private static final long MAXIMUM_CALENDAR_MILLIS = TimeUnit.DAYS.toMillis(1);

	private PhantomJourneyLifetime() { }

	public static boolean alive(PhantomGoal goal, long now)
	{
		if (goal == null || !continuing(goal) || !valid(goal)) { return false; }
		final long started = goal.constraints().getOrDefault("journey.started", 0L);
		if (started > now || goal.constraints().getOrDefault("journey.paused", 0L) > now || started > 0 && now - started >= MAXIMUM_CALENDAR_MILLIS) { return false; }
		return paused(goal) ? started > 0 && goal.constraints().getOrDefault("journey.remaining", 0L) > 0 : goal.deadlineEpochMillis() > now;
	}

	/** A failed learning episode can own one compensation trip; its FAILED status and original cause stay durable. */
	public static boolean returningFailure(PhantomGoal goal)
	{
		return goal != null && goal.status() == PhantomGoalStatus.FAILED && "skill.learn".equals(goal.goalType()) && "learning.autonomous".equals(goal.purposeKey()) && goal.constraints().getOrDefault("journey.return", 0L) == 1;
	}
	public static boolean continuing(PhantomGoal goal) { return goal != null && (goal.status() == PhantomGoalStatus.ACTIVE || returningFailure(goal)); }

	public static boolean valid(PhantomGoal goal)
	{
		if (KEYS.stream().noneMatch(goal.constraints()::containsKey)) { return true; }
		if (!goal.constraints().keySet().containsAll(KEYS)) { return false; }
		final long started = goal.constraints().get("journey.started"), paused = goal.constraints().get("journey.paused"), remaining = goal.constraints().get("journey.remaining");
		return started > 0 && (paused == 0 || paused >= started) && remaining > 0 && remaining <= MAXIMUM_CALENDAR_MILLIS;
	}

	public static boolean paused(PhantomGoal goal) { return goal.constraints().getOrDefault("journey.paused", 0L) > 0; }
	public static long remaining(PhantomGoal goal, long now)
	{
		return !alive(goal, now) ? 0 : paused(goal) ? goal.constraints().get("journey.remaining") : Math.max(0, goal.deadlineEpochMillis() - now);
	}

	public static PhantomGoal start(PhantomGoal goal, long now)
	{
		if (!alive(goal, now) || goal.constraints().containsKey("journey.started")) { return goal; }
		final var values = new LinkedHashMap<>(goal.constraints());
		values.put("journey.started", now); values.put("journey.paused", 0L); values.put("journey.remaining", goal.deadlineEpochMillis() - now);
		return copy(goal, goal.deadlineEpochMillis(), values);
	}

	public static PhantomGoal pause(PhantomGoal goal, long now)
	{
		if (!alive(goal, now) || paused(goal)) { return goal; }
		final var started = start(goal, now);
		final var values = new LinkedHashMap<>(started.constraints());
		values.put("journey.paused", now); values.put("journey.remaining", goal.deadlineEpochMillis() - now);
		return copy(goal, goal.deadlineEpochMillis(), values);
	}

	public static PhantomGoal resume(PhantomGoal goal, long now)
	{
		if (!alive(goal, now) || !paused(goal)) { return goal; }
		final var values = new LinkedHashMap<>(goal.constraints()); values.put("journey.paused", 0L);
		final long calendarEnd = Math.addExact(values.get("journey.started"), MAXIMUM_CALENDAR_MILLIS);
		return copy(goal, Math.min(calendarEnd, Math.addExact(now, values.get("journey.remaining"))), values);
	}

	private static PhantomGoal copy(PhantomGoal goal, long deadline, java.util.Map<String, Long> values)
	{
		return new PhantomGoal(goal.goalId(), goal.goalType(), goal.status(), goal.subject(), goal.target(), goal.requiredAmount(), goal.currentAmount(), goal.acquisitionMethod(), goal.validSources(), goal.selectedAnchor(), goal.purposeKey(), goal.priority(), goal.riskBudget(), goal.expenseBudget(), deadline, values, goal.reasonKey(), goal.revision(), goal.payloadText());
	}
}

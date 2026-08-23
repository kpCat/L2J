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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SchedulerSnapshot;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.ServiceSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.MaterializationSnapshot;

/**
 * Immutable structural scale envelope derived from existing configuration and
 * navigation policy. It owns no counters, overload state or runtime resources.
 */
public record PhantomScaleEnvelope(
	int scheduledProfilesCap,
	int materializedCap,
	int schedulerPulseMillis,
	int schedulerProfilesPerPulse,
	int populationTarget,
	int populationActiveTarget,
	int populationCreationInFlight,
	int populationBoundariesPerPulse,
	int partyOperationsPerPulse,
	int socialCacheProfiles,
	int navigationQueueCap,
	int navigationWorkerCap,
	int navigationTrackedProfilesCap,
	int navigationCacheCap)
{
	public PhantomScaleEnvelope
	{
		requirePositive(scheduledProfilesCap, "scheduledProfilesCap");
		requirePositive(materializedCap, "materializedCap");
		requirePositive(schedulerPulseMillis, "schedulerPulseMillis");
		requirePositive(schedulerProfilesPerPulse, "schedulerProfilesPerPulse");
		requireNonNegative(populationTarget, "populationTarget");
		requireNonNegative(populationActiveTarget, "populationActiveTarget");
		requirePositive(populationCreationInFlight, "populationCreationInFlight");
		requirePositive(populationBoundariesPerPulse, "populationBoundariesPerPulse");
		requirePositive(partyOperationsPerPulse, "partyOperationsPerPulse");
		requirePositive(socialCacheProfiles, "socialCacheProfiles");
		requirePositive(navigationQueueCap, "navigationQueueCap");
		requirePositive(navigationWorkerCap, "navigationWorkerCap");
		requirePositive(navigationTrackedProfilesCap, "navigationTrackedProfilesCap");
		requirePositive(navigationCacheCap, "navigationCacheCap");
		if (materializedCap > scheduledProfilesCap)
		{
			throw new IllegalArgumentException("Materialized capacity must fit scheduled profile capacity.");
		}
		if (schedulerProfilesPerPulse > scheduledProfilesCap)
		{
			throw new IllegalArgumentException("Scheduler pulse budget must fit scheduled profile capacity.");
		}
		if (populationTarget > scheduledProfilesCap)
		{
			throw new IllegalArgumentException("Population target must fit scheduled profile capacity.");
		}
		if (populationActiveTarget > Math.min(populationTarget, materializedCap))
		{
			throw new IllegalArgumentException("Population ACTIVE target must fit population and materialization capacities.");
		}
		if (navigationWorkerCap > navigationQueueCap)
		{
			throw new IllegalArgumentException("Navigation worker capacity must fit queue capacity.");
		}
		if (navigationQueueCap > navigationTrackedProfilesCap)
		{
			throw new IllegalArgumentException("Navigation queue capacity must fit tracked profile capacity.");
		}
	}

	public static PhantomScaleEnvelope from(PhantomPlayersConfig.Settings settings, PhantomNavigationPolicy navigationPolicy)
	{
		Objects.requireNonNull(settings, "settings");
		Objects.requireNonNull(navigationPolicy, "navigationPolicy");
		if (!settings.enabled())
		{
			throw new IllegalArgumentException("Scale envelope requires validated enabled settings; disabled runtime settings intentionally expose zero capacity.");
		}
		return new PhantomScaleEnvelope(
			settings.maxScheduledPhantomProfiles(),
			settings.maxMaterializedPhantoms(),
			settings.schedulerPulseMillis(),
			settings.schedulerProfilesPerPulse(),
			settings.populationTarget(),
			settings.populationActiveTarget(),
			settings.populationCreationInFlight(),
			settings.populationBoundariesPerPulse(),
			settings.partyOperationsPerPulse(),
			settings.socialCacheProfiles(),
			navigationPolicy.maximumQueuedRequests(),
			navigationPolicy.maximumConcurrentPathfinders(),
			navigationPolicy.maximumTrackedProfiles(),
			navigationPolicy.maximumCacheEntries());
	}

	public Assessment assess(SchedulerSnapshot scheduler, org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceSnapshot materialization, ServiceSnapshot navigation)
	{
		Objects.requireNonNull(scheduler, "scheduler");
		Objects.requireNonNull(materialization, "materialization");
		Objects.requireNonNull(navigation, "navigation");
		final List<Violation> violations = new ArrayList<>();

		checkEqual(scheduler.capacity(), scheduledProfilesCap, Violation.SCHEDULER_CAPACITY_MISMATCH, violations);
		checkMaximum(scheduler.registered(), scheduledProfilesCap, Violation.SCHEDULER_REGISTERED_EXCEEDED, violations);
		checkMaximum(scheduler.ready(), scheduledProfilesCap, Violation.SCHEDULER_READY_EXCEEDED, violations);
		checkMaximum(scheduler.due(), scheduledProfilesCap, Violation.SCHEDULER_DUE_EXCEEDED, violations);
		checkMaximum(scheduler.scheduledTaskCount(), 1, Violation.SCHEDULER_SHARED_TASK_EXCEEDED, violations);

		checkEqual(materialization.maximumMaterialized(), materializedCap, Violation.MATERIALIZATION_CAPACITY_MISMATCH, violations);
		checkMaximum(materialization.retainedEntries(), materializedCap, Violation.MATERIALIZATION_RETAINED_EXCEEDED, violations);
		if ((materialization.availablePermits() < 0) || (materialization.availablePermits() > materializedCap))
		{
			violations.add(Violation.MATERIALIZATION_PERMITS_INVALID);
		}
		if ((materialization.retainedEntries() + materialization.availablePermits()) != materializedCap)
		{
			violations.add(Violation.MATERIALIZATION_OWNERSHIP_INCONSISTENT);
		}
		final Set<Long> profileIds = new HashSet<>();
		final Set<Integer> characterObjectIds = new HashSet<>();
		for (MaterializationSnapshot snapshot : materialization.materializations())
		{
			if (!profileIds.add(snapshot.profileId()))
			{
				violations.add(Violation.MATERIALIZATION_PROFILE_IDENTITY_DUPLICATE);
			}
			if (!characterObjectIds.add(snapshot.characterObjectId()))
			{
				violations.add(Violation.MATERIALIZATION_CHARACTER_IDENTITY_DUPLICATE);
			}
		}
		if (materialization.materializations().size() != materialization.retainedEntries())
		{
			violations.add(Violation.MATERIALIZATION_SNAPSHOT_COUNT_INCONSISTENT);
		}

		checkEqual(navigation.queueCapacity(), navigationQueueCap, Violation.NAVIGATION_QUEUE_CAPACITY_MISMATCH, violations);
		checkMaximum(navigation.queuedRequests(), navigationQueueCap, Violation.NAVIGATION_QUEUE_EXCEEDED, violations);
		checkMaximum(navigation.peakQueuedRequests(), navigationQueueCap, Violation.NAVIGATION_QUEUE_PEAK_EXCEEDED, violations);
		checkEqual(navigation.maximumWorkers(), navigationWorkerCap, Violation.NAVIGATION_WORKER_CAPACITY_MISMATCH, violations);
		checkMaximum(navigation.currentWorkers(), navigationWorkerCap, Violation.NAVIGATION_WORKERS_EXCEEDED, violations);
		checkMaximum(navigation.peakWorkers(), navigationWorkerCap, Violation.NAVIGATION_WORKER_PEAK_EXCEEDED, violations);
		checkEqual(navigation.cacheCapacity(), navigationCacheCap, Violation.NAVIGATION_CACHE_CAPACITY_MISMATCH, violations);
		checkMaximum(navigation.cacheEntries(), navigationCacheCap, Violation.NAVIGATION_CACHE_EXCEEDED, violations);
		checkMaximum(navigation.peakCacheEntries(), navigationCacheCap, Violation.NAVIGATION_CACHE_PEAK_EXCEEDED, violations);
		checkMaximum(navigation.activeRequests(), navigationTrackedProfilesCap, Violation.NAVIGATION_ACTIVE_TRACKED_EXCEEDED, violations);
		checkMaximum(navigation.completedResults(), navigationTrackedProfilesCap, Violation.NAVIGATION_COMPLETED_TRACKED_EXCEEDED, violations);
		checkMaximum(navigation.cooldownProfiles(), navigationTrackedProfilesCap, Violation.NAVIGATION_COOLDOWN_TRACKED_EXCEEDED, violations);
		checkMaximum(navigation.trackedProgressAttempts(), navigationTrackedProfilesCap, Violation.NAVIGATION_PROGRESS_TRACKED_EXCEEDED, violations);

		final boolean atCapacity = (scheduler.registered() == scheduledProfilesCap)
			|| (materialization.retainedEntries() == materializedCap)
			|| (navigation.queuedRequests() == navigationQueueCap)
			|| (navigation.cacheEntries() == navigationCacheCap);
		final Verdict verdict = violations.isEmpty() ? (atCapacity ? Verdict.AT_CAPACITY : Verdict.WITHIN_BOUNDS) : Verdict.VIOLATED;
		return new Assessment(verdict, scheduler.overloadLevel(), violations);
	}

	private static void requirePositive(int value, String name)
	{
		if (value <= 0)
		{
			throw new IllegalArgumentException(name + " must be positive.");
		}
	}

	private static void requireNonNegative(int value, String name)
	{
		if (value < 0)
		{
			throw new IllegalArgumentException(name + " must not be negative.");
		}
	}

	private static void checkEqual(int actual, int expected, Violation violation, List<Violation> violations)
	{
		if (actual != expected)
		{
			violations.add(violation);
		}
	}

	private static void checkMaximum(int actual, int maximum, Violation violation, List<Violation> violations)
	{
		if ((actual < 0) || (actual > maximum))
		{
			violations.add(violation);
		}
	}

	public enum Verdict
	{
		WITHIN_BOUNDS,
		AT_CAPACITY,
		VIOLATED
	}

	public enum Violation
	{
		SCHEDULER_CAPACITY_MISMATCH,
		SCHEDULER_REGISTERED_EXCEEDED,
		SCHEDULER_READY_EXCEEDED,
		SCHEDULER_DUE_EXCEEDED,
		SCHEDULER_SHARED_TASK_EXCEEDED,
		MATERIALIZATION_CAPACITY_MISMATCH,
		MATERIALIZATION_RETAINED_EXCEEDED,
		MATERIALIZATION_PERMITS_INVALID,
		MATERIALIZATION_OWNERSHIP_INCONSISTENT,
		MATERIALIZATION_PROFILE_IDENTITY_DUPLICATE,
		MATERIALIZATION_CHARACTER_IDENTITY_DUPLICATE,
		MATERIALIZATION_SNAPSHOT_COUNT_INCONSISTENT,
		NAVIGATION_QUEUE_CAPACITY_MISMATCH,
		NAVIGATION_QUEUE_EXCEEDED,
		NAVIGATION_QUEUE_PEAK_EXCEEDED,
		NAVIGATION_WORKER_CAPACITY_MISMATCH,
		NAVIGATION_WORKERS_EXCEEDED,
		NAVIGATION_WORKER_PEAK_EXCEEDED,
		NAVIGATION_CACHE_CAPACITY_MISMATCH,
		NAVIGATION_CACHE_EXCEEDED,
		NAVIGATION_CACHE_PEAK_EXCEEDED,
		NAVIGATION_ACTIVE_TRACKED_EXCEEDED,
		NAVIGATION_COMPLETED_TRACKED_EXCEEDED,
		NAVIGATION_COOLDOWN_TRACKED_EXCEEDED,
		NAVIGATION_PROGRESS_TRACKED_EXCEEDED
	}

	public record Assessment(Verdict verdict, PhantomActivityOverloadLevel overloadLevel, List<Violation> violations)
	{
		public Assessment
		{
			Objects.requireNonNull(verdict, "verdict");
			Objects.requireNonNull(overloadLevel, "overloadLevel");
			violations = List.copyOf(violations);
		}

		public boolean withinBounds()
		{
			return verdict != Verdict.VIOLATED;
		}
	}
}

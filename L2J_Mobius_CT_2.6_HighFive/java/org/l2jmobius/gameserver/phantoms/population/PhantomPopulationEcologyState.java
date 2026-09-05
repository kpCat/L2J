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
package org.l2jmobius.gameserver.phantoms.population;

import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;

/** Durable independent-life assignment and calendar cursor for one managed identity. */
public record PhantomPopulationEcologyState(
	String catalogHash,
	Preset preset,
	long ecologyGeneration,
	long assignmentOrdinal,
	long assignedAtEpochMinute,
	long virtualJoinEpochMinute,
	long calendarCursorEpochMinute,
	long initialTargetEpochMinute,
	Pace pace,
	int productiveShareBasisPoints,
	int productiveBlockMinutes,
	Personality personality,
	Map<Integer, Integer> initialSocialTraits,
	String scheduleTemplate,
	Disposition disposition,
	long turnoverEligibleEpochMinute,
	long replacesProfileId,
	String currentRequestId,
	long currentWindowTargetEpochMinute,
	long archiveGeneration,
	long archivedAtEpochMinute,
	String archiveReason)
{
	public static final String COMPONENT_TYPE = "population.ecology";
	public static final int SCHEMA_VERSION = 1;

	public PhantomPopulationEcologyState
	{
		requireText(catalogHash, 64, 64, "[0-9a-f]+", "catalog hash");
		Objects.requireNonNull(preset, "Ecology preset must not be null.");
		Objects.requireNonNull(pace, "Ecology pace must not be null.");
		Objects.requireNonNull(personality, "Ecology personality must not be null.");
		initialSocialTraits = Map.copyOf(Objects.requireNonNull(initialSocialTraits, "Ecology Social traits must not be null."));
		Objects.requireNonNull(disposition, "Ecology disposition must not be null.");
		requireText(scheduleTemplate, 1, 32, "[a-z][a-z0-9_.-]*", "schedule template");
		currentRequestId = Objects.requireNonNullElse(currentRequestId, "");
		archiveReason = Objects.requireNonNullElse(archiveReason, "");
		if ((ecologyGeneration < 1) || (assignmentOrdinal < 1) || (assignedAtEpochMinute < 0) || (virtualJoinEpochMinute < 0) || (virtualJoinEpochMinute > assignedAtEpochMinute) || (calendarCursorEpochMinute < virtualJoinEpochMinute) || (initialTargetEpochMinute < virtualJoinEpochMinute) || (turnoverEligibleEpochMinute < assignedAtEpochMinute) || (replacesProfileId < 0) || (productiveShareBasisPoints < 1) || (productiveShareBasisPoints > 10000) || (productiveBlockMinutes < 1) || (productiveBlockMinutes > 1440) || ((1440 % productiveBlockMinutes) != 0) || (archiveGeneration < 0) || (archivedAtEpochMinute < 0))
		{
			throw new IllegalArgumentException("Ecology state contains an invalid bounded value.");
		}
		if ((initialSocialTraits.size() != 6) || initialSocialTraits.entrySet().stream().anyMatch(entry -> (entry.getKey() == null) || (entry.getKey() < 1) || (entry.getKey() > 65535) || (entry.getValue() == null) || (entry.getValue() < PhantomSocialModel.MIN_VALUE) || (entry.getValue() > PhantomSocialModel.MAX_VALUE)))
		{
			throw new IllegalArgumentException("Ecology Social trait vector is invalid.");
		}
		if (!currentRequestId.isEmpty())
		{
			requireText(currentRequestId, 64, 64, "[0-9a-f]+", "request ID");
			if (currentWindowTargetEpochMinute <= calendarCursorEpochMinute)
			{
				throw new IllegalArgumentException("Ecology request target must be after its calendar cursor.");
			}
		}
		else if (currentWindowTargetEpochMinute != 0)
		{
			throw new IllegalArgumentException("Ecology request target requires a request ID.");
		}
		if (disposition == Disposition.MANAGED)
		{
			if ((archiveGeneration != 0) || (archivedAtEpochMinute != 0) || !archiveReason.isEmpty())
			{
				throw new IllegalArgumentException("Managed ecology state cannot contain archive metadata.");
			}
		}
		else
		{
			if ((archiveGeneration < 1) || (archivedAtEpochMinute < assignedAtEpochMinute))
			{
				throw new IllegalArgumentException("Archived ecology state requires durable archive metadata.");
			}
			requireText(archiveReason, 1, 64, "[a-z0-9_.-]+", "archive reason");
			if (!currentRequestId.isEmpty())
			{
				throw new IllegalArgumentException("Archived ecology state cannot retain a catch-up request.");
			}
		}
	}

	public boolean initialCatchupComplete()
	{
		return calendarCursorEpochMinute >= initialTargetEpochMinute;
	}

	public boolean requestPending()
	{
		return !currentRequestId.isEmpty();
	}

	public boolean newcomer(long nowEpochMinute, int newcomerDays)
	{
		return (nowEpochMinute >= virtualJoinEpochMinute) && ((nowEpochMinute - virtualJoinEpochMinute) <= (newcomerDays * 1440L));
	}

	public PhantomPopulationEcologyState advanceCalendar(long nextCursorEpochMinute)
	{
		if (requestPending() || (nextCursorEpochMinute < calendarCursorEpochMinute))
		{
			throw new IllegalStateException("Ecology calendar can only advance without an owned request.");
		}
		return copy(nextCursorEpochMinute, "", 0, disposition, archiveGeneration, archivedAtEpochMinute, archiveReason);
	}

	public PhantomPopulationEcologyState beginRequest(String requestId, long targetEpochMinute)
	{
		if (requestPending() || (targetEpochMinute <= calendarCursorEpochMinute))
		{
			throw new IllegalStateException("Ecology already owns a request or has an invalid target.");
		}
		return copy(calendarCursorEpochMinute, requestId, targetEpochMinute, disposition, archiveGeneration, archivedAtEpochMinute, archiveReason);
	}

	public PhantomPopulationEcologyState completeRequest()
	{
		if (!requestPending())
		{
			throw new IllegalStateException("Ecology has no request to complete.");
		}
		return copy(currentWindowTargetEpochMinute, "", 0, disposition, archiveGeneration, archivedAtEpochMinute, archiveReason);
	}

	public PhantomPopulationEcologyState archived(long generation, long atEpochMinute, String reason)
	{
		if ((disposition != Disposition.MANAGED) || requestPending())
		{
			throw new IllegalStateException("Only an idle managed ecology identity can be archived.");
		}
		return copy(calendarCursorEpochMinute, "", 0, Disposition.ARCHIVED, generation, atEpochMinute, reason);
	}

	private PhantomPopulationEcologyState copy(long cursor, String requestId, long requestTarget, Disposition nextDisposition, long nextArchiveGeneration, long nextArchivedAt, String nextArchiveReason)
	{
		return new PhantomPopulationEcologyState(catalogHash, preset, ecologyGeneration, assignmentOrdinal, assignedAtEpochMinute, virtualJoinEpochMinute, cursor, initialTargetEpochMinute, pace, productiveShareBasisPoints, productiveBlockMinutes, personality, initialSocialTraits, scheduleTemplate, nextDisposition, turnoverEligibleEpochMinute, replacesProfileId, requestId, requestTarget, nextArchiveGeneration, nextArchivedAt, nextArchiveReason);
	}

	private static void requireText(String value, int minimumLength, int maximumLength, String pattern, String label)
	{
		if ((value == null) || (value.length() < minimumLength) || (value.length() > maximumLength) || !value.matches(pattern))
		{
			throw new IllegalArgumentException("Ecology " + label + " is invalid.");
		}
	}

	public enum Preset
	{
		FRESH,
		LIVING,
		MATURE
	}

	public enum Pace
	{
		CASUAL,
		REGULAR,
		FAST,
		OUTLIER
	}

	public enum Personality
	{
		BALANCED,
		SOCIAL,
		COMPETITIVE,
		CAUTIOUS,
		GRINDER,
		TRADER
	}

	public enum Disposition
	{
		MANAGED,
		ARCHIVED
	}
}

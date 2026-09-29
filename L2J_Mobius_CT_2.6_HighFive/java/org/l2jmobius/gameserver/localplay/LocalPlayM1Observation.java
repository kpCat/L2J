package org.l2jmobius.gameserver.localplay;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Pure decisions for one consented M1 observation; no runtime ownership or I/O. */
public final class LocalPlayM1Observation
{
	public enum PositionSource
	{
		LIVE,
		COMMITTED,
		TRANSITION,
		UNAVAILABLE
	}

	public enum Purpose
	{
		LEAVE,
		RETURN
	}

	public record PositionChoice(PositionSource source, PhantomTopologyPoint observed)
	{
	}

	public record Ticket(String token, String runId, int actorObjectId, long profileId, int objectId, long materializedAtNanos, Purpose purpose, PhantomTopologyPoint destination, long expiresAtNanos)
	{
		public boolean valid(String suppliedToken, String suppliedRunId, int suppliedActorObjectId, long suppliedProfileId, int suppliedObjectId, long suppliedEpoch, Purpose suppliedPurpose, PhantomTopologyPoint suppliedDestination, long nowNanos)
		{
			return (nowNanos < expiresAtNanos) && Objects.equals(token, suppliedToken) && Objects.equals(runId, suppliedRunId) && (actorObjectId == suppliedActorObjectId) && (profileId == suppliedProfileId) && (objectId == suppliedObjectId) && (materializedAtNanos == suppliedEpoch) && (purpose == suppliedPurpose) && Objects.equals(destination, suppliedDestination);
		}
	}

	private LocalPlayM1Observation()
	{
	}

	public static PositionChoice select(PhantomTopologyPoint committed, State state, boolean verifiedLive, PhantomTopologyPoint live, boolean storedOwnershipAbsent)
	{
		if ((state == State.ACTIVE) && verifiedLive && (live != null))
		{
			return new PositionChoice(PositionSource.LIVE, live);
		}
		if ((state == State.STORED) && storedOwnershipAbsent)
		{
			return committed == null ? new PositionChoice(PositionSource.UNAVAILABLE, null) : new PositionChoice(PositionSource.COMMITTED, committed);
		}
		if (state == State.STORED) { return new PositionChoice(PositionSource.TRANSITION, null); }
		return new PositionChoice(state == null ? PositionSource.UNAVAILABLE : PositionSource.TRANSITION, null);
	}

	public static boolean contact(boolean worldPresent, boolean clientVisible, long distance2D)
	{
		return worldPresent && clientVisible && (distance2D <= 900);
	}
}

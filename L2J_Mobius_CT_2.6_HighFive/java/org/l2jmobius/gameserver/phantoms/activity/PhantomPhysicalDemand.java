/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.activity;

/** A finite reason to ask the existing lifecycle gate, never travel, reclaim, calendar or capacity authority. */
public record PhantomPhysicalDemand(Kind kind, long sourceProfileId, long sourceEpoch, String owner, long createdMillis, long expiresMillis)
{
	public enum Kind { HUMAN_OBSERVATION, AMBIENT_TARGET, LOGICAL_PARTY_WAKE, ACCEPTED_TRAVEL, NATIVE_CONTEXT }
	public PhantomPhysicalDemand
	{
		if (kind == null || sourceProfileId <= 0 || sourceEpoch < 0 || kind == Kind.LOGICAL_PARTY_WAKE && sourceEpoch == 0 || owner == null || !owner.matches("[A-Za-z0-9_.:-]{1,128}")
			|| createdMillis <= 0 || expiresMillis <= createdMillis || expiresMillis - createdMillis > 60_000)
		{ throw new IllegalArgumentException("Invalid bounded physical demand."); }
	}
	public boolean current(long now) { return now >= createdMillis && now < expiresMillis; }
	public static PhantomPhysicalDemand ambientTarget(long profileId, int occupiedSlots, int target, boolean alreadyWorldPresent, long now)
	{
		if (occupiedSlots < 0 || target < 0) { throw new IllegalArgumentException("Invalid physical target accounting."); }
		if (target == 0 || occupiedSlots >= target && !alreadyWorldPresent) { return null; }
		return new PhantomPhysicalDemand(Kind.AMBIENT_TARGET,profileId,0,"population.ambient",now,Math.addExact(now,10_000));
	}
	public boolean ordinaryAdmission(boolean online, boolean admitted, long now)
	{
		return current(now) && online && admitted && (kind == Kind.AMBIENT_TARGET || kind == Kind.LOGICAL_PARTY_WAKE);
	}
}

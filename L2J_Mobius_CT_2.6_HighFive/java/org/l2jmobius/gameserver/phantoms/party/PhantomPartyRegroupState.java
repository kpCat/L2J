/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;

/** Durable per-actor distance clock, independent of invitation and route generations. */
public record PhantomPartyRegroupState(MemberRef actor, String intentHash, long farSinceMillis, long cooldownUntilMillis, long retryAfterMillis, long lastObservedMillis)
{
	public static final String COMPONENT_TYPE = "party.regroup-clock";
	public static final int SCHEMA_VERSION = 1;
	public static final long FAR_MILLIS = 60_000;
	public static final long COOLDOWN_MILLIS = 300_000;
	private static final int MAGIC = 0x50524731;

	public PhantomPartyRegroupState
	{
		if (actor == null || actor.kind() != MemberKind.PHANTOM || actor.characterObjectId() <= 0 || farSinceMillis < 0 || cooldownUntilMillis < 0 || retryAfterMillis < 0 || lastObservedMillis <= 0 || farSinceMillis > lastObservedMillis)
		{
			throw new IllegalArgumentException("Invalid Phantom regroup clock.");
		}
		PhantomPartyModel.requireHash(intentHash, "Regroup intent");
	}

	public static PhantomPartyRegroupState observe(PhantomPartyRegroupState previous, MemberRef actor, String intentHash, boolean far, long now)
	{
		if (now <= 0 || previous != null && !previous.actor().equals(actor)) { throw new IllegalArgumentException("Foreign regroup actor or clock."); }
		if (previous != null && now < previous.lastObservedMillis()) { return previous; }
		final boolean continuing = previous != null && previous.intentHash().equals(intentHash) && previous.farSinceMillis() > 0;
		return new PhantomPartyRegroupState(actor, intentHash, far ? continuing ? previous.farSinceMillis() : now : 0,
			previous == null ? 0 : previous.cooldownUntilMillis(), previous == null ? 0 : previous.retryAfterMillis(), now);
	}

	public boolean due(long now)
	{
		return farSinceMillis > 0 && now >= lastObservedMillis && now - farSinceMillis > FAR_MILLIS && now >= cooldownUntilMillis && now >= retryAfterMillis;
	}

	/** Persist this reservation by CAS before any native side effect, including after a crash. */
	public PhantomPartyRegroupState reserve(long now)
	{
		if (!due(now)) { throw new IllegalStateException("Regroup is not due."); }
		return new PhantomPartyRegroupState(actor, intentHash, 0, Math.addExact(now, COOLDOWN_MILLIS), Math.addExact(now, 5_000), now);
	}

	public byte[] encode()
	{
		return java.nio.ByteBuffer.allocate(112).putInt(MAGIC).putLong(actor.profileId()).putInt(actor.characterObjectId()).put(intentHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII))
			.putLong(farSinceMillis).putLong(cooldownUntilMillis).putLong(retryAfterMillis).putLong(lastObservedMillis).array();
	}

	public static PhantomPartyRegroupState decode(byte[] payload)
	{
		if (payload == null || payload.length != 112) { throw new IllegalArgumentException("Invalid regroup payload length."); }
		final var input = java.nio.ByteBuffer.wrap(payload);
		if (input.getInt() != MAGIC) { throw new IllegalArgumentException("Unknown regroup payload."); }
		final var actor = MemberRef.phantom(input.getLong(), input.getInt()); final byte[] hash = new byte[64]; input.get(hash);
		return new PhantomPartyRegroupState(actor, new String(hash, java.nio.charset.StandardCharsets.US_ASCII), input.getLong(), input.getLong(), input.getLong(), input.getLong());
	}

	/** A cohesive strict majority of the confirmed roster, already in one native Party. Ties cannot move an actor. */
	public static java.util.List<MemberRef> majority(MemberRef actor, java.util.List<MemberRef> roster, java.util.List<PhantomPartyModel.MemberSnapshot> nativeMembers)
	{
		if (roster == null || roster.size() < 3 || roster.size() > 9 || !roster.contains(actor) || roster.stream().distinct().count() != roster.size() || roster.stream().anyMatch(ref -> ref.kind() != MemberKind.PHANTOM)) { return java.util.List.of(); }
		for (var center : nativeMembers.stream().sorted(java.util.Comparator.comparing(member -> member.ref().stableKey())).toList())
		{
			final var cohort = nativeMembers.stream().filter(member -> !member.dead() && !member.ref().equals(actor) && roster.contains(member.ref()) && member.instanceId() == center.instanceId()
				&& Math.abs((long) member.z() - center.z()) <= 200 && Math.hypot((long) member.x() - center.x(), (long) member.y() - center.y()) <= 1200).map(PhantomPartyModel.MemberSnapshot::ref).distinct().sorted(java.util.Comparator.comparing(MemberRef::stableKey)).toList();
			if (cohort.size() > roster.size() / 2) { return cohort; }
		}
		return java.util.List.of();
	}
}

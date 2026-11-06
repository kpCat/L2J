/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;

/** Durable agreement only. Native object identities and epochs belong to the physical projection. */
public record PhantomLogicalParty(String groupId, long generation, long leaderProfileId, List<Member> members,
	PhantomDomainRef objective, String anchorId, Phase phase, long backgroundCursor, long nextDueMillis, String waitReason)
{
	public static final String COMPONENT_TYPE = "party.logical_group";
	public static final String CLAIM_COMPONENT_TYPE = "party.logical_claim";
	public static final int SCHEMA_VERSION = 1;
	private static final int MAGIC = 0x504c4701;
	public enum Phase { AGREED, BACKGROUND, WAKE_REQUESTED, ASSEMBLING, NATIVE_ACTIVE, DRAINING, DISBANDED }
	public record Member(long profileId, String role, long goalId, long goalRevision)
	{
		public Member
		{
			if (profileId <= 0 || role == null || !role.matches("[a-z][a-z0-9_.-]{0,63}") || goalId <= 0 || goalRevision < 0)
			{ throw new IllegalArgumentException("Invalid logical member."); }
		}
	}
	public PhantomLogicalParty
	{
		PhantomPartyModel.requireHash(groupId, "Logical group");
		members = List.copyOf(members);
		if (generation <= 0 || leaderProfileId <= 0 || members.size() < 2 || members.size() > 9
			|| members.getFirst().profileId() != leaderProfileId || members.stream().map(Member::profileId).distinct().count() != members.size()
			|| objective == null || !"background.farm".equals(objective.namespace()) || !objective.key().matches("[1-9][0-9]{0,8}@[^@\\s]{1,128}")
			|| anchorId == null || !objective.key().endsWith("@" + anchorId) || phase == null || backgroundCursor < 0 || nextDueMillis <= 0
			|| waitReason == null || !waitReason.matches("[A-Z0-9_.-]{0,96}"))
		{ throw new IllegalArgumentException("Invalid logical group aggregate."); }
	}
	public boolean contains(long profileId) { return members.stream().anyMatch(member -> member.profileId() == profileId); }
	public boolean active() { return phase != Phase.DISBANDED; }
	public PhantomLogicalParty transition(Phase next, long cursor, long nextDue, String reason)
	{
		if (cursor < backgroundCursor || !permits(phase, next)) { throw new IllegalArgumentException("Invalid logical group transition."); }
		return new PhantomLogicalParty(groupId, generation, leaderProfileId, members, objective, anchorId, next, cursor, nextDue, reason);
	}
	private static boolean permits(Phase from, Phase to)
	{
		if (from == to) { return true; }
		if (from == Phase.DISBANDED) { return false; }
		if (to == Phase.DISBANDED) { return from == Phase.AGREED || from == Phase.BACKGROUND; }
		return switch (from)
		{
			case AGREED, BACKGROUND -> to == Phase.BACKGROUND || to == Phase.WAKE_REQUESTED;
			case WAKE_REQUESTED -> to == Phase.ASSEMBLING || to == Phase.BACKGROUND;
			case ASSEMBLING -> to == Phase.NATIVE_ACTIVE || to == Phase.DRAINING || to == Phase.BACKGROUND;
			case NATIVE_ACTIVE -> to == Phase.DRAINING;
			case DRAINING -> to == Phase.BACKGROUND || to == Phase.NATIVE_ACTIVE;
			case DISBANDED -> false;
		};
	}
	public byte[] encode()
	{
		try
		{
			final var bytes = new ByteArrayOutputStream();
			try (var out = new DataOutputStream(bytes))
			{
				out.writeInt(MAGIC); out.writeUTF(groupId); out.writeLong(generation); out.writeLong(leaderProfileId); out.writeInt(members.size());
				for (var member : members) { out.writeLong(member.profileId()); out.writeUTF(member.role()); out.writeLong(member.goalId()); out.writeLong(member.goalRevision()); }
				out.writeUTF(objective.key()); out.writeUTF(anchorId); out.writeInt(phase.ordinal()); out.writeLong(backgroundCursor); out.writeLong(nextDueMillis); out.writeUTF(waitReason);
			}
			return bytes.toByteArray();
		}
		catch (IOException failure) { throw new IllegalArgumentException("Cannot encode logical group.", failure); }
	}
	public static PhantomLogicalParty decode(byte[] payload)
	{
		if (payload == null || payload.length > 4096) { throw new IllegalArgumentException("Invalid logical group payload size."); }
		try (var in = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (in.readInt() != MAGIC) { throw new IllegalArgumentException("Unknown logical group schema; native rendezvous v1 is not a logical group."); }
			final String group = in.readUTF(); final long generation = in.readLong(), leader = in.readLong(); final int count = in.readInt();
			if (count < 2 || count > 9) { throw new IllegalArgumentException("Invalid logical roster size."); }
			final var members = new java.util.ArrayList<Member>(count);
			for (int i = 0; i < count; i++) { members.add(new Member(in.readLong(), in.readUTF(), in.readLong(), in.readLong())); }
			final var objective = new PhantomDomainRef("background.farm", in.readUTF()); final String anchor = in.readUTF(); final int phase = in.readInt();
			if (phase < 0 || phase >= Phase.values().length) { throw new IllegalArgumentException("Unknown logical group phase."); }
			final var result = new PhantomLogicalParty(group, generation, leader, members, objective, anchor, Phase.values()[phase], in.readLong(), in.readLong(), in.readUTF());
			if (in.available() != 0) { throw new IllegalArgumentException("Trailing logical group payload."); }
			return result;
		}
		catch (IOException failure) { throw new IllegalArgumentException("Truncated logical group.", failure); }
	}
}

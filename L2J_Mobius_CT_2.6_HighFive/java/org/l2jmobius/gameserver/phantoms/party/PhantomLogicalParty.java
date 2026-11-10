/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;

/** Durable agreement only. Native object identities and epochs belong to the physical projection. */
public record PhantomLogicalParty(String groupId, long generation, long leaderProfileId, List<Member> members,
	PhantomDomainRef objective, String anchorId, Phase phase, long backgroundCursor, long nextDueMillis, String waitReason,
	long expiresAtMillis, List<String> rejectedActivitySources)
{
	public static final String COMPONENT_TYPE = "party.logical_group";
	public static final String CLAIM_COMPONENT_TYPE = "party.logical_claim";
	public static final String ACTIVITY_COMPONENT_TYPE = "party.logical_activity";
	public static final int SCHEMA_VERSION = 2;
	public static final long IDLE_LIFETIME_MILLIS = 600_000;
	static final int MAXIMUM_ACTIVITY_REPLACEMENTS = 8;
	private static final int MAGIC = 0x504c4702;
	private static final int MAGIC_WITH_REJECTIONS = 0x504c4703;
	public PhantomLogicalParty(String groupId, long generation, long leaderProfileId, List<Member> members,
		PhantomDomainRef objective, String anchorId, Phase phase, long backgroundCursor, long nextDueMillis, String waitReason)
	{ this(groupId, generation, leaderProfileId, members, objective, anchorId, phase, backgroundCursor, nextDueMillis, waitReason, Math.addExact(nextDueMillis, IDLE_LIFETIME_MILLIS)); }
	public PhantomLogicalParty(String groupId, long generation, long leaderProfileId, List<Member> members,
		PhantomDomainRef objective, String anchorId, Phase phase, long backgroundCursor, long nextDueMillis, String waitReason, long expiresAtMillis)
	{ this(groupId, generation, leaderProfileId, members, objective, anchorId, phase, backgroundCursor, nextDueMillis, waitReason, expiresAtMillis, List.of()); }
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
		rejectedActivitySources = List.copyOf(rejectedActivitySources);
		if (generation <= 0 || leaderProfileId <= 0 || members.size() < 2 || members.size() > 9
			|| members.getFirst().profileId() != leaderProfileId || members.stream().map(Member::profileId).distinct().count() != members.size()
			|| objective == null || !"background.farm".equals(objective.namespace()) || !validSourceKey(objective.key())
			|| anchorId == null || !objective.key().endsWith("@" + anchorId) || phase == null || backgroundCursor < 0 || nextDueMillis <= 0
			|| expiresAtMillis <= 0 || waitReason == null || !waitReason.matches("[A-Z0-9_.-]{0,96}")
			|| rejectedActivitySources.size() > MAXIMUM_ACTIVITY_REPLACEMENTS || rejectedActivitySources.contains(objective.key())
			|| rejectedActivitySources.stream().anyMatch(source -> !validSourceKey(source))
			|| Set.copyOf(rejectedActivitySources).size() != rejectedActivitySources.size())
		{ throw new IllegalArgumentException("Invalid logical group aggregate."); }
	}
	private static boolean validSourceKey(String key) { return key.length() <= 128 && key.matches("[1-9][0-9]{0,8}@[!-?A-~]+"); }
	public boolean contains(long profileId) { return members.stream().anyMatch(member -> member.profileId() == profileId); }
	public boolean active() { return phase != Phase.DISBANDED; }
	/** A failed activity keeps membership, but cannot reacquire its old execution grant. */
	public boolean activityReplanRequired() { return waitReason.startsWith("REPLAN_") || waitReason.equals("ACTIVITY_PENDING"); }
	public boolean activityPending() { return waitReason.equals("ACTIVITY_PENDING"); }
	public boolean requiresSoloExecution(long now)
	{ return !active() || phase == Phase.DRAINING || activityReplanRequired() || now >= expiresAtMillis && phase != Phase.NATIVE_ACTIVE; }
	public Set<String> activitySourceExclusions()
	{
		final var excluded = new java.util.HashSet<>(rejectedActivitySources);
		excluded.add(objective.key());
		return Set.copyOf(excluded);
	}
	/** Distinct from roster replacement: activity metadata never resets earned time. */
	public PhantomLogicalParty activityReplacement(List<Member> roots, PhantomDomainRef source, long now)
	{
		if (phase != Phase.BACKGROUND || !activityPending()) { throw new IllegalStateException("Activity revision requires its durable idle fence."); }
		if (activitySourceExclusions().contains(source.key()) || rejectedActivitySources.size() >= MAXIMUM_ACTIVITY_REPLACEMENTS
			|| now >= expiresAtMillis || roots.size() != members.size()) { throw new IllegalArgumentException("Activity revision requires another common source within its original budget."); }
		for (int i = 0; i < members.size(); i++)
		{
			final var old = members.get(i); final var next = roots.get(i);
			if (next.profileId() != old.profileId() || !next.role().equals(old.role()) || next.goalId() != old.goalId() || next.goalRevision() <= old.goalRevision())
			{ throw new IllegalArgumentException("Activity revision cannot replace membership or reuse stale roots."); }
		}
		final var rejected = new java.util.ArrayList<>(rejectedActivitySources);
		rejected.add(objective.key());
		return new PhantomLogicalParty(groupId, Math.addExact(generation, 1), leaderProfileId, roots, source, source.key().substring(source.key().indexOf('@') + 1),
			Phase.BACKGROUND, backgroundCursor, now, "", expiresAtMillis, rejected);
	}
	/** Per-member proposal, not an executable root. The aggregate fence and all proposals commit together. */
	public record ActivityRoot(String groupId, long generation, long profileId, long beforeGoalVersion, long deadline,
		org.l2jmobius.gameserver.phantoms.decision.PhantomGoal goal,
		org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation history, boolean pending)
	{
		public ActivityRoot(String groupId, long generation, long profileId, long beforeGoalVersion, long deadline, org.l2jmobius.gameserver.phantoms.decision.PhantomGoal goal,
			org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation history) { this(groupId, generation, profileId, beforeGoalVersion, deadline, goal, history, true); }
		public ActivityRoot settled() { return new ActivityRoot(groupId, generation, profileId, beforeGoalVersion, deadline, goal, history, false); }
		public ActivityRoot
		{
			PhantomPartyModel.requireHash(groupId, "Activity group");
			if (generation < 1 || profileId < 1 || beforeGoalVersion < 0 || deadline <= 0 || goal == null || history == null
				|| !history.componentType().equals(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.COMPONENT_TYPE)
				|| history.componentSchemaVersion() != org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.SCHEMA_VERSION
				|| goal.status() != org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE || !goal.subject().equals(new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", Long.toString(profileId)))
				|| !org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType()))
			{ throw new IllegalArgumentException("Invalid activity proposal."); }
		}
		public byte[] encode()
		{
			try
			{
				final var bytes = new ByteArrayOutputStream();
				try (var out = new DataOutputStream(bytes))
				{
					out.writeInt(1); out.writeUTF(groupId); out.writeLong(generation); out.writeLong(profileId); out.writeLong(beforeGoalVersion); out.writeLong(deadline); out.writeBoolean(pending);
					final byte[] root = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(goal);
					out.writeInt(root.length); out.write(root); out.writeLong(history.expectedRowVersion()); out.writeInt(history.componentSchemaVersion());
					out.writeInt(history.payload().length); out.write(history.payload());
				}
				if (bytes.size() > 4096) { throw new IllegalArgumentException("Activity proposal exceeds component budget."); }
				return bytes.toByteArray();
			}
			catch (IOException failure) { throw new IllegalArgumentException("Cannot encode activity proposal.", failure); }
		}
		public static ActivityRoot decode(byte[] payload)
		{
			if (payload == null || payload.length > 4096) { throw new IllegalArgumentException("Invalid activity proposal size."); }
			try (var in = new DataInputStream(new ByteArrayInputStream(payload)))
			{
				if (in.readInt() != 1) { throw new IllegalArgumentException("Unknown activity proposal schema."); }
				final String group = in.readUTF(); final long generation = in.readLong(), id = in.readLong(), version = in.readLong(), deadline = in.readLong(); final boolean pending = in.readBoolean();
				final int rootSize = in.readInt(); if (rootSize < 1 || rootSize > in.available()) { throw new IllegalArgumentException("Invalid activity root size."); }
				final var goal = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(in.readNBytes(rootSize));
				final long historyVersion = in.readLong(); final int schema = in.readInt(), historySize = in.readInt();
				if (historySize < 1 || historySize != in.available()) { throw new IllegalArgumentException("Invalid activity history size."); }
				return new ActivityRoot(group, generation, id, version, deadline, goal, new org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation(
					org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.COMPONENT_TYPE, historyVersion, schema, in.readNBytes(historySize)), pending);
			}
			catch (IOException failure) { throw new IllegalArgumentException("Invalid activity proposal.", failure); }
		}
	}
	/** A finite retention fact, never native Party membership or action permission. */
	public boolean assemblyPending(long profileId, long goalId, long goalRevision, long now)
	{
		return !activityReplanRequired() && now < expiresAtMillis && (phase == Phase.AGREED || phase == Phase.WAKE_REQUESTED || phase == Phase.ASSEMBLING || phase == Phase.BACKGROUND && nextDueMillis <= now)
			&& members.stream().anyMatch(member -> member.profileId() == profileId && member.goalId() == goalId && goalRevision >= member.goalRevision());
	}
	public PhantomLogicalParty transition(Phase next, long cursor, long nextDue, String reason)
	{
		if (cursor < backgroundCursor || !permits(phase, next) || activityReplanRequired() && phase == Phase.DRAINING && next == Phase.NATIVE_ACTIVE) { throw new IllegalArgumentException("Invalid logical group transition."); }
		final boolean ordinaryDrain = phase == Phase.DRAINING && next == Phase.BACKGROUND && !activityReplanRequired() && rejectedActivitySources.isEmpty()
			&& !"EXPIRED_ASSEMBLY".equals(waitReason) && !"EXPIRED_ASSEMBLY".equals(reason);
		final long expiry = ordinaryDrain
			? Math.max(expiresAtMillis, Math.addExact(nextDue, IDLE_LIFETIME_MILLIS)) : expiresAtMillis;
		// STORE may postpone release, but cannot erase the terminal cause and restart its lifetime.
		final String nextReason = activityReplanRequired() && !activityPending() && (next == Phase.DRAINING || next == Phase.BACKGROUND) && !reason.startsWith("REPLAN_") && !reason.equals("ACTIVITY_PENDING")
			|| phase == Phase.DRAINING && next == Phase.DRAINING && "EXPIRED_ASSEMBLY".equals(waitReason) ? waitReason : reason;
		return new PhantomLogicalParty(groupId, generation, leaderProfileId, members, objective, anchorId, next, cursor, nextDue, nextReason, expiry,
			rejectedActivitySources);
	}
	private static boolean permits(Phase from, Phase to)
	{
		if (from == to) { return true; }
		if (from == Phase.DISBANDED) { return false; }
		if (to == Phase.DISBANDED) { return from == Phase.AGREED || from == Phase.BACKGROUND; }
		return switch (from)
		{
			case AGREED, BACKGROUND -> to == Phase.BACKGROUND || to == Phase.WAKE_REQUESTED || to == Phase.DRAINING;
			case WAKE_REQUESTED -> to == Phase.ASSEMBLING || to == Phase.BACKGROUND || to == Phase.DRAINING;
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
				out.writeInt(rejectedActivitySources.isEmpty() ? MAGIC : MAGIC_WITH_REJECTIONS);
				out.writeUTF(groupId); out.writeLong(generation); out.writeLong(leaderProfileId); out.writeInt(members.size());
				for (var member : members) { out.writeLong(member.profileId()); out.writeUTF(member.role()); out.writeLong(member.goalId()); out.writeLong(member.goalRevision()); }
				out.writeUTF(objective.key()); out.writeUTF(anchorId); out.writeInt(phase.ordinal()); out.writeLong(backgroundCursor); out.writeLong(nextDueMillis); out.writeUTF(waitReason); out.writeLong(expiresAtMillis);
				if (!rejectedActivitySources.isEmpty())
				{
					out.writeByte(rejectedActivitySources.size());
					for (String source : rejectedActivitySources) { out.writeUTF(source); }
				}
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
			final int magic = in.readInt();
			if (magic != MAGIC && magic != MAGIC_WITH_REJECTIONS && magic != 0x504c4701) { throw new IllegalArgumentException("Unknown logical group schema; native rendezvous v1 is not a logical group."); }
			final String group = in.readUTF(); final long generation = in.readLong(), leader = in.readLong(); final int count = in.readInt();
			if (count < 2 || count > 9) { throw new IllegalArgumentException("Invalid logical roster size."); }
			final var members = new java.util.ArrayList<Member>(count);
			for (int i = 0; i < count; i++) { members.add(new Member(in.readLong(), in.readUTF(), in.readLong(), in.readLong())); }
			final var objective = new PhantomDomainRef("background.farm", in.readUTF()); final String anchor = in.readUTF(); final int phase = in.readInt();
			if (phase < 0 || phase >= Phase.values().length) { throw new IllegalArgumentException("Unknown logical group phase."); }
			final long cursor = in.readLong(), due = in.readLong(); final String reason = in.readUTF();
			// Legacy idle expiry is derived from the persisted deadline, never reset by restart.
			final long expiry = magic == 0x504c4701 ? Math.addExact(due, IDLE_LIFETIME_MILLIS) : in.readLong();
			final var rejected = new java.util.ArrayList<String>();
			if (magic == MAGIC_WITH_REJECTIONS)
			{
				final int size = in.readUnsignedByte();
				if (size < 1 || size > MAXIMUM_ACTIVITY_REPLACEMENTS) { throw new IllegalArgumentException("Invalid rejected source count."); }
				for (int i = 0; i < size; i++) { rejected.add(in.readUTF()); }
			}
			final var result = new PhantomLogicalParty(group, generation, leader, members, objective, anchor, Phase.values()[phase], cursor, due, reason, expiry, rejected);
			if (in.available() != 0) { throw new IllegalArgumentException("Trailing logical group payload."); }
			return result;
		}
		catch (IOException failure) { throw new IllegalArgumentException("Truncated logical group.", failure); }
	}
}

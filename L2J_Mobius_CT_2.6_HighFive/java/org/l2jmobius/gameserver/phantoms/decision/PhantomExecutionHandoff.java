/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.decision;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Execution generation is independent of the immutable economic operation revision. */
public record PhantomExecutionHandoff(long generation, PhantomDomainRef owner, Phase phase,
	PhantomDomainRef recipient, String requestId, long requestedAt, long deadline, List<Dependency> dependencies, boolean sourceReady)
{
	public static final int SCHEMA_VERSION = 2;
	public static final int MAX_DEPENDENCIES = 16;
	public enum Phase { ACTIVE, REQUESTED, YIELDED, CHECKPOINTED, RECOVERING }
	public PhantomExecutionHandoff recovered(List<Dependency> proof)
	{
		if (phase != Phase.RECOVERING || proof.isEmpty()) { throw new IllegalStateException("Recipient recovery acknowledgement requires its durable proof."); }
		requireObligations(proof);
		return new PhantomExecutionHandoff(generation, owner, Phase.ACTIVE, null, requestId, requestedAt, deadline, proof, true);
	}
	public record Dependency(String componentType, int schema, long rowVersion, String sha256)
	{
		public Dependency
		{
			if (componentType == null || !componentType.matches("[a-z][a-z0-9_.-]{0,63}") || rowVersion < -1
				|| (rowVersion == -1 ? schema != 0 || !"".equals(sha256) : schema < 1 || schema > 65535 || sha256 == null || !sha256.matches("[a-f0-9]{64}")))
			{ throw new IllegalArgumentException("Invalid execution checkpoint dependency."); }
		}
	}
	public PhantomExecutionHandoff
	{
		Objects.requireNonNull(owner); Objects.requireNonNull(phase); Objects.requireNonNull(requestId);
		dependencies = List.copyOf(dependencies);
		final var types = new HashSet<String>();
		if (generation < 1 || dependencies.size() > MAX_DEPENDENCIES || dependencies.stream().anyMatch(d -> !types.add(d.componentType())))
		{ throw new IllegalArgumentException("Invalid execution generation/checkpoint bound."); }
		if (phase == Phase.ACTIVE || phase == Phase.RECOVERING)
		{
			if (recipient != null) { throw new IllegalArgumentException("Active execution cannot have a pending recipient."); }
			if (sourceReady != (phase == Phase.ACTIVE)) { throw new IllegalArgumentException("Execution recovery readiness disagrees with its phase."); }
		}
		else if (recipient == null || !requestId.matches("[A-Za-z0-9_.:-]{1,96}")
			|| requestedAt < 0 || deadline <= requestedAt || deadline - requestedAt > 600_000)
		{ throw new IllegalArgumentException("Invalid finite execution request."); }
		if (phase == Phase.CHECKPOINTED && dependencies.isEmpty()) { throw new IllegalArgumentException("Checkpoint proof is required."); }
	}
	public static PhantomExecutionHandoff initial(PhantomDomainRef owner)
	{ return new PhantomExecutionHandoff(1, owner, Phase.ACTIVE, null, "", 0, 0, List.of(), true); }
	public static PhantomDomainRef solo(long profileId)
	{
		if (profileId <= 0) { throw new IllegalArgumentException("Profile ID must be positive."); }
		return new PhantomDomainRef("execution.solo", Long.toString(profileId));
	}
	/** REQUEST preserves earned completion rights; YIELD closes those rights before the durable checkpoint. */
	public boolean ownerMayComplete(PhantomDomainRef expected)
	{ return owner.equals(expected) && (phase == Phase.ACTIVE || phase == Phase.REQUESTED || phase == Phase.RECOVERING); }
	public PhantomExecutionHandoff request(PhantomDomainRef target, String id, long now, long until)
	{
		if (phase != Phase.ACTIVE && phase != Phase.RECOVERING) { throw new IllegalStateException("Execution transfer already pending."); }
		return new PhantomExecutionHandoff(generation, owner, Phase.REQUESTED, target, id, now, until, dependencies, phase == Phase.ACTIVE);
	}
	private void requireObligations(List<Dependency> proof)
	{
		if (dependencies.stream().anyMatch(prior -> proof.stream().noneMatch(next -> prior.componentType().equals(next.componentType()))))
		{ throw new IllegalArgumentException("Execution checkpoint cannot discard a prior obligation."); }
	}
	public PhantomExecutionHandoff yield(long now)
	{
		require(Phase.REQUESTED, now);
		return new PhantomExecutionHandoff(generation, owner, Phase.YIELDED, recipient, requestId, requestedAt, deadline, dependencies, sourceReady);
	}
	public PhantomExecutionHandoff checkpoint(List<Dependency> proof, long now)
	{
		require(Phase.YIELDED, now);
		requireObligations(proof);
		return new PhantomExecutionHandoff(generation, owner, Phase.CHECKPOINTED, recipient, requestId, requestedAt, deadline, proof, sourceReady);
	}
	public PhantomExecutionHandoff transfer(long now)
	{
		require(Phase.CHECKPOINTED, now);
		return new PhantomExecutionHandoff(Math.incrementExact(generation), recipient, Phase.RECOVERING, null, requestId, requestedAt, deadline, dependencies, false);
	}
	/** Cancellation/timeout before transfer restores the source under a fresh grant; it must recover its checkpoint. */
	public PhantomExecutionHandoff recoverSource()
	{
		if (phase == Phase.ACTIVE || phase == Phase.RECOVERING) { throw new IllegalStateException("No pending execution transfer to recover."); }
		return new PhantomExecutionHandoff(Math.incrementExact(generation), owner, Phase.RECOVERING, null, requestId, requestedAt, deadline, dependencies, false);
	}
	/** A persisted ACTIVE record is a claim, not proof that a new process recovered its native/economic obligations. */
	public PhantomExecutionHandoff restartRecovery()
	{
		if (phase != Phase.ACTIVE) { throw new IllegalStateException("Pending execution already requires recovery/cooperation."); }
		return new PhantomExecutionHandoff(Math.incrementExact(generation), owner, Phase.RECOVERING, null, requestId, requestedAt, deadline, dependencies, false);
	}
	private void require(Phase expected, long now)
	{
		if (phase != expected || now < requestedAt || now >= deadline) { throw new IllegalStateException("Execution transfer phase/deadline mismatch."); }
	}
	public byte[] encode()
	{
		try
		{
			final var bytes = new ByteArrayOutputStream();
			try (var out = new DataOutputStream(bytes))
			{
				out.writeInt(SCHEMA_VERSION); out.writeLong(generation); writeRef(out, owner); out.writeUTF(phase.name());
				out.writeBoolean(recipient != null); if (recipient != null) { writeRef(out, recipient); }
				out.writeUTF(requestId); out.writeLong(requestedAt); out.writeLong(deadline); out.writeBoolean(sourceReady); out.writeInt(dependencies.size());
				for (var dep : dependencies) { out.writeUTF(dep.componentType()); out.writeInt(dep.schema()); out.writeLong(dep.rowVersion()); out.writeUTF(dep.sha256()); }
			}
			if (bytes.size() > 4096) { throw new IllegalArgumentException("Execution checkpoint exceeds profile payload bound."); }
			return bytes.toByteArray();
		}
		catch (IOException e) { throw new IllegalStateException("Cannot encode execution checkpoint.", e); }
	}
	public static PhantomExecutionHandoff decode(byte[] payload)
	{
		if (payload == null || payload.length > 4096) { throw new IllegalArgumentException("Invalid execution payload bound."); }
		try (var in = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (in.readInt() != SCHEMA_VERSION) { throw new IllegalArgumentException("Unknown execution checkpoint schema; no implicit ownership migration."); }
			final long generation = in.readLong(); final var owner = readRef(in); final var phase = Phase.valueOf(in.readUTF());
			final var recipient = in.readBoolean() ? readRef(in) : null;
			final String id = in.readUTF(); final long start = in.readLong(), end = in.readLong(); final boolean ready = in.readBoolean(); final int count = in.readInt();
			if (count < 0 || count > MAX_DEPENDENCIES) { throw new IllegalArgumentException("Invalid execution dependency count."); }
			final var deps = new java.util.ArrayList<Dependency>(count);
			for (int i = 0; i < count; i++) { deps.add(new Dependency(in.readUTF(), in.readInt(), in.readLong(), in.readUTF())); }
			if (in.available() != 0) { throw new IllegalArgumentException("Trailing execution checkpoint bytes."); }
			return new PhantomExecutionHandoff(generation, owner, phase, recipient, id, start, end, deps, ready);
		}
		catch (IOException e) { throw new IllegalArgumentException("Invalid execution checkpoint payload.", e); }
	}
	private static void writeRef(DataOutputStream out, PhantomDomainRef ref) throws IOException { out.writeUTF(ref.namespace()); out.writeUTF(ref.key()); }
	private static PhantomDomainRef readRef(DataInputStream in) throws IOException { return new PhantomDomainRef(in.readUTF(), in.readUTF()); }
}

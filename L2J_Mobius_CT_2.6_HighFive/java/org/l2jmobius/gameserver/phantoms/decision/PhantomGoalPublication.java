/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.decision;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/** GoalStore's bounded pending publication, consumed only by the existing execution transfer CAS. */
public record PhantomGoalPublication(Status status, long beforeGoalVersion, long requestedAt, long deadline,
	boolean deletion, String goalHash, List<Companion> companions, long committedGeneration)
{
	public static final String COMPONENT_TYPE = "goal.publication";
	public static final String GOAL_COMPONENT = "goal.publication.root";
	public static final String NATIVE_ROOT_COMPONENT = "goal.native-root";
	public static final int SCHEMA_VERSION = 1;
	private static final int MAGIC = 0x50475031;
	public enum Status { PENDING, COMMITTED, CANCELLED }
	public record Companion(String componentType, long expectedVersion, int schemaVersion, String payloadHash)
	{
		public Companion
		{
			new org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation(componentType, expectedVersion, schemaVersion, new byte[0]);
			if (!java.util.Set.of(org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.COMPONENT_TYPE,
				org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.COMPONENT_TYPE,
				org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.COMPONENT_TYPE).contains(componentType) || expectedVersion < -1 || !hash(payloadHash))
			{ throw new IllegalArgumentException("Invalid Goal publication companion."); }
		}
	}
	public PhantomGoalPublication
	{
		companions = List.copyOf(companions);
		if (status == null || beforeGoalVersion < -1 || requestedAt <= 0 || deadline <= requestedAt || deadline - requestedAt > 30_000
			|| goalHash == null || (deletion ? !goalHash.isEmpty() || beforeGoalVersion < 0 : !hash(goalHash)) || companions.size() > 2
			|| companions.stream().map(Companion::componentType).distinct().count() != companions.size()
			|| (status == Status.COMMITTED ? committedGeneration < 2 : committedGeneration != 0))
		{ throw new IllegalArgumentException("Invalid Goal publication request."); }
	}
	public static String companionComponent(int index)
	{ if (index < 0 || index > 1) { throw new IllegalArgumentException("Goal companion index exceeds bound."); } return "goal.publication.companion." + index; }
	public PhantomGoalPublication committed(long generation) { return new PhantomGoalPublication(Status.COMMITTED, beforeGoalVersion, requestedAt, deadline, deletion, goalHash, companions, generation); }
	public PhantomGoalPublication cancelled() { return new PhantomGoalPublication(Status.CANCELLED, beforeGoalVersion, requestedAt, deadline, deletion, goalHash, companions, 0); }
	public String requestId()
	{ return "goal." + digest(new PhantomGoalPublication(Status.PENDING, beforeGoalVersion, requestedAt, deadline, deletion, goalHash, companions, 0).encode()); }
	public byte[] encode()
	{
		try
		{
			final var bytes = new ByteArrayOutputStream();
			try (var out = new DataOutputStream(bytes))
			{
				out.writeInt(MAGIC); out.writeByte(status.ordinal()); out.writeLong(beforeGoalVersion); out.writeLong(requestedAt); out.writeLong(deadline);
				out.writeBoolean(deletion); out.writeUTF(goalHash); out.writeByte(companions.size());
				for (var companion : companions) { out.writeUTF(companion.componentType()); out.writeLong(companion.expectedVersion()); out.writeInt(companion.schemaVersion()); out.writeUTF(companion.payloadHash()); }
				out.writeLong(committedGeneration);
			}
			return bytes.toByteArray();
		}
		catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}
	public static PhantomGoalPublication decode(byte[] payload)
	{
		if (payload == null || payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES) { throw new IllegalArgumentException("Invalid Goal publication size."); }
		try (var in = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (in.readInt() != MAGIC) { throw new IllegalArgumentException("Unknown Goal publication schema."); }
			final int phase = in.readUnsignedByte(); if (phase >= Status.values().length) { throw new IllegalArgumentException("Unknown Goal publication status."); }
			final long before = in.readLong(), requested = in.readLong(), deadline = in.readLong(); final boolean deletion = in.readBoolean(); final String root = in.readUTF();
			final int count = in.readUnsignedByte(); if (count > 2) { throw new IllegalArgumentException("Goal companion bound exceeded."); }
			final var companions = new ArrayList<Companion>();
			for (int i = 0; i < count; i++) { companions.add(new Companion(in.readUTF(), in.readLong(), in.readInt(), in.readUTF())); }
			final var result = new PhantomGoalPublication(Status.values()[phase], before, requested, deadline, deletion, root, companions, in.readLong());
			if (in.available() != 0) { throw new IllegalArgumentException("Trailing Goal publication data."); } return result;
		}
		catch (IOException failure) { throw new IllegalArgumentException("Invalid Goal publication payload.", failure); }
	}
	public static String digest(byte[] payload)
	{
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload)); }
		catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}
	private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}

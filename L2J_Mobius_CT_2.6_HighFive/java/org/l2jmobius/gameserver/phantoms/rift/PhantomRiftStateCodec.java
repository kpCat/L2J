/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Refusal;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Stage;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Status;

public final class PhantomRiftStateCodec
{
	public byte[] encode(Preparation value)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(PhantomRiftModel.SCHEMA_VERSION);
				output.writeLong(value.leaderProfileId());
				output.writeLong(value.goalId());
				output.writeLong(value.goalRevision());
				output.writeByte(value.tierType());
				output.writeByte(value.stage().ordinal());
				output.writeByte(value.status().ordinal());
				string(output, value.rosterHash());
				string(output, value.catalogHash());
				string(output, value.policyHash());
				string(output, value.configHash());
				string(output, value.roleHash());
				string(output, value.missingVacancyKey());
				member(output, value.pendingCandidate());
				output.writeLong(value.pendingInvitationSequence());
				output.writeByte(value.totalAttempts());
				output.writeByte(value.seatAttempts());
				output.writeByte(value.refusals().size());
				for (Refusal refusal : value.refusals())
				{
					member(output, refusal.candidate());
					string(output, refusal.vacancyKey());
					output.writeLong(refusal.refusedEpochMillis());
					output.writeLong(refusal.cooldownUntilEpochMillis());
					string(output, refusal.reasonKey());
				}
				string(output, value.routeHash());
				output.writeLong(value.updatedEpochMillis());
			}
			final byte[] payload = bytes.toByteArray();
			if (payload.length > PhantomRiftModel.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Rift preparation payload exceeds its bound.");
			}
			return payload;
		}
		catch (IllegalArgumentException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Cannot encode Rift preparation.", e);
		}
	}

	public Preparation decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > PhantomRiftModel.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Invalid Rift preparation payload size.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (input.readInt() != PhantomRiftModel.SCHEMA_VERSION)
			{
				throw new IllegalArgumentException("Unknown Rift preparation schema version.");
			}
			final long leaderProfileId = input.readLong();
			final long goalId = input.readLong();
			final long goalRevision = input.readLong();
			final int tierType = input.readUnsignedByte();
			final Stage stage = value(input, Stage.class);
			final Status status = value(input, Status.class);
			final String rosterHash = string(input, 64);
			final String catalogHash = string(input, 64);
			final String policyHash = string(input, 64);
			final String configHash = string(input, 64);
			final String roleHash = string(input, 64);
			final String vacancyKey = string(input, 96);
			final MemberRef pending = member(input);
			final long invitationSequence = input.readLong();
			final int totalAttempts = input.readUnsignedByte();
			final int seatAttempts = input.readUnsignedByte();
			final int refusalCount = input.readUnsignedByte();
			if (refusalCount > PhantomRiftModel.MAX_REFUSALS)
			{
				throw new IllegalArgumentException("Rift refusal history exceeds its bound.");
			}
			final List<Refusal> refusals = new ArrayList<>(refusalCount);
			for (int i = 0; i < refusalCount; i++)
			{
				refusals.add(new Refusal(member(input), string(input, 96), input.readLong(), input.readLong(), string(input, 96)));
			}
			final String routeHash = string(input, 64);
			final long updated = input.readLong();
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("Trailing Rift preparation payload data.");
			}
			return new Preparation(leaderProfileId, goalId, goalRevision, tierType, stage, status, rosterHash, catalogHash, policyHash, configHash, roleHash, vacancyKey, pending, invitationSequence, totalAttempts, seatAttempts, refusals, routeHash, updated);
		}
		catch (IllegalArgumentException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Cannot decode Rift preparation.", e);
		}
	}

	private static void member(DataOutputStream output, MemberRef member) throws Exception
	{
		output.writeBoolean(member != null);
		if (member != null)
		{
			output.writeByte(member.kind().ordinal());
			output.writeLong(member.profileId());
			output.writeInt(member.characterObjectId());
		}
	}

	private static MemberRef member(DataInputStream input) throws Exception
	{
		if (!input.readBoolean())
		{
			return null;
		}
		final MemberKind kind = value(input, MemberKind.class);
		return new MemberRef(kind, input.readLong(), input.readInt());
	}

	private static void string(DataOutputStream output, String value) throws Exception
	{
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 512)
		{
			throw new IllegalArgumentException("Rift state string exceeds its bound.");
		}
		output.writeShort(bytes.length);
		output.write(bytes);
	}

	private static String string(DataInputStream input, int maximum) throws Exception
	{
		final int length = input.readUnsignedShort();
		if (length > maximum)
		{
			throw new IllegalArgumentException("Rift state string exceeds its bound.");
		}
		final byte[] bytes = input.readNBytes(length);
		if (bytes.length != length)
		{
			throw new IllegalArgumentException("Truncated Rift state string.");
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static <E extends Enum<E>> E value(DataInputStream input, Class<E> type) throws Exception
	{
		final int ordinal = input.readUnsignedByte();
		final E[] values = type.getEnumConstants();
		if (ordinal >= values.length)
		{
			throw new IllegalArgumentException("Unknown Rift state enum value.");
		}
		return values[ordinal];
	}
}

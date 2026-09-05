/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Personality;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Preset;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/** Canonical bounded binary codec for {@code population.ecology}. */
public final class PhantomPopulationEcologyStateCodec
{
	private static final int MAGIC_V1 = 0x50454331;

	public byte[] encode(PhantomPopulationEcologyState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(384);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC_V1);
				writeText(output, state.catalogHash());
				output.writeByte(state.preset().ordinal());
				output.writeLong(state.ecologyGeneration());
				output.writeLong(state.assignmentOrdinal());
				output.writeLong(state.assignedAtEpochMinute());
				output.writeLong(state.virtualJoinEpochMinute());
				output.writeLong(state.calendarCursorEpochMinute());
				output.writeLong(state.initialTargetEpochMinute());
				output.writeByte(state.pace().ordinal());
				output.writeShort(state.productiveShareBasisPoints());
				output.writeShort(state.productiveBlockMinutes());
				output.writeByte(state.personality().ordinal());
				output.writeByte(state.initialSocialTraits().size());
				for (Map.Entry<Integer, Integer> trait : new java.util.TreeMap<>(state.initialSocialTraits()).entrySet())
				{
					output.writeShort(trait.getKey());
					output.writeInt(trait.getValue());
				}
				writeText(output, state.scheduleTemplate());
				output.writeByte(state.disposition().ordinal());
				output.writeLong(state.turnoverEligibleEpochMinute());
				output.writeLong(state.replacesProfileId());
				writeText(output, state.currentRequestId());
				output.writeLong(state.currentWindowTargetEpochMinute());
				output.writeLong(state.archiveGeneration());
				output.writeLong(state.archivedAtEpochMinute());
				writeText(output, state.archiveReason());
			}
			final byte[] payload = bytes.toByteArray();
			if (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Ecology state exceeds component payload capacity.");
			}
			return payload;
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not encode ecology state.", e);
		}
	}

	public PhantomPopulationEcologyState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Ecology state payload has invalid size.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (input.readInt() != MAGIC_V1)
			{
				throw new IllegalArgumentException("Ecology state payload has invalid magic.");
			}
			final String catalogHash = readText(input, 64);
			final Preset preset = enumValue(Preset.values(), input.readUnsignedByte(), "preset");
			final long ecologyGeneration = input.readLong();
			final long assignmentOrdinal = input.readLong();
			final long assignedAt = input.readLong();
			final long virtualJoin = input.readLong();
			final long calendarCursor = input.readLong();
			final long initialTarget = input.readLong();
			final Pace pace = enumValue(Pace.values(), input.readUnsignedByte(), "pace");
			final int productiveShare = input.readUnsignedShort();
			final int productiveBlock = input.readUnsignedShort();
			final Personality personality = enumValue(Personality.values(), input.readUnsignedByte(), "personality");
			final int traitCount = input.readUnsignedByte();
			if (traitCount != 6)
			{
				throw new IllegalArgumentException("Ecology state Social trait count is invalid.");
			}
			final Map<Integer, Integer> traits = new LinkedHashMap<>();
			for (int index = 0; index < traitCount; index++)
			{
				if (traits.putIfAbsent(input.readUnsignedShort(), input.readInt()) != null)
				{
					throw new IllegalArgumentException("Ecology state contains duplicate Social traits.");
				}
			}
			final PhantomPopulationEcologyState state = new PhantomPopulationEcologyState(
				catalogHash,
				preset,
				ecologyGeneration,
				assignmentOrdinal,
				assignedAt,
				virtualJoin,
				calendarCursor,
				initialTarget,
				pace,
				productiveShare,
				productiveBlock,
				personality,
				traits,
				readText(input, 32),
				enumValue(Disposition.values(), input.readUnsignedByte(), "disposition"),
				input.readLong(),
				input.readLong(),
				readText(input, 64),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				readText(input, 64));
			if (input.read() != -1)
			{
				throw new IllegalArgumentException("Ecology state payload contains trailing bytes.");
			}
			return state;
		}
		catch (EOFException e)
		{
			throw new IllegalArgumentException("Ecology state payload is truncated.", e);
		}
		catch (IOException e)
		{
			throw new IllegalArgumentException("Could not decode ecology state.", e);
		}
	}

	private static void writeText(DataOutputStream output, String value) throws IOException
	{
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeShort(bytes.length);
		output.write(bytes);
	}

	private static String readText(DataInputStream input, int maximumBytes) throws IOException
	{
		final int length = input.readUnsignedShort();
		if (length > maximumBytes)
		{
			throw new IllegalArgumentException("Ecology state text exceeds its bounded field.");
		}
		final byte[] bytes = input.readNBytes(length);
		if (bytes.length != length)
		{
			throw new EOFException("Ecology state text is truncated.");
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static <T> T enumValue(T[] values, int ordinal, String label)
	{
		if ((ordinal < 0) || (ordinal >= values.length))
		{
			throw new IllegalArgumentException("Ecology " + label + " ordinal is invalid.");
		}
		return values[ordinal];
	}
}

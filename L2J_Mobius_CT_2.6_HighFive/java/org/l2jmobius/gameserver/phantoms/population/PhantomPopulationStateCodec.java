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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.CreationStage;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/**
 * Canonical bounded binary codec for {@code population.state}.
 */
public final class PhantomPopulationStateCodec
{
	private static final int MAGIC = 0x50505731;

	public byte[] encode(PhantomPopulationState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeByte(state.state().ordinal());
				output.writeLong(state.populationGeneration());
				output.writeLong(state.creationOrdinal());
				writeText(output, state.catalogHash());
				output.writeLong(state.deterministicSeed());
				output.writeByte(state.nameAttempt());
				writeText(output, state.reservedAccount());
				writeText(output, state.ownershipToken());
				writeText(output, state.characterName());
				output.writeShort(state.classId());
				output.writeBoolean(state.female());
				output.writeByte(state.face());
				output.writeByte(state.hairColor());
				output.writeByte(state.hairStyle());
				writeText(output, state.scheduleTemplate());
				output.writeShort(state.schedulePhaseMinutes());
				output.writeInt(state.homeMapRegionId());
				output.writeInt(state.creationX());
				output.writeInt(state.creationY());
				output.writeInt(state.creationZ());
				writeNullableInt(output, state.expectedCharacterObjectId());
				writeNullableInt(output, state.actualCharacterObjectId());
				output.writeByte(state.creationStage().ordinal());
				writeText(output, state.initializationHash());
				writeText(output, state.lastFailure());
			}
			final byte[] payload = bytes.toByteArray();
			if (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Population state exceeds component payload capacity.");
			}
			return payload;
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not encode population state.", e);
		}
	}

	public PhantomPopulationState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Population state payload has invalid size.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (input.readInt() != MAGIC)
			{
				throw new IllegalArgumentException("Population state payload has invalid magic.");
			}
			final PhantomPopulationState state = new PhantomPopulationState(
				enumValue(State.values(), input.readUnsignedByte(), "state"),
				input.readLong(),
				input.readLong(),
				readText(input, 64),
				input.readLong(),
				input.readUnsignedByte(),
				readText(input, 14),
				readText(input, 64),
				readText(input, 16),
				input.readUnsignedShort(),
				input.readBoolean(),
				input.readUnsignedByte(),
				input.readUnsignedByte(),
				input.readUnsignedByte(),
				readText(input, 32),
				input.readShort(),
				input.readInt(),
				input.readInt(),
				input.readInt(),
				input.readInt(),
				readNullableInt(input),
				readNullableInt(input),
				enumValue(CreationStage.values(), input.readUnsignedByte(), "creation stage"),
				readText(input, 64),
				readText(input, 96));
			if (input.read() != -1)
			{
				throw new IllegalArgumentException("Population state payload contains trailing bytes.");
			}
			return state;
		}
		catch (EOFException e)
		{
			throw new IllegalArgumentException("Population state payload is truncated.", e);
		}
		catch (IOException e)
		{
			throw new IllegalArgumentException("Could not decode population state.", e);
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
			throw new IllegalArgumentException("Population state text exceeds its bounded field.");
		}
		final byte[] bytes = input.readNBytes(length);
		if (bytes.length != length)
		{
			throw new EOFException("Population state text is truncated.");
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static void writeNullableInt(DataOutputStream output, Integer value) throws IOException
	{
		output.writeBoolean(value != null);
		if (value != null)
		{
			output.writeInt(value);
		}
	}

	private static Integer readNullableInt(DataInputStream input) throws IOException
	{
		return input.readBoolean() ? input.readInt() : null;
	}

	private static <T> T enumValue(T[] values, int ordinal, String label)
	{
		if ((ordinal < 0) || (ordinal >= values.length))
		{
			throw new IllegalArgumentException("Population " + label + " ordinal is invalid.");
		}
		return values[ordinal];
	}
}

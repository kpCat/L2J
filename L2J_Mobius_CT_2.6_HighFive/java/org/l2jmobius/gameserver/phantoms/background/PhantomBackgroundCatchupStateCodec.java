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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes;

/** Strict binary codec for background.catchup schema v1. */
public final class PhantomBackgroundCatchupStateCodec
{
	private static final int MAGIC = 0x50424331;

	public byte[] encode(PhantomBackgroundCatchupState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeInt(PhantomBackgroundCatchupState.SCHEMA_VERSION);
				output.writeUTF(state.status().name());
				output.writeUTF(state.requestId());
				output.writeLong(state.deterministicSeed());
				output.writeLong(state.fromEpochMinute());
				output.writeLong(state.targetEpochMinute());
				output.writeLong(state.cursorEpochMinute());
				output.writeLong(state.planOrdinal());
				output.writeLong(state.intervalOrdinal());
				output.writeLong(state.generation());
				output.writeLong(state.knowledgeGeneration());
				output.writeLong(state.topologyGeneration());
				output.writeLong(state.goalId());
				output.writeLong(state.goalRevision());
				output.writeUTF(state.planIdentity());
				output.writeInt(state.modelVersion());
				writeHashes(output, state.authorityHashes());
				output.writeUTF(state.failureReason());
			}
			return bytes.toByteArray();
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Could not encode Background catch-up state.", exception);
		}
	}
	public PhantomBackgroundCatchupState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > 4096))
		{
			throw new IllegalArgumentException("Invalid Background catch-up payload size.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if ((input.readInt() != MAGIC) || (input.readInt() != PhantomBackgroundCatchupState.SCHEMA_VERSION))
			{
				throw new IllegalArgumentException("Unsupported Background catch-up payload.");
			}
			final PhantomBackgroundCatchupState state = new PhantomBackgroundCatchupState(
				Status.valueOf(input.readUTF()),
				input.readUTF(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readLong(),
				input.readUTF(),
				input.readInt(),
				readHashes(input),
				input.readUTF());
			if (input.read() != -1)
			{
				throw new IllegalArgumentException("Trailing Background catch-up payload bytes.");
			}
			return state;
		}
		catch (EOFException exception)
		{
			throw new IllegalArgumentException("Truncated Background catch-up payload.", exception);
		}
		catch (IOException | RuntimeException exception)
		{
			if (exception instanceof IllegalArgumentException invalid)
			{
				throw invalid;
			}
			throw new IllegalArgumentException("Could not decode Background catch-up payload.", exception);
		}
	}

	private static void writeHashes(DataOutputStream output, Hashes hashes) throws IOException
	{
		output.writeUTF(hashes.knowledge());
		output.writeUTF(hashes.topology());
		output.writeUTF(hashes.progression());
		output.writeUTF(hashes.commerce());
	}

	private static Hashes readHashes(DataInputStream input) throws IOException
	{
		return new Hashes(input.readUTF(), input.readUTF(), input.readUTF(), input.readUTF());
	}
}
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
package org.l2jmobius.gameserver.phantoms.decision;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

public final class PhantomGoalStateCodec
{
	private static final int MAGIC = 0x50475731;
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_KEY_BYTES = 64;
	private static final int MAX_DOMAIN_NAMESPACE_BYTES = 32;
	private static final int MAX_DOMAIN_KEY_BYTES = 128;

	public byte[] encode(PhantomGoal goal)
	{
		return encode(goal, PhantomGoal.SCHEMA_VERSION);
	}

	private byte[] encode(PhantomGoal goal, int schemaVersion)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(384);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeShort(FORMAT_VERSION);
				output.writeShort(schemaVersion);
				output.writeLong(goal.goalId());
				output.writeLong(goal.revision());
				output.writeByte(statusCode(goal.status()));
				writeString(output, goal.goalType(), MAX_KEY_BYTES);
				writeOptionalRef(output, goal.subject());
				writeOptionalRef(output, goal.target());
				output.writeLong(goal.requiredAmount());
				output.writeLong(goal.currentAmount());
				writeOptionalString(output, goal.acquisitionMethod(), MAX_KEY_BYTES);
				output.writeByte(goal.validSources().size());
				for (PhantomDomainRef source : goal.validSources())
				{
					writeRef(output, source);
				}
				writeOptionalRef(output, goal.selectedAnchor());
				writeString(output, goal.purposeKey(), MAX_KEY_BYTES);
				output.writeShort(goal.priority());
				output.writeLong(goal.riskBudget());
				output.writeLong(goal.expenseBudget());
				output.writeLong(goal.deadlineEpochMillis());
				output.writeByte(goal.constraints().size());
				for (Map.Entry<String, Long> constraint : goal.constraints().entrySet())
				{
					writeString(output, constraint.getKey(), MAX_KEY_BYTES);
					output.writeLong(constraint.getValue());
				}
				writeString(output, goal.reasonKey(), MAX_KEY_BYTES);
				if (schemaVersion >= 2)
				{
					writeOptionalString(output, goal.payloadText(), PhantomGoal.MAX_PAYLOAD_UTF8_BYTES);
				}
			}
			final byte[] payload = bytes.toByteArray();
			if (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Encoded goal.runtime payload exceeds 4096 bytes.");
			}
			return payload;
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Unexpected in-memory goal encoding failure.", e);
		}
	}

	public PhantomGoal decode(byte[] payload)
	{
		if (payload == null)
		{
			throw new NullPointerException("Goal payload must not be null.");
		}
		if (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
		{
			throw new IllegalArgumentException("goal.runtime payload exceeds 4096 bytes.");
		}
		try
		{
			final ByteArrayInputStream bytes = new ByteArrayInputStream(payload);
			try (DataInputStream input = new DataInputStream(bytes))
			{
				if (input.readInt() != MAGIC)
				{
					throw new IllegalArgumentException("Unknown goal.runtime magic.");
				}
				if (input.readUnsignedShort() != FORMAT_VERSION)
				{
					throw new IllegalArgumentException("Unknown goal.runtime binary format version.");
				}
				final int schemaVersion = input.readUnsignedShort();
				if ((schemaVersion != 1) && (schemaVersion != PhantomGoal.SCHEMA_VERSION))
				{
					throw new IllegalArgumentException("Unknown goal schema version.");
				}
				final long goalId = input.readLong();
				final long revision = input.readLong();
				final PhantomGoalStatus status = readStatus(input.readUnsignedByte());
				final String goalType = readString(input, bytes, MAX_KEY_BYTES);
				final PhantomDomainRef subject = readOptionalRef(input, bytes);
				final PhantomDomainRef target = readOptionalRef(input, bytes);
				final long requiredAmount = input.readLong();
				final long currentAmount = input.readLong();
				final String acquisitionMethod = readOptionalString(input, bytes, MAX_KEY_BYTES);
				final int sourceCount = input.readUnsignedByte();
				if (sourceCount > PhantomGoal.MAX_VALID_SOURCES)
				{
					throw new IllegalArgumentException("Goal valid source count exceeds 16.");
				}
				final List<PhantomDomainRef> validSources = new ArrayList<>(sourceCount);
				for (int index = 0; index < sourceCount; index++)
				{
					validSources.add(readRef(input, bytes));
				}
				final PhantomDomainRef selectedAnchor = readOptionalRef(input, bytes);
				final String purposeKey = readString(input, bytes, MAX_KEY_BYTES);
				final int priority = input.readUnsignedShort();
				final long riskBudget = input.readLong();
				final long expenseBudget = input.readLong();
				final long deadlineEpochMillis = input.readLong();
				final int constraintCount = input.readUnsignedByte();
				if (constraintCount > PhantomGoal.MAX_CONSTRAINTS)
				{
					throw new IllegalArgumentException("Goal constraint count exceeds 16.");
				}
				final Map<String, Long> constraints = new LinkedHashMap<>(constraintCount);
				for (int index = 0; index < constraintCount; index++)
				{
					final String key = readString(input, bytes, MAX_KEY_BYTES);
					if (constraints.put(key, input.readLong()) != null)
					{
						throw new IllegalArgumentException("Duplicate goal constraint key.");
					}
				}
				final String reasonKey = readString(input, bytes, MAX_KEY_BYTES);
				final String payloadText = schemaVersion >= 2 ? readOptionalString(input, bytes, PhantomGoal.MAX_PAYLOAD_UTF8_BYTES) : null;
				if (bytes.available() != 0)
				{
					throw new IllegalArgumentException("Trailing bytes after goal.runtime payload.");
				}
				final PhantomGoal goal = new PhantomGoal(goalId, goalType, status, subject, target, requiredAmount, currentAmount, acquisitionMethod, validSources, selectedAnchor, purposeKey, priority, riskBudget, expenseBudget, deadlineEpochMillis, constraints, reasonKey, revision, payloadText);
				if (!Arrays.equals(payload, encode(goal, schemaVersion)))
				{
					throw new IllegalArgumentException("Non-canonical goal.runtime payload.");
				}
				return goal;
			}
		}
		catch (EOFException e)
		{
			throw new IllegalArgumentException("Truncated goal.runtime payload.", e);
		}
		catch (IOException e)
		{
			throw new IllegalArgumentException("Invalid goal.runtime payload.", e);
		}
	}

	private static void writeOptionalString(DataOutputStream output, String value, int maximumBytes) throws IOException
	{
		output.writeBoolean(value != null);
		if (value != null)
		{
			writeString(output, value, maximumBytes);
		}
	}

	private static String readOptionalString(DataInputStream input, ByteArrayInputStream bytes, int maximumBytes) throws IOException
	{
		return input.readBoolean() ? readString(input, bytes, maximumBytes) : null;
	}

	private static void writeOptionalRef(DataOutputStream output, PhantomDomainRef reference) throws IOException
	{
		output.writeBoolean(reference != null);
		if (reference != null)
		{
			writeRef(output, reference);
		}
	}

	private static PhantomDomainRef readOptionalRef(DataInputStream input, ByteArrayInputStream bytes) throws IOException
	{
		return input.readBoolean() ? readRef(input, bytes) : null;
	}

	private static void writeRef(DataOutputStream output, PhantomDomainRef reference) throws IOException
	{
		writeString(output, reference.namespace(), MAX_DOMAIN_NAMESPACE_BYTES);
		writeString(output, reference.key(), MAX_DOMAIN_KEY_BYTES);
	}

	private static PhantomDomainRef readRef(DataInputStream input, ByteArrayInputStream bytes) throws IOException
	{
		return new PhantomDomainRef(readString(input, bytes, MAX_DOMAIN_NAMESPACE_BYTES), readString(input, bytes, MAX_DOMAIN_KEY_BYTES));
	}

	private static void writeString(DataOutputStream output, String value, int maximumBytes) throws IOException
	{
		final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
		if ((encoded.length < 1) || (encoded.length > maximumBytes))
		{
			throw new IllegalArgumentException("Encoded string exceeds its bounded goal.runtime field.");
		}
		output.writeShort(encoded.length);
		output.write(encoded);
	}

	private static String readString(DataInputStream input, ByteArrayInputStream bytes, int maximumBytes) throws IOException
	{
		final int length = input.readUnsignedShort();
		if ((length < 1) || (length > maximumBytes) || (length > bytes.available()))
		{
			throw new IllegalArgumentException("Invalid bounded string length in goal.runtime payload.");
		}
		final byte[] encoded = new byte[length];
		input.readFully(encoded);
		return new String(encoded, StandardCharsets.UTF_8);
	}

	private static int statusCode(PhantomGoalStatus status)
	{
		return switch (status)
		{
			case ACTIVE -> 1;
			case COMPLETED -> 2;
			case ABANDONED -> 3;
			case FAILED -> 4;
		};
	}

	private static PhantomGoalStatus readStatus(int code)
	{
		return switch (code)
		{
			case 1 -> PhantomGoalStatus.ACTIVE;
			case 2 -> PhantomGoalStatus.COMPLETED;
			case 3 -> PhantomGoalStatus.ABANDONED;
			case 4 -> PhantomGoalStatus.FAILED;
			default -> throw new IllegalArgumentException("Unknown goal status code.");
		};
	}
}

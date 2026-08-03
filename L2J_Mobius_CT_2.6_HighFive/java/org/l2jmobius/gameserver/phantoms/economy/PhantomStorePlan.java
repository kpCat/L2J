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
package org.l2jmobius.gameserver.phantoms.economy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Durable bounded plan for one visible materialized Phantom store. */
public record PhantomStorePlan(Type type, State state, String title, List<Line> lines, long expiresEpochMillis, String contentHash)
{
	public static final String COMPONENT_TYPE = "economy.store.plan";
	public static final int SCHEMA_VERSION = 1;

	public PhantomStorePlan(Type type, State state, String title, List<Line> lines, long expiresEpochMillis)
	{
		this(type, state, title, lines, expiresEpochMillis, hash(type, title, lines, expiresEpochMillis));
	}

	public PhantomStorePlan
	{
		Objects.requireNonNull(type);
		Objects.requireNonNull(state);
		title = Objects.requireNonNull(title);
		lines = List.copyOf(lines);
		if ((title.getBytes(StandardCharsets.UTF_8).length > 64) || lines.isEmpty() || (lines.size() > 16) || (expiresEpochMillis <= 0) || (contentHash == null) || !contentHash.matches("[0-9a-f]{64}") || !contentHash.equals(hash(type, title, lines, expiresEpochMillis)))
		{
			throw new IllegalArgumentException("Invalid Phantom store plan.");
		}
	}

	public PhantomStorePlan withState(State next)
	{
		return new PhantomStorePlan(type, next, title, lines, expiresEpochMillis, contentHash);
	}

	public PhantomStorePlan withLines(List<Line> remaining)
	{
		return new PhantomStorePlan(type, state, title, remaining, expiresEpochMillis);
	}

	public byte[] encode()
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(512);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeUTF(type.name());
				output.writeUTF(state.name());
				output.writeUTF(title);
				output.writeLong(expiresEpochMillis);
				output.writeUTF(contentHash);
				output.writeInt(lines.size());
				for (Line line : lines)
				{
					output.writeInt(line.objectOrRecipeId());
					output.writeInt(line.itemId());
					output.writeLong(line.count());
					output.writeLong(line.price());
				}
			}
			if (bytes.size() > 4096)
			{
				throw new IllegalArgumentException("Phantom store plan payload is too large.");
			}
			return bytes.toByteArray();
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Could not encode Phantom store plan.", exception);
		}
	}

	public static PhantomStorePlan decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > 4096))
		{
			throw new IllegalArgumentException("Invalid Phantom store plan payload.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			final Type type = Type.valueOf(input.readUTF());
			final State state = State.valueOf(input.readUTF());
			final String title = input.readUTF();
			final long expiry = input.readLong();
			final String hash = input.readUTF();
			final int count = input.readInt();
			if ((count < 1) || (count > 16))
			{
				throw new IllegalArgumentException("Invalid Phantom store plan line count.");
			}
			final java.util.ArrayList<Line> lines = new java.util.ArrayList<>(count);
			for (int i = 0; i < count; i++)
			{
				lines.add(new Line(input.readInt(), input.readInt(), input.readLong(), input.readLong()));
			}
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("Phantom store plan has trailing data.");
			}
			return new PhantomStorePlan(type, state, title, lines, expiry, hash);
		}
		catch (IOException | IllegalArgumentException exception)
		{
			throw new IllegalArgumentException("Could not decode Phantom store plan.", exception);
		}
	}

	private static String hash(Type type, String title, List<Line> lines, long expiry)
	{
		return PhantomEconomyOperation.sha256(type + "|" + title + "|" + lines + "|" + expiry);
	}

	public record Line(int objectOrRecipeId, int itemId, long count, long price)
	{
		public Line
		{
			if ((objectOrRecipeId <= 0) || (itemId <= 0) || (count <= 0) || (price < 0))
			{
				throw new IllegalArgumentException("Invalid Phantom store line.");
			}
		}
	}

	public enum Type
	{
		SELL,
		PACKAGE_SELL,
		BUY,
		MANUFACTURE
	}

	public enum State
	{
		REQUESTED,
		OPEN
	}
}

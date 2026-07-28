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
package org.l2jmobius.gameserver.phantoms.commerce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Durable description of one conservative NPC commerce operation.
 */
public record PhantomCommerceReceipt(String operationKey, long profileId, long goalId, long goalRevision, OperationRequest request, State state, int resumeCount, ConservationFacts before, ConservationFacts expectedAfter)
{
	public static final String COMPONENT_TYPE = "commerce.operation";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_PAYLOAD_BYTES = 4096;

	public PhantomCommerceReceipt
	{
		requireHash(operationKey, "Operation key");
		if ((profileId <= 0) || (goalId <= 0) || (goalRevision < 0))
		{
			throw new IllegalArgumentException("Receipt profile/goal IDs must be positive and revision nonnegative.");
		}
		Objects.requireNonNull(request, "Operation request must not be null.");
		Objects.requireNonNull(state, "Receipt state must not be null.");
		if ((resumeCount < 0) || (resumeCount > 1))
		{
			throw new IllegalArgumentException("Receipt may be resumed at most once.");
		}
		Objects.requireNonNull(before, "Before facts must not be null.");
		Objects.requireNonNull(expectedAfter, "Expected-after facts must not be null.");
		final String expectedKey = operationKey(profileId, goalId, goalRevision, request);
		if (!operationKey.equals(expectedKey))
		{
			throw new IllegalArgumentException("Receipt operation key does not match its canonical request.");
		}
	}

	public static PhantomCommerceReceipt prepared(long profileId, long goalId, long goalRevision, OperationRequest request, ConservationFacts before, ConservationFacts expectedAfter)
	{
		return new PhantomCommerceReceipt(operationKey(profileId, goalId, goalRevision, request), profileId, goalId, goalRevision, request, State.PREPARED, 0, before, expectedAfter);
	}

	public PhantomCommerceReceipt withState(State replacement)
	{
		if (!state.canTransitionTo(replacement))
		{
			throw new IllegalStateException("Invalid commerce receipt transition " + state + " -> " + replacement + ".");
		}
		return new PhantomCommerceReceipt(operationKey, profileId, goalId, goalRevision, request, replacement, resumeCount, before, expectedAfter);
	}

	public PhantomCommerceReceipt resumed()
	{
		if ((state != State.COMMITTING) || (resumeCount != 0))
		{
			throw new IllegalStateException("Receipt cannot be resumed again.");
		}
		return new PhantomCommerceReceipt(operationKey, profileId, goalId, goalRevision, request, state, 1, before, expectedAfter);
	}

	public Reconciliation reconcile(ConservationFacts current)
	{
		Objects.requireNonNull(current);
		if (current.equals(expectedAfter))
		{
			return Reconciliation.EXACT_AFTER;
		}
		if (current.equals(before))
		{
			return Reconciliation.EXACT_BEFORE;
		}
		if (firstEffectOnly(current))
		{
			return Reconciliation.FIRST_EFFECT_ONLY;
		}
		return Reconciliation.INCONSISTENT;
	}

	private boolean firstEffectOnly(ConservationFacts current)
	{
		return switch (request.kind())
		{
			case BUY -> (current.primaryCount() == expectedAfter.primaryCount()) //
				&& (current.secondaryCount() == before.secondaryCount()) //
				&& (current.objectCount() == before.objectCount()) //
				&& current.samePosition(before);
			case SELL -> (current.primaryCount() == before.primaryCount()) //
				&& (current.secondaryCount() == expectedAfter.secondaryCount()) //
				&& (current.objectCount() == expectedAfter.objectCount()) //
				&& current.samePosition(before);
			case TELEPORT -> (current.primaryCount() == expectedAfter.primaryCount()) //
				&& (current.secondaryCount() == before.secondaryCount()) //
				&& (current.objectCount() == before.objectCount()) //
				&& current.samePosition(before);
		};
	}

	public byte[] encode()
	{
		final String payload = String.join("\n", List.of(
			"v=1",
			"key=" + operationKey,
			"profile=" + profileId,
			"goal=" + goalId,
			"revision=" + goalRevision,
			"state=" + state,
			"resume=" + resumeCount,
			"request=" + request.encode(),
			"before=" + before.encode(),
			"after=" + expectedAfter.encode()));
		final byte[] encoded = payload.getBytes(StandardCharsets.UTF_8);
		if (encoded.length > MAX_PAYLOAD_BYTES)
		{
			throw new IllegalArgumentException("Commerce receipt exceeds 4096 bytes.");
		}
		return encoded;
	}

	public static PhantomCommerceReceipt decode(byte[] payload)
	{
		Objects.requireNonNull(payload);
		if (payload.length > MAX_PAYLOAD_BYTES)
		{
			throw new IllegalArgumentException("Commerce receipt exceeds 4096 bytes.");
		}
		final String[] lines = new String(payload, StandardCharsets.UTF_8).split("\\n", -1);
		if ((lines.length != 10) || !"v=1".equals(lines[0]))
		{
			throw new IllegalArgumentException("Unsupported commerce receipt payload.");
		}
		return new PhantomCommerceReceipt(
			value(lines[1], "key"),
			Long.parseLong(value(lines[2], "profile")),
			Long.parseLong(value(lines[3], "goal")),
			Long.parseLong(value(lines[4], "revision")),
			OperationRequest.decode(value(lines[7], "request")),
			State.valueOf(value(lines[5], "state")),
			Integer.parseInt(value(lines[6], "resume")),
			ConservationFacts.decode(value(lines[8], "before")),
			ConservationFacts.decode(value(lines[9], "after")));
	}

	public static String operationKey(long profileId, long goalId, long goalRevision, OperationRequest request)
	{
		if ((profileId <= 0) || (goalId <= 0) || (goalRevision < 0))
		{
			throw new IllegalArgumentException("Operation key identifiers are invalid.");
		}
		return sha256(profileId + "|" + goalId + "|" + goalRevision + "|" + request.kind() + "|" + request.canonicalHash());
	}

	private static String value(String line, String key)
	{
		final String prefix = key + "=";
		if (!line.startsWith(prefix))
		{
			throw new IllegalArgumentException("Missing commerce receipt field " + key + ".");
		}
		return line.substring(prefix.length());
	}

	private static String sha256(String value)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	private static void requireHash(String value, String label)
	{
		if ((value == null) || !value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException(label + " must be lowercase SHA-256.");
		}
	}

	public enum OperationKind
	{
		BUY,
		SELL,
		TELEPORT
	}

	public enum State
	{
		PREPARED,
		COMMITTING,
		COMMITTED,
		ABORTED,
		INCONSISTENT;

		private boolean canTransitionTo(State replacement)
		{
			if (this == replacement)
			{
				return true;
			}
			return switch (this)
			{
				case PREPARED -> (replacement == COMMITTING) || (replacement == ABORTED) || (replacement == INCONSISTENT);
				case COMMITTING -> (replacement == COMMITTED) || (replacement == INCONSISTENT);
				case COMMITTED -> replacement == INCONSISTENT;
				case ABORTED, INCONSISTENT -> false;
			};
		}

		public boolean terminal()
		{
			return (this == COMMITTED) || (this == ABORTED) || (this == INCONSISTENT);
		}
	}

	public enum Reconciliation
	{
		EXACT_AFTER,
		EXACT_BEFORE,
		FIRST_EFFECT_ONLY,
		INCONSISTENT
	}

	public record OperationRequest(OperationKind kind, int npcTemplateId, int npcObjectId, int listId, int itemId, int itemObjectId, long count, long amount, int feeItemId, long feeCount, int ordinal, String listName, int destinationX, int destinationY, int destinationZ)
	{
		public OperationRequest
		{
			Objects.requireNonNull(kind, "Operation kind must not be null.");
			if ((npcTemplateId <= 0) || (npcObjectId <= 0) || (count < 0) || (amount < 0) || (feeItemId < 0) || (feeCount < 0) || (ordinal < 0))
			{
				throw new IllegalArgumentException("Operation request contains invalid numeric facts.");
			}
			if ((listName == null) || (listName.length() > 96))
			{
				throw new IllegalArgumentException("Operation list name is invalid.");
			}
			switch (kind)
			{
				case BUY ->
				{
					if ((listId <= 0) || (itemId <= 0) || (itemObjectId != 0) || (count <= 0) || (listName.length() != 0))
					{
						throw new IllegalArgumentException("Invalid canonical buy request.");
					}
				}
				case SELL ->
				{
					if ((listId <= 0) || (itemId <= 0) || (itemObjectId <= 0) || (count <= 0) || (listName.length() != 0))
					{
						throw new IllegalArgumentException("Invalid canonical sell request.");
					}
				}
				case TELEPORT ->
				{
					if ((listId != 0) || (itemId != 0) || (itemObjectId != 0) || (count != 0) || listName.isBlank())
					{
						throw new IllegalArgumentException("Invalid canonical teleport request.");
					}
				}
			}
		}

		public String canonicalHash()
		{
			return sha256(encode());
		}

		private String encode()
		{
			final String encodedName = Base64.getUrlEncoder().withoutPadding().encodeToString(listName.getBytes(StandardCharsets.UTF_8));
			return kind + "," + npcTemplateId + "," + npcObjectId + "," + listId + "," + itemId + "," + itemObjectId + "," + count + "," + amount + "," + feeItemId + "," + feeCount + "," + ordinal + "," + encodedName + "," + destinationX + "," + destinationY + "," + destinationZ;
		}

		private static OperationRequest decode(String value)
		{
			final String[] fields = value.split(",", -1);
			if (fields.length != 15)
			{
				throw new IllegalArgumentException("Invalid canonical commerce request.");
			}
			return new OperationRequest(
				OperationKind.valueOf(fields[0]),
				Integer.parseInt(fields[1]),
				Integer.parseInt(fields[2]),
				Integer.parseInt(fields[3]),
				Integer.parseInt(fields[4]),
				Integer.parseInt(fields[5]),
				Long.parseLong(fields[6]),
				Long.parseLong(fields[7]),
				Integer.parseInt(fields[8]),
				Long.parseLong(fields[9]),
				Integer.parseInt(fields[10]),
				new String(Base64.getUrlDecoder().decode(fields[11]), StandardCharsets.UTF_8),
				Integer.parseInt(fields[12]),
				Integer.parseInt(fields[13]),
				Integer.parseInt(fields[14]));
		}
	}

	public record ConservationFacts(long primaryCount, long secondaryCount, long objectCount, int instanceId, int x, int y, int z)
	{
		public ConservationFacts
		{
			if ((primaryCount < 0) || (secondaryCount < 0) || (objectCount < 0))
			{
				throw new IllegalArgumentException("Conservation counts must not be negative.");
			}
		}

		private boolean samePosition(ConservationFacts other)
		{
			return (instanceId == other.instanceId) && (x == other.x) && (y == other.y) && (z == other.z);
		}

		private String encode()
		{
			return primaryCount + "," + secondaryCount + "," + objectCount + "," + instanceId + "," + x + "," + y + "," + z;
		}

		private static ConservationFacts decode(String value)
		{
			final String[] fields = value.split(",", -1);
			if (fields.length != 7)
			{
				throw new IllegalArgumentException("Invalid commerce conservation facts.");
			}
			return new ConservationFacts(Long.parseLong(fields[0]), Long.parseLong(fields[1]), Long.parseLong(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]), Integer.parseInt(fields[6]));
		}
	}
}

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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Immutable identity and resource contract for durable Phantom economy work. */
public record PhantomEconomyOperation(Identity identity, Kind kind, State state, String authorityHash, String intentHash, byte[] beforePayload, byte[] intentPayload, long createdEpochMillis, long updatedEpochMillis, long expiresEpochMillis, long rowVersion)
{
	public static final int MAX_PAYLOAD_BYTES = 4096;

	public PhantomEconomyOperation
	{
		Objects.requireNonNull(identity);
		Objects.requireNonNull(kind);
		Objects.requireNonNull(state);
		authorityHash = hash(authorityHash, "authority hash");
		intentHash = hash(intentHash, "intent hash");
		beforePayload = payload(beforePayload, "before payload");
		intentPayload = payload(intentPayload, "intent payload");
		if ((createdEpochMillis < 0) || (updatedEpochMillis < createdEpochMillis) || (expiresEpochMillis < createdEpochMillis) || (rowVersion < 0))
		{
			throw new IllegalArgumentException("Invalid economy operation epochs or row version.");
		}
	}

	@Override
	public byte[] beforePayload()
	{
		return beforePayload.clone();
	}

	@Override
	public byte[] intentPayload()
	{
		return intentPayload.clone();
	}

	public String operationId()
	{
		return identity.operationId(kind, authorityHash, intentHash);
	}

	public boolean terminal()
	{
		return state.terminal();
	}

	public boolean safelyExpirable(long nowEpochMillis)
	{
		return ((state == State.PREPARED) || (state == State.RESERVED)) && (expiresEpochMillis <= nowEpochMillis);
	}

	public static byte[] utf8Payload(String value)
	{
		return payload(Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8), "UTF-8 payload");
	}

	private static String hash(String value, String name)
	{
		if ((value == null) || !value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid " + name + ".");
		}
		return value;
	}

	private static byte[] payload(byte[] value, String name)
	{
		if ((value == null) || (value.length > MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Invalid economy " + name + ".");
		}
		return value.clone();
	}

	public enum Kind
	{
		SELF_CRAFT,
		ITEM_ENCHANT
	}

	public enum State
	{
		PREPARED(false),
		RESERVED(false),
		DISPATCHING(false),
		OBSERVING(false),
		COMMITTED(true),
		ABORTED(true),
		EXPIRED(true),
		INCONSISTENT(true);

		private final boolean _terminal;

		State(boolean terminal)
		{
			_terminal = terminal;
		}

		public boolean terminal()
		{
			return _terminal;
		}

		public boolean canTransitionTo(State next)
		{
			return switch (this)
			{
				case PREPARED -> (next == RESERVED) || (next == ABORTED) || (next == EXPIRED);
				case RESERVED -> (next == DISPATCHING) || (next == ABORTED) || (next == EXPIRED);
				case DISPATCHING -> (next == OBSERVING) || (next == COMMITTED) || (next == ABORTED) || (next == INCONSISTENT);
				case OBSERVING -> (next == COMMITTED) || (next == INCONSISTENT);
				case COMMITTED, ABORTED, EXPIRED, INCONSISTENT -> false;
			};
		}
	}

	public enum ResourceKind
	{
		ADENA,
		ITEM_COUNT,
		ITEM_OBJECT,
		RECIPE,
		SKILL,
		CAPACITY
	}

	public enum Result
	{
		SUCCESS,
		CRAFT_FAILED,
		SAFE_FAILURE,
		BLESSED_RESET,
		DESTROYED_WITH_CRYSTALS,
		ERROR,
		ACTIVE_REQUIRED,
		CONFLICT,
		STALE_AUTHORITY,
		INCONSISTENT
	}

	public record Identity(long profileId, int characterObjectId, long goalId, long goalRevision, int attempt, String intentId, long activityGeneration, long activityTick)
	{
		public Identity
		{
			if ((profileId <= 0) || (characterObjectId <= 0) || (goalId <= 0) || (goalRevision < 0) || (attempt < 1) || (attempt > 32) || (activityGeneration < 0) || (activityTick < 0) || (intentId == null) || !intentId.matches("[A-Za-z0-9._:-]{1,96}"))
			{
				throw new IllegalArgumentException("Invalid economy operation identity.");
			}
		}

		public String operationId(Kind kind, String authorityHash, String intentHash)
		{
			return sha256(profileId + "|" + characterObjectId + "|" + goalId + "|" + goalRevision + "|" + Objects.requireNonNull(kind).name() + "|" + attempt + "|" + intentId + "|" + authorityHash + "|" + intentHash + "|" + activityGeneration + "|" + activityTick);
		}
	}

	public record Reservation(long profileId, int ownerObjectId, int ownerClassIndex, ResourceKind kind, int objectId, int itemId, long count, long expectedCount, int expectedEnchantLevel, String expectedLocation)
	{
		public static final Comparator<Reservation> CANONICAL_ORDER = Comparator.comparing(Reservation::canonicalKey);

		public Reservation(long profileId, int ownerObjectId, ResourceKind kind, int objectId, int itemId, long count, long expectedCount, int expectedEnchantLevel, String expectedLocation)
		{
			this(profileId, ownerObjectId, 0, kind, objectId, itemId, count, expectedCount, expectedEnchantLevel, expectedLocation);
		}

		public Reservation
		{
			Objects.requireNonNull(kind);
			if ((profileId <= 0) || (ownerObjectId <= 0) || (ownerClassIndex < 0) || (ownerClassIndex > 3) || (objectId < 0) || (itemId < 0) || (count < 0) || (expectedCount < 0) || (expectedEnchantLevel < 0) || (expectedLocation == null) || (expectedLocation.length() > 16))
			{
				throw new IllegalArgumentException("Invalid economy reservation.");
			}
			if (((kind == ResourceKind.ITEM_OBJECT) && (objectId == 0)) || (((kind == ResourceKind.ITEM_COUNT) || (kind == ResourceKind.ADENA)) && ((itemId == 0) || (count == 0))))
			{
				throw new IllegalArgumentException("Incomplete economy reservation resource.");
			}
		}

		public String canonicalKey()
		{
			return switch (kind)
			{
				case ADENA -> String.format("%010d:ADENA", ownerObjectId);
				case ITEM_COUNT -> String.format("%010d:ITEM:%010d", ownerObjectId, itemId);
				case ITEM_OBJECT -> String.format("%010d:OBJECT:%010d", ownerObjectId, objectId);
				case RECIPE -> String.format("%010d:CLASS:%02d:RECIPE:%010d", ownerObjectId, ownerClassIndex, itemId);
				case SKILL -> String.format("%010d:CLASS:%02d:SKILL:%010d", ownerObjectId, ownerClassIndex, itemId);
				case CAPACITY -> String.format("%010d:CAPACITY", ownerObjectId);
			};
		}
	}

	public record Audit(Result result, String reason, byte[] consequencePayload, long itemsConsumed, long itemsProduced, long adenaSource, long adenaSink, long crystalsProduced, long targetItemsDestroyed)
	{
		public Audit(Result result, String reason, byte[] consequencePayload)
		{
			this(result, reason, consequencePayload, 0, 0, 0, 0, 0, 0);
		}

		public Audit
		{
			Objects.requireNonNull(result);
			if ((reason == null) || !reason.matches("[a-z0-9._-]{1,96}") || (itemsConsumed < 0) || (itemsProduced < 0) || (adenaSource < 0) || (adenaSink < 0) || (crystalsProduced < 0) || (targetItemsDestroyed < 0))
			{
				throw new IllegalArgumentException("Invalid economy audit reason.");
			}
			consequencePayload = payload(consequencePayload, "audit payload");
		}

		@Override
		public byte[] consequencePayload()
		{
			return consequencePayload.clone();
		}
	}

	public static List<Reservation> canonicalReservations(List<Reservation> reservations, int maximum)
	{
		if ((reservations == null) || reservations.isEmpty() || (reservations.size() > maximum))
		{
			throw new IllegalArgumentException("Invalid economy reservation count.");
		}
		final List<Reservation> result = new ArrayList<>(reservations);
		result.sort(Reservation.CANONICAL_ORDER);
		for (int i = 1; i < result.size(); i++)
		{
			if (result.get(i - 1).canonicalKey().equals(result.get(i).canonicalKey()))
			{
				throw new IllegalArgumentException("Duplicate economy reservation key.");
			}
		}
		return List.copyOf(result);
	}

	public static String sha256(String value)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}
}

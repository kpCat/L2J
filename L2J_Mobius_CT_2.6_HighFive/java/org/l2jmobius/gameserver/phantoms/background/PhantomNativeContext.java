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
package org.l2jmobius.gameserver.phantoms.background;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;

/** Parallel native scalar proof. Neither B4 rewards nor owned receipt bytes are extended. */
public record PhantomNativeContext(Identity identity, Phase phase, int beforePoints, Eligibility beforeEligibility,
	int afterPoints, Eligibility afterEligibility, long stateRowVersion, String stateDigest,
	long preparedRowVersion, long epoch, String receiptDigest)
{
	public static final String COMPONENT_TYPE = "background.native-context";
	public static final int SCHEMA_VERSION = 1;
	private static final int MAGIC = 0x504e4331; // PNC1
	private static final int MAX_BYTES = 512;
	private static final String EMPTY_DIGEST = "0".repeat(64);

	public enum Phase { UNKNOWN, COMPLETED, PENDING }
	public enum Eligibility { UNKNOWN, SUPPORTED, VITALITY_REQUIRES_NATIVE }

	/** Only fresh native capture can supply policy eligibility; canonical zero is not a native value. */
	public record Capture(int vitalityPoints, Eligibility eligibility)
	{
		public Capture
		{
			validatePoints(vitalityPoints);
			Objects.requireNonNull(eligibility, "eligibility");
			if ((vitalityPoints == 0) || ((eligibility == Eligibility.SUPPORTED) && (vitalityPoints != 1)))
			{
				throw new IllegalArgumentException("Invalid attested native vitality capture.");
			}
		}
	}

	public PhantomNativeContext
	{
		Objects.requireNonNull(identity, "identity");
		Objects.requireNonNull(phase, "phase");
		Objects.requireNonNull(beforeEligibility, "beforeEligibility");
		Objects.requireNonNull(afterEligibility, "afterEligibility");
		validatePoints(beforePoints); validatePoints(afterPoints);
		if ((stateRowVersion < 0) || !digestValid(stateDigest) || !digestValid(receiptDigest)
			|| ((beforeEligibility == Eligibility.SUPPORTED) && (beforePoints != 1))
			|| ((afterEligibility == Eligibility.SUPPORTED) && (afterPoints != 1)))
		{
			throw new IllegalArgumentException("Invalid native scalar proof.");
		}
		if (phase == Phase.PENDING)
		{
			if ((preparedRowVersion < 0) || (preparedRowVersion > stateRowVersion) || receiptDigest.equals(EMPTY_DIGEST) || (afterPoints == 0))
			{
				throw new IllegalArgumentException("Invalid pending native scalar proof.");
			}
		}
		else if ((beforePoints != afterPoints) || (beforeEligibility != afterEligibility) || (preparedRowVersion != -1) || (epoch != 0)
			|| !receiptDigest.equals(EMPTY_DIGEST) || ((phase == Phase.UNKNOWN) != (afterEligibility == Eligibility.UNKNOWN)))
		{
			throw new IllegalArgumentException("Contradictory completed native scalar proof.");
		}
	}

	public static PhantomNativeContext completed(Identity identity, int points, Eligibility eligibility, long version, byte[] state)
	{
		return new PhantomNativeContext(identity, eligibility == Eligibility.UNKNOWN ? Phase.UNKNOWN : Phase.COMPLETED,
			points, eligibility, points, eligibility, version, digest(state), -1, 0, EMPTY_DIGEST);
	}

	public static PhantomNativeContext pending(Identity identity, int before, Eligibility beforeEligibility, Capture after,
		long version, byte[] state, long epoch, byte[] receipt)
	{
		return new PhantomNativeContext(identity, Phase.PENDING, before, beforeEligibility, after.vitalityPoints(), after.eligibility(),
			version, digest(state), version, epoch, digest(receipt));
	}

	public boolean simulationEligible() { return (phase == Phase.COMPLETED) && (afterEligibility == Eligibility.SUPPORTED) && (afterPoints == 1); }
	public boolean binds(Identity expected, long version, byte[] payload) { return identity.equals(expected) && (stateRowVersion == version) && stateDigest.equals(digest(payload)); }
	public boolean matchesReceipt(long version, long expectedEpoch, byte[] payload) { return (phase == Phase.PENDING) && (preparedRowVersion == version) && (epoch == expectedEpoch) && receiptDigest.equals(digest(payload)); }
	public boolean matchesBefore(int canonicalPoints) { return canonicalPoints == beforePoints; }
	public boolean matchesAfter(int canonicalPoints) { return canonicalPoints == afterPoints; }

	public PhantomNativeContext complete(boolean after, long version, byte[] state)
	{
		if (phase != Phase.PENDING) { throw new IllegalStateException("Native scalar is not pending."); }
		return completed(identity, after ? afterPoints : beforePoints, after ? afterEligibility : beforeEligibility, version, state);
	}

	public PhantomNativeContext rebind(long version, byte[] state)
	{
		return new PhantomNativeContext(identity, phase, beforePoints, beforeEligibility, afterPoints, afterEligibility,
			version, digest(state), preparedRowVersion, epoch, receiptDigest);
	}

	public byte[] encode()
	{
		try
		{
			final var bytes = new ByteArrayOutputStream();
			try (var output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC); output.writeInt(SCHEMA_VERSION);
				output.writeLong(identity.profileId()); output.writeInt(identity.characterObjectId()); output.writeInt(identity.classIndex()); output.writeInt(identity.activeClassId()); output.writeInt(identity.raceOrdinal());
				output.writeByte(phase.ordinal()); output.writeInt(beforePoints); output.writeByte(beforeEligibility.ordinal()); output.writeInt(afterPoints); output.writeByte(afterEligibility.ordinal());
				output.writeLong(stateRowVersion); output.writeUTF(stateDigest); output.writeLong(preparedRowVersion); output.writeLong(epoch); output.writeUTF(receiptDigest);
			}
			return bytes.toByteArray();
		}
		catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}

	public static PhantomNativeContext decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > MAX_BYTES)) { throw new IllegalArgumentException("Native scalar payload bound."); }
		try (var input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if ((input.readInt() != MAGIC) || (input.readInt() != SCHEMA_VERSION)) { throw new IllegalArgumentException("Native scalar schema."); }
			final var identity = new Identity(input.readLong(), input.readInt(), input.readInt(), input.readInt(), input.readInt());
			final var phase = Phase.values()[input.readUnsignedByte()];
			final int before = input.readInt(); final var beforeEligibility = Eligibility.values()[input.readUnsignedByte()];
			final int after = input.readInt(); final var afterEligibility = Eligibility.values()[input.readUnsignedByte()];
			final var result = new PhantomNativeContext(identity, phase, before, beforeEligibility, after, afterEligibility, input.readLong(), input.readUTF(), input.readLong(), input.readLong(), input.readUTF());
			if (input.available() != 0) { throw new IllegalArgumentException("Trailing native scalar bytes."); }
			return result;
		}
		catch (IOException | IndexOutOfBoundsException failure) { throw new IllegalArgumentException("Invalid native scalar payload.", failure); }
	}

	public static void validatePoints(int points)
	{
		if ((points < 0) || (points > 20000)) { throw new IllegalArgumentException("Invalid canonical vitality points."); }
	}

	private static boolean digestValid(String value) { return (value != null) && value.matches("[0-9a-f]{64}"); }
	private static String digest(byte[] value)
	{
		Objects.requireNonNull(value, "payload");
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
		catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}
}

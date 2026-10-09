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
	long preparedRowVersion, long epoch, String receiptDigest, PhantomBackgroundSimulationPolicy beforePolicy, PhantomBackgroundSimulationPolicy afterPolicy)
{
	public static final String COMPONENT_TYPE = "background.native-context";
	public static final int SCHEMA_VERSION = 1;
	private static final int MAGIC = 0x504e4331; // PNC1
	private static final int MAX_BYTES = 1024;
    private static final int MAGIC_V2 = 0x504e4332;
    public int schemaVersion() { return beforePolicy == null && afterPolicy == null ? 1 : 2; }
	private static final String EMPTY_DIGEST = "0".repeat(64);

	public enum Phase { UNKNOWN, COMPLETED, PENDING }
	public enum Eligibility { UNKNOWN, SUPPORTED, VITALITY_REQUIRES_NATIVE, POSITION_REQUIRES_NATIVE }

	/** Only fresh native capture can supply policy eligibility; canonical zero is not a native value. */
	public record Capture(int vitalityPoints, Eligibility eligibility, PhantomBackgroundSimulationPolicy policy)
	{
		public Capture(int vitalityPoints, Eligibility eligibility) { this(vitalityPoints, eligibility, null); }
		public Capture
		{
			validatePoints(vitalityPoints);
			Objects.requireNonNull(eligibility, "eligibility");
            if (policy != null && policy.canonicalPoints() != vitalityPoints) { throw new IllegalArgumentException("Capture policy points changed."); }
			if ((vitalityPoints == 0) || ((eligibility == Eligibility.SUPPORTED) && (vitalityPoints != 1)))
			{
				throw new IllegalArgumentException("Invalid attested native vitality capture.");
			}
		}
	}

    public PhantomNativeContext(Identity identity, Phase phase, int beforePoints, Eligibility beforeEligibility,
        int afterPoints, Eligibility afterEligibility, long stateRowVersion, String stateDigest,
        long preparedRowVersion, long epoch, String receiptDigest)
    {
        this(identity, phase, beforePoints, beforeEligibility, afterPoints, afterEligibility, stateRowVersion,
            stateDigest, preparedRowVersion, epoch, receiptDigest, null, null);
    }
	public PhantomNativeContext
	{
		Objects.requireNonNull(identity, "identity");
		Objects.requireNonNull(phase, "phase");
		Objects.requireNonNull(beforeEligibility, "beforeEligibility");
		Objects.requireNonNull(afterEligibility, "afterEligibility");
		validatePoints(beforePoints); validatePoints(afterPoints);
        if (beforePolicy != null && beforePolicy.canonicalPoints() != beforePoints || afterPolicy != null && afterPolicy.canonicalPoints() != afterPoints
            || phase != Phase.PENDING && !Objects.equals(beforePolicy, afterPolicy))
        { throw new IllegalArgumentException("Contradictory native policy capsule."); }
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
        return pending(identity, before, beforeEligibility, after, version, state, epoch, receipt, null);
	}

    public static PhantomNativeContext completed(Identity identity, Capture capture, long version, byte[] state)
    {
        return new PhantomNativeContext(identity, capture.eligibility() == Eligibility.UNKNOWN ? Phase.UNKNOWN : Phase.COMPLETED,
            capture.vitalityPoints(), capture.eligibility(), capture.vitalityPoints(), capture.eligibility(),
            version, digest(state), -1, 0, EMPTY_DIGEST, capture.policy(), capture.policy());
    }
    public static PhantomNativeContext pending(Identity identity, int before, Eligibility beforeEligibility, Capture after,
        long version, byte[] state, long epoch, byte[] receipt, PhantomBackgroundSimulationPolicy beforePolicy)
    {
        return new PhantomNativeContext(identity, Phase.PENDING, before, beforeEligibility, after.vitalityPoints(), after.eligibility(),
            version, digest(state), version, epoch, digest(receipt), beforePolicy, after.policy());
    }
    public boolean permits(PhantomBackgroundSimulationPolicy.Operation operation, String currentRules)
    {
        if (phase != Phase.COMPLETED) { return false; }
        if (afterPolicy == null) { return operation != PhantomBackgroundSimulationPolicy.Operation.REST && simulationEligible(); }
        return afterPolicy.permits(operation, currentRules);
    }
	public boolean simulationEligible() { return (phase == Phase.COMPLETED) && (afterEligibility == Eligibility.SUPPORTED) && (afterPoints == 1); }
	public boolean binds(Identity expected, long version, byte[] payload) { return identity.equals(expected) && (stateRowVersion == version) && stateDigest.equals(digest(payload)); }
	public boolean matchesReceipt(long version, long expectedEpoch, byte[] payload) { return (phase == Phase.PENDING) && (preparedRowVersion == version) && (epoch == expectedEpoch) && receiptDigest.equals(digest(payload)); }
	public boolean matchesBefore(int canonicalPoints) { return canonicalPoints == beforePoints; }
	public boolean matchesAfter(int canonicalPoints) { return canonicalPoints == afterPoints; }

	public PhantomNativeContext complete(boolean after, long version, byte[] state)
	{
		if (phase != Phase.PENDING) { throw new IllegalStateException("Native scalar is not pending."); }
		final var policy = after ? afterPolicy : beforePolicy;
        return policy == null ? completed(identity, after ? afterPoints : beforePoints, after ? afterEligibility : beforeEligibility, version, state)
            : completed(identity, new Capture(after ? afterPoints : beforePoints, after ? afterEligibility : beforeEligibility, policy), version, state);
	}

	public PhantomNativeContext rebind(long version, byte[] state)
	{
		return new PhantomNativeContext(identity, phase, beforePoints, beforeEligibility, afterPoints, afterEligibility,
			version, digest(state), preparedRowVersion, epoch, receiptDigest, beforePolicy, afterPolicy);
	}

	public byte[] encode()
	{
		try
		{
			final var bytes = new ByteArrayOutputStream();
			try (var output = new DataOutputStream(bytes))
			{
				output.writeInt(schemaVersion() == 1 ? MAGIC : MAGIC_V2); output.writeInt(schemaVersion());
				output.writeLong(identity.profileId()); output.writeInt(identity.characterObjectId()); output.writeInt(identity.classIndex()); output.writeInt(identity.activeClassId()); output.writeInt(identity.raceOrdinal());
				output.writeByte(phase.ordinal()); output.writeInt(beforePoints); output.writeByte(beforeEligibility.ordinal()); output.writeInt(afterPoints); output.writeByte(afterEligibility.ordinal());
				output.writeLong(stateRowVersion); output.writeUTF(stateDigest); output.writeLong(preparedRowVersion); output.writeLong(epoch); output.writeUTF(receiptDigest);
                if (schemaVersion() == 2)
                {
                    output.writeBoolean(beforePolicy != null); if (beforePolicy != null) { beforePolicy.write(output); }
                    output.writeBoolean(afterPolicy != null); if (afterPolicy != null) { afterPolicy.write(output); }
                }
			}
			if (bytes.size() > MAX_BYTES) { throw new IllegalArgumentException("Native policy payload bound."); }
            return bytes.toByteArray();
		}
		catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}

	public static PhantomNativeContext decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > MAX_BYTES)) { throw new IllegalArgumentException("Native scalar payload bound."); }
		try (var input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			final int magic = input.readInt(), schema = input.readInt();
            if ((schema == 1 && magic != MAGIC) || (schema == 2 && magic != MAGIC_V2) || schema < 1 || schema > 2 || (schema == 1 && payload.length > 512)) { throw new IllegalArgumentException("Native scalar schema."); }
			final var identity = new Identity(input.readLong(), input.readInt(), input.readInt(), input.readInt(), input.readInt());
			final var phase = Phase.values()[input.readUnsignedByte()];
			final int before = input.readInt(); final var beforeEligibility = Eligibility.values()[input.readUnsignedByte()];
			final int after = input.readInt(); final var afterEligibility = Eligibility.values()[input.readUnsignedByte()];
			final long version = input.readLong(); final String stateDigest = input.readUTF();
            final long prepared = input.readLong(), epoch = input.readLong(); final String receipt = input.readUTF();
            final var beforePolicy = schema == 2 && input.readBoolean() ? PhantomBackgroundSimulationPolicy.read(input) : null;
            final var afterPolicy = schema == 2 && input.readBoolean() ? PhantomBackgroundSimulationPolicy.read(input) : null;
            if (schema == 2 && beforePolicy == null && afterPolicy == null) { throw new IllegalArgumentException("Empty V2 policy."); }
            final var result = new PhantomNativeContext(identity, phase, before, beforeEligibility, after, afterEligibility,
                version, stateDigest, prepared, epoch, receipt, beforePolicy, afterPolicy);
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

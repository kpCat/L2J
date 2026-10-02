package org.l2jmobius.gameserver.phantoms.background;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;

/** Bounded receipt for the existing background transaction, not a second canonical writer. */
public record PhantomOwnedStoreIntent(long preparedRowVersion, long materializedAtNanos, State targetState, String previousState, String previousPayloadHash, String skillsHash, PhantomBackgroundState before, PhantomBackgroundState after)
{
	public static final String COMPONENT_TYPE = "background.owned-store";
	public static final int SCHEMA_VERSION = 1;
	private static final int MAGIC = 0x504f5331;
	private static final int MAX_BYTES = 65536;

	public PhantomOwnedStoreIntent
	{
		Objects.requireNonNull(before); Objects.requireNonNull(after); Objects.requireNonNull(previousPayloadHash); Objects.requireNonNull(previousState); Objects.requireNonNull(skillsHash);
		if ((preparedRowVersion < 0) || !before.identity().equals(after.identity()) || !previousState.matches("ABSENT|MATERIALIZED|READY|DEAD") || !previousPayloadHash.matches("[0-9a-f]{64}|ABSENT") || !skillsHash.matches("[0-9a-f]{64}") || ((targetState != State.MATERIALIZED) && (targetState != State.READY) && (targetState != State.DEAD)) || ((targetState == State.DEAD) && (after.vitals().currentHp() != 0)) || ((targetState == State.READY) && (after.vitals().currentHp() == 0)))
		{
			throw new IllegalArgumentException("OWNED_STORE_INTENT_INVALID");
		}
	}

	public byte[] encode()
	{
		try
		{
			final var bytes = new ByteArrayOutputStream();
			final var codec = new PhantomBackgroundStateCodec();
			try (var out = new DataOutputStream(bytes))
			{
				out.writeInt(MAGIC); out.writeLong(preparedRowVersion); out.writeLong(materializedAtNanos); out.writeByte(targetState.ordinal()); out.writeUTF(previousState); out.writeUTF(previousPayloadHash); out.writeUTF(skillsHash);
				for (var state : java.util.List.of(before, after)) { final byte[] payload = codec.encode(state); out.writeInt(payload.length); out.write(payload); }
			}
			if (bytes.size() > MAX_BYTES) { throw new IllegalArgumentException("OWNED_STORE_INTENT_TOO_LARGE"); }
			return bytes.toByteArray();
		}
		catch (IOException failure) { throw new IllegalArgumentException("OWNED_STORE_INTENT_ENCODE", failure); }
	}

	public static PhantomOwnedStoreIntent decode(byte[] payload)
	{
		if ((payload == null) || (payload.length > MAX_BYTES)) { throw new IllegalArgumentException("OWNED_STORE_INTENT_SIZE"); }
		try (var in = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (in.readInt() != MAGIC) { throw new IllegalArgumentException("OWNED_STORE_INTENT_MAGIC"); }
			final long version = in.readLong(); final long epoch = in.readLong(); final int target = in.readUnsignedByte(); final String previous = in.readUTF(); final String hash = in.readUTF(); final String skills = in.readUTF();
			if (target >= State.values().length) { throw new IllegalArgumentException("OWNED_STORE_INTENT_TARGET"); }
			final var codec = new PhantomBackgroundStateCodec();
			final var before = codec.decode(readPayload(in)); final var after = codec.decode(readPayload(in));
			if (in.available() != 0) { throw new IllegalArgumentException("OWNED_STORE_INTENT_TRAILING_BYTES"); }
			return new PhantomOwnedStoreIntent(version, epoch, State.values()[target], previous, hash, skills, before, after);
		}
		catch (IOException failure) { throw new IllegalArgumentException("OWNED_STORE_INTENT_DECODE", failure); }
	}

	private static byte[] readPayload(DataInputStream in) throws IOException
	{
		final int count = in.readInt();
		if ((count < 1) || (count > MAX_BYTES) || (count > in.available())) { throw new IllegalArgumentException("OWNED_STORE_INTENT_PAYLOAD_SIZE"); }
		return in.readNBytes(count);
	}
}

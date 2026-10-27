package org.l2jmobius.gameserver.phantoms.background;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/** Bounded receipt for the existing background transaction, not a second canonical writer. */
public record PhantomOwnedStoreIntent(long preparedRowVersion, long materializedAtNanos, State targetState, String previousState, String previousPayloadHash, String skillsHash, PhantomBackgroundState before, PhantomBackgroundState after, RawDeadVitals rawBeforeVitals)
{
	public static final String COMPONENT_TYPE = "background.owned-store";
	public static final int SCHEMA_VERSION = 1;
	private static final int MAGIC = 0x504f5331; // Existing POS1 receipt layout.
	private static final int RAW_MAGIC = 0x504f5332; // POS2: POS1 fields followed by exact raw corpse MP/CP.
	private static final int COMPRESSED_MAGIC = 0x504f5a31; // POZ1: size + one zlib stream containing exact plain receipt bytes.
	private static final int MAX_LEGACY_BYTES = 65536;
	// Two bounded state payloads and <=191 bytes of header/raw tail; existing256 bound is unchanged.
	private static final int MAX_EXPANDED_BYTES = (2 * PhantomProfileComponent.MAX_PAYLOAD_BYTES) + 256;
	private static final int COMPRESSED_HEADER_BYTES = 8;

	public PhantomOwnedStoreIntent(long preparedRowVersion, long materializedAtNanos, State targetState, String previousState, String previousPayloadHash, String skillsHash, PhantomBackgroundState before, PhantomBackgroundState after)
	{
		this(preparedRowVersion, materializedAtNanos, targetState, previousState, previousPayloadHash, skillsHash, before, after, null);
	}

	/** Exact SQL preimage; deliberately separate from the strict bounded Background vitals. */
	public record RawDeadVitals(double currentMp, double currentCp)
	{
		public RawDeadVitals
		{
			if (!Double.isFinite(currentMp) || !Double.isFinite(currentCp) || currentMp < 0 || currentCp < 0) { throw new IllegalArgumentException("OWNED_STORE_RAW_DEAD_VITALS_INVALID"); }
		}
		public boolean projectsTo(PhantomBackgroundState.Vitals vitals)
		{
			return vitals.currentHp() == 0 && currentMp > vitals.maximumMp() && vitals.maximumMp() > 0
				&& Math.min(currentMp, vitals.maximumMp()) == vitals.currentMp() && Math.min(currentCp, vitals.maximumCp()) == vitals.currentCp();
		}
	}

	public PhantomOwnedStoreIntent
	{
		Objects.requireNonNull(before); Objects.requireNonNull(after); Objects.requireNonNull(previousPayloadHash); Objects.requireNonNull(previousState); Objects.requireNonNull(skillsHash);
		if ((preparedRowVersion < 0) || (!before.identity().equals(after.identity()) && !nativeWarriorTransition(before, after)) || !previousState.matches("ABSENT|MATERIALIZED|READY|DEAD") || !previousPayloadHash.matches("[0-9a-f]{64}|ABSENT") || !skillsHash.matches("[0-9a-f]{64}") || ((targetState != State.MATERIALIZED) && (targetState != State.READY) && (targetState != State.DEAD)) || ((targetState == State.DEAD) && (after.vitals().currentHp() != 0)) || ((targetState == State.READY) && (after.vitals().currentHp() == 0)))
		{
			throw new IllegalArgumentException("OWNED_STORE_INTENT_INVALID");
		}
		if (rawBeforeVitals != null && (preparedRowVersion != 0 || materializedAtNanos <= 0 || targetState != State.DEAD || !previousState.equals("ABSENT") || !previousPayloadHash.equals("ABSENT")
			|| before.state() != State.DEAD || before.identity().classIndex() != 0 || !before.identity().equals(after.identity()) || !before.progress().equals(after.progress()) || !rawBeforeVitals.projectsTo(before.vitals())))
		{
			throw new IllegalArgumentException("OWNED_STORE_RAW_DEAD_RECEIPT_INVALID");
		}
	}

	/** Compatibility name retained for existing callers; all native next-edge receipt shapes are supported. */
	public static boolean nativeWarriorTransition(PhantomBackgroundState before, PhantomBackgroundState after)
	{
		return nativeProfessionTransition(before, after);
	}

	/** Receipt shape only; prepare additionally attests the current career owner and saved branch. */
	public static boolean nativeProfessionTransition(PhantomBackgroundState before, PhantomBackgroundState after)
	{
		final var from = before.identity(); final var to = after.identity();
		final var current = org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(from.activeClassId());
		final var target = org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(to.activeClassId());
		return from.profileId() == to.profileId() && from.characterObjectId() == to.characterObjectId()
			&& from.classIndex() == 0 && to.classIndex() == 0 && from.raceOrdinal() == to.raceOrdinal() && current != null && target != null
			&& current.getRace().ordinal() == from.raceOrdinal() && target.getRace() == current.getRace() && current.getNextClasses().contains(target)
			&& target != org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.INSPECTOR
			&& after.progress().level() >= org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.minimumLevel(target);
	}

	public byte[] encode()
	{
		final byte[] plain = encodePlain();
		if (plain.length <= PhantomProfileComponent.MAX_PAYLOAD_BYTES) { return plain; }
		try
		{
			final var bytes = new ByteArrayOutputStream(PhantomProfileComponent.MAX_PAYLOAD_BYTES);
			try (var out = new DataOutputStream(bytes); var deflater = new Deflater())
			{
				out.writeInt(COMPRESSED_MAGIC); out.writeInt(plain.length);
				deflater.setInput(plain); deflater.finish();
				final byte[] buffer = new byte[512];
				while (!deflater.finished())
				{
					final int count = deflater.deflate(buffer);
					if ((count <= 0) || (bytes.size() + count > PhantomProfileComponent.MAX_PAYLOAD_BYTES)) { throw new IllegalArgumentException("OWNED_STORE_INTENT_TOO_LARGE"); }
					out.write(buffer, 0, count);
				}
			}
			return bytes.toByteArray();
		}
		catch (IOException failure) { throw new IllegalArgumentException("OWNED_STORE_INTENT_ENCODE", failure); }
	}

	private byte[] encodePlain()
	{
		try
		{
			final var bytes = new ByteArrayOutputStream();
			final var codec = new PhantomBackgroundStateCodec();
			try (var out = new DataOutputStream(bytes))
			{
				out.writeInt(rawBeforeVitals == null ? MAGIC : RAW_MAGIC); out.writeLong(preparedRowVersion); out.writeLong(materializedAtNanos); out.writeByte(targetState.ordinal()); out.writeUTF(previousState); out.writeUTF(previousPayloadHash); out.writeUTF(skillsHash);
				for (var state : java.util.List.of(before, after)) { final byte[] payload = codec.encode(state); out.writeInt(payload.length); out.write(payload); }
				if (rawBeforeVitals != null) { out.writeDouble(rawBeforeVitals.currentMp()); out.writeDouble(rawBeforeVitals.currentCp()); }
			}
			if (bytes.size() > MAX_EXPANDED_BYTES) { throw new IllegalArgumentException("OWNED_STORE_INTENT_TOO_LARGE"); }
			return bytes.toByteArray();
		}
		catch (IOException failure) { throw new IllegalArgumentException("OWNED_STORE_INTENT_ENCODE", failure); }
	}

	public static PhantomOwnedStoreIntent decode(byte[] payload)
	{
		if ((payload == null) || (payload.length > MAX_LEGACY_BYTES)) { throw new IllegalArgumentException("OWNED_STORE_INTENT_SIZE"); }
		try (var in = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			final int magic = in.readInt();
			if (magic == MAGIC || magic == RAW_MAGIC) { return decodePlain(payload); }
			if (magic != COMPRESSED_MAGIC) { throw new IllegalArgumentException("OWNED_STORE_INTENT_MAGIC"); }
			if ((payload.length <= COMPRESSED_HEADER_BYTES) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)) { throw new IllegalArgumentException("OWNED_STORE_INTENT_SIZE"); }
			final int size = in.readInt();
			if ((size < 1) || (size > MAX_EXPANDED_BYTES)) { throw new IllegalArgumentException("OWNED_STORE_INTENT_EXPANDED_SIZE"); }
			return decodePlain(inflate(payload, size));
		}
		catch (IOException failure) { throw new IllegalArgumentException("OWNED_STORE_INTENT_DECODE", failure); }
	}

	private static byte[] inflate(byte[] payload, int size)
	{
		// One extra bounded byte distinguishes exact output from a forged shorter declared length.
		final byte[] expanded = new byte[size + 1];
		try (var inflater = new Inflater())
		{
			inflater.setInput(payload, COMPRESSED_HEADER_BYTES, payload.length - COMPRESSED_HEADER_BYTES);
			int count = 0;
			while (!inflater.finished())
			{
				final int next = inflater.inflate(expanded, count, expanded.length - count);
				count += next;
				if ((count > size) || ((next == 0) && !inflater.finished())) { throw new IllegalArgumentException("OWNED_STORE_INTENT_COMPRESSION_INVALID"); }
			}
			if ((count != size) || (inflater.getRemaining() != 0)) { throw new IllegalArgumentException("OWNED_STORE_INTENT_COMPRESSION_TRAILING_OR_SIZE"); }
			return Arrays.copyOf(expanded, size);
		}
		catch (DataFormatException failure) { throw new IllegalArgumentException("OWNED_STORE_INTENT_COMPRESSION_INVALID", failure); }
	}

	private static PhantomOwnedStoreIntent decodePlain(byte[] payload)
	{
		try (var in = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			final int magic = in.readInt();
			if (magic != MAGIC && magic != RAW_MAGIC) { throw new IllegalArgumentException("OWNED_STORE_INTENT_MAGIC"); }
			final long version = in.readLong(); final long epoch = in.readLong(); final int target = in.readUnsignedByte(); final String previous = in.readUTF(); final String hash = in.readUTF(); final String skills = in.readUTF();
			if (target >= State.values().length) { throw new IllegalArgumentException("OWNED_STORE_INTENT_TARGET"); }
			final var codec = new PhantomBackgroundStateCodec();
			final var before = codec.decode(readPayload(in)); final var after = codec.decode(readPayload(in));
			final var raw = magic == RAW_MAGIC ? new RawDeadVitals(in.readDouble(), in.readDouble()) : null;
			if (in.available() != 0) { throw new IllegalArgumentException("OWNED_STORE_INTENT_TRAILING_BYTES"); }
			return new PhantomOwnedStoreIntent(version, epoch, State.values()[target], previous, hash, skills, before, after, raw);
		}
		catch (IOException failure) { throw new IllegalArgumentException("OWNED_STORE_INTENT_DECODE", failure); }
	}

	private static byte[] readPayload(DataInputStream in) throws IOException
	{
		final int count = in.readInt();
		if ((count < 1) || (count > MAX_LEGACY_BYTES) || (count > in.available())) { throw new IllegalArgumentException("OWNED_STORE_INTENT_PAYLOAD_SIZE"); }
		return in.readNBytes(count);
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.MemoryRecord;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.RelationshipRecord;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;

/**
 * Canonical compact binary codec for social.state schema 1.
 */
public final class PhantomSocialStateCodec
{
	private static final int MAGIC = 0x534F4331;
	private static final int FORMAT_VERSION = 1;
	private static final int HASH_BYTES = 32;

	public byte[] encode(SocialState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(PhantomProfileComponent.MAX_PAYLOAD_BYTES);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeByte(FORMAT_VERSION);
				writeHash(output, state.authorityHash());
				output.writeLong(state.personalitySeed());
				output.writeLong(state.logicalMinute());
				output.writeByte(state.traits().size());
				output.writeByte(state.relationships().size());
				output.writeByte(state.memories().size());
				for (var trait : state.traits().entrySet())
				{
					output.writeShort(trait.getKey());
					output.writeShort(trait.getValue());
				}
				for (RelationshipRecord relationship : state.relationships())
				{
					writeSubject(output, relationship.subject());
					for (int value : relationship.values())
					{
						output.writeShort(value);
					}
					for (int counter : relationship.agreements())
					{
						output.writeShort(counter);
					}
					output.writeLong(relationship.lastDecayMinute());
					output.writeLong(relationship.lastInteractionMinute());
				}
				for (MemoryRecord memory : state.memories())
				{
					writeHash(output, memory.eventId());
					output.writeShort(memory.eventCode());
					writeSubject(output, memory.subject());
					output.writeLong(memory.happenedMinute());
					output.writeLong(memory.expiryMinute());
					output.writeShort(memory.salience());
					output.writeShort(memory.magnitude());
					writeHash(output, memory.evidenceHash());
				}
			}
			final byte[] result = bytes.toByteArray();
			if (result.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Encoded social.state exceeds 4096 bytes.");
			}
			return result;
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not encode social.state.", e);
		}
	}

	public SocialState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Social state payload size is invalid.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if ((input.readInt() != MAGIC) || (input.readUnsignedByte() != FORMAT_VERSION))
			{
				throw new IllegalArgumentException("Unknown social.state format.");
			}
			final String authorityHash = readHash(input);
			final long seed = input.readLong();
			final long logicalMinute = input.readLong();
			final int traitCount = input.readUnsignedByte();
			final int relationshipCount = input.readUnsignedByte();
			final int memoryCount = input.readUnsignedByte();
			if ((traitCount < 1) || (traitCount > PhantomSocialModel.MAX_TRAITS) || (relationshipCount > PhantomSocialModel.MAX_RELATIONSHIPS) || (memoryCount > PhantomSocialModel.MAX_MEMORIES))
			{
				throw new IllegalArgumentException("Social state collection count is outside bounds.");
			}

			final NavigableMap<Integer, Integer> traits = new TreeMap<>();
			int previousTrait = 0;
			for (int index = 0; index < traitCount; index++)
			{
				final int code = input.readUnsignedShort();
				final int value = input.readShort();
				if ((code <= previousTrait) || (traits.put(code, value) != null))
				{
					throw new IllegalArgumentException("Social trait ordering or uniqueness is invalid.");
				}
				previousTrait = code;
			}

			final List<RelationshipRecord> relationships = new ArrayList<>(relationshipCount);
			SubjectRef previousSubject = null;
			for (int index = 0; index < relationshipCount; index++)
			{
				final SubjectRef subject = readSubject(input);
				if ((previousSubject != null) && (previousSubject.compareTo(subject) >= 0))
				{
					throw new IllegalArgumentException("Social relationship ordering or uniqueness is invalid.");
				}
				final List<Integer> values = new ArrayList<>(PhantomSocialModel.DIMENSION_COUNT);
				for (int value = 0; value < PhantomSocialModel.DIMENSION_COUNT; value++)
				{
					values.add((int) input.readShort());
				}
				final List<Integer> agreements = new ArrayList<>(PhantomSocialModel.AGREEMENT_COUNT);
				for (int counter = 0; counter < PhantomSocialModel.AGREEMENT_COUNT; counter++)
				{
					agreements.add(input.readUnsignedShort());
				}
				relationships.add(new RelationshipRecord(subject, values, agreements, input.readLong(), input.readLong()));
				previousSubject = subject;
			}

			final List<MemoryRecord> memories = new ArrayList<>(memoryCount);
			String previousEventId = null;
			for (int index = 0; index < memoryCount; index++)
			{
				final String eventId = readHash(input);
				if ((previousEventId != null) && (previousEventId.compareTo(eventId) >= 0))
				{
					throw new IllegalArgumentException("Social memory ordering or uniqueness is invalid.");
				}
				memories.add(new MemoryRecord(eventId, input.readUnsignedShort(), readSubject(input), input.readLong(), input.readLong(), input.readUnsignedShort(), input.readUnsignedShort(), readHash(input)));
				previousEventId = eventId;
			}
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("Trailing bytes follow social.state.");
			}
			return new SocialState(authorityHash, seed, logicalMinute, traits, relationships, memories);
		}
		catch (EOFException e)
		{
			throw new IllegalArgumentException("Social state payload is truncated.", e);
		}
		catch (IllegalArgumentException e)
		{
			throw e;
		}
		catch (IOException e)
		{
			throw new IllegalArgumentException("Could not decode social.state.", e);
		}
	}

	private static void writeSubject(DataOutputStream output, SubjectRef subject) throws IOException
	{
		output.writeByte(subject.kind().code());
		output.writeLong(subject.id());
	}

	private static SubjectRef readSubject(DataInputStream input) throws IOException
	{
		return new SubjectRef(SubjectKind.fromCode(input.readUnsignedByte()), input.readLong());
	}

	private static void writeHash(DataOutputStream output, String hash) throws IOException
	{
		output.write(HexFormat.of().parseHex(PhantomSocialModel.requireHash(hash, "Encoded social hash")));
	}

	private static String readHash(DataInputStream input) throws IOException
	{
		final byte[] hash = input.readNBytes(HASH_BYTES);
		if (hash.length != HASH_BYTES)
		{
			throw new EOFException("Truncated SHA-256 value.");
		}
		return HexFormat.of().withUpperCase().formatHex(hash);
	}
}

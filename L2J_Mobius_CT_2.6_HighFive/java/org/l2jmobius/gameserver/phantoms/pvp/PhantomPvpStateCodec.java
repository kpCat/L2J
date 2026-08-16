/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Counterpart;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.CounterpartKind;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Encounter;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Stage;

public final class PhantomPvpStateCodec
{
	private static final int MAGIC = 0x50565031; // PVP1

	public byte[] encode(Encounter encounter)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(320);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeLong(encounter.profileId());
				value(output, encounter.counterpart().kind());
				output.writeLong(encounter.counterpart().identity());
				output.writeInt(encounter.counterpart().currentObjectId());
				value(output, encounter.source());
				string(output, encounter.authorityHash(), PhantomPvpModel.MAX_AUTHORITY_LENGTH);
				value(output, encounter.stage());
				string(output, encounter.warningReceiptId(), PhantomPvpModel.MAX_RECEIPT_LENGTH);
				string(output, encounter.helpReceiptId(), PhantomPvpModel.MAX_RECEIPT_LENGTH);
				output.writeByte(encounter.proactiveEngagements());
				output.writeLong(encounter.createdLogicalNanos());
				output.writeLong(encounter.expiresLogicalNanos());
				output.writeLong(encounter.warningLogicalNanos());
				output.writeLong(encounter.cooldownUntilLogicalNanos());
				string(output, encounter.terminalReason(), PhantomPvpModel.MAX_REASON_LENGTH);
			}
			final byte[] result = bytes.toByteArray();
			if (result.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("PvP state exceeds component payload bound.");
			}
			return result;
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not encode PvP state.", exception);
		}
	}

	public Encounter decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("PvP state payload is outside bounds.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (input.readInt() != MAGIC)
			{
				throw new IllegalArgumentException("Unknown PvP state payload.");
			}
			final long profileId = input.readLong();
			final Counterpart counterpart = new Counterpart(value(input, CounterpartKind.class), input.readLong(), input.readInt());
			final Encounter encounter = new Encounter(profileId, counterpart, value(input, Source.class), string(input, PhantomPvpModel.MAX_AUTHORITY_LENGTH), value(input, Stage.class), string(input, PhantomPvpModel.MAX_RECEIPT_LENGTH), string(input, PhantomPvpModel.MAX_RECEIPT_LENGTH), input.readUnsignedByte(), input.readLong(), input.readLong(), input.readLong(), input.readLong(), string(input, PhantomPvpModel.MAX_REASON_LENGTH));
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("PvP state payload has trailing data.");
			}
			return encounter;
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (EOFException exception)
		{
			throw new IllegalArgumentException("PvP state payload is truncated.", exception);
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not decode PvP state.", exception);
		}
	}

	private static void string(DataOutputStream output, String value, int maximum) throws Exception
	{
		if ((value == null) || (value.length() > maximum))
		{
			throw new IllegalArgumentException("PvP state string is outside bounds.");
		}
		output.writeUTF(value);
	}

	private static String string(DataInputStream input, int maximum) throws Exception
	{
		final String value = input.readUTF();
		if (value.length() > maximum)
		{
			throw new IllegalArgumentException("PvP state string is outside bounds.");
		}
		return value;
	}

	private static void value(DataOutputStream output, Enum<?> value) throws Exception
	{
		output.writeByte(value.ordinal());
	}

	private static <T extends Enum<T>> T value(DataInputStream input, Class<T> type) throws Exception
	{
		final int ordinal = input.readUnsignedByte();
		final T[] values = type.getEnumConstants();
		if (ordinal >= values.length)
		{
			throw new IllegalArgumentException("Unknown PvP state enum value.");
		}
		return values[ordinal];
	}
}

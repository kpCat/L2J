/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Compact durable idempotency authority for social events. The fixed-width
 * format deliberately fits all 96 receipts inside one profile component.
 */
public final class PhantomSocialReceiptLedger
{
	public static final String COMPONENT_TYPE = "social.receipts";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_RECEIPTS = 96;
	private static final int MAGIC = 0x53524350;
	private static final int FORMAT_VERSION = 1;
	private static final int RECEIPT_BYTES = 42;
	private static final long UNSIGNED_INT_MAX = 0xffffffffL;
	private static final int UNSIGNED_24_MAX = 0xffffff;

	public enum ReceiptStatus
	{
		APPLIED,
		STALE
	}

	public record Receipt(String eventId, int eventCode, long happenedMinute, long expiryMinute, ReceiptStatus status)
	{
		public Receipt
		{
			eventId = PhantomSocialModel.requireHash(eventId, "Social receipt event ID");
			if ((eventCode <= 0) || (eventCode > 0xffff) || (happenedMinute < 0) || (happenedMinute > UNSIGNED_INT_MAX) || (expiryMinute < happenedMinute) || ((expiryMinute - happenedMinute) > UNSIGNED_24_MAX) || (status == null))
			{
				throw new IllegalArgumentException("Social receipt metadata is outside bounds.");
			}
		}
	}

	private final List<Receipt> _receipts;

	public PhantomSocialReceiptLedger(List<Receipt> receipts)
	{
		if ((receipts == null) || (receipts.size() > MAX_RECEIPTS))
		{
			throw new IllegalArgumentException("Social receipt count exceeds the bounded ledger.");
		}
		final List<Receipt> ordered = receipts.stream().sorted(Comparator.comparing(Receipt::eventId)).toList();
		for (int index = 0; index < ordered.size(); index++)
		{
			if (!ordered.get(index).equals(receipts.get(index)) || ((index > 0) && ordered.get(index - 1).eventId().equals(ordered.get(index).eventId())))
			{
				throw new IllegalArgumentException("Social receipts must have unique, sorted event IDs.");
			}
		}
		_receipts = List.copyOf(receipts);
	}

	public static PhantomSocialReceiptLedger empty()
	{
		return new PhantomSocialReceiptLedger(List.of());
	}

	public List<Receipt> receipts()
	{
		return _receipts;
	}

	public Receipt find(String eventId)
	{
		for (Receipt receipt : _receipts)
		{
			if (receipt.eventId().equals(eventId))
			{
				return receipt;
			}
		}
		return null;
	}

	public PhantomSocialReceiptLedger prune(long nowMinute)
	{
		if (nowMinute < 0)
		{
			throw new IllegalArgumentException("Social receipt prune minute is invalid.");
		}
		return new PhantomSocialReceiptLedger(_receipts.stream().filter(receipt -> receipt.expiryMinute() > nowMinute).toList());
	}

	public PhantomSocialReceiptLedger add(Receipt receipt)
	{
		if ((receipt == null) || (find(receipt.eventId()) != null))
		{
			throw new IllegalArgumentException("Social receipt is null or already present.");
		}
		if (_receipts.size() >= MAX_RECEIPTS)
		{
			throw new IllegalStateException("Social receipt capacity reached.");
		}
		final List<Receipt> next = new ArrayList<>(_receipts);
		next.add(receipt);
		next.sort(Comparator.comparing(Receipt::eventId));
		return new PhantomSocialReceiptLedger(next);
	}

	public byte[] encode()
	{
		final ByteBuffer buffer = ByteBuffer.allocate(6 + (_receipts.size() * RECEIPT_BYTES)).order(ByteOrder.BIG_ENDIAN);
		buffer.putInt(MAGIC);
		buffer.put((byte) FORMAT_VERSION);
		buffer.put((byte) _receipts.size());
		for (Receipt receipt : _receipts)
		{
			buffer.put(HexFormat.of().parseHex(receipt.eventId()));
			buffer.putShort((short) receipt.eventCode());
			buffer.putInt((int) receipt.happenedMinute());
			putUnsigned24(buffer, (int) (receipt.expiryMinute() - receipt.happenedMinute()));
			buffer.put((byte) receipt.status().ordinal());
		}
		return buffer.array();
	}

	public static PhantomSocialReceiptLedger decode(byte[] payload)
	{
		if ((payload == null) || (payload.length < 6) || (payload.length > 4096))
		{
			throw new IllegalArgumentException("Social receipt payload length is invalid.");
		}
		final ByteBuffer buffer = ByteBuffer.wrap(payload.clone()).order(ByteOrder.BIG_ENDIAN);
		if ((buffer.getInt() != MAGIC) || (Byte.toUnsignedInt(buffer.get()) != FORMAT_VERSION))
		{
			throw new IllegalArgumentException("Social receipt payload header is invalid.");
		}
		final int count = Byte.toUnsignedInt(buffer.get());
		if ((count > MAX_RECEIPTS) || (buffer.remaining() != (count * RECEIPT_BYTES)))
		{
			throw new IllegalArgumentException("Social receipt payload count is invalid.");
		}
		final List<Receipt> receipts = new ArrayList<>(count);
		for (int index = 0; index < count; index++)
		{
			final byte[] hash = new byte[32];
			buffer.get(hash);
			final int code = Short.toUnsignedInt(buffer.getShort());
			final long happened = Integer.toUnsignedLong(buffer.getInt());
			final long expiry = happened + getUnsigned24(buffer);
			final int status = Byte.toUnsignedInt(buffer.get());
			if (status >= ReceiptStatus.values().length)
			{
				throw new IllegalArgumentException("Social receipt status is invalid.");
			}
			receipts.add(new Receipt(HexFormat.of().withUpperCase().formatHex(hash), code, happened, expiry, ReceiptStatus.values()[status]));
		}
		return new PhantomSocialReceiptLedger(receipts);
	}

	private static void putUnsigned24(ByteBuffer buffer, int value)
	{
		buffer.put((byte) (value >>> 16));
		buffer.put((byte) (value >>> 8));
		buffer.put((byte) value);
	}

	private static int getUnsigned24(ByteBuffer buffer)
	{
		return (Byte.toUnsignedInt(buffer.get()) << 16) | (Byte.toUnsignedInt(buffer.get()) << 8) | Byte.toUnsignedInt(buffer.get());
	}
}

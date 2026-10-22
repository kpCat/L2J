/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/** Narrow merchant birth receipts in existing profile components; item ownership stays native. */
public final class PhantomMerchantBootstrap
{
	public static final String COMPONENT = "merchant.server.v1";
	public static final String ROLE_PREFIX = "merchant.owner.";
	public enum Stage { RESERVED, CHARACTER, PENDING, READY, INCONSISTENT }
	private PhantomMerchantBootstrap() { }

	public record Ledger(int identityLimit, long capitalLimit, long stockValueLimit, long perItemLimit, int nextOrdinal, long issuedCapital, long issuedStockValue, Map<Integer, Long> issuedItems)
	{
		public Ledger
		{
			issuedItems = checkedItems(issuedItems);
			if (identityLimit < 1 || identityLimit > 512 || capitalLimit < 0 || stockValueLimit < 0 || perItemLimit < 1 || nextOrdinal < 0 || nextOrdinal > identityLimit || issuedCapital < 0 || issuedCapital > capitalLimit || issuedStockValue < 0 || issuedStockValue > stockValueLimit || issuedItems.values().stream().anyMatch(count -> count > perItemLimit)) { throw new IllegalArgumentException("Invalid merchant lifetime quota."); }
		}
		public static Ledger empty(int identities, long capital, long stock, long perItem) { return new Ledger(identities, capital, stock, perItem, 0, 0, 0, Map.of()); }
		public Ledger reserve(long capital, long stockValue, Map<Integer, Long> stock)
		{
			if (capital < 0 || stockValue < 0 || nextOrdinal == identityLimit || capital > capitalLimit - issuedCapital || stockValue > stockValueLimit - issuedStockValue) { throw new IllegalArgumentException("Merchant lifetime bootstrap exhausted."); }
			final var items = new TreeMap<>(issuedItems);
			checkedItems(stock).forEach((id, count) -> { final long before = items.getOrDefault(id, 0L); if (count > perItemLimit - before) { throw new IllegalArgumentException("Merchant per-item bootstrap exhausted."); } items.put(id, before + count); });
			return new Ledger(identityLimit, capitalLimit, stockValueLimit, perItemLimit, nextOrdinal + 1, issuedCapital + capital, issuedStockValue + stockValue, items);
		}
	}

	/** Slot and birth facts are immutable even if operator configuration changes later. */
	public record Role(int ordinal, int characterId, String account, String token, String name, int classId, int storeKind, int x, int y, int z, long capital, Map<Integer, Long> stock, Stage stage)
	{
		public Role
		{
			stock = checkedItems(stock);
			if (ordinal < 0 || ordinal >= 512 || characterId < 0 || account == null || !account.matches("[a-z0-9_]{1,45}") || token == null || !token.matches("[A-Za-z0-9_-]{43}") || name == null || !name.matches("[A-Za-z0-9]{1,16}") || classId < 0 || storeKind < 0 || storeKind > 2 || capital < 0 || stage == null || stock.size() > 8 || stage != Stage.RESERVED && characterId == 0) { throw new IllegalArgumentException("Invalid merchant birth receipt."); }
		}
		public String component() { return ROLE_PREFIX + String.format(java.util.Locale.ROOT, "%04d", ordinal); }
		public Role withCharacter(int id) { return new Role(ordinal, id, account, token, name, classId, storeKind, x, y, z, capital, stock, Stage.CHARACTER); }
		public Role pending() { return withStage(Stage.PENDING); }
		public Role withStage(Stage value) { return new Role(ordinal, characterId, account, token, name, classId, storeKind, x, y, z, capital, stock, value); }
		public Role afterUncertainRestart() { return stage == Stage.PENDING ? withStage(Stage.INCONSISTENT) : this; }
	}

	private static Map<Integer, Long> checkedItems(Map<Integer, Long> stock)
	{
		if (stock == null || stock.size() > 128 || stock.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey() <= 0 || entry.getValue() == null || entry.getValue() <= 0)) { throw new IllegalArgumentException("Invalid bounded merchant stock."); }
		return Map.copyOf(stock);
	}
	public static byte[] encode(Ledger value)
	{
		return write(out -> { out.writeByte(1); out.writeInt(value.identityLimit()); out.writeLong(value.capitalLimit()); out.writeLong(value.stockValueLimit()); out.writeLong(value.perItemLimit()); out.writeInt(value.nextOrdinal()); out.writeLong(value.issuedCapital()); out.writeLong(value.issuedStockValue()); items(out, value.issuedItems()); });
	}
	public static byte[] encode(Role value)
	{
		return write(out -> { out.writeByte(1); out.writeInt(value.ordinal()); out.writeInt(value.characterId()); out.writeUTF(value.account()); out.writeUTF(value.token()); out.writeUTF(value.name()); out.writeInt(value.classId()); out.writeByte(value.storeKind()); out.writeInt(value.x()); out.writeInt(value.y()); out.writeInt(value.z()); out.writeLong(value.capital()); out.writeByte(value.stage().ordinal()); items(out, value.stock()); });
	}
	public static Ledger decodeLedger(byte[] bytes)
	{
		return read(bytes, in -> new Ledger(in.readInt(), in.readLong(), in.readLong(), in.readLong(), in.readInt(), in.readLong(), in.readLong(), items(in)));
	}
	public static Role decodeRole(byte[] bytes)
	{
		return read(bytes, in -> { final int ordinal = in.readInt(), character = in.readInt(); final String account = in.readUTF(), token = in.readUTF(), name = in.readUTF(); final int classId = in.readInt(), kind = in.readUnsignedByte(), x = in.readInt(), y = in.readInt(), z = in.readInt(); final long capital = in.readLong(); final int stage = in.readUnsignedByte(); if (stage >= Stage.values().length) { throw new IllegalArgumentException("Unknown merchant stage."); } return new Role(ordinal, character, account, token, name, classId, kind, x, y, z, capital, items(in), Stage.values()[stage]); });
	}
	private static void items(DataOutputStream out, Map<Integer, Long> values) throws IOException { out.writeInt(values.size()); for (var entry : new TreeMap<>(values).entrySet()) { out.writeInt(entry.getKey()); out.writeLong(entry.getValue()); } }
	private static Map<Integer, Long> items(DataInputStream in) throws IOException
	{
		final int count = in.readInt(); if (count < 0 || count > 128) { throw new IllegalArgumentException("Merchant stock count outside bound."); }
		final var values = new TreeMap<Integer, Long>(); for (int i = 0; i < count; i++) { if (values.put(in.readInt(), in.readLong()) != null) { throw new IllegalArgumentException("Duplicate merchant item."); } } return values;
	}
	private interface Writer { void write(DataOutputStream out) throws IOException; }
	private interface Reader<T> { T read(DataInputStream in) throws IOException; }
	private static byte[] write(Writer writer)
	{
		try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes)) { writer.write(out); out.flush(); if (bytes.size() > 4096) { throw new IllegalArgumentException("Merchant component exceeds existing bound."); } return bytes.toByteArray(); }
		catch (IOException impossible) { throw new IllegalArgumentException("Merchant component encode failed.", impossible); }
	}
	private static <T> T read(byte[] bytes, Reader<T> reader)
	{
		if (bytes == null || bytes.length > 4096) { throw new IllegalArgumentException("Invalid merchant component size."); }
		try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) { if (in.readUnsignedByte() != 1) { throw new IllegalArgumentException("Unknown merchant component version."); } final T value = reader.read(in); if (in.available() != 0) { throw new IllegalArgumentException("Trailing merchant component bytes."); } return value; }
		catch (IOException invalid) { throw new IllegalArgumentException("Invalid merchant component.", invalid); }
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import org.l2jmobius.gameserver.model.item.type.CrystalType;

/** Separate market quota/intent codec. Reserved uncertain effects remain spent and quarantined. */
public final class PhantomMerchantMarketLedger
{
	public static final String COMPONENT = "merchant.market.v1";
	public static final String RECEIPT_PREFIX = "merchant.market.grant.";
	public enum State { PREPARED, COMMITTED, INCONSISTENT }
	private PhantomMerchantMarketLedger() { }
	public record Receipt(long sequence, int ordinal, int characterId, State state, int itemId, long quantity, int enchant,
		long value, String category, CrystalType grade, long createdAt, long beforeAdena, long beforeCount, int beforeObjectId, int deliveredObjectId, String source)
	{
		public Receipt
		{
			if (sequence < 1 || ordinal < 0 || ordinal >= 512 || characterId <= 0 || state == null || itemId <= 0 || quantity <= 0 || enchant < 0 || enchant > 25 || value <= 0
				|| !Set.of("FUNDING", "MATERIAL_RESOURCE", "EQUIPMENT_PIECE", "RECIPE_SCROLL60", "OTHER_USEFUL", "FINISHED_GEAR").contains(category) || grade == null || createdAt <= 0 || beforeAdena < 0 || beforeCount < 0 || beforeObjectId < 0 || deliveredObjectId < 0 || source == null || source.isBlank() || source.length() > 512
				|| (itemId == 57) != category.equals("FUNDING") || itemId == 57 && (quantity != value || enchant != 0) || enchant > 0 && !category.equals("FINISHED_GEAR")) { throw new IllegalArgumentException("Invalid market grant intent."); }
		}
		public String component() { return RECEIPT_PREFIX + String.format(java.util.Locale.ROOT, "%08d", sequence); }
		public boolean quarantined() { return state != State.COMMITTED; }
		public Receipt withState(State next, int objectId) { return new Receipt(sequence, ordinal, characterId, next, itemId, quantity, enchant, value, category, grade, createdAt, beforeAdena, beforeCount, beforeObjectId, objectId, source); }
	}
	public record Usage(long funding, long stockValue, long itemCount, long gradeCount, long gearGradeCount, int grants, long lastFundingAt)
	{
		public Usage { if (funding < 0 || stockValue < 0 || itemCount < 0 || gradeCount < 0 || gearGradeCount < 0 || grants < 0 || lastFundingAt < 0) { throw new IllegalArgumentException("Invalid market usage."); } }
	}
	public record Ledger(int policyVersion, PhantomMerchantMarketPolicy policy, long sequence, long fundingIssued, long stockIssued, long enchantedEpochStart, int epochEnchanted)
	{
		public Ledger
		{
			if (policyVersion < 1 || policy == null || sequence < 0 || sequence > 32768 || fundingIssued < 0 || stockIssued < 0 || enchantedEpochStart < 0 || epochEnchanted < 0 || epochEnchanted > 32768) { throw new IllegalArgumentException("Invalid market ledger."); }
		}
		public static Ledger empty(PhantomMerchantMarketPolicy policy) { return new Ledger(1, policy, 0, 0, 0, 0, 0); }
		public Ledger withPolicy(PhantomMerchantMarketPolicy next, java.util.List<Receipt> receipts, long now)
		{
			if (policy.equals(next)) { return this; }
			if (now <= 0 || receipts.size() != sequence) { throw new IllegalArgumentException("Exact reserved receipts required for policy transition."); }
			final long period = next.assortmentSeconds() * 1000L, start = now / period * period;
			// Count PREPARED/INCONSISTENT too. Changing the interval never replenishes the current bucket.
			final int count = Math.toIntExact(receipts.stream().filter(receipt -> receipt.enchant() > 0 && receipt.createdAt() >= start).count());
			return new Ledger(Math.addExact(policyVersion, 1), next, sequence, fundingIssued, stockIssued, start, count);
		}
		public Ledger reserve(Receipt intent, Usage usage)
		{
			if (intent.state() != State.PREPARED || intent.sequence() != sequence + 1 || sequence >= policy.grantsGlobal() || usage.grants() >= policy.grantsPerOwner()) { throw new IllegalArgumentException("Market intent replay or lifetime grant quota exhausted."); }
			if (intent.itemId() == 57)
			{
				if (!policy.fundingEnabled() || intent.quantity() > policy.fundingGrant() || fundingIssued >= policy.fundingGlobal() || intent.quantity() > policy.fundingGlobal() - fundingIssued
					|| usage.funding() >= policy.fundingPerOwner() || intent.quantity() > policy.fundingPerOwner() - usage.funding()
					|| usage.lastFundingAt() > 0 && (intent.createdAt() < usage.lastFundingAt() || intent.createdAt() - usage.lastFundingAt() < policy.fundingCooldown() * 1000L)) { throw new IllegalArgumentException("Finite operating funding unavailable or not due."); }
				return new Ledger(policyVersion, policy, intent.sequence(), Math.addExact(fundingIssued, intent.value()), stockIssued, enchantedEpochStart, epochEnchanted);
			}
			if (!policy.stockEnabled() || stockIssued >= policy.stockGlobal() || intent.value() > policy.stockGlobal() - stockIssued
				|| usage.stockValue() >= policy.stockPerOwner() || intent.value() > policy.stockPerOwner() - usage.stockValue()
				|| usage.itemCount() >= policy.stockPerItem() || intent.quantity() > policy.stockPerItem() - usage.itemCount()
				|| usage.gradeCount() >= policy.stockPerGrade() || intent.quantity() > policy.stockPerGrade() - usage.gradeCount()
				|| intent.quantity() > policy.stockStack()) { throw new IllegalArgumentException("Finite market stock quota exhausted."); }
			if (intent.category().equals("FINISHED_GEAR") && (intent.quantity() != 1 || !policy.gearGrades().contains(intent.grade()) || usage.gearGradeCount() >= policy.gearPerGrade() || intent.enchant() > policy.enchantMax())) { throw new IllegalArgumentException("Finite gear grade/enchant quota exhausted."); }
			long epoch = enchantedEpochStart; int count = epochEnchanted;
			if (intent.enchant() > 0)
			{
				final long period = policy.assortmentSeconds() * 1000L, current = intent.createdAt() / period * period;
				if (current < enchantedEpochStart) { throw new IllegalArgumentException("Market enchant epoch moved backwards."); }
				count = current == epoch ? count : 0; epoch = current;
				if (count >= policy.enchantedPerEpoch()) { throw new IllegalArgumentException("Enchanted stock epoch quota exhausted."); } count++;
			}
			return new Ledger(policyVersion, policy, intent.sequence(), fundingIssued, Math.addExact(stockIssued, intent.value()), epoch, count);
		}
	}
	public static byte[] encode(Ledger ledger)
	{
		return write(out -> { out.writeInt(ledger.policyVersion()); policy(out, ledger.policy()); out.writeLong(ledger.sequence()); out.writeLong(ledger.fundingIssued()); out.writeLong(ledger.stockIssued()); out.writeLong(ledger.enchantedEpochStart()); out.writeInt(ledger.epochEnchanted()); });
	}
	public static byte[] encode(Receipt receipt)
	{
		return write(out -> { out.writeLong(receipt.sequence()); out.writeInt(receipt.ordinal()); out.writeInt(receipt.characterId()); out.writeUTF(receipt.state().name()); out.writeInt(receipt.itemId()); out.writeLong(receipt.quantity()); out.writeInt(receipt.enchant()); out.writeLong(receipt.value()); out.writeUTF(receipt.category()); out.writeUTF(receipt.grade().name()); out.writeLong(receipt.createdAt()); out.writeLong(receipt.beforeAdena()); out.writeLong(receipt.beforeCount()); out.writeInt(receipt.beforeObjectId()); out.writeInt(receipt.deliveredObjectId()); out.writeUTF(receipt.source()); });
	}
	public static Ledger decodeLedger(byte[] bytes) { return read(bytes, in -> new Ledger(in.readInt(), policy(in), in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readInt())); }
	public static Receipt decodeReceipt(byte[] bytes) { return read(bytes, in -> new Receipt(in.readLong(), in.readInt(), in.readInt(), State.valueOf(in.readUTF()), in.readInt(), in.readLong(), in.readInt(), in.readLong(), in.readUTF(), CrystalType.valueOf(in.readUTF()), in.readLong(), in.readLong(), in.readLong(), in.readInt(), in.readInt(), in.readUTF())); }
	private static void policy(DataOutputStream out, PhantomMerchantMarketPolicy p) throws IOException
	{
		out.writeInt(p.assortmentSeconds()); out.writeInt(p.gearShare()); int mask = 0; for (var grade : p.gearGrades()) { mask |= 1 << grade.ordinal(); } out.writeInt(mask);
		out.writeBoolean(p.fundingEnabled()); out.writeLong(p.fundingGrant()); out.writeInt(p.fundingCooldown()); out.writeLong(p.fundingPerOwner()); out.writeLong(p.fundingGlobal());
		out.writeBoolean(p.stockEnabled()); out.writeLong(p.stockGlobal()); out.writeLong(p.stockPerOwner()); out.writeLong(p.stockPerItem()); out.writeLong(p.stockPerGrade()); out.writeInt(p.gearPerGrade()); out.writeInt(p.stockStack()); out.writeInt(p.enchantMax()); out.writeInt(p.enchantedPerEpoch()); out.writeInt(p.grantsPerOwner()); out.writeInt(p.grantsGlobal());
	}
	private static PhantomMerchantMarketPolicy policy(DataInputStream in) throws IOException
	{
		final int seconds = in.readInt(), share = in.readInt(), mask = in.readInt();
		if (mask <= 0 || (mask & ~255) != 0) { throw new IllegalArgumentException("Invalid market grade mask."); }
		final var grades = java.util.EnumSet.noneOf(CrystalType.class); for (var grade : CrystalType.values()) { if ((mask & (1 << grade.ordinal())) != 0) { grades.add(grade); } }
		return new PhantomMerchantMarketPolicy(seconds, share, grades, in.readBoolean(), in.readLong(), in.readInt(), in.readLong(), in.readLong(), in.readBoolean(), in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
	}
	private interface Writer { void write(DataOutputStream out) throws IOException; }
	private interface Reader<T> { T read(DataInputStream in) throws IOException; }
	private static byte[] write(Writer writer)
	{
		try (var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes))
		{
			out.writeByte(1); writer.write(out); out.flush(); if (bytes.size() > 4096) { throw new IllegalArgumentException("Market component exceeds4096 bytes."); } return bytes.toByteArray();
		}
		catch (IOException invalid) { throw new IllegalArgumentException("Market component encode failed.", invalid); }
	}
	private static <T> T read(byte[] bytes, Reader<T> reader)
	{
		if (bytes == null || bytes.length > 4096) { throw new IllegalArgumentException("Invalid market component size."); }
		try (var in = new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			if (in.readUnsignedByte() != 1) { throw new IllegalArgumentException("Unknown market component version."); }
			final T value = reader.read(in); if (in.available() != 0) { throw new IllegalArgumentException("Trailing market component data."); } return value;
		}
		catch (IOException invalid) { throw new IllegalArgumentException("Invalid market component.", invalid); }
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Non-trainable native failure/UNKNOWN observation. It has no reward or conversion to an Example. */
public record TacticalFailureV3(String eventKey, TacticalReceiptV3.Attempt choice, String reason, boolean nativeCompleted,
	Double observedHp, Double observedMp, long elapsedNanos, HealEvidenceV3.SourceOwner owner)
{
	public static final int MAX_BYTES = 600000;
	private static final int MAGIC = 0x54463446;
	public TacticalFailureV3
	{
		if (eventKey == null || !eventKey.matches("[a-f0-9]{64}") || choice == null || reason == null || !reason.matches("[A-Z0-9_]{1,80}")
			|| elapsedNanos <= 0 || observedHp != null && (!Double.isFinite(observedHp) || observedHp < 0)
			|| observedMp != null && (!Double.isFinite(observedMp) || observedMp < 0))
		{ throw new IllegalArgumentException("Invalid non-trainable V3 observation"); }
	}
	public String outcome() { return nativeCompleted ? "COMPLETED_NO_TRAINABLE_EFFECT" : "UNKNOWN_OR_CANCELLED"; }
	public byte[] bytes()
	{
		try
		{
			final var buffer = new ByteArrayOutputStream(); final var out = new DataOutputStream(buffer);
			out.writeInt(MAGIC); out.writeInt(1); out.writeUTF(TacticalObservationV3.SCHEMA); out.writeUTF(LegalActionV3.FINGERPRINT);
			out.writeUTF(eventKey); out.writeUTF(reason); out.writeBoolean(nativeCompleted); out.writeLong(elapsedNanos);
			out.writeBoolean(observedHp != null); if (observedHp != null) { out.writeDouble(observedHp); }
			out.writeBoolean(observedMp != null); if (observedMp != null) { out.writeDouble(observedMp); }
			out.writeBoolean(owner != null); if (owner != null) { out.writeLong(owner.generation()); out.writeUTF(owner.operationKey()); }
			final byte[] observation = choice.observation().bytes(); out.writeInt(observation.length); out.write(observation);
			out.writeInt(choice.offered().size()); for (var action : choice.offered()) { action.write(out); }
			out.writeInt(choice.offered().indexOf(choice.selected())); out.writeInt(choice.offered().indexOf(choice.baseline())); out.writeLong(choice.modelVersion());
			out.flush(); if (buffer.size() > MAX_BYTES) { throw new IllegalArgumentException("V3 failure observation byte bound"); }
			return buffer.toByteArray();
		}
		catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}
	public String sha256() { return TacticalObservationV3.hash(bytes()); }
	public static TacticalFailureV3 restore(byte[] bytes, String expectedSha)
	{
		if (bytes == null || bytes.length > MAX_BYTES || expectedSha == null || !expectedSha.matches("[a-f0-9]{64}"))
		{ throw new IllegalArgumentException("V3 failure size/checksum required"); }
		final byte[] owned = bytes.clone();
		if (!expectedSha.equals(TacticalObservationV3.hash(owned))) { throw new IllegalArgumentException("V3 failure checksum mismatch"); }
		try
		{
			final var in = new DataInputStream(new ByteArrayInputStream(owned));
			if (in.readInt() != MAGIC || in.readInt() != 1 || !TacticalObservationV3.SCHEMA.equals(in.readUTF()) || !LegalActionV3.FINGERPRINT.equals(in.readUTF()))
			{ throw new IllegalArgumentException("V3 failure schema mismatch"); }
			final String key = in.readUTF(), reason = in.readUTF(); final boolean completed = in.readBoolean(); final long elapsed = in.readLong();
			final Double hp = in.readBoolean() ? in.readDouble() : null, mp = in.readBoolean() ? in.readDouble() : null;
			final var owner = in.readBoolean() ? new HealEvidenceV3.SourceOwner(in.readLong(), in.readUTF()) : null;
			final int size = in.readInt(); if (size <= 0 || size > 524288 || size > in.available()) { throw new IllegalArgumentException("V3 failure observation bound"); }
			final var observation = TacticalObservationV3.restore(in.readNBytes(size));
			final int count = in.readInt(); if (count < 2 || count > 16) { throw new IllegalArgumentException("V3 failure action bound"); }
			final var actions = new ArrayList<LegalActionV3>(); for (int i = 0; i < count; i++) { actions.add(LegalActionV3.read(in)); }
			final int selected = in.readInt(), baseline = in.readInt();
			if (selected < 0 || selected >= count || baseline < 0 || baseline >= count) { throw new IllegalArgumentException("V3 failure action index"); }
			final var choice = new TacticalReceiptV3.Attempt(observation, actions, actions.get(selected), actions.get(baseline), in.readLong());
			if (in.available() != 0) { throw new IllegalArgumentException("Trailing V3 failure bytes"); }
			return new TacticalFailureV3(key, choice, reason, completed, hp, mp, elapsed, owner);
		}
		catch (IOException failure) { throw new IllegalArgumentException("Corrupt V3 failure envelope", failure); }
	}
	public String json()
	{
		return "{\"schemaVersion\":1,\"eventKey\":" + TrainingStore.quote(eventKey) + ",\"family\":" + TrainingStore.quote(choice.selected().family().name())
			+ ",\"actorProfile\":" + choice.observation().profileId() + ",\"reason\":" + TrainingStore.quote(reason) + ",\"outcome\":" + TrainingStore.quote(outcome())
			+ ",\"nativeCompleted\":" + nativeCompleted + ",\"observedHp\":" + observedHp + ",\"observedMp\":" + observedMp
			+ ",\"elapsedNanos\":" + elapsedNanos + ",\"reward\":null,\"trainable\":false,\"sha256\":" + TrainingStore.quote(sha256()) + "}";
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;

/** A proposal from an existing owner, never a permission to issue a new command. */
public record LegalActionV3(Family family, String owner, long profileId, int actorObjectId, long actorEpoch,
	long generation, int targetObjectId, long targetEpoch, String partyIdentity, int skillId, int skillLevel,
	double mpCost, int castMillis, int range, boolean criticalSafety, boolean lawful)
{
	public static final int VERSION = 3;
	public static final String FINGERPRINT = TacticalObservationV3.hash(("LegalActionV3/f32be/paired-mask/clip[-1,1]/64/family,skill/20000,level/100,mp/500,cast/10000,range/4000,knownClass/200-or-hashedNpc,role/3;"
		+ "level,hp,maxHp,mp,maxMp,mDef,pDef,distance,height,facing,los,gearSignature,effectSignature,effectCount,debuffCount,casting,stunned,muted,elementDefense,back,flank,geo,weaponGrade,jewelGrade;stockOwner;receipt=NATIVE_HEAL").getBytes(java.nio.charset.StandardCharsets.UTF_8));
	public enum Family { DAMAGE, HEAL, RECHARGE, RESURRECT_SKILL, CONTROL, DISPEL, MOVE, RETREAT, SUMMON, SPOIL, SWEEP }
	public enum ReceiptKind { NATIVE_DAMAGE, NATIVE_HEAL, NATIVE_STATUS, NATIVE_REVIVE, NATIVE_TRAVEL, NATIVE_SUMMON, NATIVE_ACQUISITION, UNKNOWN }
	public LegalActionV3
	{
		if (family == null || owner == null || owner.isBlank() || owner.length() > 64 || profileId <= 0 || actorObjectId <= 0 || actorEpoch <= 0
			|| generation <= 0 || targetObjectId <= 0 || targetEpoch < 0 || partyIdentity == null || partyIdentity.length() > 256 || skillId < 0 || skillLevel < 0
			|| !Double.isFinite(mpCost) || mpCost < 0 || castMillis < 0 || range < 0) { throw new IllegalArgumentException("Invalid legal action identity/cost"); }
	}
	public boolean trainable() { return lawful && (family == Family.HEAL || family == Family.DAMAGE); }
	/** Rich native time/cost contract; frozen float features retain their original base-cast semantics. */
	public TacticalTimingV3.Timing timing(TacticalObservationV3 observation)
	{
		if (observation.profileId() != profileId || observation.actorObjectId() != actorObjectId || observation.actorEpoch() != actorEpoch || !observation.partyIdentity().equals(partyIdentity))
		{ throw new IllegalArgumentException("Action timing does not belong to observation"); }
		return observation.self().abilities().stream().filter(v -> v.skillId() == skillId && v.level() == skillLevel && v.timing() != null)
			.map(TacticalObservationV3.Ability::timing).findFirst().orElseGet(TacticalTimingV3::unknown);
	}
	public ReceiptKind receiptKind() { return family == Family.HEAL ? ReceiptKind.NATIVE_HEAL : family == Family.DAMAGE ? ReceiptKind.NATIVE_DAMAGE : ReceiptKind.UNKNOWN; }
	public boolean current(int actor, long epoch, int target, long targetGeneration, String party)
	{ return lawful && actorObjectId == actor && actorEpoch == epoch && targetObjectId == target && targetEpoch == targetGeneration && partyIdentity.equals(party); }
	public LegalActionV3 withFamily(Family value)
	{ return new LegalActionV3(value, owner, profileId, actorObjectId, actorEpoch, generation, targetObjectId, targetEpoch, partyIdentity, skillId, skillLevel, mpCost, castMillis, range, criticalSafety, lawful); }
	public float[] features(TacticalObservationV3 observation)
	{
		if (observation.profileId() != profileId || observation.actorObjectId() != actorObjectId || observation.actorEpoch() != actorEpoch || !observation.partyIdentity().equals(partyIdentity))
		{ throw new IllegalArgumentException("Action does not belong to observation"); }
		final Entity target = observation.entity(targetObjectId);
		if (target == null || target.epoch() != targetEpoch) { throw new IllegalArgumentException("Action target absent or stale"); }
		final float[] result = new float[64];
		final double[] common = { family.ordinal(), skillId, skillLevel, mpCost, castMillis, range, target.classId(), target.role().ordinal() };
		final double[] scales = { 12, 20000, 100, 500, 10000, 4000, 200, 3 };
		for (int i = 0; i < common.length; i++) { TacticalObservationV3.put(result, i, i == 6 ? target.fact(Field.CLASS_ID) : Fact.known(common[i]), scales[i]); }
		if (!target.fact(Field.CLASS_ID).known() && target.fact(Field.NPC_ID).known())
		{
			int hash = (int) target.fact(Field.NPC_ID).value(); hash = (hash ^ hash >>> 16) * 0x7feb352d; hash ^= hash >>> 15;
			TacticalObservationV3.put(result, 6, Fact.known(hash / (double) Integer.MAX_VALUE), 1);
		}
		final Field[] fields = { Field.LEVEL, Field.HP, Field.MAX_HP, Field.MP, Field.MAX_MP, Field.M_DEF, Field.P_DEF, Field.DISTANCE, Field.HEIGHT, Field.FACING, Field.LOS,
			Field.GEAR_SIGNATURE, Field.EFFECT_SIGNATURE, Field.EFFECT_COUNT, Field.DEBUFF_COUNT, Field.CASTING, Field.STUNNED, Field.MUTED, Field.ELEMENT_DEFENSE, Field.BACK, Field.FLANK, Field.GEO, Field.WEAPON_GRADE, Field.JEWEL_GRADE };
		for (int i = 0; i < fields.length; i++) { final Field field = fields[i]; TacticalObservationV3.put(result, i + 8, target.fact(field), field.scale); }
		return result;
	}
	void write(DataOutput out) throws IOException
	{
		out.writeInt(VERSION); out.writeInt(family.ordinal()); out.writeUTF(owner); out.writeLong(profileId); out.writeInt(actorObjectId); out.writeLong(actorEpoch); out.writeLong(generation);
		out.writeInt(targetObjectId); out.writeLong(targetEpoch); out.writeUTF(partyIdentity); out.writeInt(skillId); out.writeInt(skillLevel); out.writeDouble(mpCost); out.writeInt(castMillis); out.writeInt(range); out.writeBoolean(criticalSafety); out.writeBoolean(lawful);
	}
	static LegalActionV3 read(DataInput in) throws IOException
	{
		if (in.readInt() != VERSION) { throw new IllegalArgumentException("Action version"); }
		final int family = in.readInt(); if (family < 0 || family >= Family.values().length) { throw new IllegalArgumentException("Action family"); }
		return new LegalActionV3(Family.values()[family], in.readUTF(), in.readLong(), in.readInt(), in.readLong(), in.readLong(), in.readInt(), in.readLong(), in.readUTF(), in.readInt(), in.readInt(), in.readDouble(), in.readInt(), in.readInt(), in.readBoolean(), in.readBoolean());
	}
}

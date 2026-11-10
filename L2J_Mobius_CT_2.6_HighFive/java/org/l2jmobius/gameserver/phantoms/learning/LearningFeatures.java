/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** V1 has 64 slots, each containing a value followed by a presence mask. */
public final class LearningFeatures
{
	public static final int SIZE = 128;
	public static final String SCHEMA = NeuralPolicy.sha256(("v1/f32be/paired-mask/clip[-1,1]/hash-mix7feb352d:"
		+ "level/100,hp,mp,class[6],cp,pAtk/5000,mAtk/5000,weaponKind,armorKind,grade,enchant/20,shots,"
		+ "pDef/3000,mDef/3000,buffs/40,debuffs/16,cc,moving,casting,attacking,magicCapable,range/2000,height/512,"
		+ "party,partySize/9,partyTank/9,partyHeal/9,partyDamage/9,partyMinLevel/100,partyMaxLevel/100,partyMeanLevel/100,"
		+ "partyHp,partyMp,partyLowHp/9,partyFocus,npcHp,npcLevelDelta/40,npcDefenseBias,npcId[4],npcAggro,"
		+ "npcPDef/3000,npcMDef/3000,los,walkable,inRange,skillId,skillLevel/100,skillMagicLevel/100,magic,skillMp/500,cast/10000,"
		+ "reuse/60000,skillRange/2000,skillTarget,skillPower/5000,skillElement").getBytes(StandardCharsets.UTF_8));
	private final float[] _values;

	public LearningFeatures()
	{
		_values = new float[SIZE];
	}

	private LearningFeatures(float[] values)
	{
		_values = values.clone();
	}

	public LearningFeatures set(int slot, double normalized)
	{
		if (Double.isFinite(normalized))
		{
			_values[slot * 2] = (float) Math.clamp(normalized, -1, 1);
			_values[slot * 2 + 1] = 1;
		}
		return this;
	}

	public LearningFeatures category(int slot, int buckets, int id)
	{
		int hash = (id ^ (id >>> 16)) * 0x7feb352d;
		hash ^= hash >>> 15;
		if (buckets == 1)
		{
			return set(slot, (hash >> 8) / 8388608.0);
		}
		for (int i = 0; i < buckets; i++)
		{
			set(slot + i, 0);
		}
		return set(slot + Math.floorMod(hash, buckets), hash < 0 ? -1 : 1);
	}

	public LearningFeatures fork()
	{
		return new LearningFeatures(_values);
	}

	public float[] copy()
	{
		return _values.clone();
	}

	public static byte[] encode(float[] values)
	{
		validate(values);
		final ByteBuffer bytes = ByteBuffer.allocate(SIZE * Float.BYTES);
		bytes.asFloatBuffer().put(values);
		return bytes.array();
	}

	public static float[] decode(byte[] bytes)
	{
		if (bytes == null || bytes.length != SIZE * Float.BYTES)
		{
			throw new IllegalArgumentException("Invalid feature length");
		}
		final float[] values = new float[SIZE];
		ByteBuffer.wrap(bytes).asFloatBuffer().get(values);
		validate(values);
		return values;
	}

	static void validate(float[] values)
	{
		if (values.length != SIZE)
		{
			throw new IllegalArgumentException("Invalid feature length");
		}
		for (int i = 0; i < SIZE; i += 2)
		{
			final float value = values[i];
			final float present = values[i + 1];
			if (!Float.isFinite(value) || Math.abs(value) > 1 || (present != 1 && (present != 0 || value != 0)))
			{
				throw new IllegalArgumentException("Invalid feature value or mask");
			}
		}
	}
}

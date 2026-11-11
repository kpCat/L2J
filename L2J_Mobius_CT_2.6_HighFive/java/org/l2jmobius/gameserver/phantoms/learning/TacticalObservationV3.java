/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Immutable rich observations; a missing fact is explicitly different from a measured zero. */
public record TacticalObservationV3(long decisionId, long capturedNanos, long profileId, int actorObjectId,
	long actorEpoch, String partyIdentity, List<TacticalObservationV3.Entity> entities)
{
	public static final int VERSION = 3, MAX_ENTITIES = 20;
	public enum Role { SELF, ALLY, SUMMON, ENEMY }
	public enum Field
	{
		LEVEL(100), HP(1), MP(1), CP(1), MAX_HP(20000), MAX_MP(20000), MAX_CP(20000),
		P_ATK(20000), M_ATK(20000), P_DEF(10000), M_DEF(10000), CAST_SPEED(3000), ATTACK_SPEED(3000), MOVE_SPEED(500),
		CRITICAL(1000), EVASION(300), WEAPON_GRADE(8), WEAPON_ENCHANT(30), ARMOR_GRADE(8), JEWEL_GRADE(8),
		GEAR_SIGNATURE(1), EFFECT_SIGNATURE(1), EFFECT_COUNT(64), DEBUFF_COUNT(64), CASTING(1), MOVING(1), STUNNED(1), MUTED(1),
		DISTANCE(4000), HEIGHT(2000), FACING(Math.PI), LOS(1), ELEMENT_ATTACK(500), ELEMENT_DEFENSE(500), CLASS_ID(200),
		SKILL_COUNT(128), RECENT_SKILL(10000), BACK(1), FLANK(1), GEO(1), NPC_ID(40000);
		final double scale;
		Field(double scale) { this.scale = scale; }
	}
	public record Fact(double value, boolean known)
	{
		public Fact { if (!Double.isFinite(value) || !known && value != 0) { throw new IllegalArgumentException("Invalid observation fact"); } }
		public static Fact known(double value) { return new Fact(value, true); }
		public static Fact unknown() { return new Fact(0, false); }
	}
	public record Gear(int slot, int itemId, int grade, int enchant, String family, int augmentationId, boolean augmentationKnown)
	{
		public Gear(int slot, int itemId, int grade, int enchant, String family) { this(slot, itemId, grade, enchant, family, 0, false); }
		public Gear { if (slot < 0 || slot >= 32 || itemId <= 0 || grade < 0 || grade > 8 || enchant < 0 || family == null || family.length() > 64 || !augmentationKnown && augmentationId != 0) { throw new IllegalArgumentException("Invalid gear fact"); } }
	}
	public record Effect(int skillId, int level, int remainingSeconds, boolean durationKnown, boolean debuff)
	{ public Effect { if (skillId <= 0 || level <= 0 || remainingSeconds < 0 || !durationKnown && remainingSeconds != 0) { throw new IllegalArgumentException("Invalid effect fact"); } } }
	public record Ability(int skillId, int level, int magicLevel, double mp, int hitMillis, long reuseMillis, int range, boolean ready, TacticalTimingV3.Timing timing)
	{
		public Ability(int skillId, int level, int magicLevel, double mp, int hitMillis, long reuseMillis, int range, boolean ready)
		{ this(skillId, level, magicLevel, mp, hitMillis, reuseMillis, range, ready, null); }
		public Ability { if (skillId <= 0 || level <= 0 || magicLevel < 0 || !Double.isFinite(mp) || mp < 0 || hitMillis < 0 || reuseMillis < 0 || range < 0) { throw new IllegalArgumentException("Invalid ability fact"); } }
	}
	public record PastAction(int skillId, int targetObjectId, long observedNanos) { }
	public record Entity(Role role, int objectId, long epoch, int ownerObjectId, int classId, Map<Field, Fact> facts,
		List<Gear> gear, List<Effect> effects, List<Ability> abilities, List<PastAction> pastActions)
	{
		public Entity
		{
			Objects.requireNonNull(role);
			if (objectId <= 0 || epoch < 0 || classId < 0 || ownerObjectId < 0 || facts.size() > Field.values().length
				|| gear.size() > 32 || effects.size() > 64 || abilities.size() > 128 || pastActions.size() > 8)
			{ throw new IllegalArgumentException("Entity outside observation bounds"); }
			facts = Map.copyOf(facts); gear = List.copyOf(gear); effects = List.copyOf(effects);
			abilities = List.copyOf(abilities); pastActions = List.copyOf(pastActions);
		}
		public Fact fact(Field field) { return facts.getOrDefault(field, Fact.unknown()); }
		public float[] vector()
		{
			final float[] values = new float[Field.values().length * 2];
			for (Field field : Field.values()) { put(values, field.ordinal(), fact(field), field.scale); }
			return values;
		}
	}
	public static final String SCHEMA = hash(("TacticalObservationV3/f32be/paired-mask/clip[-1,1]/state128/self80/meanRole16x3/"
		+ Arrays.toString(Field.values()) + "/scales=" + Arrays.toString(Arrays.stream(Field.values()).mapToDouble(v -> v.scale).toArray())
		+ "/gear32/item-grade-enchant-family-augmentation-known/effects64/skills128/history8/entity20/role-sort-objectid/no-hidden-hostile-facts").getBytes(StandardCharsets.UTF_8));
	public TacticalObservationV3
	{
		if (decisionId <= 0 || profileId <= 0 || actorObjectId <= 0 || actorEpoch <= 0 || partyIdentity == null || partyIdentity.length() > 256
			|| entities.isEmpty() || entities.size() > MAX_ENTITIES) { throw new IllegalArgumentException("Observation identity or bounds"); }
		entities = entities.stream().sorted(Comparator.comparing(Entity::role).thenComparingInt(Entity::objectId)).toList();
		if (entities.stream().map(Entity::objectId).distinct().count() != entities.size() || entities.stream().filter(v -> v.role() == Role.SELF).count() != 1
			|| entities.getFirst().objectId() != actorObjectId || entities.getFirst().epoch() != actorEpoch
			|| entities.stream().filter(v -> v.role() == Role.ALLY).count() > 9 || entities.stream().filter(v -> v.role() == Role.SUMMON).count() > 2
			|| entities.stream().filter(v -> v.role() == Role.ENEMY).count() > 8) { throw new IllegalArgumentException("Ambiguous observation entities"); }
	}
	public Entity self() { return entities.getFirst(); }
	public Entity entity(int objectId) { return entities.stream().filter(v -> v.objectId() == objectId).findFirst().orElse(null); }
	public float[] state()
	{
		final float[] state = new float[128];
		System.arraycopy(self().vector(), 0, state, 0, 80);
		final Field[] summary = { Field.HP, Field.MP, Field.LEVEL, Field.M_ATK, Field.M_DEF, Field.DISTANCE, Field.LOS };
		for (int role = 1; role < 4; role++)
		{
			final Role kind = Role.values()[role];
			final var group = entities.stream().filter(v -> v.role() == kind).toList();
			final int base = 40 + (role - 1) * 8;
			put(state, base, Fact.known(group.size()), kind == Role.SUMMON ? 2 : 9);
			for (int index = 0; index < summary.length; index++)
			{
				final Field field = summary[index];
				final var known = group.stream().map(v -> v.fact(field)).filter(Fact::known).mapToDouble(Fact::value).average();
				if (known.isPresent()) { put(state, base + index + 1, Fact.known(known.getAsDouble()), field.scale); }
			}
		}
		return state;
	}
	static void put(float[] values, int slot, Fact fact, double scale)
	{
		if (fact.known()) { values[slot * 2] = (float) Math.clamp(fact.value() / scale, -1, 1); values[slot * 2 + 1] = 1; }
	}
	public byte[] bytes()
	{
		try
		{
			final var buffer = new ByteArrayOutputStream(); final var out = new DataOutputStream(buffer);
			out.writeInt(VERSION); out.writeUTF(SCHEMA); out.writeLong(decisionId); out.writeLong(capturedNanos); out.writeLong(profileId);
			out.writeInt(actorObjectId); out.writeLong(actorEpoch); out.writeUTF(partyIdentity); out.writeInt(entities.size());
			for (Entity entity : entities)
			{
				out.writeInt(entity.role().ordinal()); out.writeInt(entity.objectId()); out.writeLong(entity.epoch()); out.writeInt(entity.ownerObjectId()); out.writeInt(entity.classId());
				for (Field field : Field.values()) { final var fact = entity.fact(field); out.writeBoolean(fact.known()); out.writeDouble(fact.value()); }
				out.writeInt(entity.gear().size()); for (Gear gear : entity.gear()) { out.writeInt(gear.slot()); out.writeInt(gear.itemId()); out.writeInt(gear.grade()); out.writeInt(gear.enchant()); out.writeUTF(gear.family()); out.writeInt(gear.augmentationId()); out.writeBoolean(gear.augmentationKnown()); }
				out.writeInt(entity.effects().size()); for (Effect effect : entity.effects()) { out.writeInt(effect.skillId()); out.writeInt(effect.level()); out.writeInt(effect.remainingSeconds()); out.writeBoolean(effect.durationKnown()); out.writeBoolean(effect.debuff()); }
				out.writeInt(entity.abilities().size()); for (Ability ability : entity.abilities()) { out.writeInt(ability.skillId()); out.writeInt(ability.level()); out.writeInt(ability.magicLevel()); out.writeDouble(ability.mp()); out.writeInt(ability.hitMillis()); out.writeLong(ability.reuseMillis()); out.writeInt(ability.range()); out.writeBoolean(ability.ready()); }
				out.writeInt(entity.pastActions().size()); for (PastAction action : entity.pastActions()) { out.writeInt(action.skillId()); out.writeInt(action.targetObjectId()); out.writeLong(action.observedNanos()); }
			}
			final int temporalCount = entities.stream().mapToInt(entity -> (int) entity.abilities().stream().filter(ability -> ability.timing() != null).count()).sum();
			if (temporalCount > 0)
			{
				out.writeInt(TacticalTimingV3.EXTENSION_MAGIC); out.writeInt(TacticalTimingV3.EXTENSION_VERSION); out.writeUTF(TacticalTimingV3.FINGERPRINT); out.writeInt(temporalCount);
				for (int entityIndex = 0; entityIndex < entities.size(); entityIndex++)
				{
					final var abilities = entities.get(entityIndex).abilities();
					for (int abilityIndex = 0; abilityIndex < abilities.size(); abilityIndex++)
					{
						final var ability = abilities.get(abilityIndex); if (ability.timing() == null) { continue; }
						out.writeInt(entityIndex); out.writeInt(abilityIndex); out.writeInt(ability.skillId()); out.writeInt(ability.level()); TacticalTimingV3.write(ability.timing(),out);
					}
				}
			}
			out.flush(); if (buffer.size() > 524288) { throw new IllegalArgumentException("Observation size"); } return buffer.toByteArray();
		}
		catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}
	public static TacticalObservationV3 restore(byte[] bytes)
	{
		if (bytes == null || bytes.length > 524288) { throw new IllegalArgumentException("Observation size"); }
		try
		{
			final var in = new DataInputStream(new ByteArrayInputStream(bytes));
			if (in.readInt() != VERSION || !SCHEMA.equals(in.readUTF())) { throw new IllegalArgumentException("Observation schema"); }
			final long decision = in.readLong(), now = in.readLong(), profile = in.readLong(); final int actor = in.readInt(); final long epoch = in.readLong(); final String party = in.readUTF();
			final var entities = new ArrayList<Entity>();
			final int count = bounded(in.readInt(), MAX_ENTITIES);
			for (int index = 0; index < count; index++)
			{
				final Role role = Role.values()[bounded(in.readInt(), 3)]; final int id = in.readInt(); final long ownerEpoch = in.readLong(); final int owner = in.readInt(), classId = in.readInt();
				final var facts = new EnumMap<Field, Fact>(Field.class);
				for (Field field : Field.values()) { final boolean known = in.readBoolean(); facts.put(field, new Fact(in.readDouble(), known)); }
				final var gear = new ArrayList<Gear>(); for (int n = bounded(in.readInt(), 32); n > 0; n--) { gear.add(new Gear(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readUTF(), in.readInt(), in.readBoolean())); }
				final var effects = new ArrayList<Effect>(); for (int n = bounded(in.readInt(), 64); n > 0; n--) { effects.add(new Effect(in.readInt(), in.readInt(), in.readInt(), in.readBoolean(), in.readBoolean())); }
				final var abilities = new ArrayList<Ability>(); for (int n = bounded(in.readInt(), 128); n > 0; n--) { abilities.add(new Ability(in.readInt(), in.readInt(), in.readInt(), in.readDouble(), in.readInt(), in.readLong(), in.readInt(), in.readBoolean())); }
				final var past = new ArrayList<PastAction>(); for (int n = bounded(in.readInt(), 8); n > 0; n--) { past.add(new PastAction(in.readInt(), in.readInt(), in.readLong())); }
				entities.add(new Entity(role, id, ownerEpoch, owner, classId, facts, gear, effects, abilities, past));
			}
			if (in.available() > 0)
			{
				if (in.readInt() != TacticalTimingV3.EXTENSION_MAGIC || in.readInt() != TacticalTimingV3.EXTENSION_VERSION || !TacticalTimingV3.FINGERPRINT.equals(in.readUTF()))
				{ throw new IllegalArgumentException("Unknown temporal extension"); }
				final int temporalCount = bounded(in.readInt(),MAX_ENTITIES * 128);
				if (temporalCount == 0) { throw new IllegalArgumentException("Empty temporal extension"); }
				long previous = -1;
				for (int index = 0; index < temporalCount; index++)
				{
					final int entityIndex = in.readInt(), abilityIndex = in.readInt(), skillId = in.readInt(), skillLevel = in.readInt();
					if (entityIndex < 0 || entityIndex >= entities.size() || abilityIndex < 0 || abilityIndex >= entities.get(entityIndex).abilities().size())
					{ throw new IllegalArgumentException("Temporal identity outside observation"); }
					final long identity = (long) entityIndex * 128 + abilityIndex;
					if (identity <= previous) { throw new IllegalArgumentException("Duplicate or unordered temporal identity"); } previous = identity;
					final var entity = entities.get(entityIndex); final var abilities = new ArrayList<>(entity.abilities()); final var old = abilities.get(abilityIndex);
					if (old.skillId() != skillId || old.level() != skillLevel) { throw new IllegalArgumentException("Temporal skill identity changed"); }
					abilities.set(abilityIndex,new Ability(old.skillId(),old.level(),old.magicLevel(),old.mp(),old.hitMillis(),old.reuseMillis(),old.range(),old.ready(),TacticalTimingV3.read(in)));
					entities.set(entityIndex,new Entity(entity.role(),entity.objectId(),entity.epoch(),entity.ownerObjectId(),entity.classId(),entity.facts(),entity.gear(),entity.effects(),abilities,entity.pastActions()));
				}
			}
			if (in.available() != 0) { throw new IllegalArgumentException("Trailing observation bytes"); }
			return new TacticalObservationV3(decision, now, profile, actor, epoch, party, entities);
		}
		catch (IOException | IndexOutOfBoundsException failure) { throw new IllegalArgumentException("Invalid observation", failure); }
	}
	private static int bounded(int value, int maximum) { if (value < 0 || value > maximum) { throw new IllegalArgumentException("Observation bound"); } return value; }
	public static String hash(byte[] value)
	{
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
		catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}
}

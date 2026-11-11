/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.*;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;

/** Contract checks: UNKNOWN, bounded rich facts and safe action typing. No native claims. */
public final class PhantomLearning003Contracts
{
	public static void main(String[] args) throws Exception
	{
		final var self = entity(Role.SELF, 1, 76, 1000, 0);
		final var ally = entity(Role.ALLY, 2, 61, 400, 100);
		final var observation = new TacticalObservationV3(1, 100, 7, 1, 10, "party", List.of(ally, self));
		check(observation.entities().getFirst().role() == Role.SELF, "deterministic identity order");
		check(observation.state().length == 128, "state contract");
		check(!PhantomLearning003Native.complete(32, 8, true) && !PhantomLearning003Native.complete(32, 8, false)
			&& PhantomLearning003Native.complete(32, 32, false), "partial native batch cannot publish a terminal phase marker");
		check(Arrays.equals(observation.bytes(), TacticalObservationV3.restore(observation.bytes()).bytes()), "rich facts roundtrip");
		temporalContract(self, ally);
		final var unknown = entity(Role.ENEMY, 3, 0, 0, 100);
		check(unknown.vector()[Field.LEVEL.ordinal() * 2 + 1] == 0, "unknown hostile level is not known zero");
		check(ally.vector()[Field.HP.ordinal() * 2 + 1] == 1, "known HP mask");
		final var changed = entity(Role.ALLY, 2, 68, 400, 500);
		check(!Arrays.equals(ally.vector(), changed.vector()), "level and distance are actual features");
		final var action = new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", 7, 1, 10, 1, 2, 20, "party", 1401, 1, 42, 3000, 600, false, true);
		check(action.features(observation).length == 64 && action.trainable(), "lawful HEAL feature contract");
		check(!action.withFamily(LegalActionV3.Family.CONTROL).trainable(), "future action cannot acquire native trainability");
		check(!action.current(1, 11, 2, 20, "party"), "stale actor epoch");
		check(!action.current(1, 10, 2, 20, "changed"), "stale party");
		final var fighterFacts = Map.of(Field.CLASS_ID, Fact.known(0), Field.HP, Fact.known(.5));
		final var fighter = new Entity(Role.ALLY, 2, 20, 0, 0, fighterFacts, List.of(), List.of(), List.of(), List.of());
		final var fighterObservation = new TacticalObservationV3(1, 100, 7, 1, 10, "party", List.of(self, fighter));
		check(action.features(fighterObservation)[13] == 1 && action.features(fighterObservation)[12] == 0, "known Human Fighter class0 is not UNKNOWN");
		check(org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.reward(500, 2000, 80, 1000000000) == .16f, "native useful HP minus exact MP/time");
		try { new TacticalObservationV3(1, 100, 7, 1, 10, "party", List.of(self, self)); throw new AssertionError("duplicate entity accepted"); }
		catch (IllegalArgumentException expected) { }
		final var configFile = java.nio.file.Files.createTempFile("learning003-config-", ".ini");
		try
		{
			java.nio.file.Files.writeString(configFile, "schemaVersion=1\nv3Enabled=true\nfeatureSchemaV3=TACTICAL_V3\n");
			org.l2jmobius.gameserver.phantoms.learning.TrainingConfig.load(configFile);
			java.nio.file.Files.writeString(configFile, "schemaVersion=1\nv3Enabled=true\nfeatureSchemaV3=wrong\n");
			try { org.l2jmobius.gameserver.phantoms.learning.TrainingConfig.load(configFile); throw new AssertionError("Wrong V3 schema accepted before native bootstrap"); }
			catch (IllegalArgumentException expected) { }
		}
		finally { java.nio.file.Files.deleteIfExists(configFile); }
		System.out.println("LEARNING003_CONTRACT_PASS unknownMasks=true richRoundtrip=true staleAuthority=true futureUntrained=true");
	}
	private static Entity entity(Role role, int id, int level, double hp, double distance)
	{
		return new Entity(role, id, id * 10L, 0, role == Role.ENEMY ? 0 : 105,
			Map.of(Field.LEVEL, level == 0 ? Fact.unknown() : Fact.known(level), Field.HP, role == Role.ENEMY ? Fact.unknown() : Fact.known(hp / 1000), Field.DISTANCE, Fact.known(distance)),
			List.of(), List.of(), List.of(), List.of());
	}
	private static void temporalContract(Entity self, Entity ally) throws Exception
	{
		final var ability = new Ability(1401, 11, 75, 80, 5000, 0, 600, true);
		final var legacySelf = new Entity(self.role(), self.objectId(), self.epoch(), self.ownerObjectId(), self.classId(), self.facts(), self.gear(), self.effects(), List.of(ability), self.pastActions());
		final var legacy = new TacticalObservationV3(1, 100, 7, 1, 10, "party", List.of(legacySelf, ally));
		final byte[] oldBytes = legacy.bytes();
		check(TacticalObservationV3.SCHEMA.equals("a3648815c4afab480c4217c54e5a16e9b7e09118cbd50774c4e4f3ca83e15c58")
			&& TacticalObservationV3.hash(oldBytes).equals("9296859f0fba806e28ca5e8be613fab5db7d5dcb143a09dee081829708c03ff0"), "temporal addition relabelled frozen model2 schema/legacy bytes");
		System.out.println("LEARNING003_TEMPORAL_LEGACY schema=" + TacticalObservationV3.SCHEMA + " bytesSha=" + TacticalObservationV3.hash(oldBytes));
		final Class<?> timing;
		try { timing = Class.forName("org.l2jmobius.gameserver.phantoms.learning.TacticalTimingV3"); }
		catch (ClassNotFoundException missing) { throw new AssertionError("V3 native temporal contract is missing", missing); }
		final var remaining = timing.getMethod("remainingReuse", long.class, Long.class, long.class, boolean.class);
		final Object shortReuse = remaining.invoke(null, -1L, 1750L, 1000L, false);
		check((boolean) shortReuse.getClass().getMethod("known").invoke(shortReuse)
			&& (long) shortReuse.getClass().getMethod("value").invoke(shortReuse) == 750, "subsecond disabled-map reuse lost");
		final Object maximum = remaining.invoke(null, 900L, 1750L, 1000L, false);
		check((long) maximum.getClass().getMethod("value").invoke(maximum) == 900, "reuse must use maximum native source");
		for (Object unknown : List.of(remaining.invoke(null, 900L, Long.MAX_VALUE, 1000L, false), remaining.invoke(null, 0L, null, 1000L, true)))
		{ check(!(boolean) unknown.getClass().getMethod("known").invoke(unknown), "indefinite/global disabled became known zero"); }
		final Object temporal = timing.getMethod("unknown").invoke(null);
		final Class<?> timingRecord = temporal.getClass();
		check(!(boolean) timingRecord.getMethod("interruptibleKnown").invoke(temporal)
			&& !(boolean) timingRecord.getMethod("interruptibleNow").invoke(temporal), "unknown timing invented interruptibility");
		final Object probability = timingRecord.getMethod("futureInterruptProbability").invoke(temporal);
		check(!(boolean) probability.getClass().getMethod("known").invoke(probability), "unknown timing invented future interrupt probability");
		final var richAbility = Ability.class.getConstructor(int.class, int.class, int.class, double.class, int.class, long.class, int.class, boolean.class, timingRecord)
			.newInstance(1401, 11, 75, 80d, 5000, 0L, 600, true, temporal);
		final var richSelf = new Entity(self.role(), self.objectId(), self.epoch(), self.ownerObjectId(), self.classId(), self.facts(), self.gear(), self.effects(), List.of(richAbility), self.pastActions());
		final var rich = new TacticalObservationV3(1, 100, 7, 1, 10, "party", List.of(richSelf, ally));
		final byte[] richBytes = rich.bytes();
		check(Arrays.equals(legacy.state(), rich.state()), "additive timing changed frozen state128 inputs");
		check(richBytes.length > oldBytes.length && Arrays.equals(oldBytes, Arrays.copyOf(richBytes, oldBytes.length)), "temporal extension changed legacy bytes");
		check(Arrays.equals(richBytes, TacticalObservationV3.restore(richBytes).bytes()), "temporal extension roundtrip");
		check(Arrays.equals(oldBytes, TacticalObservationV3.restore(oldBytes).bytes()), "old timingless bytes gained invented extension");
		final byte[] bad = richBytes.clone(); bad[oldBytes.length] ^= 1;
		try { TacticalObservationV3.restore(bad); throw new AssertionError("unknown temporal extension accepted"); } catch (IllegalArgumentException expected) { }
		final byte[] wrongVersion = richBytes.clone(); java.nio.ByteBuffer.wrap(wrongVersion).putInt(oldBytes.length + 4, 99);
		try { TacticalObservationV3.restore(wrongVersion); throw new AssertionError("unknown temporal version accepted"); } catch (IllegalArgumentException expected) { }
		final byte[] wrongFingerprint = richBytes.clone(); wrongFingerprint[oldBytes.length + 10] ^= 1;
		try { TacticalObservationV3.restore(wrongFingerprint); throw new AssertionError("foreign temporal fingerprint accepted"); } catch (IllegalArgumentException expected) { }
		try { TacticalObservationV3.restore(Arrays.copyOf(richBytes, richBytes.length - 1)); throw new AssertionError("truncated temporal extension accepted"); } catch (IllegalArgumentException expected) { }
		try { TacticalObservationV3.restore(Arrays.copyOf(richBytes, richBytes.length + 1)); throw new AssertionError("trailing temporal extension accepted"); } catch (IllegalArgumentException expected) { }
	}
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
	private PhantomLearning003Contracts() { }
}

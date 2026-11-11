/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Native encounter settlement, separate from individual action rewards. Unreceipted facts stay UNKNOWN. */
public record TacticalEpisodeV3(String id, String ownerNonce, String scope, long startedNanos, long settledNanos,
	String reason, List<Actor> actors, List<Kill> kills, List<String> eventKeys, double exactObservedDamage,
	double usefulObservedHeal, double exactObservedMp, int observedDeaths)
{
	@FunctionalInterface
	public interface NativeDrain { void finish() throws Exception; }
	/** The final snapshot must observe every earned callback that the canonical owner is draining. */
	public static TacticalEpisodeV3 afterNativeDrain(NativeDrain drain, java.util.function.Supplier<TacticalEpisodeV3> finalSnapshot) throws Exception
	{
		drain.finish();
		return finalSnapshot.get();
	}
	public record Actor(long profile, int objectId, long epoch, int classId, int level, double hp, double mp, boolean dead, Long exp, Long sp)
	{
		public Actor
		{
			if (profile <= 0 || objectId <= 0 || epoch <= 0 || classId < 0 || level <= 0 || !Double.isFinite(hp) || hp < 0
				|| !Double.isFinite(mp) || mp < 0 || exp != null && exp < 0 || sp != null && sp < 0)
			{ throw new IllegalArgumentException("Invalid native episode participant"); }
		}
		String json()
		{
			return "{\"profile\":" + profile + ",\"objectId\":" + objectId + ",\"epoch\":" + epoch + ",\"class\":" + classId
				+ ",\"level\":" + level + ",\"remainingHp\":" + hp + ",\"remainingMp\":" + mp + ",\"dead\":" + dead + ",\"nativeCreditedExp\":" + exp + ",\"nativeCreditedSp\":" + sp + "}";
		}
	}
	public record Kill(int targetObjectId, int npcId, long spawnGeneration, int instanceId, int creditedActorId, long nativeSequence)
	{
		public Kill
		{
			if (targetObjectId <= 0 || npcId <= 0 || spawnGeneration <= 0 || instanceId < 0 || creditedActorId <= 0 || nativeSequence <= 0)
			{ throw new IllegalArgumentException("Native target kill identity required"); }
		}
		String json()
		{
			return "{\"targetObjectId\":" + targetObjectId + ",\"npcId\":" + npcId + ",\"spawnGeneration\":" + spawnGeneration
				+ ",\"instanceId\":" + instanceId + ",\"creditedActorId\":" + creditedActorId + ",\"nativeKillSequence\":" + nativeSequence + "}";
		}
	}
	public TacticalEpisodeV3
	{
		actors = List.copyOf(actors); kills = List.copyOf(kills); eventKeys = List.copyOf(eventKeys);
		if (id == null || !id.matches("[a-f0-9]{64}") || ownerNonce == null || !ownerNonce.matches("[a-f0-9]{32}")
			|| !Set.of("SOLO", "PARTY").contains(scope) || startedNanos <= 0 || settledNanos <= startedNanos || reason == null
			|| reason.isBlank() || reason.length() > 128 || reason.chars().anyMatch(v -> v < 32) || actors.isEmpty() || actors.size() > 9
			|| kills.size() > 16 || eventKeys.size() > 1024 || observedDeaths < 0 || observedDeaths > 1024
			|| !finiteNonnegative(exactObservedDamage, usefulObservedHeal, exactObservedMp)
			|| actors.stream().map(Actor::objectId).distinct().count() != actors.size() || new HashSet<>(eventKeys).size() != eventKeys.size()
			|| eventKeys.stream().anyMatch(key -> !key.matches("[a-f0-9]{64}")) || reason.equals("NATIVE_KILL") && kills.isEmpty()
			|| actors.stream().filter(Actor::dead).count() > observedDeaths)
		{ throw new IllegalArgumentException("Incomplete or fabricated native episode settlement"); }
		for (var kill : kills)
		{
			if (actors.stream().noneMatch(actor -> actor.objectId() == kill.creditedActorId()))
			{ throw new IllegalArgumentException("Foreign episode kill receipt"); }
		}
	}
	private static boolean finiteNonnegative(double... values)
	{
		for (double value : values) { if (!Double.isFinite(value) || value < 0) { return false; } }
		return true;
	}
	public String outcome()
	{
		return observedDeaths > 0 || actors.stream().anyMatch(Actor::dead) ? "FAILED" : reason.equals("NATIVE_KILL") ? "COMPLETED" : "INCOMPLETE";
	}
	public String json()
	{
		return "{\"schemaVersion\":4,\"episode\":" + TrainingStore.quote(id) + ",\"ownerNonce\":" + TrainingStore.quote(ownerNonce)
			+ ",\"goal\":" + TrainingStore.quote(scope + "_PVE_TARGET") + ",\"scope\":" + TrainingStore.quote(scope) + ",\"outcome\":" + TrainingStore.quote(outcome())
			+ ",\"reason\":" + TrainingStore.quote(reason) + ",\"durationNanos\":" + (settledNanos - startedNanos) + ",\"confirmedKill\":" + !kills.isEmpty()
			+ ",\"actors\":[" + actors.stream().map(Actor::json).collect(java.util.stream.Collectors.joining(",")) + "],\"nativeKills\":["
			+ kills.stream().map(Kill::json).collect(java.util.stream.Collectors.joining(",")) + "],\"eventKeys\":["
			+ eventKeys.stream().map(TrainingStore::quote).collect(java.util.stream.Collectors.joining(",")) + "]"
			+ ",\"exactObservedDamage\":" + exactObservedDamage + ",\"usefulObservedHeal\":" + usefulObservedHeal + ",\"exactObservedMp\":" + exactObservedMp
			+ ",\"observedDeathsLowerBound\":" + observedDeaths + ",\"completeDeathCount\":null,\"wholePartyDamage\":null,\"consumables\":null,\"loot\":null,\"adenaNet\":null"
			+ ",\"terminalRewardUsedForTraining\":false,\"metricScope\":\"Selected exact native action receipts plus canonical kill/EXP/SP writers; unknown metrics are not zero\"}";
	}
	public String sha256() { return TacticalObservationV3.hash(json().getBytes(StandardCharsets.UTF_8)); }
}

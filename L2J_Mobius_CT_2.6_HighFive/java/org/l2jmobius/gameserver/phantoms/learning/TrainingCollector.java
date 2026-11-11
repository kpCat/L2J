/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;

/** A bounded in-memory observer. SQL verification and credit happen later at the episode boundary. */
public final class TrainingCollector implements LearningPolicy.Observer
{
	private static final int CAPACITY = 1024;
	public record Observation(String eventKey, String json) { }
	private record Prepared(LearningPolicy.Attempt attempt, Map<String, Object> facts) { }
	private static final class Episode
	{
		final String id;
		final Map<String, Prepared> issued = new ConcurrentHashMap<>();
		final Map<String, Observation> recorded = new ConcurrentHashMap<>();
		Episode(String id) { this.id = id; }
	}
	private final AtomicReference<Episode> _episode = new AtomicReference<>();

	public void begin(String episodeId)
	{
		if (episodeId == null || episodeId.isBlank() || episodeId.length() > 128) { throw new IllegalArgumentException("Неверный идентификатор эпизода."); }
		if (!_episode.compareAndSet(null, new Episode(episodeId))) { throw new IllegalStateException("Предыдущий эпизод ещё не завершён."); }
	}
	public int size()
	{
		final Episode episode = _episode.get();
		return episode == null ? 0 : episode.recorded.size();
	}
	public List<Observation> snapshot()
	{
		return observations(_episode.get());
	}
	public List<Observation> end()
	{
		return observations(_episode.getAndSet(null));
	}
	private static List<Observation> observations(Episode episode)
	{
		return episode == null ? List.of() : episode.recorded.values().stream().sorted(Comparator.comparing(Observation::eventKey)).toList();
	}

	@Override
	public void issued(LearningPolicy.Attempt attempt, Player actor, Monster target)
	{
		final Episode episode = _episode.get();
		if (episode == null || episode.issued.size() >= CAPACITY || attempt.offered().size() < 2 || attempt.offered().size() > 4) { return; }
		final Map<String, Object> facts = new LinkedHashMap<>();
		facts.put("schemaVersion", 1);
		facts.put("episode", episode.id);
		facts.put("observedAtMillis", System.currentTimeMillis());
		facts.put("profileId", attempt.request().profileId());
		facts.put("actor", actorFacts(actor));
		facts.put("epoch", actor.getNativeWorkOwner() == null ? null : actor.getNativeWorkOwner().epoch());
		facts.put("combatGeneration", attempt.generation());
		facts.put("sequence", attempt.sequence());
		facts.put("scope", attempt.request().learningScope().name());
		facts.put("decisionNanos", attempt.decisionNanos());
		facts.put("featureSchema", LearningFeatures.SCHEMA);
		final var offered = new ArrayList<Map<String, Object>>();
		for (var action : attempt.offered())
		{
			final Map<String, Object> item = new LinkedHashMap<>();
			item.put("skill", action.skill().skillId()); item.put("level", action.skill().skillLevel());
			item.put("capability", "combat.ranged_magic_damage");
			item.put("offered", true); item.put("dispatchRevalidation", "REQUIRED");
			item.put("mpCost", action.mpCost()); item.put("castMillis", action.castMillis()); item.put("itemCost", action.itemCost());
			// V1 contains explicit value/presence pairs; an unobserved zero remains masked.
			final var features = new ArrayList<Float>(LearningFeatures.SIZE);
			for (float value : action.features()) { features.add(value); }
			item.put("featuresV1WithMasks", features);
			offered.add(item);
		}
		facts.put("offeredActions", offered);
		facts.put("baselineSkill", attempt.baseline().skillId());
		facts.put("chosenSkill", attempt.candidate().skill().skillId());
		facts.put("chosenLevel", attempt.candidate().skill().skillLevel());
		final var allies = new ArrayList<Map<String, Object>>();
		if (actor.getParty() != null)
		{
			for (Player member : actor.getParty().getMembers().stream().limit(9).toList()) { allies.add(actorFacts(member)); }
		}
		facts.put("allies", allies);
		final Map<String, Object> enemy = new LinkedHashMap<>();
		enemy.put("kind", "NPC"); enemy.put("npcId", target.getId()); enemy.put("objectId", target.getObjectId());
		enemy.put("spawn", target.getNativeEvidenceTarget().toString()); enemy.put("level", target.getLevel());
		enemy.put("hp", target.getCurrentHp()); enemy.put("maxHp", target.getMaxHp());
		enemy.put("x", target.getX()); enemy.put("y", target.getY()); enemy.put("z", target.getZ());
		enemy.put("instance", target.getInstanceId()); enemy.put("distance", actor.calculateDistance2D(target));
		enemy.put("targetObjectId", target.getTarget() == null ? null : target.getTarget().getObjectId());
		enemy.put("resistances", null); enemy.put("bearing", null);
		facts.put("enemy", enemy);
		facts.put("unknownFacts", List.of("enemy.resistances", "enemy.bearing", "allies.roles", "pets", "lootReceipt", "expReceipt", "spReceipt", "spoilReceipt", "sweepReceipt"));
		episode.issued.putIfAbsent(identity(attempt), new Prepared(attempt, facts));
	}

	@Override
	public void recorded(LearningPolicy.Attempt attempt, LearningStore.Evidence evidence)
	{
		final Episode episode = _episode.get();
		if (episode == null || episode.recorded.size() >= CAPACITY || !evidence.eventKey().matches("[0-9a-f]{64}")) { return; }
		final Prepared prepared = episode.issued.remove(identity(attempt));
		if (prepared == null || prepared.attempt() != attempt || evidence.damage() <= 0 || !evidence.scope().equals(attempt.request().learningScope().name())) { return; }
		final Map<String, Object> envelope = new LinkedHashMap<>();
		envelope.put("schemaVersion", 1); envelope.put("provenance", "NATIVE");
		envelope.put("evidenceKind", "EXACT_CAST_HP"); envelope.put("eventKey", evidence.eventKey());
		envelope.put("actorGroup", evidence.actorGroup()); envelope.put("policyVersion", evidence.version());
		envelope.put("skill", evidence.skill()); envelope.put("level", evidence.level());
		envelope.put("exactHp", evidence.damage()); envelope.put("reward", evidence.reward());
		envelope.put("observation", prepared.facts());
		envelope.put("sqlDurability", "VERIFY_AT_SETTLEMENT");
		episode.recorded.putIfAbsent(evidence.eventKey(), new Observation(evidence.eventKey(), json(envelope)));
	}

	private static String identity(LearningPolicy.Attempt attempt)
	{ return attempt.request().profileId() + ":" + attempt.generation() + ":" + attempt.sequence(); }

	private static Map<String, Object> actorFacts(Player player)
	{
		final Map<String, Object> facts = new LinkedHashMap<>();
		facts.put("objectId", player.getObjectId()); facts.put("classId", player.getActiveClass()); facts.put("level", player.getLevel());
		facts.put("hp", player.getCurrentHp()); facts.put("maxHp", player.getMaxHp());
		facts.put("mp", player.getCurrentMp()); facts.put("maxMp", player.getMaxMp());
		facts.put("cp", player.getCurrentCp()); facts.put("maxCp", player.getMaxCp());
		facts.put("x", player.getX()); facts.put("y", player.getY()); facts.put("z", player.getZ()); facts.put("instance", player.getInstanceId());
		facts.put("weaponId", player.getActiveWeaponItem() == null ? null : player.getActiveWeaponItem().getId());
		facts.put("buffs", player.getEffectList().getBuffs().stream().limit(32).map(info -> info.getSkill().getId()).toList());
		facts.put("debuffs", player.getEffectList().getDebuffs().stream().limit(16).map(info -> info.getSkill().getId()).toList());
		facts.put("dead", player.isAlikeDead());
		return facts;
	}
	public static String actorSnapshot(Player player) { return json(actorFacts(player)); }

	/** Small JSON writer for bounded immutable facts, without new runtime dependencies. */
	public static String json(Object value)
	{
		if (value == null) { return "null"; }
		if (value instanceof Boolean || value instanceof Number)
		{
			return value instanceof Double doubleValue && !Double.isFinite(doubleValue) || value instanceof Float floatValue && !Float.isFinite(floatValue) ? "null" : value.toString();
		}
		if (value instanceof Map<?, ?> map)
		{
			return map.entrySet().stream().map(entry -> json(entry.getKey().toString()) + ":" + json(entry.getValue())).collect(java.util.stream.Collectors.joining(",", "{", "}"));
		}
		if (value instanceof Iterable<?> list)
		{
			final List<String> items = new ArrayList<>();
			for (Object item : list) { items.add(json(item)); }
			return "[" + String.join(",", items) + "]";
		}
		final StringBuilder output = new StringBuilder("\"");
		for (char character : value.toString().toCharArray())
		{
			switch (character)
			{
				case '"' -> output.append("\\\"");
				case '\\' -> output.append("\\\\");
				case '\n' -> output.append("\\n");
				case '\r' -> output.append("\\r");
				case '\t' -> output.append("\\t");
				default -> { if (character < 32) { output.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character)); } else { output.append(character); } }
			}
		}
		return output.append('"').toString();
	}
}

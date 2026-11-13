/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef;

/** Reproducible planning facts only. Native owners determine legality, stock choice and execution. */
public final class AutonomousTrainingV1
{
	public static final String VERSION = "AUTONOMOUS_TRAINING_V1";
	public static final String COMPARATOR = "STOCK_ON_FROZEN_SHORTLIST_V1";
	public record CohortSlot(int ordinal, String cell, String block, String split, boolean experimental) { }
	/** Four independent A/B blocks leave three controls after excluding the paid block. */
	public static List<CohortSlot> cohortSlots(List<String> cells)
	{
		if (cells == null || cells.isEmpty() || cells.size() > 32 || cells.stream().anyMatch(c -> c == null || !c.matches("[A-Za-z0-9_-]{1,80}"))
			|| cells.stream().distinct().count() != cells.size()) { throw new IllegalArgumentException("Недопустимые или повторные cohort cells."); }
		final var result = new ArrayList<CohortSlot>();
		for (String cell : cells)
		{
			for (int block = 0; block < 4; block++)
			{
				final String key = hash(cell + "/TRAIN/" + block);
				// Alternate order without selecting on outcomes or native RNG.
				for (int side = 0; side < 2; side++) { result.add(new CohortSlot(result.size(), cell, key, "TRAIN", (block + side) % 2 == 1)); }
			}
			for (String split : List.of("TUNING", "AUDIT"))
			{ for (int block = 0; block < 2; block++) { result.add(new CohortSlot(result.size(), cell, hash(cell + "/" + split + "/" + block), split, false)); } }
		}
		return List.copyOf(result);
	}
	public record Shortlist(List<SkillRef> candidates, long nextCursor, boolean randomInclusion)
	{
		public Shortlist
		{
			candidates = List.copyOf(candidates);
			if (nextCursor < 0 || randomInclusion || candidates.size() > 4 || candidates.stream().map(SkillRef::skillId).distinct().count() != candidates.size())
			{ throw new IllegalArgumentException("Повреждён frozen shortlist или отсутствует random-inclusion contract."); }
		}
	}
	/** One native owner/mode per request; a magic shortlist cannot contain physical alternatives. */
	public record ActionPool(LegalActionV3.Family family, PhantomCombatMode mode, List<SkillRef> skills)
	{
		public ActionPool
		{
			if (family == null || (family != LegalActionV3.Family.DAMAGE && family != LegalActionV3.Family.HEAL)
				|| (family == LegalActionV3.Family.DAMAGE ? mode == null : mode != null) || skills == null
				|| skills.size() > PhantomNativeCombatCapability.MAXIMUM_SKILLS)
			{ throw new IllegalArgumentException("Недопустимое семейство или режим native-действий."); }
			skills = List.copyOf(skills);
			if (skills.stream().map(SkillRef::skillId).distinct().count() != skills.size())
			{ throw new IllegalArgumentException("Режим содержит несколько версий одного навыка."); }
		}
		public String key() { return family + "/mode/" + (mode == null ? "PARTY" : mode.name()); }
	}
	/** Actual acquired refs and adapter support are separate inputs; neither is a class-tree grant. */
	public record ActorSeed(String sourceSnapshotSha, int classId, int level, int classIndex, List<SkillRef> acquired,
		List<ActionPool> supported)
	{
		public ActorSeed
		{
			if (sourceSnapshotSha == null || !sourceSnapshotSha.matches("[a-f0-9]{64}") || PlayerClass.getPlayerClass(classId) == null
				|| level < 1 || level > 255 || classIndex < 0 || acquired == null || acquired.size() > PhantomNativeCombatCapability.MAXIMUM_SKILLS || supported == null)
			{ throw new IllegalArgumentException("Недопустимый источник фактически изученных навыков."); }
			acquired = List.copyOf(acquired);
			if (acquired.stream().map(SkillRef::skillId).distinct().count() != acquired.size())
			{ throw new IllegalArgumentException("Исходный набор содержит несколько версий одного навыка."); }
			final var learned = Set.copyOf(acquired);
			for (var pool : supported)
			{
				if (pool.skills().stream().anyMatch(skill -> !learned.contains(skill)))
				{ throw new IllegalArgumentException("Предложение включает неподдерживаемый или неизученный навык."); }
			}
			supported = List.copyOf(supported);
			if (supported.stream().map(ActionPool::key).distinct().count() != supported.size())
			{ throw new IllegalArgumentException("Режим native-действий повторяется."); }
		}
	}
	/** A proposal still requires actual target/Geo/owner/readiness admission before CUT. */
	public record Proposal(ActorSeed actor, ActionPool pool, String shortlistKey, Shortlist shortlist)
	{
		public Proposal
		{
			Objects.requireNonNull(actor); Objects.requireNonNull(pool); Objects.requireNonNull(shortlist);
			if (!actor.supported().contains(pool) || !Objects.equals(shortlistKey, "shortlist/" + actor.sourceSnapshotSha() + "/" + pool.key())
				|| shortlist.candidates().size() < (pool.family() == LegalActionV3.Family.DAMAGE ? 2 : 1) || !pool.skills().containsAll(shortlist.candidates()))
			{ throw new IllegalArgumentException("Предложение сцены не соответствует фактически изученному pool."); }
		}
	}
	private record Candidate(ActorSeed actor, ActionPool pool, long classVisits, long familyVisits, long sourceVisits, String tie) { }

	public static Optional<Proposal> nextCandidate(List<ActorSeed> actors, Map<String, Long> attempts, Map<String, Long> shortlistCursors, long sequence, long seed)
	{
		if (actors == null || actors.size() > 4096 || sequence < 0 || attempts == null || shortlistCursors == null)
		{ throw new IllegalArgumentException("Недопустимые данные curriculum или сохранённый курсор."); }
		if (actors.stream().map(ActorSeed::sourceSnapshotSha).distinct().count() != actors.size())
		{ throw new IllegalArgumentException("Источники curriculum повторяются."); }
		final Comparator<Candidate> order = Comparator.comparingLong(Candidate::classVisits).thenComparingLong(Candidate::familyVisits)
			.thenComparingLong(Candidate::sourceVisits).thenComparing(Candidate::tie);
		return actors.stream().flatMap(actor -> actor.supported().stream()
			.filter(pool -> pool.skills().size() >= (pool.family() == LegalActionV3.Family.DAMAGE ? 2 : 1))
			.map(pool -> new Candidate(actor, pool, count(attempts, "class/" + actor.classId()),
				count(attempts, "class/" + actor.classId() + "/family/" + pool.key()), count(attempts, "source/" + actor.sourceSnapshotSha()),
				hash(VERSION + "/" + seed + "/" + sequence + "/" + actor.sourceSnapshotSha() + "/" + pool.key()))))
			.min(order).map(candidate ->
			{
				final String key = "shortlist/" + candidate.actor().sourceSnapshotSha() + "/" + candidate.pool().key();
				return new Proposal(candidate.actor(), candidate.pool(), key,
					shortlist(candidate.pool().skills(), count(shortlistCursors, key), 4));
			});
	}

	private static long count(Map<String, Long> values, String key)
	{
		final Long value = values.get(key);
		if (value == null && !values.containsKey(key)) { return 0; }
		if (value == null || value < 0) { throw new IllegalArgumentException("Повреждён счётчик покрытия или курсор shortlist."); }
		return value;
	}

	/** A full circular visit, not the first four IDs on every episode. No randomness or fake actions. */
	public static Shortlist shortlist(List<SkillRef> eligible, long cursor, int maximum)
	{
		if (eligible == null || eligible.size() > PhantomNativeCombatCapability.MAXIMUM_SKILLS || cursor < 0 || maximum < 1 || maximum > 4)
		{ throw new IllegalArgumentException("Недопустимый полный набор, курсор или размер shortlist."); }
		if (eligible.stream().map(SkillRef::skillId).distinct().count() != eligible.size())
		{ throw new IllegalArgumentException("Shortlist содержит несколько версий одного изученного навыка."); }
		final var ordered = eligible.stream().sorted(Comparator.comparingInt(SkillRef::skillId).thenComparingInt(SkillRef::skillLevel)).toList();
		final var selected = new ArrayList<SkillRef>();
		final int count = Math.min(maximum, ordered.size());
		for (int index = 0; index < count; index++) { selected.add(ordered.get((int) ((cursor % ordered.size() + index) % ordered.size()))); }
		return new Shortlist(selected, Math.addExact(cursor, count), false);
	}
	private static String hash(String value)
	{
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
		catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}
	private AutonomousTrainingV1() { }
}

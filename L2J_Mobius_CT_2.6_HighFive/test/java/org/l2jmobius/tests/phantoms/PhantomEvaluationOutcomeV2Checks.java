/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;

/** Behavioral contracts: actual deaths stay in the denominator; unverifiable outcomes do not. */
public final class PhantomEvaluationOutcomeV2Checks
{
	static final String SHA = "a".repeat(64), OTHER = "b".repeat(64);
	static PhantomEvaluationOutcomeV2.Arm arm(String side, String reason, boolean dead, boolean kill, boolean admitted, long terminal, long deadline)
	{
		final int actor = side.equals("STOCK") ? 101 : 201, target = actor + 1;
		final var identity = new PhantomEvaluationOutcomeV2.Identity(actor, 1, 0);
		final var episode = new TacticalEpisodeV3(side.equals("STOCK") ? SHA : OTHER, "c".repeat(32), "SOLO", 100, terminal + 1, reason,
			List.of(new TacticalEpisodeV3.Actor(actor, actor, 1, 1, 76, dead ? 0 : 500, 200, dead, null, null)),
			kill ? List.of(new TacticalEpisodeV3.Kill(target, 1, 1, 0, actor, 1)) : List.of(), List.of(), 0, 0, 0, dead ? 1 : 0);
		return new PhantomEvaluationOutcomeV2.Arm(SHA, "SOLO_DAMAGE", side, side.equals("STOCK") ? 0 : 1, 183,
			new PhantomEvaluationOutcomeV2.Authority(SHA, SHA, SHA, 14, SHA, SHA), SHA, List.of(identity), new PhantomEvaluationOutcomeV2.Identity(target, 1, 0),
			admitted, true, false, 100, terminal, deadline, episode, List.of(), Map.of("settlement.json", episode.sha256()), "ORDINARY", "");
	}
	static void outcomes()
	{
		final var died = arm("STOCK", "NATIVE_ACTOR_DIED", true, false, true, 200, 300);
		final var won = arm("LEARNED", "NATIVE_KILL", false, true, true, 200, 300);
		final var pair = PhantomEvaluationOutcomeV2.compare(died, won);
		check(pair.comparable() && !pair.bothGoalSucceeded() && pair.winDifference() == 1 && pair.killTimeDeltaMillis() == null,
			"V2_DEATH_KILL_MUST_BE_TERMINAL_COMPARABLE_WITH_DELTA_ONE");
		final var bothDied = PhantomEvaluationOutcomeV2.compare(died, arm("LEARNED", "NATIVE_ACTOR_DIED", true, false, true, 200, 300));
		check(bothDied.comparable() && bothDied.winDifference() == 0 && !bothDied.bothGoalSucceeded(), "V2_BOTH_DEATHS_RETAINED");
		final var bothWon = PhantomEvaluationOutcomeV2.compare(arm("STOCK", "NATIVE_KILL", false, true, true, 200, 300), won);
		check(bothWon.comparable() && bothWon.bothGoalSucceeded() && bothWon.killTimeDeltaMillis() == 0, "V2_BOTH_KILLS");
	}
	static void check(boolean condition, String reason) { if (!condition) { throw new AssertionError(reason); } }
	static void invalidEvidence()
	{
		final var stock = arm("STOCK", "NATIVE_ACTOR_DIED", true, false, true, 200, 300);
		for (var bad : List.of(arm("LEARNED", "NATIVE_ACTOR_DIED", false, false, true, 200, 300), arm("LEARNED", "NATIVE_KILL", false, true, false, 200, 300),
			arm("LEARNED", "REQUESTED_DRAIN", false, false, true, 200, 300), arm("LEARNED", "TIMEOUT", false, false, true, 200, 300),
			stock))
		{ check(!PhantomEvaluationOutcomeV2.compare(stock, bad).comparable(), "V2_UNPROVEN_OUTCOME_EXCLUDED"); }
		final var timeout = arm("LEARNED", "TIMEOUT", false, false, true, 300, 300);
		final var pair = PhantomEvaluationOutcomeV2.compare(stock, timeout);
		check(pair.comparable() && pair.reason().equals("COMPARABLE_WITH_TIMEOUT") && pair.winDifference() == 0 && pair.killTimeDeltaMillis() == null, "V2_VERIFIED_TIMEOUT_CENSORED");
		for (String mutation : List.of("MISSING", "CANCELLED", "AUTHORITY", "PHYSICAL", "ACTOR", "TARGET", "EVENT"))
		{ check(!PhantomEvaluationOutcomeV2.compare(stock, mutate(timeout, mutation, stock)).comparable(), "V2_MUTATION_REFUSED_" + mutation); }
	}
	static PhantomEvaluationOutcomeV2.Arm mutate(PhantomEvaluationOutcomeV2.Arm a, String mutation, PhantomEvaluationOutcomeV2.Arm other)
	{
		return new PhantomEvaluationOutcomeV2.Arm(a.pairId(), a.direction(), a.side(), a.position(), a.seed(), mutation.equals("AUTHORITY") ? new PhantomEvaluationOutcomeV2.Authority(OTHER, SHA, SHA, 14, SHA, SHA) : a.authority(),
			mutation.equals("PHYSICAL") ? OTHER : a.physicalSha(), mutation.equals("ACTOR") ? other.actors() : a.actors(), mutation.equals("TARGET") ? other.target() : a.target(), a.nativeAdmitted(), a.afterCut(), mutation.equals("CANCELLED"),
			a.cutNanos(), a.terminalNanos(), a.deadlineNanos(), mutation.equals("MISSING") ? null : a.episode(), mutation.equals("EVENT") ? List.of(new PhantomEvaluationOutcomeV2.Paid(SHA, SHA, true, 1, 1)) : a.paid(), a.sourceHashes(), a.stratum(), a.refusal());
	}
	static void codecAndStatistics() throws Exception
	{
		final var stock = arm("STOCK", "NATIVE_ACTOR_DIED", true, false, true, 200, 300);
		final var learned = arm("LEARNED", "NATIVE_KILL", false, true, true, 200, 300);
		final byte[] encoded = PhantomEvaluationOutcomeV2.encode(stock);
		check(PhantomEvaluationOutcomeV2.decode(encoded).equals(stock), "V2_CODEC_ROUND_TRIP");
		try { PhantomEvaluationOutcomeV2.decode(java.util.Arrays.copyOf(encoded, encoded.length - 1)); throw new AssertionError("TRUNCATED_CODEC_ACCEPTED"); } catch (IllegalArgumentException expected) { }
		final var stats = PhantomEvaluationOutcomeV2.statistics(List.of(PhantomEvaluationOutcomeV2.compare(stock, learned)));
		check(PhantomEvaluationOutcomeV2.json(stats).contains("\"exactTwoSidedSignP\":1.0"), "SMALL_N_SIGN_NOT_ADVANTAGE");
		check(PhantomEvaluationOutcomeV2.armSummary(stock).get("wholePartyMp") == null && PhantomEvaluationOutcomeV2.armSummary(stock).get("timeToKillMillis") == null, "UNKNOWN_IS_NOT_ZERO");
	}
	static void curriculum()
	{
		final var lower = new PhantomEvaluationCurriculumV2.Facts(SHA, 0, 1000, 100, 100, 200, 300, 500, 400, 2, 20, SHA);
		final var higher = new PhantomEvaluationCurriculumV2.Facts(OTHER, 0, 3000, 100, 100, 200, 300, 500, 400, 2, 20, SHA);
		final var repeated = new PhantomEvaluationCurriculumV2.Facts("d".repeat(64), 0, 1000, 100, 100, 200, 300, 500, 400, 2, 20, SHA);
		final var tiers = PhantomEvaluationCurriculumV2.classify(List.of(higher, repeated, lower));
		check(tiers.get(SHA).equals("LOWER_PRECUT_BURDEN") && tiers.get(OTHER).equals("HIGHER_PRECUT_BURDEN") && tiers.get(repeated.key()).equals(tiers.get(SHA)), "PRE_CUT_TIERS_MUST_RETAIN_REAL_DIFFERENCES_AND_TIES");
		check(PhantomEvaluationCurriculumV2.order(List.of(higher, lower, repeated), 0, 183).getFirst().npcHp() == 1000
			&& PhantomEvaluationCurriculumV2.order(List.of(higher, lower, repeated), 1, 183).getFirst().npcHp() == 3000, "FIXED_PRE_CUT_DIVERSITY_QUOTA");
		check(PhantomEvaluationCurriculumV2.classify(List.of(lower, repeated)).values().stream().allMatch("UNCLASSIFIED_SINGLE_BURDEN"::equals), "IDENTICAL_TARGETS_ARE_NOT_DIVERSITY");
	}
	static void reviewRegressions() throws Exception
	{
		final var failures = new java.util.ArrayList<String>();
		final var pair = PhantomEvaluationOutcomeV2.compare(arm("STOCK", "NATIVE_ACTOR_DIED", true, false, true, 200, 300), arm("LEARNED", "NATIVE_KILL", false, true, true, 200, 300));
		if (PhantomEvaluationOutcomeV2.campaign(List.of(pair, pair)).stream().anyMatch(PhantomEvaluationOutcomeV2.Pair::comparable)) { failures.add("CROSS_PAIR_REUSE"); }
		if (PhantomEvaluationCurriculumV2.selectedDiverse(List.of("same-known-vector", "same-known-vector"))) { failures.add("FALSE_DIVERSITY"); }
		final var output = java.nio.file.Files.createTempDirectory("eval012-resume-");
		try { PhantomEvaluationReplayV2.publish(output, List.of(), false); PhantomEvaluationReplayV2.publish(output, List.of(pair), true); }
		catch (Exception conflict) { failures.add("PARTIAL_RESUME_IMMUTABLE_CONFLICT"); }
		final var work = java.nio.file.Files.createTempDirectory("eval012-invalid-").resolve(SHA); java.nio.file.Files.createDirectory(work);
		java.nio.file.Files.writeString(work.resolve("reserved.json"), "{\"episode\":\"" + SHA + "\",\"side\":\"STOCK\",\"split\":\"EVAL\",\"trainingCredit\":false}");
		java.nio.file.Files.writeString(work.resolve("cut.json"), "{}"); java.nio.file.Files.writeString(work.resolve("settlement.json"), "{}");
		java.nio.file.Files.writeString(work.resolve("terminal-boundary-v2.json"), "{}");
		try
		{
			final var invalid = PhantomEvaluationReplayV2.readArm(work, SHA, "SOLO_DAMAGE", 0, 183, pair.stock().authority(), "ORDINARY", false);
			if (invalid.refusal().isEmpty() || !invalid.sourceHashes().containsKey("terminal-boundary-v2.json") || !PhantomEvaluationOutcomeV2.armSummary(invalid).containsKey("refusal")) { failures.add("INVALID_OR_BOUNDARY_TRACEABILITY"); }
			if (!PhantomEvaluationOutcomeV2.compare(invalid, pair.learned()).reason().contains(invalid.refusal())) { failures.add("SPECIFIC_INVALID_REASON_LOST"); }
		} catch (Exception thrown) { failures.add("INVALID_ARM_THROWN_AWAY"); }
		check(failures.isEmpty(), "REVIEW012_REGRESSIONS_" + failures);
	}
	static void authenticReaderMutations() throws Exception
	{
		final var module = java.nio.file.Path.of(System.getProperty("phantom.module.root", "L2J_Mobius_CT_2.6_HighFive")).toAbsolutePath();
		final var history = module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-011-LAWFUL-ENCOUNTER-ENGINE/evidence/continuation");
		final var witness = PhantomEvaluationReplayV2.map(PhantomEvaluationReplayV2.list(PhantomEvaluationReplayV2.parse(PhantomEvaluationReplayV2.text(history.resolve("native-paid-witnesses.json")))).getFirst());
		final String id = PhantomEvaluationReplayV2.string(witness, "arm");
		final var rawArm = PhantomEvaluationReplayV2.list(PhantomEvaluationReplayV2.object(history.resolve("native-evaluation.json")).get("arms")).stream().map(PhantomEvaluationReplayV2::map).filter(a -> a.get("arm").equals(id)).findFirst().orElseThrow();
		final var episode = PhantomEvaluationReplayV2.episode(PhantomEvaluationReplayV2.map(rawArm.get("settlement")), PhantomEvaluationReplayV2.number(PhantomEvaluationReplayV2.map(rawArm.get("cut")), "cutNanos"));
		final String[] lines = PhantomEvaluationReplayV2.string(rawArm, "evaluationArmTsv").split("\n"); final var actors = java.util.Arrays.stream(lines[1].split(",")).map(PhantomEvaluationReplayV2::identity).toList();
		final var target = PhantomEvaluationReplayV2.identity(lines[0].split("\t")[3]); final var nativeRaw = PhantomEvaluationReplayV2.map(witness.get("witness")); final String record = PhantomEvaluationReplayV2.string(nativeRaw, "nativeRecord");
		final String key = PhantomEvaluationReplayV2.single("^TacticalReceiptV3\\[eventKey=([a-f0-9]{64}),", record);
		final var times = PhantomEvaluationReplayV2.list(PhantomEvaluationReplayV2.parse(PhantomEvaluationReplayV2.text(history.resolve("native-timing.json")))).stream().map(PhantomEvaluationReplayV2::map).filter(a -> a.get("arm").equals(id)).findFirst().orElseThrow();
		final var time = PhantomEvaluationReplayV2.list(times.get("timings")).stream().map(PhantomEvaluationReplayV2::map).filter(t -> t.get("eventKey").equals(key)).findFirst().orElseThrow();
		final var work = java.nio.file.Files.createTempDirectory("eval012-native-reader-"); final var file = work.resolve(key + ".native.json");
		java.nio.file.Files.writeString(work.resolve("native-admission.json"), PhantomEvaluationReplayV2.string(rawArm, "nativeAdmissionRaw"));
		java.nio.file.Files.writeString(file, PhantomEvaluationOutcomeV2.json(nativeRaw)); final var authority = new PhantomEvaluationOutcomeV2.Authority(SHA, SHA, SHA, 14, SHA, SHA);
		java.nio.file.Files.writeString(work.resolve(key + ".native-authority-v2.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_NATIVE_AUTHORITY_V2", "eventKey", key, "ownerGeneration", 13,
			"operationKey", "Combat/5763/13/1", "decisionId", 4, "actorObjectId", 268513034, "actorEpoch", 1487652022622100L, "nativeRecordSha", PhantomEvaluationOutcomeV2.hash(java.nio.file.Files.readAllBytes(file)), "trainingCredit", false)));
		check(PhantomEvaluationReplayV2.paid(file, time, episode, actors, target, authority).hp() > 0, "ORIGINAL_NATIVE_READER_REFUSED");
		final var failures = new java.util.ArrayList<String>();
		for (String mutation : List.of("DECISION", "OWNER"))
		{
			final var changed = new java.util.LinkedHashMap<>(nativeRaw); changed.put("nativeRecord", mutation.equals("DECISION") ? record.replaceAll("generation=[0-9]+, targetObjectId=", "generation=999999, targetObjectId=") : record.replaceAll("owner=SourceOwner\\[generation=[0-9]+, operationKey=[^]]+\\]", "owner=SourceOwner[generation=1, operationKey=CONTRACT_FIXTURE]"));
			java.nio.file.Files.writeString(file, PhantomEvaluationOutcomeV2.json(changed));
			try { PhantomEvaluationReplayV2.paid(file, time, episode, actors, target, authority); failures.add(mutation); } catch (IllegalArgumentException expected) { }
		}
		check(failures.isEmpty(), "REVIEW012_RAW_READER_ACCEPTED_" + failures);
	}
	public static void main(String[] args) throws Exception
	{
		if (args.length > 0 && args[0].equals("review")) { reviewRegressions(); return; }
		if (args.length > 0 && args[0].equals("reader")) { authenticReaderMutations(); return; }
		outcomes(); invalidEvidence(); codecAndStatistics(); curriculum();
		reviewRegressions(); authenticReaderMutations();
		System.out.println("EVAL012_OUTCOMES_NEGATIVE_CODEC_STATISTICS_GREEN");
	}
}

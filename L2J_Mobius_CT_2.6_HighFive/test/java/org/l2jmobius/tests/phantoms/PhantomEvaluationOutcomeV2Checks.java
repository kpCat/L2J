/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
	static void resourceSelection()
	{
		final String wait = "WAIT_GROUP_MP:target=12:actor=13:actionMPRequired=2484.0:MP=2739.0";
		final var refusal = Map.<String, Object>of("admitted", false, "wait", wait, "reasons", "[" + wait + "]");
		final String reason = PhantomAutonomousTrainingNative.preCutSelectionRefusal(refusal, true);
		check(reason.equals("AUTONOMOUS_PRE_CUT_PARTY_MP_REFUSED") && PhantomAutonomousTrainingNative.retryableSelectionRefusal(reason, true), "PURE_PRE_CUT_MP_REFUSAL_MUST_EXCLUDE_CANDIDATE_WITHIN_BOUNDED_SELECTION");
		check(PhantomAutonomousTrainingNative.preCutSelectionRefusal(refusal, false).equals("AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED") && !PhantomAutonomousTrainingNative.retryableSelectionRefusal(reason, false), "V1_REFUSAL_CONTRACT_UNCHANGED");
		check(PhantomAutonomousTrainingNative.preCutSelectionRefusal(Map.of("admitted", false, "wait", wait, "reasons", "[" + wait + ", OWNER]"), true).equals("AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED"), "MIXED_OWNER_REFUSAL_MUST_STAY_FATAL");
	}
	static void nativeFirstCurriculum()
	{
		final var low = new PhantomEvaluationCurriculumV2.Facts(SHA, 0, 1000, 100, 100, 200, 300, 500, 400, 2, 20, SHA);
		final var middle = new PhantomEvaluationCurriculumV2.Facts("e".repeat(64), 0, 2000, 100, 100, 200, 300, 500, 400, 2, 20, SHA);
		final var rejectedHigh = new PhantomEvaluationCurriculumV2.Facts(OTHER, 0, 3000, 100, 100, 200, 300, 500, 400, 2, 20, SHA);
		final var directions = Map.of(low.key(), "PARTY_HEAL", middle.key(), "PARTY_HEAL", rejectedHigh.key(), "PARTY_HEAL");
		final var selected = PhantomEvaluationCurriculumV2.selectAdmitted(List.of(rejectedHigh, middle, low), Set.of(low.key(), middle.key()), directions, 183);
		final var failures = new java.util.ArrayList<String>();
		if (!"LOWER_PRECUT_BURDEN".equals(selected.get(low.key())) || !"HIGHER_PRECUT_BURDEN".equals(selected.get(middle.key())) || selected.containsKey(rejectedHigh.key()))
		{ failures.add("REJECTED_THEORETICAL_HIGHER_MUST_NOT_REMOVE_ADMITTED_HIGHER"); }
		final String raw = PhantomEvaluationCurriculumV2.proposals(List.of(low, middle, rejectedHigh), 183);
		if (raw.contains("LOWER_PRECUT_BURDEN") || raw.contains("HIGHER_PRECUT_BURDEN") || !raw.contains("\"fullNativeAdmission\":false"))
		{ failures.add("NO_STRATA_OR_LAWFUL_CLAIM_BEFORE_PAIRED_NATIVE_ADMISSION"); }
		if (!PhantomEvaluationCurriculumV2.selectAdmitted(List.of(low, middle), Set.of(low.key()), Map.of(low.key(), "PARTY_HEAL", middle.key(), "PARTY_HEAL"), 183).isEmpty())
		{ failures.add("ONE_COMPLETE_PAIR_CANNOT_PROVE_DIFFICULTY_COVERAGE"); }
		final var masks = PhantomEvaluationCurriculumV2.rosterMasks(4, 2);
		if (masks.size() != 4 || masks.stream().distinct().count() != masks.size() || masks.stream().anyMatch(m -> (m & 4) == 0 || Integer.bitCount(m) < 3 || (m >>> 4) != 0))
		{ failures.add("REAL_ROSTER_SUBSETS_MUST_KEEP_LEARNER_AND_BOUND"); }
		check(failures.isEmpty(), "NATIVE_FIRST012_" + failures);
		check(selected.equals(PhantomEvaluationCurriculumV2.selectAdmitted(List.of(low, rejectedHigh, middle), Set.of(middle.key(), low.key()), directions, 183)), "NATIVE_FIRST_SELECTION_ORDER_DETERMINISTIC");
	}
	static void nativeFirstReview() throws Exception
	{
		final var failures = new java.util.ArrayList<String>();
		try { failures.addAll(PhantomAutonomousTrainingNative.pairedArtifactRegressions()); }
		catch (IllegalStateException expected) { failures.add("CANONICAL_HEADLESS_NON_SEALABLE_PROOF_REFUSED"); }
		final String riskWait = "WAIT_TANK_AGGRO:target=12:actor=13:incoming=12305.0:HP=6492.0";
		final var riskRefusal = Map.<String, Object>of("admitted", false, "wait", riskWait, "reasons", "[" + riskWait + "]");
		final String riskReason = PhantomAutonomousTrainingNative.preCutSelectionRefusal(riskRefusal, true);
		if (!riskReason.equals("AUTONOMOUS_PRE_CUT_PARTY_RISK_REFUSED") || !PhantomAutonomousTrainingNative.retryableSelectionRefusal(riskReason, true)) { failures.add("PURE_NATIVE_RISK_REFUSAL_IS_INELIGIBLE_CANDIDATE"); }
		if (PhantomAutonomousTrainingNative.remainingProbeBudget(4) != 8 || PhantomAutonomousTrainingNative.remainingProbeBudget(12) != 0) { failures.add("PRIOR_HEADLESS_PROBES_MUST_NOT_RESET_CAP12"); }
		try
		{
			final var oneSkill = new PhantomEvaluationCurriculumV2.Facts(SHA, 0, 1000, 100, 100, 200, 300, 500, 400, 1, 20, SHA);
			final String raw = PhantomEvaluationCurriculumV2.proposals(List.of(oneSkill), 183);
			if (raw.contains("LOWER_PRECUT_BURDEN") || raw.contains("HIGHER_PRECUT_BURDEN")) { failures.add("ONE_SKILL_PROPOSAL_CLAIMED_STRATA"); }
		}
		catch (IllegalArgumentException expected) { failures.add("ONE_HEAL_SKILL_REFUSED_BEFORE_NATIVE_OFFERS"); }
		final String first = PhantomAutonomousTrainingNative.comparisonCellKey(0, SHA, "HEAL", true, OTHER, List.of(SHA, OTHER), true);
		if (!first.equals(PhantomAutonomousTrainingNative.comparisonCellKey(9, SHA, "HEAL", true, OTHER, List.of(SHA, OTHER), true))) { failures.add("SAME_PHYSICAL_CANDIDATE_CONSUMES_BUDGET_TWICE"); }
		check(failures.isEmpty(), "REVIEW012_R1_" + failures);
		check(!first.equals(PhantomAutonomousTrainingNative.comparisonCellKey(0, SHA, "HEAL", true, OTHER, List.of(OTHER, SHA), true)), "ROSTER_LEADER_IS_PHYSICAL_IDENTITY");
		check(!PhantomAutonomousTrainingNative.comparisonCellKey(0, SHA, "HEAL", true, OTHER, List.of(SHA, OTHER), false).equals(PhantomAutonomousTrainingNative.comparisonCellKey(9, SHA, "HEAL", true, OTHER, List.of(SHA, OTHER), false)), "V1_CELL_IDENTITY_UNCHANGED");
	}
	static void nativeProbeFairness()
	{
		final var proposals = List.of("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7"); final var rostersSeen = new java.util.HashSet<Integer>(); int probes = 0;
		outer: for (int roster = 0; roster < 6; roster++)
		{
			for (String ignored : PhantomAutonomousTrainingNative.nativeProbePage(proposals, true))
			{ if (probes == 12) { break outer; } probes++; rostersSeen.add(roster); }
		}
		check(probes == 12 && rostersSeen.size() == 6, "ONE_INELIGIBLE_ROSTER_MUST_NOT_CONSUME_SHARED_CAP12");
		check(PhantomAutonomousTrainingNative.nativeProbePage(proposals, true).equals(proposals.subList(0, 2)), "NATIVE_PROBE_PAGE_KEEPS_SEALED_SEED_ORDER");
		check(PhantomAutonomousTrainingNative.nativeProbePage(proposals, false).equals(proposals), "V1_PROBE_PAGE_UNCHANGED");
	}
	public static void main(String[] args) throws Exception
	{
		if (args.length > 0 && args[0].equals("history013")) { historicalPhysicalFence013(); return; }
		if (args.length > 0 && args[0].equals("review")) { reviewRegressions(); return; }
		if (args.length > 0 && args[0].equals("reader")) { authenticReaderMutations(); return; }
		if (args.length > 0 && args[0].equals("resource")) { resourceSelection(); return; }
		if (args.length > 0 && args[0].equals("native-first")) { nativeFirstCurriculum(); return; }
		if (args.length > 0 && args[0].equals("r1-review")) { nativeFirstReview(); return; }
		if (args.length > 0 && args[0].equals("probe-fairness")) { nativeProbeFairness(); return; }
		outcomes(); invalidEvidence(); codecAndStatistics(); curriculum();
		reviewRegressions(); authenticReaderMutations();
		resourceSelection();
		nativeFirstCurriculum();
		nativeFirstReview();
		nativeProbeFairness();
		System.out.println("EVAL012_OUTCOMES_NEGATIVE_CODEC_STATISTICS_GREEN");
	}
	static void historicalPhysicalFence013() throws Exception
	{
		check(PhantomAutonomousTrainingNative.safeReason(new IllegalStateException("TRAIN013_INSUFFICIENT_LEGAL_COHORT")).equals("TRAIN013_INSUFFICIENT_LEGAL_COHORT"), "TRAIN013_NO_DATA_REASON_MUST_NOT_BECOME_TECHNICAL_ERROR");
		check(PhantomAutonomousTrainingNative.safeReason(new IllegalStateException("vendor password=private")).equals("IllegalStateException"), "TRAIN013_VENDOR_DIAGNOSTIC_MUST_REMAIN_PRIVATE");
		check(PhantomAutonomousTrainingNative.retryableAdmission013("AUTONOMOUS_PRE_CUT_SUPPORTED_STOCK_HEAL_OFFERS", true, false), "TRAIN013_UNAVAILABLE_HEAL_MUST_EXCLUDE_SCENE_BEFORE_CUT");
		check(!PhantomAutonomousTrainingNative.retryableAdmission013("AUTONOMOUS_PRE_CUT_SUPPORTED_STOCK_HEAL_OFFERS", false, true), "TRAIN013_LEGACY_EVAL_REFUSAL_UNCHANGED");
		check(!PhantomAutonomousTrainingNative.retryableAdmission013("AUTONOMOUS_PRE_CUT_NATIVE_OWNER", true, false), "TRAIN013_OWNER_FAILURE_MUST_REMAIN_FATAL");
		final var columns = List.of("charId", "char_name", "account_name", "level"); final var types = List.of(4, 12, 12, 4);
		final var historic = new PhantomTrainingActors.SavedActor(100, List.of(new PhantomTrainingActors.SavedTable("characters", "charId", columns, types, List.of(List.of("100", "old", "fixture", "76")))));
		final var other = new PhantomTrainingActors.SavedActor(200, List.of(new PhantomTrainingActors.SavedTable("characters", "charId", columns, types, List.of(List.of("200", "new", "fixture", "76")))));
		check(historic.physicalFingerprint().equals(other.physicalFingerprint()) && !historic.sha().equals(other.sha()), "HISTORY013_FIXTURE_REQUIRES_DIFFERENT_ID_SAME_PHYSICAL");
		final var root = java.nio.file.Files.createTempDirectory("history013-"); final var run = root.resolve("autonomous/v1/campaigns/history/evaluations/eval"); java.nio.file.Files.createDirectories(run);
		java.nio.file.Files.writeString(run.resolve(historic.sha() + ".source.json"), historic.json(), java.nio.charset.StandardCharsets.UTF_8);
		final var fields = new java.util.ArrayList<>(java.util.Collections.nCopies(16, "0")); fields.set(1, historic.sha());
		java.nio.file.Files.writeString(run.resolve("cells.tsv"), "header\n" + String.join("\t", fields) + "\n", java.nio.charset.StandardCharsets.UTF_8);
		final var fence = PhantomAutonomousTrainingNative.historicalFence013(root, List.of(other));
		check(fence.physical().contains(other.physicalFingerprint()), "HISTORY013_OTHER_ACTOR_WITH_HISTORICAL_PHYSICAL_MUST_BE_EXCLUDED_WITHOUT_HISTORICAL_ACTOR_IN_CURRENT_PAGE");
		System.out.println("TRAIN013_HISTORICAL_PHYSICAL_FENCE_GREEN");
	}
}

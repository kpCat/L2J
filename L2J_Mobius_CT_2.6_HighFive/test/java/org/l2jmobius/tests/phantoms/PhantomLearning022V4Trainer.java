/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.*;
import static org.l2jmobius.tests.phantoms.PhantomLearning019ActionValue.check;

/** TEST command boundary; source authentication stays in the canonical native adapter. */
public final class PhantomLearning022V4Trainer
{
	static final String ACCEPTED = "90cdb499d44edde5f53819b357f1383dc65f9b9de1bd4d43120f6a154d1a55a6";
	static String json(Object value) { return PhantomEvaluationOutcomeV2.jsonValue(value); }
	static Map<String, Object> curriculum(Map<String, Object> original, long round)
	{
		check(round >= 0, "022_NONNEGATIVE_DURABLE_CURSOR");
		var candidate = new LinkedHashMap<>(original);
		String[] cell = PhantomEvaluationReplayV2.string(candidate, "cell").split("\t", -1);
		var source = PhantomAutonomousTrainingNative.soloSource020(PhantomEvaluationReplayV2.string(candidate, "cell"), PhantomEvaluationReplayV2.string(candidate, "sourceImage"));
		var acquired = source.learned().entrySet().stream().map(e -> new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(e.getKey(), e.getValue())).toList();
		var pool = new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode.RANGED_MAGIC, PhantomLearning020V4Native.refs(cell));
		check(source.field("classid").equals(source.field("base_class")), "022_MAIN_CLASS_SOURCE_REQUIRED");
		var actor = new AutonomousTrainingV1.ActorSeed(source.sha(), Integer.parseInt(source.field("classid")), Integer.parseInt(source.field("level")), 0, acquired, List.of(pool));
		String key = "shortlist/" + source.sha() + "/" + pool.key();
		var proposal = AutonomousTrainingV1.nextCandidate(List.of(actor), Map.of(), Map.of(key, round), round, 22001).orElseThrow();
		cell[4] = String.join(",", proposal.shortlist().candidates().stream().map(s -> s.skillId() + ":" + s.skillLevel()).toList());
		cell[5] = Long.toString(proposal.shortlist().nextCursor()); candidate.put("cell", String.join("\t", cell));
		candidate.put("cursor022", round); candidate.put("nextCursor022", proposal.shortlist().nextCursor());
		String split = PhantomLearning020V4Native.SPLIT022;
		long step = Long.getLong("phantom.learning022.step", round);
		candidate.put("policy022", !split.equals("TRAIN") ? "BASELINE_V1" : switch ((int)(step % 4))
		{ case 0 -> "COVERAGE_TEST_V1"; case 1 -> "BASELINE_V1"; case 2 -> "MODEL_V1"; default -> "EPSILON_V1"; });
		return candidate;
	}
	static Map<String, Object> summary(ActionContinuationV4.State state)
	{
		return Map.of("head", state.head(), "modelSha", state.model().sha256(), "globalUpdates", state.globalUpdates(),
			"localUpdates", state.model().updates(), "segment", state.segment(), "sequence", state.sequence(), "globalKeys", state.events().size());
	}
	public static void main(String[] args) throws Exception
	{
		check(args.length >= 2, "022_COMMAND_ROOT_REQUIRED"); Path root = Path.of(args[1]);
		switch (args[0])
		{
			case "offline" -> {
				check(args.length == 3, "022_OFFLINE_ARGUMENTS");
				var parent = ActionLearningV4.load(Path.of(args[2]), ACCEPTED);
				org.l2jmobius.gameserver.phantoms.learning.ActionContinuation022Checks.run(parent, PhantomLearning019ActionValue.source(root), root);
				var original = PhantomLearning020V4Native.candidates(root).getFirst();
				var first = curriculum(original, 0); var second = curriculum(original, 1);
				check(!first.get("policy022").equals(second.get("policy022")) && !first.get("cursor022").equals(second.get("cursor022"))
					&& curriculum(original, 0).equals(first), "022_DETERMINISTIC_CURRICULUM");
				check(curriculum(original, 2).get("policy022").equals("MODEL_V1") && curriculum(original, 3).get("policy022").equals("EPSILON_V1"), "022_MODEL_AND_EPSILON_ROUTES_REQUIRED");
				new ActionExplorationV4.Plan(1, "BASELINE_V1", 22001, 1, .15, "AUDIT", "a".repeat(64), "b".repeat(64), ACCEPTED, List.of("1230:28", "1275:14"));
				PhantomLearning019ActionValue.rejects(() -> new ActionExplorationV4.Plan(1, "COVERAGE_TEST_V1", 22001, 1, .15, "AUDIT", "a".repeat(64), "b".repeat(64), ACCEPTED, List.of("1230:28", "1275:14")));
				System.out.println("LEARNING022_CURRICULUM_HELDOUT_PASS GS=0 SQL=0");
			}
			case "initialize" -> {
				check(args.length == 3, "022_PARENT_REQUIRED"); var parent = ActionLearningV4.load(Path.of(args[2]), ACCEPTED);
				check(parent.updates() == 188, "022_PARENT188_REQUIRED"); System.out.println(json(summary(ActionContinuationV4.initialize(root, parent))));
			}
			case "head" -> System.out.println(json(summary(ActionContinuationV4.load(root))));
			case "cold" -> { check(args.length == 3, "022_COLD_ARGUMENTS"); ActionContinuationV4.cold(root, args[2]); }
			case "commit" -> { check(args.length == 4, "022_COMMIT_ARGUMENTS"); System.out.println(json(summary(ActionContinuationV4.commit(root, args[2], args[3])))); }
			case "prepare" -> {
				check(args.length == 4, "022_PREPARE_ARGUMENTS"); Path round = Path.of(args[3]);
				var complete = PhantomEvaluationReplayV2.object(round.resolve("native021.complete.json"));
				String proofSha = PhantomLearning020V4Native.sha(round.resolve("native020.json"));
				check(PhantomEvaluationReplayV2.string(complete, "nativeSha").equals(proofSha)
					&& PhantomEvaluationReplayV2.string(complete, "manifestSha").equals(PhantomLearning020V4Native.sha(round.resolve("manifest021.json"))), "022_NATIVE_COMPLETE_REQUIRED");
				var before = ActionContinuationV4.load(root);
				check(before.head().equals(args[2]) && before.model().sha256().equals(PhantomLearning020V4Native.PARENT), "022_EXPECTED_PARENT");
				var samples = PhantomLearning020V4Native.samples(round);
				System.out.println(json(summary(ActionContinuationV4.prepare(root, args[2], samples, proofSha))));
			}
			case "verify-prepared" -> {
				check(args.length == 5, "022_VERIFY_ARGUMENTS"); Path round = Path.of(args[3]);
				var complete = PhantomEvaluationReplayV2.object(round.resolve("native021.complete.json"));
				String nativeSha = PhantomLearning020V4Native.sha(round.resolve("native020.json"));
				check(PhantomEvaluationReplayV2.string(complete, "nativeSha").equals(nativeSha)
					&& PhantomEvaluationReplayV2.string(complete, "manifestSha").equals(PhantomLearning020V4Native.sha(round.resolve("manifest021.json"))), "022_COMPLETE_SOURCE_CHANGED");
				ActionContinuationV4.verifyPrepared(root, args[2], args[4], PhantomLearning020V4Native.samples(round, true), nativeSha);
			}
			case "replay" -> {
				check(args.length == 3, "022_REPLAY_ARGUMENTS"); Path round = Path.of(args[2]);
				var before = ActionContinuationV4.load(root); var samples = PhantomLearning020V4Native.samples(round, true);
				check(ActionLearningV4.fit(before.model(), samples).sha256().equals(before.model().sha256()), "022_LOCAL_REPLAY_CREDIT");
				var after = ActionContinuationV4.prepare(root, before.head(), samples, PhantomLearning020V4Native.sha(round.resolve("native020.json")));
				check(after.head().equals(before.head()) && after.globalUpdates() == before.globalUpdates(), "022_GLOBAL_REPLAY_CREDIT");
				System.out.println(json(Map.of("checkpointSha", after.model().sha256(), "globalUpdates", after.globalUpdates(), "receipts", samples.size(), "replayAdditionalUpdates", 0, "localReplayAdditionalUpdates", 0)));
			}
			case "fault-prepare" -> {
				check(args.length == 4 && root.getFileName().toString().startsWith("rollover022-"), "022_OFFLINE_FAULT_FIXTURE_ONLY");
				var prepared = ActionContinuationV4.prepare(root, args[2], List.of(PhantomLearning019ActionValue.source(Path.of(args[3])).getFirst()), "d".repeat(64));
				ActionExperimentReceiptV4.publish(root.resolve("fault-ready022.txt"), prepared.head().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
				Thread.sleep(60_000);
			}
			case "fault-paid" -> {
				check(args.length == 3 && root.getFileName().toString().startsWith("partial-paid022-"), "022_PAID_FAULT_FIXTURE_ONLY");
				var original = PhantomLearning019ActionValue.source(Path.of(args[2])).getFirst();
				ActionExperimentReceiptV4.publish(root.resolve("original-paid.row.bin"), TacticalStoreV3.encode(original.row()));
				ActionExperimentReceiptV4.publish(root.resolve("paid-ready022.txt"), original.row().eventKey().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
				Thread.sleep(60_000);
			}
			default -> throw new IllegalArgumentException("022_UNKNOWN_COMMAND");
		}
	}
}

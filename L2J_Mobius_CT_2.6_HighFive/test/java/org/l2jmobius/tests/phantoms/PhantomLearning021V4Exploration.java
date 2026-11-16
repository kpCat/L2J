/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.*;
import static org.l2jmobius.tests.phantoms.PhantomLearning019ActionValue.*;

/** Bounded contracts over genuine archived actions. No new training credit, SQL or GS. */
public final class PhantomLearning021V4Exploration
{
	public static final String PARENT = "42cfff5c02fd386a31ab1e95d4234a8eb22549a62debd729a3fc9a7a602ef96c";
	public static void main(String[] args) throws Exception
	{
		if (args.length == 1 && args[0].equals("continuation-contract")) { continuationContract(); return; }
		if (args.length == 2 && args[0].equals("diagnose")) { diagnose(Path.of(args[1])); return; }
		if (args.length == 2 && args[0].equals("post")) { post(Path.of(args[1])); return; }
		var parent = ActionLearningV4.load(Path.of(args[1]), PARENT);
		check(parent.updates() == 179, "PARENT179_COLD");
		check(ActionLearningV4.fit(parent, PhantomLearning020V4Native.samples(Path.of(args[1]).getParent())).sha256().equals(PARENT), "PARENT179_HISTORICAL_REPLAY_ZERO");
		var sample = source(Path.of(args[0])).stream().filter(s -> s.row().receipt().attempt().selected().family() == LegalActionV3.Family.DAMAGE).findFirst().orElseThrow();
		var a = sample.row().receipt().attempt();
		var plan = new ActionExplorationV4.Plan(1, "COVERAGE_TEST_V1", 21001, 1, .15, "TRAIN", sample.episodeId(), "a".repeat(64), PARENT,
			a.offered().stream().map(v -> v.skillId() + ":" + v.skillLevel()).sorted().toList());
		var chooser = new ActionExplorationV4(plan);
		long deadline = System.nanoTime() + 100_000_000L;
		var d = chooser.choose(a.observation(), a.offered(), a.baseline(), true, deadline);
		check(d.mode().equals("EXPLORE_COVERAGE") && !d.selected().equals(a.baseline()) && d.propensity() == 1, "COVERAGE_REAL_ALTERNATIVE");
		var second = chooser.choose(a.observation(), a.offered(), a.baseline(), true, deadline);
		check(second.mode().equals("BASELINE") && second.selected().equals(a.baseline()) && second.propensity() == 1, "COVERAGE_CAP");
		for (int k : new int[]{2, 3})
		{
			var stock = ActionExplorationV4.pick(k, 0, .15, .2);
			var alt = ActionExplorationV4.pick(k, 0, .15, .99);
			check(stock.index() == 0 && stock.propensity() == .85 && Math.abs(alt.propensity() - (k == 2 ? .15 : .075)) < 1e-12, "EPSILON_PROBABILITY");
			check(Math.abs(stock.propensity() + (k - 1) * alt.propensity() - 1) < 1e-12, "EPSILON_SUM");
		}
		var epsilonPlan = new ActionExplorationV4.Plan(1, "EPSILON_V1", 21001, 1, .15, "TRAIN", sample.episodeId(), "a".repeat(64), PARENT, plan.skills());
		var epsilon = new ActionExplorationV4(epsilonPlan).choose(a.observation(), a.offered(), a.baseline(), true, Long.MAX_VALUE);
		check(epsilon.mode().equals("EXPLORE_EPSILON") && ActionExplorationV4.Decision.restore(epsilon.bytes()).equals(epsilon), "EPSILON_FROZEN_DRAW_REPLAY");
		for (boolean safe : new boolean[]{false, true})
		{
			var denied = new ActionExplorationV4(plan).choose(a.observation(), a.offered(), a.baseline(), safe, safe ? 1 : deadline);
			check(denied.mode().equals("BASELINE") && denied.propensity() == 1 && denied.selected().equals(a.baseline()), "SAFETY_OR_DEADLINE");
		}
		rejects(() -> new ActionExplorationV4.Plan(2, "COVERAGE_TEST_V1", 1, 1, .15, "TRAIN", sample.episodeId(), "a".repeat(64), PARENT, plan.skills()));
		rejects(() -> new ActionExplorationV4.Plan(1, "EPSILON_V1", 1, 1, .15, "AUDIT", sample.episodeId(), "a".repeat(64), PARENT, plan.skills()));
		rejects(() -> new ActionExplorationV4.Plan(1, "EPSILON_V1", 1, 1, .25, "TRAIN", sample.episodeId(), "a".repeat(64), PARENT, plan.skills()));
		var bytes = d.bytes(); check(ActionExplorationV4.Decision.restore(bytes).equals(d), "DECISION_CODEC");
		bytes[0] ^= 1; rejects(() -> ActionExplorationV4.Decision.restore(bytes));
		var foreign = a.offered().stream().map(v -> v.withFamily(LegalActionV3.Family.HEAL)).toList();
		rejects(() -> new ActionExplorationV4(plan).choose(a.observation(), foreign, foreign.getFirst(), true, Long.MAX_VALUE));
		// NOT_NATIVE metadata fixture around an archived row: never used for fitting or experiment credit.
		String fixtureMode = a.selected().equals(a.baseline()) ? "BASELINE" : "MODEL";
		var fixturePlan = new ActionExplorationV4.Plan(1, "MODEL_V1", 21001, 1, .15, "TRAIN", sample.episodeId(), "a".repeat(64), PARENT, plan.skills());
		var fixture = new ActionExplorationV4.Decision(fixturePlan, 0, a.observation(), a.offered(), a.baseline(), a.selected(), fixtureMode, 1, 0, a.observation().capturedNanos(), Long.MAX_VALUE, "NOT_NATIVE_CONTRACT");
		byte[] rowBytes = TacticalStoreV3.encode(sample.row());
		var intent = new ActionExperimentReceiptV4.Intent(fixture, sample.row().receipt().owner(), fixture.capturedNanos());
		var effect = ActionExperimentReceiptV4.bind(intent, fixturePlan.sha256(), rowBytes, sample.episode(), sample.sourceSha());
		check(effect.paidHp() == sample.row().receipt().actualHp() && effect.paidMp() == sample.row().receipt().actualMp() && effect.elapsedNanos() == sample.row().receipt().elapsedNanos(), "EXACT_NATIVE_METRICS");
		// Live snapshots omit unknown facts; the historical codec explicitly restores them.
		var sparseEntities = a.observation().entities().stream().map(e -> new TacticalObservationV3.Entity(e.role(), e.objectId(), e.epoch(), e.ownerObjectId(), e.classId(),
			e.facts().entrySet().stream().filter(v -> v.getValue().known()).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)), e.gear(), e.effects(), e.abilities(), e.pastActions())).toList();
		var old = a.observation(); var sparse = new TacticalObservationV3(old.decisionId(), old.capturedNanos(), old.profileId(), old.actorObjectId(), old.actorEpoch(), old.partyIdentity(), sparseEntities);
		check(!sparse.equals(old) && Arrays.equals(sparse.bytes(), old.bytes()), "CANONICAL_UNKNOWN_FACTS_FIXTURE");
		var sparseDecision = new ActionExplorationV4.Decision(fixturePlan, 0, sparse, a.offered(), a.baseline(), a.selected(), fixtureMode, 1, 0, old.capturedNanos(), Long.MAX_VALUE, "NOT_NATIVE_SPARSE_CONTRACT");
		ActionExperimentReceiptV4.bind(new ActionExperimentReceiptV4.Intent(sparseDecision, intent.owner(), intent.capturedNanos()), fixturePlan.sha256(), rowBytes, sample.episode(), sample.sourceSha());
		rejects(() -> ActionExperimentReceiptV4.bind(intent, "0".repeat(64), rowBytes, sample.episode(), sample.sourceSha()));
		var foreignOwner = new ActionExperimentReceiptV4.Intent(fixture, new HealEvidenceV3.SourceOwner(1, "FOREIGN"), fixture.capturedNanos());
		rejects(() -> ActionExperimentReceiptV4.bind(foreignOwner, fixturePlan.sha256(), rowBytes, sample.episode(), sample.sourceSha()));
		check(ActionExperimentReceiptV4.Intent.restore(intent.bytes()).equals(intent), "INTENT_EXACT_CODEC");
		rejects(() -> ActionExplorationV4.validateLog(List.of(second)));
		rejects(() -> ActionExplorationV4.validateLog(List.of(d, d)));
		var summary = new LinkedHashMap<String, Object>(Map.of("mode", fixture.mode(), "propensity", 1d, "decisionSha", fixture.sha256(), "stock", fixture.stock().skillId(), "chosen", fixture.selected().skillId()));
		ActionExperimentReceiptV4.validateSummary(summary, fixture);
		summary.put("propensity", .15); rejects(() -> ActionExperimentReceiptV4.validateSummary(summary, fixture));
		rejects(() -> new ActionExplorationV4.Decision(plan, d.cursor(), d.observation(), d.offered(), d.stock(), d.selected(), d.mode(), .15, d.draw(), d.capturedNanos(), d.deadline(), d.reason()));
		var off = TacticalPolicyV3.actionValueChoice(TacticalPolicyV3.Mode.OFF, parent, a.observation(), a.offered(), a.baseline(), false, Long.MAX_VALUE);
		check(off.selected().equals(a.baseline()) && !off.consulted(), "LEGACY_OFF_STOCK");
		System.setProperty("phantom.learning.train", "true");
		var tactical = new TacticalPolicyV3(TacticalPolicyV3.Mode.SHADOW, 0, null, 20, 16, 1000, _ -> { });
		tactical.actionValueTest(TacticalPolicyV3.Mode.ON, parent, _ -> { }); tactical.actionExplorationTest(new ActionExplorationV4(plan));
		rejects(() -> TacticalPolicyV3.OFF.actionExplorationTest(new ActionExplorationV4(plan)));
		System.clearProperty("phantom.learning.train");
		rejects(() -> tactical.actionExplorationTest(new ActionExplorationV4(plan)));
		check(ActionLearningV4.load(Path.of(args[1]), PARENT).sha256().equals(PARENT), "PARENT_UNCHANGED");
		PhantomLearning020V4Native.contracts(Path.of(args[0]));
		System.out.println("LEARNING021_CONTRACT_GREEN parent179=true coverage=1 epsilon=.85/.15/.075 GS=0 SQL=0 trainingCredit=false");
	}
	static void continuationContract() throws Exception
	{
		Path parent = Files.createTempDirectory("learning021-continuation-"), root = Files.createDirectory(parent.resolve("train021-c1"));
		try
		{
			Files.writeString(root.resolve("owner.txt"), "LEARNING002:" + "a".repeat(32));
			PhantomLearning020V4Native.owned(root);
			check(PhantomLearning020V4Native.experimentCount(List.of(Map.of("mode", "EXPLORE_COVERAGE", "stock", 1275, "chosen", 1230))) == 1, "IN_MEMORY_INTEGER_EXPERIMENT_SUMMARY");
			System.out.println("LEARNING021_CONTINUATION_ADMISSION_GREEN GS=0 SQL=0");
		}
		finally { Files.delete(root.resolve("owner.txt")); Files.delete(root); Files.delete(parent); }
	}
	static void diagnose(Path root) throws Exception
	{
		Path rowPath; try (var paths = Files.list(root)) { rowPath = paths.filter(p -> p.getFileName().toString().endsWith(".row.bin")).findFirst().orElseThrow(); }
		var row = TacticalStoreV3.decode(Files.readAllBytes(rowPath));
		var intent = ActionExperimentReceiptV4.Intent.restore(Files.readAllBytes(root.resolve("intent021-0.bin"))); var d = intent.decision(); var a = row.receipt().attempt();
		var cut = PhantomEvaluationReplayV2.object(root.resolve("cut020.json"));
		var episode = PhantomEvaluationReplayV2.episode(PhantomEvaluationReplayV2.object(root.resolve("episode020.json")), PhantomEvaluationReplayV2.number(cut, "startedNanos"));
		System.out.println("DIAG021 " + Map.of("observationEquals", a.observation().equals(d.observation()), "observationBytes", Arrays.equals(a.observation().bytes(), d.observation().bytes()),
			"offered", a.offered().equals(d.offered()), "stock", a.baseline().equals(d.stock()), "selected", a.selected().equals(d.selected()), "owner", row.receipt().owner().equals(intent.owner()),
			"split", row.split().equals(d.plan().split()), "afterCut", d.capturedNanos() >= episode.startedNanos(), "beforeSettle", intent.capturedNanos() <= episode.settledNanos()));
		var alternative = ActionExperimentReceiptV4.Intent.restore(Files.readAllBytes(root.resolve("intent021-1.bin"))).decision();
		System.out.println("SELECTED021 mode=" + alternative.mode() + " stock=" + alternative.stock().skillId() + ":" + alternative.stock().skillLevel()
			+ " selected=" + alternative.selected().skillId() + ":" + alternative.selected().skillLevel() + " propensity=" + alternative.propensity() + " policy=" + alternative.plan().policyId()
			+ " capturedBeforeDeadline=" + (alternative.capturedNanos() < alternative.deadline()) + " paidReceipt=UNPROVEN");
	}
	/** Adversarial copies only: original native root and models remain immutable. No fit occurs. */
	static void post(Path source) throws Exception
	{
		System.setProperty("phantom.learning.explore021", "true");
		if (source.getFileName().toString().equals("train021-c1")) { System.setProperty("phantom.learning.explore021.continuation", "true"); }
		if (!Files.exists(source.resolve("native021.complete.json")))
		{
			rejectSamples(source, "INCOMPLETE_NATIVE_PUBLICATION_ACCEPTED");
			var cut = PhantomEvaluationReplayV2.object(source.resolve("cut020.json"));
			var episode = PhantomEvaluationReplayV2.episode(PhantomEvaluationReplayV2.object(source.resolve("episode020.json")), PhantomEvaluationReplayV2.number(cut, "startedNanos"));
			Path rowPath; try (var paths = Files.list(source)) { rowPath = paths.filter(p -> p.getFileName().toString().endsWith(".row.bin")).findFirst().orElseThrow(); }
			byte[] bytes = Files.readAllBytes(rowPath); var row = TacticalStoreV3.decode(bytes);
			Path raw = source.resolve(row.eventKey() + ".native.json");
			PhantomLearning020V4Native.nativeRecord(row, PhantomEvaluationReplayV2.object(raw));
			var intent = ActionExperimentReceiptV4.Intent.restore(Files.readAllBytes(source.resolve("intent021-0.bin")));
			var effect = ActionExperimentReceiptV4.bind(intent, PhantomEvaluationReplayV2.string(cut, "policySha"), bytes, episode, PhantomLearning020V4Native.sha(raw));
			check(intent.decision().mode().equals("BASELINE") && effect.paidHp() > 0, "PARTIAL_CONTROL_ONLY_NO_EXPERIMENT_CREDIT");
			System.out.println("LEARNING021_PARTIAL_POST_GREEN incompletePublication=reject persistedControlHp=" + effect.paidHp() + " mp=" + effect.paidMp() + " nanos=" + effect.elapsedNanos() + " trainingCredit=false experimentalReceipt=UNPROVEN");
			return;
		}
		var original = PhantomLearning020V4Native.samples(source);
		Path parent = Files.createTempDirectory("learning021-adversarial-"), root = Files.createDirectory(parent.resolve(source.getFileName()));
		try
		{
			var proof = PhantomEvaluationReplayV2.object(source.resolve("native020.json"));
			var files = PhantomEvaluationReplayV2.map(proof.get("files"));
			for (String name : files.keySet()) { Files.copy(source.resolve(name), root.resolve(name)); }
			for (String name : List.of("owner.txt", "native020.json", "native021.complete.json")) { Files.copy(source.resolve(name), root.resolve(name)); }
			check(PhantomLearning020V4Native.samples(root).size() == original.size(), "COMPLETE_NATIVE_COPY");
			String decision = files.keySet().stream().filter(s -> s.startsWith("decision021-")).sorted().reduce((a, b) -> b).orElseThrow();
			byte[] decisionBytes = Files.readAllBytes(root.resolve(decision)); Files.delete(root.resolve(decision));
			rejectSamples(root, "MISSING_TRAILING_DECISION_ACCEPTED"); Files.write(root.resolve(decision), decisionBytes);
			byte[] proofBytes = Files.readAllBytes(root.resolve("native020.json"));
			var records = PhantomEvaluationReplayV2.list(proof.get("records"));
			var first = PhantomEvaluationReplayV2.map(records.getFirst()); first.put("propensity", .123);
			Files.writeString(root.resolve("native020.json"), PhantomLearning020V4Native.json(proof));
			// Re-pin the outer seal to exercise semantic summary validation, not just file SHA.
			var seal = PhantomEvaluationReplayV2.object(root.resolve("native021.complete.json")); seal.put("nativeSha", PhantomLearning020V4Native.sha(root.resolve("native020.json")));
			Files.writeString(root.resolve("native021.complete.json"), PhantomLearning020V4Native.json(seal));
			rejectSamples(root, "SUMMARY_PROPENSITY_TAMPER_ACCEPTED");
			Files.write(root.resolve("native020.json"), proofBytes); Files.copy(source.resolve("native021.complete.json"), root.resolve("native021.complete.json"), StandardCopyOption.REPLACE_EXISTING);
			check(PhantomLearning020V4Native.samples(root).size() == original.size(), "RESTORED_COMPLETE_COPY");
			System.out.println("LEARNING021_POST_NATIVE_GREEN missingDecision=reject summaryTamper=reject originalUnchanged=true trainingCredit=false");
		}
		finally { try (var paths = Files.list(root)) { for (var path : paths.toList()) { Files.delete(path); } } Files.delete(root); Files.delete(parent); }
	}
	static void rejectSamples(Path root, String message) throws Exception
	{
		try { PhantomLearning020V4Native.samples(root); } catch (IllegalArgumentException | AssertionError | java.io.IOException expected) { return; }
		throw new AssertionError(message);
	}
}

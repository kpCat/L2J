/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import org.l2jmobius.tests.phantoms.PhantomLearning008Scenarios;

/** Controlled contracts are not native gameplay evidence. */
public final class Learning008Checks
{
	public static void main(String[] args) throws Exception
	{
		if (args.length == 2 && args[0].equals("offline")) { offline(Path.of(args[1])); return; }
		if (args.length != 1) { throw new IllegalArgumentException("Expected one focused group"); }
		if (args[0].equals("baseline")) { baseline(); return; }
		if (args[0].equals("targets")) { targets(); return; }
		if (args[0].equals("authority")) { authority(); return; }
		if (!args[0].equals("admission")) { throw new IllegalArgumentException("Unknown008 focused group"); }
		var module = Path.of(System.getProperty("phantom.module.root", "L2J_Mobius_CT_2.6_HighFive"));
		var game = module.resolve("dist/game");
		var archmage = PhantomLearning008Scenarios.canonical(game, 94, 77);
		var muse = PhantomLearning008Scenarios.canonical(game, 103, 77);
		var storm = PhantomLearning008Scenarios.canonical(game, 110, 77);
		check(archmage.stream().map(c -> c.skillId() + ":" + c.skillLevel()).toList().equals(List.of("1230:28", "1339:1")), "Archmage canonical DAMAGE admission lost");
		check(muse.stream().anyMatch(c -> c.skillId() == 1235 && c.skillLevel() == 28)
			&& muse.stream().anyMatch(c -> c.skillId() == 1340 && c.skillLevel() == 1), "MysticMuse needs inherited Hydro Blast and learned Ice Vortex");
		check(storm.stream().anyMatch(c -> c.skillId() == 1239 && c.skillLevel() == 28)
			&& storm.stream().anyMatch(c -> c.skillId() == 1341 && c.skillLevel() == 1), "StormScreamer needs inherited Hurricane and learned Wind Vortex");
		check(muse.stream().noneMatch(c -> c.skillId() == 1339) && storm.stream().noneMatch(c -> c.skillId() == 1339), "Archmage skill leaked to another class");
		check(PhantomLearning008Scenarios.canonical(game, 103, 76).stream().noneMatch(c -> c.skillId() == 1340)
			&& PhantomLearning008Scenarios.canonical(game, 110, 76).stream().noneMatch(c -> c.skillId() == 1341), "Unlearned level77 Vortex admitted at76");
		for (var list : List.of(archmage, muse, storm))
		{
			check(list.size() >= 2 && list.size() <= 4, "Bounded legal alternatives missing");
			check(list.stream().allMatch(c -> c.mpCost() > 0 && c.castMillis() > 0 && c.range() == 900 && c.source().startsWith("data/stats/players/skillTrees/")), "Canonical skill source/cost missing");
			check(list.stream().map(PhantomLearning008Scenarios.Capability::castMillis).distinct().count() >= 2, "Real cast-duration diversity missing");
		}
		check(PhantomLearning008Scenarios.canonical(game, 105, 77).isEmpty(), "HEAL class was relabeled as DAMAGE");
		check(PhantomLearning008Scenarios.learned(103, List.of(1235, 1340), id -> id == 1235 || id == 1340)
			&& PhantomLearning008Scenarios.learned(110, List.of(1239, 1341), id -> id == 1239 || id == 1341), "TEST admission still requires Archmage Prominence");
		check(!PhantomLearning008Scenarios.learned(103, List.of(1235, 1340), id -> id == 1230)
			&& !PhantomLearning008Scenarios.learned(103, List.of(1230, 1339), id -> true), "Foreign or missing learned skills admitted");
		check(org.l2jmobius.tests.phantoms.PhantomLearning001ActorFixture.coldOpeningRange(94, id -> id == 1230 || id == 1339 ? 900 : 0) == 900, "Legacy94 cold range changed");
		check(org.l2jmobius.tests.phantoms.PhantomLearning001ActorFixture.coldOpeningRange(103, id -> id == 1235 || id == 1340 || id == 1342 ? 900 : 0) == 900, "Cold TEST admission still returns zero range for MysticMuse");
		check(org.l2jmobius.tests.phantoms.PhantomLearning001ActorFixture.coldOpeningRange(110, id -> id == 1239 || id == 1341 || id == 1343 ? 900 : 0) == 900, "Cold TEST admission still returns zero range for StormScreamer");
		check(org.l2jmobius.tests.phantoms.PhantomLearning001ActorFixture.coldOpeningRange(103, id -> id == 1230 || id == 1339 ? 900 : 0) == 0, "Foreign Fire skills set MysticMuse cold range");
		System.out.println("LEARNING008_ADMISSION canonical94=2 canonical103=" + muse.size() + " canonical110=" + storm.size() + " inheritedRanks=EXACT foreignSkills=REFUSED nativeReadiness=NOT_RUN");
	}
	private static void offline(Path root) throws Exception
	{
		var store = new TacticalStoreV3(root); store.requireStopped(); var parent = store.optimizationParent(); var model = store.candidate(parent.modelId()); var teacher = store.candidate(11);
		check(teacher.sha().equals(ActionAdvantage008.TEACHER_SHA), "Historical DRAFT11 changed");
		check(store.accepted(2, "6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc") != null, "Accepted2 changed");
		var historical = store.advantageSnapshot(11);
		check(historical.datasetSha().equals("d021f8c6fe2f352d2895a406436c849f94dcfeea57ed539e2cccd241e0132d84") && historical.samples().size() == 58 && historical.weightedExamples().size() == 19, "Historical007 targets or atomic markers changed");
		try (var connection = new TrainingStore(root).openTacticalConnection(); var query = connection.prepareStatement("SELECT optimizer_sha,optimizer_blob FROM phantom_tactical_v3_optimizers WHERE model_id=11"))
		{
			try (var rows = query.executeQuery()) { check(rows.next() && rows.getString(1).equals(ActionAdvantage008.PARENT_ADAM_SHA), "Historical Adam11 SHA changed"); var adam = TacticalModelV3.Optimizer.restore(rows.getBytes(2), rows.getString(1)); check(adam.updates() == 855 && adam.completedEpochs() == 191 && adam.model().sha256().equals(ActionAdvantage008.PARENT_WEIGHTS_SHA), "Historical Adam11 payload changed"); }
		}
		if (model.id() == 11) { System.out.println("LEARNING008_OFFLINE NOT_RUN_NO_NEW_DRAFT"); return; }
		check(model.status().equals("DRAFT") && model.metrics().contains("\"objective\":\"" + ActionAdvantage008.OBJECTIVE + "\""), "008 DRAFT lineage missing");
		var frozen = store.residualSnapshot(model.id()); check(frozen.sufficient(), "Committed008 dataset insufficient");
		check(parent.state().updates() > 855 && parent.state().completedEpochs() > 191, "Adam11 did not advance");
		long applications = 0, gradients = 0;
		try (var connection = new TrainingStore(root).openTacticalConnection(); var query = connection.prepareStatement("SELECT fit_role,gradient_used,optimizer_sha FROM phantom_tactical_v3_advantage_applications WHERE model_id=?"))
		{
			query.setLong(1, model.id()); try (var rows = query.executeQuery()) { while (rows.next()) { applications++; if (rows.getBoolean(2)) { gradients++; } check(rows.getBoolean(2) == rows.getString(1).equals("GRADIENT") && rows.getString(3).equals(parent.sha()), "008 atomic marker/optimizer mismatch"); } }
		}
		check(applications == frozen.samples().size(), "008 missing or duplicate application markers");
		long trainEpisodes = frozen.targets().stream().filter(t -> t.sample().selection().split().equals("TRAIN")).map(t -> t.sample().terminal().naturalKey()).distinct().count();
		check(gradients == trainEpisodes, "008 episode markers were counted as action targets");
		check(store.freezeResidual() == null, "008 duplicate training credit available after COMMIT");
		var observations = new ArrayList<String>(); var groups = new ArrayList<String>();
		for (String split : List.of("TRAIN", "TUNING", "AUDIT")) { for (int classId : List.of(94, 103, 110))
		{
			var samples = frozen.samples().stream().filter(s -> s.selection().split().equals(split) && s.selection().observation().self().classId() == classId).toList(); if (samples.isEmpty()) { continue; }
			int legal = 0, candidateDifferent = 0, teacherDifferent = 0, candidateMargin = 0, teacherMargin = 0;
			for (var sample : samples)
			{
				var selection = sample.selection(); var observation = selection.observation(); var offered = selection.offered(); if (offered.size() < 2) { continue; } legal++;
				var ranks = new ArrayList<String>(); int index = 0;
				for (var checkpoint : List.of(teacher, model))
				{
					float best = -Float.MAX_VALUE, second = best; LegalActionV3 top = null; var scores = new ArrayList<String>();
					for (var action : offered) { float score = checkpoint.model().score(observation.state(), action.features(observation)); scores.add("{\"skillId\":" + action.skillId() + ",\"skillLevel\":" + action.skillLevel() + ",\"score\":" + score + "}"); if (score > best) { second = best; best = score; top = action; } else { second = Math.max(second, score); } }
					boolean differs = !top.equals(selection.stock()); double gap = best - second;
					if (index++ == 0) { if (differs) { teacherDifferent++; if (gap >= .025) { teacherMargin++; } } } else if (differs) { candidateDifferent++; if (gap >= .025) { candidateMargin++; } }
					ranks.add("{\"modelId\":" + checkpoint.id() + ",\"topSkill\":" + top.skillId() + ",\"topDifferentFromStock\":" + differs + ",\"gap\":" + gap + ",\"scores\":[" + String.join(",", scores) + "]}");
				}
				observations.add("{\"episode\":" + TrainingStore.quote(sample.terminal().episode().id()) + ",\"classId\":" + classId + ",\"split\":" + TrainingStore.quote(split) + ",\"stockSkill\":" + selection.stock().skillId() + ",\"actualSelectedSkill\":" + selection.selected().skillId() + ",\"propensity\":" + selection.propensity() + ",\"ranks\":[" + String.join(",", ranks) + "],\"counterfactualOutcome\":null}");
			}
			groups.add("{\"classId\":" + classId + ",\"split\":" + TrainingStore.quote(split) + ",\"independentEpisodes\":" + samples.size() + ",\"legalTwoPlus\":" + legal + ",\"candidateTopDifferent\":" + candidateDifferent + ",\"candidateDifferentAtMargin\":" + candidateMargin + ",\"draft11TopDifferent\":" + teacherDifferent + ",\"draft11DifferentAtMargin\":" + teacherMargin + "}");
		}}
		String report = "{\"schemaVersion\":8,\"check\":\"COLD_SQL_AND_FROZEN_FACTUAL_OBSERVATIONS\",\"modelId\":" + model.id() + ",\"modelSha\":" + TrainingStore.quote(model.sha()) + ",\"targetSha\":" + TrainingStore.quote(frozen.datasetSha()) + ",\"optimizerSha\":" + TrainingStore.quote(parent.sha()) + ",\"optimizerUpdates\":" + parent.state().updates() + ",\"completedEpochs\":" + parent.state().completedEpochs() + ",\"applicationMarkers\":" + applications + ",\"gradientEpisodes\":" + gradients + ",\"gradientActionTargets\":" + frozen.weightedExamples().size() + ",\"duplicateCreditAvailable\":false,\"trainingCredit\":false,\"quality\":" + model.metrics() + ",\"rankingGroups\":[" + String.join(",", groups) + "],\"observations\":[" + String.join(",", observations) + "],\"limitation\":\"Factual residual forecast MSE and predicted ranks do not measure causal stock advantage; later stock p1 is conditional, not uniform action evidence\"}";
		report = report.substring(0, report.length() - 1) + ",\"historical007AtomicMarkers\":58,\"historical007Gradients\":19,\"historical007TargetSha\":" + TrainingStore.quote(historical.datasetSha()) + ",\"historicalAdam11Sha\":" + TrainingStore.quote(ActionAdvantage008.PARENT_ADAM_SHA) + ",\"historicalAdam11PayloadExact\":true}";
		Path path = store.residualDirectory().resolve("offline-" + model.id() + "-" + model.sha() + ".json"); byte[] bytes = (report + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
		if (java.nio.file.Files.exists(path)) { check(java.util.Arrays.equals(bytes, java.nio.file.Files.readAllBytes(path)), "Cold008 evidence changed"); } else { java.nio.file.Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW); }
		System.out.println("LEARNING008_OFFLINE model=" + model.id() + " sha=" + model.sha() + " Adam=" + parent.sha() + " markers=" + applications + " gradientEpisodes=" + gradients + " actionTargets=" + frozen.weightedExamples().size());
	}
	private static void baseline()
	{
		var controls = new ArrayList<ActionAdvantage008.Control>(); var query = new float[192]; query[0] = .5f;
		for (int i = 0; i < 8; i++)
		{
			controls.add(new ActionAdvantage008.Control("paid-" + i, "cell-" + (i % 2), "block-" + i, "TRAIN", i % 2 == 0 ? 94 : 103, query, i == 0 ? 1 : .2));
		}
		var forecast = ActionAdvantage008.baseline(controls, "block-0", query);
		check(Math.abs(forecast.value() - .2) < .005, "TRAIN-only baseline did not exclude own matched control");
		check(forecast.controls() == 7 && forecast.cells() == 2 && forecast.sourceSha().matches("[a-f0-9]{64}"), "Baseline lost exact cell/source provenance");
		var changed = new ArrayList<>(controls); changed.set(0, new ActionAdvantage008.Control("paid-0", "cell-0", "block-0", "TRAIN", 94, query, -.9));
		check(ActionAdvantage008.baseline(changed, "block-0", query).equals(forecast), "Excluded own-block utility leaked into baseline");
		var imbalanced = List.of(new ActionAdvantage008.Control("a", "c94", "a", "TRAIN", 94, query, .1),
			new ActionAdvantage008.Control("b", "c94", "b", "TRAIN", 94, query, .1),
			new ActionAdvantage008.Control("c", "c94", "c", "TRAIN", 94, query, .1),
			new ActionAdvantage008.Control("d", "c103", "d", "TRAIN", 103, query, .9));
		check(Math.abs(ActionAdvantage008.baseline(imbalanced, "", query).value() - .5) < .005, "Dominant TRAIN class biased baseline accounting");
		reject(() -> new ActionAdvantage008.Control("audit", "unseen", "audit", "AUDIT", 110, query, .9), "Unseen AUDIT entered baseline");
		reject(() -> new ActionAdvantage008.Control("train110", "unseen", "train", "TRAIN", 110, query, .9), "Class110 entered TRAIN");
		reject(() -> ActionAdvantage008.baseline(List.of(controls.get(1), controls.get(1), controls.get(2), controls.get(3)), "", query), "Paid control reused");
		check(query[0] == .5f && controls.size() == 8, "Frozen source mutated");
		System.out.println("LEARNING008_BASELINE ownBlock=EXCLUDED classAccounting=BALANCED sourceCells=PRESERVED AUDIT=REFUSED counterfactualReceipts=NONE native=NOT_RUN");
	}
	private static void reject(Runnable action, String message)
	{
		boolean rejected = false; try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
		check(rejected, message);
	}
	private static void targets()
	{
		var samples = new ArrayList<ActionAdvantage008.Sample>();
		for (int c = 0; c < 2; c++)
		{
			for (int block = 0; block < 4; block++) { samples.add(sample(c * 8 + block, "TRAIN", c == 0 ? 94 : 103, false, block, 0)); }
			for (int block = 0; block < 4; block++) { samples.add(sample(c * 8 + 4 + block, "TRAIN", c == 0 ? 94 : 103, true, block, 0)); }
		}
		for (int i = 16; i < 24; i++) { samples.add(sample(i, "TUNING", i % 2 == 0 ? 94 : 103, false, i, 0)); }
		for (int i = 24; i < 32; i++) { samples.add(sample(i, "AUDIT", 110, false, i, 0)); }
		var frozen = ActionAdvantage008.freeze("1".repeat(32), samples); var targets = frozen.targets();
		var auditModel = TacticalModelV3.initialized(183);
		check(Math.abs(auditModel.loss(TacticalStoreV3.coldAuditExamples(frozen.sourceRows(), frozen)) - auditModel.loss(frozen.examples("AUDIT"))) < 1e-12, "Cold008 reader compares residual loss with local-action labels");
		check(Math.abs(TacticalStoreV3.coldAuditConstant(frozen.sourceRows(), frozen) - frozen.constantLoss("AUDIT")) < 1e-12, "Cold008 reader uses local-action constant instead of TRAIN residual constant");
		check(auditModel.loss(TacticalStoreV3.coldAuditExamples(frozen.sourceRows(), null)) == auditModel.loss(frozen.sourceRows().stream().filter(r -> r.split().equals("AUDIT")).map(TacticalStoreV3.Row::example).toList()), "Historical local-action cold audit units changed");
		var restored = ActionAdvantage008.restore(frozen.runId(), frozen.bytes(), frozen.datasetSha());
		check(restored != null && java.util.Arrays.equals(restored.bytes(), frozen.bytes()), "008 cold target codec missing or changed");
		var corrupt = frozen.bytes(); corrupt[corrupt.length - 1] ^= 1;
		reject(() -> ActionAdvantage008.restore(frozen.runId(), corrupt, frozen.datasetSha()), "Corrupt008 frozen sources admitted");
		var train = targets.stream().filter(t -> t.sample().selection().split().equals("TRAIN")).toList();
		check(train.size() == 16, "008 lost factual initial/later paid TRAIN_B targets");
		check(train.stream().filter(t -> t.sample().selection().observation().self().classId() == 94).count() == 8
			&& train.stream().filter(t -> t.sample().selection().observation().self().classId() == 103).count() == 8, "Class-aware TRAIN targets not balanced");
		check(train.stream().filter(t -> t.propensity() == 1).count() == 8 && train.stream().filter(t -> t.propensity() == .25).count() == 8, "Late deterministic stock propensity was confused with exploration");
		check(train.stream().allMatch(t -> t.weight() > 0 && t.weight() <= 4), "Unbounded gradient weight");
		for (var s : samples.stream().filter(s -> s.selection().experimental()).toList())
		{ check(Math.abs(train.stream().filter(t -> t.sample() == s).mapToDouble(ActionAdvantage008.Target::weight).sum() - 2) < 1e-8, "Long episode dominated gradient accounting"); }
		check(targets.stream().filter(t -> t.sample().selection().split().equals("AUDIT")).allMatch(t -> t.baselineKind().equals("TRAIN_FORECAST_OOD")), "Unseen class received a fake exact baseline");
		check(frozen.sufficient() && frozen.examples("TUNING").size() >= 8 && frozen.examples("AUDIT").size() >= 8, "Independent008 coverage missing");
		var missingMate = samples.stream().filter(s -> !(s.selection().split().equals("TRAIN") && !s.selection().experimental() && s.selection().block().equals("c94-block0"))).toList();
		check(ActionAdvantage008.freeze("6".repeat(32), missingMate).targets().stream().noneMatch(t -> t.sample().selection().split().equals("TRAIN") && t.sample().selection().block().equals("c94-block0")), "Unmatched TRAIN_B entered gradient targets");
		var changed = new ArrayList<>(samples); changed.set(24, sample(24, "AUDIT", 110, false, 24, 100));
		var changedTrain = ActionAdvantage008.freeze("2".repeat(32), changed).targets().stream().filter(t -> t.sample().selection().split().equals("TRAIN")).toList();
		check(train.stream().map(t -> t.residual() + "/" + t.baselineSha() + "/" + t.weight()).toList().equals(changedTrain.stream().map(t -> t.residual() + "/" + t.baselineSha() + "/" + t.weight()).toList()), "AUDIT outcome changed TRAIN objective");
		var duplicate = new ArrayList<>(samples); duplicate.add(samples.getFirst()); reject(() -> ActionAdvantage008.freeze("3".repeat(32), duplicate), "Episode credit reused");
		reject(() -> sample(99, "TRAIN", 110, true, 99, 0), "Unseen class received gradient authority");
		System.out.println("LEARNING008_TARGETS paidOnly=PASS lateStockPropensity=EXACT oneOpportunity=UNCHANGED episodeWeight=BALANCED trainClasses=94,103 unseen110=NO_GRADIENT native=NOT_RUN");
	}
	private static void authority() throws Exception
	{
		var module = Path.of(System.getProperty("phantom.module.root", "L2J_Mobius_CT_2.6_HighFive"));
		var shipped = TrainingConfig.load(module.resolve("tools/phantom-runtime/training/Exploration008.ini"));
		check(shipped.text("exploration008Enabled", "").equals("false") && shipped.text("outcome006Enabled", "").equals("false")
			&& shipped.text("exploration007Enabled", "").equals("false"), "Shipped008 exploration not OFF");
		check(shipped.integer("decisionCooldownMillis", 0, 1000, 60000) == 2000 && shipped.text("v3Mode", "").equals("SHADOW"), "008 policy safety settings changed");
		check(ActionAdvantage008.pinsMatch(11, ActionAdvantage008.TEACHER_SHA, ActionAdvantage008.PARENT_ADAM_SHA, ActionAdvantage008.PARENT_WEIGHTS_SHA, 855, 191), "Exact Adam11 parent refused");
		check(!ActionAdvantage008.pinsMatch(11, ActionAdvantage008.TEACHER_SHA, ActionAdvantage008.PARENT_ADAM_SHA, ActionAdvantage008.TEACHER_SHA, 855, 191), "Selected early-stopping weights attached to terminal moments");
		check(!ActionAdvantage008.pinsMatch(9, ActionAdvantage008.TEACHER_SHA, ActionAdvantage008.PARENT_ADAM_SHA, ActionAdvantage008.PARENT_WEIGHTS_SHA, 855, 191)
			&& !ActionAdvantage008.pinsMatch(11, ActionAdvantage008.TEACHER_SHA, "0".repeat(64), ActionAdvantage008.PARENT_WEIGHTS_SHA, 855, 191), "Foreign parent/model pin admitted");
		check(ActionAdvantage008.explorationPermitted(true, true, "COLLECT", ActionAdvantage008.CAMPAIGN, ActionAdvantage008.DATASET, "TRAIN"), "Owned008 TRAIN permission missing");
		for (String split : List.of("TUNING", "AUDIT", "EVAL", "MAIN", "PLAY"))
		{ check(!ActionAdvantage008.explorationPermitted(true, true, "COLLECT", ActionAdvantage008.CAMPAIGN, ActionAdvantage008.DATASET, split), "Exploration leaked to " + split); }
		check(!ActionAdvantage008.explorationPermitted(false, true, "COLLECT", ActionAdvantage008.CAMPAIGN, ActionAdvantage008.DATASET, "TRAIN")
			&& !ActionAdvantage008.explorationPermitted(true, false, "COLLECT", ActionAdvantage008.CAMPAIGN, ActionAdvantage008.DATASET, "TRAIN")
			&& !ActionAdvantage008.explorationPermitted(true, true, "EVALUATE", ActionAdvantage008.CAMPAIGN, ActionAdvantage008.DATASET, "TRAIN")
			&& !ActionAdvantage008.explorationPermitted(true, true, "COLLECT", TrainExplorationV1.CAMPAIGN, ActionAdvantage008.DATASET, "TRAIN"), "008 isolation/phase/campaign guard weakened");
		boolean admitted = false;
		try { new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.DraftAuthority(12, "a".repeat(64), "b".repeat(64), "c".repeat(64), "1".repeat(32), "EVAL008"); admitted = true; }
		catch (IllegalArgumentException absent) { }
		check(admitted, "Typed EVAL008 namespace missing");
		var authority = new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.DraftAuthority(12, "a".repeat(64), "b".repeat(64), "c".repeat(64), "1".repeat(32), "EVAL008");
		var mode = org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Mode.SOLO_DAMAGE_V1;
		var pin = new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Pin(mode, 12, "DRAFT", "a".repeat(64), TacticalObservationV3.SCHEMA, 3, authority);
		var arm = new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Arm("008-contract", org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Side.STOCK, mode, 183, 0, "d".repeat(64), "e".repeat(64), "controlled-math", java.util.Map.of(), List.of(), null, pin,
			org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.Outcome.SKIP, "MATH_ONLY", 0, 0, java.util.Map.of(), List.of(), List.of(), true);
		var root = java.nio.file.Files.createTempDirectory("eval008-contract-").toRealPath();
		try (var ledger = new org.l2jmobius.tests.phantoms.PhantomLearning005Ledger(root, "eval008", "d".repeat(64), "e".repeat(64), authority)) { check(ledger.put(arm), "008 arm refused"); }
		try (var ledger = new org.l2jmobius.tests.phantoms.PhantomLearning005Ledger(root, "eval008", "d".repeat(64), "e".repeat(64), authority)) { check(ledger.arms().size() == 1 && ledger.arms().getFirst().pin().authority().equals(authority), "Cold008 authority lost"); }
		var legacy = new org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.DraftAuthority(12, "a".repeat(64), "b".repeat(64), "c".repeat(64), "1".repeat(32), "EVAL007");
		boolean refused = false; try (var ignored = new org.l2jmobius.tests.phantoms.PhantomLearning005Ledger(root, "eval008", "d".repeat(64), "e".repeat(64), legacy)) { } catch (java.io.IOException expected) { refused = true; }
		check(refused, "008 frames admitted as historical007");
		System.out.println("LEARNING008_AUTHORITY Adam11=EXACT selectedVsTerminalWeights=SEPARATE exploration=ISOLATED_TRAIN_ONLY EVAL008=EXACT_COLD legacyIsolation=PASS native=NOT_RUN");
	}
	private static ActionAdvantage008.Sample sample(int i, String split, int classId, boolean experimental, int block, double extraHp)
	{
		int actor = 100 + i, target = 1000 + i, stockId = classId == 94 ? 1230 : classId == 103 ? 1235 : 1239, otherId = classId == 94 ? 1339 : classId == 103 ? 1340 : 1341;
		long profile = 10000 + i, epoch = 100000 + i, spawn = 200000 + i;
		String id = String.format("%064x", 1000 + i), party = "solo:" + actor;
		var rows = new ArrayList<TerminalRewardReceiptV1.TimedAction>();
		TacticalObservationV3 firstObservation = null; List<LegalActionV3> firstOffered = null; LegalActionV3 firstStock = null, firstSelected = null;
		for (int step = 0; step < 2; step++)
		{
			var facts = java.util.Map.of(TacticalObservationV3.Field.HP, TacticalObservationV3.Fact.known(.8), TacticalObservationV3.Field.MAX_HP, TacticalObservationV3.Fact.known(2000),
				TacticalObservationV3.Field.LEVEL, TacticalObservationV3.Fact.known(77), TacticalObservationV3.Field.CLASS_ID, TacticalObservationV3.Fact.known(classId));
			var self = new TacticalObservationV3.Entity(TacticalObservationV3.Role.SELF, actor, epoch, 0, classId, facts, List.of(), List.of(), List.of(), List.of());
			var enemyFacts = java.util.Map.of(TacticalObservationV3.Field.HP, TacticalObservationV3.Fact.known(.8), TacticalObservationV3.Field.MAX_HP, TacticalObservationV3.Fact.known(2000),
				TacticalObservationV3.Field.LEVEL, TacticalObservationV3.Fact.known(76), TacticalObservationV3.Field.NPC_ID, TacticalObservationV3.Fact.known(21798));
			var enemy = new TacticalObservationV3.Entity(TacticalObservationV3.Role.ENEMY, target, spawn, 0, 0, enemyFacts, List.of(), List.of(), List.of(), List.of());
			var observation = new TacticalObservationV3(step + 1, 150 + step * 1000, profile, actor, epoch, party, List.of(self, enemy));
			var stock = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", profile, actor, epoch, step + 1, target, spawn, party, stockId, 28, 60, 4000, 900, false, true);
			var alternative = new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", profile, actor, epoch, step + 1, target, spawn, party, otherId, 1, 105, 6000, 900, false, true);
			var selected = experimental && step == 0 ? alternative : stock; double hp = (selected == alternative ? 900 : 500) + extraHp;
			var receipt = new TacticalReceiptV3(String.format("%064x", 10000 + i * 2 + step), new TacticalReceiptV3.Attempt(observation, List.of(stock, alternative), selected, stock, 11),
				2000, 2000 - hp, hp, selected.mpCost(), 1000000000L, TacticalReceiptV3.reward(hp, 2000, selected.mpCost(), 1000000000L), new HealEvidenceV3.SourceOwner(1, "Combat/math008/" + i));
			rows.add(new TerminalRewardReceiptV1.TimedAction(new TacticalStoreV3.Row(split, "learning008|" + split + "|spot=math|episode=" + i, receipt), 0, 200 + step * 1000, 300 + step * 1000, 400 + step * 1000, 500 + step * 1000, false));
			if (step == 0) { firstObservation = observation; firstOffered = List.of(stock, alternative); firstStock = stock; firstSelected = selected; }
		}
		var result = new TacticalEpisodeV3.Actor(profile, actor, epoch, classId, 77, 900, 800, false, 50000L, 10000L);
		var episode = new TacticalEpisodeV3(id, "b".repeat(32), "SOLO", 100, 3000000100L, "NATIVE_KILL", List.of(result), List.of(new TacticalEpisodeV3.Kill(target, 21798, spawn, 0, actor, 1)),
			rows.stream().map(r -> r.row().eventKey()).toList(), 1400, 0, 165, 0);
		var boundary = new TerminalRewardReceiptV1.Boundary("a".repeat(64), "b".repeat(32), 1, 0, target, spawn, 100, 2000000100L, 3000000100L, true);
		var terminal = TerminalRewardReceiptV1.create(boundary, episode, rows, split, ActionAdvantage008.DATASET, TerminalRewardReceiptV1.Config.defaults());
		String cell = (split.equals("TRAIN") ? classId == 94 ? "c" : "d" : "e").repeat(64);
		var selection = new TrainExplorationV1.Selection(id, "c" + classId + "-block" + block, cell, split, experimental, firstObservation, firstOffered, firstStock, firstSelected,
			experimental ? .25 : 1, experimental ? .9 : 0, experimental ? "EXPLORATION_ALTERNATIVE" : "CONTROL_STOCK", "", 11, ActionAdvantage008.TEACHER_SHA);
		return new ActionAdvantage008.Sample(selection, terminal);
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

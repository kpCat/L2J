/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.phantoms.learning.ActionContinuationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExperimentReceiptV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionFeedbackV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionValueV4;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpPoolWrite;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.PvpReceipt;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Entity;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Fact;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Role;
import org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;

/** DB-free contract checks for the TASK131 frozen evaluation policy. */
public final class PhantomLearning131FrozenChecks
{
	private static final String TRAIN_EPISODE = "a".repeat(64);
	private static final String SOURCE = "b".repeat(64);
	private static final String EVAL_EPISODE = "c".repeat(64);
	private static final List<String> SKILLS = List.of("1000:1", "7000:1");

	public static void main(String[] args) throws Exception
	{
		if (args.length == 3 && args[0].equals("cold"))
		{
			ActionContinuationV4.cold(Path.of(args[1]), args[2]);
			return;
		}
		if (args.length != 0) { throw new IllegalArgumentException("Unexpected frozen test arguments"); }
		final var model = checkpoint();
		final Path scratch = Files.createTempDirectory(Path.of(".").toAbsolutePath().normalize(), "learning131-frozen-");
		try
		{
			final var lineage = ActionContinuationV4.initialize(scratch, model);
			checkFrozen(scratch, lineage.head(), model);
			System.out.println("LEARNING131_FROZEN_CONTRACT_GREEN TUNING=MODEL AUDIT=MODEL exploration=0 checkpointUnchanged=true");
			checkNativeHealOffers(scratch, lineage.head(), model);
			System.out.println("LEARNING131_V4_HEAL_OFFER_CONTRACT_PASS");
			checkCaptureScope(model);
			System.out.println("LEARNING131_EPISODE_SCOPE_OFFLINE_PASS");
			checkPvpReceipt();
			System.out.println("LEARNING131_PVP_HP_CP_CONTRACT_PASS");
			checkCommittedReplay(scratch, lineage.head());
			System.out.println("LEARNING131_COMMITTED_REPLAY_OFFLINE_PASS");
		}
		finally
		{
			try (var files = Files.list(scratch))
			{
				for (Path file : files.toList()) { Files.delete(file); }
			}
			Files.delete(scratch);
		}
	}

	private static void checkPvpReceipt() throws IOException
	{
		final long started = System.nanoTime();
		final var self = new Entity(Role.SELF, 101, 1, 0, 0, Map.of(), List.of(), List.of(), List.of(), List.of());
		final var enemy = new Entity(Role.ENEMY, 102, 2, 0, 0, Map.of(Field.MAX_HP, Fact.known(800), Field.MAX_CP, Fact.known(400)),
			List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(1, started, 1, 101, 1, "", List.of(self, enemy));
		final var stock = offer(observation, 1000, 2);
		final var alternative = offer(observation, 7000, 2);
		final var attempt = new TacticalReceiptV3.Attempt(observation, List.of(stock, alternative), alternative, stock, 0);
		final var owner = new SourceOwner(1, "Combat/1/1/1");
		final var cp = new PvpPoolWrite(100, 0, started + 40, started + 40);
		final var hp = new PvpPoolWrite(800, 740, started + 50, started + 50);
		final var cpOnly = new PvpReceipt(SOURCE, attempt, null, cp, 4, started + 10, started + 20, 100, 0, false, owner);
		check(cpOnly.hp() == null && cpOnly.cp().delta() == 100, "CP-only action must keep HP absent");
		final var mixed = new PvpReceipt(SOURCE, attempt, hp, cp, 4, started + 10, started + 20, 100, 0, false, owner);
		check(mixed.hp().delta() == 60 && mixed.cp().delta() == 100, "PvP HP and CP cannot share a pool");
		rejects(() -> new PvpReceipt(SOURCE, attempt, null, cp, 0, started + 10, started + 20, 100, 0, false, owner), "Unpaid PvP effect");
		rejects(() -> new PvpReceipt(SOURCE, attempt, null, new PvpPoolWrite(500, 350, started + 40, started + 40), 4,
			started + 10, started + 20, 100, 0, false, owner), "PvP CP debit above the pinned pool");
	}

	private static void checkCommittedReplay(Path root, String originalHead) throws Exception
	{
		final String originalModel = ActionContinuationV4.load(root).model().sha256();
		final String nativeSha = "1".repeat(64);
		final var firstSamples = historySamples("8".repeat(64), 1, 257);
		final var first = ActionContinuationV4.prepareBatch(root, originalHead, firstSamples, nativeSha);
		final var internal = ActionContinuationV4.prepare(root, originalHead, firstSamples.subList(0, 256), nativeSha);
		cold(root, internal.head());
		cold(root, first.head());
		final var firstPlan = plan("TUNING", EVAL_EPISODE, first.model().sha256());
		rejects(() -> ActionExplorationV4.frozenCommitted(firstPlan, root, first.head()), "Cold but uncommitted checkpoint");
		rejects(() -> ActionContinuationV4.verifyCommittedBatchOrigin(root, originalHead, first.head(), firstSamples, nativeSha), "Cold but uncommitted batch");
		ActionContinuationV4.commitBatch(root, originalHead, first.head(), firstSamples, nativeSha);
		final var observation = observation();
		final var stock = offer(observation, 1000, 2);
		final var choices = List.of(stock, offer(observation, 7000, 2));
		final var decision = ActionExplorationV4.frozen(firstPlan, root, first.head()).choose(observation, choices, stock, true, Long.MAX_VALUE);

		final String successorSha = "2".repeat(64);
		final var successorSamples = historySamples("9".repeat(64), 258, 1);
		final var successor = ActionContinuationV4.prepareBatch(root, first.head(), successorSamples, successorSha);
		cold(root, successor.head());
		ActionContinuationV4.commitBatch(root, first.head(), successor.head(), successorSamples, successorSha);
		final List<Path> paths = files(root);
		ActionExplorationV4.frozenCommitted(firstPlan, root, first.head()).verifyFrozenLog(List.of(ActionExplorationV4.Decision.restore(decision.bytes())));
		ActionContinuationV4.verifyCommittedBatchOrigin(root, originalHead, first.head(), firstSamples, nativeSha);
		ActionContinuationV4.verifyAuditModelOrder(root, first.head(), successor.head(), first.model().sha256(), successor.model().sha256());
		rejects(() -> ActionContinuationV4.verifyAuditModelOrder(root, successor.head(), first.head(), successor.model().sha256(), first.model().sha256()),
			"Candidate and reference checkpoints were swapped");
		rejects(() -> ActionContinuationV4.verifyAuditModelOrder(root, originalHead, successor.head(), originalModel,
			successor.model().sha256()), "Imported bootstrap is not the first committed TRAIN result");
		rejects(() -> ActionExplorationV4.frozen(firstPlan, root, first.head()), "Historical checkpoint through the live route");
		rejects(() -> ActionContinuationV4.verifyCurrentBatchOrigin(root, originalHead, first.head(), firstSamples, nativeSha), "Historical batch through the current route");
		final var internalPlan = plan("AUDIT", EVAL_EPISODE, internal.model().sha256());
		rejects(() -> ActionExplorationV4.frozenCommitted(internalPlan, root, internal.head()), "Cold intermediate TRAIN chunk");
		rejects(() -> ActionContinuationV4.verifyCommittedBatchOrigin(root, originalHead, internal.head(), firstSamples, nativeSha), "Intermediate origin of a committed batch");
		check(ActionContinuationV4.load(root).head().equals(successor.head()) && paths.equals(files(root)), "Historical replay changed continuation artifacts");
	}

	private static List<Path> files(Path root) throws IOException
	{
		try (var stream = Files.list(root)) { return stream.sorted().toList(); }
	}

	static List<ActionFeedbackV4> historySamples(String episodeId, int first, int count)
	{
		final var self = new Entity(Role.SELF, 101, 1, 0, 0, Map.of(), List.of(), List.of(), List.of(), List.of());
		final var enemy = new Entity(Role.ENEMY, 102, 2, 0, 0, Map.of(Field.MAX_HP, Fact.known(800)), List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(1, 200, 1, 101, 1, "solo:101", List.of(self, enemy));
		final var stock = offer(observation, 1000, 2);
		final var offered = List.of(stock, offer(observation, 7000, 2));
		final var attempt = new TacticalReceiptV3.Attempt(observation, offered, stock, stock, 0);
		final var owner = new SourceOwner(1, "Combat/1/1/1");
		final var keys = java.util.stream.IntStream.range(first, first + count).mapToObj(index -> String.format(java.util.Locale.ROOT, "%064x", index)).toList();
		final var episode = new TacticalEpisodeV3(episodeId, "b".repeat(32), "SOLO", 100, 300, "NATIVE_KILL",
			List.of(new TacticalEpisodeV3.Actor(1, 101, 1, 12, 52, 700, 600, false, 1L, 1L)),
			List.of(new TacticalEpisodeV3.Kill(102, 12, 2, 0, 101, 1)), keys, count * 100d, 0, count * 4d, 0);
		return keys.stream().map(key ->
		{
			final var receipt = new TacticalReceiptV3(key, attempt, 400, 300, 100, 4, 50, TacticalReceiptV3.reward(100, 800, 4, 50), owner);
			final byte[] row = TacticalStoreV3.encode(new TacticalStoreV3.Row("TRAIN", "test|" + episode.id(), receipt));
			return ActionFeedbackV4.nativeAction(row, TacticalObservationV3.hash(row), episode, owner, SOURCE);
		}).toList();
	}

	static void cold(Path root, String head) throws Exception
	{
		final String executable = ProcessHandle.current().info().command().orElseThrow();
		final Process process = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"), PhantomLearning131FrozenChecks.class.getName(),
			"cold", root.toString(), head).redirectErrorStream(true).start();
		if (!process.waitFor(30, TimeUnit.SECONDS))
		{
			process.destroyForcibly();
			throw new AssertionError("Offline cold checkpoint verification timed out");
		}
		check(process.exitValue() == 0, new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
	}

	private static void checkCaptureScope(ActionValueV4 model) throws IOException
	{
		final String flag = "phantom.learning.train";
		final String previous = System.getProperty(flag);
		try
		{
			System.clearProperty(flag);
			rejects(() -> new PhantomLearning131Capture(SOURCE, model), "Episode capture without TEST opt-in");
			System.setProperty(flag, "true");
			final var capture = new PhantomLearning131Capture(SOURCE, model);
			final var observed = observation();
			final var planned = new ActionExplorationV4.Plan(2, "EPSILON_V2", 22, 2, .15, "TRAIN", TRAIN_EPISODE, SOURCE, model.sha256(), SKILLS);
			final var experiment = new ActionExplorationV4(planned);
			final var episode = new PhantomLearning131Capture.Episode(experiment, observed.capturedNanos(),
				List.of(new PhantomLearning131Capture.Actor(1, 101, 1)), Set.of(new PhantomLearning131Capture.Target(102, 2)));
			final var offered = List.of(offer(observed, 1000, 2), offer(observed, 7000, 2));
			check(capture.selectExperiment(observed, offered) == null, "Capture selected without a bound episode");
			capture.open(episode);
			check(capture.selectExperiment(observed, offered) == experiment, "Exact active episode was not selected");
			check(capture.selectExperiment(observed, List.of(offer(observed, 1000, 3), offer(observed, 7000, 3))) == null, "Recycled target generation borrowed the V4 scope");
			final var foreign = new TacticalObservationV3(2, observed.capturedNanos(), 2, 101, 1, "", observed.entities());
			check(capture.selectExperiment(foreign, offered) == null, "A different profile borrowed the episode");
			rejects(() -> capture.tactical().actionValueTest(TacticalPolicyV3.Mode.ON, ActionValueV4.empty(), _ -> { }), "Live replacement of the fixed boot model");
			capture.closeChoices(episode);
			check(capture.selectExperiment(observed, offered) == null && capture.receipts(episode).isEmpty(), "Closed episode granted a new choice or fabricated a paid effect");
		}
		finally
		{
			if (previous == null) { System.clearProperty(flag); }
			else { System.setProperty(flag, previous); }
		}
	}

	private static void checkNativeHealOffers(Path lineage, String head, ActionValueV4 model) throws IOException
	{
		final var reference = observation();
		final var ally = new Entity(Role.ALLY, 102, 2, 0, 0, Map.of(Field.MAX_HP, Fact.known(800)), List.of(), List.of(), List.of(), List.of());
		final var observation = TacticalObservationV3.restore(new TacticalObservationV3(1, System.nanoTime(), 1, 101, 1, "party", List.of(reference.self(), ally)).bytes());
		final var stock = offer(observation, 1000, 2).withFamily(LegalActionV3.Family.HEAL);
		final var alternative = offer(observation, 7000, 2).withFamily(LegalActionV3.Family.HEAL);
		final var offered = List.of(stock, alternative);
		final var training = new ActionExplorationV4.Plan(2, "EPSILON_V2", 7, 1, .15, "TRAIN", TRAIN_EPISODE, SOURCE, model.sha256(), SKILLS);
		final var trained = new ActionExplorationV4(training).choose(observation, offered, stock, true, Long.MAX_VALUE);
		check(ActionExplorationV4.Decision.restore(trained.bytes()).equals(trained), "TRAIN heal decision must preserve the exact choice and alternatives");
		checkHealReceipt(trained);
		final var evaluation = ActionExplorationV4.frozen(plan("AUDIT", EVAL_EPISODE, model.sha256()), lineage, head);
		final var decision = evaluation.choose(observation, offered, stock, true, Long.MAX_VALUE);
		check(decision.mode().equals("MODEL") && decision.selected().equals(alternative), "Frozen V4 must rank exact admitted heal alternatives");
		evaluation.verifyFrozenLog(List.of(ActionExplorationV4.Decision.restore(decision.bytes())));
		final var legacy = new ActionExplorationV4.Plan(1, "EPSILON_V1", 7, 1, .15, "TRAIN", TRAIN_EPISODE, SOURCE, model.sha256(), SKILLS);
		rejects(() -> new ActionExplorationV4(legacy).choose(observation, offered, stock, true, Long.MAX_VALUE), "Legacy DAMAGE plan with a foreign family");
		rejects(() -> new ActionExplorationV4.Plan(2, "EPSILON_V2", 7, 1, .15, "AUDIT", EVAL_EPISODE, SOURCE, model.sha256(), SKILLS), "New exploration policy on AUDIT");
	}

	/** Receipt binding against controlled bytes. No GS, native model fitting, or combat proof. */
	private static void checkHealReceipt(ActionExplorationV4.Decision decision) throws IOException
	{
		final long elapsed = 1_000_000;
		final var owner = new SourceOwner(1, "party-support-heal131");
		final var intent = new ActionExperimentReceiptV4.Intent(decision, owner, decision.capturedNanos());
		final var attempt = new TacticalReceiptV3.Attempt(decision.observation(), decision.offered(), decision.selected(), decision.stock(), 0);
		final var receipt = new TacticalReceiptV3("e".repeat(64), attempt, 400, 500, 100, 4, elapsed, TacticalReceiptV3.reward(100, 800, 4, elapsed), owner);
		final var episode = new TacticalEpisodeV3(decision.plan().episode(), "a".repeat(32), "PARTY", decision.observation().capturedNanos(), decision.capturedNanos() + elapsed,
			"CONTRACT_PAID_HEAL", List.of(new TacticalEpisodeV3.Actor(1, 101, 1, 12, 52, 700, 600, false, null, null),
				new TacticalEpisodeV3.Actor(2, 102, 2, 2, 52, 500, 300, false, null, null)), List.of(), List.of(receipt.eventKey()), 0, 100, 4, 0);
		final byte[] row = TacticalStoreV3.encode(new TacticalStoreV3.Row("TRAIN", "test|" + episode.id(), receipt));
		final var bound = ActionExperimentReceiptV4.bind(intent, decision.plan().sha256(), row, episode, SOURCE);
		check(bound.schemaVersion() == 2 && bound.family().equals("HEAL") && bound.usefulHpFraction() == .125 && bound.paidMp() == 4, "Heal effect must retain useful HP and native MP");
		final var other = new ActionExperimentReceiptV4.Intent(decision, new SourceOwner(2, "other-party-support"), decision.capturedNanos());
		rejects(() -> ActionExperimentReceiptV4.bind(other, decision.plan().sha256(), row, episode, SOURCE), "Heal effect with a foreign dispatch owner");
	}

	private static void checkFrozen(Path lineageRoot, String head, ActionValueV4 model) throws IOException
	{
		final var observation = observation();
		final var stock = offer(observation, 1000, 2);
		final var alternative = offer(observation, 7000, 2);
		final var offered = List.of(stock, alternative);
		final byte[] original = model.bytes();

		for (String split : List.of("TUNING", "AUDIT"))
		{
			final var plan = plan(split, EVAL_EPISODE, model.sha256());
			final var session = ActionExplorationV4.frozen(plan, lineageRoot, head);
			final var decision = session.choose(observation, offered, stock, true, Long.MAX_VALUE);
			check(decision.mode().equals("MODEL") && decision.selected().equals(alternative), split + " must use the model to reorder the legal offer");
			check(decision.propensity() == 1 && decision.draw() == 0, split + " must be deterministic inference without exploration");
			final var restored = ActionExplorationV4.Decision.restore(decision.bytes());
			check(restored.equals(decision), split + " frozen decision must replay exactly");
			session.verifyFrozenLog(List.of(restored));
			rejects(() -> ActionExplorationV4.validateLog(List.of(restored)), "Frozen evidence through the historical verifier");
			final var forged = new ActionExplorationV4.Decision(plan, 0, observation, offered, stock, stock,
				"MODEL", 1, 0, decision.capturedNanos(), Long.MAX_VALUE, decision.reason());
			rejects(() -> session.verifyFrozenLog(List.of(ActionExplorationV4.Decision.restore(forged.bytes()))),
				"Different legal selection under the frozen MODEL label");
			final var foreignPlan = new ActionExplorationV4.Plan(2, "MODEL_V2", 0, 0, 0, split, EVAL_EPISODE, "f".repeat(64), model.sha256(), SKILLS);
			final var foreignSession = ActionExplorationV4.frozen(foreignPlan, lineageRoot, head);
			rejects(() -> foreignSession.verifyFrozenLog(List.of(restored)), "Decision from a foreign source plan");

			rejects(() -> new ActionExplorationV4.Decision(plan, 0, observation, offered, stock, alternative,
				"EXPLORE_COVERAGE", 1, 0, decision.capturedNanos(), Long.MAX_VALUE, "COVERAGE"), "Exploration receipt in evaluation");
			rejects(() -> session.choose(observation, offered, stock, true, Long.MAX_VALUE), "Replay of a consumed observation");
			rejects(() -> ActionExplorationV4.frozen(plan, lineageRoot, head).choose(observation, List.of(stock, offer(observation, 7000, 3)), stock,
				true, Long.MAX_VALUE), "Stale target epoch");
			rejects(() -> ActionExplorationV4.frozen(plan, lineageRoot, head).choose(observation, offered, stock, alternative, true, Long.MAX_VALUE),
				"Caller-supplied model selection");
			for (boolean safe : List.of(false, true))
			{
				final long deadline = safe ? 1 : Long.MAX_VALUE;
				final var fallback = ActionExplorationV4.frozen(plan, lineageRoot, head).choose(observation, offered, stock, safe, deadline);
				check(fallback.mode().equals("BASELINE") && fallback.selected().equals(stock), "Unsafe or late MODEL must fall back to stock");
			}
		}
		check(Arrays.equals(original, model.bytes()) && model.updates() == 1, "Evaluation changed the frozen checkpoint");
		check(ActionContinuationV4.load(lineageRoot).head().equals(head), "Evaluation changed the committed lineage");
		admissionRejectsExplorationAndStaleModels(lineageRoot, head, model, observation, offered, stock);
	}

	private static void admissionRejectsExplorationAndStaleModels(Path lineageRoot, String head, ActionValueV4 model,
		TacticalObservationV3 observation, List<LegalActionV3> offered, LegalActionV3 stock) throws IOException
	{
		rejects(() -> plan("TRAIN", EVAL_EPISODE, model.sha256()), "Frozen evaluation policy in TRAIN");
		rejects(() -> ActionExplorationV4.frozen(plan("AUDIT", TRAIN_EPISODE, model.sha256()), lineageRoot, head), "Evaluation episode already present in lineage");
		rejects(() -> ActionExplorationV4.frozen(plan("AUDIT", EVAL_EPISODE, SOURCE), lineageRoot, head), "Foreign checkpoint SHA");
		rejects(() -> ActionExplorationV4.frozen(plan("AUDIT", EVAL_EPISODE, model.sha256()), lineageRoot, SOURCE), "Unpinned lineage HEAD");
		final var empty = ActionValueV4.empty();
		final Path emptyRoot = Files.createTempDirectory(lineageRoot.getParent(), "learning131-empty-");
		try
		{
			final String emptyHead = ActionContinuationV4.initialize(emptyRoot, empty).head();
			rejects(() -> ActionExplorationV4.frozen(plan("AUDIT", EVAL_EPISODE, empty.sha256()), emptyRoot, emptyHead), "Unfitted checkpoint");
		}
		finally
		{
			try (var files = Files.list(emptyRoot))
			{
				for (Path file : files.toList()) { Files.delete(file); }
			}
			Files.delete(emptyRoot);
		}

		for (String split : List.of("TUNING", "AUDIT"))
		{
			for (String policy : List.of("EPSILON_V1", "COVERAGE_TEST_V1", "MODEL_V1"))
			{
				rejects(() -> new ActionExplorationV4.Plan(1, policy, 7, 1, .15, split, EVAL_EPISODE, SOURCE, model.sha256(), SKILLS),
					"Historical non-baseline policy in evaluation");
			}
			rejects(() -> new ActionExplorationV4.Plan(2, "MODEL_V2", 7, 1, .15, split, EVAL_EPISODE, SOURCE, model.sha256(), SKILLS),
				"Exploration parameters in a frozen evaluation plan");
			final var legacy = new ActionExplorationV4.Plan(1, "BASELINE_V1", 7, 1, .15, split, EVAL_EPISODE, SOURCE, model.sha256(), SKILLS);
			final var baseline = new ActionExplorationV4(legacy).choose(observation, offered, stock, true, Long.MAX_VALUE);
			check(baseline.mode().equals("BASELINE") && ActionExplorationV4.Decision.restore(baseline.bytes()).equals(baseline), "Historical baseline replay");
		}

		final var training = new ActionExplorationV4.Plan(1, "COVERAGE_TEST_V1", 7, 1, .15, "TRAIN", TRAIN_EPISODE, SOURCE, model.sha256(), SKILLS);
		final var coverage = new ActionExplorationV4(training).choose(observation, offered, stock, true, Long.MAX_VALUE);
		check(coverage.mode().equals("EXPLORE_COVERAGE") && coverage.selected().equals(offered.getLast()), "Historical TRAIN coverage exploration");
		final var legacyModel = new ActionExplorationV4.Plan(1, "MODEL_V1", 7, 1, .15, "TRAIN", TRAIN_EPISODE, SOURCE, model.sha256(), SKILLS);
		final var legacyModelDecision = new ActionExplorationV4(legacyModel).choose(observation, offered, stock, offered.getLast(), true, Long.MAX_VALUE);
		check(legacyModelDecision.mode().equals("MODEL") && legacyModelDecision.selected().equals(offered.getLast())
			&& ActionExplorationV4.Decision.restore(legacyModelDecision.bytes()).equals(legacyModelDecision), "Historical TRAIN model selection and replay");

		final var plan = plan("AUDIT", EVAL_EPISODE, model.sha256());
		rejects(() -> new ActionExplorationV4(plan), "Evaluation session without a checkpoint");
		final var session = ActionExplorationV4.frozen(plan, lineageRoot, head);
		final var policy = new TacticalPolicyV3(TacticalPolicyV3.Mode.SHADOW, 0, null, 20, 16, 1000, _ -> { });
		final String optIn = "phantom.learning.train";
		final String previous = System.getProperty(optIn);
		try
		{
			System.clearProperty(optIn);
			rejects(() -> policy.actionExplorationTest(session), "Ordinary configuration without TEST admission");
			System.setProperty(optIn, "true");
			policy.actionValueTest(TacticalPolicyV3.Mode.ON, empty, _ -> { });
			rejects(() -> policy.actionExplorationTest(session), "Foreign model in the tactical policy");
			policy.actionValueTest(TacticalPolicyV3.Mode.ON, model, _ -> { });
			policy.actionExplorationTest(session);
			rejects(() -> policy.actionExplorationTest(new ActionExplorationV4(training)), "Replacement of a frozen episode");
			rejects(() -> policy.actionValueTest(TacticalPolicyV3.Mode.ON, empty, _ -> { }), "Replacement of a frozen checkpoint");
		}
		finally
		{
			if (previous == null) { System.clearProperty(optIn); }
			else { System.setProperty(optIn, previous); }
		}
	}

	private static ActionExplorationV4.Plan plan(String split, String episode, String modelSha)
	{
		return new ActionExplorationV4.Plan(2, "MODEL_V2", 0, 0, 0, split, episode, SOURCE, modelSha, SKILLS);
	}

	private static TacticalObservationV3 observation()
	{
		final var self = new Entity(Role.SELF, 101, 1, 0, 0, Map.of(), List.of(), List.of(), List.of(), List.of());
		final var enemy = new Entity(Role.ENEMY, 102, 2, 0, 0, Map.of(), List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(1, System.nanoTime(), 1, 101, 1, "", List.of(self, enemy));
		return TacticalObservationV3.restore(observation.bytes());
	}

	private static LegalActionV3 offer(TacticalObservationV3 observation, int skill, long targetEpoch)
	{
		return new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", observation.profileId(), observation.actorObjectId(),
			observation.actorEpoch(), observation.decisionId(), 102, targetEpoch, observation.partyIdentity(), skill, 1, 4, 500, 300, false, true);
	}

	/** Synthetic checkpoint fixture with a positive skill-id weight; no FIT or native evidence. */
	static ActionValueV4 checkpoint() throws IOException
	{
		final var buffer = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(buffer))
		{
			out.writeInt(0x41563431);
			out.writeUTF(ActionValueV4.SCHEMA);
			out.writeUTF("0".repeat(64));
			out.writeUTF(SOURCE);
			out.writeLong(1);
			for (int i = 0; i < ActionValueV4.COEFFICIENTS; i++)
			{
				out.writeDouble(i == 2 ? 1 : 0);
			}
			out.writeInt(1);
			out.writeUTF("d".repeat(64));
			out.writeUTF(SOURCE);
			out.writeInt(1);
			out.writeUTF(TRAIN_EPISODE);
			out.writeUTF("TRAIN");
		}
		final byte[] bytes = buffer.toByteArray();
		return ActionValueV4.restore(bytes, TacticalObservationV3.hash(bytes));
	}

	@FunctionalInterface
	private interface Probe { void run() throws IOException; }

	private static void rejects(Probe action, String message) throws IOException
	{
		try { action.run(); }
		catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message + " was accepted");
	}

	private static void check(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}

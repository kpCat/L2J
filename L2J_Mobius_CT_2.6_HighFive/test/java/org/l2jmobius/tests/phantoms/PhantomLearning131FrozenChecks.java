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

import org.l2jmobius.gameserver.phantoms.learning.ActionContinuationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionValueV4;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Entity;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Role;
import org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3;

/** DB-free contract checks for the TASK131 frozen evaluation policy. */
public final class PhantomLearning131FrozenChecks
{
	private static final String TRAIN_EPISODE = "a".repeat(64);
	private static final String SOURCE = "b".repeat(64);
	private static final String EVAL_EPISODE = "c".repeat(64);
	private static final List<String> SKILLS = List.of("1000:1", "7000:1");

	public static void main(String[] args) throws IOException
	{
		final var model = checkpoint();
		final Path scratch = Files.createTempDirectory(Path.of(".").toAbsolutePath().normalize(), "learning131-frozen-");
		try
		{
			final var lineage = ActionContinuationV4.initialize(scratch, model);
			checkFrozen(scratch, lineage.head(), model);
			System.out.println("LEARNING131_FROZEN_CONTRACT_GREEN TUNING=MODEL AUDIT=MODEL exploration=0 checkpointUnchanged=true");
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
	private static ActionValueV4 checkpoint() throws IOException
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

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;

/** TEST-only single decision owner. Proposals never grant native execution authority. */
public final class ActionExplorationV4
{
	public enum AuditControl
	{
		HEURISTIC, STOCK_PVE, STOCK_PVP;
		public boolean archived() { return this != HEURISTIC; }
	}

	public record Plan(int schemaVersion, String policyId, long seed, int cap, double epsilon, String split,
		String episode, String sourceSha, String modelSha, List<String> skills)
	{
		public Plan
		{
			skills = List.copyOf(skills);
			final boolean evaluation = "TUNING".equals(split) || "AUDIT".equals(split);
			final boolean controlAudit = "HEURISTIC_V2".equals(policyId) || "STOCK_PVE_V2".equals(policyId) || "STOCK_PVP_V2".equals(policyId);
			final boolean deterministic = seed == 0 && cap == 0 && Double.compare(epsilon, 0) == 0;
			final boolean validPolicy = switch (schemaVersion)
			{
				case 1 -> policyId != null && Set.of("COVERAGE_TEST_V1", "EPSILON_V1", "BASELINE_V1", "MODEL_V1").contains(policyId)
					&& cap >= 1 && cap <= 16 && (!policyId.equals("COVERAGE_TEST_V1") || cap == 1)
					&& Double.isFinite(epsilon) && epsilon >= .05 && epsilon <= .20
					&& ("TRAIN".equals(split) || evaluation && policyId.equals("BASELINE_V1"));
				case 2 -> ("MODEL_V2".equals(policyId) && evaluation && deterministic)
					|| (controlAudit && "AUDIT".equals(split) && deterministic)
					|| ("EPSILON_V2".equals(policyId) && "TRAIN".equals(split) && cap >= 1 && cap <= 16 && Double.isFinite(epsilon) && epsilon >= .05 && epsilon <= .20);
				default -> false;
			};
			if (!validPolicy || episode == null || !episode.matches("[a-f0-9]{64}") || !sourceSha.matches("[a-f0-9]{64}")
				|| !(controlAudit ? "NONE".equals(modelSha) : modelSha.matches("[a-f0-9]{64}"))
				|| skills.size() < 2 || skills.size() > 16 || new HashSet<>(skills).size() != skills.size() || skills.stream().anyMatch(s -> !s.matches("[1-9][0-9]*:[1-9][0-9]*")))
			{ throw new IllegalArgumentException("Invalid frozen V4 experiment plan"); }
		}
		boolean frozenInference() { return "MODEL_V2".equals(policyId); }
		public AuditControl auditControl()
		{
			return switch (policyId)
			{
				case "HEURISTIC_V2" -> AuditControl.HEURISTIC;
				case "STOCK_PVE_V2" -> AuditControl.STOCK_PVE;
				case "STOCK_PVP_V2" -> AuditControl.STOCK_PVP;
				default -> null;
			};
		}
		boolean epsilonExploration() { return "EPSILON_V1".equals(policyId) || "EPSILON_V2".equals(policyId); }
		boolean accepts(LegalActionV3 action)
		{
			final var control = auditControl();
			return (action.family() == LegalActionV3.Family.DAMAGE || schemaVersion == 2 && (control == null || !control.archived()) && action.family() == LegalActionV3.Family.HEAL)
				&& skills.contains(action.skillId() + ":" + action.skillLevel());
		}
		void write(DataOutput out) throws IOException
		{
			out.writeInt(schemaVersion); out.writeUTF(policyId); out.writeLong(seed); out.writeInt(cap); out.writeDouble(epsilon); out.writeUTF(split);
			out.writeUTF(episode); out.writeUTF(sourceSha); out.writeUTF(modelSha); out.writeInt(skills.size()); for (var s : skills) { out.writeUTF(s); }
		}
		static Plan read(DataInput in) throws IOException
		{
			int version = in.readInt(); String policy = in.readUTF(); long seed = in.readLong(); int cap = in.readInt(); double epsilon = in.readDouble();
			String split = in.readUTF(), episode = in.readUTF(), source = in.readUTF(), model = in.readUTF(); int n = in.readInt();
			if (n < 2 || n > 16) { throw new IllegalArgumentException("V4 known action bound"); }
			var skills = new ArrayList<String>(); for (int i = 0; i < n; i++) { skills.add(in.readUTF()); }
			return new Plan(version, policy, seed, cap, epsilon, split, episode, source, model, skills);
		}
		public byte[] bytes() { return encode(out -> write(out)); }
		public String sha256() { return TacticalObservationV3.hash(bytes()); }
	}
	public record Pick(int index, double propensity) { }
	public static Pick pick(int count, int stock, double epsilon, double draw)
	{
		if (count < 2 || count > 16 || stock < 0 || stock >= count || !Double.isFinite(epsilon) || epsilon < .05 || epsilon > .20 || !Double.isFinite(draw) || draw < 0 || draw >= 1)
		{ throw new IllegalArgumentException("Invalid epsilon distribution"); }
		if (draw < 1 - epsilon) { return new Pick(stock, 1 - epsilon); }
		int alternative = Math.min(count - 2, (int)((draw - (1 - epsilon)) / epsilon * (count - 1)));
		return new Pick(alternative >= stock ? alternative + 1 : alternative, epsilon / (count - 1));
	}
	private static double draw(Plan plan, long cursor) { return new SplittableRandom(plan.seed() + cursor).nextDouble(); }
	private static LegalActionV3 coverage(Plan plan, List<LegalActionV3> offered, LegalActionV3 stock)
	{
		return offered.stream().filter(a -> !a.equals(stock)).min(Comparator.comparingInt(a -> plan.skills().indexOf(a.skillId() + ":" + a.skillLevel()))).orElseThrow();
	}
	public record Decision(Plan plan, long cursor, TacticalObservationV3 observation, List<LegalActionV3> offered,
		LegalActionV3 stock, LegalActionV3 selected, String mode, double propensity, double draw, long capturedNanos, long deadline, String reason)
	{
		public Decision
		{
			offered = List.copyOf(offered); new TacticalReceiptV3.Attempt(observation, offered, selected, stock, 0);
			if (cursor < 0 || cursor >= 64 || capturedNanos < observation.capturedNanos() || reason == null || reason.length() > 128
				|| offered.stream().anyMatch(a -> !plan.accepts(a))
				|| stock.family() == LegalActionV3.Family.HEAL && offered.stream().anyMatch(a -> observation.entity(a.targetObjectId()).role() == TacticalObservationV3.Role.ENEMY))
			{ throw new IllegalArgumentException("Invalid V4 decision authority"); }
			final var archived = observation.stockSelection();
			final var stockControl = switch (archived)
			{
				case TacticalObservationV3.StockPveSelection ignored -> AuditControl.STOCK_PVE;
				case TacticalObservationV3.StockPvpSelection ignored -> AuditControl.STOCK_PVP;
				case null -> null;
			};
			final var audit = plan.auditControl();
			if (stockControl != (audit != null && audit.archived() ? audit : null))
			{
				throw new IllegalArgumentException("Archived observation is required only for the matching S control");
			}
			if (archived != null)
			{
				final var offeredSkills = offered.stream().map(action -> new SelectedSkill(action.skillId(), action.skillLevel())).toList();
				if (!archived.selected().equals(new SelectedSkill(stock.skillId(), stock.skillLevel()))
					|| !offeredSkills.equals(archived.ready().stream().filter(offeredSkills::contains).toList()))
				{
					throw new IllegalArgumentException("Archived input does not reproduce the recorded stock action");
				}
			}
			if (mode.equals("EXPLORE_COVERAGE"))
			{
				if (!plan.policyId().equals("COVERAGE_TEST_V1") || propensity != 1 || draw != 0 || !selected.equals(coverage(plan, offered, stock)) || capturedNanos >= deadline)
				{ throw new IllegalArgumentException("Coverage decision mismatch"); }
			}
			else if (mode.equals("EXPLORE_EPSILON"))
			{
				var p = pick(offered.size(), offered.indexOf(stock), plan.epsilon(), draw);
				if (!plan.epsilonExploration() || draw != ActionExplorationV4.draw(plan, cursor) || propensity != p.propensity() || !selected.equals(offered.get(p.index())) || capturedNanos >= deadline)
				{ throw new IllegalArgumentException("Epsilon decision mismatch"); }
			}
			else if (!Set.of("BASELINE", "MODEL").contains(mode) || propensity != 1 || draw != 0 || mode.equals("BASELINE") && !selected.equals(stock)
				|| mode.equals("MODEL") && (plan.frozenInference() ? capturedNanos >= deadline : !plan.policyId().equals("MODEL_V1")))
			{ throw new IllegalArgumentException("Stock/model decision mismatch"); }
		}
		public byte[] bytes()
		{
			return encode(out -> { out.writeInt(0x45583231); plan.write(out); out.writeLong(cursor); byte[] obs = observation.bytes(); out.writeInt(obs.length); out.write(obs);
				out.writeInt(offered.size()); for (var a : offered) { a.write(out); } stock.write(out); selected.write(out); out.writeUTF(mode); out.writeDouble(propensity); out.writeDouble(draw);
				out.writeLong(capturedNanos); out.writeLong(deadline); out.writeUTF(reason); });
		}
		public String sha256() { return TacticalObservationV3.hash(bytes()); }
		public static Decision restore(byte[] bytes)
		{
			if (bytes == null || bytes.length > 500000) { throw new IllegalArgumentException("Decision size"); }
			try (var in = new DataInputStream(new ByteArrayInputStream(bytes)))
			{
				if (in.readInt() != 0x45583231) { throw new IllegalArgumentException("Decision schema"); }
				var plan = Plan.read(in); long cursor = in.readLong(); int length = in.readInt(); if (length < 1 || length > 450000) { throw new IllegalArgumentException("Observation size"); }
				var obs = TacticalObservationV3.restore(in.readNBytes(length)); int n = in.readInt(); if (n < 2 || n > 16) { throw new IllegalArgumentException("Offer size"); }
				var offered = new ArrayList<LegalActionV3>(); for (int i = 0; i < n; i++) { offered.add(LegalActionV3.read(in)); }
				var result = new Decision(plan, cursor, obs, offered, LegalActionV3.read(in), LegalActionV3.read(in), in.readUTF(), in.readDouble(), in.readDouble(), in.readLong(), in.readLong(), in.readUTF());
				if (in.available() != 0) { throw new IllegalArgumentException("Trailing decision bytes"); } return result;
			}
			catch (IOException e) { throw new IllegalArgumentException("Incomplete decision", e); }
		}
	}
	@FunctionalInterface private interface Writer { void write(DataOutputStream out) throws IOException; }
	private static byte[] encode(Writer writer)
	{
		try { var bytes = new ByteArrayOutputStream(); try (var out = new DataOutputStream(bytes)) { writer.write(out); } return bytes.toByteArray(); }
		catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}
	private final Plan _plan;
	private final ActionValueV4 _frozenModel;
	private final List<Decision> _decisions = new ArrayList<>();
	private int _experiments;
	private boolean _complete = true;
	public ActionExplorationV4(Plan plan) { this(plan, null); }
	private ActionExplorationV4(Plan plan, ActionValueV4 frozenModel)
	{
		_plan = Objects.requireNonNull(plan);
		if (_plan.frozenInference() != (frozenModel != null))
		{ throw new IllegalArgumentException("Frozen evaluation requires committed lineage admission"); }
		_frozenModel = frozenModel;
	}
	/** Admit against an externally pinned plan and published continuation HEAD before any scoring. */
	public static ActionExplorationV4 frozen(Plan plan, Path lineageRoot, String expectedHead) throws IOException
	{
		requireFrozenPlan(plan, expectedHead);
		final var lineage = ActionContinuationV4.load(lineageRoot);
		if (!lineage.head().equals(expectedHead)) { throw new IllegalArgumentException("Frozen evaluation requires the exact current HEAD"); }
		return admitFrozen(plan, lineage);
	}

	/** Offline replay may bind to an older checkpoint on the current committed lineage. */
	public static ActionExplorationV4 frozenCommitted(Plan plan, Path lineageRoot, String checkpointHead) throws IOException
	{
		requireFrozenPlan(plan, checkpointHead);
		return admitFrozen(plan, ActionContinuationV4.committedState(lineageRoot, checkpointHead));
	}

	private static void requireFrozenPlan(Plan plan, String head)
	{
		if (plan == null || !plan.frozenInference() || head == null || !head.matches("[a-f0-9]{64}"))
		{ throw new IllegalArgumentException("Frozen evaluation requires a versioned plan and exact lineage HEAD"); }
	}

	private static ActionExplorationV4 admitFrozen(Plan plan, ActionContinuationV4.State lineage)
	{
		final var model = lineage.model();
		if (model.updates() == 0 || !plan.modelSha().equals(model.sha256())
			|| lineage.episodes().containsKey(plan.episode()))
		{ throw new IllegalArgumentException("Frozen evaluation requires the pinned trained checkpoint and a fresh episode"); }
		return new ActionExplorationV4(plan, model);
	}
	public Plan plan() { return _plan; }
	/** Historical whole-buffer validation precedes all training credit. */
	public static void validateLog(List<Decision> decisions)
	{
		final var plan = validateLogStructure(decisions);
		if (plan.frozenInference() || plan.auditControl() != null)
		{ throw new IllegalArgumentException("Frozen evaluation log requires its own policy verifier"); }
	}

	/** A model-free arm keeps its heuristic even when collecting the snapshot exceeds the ranking budget. */
	public static void verifyHeuristicLog(List<Decision> decisions)
	{
		verifyControlLog(AuditControl.HEURISTIC, decisions);
	}

	public static void verifyControlLog(AuditControl control, List<Decision> decisions)
	{
		if (control == null || validateLogStructure(decisions).auditControl() != control || decisions.stream().anyMatch(decision ->
		{
			final boolean late = decision.capturedNanos() >= decision.deadline();
			final boolean expectedReason = late ? decision.reason().equals("BUDGET_5MS")
				: decision.reason().equals("CONTROL_STOCK") || decision.reason().equals("SAFETY_STOCK");
			return !decision.mode().equals("BASELINE") || !decision.selected().equals(decision.stock()) || !expectedReason;
		}))
		{
			throw new IllegalArgumentException("Incomplete or substituted model-free AUDIT decision");
		}
	}

	/** The caller supplies the already authenticated decision log for the pinned evaluation plan. */
	public void verifyFrozenLog(List<Decision> decisions)
	{
		final var log = List.copyOf(decisions);
		if (_frozenModel == null || !_plan.equals(validateLogStructure(log)))
		{ throw new IllegalArgumentException("Frozen evaluation log does not match the admitted plan"); }
		for (var decision : log)
		{
			if (decision.mode().equals("MODEL"))
			{
				final var replay = TacticalPolicyV3.actionValueChoice(TacticalPolicyV3.Mode.ON, _frozenModel,
					decision.observation(), decision.offered(), decision.stock(), false, Long.MAX_VALUE, true);
				if (!ranked(replay.reason()) || !replay.reason().equals(decision.reason()) || !replay.selected().equals(decision.selected()))
				{ throw new IllegalArgumentException("Frozen evaluation decision differs from the pinned model"); }
			}
			else if (!decision.reason().equals("CRITICAL_STOCK") || decision.capturedNanos() >= decision.deadline())
			{ throw new IllegalArgumentException("Incomplete frozen evaluation inference"); }
		}
	}

	private static Plan validateLogStructure(List<Decision> decisions)
	{
		if (decisions.isEmpty() || decisions.size() > 64) { throw new IllegalArgumentException("Incomplete decision log"); }
		var plan = decisions.getFirst().plan(); var generations = new HashSet<Long>(); int alternatives = 0;
		for (int i = 0; i < decisions.size(); i++)
		{
			var d = decisions.get(i);
			if (d.cursor() != i || !d.plan().equals(plan) || !generations.add(d.observation().decisionId()) || alternatives >= plan.cap() && d.mode().startsWith("EXPLORE")
				|| (plan.frozenInference() || plan.auditControl() != null) && i > 0 && d.observation().decisionId() <= decisions.get(i - 1).observation().decisionId())
			{ throw new IllegalArgumentException("Incomplete, duplicate or over-cap experiment log"); }
			if (d.mode().startsWith("EXPLORE") && !d.selected().equals(d.stock())) { alternatives++; }
		}
		return plan;
	}
	public synchronized List<Decision> decisions() { if (!_complete) { throw new IllegalArgumentException("Incomplete decision buffer"); } return List.copyOf(_decisions); }
	public synchronized Decision choose(TacticalObservationV3 obs, List<LegalActionV3> offered, LegalActionV3 stock, boolean safe, long deadline)
	{
		return _plan.frozenInference() ? chooseFrozen(obs, offered, stock, safe, deadline).decision() : choose(obs, offered, stock, stock, safe, deadline);
	}
	record FrozenChoice(Decision decision, TacticalPolicyV3.ActionValueDecision inference) { }
	synchronized FrozenChoice chooseFrozen(TacticalObservationV3 obs, List<LegalActionV3> offered, LegalActionV3 stock, boolean safe, long deadline)
	{
		if (_frozenModel == null || !_decisions.isEmpty() && obs.decisionId() <= _decisions.getLast().observation().decisionId())
		{ throw new IllegalArgumentException("Missing frozen scope or stale evaluation observation"); }
		requireSpace();

		final var inference = TacticalPolicyV3.actionValueChoice(TacticalPolicyV3.Mode.ON, _frozenModel, obs, offered, stock, !safe, deadline, true);
		final long stamp = System.nanoTime();
		final String reason = inference.reason();
		final boolean ranked = stamp < deadline && ranked(reason);
		var decision = new Decision(_plan, _decisions.size(), obs, offered, stock, ranked ? inference.selected() : stock,
			ranked ? "MODEL" : "BASELINE", 1, 0, stamp, deadline, stamp >= deadline ? "BUDGET_5MS" : reason);
		if (ranked && System.nanoTime() >= deadline)
		{
			decision = new Decision(_plan, _decisions.size(), obs, offered, stock, stock, "BASELINE", 1, 0, System.nanoTime(), deadline, "BUDGET_5MS");
		}
		_decisions.add(decision);
		return new FrozenChoice(decision, inference);
	}
	private static boolean ranked(String reason)
	{
		return reason.equals("V4_REORDERED") || reason.equals("V4_STOCK") || reason.equals("MARGIN");
	}
	private void requireSpace()
	{
		if (_decisions.size() >= 64) { _complete = false; throw new IllegalArgumentException("Decision buffer exhausted"); }
	}
	public synchronized Decision choose(TacticalObservationV3 obs, List<LegalActionV3> offered, LegalActionV3 stock, LegalActionV3 model, boolean safe, long deadline)
	{
		if (_plan.frozenInference()) { throw new IllegalArgumentException("Frozen evaluation computes its model selection"); }
		requireSpace();
		long cursor = _decisions.size(); String mode = "BASELINE", reason = "CONTROL_STOCK"; double probability = 1, random = 0; var selected = stock;
		if (!safe) { reason = "SAFETY_STOCK"; }
		else if (System.nanoTime() >= deadline) { reason = "BUDGET_5MS"; }
		else if (_plan.policyId().equals("MODEL_V1")) { mode = "MODEL"; selected = model; reason = "MODEL"; }
		else if (_experiments < _plan.cap() && _plan.policyId().equals("COVERAGE_TEST_V1")) { mode = "EXPLORE_COVERAGE"; selected = coverage(_plan, offered, stock); }
		else if (_experiments < _plan.cap() && _plan.epsilonExploration())
		{
			mode = "EXPLORE_EPSILON"; random = draw(_plan, cursor); var p = pick(offered.size(), offered.indexOf(stock), _plan.epsilon(), random); selected = offered.get(p.index()); probability = p.propensity();
		}
		long stamp = System.nanoTime();
		if (stamp >= deadline) { selected = stock; mode = "BASELINE"; probability = 1; random = 0; reason = "BUDGET_5MS"; }
		var d = new Decision(_plan, cursor, obs, offered, stock, selected, mode, probability, random, stamp, deadline, reason);
		if (System.nanoTime() >= deadline && !mode.equals("BASELINE")) { d = new Decision(_plan, cursor, obs, offered, stock, stock, "BASELINE", 1, 0, System.nanoTime(), deadline, "BUDGET_5MS"); }
		if (!d.selected().equals(stock) && d.mode().startsWith("EXPLORE")) { _experiments++; }
		_decisions.add(d); return d;
	}
}

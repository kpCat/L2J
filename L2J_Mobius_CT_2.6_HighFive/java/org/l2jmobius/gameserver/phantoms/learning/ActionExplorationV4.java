/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.util.*;

/** TEST-only single decision owner. Proposals never grant native execution authority. */
public final class ActionExplorationV4
{
	public record Plan(int schemaVersion, String policyId, long seed, int cap, double epsilon, String split,
		String episode, String sourceSha, String modelSha, List<String> skills)
	{
		public Plan
		{
			skills = List.copyOf(skills);
			if (schemaVersion != 1 || !Set.of("COVERAGE_TEST_V1", "EPSILON_V1", "BASELINE_V1", "MODEL_V1").contains(policyId)
				|| cap < 1 || cap > 16 || policyId.equals("COVERAGE_TEST_V1") && cap != 1 || !Double.isFinite(epsilon) || epsilon < .05 || epsilon > .20
				|| !("TRAIN".equals(split) || Set.of("TUNING", "AUDIT").contains(split) && policyId.equals("BASELINE_V1")) || episode == null || !episode.matches("[a-f0-9]{64}") || !sourceSha.matches("[a-f0-9]{64}") || !modelSha.matches("[a-f0-9]{64}")
				|| skills.size() < 2 || skills.size() > 16 || new HashSet<>(skills).size() != skills.size() || skills.stream().anyMatch(s -> !s.matches("[1-9][0-9]*:[1-9][0-9]*")))
			{ throw new IllegalArgumentException("Invalid frozen V4 experiment plan"); }
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
				|| offered.stream().anyMatch(a -> a.family() != LegalActionV3.Family.DAMAGE || !plan.skills().contains(a.skillId() + ":" + a.skillLevel())))
			{ throw new IllegalArgumentException("Invalid V4 decision authority"); }
			if (mode.equals("EXPLORE_COVERAGE"))
			{
				if (!plan.policyId().equals("COVERAGE_TEST_V1") || propensity != 1 || draw != 0 || !selected.equals(coverage(plan, offered, stock)) || capturedNanos >= deadline)
				{ throw new IllegalArgumentException("Coverage decision mismatch"); }
			}
			else if (mode.equals("EXPLORE_EPSILON"))
			{
				var p = pick(offered.size(), offered.indexOf(stock), plan.epsilon(), draw);
				if (!plan.policyId().equals("EPSILON_V1") || draw != ActionExplorationV4.draw(plan, cursor) || propensity != p.propensity() || !selected.equals(offered.get(p.index())) || capturedNanos >= deadline)
				{ throw new IllegalArgumentException("Epsilon decision mismatch"); }
			}
			else if (!Set.of("BASELINE", "MODEL").contains(mode) || propensity != 1 || draw != 0 || mode.equals("BASELINE") && !selected.equals(stock)
				|| mode.equals("MODEL") && !plan.policyId().equals("MODEL_V1"))
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
	private final List<Decision> _decisions = new ArrayList<>();
	private int _experiments;
	private boolean _complete = true;
	public ActionExplorationV4(Plan plan) { _plan = Objects.requireNonNull(plan); }
	public Plan plan() { return _plan; }
	/** Whole-buffer validation precedes all training credit; cursors cannot be omitted or duplicated. */
	public static void validateLog(List<Decision> decisions)
	{
		if (decisions.isEmpty() || decisions.size() > 64) { throw new IllegalArgumentException("Incomplete decision log"); }
		var plan = decisions.getFirst().plan(); var generations = new HashSet<Long>(); int alternatives = 0;
		for (int i = 0; i < decisions.size(); i++)
		{
			var d = decisions.get(i);
			if (d.cursor() != i || !d.plan().equals(plan) || !generations.add(d.observation().decisionId()) || alternatives >= plan.cap() && d.mode().startsWith("EXPLORE"))
			{ throw new IllegalArgumentException("Incomplete, duplicate or over-cap experiment log"); }
			if (d.mode().startsWith("EXPLORE") && !d.selected().equals(d.stock())) { alternatives++; }
		}
	}
	public synchronized List<Decision> decisions() { if (!_complete) { throw new IllegalArgumentException("Incomplete decision buffer"); } return List.copyOf(_decisions); }
	public synchronized Decision choose(TacticalObservationV3 obs, List<LegalActionV3> offered, LegalActionV3 stock, boolean safe, long deadline)
	{ return choose(obs, offered, stock, stock, safe, deadline); }
	public synchronized Decision choose(TacticalObservationV3 obs, List<LegalActionV3> offered, LegalActionV3 stock, LegalActionV3 model, boolean safe, long deadline)
	{
		if (_decisions.size() >= 64) { _complete = false; throw new IllegalArgumentException("Decision buffer exhausted"); }
		long cursor = _decisions.size(); String mode = "BASELINE", reason = "CONTROL_STOCK"; double probability = 1, random = 0; var selected = stock;
		if (!safe) { reason = "SAFETY_STOCK"; }
		else if (System.nanoTime() >= deadline) { reason = "BUDGET_5MS"; }
		else if (_plan.policyId().equals("MODEL_V1")) { mode = "MODEL"; selected = model; reason = "MODEL"; }
		else if (_experiments < _plan.cap() && _plan.policyId().equals("COVERAGE_TEST_V1")) { mode = "EXPLORE_COVERAGE"; selected = coverage(_plan, offered, stock); }
		else if (_experiments < _plan.cap() && _plan.policyId().equals("EPSILON_V1"))
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

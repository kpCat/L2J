/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;

/** Generic exact-cell executed-action targets. V1/008 history and model codecs are unchanged. */
public final class ActionAdvantageV2
{
	public static final String OBJECTIVE = "ACQUIRED_ACTION_ADVANTAGE_V2";
	public static final String DATASET_PREFIX = "autonomous009-";
	public static final String REVISION = "009/exact-preCUT-cell/leave-own-block-out-A3/.70terminal+.30local/scale4/class-episode-balance/v2";
	public static final String CONFIG_SHA = hash(REVISION + "/TRAIN-only-controls/one-paid-credit/conditional-shortlist-IPSclip4/class-sum-equal/global-cap4/TRAIN-mean-normalization/constant=class-balanced-TRAIN-mean");
	private static final int MAGIC = 0x41443039, MAX_BYTES = 64 * 1024 * 1024, MAX_SAMPLES = 512;
	public record Sample(TrainExplorationV1.Selection selection, TerminalRewardReceiptV1 terminal)
	{
		public Sample
		{
			if (selection == null || terminal == null || !selection.episode().equals(terminal.episode().id()) || !selection.split().equals(terminal.split())
				|| !terminal.dataset().matches(DATASET_PREFIX + "[A-Za-z0-9_-]{1,80}") || !selection.fallback().isEmpty()
				|| terminal.actions().stream().filter(action -> selection.matches(action.row().receipt())).count() != 1)
			{ throw new IllegalArgumentException("V2 requires one exact selected paid action and native terminal boundary"); }
		}
		public TacticalStoreV3.Row row() { return terminal.actions().stream().filter(action -> selection.matches(action.row().receipt())).findFirst().orElseThrow().row(); }
		public String actionKey() { return row().actionKey(); }
		public float utility()
		{
			final var receipt = row().receipt(); final var maximum = selection.observation().entity(selection.selected().targetObjectId()).fact(Field.MAX_HP);
			if (!maximum.known() || maximum.value() <= 0) { throw new IllegalArgumentException("Unknown native effect scale"); }
			final double local = Math.clamp(2 * receipt.actualHp() / maximum.value() - receipt.actualMp() / 500 - receipt.elapsedNanos() / 1e10, -1, 1);
			return (float) (.70 * terminal.reward() + .30 * local);
		}
		/** cell is attested by the sealed pre-CUT manifest; receipt identities stay in Row.contextKey. */
		public String context()
		{
			final var observation = selection.observation(); final var shape = new ArrayList<String>();
			for (var action : selection.offered())
			{
				final var target = observation.entity(action.targetObjectId());
				shape.add(action.family() + ":" + action.skillId() + ":" + action.skillLevel() + ":" + action.mpCost() + ":" + action.castMillis()
					+ ":" + action.range() + ":" + target.role() + ":" + target.classId() + ":" + target.fact(Field.MAX_HP));
			}
			Collections.sort(shape);
			return hash(selection.cell() + "/" + observation.self().classId() + "/" + observation.self().fact(Field.LEVEL) + "/" + shape);
		}
	}
	public record Target(Sample sample, float baseline, float utility, float advantage, double weight, int baselineCount, String baselineSha)
	{
		public TacticalModelV3.Example example()
		{ final var s = sample.selection(); return new TacticalModelV3.Example(s.observation().state(), s.selected().features(s.observation()), advantage); }
	}
	public record Frozen(String runId, String datasetSha, List<Sample> samples)
	{
		public Frozen
		{
			if (runId == null || !runId.matches("[a-f0-9]{32}")) { throw new IllegalArgumentException("V2 run identity"); }
			samples = validate(samples);
			if (datasetSha == null || !TacticalObservationV3.hash(pack(samples)).equals(datasetSha)) { throw new IllegalArgumentException("V2 dataset checksum"); }
		}
		public byte[] bytes() { return pack(samples); }
		public List<Target> targets()
		{
			final var controls = samples.stream().filter(s -> s.selection().split().equals("TRAIN") && !s.selection().experimental()).toList();
			final var raw = new ArrayList<Target>();
			for (var sample : samples)
			{
				final var selection = sample.selection(); if (selection.split().equals("TRAIN") && !selection.experimental()) { continue; }
				final var baseline = controls.stream().filter(a -> a.context().equals(sample.context())
					&& (!selection.split().equals("TRAIN") || !a.selection().block().equals(selection.block()))).toList();
				if (baseline.stream().map(a -> a.selection().block()).distinct().count() < 3) { continue; }
				final float b = (float) baseline.stream().mapToDouble(Sample::utility).average().orElseThrow(), u = sample.utility();
				final double ips = Math.min(4, 1 / (selection.offered().size() * selection.propensity()));
				final String sha = hash(baseline.stream().map(a -> a.terminal().naturalKey() + ":" + a.utility()).sorted().toList().toString());
				raw.add(new Target(sample, b, u, (float) Math.clamp(4 * (u - b), -1, 1), ips, baseline.size(), sha));
			}
			final var totals = new HashMap<Integer, Double>();
			for (var target : raw) { if (gradient(target)) { totals.merge(target.sample().selection().observation().self().classId(), target.weight(), Double::sum); } }
			final int trainCount = (int) raw.stream().filter(ActionAdvantageV2::gradient).count();
			final double maximum = raw.stream().filter(ActionAdvantageV2::gradient).mapToDouble(t -> t.weight() / totals.get(t.sample().selection().observation().self().classId())).max().orElse(1);
			final double scale = totals.isEmpty() ? 1 : Math.min((double) trainCount / totals.size(), 4 / maximum);
			return raw.stream().map(t -> new Target(t.sample(), t.baseline(), t.utility(), t.advantage(), gradient(t)
				? t.weight() / totals.get(t.sample().selection().observation().self().classId()) * scale : 1, t.baselineCount(), t.baselineSha())).toList();
		}
		public List<TacticalModelV3.WeightedExample> weightedExamples()
		{ return targets().stream().filter(ActionAdvantageV2::gradient).map(t -> new TacticalModelV3.WeightedExample(t.example(), t.weight())).toList(); }
		public List<TacticalModelV3.Example> examples(String split)
		{
			if (!Set.of("TUNING", "AUDIT").contains(split)) { throw new IllegalArgumentException("Held-out V2 examples only"); }
			return targets().stream().filter(t -> t.sample().selection().split().equals(split)).map(Target::example).toList();
		}
		public List<TacticalStoreV3.Row> sourceRows() { return samples.stream().flatMap(s -> s.terminal().actions().stream()).map(TerminalRewardReceiptV1.TimedAction::row).toList(); }
		public boolean sufficient()
		{
			final var train = targets().stream().filter(ActionAdvantageV2::gradient).toList();
			return train.size() >= 16 && examples("TUNING").size() >= 8 && examples("AUDIT").size() >= 8
				&& train.stream().map(t -> t.sample().selection().observation().self().classId()).distinct().count() >= 2
				&& train.stream().map(t -> t.sample().terminal().episode().scope()).distinct().count() >= 2
				&& train.stream().anyMatch(t -> !t.sample().selection().selected().equals(t.sample().selection().stock()));
		}
		public double constantLoss()
		{
			final var train = weightedExamples();
			final double total = train.stream().mapToDouble(TacticalModelV3.WeightedExample::weight).sum();
			if (total <= 0) { throw new IllegalArgumentException("No class-balanced TRAIN benchmark"); }
			final double mean = train.stream().mapToDouble(w -> w.weight() * w.example().reward()).sum() / total;
			return examples("AUDIT").stream().mapToDouble(e -> Math.pow(e.reward() - mean, 2)).average().orElseThrow();
		}
		public String metrics()
		{
			return "{\"objective\":" + TrainingStore.quote(OBJECTIVE) + ",\"configSha\":" + TrainingStore.quote(CONFIG_SHA) + ",\"datasetSha\":" + TrainingStore.quote(datasetSha)
				+ ",\"samples\":" + samples.size() + ",\"train\":" + weightedExamples().size() + ",\"tuning\":" + examples("TUNING").size() + ",\"audit\":" + examples("AUDIT").size()
				+ ",\"baselineData\":\"EXACT_CELL_TRAIN_A_ONLY_LEAVE_OWN_BLOCK_OUT_MIN3\",\"propensity\":\"CONDITIONAL_ON_FROZEN_SHORTLIST_CLIPPED_IPS\",\"classBalanced\":true,\"maximumPaidCreditsPerEpisode\":1,\"counterfactualLabels\":false,\"productionPromotion\":false}";
		}
	}
	private static boolean gradient(Target target) { return target.sample().selection().split().equals("TRAIN") && target.sample().selection().experimental(); }
	private static List<Sample> validate(List<Sample> input)
	{
		if (input == null || input.isEmpty() || input.size() > MAX_SAMPLES || input.stream().anyMatch(Objects::isNull)) { throw new IllegalArgumentException("V2 capacity"); }
		final var samples = input.stream().sorted(Comparator.comparing(s -> s.terminal().naturalKey())).toList();
		final var episodes = new HashSet<String>(); final var actions = new HashSet<String>(); final var events = new HashSet<String>();
		final var controls = new HashSet<String>(); final var blocks = new HashMap<String, String>();
		final var first = samples.getFirst();
		for (var sample : samples)
		{
			if (!episodes.add(sample.terminal().naturalKey()) || !actions.add(sample.actionKey()) || !first.terminal().boundary().installation().equals(sample.terminal().boundary().installation())
				|| !first.terminal().config().sha256().equals(sample.terminal().config().sha256()) || !first.terminal().dataset().equals(sample.terminal().dataset())
				|| first.selection().modelVersion() != sample.selection().modelVersion() || !first.selection().modelSha().equals(sample.selection().modelSha()))
			{ throw new IllegalArgumentException("Duplicate or foreign V2 native source/model"); }
			final var selection = sample.selection(); final String previous = blocks.putIfAbsent(selection.block(), selection.split());
			if (previous != null && !previous.equals(selection.split())) { throw new IllegalArgumentException("V2 block leaked across splits"); }
			if (selection.split().equals("TRAIN") && !selection.experimental() && !controls.add(sample.context() + "/" + selection.block()))
			{ throw new IllegalArgumentException("Repeated control block is not independent evidence"); }
			for (var action : sample.terminal().actions()) { if (!events.add(action.row().eventKey())) { throw new IllegalArgumentException("Native action credited twice"); } }
			sample.utility(); // Known scale required before freezing, including negative outcomes.
		}
		TacticalStoreV3.validateDataset(samples.stream().flatMap(s -> s.terminal().actions().stream()).map(TerminalRewardReceiptV1.TimedAction::row).toList());
		return List.copyOf(samples);
	}
	public static Frozen freeze(String runId, List<Sample> samples) { final var valid = validate(samples); return new Frozen(runId, TacticalObservationV3.hash(pack(valid)), valid); }
	private static byte[] pack(List<Sample> samples)
	{
		try
		{
			final var buffer = new ByteArrayOutputStream(); final var out = new DataOutputStream(buffer);
			out.writeInt(MAGIC); out.writeInt(2); out.writeUTF(REVISION); out.writeUTF(CONFIG_SHA); out.writeInt(samples.size());
			for (var sample : samples)
			{
				blob(out, sample.selection().bytes()); out.writeUTF(sample.terminal().sha256()); blob(out, sample.terminal().bytes());
				if (buffer.size() > MAX_BYTES) { throw new IllegalArgumentException("V2 byte capacity"); }
			}
			out.flush(); return buffer.toByteArray();
		}
		catch (IOException failure) { throw new IllegalArgumentException("V2 encoding", failure); }
	}
	public static Frozen restore(String runId, byte[] bytes, String expectedSha)
	{
		if (bytes == null || bytes.length > MAX_BYTES || !TacticalObservationV3.hash(bytes).equals(expectedSha)) { throw new IllegalArgumentException("V2 SHA changed"); }
		try (var in = new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			if (in.readInt() != MAGIC || in.readInt() != 2 || !in.readUTF().equals(REVISION) || !in.readUTF().equals(CONFIG_SHA)) { throw new IllegalArgumentException("V2 revision changed"); }
			final int count = in.readInt(); if (count < 1 || count > MAX_SAMPLES) { throw new IllegalArgumentException("V2 count"); }
			final var samples = new ArrayList<Sample>();
			for (int index = 0; index < count; index++)
			{ final var selection = TrainExplorationV1.Selection.restore(blob(in, 1000000)); final String sha = in.readUTF(); samples.add(new Sample(selection, TerminalRewardReceiptV1.restore(blob(in, TerminalRewardReceiptV1.MAX_BYTES), sha))); }
			final var result = new Frozen(runId, expectedSha, samples);
			if (in.available() != 0 || !Arrays.equals(result.bytes(), bytes)) { throw new IllegalArgumentException("Noncanonical V2 snapshot"); } return result;
		}
		catch (IOException failure) { throw new IllegalArgumentException("V2 snapshot invalid", failure); }
	}
	private static void blob(DataOutputStream out, byte[] bytes) throws IOException { out.writeInt(bytes.length); out.write(bytes); }
	private static byte[] blob(DataInputStream in, int maximum) throws IOException
	{ final int count = in.readInt(); if (count < 1 || count > maximum || count > in.available()) { throw new IllegalArgumentException("V2 blob bound"); } return in.readNBytes(count); }
	private static String hash(String value) { return TacticalObservationV3.hash(value.getBytes(StandardCharsets.UTF_8)); }
	private ActionAdvantageV2() { }
}

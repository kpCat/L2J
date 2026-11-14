/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.util.List;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.io.*;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;

/** Offline factual residual utility. A baseline forecast is never a native counterfactual receipt. */
public final class ActionAdvantage008
{
	public static final String CAMPAIGN = "learning008-pve-generalization-v1", DATASET = "learning008-residual-v1";
	public static final long TEACHER_VERSION = 11;
	public static final String TEACHER_SHA = "7de6f5b6bd51f71a8bfce143422139e6b18da44cf81e6ea81f91728b0da206d0";
	public static final String PARENT_ADAM_SHA = "f13d2f73ed7d0f3ffd38534b8ad413966b64cf7ba7c980a6ade2318da019d74d";
	public static final String PARENT_WEIGHTS_SHA = "6f3dfa43ef8a8335c9ebee16498d7a6406de629c6a85bdd66755675fb55b9546";
	public static final String OBJECTIVE = "FACTUAL_RESIDUAL_UTILITY_008";
	public static final String REVISION = "008/007U/scale4/initial-TRAIN-A-exact3-else-class-balanced-linear192-ridge.01/LOBO/max2-paid/episode-class-IPSclip4/v1";
	public static final String CONFIG_SHA = hash(REVISION);
	public record Control(String key, String cell, String block, String split, int classId, float[] features, double utility)
	{
		public Control
		{
			features = features.clone();
			if (!"TRAIN".equals(split) || classId != 94 && classId != 103 || features.length != 192 || !Double.isFinite(utility) || Math.abs(utility) > 1)
			{ throw new IllegalArgumentException("008 baseline requires paid TRAIN_A94/103 controls"); }
			for (float value : features) { if (!Float.isFinite(value) || Math.abs(value) > 1) { throw new IllegalArgumentException("Invalid canonical baseline feature"); } }
		}
		@Override public float[] features() { return features.clone(); }
	}
	public record Forecast(double value, String sourceSha, int controls, int cells, double nearestSquaredDistance) { }
	public static Forecast baseline(List<Control> source, String excludedBlock, float[] query)
	{
		if (source == null || source.size() > 128 || query == null || query.length != 192 || excludedBlock == null) { throw new IllegalArgumentException("008 baseline bound"); }
		for (float value : query) { if (!Float.isFinite(value) || Math.abs(value) > 1) { throw new IllegalArgumentException("Invalid baseline query"); } }
		if (source.stream().map(Control::key).distinct().count() != source.size()) { throw new IllegalArgumentException("Paid baseline control reused"); }
		var controls = source.stream().filter(c -> !c.block().equals(excludedBlock)).sorted(Comparator.comparing(Control::key)).toList();
		var classes = new HashMap<Integer, Integer>(); controls.forEach(c -> classes.merge(c.classId(), 1, Integer::sum));
		if (controls.size() < 4 || classes.size() != 2) { throw new IllegalArgumentException("Independent two-class TRAIN baseline missing"); }
		int n = controls.size(); var matrix = new double[n][n + 1]; var scale = new double[n];
		for (int i = 0; i < n; i++) { scale[i] = Math.sqrt(n / (2.0 * classes.get(controls.get(i).classId()))); }
		for (int i = 0; i < n; i++)
		{
			for (int j = 0; j < n; j++) { matrix[i][j] = scale[i] * kernel(controls.get(i).features, controls.get(j).features) * scale[j] + (i == j ? .01 : 0); }
			matrix[i][n] = scale[i] * controls.get(i).utility();
		}
		// Bounded dual ridge solve. Fixed lambda and input schema are pinned before held-out data.
		for (int column = 0; column < n; column++)
		{
			int pivot = column; for (int row = column + 1; row < n; row++) { if (Math.abs(matrix[row][column]) > Math.abs(matrix[pivot][column])) { pivot = row; } }
			var swap = matrix[pivot]; matrix[pivot] = matrix[column]; matrix[column] = swap;
			double divisor = matrix[column][column]; if (!Double.isFinite(divisor) || Math.abs(divisor) < 1e-12) { throw new IllegalArgumentException("Singular TRAIN baseline"); }
			for (int j = column; j <= n; j++) { matrix[column][j] /= divisor; }
			for (int row = 0; row < n; row++)
			{
				if (row == column) { continue; } double multiplier = matrix[row][column];
				for (int j = column; j <= n; j++) { matrix[row][j] -= multiplier * matrix[column][j]; }
			}
		}
		double prediction = 0, nearest = Double.POSITIVE_INFINITY; var provenance = new StringBuilder("008/TRAIN_A/class-balanced/linear-kernel192/ridge.01/");
		for (int i = 0; i < n; i++)
		{
			var c = controls.get(i); prediction += scale[i] * kernel(query, c.features) * matrix[i][n]; double distance = 0;
			for (int j = 0; j < 192; j++) { distance += Math.pow(query[j] - c.features[j], 2); }
			nearest = Math.min(nearest, distance / 192);
			provenance.append(c.key()).append('/').append(c.cell()).append('/').append(c.block()).append('/').append(c.classId())
				.append('/').append(Double.toHexString(c.utility())).append('/').append(Arrays.toString(c.features)).append('\n');
		}
		if (!Double.isFinite(prediction)) { throw new IllegalArgumentException("Nonfinite TRAIN baseline"); }
		return new Forecast(Math.clamp(prediction, -1, 1), NeuralPolicy.sha256(provenance.toString().getBytes(StandardCharsets.UTF_8)), n,
			(int) controls.stream().map(Control::cell).distinct().count(), nearest);
	}
	private static double kernel(float[] left, float[] right)
	{
		double value = 1; for (int i = 0; i < 192; i++) { value += left[i] * (double) right[i] / 192; } return value;
	}
	public record Sample(TrainExplorationV1.Selection selection, TerminalRewardReceiptV1 terminal)
	{
		public Sample
		{
			if (selection == null || terminal == null || !selection.episode().equals(terminal.episode().id()) || !selection.split().equals(terminal.split())
				|| !DATASET.equals(terminal.dataset()) || !selection.fallback().isEmpty() || selection.modelVersion() != TEACHER_VERSION || !selection.modelSha().equals(TEACHER_SHA)
				|| selection.selected().family() != LegalActionV3.Family.DAMAGE || terminal.actions().stream().filter(a -> selection.matches(a.row().receipt())).count() != 1)
			{ throw new IllegalArgumentException("008 requires an exact paid frozen SHADOW selection and terminal boundary"); }
			int classId = selection.observation().self().classId();
			if (selection.split().equals("AUDIT") ? classId != 110 : classId != 94 && classId != 103)
			{ throw new IllegalArgumentException("008 class/split isolation changed"); }
			var identity = selection.observation().self().fact(Field.CLASS_ID);
			if (!identity.known() || identity.value() != classId) { throw new IllegalArgumentException("Canonical class feature missing"); }
		}
		public TacticalStoreV3.Row row()
		{ return terminal.actions().stream().filter(a -> selection.matches(a.row().receipt())).findFirst().orElseThrow().row(); }
		public List<TacticalStoreV3.Row> paidCandidates()
		{
			return terminal.actions().stream().map(TerminalRewardReceiptV1.TimedAction::row).filter(r ->
			{
				var a = r.receipt().attempt(); var o = a.observation();
				return o.profileId() == selection.observation().profileId() && o.actorObjectId() == selection.observation().actorObjectId()
					&& o.actorEpoch() == selection.observation().actorEpoch() && o.self().classId() == selection.observation().self().classId()
					&& a.modelVersion() == TEACHER_VERSION && a.selected().family() == LegalActionV3.Family.DAMAGE
					&& (selection.matches(r.receipt()) || o.capturedNanos() > selection.observation().capturedNanos() && a.selected().equals(a.baseline()));
			}).sorted(Comparator.comparingLong((TacticalStoreV3.Row r) -> r.receipt().attempt().observation().capturedNanos()).thenComparing(TacticalStoreV3.Row::eventKey)).limit(2).toList();
		}
		public String actionKey() { return hash(paidCandidates().stream().map(TacticalStoreV3.Row::actionKey).sorted().toList().toString()); }
		public String context(TacticalStoreV3.Row row)
		{
			var o = row.receipt().attempt().observation(); var shape = new ArrayList<String>();
			for (var a : row.receipt().attempt().offered())
			{
				var target = o.entity(a.targetObjectId());
				shape.add(a.family() + ":" + a.skillId() + ":" + a.skillLevel() + ":" + a.mpCost() + ":" + a.castMillis() + ":" + a.range() + ":" + target.role() + ":" + target.classId() + ":" + target.fact(Field.MAX_HP));
			}
			shape.sort(Comparator.naturalOrder()); return hash(selection.cell() + "/" + o.self().classId() + "/" + o.self().fact(Field.LEVEL) + "/" + shape);
		}
		public float utility(TacticalStoreV3.Row row)
		{
			var receipt = row.receipt(); var a = receipt.attempt(); var maximum = a.observation().entity(a.selected().targetObjectId()).fact(Field.MAX_HP);
			if (!maximum.known() || maximum.value() <= 0) { throw new IllegalArgumentException("Unknown paid utility scale"); }
			double local = Math.clamp(2 * receipt.actualHp() / maximum.value() - receipt.actualMp() / 500 - receipt.elapsedNanos() / 1e10, -1, 1);
			return (float) (.70 * terminal.reward() + .30 * local);
		}
		Control control() { var r = row(); return new Control(terminal.naturalKey(), context(r), selection.block(), selection.split(), selection.observation().self().classId(), baselineFeatures(r), utility(r)); }
	}
	private static float[] baselineFeatures(TacticalStoreV3.Row row)
	{
		var a = row.receipt().attempt(); var result = Arrays.copyOf(a.observation().state(), 192);
		System.arraycopy(a.baseline().features(a.observation()), 0, result, 128, 64); return result;
	}
	public record Target(Sample sample, TacticalStoreV3.Row row, double propensity, float utility, float baseline, float residual,
		double weight, String baselineKind, String baselineSha, int baselineCount)
	{
		public TacticalModelV3.Example example() { var a = row.receipt().attempt(); return new TacticalModelV3.Example(a.observation().state(), a.selected().features(a.observation()), residual); }
	}
	public record Frozen(String runId, String datasetSha, List<Sample> samples)
	{
		public Frozen
		{
			if (runId == null || !runId.matches("[a-f0-9]{32}")) { throw new IllegalArgumentException("008 frozen run identity"); }
			samples = validate(samples);
			if (datasetSha == null || !datasetSha.equals(TacticalObservationV3.hash(pack(samples)))) { throw new IllegalArgumentException("008 dataset SHA changed"); }
		}
		public List<Target> targets()
		{
			var controls = samples.stream().filter(s -> s.selection().split().equals("TRAIN") && !s.selection().experimental()).map(Sample::control).toList();
			if (controls.size() < 4 || controls.stream().map(Control::classId).distinct().count() != 2) { return List.of(); }
			var result = new ArrayList<Target>();
			for (var sample : samples)
			{
				var selection = sample.selection(); boolean train = selection.split().equals("TRAIN");
				if (train && !selection.experimental()) { continue; }
				if (train && samples.stream().noneMatch(a -> a.selection().split().equals("TRAIN") && !a.selection().experimental()
					&& a.selection().block().equals(selection.block()) && a.selection().cell().equals(selection.cell())
					&& a.selection().observation().self().classId() == selection.observation().self().classId())) { continue; }
				for (var row : sample.paidCandidates())
				{
					String excluded = train ? selection.block() : ""; var exact = controls.stream().filter(c -> c.cell().equals(sample.context(row)) && !c.block().equals(excluded)).toList();
					double b; String baselineSha, kind; int count;
					if (train && selection.matches(row.receipt()) && exact.size() >= 3)
					{
						b = exact.stream().mapToDouble(Control::utility).average().orElseThrow(); count = exact.size(); kind = "TRAIN_EXACT_LEAVE_BLOCK_OUT";
						baselineSha = hash(exact.stream().map(c -> c.key() + ":" + c.cell() + ":" + Double.toHexString(c.utility())).sorted().toList().toString());
					}
					else
					{
						var independent = controls.stream().filter(c -> !c.block().equals(excluded)).toList();
						if (independent.size() < 4 || independent.stream().map(Control::classId).distinct().count() != 2) { continue; }
						var forecast = baseline(controls, excluded, baselineFeatures(row)); b = forecast.value(); baselineSha = forecast.sourceSha(); count = forecast.controls();
						kind = train ? "TRAIN_FORECAST_LEAVE_BLOCK_OUT" : "TRAIN_FORECAST_OOD";
					}
					double propensity = selection.matches(row.receipt()) ? selection.propensity() : 1;
					double rawWeight = Math.min(4, 1 / (row.receipt().attempt().offered().size() * propensity)); float u = sample.utility(row);
					result.add(new Target(sample, row, propensity, u, (float) b, (float) Math.clamp(4 * (u - b), -1, 1), rawWeight, kind, baselineSha, count));
				}
			}
			var episodes = new HashMap<Integer, Set<String>>(); var totals = new HashMap<String, Double>(); int trainCount = 0;
			for (var t : result)
			{
				if (!t.sample().selection().split().equals("TRAIN")) { continue; } trainCount++;
				String episode = t.sample().terminal().naturalKey(); totals.merge(episode, t.weight(), Double::sum);
				episodes.computeIfAbsent(t.sample().selection().observation().self().classId(), _ -> new HashSet<>()).add(episode);
			}
			var normalized = new ArrayList<Target>();
			for (var t : result)
			{
				double weight = t.weight();
				if (t.sample().selection().split().equals("TRAIN"))
				{
					int count = episodes.get(t.sample().selection().observation().self().classId()).size();
					weight = Math.min(4, weight / totals.get(t.sample().terminal().naturalKey()) * trainCount / (2.0 * count));
				}
				normalized.add(new Target(t.sample(), t.row(), t.propensity(), t.utility(), t.baseline(), t.residual(), weight, t.baselineKind(), t.baselineSha(), t.baselineCount()));
			}
			return List.copyOf(normalized);
		}
		/** Exactly one paid initial decision per held-out episode; auxiliary observations do not inflate quality n. */
		public List<TacticalModelV3.Example> examples(String split)
		{
			if (!Set.of("TUNING", "AUDIT").contains(split)) { throw new IllegalArgumentException("008 held-out examples only"); }
			return targets().stream().filter(t -> t.sample().selection().split().equals(split) && t.sample().selection().matches(t.row().receipt())).map(Target::example).toList();
		}
		public double constantMean()
		{
			var train = weightedExamples(); return train.stream().mapToDouble(w -> w.weight() * w.example().reward()).sum() / train.stream().mapToDouble(TacticalModelV3.WeightedExample::weight).sum();
		}
		public double constantLoss(String split)
		{ double mean = constantMean(); return examples(split).stream().mapToDouble(e -> Math.pow(e.reward() - mean, 2)).average().orElseThrow(); }
		public String metrics()
		{
			var targets = targets(); var train = targets.stream().filter(t -> t.sample().selection().split().equals("TRAIN")).toList();
			return "{\"objective\":" + TrainingStore.quote(OBJECTIVE) + ",\"configSha\":" + TrainingStore.quote(CONFIG_SHA) + ",\"datasetSha\":" + TrainingStore.quote(datasetSha)
				+ ",\"samples\":" + samples.size() + ",\"trainTargets\":" + train.size() + ",\"trainEpisodes\":" + train.stream().map(t -> t.sample().terminal().naturalKey()).distinct().count()
				+ ",\"tuning\":" + examples("TUNING").size() + ",\"audit\":" + examples("AUDIT").size() + ",\"heldOutQualityUnit\":\"ONE_INITIAL_PAID_DECISION_PER_EPISODE\""
				+ ",\"maximumPaidTargetsPerEpisode\":2,\"baseline\":\"TRAIN_ONLY_EXACT_INITIAL_OR_FROZEN_RIDGE_FORECAST\",\"gradientWeight\":\"EPISODE_CLASS_BALANCED_CLIPPED_IPS\""
				+ ",\"lateStockConditionalPropensity\":1,\"unbiasedUniformActionClaim\":false,\"syntheticCounterfactualOutcomes\":false,\"productionPromotion\":false}";
		}
		public List<TacticalModelV3.WeightedExample> weightedExamples()
		{ return targets().stream().filter(t -> t.sample().selection().split().equals("TRAIN")).map(t -> new TacticalModelV3.WeightedExample(t.example(), t.weight())).toList(); }
		public List<TacticalStoreV3.Row> sourceRows()
		{ return samples.stream().flatMap(s -> s.terminal().actions().stream()).map(TerminalRewardReceiptV1.TimedAction::row).toList(); }
		public boolean sufficient()
		{
			var targets = targets();
			if (targets.stream().filter(t -> t.sample().selection().split().equals("TRAIN")).count() < 16 || examples("TUNING").size() < 8 || examples("AUDIT").size() < 8) { return false; }
			for (int classId : List.of(94, 103))
			{
				var train = targets.stream().filter(t -> t.sample().selection().split().equals("TRAIN") && t.sample().selection().observation().self().classId() == classId).toList();
				if (train.stream().map(t -> t.sample().terminal().naturalKey()).distinct().count() < 4
					|| train.stream().noneMatch(t -> !t.row().receipt().attempt().selected().equals(t.row().receipt().attempt().baseline()))) { return false; }
			}
			return true;
		}
		public byte[] bytes() { return pack(samples); }
	}
	public static Frozen freeze(String runId, List<Sample> samples) { var valid = validate(samples); return new Frozen(runId, TacticalObservationV3.hash(pack(valid)), valid); }
	private static List<Sample> validate(List<Sample> input)
	{
		if (input == null || input.isEmpty() || input.size() > 48 || input.stream().anyMatch(Objects::isNull)) { throw new IllegalArgumentException("008 sample capacity"); }
		var samples = input.stream().sorted(Comparator.comparing(s -> s.terminal().naturalKey())).toList(); var episodes = new HashSet<String>(); var events = new HashSet<String>();
		String installation = samples.getFirst().terminal().boundary().installation(), reward = samples.getFirst().terminal().config().sha256();
		for (var sample : samples)
		{
			if (!episodes.add(sample.terminal().naturalKey()) || !installation.equals(sample.terminal().boundary().installation()) || !reward.equals(sample.terminal().config().sha256()))
			{ throw new IllegalArgumentException("Duplicate or foreign008 episode"); }
			for (var action : sample.terminal().actions()) { if (!events.add(action.row().eventKey())) { throw new IllegalArgumentException("008 paid action reused"); } }
		}
		if (samples.stream().filter(s -> s.selection().split().equals("TRAIN") && s.selection().experimental()).count() > 16
			|| samples.stream().filter(s -> s.selection().split().equals("TRAIN") && !s.selection().experimental()).count() > 16
			|| samples.stream().filter(s -> s.selection().split().equals("TUNING")).count() > 8 || samples.stream().filter(s -> s.selection().split().equals("AUDIT")).count() > 8)
		{ throw new IllegalArgumentException("008 declared quota exceeded"); }
		TacticalStoreV3.validateDataset(samples.stream().flatMap(s -> s.terminal().actions().stream()).map(TerminalRewardReceiptV1.TimedAction::row).toList()); return List.copyOf(samples);
	}
	private static byte[] pack(List<Sample> samples)
	{
		try
		{
			var buffer = new ByteArrayOutputStream(); var out = new DataOutputStream(buffer); out.writeInt(0x41443038); out.writeInt(1); out.writeUTF(REVISION); out.writeUTF(CONFIG_SHA); out.writeInt(samples.size());
			for (var s : samples) { blob(out, s.selection().bytes()); out.writeUTF(s.terminal().sha256()); blob(out, s.terminal().bytes()); if (buffer.size() > 64 * 1024 * 1024) { throw new IllegalArgumentException("008 frozen byte capacity"); } }
			out.flush(); return buffer.toByteArray();
		}
		catch (IOException failure) { throw new IllegalArgumentException("008 encoding", failure); }
	}
	private static void blob(DataOutputStream out, byte[] bytes) throws IOException { out.writeInt(bytes.length); out.write(bytes); }
	public static Frozen restore(String runId, byte[] bytes, String expectedSha)
	{
		if (bytes == null || bytes.length > 64 * 1024 * 1024 || !TacticalObservationV3.hash(bytes).equals(expectedSha)) { throw new IllegalArgumentException("008 frozen SHA changed"); }
		try (var in = new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			if (in.readInt() != 0x41443038 || in.readInt() != 1 || !in.readUTF().equals(REVISION) || !in.readUTF().equals(CONFIG_SHA)) { throw new IllegalArgumentException("008 objective revision changed"); }
			int count = in.readInt(); if (count < 1 || count > 48) { throw new IllegalArgumentException("008 snapshot sample bound"); }
			var samples = new ArrayList<Sample>();
			for (int i = 0; i < count; i++)
			{
				var selection = TrainExplorationV1.Selection.restore(blob(in, 1000000)); String sha = in.readUTF();
				samples.add(new Sample(selection, TerminalRewardReceiptV1.restore(blob(in, TerminalRewardReceiptV1.MAX_BYTES), sha)));
			}
			var result = new Frozen(runId, expectedSha, samples);
			if (in.available() != 0 || !Arrays.equals(bytes, result.bytes())) { throw new IllegalArgumentException("Noncanonical008 snapshot"); } return result;
		}
		catch (IOException failure) { throw new IllegalArgumentException("Invalid008 snapshot", failure); }
	}
	private static byte[] blob(DataInputStream in, int maximum) throws IOException
	{
		int count = in.readInt(); if (count < 1 || count > maximum || count > in.available()) { throw new IllegalArgumentException("008 blob bound"); } return in.readNBytes(count);
	}
	public static boolean pinsMatch(long model, String selectedSha, String adamSha, String weightsSha, long updates, long epochs)
	{ return model == TEACHER_VERSION && TEACHER_SHA.equals(selectedSha) && PARENT_ADAM_SHA.equals(adamSha) && PARENT_WEIGHTS_SHA.equals(weightsSha) && updates == 855 && epochs == 191; }
	public static boolean explorationPermitted(boolean isolated, boolean optIn, String phase, String campaign, String dataset, String split)
	{ return isolated && optIn && "COLLECT".equals(phase) && CAMPAIGN.equals(campaign) && DATASET.equals(dataset) && "TRAIN".equals(split); }
	private static String hash(String value) { return TacticalObservationV3.hash(value.getBytes(StandardCharsets.UTF_8)); }
	private ActionAdvantage008() { }
}

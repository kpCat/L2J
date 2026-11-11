/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

/** Immutable CPU action scorer. Training is offline; callers encode each decision once. */
public final class TacticalModelV3
{
	public static final int ALGORITHM = 3;
	public static final int STATE_SIZE = 128;
	public static final int ACTION_SIZE = 64;
	public static final int ENCODED_SIZE = 32;
	public static final int MAX_EXAMPLES = 4096;
	private static final int FIRST = 512;
	private static final int SECOND = 2048;
	private static final int HEAD = 32;
	private static final int FIRST_OFFSET = 0;
	private static final int SECOND_OFFSET = (STATE_SIZE + 1) * FIRST;
	private static final int ENCODED_OFFSET = SECOND_OFFSET + (FIRST + 1) * SECOND;
	private static final int HEAD_OFFSET = ENCODED_OFFSET + (SECOND + 1) * ENCODED_SIZE;
	private static final int OUTPUT_OFFSET = HEAD_OFFSET + (ENCODED_SIZE + ACTION_SIZE + 1) * HEAD;
	public static final int PARAMETER_COUNT = OUTPUT_OFFSET + HEAD + 1;
	public static final int WEIGHT_BYTES = PARAMETER_COUNT * Float.BYTES;
	private static final int MAGIC = 0x50575633;
	private static final int NORMALIZATION = 1;
	private static final int HEADER_SIZE = 10 * Integer.BYTES + 32;
	public static final int BYTE_SIZE = HEADER_SIZE + WEIGHT_BYTES;
	public static final String FINGERPRINT = digest(("phantom-tactical-v3/f32be/128-512-2048-32/factorized-state32-action64-32-1/"
		+ "leaky-relu:0.05,leaky-relu:0.05,tanh,leaky-relu:0.05,tanh/normalized-finite-input[-1,1]/reward[-1,1]/weight-bound:8").getBytes(StandardCharsets.UTF_8));
	private static final float LEAK = .05f;
	private static final float WEIGHT_BOUND = 8;
	private static final int BATCH_SIZE = 16;
	private static final int MAX_EPOCHS = 48;
	private static final int PATIENCE = 16;
	private final float[] _weights;

	private TacticalModelV3(float[] ownedWeights)
	{
		_weights = ownedWeights;
	}

	public record Example(float[] state, float[] action, float reward)
	{
		public Example
		{
			if (state == null || action == null) { throw new IllegalArgumentException("Missing V3 example vector"); }
			state = state.clone();
			action = action.clone();
			validate(state, STATE_SIZE);
			validate(action, ACTION_SIZE);
			if (!Float.isFinite(reward) || Math.abs(reward) > 1) { throw new IllegalArgumentException("Invalid V3 reward"); }
		}

		@Override
		public float[] state() { return state.clone(); }

		@Override
		public float[] action() { return action.clone(); }
	}

	/** Epochs identifies the selected tuning checkpoint, including zero for unchanged initialization. */
	public record Fit(TacticalModelV3 model, int epochs, double initialLoss, double loss) { }

	/** Candidate selection and last completed optimization boundary are deliberately separate. */
	public record IncrementalFit(Fit fit, Optimizer continuation) { }

	/** Immutable offline Adam boundary. This envelope does not change the accepted V3 model codec. */
	public static final class Optimizer
	{
		private static final int MAGIC = 0x50574f34, VERSION = 1;
		private static final String SCHEMA = digest((FINGERPRINT + "/Adam004/m=.9/v=.999/rate=.0003/epsilon=1e-8/clip=1/batch16/epoch-order-v1")
			.getBytes(StandardCharsets.UTF_8));
		private static final int HEADER = 3 * Integer.BYTES + 3 * Long.BYTES + 32;
		public static final int BYTE_SIZE = HEADER + TacticalModelV3.BYTE_SIZE + 2 * WEIGHT_BYTES;
		private final TacticalModelV3 _model;
		private final float[] _first, _second;
		private final long _seed, _updates, _epochs;

		private Optimizer(TacticalModelV3 model, float[] first, float[] second, long seed, long updates, long epochs)
		{
			_model = model; _first = first; _second = second; _seed = seed; _updates = updates; _epochs = epochs;
		}
		public TacticalModelV3 model() { return _model; }
		public long updates() { return _updates; }
		public long completedEpochs() { return _epochs; }
		public long seed() { return _seed; }
		public String sha256() { return digest(bytes()); }
		public byte[] bytes()
		{
			final var buffer = ByteBuffer.allocate(BYTE_SIZE);
			buffer.putInt(MAGIC).putInt(VERSION).putInt(PARAMETER_COUNT).putLong(_seed).putLong(_updates).putLong(_epochs);
			buffer.put(HexFormat.of().parseHex(SCHEMA)).put(_model.bytes());
			buffer.asFloatBuffer().put(_first).put(_second);
			return buffer.array();
		}
		public static Optimizer restore(byte[] bytes, String expectedSha)
		{
			if (bytes == null || bytes.length != BYTE_SIZE || expectedSha == null || !expectedSha.matches("[a-f0-9]{64}"))
			{ throw new IllegalArgumentException("Invalid V3 optimizer size/SHA"); }
			final byte[] owned = bytes.clone();
			if (!digest(owned).equals(expectedSha)) { throw new IllegalArgumentException("Invalid V3 optimizer checksum"); }
			final var buffer = ByteBuffer.wrap(owned);
			if (buffer.getInt() != MAGIC || buffer.getInt() != VERSION || buffer.getInt() != PARAMETER_COUNT)
			{ throw new IllegalArgumentException("Unsupported V3 optimizer format"); }
			final long seed = buffer.getLong(), updates = buffer.getLong(), epochs = buffer.getLong();
			if (updates < 0 || epochs < 0 || epochs > updates) { throw new IllegalArgumentException("Invalid V3 optimizer boundary"); }
			final byte[] schema = new byte[32]; buffer.get(schema);
			if (!SCHEMA.equals(HexFormat.of().formatHex(schema))) { throw new IllegalArgumentException("Incompatible V3 optimizer schema"); }
			final byte[] modelBytes = new byte[TacticalModelV3.BYTE_SIZE]; buffer.get(modelBytes);
			final var model = TacticalModelV3.restore(modelBytes, digest(modelBytes));
			final float[] first = new float[PARAMETER_COUNT], second = new float[PARAMETER_COUNT];
			buffer.asFloatBuffer().get(first).get(second);
			for (int i = 0; i < first.length; i++)
			{
				if (!Float.isFinite(first[i]) || Math.abs(first[i]) > 1.00001f || !Float.isFinite(second[i]) || second[i] < 0 || second[i] > 1.00001f
					|| updates == 0 && (first[i] != 0 || second[i] != 0))
				{ throw new IllegalArgumentException("Invalid V3 Adam moments"); }
			}
			return new Optimizer(model, first, second, seed, updates, epochs);
		}
	}

	/** Explicit legacy migration: preserve authenticated parent weights, initialize unavailable moments to zero. */
	public static Optimizer startOptimization(TacticalModelV3 parent, long seed)
	{
		if (parent == null) { throw new IllegalArgumentException("Compatible V3 parent required"); }
		return new Optimizer(parent, new float[PARAMETER_COUNT], new float[PARAMETER_COUNT], seed, 0, 0);
	}

	/** One ordered batch; caller must durably bind sample keys before giving this boundary credit. */
	public static Optimizer optimizeBatch(Optimizer parent, List<Example> examples)
	{
		if (parent == null) { throw new IllegalArgumentException("V3 optimizer parent required"); }
		final var batch = validatedSplit(examples);
		if (batch.size() > BATCH_SIZE) { throw new IllegalArgumentException("V3 optimizer batch bound"); }
		final float[] weights = parent._model._weights.clone(), first = parent._first.clone(), second = parent._second.clone();
		final float[] gradient = new float[PARAMETER_COUNT];
		final var scratch = new Scratch();
		for (var example : batch) { accumulate(weights, gradient, example, scratch); }
		final long updates = Math.incrementExact(parent._updates);
		update(weights, gradient, first, second, batch.size(), updates);
		return new Optimizer(new TacticalModelV3(weights), first, second, parent._seed, updates, parent._epochs);
	}

	/** Offline, deterministic whole-epoch continuation. An unfinished call has no durable completion credit. */
	public static IncrementalFit continueTraining(Optimizer parent, List<Example> examples, List<Example> tuning)
	{
		if (parent == null) { throw new IllegalArgumentException("V3 optimizer parent required"); }
		final var train = validatedSplit(examples);
		final var selection = validatedSplit(tuning);
		if (train.size() < 16 || selection.size() < 8 || train.size() + selection.size() > MAX_EXAMPLES)
		{ throw new IllegalArgumentException("Insufficient or oversized V3 train/tuning split"); }
		final float[] weights = parent._model._weights.clone(), first = parent._first.clone(), second = parent._second.clone();
		final float[] gradient = new float[PARAMETER_COUNT];
		final var scratch = new Scratch();
		final double initialLoss = loss(weights, selection, scratch);
		double bestLoss = initialLoss;
		TacticalModelV3 best = parent._model;
		int bestEpoch = 0, completed = 0;
		long updates = parent._updates;
		final int epochs = Math.max(1, Math.min(MAX_EPOCHS, 256 / ((train.size() + BATCH_SIZE - 1) / BATCH_SIZE)));
		for (int epoch = 1; epoch <= epochs && epoch - bestEpoch <= PATIENCE; epoch++)
		{
			// Epoch order is reproducible from the persisted seed/global epoch, without hidden Random state.
			final int[] order = new int[train.size()];
			for (int i = 0; i < order.length; i++) { order[i] = i; }
			final var shuffle = new Random(parent._seed ^ Math.addExact(parent._epochs, epoch) * 0x9e3779b97f4a7c15L);
			for (int i = order.length - 1; i > 0; i--)
			{
				final int j = shuffle.nextInt(i + 1), saved = order[i]; order[i] = order[j]; order[j] = saved;
			}
			for (int start = 0; start < order.length; start += BATCH_SIZE)
			{
				final int end = Math.min(start + BATCH_SIZE, order.length);
				Arrays.fill(gradient, 0);
				for (int i = start; i < end; i++) { accumulate(weights, gradient, train.get(order[i]), scratch); }
				updates = Math.incrementExact(updates);
				update(weights, gradient, first, second, end - start, updates);
			}
			completed = epoch;
			final double candidate = loss(weights, selection, scratch);
			if (candidate + 1e-7 < bestLoss)
			{ best = new TacticalModelV3(weights.clone()); bestLoss = candidate; bestEpoch = epoch; }
		}
		final var continuation = new Optimizer(new TacticalModelV3(weights), first, second, parent._seed, updates, Math.addExact(parent._epochs, completed));
		return new IncrementalFit(new Fit(best, bestEpoch, initialLoss, bestLoss), continuation);
	}

	public static TacticalModelV3 initialized(long seed)
	{
		final var random = new Random(seed);
		final float[] weights = new float[PARAMETER_COUNT];
		initialize(weights, FIRST_OFFSET, STATE_SIZE, FIRST, random);
		initialize(weights, SECOND_OFFSET, FIRST, SECOND, random);
		initialize(weights, ENCODED_OFFSET, SECOND, ENCODED_SIZE, random);
		initialize(weights, HEAD_OFFSET, ENCODED_SIZE + ACTION_SIZE, HEAD, random);
		initialize(weights, OUTPUT_OFFSET, HEAD, 1, random);
		return new TacticalModelV3(weights);
	}

	private static void initialize(float[] weights, int offset, int inputs, int outputs, Random random)
	{
		final float bound = (float) Math.sqrt(6.0 / inputs);
		for (int o = 0; o < outputs; o++)
		{
			for (int i = 0; i < inputs; i++) { weights[offset++] = (random.nextFloat() * 2 - 1) * bound; }
			offset++; // A real trainable bias, initially zero.
		}
	}

	/** Returned encoding belongs to the caller and is valid only for its unchanged observation. */
	public float[] encode(float[] state)
	{
		validate(state, STATE_SIZE);
		final float[] first = new float[FIRST];
		final float[] second = new float[SECOND];
		final float[] encoded = new float[ENCODED_SIZE];
		encode(_weights, state, first, second, encoded);
		return encoded;
	}

	public float scoreEncoded(float[] encoded, float[] action)
	{
		validate(encoded, ENCODED_SIZE);
		validate(action, ACTION_SIZE);
		return head(_weights, encoded, action, null);
	}

	public float score(float[] state, float[] action)
	{
		return scoreEncoded(encode(state), action);
	}

	private static void encode(float[] weights, float[] state, float[] first, float[] second, float[] encoded)
	{
		dense(weights, FIRST_OFFSET, state, first, false);
		dense(weights, SECOND_OFFSET, first, second, false);
		dense(weights, ENCODED_OFFSET, second, encoded, true);
	}

	private static void dense(float[] weights, int offset, float[] input, float[] output, boolean tanh)
	{
		for (int o = 0; o < output.length; o++)
		{
			float sum = weights[offset + input.length];
			for (int i = 0; i < input.length; i++) { sum += weights[offset + i] * input[i]; }
			output[o] = tanh ? (float) Math.tanh(sum) : activation(sum);
			offset += input.length + 1;
		}
	}

	/** No temporary action array or per-action hidden array in the inference hot path. */
	private static float head(float[] weights, float[] encoded, float[] action, float[] hidden)
	{
		float output = weights[OUTPUT_OFFSET + HEAD];
		int offset = HEAD_OFFSET;
		for (int h = 0; h < HEAD; h++)
		{
			float stateProjection = 0;
			float actionProjection = 0;
			for (int i = 0; i < ENCODED_SIZE; i++) { stateProjection += encoded[i] * weights[offset + i]; }
			for (int i = 0; i < ACTION_SIZE; i++) { actionProjection += action[i] * weights[offset + ENCODED_SIZE + i]; }
			final float value = activation(stateProjection * actionProjection + weights[offset + ENCODED_SIZE + ACTION_SIZE]);
			if (hidden != null) { hidden[h] = value; }
			output += value * weights[OUTPUT_OFFSET + h];
			offset += ENCODED_SIZE + ACTION_SIZE + 1;
		}
		return (float) Math.tanh(output);
	}

	private static float activation(float value)
	{
		return value >= 0 ? value : LEAK * value;
	}

	public static Fit train(List<Example> examples, List<Example> tuning, long seed)
	{
		final List<Example> train = validatedSplit(examples);
		final List<Example> selection = validatedSplit(tuning);
		if (train.size() < 16 || selection.size() < 8 || train.size() + selection.size() > MAX_EXAMPLES)
		{
			throw new IllegalArgumentException("Insufficient or oversized V3 train/tuning split");
		}
		final var baseline = initialized(seed);
		final float[] weights = baseline._weights.clone();
		final float[] gradient = new float[PARAMETER_COUNT];
		final float[] firstMoment = new float[PARAMETER_COUNT];
		final float[] secondMoment = new float[PARAMETER_COUNT];
		final var scratch = new Scratch();
		final int[] order = new int[train.size()];
		for (int i = 0; i < order.length; i++) { order[i] = i; }
		final var shuffle = new Random(seed);
		final double initialLoss = loss(weights, selection, scratch);
		double bestLoss = initialLoss;
		float[] best = baseline._weights;
		int bestEpoch = 0;
		int updates = 0;
		// Complete epochs only. Large inputs get fewer epochs, bounding offline work to about 256 batches.
		final int epochs = Math.max(1, Math.min(MAX_EPOCHS, 256 / ((train.size() + BATCH_SIZE - 1) / BATCH_SIZE)));
		for (int epoch = 1; epoch <= epochs && epoch - bestEpoch <= PATIENCE; epoch++)
		{
			for (int i = order.length - 1; i > 0; i--)
			{
				final int j = shuffle.nextInt(i + 1);
				final int saved = order[i];
				order[i] = order[j];
				order[j] = saved;
			}
			for (int start = 0; start < order.length; start += BATCH_SIZE)
			{
				final int end = Math.min(start + BATCH_SIZE, order.length);
				Arrays.fill(gradient, 0);
				for (int i = start; i < end; i++) { accumulate(weights, gradient, train.get(order[i]), scratch); }
				update(weights, gradient, firstMoment, secondMoment, end - start, ++updates);
			}
			final double candidate = loss(weights, selection, scratch);
			if (candidate + 1e-7 < bestLoss)
			{
				best = weights.clone();
				bestLoss = candidate;
				bestEpoch = epoch;
			}
		}
		return new Fit(new TacticalModelV3(best), bestEpoch, initialLoss, bestLoss);
	}

	private static void update(float[] weights, float[] gradient, float[] first, float[] second, int batch, long step)
	{
		final double firstCorrection = 1 - Math.pow(.9, step);
		final double secondCorrection = 1 - Math.pow(.999, step);
		for (int i = 0; i < weights.length; i++)
		{
			final float value = Math.clamp(gradient[i] / batch, -1, 1);
			first[i] = .9f * first[i] + .1f * value;
			second[i] = .999f * second[i] + .001f * value * value;
			final double delta = .0003 * (first[i] / firstCorrection) / (Math.sqrt(second[i] / secondCorrection) + 1e-8);
			weights[i] = (float) Math.clamp(weights[i] - delta, -WEIGHT_BOUND, WEIGHT_BOUND);
			if (!Float.isFinite(weights[i])) { throw new IllegalStateException("Non-finite V3 training update"); }
		}
	}

	/** All five dense layers receive derivatives of the same observed reward loss. */
	private static void accumulate(float[] weights, float[] gradient, Example example, Scratch s)
	{
		encode(weights, example.state, s.first, s.second, s.encoded);
		final float prediction = head(weights, s.encoded, example.action, s.head);
		final float error = (prediction - example.reward) * (1 - prediction * prediction);
		gradient[OUTPUT_OFFSET + HEAD] += error;
		Arrays.fill(s.encodedDelta, 0);
		for (int h = 0; h < HEAD; h++)
		{
			gradient[OUTPUT_OFFSET + h] += error * s.head[h];
			final float delta = error * weights[OUTPUT_OFFSET + h] * (s.head[h] >= 0 ? 1 : LEAK);
			final int offset = HEAD_OFFSET + h * (ENCODED_SIZE + ACTION_SIZE + 1);
			float stateProjection = 0;
			float actionProjection = 0;
			for (int i = 0; i < ENCODED_SIZE; i++) { stateProjection += s.encoded[i] * weights[offset + i]; }
			for (int i = 0; i < ACTION_SIZE; i++) { actionProjection += example.action[i] * weights[offset + ENCODED_SIZE + i]; }
			for (int i = 0; i < ENCODED_SIZE; i++)
			{
				gradient[offset + i] += delta * actionProjection * s.encoded[i];
				s.encodedDelta[i] += delta * actionProjection * weights[offset + i];
			}
			for (int i = 0; i < ACTION_SIZE; i++) { gradient[offset + ENCODED_SIZE + i] += delta * stateProjection * example.action[i]; }
			gradient[offset + ENCODED_SIZE + ACTION_SIZE] += delta;
		}
		for (int i = 0; i < ENCODED_SIZE; i++) { s.encodedDelta[i] *= 1 - s.encoded[i] * s.encoded[i]; }
		backward(weights, gradient, ENCODED_OFFSET, s.second, s.encodedDelta, s.secondDelta);
		for (int i = 0; i < SECOND; i++) { s.secondDelta[i] *= s.second[i] >= 0 ? 1 : LEAK; }
		backward(weights, gradient, SECOND_OFFSET, s.first, s.secondDelta, s.firstDelta);
		for (int i = 0; i < FIRST; i++) { s.firstDelta[i] *= s.first[i] >= 0 ? 1 : LEAK; }
		backward(weights, gradient, FIRST_OFFSET, example.state, s.firstDelta, null);
	}

	private static void backward(float[] weights, float[] gradient, int offset, float[] input, float[] delta, float[] inputDelta)
	{
		if (inputDelta != null) { Arrays.fill(inputDelta, 0); }
		for (int o = 0; o < delta.length; o++)
		{
			final float error = delta[o];
			for (int i = 0; i < input.length; i++)
			{
				gradient[offset + i] += error * input[i];
				if (inputDelta != null) { inputDelta[i] += error * weights[offset + i]; }
			}
			gradient[offset + input.length] += error;
			offset += input.length + 1;
		}
	}

	public double loss(List<Example> examples)
	{
		return loss(_weights, validatedSplit(examples), new Scratch());
	}

	private static double loss(float[] weights, List<Example> examples, Scratch s)
	{
		double sum = 0;
		for (Example example : examples)
		{
			encode(weights, example.state, s.first, s.second, s.encoded);
			final double error = head(weights, s.encoded, example.action, null) - example.reward;
			sum += error * error;
		}
		return sum / examples.size();
	}

	private static List<Example> validatedSplit(List<Example> examples)
	{
		if (examples == null || examples.isEmpty() || examples.size() > MAX_EXAMPLES)
		{
			throw new IllegalArgumentException("Invalid V3 example split");
		}
		for (Example example : examples)
		{
			if (example == null) { throw new IllegalArgumentException("Missing V3 example"); }
		}
		return List.copyOf(examples);
	}

	private static final class Scratch
	{
		final float[] first = new float[FIRST];
		final float[] second = new float[SECOND];
		final float[] encoded = new float[ENCODED_SIZE];
		final float[] head = new float[HEAD];
		final float[] firstDelta = new float[FIRST];
		final float[] secondDelta = new float[SECOND];
		final float[] encodedDelta = new float[ENCODED_SIZE];
	}

	public byte[] bytes()
	{
		final var bytes = ByteBuffer.allocate(BYTE_SIZE);
		bytes.putInt(MAGIC).putInt(ALGORITHM).putInt(STATE_SIZE).putInt(ACTION_SIZE).putInt(FIRST).putInt(SECOND)
			.putInt(ENCODED_SIZE).putInt(HEAD).putInt(PARAMETER_COUNT).putInt(NORMALIZATION);
		bytes.put(HexFormat.of().parseHex(FINGERPRINT));
		bytes.asFloatBuffer().put(_weights);
		return bytes.array();
	}

	public String sha256()
	{
		return digest(bytes());
	}

	public static TacticalModelV3 restore(byte[] bytes, String expectedSha)
	{
		if (bytes == null || bytes.length != BYTE_SIZE || expectedSha == null || !expectedSha.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid V3 model size or checksum");
		}
		// Own the input before hashing/parsing: a caller cannot race the checksum with mutation.
		final byte[] owned = bytes.clone();
		if (!digest(owned).equals(expectedSha)) { throw new IllegalArgumentException("Invalid V3 model checksum"); }
		final var payload = ByteBuffer.wrap(owned);
		for (int expected : new int[] {MAGIC, ALGORITHM, STATE_SIZE, ACTION_SIZE, FIRST, SECOND, ENCODED_SIZE, HEAD, PARAMETER_COUNT, NORMALIZATION})
		{
			if (payload.getInt() != expected) { throw new IllegalArgumentException("Unsupported V3 model format"); }
		}
		final byte[] fingerprint = new byte[32];
		payload.get(fingerprint);
		if (!FINGERPRINT.equals(HexFormat.of().formatHex(fingerprint))) { throw new IllegalArgumentException("Invalid V3 algorithm fingerprint"); }
		final float[] weights = new float[PARAMETER_COUNT];
		payload.asFloatBuffer().get(weights);
		for (float weight : weights)
		{
			if (!Float.isFinite(weight) || Math.abs(weight) > WEIGHT_BOUND) { throw new IllegalArgumentException("Invalid V3 model weight"); }
		}
		return new TacticalModelV3(weights);
	}

	private static void validate(float[] values, int size)
	{
		if (values == null || values.length != size) { throw new IllegalArgumentException("Invalid V3 vector size"); }
		for (float value : values)
		{
			if (!Float.isFinite(value) || Math.abs(value) > 1) { throw new IllegalArgumentException("Invalid V3 normalized value"); }
		}
	}

	private static String digest(byte[] bytes)
	{
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
}

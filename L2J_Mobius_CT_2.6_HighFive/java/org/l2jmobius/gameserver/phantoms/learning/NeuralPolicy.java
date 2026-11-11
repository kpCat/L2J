/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;

/** Immutable action-conditioned MLP. Training is an explicit offline operation. */
public final class NeuralPolicy
{
	public static final int ALGORITHM = 1;
	public static final int RESIDUAL_ALGORITHM = 2;
	private static final int INPUTS = LearningFeatures.SIZE;
	private static final int HIDDEN = 24;
	private static final int STRIDE = INPUTS + 1;
	private static final int OUTPUT = HIDDEN * STRIDE;
	private static final int PARAMETERS = OUTPUT + HIDDEN + 1;
	public static final int BYTE_SIZE = PARAMETERS * Float.BYTES;
	public static final int RESIDUAL_BYTE_SIZE = BYTE_SIZE + 2 * Double.BYTES;
	public static final int MAX_EXAMPLES = 4096;
	private static final int BATCH_SIZE = 24;
	private static final float WEIGHT_BOUND = 8;
	private final float[] _weights;
	private final int _algorithm;
	private final double _center;
	private final double _scale;

	private NeuralPolicy(float[] weights)
	{
		this(weights, ALGORITHM, 0, 1);
	}

	private NeuralPolicy(float[] weights, int algorithm, double center, double scale)
	{
		_weights = weights;
		_algorithm = algorithm;
		_center = center;
		_scale = scale;
	}

	public int algorithm() { return _algorithm; }

	public record Example(float[] features, float reward)
	{
		public Example
		{
			LearningFeatures.validate(features);
			if (!Float.isFinite(reward) || Math.abs(reward) > 1)
			{
				throw new IllegalArgumentException("Reward out of bounds");
			}
			features = features.clone();
		}

		@Override
		public float[] features()
		{
			return features.clone();
		}
	}

	public record Training(NeuralPolicy model, int step, double initialLoss, double loss) { }

	public static NeuralPolicy initial(long seed)
	{
		final Random random = new Random(seed);
		final float[] weights = new float[PARAMETERS];
		for (int h = 0; h < HIDDEN; h++)
		{
			for (int i = 0; i < INPUTS; i++)
			{
				weights[h * STRIDE + i] = (random.nextFloat() * 2 - 1) * .16f;
			}
			weights[OUTPUT + h] = (random.nextFloat() * 2 - 1) * .18f;
		}
		return new NeuralPolicy(weights);
	}

	public float score(float[] features)
	{
		final float prediction = forward(_weights, features, null);
		return _algorithm == ALGORITHM ? prediction : (float) Math.clamp(_center + _scale * prediction, -1, 1);
	}

	private static float forward(float[] weights, float[] features, float[] hidden)
	{
		float output = weights[PARAMETERS - 1];
		for (int h = 0; h < HIDDEN; h++)
		{
			final int offset = h * STRIDE;
			float sum = weights[offset + INPUTS];
			for (int i = 0; i < INPUTS; i++)
			{
				sum += weights[offset + i] * features[i];
			}
			final float activation = Math.max(0, sum);
			if (hidden != null)
			{
				hidden[h] = activation;
			}
			output += activation * weights[OUTPUT + h];
		}
		return (float) Math.tanh(output);
	}

	public static Training train(List<Example> examples, List<Example> validation, long seed)
	{
		if (examples.size() < 16 || validation.size() < 8 || examples.size() + validation.size() > MAX_EXAMPLES)
		{
			throw new IllegalArgumentException("Insufficient or oversized training split");
		}
		final NeuralPolicy baseline = initial(seed);
		final float[] weights = baseline._weights.clone();
		final float[] gradient = new float[PARAMETERS];
		final float[] hidden = new float[HIDDEN];
		final int[] order = new int[examples.size()];
		for (int i = 0; i < order.length; i++)
		{
			order[i] = i;
		}
		final Random shuffle = new Random(seed);
		final double initialLoss = loss(weights, validation);
		double bestLoss = initialLoss;
		float[] best = baseline._weights;
		int bestStep = 0;
		for (int step = 1; step <= 160 && step - bestStep <= 20; step++)
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
				for (int i = start; i < end; i++)
				{
					accumulate(weights, gradient, hidden, examples.get(order[i]));
				}
				for (int w = 0; w < PARAMETERS; w++)
				{
					final float update = gradient[w] / (end - start) + .0002f * weights[w];
					weights[w] = Math.clamp(weights[w] - .035f * Math.clamp(update, -1, 1), -WEIGHT_BOUND, WEIGHT_BOUND);
				}
			}
			final double candidate = loss(weights, validation);
			if (candidate + 1e-6 < bestLoss)
			{
				bestStep = step;
				bestLoss = candidate;
				best = weights.clone();
			}
		}
		return new Training(new NeuralPolicy(best), bestStep, initialLoss, bestLoss);
	}

	/** Fit the native reward residual. Restore the original reward units before making a decision. */
	public static Training trainResidual(List<Example> examples, List<Example> validation, long seed)
	{
		final double center = examples.stream().mapToDouble(Example::reward).average().orElseThrow();
		final double scale = 1 + Math.abs(center);
		final java.util.function.Function<Example, Example> residual = row -> new Example(row.features, (float) ((row.reward - center) / scale));
		final var fit = train(examples.stream().map(residual).toList(), validation.stream().map(residual).toList(), seed);
		final var model = new NeuralPolicy(fit.model()._weights, RESIDUAL_ALGORITHM, center, scale);
		return new Training(model, fit.step(), loss(initial(seed)._weights, validation), model.predictionLoss(validation));
	}

	/** Audit the frozen checkpoint on a third split that never chose a training epoch. */
	public static Training audit(Training checkpoint, List<Example> heldOut, long seed)
	{
		if (heldOut.size() < 8 || heldOut.size() > MAX_EXAMPLES)
		{
			throw new IllegalArgumentException("Insufficient or oversized independent audit split");
		}
		return new Training(checkpoint.model(), checkpoint.step(), loss(initial(seed)._weights, heldOut), checkpoint.model().predictionLoss(heldOut));
	}

	private static void accumulate(float[] weights, float[] gradient, float[] hidden, Example example)
	{
		final float score = forward(weights, example.features, hidden);
		final float error = (score - example.reward) * (1 - score * score);
		gradient[PARAMETERS - 1] += error;
		for (int h = 0; h < HIDDEN; h++)
		{
			if (hidden[h] == 0)
			{
				continue;
			}
			gradient[OUTPUT + h] += error * hidden[h];
			final float hiddenError = error * weights[OUTPUT + h];
			final int offset = h * STRIDE;
			for (int i = 0; i < INPUTS; i++)
			{
				gradient[offset + i] += hiddenError * example.features[i];
			}
			gradient[offset + INPUTS] += hiddenError;
		}
	}

	private static double loss(float[] weights, List<Example> examples)
	{
		double loss = 0;
		for (Example example : examples)
		{
			final double error = forward(weights, example.features, null) - example.reward;
			loss += error * error;
		}
		return loss / examples.size();
	}

	private double predictionLoss(List<Example> examples)
	{
		double loss = 0;
		for (Example example : examples)
		{
			final double error = score(example.features) - example.reward;
			loss += error * error;
		}
		return loss / examples.size();
	}

	public byte[] bytes()
	{
		final ByteBuffer bytes = ByteBuffer.allocate(_algorithm == ALGORITHM ? BYTE_SIZE : RESIDUAL_BYTE_SIZE);
		bytes.asFloatBuffer().put(_weights);
		if (_algorithm == RESIDUAL_ALGORITHM)
		{
			bytes.putDouble(BYTE_SIZE, _center);
			bytes.putDouble(BYTE_SIZE + Double.BYTES, _scale);
		}
		return bytes.array();
	}

	public String sha256()
	{
		return sha256(bytes());
	}

	public static NeuralPolicy restore(byte[] bytes, String expectedSha)
	{
		if (bytes == null || (bytes.length != BYTE_SIZE && bytes.length != RESIDUAL_BYTE_SIZE) || !sha256(bytes).equals(expectedSha))
		{
			throw new IllegalArgumentException("Invalid model size or checksum");
		}
		final float[] weights = new float[PARAMETERS];
		final ByteBuffer payload = ByteBuffer.wrap(bytes);
		payload.asFloatBuffer().get(weights);
		for (float value : weights)
		{
			if (!Float.isFinite(value) || Math.abs(value) > WEIGHT_BOUND)
			{
				throw new IllegalArgumentException("Invalid model weight");
			}
		}
		if (bytes.length == BYTE_SIZE) { return new NeuralPolicy(weights); }
		final double center = payload.getDouble(BYTE_SIZE);
		final double scale = payload.getDouble(BYTE_SIZE + Double.BYTES);
		if (!Double.isFinite(center) || Math.abs(center) > 1 || scale != 1 + Math.abs(center))
		{
			throw new IllegalArgumentException("Invalid residual calibration");
		}
		return new NeuralPolicy(weights, RESIDUAL_ALGORITHM, center, scale);
	}

	public static String sha256(byte[] bytes)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException(e);
		}
	}
}

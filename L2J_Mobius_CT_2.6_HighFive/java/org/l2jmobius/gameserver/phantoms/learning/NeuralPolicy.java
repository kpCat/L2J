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
	private static final int INPUTS = LearningFeatures.SIZE;
	private static final int HIDDEN = 24;
	private static final int STRIDE = INPUTS + 1;
	private static final int OUTPUT = HIDDEN * STRIDE;
	private static final int PARAMETERS = OUTPUT + HIDDEN + 1;
	public static final int BYTE_SIZE = PARAMETERS * Float.BYTES;
	public static final int MAX_EXAMPLES = 4096;
	private static final int BATCH_SIZE = 24;
	private static final float WEIGHT_BOUND = 8;
	private final float[] _weights;

	private NeuralPolicy(float[] weights)
	{
		_weights = weights;
	}

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
		return forward(_weights, features, null);
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

	/** Audit the frozen checkpoint on a third split that never chose a training epoch. */
	public static Training audit(Training checkpoint, List<Example> heldOut, long seed)
	{
		if (heldOut.size() < 8 || heldOut.size() > MAX_EXAMPLES)
		{
			throw new IllegalArgumentException("Insufficient or oversized independent audit split");
		}
		return new Training(checkpoint.model(), checkpoint.step(), loss(initial(seed)._weights, heldOut), loss(checkpoint.model()._weights, heldOut));
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

	public byte[] bytes()
	{
		final ByteBuffer bytes = ByteBuffer.allocate(BYTE_SIZE);
		bytes.asFloatBuffer().put(_weights);
		return bytes.array();
	}

	public String sha256()
	{
		return sha256(bytes());
	}

	public static NeuralPolicy restore(byte[] bytes, String expectedSha)
	{
		if (bytes == null || bytes.length != BYTE_SIZE || !sha256(bytes).equals(expectedSha))
		{
			throw new IllegalArgumentException("Invalid model size or checksum");
		}
		final float[] weights = new float[PARAMETERS];
		ByteBuffer.wrap(bytes).asFloatBuffer().get(weights);
		for (float value : weights)
		{
			if (!Float.isFinite(value) || Math.abs(value) > WEIGHT_BOUND)
			{
				throw new IllegalArgumentException("Invalid model weight");
			}
		}
		return new NeuralPolicy(weights);
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

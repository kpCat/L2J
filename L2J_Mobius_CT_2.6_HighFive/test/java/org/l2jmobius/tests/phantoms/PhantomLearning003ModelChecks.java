/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import org.l2jmobius.gameserver.phantoms.learning.TacticalModelV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalModelV3.Example;

/** JDK-only model contracts. Synthetic fixtures prove mathematics, never native game quality. */
public final class PhantomLearning003ModelChecks
{
	private static volatile float _sink;

	public static void main(String[] args) throws Exception
	{
		if (args.length == 1 && "benchmark".equals(args[0]))
		{
			benchmark();
			return;
		}
		checkpoint();
		mathLearning();
		System.out.println("LEARNING003_MODEL checks=PASS synthetic=MATH_ONLY native=NOT_RUN");
	}

	private static void checkpoint() throws Exception
	{
		final var model = TacticalModelV3.initialized(183);
		final byte[] original = model.bytes();
		check(original.length >= 4 * 1024 * 1024 && original.length < 8 * 1024 * 1024, "real dense checkpoint outside 4-8 MiB");
		check(Arrays.equals(original, TacticalModelV3.initialized(183).bytes()), "seed is nondeterministic");
		check(!Arrays.equals(original, TacticalModelV3.initialized(184).bytes()), "seed has no effect");
		final var restored = TacticalModelV3.restore(original, model.sha256());
		final var row = fixture(1, 29).getFirst();
		check(Double.isFinite(model.loss(List.of(row))), "immutable singleton loss split rejected");
		final float score = model.score(row.state(), row.action());
		check(score == restored.score(row.state(), row.action()), "roundtrip changed prediction");
		check(score == model.scoreEncoded(model.encode(row.state()), row.action()), "encoded scoring changed prediction");
		check(Arrays.equals(original, restored.bytes()), "roundtrip changed immutable bytes");
		original[original.length - 1] ^= 1;
		check(score == model.score(row.state(), row.action()), "bytes leaked mutable model state");
		check(score == restored.score(row.state(), row.action()), "restore retained caller-owned bytes");
		reject(() -> TacticalModelV3.restore(original, model.sha256()), "wrong SHA accepted");
		reject(() -> TacticalModelV3.restore(Arrays.copyOf(original, original.length - 4), digest(original)), "truncated payload accepted");
		final byte[] version = model.bytes();
		ByteBuffer.wrap(version).putInt(4, 99);
		reject(() -> TacticalModelV3.restore(version, digest(version)), "unknown version accepted with correct SHA");
		final byte[] dimensions = model.bytes();
		ByteBuffer.wrap(dimensions).putInt(8, 129);
		reject(() -> TacticalModelV3.restore(dimensions, digest(dimensions)), "wrong dimensions accepted with correct SHA");
		final byte[] fingerprint = model.bytes();
		fingerprint[40] ^= 1;
		reject(() -> TacticalModelV3.restore(fingerprint, digest(fingerprint)), "wrong algorithm fingerprint accepted with correct SHA");
		final byte[] nonfinite = model.bytes();
		ByteBuffer.wrap(nonfinite).putFloat(nonfinite.length - 4, Float.NaN);
		reject(() -> TacticalModelV3.restore(nonfinite, digest(nonfinite)), "NaN weight accepted with correct SHA");
		final float[] state = row.state();
		state[0] = Float.NaN;
		reject(() -> model.encode(state), "NaN state accepted");
		reject(() -> model.encode(new float[127]), "wrong state size accepted");
		reject(() -> model.scoreEncoded(new float[31], row.action()), "wrong encoded size accepted");
		final float[] action = row.action();
		action[0] = 1.1f;
		reject(() -> model.score(row.state(), action), "unnormalized action accepted");
		final float[] copied = row.state();
		copied[0] = 0;
		check(row.state()[0] != 0, "Example state is mutable");
		final float[] constructorState = row.state();
		final float[] constructorAction = row.action();
		final var owned = new Example(constructorState, constructorAction, row.reward());
		constructorState[0] = 0;
		constructorAction[0] = 0;
		check(owned.state()[0] != 0 && owned.action()[0] != 0, "Example retained caller-owned input");
		reject(() -> new Example(row.state(), row.action(), Float.NaN), "NaN reward accepted");
		reject(() -> model.loss(List.of()), "empty loss split accepted");
		System.out.printf(Locale.ROOT, "LEARNING003_CHECKPOINT bytes=%d sha=%s immutable=PASS corruption=PASS%n", model.bytes().length, model.sha256());
	}

	private static void mathLearning()
	{
		// Removing state/action interaction, freezing any dense layer, or using tuning as train must fail this contract.
		final var train = List.copyOf(fixture(32, 101));
		final var tuning = List.copyOf(fixture(8, 202));
		final var audit = fixture(8, 303);
		final long start = System.nanoTime();
		final var fit = TacticalModelV3.train(train, tuning, 183);
		final double initialAudit = TacticalModelV3.initialized(183).loss(audit);
		final double auditLoss = fit.model().loss(audit);
		System.out.printf(Locale.ROOT, "LEARNING003_MATH_FIT epochs=%d tuningInitial=%.8f tuningLoss=%.8f trainLoss=%.8f auditInitial=%.8f auditLoss=%.8f elapsedSeconds=%.3f%n",
			fit.epochs(), fit.initialLoss(), fit.loss(), fit.model().loss(train), initialAudit, auditLoss, (System.nanoTime() - start) / 1e9);
		check(fit.epochs() > 0 && fit.loss() < fit.initialLoss() * .4 && auditLoss < initialAudit * .4, "math state/action interaction did not generalize");
		final byte[] before = TacticalModelV3.initialized(183).bytes();
		final byte[] after = fit.model().bytes();
		final int header = after.length - 1185377 * Float.BYTES;
		int offset = header;
		for (int count : new int[] {66048, 1050624, 65568, 3104, 33})
		{
			check(!Arrays.equals(before, offset, offset + count * 4, after, offset, offset + count * 4), "a dense trainable layer was frozen");
			offset += count * 4;
		}
		final var second = TacticalModelV3.train(train, tuning, 183);
		check(Arrays.equals(after, second.model().bytes()) && fit.epochs() == second.epochs(), "training is nondeterministic");
		final var contrary = tuning.stream().map(row -> new Example(row.state(), row.action(), -row.reward())).toList();
		final var rejected = TacticalModelV3.train(train, contrary, 183);
		check(rejected.loss() <= rejected.initialLoss(), "tuning selected a worse checkpoint");
		reject(() -> TacticalModelV3.train(train, List.of(), 183), "missing tuning split accepted");
		System.out.printf(Locale.ROOT, "LEARNING003_MATH origin=SYNTHETIC purpose=MATH_ONLY train=%d tuning=%d audit=%d epochs=%d tuningInitial=%.8f tuningLoss=%.8f auditInitial=%.8f auditLoss=%.8f runs=3 elapsedSeconds=%.3f%n",
			train.size(), tuning.size(), audit.size(), fit.epochs(), fit.initialLoss(), fit.loss(), initialAudit, auditLoss, (System.nanoTime() - start) / 1e9);
	}

	private static List<Example> fixture(int size, long seed)
	{
		final var random = new Random(seed);
		final var rows = new ArrayList<Example>();
		for (int i = 0; i < size; i++)
		{
			final float[] state = new float[128];
			final float[] action = new float[64];
			for (int j = 0; j < state.length; j++) { state[j] = (random.nextFloat() - .5f) * .04f; }
			for (int j = 0; j < action.length; j++) { action[j] = (random.nextFloat() - .5f) * .04f; }
			state[0] = (i & 1) == 0 ? -.8f : .8f;
			action[0] = (i & 2) == 0 ? -.8f : .8f;
			rows.add(new Example(state, action, Math.signum(state[0] * action[0]) * .7f));
		}
		return rows;
	}

	private static void benchmark()
	{
		final var model = TacticalModelV3.initialized(183);
		final var actors = fixture(128, 403);
		final var actions = fixture(16, 503);
		final var thread = ManagementFactory.getThreadMXBean();
		final var allocation = thread instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported() ? bean : null;
		if (thread.isCurrentThreadCpuTimeSupported() && !thread.isThreadCpuTimeEnabled()) { thread.setThreadCpuTimeEnabled(true); }
		if (allocation != null && !allocation.isThreadAllocatedMemoryEnabled()) { allocation.setThreadAllocatedMemoryEnabled(true); }
		final float[][] states = actors.stream().map(Example::state).toArray(float[][]::new);
		final float[][] candidates = actions.stream().map(Example::action).toArray(float[][]::new);
		for (int count : new int[] {4, 8, 16})
		{
			final long warmUntil = System.nanoTime() + 2_000_000_000L;
			int actor = 0;
			while (System.nanoTime() < warmUntil) { decision(model, states[actor++ & 127], candidates, count); }
			final long[] times = new long[8192];
			final long allocatedBefore = allocation == null ? -1 : allocation.getThreadAllocatedBytes(Thread.currentThread().threadId());
			final long cpuBefore = thread.isCurrentThreadCpuTimeSupported() ? thread.getCurrentThreadCpuTime() : -1;
			final long start = System.nanoTime();
			for (int i = 0; i < times.length; i++)
			{
				final long decisionStart = System.nanoTime();
				decision(model, states[i & 127], candidates, count);
				times[i] = System.nanoTime() - decisionStart;
			}
			final long elapsed = System.nanoTime() - start;
			final long cpu = cpuBefore < 0 ? -1 : thread.getCurrentThreadCpuTime() - cpuBefore;
			final long allocated = allocation == null ? -1 : allocation.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocatedBefore;
			Arrays.sort(times);
			System.out.printf(Locale.ROOT, "LEARNING003_MODEL_BENCH actors=128 actions=%d samples=%d warmupSeconds=2 p50Micros=%.3f p95Micros=%.3f p99Micros=%.3f worstMicros=%.3f cpuNanosPerDecision=%.1f allocatedBytesPerDecision=%.1f wallSeconds=%.3f snapshot=NOT_MEASURED dispatch=NOT_MEASURED%n",
				count, times.length, times[times.length / 2] / 1e3, times[(int) (times.length * .95)] / 1e3, times[(int) (times.length * .99)] / 1e3, times[times.length - 1] / 1e3,
				cpu < 0 ? -1 : cpu / (double) times.length, allocated < 0 ? -1 : allocated / (double) times.length, elapsed / 1e9);
		}
	}

	private static void decision(TacticalModelV3 model, float[] state, float[][] actions, int count)
	{
		final float[] encoded = model.encode(state);
		float sum = 0;
		for (int i = 0; i < count; i++) { sum += model.scoreEncoded(encoded, actions[i]); }
		_sink = sum;
	}

	private static String digest(byte[] bytes)
	{
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (Exception e) { throw new AssertionError(e); }
	}

	private static void reject(Runnable action, String message)
	{
		try { action.run(); }
		catch (IllegalArgumentException expected) { return; }
		throw new AssertionError(message);
	}

	private static void check(boolean condition, String message)
	{
		if (!condition) { throw new AssertionError(message); }
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.zip.ZipFile;

import org.l2jmobius.gameserver.phantoms.learning.TacticalModelV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalModelV3.Example;
import org.l2jmobius.gameserver.phantoms.learning.TacticalModelV3.Optimizer;

/** Offline mathematics/compatibility only. No synthetic fixture is native game evidence. */
public final class PhantomLearning004ModelChecks
{
	private static final String ACCEPTED_SHA = "6b91706463f4e24f89f1c4f8be3a82d9ee58eadb07a4254c79449665e3fa97dc";

	public static void main(String[] args) throws Exception
	{
		final var accepted = legacyImport();
		splitResume(accepted);
		incrementalFit(accepted);
		System.out.println("LEARNING004_MODEL checks=3 PASS native=NOT_RUN synthetic=MATH_ONLY");
	}

	private static TacticalModelV3 legacyImport() throws Exception
	{
		final Path module = Path.of(System.getProperty("phantom.module.root", "L2J_Mobius_CT_2.6_HighFive"));
		final Path archive = module.resolve("docs/phantoms/tasks/PHANTOM-LEARNING-003/artifacts/accepted-model-2/model-2.zip");
		final byte[] bytes;
		try (var zip = new ZipFile(archive.toFile()); var input = zip.getInputStream(zip.getEntry("weights.bin")))
		{
			bytes = input.readAllBytes();
		}
		final var model = TacticalModelV3.restore(bytes, ACCEPTED_SHA);
		check(Arrays.equals(bytes, model.bytes()), "accepted003 model bytes changed");
		final var initial = TacticalModelV3.startOptimization(model, 183);
		check(initial.updates() == 0 && initial.completedEpochs() == 0, "invented historical Adam state");
		check(Arrays.equals(bytes, initial.model().bytes()), "warm-start reset accepted weights");
		System.out.println("LEARNING004_IMPORT sha=" + model.sha256() + " migration=MOMENTS_INITIALIZED originalBytes=UNCHANGED");
		return model;
	}

	private static void splitResume(TacticalModelV3 accepted)
	{
		// Resetting moments, dropping update count or aliasing arrays must change the second real Adam update.
		final var initial = TacticalModelV3.startOptimization(accepted, 183);
		final var firstRows = examples(16, 401);
		final var secondRows = examples(16, 402);
		final var first = TacticalModelV3.optimizeBatch(initial, firstRows);
		final var continuous = TacticalModelV3.optimizeBatch(first, secondRows);
		final byte[] saved = first.bytes();
		final var restored = Optimizer.restore(saved, first.sha256());
		final var resumed = TacticalModelV3.optimizeBatch(restored, secondRows);
		check(Arrays.equals(continuous.bytes(), resumed.bytes()), "split resume differs from uninterrupted Adam");
		check(resumed.updates() == 2, "Adam update count lost or double applied");
		check(accepted.sha256().equals(ACCEPTED_SHA) && initial.model().sha256().equals(ACCEPTED_SHA), "optimizer mutated immutable parent");
		final var reset = TacticalModelV3.optimizeBatch(TacticalModelV3.startOptimization(first.model(), 183), secondRows);
		check(!Arrays.equals(reset.model().bytes(), resumed.model().bytes()), "test cannot detect lost Adam moments");
		saved[saved.length - 1] ^= 1;
		reject(() -> Optimizer.restore(saved, first.sha256()), "forged optimizer SHA accepted");
		check(Arrays.equals(first.bytes(), restored.bytes()), "restore retained mutable caller bytes");
		final byte[] truncated = Arrays.copyOf(first.bytes(), first.bytes().length - 4);
		reject(() -> Optimizer.restore(truncated, digest(truncated)), "partial checkpoint accepted");
		final byte[] version = first.bytes();
		ByteBuffer.wrap(version).putInt(4, 99);
		reject(() -> Optimizer.restore(version, digest(version)), "unknown optimizer schema accepted");
		final byte[] nonfinite = first.bytes();
		ByteBuffer.wrap(nonfinite).putFloat(nonfinite.length - 4, Float.NaN);
		reject(() -> Optimizer.restore(nonfinite, digest(nonfinite)), "nonfinite Adam state accepted");
		System.out.println("LEARNING004_RESUME updates=2 bitExact=PASS optimizerSha=" + resumed.sha256() + " modelSha=" + resumed.model().sha256());
	}

	private static void incrementalFit(TacticalModelV3 accepted)
	{
		final var parent = TacticalModelV3.startOptimization(accepted, 183);
		final var train = examples(16, 501);
		final var tuning = examples(8, 502);
		final var fit = TacticalModelV3.continueTraining(parent, train, tuning);
		check(fit.fit().initialLoss() == accepted.loss(tuning), "fit did not start from accepted model");
		check(fit.continuation().updates() > 0 && fit.continuation().completedEpochs() > 0, "fit lost continuation optimizer");
		check(fit.fit().loss() <= fit.fit().initialLoss(), "tuning selection regressed");
		final var cold = Optimizer.restore(fit.continuation().bytes(), fit.continuation().sha256());
		final var next = TacticalModelV3.continueTraining(cold, train, tuning);
		check(next.fit().initialLoss() == cold.model().loss(tuning), "second fit randomized or substituted selected tuning weights");
		check(next.continuation().updates() > cold.updates() && next.continuation().completedEpochs() > cold.completedEpochs(), "new fit reset optimizer lineage");
		final var again = TacticalModelV3.continueTraining(parent, train, tuning);
		check(Arrays.equals(again.continuation().bytes(), fit.continuation().bytes()) && Arrays.equals(again.fit().model().bytes(), fit.fit().model().bytes()), "epoch order is nondeterministic");
		System.out.println("LEARNING004_INCREMENTAL parent=" + ACCEPTED_SHA + " epochs=" + cold.completedEpochs() + " updates=" + cold.updates() + " nextUpdates=" + next.continuation().updates() + " deterministic=PASS");
	}

	private static List<Example> examples(int count, long seed)
	{
		final var random = new Random(seed);
		final var rows = new ArrayList<Example>();
		for (int i = 0; i < count; i++)
		{
			final float[] state = new float[128], action = new float[64];
			for (int j = 0; j < state.length; j++) { state[j] = (random.nextFloat() - .5f) * .04f; }
			for (int j = 0; j < action.length; j++) { action[j] = (random.nextFloat() - .5f) * .04f; }
			state[0] = (i & 1) == 0 ? -.8f : .8f;
			action[0] = (i & 2) == 0 ? -.8f : .8f;
			rows.add(new Example(state, action, Math.signum(state[0] * action[0]) * .7f));
		}
		return List.copyOf(rows);
	}

	private static String digest(byte[] bytes)
	{
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (Exception failure) { throw new AssertionError(failure); }
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

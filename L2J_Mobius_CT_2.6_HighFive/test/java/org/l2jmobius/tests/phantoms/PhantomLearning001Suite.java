/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.lang.management.ManagementFactory;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.learning.LearningFeatures;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy.Example;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleAssignment;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;

public final class PhantomLearning001Suite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "learning001";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("held-out-generalization-and-real-combat-dispatch", _ -> heldOut());
		registry.add("warmed-128-actor-inference", _ -> benchmark());
		registry.add("party-receipt-survives-readiness-revision", _ -> partyReceiptAuthority());
		registry.add("residual-model-keeps-calibration-through-reload", _ -> residualRoundtrip());
	}

	private static void residualRoundtrip()
	{
		final var fixture = fixture();
		final var fit = NeuralPolicy.trainResidual(fixture.train(), fixture.tuning(), 183);
		final var audit = NeuralPolicy.audit(fit, fixture.heldOut(), 183);
		final var model = audit.model();
		check(model.algorithm() == NeuralPolicy.RESIDUAL_ALGORITHM && audit.loss() < .05, "calibrated model did not learn");
		final byte[] bytes = model.bytes();
		final var restored = NeuralPolicy.restore(bytes, model.sha256());
		check(restored.algorithm() == model.algorithm() && Arrays.equals(bytes, restored.bytes()), "calibration did not roundtrip");
		for (Example row : fixture.heldOut())
		{
			check(restored.score(row.features()) == model.score(row.features()) && Math.abs(restored.score(row.features())) <= 1, "restored prediction changed or is out of bounds");
		}
		assertDispatch(restored);
		java.nio.ByteBuffer.wrap(bytes).putDouble(NeuralPolicy.BYTE_SIZE, Double.NaN);
		try
		{
			NeuralPolicy.restore(bytes, NeuralPolicy.sha256(bytes));
			throw new AssertionError("non-finite calibration accepted with a matching SHA");
		}
		catch (IllegalArgumentException expected)
		{
		}
	}

	private static void partyReceiptAuthority()
	{
		final var leader = MemberRef.phantom(1, 101);
		final var learner = MemberRef.phantom(2, 102);
		final var source = new PhantomDomainRef("farm.party", "1,2");
		final String hash = "0".repeat(64);
		final var before = new RoleAssignment("farm.slot.1", "damage.ranged", learner, "combat.ranged_magic_damage", "prominence", 1000, "fixture");
		final var issued = new PartyState(hash, 7, 10, StateStatus.LEADER, leader, "", hash, List.of(leader, learner), List.of(),
			ObjectiveMode.GENERAL_PVE, source, List.of(), List.of(before), null, null, hash, hash, "");
		final var refreshed = new RoleAssignment("farm.slot.1", "damage.ranged", learner, "combat.ranged_magic_damage", "prominence", 800, "fixture");
		final var sameRoster = issued.committed(StateStatus.LEADER, issued.phantomMembers(), List.of(), List.of(refreshed), hash);
		check(issued.matchesLearningAuthority(sameRoster), "An exact Party cast lost its receipt after a readiness score refresh");
		final var nextGeneration = new PartyState(hash, 8, 11, StateStatus.LEADER, leader, "", hash, issued.phantomMembers(), List.of(),
			ObjectiveMode.GENERAL_PVE, source, List.of(), List.of(before), null, null, hash, hash, "");
		check(!issued.matchesLearningAuthority(nextGeneration), "A receipt survived a native roster generation change");
		final var changedRoster = issued.committed(StateStatus.LEADER, List.of(leader, MemberRef.phantom(3, 103)), List.of(), List.of(), hash);
		check(!issued.matchesLearningAuthority(changedRoster), "A receipt survived a different native roster");
	}

	public record Fixture(List<Example> train, List<Example> tuning, List<Example> heldOut) { }

	public static Fixture fixture()
	{
		final var train = new ArrayList<Example>();
		final var tuning = new ArrayList<Example>();
		final var heldOut = new ArrayList<Example>();
		for (int actor = 0; actor < 12; actor++)
		{
			for (int npc = 0; npc < 10; npc++)
			{
				final List<Example> split = switch ((actor + npc) % 5)
				{
					case 0 -> heldOut;
					case 1 -> tuning;
					default -> train;
				};
				for (int action = 0; action < 2; action++)
				{
					final boolean vulnerableToMagic = npc % 2 == 0;
					split.add(new Example(features(actor, npc, action), (action == 1) == vulnerableToMagic ? .8f : -.3f));
				}
			}
		}
		return new Fixture(List.copyOf(train), List.copyOf(tuning), List.copyOf(heldOut));
	}

	private static void heldOut()
	{
		final Fixture fixture = fixture();
		final var train = fixture.train();
		final var heldOut = fixture.heldOut();
		final var fitted = new HashSet<String>();
		for (Example example : train) { fitted.add(featureKey(example)); }
		for (Example example : fixture.tuning()) { fitted.add(featureKey(example)); }
		for (Example example : heldOut) { check(!fitted.contains(featureKey(example)), "audit feature vector leaked into training or tuning"); }
		final NeuralPolicy initial = NeuralPolicy.initial(183);
		final var result = NeuralPolicy.audit(NeuralPolicy.train(train, fixture.tuning(), 183), heldOut, 183);
		final NeuralPolicy trained = result.model();
		check(!Arrays.equals(initial.bytes(), trained.bytes()), "weights never changed");
		check(Arrays.equals(trained.bytes(), NeuralPolicy.train(train, fixture.tuning(), 183).model().bytes()), "training was not reproducible");
		check(result.loss() < .05 && result.loss() < result.initialLoss() / 2, "held-out loss did not improve");
		double learned = 0;
		double baseline = 0;
		for (int i = 0; i < heldOut.size(); i += 2)
		{
			final Example a = heldOut.get(i);
			final Example b = heldOut.get(i + 1);
			baseline += (a.reward() + b.reward()) / 2;
			learned += trained.score(a.features()) >= trained.score(b.features()) ? a.reward() : b.reward();
		}
		check(learned / (heldOut.size() / 2) > baseline / (heldOut.size() / 2) + .4, "no held-out decision improvement");
		final byte[] bytes = trained.bytes();
		check(Arrays.equals(bytes, NeuralPolicy.restore(bytes, trained.sha256()).bytes()), "model roundtrip");
		bytes[0] ^= 1;
		try
		{
			NeuralPolicy.restore(bytes, trained.sha256());
			throw new AssertionError("corrupt weights accepted");
		}
		catch (IllegalArgumentException expected)
		{
		}
		check(features(1, 2, 0)[1] == 1 && features(1, 2, 0)[3] == 0, "unknown and observed zero were conflated");
		assertDispatch(trained);
		System.out.printf(Locale.ROOT, "learning001 fixtures train=%d tuning=%d heldOut=%d initialMse=%.6f heldOutMse=%.6f rrReturn=%.4f learnedReturn=%.4f sha256=%s%n",
			train.size(), fixture.tuning().size(), heldOut.size(), result.initialLoss(), result.loss(), baseline / (heldOut.size() / 2), learned / (heldOut.size() / 2), trained.sha256());
	}

	public static void assertDispatch(NeuralPolicy trained)
	{
		PhantomCombatActionOwnershipSuite.learningDispatch(trained, features(0, 0, 0), features(0, 0, 1), features(5, 0, 0), features(5, 0, 1));
	}

	private static String featureKey(Example example)
	{
		return NeuralPolicy.sha256(LearningFeatures.encode(example.features()));
	}

	private static void benchmark()
	{
		final var fixture = fixture();
		final NeuralPolicy model = NeuralPolicy.trainResidual(fixture.train(), fixture.tuning(), 183).model();
		benchmark(model);
	}

	static void benchmark(NeuralPolicy model)
	{
		final LearningPolicy policy = new LearningPolicy(LearningPolicy.Mode.ON, 1, model, 4);
		final List<List<LearningPolicy.Candidate>> facts = new ArrayList<>(128);
		for (int actor = 0; actor < 128; actor++)
		{
			final List<LearningPolicy.Candidate> actions = new ArrayList<>(4);
			for (int action = 0; action < 4; action++)
			{
				actions.add(new LearningPolicy.Candidate(new SelectedSkill(action + 1, 1), features(actor, actor % 10, action), 0, 0, 0));
			}
			facts.add(List.copyOf(actions));
		}
		final SelectedSkill baseline = facts.getFirst().getFirst().skill();
		for (int i = 0; i < 30_000; i++)
		{
			policy.choose(facts.get(i & 127), baseline, i);
		}
		final var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
		final long thread = Thread.currentThread().threadId();
		final long[] times = new long[30_000];
		final long startCpu = bean.getCurrentThreadCpuTime();
		final long startAllocated = bean.getThreadAllocatedBytes(thread);
		final long heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
		for (int i = 0; i < times.length; i++)
		{
			final long start = System.nanoTime();
			policy.choose(facts.get(i & 127), baseline, i);
			times[i] = System.nanoTime() - start;
		}
		final double allocations = (bean.getThreadAllocatedBytes(thread) - startAllocated) / (double) times.length;
		final double cpuMs = (bean.getCurrentThreadCpuTime() - startCpu) / 1_000_000.0;
		Arrays.sort(times);
		final long p95 = times[times.length * 95 / 100];
		System.out.printf(Locale.ROOT, "learning001 benchmark actors=128 candidates=4 samples=%d p50=%.3fus p95=%.3fus p99=%.3fus alloc=%.1fB/op cpu=%.3fms weights=%dB featurePayload=%dB heapBefore=%dB heapAfter=%dB%n",
			times.length, times[times.length / 2] / 1000.0, p95 / 1000.0, times[times.length * 99 / 100] / 1000.0,
			allocations, cpuMs, model.bytes().length, 128 * 4 * LearningFeatures.SIZE * Float.BYTES, heapBefore, ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
		check(p95 < 1_000_000, "four-action p95 exceeds 1ms");
	}

	private static float[] features(int actor, int npc, int action)
	{
		return new LearningFeatures()
			.set(0, actor / 12.0).category(3, 6, actor % 3 + 1)
			.set(12, actor % 4 / 4.0).set(28, actor % 2)
			.set(41, (npc - 5) / 40.0).category(43, 4, npc + 1).set(42, npc % 2 == 0 ? -.8 : .8)
			.category(53, 1, 1339 + action).set(55, action == 1 ? .8 : -.8).set(56, action == 1 ? 1 : 0).copy();
	}

	private static void check(boolean ok, String message)
	{
		if (!ok)
		{
			throw new AssertionError(message);
		}
	}
}

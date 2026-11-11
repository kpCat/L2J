/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.*;

/** Warm CPU benchmark from preserved real observations. Logical actors and repeated action vectors are not native gameplay. */
public final class PhantomLearning003Performance
{
	private static volatile float blackhole;
	public static void main(String[] args) throws Exception
	{
		if (args.length != 2) { throw new IllegalArgumentException("Expected own installation and output JSON"); }
		final Path root = Path.of(args[0]).toAbsolutePath().normalize();
		final var store = new TacticalStoreV3(root); final var checkpoint = store.latestAccepted();
		if (checkpoint == null) { throw new IllegalStateException("Accepted native TEST checkpoint required"); }
		final var rows = checkpoint.rows();
		if (rows.isEmpty()) { throw new IllegalStateException("Preserved native observations required"); }
		final var model = checkpoint.model();
		final var observations = rows.stream().map(r -> r.receipt().attempt().observation()).toList();
		final var actions = rows.stream().map(r -> r.receipt().attempt().offered().stream().map(a -> a.features(r.receipt().attempt().observation())).toArray(float[][]::new)).toList();
		final var thread = ManagementFactory.getThreadMXBean();
		final var allocation = thread instanceof com.sun.management.ThreadMXBean extended ? extended : null;
		final var results = new ArrayList<String>();
		for (int count : new int[] {4, 8, 16})
		{
			final long warmDeadline = System.nanoTime() + 2_000_000_000L;
			int ordinal = 0;
			while (System.nanoTime() < warmDeadline) { decision(model, observations, actions, ordinal++, count, null, null); }
			final long[] total = new long[512], vector = new long[512], encode = new long[512];
			final long cpu = thread.getCurrentThreadCpuTime(), allocated = allocation == null || !allocation.isThreadAllocatedMemoryEnabled() ? -1 : allocation.getThreadAllocatedBytes(Thread.currentThread().threadId());
			final long start = System.nanoTime();
			for (int round = 0; round < 4; round++)
			{
				for (int actor = 0; actor < 128; actor++)
				{
					final int index = round * 128 + actor; final long before = System.nanoTime();
					decision(model, observations, actions, actor, count, vector, encode, index); total[index] = System.nanoTime() - before;
				}
			}
			final long wall = System.nanoTime() - start, cpuNanos = thread.getCurrentThreadCpuTime() - cpu;
			final long bytes = allocated < 0 ? -1 : allocation.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
			final String result = "{\"logicalActors\":128,\"candidates\":" + count + ",\"samples\":512,\"totalNanos\":" + summary(total)
				+ ",\"richStateVectorizationNanos\":" + summary(vector) + ",\"encoderNanos\":" + summary(encode) + ",\"wallNanos\":" + wall + ",\"cpuNanos\":" + cpuNanos + ",\"allocatedBytes\":" + (bytes < 0 ? "null" : bytes) + "}";
			results.add(result); System.out.println("V3_NATIVE_LAYOUT_BENCH " + result);
		}
		final double constant = rows.stream().filter(r -> r.split().equals("TRAIN")).mapToDouble(r -> r.receipt().reward()).average().orElseThrow();
		final var groups = new TreeMap<String, List<TacticalStoreV3.Row>>();
		for (var row : rows)
		{
			final int healerClass = row.receipt().attempt().observation().entity(row.receipt().attempt().observation().actorObjectId()).classId();
			groups.computeIfAbsent(row.split() + ".class" + healerClass, _ -> new ArrayList<>()).add(row);
		}
		final var coverage = new ArrayList<String>();
		for (var group : groups.entrySet())
		{
			double mse = 0, baseline = 0, useful = 0, mp = 0; int largeHeals = 0;
			for (var row : group.getValue())
			{
				final var example = row.example(); final double error = model.score(example.state(), example.action()) - example.reward();
				mse += error * error; final double baseError = constant - example.reward(); baseline += baseError * baseError;
				useful += row.receipt().actualHp(); mp += row.receipt().actualMp(); if (row.receipt().actualHp() > 2000) { largeHeals++; }
			}
			final int n = group.getValue().size();
			coverage.add("{\"group\":\"" + group.getKey() + "\",\"nativeRows\":" + n + ",\"candidateMse\":" + mse / n
				+ ",\"trainConstantMse\":" + baseline / n + ",\"meanUsefulHp\":" + useful / n + ",\"meanActualMp\":" + mp / n + ",\"largeHealCount\":" + largeHeals + "}");
		}
		final String json = "{\"schema\":3,\"nativeWorldCollection\":false,\"repeatedNativeActionVectors\":true,\"nativeRows\":" + rows.size()
			+ ",\"modelSha\":\"" + checkpoint.sha() + "\",\"jarSha\":\"" + NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar")))
			+ "\",\"pid\":" + ProcessHandle.current().pid() + ",\"measurements\":[" + String.join(",", results) + "],\"perClassQuality\":[" + String.join(",", coverage) + "]}";
		Files.writeString(Path.of(args[1]), json, StandardOpenOption.CREATE_NEW);
	}
	private static void decision(TacticalModelV3 model, List<TacticalObservationV3> observations, List<float[][]> actions, int actor, int count, long[] vector, long[] encoder)
	{ decision(model, observations, actions, actor, count, vector, encoder, 0); }
	private static void decision(TacticalModelV3 model, List<TacticalObservationV3> observations, List<float[][]> actions, int actor, int count, long[] vector, long[] encoder, int index)
	{
		final int row = actor % observations.size(); long start = System.nanoTime(); final var state = observations.get(row).state();
		if (vector != null) { vector[index] = System.nanoTime() - start; }
		start = System.nanoTime(); final var encoded = model.encode(state); if (encoder != null) { encoder[index] = System.nanoTime() - start; }
		final var offered = actions.get(row); float sum = 0; for (int candidate = 0; candidate < count; candidate++) { sum += model.scoreEncoded(encoded, offered[candidate % offered.length]); } blackhole = sum;
	}
	private static String summary(long[] values)
	{
		final long[] sorted = values.clone(); Arrays.sort(sorted); final int n = sorted.length;
		return "{\"p50\":" + sorted[(n - 1) / 2] + ",\"p95\":" + sorted[(int) Math.ceil(n * .95) - 1] + ",\"p99\":" + sorted[(int) Math.ceil(n * .99) - 1] + ",\"max\":" + sorted[n - 1] + "}";
	}
	private PhantomLearning003Performance() { }
}

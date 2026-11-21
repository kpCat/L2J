/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.phantoms.learning.ActionContinuationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionFeedbackV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionLearningV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionValueV4;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Entity;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Fact;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Role;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;

/** Offline continuation checks with synthetic receipts, never native campaign evidence. */
public final class PhantomLearning131BatchChecks
{
	private static final String NATIVE_SHA = "b".repeat(64);
	private static final long ELAPSED = 4_000_000_000L;
	private static final int BASE_COUNT = ActionValueV4.MAX_EVENTS - 256;
	private static final int BATCH_COUNT = 257;
	private static final int PREPARE_CRASH = 71;
	private static final int COMMIT_CRASH = 72;

	@FunctionalInterface
	private interface Probe { void run() throws Exception; }

	private PhantomLearning131BatchChecks() { }

	public static void main(String[] args) throws Exception
	{
		if (args.length > 0)
		{
			final Path root = Path.of(args[1]);
			switch (args[0])
			{
				case "cold" -> ActionContinuationV4.cold(root, args[2]);
				case "prepare-crash" ->
				{
					var shuffled = new ArrayList<>(samples(BASE_COUNT + 1, BATCH_COUNT));
					Collections.reverse(shuffled);
					shuffled.add(shuffled.getFirst());
					var prepared = ActionContinuationV4.prepareBatch(root, args[2], shuffled, NATIVE_SHA);
					System.out.println(prepared.head());
					System.out.flush();
					Runtime.getRuntime().halt(PREPARE_CRASH);
				}
				case "commit-crash" ->
				{
					ActionContinuationV4.commitBatch(root, args[2], args[3], samples(BASE_COUNT + 1, BATCH_COUNT), NATIVE_SHA);
					Runtime.getRuntime().halt(COMMIT_CRASH);
				}
				default -> throw new IllegalArgumentException("Unknown offline check");
			}
			return;
		}
		final Path root = Files.createTempDirectory("learning131-d7-").toRealPath();
		try
		{
			checkBatch(root);
			checkLegacyCommit(Files.createDirectory(root.resolve("legacy")));
			System.out.println("TASK131_D7_OFFLINE_PASS fit257=257 boundary=4095/4096/4097 preCommitCrash=71 postCommitCrash=72 legacyCommit=PASS pinFailure=REJECTED GS=0 SQL=0");
		}
		finally
		{
			try (var paths = Files.walk(root))
			{
				for (var path : paths.sorted(Comparator.reverseOrder()).toList())
				{
					Files.delete(path);
				}
			}
		}
	}

	private static void checkBatch(Path root) throws Exception
	{
		final var model = checkpoint(ActionValueV4.SCHEMA);
		rejects(() -> checkpoint("foreign-schema"), "Incompatible legacy schema accepted");
		final var before = ActionContinuationV4.initialize(root, model);
		final var all = samples(BASE_COUNT + 1, BATCH_COUNT);
		rejects(() -> ActionLearningV4.fit(model, all), "One FIT accepted more than 256 events");
		final var first256 = all.subList(0, 256);
		final var first = all.getFirst();
		final var last = all.getLast();
		rejects(() -> ActionContinuationV4.prepareBatch(root, before.head(), first256, NATIVE_SHA), "First import accepted a prefix of the drained episode");
		final var movedWindow = new ArrayList<>(all);
		final var shifted = settlement(last.episode(), last.episode().startedNanos() + 1, last.episode().settledNanos() + 1, last.episode().eventKeys());
		check(shifted.sha256().equals(last.episode().sha256()) && !shifted.equals(last.episode()), "Fixture must preserve the relative episode JSON hash");
		movedWindow.set(256, sample(BASE_COUNT + BATCH_COUNT, shifted, "TRAIN", 220));
		rejects(() -> ActionContinuationV4.prepareBatch(root, before.head(), movedWindow, NATIVE_SHA), "Distinct native time windows shared the same episode ID");
		final var changedTail = new ArrayList<>(all);
		changedTail.set(256, sample(BASE_COUNT + BATCH_COUNT, last.episode(), "TRAIN", 900));
		final var changedDuplicate = new ArrayList<>(all);
		changedDuplicate.add(changedTail.getLast());
		rejects(() -> ActionContinuationV4.prepareBatch(root, before.head(), changedDuplicate, NATIVE_SHA), "Changed duplicate accepted before prepare");
		final var mixed = new ArrayList<>(all);
		mixed.set(256, sample(BASE_COUNT + BATCH_COUNT, last.episode(), "AUDIT", 900));
		rejects(() -> ActionContinuationV4.prepareBatch(root, before.head(), mixed, NATIVE_SHA), "AUDIT tail was accepted for TRAIN");

		final String target = child(PREPARE_CRASH, "prepare-crash", root, before.head());
		check(ActionContinuationV4.load(root).head().equals(before.head()), "A prepared chunk advanced HEAD after a crash");
		rejects(() -> ActionContinuationV4.prepareBatch(root, before.head(), changedTail, NATIVE_SHA), "Changed source reused a prepared batch");
		final var shiftedBatch = new ArrayList<ActionFeedbackV4>();
		for (int index = BASE_COUNT + 1; index <= BASE_COUNT + BATCH_COUNT; index++)
		{
			var shiftedSample = sample(index, shifted, "TRAIN", 220 + index % 61);
			check(shiftedSample.fingerprint().equals(all.get(shiftedBatch.size()).fingerprint()), "Fixture must preserve the original action fingerprint");
			shiftedBatch.add(shiftedSample);
		}
		rejects(() -> ActionContinuationV4.prepareBatch(root, before.head(), shiftedBatch, NATIVE_SHA), "A consistent absolute time shift reused a prepared batch");
		rejects(() -> ActionContinuationV4.verifyPreparedBatch(root, before.head(), target, shiftedBatch, NATIVE_SHA), "Cold verification accepted a shifted native window");
		final var retried = ActionContinuationV4.prepareBatch(root, before.head(), all, NATIVE_SHA);
		check(retried.head().equals(target) && retried.sequence() == 2 && retried.globalUpdates() == 4097, "Shuffled pre-commit retry changed candidate or lost the tail");
		ActionContinuationV4.verifyPreparedBatch(root, before.head(), target, all, NATIVE_SHA);
		rejects(() -> ActionContinuationV4.verifyPreparedBatch(root, before.head(), target, first256, NATIVE_SHA), "Truncated batch verified");
		rejects(() -> ActionContinuationV4.commitBatch(root, before.head(), target, all, NATIVE_SHA), "Published before separate-JVM verification");

		final var near = ActionLearningV4.fit(model, first256.subList(0, 255));
		check(ActionValueV4.restore(near.bytes(), near.sha256()).updates() == 4095, "4095 did not preserve the V4 codec");
		final var prefix = ActionContinuationV4.prepare(root, before.head(), first256, NATIVE_SHA);
		check(prefix.globalUpdates() == 4096 && prefix.model().updates() == 4096 && prefix.segment() == 0, "4096 rolled over early");
		final var expected = ActionLearningV4.fit(ActionContinuationV4.segment(prefix.model()), List.of(last));
		check(retried.segment() == 1 && retried.model().updates() == 1 && retried.model().sha256().equals(expected.sha256()),
			"4097 did not roll over to the deterministically ordered tail");
		child(0, "cold", root, prefix.head());
		rejects(() -> ActionContinuationV4.commit(root, before.head(), prefix.head()), "Legacy commit published an incomplete TRAIN batch");
		rejects(() -> ActionContinuationV4.commitBatch(root, before.head(), prefix.head(), all, NATIVE_SHA), "Committed a prefix instead of the full TRAIN batch");
		final Path batchPin;
		try (var files = Files.list(root))
		{
			batchPin = files.filter(path -> path.getFileName().toString().startsWith("train-batch-")).findFirst().orElseThrow();
		}
		final byte[] originalPin = Files.readAllBytes(batchPin);
		Files.writeString(batchPin, "corrupted");
		rejects(() -> ActionContinuationV4.verifyPreparedBatch(root, before.head(), target, all, NATIVE_SHA), "Corrupted input pin verified");
		rejects(() -> ActionContinuationV4.commit(root, before.head(), prefix.head()), "Corrupted input pin let legacy commit publish a prefix");
		Files.delete(batchPin);
		rejects(() -> ActionContinuationV4.verifyPreparedBatch(root, before.head(), target, all, NATIVE_SHA), "Missing input pin verified");
		rejects(() -> ActionContinuationV4.commit(root, before.head(), prefix.head()), "Missing input pin let legacy commit publish a prefix");
		check(ActionContinuationV4.load(root).head().equals(before.head()), "Input pin failure changed HEAD");
		Files.write(batchPin, originalPin);
		final Path chunkPin;
		try (var files = Files.list(root))
		{
			chunkPin = files.filter(path -> path.getFileName().toString().startsWith("prepared-")).filter(path -> readPin(path).equals(target)).findFirst().orElseThrow();
		}
		final byte[] originalChunkPin = Files.readAllBytes(chunkPin);
		Files.delete(chunkPin);
		rejects(() -> ActionContinuationV4.verifyPreparedBatch(root, before.head(), target, all, NATIVE_SHA), "Unpublished final chunk verified");
		Files.write(chunkPin, originalChunkPin);
		ActionContinuationV4.verifyPreparedBatch(root, before.head(), target, all, NATIVE_SHA);

		child(0, "cold", root, target);
		child(COMMIT_CRASH, "commit-crash", root, before.head(), target);
		final var committed = ActionContinuationV4.load(root);
		check(committed.head().equals(target) && committed.events().size() == 4097 && committed.globalUpdates() == 4097, "Published state lost updates after a crash");
		final String historicalEpisode = key(100000);
		check(committed.episodes().get(historicalEpisode).equals("TRAIN"), "Rollover lost the original TRAIN episode");
		final var contaminated = new ActionExplorationV4.Plan(2, "MODEL_V2", 0, 0, 0, "AUDIT", historicalEpisode, NATIVE_SHA,
			expected.sha256(), List.of("1000:1", "7000:1"));
		rejects(() -> ActionExplorationV4.frozen(contaminated, root, target), "Rolled TRAIN episode was admitted to AUDIT");
		final var holdout = new ActionExplorationV4.Plan(2, "MODEL_V2", 0, 0, 0, "AUDIT", key(100001), NATIVE_SHA,
			expected.sha256(), contaminated.skills());
		ActionExplorationV4.frozen(holdout, root, target);
		for (var sample : all)
		{
			check(sample.fingerprint().equals(committed.events().get(sample.row().eventKey())), "A source event was lost");
		}
		check(ActionContinuationV4.commitBatch(root, before.head(), target, all, NATIVE_SHA).head().equals(target), "Commit retry changed HEAD");
		rejects(() -> ActionContinuationV4.commitBatch(root, before.head(), target, shiftedBatch, NATIVE_SHA), "Commit retry accepted a shifted native window");
		check(ActionContinuationV4.prepareBatch(root, target, all, NATIVE_SHA).head().equals(target), "Exact replay received new credit");
		check(ActionContinuationV4.prepareBatch(root, target, List.of(first), NATIVE_SHA).head().equals(target), "Exact single-action replay changed HEAD");
		final var changedHistory = List.of(sample(BASE_COUNT + 1, first.episode(), "TRAIN", 900));
		rejects(() -> ActionContinuationV4.prepareBatch(root, target, changedHistory, NATIVE_SHA), "Changed historical bytes passed after rollover");
		final var extendedKeys = new ArrayList<>(first.episode().eventKeys());
		extendedKeys.add(key(BASE_COUNT + BATCH_COUNT + 1));
		final var extended = settlement(first.episode(), first.episode().startedNanos(), first.episode().settledNanos(), extendedKeys);
		final var fabricatedTail = List.of(sample(BASE_COUNT + BATCH_COUNT + 1, extended, "TRAIN", 200));
		rejects(() -> ActionContinuationV4.prepareBatch(root, target, fabricatedTail, NATIVE_SHA), "New credit was appended to a published episode");
	}

	private static String readPin(Path path)
	{
		try { return Files.readString(path); }
		catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
	}

	private static TacticalEpisodeV3 settlement(TacticalEpisodeV3 old, long started, long settled, List<String> keys)
	{
		return new TacticalEpisodeV3(old.id(), old.ownerNonce(), old.scope(), started, settled, old.reason(), old.actors(), old.kills(), keys,
			old.exactObservedDamage(), old.usefulObservedHeal(), old.exactObservedMp(), old.observedDeaths());
	}

	private static void checkLegacyCommit(Path root) throws Exception
	{
		final var before = ActionContinuationV4.initialize(root, ActionValueV4.empty());
		final var train = samples(1, 1).getFirst();
		final var audit = sample(1, train.episode(), "AUDIT", 220);
		final var prepared = ActionContinuationV4.prepare(root, before.head(), List.of(audit), NATIVE_SHA);
		ActionContinuationV4.verifyPrepared(root, before.head(), prepared.head(), List.of(audit), NATIVE_SHA);
		child(0, "cold", root, prepared.head());
		final var committed = ActionContinuationV4.commit(root, before.head(), prepared.head());
		check(committed.head().equals(prepared.head()) && committed.globalUpdates() == 0
			&& committed.episodes().get(audit.episodeId()).equals("AUDIT"), "Legacy AUDIT ledger behavior changed");
	}

	private static String child(int expected, String command, Path root, String... heads) throws Exception
	{
		final var args = new ArrayList<>(List.of(ProcessHandle.current().info().command().orElseThrow(), "-cp",
			System.getProperty("java.class.path"), PhantomLearning131BatchChecks.class.getName(), command, root.toString()));
		args.addAll(List.of(heads));
		final var process = new ProcessBuilder(args).redirectErrorStream(true).start();
		if (!process.waitFor(45, TimeUnit.SECONDS))
		{
			process.destroyForcibly();
			throw new AssertionError(command + " timed out");
		}
		try (var stream = process.getInputStream())
		{
			final String output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
			check(process.exitValue() == expected, command + " exited " + process.exitValue() + ": " + output);
			return output;
		}
	}

	/** Baseline checkpoint fixture with the existing V4 byte schema and a near-full event ledger. */
	private static ActionValueV4 checkpoint(String schema) throws Exception
	{
		final var buffer = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(buffer))
		{
			out.writeInt(0x41563431);
			out.writeUTF(schema);
			out.writeUTF("0".repeat(64));
			out.writeUTF(NATIVE_SHA);
			out.writeLong(BASE_COUNT);
			for (int index = 0; index < ActionValueV4.COEFFICIENTS; index++)
			{
				out.writeDouble(0);
			}
			out.writeInt(BASE_COUNT);
			for (int index = 1; index <= BASE_COUNT; index++)
			{
				out.writeUTF(key(index));
				out.writeUTF(NATIVE_SHA);
			}
			out.writeInt(1);
			out.writeUTF(key(100000));
			out.writeUTF("TRAIN");
		}
		final byte[] bytes = buffer.toByteArray();
		return ActionValueV4.restore(bytes, TacticalObservationV3.hash(bytes));
	}

	private static void rejects(Probe action, String message) throws Exception
	{
		try
		{
			action.run();
		}
		catch (IllegalArgumentException expected)
		{
			return;
		}
		throw new AssertionError(message);
	}

	private static List<ActionFeedbackV4> samples(int start, int count)
	{
		final var keys = new ArrayList<String>();
		for (int index = start; index < start + count; index++)
		{
			keys.add(key(index));
		}
		final var episode = new TacticalEpisodeV3(key(100000 + start), "1".repeat(32), "PARTY", 100, 200, "NATIVE_KILL",
			List.of(new TacticalEpisodeV3.Actor(1000, 100, 10, 105, 77, 600, 200, false, 0L, 0L)),
			List.of(new TacticalEpisodeV3.Kill(102, 12000, 12, 0, 100, 1)), keys, 0, count * 250, count * 8, 0);
		final var values = new ArrayList<ActionFeedbackV4>();
		for (int index = start; index < start + count; index++)
		{
			values.add(sample(index, episode, "TRAIN", 220 + index % 61));
		}
		return values;
	}

	private static ActionFeedbackV4 sample(int index, TacticalEpisodeV3 episode, String split, double healed)
	{
		final var facts = Map.of(Field.HP, Fact.known(.3), Field.MAX_HP, Fact.known(2000), Field.LEVEL, Fact.known(77));
		final var self = new Entity(Role.SELF, 100, 10, 0, 105, facts, List.of(), List.of(), List.of(), List.of());
		final var target = new Entity(Role.ALLY, 101, 11, 0, 94, facts, List.of(), List.of(), List.of(), List.of());
		final var observation = new TacticalObservationV3(7, 150, 1000, 100, 10, "party:offline", List.of(self, target));
		final var a = new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", 1000, 100, 10, 7, 101, 11, "party:offline", 1217, 1, 8, 4000, 900, false, true);
		final var b = new LegalActionV3(LegalActionV3.Family.HEAL, "PartyTactics", 1000, 100, 10, 7, 101, 11, "party:offline", 1218, 1, 8, 4000, 900, false, true);
		final var attempt = new TacticalReceiptV3.Attempt(observation, List.of(a, b), a, b, 2);
		final var owner = new HealEvidenceV3.SourceOwner(40, "offline-d7");
		final var receipt = new TacticalReceiptV3(key(index), attempt, 600, 600 + healed, healed, 8, ELAPSED,
			TacticalReceiptV3.reward(healed, 2000, 8, ELAPSED), owner);
		final byte[] bytes = TacticalStoreV3.encode(new TacticalStoreV3.Row(split, "offline-d7|" + episode.id(), receipt));
		return ActionFeedbackV4.nativeAction(bytes, TacticalObservationV3.hash(bytes), episode, owner, NATIVE_SHA);
	}

	private static String key(int value)
	{
		return "0".repeat(56) + HexFormat.of().toHexDigits(value);
	}

	private static void check(boolean value, String message)
	{
		if (!value)
		{
			throw new AssertionError(message);
		}
	}
}

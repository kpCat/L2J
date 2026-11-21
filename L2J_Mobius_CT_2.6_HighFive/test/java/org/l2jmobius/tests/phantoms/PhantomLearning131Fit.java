/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.episode;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.map;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.number;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.parse;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.string;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.learning.ActionContinuationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExperimentReceiptV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionFeedbackV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionLearningV4;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;

/** Offline TRAIN ingress for a drained owned bootstrap, with independent owner-origin pins. */
public final class PhantomLearning131Fit
{
	private static final int META_LIMIT = 1_000_000;
	private static final long WINDOWS_EPOCH_OFFSET = 11_644_473_600L;
	private static final String NATIVE_CLASS = "org.l2jmobius.tests.phantoms.PhantomLearning131Native";

	private record Seal(Map<String, String> meta, List<String[]> rows) { }
	private record Batch(Path ledger, String expectedHead, String learnerSha, String nativeSha, List<ActionFeedbackV4> samples) { }

	private PhantomLearning131Fit() { }

	public static void main(String[] args) throws Exception
	{
		run(args);
	}

	static void run(String[] args) throws Exception
	{
		require(args.length == 4 && args[0].equals("prepare") || args.length == 5 && Set.of("cold", "commit").contains(args[0]), "USAGE");
		final Batch batch = readBatch(Path.of(args[1]), Path.of(args[2]), digest(args[3]));
		final var current = ActionContinuationV4.load(batch.ledger());
		if (current.head().equals(batch.expectedHead()))
		{
			require(current.model().sha256().equals(batch.learnerSha()), "LEARNER_HEAD");
		}
		switch (args[0])
		{
			case "prepare" ->
			{
				final var candidate = ActionContinuationV4.prepareBatch(batch.ledger(), batch.expectedHead(), batch.samples(), batch.nativeSha());
				require(!candidate.head().equals(batch.expectedHead()), "TRAIN_BATCH_ALREADY_CREDITED");
				System.out.println("TASK131_FIT_PREPARED head=" + candidate.head() + " model=" + candidate.model().sha256());
			}
			case "cold" ->
			{
				final String target = target(args[4], batch.expectedHead());
				ActionContinuationV4.verifyPreparedBatch(batch.ledger(), batch.expectedHead(), target, batch.samples(), batch.nativeSha());
				ActionContinuationV4.cold(batch.ledger(), target);
				System.out.println("TASK131_FIT_COLD_VERIFIED head=" + target);
			}
			case "commit" ->
			{
				final String target = target(args[4], batch.expectedHead());
				final var committed = ActionContinuationV4.commitBatch(batch.ledger(), batch.expectedHead(), target, batch.samples(), batch.nativeSha());
				System.out.println("TASK131_FIT_COMMITTED head=" + committed.head() + " model=" + committed.model().sha256() + " updates=" + committed.globalUpdates());
			}
			default -> throw new IllegalStateException("Unsupported FIT command");
		}
	}

	private static Batch readBatch(Path boot, Path origin, String originSha) throws Exception
	{
		physical(boot);
		require(Files.isDirectory(boot, LinkOption.NOFOLLOW_LINKS) && boot.getNameCount() >= 7, "TRAIN_BOOT_PATH");
		final Path campaign = boot.getParent().getParent();
		final Path task = campaign.getParent().getParent();
		final String bootId = leaf(boot);
		require(bootId.matches("[0-9]{5}") && Integer.parseInt(bootId) > 0 && Integer.parseInt(bootId) <= 12
			&& leaf(boot.getParent()).equals("boots") && leaf(campaign).matches("train-0[1-6]") && leaf(campaign.getParent()).equals("campaigns")
			&& leaf(task).equals("TASK131") && leaf(task.getParent()).equals("logs") && leaf(task.getParent().getParent()).equals(".phantom-local"), "TRAIN_BOOT_SCOPE");
		require(origin.isAbsolute() && task.resolve("fit-pins").equals(origin.getParent()), "INDEPENDENT_ORIGIN_PATH");
		final var pins = json(checked(origin, originSha, META_LIMIT));
		require(string(pins, "schema").equals("TRAINING_FIT_ORIGIN131_V1") && string(pins, "phase").equals("TRAIN"), "TRAIN_ORIGIN_SCHEMA");
		final String manifestSha = hashField(pins, "manifestSha");
		final String expectedHead = hashField(pins, "expectedHead"), learner = hashField(pins, "learnerSha"), opponent = hashField(pins, "opponentSha");
		require(hashField(pins, "rollbackHead").equals(expectedHead) && hashField(pins, "rollbackModelSha").equals(learner), "ROLLBACK_PIN");
		final var scenes = map(pins.get("scenes"));
		require(!scenes.isEmpty() && scenes.size() <= 6, "TRAIN_SCENE_QUOTA");
		final var manifest = json(checked(campaign.resolve("campaign.json"), manifestSha, META_LIMIT));
		final int cycle = Math.toIntExact(number(manifest, "cycle"));
		final String jar = hashField(manifest, "gameJarSha");
		final String source = string(manifest, "sourceCommit");
		require(string(manifest, "schema").equals("TRAINING_COMBAT131_V1") && string(manifest, "phase").equals("TRAIN")
			&& string(manifest, "campaignId").equals(leaf(campaign)) && String.format(Locale.ROOT, "train-%02d", cycle).equals(leaf(campaign))
			&& number(manifest, "auditBank") == 0 && source.matches("[a-f0-9]{40}") && hashField(manifest, "modelSha").equals(learner), "TRAIN_MANIFEST_PIN");

		final var close = json(checked(boot.resolve("closed.json"), hashField(pins, "closedSha"), META_LIMIT));
		final String reservationSha = hashField(close, "reservationSha");
		require(string(close, "schema").equals("TRAINING_CLOSED131_V1") && string(close, "status").equals("COMPLETED") && number(close, "exitCode") == 0
			&& string(close, "reason").equals("NATIVE_DRAIN_ACK_VERIFIED") && string(close, "control").equals("none")
			&& hashField(close, "masterSha").equals(hashField(map(manifest.get("configShas")), "game/config/Custom/PhantomPlayers.ini")), "TRAIN_NOT_DRAINED");
		final var reservation = json(checked(boot.resolve("reserved.json"), reservationSha, META_LIMIT));
		final String nonce = string(reservation, "nonce");
		final var launch = properties(checked(boot.resolve("launch131.properties"), hashField(reservation, "launchSha"), META_LIMIT));
		final var authority = json(checked(campaign.resolve("authorization.json"), hashField(reservation, "authoritySha"), META_LIMIT));
		require(string(reservation, "schema").equals("TRAINING_RESERVED131_V1") && nonce.matches("[a-f0-9]{32}")
			&& string(reservation, "bootId").equals(bootId) && number(reservation, "sequence") == Integer.parseInt(bootId)
			&& hashField(reservation, "manifestSha").equals(manifestSha) && string(authority, "schema").equals("TASK131_LAUNCH_AUTHORITY_V1")
			&& string(authority, "authority").equals("OWNER_TASK131_GOAL") && string(authority, "scope").equals("OWNED_TEST131")
			&& hashField(authority, "manifestSha").equals(manifestSha) && "TRAINING_LAUNCH131_V1".equals(launch.getProperty("schema"))
			&& "TRAIN".equals(launch.getProperty("phase")) && leaf(campaign).equals(launch.getProperty("campaignId")) && nonce.equals(launch.getProperty("nonce"))
			&& bootId.equals(launch.getProperty("bootId")) && boot.toString().equals(launch.getProperty("bootRoot")) && manifestSha.equals(launch.getProperty("manifestSha"))
			&& hashField(manifest, "executionSha").equals(launch.getProperty("executionSha")), "TRAIN_LAUNCH_SCOPE");
		final var execution = properties(checked(campaign.resolve("execution.properties"), hashField(manifest, "executionSha"), META_LIMIT));
		verifyExecution(execution, leaf(campaign), source, jar, expectedHead, learner, opponent, scenes);
		final long completed = number(close, "completed"), previous = number(reservation, "lastCompleted");
		require(previous == 0 && completed <= 6 && completed == scenes.size()
			&& completed == number(map(manifest.get("quota")), "maxEpisodes") && Long.toString(previous).equals(launch.getProperty("lastCompleted")), "TRAIN_COMPLETION_QUOTA");

		final var process = json(checked(boot.resolve("process.json"), hashField(close, "ownerSha"), META_LIMIT));
		final long pid = number(process, "pid");
		final String argumentLine = string(process, "arguments");
		final var ack = properties(checked(boot.resolve("drain131.properties"), hashField(close, "ackSha"), META_LIMIT));
		final String nativeSha = hashField(close, "sealSha");
		final Instant started = Instant.parse(ack.getProperty("startedUtc", ""));
		final Instant created = windowsTime(number(process, "created"));
		require(string(process, "schema").equals("TRAINING_PROCESS131_V1") && pid > 0 && string(process, "nonce").equals(nonce)
			&& hashField(process, "reservationSha").equals(reservationSha) && hashField(process, "javaSha").equals(hashField(manifest, "javaSha"))
			&& argumentLine.contains(" -Dphantom.learning131.nonce=" + nonce + " ") && argumentLine.contains(" " + NATIVE_CLASS + " --train ")
			&& string(process, "commandLine").endsWith(argumentLine) && Duration.between(created, started).abs().compareTo(Duration.ofMillis(1)) <= 0
			&& "NATIVE_DRAIN131_V1".equals(ack.getProperty("schema")) && "COMPLETED".equals(ack.getProperty("status"))
			&& manifestSha.equals(ack.getProperty("manifestSha")) && nativeSha.equals(ack.getProperty("sealSha")) && nonce.equals(ack.getProperty("nonce"))
			&& bootId.equals(ack.getProperty("bootId")) && Long.toString(pid).equals(ack.getProperty("pid")) && Long.toString(completed).equals(ack.getProperty("completed")), "TRAIN_PROCESS_DRAIN");

		final var bootSeal = seal(checked(boot.resolve("complete131.tsv"), nativeSha, META_LIMIT), "NATIVE_TRAIN_BOOT131_V1", "phase", "status", "bootId", "nonce",
			"pid", "processStarted", "manifestSha", "sourceCommit", "jarSha", "expectedHead", "learnerSha", "opponentSha", "cycle");
		matches(bootSeal, Map.ofEntries(Map.entry("phase", "TRAIN"), Map.entry("status", "DRAINED"), Map.entry("bootId", bootId), Map.entry("nonce", nonce),
			Map.entry("pid", Long.toString(pid)), Map.entry("manifestSha", manifestSha), Map.entry("sourceCommit", source), Map.entry("jarSha", jar),
			Map.entry("expectedHead", expectedHead), Map.entry("learnerSha", learner), Map.entry("opponentSha", opponent), Map.entry("cycle", Integer.toString(cycle))));
		require(started.equals(Instant.parse(bootSeal.meta().get("processStarted"))) && bootSeal.rows().size() == scenes.size(), "TRAIN_BOOT_EVIDENCE");

		final var samples = new ArrayList<ActionFeedbackV4>();
		final var episodeIds = new HashSet<String>();
		final var eventIds = new HashSet<String>();
		for (String[] row : bootSeal.rows())
		{
			require(row.length == 3 && row[0].equals("episode"), "EPISODE_ROW");
			final String episodeId = digest(row[1]);
			require(episodeIds.add(episodeId) && scenes.containsKey(episodeId), "UNFROZEN_EPISODE");
			readEpisode(boot.resolve("episodes").resolve(episodeId), row[2], episodeId, bootId, nonce, learner, map(scenes.get(episodeId)), eventIds, samples);
		}
		final Path ledger = task.resolve("fit-ledger");
		physical(ledger);
		require(Files.isDirectory(ledger, LinkOption.NOFOLLOW_LINKS), "LEDGER_SCOPE");
		ActionLearningV4.load(ledger.resolve("candidate-" + learner + ".bin"), learner);
		ActionLearningV4.load(ledger.resolve("candidate-" + opponent + ".bin"), opponent);
		return new Batch(ledger, expectedHead, learner, nativeSha, List.copyOf(samples));
	}

	private static void verifyExecution(Properties execution, String campaign, String source, String jar, String expectedHead, String learner,
		String opponent, Map<String, Object> scenes)
	{
		final var expected = new HashMap<>(Map.of("schema", "TRAINING_EXECUTION131_V1", "phase", "TRAIN", "campaignId", campaign, "sourceCommit", source,
			"gameJarSha", jar, "expectedHead", expectedHead, "learnerSha", learner, "opponentSha", opponent, "rollbackHead", expectedHead, "rollbackModelSha", learner));
		for (var entry : scenes.entrySet())
		{
			final String prefix = "episode." + digest(entry.getKey()) + ".";
			final var scene = map(entry.getValue());
			expected.put(prefix + "sourceSha", hashField(scene, "sourceSha"));
			expected.put(prefix + "policySha", hashField(scene, "policySha"));
		}
		require(expected.entrySet().stream().allMatch(field -> field.getValue().equals(execution.getProperty(field.getKey())))
			&& execution.stringPropertyNames().stream().filter(key -> key.startsWith("episode.")).allMatch(expected::containsKey), "FROZEN_EXECUTION_PLAN");
	}

	private static void readEpisode(Path root, String sealSha, String episodeId, String bootId, String nonce, String learner,
		Map<String, Object> scene, Set<String> acceptedEvents, List<ActionFeedbackV4> samples) throws Exception
	{
		final var data = seal(checked(root.resolve("seal131.tsv"), digest(sealSha), META_LIMIT), "NATIVE_TRAIN_EPISODE131_V1", "split", "status",
			"bootId", "nonce", "episodeId", "sourceSha", "policySha", "startedNanos", "settledNanos", "episodeSha");
		final String source = hashField(scene, "sourceSha"), policy = hashField(scene, "policySha");
		matches(data, Map.of("split", "TRAIN", "status", "DRAINED", "bootId", bootId, "nonce", nonce, "episodeId", episodeId,
			"sourceSha", source, "policySha", policy));
		final String originalEpisode = text(checked(root.resolve("episode131.json"), digest(data.meta().get("episodeSha")), META_LIMIT));
		final var settled = episode(map(parse(originalEpisode)), Long.parseLong(data.meta().get("startedNanos")));
		require((originalEpisode.equals(settled.json()) || originalEpisode.equals(settled.json() + "\n")) && settled.id().equals(episodeId)
			&& settled.ownerNonce().equals(nonce) && settled.settledNanos() == Long.parseLong(data.meta().get("settledNanos"))
			&& (Set.of("COMPLETED", "FAILED").contains(settled.outcome()) || settled.reason().equals("NATIVE_TIMEOUT"))
			&& !settled.eventKeys().isEmpty() && data.rows().size() == settled.eventKeys().size(), "EXACT_SETTLED_EPISODE");
		final var paid = new HashSet<String>(settled.eventKeys());
		for (String[] row : data.rows())
		{
			require(row.length == 6 && row[0].equals("event"), "PAID_EVENT_ROW");
			final String key = digest(row[1]);
			require(paid.remove(key) && acceptedEvents.add(key), "REPLAY_OR_FOREIGN_EVENT");
			final byte[] intentBytes = checked(root.resolve(key + ".intent.bin"), digest(row[2]), 501_000);
			final var intent = ActionExperimentReceiptV4.Intent.restore(intentBytes);
			final var plan = intent.decision().plan();
			require(Arrays.equals(intent.bytes(), intentBytes) && plan.schemaVersion() == 2 && plan.split().equals("TRAIN") && plan.policyId().equals("EPSILON_V2")
				&& plan.episode().equals(episodeId) && plan.sourceSha().equals(source) && plan.modelSha().equals(learner) && plan.sha256().equals(policy), "FROZEN_TRAIN_INTENT");
			final String rowSha = digest(row[3]), rawSha = digest(row[4]);
			final byte[] rowBytes = checked(root.resolve(key + ".row.bin"), rowSha, META_LIMIT);
			final var decoded = TacticalStoreV3.decode(rowBytes);
			require(decoded.eventKey().equals(key) && Arrays.equals(TacticalStoreV3.encode(decoded), rowBytes), "ORIGINAL_ACTION_ROW");
			final var raw = json(checked(root.resolve(key + ".native.json"), rawSha, META_LIMIT));
			require(raw.keySet().equals(Set.of("schemaVersion", "format", "nativeRecord", "observationSha")) && number(raw, "schemaVersion") == 6
				&& string(raw, "format").equals("IMMUTABLE_JAVA_RECORD_TEXT_V1"), "ORIGINAL_NATIVE_WRITER_FORMAT");
			PhantomLearning020V4Native.nativeRecord(decoded, raw);
			final var effect = ActionExperimentReceiptV4.bind(intent, policy, rowBytes, settled, rawSha);
			require(Arrays.equals(effect.bytes(), checked(root.resolve(key + ".effect.bin"), digest(row[5]), META_LIMIT)), "ORIGINAL_EFFECT_RECEIPT");
			samples.add(ActionFeedbackV4.nativeAction(rowBytes, rowSha, settled, intent.owner(), rawSha));
		}
		require(paid.isEmpty(), "MISSING_PAID_EVENT");
	}

	private static Instant windowsTime(long ticks)
	{
		require(ticks > 0, "PROCESS_START");
		return Instant.ofEpochSecond(ticks / 10_000_000L - WINDOWS_EPOCH_OFFSET, ticks % 10_000_000L * 100L);
	}

	private static String target(String candidate, String parent)
	{
		require(!digest(candidate).equals(parent), "EMPTY_CANDIDATE");
		return candidate;
	}

	private static Properties properties(byte[] bytes) throws IOException
	{
		final var result = new Properties()
		{
			@Override
			public synchronized Object put(Object key, Object value)
			{
				require(!containsKey(key), "DUPLICATE_PROPERTY");
				return super.put(key, value);
			}
		};
		result.load(new StringReader(text(bytes)));
		return result;
	}

	private static Seal seal(byte[] bytes, String schema, String... fields) throws IOException
	{
		final var meta = new HashMap<String, String>();
		final var rows = new ArrayList<String[]>();
		for (String line : text(bytes).lines().toList())
		{
			final String[] parts = line.split("\t", -1);
			if (parts[0].equals("meta"))
			{
				require(parts.length == 3 && !parts[2].isEmpty() && rows.isEmpty() && meta.putIfAbsent(parts[1], parts[2]) == null, "NATIVE_SEAL_META");
			}
			else
			{
				rows.add(parts);
			}
		}
		require(schema.equals(meta.get("schema")) && meta.size() == fields.length + 1 && meta.keySet().containsAll(List.of(fields)), "NATIVE_SEAL_SCHEMA");
		return new Seal(meta, rows);
	}

	private static void matches(Seal seal, Map<String, String> expected)
	{
		require(expected.entrySet().stream().allMatch(field -> field.getValue().equals(seal.meta().get(field.getKey()))), "NATIVE_SEAL_PIN");
	}

	private static byte[] checked(Path path, String expected, int limit) throws IOException
	{
		physical(path);
		require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "ORIGINAL_FILE");
		try (var stream = Files.newInputStream(path))
		{
			final byte[] bytes = stream.readNBytes(limit + 1);
			require(bytes.length <= limit && TacticalObservationV3.hash(bytes).equals(digest(expected)), "ORIGINAL_SHA_OR_SIZE");
			return bytes;
		}
	}

	private static void physical(Path path) throws IOException
	{
		require(path.isAbsolute() && path.normalize().equals(path) && path.toRealPath().equals(path), "PHYSICAL_PATH");
	}

	private static String leaf(Path path) { return path.getFileName().toString(); }
	private static String text(byte[] bytes) throws IOException { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
	private static Map<String, Object> json(byte[] bytes) throws IOException { return map(parse(text(bytes))); }
	private static String hashField(Map<String, Object> source, String name) { return digest(string(source, name)); }
	private static String digest(String value)
	{
		require(value != null && value.matches("[a-f0-9]{64}") && !value.equals("0".repeat(64)), "SHA256_FORMAT");
		return value;
	}

	private static void require(boolean condition, String reason)
	{
		if (!condition) { throw new IllegalArgumentException("FIT131_" + reason); }
	}
}

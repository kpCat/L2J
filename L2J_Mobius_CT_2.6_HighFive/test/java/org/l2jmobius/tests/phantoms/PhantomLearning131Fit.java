/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.episode;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.list;
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
import java.util.Collections;
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
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
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
	private record Location(Path campaign, Path task, int ordinal) { }
	private record Scene(String id, long profile, String sourceSha, String policySha) { }
	private record Scope(Path campaign, String manifestSha, String executionSha, String masterSha, String source, String jar, String javaSha,
		String head, String learner, String opponent, int cycle, int maxBoots, Instant deadline, List<Scene> scenes) { }
	private record Reservation(Path boot, String id, String sha, String parent, String nonce, int completed, Instant reservedAt, Instant deadline) { }
	private record Closure(Reservation reservation, Map<String, Object> body, Instant closedAt) { }
	private record Store(Path path, String sha) { }
	private record Chain(int completed, List<ActionFeedbackV4> samples, Store store, Set<String> nonces, Instant lastClose) { }
	record CompletedPrefix(int completed, List<String> episodeIds, Path store, String storeSha) { }

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
		final Location location = location(boot);
		require(origin.isAbsolute() && location.task().resolve("fit-pins").equals(origin.getParent()), "INDEPENDENT_ORIGIN_PATH");
		final var pins = json(checked(origin, originSha, META_LIMIT));
		require(string(pins, "schema").equals("TRAINING_FIT_ORIGIN131_V1") && string(pins, "phase").equals("TRAIN"), "TRAIN_ORIGIN_SCHEMA");
		final String expectedHead = hashField(pins, "expectedHead"), learner = hashField(pins, "learnerSha"), opponent = hashField(pins, "opponentSha");
		require(hashField(pins, "rollbackHead").equals(expectedHead) && hashField(pins, "rollbackModelSha").equals(learner), "ROLLBACK_PIN");
		final String closedSha = hashField(pins, "closedSha");
		final var terminal = json(checked(boot.resolve("closed.json"), closedSha, META_LIMIT));
		require(string(terminal, "status").equals("COMPLETED"), "TRAIN_NOT_DRAINED");
		final Scope scope = scope(location, hashField(pins, "manifestSha"));
		require(expectedHead.equals(scope.head()) && learner.equals(scope.learner()) && opponent.equals(scope.opponent()), "FROZEN_ORIGIN_LINEAGE");
		final var scenes = map(pins.get("scenes"));
		require(scenes.size() == scope.scenes().size(), "TRAIN_SCENE_QUOTA");
		for (Scene scene : scope.scenes())
		{
			final var pinned = map(scenes.get(scene.id()));
			require(hashField(pinned, "sourceSha").equals(scene.sourceSha()) && hashField(pinned, "policySha").equals(scene.policySha()), "FROZEN_ORIGIN_EPISODE");
		}
		final Chain chain = chain(scope, location.ordinal(), closedSha, true);
		require(chain.completed() == scope.scenes().size() && !chain.samples().isEmpty(), "TRAIN_COMPLETION_QUOTA");
		final Path ledger = location.task().resolve("fit-ledger");
		physical(ledger);
		require(Files.isDirectory(ledger, LinkOption.NOFOLLOW_LINKS), "LEDGER_SCOPE");
		ActionLearningV4.load(ledger.resolve("candidate-" + learner + ".bin"), learner);
		ActionLearningV4.load(location.campaign().resolve("candidate-" + learner + ".bin"), learner);
		ActionLearningV4.load(location.campaign().resolve("candidate-" + opponent + ".bin"), opponent);
		return new Batch(ledger, expectedHead, learner, closedSha, chain.samples());
	}

	/** A next owned reservation pins the previous closure before another GameServer admission. */
	static CompletedPrefix completedPrefix(Path openBoot, String manifestSha, String reservationSha) throws Exception
	{
		final Location location = location(openBoot);
		final Scope scope = scope(location, digest(manifestSha));
		require(Files.notExists(openBoot.resolve("closed.json"), LinkOption.NOFOLLOW_LINKS), "RESUME_ALREADY_CLOSED");
		final Reservation next = reservation(scope, location.ordinal(), digest(reservationSha));
		final Chain previous = chain(scope, location.ordinal() - 1, next.parent(), false);
		require(previous.completed() == next.completed() && previous.completed() < scope.scenes().size() && !previous.nonces().contains(next.nonce())
			&& (previous.lastClose() == null || !next.reservedAt().isBefore(previous.lastClose())), "RESUME_PREFIX_CURSOR");
		final Store store = previous.store();
		return new CompletedPrefix(previous.completed(), scope.scenes().subList(0, previous.completed()).stream().map(Scene::id).toList(),
			store == null ? null : store.path(), store == null ? "NONE" : store.sha());
	}

	private static Location location(Path boot) throws IOException
	{
		physical(boot);
		require(Files.isDirectory(boot, LinkOption.NOFOLLOW_LINKS) && boot.getNameCount() >= 7 && leaf(boot).matches("[0-9]{5}"), "TRAIN_BOOT_PATH");
		final Path campaign = boot.getParent().getParent();
		final Path task = campaign.getParent().getParent();
		final int ordinal = Integer.parseInt(leaf(boot));
		require(ordinal > 0 && ordinal <= 12 && leaf(boot.getParent()).equals("boots") && leaf(campaign).matches("train-0[1-6]")
			&& leaf(campaign.getParent()).equals("campaigns") && leaf(task).equals("TASK131") && leaf(task.getParent()).equals("logs")
			&& leaf(task.getParent().getParent()).equals(".phantom-local"), "TRAIN_BOOT_SCOPE");
		return new Location(campaign, task, ordinal);
	}

	private static Scope scope(Location location, String manifestSha) throws Exception
	{
		final Path campaign = location.campaign();
		final var manifest = json(checked(campaign.resolve("campaign.json"), manifestSha, META_LIMIT));
		final int cycle = Math.toIntExact(number(manifest, "cycle"));
		final String jar = hashField(manifest, "gameJarSha"), learner = hashField(manifest, "modelSha");
		final String source = string(manifest, "sourceCommit");
		require(string(manifest, "schema").equals("TRAINING_COMBAT131_V1") && string(manifest, "phase").equals("TRAIN")
			&& string(manifest, "campaignId").equals(leaf(campaign)) && String.format(Locale.ROOT, "train-%02d", cycle).equals(leaf(campaign))
			&& number(manifest, "auditBank") == 0 && source.matches("[a-f0-9]{40}"), "TRAIN_MANIFEST_PIN");
		final var quota = map(manifest.get("quota"));
		final int maxBoots = Math.toIntExact(number(quota, "maxBoots")), maxEpisodes = Math.toIntExact(number(quota, "maxEpisodes"));
		final var planned = list(manifest.get("scenes"));
		require(maxBoots >= location.ordinal() && maxBoots <= 12 && maxEpisodes > 0 && maxEpisodes <= 6 && planned.size() == maxEpisodes, "TRAIN_SCENE_QUOTA");
		final String executionSha = hashField(manifest, "executionSha"), scheduleSha = hashField(manifest, "fixtureSha"), policySha = hashField(manifest, "policySha");
		final var execution = properties(checked(campaign.resolve("execution.properties"), executionSha, META_LIMIT));
		final String head = digest(execution.getProperty("expectedHead")), opponent = digest(execution.getProperty("opponentSha"));
		require(propertiesMatch(execution, Map.ofEntries(Map.entry("schema", "TRAINING_EXECUTION131_V1"), Map.entry("phase", "TRAIN"), Map.entry("campaignId", leaf(campaign)),
			Map.entry("sourceCommit", source), Map.entry("gameJarSha", jar), Map.entry("learnerSha", learner), Map.entry("rollbackHead", head),
			Map.entry("rollbackModelSha", learner), Map.entry("trainScenesSha", scheduleSha), Map.entry("policySha", policySha),
			Map.entry("maxEpisodes", Integer.toString(maxEpisodes)), Map.entry("cycle", Integer.toString(cycle)))), "FROZEN_EXECUTION_PLAN");
		final var policy = properties(checked(campaign.resolve("policy131.properties"), policySha, META_LIMIT));
		require(propertiesMatch(policy, Map.of("schema", "TRAIN_POLICY131_V1", "expectedHead", head, "learnerSha", learner, "opponentSha", opponent)), "FROZEN_TRAIN_POLICY");
		final var schedule = text(checked(campaign.resolve("scenes.tsv"), scheduleSha, META_LIMIT)).lines().toList();
		require(schedule.size() == planned.size() + 1 && schedule.getFirst().equals("episodeId\tprofileId\tseconds"), "FROZEN_TRAIN_SCHEDULE");
		final var scenes = new ArrayList<Scene>();
		final var seen = new HashSet<String>();
		final var fields = new HashSet<String>();
		for (int index = 0; index < planned.size(); index++)
		{
			final var declared = map(planned.get(index));
			final String[] row = schedule.get(index + 1).split("\t", -1);
			final String id = digest(string(declared, "id"));
			require(row.length == 3 && row[0].equals(id) && seen.add(id) && Long.toString(number(declared, "deadlineSeconds")).equals(row[2]), "FROZEN_EPISODE_ORDER");
			final long profile = Long.parseLong(row[1]);
			require(profile > 0, "FROZEN_EPISODE_PROFILE");
			final String prefix = "episode." + id + ".";
			fields.add(prefix + "sourceSha"); fields.add(prefix + "policySha");
			scenes.add(new Scene(id, profile, digest(execution.getProperty(prefix + "sourceSha")), digest(execution.getProperty(prefix + "policySha"))));
		}
		require(execution.stringPropertyNames().stream().filter(key -> key.startsWith("episode.")).allMatch(fields::contains), "UNFROZEN_EPISODE");
		return new Scope(campaign, manifestSha, executionSha, hashField(map(manifest.get("configShas")), "game/config/Custom/PhantomPlayers.ini"),
			source, jar, hashField(manifest, "javaSha"), head, learner, opponent, cycle, maxBoots, Instant.parse(string(quota, "deadlineUtc")), List.copyOf(scenes));
	}

	private static Path bootPath(Scope scope, int ordinal)
	{
		return scope.campaign().resolve("boots").resolve(String.format(Locale.ROOT, "%05d", ordinal));
	}

	private static Reservation reservation(Scope scope, int ordinal, String sha) throws Exception
	{
		require(ordinal > 0 && ordinal <= scope.maxBoots(), "BOOT_QUOTA");
		final Path boot = bootPath(scope, ordinal);
		final String id = leaf(boot);
		final var reserved = json(checked(boot.resolve("reserved.json"), sha, META_LIMIT));
		final String nonce = string(reserved, "nonce");
		final int completed = Math.toIntExact(number(reserved, "lastCompleted"));
		final String deadlineText = string(reserved, "deadlineUtc");
		final Instant at = Instant.parse(string(reserved, "reservedUtc")), deadline = Instant.parse(deadlineText);
		require(string(reserved, "schema").equals("TRAINING_RESERVED131_V1") && nonce.matches("[a-f0-9]{32}") && string(reserved, "bootId").equals(id)
			&& number(reserved, "sequence") == ordinal && hashField(reserved, "manifestSha").equals(scope.manifestSha())
			&& completed >= 0 && completed < scope.scenes().size() && at.isBefore(deadline) && !deadline.isAfter(scope.deadline()), "TRAIN_RESERVATION_PIN");
		final var authority = json(checked(scope.campaign().resolve("authorization.json"), hashField(reserved, "authoritySha"), META_LIMIT));
		require(string(authority, "schema").equals("TASK131_LAUNCH_AUTHORITY_V1") && string(authority, "authority").equals("OWNER_TASK131_GOAL")
			&& string(authority, "scope").equals("OWNED_TEST131") && hashField(authority, "manifestSha").equals(scope.manifestSha()), "TRAIN_LAUNCH_AUTHORITY");
		final var launch = properties(checked(boot.resolve("launch131.properties"), hashField(reserved, "launchSha"), META_LIMIT));
		require(propertiesMatch(launch, Map.of("schema", "TRAINING_LAUNCH131_V1", "phase", "TRAIN", "campaignId", leaf(scope.campaign()), "bootId", id,
			"bootRoot", boot.toString(), "manifestSha", scope.manifestSha(), "executionSha", scope.executionSha(), "nonce", nonce,
			"lastCompleted", Integer.toString(completed), "deadlineUtc", deadlineText)), "TRAIN_LAUNCH_SCOPE");
		return new Reservation(boot, id, sha, hashField(reserved, "parentSha"), nonce, completed, at, deadline);
	}

	private static Chain chain(Scope scope, int count, String tail, boolean finalRequired) throws Exception
	{
		final var closures = new ArrayList<Closure>();
		for (int ordinal = count; ordinal >= 1; ordinal--)
		{
			final var close = json(checked(bootPath(scope, ordinal).resolve("closed.json"), tail, META_LIMIT));
			final Reservation reserved = reservation(scope, ordinal, hashField(close, "reservationSha"));
			final Instant closedAt = Instant.parse(string(close, "closedUtc"));
			require(string(close, "schema").equals("TRAINING_CLOSED131_V1") && hashField(close, "masterSha").equals(scope.masterSha())
				&& !closedAt.isBefore(reserved.reservedAt()), "TRAIN_CLOSE_CHAIN");
			closures.add(new Closure(reserved, close, closedAt));
			tail = reserved.parent();
		}
		require(tail.equals(scope.manifestSha()), "TRAIN_CHAIN_GENESIS");
		Collections.reverse(closures);
		final var samples = new ArrayList<ActionFeedbackV4>();
		final var events = new HashSet<String>();
		final var nonces = new HashSet<String>();
		final var processes = new HashSet<String>();
		int done = 0;
		Instant priorClose = null;
		Store store = null;
		for (int index = 0; index < closures.size(); index++)
		{
			final Closure closure = closures.get(index);
			final Reservation reserved = closure.reservation();
			final var close = closure.body();
			final int next = Math.toIntExact(number(close, "completed"));
			final boolean last = finalRequired && index == closures.size() - 1;
			require(reserved.completed() == done && nonces.add(reserved.nonce()) && (priorClose == null || !reserved.reservedAt().isBefore(priorClose))
				&& next >= done && next <= scope.scenes().size()
				&& string(close, "status").equals(last ? "COMPLETED" : "PAUSED")
				&& (last ? next == scope.scenes().size() && next > done : next < scope.scenes().size()), "TRAIN_PREFIX_CURSOR");
			final String control = string(close, "control");
			require(control.equals("pause") || last && control.equals("none"), "TRAIN_CLOSE_CONTROL");
			if (control.equals("pause"))
			{
				final byte[] expected = (reserved.nonce() + "\n").getBytes(StandardCharsets.US_ASCII);
				checked(reserved.boot().resolve("pause131.request"), TacticalObservationV3.hash(expected), expected.length);
			}
			final String reason = string(close, "reason");
			if (reason.equals("CONTROL_BEFORE_JAVA") || reason.equals("CONTROL_BEFORE_NATIVE_ADMISSION"))
			{
				require(!last && next == done && string(close, "ackSha").equals("NONE") && string(close, "sealSha").equals("NONE")
					&& Set.of("complete131.tsv", "drain131.properties", "episodes", "train-store131.tsv", "train-java.claim", "n01-java.claim",
						"n01-binding.tsv", "n01-native-cycles.tsv", "n01-native-costs.tsv", "n01-native-loot.tsv")
						.stream().allMatch(file -> Files.notExists(reserved.boot().resolve(file), LinkOption.NOFOLLOW_LINKS)), "TRAIN_UNCLAIMED_PAUSE");
				if (reason.equals("CONTROL_BEFORE_NATIVE_ADMISSION"))
				{
					Math.toIntExact(number(close, "exitCode"));
					process(scope, closure, processes);
				}
				else
				{
					require(close.containsKey("exitCode") && close.get("exitCode") == null && string(close, "ownerSha").equals("NONE")
						&& Files.notExists(reserved.boot().resolve("process.json"), LinkOption.NOFOLLOW_LINKS), "TRAIN_UNCLAIMED_PAUSE");
				}
			}
			else
			{
				require(reason.equals("NATIVE_DRAIN_ACK_VERIFIED") && number(close, "exitCode") == 0, "TRAIN_NOT_DRAINED");
				store = readBoot(scope, closure, next, last, processes, events, samples);
			}
			done = next;
			priorClose = closure.closedAt();
		}
		return new Chain(done, List.copyOf(samples), store, Set.copyOf(nonces), priorClose);
	}

	private record OwnedProcess(long pid, Instant created) { }

	private static OwnedProcess process(Scope scope, Closure closure, Set<String> processes) throws Exception
	{
		final var reserved = closure.reservation();
		final var owner = json(checked(reserved.boot().resolve("process.json"), hashField(closure.body(), "ownerSha"), META_LIMIT));
		final long pid = number(owner, "pid"), createdTicks = number(owner, "created");
		final String args = string(owner, "arguments");
		final Instant created = windowsTime(createdTicks);
		require(pid > 0 && processes.add(pid + ":" + createdTicks) && string(owner, "schema").equals("TRAINING_PROCESS131_V1")
			&& string(owner, "nonce").equals(reserved.nonce()) && hashField(owner, "reservationSha").equals(reserved.sha()) && hashField(owner, "javaSha").equals(scope.javaSha())
			&& args.contains(" -Dphantom.learning131.nonce=" + reserved.nonce() + " ") && args.contains(" " + NATIVE_CLASS + " --train ")
			&& string(owner, "commandLine").endsWith(args)
			&& !created.isBefore(reserved.reservedAt()) && created.isBefore(reserved.deadline()) && !created.isAfter(closure.closedAt()), "TRAIN_PROCESS_IDENTITY");
		return new OwnedProcess(pid, created);
	}

	private static Store readBoot(Scope scope, Closure closure, int next, boolean last,
		Set<String> processes, Set<String> events, List<ActionFeedbackV4> samples) throws Exception
	{
		final var reserved = closure.reservation();
		final Path boot = reserved.boot();
		final var close = closure.body();
		final var process = process(scope, closure, processes);
		final long pid = process.pid();
		final String nativeSha = hashField(close, "sealSha");
		final var ack = properties(checked(boot.resolve("drain131.properties"), hashField(close, "ackSha"), META_LIMIT));
		final Instant started = Instant.parse(ack.getProperty("startedUtc", ""));
		require(Duration.between(process.created(), started).abs().compareTo(Duration.ofMillis(1)) <= 0, "TRAIN_PROCESS_IDENTITY");
		require(propertiesMatch(ack, Map.of("schema", "NATIVE_DRAIN131_V1", "status", last ? "COMPLETED" : "PAUSED", "manifestSha", scope.manifestSha(),
			"sealSha", nativeSha, "nonce", reserved.nonce(), "bootId", reserved.id(), "pid", Long.toString(pid), "completed", Integer.toString(next))), "TRAIN_PROCESS_DRAIN");
		final var bootSeal = seal(checked(boot.resolve("complete131.tsv"), nativeSha, META_LIMIT), "NATIVE_TRAIN_BOOT131_V1", "phase", "status",
			"bootId", "nonce", "pid", "processStarted", "manifestSha", "sourceCommit", "jarSha", "expectedHead", "learnerSha", "opponentSha", "cycle", "storeSha");
		matches(bootSeal, Map.ofEntries(Map.entry("phase", "TRAIN"), Map.entry("status", last ? "DRAINED" : "PAUSED"), Map.entry("bootId", reserved.id()),
			Map.entry("nonce", reserved.nonce()), Map.entry("pid", Long.toString(pid)), Map.entry("manifestSha", scope.manifestSha()), Map.entry("sourceCommit", scope.source()),
			Map.entry("jarSha", scope.jar()), Map.entry("expectedHead", scope.head()), Map.entry("learnerSha", scope.learner()), Map.entry("opponentSha", scope.opponent()),
			Map.entry("cycle", Integer.toString(scope.cycle()))));
		require(started.equals(Instant.parse(bootSeal.meta().get("processStarted"))) && bootSeal.rows().size() == next - reserved.completed(), "TRAIN_BOOT_EVIDENCE");
		final String storeSha = digest(bootSeal.meta().get("storeSha"));
		final Path store = boot.resolve("train-store131.tsv");
		final var actors = verifyStore(scope, checked(store, storeSha, META_LIMIT));
		for (int index = reserved.completed(); index < next; index++)
		{
			final String[] row = bootSeal.rows().get(index - reserved.completed());
			final Scene scene = scope.scenes().get(index);
			require(row.length == 3 && row[0].equals("episode") && row[1].equals(scene.id()), "TRAIN_EPISODE_PREFIX_ORDER");
			readEpisode(boot.resolve("episodes").resolve(scene.id()), row[2], scene, actors.get(scene.profile()), reserved.id(), reserved.nonce(), scope.learner(), events, samples);
		}
		return new Store(store, storeSha);
	}

	private static Map<Long, Integer> verifyStore(Scope scope, byte[] bytes) throws IOException
	{
		final var rows = text(bytes).lines().toList();
		require(rows.size() == 5 && rows.getFirst().equals("profileId\tactorObjectId\tbackgroundSha\texp\tsp\tshots\tadena"), "TRAIN_STORE_SHAPE");
		final var profiles = new HashMap<Long, Integer>();
		final var actors = new HashSet<Integer>();
		for (String line : rows.subList(1, rows.size()))
		{
			final String[] row = line.split("\t", -1);
			require(row.length == 7, "TRAIN_STORE_ROW");
			final long profile = Long.parseLong(row[0]);
			final int actor = Integer.parseInt(row[1]);
			digest(row[2]);
			require(profile > 0 && profiles.putIfAbsent(profile, actor) == null && actor > 0 && actors.add(actor) && Long.parseLong(row[3]) >= 0 && Long.parseLong(row[4]) >= 0
				&& Long.parseLong(row[5]) >= 0 && Long.parseLong(row[6]) >= 0, "TRAIN_STORE_FACTS");
		}
		require(scope.scenes().stream().allMatch(scene -> profiles.containsKey(scene.profile())), "TRAIN_STORE_COHORT");
		return Map.copyOf(profiles);
	}

	private static void readEpisode(Path root, String sealSha, Scene scene, int objectId, String bootId, String nonce, String learner,
		Set<String> acceptedEvents, List<ActionFeedbackV4> samples) throws Exception
	{
		final var data = seal(checked(root.resolve("seal131.tsv"), digest(sealSha), META_LIMIT), "NATIVE_TRAIN_EPISODE131_V1", "split", "status",
			"bootId", "nonce", "episodeId", "sourceSha", "policySha", "startedNanos", "settledNanos", "episodeSha");
		final String episodeId = scene.id(), source = scene.sourceSha(), policy = scene.policySha();
		matches(data, Map.of("split", "TRAIN", "status", "DRAINED", "bootId", bootId, "nonce", nonce, "episodeId", episodeId,
			"sourceSha", source, "policySha", policy));
		final String originalEpisode = text(checked(root.resolve("episode131.json"), digest(data.meta().get("episodeSha")), META_LIMIT));
		final var settled = episode(map(parse(originalEpisode)), Long.parseLong(data.meta().get("startedNanos")));
		require((originalEpisode.equals(settled.json()) || originalEpisode.equals(settled.json() + "\n")) && settled.id().equals(episodeId)
			&& settled.ownerNonce().equals(nonce) && settled.settledNanos() == Long.parseLong(data.meta().get("settledNanos"))
			&& (Set.of("COMPLETED", "FAILED").contains(settled.outcome()) || settled.reason().equals("NATIVE_TIMEOUT"))
			&& !settled.eventKeys().isEmpty() && data.rows().size() == settled.eventKeys().size(), "EXACT_SETTLED_EPISODE");
		require(settled.scope().equals("SOLO") && settled.actors().size() == 1 && settled.actors().getFirst().profile() == scene.profile()
			&& settled.actors().getFirst().objectId() == objectId, "SOLO_EPISODE_ACTOR");
		final var actor = settled.actors().getFirst();
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
				&& plan.episode().equals(episodeId) && plan.sourceSha().equals(source) && plan.modelSha().equals(learner) && plan.sha256().equals(policy)
				&& sameActor(actor, intent.decision().observation()), "FROZEN_TRAIN_INTENT");
			final String rowSha = digest(row[3]), rawSha = digest(row[4]);
			final byte[] rowBytes = checked(root.resolve(key + ".row.bin"), rowSha, META_LIMIT);
			final var decoded = TacticalStoreV3.decode(rowBytes);
			require(decoded.eventKey().equals(key) && Arrays.equals(TacticalStoreV3.encode(decoded), rowBytes) && decoded.contextKey().equals("task131|TRAIN|" + episodeId)
				&& sameActor(actor, decoded.receipt().attempt().observation())
				&& decoded.receipt().elapsedNanos() <= settled.settledNanos() - intent.capturedNanos(), "ORIGINAL_ACTION_ROW");
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

	private static boolean sameActor(TacticalEpisodeV3.Actor actor, TacticalObservationV3 observation)
	{
		return actor.profile() == observation.profileId() && actor.objectId() == observation.actorObjectId() && actor.epoch() == observation.actorEpoch();
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

	private static boolean propertiesMatch(Properties properties, Map<String, String> expected)
	{
		return expected.entrySet().stream().allMatch(field -> field.getValue().equals(properties.getProperty(field.getKey())));
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

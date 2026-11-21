/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.l2jmobius.gameserver.phantoms.learning.ActionContinuationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExperimentReceiptV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionExplorationV4;
import org.l2jmobius.gameserver.phantoms.learning.ActionValueV4;
import org.l2jmobius.gameserver.phantoms.learning.HealEvidenceV3.SourceOwner;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Entity;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Fact;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Field;
import org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.Role;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;

/** Negative FIT ingress checks with no native run and no optimizer credit. */
public final class PhantomLearning131FitChecks
{
	private static final String HASH = "1".repeat(64);

	private PhantomLearning131FitChecks() { }

	public static void main(String[] args) throws Exception
	{
		final Path reports = Path.of(System.getProperty("phantom.test.reports", ".")).toRealPath();
		final Path scratch = Files.createTempDirectory(reports, "fit131-contract-");
		try
		{
			final Path task = scratch.resolve(".phantom-local/logs/TASK131");
			final Path campaign = task.resolve("campaigns/train-01");
			final Path boot = Files.createDirectories(campaign.resolve("boots/00001"));
			final Path origin = Files.createDirectories(task.resolve("fit-pins")).resolve("origin.json");
			final var pins = new HashMap<String, Object>();
			pins.put("schema", "TRAINING_FIT_ORIGIN131_V1");
			pins.put("phase", "AUDIT");
			final String heldout = write(origin, pins);
			refuse(boot, origin, heldout, "TRAIN_ORIGIN_SCHEMA");
			pins.put("phase", "TRAIN");
			write(origin, pins);
			refuse(boot, origin, heldout, "ORIGINAL_SHA_OR_SIZE");
			final String manifestSha = write(campaign.resolve("campaign.json"), Map.of("schema", "TRAINING_COMBAT131_V1", "phase", "TRAIN",
				"campaignId", "train-01", "cycle", 1, "auditBank", 0, "sourceCommit", "a".repeat(40), "gameJarSha", HASH, "modelSha", HASH));
			final String closeSha = write(boot.resolve("closed.json"), Map.of("schema", "TRAINING_CLOSED131_V1", "status", "PAUSED", "reservationSha", HASH));
			pins.putAll(Map.of("manifestSha", manifestSha, "closedSha", closeSha, "expectedHead", HASH, "learnerSha", HASH,
				"opponentSha", HASH, "rollbackHead", HASH, "rollbackModelSha", HASH, "scenes", Map.of(HASH, Map.of("sourceSha", HASH, "policySha", HASH))));
			refuse(boot, origin, write(origin, pins), "TRAIN_NOT_DRAINED");
			pins.put("rollbackModelSha", "2".repeat(64));
			refuse(boot, origin, write(origin, pins), "ROLLBACK_PIN");
			resumePrefix(campaign, boot);
			if (Files.exists(task.resolve("fit-ledger"))) { throw new AssertionError("Rejected evidence created FIT state"); }
			tuningPrefix(task);
			System.out.println("TASK131_FIT_INGRESS_OFFLINE_CHECKS_PASS GS=0 LS=0 SQL=0 FIT=0");
		}
		finally
		{
			try (var paths = Files.walk(scratch))
			{
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.delete(path); }
			}
		}
	}

	private static void tuningPrefix(Path task) throws Exception
	{
		final Path campaign = task.resolve("campaigns/tuning-01");
		final Path boot = Files.createDirectories(campaign.resolve("boots/00001"));
		final String source = "a".repeat(40), stock = "2".repeat(64), fixture = "3".repeat(64), scene = "4".repeat(64);
		final Path ledger = Files.createDirectory(task.resolve("fit-ledger"));
		final var fitted = ActionContinuationV4.initialize(ledger, fittedFixture());
		final var plan = new ActionExplorationV4.Plan(2, "MODEL_V2", 0, 0, 0, "TUNING", HASH, stock, fitted.model().sha256(), List.of("1000:1", "7000:1"));
		final String stockSha = TacticalObservationV3.hash(("TASK131_TUNING_STOCK_V1\n" + fixture + "\n" + scene + "\n").getBytes(StandardCharsets.US_ASCII));
		final String policySha = writeProperties(campaign.resolve("policy131.properties"), Map.of("schema", "TUNING_POLICY131_V1",
			"expectedHead", fitted.head(), "learnerSha", fitted.model().sha256(), "opponentSha", stockSha));
		final String scheduleSha = sha(Files.writeString(campaign.resolve("scenes.tsv"), "episodeId\tprofileId\tseconds\n" + HASH + "\t101\t15\n" + stock + "\t102\t15\n"));
		final String executionSha = writeProperties(campaign.resolve("execution.properties"), Map.ofEntries(Map.entry("schema", "TRAINING_EXECUTION131_V1"),
			Map.entry("phase", "TUNING"), Map.entry("campaignId", "tuning-01"), Map.entry("sourceCommit", source), Map.entry("gameJarSha", HASH),
			Map.entry("learnerSha", fitted.model().sha256()), Map.entry("expectedHead", fitted.head()), Map.entry("rollbackHead", fitted.head()), Map.entry("opponentSha", stockSha),
			Map.entry("rollbackModelSha", fitted.model().sha256()), Map.entry("trainScenesSha", scheduleSha), Map.entry("policySha", policySha), Map.entry("maxEpisodes", "2"),
			Map.entry("cycle", "1"), Map.entry("fixtureSha", fixture), Map.entry("sceneSha", scene), Map.entry("trainClosedSha", stock),
			Map.entry("episode." + HASH + ".sourceSha", stock), Map.entry("episode." + HASH + ".policySha", plan.sha256()),
			Map.entry("episode." + stock + ".sourceSha", fixture), Map.entry("episode." + stock + ".policySha", HASH)));
		final Instant at = Instant.parse("2026-11-20T12:00:00Z");
		final var manifest = new HashMap<String, Object>(Map.of("schema", "TRAINING_COMBAT131_V1", "phase", "TUNING", "campaignId", "tuning-01", "cycle", 1,
			"auditBank", 0, "sourceCommit", source, "gameJarSha", HASH, "modelSha", fitted.model().sha256(), "javaSha", HASH));
		manifest.putAll(Map.of("executionSha", executionSha, "fixtureSha", scheduleSha, "policySha", policySha,
			"configShas", Map.of("game/config/Custom/PhantomPlayers.ini", HASH), "quota", Map.of("maxBoots", 2, "maxEpisodes", 2, "deadlineUtc", at.plusSeconds(600).toString()),
			"scenes", List.of(Map.of("id", HASH, "deadlineSeconds", 15), Map.of("id", stock, "deadlineSeconds", 15))));
		final String manifestSha = write(campaign.resolve("campaign.json"), manifest);
		final String authority = write(campaign.resolve("authorization.json"), Map.of("schema", "TASK131_LAUNCH_AUTHORITY_V1", "authority", "OWNER_TASK131_GOAL",
			"scope", "OWNED_TEST131", "manifestSha", manifestSha));
		final String old = reserve(boot, 1, manifestSha, manifestSha, executionSha, authority, 0, at, "TUNING");
		Files.writeString(boot.resolve("pause131.request"), nonce(1) + "\n");
		final String closed = write(boot.resolve("closed.json"), close(old, at.plusSeconds(5)));
		final Path next = Files.createDirectory(boot.resolveSibling("00002"));
		final String nextSha = reserve(next, 2, closed, manifestSha, executionSha, authority, 0, at.plusSeconds(6), "TUNING");
		if (PhantomLearning131Fit.completedPrefix(next, manifestSha, nextSha, "TUNING").completed() != 0)
		{
			throw new AssertionError("Pre-admission TUNING pause advanced the cursor");
		}
		final Path claim = Files.writeString(boot.resolve("tuning-java.claim"), "partial\n");
		try
		{
			PhantomLearning131Fit.completedPrefix(next, manifestSha, nextSha, "TUNING");
			throw new AssertionError("Resumed a TUNING process after unsealed native entry");
		}
		catch (IllegalArgumentException rejected)
		{
			if (!"FIT131_TRAIN_UNCLAIMED_PAUSE".equals(rejected.getMessage())) { throw rejected; }
		}
		Files.delete(claim);
		tuningPaidPrefix(boot, next, old, manifestSha, executionSha, authority, source, at, plan, fitted, stockSha, ledger);
	}

	private static void tuningPaidPrefix(Path boot, Path next, String reserved, String manifestSha, String executionSha, String authority,
		String source, Instant at, ActionExplorationV4.Plan plan, ActionContinuationV4.State fitted, String stockSha, Path ledger) throws Exception
	{
		final var observation = new TacticalObservationV3(1, System.nanoTime(), 101, 201, 10, "solo:201", List.of(
			new Entity(Role.SELF, 201, 10, 0, 12, Map.of(), List.of(), List.of(), List.of(), List.of()),
			new Entity(Role.ENEMY, 999, 20, 0, 0, Map.of(Field.MAX_HP, Fact.known(800)), List.of(), List.of(), List.of(), List.of())));
		final var offered = List.of(1000, 7000).stream().map(id -> new LegalActionV3(LegalActionV3.Family.DAMAGE, "Combat", 101, 201, 10, 1,
			999, 20, observation.partyIdentity(), id, 1, 4, 500, 300, false, true)).toList();
		final var experiment = ActionExplorationV4.frozen(plan, ledger, fitted.head());
		final var decision = experiment.choose(observation, offered, offered.getFirst(), true, Long.MAX_VALUE);
		if (!decision.mode().equals("MODEL") || !decision.selected().equals(offered.getLast()))
		{
			throw new AssertionError("The TUNING paid evidence did not exercise a model reorder");
		}
		final var owner = new SourceOwner(10, "Combat/101/10/1");
		final var intent = new ActionExperimentReceiptV4.Intent(decision, owner, decision.capturedNanos());
		final long elapsed = 1_000_000L, end = intent.capturedNanos() + elapsed;
		final var attempt = new TacticalReceiptV3.Attempt(decision.observation(), decision.offered(), decision.selected(), decision.stock(), 0);
		final var receipt = new TacticalReceiptV3("8".repeat(64), attempt, 200, 0, 200, 4, elapsed, TacticalReceiptV3.reward(200, 800, 4, elapsed), owner);
		final String key = receipt.eventKey(), nonce = nonce(1);
		final var episode = new TacticalEpisodeV3(plan.episode(), nonce, "SOLO", observation.capturedNanos(), end, "NATIVE_KILL",
			List.of(new TacticalEpisodeV3.Actor(101, 201, 10, 12, 52, 500, 300, false, 1L, 1L)),
			List.of(new TacticalEpisodeV3.Kill(999, 12, 20, 0, 201, 1)), List.of(key), 200, 0, 4, 0);
		final Path original = Files.createDirectories(boot.resolve("episodes").resolve(plan.episode()));
		final String episodeSha = sha(Files.writeString(original.resolve("episode131.json"), episode.json()));
		final String intentSha = sha(Files.write(original.resolve(key + ".intent.bin"), intent.bytes()));
		final byte[] row = TacticalStoreV3.encode(new TacticalStoreV3.Row("TUNING", "task131|TUNING|" + episode.id(), receipt));
		final String rowSha = sha(Files.write(original.resolve(key + ".row.bin"), row));
		final String rawSha = sha(Files.writeString(original.resolve(key + ".native.json"), PhantomLearning005Native.nativeReceiptJson(receipt)));
		final String effectSha = sha(Files.write(original.resolve(key + ".effect.bin"), ActionExperimentReceiptV4.bind(intent, plan.sha256(), row, episode, rawSha).bytes()));
		final String decisionLine = Base64.getEncoder().encodeToString(decision.bytes()) + "\n";
		final Path decisions = Files.writeString(original.resolve("decisions131.tsv"), decisionLine);
		final var episodeMeta = Map.ofEntries(Map.entry("schema", "NATIVE_TUNING_EPISODE131_V1"), Map.entry("split", "TUNING"), Map.entry("status", "DRAINED"),
			Map.entry("bootId", "00001"), Map.entry("nonce", nonce), Map.entry("episodeId", episode.id()), Map.entry("sourceSha", plan.sourceSha()),
			Map.entry("policySha", plan.sha256()), Map.entry("startedNanos", Long.toString(episode.startedNanos())), Map.entry("settledNanos", Long.toString(end)),
			Map.entry("episodeSha", episodeSha), Map.entry("decisionsSha", sha(decisions)));
		final String originalSha = seal(original.resolve("seal131.tsv"), episodeMeta, String.join("\t", "event", key, intentSha, rowSha, rawSha, effectSha) + "\n");
		final Instant entered = at.plusSeconds(1);
		final String args = "javaOpts -Dphantom.learning131.nonce=" + nonce + " org.l2jmobius.tests.phantoms.PhantomLearning131Native --tuning launch";
		final String ownerSha = write(boot.resolve("process.json"), Map.of("schema", "TRAINING_PROCESS131_V1", "pid", 131, "nonce", nonce, "reservationSha", reserved,
			"javaSha", HASH, "arguments", args, "commandLine", "java.exe " + args, "created", (entered.getEpochSecond() + 11_644_473_600L) * 10_000_000L));
		final var storeBody = new StringBuilder("profileId\tactorObjectId\tbackgroundSha\texp\tsp\tshots\tadena\n");
		for (int id = 101; id <= 104; id++) { storeBody.append(id).append('\t').append(id + 100).append('\t').append(HASH).append("\t0\t0\t0\t0\n"); }
		final Path store = Files.writeString(boot.resolve("tuning-store131.tsv"), storeBody);
		final String bootSha = seal(boot.resolve("complete131.tsv"), Map.ofEntries(Map.entry("schema", "NATIVE_TUNING_BOOT131_V1"), Map.entry("phase", "TUNING"),
			Map.entry("status", "PAUSED"), Map.entry("bootId", "00001"), Map.entry("nonce", nonce), Map.entry("pid", "131"), Map.entry("processStarted", entered.toString()),
			Map.entry("manifestSha", manifestSha), Map.entry("sourceCommit", source), Map.entry("jarSha", HASH), Map.entry("expectedHead", fitted.head()),
			Map.entry("learnerSha", fitted.model().sha256()), Map.entry("opponentSha", stockSha), Map.entry("cycle", "1"), Map.entry("storeSha", sha(store))),
			"episode\t" + episode.id() + "\t" + originalSha + "\n");
		final String ackSha = writeProperties(boot.resolve("drain131.properties"), Map.of("schema", "NATIVE_DRAIN131_V1", "status", "PAUSED", "manifestSha", manifestSha,
			"sealSha", bootSha, "nonce", nonce, "bootId", "00001", "pid", "131", "completed", "1", "startedUtc", entered.toString()));
		final var closed = close(reserved, at.plusSeconds(5));
		closed.putAll(Map.of("ownerSha", ownerSha, "ackSha", ackSha, "sealSha", bootSha, "exitCode", 0, "reason", "NATIVE_DRAIN_ACK_VERIFIED", "completed", 1));
		final String parent = write(boot.resolve("closed.json"), closed);
		final String nextSha = reserve(next, 2, parent, manifestSha, executionSha, authority, 1, at.plusSeconds(6), "TUNING");
		final var prefix = PhantomLearning131Fit.completedPrefix(next, manifestSha, nextSha, "TUNING");
		if (prefix.completed() != 1 || !prefix.episodeIds().equals(List.of(plan.episode())) || !prefix.store().equals(store)
			|| !ActionContinuationV4.load(ledger).head().equals(fitted.head()))
		{
			throw new AssertionError("Paid TUNING replay drifted from the sealed prefix or wrote training state");
		}
		Files.writeString(decisions, decisionLine + decisionLine);
		refuseResume(next, manifestSha, nextSha, "TUNING", "ORIGINAL_SHA_OR_SIZE");
	}

	/** Synthetic compatible checkpoint with a positive skill-id weight. No optimizer or native run. */
	private static ActionValueV4 fittedFixture() throws Exception
	{
		final var bytes = new ByteArrayOutputStream();
		try (var data = new DataOutputStream(bytes))
		{
			data.writeInt(0x41563431);
			data.writeUTF(ActionValueV4.SCHEMA);
			data.writeUTF("0".repeat(64));
			data.writeUTF(HASH);
			data.writeLong(1);
			for (int index = 0; index < ActionValueV4.COEFFICIENTS; index++) { data.writeDouble(index == 2 ? 1 : 0); }
			data.writeInt(1);
			data.writeUTF("6".repeat(64));
			data.writeUTF(HASH);
			data.writeInt(1);
			data.writeUTF("7".repeat(64));
			data.writeUTF("TRAIN");
		}
		final byte[] original = bytes.toByteArray();
		return ActionValueV4.restore(original, TacticalObservationV3.hash(original));
	}

	private static void resumePrefix(Path campaign, Path first) throws Exception
	{
		final String secondEpisode = "2".repeat(64), source = "a".repeat(40);
		final Instant start = Instant.parse("2026-11-20T12:00:00Z");
		final var policy = Map.of("schema", "TRAIN_POLICY131_V1", "expectedHead", HASH, "learnerSha", HASH, "opponentSha", HASH);
		final String policySha = writeProperties(campaign.resolve("policy131.properties"), policy);
		final Path schedule = Files.writeString(campaign.resolve("scenes.tsv"), "episodeId\tprofileId\tseconds\n" + HASH + "\t101\t15\n" + secondEpisode + "\t102\t15\n");
		final String scheduleSha = sha(schedule);
		final var execution = new HashMap<>(policy);
		execution.putAll(Map.of("schema", "TRAINING_EXECUTION131_V1", "phase", "TRAIN", "campaignId", "train-01", "sourceCommit", source, "gameJarSha", HASH,
			"rollbackHead", HASH, "rollbackModelSha", HASH, "trainScenesSha", scheduleSha, "policySha", policySha, "maxEpisodes", "2"));
		execution.put("cycle", "1");
		for (String id : List.of(HASH, secondEpisode))
		{
			execution.put("episode." + id + ".sourceSha", HASH);
			execution.put("episode." + id + ".policySha", HASH);
		}
		final String executionSha = writeProperties(campaign.resolve("execution.properties"), execution);
		final var manifest = new HashMap<String, Object>(Map.of("schema", "TRAINING_COMBAT131_V1", "phase", "TRAIN", "campaignId", "train-01", "cycle", 1,
			"auditBank", 0, "sourceCommit", source, "gameJarSha", HASH, "modelSha", HASH, "javaSha", HASH));
		manifest.putAll(Map.of("executionSha", executionSha, "fixtureSha", scheduleSha, "policySha", policySha,
			"configShas", Map.of("game/config/Custom/PhantomPlayers.ini", HASH), "quota", Map.of("maxBoots", 3, "maxEpisodes", 2, "deadlineUtc", start.plusSeconds(600).toString()),
			"scenes", List.of(Map.of("id", HASH, "deadlineSeconds", 15), Map.of("id", secondEpisode, "deadlineSeconds", 15))));
		final String manifestSha = write(campaign.resolve("campaign.json"), manifest);
		final String authSha = write(campaign.resolve("authorization.json"), Map.of("schema", "TASK131_LAUNCH_AUTHORITY_V1", "authority", "OWNER_TASK131_GOAL",
			"scope", "OWNED_TEST131", "manifestSha", manifestSha));
		final String reserved = reserve(first, 1, manifestSha, manifestSha, executionSha, authSha, 0, start);
		final String nonce = nonce(1);
		final Instant entered = start.plusSeconds(1);
		final String args = "javaOpts -Dphantom.learning131.nonce=" + nonce + " org.l2jmobius.tests.phantoms.PhantomLearning131Native --train launch";
		final String ownerSha = write(first.resolve("process.json"), Map.of("schema", "TRAINING_PROCESS131_V1", "pid", 131, "nonce", nonce, "reservationSha", reserved,
			"javaSha", HASH, "arguments", args, "commandLine", "java.exe " + args, "created", (entered.getEpochSecond() + 11_644_473_600L) * 10_000_000L));
		final var storeBody = new StringBuilder("profileId\tactorObjectId\tbackgroundSha\texp\tsp\tshots\tadena\n");
		for (int id = 101; id <= 104; id++) { storeBody.append(id).append('\t').append(id + 100).append('\t').append(HASH).append("\t0\t0\t0\t0\n"); }
		final Path store = Files.writeString(first.resolve("train-store131.tsv"), storeBody);
		final String storeSha = sha(store);
		final var seal = new HashMap<>(Map.of("schema", "NATIVE_TRAIN_BOOT131_V1", "phase", "TRAIN", "status", "PAUSED", "bootId", "00001", "nonce", nonce,
			"pid", "131", "processStarted", entered.toString(), "manifestSha", manifestSha, "sourceCommit", source));
		seal.putAll(Map.of("jarSha", HASH, "expectedHead", HASH, "learnerSha", HASH, "opponentSha", HASH, "cycle", "1", "storeSha", storeSha));
		final var nativeSeal = new StringBuilder();
		seal.forEach((key, value) -> nativeSeal.append("meta\t").append(key).append('\t').append(value).append('\n'));
		final String bootSha = sha(Files.writeString(first.resolve("complete131.tsv"), nativeSeal.toString()));
		final String ackSha = writeProperties(first.resolve("drain131.properties"), Map.of("schema", "NATIVE_DRAIN131_V1", "status", "PAUSED", "manifestSha", manifestSha,
			"sealSha", bootSha, "nonce", nonce, "bootId", "00001", "pid", "131", "completed", "0", "startedUtc", entered.toString()));
		Files.writeString(first.resolve("pause131.request"), nonce + "\n");
		final var closed = close(reserved, start.plusSeconds(15));
		closed.putAll(Map.of("ownerSha", ownerSha, "ackSha", ackSha, "sealSha", bootSha, "exitCode", 0, "reason", "NATIVE_DRAIN_ACK_VERIFIED"));
		final String closeSha = write(first.resolve("closed.json"), closed);
		final Path second = Files.createDirectories(first.resolveSibling("00002"));
		final String reservedSecond = reserve(second, 2, closeSha, manifestSha, executionSha, authSha, 0, start.plusSeconds(20));
		Files.writeString(second.resolve("pause131.request"), nonce(2) + "\n");
		final String nextParent = write(second.resolve("closed.json"), close(reservedSecond, start.plusSeconds(21)));
		final Path next = Files.createDirectories(first.resolveSibling("00003"));
		final String correct = reserve(next, 3, nextParent, manifestSha, executionSha, authSha, 0, start.plusSeconds(25));
		final var prefix = PhantomLearning131Fit.completedPrefix(next, manifestSha, correct);
		if (prefix.completed() != 0 || !prefix.episodeIds().isEmpty() || !store.equals(prefix.store()) || !storeSha.equals(prefix.storeSha()))
		{
			throw new AssertionError("A no-Java pause discarded the last verified STORE pin");
		}
		final String jumped = reserve(next, 3, nextParent, manifestSha, executionSha, authSha, 1, start.plusSeconds(25));
		refuseResume(next, manifestSha, jumped, "RESUME_PREFIX_CURSOR");
		if (!reserve(next, 3, nextParent, manifestSha, executionSha, authSha, 0, start.plusSeconds(25)).equals(correct)) { throw new AssertionError("Changed fixture pin"); }
		Files.writeString(store, "tampered\n");
		refuseResume(next, manifestSha, correct, "ORIGINAL_SHA_OR_SIZE");
		Files.writeString(store, storeBody);
		final Instant startup = start.plusSeconds(21);
		final String secondNonce = nonce(2);
		final String startupArgs = args.replace(nonce, secondNonce);
		final String startupOwner = write(second.resolve("process.json"), Map.of("schema", "TRAINING_PROCESS131_V1", "pid", 132,
			"nonce", secondNonce, "reservationSha", reservedSecond, "javaSha", HASH, "arguments", startupArgs, "commandLine", "java.exe " + startupArgs,
			"created", (startup.getEpochSecond() + 11_644_473_600L) * 10_000_000L));
		final var startupClose = close(reservedSecond, start.plusSeconds(22));
		startupClose.putAll(Map.of("reason", "CONTROL_BEFORE_NATIVE_ADMISSION", "ownerSha", startupOwner, "exitCode", 1));
		final String startupParent = write(second.resolve("closed.json"), startupClose);
		final String startupReservation = reserve(next, 3, startupParent, manifestSha, executionSha, authSha, 0, start.plusSeconds(25));
		if (!store.equals(PhantomLearning131Fit.completedPrefix(next, manifestSha, startupReservation).store()))
		{
			throw new AssertionError("An exited JVM before admission discarded the STORE pin");
		}
		final Path partial = Files.writeString(second.resolve("train-store131.tsv"), "partial\n");
		refuseResume(next, manifestSha, startupReservation, "TRAIN_UNCLAIMED_PAUSE");
		Files.delete(partial);

		final Path original = Files.createDirectories(first.resolve("episodes").resolve(HASH));
		final String eventKey = "3".repeat(64);
		final var extraActor = new TacticalEpisodeV3(HASH, nonce, "SOLO", 100, 200, "NATIVE_KILL",
			List.of(new TacticalEpisodeV3.Actor(101, 201, 1, 10, 52, 100, 50, false, 0L, 0L),
				new TacticalEpisodeV3.Actor(102, 202, 1, 10, 52, 100, 50, false, 0L, 0L)),
			List.of(new TacticalEpisodeV3.Kill(999, 10, 1, 0, 201, 1)), List.of(eventKey), 1, 0, 1, 0);
		final String episodeSha = sha(Files.writeString(original.resolve("episode131.json"), extraActor.json()));
		final String episodeSeal = seal(original.resolve("seal131.tsv"), Map.of("schema", "NATIVE_TRAIN_EPISODE131_V1", "split", "TRAIN", "status", "DRAINED",
			"bootId", "00001", "nonce", nonce, "episodeId", HASH, "sourceSha", HASH, "policySha", HASH, "startedNanos", "100", "settledNanos", "200"),
			"meta\tepisodeSha\t" + episodeSha + "\nevent\t" + eventKey + ("\t" + HASH).repeat(4) + "\n");
		final String episodes = "episode\t" + HASH + "\t" + episodeSeal + "\nepisode\t" + secondEpisode + "\t" + HASH + "\n";
		final Path origin = campaign.getParent().getParent().resolve("fit-pins/origin.json");
		seal.put("status", "DRAINED");
		seal.remove("storeSha");
		refuse(first, origin, finalOrigin(first, origin, manifestSha, secondEpisode, seal(first.resolve("complete131.tsv"), seal, episodes), nonce, entered, closed), "NATIVE_SEAL_SCHEMA");
		seal.put("storeSha", storeSha);
		refuse(first, origin, finalOrigin(first, origin, manifestSha, secondEpisode, seal(first.resolve("complete131.tsv"), seal, episodes), nonce, entered, closed), "SOLO_EPISODE_ACTOR");
	}

	private static String finalOrigin(Path first, Path origin, String manifestSha, String secondEpisode, String bootSha,
		String nonce, Instant entered, Map<String, Object> closed) throws Exception
	{
		final String ack = writeProperties(first.resolve("drain131.properties"), Map.of("schema", "NATIVE_DRAIN131_V1", "status", "COMPLETED", "manifestSha", manifestSha,
			"sealSha", bootSha, "nonce", nonce, "bootId", "00001", "pid", "131", "completed", "2", "startedUtc", entered.toString()));
		closed.putAll(Map.of("status", "COMPLETED", "control", "none", "completed", 2, "ackSha", ack, "sealSha", bootSha));
		final String closeSha = write(first.resolve("closed.json"), closed);
		final var scene = Map.of("sourceSha", HASH, "policySha", HASH);
		return write(origin, Map.of("schema", "TRAINING_FIT_ORIGIN131_V1", "phase", "TRAIN", "manifestSha", manifestSha, "closedSha", closeSha,
			"expectedHead", HASH, "learnerSha", HASH, "opponentSha", HASH, "rollbackHead", HASH, "rollbackModelSha", HASH, "scenes", Map.of(HASH, scene, secondEpisode, scene)));
	}

	private static String seal(Path path, Map<String, String> metadata, String rows) throws Exception
	{
		final var text = new StringBuilder();
		metadata.forEach((key, value) -> text.append("meta\t").append(key).append('\t').append(value).append('\n'));
		return sha(Files.writeString(path, text.append(rows)));
	}

	private static String reserve(Path boot, int ordinal, String previous, String manifest, String execution, String authority, int completed, Instant at) throws Exception
	{
		return reserve(boot, ordinal, previous, manifest, execution, authority, completed, at, "TRAIN");
	}

	private static String reserve(Path boot, int ordinal, String previous, String manifest, String execution, String authority, int completed, Instant at, String phase) throws Exception
	{
		final String id = boot.getFileName().toString(), deadline = at.plusSeconds(90).toString();
		final String launch = writeProperties(boot.resolve("launch131.properties"), Map.of("schema", "TRAINING_LAUNCH131_V1", "phase", phase, "campaignId", boot.getParent().getParent().getFileName().toString(),
			"bootId", id, "bootRoot", boot.toString(), "manifestSha", manifest, "executionSha", execution, "nonce", nonce(ordinal),
			"lastCompleted", Integer.toString(completed), "deadlineUtc", deadline));
		return write(boot.resolve("reserved.json"), Map.ofEntries(Map.entry("schema", "TRAINING_RESERVED131_V1"), Map.entry("bootId", id), Map.entry("sequence", ordinal),
			Map.entry("nonce", nonce(ordinal)), Map.entry("lastCompleted", completed), Map.entry("reservedUtc", at.toString()), Map.entry("deadlineUtc", deadline),
			Map.entry("manifestSha", manifest), Map.entry("parentSha", previous), Map.entry("launchSha", launch), Map.entry("authoritySha", authority)));
	}

	private static Map<String, Object> close(String reservation, Instant at)
	{
		final var result = new HashMap<String, Object>(Map.of("schema", "TRAINING_CLOSED131_V1", "status", "PAUSED", "control", "pause", "completed", 0,
			"masterSha", HASH, "reservationSha", reservation, "ownerSha", "NONE", "ackSha", "NONE"));
		result.put("sealSha", "NONE"); result.put("reason", "CONTROL_BEFORE_JAVA"); result.put("closedUtc", at.toString()); result.put("exitCode", null);
		return result;
	}

	private static String nonce(int ordinal) { return String.format(Locale.ROOT, "%032x", ordinal); }

	private static String writeProperties(Path path, Map<String, String> values) throws Exception
	{
		final var text = new StringBuilder();
		new java.util.TreeMap<>(values).forEach((key, value) -> text.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n'));
		return sha(Files.writeString(path, text.toString()));
	}

	private static void refuseResume(Path boot, String manifest, String reservation, String reason) throws Exception
	{
		refuseResume(boot, manifest, reservation, "TRAIN", reason);
	}

	private static void refuseResume(Path boot, String manifest, String reservation, String phase, String reason) throws Exception
	{
		try
		{
			PhantomLearning131Fit.completedPrefix(boot, manifest, reservation, phase);
		}
		catch (IllegalArgumentException rejected)
		{
			if (("FIT131_" + reason).equals(rejected.getMessage())) { return; }
			throw rejected;
		}
		throw new AssertionError("Accepted resumed input without " + reason);
	}

	private static String write(Path path, Object value) throws Exception
	{
		Files.writeString(path, PhantomEvaluationOutcomeV2.jsonValue(value));
		return sha(path);
	}

	private static String sha(Path path) throws Exception { return TacticalObservationV3.hash(Files.readAllBytes(path)); }

	private static void refuse(Path boot, Path origin, String sha, String reason) throws Exception
	{
		try
		{
			PhantomLearning131Fit.run(new String[] { "prepare", boot.toString(), origin.toString(), sha });
		}
		catch (IllegalArgumentException rejected)
		{
			if (("FIT131_" + reason).equals(rejected.getMessage())) { return; }
			throw rejected;
		}
		throw new AssertionError("Accepted input without " + reason);
	}
}

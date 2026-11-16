/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.util.*;
import org.l2jmobius.gameserver.phantoms.learning.*;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.*;

/** One bounded native016 reader and focused V4 regression route; no SQL writes or server boot here. */
public final class PhantomLearning019ActionValue
{
	static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
	static void rejects(Runnable action)
	{
		try { action.run(); } catch (IllegalArgumentException expected) { return; }
		throw new AssertionError("Untrusted input accepted");
	}
	static String reservation(Map<String, String[]> schedule, Map<String, Object> reserved, String id, String cohortSha)
	{
		final String[] slot = schedule.get(id);
		require(slot != null && id.equals(reserved.get("episode")) && cohortSha.equals(reserved.get("cohortSha")), "V4_FOREIGN_NATIVE_COHORT");
		require(number(reserved, "slot") == Long.parseLong(slot[0]) && slot[2].equals(reserved.get("block")) && slot[3].equals(reserved.get("split")), "V4_NATIVE_SLOT_SPLIT");
		return slot[3];
	}
	static List<ActionFeedbackV4> source(Path cohort) throws Exception
	{
		check(cohort.isAbsolute() && cohort.equals(cohort.toRealPath()), "Physical native016 cohort");
		final var seal = object(cohort.resolve("cohort016.complete.json"));
		final byte[] manifest = Files.readAllBytes(cohort.resolve("cohort016.json"));
		check(TacticalObservationV3.hash(manifest).equals(string(seal, "manifestSha")), "Native016 seal SHA");
		final var pins = map(map(parse(new String(manifest, java.nio.charset.StandardCharsets.UTF_8))).get("files"));
		for (var pin : pins.entrySet())
		{
			final Path path = cohort.resolve(pin.getKey()).normalize();
			check(path.startsWith(cohort) && path.equals(path.toRealPath()) && TacticalObservationV3.hash(Files.readAllBytes(path)).equals(pin.getValue()), "Frozen pre-CUT source binding");
		}
		final String cohortSha = TacticalObservationV3.hash(manifest);
		final var schedule = new TreeMap<String, String[]>();
		for (var line : Files.readAllLines(cohort.resolve("schedule.tsv")).stream().skip(1).toList())
		{
			final String[] slot = line.split("\t", -1);
			check(slot.length == 5, "Native016 schedule columns");
			final String id = TacticalObservationV3.hash((cohort.getFileName() + "/" + slot[0] + "/" + slot[1]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			check(schedule.putIfAbsent(id, slot) == null, "Native016 unique slot");
		}
		check(schedule.size() == 51, "Frozen native016 slots");
		// NOT_NATIVE boundary mutations. Neither receives a sample or optimizer credit.
		rejects(() -> reservation(schedule, Map.of("episode", "f".repeat(64), "cohortSha", cohortSha), "f".repeat(64), cohortSha));
		final var slot = schedule.firstEntry();
		rejects(() -> reservation(schedule, Map.of("episode", slot.getKey(), "cohortSha", cohortSha, "slot", Long.parseLong(slot.getValue()[0]), "block", slot.getValue()[2], "split", "FOREIGN"), slot.getKey(), cohortSha));
		final var result = new ArrayList<ActionFeedbackV4>();
		try (var paths = Files.list(cohort))
		{
			for (var work : paths.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}")).sorted().toList())
			{
				final Path terminal = work.resolve("terminal.bin");
				if (!Files.isRegularFile(terminal)) { continue; }
				final String id = work.getFileName().toString();
				final var reserved = object(work.resolve("reserved.json"));
				final String split = reservation(schedule, reserved, id, cohortSha);
				final var settled = object(work.resolve("settled016.json"));
				final var bindings = map(settled.get("files"));
				final byte[] bytes = Files.readAllBytes(terminal);
				final String sha = TacticalObservationV3.hash(bytes);
				// cohort016 is a pre-CUT seal; paid bytes are bound by the existing post-drain settled016 ledger.
				check(id.equals(string(settled, "episode")) && string(settled, "state").equals("NATIVE_SETTLED")
					&& sha.equals(bindings.get(id + "/terminal.bin")), "Frozen terminal SHA");
				final var envelope = TerminalRewardReceiptV1.restore(bytes, sha);
				check(envelope.episode().id().equals(id) && envelope.split().equals(split) && envelope.episode().ownerNonce().equals(reserved.get("attempt")), "Native episode/split/owner identity");
				for (var action : envelope.actions())
				{
					final var row = action.row();
					check(row.split().equals(split) && row.contextKey().equals("autonomous009|" + cohort.getFileName() + "|" + split + "|" + id), "Native action context");
					final Path raw = work.resolve(row.eventKey() + ".native.json");
					final byte[] rawBytes = Files.readAllBytes(raw);
					final String rawSha = TacticalObservationV3.hash(rawBytes);
					check(rawSha.equals(bindings.get(id + "/" + raw.getFileName())), "Frozen paid native SHA");
					final var nativeJson = object(raw);
					final String rawRecord = string(nativeJson, "nativeRecord"), decodedRecord = row.receipt().toString();
					// Immutable Map iteration order in observation.toString is JVM-specific. Canonical observation bytes and action/outcome suffix are stable.
					check(rawRecord.substring(0, rawRecord.indexOf(", entities=[")).equals(decodedRecord.substring(0, decodedRecord.indexOf(", entities=[")))
						&& rawRecord.substring(rawRecord.indexOf(", offered=[")).equals(decodedRecord.substring(decodedRecord.indexOf(", offered=[")))
						&& string(nativeJson, "observationSha").equals(TacticalObservationV3.hash(row.receipt().attempt().observation().bytes())), "Independent native row match");
					final byte[] encoded = TacticalStoreV3.encode(row);
					result.add(ActionFeedbackV4.nativeAction(encoded, TacticalObservationV3.hash(encoded), envelope.episode(), row.receipt().owner(), rawSha));
					if (result.size() == 256) { return List.copyOf(result); }
				}
			}
		}
		return List.copyOf(result);
	}
	public static void main(String[] args) throws Exception
	{
		if (args.length == 4 && args[0].equals("native")) { nativeOnce(Path.of(args[1]), args[2], args[3]); return; }
		if (args.length == 3 && args[0].equals("cold"))
		{
			final Path output = Path.of(args[2]); final var proof = object(output.resolve("offline.json"));
			final var model = ActionLearningV4.load(output.resolve("candidate.bin"), string(proof, "modelShaAfter"));
			final var samples = source(Path.of(args[1]));
			final var attempt = samples.stream().filter(s -> s.row().split().equals("TRAIN")).findFirst().orElseThrow().row().receipt().attempt();
			check(Double.compare(real(proof, "newScore"), model.score(attempt.observation(), attempt.selected())) == 0 && ActionLearningV4.fit(model, samples).sha256().equals(model.sha256()), "Fresh JVM cold score/replay");
			System.out.println("LEARNING019_COLD_PASS updates=" + model.updates() + " sha=" + model.sha256()); return;
		}
		check(args.length == 3 && args[0].equals("offline"), "offline <native016 cohort> <fresh output>");
		final Path output = Path.of(args[2]).toAbsolutePath().normalize();
		Files.createDirectory(output);
		final long start = System.nanoTime();
		final var samples = source(Path.of(args[1]));
		check(samples.size() >= 8 && samples.stream().map(ActionFeedbackV4::episodeId).distinct().count() >= 2, "Genuine native source");
		final var control = samples.stream().filter(s -> s.row().split().equals("TRAIN")).findFirst().orElseThrow();
		final var attempt = control.row().receipt().attempt();
		final var before = ActionValueV4.empty();
		// NOT_NATIVE contract mutation: heldout episode must never gain TRAIN credit through a replay.
		final var heldout = samples.stream().filter(s -> !s.row().split().equals("TRAIN")).findFirst().orElseThrow();
		final var heldoutState = ActionLearningV4.fit(before, List.of(heldout));
		check(heldoutState.updates() == 0 && !heldoutState.sha256().equals(before.sha256()), "Heldout ledger persists without optimizer updates");
		final var changedSplit = new TacticalStoreV3.Row("TRAIN", heldout.row().contextKey(), heldout.row().receipt());
		final byte[] changedBytes = TacticalStoreV3.encode(changedSplit);
		final var changed = ActionFeedbackV4.nativeAction(changedBytes, TacticalObservationV3.hash(changedBytes), heldout.episode(), heldout.row().receipt().owner(), heldout.sourceSha());
		rejects(() -> ActionLearningV4.fit(ActionValueV4.restore(heldoutState.bytes(), heldoutState.sha256()), List.of(changed)));
		final double oldScore = before.score(attempt.observation(), attempt.selected());
		final var learned = ActionLearningV4.fit(before, samples);
		check(learned.updates() > 0 && !learned.sha256().equals(before.sha256()), "Real optimizer update changes bytes");
		final double score = learned.score(attempt.observation(), attempt.selected());
		check(score != oldScore, "Actual native action score changes");
		final Path checkpoint = output.resolve("candidate.bin");
		ActionLearningV4.save(checkpoint, learned);
		final var cold = ActionLearningV4.load(checkpoint, learned.sha256());
		check(Arrays.equals(learned.bytes(), cold.bytes()) && Double.compare(score, cold.score(attempt.observation(), attempt.selected())) == 0, "Cold exact restore");
		check(ActionLearningV4.fit(cold, samples).sha256().equals(cold.sha256()), "No double credit after cold restore");
		byte[] damaged = cold.bytes(); damaged[damaged.length - 1] ^= 1;
		rejects(() -> ActionValueV4.restore(damaged, cold.sha256()));
		rejects(() -> ActionValueV4.restore(Arrays.copyOf(cold.bytes(), 35), cold.sha256()));
		final var rowBytes = TacticalStoreV3.encode(control.row());
		rejects(() -> ActionFeedbackV4.nativeAction(rowBytes, "0".repeat(64), control.episode(), control.row().receipt().owner(), control.sourceSha()));
		final var off = TacticalPolicyV3.actionValueChoice(TacticalPolicyV3.Mode.OFF, cold, attempt.observation(), attempt.offered(), attempt.baseline(), false, Long.MAX_VALUE);
		final var emergency = TacticalPolicyV3.actionValueChoice(TacticalPolicyV3.Mode.ON, cold, attempt.observation(), attempt.offered(), attempt.baseline(), true, Long.MAX_VALUE);
		final var missing = TacticalPolicyV3.actionValueChoice(TacticalPolicyV3.Mode.ON, null, attempt.observation(), attempt.offered(), attempt.baseline(), false, Long.MAX_VALUE);
		final var timeout = TacticalPolicyV3.actionValueChoice(TacticalPolicyV3.Mode.ON, cold, attempt.observation(), attempt.offered(), attempt.baseline(), false, 0);
		check(off.selected().equals(attempt.baseline()) && emergency.selected().equals(attempt.baseline()) && missing.selected().equals(attempt.baseline()) && timeout.selected().equals(attempt.baseline()), "Stock safety fallback");
		final var decision = TacticalPolicyV3.actionValueChoice(TacticalPolicyV3.Mode.ON, cold, attempt.observation(), attempt.offered(), attempt.baseline(), false, Long.MAX_VALUE);
		check(decision.consulted() && decision.scores().size() == attempt.offered().size() && attempt.offered().contains(decision.selected()), "Lawful alternatives scored");
		final long train = samples.stream().filter(s -> s.row().split().equals("TRAIN")).count();
		final String evidence = "{\"sourceKind\":\"NATIVE\",\"read\":" + samples.size() + ",\"accepted\":" + samples.size()
			+ ",\"uniqueActors\":" + samples.stream().map(s -> s.row().actorProfile()).distinct().count() + ",\"uniqueEpisodes\":" + samples.stream().map(ActionFeedbackV4::episodeId).distinct().count()
			+ ",\"train\":" + train + ",\"heldout\":" + (samples.size() - train) + ",\"updates\":" + cold.updates() + ",\"replaySkipped\":" + train
			+ ",\"modelShaBefore\":\"" + before.sha256() + "\",\"modelShaAfter\":\"" + cold.sha256() + "\",\"oldScore\":" + oldScore + ",\"newScore\":" + score
			+ ",\"offeredSkills\":" + attempt.offered().stream().map(LegalActionV3::skillId).toList() + ",\"scores\":" + decision.scores() + ",\"stockSkill\":" + attempt.baseline().skillId() + ",\"selectedSkill\":" + decision.selected().skillId()
			+ ",\"decisionReason\":\"" + decision.reason() + "\",\"scoreChanged\":" + decision.scores().stream().filter(v -> v != 0).count()
			+ ",\"coldExact\":true,\"tamperRejected\":true,\"fallback\":true,\"superiority\":\"NOT_EVALUATED\",\"elapsedMillis\":" + (System.nanoTime() - start) / 1_000_000 + "}";
		Files.writeString(output.resolve("offline.json"), evidence, StandardOpenOption.CREATE_NEW);
		System.out.println("LEARNING019_OFFLINE_PASS " + evidence);
	}
	static void nativeOnce(Path root, String jarSha, String modelSha) throws Exception
	{
		check(Boolean.getBoolean("phantom.learning.train") && root.isAbsolute() && root.equals(root.toRealPath())
			&& root.getFileName().toString().equals("train019") && Files.readString(root.resolve("owner.txt")).matches("LEARNING002:[a-f0-9]{32}"), "Owned explicit TEST019");
		final Path game = root.resolve("game"), config = game.resolve("config/Custom/PhantomPlayers.ini"), jar = game.resolve("libs/GameServer.jar");
		check(Path.of(".").toRealPath().equals(game) && TacticalObservationV3.hash(Files.readAllBytes(jar)).equals(jarSha), "Native cwd/JAR pin");
		for (Class<?> type : List.of(org.l2jmobius.gameserver.GameServer.class, TacticalPolicyV3.class, ActionValueV4.class))
		{ check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar), "Native production class origin"); }
		final var database = new Properties(); try (var reader = Files.newBufferedReader(game.resolve("config/Database.ini"))) { database.load(reader); }
		check(database.getProperty("URL").equals("jdbc:mysql://127.0.0.1:33359/phantom_learning001_train002"), "Separate copied TEST019 SQL");
		final var endpoints = new Properties(); try (var reader = Files.newBufferedReader(game.resolve("config/Server.ini"))) { endpoints.load(reader); }
		check(endpoints.getProperty("GameserverHostname").equals("127.0.0.1") && endpoints.getProperty("GameserverPort").equals("18019")
			&& endpoints.getProperty("LoginHost").equals("127.0.0.1") && endpoints.getProperty("LoginPort").equals("19319"), "Separate loopback TEST019 ports");
		final var model = ActionLearningV4.load(root.resolve("candidate.bin"), modelSha);
		final String off = Files.readString(config); check(off.contains("EnablePhantomSystem = False"), "Cold master OFF");
		final var decisions = new java.util.concurrent.ConcurrentLinkedQueue<TacticalPolicyV3.ActionValueDecision>();
		final var receipts = new java.util.concurrent.ConcurrentLinkedQueue<TacticalReceiptV3>();
		final var failures = new java.util.concurrent.ConcurrentLinkedQueue<TacticalFailureV3>();
		boolean booted = false;
		try
		{
			org.l2jmobius.gameserver.GameServer.main(new String[0]); booted = true;
			check(!org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.isEnabled() && !org.l2jmobius.gameserver.phantoms.PhantomSystem.hasConfiguredInstance(), "Global native owner OFF");
			Files.writeString(config, PhantomTrainingNative.phaseConfiguration(off, "SHADOW", 0, 183, true));
			org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.load();
			final var tactical = new TacticalPolicyV3(TacticalPolicyV3.Mode.SHADOW, 0, null, 20, 16, 1000, _ -> { });
			tactical.actionValueTest(TacticalPolicyV3.Mode.ON, model, d -> { if (decisions.size() < 64) { decisions.add(d); } });
			tactical.damageObserver(r -> { if (receipts.size() < 32) { receipts.add(r); } });
			tactical.failureObserver(f -> { if (failures.size() < 32) { failures.add(f); } });
			// Existing read-only TEST observer factory supplies collecting=true; V1 remains SHADOW and receives no writes or training.
			final var observer = new LearningStore(org.l2jmobius.commons.config.DatabaseConfig.DATABASE_URL,
				org.l2jmobius.commons.config.DatabaseConfig.DATABASE_LOGIN, org.l2jmobius.commons.config.DatabaseConfig.DATABASE_PASSWORD, game).accepted(1);
			check(observer != null, "Existing native observer unavailable");
			final var policy = LearningPolicy.evaluation(LearningPolicy.Mode.SHADOW, 1, observer.model(), 4,
				TacticalObservationV3.hash("TEST019_NATIVE_OBSERVER".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
			final var rows = Files.readAllLines(root.resolve("roster.tsv")).stream().filter(s -> !s.isBlank()).map(s -> s.split("\t", -1)).toList();
			try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
				var runtime = new PhantomLearning001ShadowNative.Runtime(authority, game, policy, root.resolve("stop.request"), true, 1, tactical))
			{
				Files.writeString(root.resolve("native-facts.json"), runtime.actionValue019Solo(rows.getFirst(), () -> receipts.stream().anyMatch(r -> decisions.stream().anyMatch(d -> d.consulted() && d.selected().equals(r.attempt().selected())))), StandardOpenOption.CREATE_NEW);
			}
			final var matched = receipts.stream().filter(r -> decisions.stream().anyMatch(d -> d.consulted() && d.selected().equals(r.attempt().selected()))).toList();
			final var records = new ArrayList<Object>();
			for (var receipt : receipts)
			{
				Files.writeString(root.resolve(receipt.eventKey() + ".native.json"), PhantomLearning005Native.nativeReceiptJson(receipt), StandardOpenOption.CREATE_NEW);
				for (var decision : decisions.stream().filter(d -> d.selected().equals(receipt.attempt().selected())).toList())
				{ records.add(Map.of("eventKey", receipt.eventKey(), "offered", receipt.attempt().offered().stream().map(a -> a.skillId() + ":" + a.skillLevel()).toList(), "stock", receipt.attempt().baseline().skillId(), "chosen", receipt.attempt().selected().skillId(), "scores", decision.scores(), "reason", decision.reason(), "consulted", decision.consulted(), "paidHp", receipt.actualHp(), "paidMp", receipt.actualMp(), "scoreNanos", decision.nanos())); }
			}
			final String evidence = PhantomEvaluationOutcomeV2.jsonValue(Map.of("modelSha", modelSha, "MODEL_CONSULTED", !matched.isEmpty(), "MODEL_REORDERED", matched.stream().anyMatch(r -> !r.attempt().selected().equals(r.attempt().baseline())), "NATIVE_EXECUTED", !receipts.isEmpty(), "EFFECT_PAID", !matched.isEmpty(), "nativePaid", matched.size(), "records", records, "diagnostics", tactical.diagnostics(), "nativeFailures", failures.stream().map(TacticalFailureV3::reason).toList()));
			Files.writeString(root.resolve("native019.json"), evidence, StandardOpenOption.CREATE_NEW);
			check(!matched.isEmpty(), "V4_NATIVE_CONNECTION_MISSING");
			System.out.println("LEARNING019_NATIVE_PASS " + evidence);
		}
		catch (Exception | AssertionError failure)
		{
			if (!Files.exists(root.resolve("native019.json")))
			{
				final String reason = failure.getMessage() != null && failure.getMessage().matches("[A-Z0-9_]+") ? failure.getMessage() : failure.getClass().getSimpleName();
				Files.writeString(root.resolve("native019.json"), PhantomEvaluationOutcomeV2.jsonValue(Map.of("modelSha", modelSha, "MODEL_CONSULTED", false,
					"MODEL_REORDERED", false, "NATIVE_EXECUTED", false, "EFFECT_PAID", false, "nativePaid", 0, "reason", reason)), StandardOpenOption.CREATE_NEW);
			}
			throw failure;
		}
		finally
		{
			Files.writeString(config, off);
			if (booted) { org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.load(); org.l2jmobius.gameserver.Shutdown.getInstance().startShutdown(null, 0, false); }
		}
	}
}

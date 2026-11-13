/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.GameServer;
import org.l2jmobius.gameserver.Shutdown;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.LearningStore;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TrainingCollector;
import org.l2jmobius.gameserver.phantoms.learning.TrainingConfig;
import org.l2jmobius.gameserver.phantoms.learning.TrainingStore;

/** One owned stock GameServer bootstrap, multiple exact native episodes, no offline simulator. */
public final class PhantomTrainingNative
{
	static boolean evaluationOnly(TrainingConfig config)
	{return "true".equals(config.text("evaluation005Enabled","false"))||PhantomLearning005Native.outcomeEvaluation(config);}
	public static void main(String[] args) throws Exception
	{
		if (args.length != 4 || !args[1].matches("[a-f0-9]{32}") || !args[2].matches("[a-f0-9]{64}") || !Boolean.getBoolean("phantom.learning.train"))
		{ throw new IllegalArgumentException("Expected installation, attempt UUID, attested JAR SHA and config; training opt-in required"); }
		final var root = PhantomTrainingActors.physicalRoot(Path.of(args[0]));
		final var configPath = Path.of(args[3]).toAbsolutePath().normalize();
		if (!configPath.equals(root.resolve("TRAIN_CONFIG.ini")) || !configPath.toRealPath().equals(configPath)) { throw new IllegalArgumentException("Owned physical TRAIN_CONFIG.ini required"); }
		final var config = TrainingConfig.load(configPath);
		final var database = PhantomTrainingActors.settings(root);
		final boolean tacticalV3 = "true".equals(config.text("v3Enabled", "false"));
		if (!tacticalV3) { PhantomTrainingActors.verify(root, config); }
		final var scenes = tacticalV3 ? List.<PhantomTrainingScenarios.Scene>of() : PhantomTrainingActors.scenes(root);
		final var attempt = root.resolve("attempts").resolve(args[1]);
		if (!attempt.toRealPath().equals(attempt)) { throw new IllegalArgumentException("Physical owned attempt required"); }
		final var game = root.resolve("game");
		final var jar = game.resolve("libs/GameServer.jar");
		if (!args[2].equals(NeuralPolicy.sha256(Files.readAllBytes(jar)))) { throw new IllegalArgumentException("Attested JAR SHA changed"); }
		for (Class<?> type : List.of(GameServer.class, LearningPolicy.class, PhantomCombatService.class))
		{
			if (!Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar)) { throw new IllegalArgumentException("Production class did not load from attested JAR"); }
		}
		final var endpoints = new Properties();
		try (var reader = Files.newBufferedReader(game.resolve("config/Server.ini"))) { endpoints.load(reader); }
		if (!"127.0.0.1".equals(endpoints.getProperty("GameserverHostname")) || !Integer.toString(config.integer("gamePort", 17971, 1024, 65535)).equals(endpoints.getProperty("GameserverPort"))
			|| !"127.0.0.1".equals(endpoints.getProperty("LoginHost")) || !Integer.toString(config.integer("loginPort", 19271, 1024, 65535)).equals(endpoints.getProperty("LoginPort"))
			|| !".".equals(endpoints.getProperty("DatapackRoot"))) { throw new IllegalArgumentException("Exact owned loopback GameServer endpoints required"); }
		final var phantomConfig = game.resolve("config/Custom/PhantomPlayers.ini");
		final String off = Files.readString(phantomConfig);
		if (!off.matches("(?s).*EnablePhantomSystem\\s*=\\s*False.*")) { throw new IllegalArgumentException("Master must be OFF at bootstrap"); }
		final String mode = config.text("learningMode", "SHADOW");
		final long version = config.longValue("modelVersion", 0, 0, Long.MAX_VALUE);
		String phase = setting(off, "LearningMode", mode);
		phase = setting(phase, "LearningModelVersion", Long.toString(version));
		phase = setting(phase, "LearningEvidenceEnabled", "True");
		phase = setting(phase, "LearningDeterministicSeed", Long.toString(config.seed()));
		final var store = new TrainingStore(root);
		store.initialize();
		final long catalogGeneration = tacticalV3 ? 0 : PhantomTrainingActors.generation(root);
		if (!tacticalV3 && catalogGeneration != store.counts().attempts()) { throw new IllegalStateException("Prepared actual actor cohort does not match durable evaluation generation"); }
		String run = Files.readString(root.resolve("owner.txt")).trim().substring("LEARNING002:".length());
		store.recover(run, args[1]);
		boolean booted = false;
		final long startup = System.nanoTime();
		try
		{
			Files.writeString(phantomConfig, phase);
			GameServer.main(new String[0]);
			booted = true;
			final String pid = Long.toString(ProcessHandle.current().pid());
			Files.writeString(attempt.resolve("game.ready"), pid, StandardOpenOption.CREATE_NEW);
			await(() -> Files.isRegularFile(attempt.resolve("socket.ok")), Duration.ofSeconds(45), "OS loopback socket was not attested");
			if (!pid.equals(Files.readString(attempt.resolve("socket.ok")))) { throw new IllegalStateException("Socket attestation PID changed"); }
			if (PhantomPlayersConfig.isEnabled() || PhantomSystem.hasConfiguredInstance()) { throw new IllegalStateException("Global Phantom owner must remain inactive"); }
			Files.writeString(phantomConfig, setting(phase, "EnablePhantomSystem", "True"));
			PhantomPlayersConfig.load();
			if(PhantomLearning008Native.enabled(config))
			{
				if(config.text("exploration008Phase","").equals("COLLECT")){PhantomLearning008Native.run(root,attempt,config);}
				else if(PhantomLearning005Native.generalizationEvaluation(config)){PhantomLearning008Native.runEvaluation(root,attempt,config);}
				else{throw new IllegalArgumentException("008 native phase refused");}
				return;
			}
			if(PhantomLearning007Native.enabled(config)&&config.text("exploration007Phase","").equals("COLLECT"))
			{PhantomLearning007Native.run(root,attempt,config);return;}
			if (evaluationOnly(config))
			{
				PhantomLearning005Native.run(root, attempt, config);
				return;
			}
			if ("ON".equals(mode))
			{
				var model = new LearningStore(database.getProperty("URL"), "learning001", database.getProperty("Password"), game).accepted(version);
				if (model == null || !model.model().sha256().equals(config.text("modelSha", ""))) { throw new IllegalStateException("Cold accepted SQL model SHA changed"); }
				System.out.println("TRAIN_COLD_MODEL version=" + version + " sha=" + model.model().sha256());
			}
			final var policy = LearningPolicy.configured(game);
			final var collector = new TrainingCollector();
			policy.observer(collector);
			if (!policy.collecting()) { throw new IllegalStateException("Native learning collector unavailable"); }
			if (tacticalV3)
			{
				try
				{
					if ("true".equals(config.text("v3Core004", "false"))) { PhantomLearning004Native.run(root, attempt, config, policy); }
					else { PhantomLearning003Native.run(root, attempt, config, policy); }
				}
				finally { await(policy::stop, Duration.ofSeconds(15), "Native evidence writer did not drain"); }
				return;
			}
			int committed = 0, rejected = 0;
			try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
				var runtime = new PhantomLearning001ShadowNative.Runtime(authority, game, policy, attempt.resolve("stop.request"), "ON".equals(mode), config.maxActiveActors()))
			{
				System.out.println("TRAIN_BOOT_READY elapsedMillis=" + Duration.ofNanos(System.nanoTime() - startup).toMillis() + " scenes=" + scenes.size());
				final var modes = java.util.Arrays.stream(config.text("modes", config.text("mode", "SOLO,PARTY")).split(",")).map(String::trim).toList();
				for (int index = 0; index < config.episodesPerBatch() && !requested(attempt); index++)
				{
					if (scenes.isEmpty()) { break; }
					final long ordinal = store.nextOrdinal();
					final long episodeSeed = config.seed() ^ Long.parseUnsignedLong(PhantomTrainingScenarios.hash("episode:" + ordinal).substring(0, 16), 16);
					final String scope = modes.get(Math.floorMod(ordinal, modes.size()));
					final var queue = PhantomTrainingScenarios.orderedKeys(scenes.stream().map(scene -> scene.id()).toList(), config.seed(), ordinal / modes.size());
					final String sceneId = queue.getFirst();
					final var scene = scenes.stream().filter(value -> sceneId.equals(value.id())).findFirst().orElseThrow();
					String episode = null;
					long started = System.nanoTime();
					long preparationNanos = -1, encounterNanos = -1;
					String outcome = "PREPARATION_REJECTED";
					String before = "{\"schema\":1,\"preparation\":\"UNKNOWN\",\"scene\":\"" + scene.id() + "\",\"configSeed\":" + config.seed() + ",\"episodeSeed\":" + episodeSeed + "}";
					try
					{
						before = "{\"configSeed\":" + config.seed() + ",\"episodeSeed\":" + episodeSeed + ",\"catalogGeneration\":" + catalogGeneration
							+ ",\"npcId\":" + scene.npc() + ",\"learnerClass\":" + scene.classId() + ",\"level\":" + scene.level() + ",\"weaponEnchant\":" + scene.weaponEnchant() + ",\"grade\":\"" + scene.grade()
							+ "\",\"observed\":" + runtime.prepareTraining(scene, PhantomTrainingActors.actors(root, scene.id()), "PARTY".equals(scope), config.preparationSeconds(), () -> requested(attempt)) + "}";
						preparationNanos = System.nanoTime() - started;
						episode = store.begin(run, args[1], ordinal, scene.id(), episodeSeed, scope, scene.split(), before);
						collector.begin(episode);
						System.out.println("TRAIN_EPISODE_ACTIVE episode=" + episode + " scope=" + scope + " scenario=" + scene.id() + " npc=" + scene.npc() + " level=" + scene.level() + " grade=" + scene.grade());
						final long encounterStarted = System.nanoTime();
						try { outcome = runtime.runTrainingEpisode(collector, config.episodeSeconds(), config.integer("eventsPerEpisode", 4, 1, 64), () -> requested(attempt)); }
						finally { encounterNanos = System.nanoTime() - encounterStarted; }
					}
					catch (IllegalStateException unavailable)
					{
						outcome = "PREPARATION_OR_NATIVE_REFUSED";
						PhantomTrainingScenarios.skip(outcome, scene.id() + ":" + unavailable.getMessage());
					}
					if (preparationNanos < 0) { preparationNanos = System.nanoTime() - started; }
					// Failure to drain is a batch failure: never start another scene over retained owners.
					final long settlingStarted = System.nanoTime();
					final String worldAfter = runtime.settleTraining();
					final long settlementNanos = System.nanoTime() - settlingStarted;
					final String after = "{\"observed\":" + worldAfter + ",\"timing\":{\"preparationNanos\":" + preparationNanos
						+ ",\"encounterNanos\":" + (encounterNanos < 0 ? "null" : encounterNanos) + ",\"settlementNanos\":" + settlementNanos + "}}";
					final var observations = collector.end();
					if (episode == null) { episode = store.begin(run, args[1], ordinal, scene.id(), episodeSeed, scope, scene.split(), before); }
					long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
					long sqlReadbackNanos = -1;
					long commitStarted = System.nanoTime();
					if (!observations.isEmpty() && ("NATIVE_EVIDENCE".equals(outcome) || "REQUESTED_DRAIN".equals(outcome)))
					{
						awaitNativeSql(observations);
						sqlReadbackNanos = System.nanoTime() - commitStarted;
						commitStarted = System.nanoTime();
						if (store.finish(episode, after, observations, outcome, elapsed)) { committed++; }
						else { rejected++; outcome = "SQL_NATIVE_VERIFICATION_REJECTED"; }
					}
					else { store.abort(episode, outcome, after, elapsed); rejected++; }
					System.out.println("TRAIN_EPISODE_TIMING episode=" + episode + " preparationNanos=" + preparationNanos + " encounterNanos=" + encounterNanos
						+ " settlementNanos=" + settlementNanos + " sqlReadbackNanos=" + sqlReadbackNanos + " sqlCommitNanos=" + (System.nanoTime() - commitStarted));
					System.out.println("TRAIN_EPISODE_END episode=" + episode + " outcome=" + outcome + " exact=" + observations.size() + " elapsedMillis=" + elapsed);
				}
			}
			finally { await(policy::stop, Duration.ofSeconds(15), "Native evidence writer did not drain"); }
			Files.writeString(attempt.resolve("batch.complete"), "schemaVersion=1\ncommitted=" + committed + "\nrejected=" + rejected + "\nreason="
				+ (scenes.isEmpty() ? "NONE_FEASIBLE" : committed == 0 ? "NO_COMMITTED_NATIVE_EPISODE" : requested(attempt) ? "REQUESTED_DRAIN" : "BATCH_LIMIT") + "\n", StandardOpenOption.CREATE_NEW);
		}
		catch (Exception failure)
		{
			Files.writeString(attempt.resolve("batch.failed"), failure.getClass().getSimpleName() + ": " + failure.getMessage(), StandardOpenOption.CREATE_NEW);
			throw failure;
		}
		finally
		{
			Files.writeString(phantomConfig, off);
			if (booted) { PhantomPlayersConfig.load(); Shutdown.getInstance().startShutdown(null, 0, false); }
		}
	}

	private static void awaitNativeSql(List<TrainingCollector.Observation> observations) throws Exception
	{
		final var keys = observations.stream().map(TrainingCollector.Observation::eventKey).filter(key -> key != null && key.matches("[a-f0-9]{64}")).distinct().toList();
		if (keys.isEmpty()) { throw new IllegalStateException("No exact recorded keys"); }
		final long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (true)
		{
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT COUNT(*) FROM phantom_ai_training_examples WHERE origin='NATIVE' AND evidence_kind='EXACT_CAST_HP' AND event_key IN (" + String.join(",", java.util.Collections.nCopies(keys.size(), "?")) + ")"))
			{
				for (int index = 0; index < keys.size(); index++) { query.setString(index + 1, keys.get(index)); }
				try (var rows = query.executeQuery()) { if (rows.next() && rows.getInt(1) == keys.size()) { return; } }
			}
			if (System.nanoTime() >= deadline) { throw new IllegalStateException("Exact native writer SQL readback timed out"); }
			Thread.sleep(200);
		}
	}
	private static boolean requested(Path attempt) { return Files.exists(attempt.resolve("stop.request")) || Files.exists(attempt.resolve("pause.request")); }
	private static String setting(String original, String key, String value)
	{
		String pattern = "(?m)^" + key + "\\s*=.*$";
		if (!java.util.regex.Pattern.compile(pattern).matcher(original).find()) { throw new IllegalArgumentException("Missing required native config key " + key); }
		return original.replaceAll(pattern, key + " = " + value);
	}
	private static void await(BooleanSupplier completed, Duration timeout, String reason) throws Exception
	{
		long deadline = System.nanoTime() + timeout.toNanos();
		while (!completed.getAsBoolean()) { if (System.nanoTime() >= deadline) { throw new IllegalStateException(reason); } Thread.sleep(100); }
	}
	private PhantomTrainingNative() { }
}

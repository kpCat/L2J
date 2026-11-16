/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.l2jmobius.gameserver.phantoms.learning.*;
import org.l2jmobius.gameserver.phantoms.combat.*;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import static org.l2jmobius.tests.phantoms.PhantomEvaluationReplayV2.*;
import static org.l2jmobius.tests.phantoms.PhantomLearning019ActionValue.check;

/** Один TEST-only acquired encounter. Никаких producer credits, SQL fit или фонового director. */
public final class PhantomLearning020V4Native
{
	static final String PARENT = "569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061";
	static String sha(Path p) throws Exception { return TacticalObservationV3.hash(Files.readAllBytes(p)); }
	static void write(Path p, String value) throws Exception { Files.writeString(p, value, StandardOpenOption.CREATE_NEW); }
	static String json(Object value) { return PhantomEvaluationOutcomeV2.jsonValue(value); }
	static void physical(Path p) throws Exception { check(p.isAbsolute() && p.normalize().equals(p.toRealPath()), "V4_PHYSICAL_PATH_REQUIRED"); }
	static void claimBoot(Path root, String jarSha, String preflightSha, String modelSha) throws Exception
	{
		var reservation = object(root.resolve("boot-reserved020.json"));
		check(string(reservation, "jarSha").equals(jarSha) && string(reservation, "preflightSha").equals(preflightSha) && string(reservation, "modelSha").equals(modelSha), "V4_BOOT_RESERVATION");
		check(!Files.exists(root.resolve("stop.request")) && !Files.exists(root.resolve("safety020.json")) && !Files.exists(root.resolve("cut020.json")) && !Files.exists(root.resolve("native020.json")), "V4_STOPPED_OR_USED_NATIVE_ROOT");
		write(root.resolve("native-entered020.marker"), preflightSha); // CREATE_NEW закрывает повтор и конкурентный ingress до GS.
	}
	static void nativeRecord(TacticalStoreV3.Row row, Map<String, Object> raw)
	{
		check(string(raw, "observationSha").equals(TacticalObservationV3.hash(row.receipt().attempt().observation().bytes())), "V4_NATIVE_OBSERVATION");
		String original = string(raw, "nativeRecord"), restored = row.receipt().toString();
		check(original.contains(", entities=[") && original.contains(", offered=[")
			&& original.substring(0, original.indexOf(", entities=[")).equals(restored.substring(0, restored.indexOf(", entities=[")))
			&& original.substring(original.indexOf(", offered=[")).equals(restored.substring(restored.indexOf(", offered=["))), "V4_NATIVE_ACTION_OWNER_EFFECT_MISMATCH");
	}
	static void contracts(Path cohort) throws Exception
	{
		preparationContract(cohort);
		var sample = PhantomLearning019ActionValue.source(cohort).getFirst();
		var raw = map(parse(PhantomLearning005Native.nativeReceiptJson(sample.row().receipt()))); nativeRecord(sample.row(), raw);
		raw.put("nativeRecord", string(raw, "nativeRecord") + " TAMPER");
		boolean refused = false; try { nativeRecord(sample.row(), raw); } catch (AssertionError expected) { refused = true; }
		check(refused, "V4_RAW_EFFECT_OWNER_TAMPER_ACCEPTED");
		Path root = Files.createTempDirectory("v4-contract020-");
		try
		{
			write(root.resolve("boot-reserved020.json"), json(Map.of("jarSha", "a".repeat(64), "preflightSha", "b".repeat(64), "modelSha", PARENT)));
			claimBoot(root, "a".repeat(64), "b".repeat(64), PARENT);
			refused = false; try { claimBoot(root, "a".repeat(64), "b".repeat(64), PARENT); } catch (Exception | AssertionError expected) { refused = true; }
			check(refused, "V4_SECOND_NATIVE_BOOT_ACCEPTED");
			Files.delete(root.resolve("native-entered020.marker")); write(root.resolve("stop.request"), "STOP");
			refused = false; try { claimBoot(root, "a".repeat(64), "b".repeat(64), PARENT); } catch (Exception | AssertionError expected) { refused = true; }
			check(refused, "V4_STOP_REQUEST_NATIVE_BOOT_ACCEPTED");
		}
		finally { for (String name : List.of("boot-reserved020.json", "native-entered020.marker", "stop.request")) { Files.deleteIfExists(root.resolve(name)); } Files.delete(root); }
		System.out.println("LEARNING020_CONTRACTS_PASS GS=0 SQL=0");
	}
	static void preparationContract(Path cohort) throws Exception
	{
		var candidate = candidates(cohort).getFirst();
		check(candidates(cohort).size() == 1 && candidate.containsKey("sourceImage"), "V4_RECONSTRUCTION_SOURCE_AND_ONE_CANDIDATE_REQUIRED");
		String source = string(candidate, "sourceImage");
		var image = PhantomAutonomousTrainingNative.soloSource020(string(candidate, "cell"), source);
		check(image.sha().equals(string(candidate, "sourceSha")) && image.learned().size() >= 2, "V4_CANONICAL_RECONSTRUCTIBLE_SOURCE");
		boolean refused = false; try { PhantomAutonomousTrainingNative.soloSource020(string(candidate, "cell"), source.replace("learn001fixture", "foreign")); } catch (Exception | AssertionError expected) { refused = true; }
		check(refused, "V4_FOREIGN_RECONSTRUCTION_SOURCE_ACCEPTED");
		Path module = Path.of(System.getProperty("phantom.module.root", "L2J_Mobius_CT_2.6_HighFive"));
		String route = Files.readString(module.resolve("test/java/org/l2jmobius/tests/phantoms/PhantomLearning020V4Native.java"));
		route = route.substring(route.lastIndexOf("static TacticalEpisodeV3 encounter("), route.lastIndexOf("static void nativeOnce("));
		preparationRoute(route); refused = false;
		try { preparationRoute(route.replace(".positionAcquired016(", ".missingPosition(")); } catch (AssertionError expected) { refused = true; }
		check(refused, "V4_MISSING_POSITION_STAGE_ACCEPTED");
		System.out.println("LEARNING020_PREPARATION_CONTRACT_PASS original016=true candidate=1 recovery=180");
	}
	static void preparationRoute(String route)
	{
		int offset = 0;
		for (String stage : List.of(".reproduceSolo020(", ".prepareAcquired(", ".realize(", ".prepareAcquiredOutcome(", ".positionAcquired016(", ".eligibility(", ".acquiredEncounterAdmission(", ".runLearning006Encounter("))
		{ int next = route.indexOf(stage, offset); check(next >= offset, "V4_REQUIRED016_STAGE_" + stage); offset = next + stage.length(); }
	}
	static void owned(Path root) throws Exception
	{
		physical(root); check(root.getFileName().toString().matches("train020(?:-c1|-[a-f0-9]{32})?")
			&& Files.readString(root.resolve("owner.txt")).matches("LEARNING002:[a-f0-9]{32}"), "V4_OWNED_TEST020_REQUIRED");
	}
	static void bindings(Path root, Map<String, Object> files) throws Exception
	{
		for (var entry : files.entrySet())
		{
			Path p = root.resolve(entry.getKey()).normalize(); check(p.startsWith(root), "V4_SOURCE_ESCAPE"); physical(p);
			check(sha(p).equals(entry.getValue()), "V4_SOURCE_SHA_MISMATCH");
		}
	}
	static List<Map<String, Object>> candidates(Path cohort) throws Exception
	{
		physical(cohort);
		check(sha(cohort.resolve("cohort016.json")).equals(string(object(cohort.resolve("cohort016.complete.json")), "manifestSha")), "V4_SEALED016_REQUIRED");
		bindings(cohort, map(object(cohort.resolve("cohort016.json")).get("files")));
		final var result = new ArrayList<Map<String, Object>>();
		for (String line : Files.readAllLines(cohort.resolve("cells.tsv")).stream().skip(1).toList())
		{
			String[] cell = line.split("\t", -1); check(cell.length == 16, "V4_CELL_CODEC");
			if (cell[1].contains(",") || !cell[2].equals("DAMAGE") || !cell[3].equals("RANGED_MAGIC")) { continue; }
			var inventory = object(cohort.resolve(cell[1] + ".inventory.json"));
			int classId = integer(inventory, "classId"); if (classId != 94) { continue; }
			String sourceImage = text(cohort.resolve(cell[1] + ".source.json"));
			PhantomAutonomousTrainingNative.soloSource020(line, sourceImage);
			try (var dirs = Files.list(cohort))
			{
				for (Path work : dirs.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}")).sorted().toList())
				{
					if (!Files.isRegularFile(work.resolve("terminal.bin"))) { continue; }
					var r = object(work.resolve("result.json")); var reserved = object(work.resolve("reserved.json"));
					var schedule = Files.readAllLines(cohort.resolve("schedule.tsv")).stream().skip(1).map(s -> s.split("\t", -1))
						.filter(s -> Long.parseLong(s[0]) == number(reserved, "slot") && s[1].equals(cell[0])).findFirst();
					if (schedule.isEmpty() || !string(r, "scope").equals("SOLO") || integer(r, "classId") != classId || !string(r, "outcome").equals("COMPLETED") || number(r, "paidRawEffects") < 2) { continue; }
					var settled = object(work.resolve("settled016.json")); check(string(settled, "state").equals("NATIVE_SETTLED"), "V4_OLD_SETTLEMENT");
					bindings(cohort, map(settled.get("files")));
					var rows = Files.readAllLines(work.resolve("roster.tsv")).stream().filter(s -> !s.isBlank()).toList(); check(rows.size() == 1, "V4_SOLO_ROSTER");
					String[] row = rows.getFirst().split("\t", -1); check(row.length == 11 && row[4].equals(Integer.toString(classId)) && row[7].equals(string(inventory, "fingerprint")), "V4_ROSTER_INVENTORY");
					row[0] = "TRAIN"; // Новый split назначен до boot и результата, старый event не импортируется.
					var facts = new LinkedHashMap<String, Object>(); facts.put("cell", line); facts.put("roster", String.join("\t", row));
					facts.put("sourceSha", cell[1]); facts.put("inventorySha", sha(cohort.resolve(cell[1] + ".inventory.json")));
					facts.put("sourceImage", sourceImage); facts.put("inventoryFingerprint", string(inventory, "fingerprint"));
					facts.put("historicalEpisodeSha", sha(work.resolve("terminal.bin"))); facts.put("liveAdmitted", false); result.add(facts); break;
				}
			}
			if (result.size() == 1) { break; }
		}
		check(!result.isEmpty(), "V4_NO_SEALED_LAWFUL_CANDIDATE"); return List.copyOf(result);
	}
	static List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef> refs(String[] cell)
	{
		return Arrays.stream(cell[4].split(",")).map(s -> s.split(":"))
			.map(s -> new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(Integer.parseInt(s[0]), Integer.parseInt(s[1]))).toList();
	}
	static void preflight(Path cohort, Path root) throws Exception
	{
		owned(root); var parent = ActionLearningV4.load(root.resolve("candidate.bin"), PARENT); check(parent.updates() == 161, "V4_PARENT161_REQUIRED");
		var candidates = candidates(cohort); String nonce = UUID.randomUUID().toString().replace("-", "");
		write(root.resolve("preflight020.json"), json(Map.of("schema", "V4_SINGLE_STEP020", "split", "TRAIN", "nonce", nonce,
			"cohortSha", sha(cohort.resolve("cohort016.json")), "parentSha", PARENT, "candidates", candidates, "liveAdmitted", false)));
		System.out.println("LEARNING020_PREFLIGHT_PASS candidates=" + candidates.size() + " liveAdmitted=false parentUpdates=161");
	}
	static void sourceSql(Path root) throws Exception
	{
		owned(root); check(root.getFileName().toString().equals("train020-c1"), "V4_CONTINUATION_SQL_SCOPE");
		var database = new Properties(); try (var reader = Files.newBufferedReader(root.resolve("game/config/Database.ini"))) { database.load(reader); }
		check(database.getProperty("URL").equals("jdbc:mysql://127.0.0.1:33361/phantom_learning001_train002"), "V4_PRIVATE_SOURCE_SQL");
		var candidates = list(object(root.resolve("preflight020.json")).get("candidates")); check(candidates.size() == 1, "V4_ONE_CANDIDATE"); var candidate = map(candidates.getFirst());
		var image = PhantomAutonomousTrainingNative.soloSource020(string(candidate, "cell"), string(candidate, "sourceImage"));
		try (var connection = java.sql.DriverManager.getConnection(database.getProperty("URL"), database.getProperty("Login"), database.getProperty("Password")))
		{
			connection.setReadOnly(true); var actual = PhantomTrainingActors.readSavedActor(connection, image.sourceId());
			check(actual.sha().equals(image.sha()) && actual.physicalFingerprint().equals(image.physicalFingerprint()), "V4_PRIVATE_SOURCE_IMAGE_DRIFT_NO_GO");
		}
		var config = TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini")); check(config.preparationSeconds() == 180, "V4_ORIGINAL180_REQUIRED");
		write(root.resolve("source-sql020.json"), json(Map.of("sourceSha", image.sha(), "physicalSha", image.physicalFingerprint(), "sqlReadOnly", true, "gsBoots", 0, "recoverySeconds", 180, "configSha", config.sha256())));
		System.out.println("LEARNING020_SOURCE_SQL_PASS GS=0 sourceExact=true recovery=180");
	}
	static TacticalEpisodeV3 encounter(Path root, Map<String, Object> candidate, int ordinal, PhantomBackgroundSuite.ProductionAuthorityFixture authority,
		LearningPolicy policy, ActionValueV4 model, ConcurrentLinkedQueue<TacticalReceiptV3> receipts, ConcurrentLinkedQueue<TacticalPolicyV3.ActionValueDecision> decisions) throws Exception
	{
		String[] cell = string(candidate, "cell").split("\t", -1);
		var prepared = PhantomAutonomousTrainingNative.reproduceSolo020(authority, root, string(candidate, "cell"), string(candidate, "sourceImage"), string(candidate, "inventoryFingerprint"));
		var scene = prepared.scene(); var row = prepared.roster().getFirst(); var actions = prepared.actions();
		var point = new PhantomTopologyPoint(Integer.parseInt(cell[12]), Integer.parseInt(cell[13]), Integer.parseInt(cell[14]), 0);
		var tactical = new TacticalPolicyV3(TacticalPolicyV3.Mode.SHADOW, 0, null, 20, 16, 1000, _ -> { });
		tactical.actionValueTest(TacticalPolicyV3.Mode.ON, model, d -> { check(decisions.size() < 64, "V4_DECISION_BOUND"); decisions.add(d); });
		tactical.damageObserver(r -> { check(receipts.size() < 32, "V4_RECEIPT_BOUND"); receipts.add(r); });
		PhantomCombatCapabilityResolver.CapabilitySource frozen = prepared.capabilities();
		Monster target = null; java.util.function.Supplier<TacticalEpisodeV3> terminal = null;
		try (var runtime = new PhantomLearning001ShadowNative.Runtime(authority, root.resolve("game"), policy, root.resolve("stop.request"), true, 1, tactical, true, frozen))
		{
			try
			{
				var local = PhantomEncounterLocalSource.resolve(authority, scene, point).orElseThrow(() -> new IllegalStateException("V4_LOCAL_SOURCE_REQUIRED"));
				runtime.localEncounterSource(local);
				runtime.preparationObserver014(facts -> { try { Files.writeString(root.resolve("preparation020.jsonl"), json(facts) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); } catch (Exception failure) { throw new IllegalStateException("V4_PREPARATION_DIAGNOSTIC_FAILED", failure); } });
				runtime.prepareAcquired(scene, prepared.roster(), false, PhantomCombatMode.RANGED_MAGIC, actions, prepared.inventories(), prepared.preparationSeconds(), () -> Files.exists(root.resolve("stop.request")));
				var spawn = PhantomEncounterLocalSource.realize(local); target = (Monster) spawn.doSpawn(false); spawn.stopRespawn();
				runtime.prepareAcquiredOutcome(target); var actor = runtime.evaluationPlayers().getFirst(); var geo = GeoEngine.getInstance();
				PhantomAutonomousTrainingNative.positionAcquired016(scene, runtime.evaluationPlayers(), target);
				check(target.getX() == point.x() && target.getY() == point.y() && target.getZ() == point.z() && !target.isMoving() && !target.isInCombat()
					&& target.getAttackByList().isEmpty() && !target.isInvul() && target.isTargetable() && target.isAutoAttackable(actor), "V4_FRESH_NATIVE_TARGET_REQUIRED");
				check(geo.hasGeo(actor.getX(), actor.getY()) && geo.hasGeo(target.getX(), target.getY()) && geo.canSeeTarget(actor, target) && geo.canMoveToTarget(actor, target), "V4_NATIVE_GEO_LOS");
				PhantomLearning001ActorFixture.requireSafePartySpawn(actor.getX(), actor.getY(), actor.getZ());
				check(PhantomTrainingScenarios.factionIsolated(target, point, target), "V4_NATIVE_FACTION_ISOLATION");
				var inventory = PhantomSkillInventory.acquired(actor, authority.progression()); final Monster exact = target; var owner = actor.getNativeWorkOwner();
				check(owner != null && owner.isCurrent() && actions.size() >= 2 && actions.stream().allMatch(ref -> inventory.abilities().stream().anyMatch(a -> a.skill().equals(ref)
					&& PhantomSkillInventory.eligibility(actor, exact, a, owner::isCurrent, true).ready())), "V4_TWO_READY_NATIVE_ALTERNATIVES");
				for (int warm = 1; warm <= 3; warm++) { var observation = TacticalSnapshotV3.collect(actor, Long.parseLong(row[2]), warm, 20); observation.bytes(); }
				write(root.resolve("live" + ordinal + ".json"), json(Map.of("TEST_FIXTURE_SPAWN", true, "source", scene.source(), "npc", target.getId(), "classId", actor.getActiveClass(),
					"skills", cell[4], "hp", actor.getCurrentHp(), "mp", actor.getCurrentMp(), "inventory", inventory.fingerprint(), "geoLos", true, "ownerCurrent", true)));
				var admission = runtime.acquiredEncounterAdmission(45000);
				String nonce = string(object(root.resolve("preflight020.json")), "nonce"), id = TacticalObservationV3.hash((nonce + "/" + ordinal).getBytes(StandardCharsets.UTF_8));
				terminal = runtime.runLearning006Encounter(id, nonce, 45, () -> Files.exists(root.resolve("stop.request")), () -> List.copyOf(receipts), stamp ->
				{
					try { runtime.revalidateAcquiredEncounter(); write(root.resolve("cut020.json"), json(Map.of("episode", id, "nonce", nonce, "startedNanos", stamp, "split", "TRAIN", "modelSha", model.sha256(), "admission", admission))); }
					catch (Exception failure) { throw new IllegalStateException("V4_DURABLE_CUT_FAILED", failure); }
				});
			}
			catch (Exception | AssertionError failure)
			{
				var snapshots = runtime.evaluationPlayers().stream().map(p -> Map.ofEntries(Map.entry("hp", p.getCurrentHp()), Map.entry("maxHp", p.getMaxHp()), Map.entry("mp", p.getCurrentMp()), Map.entry("maxMp", p.getMaxMp()), Map.entry("moving", p.isMoving()), Map.entry("sitting", p.isSitting()), Map.entry("attacking", p.isAttackingNow()), Map.entry("casting", p.isCastingNow()), Map.entry("reason", String.valueOf(failure.getMessage())))).toList();
				write(root.resolve("preparation-refusal020.json"), json(snapshots)); throw failure;
			}
			finally
			{
				if (terminal == null) { runtime.cancelAcquiredEncounter(); runtime.settleTraining(); }
				else { var settled = runtime.settleLearning004(terminal).episode(); write(root.resolve("episode020.json"), settled.json()); }
				runtime.releaseOutcome006();
				if (target != null) { target.deleteMe(); }
			}
		}
		return episode(object(root.resolve("episode020.json")), number(object(root.resolve("cut020.json")), "startedNanos"));
	}
	static void nativeOnce(Path root, String jarSha, String modelSha, String preflightSha) throws Exception
	{
		owned(root); check(Boolean.getBoolean("phantom.learning.train") && modelSha.equals(PARENT) && sha(root.resolve("preflight020.json")).equals(preflightSha), "V4_EXPLICIT_FROZEN_TEST");
		Path game = root.resolve("game"), jar = game.resolve("libs/GameServer.jar"), config = game.resolve("config/Custom/PhantomPlayers.ini");
		check(Path.of(".").toRealPath().equals(game) && sha(jar).equals(jarSha), "V4_NATIVE_JAR_CWD");
		for (Class<?> type : List.of(org.l2jmobius.gameserver.GameServer.class, TacticalPolicyV3.class, ActionValueV4.class))
		{ check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar), "V4_PRODUCTION_JAR_ORIGIN"); }
		var db = new Properties(); try (var reader = Files.newBufferedReader(game.resolve("config/Database.ini"))) { db.load(reader); }
		boolean continuation = root.getFileName().toString().equals("train020-c1");
		check(db.getProperty("URL").equals("jdbc:mysql://127.0.0.1:" + (continuation ? 33361 : 33360) + "/phantom_learning001_train002"), "V4_SEPARATE_SQL020");
		if (continuation)
		{
			var sourceProof = object(root.resolve("source-sql020.json")); var reservation = object(root.resolve("boot-reserved020.json"));
			var frozenCandidates = list(object(root.resolve("preflight020.json")).get("candidates")); check(frozenCandidates.size() == 1, "V4_ONE_SOURCE_SQL_GATE");
			var frozen = map(frozenCandidates.getFirst()); var source = PhantomAutonomousTrainingNative.soloSource020(string(frozen, "cell"), string(frozen, "sourceImage"));
			check(bool(sourceProof, "sqlReadOnly") && number(sourceProof, "gsBoots") == 0 && number(sourceProof, "recoverySeconds") == 180
				&& string(sourceProof, "sourceSha").equals(source.sha()) && string(sourceProof, "physicalSha").equals(source.physicalFingerprint())
				&& string(sourceProof, "configSha").equals(sha(root.resolve("TRAIN_CONFIG.ini"))) && string(reservation, "sourceSqlSha").equals(sha(root.resolve("source-sql020.json")))
				&& number(reservation, "goal020BootOrdinal") == 2, "V4_SUCCESSFUL_OFFLINE_SOURCE_GATE_REQUIRED");
		}
		var endpoints = new Properties(); try (var reader = Files.newBufferedReader(game.resolve("config/Server.ini"))) { endpoints.load(reader); }
		check(endpoints.getProperty("GameserverHostname").equals("127.0.0.1") && endpoints.getProperty("GameserverPort").equals(continuation ? "18021" : "18020") && endpoints.getProperty("LoginHost").equals("127.0.0.1") && endpoints.getProperty("LoginPort").equals(continuation ? "19321" : "19320"), "V4_LOOPBACK020");
		var model = ActionLearningV4.load(root.resolve("candidate.bin"), modelSha); String off = Files.readString(config); check(off.contains("EnablePhantomSystem = False"), "V4_COLD_OFF");
		claimBoot(root, jarSha, preflightSha, modelSha);
		boolean booted = false; var receipts = new ConcurrentLinkedQueue<TacticalReceiptV3>(); var decisions = new ConcurrentLinkedQueue<TacticalPolicyV3.ActionValueDecision>();
		try
		{
			org.l2jmobius.gameserver.GameServer.main(new String[0]); booted = true;
			check(!org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.isEnabled() && !org.l2jmobius.gameserver.phantoms.PhantomSystem.hasConfiguredInstance(), "V4_GLOBAL_OWNER_OFF");
			Files.writeString(config, PhantomTrainingNative.phaseConfiguration(off, "SHADOW", 0, 183, true)); org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.load();
			var observer = new LearningStore(org.l2jmobius.commons.config.DatabaseConfig.DATABASE_URL, org.l2jmobius.commons.config.DatabaseConfig.DATABASE_LOGIN,
				org.l2jmobius.commons.config.DatabaseConfig.DATABASE_PASSWORD, game).accepted(1); check(observer != null, "V4_SHADOW_OBSERVER_REQUIRED");
			var policy = LearningPolicy.evaluation(LearningPolicy.Mode.SHADOW, 1, observer.model(), 4, TacticalObservationV3.hash("TEST020_NATIVE_OBSERVER".getBytes(StandardCharsets.UTF_8)));
			TacticalEpisodeV3 settled = null;
			try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				var candidates = list(object(root.resolve("preflight020.json")).get("candidates")); check(candidates.size() == 1, "V4_CANDIDATE_BOUND");
				for (int i = 0; i < candidates.size(); i++)
				{
					try { settled = encounter(root, map(candidates.get(i)), i, authority, policy, model, receipts, decisions); break; }
					catch (Exception | AssertionError refused)
					{
						write(root.resolve("refused" + i + ".json"), json(Map.of("reason", String.valueOf(refused.getMessage()), "afterCut", Files.exists(root.resolve("cut020.json")))));
						if (Files.exists(root.resolve("cut020.json")) || Files.exists(root.resolve("stop.request")) || !receipts.isEmpty()) { throw refused; }
					}
				}
			}
			check(settled != null, "V4_NO_LAWFUL_NATIVE_SCENE");
			var files = new TreeMap<String, Object>(); var records = new ArrayList<Object>();
			for (String name : List.of("preflight020.json", "cut020.json", "episode020.json", "boot-reserved020.json", "native-entered020.marker")) { files.put(name, sha(root.resolve(name))); }
			for (var receipt : receipts)
			{
				var decision = decisions.stream().filter(d -> d.consulted() && d.modelSha().equals(modelSha) && d.selected().equals(receipt.attempt().selected())).findFirst();
				if (decision.isEmpty()) { continue; } // LegalAction включает actor/target epoch и уникальный decision generation.
				String raw = receipt.eventKey() + ".native.json", encoded = receipt.eventKey() + ".row.bin";
				write(root.resolve(raw), PhantomLearning005Native.nativeReceiptJson(receipt));
				var row = new TacticalStoreV3.Row("TRAIN", "learning020|TRAIN|" + settled.id(), receipt); byte[] bytes = TacticalStoreV3.encode(row);
				Files.write(root.resolve(encoded), bytes, StandardOpenOption.CREATE_NEW);
				ActionFeedbackV4.nativeAction(bytes, TacticalObservationV3.hash(bytes), settled, receipt.owner(), sha(root.resolve(raw)));
				files.put(raw, sha(root.resolve(raw))); files.put(encoded, sha(root.resolve(encoded)));
				var d = decision.orElseThrow(); records.add(Map.of("eventKey", receipt.eventKey(), "stock", receipt.attempt().baseline().skillId(), "chosen", d.selected().skillId(), "scores", d.scores(), "reason", d.reason(), "paidHp", receipt.actualHp(), "paidMp", receipt.actualMp(), "elapsedNanos", receipt.elapsedNanos()));
			}
			check(!records.isEmpty(), "V4_MODEL_BOUND_PAID_RECEIPT_REQUIRED");
			var proof = new LinkedHashMap<String, Object>(); proof.put("modelSha", modelSha); proof.put("MODEL_CONSULTED", true);
			proof.put("MODEL_REORDERED", receipts.stream().anyMatch(r -> !r.attempt().baseline().equals(r.attempt().selected())));
			proof.put("NATIVE_EXECUTED", true); proof.put("EFFECT_PAID", true); proof.put("nativePaid", records.size()); proof.put("newTrainSamples", records.size());
			proof.put("settled", true); proof.put("episodeSha", settled.sha256()); proof.put("records", records); proof.put("files", files);
			write(root.resolve("native020.json"), json(proof)); System.out.println("LEARNING020_NATIVE_PASS paid=" + records.size());
		}
		finally
		{
			Files.writeString(config, off);
			if (booted) { org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.load(); org.l2jmobius.gameserver.Shutdown.getInstance().startShutdown(null, 0, false); }
		}
	}
	static List<ActionFeedbackV4> samples(Path root) throws Exception
	{
		owned(root); var proof = object(root.resolve("native020.json")); var files = map(proof.get("files")); bindings(root, files);
		check(files.keySet().containsAll(List.of("preflight020.json", "cut020.json", "episode020.json", "boot-reserved020.json", "native-entered020.marker")), "V4_REQUIRED_SETTLED_BINDINGS");
		var cut = object(root.resolve("cut020.json")); check(string(cut, "split").equals("TRAIN") && string(cut, "modelSha").equals(PARENT), "V4_PRE_CUT_TRAIN");
		var frozen = object(root.resolve("preflight020.json")); var boot = object(root.resolve("boot-reserved020.json"));
		check(string(frozen, "split").equals("TRAIN") && string(frozen, "nonce").equals(string(cut, "nonce")) && string(boot, "preflightSha").equals(sha(root.resolve("preflight020.json"))), "V4_FROZEN_SPLIT_OWNER_SOURCE");
		var episode = episode(object(root.resolve("episode020.json")), number(cut, "startedNanos")); check(episode.sha256().equals(string(proof, "episodeSha")) && episode.id().equals(string(cut, "episode")) && episode.ownerNonce().equals(string(cut, "nonce")), "V4_ORIGINAL_SETTLEMENT");
		var result = new ArrayList<ActionFeedbackV4>();
		for (Object value : list(proof.get("records")))
		{
			String key = string(map(value), "eventKey"); check(key.matches("[a-f0-9]{64}"), "V4_EVENT_KEY");
			check(files.containsKey(key + ".row.bin") && files.containsKey(key + ".native.json"), "V4_REQUIRED_ACTION_BINDINGS");
			byte[] bytes = Files.readAllBytes(root.resolve(key + ".row.bin")); var row = TacticalStoreV3.decode(bytes);
			check(row.eventKey().equals(key) && row.split().equals("TRAIN"), "V4_NATIVE_ROW_IDENTITY");
			var raw = object(root.resolve(key + ".native.json")); nativeRecord(row, raw);
			result.add(ActionFeedbackV4.nativeAction(bytes, TacticalObservationV3.hash(bytes), episode, row.receipt().owner(), sha(root.resolve(key + ".native.json"))));
		}
		check(!result.isEmpty() && result.stream().map(s -> s.row().eventKey()).distinct().count() == result.size(), "V4_FRESH_UNIQUE_BATCH"); return List.copyOf(result);
	}
	static void fit(Path root) throws Exception
	{
		var parent = ActionLearningV4.load(root.resolve("candidate.bin"), PARENT); var samples = samples(root); var learned = ActionLearningV4.fit(parent, samples);
		check(learned.updates() == 161 + samples.size() && !learned.sha256().equals(PARENT), "V4_NEW_INCREMENT_REQUIRED");
		if (Files.exists(root.resolve("fit020.json")))
		{
			var previous = object(root.resolve("fit020.json")); String name = string(previous, "candidate");
			check(name.equals("candidate020-" + learned.sha256() + ".bin") && string(previous, "newSha").equals(learned.sha256()) && string(previous, "nativeSha").equals(sha(root.resolve("native020.json"))), "V4_RETRY_CHANGED_FIT_SOURCE");
			var saved = ActionLearningV4.load(root.resolve(name), learned.sha256());
			check(Arrays.equals(saved.bytes(), learned.bytes()) && ActionLearningV4.fit(saved, samples).sha256().equals(saved.sha256()), "V4_RETRY_REPLAY_ZERO");
			System.out.println("LEARNING020_FIT_REPLAY_PASS additionalUpdates=0 sha=" + saved.sha256()); return;
		}
		Path path = root.resolve("candidate020-" + learned.sha256() + ".bin"); ActionLearningV4.save(path, learned);
		write(root.resolve("fit020.json"), json(Map.of("nativeSha", sha(root.resolve("native020.json")), "parentSha", PARENT, "newSha", learned.sha256(), "candidate", path.getFileName().toString(), "updatesBefore", 161, "updatesAfter", learned.updates(), "newTrainSamples", samples.size())));
		System.out.println("LEARNING020_FIT_PASS updates=" + learned.updates() + " sha=" + learned.sha256());
	}
	static void cold(Path root) throws Exception
	{
		var fit = object(root.resolve("fit020.json")); check(string(fit, "nativeSha").equals(sha(root.resolve("native020.json"))), "V4_COLD_NATIVE_PIN");
		String name = string(fit, "candidate"); check(name.matches("candidate020-[a-f0-9]{64}\\.bin"), "V4_COLD_PATH");
		var model = ActionLearningV4.load(root.resolve(name), string(fit, "newSha")); var samples = samples(root);
		check(model.updates() == number(fit, "updatesAfter") && ActionLearningV4.fit(model, samples).sha256().equals(model.sha256()), "V4_COLD_REPLAY_ZERO");
		byte[] damaged = model.bytes(); damaged[damaged.length - 1] ^= 1; PhantomLearning019ActionValue.rejects(() -> ActionValueV4.restore(damaged, model.sha256()));
		if (!Files.exists(root.resolve("cold020.json"))) { write(root.resolve("cold020.json"), json(Map.of("sha", model.sha256(), "updates", model.updates(), "replayAdditionalUpdates", 0, "tamperRejected", true, "separateJvm", true))); }
		else { var previous = object(root.resolve("cold020.json")); check(string(previous, "sha").equals(model.sha256()) && number(previous, "updates") == model.updates() && number(previous, "replayAdditionalUpdates") == 0, "V4_RETRY_COLD_PROOF_CHANGED"); }
		System.out.println("LEARNING020_COLD_PASS replay=0 sha=" + model.sha256());
	}
	public static void main(String[] args) throws Exception
	{
		check(args.length >= 2, "V4_COMMAND_REQUIRED"); Path root = Path.of(args[1]);
		switch (args[0])
		{
			case "preflight" -> { check(args.length == 3, "V4_PREFLIGHT_ARGUMENTS"); preflight(root, Path.of(args[2])); }
			case "contracts" -> contracts(root);
			case "source-sql" -> sourceSql(root);
			case "native" -> { check(args.length == 5, "V4_NATIVE_ARGUMENTS"); nativeOnce(root, args[2], args[3], args[4]); }
			case "fit" -> fit(root);
			case "cold" -> cold(root);
			default -> throw new IllegalArgumentException("V4_UNKNOWN_COMMAND");
		}
	}
}

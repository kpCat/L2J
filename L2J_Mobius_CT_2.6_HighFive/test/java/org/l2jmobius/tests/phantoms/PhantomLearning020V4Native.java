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
	static final boolean EXPERIMENT021 = Boolean.getBoolean("phantom.learning.explore021");
	static final boolean CONTINUATION021 = EXPERIMENT021 && Boolean.getBoolean("phantom.learning.explore021.continuation");
	static final String ROOT021 = CONTINUATION021 ? "train021-c1" : "train021";
	static final int SQL021 = CONTINUATION021 ? 33363 : 33362, GAME021 = CONTINUATION021 ? 18023 : 18022, LOGIN021 = CONTINUATION021 ? 19323 : 19322;
	static final String PARENT = EXPERIMENT021 ? PhantomLearning021V4Exploration.PARENT : "569295cf4d8f4172eee0e8a7e530246534dd1cf24d34ab95e07ded65c6b2f061";
	static final int PARENT_UPDATES = EXPERIMENT021 ? 179 : 161;
	private static ActionExplorationV4 experiment;
	private static final java.util.concurrent.ConcurrentHashMap<Long, ActionExperimentReceiptV4.Intent> intents021 = new java.util.concurrent.ConcurrentHashMap<>();
	private static volatile boolean intentIntegrity021 = true;
	static String sha(Path p) throws Exception { return TacticalObservationV3.hash(Files.readAllBytes(p)); }
	static void write(Path p, String value) throws Exception { Files.writeString(p, value, StandardOpenOption.CREATE_NEW); }
	static String json(Object value) { return PhantomEvaluationOutcomeV2.jsonValue(value); }
	static long experimentCount(List<Object> records)
	{ return records.stream().map(PhantomEvaluationReplayV2::map).filter(r -> string(r, "mode").startsWith("EXPLORE") && ((Number)r.get("stock")).intValue() != ((Number)r.get("chosen")).intValue()).count(); }
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
		physical(root); check((EXPERIMENT021 ? root.getFileName().toString().equals(ROOT021) : root.getFileName().toString().matches("train020(?:-c1|-[a-f0-9]{32})?"))
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
		owned(root); var parent = ActionLearningV4.load(root.resolve("candidate.bin"), PARENT); check(parent.updates() == PARENT_UPDATES, "V4_EXACT_PARENT_REQUIRED");
		var candidates = candidates(cohort); String nonce = UUID.randomUUID().toString().replace("-", "");
		write(root.resolve("preflight020.json"), json(Map.of("schema", "V4_SINGLE_STEP020", "split", "TRAIN", "nonce", nonce,
			"cohortSha", sha(cohort.resolve("cohort016.json")), "parentSha", PARENT, "candidates", candidates, "liveAdmitted", false)));
		System.out.println("LEARNING020_PREFLIGHT_PASS candidates=" + candidates.size() + " liveAdmitted=false parentUpdates=" + PARENT_UPDATES);
	}
	static void sourceSql(Path root) throws Exception
	{
		owned(root); check(root.getFileName().toString().equals(EXPERIMENT021 ? ROOT021 : "train020-c1"), "V4_CONTINUATION_SQL_SCOPE");
		var database = new Properties(); try (var reader = Files.newBufferedReader(root.resolve("game/config/Database.ini"))) { database.load(reader); }
		check(database.getProperty("URL").equals("jdbc:mysql://127.0.0.1:" + (EXPERIMENT021 ? SQL021 : 33361) + "/phantom_learning001_train002"), "V4_PRIVATE_SOURCE_SQL");
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
		if (EXPERIMENT021)
		{
			policy.observer(new LearningPolicy.Observer()
			{
				public void issued(LearningPolicy.Attempt a, org.l2jmobius.gameserver.model.actor.Player actor, Monster target)
				{
					if (a.tactical() == null) { return; }
					try
					{
						var d = experiment.decisions().stream().filter(v -> v.selected().equals(a.tactical().selected())).findFirst().orElseThrow();
						check(actor.getNativeWorkOwner() != null && actor.getNativeWorkOwner().isCurrent() && TacticalSnapshotV3.epoch(actor) == d.observation().actorEpoch()
							&& target.getObjectId() == d.selected().targetObjectId() && TacticalSnapshotV3.epoch(target) == d.selected().targetEpoch(), "V4_PRE_NATIVE_OWNER_TARGET");
						var owner = new HealEvidenceV3.SourceOwner(a.generation(), "Combat/" + a.request().profileId() + "/" + a.generation() + "/" + a.sequence());
						check(intents021.size() < 64 && intents021.putIfAbsent(d.cursor(), new ActionExperimentReceiptV4.Intent(d, owner, System.nanoTime())) == null, "V4_DUPLICATE_PRE_NATIVE_INTENT");
					}
					catch (Exception | AssertionError failed) { intentIntegrity021 = false; }
				}
				public void recorded(LearningPolicy.Attempt a, LearningStore.Evidence evidence) { }
			});
		}
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
				if (EXPERIMENT021)
				{
					var plan = new ActionExplorationV4.Plan(1, "COVERAGE_TEST_V1", 21001, 1, .15, "TRAIN", id, string(candidate, "sourceSha"), model.sha256(), actions.stream().map(a -> a.skillId() + ":" + a.skillLevel()).sorted().toList());
					experiment = new ActionExplorationV4(plan);
					ActionExperimentReceiptV4.publish(root.resolve("policy021.bin"), plan.bytes());
					tactical.actionExplorationTest(experiment);
				}
				terminal = runtime.runLearning006Encounter(id, nonce, 45, () -> Files.exists(root.resolve("stop.request")), () -> List.copyOf(receipts), stamp ->
				{
					try
					{
						runtime.revalidateAcquiredEncounter();
						var cut = new LinkedHashMap<String, Object>(Map.of("episode", id, "nonce", nonce, "startedNanos", stamp, "split", "TRAIN", "modelSha", model.sha256(), "admission", admission));
						if (EXPERIMENT021) { cut.put("policySha", sha(root.resolve("policy021.bin"))); }
						write(root.resolve("cut020.json"), json(cut));
					}
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
		if (EXPERIMENT021) { write(root.resolve("diagnostics021.json"), tactical.diagnostics()); }
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
		boolean continuation = root.getFileName().toString().equals("train020-c1") || EXPERIMENT021;
		check(db.getProperty("URL").equals("jdbc:mysql://127.0.0.1:" + (EXPERIMENT021 ? SQL021 : continuation ? 33361 : 33360) + "/phantom_learning001_train002"), "V4_SEPARATE_SQL020");
		if (continuation)
		{
			var sourceProof = object(root.resolve("source-sql020.json")); var reservation = object(root.resolve("boot-reserved020.json"));
			var frozenCandidates = list(object(root.resolve("preflight020.json")).get("candidates")); check(frozenCandidates.size() == 1, "V4_ONE_SOURCE_SQL_GATE");
			var frozen = map(frozenCandidates.getFirst()); var source = PhantomAutonomousTrainingNative.soloSource020(string(frozen, "cell"), string(frozen, "sourceImage"));
			check(bool(sourceProof, "sqlReadOnly") && number(sourceProof, "gsBoots") == 0 && number(sourceProof, "recoverySeconds") == 180
				&& string(sourceProof, "sourceSha").equals(source.sha()) && string(sourceProof, "physicalSha").equals(source.physicalFingerprint())
				&& string(sourceProof, "configSha").equals(sha(root.resolve("TRAIN_CONFIG.ini"))) && string(reservation, "sourceSqlSha").equals(sha(root.resolve("source-sql020.json")))
				&& number(reservation, "goal020BootOrdinal") == (EXPERIMENT021 ? CONTINUATION021 ? 2 : 1 : 2), "V4_SUCCESSFUL_OFFLINE_SOURCE_GATE_REQUIRED");
		}
		var endpoints = new Properties(); try (var reader = Files.newBufferedReader(game.resolve("config/Server.ini"))) { endpoints.load(reader); }
		check(endpoints.getProperty("GameserverHostname").equals("127.0.0.1") && endpoints.getProperty("GameserverPort").equals(EXPERIMENT021 ? Integer.toString(GAME021) : continuation ? "18021" : "18020") && endpoints.getProperty("LoginHost").equals("127.0.0.1") && endpoints.getProperty("LoginPort").equals(EXPERIMENT021 ? Integer.toString(LOGIN021) : continuation ? "19321" : "19320"), "V4_LOOPBACK020");
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
			if (EXPERIMENT021)
			{
				check(experiment != null && intentIntegrity021, "V4_EXPERIMENT_PLAN_AND_PRE_NATIVE_INTEGRITY_REQUIRED");
				ActionExplorationV4.validateLog(experiment.decisions());
				files.put("policy021.bin", sha(root.resolve("policy021.bin")));
				check(string(object(root.resolve("cut020.json")), "policySha").equals(sha(root.resolve("policy021.bin"))), "V4_PRE_CUT_POLICY_PIN");
				files.put("diagnostics021.json", sha(root.resolve("diagnostics021.json")));
				for (var d : experiment.decisions())
				{
					String name = "decision021-" + d.cursor() + ".bin";
					ActionExperimentReceiptV4.publish(root.resolve(name), d.bytes()); files.put(name, sha(root.resolve(name)));
				}
				for (var entry : new TreeMap<>(intents021).entrySet())
				{
					String name = "intent021-" + entry.getKey() + ".bin";
					ActionExperimentReceiptV4.publish(root.resolve(name), entry.getValue().bytes()); files.put(name, sha(root.resolve(name)));
				}
			}
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
				var d = decision.orElseThrow();
				var record = new LinkedHashMap<String, Object>(Map.of("eventKey", receipt.eventKey(), "stock", receipt.attempt().baseline().skillId(), "chosen", d.selected().skillId(), "scores", d.scores(), "reason", d.reason(), "paidHp", receipt.actualHp(), "paidMp", receipt.actualMp(), "elapsedNanos", receipt.elapsedNanos()));
				if (EXPERIMENT021)
				{
					var selected = experiment.decisions().stream().filter(e -> e.selected().equals(receipt.attempt().selected())).findFirst().orElseThrow();
					var intent = intents021.get(selected.cursor()); check(intent != null, "V4_PRE_NATIVE_INTENT_REQUIRED");
					var effect = ActionExperimentReceiptV4.bind(intent, sha(root.resolve("policy021.bin")), bytes, settled, sha(root.resolve(raw)));
					String effectName = receipt.eventKey() + ".effect021.bin"; ActionExperimentReceiptV4.publish(root.resolve(effectName), effect.bytes()); files.put(effectName, sha(root.resolve(effectName)));
					record.put("decision", "decision021-" + selected.cursor() + ".bin"); record.put("mode", selected.mode()); record.put("propensity", selected.propensity());
					record.put("decisionSha", effect.decisionSha()); record.put("effect", effectName); record.put("usefulHpFraction", effect.usefulHpFraction()); record.put("policy", selected.plan().policyId());
					record.put("intent", "intent021-" + selected.cursor() + ".bin");
				}
				records.add(record);
			}
			check(!records.isEmpty(), "V4_MODEL_BOUND_PAID_RECEIPT_REQUIRED");
			var proof = new LinkedHashMap<String, Object>(); proof.put("modelSha", modelSha); proof.put("MODEL_CONSULTED", true);
			proof.put("MODEL_REORDERED", EXPERIMENT021 ? decisions.stream().anyMatch(d -> d.reason().equals("MODEL") && !d.selected().equals(receipts.stream().filter(r -> r.attempt().selected().equals(d.selected())).findFirst().orElseThrow().attempt().baseline())) : receipts.stream().anyMatch(r -> !r.attempt().baseline().equals(r.attempt().selected())));
			if (EXPERIMENT021)
			{
				long experimental = experimentCount(records);
				long controls = records.stream().map(PhantomEvaluationReplayV2::map).filter(r -> string(r, "mode").equals("BASELINE")).count();
				proof.put("EXPERIMENT_REORDERED", experimental > 0); proof.put("experimentalPaid", experimental); proof.put("baselinePaid", controls);
				check(experimental >= 1 && controls >= 1, "V4_REAL_EXPERIMENT_AND_CONTROL_REQUIRED");
			}
			proof.put("NATIVE_EXECUTED", true); proof.put("EFFECT_PAID", true); proof.put("nativePaid", records.size()); proof.put("newTrainSamples", records.size());
			proof.put("settled", true); proof.put("episodeSha", settled.sha256()); proof.put("records", records); proof.put("files", files);
			if (EXPERIMENT021)
			{
				var logFiles = new TreeMap<String, Object>(); files.forEach((name, digest) -> { if (name.startsWith("decision021-") || name.startsWith("intent021-")) { logFiles.put(name, digest); } });
				ActionExperimentReceiptV4.publish(root.resolve("manifest021.json"), json(Map.of("schemaVersion", 1, "decisionCount", experiment.decisions().size(), "intentCount", intents021.size(), "recordCount", records.size(), "files", logFiles)).getBytes(StandardCharsets.UTF_8));
				files.put("manifest021.json", sha(root.resolve("manifest021.json")));
			}
			write(root.resolve("native020.json"), json(proof));
			if (EXPERIMENT021) { ActionExperimentReceiptV4.publish(root.resolve("native021.complete.json"), json(Map.of("schemaVersion", 1, "nativeSha", sha(root.resolve("native020.json")), "manifestSha", sha(root.resolve("manifest021.json")))).getBytes(StandardCharsets.UTF_8)); }
			System.out.println("LEARNING020_NATIVE_PASS paid=" + records.size());
		}
		finally
		{
			Files.writeString(config, off);
			if (booted) { org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.load(); org.l2jmobius.gameserver.Shutdown.getInstance().startShutdown(null, 0, false); }
		}
	}
	/** Explicit post-drain completion only for the known Integer-summary failure. Never recreates native rows/effects. */
	static void recoverPublication021(Path root) throws Exception
	{
		owned(root); check(CONTINUATION021 && !Files.exists(root.resolve("native020.json")) && !Files.exists(root.resolve("manifest021.json"))
			&& !Files.exists(root.resolve("native021.complete.json")), "V4_FRESH_OFFLINE_PUBLICATION_ONLY");
		var safety = object(root.resolve("safety020.json"));
		check(string(safety, "test").equals("OFF") && number(safety, "ownedProcesses") == 0 && bool(safety, "portsFree") && number(safety, "gsStarts") == 1, "V4_DRAINED_RECOVERY_ONLY");
		for (String name : List.of("native-process.json", "sql/owned-process.json"))
		{ check(ProcessHandle.of(number(object(root.resolve(name)), "pid")).filter(ProcessHandle::isAlive).isEmpty(), "V4_RECOVERY_PROCESS_LIVE"); }
		String error = Files.readString(root.resolve("native.err"));
		check(error.contains("EVAL012_INTEGER_stock") && error.contains("PhantomLearning020V4Native.lambda$nativeOnce$"), "V4_EXACT_POST_DRAIN_FAILURE_REQUIRED");
		var cut = object(root.resolve("cut020.json"));
		var settled = episode(object(root.resolve("episode020.json")), number(cut, "startedNanos"));
		check(string(cut, "policySha").equals(sha(root.resolve("policy021.bin"))) && string(cut, "modelSha").equals(PARENT)
			&& ActionLearningV4.load(root.resolve("candidate.bin"), PARENT).updates() == 179, "V4_RECOVERY_FROZEN_PARENT_POLICY");
		var files = new TreeMap<String, Object>();
		for (String name : List.of("preflight020.json", "cut020.json", "episode020.json", "boot-reserved020.json", "native-entered020.marker", "policy021.bin", "diagnostics021.json", "source-sql020.json", "TRAIN_CONFIG.ini", "safety020.json", "native.err", "native.out")) { files.put(name, sha(root.resolve(name))); }
		var decisions = new ArrayList<ActionExplorationV4.Decision>(); var intents = new HashMap<Long, ActionExperimentReceiptV4.Intent>();
		for (int i = 0; Files.exists(root.resolve("decision021-" + i + ".bin")); i++)
		{
			String name = "decision021-" + i + ".bin", intentName = "intent021-" + i + ".bin";
			var d = ActionExplorationV4.Decision.restore(Files.readAllBytes(root.resolve(name)));
			var intent = ActionExperimentReceiptV4.Intent.restore(Files.readAllBytes(root.resolve(intentName)));
			check(d.equals(intent.decision()) && d.plan().sha256().equals(string(cut, "policySha")), "V4_RECOVERY_ORIGINAL_INTENT");
			decisions.add(d); intents.put(d.cursor(), intent); files.put(name, sha(root.resolve(name))); files.put(intentName, sha(root.resolve(intentName)));
		}
		ActionExplorationV4.validateLog(decisions);
		var counters = map(object(root.resolve("diagnostics021.json")).get("counters")); long total = 0;
		for (String mode : List.of("BASELINE", "MODEL", "EXPLORE_COVERAGE", "EXPLORE_EPSILON"))
		{
			long expected = counters.containsKey(mode) ? number(counters, mode) : 0;
			check(decisions.stream().filter(d -> d.mode().equals(mode)).count() == expected, "V4_RECOVERY_COMPLETE_MODE_LOG"); total += expected;
		}
		check(total == decisions.size(), "V4_RECOVERY_COMPLETE_DECISION_COUNT");
		var records = new ArrayList<Object>();
		try (var paths = Files.list(root))
		{
			for (var path : paths.filter(p -> p.getFileName().toString().matches("[a-f0-9]{64}\\.row\\.bin")).sorted().toList())
			{
				byte[] bytes = Files.readAllBytes(path); var row = TacticalStoreV3.decode(bytes); var receipt = row.receipt();
				var d = decisions.stream().filter(v -> v.selected().equals(receipt.attempt().selected())).findFirst().orElseThrow();
				String key = row.eventKey(), rawName = key + ".native.json", effectName = key + ".effect021.bin";
				check(path.getFileName().toString().equals(key + ".row.bin"), "V4_RECOVERY_ROW_IDENTITY"); nativeRecord(row, object(root.resolve(rawName)));
				var effect = ActionExperimentReceiptV4.bind(intents.get(d.cursor()), string(cut, "policySha"), bytes, settled, sha(root.resolve(rawName)));
				check(Arrays.equals(effect.bytes(), Files.readAllBytes(root.resolve(effectName))), "V4_RECOVERY_ORIGINAL_EFFECT_REQUIRED");
				files.put(key + ".row.bin", sha(path)); files.put(rawName, sha(root.resolve(rawName))); files.put(effectName, sha(root.resolve(effectName)));
				var r = new LinkedHashMap<String, Object>(); r.put("eventKey", key); r.put("stock", d.stock().skillId()); r.put("chosen", d.selected().skillId()); r.put("mode", d.mode()); r.put("propensity", d.propensity());
				r.put("decision", "decision021-" + d.cursor() + ".bin"); r.put("intent", "intent021-" + d.cursor() + ".bin"); r.put("effect", effectName); r.put("decisionSha", effect.decisionSha()); r.put("policy", d.plan().policyId());
				r.put("paidHp", effect.paidHp()); r.put("paidMp", effect.paidMp()); r.put("elapsedNanos", effect.elapsedNanos()); r.put("usefulHpFraction", effect.usefulHpFraction()); r.put("scoresKnown", false); records.add(r);
			}
		}
		long experimental = experimentCount(records), controls = records.stream().map(PhantomEvaluationReplayV2::map).filter(r -> string(r, "mode").equals("BASELINE")).count();
		check(experimental == 1 && controls >= 1, "V4_RECOVERY_PAID_EXPERIMENT_AND_CONTROL_REQUIRED");
		var recovery = Map.of("schemaVersion", 1, "reason", "POST_DRAIN_INTEGER_SUMMARY", "nativeExit", 1, "originalFiles", new TreeMap<>(files), "trainingCredit", false);
		ActionExperimentReceiptV4.publish(root.resolve("recovery021.json"), json(recovery).getBytes(StandardCharsets.UTF_8)); files.put("recovery021.json", sha(root.resolve("recovery021.json")));
		var logFiles = new TreeMap<String, Object>(); files.forEach((name, digest) -> { if (name.startsWith("decision021-") || name.startsWith("intent021-")) { logFiles.put(name, digest); } });
		ActionExperimentReceiptV4.publish(root.resolve("manifest021.json"), json(Map.of("schemaVersion", 1, "decisionCount", decisions.size(), "intentCount", intents.size(), "recordCount", records.size(), "files", logFiles)).getBytes(StandardCharsets.UTF_8)); files.put("manifest021.json", sha(root.resolve("manifest021.json")));
		var proof = new LinkedHashMap<String, Object>(); proof.put("modelSha", PARENT); proof.put("MODEL_CONSULTED", true); proof.put("MODEL_REORDERED", false); proof.put("EXPERIMENT_REORDERED", true);
		proof.put("experimentalPaid", experimental); proof.put("baselinePaid", controls); proof.put("NATIVE_EXECUTED", true); proof.put("EFFECT_PAID", true); proof.put("nativePaid", records.size()); proof.put("newTrainSamples", records.size());
		proof.put("settled", true); proof.put("episodeSha", settled.sha256()); proof.put("records", records); proof.put("files", files); proof.put("postDrainSummaryRecovered", true); proof.put("nativeExit", 1);
		ActionExperimentReceiptV4.publish(root.resolve("native020.json"), json(proof).getBytes(StandardCharsets.UTF_8));
		ActionExperimentReceiptV4.publish(root.resolve("native021.complete.json"), json(Map.of("schemaVersion", 1, "nativeSha", sha(root.resolve("native020.json")), "manifestSha", sha(root.resolve("manifest021.json")))).getBytes(StandardCharsets.UTF_8));
		System.out.println("LEARNING021_OFFLINE_PUBLICATION_PASS originalPaid=" + records.size() + " experiment=" + experimental + " GS=0 SQL=0 trainingCredit=false nativeExit=1");
	}
	static void validateExperimentLog(Path root, Map<String, Object> proof, Map<String, Object> files) throws Exception
	{
		var seal = object(root.resolve("native021.complete.json")); var manifest = object(root.resolve("manifest021.json"));
		check(number(seal, "schemaVersion") == 1 && number(manifest, "schemaVersion") == 1 && string(seal, "nativeSha").equals(sha(root.resolve("native020.json")))
			&& string(seal, "manifestSha").equals(sha(root.resolve("manifest021.json"))) && files.containsKey("manifest021.json"), "V4_COMPLETE_PUBLICATION_SEAL");
		int count = integer(manifest, "decisionCount"); check(count > 0 && count <= 64, "V4_COMPLETE_DECISION_COUNT");
		var logFiles = map(manifest.get("files")); var restored = new ArrayList<ActionExplorationV4.Decision>(); var seen = new HashSet<String>();
		for (int i = 0; i < count; i++)
		{
			String name = "decision021-" + i + ".bin"; check(logFiles.containsKey(name) && Objects.equals(logFiles.get(name), files.get(name)), "V4_MISSING_DECISION_CURSOR");
			var d = ActionExplorationV4.Decision.restore(Files.readAllBytes(root.resolve(name))); restored.add(d); seen.add(name);
			check(d.plan().sha256().equals(sha(root.resolve("policy021.bin"))), "V4_CHANGED_FROZEN_POLICY");
		}
		ActionExplorationV4.validateLog(restored);
		int intentCount = 0;
		for (String name : logFiles.keySet())
		{
			if (!name.startsWith("intent021-")) { continue; }
			check(name.matches("intent021-[0-9]{1,2}\\.bin") && Objects.equals(logFiles.get(name), files.get(name)), "V4_INTENT_MANIFEST");
			var intent = ActionExperimentReceiptV4.Intent.restore(Files.readAllBytes(root.resolve(name)));
			int cursor = Math.toIntExact(intent.decision().cursor());
			check(cursor < count && intent.decision().equals(restored.get(cursor)) && name.equals("intent021-" + cursor + ".bin"), "V4_INTENT_LOG_MISMATCH"); seen.add(name); intentCount++;
		}
		check(intentCount == number(manifest, "intentCount") && logFiles.keySet().equals(seen), "V4_COMPLETE_INTENT_COUNT");
		try (var paths = Files.list(root))
		{
			var actual = paths.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("decision021-") || n.startsWith("intent021-")).collect(java.util.stream.Collectors.toSet());
			check(actual.equals(seen), "V4_UNDECLARED_OR_MISSING_LOG_FILE");
		}
		var records = list(proof.get("records")); check(records.size() == number(manifest, "recordCount") && records.size() == number(proof, "nativePaid")
			&& records.size() == number(proof, "newTrainSamples"), "V4_COMPLETE_PAID_COUNT");
		long experiments = 0, controls = 0; boolean modelReordered = false;
		for (var value : records)
		{
			var r = map(value); String name = string(r, "decision");
			check(logFiles.containsKey(name), "V4_UNDECLARED_PAID_DECISION");
			var d = ActionExplorationV4.Decision.restore(Files.readAllBytes(root.resolve(name))); ActionExperimentReceiptV4.validateSummary(r, d);
			if (d.mode().startsWith("EXPLORE") && !d.selected().equals(d.stock())) { experiments++; }
			if (d.mode().equals("BASELINE")) { controls++; }
			modelReordered |= d.mode().equals("MODEL") && !d.selected().equals(d.stock());
		}
		check(experiments >= 1 && controls >= 1 && experiments == number(proof, "experimentalPaid") && controls == number(proof, "baselinePaid")
			&& Boolean.TRUE.equals(proof.get("EXPERIMENT_REORDERED")) && Boolean.valueOf(modelReordered).equals(proof.get("MODEL_REORDERED")), "V4_EXPERIMENT_CONTROL_SUMMARY");
	}
	static List<ActionFeedbackV4> samples(Path root) throws Exception
	{
		owned(root); var proof = object(root.resolve("native020.json")); var files = map(proof.get("files")); bindings(root, files);
		if (Boolean.TRUE.equals(proof.get("postDrainSummaryRecovered"))) { check(files.containsKey("recovery021.json"), "V4_RECOVERY_SOURCE_MANIFEST_REQUIRED"); bindings(root, map(object(root.resolve("recovery021.json")).get("originalFiles"))); }
		if (EXPERIMENT021) { validateExperimentLog(root, proof, files); }
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
			if (EXPERIMENT021)
			{
				var r = map(value); String decisionName = string(r, "decision"), effectName = string(r, "effect");
				String intentName = string(r, "intent");
				check(files.containsKey("policy021.bin") && files.containsKey(decisionName) && files.containsKey(effectName) && files.containsKey(intentName), "V4_REQUIRED_EXPERIMENT_BINDINGS");
				var decision = ActionExplorationV4.Decision.restore(Files.readAllBytes(root.resolve(decisionName)));
				var intent = ActionExperimentReceiptV4.Intent.restore(Files.readAllBytes(root.resolve(intentName)));
				check(intent.decision().equals(decision), "V4_INTENT_DECISION_MISMATCH");
				ActionExperimentReceiptV4.validateSummary(r, decision);
				check(decision.plan().modelSha().equals(PARENT) && decision.plan().sourceSha().equals(string(map(list(frozen.get("candidates")).getFirst()), "sourceSha"))
					&& string(cut, "policySha").equals(sha(root.resolve("policy021.bin"))), "V4_EXPERIMENT_PARENT_SOURCE_POLICY");
				var effect = ActionExperimentReceiptV4.bind(intent, sha(root.resolve("policy021.bin")), bytes, episode, sha(root.resolve(key + ".native.json")));
				check(Arrays.equals(effect.bytes(), Files.readAllBytes(root.resolve(effectName))), "V4_EFFECT_METADATA_TAMPER");
			}
			result.add(ActionFeedbackV4.nativeAction(bytes, TacticalObservationV3.hash(bytes), episode, row.receipt().owner(), sha(root.resolve(key + ".native.json"))));
		}
		check(!result.isEmpty() && result.stream().map(s -> s.row().eventKey()).distinct().count() == result.size(), "V4_FRESH_UNIQUE_BATCH"); return List.copyOf(result);
	}
	static void fit(Path root) throws Exception
	{
		var parent = ActionLearningV4.load(root.resolve("candidate.bin"), PARENT); var samples = samples(root); var learned = ActionLearningV4.fit(parent, samples);
		check(learned.updates() == PARENT_UPDATES + samples.size() && !learned.sha256().equals(PARENT), "V4_NEW_INCREMENT_REQUIRED");
		if (Files.exists(root.resolve("fit020.json")))
		{
			var previous = object(root.resolve("fit020.json")); String name = string(previous, "candidate");
			check(name.equals((EXPERIMENT021 ? "candidate021-" : "candidate020-") + learned.sha256() + ".bin") && string(previous, "newSha").equals(learned.sha256()) && string(previous, "nativeSha").equals(sha(root.resolve("native020.json"))), "V4_RETRY_CHANGED_FIT_SOURCE");
			var saved = ActionLearningV4.load(root.resolve(name), learned.sha256());
			check(Arrays.equals(saved.bytes(), learned.bytes()) && ActionLearningV4.fit(saved, samples).sha256().equals(saved.sha256()), "V4_RETRY_REPLAY_ZERO");
			System.out.println("LEARNING020_FIT_REPLAY_PASS additionalUpdates=0 sha=" + saved.sha256()); return;
		}
		Path path = root.resolve((EXPERIMENT021 ? "candidate021-" : "candidate020-") + learned.sha256() + ".bin"); ActionLearningV4.save(path, learned);
		write(root.resolve("fit020.json"), json(Map.of("nativeSha", sha(root.resolve("native020.json")), "parentSha", PARENT, "newSha", learned.sha256(), "candidate", path.getFileName().toString(), "updatesBefore", PARENT_UPDATES, "updatesAfter", learned.updates(), "newTrainSamples", samples.size())));
		System.out.println("LEARNING020_FIT_PASS updates=" + learned.updates() + " sha=" + learned.sha256());
	}
	static void cold(Path root) throws Exception
	{
		var fit = object(root.resolve("fit020.json")); check(string(fit, "nativeSha").equals(sha(root.resolve("native020.json"))), "V4_COLD_NATIVE_PIN");
		String name = string(fit, "candidate"); check(name.matches((EXPERIMENT021 ? "candidate021-" : "candidate020-") + "[a-f0-9]{64}\\.bin"), "V4_COLD_PATH");
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
			case "recover-publication" -> recoverPublication021(root);
			case "native" -> { check(args.length == 5, "V4_NATIVE_ARGUMENTS"); nativeOnce(root, args[2], args[3], args[4]); }
			case "fit" -> fit(root);
			case "cold" -> cold(root);
			default -> throw new IllegalArgumentException("V4_UNKNOWN_COMMAND");
		}
	}
}

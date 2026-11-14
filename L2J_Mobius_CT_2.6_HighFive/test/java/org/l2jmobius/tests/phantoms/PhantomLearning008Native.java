/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.l2jmobius.commons.config.DatabaseConfig;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.phantoms.learning.*;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomLearning005Evaluation.*;

/** Bounded TEST orchestration. Existing Combat/Party owners execute every actual action. */
public final class PhantomLearning008Native
{
	private static long budgetOriginNanos, budgetBootstrapMillis = -1;
	private PhantomLearning008Native() { }
	static boolean enabled(TrainingConfig c)
	{ return c.text("exploration008Enabled", "false").equals("true") && c.text("exploration008Campaign", "").equals(ActionAdvantage008.CAMPAIGN); }
	static Path directory(Path root) { return root.resolve("outcome006/campaigns").resolve(ActionAdvantage008.CAMPAIGN); }
	private static String q(String value) { return PhantomLearning005Evaluation.q(value); }
	private static String hash(String value) { return NeuralPolicy.sha256(value.getBytes(StandardCharsets.UTF_8)); }
	private static String json(Map<String, String> facts)
	{ return "{" + new TreeMap<>(facts).entrySet().stream().map(e -> q(e.getKey()) + ":" + q(e.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}"; }
	static void immutable(Path path, byte[] bytes) throws Exception
	{
		Files.createDirectories(path.getParent());
		if (Files.exists(path)) { if (!path.equals(path.toRealPath()) || !Arrays.equals(bytes, Files.readAllBytes(path))) { throw new IllegalStateException("008 immutable artifact changed: " + path.getFileName()); } return; }
		Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
	}
	static void immutable(Path path, String text) throws Exception { immutable(path, (text + "\n").getBytes(StandardCharsets.UTF_8)); }
	private static long number(String json, String key)
	{
		var match = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
		if (!match.find()) { throw new IllegalArgumentException("008 budget field missing: " + key); } return Long.parseLong(match.group(1));
	}
	private static String text(String json, String key)
	{
		var match = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
		if (!match.find()) { throw new IllegalArgumentException("008 metadata field missing: " + key); } return match.group(1);
	}
	private static synchronized long elapsed(Path root) throws Exception
	{
		String budget = Files.readString(directory(root).resolve("budget.json")); TrainingStore.validateObservation(budget);
		if (budgetBootstrapMillis < 0) { budgetBootstrapMillis = Math.max(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime(), Math.max(0, Duration.between(Instant.parse(text(budget, "pendingStartedUtc")), Instant.now()).toMillis())); budgetOriginNanos = System.nanoTime(); }
		return budgetBootstrapMillis + Math.max(0, System.nanoTime() - budgetOriginNanos) / 1000000;
	}
	static boolean evaluationDeadline(Path root)
	{
		try { return number(Files.readString(directory(root).resolve("budget.json")), "wallMillis") + elapsed(root) >= 5100000; }
		catch (Exception unavailable) { return true; }
	}
	private static boolean collectionLimit(Path root, TrainingConfig c) throws Exception
	{
		String budget = Files.readString(directory(root).resolve("budget.json")); long current = elapsed(root), headroom = (c.preparationSeconds() + c.episodeSeconds() + c.integer("shutdownTimeoutSeconds", 180, 30, 900) + 150L) * 1000;
		return number(budget, "wallMillis") + current + headroom >= 5400000 || number(budget, "collectionWallMillis") + current + headroom >= 3600000;
	}
	static List<PhantomLearning008Scenarios.Slot> slots(Path root) throws Exception
	{
		var path = directory(root).resolve("catalog.tsv"); String body = Files.readString(path);
		var protocol = Files.readString(directory(root).resolve("protocol.json"));
		if (!hash(body).equals(text(protocol, "catalogSha"))) { throw new IllegalArgumentException("008 sealed catalog changed"); }
		var slots = body.lines().filter(s -> !s.isBlank() && !s.startsWith("ordinal\t")).map(PhantomLearning008Scenarios.Slot::parse).toList();
		if (slots.size() != 56 || slots.stream().map(PhantomLearning008Scenarios.Slot::ordinal).distinct().count() != 56) { throw new IllegalArgumentException("008 exact quota catalog required"); }
		return slots;
	}
	static void prepare(Path root, TrainingConfig c) throws Exception
	{
		if (!enabled(c) || !c.text("outcome006DatasetId", "").equals(ActionAdvantage008.DATASET) || !c.text("v3DatasetContextPrefix", "").equals("learning008|")) { throw new IllegalArgumentException("Exact008 TEST campaign/dataset/context required"); }
		var store = new TacticalStoreV3(root); store.initialize(); var parent = store.optimizationParent(); var teacher = store.candidate(11);
		if (Set.of("PREPARE", "COLLECT", "FIT").contains(c.text("exploration008Phase", "")) && parent.modelId() == 11
			&& !ActionAdvantage008.pinsMatch(parent.modelId(), teacher.sha(), parent.sha(), parent.state().model().sha256(), parent.state().updates(), parent.state().completedEpochs())) { throw new IllegalArgumentException("008 exact Adam11 parent changed"); }
		if (Set.of("PREPARE", "COLLECT").contains(c.text("exploration008Phase", "")) && parent.modelId() != 11) { throw new IllegalArgumentException("008 collection cannot continue after optimizer advance"); }
		var capability = PhantomLearning008Scenarios.installOwned(root); String jar = NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar")));
		var catalogPath = directory(root).resolve("catalog.tsv"); boolean nativeInfrastructure = false;
		if (!Files.exists(catalogPath))
		{
			PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure(); InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			nativeInfrastructure = true;
			try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				var list = PhantomLearning008Scenarios.catalog(c, authority); var body = new StringBuilder("ordinal\tsplit\tblock\texperimental\tdirection\tvariant\t" + PhantomTrainingScenarios.HEADER + "\n");
				for (var slot : list) { body.append(slot.row()).append('\n'); }
				immutable(catalogPath, body.toString().getBytes(StandardCharsets.UTF_8));
				var proof = new StringBuilder("{\"schemaVersion\":8,\"classes\":["); boolean comma = false;
				for (int classId : List.of(94, 103, 110))
				{
					var scene = list.stream().map(PhantomLearning008Scenarios.Slot::scene).filter(s -> s.classId() == classId).findFirst().orElseThrow();
					var learned = new HashMap<Integer, Integer>();
					for (var entry : org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getCompleteClassSkillTree(org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass.getPlayerClass(classId)).values())
					{ if (entry.getGetLevel() <= scene.level() && (entry.isLearnedByNpc() || entry.isAutoGet())) { learned.merge(entry.getSkillId(), entry.getSkillLevel(), Math::max); } }
					var loadout = org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.fromProgression(authority::progression).resolve(classId, org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode.RANGED_MAGIC,
						id -> learned.getOrDefault(id, 0), selected -> learned.getOrDefault(selected.skillId(), 0) >= selected.skillLevel() && PhantomLearning001ActorFixture.eligible(SkillData.getInstance().getSkill(selected.skillId(), selected.skillLevel())), false, 4).orElseThrow();
					if (loadout.selectedSkills().size() < 2) { throw new IllegalStateException("008_INSUFFICIENT_LAWFUL_CLASS_CANDIDATES_" + classId); }
					if (comma) { proof.append(','); } comma = true;
					proof.append("{\"class\":").append(classId).append(",\"level\":").append(scene.level()).append(",\"lawfulNativeSkills\":").append(q(loadout.selectedSkills().toString())).append(",\"source\":").append(q(scene.source())).append('}');
				}
				immutable(directory(root).resolve("class-preflight.json"), proof.append("],\"canonicalSkillData\":true,\"geoReachability\":true,\"gameServerStarted\":false,\"liveOwnerPreCut\":\"REQUIRED\",\"gradients\":0}").toString());
			}
		}
		String protocol = "{\"schemaVersion\":8,\"campaign\":" + q(ActionAdvantage008.CAMPAIGN) + ",\"dataset\":" + q(ActionAdvantage008.DATASET) + ",\"objectiveSha\":" + q(ActionAdvantage008.CONFIG_SHA)
			+ ",\"jarSha\":" + q(jar) + ",\"catalogSha\":" + q(hash(Files.readString(catalogPath))) + ",\"capabilitySha\":" + q(NeuralPolicy.sha256(Files.readAllBytes(capability)))
			+ ",\"parentModelSha\":" + q(ActionAdvantage008.TEACHER_SHA) + ",\"parentOptimizerSha\":" + q(ActionAdvantage008.PARENT_ADAM_SHA)
			+ ",\"trainEpisodes\":32,\"tuningEpisodes\":8,\"auditEpisodes\":8,\"evalPairsPerDirection\":2,\"controlPairs\":2,\"epsilon\":0.25,\"maximumOpportunityPerEpisode\":1,\"startsCeiling\":8,\"nativeMinutesCeiling\":90,\"collectionMinutesCeiling\":60,\"scope\":\"ISOLATED_TEST_ONLY\"}";
		sealBuild(root, jar, protocol); slots(root);
		if (!Files.exists(directory(root).resolve("geometry-preflight-r2.json")))
		{
			if (!nativeInfrastructure) { PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure(); InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE); }
			try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				var proof = new ArrayList<String>();
				for (var slot : slots(root)) { var s = slot.scene(); int[] p = position(root, authority, s); proof.add("{\"slot\":" + slot.ordinal() + ",\"classId\":" + s.classId() + ",\"source\":" + q(s.source()) + ",\"target\":" + q(p[0] + "," + p[1] + "," + p[2]) + ",\"offsets180And160\":\"GEO_LOS_DIRECT\"}"); }
				immutable(directory(root).resolve("geometry-preflight-r2.json"), "{\"schemaVersion\":8,\"selection\":\"CANONICAL_SOURCE_GEOMETRY_ONLY_NO_SCORE_NO_OUTCOME\",\"slots\":[" + String.join(",", proof) + "],\"gameServerStarted\":false}");
			}
		}
		var cold = new ArrayList<String>();
		for (int classId : List.of(94, 103, 110))
		{
			var capabilities = PhantomLearning008Scenarios.canonical(root.resolve("game"), classId, 77);
			int range = PhantomLearning001ActorFixture.coldOpeningRange(classId, id -> capabilities.stream().filter(s -> s.skillId() == id).mapToInt(PhantomLearning008Scenarios.Capability::range).findFirst().orElse(0));
			if (range <= 0 || capabilities.size() < 2) { throw new IllegalStateException("008_COLD_CLASS_RANGE_MISSING_" + classId); }
			cold.add("{\"classId\":" + classId + ",\"coldRange\":" + range + ",\"canonicalCandidates\":" + capabilities.size() + "}");
		}
		immutable(directory(root).resolve("cold-admission-preflight.json"), "{\"classes\":[" + String.join(",", cold) + "],\"safetyGatesUnchanged\":true,\"nativeOwnerPreCut\":\"REQUIRED\"}");
		System.out.println("LEARNING008_PREPARED catalog=SEALED canonicalClasses=94,103,110 nativeOwnerPreCut=REQUIRED parent=" + parent.sha() + " nativeGameServer=NOT_STARTED");
	}
	private static void sealBuild(Path root, String jar, String protocol) throws Exception
	{
		Path original = directory(root).resolve("protocol.json");
		if (!Files.exists(original) || text(Files.readString(original), "jarSha").equals(jar)) { immutable(original, protocol); return; }
		// A TEST adapter-only fix may rebuild JAR timestamps. Production payload must remain byte-identical.
		String old = Files.readString(original), oldSha = text(old, "jarSha"); Path archive = root.resolve("archives/GameServer." + oldSha + ".jar");
		if (!archive.equals(archive.toRealPath()) || !NeuralPolicy.sha256(Files.readAllBytes(archive)).equals(oldSha)) { throw new IllegalArgumentException("008 original native JAR archive missing"); }
		var payloadExceptions = new ArrayList<String>();
		try (var previous = new java.util.zip.ZipFile(archive.toFile()); var current = new java.util.zip.ZipFile(root.resolve("game/libs/GameServer.jar").toFile()))
		{
			try (var a = previous.getInputStream(previous.getEntry("META-INF/MANIFEST.MF")); var b = current.getInputStream(current.getEntry("META-INF/MANIFEST.MF")))
			{
				var before = new java.util.jar.Manifest(a); var after = new java.util.jar.Manifest(b); var stamp = new java.util.jar.Attributes.Name("Build-Date");
				before.getMainAttributes().remove(stamp); after.getMainAttributes().remove(stamp);
				if (!before.equals(after)) { throw new IllegalArgumentException("008 native JAR manifest changed beyond Build-Date"); }
			}
			var entries = previous.stream().filter(e -> !e.isDirectory() && !e.getName().equals("META-INF/MANIFEST.MF")).map(java.util.zip.ZipEntry::getName).sorted().toList();
			if (!entries.equals(current.stream().filter(e -> !e.isDirectory() && !e.getName().equals("META-INF/MANIFEST.MF")).map(java.util.zip.ZipEntry::getName).sorted().toList())) { throw new IllegalArgumentException("008 production JAR entry set changed"); }
			for (String name : entries) { try (var a = previous.getInputStream(previous.getEntry(name)); var b = current.getInputStream(current.getEntry(name)))
			{
				byte[] before = a.readAllBytes(), after = b.readAllBytes(); if (Arrays.equals(before, after)) { continue; }
				String changed = NeuralPolicy.sha256(after);
				if (!name.equals("org/l2jmobius/gameserver/phantoms/learning/TacticalStoreV3.class") || !changed.equals("fadac44dad780a9fe7493e56ca06fcbeefb29cd67e3f3be1e208157aa0b32d14")) { throw new IllegalArgumentException("008 production JAR payload changed: " + name); }
				payloadExceptions.add("{\"entry\":" + q(name) + ",\"oldSha\":" + q(NeuralPolicy.sha256(before)) + ",\"newSha\":" + q(changed) + ",\"scope\":\"VERSIONED008_COLD_AUDIT_READER_ONLY\"}");
			} }
		}
		String revision = protocol.substring(0, protocol.length() - 1) + ",\"originalProtocolSha\":" + q(hash(old)) + ",\"productionPayloadByteIdentical\":" + payloadExceptions.isEmpty() + ",\"payloadExceptions\":[" + String.join(",", payloadExceptions) + "],\"reason\":" + q(payloadExceptions.isEmpty() ? "TEST_PRE_CUT_OFFSET_GEOMETRY_FIX_WITH_FAILED_SLOT_RETAINED" : "READ_ONLY008_COLD_LOSS_UNITS_FIX_NO_WEIGHTS_OR_LABEL_CHANGES") + "}";
		immutable(directory(root).resolve("protocol-build-" + jar + ".json"), revision);
	}
	static void run(Path root, Path attempt, TrainingConfig c) throws Exception
	{
		if (!enabled(c) || !c.text("exploration008Phase", "").equals("COLLECT")) { throw new IllegalArgumentException("008 collection phase required"); }
		PhantomTrainingActors.settings(root); slots(root);
		var store = new TacticalStoreV3(root); var model = store.candidate(11);
		if (model == null || !model.sha().equals(ActionAdvantage008.TEACHER_SHA) || !store.optimizationParent().sha().equals(ActionAdvantage008.PARENT_ADAM_SHA)) { throw new IllegalArgumentException("008 collection parent changed"); }
		var v1 = new LearningStore(DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_LOGIN, DatabaseConfig.DATABASE_PASSWORD, root.resolve("game")).accepted(1);
		if (v1 == null || !v1.model().sha256().equals(PhantomLearning005Evaluation.approved(Mode.SOLO_DAMAGE_V1).sha())) { throw new IllegalStateException("Stock observer unavailable"); }
		for (int warm = 0; warm < 512; warm++) { model.model().score(new float[128], new float[64]); }
		long start = number(Files.readString(directory(root).resolve("budget.json")), "collectionStarts"); int segment = start == 1 ? 12 : start == 2 ? 36 : 48, attempted = 0; boolean timeLimit = false;
		try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			for (var slot : slots(root).stream().filter(s -> !s.split().equals("EVAL")).toList())
			{
				if (attempted >= segment || requested(attempt)) { break; }
				Path work = directory(root).resolve(String.format("slot-%03d", slot.ordinal())); if (Files.exists(work.resolve("reserved.json"))) { continue; }
				if (collectionLimit(root, c)) { timeLimit = true; break; }
				String id = hash(ActionAdvantage008.CAMPAIGN + "/" + slot.ordinal() + "/" + attempt.getFileName());
				immutable(work.resolve("reserved.json"), "{\"slot\":" + slot.ordinal() + ",\"episode\":" + q(id) + ",\"nonce\":" + q(attempt.getFileName().toString()) + ",\"split\":" + q(slot.split()) + ",\"block\":" + q(slot.block()) + ",\"classId\":" + slot.direction() + ",\"experimental\":" + slot.experimental() + "}");
				attempted++; episode(root, attempt, c, authority, store, model, v1.model(), work, id, slot);
			}
		}
		long reserved; try (var paths = Files.list(directory(root))) { reserved = paths.filter(p -> p.getFileName().toString().matches("slot-\\d{3}")).filter(p -> Files.exists(p.resolve("reserved.json"))).count(); }
		if (reserved >= 48 || timeLimit) { immutable(directory(root).resolve("native.complete"), timeLimit ? "COLLECTION_TIME_LIMIT" : "FIXED_48_SLOTS_CONSUMED"); }
		immutable(attempt.resolve("campaign008.segment.json"), "{\"attempted\":" + attempted + ",\"reservedTotal\":" + reserved + ",\"timeLimit\":" + timeLimit + ",\"fit\":false}");
		immutable(attempt.resolve("batch.complete"), "schemaVersion=8\ncollectionOnly=true");
	}
	private static int[] position(Path root, PhantomBackgroundSuite.ProductionAuthorityFixture authority, PhantomTrainingScenarios.Scene scene) throws Exception
	{
		Path path = directory(root).resolve("positions").resolve("source-" + hash(scene.source()) + ".xyz");
		if (!Files.exists(path))
		{
			var geo = GeoEngine.getInstance(); var planner = new org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner(authority.knowledge(), authority.topology(), authority.authority());
			var source = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", scene.source());
			// Reuse only geometry-valid legacy positions. No completion, utility or model score is consulted.
			for (var candidate : slots(root).stream().map(PhantomLearning008Scenarios.Slot::scene).filter(s -> s.source().equals(scene.source())).sorted(Comparator.comparing(PhantomTrainingScenarios.Scene::id)).toList())
			{
				Path legacy = directory(root).resolve("positions").resolve(candidate.id() + ".xyz"); if (!Files.exists(legacy)) { continue; }
				int[] p = Arrays.stream(Files.readString(legacy).trim().split(",")).mapToInt(Integer::parseInt).toArray();
				if (openingGeometry(scene, p[0], p[1], p[2]) && planner.partyNativeTarget(source, scene.npc(), new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(p[0], p[1], p[2], 0))) { immutable(path, p[0] + "," + p[1] + "," + p[2]); break; }
			}
			if (!Files.exists(path))
			{
			var base = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
				.filter(m -> m.getId() == scene.npc() && !m.isAlikeDead() && Math.hypot(m.getX() - scene.x(), m.getY() - scene.y()) < 1600
					&& planner.partyNativeTarget(source, scene.npc(), new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(m.getX(), m.getY(), m.getZ(), m.getInstanceId()))
					&& geo.canSeeTarget(scene.x(), scene.y(), scene.z(), 0, m.getX(), m.getY(), m.getZ(), m.getInstanceId()) && geo.canMoveToTarget(scene.x(), scene.y(), scene.z(), m.getX(), m.getY(), m.getZ(), 0) && openingGeometry(scene, m.getX(), m.getY(), m.getZ()))
				.min(Comparator.comparingInt(Monster::getObjectId)).orElseThrow(() -> new IllegalStateException("008_PRE_CUT_CANONICAL_GEOMETRY"));
			immutable(path, base.getX() + "," + base.getY() + "," + base.getZ());
			}
		}
		int[] pinned = Arrays.stream(Files.readString(path).trim().split(",")).mapToInt(Integer::parseInt).toArray(); if (!openingGeometry(scene, pinned[0], pinned[1], pinned[2])) { throw new IllegalStateException("008_PRE_CUT_PINNED_SOURCE_GEOMETRY_CHANGED"); } return pinned;
	}
	private static boolean openingGeometry(PhantomTrainingScenarios.Scene scene, int x, int y, int z)
	{
		var geo = GeoEngine.getInstance(); if (!geo.hasGeo(x, y) || geo.getHeight(x, y, z) != z) { return false; }
		for (int offset : new int[] {180, 160}) { if (!geo.hasGeo(x + offset, y) || geo.getHeight(x + offset, y, z) != z || !geo.canMoveToTarget(scene.x(), scene.y(), scene.z(), x + offset, y, z, 0)
			|| !geo.canMoveToTarget(x + 180, y, z, x + offset, y, z, 0) || !geo.canMoveToTarget(x + offset, y, z, x, y, z, 0) || !geo.canSeeTarget(x + offset, y, z, 0, x, y, z, 0)) { return false; } }
		return true;
	}
	private static void episode(Path root, Path attempt, TrainingConfig c, PhantomBackgroundSuite.ProductionAuthorityFixture authority, TacticalStoreV3 store, TacticalStoreV3.Checkpoint model, NeuralPolicy v1,
		Path work, String id, PhantomLearning008Scenarios.Slot slot) throws Exception
	{
		var scene = slot.scene(); String split = slot.split(), context = "learning008|" + split + "|SOLO|class=" + scene.classId() + "|scene=" + scene.id() + "|episode=" + id;
		var receipts = new ConcurrentLinkedQueue<TacticalReceiptV3>(); var failures = new ConcurrentLinkedQueue<TacticalFailureV3>(); var selections = new ConcurrentLinkedQueue<TrainExplorationV1.Selection>();
		var timings = new ConcurrentHashMap<String, TerminalRewardReceiptV1.NativeTiming>(); TerminalRewardReceiptV1.nativeTimingObserver(t -> { if (timings.size() >= 2048) { throw new IllegalStateException("008 native effect bound"); } timings.put(t.eventKey(), t); });
		var policy = LearningPolicy.evaluation(LearningPolicy.Mode.SHADOW, 1, v1, 4, hash(ActionAdvantage008.CAMPAIGN + "/" + id));
		var tactical = new TacticalPolicyV3(TacticalPolicyV3.Mode.SHADOW, model.id(), model.model(), 20, 16, 2000, r -> receipts.add(TacticalReceiptV3.fromHeal(r)));
		tactical.damageObserver(receipts::add); tactical.failureObserver(failures::add);
		Monster target = null; TacticalEpisodeV3 episode = null; boolean cut = false; String exclusion = "", physical = ""; long preparation = System.nanoTime();
		try (var runtime = new PhantomLearning001ShadowNative.Runtime(authority, root.resolve("game"), policy, attempt.resolve("stop.request"), true, 5, tactical, true))
		{
			java.util.function.Supplier<TacticalEpisodeV3> terminal = null;
			try
			{
				var actors = PhantomLearning003Damage.prepare(authority, scene, scene, work, c); runtime.prepareTraining(scene, actors, false, c.preparationSeconds(), () -> requested(attempt));
				int[] xyz = position(root, authority, scene);
				for (int candidate = 0; candidate < 4; candidate++)
				{
					var spawn = new Spawn(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(scene.npc())); spawn.setXYZ(xyz[0], xyz[1], xyz[2]); var npc = (Monster) spawn.doSpawn(false); spawn.stopRespawn();
					boolean admitted = !npc.isChampion() && !npc.isRaid() && !npc.isAggressive() && !npc.isAlikeDead() && npc.getCurrentHp() == npc.getMaxHp();
					Files.writeString(work.resolve("admissions.jsonl"), "{\"candidate\":" + candidate + ",\"objectId\":" + npc.getObjectId() + ",\"champion\":" + npc.isChampion() + ",\"admitted\":" + admitted + "}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					if (admitted) { target = npc; break; } npc.deleteMe();
				}
				if (target == null) { throw new IllegalStateException("008_NORMAL_TARGET_ADMISSIONS_EXHAUSTED"); }
				target.teleToLocation(xyz[0], xyz[1], xyz[2], 0, false); target.setHeading(0); runtime.prepareOutcome006(target, slot.variant());
				var player = runtime.evaluationPlayers().getFirst();
				// Declared input variation only before CUT; native regeneration remains active.
				player.setCurrentHp(player.getMaxHp() * (slot.variant() == 0 ? 1 : .95)); player.setCurrentMp(player.getMaxMp() * (slot.variant() == 0 ? 1 : .90));
				int distance = slot.variant() == 0 ? 180 : 160; var geo = GeoEngine.getInstance();
				if (!geo.canMoveToTarget(player.getX(), player.getY(), player.getZ(), target.getX() + distance, target.getY(), target.getZ(), player.getInstanceId()) || !geo.canMoveToTarget(target.getX() + distance, target.getY(), target.getZ(), target.getX(), target.getY(), target.getZ(), player.getInstanceId())) { throw new IllegalStateException("008_PRE_CUT_DECLARED_OFFSET_GEO"); }
				player.teleToLocation(target.getX() + distance, target.getY(), target.getZ(), 0, false); player.setHeading(0); if (player.hasHeadlessOutboundSession() && player.isTeleporting()) { player.onTeleported(); }
				admitDamage(player, target, scene);
				var facts = PhantomLearning005Native.facts(runtime.evaluationPlayers(), target, scene, c); physical = hash(json(facts));
				Path matched = directory(root).resolve("blocks").resolve(slot.block() + ".physical"); if (split.equals("TRAIN")) { if (Files.exists(matched) && !Files.readString(matched).trim().equals(physical)) { throw new IllegalStateException("008_PRE_CUT_MATCHED_PHYSICAL_FACTS_DIFFER"); } immutable(matched, physical); }
				immutable(work.resolve("precut.json"), "{\"episode\":" + q(id) + ",\"physicalSha\":" + q(physical) + ",\"facts\":" + json(facts) + ",\"currentNativeOwner\":true,\"minimumReadyCandidates\":2}");
				var window = TrainExplorationV1.owned(root, id, slot.block(), physical, split, slot.experimental(), LegalActionV3.Family.DAMAGE, model.id(), model.sha(), selections::add);
				var identity = target.getNativeEvidenceTarget(); String before = "{\"comparisonCellSha\":" + q(physical) + ",\"comparisonFacts\":" + json(facts) + ",\"runtime\":" + runtime.outcomeBeforeFacts() + ",\"collectionPolicy\":\"SHADOW\",\"campaign\":" + q(ActionAdvantage008.CAMPAIGN) + "}";
				long generation = number(Files.readString(root.resolve("director.json")), "generation"); receipts.clear(); failures.clear(); timings.clear(); cut = true;
				terminal = runtime.runLearning006Encounter(id, attempt.getFileName().toString(), c.episodeSeconds(), () -> requested(attempt), () -> List.copyOf(receipts), stamp ->
				{ try { store.beginTerminalEpisode(id, attempt.getFileName().toString(), "SOLO", before, generation, identity.instanceId(), identity.objectId(), identity.spawnGeneration(), stamp); tactical.trainExploration(window); } catch (Exception failure) { throw new IllegalStateException("008 durable CUT failed", failure); } });
			}
			catch (IllegalStateException unavailable)
			{
				exclusion = unavailable.getMessage();
				if (exclusion == null || exclusion.equals("008 durable CUT failed") || !cut && !exclusion.startsWith("008_") && !exclusion.startsWith("EVAL005_") && !exclusion.startsWith("OUTCOME006_") && !exclusion.startsWith("LEARNING001_OUTCOME006_") && !exclusion.startsWith("LEARNING001_TRAIN_")) { immutable(work.resolve("failure.json"), "{\"afterCut\":" + cut + ",\"exception\":" + q(unavailable.getClass().getSimpleName()) + "}"); throw unavailable; }
			}
			finally { tactical.trainExploration(null); if (terminal == null) { runtime.settleTraining(); } else { episode = runtime.settleLearning004(terminal).episode(); } runtime.releaseOutcome006(); }
			if (episode != null)
			{
				for (var receipt : receipts) { store.record(receipt, split, context); } for (var failure : failures) { store.recordFailure(failure, split, context, id); } store.settleEpisode(episode); immutable(work.resolve("settlement.json"), episode.json());
				if (receipts.isEmpty() || !runtime.outcomeEncounterAdmitted() || !Set.of("NATIVE_KILL", "NATIVE_ACTOR_DIED", "TIMEOUT").contains(episode.reason())) { exclusion = "NO_ELIGIBLE_TERMINAL_ACTIONS_OR_ENCOUNTER"; }
				else
				{
					var identity = target.getNativeEvidenceTarget(); var boundary = new TerminalRewardReceiptV1.Boundary(hash(Files.readString(root.resolve("owner.txt"))), attempt.getFileName().toString(), number(Files.readString(root.resolve("director.json")), "generation"), identity.instanceId(), identity.objectId(), identity.spawnGeneration(), runtime.outcomeCutNanos(), runtime.outcomeTerminalNanos(), episode.settledNanos(), true);
					var eligible = new ArrayList<TerminalRewardReceiptV1.TimedAction>();
					for (var receipt : receipts)
					{
						try { var timing = timings.get(receipt.eventKey()); if (timing == null) { throw new IllegalArgumentException("Missing exact native timing"); } var action = timing.bind(new TacticalStoreV3.Row(split, context, receipt)); TerminalRewardReceiptV1.create(boundary, episode, List.of(action), split, ActionAdvantage008.DATASET, store.terminalConfig()); eligible.add(action); }
						catch (IllegalArgumentException invalid) { Files.writeString(work.resolve("excluded-actions.jsonl"), "{\"eventKey\":" + q(receipt.eventKey()) + ",\"reason\":" + q(invalid.getMessage()) + "}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
					}
					if (!eligible.isEmpty()) { var reward = TerminalRewardReceiptV1.create(boundary, episode, eligible, split, ActionAdvantage008.DATASET, store.terminalConfig()); store.stageTerminal(reward); immutable(work.resolve("terminal.bin"), reward.bytes()); immutable(work.resolve("terminal.json"), reward.json()); for (var action : eligible) { immutable(work.resolve(action.row().eventKey() + ".native.json"), PhantomLearning005Native.nativeReceiptJson(action.row().receipt())); } }
				}
			}
			for (var selection : selections) { immutable(directory(root).resolve("samples").resolve(id + ".selection.bin"), selection.bytes()); immutable(work.resolve("selection.json"), "{\"sha\":" + q(NeuralPolicy.sha256(selection.bytes())) + ",\"propensity\":" + selection.propensity() + ",\"draw\":" + selection.draw() + ",\"reason\":" + q(selection.reason()) + ",\"fallback\":" + q(selection.fallback()) + ",\"stock\":" + q(selection.stock().toString()) + ",\"selected\":" + q(selection.selected().toString()) + ",\"offered\":" + q(selection.offered().toString()) + "}"); }
			immutable(work.resolve("result.json"), "{\"slot\":" + slot.ordinal() + ",\"episode\":" + q(id) + ",\"classId\":" + scene.classId() + ",\"split\":" + q(split) + ",\"experimental\":" + slot.experimental() + ",\"physicalSha\":" + q(physical) + ",\"afterCut\":" + cut + ",\"outcome\":" + (episode == null ? "null" : q(episode.outcome())) + ",\"exclusion\":" + q(exclusion) + ",\"opportunities\":" + selections.size() + ",\"paidRawEffects\":" + receipts.size() + ",\"totalMillis\":" + (System.nanoTime() - preparation) / 1e6 + ",\"policyDiagnostics\":" + tactical.diagnostics() + ",\"policyTiming\":" + q(tactical.timing()) + "}");
			System.out.println("LEARNING008_EPISODE slot=" + slot.ordinal() + " class=" + scene.classId() + " split=" + split + " B=" + slot.experimental() + " outcome=" + (episode == null ? "SKIP" : episode.outcome()) + " decisions=" + selections.size() + " effects=" + receipts.size() + " exclusion=" + exclusion);
			if (!cut && (exclusion.startsWith("008_PRE_CUT_NATIVE_OWNER_OR_TWO_READY_CANDIDATES") || exclusion.startsWith("008_PRE_CUT_NATIVE_COLD_ADMISSION"))) { throw new IllegalStateException("008_CLASS_ADMISSION_BLOCKED " + exclusion); }
		}
		finally { if (target != null) { target.deleteMe(); } TerminalRewardReceiptV1.nativeTimingObserver(_ -> { }); }
	}
	static void admitDamage(Player player, Monster target, PhantomTrainingScenarios.Scene scene)
	{
		var owner = player.getNativeWorkOwner(); var geo = GeoEngine.getInstance();
		var diagnostics = new ArrayList<String>();
		for (int id : scene.skills()) { var skill = player.getKnownSkill(id); diagnostics.add(id + ":known=" + (skill != null) + (skill == null ? "" : ":eligible=" + PhantomLearning001ActorFixture.eligible(skill) + ":ready=" + TacticalTimingV3.observe(player, skill).readyNow() + ":disabled=" + player.isSkillDisabled(skill) + ":cast=" + player.checkDoCastConditions(skill) + ":condition=" + skill.checkCondition(player, target, false) + ":range=" + player.getMagicalAttackRange(skill) + ":mp=" + (player.getStat().getMpConsume(skill) + player.getStat().getMpInitialConsume(skill)))); }
		long ready = scene.skills().stream().map(player::getKnownSkill).filter(Objects::nonNull).filter(skill -> PhantomLearning001ActorFixture.eligible(skill) && TacticalTimingV3.observe(player, skill).readyNow()
			&& !player.isSkillDisabled(skill) && player.checkDoCastConditions(skill) && skill.checkCondition(player, target, false) && player.calculateDistance3D(target) <= player.getMagicalAttackRange(skill)
			&& player.getCurrentMp() >= player.getStat().getMpConsume(skill) + player.getStat().getMpInitialConsume(skill)).count();
		if (owner == null || !owner.isCurrent() || player.getActiveClass() != scene.classId() || ready < 2) { throw new IllegalStateException("008_PRE_CUT_NATIVE_OWNER_OR_TWO_READY_CANDIDATES class=" + scene.classId() + " ready=" + ready + " owner=" + (owner != null && owner.isCurrent()) + " mp=" + player.getCurrentMp() + " skills=" + diagnostics); }
		if (!geo.canSeeTarget(player, target) || !geo.canMoveToTarget(player, target) || !PhantomLearning001ActorFixture.soloOpeningTargets(player).contains(target)) { throw new IllegalStateException("008_PRE_CUT_GEOMETRY_OR_NATIVE_OPENING class=" + scene.classId()); }
		if (!PhantomLearning001ActorFixture.coldSoloOpeningTargets(player).contains(target)) { PhantomLearning001ActorFixture.inspectColdSoloOpening(player); throw new IllegalStateException("008_PRE_CUT_NATIVE_COLD_ADMISSION class=" + scene.classId()); }
	}
	static void runEvaluation(Path root, Path attempt, TrainingConfig c) throws Exception
	{
		if (!PhantomLearning005Native.generalizationEvaluation(c)) { throw new IllegalArgumentException("008 EVAL authority required"); }
		PhantomTrainingActors.settings(root); var model = PhantomLearning005Native.verifiedDraft(root, c); slots(root);
		var v1 = new LearningStore(DatabaseConfig.DATABASE_URL, DatabaseConfig.DATABASE_LOGIN, DatabaseConfig.DATABASE_PASSWORD, root.resolve("game")).accepted(1);
		if (v1 == null || !v1.model().sha256().equals(PhantomLearning005Evaluation.approved(Mode.SOLO_DAMAGE_V1).sha())) { throw new IllegalStateException("Stock observer unavailable"); }
		boolean control = c.text("exploration008Phase", "").equals("CONTROL"); String before = PhantomLearning005Native.trainingState(root, true), jar = NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar")));
		for (int warm = 0; warm < 2048; warm++) { model.model().score(new float[128], new float[64]); }
		PhantomLearning005Native.residualDeadline = true;
		try (var ledger = PhantomLearning005Native.ledger(root, c); var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			for (var slot : slots(root).stream().filter(s -> s.split().equals("EVAL") && (!control || s.variant() == 0 && (s.direction() == 94 || s.direction() == 105))).toList())
			{
				if (requested(attempt) || evaluationDeadline(root)) { break; }
				var scene = slot.scene(); String pair = slot.block(); var mode = slot.direction() == 105 ? Mode.PARTY_HEAL_V3 : Mode.SOLO_DAMAGE_V1;
				int[] xyz = position(root, authority, scene); Side first = slot.variant() == 0 ? Side.STOCK : Side.LEARNED;
				for (int order = 0; order < 2 && !requested(attempt) && !evaluationDeadline(root); order++)
				{
					Side side = order == 0 ? first : first == Side.STOCK ? Side.LEARNED : Side.STOCK;
					if (ledger.arms().stream().anyMatch(a -> a.pair().equals(pair) && a.side() == side)) { continue; }
					PhantomLearning005Native.arm(root, attempt, c, authority, ledger, v1.model(), model.model(), scene, pair, mode, side, order, slot.variant(), xyz[0], xyz[1], xyz[2], jar);
				}
			}
		}
		finally { PhantomLearning005Native.residualDeadline = false; }
		String after = PhantomLearning005Native.trainingState(root, true); if (!before.equals(after)) { throw new IllegalStateException("008 EVAL changed optimizer or training credits"); }
		immutable(attempt.resolve("evaluation008.training-unchanged.json"), "{\"before\":" + q(before) + ",\"after\":" + q(after) + ",\"equal\":true}");
		immutable(attempt.resolve("batch.complete"), "schemaVersion=8\nevaluationOnly=true"); report(root, c);
	}
	static void report(Path root, TrainingConfig c) throws Exception
	{
		List<Arm> arms; List<PhantomLearning005Evaluation.Pair> pairs; try (var ledger = PhantomLearning005Native.ledger(root, c)) { arms = ledger.arms(); pairs = ledger.pairs(); }
		boolean control = c.text("exploration008Phase", "").equals("CONTROL"); int expected = control ? 4 : 16;
		var summaries = new ArrayList<String>(); var report = new StringBuilder("# PHANTOM-LEARNING-008 — независимая оценка\n\nDRAFT не повышается автоматически. TRAIN exploration выключен; trainingCredit=false. При n=2 вывод о статистическом преимуществе недопустим. UNKNOWN не заменяется нулём.\n\n");
		for (int classId : control ? List.of(94, 105) : List.of(94, 103, 110, 105))
		{
			String prefix = "class" + classId + "-"; var selectedArms = arms.stream().filter(a -> a.pair().startsWith(prefix)).toList(); var selectedPairs = pairs.stream().filter(p -> p.id().startsWith(prefix)).toList(); var s = PhantomLearning005Evaluation.summarize(selectedPairs, c.seed());
			summaries.add("{\"classId\":" + classId + ",\"arms\":" + selectedArms.size() + ",\"completedMatchedPairs\":" + s.n() + ",\"incompletePairs\":" + s.incomplete() + ",\"unmatched\":" + s.unmatched() + ",\"divergences\":" + s.divergences() + ",\"verdict\":" + q(s.label()) + ",\"medianDeltaMillis\":" + s.median() + ",\"ciLow\":" + s.ciLow() + ",\"ciHigh\":" + s.ciHigh() + "}");
			report.append("## Class ").append(classId).append("\n\nВердикт: ").append(s.label()).append("; независимых matched completed pairs n=").append(s.n()).append("; divergences=").append(s.divergences()).append(".\n\n");
			for (var p : selectedPairs) { report.append("- ").append(p.id()).append(": ").append(p.status()).append('/').append(p.reason()).append("; A=").append(p.stock().outcome()).append('/').append(p.stock().elapsedMillis()).append("мс; B=").append(p.learned().outcome()).append('/').append(p.learned().elapsedMillis()).append("мс; Δ=").append(p.deltas()).append("; divergences=").append(p.divergences()).append(".\n"); }
			for (var a : selectedArms) { report.append("- Arm ").append(a.pair()).append('/').append(a.side()).append(": ").append(a.outcome()).append('/').append(a.reason()).append("; metrics=").append(a.metrics()).append("; receipt=").append(a.id()).append(".json.\n"); }
		}
		String summary = "{\"schemaVersion\":8,\"authority\":" + PhantomLearning005Native.draftAuthority(c).json() + ",\"phase\":" + q(c.text("exploration008Phase", "")) + ",\"exploration\":false,\"trainingCredit\":false,\"plannedArms\":" + expected + ",\"attemptedArms\":" + arms.size() + ",\"classes\":[" + String.join(",", summaries) + "],\"smallSampleSuperiorityClaim\":false}";
		Path dir = PhantomLearning005Native.directory(root, c); Files.createDirectories(dir); Files.writeString(dir.resolve("summary.json"), summary + "\n"); Files.writeString(dir.resolve("report.md"), report, StandardCharsets.UTF_8);
		if (arms.size() == expected) { immutable(dir.resolve("native.complete"), "FIXED_008_EVAL_DENOMINATOR_CONSUMED"); }
		System.out.println("EVAL008_REPORT " + summary);
	}
	private static boolean requested(Path attempt) { return Files.exists(attempt.resolve("stop.request")) || Files.exists(attempt.resolve("pause.request")); }
	public static void main(String[] args)
	{
		int exit = 2;
		try
		{
			if (args.length != 2 || !args[0].equals("prepare")) { throw new IllegalArgumentException("Expected prepare and owned TEST root"); }
			Path root = PhantomTrainingActors.physicalRoot(Path.of(args[1])); var c = TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini"));
			// The stopped guard must precede our own headless Hikari connections.
			new TacticalStoreV3(root).requireStopped();
			ConfigLoader.init(); DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString()); prepare(root, c); exit = 0;
		}
		catch (Exception failure) { System.err.println("LEARNING008_PREPARE_FAILED " + failure.getClass().getSimpleName() + " " + (failure instanceof java.sql.SQLException sql ? sql.getSQLState() : failure.getMessage())); }
		finally { DatabaseFactory.close(); }
		System.exit(exit);
	}
}

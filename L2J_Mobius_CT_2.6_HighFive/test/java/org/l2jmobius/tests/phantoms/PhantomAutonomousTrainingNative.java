/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSkillSafety;
import org.l2jmobius.gameserver.phantoms.learning.AutonomousTrainingV1;
import org.l2jmobius.gameserver.phantoms.learning.LegalActionV3;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSkillInventory;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TrainingConfig;
import org.l2jmobius.gameserver.phantoms.learning.TrainingStore;
import org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalFailureV3;
import org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3;
import org.l2jmobius.gameserver.phantoms.learning.TerminalRewardReceiptV1;
import org.l2jmobius.gameserver.phantoms.learning.TrainExplorationV1;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.LearningStore;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;

/** TEST adapter called by the existing Director. It does not own processes or run a trainer. */
public final class PhantomAutonomousTrainingNative
{
	private record Source(PhantomTrainingActors.SavedActor image, PhantomSkillInventory.Snapshot inventory,
		AutonomousTrainingV1.ActorSeed seed, String grade, int enchant) { }
	private record Cell(String key, AutonomousTrainingV1.Proposal proposal, List<Source> roster, PhantomTrainingScenarios.Scene scene, int x, int y, int z)
	{
		boolean party() { return roster.size() > 1; }
		int learnerIndex() { for (int i = 0; i < roster.size(); i++) { if (roster.get(i).seed().equals(proposal.actor())) { return i; } } throw new IllegalStateException("AUTONOMOUS_ROSTER_LEARNER_MISSING"); }
	}
	private record EvaluationArm(String physical, List<PhantomLearning005Evaluation.Incarnation> actors,
		PhantomLearning005Evaluation.Incarnation target, boolean completed, List<String> events) { }
	private static String incarnationText(PhantomLearning005Evaluation.Incarnation value)
	{ return value.objectId() + ":" + value.epoch() + ":" + value.instance(); }
	private static PhantomLearning005Evaluation.Incarnation decodeIncarnation(String text)
	{ final var fields = text.split(":", -1); if (fields.length != 3) { throw new IllegalStateException("AUTONOMOUS_EVAL_ARM_CODEC"); } return new PhantomLearning005Evaluation.Incarnation(Integer.parseInt(fields[0]), Long.parseLong(fields[1]), Integer.parseInt(fields[2])); }
	private static String encodeArm(EvaluationArm arm)
	{
		return "EVAL_ARM_V1\t" + arm.physical() + "\t" + arm.completed() + "\t" + (arm.target() == null ? "" : incarnationText(arm.target())) + "\n"
			+ arm.actors().stream().map(PhantomAutonomousTrainingNative::incarnationText).collect(java.util.stream.Collectors.joining(",")) + "\n" + String.join(",", arm.events()) + "\n";
	}
	private static EvaluationArm decodeArm(String body)
	{
		final var lines = body.split("\n", -1); if (lines.length != 4 || !lines[3].isEmpty()) { throw new IllegalStateException("AUTONOMOUS_EVAL_ARM_CODEC"); }
		final var header = lines[0].split("\t", -1);
		if (header.length != 4 || !header[0].equals("EVAL_ARM_V1") || !Set.of("true", "false").contains(header[2]) || !header[1].matches("[A-Za-z0-9_-]{0,128}")) { throw new IllegalStateException("AUTONOMOUS_EVAL_ARM_CODEC"); }
		final var actors = lines[1].isEmpty() ? List.<PhantomLearning005Evaluation.Incarnation>of() : java.util.Arrays.stream(lines[1].split(",")).map(PhantomAutonomousTrainingNative::decodeIncarnation).toList();
		final var events = lines[2].isEmpty() ? List.<String>of() : List.of(lines[2].split(","));
		if (actors.size() > 9 || actors.stream().map(PhantomLearning005Evaluation.Incarnation::objectId).distinct().count() != actors.size() || events.size() > 2048 || events.stream().distinct().count() != events.size() || events.stream().anyMatch(event -> !event.matches("[A-Za-z0-9_-]{1,128}"))) { throw new IllegalStateException("AUTONOMOUS_EVAL_ARM_CODEC"); }
		final var arm = new EvaluationArm(header[1], actors, header[3].isEmpty() ? null : decodeIncarnation(header[3]), Boolean.parseBoolean(header[2]), events);
		if (!encodeArm(arm).equals(body)) { throw new IllegalStateException("AUTONOMOUS_EVAL_ARM_CODEC"); } return arm;
	}
	private static String pairStatus(List<EvaluationArm> arms)
	{
		if (arms.size() != 2) { return "INCOMPLETE_ARMS"; }
		final var a = arms.getFirst(); final var b = arms.getLast();
		if (a.physical().isEmpty() || !a.physical().equals(b.physical())) { return "PRE_CUT_FACTS_DIFFER"; }
		if (a.target() == null || b.target() == null || a.actors().isEmpty() || b.actors().isEmpty() || a.target().equals(b.target()) || a.actors().stream().anyMatch(x -> b.actors().stream().anyMatch(y -> x.objectId() == y.objectId()))) { return "REUSED_OR_MISSING_INCARNATION"; }
		if (a.events().stream().anyMatch(b.events()::contains)) { return "DUPLICATED_NATIVE_EVENT"; }
		return a.completed() && b.completed() && !a.events().isEmpty() && !b.events().isEmpty() ? "MATCHED_COMPLETED" : "MATCHED_INCOMPLETE";
	}
	private static long matchingRiskProfile(long first, int uncovered, int maximum)
	{
		if (first < 1 || first > maximum) { throw new IllegalStateException("AUTONOMOUS_EVAL_RISK_PROFILE_UNAVAILABLE"); }
		final var risk = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.DEFAULT_TACTICAL_RISK;
		for (long candidate = first; candidate <= maximum && candidate - first < 512; candidate++)
		{ if (risk.limitPercent(candidate, false) == uncovered) { return candidate; } }
		throw new IllegalStateException("AUTONOMOUS_EVAL_RISK_PROFILE_UNAVAILABLE");
	}
	/** Same read-only native CUT/terminal window as the established005 evaluator. */
	private static boolean evaluationTiming(TerminalRewardReceiptV1.NativeTiming time, long cut, long terminal, boolean linkedLethal)
	{
		return time != null && cut > 0 && terminal > cut && time.hpStart() >= cut && time.hpStart() <= terminal
			&& (time.hpEnd() <= terminal || linkedLethal) && time.mpStart() >= cut && time.mpEnd() <= terminal;
	}
	private static Path cohort(Path root, TrainingConfig config) throws Exception
	{
		final Path directory = root.resolve("autonomous/v1/campaigns").resolve(config.text("autonomous009Campaign", "")).resolve("cohorts").resolve(config.text("outcome006DatasetId", ""));
		requirePhysicalDirectory(root, directory); return directory;
	}
	private static void immutable(Path path, String body) throws Exception { immutable(path, body.getBytes(StandardCharsets.UTF_8)); }
	private static void immutable(Path path, byte[] body) throws Exception
	{
		if (Files.exists(path))
		{ if (!path.equals(path.toRealPath()) || !java.util.Arrays.equals(Files.readAllBytes(path), body)) { throw new IllegalStateException("AUTONOMOUS_IMMUTABLE_CHANGED"); } }
		else { Files.write(path, body, StandardOpenOption.CREATE_NEW); }
	}
	private static boolean requested(Path attempt) { return Files.exists(attempt.resolve("stop.request")) || Files.exists(attempt.resolve("pause.request")); }
	private static List<AutonomousTrainingV1.ActionPool> pools(Player player, PhantomSkillInventory.Snapshot inventory)
	{
		final var result = new ArrayList<AutonomousTrainingV1.ActionPool>();
		for (var mode : PhantomCombatMode.values())
		{
			final var refs = inventory.abilities().stream().filter(a -> a.category() == PhantomSkillInventory.Category.SINGLE_TARGET_DAMAGE
				&& a.support().contains(PhantomSkillInventory.Support.SUPPORTED_FOR_TRAIN)
				&& PhantomCombatSkillSafety.supports(player.getKnownSkill(a.skill().skillId()), mode, false)).map(PhantomSkillInventory.Ability::skill).toList();
			if (!refs.isEmpty()) { result.add(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, mode, refs)); }
		}
		final var heals = inventory.abilities().stream().filter(a -> a.category() == PhantomSkillInventory.Category.HEAL
			&& a.support().contains(PhantomSkillInventory.Support.SUPPORTED_FOR_TRAIN)).map(PhantomSkillInventory.Ability::skill).toList();
		if (!heals.isEmpty()) { result.add(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.HEAL, null, heals)); }
		return List.copyOf(result);
	}
	/** Canonical acquisition is probed on fresh replicas. The cursor rotates source order between cohorts. */
	private static List<Source> discoverSources(Path root, Path directory, TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority, java.util.function.BooleanSupplier requested) throws Exception
	{
		final var headers = new ArrayList<int[]>();
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT charId,classid FROM characters WHERE account_name='learn001fixture' AND char_name NOT LIKE 'AT%' ORDER BY classid,charId"))
		{ while (rows.next()) { headers.add(new int[] { rows.getInt(1), rows.getInt(2) }); } }
		final long cursor = config.longValue("autonomous009SourceCursor", 0, 0, Long.MAX_VALUE);
		headers.sort(Comparator.comparing(h -> hash(config.seed() + "/" + cursor + "/source/" + h[0])));
		final var attempts = new HashMap<Integer, Integer>(); final var signatures = new HashMap<Integer, Set<String>>(); final var sources = new ArrayList<Source>();
		final var coverage = new ArrayList<String>();
		for (var header : headers)
		{
			PhantomTrainingScenarios.requirePlanningActive(requested);
			final int id = header[0], classId = header[1];
			if (!config.classes().contains(classId) || sources.size() >= 64 || attempts.getOrDefault(classId, 0) >= 16
				|| signatures.getOrDefault(classId, Set.of()).size() >= 2) { continue; }
			attempts.merge(classId, 1, Integer::sum); Player player = null;
			try
			{
				final PhantomTrainingActors.SavedActor image;
				try (var connection = DatabaseFactory.getConnection()) { image = PhantomTrainingActors.readSavedActor(connection, id); }
				image.requireReproducible();
				if (!config.levels().contains(Integer.parseInt(image.field("level")))) { continue; }
				final String signature = image.physicalFingerprint();
				if (signatures.getOrDefault(classId, Set.of()).contains(signature)) { continue; }
				PhantomTrainingScenarios.requirePlanningActive(requested); player = reproduce(config, image);
				final var inventory = PhantomSkillInventory.acquired(player, authority.progression());
				final var seed = new AutonomousTrainingV1.ActorSeed(image.sha(), classId, player.getLevel(), player.getClassIndex(), inventory.abilities().stream().map(PhantomSkillInventory.Ability::skill).toList(), pools(player, inventory));
				final var weapon = player.getActiveWeaponItem(); final var item = player.getActiveWeaponInstance();
				sources.add(new Source(image, inventory, seed, weapon == null ? "NONE" : weapon.getCrystalType().name(), item == null ? 0 : item.getEnchantLevel()));
				signatures.computeIfAbsent(classId, _ -> new java.util.HashSet<>()).add(signature);
				immutable(directory.resolve(image.sha() + ".source.json"), image.json());
				immutable(directory.resolve(image.sha() + ".inventory.json"), inventoryJson(inventory));
				coverage.add("{\"source\":" + id + ",\"classId\":" + classId + ",\"inventorySha\":" + q(inventory.fingerprint()) + ",\"learned\":" + inventory.abilities().size() + ",\"status\":\"ACQUIRED\"}");
			}
			catch (IllegalStateException refused)
			{ PhantomTrainingScenarios.requirePlanningActive(requested); coverage.add("{\"source\":" + id + ",\"classId\":" + classId + ",\"status\":\"REJECTED\",\"reason\":" + q(safeReason(refused)) + "}"); }
			finally { if (player != null) { player.stopAllTasks(); player.deleteMe(); } }
		}
		final var classes = new ArrayList<String>();
		for (int id : PhantomSkillInventory.classIds())
		{ classes.add("{\"classId\":" + id + ",\"sources\":" + sources.stream().filter(s -> s.seed().classId() == id).count() + ",\"status\":" + q(sources.stream().anyMatch(s -> s.seed().classId() == id) ? "NATIVE_ADMISSION_REQUIRED" : "NO_ACQUIRED_SOURCE_IN_BOUNDED_PAGE") + "}"); }
		PhantomTrainingScenarios.requirePlanningActive(requested); immutable(directory.resolve("source-coverage.json"), "{\"classes\":[" + String.join(",", classes) + "],\"sourceCursor\":" + cursor + ",\"records\":[" + String.join(",", coverage) + "]}");
		return List.copyOf(sources);
	}
	private static String safeReason(Exception failure)
	{ final String message = failure.getMessage(); return message != null && message.matches("AUTONOMOUS_[A-Z0-9_]{1,100}") ? message : failure.getClass().getSimpleName(); }
	private static Player reproduce(TrainingConfig config, PhantomTrainingActors.SavedActor image) throws Exception
	{
		// Discovery and planning replicas are durable SQL actors too. The same cap applies before every reproduction.
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*),SUM(CASE WHEN account_name IS NULL OR account_name<>'learn001fixture' OR accesslevel<>0 THEN 1 ELSE 0 END) FROM characters"))
		{
			if (!rows.next() || rows.getLong(1) >= config.integer("maxStoredActors", 4096, 32, 32768) || rows.getLong(2) != 0)
			{ throw new IllegalArgumentException("AUTONOMOUS_ACTOR_CAPACITY_OR_SCOPE"); }
		}
		return PhantomLearning001ActorFixture.createFromSaved(image, "AT" + UUID.randomUUID().toString().replace("-", "").substring(0, 13));
	}
	private static void checkpoint(Path installation) throws Exception
	{
		final var root = PhantomTrainingActors.physicalRoot(installation); new TrainingStore(root).requireStopped(); PhantomTrainingActors.settings(root);
		final var store = new TacticalStoreV3(root); final var parent = store.optimizationParent(); final var model = store.candidate(parent.modelId()); final var accepted = store.latestAccepted();
		if (model == null || accepted == null) { throw new IllegalStateException("AUTONOMOUS_COLD_LINEAGE_REQUIRED"); }
		final String body = "{\"modelVersion\":" + model.id() + ",\"modelSha\":" + q(model.sha()) + ",\"optimizerSha\":" + q(parent.sha()) + ",\"terminalWeightsSha\":" + q(parent.state().model().sha256())
			+ ",\"optimizerUpdates\":" + parent.state().updates() + ",\"completedEpochs\":" + parent.state().completedEpochs() + ",\"acceptedTestVersion\":" + accepted.id() + ",\"acceptedTestSha\":" + q(accepted.sha()) + ",\"productionPromotion\":false}";
		final Path directory = root.resolve("autonomous/v1"); requirePhysicalDirectory(root, directory); final Path temporary = directory.resolve("checkpoint." + UUID.randomUUID() + ".pending");
		Files.writeString(temporary, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		final Path pointer = directory.resolve("cold-checkpoint.json"); if (Files.exists(pointer) && !pointer.equals(pointer.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_CHECKPOINT_PATH"); }
		Files.move(temporary, pointer, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("AUTONOMOUS_COLD_CHECKPOINT " + body);
	}
	private static void evaluateNative(Path root, Path attempt, TrainingConfig config) throws Exception
	{
		final var store = new TacticalStoreV3(root); final var model = store.candidate(config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE));
		if (model == null || !model.sha().equals(config.text("autonomous009ModelSha", ""))) { throw new IllegalStateException("AUTONOMOUS_EVAL_MODEL_CHANGED"); }
		final Path directory = store.autonomousDirectory().resolve("evaluations").resolve(config.text("outcome006DatasetId", "") + "-" + model.id()); requirePhysicalDirectory(root, directory);
		final var settings = PhantomTrainingActors.settings(root); final var v1 = new LearningStore(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"), root.resolve("game")).accepted(1);
		if (v1 == null) { throw new IllegalStateException("AUTONOMOUS_STOCK_OBSERVER_UNAVAILABLE"); }
		final String before = PhantomLearning005Native.trainingState(root, true);
		boolean allSettled = true;
		try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var sources = evaluationSources(root, directory, discoverSources(root, directory, config, authority, () -> requested(attempt)), config);
			final var cells = planCells(directory, sources, config, authority, () -> requested(attempt)); requireEvaluationCoverage(cells);
			immutable(directory.resolve("protocol.json"), "{\"modelVersion\":" + model.id() + ",\"modelSha\":" + q(model.sha()) + ",\"comparator\":" + q(AutonomousTrainingV1.COMPARATOR) + ",\"trainingStateSha\":" + q(hash(before)) + ",\"verdict\":\"INCONCLUSIVE_UNTIL_NATIVE_PAIRS\",\"trainingCredit\":false}");
			final var paired = new ArrayList<String>(); int completedPairs = 0;
			for (var cell : cells)
			{
				if (requested(attempt)) { allSettled = false; break; }
				final var arms = new ArrayList<EvaluationArm>();
				final var slot = new AutonomousTrainingV1.CohortSlot(cells.indexOf(cell), cell.key(), hash("EVAL/" + cell.key()), "EVAL", false);
				for (String side : cells.indexOf(cell) % 2 == 0 ? List.of("STOCK", "LEARNED") : List.of("LEARNED", "STOCK"))
				{
					if (requested(attempt)) { break; }
					final String id = hash("EVAL/" + config.text("outcome006DatasetId", "") + "/" + cell.key() + "/" + side); final var work = directory.resolve(id);
					if (Files.exists(work))
					{
						final Path saved = work.resolve("evaluation-arm.tsv");
						if (!Files.exists(saved)) { allSettled = false; break; }
						if (!saved.equals(saved.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_EVAL_ARM_PATH"); }
						arms.add(decodeArm(Files.readString(saved))); continue;
					}
					Files.createDirectory(work); immutable(work.resolve("reserved.json"), "{\"episode\":" + q(id) + ",\"side\":" + q(side) + ",\"split\":\"EVAL\",\"trainingCredit\":false}");
					arms.add(collectEpisode(root, attempt, config, authority, store, model, v1.model(), directory, work, id, slot, cell, side, false));
				}
				if (arms.size() != 2) { allSettled = false; continue; }
				final String status = pairStatus(arms); if (status.equals("MATCHED_COMPLETED")) { completedPairs++; }
				final String receipt = "{\"cell\":" + q(cell.key()) + ",\"direction\":" + q(evaluationDirection(cell)) + ",\"order\":" + q(cells.indexOf(cell) % 2 == 0 ? "STOCK_LEARNED" : "LEARNED_STOCK") + ",\"arms\":" + arms.size() + ",\"status\":" + q(status) + ",\"trainingCredit\":false}";
				immutable(directory.resolve(cell.key() + ".pair.json"), receipt); paired.add(receipt);
			}
			if (allSettled && !requested(attempt)) { immutable(directory.resolve("evaluation-pairs.json"), "{\"completedPairs\":" + completedPairs + ",\"pairs\":[" + String.join(",", paired) + "],\"trainingCredit\":false,\"verdict\":\"INCONCLUSIVE\"}"); }
		}
		final String after = PhantomLearning005Native.trainingState(root, true); if (!before.equals(after)) { throw new IllegalStateException("AUTONOMOUS_EVAL_CHANGED_TRAINING_STATE"); }
		immutable(attempt.resolve("batch.complete"), "schemaVersion=9\nevaluationOnly=true\ntrainingStateUnchanged=true\n");
		if (allSettled && !requested(attempt)) { immutable(directory.resolve("evaluation.complete"), "{\"verdict\":\"INCONCLUSIVE\",\"reason\":\"BOUNDED_NATIVE_SAMPLE_NO_PROMOTION\",\"trainingStateUnchanged\":true,\"productionPromotion\":false}"); }
	}
	private static List<Source> evaluationSources(Path root, Path directory, List<Source> sources, TrainingConfig config) throws Exception
	{
		if (!config.text("autonomous009EvaluationOnly", "false").equals("true")) { return sources; }
		final String campaign = config.text("autonomous009EvaluationReferenceCampaign", ""), dataset = config.text("autonomous009EvaluationReferenceDataset", "");
		if (!campaign.matches("autonomous009-[A-Za-z0-9_-]{1,64}") || !dataset.matches("autonomous009-[A-Za-z0-9_-]{1,80}"))
		{ throw new IllegalStateException("AUTONOMOUS_EVAL_REFERENCE_REQUIRED"); }
		final Path reference = root.resolve("autonomous/v1/campaigns").resolve(campaign);
		final Path cells = reference.resolve("cohorts").resolve(dataset).resolve("cells.tsv");
		final Path pin = reference.resolve("evaluations").resolve(dataset + "-" + config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE)).resolve("protocol.json");
		if (!cells.equals(cells.toRealPath()) || !pin.equals(pin.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_EVAL_REFERENCE_PATH"); }
		final String pinned = Files.readString(pin);
		if (!pinned.contains("\"modelSha\":" + q(config.text("autonomous009ModelSha", "")))) { throw new IllegalStateException("AUTONOMOUS_EVAL_REFERENCE_MODEL_CHANGED"); }
		final var excluded = new java.util.TreeSet<String>();
		for (String line : Files.readAllLines(cells).stream().skip(1).toList())
		{ final String[] fields = line.split("\t", -1); if (fields.length != 16) { throw new IllegalStateException("AUTONOMOUS_CELL_CODEC"); } excluded.addAll(List.of(fields[1].split(","))); }
		if (excluded.isEmpty() || !sources.stream().map(s -> s.image().sha()).toList().containsAll(excluded)) { throw new IllegalStateException("AUTONOMOUS_EVAL_REFERENCE_SOURCES_CHANGED"); }
		final var usedPhysical = sources.stream().filter(s -> excluded.contains(s.image().sha())).map(s -> s.image().physicalFingerprint()).collect(java.util.stream.Collectors.toSet());
		final var independent = sources.stream().filter(s -> !excluded.contains(s.image().sha()) && !usedPhysical.contains(s.image().physicalFingerprint())).toList();
		immutable(directory.resolve("independence.json"), "{\"referenceCampaign\":" + q(campaign) + ",\"referenceDataset\":" + q(dataset) + ",\"referenceCellsSha\":" + q(NeuralPolicy.sha256(Files.readAllBytes(cells)))
			+ ",\"referenceModelSha\":" + q(config.text("autonomous009ModelSha", "")) + ",\"excludedSourceSnapshots\":[" + excluded.stream().map(PhantomAutonomousTrainingNative::q).collect(java.util.stream.Collectors.joining(","))
			+ "],\"selectedSourceSnapshots\":[" + independent.stream().map(s -> q(s.image().sha())).collect(java.util.stream.Collectors.joining(",")) + "],\"authority\":\"PRE_CUT_SOURCE_PROVENANCE_ONLY\",\"trainingCredit\":false}");
		return independent;
	}
	private static String evaluationDirection(Cell cell)
	{ return (cell.party() ? "PARTY_" : "SOLO_") + cell.proposal().pool().family(); }
	private static void requireEvaluationCoverage(List<Cell> cells)
	{
		final var directions = cells.stream().map(PhantomAutonomousTrainingNative::evaluationDirection).collect(java.util.stream.Collectors.toSet());
		if (cells.stream().map(c -> c.scene().id()).distinct().count() < 2 || !directions.contains("SOLO_DAMAGE") || (!directions.contains("PARTY_HEAL") && !directions.contains("PARTY_DAMAGE")))
		{ throw new IllegalStateException("AUTONOMOUS_BLOCKED_EVAL_NO_LAWFUL_SCENE"); }
	}
	private static List<Cell> planCells(Path directory, List<Source> sources, TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority, java.util.function.BooleanSupplier requested) throws Exception
	{
		PhantomTrainingScenarios.requirePlanningActive(requested);
		final Path sealed = directory.resolve("cells.tsv");
		if (Files.exists(sealed))
		{
			final var result = new ArrayList<Cell>();
			for (String line : Files.readAllLines(sealed).subList(1, Files.readAllLines(sealed).size()))
			{
				PhantomTrainingScenarios.requirePlanningActive(requested);
				final String[] f = line.split("\t", -1);
				if (f.length != 16) { throw new IllegalStateException("AUTONOMOUS_CELL_CODEC"); }
				final var roster = java.util.Arrays.stream(f[1].split(",")).map(sha -> sources.stream().filter(s -> s.image().sha().equals(sha)).findFirst().orElseThrow(() -> new IllegalStateException("AUTONOMOUS_SEALED_SOURCE_CHANGED"))).toList();
				final var family = LegalActionV3.Family.valueOf(f[2]); final var mode = f[3].equals("PARTY") ? null : PhantomCombatMode.valueOf(f[3]);
				final String learnerSha = f[6].split(":", 2)[0]; final String sceneId = f[6].split(":", 2)[1];
				final var learner = roster.stream().filter(s -> s.seed().sourceSnapshotSha().equals(learnerSha)).findFirst().orElseThrow();
				final var pool = learner.seed().supported().stream().filter(p -> p.family() == family && p.mode() == mode).findFirst().orElseThrow();
				final var refs = java.util.Arrays.stream(f[4].split(",")).map(ref -> { final var pair = ref.split(":"); return new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(Integer.parseInt(pair[0]), Integer.parseInt(pair[1])); }).toList();
				final var proposal = new AutonomousTrainingV1.Proposal(learner.seed(), pool, "shortlist/" + learner.seed().sourceSnapshotSha() + "/" + pool.key(), new AutonomousTrainingV1.Shortlist(refs, Long.parseLong(f[5]), false));
				final var scene = new PhantomTrainingScenarios.Scene(sceneId, f[7], Integer.parseInt(f[8]), learner.seed().classId(), learner.seed().level(), learner.grade(), learner.enchant(), Integer.parseInt(f[9]), Integer.parseInt(f[10]), Integer.parseInt(f[11]), refs.stream().map(r -> r.skillId()).toList(), "UNASSIGNED");
				if (!f[15].equals(hash(config.text("autonomous009ModelSha", "") + "/" + config.episodeSeconds()))) { throw new IllegalStateException("AUTONOMOUS_SEALED_PROTOCOL_CHANGED"); }
				result.add(new Cell(f[0], proposal, roster, scene, Integer.parseInt(f[12]), Integer.parseInt(f[13]), Integer.parseInt(f[14])));
			}
			return List.copyOf(result);
		}
		final var planned = new ArrayList<Cell>(); final var visits = new HashMap<String, Long>(); final var cursors = new HashMap<String, Long>(); final var refusals = new ArrayList<String>(); final var sceneCounts = new java.util.TreeMap<String, Long>();
		final int cellLimit = AutonomousTrainingV1.cohortCellLimit(config.integer("episodesPerBatch", 48, 48, 256));
		for (var source : sources) { for (var pool : source.seed().supported()) { cursors.put("shortlist/" + source.seed().sourceSnapshotSha() + "/" + pool.key(), Math.multiplyExact(config.longValue("autonomous009SourceCursor", 0, 0, Long.MAX_VALUE), 4)); } }
		for (int sequence = 0; sequence < 48 && planned.size() < cellLimit; sequence++)
		{
			PhantomTrainingScenarios.requirePlanningActive(requested);
			final var weightedVisits = new HashMap<>(visits); final var weights = config.autonomousClassWeights();
			for (var source : sources) { final String key = "class/" + source.seed().classId(); weightedVisits.put(key, visits.getOrDefault(key, 0L) * 1000 / weights.get(source.seed().classId())); }
			final var next = AutonomousTrainingV1.nextCandidate(sources.stream().map(Source::seed).toList(), weightedVisits, cursors, sequence, config.seed());
			if (next.isEmpty()) { break; }
			final var proposal = next.orElseThrow(); final var source = sources.stream().filter(s -> s.seed().equals(proposal.actor())).findFirst().orElseThrow();
			visits.merge("class/" + source.seed().classId(), 1L, Long::sum); visits.merge("class/" + source.seed().classId() + "/family/" + proposal.pool().key(), 1L, Long::sum);
			visits.merge("source/" + source.seed().sourceSnapshotSha(), 1L, Long::sum); cursors.put(proposal.shortlistKey(), proposal.shortlist().nextCursor());
			if (!config.autonomousFamilies().contains(proposal.pool().family())) { continue; }
			final boolean party = proposal.pool().family() == LegalActionV3.Family.HEAL || planned.size() % 2 == 1;
			final var roster = new ArrayList<Source>(); roster.add(source);
			if (party)
			{
				for (var helper : sources.stream().sorted(Comparator.comparing(s -> hash(config.seed() + "/helper/" + s.image().physicalFingerprint()))).toList())
				{
					if (roster.size() >= config.maxActiveActors()) { break; }
					if (roster.stream().noneMatch(s -> s.seed().classId() == helper.seed().classId()) && Math.abs(helper.seed().level() - source.seed().level()) <= 8) { roster.add(helper); }
				}
				if (roster.size() < 3) { refusals.add("AUTONOMOUS_NO_DISTINCT_CLASS_PARTY_ROSTER"); continue; }
				if (proposal.pool().family() == LegalActionV3.Family.HEAL)
				{
					// The learned healer is not automatically the farming leader. Choose by acquired offense, without class IDs.
					final var leader = roster.stream().filter(s -> s != source).max(Comparator.comparingInt(s -> s.seed().supported().stream().filter(p -> p.family() == LegalActionV3.Family.DAMAGE).mapToInt(p -> p.skills().size()).max().orElse(0))).orElseThrow();
					roster.remove(leader); roster.addFirst(leader);
				}
			}
			Player probe = null;
			try
			{
				PhantomTrainingScenarios.requirePlanningActive(requested); probe = reproduce(config, source.image());
				final Player actor = probe; final var admitted = new HashMap<String, AutonomousTrainingV1.Proposal>();
				final var scenes = PhantomTrainingScenarios.acquiredScenes(proposal, authority, roster.stream().map(s -> s.seed().level()).toList(), source.grade(), source.enchant(), config.seed() + config.longValue("autonomous009SourceCursor", 0, 0, Long.MAX_VALUE) + planned.size(), 12, sceneCounts,
					candidate -> { final var bound = admitScene(proposal, source, List.copyOf(roster), candidate, actor, refusals); bound.ifPresent(value -> admitted.put(candidate.scene().id(), value)); return bound.isPresent(); }, requested);
				for (var candidate : scenes)
				{
					PhantomTrainingScenarios.requirePlanningActive(requested);
					final var candidateScene = candidate.scene(); final var targetPoint = candidate.target();
					var scene = candidateScene;
					final var admittedProposal = admitted.get(candidateScene.id());
					final var geo = GeoEngine.getInstance();
					final var target = candidate.exemplar();
					if (!target.isSpawned() || target.isAlikeDead() || !target.getAttackByList().isEmpty() || !openingGeometry(candidateScene, targetPoint.x(), targetPoint.y(), targetPoint.z())) { continue; }
					if (proposal.pool().family() == LegalActionV3.Family.DAMAGE)
					{
						final String sceneId = hash(PhantomTrainingScenarios.acquiredSceneId(admittedProposal, scene.source(), scene.npc(), scene.x(), scene.y(), scene.z(), scene.grade(), scene.weaponEnchant()) + "/target/" + targetPoint);
						scene = new PhantomTrainingScenarios.Scene(sceneId, scene.source(), scene.npc(), scene.classId(), scene.level(), scene.grade(), scene.weaponEnchant(), scene.x(), scene.y(), scene.z(), admittedProposal.shortlist().candidates().stream().map(ref -> ref.skillId()).toList(), scene.split());
					}
					final String key = hash("cell/" + planned.size() + "/" + source.image().physicalFingerprint() + "/" + proposal.pool().key() + "/" + party + "/" + scene.id());
					cursors.put(admittedProposal.shortlistKey(), admittedProposal.shortlist().nextCursor());
					planned.add(new Cell(key, admittedProposal, List.copyOf(roster), scene, targetPoint.x(), targetPoint.y(), targetPoint.z())); break;
				}
			}
			finally { if (probe != null) { probe.stopAllTasks(); probe.deleteMe(); } }
		}
		final var lines = new ArrayList<String>(); lines.add("cell\tsourceShas\tfamily\tmode\tshortlist\tcursor\tscene\tsource\tnpc\tx\ty\tz\ttargetX\ttargetY\ttargetZ\tprotocolSha");
		for (var cell : planned)
		{
			final var scene = cell.scene(); final var p = cell.proposal();
			lines.add(String.join("\t", cell.key(), cell.roster().stream().map(s -> s.image().sha()).collect(java.util.stream.Collectors.joining(",")), p.pool().family().name(), p.pool().mode() == null ? "PARTY" : p.pool().mode().name(), p.shortlist().candidates().stream().map(r -> r.skillId() + ":" + r.skillLevel()).collect(java.util.stream.Collectors.joining(",")), Long.toString(p.shortlist().nextCursor()), p.actor().sourceSnapshotSha() + ":" + scene.id(), scene.source(), Integer.toString(scene.npc()), Integer.toString(scene.x()), Integer.toString(scene.y()), Integer.toString(scene.z()), Integer.toString(cell.x()), Integer.toString(cell.y()), Integer.toString(cell.z()), hash(config.text("autonomous009ModelSha", "") + "/" + config.episodeSeconds())));
		}
		PhantomTrainingScenarios.requirePlanningActive(requested); immutable(sealed, String.join("\n", lines) + "\n"); immutable(directory.resolve("planning-refusals.json"), "{\"refusals\":[" + refusals.stream().map(PhantomAutonomousTrainingNative::q).collect(java.util.stream.Collectors.joining(",")) + "],\"plannedCells\":" + planned.size() + "}");
		immutable(directory.resolve("scene-catalog-counts.json"), PhantomLearning005Evaluation.mapJson(sceneCounts));
		return List.copyOf(planned);
	}
	/** The same explicit native admission runs before the bounded scene page in TRAIN and EVAL. */
	private static java.util.Optional<AutonomousTrainingV1.Proposal> admitScene(AutonomousTrainingV1.Proposal proposal, Source source,
		List<Source> roster, PhantomTrainingScenarios.AcquiredScene candidate, Player actor, List<String> refusals)
	{
		final var target = candidate.exemplar(); final var scene = candidate.scene(); final var point = candidate.target();
		if (!target.isSpawned() || target.isAlikeDead() || !target.getAttackByList().isEmpty() || !openingGeometry(scene, point.x(), point.y(), point.z())) { return java.util.Optional.empty(); }
		actor.setXYZ(target.getX() + 180, target.getY(), target.getZ()); actor.setCurrentHp(actor.getMaxHp()); actor.setCurrentMp(actor.getMaxMp());
		var admitted = java.util.Optional.of(proposal);
		if (proposal.pool().family() == LegalActionV3.Family.DAMAGE)
		{
			final var targetFacts = org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.nativePlanningTarget(target, true);
			final var eligible = proposal.pool().skills().stream().filter(ref -> source.inventory().abilities().stream().filter(a -> a.skill().equals(ref)).anyMatch(a -> PhantomSkillInventory.eligibility(actor, target, a, () -> true, true).ready()))
				.filter(ref ->
				{
					final var combat = org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.nativePlanningCombat(actor, target, actor.getKnownSkill(ref.skillId()));
					final var mechanics = combat.forTarget(targetFacts);
					return mechanics != null && org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.estimateCastCycle(combat.combat(), targetFacts, mechanics).isPresent();
				}).toList();
			admitted = targetBoundProposal(proposal, eligible);
			if (admitted.isEmpty()) { refusals.add("TARGET_BOUND_ALTERNATIVES:" + source.image().sha() + ":npc=" + target.getId() + ":level=" + target.getLevel() + ":eligible=" + eligible.size() + ":full=" + proposal.pool().skills().size()); return admitted; }
		}
		if (roster.size() > 1 && !targetBoundHelper(new Cell("planning", admitted.orElseThrow(), roster, scene, point.x(), point.y(), point.z()), target))
		{ refusals.add("TARGET_BOUND_HELPER_MAGIC_ENVELOPE:" + source.image().sha() + ":npc=" + target.getId() + ":level=" + target.getLevel()); return java.util.Optional.empty(); }
		return admitted;
	}
	/** Keep the complete acquired pool; only the conditional pre-CUT shortlist is target-bound. */
	private static java.util.Optional<AutonomousTrainingV1.Proposal> targetBoundProposal(AutonomousTrainingV1.Proposal proposal,
		List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef> eligible)
	{
		if (!proposal.pool().skills().containsAll(eligible)) { throw new IllegalArgumentException("AUTONOMOUS_TARGET_BOUND_UNLEARNED_REF"); }
		if (eligible.size() < 2) { return java.util.Optional.empty(); }
		final long cursor = proposal.shortlist().nextCursor() - proposal.shortlist().candidates().size();
		return java.util.Optional.of(new AutonomousTrainingV1.Proposal(proposal.actor(), proposal.pool(), proposal.shortlistKey(), AutonomousTrainingV1.shortlist(eligible, cursor, 4)));
	}
	/** Necessary native envelope only; the full Party admission still runs unchanged before CUT. */
	private static boolean targetBoundHelper(Cell cell, Monster target)
	{
		final var source = rosterSource(cell);
		for (var actor : cell.roster())
		{
			if (actor.seed().equals(cell.proposal().actor())) { continue; }
			final var capabilities = source.capabilities(actor.seed().classId());
			// Same mode priority as the ordinary Party owner; no class/skill allowlist.
			final var selected = List.of(PhantomCombatMode.RANGED_MAGIC, PhantomCombatMode.RANGED_PHYSICAL, PhantomCombatMode.MELEE_PHYSICAL).stream()
				.flatMap(mode -> capabilities.stream().filter(cap -> cap.capabilityKey().equals(mode.capabilityKey()))).findFirst().orElse(null);
			if (selected == null || selected.skills().isEmpty()) { continue; }
			if (selected.skills().stream().allMatch(ref ->
			{
				final var skill = org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(ref.skillId(), ref.skillLevel());
				final var mechanics = org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.mechanics(skill);
				return skill != null && skill.getLevel() == ref.skillLevel() && mechanics != null
					&& org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.magicLevelReady(mechanics, actor.seed().level(), target.getLevel());
			})) { return true; }
		}
		return false;
	}
	private static boolean openingGeometry(PhantomTrainingScenarios.Scene scene, int x, int y, int z)
	{
		final var geo = GeoEngine.getInstance();
		return geo.hasGeo(x, y) && geo.getHeight(x, y, z) == z && geo.hasGeo(x + 180, y) && geo.getHeight(x + 180, y, z) == z
			&& geo.canSeeTarget(x + 180, y, z, 0, x, y, z, 0) && geo.canMoveToTarget(scene.x(), scene.y(), scene.z(), x + 180, y, z, 0)
			&& geo.canMoveToTarget(x + 180, y, z, x, y, z, 0) && geo.canMoveToTarget(x, y, z, x + 180, y, z, 0);
	}
	/** Called only after the existing entrypoint has booted and attested its own GameServer. */
	static void run(Path root, Path attempt, TrainingConfig config) throws Exception
	{
		if (!config.autonomousConfigured() || !config.text("autonomous009Enabled", "false").equals("true") || !Set.of("COLLECT", "EVAL").contains(config.text("autonomous009Phase", "")))
		{ throw new IllegalStateException("AUTONOMOUS_COLLECTION_AUTHORITY_REQUIRED"); }
		if (config.text("autonomous009Phase", "").equals("EVAL")) { evaluateNative(root, attempt, config); return; }
		PhantomTrainingActors.settings(root); final var directory = cohort(root, config); final var store = new TacticalStoreV3(root);
		final var parent = store.optimizationParent(); final var model = store.candidate(config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE));
		if (model == null || model.id() != parent.modelId() || !model.sha().equals(config.text("autonomous009ModelSha", ""))) { throw new IllegalStateException("AUTONOMOUS_FROZEN_MODEL_CHANGED"); }
		final var settings = PhantomTrainingActors.settings(root); final var v1 = new LearningStore(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"), root.resolve("game")).accepted(1);
		if (v1 == null) { throw new IllegalStateException("AUTONOMOUS_STOCK_OBSERVER_UNAVAILABLE"); }
		for (int warm = 0; warm < 2048; warm++) { model.model().score(new float[128], new float[64]); }
		try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var sources = discoverSources(root, directory, config, authority, () -> requested(attempt)); final var cells = planCells(directory, sources, config, authority, () -> requested(attempt));
			final String jar = NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar")));
			immutable(directory.resolve("protocol.json"), "{\"version\":" + q(AutonomousTrainingV1.VERSION) + ",\"objective\":\"ACQUIRED_ACTION_ADVANTAGE_V2\",\"configSha\":" + q(org.l2jmobius.gameserver.phantoms.learning.ActionAdvantageV2.CONFIG_SHA)
				+ ",\"comparator\":" + q(AutonomousTrainingV1.COMPARATOR) + ",\"jarSha\":" + q(jar) + ",\"modelVersion\":" + model.id() + ",\"modelSha\":" + q(model.sha()) + ",\"parentAdamSha\":" + q(parent.sha()) + ",\"cellsSha\":" + q(NeuralPolicy.sha256(Files.readAllBytes(directory.resolve("cells.tsv")))) + ",\"explorationEpsilon\":0.25,\"nativeRng\":\"UNMODIFIED\",\"episodeSeconds\":" + config.episodeSeconds() + ",\"episodeBudget\":" + config.integer("episodesPerBatch", 48, 48, 256) + ",\"productionPromotion\":false}");
			final var schedule = cells.isEmpty() ? List.<AutonomousTrainingV1.CohortSlot>of() : AutonomousTrainingV1.cohortSlots(cells.stream().map(Cell::key).toList(), config.integer("episodesPerBatch", 48, 48, 256));
			immutable(directory.resolve("schedule.tsv"), "ordinal\tcell\tblock\tsplit\texperimental\n" + schedule.stream().map(s -> s.ordinal() + "\t" + s.cell() + "\t" + s.block() + "\t" + s.split() + "\t" + s.experimental()).collect(java.util.stream.Collectors.joining("\n")) + "\n");
			for (var slot : schedule)
			{
				if (requested(attempt)) { break; }
				final String id = hash(config.text("outcome006DatasetId", "") + "/" + slot.ordinal() + "/" + slot.cell()); final Path work = directory.resolve(id);
				if (Files.exists(work)) { continue; } // Reserved incarnations are never dispatched twice, including crash/partial slots.
				Files.createDirectory(work); immutable(work.resolve("reserved.json"), "{\"episode\":" + q(id) + ",\"slot\":" + slot.ordinal() + ",\"block\":" + q(slot.block()) + ",\"split\":" + q(slot.split()) + ",\"attempt\":" + q(attempt.getFileName().toString()) + "}");
				final var cell = cells.stream().filter(c -> c.key().equals(slot.cell())).findFirst().orElseThrow();
				collectEpisode(root, attempt, config, authority, store, model, v1.model(), directory, work, id, slot, cell, null, false);
			}
			if (!requested(attempt)) { immutable(directory.resolve("collection.complete"), "Reserved attempts are settled or preserved; no claim of dataset sufficiency.\n"); }
		}
		immutable(attempt.resolve("batch.complete"), "schemaVersion=9\ncollectionOnly=true\n");
	}
	private static List<String[]> reproduceRoster(PhantomBackgroundSuite.ProductionAuthorityFixture authority, Cell cell, Path work, String split, TrainingConfig config, Path riskPin) throws Exception
	{
		final var profiles = PhantomProfileRepository.open();
		final List<String[]> pinned;
		if (riskPin != null && Files.exists(riskPin))
		{
			if (!riskPin.equals(riskPin.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_EVAL_RISK_PIN_PATH"); }
			pinned = Files.readAllLines(riskPin).stream().skip(1).map(line -> line.split("\t", -1)).toList();
			if (pinned.size() != cell.roster().size()) { throw new IllegalStateException("AUTONOMOUS_EVAL_RISK_PIN_ROSTER"); }
		}
		else { pinned = List.of(); }
		final var riskLines = new ArrayList<String>(); riskLines.add("role\tsourcePhysicalSha\tuncovered\tcovered");
		final var risk = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.DEFAULT_TACTICAL_RISK;
		try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT GREATEST(COALESCE(MAX(profile_id),0),(SELECT COALESCE(MAX(actor_profile),0) FROM phantom_tactical_v3_events))+1,(SELECT COUNT(*) FROM characters) FROM phantom_profiles"))
		{
			if (!rows.next() || rows.getLong(1) + cell.roster().size() > config.integer("maxStoredProfiles", 4096, 64, 32768) || rows.getLong(2) + cell.roster().size() > config.integer("maxStoredActors", 4096, 32, 32768))
			{ throw new IllegalStateException("AUTONOMOUS_ACTOR_CAPACITY"); }
			final long next = rows.getLong(1); try (var alter = connection.createStatement()) { alter.executeUpdate("ALTER TABLE phantom_profiles AUTO_INCREMENT=" + next); }
		}
		final var roster = new ArrayList<String[]>();
		for (var source : cell.roster())
		{
			long expectedProfile = 0; final int index = roster.size();
			if (!pinned.isEmpty())
			{
				final String[] expected = pinned.get(index);
				if (expected.length != 4 || !expected[0].equals(Integer.toString(index)) || !expected[1].equals(source.image().physicalFingerprint())) { throw new IllegalStateException("AUTONOMOUS_EVAL_RISK_PIN_ROSTER"); }
				try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT GREATEST(COALESCE(MAX(profile_id),0),(SELECT COALESCE(MAX(actor_profile),0) FROM phantom_tactical_v3_events))+1 FROM phantom_profiles"))
				{
					if (!rows.next()) { throw new IllegalStateException("AUTONOMOUS_ACTOR_CAPACITY"); }
					expectedProfile = matchingRiskProfile(rows.getLong(1), Integer.parseInt(expected[2]), config.integer("maxStoredProfiles", 4096, 64, 32768) - (cell.roster().size() - index));
					try (var alter = connection.createStatement()) { alter.executeUpdate("ALTER TABLE phantom_profiles AUTO_INCREMENT=" + expectedProfile); }
				}
			}
			final var player = reproduce(config, source.image());
			try
			{
				player.setXYZ(cell.scene().x(), cell.scene().y(), cell.scene().z()); player.setHeading(0);
				// Declared scene input before preparation/CUT. No skill/item grants and no post-CUT resource writes.
				player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(player.getMaxCp());
				final long profile = profiles.create(player.getObjectId()).profileId();
				final String riskLine = index + "\t" + source.image().physicalFingerprint() + "\t" + risk.limitPercent(profile, false) + "\t" + risk.limitPercent(profile, true);
				if (!pinned.isEmpty() && (profile != expectedProfile || !riskLine.equals(String.join("\t", pinned.get(index))))) { throw new IllegalStateException("AUTONOMOUS_EVAL_RISK_PROFILE_CHANGED"); }
				riskLines.add(riskLine);
				final var planner = new PhantomHistoricalBackgroundPlanner(authority.knowledge(), authority.topology(), authority.authority());
				final var initial = planner.planInitial(profile, player, config.seed(), 1); final var baseline = (initial.ready() ? initial : planner.idleInitial(profile, player, config.seed(), 1)).goal();
				if (baseline == null) { throw new IllegalStateException("AUTONOMOUS_ORDINARY_GOAL_UNAVAILABLE"); }
				final var result = planner.replanLogicalRendezvous(profile, authority.authority().capture(profile, player, baseline, null), baseline, config.seed(), 2, new PhantomDomainRef("background.farm", cell.scene().source()));
				if (result.goal() == null) { throw new IllegalStateException("AUTONOMOUS_ORDINARY_GOAL_REFUSED"); }
				final var goals = new PhantomGoalStateStore(profiles); goals.insert(profile, result.goal());
				if (!goals.load(profile).orElseThrow().goal().equals(result.goal())) { throw new IllegalStateException("AUTONOMOUS_GOAL_READBACK_CHANGED"); }
				player.getInventory().updateDatabase(); player.storeMe();
				roster.add(new String[] { split, cell.party() ? "PARTY" : "SOLO", Long.toString(profile), Integer.toString(player.getObjectId()), Integer.toString(source.seed().classId()), Integer.toString(source.seed().level()), "ACQUIRED", source.inventory().fingerprint(), Integer.toString(cell.scene().x()), Integer.toString(cell.scene().y()), Integer.toString(cell.scene().z()) });
			}
			finally { player.stopAllTasks(); player.deleteMe(); }
		}
		if (riskPin != null) { immutable(riskPin, String.join("\n", riskLines) + "\n"); }
		immutable(work.resolve("roster.tsv"), roster.stream().map(row -> String.join("\t", row)).collect(java.util.stream.Collectors.joining("\n")) + "\n"); return List.copyOf(roster);
	}
	private static org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilitySource rosterSource(Cell cell)
	{
		final var frozen = new LinkedHashMap<Integer, List<org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilityEvidence>>();
		for (var source : cell.roster())
		{
			final var evidence = new ArrayList<org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilityEvidence>();
			for (var pool : source.seed().supported())
			{
				if (pool.family() != LegalActionV3.Family.DAMAGE) { continue; }
				final boolean learner = source.seed().equals(cell.proposal().actor()) && cell.proposal().pool().family() == LegalActionV3.Family.DAMAGE;
				if (learner && pool.mode() != cell.proposal().pool().mode()) { continue; }
				// Helpers are not gradient recipients. Their one native action is derived from canonical power/time, without class or skill IDs.
				final var refs = learner ? cell.proposal().shortlist().candidates() : pool.skills().stream().sorted(Comparator.comparingDouble((org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef ref) ->
				{
					final var ability = source.inventory().abilities().stream().filter(a -> a.skill().equals(ref)).findFirst().orElseThrow();
					return ability.power() / Math.max(1, ability.castMillis() + (double) ability.reuseMillis());
				}).reversed().thenComparingInt(ref -> ref.skillId())).limit(1).toList();
				evidence.add(new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilityEvidence(pool.mode().capabilityKey(), "autonomous-frozen", 850,
					refs.stream().map(ref -> new org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill(ref.skillId(), ref.skillLevel())).toList()));
			}
			if (frozen.put(source.seed().classId(), List.copyOf(evidence)) != null) { throw new IllegalStateException("AUTONOMOUS_CLASS_ONLY_RESOLVER_ROSTER_COLLISION"); }
		}
		final var immutable = Map.copyOf(frozen); return classId -> immutable.getOrDefault(classId, List.of());
	}
	private static EvaluationArm collectEpisode(Path root, Path attempt, TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority, TacticalStoreV3 store, TacticalStoreV3.Checkpoint model, NeuralPolicy v1,
		Path directory, Path work, String id, AutonomousTrainingV1.CohortSlot slot, Cell cell, String evaluationSide, boolean admissionOnly) throws Exception
	{
		final boolean evaluation = evaluationSide != null;
		final var receipts = new ConcurrentLinkedQueue<TacticalReceiptV3>(); final var failures = new ConcurrentLinkedQueue<TacticalFailureV3>(); final var selections = new ConcurrentLinkedQueue<TrainExplorationV1.Selection>();
		final var timings = new ConcurrentHashMap<String, TerminalRewardReceiptV1.NativeTiming>(); TerminalRewardReceiptV1.nativeTimingObserver(t -> { if (timings.size() >= 2048) { throw new IllegalStateException("AUTONOMOUS_NATIVE_EFFECT_BOUND"); } timings.put(t.eventKey(), t); });
		final var policy = LearningPolicy.evaluation(LearningPolicy.Mode.SHADOW, 1, v1, 4, hash(config.text("autonomous009Campaign", "") + "/" + id));
		final var tactical = new TacticalPolicyV3("LEARNED".equals(evaluationSide) ? TacticalPolicyV3.Mode.ON : TacticalPolicyV3.Mode.SHADOW, model.id(), model.model(), 20, 16, 2000, r -> receipts.add(TacticalReceiptV3.fromHeal(r)));
		tactical.damageObserver(receipts::add); tactical.failureObserver(failures::add);
		final var scene = cell.scene(); final var proposal = cell.proposal(); final var family = proposal.pool().family();
		final var source = rosterSource(cell);
		Monster target = null; TacticalEpisodeV3 episode = null; boolean cut = false; String exclusion = "", physical = ""; final long started = System.nanoTime();
		List<PhantomLearning005Evaluation.Incarnation> actors = List.of(); PhantomLearning005Evaluation.Incarnation targetIdentity = null;
		List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef> frozenActions = proposal.shortlist().candidates();
		try (var runtime = new PhantomLearning001ShadowNative.Runtime(authority, root.resolve("game"), policy, attempt.resolve("stop.request"), true, config.maxActiveActors(), tactical, true, source))
		{
			java.util.function.Supplier<TacticalEpisodeV3> terminal = null;
			try
			{
				final var roster = reproduceRoster(authority, cell, work, slot.split(), config, evaluation && cell.party() ? directory.resolve(cell.key() + ".risk.tsv") : null); final var inventories = new HashMap<Long, String>();
				for (int i = 0; i < roster.size(); i++) { inventories.put(Long.parseLong(roster.get(i)[2]), cell.roster().get(i).inventory().fingerprint()); }
				runtime.prepareAcquired(scene, roster, cell.party(), proposal.pool().mode(), proposal.shortlist().candidates(), inventories, config.preparationSeconds(), () -> requested(attempt));
				immutable(work.resolve("shared-source.txt"), runtime.acquiredSourceFacts());
				for (int candidate = 0; candidate < 4; candidate++)
				{
					final var spawn = new Spawn(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(scene.npc())); spawn.setXYZ(cell.x(), cell.y(), cell.z()); final var npc = (Monster) spawn.doSpawn(false); spawn.stopRespawn();
					final boolean admitted = !npc.isChampion() && !npc.isRaid() && !npc.isAggressive() && !npc.isAlikeDead() && npc.getCurrentHp() == npc.getMaxHp();
					Files.writeString(work.resolve("admissions.jsonl"), "{\"candidate\":" + candidate + ",\"objectId\":" + npc.getObjectId() + ",\"admitted\":" + admitted + "}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					if (admitted) { target = npc; break; } npc.deleteMe();
				}
				if (target == null || !openingGeometry(scene, cell.x(), cell.y(), cell.z())) { throw new IllegalStateException("AUTONOMOUS_FRESH_TARGET_OR_GEO_REFUSED"); }
				target.setHeading(0); runtime.prepareAcquiredOutcome(target);
				// Native AI may have wandered during formation. Restore the sealed declared position strictly before CUT.
				target.teleToLocation(cell.x(), cell.y(), cell.z(), 0, false); target.setHeading(0);
				final var players = runtime.evaluationPlayers();
				// Warm read-only snapshot/encoder paths before declared injuries and the native 5ms decision budget.
				for (int warm = 0; warm < 3; warm++)
				{
					final var observation = org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.collect(players.get(cell.learnerIndex()), Long.parseLong(roster.get(cell.learnerIndex())[2]), warm + 1, 20);
					model.model().encode(observation.state());
				}
				for (int i = 0; i < players.size(); i++)
				{
					final var player = players.get(i); final int offset = 180 + i * 20;
					if (!GeoEngine.getInstance().canMoveToTarget(scene.x(), scene.y(), scene.z(), target.getX() + offset, target.getY(), target.getZ(), player.getInstanceId())) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_OFFSET_GEO"); }
					player.teleToLocation(target.getX() + offset, target.getY(), target.getZ(), 0, false); player.setHeading(0); if (player.hasHeadlessOutboundSession() && player.isTeleporting()) { player.onTeleported(); }
					player.setCurrentHp(player.getMaxHp() * (family == LegalActionV3.Family.HEAL && i != cell.learnerIndex() && (i == 0 || i == 2) ? .50 : 1)); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(player.getMaxCp());
					PhantomLearning001ActorFixture.requireSafePartySpawn(player.getX(), player.getY(), player.getZ());
				}
				final var learner = players.get(cell.learnerIndex()); final var owner = learner.getNativeWorkOwner();
				if (owner == null || !owner.isCurrent()) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_NATIVE_OWNER"); }
				if (family == LegalActionV3.Family.HEAL)
				{
					final long profile = Long.parseLong(roster.get(cell.learnerIndex())[2]);
					final var offers = runtime.inspectHeal().stream().filter(d -> d.actor().profileId() == profile && d.kind() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind.HEAL_MEMBER).toList();
					frozenActions = offers.stream().map(d -> new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(d.actionSkillId(), d.actionSkillLevel())).distinct().toList();
					if (offers.size() < 2 || offers.size() > 16 || frozenActions.isEmpty() || frozenActions.size() > 4 || !proposal.pool().skills().containsAll(frozenActions)) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_SUPPORTED_STOCK_HEAL_OFFERS"); }
				}
				if (family == LegalActionV3.Family.DAMAGE)
				{
					final Monster exactTarget = target; final var inventory = PhantomSkillInventory.acquired(learner, authority.progression());
					final var readiness = proposal.shortlist().candidates().stream().map(ref -> { final var ability = inventory.abilities().stream().filter(a -> a.skill().equals(ref)).findFirst().orElseThrow(); return ref + ":" + PhantomSkillInventory.eligibility(learner, exactTarget, ability, owner::isCurrent, true).reasons(); }).toList();
					immutable(work.resolve("readiness.txt"), readiness.toString());
					if (proposal.shortlist().candidates().stream().filter(ref -> inventory.abilities().stream().filter(a -> a.skill().equals(ref)).anyMatch(a -> PhantomSkillInventory.eligibility(learner, exactTarget, a, owner::isCurrent, true).ready())).count() < 2)
					{ throw new IllegalStateException("AUTONOMOUS_PRE_CUT_TWO_READY_ALTERNATIVES"); }
				}
				if (!PhantomTrainingScenarios.factionIsolated(target, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId()), target))
				{ throw new IllegalStateException("AUTONOMOUS_PRE_CUT_NPC_FACTION_NOT_ISOLATED"); }
				final var facts = new java.util.TreeMap<>(PhantomLearning005Native.facts(players, target, scene, config));
				facts.put("npcFactionIsolation", "clear=true;helpRange=" + target.getTemplate().getClanHelpRange() + ";collision=" + target.getTemplate().getCollisionRadius() + ";reserve=300");
				facts.put("sourcePhysical", cell.roster().stream().map(s -> s.image().physicalFingerprint()).toList().toString()); facts.put("shortlist", frozenActions.toString()); facts.put("comparator", AutonomousTrainingV1.COMPARATOR);
				if (evaluation)
				{
					final var risk = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.DEFAULT_TACTICAL_RISK;
					facts.put("nativeRiskThresholds", cell.party() ? roster.stream().map(row -> risk.limitPercent(Long.parseLong(row[2]), false) + ":" + risk.limitPercent(Long.parseLong(row[2]), true)).toList().toString() : "SOLO");
				}
				physical = hash(PhantomLearning005Evaluation.mapJson(facts));
				immutable(work.resolve("admission-facts.json"), PhantomLearning005Evaluation.mapJson(facts));
				if (cell.party())
				{
					final var admission = runtime.acquiredPartyAdmission(); immutable(work.resolve("party-admission.json"), PhantomLearning005Evaluation.mapJson(admission));
					if (!Boolean.TRUE.equals(admission.get("admitted"))) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED"); }
				}
				final Path match = directory.resolve(cell.key() + ".physical.sha"); if (Files.exists(match) && !Files.readString(match).equals(physical)) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_MATCHED_PHYSICAL_FACTS_DIFFER"); } immutable(match, physical);
				immutable(work.resolve("precut.json"), "{\"comparisonCellSha\":" + q(physical) + ",\"facts\":" + PhantomLearning005Evaluation.mapJson(facts) + ",\"actorProfile\":" + roster.get(cell.learnerIndex())[2] + "}");
				actors = players.stream().map(player -> new PhantomLearning005Evaluation.Incarnation(player.getObjectId(), org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(player), player.getInstanceId())).toList();
				targetIdentity = new PhantomLearning005Evaluation.Incarnation(target.getObjectId(), org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(target), target.getInstanceId());
				immutable(work.resolve("incarnations.tsv"), "role\tobjectId\tepoch\tinstance\n" + players.stream().map(player -> "ACTOR\t" + player.getObjectId() + "\t" + org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(player) + "\t" + player.getInstanceId()).collect(java.util.stream.Collectors.joining("\n"))
					+ "\nTARGET\t" + target.getObjectId() + "\t" + org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(target) + "\t" + target.getInstanceId() + "\n");
				if (admissionOnly)
				{
					if (!receipts.isEmpty() || !failures.isEmpty()) { throw new IllegalStateException("AUTONOMOUS_ADMISSION_NATIVE_ACTION_OBSERVED"); }
					immutable(work.resolve("admission-only.json"), "{\"admitted\":true,\"afterCut\":false,\"gameServerStarted\":false,\"nativeActionsObserved\":0,\"trainingCredit\":false}"); return new EvaluationArm(physical, actors, targetIdentity, false, List.of());
				}
				final var window = evaluation ? null : TrainExplorationV1.ownedAutonomous(root, id, slot.block(), physical, slot.split(), slot.experimental() && config.text("autonomous009Exploration", "true").equals("true"), family, Long.parseLong(roster.get(cell.learnerIndex())[2]), model.id(), model.sha(), frozenActions, selections::add);
				final String before = "{\"comparisonCellSha\":" + q(physical) + ",\"comparator\":" + q(AutonomousTrainingV1.COMPARATOR) + ",\"runtime\":" + runtime.outcomeBeforeFacts() + ",\"cell\":" + q(cell.key()) + "}";
				final long generation = Long.parseLong(new String(Files.readAllBytes(root.resolve("director.json")), StandardCharsets.UTF_8).replaceAll("(?s).*\"generation\"\\s*:\\s*([0-9]+).*", "$1"));
				final var identity = target.getNativeEvidenceTarget(); receipts.clear(); failures.clear(); timings.clear(); cut = true;
				terminal = runtime.runLearning006Encounter(id, attempt.getFileName().toString(), config.episodeSeconds(), () -> requested(attempt), () -> List.copyOf(receipts), stamp ->
				{ try { if (!evaluation) { store.beginTerminalEpisode(id, attempt.getFileName().toString(), cell.party() ? "PARTY" : "SOLO", before, generation, identity.instanceId(), identity.objectId(), identity.spawnGeneration(), stamp); tactical.trainExploration(window); } else { immutable(work.resolve("cut.json"), before.substring(0, before.length() - 1) + ",\"cutNanos\":" + stamp + ",\"trainingCredit\":false}"); } } catch (Exception failure) { throw new IllegalStateException("AUTONOMOUS_DURABLE_CUT_FAILED", failure); } });
			}
			catch (IllegalStateException refused)
			{ if (cut) { throw refused; } exclusion = safeReason(refused); immutable(work.resolve("precut-refused.json"), "{\"reason\":" + q(exclusion) + "}"); }
			finally { tactical.trainExploration(null); if (terminal == null) { runtime.settleTraining(); } else { episode = runtime.settleLearning004(terminal).episode(); } runtime.releaseOutcome006(); }
			final String context = "autonomous009|" + config.text("outcome006DatasetId", "") + "|" + slot.split() + "|" + id;
			if (episode != null)
			{
				if (!evaluation) { for (var receipt : receipts) { store.record(receipt, slot.split(), context); } for (var failure : failures) { store.recordFailure(failure, slot.split(), context, id); } store.settleEpisode(episode); }
				immutable(work.resolve("settlement.json"), episode.json());
				for (var receipt : receipts) { immutable(work.resolve(receipt.eventKey() + ".native.json"), PhantomLearning005Native.nativeReceiptJson(receipt)); }
				if (!evaluation && !receipts.isEmpty() && runtime.outcomeEncounterAdmitted() && Set.of("NATIVE_KILL", "NATIVE_ACTOR_DIED", "TIMEOUT").contains(episode.reason()))
				{
					final var identity = target.getNativeEvidenceTarget(); final long generation = Long.parseLong(Files.readString(root.resolve("director.json")).replaceAll("(?s).*\"generation\"\\s*:\\s*([0-9]+).*", "$1"));
					final var boundary = new TerminalRewardReceiptV1.Boundary(hash(Files.readString(root.resolve("owner.txt"))), attempt.getFileName().toString(), generation, identity.instanceId(), identity.objectId(), identity.spawnGeneration(), runtime.outcomeCutNanos(), runtime.outcomeTerminalNanos(), episode.settledNanos(), true);
					final var eligible = new ArrayList<TerminalRewardReceiptV1.TimedAction>();
					for (var receipt : receipts)
					{
						try { final var timing = timings.get(receipt.eventKey()); if (timing == null) { continue; } final var action = timing.bind(new TacticalStoreV3.Row(slot.split(), context, receipt)); TerminalRewardReceiptV1.create(boundary, episode, List.of(action), slot.split(), config.text("outcome006DatasetId", ""), store.terminalConfig()); eligible.add(action); }
						catch (IllegalArgumentException invalid) { Files.writeString(work.resolve("excluded-actions.jsonl"), "{\"eventKey\":" + q(receipt.eventKey()) + ",\"reason\":" + q(invalid.getClass().getSimpleName()) + "}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
					}
					if (!eligible.isEmpty()) { final var reward = TerminalRewardReceiptV1.create(boundary, episode, eligible, slot.split(), config.text("outcome006DatasetId", ""), store.terminalConfig()); store.stageTerminal(reward); immutable(work.resolve("terminal.bin"), reward.bytes()); immutable(work.resolve("terminal.json"), reward.json()); }
				}
			}
			for (var selection : selections)
			{
				final var exactActions = frozenActions;
				final boolean supported = selection.offered().stream().allMatch(action -> exactActions.stream().anyMatch(ref -> ref.skillId() == action.skillId() && ref.skillLevel() == action.skillLevel()));
				immutable(work.resolve("selection.bin"), selection.bytes());
				if (supported) { requirePhysicalDirectory(root, directory.resolve("samples")); immutable(directory.resolve("samples").resolve(id + ".selection.bin"), selection.bytes()); }
				else { exclusion = "AUTONOMOUS_STOCK_OFFER_OUTSIDE_SUPPORTED_FROZEN_POOL"; }
			}
			immutable(work.resolve("result.json"), "{\"episode\":" + q(id) + ",\"classId\":" + scene.classId() + ",\"scope\":" + q(cell.party() ? "PARTY" : "SOLO") + ",\"family\":" + q(family.name()) + ",\"split\":" + q(slot.split()) + ",\"experimental\":" + slot.experimental() + ",\"afterCut\":" + cut + ",\"outcome\":" + (episode == null ? "null" : q(episode.outcome())) + ",\"exclusion\":" + q(exclusion) + ",\"opportunities\":" + selections.size() + ",\"paidRawEffects\":" + receipts.size() + ",\"totalMillis\":" + (System.nanoTime() - started) / 1e6 + ",\"policyDiagnostics\":" + tactical.diagnostics() + ",\"policyTiming\":" + q(tactical.timing()) + "}");
			System.out.println("AUTONOMOUS_EPISODE slot=" + slot.ordinal() + " class=" + scene.classId() + " scope=" + (cell.party() ? "PARTY" : "SOLO") + " split=" + slot.split() + " cut=" + cut + " effects=" + receipts.size() + " exclusion=" + exclusion);
			final var paid = new ArrayList<String>();
			if (evaluation && episode != null)
			{
				for (var receipt : receipts)
				{
					final var time = timings.get(receipt.eventKey()); final var action = receipt.attempt().selected();
					final boolean lethal = time != null && time.lethal() && action.family() == LegalActionV3.Family.DAMAGE && episode.kills().stream().anyMatch(k -> k.targetObjectId() == action.targetObjectId() && k.spawnGeneration() == action.targetEpoch());
					final boolean valid = evaluationTiming(time, runtime.outcomeCutNanos(), runtime.outcomeTerminalNanos(), lethal);
					if (valid) { paid.add(receipt.eventKey()); }
					immutable(work.resolve(receipt.eventKey() + ".evaluation-timing.json"), "{\"eventKey\":" + q(receipt.eventKey()) + ",\"included\":" + valid + ",\"cutNanos\":" + runtime.outcomeCutNanos() + ",\"terminalNanos\":" + runtime.outcomeTerminalNanos()
						+ ",\"hpStartedNanos\":" + (time == null ? "null" : time.hpStart()) + ",\"hpFinishedNanos\":" + (time == null ? "null" : time.hpEnd()) + ",\"mpStartedNanos\":" + (time == null ? "null" : time.mpStart()) + ",\"mpFinishedNanos\":" + (time == null ? "null" : time.mpEnd()) + ",\"linkedLethal\":" + lethal + ",\"trainingCredit\":false}");
				}
			}
			final var arm = new EvaluationArm(physical, actors, targetIdentity, cut && episode != null && episode.outcome().equals("COMPLETED"), List.copyOf(paid));
			if (evaluation) { immutable(work.resolve("evaluation-arm.tsv"), encodeArm(arm)); }
			return arm;
		}
		finally { if (target != null) { target.deleteMe(); } TerminalRewardReceiptV1.nativeTimingObserver(_ -> { }); final long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos(); while (!policy.stop() && System.nanoTime() < deadline) { Thread.sleep(50); } }
	}
	public static void main(String[] args)
	{
		try
		{
			if (args.length == 1 && args[0].equals("source-contract")) { sourceContract(); return; }
			if (args.length == 2 && args[0].equals("diagnose-cohort")) { diagnoseCohort(Path.of(args[1])); return; }
			if (args.length != 2 || !List.of("inspect", "prepare", "admit-eval", "checkpoint").contains(args[0])) { throw new IllegalArgumentException("Ожидается inspect/prepare/admit-eval/checkpoint <собственная установка>."); }
			if (args[0].equals("prepare") || args[0].equals("admit-eval")) { prepareSources(Path.of(args[1]), args[0].equals("admit-eval")); } else if (args[0].equals("checkpoint")) { checkpoint(Path.of(args[1])); } else { inspect(Path.of(args[1])); }
			if (args[0].equals("admit-eval")) { System.exit(0); }
		}
		catch (Exception failure)
		{
			// JDBC/vendor messages can include credentials. Keep the normal safe worker diagnostic contract.
			final String message = failure.getMessage();
			System.err.println("AUTONOMOUS_NATIVE_FAILED " + (message != null && message.matches("AUTONOMOUS_[A-Z0-9_]{1,80}") ? message : failure.getClass().getSimpleName()));
			java.util.Arrays.stream(failure.getStackTrace()).filter(frame -> frame.getClassName().startsWith("org.l2jmobius."))
				.limit(8).forEach(frame -> System.err.println("AUTONOMOUS_FAILURE_SITE " + frame));
			System.exit(2);
		}
	}
	/** Bounded native Player ingress before GameServer collection; originals are never loaded. */
	private static void admitEvaluation(Path root, Path directory, TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority) throws Exception
	{
		if (!config.text("autonomous009EvaluationOnly", "false").equals("true") || !config.text("autonomous009Exploration", "true").equals("false"))
		{ throw new IllegalStateException("AUTONOMOUS_EVAL_ONLY_REQUIRED"); }
		final String before = PhantomLearning005Native.trainingState(root, true); final var store = new TacticalStoreV3(root);
		final var model = store.candidate(config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE));
		if (model == null || !model.sha().equals(config.text("autonomous009ModelSha", ""))) { throw new IllegalStateException("AUTONOMOUS_EVAL_MODEL_CHANGED"); }
		final var settings = PhantomTrainingActors.settings(root); final var stock = new LearningStore(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"), root.resolve("game")).accepted(1);
		if (stock == null) { throw new IllegalStateException("AUTONOMOUS_STOCK_OBSERVER_UNAVAILABLE"); }
		final var sources = evaluationSources(root, directory, discoverSources(root, directory, config, authority, () -> requested(directory)), config);
		final var cells = planCells(directory, sources, config, authority, () -> requested(directory)); requireEvaluationCoverage(cells);
		final var selected = new ArrayList<Cell>();
		selected.add(cells.stream().filter(c -> evaluationDirection(c).equals("SOLO_DAMAGE")).findFirst().orElseThrow());
		selected.add(cells.stream().filter(c -> evaluationDirection(c).equals("PARTY_HEAL")).findFirst()
			.orElseGet(() -> cells.stream().filter(c -> evaluationDirection(c).equals("PARTY_DAMAGE")).findFirst().orElseThrow()));
		for (var cell : selected)
		{
			PhantomTrainingScenarios.requirePlanningActive(() -> requested(directory)); final String id = hash("ADMISSION/" + cell.key());
			final Path work = directory.resolve(id); Files.createDirectory(work);
			collectEpisode(root, directory, config, authority, store, model, stock.model(), directory, work, id,
				new AutonomousTrainingV1.CohortSlot(selected.indexOf(cell), cell.key(), hash(id), "EVAL", false), cell, "STOCK", true);
			if (!Files.exists(work.resolve("admission-only.json"))) { throw new IllegalStateException("AUTONOMOUS_BLOCKED_EVAL_NO_LAWFUL_SCENE"); }
		}
		if (!before.equals(PhantomLearning005Native.trainingState(root, true))) { throw new IllegalStateException("AUTONOMOUS_EVAL_CHANGED_TRAINING_STATE"); }
		immutable(directory.resolve("admission.complete.json"), "{\"admittedScenes\":2,\"directions\":[" + selected.stream().map(c -> q(evaluationDirection(c))).collect(java.util.stream.Collectors.joining(","))
			+ "],\"modelVersion\":" + model.id() + ",\"modelSha\":" + q(model.sha()) + ",\"trainingStateSha\":" + q(hash(before)) + ",\"afterCut\":false,\"gameServerStarted\":false,\"trainingStateUnchanged\":true,\"trainingCredit\":false}");
		System.out.println("AUTONOMOUS_EVAL_ADMISSION_GREEN scenes=2 gameServerStarted=false afterCut=false session=" + directory);
	}
	private static void prepareSources(Path installation, boolean admissionOnly) throws Exception
	{
		final var root = PhantomTrainingActors.physicalRoot(installation);
		final var config = TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini"));
		new TrainingStore(root).requireStopped();
		final var settings = PhantomTrainingActors.settings(root);
		final var sourceIds = new ArrayList<int[]>();
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			connection.setReadOnly(true);
			PhantomTrainingSql.requireIdentity(connection, root, config.integer("sqlPort", 33327, 33000, 65535), TrainingStore.SCHEMA, "learning001@127.0.0.1");
			try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*),SUM(CASE WHEN account_name IS NULL OR account_name<>'learn001fixture' OR accesslevel<>0 THEN 1 ELSE 0 END) FROM characters"))
			{
				if (!rows.next() || rows.getLong(1) > config.integer("maxStoredActors", 4096, 32, 32768) - 64 || rows.getLong(2) != 0)
				{ throw new IllegalStateException("AUTONOMOUS_SOURCE_SCOPE_OR_CAPACITY"); }
			}
			try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT charId,classid FROM characters WHERE account_name='learn001fixture' AND char_name NOT LIKE 'AT%' ORDER BY classid,charId"))
			{ while (rows.next()) { sourceIds.add(new int[] { rows.getInt(1), rows.getInt(2) }); } }
		}
		final Path directory = admissionOnly ? new TacticalStoreV3(root).autonomousDirectory().resolve("admission") : root.resolve("autonomous/v1/ingress"); requirePhysicalDirectory(root, directory);
		final Path session = directory.resolve(UUID.randomUUID().toString().replace("-", "")); Files.createDirectory(session);
		final var records = new ArrayList<String>();
		final var sourceIndex = new ArrayList<String>();
		sourceIndex.add("sourceActorId\tsourceProfileId\tsourceSnapshotSha\tsourcePhysicalSha\tinventorySha\tclassId\tclassIndex\tlevel");
		final var attempts = new HashMap<Integer, Integer>(); final var accepted = new HashMap<Integer, Integer>();
		ConfigLoader.init();
		if (PhantomPlayersConfig.isEnabled()) { throw new IllegalStateException("AUTONOMOUS_MASTER_MUST_BE_OFF"); }
		DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString());
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure();
			org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
			try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				if (admissionOnly) { admitEvaluation(root, session, config, authority); return; }
				for (var header : sourceIds)
				{
					final int sourceId = header[0], classId = header[1];
					if (records.size() >= 64 || attempts.getOrDefault(classId, 0) >= 8 || accepted.getOrDefault(classId, 0) >= 2) { continue; }
					attempts.merge(classId, 1, Integer::sum);
					PhantomTrainingActors.SavedActor image;
					final Long sourceProfile;
					try (var connection = DatabaseFactory.getConnection())
					{
						image = PhantomTrainingActors.readSavedActor(connection, sourceId);
						try (var query = connection.prepareStatement("SELECT profile_id FROM phantom_profiles WHERE character_object_id=?"))
						{ query.setInt(1, sourceId); try (var rows = query.executeQuery()) { sourceProfile = rows.next() ? rows.getLong(1) : null; } }
					}
					final Path slot = session.resolve(Integer.toString(sourceId)); Files.createDirectory(slot);
					Files.writeString(slot.resolve("source.json"), image.json() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
					Files.writeString(slot.resolve("reserved.json"), "{\"sourceSnapshotSha\":" + q(image.sha()) + ",\"sourceProfileId\":" + sourceProfile + ",\"sourceProfileProvenance\":" + q(sourceProfile == null ? "UNKNOWN" : "OWNED_SQL_PROFILE_BINDING") + ",\"phase\":\"RESERVED\",\"trainingCredit\":false}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
					org.l2jmobius.gameserver.model.actor.Player player = null;
					try
					{
						player = reproduce(config, image);
						final var inventory = PhantomSkillInventory.acquired(player, authority.progression());
						final var pools = new ArrayList<AutonomousTrainingV1.ActionPool>();
						for (var mode : PhantomCombatMode.values())
						{
							final var actor = player;
							final var skills = inventory.abilities().stream().filter(a -> a.category() == PhantomSkillInventory.Category.SINGLE_TARGET_DAMAGE
								&& a.support().contains(PhantomSkillInventory.Support.SUPPORTED_FOR_TRAIN)
								&& PhantomCombatSkillSafety.supports(actor.getKnownSkill(a.skill().skillId()), mode, false)).map(PhantomSkillInventory.Ability::skill).toList();
							if (!skills.isEmpty()) { pools.add(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, mode, skills)); }
						}
						final var heals = inventory.abilities().stream().filter(a -> a.category() == PhantomSkillInventory.Category.HEAL
							&& a.support().contains(PhantomSkillInventory.Support.SUPPORTED_FOR_TRAIN)).map(PhantomSkillInventory.Ability::skill).toList();
						if (!heals.isEmpty()) { pools.add(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.HEAL, null, heals)); }
						final var seed = new AutonomousTrainingV1.ActorSeed(image.sha(), classId, player.getLevel(), player.getClassIndex(), inventory.abilities().stream().map(PhantomSkillInventory.Ability::skill).toList(), pools);
						Files.writeString(slot.resolve("inventory.json"), inventoryJson(inventory) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
						Files.writeString(slot.resolve("pools.tsv"), "family\tmode\tskills\n" + pools.stream().map(pool -> pool.family() + "\t" + (pool.mode() == null ? "PARTY" : pool.mode().name()) + "\t"
							+ pool.skills().stream().map(skill -> skill.skillId() + ":" + skill.skillLevel()).collect(java.util.stream.Collectors.joining(","))).collect(java.util.stream.Collectors.joining("\n")) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
						final String record = "{\"sourceActorId\":" + sourceId + ",\"sourceProfileId\":" + sourceProfile + ",\"incarnationId\":" + player.getObjectId() + ",\"sourceSnapshotSha\":" + q(seed.sourceSnapshotSha())
							+ ",\"sourcePhysicalSha\":" + q(image.physicalFingerprint()) + ",\"classId\":" + classId + ",\"level\":" + seed.level() + ",\"actualAcquiredSkills\":" + inventory.abilities().size() + ",\"inventorySha\":" + q(inventory.fingerprint())
							+ ",\"poolCount\":" + pools.size() + ",\"nativeReady\":\"TARGET_OWNER_ADMISSION_REQUIRED\",\"trainingCredit\":false,\"status\":\"ACQUIRED_SNAPSHOT_VERIFIED\"}";
						Files.writeString(slot.resolve("result.json"), record + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); records.add(record); accepted.merge(classId, 1, Integer::sum);
						sourceIndex.add(sourceId + "\t" + (sourceProfile == null ? "" : sourceProfile) + "\t" + seed.sourceSnapshotSha() + "\t" + image.physicalFingerprint() + "\t" + inventory.fingerprint() + "\t" + classId + "\t" + seed.classIndex() + "\t" + seed.level());
					}
					catch (IllegalStateException refused)
					{
						final String reason = refused.getMessage() != null && refused.getMessage().matches("AUTONOMOUS_[A-Z0-9_]+") ? refused.getMessage() : "AUTONOMOUS_NATIVE_SOURCE_REFUSED";
						final String record = "{\"sourceActorId\":" + sourceId + ",\"classId\":" + classId + ",\"sourceSnapshotSha\":" + q(image.sha()) + ",\"status\":\"REJECTED\",\"reason\":" + q(reason) + ",\"trainingCredit\":false}";
						Files.writeString(slot.resolve("result.json"), record + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); records.add(record);
					}
					finally { if (player != null) { player.stopAllTasks(); player.deleteMe(); } }
				}
			}
			final String indexBody = String.join("\n", sourceIndex) + "\n";
			Files.writeString(session.resolve("source-index.tsv"), indexBody, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			Files.writeString(session.resolve("report.json"), "{\"schema\":\"AUTONOMOUS_NATIVE_ACQUIRED_INGRESS_V1\",\"sourceIndexSha\":" + q(hash(indexBody))
				+ ",\"sourceJarSha\":" + q(NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar"))))
				+ ",\"gameServerStarted\":false,\"trainingCredit\":false,\"records\":[" + String.join(",", records) + "]}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("AUTONOMOUS_SOURCE_PREPARED accepted=" + accepted.values().stream().mapToInt(Integer::intValue).sum() + " attempted=" + records.size() + " session=" + session.getFileName() + " gameServerStarted=false trainingCredit=false");
		}
		finally { ThreadPool.shutdown(); DatabaseFactory.close(); }
	}
	private static String inventoryJson(PhantomSkillInventory.Snapshot inventory)
	{
		final var rows = new ArrayList<String>();
		for (var a : inventory.abilities())
		{
			rows.add("{\"id\":" + a.skill().skillId() + ",\"canonicalLevel\":" + a.skill().skillLevel() + ",\"enchantRoute\":" + a.enchantRoute().value() + ",\"enchantLevel\":" + a.enchantLevel().value()
				+ ",\"category\":" + q(a.category().name()) + ",\"support\":[" + a.support().stream().map(value -> q(value.name())).collect(java.util.stream.Collectors.joining(",")) + "]"
				+ ",\"targetType\":" + q(a.targetType()) + ",\"castRange\":" + a.castRange().value() + ",\"effectRange\":" + a.effectRange().value() + ",\"areaRadius\":" + a.areaRadius()
				+ ",\"maximumTargets\":" + a.maximumTargets().value() + ",\"mpCost\":" + a.mpCost() + ",\"hpCost\":" + a.hpCost() + ",\"itemId\":" + a.itemId() + ",\"itemCount\":" + a.itemCount()
				+ ",\"charges\":" + a.charges() + ",\"souls\":" + a.souls() + ",\"castMillis\":" + a.castMillis() + ",\"reuseMillis\":" + a.reuseMillis() + ",\"magic\":" + a.magic() + ",\"physical\":" + a.physical()
				+ ",\"element\":" + a.element() + ",\"elementPower\":" + a.elementPower() + ",\"power\":" + a.power() + ",\"abnormalSeconds\":" + a.abnormalSeconds() + ",\"abnormalType\":" + q(a.abnormalType())
				+ ",\"abnormalLevel\":" + a.abnormalLevel() + ",\"castConditionsPresent\":" + a.conditionsPresent() + ",\"effects\":[" + a.effects().stream().map(PhantomAutonomousTrainingNative::q).collect(java.util.stream.Collectors.joining(",")) + "]"
				+ ",\"definitionSources\":" + (a.definitionSources().known() ? "[" + a.definitionSources().value().stream().map(PhantomAutonomousTrainingNative::q).collect(java.util.stream.Collectors.joining(",")) + "]" : "null") + ",\"acquisitionOrigin\":null}");
		}
		return "{\"schema\":" + q(PhantomSkillInventory.VERSION) + ",\"classId\":" + inventory.classId() + ",\"classIndex\":" + inventory.classIndex() + ",\"catalogHash\":" + q(inventory.catalogHash())
			+ ",\"fingerprint\":" + q(inventory.fingerprint()) + ",\"readiness\":\"TARGET_OWNER_ADMISSION_REQUIRED\",\"abilities\":[" + String.join(",", rows) + "]}";
	}
	private static void requirePhysicalDirectory(Path root, Path directory) throws Exception
	{
		for (Path part = directory; part != null && part.startsWith(root); part = part.getParent())
		{ if (Files.exists(part, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !part.equals(part.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_SOURCE_REPARSE_PATH"); } }
		Files.createDirectories(directory);
		if (!directory.equals(directory.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_SOURCE_REPARSE_PATH"); }
	}
	private static void sourceContract()
	{
		final var geometryFirst = java.util.stream.IntStream.range(0, 14).boxed().toList();
		final var lawful = PhantomTrainingScenarios.lawfulPage(geometryFirst.stream(), value -> value >= 12, 12, () -> false);
		if (!lawful.equals(List.of(12, 13))) { throw new AssertionError("Geometric candidate cap starved later lawful native scenes"); }
		try { PhantomTrainingScenarios.lawfulPage(geometryFirst.stream(), value -> true, 12, () -> true); throw new AssertionError("Cancelled planning returned a sealable partial page"); }
		catch (IllegalStateException expected) { if (!"AUTONOMOUS_PLANNING_REQUESTED_DRAIN".equals(expected.getMessage())) { throw expected; } }
		final var a = new EvaluationArm("same", List.of(new PhantomLearning005Evaluation.Incarnation(1, 1)), new PhantomLearning005Evaluation.Incarnation(2, 1), true, List.of("event-a"));
		final var b = new EvaluationArm("same", List.of(new PhantomLearning005Evaluation.Incarnation(3, 1)), new PhantomLearning005Evaluation.Incarnation(4, 1), true, List.of("event-b"));
		if (!pairStatus(List.of(decodeArm(encodeArm(a)), b)).equals("MATCHED_COMPLETED") || !pairStatus(List.of(decodeArm(encodeArm(a)))).equals("INCOMPLETE_ARMS"))
		{ throw new AssertionError("Interrupted EVAL lost its immutable settled arm or counted one arm as a pair"); }
		if (!pairStatus(List.of(a, b)).equals("MATCHED_COMPLETED") || !pairStatus(List.of(a)).equals("INCOMPLETE_ARMS")
			|| !pairStatus(List.of(a, new EvaluationArm("risk-differs", b.actors(), b.target(), true, b.events()))).equals("PRE_CUT_FACTS_DIFFER")
			|| !pairStatus(List.of(a, a)).equals("REUSED_OR_MISSING_INCARNATION"))
		{ throw new AssertionError("EVAL pair count accepted a single arm, unmatched native policy or reused incarnation"); }
		final var risk = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.DEFAULT_TACTICAL_RISK;
		final int expectedRisk = risk.limitPercent(11, false); long firstProfile = 12;
		while (risk.limitPercent(firstProfile, false) == expectedRisk) { firstProfile++; }
		final long matchedProfile = matchingRiskProfile(firstProfile, expectedRisk, 32768);
		if (matchedProfile < firstProfile || matchedProfile >= firstProfile + 512 || risk.limitPercent(matchedProfile, false) != expectedRisk)
		{ throw new AssertionError("Fresh evaluation profile changed the pinned native tactical risk"); }
		try { matchingRiskProfile(firstProfile, expectedRisk, (int) firstProfile); throw new AssertionError("Risk matching exceeded the stored profile cap"); }
		catch (IllegalStateException expected) { if (!expected.getMessage().equals("AUTONOMOUS_EVAL_RISK_PROFILE_UNAVAILABLE")) { throw expected; } }
		final var paidTiming = new TerminalRewardReceiptV1.NativeTiming("paid", 0, 110, 140, 105, 130, false);
		final var lateTiming = new TerminalRewardReceiptV1.NativeTiming("late", 0, 110, 160, 105, 130, true);
		if (!evaluationTiming(paidTiming, 100, 150, false) || evaluationTiming(lateTiming, 100, 150, false)
			|| !evaluationTiming(lateTiming, 100, 150, true) || evaluationTiming(null, 100, 150, false))
		{ throw new AssertionError("EVAL counted an unproven or late effect without the canonical linked lethal exception"); }
		final var offsets = PhantomTrainingScenarios.acquiredTargetOffsets();
		if (offsets.size() > 400 || !offsets.contains(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(600, 200, 0, 0))
			|| offsets.stream().anyMatch(p -> Math.hypot(p.x(), p.y()) > org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.MAXIMUM_TARGET_DISTANCE))
		{ throw new AssertionError("Sparse placement page missed an in-bound lawful pocket or exceeded the ordinary acquisition bound"); }
		final var point = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(0, 0, 0, 0);
		final var outsideTopology = new PhantomTrainingScenarios.ClanNeighbor(400, 0, World.WORLD_Z_MAX + 1, 0, Set.of(1));
		if (PhantomTrainingScenarios.factionClear(Set.of(1), 300, 20, point, List.of(outsideTopology))
			|| PhantomTrainingScenarios.inWorldBounds(0, 0, World.WORLD_Z_MAX + 1)
			|| !PhantomTrainingScenarios.inWorldBounds(World.WORLD_X_MIN, World.WORLD_Y_MAX, World.WORLD_Z_MIN))
		{ throw new AssertionError("Unrelated raw World NPC coordinates crashed or weakened the topology admission boundary"); }
		final var nearClan = new PhantomTrainingScenarios.ClanNeighbor(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(400, 0, 0, 0), Set.of(1));
		if (PhantomTrainingScenarios.factionClear(Set.of(1), 300, 20, point, List.of(nearClan)))
		{ throw new AssertionError("A nearby same-clan NPC crossed the native help-range reserve"); }
		if (!PhantomTrainingScenarios.factionClear(Set.of(1), 300, 20, point, List.of(new PhantomTrainingScenarios.ClanNeighbor(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(1000, 0, 0, 0), Set.of(1)))))
		{ throw new AssertionError("A distant clan member prevented an isolated native target"); }
		if (!PhantomTrainingScenarios.factionClear(Set.of(1), 300, 20, point, List.of(new PhantomTrainingScenarios.ClanNeighbor(point, Set.of(2))))
			|| !PhantomTrainingScenarios.factionClear(Set.of(), 300, 20, point, List.of(nearClan)))
		{ throw new AssertionError("Unrelated or absent clans were treated as assisting NPCs"); }
		final var targetRefs = List.of(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(7001, 1),
			new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(7002, 1),
			new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(7003, 1));
		final var seed = new AutonomousTrainingV1.ActorSeed("a".repeat(64), 103, 77, 0, targetRefs,
			List.of(new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.RANGED_MAGIC, targetRefs)));
		final var proposed = new AutonomousTrainingV1.Proposal(seed, seed.supported().getFirst(), "shortlist/" + seed.sourceSnapshotSha() + "/" + seed.supported().getFirst().key(), AutonomousTrainingV1.shortlist(targetRefs, 7, 4));
		final var bound = targetBoundProposal(proposed, targetRefs.subList(1, 3)).orElseThrow();
		if (bound.shortlist().candidates().size() != 2 || bound.shortlist().candidates().contains(targetRefs.getFirst()) || bound.pool().skills().size() != 3)
		{ throw new AssertionError("Target-bound shortlist lost the full acquired denominator or retained a refused action"); }
		if (targetBoundProposal(proposed, targetRefs.subList(0, 1)).isPresent()) { throw new AssertionError("One target-bound action became an experimental choice"); }
		final var magic = new org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.Mechanics(
			org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.DamageKind.MAGIC, 100, 1000, 0, false, 0, 74, (byte) -1, 0, 1);
		if (!org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.magicLevelReady(magic, 77, 74)
			|| org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.magicLevelReady(magic, 77, 76))
		{ throw new AssertionError("Canonical magic-level planning boundary changed"); }
		try
		{
			org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend.class.getConstructor(PhantomProfileRepository.class,
				org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.class, org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService.class);
		}
		catch (NoSuchMethodException missing) { throw new AssertionError("Legacy public Party constructor ABI changed", missing); }
		if (!Boolean.getBoolean("phantom.learning.train"))
		{
			try { new org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend(null, null, null, _ -> List.of()); throw new AssertionError("Ordinary production owner accepted a TRAIN source"); }
			catch (IllegalArgumentException expected) { }
		}
		final var soloBand = PhantomTrainingScenarios.acquiredLevelBand(List.of(73));
		if (!soloBand.contains(69) || !soloBand.contains(77) || soloBand.contains(78)) { throw new AssertionError("SOLO source rejected or expanded beyond the canonical member band"); }
		try { PhantomTrainingScenarios.acquiredLevelBand(List.of(1, 85)); throw new AssertionError("Incompatible Party band admitted"); }
		catch (IllegalStateException expected) { }
		final var schedule = AutonomousTrainingV1.cohortSlots(List.of("cell-a", "cell-b", "cell-c", "cell-d"));
		if (schedule.size() != 48 || schedule.stream().filter(s -> s.experimental()).count() != 16
			|| schedule.stream().filter(s -> s.split().equals("TUNING")).count() != 8 || schedule.stream().filter(s -> s.split().equals("AUDIT")).count() != 8)
		{ throw new AssertionError("Cohort lost the frozen minimum evidence budget"); }
		for (String cell : List.of("cell-a", "cell-b", "cell-c", "cell-d"))
		{
			final var controls = schedule.stream().filter(s -> s.cell().equals(cell) && s.split().equals("TRAIN") && !s.experimental()).toList();
			if (controls.size() != 4 || controls.stream().map(s -> s.block()).distinct().count() != 4)
			{ throw new AssertionError("Leave-own-block-out requires four independent native controls per cell"); }
		}
		try { AutonomousTrainingV1.cohortSlots(List.of("same", "same")); throw new AssertionError("Repeated cohort cell admitted"); }
		catch (IllegalArgumentException expected) { }
		final var reserve = AutonomousTrainingV1.cohortSlots(List.of("reserve-a", "reserve-b", "reserve-c"), 51);
		if (reserve.size() != 51 || reserve.stream().filter(s -> s.experimental()).count() != 18
			|| reserve.stream().filter(s -> s.split().equals("TUNING")).count() != 9 || reserve.stream().filter(s -> s.split().equals("AUDIT")).count() != 9
			|| AutonomousTrainingV1.cohortCellLimit(51) != 3 || AutonomousTrainingV1.cohortCellLimit(48) != 4)
		{ throw new AssertionError("Reserve allocation weakened the unchanged16/8/8 evidence thresholds or exceeded its budget"); }
		for (var paid : reserve.stream().filter(s -> s.experimental()).toList())
		{
			final var controls = reserve.stream().filter(s -> s.cell().equals(paid.cell()) && s.split().equals("TRAIN") && !s.experimental()).toList();
			if (controls.size() != 5) { throw new AssertionError("Reserve requires five independent control blocks"); }
			for (var missing : controls)
			{
				if (controls.stream().filter(s -> s != missing && !s.block().equals(paid.block())).map(s -> s.block()).distinct().count() < 3)
				{ throw new AssertionError("One lost native control must not erase the other paid comparisons"); }
			}
		}
		final var skills = new PhantomTrainingActors.SavedTable("character_skills", "charId",
			java.util.List.of("charId", "skill_id", "skill_level", "class_index"), java.util.List.of(4, 4, 4, 4),
			java.util.List.of(java.util.List.of("11", "200", "101", "0"), java.util.List.of("11", "201", "3", "1")));
		final var remapped = skills.forIncarnation(11, 22, "ATexample", Map.of());
		if (!remapped.rows().equals(java.util.List.of(java.util.List.of("22", "200", "101", "0"), java.util.List.of("22", "201", "3", "1"))))
		{ throw new AssertionError("Snapshot reproduction changed ranks/enchant/subclass or retained source identity"); }
		final var elemental = new PhantomTrainingActors.SavedTable("item_elementals", "itemId",
			java.util.List.of("itemId", "elemType", "elemValue"), java.util.List.of(4, 4, 4), java.util.List.of(java.util.List.of("30", "1", "120")));
		if (!elemental.forIncarnation(11, 22, "ATexample", Map.of(30, 40)).rows().getFirst().equals(java.util.List.of("40", "1", "120")))
		{ throw new AssertionError("Item attributes lost exact state or source item identity survived cloning"); }
		try { elemental.forIncarnation(11, 22, "ATexample", Map.of()); throw new AssertionError("Unmapped item foreign key admitted"); }
		catch (IllegalArgumentException expected) { }
		try { skills.forIncarnation(10, 22, "ATexample", Map.of()); throw new AssertionError("Foreign actor row admitted"); }
		catch (IllegalArgumentException expected) { }
		final var items = new PhantomTrainingActors.SavedTable("items", "owner_id", List.of("owner_id", "object_id", "item_id", "enchant_level", "count"), List.of(4, 4, 4, 4, 4), List.of(List.of("11", "30", "1000", "5", "1")));
		final var physical = new PhantomTrainingActors.SavedActor(11, List.of(skills, items, elemental));
		final var replica = new PhantomTrainingActors.SavedActor(22, physical.tables().stream().map(table -> table.forIncarnation(11, 22, "ATexample", Map.of(30, 40))).toList());
		if (!physical.physicalFingerprint().equals(replica.physicalFingerprint()) || physical.sha().equals(replica.sha()))
		{ throw new AssertionError("Comparison facts retained incarnation IDs or dispatch binding lost them"); }
		final var alteredElement = new PhantomTrainingActors.SavedTable("item_elementals", "itemId", elemental.columns(), elemental.types(), List.of(List.of("30", "1", "121")));
		if (physical.physicalFingerprint().equals(new PhantomTrainingActors.SavedActor(11, List.of(skills, items, alteredElement)).physicalFingerprint()))
		{ throw new AssertionError("Comparison cell ignored actual item elemental state"); }
		final var refs = List.of(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(200, 101), new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(201, 2));
		final var pool = new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.RANGED_MAGIC, refs);
		final var actor = new AutonomousTrainingV1.ActorSeed("a".repeat(64), 88, 77, 0, refs, List.of(pool));
		final var proposal = AutonomousTrainingV1.nextCandidate(List.of(actor), Map.of(), Map.of(), 0, 183).orElseThrow();
		final var helperActor = new AutonomousTrainingV1.ActorSeed("b".repeat(64), 94, 77, 0, refs, List.of(pool));
		final var mixedCell = new Cell("contract", proposal, List.of(new Source(null, null, helperActor, "NONE", 0), new Source(null, null, actor, "NONE", 0)), null, 0, 0, 0);
		if (mixedCell.learnerIndex() != 1) { throw new AssertionError("Independent farming leader stole the learner binding"); }
		final var resolver = PhantomTrainingScenarios.frozenResolver(actor.classId(), pool.mode(), proposal.shortlist().candidates(), () -> null);
		final var loadout = resolver.resolve(actor.classId(), pool.mode(), id -> refs.stream().filter(ref -> ref.skillId() == id).mapToInt(ref -> ref.skillLevel()).findFirst().orElse(0), _ -> true, false, 4).orElseThrow();
		if (!loadout.selectedSkills().stream().map(value -> value.skillId() + ":" + value.skillLevel()).toList().equals(List.of("200:101", "201:2")))
		{ throw new AssertionError("Per-episode resolver lost the exact acquired shortlist"); }
		final String identity = PhantomTrainingScenarios.acquiredSceneId(proposal, "100@fixture", 100, 1, 2, 3, "S", 0);
		final var changed = List.of(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(200, 102), refs.get(1));
		final var changedPool = new AutonomousTrainingV1.ActionPool(LegalActionV3.Family.DAMAGE, PhantomCombatMode.RANGED_MAGIC, changed);
		final var changedActor = new AutonomousTrainingV1.ActorSeed("a".repeat(64), 88, 77, 0, changed, List.of(changedPool));
		final var changedProposal = AutonomousTrainingV1.nextCandidate(List.of(changedActor), Map.of(), Map.of(), 0, 183).orElseThrow();
		if (identity.equals(PhantomTrainingScenarios.acquiredSceneId(changedProposal, "100@fixture", 100, 1, 2, 3, "S", 0)))
		{ throw new AssertionError("Scene identity lost exact acquired enchant version"); }
		try
		{
			new AutonomousTrainingV1.Proposal(actor, pool, proposal.shortlistKey(), new AutonomousTrainingV1.Shortlist(List.of(new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(999, 1)), 1, false));
			throw new AssertionError("Forged scene proposal bypassed the acquired pool");
		}
		catch (IllegalArgumentException expected) { }
		System.out.println("AUTONOMOUS_SOURCE_CONTRACT_GREEN");
	}

	private static void inspect(Path installation) throws Exception
	{
		final var root = PhantomTrainingActors.physicalRoot(installation);
		final var config = TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini"));
		final var guard = new TrainingStore(root);
		guard.requireStopped();
		final var settings = PhantomTrainingActors.settings(root);
		final var actors = new ArrayList<String>();
		final var sourceHeaders = new ArrayList<int[]>();
		final var perClass = new HashMap<Integer, long[]>();
		final var snapshots = new ArrayList<PhantomTrainingActors.SavedActor>();
		final var snapshotDiagnostics = new ArrayList<String>();
		final var sourceAttempts = new HashMap<Integer, Integer>();
		final var sourceAccepted = new HashMap<Integer, Integer>();
		long persistedSkills = 0;
		final int maximum = config.integer("maxStoredActors", 4096, 32, 32768);
		try (var connection = DriverManager.getConnection(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password")))
		{
			connection.setReadOnly(true);
			PhantomTrainingSql.requireIdentity(connection, root, config.integer("sqlPort", 33327, 33000, 65535), TrainingStore.SCHEMA, "learning001@127.0.0.1");
			connection.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
			connection.setAutoCommit(false);
			try (var query = connection.createStatement(); var rows = query.executeQuery("SELECT COUNT(*),SUM(CASE WHEN account_name IS NULL OR account_name<>'learn001fixture' OR accesslevel<>0 THEN 1 ELSE 0 END) FROM characters"))
			{
				if (!rows.next() || rows.getLong(1) > maximum || rows.getLong(2) != 0)
				{ throw new IllegalStateException("AUTONOMOUS_SOURCE_SCOPE_OR_CAPACITY"); }
			}
			// One bounded batch query; persisted rows are not advertised as Player.getAllSkills().
			final String sql = "SELECT c.charId,c.char_name,c.classid,c.base_class,c.level,c.transform_id,c.nobless,c.clanid,"
				+ "COALESCE(k.skill_rows,0) AS persisted_skill_rows,COALESCE(s.subclass_rows,0) AS subclass_rows "
				+ "FROM characters c LEFT JOIN (SELECT charId,COUNT(*) skill_rows FROM character_skills GROUP BY charId) k ON k.charId=c.charId "
				+ "LEFT JOIN (SELECT charId,COUNT(*) subclass_rows FROM character_subclasses GROUP BY charId) s ON s.charId=c.charId "
				+ "WHERE c.account_name='learn001fixture' ORDER BY c.charId";
			try (var query = connection.createStatement(); var rows = query.executeQuery(sql))
			{
				while (rows.next())
				{
					if (actors.size() >= maximum) { throw new IllegalStateException("AUTONOMOUS_SOURCE_CAPACITY"); }
					final var fields = new ArrayList<String>();
					final var metadata = rows.getMetaData();
					for (int column = 1; column <= metadata.getColumnCount(); column++)
					{ final String value = rows.getString(column); fields.add(q(metadata.getColumnLabel(column)) + ":" + (value == null ? "null" : q(value))); }
					final String body = "{" + String.join(",", fields) + "}";
					final int classId = rows.getInt("classid");
					if (rows.wasNull() || PlayerClass.getPlayerClass(classId) == null) { throw new IllegalStateException("AUTONOMOUS_SOURCE_UNKNOWN_CLASS"); }
					final long skills = rows.getLong("persisted_skill_rows");
					final var count = perClass.computeIfAbsent(classId, _ -> new long[2]); count[0]++; count[1] += skills; persistedSkills += skills;
                    if (!rows.getString("char_name").startsWith("AT")) { sourceHeaders.add(new int[] { rows.getInt("charId"), classId }); }
					actors.add("{\"rowReferenceSha\":" + q(hash(body)) + ",\"persistedFields\":" + body
						+ ",\"actualPlayerInventory\":null,\"nativeReadiness\":\"NOT_RUN\",\"sourceSnapshotComplete\":false}");
				}
			}
			// Bounded read-only saved images. This is still not native Player admission.
			for (var header : sourceHeaders)
			{
				final int id = header[0], classId = header[1];
				if (snapshots.size() >= 64 || sourceAccepted.getOrDefault(classId, 0) >= 2 || sourceAttempts.getOrDefault(classId, 0) >= 8) { continue; }
				sourceAttempts.merge(classId, 1, Integer::sum);
				final var snapshot = PhantomTrainingActors.readSavedActor(connection, id); snapshots.add(snapshot);
				String reason = "SAVED_IMAGE_REPRODUCIBLE_NATIVE_ADMISSION_REQUIRED";
				try { snapshot.requireReproducible(); sourceAccepted.merge(classId, 1, Integer::sum); }
				catch (IllegalStateException unavailable) { reason = unavailable.getMessage(); }
				snapshotDiagnostics.add("{\"sourceActorId\":" + id + ",\"classId\":" + classId + ",\"sourceSnapshotSha\":" + q(snapshot.sha())
					+ ",\"persistedCurrentClassSkills\":" + snapshot.learned().size() + ",\"actualPlayerInventory\":null,\"reason\":" + q(reason) + "}");
			}
			connection.rollback();
		}
		final var classes = new ArrayList<String>();
		for (int id : PhantomSkillInventory.classIds())
		{
			final var count = perClass.get(id);
			classes.add("{\"classId\":" + id + ",\"name\":" + q(PlayerClass.getPlayerClass(id).name())
				+ ",\"persistedActors\":" + (count == null ? 0 : count[0]) + ",\"persistedSkillRowsAllClassIndexes\":" + (count == null ? 0 : count[1])
				+ ",\"actualAcquiredSkillCount\":null,\"trainReadiness\":\"NOT_ASSESSED\",\"reason\":"
				+ q(count == null ? "NO_PERSISTED_SOURCE_ACTOR" : "NATIVE_ACQUIRED_SNAPSHOT_REQUIRED") + "}");
		}
		final var tactical = new org.l2jmobius.gameserver.phantoms.learning.TacticalStoreV3(root);
		final var parent = tactical.optimizationParent(); final var selected = tactical.candidate(parent.modelId());
		final String lineage = "{\"modelVersion\":" + parent.modelId() + ",\"selectedWeightsSha\":" + q(selected.sha()) + ",\"optimizerSha\":" + q(parent.sha())
			+ ",\"terminalWeightsSha\":" + q(parent.state().model().sha256()) + ",\"updates\":" + parent.state().updates() + ",\"epochs\":" + parent.state().completedEpochs() + ",\"readOnly\":true}";
		final String jar = NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar")));
		final String payload = "{\"schema\":\"AUTONOMOUS_PERSISTED_SOURCE_CENSUS_V1\",\"ownerSha\":" + q(hash(Files.readString(root.resolve("owner.txt"))))
			+ ",\"sourceJarSha\":" + q(jar) + ",\"createdUtc\":" + q(Instant.now().toString()) + ",\"classesDiscovered\":" + classes.size()
			+ ",\"classesWithPersistedActors\":" + perClass.size() + ",\"persistedActorCount\":" + actors.size() + ",\"persistedSkillRowsAllClassIndexes\":" + persistedSkills
			+ ",\"persistedSourceActorsExcludingIncarnations\":" + sourceHeaders.size() + ",\"coldOptimizerLineage\":" + lineage
			+ ",\"actualPlayerGetAllSkillsCount\":null,\"nativeReadiness\":\"NOT_RUN\",\"trainingCredit\":false,\"classes\":[" + String.join(",", classes)
			+ "],\"actors\":[" + String.join(",", actors) + "],\"savedImageDiagnostics\":[" + String.join(",", snapshotDiagnostics) + "]}";
		final Path directory = root.resolve("autonomous/v1/source-census");
		for (Path part = directory; part != null && part.startsWith(root); part = part.getParent())
		{
			if (Files.exists(part, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !part.equals(part.toRealPath()))
			{ throw new IllegalStateException("AUTONOMOUS_SOURCE_REPARSE_PATH"); }
		}
		Files.createDirectories(directory);
		if (!directory.equals(directory.toRealPath())) { throw new IllegalStateException("Census должен сохраняться в физическом собственном каталоге."); }
		for (var snapshot : snapshots)
		{
			final Path image = directory.resolve(snapshot.sha() + ".source.json");
			final String bytes = snapshot.json() + "\n";
			if (Files.exists(image))
			{ if (!image.equals(image.toRealPath()) || !Files.readString(image).equals(bytes)) { throw new IllegalStateException("AUTONOMOUS_SAVED_IMAGE_CHANGED"); } }
			else { Files.writeString(image, bytes, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
		}
		final String sha = hash(payload);
		final Path output = directory.resolve(sha + ".json");
		Files.writeString(output, payload + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		final Path pointer = directory.resolve("latest.json");
		if (Files.exists(pointer) && !pointer.equals(pointer.toRealPath())) { throw new IllegalStateException("Перенаправление census запрещено."); }
		final Path pending = directory.resolve("latest." + UUID.randomUUID() + ".pending");
		Files.writeString(pending, "{\"sha\":" + q(sha) + ",\"file\":" + q(output.getFileName().toString()) + "}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		Files.move(pending, pointer, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("AUTONOMOUS_SOURCE_CENSUS actors=" + actors.size() + " classes=" + perClass.size() + " persistedSkills=" + persistedSkills + " actualPlayerSnapshot=NOT_RUN sha=" + sha);
	}
	private static String q(String value) { return PhantomLearning005Evaluation.q(value); }
	/** Read existing native files only; never reserves SQL credit, fits weights or changes a cohort. */
	private static void diagnoseCohort(Path installation) throws Exception
	{
		final Path root = PhantomTrainingActors.physicalRoot(installation);
		final var config = TrainingConfig.load(root.resolve("TRAIN_CONFIG.ini"));
		final Path directory = root.resolve("autonomous/v1/campaigns").resolve(config.text("autonomous009Campaign", "")).resolve("cohorts").resolve(config.text("outcome006DatasetId", ""));
		if (!directory.equals(directory.toRealPath()) || Files.isSymbolicLink(directory) || !Files.isRegularFile(directory.resolve("collection.complete"))) { throw new IllegalArgumentException("Physical completed cohort required"); }
		final var samples = new ArrayList<org.l2jmobius.gameserver.phantoms.learning.ActionAdvantageV2.Sample>();
		final var excluded = new java.util.TreeMap<String, Long>();
		try (var paths = Files.list(directory.resolve("samples")))
		{
			final var selected = paths.filter(path -> path.getFileName().toString().matches("[a-f0-9]{64}\\.selection\\.bin")).sorted().toList();
			if (selected.size() > 512) { throw new IllegalArgumentException("Bounded native selections required"); }
			for (var path : selected)
			{
				if (!path.equals(path.toRealPath()) || Files.size(path) > 1000000) { throw new IllegalArgumentException("Physical bounded selection required"); }
				final var selection = TrainExplorationV1.Selection.restore(Files.readAllBytes(path));
				if (!path.getFileName().toString().equals(selection.episode() + ".selection.bin")) { throw new IllegalArgumentException("Native selection identity changed"); }
				final Path terminal = directory.resolve(selection.episode()).resolve("terminal.bin");
				if (!selection.fallback().isEmpty() || !Files.exists(terminal)) { excluded.merge("FALLBACK_OR_NO_TERMINAL", 1L, Long::sum); continue; }
				if (!terminal.equals(terminal.toRealPath()) || Files.size(terminal) > TerminalRewardReceiptV1.MAX_BYTES) { throw new IllegalArgumentException("Physical bounded terminal required"); }
				final byte[] bytes = Files.readAllBytes(terminal); final var receipt = TerminalRewardReceiptV1.restore(bytes, NeuralPolicy.sha256(bytes));
				if (receipt.actions().stream().filter(action -> selection.matches(action.row().receipt())).count() != 1) { excluded.merge("NO_EXACT_SELECTED_PAID_TERMINAL", 1L, Long::sum); continue; }
				samples.add(new org.l2jmobius.gameserver.phantoms.learning.ActionAdvantageV2.Sample(selection, receipt));
			}
		}
		if (samples.isEmpty()) { System.out.println("AUTONOMOUS_COHORT_DIAGNOSTIC samples=0 readOnly=true excluded=" + excluded); return; }
		final var frozen = org.l2jmobius.gameserver.phantoms.learning.ActionAdvantageV2.freeze(hash(directory.getFileName().toString()).substring(0, 32), samples);
		final var cells = new java.util.TreeMap<String, Map<String, Long>>();
		for (var sample : samples)
		{
			final var selection = sample.selection(); final var row = cells.computeIfAbsent(selection.cell(), _ -> new java.util.TreeMap<>());
			row.merge(selection.split() + (selection.experimental() ? "_B" : "_A"), 1L, Long::sum);
			row.put("class", (long) selection.observation().self().classId());
			row.put("controlContexts", samples.stream().filter(s -> s.selection().cell().equals(selection.cell()) && s.selection().split().equals("TRAIN") && !s.selection().experimental()).map(org.l2jmobius.gameserver.phantoms.learning.ActionAdvantageV2.Sample::context).distinct().count());
		}
		for (var target : frozen.targets()) { cells.get(target.sample().selection().cell()).merge("TARGET_" + target.sample().selection().split(), 1L, Long::sum); }
		System.out.println("AUTONOMOUS_COHORT_DIAGNOSTIC readOnly=true sufficient=" + frozen.sufficient() + " metrics=" + frozen.metrics() + " excluded=" + excluded + " cells=" + cells);
	}
	private static String hash(String value) { return NeuralPolicy.sha256(value.getBytes(StandardCharsets.UTF_8)); }
	private PhantomAutonomousTrainingNative() { }
}

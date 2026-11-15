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
import java.util.HashSet;
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
import org.l2jmobius.gameserver.phantoms.learning.EncounterAdmissionV1;
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
		PhantomLearning005Evaluation.Incarnation target, boolean completed, List<String> events, String preCutRefusal)
	{
		EvaluationArm(String physical, List<PhantomLearning005Evaluation.Incarnation> actors, PhantomLearning005Evaluation.Incarnation target, boolean completed, List<String> events)
		{ this(physical, actors, target, completed, events, ""); }
	}
	@FunctionalInterface private interface CellAdmission { boolean admit(Cell cell, int ordinal) throws Exception; }
	static String comparisonCellKey(int ordinal, String sourcePhysical, String pool, boolean party, String scene, List<String> roster, boolean v2)
	{ return hash((v2 ? "cell/v2/" : "cell/" + ordinal + "/") + sourcePhysical + "/" + pool + "/" + party + "/" + scene + (v2 ? "/roster/" + roster : "")); }
	static <T> List<T> nativeProbePage(List<T> seedOrdered, boolean v2) { return v2 ? seedOrdered.stream().limit(2).toList() : seedOrdered; }
	static int remainingProbeBudget(int priorAttempts)
	{ if (priorAttempts < 0 || priorAttempts > 12) { throw new IllegalArgumentException("EVAL012_PRIOR_PROBE_BUDGET"); } return 12 - priorAttempts; }
	private static int priorNativeFirstProbes(Path directory) throws Exception
	{
		int result = 0;
		try (var sessions = Files.list(directory.getParent()))
		{
			for (Path prior : sessions.filter(p -> !p.equals(directory) && Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)).toList())
			{
				if (!prior.equals(prior.toRealPath())) { throw new IllegalStateException("EVAL012_PRIOR_PROBE_PATH"); }
				boolean nativeFirst = false;
				try (var catalogs = Files.newDirectoryStream(prior, "curriculum-candidates-v2.*.json"))
				{ for (Path catalog : catalogs) { if ("EVAL_CURRICULUM_PROPOSALS_V2".equals(PhantomEvaluationReplayV2.object(catalog).get("schema"))) { nativeFirst = true; break; } } }
				if (!nativeFirst) { continue; }
				try (var files = Files.walk(prior))
				{ if (files.anyMatch(p -> Set.of("cut.json", "reserved.json").contains(p.getFileName().toString()))) { throw new IllegalStateException("EVAL012_PRIOR_PROBE_NOT_PRE_CUT"); } }
				final var budget = PhantomEvaluationReplayV2.object(prior.resolve("headless-candidate-budget.json"));
				if (PhantomEvaluationReplayV2.number(budget, "maximum") != 12 || !Boolean.FALSE.equals(budget.get("afterCut"))) { throw new IllegalStateException("EVAL012_PRIOR_PROBE_COUNTER_INVALID"); }
				result = Math.addExact(result, PhantomEvaluationReplayV2.integer(budget, "attempts"));
			}
		}
		remainingProbeBudget(result); return result;
	}
	private static final class HeadlessBudget
	{
		private final int maximum; private int attempts; private final Set<String> seen = new HashSet<>();
		HeadlessBudget(int maximum) { this.maximum = maximum; }
		boolean claim(String key)
		{
			if (seen.contains(key)) { return false; }
			if (attempts >= maximum) { throw new IllegalStateException("AUTONOMOUS_HEADLESS_CANDIDATE_BUDGET"); }
			seen.add(key); attempts++; return true;
		}
	}
	private static boolean retryableHeadlessRefusal(String reason)
	{ return reason.equals("AUTONOMOUS_PRE_CUT_PARTY_DAMAGE_REFUSED"); }
	private static String partyAdmissionFailure(Map<String, Object> admission)
	{
		final String wait = String.valueOf(admission.get("wait"));
		return Boolean.FALSE.equals(admission.get("admitted")) && wait.startsWith("WAIT_GROUP_DAMAGE:")
			&& String.valueOf(admission.get("reasons")).equals("[" + wait + "]")
			? "AUTONOMOUS_PRE_CUT_PARTY_DAMAGE_REFUSED" : "AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED";
	}
	static String preCutSelectionRefusal(Map<String, Object> admission, boolean v2)
	{
		final String wait = String.valueOf(admission.get("wait"));
		if (v2 && Boolean.FALSE.equals(admission.get("admitted")) && String.valueOf(admission.get("reasons")).equals("[" + wait + "]"))
		{
			if (wait.startsWith("WAIT_GROUP_MP:")) { return "AUTONOMOUS_PRE_CUT_PARTY_MP_REFUSED"; }
			if (wait.startsWith("WAIT_TANK_AGGRO:")) { return "AUTONOMOUS_PRE_CUT_PARTY_RISK_REFUSED"; }
		}
		return partyAdmissionFailure(admission);
	}
	static boolean retryableSelectionRefusal(String reason, boolean v2)
	{ return retryableHeadlessRefusal(reason) || v2 && Set.of("AUTONOMOUS_PRE_CUT_PARTY_MP_REFUSED", "AUTONOMOUS_PRE_CUT_PARTY_RISK_REFUSED").contains(reason); }
	private static final class Refusals
	{
		private final List<String> samples = new ArrayList<>();
		private final Map<String, Long> counts = new java.util.TreeMap<>();
		void add(String value)
		{ counts.merge(value.split(":", 2)[0], 1L, Long::sum); if (samples.size() < 128) { samples.add(value); } }
	}
	private static EncounterAdmissionV1.Proposal encounterProposal(Cell cell, TrainingConfig config)
	{
		return new EncounterAdmissionV1.Proposal(cell.party() ? EncounterAdmissionV1.Mode.PARTY_PVE : EncounterAdmissionV1.Mode.SOLO_PVE,
			cell.proposal().pool().family(), cell.roster().stream().map(source -> new EncounterAdmissionV1.Entity(
				source.seed().equals(cell.proposal().actor()) ? "LEARNER" : "HELPER", source.image().sha(), source.inventory().fingerprint())).toList(),
			cell.scene().source(), hash(cell.scene().row() + "/" + cell.x() + "/" + cell.y() + "/" + cell.z()), config.sha256(),
			config.episodeSeconds() * 1000L, cell.proposal().shortlist().candidates());
	}
	private static List<String> preparedArtifactNames(String cellsBody)
	{
		final var names = new ArrayList<String>(); names.add("cells.tsv");
		for (String line : cellsBody.lines().skip(1).filter(value -> !value.isBlank()).toList())
		{
			final String[] fields = line.split("\t", -1);
			if (fields.length != 16 || !fields[0].matches("[a-f0-9]{64}")) { throw new IllegalStateException("AUTONOMOUS_CELL_CODEC"); }
			names.add(fields[0] + ".physical.sha");
			if (fields[1].contains(",")) { names.add(fields[0] + ".risk.tsv"); }
		}
		return List.copyOf(names);
	}
	private static List<String> catalogArtifactBindings(Path directory, String cellsBody) throws Exception
	{
		final var bindings = new ArrayList<String>();
		for (String name : evaluationArtifactNames(directory, cellsBody))
		{
			final Path path = directory.resolve(name);
			if (!path.equals(path.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_PREPARED_PATH"); }
			bindings.add(name + ":" + NeuralPolicy.sha256(Files.readAllBytes(path)));
		}
		return List.copyOf(bindings);
	}
	private static List<String> pairedNativeArtifacts(Path directory, String cell) throws Exception
	{
		PhantomEvaluationOutcomeV2.sha(cell);
		final var pair = PhantomEvaluationReplayV2.object(directory.resolve(cell + ".paired-native-v2.json"));
		final String direction = PhantomEvaluationReplayV2.string(pair, "direction"), scope = direction.split("_", 2)[0];
		final String physical = PhantomEvaluationReplayV2.string(pair, "physicalSha"), proposal = PhantomEvaluationReplayV2.string(pair, "proposalSha");
		PhantomEvaluationOutcomeV2.sha(physical); PhantomEvaluationOutcomeV2.sha(proposal); PhantomEvaluationOutcomeV2.sha(PhantomEvaluationReplayV2.string(pair, "configSha"));
		if (!"EVAL_PRECUT_PAIR_V2".equals(pair.get("schema")) || !cell.equals(pair.get("cell")) || !Set.of("SOLO_DAMAGE", "PARTY_DAMAGE", "PARTY_HEAL").contains(direction)
			|| !Boolean.TRUE.equals(pair.get("fullNativeAdmission")) || !Boolean.TRUE.equals(pair.get("matchedPhysical")) || !Boolean.TRUE.equals(pair.get("freshIncarnations"))
			|| !Boolean.FALSE.equals(pair.get("afterCut")) || !Boolean.FALSE.equals(pair.get("trainingCredit"))) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_HEADER"); }
		final var arms = PhantomEvaluationReplayV2.list(pair.get("arms"));
		if (arms.size() != 2) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_ARM_COUNT"); }
		final var result = new ArrayList<String>(); final var identities = new HashSet<Integer>();
		for (int index = 0; index < 2; index++)
		{
			final String side = index == 0 ? "STOCK" : "LEARNED", work = hash(hash("ADMISSION/" + cell) + "/" + side);
			final var arm = PhantomEvaluationReplayV2.map(arms.get(index)); final var shas = PhantomEvaluationReplayV2.map(arm.get("rawFileShas"));
			final var mandatory = new ArrayList<>(List.of("admission-only.json", "native-admission.json", "encounter-admission.json", "precut.json", "incarnations.tsv", "curriculum-native-facts-v2.json"));
			if (scope.equals("PARTY")) { mandatory.add("party-admission.json"); }
			if (!side.equals(arm.get("side")) || !work.equals(arm.get("work")) || !shas.keySet().equals(new HashSet<>(mandatory))) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_SAFE_PATHS"); }
			for (String name : mandatory)
			{
				final Path raw = directory.resolve(work).resolve(name); PhantomEvaluationReplayV2.text(raw);
				if (!NeuralPolicy.sha256(Files.readAllBytes(raw)).equals(shas.get(name))) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_RAW_CHANGED"); } result.add(work + "/" + name);
			}
			final var admitted = PhantomEvaluationReplayV2.object(directory.resolve(work).resolve("admission-only.json"));
			final var encounter = PhantomEvaluationReplayV2.object(directory.resolve(work).resolve("encounter-admission.json"));
			final var precut = PhantomEvaluationReplayV2.object(directory.resolve(work).resolve("precut.json"));
			final var features = PhantomEvaluationReplayV2.object(directory.resolve(work).resolve("curriculum-native-facts-v2.json"));
			if (!Boolean.TRUE.equals(admitted.get("admitted")) || !Boolean.FALSE.equals(admitted.get("afterCut")) || !Boolean.FALSE.equals(admitted.get("gameServerStarted"))
				|| !Boolean.FALSE.equals(admitted.get("trainingCredit")) || PhantomEvaluationReplayV2.number(admitted, "nativeActionsObserved") != 0
				|| !EncounterAdmissionV1.VERSION.equals(encounter.get("schema")) || !"HEADLESS_READY".equals(encounter.get("stage")) || !Boolean.FALSE.equals(encounter.get("sealable"))
				|| !Boolean.FALSE.equals(encounter.get("afterCut")) || !Boolean.FALSE.equals(encounter.get("trainingCredit")) || !proposal.equals(encounter.get("proposalSha"))
				|| !physical.equals(encounter.get("physicalSha")) || !physical.equals(precut.get("comparisonCellSha"))
				|| !physical.equals(hash(PhantomLearning005Evaluation.mapJson(PhantomEvaluationReplayV2.map(precut.get("facts")))))
				|| !cell.equals(features.get("key")) || !features.equals(pair.get("curriculumFacts")) || PhantomEvaluationReplayV2.number(features, "targetBoundAlternatives") < 2)
			{ throw new IllegalStateException("EVAL012_NATIVE_PAIR_PAYLOAD"); }
			int target = 0, actors = 0;
			final var lines = PhantomEvaluationReplayV2.text(directory.resolve(work).resolve("incarnations.tsv")).lines().toList();
			if (lines.isEmpty() || !lines.getFirst().equals("role\tobjectId\tepoch\tinstance")) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_IDENTITY_HEADER"); }
			for (String line : lines.subList(1, lines.size()))
			{
				final var f = line.split("\t", -1); if (f.length != 4) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_IDENTITY_CODEC"); }
				final var identity = new PhantomEvaluationOutcomeV2.Identity(Integer.parseInt(f[1]), Long.parseLong(f[2]), Integer.parseInt(f[3]));
				if (!identities.add(identity.objectId())) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_IDENTITY_REUSE"); }
				if (f[0].equals("TARGET") && target == 0) { target = identity.objectId(); } else if (f[0].equals("ACTOR")) { actors++; } else { throw new IllegalStateException("EVAL012_NATIVE_PAIR_IDENTITY_ROLE"); }
			}
			if (target == 0 || actors < 1 || actors > 9 || scope.equals("SOLO") && actors != 1 || scope.equals("PARTY") && actors < 3) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_IDENTITY_COUNT"); }
			final String nativeRaw = PhantomEvaluationReplayV2.text(directory.resolve(work).resolve("native-admission.json"));
			PhantomLearning005Evaluation.requireRaw(nativeRaw, "admitted", "true"); PhantomLearning005Evaluation.requireRaw(nativeRaw, "scope", q(scope)); PhantomLearning005Evaluation.requireRaw(nativeRaw, "target", Integer.toString(target));
			final long timeout = Long.parseLong(PhantomLearning005Evaluation.rawField(nativeRaw, "requestTimeoutMillis"));
			if (timeout < 1000 || timeout > org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_REQUEST_WINDOW"); }
			final var targets = java.util.regex.Pattern.compile("\\btarget=([0-9]+)").matcher(nativeRaw); int sessions = 0;
			while (targets.find()) { sessions++; if (Integer.parseInt(targets.group(1)) != target) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_SESSION_TARGET"); } }
			if (sessions == 0) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_NO_SESSION"); }
			if (scope.equals("PARTY"))
			{
				final var party = PhantomEvaluationReplayV2.object(directory.resolve(work).resolve("party-admission.json"));
				if (!Boolean.TRUE.equals(party.get("admitted")) || !Boolean.TRUE.equals(party.get("combatRequestValidated")) || PhantomEvaluationReplayV2.number(party, "target") != target) { throw new IllegalStateException("EVAL012_NATIVE_PAIR_PARTY_REFUSED"); }
			}
		}
		return List.copyOf(result);
	}
	private static List<String> evaluationArtifactNames(Path directory, String cellsBody) throws Exception
	{
		final var names = new ArrayList<>(preparedArtifactNames(cellsBody));
		if (Files.exists(directory.resolve("curriculum-v2.json")))
		{
			names.add("curriculum-v2.json");
			for (String line : cellsBody.lines().skip(1).filter(value -> !value.isBlank()).toList())
			{ final String cell = line.split("\t", -1)[0]; names.add(cell + ".paired-native-v2.json"); names.addAll(pairedNativeArtifacts(directory, cell)); }
		}
		return List.copyOf(names);
	}
	private static void copyPreparedArtifacts(Path source, Path destination, String cellsBody) throws Exception
	{
		catalogArtifactBindings(source, cellsBody);
		for (String name : evaluationArtifactNames(source, cellsBody)) { requirePhysicalDirectory(destination, destination.resolve(name).getParent()); immutable(destination.resolve(name), Files.readAllBytes(source.resolve(name))); }
	}
	private static void copyPreparedProposals(Path source, Path destination, String cellsBody) throws Exception
	{
		catalogArtifactBindings(source, cellsBody);
		// Expected headless hashes stay in prepared/. A live physical seal exists only after actual admission.
		for (String name : evaluationArtifactNames(source, cellsBody).stream().filter(value -> !value.endsWith(".physical.sha")).toList())
		{ requirePhysicalDirectory(destination, destination.resolve(name).getParent()); immutable(destination.resolve(name), Files.readAllBytes(source.resolve(name))); }
	}
	private static String catalogFingerprint(Path root, Path directory, List<Source> sources, TrainingConfig config) throws Exception
	{
		final String cellsBody = Files.readString(directory.resolve("cells.tsv"));
		final var bindings = new ArrayList<>(sources.stream().map(source -> source.image().sha() + ":" + source.inventory().fingerprint()).toList());
		bindings.addAll(catalogArtifactBindings(directory, cellsBody));
		bindings.add("JAR:" + NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar"))));
		return EncounterAdmissionV1.catalogFingerprint(config.sha256(), Files.readString(directory.resolve("cells.tsv")),
			bindings);
	}
	private static Path preparedDirectory(Path root, TrainingConfig config) throws Exception
	{ return preparedDirectory(root, config.sha256(), NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar")))); }
	private static Path preparedDirectory(Path root, String configSha, String jarSha)
	{
		if (configSha == null || jarSha == null || !configSha.matches("[a-f0-9]{64}") || !jarSha.matches("[a-f0-9]{64}"))
		{ throw new IllegalArgumentException("AUTONOMOUS_PREPARED_IDENTITY"); }
		return root.resolve("autonomous/v1/prepared").resolve(configSha).resolve(jarSha);
	}
	private static void loadPreparedCatalog(Path root, Path directory, List<Source> sources, TrainingConfig config) throws Exception
	{
		final Path prepared = preparedDirectory(root, config);
		if (!Files.isRegularFile(prepared.resolve("headless.complete")) || !Files.isRegularFile(prepared.resolve("cells.tsv")))
		{ throw new IllegalStateException("AUTONOMOUS_HEADLESS_PREPARATION_REQUIRED"); }
		for (String name : List.of("headless.complete", "cells.tsv"))
		{ final Path path = prepared.resolve(name); if (!path.equals(path.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_PREPARED_PATH"); } }
		if (!Files.readString(prepared.resolve("headless.complete")).equals(catalogFingerprint(root, prepared, sources, config)))
		{ throw new IllegalStateException("AUTONOMOUS_PREPARED_CAPABILITY_OR_CONFIG_CHANGED"); }
		copyPreparedProposals(prepared, directory, Files.readString(prepared.resolve("cells.tsv")));
	}
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
		final boolean isolated013 = config.integer("autonomous013Version", 0, 0, 1) == 1 && !config.text("autonomous009EvaluationOnly", "false").equals("true");
		final var history013 = isolated013 ? historicalFence013(root, List.of()) : new SourceFence013(Set.of(), Set.of(), Set.of());
		int inspected013 = 0;
		for (var header : headers)
		{
			PhantomTrainingScenarios.requirePlanningActive(requested);
			final int id = header[0], classId = header[1];
			if (!config.classes().contains(classId) || sources.size() >= 64 || attempts.getOrDefault(classId, 0) >= 16
				|| signatures.getOrDefault(classId, Set.of()).size() >= 2) { continue; }
			if (isolated013 && inspected013++ >= 4096) { break; }
			if (!isolated013) { attempts.merge(classId, 1, Integer::sum); }
			Player player = null;
			try
			{
				final PhantomTrainingActors.SavedActor image;
				try (var connection = DatabaseFactory.getConnection()) { image = PhantomTrainingActors.readSavedActor(connection, id); }
				image.requireReproducible();
				if (isolated013 && (history013.hashes().contains(image.sha()) || history013.actorIds().contains(id) || history013.physical().contains(image.physicalFingerprint())))
				{ coverage.add("{\"source\":" + id + ",\"classId\":" + classId + ",\"status\":\"EXCLUDED_HISTORICAL_EVAL\"}"); continue; }
				if (!config.levels().contains(Integer.parseInt(image.field("level")))) { continue; }
				final String signature = image.physicalFingerprint();
				if (signatures.getOrDefault(classId, Set.of()).contains(signature)) { continue; }
				if (isolated013) { attempts.merge(classId, 1, Integer::sum); }
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
	static String safeReason(Exception failure)
	{
		String message = failure.getMessage();
		if (message != null && message.startsWith("LEARNING001_AUTONOMOUS_")) { message = message.substring("LEARNING001_".length()); }
		return message != null && message.matches("(?:AUTONOMOUS|TRAIN013)_[A-Z0-9_]{1,120}") ? message : failure.getClass().getSimpleName();
	}
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
	{ checkpoint(installation, false); }
	private static void checkpoint(Path installation, boolean cycle013) throws Exception
	{
		final var root = PhantomTrainingActors.physicalRoot(installation); new TrainingStore(root).requireStopped(); PhantomTrainingActors.settings(root);
		final var store = new TacticalStoreV3(root); final var parent = store.optimizationParent(); final var model = store.candidate(parent.modelId()); final var accepted = store.latestAccepted();
		if (model == null || accepted == null) { throw new IllegalStateException("AUTONOMOUS_COLD_LINEAGE_REQUIRED"); }
		final String body = "{\"modelVersion\":" + model.id() + ",\"modelSha\":" + q(model.sha()) + ",\"optimizerSha\":" + q(parent.sha()) + ",\"terminalWeightsSha\":" + q(parent.state().model().sha256())
			+ ",\"optimizerUpdates\":" + parent.state().updates() + ",\"completedEpochs\":" + parent.state().completedEpochs() + ",\"acceptedTestVersion\":" + accepted.id() + ",\"acceptedTestSha\":" + q(accepted.sha())
			+ (cycle013 ? ",\"schemaVersion\":13,\"featureSchemaSha\":" + q(org.l2jmobius.gameserver.phantoms.learning.TacticalObservationV3.SCHEMA) : "") + ",\"productionPromotion\":false}";
		final Path directory = root.resolve(cycle013 ? "autonomous/v13" : "autonomous/v1"); requirePhysicalDirectory(root, directory); final Path temporary = directory.resolve("checkpoint." + UUID.randomUUID() + ".pending");
		if (cycle013)
		{
			final Path selected = directory.resolve("models").resolve(Long.toString(model.id())); requirePhysicalDirectory(root, selected);
			atomicBlob013(selected.resolve("selected.bin"), model.model().bytes()); atomicBlob013(selected.resolve("terminal-adam.bin"), parent.state().bytes());
			final Path best = directory.resolve("models").resolve(Long.toString(accepted.id())); requirePhysicalDirectory(root, best);
			atomicBlob013(best.resolve("selected.bin"), accepted.model().bytes());
		}
		Files.writeString(temporary, body, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		final Path pointer = directory.resolve(cycle013 ? "sql-checkpoint.json" : "cold-checkpoint.json"); if (Files.exists(pointer) && !pointer.equals(pointer.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_CHECKPOINT_PATH"); }
		Files.move(temporary, pointer, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		System.out.println("AUTONOMOUS_COLD_CHECKPOINT " + body);
	}
	private static void atomicBlob013(Path path, byte[] bytes) throws Exception
	{
		if (Files.exists(path))
		{
			if (!path.equals(path.toRealPath()) || !java.util.Arrays.equals(Files.readAllBytes(path), bytes)) { throw new IllegalStateException("TRAIN013_COLD_BLOB_CHANGED"); }
			return;
		}
		final Path pending = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".pending");
		try { Files.write(pending, bytes, StandardOpenOption.CREATE_NEW); Files.move(pending, path, StandardCopyOption.ATOMIC_MOVE); }
		finally { Files.deleteIfExists(pending); }
	}
	private static void evaluateNative(Path root, Path attempt, TrainingConfig config) throws Exception
	{
		if (outcomeV2(config)) { evaluateNativeV2(root, attempt, config); return; }
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
			loadPreparedCatalog(root, directory, sources, config);
			final var cells = planCells(directory, sources, config, authority, () -> requested(attempt)); requireEvaluationCoverage(cells);
			immutable(directory.resolve("protocol.json"), "{\"modelVersion\":" + model.id() + ",\"modelSha\":" + q(model.sha()) + ",\"comparator\":" + q(AutonomousTrainingV1.COMPARATOR) + ",\"trainingStateSha\":" + q(hash(before)) + ",\"verdict\":\"INCONCLUSIVE_UNTIL_NATIVE_PAIRS\",\"trainingCredit\":false}");
			final var paired = new ArrayList<String>(); int completedPairs = 0;
			for (var cell : cells)
			{
				if (requested(attempt)) { allSettled = false; break; }
				final var arms = new ArrayList<EvaluationArm>();
				final var slot = new AutonomousTrainingV1.CohortSlot(cells.indexOf(cell), cell.key(), hash("EVAL/" + cell.key()), "EVAL", false);
				final var order = evaluationOrder(cells.stream().map(PhantomAutonomousTrainingNative::evaluationDirection).toList(), cells.indexOf(cell));
				for (String side : order)
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
				final String receipt = "{\"cell\":" + q(cell.key()) + ",\"direction\":" + q(evaluationDirection(cell)) + ",\"order\":" + q(order.getFirst().equals("STOCK") ? "STOCK_LEARNED" : "LEARNED_STOCK") + ",\"arms\":" + arms.size() + ",\"status\":" + q(status) + ",\"trainingCredit\":false}";
				immutable(directory.resolve(cell.key() + ".pair.json"), receipt); paired.add(receipt);
			}
			if (allSettled && !requested(attempt)) { immutable(directory.resolve("evaluation-pairs.json"), "{\"completedPairs\":" + completedPairs + ",\"pairs\":[" + String.join(",", paired) + "],\"trainingCredit\":false,\"verdict\":\"INCONCLUSIVE\"}"); }
		}
		final String after = PhantomLearning005Native.trainingState(root, true); if (!before.equals(after)) { throw new IllegalStateException("AUTONOMOUS_EVAL_CHANGED_TRAINING_STATE"); }
		immutable(attempt.resolve("batch.complete"), "schemaVersion=9\nevaluationOnly=true\ntrainingStateUnchanged=true\n");
		if (allSettled && !requested(attempt)) { immutable(directory.resolve("evaluation.complete"), "{\"verdict\":\"INCONCLUSIVE\",\"reason\":\"BOUNDED_NATIVE_SAMPLE_NO_PROMOTION\",\"trainingStateUnchanged\":true,\"productionPromotion\":false}"); }
	}
	private static boolean outcomeV2(TrainingConfig config)
	{
		final String version = config.text("evaluationOutcomeVersion", "1");
		if (!Set.of("1", "2").contains(version)) { throw new IllegalStateException("EVAL012_UNSUPPORTED_VERSION"); }
		if (version.equals("1")) { return false; }
		if (!config.text("autonomous009EvaluationOnly", "false").equals("true") || !config.text("autonomous009Exploration", "true").equals("false")
			|| !Set.of("OFF", "EVAL").contains(config.text("autonomous009Phase", "")) || config.integer("autonomous009SmokeMaxStarts", 0, 1, 16) > 2
			|| config.integer("autonomous009SmokeMaxNativeMinutes", 0, 1, 90) > 35) { throw new IllegalStateException("EVAL012_ONLY_FROZEN_BOUNDED_EVAL"); }
		return true;
	}
	private static Map<String, Object> curriculumCell(Path directory, String key, TrainingConfig config) throws Exception
	{
		final var catalog = PhantomEvaluationReplayV2.object(directory.resolve("curriculum-v2.json"));
		if (!PhantomEvaluationReplayV2.string(catalog, "schema").equals("EVAL_CURRICULUM_CATALOG_V2") || !PhantomEvaluationReplayV2.string(catalog, "configSha").equals(config.sha256())
			|| PhantomEvaluationReplayV2.number(catalog, "seed") != config.seed()) { throw new IllegalStateException("EVAL012_CURRICULUM_PIN"); }
		return PhantomEvaluationReplayV2.list(catalog.get("cells")).stream().map(PhantomEvaluationReplayV2::map).filter(row -> row.get("cell").equals(key)).findFirst().orElseThrow(() -> new IllegalStateException("EVAL012_CURRICULUM_CELL"));
	}
	private static void requireV2Coverage(Path directory, List<Cell> cells, TrainingConfig config) throws Exception
	{
		for (String scope : List.of("SOLO", "PARTY"))
		{
			final var group = cells.stream().filter(c -> c.party() == scope.equals("PARTY")).toList(); final var strata = new HashSet<String>(); final var burdens = new ArrayList<String>();
			for (var cell : group)
			{
				final var row = curriculumCell(directory, cell.key(), config); strata.add(PhantomEvaluationReplayV2.string(row, "stratum")); burdens.add(PhantomEvaluationReplayV2.string(row, "burdenVector"));
				final Path proof = directory.resolve(cell.key() + ".paired-native-v2.json"); final var pair = PhantomEvaluationReplayV2.object(proof);
				pairedNativeArtifacts(directory, cell.key());
				if (!proof.equals(proof.toRealPath()) || !PhantomEvaluationReplayV2.string(row, "pairedNativeSha").equals(NeuralPolicy.sha256(Files.readAllBytes(proof)))
					|| !"EVAL_PRECUT_PAIR_V2".equals(pair.get("schema")) || !cell.key().equals(pair.get("cell")) || !config.sha256().equals(pair.get("configSha"))
					|| !Boolean.TRUE.equals(pair.get("fullNativeAdmission")) || !Boolean.TRUE.equals(pair.get("matchedPhysical")) || !Boolean.TRUE.equals(pair.get("freshIncarnations"))
					|| !row.get("physicalSha").equals(pair.get("physicalSha")) || !Boolean.FALSE.equals(pair.get("afterCut"))
					|| !PhantomEvaluationReplayV2.list(pair.get("arms")).stream().map(PhantomEvaluationReplayV2::map).map(a -> a.get("side")).toList().equals(List.of("STOCK", "LEARNED")))
				{ throw new IllegalStateException("EVAL012_FULL_PAIRED_NATIVE_ADMISSION_REQUIRED"); }
			}
			if (group.size() < 2 || !strata.containsAll(List.of("LOWER_PRECUT_BURDEN", "HIGHER_PRECUT_BURDEN")) || !PhantomEvaluationCurriculumV2.selectedDiverse(burdens)) { throw new IllegalStateException("EVAL012_PRE_CUT_DIVERSITY_COVERAGE_" + scope); }
		}
	}
	private static void evaluateNativeV2(Path root, Path attempt, TrainingConfig config) throws Exception
	{
		final var store = new TacticalStoreV3(root); final var model = store.candidate(config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE));
		if (model == null || !model.sha().equals(config.text("autonomous009ModelSha", ""))) { throw new IllegalStateException("EVAL012_MODEL_CHANGED"); }
		final Path directory = store.autonomousDirectory().resolve("evaluations").resolve(config.text("outcome006DatasetId", "") + "-" + model.id()); requirePhysicalDirectory(root, directory);
		final var settings = PhantomTrainingActors.settings(root); final var stock = new LearningStore(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"), root.resolve("game")).accepted(1);
		if (stock == null) { throw new IllegalStateException("EVAL012_STOCK_UNAVAILABLE"); }
		final String before = PhantomLearning005Native.trainingState(root, true); final var build = PhantomEvaluationReplayV2.object(root.resolve("build.json"));
		final var pin = new PhantomEvaluationOutcomeV2.Authority(PhantomEvaluationReplayV2.string(build, "sourceFingerprint"), PhantomEvaluationReplayV2.string(build, "jarSha").toLowerCase(java.util.Locale.ROOT), config.sha256(), model.id(), model.sha(), hash(before));
		if (!pin.buildSha().equals(NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar"))))) { throw new IllegalStateException("EVAL012_BUILD_PIN"); }
		immutable(directory.resolve("protocol-v2.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_PROTOCOL_V2", "sourceSha", pin.sourceSha(), "buildSha", pin.buildSha(), "configSha", pin.configSha(), "modelVersion", pin.modelVersion(), "modelSha", pin.modelSha(), "trainingStateSha", pin.trainingStateSha(), "seed", config.seed(), "trainingCredit", false)));
		final var pairs = new ArrayList<PhantomEvaluationOutcomeV2.Pair>(); boolean allSettled = true;
		try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var sources = evaluationSources(root, directory, discoverSources(root, directory, config, authority, () -> requested(attempt)), config); loadPreparedCatalog(root, directory, sources, config);
			final var cells = planCells(directory, sources, config, authority, () -> requested(attempt)); requireEvaluationCoverage(cells); requireV2Coverage(directory, cells, config);
			for (var cell : cells)
			{
				if (requested(attempt)) { allSettled = false; break; }
				final var order = evaluationOrder(cells.stream().map(PhantomAutonomousTrainingNative::evaluationDirection).toList(), cells.indexOf(cell)); final var arms = new ArrayList<PhantomEvaluationOutcomeV2.Arm>();
				final String tier = PhantomEvaluationReplayV2.string(curriculumCell(directory, cell.key(), config), "stratum");
				for (String side : order)
				{
					if (requested(attempt)) { allSettled = false; break; }
					final String id = hash("EVAL/" + config.text("outcome006DatasetId", "") + "/" + cell.key() + "/" + side); final Path work = directory.resolve(id), saved = work.resolve("evaluation-arm-v2.bin");
					if (!Files.exists(work))
					{
						Files.createDirectory(work); immutable(work.resolve("reserved.json"), "{\"episode\":" + q(id) + ",\"side\":" + q(side) + ",\"split\":\"EVAL\",\"trainingCredit\":false}");
						collectEpisode(root, attempt, config, authority, store, model, stock.model(), directory, work, id,
							new AutonomousTrainingV1.CohortSlot(cells.indexOf(cell), cell.key(), hash("EVAL/" + cell.key()), "EVAL", false), cell, side, false);
					}
					// A reserved arm is reopened only from its evidence. No native dispatch occurs on this branch.
					final var arm = PhantomEvaluationReplayV2.readArm(work, cell.key(), evaluationDirection(cell), order.indexOf(side), config.seed(), pin, tier, false, side);
					if (Files.exists(saved) && !PhantomEvaluationOutcomeV2.decode(Files.readAllBytes(saved)).equals(arm)) { throw new IllegalStateException("EVAL012_RESUME_RECEIPT_CHANGED"); }
					immutable(saved, PhantomEvaluationOutcomeV2.encode(arm)); arms.add(arm);
				}
				if (arms.size() != 2) { allSettled = false; continue; } pairs.add(PhantomEvaluationOutcomeV2.compare(arms.getFirst(), arms.getLast()));
			}
		}
		if (!before.equals(PhantomLearning005Native.trainingState(root, true))) { throw new IllegalStateException("EVAL012_CHANGED_TRAINING_STATE"); }
		PhantomEvaluationReplayV2.publish(directory, PhantomEvaluationOutcomeV2.campaign(pairs), allSettled && !requested(attempt)); immutable(attempt.resolve("batch.complete"), "schemaVersion=9\nevaluationOnly=true\ntrainingStateUnchanged=true\n");
		if (allSettled && !requested(attempt)) { immutable(directory.resolve("evaluation-v2.complete"), "{\"schema\":\"EVAL_COMPLETE_V2\",\"trainingStateUnchanged\":true,\"trainingCredit\":false,\"productionPromotion\":false,\"verdict\":\"INCONCLUSIVE\"}"); }
	}
	private static List<Source> trainingSources013(Path root, List<Source> sources, TrainingConfig config) throws Exception
	{
		if (config.integer("autonomous013Version", 0, 0, 1) != 1 || config.text("autonomous009EvaluationOnly", "false").equals("true")) { return sources; }
		final var fence = historicalFence013(root, sources.stream().map(Source::image).toList());
		return sources.stream().filter(s -> !fence.hashes().contains(s.image().sha()) && !fence.actorIds().contains(s.image().sourceId()) && !fence.physical().contains(s.image().physicalFingerprint())).toList();
	}
	static record SourceFence013(Set<String> hashes, Set<Integer> actorIds, Set<String> physical) { }
	static SourceFence013 historicalFence013(Path root, List<PhantomTrainingActors.SavedActor> current) throws Exception
	{
		final var held = new HashSet<String>(); final var heldActors = new HashSet<Integer>();
		final var historicalPhysical = new HashSet<String>();
		final Path campaigns = root.resolve("autonomous/v1/campaigns");
		if (!Files.isDirectory(campaigns)) { return new SourceFence013(Set.of(), Set.of(), Set.of()); }
		try (var history = Files.list(campaigns))
		{
			final var entries = history.filter(Files::isDirectory).toList(); if (entries.size() > 256) { throw new IllegalStateException("TRAIN013_HISTORY_CAPACITY"); }
			for (Path campaign : entries)
			{
				final Path evaluations = campaign.resolve("evaluations"); if (!Files.isDirectory(evaluations)) { continue; }
				try (var runs = Files.list(evaluations))
				{
					for (Path run : runs.filter(Files::isDirectory).toList())
					{
						final Path cells = run.resolve("cells.tsv"); if (!Files.isRegularFile(cells)) { continue; }
						if (!cells.equals(cells.toRealPath())) { throw new IllegalStateException("TRAIN013_HISTORY_REPARSE"); }
						for (String line : Files.readAllLines(cells).stream().skip(1).toList())
						{
							final var fields = line.split("\t", -1); if (fields.length != 16) { throw new IllegalStateException("TRAIN013_HISTORY_CELL_CODEC"); }
							for (String sha : fields[1].split(","))
							{
								if (!sha.matches("[a-f0-9]{64}")) { throw new IllegalStateException("TRAIN013_HISTORY_SOURCE_SHA"); }
								held.add(sha); final Path image = run.resolve(sha + ".source.json");
								if (!image.equals(image.toRealPath()) || !NeuralPolicy.sha256(Files.readAllBytes(image)).equals(sha)) { throw new IllegalStateException("TRAIN013_HISTORY_SOURCE_CHANGED"); }
								final var snapshot = historicalSnapshot013(PhantomEvaluationReplayV2.object(image));
								if (!snapshot.sha().equals(sha)) { throw new IllegalStateException("TRAIN013_HISTORY_SNAPSHOT_CODEC"); }
								heldActors.add(snapshot.sourceId()); historicalPhysical.add(snapshot.physicalFingerprint());
							}
						}
					}
				}
			}
		}
		final var physical = current.stream().filter(s -> held.contains(s.sha()) || heldActors.contains(s.sourceId())).map(PhantomTrainingActors.SavedActor::physicalFingerprint).collect(java.util.stream.Collectors.toSet());
		physical.addAll(historicalPhysical);
		return new SourceFence013(Set.copyOf(held), Set.copyOf(heldActors), Set.copyOf(physical));
	}
	private static PhantomTrainingActors.SavedActor historicalSnapshot013(Map<String, Object> raw)
	{
		if (!"AUTONOMOUS_SAVED_ACTOR_V1".equals(raw.get("schema"))) { throw new IllegalStateException("TRAIN013_HISTORY_SNAPSHOT_VERSION"); }
		final var tables = new ArrayList<PhantomTrainingActors.SavedTable>();
		for (var item : PhantomEvaluationReplayV2.list(raw.get("tables")))
		{
			final var table = PhantomEvaluationReplayV2.map(item);
			final var columns = PhantomEvaluationReplayV2.list(table.get("columns")).stream().map(value -> (String) value).toList();
			final var types = PhantomEvaluationReplayV2.list(table.get("jdbcTypes")).stream().map(value -> ((Number) value).intValue()).toList();
			final var rows = new ArrayList<List<String>>();
			for (var row : PhantomEvaluationReplayV2.list(table.get("rows"))) { rows.add(PhantomEvaluationReplayV2.list(row).stream().map(value -> (String) value).toList()); }
			tables.add(new PhantomTrainingActors.SavedTable(PhantomEvaluationReplayV2.string(table, "table"), PhantomEvaluationReplayV2.string(table, "identity"), columns, types, rows));
		}
		return new PhantomTrainingActors.SavedActor(PhantomEvaluationReplayV2.integer(raw, "sourceActorId"), tables);
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
	private static List<String> evaluationOrder(List<String> directions, int ordinal)
	{
		if (ordinal < 0 || ordinal >= directions.size()) { throw new IllegalArgumentException("AUTONOMOUS_EVAL_ORDER_ORDINAL"); }
		final long prior = java.util.stream.IntStream.range(0, ordinal).filter(index -> directions.get(index).equals(directions.get(ordinal))).count();
		return prior % 2 == 0 ? List.of("STOCK", "LEARNED") : List.of("LEARNED", "STOCK");
	}
	private static void requireEvaluationCoverage(List<Cell> cells)
	{
		final var directions = cells.stream().map(PhantomAutonomousTrainingNative::evaluationDirection).collect(java.util.stream.Collectors.toSet());
		if (cells.stream().map(c -> c.scene().id()).distinct().count() < 2 || !directions.contains("SOLO_DAMAGE") || (!directions.contains("PARTY_HEAL") && !directions.contains("PARTY_DAMAGE")))
		{ throw new IllegalStateException("AUTONOMOUS_BLOCKED_EVAL_NO_LAWFUL_SCENE"); }
	}
	private static List<Cell> planCells(Path directory, List<Source> sources, TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority, java.util.function.BooleanSupplier requested) throws Exception
	{ return planCells(directory, sources, config, authority, requested, null); }
	private static List<Cell> planCells(Path directory, List<Source> sources, TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority,
		java.util.function.BooleanSupplier requested, CellAdmission nativeAdmission) throws Exception
	{
		PhantomTrainingScenarios.requirePlanningActive(requested);
		final Path sealed = directory.resolve("cells.tsv");
		if (Files.exists(sealed))
		{
			if (nativeAdmission != null) { throw new IllegalStateException("AUTONOMOUS_SEALED_HEADLESS_RECHECK_REQUIRED"); }
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
				if (!f[15].equals(hash(EncounterAdmissionV1.VERSION + "/" + config.sha256()))) { throw new IllegalStateException("AUTONOMOUS_SEALED_PROTOCOL_CHANGED"); }
				result.add(new Cell(f[0], proposal, roster, scene, Integer.parseInt(f[12]), Integer.parseInt(f[13]), Integer.parseInt(f[14])));
			}
			return List.copyOf(result);
		}
		if (outcomeV2(config) && nativeAdmission == null) { throw new IllegalStateException("EVAL012_FULL_PRE_CUT_CATALOG_REQUIRED"); }
		final var planned = new ArrayList<Cell>(); final var visits = new HashMap<String, Long>(); final var cursors = new HashMap<String, Long>(); final var refusals = new Refusals(); final var sceneCounts = new java.util.TreeMap<String, Long>();
		final var nativeFacts = new ArrayList<PhantomEvaluationCurriculumV2.Facts>(); final var rosterVisits = new HashMap<String, Long>();
		final var curriculumRows = new ArrayList<Map<String, Object>>();
		final int cellLimit = AutonomousTrainingV1.cohortCellLimit(config.integer("episodesPerBatch", 48, 48, 256));
		for (var source : sources) { for (var pool : source.seed().supported()) { cursors.put("shortlist/" + source.seed().sourceSnapshotSha() + "/" + pool.key(), Math.multiplyExact(config.longValue("autonomous009SourceCursor", 0, 0, Long.MAX_VALUE), 4)); } }
		for (int sequence = 0; sequence < 48 && planned.size() < (outcomeV2(config) ? 12 : cellLimit); sequence++)
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
				if (outcomeV2(config))
				{
					final var full = List.copyOf(roster);
					final var variants = PhantomEvaluationCurriculumV2.rosterMasks(full.size(), 0).stream().map(mask -> java.util.stream.IntStream.range(0, full.size())
						.filter(i -> (mask & (1 << i)) != 0).mapToObj(full::get).toList())
						.sorted(Comparator.comparing(v -> hash(config.seed() + "/roster012/" + v.stream().map(s -> s.image().physicalFingerprint()).toList()))).toList();
					final long ordinal = rosterVisits.merge(source.image().sha() + "/" + proposal.pool().key(), 1L, Long::sum) - 1;
					roster.clear(); roster.addAll(variants.get((int) (ordinal % variants.size())));
				}
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
					target -> proposal.pool().family() != LegalActionV3.Family.DAMAGE || admitTarget(proposal, source, target, actor, refusals).isPresent(),
					candidate -> { final var bound = admitScene(proposal, source, List.copyOf(roster), candidate, actor, refusals); bound.ifPresent(value -> admitted.put(candidate.scene().id(), value)); return bound.isPresent(); }, requested);
				final int directionOrdinal = (int) planned.stream().filter(c -> c.party() == party && c.proposal().pool().family() == proposal.pool().family()).count();
				final var candidateFacts = new ArrayList<PhantomEvaluationCurriculumV2.Facts>();
				if (outcomeV2(config))
				{
					final double rosterHp = roster.stream().mapToDouble(s -> Double.parseDouble(s.image().field("curHp"))).sum(), rosterMp = roster.stream().mapToDouble(s -> Double.parseDouble(s.image().field("curMp"))).sum();
					final String sourceFacts = hash(roster.stream().map(s -> s.image().sha() + "/" + s.image().physicalFingerprint() + "/" + s.inventory().fingerprint()).toList().toString());
					for (var candidate : scenes)
					{
						final var npc = candidate.exemplar(); final var bound = admitted.get(candidate.scene().id());
						final double minimumMp = source.inventory().abilities().stream().filter(a -> bound.shortlist().candidates().contains(a.skill())).mapToDouble(PhantomSkillInventory.Ability::mpCost).min().orElseThrow();
						candidateFacts.add(new PhantomEvaluationCurriculumV2.Facts(candidate.scene().id(), npc.getLevel() - roster.stream().mapToInt(s -> s.seed().level()).max().orElseThrow(),
							npc.getMaxHp(), npc.getPDef(actor), npc.getMDef(actor, null), npc.getPAtk(actor), npc.getMAtk(actor, null), rosterHp, rosterMp, bound.shortlist().candidates().size(), minimumMp, sourceFacts));
					}
				}
				if (outcomeV2(config))
				{
					final String catalog = PhantomEvaluationCurriculumV2.proposals(candidateFacts, config.seed());
					immutable(directory.resolve("curriculum-candidates-v2." + hash(catalog) + ".json"), catalog);
				}
				// The acquired scene page is already seed ordered. No strata exist until full native A/B admission.
				final var candidateOrder = nativeProbePage(scenes, outcomeV2(config));
				if (outcomeV2(config) && candidateOrder.isEmpty()) { refusals.add("EVAL012_NO_ELIGIBLE_PROPOSALS:" + (party ? "PARTY" : "SOLO")); }
				for (var candidate : candidateOrder)
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
					final String key = comparisonCellKey(planned.size(), source.image().physicalFingerprint(), proposal.pool().key(), party, scene.id(), roster.stream().map(s -> s.image().physicalFingerprint()).toList(), outcomeV2(config));
					final var cell = new Cell(key, admittedProposal, List.copyOf(roster), scene, targetPoint.x(), targetPoint.y(), targetPoint.z());
					if (nativeAdmission != null && !nativeAdmission.admit(cell, planned.size()))
					{ refusals.add("HEADLESS_NATIVE_ADMISSION_REFUSED:" + key); continue; }
					cursors.put(admittedProposal.shortlistKey(), admittedProposal.shortlist().nextCursor());
					if (outcomeV2(config))
					{
						final var pair = PhantomEvaluationReplayV2.object(directory.resolve(key + ".paired-native-v2.json")); final var f = PhantomEvaluationReplayV2.map(pair.get("curriculumFacts"));
						nativeFacts.add(new PhantomEvaluationCurriculumV2.Facts(key, (int) PhantomEvaluationReplayV2.number(f, "levelGap"), PhantomEvaluationReplayV2.real(f, "npcHp"),
							PhantomEvaluationReplayV2.real(f, "npcPDef"), PhantomEvaluationReplayV2.real(f, "npcMDef"), PhantomEvaluationReplayV2.real(f, "npcPAtk"), PhantomEvaluationReplayV2.real(f, "npcMAtk"),
							PhantomEvaluationReplayV2.real(f, "rosterHp"), PhantomEvaluationReplayV2.real(f, "rosterMp"), (int) PhantomEvaluationReplayV2.number(f, "targetBoundAlternatives"),
							PhantomEvaluationReplayV2.real(f, "minimumActionMp"), PhantomEvaluationReplayV2.string(f, "sourceFactsSha")));
					}
					planned.add(cell); break;
				}
			}
			catch (IllegalStateException failure)
			{
				if (!outcomeV2(config) || !"AUTONOMOUS_HEADLESS_CANDIDATE_BUDGET".equals(failure.getMessage())) { throw failure; }
				refusals.add(failure.getMessage()); break;
			}
			finally { if (probe != null) { probe.stopAllTasks(); probe.deleteMe(); } }
		}
		if (outcomeV2(config))
		{
			final var directions = planned.stream().collect(java.util.stream.Collectors.toMap(Cell::key, PhantomAutonomousTrainingNative::evaluationDirection));
			final var selected = PhantomEvaluationCurriculumV2.selectAdmitted(nativeFacts, directions.keySet(), directions, config.seed());
			final var qualified = new ArrayList<Map<String, Object>>();
			for (var f : nativeFacts)
			{
				final var group = nativeFacts.stream().filter(value -> directions.get(f.key()).equals(directions.get(value.key()))).toList();
				final var row = new java.util.LinkedHashMap<String, Object>(f.json(PhantomEvaluationCurriculumV2.classify(group).get(f.key())));
				row.put("direction", directions.get(f.key())); row.put("selected", selected.containsKey(f.key()));
				row.put("pairedNativeSha", NeuralPolicy.sha256(Files.readAllBytes(directory.resolve(f.key() + ".paired-native-v2.json")))); qualified.add(row);
			}
			final var catalog = PhantomEvaluationOutcomeV2.fields("schema", "EVAL_NATIVE_QUALIFIED_CANDIDATES_V2", "configSha", config.sha256(), "seed", config.seed(),
				"fullNativeAdmission", !qualified.isEmpty(), "fullCampaignReady", selected.size() == 4, "afterCut", false, "usesOutcomesOrScores", false, "candidates", qualified);
			immutable(directory.resolve("curriculum-native-qualified-v2.json"), PhantomEvaluationOutcomeV2.json(catalog));
			planned.removeIf(cell -> !selected.containsKey(cell.key()));
			planned.sort(Comparator.comparing((Cell cell) -> evaluationDirection(cell)).thenComparingInt(cell -> selected.get(cell.key()).equals("LOWER_PRECUT_BURDEN") ? 0 : 1));
			for (var cell : planned)
			{
				final var f = nativeFacts.stream().filter(value -> value.key().equals(cell.key())).findFirst().orElseThrow();
				curriculumRows.add(PhantomEvaluationOutcomeV2.fields("cell", cell.key(), "direction", evaluationDirection(cell), "stratum", selected.get(cell.key()), "burdenVector", f.burden(),
					"selectedCandidate", cell.scene().id(), "physicalSha", Files.readString(directory.resolve(cell.key() + ".physical.sha")),
					"pairedNativeSha", NeuralPolicy.sha256(Files.readAllBytes(directory.resolve(cell.key() + ".paired-native-v2.json"))), "catalog", catalog));
			}
		}
		final var lines = new ArrayList<String>(); lines.add("cell\tsourceShas\tfamily\tmode\tshortlist\tcursor\tscene\tsource\tnpc\tx\ty\tz\ttargetX\ttargetY\ttargetZ\tprotocolSha");
		for (var cell : planned)
		{
			final var scene = cell.scene(); final var p = cell.proposal();
			lines.add(String.join("\t", cell.key(), cell.roster().stream().map(s -> s.image().sha()).collect(java.util.stream.Collectors.joining(",")), p.pool().family().name(), p.pool().mode() == null ? "PARTY" : p.pool().mode().name(), p.shortlist().candidates().stream().map(r -> r.skillId() + ":" + r.skillLevel()).collect(java.util.stream.Collectors.joining(",")), Long.toString(p.shortlist().nextCursor()), p.actor().sourceSnapshotSha() + ":" + scene.id(), scene.source(), Integer.toString(scene.npc()), Integer.toString(scene.x()), Integer.toString(scene.y()), Integer.toString(scene.z()), Integer.toString(cell.x()), Integer.toString(cell.y()), Integer.toString(cell.z()), hash(EncounterAdmissionV1.VERSION + "/" + config.sha256())));
		}
		PhantomTrainingScenarios.requirePlanningActive(requested); immutable(sealed, String.join("\n", lines) + "\n"); immutable(directory.resolve("planning-refusals.json"), "{\"refusals\":[" + refusals.samples.stream().map(PhantomAutonomousTrainingNative::q).collect(java.util.stream.Collectors.joining(",")) + "],\"sampleLimit\":128,\"counts\":" + PhantomLearning005Evaluation.mapJson(refusals.counts) + ",\"plannedCells\":" + planned.size() + "}");
		if (outcomeV2(config)) { immutable(directory.resolve("curriculum-v2.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_CURRICULUM_CATALOG_V2", "configSha", config.sha256(), "seed", config.seed(), "cells", curriculumRows, "usesOutcomesOrScores", false))); }
		immutable(directory.resolve("scene-catalog-counts.json"), PhantomLearning005Evaluation.mapJson(sceneCounts));
		if (planned.isEmpty())
		{
			immutable(directory.resolve("unsatisfiable.json"), "{\"schema\":" + q(EncounterAdmissionV1.VERSION) + ",\"scope\":\"BOUNDED_SOURCE_CATALOG\",\"globalPveImpossible\":false,\"sourceCount\":" + sources.size()
				+ ",\"capabilityBindings\":" + q(sources.stream().map(s -> s.image().sha() + ":" + s.inventory().fingerprint()).toList().toString())
				+ ",\"configSha\":" + q(config.sha256()) + ",\"counts\":" + PhantomLearning005Evaluation.mapJson(sceneCounts) + ",\"gameServerStartAllowed\":false}");
		}
		return List.copyOf(planned);
	}
	/** The same explicit native admission runs before the bounded scene page in TRAIN and EVAL. */
	private static java.util.Optional<AutonomousTrainingV1.Proposal> admitScene(AutonomousTrainingV1.Proposal proposal, Source source,
		List<Source> roster, PhantomTrainingScenarios.AcquiredScene candidate, Player actor, Refusals refusals)
	{
		final var target = candidate.exemplar(); final var scene = candidate.scene(); final var point = candidate.target();
		if (!target.isSpawned() || target.isAlikeDead() || !target.getAttackByList().isEmpty() || !openingGeometry(scene, point.x(), point.y(), point.z())) { return java.util.Optional.empty(); }
		final var admitted = admitTarget(proposal, source, target, actor, refusals);
		if (admitted.isEmpty()) { return admitted; }
		if (roster.size() > 1 && !targetBoundHelper(new Cell("planning", admitted.orElseThrow(), roster, scene, point.x(), point.y(), point.z()), target))
		{ refusals.add("TARGET_BOUND_HELPER_MAGIC_ENVELOPE:" + source.image().sha() + ":npc=" + target.getId() + ":level=" + target.getLevel()); return java.util.Optional.empty(); }
		return admitted;
	}
	/** Capability-to-target admission precedes placement; scanning more coordinates cannot repair acquired ranks. */
	private static java.util.Optional<AutonomousTrainingV1.Proposal> admitTarget(AutonomousTrainingV1.Proposal proposal, Source source,
		Monster target, Player actor, Refusals refusals)
	{
		actor.setXYZ(target.getX() + 180, target.getY(), target.getZ());
		var admitted = java.util.Optional.of(proposal);
		if (proposal.pool().family() == LegalActionV3.Family.DAMAGE)
		{
			final var targetFacts = org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.nativePlanningTarget(target, true);
			final var eligible = proposal.pool().skills().stream().filter(ref ->
				{
					final var mechanics = org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.mechanics(actor.getKnownSkill(ref.skillId()));
					return mechanics != null && org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.magicLevelReady(mechanics, actor.getLevel(), target.getLevel());
				}).filter(ref -> source.inventory().abilities().stream().filter(a -> a.skill().equals(ref)).anyMatch(a -> PhantomSkillInventory.eligibility(actor, target, a, () -> true, true).ready()))
				.filter(ref ->
				{
					final var combat = org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.nativePlanningCombat(actor, target, actor.getKnownSkill(ref.skillId()));
					final var mechanics = combat.forTarget(targetFacts);
					return mechanics != null && org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel.estimateCastCycle(combat.combat(), targetFacts, mechanics).isPresent();
				}).toList();
			admitted = targetBoundProposal(proposal, eligible);
			if (admitted.isEmpty()) { refusals.add("TARGET_BOUND_ALTERNATIVES:" + source.image().sha() + ":npc=" + target.getId() + ":level=" + target.getLevel() + ":eligible=" + eligible.size() + ":full=" + proposal.pool().skills().size()); return admitted; }
		}
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
	private static void requirePhysicalParity(String expected, Map<String, String> current)
	{ if (!expected.equals(hash(PhantomLearning005Evaluation.mapJson(current)))) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_PHYSICAL_FACTS_CHANGED"); } }
	/** Last full read before the durable CUT; never repair a changed World or resource value here. */
	private static void requireFreshEncounterFacts(Cell cell, TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority,
		PhantomLearning001ShadowNative.Runtime runtime, List<Player> players, Monster target, long targetEpoch,
		List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef> actions, long learnerProfile, Map<String, String> facts, String physical) throws Exception
	{
		if (org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(target) != targetEpoch || target.getX() != cell.x() || target.getY() != cell.y() || target.getZ() != cell.z()
			|| target.isMoving() || target.isInCombat() || !target.getAttackByList().isEmpty() || !openingGeometry(cell.scene(), target.getX(), target.getY(), target.getZ()))
		{ throw new IllegalStateException("AUTONOMOUS_PRE_CUT_WORLD_AI_MOVEMENT"); }
		if (!PhantomTrainingScenarios.factionIsolated(target, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId()), target))
		{ throw new IllegalStateException("AUTONOMOUS_PRE_CUT_NPC_FACTION_NOT_ISOLATED"); }
		for (int index = 0; index < players.size(); index++)
		{
			if (!PhantomSkillInventory.acquired(players.get(index), authority.progression()).fingerprint().equals(cell.roster().get(index).inventory().fingerprint()))
			{ throw new IllegalStateException("AUTONOMOUS_PRE_CUT_ACQUIRED_INVENTORY_CHANGED"); }
		}
		final var learner = players.get(cell.learnerIndex()); final var inventory = PhantomSkillInventory.acquired(learner, authority.progression());
		if (cell.proposal().pool().family() == LegalActionV3.Family.DAMAGE)
		{
			if (actions.stream().filter(ref -> inventory.abilities().stream().filter(ability -> ability.skill().equals(ref)).anyMatch(ability ->
				PhantomSkillInventory.eligibility(learner, target, ability, () -> learner.getNativeWorkOwner() != null && learner.getNativeWorkOwner().isCurrent(), true).ready())).count() < 2)
			{ throw new IllegalStateException("AUTONOMOUS_PRE_CUT_TWO_READY_ALTERNATIVES"); }
		}
		else
		{
			final var offers = runtime.inspectHeal().stream().filter(value -> value.actor().profileId() == learnerProfile && value.kind() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind.HEAL_MEMBER).toList();
			if (offers.size() < 2 || !offers.stream().map(value -> new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef(value.actionSkillId(), value.actionSkillLevel())).distinct().toList().equals(actions))
			{ throw new IllegalStateException("AUTONOMOUS_PRE_CUT_HEAL_READINESS_CHANGED"); }
		}
		final var current = new java.util.TreeMap<>(facts); current.putAll(PhantomLearning005Native.facts(players, target, cell.scene(), config));
		requirePhysicalParity(physical, current); runtime.revalidateAcquiredEncounter();
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
			final var sources = trainingSources013(root, discoverSources(root, directory, config, authority, () -> requested(attempt)), config); final var cells = planCells(directory, sources, config, authority, () -> requested(attempt));
			if (config.integer("autonomous013Version", 0, 0, 1) == 1)
			{
				final var admission = PhantomEvaluationReplayV2.object(directory.resolve("training-admission013.json"));
				if (!config.sha256().equals(admission.get("configSha")) || !hash(Files.readString(directory.resolve("cells.tsv"))).equals(admission.get("cellsSha"))) { throw new IllegalStateException("TRAIN013_PREPARED_COHORT_CHANGED"); }
			}
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
				// Preserve restored resources. Preparation uses ordinary native recovery, without synthetic injuries.
				final long profile = profiles.create(player.getObjectId()).profileId();
				final String riskLine = index + "\t" + source.image().physicalFingerprint() + "\t" + risk.limitPercent(profile, false) + "\t" + risk.limitPercent(profile, true);
				if (!pinned.isEmpty() && (profile != expectedProfile || !riskLine.equals(String.join("\t", pinned.get(index))))) { throw new IllegalStateException("AUTONOMOUS_EVAL_RISK_PROFILE_CHANGED"); }
				riskLines.add(riskLine);
				final var planner = new PhantomHistoricalBackgroundPlanner(authority.knowledge(), authority.topology(), authority.authority());
				final var local = PhantomEncounterLocalSource.resolve(authority, cell.scene(), new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(cell.x(), cell.y(), cell.z(), 0));
				final org.l2jmobius.gameserver.phantoms.decision.PhantomGoal goal;
				if (local.isPresent()) { goal = PhantomEncounterLocalSource.goal(profile, local.orElseThrow()); }
				else
				{
					final var initial = planner.planInitial(profile, player, config.seed(), 1); final var baseline = (initial.ready() ? initial : planner.idleInitial(profile, player, config.seed(), 1)).goal();
					if (baseline == null) { throw new IllegalStateException("AUTONOMOUS_ORDINARY_GOAL_UNAVAILABLE"); }
					goal = planner.replanLogicalRendezvous(profile, authority.authority().capture(profile, player, baseline, null), baseline, config.seed(), 2, new PhantomDomainRef("background.farm", cell.scene().source())).goal();
				}
				if (goal == null) { throw new IllegalStateException("AUTONOMOUS_ORDINARY_GOAL_REFUSED"); }
				final var goals = new PhantomGoalStateStore(profiles); goals.insert(profile, goal);
				if (!goals.load(profile).orElseThrow().goal().equals(goal)) { throw new IllegalStateException("AUTONOMOUS_GOAL_READBACK_CHANGED"); }
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
		Monster target = null; TacticalEpisodeV3 episode = null; final var cut = new java.util.concurrent.atomic.AtomicBoolean(); String exclusion = "", physical = ""; final long started = System.nanoTime();
		List<PhantomLearning005Evaluation.Incarnation> actors = List.of(); PhantomLearning005Evaluation.Incarnation targetIdentity = null;
		List<org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.SkillRef> frozenActions = proposal.shortlist().candidates();
		try (var runtime = new PhantomLearning001ShadowNative.Runtime(authority, root.resolve("game"), policy, attempt.resolve("stop.request"), true, config.maxActiveActors(), tactical, true, source))
		{
			java.util.function.Supplier<TacticalEpisodeV3> terminal = null;
			try
			{
				final var roster = reproduceRoster(authority, cell, work, slot.split(), config, evaluation && cell.party() ? directory.resolve(cell.key() + ".risk.tsv") : null); final var inventories = new HashMap<Long, String>();
				for (int i = 0; i < roster.size(); i++) { inventories.put(Long.parseLong(roster.get(i)[2]), cell.roster().get(i).inventory().fingerprint()); }
				final var localSource = PhantomEncounterLocalSource.resolve(authority, scene,
					new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(cell.x(), cell.y(), cell.z(), 0));
				runtime.localEncounterSource(localSource.orElse(null));
				runtime.prepareAcquired(scene, roster, cell.party(), proposal.pool().mode(), proposal.shortlist().candidates(), inventories, config.preparationSeconds(), () -> requested(attempt));
				immutable(work.resolve("shared-source.txt"), runtime.acquiredSourceFacts());
				for (int candidate = 0; candidate < 4; candidate++)
				{
					final var spawn = localSource.isPresent() ? PhantomEncounterLocalSource.realize(localSource.orElseThrow()) : new Spawn(org.l2jmobius.gameserver.data.xml.NpcData.getInstance().getTemplate(scene.npc()));
					if (localSource.isEmpty()) { spawn.setXYZ(cell.x(), cell.y(), cell.z()); }
					final var npc = (Monster) spawn.doSpawn(false); spawn.stopRespawn();
					final boolean admitted = !npc.isChampion() && !npc.isRaid() && !npc.isAggressive() && !npc.isAlikeDead() && npc.getCurrentHp() == npc.getMaxHp();
					Files.writeString(work.resolve("admissions.jsonl"), "{\"candidate\":" + candidate + ",\"objectId\":" + npc.getObjectId() + ",\"admitted\":" + admitted + "}\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					if (admitted) { target = npc; break; } npc.deleteMe();
				}
				if (target == null || !openingGeometry(scene, cell.x(), cell.y(), cell.z())) { throw new IllegalStateException("AUTONOMOUS_FRESH_TARGET_OR_GEO_REFUSED"); }
				if (localSource.isPresent()) { immutable(work.resolve("local-source-binding.json"), PhantomLearning005Evaluation.mapJson(PhantomEncounterLocalSource.diagnostics(localSource.orElseThrow(), target))); }
				target.setHeading(0); runtime.prepareAcquiredOutcome(target);
				// A proposal is not a frozen World. Refuse drift before CUT instead of resetting native NPC state.
				if (target.getX() != cell.x() || target.getY() != cell.y() || target.getZ() != cell.z() || target.isMoving()
					|| target.isInCombat() || !target.getAttackByList().isEmpty()) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_WORLD_AI_MOVEMENT"); }
				final var players = runtime.evaluationPlayers();
				// Warm read-only snapshot/encoder paths before the native decision budget; resources remain natural.
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
					// Passive constructor proof with the exact native SOLO parameter shape; no dispatch.
					final var request = soloRequestProbe(cell.party(), Long.parseLong(roster.get(cell.learnerIndex())[2]), target.getObjectId(), proposal.pool().mode(), config.episodeSeconds() * 1000L);
					if (request != null) { immutable(work.resolve("combat-request.json"), "{\"validated\":true,\"dispatched\":false,\"profile\":" + request.profileId() + ",\"target\":" + request.targetObjectId() + ",\"mode\":" + q(request.mode().name()) + ",\"timeoutMillis\":" + request.timeoutMillis() + ",\"maximumTimeoutMillis\":" + org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.MAXIMUM_TIMEOUT_MILLIS + "}"); }
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
				if (cell.party())
				{
					final var admission = runtime.acquiredPartyAdmission(); immutable(work.resolve("party-admission.json"), PhantomLearning005Evaluation.mapJson(admission));
					if (!Boolean.TRUE.equals(admission.get("admitted"))) { throw new IllegalStateException(preCutSelectionRefusal(admission, evaluation && outcomeV2(config))); }
					if (evaluation && !Boolean.TRUE.equals(admission.get("combatRequestValidated"))) { throw new IllegalStateException("AUTONOMOUS_PARTY_REQUEST_PREFLIGHT_REQUIRED"); }
				}
				final var nativeAdmission = runtime.acquiredEncounterAdmission(config.episodeSeconds() * 1000L);
				immutable(work.resolve("native-admission.json"), PhantomLearning005Evaluation.mapJson(nativeAdmission));
				facts.put("encounterContract", EncounterAdmissionV1.VERSION);
				facts.put("nativeOpeningAdmission", "ACCEPTED/" + encounterProposal(cell, config).requestTimeoutMillis());
				physical = hash(PhantomLearning005Evaluation.mapJson(facts));
				immutable(work.resolve("admission-facts.json"), PhantomLearning005Evaluation.mapJson(facts));
				final var witness = new EncounterAdmissionV1.Receipt(encounterProposal(cell, config), admissionOnly ? EncounterAdmissionV1.Stage.HEADLESS_READY : EncounterAdmissionV1.Stage.LIVE_ADMITTED, physical, List.of());
				if (evaluation && !admissionOnly)
				{
					final Path expected = preparedDirectory(root, config).resolve(cell.key() + ".physical.sha");
					if (!Files.isRegularFile(expected) || !expected.equals(expected.toRealPath())) { throw new IllegalStateException("AUTONOMOUS_PREPARED_PHYSICAL_FACTS_REQUIRED"); }
					final var prepared = new EncounterAdmissionV1.Receipt(witness.proposal(), EncounterAdmissionV1.Stage.HEADLESS_READY, Files.readString(expected), List.of());
					final var diff = EncounterAdmissionV1.parity(prepared, witness);
					immutable(work.resolve("headless-live-parity.json"), "{\"proposalSha\":" + q(witness.proposal().fingerprint()) + ",\"expectedPhysicalSha\":" + q(prepared.physicalSha()) + ",\"actualPhysicalSha\":" + q(physical) + ",\"diff\":" + q(diff.toString()) + "}");
					if (!diff.isEmpty()) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_HEADLESS_LIVE_PARITY_REJECT"); }
				}
				immutable(work.resolve("encounter-admission.json"), "{\"schema\":" + q(EncounterAdmissionV1.VERSION) + ",\"stage\":" + q(witness.stage().name()) + ",\"proposalSha\":" + q(witness.proposal().fingerprint())
					+ ",\"physicalSha\":" + q(physical) + ",\"sealable\":" + witness.sealable() + ",\"afterCut\":false,\"trainingCredit\":false}");
				final Path match = directory.resolve(cell.key() + ".physical.sha"); if (Files.exists(match) && !Files.readString(match).equals(physical)) { throw new IllegalStateException("AUTONOMOUS_PRE_CUT_MATCHED_PHYSICAL_FACTS_DIFFER"); } immutable(match, physical);
				immutable(work.resolve("precut.json"), "{\"comparisonCellSha\":" + q(physical) + ",\"facts\":" + PhantomLearning005Evaluation.mapJson(facts) + ",\"actorProfile\":" + roster.get(cell.learnerIndex())[2] + "}");
				actors = players.stream().map(player -> new PhantomLearning005Evaluation.Incarnation(player.getObjectId(), org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(player), player.getInstanceId())).toList();
				targetIdentity = new PhantomLearning005Evaluation.Incarnation(target.getObjectId(), org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(target), target.getInstanceId());
				immutable(work.resolve("incarnations.tsv"), "role\tobjectId\tepoch\tinstance\n" + players.stream().map(player -> "ACTOR\t" + player.getObjectId() + "\t" + org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(player) + "\t" + player.getInstanceId()).collect(java.util.stream.Collectors.joining("\n"))
					+ "\nTARGET\t" + target.getObjectId() + "\t" + org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(target) + "\t" + target.getInstanceId() + "\n");
				final Monster admittedTarget = target; final long admittedEpoch = org.l2jmobius.gameserver.phantoms.learning.TacticalSnapshotV3.epoch(target);
				final var admittedActions = List.copyOf(frozenActions); final var admittedFacts = Map.copyOf(facts); final String admittedPhysical = physical;
				final long learnerProfile = Long.parseLong(roster.get(cell.learnerIndex())[2]);
				if (admissionOnly)
				{
					requireFreshEncounterFacts(cell, config, authority, runtime, players, admittedTarget, admittedEpoch, admittedActions, learnerProfile, admittedFacts, admittedPhysical);
					if (!receipts.isEmpty() || !failures.isEmpty()) { throw new IllegalStateException("AUTONOMOUS_ADMISSION_NATIVE_ACTION_OBSERVED"); }
					if (outcomeV2(config))
					{
						final var acquired = PhantomSkillInventory.acquired(learner, authority.progression());
						final double minimumMp = acquired.abilities().stream().filter(a -> admittedActions.contains(a.skill())).mapToDouble(PhantomSkillInventory.Ability::mpCost).min().orElseThrow();
						final int alternatives = family == LegalActionV3.Family.HEAL ? (int) runtime.inspectHeal().stream().filter(value -> value.actor().profileId() == learnerProfile
							&& value.kind() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind.HEAL_MEMBER).count() : admittedActions.size();
						final String sourceFacts = hash(cell.roster().stream().map(s -> s.image().sha() + "/" + s.image().physicalFingerprint() + "/" + s.inventory().fingerprint()).toList().toString());
						final var fullFacts = new PhantomEvaluationCurriculumV2.Facts(cell.key(), target.getLevel() - players.stream().mapToInt(Player::getLevel).max().orElseThrow(),
							target.getMaxHp(), target.getPDef(learner), target.getMDef(learner, null), target.getPAtk(learner), target.getMAtk(learner, null),
							players.stream().mapToDouble(Player::getCurrentHp).sum(), players.stream().mapToDouble(Player::getCurrentMp).sum(), alternatives, minimumMp, sourceFacts);
						immutable(work.resolve("curriculum-native-facts-v2.json"), PhantomEvaluationOutcomeV2.json(fullFacts.json("PENDING_PAIRED_NATIVE_ADMISSION")));
					}
					immutable(work.resolve("admission-only.json"), "{\"admitted\":true,\"afterCut\":false,\"gameServerStarted\":false,\"nativeActionsObserved\":0,\"trainingCredit\":false}"); return new EvaluationArm(physical, actors, targetIdentity, false, List.of());
				}
				final var window = evaluation ? null : TrainExplorationV1.ownedAutonomous(root, id, slot.block(), physical, slot.split(), slot.experimental() && config.text("autonomous009Exploration", "true").equals("true"), family, Long.parseLong(roster.get(cell.learnerIndex())[2]), model.id(), model.sha(), frozenActions, selections::add);
				final String before = "{\"comparisonCellSha\":" + q(physical) + ",\"comparator\":" + q(AutonomousTrainingV1.COMPARATOR) + ",\"runtime\":" + runtime.outcomeBeforeFacts() + ",\"cell\":" + q(cell.key()) + "}";
				final long generation = Long.parseLong(new String(Files.readAllBytes(root.resolve("director.json")), StandardCharsets.UTF_8).replaceAll("(?s).*\"generation\"\\s*:\\s*([0-9]+).*", "$1"));
				final var identity = target.getNativeEvidenceTarget(); receipts.clear(); failures.clear(); timings.clear();
				runtime.revalidateAcquiredEncounter();
				terminal = runtime.runLearning006Encounter(id, attempt.getFileName().toString(), config.episodeSeconds(), () -> requested(attempt), () -> List.copyOf(receipts), stamp ->
				{ try { runtime.revalidateAcquiredEncounter(); requireFreshEncounterFacts(cell, config, authority, runtime, players, admittedTarget, admittedEpoch, admittedActions, learnerProfile, admittedFacts, admittedPhysical);
					if (!evaluation) { store.beginTerminalEpisode(id, attempt.getFileName().toString(), cell.party() ? "PARTY" : "SOLO", before, generation, identity.instanceId(), identity.objectId(), identity.spawnGeneration(), stamp); tactical.trainExploration(window); } else { immutable(work.resolve("cut.json"), before.substring(0, before.length() - 1) + ",\"cutNanos\":" + stamp + ",\"trainingCredit\":false}"); } cut.set(true);
				} catch (IllegalStateException refused) { throw refused; } catch (Exception failure) { throw new IllegalStateException("AUTONOMOUS_DURABLE_CUT_FAILED", failure); } });
			}
			catch (IllegalStateException refused)
			{ if (cut.get()) { throw refused; } exclusion = safeReason(refused); immutable(work.resolve("precut-refused.json"), "{\"schema\":" + q(EncounterAdmissionV1.VERSION) + ",\"reason\":" + q(exclusion)
				+ ",\"stage\":\"PRE_CUT\",\"sourceSha\":" + q(proposal.actor().sourceSnapshotSha()) + ",\"capabilityShas\":" + q(cell.roster().stream().map(s -> s.inventory().fingerprint()).toList().toString())
				+ ",\"configSha\":" + q(config.sha256()) + ",\"npc\":" + scene.npc() + ",\"source\":" + q(scene.source()) + ",\"placement\":" + q(cell.x() + ":" + cell.y() + ":" + cell.z())
				+ ",\"requestTimeoutMillis\":" + encounterProposal(cell, config).requestTimeoutMillis() + ",\"afterCut\":false,\"trainingCredit\":false}"); }
			finally { tactical.trainExploration(null); if (terminal == null) { runtime.cancelAcquiredEncounter(); runtime.settleTraining(); } else { episode = runtime.settleLearning004(terminal).episode(); } runtime.releaseOutcome006(); }
			final String context = "autonomous009|" + config.text("outcome006DatasetId", "") + "|" + slot.split() + "|" + id;
			if (episode != null)
			{
				if (!evaluation) { for (var receipt : receipts) { store.record(receipt, slot.split(), context); } for (var failure : failures) { store.recordFailure(failure, slot.split(), context, id); } store.settleEpisode(episode); }
				immutable(work.resolve("settlement.json"), episode.json());
				if (evaluation && outcomeV2(config))
				{
					immutable(work.resolve("terminal-boundary-v2.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_BOUNDARY_V2", "episode", episode.id(), "ownerNonce", episode.ownerNonce(),
						"cutNanos", runtime.outcomeCutNanos(), "terminalNanos", runtime.outcomeTerminalNanos(), "settledNanos", episode.settledNanos(), "deadlineNanos", Math.addExact(runtime.outcomeCutNanos(), config.episodeSeconds() * 1_000_000_000L),
						"nativeAdmitted", runtime.outcomeEncounterAdmitted(), "stopRequested", requested(attempt) || episode.reason().equals("REQUESTED_DRAIN"), "settlementSha", episode.sha256(), "trainingCredit", false)));
				}
				for (var receipt : receipts)
				{
					final String nativeJson = PhantomLearning005Native.nativeReceiptJson(receipt); immutable(work.resolve(receipt.eventKey() + ".native.json"), nativeJson);
					if (evaluation && outcomeV2(config))
					{
						final var observation = receipt.attempt().observation();
						immutable(work.resolve(receipt.eventKey() + ".native-authority-v2.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_NATIVE_AUTHORITY_V2", "eventKey", receipt.eventKey(),
							"ownerGeneration", receipt.owner().generation(), "operationKey", receipt.owner().operationKey(), "decisionId", observation.decisionId(), "actorObjectId", observation.actorObjectId(), "actorEpoch", observation.actorEpoch(),
							"nativeRecordSha", hash(nativeJson), "trainingCredit", false)));
					}
				}
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
			final var arm = new EvaluationArm(physical, actors, targetIdentity, cut.get() && episode != null && episode.outcome().equals("COMPLETED"), List.copyOf(paid), cut.get() ? "" : exclusion);
			if (evaluation) { immutable(work.resolve("evaluation-arm.tsv"), encodeArm(arm)); }
			return arm;
		}
		finally { if (target != null) { target.deleteMe(); } TerminalRewardReceiptV1.nativeTimingObserver(_ -> { }); final long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos(); while (!policy.stop() && System.nanoTime() < deadline) { Thread.sleep(50); } }
	}
	public static void main(String[] args)
	{
		try
		{
			if ((args.length == 2 || args.length == 3) && args[0].equals("verify-eval013")) { verifyEvaluation013(Path.of(args[1]), args.length == 3 ? Path.of(args[2]) : null); return; }
			if (args.length == 1 && args[0].equals("source-contract")) { sourceContract(); return; }
			if (args.length == 2 && args[0].equals("diagnose-cohort")) { diagnoseCohort(Path.of(args[1])); return; }
			if (args.length != 2 || !List.of("inspect", "prepare", "admit-eval", "checkpoint", "checkpoint013").contains(args[0])) { throw new IllegalArgumentException("Ожидается inspect/prepare/admit-eval/checkpoint/checkpoint013 <собственная установка>."); }
			if (args[0].equals("prepare") || args[0].equals("admit-eval")) { prepareSources(Path.of(args[1]), args[0].equals("admit-eval")); } else if (args[0].startsWith("checkpoint")) { checkpoint(Path.of(args[1]), args[0].equals("checkpoint013")); } else { inspect(Path.of(args[1])); }
			if (args[0].equals("admit-eval")) { System.exit(0); }
		}
		catch (Exception failure)
		{
			// JDBC/vendor messages can include credentials. Keep the normal safe worker diagnostic contract.
			System.err.println("AUTONOMOUS_NATIVE_FAILED " + safeReason(failure));
			java.util.Arrays.stream(failure.getStackTrace()).filter(frame -> frame.getClassName().startsWith("org.l2jmobius."))
				.limit(8).forEach(frame -> System.err.println("AUTONOMOUS_FAILURE_SITE " + frame));
			System.exit(2);
		}
	}
	/** Re-read genuine raw receipts through the existing V2 typed codec before a cycle credit. */
	private static void verifyEvaluation013(Path installation, Path configuration) throws Exception
	{
		final Path root = PhantomTrainingActors.physicalRoot(installation); final Path configPath = configuration == null ? root.resolve("TRAIN_CONFIG.ini") : configuration.toAbsolutePath().normalize();
		if (!configPath.equals(configPath.toRealPath())) { throw new IllegalStateException("TRAIN013_REPLAY_CONFIG_PATH"); }
		final var config = TrainingConfig.load(configPath);
		if (!outcomeV2(config)) { throw new IllegalStateException("TRAIN013_V2_READBACK_REQUIRED"); }
		final Path directory = root.resolve("autonomous/v1/campaigns").resolve(config.text("autonomous009Campaign", "")).resolve("evaluations")
			.resolve(config.text("outcome006DatasetId", "") + "-" + config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE));
		if (!directory.equals(directory.toRealPath()) || !Files.isRegularFile(directory.resolve("evaluation-v2.complete"))) { throw new IllegalStateException("TRAIN013_V2_READBACK_INCOMPLETE"); }
		final var protocol = PhantomEvaluationReplayV2.object(directory.resolve("protocol-v2.json"));
		final var pin = new PhantomEvaluationOutcomeV2.Authority(PhantomEvaluationReplayV2.string(protocol, "sourceSha"), PhantomEvaluationReplayV2.string(protocol, "buildSha"),
			PhantomEvaluationReplayV2.string(protocol, "configSha"), PhantomEvaluationReplayV2.number(protocol, "modelVersion"), PhantomEvaluationReplayV2.string(protocol, "modelSha"), PhantomEvaluationReplayV2.string(protocol, "trainingStateSha"));
		if (!pin.configSha().equals(config.sha256()) || !pin.modelSha().equals(config.text("autonomous009ModelSha", "")) || pin.modelVersion() != config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE)) { throw new IllegalStateException("TRAIN013_V2_READBACK_AUTHORITY"); }
		final var raw = PhantomEvaluationReplayV2.object(directory.resolve("evaluation-pairs-v2.json")); final var pairs = new ArrayList<PhantomEvaluationOutcomeV2.Pair>(); final var proofFiles = new java.util.TreeMap<String, String>();
		for (var value : PhantomEvaluationReplayV2.list(raw.get("pairs")))
		{
			final var row = PhantomEvaluationReplayV2.map(value); final String cell = PhantomEvaluationReplayV2.string(row, "cell"), direction = PhantomEvaluationReplayV2.string(row, "direction"), tier = PhantomEvaluationReplayV2.string(row, "stratum");
			final String first = PhantomEvaluationReplayV2.string(row, "order").equals("STOCK_LEARNED") ? "STOCK" : "LEARNED";
			final var arms = new ArrayList<PhantomEvaluationOutcomeV2.Arm>();
			for (String side : List.of("STOCK", "LEARNED"))
			{
				final Path work = directory.resolve(hash("EVAL/" + config.text("outcome006DatasetId", "") + "/" + cell + "/" + side));
				final var arm = PhantomEvaluationReplayV2.readArm(work, cell, direction, side.equals(first) ? 0 : 1, config.seed(), pin, tier, false, side);
				if (!PhantomEvaluationOutcomeV2.decode(Files.readAllBytes(work.resolve("evaluation-arm-v2.bin"))).equals(arm)
					|| !PhantomEvaluationOutcomeV2.decode(Files.readAllBytes(directory.resolve(cell + "." + side + ".eval-arm-v2.bin"))).equals(arm)) { throw new IllegalStateException("TRAIN013_V2_TYPED_ARM_CHANGED"); }
				for (var entry : arm.sourceHashes().entrySet())
				{
					final Path file = work.resolve(entry.getKey());
					if (!file.equals(file.toRealPath()) || !NeuralPolicy.sha256(Files.readAllBytes(file)).equals(entry.getValue())) { throw new IllegalStateException("TRAIN013_V2_RAW_SOURCE_CHANGED"); }
					proofFiles.put(directory.relativize(file).toString().replace('\\', '/'), entry.getValue());
				}
				proofFiles.put(directory.relativize(work.resolve("evaluation-arm-v2.bin")).toString().replace('\\', '/'), NeuralPolicy.sha256(Files.readAllBytes(work.resolve("evaluation-arm-v2.bin"))));
				proofFiles.put(cell + "." + side + ".eval-arm-v2.bin", NeuralPolicy.sha256(PhantomEvaluationOutcomeV2.encode(arm)));
				arms.add(arm);
			}
			if (!Files.isRegularFile(directory.resolve(cell + ".eval-pair-v2.json"))) { throw new IllegalStateException("TRAIN013_V2_PAIR_MISSING"); }
			pairs.add(PhantomEvaluationOutcomeV2.compare(arms.getFirst(), arms.getLast()));
		}
		final var checked = PhantomEvaluationOutcomeV2.campaign(pairs);
		if (checked.size() < 4 || checked.stream().anyMatch(p -> !p.comparable())) { throw new IllegalStateException("TRAIN013_V2_TYPED_COVERAGE"); }
		for (var pair : checked)
		{
			final String name = pair.stock().pairId() + ".eval-pair-v2.json";
			final byte[] expected = PhantomEvaluationOutcomeV2.pairJson(pair).getBytes(StandardCharsets.UTF_8);
			if (!java.util.Arrays.equals(Files.readAllBytes(directory.resolve(name)), expected)) { throw new IllegalStateException("TRAIN013_V2_PAIR_BYTES_CHANGED"); }
			proofFiles.put(name, NeuralPolicy.sha256(expected));
		}
		final String aggregate = "{\"schema\":\"EVAL_PAIRS_V2\",\"terminalComparablePairs\":" + checked.stream().filter(PhantomEvaluationOutcomeV2.Pair::comparable).count()
			+ ",\"pairs\":[" + checked.stream().map(PhantomEvaluationOutcomeV2::pairJson).collect(java.util.stream.Collectors.joining(",")) + "],\"statistics\":" + PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.statistics(checked)) + ",\"trainingCredit\":false,\"verdict\":\"INCONCLUSIVE\"}";
		if (!java.util.Arrays.equals(Files.readAllBytes(directory.resolve("evaluation-pairs-v2.json")), aggregate.getBytes(StandardCharsets.UTF_8))) { throw new IllegalStateException("TRAIN013_V2_AGGREGATE_CHANGED"); }
		for (String name : List.of("protocol-v2.json", "evaluation-v2.complete", "evaluation-pairs-v2.json"))
		{ final Path file = directory.resolve(name); if (!file.equals(file.toRealPath())) { throw new IllegalStateException("TRAIN013_V2_PROOF_PATH"); } proofFiles.put(name, NeuralPolicy.sha256(Files.readAllBytes(file))); }
		System.out.println("TRAIN013_TYPED_EVAL_PROOF " + PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schemaVersion", 13, "directory", directory.toString(), "modelVersion", pin.modelVersion(), "modelSha", pin.modelSha(), "proofFiles", proofFiles, "trainingCredit", false)));
		System.out.println("TRAIN013_TYPED_EVAL_READBACK pairs=" + checked.size() + " paid=" + checked.stream().flatMap(p -> java.util.stream.Stream.of(p.stock(), p.learned())).mapToInt(a -> a.paid().size()).sum() + " trainingCredit=false");
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
		final int priorProbes = outcomeV2(config) ? priorNativeFirstProbes(directory) : 0;
		final var budget = new HeadlessBudget(outcomeV2(config) ? remainingProbeBudget(priorProbes) : 12); final boolean[] nativeProbed = { false };
		final List<Cell> cells;
		try
		{
		cells = planCells(directory, sources, config, authority, () -> requested(directory), (cell, ordinal) ->
		{
			if (!budget.claim(cell.key())) { return false; }
			if (!nativeProbed[0] && PhantomEncounterLocalSource.local(cell.scene().source()))
			{
				PhantomEncounterLocalSourceChecks.nativeSpawns(PhantomEncounterLocalSource.resolve(authority, cell.scene(),
					new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(cell.x(), cell.y(), cell.z(), 0)).orElseThrow(), directory);
				nativeProbed[0] = true;
			}
			return admitHeadlessCell(root, directory, config, authority, store, model, stock.model(), cell, ordinal);
		});
		}
		finally
		{
			if (outcomeV2(config)) { immutable(directory.resolve("headless-candidate-budget.json"), "{\"attempts\":" + budget.attempts + ",\"priorAttempts\":" + priorProbes + ",\"totalAttempts\":" + (priorProbes + budget.attempts) + ",\"maximum\":12,\"uniqueOnly\":true,\"afterCut\":false}"); }
		}
		try { requireEvaluationCoverage(cells); if (outcomeV2(config)) { requireV2Coverage(directory, cells, config); } }
		catch (IllegalStateException failure)
		{
			if (outcomeV2(config))
			{
				immutable(directory.resolve("unsatisfiable-native-v2.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_BOUNDED_UNSATISFIABLE_V2",
					"scope", "CURRENT_BOUNDED_SOURCE_AND_CANDIDATE_CATALOG", "reason", failure.getMessage(), "seed", config.seed(), "sourceCursor", config.longValue("autonomous009SourceCursor", 0, 0, Long.MAX_VALUE),
					"attempts", budget.attempts, "priorAttempts", priorProbes, "totalAttempts", priorProbes + budget.attempts, "maximum", 12, "selectedScenes", cells.size(), "afterCut", false, "gameServerStartAllowed", false, "globalPveImpossible", false,
					"sources", sources.stream().map(s -> PhantomEvaluationOutcomeV2.fields("sourceSha", s.image().sha(), "inventorySha", s.inventory().fingerprint(), "classId", s.seed().classId(), "level", s.seed().level(),
						"hp", Double.parseDouble(s.image().field("curHp")), "mp", Double.parseDouble(s.image().field("curMp")), "learned", s.seed().acquired().stream().map(ref -> PhantomEvaluationOutcomeV2.fields("id", ref.skillId(), "canonicalLevel", ref.skillLevel())).toList(),
						"supportedPools", s.seed().supported().stream().map(pool -> PhantomEvaluationOutcomeV2.fields("family", pool.family().name(), "mode", pool.mode() == null ? null : pool.mode().name(),
							"skills", pool.skills().stream().map(ref -> PhantomEvaluationOutcomeV2.fields("id", ref.skillId(), "canonicalLevel", ref.skillLevel())).toList())).toList())).toList())));
			}
			throw failure;
		}
		final List<Cell> selected = cells;
		if (!outcomeV2(config)) { immutable(directory.resolve("headless-candidate-budget.json"), "{\"attempts\":" + budget.attempts + ",\"maximum\":12,\"uniqueOnly\":true,\"afterCut\":false}"); }
		if (!before.equals(PhantomLearning005Native.trainingState(root, true))) { throw new IllegalStateException("AUTONOMOUS_EVAL_CHANGED_TRAINING_STATE"); }
		// Publish only a fully checked headless catalog. Live EVAL consumes these exact proposals and inventories.
		final Path prepared = preparedDirectory(root, config); requirePhysicalDirectory(root, prepared);
		copyPreparedArtifacts(directory, prepared, Files.readString(directory.resolve("cells.tsv")));
		immutable(prepared.resolve("headless.complete"), catalogFingerprint(root, directory, sources, config));
		immutable(directory.resolve("admission.complete.json"), "{\"admittedScenes\":" + selected.size() + ",\"directions\":[" + selected.stream().map(c -> q(evaluationDirection(c))).collect(java.util.stream.Collectors.joining(","))
			+ "],\"modelVersion\":" + model.id() + ",\"modelSha\":" + q(model.sha()) + ",\"trainingStateSha\":" + q(hash(before)) + ",\"precutArms\":" + (2 * selected.size()) + ",\"matchedPrecutFacts\":true,\"riskMatched\":true,\"episodeSeconds\":" + config.episodeSeconds() + ",\"afterCut\":false,\"gameServerStarted\":false,\"trainingStateUnchanged\":true,\"trainingCredit\":false}");
		System.out.println("AUTONOMOUS_EVAL_ADMISSION_GREEN scenes=" + selected.size() + " gameServerStarted=false afterCut=false session=" + directory);
	}
	private static boolean admitHeadlessCell(Path root, Path directory, TrainingConfig config, PhantomBackgroundSuite.ProductionAuthorityFixture authority,
		TacticalStoreV3 store, TacticalStoreV3.Checkpoint model, NeuralPolicy stock, Cell cell, int ordinal) throws Exception
	{
		PhantomTrainingScenarios.requirePlanningActive(() -> requested(directory)); final String id = hash("ADMISSION/" + cell.key());
		final var arms = new ArrayList<EvaluationArm>();
		for (String side : List.of("STOCK", "LEARNED"))
		{
			final String armId = hash(id + "/" + side); final Path work = directory.resolve(armId); Files.createDirectory(work);
			final var arm = collectEpisode(root, directory, config, authority, store, model, stock, directory, work, armId,
				new AutonomousTrainingV1.CohortSlot(ordinal, cell.key(), hash(id), "EVAL", false), cell, side, true);
			if (!Files.exists(work.resolve("admission-only.json")))
			{
				if (retryableSelectionRefusal(arm.preCutRefusal(), outcomeV2(config))) { return false; }
				throw new IllegalStateException(arm.preCutRefusal().isEmpty() ? "AUTONOMOUS_HEADLESS_RECEIPT_MISSING" : arm.preCutRefusal());
			}
			arms.add(arm);
		}
		if (!arms.getFirst().physical().equals(arms.getLast().physical()) || arms.getFirst().target().equals(arms.getLast().target())
			|| arms.getFirst().actors().stream().anyMatch(arms.getLast().actors()::contains)) { throw new IllegalStateException("AUTONOMOUS_PREFLIGHT_MATCH_OR_FRESHNESS_FAILED"); }
		if (outcomeV2(config))
		{
			if (arms.getFirst().target().objectId() == arms.getLast().target().objectId()
				|| arms.getFirst().actors().stream().anyMatch(a -> arms.getLast().actors().stream().anyMatch(b -> a.objectId() == b.objectId())))
			{ throw new IllegalStateException("EVAL012_PRE_CUT_REUSED_NATIVE_IDENTITY"); }
			final var proofArms = new ArrayList<Map<String, Object>>();
			String curriculumFacts = null;
			for (String side : List.of("STOCK", "LEARNED"))
			{
				final String workId = hash(id + "/" + side); final var raw = new java.util.TreeMap<String, String>();
				for (String name : List.of("admission-only.json", "native-admission.json", "encounter-admission.json", "precut.json", "incarnations.tsv", "curriculum-native-facts-v2.json"))
				{ raw.put(name, NeuralPolicy.sha256(Files.readAllBytes(directory.resolve(workId).resolve(name)))); }
				if (cell.party()) { raw.put("party-admission.json", NeuralPolicy.sha256(Files.readAllBytes(directory.resolve(workId).resolve("party-admission.json")))); }
				final String actualFacts = Files.readString(directory.resolve(workId).resolve("curriculum-native-facts-v2.json"));
				if (curriculumFacts != null && !curriculumFacts.equals(actualFacts)) { throw new IllegalStateException("EVAL012_PRE_CUT_CURRICULUM_PARITY"); } curriculumFacts = actualFacts;
				proofArms.add(PhantomEvaluationOutcomeV2.fields("side", side, "work", workId, "rawFileShas", raw));
			}
			immutable(directory.resolve(cell.key() + ".paired-native-v2.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_PRECUT_PAIR_V2",
				"cell", cell.key(), "configSha", config.sha256(), "proposalSha", encounterProposal(cell, config).fingerprint(), "direction", evaluationDirection(cell), "fullNativeAdmission", true, "matchedPhysical", true, "freshIncarnations", true,
				"physicalSha", arms.getFirst().physical(), "curriculumFacts", PhantomEvaluationReplayV2.parse(curriculumFacts), "afterCut", false, "trainingCredit", false, "arms", proofArms)));
		}
		return true;
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
		final Path phantomConfig = root.resolve("game/config/Custom/PhantomPlayers.ini"); final String off = Files.readString(phantomConfig);
		if (!off.matches("(?s).*EnablePhantomSystem\\s*=\\s*False.*")) { throw new IllegalStateException("AUTONOMOUS_MASTER_MUST_BE_OFF"); }
		try
		{
			Files.writeString(phantomConfig, PhantomTrainingNative.phaseConfiguration(off, config.text("learningMode", "SHADOW"), config.longValue("modelVersion", 0, 0, Long.MAX_VALUE), config.seed(), false));
			ConfigLoader.init();
			if (PhantomPlayersConfig.isEnabled()) { throw new IllegalStateException("AUTONOMOUS_MASTER_MUST_BE_OFF"); }
			DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString());
			PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure();
			org.l2jmobius.gameserver.data.xml.EnchantItemHPBonusData.getInstance();
			org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
			Files.writeString(phantomConfig, PhantomTrainingNative.phaseConfiguration(off, config.text("learningMode", "SHADOW"), config.longValue("modelVersion", 0, 0, Long.MAX_VALUE), config.seed(), true));
			PhantomPlayersConfig.load();
			if (org.l2jmobius.gameserver.phantoms.PhantomSystem.hasConfiguredInstance()) { throw new IllegalStateException("AUTONOMOUS_GLOBAL_OWNER_MUST_REMAIN_ABSENT"); }
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
				if (config.integer("autonomous013Version", 0, 0, 1) == 1)
				{
					final Path planned = cohort(root, config); final var tactical = new TacticalStoreV3(root);
					final var model = tactical.candidate(config.longValue("autonomous009ModelVersion", 0, 1, Long.MAX_VALUE));
					if (model == null || !model.sha().equals(config.text("autonomous009ModelSha", ""))) { throw new IllegalStateException("TRAIN013_PREPARE_MODEL_CHANGED"); }
					final var stock = new LearningStore(settings.getProperty("URL"), settings.getProperty("Login"), settings.getProperty("Password"), root.resolve("game")).accepted(1);
					final var sources = trainingSources013(root, discoverSources(root, planned, config, authority, () -> requested(planned)), config);
					final var cells = planCells(planned, sources, config, authority, () -> requested(planned),
						(cell, ordinal) -> admitHeadlessCell(root, planned, config, authority, tactical, model, stock.model(), cell, ordinal));
					final int minimum = config.integer("episodesPerBatch", 48, 48, 256) >= 51 ? 3 : 4;
					final long classes = cells.stream().map(c -> c.proposal().actor().classId()).distinct().count();
					final long scopes = cells.stream().map(Cell::party).distinct().count();
					immutable(planned.resolve("training-admission013.json"), "{\"schema\":\"TRAIN_ADMISSION_V13\",\"configSha\":" + q(config.sha256()) + ",\"cellsSha\":" + q(hash(Files.readString(planned.resolve("cells.tsv"))))
						+ ",\"cells\":" + cells.size() + ",\"classes\":" + classes + ",\"scopes\":" + scopes + ",\"minimumCells\":" + minimum + ",\"historicalEvaluationExcluded\":true,\"trainingCredit\":false,\"gameServerStarted\":false}");
					if (cells.size() < minimum || classes < 2 || scopes < 2) { throw new IllegalStateException("TRAIN013_INSUFFICIENT_LEGAL_COHORT"); }
				}
			}
			final String indexBody = String.join("\n", sourceIndex) + "\n";
			Files.writeString(session.resolve("source-index.tsv"), indexBody, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			Files.writeString(session.resolve("report.json"), "{\"schema\":\"AUTONOMOUS_NATIVE_ACQUIRED_INGRESS_V1\",\"sourceIndexSha\":" + q(hash(indexBody))
				+ ",\"sourceJarSha\":" + q(NeuralPolicy.sha256(Files.readAllBytes(root.resolve("game/libs/GameServer.jar"))))
				+ ",\"gameServerStarted\":false,\"trainingCredit\":false,\"records\":[" + String.join(",", records) + "]}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			System.out.println("AUTONOMOUS_SOURCE_PREPARED accepted=" + accepted.values().stream().mapToInt(Integer::intValue).sum() + " attempted=" + records.size() + " session=" + session.getFileName() + " gameServerStarted=false trainingCredit=false");
		}
		finally { try { Files.writeString(phantomConfig, off); PhantomPlayersConfig.load(); } finally { ThreadPool.shutdown(); DatabaseFactory.close(); } }
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
	private static org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest soloRequestProbe(boolean party, long profile, int target, PhantomCombatMode mode, long timeout)
	{
		if (party) { return null; }
		return PhantomLearning001ShadowNative.acquiredSoloRequest(profile, target, mode, timeout);
	}
	private static void sourceContract() throws Exception
	{
		if (!safeReason(new IllegalStateException("LEARNING001_AUTONOMOUS_ENCOUNTER_RESERVATION_EXPIRED")).equals("AUTONOMOUS_ENCOUNTER_RESERVATION_EXPIRED")
			|| !safeReason(new IllegalStateException("password=private JDBC vendor text")).equals("IllegalStateException"))
		{ throw new AssertionError("Native admission reason was lost or arbitrary vendor text was exposed"); }
		final Path artifactTest = Files.createTempDirectory("encounter011-catalog-");
		try
		{
			final Path headless = Files.createDirectory(artifactTest.resolve("headless")), live = Files.createDirectory(artifactTest.resolve("live"));
			final String cell = "a".repeat(64); final var fields = new ArrayList<>(java.util.Collections.nCopies(16, "fixture"));
			fields.set(0, cell); fields.set(1, "b".repeat(64) + "," + "c".repeat(64));
			final String body = "header\n" + String.join("\t", fields) + "\n";
			Files.writeString(headless.resolve("cells.tsv"), body); Files.writeString(headless.resolve(cell + ".physical.sha"), "d".repeat(64));
			boolean missingRiskRejected = false;
			try { catalogArtifactBindings(headless, body); } catch (java.io.IOException expected) { missingRiskRejected = true; }
			if (!missingRiskRejected) { throw new AssertionError("Prepared Party catalog accepted a missing native risk pin"); }
			Files.writeString(headless.resolve(cell + ".risk.tsv"), "role\tsourcePhysicalSha\tuncovered\tcovered\n0\tactual-source\t27\t37\n");
			final var bindings = catalogArtifactBindings(headless, body); copyPreparedProposals(headless, live, body);
			if (!Files.readString(headless.resolve(cell + ".risk.tsv")).equals(Files.readString(live.resolve(cell + ".risk.tsv")))
				|| Files.exists(live.resolve(cell + ".physical.sha"))) { throw new AssertionError("Different headless/live directories lost risk provenance or sealed physical facts before live admission"); }
			Files.writeString(headless.resolve(cell + ".risk.tsv"), "changed profile-derived risk");
			if (bindings.equals(catalogArtifactBindings(headless, body))) { throw new AssertionError("Changed risk pin retained prepared identity"); }
			boolean driftRejected = false;
			try { requirePhysicalParity(hash(PhantomLearning005Evaluation.mapJson(Map.of("hp", "100"))), Map.of("hp", "101")); }
			catch (IllegalStateException expected) { driftRejected = true; }
			if (!driftRejected) { throw new AssertionError("Final pre-CUT HP drift retained the physical seal"); }
		}
		finally
		{ try (var paths = Files.walk(artifactTest)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); } } }
		if (PhantomLearning001ShadowNative.acquiredSoloRequest(1, 1, PhantomCombatMode.RANGED_MAGIC, 500) != null) { throw new AssertionError("Sub-second native request was dispatched"); }
		if (PhantomLearning001ShadowNative.acquiredSoloRequest(1, 1, PhantomCombatMode.RANGED_MAGIC, 1000).timeoutMillis() != 1000) { throw new AssertionError("Native lower request boundary changed"); }
		if (soloRequestProbe(true, 1, 1, PhantomCombatMode.RANGED_MAGIC, 180000) != null) { throw new AssertionError("Party inherited the SOLO observation-window request"); }
		if (soloRequestProbe(false, 1, 1, PhantomCombatMode.RANGED_MAGIC, 120000).timeoutMillis() != 120000) { throw new AssertionError("Canonical120s SOLO boundary changed"); }
		try { soloRequestProbe(false, 1, 1, PhantomCombatMode.RANGED_MAGIC, 180000); throw new AssertionError("Native SOLO maximum relaxed"); } catch (IllegalArgumentException expected) { }
		final String offPhase = "EnablePhantomSystem = False\nLearningMode = OFF\nLearningModelVersion = 0\nLearningEvidenceEnabled = False\nLearningDeterministicSeed = 0\n";
		final String bootPhase = PhantomTrainingNative.phaseConfiguration(offPhase, "SHADOW", 2, 183, false);
		final String activePhase = PhantomTrainingNative.phaseConfiguration(offPhase, "SHADOW", 2, 183, true);
		if (!bootPhase.contains("EnablePhantomSystem = False") || !activePhase.contains("EnablePhantomSystem = True") || !activePhase.contains("LearningDeterministicSeed = 183")
			|| !activePhase.equals(PhantomTrainingNative.phaseConfiguration(bootPhase, "SHADOW", 2, 183, true))) { throw new AssertionError("Headless/native phase configuration diverged"); }
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
		final var nativeSpawn = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(100, 0, 0, 0);
		if (!PhantomTrainingScenarios.sourceTargetPoints(point, nativeSpawn).equals(List.of(point, nativeSpawn))
			|| !PhantomTrainingScenarios.sourceTargetPoints(point, null).equals(List.of(point))
			|| !PhantomTrainingScenarios.sourceTargetPoints(point, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(100, 0, 0, 1)).equals(List.of(point)))
		{ throw new AssertionError("Source-led placement fabricated offsets, lost native spawn provenance or crossed instances"); }
		final var outsideTopology = new PhantomTrainingScenarios.ClanNeighbor(400, 0, World.WORLD_Z_MAX + 1, 0, Set.of(1));
		if (PhantomTrainingScenarios.factionClear(Set.of(1), 300, 20, point, List.of(outsideTopology))
			|| PhantomTrainingScenarios.inWorldBounds(0, 0, World.WORLD_Z_MAX + 1)
			|| !PhantomTrainingScenarios.inWorldBounds(World.WORLD_X_MIN, World.WORLD_Y_MAX, World.WORLD_Z_MIN))
		{ throw new AssertionError("Unrelated raw World NPC coordinates crashed or weakened the topology admission boundary"); }
		final var driftClan = new PhantomTrainingScenarios.ClanNeighbor(new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(1000, 0, 0, 0), Set.of(1));
		if (!PhantomTrainingScenarios.factionClear(Set.of(1), 300, 20, point, List.of(driftClan)) || PhantomTrainingScenarios.planningFactionClear(Set.of(1), 300, 20, point, List.of(driftClan), 300))
		{ throw new AssertionError("Planning ignored canonical NPC drift or changed the live faction guard"); }
		final var homeClan = new PhantomTrainingScenarios.ClanNeighbor(950, 0, 0, 0, Set.of(1));
		final var currentClan = new PhantomTrainingScenarios.ClanNeighbor(700, 0, 0, 0, Set.of(1));
		final var homeEnvelope = PhantomTrainingScenarios.planningNeighbor(currentClan, homeClan, false, 300);
		if (!homeEnvelope.spawnCentered() || !PhantomTrainingScenarios.planningFactionClear(Set.of(1), 300, 20, point, List.of(homeEnvelope), 300)
			|| PhantomTrainingScenarios.planningNeighbor(currentClan, homeClan, true, 300).spawnCentered()
			|| PhantomTrainingScenarios.planningNeighbor(currentClan, new PhantomTrainingScenarios.ClanNeighbor(1100, 0, 0, 0, Set.of(1)), false, 300).spawnCentered()
			|| PhantomTrainingScenarios.planningFactionClear(Set.of(1), 300, 20, point, List.of(new PhantomTrainingScenarios.ClanNeighbor(900, 0, 0, 0, Set.of(1), true)), 300))
		{ throw new AssertionError("Planning lost the complete canonical spawn drift envelope or accepted an unbounded walker"); }
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
		final var budget = new HeadlessBudget(1);
		if (!budget.claim("first") || budget.claim("first") || budget.attempts != 1) { throw new AssertionError("Repeated headless candidate consumed a second native probe"); }
		try { budget.claim("second"); throw new AssertionError("Headless candidate cap was bypassed"); }
		catch (IllegalStateException expected) { if (!expected.getMessage().equals("AUTONOMOUS_HEADLESS_CANDIDATE_BUDGET")) { throw expected; } }
		if (!retryableHeadlessRefusal("AUTONOMOUS_PRE_CUT_PARTY_DAMAGE_REFUSED") || retryableHeadlessRefusal("AUTONOMOUS_PRE_CUT_PARTY_TARGET_REFUSED") || retryableHeadlessRefusal("AUTONOMOUS_SEALED_SOURCE_CHANGED")
			|| retryableHeadlessRefusal("AUTONOMOUS_PREPARED_PHYSICAL_FACTS_CHANGED") || retryableHeadlessRefusal("AUTONOMOUS_PREPARATION_REQUESTED_DRAIN"))
		{ throw new AssertionError("A safety/parity/STOP error was retried as ordinary source lawfulness"); }
		final String damageWait = "WAIT_GROUP_DAMAGE:target=1:durationMillis=Infinity";
		if (!retryableHeadlessRefusal(partyAdmissionFailure(Map.of("admitted", false, "wait", damageWait, "reasons", "[" + damageWait + "]")))
			|| retryableHeadlessRefusal(partyAdmissionFailure(Map.of("admitted", false, "wait", "WAIT_NATIVE_OWNER", "reasons", "[]")))
			|| retryableHeadlessRefusal(partyAdmissionFailure(Map.of("admitted", false, "wait", "WAIT_GROUP_SOURCE", "reasons", "[]")))
			|| retryableHeadlessRefusal(partyAdmissionFailure(Map.of("admitted", false, "wait", damageWait, "reasons", "[" + damageWait + ", source_changed]"))))
		{ throw new AssertionError("Party owner/source/mixed diagnostics were misclassified as a pure damage-envelope refusal"); }
		final var orderDirections = List.of("SOLO_DAMAGE", "PARTY_DAMAGE", "SOLO_DAMAGE", "PARTY_DAMAGE");
		if (!evaluationOrder(orderDirections, 0).equals(List.of("STOCK", "LEARNED")) || !evaluationOrder(orderDirections, 1).equals(List.of("STOCK", "LEARNED"))
			|| !evaluationOrder(orderDirections, 2).equals(List.of("LEARNED", "STOCK")) || !evaluationOrder(orderDirections, 3).equals(List.of("LEARNED", "STOCK")))
		{ throw new AssertionError("AB/BA was balanced globally but remained confounded with Solo/Party direction"); }
		final var cacheRoot = Path.of("fixture-root");
		if (preparedDirectory(cacheRoot, "a".repeat(64), "b".repeat(64)).equals(preparedDirectory(cacheRoot, "a".repeat(64), "c".repeat(64))))
		{ throw new AssertionError("A new pinned JAR collided with an immutable earlier headless catalog"); }
		System.out.println("AUTONOMOUS_SOURCE_CONTRACT_GREEN");
	}
	static List<String> pairedArtifactRegressions() throws Exception
	{
		final var failures = new ArrayList<String>(); final Path root = Files.createTempDirectory("eval012-raw-pair-");
		try
		{
			final Path headless = Files.createDirectory(root.resolve("headless")), live = Files.createDirectory(root.resolve("live"));
			final String cell = "a".repeat(64), configSha = "c".repeat(64), proposalSha = "b".repeat(64);
			final var facts = new java.util.TreeMap<String, Object>(); facts.put("hp", "100"); final String physical = hash(PhantomLearning005Evaluation.mapJson(facts));
			final var feature = new PhantomEvaluationCurriculumV2.Facts(cell, 0, 1000, 100, 100, 200, 300, 500, 400, 2, 20, configSha).json("PENDING_PAIRED_NATIVE_ADMISSION");
			final var fields = new ArrayList<>(java.util.Collections.nCopies(16, "fixture")); fields.set(0, cell); fields.set(1, configSha);
			final String body = "header\n" + String.join("\t", fields) + "\n";
			Files.writeString(headless.resolve("cells.tsv"), body); Files.writeString(headless.resolve(cell + ".physical.sha"), physical); Files.writeString(headless.resolve("curriculum-v2.json"), "{}");
			final var proofArms = new ArrayList<Map<String, Object>>(); String learnedWork = "";
			for (int sideIndex = 0; sideIndex < 2; sideIndex++)
			{
				final String side = sideIndex == 0 ? "STOCK" : "LEARNED", workId = hash(hash("ADMISSION/" + cell) + "/" + side); if (sideIndex == 1) { learnedWork = workId; }
				final Path work = Files.createDirectory(headless.resolve(workId)); final int actor = 1 + sideIndex * 2, target = actor + 1;
				Files.writeString(work.resolve("admission-only.json"), "{\"admitted\":true,\"afterCut\":false,\"gameServerStarted\":false,\"nativeActionsObserved\":0,\"trainingCredit\":false}");
				Files.writeString(work.resolve("native-admission.json"), "{\"admitted\":true,\"scope\":\"SOLO\",\"target\":" + target + ",\"requestTimeoutMillis\":120000,\"sessions\":[{profile=1, generation=1, target=" + target + "}]}");
				Files.writeString(work.resolve("encounter-admission.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", EncounterAdmissionV1.VERSION, "stage", "HEADLESS_READY", "proposalSha", proposalSha, "physicalSha", physical, "sealable", false, "afterCut", false, "trainingCredit", false)));
				Files.writeString(work.resolve("precut.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("comparisonCellSha", physical, "facts", facts, "actorProfile", 1)));
				Files.writeString(work.resolve("incarnations.tsv"), "role\tobjectId\tepoch\tinstance\nACTOR\t" + actor + "\t1\t0\nTARGET\t" + target + "\t1\t0\n");
				Files.writeString(work.resolve("curriculum-native-facts-v2.json"), PhantomEvaluationOutcomeV2.json(feature));
				final var raw = new java.util.TreeMap<String, String>();
				for (String name : List.of("admission-only.json", "native-admission.json", "encounter-admission.json", "precut.json", "incarnations.tsv", "curriculum-native-facts-v2.json")) { raw.put(name, NeuralPolicy.sha256(Files.readAllBytes(work.resolve(name)))); }
				proofArms.add(PhantomEvaluationOutcomeV2.fields("side", side, "work", workId, "rawFileShas", raw));
			}
			Files.writeString(headless.resolve(cell + ".paired-native-v2.json"), PhantomEvaluationOutcomeV2.json(PhantomEvaluationOutcomeV2.fields("schema", "EVAL_PRECUT_PAIR_V2", "cell", cell, "configSha", configSha,
				"proposalSha", proposalSha, "direction", "SOLO_DAMAGE", "fullNativeAdmission", true, "matchedPhysical", true, "freshIncarnations", true, "physicalSha", physical, "curriculumFacts", feature, "afterCut", false, "trainingCredit", false, "arms", proofArms)));
			catalogArtifactBindings(headless, body); copyPreparedProposals(headless, live, body);
			if (!Files.isRegularFile(live.resolve(learnedWork).resolve("native-admission.json"))) { failures.add("RAW_PAIR_PROOFS_LOST_ON_LIVE_COPY"); }
			final Path raw = headless.resolve(learnedWork).resolve("native-admission.json"); final byte[] before = Files.readAllBytes(raw); Files.writeString(raw, "{\"admitted\":false}");
			try { catalogArtifactBindings(headless, body); failures.add("MUTATED_RAW_PROOF_ACCEPTED"); } catch (Exception expected) { }
			Files.write(raw, before); Files.delete(raw);
			try { catalogArtifactBindings(headless, body); failures.add("MISSING_RAW_PROOF_ACCEPTED"); } catch (Exception expected) { }
		}
		finally { try (var paths = Files.walk(root)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); } } }
		return List.copyOf(failures);
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

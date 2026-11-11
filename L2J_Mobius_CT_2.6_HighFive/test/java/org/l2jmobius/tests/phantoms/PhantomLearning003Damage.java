/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.learning.Learning003DamageImport;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TrainingCollector;
import org.l2jmobius.gameserver.phantoms.learning.TrainingConfig;
import org.l2jmobius.gameserver.phantoms.learning.TrainingStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** One owned regression batch after HEAL: historical V1 weights, fresh native actors, no refit. */
public final class PhantomLearning003Damage
{
	private static final String ANCHOR = "generated.farm.72007cc387f17e963e4fcf2c.anchor";
	private static final int X = 180488, Y = -75315, Z = -2728;
	private static final List<Integer> SKILLS = List.of(1230, 1339);

	public static void run(Path root, Path attempt, TrainingConfig config) throws Exception
	{
		PhantomTrainingActors.physicalRoot(root);
		PhantomTrainingActors.settings(root);
		if (!attempt.toRealPath().equals(attempt) || !attempt.getParent().equals(root.resolve("attempts"))
			|| Files.exists(attempt.resolve("damage002.complete")) || Files.exists(attempt.resolve("damage002.failed")))
		{ throw new IllegalStateException("Fresh own DAMAGE regression attempt required"); }
		LearningPolicy policy = null;
		boolean drained = false;
		try
		{
			// Owned layout is clone/.phantom-local/learning002/train; never search old runtime installations.
			final Path clone = root.getParent().getParent().getParent();
			final Path archive = clone.resolve("L2J_Mobius_CT_2.6_HighFive/docs/phantoms/tasks/PHANTOM-LEARNING-002/artifacts/accepted-model-1/model-1.zip");
			if (!archive.normalize().startsWith(clone) || !Files.isRegularFile(archive)) { throw new IllegalStateException("Tracked accepted002 archive not found in own clone"); }
			final var imported = Learning003DamageImport.load(root, archive, config);
			policy = imported.policy();
			Files.writeString(attempt.resolve("damage002-import-manifest.json"), imported.manifest(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			if (!policy.collecting() || !policy.active(LearningScope.SOLO) || !policy.active(LearningScope.PARTY)) { throw new IllegalStateException("Historical ON policy unavailable"); }
			System.out.println("DAMAGE002_COLD_MODEL version=1 algorithm=2 bytes=12500 sha=" + imported.checkpoint().model().sha256() + " importedHistorical=true retrained=false");
			final float[] mathWarmup = new float[128];
			for (int i = 0; i < 2048; i++) { imported.checkpoint().model().score(mathWarmup); }
			System.out.println("DAMAGE002_WARMUP mathOnly=true iterations=2048 explorationSeed=0");
			final var collector = new TrainingCollector();
			policy.observer(collector);
			try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				final int npc = sourcePreflight(source);
				final var solo = scene(npc, 77, "A", 4);
				final var grouped = scene(npc, 78, "S", 1);
				final var rows = prepare(source, solo, grouped, attempt, config);
				try (var runtime = new PhantomLearning001ShadowNative.Runtime(source, root.resolve("game"), policy, attempt.resolve("stop.request"), true, 5))
				{
					proof(runtime, solo, rows, false, root, attempt, config, collector, policy);
					proof(runtime, grouped, rows, true, root, attempt, config, collector, policy);
				}
			}
			Learning003DamageImport.verifyUnchanged(root, config);
			drain(policy); drained = true;
			Files.writeString(attempt.resolve("damage002.complete"), "modelVersion=1\nmodelSha=" + Learning003DamageImport.MODEL_SHA
				+ "\nzipSha=" + Learning003DamageImport.ZIP_SHA + "\nscopes=SOLO,PARTY\nretrained=false\n", StandardOpenOption.CREATE_NEW);
			System.out.println("DAMAGE002_REGRESSION_PASS scopes=SOLO,PARTY modelSha=" + Learning003DamageImport.MODEL_SHA + " counters=" + policy.counters());
		}
		catch (Exception failure)
		{
			Files.writeString(attempt.resolve("damage002.failed"), failure.getClass().getSimpleName() + ": " + failure.getMessage() + "\n", StandardOpenOption.CREATE_NEW);
			throw failure;
		}
		finally
		{
			if (policy != null && !drained) { drain(policy); }
		}
	}

	static int sourcePreflight(PhantomBackgroundSuite.ProductionAuthorityFixture source)
	{
		final var anchor = source.topology().findAnchor(ANCHOR).orElseThrow(() -> new IllegalStateException("Canonical DAMAGE anchor missing"));
		final var point = anchor.point();
		final var geo = GeoEngine.getInstance();
		if (point.x() != X || point.y() != Y || point.z() != Z || point.instanceId() != 0 || !geo.hasGeo(X, Y) || geo.getHeight(X, Y, Z) != Z)
		{ throw new IllegalStateException("Canonical DAMAGE anchor/Geo changed"); }
		final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
		return World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
			.filter(npc -> Set.of(21798, 21799).contains(npc.getId()) && npc.isSpawned() && !npc.isAlikeDead() && npc.getInstanceId() == 0
				&& !npc.isAggressive() && !npc.isRaid() && !npc.isChampion() && npc.isTargetable() && !npc.isInvul()
				&& Math.hypot((long) npc.getX() - X, (long) npc.getY() - Y) < 1600
				&& planner.partyNativeTarget(new PhantomDomainRef("background.farm", npc.getId() + "@" + ANCHOR), npc.getId(), new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), 0))
				&& geo.canSeeTarget(X, Y, Z, 0, npc.getX(), npc.getY(), npc.getZ(), 0)
				&& geo.canMoveToTarget(X, Y, Z, npc.getX(), npc.getY(), npc.getZ(), 0))
			.sorted(Comparator.comparingInt(Monster::getId).thenComparingInt(Monster::getObjectId)).mapToInt(Monster::getId).findFirst()
			.orElseThrow(() -> new IllegalStateException("No actual safe21798/21799 at canonical DAMAGE source"));
	}

	static PhantomTrainingScenarios.Scene scene(int npc, int level, String grade, int enchant)
	{
		final String source = npc + "@" + ANCHOR;
		return new PhantomTrainingScenarios.Scene(PhantomTrainingScenarios.physicalId(source, 94, level, grade, X, Y, Z, SKILLS, 3, enchant),
			source, npc, 94, level, grade, enchant, X, Y, Z, SKILLS, "REGRESSION");
	}

	static List<String[]> prepare(PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomTrainingScenarios.Scene solo,
		PhantomTrainingScenarios.Scene grouped, Path attempt, TrainingConfig config) throws Exception
	{
		final var profiles = PhantomProfileRepository.open();
		int next;
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.createStatement(); var rows = sql.executeQuery(
			"SELECT COALESCE(MAX(profile_id),0)+1,(SELECT COUNT(*) FROM characters WHERE account_name IS NULL OR account_name<>'learn001fixture'),(SELECT COUNT(*) FROM characters) FROM phantom_profiles"))
		{
			if (!rows.next() || rows.getLong(2) != 0 || rows.getLong(3) + 5 > config.integer("maxStoredActors", 4096, 32, 32768)
				|| rows.getLong(1) + 4 > config.integer("maxStoredProfiles", 4096, 64, 32768))
			{ throw new IllegalStateException("Owned DAMAGE actor capacity/identity refused"); }
			next = Math.toIntExact(rows.getLong(1));
		}
		final var resolver = PhantomCombatCapabilityResolver.fromProgression(source::progression);
		final var rows = new ArrayList<String[]>();
		final Path manifest = attempt.resolve("damage002-actors.tsv");
		Files.writeString(manifest, "split\trole\tprofile\tactor\tclass\tlevel\tweapon\tskills\tx\ty\tz\n", StandardOpenOption.CREATE_NEW);
		final String[] roles = {"SOLO", "PARTY", "HELPER", "DAMAGE_HELPER", "HEAL_HELPER"};
		final int[] classes = {94, 94, 90, 94, 105};
		for (int i = 0; i < roles.length; i++)
		{
			final String split = i < 2 ? "REGRESSION" : "FIXTURE";
			final String name = "D3" + Long.toUnsignedString(System.nanoTime(), 36) + i;
			final var nativeScene = i == 0 ? solo : grouped;
			final String row = PhantomLearning001ActorFixture.createTraining(profiles, resolver, next++, split, roles[i], new Location(nativeScene.x(), nativeScene.y(), nativeScene.z()),
				i == 0 ? solo.level() : i == 1 ? grouped.level() : 76,
				i == 0 ? CrystalType.valueOf(solo.grade()) : i == 1 ? CrystalType.valueOf(grouped.grade()) : CrystalType.S,
				i < 2 ? nativeScene.classId() : classes[i], Set.copyOf(i < 2 ? nativeScene.skills() : SKILLS), i == 0 ? solo.weaponEnchant() : i == 1 ? grouped.weaponEnchant() : -1, name);
			Files.writeString(manifest, row + "\n", StandardOpenOption.APPEND);
			final var fields = row.split("\t", -1); rows.add(fields);
			plan(source, fields, i == 0 ? solo : grouped, config.seed());
		}
		System.out.println("DAMAGE002_PREPARED freshActors=5 source=" + solo.source() + " manifest=" + manifest.getFileName());
		return List.copyOf(rows);
	}

	/** Exact adjacent PhantomTrainingActors.plan pattern, with no private API/reflection. */
	private static void plan(PhantomBackgroundSuite.ProductionAuthorityFixture source, String[] row, PhantomTrainingScenarios.Scene scene, long seed)
	{
		final long profile = Long.parseLong(row[2]);
		final var player = Player.load(Integer.parseInt(row[3]));
		if (player == null) { throw new IllegalStateException("Fresh DAMAGE actor failed native reload"); }
		try
		{
			final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
			final var initial = planner.planInitial(profile, player, seed, 1);
			final var baseline = (initial.ready() ? initial : planner.idleInitial(profile, player, seed, 1)).goal();
			if (baseline == null) { throw new IllegalStateException("No ordinary DAMAGE farm root"); }
			final var state = source.authority().capture(profile, player, baseline, null);
			final var result = planner.replanLogicalRendezvous(profile, state, baseline, seed, 2, new PhantomDomainRef("background.farm", scene.source()));
			if (result.goal() == null) { throw new IllegalStateException("DAMAGE source planning refused: " + result.reasonKey()); }
			final var goals = new PhantomGoalStateStore(PhantomProfileRepository.open());
			goals.insert(profile, result.goal());
			if (!goals.load(profile).orElseThrow().goal().equals(result.goal())) { throw new IllegalStateException("DAMAGE ordinary goal readback changed"); }
		}
		finally { player.stopAllTasks(); player.deleteMe(); }
	}

	private static void proof(PhantomLearning001ShadowNative.Runtime runtime, PhantomTrainingScenarios.Scene scene, List<String[]> rows, boolean grouped,
		Path root, Path attempt, TrainingConfig config, TrainingCollector collector, LearningPolicy policy) throws Exception
	{
		final String scope = grouped ? "PARTY" : "SOLO";
		String before = "UNKNOWN", after = "UNKNOWN", outcome = "PREPARATION_FAILED";
		Exception failure = null;
		try
		{
			before = runtime.prepareTraining(scene, rows, grouped, config.preparationSeconds(), () -> requested(attempt));
			collector.begin("damage003-" + scope);
			System.out.println("DAMAGE002_CUT scope=" + scope + " source=" + scene.source() + " before=" + before + " modelSha=" + Learning003DamageImport.MODEL_SHA);
			// Party preflight disbands its proof group; stock five-second pulses must reassemble before the first cast.
			final var cooldown = grouped ? null : runtime.cooldownWitness();
			outcome = runtime.runTrainingEpisode(collector, config.episodeSeconds(), 1, () -> requested(attempt), cooldown);
			if (cooldown != null) { Files.writeString(attempt.resolve("damage002-cooldown.txt"), cooldown.description() + "\n", StandardOpenOption.CREATE_NEW); }
		}
		catch (Exception problem) { failure = problem; }
		try { after = runtime.settleTraining(); }
		catch (Exception cleanup) { if (failure == null) { failure = cleanup; } else { failure.addSuppressed(cleanup); } }
		final var observations = collector.end();
		final Path evidence = attempt.resolve("damage002-" + scope.toLowerCase(java.util.Locale.ROOT) + ".jsonl");
		Files.write(evidence, observations.stream().map(TrainingCollector.Observation::json).toList(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
		Files.writeString(attempt.resolve("damage002-" + scope.toLowerCase(java.util.Locale.ROOT) + "-state.txt"),
			"source=" + scene.source() + "\noutcome=" + outcome + "\nbefore=" + before + "\nafter=" + after + "\n", StandardOpenOption.CREATE_NEW);
		if (failure != null) { throw failure; }
		if (!"NATIVE_EVIDENCE".equals(outcome) || observations.isEmpty()) { throw new IllegalStateException("DAMAGE " + scope + " lacked exact receipt: " + outcome); }
		readback(scope, rows, observations);
		Learning003DamageImport.verifyUnchanged(root, config);
		System.out.println("DAMAGE002_SCOPE_PASS scope=" + scope + " nativeReceipts=" + observations.size() + " exactReordered="
			+ policy.exactReordered(grouped ? LearningScope.PARTY : LearningScope.SOLO) + " modelSha=" + Learning003DamageImport.MODEL_SHA);
	}

	private static void readback(String scope, List<String[]> actors, List<TrainingCollector.Observation> observations) throws Exception
	{
		final Set<Long> profiles = actors.stream().map(row -> Long.parseLong(row[2])).collect(java.util.stream.Collectors.toSet());
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.prepareStatement("SELECT scope,policy_version,skill_id,skill_level,damage,features,actor_group FROM phantom_ai_training_examples WHERE event_key=? AND origin='NATIVE' AND evidence_kind='EXACT_CAST_HP' AND schema_fingerprint=? AND skill_catalog=?"))
		{
			for (var observation : observations)
			{
				TrainingStore.validateObservation(observation.json());
				if (!profiles.contains(number(observation.json(), "profileId"))) { throw new IllegalStateException("Foreign DAMAGE receipt"); }
				final long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
				while (true)
				{
					sql.setString(1, observation.eventKey()); sql.setString(2, Learning003DamageImport.FEATURE_SCHEMA); sql.setString(3, Learning003DamageImport.CATALOG);
					try (var rows = sql.executeQuery())
					{
						if (rows.next())
						{
							if (!scope.equals(rows.getString(1)) || rows.getLong(2) != 1 || !Set.of(1230, 1339).contains(rows.getInt(3))
								|| rows.getInt(3) != number(observation.json(), "skill") || rows.getInt(4) != number(observation.json(), "level")
								|| !Double.isFinite(rows.getDouble(5)) || rows.getDouble(5) <= 0) { throw new IllegalStateException("DAMAGE exact SQL receipt mismatch"); }
							System.out.println("DAMAGE002_NATIVE_RECEIPT scope=" + scope + " eventKey=" + observation.eventKey() + " model=1 modelSha=" + Learning003DamageImport.MODEL_SHA
								+ " baseline=" + number(observation.json(), "baselineSkill") + " actual=" + rows.getInt(3) + ":" + rows.getInt(4) + " exactHp=" + rows.getDouble(5)
								+ " featuresSha=" + NeuralPolicy.sha256(rows.getBytes(6)) + " actorGroup=" + rows.getString(7));
							break;
						}
					}
					if (System.nanoTime() >= deadline) { throw new IllegalStateException("DAMAGE exact SQL writer readback timed out"); }
					Thread.sleep(200);
				}
			}
		}
	}

	private static long number(String json, String key)
	{
		final var matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+)").matcher(json);
		if (!matcher.find()) { throw new IllegalStateException("Native DAMAGE field missing: " + key); }
		return Long.parseLong(matcher.group(1));
	}

	private static boolean requested(Path attempt) { return Files.exists(attempt.resolve("stop.request")) || Files.exists(attempt.resolve("pause.request")); }
	private static void drain(LearningPolicy policy) throws Exception
	{
		final long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
		while (!policy.stop())
		{
			if (System.nanoTime() >= deadline) { throw new IllegalStateException("Historical DAMAGE evidence writer did not drain"); }
			Thread.sleep(100);
		}
	}
	private PhantomLearning003Damage() { }
}

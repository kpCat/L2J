/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.ConfigLoader;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.learning.*;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** TEST-only scene inputs before CUT; all measured effects use the real Party/Combat/Skill owners. */
public final class PhantomLearning003Native
{
	@FunctionalInterface
	interface CoreContinuation
	{
		void run(PhantomBackgroundSuite.ProductionAuthorityFixture authority, PhantomLearning001ShadowNative.Runtime runtime, TacticalPolicyV3 shared) throws Exception;
	}
	private static final String HEADER = "context\tsplit\tvariant\tactorRow";
	private static String phase(TrainingConfig config)
	{
		return "ON".equals(config.text("v3Mode", "SHADOW")) ? "on-v" + config.longValue("v3ModelVersion", 0, 0, Long.MAX_VALUE)
			+ (config.integer("v3ProofRound", 0, 0, 99) == 0 ? "" : "-r" + config.integer("v3ProofRound", 0, 0, 99))
			: "collect" + (config.integer("v3CollectionRound", 0, 0, 99) == 0 ? "" : "-r" + config.integer("v3CollectionRound", 0, 0, 99));
	}
	private static Path phaseMarker(Path root, TrainingConfig config) { return root.resolve("v3-" + phase(config) + ".complete"); }
	static boolean complete(int expected, int finished, boolean interrupted) { return !interrupted && expected > 0 && finished == expected; }
	public static PhantomGoal partyGoal(long profile, long id, String type, PhantomDomainRef target)
	{
		return new PhantomGoal(id, type, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profile)), target,
			1, 0, null, List.of(), null, "learning003.heal", 500, 0, 0, 0, Map.of(), "learning003.heal", 0);
	}
	static void prepareActors(Path root, TrainingConfig config) throws Exception
	{
		PhantomTrainingActors.settings(root);
		final String mode = config.text("v3Mode", "SHADOW");
		final Path manifest = root.resolve("v3-actors-" + phase(config) + ".tsv");
		if (Files.isRegularFile(manifest))
		{
			if (Files.exists(phaseMarker(root, config))) { throw new IllegalStateException("V3 phase already completed; retained actors cannot be reset"); }
			System.out.println("V3_ACTORS_REUSED manifest=" + manifest.getFileName()); return;
		}
		ConfigLoader.init();
		if (PhantomPlayersConfig.isEnabled()) { throw new IllegalStateException("V3 preparation requires master OFF"); }
		DatabaseFactory.initFromConfig(root.resolve("game/config/Database.ini").toString());
		PhantomHeadlessPlayerTestEnvironment.initializeOwnedPlayerInfrastructure();
		InitialEquipmentData.getInstance(); InitialShortcutData.getInstance();
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		final var profiles = PhantomProfileRepository.open();
		int next;
		try (var connection = DatabaseFactory.getConnection(); var sql = connection.createStatement())
		{
			try (var rows = sql.executeQuery("SELECT COALESCE(MAX(profile_id),0)+1,(SELECT COUNT(*) FROM characters WHERE account_name<>'learn001fixture') FROM phantom_profiles"))
			{ if (!rows.next() || rows.getInt(2) != 0) { throw new IllegalStateException("Own ordinary actors only"); } next = rows.getInt(1); }
			sql.executeUpdate("INSERT IGNORE INTO accounts(login,password,accessLevel,lastServer) VALUES ('learn001fixture',NULL,0,1)");
		}
		final int train = config.integer("v3TrainContexts", 16, 16, 256), tuning = config.integer("v3TuningContexts", 8, 8, 128), audit = config.integer("v3AuditContexts", 8, 8, 128);
		final int count = "ON".equals(mode) ? 4 : train + tuning + audit;
		if (next + count * 3 > config.integer("maxStoredProfiles", 4096, 64, 32768) || next + count * 3 > config.integer("maxStoredActors", 4096, 32, 32768))
		{ throw new IllegalStateException("V3 actor retention capacity reached"); }
		final var lines = new ArrayList<String>(); lines.add(HEADER);
		try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var resolver = PhantomCombatCapabilityResolver.fromProgression(authority::progression);
			for (int scene = 0; scene < count; scene++)
			{
				final String split = "ON".equals(mode) ? "PROOF" : scene < train ? "TRAIN" : scene < train + tuning ? "TUNING" : "AUDIT";
				final int variant = "ON".equals(mode) ? scene : scene < train ? scene : scene < train + tuning ? scene - train : scene - train - tuning;
				final int level = 76 + variant / 4 % 3, healerClass = variant / 2 % 2 == 0 ? 97 : 105, enchant = 4 + variant / 4 % 4;
				final int segmentOffset = "true".equals(config.text("v3Core004", "false")) ? Math.multiplyExact(config.integer("v3CollectionRound", 4, 4, 99) - 4, 30) : 0;
				final int offset = ("TRAIN".equals(split) ? 0 : "TUNING".equals(split) ? 10 : "AUDIT".equals(split) ? 20 : 30) + segmentOffset;
				final String context = "giran.v3r" + config.integer("v3CollectionRound", 0, 0, 99) + ".class" + healerClass + ".level" + level
					+ ".enchant" + enchant + ".hp" + (variant % 8) + ".d" + (45 + offset) + "-" + (90 + offset);
				final var rows = new ArrayList<String[]>();
				for (int actor = 0; actor < 3; actor++)
				{
					// The historical DAMAGE_HELPER fixes magicLevel=76; variable-level recipients use ordinary learned Archmage skills.
					final String role = actor == 0 ? "HEAL_HELPER" : actor == 1 ? "HELPER" : "SOLO";
					final int type = actor == 0 ? healerClass : actor == 1 ? 90 : 94;
					// Native Giran respawn point from data/mapregion/giran_castle_town.xml; no hostile scene.
					final var point = new Location(82480 + actor * 45 + (actor == 0 ? 0 : offset), 149087 + (variant % 4) * 20, -3350);
					final String name = "V3" + Long.toUnsignedString(System.nanoTime(), 36) + actor;
					final int actorLevel = actor == 2 ? Math.clamp(level, 77, 78) : level;
					final var row = PhantomLearning001ActorFixture.createTraining(profiles, resolver, next++, split, role, point, actorLevel,
						CrystalType.S, type, Set.of(1230, 1339), enchant, name).split("\t", -1);
					rows.add(row); lines.add(context + "\t" + split + "\t" + variant + "\t" + String.join("\t", row));
				}
				final var goals = new PhantomGoalStateStore(profiles);
				for (int actor = 0; actor < rows.size(); actor++)
				{
					final long profile = Long.parseLong(rows.get(actor)[2]);
					goals.insert(profile, partyGoal(profile, profile * 10, actor == 0 ? PhantomPartyCoordinator.FORM_GOAL : PhantomPartyCoordinator.JOIN_GOAL,
						actor == 0 ? null : new PhantomDomainRef("character.object", rows.getFirst()[3])));
				}
			}
		}
		Files.write(manifest, lines, StandardOpenOption.CREATE_NEW);
		System.out.println("V3_ACTORS_PREPARED contexts=" + (lines.size() - 1) / 3 + " manifest=" + manifest.getFileName());
	}
	static void run(Path root, Path attempt, TrainingConfig config, LearningPolicy legacy) throws Exception
	{ run(root, attempt, config, legacy, null); }
	static void run(Path root, Path attempt, TrainingConfig config, LearningPolicy legacy, CoreContinuation continuation) throws Exception
	{
		if (config.integer("modelAlgorithmV3", 3, 3, 3) != TacticalModelV3.ALGORITHM || !Set.of("TACTICAL_V3", TacticalObservationV3.SCHEMA).contains(config.text("featureSchemaV3", "TACTICAL_V3"))
			|| !"DAMAGE,HEAL".equals(config.text("learningActionFamilies", "DAMAGE,HEAL"))) { throw new IllegalArgumentException("V3 contract configuration mismatch"); }
		final var store = new TacticalStoreV3(root); store.initialize();
		final var mode = TacticalPolicyV3.Mode.valueOf(config.text("v3Mode", "SHADOW"));
		final long requestedVersion = config.longValue("v3ModelVersion", 0, 0, Long.MAX_VALUE);
		final var checkpoint = mode == TacticalPolicyV3.Mode.ON || requestedVersion > 0 ? store.accepted(requestedVersion, config.text("v3ModelSha", ""))
			: continuation != null && mode == TacticalPolicyV3.Mode.SHADOW ? store.latestAccepted() : null;
		final long version = checkpoint == null ? requestedVersion : checkpoint.id();
		if (mode == TacticalPolicyV3.Mode.ON && checkpoint == null) { throw new IllegalStateException("Cold V3 accepted TEST checkpoint missing"); }
		if (checkpoint != null) { System.out.println("V3_COLD_MODEL version=" + checkpoint.id() + " sha=" + checkpoint.sha() + " bytes=" + checkpoint.model().bytes().length); }
		if (checkpoint != null)
		{
			final float[] state = new float[128], action = new float[64];
			for (int warm = 0; warm < 2048; warm++) { checkpoint.model().scoreEncoded(checkpoint.model().encode(state), action); }
			System.out.println("V3_WARMUP mathOnly=true iterations=2048 beforeNativeOn=true");
		}
		final var receipts = new ConcurrentLinkedQueue<HealEvidenceV3.Receipt>();
		final var failures = new ConcurrentLinkedQueue<org.l2jmobius.gameserver.phantoms.learning.TacticalFailureV3>();
		final var policy = new TacticalPolicyV3(mode, version, checkpoint == null ? null : checkpoint.model(), config.integer("maxEntities", 20, 3, 20), config.integer("maxActions", 16, 2, 16),
			config.integer("decisionCooldownMillis", 2000, 1000, 60000), receipt -> { if (receipts.size() >= 64) { throw new IllegalStateException("V3 receipt queue bound"); } receipts.add(receipt); });
		policy.failureObserver(failure -> { if (failures.size() >= 1024) { throw new IllegalStateException("V3 failure queue bound"); } failures.add(failure); });
		final var manifest = root.resolve("v3-actors-" + phase(config) + ".tsv");
		final var lines = Files.readAllLines(manifest);
		if (lines.isEmpty() || !HEADER.equals(lines.getFirst()) || (lines.size() - 1) % 3 != 0) { throw new IllegalStateException("V3 actor manifest"); }
		final var preserved = mode == TacticalPolicyV3.Mode.SHADOW ? store.dataset() : List.<TacticalStoreV3.Row>of();
		int committed = 0, changed = 0, finished = 0; boolean interrupted = false;
		try (var authority = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
			var runtime = new PhantomLearning001ShadowNative.Runtime(authority, root.resolve("game"), legacy, attempt.resolve("stop.request"), false, 5, policy))
		{
			for (int index = 1; index < lines.size(); index += 3)
			{
				if (Files.exists(attempt.resolve("stop.request")) || Files.exists(attempt.resolve("pause.request"))) { interrupted = true; break; }
				final var metadata = lines.get(index).split("\t", 4); final String context = metadata[0], split = metadata[1]; final int variant = Integer.parseInt(metadata[2]);
				final var roster = new ArrayList<String[]>(); for (int j = 0; j < 3; j++) { roster.add(lines.get(index + j).split("\t", 4)[3].split("\t", -1)); }
				final var prior = preserved.stream().filter(row -> row.contextKey().equals(context) && row.actorProfile() == Long.parseLong(roster.getFirst()[2])).toList();
				if (!prior.isEmpty())
				{
					if (prior.stream().anyMatch(row -> row.actorProfile() != Long.parseLong(roster.getFirst()[2]))) { throw new IllegalStateException("Native context/actor reuse mismatch"); }
					System.out.println("V3_COMMITTED_CONTEXT_REUSED context=" + context + " events=" + prior.size() + " hpReset=false"); committed += prior.size(); finished++; continue;
				}
				final var cutDirectory = root.resolve("v3-cuts"); Files.createDirectories(cutDirectory);
				final var cutFence = cutDirectory.resolve(roster.getFirst()[2] + ".cut");
				if (Files.exists(cutFence)) { throw new IllegalStateException("Previous CUT without committed receipt requires fresh actors; HP reset forbidden profile=" + roster.getFirst()[2]); }
				final var players = runtime.prepareHeal(roster); final var healer = players.getFirst();
				final double lower = .30 + variant % 8 * .025, higher = .48 + variant % 3 * .015;
				Files.writeString(cutFence, "context=" + context + "\nprofile=" + roster.getFirst()[2] + "\nactor=" + healer.getObjectId() + "\nepoch=" + TacticalSnapshotV3.epoch(healer)
					+ "\ninputLower=" + lower + "\ninputHigher=" + higher + "\n", StandardOpenOption.CREATE_NEW);
				// The only injury assignments are declared TEST INPUT before CUT, never outcomes.
				players.get(1).setCurrentHp(players.get(1).getMaxHp() * (variant % 2 == 0 ? lower : higher));
				players.get(2).setCurrentHp(players.get(2).getMaxHp() * (variant % 2 == 0 ? higher : lower));
				final var offered = runtime.inspectHeal().stream().filter(d -> d.actor().profileId() == Long.parseLong(roster.getFirst()[2]) && d.kind() == DirectiveKind.HEAL_MEMBER).toList();
				if (offered.size() != 2 || players.stream().anyMatch(p -> p.isCastingNow() || p.isAlikeDead()) || players.stream().anyMatch(PhantomLearning003Native::threatened))
				{ throw new IllegalStateException("V3 requires exactly two admissible noncritical native heals: " + offered); }
				final var geo = GeoEngine.getInstance();
				if (players.stream().anyMatch(p -> !geo.hasGeo(p.getX(), p.getY()) || !geo.canSeeTarget(healer, p))) { throw new IllegalStateException("V3 native LOS preflight"); }
				final var observation = TacticalSnapshotV3.collect(healer, Long.parseLong(roster.getFirst()[2]), index, 20);
				Files.write(attempt.resolve("actor-" + roster.getFirst()[2] + ".precut.bin"), observation.bytes(), StandardOpenOption.CREATE_NEW);
				System.out.println("V3_CUT context=" + context + " split=" + split + " stock=" + offered.getFirst().targetObjectId() + " candidates=" + offered + " hp=" + players.stream().map(p -> p.getObjectId() + ":" + p.getCurrentHp() + "/" + p.getMaxHp()).toList() + " mp=" + healer.getCurrentMp());
				final long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
				while (receipts.isEmpty() && System.nanoTime() < deadline)
				{
					runtime.pulseHeal(); if (players.stream().anyMatch(PhantomLearning003Native::threatened)) { throw new IllegalStateException("Unexpected native threat"); } Thread.sleep(50);
				}
				runtime.finishHeal();
				org.l2jmobius.gameserver.phantoms.learning.TacticalFailureV3 failed;
				while ((failed = failures.poll()) != null)
				{
					if (failed.choice().observation().profileId() != Long.parseLong(roster.getFirst()[2])) { throw new IllegalStateException("V3 foreign failure"); }
					store.recordFailure(failed,split,context,null); System.out.println("LEARNING004_NATIVE_FAILURE " + failed.json());
				}
				if (receipts.isEmpty()) { throw new IllegalStateException("V3 no exact HEAL: latest=" + policy.latest() + " rejects=" + HealEvidenceV3.REJECTED.sum()); }
				HealEvidenceV3.Receipt receipt;
				while ((receipt = receipts.poll()) != null)
				{
					if (receipt.attempt().selected().profileId() != Long.parseLong(roster.getFirst()[2])) { throw new IllegalStateException("V3 foreign receipt"); }
					final boolean reorder = receipt.attempt().selected().targetObjectId() != receipt.attempt().baseline().targetObjectId();
					if (reorder) { changed++; }
					if (mode != TacticalPolicyV3.Mode.ON && !store.record(receipt, split, context)) { throw new IllegalStateException("V3 duplicate fresh receipt"); }
					System.out.println("V3_NATIVE_HEAL context=" + context + " split=" + split + " key=" + receipt.eventKey() + " baseline=" + receipt.attempt().baseline().targetObjectId()
						+ " selected=" + receipt.attempt().selected().targetObjectId() + " skill=" + receipt.attempt().selected().skillId() + ":" + receipt.attempt().selected().skillLevel()
						+ " beforeHp=" + receipt.beforeHp() + " afterHp=" + receipt.afterHp() + " usefulHp=" + receipt.actualHp() + " mpDebit=" + receipt.actualMp()
						+ " elapsedNanos=" + receipt.elapsedNanos() + " reward=" + receipt.reward() + " model=" + receipt.attempt().modelVersion() + " changed=" + reorder);
					committed++;
				}
				finished++;
			}
			if (continuation != null && complete((lines.size() - 1) / 3, finished, interrupted)) { continuation.run(authority, runtime, policy); }
		}
		if (!complete((lines.size() - 1) / 3, finished, interrupted))
		{
			Files.writeString(attempt.resolve("batch.complete"), "schemaVersion=3\npartial=true\nfinished=" + finished + "\ncommitted=" + committed + "\n", StandardOpenOption.CREATE_NEW);
			System.out.println("V3_NATIVE_BATCH_PARTIAL finished=" + finished + " terminalMarker=false"); return;
		}
		if (mode == TacticalPolicyV3.Mode.ON && changed == 0) { throw new IllegalStateException("Cold accepted model did not change lawful native HEAL choice"); }
		if (mode == TacticalPolicyV3.Mode.SHADOW && continuation == null)
		{
			final int healerObjectId = Integer.parseInt(lines.get(1).split("\t", 4)[3].split("\t", -1)[3]);
			normalHealControl(attempt, healerObjectId);
		}
		System.out.println("V3_NATIVE_DECISION_TIMING " + policy.timing());
		if (mode == TacticalPolicyV3.Mode.ON && continuation == null)
		{
			PhantomLearning003Damage.run(root, attempt, config);
			PhantomLearning003TemporalNative.run(root, attempt, config, legacy);
		}
		Files.writeString(phaseMarker(root, config), "native=" + committed + " changed=" + changed + "\n", StandardOpenOption.CREATE_NEW);
		Files.writeString(attempt.resolve("batch.complete"), "schemaVersion=3\ncommitted=" + committed + "\nchanged=" + changed + "\n", StandardOpenOption.CREATE_NEW);
		System.out.println("V3_NATIVE_BATCH_PASS mode=" + mode + " native=" + committed + " changed=" + changed + " evaluated=" + policy.evaluated.sum() + " overBudget=" + policy.overBudget.sum());
	}
	private static boolean threatened(Player player)
	{ return org.l2jmobius.gameserver.model.World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.actor.instance.Monster.class, 2000).stream().anyMatch(v -> v.hasAI() && (v.getAI().getAttackTarget() == player || v.getAI().getCastTarget() == player)); }
	private static void normalHealControl(Path attempt, int healerObjectId) throws Exception
	{
		final Player caster = Player.load(healerObjectId);
		if (caster == null || caster.isNativeWorkManaged() || caster.getKnownSkill(1401) == null) { throw new IllegalStateException("Ordinary native healer control unavailable"); }
		final Player recipient = Player.create(org.l2jmobius.gameserver.data.xml.PlayerTemplateData.getInstance().getTemplate(0), "learn001fixture",
			"V3H" + Long.toUnsignedString(System.nanoTime(), 36), new org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		if (recipient == null) { throw new IllegalStateException("Ordinary native recipient unavailable"); }
		try
		{
			caster.setOnlineStatus(true, false); recipient.setOnlineStatus(true, false);
			org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.initialize(recipient,
				org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.Mode.POPULATION, new Location(caster.getX() + 30, caster.getY(), caster.getZ()));
			recipient.getStat().setExp(recipient.getStat().getExpForLevel(78)); recipient.getStat().setLevel((byte) 78);
			recipient.setCurrentHp(recipient.getMaxHp() * .4);
			caster.spawnMe(); recipient.spawnMe();
			final var skill = caster.getKnownSkill(1401); final double hp = recipient.getCurrentHp(), mp = caster.getCurrentMp();
			final long exp = caster.getExp(), sp = caster.getSp(), recipientExp = recipient.getExp(), recipientSp = recipient.getSp(), credits = HealEvidenceV3.ACCEPTED.sum();
			Files.writeString(attempt.resolve("ordinary-heal.precut.tsv"), "caster\trecipient\tskill\thp\tmp\n" + caster.getObjectId() + "\t" + recipient.getObjectId() + "\t" + skill.getId() + ":" + skill.getLevel() + "\t" + hp + "\t" + mp + "\n", StandardOpenOption.CREATE_NEW);
			caster.setTarget(recipient); if (!caster.useMagic(skill, false, false)) { throw new IllegalStateException("Ordinary stock useMagic refused"); }
			final long deadline = System.nanoTime() + Duration.ofSeconds(12).toNanos(); boolean restored = false, spent = false;
			while (System.nanoTime() < deadline)
			{
				restored |= recipient.getCurrentHp() > hp + recipient.getMaxHp() * .15; spent |= caster.getCurrentMp() < mp - 1;
				if (restored && spent && !caster.isCastingNow()) { break; } Thread.sleep(50);
			}
			if (!restored || !spent || caster.isNativeWorkManaged() || recipient.isNativeWorkManaged() || caster.getExp() != exp || caster.getSp() != sp
				|| recipient.getExp() != recipientExp || recipient.getSp() != recipientSp || HealEvidenceV3.ACCEPTED.sum() != credits) { throw new IllegalStateException("Ordinary heal or no-credit conservation failed"); }
			System.out.println("V3_ORDINARY_HEAL_PASS skill=" + skill.getId() + ":" + skill.getLevel() + " hp=" + hp + "->" + recipient.getCurrentHp() + " mp=" + mp + "->" + caster.getCurrentMp() + " managed=false nativeLearningCredits=0 expSp=UNCHANGED");
		}
		finally { for (var player : List.of(caster, recipient)) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); } }
	}
	private PhantomLearning003Native() { }
}

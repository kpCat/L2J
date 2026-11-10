/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.GameServer;
import org.l2jmobius.gameserver.Shutdown;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeLootControl;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.LearningStore;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleMatcher;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartySupportPolicy;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** A first-boot fixture using the stock native owners and natural spawns. */
public final class PhantomLearning001ShadowNative
{
	private static final String URL = "jdbc:mysql://127.0.0.1:33317/phantom_learning001_native";
	private static final String OWNER_PATTERN = "LEARNING001:[0-9a-f]{32}";

	public static void main(String[] args) throws Exception
	{
		check(args.length == 7 && PhantomLearning001FixtureSql.writableLane(args[1]) && args[2].matches("[0-9a-f]{32}") && args[3].matches("[0-9a-f]{64}")
			&& Boolean.getBoolean("phantom.learning.train"), "EXPLICIT_OPT_IN");
		final boolean on = "on".equals(args[4]);
		final long version = Long.parseLong(args[5]);
		check(on ? version > 0 && args[6].matches("[0-9a-f]{64}") && Boolean.getBoolean("phantom.learning.witness")
			: "shadow".equals(args[4]) && version == 0 && "-".equals(args[6]), "EXPLICIT_PHASE");
		final Path lane = PhantomLearning001FixtureSql.resolveLane(args[0], args[1]);
		final Path attempt = lane.resolve("attempts").resolve(args[2]);
		check(attempt.toRealPath().equals(attempt), "PHYSICAL_ATTEMPT");
		final Path game = lane.resolve("game");
		check(Path.of(".").toRealPath().equals(game) && Files.readString(lane.resolve("owner.txt")).matches(OWNER_PATTERN), "OWNED_RUNTIME");
		final Path jar = game.resolve("libs/GameServer.jar");
		check(args[3].equals(hashFile(jar)), "JAR_SHA");
		for (Class<?> type : List.of(GameServer.class, PhantomCombatService.class, LearningPolicy.class))
		{
			check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar), "JAR_CLASS_ORIGIN");
		}
		final Path db = game.resolve("config/Database.ini");
		final var endpoints = new Properties();
		try (var reader = Files.newBufferedReader(game.resolve("config/Server.ini"))) { endpoints.load(reader); }
		check("127.0.0.1".equals(endpoints.getProperty("GameserverHostname")) && "17961".equals(endpoints.getProperty("GameserverPort"))
			&& "127.0.0.1".equals(endpoints.getProperty("LoginHost")) && "19261".equals(endpoints.getProperty("LoginPort"))
			&& ".".equals(endpoints.getProperty("DatapackRoot")), "OWNED_GAME_ENDPOINTS");
		check(Files.mismatch(db, lane.resolve("Database.ini")) == -1, "OWNED_DATABASE");
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(db)) { settings.load(reader); }
		check(URL.equals(settings.getProperty("URL")) && "learning001".equals(settings.getProperty("Login")), "OWNED_ENDPOINT");
		try (var connection = DriverManager.getConnection(URL, "learning001", settings.getProperty("Password")))
		{
			PhantomLearning001FixtureSql.requireActorIdentity(connection, lane);
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM phantom_ai_training_examples WHERE origin<>'NATIVE' OR evidence_kind<>'EXACT_CAST_HP'), (SELECT COUNT(*) FROM phantom_ai_model_versions)"))
			{
				check(rows.next() && rows.getInt(1) == 0 && (on ? rows.getInt(2) > 0 : rows.getInt(2) == 0), "NATIVE_ONLY_FOR_PHASE");
			}
		}
		final var actors = PhantomLearning001ActorFixture.readActors(lane.resolve("actors.tsv"));
		check(Files.readAllLines(lane.resolve("rosters.tsv")).size() == actors.size() + 1
			&& Files.readAllLines(lane.resolve("capacity.tsv")).size() == actors.size() + 1
			&& args[3].equals(Files.readString(lane.resolve("party.ready")).trim()), "ROSTER_AND_CAPACITY_PREFLIGHT");
		final Path config = game.resolve("config/Custom/PhantomPlayers.ini");
		final String off = Files.readString(config);
		check(off.contains("EnablePhantomSystem = False") && off.contains("LearningMode = OFF")
			&& off.contains("LearningModelVersion = 0") && off.contains("LearningEvidenceEnabled = False")
			&& off.contains("LearningDeterministicSeed = 0"), "MASTER_OFF_AT_BOOT");
		final String mode = on ? "ON" : "SHADOW";
		final String phaseConfig = off.replace("LearningMode = OFF", "LearningMode = " + mode)
			.replace("LearningModelVersion = 0", "LearningModelVersion = " + version)
			.replace("LearningEvidenceEnabled = False", "LearningEvidenceEnabled = True");
		final String pid = Long.toString(ProcessHandle.current().pid());
		Files.writeString(attempt.resolve("cut.properties"), "phase=" + mode + "\npid=" + pid + "\njarSha256=" + args[3] + "\n", StandardOpenOption.CREATE_NEW);
		boolean booted = false;
		try
		{
			Files.writeString(config, phaseConfig);
			GameServer.main(new String[0]);
			booted = true;
			Files.writeString(attempt.resolve("game.ready"), pid, StandardOpenOption.CREATE_NEW);
			final Path socket = attempt.resolve("socket.ok");
			await(() -> Files.isRegularFile(socket), Duration.ofSeconds(45), "OS_SOCKET_ATTESTATION_TIMEOUT");
			check(pid.equals(Files.readString(socket)), "OS_SOCKET_ATTESTATION_PID");
			final Path stop = attempt.resolve("stop.request");
			check(!Files.exists(stop), "OPERATOR_STOP");
			check(!PhantomPlayersConfig.isEnabled() && !PhantomSystem.hasConfiguredInstance(), "GLOBAL_OWNER_INACTIVE");
			final String enabled = phaseConfig.replace("EnablePhantomSystem = False", "EnablePhantomSystem = True");
			Files.writeString(config, enabled);
			PhantomPlayersConfig.load();
			if (on)
			{
				final var model = new LearningStore(URL, "learning001", settings.getProperty("Password"), game).accepted(version);
				check(model != null && model.model().sha256().equals(args[6]), "COLD_ACCEPTED_SQL_SHA");
				System.out.println("LEARNING001_COLD_SQL_MODEL version=" + version + " sha256=" + model.model().sha256() + " trainingHash=" + model.trainingHash());
			}
			final var policy = LearningPolicy.configured(game);
			try
			{
				check(policy.collecting() && policy.active(LearningScope.SOLO) && policy.active(LearningScope.PARTY), "CONFIGURED_" + mode);
				final var helpers = actors.stream().filter(row -> "FIXTURE".equals(row[0])).toList();
				try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start();
					var runtime = new Runtime(source, game, policy, stop, on, 2 + helpers.size()))
				{
					final String salt = key("learning001/actor/phantom_learning001_native/" + settings.getProperty("Password"));
					final var splits = on ? List.of("AUDIT") : List.of("TRAIN", "TUNING", "AUDIT");
					for (String split : splits)
					{
						final var roster = actors.stream().filter(row -> split.equals(row[0])).toList();
						runtime.party(roster, helpers, salt, "TRAIN".equals(split) ? 8 : 4);
					}
					for (String split : splits)
					{
						final var roster = actors.stream().filter(row -> split.equals(row[0])).toList();
						final var solo = roster.stream().filter(row -> "SOLO".equals(row[1])).findFirst().orElseThrow();
						runtime.solo(solo, salt, "TRAIN".equals(split) ? 8 : 4);
					}
				}
				final String evidence = on ? "modelVersion=" + version + "\nmodelSha=" + args[6] + "\nexactSolo="
					+ policy.exactReordered(LearningScope.SOLO) + "\nexactParty=" + policy.exactReordered(LearningScope.PARTY) : "nativeSqlMinimum=32";
				Files.writeString(attempt.resolve(args[4] + ".complete"), evidence + "\npolicy=" + policy.counters() + "\n", StandardOpenOption.CREATE_NEW);
			}
			finally { await(policy::stop, Duration.ofSeconds(15), "WRITER_STOP"); }
		}
		catch (Exception failure)
		{
			Files.writeString(attempt.resolve(args[4] + ".failed"), failure.getClass().getSimpleName() + ": " + failure.getMessage(), StandardOpenOption.CREATE_NEW);
			throw failure;
		}
		finally
		{
			try { Files.writeString(config, off); }
			finally
			{
				if (booted) { PhantomPlayersConfig.load(); Shutdown.getInstance().startShutdown(null, 0, false); }
			}
		}
	}

	static void preflightParty(PhantomBackgroundSuite.ProductionAuthorityFixture source, Path game, Path lane, List<String[]> actors) throws Exception
	{
		final var helpers = actors.stream().filter(row -> "FIXTURE".equals(row[0])).toList();
		try (var runtime = new Runtime(source, game, LearningPolicy.OFF, lane.resolve("preflight.stop"), false, 2 + helpers.size()))
		{
			runtime.coordinator.installLogicalFarmAdmission(_ -> false);
			for (String split : List.of("TRAIN", "TUNING", "AUDIT"))
			{
				final var roster = new java.util.ArrayList<>(actors.stream().filter(row -> split.equals(row[0])).toList());
				roster.addAll(helpers);
				final var players = roster.stream().map(runtime::materialize).toList();
				for (int index = 0; index < roster.size(); index++)
				{
					final var row = roster.get(index);
					final Player player = players.get(index);
					check(!player.isAlikeDead() && player.getLevel() >= Integer.parseInt(row[5]), "PARTY_PREFLIGHT_ACTOR_STATE profile=" + row[2]
						+ " declaredLevel=" + row[5] + " loadedLevel=" + player.getLevel() + " hp=" + player.getCurrentHp() + " dead=" + player.isAlikeDead());
				}
				runtime.inspectParty(roster, players);
				runtime.provePartyTarget(roster, players);
				for (String[] row : roster) { runtime.store(Long.parseLong(row[2])); }
			}
		}
	}

	private static final class Runtime implements AutoCloseable
	{
		private final Path stop;
		private final boolean on;
		private final PhantomMaterializationService materialization;
		private final PhantomProgressionService progression;
		private final PhantomNavigationService navigation;
		private final PhantomCombatService combat;
		private final L2jPhantomPartyBackend backend;
		private final PhantomPartyRoleMatcher roles;
		private final PhantomPartyCoordinator coordinator;
		private final PhantomAutonomousFarmParty autonomous;
		private final PhantomGoalStateStore goals;
		private final LearningPolicy policy;

		Runtime(PhantomBackgroundSuite.ProductionAuthorityFixture source, Path game, LearningPolicy policy, Path stop, boolean on, int partySize) throws Exception
		{
			this.policy = policy;
			this.stop = stop;
			this.on = on;
			final var profiles = PhantomProfileRepository.open();
			goals = new PhantomGoalStateStore(profiles);
			final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics,
				new PhantomDiagnosticTrace(false, 0, 0, metrics), partySize);
			progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, game, source::knowledge), PhantomProgressionPolicy.productionDefaults());
			navigation = new PhantomNavigationService(metrics);
			combat = new PhantomCombatService(new L2jCombatBackend(materialization, source::knowledge, () -> progression.findCatalog().orElse(null)),
				PhantomCombatCapabilityResolver.fromProgression(() -> progression.findCatalog().orElse(null)), PhantomCombatPolicy.productionDefaults(64));
			combat.installLearning(policy);
			backend = new L2jPhantomPartyBackend(profiles, materialization, progression);
			final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
			backend.installFarmSourcePolicy((farm, target) -> planner.partyNativeTarget(farm, target.asNpc().getId(),
				new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId())));
			final Path rules = game.resolve("data/phantoms/party");
			final var roleCatalog = PhantomPartyRoleCatalog.load(rules.resolve("high-five-party-roles-v1.xml"));
			roles = new PhantomPartyRoleMatcher(roleCatalog);
			final var tactics = new PhantomPartyTactics(combat, backend, PhantomPartySupportPolicy.load(rules.resolve("high-five-party-support-v1.xml")));
			coordinator = new PhantomPartyCoordinator(new PhantomPartyStore(profiles), goals, backend, roleCatalog,
				new PhantomPartyRouteCoordinator(navigation, combat), tactics, () -> source.topology().snapshot().canonicalHash(), System::nanoTime, 64);
			autonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, roleCatalog, System::nanoTime);
			progression.start();
			check(materialization.start() && navigation.start() && coordinator.start(), "NATIVE_OWNERS_START");
			combat.start();
		}

		private Player materialize(String[] row)
		{
			final long profile = Long.parseLong(row[2]);
			final var result = materialization.materialize(profile);
			check(result.status() == PhantomMaterializationService.ResultStatus.SUCCESS, "MATERIALIZE_" + profile + " status=" + result.status());
			final Player player = World.getInstance().getPlayer(Integer.parseInt(row[3]));
			check(player != null && !player.isGM() && player.getParty() == null && PlayerNativeWork.observationEvidence(player) != null, "NATIVE_PLAYER_" + profile);
			return player;
		}

		void solo(String[] row, String salt, int minimum) throws Exception
		{
			final long profile = Long.parseLong(row[2]);
			final Player player = materialize(row);
			final long deadline = System.nanoTime() + Duration.ofMillis(PhantomLearning001ActorFixture.sampleWindow(minimum)).toNanos();
			final String group = key(salt + ":" + profile);
			long progressed = System.nanoTime();
			long highWater = 0;
			for (Samples samples = samples("SOLO", group, group); !finished(LearningScope.SOLO, samples, minimum, 1); samples = samples("SOLO", group, group))
			{
				check(!Files.exists(stop), "OPERATOR_STOP");
				if (samples.count() > highWater) { highWater = samples.count(); progressed = System.nanoTime(); }
				check(System.nanoTime() - progressed < Duration.ofMinutes(3).toNanos(), "SOLO_SQL_STALLED_" + row[0] + " " + samples + " " + policy.counters());
				if (player.isAlikeDead())
				{
					throw new IllegalStateException("LEARNING001_SOLO_DIED profile=" + profile + " hp=" + player.getCurrentHp() + " mp=" + player.getCurrentMp()
						+ " combat=" + combat.find(profile) + " stunned=" + player.isStunned() + " " + samples);
				}
				check(System.nanoTime() < deadline, "SOLO_SAMPLE_DEADLINE_" + row[0] + " " + policy.counters());
				if (readyForSoloSession(combat, profile))
				{
					if (retreat(profile, player) || recover(player))
					{
						Thread.sleep(600);
						continue;
					}
					final var targets = PhantomLearning001ActorFixture.soloOpeningTargets(player);
					final var target = targets.isEmpty() ? null : targets.getFirst();
					if (target != null)
					{
						final long timeout = 45000;
						final var request = new PhantomCombatRequest(profile, target.getObjectId(), PhantomCombatMode.RANGED_MAGIC, true, false, timeout,
							() -> false, LearningScope.SOLO, null, () -> false);
						final var result = combat.startSession(request);
						if (result.status() == StartStatus.ACCEPTED && result.session() != null)
						{
							System.out.println("LEARNING001_SOLO_SESSION profile=" + profile + " actor=" + player.getObjectId() + " epoch="
								+ player.getNativeWorkOwner().epoch() + " generation=" + result.session().generation() + " target=" + target.getNativeEvidenceTarget()
								+ " npc=" + target.getId() + " distance=" + player.calculateDistance2D(target) + " hp=" + player.getCurrentHp()
								+ " mp=" + player.getCurrentMp() + " timeoutMs=" + timeout);
						}
					}
				}
				Thread.sleep(1200);
			}
			combat.cancel(profile);
			await(() -> readyForSoloSession(combat, profile) && !retreat(profile, player), Duration.ofSeconds(60), "SOLO_RELEASE_" + profile);
			store(profile);
			System.out.println(on ? "LEARNING001_SOLO_EXACT_REORDERED count=" + policy.exactReordered(LearningScope.SOLO)
				: "LEARNING001_SOLO_SQL_MINIMUM split=" + row[0] + " profile=" + profile + " countAtLeast=" + minimum + " bothSkills=true");
		}

		private boolean retreat(long profile, Player player)
		{
			final var threat = PhantomNativeLootControl.threat(player);
			if (threat == null || player.isAlikeDead()) { return false; }
			if (player.isSitting()) { player.standUp(); }
			if (player.isMoving() || player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.getQueuedSkill() != null) { return true; }
			final var request = new PhantomCombatRequest(profile, threat.getObjectId(), PhantomCombatMode.RANGED_MAGIC, false, false, 30000, () -> false);
			final var result = combat.startContentRetreatSession(request, "learning001.fixture.recovery");
			if (result.status() == StartStatus.ACCEPTED)
			{
				System.out.println("LEARNING001_SOLO_RETREAT profile=" + profile + " threat=" + threat.getObjectId() + " hp=" + player.getCurrentHp());
			}
			return true;
		}

		private static boolean recover(Player player)
		{
			if (player.isMoving() || player.isAttackingNow() || player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.getQueuedSkill() != null) { return true; }
			final var skill = player.getKnownSkill(1230);
			final double mp = (player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill)) * 3;
			if (!PhantomCombatPolicy.pveResourcesReady(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), mp, player.isSitting()))
			{
				if (!player.isSitting()) { player.sitDown(); }
				return true;
			}
			if (player.isSitting()) { player.standUp(); return true; }
			return false;
		}

		private void inspectParty(List<String[]> roster, List<Player> players)
		{
			final var first = players.getFirst();
			PhantomLearning001ActorFixture.requireSafePartySpawn(first.getX(), first.getY(), first.getZ());
			final var page = backend.materializedFarmMembers(0, PhantomAutonomousFarmParty.MAXIMUM_CANDIDATES);
			final var candidates = page.members().stream().filter(member -> backend.autonomousFarmAvailable(member.ref())).toList();
			for (int index = 0; index < roster.size(); index++)
			{
				final long id = Long.parseLong(roster.get(index)[2]);
				final var member = backend.currentMember(id).orElseThrow();
				final var player = players.get(index);
				final var goal = goals.load(id).map(row -> row.goal()).orElse(null);
				final var facts = backend.memberSnapshot(member).orElse(null);
				System.out.println("LEARNING001_PARTY_PREFLIGHT profile=" + id + " level=" + backend.currentLevel(member)
					+ " ordinaryGoal=" + PhantomAutonomousFarmParty.ordinaryIntent(id, goal) + " inPage=" + page.members().stream().anyMatch(row -> row.ref().equals(member))
					+ " available=" + backend.autonomousFarmAvailable(member) + " transaction=" + player.isProcessingTransaction() + " moving=" + player.isMoving()
					+ " contributor=" + (facts != null && PhantomAutonomousFarmParty.farmContributor(facts)) + " capabilities=" + (facts == null ? List.of()
						: facts.capabilities().stream().map(value -> value.capabilityKey() + ":" + value.actionSkillId() + ":" + value.readyNow()).toList())
					+ " x=" + player.getX() + " y=" + player.getY() + " z=" + player.getZ());
				if (!"FIXTURE".equals(roster.get(index)[0]))
				{
					check(progression.capabilities(id, null).stream().anyMatch(capability -> capability.actionSkill().skillId() == 1230 && capability.learned()),
						"AUTHORED_PROMINENCE_" + id);
					try (var observation = materialization.tryAcquireObservation(id).orElseThrow(); var action = materialization.tryAcquireAction(id).orElse(null))
					{
						check(observation.player() == player && action == null, "PARTY_OBSERVATION_DOES_NOT_ADMIT_ACTION_" + id);
					}
					check(facts != null && facts.capabilities().stream().anyMatch(capability -> "combat.ranged_magic_damage".equals(capability.capabilityKey())
						&& capability.actionSkillId() == 1230 && capability.learned()), "PARTY_OBSERVES_LEARNED_PROMINENCE_" + id);
				}
			}
			final var team = candidates.stream().findFirst().map(leader -> PhantomAutonomousFarmParty.selectTeam(roles, leader, candidates)).orElse(List.of());
			System.out.println("LEARNING001_PARTY_PREFLIGHT_TEAM " + team);
			check(team.size() == roster.size(), "STOCK_PARTY_TEAM_ADMISSION");
		}

		private void provePartyTarget(List<String[]> roster, List<Player> players) throws Exception
		{
			final var profiles = roster.stream().map(row -> Long.parseLong(row[2])).toList();
			await(() ->
			{
				check(players.stream().noneMatch(PhantomLearning001ShadowNative::preflightThreatened), "STOCK_PARTY_PREFLIGHT_INVITE_THREAT");
				final var nativeParty = players.getFirst().getParty();
				if (nativeParty == null || !nativeParty.getMembers().containsAll(players)) { autonomous.onPulse(); }
				coordinator.onPulse();
				return players.getFirst().getParty() != null && players.getFirst().getParty().getMembers().containsAll(players)
					&& profiles.stream().allMatch(coordinator::committed);
			}, Duration.ofSeconds(45), "STOCK_PARTY_PREFLIGHT_INVITES");
			final long leader = profiles.get(players.indexOf(players.getFirst().getParty().getLeader()));
			final var party = backend.currentMember(leader).flatMap(backend::observe).orElseThrow();
			final var authority = coordinator.claim(leader).orElseThrow().state();
			final var supports = authority.assignments().stream().filter(row -> row.roleKey().startsWith("support."))
				.map(row -> row.member()).collect(java.util.stream.Collectors.toSet());
			final var farm = PhantomAutonomousFarmParty.farmSource(goals.load(leader).orElseThrow().goal());
			final var reasons = new java.util.ArrayList<String>();
			int target = backend.autonomousFarmTarget(party, supports, farm, reasons::add).orElse(0);
			final long deadline = System.nanoTime() + Duration.ofSeconds(75).toNanos();
			// Natural spawn movement and HP regeneration may change the same stock risk verdict.
			while (target == 0 && System.nanoTime() < deadline && players.stream().noneMatch(PhantomLearning001ShadowNative::preflightThreatened))
			{
				Thread.sleep(1500);
				reasons.clear();
				target = backend.autonomousFarmTarget(party, supports, farm, reasons::add).orElse(0);
			}
			final var threatened = players.stream().filter(PhantomLearning001ShadowNative::preflightThreatened).map(Player::getObjectId).toList();
			System.out.println("LEARNING001_PARTY_PREFLIGHT_TARGET profiles=" + profiles + " leader=" + leader + " target=" + target
				+ " wait=" + backend.autonomousFarmWaitReason(party.leader()) + " threatened=" + threatened + " " + reasons);
			check(combat.snapshot().activeSessions() == 0, "NO_PREFLIGHT_COMBAT");
			await(() ->
			{
				coordinator.onPulse();
				return coordinator.drainAutonomousFarm(leader);
			}, Duration.ofSeconds(30), "STOCK_PARTY_PREFLIGHT_RELEASE");
			check(players.stream().noneMatch(Player::isInParty), "STOCK_PARTY_PREFLIGHT_DISBANDED");
			check(threatened.isEmpty(), "STOCK_PARTY_PREFLIGHT_THREAT");
			check(target > 0, "STOCK_PARTY_PREFLIGHT_TARGET");
		}

		void party(List<String[]> rows, List<String[]> helpers, String salt, int minimum) throws Exception
		{
			final var ids = rows.stream().map(row -> Long.parseLong(row[2])).toList();
			final var farm = PhantomAutonomousFarmParty.farmSource(goals.load(ids.getFirst()).orElseThrow().goal());
			check(farm != null && farm.equals(PhantomAutonomousFarmParty.farmSource(goals.load(ids.getLast()).orElseThrow().goal()))
				&& helpers.stream().allMatch(row -> farm.equals(PhantomAutonomousFarmParty.farmSource(goals.load(Long.parseLong(row[2])).orElseThrow().goal()))), "SHARED_STOCK_SOURCE");
			final String first = key(salt + ":" + ids.getFirst());
			final String last = key(salt + ":" + ids.getLast());
			final int minimumActors = "AUDIT".equals(rows.getFirst()[0]) ? 2 : 1;
			if (!on && samples("PARTY", first, last).enough(minimum, minimumActors))
			{
				System.out.println("LEARNING001_PARTY_SQL_RESUME split=" + rows.getFirst()[0]);
				return;
			}
			final var roster = new java.util.ArrayList<>(rows);
			roster.addAll(helpers);
			final var profiles = roster.stream().map(row -> Long.parseLong(row[2])).toList();
			final var players = roster.stream().map(this::materialize).toList();
			inspectParty(roster, players);
			final long deadline = System.nanoTime() + Duration.ofMillis(PhantomLearning001ActorFixture.sampleWindow(minimum)).toNanos();
			boolean observed = false;
			long progressed = System.nanoTime();
			long highWater = 0;
			long nextStatus = 0;
			long nextSample = 0;
			Samples samples = samples("PARTY", first, last);
			while (!finished(LearningScope.PARTY, samples, minimum, minimumActors))
			{
				check(!Files.exists(stop), "OPERATOR_STOP");
				if (samples.count() > highWater) { highWater = samples.count(); progressed = System.nanoTime(); }
				check(System.nanoTime() - progressed < Duration.ofMinutes(3).toNanos(),
					"PARTY_SQL_STALLED_" + rows.getFirst()[0] + " " + samples + " " + policy.counters() + " " + coordinator.snapshot());
				check(System.nanoTime() < deadline && players.stream().noneMatch(Player::isAlikeDead),
					"PARTY_SAMPLE_DEADLINE_" + rows.getFirst()[0] + " " + policy.counters() + " " + coordinator.snapshot());
				coordinator.onPulse();
				autonomous.onPulse();
				final var party = players.getFirst().getParty();
				if (!observed && party != null && party == players.getLast().getParty() && party.getMembers().containsAll(players))
				{
					observed = true;
					System.out.println("LEARNING001_AUTONOMOUS_STOCK_PARTY profiles=" + profiles + " source=" + farm.key());
				}
				if (System.nanoTime() >= nextStatus)
				{
					final var members = players.stream().map(player ->
					{
						final var cast = player.observeNativeMagicUse(16).primary();
						return player.getObjectId() + ":hp=" + player.getCurrentHp() + ":mp=" + player.getCurrentMp()
							+ ":target=" + (player.getTarget() == null ? 0 : player.getTarget().getObjectId())
							+ ":cast=" + (cast == null || cast.skill() == null ? 0 : cast.skill().getId());
					}).toList();
					final var threats = players.stream().map(PhantomNativeLootControl::threat).filter(java.util.Objects::nonNull)
						.map(threat -> threat.getObjectId() + ":npc=" + (threat.isNpc() ? threat.asNpc().getId() : 0)).distinct().toList();
					final var wait = backend.currentMember(profiles.getFirst()).map(backend::autonomousFarmWaitReason).orElse("NO_LEADER");
					System.out.println("LEARNING001_PARTY_PROGRESS split=" + rows.getFirst()[0] + " observed=" + observed + " " + samples + " wait=" + wait
						+ " members=" + members + " threats=" + threats + " " + coordinator.snapshot() + " " + combat.snapshot());
					nextStatus = System.nanoTime() + Duration.ofSeconds(20).toNanos();
				}
				if (System.nanoTime() >= nextSample)
				{
					samples = samples("PARTY", first, last);
					nextSample = System.nanoTime() + Duration.ofSeconds(1).toNanos();
				}
				Thread.sleep(PhantomPlayersConfig.settings().schedulerPulseMillis());
			}
			check(observed, "STOCK_PARTY_OBSERVED");
			final Player leader = players.getFirst().getParty() == null ? null : players.getFirst().getParty().getLeader();
			for (long profile : profiles) { combat.cancel(profile); }
			if (leader != null)
			{
				final long profile = profiles.get(players.indexOf(leader));
				await(() -> coordinator.drainAutonomousFarm(profile), Duration.ofSeconds(30), "PARTY_CANONICAL_RELEASE_" + profile);
			}
			for (long profile : profiles) { store(profile); }
			System.out.println(on ? "LEARNING001_PARTY_EXACT_REORDERED count=" + policy.exactReordered(LearningScope.PARTY)
				: "LEARNING001_PARTY_SQL_MINIMUM split=" + rows.getFirst()[0] + " profiles=" + ids + " countAtLeast=" + minimum + " bothSkills=true");
		}

		private boolean finished(LearningScope scope, Samples samples, int minimum, int minimumActors)
		{
			return on ? policy.exactReordered(scope) > 0 : samples.enough(minimum, minimumActors);
		}

		private void store(long profile) throws Exception
		{
		await(() ->
		{
			final var status = materialization.dematerialize(profile).status();
			return status == PhantomMaterializationService.ResultStatus.SUCCESS || status == PhantomMaterializationService.ResultStatus.NOT_ACTIVE;
		}, Duration.ofSeconds(30), "STORE_" + profile);
		}

		@Override
		public void close() throws Exception
		{
			coordinator.beginStop();
			combat.beginStop();
			navigation.beginStop();
			await(combat::finishStop, Duration.ofSeconds(30), "COMBAT_STOP");
			await(coordinator::finishStop, Duration.ofSeconds(30), "PARTY_STOP");
			await(navigation::finishStop, Duration.ofSeconds(30), "NAVIGATION_STOP");
			check(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "MATERIALIZATION_STOP");
			progression.beginStop();
			await(progression::finishStop, Duration.ofSeconds(30), "PROGRESSION_STOP");
			System.out.println("LEARNING001_NATIVE_OWNERS_STOP " + policy.counters());
		}
	}

	private static boolean preflightThreatened(Player player)
	{
		return player.isAlikeDead() || PhantomNativeLootControl.threat(player) != null;
	}

	private record Samples(long count, int skills, int actors)
	{
		boolean enough(int minimum, int minimumActors) { return count >= minimum && skills == 2 && actors >= minimumActors; }
	}

	private static Samples samples(String scope, String first, String last) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement(
			"SELECT COUNT(*), COUNT(DISTINCT skill_id), COUNT(DISTINCT actor_group) FROM phantom_ai_training_examples WHERE origin='NATIVE' AND evidence_kind='EXACT_CAST_HP' "
			+ "AND policy_version=0 AND damage>0 AND scope=? AND actor_group IN (?,?)"))
		{
			statement.setString(1, scope);
			statement.setString(2, first);
			statement.setString(3, last);
			try (var rows = statement.executeQuery())
			{
				check(rows.next(), "SQL_SAMPLE_TOTALS");
				return new Samples(rows.getLong(1), rows.getInt(2), rows.getInt(3));
			}
		}
	}

	private static String key(String value) { return NeuralPolicy.sha256(value.getBytes(StandardCharsets.UTF_8)); }

	private static String hashFile(Path path) throws Exception
	{
		final var digest = MessageDigest.getInstance("SHA-256");
		try (var in = new DigestInputStream(Files.newInputStream(path), digest)) { in.transferTo(java.io.OutputStream.nullOutputStream()); }
		return HexFormat.of().formatHex(digest.digest());
	}

	static boolean readyForSoloSession(PhantomCombatService combat, long profile)
	{
		combat.consumeTerminal(profile).ifPresent(snapshot -> System.out.println("LEARNING001_SOLO_TERMINAL profile=" + profile
			+ " generation=" + snapshot.generation() + " target=" + snapshot.targetObjectId() + " result=" + snapshot.result()));
		return !combat.hasClaim(profile);
	}

	private static void await(BooleanSupplier finished, Duration duration, String label) throws InterruptedException
	{
		final long deadline = System.nanoTime() + duration.toNanos();
		while (!finished.getAsBoolean()) { check(System.nanoTime() < deadline, label); Thread.sleep(100); }
	}

	private static void check(boolean condition, String failure)
	{
		if (!condition) { throw new IllegalStateException("LEARNING001_" + failure); }
	}
}

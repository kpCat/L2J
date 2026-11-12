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
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.StartStatus;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeLootControl;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPartySupportAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.learning.LearningFeatures;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.LearningStore;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TrainingCollector;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.RequestState;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleMatcher;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartySupportPolicy;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
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
		check(args.length == 8 && PhantomLearning001FixtureSql.writableLane(args[1]) && args[2].matches("[0-9a-f]{32}") && args[3].matches("[0-9a-f]{64}")
			&& Boolean.getBoolean("phantom.learning.train"), "EXPLICIT_OPT_IN");
		final boolean on = "on".equals(args[4]);
		final long version = Long.parseLong(args[5]);
		final String proofScope = args[7];
		check(on ? java.util.Set.of("BOTH", "SOLO", "PARTY").contains(proofScope) : "BOTH".equals(proofScope), "EXPLICIT_PROOF_SCOPE");
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
			&& Files.readAllLines(lane.resolve("capacity.tsv")).size() == actors.size() + 1, "ROSTER_AND_CAPACITY_PREFLIGHT");
		if (!"SOLO".equals(proofScope)) { check(args[3].equals(Files.readString(lane.resolve("party.ready")).trim()), "PARTY_PREFLIGHT_PIN"); }
		if (on && !"PARTY".equals(proofScope)) { check(args[3].equals(Files.readString(lane.resolve("solo.ready")).trim()), "SOLO_PREFLIGHT_PIN"); }
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
					if (on && !"PARTY".equals(proofScope)) { runtime.solo(soloActor(actors, "AUDIT"), salt, 4); }
					if (!"SOLO".equals(proofScope))
					{
						for (String split : splits)
						{
							final var roster = actors.stream().filter(row -> split.equals(row[0])).toList();
							runtime.party(roster, helpers, salt, "TRAIN".equals(split) ? 8 : 4);
						}
					}
					if (!on)
					{
						for (String split : splits) { runtime.solo(soloActor(actors, split), salt, "TRAIN".equals(split) ? 8 : 4); }
					}
				}
				final String evidence = on ? "modelVersion=" + version + "\nmodelSha=" + args[6] + "\nproofScope=" + proofScope + "\nexactSolo="
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

	static void preflightParty(PhantomBackgroundSuite.ProductionAuthorityFixture source, Path game, Path lane, List<String[]> actors, boolean recovery, String proofScope) throws Exception
	{
		final var helpers = actors.stream().filter(row -> "FIXTURE".equals(row[0])).toList();
		try (var runtime = new Runtime(source, game, LearningPolicy.OFF, lane.resolve("preflight.stop"), false, 2 + helpers.size()))
		{
			runtime.coordinator.installLogicalFarmAdmission(_ -> false);
			for (String split : recovery ? List.of("AUDIT") : List.of("TRAIN", "TUNING", "AUDIT"))
			{
				final var roster = new java.util.ArrayList<>(actors.stream().filter(row -> split.equals(row[0])).toList());
				roster.addAll(helpers);
				final var players = roster.stream().map(runtime::materialize).toList();
				if (recovery)
				{
					final var solo = soloActor(roster, "AUDIT");
					final var learner = players.get(roster.indexOf(solo));
					final var heal = helpers.stream().filter(row -> "HEAL_HELPER".equals(row[1])).findFirst().orElseThrow();
					if (learner.isDead()) { runtime.resurrect(solo, learner, heal, players.get(roster.indexOf(heal))); }
					runtime.returnToPartyAnchor(solo, learner);
					if (!"PARTY".equals(proofScope))
					{
						await(() -> !Runtime.recover(learner, true), Duration.ofSeconds(90), "RECOVERY_SOLO_REST");
						final var targets = PhantomLearning001ActorFixture.coldSoloOpeningTargets(learner);
						if (targets.isEmpty()) { PhantomLearning001ActorFixture.inspectColdSoloOpening(learner); }
						check(!targets.isEmpty(), "RECOVERY_SOLO_VIABLE_OPENING");
						System.out.println("LEARNING001_RECOVERY_SOLO_READY profile=" + solo[2] + " targets=" + targets.stream().map(npc -> npc.getId()).toList());
					}
				}
				for (int index = 0; index < roster.size(); index++)
				{
					final var row = roster.get(index);
					final Player player = players.get(index);
					check(!player.isAlikeDead() && (recovery || player.getLevel() >= Integer.parseInt(row[5])), "PARTY_PREFLIGHT_ACTOR_STATE profile=" + row[2]
						+ " declaredLevel=" + row[5] + " loadedLevel=" + player.getLevel() + " hp=" + player.getCurrentHp() + " dead=" + player.isAlikeDead());
					if (recovery && player.getLevel() != Integer.parseInt(row[5]))
					{
						System.out.println("LEARNING001_RECOVERY_NATIVE_LEVEL profile=" + row[2] + " declared=" + row[5] + " loaded=" + player.getLevel());
					}
				}
				if (!"SOLO".equals(proofScope))
				{
					runtime.inspectParty(roster, players);
					runtime.provePartyTarget(roster, players);
				}
				for (String[] row : roster) { runtime.store(Long.parseLong(row[2])); }
			}
		}
	}

	static final class Runtime implements AutoCloseable
	{
		private final Path stop;
		private final boolean on;
		private final PhantomMaterializationService materialization;
		private final PhantomProgressionService progression;
		private final PhantomNavigationService navigation;
		private final PhantomCombatService combat;
		private final L2jPhantomPartyBackend backend;
		private final PhantomPartyRoleMatcher roles;
		private final PhantomPartyTactics healTactics;
		private final PhantomPartyCoordinator coordinator;
		private final PhantomAutonomousFarmParty autonomous;
		private final PhantomGoalStateStore goals;
		private final LearningPolicy policy;
		private final PhantomHistoricalBackgroundPlanner sourcePlanner;
		private List<String[]> trainingRows = List.of();
		private List<Player> trainingPlayers = List.of();
		private PhantomTrainingScenarios.Scene trainingScene;
		private boolean trainingParty;
		private volatile boolean trainingAdmission;

		Runtime(PhantomBackgroundSuite.ProductionAuthorityFixture source, Path game, LearningPolicy policy, Path stop, boolean on, int partySize) throws Exception
		{
			this(source, game, policy, stop, on, partySize, org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3.OFF);
		}

		Runtime(PhantomBackgroundSuite.ProductionAuthorityFixture source, Path game, LearningPolicy policy, Path stop, boolean on, int partySize,
			org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3 tactical) throws Exception
		{
			this(source, game, policy, stop, on, partySize, tactical, true);
		}
		private Monster evaluationTarget;
		private long evaluationStarted, evaluationStopped, evaluationTerminal, evaluationDrainNanos;
		private BooleanSupplier evaluationTerminalProbe = () -> false;
		double evaluationEncounterMillis() { return ((evaluationTerminal > 0 ? evaluationTerminal : evaluationStopped) - evaluationStarted) / 1e6; }
		double evaluationDrainMillis() { return evaluationDrainNanos / 1e6; }
		long outcomeCutNanos(){return evaluationStarted;}
		long outcomeTerminalNanos(){return evaluationTerminal>0?evaluationTerminal:evaluationStopped;}
		private boolean outcome006;
		private boolean outcomeEncounterAdmitted;
		boolean outcomeEncounterAdmitted(){return outcomeEncounterAdmitted;}
		static final class OutcomeDeaths006
		{
			record Snapshot(long first,java.util.Map<Integer,Long> times)
			{boolean firstDeath(int object){return times.getOrDefault(object,Long.MAX_VALUE)<=first;}}
			private final java.util.Map<Integer,Long> times=new java.util.HashMap<>();
			private Snapshot frozen;
			synchronized void observe(int object,long stamp)
			{if(frozen==null){times.merge(object,stamp,Math::min);}}
			synchronized Snapshot snapshot(long deadline,boolean freeze)
			{
				if(frozen!=null){return frozen;}
				final var bounded=new java.util.HashMap<Integer,Long>();
				for(var entry:times.entrySet()){if(entry.getValue()<=deadline){bounded.put(entry.getKey(),entry.getValue());}}
				final long first=bounded.values().stream().mapToLong(Long::longValue).min().orElse(0);
				final var result=new Snapshot(first,java.util.Map.copyOf(bounded));if(freeze){frozen=result;}return result;
			}
		}
		private OutcomeDeaths006 outcomeDeaths=new OutcomeDeaths006();
		private OutcomeDeaths006.Snapshot outcomeFrozen;
		private long outcomeDeadline;
		private final java.util.Map<org.l2jmobius.gameserver.model.actor.Creature,org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener> outcomeListeners=new java.util.LinkedHashMap<>();
		void prepareOutcome006(Monster target,int variant) throws Exception
		{
			prepareEvaluation(target,variant);
			if(!trainingParty)
			{
				final var player=trainingPlayers.getFirst();
				final var geo=org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance();
				check(geo.canMoveToTarget(player.getX(),player.getY(),player.getZ(),target.getX()+180,target.getY(),target.getZ(),player.getInstanceId()),"OUTCOME006_PRE_CUT_GEO");
				player.teleToLocation(target.getX()+180,target.getY(),target.getZ(),0,false);
				if(player.hasHeadlessOutboundSession()&&player.isTeleporting()){player.onTeleported();}
				check(PhantomLearning001ActorFixture.soloOpeningTargets(player).contains(target),"OUTCOME006_FULL_SOLO_ADMISSION");
			}
			outcome006=true;outcomeDeaths=new OutcomeDeaths006();outcomeFrozen=null;outcomeEncounterAdmitted=false;
			final var observed=new java.util.ArrayList<org.l2jmobius.gameserver.model.actor.Creature>(trainingPlayers);observed.add(target);
			for(var creature:observed)
			{
				final var listener=new org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener(creature,org.l2jmobius.gameserver.model.events.EventType.ON_CREATURE_DEATH,
					(org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDeath event)->
					{if(event.getTarget()==creature){outcomeDeaths.observe(creature.getObjectId(),System.nanoTime());org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence.terminalDeath(creature);}},this);
				creature.addListener(listener);outcomeListeners.put(creature,listener);
			}
		}
		void releaseOutcome006()
		{for(var entry:outcomeListeners.entrySet()){entry.getKey().removeListener(entry.getValue());}outcomeListeners.clear();}
		Runtime(PhantomBackgroundSuite.ProductionAuthorityFixture source, Path game, LearningPolicy policy, Path stop, boolean on, int partySize,
			org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3 tactical, boolean rankDamageWithV3) throws Exception
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
			policy.tactical(rankDamageWithV3 ? tactical : org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3.OFF);
			backend = new L2jPhantomPartyBackend(profiles, materialization, progression);
			sourcePlanner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
			backend.installFarmSourcePolicy((farm, target) -> (evaluationTarget == null || target == evaluationTarget) && sourcePlanner.partyNativeTarget(farm, target.asNpc().getId(),
				new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId())));
			final Path rules = game.resolve("data/phantoms/party");
			final var roleCatalog = PhantomPartyRoleCatalog.load(rules.resolve("high-five-party-roles-v1.xml"));
			roles = new PhantomPartyRoleMatcher(roleCatalog);
			final var tactics = new PhantomPartyTactics(combat, backend, PhantomPartySupportPolicy.load(rules.resolve("high-five-party-support-v1.xml")), tactical);
			healTactics = new PhantomPartyTactics(combat, backend, PhantomPartySupportPolicy.load(rules.resolve("high-five-party-support-v1.xml")));
			coordinator = new PhantomPartyCoordinator(new PhantomPartyStore(profiles), goals, backend, roleCatalog,
				new PhantomPartyRouteCoordinator(navigation, combat), tactics, () -> source.topology().snapshot().canonicalHash(), System::nanoTime, 64);
			autonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, roleCatalog, System::nanoTime);
			progression.start();
			check(materialization.start() && navigation.start() && coordinator.start(), "NATIVE_OWNERS_START");
			combat.start();
		}

		/** Director-only admission; ordinary 001 entrypoints retain their original lifecycle. */
		List<Player> prepareHeal(List<String[]> rows) throws Exception
		{
			check(trainingRows.isEmpty() && materialization.list().isEmpty(), "V3_PREVIOUS_NOT_STORED");
			trainingRows = List.copyOf(rows);
			final var players = new java.util.ArrayList<Player>();
			for (var row : rows) { players.add(materialize(row)); trainingPlayers = List.copyOf(players); }
			final long leader = Long.parseLong(rows.getFirst()[2]);
			final var goal = goals.load(leader).orElseThrow().goal();
			check(coordinator.form(leader, goal.goalId(), goal.revision(), org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode.GENERAL_PVE,
				new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("party", "v3-heal-" + leader), List.of()) == PhantomPartyCoordinator.CommandOutcome.ACCEPTED, "V3_FORM");
			for (int index = 1; index < rows.size(); index++)
			{
				final long member = Long.parseLong(rows.get(index)[2]);
				check(coordinator.invite(leader, backend.currentMember(member).orElseThrow(), org.l2jmobius.gameserver.model.groups.PartyDistributionType.FINDERS_KEEPERS)
					== PhantomPartyCoordinator.CommandOutcome.ACCEPTED, "V3_INVITE_" + member);
				await(() -> { coordinator.onPulse(); return players.get(indexOf(rows, member)).getParty() == players.getFirst().getParty() && players.getFirst().getParty() != null; }, Duration.ofSeconds(15), "V3_PARTY_JOIN");
			}
			check(players.getFirst().getParty().getMemberCount() == rows.size() && players.stream().noneMatch(Player::isCastingNow), "V3_EXACT_PARTY");
			return List.copyOf(players);
		}
		private static int indexOf(List<String[]> rows, long profile) { for (int i = 0; i < rows.size(); i++) { if (Long.parseLong(rows.get(i)[2]) == profile) { return i; } } throw new IllegalArgumentException("Missing member"); }
		List<org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.TacticalDirective> inspectHeal()
		{
			final var roster = trainingRows.stream().map(row -> backend.currentMember(Long.parseLong(row[2])).orElseThrow()).toList();
			final var snapshots = new java.util.LinkedHashMap<org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot>();
			for (var member : roster) { snapshots.put(member, backend.memberSnapshot(member).orElseThrow()); }
			return healTactics.plan(roster.getFirst(), roster, snapshots);
		}
		void pulseHeal() { coordinator.onPulse(); }
		boolean startTemporalThreat(long profile, int targetObjectId, org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken token)
		{
			return combat.startSession(new PhantomCombatRequest(profile, targetObjectId, PhantomCombatMode.MELEE_PHYSICAL, false, false, 30000, token)).accepted();
		}
		List<org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability> temporalHealCapabilities(long profile, int targetObjectId)
		{ return backend.capabilities(backend.currentMember(profile).orElseThrow(), targetObjectId, true); }
		org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot temporalMemberSnapshot(long profile)
		{ return backend.memberSnapshot(backend.currentMember(profile).orElseThrow()).orElseThrow(); }
		java.util.Optional<PhantomCombatService.ExternalActionLease> dispatchTemporalHeal(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.TacticalDirective directive, String operationKey, long deadline)
		{ return healTactics.dispatch(directive, operationKey, deadline, () -> false); }
		boolean temporalThreatDrained(long profile) { combat.consumeTerminal(profile); return !combat.hasClaim(profile); }
		void finishHeal() throws Exception
		{
			await(() -> trainingPlayers.stream().noneMatch(p -> p.isCastingNow() || p.isCastingSimultaneouslyNow() || p.isAttackingNow() || p.getQueuedSkill() != null), Duration.ofSeconds(20), "V3_CAST_DRAIN");
			for (int index = trainingRows.size() - 1; index >= 0; index--)
			{
				final long profile = Long.parseLong(trainingRows.get(index)[2]);
				if (!trainingPlayers.get(index).isInParty()) { continue; }
				final var before = goals.load(profile).orElseThrow();
				final var goal = PhantomLearning003Native.partyGoal(profile, before.goal().goalId() + 100000, PhantomPartyCoordinator.LEAVE_GOAL, null);
				goals.replace(profile, before.rowVersion(), goal);
				final var claim = coordinator.claim(profile).orElseThrow().state();
				final var result = coordinator.leave(profile, goal.goalId(), goal.revision(), claim.groupGeneration());
				check(result == PhantomPartyCoordinator.CommandOutcome.ACCEPTED || result == PhantomPartyCoordinator.CommandOutcome.IDEMPOTENT, "V3_LEAVE_" + result);
			}
			await(() -> { coordinator.onPulse(); return trainingPlayers.stream().noneMatch(Player::isInParty) && trainingRows.stream().noneMatch(row -> combat.hasClaim(Long.parseLong(row[2]))); }, Duration.ofSeconds(20), "V3_LEASE_DRAIN");
			for (var row : trainingRows) { store(Long.parseLong(row[2])); }
			trainingRows = List.of(); trainingPlayers = List.of();
		}

		/** Director-only admission; ordinary 001 entrypoints retain their original lifecycle. */
		String prepareTraining(PhantomTrainingScenarios.Scene scene, List<String[]> roster, boolean grouped, int preparationSeconds, BooleanSupplier requested) throws Exception
		{
			check(trainingRows.isEmpty() && materialization.list().isEmpty(), "TRAIN_PREVIOUS_EPISODE_NOT_STORED");
			trainingAdmission = false;
			coordinator.installLogicalFarmAdmission(_ -> trainingAdmission);
			trainingScene = scene;
			trainingParty = grouped;
			trainingRows = grouped ? List.copyOf(roster) : List.<String[]>of(roster.stream().filter(row -> "SOLO".equals(row[1])).findFirst().orElseThrow());
			final var materialized = new java.util.ArrayList<Player>();
			// Keep partial preparation visible to settlement if the next actor refuses admission.
			for (var row : trainingRows)
			{
				final var player = materialize(row);
				materialized.add(player);
				trainingPlayers = List.copyOf(materialized);
				check(!player.isAlikeDead(), "TRAIN_RETIRED_DEAD_ACTOR_" + row[2]);
				returnToPartyAnchor(row, player);
				final var root = goals.load(Long.parseLong(row[2])).orElseThrow().goal();
				check(new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", scene.source()).equals(PhantomAutonomousFarmParty.farmSource(root)), "TRAIN_SOURCE_CHANGED");
			}
			System.out.println("TRAIN_PREPARATION_START grouped=" + grouped + " budgetSeconds=" + preparationSeconds + " facts=" + trainingFacts());
			boolean prepared = false;
			try
			{
				await(() ->
				{
					check(!requested.getAsBoolean(), "TRAIN_PREPARATION_REQUESTED_DRAIN");
					boolean recovered = true;
					for (int index = 0; index < trainingPlayers.size(); index++)
					{
						final var player = trainingPlayers.get(index);
						check(!preflightThreatened(player), "TRAIN_PREPARATION_NATIVE_THREAT");
						check("FIXTURE".equals(trainingRows.get(index)[0]) || player.getKnownSkill(1230) != null, "TRAIN_PREPARATION_LEARNED_PROMINENCE");
						// Every member begins native rest on the same shared preparation pulse.
						recovered &= !recover(player, true);
					}
					return recovered;
				}, Duration.ofSeconds(preparationSeconds), "TRAIN_NATURAL_RECOVERY_DEADLINE");
				prepared = true;
			}
			finally { System.out.println("TRAIN_PREPARATION_END ready=" + prepared + " facts=" + trainingFacts()); }
			if (grouped)
			{
				inspectParty(trainingRows, trainingPlayers);
				provePartyTarget(trainingRows, trainingPlayers);
			}
			return trainingFacts();
		}

		String runTrainingEpisode(TrainingCollector collector, int seconds, int evidenceLimit, BooleanSupplier requested) throws Exception
		{
			return runTrainingEpisode(collector, seconds, evidenceLimit, requested, null);
		}

		String runTrainingEpisode(TrainingCollector collector, int seconds, int evidenceLimit, BooleanSupplier requested, CooldownWitness cooldown) throws Exception
		{ return runTrainingEpisode(collector, seconds, evidenceLimit, requested, cooldown, () -> false); }
		private String runTrainingEpisode(TrainingCollector collector, int seconds, int evidenceLimit, BooleanSupplier requested, CooldownWitness cooldown, BooleanSupplier nativeTerminal) throws Exception
		{
			check(!trainingPlayers.isEmpty(), "TRAIN_NOT_PREPARED");
			check(cooldown == null || !trainingParty, "COOLDOWN_WITNESS_REQUIRES_SOLO");
			trainingAdmission = true;
			final long deadline = outcome006?outcomeDeadline:System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
			final long idleDeadline = System.nanoTime() + Duration.ofSeconds(Math.min(seconds, 45)).toNanos();
			boolean encountered = false;
			while (System.nanoTime() < deadline && !nativeTerminal.getAsBoolean() && (collector.size() < evidenceLimit || cooldown != null && !cooldown.passed()))
			{
				if (requested.getAsBoolean()) { return "REQUESTED_DRAIN"; }
				if (trainingPlayers.stream().anyMatch(Player::isAlikeDead)) { return "NATIVE_ACTOR_DIED"; }
				if (cooldown != null) { cooldown.observe(collector); if (cooldown.passed() && collector.size() >= evidenceLimit) { break; } }
				if (trainingParty)
				{
					coordinator.onPulse();
					// This is the same stock autonomous scheduler, including its five-second budget.
					autonomous.onPulse();
					encountered |= combat.snapshot().activeSessions() > 0;
					if(outcome006){outcomeEncounterAdmitted|=encountered;}
				}
				else
				{
					final var row = trainingRows.getFirst();
					final long profile = Long.parseLong(row[2]);
					final var player = trainingPlayers.getFirst();
					if (readyForSoloSession(combat, profile) && !retreat(profile, player) && !recover(player, on))
					{
						final var source = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", trainingScene.source());
						final var targets = on ? PhantomLearning001ActorFixture.coldSoloOpeningTargets(player) : PhantomLearning001ActorFixture.soloOpeningTargets(player);
						final var target = targets.stream()
							.filter(npc -> (evaluationTarget == null || npc == evaluationTarget) && npc.getId() == trainingScene.npc() && sourcePlanner.partyNativeTarget(source, npc.getId(), new PhantomTopologyPoint(npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId())))
							.findFirst().orElse(null);
						if (target != null)
						{
							final var request = new PhantomCombatRequest(profile, target.getObjectId(), PhantomCombatMode.RANGED_MAGIC, true, true,
								evaluationTarget != null ? Math.max(1, (deadline - System.nanoTime()) / 1_000_000L) : on ? PhantomLearning001ActorFixture.COLD_SOLO_TIMEOUT_MILLIS : 45_000,
								() -> false, LearningScope.SOLO, null, () -> false);
							final var accepted = combat.startSession(request);
							encountered |= accepted.accepted();
							if(outcome006){outcomeEncounterAdmitted|=encountered;}
						}
					}
				}
				if (!encountered && collector.size() == 0 && System.nanoTime() >= idleDeadline)
				{
					if (!trainingParty) { PhantomLearning001ActorFixture.inspectColdSoloOpening(trainingPlayers.getFirst()); }
					final var reason = trainingParty ? backend.currentMember(Long.parseLong(trainingRows.getFirst()[2])).map(backend::autonomousFarmWaitReason).orElse("NO_LEADER") : "NO_SAFE_SOLO_OPENING";
					return "NO_LEGAL_TARGET:" + reason;
				}
				Thread.sleep(cooldown == null ? Math.max(100, PhantomPlayersConfig.settings().schedulerPulseMillis()) : 50);
			}
			return collector.size() > 0 && (cooldown == null || cooldown.passed()) ? "NATIVE_EVIDENCE"
				: cooldown == null ? "NO_EXACT_NATIVE_EVIDENCE" : "NO_NATIVE_COOLDOWN_WITNESS:" + cooldown.description();
		}

		java.util.function.Supplier<org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3> runLearning004Encounter(String id, String nonce, int seconds,
			BooleanSupplier requested, java.util.function.Supplier<List<org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3>> receipts) throws Exception
		{return runLearning006Encounter(id,nonce,seconds,requested,receipts,_ -> { });}
		java.util.function.Supplier<org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3> runLearning006Encounter(String id,String nonce,int seconds,
			BooleanSupplier requested,java.util.function.Supplier<List<org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3>> receipts,java.util.function.LongConsumer cut) throws Exception
		{
			final long started = System.nanoTime();
			evaluationStarted = started; evaluationStopped = 0; evaluationTerminal = 0; evaluationDrainNanos = 0;
			if(outcome006){outcomeDeadline=Math.addExact(started,Duration.ofSeconds(seconds).toNanos());}
			cut.accept(started);
			final var before = new java.util.HashMap<Integer,org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Snapshot>();
			for (var player : trainingPlayers) { before.put(player.getObjectId(), PlayerNativeWork.observationEvidence(player).snapshot()); }
			final var kills = new java.util.LinkedHashMap<String,org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3.Kill>();
			final var deaths = new java.util.HashSet<Integer>();
			final boolean[] terminalClosed={false};
			final BooleanSupplier terminal = () ->
			{
				if(outcome006&&terminalClosed[0]){return true;}
				final var deathSnapshot=outcome006?outcomeDeaths.snapshot(outcomeDeadline,false):null;
				for (var player : trainingPlayers)
				{
					if (player.isAlikeDead()&&(!outcome006||deathSnapshot.firstDeath(player.getObjectId()))) { deaths.add(player.getObjectId()); }
					final var evidence = PlayerNativeWork.observationEvidence(player);
					if (evidence == null) { continue; }
					final var nativeReceipts = evidence.targetReceipts();
					final var target = nativeReceipts.killTarget();
					if (nativeReceipts.killSequence() <= before.get(player.getObjectId()).killSequence() || target == null) { continue; }
					if ((!outcome006||deathSnapshot.firstDeath(target.objectId()))&&World.getInstance().findObject(target.objectId()) instanceof org.l2jmobius.gameserver.model.actor.instance.Monster npc
						&& target.equals(npc.getNativeEvidenceTarget()) && npc.isAlikeDead())
					{
						kills.putIfAbsent(target.toString() + '/' + player.getObjectId(), new org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3.Kill(
							target.objectId(), npc.getId(), target.spawnGeneration(), target.instanceId(), player.getObjectId(), nativeReceipts.killSequence()));
					}
				}
				final boolean done = !kills.isEmpty() || !deaths.isEmpty();
				if (done && evaluationTerminal == 0) { evaluationTerminal = outcome006?deathSnapshot.first():System.nanoTime(); }
				return done;
			};
			evaluationTerminalProbe = terminal;
			final var collector = new TrainingCollector();
			collector.begin(id);
			final String stopped;
			try { stopped = runTrainingEpisode(collector, seconds, Integer.MAX_VALUE, requested, null, terminal); }
			finally { collector.end(); }
			evaluationStopped = System.nanoTime();
			if(outcome006){outcomeFrozen=outcomeDeaths.snapshot(Math.min(outcomeDeadline,evaluationStopped),true);kills.clear();deaths.clear();evaluationTerminal=0;}
			terminal.getAsBoolean();
			if(outcome006){terminalClosed[0]=true;evaluationTerminal=outcomeFrozen.first()>0?outcomeFrozen.first():Math.min(outcomeDeadline,evaluationStopped);}
			final var frozenActors=outcome006?outcomeActors(before):null;
			final boolean pendingTerminal=outcome006&&(outcomeFrozen.first()>0&&kills.isEmpty()&&deaths.isEmpty()||stopped.equals("NATIVE_ACTOR_DIED")&&deaths.isEmpty()
				||trainingPlayers.stream().anyMatch(p->p.isAlikeDead()&&!outcomeFrozen.times().containsKey(p.getObjectId()))
				||evaluationTarget!=null&&evaluationTarget.isAlikeDead()&&!outcomeFrozen.firstDeath(evaluationTarget.getObjectId())&&outcomeFrozen.first()==0);
			return () ->
			{
			terminal.getAsBoolean();
			final var actors=outcome006?frozenActors:outcomeActors(before);
			final var observed = receipts.get();
			final String reason = outcome006?(pendingTerminal?"NATIVE_TERMINAL_CREDIT_PENDING":outcomeReason(stopped,outcomeEncounterAdmitted,!kills.isEmpty(),!deaths.isEmpty())):!deaths.isEmpty() ? "NATIVE_ACTOR_DIED" : !kills.isEmpty() ? "NATIVE_KILL" : stopped.equals("REQUESTED_DRAIN") ? stopped : "TIMEOUT";
			return new org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3(id, nonce, trainingParty ? "PARTY" : "SOLO", started, System.nanoTime(), reason,
				actors, List.copyOf(kills.values()), observed.stream().map(org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3::eventKey).toList(),
				observed.stream().filter(row -> row.attempt().selected().family() == org.l2jmobius.gameserver.phantoms.learning.LegalActionV3.Family.DAMAGE).mapToDouble(org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3::actualHp).sum(),
				observed.stream().filter(row -> row.attempt().selected().family() == org.l2jmobius.gameserver.phantoms.learning.LegalActionV3.Family.HEAL).mapToDouble(org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3::actualHp).sum(),
				observed.stream().mapToDouble(org.l2jmobius.gameserver.phantoms.learning.TacticalReceiptV3::actualMp).sum(), deaths.size());
			};
		}
		static String outcomeReason(String stopped,boolean admitted,boolean killed,boolean died)
		{
			if(stopped.equals("REQUESTED_DRAIN")||stopped.startsWith("NO_LEGAL_TARGET")){return stopped;}
			if(died){return "NATIVE_ACTOR_DIED";}if(killed){return "NATIVE_KILL";}
			return admitted?"TIMEOUT":"NO_LEGAL_TARGET:NOT_ADMITTED";
		}
		private List<org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3.Actor> outcomeActors(java.util.Map<Integer,PlayerNativeEvidence.Snapshot> before)
		{
			final var actors=new java.util.ArrayList<org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3.Actor>();
			for(int index=0;index<trainingPlayers.size();index++)
			{
				final var player=trainingPlayers.get(index);final var previous=before.get(player.getObjectId());
				final var evidence=PlayerNativeWork.observationEvidence(player);final var current=evidence==null?null:evidence.snapshot();
				final boolean known=current!=null&&current.epoch()==previous.epoch()&&!current.overflow()&&!previous.overflow()&&current.firstUnprovenReason()==PlayerNativeEvidence.UnprovenReason.NONE;
				final boolean dead=outcome006?outcomeFrozen.firstDeath(player.getObjectId()):player.isAlikeDead();
				actors.add(new org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3.Actor(Long.parseLong(trainingRows.get(index)[2]),player.getObjectId(),previous.epoch(),player.getActiveClass(),player.getLevel(),player.getCurrentHp(),player.getCurrentMp(),dead,known?current.expGained()-previous.expGained():null,known?current.spGained()-previous.spGained():null));
			}
			return List.copyOf(actors);
		}

		CooldownWitness cooldownWitness()
		{
			check(!trainingParty && trainingPlayers.size() == 1, "COOLDOWN_WITNESS_SINGLE_NATIVE_ACTOR");
			return new CooldownWitness(trainingPlayers.getFirst());
		}

		/** Diagnostic only: observes natural native tasks/counters; never creates an Attempt or changes reuse. */
		final class CooldownWitness
		{
			private final Player actor;
			private final PlayerNativeWork.Owner owner;
			private final org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence evidence;
			private final org.l2jmobius.gameserver.model.skill.Skill first, alternative;
			private org.l2jmobius.gameserver.model.actor.instance.Monster target;
			private org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target spawn;
			private boolean sawFirstTask, armed, sawAlternativeTask, done;
			private long remainingBefore, remainingAtAlternative, damageBefore, rejectedBefore;
			private double hpBefore, mpBefore;
			private int recordedBefore;
			private String result = "waiting_for_natural1339";

			private CooldownWitness(Player player)
			{
				actor = player; owner = player.getNativeWorkOwner(); evidence = PlayerNativeWork.observationEvidence(player);
				first = player.getKnownSkill(1339); alternative = player.getKnownSkill(1230);
				check(owner != null && evidence != null && first != null && alternative != null && first != alternative, "COOLDOWN_NATIVE_SKILLS_OWNER");
			}

			boolean passed() { return done; }
			String description() { return result; }

			private void observe(TrainingCollector collector)
			{
				if (done) { return; }
				check(actor.getNativeWorkOwner() == owner && owner.isCurrent() && PlayerNativeWork.observationEvidence(actor) == evidence
					&& actor.getKnownSkill(1339) == first && actor.getKnownSkill(1230) == alternative, "COOLDOWN_NATIVE_AUTHORITY_CHANGED");
				final var observed = actor.observeNativeMagicUse(1);
				final var primary = observed.primary();
				if (observed.isCurrent() && primary != null && primary.isCurrent() && primary.targets().size() == 1
					&& primary.targets().getFirst() instanceof org.l2jmobius.gameserver.model.actor.instance.Monster actual)
				{
					if (!armed && primary.skill() == first)
					{
						sawFirstTask = true; target = actual; spawn = actual.getNativeEvidenceTarget();
					}
					if (armed && primary.skill() == alternative)
					{
						if (!sawAlternativeTask)
						{
							remainingAtAlternative = actor.getSkillRemainingReuseTime(first.getReuseHashCode());
							check(actor.isSkillDisabled(first) && remainingAtAlternative > 0, "COOLDOWN_ALTERNATIVE_DID_NOT_SKIP_LIVE_REUSE");
							// A lawful next target may differ after the first victim dies. Observe this exact native task's recipient.
							target = actual; spawn = actual.getNativeEvidenceTarget();
							hpBefore = actual.getCurrentHp(); mpBefore = actor.getCurrentMp(); damageBefore = evidence.snapshot().damageSequence();
							sawAlternativeTask = true; result = "waiting_for1230_native_effect_and_drain";
						}
					}
				}
				if (!armed && sawFirstTask)
				{
					final boolean firstRecorded = collector.snapshot().stream().anyMatch(row ->
					{
						final var skill = java.util.regex.Pattern.compile("\"skill\"\\s*:\\s*([0-9]+)").matcher(row.json());
						return skill.find() && "1339".equals(skill.group(1));
					});
					if (firstRecorded && !actor.isSkillDisabled(alternative) && actor.checkDoCastConditions(alternative) && alternative.checkCondition(actor, target, false))
					{
						remainingBefore = actor.getSkillRemainingReuseTime(first.getReuseHashCode());
						check(actor.isSkillDisabled(first) && remainingBefore > 0, "COOLDOWN1339_NOT_LIVE_AFTER_NATIVE_RECEIPT");
						check(World.getInstance().findObject(target.getObjectId()) == target && spawn.equals(target.getNativeEvidenceTarget()), "COOLDOWN_FIRST_TARGET_STALE");
						hpBefore = target.getCurrentHp(); mpBefore = actor.getCurrentMp(); damageBefore = evidence.snapshot().damageSequence();
						rejectedBefore = combat.metrics().skillCastsRejected(); recordedBefore = collector.size(); armed = true;
						result = "waiting_for_ready1230_native_task";
						System.out.println("V3_NATIVE_COOLDOWN_ARM first=1339 groupHash=" + first.getReuseHashCode() + " remainingBeforeMillis=" + remainingBefore
							+ " disabled=true alternative=1230 alternativeReady=true actor=" + actor.getObjectId() + " target=" + target.getObjectId() + " epoch=" + owner.epoch());
					}
				}
				if (armed && sawAlternativeTask && !actor.isCastingNow() && actor.getLastSkillCast() == alternative)
				{
					check(World.getInstance().findObject(target.getObjectId()) == target && spawn.equals(target.getNativeEvidenceTarget()), "COOLDOWN_NATIVE_TARGET_STALE");
					final long writes = evidence.snapshot().damageSequence() - damageBefore;
					final double hpDelta = hpBefore - target.getCurrentHp(), mpDelta = mpBefore - actor.getCurrentMp();
					final long rejected = combat.metrics().skillCastsRejected() - rejectedBefore;
					check(writes > 0 && hpDelta > 0 && mpDelta > 0, "COOLDOWN1230_HAS_NO_NATIVE_HP_MP_EFFECT");
					check(rejected == 0, "COOLDOWN_REJECTED_CAST_RETRY");
					check(collector.size() == recordedBefore, "COOLDOWN_SINGLETON_ALTERNATIVE_GOT_TRAINING_CREDIT");
					done = true;
					result = "V3_NATIVE_COOLDOWN_PASS first=1339 groupHash=" + first.getReuseHashCode() + " remainingBeforeMillis=" + remainingBefore
						+ " remainingAtAlternativeMillis=" + remainingAtAlternative + " alternative=1230 actual=1230:" + alternative.getLevel()
						+ " nativeOwnHpWrites=" + writes + " observedNetHpDelta=" + hpDelta + " observedNetMpDebit=" + mpDelta
						+ " nativeMpCost=" + (actor.getStat().getMpInitialConsume(alternative) + actor.getStat().getMpConsume(alternative))
						+ " castRejectedDelta=" + rejected + " actor=" + actor.getObjectId() + " target=" + target.getObjectId()
						+ " diagnosticOnly=true alternativeTrained=false castDrained=true";
					System.out.println(result);
				}
			}
		}

		String settleTraining() throws Exception
		{
			drainTraining();
			return storeTraining();
		}
		record Learning004Settled(org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3 episode, String facts) { }
		Learning004Settled settleLearning004(java.util.function.Supplier<org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3> finalSnapshot) throws Exception
		{
			final long drainStarted = System.nanoTime();
			final var episode = org.l2jmobius.gameserver.phantoms.learning.TacticalEpisodeV3.afterNativeDrain(this::drainTraining, finalSnapshot);
			evaluationDrainNanos = System.nanoTime() - drainStarted;
			return new Learning004Settled(episode, storeTraining());
		}
		private void drainTraining() throws Exception
		{
			trainingAdmission = false;
			if (trainingRows.isEmpty()) { return; }
			final var rows = trainingRows;
			final var players = trainingPlayers;
			await(() ->
			{
				if (evaluationTarget != null) { evaluationTerminalProbe.getAsBoolean(); }
				coordinator.onPulse();
				boolean drained = true;
				for (var player : players)
				{
					final var nativeParty = player.getParty();
					if (nativeParty != null)
					{
						final var leader = nativeParty.getLeader();
						final var leaderRow = rows.stream().filter(row -> Integer.parseInt(row[3]) == leader.getObjectId()).findFirst().orElseThrow();
						drained &= coordinator.drainAutonomousFarm(Long.parseLong(leaderRow[2]));
					}
				}
				for (var row : rows)
				{
					long profile = Long.parseLong(row[2]);
					if (!coordinator.committed(profile)) { combat.consumeTerminal(profile); }
					drained &= !combat.hasClaim(profile);
				}
				return drained && players.stream().noneMatch(player -> player.isInParty() || player.isAttackingNow() || player.isCastingNow()
					|| player.isCastingSimultaneouslyNow() || player.getQueuedSkill() != null || player.isMoving());
			}, Duration.ofSeconds(90), "TRAIN_CANONICAL_DRAIN");
		}
		private String storeTraining() throws Exception
		{
			if (trainingRows.isEmpty()) { return "{\"actors\":[],\"nativeRewards\":\"UNKNOWN\"}"; }
			final var rows = trainingRows;
			final var players = trainingPlayers;
			final String facts = trainingFacts();
			for (var row : rows) { store(Long.parseLong(row[2])); }
			check(players.stream().allMatch(player -> World.getInstance().getPlayer(player.getObjectId()) == null), "TRAIN_WORLD_NOT_EMPTY");
			trainingRows = List.of(); trainingPlayers = List.of(); trainingScene = null;
			return facts;
		}

		List<Player> evaluationPlayers() { return List.copyOf(trainingPlayers); }
		String outcomeBeforeFacts(){return trainingFacts();}
		/** TEST input only, before caller's durable CUT. No action is forced and no native target is replaced after CUT. */
		void prepareEvaluation(Monster target, int variant) throws Exception
		{
			check(evaluationTarget == null && !trainingPlayers.isEmpty() && !target.isAlikeDead() && !target.isChampion() && !target.isRaid()
				&& target.getCurrentHp() == target.getMaxHp() && !target.isAggressive(), "EVAL005_FRESH_STOCK_TARGET prior=" + (evaluationTarget != null)
				+ " players=" + trainingPlayers.size() + " dead=" + target.isAlikeDead() + " champion=" + target.isChampion() + " raid=" + target.isRaid()
				+ " hp=" + target.getCurrentHp() + "/" + target.getMaxHp() + " aggressive=" + target.isAggressive());
			evaluationTarget = target;
			if (!trainingParty) { return; }
			final var profiles = trainingRows.stream().map(row -> Long.parseLong(row[2])).toList();
			await(() ->
			{
				check(!trainingAdmission && trainingPlayers.stream().noneMatch(PhantomLearning001ShadowNative::preflightThreatened), "EVAL005_PRE_CUT_THREAT");
				autonomous.onPulse(); coordinator.onPulse();
				return trainingPlayers.getFirst().getParty() != null && trainingPlayers.getFirst().getParty().getMembers().containsAll(trainingPlayers)
					&& profiles.stream().allMatch(coordinator::committed) && trainingPlayers.stream().noneMatch(p -> p.isCastingNow() || p.isAttackingNow());
			}, Duration.ofSeconds(60), "EVAL005_PARTY_PRE_CUT_FORMATION");
			// Declared pre-CUT positions reuse003's45/90 distance variation. No post-CUT relocation.
			final var healer = trainingPlayers.get(3);
			for (int index : new int[] {0, 2})
			{
				final int offset = index == 0 ? 45 : 90;
				check(org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().canMoveToTarget(healer.getX(), healer.getY(), healer.getZ(), healer.getX() + offset, healer.getY(), healer.getZ(), healer.getInstanceId()), "EVAL005_PRE_CUT_POSITION_GEO");
				final var player = trainingPlayers.get(index);
				player.teleToLocation(healer.getX() + offset, healer.getY(), healer.getZ(), 0, false);
				if (player.hasHeadlessOutboundSession() && player.isTeleporting()) { player.onTeleported(); }
				check(!player.isTeleporting() && !player.isMoving(), "EVAL005_PRE_CUT_POSITION_SETTLED");
			}
			// Declared matched initial injuries, following the003 native fixture. Nothing writes HP after this method.
			final double lower = .30 + variant % 4 * .025, higher = .48 + variant % 3 * .015;
			trainingPlayers.get(0).setCurrentHp(trainingPlayers.get(0).getMaxHp() * (variant % 2 == 0 ? lower : higher));
			trainingPlayers.get(2).setCurrentHp(trainingPlayers.get(2).getMaxHp() * (variant % 2 == 0 ? higher : lower));
			final var offers = inspectHeal().stream().filter(d -> d.kind() == DirectiveKind.HEAL_MEMBER).toList();
			check(offers.size() >= 2, "EVAL005_TWO_STOCK_HEAL_ALTERNATIVES");
		}

		private String trainingFacts()
		{
			final var facts = new java.util.ArrayList<String>();
			for (int index = 0; index < trainingPlayers.size(); index++)
			{
				final var player = trainingPlayers.get(index);
				final var owner = player.getNativeWorkOwner();
				facts.add("{\"profile\":" + trainingRows.get(index)[2] + ",\"actor\":" + player.getObjectId() + ",\"epoch\":" + (owner == null ? 0 : owner.epoch())
					+ ",\"class\":" + player.getActiveClass() + ",\"level\":" + player.getLevel() + ",\"hp\":" + player.getCurrentHp() + ",\"maxHp\":" + player.getMaxHp()
					+ ",\"mp\":" + player.getCurrentMp() + ",\"maxMp\":" + player.getMaxMp()
					+ ",\"exp\":" + player.getExp() + ",\"sp\":" + player.getSp() + ",\"adena\":" + player.getAdena() + ",\"x\":" + player.getX() + ",\"y\":" + player.getY() + ",\"z\":" + player.getZ() + ",\"dead\":" + player.isAlikeDead() + "}");
			}
			return "{\"schema\":1,\"actors\":[" + String.join(",", facts) + "],\"rewardAttribution\":\"UNKNOWN\",\"spoil\":\"UNKNOWN\"}";
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
			final String group = key(salt + ":" + profile);
			if (!on && samples("SOLO", group, group).enough(minimum, 1))
			{
				System.out.println("LEARNING001_SOLO_SQL_RESUME split=" + row[0] + " profile=" + profile);
				return;
			}
			final Player player = materialize(row);
			if (on) { returnToPartyAnchor(row, player); }
			final long deadline = System.nanoTime() + Duration.ofMillis(PhantomLearning001ActorFixture.sampleWindow(minimum)).toNanos();
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
					if (retreat(profile, player) || recover(player, on))
					{
						Thread.sleep(600);
						continue;
					}
					final var targets = on ? PhantomLearning001ActorFixture.coldSoloOpeningTargets(player) : PhantomLearning001ActorFixture.soloOpeningTargets(player);
					final var target = targets.isEmpty() ? null : targets.getFirst();
					if (target != null)
					{
						final long timeout = on ? PhantomLearning001ActorFixture.COLD_SOLO_TIMEOUT_MILLIS : 45_000;
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
			// The ON witness ends with canonical drain/store after its native session. SHADOW keeps farming recovery.
			await(() -> readyForSoloSession(combat, profile) && (on || !retreat(profile, player)), Duration.ofSeconds(90), "SOLO_RELEASE_" + profile);
			check(!player.isAlikeDead(), "SOLO_ALIVE_AT_STORE_" + profile);
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

		private static boolean recover(Player player, boolean cold)
		{
			if (player.isMoving() || player.isAttackingNow() || player.isCastingNow() || player.isCastingSimultaneouslyNow() || player.getQueuedSkill() != null) { return true; }
			final var skill = player.getKnownSkill(1230);
			final double mp = skill == null ? 0 : (player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill)) * 3;
			if ((cold && player.getCurrentHp() < player.getMaxHp() * .9)
				|| !PhantomCombatPolicy.pveResourcesReady(player.getCurrentHp(), player.getMaxHp(), player.getCurrentMp(), mp, player.isSitting()))
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

		private void returnToPartyAnchor(String[] row, Player player) throws Exception
		{
			final var anchor = new PhantomNavigationPoint(Integer.parseInt(row[8]), Integer.parseInt(row[9]), Integer.parseInt(row[10]), 0);
			// This keeps the stock 1200-unit compatibility check for any two members near the same anchor.
			walkFixture(Long.parseLong(row[2]), player, anchor, 550);
		}

		private void resurrect(String[] row, Player learner, String[] healRow, Player healer) throws Exception
		{
			final long profile = Long.parseLong(row[2]);
			final long healerProfile = Long.parseLong(healRow[2]);
			final var skill = healer.getKnownSkill(1016);
			check(skill != null && "combat.resurrection".equals(PhantomSupportEffectAuthority.nativeCapability(skill))
				&& !learner.isReviveRequested(), "RECOVERY_STOCK_RESURRECTION");
			walkFixture(healerProfile, healer, PhantomNativeTravelActions.point(learner), Math.min(250, healer.getMagicalAttackRange(skill)));
			final var action = new PhantomPartySupportAction("combat.resurrection", PhantomSupportEffectAuthority.nativeVariant(skill),
				PhantomSupportEffectAuthority.nativeScope(skill), learner.getObjectId(), new SelectedSkill(skill.getId(), skill.getLevel()),
				PhantomPartySupportAction.Audience.EXACT_REQUESTER, 0, true);
			try (var lease = combat.acquireExternalAction(new ExternalActionRequest(healerProfile, ExternalActionKind.PARTY_SUPPORT,
				"learning001.fixture.audit.resurrection", System.nanoTime() + Duration.ofSeconds(30).toNanos(), () -> Files.exists(stop))).lease())
			{
				check(lease != null && lease.castSupport(action) == ActionOutcome.ISSUED, "RECOVERY_RESURRECTION_DISPATCH");
				await(() -> learner.isReviveRequested() && !learner.isRevivingPet(), Duration.ofSeconds(20), "RECOVERY_RESURRECTION_REQUEST");
				try (var answer = materialization.tryAcquireAction(profile).orElseThrow(); var execution = answer.enterExecutionScope())
				{
					check(answer.player() == learner, "RECOVERY_EXACT_RECIPIENT");
					PlayerNativeWork.run(learner, "Learning001ReviveAnswer", () -> learner.reviveAnswer(1));
				}
				check(!learner.isAlikeDead() && learner.getCurrentHp() > 0, "RECOVERY_RESURRECTION_EFFECT");
				lease.complete();
			}
			System.out.println("LEARNING001_RECOVERY_RESURRECTED profile=" + profile + " healer=" + healerProfile + " skill=" + skill.getId()
				+ " hp=" + learner.getCurrentHp() + " mp=" + learner.getCurrentMp());
			returnToPartyAnchor(healRow, healer);
		}

		private void walkFixture(long profile, Player player, PhantomNavigationPoint anchor, int radius) throws Exception
		{
			final var origin = PhantomNativeTravelActions.point(player);
			if (origin.distanceTo(anchor) <= radius) { return; }
			PhantomLearning001ActorFixture.requireSafePartySpawn(anchor.x(), anchor.y(), anchor.z());
			check(!preflightThreatened(player), "COLD_PARTY_WALK_THREAT_" + profile);
			final long now = System.nanoTime();
			final long deadline = now + Duration.ofSeconds(60).toNanos();
			final var submission = navigation.submitWalkCorridor(new PhantomNavigationRequest(profile, origin, anchor, now, deadline, 3000));
			check(submission.status() != SubmissionStatus.REJECTED, "COLD_PARTY_WALK_SUBMIT_" + profile + " " + submission.immediateResult());
			await(() -> navigation.find(submission.requestId()).map(result -> result.state() == RequestState.TERMINAL).orElse(false),
				Duration.ofSeconds(15), "COLD_PARTY_WALK_PLAN_" + profile);
			final var result = navigation.consume(submission.requestId()).orElseThrow();
			check(result.status() == Status.DIRECT_VALIDATED || result.status() == Status.PATH_FOUND, "COLD_PARTY_WALK_ROUTE_" + profile + " " + result.proof().diagnostic());
			try (var lease = combat.acquireExternalAction(new ExternalActionRequest(profile, ExternalActionKind.PARTY_ROUTE,
				"learning001.fixture.party.rendezvous", deadline, () -> Files.exists(stop))).lease())
			{
				check(lease != null, "COLD_PARTY_WALK_LEASE_" + profile);
				for (var point : result.route().waypoints())
				{
					if (PhantomNativeTravelActions.point(player).distanceTo(point) <= 50) { continue; }
					check(!preflightThreatened(player) && lease.partyWalkSafe(point.x(), point.y(), point.z(), point.instanceId()), "COLD_PARTY_WALK_SAFE_" + profile);
					final var outcome = lease.moveTo(point.x(), point.y(), point.z(), point.instanceId());
					check(outcome == ActionOutcome.ISSUED || outcome == ActionOutcome.ALREADY_OWNED, "COLD_PARTY_WALK_DISPATCH_" + profile + " " + outcome);
					await(() -> !player.isMoving() && PhantomNativeTravelActions.point(player).distanceTo(point) <= 50,
						Duration.ofSeconds(12), "COLD_PARTY_WALK_ARRIVE_" + profile);
				}
				lease.complete();
			}
			System.out.println("LEARNING001_COLD_PARTY_WALK profile=" + profile + " from=" + origin + " arrived=" + PhantomNativeTravelActions.point(player));
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
			if (on)
			{
				for (int index = 0; index < roster.size(); index++) { returnToPartyAnchor(roster.get(index), players.get(index)); }
			}
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
				await(() ->
				{
					coordinator.onPulse();
					return coordinator.drainAutonomousFarm(profile);
				}, Duration.ofSeconds(30), "PARTY_CANONICAL_RELEASE_" + profile);
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

	private static String[] soloActor(List<String[]> actors, String split)
	{
		return actors.stream().filter(row -> split.equals(row[0]) && "SOLO".equals(row[1])).findFirst().orElseThrow();
	}

	private record Samples(long count, int skills, int actors, int pairedActors)
	{
		boolean enough(int minimum, int minimumActors) { return count >= minimum && skills == 2 && pairedActors >= minimumActors; }
	}

	private static Samples samples(String scope, String first, String last) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement(
			"SELECT actor_group,skill_id,features FROM phantom_ai_training_examples WHERE origin='NATIVE' AND evidence_kind='EXACT_CAST_HP' "
			+ "AND policy_version=0 AND damage>0 AND schema_fingerprint=? AND scope=? AND actor_group IN (?,?)"))
		{
			statement.setString(1, LearningFeatures.SCHEMA);
			statement.setString(2, scope);
			statement.setString(3, first);
			statement.setString(4, last);
			try (var rows = statement.executeQuery())
			{
				long count = 0;
				final var skills = new java.util.HashSet<Integer>();
				final var skillsByActor = new java.util.HashMap<String, java.util.Map<String, java.util.Set<Integer>>>();
				while (rows.next())
				{
					final String actor = rows.getString(1);
					final int skill = rows.getInt(2);
					final String context = Math.floorMod(actor.hashCode(), 5) == 0 ? PhantomLearning001Offline.contextKey(rows.getBytes(3)) : "";
					skills.add(skill);
					skillsByActor.computeIfAbsent(actor, _ -> new java.util.HashMap<>()).computeIfAbsent(context, _ -> new java.util.HashSet<>()).add(skill);
					count++;
				}
				final long pairs = skillsByActor.values().stream().filter(byContext -> byContext.values().stream().anyMatch(known -> known.size() == 2)).count();
				return new Samples(count, skills.size(), skillsByActor.size(), (int) pairs);
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

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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.GameServer;
import org.l2jmobius.gameserver.Shutdown;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.actions.PhantomEngagementPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.NeuralPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
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
	private static final String JAR_SHA = "eec58870528bd2b77de154472892154891bea9780d2f2e22d8ca620c12f097e0";
	private static final String URL = "jdbc:mysql://127.0.0.1:33317/phantom_learning001_native";
	private static final String OWNER_PATTERN = "LEARNING001:[0-9a-f]{32}";

	public static void main(String[] args) throws Exception
	{
		check(args.length == 1 && Boolean.getBoolean("phantom.learning.train"), "EXPLICIT_OPT_IN");
		final Path lane = Path.of(args[0]).toRealPath().getParent().resolve(".phantom-local/learning001/native").toRealPath();
		final Path game = lane.resolve("game");
		check(Path.of(".").toRealPath().equals(game) && Files.readString(lane.resolve("owner.txt")).matches(OWNER_PATTERN), "OWNED_RUNTIME");
		final Path jar = game.resolve("libs/GameServer.jar");
		check(JAR_SHA.equals(hashFile(jar)), "JAR_SHA");
		for (Class<?> type : List.of(GameServer.class, PhantomCombatService.class, LearningPolicy.class))
		{
			check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar), "JAR_CLASS_ORIGIN");
		}
		final Path db = game.resolve("config/Database.ini");
		check(Files.mismatch(db, lane.resolve("Database.ini")) == -1, "OWNED_DATABASE");
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(db)) { settings.load(reader); }
		check(URL.equals(settings.getProperty("URL")) && "learning001".equals(settings.getProperty("Login")), "OWNED_ENDPOINT");
		try (var connection = DriverManager.getConnection(URL, "learning001", settings.getProperty("Password")))
		{
			PhantomLearning001FixtureSql.requireActorIdentity(connection, lane);
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM phantom_ai_training_examples), (SELECT COUNT(*) FROM phantom_ai_model_versions)"))
			{
				check(rows.next() && rows.getInt(1) == 0 && rows.getInt(2) == 0, "CLEAN_NATIVE_LEARNING_TABLES");
			}
		}
		final var actors = PhantomLearning001ActorFixture.readActors(lane.resolve("actors.tsv"));
		check(Files.readAllLines(lane.resolve("rosters.tsv")).size() == actors.size() + 1
			&& Files.readAllLines(lane.resolve("capacity.tsv")).size() == actors.size() + 1, "ROSTER_AND_CAPACITY_PREFLIGHT");
		final Path config = game.resolve("config/Custom/PhantomPlayers.ini");
		final String off = Files.readString(config);
		check(off.contains("EnablePhantomSystem = False") && off.contains("LearningMode = OFF")
			&& off.contains("LearningModelVersion = 0") && off.contains("LearningEvidenceEnabled = False"), "MASTER_OFF_AT_BOOT");
		final String shadow = off.replace("EnablePhantomSystem = False", "EnablePhantomSystem = True")
			.replace("LearningMode = OFF", "LearningMode = SHADOW").replace("LearningEvidenceEnabled = False", "LearningEvidenceEnabled = True");
		Files.writeString(lane.resolve("cut.properties"), "phase=SHADOW\npid=" + ProcessHandle.current().pid() + "\njarSha256=" + JAR_SHA + "\n", StandardOpenOption.CREATE_NEW);
		boolean booted = false;
		try
		{
			GameServer.main(new String[0]);
			booted = true;
			check(!PhantomPlayersConfig.isEnabled() && !PhantomSystem.hasConfiguredInstance(), "GLOBAL_OWNER_INACTIVE");
			Files.writeString(config, shadow);
			PhantomPlayersConfig.load();
			final var policy = LearningPolicy.configured(game);
			try
			{
				check(policy.collecting() && policy.active(LearningScope.SOLO) && policy.active(LearningScope.PARTY), "CONFIGURED_SHADOW");
				try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start(); var runtime = new Runtime(source, game, policy))
				{
					final String salt = key("learning001/actor/phantom_learning001_native/" + settings.getProperty("Password"));
					for (String split : List.of("TRAIN", "TUNING", "AUDIT"))
					{
						final var roster = actors.stream().filter(row -> split.equals(row[0])).toList();
						final var solo = roster.stream().filter(row -> "SOLO".equals(row[1])).findFirst().orElseThrow();
						final int minimum = "TRAIN".equals(split) ? 8 : 4;
						runtime.solo(solo, salt, minimum);
						runtime.party(roster, salt, minimum);
					}
				}
				Files.writeString(lane.resolve("shadow.complete"), "nativeSqlMinimum=32\npolicy=" + policy.counters() + "\n", StandardOpenOption.CREATE_NEW);
			}
			finally { await(policy::stop, Duration.ofSeconds(15), "WRITER_STOP"); }
		}
		catch (Exception failure)
		{
			Files.writeString(lane.resolve("shadow.failed"), failure.getClass().getSimpleName() + ": " + failure.getMessage(), StandardOpenOption.CREATE_NEW);
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

	private static final class Runtime implements AutoCloseable
	{
		private final PhantomMaterializationService materialization;
		private final PhantomProgressionService progression;
		private final PhantomNavigationService navigation;
		private final PhantomCombatService combat;
		private final PhantomPartyCoordinator coordinator;
		private final PhantomAutonomousFarmParty autonomous;
		private final PhantomGoalStateStore goals;
		private final LearningPolicy policy;

		Runtime(PhantomBackgroundSuite.ProductionAuthorityFixture source, Path game, LearningPolicy policy) throws Exception
		{
			this.policy = policy;
			final var profiles = PhantomProfileRepository.open();
			goals = new PhantomGoalStateStore(profiles);
			final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 4);
			progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, game, source::knowledge), PhantomProgressionPolicy.productionDefaults());
			navigation = new PhantomNavigationService(metrics);
			combat = new PhantomCombatService(new L2jCombatBackend(materialization, source::knowledge, () -> progression.findCatalog().orElse(null)),
				PhantomCombatCapabilityResolver.fromProgression(() -> progression.findCatalog().orElse(null)), PhantomCombatPolicy.productionDefaults(64));
			combat.installLearning(policy);
			final var backend = new L2jPhantomPartyBackend(profiles, materialization, progression);
			final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
			backend.installFarmSourcePolicy((farm, target) -> planner.partyNativeTarget(farm, target.asNpc().getId(),
				new PhantomTopologyPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId())));
			final Path rules = game.resolve("data/phantoms/party");
			final var roles = PhantomPartyRoleCatalog.load(rules.resolve("high-five-party-roles-v1.xml"));
			final var tactics = new PhantomPartyTactics(combat, backend, PhantomPartySupportPolicy.load(rules.resolve("high-five-party-support-v1.xml")));
			coordinator = new PhantomPartyCoordinator(new PhantomPartyStore(profiles), goals, backend, roles,
				new PhantomPartyRouteCoordinator(navigation, combat), tactics, () -> source.topology().snapshot().canonicalHash(), System::nanoTime, 64);
			autonomous = new PhantomAutonomousFarmParty(coordinator, backend, goals, roles, System::nanoTime);
			progression.start();
			check(materialization.start() && navigation.start() && coordinator.start(), "NATIVE_OWNERS_START");
			combat.start();
		}

		private Player materialize(String[] row)
		{
			final long profile = Long.parseLong(row[2]);
			check(materialization.materialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "MATERIALIZE_" + profile);
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
			for (Samples samples = samples("SOLO", group, group); !samples.enough(minimum); samples = samples("SOLO", group, group))
			{
				if (samples.count() > highWater) { highWater = samples.count(); progressed = System.nanoTime(); }
				check(System.nanoTime() - progressed < Duration.ofMinutes(3).toNanos(), "SOLO_SQL_STALLED_" + row[0] + " " + samples + " " + policy.counters());
				check(System.nanoTime() < deadline && !player.isAlikeDead(), "SOLO_SAMPLE_DEADLINE_" + row[0] + " " + policy.counters());
				if (combat.find(profile).filter(snapshot -> snapshot.result() == null).isEmpty())
				{
					final var geo = GeoEngine.getInstance();
					final var target = World.getInstance().getVisibleObjectsInRange(player, Monster.class, 800).stream()
						.filter(npc -> npc.isSpawned() && !npc.isAggressive() && !npc.isAlikeDead() && npc.isTargetable() && !npc.isInvul()
							&& npc.getInstanceId() == player.getInstanceId() && player.calculateDistance2D(npc) >= 250
							&& Math.abs(player.getZ() - npc.getZ()) <= 200 && Math.abs(player.getLevel() - npc.getLevel()) <= 4
							&& PhantomEngagementPlanner.rewardingFarmTarget(player, npc) && geo.hasGeo(npc.getX(), npc.getY())
							&& geo.canSeeTarget(player, npc) && geo.canMoveToTarget(player, npc))
						.min(Comparator.comparingDouble(player::calculateDistance2D)).orElse(null);
					if (target != null)
					{
						final var request = new PhantomCombatRequest(profile, target.getObjectId(), PhantomCombatMode.RANGED_MAGIC, true, false, 45000,
							() -> false, LearningScope.SOLO, null, () -> false);
						final var result = combat.startSession(request);
						if (result.accepted() && result.session() != null)
						{
							System.out.println("LEARNING001_SOLO_SESSION profile=" + profile + " actor=" + player.getObjectId() + " epoch="
								+ player.getNativeWorkOwner().epoch() + " generation=" + result.session().generation() + " target=" + target.getNativeEvidenceTarget());
						}
					}
				}
				Thread.sleep(1200);
			}
			combat.cancel(profile);
			store(profile);
			System.out.println("LEARNING001_SOLO_SQL_MINIMUM split=" + row[0] + " profile=" + profile + " countAtLeast=" + minimum + " bothSkills=true");
		}

		void party(List<String[]> rows, String salt, int minimum) throws Exception
		{
			final var ids = rows.stream().map(row -> Long.parseLong(row[2])).toList();
			final var farm = PhantomAutonomousFarmParty.farmSource(goals.load(ids.getFirst()).orElseThrow().goal());
			check(farm != null && farm.equals(PhantomAutonomousFarmParty.farmSource(goals.load(ids.getLast()).orElseThrow().goal())), "SHARED_STOCK_SOURCE");
			final var players = rows.stream().map(this::materialize).toList();
			final long deadline = System.nanoTime() + Duration.ofMillis(PhantomLearning001ActorFixture.sampleWindow(minimum)).toNanos();
			final String first = key(salt + ":" + ids.getFirst());
			final String last = key(salt + ":" + ids.getLast());
			boolean observed = false;
			long progressed = System.nanoTime();
			long highWater = 0;
			for (Samples samples = samples("PARTY", first, last); !samples.enough(minimum); samples = samples("PARTY", first, last))
			{
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
					System.out.println("LEARNING001_AUTONOMOUS_STOCK_PARTY profiles=" + ids + " source=" + farm.key());
				}
				Thread.sleep(1000);
			}
			check(observed, "STOCK_PARTY_OBSERVED");
			final Player leader = players.getFirst().getParty() == null ? null : players.getFirst().getParty().getLeader();
		for (long profile : ids) { combat.cancel(profile); }
		if (leader != null)
			{
				final long profile = leader == players.getFirst() ? ids.getFirst() : ids.getLast();
			await(() -> coordinator.drainAutonomousFarm(profile), Duration.ofSeconds(30), "PARTY_CANONICAL_RELEASE_" + profile);
			}
		for (long profile : ids) { store(profile); }
			System.out.println("LEARNING001_PARTY_SQL_MINIMUM split=" + rows.getFirst()[0] + " profiles=" + ids + " countAtLeast=" + minimum + " bothSkills=true");
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

	private record Samples(long count, int skills)
	{
		boolean enough(int minimum) { return count >= minimum && skills == 2; }
	}

	private static Samples samples(String scope, String first, String last) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement(
			"SELECT COUNT(*), COUNT(DISTINCT skill_id) FROM phantom_ai_training_examples WHERE origin='NATIVE' AND evidence_kind='EXACT_CAST_HP' "
			+ "AND policy_version=0 AND damage>0 AND scope=? AND actor_group IN (?,?)"))
		{
			statement.setString(1, scope);
			statement.setString(2, first);
			statement.setString(3, last);
			try (var rows = statement.executeQuery())
			{
				check(rows.next(), "SQL_SAMPLE_TOTALS");
				return new Samples(rows.getLong(1), rows.getInt(2));
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

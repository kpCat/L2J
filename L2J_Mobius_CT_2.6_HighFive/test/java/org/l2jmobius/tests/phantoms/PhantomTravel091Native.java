/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.TeleporterData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Read-only admission before provisioning any TEST091 schema or native actor. */
public final class PhantomTravel091Native
{
	private static final String SCHEMA = "l2jmobiush5_phantom_test_091";

	public static void main(String[] args) throws Exception
	{
		if (args.length != 2 || !List.of("preflight", "configure", "guards", "bootstrap", "native", "continuenative", "connections").contains(args[0]) || !Boolean.getBoolean("phantom.task091.native"))
		{
			throw new IllegalArgumentException("TASK091_EXPLICIT_PREFLIGHT_REQUIRED");
		}
		final Path source = Path.of(args[1]);
		final Path config = source.resolve("dist/game/config/Database.ini");
		if (!source.isAbsolute() || !source.equals(source.toRealPath()) || !Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS)
			|| Files.isSymbolicLink(config) || Files.size(config) > 16384)
		{
			throw new IllegalArgumentException("TASK091_SOURCE_INPUT");
		}
		final var settings = new Properties();
		try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) { settings.load(reader); }
		if (!settings.getProperty("URL", "").matches("jdbc:mysql://127\\.0\\.0\\.1:3308/[^\\r\\n]+")
			|| settings.getProperty("Login", "").isBlank() || !settings.containsKey("Password"))
		{
			throw new IllegalArgumentException("TASK091_STOP_NATIVE_INPUT");
		}
		Class.forName("com.mysql.cj.jdbc.Driver");
		if (!"preflight".equals(args[0]))
		{
			int exit = 1;
			try
			{
				final Path module = Path.of(System.getProperty("phantom.module.root"));
				final Path targetConfig = Path.of(System.getProperty("phantom.test.config"));
				if ("configure".equals(args[0]))
				{
					PhantomTask091Lane.privateAcl(PhantomTask091Lane.root());
					final Path ownership = PhantomTask091Lane.root().resolve("private/owner.properties");
					final var owner = PhantomTask091Lane.read(ownership);
					PhantomTask091Lane.ownerFields(owner, module, System.getProperty("phantom.task091.uuid"));
					PhantomTask091Lane.require(!Files.exists(targetConfig) && source.equals(Path.of(owner.getProperty("sourceModule"))), "CONFIGURE_OWNER");
					final var privateSettings = new Properties();
					privateSettings.putAll(settings);
					privateSettings.setProperty("Driver", "com.mysql.cj.jdbc.Driver");
					privateSettings.setProperty("URL", "jdbc:mysql://127.0.0.1:3308/" + SCHEMA + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8");
					privateSettings.setProperty("MaximumDatabaseConnections", "4");
					privateSettings.setProperty("TestDatabaseConnections", "false");
					privateSettings.setProperty("BackupDatabase", "false");
					try (var writer = Files.newBufferedWriter(targetConfig, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { privateSettings.store(writer, "Private TEST091 only"); }
					owner.setProperty("loginSha256", PhantomTask091Lane.hashText(settings.getProperty("Login")));
					try (var writer = Files.newBufferedWriter(ownership, StandardCharsets.UTF_8)) { owner.store(writer, "Private TEST091 ownership"); }
					PhantomTask091Lane.validate(module, targetConfig);
					System.out.println("TASK091_CONFIGURED privateACL=true DDL=0 DML=0 actors=0");
				}
				else if ("guards".equals(args[0]))
				{
					final var owner = PhantomTask091Lane.validateAt(module, targetConfig, Path.of("").toAbsolutePath());
					final var wrong = PhantomTask091Lane.read(targetConfig);
					wrong.setProperty("URL", wrong.getProperty("URL").replace("test_091", "release_078"));
					PhantomTask091Lane.rejected("JDBC_EXACT", () -> PhantomTask091Lane.settings(wrong, owner));
					PhantomTask091Lane.rejected("OWNER_UUID", () -> PhantomTask091Lane.ownerFields(owner, module, java.util.UUID.randomUUID().toString()));
					PhantomTask091Lane.rejected("CWD_CONFIG", () -> PhantomTask091Lane.validateAt(module, config, module.resolve("dist/game")));
					System.setProperty("phantom.task087.native", "true");
					try { PhantomTask091Lane.rejected("FOREIGN_NATIVE", PhantomTask091Lane::flags); }
					finally { System.clearProperty("phantom.task087.native"); }
					System.out.println("TASK091_GUARDS_PASS positiveActual=1 negatives=4 SQL=0");
				}
				else if (List.of("native", "continuenative").contains(args[0])) { firstPlayer(module, "continuenative".equals(args[0])); }
				else { PhantomTask091Lane.environment(module, targetConfig, args[0]); }
				exit = 0;
			}
			catch (Throwable failure)
			{
				final String key = failure.getMessage();
				System.out.println("TASK091_FAIL type=" + failure.getClass().getSimpleName() + "; key=" + (key != null && key.matches("TASK091_[A-Z_]+") ? key : "WITHHELD"));
				for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.tests.phantoms.")) { System.out.println("TASK091_FRAME " + frame); } }
			}
			finally
			{
				if (List.of("native", "continuenative").contains(args[0])) { ThreadPool.shutdown(); }
				if (DatabaseFactory.isInitialized()) { DatabaseFactory.close(); }
			}
			System.exit(exit);
			return;
		}
		// No default catalog: this cannot select a protected or historical database.
		try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3308/?useSSL=false&connectTimeout=3000&socketTimeout=3000", settings.getProperty("Login"), settings.getProperty("Password")))
		{
			connection.setReadOnly(true);
			try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=?"))
			{
				statement.setString(1, SCHEMA);
				try (var rows = statement.executeQuery())
				{
					if (!rows.next() || rows.getLong(1) != 0) { throw new IllegalArgumentException("TASK091_EXISTING_SCHEMA_STOP"); }
				}
			}
			boolean createAllowed = false;
			try (var statement = connection.createStatement(); var rows = statement.executeQuery("SHOW GRANTS FOR CURRENT_USER"))
			{
				while (rows.next())
				{
					final String grant = rows.getString(1);
					createAllowed |= grant.matches("(?is)GRANT (ALL PRIVILEGES|[^;]*\\bCREATE\\b[^;]*) ON (\\*\\.\\*|`" + SCHEMA + "`\\.\\*) TO .*");
				}
			}
			if (!createAllowed) { throw new IllegalArgumentException("TASK091_NAMESPACE_PRIVILEGE_STOP"); }
			System.out.println("TASK091_PREFLIGHT_PASS schemaAbsent=true namespaceCreateAllowed=true selectedCatalog=NONE DDL=0 DML=0 actors=0");
		}
		catch (SQLException failure)
		{
			// JDBC exception messages may contain private login or endpoint details.
			System.out.println("TASK091_STOP_NATIVE_INPUT sqlState=" + failure.getSQLState() + "; vendorCode=" + failure.getErrorCode() + "; DDL=0; DML=0; actors=0");
			System.exit(2);
		}
	}

	/** One fixture and one payment, before expanding the production journey design. */
	private static void firstPlayer(Path module, boolean continuation) throws Exception
	{
		PhantomTask091Lane.validate(module, Path.of(System.getProperty("phantom.test.config")));
		final Path root = PhantomTask091Lane.root();
		final String[] original = continuation ? Files.readString(root.resolve("fixture-before.tsv")).strip().split("\t", -1) : null;
		if (continuation)
		{
			PhantomTask091Lane.require(Files.exists(root.resolve("native.started")) && !Files.exists(root.resolve("first-payment.intent")) && original.length == 9, "UNPAID_CONTINUATION_ONLY");
			Files.writeString(root.resolve("native-continuation.started"), "One causal endpoint correction; same unpaid actor, no reseed", StandardOpenOption.CREATE_NEW);
		}
		else { Files.writeString(root.resolve("native.started"), "One bounded native campaign; no automatic replay", StandardOpenOption.CREATE_NEW); }
		PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(910091, module, root.resolve("reports")));
		try (var fixture = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			if (!continuation) try (var connection = DatabaseFactory.getConnection(); var statement = connection.createStatement())
			{
				statement.executeUpdate("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task091native',NULL,0,1)");
			}
			final var profiles = PhantomProfileRepository.open();
			final var metrics = new PhantomMetrics();
			final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics), 1);
			final var navigation = new PhantomNavigationService(metrics);
			PhantomTask091Lane.require(materialization.start() && navigation.start(), "OWNERS_START");
			long profile = continuation ? Long.parseLong(original[4]) : 0;
			try
			{
				if (!continuation)
				{
				final var ingress = fixture.topology().findAnchor("population.ingress.orc.01").orElseThrow().point();
				final Player setup = Player.create(PlayerTemplateData.getInstance().getTemplate(115), "task091native", "Trip091Orc", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
				PhantomTask091Lane.require(setup != null && !setup.isGM(), "PLAYER_CREATE");
				try
				{
					setup.stopAllTasks();
					setup.setXYZInvisible(ingress.x(), ingress.y(), GeoEngine.getInstance().getHeight(ingress.x(), ingress.y(), ingress.z()));
					setup.setPlayerClass(115); setup.setBaseClass(115);
					setup.getStat().setExp(setup.getStat().getExpForLevel(80)); setup.getStat().setLevel((byte) 80);
					setup.giveAvailableAutoGetSkills(); setup.giveAvailableSkills(false, true, false); setup.rewardSkills();
					for (int item : new int[] {6579, 6383, 6384, 6385, 6386}) { setup.getInventory().equipItem(setup.addItem(ItemProcessType.REWARD, item, 1, setup, false)); }
					setup.addAdena(ItemProcessType.REWARD, 200000, setup, false);
					setup.refreshExpertisePenalty(); setup.refreshOverloaded(); setup.setRunning();
					setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.setCurrentCp(setup.getMaxCp());
					setup.getInventory().updateDatabase(); setup.storeMe(); profile = profiles.create(setup.getObjectId()).profileId();
				}
				finally { setup.stopAllTasks(); setup.deleteMe(); }
				}
				PhantomTask091Lane.require(materialization.materialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "MATERIALIZATION");
				final Player player;
				try (var action = materialization.tryAcquireAction(profile).orElseThrow())
				{
					player = action.player();
					final String items = player.getInventory().getItems().stream().map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.isEquipped()).sorted().toList().toString();
					if (continuation)
					{
						PhantomTask091Lane.require(player.getObjectId() == Integer.parseInt(original[5]) && player.getLevel() == 80 && player.getActiveClass() == 115 && player.getAdena() == Long.parseLong(original[7]) && items.equals(original[8]), "CONTINUATION_IDENTITY_INVENTORY");
						Files.writeString(root.resolve("continuation-before.tsv"), "SAME_UNPAID_ACTOR\t" + profile + "\t" + player.getObjectId() + "\t" + PhantomNativeTravelActions.point(player) + "\t" + player.getAdena() + "\t" + items + "\n", StandardOpenOption.CREATE_NEW);
					}
					else { Files.writeString(root.resolve("fixture-before.tsv"), "TEST_FIXTURE\tOrc\t80\t115\t" + profile + "\t" + player.getObjectId() + "\t" + PhantomNativeTravelActions.point(player) + "\t" + player.getAdena() + "\t" + items + "\n", StandardOpenOption.CREATE_NEW); }
				}
				final var npc = World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof Npc).map(value -> (Npc) value)
					.filter(value -> value.getSpawn() != null && value.getInstanceId() == 0 && !value.isAlikeDead() && TeleporterData.getInstance().getHolder(value.getId(), "NORMAL") != null)
					.min(Comparator.comparingDouble((Npc value) -> Math.hypot((long) value.getX() - player.getX(), (long) value.getY() - player.getY())).thenComparingInt(Npc::getId)).orElseThrow();
				final var holder = TeleporterData.getInstance().getHolder(npc.getId(), "NORMAL");
				PhantomNormalGatekeeperTravel.Leg leg = null;
				for (int index = 0; index < holder.getLocations().size(); index++)
				{
					final var observed = PhantomNormalGatekeeperTravel.observeNativeLeg(npc.getId(), index, npc.getX(), npc.getY(), npc.getZ()).orElse(null);
					if (observed != null && PhantomNativeTravelActions.available(observed) && PhantomNormalGatekeeperTravel.fee(observed, player.getClassIndex(), player.getLevel(), System.currentTimeMillis() / 60000) > 0) { leg = observed; break; }
				}
				PhantomTask091Lane.require(leg != null, "PAID_NORMAL_SOURCE");
				System.out.println("TASK091_PLAYER_PREPARED profile=" + profile + "; object=" + player.getObjectId() + "; npc=" + npc.getId() + "; index=" + leg.destinationIndex() + "; evidence=PLAYER_PREPARED");
				walkFirstMile(materialization, navigation, profile, player, npc, root.resolve(continuation ? "continued-walk.tsv" : "first-walk.tsv"));
				// Test-only one-shot boundary, not a claim of a production durable journey owner.
				Files.writeString(root.resolve("first-payment.intent"), profile + ":" + player.getObjectId() + ":" + leg.id(), StandardOpenOption.CREATE_NEW);
				try (var action = materialization.tryAcquireAction(profile).orElseThrow())
				{
					PhantomTask091Lane.require(action.player() == player && npc.canInteract(player), "PAYMENT_OWNER_RANGE");
					final var paid = PhantomNativeTravelActions.gatekeeper(player, leg);
					Files.writeString(root.resolve("first-payment.tsv"), paid + "\n", StandardOpenOption.CREATE_NEW);
					System.out.println("TASK091_NATIVE_PAYMENT status=" + paid.status() + "; fee=" + paid.fee() + "; walletBefore=" + paid.adenaBefore() + "; walletAfter=" + paid.adenaAfter() + "; actual=" + paid.after());
					PhantomTask091Lane.require(paid.status() == PhantomNativeTravelActions.Status.ARRIVED && paid.fee() > 0 && paid.adenaBefore() - paid.adenaAfter() == paid.fee(), "NATIVE_PAID_ARRIVAL");
				}
			}
			finally
			{
				if (profile > 0 && materialization.find(profile).isPresent())
				{
					try (var action = materialization.tryAcquireAction(profile).orElse(null)) { if (action != null) { action.player().stopMove(null); } }
					PhantomTask091Lane.require(materialization.dematerialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "OWN_PLAYER_STOP");
				}
				navigation.beginStop(); PhantomTask091Lane.require(navigation.finishStop(), "NAVIGATION_STOP");
				PhantomTask091Lane.require(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "MATERIALIZATION_STOP");
			}
		}
	}

	private static void walkFirstMile(PhantomMaterializationService materialization, PhantomNavigationService navigation, long profile, Player player, Npc npc, Path walkReceipt) throws Exception
	{
		final var target = new PhantomNavigationPoint(npc.getX(), npc.getY(), GeoEngine.getInstance().getHeight(npc.getX(), npc.getY(), npc.getZ()), 0);
		final var visited = new HashSet<PhantomNavigationPoint>();
		final long deadline = System.nanoTime() + navigation.policy().maximumAttemptDurationNanos();
		double distance = 0;
		for (int episode = 0; episode < 16 && System.nanoTime() < deadline; episode++)
		{
			if (npc.canInteract(player)) { System.out.println("TASK091_PLAYER_ARRIVED episodes=" + episode + "; actual=" + PhantomNativeTravelActions.point(player)); return; }
			final var origin = PhantomNativeTravelActions.point(player);
			visited.add(origin);
			final long now = System.nanoTime();
			final var submission = navigation.submitWalkEpisode(new PhantomNavigationRequest(profile, origin, target, now, now + navigation.policy().defaultRequestDeadlineNanos(), 40000), visited);
			PhantomNavigationResult result = submission.immediateResult();
			while (result == null && System.nanoTime() < now + 2 * navigation.policy().defaultRequestDeadlineNanos()) { result = navigation.consume(submission.requestId()).orElse(null); if (result == null) { Thread.sleep(25); } }
			if (submission.immediateResult() != null) { navigation.consume(submission.requestId()); }
			PhantomTask091Lane.require(result != null && result.route() != null && result.route().mode() != PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA, "FINITE_WALK_PROOF");
			for (var point : result.route().waypoints())
			{
				final var before = PhantomNativeTravelActions.point(player);
				PhantomTask091Lane.require(new L2jNavigationBackend().canWalkDirect(before, point), "NATIVE_SEGMENT_PROOF");
				final var tracked = new PhantomNavigationRoute(result.route().mode(), before, point, List.of(point), result.route().geodataCapability(), System.nanoTime(), false, 1, navigation.policy().maximumRouteDistance());
				PhantomTask091Lane.require(navigation.progressTracker().begin(profile, submission.requestId(), tracked, System.nanoTime()).status() == PhantomNavigationProgressTracker.BeginStatus.TRACKING, "PROGRESS_OWNER");
				try (var action = materialization.tryAcquireAction(profile).orElseThrow())
				{
					PhantomTask091Lane.require(action.player() == player && !player.isDead() && !player.isInParty() && !player.isInStoreMode(), "WALK_OWNER");
					player.getAI().setIntention(Intention.MOVE_TO, new Location(point.x(), point.y(), point.z()));
				}
				var last = PhantomNativeTravelActions.point(player);
				boolean tracking = true;
				final long segmentDeadline = Math.min(deadline, System.nanoTime() + navigation.policy().maximumAttemptDurationNanos());
				// Arrival radius observes progress. It must not stop native movement before
				// the proven corner and thereby cut an unproved diagonal to the next segment.
				while ((player.isMoving() || PhantomNativeTravelActions.point(player).distanceTo(point) > navigation.arrivalRadius()) && System.nanoTime() < segmentDeadline)
				{
					Thread.sleep(100);
					final var actual = PhantomNativeTravelActions.point(player);
					PhantomTask091Lane.require(actual.distanceTo(last) < 1000 && !player.isDead(), "PHYSICAL_CONTINUITY");
					if (tracking)
					{
						final var progress = navigation.progressTracker().observe(profile, submission.requestId(), actual, System.nanoTime()).status();
						PhantomTask091Lane.require(progress != PhantomNavigationProgressTracker.ProgressStatus.STUCK && progress != PhantomNavigationProgressTracker.ProgressStatus.TIMEOUT && progress != PhantomNavigationProgressTracker.ProgressStatus.STALE, "NATIVE_PROGRESS_STOP");
						tracking = progress != PhantomNavigationProgressTracker.ProgressStatus.ARRIVED;
					}
					distance += last.distanceTo(actual); last = actual;
					PhantomTask091Lane.require(distance <= 40000, "JOURNEY_DISTANCE_BUDGET");
				}
				PhantomTask091Lane.require(!player.isMoving() && PhantomNativeTravelActions.point(player).distanceTo(point) <= navigation.arrivalRadius(), "WALK_DEADLINE");
				navigation.progressTracker().cancel(profile, submission.requestId());
				Files.writeString(walkReceipt, episode + "\t" + System.currentTimeMillis() + "\t" + point + "\t" + PhantomNativeTravelActions.point(player) + "\t" + distance + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				System.out.println("TASK091_PLAYER_MOVE_ISSUED episode=" + episode + "; observed=" + PhantomNativeTravelActions.point(player) + "; cumulative=" + (long) distance);
			}
		}
		PhantomTask091Lane.require(npc.canInteract(player), "FIRST_MILE_BUDGET_EXHAUSTED");
	}
}

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.zaxxer.hikari.HikariDataSource;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.config.PlayerConfig;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.data.xml.AdminData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.templates.PlayerTemplate;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore.Snapshot;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff;
import org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionBackend.BackendData;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;

/** A DB-free native planning lease and the real companion CAS over an isolated component fixture. */
public final class PhantomOwnerPlay002R3Checks
{
	private static final long ID = 91;
	private static final int OBJECT = 90000293;
	private static final long NOW = 1_000_000;
	private static final String LEASE_WAIT = "catchup.baseline.action_lease";
	private static final String PUBLICATION_WAIT = "catchup.baseline.publication_pending";
	private static final PhantomDomainRef SOLO = PhantomExecutionHandoff.solo(ID);

	public static void main(String[] args) throws Exception
	{
		check(!DatabaseFactory.isInitialized(), "This regression requires an isolated JVM.");
		final var datapack = Path.of(".").toAbsolutePath().normalize();
		ServerConfig.DATAPACK_ROOT = datapack.toFile();
		ServerConfig.SCRIPT_ROOT = datapack.resolve("data/scripts").toFile();
		PlayerConfig.load();
		GeoEngineConfig.load();
		GeoEngineConfig.GEODATA_PATH = Path.of(args[0], "dist", "game", "data", "geodata");
		// Accept stock timer registrations without running unrelated world events in this fixture.
		final var scheduler = new ScheduledThreadPoolExecutor(1)
		{
			@Override public ScheduledFuture<?> schedule(Runnable work, long delay, TimeUnit unit)
			{ return super.schedule(work, 1, TimeUnit.DAYS); }
			@Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable work, long delay, long period, TimeUnit unit)
			{ return super.schedule(work, 1, TimeUnit.DAYS); }
		};
		var field = ThreadPool.class.getDeclaredField("SCHEDULED_POOL");
		field.setAccessible(true);
		field.set(null, scheduler);
		final var knowledge = PhantomGameKnowledgeService.inertForTesting("r3-owner-scope");
		try
		{
			check(knowledge.start(), "The isolated knowledge service did not start.");
			run(knowledge);
		}
		finally
		{
			scheduler.shutdownNow();
			field.set(null, null);
			knowledge.beginStop();
			knowledge.finishStop();
		}
		check(!DatabaseFactory.isInitialized(), "Real SQL must remain uninitialized.");
		System.out.println("OWNER_PLAY_002R3_NORMAL_SCOPE_PASS");
	}

	private static void run(PhantomGameKnowledgeService knowledge) throws Exception
	{
		// The authored Human Fighter ingress from high-five-core.xml and its actual Geo tile.
		final String ingress = "population.ingress.human-fighter.01";
		final var spawn = new PhantomTopologyPoint(-71338, 258271, -3104, 0);
		final var sources = List.of("data/stats/players/templates/StartingClass/HumanFighter.xml");
		final var tags = List.of("population-ingress");
		final var node = new PhantomTopologyNode(ingress, PhantomTopologyNodeKind.ROUTE_AREA, 0, PhantomTopologyArea.pointRadius(spawn, 1), null, tags, sources);
		final var anchor = new PhantomTopologyAnchor(ingress, PhantomTopologyAnchorRole.ROUTE, ingress, spawn, null, null, 0, tags, sources);
		final var backend = new L2jTopologyValidationBackend();
		final var topology = new PhantomTopologyQuery(PhantomTopologySnapshot.create(1, "r3-owner-scope", 1, 1, List.of(node), List.of(anchor), List.of(),
			backend, PhantomTopologyPolicy.productionDefaults()), backend, new PhantomTopologyMetrics());
		final var progression = new PhantomProgressionCatalogBuilder().build(new BackendData(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Set.of()),
			PhantomProgressionPolicy.productionDefaults());
		final var commerce = new PhantomCommerceCatalog(List.of(), List.of(), List.of(), List.of());
		final var nativeAuthority = new L2jPhantomBackgroundAuthority(knowledge::query, () -> topology, () -> progression, () -> commerce);
		final var gate = new PhantomExecutionAdmission(1);
		final var identityRegistry = PhantomIdentityLeaseRegistry.getInstance();
		final var repositoryConstructor = PhantomProfileRepository.class.getDeclaredConstructor();
		repositoryConstructor.setAccessible(true);
		final var repository = repositoryConstructor.newInstance();
		final var goals = new PhantomGoalStateStore(repository);
		final var handoffs = new PhantomExecutionHandoffStore(repository);
		goals.installExecutionPublication(handoffs, gate, () -> NOW);
		final var catchups = new PhantomBackgroundCatchupStore(repository, goals);
		final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(repository, identityRegistry, metrics, new PhantomDiagnosticTrace(false, 2, 1, metrics), 1);
		materialization.installExecutionAdmission(gate);
		check(materialization.start(), "The isolated materialization service did not start.");
		final var planner = new PhantomHistoricalBackgroundPlanner(knowledge.query(), topology, nativeAuthority);
		final var background = new PhantomBackgroundService(repository, goals, identityRegistry, new PhantomBackgroundTransaction(),
			nativeAuthority, new PhantomBackgroundCompetitionRegistry(), (PhantomRelevanceSignalPort) Proxy.newProxyInstance(
				PhantomRelevanceSignalPort.class.getClassLoader(), new Class<?>[] { PhantomRelevanceSignalPort.class },
				(_, method, _) -> { throw new AssertionError("Unexpected relevance signal: " + method.getName()); }), () -> materialization);
		final var historical = new PhantomHistoricalBackgroundService(repository, goals, planner, background, materialization);
		final var planning = PhantomHistoricalBackgroundService.class.getDeclaredMethod("planInitialBaseline", long.class, Snapshot.class);
		planning.setAccessible(true);
		final var generation = planner.generation();
		final var pending = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING, "r3-fixture", 27, 10, 15, 10, 0, 0, 1,
			generation.knowledgeGeneration(), generation.topologyGeneration(), 0, 0, "", PhantomBackgroundState.MODEL_VERSION, generation.authorityHashes(), "");
		try (var identity = identityRegistry.tryAcquire(OBJECT, PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM))
		{
			final var actor = new PhantomMaterializedPlayer(OBJECT, identityRegistry, new HeadlessPlayerOutboundSession(16, 128));
			final var nativeOwner = new PhantomNativeWorkScope[1];
			final var ownerConstructor = PhantomNativeWorkScope.class.getDeclaredConstructor(Object.class, Player.class, PhantomIdentityLeaseRegistry.Lease.class,
				long.class, PhantomExecutionAdmission.class, long.class);
			ownerConstructor.setAccessible(true);
			final var playerConstructor = Player.class.getDeclaredConstructor(int.class, PlayerTemplate.class, String.class, PlayerAppearance.class, Consumer.class);
			playerConstructor.setAccessible(true);
			final var player = playerConstructor.newInstance(OBJECT, PlayerTemplateData.getInstance().getTemplate(0), "DB_FREE_R3", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false), (Consumer<Player>) value ->
			{
				try { nativeOwner[0] = ownerConstructor.newInstance(actor, value, identity, 1L, gate, ID); }
				catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
				value.attachNativeWorkOwner(nativeOwner[0]);
			});
			final var finish = Player.class.getDeclaredMethod("finishNativeLoad", Throwable.class);
			finish.setAccessible(true);
			finish.invoke(player, (Object) null);
			player.stopAllTasks();
			set(player, "_accessLevel", AdminData.getInstance().getAccessLevel(0));
			check(org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().hasGeo(spawn.x(), spawn.y()), "The stock profile's anchor needs real geodata.");
			player.getLocation().setXYZ(spawn.x(), spawn.y(), spawn.z());
			// Resolve the stock festival eligibility singleton with the SQL pool still closed.
			check(!player.isFestivalParticipant(), "The isolated player has no festival membership.");
			set(actor, "_execution", gate);
			set(actor, "_executionProfileId", ID);
			set(actor, "_player", player);
			set(actor, "_nativeWork", nativeOwner[0]);
			set(actor, "_state", PhantomMaterializedPlayer.State.ACTIVE);
			set(actor, "_actionAdmissionOpen", true);
			final var entry = Class.forName(PhantomMaterializationService.class.getName() + "$Entry").getDeclaredConstructor(long.class, int.class, PhantomMaterializedPlayer.class);
			entry.setAccessible(true);
			final var active = PhantomMaterializationService.class.getDeclaredField("_activeByProfile");
			active.setAccessible(true);
			@SuppressWarnings("unchecked")
			final var entries = (Map<Long, Object>) active.get(materialization);
			entries.put(ID, entry.newInstance(ID, OBJECT, actor));
			try (var components = new ComponentFixture())
			{
				final var claim = catchups.claim(ID, pending);
				var grant = handoffs.initialize(ID, SOLO);
				gate.publish(ID, grant.component().rowVersion(), grant.state());
				final var actualOwner = new java.util.concurrent.atomic.AtomicReference<>(SOLO);
				final Runnable denied = () -> check(LEASE_WAIT.equals(plan(planning, historical, claim).reason()), "Planning entered with closed execution admission.");
				denied.run();
				historical.bindInitialPlanningExecution(gate, _ -> actualOwner.get());

				check(gate.prepareAuthority(ID, () -> { denied.run(); return true; }) == Boolean.TRUE, "Refresh boundary did not run.");
				gate.blocker(ID, "test.blocker", 0, true);
				denied.run();
				gate.blocker(ID, "test.blocker", 1, false);
				actualOwner.set(PhantomExecutionHandoff.solo(ID + 1));
				denied.run();
				actualOwner.set(SOLO);
				final var result = plan(planning, historical, claim);
				check(result.status() == PhantomHistoricalBackgroundService.ResultStatusCode.RETRY && PUBLICATION_WAIT.equals(result.reason()),
					"An ACTIVE solo owner must reach the stock planner and stage publication, received " + result.reason());
				check(materialization.tryAcquireAction(ID).isEmpty(), "The short NORMAL scope leaked into worker code.");
				check(goals.load(ID).isEmpty() && claim.equals(catchups.load(ID).orElseThrow()), "Staging must not publish either durable companion.");
				final var publication = goals.publication(ID).orElseThrow();
				final var beforeRequest = grant;
				grant = gate.requesting(ID, grant.component().rowVersion(), () ->
				{
					denied.run();
					final var requested = handoffs.request(beforeRequest, SOLO, publication.state().requestId(), NOW, publication.state().deadline());
					gate.publish(ID, requested.component().rowVersion(), requested.state());
					return requested;
				});
				denied.run();
				entries.clear();

				// Reopen the durable stores at REQUEST, then finish the existing companion transfer.
				final var resumedGoals = new PhantomGoalStateStore(repository);
				resumedGoals.installExecutionPublication(handoffs, new PhantomExecutionAdmission(1), () -> NOW);
				final var resumedCatchups = new PhantomBackgroundCatchupStore(repository, resumedGoals);
				grant = handoffs.yielded(handoffs.load(ID).orElseThrow(), NOW);
				grant = handoffs.checkpoint(grant, List.of(), List.of("goal.runtime", "background.catchup", "goal.publication"), NOW);
				final var authority = resumedGoals.publicationAuthority(grant, NOW);
				handoffs.transfer(grant, authority.guards(), authority.changes(), authority.removals(), authority.absences(), NOW);
				final var root = resumedGoals.load(ID).orElseThrow();
				final var committed = resumedCatchups.load(ID).orElseThrow();
				check(root.goal().goalId() == committed.state().goalId() && root.goal().revision() == committed.state().goalRevision()
					&& committed.state().requestId().equals(claim.state().requestId()) && committed.state().intervalOrdinal() == 0
					&& committed.state().planOrdinal() == 0 && committed.state().cursorEpochMinute() == claim.state().cursorEpochMinute(),
					"TRANSFER must publish the exact initial root/claim without an earned interval.");
				check(components.commits.stream().filter(rows -> rows.contains("goal.runtime")).toList()
					.equals(List.of(Set.of("goal.runtime", "background.catchup", "goal.publication", "execution.handoff"))),
					"Goal and its companion must first become durable in the same transaction.");
				check(resumedGoals.publication(ID).orElseThrow().state().status() == PhantomGoalPublication.Status.COMMITTED
					&& resumedGoals.pendingPublication(ID, NOW).isEmpty() && resumedCatchups.claim(ID, pending).equals(committed),
					"Retry after TRANSFER must retain the exact root/claim without another publication.");
			}
			finally
			{
				entries.clear();
				player.stopAllTasks();
				nativeOwner[0].drainAndSeal(System.nanoTime() + 3_000_000_000L);
				nativeOwner[0].detach();
				player.detachNativeWorkOwner(nativeOwner[0]);
			}
		}
	}

	private static PhantomHistoricalBackgroundService.Result plan(java.lang.reflect.Method method, PhantomHistoricalBackgroundService historical, Snapshot claim)
	{
		try { return (PhantomHistoricalBackgroundService.Result) method.invoke(historical, ID, claim); }
		catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		final var field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void check(boolean condition, String reason)
	{
		if (!condition) { throw new AssertionError(reason); }
	}

	/** Only Phantom component statements are accepted. Each connection has a private transaction image. */
	private static final class ComponentFixture extends HikariDataSource
	{
		private final java.lang.reflect.Field pool;
		private final List<Set<String>> commits = new ArrayList<>();
		private Map<String, Row> durable = Map.of();
		private record Row(int schema, long version, byte[] payload) { }

		ComponentFixture() throws Exception
		{
			pool = DatabaseFactory.class.getDeclaredField("DATABASE_POOL");
			pool.setAccessible(true);
			pool.set(null, this);
		}

		@Override
		public Connection getConnection()
		{
			final Map<String, Row> working = new HashMap<>(durable);
			final Set<String> writes = new java.util.HashSet<>();
			return jdbc(Connection.class, (_, method, args) -> switch (method.getName())
			{
				case "prepareStatement" -> statement((String) args[0], working, writes);
				case "commit" -> { durable = Map.copyOf(working); commits.add(Set.copyOf(writes)); yield null; }
				case "rollback", "setAutoCommit", "close" -> null;
				default -> throw new AssertionError(method);
			});
		}

		private PreparedStatement statement(String sql, Map<String, Row> rows, Set<String> writes)
		{
			final Map<Integer, Object> parameters = new HashMap<>();
			return jdbc(PreparedStatement.class, (_, method, args) -> switch (method.getName())
			{
				case "setLong", "setInt", "setString", "setBytes" -> { parameters.put((Integer) args[0], args[1]); yield null; }
				case "close" -> null;
				case "executeQuery" ->
				{
					check(Long.valueOf(ID).equals(parameters.get(1)), "Unexpected profile read.");
					if (sql.equals("SELECT profile_id FROM phantom_profiles WHERE profile_id = ? FOR UPDATE")) { yield result(null, null, true); }
					check(sql.contains(" FROM phantom_profile_components WHERE profile_id = ? AND component_type = ?"), sql);
					final String type = (String) parameters.get(2);
					yield result(type, rows.get(type), rows.containsKey(type));
				}
				case "executeUpdate" ->
				{
					if (sql.startsWith("INSERT INTO phantom_profile_components "))
					{
						final String type = (String) parameters.get(2);
						check(Long.valueOf(ID).equals(parameters.get(1)) && !rows.containsKey(type), "Unexpected component insert: " + type);
						rows.put(type, new Row((int) parameters.get(3), 0, (byte[]) parameters.get(4)));
						writes.add(type);
						yield 1;
					}
					check(sql.startsWith("UPDATE phantom_profile_components SET "), sql);
					final String type = (String) parameters.get(4);
					final Row old = rows.get(type);
					if (old == null || old.version() != (long) parameters.get(5)) { yield 0; }
					check(Long.valueOf(ID).equals(parameters.get(3)), "Unexpected component update: " + type);
					rows.put(type, new Row((int) parameters.get(1), old.version() + 1, (byte[]) parameters.get(2)));
					writes.add(type);
					yield 1;
				}
				default -> throw new AssertionError(method);
			});
		}

		private static ResultSet result(String type, Row row, boolean present)
		{
			final var next = new java.util.concurrent.atomic.AtomicBoolean(present);
			return jdbc(ResultSet.class, (_, method, args) -> switch (method.getName())
			{
				case "next" -> next.getAndSet(false);
				case "getLong" -> "profile_id".equals(args[0]) ? ID : row.version();
				case "getInt" -> row.schema();
				case "getString" -> type;
				case "getBytes" -> row.payload();
				case "getTimestamp" -> new Timestamp(NOW);
				case "close" -> null;
				default -> throw new AssertionError(method);
			});
		}

		private static <T> T jdbc(Class<T> type, java.lang.reflect.InvocationHandler handler)
		{ return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler)); }

		@Override
		public void close()
		{
			try { pool.set(null, null); }
			catch (IllegalAccessException failure) { throw new AssertionError(failure); }
			super.close();
		}
	}
}

package org.l2jmobius.gameserver.phantoms.navigation;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

import org.l2jmobius.commons.network.Connection;
import org.l2jmobius.commons.network.ConnectionConfig;
import org.l2jmobius.commons.network.PacketExecutor;
import org.l2jmobius.commons.network.ReadHandler;
import org.l2jmobius.commons.network.WriteHandler;
import org.l2jmobius.gameserver.GameServer;
import org.l2jmobius.gameserver.Shutdown;
import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.network.ConnectionState;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteTrace.Point;

/** One stock GameServer bootstrap with an isolated socket-backed actor. No production configuration or database. */
public final class HumanRoute007NativeTest
{
	private static final String ACCOUNT = "route007test";
	private record Route(String id, Point from, Point to) { }

	public static void main(String[] args) throws Exception
	{
		final Path game = Path.of(".").toRealPath();
		final Path root = game.getParent();
		final Path mailbox = Path.of(System.getProperty("phantom.humanRoute.root")).toRealPath();
		check(root.getParent().getFileName().toString().equals("009-route-lifecycle")
			&& Files.readString(root.resolve(".codex-owned.json")).contains("009-route-lifecycle"), "isolated 007 root required");
		check(mailbox.startsWith(root) && game.getFileName().toString().equals("game"), "isolated mailbox required");
		check(Files.readString(game.resolve("config/Database.ini")).contains("jdbc:mysql://127.0.0.1:33479/l2j_route007_test?useSSL=false"), "isolated database required");
		check(Files.readString(game.resolve("config/Server.ini")).contains("GameserverPort = 17779")
			&& Files.readString(game.resolve("config/Server.ini")).contains("LoginPort = 19019"), "isolated listeners required");
		GameServer.main(new String[0]);
		try { run(mailbox, args); }
		finally { Shutdown.getInstance().startShutdown(null, 0, false); }
	}

	private static void run(Path mailbox, String[] args) throws Exception
	{
		final var server = record(mailbox.resolve("server.txt"));
		check(server.get("pid").equals(Long.toString(ProcessHandle.current().pid())), "mailbox PID is not the GameServer PID");
		check(status(mailbox).get("phase").equals("WAITING_LOGIN"), "operator service requires a boot permit");
		if (args.length == 1)
		{
			final var operator = new ProcessBuilder("pwsh.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", args[0],
				"-Root", mailbox.toString(), "-Action", "status", "-ServerProcessId", server.get("pid")).inheritIO().start();
			check(operator.waitFor(15, TimeUnit.SECONDS) && operator.exitValue() == 0, "operator PowerShell status failed");
		}
		final var routes = routes();
		final String fingerprint = PhantomGeoTrainer.fingerprint(GeoEngineConfig.GEODATA_PATH, () -> false);
		final Player actor = Player.create(PlayerTemplateData.getInstance().getTemplate(0), ACCOUNT, "Route" + UUID.randomUUID().toString().substring(0, 8),
			new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		check(actor != null, "test player unavailable");
		final Point start = routes.getFirst().from();
		actor.setXYZ(start.x(), start.y(), start.z());
		actor.setCurrentHp(actor.getMaxHp());
		actor.setOnlineStatus(true, false);
		actor.spawnMe();
		try (var firstClient = new FixtureClient(actor))
		{
			check(send(mailbox, actor, "reload", "-", routes.subList(0, 3), fingerprint, 0).get("result").equals("QUEUE_LOADED"), "first queue rejected");
			land(mailbox, actor, "arm", "-", 5);
			final Route first = routes.getFirst();
			actor.moveToLocation(first.to().x(), first.to().y(), first.to().z(), 0);
			await(() -> !actor.isMoving() && point(actor).within(first.to(), 24, 24), "native MOVE did not reach first scene target");
			check(send(mailbox, actor, "arrived", first.id(), List.of(), "", 0).get("result").equals("ARRIVED"), "genuine native arrival not sealed");

			land(mailbox, actor, "next", first.id(), 0);
			expirePassage(mailbox);
			check(status(mailbox).get("detail").equals("SCENE_TIMEOUT") && status(mailbox).get("result").equals("PARTIAL"), "timeout did not seal only the passage");
			check(send(mailbox, actor, "renew", "-", List.of(), "", 10).get("result").equals("RENEWED"), "consent renewal failed");

			land(mailbox, actor, "next", routes.get(1).id(), 0);
			check(send(mailbox, actor, "skip", routes.get(2).id(), List.of(), "", 0).get("result").equals("SKIPPED"), "third scene did not seal");
			check(send(mailbox, actor, "next", routes.get(2).id(), List.of(), "", 0).get("result").equals("QUEUE_EMPTY"), "queue exhaustion did not leave consent available");
			check(send(mailbox, actor, "reload", "-", routes.subList(3, 4), fingerprint, 0).get("result").equals("QUEUE_LOADED"), "hot queue reload failed");
			land(mailbox, actor, "next", routes.get(2).id(), 0);
			check(send(mailbox, actor, "stop", "-", List.of(), "", 0).get("phase").equals("WAITING_LOGIN"), "stop did not release the client");
			check(send(mailbox, actor, "arm", "-", List.of(), "", 0).get("detail").equals("INVALID_PERMIT"), "re-arm reused old consent");
		}
		try (var secondClient = new FixtureClient(actor))
		{
			land(mailbox, actor, "arm", "-", 5);
			HumanRouteService.disconnected(secondClient.client);
			await(() -> "WAITING_LOGIN".equals(status(mailbox).get("phase")), "logout did not release consent");
		}
		final Path evidence = mailbox.resolve("evidence");
		final Map<Path, String> sealed;
		try (var files = Files.list(evidence))
		{
			sealed = files.filter(file -> file.getFileName().toString().endsWith(".trace"))
				.collect(Collectors.toMap(file -> file, file -> record(file).get("sha256")));
		}
		try (var thirdClient = new FixtureClient(actor))
		{
			land(mailbox, actor, "arm", "-", 5);
			final Object service = service();
			final Object capture = field(service, "_capture").get(service);
			final var id = capture.getClass().getDeclaredMethod("id");
			id.setAccessible(true);
			final Path obstructed = Files.createDirectory(evidence.resolve(id.invoke(capture) + "-" + routes.get(3).id() + ".trace"));
			try
			{
				check(send(mailbox, actor, "skip", routes.get(3).id(), List.of(), "", 0).get("result").equals("FAILED"), "write failure went unreported");
				check(status(mailbox).get("phase").equals("WAITING_LOGIN"), "write failure left consent bound");
				for (var entry : sealed.entrySet())
				{
					check(entry.getValue().equals(record(entry.getKey()).get("sha256")), "a completed route was lost on write failure");
				}
			}
			finally { Files.delete(obstructed); }
		}
		check(server.equals(record(mailbox.resolve("server.txt"))), "server PID, start or incarnation changed");
		check(Integer.parseInt(status(mailbox).get("saved")) == 5, "completed routes lost or overwritten");
		System.out.println("PASS HumanRoute native GameServer PID=" + server.get("pid") + " ARRIVED, PARTIAL/SCENE_TIMEOUT, SKIPPED, reload, renew, stop, re-arm, logout and export failure");
	}

	private static void expirePassage(Path mailbox) throws Exception
	{
		// Shift only the test capture timestamp. Production still uses its fixed ten-minute limit.
		final Object service = service();
		final var captureField = field(service, "_capture");
		synchronized (service)
		{
			final Object capture = captureField.get(service);
			final var fields = capture.getClass().getRecordComponents();
			final Object[] values = new Object[fields.length];
			final Class<?>[] types = new Class<?>[fields.length];
			for (int i = 0; i < fields.length; i++)
			{
				final var accessor = fields[i].getAccessor();
				accessor.setAccessible(true);
				values[i] = accessor.invoke(capture);
				types[i] = fields[i].getType();
			}
			final var constructor = capture.getClass().getDeclaredConstructor(types);
			constructor.setAccessible(true);
			values[values.length - 1] = (long) values[values.length - 1] - 600_001;
			captureField.set(service, constructor.newInstance(values));
		}
		await(() -> "SEALED".equals(status(mailbox).get("phase")), "passage timeout did not seal");
	}

	private static Object service() throws Exception
	{
		final var active = HumanRouteService.class.getDeclaredField("ACTIVE");
		active.setAccessible(true);
		return active.get(null);
	}

	private static java.lang.reflect.Field field(Object object, String name) throws Exception
	{
		final var field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}

	private static List<Route> routes()
	{
		final List<Route> result = new ArrayList<>();
		for (int y = 15392; y <= 16032 && result.size() < 4; y += 32)
		{
			final Point from = new Point(12207, y, -4552, 0);
			final Point to = new Point(12399, y, -4552, 0);
			if (HumanRouteScenes.safeLanding(from, 24) && HumanRouteScenes.knownDryPoint(to, 24)
				&& GeoEngine.getInstance().canMoveToTarget(from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), 0))
			{
				result.add(new Route("route-" + (result.size() + 1), from, to));
			}
		}
		check(result.size() == 4, "test fixture lacks four dry traversable segments");
		return result;
	}

	private static void land(Path mailbox, Player actor, String action, String scene, int minutes) throws Exception
	{
		check(send(mailbox, actor, action, scene, List.of(), "", minutes).get("result").equals("TELEPORT_PENDING"), action + " did not issue a native teleport");
		if (actor.isTeleporting()) { actor.onTeleported(); }
		await(() -> "RECORDING".equals(status(mailbox).get("phase")), "server did not validate landing");
	}

	private static Map<String, String> send(Path mailbox, Player actor, String action, String scene, List<Route> routes, String fingerprint, int minutes) throws Exception
	{
		final String incarnation = record(mailbox.resolve("server.txt")).get("incarnation");
		final UUID id = UUID.randomUUID();
		final boolean stop = action.equals("stop");
		final String sequence = stop ? "0" : status(mailbox).get("next_sequence");
		final long deadline = System.currentTimeMillis() + 12_000;
		if (minutes > 0)
		{
			Files.writeString(mailbox.resolve("permit-" + id + ".txt"), "EnableHumanRouteDemonstration=True\nincarnation=" + incarnation + "\nid=" + id
				+ "\nsequence=" + sequence + "\nrequestDeadline=" + deadline + "\naccount=" + ACCOUNT + "\nobjectId=" + actor.getObjectId()
				+ "\nexpires=" + (System.currentTimeMillis() + minutes * 60_000L) + "\n");
		}
		if (!routes.isEmpty())
		{
			final Path bundle = Files.createDirectory(mailbox.resolve("queue-" + id));
			final var rows = new StringBuilder();
			final var failures = new StringBuilder();
			for (Route route : routes)
			{
				final Point from = route.from();
				final Point to = route.to();
				final String edge = "%d\t%d\t%d\t%d\t%d\t%d".formatted(from.x(), from.y(), from.z(), to.x(), to.y(), to.z());
				rows.append(route.id()).append('\t').append(edge).append("\t24\t24\tУ старой лавки\tИди по площадке\tLEARNED_FAILURE\n");
				failures.append("0\t").append(edge).append("\tFAILURE_ONLY\tFAILURE_ONLY\t0\t0\t1\t0\t0\n");
			}
			Files.writeString(bundle.resolve("scenes.tsv"), rows);
			Files.writeString(bundle.resolve("region-0_0.tsv"), "PHANTOM_ROUTE_LEARNING\t1\t" + fingerprint + "\t" + HumanRouteScenes.sha256(failures.toString()) + "\n" + failures);
		}
		final Path pending = mailbox.resolve("fixture-" + id);
		Files.writeString(pending, "version=1\nincarnation=" + incarnation + "\nid=" + id + "\nsequence=" + sequence
			+ "\ndeadline=" + deadline + "\naction=" + action + "\nscene=" + scene + "\n");
		Files.move(pending, mailbox.resolve(stop ? "stop.txt" : "request.txt"), StandardCopyOption.ATOMIC_MOVE);
		final Path reply = mailbox.resolve(stop ? "stop-receipt.txt" : "receipt.txt");
		await(() -> id.toString().equals(record(reply).get("id")), "no reply for " + action);
		return record(reply);
	}

	private static Point point(Player actor) { return new Point(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId()); }
	private static Map<String, String> status(Path mailbox) { return record(mailbox.resolve("status.txt")); }

	private static Map<String, String> record(Path file)
	{
		try { return Files.readString(file).lines().filter(line -> line.indexOf('=') > 0).map(line -> line.split("=", 2)).collect(Collectors.toMap(parts -> parts[0], parts -> parts[1])); }
		catch (java.io.IOException absent) { return Map.of(); }
	}

	private static void await(BooleanSupplier condition, String message) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) { Thread.sleep(50); }
		check(condition.getAsBoolean(), message);
	}

	private static void check(boolean passed, String message) { if (!passed) { throw new AssertionError(message); } }

	private static final class FixtureClient implements AutoCloseable
	{
		private final Player actor;
		private final AsynchronousServerSocketChannel listener = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress("127.0.0.1", 0));
		private final AsynchronousSocketChannel channel = AsynchronousSocketChannel.open();
		private final AsynchronousSocketChannel peer;
		final GameClient client;

		FixtureClient(Player actor) throws Exception
		{
			this.actor = actor;
			final var accept = listener.accept();
			channel.connect(listener.getLocalAddress()).get(5, TimeUnit.SECONDS);
			peer = accept.get(5, TimeUnit.SECONDS);
			final var config = new ConnectionConfig(listener.getLocalAddress());
			final var reader = new ReadHandler<GameClient>((buffer, gameClient) -> null, new PacketExecutor<>(config));
			final var connection = new Connection<GameClient>(channel, reader, new WriteHandler<>(), config);
			client = new GameClient(connection);
			client.setAccountName(ACCOUNT);
			client.setConnectionState(ConnectionState.IN_GAME);
			connection.setClient(client);
			client.setPlayer(actor);
			actor.setClient(client);
		}

		@Override
		public void close() throws Exception
		{
			actor.setClient(null);
			client.setPlayer(null);
			channel.close();
			peer.close();
			listener.close();
		}
	}
}

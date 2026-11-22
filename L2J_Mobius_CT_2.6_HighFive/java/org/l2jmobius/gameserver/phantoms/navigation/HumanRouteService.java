package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.ConnectionState;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteMailbox.Permit;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteMailbox.Request;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteScenes.Scene;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteTrace.Point;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteTrace.Report;

/** A short, private observation of one consenting real client. The scheduler owns command and export ordering. */
public final class HumanRouteService
{
	private static final Logger LOGGER = Logger.getLogger(HumanRouteService.class.getName());
	private static final int SCENE_MILLIS = 600_000;
	private static final int LANDING_MILLIS = 15_000;
	private static final int CHECKPOINT_MILLIS = 5_000;
	private static final int CONSENT_MILLIS = 45 * 60_000;
	private static final int MAX_EXPORTS = 128;
	private static final long MAX_EXPORT_BYTES = 64L * 1024 * 1024;
	private static volatile HumanRouteService ACTIVE;

	private enum Phase { WAITING_LOGIN, TELEPORT_PENDING, RECORDING, SEALED, SKIPPED, OFF }
	private record Capture(Scene scene, HumanRouteTrace trace, long started) { }
	private record Landing(Scene scene, Point expected, long deadline) { }
	private record Receipt(String result, String detail, Report report) { }

	private static final class Binding
	{
		final Player player;
		final GameClient client;
		final long deadline;
		volatile Thread issuingTeleport;
		volatile String interruption;

		Binding(Player player, GameClient client, long deadline)
		{
			this.player = player;
			this.client = client;
			this.deadline = deadline;
		}
	}

	private final HumanRouteMailbox _mailbox;
	private final Permit _permit;
	private final Set<String> _attempted = new HashSet<>();
	private final UUID _run = UUID.randomUUID();
	private volatile boolean _stopping;
	private volatile Binding _binding;
	private volatile Capture _capture;
	private volatile Point _lastObserved;
	private ScheduledFuture<?> _worker;
	private HumanRouteScenes.Queue _queue;
	private Landing _landing;
	private String _fingerprint;
	private long _geoGeneration;
	private String _lastScene;
	private String _lastResult = "WAITING_LOGIN";
	private String _lastDetail = "-";
	private Report _lastReport;
	private Phase _phase = Phase.WAITING_LOGIN;
	private long _nextCheckpoint;
	private int _sealed;
	private boolean _used;
	private boolean _released;

	private HumanRouteService(HumanRouteMailbox mailbox, Permit permit)
	{
		_mailbox = mailbox;
		_permit = permit;
	}

	public static synchronized void startConfigured()
	{
		final String root = System.getProperty("phantom.humanRoute.root");
		if (root == null || root.isBlank() || ACTIVE != null) { return; }
		HumanRouteMailbox mailbox = null;
		try
		{
			mailbox = new HumanRouteMailbox(Path.of(root));
			mailbox.validate();
			final var service = new HumanRouteService(mailbox, mailbox.permit(System.currentTimeMillis()));
			ACTIVE = service;
			service._worker = ThreadPool.scheduleAtFixedRateOrThrow(service::poll, 250, 250);
		}
		catch (IOException | RuntimeException failure)
		{
			ACTIVE = null;
			closeMailbox(mailbox);
			LOGGER.warning("Human route demonstration disabled: private startup validation failed.");
		}
	}

	public static synchronized void stopConfigured()
	{
		final HumanRouteService service = ACTIVE;
		if (service != null)
		{
			service._stopping = true;
			service._worker.cancel(false);
			service.drain();
			ACTIVE = null;
		}
	}

	public static void teleportStarting(Creature actor)
	{
		final HumanRouteService service = ACTIVE;
		final Binding binding = service == null ? null : service._binding;
		if (binding != null && binding.player == actor && binding.issuingTeleport != Thread.currentThread())
		{
			service.interrupt(binding, "UNEXPECTED_TELEPORT");
		}
	}

	public static void disconnected(GameClient client)
	{
		final HumanRouteService service = ACTIVE;
		final Binding binding = service == null ? null : service._binding;
		if (binding != null && binding.client == client)
		{
			service.interrupt(binding, "LOGOUT");
		}
	}

	public static void died(Creature actor)
	{
		final HumanRouteService service = ACTIVE;
		final Binding binding = service == null ? null : service._binding;
		if (binding != null && binding.player == actor)
		{
			service.interrupt(binding, "DEATH");
		}
	}

	public static void instanceChanging(WorldObject actor)
	{
		final HumanRouteService service = ACTIVE;
		final Binding binding = service == null ? null : service._binding;
		if (binding != null && binding.player == actor)
		{
			service.interrupt(binding, "INSTANCE_CHANGED");
		}
	}

	public static void nativeMove(Creature actor, int previousX, int previousY, int previousZ)
	{
		final HumanRouteService service = ACTIVE;
		final Binding binding = service == null ? null : service._binding;
		final Capture capture = service == null ? null : service._capture;
		if (capture == null || binding == null || binding.player != actor || actor.isTeleporting() || !service.ownerValid(binding, monotonicMillis())) { return; }
		final Point position = position(binding.player);
		if (position.x() != previousX || position.y() != previousY || position.z() != previousZ)
		{
			capture.trace().nativeMove(position);
		}
	}

	public static void water(Creature actor, int zone, boolean entered)
	{
		final HumanRouteService service = ACTIVE;
		final Binding binding = service == null ? null : service._binding;
		final Capture capture = service == null ? null : service._capture;
		if (capture != null && binding != null && binding.player == actor && service.ownerValid(binding, monotonicMillis()) && !actor.isTeleporting())
		{
			capture.trace().water(System.currentTimeMillis(), zone, entered);
		}
	}

	private void interrupt(Binding binding, String reason)
	{
		synchronized (binding)
		{
			if (binding.interruption != null) { return; }
			binding.interruption = reason;
			final Capture capture = _capture;
			if (capture != null) { capture.trace().breakTrace(reason); }
		}
	}

	private synchronized void poll()
	{
		if (_stopping) { return; }
		try
		{
			_mailbox.validate();
			final long wall = System.currentTimeMillis();
			final Phase before = _phase;
			if (_phase != Phase.OFF && wall >= _permit.expires())
			{
				stop("CONSENT_EXPIRED");
			}
			else if (_phase != Phase.OFF && !_permit.equals(_mailbox.permit(wall)))
			{
				stop("PERMIT_CHANGED");
			}
			if (_queue == null)
			{
				_geoGeneration = GeoEngine.getInstance().generation();
				_fingerprint = PhantomGeoTrainer.fingerprint(GeoEngineConfig.GEODATA_PATH, () -> _stopping);
				if (!geoCurrent()) { throw new IOException("GeoData changed during fingerprinting."); }
				_queue = HumanRouteScenes.read(_mailbox, _fingerprint);
				_mailbox.announce();
				_mailbox.status(describe(current()));
			}
			final Request request = _mailbox.receive(System.currentTimeMillis());
			if (request != null && request.action() == HumanRouteMailbox.Action.STOP)
			{
				respond(request, stop("OWNER_STOP"));
				release();
				return;
			}
			advance();
			if (request != null)
			{
				respond(request, dispatch(request));
			}
			else if (_phase != before)
			{
				_mailbox.status(describe(current()));
			}
			if (_phase == Phase.OFF && !_mailbox.stopPending()) { release(); }
		}
		catch (IOException | RuntimeException failure)
		{
			failClosed();
		}
	}

	private Receipt dispatch(Request request) throws IOException
	{
		if (request.deadline() <= System.currentTimeMillis()) { return result("REJECTED", "REQUEST_EXPIRED"); }
		return switch (request.action())
		{
			case STATUS -> current();
			case ARM -> request.scene().equals("-") ? arm(request) : result("REJECTED", "UNEXPECTED_SCENE");
			case ARRIVED, SKIP -> finishCommand(request);
			case NEXT -> next(request);
			case STOP -> stop("OWNER_STOP");
		};
	}

	private Receipt arm(Request request) throws IOException
	{
		if (_phase != Phase.WAITING_LOGIN || _used) { return result("REJECTED", "LEASE_UNAVAILABLE"); }
		final Player player = World.getInstance().getPlayer(_permit.objectId());
		if (player == null || !player.isOnline()) { return result("WAITING_LOGIN", "-"); }
		final GameClient client = player.getClient();
		if (client == null || !_permit.account().equals(client.getAccountName()) || !clientMatches(player, client)) { return result("REJECTED", "IDENTITY_MISMATCH"); }
		if (!idle(player)) { return result("REJECTED", "PLAYER_BUSY"); }
		checkExportBudget();
		_binding = new Binding(player, client, monotonicMillis() + CONSENT_MILLIS);
		_used = true;
		return beginNext(request);
	}

	private Receipt next(Request request) throws IOException
	{
		if ((_phase != Phase.SEALED && _phase != Phase.SKIPPED) || !_lastScene.equals(request.scene()))
		{
			return result("REJECTED", "SEALED_SCENE_REQUIRED");
		}
		checkExportBudget();
		return beginNext(request);
	}

	private Receipt beginNext(Request request) throws IOException
	{
		final Binding binding = _binding;
		if (!ownerValid(binding, monotonicMillis())) { return stop("LEASE_LOST"); }
		if (!idle(binding.player)) { return _phase == Phase.WAITING_LOGIN ? stop("PLAYER_BUSY") : result("REJECTED", "PLAYER_BUSY"); }
		for (Scene scene : _queue.ready())
		{
			if (_attempted.contains(scene.id())) { continue; }
			if (request.deadline() <= System.currentTimeMillis()) { return stop("REQUEST_EXPIRED"); }
			if (!HumanRouteScenes.safeLanding(scene.start(), 24) || !HumanRouteScenes.knownTarget(scene.target(), scene.height()))
			{
				writeSkipped(scene, "SKIP_UNSAFE");
				_attempted.add(scene.id());
				continue;
			}
			final Integer height = HumanRouteScenes.surface(scene.start());
			if (height == null) { return stop("START_GEODATA_LOST"); }
			final Point landing = new Point(scene.start().x(), scene.start().y(), height + 5, 0);
			if (landing.within(scene.target(), scene.radius(), scene.height()))
			{
				writeSkipped(scene, "START_OVERLAPS_TARGET");
				_attempted.add(scene.id());
				continue;
			}
			_mailbox.validate();
			if (!_permit.equals(_mailbox.permit(System.currentTimeMillis()))) { return stop("PERMIT_CHANGED"); }
			final Player player = binding.player;
			synchronized (player)
			{
				if (!ownerValid(binding, monotonicMillis()) || !idle(player)) { return stop("PLAYER_CHANGED"); }
				if (request.deadline() <= System.currentTimeMillis()) { return stop("REQUEST_EXPIRED"); }
				// The private local STOP marker is the final admission fence, including time spent waiting for the player monitor.
				if (_mailbox.stopPending()) { return stop("OWNER_STOP_PENDING"); }
				_attempted.add(scene.id());
				_landing = new Landing(scene, landing, monotonicMillis() + LANDING_MILLIS);
				_phase = Phase.TELEPORT_PENDING;
				_lastResult = "TELEPORT_PENDING";
				_lastDetail = "-";
				binding.issuingTeleport = Thread.currentThread();
				try { player.teleToLocation(new Location(landing.x(), landing.y(), height, player.getHeading(), 0), false); }
				finally { binding.issuingTeleport = null; }
			}
			return result("TELEPORT_PENDING", "-");
		}
		return stop(_queue.needsLandmark() > 0 ? "NEEDS_LANDMARK" : "QUEUE_EMPTY");
	}

	private void advance() throws IOException
	{
		final Binding binding = _binding;
		if (binding == null) { return; }
		final long now = monotonicMillis();
		if (!ownerValid(binding, now))
		{
			stop(binding.interruption != null ? binding.interruption : !geoCurrent() ? "GEODATA_CHANGED" : "LEASE_OR_PLAYER_LOST");
			return;
		}
		final Player player = binding.player;
		if (_landing != null)
		{
			checkLanding(player, now);
			return;
		}
		final Capture capture = _capture;
		if (capture == null) { return; }
		final String interrupted = capture.trace().interrupted();
		if (interrupted != null || player.isTeleporting() || now - capture.started() >= SCENE_MILLIS)
		{
			stop(interrupted != null ? interrupted : (player.isTeleporting() ? "UNEXPECTED_TELEPORT" : "SCENE_TIMEOUT"));
			return;
		}
		final Point observed = position(player);
		if (!ownerValid(binding, now) || observed.instance() != 0) { stop("PLAYER_CHANGED"); return; }
		final Integer surface = HumanRouteScenes.surface(observed);
		if (capture.trace().observe(now, System.currentTimeMillis(), observed, surface, player.isInsideZone(ZoneId.WATER), player.isMoving()))
		{
			_lastObserved = observed;
		}
		if (now >= _nextCheckpoint)
		{
			checkpoint(capture);
			_nextCheckpoint = now + CHECKPOINT_MILLIS;
		}
	}

	private void checkLanding(Player player, long now) throws IOException
	{
		final Landing landing = _landing;
		if (now >= landing.deadline()) { stop("LANDING_TIMEOUT"); return; }
		Point actual = null;
		synchronized (player)
		{
			if (!player.isTeleporting() && player.isSpawned()) { actual = position(player); }
		}
		if (actual == null) { return; }
		final Scene scene = landing.scene();
		if (!actual.within(landing.expected(), 2, 12) || actual.within(scene.target(), scene.radius(), scene.height()) || !HumanRouteScenes.safeLanding(actual, 24)
			|| player.isInsideZone(ZoneId.WATER) || !ownerValid(_binding, now))
		{
			stop("LANDING_INVALID");
			return;
		}
		final Capture capture = new Capture(scene, new HumanRouteTrace(actual, scene.target(), scene.radius(), scene.height(), now), now);
		_lastObserved = actual;
		checkpoint(capture);
		_nextCheckpoint = now + CHECKPOINT_MILLIS;
		_capture = capture;
		_landing = null;
		_phase = Phase.RECORDING;
		_lastResult = "RECORDING";
		_lastDetail = "-";
	}

	private Receipt finishCommand(Request request) throws IOException
	{
		final Capture capture = _capture;
		if (_phase != Phase.RECORDING || capture == null || !capture.scene().id().equals(request.scene()))
		{
			return result("REJECTED", "ACTIVE_SCENE_MISMATCH");
		}
		final Binding binding = _binding;
		if (!ownerValid(binding, monotonicMillis()) || binding.player.isTeleporting()) { return stop("LEASE_OR_PLAYER_LOST"); }
		final Point end = position(binding.player);
		if (!ownerValid(binding, monotonicMillis()) || capture.trace().interrupted() != null) { return stop("PLAYER_CHANGED"); }
		final boolean skip = request.action() == HumanRouteMailbox.Action.SKIP;
		final boolean knownTarget = !skip && HumanRouteScenes.knownTarget(end, capture.scene().height());
		final Report report;
		synchronized (binding)
		{
			if (!ownerValid(binding, monotonicMillis()) || binding.player.isTeleporting())
			{
				report = null;
			}
			else
			{
				final boolean arrived = knownTarget && capture.trace().arrived(end);
				final String outcome = skip ? "SKIPPED" : arrived ? "ARRIVED" : "PARTIAL";
				final String reason = skip ? "OWNER_SKIPPED" : arrived ? "TARGET_REACHED" : "NOT_REACHED";
				report = capture.trace().finish(end, outcome, reason);
			}
		}
		if (report == null) { return stop(!geoCurrent() ? "GEODATA_CHANGED" : "LEASE_OR_PLAYER_LOST"); }
		publish(capture, report);
		if (report.outcome().equals("INTERRUPTED"))
		{
			_binding = null;
			_phase = Phase.OFF;
		}
		else
		{
			_phase = skip ? Phase.SKIPPED : Phase.SEALED;
		}
		return new Receipt(report.outcome(), report.reason(), report);
	}

	private Receipt stop(String reason) throws IOException
	{
		if (_phase == Phase.OFF) { return current(); }
		final Capture capture = _capture;
		_phase = Phase.OFF;
		_used = true;
		_binding = null;
		_landing = null;
		if (capture != null)
		{
			capture.trace().breakTrace(reason);
			final Report report = capture.trace().finish(_lastObserved, "INTERRUPTED", reason);
			publish(capture, report);
			return new Receipt(report.outcome(), report.reason(), report);
		}
		_lastResult = "OFF";
		_lastDetail = reason;
		return result("OFF", reason);
	}

	private void checkpoint(Capture capture) throws IOException
	{
		final Report report = capture.trace().report(_lastObserved, "UNSEALED", "PROCESS_LOST_IF_UNSEALED");
		_mailbox.write(path(capture.scene(), ".partial"), envelope(capture.scene(), report.body()));
	}

	private void publish(Capture capture, Report report) throws IOException
	{
		_capture = null;
		_mailbox.write(path(capture.scene(), ".trace"), envelope(capture.scene(), report.body()));
		_sealed++;
		_lastScene = capture.scene().id();
		_lastReport = report;
		_lastResult = report.outcome();
		_lastDetail = report.reason();
		Files.deleteIfExists(path(capture.scene(), ".partial"));
	}

	private void writeSkipped(Scene scene, String reason) throws IOException
	{
		_mailbox.write(path(scene, ".trace"), envelope(scene, "type=HUMAN_DEMONSTRATED\nresult=SKIPPED\nreason=" + reason + "\nsamples=0\n"));
	}

	private Path path(Scene scene, String suffix)
	{
		return _mailbox.evidence().resolve(_run + "-" + scene.id() + suffix);
	}

	private String envelope(Scene scene, String body)
	{
		final String payload = "schema=1\nrun=" + _run + "\nscene=" + scene.id() + "\nincarnation=" + _mailbox.incarnation() + "\ngeodata=" + _fingerprint + "\n" + body;
		return "sha256=" + HumanRouteScenes.sha256(payload) + "\n" + payload;
	}

	private void checkExportBudget() throws IOException
	{
		try (var files = Files.list(_mailbox.evidence()))
		{
			long bytes = 0;
			final var existing = files.limit(MAX_EXPORTS + 1).toList();
			if (existing.size() > MAX_EXPORTS - 2 * _queue.ready().size()) { throw new IOException("Evidence count limit."); }
			for (Path file : existing)
			{
				_mailbox.check(file, false);
				final long size = Files.size(file);
				if (size > MAX_EXPORT_BYTES - bytes) { throw new IOException("Evidence byte limit."); }
				bytes += size;
			}
			if (bytes > MAX_EXPORT_BYTES - 2_000_000) { throw new IOException("Evidence byte reserve."); }
		}
	}

	private void respond(Request request, Receipt result) throws IOException
	{
		_mailbox.status(describe(current()));
		_mailbox.reply(request, describe(result));
	}

	private Receipt current() { return new Receipt(_lastResult, _lastDetail, _lastReport); }

	private String describe(Receipt receipt)
	{
		final StringBuilder result = new StringBuilder("phase=").append(_phase).append("\nresult=").append(receipt.result()).append("\ndetail=").append(receipt.detail()).append("\nsaved=").append(_sealed);
		result.append("\nremaining=").append(_queue == null ? 0 : _queue.ready().size() - _attempted.size());
		result.append("\nneeds_landmark=").append(_queue == null ? 0 : _queue.needsLandmark());
		final Capture capture = _capture;
		if (capture != null)
		{
			result.append("\nscene=").append(capture.scene().id()).append("\nstart_landmark=").append(capture.scene().startLandmark()).append("\ndirections=").append(capture.scene().directions());
			result.append("\nselection_reason=").append(capture.scene().reason());
		}
		if (_lastScene != null) { result.append("\nsealed_scene=").append(_lastScene); }
		if (receipt.report() != null)
		{
			result.append("\nsamples=").append(receipt.report().samples()).append("\ndrops=").append(receipt.report().drops()).append("\nwater=").append(receipt.report().water());
		}
		return result.append('\n').toString();
	}

	private void failClosed()
	{
		final Capture capture = _capture;
		_binding = null;
		_landing = null;
		_capture = null;
		_phase = Phase.OFF;
		_stopping = true;
		if (_worker != null) { _worker.cancel(false); }
		try
		{
			_mailbox.validate();
			if (capture != null)
			{
				capture.trace().breakTrace("OPERATOR_OR_EXPORT_FAILURE");
				publish(capture, capture.trace().finish(_lastObserved, "INTERRUPTED", "OPERATOR_OR_EXPORT_FAILURE"));
			}
			_mailbox.status(describe(new Receipt("OFF", "OPERATOR_OR_EXPORT_FAILURE", _lastReport)));
		}
		catch (IOException | RuntimeException failure)
		{
			// An unsafe or unavailable mailbox must not be used for retries. The last checkpoint stays unsealed.
		}
		finally { release(); }
		LOGGER.warning("Human route demonstration closed: operator boundary or export failure.");
	}

	private synchronized void drain()
	{
		if (_released) { return; }
		try
		{
			_mailbox.validate();
			_mailbox.status(describe(stop("SERVER_SHUTDOWN")));
		}
		catch (IOException | RuntimeException failure)
		{
			_binding = null;
			_capture = null;
			LOGGER.warning("Human route shutdown could not seal; last checkpoint remains unsealed.");
		}
		finally { release(); }
	}

	private void release()
	{
		if (_released) { return; }
		_released = true;
		_stopping = true;
		if (_worker != null) { _worker.cancel(false); }
		closeMailbox(_mailbox);
	}

	private static void closeMailbox(HumanRouteMailbox mailbox)
	{
		if (mailbox == null) { return; }
		try { mailbox.close(); }
		catch (IOException failure) { LOGGER.warning("Human route mailbox lock could not be closed."); }
	}

	private static Receipt result(String status, String detail) { return new Receipt(status, detail, null); }
	private static long monotonicMillis() { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()); }
	private static Point position(Player actor) { return new Point(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId()); }

	private boolean ownerValid(Binding binding, long now)
	{
		return !_stopping && binding != null && _binding == binding && binding.interruption == null && now < binding.deadline && System.currentTimeMillis() < _permit.expires()
			&& binding.player.getInstanceId() == 0 && !binding.player.isDead() && World.getInstance().getPlayer(_permit.objectId()) == binding.player
			&& !binding.player.isAutoPlaying() && !binding.player.isInVehicle() && !binding.player.isFlying() && !binding.player.isMounted()
			&& _permit.account().equals(binding.client.getAccountName()) && clientMatches(binding.player, binding.client) && geoCurrent();
	}

	private boolean geoCurrent() { return GeoEngine.getInstance().generation() == _geoGeneration; }

	private static boolean clientMatches(Player player, GameClient client)
	{
		return player.isOnline() && !player.isInOfflineMode() && player.getAccessLevel().getLevel() == 0 && player.getClient() == client
			&& !client.isDetached() && client.getPlayer() == player && client.getConnectionState() == ConnectionState.IN_GAME;
	}

	private static boolean idle(Player player)
	{
		return player.isSpawned() && !player.isAlikeDead() && !player.isTeleporting() && !player.isMoving() && !player.isInCombat() && !player.isAttackingNow()
			&& !player.isCastingNow() && !player.isInStoreMode() && !player.isSitting() && !player.isFlying() && !player.isMounted() && !player.isInOlympiadMode()
			&& !player.isAutoPlaying() && !player.isInVehicle() && !player.hasSummon() && !player.isInDuel() && !player.isInsideZone(ZoneId.WATER) && !player.isInsideZone(ZoneId.SIEGE) && player.getInstanceId() == 0;
	}
}

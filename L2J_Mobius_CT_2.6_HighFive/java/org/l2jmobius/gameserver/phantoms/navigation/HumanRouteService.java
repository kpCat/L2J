package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.zone.ZoneId;
import org.l2jmobius.gameserver.network.ConnectionState;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteMailbox.Permit;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteMailbox.Request;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteScenes.Scene;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteTrace.Point;
import org.l2jmobius.gameserver.phantoms.navigation.HumanRouteTrace.Report;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.BudgetProof;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind;

/** Private operator mailbox, with at most one consenting real client and one passage at a time. */
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
	private record Capture(UUID id, Scene scene, Binding binding, HumanRouteTrace trace, long started) { }
	private record Landing(Scene scene, Point expected, long deadline) { }
	private record Receipt(String result, String detail, Report report) { }
	private record Consent(Permit permit, long deadline)
	{
		static Consent from(Permit permit)
		{
			return new Consent(permit, monotonicMillis() + Math.min(CONSENT_MILLIS, permit.expires() - System.currentTimeMillis()));
		}
	}

	private static final class Binding
	{
		final Player player;
		final GameClient client;
		volatile Consent consent;
		volatile Thread issuingTeleport;
		volatile String interruption;

		Binding(Player player, GameClient client, Permit permit)
		{
			this.player = player;
			this.client = client;
			consent = Consent.from(permit);
		}
	}

	private final HumanRouteMailbox _mailbox;
	private final HumanRouteFarmRefusals _farmRefusals;
	private final List<Long> _farmProfiles;
	private HumanRouteScenes.Queue _queue = new HumanRouteScenes.Queue(List.of(), 0);
	private final String _fingerprint;
	private final long _geoGeneration;
	private final Set<String> _attempted = new HashSet<>();
	private volatile boolean _stopping;
	private volatile Binding _binding;
	private volatile Capture _capture;
	private volatile Point _lastObserved;
	private ScheduledFuture<?> _worker;
	private Landing _landing;
	private UUID _passageId;
	private String _lastScene;
	private String _lastResult = "WAITING_LOGIN";
	private String _lastDetail = "-";
	private Report _lastReport;
	private Phase _phase = Phase.WAITING_LOGIN;
	private long _nextCheckpoint;
	private int _sealed;
	private boolean _mailboxFault;

	private HumanRouteService(HumanRouteMailbox mailbox) throws IOException
	{
		_mailbox = mailbox;
		_farmRefusals = new HumanRouteFarmRefusals(mailbox.incarnation());
		_farmProfiles = farmProfiles(System.getProperty("phantom.humanRoute.observeFarmProfiles", ""));
		_geoGeneration = GeoEngine.getInstance().generation();
		_fingerprint = PhantomGeoTrainer.fingerprint(GeoEngineConfig.GEODATA_PATH, () -> _stopping);
		if (!geoCurrent()) { throw new IOException("GeoData changed during fingerprinting."); }
	}

	static List<Long> farmProfiles(String csv)
	{
		if (csv.isEmpty()) { return List.of(); }
		if (!csv.matches("[1-9][0-9]{0,8}(,[1-9][0-9]{0,8}){0,3}")) { throw new IllegalArgumentException("Invalid farm observation profiles."); }
		return Arrays.stream(csv.split(",")).map(Long::valueOf).distinct().toList();
	}

	/** Called by the farm owner only after consuming the matching bounded native search result. */
	public static void observeFarmRefusal(long profileId, Player player, long epoch, long requestId, long geoGeneration, long zoneGeneration, BudgetProof proof)
	{
		final HumanRouteService service = ACTIVE;
		if (service == null || service._stopping) { return; }
		try { service.recordFarmRefusal(profileId, player, epoch, requestId, geoGeneration, zoneGeneration, proof); }
		catch (RuntimeException failure) { LOGGER.warning("Human route demonstration could not record a farm refusal."); }
	}

	private void recordFarmRefusal(long profileId, Player player, long epoch, long requestId, long geoGeneration, long zoneGeneration, BudgetProof proof)
	{
		final var origin = proof.origin();
		final var target = proof.submittedDestination();
		final long now = System.currentTimeMillis();
		if (geoGeneration != _geoGeneration || !geoCurrent() || zoneGeneration < 0
			|| ZoneManager.getInstance().loadedGeneration() != zoneGeneration || requestId <= 0 || epoch <= 0
			|| (proof.kind() != LimitKind.SEARCH_LIMIT && proof.kind() != LimitKind.NO_PATH_WITHIN_SEARCH)
			|| origin == null || target == null || !target.equals(proof.destination()) || origin.instanceId() != 0 || target.instanceId() != 0
			|| player.getInstanceId() != 0 || origin.x() != player.getX() || origin.y() != player.getY() || origin.z() != player.getZ()
			|| currentFarmEpoch(player) != epoch) { return; }
		final int objectId = player.getObjectId();
		final Point start = new Point(origin.x(), origin.y(), origin.z(), 0);
		final Point end = new Point(target.x(), target.y(), target.z(), 0);
		_farmRefusals.observe(new HumanRouteFarmRefusals.Evidence(_mailbox.incarnation(), _fingerprint, geoGeneration, zoneGeneration,
			profileId, objectId, epoch, requestId, start, end, now, proof.kind()));
	}

	private boolean sourceCurrent(Scene scene)
	{
		final var source = scene.farmSource();
		if (source == null) { return true; }
		final long now = System.currentTimeMillis();
		final var signed = _farmRefusals.find(scene, now);
		if (signed == null || !geoCurrent()) { return false; }
		final var player = World.getInstance().getPlayer(source.objectId());
		return _farmRefusals.matches(signed, scene, _fingerprint, _geoGeneration, ZoneManager.getInstance().loadedGeneration(),
			currentFarmEpoch(player), now);
	}

	private static long currentFarmEpoch(Player player)
	{
		if (player == null || player.getInstanceId() != 0 || !player.isNativeWorkManaged() || World.getInstance().getPlayer(player.getObjectId()) != player) { return -1; }
		final var owner = player.getNativeWorkOwner();
		return owner != null && owner.player() == player && owner.isCurrent() && !owner.sealed() ? owner.epoch() : -1;
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
			final var service = new HumanRouteService(mailbox);
			mailbox.announce();
			mailbox.status(service.describe(service.current()));
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
		if (capture == null || capture.binding() != binding || binding.player != actor || actor.isTeleporting() || !service.ownerValid(binding, monotonicMillis())) { return; }
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
		if (capture != null && capture.binding() == binding && binding.player == actor && service.ownerValid(binding, monotonicMillis()) && !actor.isTeleporting())
		{
			capture.trace().water(System.currentTimeMillis(), zone, entered);
		}
	}

	private void interrupt(Binding binding, String reason)
	{
		synchronized (binding)
		{
			if (_binding != binding || binding.interruption != null) { return; }
			binding.interruption = reason;
			final Capture capture = _capture;
			if (capture != null && capture.binding() == binding) { capture.trace().breakTrace(reason); }
		}
	}

	private synchronized void poll()
	{
		if (_stopping) { return; }
		Request request = null;
		try
		{
			_mailbox.validate();
			final long wall = System.currentTimeMillis();
			final Phase before = _phase;
			request = _mailbox.receive(wall);
			if (request != null && request.action() == HumanRouteMailbox.Action.STOP)
			{
				respond(request, stop("OWNER_STOP"));
				_mailboxFault = false;
				return;
			}
			advance();
			if (request != null)
			{
				respond(request, dispatch(request));
			}
			else if (_phase != before || _mailboxFault)
			{
				_mailbox.status(describe(current()));
			}
			_mailboxFault = false;
		}
		catch (IOException | RuntimeException failure)
		{
			failClosed();
			if (request != null)
			{
				try
				{
					_mailbox.validate();
					_mailbox.reply(request, describe(result("FAILED", "OPERATOR_OR_EXPORT_FAILURE")));
				}
				catch (IOException | RuntimeException unavailable)
				{
					// There is no trustworthy reply channel. The last checkpoint remains unsealed.
				}
			}
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
			case RENEW -> renew(request);
			case RELOAD -> reload(request);
		};
	}

	private Receipt arm(Request request) throws IOException
	{
		if (_phase != Phase.WAITING_LOGIN || _binding != null) { return result("REJECTED", "LEASE_UNAVAILABLE"); }
		final Permit permit = readConsent(request);
		if (permit == null) { return result("REJECTED", "INVALID_PERMIT"); }
		final Player player = World.getInstance().getPlayer(permit.objectId());
		if (player == null || !player.isOnline()) { return result("WAITING_LOGIN", "-"); }
		final GameClient client = player.getClient();
		if (client == null || !permit.account().equals(client.getAccountName()) || !clientMatches(player, client)) { return result("REJECTED", "IDENTITY_MISMATCH"); }
		if (!idle(player)) { return result("REJECTED", "PLAYER_BUSY"); }
		checkExportBudget();
		_binding = new Binding(player, client, permit);
		final Receipt receipt = beginNext(request);
		if (_phase == Phase.WAITING_LOGIN) { _binding = null; }
		return receipt;
	}

	private Permit readConsent(Request request)
	{
		try { return _mailbox.permit(request, System.currentTimeMillis()); }
		catch (IOException | IllegalArgumentException failure) { return null; }
	}

	private Receipt renew(Request request)
	{
		if (!request.scene().equals("-")) { return result("REJECTED", "UNEXPECTED_SCENE"); }
		final Binding binding = _binding;
		if (!ownerValid(binding, monotonicMillis())) { return result("REJECTED", "LEASE_UNAVAILABLE"); }
		final Permit permit = readConsent(request);
		if (permit == null || permit.objectId() != binding.consent.permit().objectId() || !permit.account().equals(binding.consent.permit().account()))
		{
			return result("REJECTED", "INVALID_PERMIT");
		}
		synchronized (binding)
		{
			if (!ownerValid(binding, monotonicMillis())) { return result("REJECTED", "LEASE_UNAVAILABLE"); }
			if (request.deadline() <= System.currentTimeMillis() || _mailbox.stopPending()) { return result("REJECTED", "RENEWAL_FENCED"); }
			final Consent renewed = Consent.from(permit);
			if (renewed.deadline() <= binding.consent.deadline()) { return result("REJECTED", "CONSENT_NOT_EXTENDED"); }
			binding.consent = renewed;
		}
		return result("RENEWED", "-");
	}

	private Receipt reload(Request request)
	{
		if (!request.scene().equals("-")) { return result("REJECTED", "UNEXPECTED_SCENE"); }
		if (_landing != null || _capture != null) { return result("REJECTED", "ACTIVE_SCENE"); }
		if (!geoCurrent()) { return result("REJECTED", "GEODATA_CHANGED"); }
		final HumanRouteScenes.Queue staged;
		try { staged = HumanRouteScenes.read(_mailbox, _mailbox.queue(request), _fingerprint); }
		catch (IOException | IllegalArgumentException failure) { return result("REJECTED", "INVALID_QUEUE"); }
		if (!geoCurrent()) { return result("REJECTED", "GEODATA_CHANGED"); }
		if (request.deadline() <= System.currentTimeMillis()) { return result("REJECTED", "REQUEST_EXPIRED"); }
		_queue = staged;
		_attempted.clear();
		return result("QUEUE_LOADED", "-");
	}

	private Receipt next(Request request) throws IOException
	{
		if ((_phase != Phase.SEALED && _phase != Phase.SKIPPED) || !request.scene().equals(_lastScene))
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
		boolean waitingRefusal = false;
		for (Scene scene : _queue.ready())
		{
			if (_attempted.contains(scene.id())) { continue; }
			if (!sourceCurrent(scene)) { waitingRefusal = true; continue; }
			if (request.deadline() <= System.currentTimeMillis()) { return stop("REQUEST_EXPIRED"); }
			if (!HumanRouteScenes.safeLanding(scene.start(), 24) || !HumanRouteScenes.knownTarget(scene.target(), scene.height())
				|| (scene.farmSource() != null && !HumanRouteScenes.knownDryPoint(scene.target(), scene.height())))
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
			final Player player = binding.player;
			synchronized (player)
			{
				if (!sourceCurrent(scene)) { waitingRefusal = true; continue; }
				if (!ownerValid(binding, monotonicMillis()) || !idle(player)) { return stop("PLAYER_CHANGED"); }
				if (request.deadline() <= System.currentTimeMillis()) { return stop("REQUEST_EXPIRED"); }
				// The private local STOP marker is the final admission fence, including time spent waiting for the player monitor.
				if (_mailbox.stopPending()) { return stop("OWNER_STOP_PENDING"); }
				_attempted.add(scene.id());
				_passageId = request.id();
				_landing = new Landing(scene, landing, monotonicMillis() + LANDING_MILLIS);
				_phase = Phase.TELEPORT_PENDING;
				_lastResult = "TELEPORT_PENDING";
				_lastDetail = "-";
				_lastReport = null;
				binding.issuingTeleport = Thread.currentThread();
				try { player.teleToLocation(new Location(landing.x(), landing.y(), height, player.getHeading(), 0), false); }
				finally { binding.issuingTeleport = null; }
			}
			return result("TELEPORT_PENDING", "-");
		}
		if (waitingRefusal) { return result("WAITING_REFUSAL", "NO_CURRENT_FARM_REFUSAL"); }
		return result(_queue.needsLandmark() > 0 ? "NEEDS_LANDMARK" : "QUEUE_EMPTY", "-");
	}

	private void advance() throws IOException
	{
		final Binding binding = _binding;
		if (binding == null) { return; }
		final long now = monotonicMillis();
		if (!ownerValid(binding, now))
		{
			stop(binding.interruption != null ? binding.interruption : !geoCurrent() ? "GEODATA_CHANGED"
				: consentExpired(binding.consent, now) ? "CONSENT_EXPIRED" : "LEASE_OR_PLAYER_LOST");
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
		if (interrupted != null || player.isTeleporting())
		{
			stop(interrupted != null ? interrupted : "UNEXPECTED_TELEPORT");
			return;
		}
		if (now - capture.started() >= SCENE_MILLIS)
		{
			finishPassage(capture, capture.trace().finish(_lastObserved, "PARTIAL", "SCENE_TIMEOUT"));
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
		final Capture capture = new Capture(UUID.randomUUID(), scene, _binding, new HumanRouteTrace(actual, scene.target(), scene.radius(), scene.height(), now), now);
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
			if (request.deadline() <= System.currentTimeMillis() || _mailbox.stopPending()) { return result("REJECTED", "SEAL_FENCED"); }
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
		return finishPassage(capture, report);
	}

	private Receipt finishPassage(Capture capture, Report report) throws IOException
	{
		publish(capture, report);
		if (report.outcome().equals("INTERRUPTED"))
		{
			releaseBinding();
		}
		else
		{
			_phase = report.outcome().equals("SKIPPED") ? Phase.SKIPPED : Phase.SEALED;
		}
		return new Receipt(report.outcome(), report.reason(), report);
	}

	private Receipt stop(String reason) throws IOException
	{
		if (_phase == Phase.OFF) { return current(); }
		final Capture capture = _capture;
		releaseBinding();
		if (capture != null)
		{
			capture.trace().breakTrace(reason);
			final Report report = capture.trace().finish(_lastObserved, "INTERRUPTED", reason);
			publish(capture, report);
			return new Receipt(report.outcome(), report.reason(), report);
		}
		_lastResult = "WAITING_LOGIN";
		_lastDetail = reason;
		_lastReport = null;
		return current();
	}

	private void releaseBinding()
	{
		_binding = null;
		_capture = null;
		_landing = null;
		_attempted.clear();
		_phase = Phase.WAITING_LOGIN;
	}

	private void checkpoint(Capture capture) throws IOException
	{
		final Report report = capture.trace().report(_lastObserved, "UNSEALED", "PROCESS_LOST_IF_UNSEALED");
		_mailbox.write(path(capture.id(), capture.scene(), ".partial"), envelope(capture.id(), capture.scene(), report.body()));
	}

	private void publish(Capture capture, Report report) throws IOException
	{
		_capture = null;
		_mailbox.write(path(capture.id(), capture.scene(), ".trace"), envelope(capture.id(), capture.scene(), report.body()));
		_sealed++;
		_lastScene = capture.scene().id();
		_lastReport = report;
		_lastResult = report.outcome();
		_lastDetail = report.reason();
		try { Files.deleteIfExists(path(capture.id(), capture.scene(), ".partial")); }
		catch (IOException failure) { LOGGER.warning("Human route sealed; its previous checkpoint could not be removed."); }
	}

	private void writeSkipped(Scene scene, String reason) throws IOException
	{
		final UUID id = UUID.randomUUID();
		_mailbox.write(path(id, scene, ".trace"), envelope(id, scene, "type=HUMAN_DEMONSTRATED\nresult=SKIPPED\nreason=" + reason + "\nsamples=0\n"));
	}

	private Path path(UUID id, Scene scene, String suffix)
	{
		return _mailbox.evidence().resolve(id + "-" + scene.id() + suffix);
	}

	private String envelope(UUID id, Scene scene, String body)
	{
		final String payload = "schema=1\nrun=" + id + "\nscene=" + scene.id() + "\nincarnation=" + _mailbox.incarnation() + "\ngeodata=" + _fingerprint
			+ "\nselection_reason=" + scene.reason() + "\n" + body;
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
		_mailbox.reply(request, describe(result) + (request.action() == HumanRouteMailbox.Action.STATUS && _phase != Phase.OFF ? farmStatus() : ""));
	}

	private String farmStatus()
	{
		final var text = new StringBuilder();
		for (long profileId : _farmProfiles)
		{
			final var actor = PhantomSystem.operatorMaterialization(profileId).orElse(null);
			if (actor == null || !actor.worldPresent() || !actor.identityLeaseRetained()) { continue; }
			final int objectId = actor.characterObjectId();
			final var player = World.getInstance().getPlayer(objectId);
			if (player == null || player.getClient() != null || currentFarmEpoch(player) != actor.materializedAtNanos()) { continue; }
			final var owner = player.getNativeWorkOwner();
			final var evidence = owner == null ? null : owner.evidence();
			if (evidence == null || !evidence.matches(objectId, actor.materializedAtNanos())) { continue; }
			final PlayerNativeEvidence.Snapshot progress;
			final PlayerNativeEvidence.TargetReceipts receipts;
			synchronized (evidence)
			{
				progress = evidence.currentSnapshot();
				receipts = evidence.targetReceipts();
			}
			final long exp = player.getExp(), sp = player.getSp();
			if (currentFarmEpoch(player) != actor.materializedAtNanos() || player.getNativeWorkOwner() != owner) { continue; }
			text.append("farm_").append(profileId).append("=object:").append(objectId).append(",epoch:").append(actor.materializedAtNanos())
				.append(",instance:").append(player.getInstanceId()).append(",admission:").append(actor.actionAdmissionOpen())
				.append(",moving:").append(player.isMoving()).append(",attacking:").append(player.isAttackingNow())
				.append(",casting:").append(player.isCastingNow()).append(",target:").append(player.getTargetId()).append(",phase:").append(progress.phase())
				.append(",sampleNanos:").append(progress.sampleNanos()).append(",damage:").append(progress.damageSequence()).append(",kill:").append(progress.killSequence())
				.append(",reward:").append(progress.rewardSequence()).append(",cycle:").append(progress.farmCycleSequence()).append(",overflow:").append(progress.overflow())
				.append(",exp:").append(exp).append(",sp:").append(sp).append(",expGained:").append(progress.expGained()).append(",spGained:").append(progress.spGained())
				.append(",killed:").append(targetIdentity(receipts.killTarget())).append(",rewarded:").append(targetIdentity(receipts.rewardTarget()))
				.append(",settled:").append(targetIdentity(receipts.settledTarget())).append(",settledReward:").append(receipts.settledRewardSequence()).append('\n');
		}
		return text.toString();
	}

	private static String targetIdentity(PlayerNativeEvidence.Target target)
	{
		return target == null ? "-" : target.objectId() + "/" + target.instanceId() + "/" + target.spawnGeneration();
	}

	private Receipt current() { return new Receipt(_lastResult, _lastDetail, _lastReport); }

	private String describe(Receipt receipt)
	{
		final StringBuilder result = new StringBuilder("phase=").append(_phase).append("\nresult=").append(receipt.result()).append("\ndetail=").append(receipt.detail()).append("\nsaved=").append(_sealed);
		if (_passageId != null) { result.append("\npassage_id=").append(_passageId); }
		result.append("\nremaining=").append(_queue.ready().size() - _attempted.size());
		result.append("\nneeds_landmark=").append(_queue.needsLandmark());
		result.append("\nconfirmed_runtime=").append(_phase == Phase.OFF ? 0 : _queue.ready().stream()
			.filter(scene -> scene.farmSource() != null && !_attempted.contains(scene.id()) && sourceCurrent(scene)).count());
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
		releaseBinding();
		_lastResult = "WAITING_LOGIN";
		_lastDetail = "OPERATOR_OR_EXPORT_FAILURE";
		_lastReport = null;
		if (_mailboxFault) { return; }
		_mailboxFault = true;
		try
		{
			_mailbox.validate();
			_mailbox.status(describe(current()));
		}
		catch (IOException | RuntimeException failure)
		{
			// The next poll validates the mailbox again. The last checkpoint stays unsealed.
		}
		LOGGER.warning("Human route consent released: operator boundary or export failure.");
	}

	private synchronized void drain()
	{
		try
		{
			_mailbox.validate();
			stop("SERVER_SHUTDOWN");
			_phase = Phase.OFF;
			_mailbox.status(describe(result("OFF", "SERVER_SHUTDOWN")));
		}
		catch (IOException | RuntimeException failure)
		{
			_binding = null;
			_capture = null;
			LOGGER.warning("Human route shutdown could not seal; last checkpoint remains unsealed.");
		}
		finally { closeMailbox(_mailbox); }
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
		if (_stopping || binding == null || _binding != binding || binding.interruption != null) { return false; }
		final Consent consent = binding.consent;
		return !consentExpired(consent, now) && binding.player.getInstanceId() == 0 && !binding.player.isDead()
			&& World.getInstance().getPlayer(consent.permit().objectId()) == binding.player
			&& !binding.player.isAutoPlaying() && !binding.player.isInVehicle() && !binding.player.isFlying() && !binding.player.isMounted()
			&& consent.permit().account().equals(binding.client.getAccountName()) && clientMatches(binding.player, binding.client) && geoCurrent();
	}

	private static boolean consentExpired(Consent consent, long now)
	{
		return now >= consent.deadline() || System.currentTimeMillis() >= consent.permit().expires();
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

package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.config.GeoEngineConfig;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService.SubmissionStatus;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Lifecycle owner of the route writer and optional headless GeoData probes. */
public final class PhantomGeoTrainer
{
	private static final Logger LOGGER = Logger.getLogger(PhantomGeoTrainer.class.getName());
	private static final Path CONFIG = Path.of("config/Custom/PhantomGeoTrainer.ini");
	private static final Path SCOUT_COMMISSION_REQUEST = CONFIG.resolveSibling("PhantomGeoScoutCommission.once");
	private static final Path LEARNED = Path.of("data/phantoms/travel/learned");
	private static final String MANIFEST = "{\"schema\":1,\"format\":\"phantom-directed-leg-tsv\"}";
	private static final int[][] OFFSETS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
	private static final int MAX_ANCHORS = 2048;
	private static final int PROBE_DISTANCE = 2400;
	static final int TICK_MILLIS = 200;
	private static final long MINUTE_NANOS = TimeUnit.MINUTES.toNanos(1);

	record Settings(boolean learning, boolean trainer, int workers, int probesPerMinute, int saveSeconds, Path path, PhantomPhysicalGeoScout.Settings scouts)
	{
		private static Settings read(Path datapack) throws IOException
		{
			final var ini = new Properties();
			if (Files.exists(CONFIG))
			{
				try (var input = Files.newBufferedReader(CONFIG, StandardCharsets.UTF_8)) { ini.load(input); }
			}
			final Path configured = Path.of(ini.getProperty("GeoTrainerDataPath", LEARNED.toString()).trim());
			if (!configured.equals(configured.normalize()) || (!configured.isAbsolute() && !configured.equals(LEARNED)))
			{
				throw new IllegalArgumentException("GeoTrainerDataPath must be the default relative path or an absolute checkout path.");
			}
			final boolean learning = flag(ini, "EnableRouteLearning", true);
			final var scouts = learning && flag(ini, "EnablePhysicalGeoScouts", false) ? readScoutSettings(ini) : null;
			return new Settings(learning, flag(ini, "EnableSuperGeoTrainer", false),
				number(ini, "GeoTrainerMaxWorkers", 1, 1, 2), number(ini, "GeoTrainerMaxProbesPerMinute", 20, 1, 100),
				number(ini, "GeoTrainerSaveIntervalSeconds", 120, 15, 3600), datapack.resolve(configured).toAbsolutePath().normalize(), scouts);
		}

		static PhantomPhysicalGeoScout.Settings readScoutSettings(Properties ini) throws IOException
		{
			final var mode = PhantomPhysicalGeoScout.Mode.valueOf(ini.getProperty("PhysicalGeoScoutMode", "LEGACY").trim().toUpperCase(java.util.Locale.ROOT));
			final boolean dedicated = mode == PhantomPhysicalGeoScout.Mode.DEDICATED;
			final var schedule = PhantomPhysicalGeoScout.Schedule.valueOf(ini.getProperty("PhysicalGeoScoutSchedule", dedicated ? "ALWAYS" : "NIGHT").trim().toUpperCase(java.util.Locale.ROOT));
			final int count = number(ini, "PhysicalGeoScoutCount", dedicated ? 32 : 4, 1, dedicated ? 32 : 16);
			final int hours = number(ini, "PhysicalGeoScoutSessionHours", 8, 1, 24);
			final ZoneId zone = ZoneId.of(ini.getProperty("PhysicalGeoScoutTimeZone", "Europe/Chisinau").trim());
			final LocalTime start = LocalTime.parse(ini.getProperty("PhysicalGeoScoutSessionStart", "23:00").trim());
			final int minutes = number(ini, "PhysicalGeoScoutCommissionMinutes", 0, 0, 10);
			// Only the explicitly armed boot can open a daytime session. A later restart uses the night schedule.
			final boolean armed = !dedicated && minutes > 0 && Files.isRegularFile(SCOUT_COMMISSION_REQUEST, LinkOption.NOFOLLOW_LINKS) && Files.deleteIfExists(SCOUT_COMMISSION_REQUEST);
			return new PhantomPhysicalGeoScout.Settings(count, hours, zone, start, armed ? minutes : 0, mode, schedule);
		}

		private static boolean flag(Properties ini, String key, boolean fallback)
		{
			final String value = ini.getProperty(key, String.valueOf(fallback)).trim();
			if ("true".equalsIgnoreCase(value)) { return true; }
			if ("false".equalsIgnoreCase(value)) { return false; }
			throw new IllegalArgumentException(key + " must be True or False.");
		}

		private static int number(Properties ini, String key, int fallback, int min, int max)
		{
			final int value = Integer.parseInt(ini.getProperty(key, String.valueOf(fallback)).trim());
			if (value < min || value > max) { throw new IllegalArgumentException(key + " must be in " + min + ".." + max); }
			return value;
		}

	}

	static final class ProbePacing
	{
		private final long _spacing;
		private long _next;
		private boolean _armed;

		ProbePacing(int probesPerMinute)
		{
			_spacing = (MINUTE_NANOS + probesPerMinute - 1) / probesPerMinute;
		}

		boolean take(long now, boolean idle)
		{
			if (!idle || (_armed && now - _next < 0)) { return false; }
			deferAfter(now);
			return true;
		}

		void deferAfter(long now)
		{
			_armed = true;
			_next = now + _spacing;
		}
	}

	private record Probe(long profile, long request) { }

	private final Settings _settings;
	private final PhantomNavigationService _navigation;
	private final PhantomTopologyQuery _topology;
	private final ProbePacing _pacing;
	private final ScheduledThreadPoolExecutor _writer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("PhantomGeoTrainer").factory());
	private final List<Probe> _active = new ArrayList<>(2);
	private volatile PhantomRouteLearning _memory;
	private volatile PhantomPhysicalGeoScout _scout;
	private volatile PhantomDedicatedScoutRegions _dedicatedRegions;
	private volatile boolean _stopping;
	private List<PhantomNavigationPoint> _anchors = List.of();
	private String _anchorsFingerprint;
	private long _nextSave;
	private long _nextReport;
	private long _nextDedicatedReport;
	private int _cursor;
	private int _savedCursor = -1;
	private final ArrayDeque<Long> _recentAccepted = new ArrayDeque<>(100);
	private long _attempts;
	private long _accepted;
	private long _completed;
	private long _routes;

	static PhantomGeoTrainer start(Path datapack, PhantomNavigationService navigation, PhantomTopologyQuery topology)
	{
		try
		{
			final var settings = Settings.read(datapack);
			if (!settings.learning() && !settings.trainer()) { return null; }
			return new PhantomGeoTrainer(settings, navigation, topology);
		}
		catch (IOException | RuntimeException ex)
		{
			LOGGER.log(Level.WARNING, "Phantom route learning configuration disabled", ex);
			return null;
		}
	}

	private PhantomGeoTrainer(Settings settings, PhantomNavigationService navigation, PhantomTopologyQuery topology)
	{
		_settings = settings;
		_navigation = navigation;
		_topology = topology;
		_pacing = new ProbePacing(settings.probesPerMinute());
		_writer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
		_writer.execute(this::initialize);
		_writer.scheduleWithFixedDelay(this::tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
	}

	PhantomRouteLearning memory()
	{
		return _stopping || !_settings.learning() ? null : _memory;
	}

	PhantomPhysicalGeoScout installPhysicalScout(java.util.function.Function<PhantomPhysicalGeoScout.Settings, PhantomPhysicalGeoScout> factory)
	{
		if (_settings.scouts() != null && _settings.learning()) { _scout = factory.apply(_settings.scouts()); }
		if (_scout != null && _dedicatedRegions != null) { _scout.installRegions(_dedicatedRegions); }
		return _scout;
	}

	private void initialize()
	{
		try
		{
			final Path root = checkoutPath(_settings.path());
			LOGGER.info("Phantom route learning path=" + root + ", Git-managed layout and manifest verified.");
			final String fingerprint = fingerprint(GeoEngineConfig.GEODATA_PATH, () -> _stopping);
			if (_stopping) { return; }
			_memory = PhantomRouteLearning.open(root, fingerprint);
			if (_settings.scouts() != null && _settings.scouts().mode() == PhantomPhysicalGeoScout.Mode.DEDICATED)
			{
				_dedicatedRegions = PhantomDedicatedScoutRegions.open(_memory);
				final var scout = _scout;
				if (scout != null) { scout.installRegions(_dedicatedRegions); }
			}
			if (_settings.scouts() != null) { _memory.openPhysicalWitness(); }
			if (_settings.trainer())
			{
				_anchors = _topology.snapshot().anchors().stream().map(anchor -> anchor.point())
					.filter(point -> point.instanceId() == 0).map(point -> new PhantomNavigationPoint(point.x(), point.y(), point.z(), point.instanceId()))
					.distinct().sorted(Comparator.comparingInt(PhantomNavigationPoint::x).thenComparingInt(PhantomNavigationPoint::y)
						.thenComparingInt(PhantomNavigationPoint::z)).limit(MAX_ANCHORS).toList();
				if (!_anchors.isEmpty())
				{
					_anchorsFingerprint = anchorFingerprint(_anchors);
					_cursor = _memory.loadCursor(_anchorsFingerprint, cursorLimit());
				}
			}
			final long now = System.nanoTime();
			_nextSave = now + TimeUnit.SECONDS.toNanos(_settings.saveSeconds());
			_nextReport = now + MINUTE_NANOS;
			LOGGER.info("Phantom route learning ready, GeoData=" + fingerprint + ", trainer=" + _settings.trainer()
				+ ", anchors=" + _anchors.size() + ", cursor=" + _cursor + "/" + cursorLimit() + ", probe_limit_per_minute=" + _settings.probesPerMinute());
		}
		catch (IOException | RuntimeException ex)
		{
			if (!_stopping) { LOGGER.log(Level.WARNING, "Phantom route learning unavailable at " + _settings.path(), ex); }
		}
	}

	private static String anchorFingerprint(List<PhantomNavigationPoint> anchors)
	{
		final var digest = PhantomRouteLearning.sha256();
		final var values = java.nio.ByteBuffer.allocate(4 * Integer.BYTES);
		for (var point : anchors)
		{
			values.clear();
			values.putInt(point.x()).putInt(point.y()).putInt(point.z()).putInt(point.instanceId());
			digest.update(values.array());
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	private int cursorLimit() { return _anchors.size() * (OFFSETS.length + 1); }

	static Path checkoutPath(Path configured) throws IOException
	{
		final Path root = configured.toRealPath();
		if (!root.equals(configured)) { throw new IOException("Learned data path has a filesystem alias."); }
		Path checkout = root;
		for (int level = 0; level < 7; level++, checkout = checkout.getParent())
		{
			if (checkout == null || Files.isSymbolicLink(checkout)) { throw new IOException("Invalid learned data path."); }
		}
		final Path manifest = root.resolve("manifest.json");
		if (checkout == null || Files.isSymbolicLink(checkout) || !root.endsWith(Path.of("dist", "game").resolve(LEARNED))
			|| (!Files.isDirectory(checkout.resolve(".git"), LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(checkout.resolve(".git"), LinkOption.NOFOLLOW_LINKS))
			|| !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) || Files.size(manifest) > 256
			|| !Files.readString(manifest).strip().equals(MANIFEST))
		{
			throw new IOException("Use the tracked High Five learned directory and its schema 1 manifest.");
		}
		return root;
	}

	static String fingerprint(Path geodata, BooleanSupplier cancelled) throws IOException
	{
		final var digest = PhantomRouteLearning.sha256();
		final byte[] buffer = new byte[64 * 1024];
		int count = 0;
		for (int x = World.TILE_X_MIN; x <= World.TILE_X_MAX; x++)
		{
			for (int y = World.TILE_Y_MIN; y <= World.TILE_Y_MAX; y++)
			{
				if (cancelled.getAsBoolean()) { throw new IOException("GeoData fingerprint cancelled."); }
				final String name = String.format(java.util.Locale.ROOT, GeoEngine.FILE_NAME_FORMAT, x, y);
				final Path file = geodata.resolve(name);
				if (!Files.isRegularFile(file)) { continue; }
				final var before = Files.readAttributes(file, BasicFileAttributes.class);
				digest.update((name + "\n" + before.size() + "\n").getBytes(StandardCharsets.US_ASCII));
				try (var input = Files.newInputStream(file))
				{
					for (int length; (length = input.read(buffer)) != -1;)
					{
						if (cancelled.getAsBoolean()) { throw new IOException("GeoData fingerprint cancelled."); }
						digest.update(buffer, 0, length);
					}
				}
				final var after = Files.readAttributes(file, BasicFileAttributes.class);
				if (before.size() != after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
					|| !java.util.Objects.equals(before.fileKey(), after.fileKey())) { throw new IOException("GeoData changed during fingerprint: " + name); }
				count++;
			}
		}
		if (count == 0) { throw new IOException("No GeoData regions for route learning."); }
		return HexFormat.of().formatHex(digest.digest());
	}

	private void tick()
	{
		if (_stopping || _memory == null) { return; }
		try
		{
			_active.removeIf(probe -> {
				final var result = _navigation.consume(probe.request()).orElse(null);
				if (result != null) { completeProbe(result); }
				return result != null || _navigation.find(probe.request()).isEmpty();
			});
			final long now = System.nanoTime();
			if (now - _nextSave >= 0)
			{
				_nextSave = now + TimeUnit.SECONDS.toNanos(_settings.saveSeconds());
				try { _memory.flush(); }
				finally
				{
					saveCursor();
					if (_dedicatedRegions != null) { _dedicatedRegions.save(_memory); }
				}
			}
			if (now - _nextReport >= 0)
			{
				_nextReport = now + MINUTE_NANOS;
				report(now, false);
				if (_scout != null && !_scout.dedicated())
				{
					_memory.flushPhysicalWitness();
					writeScoutStatus();
				}
			}
			if (_scout != null && _scout.dedicated() && now - _nextDedicatedReport >= 0)
			{
				_nextDedicatedReport = now + TimeUnit.SECONDS.toNanos(5);
				writeScoutStatus();
			}
			if (!_stopping && (_scout == null || !_scout.sessionOpen()) && !_anchors.isEmpty() && _active.size() < _settings.workers() && _pacing.take(System.nanoTime(), _navigation.canProbeGeo()))
			{
				_attempts++;
				probe();
			}
		}
		catch (IOException | RuntimeException ex) { LOGGER.log(Level.WARNING, "Phantom route learning tick failed", ex); }
	}

	private void probe()
	{
		final int index = _cursor % _anchors.size();
		final int round = _cursor / _anchors.size();
		_cursor = (_cursor + 1) % cursorLimit();
		final var anchor = _anchors.get(index);
		final var backend = new L2jNavigationBackend();
		final var origin = backend.learnedSurface(anchor, 100);
		if (origin == null) { return; }
		PhantomNavigationPoint target = null;
		if (round == 0)
		{
			target = _anchors.stream().filter(point -> point.distanceTo(origin) >= 160 && point.distanceTo(origin) <= PROBE_DISTANCE)
				.min(java.util.Comparator.comparingDouble(origin::distanceTo)).orElse(null);
		}
		if (target == null)
		{
			final int[] offset = OFFSETS[Math.max(0, round - 1)];
			final int span = 384 + (index % 3) * 256;
			final int x = Math.clamp(anchor.x() + span * offset[0], World.WORLD_X_MIN, World.WORLD_X_MAX - 1);
			final int y = Math.clamp(anchor.y() + span * offset[1], World.WORLD_Y_MIN, World.WORLD_Y_MAX - 1);
			target = new PhantomNavigationPoint(x, y, origin.z(), origin.instanceId());
		}
		final var destination = backend.learnedSurface(target, 100);
		if (_stopping || destination == null || origin.equals(destination) || _memory.find(new Edge(origin, destination)) != null) { return; }
		final long profile = _active.stream().anyMatch(probe -> probe.profile() == Long.MAX_VALUE) ? Long.MAX_VALUE - 1 : Long.MAX_VALUE;
		final long now = Math.max(1, System.nanoTime());
		final var request = new PhantomNavigationRequest(profile, origin, destination, now, now + _navigation.policy().defaultRequestDeadlineNanos(), 3000);
		final var submission = _navigation.submitGeoProbe(request);
		if (submission.requestId() == 0) { return; }
		if (submission.status() == SubmissionStatus.REJECTED || (submission.immediateResult() != null && submission.immediateResult().status() == Status.QUEUE_BACKPRESSURE))
		{
			_navigation.consume(submission.requestId());
			return;
		}
		final long submitted = System.nanoTime();
		_pacing.deferAfter(submitted);
		trimAccepted(submitted);
		_recentAccepted.addLast(submitted);
		_accepted++;
		if (submission.immediateResult() != null)
		{
			_navigation.consume(submission.requestId());
			completeProbe(submission.immediateResult());
		}
		else { _active.add(new Probe(profile, submission.requestId())); }
	}

	private void completeProbe(PhantomNavigationResult result)
	{
		_completed++;
		if (_stopping || result.route() == null || result.route().mode() == PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA) { return; }
		var from = result.route().origin();
		for (var to : result.route().waypoints())
		{
			_memory.propose(new Edge(from, to));
			from = to;
		}
		_routes++;
	}

	private void trimAccepted(long now)
	{
		while (!_recentAccepted.isEmpty() && now - _recentAccepted.getFirst() >= MINUTE_NANOS) { _recentAccepted.removeFirst(); }
	}

	private void saveCursor() throws IOException
	{
		if (_anchorsFingerprint != null && _savedCursor != _cursor)
		{
			_memory.saveCursor(_anchorsFingerprint, _cursor);
			_savedCursor = _cursor;
		}
	}

	private void report(long now, boolean stopped) throws IOException
	{
		trimAccepted(now);
		final var legs = _memory.legTotals();
		final var journeys = _memory.journeyTotals();
		final String status = "state=" + (stopped ? "stopped" : "running") + " probe_limit_per_minute=" + _settings.probesPerMinute()
			+ " accepted_last_60s=" + _recentAccepted.size() + " attempts=" + _attempts + " accepted=" + _accepted + " completed=" + _completed
			+ " candidate_routes=" + _routes + " cursor=" + _cursor + "/" + cursorLimit() + " regions=" + legs.regions()
			+ " PROVEN=" + legs.proven() + " CANDIDATE=" + legs.candidate() + " full_journeys_completed=" + journeys.completed()
			+ " full_journeys_failed=" + journeys.failed() + " cross_region_completed=" + journeys.crossRegion()
			+ " journey_stats_writable=" + _memory.journeyStatsWritable();
		LOGGER.info("Phantom GeoTrainer " + status);
		_memory.writeFile("trainer-status.txt", status.replace(' ', '\n') + "\n");
	}

	void beginStop()
	{
		_stopping = true;
		final var memory = _memory;
		if (memory != null) { memory.stopAccepting(); }
		_writer.execute(() ->
		{
			if (_memory == null) { return; }
			try
			{
				try (var memoryToClose = _memory)
				{
					try { saveCursor(); }
					finally
					{
						if (_dedicatedRegions != null) { _dedicatedRegions.save(_memory); }
						report(System.nanoTime(), true);
						if (_scout != null) { writeScoutStatus(); }
					}
				}
			}
			catch (IOException | RuntimeException ex) { LOGGER.log(Level.WARNING, "Phantom route learning final flush failed", ex); }
		});
		_writer.shutdown();
	}

	boolean stopped() { return _writer.isTerminated(); }

	private void writeScoutStatus() throws IOException
	{
		final String regions = _dedicatedRegions == null ? "" : _dedicatedRegions.status();
		_memory.writeFile("physical-scout-status.txt", _scout.status() + regions);
	}
}

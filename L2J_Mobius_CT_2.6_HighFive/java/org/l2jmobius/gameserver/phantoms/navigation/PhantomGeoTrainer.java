package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
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
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Lifecycle owner of the route writer and optional headless GeoData probes. */
public final class PhantomGeoTrainer
{
	private static final Logger LOGGER = Logger.getLogger(PhantomGeoTrainer.class.getName());
	private static final Path CONFIG = Path.of("config/Custom/PhantomGeoTrainer.ini");
	private static final Path LEARNED = Path.of("data/phantoms/travel/learned");
	private static final String MANIFEST = "{\"schema\":1,\"format\":\"phantom-directed-leg-tsv\"}";
	private static final int[][] OFFSETS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
	private static final int MAX_ANCHORS = 2048;
	private static final int PROBE_DISTANCE = 2400;

	record Settings(boolean learning, boolean trainer, int workers, int probesPerMinute, int saveSeconds, Path path)
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
			return new Settings(flag(ini, "EnableRouteLearning", true), flag(ini, "EnableSuperGeoTrainer", false),
				number(ini, "GeoTrainerMaxWorkers", 1, 1, 2), number(ini, "GeoTrainerMaxProbesPerMinute", 20, 1, 20),
				number(ini, "GeoTrainerSaveIntervalSeconds", 120, 15, 3600), datapack.resolve(configured).toAbsolutePath().normalize());
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

		long probeSpacingNanos() { return (TimeUnit.MINUTES.toNanos(1) + probesPerMinute - 1) / probesPerMinute; }
	}

	private record Probe(long profile, long request) { }

	private final Settings _settings;
	private final PhantomNavigationService _navigation;
	private final PhantomTopologyQuery _topology;
	private final ScheduledThreadPoolExecutor _writer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("PhantomGeoTrainer").factory());
	private final List<Probe> _active = new ArrayList<>(2);
	private volatile PhantomRouteLearning _memory;
	private volatile boolean _stopping;
	private List<PhantomNavigationPoint> _anchors = List.of();
	private long _nextProbe;
	private long _nextSave;
	private int _cursor;
	private int _probes;
	private int _routes;

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
		_writer.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
		_writer.execute(this::initialize);
		_writer.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
	}

	PhantomRouteLearning memory()
	{
		return _stopping || !_settings.learning() ? null : _memory;
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
			if (_settings.trainer())
			{
				_anchors = _topology.snapshot().anchors().stream().map(anchor -> anchor.point())
					.filter(point -> point.instanceId() == 0).map(point -> new PhantomNavigationPoint(point.x(), point.y(), point.z(), point.instanceId()))
					.distinct().limit(MAX_ANCHORS).toList();
			}
			_nextSave = System.nanoTime() + TimeUnit.SECONDS.toNanos(_settings.saveSeconds());
			_nextProbe = System.nanoTime();
			LOGGER.info("Phantom route learning ready, GeoData=" + fingerprint + ", trainer=" + _settings.trainer() + ", anchors=" + _anchors.size());
		}
		catch (IOException | RuntimeException ex)
		{
			if (!_stopping) { LOGGER.log(Level.WARNING, "Phantom route learning unavailable at " + _settings.path(), ex); }
		}
	}

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
				if (result != null) { saveCandidate(result); }
				return result != null || _navigation.find(probe.request()).isEmpty();
			});
			final long now = System.nanoTime();
			if (now - _nextSave >= 0)
			{
				_nextSave = now + TimeUnit.SECONDS.toNanos(_settings.saveSeconds());
				_memory.flush();
			}
			if (!_anchors.isEmpty() && _active.size() < _settings.workers() && now - _nextProbe >= 0 && _navigation.canProbeGeo())
			{
				_nextProbe = now + _settings.probeSpacingNanos();
				probe(Math.max(1, now));
			}
		}
		catch (IOException | RuntimeException ex) { LOGGER.log(Level.WARNING, "Phantom route learning tick failed", ex); }
	}

	private void probe(long now)
	{
		final int index = _cursor % _anchors.size();
		final int round = _cursor / _anchors.size();
		_cursor = (_cursor + 1) % (_anchors.size() * (OFFSETS.length + 1));
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
		final var request = new PhantomNavigationRequest(profile, origin, destination, now, now + _navigation.policy().defaultRequestDeadlineNanos(), 3000);
		final var submission = _navigation.submitGeoProbe(request);
		if (submission.requestId() == 0) { return; }
		_probes++;
		if (submission.immediateResult() != null)
		{
			_navigation.consume(submission.requestId());
			saveCandidate(submission.immediateResult());
		}
		else { _active.add(new Probe(profile, submission.requestId())); }
	}

	private void saveCandidate(PhantomNavigationResult result)
	{
		if (_stopping || result.route() == null || result.route().mode() == PhantomNavigationRoute.Mode.DIRECT_UNVERIFIED_NO_GEODATA) { return; }
		var from = result.route().origin();
		for (var to : result.route().waypoints())
		{
			_memory.propose(new Edge(from, to));
			from = to;
		}
		_routes++;
	}

	void beginStop()
	{
		_stopping = true;
		final var memory = _memory;
		if (memory != null) { memory.stopAccepting(); }
		_writer.execute(() ->
		{
			try { if (_memory != null) { _memory.close(); } }
			catch (IOException | RuntimeException ex) { LOGGER.log(Level.WARNING, "Phantom route learning final flush failed", ex); }
			LOGGER.info("Phantom GeoTrainer stopped, probes=" + _probes + ", candidate routes=" + _routes);
		});
		_writer.shutdown();
	}

	boolean stopped() { return _writer.isTerminated(); }
}

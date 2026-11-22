package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Edge;

/** Shared, directed WALK evidence. Only the lifecycle writer opens or flushes shards. */
public final class PhantomRouteLearning implements AutoCloseable
{
	public enum Failure { GEOMETRY, TRANSIENT }

	public record Evidence(boolean candidate, int successes, int geometryFailures, int transientFailures, long retryAfter)
	{
		private static final Evidence EMPTY = new Evidence(false, 0, 0, 0, 0);

		private String provenance()
		{
			return successes > 0 ? "NATIVE_ARRIVED" : candidate ? "GEODATA_PROPOSED" : "FAILURE_ONLY";
		}

		private String level()
		{
			return successes > 0 ? "PROVEN" : candidate ? "CANDIDATE" : "FAILURE_ONLY";
		}
	}

	record Region(int x, int y)
	{
		private static Region of(PhantomNavigationPoint point) { return new Region(point.x() >> 15, point.y() >> 15); }
		private String filename() { return "region-" + x + "_" + y + ".tsv"; }
	}

	private record JourneyKey(PhantomJourneyObservation.Kind kind, Region from, Region to)
	{
		private String encode() { return kind + "\t" + from.x() + "\t" + from.y() + "\t" + to.x() + "\t" + to.y(); }
	}

	private record JourneyCounts(long completed, long route, long nativeFailure, long budget, long millis, long distance)
	{
		private static final JourneyCounts ZERO = new JourneyCounts(0, 0, 0, 0, 0, 0);
		private long failed() { return add(add(route, nativeFailure), budget); }
		private String encode()
		{
			return add(completed, failed()) + "\t" + completed + "\t" + route + "\t" + nativeFailure + "\t" + budget + "\t" + millis + "\t" + distance;
		}
	}

	record JourneyTotals(long completed, long failed, long crossRegion) { }
	record LegTotals(long proven, long candidate, int regions) { }

	private static final Logger LOGGER = Logger.getLogger(PhantomRouteLearning.class.getName());
	private static final String HEADER = "PHANTOM_ROUTE_LEARNING\t1\t";
	private static final String CURSOR = "trainer-progress.tsv";
	private static final String CURSOR_HEADER = "PHANTOM_GEO_CURSOR\t1\t";
	private static final String JOURNEYS = "journey-stats.tsv";
	private static final String JOURNEY_HEADER = "PHANTOM_JOURNEY_STATS\t1\t";
	private static final String JOURNEY_COLUMNS = "kind\tfrom_x\tfrom_y\tto_x\tto_y\tattempts\tcompleted\troute_failed\tnative_failed\tbudget_failed\telapsed_ms\twalked_units\n";
	private static final int MAX_JOURNEY_PAIRS = 1024;
	private static final Region OTHER = new Region(999, 999);
	private static final Region OTHER_CROSS = new Region(999, 998);
	private static final Pattern SHARD = Pattern.compile("region-(-?[0-9]{1,3})_(-?[0-9]{1,3})\\.tsv");
	private static final int MAX_REGIONS = 512;
	private static final int MAX_EDGES = 4096;
	private static final int MAX_SHARD_BYTES = 1024 * 1024;
	private static final int MAX_COUNT = 65535;
	private static final int MAX_LEG = 3000;
	private static final long RETRY_MILLIS = 15 * 60_000L;

	private final Path _root;
	private final String _fingerprint;
	private final FileLock _writerLock;
	private final Set<Region> _readOnly = new HashSet<>();
	private final Set<Region> _dirty = new HashSet<>();
	private volatile Map<Region, PhantomRouteGraph.Shard> _snapshot = Map.of();
	private final Map<JourneyKey, JourneyCounts> _journeys = new HashMap<>();
	private long _journeyRevision;
	private long _savedJourneyRevision = -1;
	private boolean _journeysReadOnly;
	private boolean _accepting = true;

	private PhantomRouteLearning(Path root, String fingerprint, FileLock writerLock)
	{
		_root = root;
		_fingerprint = fingerprint;
		_writerLock = writerLock;
	}

	public static PhantomRouteLearning open(Path root, String fingerprint) throws IOException
	{
		if (!fingerprint.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Invalid GeoData fingerprint."); }
		final FileChannel lock = FileChannel.open(root.resolve(".writer.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
		try
		{
			final var lease = lock.tryLock();
			if (lease == null) { throw new IOException("Another GameServer owns " + root); }
			final var memory = new PhantomRouteLearning(root, fingerprint, lease);
			memory.load();
			memory.loadJourneys();
			return memory;
		}
		catch (IOException | RuntimeException ex)
		{
			lock.close();
			throw ex;
		}
	}

	public Evidence find(Edge edge)
	{
		final var shard = _snapshot.get(Region.of(edge.from()));
		return shard == null ? null : shard.entries().get(edge);
	}

	public void propose(Edge edge)
	{
		update(edge, old -> new Evidence(true, old.successes(), old.geometryFailures(), old.transientFailures(), old.retryAfter()));
	}

	/** Caller owns the native completion receipt and must consume it once. */
	public void arrived(Edge edge)
	{
		update(edge, old -> new Evidence(old.candidate(), increment(old.successes()), old.geometryFailures(), old.transientFailures(), 0));
	}

	public void failed(Edge edge, Failure failure, long nowMillis)
	{
		update(edge, old -> failure == Failure.GEOMETRY
			? new Evidence(old.candidate(), old.successes(), increment(old.geometryFailures()), old.transientFailures(), old.retryAfter())
			: new Evidence(old.candidate(), old.successes(), old.geometryFailures(), increment(old.transientFailures()), nowMillis + RETRY_MILLIS));
	}

	List<PhantomNavigationPoint> route(PhantomNavigationRequest request, PhantomNavigationPolicy policy, boolean episode,
		Set<PhantomNavigationPoint> visited, Set<Edge> blocked, long nowMillis, BiPredicate<PhantomNavigationPoint, PhantomNavigationPoint> walk, BooleanSupplier interrupted)
	{
		return new PhantomRouteGraph(_snapshot, request, policy, episode, visited, blocked, nowMillis, walk, interrupted).search();
	}

	LegTotals legTotals()
	{
		final var snapshot = _snapshot;
		long proven = 0;
		long candidate = 0;
		for (var shard : snapshot.values())
		{
			for (var evidence : shard.entries().values())
			{
				if (evidence.successes() > 0) { proven++; }
				else if (evidence.candidate()) { candidate++; }
			}
		}
		return new LegTotals(proven, candidate, snapshot.size());
	}

	private synchronized void update(Edge edge, UnaryOperator<Evidence> change)
	{
		if (!_accepting || !valid(edge)) { return; }
		final var region = Region.of(edge.from());
		if (_readOnly.contains(region) || (!_snapshot.containsKey(region) && _snapshot.size() + _readOnly.size() >= MAX_REGIONS)) { return; }
		final var previousShard = _snapshot.get(region);
		final Map<Edge, Evidence> previous = previousShard == null ? Map.of() : previousShard.entries();
		final var old = previous.getOrDefault(edge, Evidence.EMPTY);
		final var next = change.apply(old);
		if (old.equals(next)) { return; }
		final var entries = new HashMap<>(previous);
		final boolean added = old == Evidence.EMPTY;
		Edge evicted = null;
		if (added && entries.size() >= MAX_EDGES)
		{
			// Never evict proof to make room for a probe or a failure.
			final var eviction = previous.entrySet().stream().filter(entry -> entry.getValue().successes() == 0
				&& (next.successes() > 0 || (next.candidate() && !entry.getValue().candidate())))
				.min(Comparator.<Map.Entry<Edge, Evidence>, Boolean>comparing(entry -> entry.getValue().candidate())
					.thenComparing(entry -> encodeEdge(entry.getKey()))).orElse(null);
			if (eviction == null) { return; }
			evicted = eviction.getKey();
			entries.remove(evicted);
		}
		entries.put(edge, next);
		final var updated = new HashMap<>(_snapshot);
		final var immutable = Map.copyOf(entries);
		updated.put(region, previousShard == null ? PhantomRouteGraph.Shard.of(immutable) : previousShard.updated(immutable, added ? edge : null, evicted));
		_snapshot = Map.copyOf(updated);
		_dirty.add(region);
	}

	private static boolean valid(Edge edge)
	{
		return edge.from().instanceId() == edge.to().instanceId()
			&& (edge.from().x() != edge.to().x() || edge.from().y() != edge.to().y())
			&& edge.from().distanceTo(edge.to()) <= MAX_LEG;
	}

	private static int increment(int value) { return Math.min(MAX_COUNT, value + 1); }

	private void load() throws IOException
	{
		final List<Path> paths;
		try (var files = Files.list(_root))
		{
			paths = files.filter(path -> SHARD.matcher(path.getFileName().toString()).matches()).limit(MAX_REGIONS + 1L).toList();
		}
		if (paths.size() > MAX_REGIONS) { throw new IOException("Too many learned-route shards."); }
		final var loaded = new HashMap<Region, PhantomRouteGraph.Shard>();
		for (var path : paths)
		{
			final var matcher = SHARD.matcher(path.getFileName().toString());
			if (!matcher.matches()) { continue; }
			final var region = new Region(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
			try
			{
				if (!region.filename().equals(path.getFileName().toString())) { throw new IOException("Noncanonical shard name."); }
				final var entries = readShard(path, region);
				if (!entries.isEmpty()) { loaded.put(region, PhantomRouteGraph.Shard.of(entries)); }
			}
			catch (IOException | IllegalArgumentException ex)
			{
				loaded.remove(region);
				_readOnly.add(region);
				LOGGER.warning("Phantom route learning ignored and protected " + path + " (" + ex.getMessage() + ")");
			}
		}
		_readOnly.forEach(loaded::remove);
		_snapshot = Map.copyOf(loaded);
	}

	private Map<Edge, Evidence> readShard(Path path, Region region) throws IOException
	{
		final String content;
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("Shard must be a regular file."); }
		try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS))
		{
			final byte[] bytes = input.readNBytes(MAX_SHARD_BYTES + 1);
			if (bytes.length > MAX_SHARD_BYTES) { throw new IOException("Oversize shard."); }
			content = new String(bytes, StandardCharsets.UTF_8);
		}
		if (content.equals(HEADER + "EMPTY\n")) { return Map.of(); }
		final int newline = content.indexOf('\n');
		if (newline < 0 || !content.startsWith(HEADER + _fingerprint + "\t"))
		{
			throw new IOException("Incompatible schema, invalid header or stale GeoData.");
		}
		final String payload = content.substring(newline + 1);
		if (!content.substring(0, newline).equals(HEADER + _fingerprint + "\t" + checksum(payload))
			|| !payload.endsWith("\n")) { throw new IOException("Shard checksum mismatch."); }
		final var entries = new HashMap<Edge, Evidence>();
		for (String line : payload.split("\n"))
		{
			final var fields = line.split("\t", -1);
			if (fields.length != 14 || entries.size() >= MAX_EDGES) { throw new IOException("Invalid shard entries."); }
			final int instance = Integer.parseInt(fields[0]);
			final var edge = new Edge(decodePoint(fields, 1, instance), decodePoint(fields, 4, instance));
			final var evidence = new Evidence(switch (fields[9]) { case "0" -> false; case "1" -> true; default -> throw new IllegalArgumentException("Invalid candidate flag."); },
				count(fields[10]), count(fields[11]), count(fields[12]), Long.parseLong(fields[13]));
			if (!region.equals(Region.of(edge.from())) || !valid(edge) || !evidence.level().equals(fields[7]) || !evidence.provenance().equals(fields[8])
				|| evidence.retryAfter() < 0 || evidence.retryAfter() > System.currentTimeMillis() + RETRY_MILLIS
				|| evidence.equals(Evidence.EMPTY) || entries.putIfAbsent(edge, evidence) != null)
			{
				throw new IOException("Invalid or duplicate directed edge.");
			}
		}
		return Map.copyOf(entries);
	}

	private static int count(String value)
	{
		final int count = Integer.parseInt(value);
		if (count < 0 || count > MAX_COUNT) { throw new IllegalArgumentException("Invalid evidence count."); }
		return count;
	}

	private static PhantomNavigationPoint decodePoint(String[] fields, int start, int instance)
	{
		return new PhantomNavigationPoint(Integer.parseInt(fields[start]), Integer.parseInt(fields[start + 1]), Integer.parseInt(fields[start + 2]), instance);
	}

	private static String encodePoint(PhantomNavigationPoint point)
	{
		return point.x() + "\t" + point.y() + "\t" + point.z();
	}

	private static String encodeEdge(Edge edge)
	{
		return edge.from().instanceId() + "\t" + encodePoint(edge.from()) + "\t" + encodePoint(edge.to());
	}

	public void flush() throws IOException
	{
		final List<Region> dirty;
		final Map<Region, PhantomRouteGraph.Shard> snapshot;
		synchronized (this)
		{
			dirty = List.copyOf(_dirty);
			snapshot = _snapshot;
		}
		for (Region region : dirty)
		{
			final var shard = snapshot.get(region);
			final var entries = shard.entries();
			final var payload = new StringBuilder();
			entries.entrySet().stream().sorted(Comparator.comparing(entry -> encodeEdge(entry.getKey()))).forEach(entry ->
			{
				final var evidence = entry.getValue();
				payload.append(encodeEdge(entry.getKey())).append('\t').append(evidence.level()).append('\t').append(evidence.provenance()).append('\t')
					.append(evidence.candidate() ? 1 : 0).append('\t').append(evidence.successes()).append('\t')
					.append(evidence.geometryFailures()).append('\t').append(evidence.transientFailures()).append('\t').append(evidence.retryAfter()).append('\n');
			});
			writeFile(region.filename(), checkedContent(HEADER, payload.toString()));
			synchronized (this)
			{
				if (_snapshot.get(region) == shard) { _dirty.remove(region); }
			}
		}
		flushJourneys();
	}

	int loadCursor(String anchors, int limit)
	{
		try
		{
			final var payload = readChecked(CURSOR, CURSOR_HEADER, 512);
			if (payload == null) { return 0; }
			final var fields = payload.strip().split("\t", -1);
			if (fields.length != 2 || !anchors.equals(fields[0])) { throw new IOException("Anchor set changed or invalid cursor."); }
			final int cursor = Integer.parseInt(fields[1]);
			if (cursor < 0 || cursor >= limit) { throw new IOException("Cursor outside current anchor cycle."); }
			return cursor;
		}
		catch (IOException | IllegalArgumentException ex)
		{
			LOGGER.warning("Phantom GeoTrainer cursor reset: " + ex.getMessage());
			return 0;
		}
	}

	void saveCursor(String anchors, int cursor) throws IOException
	{
		writeFile(CURSOR, checkedContent(CURSOR_HEADER, anchors + "\t" + cursor + "\n"));
	}

	synchronized void recordJourney(PhantomJourneyObservation.Outcome outcome)
	{
		if (!_accepting || _journeysReadOnly || outcome == null) { return; }
		final var from = Region.of(outcome.from());
		final var to = Region.of(outcome.to());
		var key = new JourneyKey(outcome.kind(), from, to);
		if (_journeys.size() >= MAX_JOURNEY_PAIRS - 4 && !_journeys.containsKey(key))
		{
			key = new JourneyKey(outcome.kind(), OTHER, from.equals(to) ? OTHER : OTHER_CROSS);
		}
		final var old = _journeys.getOrDefault(key, JourneyCounts.ZERO);
		final var failure = outcome.failure();
		_journeys.put(key, new JourneyCounts(add(old.completed(), failure == null ? 1 : 0),
			add(old.route(), failure == PhantomJourneyObservation.Failure.ROUTE ? 1 : 0),
			add(old.nativeFailure(), failure == PhantomJourneyObservation.Failure.NATIVE ? 1 : 0),
			add(old.budget(), failure == PhantomJourneyObservation.Failure.BUDGET ? 1 : 0),
			add(old.millis(), failure == null ? outcome.elapsedMillis() : 0), add(old.distance(), failure == null ? outcome.walkedDistance() : 0)));
		_journeyRevision++;
	}

	public void recordCompletedJourney(PhantomJourneyObservation observation, PhantomJourneyObservation.Identity identity, String anchor, PhantomNavigationPoint captured, long now)
	{
		recordJourney(observation.complete(identity, anchor, captured, now));
	}

	public void recordFailedJourney(PhantomJourneyObservation observation, PhantomJourneyObservation.Identity identity, PhantomJourneyObservation.Failure failure, long now)
	{
		recordJourney(observation.fail(identity, failure, now));
	}

	synchronized JourneyTotals journeyTotals()
	{
		long completed = 0;
		long failed = 0;
		long cross = 0;
		for (var entry : _journeys.entrySet())
		{
			final var counts = entry.getValue();
			completed = add(completed, counts.completed());
			failed = add(failed, counts.failed());
			if (!entry.getKey().from().equals(entry.getKey().to())) { cross = add(cross, counts.completed()); }
		}
		return new JourneyTotals(completed, failed, cross);
	}

	boolean journeyStatsWritable() { return !_journeysReadOnly; }

	private void loadJourneys()
	{
		try
		{
			final var payload = readChecked(JOURNEYS, JOURNEY_HEADER, MAX_SHARD_BYTES);
			if (payload == null) { return; }
			if (!payload.startsWith(JOURNEY_COLUMNS)) { throw new IOException("Invalid journey columns."); }
			for (var line : payload.substring(JOURNEY_COLUMNS.length()).lines().toList())
			{
				final var f = line.split("\t", -1);
				if (f.length != 12 || _journeys.size() >= MAX_JOURNEY_PAIRS) { throw new IOException("Invalid journey rows."); }
				final var key = new JourneyKey(PhantomJourneyObservation.Kind.valueOf(f[0]), new Region(Integer.parseInt(f[1]), Integer.parseInt(f[2])),
					new Region(Integer.parseInt(f[3]), Integer.parseInt(f[4])));
				final var counts = new JourneyCounts(amount(f[6]), amount(f[7]), amount(f[8]), amount(f[9]), amount(f[10]), amount(f[11]));
				if (amount(f[5]) != add(counts.completed(), counts.failed()) || _journeys.putIfAbsent(key, counts) != null) { throw new IOException("Invalid or duplicate journey totals."); }
			}
			_savedJourneyRevision = _journeyRevision;
		}
		catch (IOException | IllegalArgumentException ex)
		{
			_journeys.clear();
			_journeysReadOnly = true;
			LOGGER.warning("Phantom journey statistics ignored and protected: " + ex.getMessage());
		}
	}

	private void flushJourneys() throws IOException
	{
		final Map<JourneyKey, JourneyCounts> journeys;
		final long revision;
		synchronized (this)
		{
			if (_journeysReadOnly || _savedJourneyRevision == _journeyRevision) { return; }
			journeys = Map.copyOf(_journeys);
			revision = _journeyRevision;
		}
		final var payload = new StringBuilder(JOURNEY_COLUMNS);
		journeys.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().encode())).forEach(entry ->
			payload.append(entry.getKey().encode()).append('\t').append(entry.getValue().encode()).append('\n'));
		writeFile(JOURNEYS, checkedContent(JOURNEY_HEADER, payload.toString()));
		_savedJourneyRevision = revision;
	}

	private static long amount(String value)
	{
		final long count = Long.parseLong(value);
		if (count < 0) { throw new IllegalArgumentException("Negative journey count."); }
		return count;
	}

	private static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }

	private String checkedContent(String header, String payload)
	{
		return header + _fingerprint + "\t" + checksum(payload) + "\n" + payload;
	}

	private String readChecked(String filename, String header, int maxBytes) throws IOException
	{
		final Path file = _root.resolve(filename);
		if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) { return null; }
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("Not a regular file: " + filename); }
		try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS))
		{
			final var bytes = input.readNBytes(maxBytes + 1);
			if (bytes.length > maxBytes) { throw new IOException("Oversize file: " + filename); }
			final String content = new String(bytes, StandardCharsets.UTF_8);
			final int newline = content.indexOf('\n');
			if (newline < 0 || !content.startsWith(header + _fingerprint + "\t")) { throw new IOException("Invalid schema or stale GeoData: " + filename); }
			final var payload = content.substring(newline + 1);
			if (!content.equals(checkedContent(header, payload)) || !payload.endsWith("\n")) { throw new IOException("Checksum mismatch: " + filename); }
			return payload;
		}
	}

	void writeFile(String filename, String content) throws IOException
	{
		final Path target = _root.resolve(filename);
		if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
		{
			throw new IOException("Not a regular learned file: " + filename);
		}
		final Path partial = Files.createTempFile(_root, ".route-", ".tmp");
		try
		{
			try (FileChannel channel = FileChannel.open(partial, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))
			{
				final var bytes = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
				while (bytes.hasRemaining()) { channel.write(bytes); }
				channel.force(true);
			}
			Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		finally { Files.deleteIfExists(partial); }
	}

	static MessageDigest sha256()
	{
		try { return MessageDigest.getInstance("SHA-256"); }
		catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
	}

	private static String checksum(String payload)
	{
		return HexFormat.of().formatHex(sha256().digest(payload.getBytes(StandardCharsets.UTF_8)));
	}

	synchronized void stopAccepting() { _accepting = false; }

	@Override
	public void close() throws IOException
	{
		stopAccepting();
		try { flush(); }
		finally { _writerLock.channel().close(); }
	}
}

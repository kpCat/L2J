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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	private record Region(int x, int y)
	{
		private static Region of(PhantomNavigationPoint point) { return new Region(point.x() >> 15, point.y() >> 15); }
		private String filename() { return "region-" + x + "_" + y + ".tsv"; }
	}

	private static final Logger LOGGER = Logger.getLogger(PhantomRouteLearning.class.getName());
	private static final String HEADER = "PHANTOM_ROUTE_LEARNING\t1\t";
	private static final Pattern SHARD = Pattern.compile("region-(-?[0-9]{1,3})_(-?[0-9]{1,3})\\.tsv");
	private static final int MAX_REGIONS = 512;
	private static final int MAX_EDGES = 4096;
	private static final int MAX_SHARD_BYTES = 1024 * 1024;
	private static final int MAX_COUNT = 65535;
	private static final int CONNECTOR = 160;
	private static final int MAX_LEG = 3000;
	private static final long RETRY_MILLIS = 15 * 60_000L;

	private final Path _root;
	private final String _fingerprint;
	private final FileLock _writerLock;
	private final Set<Region> _readOnly = new HashSet<>();
	private final Set<Region> _dirty = new HashSet<>();
	private volatile Map<Region, Map<Edge, Evidence>> _snapshot = Map.of();
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
		return _snapshot.getOrDefault(Region.of(edge.from()), Map.of()).get(edge);
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

	List<Edge> hints(PhantomNavigationPoint origin, PhantomNavigationPoint goal, boolean episode, Set<Edge> blocked, long nowMillis)
	{
		final var snapshot = _snapshot;
		final var hints = new ArrayList<Map.Entry<Edge, Evidence>>();
		for (int x = (origin.x() - CONNECTOR) >> 15; x <= (origin.x() + CONNECTOR) >> 15; x++)
		{
			for (int y = (origin.y() - CONNECTOR) >> 15; y <= (origin.y() + CONNECTOR) >> 15; y++)
			{
				for (var entry : snapshot.getOrDefault(new Region(x, y), Map.of()).entrySet())
				{
					final var edge = entry.getKey();
					final var evidence = entry.getValue();
					if (edge.from().instanceId() != origin.instanceId() || edge.to().equals(origin) || edge.from().equals(goal) || blocked.contains(edge)
						|| evidence.retryAfter() > nowMillis || (evidence.successes() == 0 && !evidence.candidate())
						|| !connector(origin, edge.from())) { continue; }
					if (connector(edge.to(), goal) || (episode && (edge.to().distanceTo(goal) + CONNECTOR < origin.distanceTo(goal))))
					{
						hints.add(entry);
					}
				}
			}
		}
		return hints.stream().sorted(Comparator
			.<Map.Entry<Edge, Evidence>>comparingInt(entry -> entry.getValue().geometryFailures())
			.thenComparing(entry -> entry.getValue().successes() == 0)
			.thenComparingDouble(entry -> entry.getKey().to().distanceTo(goal))
			.thenComparing(entry -> encodeEdge(entry.getKey())))
			.limit(3).map(Map.Entry::getKey).toList();
	}

	static boolean connector(PhantomNavigationPoint from, PhantomNavigationPoint to)
	{
		return from.distanceTo(to) <= CONNECTOR && Math.abs((long) from.z() - to.z()) <= 32;
	}

	private synchronized void update(Edge edge, UnaryOperator<Evidence> change)
	{
		if (!_accepting || !valid(edge)) { return; }
		final var region = Region.of(edge.from());
		if (_readOnly.contains(region) || (!_snapshot.containsKey(region) && _snapshot.size() + _readOnly.size() >= MAX_REGIONS)) { return; }
		final var previous = _snapshot.getOrDefault(region, Map.of());
		final var old = previous.getOrDefault(edge, Evidence.EMPTY);
		final var next = change.apply(old);
		if (old.equals(next)) { return; }
		final var entries = new HashMap<>(previous);
		if (!previous.containsKey(edge) && entries.size() >= MAX_EDGES)
		{
			// Never evict proof to make room for a probe or a failure.
			final var eviction = previous.entrySet().stream().filter(entry -> entry.getValue().successes() == 0
				&& (next.successes() > 0 || (next.candidate() && !entry.getValue().candidate())))
				.min(Comparator.<Map.Entry<Edge, Evidence>, Boolean>comparing(entry -> entry.getValue().candidate())
					.thenComparing(entry -> encodeEdge(entry.getKey()))).orElse(null);
			if (eviction == null) { return; }
			entries.remove(eviction.getKey());
		}
		entries.put(edge, next);
		final var updated = new HashMap<>(_snapshot);
		updated.put(region, Map.copyOf(entries));
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
		final var loaded = new HashMap<Region, Map<Edge, Evidence>>();
		for (var path : paths)
		{
			final var matcher = SHARD.matcher(path.getFileName().toString());
			if (!matcher.matches()) { continue; }
			final var region = new Region(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
			try
			{
				if (!region.filename().equals(path.getFileName().toString())) { throw new IOException("Noncanonical shard name."); }
				final var entries = readShard(path, region);
				if (!entries.isEmpty()) { loaded.put(region, entries); }
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
		final Map<Region, Map<Edge, Evidence>> snapshot;
		synchronized (this)
		{
			dirty = List.copyOf(_dirty);
			snapshot = _snapshot;
		}
		for (Region region : dirty)
		{
			final var entries = snapshot.get(region);
			final var payload = new StringBuilder();
			entries.entrySet().stream().sorted(Comparator.comparing(entry -> encodeEdge(entry.getKey()))).forEach(entry ->
			{
				final var evidence = entry.getValue();
				payload.append(encodeEdge(entry.getKey())).append('\t').append(evidence.level()).append('\t').append(evidence.provenance()).append('\t')
					.append(evidence.candidate() ? 1 : 0).append('\t').append(evidence.successes()).append('\t')
					.append(evidence.geometryFailures()).append('\t').append(evidence.transientFailures()).append('\t').append(evidence.retryAfter()).append('\n');
			});
			writeShard(region, HEADER + _fingerprint + "\t" + checksum(payload.toString()) + "\n" + payload);
			synchronized (this)
			{
				if (_snapshot.get(region) == entries) { _dirty.remove(region); }
			}
		}
	}

	private void writeShard(Region region, String content) throws IOException
	{
		final Path target = _root.resolve(region.filename());
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

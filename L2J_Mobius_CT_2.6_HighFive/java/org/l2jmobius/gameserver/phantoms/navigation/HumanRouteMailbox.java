package org.l2jmobius.gameserver.phantoms.navigation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class HumanRouteMailbox implements AutoCloseable
{
	enum Action { STATUS, ARM, ARRIVED, SKIP, STOP, NEXT }
	record Request(UUID id, long sequence, long deadline, Action action, String scene) { }
	record Permit(String account, int objectId, long expires) { }

	private static final int MAX_REQUEST = 1024;
	private static final int MAX_REQUESTS = 2048;
	private static final long MAX_REQUEST_LIFETIME = 15_000;
	private static final long MAX_PERMIT_LIFETIME = 3_600_000;
	private final Path _root;
	private final UserPrincipal _owner;
	private final FileChannel _lockChannel;
	private final FileLock _lock;
	private final UUID _incarnation = UUID.randomUUID();
	private final Set<UUID> _seen = new HashSet<>();
	private long _sequence = 1;

	HumanRouteMailbox(Path root) throws IOException
	{
		_root = root.toAbsolutePath().normalize();
		final String local = System.getenv("LOCALAPPDATA");
		if (local == null || !_root.startsWith(Path.of(local).toAbsolutePath().normalize().resolve("PhantomHumanRoutes")) || _root.equals(Path.of(local).toAbsolutePath().normalize().resolve("PhantomHumanRoutes")))
		{
			throw new IOException("Private root must be under LocalAppData/PhantomHumanRoutes.");
		}
		_owner = _root.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(System.getProperty("user.name"));
		check(_root, true);
		final Path lock = _root.resolve("server.lock");
		if (Files.exists(lock, LinkOption.NOFOLLOW_LINKS))
		{
			check(lock, false);
		}
		_lockChannel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
		try
		{
			_lock = _lockChannel.tryLock();
			if (_lock == null)
			{
				throw new IOException("Mailbox already has a server owner.");
			}
			check(lock, false);
		}
		catch (IOException | RuntimeException failure)
		{
			_lockChannel.close();
			throw failure;
		}
	}

	UUID incarnation() { return _incarnation; }
	void check(Path path, boolean directory) throws IOException
	{
		if (!(directory ? Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) || !path.toRealPath().equals(path) || Files.isSymbolicLink(path))
		{
			throw new IOException("Invalid private path.");
		}
		final var acl = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		if (acl == null || !_owner.equals(acl.getOwner()) || acl.getAcl().stream().noneMatch(entry -> entry.type() == AclEntryType.ALLOW && _owner.equals(entry.principal()))
			|| acl.getAcl().stream().anyMatch(entry -> entry.type() == AclEntryType.ALLOW && !_owner.equals(entry.principal())))
		{
			throw new IOException("Private path grants access outside the server owner.");
		}
	}

	void validate() throws IOException
	{
		check(_root, true);
		check(_root.resolve("evidence"), true);
		if (!_lock.isValid())
		{
			throw new IOException("Mailbox lock lost.");
		}
	}

	Permit permit(long now) throws IOException
	{
		final var fields = fields(read(_root.resolve("permit.txt"), MAX_REQUEST), Set.of("EnableHumanRouteDemonstration", "account", "objectId", "expires"));
		final long expires = Long.parseLong(fields.get("expires"));
		final int objectId = Integer.parseInt(fields.get("objectId"));
		final String account = fields.get("account");
		if (!"True".equals(fields.get("EnableHumanRouteDemonstration")) || !account.matches("[A-Za-z0-9_]{1,32}") || objectId <= 0 || expires <= now || expires > now + MAX_PERMIT_LIFETIME)
		{
			throw new IllegalArgumentException("Invalid private permit.");
		}
		return new Permit(account, objectId, expires);
	}

	void announce() throws IOException
	{
		final ProcessHandle process = ProcessHandle.current();
		final Instant start = process.info().startInstant().orElseThrow(() -> new IOException("Process incarnation unavailable."));
		write(_root.resolve("server.txt"), "version=1\nincarnation=" + _incarnation + "\npid=" + process.pid() + "\nstarted=" + start.toEpochMilli() + "\n");
	}

	Request receive(long now) throws IOException
	{
		final Request stop = receive(now, true);
		return stop == null ? receive(now, false) : stop;
	}

	boolean stopPending()
	{
		return Files.exists(_root.resolve("stop.txt"), LinkOption.NOFOLLOW_LINKS);
	}

	private Request receive(long now, boolean stop) throws IOException
	{
		final Path pending = _root.resolve(stop ? "stop.txt" : "request.txt");
		if (!Files.exists(pending, LinkOption.NOFOLLOW_LINKS))
		{
			return null;
		}
		final Path claimed = _root.resolve("processing.txt");
		if (Files.exists(claimed, LinkOption.NOFOLLOW_LINKS))
		{
			check(claimed, false);
			Files.delete(claimed);
		}
		check(pending, false);
		Files.move(pending, claimed, StandardCopyOption.ATOMIC_MOVE);
		try
		{
			final Request request = parseRequest(read(claimed, MAX_REQUEST), _incarnation, now);
			if (stop != (request.action() == Action.STOP) || (!stop && request.sequence() != _sequence) || !_seen.add(request.id()))
			{
				return null;
			}
			if (_seen.size() > MAX_REQUESTS)
			{
				throw new IOException("Request budget exhausted.");
			}
			if (!stop) { _sequence++; }
			return request;
		}
		catch (IllegalArgumentException rejected)
		{
			return null;
		}
		finally
		{
			Files.delete(claimed);
		}
	}

	static Request parseRequest(String content, UUID incarnation, long now)
	{
		final var fields = fields(content, Set.of("version", "incarnation", "id", "sequence", "deadline", "action", "scene"));
		final UUID id = UUID.fromString(fields.get("id"));
		final long sequence = Long.parseLong(fields.get("sequence"));
		final long deadline = Long.parseLong(fields.get("deadline"));
		final Action action = Action.valueOf(fields.get("action").toUpperCase(Locale.ROOT));
		final String scene = fields.get("scene");
		if (!fields.get("id").equals(id.toString()) || !"1".equals(fields.get("version")) || !incarnation.toString().equals(fields.get("incarnation"))
			|| (action == Action.STOP ? sequence != 0 || !scene.equals("-") : sequence < 1) || deadline <= now || deadline > now + MAX_REQUEST_LIFETIME
			|| (!scene.equals("-") && !scene.matches("[a-z0-9][a-z0-9-]{0,47}")))
		{
			throw new IllegalArgumentException("Invalid request envelope.");
		}
		return new Request(id, sequence, deadline, action, scene);
	}

	private static Map<String, String> fields(String content, Set<String> expected)
	{
		final Map<String, String> fields = new HashMap<>();
		for (String line : content.lines().toList())
		{
			final int separator = line.indexOf('=');
			if (separator < 1 || fields.putIfAbsent(line.substring(0, separator), line.substring(separator + 1)) != null)
			{
				throw new IllegalArgumentException("Invalid record fields.");
			}
		}
		if (!fields.keySet().equals(expected))
		{
			throw new IllegalArgumentException("Unknown or missing fields.");
		}
		return fields;
	}

	String input(String name, int maximum) throws IOException
	{
		return read(_root.resolve(name), maximum);
	}

	private String read(Path path, int maximum) throws IOException
	{
		check(path, false);
		try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS))
		{
			final byte[] data = input.readNBytes(maximum + 1);
			if (data.length == 0 || data.length > maximum)
			{
				throw new IOException("Private file exceeds its size contract.");
			}
			return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString();
		}
	}

	void reply(Request request, String body) throws IOException
	{
		write(_root.resolve(request.action() == Action.STOP ? "stop-receipt.txt" : "receipt.txt"), "incarnation=" + _incarnation + "\nid=" + request.id() + "\nnext_sequence=" + _sequence + "\n" + body);
	}

	void status(String body) throws IOException
	{
		write(_root.resolve("status.txt"), "incarnation=" + _incarnation + "\nnext_sequence=" + _sequence + "\n" + body);
	}

	Path evidence() { return _root.resolve("evidence"); }

	void write(Path path, String content) throws IOException
	{
		check(path.getParent(), true);
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
		{
			check(path, false);
		}
		final Path temporary = Files.createTempFile(path.getParent(), ".human-route-", ".tmp");
		try
		{
			check(temporary, false);
			try (var output = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))
			{
				final ByteBuffer data = StandardCharsets.UTF_8.encode(content);
				while (data.hasRemaining()) { output.write(data); }
				output.force(true);
			}
			for (int attempt = 0; ; attempt++)
			{
				try
				{
					Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
					break;
				}
				catch (FileSystemException failure)
				{
					if (attempt == 4) { throw failure; }
					// Windows can transiently reject replacement while a reader holds the old receipt.
					try { Thread.sleep(10); }
					catch (InterruptedException interrupted)
					{
						Thread.currentThread().interrupt();
						throw new IOException("Private write interrupted.", interrupted);
					}
				}
			}
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}

	@Override
	public void close() throws IOException
	{
		_lockChannel.close();
	}
}

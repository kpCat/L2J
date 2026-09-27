package org.l2jmobius.gameserver.localplay;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileSystems;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.network.ConnectionState;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;

/** One private LocalPlay mailbox and one real-client consent lease. */
public final class LocalPlayPilotService
{
	private static final Logger LOGGER = Logger.getLogger(LocalPlayPilotService.class.getName());
	private static final LocalPlayPilotService INSTANCE = new LocalPlayPilotService();
	private static final long DOTNET_EPOCH_TICKS = 621355968000000000L;
	private static final long CONSENT_NANOS = 120L * 60 * 1000000000L;
	private static final Pattern OWNED_RECORD = Pattern.compile("\\{\"format\":2,\"role\":\"GameServer\",\"pid\":([0-9]+),\"startTimeUtcTicks\":([0-9]+),\"jar\":\"GameServer.jar\",\"runtime\":\"([0-9a-f]{64})\"\\}");
	private static final Pattern MANIFEST_FIELD = Pattern.compile("\"([A-Za-z][A-Za-z0-9]*)\"\\s*:\\s*(?:\"([^\"\\\\]*)\"|([0-9]+|true|false))");
	private Path _runtimeRoot;
	private Path _pilotRoot;
	private String _runtimeId;
	private long _pid;
	private long _startTicks;
	private long _observedStartMillis;
	private LocalPlayPilotLease _lease;
	private Player _player;
	private GameClient _client;
	private LocalPlayPilotActions _actions;
	private ScheduledFuture<?> _poller;
	private long _lastReadNanos;
	private long _expiresUtcMillis;
	private String _runId;
	private String _stoppedRunId;
	private long _lastHeartbeatNanos;
	private long _runDeadlineNanos;

	private LocalPlayPilotService()
	{
	}

	public static LocalPlayPilotService getInstance()
	{
		return INSTANCE;
	}

	public synchronized boolean startConfigured()
	{
		if (!LocalPlayPilotConfig.isEnabled())
		{
			return false;
		}
		try
		{
			final Path gameRoot = ServerConfig.DATAPACK_ROOT.toPath().toRealPath();
			_runtimeRoot = gameRoot.getParent();
			if ((_runtimeRoot == null) || !Files.isRegularFile(_runtimeRoot.resolve("local-play.json"), LinkOption.NOFOLLOW_LINKS))
			{
				throw new IllegalStateException("LocalPlay manifest is absent.");
			}
			_runtimeId = runtimeId(_runtimeRoot);
			_pid = ProcessHandle.current().pid();
			final Instant started = ProcessHandle.current().info().startInstant().orElseThrow();
			_observedStartMillis = started.toEpochMilli();
			_startTicks = ownedStartTicks();
			if (!validManifest() || (_startTicks == 0))
			{
				throw new IllegalStateException("GameServer ownership incarnation is not current.");
			}
			_pilotRoot = _runtimeRoot.resolve("playtest-pilot");
			_poller = ThreadPool.scheduleAtFixedRate(this::safePoll, 100, 200);
			LOGGER.info("LocalPlay pilot mailbox enabled for one real-client consent lease.");
			return true;
		}
		catch (Exception exception)
		{
			LOGGER.log(Level.WARNING, "LocalPlay pilot disabled: " + exception.getMessage());
			_runtimeRoot = null;
			_pilotRoot = null;
			return false;
		}
	}

	public synchronized void shutdown()
	{
		if (_poller != null)
		{
			_poller.cancel(false);
			_poller = null;
		}
		revoke();
	}

	private static String runtimeId(Path root) throws Exception
	{
		final String normalized = root.toAbsolutePath().normalize().toString().replace('/', '\\').replaceAll("\\\\+$", "").toLowerCase(Locale.ROOT);
		final byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(digest);
	}

	private boolean ownedProcess() throws IOException
	{
		return (_startTicks != 0) && (ownedStartTicks() == _startTicks);
	}

	private long ownedStartTicks() throws IOException
	{
		if ((_runtimeRoot == null) || (_runtimeId == null) || !_runtimeId.equals(System.getProperty("phantom.localplay.runtime")) || !"GameServer".equals(System.getProperty("phantom.localplay.role")))
		{
			return 0;
		}
		final Path record = _runtimeRoot.resolve("local-play/pids/GameServer.json");
		if (!Files.isRegularFile(record, LinkOption.NOFOLLOW_LINKS) || !record.toRealPath().startsWith(_runtimeRoot) || (Files.size(record) > 4096))
		{
			return 0;
		}
		String json = Files.readString(record, StandardCharsets.UTF_8).strip();
		if (!json.isEmpty() && (json.charAt(0) == 0xfeff))
		{
			json = json.substring(1);
		}
		final Matcher matcher = OWNED_RECORD.matcher(json);
		if (!matcher.matches() || !Long.toString(_pid).equals(matcher.group(1)) || !_runtimeId.equals(matcher.group(3)))
		{
			return 0;
		}
		final long recordedTicks;
		try
		{
			recordedTicks = Long.parseLong(matcher.group(2));
		}
		catch (NumberFormatException exception)
		{
			return 0;
		}
		// ProcessHandle.startInstant() has millisecond precision on Windows; retain the exact
		// LocalPlay record ticks after matching the same observable process start millisecond.
		return (recordedTicks >= DOTNET_EPOCH_TICKS) && (((recordedTicks - DOTNET_EPOCH_TICKS) / 10000L) == _observedStartMillis) ? recordedTicks : 0;
	}

	private boolean validManifest() throws Exception
	{
		final Path manifest = _runtimeRoot.resolve("local-play.json");
		final Path jar = _runtimeRoot.resolve("libs/GameServer.jar");
		if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) || !manifest.toRealPath().startsWith(_runtimeRoot) || (Files.size(manifest) > 8192) || !Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || !jar.toRealPath().startsWith(_runtimeRoot))
		{
			return false;
		}
		String json = Files.readString(manifest, StandardCharsets.UTF_8).strip();
		if (!json.isEmpty() && (json.charAt(0) == 0xfeff))
		{
			json = json.substring(1).strip();
		}
		if (!json.startsWith("{") || !json.endsWith("}"))
		{
			return false;
		}
		final Properties fields = new Properties();
		int position = 1;
		while (position < (json.length() - 1))
		{
			while (Character.isWhitespace(json.charAt(position)))
			{
				position++;
			}
			final Matcher matcher = MANIFEST_FIELD.matcher(json).region(position, json.length() - 1);
			if (!matcher.lookingAt() || (fields.put(matcher.group(1), matcher.group(2) == null ? matcher.group(3) : matcher.group(2)) != null))
			{
				return false;
			}
			position = matcher.end();
			while (Character.isWhitespace(json.charAt(position)))
			{
				position++;
			}
			if (json.charAt(position) == ',')
			{
				position++;
				while ((position < (json.length() - 1)) && Character.isWhitespace(json.charAt(position)))
				{
					position++;
				}
				if (position == (json.length() - 1))
				{
					return false;
				}
			}
			else if (json.charAt(position) != '}')
			{
				return false;
			}
			else
			{
				break;
			}
		}
		if ((position != json.length() - 1) || !"1".equals(fields.getProperty("format")))
		{
			return false;
		}
		final MessageDigest digest = MessageDigest.getInstance("SHA-256");
		try (InputStream input = Files.newInputStream(jar))
		{
			final byte[] buffer = new byte[8192];
			for (int read; (read = input.read(buffer)) >= 0;)
			{
				digest.update(buffer, 0, read);
			}
		}
		return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(fields.getProperty("gameJarSha256", ""));
	}

	private static Properties readProperties(Path file) throws IOException
	{
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file) || (Files.size(file) > 8192))
		{
			throw new IOException("Private control file is invalid.");
		}
		final Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			properties.load(reader);
		}
		return properties;
	}

	private boolean mailboxSafe()
	{
		if (!safeDirectory(_pilotRoot) || !privateAcl(_pilotRoot))
		{
			return false;
		}
		for (String name : List.of("inbox", "processing", "results", "journal"))
		{
			final Path child = _pilotRoot.resolve(name);
			if (!safeDirectory(child) || !privateAcl(child))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean privateAcl(Path path)
	{
		try
		{
			final AclFileAttributeView view = Files.getFileAttributeView(path, AclFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
			if (view == null)
			{
				return false;
			}
			final var lookup = FileSystems.getDefault().getUserPrincipalLookupService();
			final UserPrincipal user = lookup.lookupPrincipalByName(System.getProperty("user.name"));
			final UserPrincipal system = lookup.lookupPrincipalByName("S-1-5-18");
			final UserPrincipal administrators = lookup.lookupPrincipalByName("S-1-5-32-544");
			for (AclEntry entry : view.getAcl())
			{
				if ((entry.type() == AclEntryType.ALLOW) && !entry.principal().equals(user) && !entry.principal().equals(system) && !entry.principal().equals(administrators))
				{
					return false;
				}
			}
			return true;
		}
		catch (Exception exception)
		{
			return false;
		}
	}

	private static boolean safeDirectory(Path path)
	{
		try
		{
			return (path != null) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && path.toRealPath().equals(path.toAbsolutePath().normalize());
		}
		catch (IOException exception)
		{
			return false;
		}
	}

	private static void writeProperties(Path file, Properties properties) throws IOException
	{
		final Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8))
		{
			properties.store(writer, null);
		}
		Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
	}

	public synchronized String arm(Player player, String nonce)
	{
		if ((_poller == null) || !mailboxSafe() || (player == null) || (nonce == null) || !nonce.matches("[0-9a-fA-F]{32,128}") || !realClient(player))
		{
			return "Пилот недоступен или настоящий клиент не подтверждён.";
		}
		if ((_lease != null) && (_lease.state() != LocalPlayPilotLease.State.OFF))
		{
			return "Пилот уже привязан к другой или текущей сессии.";
		}
		try
		{
			if (!validManifest() || !ownedProcess())
			{
				return "Текущий GameServer не принадлежит LocalPlay.";
			}
			final Path permitFile = _pilotRoot.resolve("arm.properties");
			if (!privateAcl(permitFile))
			{
				return "Private разрешение имеет небезопасный ACL.";
			}
			final Properties permit = readProperties(permitFile);
			final long permitExpiry = Long.parseLong(permit.getProperty("expiresUtcMillis", "0"));
			final long now = System.currentTimeMillis();
			if (!"1".equals(permit.getProperty("version")) || (permitExpiry <= now) || (permitExpiry > now + (10 * 60 * 1000L)) || !_runtimeId.equals(permit.getProperty("runtimeId")) || !Long.toString(_pid).equals(permit.getProperty("pid")) || !Long.toString(_startTicks).equals(permit.getProperty("startTimeUtcTicks")) || !player.getName().equals(permit.getProperty("expectedName")))
			{
				return "Разрешение не соответствует этому персонажу или процессу.";
			}
			final long armDeadlineNanos = System.nanoTime() + ((permitExpiry - now) * 1000000L);
			if (!nonce.equals(permit.getProperty("nonce")))
			{
				return "Неверный или использованный код разрешения.";
			}
			Files.delete(permitFile);
			final LocalPlayPilotLease lease = new LocalPlayPilotLease(nonce, player.getName(), _pid, _startTicks, armDeadlineNanos, System::nanoTime);
			if (!lease.arm(nonce, player.getName(), player.getAccountName(), player.getObjectId(), player.getClient(), _pid, _startTicks, CONSENT_NANOS))
			{
				return "Неверный или использованный код разрешения.";
			}
			_client = player.getClient();
			_player = player;
			_lease = lease;
			_actions = new LocalPlayPilotActions(player);
			_expiresUtcMillis = now + (CONSENT_NANOS / 1000000L);
			writeSession();
			return "Пилот привязан к этому клиенту до " + Instant.ofEpochMilli(_expiresUtcMillis) + ". .playtest off отзывает доступ.";
		}
		catch (Exception exception)
		{
			revoke();
			return "Привязка отклонена: private LocalPlay evidence недействительно.";
		}
	}

	private static boolean realClient(Player player)
	{
		final GameClient client = player.getClient();
		return player.isOnline() && !player.hasHeadlessOutboundSession() && (client != null) && !client.isDetached() && (client.getPlayer() == player) && (client.getConnectionState() == ConnectionState.IN_GAME) && (PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(player.getObjectId()) == OwnerKind.REAL_LOGIN);
	}

	private boolean sessionValid()
	{
		try
		{
			return (_lease != null) && (_player != null) && (_client != null) && realClient(_player) && (_player.getClient() == _client) && ownedProcess() && _lease.valid(_client.getAccountName(), _player.getObjectId(), _client, _pid, _startTicks);
		}
		catch (IOException exception)
		{
			return false;
		}
	}

	private void writeSession() throws IOException
	{
		final Properties session = new Properties();
		session.setProperty("version", "1");
		session.setProperty("sessionId", _lease.sessionId());
		session.setProperty("pid", Long.toString(_pid));
		session.setProperty("startTimeUtcTicks", Long.toString(_startTicks));
		session.setProperty("account", _client.getAccountName());
		session.setProperty("objectId", Integer.toString(_player.getObjectId()));
		session.setProperty("expiresUtcMillis", Long.toString(_expiresUtcMillis));
		session.setProperty("nextSequence", Long.toString(_lease.nextSequence()));
		session.setProperty("state", _runId == null ? "ARMED_IDLE" : "RUNNING");
		if (_stoppedRunId != null)
		{
			session.setProperty("stoppedRunId", _stoppedRunId);
		}
		writeProperties(_pilotRoot.resolve("session.properties"), session);
	}

	public synchronized String status(Player player)
	{
		if ((_poller == null) || !sessionValid() || (_player != player))
		{
			return "Пилот не привязан к этому клиенту.";
		}
		if ((System.nanoTime() - _lastReadNanos) < 250000000L)
		{
			return "THROTTLED";
		}
		_lastReadNanos = System.nanoTime();
		return "Пилот " + _lease.state() + ", конец согласия " + Instant.ofEpochMilli(_expiresUtcMillis) + "; snapshot через private mailbox.";
	}

	public synchronized String stop(Player player)
	{
		if (!sessionValid() || (_player != player))
		{
			return "Нет действующей привязки этого клиента.";
		}
		stopRun();
		return "Сценарий остановлен; согласие остаётся до срока или .playtest off.";
	}

	public synchronized String off(Player player)
	{
		if (!sessionValid() || (_player != player))
		{
			return "Нет действующей привязки этого клиента.";
		}
		stopRun();
		revoke();
		return "Доступ пилота отозван.";
	}

	public synchronized void onDisconnect(GameClient client)
	{
		if ((_client != null) && (_client == client))
		{
			revoke();
		}
	}

	public synchronized void onManualAction(Player player)
	{
		if ((_player != null) && (_player == player) && (_lease != null) && (_lease.state() == LocalPlayPilotLease.State.RUNNING))
		{
			stopRun();
		}
	}

	private void revoke()
	{
		cancelInbox();
		if (_actions != null)
		{
			_actions.cancelPendingInvitation();
		}
		if (_lease != null)
		{
			_lease.off();
		}
		if (_pilotRoot != null)
		{
			try
			{
				Files.deleteIfExists(_pilotRoot.resolve("session.properties"));
			}
			catch (IOException exception)
			{
				LOGGER.log(Level.FINE, "Pilot session file cleanup failed.", exception);
			}
		}
		_player = null;
		_client = null;
		_actions = null;
		_runId = null;
		_stoppedRunId = null;
		_runDeadlineNanos = 0;
	}

	private void stopRun()
	{
		if (_lease == null)
		{
			return;
		}
		if (_runId != null)
		{
			_stoppedRunId = _runId;
			_runId = null;
			_runDeadlineNanos = 0;
		}
		_lease.stop();
		cancelInbox();
		if (_actions != null)
		{
			_actions.cancelPendingInvitation();
		}
		try
		{
			writeSession();
		}
		catch (IOException exception)
		{
			revoke();
		}
	}

	private void cancelInbox()
	{
		if (_pilotRoot == null)
		{
			return;
		}
		final Path inbox = _pilotRoot.resolve("inbox");
		if (!Files.isDirectory(inbox, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(inbox))
		{
			return;
		}
		try (Stream<Path> pending = Files.list(inbox))
		{
			for (Path path : pending.limit(33).toList())
			{
				if (path.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.xml") && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
				{
					Files.deleteIfExists(path);
				}
			}
		}
		catch (IOException exception)
		{
			LOGGER.log(Level.FINE, "Pilot inbox cancellation failed.", exception);
		}
	}

	private void safePoll()
	{
		try
		{
			synchronized (this)
			{
				poll();
			}
		}
		catch (Exception exception)
		{
			LOGGER.log(Level.WARNING, "LocalPlay pilot mailbox stopped after a private I/O failure.", exception);
			shutdown();
		}
	}

	private void poll() throws Exception
	{
		if ((_pilotRoot == null) || (_lease == null))
		{
			return;
		}
		if (!mailboxSafe())
		{
			shutdown();
			return;
		}
		if (!sessionValid())
		{
			revoke();
			return;
		}
		final Path stopFile = _pilotRoot.resolve("stop.properties");
		if (Files.exists(stopFile, LinkOption.NOFOLLOW_LINKS))
		{
			final Properties stop = readProperties(stopFile);
			Files.deleteIfExists(stopFile);
			if ("1".equals(stop.getProperty("version")) && _lease.sessionId().equals(stop.getProperty("sessionId")))
			{
				stopRun();
				return;
			}
		}
		if ((_lease.state() == LocalPlayPilotLease.State.RUNNING) && ((System.nanoTime() - _runDeadlineNanos) >= 0 || !heartbeatCurrent()))
		{
			stopRun();
			return;
		}
		final Path inbox = _pilotRoot.resolve("inbox");
		if (Files.isSymbolicLink(inbox) || !Files.isDirectory(inbox, LinkOption.NOFOLLOW_LINKS))
		{
			throw new IOException("Pilot inbox was redirected.");
		}
		final List<Path> pending;
		try (Stream<Path> files = Files.list(inbox))
		{
			pending = files.filter(path -> path.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.xml")).sorted(Comparator.comparing(path -> path.getFileName().toString())).limit(33).toList();
		}
		if (pending.size() > 32)
		{
			stopRun();
			return;
		}
		if (!pending.isEmpty())
		{
			processRequest(pending.get(0));
		}
	}

	private boolean heartbeatCurrent()
	{
		final long now = System.nanoTime();
		if (_runId == null)
		{
			return false;
		}
		final Path heartbeat = _pilotRoot.resolve("heartbeat.properties");
		try
		{
			final Properties properties = readProperties(heartbeat);
			final long stamp = Long.parseLong(properties.getProperty("updatedUtcMillis", "0"));
			final long wallNow = System.currentTimeMillis();
			if ("1".equals(properties.getProperty("version")) && _lease.sessionId().equals(properties.getProperty("sessionId")) && _runId.equals(properties.getProperty("runId")) && (stamp <= wallNow + 5000) && (stamp >= wallNow - 30000))
			{
				_lastHeartbeatNanos = now;
			}
		}
		catch (Exception exception)
		{
			// The bounded watchdog handles an absent or malformed operator heartbeat.
		}
		return (now - _lastHeartbeatNanos) <= 30000000000L;
	}

	private void processRequest(Path inboxFile) throws Exception
	{
		final Path processing = _pilotRoot.resolve("processing").resolve(inboxFile.getFileName());
		Files.move(inboxFile, processing, StandardCopyOption.ATOMIC_MOVE);
		final LocalPlayPilotProtocol.Request request;
		try
		{
			request = LocalPlayPilotProtocol.read(processing);
		}
		catch (Exception exception)
		{
			Files.deleteIfExists(processing);
			return;
		}
		final Path resultFile = _pilotRoot.resolve("results").resolve(request.requestId() + ".xml");
		final Path journalFile = _pilotRoot.resolve("journal").resolve(request.requestId() + ".properties");
		if (Files.isRegularFile(resultFile, LinkOption.NOFOLLOW_LINKS))
		{
			Files.deleteIfExists(processing);
			return;
		}
		if (Files.exists(journalFile, LinkOption.NOFOLLOW_LINKS))
		{
			LocalPlayPilotProtocol.writeResult(resultFile, request, "UNCERTAIN", "CLAIMED_WITHOUT_RESULT", Instant.now(), Instant.now(), _player.getObjectId(), LocalPlayPilotActions.snapshot(_player), LocalPlayPilotActions.snapshot(_player), null);
			Files.deleteIfExists(processing);
			return;
		}
		if (!sessionValid() || !_lease.sessionId().equals(request.sessionId()) || request.runId().equals(_stoppedRunId) || ((_runId != null) && !_runId.equals(request.runId())) || request.deadline().isBefore(Instant.now()) || request.deadline().isAfter(Instant.now().plusSeconds(1200)))
		{
			LocalPlayPilotProtocol.writeResult(resultFile, request, "REJECTED", "SESSION_OR_DEADLINE", Instant.now(), Instant.now(), _player.getObjectId(), LocalPlayPilotActions.snapshot(_player), LocalPlayPilotActions.snapshot(_player), null);
			Files.deleteIfExists(processing);
			return;
		}
		if (countRecords(_pilotRoot.resolve("journal")) >= 512 || countRecords(_pilotRoot.resolve("results")) >= 512)
		{
			LocalPlayPilotProtocol.writeResult(resultFile, request, "REJECTED", "RETENTION_CAP", Instant.now(), Instant.now(), _player.getObjectId(), LocalPlayPilotActions.snapshot(_player), LocalPlayPilotActions.snapshot(_player), null);
			Files.deleteIfExists(processing);
			return;
		}
		if (!_lease.claim(request.sessionId(), request.sequence(), request.requestId()))
		{
			LocalPlayPilotProtocol.writeResult(resultFile, request, "REJECTED", "SEQUENCE_OR_CONSENT", Instant.now(), Instant.now(), _player.getObjectId(), LocalPlayPilotActions.snapshot(_player), LocalPlayPilotActions.snapshot(_player), null);
			Files.deleteIfExists(processing);
			return;
		}
		if (_runId == null)
		{
			_runId = request.runId();
			_lastHeartbeatNanos = System.nanoTime();
			_runDeadlineNanos = System.nanoTime() + (20L * 60 * 1000000000L);
		}
		final Properties claim = new Properties();
		claim.setProperty("version", "1");
		claim.setProperty("requestId", request.requestId());
		claim.setProperty("sessionId", request.sessionId());
		claim.setProperty("runId", request.runId());
		claim.setProperty("sequence", Long.toString(request.sequence()));
		claim.setProperty("state", "CLAIMED");
		writeProperties(journalFile, claim);
		writeSession();
		if (!sessionValid() || request.runId().equals(_stoppedRunId))
		{
			LocalPlayPilotProtocol.writeResult(resultFile, request, "CANCELLED", "SESSION_REVOKED_BEFORE_ACTION", Instant.now(), Instant.now(), _player.getObjectId(), LocalPlayPilotActions.snapshot(_player), LocalPlayPilotActions.snapshot(_player), null);
			Files.deleteIfExists(processing);
			return;
		}
		final Instant started = Instant.now();
		final Player actor = _player;
		final var before = LocalPlayPilotActions.snapshot(actor);
		final LocalPlayPilotActions.Outcome outcome;
		try
		{
			outcome = _actions.execute(actor, request);
		}
		catch (RuntimeException exception)
		{
			LOGGER.log(Level.WARNING, "Claimed pilot native action had an uncertain outcome.", exception);
			LocalPlayPilotProtocol.writeResult(resultFile, request, "UNCERTAIN", "NATIVE_EXCEPTION_AFTER_CLAIM", started, Instant.now(), actor.getObjectId(), before, LocalPlayPilotActions.snapshot(actor), null);
			Files.deleteIfExists(processing);
			stopRun();
			return;
		}
		final var after = LocalPlayPilotActions.snapshot(actor);
		LocalPlayPilotProtocol.writeResult(resultFile, request, outcome.status(), outcome.reason(), started, Instant.now(), actor.getObjectId(), before, after, outcome.candidate());
		Files.deleteIfExists(processing);
	}

	private static long countRecords(Path root) throws IOException
	{
		try (Stream<Path> files = Files.list(root))
		{
			return files.limit(513).count();
		}
	}
}

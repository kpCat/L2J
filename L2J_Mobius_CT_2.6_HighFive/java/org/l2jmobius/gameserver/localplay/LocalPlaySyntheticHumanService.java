package org.l2jmobius.gameserver.localplay;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import org.l2jmobius.gameserver.config.custom.LocalPlayPilotConfig;

/** Separate LocalPlay-only transport. Polled by the existing Pilot worker; no consent imitation. */
final class LocalPlaySyntheticHumanService implements AutoCloseable
{
	private final Path _root;
	private final String _runtimeId;
	private final long _pid;
	private final long _startTicks;
	private final BooleanSupplier _owned;
	private final BooleanSupplier _startGuard;
	private final Set<String> _started = new HashSet<>();
	private LocalPlaySyntheticHumanSession _session;
	private Path _mailbox;
	private String _runId;
	private long _sequence;
	private long _deadlineNanos;
	private long _expiresUtcMillis;
	private String _state = "OFF";
	private String _reason = "NOT_STARTED";
	private String _heartbeatReason = "NOT_READ";
	private long _setupProfileId;
	private org.l2jmobius.gameserver.model.Location _initialPosition;

	LocalPlaySyntheticHumanService(Path runtime, String runtimeId, long pid, long startTicks, BooleanSupplier owned, BooleanSupplier startGuard)
	{
		_root = runtime.resolve("playtest-synthetic"); _runtimeId = runtimeId; _pid = pid; _startTicks = startTicks; _owned = owned; _startGuard = startGuard;
	}

	void poll() throws Exception
	{
		if (!LocalPlayPilotService.safeDirectory(_root) || !LocalPlayPilotService.privateAcl(_root) || !_owned.getAsBoolean())
		{
			close();
			return;
		}
		final Path controlFile = _root.resolve("control.properties");
		if (Files.exists(controlFile, LinkOption.NOFOLLOW_LINKS))
		{
			if (!LocalPlayPilotService.privateAcl(controlFile)) { close(); throw new IllegalStateException("SYNTHETIC_CONTROL_ACL"); }
			final Properties control = LocalPlayPilotService.readProperties(controlFile);
			Files.delete(controlFile);
			final String runId = LocalPlayPilotProtocol.canonicalUuid(control.getProperty("runId", ""));
			if (!"1".equals(control.getProperty("version")) || !_runtimeId.equals(control.getProperty("runtimeId")) || !Long.toString(_pid).equals(control.getProperty("pid")) || !Long.toString(_startTicks).equals(control.getProperty("startTimeUtcTicks"))) { close(); throw new IllegalStateException("SYNTHETIC_CONTROL_INCARNATION"); }
			if ("STOP".equals(control.getProperty("command")))
			{
				if (runId.equals(_runId)) { close(); }
			}
			else if ("START".equals(control.getProperty("command"))) { start(runId, control); }
			else { close(); throw new IllegalStateException("SYNTHETIC_CONTROL_COMMAND"); }
		}
		if (_session == null) { return; }
		if (!LocalPlayPilotService.mailboxSafe(_mailbox)) { _reason = "SYNTHETIC_MAILBOX_SAFETY"; close(); return; }
		if (!_session.valid()) { _reason = "SYNTHETIC_IDENTITY"; close(); return; }
		if (System.nanoTime() >= _deadlineNanos) { _reason = "SYNTHETIC_TTL"; close(); return; }
		if (!heartbeatCurrent()) { _reason = "SYNTHETIC_HEARTBEAT:" + _heartbeatReason; close(); return; }
		final java.util.List<Path> pending;
		try (Stream<Path> files = Files.list(_mailbox.resolve("inbox")))
		{
			pending = files.filter(path -> path.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.xml")).sorted(Comparator.comparing(path -> path.getFileName().toString())).limit(33).toList();
		}
		if (pending.size() > 32) { _reason = "SYNTHETIC_INBOX_CAP"; close(); return; }
		if (!pending.isEmpty()) { process(pending.get(0)); }
	}

	private void start(String runId, Properties control) throws Exception
	{
		final long expires = Long.parseLong(control.getProperty("expiresUtcMillis", "0"));
		final long now = System.currentTimeMillis();
		if ((_session != null) || _started.contains(runId) || (_started.size() >= 5)) { _reason = "SYNTHETIC_ACTIVE_REPLAY_OR_RUN_CAP"; writeState(); return; }
		_runId = runId; _mailbox = _root.resolve(runId); _sequence = 1; _expiresUtcMillis = 0;
		if (!_startGuard.getAsBoolean() || (expires <= now) || (expires > now + 60000) || !LocalPlayPilotService.mailboxSafe(_mailbox)) { _state = "REJECTED"; _reason = "SYNTHETIC_START_GUARD"; writeState(); return; }
		_started.add(runId);
		_session = new LocalPlaySyntheticHumanSession(LocalPlayPilotConfig.syntheticObjectId(), LocalPlayPilotConfig.syntheticName());
		try
		{
			_setupProfileId = Long.parseLong(control.getProperty("setupProfileId", "0"));
			_initialPosition = null;
			if (_setupProfileId < 0) { throw new IllegalArgumentException("SYNTHETIC_SETUP_PROFILE_INVALID"); }
			if (_setupProfileId == 0) { _session.start(); }
			else
			{
				final var target = org.l2jmobius.gameserver.phantoms.PhantomSystem.operatorSetupLocalityTarget(_setupProfileId).orElseThrow(() -> new IllegalArgumentException("SYNTHETIC_SETUP_UNAVAILABLE"));
				final var point = target.committedPosition();
				_session.start(new org.l2jmobius.gameserver.model.Location(point.x(), point.y(), point.z(), 0, point.instanceId()));
			}
			_initialPosition = _session.actor().getLocation().clone();
			_deadlineNanos = System.nanoTime() + 525_000_000_000L;
			_expiresUtcMillis = System.currentTimeMillis() + 525000;
			_state = "RUNNING"; _reason = "SYNTHETIC_NATIVE_PLAYER_STARTED";
			writeState();
		}
		catch (RuntimeException failure)
		{
			_reason = failure.getMessage(); close(); _state = "REJECTED"; writeState();
		}
	}

	private boolean heartbeatCurrent()
	{
		try
		{
			final Path file = _mailbox.resolve("heartbeat.properties");
			if (!LocalPlayPilotService.privateAcl(file)) { _heartbeatReason = "ACL_OR_ABSENT"; return false; }
			final Properties value = LocalPlayPilotService.readProperties(file);
			final long stamp = Long.parseLong(value.getProperty("updatedUtcMillis", "0"));
			if (!"1".equals(value.getProperty("version")) || !_runId.equals(value.getProperty("sessionId")) || !_runId.equals(value.getProperty("runId"))) { _heartbeatReason = "IDENTITY"; return false; }
			final long now = System.currentTimeMillis();
			_heartbeatReason = "STAMP_AGE_MILLIS=" + (now - stamp);
			return (stamp <= now + 5000) && (stamp >= now - 30000);
		}
		catch (Exception exception) { _heartbeatReason = "READ_FAILURE:" + exception.getClass().getSimpleName() + ":" + exception.getMessage(); return false; }
	}

	private void process(Path file) throws Exception
	{
		if (!LocalPlayPilotService.privateAcl(file)) { _reason = "SYNTHETIC_REQUEST_ACL"; close(); return; }
		final Path processing = _mailbox.resolve("processing").resolve(file.getFileName());
		Files.move(file, processing, StandardCopyOption.ATOMIC_MOVE);
		final var request = LocalPlayPilotProtocol.read(processing);
		final Path result = _mailbox.resolve("results").resolve(request.requestId() + ".xml");
		final Path journal = _mailbox.resolve("journal").resolve(request.requestId() + ".properties");
		if (Files.exists(result, LinkOption.NOFOLLOW_LINKS)) { Files.delete(processing); return; }
		final var actor = _session.actor();
		final var before = LocalPlayPilotActions.snapshot(actor);
		final Instant start = Instant.now();
		String status = "REJECTED"; String reason = "SYNTHETIC_SEQUENCE_RUN_OR_DEADLINE"; Map<String, String> candidate = null;
		if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS)) { status = "UNCERTAIN"; reason = "CLAIMED_WITHOUT_RESULT"; }
		else if (_runId.equals(request.sessionId()) && _runId.equals(request.runId()) && (_sequence == request.sequence()) && (request.deadline().isAfter(start)) && (request.deadline().isBefore(start.plusSeconds(120))) && (_sequence <= 400))
		{
			final Properties claim = new Properties(); claim.setProperty("requestId", request.requestId()); claim.setProperty("state", "CLAIMED");
			LocalPlayPilotService.writeProperties(journal, claim);
			_sequence++; writeState();
			try { final var outcome = _session.execute(request); status = outcome.status(); reason = outcome.reason(); candidate = outcome.candidate(); }
			catch (RuntimeException exception) { status = "UNCERTAIN"; reason = "SYNTHETIC_NATIVE_EXCEPTION:" + exception.getClass().getSimpleName() + ":" + exception.getMessage(); }
		}
		LocalPlayPilotProtocol.writeResult(result, request, status, reason, start, Instant.now(), actor.getObjectId(), before, LocalPlayPilotActions.snapshot(actor), candidate, "SYNTHETIC_SERVER", "LOCALPLAY_SYNTHETIC");
		Files.delete(processing);
		if ("UNCERTAIN".equals(status)) { _reason = reason; close(); }
	}

	private void writeState() throws java.io.IOException
	{
		if (!LocalPlayPilotService.safeDirectory(_root) || !LocalPlayPilotService.privateAcl(_root)) { return; }
		final Properties state = new Properties();
		state.setProperty("version", "1"); state.setProperty("sessionId", _runId == null ? "" : _runId);
		state.setProperty("pid", Long.toString(_pid)); state.setProperty("startTimeUtcTicks", Long.toString(_startTicks));
		state.setProperty("state", _state); state.setProperty("reason", _reason);
		state.setProperty("objectId", Integer.toString(LocalPlayPilotConfig.syntheticObjectId()));
		state.setProperty("nextSequence", Long.toString(_sequence)); state.setProperty("expiresUtcMillis", Long.toString(_expiresUtcMillis));
		state.setProperty("actorMode", "SYNTHETIC");
		state.setProperty("setupProfileId", Long.toString(_setupProfileId));
		if (_initialPosition != null)
		{
			state.setProperty("initialX", Integer.toString(_initialPosition.getX())); state.setProperty("initialY", Integer.toString(_initialPosition.getY())); state.setProperty("initialZ", Integer.toString(_initialPosition.getZ()));
			state.setProperty("initialInstanceId", Integer.toString(_initialPosition.getInstanceId()));
		}
		LocalPlayPilotService.writeProperties(_root.resolve("session.properties"), state);
		if ((_mailbox != null) && LocalPlayPilotService.safeDirectory(_mailbox) && LocalPlayPilotService.privateAcl(_mailbox)) { LocalPlayPilotService.writeProperties(_mailbox.resolve("session.properties"), state); }
	}

	@Override
	public void close()
	{
		try
		{
			if (_session != null) { _session.close(); _session = null; }
			_state = "STOPPED"; writeState();
		}
		catch (Exception failure) { _state = "CLEANUP_FAILED"; _reason = "SYNTHETIC_CLEANUP_FAILED:" + failure.getMessage(); throw new IllegalStateException(_reason, failure); }
	}
}

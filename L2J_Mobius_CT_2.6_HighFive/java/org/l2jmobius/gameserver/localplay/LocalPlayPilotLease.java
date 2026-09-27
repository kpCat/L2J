package org.l2jmobius.gameserver.localplay;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** One real-client consent lease; no Player or transport is created here. */
public final class LocalPlayPilotLease
{
	private final String _nonce;
	private final String _expectedName;
	private final long _pid;
	private final long _startTicks;
	private final long _armDeadlineNanos;
	private final LongSupplier _clock;
	private State _state = State.WAITING_ARM;
	private String _account;
	private int _objectId;
	private Object _client;
	private String _sessionId;
	private long _expiresNanos;
	private long _nextSequence = 1;
	private String _lastRequestId;

	public enum State
	{
		WAITING_ARM,
		ARMED_IDLE,
		RUNNING,
		OFF
	}

	public LocalPlayPilotLease(String nonce, String expectedName, long pid, long startTicks, long armDeadlineNanos, LongSupplier clock)
	{
		_nonce = Objects.requireNonNull(nonce);
		_expectedName = Objects.requireNonNull(expectedName);
		_pid = pid;
		_startTicks = startTicks;
		_armDeadlineNanos = armDeadlineNanos;
		_clock = Objects.requireNonNull(clock);
	}

	public synchronized boolean arm(String nonce, String name, String account, int objectId, Object client, long pid, long startTicks, long consentNanos)
	{
		if ((_state != State.WAITING_ARM) || (_clock.getAsLong() >= _armDeadlineNanos) || !_nonce.equals(nonce) || !_expectedName.equals(name) || (account == null) || account.isBlank() || (objectId <= 0) || (client == null) || (pid != _pid) || (startTicks != _startTicks) || (consentNanos <= 0))
		{
			return false;
		}
		_account = account;
		_objectId = objectId;
		_client = client;
		_sessionId = UUID.randomUUID().toString();
		_expiresNanos = _clock.getAsLong() + consentNanos;
		_state = State.ARMED_IDLE;
		return true;
	}

	public synchronized boolean valid(String account, int objectId, Object client, long pid, long startTicks)
	{
		if ((_state == State.OFF) || (_state == State.WAITING_ARM))
		{
			return false;
		}
		if (_clock.getAsLong() >= _expiresNanos)
		{
			off();
			return false;
		}
		return _account.equals(account) && (_objectId == objectId) && (_client == client) && (_pid == pid) && (_startTicks == startTicks);
	}

	public synchronized boolean claim(String sessionId, long sequence, String requestId)
	{
		if ((_state != State.ARMED_IDLE) && (_state != State.RUNNING))
		{
			return false;
		}
		if (_clock.getAsLong() >= _expiresNanos)
		{
			off();
			return false;
		}
		if (!Objects.equals(_sessionId, sessionId) || (sequence != _nextSequence) || (requestId == null) || requestId.isBlank() || requestId.equals(_lastRequestId))
		{
			return false;
		}
		_lastRequestId = requestId;
		_nextSequence++;
		_state = State.RUNNING;
		return true;
	}

	public synchronized void stop()
	{
		if (_state == State.RUNNING)
		{
			_state = State.ARMED_IDLE;
		}
	}

	public synchronized void off()
	{
		_state = State.OFF;
		_client = null;
		_sessionId = null;
		_account = null;
		_lastRequestId = null;
	}

	public synchronized State state()
	{
		if (((_state == State.ARMED_IDLE) || (_state == State.RUNNING)) && (_clock.getAsLong() >= _expiresNanos))
		{
			off();
		}
		return _state;
	}

	public synchronized String sessionId()
	{
		return _sessionId;
	}

	public synchronized long nextSequence()
	{
		return _nextSequence;
	}
}

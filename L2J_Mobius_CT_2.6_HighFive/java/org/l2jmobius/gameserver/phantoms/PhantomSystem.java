/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms;

import java.util.Objects;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ShutdownResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ServiceState;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/**
 * Lifecycle owner for the disabled-by-default Phantom World subsystem.
 */
public final class PhantomSystem
{
	public static final int QUEUE_CAPACITY = 256;
	public static final int TRACE_CAPACITY = 64;
	public static final int TRACE_SAMPLE_EVERY = 16;

	private static PhantomSystem _configuredInstance;

	private final PhantomPlayersConfig.Settings _settings;
	private final PhantomMetrics _metrics;
	private final PhantomScheduler _scheduler;
	private final PhantomDiagnosticTrace _trace;
	private final boolean _productionMaterialization;
	private PhantomMaterializationService _materializationService;
	private State _state = State.NEW;

	public PhantomSystem(PhantomPlayersConfig.Settings settings)
	{
		this(settings, false);
	}

	private PhantomSystem(PhantomPlayersConfig.Settings settings, boolean productionMaterialization)
	{
		_settings = Objects.requireNonNull(settings);
		_productionMaterialization = productionMaterialization;
		_metrics = new PhantomMetrics();
		if (settings.enabled())
		{
			_scheduler = new PhantomScheduler(QUEUE_CAPACITY, _metrics);
			_trace = new PhantomDiagnosticTrace(settings.diagnosticsEnabled(), TRACE_CAPACITY, TRACE_SAMPLE_EVERY, _metrics);
		}
		else
		{
			_scheduler = null;
			_trace = null;
		}
	}

	public synchronized boolean start()
	{
		if (_state != State.NEW)
		{
			return false;
		}

		if (!_settings.enabled())
		{
			_state = State.DISABLED;
			return false;
		}

		if (!_scheduler.start())
		{
			throw new IllegalStateException("Phantom queue could not enter the running state.");
		}
		try
		{
			if (_productionMaterialization)
			{
				final PhantomProfileRepository profileRepository = PhantomProfileRepository.open();
				_materializationService = new PhantomMaterializationService(profileRepository, PhantomIdentityLeaseRegistry.getInstance(), _metrics, _trace, _settings.maxMaterializedPhantoms());
				if (!_materializationService.start())
				{
					throw new IllegalStateException("Phantom materialization service could not enter the running state.");
				}
			}
		}
		catch (RuntimeException e)
		{
			if (_materializationService != null)
			{
				_materializationService.shutdown();
			}
			_scheduler.stop();
			_state = State.STOPPED;
			throw e;
		}
		_metrics.recordLifecycleStart();
		_state = State.RUNNING;
		return true;
	}

	public synchronized boolean shutdown()
	{
		if (_state == State.STOPPED)
		{
			return false;
		}

		if (_state == State.RUNNING)
		{
			if (_materializationService != null)
			{
				final ShutdownResult result = _materializationService.shutdown();
				if (result.state() != ServiceState.STOPPED)
				{
					_state = State.FAILED;
					return false;
				}
			}
			_scheduler.stop();
			_metrics.recordLifecycleStop();
			_state = State.STOPPED;
			return true;
		}
		if ((_state == State.FAILED) && (_materializationService != null))
		{
			final ShutdownResult result = _materializationService.shutdown();
			if (result.state() != ServiceState.STOPPED)
			{
				return false;
			}
			_scheduler.stop();
			_metrics.recordLifecycleStop();
			_state = State.STOPPED;
			return true;
		}

		_state = State.STOPPED;
		return false;
	}

	public synchronized Snapshot snapshot()
	{
		return new Snapshot(_state, _settings, _scheduler != null ? _scheduler.snapshot() : PhantomScheduler.Snapshot.inactive(), _metrics.snapshot(), _trace != null ? _trace.snapshot() : PhantomDiagnosticTrace.Snapshot.disabled());
	}

	public static synchronized boolean startConfigured()
	{
		if (!PhantomPlayersConfig.isEnabled() || (_configuredInstance != null))
		{
			return false;
		}

		final PhantomSystem candidate = new PhantomSystem(PhantomPlayersConfig.settings(), true);
		try
		{
			if (!candidate.start())
			{
				throw new IllegalStateException("Configured Phantom World skeleton did not start.");
			}
			_configuredInstance = candidate;
			return true;
		}
		catch (RuntimeException e)
		{
			candidate.shutdown();
			throw e;
		}
	}

	public static synchronized boolean shutdownIfStarted()
	{
		if (_configuredInstance == null)
		{
			return false;
		}

		final PhantomSystem configured = _configuredInstance;
		final boolean stopped = configured.shutdown();
		if (configured.snapshot().state() == State.STOPPED)
		{
			_configuredInstance = null;
		}
		return stopped;
	}

	public static synchronized boolean hasConfiguredInstance()
	{
		return _configuredInstance != null;
	}

	static synchronized PhantomMaterializationService configuredMaterializationService()
	{
		return _configuredInstance == null ? null : _configuredInstance._materializationService;
	}

	public enum State
	{
		NEW,
		DISABLED,
		RUNNING,
		FAILED,
		STOPPED
	}

	public record Snapshot(State state, PhantomPlayersConfig.Settings settings, PhantomScheduler.Snapshot scheduler, PhantomMetrics.Snapshot metrics, PhantomDiagnosticTrace.Snapshot trace)
	{
	}
}

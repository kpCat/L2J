/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.phantoms.activity.PhantomMaterializationRetentionPolicy.HumanObservation;
import org.l2jmobius.gameserver.phantoms.topology.PhantomNativeLocalityEnvelope;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Call-time observation only. A future transition must recheck inside its canonical commit admission. */
public final class PhantomOffscreenObserverAdmission
{
	public enum Code
	{
		ALLOW, DENY_RECENT_HUMAN, DENY_SOURCE_HUMAN, DENY_DESTINATION_HUMAN,
		HUMAN_CENSUS_OVERFLOW, UNKNOWN_RECENT_HUMAN, UNKNOWN_WORLD, UNKNOWN_INSTANCE,
		UNKNOWN_HUMAN_CENSUS, UNKNOWN_AUTONOMOUS_OBSERVER, UNKNOWN_RUNTIME, UNKNOWN_PROFILE
	}

	public record Verdict(Code code, long profileId, PhantomTopologyPoint source, PhantomTopologyPoint destination, int instanceId, long checkedAtNanos, int inspectedHumans)
	{
		public Verdict { Objects.requireNonNull(code); }
	}

	private final PhantomMaterializationRetentionPolicy _retention;
	private final LongSupplier _clock;

	public PhantomOffscreenObserverAdmission(PhantomMaterializationRetentionPolicy retention, LongSupplier clock)
	{
		_retention = Objects.requireNonNull(retention);
		_clock = Objects.requireNonNull(clock);
	}

	/** The iterable is live and lazy, never the locality pipeline's truncated point list. */
	public Verdict evaluate(long profileId, PhantomTopologyPoint source, PhantomTopologyPoint destination, int instanceId, Supplier<? extends Iterable<PhantomTopologyPoint>> humans, Supplier<PhantomTopologyPoint> autonomous)
	{
		int inspected = 0;
		try
		{
			if (profileId <= 0) { return verdict(Code.UNKNOWN_PROFILE, profileId, source, destination, instanceId, inspected); }
			if ((source == null) || (destination == null) || (PhantomNativeLocalityEnvelope.region(source) == null) || (PhantomNativeLocalityEnvelope.region(destination) == null))
			{
				return verdict(Code.UNKNOWN_WORLD, profileId, source, destination, instanceId, inspected);
			}
			if ((instanceId != 0) || (source.instanceId() != instanceId) || (destination.instanceId() != instanceId))
			{
				return verdict(Code.UNKNOWN_INSTANCE, profileId, source, destination, instanceId, inspected);
			}
			Code code = recentCode(profileId);
			if (code != Code.ALLOW) { return verdict(code, profileId, source, destination, instanceId, inspected); }
			for (PhantomTopologyPoint human : humans.get())
			{
				if (++inspected > 256) { return verdict(Code.HUMAN_CENSUS_OVERFLOW, profileId, source, destination, instanceId, inspected); }
				if ((human == null) || (PhantomNativeLocalityEnvelope.region(human) == null))
				{
					return verdict(Code.UNKNOWN_HUMAN_CENSUS, profileId, source, destination, instanceId, inspected);
				}
				if (PhantomNativeLocalityEnvelope.prewarm(human, source)) { code = Code.DENY_SOURCE_HUMAN; }
				else if ((code == Code.ALLOW) && PhantomNativeLocalityEnvelope.prewarm(human, destination)) { code = Code.DENY_DESTINATION_HUMAN; }
			}
			if (inspected == 0)
			{
				final PhantomTopologyPoint simulation = autonomous.get();
				if (simulation != null)
				{
					code = PhantomNativeLocalityEnvelope.prewarm(simulation, source) ? Code.DENY_SOURCE_HUMAN
						: PhantomNativeLocalityEnvelope.prewarm(simulation, destination) ? Code.DENY_DESTINATION_HUMAN : Code.UNKNOWN_AUTONOMOUS_OBSERVER;
				}
			}
			// Observation can be renewed while the live census is read; never use the older recent-human answer.
			if (code == Code.ALLOW) { code = recentCode(profileId); }
			return verdict(code, profileId, source, destination, instanceId, inspected);
		}
		catch (RuntimeException unavailable)
		{
			return new Verdict(Code.UNKNOWN_HUMAN_CENSUS, profileId, source, destination, instanceId, 0, inspected);
		}
	}

	private Code recentCode(long profileId)
	{
		final HumanObservation recent = _retention.recentHumanObservation(profileId);
		return recent == HumanObservation.CLEAR ? Code.ALLOW : recent == HumanObservation.RECENT ? Code.DENY_RECENT_HUMAN : Code.UNKNOWN_RECENT_HUMAN;
	}

	private Verdict verdict(Code code, long profileId, PhantomTopologyPoint source, PhantomTopologyPoint destination, int instanceId, int inspected)
	{
		return new Verdict(code, profileId, source, destination, instanceId, _clock.getAsLong(), inspected);
	}

	/** Native offline owners and headless Phantom sessions cannot see the world. */
	public static boolean humanObserver(boolean online, boolean headless, boolean nativeConnected, boolean localplayTestHuman)
	{
		return online && !headless && (nativeConnected || localplayTestHuman);
	}
}

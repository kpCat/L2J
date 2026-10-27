/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.actor.Player;

/** Physical holds belong to live materializations; recent observation survives storage until expiry. */
public final class PhantomMaterializationRetentionPolicy
{
	public enum Reason
	{
		NATIVE_VISIBLE, REAL_PARTY, ACTIVE_ACTION, RECENT_HUMAN
	}

	public enum HumanObservation
	{
		RECENT, CLEAR, UNKNOWN
	}

	public record Facts(boolean materialized, boolean humanLocal, boolean nativeVisible, boolean realParty, boolean activeAction)
	{
		public static Facts nativeFacts(Player player, boolean humanLocal, boolean nativeVisible, int admittedActions)
		{
			final var party = player.getParty();
			final boolean realParty = (party != null) && party.getMembers().stream().anyMatch(member -> !member.hasHeadlessOutboundSession());
			return new Facts(true, humanLocal, nativeVisible, realParty, (admittedActions > 0) || player.isMoving() || player.isAttackingNow() || player.isCastingNow() || player.isTeleporting() || player.isInCombat());
		}
	}

	public record Hold(Set<Reason> reasons, long lastHumanNanos, boolean humanLocal, boolean materialized)
	{
		public Hold
		{
			reasons = Set.copyOf(reasons);
		}

		public boolean hard()
		{
			return reasons.stream().anyMatch(reason -> reason != Reason.RECENT_HUMAN);
		}

		public boolean retained()
		{
			return !reasons.isEmpty();
		}

		public boolean reclaimable()
		{
			return materialized && !humanLocal && !retained();
		}
	}

	private final LongFunction<Facts> _facts;
	private final LongSupplier _clock;
	private final long _recentHumanNanos;
	private final long _startedAtNanos;
	private final Map<Long, Long> _lastHuman = new ConcurrentHashMap<>();
	private Iterator<Map.Entry<Long, Long>> _expiryCursor;

	public PhantomMaterializationRetentionPolicy(LongFunction<Facts> facts, LongSupplier clock, long recentHumanMillis)
	{
		if ((recentHumanMillis < 1) || (recentHumanMillis > 120_000))
		{
			throw new IllegalArgumentException("Recent human continuity must be between 1 and 120000 milliseconds.");
		}
		_facts = Objects.requireNonNull(facts);
		_clock = Objects.requireNonNull(clock);
		_recentHumanNanos = recentHumanMillis * 1_000_000L;
		_startedAtNanos = _clock.getAsLong();
	}

	public Hold observe(long profileId)
	{
		final Facts facts = _facts.apply(profileId);
		final long now = _clock.getAsLong();
		final Long previous = _lastHuman.get(profileId);
		if ((previous != null) && ((now - previous) >= _recentHumanNanos)) { _lastHuman.remove(profileId, previous); }
		if (!facts.materialized())
		{
			final Long lastHuman = _lastHuman.get(profileId);
			return new Hold(Set.of(), lastHuman == null ? 0 : lastHuman, false, false);
		}
		if (facts.humanLocal() || facts.nativeVisible())
		{
			// A delayed observer must not shorten a newer confirmation's full grace.
			_lastHuman.merge(profileId, now, (previousTime, currentTime) -> (currentTime - previousTime) >= 0 ? currentTime : previousTime);
		}
		final Long lastHuman = _lastHuman.get(profileId);
		final var reasons = EnumSet.noneOf(Reason.class);
		if (facts.nativeVisible())
		{
			reasons.add(Reason.NATIVE_VISIBLE);
		}
		if (facts.realParty())
		{
			reasons.add(Reason.REAL_PARTY);
		}
		if (facts.activeAction())
		{
			reasons.add(Reason.ACTIVE_ACTION);
		}
		if ((lastHuman != null) && ((now - lastHuman) >= 0) && ((now - lastHuman) < _recentHumanNanos))
		{
			reasons.add(Reason.RECENT_HUMAN);
		}
		return new Hold(reasons, lastHuman == null ? 0 : lastHuman, facts.humanLocal(), true);
	}

	/** Existing work retains its hard hold, but cannot itself renew permission for another ordinary root. */
	public boolean permitsNewActions(long profileId)
	{
		final Hold hold = observe(profileId);
		return hold.humanLocal() || hold.reasons().contains(Reason.NATIVE_VISIBLE) || hold.reasons().contains(Reason.REAL_PARTY) || hold.reasons().contains(Reason.RECENT_HUMAN);
	}

	public void refresh(List<Long> materializedProfiles)
	{
		expireObservations();
		materializedProfiles.forEach(this::observe);
	}

	/** Read-only: missing restart history cannot certify absence until one full grace has elapsed. */
	public HumanObservation recentHumanObservation(long profileId)
	{
		if (profileId <= 0) { return HumanObservation.UNKNOWN; }
		final long now = _clock.getAsLong();
		final Long last = _lastHuman.get(profileId);
		if (last != null)
		{
			final long elapsed = now - last;
			if (elapsed < 0) { return HumanObservation.UNKNOWN; }
			if (elapsed < _recentHumanNanos) { return HumanObservation.RECENT; }
		}
		final long uptime = now - _startedAtNanos;
		return (uptime < 0) || (uptime < _recentHumanNanos) ? HumanObservation.UNKNOWN : HumanObservation.CLEAR;
	}

	/** A resumable bounded sweep also reclaims stored profiles that are never observed again. */
	private synchronized void expireObservations()
	{
		final long now = _clock.getAsLong();
		if ((_expiryCursor == null) || !_expiryCursor.hasNext()) { _expiryCursor = _lastHuman.entrySet().iterator(); }
		for (int inspected = 0; (inspected < 256) && _expiryCursor.hasNext(); inspected++)
		{
			final var entry = _expiryCursor.next();
			if ((now - entry.getValue()) >= _recentHumanNanos) { _lastHuman.remove(entry.getKey(), entry.getValue()); }
		}
	}

	public long oldestSoft(List<Long> materializedProfiles)
	{
		long selected = 0;
		long oldest = Long.MAX_VALUE;
		for (long id : materializedProfiles)
		{
			final Hold hold = observe(id);
			if (hold.reclaimable() && ((hold.lastHumanNanos() < oldest) || ((hold.lastHumanNanos() == oldest) && ((selected == 0) || (id < selected)))))
			{
				selected = id;
				oldest = hold.lastHumanNanos();
			}
		}
		return selected;
	}
}

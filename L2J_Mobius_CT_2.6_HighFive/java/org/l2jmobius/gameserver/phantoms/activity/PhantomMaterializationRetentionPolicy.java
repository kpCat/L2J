/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.activity;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.actor.Player;

/** Bounded to live materializations; reasons never replace native ownership. */
public final class PhantomMaterializationRetentionPolicy
{
	public enum Reason
	{
		NATIVE_VISIBLE, REAL_PARTY, ACTIVE_ACTION, RECENT_HUMAN
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

	public record Hold(Set<Reason> reasons, long lastHumanNanos, boolean humanLocal)
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
			return !humanLocal && !hard() && reasons.contains(Reason.RECENT_HUMAN);
		}
	}

	private final LongFunction<Facts> _facts;
	private final LongSupplier _clock;
	private final long _recentHumanNanos;
	private final Map<Long, Long> _lastHuman = new ConcurrentHashMap<>();

	public PhantomMaterializationRetentionPolicy(LongFunction<Facts> facts, LongSupplier clock, long recentHumanMillis)
	{
		if ((recentHumanMillis < 1) || (recentHumanMillis > 120_000))
		{
			throw new IllegalArgumentException("Recent human continuity must be between 1 and 120000 milliseconds.");
		}
		_facts = Objects.requireNonNull(facts);
		_clock = Objects.requireNonNull(clock);
		_recentHumanNanos = recentHumanMillis * 1_000_000L;
	}

	public Hold observe(long profileId)
	{
		final Facts facts = _facts.apply(profileId);
		if (!facts.materialized())
		{
			_lastHuman.remove(profileId);
			return new Hold(Set.of(), 0, false);
		}
		final long now = _clock.getAsLong();
		if (facts.humanLocal() || facts.nativeVisible())
		{
			_lastHuman.put(profileId, now);
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
		if ((lastHuman != null) && (now >= lastHuman) && ((now - lastHuman) < _recentHumanNanos))
		{
			reasons.add(Reason.RECENT_HUMAN);
		}
		return new Hold(reasons, lastHuman == null ? 0 : lastHuman, facts.humanLocal());
	}

	/** Existing work retains its hard hold, but cannot itself renew permission for another ordinary root. */
	public boolean permitsNewActions(long profileId)
	{
		final Hold hold = observe(profileId);
		return hold.humanLocal() || hold.reasons().contains(Reason.NATIVE_VISIBLE) || hold.reasons().contains(Reason.REAL_PARTY) || hold.reasons().contains(Reason.RECENT_HUMAN);
	}

	public void refresh(List<Long> materializedProfiles)
	{
		final Set<Long> live = Set.copyOf(materializedProfiles);
		_lastHuman.keySet().removeIf(id -> !live.contains(id));
		materializedProfiles.forEach(this::observe);
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

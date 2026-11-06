/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Objects;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort.StoredLogicalGroup;

/** Logical agreement boundary, called by the existing Party control port; never creates a Player. */
public final class PhantomLogicalPartyCoordinator
{
	private final PhantomPartyPersistencePort _store;
	private final Map<Long, Discovered> _discovered = new LinkedHashMap<>();
	private long _leaderCursor;
	private final java.util.concurrent.ConcurrentMap<Long, StoredLogicalGroup> _knownMembers = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.concurrent.ConcurrentMap<Long, StoredLogicalGroup> _knownLeaders = new java.util.concurrent.ConcurrentHashMap<>();
	private volatile boolean _inventoryComplete;
	public boolean inventoryComplete() { return _inventoryComplete; }
	public List<StoredLogicalGroup> knownGroups() { return _knownLeaders.values().stream().filter(row -> row.group().active()).toList(); }
	private synchronized StoredLogicalGroup remember(StoredLogicalGroup row)
	{
		final var known = _knownLeaders.get(row.group().leaderProfileId());
		if (known != null && known.rowVersion() > row.rowVersion()) { return row; }
		_knownLeaders.put(row.group().leaderProfileId(), row);
		_knownMembers.entrySet().removeIf(entry -> entry.getValue().group().leaderProfileId() == row.group().leaderProfileId() && entry.getValue().rowVersion() <= row.rowVersion());
		if (row.group().active()) { for (var member : row.group().members()) { _knownMembers.put(member.profileId(), row); } }
		return row;
	}
	/** Scheduler-local durable recovery cache, never a grant of native/goal ownership. */
	public Optional<StoredLogicalGroup> knownGroupFor(long profileId) { return Optional.ofNullable(_knownMembers.get(profileId)).filter(row -> row.group().active()); }
	public record Candidate(PhantomLogicalParty.Member member, int level, List<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> sources,
		java.util.function.Predicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> reachable, java.util.function.BooleanSupplier current, java.util.function.BooleanSupplier durableCurrent)
	{
		public Candidate(PhantomLogicalParty.Member member, int level, List<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> sources,
			java.util.function.Predicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> reachable, java.util.function.BooleanSupplier current)
		{ this(member, level, sources, reachable, current, current); }
		public Candidate
		{
			Objects.requireNonNull(member); Objects.requireNonNull(reachable); Objects.requireNonNull(current); Objects.requireNonNull(durableCurrent); sources = List.copyOf(sources);
			if (level < 1 || level > 85 || sources.size() > 64) { throw new IllegalArgumentException("Invalid bounded logical candidate."); }
		}
	}
	private record Discovered(Candidate candidate, long expiresMillis) { }
	public PhantomLogicalPartyCoordinator(PhantomPartyPersistencePort store) { _store = Objects.requireNonNull(store); }

	/** A page of durable facts, not materialized actors. Predicates are captured read-only facts, never SQL per source. */
	public Optional<StoredLogicalGroup> discover(List<Candidate> page, java.util.function.BiPredicate<Long, Integer> seeking, long now)
	{
		if (page == null || page.size() > 16 || now <= 0) { throw new IllegalArgumentException("Invalid durable discovery page."); }
		_discovered.entrySet().removeIf(entry -> entry.getValue().expiresMillis() <= now);
		for (var candidate : page)
		{
			final long id = candidate.member().profileId();
			_discovered.remove(id);
			if (!seeking.test(id, candidate.level()) || !candidate.current().getAsBoolean() || _store.loadLogicalClaim(id).filter(PhantomPartyPersistencePort.StoredLogicalClaim::active).isPresent()) { continue; }
			if (_discovered.size() >= 256) { _discovered.remove(_discovered.keySet().iterator().next()); }
			_discovered.put(id, new Discovered(candidate, Math.addExact(now, 600_000)));
		}
		final var candidates = _discovered.values().stream().map(Discovered::candidate).toList();
		for (var leader : candidates.stream().sorted(java.util.Comparator.comparing((Candidate c) -> c.member().profileId() <= _leaderCursor)
			.thenComparingLong(c -> c.member().profileId())).limit(4).toList())
		{
			_leaderCursor = leader.member().profileId();
			if (!leader.current().getAsBoolean() || !leader.member().role().equals("damage")) { continue; }
			for (var source : leader.sources().stream().distinct().toList())
			{
				if (!leader.reachable().test(source)) { continue; }
				final var roster = new java.util.ArrayList<PhantomLogicalParty.Member>(); roster.add(leader.member());
				// Eligibility and full route admission precede the final roster cap.
				candidates.stream().filter(peer -> peer.member().profileId() != leader.member().profileId() && Math.abs(peer.level() - leader.level()) <= 8
					&& peer.current().getAsBoolean() && seeking.test(peer.member().profileId(), peer.level()) && peer.reachable().test(source))
					.sorted(java.util.Comparator.comparingLong(peer -> peer.member().profileId())).limit(8).map(Candidate::member).forEach(roster::add);
				if (roster.size() < 2) { continue; }
				boolean currentRoster = true;
				for (var member : roster)
				{
					final var candidate = _discovered.get(member.profileId());
					if (candidate == null || !candidate.candidate().durableCurrent().getAsBoolean())
					{ _discovered.remove(member.profileId()); currentRoster = false; }
				}
				if (!currentRoster) { continue; }
				final String id = org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256(roster + ":" + source + ":" + now);
				final long generation = _store.loadLogicalGroup(leader.member().profileId()).map(row -> Math.addExact(row.group().generation(), 1)).orElse(1L);
				final var group = new PhantomLogicalParty(id, generation, leader.member().profileId(), roster, source, source.key().substring(source.key().indexOf('@') + 1),
					PhantomLogicalParty.Phase.AGREED, 0, now, "");
				final var result = agree(group);
				for (var member : roster) { _discovered.remove(member.profileId()); }
				return result;
			}
		}
		return Optional.empty();
	}
	public Optional<StoredLogicalGroup> agree(PhantomLogicalParty group)
	{
		if (group.phase() != PhantomLogicalParty.Phase.AGREED) { throw new IllegalArgumentException("Formation must commit an AGREED aggregate."); }
		final var prior = _store.loadLogicalGroup(group.leaderProfileId());
		if (prior.isPresent())
		{
			if (prior.get().group().equals(group)) { return groupFor(group.leaderProfileId()); }
			if (prior.get().group().active() || group.generation() != Math.addExact(prior.get().group().generation(), 1)) { return Optional.empty(); }
		}
		final Map<Long, Long> versions = new LinkedHashMap<>();
		for (var member : group.members().stream().sorted(java.util.Comparator.comparingLong(PhantomLogicalParty.Member::profileId)).toList())
		{
			final var claim = _store.loadLogicalClaim(member.profileId());
			if (claim.filter(PhantomPartyPersistencePort.StoredLogicalClaim::active).isPresent()) { return Optional.empty(); }
			versions.put(member.profileId(), claim.map(PhantomPartyPersistencePort.StoredLogicalClaim::rowVersion).orElse(-1L));
		}
		return Optional.of(remember(_store.commitLogicalGroup(group, prior.map(StoredLogicalGroup::rowVersion).orElse(-1L), Map.copyOf(versions))));
	}
	public List<StoredLogicalGroup> recoverAfter(long leaderCursor, int limit)
	{
		if (leaderCursor < 0 || limit < 1 || limit > 256) { throw new IllegalArgumentException("Invalid logical recovery page."); }
		final var page = _store.loadLogicalGroupsAfter(leaderCursor, limit);
		if (page.isEmpty()) { _inventoryComplete = true; }
		return page.stream().map(this::remember).toList();
	}
	public Optional<StoredLogicalGroup> groupFor(long profileId)
	{
		final var claim = _store.loadLogicalClaim(profileId).orElse(null);
		if (claim == null || !claim.active()) { _knownMembers.remove(profileId); return Optional.empty(); }
		return _store.loadLogicalGroup(claim.leaderProfileId()).filter(row -> row.group().active() && row.group().contains(profileId)
			&& row.group().groupId().equals(claim.groupId()) && row.group().generation() == claim.generation()).map(this::remember);
	}
	public StoredLogicalGroup transition(StoredLogicalGroup expected, PhantomLogicalParty.Phase phase, long cursor, long nextDue, String reason)
	{
		if (cursor != expected.group().backgroundCursor()) { throw new IllegalArgumentException("Only settled Background receipts can advance group time."); }
		if (phase == PhantomLogicalParty.Phase.DISBANDED)
		{
			if (cursor != expected.group().backgroundCursor()) { throw new IllegalArgumentException("Disband cannot advance Background time."); }
			return disband(expected, nextDue, reason);
		}
		return remember(_store.updateLogicalGroup(expected.group().transition(phase, cursor, nextDue, reason), expected.rowVersion()));
	}
	public StoredLogicalGroup refreshBackground(StoredLogicalGroup expected, long now) { return remember(_store.refreshLogicalBackground(expected, now)); }
	private void requireIdle(StoredLogicalGroup expected)
	{
		if (_store.loadLogicalGroup(expected.group().leaderProfileId()).filter(expected::equals).isEmpty()) { throw new java.util.ConcurrentModificationException("Stale logical agreement."); }
		if (expected.group().phase() != PhantomLogicalParty.Phase.AGREED && expected.group().phase() != PhantomLogicalParty.Phase.BACKGROUND)
		{ throw new IllegalStateException("Logical membership waits for physical drain/STORE."); }
	}
	public StoredLogicalGroup disband(StoredLogicalGroup expected, long now, String reason)
	{
		if (!expected.group().active() && _store.loadLogicalGroup(expected.group().leaderProfileId()).filter(expected::equals).isPresent()) { return remember(expected); }
		requireIdle(expected);
		return remember(_store.reviseLogicalGroup(expected, expected.group().transition(PhantomLogicalParty.Phase.DISBANDED, expected.group().backgroundCursor(), now, reason)));
	}
	public StoredLogicalGroup replace(StoredLogicalGroup expected, List<PhantomLogicalParty.Member> members, long now)
	{
		requireIdle(expected); final var old = expected.group();
		if (old.members().equals(members)) { return remember(expected); }
		final var next = new PhantomLogicalParty(old.groupId(), Math.addExact(old.generation(), 1), old.leaderProfileId(), members,
			old.objective(), old.anchorId(), PhantomLogicalParty.Phase.BACKGROUND, 0, now, "ROSTER_CHANGED");
		return remember(_store.reviseLogicalGroup(expected, next));
	}
	public StoredLogicalGroup leave(StoredLogicalGroup expected, long profileId, long now)
	{
		requireIdle(expected); final var old = expected.group();
		if (!old.contains(profileId)) { return remember(expected); }
		// Logical leadership is never silently transferred into native or human leadership rights.
		if (profileId == old.leaderProfileId() || old.members().size() == 2) { return disband(expected, now, "MEMBER_LEFT"); }
		return replace(expected, old.members().stream().filter(member -> member.profileId() != profileId).toList(), now);
	}
	public Optional<StoredLogicalGroup> expire(StoredLogicalGroup expected, long now)
	{
		if (now < expected.group().expiresAtMillis() || !expected.group().active()
			|| expected.group().phase() != PhantomLogicalParty.Phase.AGREED && expected.group().phase() != PhantomLogicalParty.Phase.BACKGROUND) { return Optional.empty(); }
		return Optional.of(disband(expected, now, "EXPIRED"));
	}
}

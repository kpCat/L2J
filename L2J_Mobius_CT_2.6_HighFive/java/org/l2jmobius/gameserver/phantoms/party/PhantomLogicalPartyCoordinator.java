/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Objects;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort.StoredLogicalGroup;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

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
		java.util.function.Predicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> reachable, java.util.function.BooleanSupplier current, java.util.function.BooleanSupplier durableCurrent,
		java.util.function.Function<java.util.function.Predicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef>, List<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef>> sourcesMatching,
		java.util.function.Supplier<List<PhantomProfileComponent>> commitGuards)
	{
		public Candidate(PhantomLogicalParty.Member member, int level, List<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> sources,
			java.util.function.Predicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> reachable, java.util.function.BooleanSupplier current, java.util.function.BooleanSupplier durableCurrent,
			java.util.function.Function<java.util.function.Predicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef>, List<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef>> sourcesMatching)
		{ this(member, level, sources, reachable, current, durableCurrent, sourcesMatching, List::of); }
		public Candidate(PhantomLogicalParty.Member member, int level, List<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> sources,
			java.util.function.Predicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> reachable, java.util.function.BooleanSupplier current, java.util.function.BooleanSupplier durableCurrent)
		{ this(member, level, sources, reachable, current, durableCurrent, admitted -> sources.stream().filter(admitted).toList()); }
		public Candidate(PhantomLogicalParty.Member member, int level, List<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> sources,
			java.util.function.Predicate<org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef> reachable, java.util.function.BooleanSupplier current)
		{ this(member, level, sources, reachable, current, current); }
		public Candidate
		{
			Objects.requireNonNull(member); Objects.requireNonNull(reachable); Objects.requireNonNull(current); Objects.requireNonNull(durableCurrent);
			Objects.requireNonNull(sourcesMatching); Objects.requireNonNull(commitGuards); sources = List.copyOf(sources);
			if (level < 1 || level > 85 || sources.size() > 64) { throw new IllegalArgumentException("Invalid bounded logical candidate."); }
		}
	}
	private record Discovered(Candidate candidate, long expiresMillis) { }
	public PhantomLogicalPartyCoordinator(PhantomPartyPersistencePort store) { _store = Objects.requireNonNull(store); }

	public enum DiscoveryStatus { READY, WAIT_ROSTER, WAIT_COMMON_ROUTE, WAIT_DISCOVERY_BUDGET }
	public record Discovery(Optional<PhantomLogicalParty> proposal, DiscoveryStatus status, long nextDueMillis, List<PhantomProfileComponent> guards)
	{
		public Discovery { guards = List.copyOf(guards); }
	}

	public Optional<StoredLogicalGroup> discover(List<Candidate> page, java.util.function.BiPredicate<Long, Integer> seeking, long now)
	{
		return commitDiscovery(previewDiscovery(page, seeking, now));
	}

	public Optional<StoredLogicalGroup> commitDiscovery(Discovery discovery)
	{
		return discovery.proposal().flatMap(group ->
		{
			try
			{
				for (var member : group.members())
				{
					final var cached = _discovered.get(member.profileId());
					final var candidate = cached == null ? null : cached.candidate();
					if (candidate == null || !candidate.member().equals(member) || !candidate.current().getAsBoolean()
						|| !candidate.reachable().test(group.objective())) { return Optional.empty(); }
					if (discovery.guards().stream().noneMatch(guard -> guard.profileId() == member.profileId())
						&& !candidate.durableCurrent().getAsBoolean()) { return Optional.empty(); }
				}
				return agree(group, discovery.guards());
			}
			catch (java.util.ConcurrentModificationException changed) { return Optional.empty(); }
			finally
			{
				for (var member : group.members()) { _discovered.remove(member.profileId()); }
			}
		});
	}

	/** A page of durable facts, not materialized actors. Predicates are captured read-only facts, never SQL per source. */
	public Discovery previewDiscovery(List<Candidate> page, java.util.function.BiPredicate<Long, Integer> seeking, long now)
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
		boolean budget = candidates.size() > 4 || page.size() == 16;
		boolean hasPeers = false;
		for (var leader : candidates.stream().sorted(java.util.Comparator.comparing((Candidate c) -> c.member().profileId() <= _leaderCursor)
			.thenComparingLong(c -> c.member().profileId())).limit(4).toList())
		{
			_leaderCursor = leader.member().profileId();
			if (!leader.current().getAsBoolean() || leader.member().role().equals("pending.native_role")) { continue; }
			final var peers = candidates.stream().filter(peer -> peer.member().profileId() != leader.member().profileId()
				&& !peer.member().role().equals("pending.native_role") && Math.abs(peer.level() - leader.level()) <= 8
				&& peer.current().getAsBoolean() && seeking.test(peer.member().profileId(), peer.level())).sorted(java.util.Comparator.comparingLong(peer -> peer.member().profileId())).toList();
			if (peers.isEmpty()) { continue; }
			hasPeers = true;
			final var sources = leader.sourcesMatching().apply(source -> peers.stream().anyMatch(peer -> peer.reachable().test(source)));
			if (sources.size() > 64) { throw new IllegalArgumentException("Unbounded Party source search."); }
			budget |= sources.size() == 64;
			for (var source : sources.stream().distinct().toList())
			{
				if (!leader.reachable().test(source)) { continue; }
				final var roster = new java.util.ArrayList<PhantomLogicalParty.Member>(); roster.add(leader.member());
				final var levels = new java.util.ArrayList<Integer>(); levels.add(leader.level());
				// Eligibility and full route admission precede the final roster cap.
				for (var peer : peers)
				{
					if (!peer.reachable().test(source)) { continue; }
					final var proposed = new java.util.ArrayList<>(levels); proposed.add(peer.level());
					if (PhantomPartyFarmEligibility.forRoster(proposed).isEmpty()) { continue; }
					roster.add(peer.member()); levels.add(peer.level());
					if (roster.size() == 9) { break; }
				}
				if (roster.size() < 2) { continue; }
				boolean currentRoster = true;
				final var guards = new java.util.ArrayList<PhantomProfileComponent>();
				for (var member : roster)
				{
					final var candidate = _discovered.get(member.profileId());
					if (candidate == null || !candidate.candidate().durableCurrent().getAsBoolean() || !candidate.candidate().reachable().test(source))
					{ _discovered.remove(member.profileId()); currentRoster = false; break; }
					try { guards.addAll(candidate.candidate().commitGuards().get()); }
					catch (java.util.ConcurrentModificationException changed) { _discovered.remove(member.profileId()); currentRoster = false; break; }
				}
				if (!currentRoster || PhantomPartyFarmEligibility.forRoster(levels).isEmpty()) { continue; }
				final String id = org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256(roster + ":" + source + ":" + now);
				final long generation = _store.loadLogicalGroup(leader.member().profileId()).map(row -> Math.addExact(row.group().generation(), 1)).orElse(1L);
				final var group = new PhantomLogicalParty(id, generation, leader.member().profileId(), roster, source, source.key().substring(source.key().indexOf('@') + 1),
					PhantomLogicalParty.Phase.AGREED, 0, now, "");
				return new Discovery(Optional.of(group), DiscoveryStatus.READY, now, guards);
			}
		}
		final var status = budget ? DiscoveryStatus.WAIT_DISCOVERY_BUDGET : hasPeers ? DiscoveryStatus.WAIT_COMMON_ROUTE : DiscoveryStatus.WAIT_ROSTER;
		return new Discovery(Optional.empty(), status, Math.addExact(now, 1000), List.of());
	}
	public Optional<StoredLogicalGroup> agree(PhantomLogicalParty group)
	{ return agree(group, List.of()); }

	private Optional<StoredLogicalGroup> agree(PhantomLogicalParty group, List<PhantomProfileComponent> guards)
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
		return Optional.of(remember(_store.commitLogicalGroup(group, prior.map(StoredLogicalGroup::rowVersion).orElse(-1L), Map.copyOf(versions), guards)));
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
	public interface ActivityRevision
	{
		/** Called after projection absence; reject until every old Group grant has recovered as Solo. */
		List<PhantomLogicalParty.ActivityRoot> plan(PhantomLogicalParty group, long now);
		void publish(PhantomLogicalParty.ActivityRoot plan);
	}
	/** One bounded step on the existing Party control port. All incomplete phases remain fenced in SQL. */
	public StoredLogicalGroup recoverActivity(StoredLogicalGroup expected, long now, ActivityRevision revision)
	{
		final var group = expected.group();
		if (group.phase() != PhantomLogicalParty.Phase.BACKGROUND || !group.activityReplanRequired() || now < group.nextDueMillis()) { return expected; }
		if (!group.activityPending())
		{
			try
			{
				final var plans = revision.plan(group, now);
				if (now >= group.expiresAtMillis()) { return expire(expected, now).orElse(expected); }
				if (plans.isEmpty()) { return transition(expected, group.phase(), group.backgroundCursor(), now + 300_000, "REPLAN_NO_COMMON_SOURCE"); }
				return remember(_store.beginActivityRevision(expected, plans, now));
			}
			catch (java.util.ConcurrentModificationException releasePending) { return transition(expected, group.phase(), group.backgroundCursor(), now + 5000, "REPLAN_WAIT_RELEASE"); }
		}
		final var plans = _store.activityRoots(expected);
		final boolean expired = plans.stream().anyMatch(plan -> now >= plan.deadline());
		if (!expired) for (var plan : plans) { revision.publish(plan); }
		return _store.finishActivityRevision(expected, now, expired).map(this::remember).orElse(expected);
	}
	private void requireIdle(StoredLogicalGroup expected)
	{
		if (expected.group().activityPending()) { throw new IllegalStateException("Activity publication must settle before membership changes."); }
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
	/** Cold physical expiry cooperates with the existing execution owner; never recreates an objective. */
	public StoredLogicalGroup retireExpired(StoredLogicalGroup expected, long now, java.util.function.BooleanSupplier absent,
		java.util.function.LongPredicate soloRecovered, java.util.function.LongConsumer requestSolo)
	{
		if (_store.loadLogicalGroup(expected.group().leaderProfileId()).filter(expected::equals).isEmpty()) { throw new java.util.ConcurrentModificationException("Stale cold expiry generation."); }
		if (!expected.group().active() || now < expected.group().expiresAtMillis() || !absent.getAsBoolean()) { return expected; }
		var current = expected;
		if (current.group().phase() != PhantomLogicalParty.Phase.DRAINING)
		{ current = transition(current, PhantomLogicalParty.Phase.DRAINING, current.group().backgroundCursor(), now, "EXPIRED_ASSEMBLY"); }
		current.group().members().forEach(member -> requestSolo.accept(member.profileId()));
		if (current.group().members().stream().anyMatch(member -> !soloRecovered.test(member.profileId())) || !absent.getAsBoolean())
		{ return transition(current, PhantomLogicalParty.Phase.DRAINING, current.group().backgroundCursor(), now + 5000, "EXPIRED_ASSEMBLY"); }
		current = transition(current, PhantomLogicalParty.Phase.BACKGROUND, current.group().backgroundCursor(), now, "EXPIRED_ASSEMBLY");
		return expire(current, now).orElseThrow();
	}
}

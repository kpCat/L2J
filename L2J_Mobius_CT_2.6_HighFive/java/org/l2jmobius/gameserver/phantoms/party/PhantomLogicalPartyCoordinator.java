/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Objects;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
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
	/** Structural ranking only. Walking includes ingress and both sides of any NORMAL GK jump. */
	public record RouteCost(long walkingUnits, long travelMillis, int gatekeepers, long feeAdena)
	{
		private static final RouteCost EMPTY = new RouteCost(0, 0, 0, 0);
		private static final int LOCAL_WALK = PhantomNavigationPolicy.productionDefaults().maximumLocalStraightDistance();
		private static final Comparator<RouteCost> ORDER = Comparator.comparing(RouteCost::requiresExtendedTravel)
			.thenComparingLong(RouteCost::walkingUnits).thenComparingLong(RouteCost::travelMillis)
			.thenComparingInt(RouteCost::gatekeepers).thenComparingLong(RouteCost::feeAdena);

		private boolean requiresExtendedTravel() { return gatekeepers > 0 || walkingUnits > LOCAL_WALK; }

		private RouteCost include(RouteCost member)
		{
			return new RouteCost(Math.max(walkingUnits, member.walkingUnits), Math.max(travelMillis, member.travelMillis),
				gatekeepers + member.gatekeepers, feeAdena + member.feeAdena);
		}
	}
	private record FormationSource(PhantomDomainRef source, List<PhantomLogicalParty.Member> roster, List<Integer> levels, RouteCost cost) { }

	public record Candidate(PhantomLogicalParty.Member member, int level, List<PhantomDomainRef> sources,
		java.util.function.Predicate<PhantomDomainRef> reachable, java.util.function.BooleanSupplier current, java.util.function.BooleanSupplier durableCurrent,
		java.util.function.Function<java.util.function.Predicate<PhantomDomainRef>, List<PhantomDomainRef>> sourcesMatching,
		java.util.function.Supplier<List<PhantomProfileComponent>> commitGuards, java.util.function.Function<PhantomDomainRef, Optional<RouteCost>> routeCost)
	{
		public Candidate(PhantomLogicalParty.Member member, int level, List<PhantomDomainRef> sources,
			java.util.function.Predicate<PhantomDomainRef> reachable, java.util.function.BooleanSupplier current, java.util.function.BooleanSupplier durableCurrent,
			java.util.function.Function<java.util.function.Predicate<PhantomDomainRef>, List<PhantomDomainRef>> sourcesMatching,
			java.util.function.Supplier<List<PhantomProfileComponent>> commitGuards)
		{ this(member, level, sources, reachable, current, durableCurrent, sourcesMatching, commitGuards, source -> reachable.test(source) ? Optional.of(RouteCost.EMPTY) : Optional.empty()); }
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
			Objects.requireNonNull(sourcesMatching); Objects.requireNonNull(commitGuards); Objects.requireNonNull(routeCost); sources = List.copyOf(sources);
			if (level < 1 || level > 85 || sources.size() > 64) { throw new IllegalArgumentException("Invalid bounded logical candidate."); }
		}
	}
	private record Discovered(Candidate candidate, long expiresMillis) { }
	public PhantomLogicalPartyCoordinator(PhantomPartyPersistencePort store) { _store = Objects.requireNonNull(store); }

	private static final int MAXIMUM_PHYSICAL_PROOFS = 8;
	private static final int MAXIMUM_OPENING_PROOFS = 16;
	private final java.util.Map<Long, Integer> _openingOffsets = new java.util.HashMap<>();
	private java.util.function.Predicate<Discovery> _openingProof;

	public void bindOpeningProof(java.util.function.Predicate<Discovery> proof)
	{
		if (_openingProof != null) { throw new IllegalStateException("Party opening proof is already bound."); }
		_openingProof = java.util.Objects.requireNonNull(proof);
	}
	public enum DiscoveryStatus { READY, WAIT_ROSTER, WAIT_COMMON_ROUTE, WAIT_DISCOVERY_BUDGET, WAIT_SAFE_OPENING, WAIT_OPENING_PROOF_BUDGET, WAIT_PHYSICAL_PROOF, WAIT_PHYSICAL_PROOF_BUDGET }
	public record Discovery(Optional<PhantomLogicalParty> proposal, DiscoveryStatus status, long nextDueMillis, List<PhantomProfileComponent> guards, List<Integer> levels)
	{
		public Discovery { guards = List.copyOf(guards); levels = List.copyOf(levels); }
	}

	public Optional<StoredLogicalGroup> discover(List<Candidate> page, java.util.function.BiPredicate<Long, Integer> seeking, long now)
	{
		return commitDiscovery(previewDiscovery(page, seeking, now), seeking);
	}

	public Optional<StoredLogicalGroup> commitDiscovery(Discovery discovery, java.util.function.BiPredicate<Long, Integer> seeking)
	{
		return discovery.proposal().flatMap(group ->
		{
			try
			{
				for (int index = 0; index < group.members().size(); index++)
				{
					final var member = group.members().get(index);
					final var cached = _discovered.get(member.profileId());
					final var candidate = cached == null ? null : cached.candidate();
					// SQL component guards cannot witness the learned skills used to resolve the role.
					if (candidate == null || !candidate.member().equals(member) || !candidate.current().getAsBoolean()
						|| candidate.level() != discovery.levels().get(index) || !seeking.test(member.profileId(), candidate.level())
						|| !candidate.reachable().test(group.objective()) || !candidate.durableCurrent().getAsBoolean()) { return Optional.empty(); }
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
		return previewDiscovery(page, seeking, now, null);
	}

	/** Optional commissioning proof of the exact proposal. A refusal advances this same bounded enumeration. */
	public Discovery previewDiscovery(List<Candidate> page, java.util.function.BiPredicate<Long, Integer> seeking, long now,
		java.util.function.Predicate<Discovery> physicalProof)
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
		_openingOffsets.keySet().retainAll(_discovered.keySet());
		boolean budget = candidates.size() > 4 || page.size() == 16;
		boolean hasPeers = false;
		int attemptedProofs = 0;
		int observedOpenings = 0;
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
			final var ranked = rankSources(leader, peers, sources);
			if (ranked.isEmpty()) { continue; }
			final int start = _openingOffsets.getOrDefault(leader.member().profileId(), 0) % ranked.size();
			for (int index = 0; index < ranked.size(); index++)
			{
				final int position = (start + index) % ranked.size();
				final var formation = ranked.get(position);
				final var source = formation.source();
				final var roster = formation.roster();
				final var levels = formation.levels();
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
				final var proposal = new Discovery(Optional.of(group), DiscoveryStatus.READY, now, guards, levels);
				if (_openingProof != null)
				{
					if (observedOpenings == MAXIMUM_OPENING_PROOFS)
					{
						_openingOffsets.put(leader.member().profileId(), position);
						return new Discovery(Optional.empty(), DiscoveryStatus.WAIT_OPENING_PROOF_BUDGET, Math.addExact(now, 1000), List.of(), List.of());
					}
					observedOpenings++;
					if (!_openingProof.test(proposal)) { continue; }
				}
				if (physicalProof == null) { _openingOffsets.remove(leader.member().profileId()); return proposal; }
				if (attemptedProofs == MAXIMUM_PHYSICAL_PROOFS)
				{ return new Discovery(Optional.empty(), DiscoveryStatus.WAIT_PHYSICAL_PROOF_BUDGET, Math.addExact(now, 1000), List.of(), List.of()); }
				attemptedProofs++;
				if (physicalProof.test(proposal)) { _openingOffsets.remove(leader.member().profileId()); return proposal; }
			}
		}
		final var status = attemptedProofs > 0 ? DiscoveryStatus.WAIT_PHYSICAL_PROOF : observedOpenings > 0 ? DiscoveryStatus.WAIT_SAFE_OPENING
			: budget ? DiscoveryStatus.WAIT_DISCOVERY_BUDGET : hasPeers ? DiscoveryStatus.WAIT_COMMON_ROUTE : DiscoveryStatus.WAIT_ROSTER;
		return new Discovery(Optional.empty(), status, Math.addExact(now, 1000), List.of(), List.of());
	}

	private static List<FormationSource> rankSources(Candidate leader, List<Candidate> peers, List<PhantomDomainRef> sources)
	{
		final var ranked = new ArrayList<FormationSource>();
		for (var source : sources.stream().distinct().toList())
		{
			var cost = leader.routeCost().apply(source).orElse(null);
			if (cost == null) { continue; }
			final var roster = new ArrayList<PhantomLogicalParty.Member>(); roster.add(leader.member());
			final var levels = new ArrayList<Integer>(); levels.add(leader.level());
			// Eligibility and full route admission precede the final roster cap.
			for (var peer : peers)
			{
				final var proposed = new ArrayList<>(levels); proposed.add(peer.level());
				if (PhantomPartyFarmEligibility.forRoster(proposed).isEmpty()) { continue; }
				final var peerCost = peer.routeCost().apply(source).orElse(null);
				if (peerCost == null) { continue; }
				cost = cost.include(peerCost);
				roster.add(peer.member()); levels.add(peer.level());
				if (roster.size() == 9) { break; }
			}
			if (roster.size() >= 2) { ranked.add(new FormationSource(source, roster, levels, cost)); }
		}
		ranked.sort(Comparator.comparing(FormationSource::cost, RouteCost.ORDER));
		return ranked;
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
		final var committed = remember(_store.updateLogicalGroup(expected.group().transition(phase, cursor, nextDue, reason), expected.rowVersion()));
		if (phase == PhantomLogicalParty.Phase.DRAINING && expected.group().phase() != phase)
		{
			final var group = committed.group();
			java.util.logging.Logger.getLogger(PhantomLogicalPartyCoordinator.class.getName()).info("PARTY_LOGICAL_DRAIN group=" + group.groupId()
				+ " generation=" + group.generation() + " row=" + committed.rowVersion() + " from=" + expected.group().phase()
				+ " reason=" + group.waitReason() + " expiresAt=" + group.expiresAtMillis() + " nextDue=" + group.nextDueMillis());
		}
		return committed;
	}
	public StoredLogicalGroup refreshBackground(StoredLogicalGroup expected, long now) { return remember(_store.refreshLogicalBackground(expected, now)); }
	public record ActivityPlan(List<PhantomLogicalParty.ActivityRoot> roots, java.util.Map<Long, Long> backgroundVersions, String waitReason, long retryMillis)
	{
		public ActivityPlan
		{
			roots = List.copyOf(roots);
			backgroundVersions = java.util.Map.copyOf(backgroundVersions);
			if (roots.isEmpty() ? !backgroundVersions.isEmpty() || !waitReason.startsWith("REPLAN_") || retryMillis <= 0
				: backgroundVersions.size() != roots.size() || !waitReason.isEmpty() || retryMillis != 0)
			{ throw new IllegalArgumentException("Invalid Party activity result."); }
		}
		public static ActivityPlan ready(List<PhantomLogicalParty.ActivityRoot> roots, java.util.Map<Long, Long> versions) { return new ActivityPlan(roots, versions, "", 0); }
		public static ActivityPlan waitFor(String reason, long retryMillis) { return new ActivityPlan(List.of(), java.util.Map.of(), reason, retryMillis); }
	}
	public interface ActivityRevision
	{
		/** Called after projection absence; reject until every old Group grant has recovered as Solo. */
		ActivityPlan plan(PhantomLogicalParty group, long now);
		void publish(PhantomLogicalParty.ActivityRoot plan);
	}
	/** One bounded step on the existing Party control port. All incomplete phases remain fenced in SQL. */
	public StoredLogicalGroup recoverActivity(StoredLogicalGroup expected, long now, ActivityRevision revision)
	{
		final var group = expected.group();
		if (group.phase() != PhantomLogicalParty.Phase.BACKGROUND || !group.activityReplanRequired() || now < group.nextDueMillis()) { return expected; }
		if (!group.activityPending())
		{
			if (now >= group.expiresAtMillis()) { return expire(expected, now).orElse(expected); }
			if (group.rejectedActivitySources().size() >= PhantomLogicalParty.MAXIMUM_ACTIVITY_REPLACEMENTS)
			{ return transition(expected, group.phase(), group.backgroundCursor(), group.expiresAtMillis(), "REPLAN_WAIT_SOURCE_BUDGET"); }
			try
			{
				final var plan = revision.plan(group, now);
				if (plan.roots().isEmpty()) { return transition(expected, group.phase(), group.backgroundCursor(), Math.min(group.expiresAtMillis(), now + plan.retryMillis()), plan.waitReason()); }
				return remember(_store.beginActivityRevision(expected, plan.roots(), now, plan.backgroundVersions()));
			}
			catch (java.util.ConcurrentModificationException releasePending) { return transition(expected, group.phase(), group.backgroundCursor(), Math.min(group.expiresAtMillis(), now + 5000), "REPLAN_WAIT_RELEASE"); }
		}
		final var plans = _store.activityRoots(expected);
		final boolean expired = now >= group.expiresAtMillis() || plans.stream().anyMatch(plan -> now >= plan.deadline());
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

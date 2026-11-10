/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.LongSupplier;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.CommandOutcome;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationContext;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationDecision;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.VacancyStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;

/** Ordinary farm orchestration under the existing shared scheduler. */
public final class PhantomAutonomousFarmParty implements PhantomSchedulerControlPort
{
	public static final int MAXIMUM_CANDIDATES = 16;
	public static final int MAXIMUM_TARGET_DISTANCE = org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy.productionDefaults(64).maximumAcquisitionDistance();
	public static final String OBJECTIVE_NAMESPACE = "farm.party";
	private static final Set<String> DAMAGE = Set.of("combat.melee_damage", "combat.ranged_physical_damage", "combat.ranged_magic_damage");
	private static final Set<String> SUPPORT = Set.of("combat.heal", "combat.recharge", "combat.buff", "combat.song", "combat.dance");
	private final PhantomPartyCoordinator _coordinator;
	private final PhantomPartyBackend _backend;
	private final PhantomGoalStore _goals;
	private final PhantomPartyRoleMatcher _roles;
	private final LongSupplier _clock;
	private final java.util.function.BiPredicate<Long, PhantomGoal> _preparationRequired;
	private final java.util.function.BiFunction<Long, PhantomGoal, PhantomVisibleFarmTravel.ArrivalResult> _arrival;
	private final Map<Long, PhantomGoal> _travelContinuations = new LinkedHashMap<>();
	private long _nextPulse;
	private long _nextContinuationPulse;
	private long _cursor;
	private org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings _seeking;
	private RendezvousPreparation _rendezvousPreparation;
	private LongSupplier _epochMillis = System::currentTimeMillis;
	private record Discovery(MemberSnapshot member, int level, long observedNanos, boolean available) { }
	private final Map<Long, Discovery> _discovered = new LinkedHashMap<>();
	private final Map<Long, PhantomPartyRendezvousState> _offers = new LinkedHashMap<>();
	private final Map<Long, Integer> _assemblyCursors = new LinkedHashMap<>();
	private record FarmWait(long sinceNanos, String reason, String groupId, long generation)
	{
		boolean matches(PhantomLogicalParty group) { return group != null && groupId.equals(group.groupId()) && generation == group.generation(); }
		String replanReason() { return "REPLAN_" + reason.substring(5); }
	}
	private final Map<Long, FarmWait> _farmWaits = new LinkedHashMap<>();
	private long _leaderCursor;
	private PhantomLogicalPartyCoordinator _logical;
	private java.util.function.BiFunction<Long, Integer, LogicalPage> _logicalDiscovery;
	private java.util.function.BiConsumer<PhantomLogicalParty, Long> _logicalWake;
	private java.util.function.Predicate<PhantomLogicalParty> _logicalRetain;
	private java.util.function.BiPredicate<Long, PhantomLogicalParty> _executionOwnership = (_, _) -> true;
	private boolean _executionOwnershipInstalled;
	private java.util.function.LongConsumer _terminalHandoff = _ -> { };
	public void installTerminalHandoff(java.util.function.LongConsumer wake) { _terminalHandoff = java.util.Objects.requireNonNull(wake); }
	private java.util.function.LongPredicate _soloRecovered = _ -> !_executionOwnershipInstalled;
	public void installSoloRecovery(java.util.function.LongPredicate recovered) { _soloRecovered = java.util.Objects.requireNonNull(recovered); }
	private PhantomLogicalPartyCoordinator.ActivityRevision _activityRevision;
	public void installActivityRevision(PhantomLogicalPartyCoordinator.ActivityRevision revision) { _activityRevision = java.util.Objects.requireNonNull(revision); }
	public void installExecutionOwnership(java.util.function.BiPredicate<Long, PhantomLogicalParty> ownership)
	{
		if (_executionOwnershipInstalled) { throw new IllegalStateException("Party execution ownership already installed."); }
		_executionOwnership = java.util.Objects.requireNonNull(ownership); _executionOwnershipInstalled = true;
	}
	private long _logicalCursor, _groupCursor, _nextDiagnosticMillis, _logicalNextDiscoveryMillis;
	private String _lastFailure = "";
	public record LogicalPage(List<PhantomLogicalPartyCoordinator.Candidate> candidates, long examinedProfileId)
	{
		public LogicalPage { candidates = List.copyOf(candidates); if (candidates.size() > 16 || examinedProfileId < 0) { throw new IllegalArgumentException("Invalid durable party page."); } }
	}
	public void installLogicalParty(PhantomLogicalPartyCoordinator logical, java.util.function.BiFunction<Long, Integer, LogicalPage> discovery,
		java.util.function.BiConsumer<PhantomLogicalParty, Long> wake, java.util.function.Predicate<PhantomLogicalParty> retain)
	{
		if (_logical != null) { throw new IllegalStateException("Logical party owner already installed."); }
		_logical = java.util.Objects.requireNonNull(logical); _logicalDiscovery = java.util.Objects.requireNonNull(discovery); _logicalWake = java.util.Objects.requireNonNull(wake);
		_logicalRetain = java.util.Objects.requireNonNull(retain);
		_coordinator.installLogicalFarmGroups(id -> logical.knownGroupFor(id).map(PhantomPartyPersistencePort.StoredLogicalGroup::group).orElse(null));
	}

	public PhantomLogicalPartyCoordinator.Discovery previewLogicalParty(LogicalPage page, long now,
		java.util.function.Predicate<PhantomLogicalPartyCoordinator.Discovery> physicalProof)
	{
		return _logical.previewDiscovery(page.candidates(), _seeking::seeks, now, physicalProof);
	}

	public java.util.Optional<PhantomPartyPersistencePort.StoredLogicalGroup> commitLogicalParty(PhantomLogicalPartyCoordinator.Discovery discovery)
	{
		return _logical.commitDiscovery(discovery, _seeking::seeks);
	}

	/** Startup recovery uses the ordinary cold expiry and execution release, without discovery or native work. */
	public void recoverColdLogicalParty(PhantomPartyPersistencePort.StoredLogicalGroup row, long now)
	{
		final var group = row.group();
		if (!group.active() || now < group.nextDueMillis()) { return; }
		final var known = group.members().stream().map(member -> _backend.currentMember(member.profileId())).flatMap(java.util.Optional::stream).toList();
		if (known.size() != group.members().size()) { return; }
		if (group.phase() == PhantomLogicalParty.Phase.BACKGROUND && group.activityReplanRequired() && _activityRevision != null)
		{
			withAbsentMembers(known, 0, () -> { _logical.recoverActivity(row, now, _activityRevision); return true; });
		}
		else if (now >= group.expiresAtMillis() && !group.activityPending())
		{
			retireColdParty(row, now, known);
		}
	}

	private void retireColdParty(PhantomPartyPersistencePort.StoredLogicalGroup row, long now, List<MemberRef> known)
	{
		final var group = row.group();
		cancelLogicalOffers(group);
		if (known.size() != group.members().size() || !withAbsentMembers(known, 0, () ->
		{
			_logical.retireExpired(row, now, () -> true, _soloRecovered, _terminalHandoff); return true;
		})) { _logical.transition(row, group.phase(), group.backgroundCursor(), now + 5000, "WAIT_STORE"); }
	}

	private void diagnostic(String phase, RuntimeException failure)
	{
		_lastFailure = phase + "." + failure.getClass().getSimpleName();
		final long now = _epochMillis.getAsLong();
		if (now >= _nextDiagnosticMillis)
		{
			_nextDiagnosticMillis = now + 30_000;
			java.util.logging.Logger.getLogger(PhantomAutonomousFarmParty.class.getName()).warning("Party coordination failure phase=" + phase + ";profileCursor=" + _logicalCursor + ";groupCursor=" + _groupCursor + ";code=" + _lastFailure + ";retryAt=" + (now + 5000));
		}
	}
	public String lastCoordinationFailure() { return _lastFailure; }

	private void logicalPulse(List<MemberSnapshot> materializedPage)
	{
		try
		{
			final long now = _epochMillis.getAsLong();
			final var recovered = _logical.recoverAfter(_groupCursor, 8);
			_groupCursor = recovered.isEmpty() ? 0 : recovered.getLast().group().leaderProfileId();
			// The current native page must not wait for an entire durable inventory turn.
			// Refresh one claim per distinct known group; recovery keeps its independent fair cursor.
			final Map<Long, Long> presentGroups = new LinkedHashMap<>();
			for (var member : materializedPage.stream().limit(MAXIMUM_CANDIDATES).toList())
			{
				_logical.knownGroupFor(member.ref().profileId()).ifPresent(row ->
				{
					presentGroups.putIfAbsent(row.group().leaderProfileId(), member.ref().profileId());
				});
			}
			final Map<Long, PhantomPartyPersistencePort.StoredLogicalGroup> groups = new LinkedHashMap<>();
			for (long member : presentGroups.values())
			{ _logical.groupFor(member).ifPresent(row -> groups.put(row.group().leaderProfileId(), row)); }
			for (var row : recovered)
			{ if (!presentGroups.containsKey(row.group().leaderProfileId())) { groups.putIfAbsent(row.group().leaderProfileId(), row); } }
			for (var row : groups.values()) { if (row.group().active() && row.group().nextDueMillis() <= now) { projectLogical(row, now); } }
			if (now < _logicalNextDiscoveryMillis) { return; }
			_lastFailure = "";
			final var page = _logicalDiscovery.apply(_logicalCursor, 16);
			_logicalCursor = page.examinedProfileId();
			final var discovery = previewLogicalParty(page, now, null);
			_logicalNextDiscoveryMillis = discovery.nextDueMillis();
			if (discovery.proposal().isPresent())
			{
				if (commitLogicalParty(discovery).isEmpty())
				{
					_logicalNextDiscoveryMillis = now + 1000;
					_lastFailure = "WAIT_DISCOVERY_CHANGED nextDue=" + _logicalNextDiscoveryMillis;
				}
			}
			else { _lastFailure = discovery.status() + " nextDue=" + discovery.nextDueMillis(); }
		}
		catch (RuntimeException failure) { diagnostic("logical_group", failure); }
	}
	private void projectLogical(PhantomPartyPersistencePort.StoredLogicalGroup row, long now)
	{
		final var group = row.group();
		final var known = group.members().stream().map(member -> _backend.currentMember(member.profileId())).flatMap(java.util.Optional::stream).toList();
		// A completed execution transfer may leave a lawful Solo Player in World. It no longer
		// represents this group's physical session; a leftover stock Party still blocks release.
		if (known.stream().filter(actor -> !_executionOwnership.test(actor.profileId(), group)).map(_backend::observe).flatMap(java.util.Optional::stream)
			.anyMatch(party -> party.members().size() >= 2 && party.members().stream().allMatch(member -> member.kind() == MemberKind.PHANTOM && group.contains(member.profileId()))))
		{ _logical.transition(row, group.phase(), group.backgroundCursor(), now + 5000, "WAIT_NATIVE_OWNER"); return; }
		final var identities = known.stream().filter(actor -> _executionOwnership.test(actor.profileId(), group)).toList();
		final var materialized = identities.stream().filter(actor -> _backend.materializationEpoch(actor) > 0).toList();
		if (materialized.isEmpty())
		{
			if (identities.stream().anyMatch(actor -> !_backend.withUnmaterializedNativeAbsence(actor, () -> true)))
			{ _logical.transition(row, group.phase(), group.backgroundCursor(), now + 5000, "WAIT_STORE"); return; }
			if (now >= group.expiresAtMillis() && !group.activityPending())
			{
				retireColdParty(row, now, known);
				return;
			}
			if (group.phase() == PhantomLogicalParty.Phase.BACKGROUND && group.activityReplanRequired() && _activityRevision != null)
			{ _logical.recoverActivity(row, now, _activityRevision); return; }
			if (_logical.expire(row, now).isPresent()) { return; }
			if (group.phase() == PhantomLogicalParty.Phase.NATIVE_ACTIVE)
			{ _logical.transition(row, PhantomLogicalParty.Phase.DRAINING, group.backgroundCursor(), now + 5000, "WAIT_STORE"); }
			else if (group.phase() != PhantomLogicalParty.Phase.BACKGROUND)
			{ _logical.transition(row, PhantomLogicalParty.Phase.BACKGROUND, group.backgroundCursor(), group.phase() == PhantomLogicalParty.Phase.DRAINING && !"EXPIRED_ASSEMBLY".equals(group.waitReason()) ? now + 300_000 : now, group.activityReplanRequired() ? group.waitReason() : "WAIT_DEMAND"); }
			else { _logical.refreshBackground(row, now); }
			return;
		}
		final var nativeParty = materialized.stream().map(_backend::observe).flatMap(java.util.Optional::stream)
			.filter(party -> party.members().size() >= 2 && party.members().stream().allMatch(member -> member.kind() == MemberKind.PHANTOM && group.contains(member.profileId()))).findFirst().orElse(null);
		if (now >= group.expiresAtMillis() && group.phase() != PhantomLogicalParty.Phase.NATIVE_ACTIVE && group.phase() != PhantomLogicalParty.Phase.DRAINING)
		{ _logical.transition(row, PhantomLogicalParty.Phase.DRAINING, group.backgroundCursor(), now, "EXPIRED_ASSEMBLY"); return; }
		if (group.phase() == PhantomLogicalParty.Phase.DRAINING)
		{
			cancelLogicalOffers(group);
			group.members().forEach(member -> _terminalHandoff.accept(member.profileId()));
			if (nativeParty != null) { _coordinator.drainAutonomousFarm(nativeParty.leader().profileId()); }
			return;
		}
		if (group.activityReplanRequired()) { return; }
		final var journey = group.phase() == PhantomLogicalParty.Phase.NATIVE_ACTIVE
			? _coordinator.farmJourneyProof(group) : PhantomPartyCoordinator.FarmJourneyProof.UNTRACKED;
		if (journey == PhantomPartyCoordinator.FarmJourneyProof.REVOKED || journey == PhantomPartyCoordinator.FarmJourneyProof.EXPIRED)
		{
			final String reason = journey == PhantomPartyCoordinator.FarmJourneyProof.EXPIRED ? "REPLAN_PARTY_JOURNEY_DEADLINE" : "WAIT_PARTY_JOURNEY_OWNER_LOST";
			_logical.transition(row, PhantomLogicalParty.Phase.DRAINING, group.backgroundCursor(), now, reason);
			return;
		}
		if (nativeParty != null)
		{
			if (group.phase() == PhantomLogicalParty.Phase.NATIVE_ACTIVE)
			{
				if (!_logicalRetain.test(group))
				{
					final var wait = _farmWaits.remove(nativeParty.leader().profileId());
					_logical.transition(row, PhantomLogicalParty.Phase.DRAINING, group.backgroundCursor(), now,
						wait != null && wait.matches(group) ? wait.replanReason() : "WAIT_NATIVE_RETENTION_ENDED");
				}
			}
			else if (group.phase() == PhantomLogicalParty.Phase.ASSEMBLING)
			{ _logical.transition(row, PhantomLogicalParty.Phase.NATIVE_ACTIVE, group.backgroundCursor(), now + 60_000, ""); }
			else if (group.phase() == PhantomLogicalParty.Phase.WAKE_REQUESTED)
			{ _logical.transition(row, PhantomLogicalParty.Phase.ASSEMBLING, group.backgroundCursor(), now, ""); }
			else { _logical.transition(row, PhantomLogicalParty.Phase.WAKE_REQUESTED, group.backgroundCursor(), now, ""); }
			return;
		}
		if (group.phase() == PhantomLogicalParty.Phase.NATIVE_ACTIVE)
		{
			if (journey == PhantomPartyCoordinator.FarmJourneyProof.RETAINED)
			{ _logical.transition(row, group.phase(), group.backgroundCursor(), now + 5000, "WAIT_PARTY_JOURNEY_OBSERVATION"); }
			else { _logical.transition(row, PhantomLogicalParty.Phase.DRAINING, group.backgroundCursor(), now, "WAIT_NATIVE_PARTY_LOST"); }
			return;
		}
		_logicalWake.accept(group, materialized.getFirst().profileId());
		if (group.phase() == PhantomLogicalParty.Phase.AGREED || group.phase() == PhantomLogicalParty.Phase.BACKGROUND)
		{ _logical.transition(row, PhantomLogicalParty.Phase.WAKE_REQUESTED, group.backgroundCursor(), now + 5000, "WAIT_ADMISSION"); return; }
		if (_offers.values().stream().anyMatch(offer -> offer.roster().stream().anyMatch(member -> group.contains(member.actor().profileId())))) { return; }
		final var roster = new ArrayList<PhantomPartyRendezvousState.Participant>();
		final var physicalLeader = materialized.stream().filter(actor -> _backend.memberSnapshot(actor).filter(PhantomAutonomousFarmParty::nativeParticipant).isPresent())
			.sorted(Comparator.comparing((MemberRef actor) -> actor.profileId() != group.leaderProfileId()).thenComparingLong(MemberRef::profileId)).findFirst().orElse(null);
		if (physicalLeader == null) { _logical.transition(row, group.phase(), group.backgroundCursor(), now + 5000, "WAIT_NATIVE_ROLE"); return; }
		final var ordered = new ArrayList<>(materialized); ordered.remove(physicalLeader); ordered.addFirst(physicalLeader);
		String waitReason = "WAIT_NATIVE_ROLE";
		for (var actor : ordered)
		{
			final var root = _goals.load(actor.profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
			if (!ordinaryIntent(actor.profileId(), root)) { waitReason = "WAIT_INTENT_" + actor.profileId(); continue; }
			if (_preparationRequired.test(actor.profileId(), root)) { waitReason = "WAIT_PREPARATION_" + actor.profileId(); continue; }
			if (!unreserved(actor, now)) { waitReason = "WAIT_RESERVATION_" + actor.profileId(); continue; }
			if (!_backend.autonomousFarmReservationAvailable(actor)) { waitReason = "WAIT_NATIVE_OWNER_" + actor.profileId(); continue; }
			final var snapshot = _backend.memberSnapshot(actor).orElse(null);
			if (snapshot == null || !nativeParticipant(snapshot)) { continue; }
			roster.add(new PhantomPartyRendezvousState.Participant(actor, _backend.materializationEpoch(actor), root.goalId(), root.revision()));
		}
		if (roster.size() < 2)
		{ _logical.transition(row, group.phase(), group.backgroundCursor(), now + 5000, waitReason); return; }
		final String identity = org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256(group.groupId() + ":" + group.generation() + ":" + roster + ":" + now);
		PhantomPartyRendezvousState offer = null;
		for (var participant : roster)
		{
			final var before = _coordinator.rendezvous(participant.actor().profileId()).orElse(null);
			final var proposed = new PhantomPartyRendezvousState(participant.actor().profileId(), identity, roster.getFirst().actor().profileId(), roster, group.objective(), now,
				now + PhantomPartyRendezvousState.MAXIMUM_LIFETIME_MILLIS, PhantomPartyRendezvousState.Phase.PROPOSED, participant.goalRevision());
			if (offer == null) { offer = proposed; }
			if (_coordinator.saveRendezvous(before == null ? -1 : before.rowVersion(), proposed).isEmpty()) { cancelOffer(offer); return; }
		}
		if (offer != null && currentOffer(offer, false))
		{
			_offers.put(offer.leaderProfileId(), offer);
			_logical.transition(row, PhantomLogicalParty.Phase.ASSEMBLING, group.backgroundCursor(), now + 5000, "WAIT_RENDEZVOUS");
		}
	}

	@FunctionalInterface
	public interface RendezvousPreparation
	{
		boolean prepare(long profileId, PhantomGoal expectedGoal, PhantomDomainRef sharedSource, long expiresMillis, java.util.function.BooleanSupplier currentReservation);
		/** One pulse-local input; the predicates must not read persistence or perform native actions per source. */
		default SourcePreview preview(MemberRef actor, long epoch, PhantomGoal root)
		{
			final var source = farmSource(root);
			return new SourcePreview(() -> source == null ? List.of() : List.of(source), _ -> true, () -> true, _ -> false);
		}
		/** Cached negative proof only; this check must not advance travel or acquire persistence ownership. */
		default boolean unusable(MemberRef actor, long epoch, PhantomGoal currentGoal) { return false; }
		/** A canonical destination for the existing native Party route owner, never an arrival receipt. */
		default java.util.Optional<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> nativePartyDestination(PhantomGoal root) { return java.util.Optional.empty(); }
		default java.util.Optional<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> nativePartyDestination(PhantomDomainRef anchor) { return java.util.Optional.empty(); }
		/** Release only this exact shared travel owner before canonical Party takes movement. */
		default boolean yieldForNativeParty(MemberRef actor, long epoch, PhantomGoal root, java.util.function.BooleanSupplier current) { return current.getAsBoolean(); }
	}
	/** Immutable captured input under the existing preparation owner, never consent, arrival or action authority. */
	public record SourcePreview(java.util.function.Supplier<List<PhantomDomainRef>> candidates, java.util.function.Predicate<PhantomDomainRef> reachable,
		java.util.function.BooleanSupplier current, java.util.function.Predicate<PhantomDomainRef> structurallyUnavailable,
		java.util.function.Function<java.util.function.Predicate<PhantomDomainRef>, List<PhantomDomainRef>> sourcesMatching)
	{
		public SourcePreview(java.util.function.Supplier<List<PhantomDomainRef>> candidates, java.util.function.Predicate<PhantomDomainRef> reachable,
			java.util.function.BooleanSupplier current, java.util.function.Predicate<PhantomDomainRef> structurallyUnavailable)
		{ this(candidates, reachable, current, structurallyUnavailable, admitted -> candidates.get().stream().filter(admitted).limit(64).toList()); }
		public SourcePreview { java.util.Objects.requireNonNull(candidates); java.util.Objects.requireNonNull(reachable); java.util.Objects.requireNonNull(current); java.util.Objects.requireNonNull(structurallyUnavailable); java.util.Objects.requireNonNull(sourcesMatching); }
		public List<PhantomDomainRef> sources() { return candidates.get().stream().distinct().limit(64).toList(); }
		public static SourcePreview unavailable() { return new SourcePreview(List::of, _ -> false, () -> false, _ -> false); }
	}
	private static boolean sourceAdmitted(SourcePreview preview, PhantomDomainRef source) { return preview.current().getAsBoolean() && preview.reachable().test(source); }
	private static List<PhantomDomainRef> sourceOptions(SourcePreview preview, PhantomDomainRef current) { return preview.current().getAsBoolean() ? preview.sources() : List.of(); }
	private static boolean terminalSourceLoss(SourcePreview preview, PhantomDomainRef source) { return preview.current().getAsBoolean() && preview.structurallyUnavailable().test(source); }
	private SourcePreview sourcePreview(MemberRef actor, Map<Long, SourcePreview> previews)
	{
		final var existing = previews.get(actor.profileId());
		if (existing != null) { return existing; }
		if (previews.size() >= MAXIMUM_CANDIDATES) { return SourcePreview.unavailable(); }
		final var root = _goals.load(actor.profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		final var captured = root == null ? SourcePreview.unavailable() : _rendezvousPreparation.preview(actor, _backend.materializationEpoch(actor), root);
		final var preview = captured == null ? SourcePreview.unavailable() : captured;
		previews.put(actor.profileId(), preview); return preview;
	}

	public PhantomAutonomousFarmParty(PhantomPartyCoordinator coordinator, PhantomPartyBackend backend, PhantomGoalStore goals, PhantomPartyRoleCatalog roles, LongSupplier clock,
		java.util.function.BiPredicate<Long, PhantomGoal> preparationRequired, java.util.function.BiFunction<Long, PhantomGoal, PhantomVisibleFarmTravel.ArrivalResult> arrival,
		org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig.PartySeekingSettings seeking, RendezvousPreparation rendezvousPreparation, LongSupplier epochMillis)
	{
		this(coordinator, backend, goals, roles, clock, preparationRequired, arrival);
		_seeking = java.util.Objects.requireNonNull(seeking); _rendezvousPreparation = java.util.Objects.requireNonNull(rendezvousPreparation); _epochMillis = java.util.Objects.requireNonNull(epochMillis);
	}

	public PhantomAutonomousFarmParty(PhantomPartyCoordinator coordinator, PhantomPartyBackend backend, PhantomGoalStore goals, PhantomPartyRoleCatalog roles, LongSupplier clock)
	{
		this(coordinator, backend, goals, roles, clock, null);
	}

	public PhantomAutonomousFarmParty(PhantomPartyCoordinator coordinator, PhantomPartyBackend backend, PhantomGoalStore goals, PhantomPartyRoleCatalog roles, LongSupplier clock, PhantomVisibleFarmTravel travel)
	{
		this(coordinator, backend, goals, roles, clock, (_, _) -> false, travel == null ? null : travel::observeArrival);
	}

	public PhantomAutonomousFarmParty(PhantomPartyCoordinator coordinator, PhantomPartyBackend backend, PhantomGoalStore goals, PhantomPartyRoleCatalog roles, LongSupplier clock,
		java.util.function.BiPredicate<Long, PhantomGoal> preparationRequired, java.util.function.BiFunction<Long, PhantomGoal, PhantomVisibleFarmTravel.ArrivalResult> arrival)
	{
		_coordinator = coordinator; _backend = backend; _goals = goals; _roles = new PhantomPartyRoleMatcher(roles); _clock = clock;
		_preparationRequired = java.util.Objects.requireNonNull(preparationRequired); _arrival = arrival;
		_coordinator.installManagedInvitationPolicy(PhantomBackgroundGoalSpec.GOAL_TYPE, this::evaluateInvitation);
		_coordinator.installManagedInvitationPolicy(PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE, this::evaluateInvitation);
	}

	@Override
	public void onPulse()
	{
		if (_coordinator.snapshot().state() != PhantomPartyCoordinator.State.RUNNING) { return; }
		final long now = _clock.getAsLong();
		// An admitted route/store may temporarily close action admission and leave the ready page.
		// Like refreshFarmIntent, durable reads belong to a one-second control-plane window.
		// Continue at most two distinct owned journeys; native consent still reads preparation immediately.
		if (_arrival != null && now >= _nextContinuationPulse)
		{
			_nextContinuationPulse = now + 1_000_000_000L;
			int examined = 0;
			for (var entry : new ArrayList<>(_travelContinuations.entrySet()))
			{
				if (examined++ >= 2) { break; }
				if (_logical != null && _logical.knownGroupFor(entry.getKey()).map(row -> row.group().phase() == PhantomLogicalParty.Phase.DRAINING || row.group().activityReplanRequired()).orElse(false))
				{ _travelContinuations.remove(entry.getKey()); continue; }
				final var member = _backend.currentMember(entry.getKey()).orElse(null);
				// Native Party takeover removes only this scheduler bookmark, never the travel owner's receipt.
				if (member != null && _backend.observe(member).isPresent()) { _travelContinuations.remove(entry.getKey()); continue; }
				final boolean current = _goals.load(entry.getKey()).map(PhantomGoalStore.StoredGoal::goal).filter(entry.getValue()::equals).isPresent();
				// Supply owns its exact yield/drain path; a farm continuation must not cancel it.
				if (current && _preparationRequired.test(entry.getKey(), entry.getValue())) { _travelContinuations.remove(entry.getKey()); _travelContinuations.put(entry.getKey(), entry.getValue()); continue; }
				_travelContinuations.remove(entry.getKey());
				final var arrival = _arrival.apply(entry.getKey(), entry.getValue());
				if (arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.PENDING) { _travelContinuations.put(entry.getKey(), entry.getValue()); }
			}
		}
		if (now < _nextPulse || _coordinator.snapshot().state() != PhantomPartyCoordinator.State.RUNNING) { return; }
		_nextPulse = now + 5_000_000_000L;
		final var observed = _backend.materializedFarmMembers(_cursor, MAXIMUM_CANDIDATES);
		final var page = observed.members();
		if (_logical != null && _seeking != null) { logicalPulse(page); }
		final long previousCursor = _cursor;
		_cursor = observed.lastExaminedProfileId();
		if (_seeking != null) { rendezvousPulse(page, now); }
		if (page.isEmpty()) { if (_cursor == previousCursor) { _cursor = 0; } return; }
		if (page.size() > MAXIMUM_CANDIDATES) { return; }
		final Map<Long, PhantomGoal> roots = new LinkedHashMap<>();
		for (var member : page)
		{
			final long id = member.ref().profileId();
			final var root = _goals.load(id).map(PhantomGoalStore.StoredGoal::goal).filter(goal -> ordinaryIntent(id, goal)).orElse(null);
			if (root == null) { continue; }
			if (_preparationRequired.test(id, root))
			{
				if (!_coordinator.releaseAutonomousFarmForPreparation(id, root.goalId(), root.revision())) { _coordinator.reconcileAutonomousFarmAbsence(id, root.goalId(), root.revision()); }
			}
			else { roots.put(id, root); }
		}
		for (var member : page)
		{
			final long id = member.ref().profileId(); final var goal = roots.get(id);
			final var claim = _coordinator.claim(id).orElse(null);
			final var state = claim == null ? null : claim.state();
			final boolean abortedFirstFormation = state != null && state.status() == StateStatus.LEADER && state.leader().profileId() == id
				&& OBJECTIVE_NAMESPACE.equals(state.objectiveRef().namespace()) && state.phantomMembers().equals(List.of(state.leader()))
				&& state.realMembers().isEmpty() && state.route() == null && state.operation() != null
				&& state.operation().kind() == OperationKind.JOIN && state.operation().phase() == OperationPhase.ABORTED;
			if (goal != null && claim != null && (claim.state().status() == StateStatus.INCONSISTENT && "restart.claim_conflict".equals(claim.state().lastFailureKey())
				|| abortedFirstFormation))
			{
				if (_coordinator.reconcileAutonomousFarmAbsence(id, goal.goalId(), goal.revision())) { return; }
				roots.remove(id);
			}
		}
		for (var leader : page)
		{
			final long profileId = leader.ref().profileId();
			if (_logical != null && _logical.knownGroupFor(profileId).map(row -> row.group().phase() == PhantomLogicalParty.Phase.DRAINING).orElse(false)) { continue; }
			final PhantomGoal goal = roots.get(profileId);
			if (goal == null || !nativeParticipant(leader) || leader.dead()) { continue; }
			final var claim = _coordinator.claim(profileId).orElse(null);
			// Production new formations require the persisted pre-party agreement below.
			// Existing native groups keep their independent lifecycle even when seeking is disabled.
			if (_seeking != null && (claim == null || claim.state().status() == StateStatus.SOLO)) { continue; }
			if (claim != null && claim.state().status() != StateStatus.SOLO && (!claim.state().leader().equals(leader.ref()) || !OBJECTIVE_NAMESPACE.equals(claim.state().objectiveRef().namespace()))) { continue; }
			if ((claim == null || claim.state().status() == StateStatus.SOLO) && _backend.observe(leader.ref()).isPresent()) { continue; }
			if ((claim == null || claim.state().status() == StateStatus.SOLO) && !_backend.autonomousFarmAvailable(leader.ref())) { continue; }
			final int level = _backend.currentLevel(leader.ref()).orElse(0);
			if (level < 40) { continue; }
			final var candidates = page.stream().filter(member -> roots.containsKey(member.ref().profileId()) && Math.abs(_backend.currentLevel(member.ref()).orElse(0) - level) <= 8)
				.filter(member -> _backend.autonomousFarmAvailable(member.ref()))
				.filter(member -> _coordinator.claim(member.ref().profileId()).map(other -> other.state().status() == StateStatus.SOLO || other.state().leader().equals(leader.ref())).orElse(true))
				.filter(member -> _backend.observe(member.ref()).map(party -> party.leader().equals(leader.ref())).orElse(true)).toList();
			final List<MemberRef> selected;
			if (claim != null && claim.state().status() != StateStatus.SOLO)
			{
				final var planned = plannedProfiles(claim.state().objectiveRef());
				selected = planned.stream().map(_backend::currentMember).flatMap(java.util.Optional::stream).toList();
				if (planned.size() != selected.size() || selected.stream().anyMatch(ref -> _backend.memberSnapshot(ref).isEmpty())) { continue; }
			}
			else { selected = selectTeam(_roles, leader, candidates); }
			if (selected.isEmpty()) { if (_coordinator.committed(profileId)) { _coordinator.releaseAutonomousFarm(profileId); } continue; }
			if (selected.stream().anyMatch(ref -> _goals.load(ref.profileId()).map(PhantomGoalStore.StoredGoal::goal).filter(root -> _preparationRequired.test(ref.profileId(), root)).isPresent())) { continue; }
			final var roster = selected.stream().map(_backend::memberSnapshot).flatMap(java.util.Optional::stream).toList();
			if (roster.size() != selected.size() || roster.size() < 2 || roster.stream().anyMatch(member -> !nativeParticipant(member))
				|| selected.stream().anyMatch(ref -> _goals.load(ref.profileId()).map(PhantomGoalStore.StoredGoal::goal).filter(root -> ordinaryIntent(ref.profileId(), root)).isEmpty())) { _coordinator.releaseAutonomousFarm(profileId); continue; }
			if (leader.casting() || leader.attacking()) { continue; }
			if (_arrival != null)
			{
				boolean arrived = true;
				for (var member : selected)
				{
					final var memberClaim = _coordinator.claim(member.profileId()).orElse(null);
					if (memberClaim != null && memberClaim.state().status() != StateStatus.SOLO || _backend.observe(member).isPresent()) { continue; }
					if (!_travelContinuations.containsKey(member.profileId()) && _travelContinuations.size() >= MAXIMUM_CANDIDATES) { arrived = false; continue; }
					final var root = _goals.load(member.profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
					if (!ordinaryIntent(member.profileId(), root) || _preparationRequired.test(member.profileId(), root)) { arrived = false; continue; }
					final var arrival = _arrival.apply(member.profileId(), root);
					if (arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.PENDING) { _travelContinuations.put(member.profileId(), root); }
					else { _travelContinuations.remove(member.profileId()); }
					arrived &= arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.ARRIVED;
				}
				if (!arrived) { return; }
			}
			final String plan = selected.stream().map(ref -> Long.toString(ref.profileId())).collect(java.util.stream.Collectors.joining(","));
			final var nativeParty = _backend.observe(leader.ref()).orElse(null);
			if (claim == null || nativeParty == null || !_coordinator.committed(profileId))
			{
				final var formed = _coordinator.formForGoal(profileId, goal.goalId(), goal.revision(), goal.goalType(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef(OBJECTIVE_NAMESPACE, plan), requirements(roster));
				if (formed != CommandOutcome.ACCEPTED && formed != CommandOutcome.IDEMPOTENT) { continue; }
			}
			if (nativeParty != null && (nativeParty.members().size() > 9 || nativeParty.members().stream().anyMatch(member -> !selected.contains(member)))) { continue; }
			final var missing = selected.stream().filter(ref -> !ref.equals(leader.ref()) && (nativeParty == null || !nativeParty.members().contains(ref)))
				.sorted(Comparator.comparingDouble(ref -> _backend.memberSnapshot(ref).map(member -> distance(leader, member)).orElse(Double.MAX_VALUE))).findFirst();
			if (missing.isPresent()) { if (nativeParty == null || nativeParty.members().size() < 9) { _coordinator.invite(profileId, missing.orElseThrow(), PartyDistributionType.FINDERS_KEEPERS); } }
			else if (!advanceNativePartyRoute(profileId, goal, roster))
			{
				if (_coordinator.requestAutonomousFarm(profileId)) { _farmWaits.remove(profileId); }
				else { waitForFarm(profileId, leader.ref(), goal, now); }
			}
			// One form/invite/pull per shared pulse slice, including pending native consent.
			return;
		}
	}

	private void waitForFarm(long id, MemberRef leader, PhantomGoal root, long nowNanos)
	{
		final String detail = _backend.autonomousFarmWaitReason(leader);
		final String code = detail.split(":", 2)[0].matches("WAIT_[A-Z0-9_]{1,70}") ? detail.split(":", 2)[0] : "WAIT_GROUP_READINESS";
		final var row = _logical == null ? null : _logical.knownGroupFor(id).orElse(null);
		final var group = row == null ? null : row.group();
		final var before = _farmWaits.get(id);
		if (before == null && _farmWaits.size() >= 1024) { _farmWaits.remove(_farmWaits.keySet().iterator().next()); }
		final boolean sameGroup = before != null && before.matches(group);
		final var wait = new FarmWait(sameGroup ? before.sinceNanos() : nowNanos, code, group == null ? "" : group.groupId(), group == null ? 0 : group.generation());
		_farmWaits.put(id, wait);
		if (before == null || !before.reason().equals(code))
		{ org.l2jmobius.gameserver.phantoms.diagnostics.PhantomRuntimeFlightRecorder.getInstance().record(id, "PARTY_FARM_WAIT", code, "", detail + ";nextDueMillis=" + (_epochMillis.getAsLong() + 5000) + ";requiredChange=source_resources_or_observed_threat", _backend.materializationEpoch(leader), root.goalId(), root.revision()); }
		if (row != null && nowNanos - wait.sinceNanos() >= 60_000_000_000L)
		{
			if (group.phase() != PhantomLogicalParty.Phase.DRAINING)
			{ _logical.transition(row, PhantomLogicalParty.Phase.DRAINING, group.backgroundCursor(), _epochMillis.getAsLong(), wait.replanReason()); }
			_farmWaits.remove(id);
		}
	}

	private boolean advanceNativePartyRoute(long leaderId, PhantomGoal root, List<MemberSnapshot> roster)
	{
		if (_rendezvousPreparation == null) { return false; }
		final var group = _logical == null ? null : _logical.knownGroupFor(leaderId).map(PhantomPartyPersistencePort.StoredLogicalGroup::group).orElse(null);
		final var anchor = group == null ? root.selectedAnchor() : new PhantomDomainRef("topology.anchor", group.anchorId());
		final var destination = (group == null ? _rendezvousPreparation.nativePartyDestination(root) : _rendezvousPreparation.nativePartyDestination(anchor)).orElse(null);
		if (destination == null) { return group != null; }
		if (!_coordinator.usesFarmItinerary() && roster.stream().allMatch(member -> member.instanceId() == destination.instanceId()
			&& Math.abs((long) member.z() - destination.z()) <= 200 && Math.hypot((long) member.x() - destination.x(), (long) member.y() - destination.y()) <= 300)) { return false; }
		final var result = _coordinator.requestAutonomousFarmRoute(leaderId, anchor, destination);
		if (_coordinator.usesFarmItinerary() && result.outcome() == PhantomPartyCoordinator.RouteOutcome.READY) { return false; }
		if (result.outcome() == PhantomPartyCoordinator.RouteOutcome.FAILED || result.outcome() == PhantomPartyCoordinator.RouteOutcome.REJECTED || result.outcome() == PhantomPartyCoordinator.RouteOutcome.UNAVAILABLE)
		{
			java.util.logging.Logger.getLogger(PhantomAutonomousFarmParty.class.getName()).warning("Party native route declined;profile=" + leaderId + ";outcome=" + result.outcome() + ";code=" + result.reasonKey());
			_coordinator.releaseAutonomousFarm(leaderId, result.reasonKey());
		}
		return true;
	}

	public ManagedInvitationDecision evaluateInvitation(ManagedInvitationContext context)
	{
		if (_seeking != null)
		{
			final var reservation = _coordinator.rendezvous(context.inviteeProfileId()).orElse(null);
			final var nativeCore = _backend.currentMember(context.requesterProfileId()).flatMap(_backend::observe).orElse(null);
			// The durable canonical roster, not a pre-party reservation, owns a previously observed membership.
			final boolean retainedNativeMember = context.leaderClaim() != null && context.leaderClaim().phantomMembers().size() >= 2
				&& context.leaderClaim().phantomMembers().stream().anyMatch(member -> member.profileId() == context.inviteeProfileId()
					&& member.characterObjectId() == context.invitation().inviteeObjectId());
			if ((nativeCore == null || nativeCore.members().size() < 2) && !retainedNativeMember && reservation != null && reservation.state().phase() != PhantomPartyRendezvousState.Phase.COMPLETE
				&& (reservation.state().phase() != PhantomPartyRendezvousState.Phase.ARRIVED || !currentOffer(reservation.state(), true))
				&& !logicalFormationReady(reservation.state(), true)) { return ManagedInvitationDecision.REFUSE; }
		}
		final var leader = context.leaderClaim();
		final var invitee = context.inviteeClaim();
		if (context.requesterProfileId() <= 0 || context.inviteeProfileId() <= 0 || leader == null || invitee == null || context.inviteeSnapshot() == null || !ordinaryIntent(context.inviteeProfileId(), context.inviteeGoal())
			|| !OBJECTIVE_NAMESPACE.equals(leader.objectiveRef().namespace()) || !leader.groupId().equals(invitee.groupId()) || leader.groupGeneration() != invitee.groupGeneration()
			|| leader.leader().profileId() != context.requesterProfileId() || leader.leader().characterObjectId() != context.invitation().requesterObjectId()
			|| context.inviteeSnapshot().ref().characterObjectId() != context.invitation().inviteeObjectId() || leader.operation() == null || invitee.operation() == null
			|| leader.operation().invitationSequence() != context.invitation().identity().sequence() || invitee.operation().invitationSequence() != context.invitation().identity().sequence()) { return ManagedInvitationDecision.REFUSE; }
		final PhantomGoal root = _goals.load(context.requesterProfileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
		if (_preparationRequired.test(context.requesterProfileId(), root) || _preparationRequired.test(context.inviteeProfileId(), context.inviteeGoal())) { return ManagedInvitationDecision.REFUSE; }
		final var currentLeader = _backend.memberSnapshot(leader.leader()).orElse(null);
		final var currentInvitee = _backend.currentMember(context.inviteeProfileId()).orElse(null);
		if (!ordinaryIntent(context.requesterProfileId(), root) || root.goalId() != leader.operation().leaderGoalId() || root.revision() != leader.operation().leaderGoalRevision()
			|| currentLeader == null || !context.inviteeSnapshot().ref().equals(currentInvitee)
			|| (_logical == null ? !compatible(currentLeader, context.inviteeSnapshot()) : context.inviteeSnapshot().dead())
			|| Math.abs(_backend.currentLevel(leader.leader()).orElse(0) - _backend.currentLevel(currentInvitee).orElse(1000)) > 8
			|| _backend.observe(currentInvitee).isPresent() || !nativeParticipant(context.inviteeSnapshot())) { return ManagedInvitationDecision.REFUSE; }
		if (!_backend.autonomousFarmConsentAvailable(leader.leader(), currentInvitee, context.invitation().identity())) { return ManagedInvitationDecision.DEFER; }
		return ManagedInvitationDecision.ACCEPT;
	}

	public static List<Long> plannedProfiles(PhantomDomainRef objective)
	{
		if (objective == null || !OBJECTIVE_NAMESPACE.equals(objective.namespace()) || !objective.key().matches("[1-9][0-9]{0,17}(,[1-9][0-9]{0,17}){1,8}")) { return List.of(); }
		final var profiles = java.util.Arrays.stream(objective.key().split(",")).map(Long::parseLong).toList();
		return profiles.stream().distinct().count() == profiles.size() ? profiles : List.of();
	}

	public static PhantomDomainRef farmSource(PhantomGoal goal)
	{
		if (goal == null || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType())) { return null; }
		try
		{
			PhantomBackgroundGoalSpec.parse(goal);
			return goal.validSources().stream().filter(source -> PhantomBackgroundGoalSpec.SOURCE_NAMESPACE.equals(source.namespace())).findFirst().orElse(null);
		}
		catch (IllegalArgumentException exception) { return null; }
	}

	/** One bounded control-plane slice, sharing the existing Party scheduler and native owners. */
	private boolean rendezvousPulse(List<MemberSnapshot> page, long nowNanos)
	{
		final long now = _epochMillis.getAsLong();
		try
		{
			_discovered.entrySet().removeIf(entry -> nowNanos - entry.getValue().observedNanos() > 60_000_000_000L);
			for (var member : page)
			{
				final var saved = _coordinator.rendezvous(member.ref().profileId()).orElse(null);
				if (_discovered.containsKey(member.ref().profileId()) || _discovered.size() < 256)
				{
					final var goal = _goals.load(member.ref().profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
					final boolean available = _backend.autonomousFarmReservationAvailable(member.ref()) && _backend.observe(member.ref()).isEmpty()
						&& _coordinator.claim(member.ref().profileId()).map(claim -> claim.state().status() == StateStatus.SOLO).orElse(true) && unreserved(saved, member.ref(), now)
						&& ordinaryIntent(member.ref().profileId(), goal) && !_preparationRequired.test(member.ref().profileId(), goal);
					_discovered.put(member.ref().profileId(), new Discovery(member, _backend.currentLevel(member.ref()).orElse(0), nowNanos, available));
				}
				if (saved != null && saved.state().active(now) && _offers.size() < 32) { _offers.put(saved.state().leaderProfileId(), saved.state()); }
			}
			for (var offer : new ArrayList<>(_offers.values()))
			{
				if (completeNativeOffer(offer)) { _offers.remove(offer.leaderProfileId()); _assemblyCursors.remove(offer.leaderProfileId()); continue; }
				if (!offer.active(now) || invalidOffer(offer)) { cancelOffer(offer); _offers.remove(offer.leaderProfileId()); _assemblyCursors.remove(offer.leaderProfileId()); continue; }
				if (!currentOffer(offer, false)) { continue; }
				if (advanceOffer(offer))
				{
					if (_offers.remove(offer.leaderProfileId()) != null) { _offers.put(offer.leaderProfileId(), offer); }
					return true;
				}
			}
			if (!_seeking.enabled() || _logical != null) { return false; }
			final var candidates = _discovered.values().stream().filter(Discovery::available).filter(entry -> _seeking.seeks(entry.member().ref().profileId(), entry.level())).map(Discovery::member)
				.sorted(Comparator.comparingLong(member -> member.ref().profileId())).toList();
			final var previews = new LinkedHashMap<Long, SourcePreview>();
			for (var leader : candidates.stream().sorted(Comparator.comparing((MemberSnapshot member) -> member.ref().profileId() <= _leaderCursor).thenComparingLong(member -> member.ref().profileId())).limit(4).toList())
			{
				_leaderCursor = leader.ref().profileId();
				if (!nativeParticipant(leader) || !unreserved(leader.ref(), now) || _backend.observe(leader.ref()).isPresent()
					|| _coordinator.claim(leader.ref().profileId()).map(claim -> claim.state().status() != StateStatus.SOLO).orElse(false)) { continue; }
				final var root = _goals.load(leader.ref().profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
				final var currentSource = farmSource(root);
				if (currentSource == null || !ordinaryIntent(leader.ref().profileId(), root) || _preparationRequired.test(leader.ref().profileId(), root)) { continue; }
				final var leaderPreview = sourcePreview(leader.ref(), previews);
				for (var source : leaderPreview.current().getAsBoolean() ? leaderPreview.sourcesMatching().apply(source -> candidates.stream()
					.filter(member -> !member.ref().equals(leader.ref()) && nativeParticipant(member)).anyMatch(member -> sourceAdmitted(sourcePreview(member.ref(), previews), source))) : List.<PhantomDomainRef>of())
				{
				if (!sourceAdmitted(leaderPreview, source)) { continue; }
				final int level = _discovered.get(leader.ref().profileId()).level();
				final var nearby = candidates.stream().filter(member -> Math.abs(_discovered.get(member.ref().profileId()).level() - level) <= 8)
					.filter(member -> !member.ref().equals(leader.ref()) && nativeParticipant(member) && sourceAdmitted(sourcePreview(member.ref(), previews), source))
					.sorted(Comparator.comparing((MemberSnapshot member) -> !support(member)).thenComparingLong(member -> member.ref().profileId())).limit(MAXIMUM_CANDIDATES - 1).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
				nearby.addFirst(leader);
				final var team = selectRendezvousTeam(_roles, leader, nearby);
				if (team.size() < 2 || PhantomPartyFarmEligibility.forRoster(team.stream().map(actor -> _backend.currentLevel(actor).orElse(0)).toList()).isEmpty()) { continue; }
				final var roster = new ArrayList<PhantomPartyRendezvousState.Participant>();
				for (var actor : team)
				{
					final var goal = _goals.load(actor.profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
					final var snapshot = _backend.memberSnapshot(actor).orElse(null);
					if (!ordinaryIntent(actor.profileId(), goal) || _preparationRequired.test(actor.profileId(), goal) || !unreserved(actor, now)
						|| !_backend.autonomousFarmReservationAvailable(actor) || _backend.observe(actor).isPresent() || snapshot == null || !recruitmentCompatible(leader, snapshot)
						|| !_seeking.seeks(actor.profileId(), _backend.currentLevel(actor).orElse(0))
						|| _coordinator.claim(actor.profileId()).map(claim -> claim.state().status() != StateStatus.SOLO).orElse(false)) { break; }
					roster.add(new PhantomPartyRendezvousState.Participant(actor, _backend.materializationEpoch(actor), goal.goalId(), goal.revision()));
				}
				if (roster.size() != team.size()) { continue; }
				// Recheck the whole selected roster before the first durable proposal; no root/adoption has happened.
				if (team.stream().anyMatch(actor -> !sourceAdmitted(sourcePreview(actor, previews), source))) { continue; }
				final String identity = org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256(roster + ":" + source + ":" + now);
				PhantomPartyRendezvousState offer = null;
				for (var participant : roster)
				{
					final var before = _coordinator.rendezvous(participant.actor().profileId()).orElse(null);
					if (!unreserved(participant.actor(), now)) { break; }
					final var proposed = new PhantomPartyRendezvousState(participant.actor().profileId(), identity, leader.ref().profileId(), roster, source, now,
						Math.addExact(now, PhantomPartyRendezvousState.MAXIMUM_LIFETIME_MILLIS), PhantomPartyRendezvousState.Phase.PROPOSED, participant.goalRevision());
					if (offer == null) { offer = proposed; }
					if (_coordinator.saveRendezvous(before == null ? -1 : before.rowVersion(), proposed).isEmpty()) { break; }
				}
				if (offer != null && currentOffer(offer, false))
				{
					_offers.put(leader.ref().profileId(), offer);
					for (var participant : roster) { _discovered.remove(participant.actor().profileId()); }
				}
				else if (offer != null) { cancelOffer(offer); }
				return true;
				}
			}
		}
		catch (RuntimeException exception) { diagnostic("physical_rendezvous", exception); return false; }
		return false;
	}

	private boolean unreserved(MemberRef actor, long now)
	{
		final var prior = _coordinator.rendezvous(actor.profileId()).orElse(null);
		return unreserved(prior, actor, now);
	}

	private boolean unreserved(PhantomPartyPersistencePort.StoredRendezvous prior, MemberRef actor, long now)
	{
		if (prior == null || !prior.state().participant().actor().equals(actor) || prior.state().participant().epoch() != _backend.materializationEpoch(actor)) { return true; }
		// A changed peer lifetime needs fresh consent, not the obsolete offer's decline cooldown.
		if (prior.state().phase() == PhantomPartyRendezvousState.Phase.CANCELLED && prior.state().roster().stream()
			.anyMatch(peer -> _backend.materializationEpoch(peer.actor()) != peer.epoch())) { return true; }
		// Cancellation releases Solo immediately, but does not spam the same declined/failed offer again.
		return !prior.state().active(now) && (prior.state().phase() != PhantomPartyRendezvousState.Phase.CANCELLED || now >= prior.state().expiresMillis());
	}

	private boolean completeNativeOffer(PhantomPartyRendezvousState offer)
	{
		final var roster = offer.roster().stream().map(PhantomPartyRendezvousState.Participant::actor).toList();
		final var party = _backend.observe(roster.getFirst()).orElse(null);
		if (party == null || !party.leader().equals(roster.getFirst()) || party.members().size() != roster.size() || !party.members().containsAll(roster)) { return false; }
		for (var participant : offer.roster())
		{
			final var current = _coordinator.rendezvous(participant.actor().profileId()).orElse(null);
			if (current != null && offer.sameOffer(current.state()) && current.state().phase().ordinal() < PhantomPartyRendezvousState.Phase.COMPLETE.ordinal())
			{ _coordinator.saveRendezvous(current.rowVersion(), current.state().withPhase(PhantomPartyRendezvousState.Phase.COMPLETE, current.state().adoptedRevision())); }
		}
		return true;
	}

	private boolean invalidOffer(PhantomPartyRendezvousState offer)
	{
		if (_logical != null && _logical.knownGroupFor(offer.leaderProfileId()).map(row -> belongsTo(offer, row.group())
			&& (row.group().phase() == PhantomLogicalParty.Phase.DRAINING || row.group().activityReplanRequired())).orElse(false)) { return true; }
		if (offer.roster().size() < 2) { return true; }
		final int leaderLevel = _backend.currentLevel(offer.roster().getFirst().actor()).orElse(0);
		for (int index = 0; index < offer.roster().size(); index++)
		{
			final var participant = offer.roster().get(index);
			final var goal = _goals.load(participant.actor().profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
			final var snapshot = _backend.memberSnapshot(participant.actor()).orElse(null);
			if (_backend.materializationEpoch(participant.actor()) != participant.epoch() || !ordinaryIntent(participant.actor().profileId(), goal)
				|| goal.goalId() != participant.goalId() || goal.revision() < participant.goalRevision() || goal.revision() - participant.goalRevision() > 1
				|| goal.revision() != participant.goalRevision() && !offer.source().equals(farmSource(goal))) { return true; }
			if (snapshot != null && snapshot.dead()) { return true; }
			final int level = _backend.currentLevel(participant.actor()).orElse(0);
			if (leaderLevel > 0 && level > 0 && Math.abs(level - leaderLevel) > 8) { return true; }
		}
		return false;
	}

	private boolean currentOffer(PhantomPartyRendezvousState offer, boolean accepted)
	{
		if (_coordinator.snapshot().state() != PhantomPartyCoordinator.State.RUNNING) { return false; }
		for (var participant : offer.roster())
		{
			final var receipt = _coordinator.rendezvous(participant.actor().profileId()).orElse(null);
			final var goal = _goals.load(participant.actor().profileId()).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
			if (receipt == null || !offer.sameOffer(receipt.state()) || !ordinaryIntent(participant.actor().profileId(), goal)
				|| !_backend.currentMember(participant.actor().profileId()).filter(participant.actor()::equals).isPresent()
				|| !receipt.state().current(participant.actor(), _backend.materializationEpoch(participant.actor()), goal.goalId(), goal.revision(), farmSource(goal), _epochMillis.getAsLong())
					&& !(receipt.state().phase() == PhantomPartyRendezvousState.Phase.ACCEPTED && receipt.state().active(_epochMillis.getAsLong())
						&& participant.goalId() == goal.goalId() && goal.revision() - participant.goalRevision() == 1 && offer.source().equals(farmSource(goal))
						&& participant.epoch() == _backend.materializationEpoch(participant.actor()))
				|| accepted && receipt.state().phase() == PhantomPartyRendezvousState.Phase.PROPOSED
				|| _preparationRequired.test(participant.actor().profileId(), goal)) { return false; }
		}
		return true;
	}

	private boolean withAbsentMembers(List<MemberRef> members, int index, java.util.function.BooleanSupplier action)
	{
		return index == members.size() ? action.getAsBoolean() : _backend.withUnmaterializedNativeAbsence(members.get(index), () -> withAbsentMembers(members, index + 1, action));
	}
	private void cancelLogicalOffers(PhantomLogicalParty group)
	{
		for (var offer : new ArrayList<>(_offers.values())) { if (belongsTo(offer, group)) { cancelOffer(offer); _offers.remove(offer.leaderProfileId()); _assemblyCursors.remove(offer.leaderProfileId()); } }
	}
	private void cancelOffer(PhantomPartyRendezvousState offer)
	{
		for (var participant : offer.roster())
		{
			final var current = _coordinator.rendezvous(participant.actor().profileId()).orElse(null);
			if (current != null && offer.sameOffer(current.state()) && current.state().phase().ordinal() < PhantomPartyRendezvousState.Phase.COMPLETE.ordinal())
			{ _coordinator.saveRendezvous(current.rowVersion(), current.state().withPhase(PhantomPartyRendezvousState.Phase.CANCELLED, current.state().adoptedRevision())); }
		}
	}
	static boolean belongsTo(PhantomPartyRendezvousState offer, PhantomLogicalParty group)
	{
		return group.objective().equals(offer.source()) && offer.roster().stream().allMatch(participant -> group.contains(participant.actor().profileId()))
			&& offer.offerId().equals(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256(group.groupId() + ":" + group.generation() + ":" + offer.roster() + ":" + offer.createdMillis()));
	}
	private void terminalOffer(PhantomPartyRendezvousState offer)
	{
		if (_logical != null)
		{
			final var row = _logical.groupFor(offer.leaderProfileId()).orElse(null);
			if (row != null && belongsTo(offer, row.group()) && (row.group().phase() == PhantomLogicalParty.Phase.ASSEMBLING || row.group().phase() == PhantomLogicalParty.Phase.WAKE_REQUESTED))
			{
				_logical.transition(row, PhantomLogicalParty.Phase.DRAINING, row.group().backgroundCursor(), _epochMillis.getAsLong(), "REPLAN_ROUTE");
				row.group().members().forEach(member -> _terminalHandoff.accept(member.profileId()));
			}
		}
		cancelOffer(offer); _offers.remove(offer.leaderProfileId()); _assemblyCursors.remove(offer.leaderProfileId());
	}

	private boolean advanceOffer(PhantomPartyRendezvousState offer)
	{
		for (var participant : offer.roster())
		{
			final var current = _coordinator.rendezvous(participant.actor().profileId()).orElseThrow();
			if (current.state().phase() != PhantomPartyRendezvousState.Phase.PROPOSED) { continue; }
			final var goal = _goals.load(participant.actor().profileId()).orElseThrow().goal();
			if (!_seeking.seeks(participant.actor().profileId(), _backend.currentLevel(participant.actor()).orElse(0)) || !_backend.autonomousFarmReservationAvailable(participant.actor())
				|| _backend.observe(participant.actor()).isPresent()) { cancelOffer(offer); return true; }
			_coordinator.saveRendezvous(current.rowVersion(), current.state().withPhase(PhantomPartyRendezvousState.Phase.ACCEPTED, goal.revision()));
			return true;
		}
		if (!currentOffer(offer, true)) { return false; }
		if (_logical != null)
		{
			if (!logicalFormationReady(offer, false)) { return false; }
			for (var participant : offer.roster())
			{
				final var root = _goals.load(participant.actor().profileId()).orElseThrow().goal();
				if (!_rendezvousPreparation.yieldForNativeParty(participant.actor(), participant.epoch(), root, () -> currentOffer(offer, true))) { return true; }
			}
			return logicalFormationReady(offer, true) && formNativeOffer(offer);
		}
		final var assembly = new ArrayList<>(offer.roster());
		java.util.Collections.rotate(assembly, -_assemblyCursors.getOrDefault(offer.leaderProfileId(), 0));
		for (var participant : assembly)
		{
			final var current = _coordinator.rendezvous(participant.actor().profileId()).orElseThrow();
			if (current.state().phase() == PhantomPartyRendezvousState.Phase.ARRIVED) { continue; }
			_assemblyCursors.put(offer.leaderProfileId(), (offer.roster().indexOf(participant) + 1) % offer.roster().size());
			var goal = _goals.load(participant.actor().profileId()).orElseThrow().goal();
			if (current.state().phase() == PhantomPartyRendezvousState.Phase.ACCEPTED)
			{
				final var preview = _rendezvousPreparation.preview(participant.actor(), participant.epoch(), goal);
				if (preview != null && terminalSourceLoss(preview, offer.source()))
				{ terminalOffer(offer); return true; }
				// A known unusable current source cannot be held ACCEPTED while preparation keeps refusing it.
				if (offer.source().equals(farmSource(goal)) && _rendezvousPreparation.unusable(participant.actor(), participant.epoch(), goal))
				{ terminalOffer(offer); return true; }
				if (!_rendezvousPreparation.prepare(participant.actor().profileId(), goal, offer.source(), offer.expiresMillis(), () -> currentOffer(offer, true))) { return true; }
				goal = _goals.load(participant.actor().profileId()).orElseThrow().goal();
				if (!offer.source().equals(farmSource(goal)) || goal.goalId() != participant.goalId() || goal.revision() < participant.goalRevision() || goal.revision() - participant.goalRevision() > 1) { cancelOffer(offer); return true; }
				_coordinator.saveRendezvous(current.rowVersion(), current.state().withPhase(PhantomPartyRendezvousState.Phase.ASSEMBLING, goal.revision()));
				return true;
			}
			if (_arrival == null) { return false; }
			final var arrival = _arrival.apply(participant.actor().profileId(), goal);
			if (arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.TERMINAL || arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.STALE_GOAL)
			{ terminalOffer(offer); return true; }
			if (arrival.kind() == PhantomVisibleFarmTravel.ArrivalKind.ARRIVED && currentOffer(offer, true))
			{ _coordinator.saveRendezvous(current.rowVersion(), current.state().withPhase(PhantomPartyRendezvousState.Phase.ARRIVED, goal.revision())); }
			return true;
		}
		return formNativeOffer(offer);
	}

	/** Mutual consent and release of Solo work precede the stock invite. Position belongs to travel. */
	private boolean logicalFormationReady(PhantomPartyRendezvousState offer, boolean quiet)
	{
		if (_logical == null || !currentOffer(offer, true)) { return false; }
		final var group = _logical.knownGroupFor(offer.leaderProfileId()).map(row -> row.group()).orElse(null);
		if (group == null || _epochMillis.getAsLong() >= group.expiresAtMillis() || group.phase() == PhantomLogicalParty.Phase.DRAINING
			|| group.activityReplanRequired() || !belongsTo(offer, group)) { return false; }
		for (var participant : offer.roster())
		{
			final long id = participant.actor().profileId();
			final var peerGroup = _logical.knownGroupFor(id).map(row -> row.group()).orElse(null);
			final var root = _goals.load(id).map(PhantomGoalStore.StoredGoal::goal).orElse(null);
			final var receipt = _coordinator.rendezvous(id).orElse(null);
			final var member = _backend.memberSnapshot(participant.actor()).orElse(null);
			if (peerGroup == null || !peerGroup.equals(group) || !_executionOwnership.test(id, group)
				|| root == null || group.members().stream().noneMatch(logical -> logical.profileId() == id
					&& logical.goalId() == root.goalId() && root.revision() >= logical.goalRevision())
				|| receipt == null || root.revision() != receipt.state().adoptedRevision()
				|| member == null || member.dead() || quiet && member.moving() || member.casting() || member.attacking()) { return false; }
		}
		return true;
	}

	private boolean formNativeOffer(PhantomPartyRendezvousState offer)
	{
		final var leader = _backend.memberSnapshot(offer.roster().getFirst().actor()).orElse(null);
		final var roster = offer.roster().stream().map(PhantomPartyRendezvousState.Participant::actor).toList();
		final var snapshots = roster.stream().map(_backend::memberSnapshot).flatMap(java.util.Optional::stream).toList();
		if (leader == null || snapshots.size() != roster.size()
			|| PhantomPartyFarmEligibility.forRoster(roster.stream().map(actor -> _backend.currentLevel(actor).orElse(0)).toList()).isEmpty()
			|| snapshots.stream().anyMatch(member -> (_logical == null ? !compatible(leader, member) : member.dead()) || !_backend.autonomousFarmAvailable(member.ref()))
			|| _roles.match(ObjectiveMode.GENERAL_PVE, requirements(snapshots), snapshots).vacancies().stream().anyMatch(vacancy -> vacancy.status() != VacancyStatus.FILLED)) { return false; }
		final var party = _backend.observe(leader.ref()).orElse(null);
		if (party != null && (!party.leader().equals(leader.ref()) || party.members().stream().anyMatch(member -> !roster.contains(member)))) { cancelOffer(offer); return true; }
		if (party != null && party.members().containsAll(roster))
		{
			for (var participant : offer.roster())
			{
				final var receipt = _coordinator.rendezvous(participant.actor().profileId()).orElseThrow();
				_coordinator.saveRendezvous(receipt.rowVersion(), receipt.state().withPhase(PhantomPartyRendezvousState.Phase.COMPLETE, receipt.state().adoptedRevision()));
			}
			_offers.remove(offer.leaderProfileId()); return false;
		}
		final var root = _goals.load(offer.leaderProfileId()).orElseThrow().goal();
		final var objective = new PhantomDomainRef(OBJECTIVE_NAMESPACE, roster.stream().map(member -> Long.toString(member.profileId())).collect(java.util.stream.Collectors.joining(",")));
		final var formed = _coordinator.formForGoal(offer.leaderProfileId(), root.goalId(), root.revision(), root.goalType(), ObjectiveMode.GENERAL_PVE, objective, requirements(snapshots));
		if (formed != CommandOutcome.ACCEPTED && formed != CommandOutcome.IDEMPOTENT) { return false; }
		roster.stream().filter(member -> !member.equals(leader.ref()) && (party == null || !party.members().contains(member))).findFirst()
			.ifPresent(member -> _coordinator.invite(offer.leaderProfileId(), member, PartyDistributionType.FINDERS_KEEPERS));
		return true;
	}

	/** Existing historical fallback is ordinary unmet farm motivation, never an arbitrary idle command. */
	public static boolean ordinaryIntent(long profileId, PhantomGoal goal)
	{
		if (goal == null || goal.status() != PhantomGoalStatus.ACTIVE) { return false; }
		if (PhantomBackgroundGoalSpec.GOAL_TYPE.equals(goal.goalType())) { return true; }
		return PhantomBackgroundGoalSpec.canonicalHistoricalIdle(profileId, goal);
	}

	public static List<MemberRef> selectTeam(PhantomPartyRoleMatcher roles, MemberSnapshot leader, List<MemberSnapshot> nearby)
	{
		return selectTeam(roles, leader, nearby, false);
	}

	/** Distant preference is negotiation only; compatible() still gates native invitation and combat. */
	public static List<MemberRef> selectRendezvousTeam(PhantomPartyRoleMatcher roles, MemberSnapshot leader, List<MemberSnapshot> candidates)
	{
		if (leader == null || candidates == null || candidates.size() > 256 || !compatible(leader, leader) || !nativeParticipant(leader)) { return List.of(); }
		final var team = new ArrayList<MemberSnapshot>(); team.add(leader);
		candidates.stream().filter(member -> !member.ref().equals(leader.ref()) && recruitmentCompatible(leader, member)
			&& nativeParticipant(member)).distinct().limit(8).forEach(team::add);
		return team.size() < 2 || team.stream().noneMatch(PhantomAutonomousFarmParty::farmContributor) || roles.match(ObjectiveMode.GENERAL_PVE, requirements(team), team).vacancies().stream().anyMatch(vacancy -> vacancy.status() != VacancyStatus.FILLED)
			? List.of() : team.stream().map(MemberSnapshot::ref).toList();
	}

	private static List<MemberRef> selectTeam(PhantomPartyRoleMatcher roles, MemberSnapshot leader, List<MemberSnapshot> nearby, boolean rendezvous)
	{
		if (leader == null || nearby == null || nearby.size() > MAXIMUM_CANDIDATES || !compatible(leader, leader) || !nativeParticipant(leader)) { return List.of(); }
		final var candidates = nearby.stream().filter(member -> !member.ref().equals(leader.ref()) && recruitmentCompatible(leader, member)).sorted(Comparator.comparingDouble((MemberSnapshot member) -> distance(leader, member)).thenComparingLong(member -> member.ref().profileId())).toList();
		final var team = new ArrayList<MemberSnapshot>();
		team.add(leader);
		candidates.stream().filter(member -> compatible(leader, member) && nativeParticipant(member)).limit(8).forEach(team::add);
		// The existing remote JOIN path still needs a local majority; support is an option, not a mandatory slot.
		if (team.size() >= 2 && team.size() < 9 && team.stream().noneMatch(PhantomAutonomousFarmParty::supportPresent))
		{ candidates.stream().filter(member -> !compatible(leader, member) && support(member)).findFirst().ifPresent(team::add); }
		if (team.size() < 2 || team.stream().noneMatch(PhantomAutonomousFarmParty::farmContributor)) { return List.of(); }
		final var requirements = requirements(team);
		if (roles.match(ObjectiveMode.GENERAL_PVE, requirements, team).vacancies().stream().anyMatch(vacancy -> vacancy.status() != VacancyStatus.FILLED)) { return List.of(); }
		return team.stream().map(MemberSnapshot::ref).toList();
	}

	private static List<RoleRequirement> requirements(List<MemberSnapshot> team)
	{
		final var requirements = new ArrayList<RoleRequirement>();
		for (int index = 0; index < team.size(); index++)
		{
			final var member = team.get(index);
			final String role = guardianPresent(member) ? "frontline.guardian" : supportPresent(member) ? supportRole(member) : spoiler(member) ? "utility.spoiler" : damageRole(member);
			requirements.add(new RoleRequirement("farm.slot." + index, role, true, 1));
		}
		return List.copyOf(requirements);
	}

	/** Remaining native HP determines the next episode; the original risk deadline is unchanged. */
	public static double projectedFarmDuration(double remainingHp, double damagePerSecond, double regenerationPerSecond)
	{
		if (!Double.isFinite(remainingHp) || remainingHp <= 0 || !Double.isFinite(damagePerSecond) || !Double.isFinite(regenerationPerSecond) || regenerationPerSecond < 0) { return Double.POSITIVE_INFINITY; }
		final double effective = damagePerSecond - regenerationPerSecond;
		if (effective <= 0) { return Double.POSITIVE_INFINITY; }
		final double millis = remainingHp * 1000d / effective;
		return Double.isFinite(millis) && millis <= 60_000 ? millis : Double.POSITIVE_INFINITY;
	}

	/** Distance and observed-threat proof only; native Geo/door/dry checks remain mandatory. */
	public static boolean permitsFarmApproach(MemberSnapshot actor, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint destination, List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest.PveThreat> threats)
	{
		if (actor == null || destination == null || threats == null || threats.size() > 64 || actor.ref().kind() != MemberKind.PHANTOM || actor.instanceId() != destination.instanceId() || Math.abs((long) actor.z() - destination.z()) > 200) { return false; }
		final double distance = Math.hypot((long) actor.x() - destination.x(), (long) actor.y() - destination.y());
		if (distance > MAXIMUM_TARGET_DISTANCE) { return false; }
		if (distance <= 50) { return true; }
		final var origin = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(actor.x(), actor.y(), actor.z(), actor.instanceId());
		return new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(actor.ref().profileId(), origin, destination, 1, 2, MAXIMUM_TARGET_DISTANCE, threats).permitsWalkSegment(origin, destination);
	}

	public static boolean compatible(MemberSnapshot leader, MemberSnapshot member)
	{
		return member.ref().kind() == MemberKind.PHANTOM && member.ref().characterObjectId() > 0 && !member.dead() && member.hpPercent() >= 50
			&& leader.instanceId() == member.instanceId() && Math.abs((long) leader.z() - member.z()) <= 200
			&& Math.hypot((long) leader.x() - member.x(), (long) leader.y() - member.y()) <= 1200;
	}

	/** Recruitment may confirm a remote JOIN; combat and native consent retain their local gates. */
	private static boolean recruitmentCompatible(MemberSnapshot leader, MemberSnapshot member)
	{
		return member.ref().kind() == MemberKind.PHANTOM && member.ref().characterObjectId() > 0 && !member.dead() && member.hpPercent() >= 50 && leader.instanceId() == member.instanceId();
	}

	private static double distance(MemberSnapshot leader, MemberSnapshot member)
	{
		return Math.hypot((long) leader.x() - member.x(), (long) leader.y() - member.y()) + Math.abs((long) leader.z() - member.z());
	}

	public static boolean support(MemberSnapshot member)
	{
		return member.mpPercent() >= 15 && member.capabilities().stream().anyMatch(capability -> SUPPORT.contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.readyNow() && capability.actionSkillId() > 0 && capability.actionSkillLevel() > 0 && !"SELF".equals(capability.targetScope()));
	}

	public static boolean supportPresent(MemberSnapshot member)
	{
		return !member.dead() && member.mpPercent() >= 15 && member.capabilities().stream().anyMatch(capability -> SUPPORT.contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.actionSkillId() > 0 && capability.actionSkillLevel() > 0 && !"SELF".equals(capability.targetScope()));
	}

	public static boolean damageDealer(MemberSnapshot member) { return damage(member) && !prefersSupport(member); }
	public static boolean guardianPresent(MemberSnapshot member)
	{ return !member.dead() && member.capabilities().stream().anyMatch(capability -> "combat.tank".equals(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.actionSkillId() > 0 && capability.actionSkillLevel() > 0); }
	public static boolean spoiler(MemberSnapshot member)
	{
		return !member.dead() && Set.of("profession.spoil", "profession.sweep").stream().allMatch(key -> member.capabilities().stream()
			.anyMatch(capability -> key.equals(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.actionSkillId() > 0 && capability.actionSkillLevel() > 0));
	}
	public static boolean nativeParticipant(MemberSnapshot member)
	{ return member != null && !member.dead() && (damage(member) || supportPresent(member) || guardianPresent(member) || spoiler(member)); }
	/** A learned healer retains its separate native support owner; no forecasted Heal or double-counted DPS. */
	public static boolean healingSupport(MemberSnapshot member)
	{ return !guardianPresent(member) && member.capabilities().stream().anyMatch(capability -> "combat.heal".equals(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.actionSkillId() > 0 && !"SELF".equals(capability.targetScope())); }
	public static boolean farmContributor(MemberSnapshot member) { return !member.dead() && !healingSupport(member) && damage(member); }
	public static java.util.Optional<MemberRef> farmPuller(List<MemberSnapshot> members)
	{
		return members.stream().filter(PhantomAutonomousFarmParty::farmContributor)
			.sorted(Comparator.comparing((MemberSnapshot member) -> !guardian(member)).thenComparingLong(member -> member.ref().profileId())).map(MemberSnapshot::ref).findFirst();
	}

	public static boolean guardian(MemberSnapshot member)
	{
		return member.capabilities().stream().anyMatch(capability -> "combat.tank".equals(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.readyNow() && capability.actionSkillId() > 0 && capability.actionSkillLevel() > 0);
	}

	public static boolean damage(MemberSnapshot member)
	{
		return member.capabilities().stream().anyMatch(capability -> DAMAGE.contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.readyNow());
	}

	private static int score(MemberSnapshot member, Set<String> keys)
	{
		return member.capabilities().stream().filter(capability -> keys.contains(capability.capabilityKey()) && capability.intrinsic() && capability.learned() && capability.readyNow()).mapToInt(capability -> capability.rank()).max().orElse(0);
	}

	private static boolean prefersSupport(MemberSnapshot member)
	{
		return support(member) && score(member, SUPPORT) > score(member, DAMAGE);
	}

	private static String damageRole(MemberSnapshot member)
	{
		return member.capabilities().stream().anyMatch(capability -> capability.capabilityKey().startsWith("combat.ranged_") && capability.intrinsic() && capability.learned() && capability.readyNow()) ? "damage.ranged" : "damage.melee";
	}

	private static String supportRole(MemberSnapshot member)
	{
		if (member.capabilities().stream().anyMatch(capability -> "combat.heal".equals(capability.capabilityKey()) && capability.intrinsic() && capability.learned())) { return "support.healer"; }
		if (member.capabilities().stream().anyMatch(capability -> "combat.recharge".equals(capability.capabilityKey()) && capability.intrinsic() && capability.learned())) { return "support.recharge"; }
		return "support.enhancement";
	}
}

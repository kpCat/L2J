/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.ConflictLifecycle;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.ConflictObservation;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionService.ConflictSnapshot;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConflictPort.Gate;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConflictPort.Outcome;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConversationFacts.Fact;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConversationFacts.FactType;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ActiveNegotiation;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.AgreementReceipt;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.AgreementStatus;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.Alternative;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ArbitrationEvidence;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ClaimReceipt;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.CausalPerceptionReceipt;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.FarmingState;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.NegotiationStage;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ResourceKey;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.SemanticAct;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingPersistencePort.StoredState;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionChannel;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;

/**
 * Lazy, worker-free farming claim index and bilateral persistence protocol.
 * It consumes exact Goal 021 facts and never executes acquisition or social
 * language actions itself.
 */
public final class PhantomFarmingService implements PhantomFarmingConflictPort.Evaluator, PhantomFarmingConversationFacts
{
	public static final String CANDIDATE_KEY = "candidate.farming.conflict";
	public static final String ADVANCE_ACTION = "farming.conflict.advance";
	private static final Set<Method> CLAIM_METHODS = Set.of(Method.DEATH_DROP, Method.SPOIL_SWEEP, Method.MANOR_CROP, Method.QUEST_COLLECTION);

	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED
	}

	public enum AdvanceStatus
	{
		PROGRESSED,
		IDEMPOTENT,
		RETRY,
		STALE,
		FAILED
	}

	public enum FaultPoint
	{
		AFTER_OFFER,
		AFTER_RESPONSE,
		AFTER_FIRST_FINAL,
		BEFORE_SOCIAL,
		AFTER_FIRST_TERMINAL,
		BEFORE_TERMINAL_SOCIAL
	}

	private enum BindingState
	{
		EXACT,
		MOVED,
		COMPLETED,
		RELEASED,
		AUTHORITY_DRIFT,
		UNKNOWN
	}

	@FunctionalInterface
	public interface AcquisitionFacts
	{
		Optional<ConflictSnapshot> current(long profileId);

		default ConflictObservation observe(long profileId)
		{
			return current(profileId).map(snapshot -> new ConflictObservation(ConflictLifecycle.CURRENT, profileId, snapshot.goalId(), snapshot.goalRevision(), snapshot.source().sourceId(), snapshot)).orElseGet(() -> ConflictObservation.unavailable(profileId));
		}
	}

	@FunctionalInterface
	public interface PartyFacts
	{
		boolean sameParty(long firstProfileId, long secondProfileId);
	}

	public interface SocialFacts
	{
		SocialEvidence evidence(long ownerProfileId, long counterpartProfileId, long minute);

		boolean record(long ownerProfileId, long counterpartProfileId, String eventKey, String eventId, String evidenceHash, long minute);
	}

	@FunctionalInterface
	public interface FaultInjector
	{
		FaultInjector NONE = point ->
		{
		};

		void at(FaultPoint point);
	}

	public record SocialEvidence(int persistence, int escalation, int cooperation, String authorityHash)
	{
		public SocialEvidence
		{
			authorityHash = PhantomFarmingModel.hash(authorityHash, "Social evidence hash");
			if ((persistence < -3000) || (persistence > 3000) || (escalation < -3000) || (escalation > 3000) || (cooperation < -3000) || (cooperation > 3000))
			{
				throw new IllegalArgumentException("Social farming evidence is outside bounds.");
			}
		}
	}

	public record AdvanceResult(AdvanceStatus status, String reasonKey, String agreementId)
	{
		public AdvanceResult
		{
			Objects.requireNonNull(status);
			reasonKey = Objects.requireNonNull(reasonKey);
			agreementId = Objects.requireNonNullElse(agreementId, "");
		}
	}

	public record Snapshot(State state, String policyHash, int activeClaims, int resourceBuckets, int operationClaims, long claimsRequested, long claimsExpired, long claimsStale, long conflicts, long negotiationsStarted, long negotiationsResolved, long negotiationsExpired, long shareActs, long waitActs, long moveActs, long refuseActs, long escalateActs, long finalized, long fulfilled, long broken, long gatesAllow, long gatesShare, long gatesNegotiate, long gatesWait, long gatesMove, long gatesStale, long switchRequests, long perceptionUnavailable, long optimisticConflicts, long socialSuccess, long socialFailure, long exactPeerLoads, long reconciliationOperations, long socialRetries, int maximumBucketSize, int maximumActiveNegotiations, int maximumPayloadBytes)
	{
	}

	private final PhantomFarmingPolicy _policy;
	private final PhantomFarmingPersistencePort _store;
	private final AcquisitionFacts _acquisition;
	private final PhantomTopologyService _topology;
	private final PartyFacts _party;
	private final SocialFacts _social;
	private final int _capacity;
	private final LongSupplier _clock;
	private final FaultInjector _faults;
	private final ConcurrentHashMap<Long, Boolean> _operationClaims = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, StoredState> _cache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, RuntimeClaim> _claimsByProfile = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ResourceKey, ConcurrentSkipListMap<Long, RuntimeClaim>> _claimsByResource = new ConcurrentHashMap<>();
	private final Metrics _metrics = new Metrics();
	private volatile State _state = State.NEW;

	public PhantomFarmingService(PhantomFarmingPolicy policy, PhantomFarmingPersistencePort store, PhantomAcquisitionService acquisition, PhantomTopologyService topology, PhantomPartyCoordinator party, PhantomSocialService social, int capacity)
	{
		this(policy, store, new AcquisitionFacts()
		{
			@Override
			public Optional<ConflictSnapshot> current(long profileId)
			{
				return acquisition.conflictSnapshot(profileId);
			}

			@Override
			public ConflictObservation observe(long profileId)
			{
				return acquisition.conflictObservation(profileId);
			}
		}, topology, productionPartyFacts(party), new Goal018SocialFacts(social), capacity, () -> System.currentTimeMillis() / 60000L, FaultInjector.NONE);
	}

	public PhantomFarmingService(PhantomFarmingPolicy policy, PhantomFarmingPersistencePort store, AcquisitionFacts acquisition, PhantomTopologyService topology, PartyFacts party, SocialFacts social, int capacity, LongSupplier clock, FaultInjector faults)
	{
		_policy = Objects.requireNonNull(policy);
		_store = Objects.requireNonNull(store);
		_acquisition = Objects.requireNonNull(acquisition);
		_topology = Objects.requireNonNull(topology);
		_party = Objects.requireNonNull(party);
		_social = Objects.requireNonNull(social);
		if ((capacity < 1) || (capacity > 10000))
		{
			throw new IllegalArgumentException("Farming claim capacity is outside scheduled profile bounds.");
		}
		_capacity = capacity;
		_clock = Objects.requireNonNull(clock);
		_faults = Objects.requireNonNull(faults);
	}

	public synchronized boolean start()
	{
		if (_state != State.NEW)
		{
			return false;
		}
		_state = State.RUNNING;
		return true;
	}

	public synchronized boolean beginStop()
	{
		if (_state == State.STOPPED)
		{
			return false;
		}
		_state = State.STOPPING;
		return true;
	}

	public synchronized boolean finishStop()
	{
		if (_state == State.RUNNING)
		{
			beginStop();
		}
		if ((_state != State.STOPPING) || !_operationClaims.isEmpty())
		{
			return false;
		}
		_claimsByProfile.clear();
		_claimsByResource.clear();
		_cache.clear();
		_state = State.STOPPED;
		return true;
	}

	public boolean hasWork(long profileId)
	{
		final ConflictObservation observation = _acquisition.observe(profileId);
		final ConflictSnapshot snapshot = observation.lifecycle() == ConflictLifecycle.CURRENT ? observation.snapshot() : null;
		if (snapshot == null)
		{
			final StoredState persisted = load(profileId);
			return _claimsByProfile.containsKey(profileId) || ((persisted != null) && ((persisted.state().active() != null) || ((persisted.state().latest() != null) && liveStatus(persisted.state().latest().status()))));
		}
		final Gate gate = evaluate(profileId, snapshot);
		return (gate.outcome() == Outcome.NEGOTIATE) || (gate.outcome() == Outcome.STALE);
	}

	public AdvanceResult advance(long profileId)
	{
		if ((_state != State.RUNNING) || (profileId <= 0))
		{
			return new AdvanceResult(AdvanceStatus.STALE, "farming.service.unavailable", "");
		}
		_metrics.claimsRequested.increment();
		final long minute = now();
		reconcilePersisted(profileId, minute);
		final ConflictSnapshot snapshot = _acquisition.current(profileId).orElse(null);
		if (snapshot == null)
		{
			reconcilePersisted(profileId, minute);
			release(profileId);
			return new AdvanceResult(AdvanceStatus.STALE, "farming.acquisition.missing", "");
		}
		final DerivedResource derived = derive(snapshot);
		if (derived == null)
		{
			reconcilePersisted(profileId, minute);
			invalidatePersistedActive(profileId, minute);
			_metrics.claimsStale.increment();
			release(profileId);
			return new AdvanceResult(AdvanceStatus.STALE, "farming.resource.stale", "");
		}
		try (OperationClaim ignored = claimOne(profileId))
		{
			if (ignored == null)
			{
				return new AdvanceResult(AdvanceStatus.RETRY, "farming.operation.busy", "");
			}
			final AdvanceResult refreshed = refresh(profileId, snapshot, derived, minute);
			if (refreshed.status() == AdvanceStatus.FAILED)
			{
				return refreshed;
			}
		}
		final long counterpart = counterpart(profileId, derived.key(), minute);
		if (counterpart == 0)
		{
			return new AdvanceResult(AdvanceStatus.PROGRESSED, "farming.claim.active", "");
		}
		try (PairClaim ignored = claimPair(profileId, counterpart))
		{
			if (ignored == null)
			{
				return new AdvanceResult(AdvanceStatus.RETRY, "farming.pair.busy", "");
			}
			return negotiate(profileId, counterpart, derived.key(), minute);
		}
		catch (RuntimeException exception)
		{
			_metrics.optimisticConflicts.increment();
			return new AdvanceResult(AdvanceStatus.RETRY, "farming.persistence.conflict", "");
		}
	}

	@Override
	public Gate evaluate(long profileId, ConflictSnapshot snapshot)
	{
		if ((_state != State.RUNNING) || (snapshot == null) || (profileId != snapshot.profileId()))
		{
			return gate(Outcome.STALE, "farming.gate.stale", "");
		}
		final long minute = now();
		final DerivedResource derived = derive(snapshot);
		if (derived == null)
		{
			reconcilePersisted(profileId, minute);
			invalidatePersistedActive(profileId, minute);
			return gate(Outcome.STALE, "farming.resource.stale", "");
		}
		reconcilePersisted(profileId, minute);
		final RuntimeClaim own = liveClaim(profileId, snapshot, derived.key(), minute);
		if (own == null)
		{
			return gate(Outcome.NEGOTIATE, "farming.claim.required", "");
		}
		final long counterpart = counterpart(profileId, derived.key(), minute);
		if (counterpart == 0)
		{
			return gate(Outcome.ALLOW, "farming.claim.uncontested", "");
		}
		reconcilePair(profileId, counterpart, derived.key(), minute);
		final StoredState ownState = load(profileId);
		final StoredState peerState = load(counterpart);
		final AgreementReceipt receipt = currentAgreement(ownState, peerState, profileId, counterpart, derived.key(), minute);
		if (receipt == null)
		{
			return gate(Outcome.NEGOTIATE, "farming.conflict.negotiation_required", "");
		}
		final Outcome outcome = receipt.outcomeFor(profileId);
		if (outcome == Outcome.MOVE)
		{
			_metrics.switchRequests.increment();
		}
		return gate(outcome, receipt.reasonKey(), receipt.agreementId());
	}

	@Override
	public List<Fact> latest(long profileId)
	{
		reconcilePersisted(profileId, now());
		final ConflictSnapshot snapshot = _acquisition.current(profileId).orElse(null);
		final StoredState stored = _cache.get(profileId);
		if ((snapshot == null) || (stored == null) || (stored.state().claim() == null))
		{
			return List.of();
		}
		final DerivedResource derived = derive(snapshot);
		final ClaimReceipt claim = stored.state().claim();
		if ((derived == null) || !claim.resource().equals(derived.key()) || !claim.exactGoal(snapshot.goalId(), snapshot.goalRevision(), snapshot.source().sourceId()) || (claim.leaseExpiryMinute() <= now()))
		{
			return List.of();
		}
		final List<Fact> facts = new ArrayList<>();
		facts.add(new Fact(FactType.FARMING_CLAIM_STATUS, 0, null, "active", "farming.claim.current"));
		facts.add(new Fact(FactType.FARMING_REMAINING, 0, snapshot.remainingAmount(), "", "acquisition.current"));
		for (Alternative alternative : claim.alternatives().stream().limit(1).toList())
		{
			facts.add(new Fact(FactType.FARMING_ALTERNATIVE, 0, (long) alternative.score(), alternative.sourceId(), "acquisition.ranked"));
		}
		final AgreementReceipt candidate = stored.state().latest();
		final long counterpart = candidate == null ? 0 : candidate.counterpart(profileId);
		final AgreementReceipt agreement = (counterpart <= 0) ? null : currentAgreement(stored, load(counterpart), profileId, counterpart, derived.key(), now());
		if (agreement != null)
		{
			facts.add(new Fact(FactType.FARMING_CONFLICT, 0, null, agreement.resource().hash(), agreement.reasonKey()));
			facts.add(new Fact(FactType.FARMING_CONFLICT, counterpart, null, "", agreement.reasonKey()));
			final SemanticAct act = agreement.acts().getFirst();
			facts.add(new Fact(act == SemanticAct.ESCALATE ? FactType.FARMING_ESCALATION : FactType.FARMING_NEGOTIATION_ACT, 0, null, act.name(), agreement.reasonKey()));
			facts.add(new Fact(FactType.FARMING_AGREEMENT, counterpart, null, agreement.status().name(), agreement.reasonKey()));
			final ConflictSnapshot peer = _acquisition.current(counterpart).orElse(null);
			if ((peer != null) && exact(peer, agreement, counterpart))
			{
				facts.add(new Fact(FactType.FARMING_REMAINING, counterpart, peer.remainingAmount(), "", "acquisition.counterpart.current"));
			}
		}
		return List.copyOf(facts.stream().limit(8).toList());
	}

	private DerivedResource derive(ConflictSnapshot snapshot)
	{
		if (!snapshot.authorityCurrent() || !CLAIM_METHODS.contains(snapshot.source().method()))
		{
			return null;
		}
		final var query = _topology.query();
		if (!snapshot.authorityHashes().topology().equals(query.snapshot().canonicalHash()))
		{
			return null;
		}
		final long generation = query.snapshot().generation();
		if (_topology.findProfile(snapshot.profileId()).filter(profile -> profile.resolved() && (profile.topologyGeneration() == generation)).isEmpty())
		{
			_metrics.perceptionUnavailable.increment();
			return null;
		}
		final PhantomTopologyNode node = query.findNode(snapshot.source().topologyNodeId()).orElse(null);
		final PhantomTopologyAnchor anchor = query.findAnchor(snapshot.source().anchorId()).orElse(null);
		if ((node == null) || (anchor == null) || !anchor.nodeId().equals(node.id()) || (snapshot.source().npcId() <= 0))
		{
			return null;
		}
		final ResourceKey key = node.kind() == PhantomTopologyNodeKind.ROOM ? ResourceKey.room(node.id()) : ResourceKey.mobGroup(node.id(), anchor.id(), snapshot.source().npcId());
		final String authorityHash = PhantomFarmingModel.sha256(_policy.hash(), snapshot.evidenceHash(), snapshot.authorityHashes(), key.stableKey(), generation);
		return new DerivedResource(key, generation, query.snapshot().canonicalHash(), authorityHash);
	}

	private AdvanceResult refresh(long profileId, ConflictSnapshot snapshot, DerivedResource derived, long minute)
	{
		final StoredState current = load(profileId);
		if ((current != null) && !current.state().policyHash().equals(_policy.hash()))
		{
			return new AdvanceResult(AdvanceStatus.FAILED, "farming.policy.stale", "");
		}
		final ClaimReceipt previous = current == null ? null : current.state().claim();
		final boolean sameIdentity = (previous != null) && previous.resource().equals(derived.key()) && previous.exactGoal(snapshot.goalId(), snapshot.goalRevision(), snapshot.source().sourceId());
		final long claimedMinute = sameIdentity ? previous.claimedMinute() : minute;
		final List<Alternative> alternatives = snapshot.alternatives().stream().limit(_policy.limits().maximumAlternatives()).map(value -> new Alternative(value.sourceId(), value.method(), value.score())).toList();
		final ClaimReceipt claim = new ClaimReceipt(derived.key(), snapshot.goalId(), snapshot.goalRevision(), snapshot.source().sourceId(), snapshot.targetItemId(), snapshot.requiredAmount(), snapshot.progress(), snapshot.remainingAmount(), snapshot.goalPriority(), snapshot.acquisitionRowVersion(), snapshot.evidenceHash(), derived.authorityHash(), derived.generation(), claimedMinute, Math.addExact(minute, _policy.limits().claimLeaseMinutes()), alternatives, snapshot.switchFeasible() && !alternatives.isEmpty());
		if (!sameIdentity && !_claimsByProfile.containsKey(profileId) && (_claimsByProfile.size() >= _capacity))
		{
			return new AdvanceResult(AdvanceStatus.FAILED, "farming.claim.capacity", "");
		}
		final FarmingState base = current == null ? FarmingState.empty(_policy.hash(), derived.authorityHash(), minute) : current.state();
		if (sameIdentity && sameClaimFacts(previous, claim) && (previous.leaseExpiryMinute() > (minute + 1)))
		{
			installRuntime(profileId, previous);
			return new AdvanceResult(AdvanceStatus.IDEMPOTENT, "farming.claim.refresh_idempotent", "");
		}
		if (!sameIdentity)
		{
			release(profileId);
		}
		final boolean proposalEvidenceUnchanged = sameIdentity && sameClaimFacts(previous, claim);
		final FarmingState next = new FarmingState(claim, proposalEvidenceUnchanged ? base.active() : null, base.history(), _policy.hash(), derived.authorityHash(), minute);
		final StoredState saved = save(profileId, current == null ? -1 : current.rowVersion(), next);
		installRuntime(profileId, saved.state().claim());
		return new AdvanceResult(AdvanceStatus.PROGRESSED, sameIdentity ? "farming.claim.refreshed" : "farming.claim.created", "");
	}

	private static boolean sameClaimFacts(ClaimReceipt first, ClaimReceipt second)
	{
		return first.resource().equals(second.resource()) && (first.goalId() == second.goalId()) && (first.goalRevision() == second.goalRevision()) && first.sourceId().equals(second.sourceId()) && (first.requiredAmount() == second.requiredAmount()) && (first.progress() == second.progress()) && (first.acquisitionRowVersion() == second.acquisitionRowVersion()) && first.acquisitionEvidenceHash().equals(second.acquisitionEvidenceHash()) && first.authorityHash().equals(second.authorityHash()) && first.alternatives().equals(second.alternatives()) && (first.switchFeasible() == second.switchFeasible());
	}

	private void installRuntime(long profileId, ClaimReceipt claim)
	{
		final RuntimeClaim replacement = new RuntimeClaim(profileId, claim);
		final RuntimeClaim previous = _claimsByProfile.put(profileId, replacement);
		if ((previous != null) && !previous.receipt().resource().equals(claim.resource()))
		{
			removeFromBucket(previous);
		}
		final ConcurrentSkipListMap<Long, RuntimeClaim> bucket = _claimsByResource.computeIfAbsent(claim.resource(), _ -> new ConcurrentSkipListMap<>());
		if ((bucket.size() >= _policy.limits().maximumClaimants()) && !bucket.containsKey(profileId))
		{
			_claimsByProfile.remove(profileId, replacement);
			throw new IllegalStateException("Farming resource bucket capacity reached.");
		}
		bucket.put(profileId, replacement);
		_metrics.maximumBucketSize.accumulateAndGet(bucket.size(), Math::max);
	}

	private RuntimeClaim liveClaim(long profileId, ConflictSnapshot snapshot, ResourceKey resource, long minute)
	{
		final RuntimeClaim runtime = _claimsByProfile.get(profileId);
		if ((runtime == null) || (runtime.receipt().leaseExpiryMinute() <= minute))
		{
			if (runtime != null)
			{
				_metrics.claimsExpired.increment();
				release(profileId);
			}
			return null;
		}
		final ClaimReceipt claim = runtime.receipt();
		if (!claim.resource().equals(resource) || !claim.exactGoal(snapshot.goalId(), snapshot.goalRevision(), snapshot.source().sourceId()) || !claim.acquisitionEvidenceHash().equals(snapshot.evidenceHash()))
		{
			_metrics.claimsStale.increment();
			release(profileId);
			return null;
		}
		return runtime;
	}

	private long counterpart(long profileId, ResourceKey resource, long minute)
	{
		final long persisted = persistedCounterpart(profileId, resource, minute);
		if (persisted > 0)
		{
			return persisted;
		}
		final ConcurrentSkipListMap<Long, RuntimeClaim> bucket = _claimsByResource.get(resource);
		if ((bucket == null) || (bucket.size() < 2))
		{
			return 0;
		}
		final Set<Long> perceptible = _topology.perceptibleProfiles(profileId, PhantomPerceptionChannel.LOCAL_CHAT, _policy.limits().perceptionLimit()).stream().map(profile -> profile.profileId()).collect(java.util.stream.Collectors.toSet());
		for (Map.Entry<Long, RuntimeClaim> entry : bucket.entrySet())
		{
			if ((entry.getKey() == profileId) || !perceptible.contains(entry.getKey()))
			{
				continue;
			}
			final ConflictSnapshot peer = _acquisition.current(entry.getKey()).orElse(null);
			if ((peer != null) && (liveClaim(entry.getKey(), peer, resource, minute) != null))
			{
				_metrics.conflicts.increment();
				return entry.getKey();
			}
		}
		return 0;
	}

	private long persistedCounterpart(long profileId, ResourceKey resource, long minute)
	{
		final StoredState own = load(profileId);
		if (own == null)
		{
			return 0;
		}
		long counterpart = 0;
		final ActiveNegotiation active = own.state().active();
		if ((active != null) && active.resource().equals(resource))
		{
			counterpart = active.lowerProfileId() == profileId ? active.higherProfileId() : active.higherProfileId() == profileId ? active.lowerProfileId() : 0;
		}
		AgreementReceipt receipt = null;
		if (counterpart == 0)
		{
			receipt = own.state().latest();
			if ((receipt != null) && receipt.resource().equals(resource))
			{
				counterpart = receipt.counterpart(profileId);
			}
		}
		if (counterpart <= 0)
		{
			return 0;
		}
		_metrics.exactPeerLoads.increment();
		final StoredState peer = load(counterpart);
		if (peer == null)
		{
			return counterpart;
		}
		if ((receipt != null) && terminalStatus(receipt.status()))
		{
			final AgreementReceipt peerReceipt = latestPairAgreement(peer.state(), counterpart, profileId, resource);
			if (receipt.exactPair(peerReceipt))
			{
				return 0;
			}
		}
		rehydrateClaim(profileId, own, resource, minute);
		rehydrateClaim(counterpart, peer, resource, minute);
		return counterpart;
	}

	private boolean rehydrateClaim(long profileId, StoredState stored, ResourceKey resource, long minute)
	{
		final ConflictObservation observation = _acquisition.observe(profileId);
		final ConflictSnapshot snapshot = observation.lifecycle() == ConflictLifecycle.CURRENT ? observation.snapshot() : null;
		final DerivedResource derived = snapshot == null ? null : derive(snapshot);
		final ClaimReceipt previous = stored.state().claim();
		if ((snapshot == null) || (derived == null) || !resource.equals(derived.key()) || (previous == null) || !previous.resource().equals(resource) || !previous.exactGoal(snapshot.goalId(), snapshot.goalRevision(), snapshot.source().sourceId()))
		{
			return false;
		}
		final List<Alternative> alternatives = snapshot.alternatives().stream().limit(_policy.limits().maximumAlternatives()).map(value -> new Alternative(value.sourceId(), value.method(), value.score())).toList();
		final ClaimReceipt current = new ClaimReceipt(resource, snapshot.goalId(), snapshot.goalRevision(), snapshot.source().sourceId(), snapshot.targetItemId(), snapshot.requiredAmount(), snapshot.progress(), snapshot.remainingAmount(), snapshot.goalPriority(), snapshot.acquisitionRowVersion(), snapshot.evidenceHash(), derived.authorityHash(), derived.generation(), previous.claimedMinute(), Math.addExact(minute, _policy.limits().claimLeaseMinutes()), alternatives, snapshot.switchFeasible() && !alternatives.isEmpty());
		installRuntime(profileId, current);
		return true;
	}

	private CausalPerceptionReceipt capturePerception(long lowerProfileId, long higherProfileId, long minute)
	{
		final var query = _topology.query();
		final long generation = query.snapshot().generation();
		final ProfileTopologySnapshot lower = _topology.findProfile(lowerProfileId).filter(profile -> profile.resolved() && (profile.topologyGeneration() == generation)).orElse(null);
		final ProfileTopologySnapshot higher = _topology.findProfile(higherProfileId).filter(profile -> profile.resolved() && (profile.topologyGeneration() == generation)).orElse(null);
		if ((lower == null) || (higher == null) || _topology.perceptibleProfiles(lowerProfileId, PhantomPerceptionChannel.LOCAL_CHAT, _policy.limits().perceptionLimit()).stream().noneMatch(profile -> profile.profileId() == higherProfileId))
		{
			_metrics.perceptionUnavailable.increment();
			return null;
		}
		final long expiry = Math.addExact(minute, _policy.limits().negotiationTtlMinutes());
		final String evidenceHash = PhantomFarmingModel.sha256("farming.causal.perception", lowerProfileId, higherProfileId, generation, query.snapshot().canonicalHash(), lower.nodeId(), lower.sequence(), higher.nodeId(), higher.sequence(), PhantomPerceptionChannel.LOCAL_CHAT, minute, expiry);
		return new CausalPerceptionReceipt(lowerProfileId, higherProfileId, generation, query.snapshot().canonicalHash(), lower.nodeId(), lower.sequence(), higher.nodeId(), higher.sequence(), PhantomPerceptionChannel.LOCAL_CHAT, minute, expiry, evidenceHash, true);
	}

	private boolean validPerception(CausalPerceptionReceipt perception, long lowerProfileId, long higherProfileId, long minute)
	{
		if (!perception.trusted() || (perception.lowerProfileId() != lowerProfileId) || (perception.higherProfileId() != higherProfileId) || (perception.channel() != PhantomPerceptionChannel.LOCAL_CHAT) || (perception.expiryMinute() <= minute))
		{
			return false;
		}
		final var snapshot = _topology.query().snapshot();
		if ((snapshot.generation() != perception.topologyGeneration()) || !snapshot.canonicalHash().equals(perception.topologyHash()))
		{
			return false;
		}
		final String expected = PhantomFarmingModel.sha256("farming.causal.perception", lowerProfileId, higherProfileId, perception.topologyGeneration(), perception.topologyHash(), perception.lowerNodeId(), perception.lowerProfileSequence(), perception.higherNodeId(), perception.higherProfileSequence(), perception.channel(), perception.observedMinute(), perception.expiryMinute());
		return expected.equals(perception.evidenceHash());
	}

	private void release(long profileId)
	{
		final RuntimeClaim removed = _claimsByProfile.remove(profileId);
		if (removed != null)
		{
			removeFromBucket(removed);
		}
	}

	private void removeFromBucket(RuntimeClaim claim)
	{
		final ConcurrentSkipListMap<Long, RuntimeClaim> bucket = _claimsByResource.get(claim.receipt().resource());
		if (bucket != null)
		{
			bucket.remove(claim.profileId(), claim);
			if (bucket.isEmpty())
			{
				_claimsByResource.remove(claim.receipt().resource(), bucket);
			}
		}
	}

	private StoredState load(long profileId)
	{
		final StoredState cached = _cache.get(profileId);
		if (cached != null)
		{
			return cached;
		}
		final StoredState loaded = _store.load(profileId).orElse(null);
		if (loaded != null)
		{
			final StoredState raced = _cache.putIfAbsent(profileId, loaded);
			return raced == null ? loaded : raced;
		}
		return null;
	}

	private StoredState save(long profileId, long expectedRowVersion, FarmingState state)
	{
		final StoredState saved = _store.save(profileId, expectedRowVersion, state);
		_cache.put(profileId, saved);
		return saved;
	}

	private AdvanceResult negotiate(long callerProfileId, long counterpartProfileId, ResourceKey resource, long minute)
	{
		final long lowerProfileId = Math.min(callerProfileId, counterpartProfileId);
		final long higherProfileId = Math.max(callerProfileId, counterpartProfileId);
		final ConflictSnapshot lowerSnapshot = _acquisition.current(lowerProfileId).orElse(null);
		final ConflictSnapshot higherSnapshot = _acquisition.current(higherProfileId).orElse(null);
		final DerivedResource lowerResource = lowerSnapshot == null ? null : derive(lowerSnapshot);
		final DerivedResource higherResource = higherSnapshot == null ? null : derive(higherSnapshot);
		if ((lowerResource == null) || (higherResource == null) || !resource.equals(lowerResource.key()) || !resource.equals(higherResource.key()))
		{
			return new AdvanceResult(AdvanceStatus.STALE, "farming.pair.authority_stale", "");
		}
		final StoredState lower = load(lowerProfileId);
		final StoredState higher = load(higherProfileId);
		if (!exactClaim(lower, lowerSnapshot, resource, minute) || !exactClaim(higher, higherSnapshot, resource, minute))
		{
			return new AdvanceResult(AdvanceStatus.STALE, "farming.pair.claim_stale", "");
		}
		final AgreementReceipt previousLower = latestPairAgreement(lower.state(), lowerProfileId, higherProfileId, resource);
		final AgreementReceipt previousHigher = latestPairAgreement(higher.state(), higherProfileId, lowerProfileId, resource);
		final AgreementReceipt existingLower = exactLiveAgreement(previousLower, lowerSnapshot, higherSnapshot, minute) ? previousLower : null;
		final AgreementReceipt existingHigher = exactLiveAgreement(previousHigher, lowerSnapshot, higherSnapshot, minute) ? previousHigher : null;
		if ((existingLower != null) || (existingHigher != null))
		{
			final AgreementReceipt canonical = existingLower == null ? existingHigher : existingLower;
			if ((existingLower != null) && (existingHigher != null) && existingLower.exactPair(existingHigher))
			{
				retryAgreementSocial(canonical, minute, canonical.status() == AgreementStatus.SHARED && _party.sameParty(lowerProfileId, higherProfileId));
				return new AdvanceResult(AdvanceStatus.IDEMPOTENT, "farming.agreement.final_idempotent", canonical.agreementId());
			}
			return mirrorFinal(lower, higher, canonical, minute);
		}
		if (pairCoolingDown(previousLower, minute) || pairCoolingDown(previousHigher, minute))
		{
			return new AdvanceResult(AdvanceStatus.RETRY, "farming.negotiation.cooldown", "");
		}
		final boolean sameParty = _party.sameParty(lowerProfileId, higherProfileId);
		final ActiveNegotiation lowerActive = lower.state().active();
		final ActiveNegotiation higherActive = higher.state().active();
		final ActiveNegotiation canonicalActive = lowerActive != null ? lowerActive : higherActive;
		if ((canonicalActive != null) && (canonicalActive.expiryMinute() > minute))
		{
			final Draft currentEvidence = draft(lowerSnapshot, higherSnapshot, lower.state().claim(), higher.state().claim(), lowerResource, canonicalActive.perception(), canonicalActive.createdMinute(), false);
			if (!validPerception(canonicalActive.perception(), lowerProfileId, higherProfileId, minute) || !validActive(canonicalActive, currentEvidence, minute))
			{
				invalidateActivePair(lower, higher, minute);
				return new AdvanceResult(AdvanceStatus.STALE, "farming.negotiation.evidence_drift", "");
			}
		}
		final CausalPerceptionReceipt perception = canonicalActive == null ? capturePerception(lowerProfileId, higherProfileId, minute) : canonicalActive.perception();
		if ((perception == null) || !validPerception(perception, lowerProfileId, higherProfileId, minute))
		{
			return new AdvanceResult(AdvanceStatus.STALE, "farming.perception.causal_stale", "");
		}
		final Draft draft = !sameParty && (canonicalActive != null) ? resumeDraft(canonicalActive, lower.state().claim(), higher.state().claim()) : draft(lowerSnapshot, higherSnapshot, lower.state().claim(), higher.state().claim(), lowerResource, perception, minute, sameParty);
		if (sameParty)
		{
			return finalizePair(lower, higher, draft, minute, true);
		}
		if ((lowerActive == null) && (higherActive == null))
		{
			final ActiveNegotiation offer = active(draft, lowerSnapshot, higherSnapshot, NegotiationStage.OFFER);
			save(lowerProfileId, lower.rowVersion(), lower.state().withActive(offer, minute));
			_metrics.negotiationsStarted.increment();
			_metrics.maximumActiveNegotiations.accumulateAndGet(_metrics.activeNegotiations.incrementAndGet(), Math::max);
			_faults.at(FaultPoint.AFTER_OFFER);
			deliverActiveSocial(lowerProfileId, higherProfileId, "farming.agreement.offered", offer.agreementId(), offer.evidence().evidenceHash(), PhantomFarmingModel.SOCIAL_OFFER, minute);
			return new AdvanceResult(AdvanceStatus.PROGRESSED, "farming.negotiation.offer", offer.agreementId());
		}
		if ((lowerActive != null) && (higherActive == null) && validActive(lowerActive, draft, minute))
		{
			final ActiveNegotiation response = active(draft, lowerSnapshot, higherSnapshot, NegotiationStage.RESPONSE);
			save(higherProfileId, higher.rowVersion(), higher.state().withActive(response, minute));
			_faults.at(FaultPoint.AFTER_RESPONSE);
			final String responseEvent = draft.acts().contains(SemanticAct.REFUSE) ? "farming.agreement.refused" : "farming.agreement.accepted";
			deliverActiveSocial(higherProfileId, lowerProfileId, responseEvent, response.agreementId(), response.evidence().evidenceHash(), PhantomFarmingModel.SOCIAL_RESPONSE, minute);
			return new AdvanceResult(AdvanceStatus.PROGRESSED, "farming.negotiation.response", response.agreementId());
		}
		if ((lowerActive != null) && (higherActive != null) && validActive(lowerActive, draft, minute) && validActive(higherActive, draft, minute) && (lowerActive.stage() == NegotiationStage.OFFER) && (higherActive.stage() == NegotiationStage.RESPONSE))
		{
			return finalizePair(lower, higher, draft, minute, false);
		}
		if (((lowerActive != null) && (lowerActive.expiryMinute() <= minute)) || ((higherActive != null) && (higherActive.expiryMinute() <= minute)))
		{
			save(lowerProfileId, lower.rowVersion(), lower.state().withActive(null, minute));
			final StoredState refreshedHigher = load(higherProfileId);
			if ((refreshedHigher != null) && (refreshedHigher.state().active() != null))
			{
				save(higherProfileId, refreshedHigher.rowVersion(), refreshedHigher.state().withActive(null, minute));
			}
			_metrics.negotiationsExpired.increment();
			_metrics.activeNegotiations.updateAndGet(value -> Math.max(0, value - 1));
			return new AdvanceResult(AdvanceStatus.STALE, "farming.negotiation.expired", "");
		}
		return new AdvanceResult(AdvanceStatus.RETRY, "farming.negotiation.waiting", draft.agreementId());
	}

	private void invalidateActivePair(StoredState lower, StoredState higher, long minute)
	{
		if (lower.state().active() != null)
		{
			save(lower.profileId(), lower.rowVersion(), lower.state().withActive(null, minute));
		}
		final StoredState refreshedHigher = load(higher.profileId());
		if ((refreshedHigher != null) && (refreshedHigher.state().active() != null))
		{
			save(higher.profileId(), refreshedHigher.rowVersion(), refreshedHigher.state().withActive(null, minute));
		}
		_metrics.activeNegotiations.updateAndGet(value -> Math.max(0, value - 1));
	}

	private Draft draft(ConflictSnapshot lower, ConflictSnapshot higher, ClaimReceipt lowerClaim, ClaimReceipt higherClaim, DerivedResource resource, CausalPerceptionReceipt perception, long minute, boolean sameParty)
	{
		final SocialEvidence lowerSocial = _social.evidence(lower.profileId(), higher.profileId(), minute);
		final SocialEvidence higherSocial = _social.evidence(higher.profileId(), lower.profileId(), minute);
		final int lowerScore = score(lowerClaim, lowerSocial, minute);
		final int higherScore = score(higherClaim, higherSocial, minute);
		final long holder = lowerScore >= higherScore ? lower.profileId() : higher.profileId();
		final int cooperation = clamp((lowerSocial.cooperation() + higherSocial.cooperation()) / 2, -3000, 3000);
		final String evidenceHash = PhantomFarmingModel.sha256(resource.key().stableKey(), lower.evidenceHash(), higher.evidenceHash(), lowerScore, higherScore, lowerSocial.authorityHash(), higherSocial.authorityHash(), _policy.hash(), holder);
		final ArbitrationEvidence evidence = new ArbitrationEvidence(lower.profileId(), higher.profileId(), lowerScore, higherScore, lowerSocial.persistence(), higherSocial.persistence(), lowerSocial.escalation(), higherSocial.escalation(), cooperation, holder, resource.topologyHash(), resource.generation(), evidenceHash);
		final Outcome loserOutcome = (holder == lower.profileId() ? higherClaim : lowerClaim).switchFeasible() ? Outcome.MOVE : Outcome.WAIT;
		final AgreementStatus status;
		final List<SemanticAct> acts;
		final String reason;
		if (sameParty || (cooperation >= _policy.thresholds().shareCooperation()))
		{
			status = AgreementStatus.SHARED;
			acts = List.of(SemanticAct.SHARE);
			reason = sameParty ? "farming.conflict.same_party" : "farming.conflict.cooperative_share";
		}
		else if (Math.max(lowerSocial.escalation(), higherSocial.escalation()) >= _policy.thresholds().escalation())
		{
			status = AgreementStatus.ESCALATED;
			acts = List.of(SemanticAct.ESCALATE, loserOutcome == Outcome.MOVE ? SemanticAct.MOVE : SemanticAct.WAIT);
			reason = "farming.conflict.escalated";
		}
		else if (cooperation <= _policy.thresholds().refuseCooperation())
		{
			status = AgreementStatus.REFUSED;
			acts = List.of(SemanticAct.REFUSE, loserOutcome == Outcome.MOVE ? SemanticAct.MOVE : SemanticAct.WAIT);
			reason = "farming.conflict.refused";
		}
		else
		{
			status = loserOutcome == Outcome.MOVE ? AgreementStatus.MOVING : AgreementStatus.WAITING;
			acts = List.of(loserOutcome == Outcome.MOVE ? SemanticAct.MOVE : SemanticAct.WAIT);
			reason = loserOutcome == Outcome.MOVE ? "farming.conflict.move" : "farming.conflict.wait";
		}
		final long expiry = Math.addExact(minute, (status != AgreementStatus.SHARED) && (loserOutcome == Outcome.WAIT) ? _policy.limits().waitMinutes() : _policy.limits().negotiationTtlMinutes());
		final String agreementId = PhantomFarmingModel.sha256("farming.agreement", resource.key().stableKey(), lower.profileId(), higher.profileId(), lower.goalId(), lower.goalRevision(), lower.source().sourceId(), higher.goalId(), higher.goalRevision(), higher.source().sourceId(), evidenceHash);
		return new Draft(agreementId, evidence, perception, status, loserOutcome, acts, reason, minute, expiry);
	}

	private Draft resumeDraft(ActiveNegotiation active, ClaimReceipt lowerClaim, ClaimReceipt higherClaim)
	{
		final Outcome loserOutcome = (active.evidence().holderProfileId() == active.lowerProfileId() ? higherClaim : lowerClaim).switchFeasible() ? Outcome.MOVE : Outcome.WAIT;
		final AgreementStatus status;
		final List<SemanticAct> acts;
		final String reason;
		switch (active.proposalAct())
		{
			case SHARE ->
			{
				status = AgreementStatus.SHARED;
				acts = List.of(SemanticAct.SHARE);
				reason = "farming.conflict.cooperative_share";
			}
			case ESCALATE ->
			{
				status = AgreementStatus.ESCALATED;
				acts = List.of(SemanticAct.ESCALATE, loserOutcome == Outcome.MOVE ? SemanticAct.MOVE : SemanticAct.WAIT);
				reason = "farming.conflict.escalated";
			}
			case REFUSE ->
			{
				status = AgreementStatus.REFUSED;
				acts = List.of(SemanticAct.REFUSE, loserOutcome == Outcome.MOVE ? SemanticAct.MOVE : SemanticAct.WAIT);
				reason = "farming.conflict.refused";
			}
			case MOVE ->
			{
				status = AgreementStatus.MOVING;
				acts = List.of(SemanticAct.MOVE);
				reason = "farming.conflict.move";
			}
			case WAIT ->
			{
				status = AgreementStatus.WAITING;
				acts = List.of(SemanticAct.WAIT);
				reason = "farming.conflict.wait";
			}
			default -> throw new IllegalStateException("Unknown farming proposal act.");
		}
		return new Draft(active.agreementId(), active.evidence(), active.perception(), status, loserOutcome, acts, reason, active.createdMinute(), active.expiryMinute());
	}

	private int score(ClaimReceipt claim, SocialEvidence social, long minute)
	{
		final PhantomFarmingPolicy.Weights weights = _policy.weights();
		final long remainingRatio = (claim.remainingAmount() * 1000L) / claim.requiredAmount();
		final long progressRatio = (claim.progress() * 1000L) / claim.requiredAmount();
		final long age = Math.min(60, Math.max(0, minute - claim.claimedMinute()));
		final long value = ((long) claim.goalPriority() * weights.priority()) + (remainingRatio * weights.remaining()) + (progressRatio * weights.progress()) + (age * weights.claimAge()) + (claim.switchFeasible() ? weights.alternative() : 0) + ((long) social.persistence() * weights.persistence());
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
	}

	private ActiveNegotiation active(Draft draft, ConflictSnapshot lower, ConflictSnapshot higher, NegotiationStage stage)
	{
		return new ActiveNegotiation(draft.agreementId(), draftResource(draft, lower, higher), lower.profileId(), higher.profileId(), lower.goalId(), lower.goalRevision(), lower.source().sourceId(), lower.remainingAmount(), higher.goalId(), higher.goalRevision(), higher.source().sourceId(), higher.remainingAmount(), 1, lower.profileId(), draft.acts().getFirst(), stage, draft.evidence(), draft.perception(), draft.createdMinute(), draft.expiryMinute(), 0);
	}

	private ResourceKey draftResource(Draft draft, ConflictSnapshot lower, ConflictSnapshot higher)
	{
		final RuntimeClaim claim = _claimsByProfile.get(lower.profileId());
		if ((claim == null) || !claim.receipt().sourceId().equals(lower.source().sourceId()))
		{
			throw new IllegalStateException("Farming lower claim changed during negotiation.");
		}
		final RuntimeClaim other = _claimsByProfile.get(higher.profileId());
		if ((other == null) || !claim.receipt().resource().equals(other.receipt().resource()))
		{
			throw new IllegalStateException("Farming pair resource changed during negotiation.");
		}
		return claim.receipt().resource();
	}

	private AdvanceResult finalizePair(StoredState lower, StoredState higher, Draft draft, long minute, boolean sameParty)
	{
		final ClaimReceipt lowerClaim = lower.state().claim();
		final ClaimReceipt higherClaim = higher.state().claim();
		final ConflictSnapshot lowerSnapshot = _acquisition.current(lower.profileId()).orElseThrow();
		final ConflictSnapshot higherSnapshot = _acquisition.current(higher.profileId()).orElseThrow();
		final String lowerAuthority = liveAuthority(lowerSnapshot, derive(lowerSnapshot));
		final String higherAuthority = liveAuthority(higherSnapshot, derive(higherSnapshot));
		final int lowerSocial = lower.state().active() == null ? 0 : lower.state().active().socialDeliveryMask();
		final int higherSocial = higher.state().active() == null ? 0 : higher.state().active().socialDeliveryMask();
		final AgreementReceipt lowerReceipt = new AgreementReceipt(draft.agreementId(), lowerClaim.resource(), lower.profileId(), higher.profileId(), draft.evidence().holderProfileId(), lowerClaim.goalId(), lowerClaim.goalRevision(), lowerClaim.sourceId(), lowerAuthority, lowerClaim.remainingAmount(), higherClaim.goalId(), higherClaim.goalRevision(), higherClaim.sourceId(), higherAuthority, higherClaim.remainingAmount(), draft.status(), draft.loserOutcome(), draft.acts(), draft.reasonKey(), draft.evidence().evidenceHash(), draft.perception(), draft.createdMinute(), draft.expiryMinute(), false, lowerSocial);
		final AgreementReceipt higherReceipt = new AgreementReceipt(draft.agreementId(), lowerClaim.resource(), lower.profileId(), higher.profileId(), draft.evidence().holderProfileId(), lowerClaim.goalId(), lowerClaim.goalRevision(), lowerClaim.sourceId(), lowerAuthority, lowerClaim.remainingAmount(), higherClaim.goalId(), higherClaim.goalRevision(), higherClaim.sourceId(), higherAuthority, higherClaim.remainingAmount(), draft.status(), draft.loserOutcome(), draft.acts(), draft.reasonKey(), draft.evidence().evidenceHash(), draft.perception(), draft.createdMinute(), draft.expiryMinute(), false, higherSocial);
		save(lower.profileId(), lower.rowVersion(), lower.state().withAgreement(lowerReceipt, minute));
		_faults.at(FaultPoint.AFTER_FIRST_FINAL);
		final StoredState refreshedHigher = load(higher.profileId());
		save(higher.profileId(), refreshedHigher.rowVersion(), refreshedHigher.state().withAgreement(higherReceipt, minute));
		_metrics.activeNegotiations.updateAndGet(value -> Math.max(0, value - (sameParty ? 0 : 1)));
		recordResolvedMetrics(lowerReceipt);
		_faults.at(FaultPoint.BEFORE_SOCIAL);
		retryAgreementSocial(lowerReceipt, minute, sameParty);
		return new AdvanceResult(AdvanceStatus.PROGRESSED, lowerReceipt.reasonKey(), lowerReceipt.agreementId());
	}

	private AdvanceResult mirrorFinal(StoredState lower, StoredState higher, AgreementReceipt canonical, long minute)
	{
		StoredState currentLower = lower;
		StoredState currentHigher = higher;
		final AgreementReceipt lowerReceipt = latestPairAgreement(currentLower.state(), lower.profileId(), higher.profileId(), canonical.resource());
		final AgreementReceipt higherReceipt = latestPairAgreement(currentHigher.state(), higher.profileId(), lower.profileId(), canonical.resource());
		if (lowerReceipt == null)
		{
			final AgreementReceipt copy = copyAgreement(canonical, canonical.status(), canonical.perception(), 0, canonical.effectApplied());
			currentLower = save(lower.profileId(), lower.rowVersion(), lower.state().withAgreement(copy, minute));
		}
		if (higherReceipt == null)
		{
			currentHigher = load(higher.profileId());
			final AgreementReceipt copy = copyAgreement(canonical, canonical.status(), canonical.perception(), 0, canonical.effectApplied());
			save(higher.profileId(), currentHigher.rowVersion(), currentHigher.state().withAgreement(copy, minute));
		}
		_metrics.activeNegotiations.updateAndGet(value -> Math.max(0, value - 1));
		recordResolvedMetrics(canonical);
		_faults.at(FaultPoint.BEFORE_SOCIAL);
		retryAgreementSocial(canonical, minute, canonical.status() == AgreementStatus.SHARED && _party.sameParty(lower.profileId(), higher.profileId()));
		return new AdvanceResult(AdvanceStatus.PROGRESSED, "farming.agreement.mirrored", canonical.agreementId());
	}

	private AgreementReceipt reconcilePersisted(long profileId, long minute)
	{
		final StoredState own = load(profileId);
		if (own == null)
		{
			return null;
		}
		final ActiveNegotiation active = own.state().active();
		final AgreementReceipt receipt = own.state().latest();
		final long counterpart;
		final ResourceKey resource;
		if (active != null)
		{
			counterpart = active.lowerProfileId() == profileId ? active.higherProfileId() : active.higherProfileId() == profileId ? active.lowerProfileId() : 0;
			resource = active.resource();
		}
		else if (receipt != null)
		{
			counterpart = receipt.counterpart(profileId);
			resource = receipt.resource();
		}
		else
		{
			return null;
		}
		return counterpart <= 0 ? null : reconcilePair(profileId, counterpart, resource, minute);
	}

	private void invalidatePersistedActive(long profileId, long minute)
	{
		final StoredState own = load(profileId);
		final ActiveNegotiation active = own == null ? null : own.state().active();
		if (active == null)
		{
			return;
		}
		final long counterpart = active.lowerProfileId() == profileId ? active.higherProfileId() : active.higherProfileId() == profileId ? active.lowerProfileId() : 0;
		if (counterpart <= 0)
		{
			return;
		}
		try (PairClaim ignored = claimPair(profileId, counterpart))
		{
			if (ignored == null)
			{
				return;
			}
			final StoredState lower = load(Math.min(profileId, counterpart));
			final StoredState higher = load(Math.max(profileId, counterpart));
			if ((lower != null) && (lower.state().active() != null) && lower.state().active().agreementId().equals(active.agreementId()))
			{
				save(lower.profileId(), lower.rowVersion(), lower.state().withActive(null, minute));
			}
			final StoredState refreshedHigher = load(Math.max(profileId, counterpart));
			if ((refreshedHigher != null) && (refreshedHigher.state().active() != null) && refreshedHigher.state().active().agreementId().equals(active.agreementId()))
			{
				save(refreshedHigher.profileId(), refreshedHigher.rowVersion(), refreshedHigher.state().withActive(null, minute));
			}
			_metrics.activeNegotiations.updateAndGet(value -> Math.max(0, value - 1));
		}
	}

	private AgreementReceipt reconcilePair(long firstProfileId, long secondProfileId, ResourceKey resource, long minute)
	{
		try (PairClaim ignored = claimPair(firstProfileId, secondProfileId))
		{
			if (ignored == null)
			{
				return null;
			}
			final StoredState lower = load(Math.min(firstProfileId, secondProfileId));
			final StoredState higher = load(Math.max(firstProfileId, secondProfileId));
			return (lower == null) || (higher == null) ? null : reconcileAgreementLocked(lower, higher, resource, minute);
		}
		catch (RuntimeException exception)
		{
			_metrics.optimisticConflicts.increment();
			return null;
		}
	}

	private AgreementReceipt reconcileAgreementLocked(StoredState lower, StoredState higher, ResourceKey resource, long minute)
	{
		_metrics.reconciliationOperations.increment();
		AgreementReceipt lowerReceipt = latestPairAgreement(lower.state(), lower.profileId(), higher.profileId(), resource);
		AgreementReceipt higherReceipt = latestPairAgreement(higher.state(), higher.profileId(), lower.profileId(), resource);
		if ((lowerReceipt == null) && (higherReceipt == null))
		{
			return null;
		}
		AgreementReceipt canonical = lowerReceipt == null ? higherReceipt : lowerReceipt;
		if ((lowerReceipt != null) && (higherReceipt != null) && !lowerReceipt.sameIdentity(higherReceipt))
		{
			return null;
		}
		if ((higherReceipt != null) && terminalStatus(higherReceipt.status()))
		{
			canonical = higherReceipt;
		}
		if (!canonical.perception().trusted())
		{
			final BindingState lowerBinding = bindingState(canonical, lower.profileId());
			final BindingState higherBinding = bindingState(canonical, higher.profileId());
			final CausalPerceptionReceipt revalidated = (lowerBinding == BindingState.EXACT) && (higherBinding == BindingState.EXACT) ? capturePerception(lower.profileId(), higher.profileId(), minute) : null;
			if (revalidated != null)
			{
				final ConflictSnapshot currentLower = _acquisition.current(lower.profileId()).orElseThrow();
				final ConflictSnapshot currentHigher = _acquisition.current(higher.profileId()).orElseThrow();
				canonical = canonical.withBinding(revalidated, liveAuthority(currentLower, derive(currentLower)), liveAuthority(currentHigher, derive(currentHigher)));
				lowerReceipt = copyAgreement(canonical, canonical.status(), revalidated, lowerReceipt == null ? 0 : lowerReceipt.socialDeliveryMask(), canonical.effectApplied());
				higherReceipt = copyAgreement(canonical, canonical.status(), revalidated, higherReceipt == null ? 0 : higherReceipt.socialDeliveryMask(), canonical.effectApplied());
				lower = save(lower.profileId(), lower.rowVersion(), lower.state().withAgreement(lowerReceipt, minute));
				higher = save(higher.profileId(), higher.rowVersion(), higher.state().withAgreement(higherReceipt, minute));
			}
		}
		final AgreementStatus desired = desiredTerminal(canonical, minute);
		if (desired == null)
		{
			if ((lowerReceipt != null) && (higherReceipt != null) && lowerReceipt.exactPair(higherReceipt))
			{
				retryAgreementSocial(canonical, minute, canonical.status() == AgreementStatus.SHARED && _party.sameParty(lower.profileId(), higher.profileId()));
			}
			return canonical;
		}
		if ((lowerReceipt != null) && (higherReceipt != null) && (lowerReceipt.status() == desired) && lowerReceipt.exactPair(higherReceipt))
		{
			retryAgreementSocial(canonical, minute, canonical.acts().contains(SemanticAct.SHARE) && _party.sameParty(lower.profileId(), higher.profileId()));
			return lowerReceipt;
		}
		final CausalPerceptionReceipt perception = canonical.perception();
		final AgreementReceipt resolvedLower = copyAgreement(canonical, desired, perception, lowerReceipt == null ? 0 : lowerReceipt.socialDeliveryMask(), true);
		final AgreementReceipt resolvedHigher = copyAgreement(canonical, desired, perception, higherReceipt == null ? 0 : higherReceipt.socialDeliveryMask(), true);
		save(lower.profileId(), lower.rowVersion(), lower.state().withAgreement(resolvedLower, minute));
		_faults.at(FaultPoint.AFTER_FIRST_TERMINAL);
		final StoredState refreshedHigher = load(higher.profileId());
		save(higher.profileId(), refreshedHigher.rowVersion(), refreshedHigher.state().withAgreement(resolvedHigher, minute));
		release(lower.profileId());
		release(higher.profileId());
		if (desired == AgreementStatus.FULFILLED)
		{
			_metrics.fulfilled.increment();
		}
		else if (desired == AgreementStatus.BROKEN)
		{
			_metrics.broken.increment();
		}
		_faults.at(FaultPoint.BEFORE_TERMINAL_SOCIAL);
		retryAgreementSocial(resolvedLower, minute, resolvedLower.acts().contains(SemanticAct.SHARE) && _party.sameParty(lower.profileId(), higher.profileId()));
		return resolvedLower;
	}

	private AgreementStatus desiredTerminal(AgreementReceipt receipt, long minute)
	{
		if (terminalStatus(receipt.status()))
		{
			return receipt.status();
		}
		if (receipt.expiryMinute() <= minute)
		{
			return AgreementStatus.EXPIRED;
		}
		if (!validPerception(receipt.perception(), receipt.lowerProfileId(), receipt.higherProfileId(), minute))
		{
			return AgreementStatus.STALE;
		}
		final BindingState lower = bindingState(receipt, receipt.lowerProfileId());
		final BindingState higher = bindingState(receipt, receipt.higherProfileId());
		if ((lower == BindingState.UNKNOWN) || (higher == BindingState.UNKNOWN))
		{
			return null;
		}
		if ((lower == BindingState.AUTHORITY_DRIFT) || (higher == BindingState.AUTHORITY_DRIFT))
		{
			return AgreementStatus.STALE;
		}
		if ((lower == BindingState.EXACT) && (higher == BindingState.EXACT))
		{
			return terminalStatus(receipt.status()) ? receipt.status() : null;
		}
		if (receipt.status() == AgreementStatus.SHARED)
		{
			return normalEnd(lower) || normalEnd(higher) ? AgreementStatus.FULFILLED : AgreementStatus.STALE;
		}
		final BindingState holder = receipt.holderProfileId() == receipt.lowerProfileId() ? lower : higher;
		final BindingState loser = receipt.holderProfileId() == receipt.lowerProfileId() ? higher : lower;
		if ((receipt.loserOutcome() == Outcome.MOVE) && normalEnd(loser))
		{
			return AgreementStatus.FULFILLED;
		}
		if ((receipt.loserOutcome() == Outcome.WAIT) && normalEnd(holder))
		{
			return AgreementStatus.FULFILLED;
		}
		return normalEnd(holder) ? AgreementStatus.FULFILLED : AgreementStatus.STALE;
	}

	private BindingState bindingState(AgreementReceipt receipt, long profileId)
	{
		final ConflictObservation observation = _acquisition.observe(profileId);
		if (observation.lifecycle() == ConflictLifecycle.UNAVAILABLE)
		{
			return BindingState.UNKNOWN;
		}
		final long goalId = profileId == receipt.lowerProfileId() ? receipt.lowerGoalId() : receipt.higherGoalId();
		final long goalRevision = profileId == receipt.lowerProfileId() ? receipt.lowerGoalRevision() : receipt.higherGoalRevision();
		final String sourceId = profileId == receipt.lowerProfileId() ? receipt.lowerSourceId() : receipt.higherSourceId();
		if ((observation.goalId() != goalId) || (observation.goalRevision() != goalRevision))
		{
			return BindingState.AUTHORITY_DRIFT;
		}
		if (observation.lifecycle() == ConflictLifecycle.COMPLETED)
		{
			return BindingState.COMPLETED;
		}
		if (observation.lifecycle() == ConflictLifecycle.RELEASED)
		{
			return BindingState.RELEASED;
		}
		if ((observation.lifecycle() != ConflictLifecycle.CURRENT) || (observation.snapshot() == null))
		{
			return BindingState.AUTHORITY_DRIFT;
		}
		final DerivedResource derived = derive(observation.snapshot());
		if (derived == null)
		{
			return BindingState.AUTHORITY_DRIFT;
		}
		if (observation.snapshot().source().sourceId().equals(sourceId))
		{
			if (!derived.key().equals(receipt.resource()))
			{
				return BindingState.AUTHORITY_DRIFT;
			}
			final String authority = profileId == receipt.lowerProfileId() ? receipt.lowerAuthorityHash() : receipt.higherAuthorityHash();
			return !receipt.perception().trusted() || liveAuthority(observation.snapshot(), derived).equals(authority) ? BindingState.EXACT : BindingState.AUTHORITY_DRIFT;
		}
		return BindingState.MOVED;
	}

	private static boolean normalEnd(BindingState state)
	{
		return (state == BindingState.MOVED) || (state == BindingState.COMPLETED) || (state == BindingState.RELEASED);
	}

	private AgreementReceipt currentAgreement(StoredState ownState, StoredState peerState, long profileId, long counterpart, ResourceKey resource, long minute)
	{
		if ((ownState == null) || (peerState == null))
		{
			return null;
		}
		final AgreementReceipt own = latestPairAgreement(ownState.state(), profileId, counterpart, resource);
		final AgreementReceipt peer = latestPairAgreement(peerState.state(), counterpart, profileId, resource);
		if ((own == null) || !own.exactPair(peer) || (own.expiryMinute() <= minute) || !liveStatus(own.status()))
		{
			return null;
		}
		final ConflictSnapshot ownSnapshot = _acquisition.current(profileId).orElse(null);
		final ConflictSnapshot peerSnapshot = _acquisition.current(counterpart).orElse(null);
		if ((ownSnapshot == null) || (peerSnapshot == null) || !exact(ownSnapshot, own, profileId) || !exact(peerSnapshot, own, counterpart) || !validPerception(own.perception(), own.lowerProfileId(), own.higherProfileId(), minute))
		{
			return null;
		}
		final DerivedResource ownResource = derive(ownSnapshot);
		final DerivedResource peerResource = derive(peerSnapshot);
		final String ownAuthority = profileId == own.lowerProfileId() ? own.lowerAuthorityHash() : own.higherAuthorityHash();
		final String peerAuthority = counterpart == own.lowerProfileId() ? own.lowerAuthorityHash() : own.higherAuthorityHash();
		return (ownResource != null) && (peerResource != null) && resource.equals(ownResource.key()) && resource.equals(peerResource.key()) && liveAuthority(ownSnapshot, ownResource).equals(ownAuthority) && liveAuthority(peerSnapshot, peerResource).equals(peerAuthority) ? own : null;
	}

	private static boolean liveStatus(AgreementStatus status)
	{
		return Set.of(AgreementStatus.SHARED, AgreementStatus.WAITING, AgreementStatus.MOVING, AgreementStatus.REFUSED, AgreementStatus.ESCALATED).contains(status);
	}

	private static boolean terminalStatus(AgreementStatus status)
	{
		return Set.of(AgreementStatus.FULFILLED, AgreementStatus.BROKEN, AgreementStatus.EXPIRED, AgreementStatus.STALE).contains(status);
	}

	private static AgreementReceipt latestPairAgreement(FarmingState state, long ownerProfileId, long counterpart, ResourceKey resource)
	{
		for (int index = state.history().size() - 1; index >= 0; index--)
		{
			final AgreementReceipt receipt = state.history().get(index);
			if ((receipt.counterpart(ownerProfileId) == counterpart) && receipt.resource().equals(resource))
			{
				return receipt;
			}
		}
		return null;
	}

	private static boolean exact(ConflictSnapshot snapshot, AgreementReceipt receipt, long profileId)
	{
		if (profileId == receipt.lowerProfileId())
		{
			return (snapshot.goalId() == receipt.lowerGoalId()) && (snapshot.goalRevision() == receipt.lowerGoalRevision()) && snapshot.source().sourceId().equals(receipt.lowerSourceId());
		}
		if (profileId == receipt.higherProfileId())
		{
			return (snapshot.goalId() == receipt.higherGoalId()) && (snapshot.goalRevision() == receipt.higherGoalRevision()) && snapshot.source().sourceId().equals(receipt.higherSourceId());
		}
		return false;
	}

	private static boolean exactClaim(StoredState stored, ConflictSnapshot snapshot, ResourceKey resource, long minute)
	{
		if ((stored == null) || (stored.state().claim() == null))
		{
			return false;
		}
		final ClaimReceipt claim = stored.state().claim();
		return claim.resource().equals(resource) && claim.exactGoal(snapshot.goalId(), snapshot.goalRevision(), snapshot.source().sourceId()) && claim.acquisitionEvidenceHash().equals(snapshot.evidenceHash()) && (claim.leaseExpiryMinute() > minute);
	}

	private boolean validActive(ActiveNegotiation active, Draft draft, long minute)
	{
		return (active.round() <= _policy.limits().maximumRounds()) && active.agreementId().equals(draft.agreementId()) && active.evidence().evidenceHash().equals(draft.evidence().evidenceHash()) && (active.expiryMinute() > minute);
	}

	private boolean exactLiveAgreement(AgreementReceipt receipt, ConflictSnapshot lower, ConflictSnapshot higher, long minute)
	{
		if ((receipt == null) || !liveStatus(receipt.status()) || (receipt.expiryMinute() <= minute) || !exact(lower, receipt, lower.profileId()) || !exact(higher, receipt, higher.profileId()) || !validPerception(receipt.perception(), receipt.lowerProfileId(), receipt.higherProfileId(), minute))
		{
			return false;
		}
		final DerivedResource lowerResource = derive(lower);
		final DerivedResource higherResource = derive(higher);
		return (lowerResource != null) && (higherResource != null) && receipt.resource().equals(lowerResource.key()) && receipt.resource().equals(higherResource.key()) && liveAuthority(lower, lowerResource).equals(receipt.lowerAuthorityHash()) && liveAuthority(higher, higherResource).equals(receipt.higherAuthorityHash());
	}

	private boolean pairCoolingDown(AgreementReceipt receipt, long minute)
	{
		return (receipt != null) && !liveStatus(receipt.status()) && (minute < Math.addExact(receipt.createdMinute(), _policy.limits().pairCooldownMinutes()));
	}

	private static AgreementReceipt copyStatus(AgreementReceipt receipt, AgreementStatus status, boolean applied)
	{
		return receipt.withStatus(status, applied);
	}

	private static AgreementReceipt copyAgreement(AgreementReceipt receipt, AgreementStatus status, CausalPerceptionReceipt perception, int socialDeliveryMask, boolean applied)
	{
		return new AgreementReceipt(receipt.agreementId(), receipt.resource(), receipt.lowerProfileId(), receipt.higherProfileId(), receipt.holderProfileId(), receipt.lowerGoalId(), receipt.lowerGoalRevision(), receipt.lowerSourceId(), receipt.lowerAuthorityHash(), receipt.lowerRemaining(), receipt.higherGoalId(), receipt.higherGoalRevision(), receipt.higherSourceId(), receipt.higherAuthorityHash(), receipt.higherRemaining(), status, receipt.loserOutcome(), receipt.acts(), receipt.reasonKey(), receipt.evidenceHash(), perception, receipt.createdMinute(), receipt.expiryMinute(), applied, socialDeliveryMask);
	}

	private String liveAuthority(ConflictSnapshot snapshot, DerivedResource resource)
	{
		if (resource == null)
		{
			throw new IllegalArgumentException("Missing farming authority resource.");
		}
		final var hashes = snapshot.authorityHashes();
		return PhantomFarmingModel.sha256("farming.live.authority", _policy.hash(), hashes.catalog(), hashes.knowledge(), hashes.topology(), hashes.progression(), hashes.background(), resource.key().stableKey(), resource.generation(), resource.topologyHash());
	}

	private void recordResolvedMetrics(AgreementReceipt receipt)
	{
		_metrics.negotiationsResolved.increment();
		_metrics.finalized.increment();
		for (SemanticAct act : receipt.acts())
		{
			switch (act)
			{
				case SHARE -> _metrics.shareActs.increment();
				case WAIT -> _metrics.waitActs.increment();
				case MOVE -> _metrics.moveActs.increment();
				case REFUSE -> _metrics.refuseActs.increment();
				case ESCALATE -> _metrics.escalateActs.increment();
			}
		}
	}

	private void retryAgreementSocial(AgreementReceipt receipt, long minute, boolean sameParty)
	{
		if (!sameParty)
		{
			deliverAgreementSocial(receipt.lowerProfileId(), receipt.higherProfileId(), "farming.agreement.offered", receipt.agreementId(), receipt.evidenceHash(), PhantomFarmingModel.SOCIAL_OFFER, minute);
			final String response = receipt.acts().contains(SemanticAct.REFUSE) ? "farming.agreement.refused" : "farming.agreement.accepted";
			deliverAgreementSocial(receipt.higherProfileId(), receipt.lowerProfileId(), response, receipt.agreementId(), receipt.evidenceHash(), PhantomFarmingModel.SOCIAL_RESPONSE, minute);
		}
		if (receipt.acts().contains(SemanticAct.ESCALATE))
		{
			deliverAgreementSocial(receipt.lowerProfileId(), receipt.higherProfileId(), "farming.conflict.escalated", receipt.agreementId(), receipt.evidenceHash(), PhantomFarmingModel.SOCIAL_ESCALATION, minute);
			deliverAgreementSocial(receipt.higherProfileId(), receipt.lowerProfileId(), "farming.conflict.escalated", receipt.agreementId(), receipt.evidenceHash(), PhantomFarmingModel.SOCIAL_ESCALATION, minute);
		}
		if ((receipt.status() == AgreementStatus.FULFILLED) || (receipt.status() == AgreementStatus.BROKEN))
		{
			final String eventKey = receipt.status() == AgreementStatus.BROKEN ? "agreement.broken" : "agreement.fulfilled";
			deliverAgreementSocial(receipt.lowerProfileId(), receipt.higherProfileId(), eventKey, receipt.agreementId(), receipt.evidenceHash(), PhantomFarmingModel.SOCIAL_TERMINAL, minute);
			deliverAgreementSocial(receipt.higherProfileId(), receipt.lowerProfileId(), eventKey, receipt.agreementId(), receipt.evidenceHash(), PhantomFarmingModel.SOCIAL_TERMINAL, minute);
		}
	}

	private void deliverActiveSocial(long ownerProfileId, long counterpartProfileId, String eventKey, String agreementId, String evidenceHash, int delivery, long minute)
	{
		final StoredState owner = load(ownerProfileId);
		final ActiveNegotiation active = owner == null ? null : owner.state().active();
		if ((active == null) || !active.agreementId().equals(agreementId) || ((active.socialDeliveryMask() & delivery) != 0))
		{
			return;
		}
		_metrics.socialRetries.increment();
		final String eventId = PhantomFarmingModel.sha256("farming.social", ownerProfileId, counterpartProfileId, eventKey, agreementId);
		if (_social.record(ownerProfileId, counterpartProfileId, eventKey, eventId, evidenceHash, minute))
		{
			save(ownerProfileId, owner.rowVersion(), owner.state().withActive(active.withSocialDelivery(delivery), minute));
			_metrics.socialSuccess.increment();
		}
		else
		{
			_metrics.socialFailure.increment();
		}
	}

	private void deliverAgreementSocial(long ownerProfileId, long counterpartProfileId, String eventKey, String agreementId, String evidenceHash, int delivery, long minute)
	{
		final StoredState owner = load(ownerProfileId);
		final AgreementReceipt receipt = owner == null ? null : owner.state().agreement(agreementId);
		if ((receipt == null) || ((receipt.socialDeliveryMask() & delivery) != 0))
		{
			return;
		}
		_metrics.socialRetries.increment();
		final String eventId = PhantomFarmingModel.sha256("farming.social", ownerProfileId, counterpartProfileId, eventKey, agreementId);
		if (_social.record(ownerProfileId, counterpartProfileId, eventKey, eventId, evidenceHash, minute))
		{
			save(ownerProfileId, owner.rowVersion(), owner.state().withAgreement(receipt.withSocialDelivery(delivery), minute));
			_metrics.socialSuccess.increment();
		}
		else
		{
			_metrics.socialFailure.increment();
		}
	}

	private Gate gate(Outcome outcome, String reasonKey, String agreementId)
	{
		switch (outcome)
		{
			case ALLOW -> _metrics.gatesAllow.increment();
			case SHARE -> _metrics.gatesShare.increment();
			case NEGOTIATE -> _metrics.gatesNegotiate.increment();
			case WAIT -> _metrics.gatesWait.increment();
			case MOVE -> _metrics.gatesMove.increment();
			case STALE -> _metrics.gatesStale.increment();
		}
		return new Gate(outcome, reasonKey, agreementId);
	}

	public Snapshot snapshot()
	{
		return new Snapshot(_state, _policy.hash(), _claimsByProfile.size(), _claimsByResource.size(), _operationClaims.size(), _metrics.claimsRequested.sum(), _metrics.claimsExpired.sum(), _metrics.claimsStale.sum(), _metrics.conflicts.sum(), _metrics.negotiationsStarted.sum(), _metrics.negotiationsResolved.sum(), _metrics.negotiationsExpired.sum(), _metrics.shareActs.sum(), _metrics.waitActs.sum(), _metrics.moveActs.sum(), _metrics.refuseActs.sum(), _metrics.escalateActs.sum(), _metrics.finalized.sum(), _metrics.fulfilled.sum(), _metrics.broken.sum(), _metrics.gatesAllow.sum(), _metrics.gatesShare.sum(), _metrics.gatesNegotiate.sum(), _metrics.gatesWait.sum(), _metrics.gatesMove.sum(), _metrics.gatesStale.sum(), _metrics.switchRequests.sum(), _metrics.perceptionUnavailable.sum(), _metrics.optimisticConflicts.sum(), _metrics.socialSuccess.sum(), _metrics.socialFailure.sum(), _metrics.exactPeerLoads.sum(), _metrics.reconciliationOperations.sum(), _metrics.socialRetries.sum(), _metrics.maximumBucketSize.get(), _metrics.maximumActiveNegotiations.get(), new PhantomFarmingStateCodec().declaredWorstCaseBytes());
	}

	private long now()
	{
		final long value = _clock.getAsLong();
		if (value < 0)
		{
			throw new IllegalStateException("Farming logical clock returned a negative minute.");
		}
		return value;
	}

	private OperationClaim claimOne(long profileId)
	{
		return _operationClaims.putIfAbsent(profileId, Boolean.TRUE) == null ? new OperationClaim(profileId) : null;
	}

	private PairClaim claimPair(long firstProfileId, long secondProfileId)
	{
		final long lower = Math.min(firstProfileId, secondProfileId);
		final long higher = Math.max(firstProfileId, secondProfileId);
		if (_operationClaims.putIfAbsent(lower, Boolean.TRUE) != null)
		{
			return null;
		}
		if (_operationClaims.putIfAbsent(higher, Boolean.TRUE) != null)
		{
			_operationClaims.remove(lower, Boolean.TRUE);
			return null;
		}
		return new PairClaim(lower, higher);
	}

	private static PartyFacts productionPartyFacts(PhantomPartyCoordinator party)
	{
		Objects.requireNonNull(party);
		return (firstProfileId, secondProfileId) ->
		{
			final var first = party.claim(firstProfileId).orElse(null);
			final var second = party.claim(secondProfileId).orElse(null);
			if ((first == null) || (second == null) || !Set.of(StateStatus.LEADER, StateStatus.MEMBER).contains(first.state().status()) || !Set.of(StateStatus.LEADER, StateStatus.MEMBER).contains(second.state().status()))
			{
				return false;
			}
			if (!first.state().groupId().equals(second.state().groupId()) || (first.state().groupGeneration() != second.state().groupGeneration()) || (first.state().membershipRevision() != second.state().membershipRevision()) || !first.state().leaderManifestHash().equals(second.state().leaderManifestHash()))
			{
				return false;
			}
			return partyContains(first.state(), firstProfileId) && partyContains(first.state(), secondProfileId) && partyContains(second.state(), firstProfileId) && partyContains(second.state(), secondProfileId);
		};
	}

	private static boolean partyContains(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState state, long profileId)
	{
		return ((state.leader().kind() == MemberKind.PHANTOM) && (state.leader().profileId() == profileId)) || state.phantomMembers().stream().anyMatch(member -> member.profileId() == profileId);
	}

	private static int clamp(int value, int minimum, int maximum)
	{
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static final class Goal018SocialFacts implements SocialFacts
	{
		private final PhantomSocialService _service;

		private Goal018SocialFacts(PhantomSocialService service)
		{
			_service = Objects.requireNonNull(service);
		}

		@Override
		public SocialEvidence evidence(long ownerProfileId, long counterpartProfileId, long minute)
		{
			final SubjectRef subject = SubjectRef.phantom(counterpartProfileId);
			final var persistence = _service.modifier(ownerProfileId, subject, "goal.persistence", minute);
			final var escalation = _service.modifier(ownerProfileId, subject, "conflict.escalation", minute);
			final var social = _service.snapshot(ownerProfileId, subject, 0, minute);
			final int persistenceValue = persistence.available() ? persistence.value().deltaBasisPoints() : 0;
			final int escalationValue = escalation.available() ? escalation.value().deltaBasisPoints() : 0;
			int cooperation = 0;
			String socialAuthority = PhantomFarmingModel.sha256("social.unavailable", ownerProfileId, counterpartProfileId);
			if (social.available())
			{
				final Map<String, Integer> relationship = social.value().relationship().relationship();
				final Map<String, Integer> reputation = social.value().relationship().reputation();
				final int positive = relationship.getOrDefault("trust", 0) + relationship.getOrDefault("friendship", 0) + relationship.getOrDefault("respect", 0) + reputation.getOrDefault("reliability", 0) + reputation.getOrDefault("helpfulness", 0);
				final int negative = relationship.getOrDefault("anger", 0) + relationship.getOrDefault("rivalry", 0) + reputation.getOrDefault("hostility", 0);
				cooperation = clamp((positive - negative) / 4, -3000, 3000);
				socialAuthority = social.value().authorityHash();
			}
			final String authority = PhantomFarmingModel.sha256(persistenceValue, escalationValue, cooperation, persistence.available() ? persistence.value().authorityHash() : "none", escalation.available() ? escalation.value().authorityHash() : "none", socialAuthority);
			return new SocialEvidence(persistenceValue, escalationValue, cooperation, authority);
		}

		@Override
		public boolean record(long ownerProfileId, long counterpartProfileId, String eventKey, String eventId, String evidenceHash, long minute)
		{
			return _service.record(new SocialEvent(ownerProfileId, eventId, eventKey, SubjectRef.phantom(counterpartProfileId), minute, 1, evidenceHash)).durable();
		}
	}

	private final class OperationClaim implements AutoCloseable
	{
		private final long _profileId;

		private OperationClaim(long profileId)
		{
			_profileId = profileId;
		}

		@Override
		public void close()
		{
			_operationClaims.remove(_profileId, Boolean.TRUE);
		}
	}

	private final class PairClaim implements AutoCloseable
	{
		private final long _lower;
		private final long _higher;

		private PairClaim(long lower, long higher)
		{
			_lower = lower;
			_higher = higher;
		}

		@Override
		public void close()
		{
			_operationClaims.remove(_higher, Boolean.TRUE);
			_operationClaims.remove(_lower, Boolean.TRUE);
		}
	}

	private record DerivedResource(ResourceKey key, long generation, String topologyHash, String authorityHash)
	{
	}

	private record RuntimeClaim(long profileId, ClaimReceipt receipt)
	{
	}

	private record Draft(String agreementId, ArbitrationEvidence evidence, CausalPerceptionReceipt perception, AgreementStatus status, Outcome loserOutcome, List<SemanticAct> acts, String reasonKey, long createdMinute, long expiryMinute)
	{
		private Draft
		{
			acts = List.copyOf(acts);
		}
	}

	private static final class Metrics
	{
		private final LongAdder claimsRequested = new LongAdder();
		private final LongAdder claimsExpired = new LongAdder();
		private final LongAdder claimsStale = new LongAdder();
		private final LongAdder conflicts = new LongAdder();
		private final LongAdder negotiationsStarted = new LongAdder();
		private final LongAdder negotiationsResolved = new LongAdder();
		private final LongAdder negotiationsExpired = new LongAdder();
		private final LongAdder shareActs = new LongAdder();
		private final LongAdder waitActs = new LongAdder();
		private final LongAdder moveActs = new LongAdder();
		private final LongAdder refuseActs = new LongAdder();
		private final LongAdder escalateActs = new LongAdder();
		private final LongAdder finalized = new LongAdder();
		private final LongAdder fulfilled = new LongAdder();
		private final LongAdder broken = new LongAdder();
		private final LongAdder gatesAllow = new LongAdder();
		private final LongAdder gatesShare = new LongAdder();
		private final LongAdder gatesNegotiate = new LongAdder();
		private final LongAdder gatesWait = new LongAdder();
		private final LongAdder gatesMove = new LongAdder();
		private final LongAdder gatesStale = new LongAdder();
		private final LongAdder switchRequests = new LongAdder();
		private final LongAdder perceptionUnavailable = new LongAdder();
		private final LongAdder optimisticConflicts = new LongAdder();
		private final LongAdder socialSuccess = new LongAdder();
		private final LongAdder socialFailure = new LongAdder();
		private final LongAdder exactPeerLoads = new LongAdder();
		private final LongAdder reconciliationOperations = new LongAdder();
		private final LongAdder socialRetries = new LongAdder();
		private final AtomicInteger maximumBucketSize = new AtomicInteger();
		private final AtomicInteger activeNegotiations = new AtomicInteger();
		private final AtomicInteger maximumActiveNegotiations = new AtomicInteger();
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationContext;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator.ManagedInvitationDecision;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.MemberFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.EntryFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.BindingStability;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CandidateReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CandidateScore;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.InvitationStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PartyBindingReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PendingInvitationReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PartyReadiness;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Refusal;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Stage;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.SemanticFact;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.SemanticFactType;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Status;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftPersistencePort.StoredPreparation;

/**
 * Durable, decision-driven Rift preparation. READY_TO_ENTER is an observation;
 * this service owns no Rift entry, item mutation, teleport, room or combat API.
 */
public final class PhantomRiftService implements PhantomRiftConversationFacts
{
	public static final String GOAL_TYPE = "rift.prepare";
	private static final String ZERO_HASH = "0".repeat(64);
	private final PhantomRiftBackend _backend;
	private final PhantomRiftCatalog _catalog;
	private final PhantomRiftPolicy _policy;
	private final PhantomRiftReadinessService _readiness;
	private final PhantomRiftPersistencePort _store;
	private final PartyPort _party;
	private final LongSupplier _clock;
	private final PhantomRiftMetrics _metrics = new PhantomRiftMetrics();

	public PhantomRiftService(PhantomRiftBackend backend, PhantomRiftCatalog catalog, PhantomRiftPolicy policy, PhantomRiftReadinessService readiness, PhantomRiftPersistencePort store, PartyPort party, LongSupplier clock)
	{
		_backend = Objects.requireNonNull(backend);
		_catalog = Objects.requireNonNull(catalog);
		_policy = Objects.requireNonNull(policy);
		_readiness = Objects.requireNonNull(readiness);
		_store = Objects.requireNonNull(store);
		_party = Objects.requireNonNull(party);
		_clock = Objects.requireNonNull(clock);
	}

	public AdvanceResult advance(long leaderProfileId, long goalId, long goalRevision, int tierType)
	{
		if ((leaderProfileId <= 0) || (goalId <= 0) || (goalRevision < 0) || (tierType < 1) || (tierType > 6))
		{
			return new AdvanceResult(AdvanceOutcome.REPLAN, null, null, "rift.goal.invalid");
		}
		final Optional<StoredPreparation> loaded = _store.load(leaderProfileId);
		final StoredPreparation stored;
		if (loaded.isEmpty() || !sameGoal(loaded.get().preparation(), goalId, goalRevision, tierType))
		{
			final Preparation initial = new Preparation(leaderProfileId, goalId, goalRevision, tierType, Stage.DISCOVER_CONTENT, Status.STALE, ZERO_HASH, _catalog.catalogHash(), _policy.hash(), _backend.config().hash(), ZERO_HASH, "", null, 0, 0, 0, List.of(), ZERO_HASH, now());
			return saved(leaderProfileId, loaded.map(StoredPreparation::rowVersion).orElse(-1L), initial, null, "rift.content.discovered");
		}
		stored = loaded.get();
		if (stored.preparation().legacyUntrusted())
		{
			final Preparation legacy = stored.preparation();
			final Preparation replanned = new Preparation(legacy.leaderProfileId(), legacy.goalId(), legacy.goalRevision(), legacy.tierType(), Stage.DISCOVER_CONTENT, Status.STALE, ZERO_HASH, _catalog.catalogHash(), _policy.hash(), _backend.config().hash(), ZERO_HASH, "", null, 0, legacy.totalAttempts(), 0, activeRefusals(legacy.refusals()), ZERO_HASH, now(), null, null, null, false);
			_metrics.migrationReplan();
			return saved(stored, replanned, null, "rift.schema.v1_replanned");
		}
		return switch (stored.preparation().stage())
		{
			case DISCOVER_CONTENT -> discover(stored);
			case SNAPSHOT_ROSTER -> snapshot(stored);
			case EVALUATE_READINESS -> evaluate(stored);
			case SELECT_CANDIDATE -> select(stored);
			case ENSURE_PARTY_BINDING -> ensureBinding(stored);
			case REQUEST_INVITE -> requestInvite(stored);
			case OBSERVE_INVITE -> observeInvite(stored);
			case REQUEST_PARTY_ROUTE -> requestRoute(stored);
			case OBSERVE_ROUTE -> observeRoute(stored);
			case DECLARE_READY -> declareReady(stored);
		};
	}

	public Optional<StoredPreparation> load(long leaderProfileId)
	{
		return _store.load(leaderProfileId);
	}

	public PhantomRiftMetrics.Snapshot metrics()
	{
		return _metrics.snapshot();
	}

	public ManagedInvitationDecision evaluateManagedInvitation(ManagedInvitationContext context)
	{
		if ((context == null) || (context.leaderClaim() == null) || (context.inviteeClaim() == null) || (context.inviteeSnapshot() == null))
		{
			return ManagedInvitationDecision.DEFER;
		}
		final var operation = context.leaderClaim().operation();
		final var inviteeOperation = context.inviteeClaim().operation();
		if ((operation == null) || (operation.kind() != OperationKind.JOIN) || (operation.phase() != OperationPhase.CANONICAL_PENDING) || (inviteeOperation == null) || (inviteeOperation.kind() != OperationKind.JOIN) || (inviteeOperation.phase() != OperationPhase.CANONICAL_PENDING) || (operation.invitationSequence() != context.invitation().identity().sequence()) || (operation.leader().characterObjectId() != context.invitation().identity().requesterObjectId()) || (operation.member() == null) || (operation.member().characterObjectId() != context.invitation().identity().inviteeObjectId()) || (context.inviteeClaim().status() != StateStatus.INVITED_INBOUND))
		{
			return ManagedInvitationDecision.DEFER;
		}
		final Preparation preparation = _store.load(context.requesterProfileId()).map(StoredPreparation::preparation).orElse(null);
		if ((preparation == null) || preparation.legacyUntrusted() || (preparation.candidateReceipt() == null) || !preparation.candidateReceipt().candidate().equals(operation.member()) || (preparation.goalId() != operation.leaderGoalId()) || (preparation.goalRevision() != operation.leaderGoalRevision()) || !Set.of(Stage.REQUEST_INVITE, Stage.OBSERVE_INVITE).contains(preparation.stage()))
		{
			return ManagedInvitationDecision.DEFER;
		}
		if (context.inviteeSnapshot().dead())
		{
			return ManagedInvitationDecision.REFUSE;
		}
		if ((context.inviteeGoal() != null) && (context.inviteeGoal().status() == org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE) && context.inviteeGoal().goalType().startsWith("party.") && !"party.join".equals(context.inviteeGoal().goalType()))
		{
			return ManagedInvitationDecision.REFUSE;
		}
		final PartyReadiness readiness = _readiness.evaluate(context.requesterProfileId(), preparation.tierType());
		if (!sameSources(preparation, readiness) || readiness.roster().fullParty() || !preparation.candidateReceipt().vacancyKey().equals(recruitmentVacancy(readiness)))
		{
			return ManagedInvitationDecision.DEFER;
		}
		final var relationship = _backend.relationship(context.inviteeProfileId(), operation.leader());
		return relationship.available() && (relationship.modifierBasisPoints() < -1000) ? ManagedInvitationDecision.REFUSE : ManagedInvitationDecision.ACCEPT;
	}
	@Override
	public List<SemanticFact> latest(long profileId)
	{
		final Preparation preparation = _store.load(profileId).map(StoredPreparation::preparation).orElse(null);
		if ((preparation == null) || preparation.legacyUntrusted())
		{
			return List.of();
		}
		final PartyReadiness readiness = _readiness.evaluate(profileId, preparation.tierType());
		if (_party.requiresExactBinding() && !sameSources(preparation, readiness))
		{
			return _readiness.semanticFacts(readiness).stream().filter(fact -> fact.type() != SemanticFactType.RIFT_READY).toList();
		}
		final PhantomDomainRef objective = new PhantomDomainRef("rift.tier", Integer.toString(preparation.tierType()));
		final PartyBinding binding = _party.observeBinding(preparation.leaderProfileId(), preparation.goalId(), preparation.goalRevision(), objective, _policy.requireTier(preparation.tierType()).requirements(), readiness.roster());
		final boolean stable = !_party.requiresExactBinding() || sameBinding(preparation.partyBinding(), binding);
		final List<SemanticFact> facts = new ArrayList<>(_readiness.semanticFacts(readiness).stream().filter(fact -> stable || (fact.type() != SemanticFactType.RIFT_READY)).toList());
		if (stable && (preparation.invitationReceipt() != null) && (preparation.invitationReceipt().status() == InvitationStatus.PENDING) && (preparation.candidateReceipt() != null) && preparation.candidateReceipt().selectedRosterHash().equals(readiness.roster().evidenceHash()))
		{
			facts.removeIf(fact -> fact.type() == SemanticFactType.RIFT_PREP_STATUS);
			facts.add(new SemanticFact(SemanticFactType.RIFT_PREP_STATUS, Map.of("tier", Integer.toString(preparation.tierType()), "status", Status.INVITE_PENDING.name(), "partySize", Integer.toString(readiness.roster().members().size())), readiness.roster().evidenceHash()));
			facts.add(new SemanticFact(SemanticFactType.RIFT_INVITE_REQUEST, Map.of("tier", Integer.toString(preparation.tierType()), "vacancy", preparation.candidateReceipt().vacancyKey(), "candidateCharacterId", Integer.toString(preparation.candidateReceipt().candidate().characterObjectId()), "partySize", Integer.toString(readiness.roster().members().size())), readiness.roster().evidenceHash()));
		}
		final Refusal refusal = activeRefusals(preparation.refusals()).stream().reduce((left, right) -> right).orElse(null);
		if (stable && (refusal != null) && !readiness.roster().members().contains(refusal.candidate()))
		{
			facts.add(new SemanticFact(SemanticFactType.RIFT_INVITE_REFUSED, Map.of("candidateCharacterId", Integer.toString(refusal.candidate().characterObjectId()), "vacancy", refusal.vacancyKey(), "reasonKey", refusal.reasonKey()), readiness.roster().evidenceHash()));
		}
		return List.copyOf(facts);
	}

	private AdvanceResult discover(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		if (!_catalog.requireTier(current.tierType()).supported())
		{
			return saved(stored, copy(current, Stage.EVALUATE_READINESS, Status.BLOCKED, ZERO_HASH, ZERO_HASH, "", null, current.pendingInvitationSequence(), current.totalAttempts(), current.seatAttempts(), current.refusals(), ZERO_HASH), null, "rift.authority.unsupported");
		}
		return saved(stored, copy(current, Stage.SNAPSHOT_ROSTER, Status.STALE, ZERO_HASH, ZERO_HASH, "", null, 0, current.totalAttempts(), current.seatAttempts(), current.refusals(), ZERO_HASH), null, "rift.content.authoritative");
	}

	private AdvanceResult snapshot(StoredPreparation stored)
	{
		final PartyReadiness readiness = current(stored.preparation());
		return saved(stored, fromReadiness(stored.preparation(), readiness, Stage.EVALUATE_READINESS, null, 0, stored.preparation().totalAttempts(), stored.preparation().seatAttempts(), stored.preparation().refusals(), stored.preparation().routeHash()), readiness, "rift.roster.snapshotted");
	}

	private AdvanceResult evaluate(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		final Stage next;
		if (readiness.status() == Status.READY_TO_ENTER)
		{
			next = Stage.ENSURE_PARTY_BINDING;
		}
		else if (readiness.status() == Status.NEEDS_TRAVEL)
		{
			next = Stage.ENSURE_PARTY_BINDING;
		}
		else if ((readiness.status() == Status.NEEDS_PARTY) || (readiness.status() == Status.NEEDS_ROLE))
		{
			next = Stage.SELECT_CANDIDATE;
		}
		else
		{
			next = Stage.EVALUATE_READINESS;
		}
		final String vacancy = recruitmentVacancy(readiness);
		final int seatAttempts = vacancy.equals(current.missingVacancyKey()) ? current.seatAttempts() : 0;
		final Preparation replacement = fromReadiness(current, readiness, next, null, 0, current.totalAttempts(), seatAttempts, activeRefusals(current.refusals()), current.routeHash());
		if (replacement.equals(current))
		{
			return new AdvanceResult(AdvanceOutcome.RETRY, current, readiness, readiness.reasonKeys().getFirst());
		}
		return saved(stored, replacement, readiness, readiness.reasonKeys().getFirst());
	}

	private AdvanceResult select(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		if (readiness.roster().fullParty() || !readiness.roster().leader().equals(_backend.currentMember(current.leaderProfileId()).orElse(null)))
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), 0, activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.roster.changed");
		}
		final String vacancyKey = recruitmentVacancy(readiness);
		final RoleRequirement requirement = _policy.requireTier(current.tierType()).requirements().stream().filter(value -> value.vacancyKey().equals(vacancyKey)).findFirst().orElse(null);
		if ((requirement == null) || (current.totalAttempts() >= _policy.limits().maximumTotalAttempts()) || (current.seatAttempts() >= _policy.limits().maximumSeatAttempts()))
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.recruitment.exhausted");
		}
		final MemberFacts leader = _backend.memberFacts(readiness.roster().leader(), Set.of(_catalog.requireTier(current.tierType()).entry().itemId())).orElse(null);
		if (leader == null)
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.leader.snapshot.stale");
		}
		_metrics.candidateSearch();
		final List<Refusal> refusals = activeRefusals(current.refusals());
		final List<CandidateScore> candidates = _backend.nearbyCandidates(readiness.roster().leader(), Set.of(_catalog.requireTier(current.tierType()).entry().itemId()), _policy.limits().candidateRange(), _policy.limits().candidateLimit()).stream()
			.filter(candidate -> !readiness.roster().members().contains(candidate.member().ref()))
			.filter(candidate -> !candidate.inAnotherParty(readiness.roster().members()))
			.filter(candidate -> candidate.member().instanceId() == leader.member().instanceId())
			.filter(candidate -> refusals.stream().noneMatch(refusal -> refusal.candidate().equals(candidate.member().ref()) && refusal.vacancyKey().equals(vacancyKey)))
			.map(candidate -> _readiness.candidate(candidate, requirement, current.tierType(), leader.member().x(), leader.member().y(), leader.member().z(), _backend.relationship(current.leaderProfileId(), candidate.member().ref())).orElse(null))
			.filter(Objects::nonNull)
			.sorted(Comparator.comparingInt(CandidateScore::roleScore).reversed().thenComparing(Comparator.comparingInt(CandidateScore::readinessScore).reversed()).thenComparing(Comparator.comparingInt(CandidateScore::relationshipModifier).reversed()).thenComparingLong(CandidateScore::distanceSquared).thenComparing(CandidateScore::ordinaryRealPlayer).thenComparing(value -> value.member().stableKey()))
			.limit(PhantomRiftModel.MAX_CANDIDATES)
			.toList();
		if (candidates.isEmpty())
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), refusals, current.routeHash()), readiness, "rift.candidate.none");
		}
		final CandidateScore selected = candidates.getFirst();
		final CandidateReceipt receipt = new CandidateReceipt(vacancyKey, selected.member(), selected.evidenceHash(), readiness.roster().evidenceHash(), selected.relationshipEvidenceHash());
		return saved(stored, withCandidate(fromReadiness(current, readiness, Stage.ENSURE_PARTY_BINDING, selected.member(), 0, current.totalAttempts(), current.seatAttempts(), refusals, current.routeHash()), receipt), readiness, "rift.candidate.selected");
	}

	private AdvanceResult ensureBinding(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		final PhantomDomainRef objective = new PhantomDomainRef("rift.tier", Integer.toString(current.tierType()));
		final PartyBinding binding = _party.bind(current.leaderProfileId(), current.goalId(), current.goalRevision(), objective, _policy.requireTier(current.tierType()).requirements(), readiness.roster());
		if (!binding.stable() || !binding.leader().equals(readiness.roster().leader()) || !binding.rosterHash().equals(readiness.roster().evidenceHash()))
		{
			_metrics.bindingConflict();
			return saved(stored, clearReceipts(fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), ZERO_HASH)), readiness, binding.reasonKey().isBlank() ? "rift.binding.conflict" : binding.reasonKey());
		}
		final PartyBindingReceipt receipt = new PartyBindingReceipt(binding.groupId(), binding.groupGeneration(), binding.membershipRevision(), binding.leader(), binding.rosterHash(), binding.manifestHash(), BindingStability.STABLE);
		final Stage next = current.pendingCandidate() != null ? Stage.REQUEST_INVITE : readiness.status() == Status.NEEDS_TRAVEL ? Stage.REQUEST_PARTY_ROUTE : readiness.status() == Status.READY_TO_ENTER ? Stage.DECLARE_READY : Stage.EVALUATE_READINESS;
		return saved(stored, withBinding(fromReadiness(current, readiness, next, current.pendingCandidate(), current.pendingInvitationSequence(), current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), receipt), readiness, "rift.binding.stable");
	}
	private AdvanceResult requestInvite(StoredPreparation stored)
	{
		final StoredPreparation exact = _store.load(stored.profileId()).orElse(null);
		if ((exact == null) || (exact.rowVersion() != stored.rowVersion()) || !exact.preparation().equals(stored.preparation()))
		{
			_metrics.rosterStale();
			return new AdvanceResult(AdvanceOutcome.RETRY, exact == null ? stored.preparation() : exact.preparation(), null, "rift.preinvite.component_changed");
		}
		final Preparation current = exact.preparation();
		final PartyReadiness readiness = current(current);
		final CandidateReceipt selected = current.candidateReceipt();
		if ((current.pendingCandidate() == null) || (selected == null) || readiness.roster().fullParty() || !sameSources(current, readiness) || !selected.selectedRosterHash().equals(readiness.roster().evidenceHash()) || !selected.vacancyKey().equals(recruitmentVacancy(readiness)) || readiness.roster().members().contains(current.pendingCandidate()))
		{
			_metrics.sourceStale();
			return staleCandidate(exact, readiness, "rift.preinvite.source_or_roster_changed");
		}
		final PhantomDomainRef objective = new PhantomDomainRef("rift.tier", Integer.toString(current.tierType()));
		final PartyBinding binding = _party.observeBinding(current.leaderProfileId(), current.goalId(), current.goalRevision(), objective, _policy.requireTier(current.tierType()).requirements(), readiness.roster());
		if (!sameBinding(current.partyBinding(), binding) || !_party.candidateClaimAvailable(current.pendingCandidate()))
		{
			_metrics.bindingConflict();
			return staleCandidate(exact, readiness, "rift.preinvite.binding_or_claim_changed");
		}
		final RoleRequirement requirement = _policy.requireTier(current.tierType()).requirements().stream().filter(value -> value.vacancyKey().equals(selected.vacancyKey())).findFirst().orElse(null);
		final Set<Integer> itemIds = Set.of(_catalog.requireTier(current.tierType()).entry().itemId());
		final MemberFacts leader = _backend.memberFacts(readiness.roster().leader(), itemIds).orElse(null);
		final MemberFacts candidate = _backend.candidateFacts(readiness.roster().leader(), current.pendingCandidate(), itemIds, _policy.limits().candidateRange()).orElse(null);
		if ((requirement == null) || (leader == null) || (candidate == null) || candidate.member().dead() || candidate.inAnotherParty(readiness.roster().members()) || (candidate.member().instanceId() != leader.member().instanceId()) || activeRefusals(current.refusals()).stream().anyMatch(refusal -> refusal.candidate().equals(current.pendingCandidate()) && refusal.vacancyKey().equals(selected.vacancyKey())))
		{
			_metrics.candidateRejected();
			return staleCandidate(exact, readiness, "rift.preinvite.candidate_changed");
		}
		final CandidateScore refreshed = _readiness.candidate(candidate, requirement, current.tierType(), leader.member().x(), leader.member().y(), leader.member().z(), _backend.relationship(current.leaderProfileId(), current.pendingCandidate())).orElse(null);
		if ((refreshed == null) || !refreshed.evidenceHash().equals(selected.candidateEvidenceHash()) || !refreshed.relationshipEvidenceHash().equals(selected.relationshipEvidenceHash()))
		{
			_metrics.candidateRejected();
			return staleCandidate(exact, readiness, "rift.preinvite.candidate_evidence_changed");
		}
		final InviteObservation invite = _party.invite(current.leaderProfileId(), current.pendingCandidate(), readiness.roster().distribution());
		if (invite.status() == InviteStatus.REJECTED)
		{
			return refusal(exact, readiness, invite.reasonKey(), InvitationStatus.REJECTED, invite);
		}
		final InviteObservation exactInvite;
		if (invite.exactFor(readiness.roster().leader(), current.pendingCandidate()))
		{
			exactInvite = invite;
		}
		else if (!_party.requiresExactBinding() && (invite.sequence() > 0))
		{
			exactInvite = new InviteObservation(invite.status(), invite.sequence(), readiness.roster().leader().characterObjectId(), current.pendingCandidate().characterObjectId(), Math.max(1, now() + _policy.limits().inviteTimeoutMillis()), invite.reasonKey());
		}
		else
		{
			return staleCandidate(exact, readiness, "rift.invite.identity_missing");
		}
		_metrics.inviteRequest();
		final PendingInvitationReceipt invitation = new PendingInvitationReceipt(exactInvite.sequence(), exactInvite.requesterObjectId(), exactInvite.inviteeObjectId(), now(), exactInvite.canonicalExpiresAtGameTick(), InvitationStatus.PENDING, "rift.invite.pending");
		final Preparation replacement = withInvitation(withStatus(fromReadiness(current, readiness, Stage.OBSERVE_INVITE, current.pendingCandidate(), exactInvite.sequence(), current.totalAttempts() + 1, current.seatAttempts() + 1, activeRefusals(current.refusals()), current.routeHash()), Status.INVITE_PENDING), invitation);
		return saved(exact, replacement, readiness, current.pendingCandidate().kind() == PhantomPartyModel.MemberKind.REAL ? "rift.real_player.consent.pending" : "rift.phantom.invite.pending");
	}
	private AdvanceResult observeInvite(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		if ((current.pendingCandidate() != null) && readiness.roster().members().contains(current.pendingCandidate()))
		{
			_metrics.inviteAccepted();
			return saved(stored, clearCandidate(fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), 0, activeRefusals(current.refusals()), current.routeHash())), readiness, "rift.invite.accepted");
		}
		if ((current.pendingCandidate() == null) || (current.invitationReceipt() == null))
		{
			return saved(stored, clearCandidate(fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), 0, activeRefusals(current.refusals()), current.routeHash())), readiness, "rift.invite.missing");
		}
		final PendingInvitationReceipt expected = current.invitationReceipt();
		final InviteObservation observation = _party.observeInvite(current.leaderProfileId(), current.pendingCandidate(), expected.sequence());
		if ((observation.sequence() > 0) && ((observation.sequence() != expected.sequence()) || (observation.requesterObjectId() != expected.requesterObjectId()) || (observation.inviteeObjectId() != expected.inviteeObjectId())))
		{
			return refusal(stored, readiness, "rift.invite.identity_stale", InvitationStatus.STALE, observation);
		}
		if (observation.status() == InviteStatus.PENDING)
		{
			final long expiry = observation.canonicalExpiresAtGameTick() > 0 ? Math.min(expected.canonicalExpiresAtGameTick(), observation.canonicalExpiresAtGameTick()) : expected.canonicalExpiresAtGameTick();
			final PendingInvitationReceipt pendingReceipt = new PendingInvitationReceipt(expected.sequence(), expected.requesterObjectId(), expected.inviteeObjectId(), expected.requestedAtEpochMillis(), expiry, InvitationStatus.PENDING, "rift.invite.pending");
			final Preparation pending = withInvitation(withStatus(fromReadiness(current, readiness, Stage.OBSERVE_INVITE, current.pendingCandidate(), expected.sequence(), current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), Status.INVITE_PENDING), pendingReceipt);
			return pending.equals(current) ? new AdvanceResult(AdvanceOutcome.RETRY, current, readiness, "rift.invite.pending") : saved(stored, pending, readiness, "rift.invite.pending");
		}
		if (observation.status() == InviteStatus.ACCEPTED)
		{
			_metrics.inviteAccepted();
			return saved(stored, clearCandidate(fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), 0, activeRefusals(current.refusals()), current.routeHash())), readiness, "rift.invite.accepted");
		}
		final InvitationStatus terminal = switch (observation.status())
		{
			case REFUSED -> InvitationStatus.REFUSED;
			case EXPIRED, TIMED_OUT -> InvitationStatus.EXPIRED;
			case CANCELLED -> InvitationStatus.CANCELLED;
			case REJECTED -> InvitationStatus.REJECTED;
			case STALE -> InvitationStatus.STALE;
			case NONE -> InvitationStatus.NONE;
			case PENDING, ACCEPTED -> throw new IllegalStateException("Handled invitation status.");
		};
		return refusal(stored, readiness, observation.reasonKey().isEmpty() ? "rift.invite.reconciled_without_accept" : observation.reasonKey(), terminal, observation);
	}
	private AdvanceResult refusal(StoredPreparation stored, PartyReadiness readiness, String reasonKey, InvitationStatus terminal, InviteObservation observation)
	{
		final Preparation current = stored.preparation();
		final List<Refusal> refusals = new ArrayList<>(activeRefusals(current.refusals()));
		if (current.pendingCandidate() != null)
		{
			refusals.add(new Refusal(current.pendingCandidate(), current.missingVacancyKey(), now(), now() + _policy.limits().refusalCooldownMillis(), normalizeReason(reasonKey)));
		}
		while (refusals.size() > PhantomRiftModel.MAX_REFUSALS)
		{
			refusals.removeFirst();
		}
		if (terminal == InvitationStatus.EXPIRED)
		{
			_metrics.inviteExpired();
		}
		else
		{
			_metrics.inviteRefused();
		}
		return saved(stored, clearCandidate(fromReadiness(current, readiness, Stage.SELECT_CANDIDATE, null, 0, current.totalAttempts(), current.seatAttempts(), refusals, current.routeHash())), readiness, normalizeReason(reasonKey));
	}

	private AdvanceResult staleCandidate(StoredPreparation stored, PartyReadiness readiness, String reasonKey)
	{
		return saved(stored, clearCandidate(fromReadiness(stored.preparation(), readiness, Stage.EVALUATE_READINESS, null, 0, stored.preparation().totalAttempts(), stored.preparation().seatAttempts(), activeRefusals(stored.preparation().refusals()), stored.preparation().routeHash())), readiness, reasonKey);
	}
	private AdvanceResult requestRoute(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		if (readiness.status() != Status.NEEDS_TRAVEL)
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.route.no_longer_required");
		}
		final PhantomDomainRef objective = new PhantomDomainRef("rift.tier", Integer.toString(current.tierType()));
		final PartyBinding binding = _party.observeBinding(current.leaderProfileId(), current.goalId(), current.goalRevision(), objective, _policy.requireTier(current.tierType()).requirements(), readiness.roster());
		if (!sameBinding(current.partyBinding(), binding))
		{
			_metrics.bindingConflict();
			return saved(stored, clearReceipts(fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), ZERO_HASH)), readiness, "rift.route.binding_changed");
		}
		final EntryFacts entry = _catalog.requireTier(current.tierType()).entry();
		final PhantomDomainRef destination = new PhantomDomainRef("rift.entry", Integer.toString(current.tierType()));
		final PhantomNavigationPoint point = new PhantomNavigationPoint(entry.destinationX(), entry.destinationY(), entry.destinationZ(), entry.destinationInstanceId());
		final RouteObservation route = _party.requestRoute(current.leaderProfileId(), destination, point);
		_metrics.routeRequest();
		if (route.status() == RouteStatus.REJECTED)
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), readiness, route.reasonKey());
		}
		return saved(stored, fromReadiness(current, readiness, Stage.OBSERVE_ROUTE, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), route.routeHash()), readiness, "rift.route.requested");
	}

	private AdvanceResult observeRoute(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		if (readiness.status() == Status.READY_TO_ENTER)
		{
			return saved(stored, fromReadiness(current, readiness, Stage.DECLARE_READY, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.route.arrived");
		}
		final RouteObservation route = _party.observeRoute(current.leaderProfileId(), current.routeHash());
		if (route.status() == RouteStatus.PENDING)
		{
			return new AdvanceResult(AdvanceOutcome.RETRY, current, readiness, "rift.route.pending");
		}
		return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), route.routeHash()), readiness, route.reasonKey().isEmpty() ? "rift.route.reconcile" : route.reasonKey());
	}

	private AdvanceResult declareReady(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		if (readiness.status() != Status.READY_TO_ENTER)
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.ready.invalidated");
		}
		final PhantomDomainRef objective = new PhantomDomainRef("rift.tier", Integer.toString(current.tierType()));
		final PartyBinding binding = _party.observeBinding(current.leaderProfileId(), current.goalId(), current.goalRevision(), objective, _policy.requireTier(current.tierType()).requirements(), readiness.roster());
		if (!sameSources(current, readiness) || !sameBinding(current.partyBinding(), binding))
		{
			_metrics.bindingConflict();
			return saved(stored, clearReceipts(fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), ZERO_HASH)), readiness, "rift.ready.binding_changed");
		}
		final Preparation ready = fromReadiness(current, readiness, Stage.DECLARE_READY, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash());
		_metrics.ready();
		return ready.equals(current) ? new AdvanceResult(AdvanceOutcome.READY, current, readiness, "rift.ready.observed") : saved(stored, ready, readiness, "rift.ready.observed");
	}

	private PartyReadiness current(Preparation preparation)
	{
		_metrics.evaluation();
		final PartyReadiness readiness = _readiness.evaluate(preparation.leaderProfileId(), preparation.tierType());
		_metrics.status(readiness.status());
		return readiness;
	}

	private String recruitmentVacancy(PartyReadiness readiness)
	{
		if (!readiness.requiredVacancies().isEmpty())
		{
			return highestPriority(readiness.tierType(), readiness.requiredVacancies());
		}
		if (!readiness.minimumPartySizeSatisfied() && !readiness.optionalVacancies().isEmpty())
		{
			return highestPriority(readiness.tierType(), readiness.optionalVacancies());
		}
		return "";
	}

	private String highestPriority(int tierType, List<String> vacancies)
	{
		return vacancies.stream().sorted(Comparator.<String>comparingInt(key ->
		{
			final var policy = _policy.requireTier(tierType).policyFor(key);
			return policy == null ? Integer.MIN_VALUE : policy.priority();
		}).reversed().thenComparing(Comparator.naturalOrder())).findFirst().orElse("");
	}

	private List<Refusal> activeRefusals(List<Refusal> refusals)
	{
		final long now = now();
		return refusals.stream().filter(value -> value.cooldownUntilEpochMillis() > now).sorted(Comparator.comparingLong(Refusal::refusedEpochMillis)).toList();
	}

	private AdvanceResult saved(StoredPreparation stored, Preparation replacement, PartyReadiness readiness, String reason)
	{
		return saved(stored.profileId(), stored.rowVersion(), replacement, readiness, reason);
	}

	private AdvanceResult saved(long profileId, long expectedVersion, Preparation replacement, PartyReadiness readiness, String reason)
	{
		try
		{
			final Preparation saved = _store.save(profileId, expectedVersion, replacement).preparation();
			return new AdvanceResult(saved.status() == Status.READY_TO_ENTER ? AdvanceOutcome.READY : AdvanceOutcome.RETRY, saved, readiness, normalizeReason(reason));
		}
		catch (RuntimeException e)
		{
			_metrics.conflict();
			return new AdvanceResult(AdvanceOutcome.RETRY, replacement, readiness, "rift.persistence.conflict");
		}
	}

	private Preparation fromReadiness(Preparation current, PartyReadiness readiness, Stage stage, MemberRef candidate, long sequence, int totalAttempts, int seatAttempts, List<Refusal> refusals, String routeHash)
	{
		return new Preparation(current.leaderProfileId(), current.goalId(), current.goalRevision(), current.tierType(), stage, readiness.status(), readiness.roster().evidenceHash(), readiness.catalogHash(), readiness.policyHash(), readiness.configHash(), readiness.roles().evidenceHash(), recruitmentVacancy(readiness), candidate, sequence, totalAttempts, seatAttempts, refusals, routeHash, now(), current.partyBinding(), candidate == null ? null : current.candidateReceipt(), sequence <= 0 ? null : current.invitationReceipt(), false);
	}

	private Preparation copy(Preparation current, Stage stage, Status status, String rosterHash, String roleHash, String vacancy, MemberRef candidate, long sequence, int totalAttempts, int seatAttempts, List<Refusal> refusals, String routeHash)
	{
		return new Preparation(current.leaderProfileId(), current.goalId(), current.goalRevision(), current.tierType(), stage, status, rosterHash, _catalog.catalogHash(), _policy.hash(), _backend.config().hash(), roleHash, vacancy, candidate, sequence, totalAttempts, seatAttempts, refusals, routeHash, now(), null, null, null, false);
	}

	private static boolean sameSources(Preparation preparation, PartyReadiness readiness)
	{
		return preparation.rosterHash().equals(readiness.roster().evidenceHash()) && preparation.catalogHash().equals(readiness.catalogHash()) && preparation.policyHash().equals(readiness.policyHash()) && preparation.configHash().equals(readiness.configHash()) && preparation.roleHash().equals(readiness.roles().evidenceHash());
	}

	private static boolean sameBinding(PartyBindingReceipt expected, PartyBinding observed)
	{
		return (expected != null) && (expected.stability() == BindingStability.STABLE) && observed.stable() && expected.groupId().equals(observed.groupId()) && (expected.groupGeneration() == observed.groupGeneration()) && (expected.membershipRevision() == observed.membershipRevision()) && expected.leader().equals(observed.leader()) && expected.rosterHash().equals(observed.rosterHash()) && expected.manifestHash().equals(observed.manifestHash());
	}

	private static Preparation withBinding(Preparation value, PartyBindingReceipt binding)
	{
		return new Preparation(value.leaderProfileId(), value.goalId(), value.goalRevision(), value.tierType(), value.stage(), value.status(), value.rosterHash(), value.catalogHash(), value.policyHash(), value.configHash(), value.roleHash(), value.missingVacancyKey(), value.pendingCandidate(), value.pendingInvitationSequence(), value.totalAttempts(), value.seatAttempts(), value.refusals(), value.routeHash(), value.updatedEpochMillis(), binding, value.candidateReceipt(), value.invitationReceipt(), false);
	}

	private static Preparation withCandidate(Preparation value, CandidateReceipt candidate)
	{
		return new Preparation(value.leaderProfileId(), value.goalId(), value.goalRevision(), value.tierType(), value.stage(), value.status(), value.rosterHash(), value.catalogHash(), value.policyHash(), value.configHash(), value.roleHash(), value.missingVacancyKey(), value.pendingCandidate(), value.pendingInvitationSequence(), value.totalAttempts(), value.seatAttempts(), value.refusals(), value.routeHash(), value.updatedEpochMillis(), value.partyBinding(), candidate, null, false);
	}

	private static Preparation withInvitation(Preparation value, PendingInvitationReceipt invitation)
	{
		return new Preparation(value.leaderProfileId(), value.goalId(), value.goalRevision(), value.tierType(), value.stage(), value.status(), value.rosterHash(), value.catalogHash(), value.policyHash(), value.configHash(), value.roleHash(), value.missingVacancyKey(), value.pendingCandidate(), value.pendingInvitationSequence(), value.totalAttempts(), value.seatAttempts(), value.refusals(), value.routeHash(), value.updatedEpochMillis(), value.partyBinding(), value.candidateReceipt(), invitation, false);
	}

	private static Preparation clearCandidate(Preparation value)
	{
		return new Preparation(value.leaderProfileId(), value.goalId(), value.goalRevision(), value.tierType(), value.stage(), value.status(), value.rosterHash(), value.catalogHash(), value.policyHash(), value.configHash(), value.roleHash(), value.missingVacancyKey(), null, 0, value.totalAttempts(), value.seatAttempts(), value.refusals(), value.routeHash(), value.updatedEpochMillis(), value.partyBinding(), null, null, false);
	}

	private static Preparation clearReceipts(Preparation value)
	{
		return new Preparation(value.leaderProfileId(), value.goalId(), value.goalRevision(), value.tierType(), value.stage(), value.status(), value.rosterHash(), value.catalogHash(), value.policyHash(), value.configHash(), value.roleHash(), value.missingVacancyKey(), null, 0, value.totalAttempts(), value.seatAttempts(), value.refusals(), value.routeHash(), value.updatedEpochMillis(), null, null, null, false);
	}
	private long now()
	{
		return Math.max(0, _clock.getAsLong());
	}

	private static boolean sameGoal(Preparation value, long goalId, long goalRevision, int tierType)
	{
		return (value.goalId() == goalId) && (value.goalRevision() == goalRevision) && (value.tierType() == tierType);
	}

	private static String normalizeReason(String value)
	{
		return (value == null) || value.isBlank() ? "rift.operation.rejected" : value;
	}

	private static Preparation withStatus(Preparation value, Status status)
	{
		return new Preparation(value.leaderProfileId(), value.goalId(), value.goalRevision(), value.tierType(), value.stage(), status, value.rosterHash(), value.catalogHash(), value.policyHash(), value.configHash(), value.roleHash(), value.missingVacancyKey(), value.pendingCandidate(), value.pendingInvitationSequence(), value.totalAttempts(), value.seatAttempts(), value.refusals(), value.routeHash(), value.updatedEpochMillis(), value.partyBinding(), value.candidateReceipt(), value.invitationReceipt(), value.legacyUntrusted());
	}

	public enum AdvanceOutcome
	{
		READY,
		RETRY,
		REPLAN
	}

	public enum InviteStatus
	{
		PENDING,
		ACCEPTED,
		REFUSED,
		EXPIRED,
		TIMED_OUT,
		CANCELLED,
		REJECTED,
		NONE,
		STALE
	}

	public enum BindingStatus
	{
		STABLE,
		PENDING,
		CONFLICT
	}

	public enum RouteStatus
	{
		PENDING,
		ARRIVED,
		FAILED,
		REJECTED,
		NONE
	}

	public record AdvanceResult(AdvanceOutcome outcome, Preparation preparation, PartyReadiness readiness, String reasonKey)
	{
	}

	public record PartyCommand(boolean accepted, String reasonKey)
	{
	}

	public record PartyBinding(BindingStatus status, String groupId, long groupGeneration, long membershipRevision, MemberRef leader, String rosterHash, String manifestHash, String reasonKey)
	{
		public boolean stable()
		{
			return status == BindingStatus.STABLE;
		}
	}

	public record InviteObservation(InviteStatus status, long sequence, int requesterObjectId, int inviteeObjectId, long canonicalExpiresAtGameTick, String reasonKey)
	{
		public InviteObservation(InviteStatus status, long sequence, String reasonKey)
		{
			this(status, sequence, 0, 0, 0, reasonKey);
		}

		public boolean exactFor(MemberRef leader, MemberRef candidate)
		{
			return (sequence > 0) && (requesterObjectId == leader.characterObjectId()) && (inviteeObjectId == candidate.characterObjectId()) && (canonicalExpiresAtGameTick > 0);
		}
	}

	public record RouteObservation(RouteStatus status, String routeHash, String reasonKey)
	{
	}

	public interface PartyPort
	{
		PartyCommand ensureFormation(long leaderProfileId, long goalId, long goalRevision, PhantomDomainRef objective, List<RoleRequirement> requirements);

		default PartyBinding bind(long leaderProfileId, long goalId, long goalRevision, PhantomDomainRef objective, List<RoleRequirement> requirements, PhantomRiftModel.CanonicalRoster roster)
		{
			final PartyCommand command = ensureFormation(leaderProfileId, goalId, goalRevision, objective, requirements);
			return new PartyBinding(command.accepted() ? BindingStatus.STABLE : BindingStatus.PENDING, command.accepted() ? PhantomPartyModel.sha256("rift.compat.binding|" + leaderProfileId + '|' + goalId + '|' + goalRevision) : ZERO_HASH, command.accepted() ? 1 : 0, 0, roster.leader(), roster.evidenceHash(), roster.evidenceHash(), command.reasonKey());
		}

		default PartyBinding observeBinding(long leaderProfileId, long goalId, long goalRevision, PhantomDomainRef objective, List<RoleRequirement> requirements, PhantomRiftModel.CanonicalRoster roster)
		{
			return bind(leaderProfileId, goalId, goalRevision, objective, requirements, roster);
		}

		default boolean requiresExactBinding()
		{
			return false;
		}
		default boolean candidateClaimAvailable(MemberRef candidate)
		{
			return true;
		}

		InviteObservation invite(long leaderProfileId, MemberRef candidate, PartyDistributionType distribution);

		InviteObservation observeInvite(long leaderProfileId, MemberRef candidate, long expectedSequence);

		RouteObservation requestRoute(long leaderProfileId, PhantomDomainRef destination, PhantomNavigationPoint point);

		RouteObservation observeRoute(long leaderProfileId, String expectedRouteHash);
	}
}
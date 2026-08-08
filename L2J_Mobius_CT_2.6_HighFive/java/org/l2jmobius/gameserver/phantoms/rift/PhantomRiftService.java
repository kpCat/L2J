/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.MemberFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.EntryFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CandidateScore;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PartyReadiness;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Refusal;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Stage;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.SemanticFact;
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
		return switch (stored.preparation().stage())
		{
			case DISCOVER_CONTENT -> discover(stored);
			case SNAPSHOT_ROSTER -> snapshot(stored);
			case EVALUATE_READINESS -> evaluate(stored);
			case SELECT_CANDIDATE -> select(stored);
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

	@Override
	public List<SemanticFact> latest(long profileId)
	{
		final Preparation preparation = _store.load(profileId).map(StoredPreparation::preparation).orElse(null);
		if (preparation == null)
		{
			return List.of();
		}
		return _readiness.semanticFacts(_readiness.evaluate(profileId, preparation.tierType()));
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
			next = Stage.DECLARE_READY;
		}
		else if (readiness.status() == Status.NEEDS_TRAVEL)
		{
			next = Stage.REQUEST_PARTY_ROUTE;
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
			.map(candidate -> _readiness.candidate(candidate, requirement, current.tierType(), leader.member().x(), leader.member().y(), leader.member().z()).orElse(null))
			.filter(Objects::nonNull)
			.sorted(Comparator.comparingInt(CandidateScore::roleScore).reversed().thenComparing(Comparator.comparingInt(CandidateScore::readinessScore).reversed()).thenComparingLong(CandidateScore::distanceSquared).thenComparing(value -> value.member().stableKey()))
			.limit(PhantomRiftModel.MAX_CANDIDATES)
			.toList();
		if (candidates.isEmpty())
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), refusals, current.routeHash()), readiness, "rift.candidate.none");
		}
		final CandidateScore selected = candidates.getFirst();
		return saved(stored, fromReadiness(current, readiness, Stage.REQUEST_INVITE, selected.member(), 0, current.totalAttempts(), current.seatAttempts(), refusals, current.routeHash()), readiness, "rift.candidate.selected");
	}

	private AdvanceResult requestInvite(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		if ((current.pendingCandidate() == null) || readiness.roster().fullParty() || !readiness.roster().evidenceHash().equals(current.rosterHash()) || readiness.roster().members().contains(current.pendingCandidate()))
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), 0, activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.invite.stale");
		}
		final PartyCommand formation = _party.ensureFormation(current.leaderProfileId(), current.goalId(), current.goalRevision(), new PhantomDomainRef("rift.tier", Integer.toString(current.tierType())), _policy.requireTier(current.tierType()).requirements());
		if (!formation.accepted())
		{
			return new AdvanceResult(AdvanceOutcome.RETRY, current, readiness, "rift.party.formation.pending");
		}
		final InviteObservation invite = _party.invite(current.leaderProfileId(), current.pendingCandidate(), readiness.roster().distribution());
		if (invite.status() == InviteStatus.REJECTED)
		{
			return refusal(stored, readiness, invite.reasonKey());
		}
		_metrics.inviteRequest();
		final Preparation replacement = withStatus(fromReadiness(current, readiness, Stage.OBSERVE_INVITE, current.pendingCandidate(), invite.sequence(), current.totalAttempts() + 1, current.seatAttempts() + 1, activeRefusals(current.refusals()), current.routeHash()), Status.INVITE_PENDING);
		return saved(stored, replacement, readiness, current.pendingCandidate().kind() == PhantomPartyModel.MemberKind.REAL ? "rift.real_player.consent.pending" : "rift.phantom.invite.pending");
	}

	private AdvanceResult observeInvite(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		if ((current.pendingCandidate() != null) && readiness.roster().members().contains(current.pendingCandidate()))
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), 0, activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.invite.accepted");
		}
		if (current.pendingCandidate() == null)
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), 0, activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.invite.missing");
		}
		final InviteObservation observation = _party.observeInvite(current.leaderProfileId(), current.pendingCandidate(), current.pendingInvitationSequence());
		if (observation.status() == InviteStatus.PENDING)
		{
			final Preparation pending = withStatus(fromReadiness(current, readiness, Stage.OBSERVE_INVITE, current.pendingCandidate(), Math.max(current.pendingInvitationSequence(), observation.sequence()), current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), Status.INVITE_PENDING);
			return pending.equals(current) ? new AdvanceResult(AdvanceOutcome.RETRY, current, readiness, "rift.invite.pending") : saved(stored, pending, readiness, "rift.invite.pending");
		}
		return refusal(stored, readiness, observation.reasonKey().isEmpty() ? "rift.invite.reconciled_without_accept" : observation.reasonKey());
	}

	private AdvanceResult refusal(StoredPreparation stored, PartyReadiness readiness, String reasonKey)
	{
		final Preparation current = stored.preparation();
		final List<Refusal> refusals = new ArrayList<>(activeRefusals(current.refusals()));
		refusals.add(new Refusal(current.pendingCandidate(), current.missingVacancyKey(), now(), now() + _policy.limits().refusalCooldownMillis(), normalizeReason(reasonKey)));
		while (refusals.size() > PhantomRiftModel.MAX_REFUSALS)
		{
			refusals.removeFirst();
		}
		_metrics.refusal();
		return saved(stored, fromReadiness(current, readiness, Stage.SELECT_CANDIDATE, null, 0, current.totalAttempts(), current.seatAttempts(), refusals, current.routeHash()), readiness, normalizeReason(reasonKey));
	}

	private AdvanceResult requestRoute(StoredPreparation stored)
	{
		final Preparation current = stored.preparation();
		final PartyReadiness readiness = current(current);
		if (readiness.status() != Status.NEEDS_TRAVEL)
		{
			return saved(stored, fromReadiness(current, readiness, Stage.EVALUATE_READINESS, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash()), readiness, "rift.route.no_longer_required");
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
		final Preparation ready = fromReadiness(current, readiness, Stage.DECLARE_READY, null, 0, current.totalAttempts(), current.seatAttempts(), activeRefusals(current.refusals()), current.routeHash());
		_metrics.ready();
		return ready.equals(current) ? new AdvanceResult(AdvanceOutcome.READY, current, readiness, "rift.ready.observed") : saved(stored, ready, readiness, "rift.ready.observed");
	}

	private PartyReadiness current(Preparation preparation)
	{
		_metrics.evaluation();
		return _readiness.evaluate(preparation.leaderProfileId(), preparation.tierType());
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
		return new Preparation(current.leaderProfileId(), current.goalId(), current.goalRevision(), current.tierType(), stage, readiness.status(), readiness.roster().evidenceHash(), readiness.catalogHash(), readiness.policyHash(), readiness.configHash(), readiness.roles().evidenceHash(), recruitmentVacancy(readiness), candidate, sequence, totalAttempts, seatAttempts, refusals, routeHash, now());
	}

	private Preparation copy(Preparation current, Stage stage, Status status, String rosterHash, String roleHash, String vacancy, MemberRef candidate, long sequence, int totalAttempts, int seatAttempts, List<Refusal> refusals, String routeHash)
	{
		return new Preparation(current.leaderProfileId(), current.goalId(), current.goalRevision(), current.tierType(), stage, status, rosterHash, _catalog.catalogHash(), _policy.hash(), _backend.config().hash(), roleHash, vacancy, candidate, sequence, totalAttempts, seatAttempts, refusals, routeHash, now());
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
		return new Preparation(value.leaderProfileId(), value.goalId(), value.goalRevision(), value.tierType(), value.stage(), status, value.rosterHash(), value.catalogHash(), value.policyHash(), value.configHash(), value.roleHash(), value.missingVacancyKey(), value.pendingCandidate(), value.pendingInvitationSequence(), value.totalAttempts(), value.seatAttempts(), value.refusals(), value.routeHash(), value.updatedEpochMillis());
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
		TIMED_OUT,
		REJECTED,
		NONE
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

	public record InviteObservation(InviteStatus status, long sequence, String reasonKey)
	{
	}

	public record RouteObservation(RouteStatus status, String routeHash, String reasonKey)
	{
	}

	public interface PartyPort
	{
		PartyCommand ensureFormation(long leaderProfileId, long goalId, long goalRevision, PhantomDomainRef objective, List<RoleRequirement> requirements);

		InviteObservation invite(long leaderProfileId, MemberRef candidate, PartyDistributionType distribution);

		InviteObservation observeInvite(long leaderProfileId, MemberRef candidate, long expectedSequence);

		RouteObservation requestRoute(long leaderProfileId, PhantomDomainRef destination, PhantomNavigationPoint point);

		RouteObservation observeRoute(long leaderProfileId, String expectedRouteHash);
	}
}

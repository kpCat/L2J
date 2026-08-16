/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatSessionSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPvpCombatRequest;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge.MessageKind;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge.Request;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomPvpRetreatCoordinator;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomPvpRetreatCoordinator.RetreatResult;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpContext.Snapshot;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Candidate;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Counterpart;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.CounterpartKind;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Decision;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Encounter;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Outcome;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Stage;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpPersistencePort.StoredEncounter;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge.EventKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;

/**
 * Worker-free Goal025 coordinator. It admits only materialized profile IDs and
 * delegates combat, chat, social effects, party evidence and retreat to their
 * existing owners.
 */
public final class PhantomPvpService implements PhantomSchedulerControlPort, PhantomMaterializationLifecyclePort
{
	public enum State
	{
		NEW,
		RUNNING,
		STOPPING,
		STOPPED,
		FAILED
	}

	public record ServiceSnapshot(State state, int trackedProfiles, int readyProfiles, int activeOwnerships, int claims, boolean pulseOwned, long pulses, long processed, long encounters, long warnings, long helps, long engagements, long retreats, long cooldowns, long failures)
	{
		public static ServiceSnapshot inactive()
		{
			return new ServiceSnapshot(State.STOPPED, 0, 0, 0, 0, false, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		}
	}

	private static final int MAXIMUM_TRACKED_PROFILES = 2048;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final long NANOS_PER_MINUTE = 60 * NANOS_PER_SECOND;
	private final PhantomPvpPolicy _policy;
	private final PhantomPvpPersistencePort _store;
	private final PhantomPvpContext _context;
	private final PhantomCombatService _combat;
	private final PhantomPvpConversationBridge _conversation;
	private final PhantomPvpSocialBridge _social;
	private final PhantomPvpRetreatCoordinator _retreat;
	private final LongSupplier _clock;
	private final ArrayBlockingQueue<Long> _ready = new ArrayBlockingQueue<>(MAXIMUM_TRACKED_PROFILES);
	private final Set<Long> _membership = ConcurrentHashMap.newKeySet();
	private final Map<Long, Ownership> _ownerships = new ConcurrentHashMap<>();
	private final AtomicBoolean _pulseOwner = new AtomicBoolean();
	private final AtomicInteger _claims = new AtomicInteger();
	private final LongAdder _pulses = new LongAdder();
	private final LongAdder _processed = new LongAdder();
	private final LongAdder _encounters = new LongAdder();
	private final LongAdder _warnings = new LongAdder();
	private final LongAdder _helps = new LongAdder();
	private final LongAdder _engagements = new LongAdder();
	private final LongAdder _retreats = new LongAdder();
	private final LongAdder _cooldowns = new LongAdder();
	private final LongAdder _failures = new LongAdder();
	private volatile State _state = State.NEW;

	public PhantomPvpService(PhantomPvpPolicy policy, PhantomPvpPersistencePort store, PhantomPvpContext context, PhantomCombatService combat, PhantomPvpConversationBridge conversation, PhantomPvpSocialBridge social, PhantomPvpRetreatCoordinator retreat)
	{
		this(policy, store, context, combat, conversation, social, retreat, PhantomPvpService::epochNanos);
	}

	public PhantomPvpService(PhantomPvpPolicy policy, PhantomPvpPersistencePort store, PhantomPvpContext context, PhantomCombatService combat, PhantomPvpConversationBridge conversation, PhantomPvpSocialBridge social, PhantomPvpRetreatCoordinator retreat, LongSupplier clock)
	{
		_policy = Objects.requireNonNull(policy);
		_store = Objects.requireNonNull(store);
		_context = Objects.requireNonNull(context);
		_combat = Objects.requireNonNull(combat);
		_conversation = Objects.requireNonNull(conversation);
		_social = Objects.requireNonNull(social);
		_retreat = Objects.requireNonNull(retreat);
		_clock = Objects.requireNonNull(clock);
	}

	public boolean start()
	{
		if (_state != State.NEW)
		{
			return false;
		}
		_state = State.RUNNING;
		return true;
	}

	@Override
	public void onPulse()
	{
		if ((_state != State.RUNNING) || !_pulseOwner.compareAndSet(false, true))
		{
			return;
		}
		_pulses.increment();
		try
		{
			for (int i = 0; i < _policy.limits().profilesPerPulse(); i++)
			{
				final Long profileId = _ready.poll();
				if (profileId == null)
				{
					break;
				}
				if (!_membership.contains(profileId))
				{
					continue;
				}
				_claims.incrementAndGet();
				try
				{
					process(profileId);
					_processed.increment();
				}
				catch (RuntimeException exception)
				{
					_failures.increment();
				}
				finally
				{
					_claims.decrementAndGet();
					if ((_state == State.RUNNING) && _membership.contains(profileId) && !_ready.offer(profileId))
					{
						_failures.increment();
					}
				}
			}
		}
		finally
		{
			_pulseOwner.set(false);
		}
	}

	private void process(long profileId)
	{
		final long now = _clock.getAsLong();
		StoredEncounter stored = _store.load(profileId).orElse(null);
		if ((stored != null) && (stored.encounter().stage() == Stage.ENGAGE))
		{
			final Encounter encounter = stored.encounter();
			if (_combat.matchesPvpSession(profileId, encounter.counterpart().currentObjectId(), encounter.authorityHash()))
			{
				final Optional<PhantomCombatSessionSnapshot> terminal = _combat.consumeTerminal(profileId);
				if (terminal.isEmpty())
				{
					return;
				}
				_ownerships.remove(profileId);
				handleCombatTerminal(stored, terminal.get(), now);
				return;
			}
			if (_combat.hasClaim(profileId))
			{
				return;
			}
		}
		if ((stored != null) && (stored.encounter().stage() == Stage.RETREAT))
		{
			final RetreatResult result = _retreat.advance(profileId, stored.encounter().authorityHash());
			if (!result.terminal())
			{
				return;
			}
			save(stored, cooldown(stored.encounter(), now, result.reasonKey()));
			return;
		}
		if ((stored != null) && (stored.encounter().cooldownUntilLogicalNanos() > now))
		{
			return;
		}
		final Snapshot observed = _context.observe(profileId, now).orElse(null);
		if (observed == null)
		{
			if ((stored != null) && (stored.encounter().expiresLogicalNanos() <= now) && !terminal(stored.encounter().stage()))
			{
				save(stored, cooldown(stored.encounter(), now, "pvp.authority.expired"));
			}
			return;
		}
		if ((stored == null) || !sameAuthority(stored.encounter(), observed.candidate()) || terminal(stored.encounter().stage()))
		{
			final Encounter initial = initial(observed.candidate());
			stored = save(stored, initial);
			if (stored == null)
			{
				return;
			}
			_encounters.increment();
		}
		if (observed.candidate().source() == Source.ACTUAL_ATTACK)
		{
			social(profileId, observed.socialSubject(), EventKind.ATTACK_RECEIVED, stored.encounter(), observed.sourceEvidenceHash(), now, "attack");
		}
		if (stored.encounter().stage() == Stage.WARN)
		{
			stored = observeWarning(stored, now);
			if ((stored == null) || (stored.encounter().warningLogicalNanos() == 0))
			{
				return;
			}
		}
		final Outcome outcome = _policy.decide(observed.candidate(), observed.risk(), stored.encounter(), now);
		switch (outcome.decision())
		{
			case WAIT:
				return;
			case WARN:
				warn(stored, observed, now);
				return;
			case HELP:
				stored = help(stored, observed, now);
				if (stored == null)
				{
					return;
				}
				engage(stored, observed, false, now);
				return;
			case ENGAGE:
				engage(stored, observed, outcome.forceUse(), now);
				return;
			case RETREAT:
				retreat(stored, now, outcome.reason());
				return;
			case DISENGAGE:
				disengage(stored, observed, now, outcome.reason());
				return;
			case COOLDOWN:
				save(stored, cooldown(stored.encounter(), now, outcome.reason()));
		}
	}

	private StoredEncounter observeWarning(StoredEncounter stored, long now)
	{
		final Encounter encounter = stored.encounter();
		if (encounter.warningReceiptId().isEmpty() || (encounter.warningLogicalNanos() > 0))
		{
			return stored;
		}
		final var receipt = _conversation.receipt(encounter.profileId(), encounter.warningReceiptId()).orElse(null);
		if (receipt == null)
		{
			return stored;
		}
		if (!receipt.delivered())
		{
			return save(stored, cooldown(encounter, now, "pvp.warning.not_delivered"));
		}
		return save(stored, encounter.withStage(Stage.WARN, encounter.warningReceiptId(), encounter.helpReceiptId(), encounter.proactiveEngagements(), now, 0, "pvp.warning.observed"));
	}

	private void warn(StoredEncounter stored, Snapshot observed, long now)
	{
		final long minute = minute(now);
		final var request = new Request(stored.profileId(), observed.conversationCounterpart(), MessageKind.WARNING, stored.encounter().authorityHash(), minute, expiryMinute(stored.encounter(), minute));
		final var submission = _conversation.submit(request);
		if (submission.durable())
		{
			save(stored, stored.encounter().withStage(Stage.WARN, submission.planId(), "", stored.encounter().proactiveEngagements(), 0, 0, "pvp.warning.persisted"));
			_warnings.increment();
		}
	}

	private StoredEncounter help(StoredEncounter stored, Snapshot observed, long now)
	{
		final long minute = minute(now);
		final var request = new Request(stored.profileId(), observed.conversationCounterpart(), MessageKind.HELP_REQUEST, stored.encounter().authorityHash(), minute, expiryMinute(stored.encounter(), minute));
		final var submission = _conversation.submit(request);
		final String receipt = submission.durable() ? submission.planId() : "";
		final StoredEncounter updated = save(stored, stored.encounter().withStage(Stage.HELP, stored.encounter().warningReceiptId(), receipt, stored.encounter().proactiveEngagements(), stored.encounter().warningLogicalNanos(), 0, "pvp.party.help"));
		if (updated != null)
		{
			_helps.increment();
			if ((observed.helpCounterpart() != null) && "profile".equals(observed.helpCounterpart().namespace()))
			{
				try
				{
					final long protectedProfileId = Long.parseLong(observed.helpCounterpart().key());
					social(protectedProfileId, SubjectRef.phantom(stored.profileId()), EventKind.HELP_RECEIVED, updated.encounter(), observed.sourceEvidenceHash(), now, "help");
				}
				catch (NumberFormatException exception)
				{
					_failures.increment();
				}
			}
		}
		return updated;
	}

	private void engage(StoredEncounter stored, Snapshot observed, boolean forceUse, long now)
	{
		if (!sameAuthority(stored.encounter(), observed.candidate()) || !observed.candidate().currentAt(now))
		{
			return;
		}
		final Encounter encounter = stored.encounter();
		final int engagements = encounter.proactiveEngagements() + (encounter.source().proactive() && (encounter.stage() != Stage.ENGAGE) ? 1 : 0);
		final Encounter engaging = encounter.withStage(Stage.ENGAGE, encounter.warningReceiptId(), encounter.helpReceiptId(), engagements, encounter.warningLogicalNanos(), 0, "pvp.combat.admitted");
		final StoredEncounter updated = save(stored, engaging);
		if (updated == null)
		{
			return;
		}
		final Ownership ownership = _ownerships.compute(updated.profileId(), (profileId, current) -> (current != null) && current.matches(updated.encounter().counterpart().currentObjectId(), updated.encounter().authorityHash()) ? current : new Ownership(updated.encounter().counterpart().currentObjectId(), updated.encounter().authorityHash()));
		final long timeoutMillis = Math.multiplyExact(_policy.limits().combatTimeoutSeconds(), 1000L);
		final PhantomPvpCombatRequest request = new PhantomPvpCombatRequest(updated.profileId(), observed.candidate().counterpart().currentObjectId(), observed.candidate().source(), updated.encounter().authorityHash(), observed.mode(), forceUse, true, _policy.limits().cpPotionThresholdPercent(), timeoutMillis, ownership._token);
		final var result = _combat.startPvpSession(request);
		if (result.accepted())
		{
			_engagements.increment();
			return;
		}
		_ownerships.remove(updated.profileId(), ownership);
		if ((result.status() == PhantomCombatService.StartStatus.REJECTED_CAPACITY) || (result.status() == PhantomCombatService.StartStatus.REJECTED_EXISTING))
		{
			return;
		}
		save(updated, cooldown(updated.encounter(), now, "pvp.combat." + result.status().name().toLowerCase()));
	}

	private void retreat(StoredEncounter stored, long now, String reason)
	{
		cancelOwned(stored.encounter());
		if (_combat.hasClaim(stored.profileId()))
		{
			return;
		}
		final StoredEncounter updated = save(stored, stored.encounter().withStage(Stage.RETREAT, stored.encounter().warningReceiptId(), stored.encounter().helpReceiptId(), stored.encounter().proactiveEngagements(), stored.encounter().warningLogicalNanos(), 0, reason));
		if (updated == null)
		{
			return;
		}
		final long duration = Math.multiplyExact(_policy.limits().retreatTimeoutSeconds(), NANOS_PER_SECOND);
		final RetreatResult result = _retreat.start(updated.profileId(), updated.encounter().authorityHash(), duration);
		_retreats.increment();
		if (result.terminal())
		{
			save(updated, cooldown(updated.encounter(), now, result.reasonKey()));
		}
	}

	private void disengage(StoredEncounter stored, Snapshot observed, long now, String reason)
	{
		cancelOwned(stored.encounter());
		final long minute = minute(now);
		_conversation.submit(new Request(stored.profileId(), observed.conversationCounterpart(), MessageKind.DISENGAGE, stored.encounter().authorityHash(), minute, expiryMinute(stored.encounter(), minute)));
		save(stored, cooldown(stored.encounter(), now, reason));
	}

	private void handleCombatTerminal(StoredEncounter stored, PhantomCombatSessionSnapshot terminal, long now)
	{
		final Encounter encounter = stored.encounter();
		final SubjectRef subject = subject(encounter.counterpart());
		if (terminal.result().victory())
		{
			social(stored.profileId(), subject, EventKind.KILL_CAUSED, encounter, encounter.authorityHash(), now, "kill");
		}
		else if (terminal.result() == PhantomCombatResult.PLAYER_DEAD)
		{
			social(stored.profileId(), subject, EventKind.DEATH_SUFFERED, encounter, encounter.authorityHash(), now, "death");
		}
		if (terminal.result() == PhantomCombatResult.LOW_HP_STOPPED)
		{
			retreat(stored, now, "pvp.combat.low_hp");
			return;
		}
		save(stored, cooldown(encounter, now, "pvp.combat." + terminal.result().name().toLowerCase()));
	}

	private void social(long ownerProfileId, SubjectRef subject, EventKind kind, Encounter encounter, String sourceEvidenceHash, long now, String operation)
	{
		final String operationId = PhantomPvpModel.sha256("pvp.social.operation", encounter.authorityHash(), encounter.createdLogicalNanos(), operation);
		final String evidence = PhantomPvpModel.sha256("pvp.social.evidence", encounter.authorityHash(), sourceEvidenceHash, kind);
		_social.record(ownerProfileId, subject, kind, operationId, evidence, minute(now), 100);
	}

	private StoredEncounter save(StoredEncounter current, Encounter encounter)
	{
		try
		{
			final long expected = current == null ? -1 : current.rowVersion();
			return _store.save(encounter.profileId(), expected, encounter);
		}
		catch (RuntimeException exception)
		{
			_failures.increment();
			return null;
		}
	}

	private Encounter initial(Candidate candidate)
	{
		return new Encounter(candidate.profileId(), candidate.counterpart(), candidate.source(), candidate.authorityHash(), Stage.OBSERVE, "", "", 0, candidate.createdLogicalNanos(), candidate.expiresLogicalNanos(), 0, 0, "pvp.observed");
	}

	private Encounter cooldown(Encounter encounter, long now, String reason)
	{
		final long until = add(now, Math.multiplyExact(_policy.limits().pairCooldownSeconds(), NANOS_PER_SECOND));
		final long expires = Math.max(encounter.expiresLogicalNanos(), add(until, 1));
		_cooldowns.increment();
		return new Encounter(encounter.profileId(), encounter.counterpart(), encounter.source(), encounter.authorityHash(), Stage.COOLDOWN, encounter.warningReceiptId(), encounter.helpReceiptId(), encounter.proactiveEngagements(), encounter.createdLogicalNanos(), expires, encounter.warningLogicalNanos(), until, reason);
	}

	private void cancelOwned(Encounter encounter)
	{
		final Ownership ownership = _ownerships.remove(encounter.profileId());
		if ((ownership != null) && ownership._authorityHash.equals(encounter.authorityHash()))
		{
			ownership._cancelled.set(true);
		}
		if (_combat.matchesPvpSession(encounter.profileId(), encounter.counterpart().currentObjectId(), encounter.authorityHash()))
		{
			_combat.cancel(encounter.profileId());
		}
		_retreat.cancel(encounter.profileId(), encounter.authorityHash());
	}

	private static boolean sameAuthority(Encounter encounter, Candidate candidate)
	{
		return (encounter.profileId() == candidate.profileId()) && (encounter.source() == candidate.source()) && encounter.authorityHash().equals(candidate.authorityHash()) && encounter.counterpart().kind() == candidate.counterpart().kind() && (encounter.counterpart().identity() == candidate.counterpart().identity()) && (encounter.counterpart().currentObjectId() == candidate.counterpart().currentObjectId());
	}

	private static boolean terminal(Stage stage)
	{
		return (stage == Stage.DISENGAGE) || (stage == Stage.COOLDOWN) || (stage == Stage.TERMINAL);
	}

	private static SubjectRef subject(Counterpart counterpart)
	{
		return counterpart.kind() == CounterpartKind.PHANTOM_PROFILE ? SubjectRef.phantom(counterpart.identity()) : SubjectRef.character(counterpart.currentObjectId());
	}

	private static long minute(long nanos)
	{
		return nanos / NANOS_PER_MINUTE;
	}

	private static long expiryMinute(Encounter encounter, long createdMinute)
	{
		final long rounded = add(encounter.expiresLogicalNanos(), NANOS_PER_MINUTE - 1) / NANOS_PER_MINUTE;
		return Math.max(add(createdMinute, 1), rounded);
	}

	private static long add(long left, long right)
	{
		try
		{
			return Math.addExact(left, right);
		}
		catch (ArithmeticException exception)
		{
			return Long.MAX_VALUE;
		}
	}

	private static long epochNanos()
	{
		try
		{
			return Math.multiplyExact(System.currentTimeMillis(), 1_000_000L);
		}
		catch (ArithmeticException exception)
		{
			return Long.MAX_VALUE - NANOS_PER_MINUTE;
		}
	}

	@Override
	public void beforeMaterialize(long profileId, int characterObjectId)
	{
	}

	@Override
	public void afterPlayerLoad(long profileId, Player player)
	{
	}

	@Override
	public void materializeSucceeded(long profileId, int characterObjectId)
	{
		if ((_state == State.RUNNING) && (profileId > 0) && _membership.add(profileId) && !_ready.offer(profileId))
		{
			_membership.remove(profileId);
			_failures.increment();
		}
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
		remove(profileId);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		remove(profileId);
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
	}

	private void remove(long profileId)
	{
		_membership.remove(profileId);
		final StoredEncounter stored = _store.load(profileId).orElse(null);
		if (stored != null)
		{
			cancelOwned(stored.encounter());
		}
	}

	public void beginStop()
	{
		if (_state == State.RUNNING)
		{
			_state = State.STOPPING;
			_membership.clear();
		}
	}

	public boolean finishStop()
	{
		if (_state == State.NEW)
		{
			_state = State.STOPPED;
			return true;
		}
		if (_state == State.RUNNING)
		{
			beginStop();
		}
		if (_pulseOwner.get() || (_claims.get() != 0))
		{
			return false;
		}
		for (Map.Entry<Long, Ownership> entry : _ownerships.entrySet())
		{
			final Ownership ownership = entry.getValue();
			ownership._cancelled.set(true);
			if (_combat.matchesPvpSession(entry.getKey(), ownership._targetObjectId, ownership._authorityHash))
			{
				_combat.cancel(entry.getKey());
			}
		}
		_ownerships.clear();
		_ready.clear();
		_retreat.finishStop();
		_state = State.STOPPED;
		return true;
	}

	public ServiceSnapshot snapshot()
	{
		return new ServiceSnapshot(_state, _membership.size(), _ready.size(), _ownerships.size(), _claims.get(), _pulseOwner.get(), _pulses.sum(), _processed.sum(), _encounters.sum(), _warnings.sum(), _helps.sum(), _engagements.sum(), _retreats.sum(), _cooldowns.sum(), _failures.sum());
	}

	private static final class Ownership
	{
		private final int _targetObjectId;
		private final String _authorityHash;
		private final AtomicBoolean _cancelled = new AtomicBoolean();
		private final PhantomCancellationToken _token = _cancelled::get;

		private Ownership(int targetObjectId, String authorityHash)
		{
			_targetObjectId = targetObjectId;
			_authorityHash = authorityHash;
		}

		private boolean matches(int targetObjectId, String authorityHash)
		{
			return (_targetObjectId == targetObjectId) && _authorityHash.equals(authorityHash);
		}
	}
}

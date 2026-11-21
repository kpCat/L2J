/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.skill.Skill;

/** Exact passive native writers carried by one real MagicUseTask. Never changes HP, MP or cast validity. */
public final class HealEvidenceV3
{
	public record Attempt(TacticalObservationV3 observation, List<LegalActionV3> offered, LegalActionV3 selected, LegalActionV3 baseline, long modelVersion,
		Consumer<Receipt> sink, Consumer<TacticalFailureV3> failureSink, BiConsumer<SourceOwner, Long> nativeIntent)
	{
		public Attempt(TacticalObservationV3 observation, List<LegalActionV3> offered, LegalActionV3 selected, LegalActionV3 baseline, long version, Consumer<Receipt> sink)
		{ this(observation,offered,selected,baseline,version,sink,_ -> { }, null); }
		public Attempt(TacticalObservationV3 observation, List<LegalActionV3> offered, LegalActionV3 selected, LegalActionV3 baseline, long version,
			Consumer<Receipt> sink, Consumer<TacticalFailureV3> failureSink)
		{ this(observation, offered, selected, baseline, version, sink, failureSink, null); }
		public Attempt
		{
			offered = List.copyOf(offered);
			final boolean heal = selected.family() == LegalActionV3.Family.HEAL && selected.trainable();
			final boolean recharge = selected.family() == LegalActionV3.Family.RECHARGE && selected.lawful() && offered.size() == 1
				&& selected.equals(baseline) && nativeIntent == null;
			if (offered.isEmpty() || offered.size() > 16 || !offered.contains(selected) || !offered.contains(baseline) || !(heal || recharge) || sink == null || failureSink == null)
			{ throw new IllegalArgumentException("Support attempt must bind offered lawful actions"); }
			for (var action : offered) { action.features(observation); if (action.generation() != observation.decisionId()) { throw new IllegalArgumentException("Stale action generation"); } }
		}
	}
	public record SourceOwner(long generation, String operationKey)
	{
		public SourceOwner { if (generation <= 0 || operationKey == null || operationKey.isBlank() || operationKey.length() > 128) { throw new IllegalArgumentException("Missing native support owner"); } }
	}
	public record Receipt(String eventKey, Attempt attempt, double beforeHp, double afterHp, double actualHp, double actualMp, long elapsedNanos, float reward, SourceOwner owner)
	{
		/** Pure contract fixtures never enter native SQL; actual receipts always supply the real lease identity. */
		public Receipt(String key, Attempt attempt, double before, double after, double hp, double mp, long nanos, float reward)
		{ this(key, attempt, before, after, hp, mp, nanos, reward, new SourceOwner(1, "CONTRACT_FIXTURE")); }
		public Receipt
		{
			if (eventKey == null || !eventKey.matches("[a-f0-9]{64}") || attempt == null || owner == null || !Double.isFinite(beforeHp) || !Double.isFinite(afterHp)
				|| !Double.isFinite(actualHp) || actualHp <= 0 || afterHp <= beforeHp || !Double.isFinite(actualMp) || actualMp <= 0 || elapsedNanos <= 0 || !Float.isFinite(reward))
			{ throw new IllegalArgumentException("Unproven HEAL receipt"); }
		}
	}

	/** Paid exact native Heal, including a stock clamp that intentionally performs no HP write. */
	public record NativeHealReceipt(LearningCastEvidence.NativeCastTrace cast, TacticalObservationV3 observation, SourceOwner owner,
		long offeredNanos, long hpWriteNanos, long mpStartedNanos, long mpFinishedNanos, double beforeHp, double afterHp,
		double offeredHp, double requestedHp, double paidMp)
	{
		public NativeHealReceipt
		{
			if (cast == null || observation == null || owner == null || offeredNanos < cast.executedNanos()
				|| mpStartedNanos < cast.decisionNanos() || mpFinishedNanos < mpStartedNanos
				|| !Double.isFinite(offeredHp) || offeredHp <= 0 || !Double.isFinite(requestedHp) || requestedHp < 0 || requestedHp > offeredHp
				|| !Double.isFinite(paidMp) || paidMp <= 0)
			{ throw new IllegalArgumentException("Invalid native Heal receipt"); }
			final var target = observation.entity(cast.targetObjectId());
			if (cast.actorObjectId() != observation.actorObjectId() || cast.actorEpoch() != observation.actorEpoch()
				|| cast.decisionNanos() < observation.capturedNanos() || target == null || target.epoch() != cast.targetEpoch())
			{ throw new IllegalArgumentException("Native Heal observation mismatch"); }
			final boolean exactHp = requestedHp == 0 ? hpWriteNanos == 0 && beforeHp == 0 && afterHp == 0
				: hpWriteNanos >= offeredNanos && Double.isFinite(beforeHp) && Double.isFinite(afterHp) && beforeHp >= 0 && afterHp > beforeHp
					&& Math.abs((afterHp - beforeHp) - requestedHp) <= Math.ulp(afterHp);
			if (!exactHp) { throw new IllegalArgumentException("Native Heal HP write mismatch"); }
		}
	}

	/** The exact stock Recharge handler result and caster debit, including a paid result with no MP write. */
	public record NativeRechargeReceipt(LearningCastEvidence.NativeCastTrace cast, TacticalObservationV3 observation, SourceOwner owner,
		long handlerFinishedNanos, long targetWriteNanos, long mpStartedNanos, long mpFinishedNanos, double beforeMp, double afterMp, double paidMp)
	{
		public NativeRechargeReceipt
		{
			if (cast == null || observation == null || owner == null || cast.actorObjectId() != observation.actorObjectId()
				|| cast.actorEpoch() != observation.actorEpoch() || cast.actorObjectId() == cast.targetObjectId()
				|| cast.decisionNanos() < observation.capturedNanos() || handlerFinishedNanos < cast.executedNanos()
				|| mpStartedNanos < cast.decisionNanos() || mpFinishedNanos < mpStartedNanos || !Double.isFinite(paidMp) || paidMp <= 0)
			{ throw new IllegalArgumentException("Invalid native Recharge receipt"); }
			final var ally = observation.entity(cast.targetObjectId());
			if (ally == null || ally.role() != TacticalObservationV3.Role.ALLY || ally.epoch() != cast.targetEpoch() || observation.partyIdentity().isBlank())
			{ throw new IllegalArgumentException("Native Recharge party observation mismatch"); }
			final boolean exact = targetWriteNanos == 0 ? beforeMp == 0 && afterMp == 0
				: targetWriteNanos >= cast.executedNanos() && targetWriteNanos <= handlerFinishedNanos
					&& Double.isFinite(beforeMp) && Double.isFinite(afterMp) && beforeMp >= 0 && afterMp > beforeMp;
			if (!exact) { throw new IllegalArgumentException("Native Recharge MP write mismatch"); }
		}
	}

	private static final boolean TRAIN = Boolean.getBoolean("phantom.learning.train");
	private static final ThreadLocal<Attempt> REQUEST = new ThreadLocal<>();
	private static final ThreadLocal<HealEvidenceV3> DISPATCH = new ThreadLocal<>(), HIT = new ThreadLocal<>(), EFFECT = new ThreadLocal<>();
	private static final ThreadLocal<HealEvidenceV3> TARGET_EFFECT = new ThreadLocal<>();
	private record Authority(SourceOwner identity, BooleanSupplier current) { }
	private static final ThreadLocal<Authority> OWNER = new ThreadLocal<>();
	private static final AtomicReference<AuditObserver> AUDIT = new AtomicReference<>();
	private record AuditObserver(Consumer<NativeHealReceipt> heals, Consumer<NativeRechargeReceipt> recharges, Consumer<String> failures) implements AutoCloseable
	{
		@Override public void close() { AUDIT.compareAndSet(this, null); }
	}

	public static AutoCloseable observeNativeAudit(Consumer<NativeHealReceipt> heals, Consumer<String> failures)
	{
		return observeNativeAudit(heals, null, failures);
	}

	public static AutoCloseable observeNativeAudit(Consumer<NativeHealReceipt> heals, Consumer<NativeRechargeReceipt> recharges, Consumer<String> failures)
	{
		if (!TRAIN) { throw new IllegalStateException("Training opt-in required"); }
		final var observer = new AuditObserver(Objects.requireNonNull(heals), recharges, Objects.requireNonNull(failures));
		if (!AUDIT.compareAndSet(null, observer)) { throw new IllegalStateException("Native Heal AUDIT observer already installed"); }
		return observer;
	}

	static Consumer<String> rechargeObserverFailure()
	{
		final var observer = TRAIN ? AUDIT.get() : null;
		return observer != null && observer.recharges() != null ? reason -> reportAuditFailure(observer, reason) : null;
	}

	public static final LongAdder ACCEPTED = new LongAdder(), REJECTED = new LongAdder();
	private final Attempt _attempt;
	private final Player _actor, _target;
	private final Skill _skill;
	private final PlayerNativeWork.Owner _owner, _targetOwner;
	private final Party _party;
	private final BooleanSupplier _authority;
	private final Authority _actionOwner;
	private final AuditObserver _auditObserver;
	private final long _created = System.nanoTime();
	private boolean _bound, _issued, _effects, _finished, _published, _failurePublished, _auditInexact;
	private volatile boolean _invalid;
	private double _hp, _mp, _beforeHp = Double.NaN, _afterHp;
	private long _hpStart,_hpEnd,_mpStart,_mpEnd;
	private long _admittedNanos, _executedNanos, _offeredNanos, _hpWriteNanos;
	private double _offeredHp, _requestedHp;
	private long _rechargeFinishedNanos, _rechargeWriteNanos;
	private double _beforeRechargeMp, _afterRechargeMp;
	private HealEvidenceV3(Attempt attempt, Player actor, Player target, Skill skill, BooleanSupplier authority)
	{
		_attempt = attempt; _actor = actor; _target = target; _skill = skill; _authority = authority;
		_owner = actor.getNativeWorkOwner(); _targetOwner = target.getNativeWorkOwner(); _party = actor.getParty();
		_actionOwner = OWNER.get();
		_auditObserver = AUDIT.get();
	}
	public static <T> T withOwnerAuthority(long generation, String operation, BooleanSupplier current, java.util.function.Supplier<T> nativeAction)
	{
		if (!TRAIN || REQUEST.get() == null) { return nativeAction.get(); }
		final var previous = OWNER.get(); OWNER.set(new Authority(new SourceOwner(generation, operation), current));
		try { return nativeAction.get(); } finally { restore(OWNER, previous); }
	}
	public static <T> T withAttempt(Attempt attempt, java.util.function.Supplier<T> nativeAction)
	{
		final Attempt old = REQUEST.get(); REQUEST.set(attempt);
		try { return nativeAction.get(); } finally { restore(REQUEST, old); }
	}
	/** Invoked only from the existing support owner, after stock legality and lease checks. */
	public static boolean dispatch(Player actor, Player target, Skill skill, BooleanSupplier authority, java.util.function.BooleanSupplier nativeCast)
	{
		final Attempt attempt = REQUEST.get();
		final var rechargeFailure = attempt != null && attempt.selected().family() == LegalActionV3.Family.RECHARGE ? rechargeObserverFailure() : null;
		if (!TRAIN || attempt == null || attempt.selected().actorObjectId() != actor.getObjectId() || attempt.selected().targetObjectId() != target.getObjectId()
			|| attempt.selected().skillId() != skill.getId() || attempt.selected().skillLevel() != skill.getLevel()) { return castWithoutRechargeEvidence(nativeCast, rechargeFailure); }
		final HealEvidenceV3 ticket = new HealEvidenceV3(attempt, actor, target, skill, authority);
		if (ticket.recharge() && (actor == target || rechargeFailure == null || !ticket.auditArmed())) { return castWithoutRechargeEvidence(nativeCast, rechargeFailure); }
		if (System.nanoTime() - attempt.observation().capturedNanos() > 1_000_000_000L || !ticket.current())
		{
			if (ticket.recharge()) { return castWithoutRechargeEvidence(nativeCast, rechargeFailure); }
			REJECTED.increment(); ticket.failure("STALE_AT_DISPATCH",false); return false;
		}
		if (attempt.nativeIntent() != null)
		{
			try { attempt.nativeIntent().accept(ticket._actionOwner.identity(), ticket._created); }
			catch (RuntimeException rejected) { REJECTED.increment(); ticket.failure("INTENT_REJECTED", false); return false; }
		}
		final HealEvidenceV3 previous = DISPATCH.get(); DISPATCH.set(ticket);
		try { final boolean issued = nativeCast.getAsBoolean(); ticket.issued(issued); return issued; }
		catch (RuntimeException | Error unavailable) { ticket.cancelled(); throw unavailable; }
		finally { restore(DISPATCH, previous); }
	}

	private boolean recharge() { return _attempt.selected().family() == LegalActionV3.Family.RECHARGE; }

	private static boolean castWithoutRechargeEvidence(BooleanSupplier nativeCast, Consumer<String> failure)
	{
		final boolean issued = nativeCast.getAsBoolean();
		if (issued && failure != null) { failure.accept("NATIVE_RECHARGE_ADMISSION_UNPROVEN"); }
		return issued;
	}
	public static HealEvidenceV3 capture(Creature actor, Skill skill, List<WorldObject> targets, boolean simultaneous)
	{
		if (!TRAIN) { return null; }
		final HealEvidenceV3 ticket = DISPATCH.get();
		if (ticket == null || ticket._bound || simultaneous || actor != ticket._actor || skill != ticket._skill || targets == null || targets.size() != 1 || targets.getFirst() != ticket._target || !ticket.current()) { return null; }
		ticket._bound = true;
		if (ticket.auditArmed()) { ticket._admittedNanos = System.nanoTime(); }
		return ticket;
	}
	public void hit(Runnable nativeHit)
	{
		final HealEvidenceV3 old = HIT.get(); HIT.set(this);
		try { nativeHit.run(); } finally { restore(HIT, old); }
	}
	public static HealEvidenceV3 takeHit() { final var ticket = HIT.get(); HIT.remove(); return ticket; }
	public static void effects(HealEvidenceV3 ticket, Creature actor, Skill skill, List<WorldObject> targets, Runnable nativeEffects)
	{
		if (ticket == null)
		{
			final var outer = EFFECT.get(); final var outerTarget = TARGET_EFFECT.get(); EFFECT.remove(); TARGET_EFFECT.remove();
			try { nativeEffects.run(); } finally { restore(EFFECT, outer); restore(TARGET_EFFECT, outerTarget); }
			return;
		}
		boolean valid;
		synchronized (ticket)
		{
			valid = !ticket._effects && actor == ticket._actor && skill == ticket._skill && targets.size() == 1 && targets.getFirst() == ticket._target && ticket.current();
			ticket._effects = true; if (!valid) { ticket._invalid = true; }
			if (valid && ticket.auditArmed()) { ticket._executedNanos = System.nanoTime(); }
		}
		final HealEvidenceV3 old = EFFECT.get(), oldTarget = TARGET_EFFECT.get(); restore(EFFECT, valid ? ticket : null); TARGET_EFFECT.remove();
		try { nativeEffects.run(); ticket.finished(valid); }
		finally { restore(EFFECT, old); restore(TARGET_EFFECT, oldTarget); }
	}
	/** Called inside the existing status monitor, after the actual successful HP assignment. */
	public static boolean observingEffects() { return EFFECT.get() != null; }
	/** Only the selected skill's exact instant support handler can attribute a status write. */
	public static void nativeEffect(Creature actor, Creature target, Skill skill, EffectType type, Runnable nativeEffect)
	{
		final var ticket = EFFECT.get(); final var previous = TARGET_EFFECT.get();
		final boolean recharge = ticket != null && ticket.recharge();
		final boolean exact = ticket != null && actor == ticket._actor && target == ticket._target && skill == ticket._skill
			&& (recharge ? type == EffectType.MANAHEAL_BY_LEVEL || type == EffectType.MANAHEAL_PERCENT : type == EffectType.HEAL);
		restore(TARGET_EFFECT, exact ? ticket : null);
		try
		{
			if (exact && !recharge && ticket._hpStart == 0) { ticket._hpStart = System.nanoTime(); }
			nativeEffect.run();
			if (exact && recharge) { ticket.rechargeFinished(); }
		}
		finally
		{
			if (exact && !recharge) { ticket._hpEnd = System.nanoTime(); }
			restore(TARGET_EFFECT, previous);
		}
	}

	private synchronized void rechargeFinished()
	{
		if (_rechargeFinishedNanos != 0 || _published) { _auditInexact = true; }
		_rechargeFinishedNanos = System.nanoTime();
	}
	public static void failedEffect() { final var ticket = EFFECT.get(); if (ticket != null) { synchronized (ticket) { ticket._invalid = true; } } }

	/** Exact stock Heal handler input and clamp result, immediately before its existing setter. */
	public static void nativeOffer(Creature effector, Creature recipient, Skill skill, double offered, double requested)
	{
		if (!TRAIN || AUDIT.get() == null) { return; }
		final var ticket = TARGET_EFFECT.get();
		if (ticket == null || ticket != EFFECT.get() || ticket.recharge() || !ticket.auditArmed() || effector != ticket._actor || recipient != ticket._target || skill != ticket._skill) { return; }
		synchronized (ticket)
		{
			if (ticket._published) { return; }
			if (ticket._offeredNanos != 0 || !Double.isFinite(offered) || offered <= 0 || !Double.isFinite(requested) || requested < 0 || requested > offered)
			{
				ticket._auditInexact = true;
				return;
			}
			ticket._offeredNanos = System.nanoTime();
			ticket._offeredHp = offered;
			ticket._requestedHp = requested;
		}
	}

	public static void hpWritten(Creature recipient, double before, double after, boolean regeneration)
	{
		final HealEvidenceV3 ticket = TARGET_EFFECT.get();
		if (ticket == null || regeneration || ticket.recharge() || recipient != ticket._target) { return; }
		final boolean positive = Double.isFinite(before) && Double.isFinite(after) && after > before;
		if (!positive && !ticket.auditArmed()) { return; }
		synchronized (ticket)
		{
			if (ticket.auditArmed() && !ticket._published)
			{
				if (!positive || ticket._offeredNanos == 0 || ticket._hpWriteNanos != 0) { ticket._auditInexact = true; }
				else { ticket._hpWriteNanos = System.nanoTime(); }
			}
			if (!positive) { return; }
			if (!ticket.current() || ticket._published) { ticket._invalid = true; return; }
			if (ticket._hp > 0) { ticket._invalid = true; return; }
			ticket._beforeHp = before; ticket._afterHp = after; ticket._hp = after - before;
		}
	}
	/** Native MP debits during this exact useMagic / task, never a later MP read. */
	public static void mpWritten(Creature actor, double before, double after, boolean regeneration)
	{
		HealEvidenceV3 ticket = DISPATCH.get(); if (ticket == null) { ticket = HIT.get(); } if (ticket == null) { ticket = EFFECT.get(); }
		if (ticket == null || regeneration) { return; }
		if (ticket.recharge() && actor == ticket._target && TARGET_EFFECT.get() == ticket)
		{
			ticket.rechargeWritten(before, after);
			return;
		}
		if (actor != ticket._actor || !Double.isFinite(before) || !Double.isFinite(after) || before <= after) { return; }
		synchronized (ticket) { if (ticket.current() && !ticket._published) { ticket._mp += before - after;final long stamp=System.nanoTime();if(ticket._mpStart==0){ticket._mpStart=stamp;}ticket._mpEnd=stamp; } else { ticket._invalid = true; } }
	}

	private synchronized void rechargeWritten(double before, double after)
	{
		if (_published || _rechargeWriteNanos != 0 || !Double.isFinite(before) || !Double.isFinite(after) || before < 0 || after < before)
		{
			_auditInexact = true;
			return;
		}
		if (after == before) { return; }
		_beforeRechargeMp = before;
		_afterRechargeMp = after;
		_rechargeWriteNanos = System.nanoTime();
	}
	private boolean current()
	{
		return !_invalid && _actionOwner != null && _actionOwner.current().getAsBoolean() && _owner != null && _targetOwner != null && _actor.getNativeWorkOwner() == _owner && _target.getNativeWorkOwner() == _targetOwner
			&& _owner.player() == _actor && _targetOwner.player() == _target && _owner.isCurrent() && _targetOwner.isCurrent()
			&& _owner.nativeObservationHealthy() && _targetOwner.nativeObservationHealthy() && !_actor.isAlikeDead() && !_target.isAlikeDead()
			&& World.getInstance().findObject(_actor.getObjectId()) == _actor && World.getInstance().findObject(_target.getObjectId()) == _target
			&& !org.l2jmobius.gameserver.data.xml.SkillData.getInstance().catalogReloaded() && _actor.getKnownSkill(_skill.getId()) == _skill && _actor.getParty() == _party && _target.getParty() == _party && _party != null
			&& _attempt.selected().current(_actor.getObjectId(), _owner.epoch(), _target.getObjectId(), _targetOwner.epoch(), TacticalSnapshotV3.partyIdentity(_actor))
			&& _authority.getAsBoolean() && System.nanoTime() - _created < 20_000_000_000L;
	}

	private void issued(boolean issued)
	{
		final boolean missingTask, publish;
		synchronized (this)
		{
			_issued = issued;
			missingTask = !issued || !_bound;
			publish = claimPublication();
		}
		if (missingTask) { failure("NO_NATIVE_TASK", false); }
		if (issued && missingTask && recharge()) { reportAuditFailure(_auditObserver, "NATIVE_RECHARGE_TASK_UNPROVEN"); }
		if (publish) { publish(); }
	}

	private void finished(boolean valid)
	{
		final boolean publish;
		synchronized (this)
		{
			_finished = valid;
			publish = claimPublication();
		}
		if (!valid) { auditFailure(recharge() ? "NATIVE_RECHARGE_EFFECT_UNPROVEN" : "NATIVE_HEAL_EFFECT_UNPROVEN"); }
		if (publish) { publish(); }
	}

	/** The ticket monitor owns the claim, and subscribers run after it is released. */
	private boolean claimPublication()
	{
		if (!_issued || !_finished || _published) { return false; }
		_published = true;
		return true;
	}

	private void publish()
	{
		if (recharge())
		{
			publishRecharge();
			return;
		}
		if (_hp <= 0 || _mp <= 0 || !current())
		{
			if (_hp <= 0 && _mp > 0 && _offeredNanos != 0 && _requestedHp == 0 && !_auditInexact && auditArmed() && current())
			{
				publishNativeAudit(eventKey());
			}
			else { auditFailure("NATIVE_HEAL_EFFECT_UNPROVEN"); }
			REJECTED.increment();
			failure(_hp <= 0 ? "NO_EXACT_HP" : _mp <= 0 ? "NO_EXACT_MP" : "NATIVE_AUTHORITY_CHANGED", true);
			return;
		}
		final long elapsed = Math.max(1, System.nanoTime() - _created);
		final double maximum = _attempt.observation().entity(_target.getObjectId()).fact(TacticalObservationV3.Field.MAX_HP).value();
		if (maximum <= 0) { auditFailure("NATIVE_HEAL_MAXIMUM_UNPROVEN"); REJECTED.increment(); failure("UNKNOWN_MAXIMUM_HP",true); return; }
		final String key = eventKey();
		publishNativeAudit(key);
		final float reward = reward(_hp, maximum, _mp, elapsed);
		try { TerminalRewardReceiptV1.nativeTiming(new TerminalRewardReceiptV1.NativeTiming(key,_actor.getInstanceId(),_hpStart,_hpEnd,_mpStart,_mpEnd,false));_attempt.sink().accept(new Receipt(key, _attempt, _beforeHp, _afterHp, _hp, _mp, elapsed, reward, _actionOwner.identity())); ACCEPTED.increment(); }
		catch (RuntimeException unavailable) { REJECTED.increment(); failure("RECEIPT_REJECTED",true); }
	}

	private String eventKey()
	{
		return TacticalObservationV3.hash((_attempt.observation().partyIdentity() + '/' + _actor.getObjectId() + '/' + _owner.epoch() + '/' + _target.getObjectId()
			+ '/' + _targetOwner.epoch() + '/' + _attempt.selected().generation() + '/' + _actionOwner.identity().generation() + '/' + _actionOwner.identity().operationKey() + '/' + _skill.getId() + '/' + _created).getBytes(StandardCharsets.UTF_8));
	}

	private boolean auditArmed() { return _auditObserver != null && _auditObserver == AUDIT.get(); }

	private LearningCastEvidence.NativeCastTrace nativeCastTrace(String key)
	{
		final var baseline = _attempt.baseline();
		return new LearningCastEvidence.NativeCastTrace(key, _actor.getObjectId(), _owner.epoch(), _target.getObjectId(), _targetOwner.epoch(),
			_actor.getInstanceId(), _skill.getId(), _skill.getLevel(), baseline.skillId(), baseline.skillLevel(), _created, _admittedNanos,
			_executedNanos, TacticalObservationV3.hash(_attempt.observation().bytes()));
	}

	private void publishRecharge()
	{
		if (!auditArmed() || _auditObserver.recharges() == null) { return; }
		if (_auditInexact || _rechargeFinishedNanos == 0 || _mp <= 0 || !current())
		{
			reportAuditFailure(_auditObserver, "NATIVE_RECHARGE_CAST_UNPROVEN");
			return;
		}
		try
		{
			final var receipt = new NativeRechargeReceipt(nativeCastTrace(eventKey()), _attempt.observation(), _actionOwner.identity(),
				_rechargeFinishedNanos, _rechargeWriteNanos, _mpStart, _mpEnd, _beforeRechargeMp, _afterRechargeMp, _mp);
			if (auditArmed()) { _auditObserver.recharges().accept(receipt); }
		}
		catch (RuntimeException | Error unavailable) { auditFailure("NATIVE_RECHARGE_RECEIPT_UNAVAILABLE"); }
	}

	private void publishNativeAudit(String key)
	{
		if (!auditArmed() || _actor == _target) { return; }
		if (_auditInexact || _offeredNanos == 0 || _admittedNanos == 0 || _executedNanos == 0)
		{
			auditFailure("NATIVE_HEAL_CAST_UNPROVEN");
			return;
		}
		final var observation = _attempt.observation();
		try
		{
			final var cast = nativeCastTrace(key);
			final var receipt = new NativeHealReceipt(cast, observation, _actionOwner.identity(), _offeredNanos, _hpWriteNanos, _mpStart, _mpEnd,
				_hp > 0 ? _beforeHp : 0, _hp > 0 ? _afterHp : 0, _offeredHp, _requestedHp, _mp);
			if (auditArmed()) { _auditObserver.heals().accept(receipt); }
		}
		catch (RuntimeException | Error unavailable) { auditFailure("NATIVE_HEAL_RECEIPT_UNAVAILABLE"); }
	}

	private void auditFailure(String reason)
	{
		if (!auditArmed() || _actor == _target || _mp <= 0) { return; }
		reportAuditFailure(_auditObserver, reason);
	}

	private static void reportAuditFailure(AuditObserver observer, String reason)
	{
		if (observer == null || observer != AUDIT.get()) { return; }
		try { observer.failures().accept(reason); }
		catch (RuntimeException | Error unavailable) { /* Optional AUDIT diagnostics never affect the native action. */ }
	}

	public void cancelled()
	{
		synchronized (this)
		{
			if (_published) { return; }
			_published = true;
		}
		if (recharge()) { auditFailure("NATIVE_RECHARGE_CANCELLED_AFTER_MP"); }
		failure("CAST_CANCELLED", false);
	}

	private void failure(String reason, boolean completed)
	{
		if (recharge()) { return; }
		final double hp, mp;
		synchronized (this)
		{
			if (_failurePublished) { return; }
			_failurePublished = true;
			hp = _hp;
			mp = _mp;
		}
		try
		{
			final String key = TacticalObservationV3.hash(("HEAL_FAILURE/" + _attempt.observation().profileId() + '/' + _attempt.selected().generation() + '/' + _created).getBytes(StandardCharsets.UTF_8));
			_attempt.failureSink().accept(new TacticalFailureV3(key,new TacticalReceiptV3.Attempt(_attempt.observation(),_attempt.offered(),_attempt.selected(),_attempt.baseline(),_attempt.modelVersion()),
				reason,completed,completed ? hp : null,mp > 0 ? mp : null,Math.max(1,System.nanoTime()-_created),_actionOwner == null ? null : _actionOwner.identity()));
		}
		catch (RuntimeException unavailable) { REJECTED.increment(); }
	}
	/** Useful fractional HP minus real resource/time costs. No invented prevented-death or counterfactual bonus. */
	public static float reward(double hp, double maximumHp, double mp, long nanos)
	{
		if (!Double.isFinite(hp) || !Double.isFinite(maximumHp) || !Double.isFinite(mp) || hp <= 0 || maximumHp <= 0 || hp > maximumHp || mp <= 0 || nanos <= 0) { throw new IllegalArgumentException("Invalid native outcome"); }
		return (float) Math.clamp(hp / maximumHp - mp / 1000.0 - nanos / 100_000_000_000.0, -1, 1);
	}
	private static <T> void restore(ThreadLocal<T> local, T old) { if (old == null) { local.remove(); } else { local.set(old); } }
}

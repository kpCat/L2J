/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.skill.Skill;

/** Exact passive native writers carried by one real MagicUseTask. Never changes HP, MP or cast validity. */
public final class HealEvidenceV3
{
	public record Attempt(TacticalObservationV3 observation, List<LegalActionV3> offered, LegalActionV3 selected, LegalActionV3 baseline, long modelVersion, Consumer<Receipt> sink, Consumer<TacticalFailureV3> failureSink)
	{
		public Attempt(TacticalObservationV3 observation, List<LegalActionV3> offered, LegalActionV3 selected, LegalActionV3 baseline, long version, Consumer<Receipt> sink)
		{ this(observation,offered,selected,baseline,version,sink,_ -> { }); }
		public Attempt
		{
			offered = List.copyOf(offered);
			if (offered.isEmpty() || offered.size() > 16 || !offered.contains(selected) || !offered.contains(baseline) || !selected.trainable() || selected.family() != LegalActionV3.Family.HEAL || sink == null || failureSink == null)
			{ throw new IllegalArgumentException("HEAL attempt must bind offered lawful actions"); }
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
	private static final boolean TRAIN = Boolean.getBoolean("phantom.learning.train");
	private static final ThreadLocal<Attempt> REQUEST = new ThreadLocal<>();
	private static final ThreadLocal<HealEvidenceV3> DISPATCH = new ThreadLocal<>(), HIT = new ThreadLocal<>(), EFFECT = new ThreadLocal<>();
	private static final ThreadLocal<HealEvidenceV3> HP_EFFECT = new ThreadLocal<>();
	private record Authority(SourceOwner identity, BooleanSupplier current) { }
	private static final ThreadLocal<Authority> OWNER = new ThreadLocal<>();
	public static final LongAdder ACCEPTED = new LongAdder(), REJECTED = new LongAdder();
	private final Attempt _attempt;
	private final Player _actor, _target;
	private final Skill _skill;
	private final PlayerNativeWork.Owner _owner, _targetOwner;
	private final Party _party;
	private final BooleanSupplier _authority;
	private final Authority _actionOwner;
	private final long _created = System.nanoTime();
	private boolean _bound, _issued, _effects, _finished, _published, _invalid, _failurePublished;
	private double _hp, _mp, _beforeHp = Double.NaN, _afterHp;
	private long _hpStart,_hpEnd,_mpStart,_mpEnd;
	private HealEvidenceV3(Attempt attempt, Player actor, Player target, Skill skill, BooleanSupplier authority)
	{
		_attempt = attempt; _actor = actor; _target = target; _skill = skill; _authority = authority;
		_owner = actor.getNativeWorkOwner(); _targetOwner = target.getNativeWorkOwner(); _party = actor.getParty();
		_actionOwner = OWNER.get();
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
		if (!TRAIN || attempt == null || attempt.selected().actorObjectId() != actor.getObjectId() || attempt.selected().targetObjectId() != target.getObjectId()
			|| attempt.selected().skillId() != skill.getId() || attempt.selected().skillLevel() != skill.getLevel()) { return nativeCast.getAsBoolean(); }
		final HealEvidenceV3 ticket = new HealEvidenceV3(attempt, actor, target, skill, authority);
		if (System.nanoTime() - attempt.observation().capturedNanos() > 1_000_000_000L || !ticket.current()) { REJECTED.increment(); ticket.failure("STALE_AT_DISPATCH",false); return false; }
		final HealEvidenceV3 previous = DISPATCH.get(); DISPATCH.set(ticket);
		try { final boolean issued = nativeCast.getAsBoolean(); synchronized (ticket) { ticket._issued = issued; if (!issued || !ticket._bound) { ticket.failure("NO_NATIVE_TASK",false); } ticket.publish(); } return issued; }
		catch (RuntimeException | Error unavailable) { ticket.cancelled(); throw unavailable; }
		finally { restore(DISPATCH, previous); }
	}
	public static HealEvidenceV3 capture(Creature actor, Skill skill, List<WorldObject> targets, boolean simultaneous)
	{
		if (!TRAIN) { return null; }
		final HealEvidenceV3 ticket = DISPATCH.get();
		if (ticket == null || ticket._bound || simultaneous || actor != ticket._actor || skill != ticket._skill || targets == null || targets.size() != 1 || targets.getFirst() != ticket._target || !ticket.current()) { return null; }
		ticket._bound = true; return ticket;
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
			final var outer = EFFECT.get(); final var outerHp = HP_EFFECT.get(); EFFECT.remove(); HP_EFFECT.remove();
			try { nativeEffects.run(); } finally { restore(EFFECT, outer); restore(HP_EFFECT, outerHp); }
			return;
		}
		boolean valid;
		synchronized (ticket)
		{
			valid = !ticket._effects && actor == ticket._actor && skill == ticket._skill && targets.size() == 1 && targets.getFirst() == ticket._target && ticket.current();
			ticket._effects = true; if (!valid) { ticket._invalid = true; }
		}
		final HealEvidenceV3 old = EFFECT.get(), oldHp = HP_EFFECT.get(); restore(EFFECT, valid ? ticket : null); HP_EFFECT.remove();
		try { nativeEffects.run(); synchronized (ticket) { ticket._finished = valid; ticket.publish(); } }
		finally { restore(EFFECT, old); restore(HP_EFFECT, oldHp); }
	}
	/** Called inside the existing status monitor, after the actual successful HP assignment. */
	public static boolean observingEffects() { return EFFECT.get() != null; }
	/** Only the selected skill's exact instant HEAL handler can attribute a status write. Nested skills clear this scope. */
	public static void nativeEffect(Creature actor, Creature target, Skill skill, org.l2jmobius.gameserver.model.effects.EffectType type, Runnable nativeEffect)
	{
		final var ticket = EFFECT.get(); final var previous = HP_EFFECT.get();
		final boolean exact = ticket != null && actor == ticket._actor && target == ticket._target && skill == ticket._skill
			&& type == org.l2jmobius.gameserver.model.effects.EffectType.HEAL;
		restore(HP_EFFECT, exact ? ticket : null);
		try { if(exact&&ticket._hpStart==0){ticket._hpStart=System.nanoTime();}nativeEffect.run(); }
		finally { if(exact){ticket._hpEnd=System.nanoTime();}restore(HP_EFFECT, previous); }
	}
	public static void failedEffect() { final var ticket = EFFECT.get(); if (ticket != null) { synchronized (ticket) { ticket._invalid = true; } } }

	public static void hpWritten(Creature recipient, double before, double after, boolean regeneration)
	{
		final HealEvidenceV3 ticket = HP_EFFECT.get();
		if (ticket == null || regeneration || recipient != ticket._target || !Double.isFinite(before) || !Double.isFinite(after) || after <= before) { return; }
		synchronized (ticket)
		{
			if (!ticket.current() || ticket._published) { ticket._invalid = true; return; }
			if (ticket._hp > 0) { ticket._invalid = true; return; }
			ticket._beforeHp = before; ticket._afterHp = after; ticket._hp = after - before;
		}
	}
	/** Native MP debits during this exact useMagic / task, never a later MP read. */
	public static void mpWritten(Creature actor, double before, double after, boolean regeneration)
	{
		HealEvidenceV3 ticket = DISPATCH.get(); if (ticket == null) { ticket = HIT.get(); } if (ticket == null) { ticket = EFFECT.get(); }
		if (ticket == null || regeneration || actor != ticket._actor || !Double.isFinite(before) || !Double.isFinite(after) || before <= after) { return; }
		synchronized (ticket) { if (ticket.current() && !ticket._published) { ticket._mp += before - after;final long stamp=System.nanoTime();if(ticket._mpStart==0){ticket._mpStart=stamp;}ticket._mpEnd=stamp; } else { ticket._invalid = true; } }
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
	private void publish()
	{
		if (!_issued || !_finished || _published) { return; }
		_published = true;
		if (_hp <= 0 || _mp <= 0 || !current()) { REJECTED.increment(); failure(_hp <= 0 ? "NO_EXACT_HP" : _mp <= 0 ? "NO_EXACT_MP" : "NATIVE_AUTHORITY_CHANGED",true); return; }
		final long elapsed = Math.max(1, System.nanoTime() - _created);
		final double maximum = _attempt.observation().entity(_target.getObjectId()).fact(TacticalObservationV3.Field.MAX_HP).value();
		if (maximum <= 0) { REJECTED.increment(); failure("UNKNOWN_MAXIMUM_HP",true); return; }
		final String key = TacticalObservationV3.hash((_attempt.observation().partyIdentity() + '/' + _actor.getObjectId() + '/' + _owner.epoch() + '/' + _target.getObjectId()
			+ '/' + _targetOwner.epoch() + '/' + _attempt.selected().generation() + '/' + _actionOwner.identity().generation() + '/' + _actionOwner.identity().operationKey() + '/' + _skill.getId() + '/' + _created).getBytes(StandardCharsets.UTF_8));
		final float reward = reward(_hp, maximum, _mp, elapsed);
		try { TerminalRewardReceiptV1.nativeTiming(new TerminalRewardReceiptV1.NativeTiming(key,_actor.getInstanceId(),_hpStart,_hpEnd,_mpStart,_mpEnd,false));_attempt.sink().accept(new Receipt(key, _attempt, _beforeHp, _afterHp, _hp, _mp, elapsed, reward, _actionOwner.identity())); ACCEPTED.increment(); }
		catch (RuntimeException unavailable) { REJECTED.increment(); failure("RECEIPT_REJECTED",true); }
	}
	public synchronized void cancelled() { if (!_published) { failure("CAST_CANCELLED",false); _published = true; } }
	private synchronized void failure(String reason, boolean completed)
	{
		if (_failurePublished) { return; } _failurePublished = true;
		try
		{
			final String key = TacticalObservationV3.hash(("HEAL_FAILURE/" + _attempt.observation().profileId() + '/' + _attempt.selected().generation() + '/' + _created).getBytes(StandardCharsets.UTF_8));
			_attempt.failureSink().accept(new TacticalFailureV3(key,new TacticalReceiptV3.Attempt(_attempt.observation(),_attempt.offered(),_attempt.selected(),_attempt.baseline(),_attempt.modelVersion()),
				reason,completed,completed ? _hp : null,_mp > 0 ? _mp : null,Math.max(1,System.nanoTime()-_created),_actionOwner == null ? null : _actionOwner.identity()));
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

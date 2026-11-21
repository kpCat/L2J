/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.skill.Skill;

/** Observation only. A ticket travels with the actual native MagicUseTask, never with a later target-HP read. */
public final class LearningCastEvidence
{
	private static final boolean TRAIN = Boolean.getBoolean("phantom.learning.train");
	private static final ThreadLocal<LearningCastEvidence> DISPATCH = new ThreadLocal<>();
	private static final ThreadLocal<LearningCastEvidence> HIT = new ThreadLocal<>();
	private static final ThreadLocal<LearningCastEvidence> EFFECT = new ThreadLocal<>();
	private static final ThreadLocal<LearningCastEvidence> DAMAGE = new ThreadLocal<>();
	private static final ThreadLocal<LearningCastEvidence> MP = new ThreadLocal<>();
	private static final AtomicReference<AuditObserver> AUDIT = new AtomicReference<>();
	private static final class RunIdentity
	{
		private static final String SALT = UUID.randomUUID().toString();
		private static final java.util.concurrent.atomic.AtomicInteger REJECTIONS = new java.util.concurrent.atomic.AtomicInteger();
	}
	public record PvpPoolWrite(double before, double after, long firstWrite, long lastWrite)
	{
		public PvpPoolWrite
		{
			if (!Double.isFinite(before) || !Double.isFinite(after) || before <= after || after < 0 || firstWrite <= 0 || lastWrite < firstWrite)
			{ throw new IllegalArgumentException("Invalid PvP pool write"); }
		}
		public double delta() { return before - after; }
	}

	/** Additive terminal witness for one exact paid native action. Zero credit fields mean no exact stock credit. */
	public record NativeTerminal(String eventKey, int actorObjectId, long actorEpoch, int targetObjectId, long targetEpoch, int instanceId,
		long corpseGeneration, long deathNanos, long killCreditNanos, long killSequence, long damageNanos)
	{
		public NativeTerminal
		{
			if (eventKey == null || !eventKey.matches("[a-f0-9]{64}") || actorObjectId <= 0 || targetObjectId <= 0 || actorObjectId == targetObjectId
				|| actorEpoch <= 0 || targetEpoch <= 0 || instanceId < 0 || corpseGeneration < 0 || damageNanos <= 0 || deathNanos < damageNanos
				|| killSequence < 0 || (killSequence == 0 ? killCreditNanos != 0 : corpseGeneration != 0 || killCreditNanos < deathNanos))
			{ throw new IllegalArgumentException("Invalid native terminal evidence"); }
		}
	}

	/** Paid native cast admission and effect-entry timestamps for independent AUDIT replay. */
	public record NativeCastTrace(String eventKey, int actorObjectId, long actorEpoch, int targetObjectId, long targetEpoch, int instanceId,
		int skillId, int skillLevel, int baselineSkillId, int baselineSkillLevel, long decisionNanos, long admittedNanos, long executedNanos,
		String observationSha)
	{
		public NativeCastTrace
		{
			if (eventKey == null || !eventKey.matches("[a-f0-9]{64}") || observationSha == null || !observationSha.matches("[a-f0-9]{64}")
				|| actorObjectId <= 0 || targetObjectId <= 0 || actorObjectId == targetObjectId || actorEpoch <= 0 || targetEpoch <= 0 || instanceId < 0
				|| skillId <= 0 || skillLevel <= 0 || baselineSkillId <= 0 || baselineSkillLevel <= 0 || decisionNanos <= 0
				|| admittedNanos < decisionNanos || executedNanos < admittedNanos)
			{ throw new IllegalArgumentException("Invalid native cast trace"); }
		}
	}

	/** Explicit owned TEST collector. Paid observers and existing receipt formats do not depend on it. */
	public static AutoCloseable observeNativeAudit(Consumer<NativeCastTrace> casts, Consumer<NativeTerminal> terminals)
	{
		if (!TRAIN) { throw new IllegalStateException("Training opt-in required"); }
		final var observer = new AuditObserver(Objects.requireNonNull(casts), Objects.requireNonNull(terminals));
		if (!AUDIT.compareAndSet(null, observer)) { throw new IllegalStateException("Native AUDIT observer already installed"); }
		return observer;
	}

	private record AuditObserver(Consumer<NativeCastTrace> casts, Consumer<NativeTerminal> terminals) implements AutoCloseable
	{
		@Override public void close() { AUDIT.compareAndSet(this, null); }
	}

	/** Exact paid PvP effect. A pool without a native debit is absent. */
	public record PvpReceipt(String eventKey, TacticalReceiptV3.Attempt attempt, PvpPoolWrite hp, PvpPoolWrite cp, double mp,
		long mpFirstWrite, long mpLastWrite, long elapsedNanos, int instanceId, boolean lethal, HealEvidenceV3.SourceOwner owner)
	{
		public PvpReceipt
		{
			if (eventKey == null || !eventKey.matches("[a-f0-9]{64}") || attempt == null || owner == null || hp == null && cp == null
				|| !Double.isFinite(mp) || mp <= 0 || elapsedNanos <= 0 || instanceId < 0 || mpLastWrite < mpFirstWrite || lethal && hp == null)
			{ throw new IllegalArgumentException("Invalid native PvP receipt"); }
			final var observation = attempt.observation();
			final var target = observation.entity(attempt.selected().targetObjectId());
			if (attempt.selected().family() != LegalActionV3.Family.DAMAGE || target == null || target.role() != TacticalObservationV3.Role.ENEMY
				|| mpFirstWrite < observation.capturedNanos()
				|| !matchesPool(hp, target.fact(TacticalObservationV3.Field.MAX_HP), observation.capturedNanos())
				|| !matchesPool(cp, target.fact(TacticalObservationV3.Field.MAX_CP), observation.capturedNanos()))
			{ throw new IllegalArgumentException("PvP effect differs from the admitted target pools"); }
		}

		private static boolean matchesPool(PvpPoolWrite write, TacticalObservationV3.Fact maximum, long observed)
		{
			return write == null || maximum.known() && maximum.value() > 0 && write.before() <= maximum.value() + 1e-6 && write.firstWrite() >= observed;
		}
	}

	private final LearningPolicy.Attempt _attempt;
	private final Player _actor;
	private final Creature _target;
	private final Skill _skill;
	private final PlayerNativeWork.Owner _owner;
	private final PlayerNativeEvidence _nativeEvidence;
	private final long _epoch;
	private final Party _party;
	private final PlayerNativeEvidence.Target _spawn;
	private final PlayerNativeWork.Owner _pvpOwner;
	private final String _pvpOpponentParty;
	private final BooleanSupplier _leaseValid;
	private final AuditObserver _auditObserver;
	private final long _created = System.nanoTime();
	private final double _maxHp;
	private boolean _bound;
	private boolean _immediatePublished;
	private boolean _issued;
	private boolean _completed;
	private boolean _published;
	private boolean _effectsStarted;
	private boolean _effectsFinished;
	private boolean _failed;
	private boolean _failurePublished;
	private double _damage;
	private long _hpStart,_hpEnd,_mpStart,_mpEnd;
	private boolean _lethal;
	private double _mp, _beforeHp = Double.NaN, _afterHp;
	private PvpPoolWrite _pvpHp;
	private PvpPoolWrite _pvpCp;
	private long _admittedNanos, _executedNanos;
	private long _nativeDamageNanos, _nativeDeathNanos, _nativeCorpseGeneration, _nativeKillCreditNanos, _nativeKillSequence;
	private String _captureRejection = "no-native-task";

	private LearningCastEvidence(LearningPolicy.Attempt attempt, Player actor, Creature target, Skill skill, BooleanSupplier leaseValid)
	{
		_attempt = attempt;
		_actor = actor;
		_target = target;
		_skill = skill;
		_owner = actor.getNativeWorkOwner();
		_nativeEvidence = PlayerNativeWork.observationEvidence(actor);
		_epoch = _owner == null ? 0 : _owner.epoch();
		_party = actor.getParty();
		_spawn = target instanceof Monster monster ? monster.getNativeEvidenceTarget() : null;
		_pvpOwner = target instanceof Player opponent ? opponent.getNativeWorkOwner() : null;
		_pvpOpponentParty = target instanceof Player opponent ? pvpPartyIdentity(opponent) : null;
		_leaseValid = leaseValid;
		_auditObserver = attempt.tactical() == null ? null : AUDIT.get();
		_maxHp = target.getMaxHp();
	}

	public static void dispatch(LearningPolicy.Attempt attempt, Player actor, Monster target, Skill skill, BooleanSupplier leaseValid, Runnable nativeDispatch)
	{
		dispatchMonster(attempt, actor, target, skill, leaseValid, nativeDispatch, false);
	}

	public static boolean dispatchImmediateSolo(LearningPolicy.Attempt attempt, Player actor, Monster target, Skill skill,
		BooleanSupplier leaseValid, Runnable nativeDispatch)
	{
		if (!TRAIN || !archivedSolo(attempt) || DISPATCH.get() != null) { return false; }
		return dispatchMonster(attempt, actor, target, skill, leaseValid, nativeDispatch, true);
	}

	private static boolean archivedSolo(LearningPolicy.Attempt attempt)
	{
		return attempt.tactical() != null && attempt.tactical().observation().stockSelection() instanceof TacticalObservationV3.StockSoloSelection;
	}

	/** The existing native CAST guards can only see this exact writer while it submits synchronously. */
	public static boolean immediateSoloDispatch(Creature actor)
	{
		if (!TRAIN) { return false; }
		final var ticket = DISPATCH.get();
		return ticket != null && ticket._actor == actor && archivedSolo(ticket._attempt);
	}

	private static boolean dispatchMonster(LearningPolicy.Attempt attempt, Player actor, Monster target, Skill skill,
		BooleanSupplier leaseValid, Runnable nativeDispatch, boolean immediate)
	{
		final LearningCastEvidence previous = DISPATCH.get();
		final LearningCastEvidence ticket = new LearningCastEvidence(attempt, actor, target, skill, leaseValid);
		if (immediate && ticket.lifetimeRejection() != null) { return false; }
		final var priorSkill = immediate ? actor.getCurrentSkill() : null;
		attempt.policy().observeIssued(attempt, actor, target);
		DISPATCH.set(ticket);
		try
		{
			nativeDispatch.run();
			if (immediate && !ticket._immediatePublished) { ticket.cancelled(); }
			else { ticket.issued(); }
		}
		catch (RuntimeException | Error unavailable) { ticket.cancelled(); throw unavailable; }
		finally
		{
			try
			{
				// Match the unsubmitted scoped AoE cleanup. Preserve any previously owned cast.
				if (immediate && !ticket._immediatePublished && actor.getNativeWorkOwner() == ticket._owner && actor.hasAI())
				{
					final var current = actor.getCurrentSkill();
					if (current != null && current != priorSkill && current.getSkill() == skill && actor.getAI().getIntention() == Intention.CAST)
					{
						actor.getAI().setIntention(Intention.ACTIVE);
					}
				}
			}
			finally { restore(DISPATCH, previous); }
		}
		return ticket._immediatePublished;
	}

	public void immediatePublished() { _immediatePublished = true; }

	public static boolean dispatchPvp(LearningPolicy.Attempt attempt, Player actor, Player opponent, Skill skill, BooleanSupplier leaseValid, BooleanSupplier nativeDispatch)
	{
		if (!TRAIN || attempt == null || attempt.tactical() == null || attempt.request().learningScope() != org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest.LearningScope.PVP)
		{ return false; }
		final var observation = attempt.tactical().observation();
		final var selected = attempt.tactical().selected();
		if (actor == null || opponent == null || skill == null || actor.getKnownSkill(skill.getId()) != skill
			|| observation.actorObjectId() != actor.getObjectId() || observation.actorEpoch() != TacticalSnapshotV3.epoch(actor)
			|| observation.profileId() != attempt.request().profileId() || attempt.request().targetObjectId() != opponent.getObjectId()
			|| selected.family() != LegalActionV3.Family.DAMAGE || selected.skillId() != skill.getId() || selected.skillLevel() != skill.getLevel())
		{ return false; }
		final var ticket = new LearningCastEvidence(attempt, actor, opponent, skill, leaseValid);
		if (ticket.lifetimeRejection() != null) { return false; }
		final var previous = DISPATCH.get();
		attempt.policy().observePvpIssued(attempt, actor, opponent);
		DISPATCH.set(ticket);
		try
		{
			final boolean accepted = nativeDispatch.getAsBoolean();
			if (accepted) { ticket.issued(); }
			else { ticket.cancelled(); }
			return accepted;
		}
		catch (RuntimeException | Error rejected) { ticket.cancelled(); throw rejected; }
		finally { restore(DISPATCH, previous); }
	}

	public static LearningCastEvidence capture(Creature actor, Skill skill, List<WorldObject> targets, boolean simultaneous)
	{
		if (!TRAIN) { return null; }
		final LearningCastEvidence ticket = DISPATCH.get();
		if (ticket == null || ticket._bound)
		{
			return null;
		}
		final String rejection = simultaneous ? "simultaneous" : !ticket.matches(actor, skill, targets)
			? "actor-skill-or-target" : ticket.lifetimeRejection();
		if (rejection != null)
		{
			ticket._captureRejection = rejection;
			return null;
		}
		ticket._bound = true;
		if (ticket.auditArmed()) { ticket._admittedNanos = System.nanoTime(); }
		return ticket;
	}

	private boolean matches(Creature actor, Skill skill, List<WorldObject> targets)
	{
		return actor == _actor && skill == _skill && targets != null && targets.size() == 1 && targets.getFirst() == _target;
	}

	private String lifetimeRejection()
	{
		if (!_attempt.policy().active(_attempt.request().learningScope())) { return "policy"; }
		if (_owner == null || _nativeEvidence == null || _actor.getNativeWorkOwner() != _owner || _owner.player() != _actor
			|| !_owner.isCurrent() || _owner.epoch() != _epoch || PlayerNativeWork.observationEvidence(_actor) != _nativeEvidence)
		{ return "actor-epoch"; }
		if (!_leaseValid.getAsBoolean()) { return "execution-lease"; }
		if (_actor.getParty() != _party) { return "native-party"; }
		if (World.getInstance().findObject(_target.getObjectId()) != _target || _target.getInstanceId() != _actor.getInstanceId()) { return "target-spawn"; }
		if (_target instanceof Monster monster)
		{
			if (!_spawn.equals(monster.getNativeEvidenceTarget())) { return "target-spawn"; }
		}
		else if (!(_target instanceof Player opponent) || _pvpOwner == null || opponent == _actor || opponent.getParty() != null && opponent.getParty() == _actor.getParty()
			|| opponent.getNativeWorkOwner() != _pvpOwner || _pvpOwner.player() != opponent || !_pvpOwner.isCurrent()
			|| PlayerNativeWork.observationEvidence(opponent) == null || _attempt.tactical().selected().targetObjectId() != opponent.getObjectId()
			|| _attempt.tactical().selected().targetEpoch() != _pvpOwner.epoch()) { return "target-epoch"; }
		else if (_pvpOpponentParty == null || !_attempt.tactical().observation().partyIdentity().equals(pvpPartyIdentity(_actor))
			|| !_pvpOpponentParty.equals(pvpPartyIdentity(opponent))) { return "pvp-party-membership"; }
		return System.nanoTime() - _created < 15_000_000_000L ? null : "age";
	}

	private static String pvpPartyIdentity(Player actor)
	{
		try { return TacticalSnapshotV3.partyIdentity(actor); }
		catch (RuntimeException changed) { return null; }
	}

	public void hit(Runnable nativeHit)
	{
		final LearningCastEvidence previous = HIT.get();
		HIT.set(this);
		try
		{
			nativeHit.run();
			if (_effectsFinished)
			{
				complete();
			}
		}
		finally
		{
			restore(HIT, previous);
		}
	}

	public static LearningCastEvidence takeHit()
	{
		if (!TRAIN) { return null; }
		final LearningCastEvidence ticket = HIT.get();
		if (ticket != null) { HIT.remove(); }
		return ticket;
	}

	public static void effects(LearningCastEvidence ticket, Creature actor, Skill skill, List<WorldObject> targets, Runnable nativeEffects)
	{
		final LearningCastEvidence previous = EFFECT.get();
		if (ticket == null && previous == null)
		{
			nativeEffects.run();
			return;
		}
		LearningCastEvidence current = null;
		if (ticket != null)
		{
			final String rejection = ticket.matches(actor, skill, targets) ? ticket.lifetimeRejection() : "actor-skill-or-target";
			if (rejection == null && ticket.beginEffects())
			{
				current = ticket;
			}
			else
			{
				ticket.witnessRejection("effects", rejection == null ? "duplicate" : rejection);
			}
		}
		restore(EFFECT, current);
		try
		{
			nativeEffects.run();
			if (current != null)
			{
				current._effectsFinished = true;
			}
		}
		finally
		{
			restore(EFFECT, previous);
		}
	}

	private synchronized boolean beginEffects()
	{
		if (_effectsStarted)
		{
			return false;
		}
		_effectsStarted = true;
		if (auditArmed()) { _executedNanos = System.nanoTime(); }
		return true;
	}

	public static boolean observingEffects()
	{
		return TRAIN && EFFECT.get() != null;
	}

	public static void failedEffect()
	{
		if (!TRAIN) { return; }
		final LearningCastEvidence ticket = EFFECT.get();
		if (ticket != null) { ticket._failed = true; }
	}

	public static void directDamage(Creature target, Creature attacker, Skill skill, boolean dot, Runnable write)
	{
		final LearningCastEvidence previous = DAMAGE.get();
		final LearningCastEvidence ticket = EFFECT.get();
		restore(DAMAGE, ticket != null && target == ticket._target && attacker == ticket._actor && skill == ticket._skill && !dot ? ticket : null);
		try
		{
			if(DAMAGE.get()!=null){final long stamp=System.nanoTime();if(ticket._hpStart==0){ticket._hpStart=stamp;}}
			write.run();
		}
		finally
		{
			if(DAMAGE.get()!=null){ticket._hpEnd=System.nanoTime();}
			restore(DAMAGE, previous);
		}
	}
	/** Synchronous Creature.doDie observer binds a lethal write to this exact native damage scope. */
	public static void terminalDeath(Creature target)
	{final var ticket=DAMAGE.get();if(ticket!=null&&ticket==EFFECT.get()&&target==ticket._target){ticket._lethal=true;}}

	/** Called immediately after the stock dead-state write, under the Creature monitor. */
	public static long nativeDeathStamp(Creature target, Creature killer)
	{
		final var ticket = terminalTicket(target, killer);
		return ticket != null && ticket.exactLethalDamage() ? System.nanoTime() : 0;
	}

	private boolean exactLethalDamage()
	{
		return _pvpOwner == null ? _nativeDamageNanos > 0 && _afterHp < 0.5 : _pvpHp != null && _pvpHp.after() < 0.5;
	}

	/** Only the non-vetoed death can become a terminal witness for the paid cast. */
	public static void nativeDeathCommitted(Creature target, Creature killer, long stamp, long corpseGeneration)
	{
		if (stamp <= 0) { return; }
		final var ticket = terminalTicket(target, killer);
		if (ticket == null || ticket._nativeDeathNanos != 0 || !target.isDead() || !ticket.terminalLifetimeCurrent()) { return; }
		if (ticket._pvpOwner == null ? ticket._spawn == null || corpseGeneration != 0
			: corpseGeneration <= 0 || ((Player) target).nativeCorpseGeneration() != corpseGeneration) { return; }
		ticket._nativeDeathNanos = stamp;
		ticket._nativeCorpseGeneration = corpseGeneration;
	}

	/** Compare the exact stock writer's receipts while it owns the sensor monitor. */
	public static void creditNativeKill(Creature victim, Player killer, PlayerNativeEvidence evidence, PlayerNativeEvidence.Target target)
	{
		final var ticket = terminalTicket(victim, killer);
		if (ticket == null || ticket._nativeDeathNanos <= 0 || ticket._nativeKillSequence != 0 || evidence != ticket._nativeEvidence
			|| !target.equals(ticket._spawn) || !ticket.terminalLifetimeCurrent())
		{
			evidence.killedIfDamaged(target);
			return;
		}
		synchronized (evidence)
		{
			final long before = evidence.targetReceipts().killSequence();
			evidence.killedIfDamaged(target);
			final var after = evidence.targetReceipts();
			if (after.killSequence() > before && after.killSequence() - before == 1 && target.equals(after.killTarget()))
			{
				ticket._nativeKillCreditNanos = System.nanoTime();
				ticket._nativeKillSequence = after.killSequence();
			}
		}
	}

	private static LearningCastEvidence terminalTicket(Creature target, Creature killer)
	{
		if (!TRAIN || AUDIT.get() == null) { return null; }
		final var ticket = DAMAGE.get();
		return ticket != null && ticket == EFFECT.get() && target == ticket._target && killer == ticket._actor && !ticket._published
			&& ticket.auditArmed() ? ticket : null;
	}

	private boolean auditArmed() { return _auditObserver != null && _auditObserver == AUDIT.get(); }

	private boolean terminalLifetimeCurrent()
	{
		try { return lifetimeRejection() == null; }
		catch (RuntimeException unavailable) { return false; }
	}

	public static void actualDamage(Player attacker, PlayerNativeWork.Owner owner, PlayerNativeEvidence.Target target, double hp)
	{ actualDamage(attacker, owner, target, hp, Double.NaN, Double.NaN); }
	public static void actualDamage(Player attacker, PlayerNativeWork.Owner owner, PlayerNativeEvidence.Target target, double hp, double before, double after)
	{
		if (!TRAIN) { return; }
		final LearningCastEvidence ticket = DAMAGE.get();
		if (ticket != null && ticket == EFFECT.get() && attacker == ticket._actor && owner == ticket._owner && target.equals(ticket._spawn) && hp > 0 && Double.isFinite(hp))
		{
			if (ticket.auditArmed() && Double.isFinite(before) && Double.isFinite(after)) { ticket._nativeDamageNanos = System.nanoTime(); }
			ticket._damage += hp;
			if (Double.isNaN(ticket._beforeHp)) { ticket._beforeHp = before; }
			ticket._afterHp = after;
		}
	}

	public static void pvpHpWritten(Player target, Creature attacker, double before, double after)
	{
		pvpPoolWritten(target, attacker, before, after, true);
	}

	public static void pvpCpWritten(Player target, Creature attacker, double before, double after)
	{
		pvpPoolWritten(target, attacker, before, after, false);
	}

	public static void pvpPoolInterfered(Player target, Creature attacker)
	{
		final var ticket = pvpDamageTicket(target, attacker);
		if (ticket != null) { ticket._failed = true; }
	}

	private static LearningCastEvidence pvpDamageTicket(Player target, Creature attacker)
	{
		if (!TRAIN) { return null; }
		final var ticket = DAMAGE.get();
		return ticket != null && ticket == EFFECT.get() && ticket._pvpOwner != null && ticket._target == target && ticket._actor == attacker
			&& target.getNativeWorkOwner() == ticket._pvpOwner && ticket._pvpOwner.isCurrent() && ticket._actor.getNativeWorkOwner() == ticket._owner
			&& ticket._owner.isCurrent() && !ticket._published ? ticket : null;
	}

	private static void pvpPoolWritten(Player target, Creature attacker, double before, double after, boolean hp)
	{
		if (!Double.isFinite(before) || !Double.isFinite(after) || before <= after || after < 0) { return; }
		final var ticket = pvpDamageTicket(target, attacker);
		if (ticket == null) { return; }
		final PvpPoolWrite previous = hp ? ticket._pvpHp : ticket._pvpCp;
		if (previous != null && previous.after() != before)
		{
			ticket._failed = true;
			return;
		}
		final long now = System.nanoTime();
		final var write = new PvpPoolWrite(previous == null ? before : previous.before(), after, previous == null ? now : previous.firstWrite(), now);
		if (hp)
		{
			ticket._pvpHp = write;
			ticket._beforeHp = write.before();
			ticket._afterHp = write.after();
			ticket._damage = write.delta();
		}
		else { ticket._pvpCp = write; }
	}
	public void mpDebit(Runnable nativeDebit)
	{
		final var previous = MP.get(); MP.set(this);
		try { nativeDebit.run(); } finally { restore(MP, previous); }
	}
	public static void mpWritten(Creature actor, double before, double after, boolean regeneration)
	{
		if (!TRAIN || regeneration || !Double.isFinite(before) || !Double.isFinite(after) || before <= after) { return; }
		LearningCastEvidence ticket = MP.get(); if (ticket == null) { ticket = DISPATCH.get(); }
		if (ticket != null && ticket._attempt.tactical() != null && actor == ticket._actor && !ticket._published && ticket.lifetimeRejection() == null)
		{ ticket._mp += before - after;final long stamp=System.nanoTime();if(ticket._mpStart==0){ticket._mpStart=stamp;}ticket._mpEnd=stamp; }
	}

	private void issued()
	{
		synchronized (this)
		{
			_issued = true;
			if (!_bound)
			{
				_attempt.policy().observationRejected();
				witnessRejection("capture", _captureRejection);
				failure("NO_NATIVE_TASK",false);
			}
			if (!claimPublication()) { return; }
		}
		publish();
	}

	private void complete()
	{
		synchronized (this)
		{
			_completed = true;
			if (!claimPublication()) { return; }
		}
		publish();
	}

	/** The caller owns the ticket monitor and releases it before invoking subscribers. */
	private boolean claimPublication()
	{
		if (!_issued || !_completed || _published) { return false; }
		_published = true;
		return true;
	}

	private void publish()
	{
		try
		{
			final String rejection = publicationRejection();
			if (rejection != null)
			{
				_attempt.policy().observationRejected();
				witnessRejection("publish", rejection);
				failure(rejection.toUpperCase(java.util.Locale.ROOT).replace('-','_'),true);
				return;
			}
			final var action = _attempt.candidate();
			final float reward = (float) Math.clamp((_damage / Math.max(1, _maxHp)) * 8 - action.mpCost() / 1000 - action.castMillis() / 30000.0 - action.itemCost() * .005, -1, 1);
			final String actorKey = _attempt.policy().actorKey(_attempt.request().profileId());
			final String targetIdentity = _pvpOwner == null ? _spawn.toString() : _target.getObjectId() + "@" + _pvpOwner.epoch();
			final String eventKey = hash(RunIdentity.SALT + ":" + actorKey + ":" + _actor.getObjectId() + ":" + _epoch + ":" + _attempt.generation()
				+ ":" + _attempt.sequence() + ":" + _attempt.request().learningScope() + ":" + targetIdentity);
			final var evidence = new LearningStore.Evidence(eventKey, actorKey, _attempt.request().learningScope().name(),
				_attempt.policy().version(), _skill.getId(), _skill.getLevel(), _damage, reward, action.features());
			if (_attempt.tactical() != null)
			{
				final var tactical = _attempt.tactical();
				final long elapsed = Math.max(1, System.nanoTime() - _created);
				final var owner = new HealEvidenceV3.SourceOwner(_attempt.generation(),
					"Combat/" + _attempt.request().profileId() + "/" + _attempt.generation() + "/" + _attempt.sequence());
				if (_pvpOwner == null)
				{
					TerminalRewardReceiptV1.nativeTiming(new TerminalRewardReceiptV1.NativeTiming(eventKey,_actor.getInstanceId(),_hpStart,_hpEnd,_mpStart,_mpEnd,_lethal));
				}
				else
				{
					final var paid = new PvpReceipt(eventKey, tactical, _pvpHp, _pvpCp, _mp, _mpStart, _mpEnd, elapsed, _actor.getInstanceId(), _lethal, owner);
					publishNativeAudit(eventKey);
					_attempt.policy().observePvpPaid(paid);
					if (_pvpHp == null) { return; }
				}
				final var paid = new TacticalReceiptV3(eventKey, tactical, _beforeHp, _afterHp, _damage, _mp, elapsed,
					TacticalReceiptV3.reward(_damage, _maxHp, _mp, elapsed), owner);
				if (_pvpOwner == null) { publishNativeAudit(eventKey); }
				_attempt.policy().recordTactical(paid);
				return;
			}
			final boolean enqueued = _attempt.policy().record(evidence);
			if (enqueued) { _attempt.policy().observeRecorded(_attempt, evidence); }
			if (enqueued && _attempt.policy().witnessing())
			{
				logExactReorder(eventKey);
			}
		}
		catch (RuntimeException rejectedObservation)
		{
			_attempt.policy().observationRejected();
			witnessRejection("publish", rejectedObservation.getClass().getSimpleName());
			failure("RECEIPT_REJECTED",true);
		}
	}

	private void publishNativeAudit(String eventKey)
	{
		if (!auditArmed() || _admittedNanos <= 0 || _executedNanos <= 0) { return; }
		final boolean pvp = _pvpOwner != null;
		final long targetEpoch = pvp ? _pvpOwner.epoch() : _spawn.spawnGeneration();
		final var tactical = _attempt.tactical();
		final var selected = tactical.selected();
		final var baseline = tactical.baseline();
		final var observation = tactical.observation();
		if (selected.targetObjectId() != _target.getObjectId() || selected.targetEpoch() != targetEpoch || selected.skillId() != _skill.getId()
			|| selected.skillLevel() != _skill.getLevel() || _created < observation.capturedNanos()) { return; }
		try
		{
			final var trace = new NativeCastTrace(eventKey, _actor.getObjectId(), _epoch, _target.getObjectId(), targetEpoch, _actor.getInstanceId(),
				selected.skillId(), selected.skillLevel(), baseline.skillId(), baseline.skillLevel(), _created, _admittedNanos, _executedNanos,
				TacticalObservationV3.hash(observation.bytes()));
			if (auditArmed()) { _auditObserver.casts().accept(trace); }
		}
		catch (RuntimeException | Error unavailable) { /* The optional observer cannot affect the existing paid callbacks. */ }
		publishNativeTerminal(eventKey, targetEpoch, pvp);
	}

	private void publishNativeTerminal(String eventKey, long targetEpoch, boolean pvp)
	{
		if (!auditArmed() || _nativeDeathNanos <= 0) { return; }
		final long damageNanos = pvp ? _pvpHp == null ? 0 : Math.max(_pvpHp.lastWrite(), _pvpCp == null ? 0 : _pvpCp.lastWrite()) : _nativeDamageNanos;
		if (damageNanos <= 0 || damageNanos > _nativeDeathNanos || (!pvp && (damageNanos < _hpStart || damageNanos > _hpEnd))) { return; }
		try
		{
			_auditObserver.terminals().accept(new NativeTerminal(eventKey, _actor.getObjectId(), _epoch, _target.getObjectId(), targetEpoch, _actor.getInstanceId(),
				_nativeCorpseGeneration, _nativeDeathNanos, _nativeKillCreditNanos, _nativeKillSequence, damageNanos));
		}
		catch (RuntimeException | Error unavailable) { /* The optional witness cannot affect the existing paid callbacks. */ }
	}

	public synchronized void cancelled() { if (_attempt.tactical() != null && !_published) { failure("CAST_CANCELLED",false); _published = true; } }
	private synchronized void failure(String reason, boolean completed)
	{
		if (_attempt.tactical() == null || _failurePublished) { return; } _failurePublished = true;
		try
		{
			final String key = hash(RunIdentity.SALT + "/DAMAGE_FAILURE/" + _actor.getObjectId() + '/' + _epoch + '/' + _attempt.generation() + '/' + _attempt.sequence() + '/' + _created);
			_attempt.policy().recordTacticalFailure(new TacticalFailureV3(key,_attempt.tactical(),reason,completed,completed ? _damage : null,_mp > 0 ? _mp : null,
				Math.max(1,System.nanoTime()-_created),new HealEvidenceV3.SourceOwner(_attempt.generation(),"Combat/" + _attempt.request().profileId() + '/' + _attempt.generation() + '/' + _attempt.sequence())));
		}
		catch (RuntimeException unavailable) { _attempt.policy().observationRejected(); }
	}

	private String publicationRejection()
	{
		if (_failed) { return "native-effect"; }
		if (_damage <= 0 && _pvpCp == null) { return "no-exact-hp"; }
		if (_pvpOwner != null && _mp <= 0) { return "no-exact-mp"; }
		final String lifetime = lifetimeRejection();
		if (lifetime != null) { return lifetime; }
		return _attempt.request().receiptCancellation().isCancelled() ? "receipt-owner" : null;
	}

	private void witnessRejection(String stage, String reason)
	{
		if (_attempt.policy().witnessing() && RunIdentity.REJECTIONS.getAndIncrement() < 24)
		{
			Logger.getLogger(LearningCastEvidence.class.getName()).info("LEARNING_CAST_REJECTED scope=" + _attempt.request().learningScope()
				+ " skill=" + _skill.getId() + " generation=" + _attempt.generation() + " sequence=" + _attempt.sequence()
				+ " stage=" + stage + " reason=" + reason);
		}
	}

	private void logExactReorder(String eventKey)
	{
		final var baseline = _attempt.baseline();
		final var scope = _attempt.request().learningScope();
		if ((baseline.skillId() != _skill.getId() || baseline.skillLevel() != _skill.getLevel()) && _attempt.policy().recordExactReorder(scope))
		{
			final String targetKey = hash(RunIdentity.SALT + ":target:" + _spawn);
			Logger.getLogger(LearningCastEvidence.class.getName()).info("LEARNING_EXACT_REORDER scope=" + scope + " version=" + _attempt.policy().version()
				+ " baseline=" + baseline.skillId() + ":" + baseline.skillLevel() + " actual=" + _skill.getId() + ":" + _skill.getLevel()
				+ " epoch=" + _epoch + " generation=" + _attempt.generation() + " sequence=" + _attempt.sequence()
				+ " targetSpawn=" + _spawn.spawnGeneration() + " targetKey=" + targetKey + " eventKey=" + eventKey + " hp=" + _damage
				+ " decisionNanos=" + _attempt.decisionNanos());
		}
	}

	private static String hash(String input)
	{
		return NeuralPolicy.sha256(input.getBytes(StandardCharsets.UTF_8));
	}

	private static void restore(ThreadLocal<LearningCastEvidence> local, LearningCastEvidence previous)
	{
		if (previous == null) { local.remove(); }
		else { local.set(previous); }
	}
}

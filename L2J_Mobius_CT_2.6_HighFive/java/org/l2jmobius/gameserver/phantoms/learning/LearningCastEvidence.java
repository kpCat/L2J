/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

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
	private static final class RunIdentity
	{
		private static final String SALT = UUID.randomUUID().toString();
		private static final java.util.concurrent.atomic.AtomicInteger REJECTIONS = new java.util.concurrent.atomic.AtomicInteger();
	}
	private final LearningPolicy.Attempt _attempt;
	private final Player _actor;
	private final Monster _target;
	private final Skill _skill;
	private final PlayerNativeWork.Owner _owner;
	private final PlayerNativeEvidence _nativeEvidence;
	private final long _epoch;
	private final Party _party;
	private final PlayerNativeEvidence.Target _spawn;
	private final BooleanSupplier _leaseValid;
	private final long _created = System.nanoTime();
	private final double _maxHp;
	private boolean _bound;
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
	private String _captureRejection = "no-native-task";

	private LearningCastEvidence(LearningPolicy.Attempt attempt, Player actor, Monster target, Skill skill, BooleanSupplier leaseValid)
	{
		_attempt = attempt;
		_actor = actor;
		_target = target;
		_skill = skill;
		_owner = actor.getNativeWorkOwner();
		_nativeEvidence = PlayerNativeWork.observationEvidence(actor);
		_epoch = _owner == null ? 0 : _owner.epoch();
		_party = actor.getParty();
		_spawn = target.getNativeEvidenceTarget();
		_leaseValid = leaseValid;
		_maxHp = target.getMaxHp();
	}

	public static void dispatch(LearningPolicy.Attempt attempt, Player actor, Monster target, Skill skill, BooleanSupplier leaseValid, Runnable nativeDispatch)
	{
		final LearningCastEvidence previous = DISPATCH.get();
		final LearningCastEvidence ticket = new LearningCastEvidence(attempt, actor, target, skill, leaseValid);
		attempt.policy().observeIssued(attempt, actor, target);
		DISPATCH.set(ticket);
		try
		{
			nativeDispatch.run();
			ticket.issued();
		}
		catch (RuntimeException | Error unavailable) { ticket.cancelled(); throw unavailable; }
		finally
		{
			restore(DISPATCH, previous);
		}
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
		if (World.getInstance().findObject(_target.getObjectId()) != _target || !_spawn.equals(_target.getNativeEvidenceTarget())
			|| _spawn.instanceId() != _actor.getInstanceId()) { return "target-spawn"; }
		return System.nanoTime() - _created < 15_000_000_000L ? null : "age";
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

	public static void actualDamage(Player attacker, PlayerNativeWork.Owner owner, PlayerNativeEvidence.Target target, double hp)
	{ actualDamage(attacker, owner, target, hp, Double.NaN, Double.NaN); }
	public static void actualDamage(Player attacker, PlayerNativeWork.Owner owner, PlayerNativeEvidence.Target target, double hp, double before, double after)
	{
		if (!TRAIN) { return; }
		final LearningCastEvidence ticket = DAMAGE.get();
		if (ticket != null && ticket == EFFECT.get() && attacker == ticket._actor && owner == ticket._owner && target.equals(ticket._spawn) && hp > 0 && Double.isFinite(hp))
		{
			ticket._damage += hp;
			if (Double.isNaN(ticket._beforeHp)) { ticket._beforeHp = before; }
			ticket._afterHp = after;
		}
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

	private synchronized void issued()
	{
		_issued = true;
		if (!_bound)
		{
			_attempt.policy().observationRejected();
			witnessRejection("capture", _captureRejection);
			failure("NO_NATIVE_TASK",false);
		}
		publish();
	}

	private synchronized void complete()
	{
		_completed = true;
		publish();
	}

	private void publish()
	{
		if (!_issued || !_completed || _published)
		{
			return;
		}
		_published = true;
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
			final String eventKey = hash(RunIdentity.SALT + ":" + actorKey + ":" + _actor.getObjectId() + ":" + _epoch + ":" + _attempt.generation()
				+ ":" + _attempt.sequence() + ":" + _attempt.request().learningScope() + ":" + _spawn);
			final var evidence = new LearningStore.Evidence(eventKey, actorKey, _attempt.request().learningScope().name(),
				_attempt.policy().version(), _skill.getId(), _skill.getLevel(), _damage, reward, action.features());
			if (_attempt.tactical() != null)
			{
				final var tactical = _attempt.tactical();
				final long elapsed = Math.max(1, System.nanoTime() - _created);
				TerminalRewardReceiptV1.nativeTiming(new TerminalRewardReceiptV1.NativeTiming(eventKey,_actor.getInstanceId(),_hpStart,_hpEnd,_mpStart,_mpEnd,_lethal));
				_attempt.policy().recordTactical(new TacticalReceiptV3(eventKey, tactical, _beforeHp, _afterHp, _damage, _mp, elapsed,
					TacticalReceiptV3.reward(_damage, _maxHp, _mp, elapsed), new HealEvidenceV3.SourceOwner(_attempt.generation(),
						"Combat/" + _attempt.request().profileId() + "/" + _attempt.generation() + "/" + _attempt.sequence())));
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
		if (_damage <= 0) { return "no-exact-hp"; }
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

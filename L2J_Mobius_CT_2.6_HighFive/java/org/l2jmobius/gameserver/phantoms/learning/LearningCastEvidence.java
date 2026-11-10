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
	private static final class RunIdentity
	{
		private static final String SALT = UUID.randomUUID().toString();
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
	private double _damage;

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
		DISPATCH.set(ticket);
		try
		{
			nativeDispatch.run();
			ticket.issued();
		}
		finally
		{
			restore(DISPATCH, previous);
		}
	}

	public static LearningCastEvidence capture(Creature actor, Skill skill, List<WorldObject> targets, boolean simultaneous)
	{
		if (!TRAIN) { return null; }
		final LearningCastEvidence ticket = DISPATCH.get();
		if (ticket == null || simultaneous || ticket._bound || !ticket.matches(actor, skill, targets) || !ticket.exactLifetime())
		{
			return null;
		}
		ticket._bound = true;
		return ticket;
	}

	private boolean matches(Creature actor, Skill skill, List<WorldObject> targets)
	{
		return actor == _actor && skill == _skill && targets != null && targets.size() == 1 && targets.getFirst() == _target;
	}

	private boolean exactLifetime()
	{
		return _attempt.policy().active(_attempt.request().learningScope()) && _owner != null && _nativeEvidence != null
			&& _actor.getNativeWorkOwner() == _owner && _owner.player() == _actor && _owner.isCurrent() && _owner.epoch() == _epoch
			&& PlayerNativeWork.observationEvidence(_actor) == _nativeEvidence && _leaseValid.getAsBoolean()
			&& _actor.getParty() == _party && World.getInstance().findObject(_target.getObjectId()) == _target
			&& _spawn.equals(_target.getNativeEvidenceTarget()) && _spawn.instanceId() == _actor.getInstanceId()
			&& System.nanoTime() - _created < 15_000_000_000L;
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
		final LearningCastEvidence current = ticket != null && ticket.matches(actor, skill, targets) && ticket.exactLifetime() && ticket.beginEffects() ? ticket : null;
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
			write.run();
		}
		finally
		{
			restore(DAMAGE, previous);
		}
	}

	public static void actualDamage(Player attacker, PlayerNativeWork.Owner owner, PlayerNativeEvidence.Target target, double hp)
	{
		if (!TRAIN) { return; }
		final LearningCastEvidence ticket = DAMAGE.get();
		if (ticket != null && ticket == EFFECT.get() && attacker == ticket._actor && owner == ticket._owner && target.equals(ticket._spawn) && hp > 0 && Double.isFinite(hp))
		{
			ticket._damage += hp;
		}
	}

	private synchronized void issued()
	{
		_issued = true;
		if (!_bound)
		{
			_attempt.policy().observationRejected();
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
			if (_failed || _damage <= 0 || !exactLifetime() || _attempt.request().receiptCancellation().isCancelled())
			{
				_attempt.policy().observationRejected();
				return;
			}
			final var action = _attempt.candidate();
			final float reward = (float) Math.clamp((_damage / Math.max(1, _maxHp)) * 8 - action.mpCost() / 1000 - action.castMillis() / 30000.0 - action.itemCost() * .005, -1, 1);
			final String actorKey = _attempt.policy().actorKey(_attempt.request().profileId());
			final String eventKey = hash(RunIdentity.SALT + ":" + actorKey + ":" + _actor.getObjectId() + ":" + _epoch + ":" + _attempt.generation()
				+ ":" + _attempt.sequence() + ":" + _attempt.request().learningScope() + ":" + _spawn);
			final boolean enqueued = _attempt.policy().record(new LearningStore.Evidence(eventKey, actorKey, _attempt.request().learningScope().name(),
				_attempt.policy().version(), _skill.getId(), _skill.getLevel(), _damage, reward, action.features()));
			if (enqueued && _attempt.policy().witnessing())
			{
				logExactReorder(eventKey);
			}
		}
		catch (RuntimeException rejectedObservation)
		{
			_attempt.policy().observationRejected();
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

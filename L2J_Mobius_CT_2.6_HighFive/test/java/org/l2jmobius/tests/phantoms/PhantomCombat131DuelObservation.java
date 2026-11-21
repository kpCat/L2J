/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.effects.EffectType;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.player.OnPlayerPvPKill;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort.AdmissionRejectedException;
import org.l2jmobius.tests.phantoms.TrainingEvaluation131.Outcome;

/** N06 observations of stock arena combat for two currently owned native lifetimes. */
final class PhantomCombat131DuelObservation implements AutoCloseable
{
	record Pool(double initial, double last, double minimum, double maximum)
	{
		Pool observe(double value)
		{
			return new Pool(initial, value, Math.min(minimum, value), Math.max(maximum, value));
		}
	}

	record Control(int skillId, int rank, EffectType effect, long firstSeenNanos, long lastSeenNanos) { }
	record SideSnapshot(int objectId, long epoch, Pool hp, Pool cp, Pool mp, Map<Integer, Long> initialInventory,
		Map<Integer, Long> lastInventory, List<Control> controls) { }
	record NativeDeath(int killerObjectId, long killerEpoch, int victimObjectId, long victimEpoch, long corpseGeneration, long observedNanos) { }
	record Result(int instanceId, int samples, long firstSampleNanos, long lastSampleNanos, SideSnapshot left, SideSnapshot right,
		List<NativeDeath> nativeDeaths, Optional<Outcome> outcome) { }
	private record ControlKey(int skillId, int rank, EffectType effect) { }

	private final Participant _left;
	private final Participant _right;
	private final int _instance;
	private final ConsumerEventListener _leftListener;
	private final ConsumerEventListener _rightListener;
	private final Map<Integer, NativeDeath> _deaths = new HashMap<>(2);
	private long _firstSample;
	private long _lastSample;
	private int _samples;
	private boolean _invalid;
	private boolean _closed;

	PhantomCombat131DuelObservation(Player left, Player right)
	{
		require(left != right && left.getObjectId() != right.getObjectId() && left.getInstanceId() == right.getInstanceId(), "OBSERVE_PAIR");
		_left = new Participant(left);
		_right = new Participant(right);
		_instance = left.getInstanceId();
		require(bothCurrent() && !left.isAlikeDead() && !right.isAlikeDead(), "OBSERVE_CURRENT_PAIR");
		_leftListener = listener(_left, _right);
		_rightListener = listener(_right, _left);
		sample();
		try
		{
			left.addListener(_leftListener);
			right.addListener(_rightListener);
		}
		catch (RuntimeException failure)
		{
			close();
			throw failure;
		}
	}

	synchronized void sample()
	{
		require(!_closed && !_invalid && bothCurrent(), "OBSERVE_CURRENT_PAIR");
		final long now = System.nanoTime();
		_left.observe(_right, now);
		_right.observe(_left, now);
		if (_samples++ == 0) { _firstSample = now; }
		_lastSample = now;
	}

	synchronized boolean hasNativeDeath() { return !_invalid && !_deaths.isEmpty(); }

	synchronized Result closeAfterDrain()
	{
		require(!_closed && !_invalid && _left.drained() && _right.drained(), "OBSERVE_DRAIN");
		final var deaths = _deaths.values().stream().sorted(Comparator.comparingLong(NativeDeath::observedNanos)
			.thenComparingInt(NativeDeath::victimObjectId)).toList();
		final Optional<Outcome> outcome = deaths.isEmpty() ? Optional.empty()
			: Optional.of(deaths.size() == 2 ? Outcome.DRAW : _deaths.containsKey(_left.player.getObjectId()) ? Outcome.LOSS : Outcome.WIN);
		final var result = new Result(_instance, _samples, _firstSample, _lastSample, _left.snapshot(), _right.snapshot(), deaths, outcome);
		close();
		return result;
	}

	private boolean bothCurrent()
	{
		return _left.current() && _right.current() && _left.player.getInstanceId() == _instance && _right.player.getInstanceId() == _instance
			&& World.getInstance().getPlayers().size() == 2;
	}

	private ConsumerEventListener listener(Participant victim, Participant killer)
	{
		return new ConsumerEventListener(victim.player, EventType.ON_PLAYER_PVP_KILL,
			(OnPlayerPvPKill event) -> stockDeath(event, victim, killer), this);
	}

	private void stockDeath(OnPlayerPvPKill event, Participant victim, Participant killer)
	{
		final long corpse = victim.player.nativeCorpseGeneration();
		final long now = System.nanoTime();
		synchronized (this)
		{
			if (_closed || _invalid) { return; }
			if (event.getTarget() != victim.player || event.getPlayer() != killer.player || corpse <= 0 || now < _firstSample
				|| !bothCurrent() || !earned(victim.owner) || !earned(killer.owner))
			{
				_invalid = true;
				return;
			}
			final var death = new NativeDeath(killer.player.getObjectId(), killer.owner.epoch(), victim.player.getObjectId(),
				victim.owner.epoch(), corpse, now);
			if (_deaths.putIfAbsent(death.victimObjectId(), death) != null) { _invalid = true; }
		}
	}

	private static boolean earned(PlayerNativeWork.Owner owner)
	{
		final var work = PlayerNativeWork.current(owner);
		return work != null && work.semantics() == PlayerNativeWork.Semantics.EARNED;
	}

	@Override
	public synchronized void close()
	{
		if (_closed) { return; }
		_closed = true;
		_left.player.removeListener(_leftListener);
		_right.player.removeListener(_rightListener);
	}

	private static final class Participant
	{
		final Player player;
		final PlayerNativeWork.Owner owner;
		private Pool _hp;
		private Pool _cp;
		private Pool _mp;
		private Map<Integer, Long> _initialInventory;
		private Map<Integer, Long> _lastInventory;
		private final Map<ControlKey, Control> _controls = new HashMap<>();

		Participant(Player player)
		{
			this.player = player;
			owner = player.getNativeWorkOwner();
		}

		boolean current()
		{
			return owner != null && owner.epoch() > 0 && player.getNativeWorkOwner() == owner && owner.player() == player && owner.isCurrent()
				&& player.getClient() == null && !player.isGM() && PlayerNativeWork.observationEvidence(player) != null;
		}

		boolean drained()
		{
			final int id = player.getObjectId();
			return !owner.isCurrent() && player.getNativeWorkOwner() == null && World.getInstance().getPlayer(id) == null
				&& World.getInstance().findObject(id) == null && PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(id) == null;
		}

		void observe(Participant opponent, long now)
		{
			_hp = pool(_hp, player.getCurrentHp());
			_cp = pool(_cp, player.getCurrentCp());
			_mp = pool(_mp, player.getCurrentMp());
			final var inventory = new HashMap<Integer, Long>();
			for (var item : player.getInventory().getItems())
			{
				final long count = item.getCount();
				if (count > 0) { inventory.merge(item.getId(), count, Math::addExact); }
			}
			_lastInventory = Map.copyOf(inventory);
			if (_initialInventory == null) { _initialInventory = _lastInventory; }

			for (var info : player.getEffectList().getEffects())
			{
				final var skill = info.getSkill();
				if (info.getEffected() != player || info.getEffector() != opponent.player || !info.isInUse() || info.isRemoved()
					|| info.getTime() <= 0 || opponent.player.getKnownSkill(skill.getId()) != skill) { continue; }
				for (var effect : info.getEffects())
				{
					final EffectType type = effect.getEffectType();
					if (activeControl(type))
					{
						final var key = new ControlKey(skill.getId(), skill.getLevel(), type);
						_controls.compute(key, (_, prior) -> new Control(key.skillId(), key.rank(), type, prior == null ? now : prior.firstSeenNanos(), now));
					}
				}
			}
		}

		private boolean activeControl(EffectType type)
		{
			return switch (type)
			{
				case STUN -> player.isStunned();
				case PARALYZE -> player.isParalyzed();
				case ROOT -> player.isRooted();
				case MUTE -> player.isMuted() || player.isPhysicalMuted();
				default -> false;
			};
		}

		SideSnapshot snapshot()
		{
			final var controls = _controls.values().stream().sorted(Comparator.comparingInt(Control::skillId)
				.thenComparingInt(Control::rank).thenComparing(Control::effect)).toList();
			return new SideSnapshot(player.getObjectId(), owner.epoch(), _hp, _cp, _mp, _initialInventory, _lastInventory, controls);
		}
	}

	private static Pool pool(Pool previous, double value)
	{
		require(Double.isFinite(value) && value >= 0, "OBSERVE_POOL");
		return previous == null ? new Pool(value, value, value, value) : previous.observe(value);
	}

	private static void require(boolean condition, String reason)
	{
		if (!condition) { throw new AdmissionRejectedException("TASK131_N06_" + reason); }
	}
}

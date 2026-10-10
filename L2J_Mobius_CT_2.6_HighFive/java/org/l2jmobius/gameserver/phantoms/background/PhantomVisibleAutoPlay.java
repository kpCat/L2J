/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.skill.AbnormalType;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.targets.TargetType;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.PhantomPolicy;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickLease;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickAdmission;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickStatus;
import org.l2jmobius.gameserver.taskmanagers.AutoUseTaskManager;

/** Runs the existing AutoPlay and AutoUse pools only while an ordinary visible goal owns the materialized Player. */
public final class PhantomVisibleAutoPlay implements PhantomMaterializationLifecyclePort
{
	private static final Logger LOGGER = Logger.getLogger(PhantomVisibleAutoPlay.class.getName());
	private static final long RECOVERY_DELAY_NANOS = 30_000_000_000L;
	private static final long STALL_DELAY_NANOS = 90_000_000_000L;
	private static final long RESOURCE_RECOVERY_NANOS = 45_000_000_000L;
	private final PhantomMaterializationService _materialization;
	private final Supplier<PhantomDecisionEngine> _decision;
	private final LongPredicate _permitsOrdinary;
	private final LongPredicate _permitsNewRoots;
	private final Map<Long, Session> _sessions = new ConcurrentHashMap<>();
	private final LongSupplier _clock;
	private final Object[] _sessionOwners = java.util.stream.IntStream.range(0, 64).mapToObj(_ -> new Object()).toArray();

	public PhantomVisibleAutoPlay(PhantomMaterializationService materialization, Supplier<PhantomDecisionEngine> decision, LongPredicate permitsOrdinary)
	{
		this(materialization, decision, permitsOrdinary, System::nanoTime);
	}

	public PhantomVisibleAutoPlay(PhantomMaterializationService materialization, Supplier<PhantomDecisionEngine> decision, LongPredicate permitsOrdinary, LongSupplier clock)
	{
		this(materialization, decision, permitsOrdinary, _ -> true, clock);
	}

	public PhantomVisibleAutoPlay(PhantomMaterializationService materialization, Supplier<PhantomDecisionEngine> decision, LongPredicate permitsOrdinary, LongPredicate permitsNewRoots, LongSupplier clock)
	{
		_materialization = Objects.requireNonNull(materialization, "materialization");
		_decision = Objects.requireNonNull(decision, "decision");
		_permitsOrdinary = Objects.requireNonNull(permitsOrdinary, "permitsOrdinary");
		_permitsNewRoots = Objects.requireNonNull(permitsNewRoots, "permitsNewRoots");
		_clock = Objects.requireNonNull(clock);
	}

	public boolean start(long profileId, PhantomGoal goal)
	{
		final PhantomBackgroundGoalSpec spec;
		try
		{
			spec = PhantomBackgroundGoalSpec.parse(goal);
		}
		catch (IllegalArgumentException exception)
		{
			return false;
		}
		final var snapshot = _materialization.find(profileId).orElse(null);
		if ((snapshot == null) || (snapshot.state() != State.ACTIVE) || !snapshot.worldPresent())
		{
			return false;
		}
		final ActionLease action = _materialization.tryAcquireAction(profileId).orElse(null);
		if (action == null)
		{
			return false;
		}
		try (action)
		{
			final Player player = action.player();
			if (!player.hasHeadlessOutboundSession() || !player.isOnline() || player.isDead() || player.hasPendingOwnedStore() || (player.getObjectId() != snapshot.characterObjectId()) || !current(profileId, goal))
			{
				return false;
			}
			final Session previous = _sessions.get(profileId);
			if (healthy(profileId, goal, previous, player))
			{
				synchronized (owner(profileId))
				{
					if ((_sessions.get(profileId) == previous) && previous._current.get()) { return true; }
				}
			}
			if (!_permitsNewRoots.test(profileId)) { return false; }
			configure(player);
			final PhantomPolicy policy = new Policy(player, profileId, goal.goalId(), goal.revision(), spec.npcId());
			final Session session = new Session(player, goal.goalId(), goal.revision(), policy, snapshot.materializedAtNanos(), _clock.getAsLong(), previous);
			final Session replaced;
			synchronized (owner(profileId))
			{
				replaced = _sessions.put(profileId, session);
				if (replaced != null) { replaced._current.set(false); }
			}
			try
			{
				stopRegistrations(replaced);
				if (!AutoPlayTaskManager.getInstance().startPhantomAutoPlay(player, policy, session._current) || !AutoUseTaskManager.getInstance().startPhantomAutoUse(player, policy, session._current) || !healthy(profileId, goal, session, player))
				{
					stop(profileId, session);
					return false;
				}
			}
			catch (RuntimeException | Error exception)
			{
				try { stop(profileId, session); }
				catch (RuntimeException | Error secondary) { if (secondary != exception) { exception.addSuppressed(secondary); } }
				throw exception;
			}
			synchronized (owner(profileId))
			{
				return (_sessions.get(profileId) == session) && session._current.get();
			}
		}
	}

	public void stop(long profileId)
	{
		final Session session;
		synchronized (owner(profileId))
		{
			session = _sessions.remove(profileId);
			if (session != null) { session._current.set(false); }
		}
		stopRegistrations(session);
	}

	private void stop(long profileId, Session expected)
	{
		synchronized (owner(profileId))
		{
			_sessions.remove(profileId, expected);
			expected._current.set(false);
		}
		stopRegistrations(expected);
	}

	private static void stopRegistrations(Session session)
	{
		if (session == null) { return; }
		try { AutoUseTaskManager.getInstance().stopPhantomAutoUse(session.player(), session._policy); }
		finally { AutoPlayTaskManager.getInstance().stopPhantomAutoPlay(session.player(), session._policy); }
	}

	private Object owner(long profileId)
	{
		return _sessionOwners[(int) (profileId & 63)];
	}

	private boolean healthy(long profileId, PhantomGoal goal, Session session, Player player)
	{
		return (session != null) && session._current.get() && (_sessions.get(profileId) == session) && (session.player() == player) && (session.goalId() == goal.goalId()) && (session.revision() == goal.revision()) && player.isAutoPlaying() && player.hasHeadlessOutboundSession() && player.isOnline() && !player.isDead() && !player.hasPendingOwnedStore() && current(profileId, goal)
			&& AutoPlayTaskManager.getInstance().hasPhantomRegistration(player, session._policy) && AutoUseTaskManager.getInstance().hasPhantomRegistration(player, session._policy);
	}

	public boolean running(long profileId, PhantomGoal goal)
	{
		final Session session = _sessions.get(profileId);
		if (session == null) { return false; }
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			return (action != null) && healthy(profileId, goal, session, action.player()) && (_sessions.get(profileId) == session) && session._current.get();
		}
	}

	/** Target availability and flags cannot erase independently witnessed useful farm debt. */
	public boolean noTargetExpired(long profileId, PhantomGoal goal)
	{
		// Retirement waits for earned native completion; it must not launch another repair or local replan.
		if (!_permitsNewRoots.test(profileId)) { return false; }
		final Session session = _sessions.get(profileId);
		if ((session == null) || (session.goalId() != goal.goalId()) || (session.revision() != goal.revision()))
		{
			return false;
		}
		try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
		{
			if ((action == null) || !healthy(profileId, goal, session, action.player())) { return false; }
			final Player player = action.player();
			final int npcId = PhantomBackgroundGoalSpec.parse(goal).npcId();
			final boolean inFlight = player.isCastingNow() || player.isAttackingNow();
			if (resourcePause(session, player))
			{
				synchronized (session) { return session._resourceExpired || stalled(session, _clock.getAsLong()); }
			}
			final var progress = nativeProgress(player, session._epoch);
			final boolean repair;
			final boolean expired;
			final boolean reportStall;
			final String reason;
			synchronized (session)
			{
				if (!session._current.get() || (_sessions.get(profileId) != session)) { return false; }
				final long now = _clock.getAsLong();
				observeProgress(session, progress, now);
				if (now < session.nextTargetCheck)
				{
					return stalled(session, now);
				}
				session.nextTargetCheck = now + 1_000_000_000L;
				final var selected = player.getTarget();
				final boolean offensive = (player.isMoving() || player.isAttackingNow() || player.isCastingNow()) && (selected instanceof Creature creature) && selectableTarget(player, creature, npcId);
				final boolean target = offensive || World.getInstance().getVisibleObjectsInRange(player, Creature.class, player.getAutoPlaySettings().isShortRange() ? AutoPlayConfig.AUTO_PLAY_SHORT_RANGE : AutoPlayConfig.AUTO_PLAY_LONG_RANGE).stream().anyMatch(creature -> selectableTarget(player, creature, npcId));
				if (target) { session.noTargetSince = -1; }
				else if (session.noTargetSince < 0) { session.noTargetSince = now; }
				expired = stalled(session, now);
				repair = !expired && !inFlight && !session._repairAttempted && (((now - session._usefulSince) >= RECOVERY_DELAY_NANOS) || ((session.noTargetSince >= 0) && ((now - session.noTargetSince) >= RECOVERY_DELAY_NANOS)));
				if (repair) { session._repairAttempted = true; }
				reportStall = expired && !session._stallReported;
				if (reportStall) { session._stallReported = true; }
				reason = target ? "NO_USEFUL_NATIVE_FARM_PROGRESS" : "NO_SELECTABLE_NATIVE_FARM_TARGET";
			}
			if (repair && healthy(profileId, goal, session, player))
			{
				// One native repair, outside the Session monitor, never a registration restart or debt reset.
				player.abortAttack(); player.abortCast(); player.stopMove(null);
				player.getAI().setIntention(Intention.IDLE);
				if (!(player.getTarget() instanceof Creature selected) || !selectableTarget(player, selected, npcId)) { player.setTarget(null); }
				LOGGER.info("Phantom visible farm repair profile=" + profileId + " object=" + player.getObjectId() + " epoch=" + session._epoch + " reason=" + reason);
			}
			if (reportStall) { LOGGER.warning("Phantom visible farm stalled profile=" + profileId + " object=" + player.getObjectId() + " epoch=" + session._epoch + " reason=" + reason); }
			return expired;
		}
	}

	/** Exact session policy uses stock sit/stand and existing regeneration; useful farm debt is never renewed by rest. */
	private boolean resourcePause(Session session, Player player)
	{
		if (!player.getPlayerClass().isMage()) { return false; }
		if (player.isCastingNow() || player.isAttackingNow()) { return false; }
		final long now = _clock.getAsLong();
		synchronized (session)
		{
			if (!session._current.get() || _sessions.get(session._profileId) != session) { return false; }
			if (now < session._resourceCheckDue) { return session._recovering; }
			session._resourceCheckDue = now + 500_000_000L;
		}
		double cost = Double.POSITIVE_INFINITY;
		for (Integer skillId : player.getAutoUseSettings().getAutoSkills())
		{
			final Skill skill = player.getKnownSkill(skillId);
			if (skill == null || !skill.hasNegativeEffect() || !skill.isActive() || skill.isPassive() || skill.isToggle()) { continue; }
			if (skill.getItemConsumeCount() > 0 && player.getInventory().getInventoryItemCount(skill.getItemConsumeId(), -1) < skill.getItemConsumeCount()) { continue; }
			if (player.getCharges() < skill.getChargeConsumeCount()) { continue; }
			final double nativeCost = Math.max(skill.getMpInitialConsume() + skill.getMpConsume(), player.getStat().getMpInitialConsume(skill) + player.getStat().getMpConsume(skill));
			if (nativeCost > 0) { cost = Math.min(cost, nativeCost); }
		}
		if (!Double.isFinite(cost)) { return false; }
		final double mp = player.getCurrentMp();
		final double maximumMp = player.getMaxMp();
		final boolean sitting = player.isSitting();
		final boolean threatened = World.getInstance().getVisibleObjectsInRange(player, Creature.class, AutoPlayConfig.AUTO_PLAY_LONG_RANGE).stream()
			.anyMatch(creature -> creature.isAttackable() && !creature.isAlikeDead() && creature.getInstanceId() == player.getInstanceId() && creature.getTarget() == player);
		final boolean sit, stand, pause;
		synchronized (session)
		{
			if (!session._current.get()) { return false; }
			session._resourceCost = cost;
			if (!session._recovering && mp >= cost)
			{
				session._resourceSince = 0; session._resourceExpired = false;
				if (!sitting) { return false; }
				// A current policy may replace the one that began this actual native rest.
				session._recovering = true;
			}
			if (session._resourceSince == 0) { session._resourceSince = now; }
			session._resourceExpired = cost > maximumMp || now - session._resourceSince >= RESOURCE_RECOVERY_NANOS;
			if (threatened || session._resourceExpired)
			{
				stand = sitting; sit = false; pause = session._resourceExpired;
				session._recovering = false; session._resourceReason = threatened ? "resource.native_threat" : "resource.bounded_unavailable";
			}
			else if (session._recovering && mp >= Math.min(maximumMp, cost * 2))
			{
				stand = sitting; sit = false; pause = stand;
				session._recovering = stand; session._resourceReason = stand ? "resource.native_stand_pending" : "resource.affordable";
				if (!stand) { session._resourceSince = 0; session._resourceExpired = false; }
			}
			else
			{
				stand = false; sit = !sitting; pause = true;
				session._recovering = true; session._resourceReason = "resource.native_rest";
			}
		}
		if (stand) { player.standUp(); }
		if (sit) { player.sitDown(); }
		return pause;
	}

	private static PlayerNativeEvidence.Snapshot nativeProgress(Player player, long epoch)
	{
		final var owner = player.getNativeWorkOwner();
		if ((owner == null) || (owner.player() != player) || !owner.isCurrent() || (owner.epoch() != epoch)) { return null; }
		final var sensor = owner.evidence();
		if ((sensor == null) || !sensor.matches(player.getObjectId(), epoch)) { return null; }
		final var sample = sensor.snapshot();
		return !sample.overflow() && (sample.objectId() == player.getObjectId()) && (sample.epoch() == epoch) ? sample : null;
	}

	private static void observeProgress(Session session, PlayerNativeEvidence.Snapshot progress, long now)
	{
		if (progress == null) { return; }
		if (!session._nativeBaseline)
		{
			session.baseline(progress);
			return;
		}
		if ((progress.damageSequence() < session._damageSequence) || (progress.rewardSequence() < session._rewardSequence) || (progress.farmCycleSequence() < session._cycleSequence) || (progress.lootSequence() < session._lootSequence)) { return; }
		// A reward on an undamaged target is not useful; concurrent loot makes its timestamp ambiguous.
		final boolean reward = (progress.rewardSequence() > session._rewardSequence) && (progress.usefulProgressNanos() > session._nativeUsefulNanos) && (progress.lootSequence() == session._lootSequence);
		if ((progress.damageSequence() > session._damageSequence) || (progress.farmCycleSequence() > session._cycleSequence) || reward)
		{
			session._usefulSince = now;
			session._repairAttempted = false;
			session._stallReported = false;
			session._resourceSince = 0; session._resourceExpired = false;
		}
		session.baseline(progress);
	}

	private static boolean stalled(Session session, long now)
	{
		return ((now - session._usefulSince) >= STALL_DELAY_NANOS) || ((session.noTargetSince >= 0) && ((now - session.noTargetSince) >= STALL_DELAY_NANOS));
	}

	static boolean selectableTarget(Player player, Creature creature, int npcId)
	{
		if (!creature.isMonster() || creature.isRaid() || creature.isAlikeDead() || !creature.isTargetable() || creature.isInvul() || !creature.asNpc().isShowName() || !creature.isAutoAttackable(player) || (creature.getInstanceId() != player.getInstanceId()) || (creature.asNpc().getId() != npcId))
		{
			return false;
		}
		if (player.getAutoPlaySettings().isRespectfulHunting() && (creature.getTarget() != null) && (creature.getTarget() != player) && !(player.hasSummon() && (player.getSummon().getObjectId() == creature.getTarget().getObjectId())))
		{
			return false;
		}
		return (Math.abs((long) player.getZ() - creature.getZ()) < 800) && GeoEngine.getInstance().canSeeTarget(player, creature) && GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), creature.getX(), creature.getY(), creature.getZ(), player.getInstanceId());
	}

	private boolean current(long profileId, PhantomGoal goal)
	{
		final PhantomDecisionEngine engine = _decision.get();
		final var runtime = engine == null ? null : engine.find(profileId).orElse(null);
		return (runtime != null) && (runtime.goalId() == goal.goalId()) && (runtime.goalRevision() == goal.revision()) && (runtime.goalStatus() == PhantomGoalStatus.ACTIVE) && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(runtime.goalType()) && _permitsOrdinary.test(profileId);
	}

	private static void configure(Player player)
	{
		player.getAutoUseSettings().getAutoActions().clear();
		player.getAutoUseSettings().getAutoSkills().clear();
		player.getAutoUseSettings().getAutoBuffs().clear();
		player.getAutoUseSettings().getAutoSupplyItems().clear();
		player.getAutoUseSettings().setAutoPotionItem(0);
		player.getAutoPlaySettings().setNextTargetMode(1);
		player.getAutoPlaySettings().setShortRange(false);
		player.getAutoPlaySettings().setPickup(true);
		player.getAutoUseSettings().getAutoActions().add(2);
		if (AutoPlayConfig.ENABLE_AUTO_SKILL)
		{
			for (Skill skill : player.getAllSkills())
			{
				if (skill.isPassive() || skill.isToggle() || !skill.isActive() || AutoPlayConfig.DISABLED_AUTO_SKILLS.contains(skill.getId()))
				{
					continue;
				}
				if (skill.hasNegativeEffect())
				{
					player.getAutoUseSettings().getAutoSkills().add(skill.getId());
				}
				else if (autoBuffEligible(skill))
				{
					player.getAutoUseSettings().getAutoBuffs().add(skill.getId());
				}
			}
		}
		if (AutoPlayConfig.ENABLE_AUTO_ITEM || AutoPlayConfig.ENABLE_AUTO_POTION)
		{
			for (Item item : player.getInventory().getItems())
			{
				if (!item.isEtcItem() || !item.getTemplate().hasSkills() || AutoPlayConfig.DISABLED_AUTO_ITEMS.contains(item.getId()))
				{
					continue;
				}
				for (var holder : item.getTemplate().getSkills())
				{
					final Skill skill = holder.getSkill();
					if ((skill != null) && AutoPlayConfig.ENABLE_AUTO_POTION && (skill.getAbnormalType() == AbnormalType.HP_RECOVER) && (player.getAutoUseSettings().getAutoPotionItem() == 0))
					{
						player.getAutoUseSettings().setAutoPotionItem(item.getId());
					}
					else if ((skill != null) && AutoPlayConfig.ENABLE_AUTO_ITEM && skill.isContinuous() && (skill.getAbnormalType() != AbnormalType.HP_RECOVER))
					{
						player.getAutoUseSettings().getAutoSupplyItems().add(item.getId());
					}
				}
			}
		}
		if (player.getActiveWeaponItem() != player.getFistsWeaponItem())
		{
			player.getInventory().getItems().stream()
				.filter(item -> item.isEtcItem() && (item.getTemplate().getCrystalType() == player.getActiveWeaponItem().getCrystalTypePlus()) && ((item.getTemplate().getDefaultAction() == ActionType.SOULSHOT) || (item.getTemplate().getDefaultAction() == ActionType.SPIRITSHOT)))
				.sorted(Comparator.comparingInt(Item::getId))
				.forEach(item -> player.addAutoSoulShot(item.getId()));
			player.rechargeShots(true, true);
		}
	}

	public static boolean autoBuffEligible(Skill skill)
	{
		return (skill.getTargetType() == TargetType.SELF) && skill.isContinuous();
	}

	private final class Policy implements PhantomPolicy
	{
		private final Player _player;
		private final long _profileId;
		private final long _goalId;
		private final long _revision;
		private final int _npcId;
		private final java.util.concurrent.atomic.AtomicLong _tickSequence = new java.util.concurrent.atomic.AtomicLong();
		private volatile TickObservation _autoPlayTick;
		private volatile TickObservation _autoUseTick;
		private final java.util.concurrent.atomic.AtomicReference<StopObservation> _firstStop = new java.util.concurrent.atomic.AtomicReference<>();

		private Policy(Player player, long profileId, long goalId, long revision, int npcId)
		{
			_player = player;
			_profileId = profileId;
			_goalId = goalId;
			_revision = revision;
			_npcId = npcId;
		}

		@Override
		public TickAdmission acquireTick(Player player, String source)
		{
			final long sequence = _tickSequence.incrementAndGet(); final long started = System.nanoTime();
			publishTick(source, new TickObservation(sequence, started, 0, "acquiring"));
			final var originalOwner = player.getNativeWorkOwner();
			final boolean checkpointBefore = (originalOwner instanceof PhantomNativeWorkScope scope) && scope.temporaryCheckpoint();
			final TickLease lease = acquire(player);
			if (lease != null)
			{
				if (!_permitsNewRoots.test(_profileId))
				{
					lease.close(); publishTick(source, new TickObservation(sequence, started, System.nanoTime(), "locality_retire_paused"));
					return new TickAdmission(TickStatus.PAUSED, null, "locality_retire");
				}
				final Session session = _sessions.get(_profileId);
				final var combat = ((session == null) || (session._policy != this)) ? null : nativeProgress(player, session._epoch);
				if ((combat != null) && (combat.phase() == PlayerNativeEvidence.Phase.COMBAT)
					&& (combat.phaseSinceNanos() > 0) && (_clock.getAsLong() - combat.phaseSinceNanos() >= STALL_DELAY_NANOS))
				{
					// Stop only new pool roots; earned attack/cast work reaches its original passive completion.
					if (player.hasAI() && !player.isCastingNow() && !player.isCastingSimultaneouslyNow() && (player.getAI().getIntention() != Intention.IDLE))
					{
						// Stock IDLE stops the next attack intention; it does not cancel captured earned hit tickets.
						player.getAI().setIntention(Intention.IDLE);
					}
					lease.close();
					publishTick(source, new TickObservation(sequence, started, System.nanoTime(), "native_combat_settlement_paused"));
					return new TickAdmission(TickStatus.PAUSED, null, "native_combat_settlement");
				}
				if (session != null && session._policy == this && resourcePause(session, player))
				{
					lease.close(); publishTick(source, new TickObservation(sequence, started, System.nanoTime(), "resource_paused"));
					return new TickAdmission(TickStatus.PAUSED, null, "resource_recovery");
				}
				publishTick(source, new TickObservation(sequence, started, 0, "acquired"));
				return new TickAdmission(TickStatus.ACQUIRED, () -> { try { lease.close(); } finally { publishTick(source, new TickObservation(sequence, started, System.nanoTime(), "completed")); } }, "acquired");
			}
			final Session session = _sessions.get(_profileId);
			final var engine = _decision.get(); final var runtime = engine == null ? null : engine.find(_profileId).orElse(null);
			final boolean current = (player == _player) && (session != null) && (session._policy == this) && session._current.get() && player.isOnline() && player.hasHeadlessOutboundSession() && !player.isDead() && !player.hasPendingOwnedStore() && (runtime != null) && (runtime.goalId() == _goalId) && (runtime.goalRevision() == _revision) && (runtime.goalStatus() == PhantomGoalStatus.ACTIVE) && _permitsOrdinary.test(_profileId);
			final boolean paused = current && (player.getNativeWorkOwner() == originalOwner) && (originalOwner instanceof PhantomNativeWorkScope scope) && (scope.epoch() == session._epoch) && scope.checkpointPauseAllowed(checkpointBefore);
			final String reason = paused ? "checkpoint_paused" : "owner_or_goal_revoked";
			publishTick(source, new TickObservation(sequence, started, System.nanoTime(), reason));
			return new TickAdmission(paused ? TickStatus.PAUSED : TickStatus.REVOKED, null, reason);
		}
		private void publishTick(String source, TickObservation tick) { if ("AutoPlay".equals(source)) { _autoPlayTick = tick; } else { _autoUseTick = tick; } }
		@Override public void stopObserved(String reason) { _firstStop.compareAndSet(null, new StopObservation(System.nanoTime(), reason, Thread.currentThread().getName())); }

		@Override
		public TickLease acquire(Player player)
		{
			final Session session = _sessions.get(_profileId);
			if ((player != _player) || (session == null) || (session._policy != this) || !session._current.get())
			{
				return null;
			}
			final PhantomDecisionEngine engine = _decision.get();
			final var runtime = engine == null ? null : engine.find(_profileId).orElse(null);
			if ((runtime == null) || (runtime.goalId() != _goalId) || (runtime.goalRevision() != _revision) || (runtime.goalStatus() != PhantomGoalStatus.ACTIVE) || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(runtime.goalType()) || !_permitsOrdinary.test(_profileId))
			{
				return null;
			}
			final ActionLease action = _materialization.tryAcquireAction(_profileId).orElse(null);
			if (action == null)
			{
				return null;
			}
			if ((action.player() != player) || (_sessions.get(_profileId) != session) || !session._current.get() || (player.getNativeWorkOwner() == null) || (player.getNativeWorkOwner().epoch() != session._epoch) || !player.hasHeadlessOutboundSession() || !player.isOnline() || player.isDead() || player.hasPendingOwnedStore())
			{
				action.close();
				return null;
			}
			return action::close;
		}

		@Override
		public int targetSelectionLoad(Creature target)
		{
			int load = 0;
			for (Session peer : _sessions.values())
			{
				final Player actor = peer.player();
				final var owner = actor.getNativeWorkOwner();
				if ((actor != _player) && peer._current.get() && (_sessions.get(peer._profileId) == peer)
					&& actor.isAutoPlaying() && actor.isOnline() && actor.hasHeadlessOutboundSession() && !actor.isDead() && !actor.isSitting()
					&& (actor.getInstanceId() == _player.getInstanceId()) && (actor.getTarget() == target)
					&& (owner != null) && owner.isCurrent() && (owner.player() == actor) && (owner.epoch() == peer._epoch)) { load++; }
			}
			return load;
		}
		@Override
		public boolean permitsTarget(Creature target)
		{
			return target.isMonster() && !target.isRaid() && (target.getInstanceId() == _player.getInstanceId()) && (target.asNpc().getId() == _npcId);
		}
	}

	/** Fresh scalar observation; it acquires no action lease and never starts or repairs AutoPlay. */
	public record ContinuationSnapshot(Map<String, String> scalarMap) { }
	private record TickObservation(long sequence, long started, long finished, String reason) { }
	private record StopObservation(long nanos, String reason, String caller) { }
	private static void tickScalars(Map<String, String> fields, String prefix, TickObservation tick)
	{
		fields.put(prefix + "Sequence", tick == null ? "0" : Long.toString(tick.sequence()));
		fields.put(prefix + "StartedNanos", tick == null ? "0" : Long.toString(tick.started()));
		fields.put(prefix + "FinishedNanos", tick == null ? "0" : Long.toString(tick.finished()));
		fields.put(prefix + "Reason", tick == null ? "not_invoked" : tick.reason());
	}
	public ContinuationSnapshot snapshotContinuation(long profileId)
	{
		final Session session = _sessions.get(profileId);
		final Map<String, String> fields = new java.util.LinkedHashMap<>();
		fields.put("continuationSampleNanos", Long.toString(System.nanoTime()));
		fields.put("continuationSession", Boolean.toString(session != null));
		fields.put("liveAutoPlay", "unknown_without_session"); fields.put("liveAutoPlayRegistered", "false"); fields.put("liveAutoUseRegistered", "false");
		tickScalars(fields, "liveAutoPlayTick", null); tickScalars(fields, "liveAutoUseTick", null);
		if (session != null)
		{
			final Player player = session._player;
			fields.put("liveAutoPlay", Boolean.toString(player.isAutoPlaying()));
			fields.put("liveAutoPlayRegistered", Boolean.toString(AutoPlayTaskManager.getInstance().hasPhantomRegistration(player, session._policy)));
			fields.put("liveAutoUseRegistered", Boolean.toString(AutoUseTaskManager.getInstance().hasPhantomRegistration(player, session._policy)));
			fields.put("livePolicyIdentity", Integer.toHexString(System.identityHashCode(session._policy)));
			fields.put("liveSessionEpoch", Long.toString(session._epoch));
			fields.put("liveSessionCurrent", Boolean.toString(session._current.get()));
			fields.put("liveResourceRecovery", Boolean.toString(session._recovering));
			fields.put("liveResourceReason", session._resourceReason);
			fields.put("liveResourceCost", Double.toString(session._resourceCost));
			fields.put("liveResourceSinceNanos", Long.toString(session._resourceSince));
			fields.put("continuationRead", "NONATOMIC_VOLATILE");
			if (session._policy instanceof Policy policy)
			{
				tickScalars(fields, "liveAutoPlayTick", policy._autoPlayTick); tickScalars(fields, "liveAutoUseTick", policy._autoUseTick);
				final var stopped = policy._firstStop.get();
				fields.put("liveFirstStopNanos", stopped == null ? "0" : Long.toString(stopped.nanos()));
				fields.put("liveFirstStopReason", stopped == null ? "" : stopped.reason());
				fields.put("liveFirstStopCaller", stopped == null ? "" : stopped.caller());
			}
		}
		return new ContinuationSnapshot(java.util.Collections.unmodifiableMap(fields));
	}

	private static final class Session
	{
		private final Player _player;
		private final long _goalId;
		private final long _revision;
		private final PhantomPolicy _policy;
		private final long _epoch;
		private final AtomicBoolean _current = new AtomicBoolean(true);
		private long nextTargetCheck;
		private long noTargetSince = -1;
		private long _usefulSince;
		private long _damageSequence;
		private long _rewardSequence;
		private long _cycleSequence;
		private long _lootSequence;
		private long _nativeUsefulNanos;
		private boolean _nativeBaseline;
		private boolean _repairAttempted;
		private boolean _stallReported;
		private final long _profileId;
		private volatile boolean _recovering, _resourceExpired;
		private volatile long _resourceSince, _resourceCheckDue;
		private volatile double _resourceCost;
		private volatile String _resourceReason = "resource.not_required";

		private Session(Player player, long goalId, long revision, PhantomPolicy policy, long epoch, long now, Session previous)
		{
			_player = player;
			_profileId = ((Policy) policy)._profileId;
			_goalId = goalId;
			_revision = revision;
			_policy = policy;
			_epoch = epoch;
			_usefulSince = now;
			baseline(nativeProgress(player, epoch));
			if ((previous != null) && (previous.player() == player) && (previous.goalId() == goalId) && (previous.revision() == revision) && (previous._epoch == epoch))
			{
				synchronized (previous)
				{
					_usefulSince = previous._usefulSince; noTargetSince = previous.noTargetSince;
					_damageSequence = previous._damageSequence; _rewardSequence = previous._rewardSequence; _cycleSequence = previous._cycleSequence;
					_lootSequence = previous._lootSequence; _nativeUsefulNanos = previous._nativeUsefulNanos; _nativeBaseline = previous._nativeBaseline;
					_repairAttempted = previous._repairAttempted; _stallReported = previous._stallReported;
					_recovering = previous._recovering; _resourceExpired = previous._resourceExpired;
					_resourceSince = previous._resourceSince; _resourceCheckDue = previous._resourceCheckDue;
					_resourceCost = previous._resourceCost; _resourceReason = previous._resourceReason;
				}
			}
		}

		private void baseline(PlayerNativeEvidence.Snapshot progress)
		{
			if (progress == null) { return; }
			_damageSequence = progress.damageSequence(); _rewardSequence = progress.rewardSequence(); _cycleSequence = progress.farmCycleSequence();
			_lootSequence = progress.lootSequence(); _nativeUsefulNanos = progress.usefulProgressNanos(); _nativeBaseline = true;
		}

		private Player player()
		{
			return _player;
		}

		private long goalId()
		{
			return _goalId;
		}

		private long revision()
		{
			return _revision;
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
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
		stop(profileId);
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
		stop(profileId);
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
	}
}

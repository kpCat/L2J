/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.config.custom.AutoPlayConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
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
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.State;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.PhantomPolicy;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.TickLease;
import org.l2jmobius.gameserver.taskmanagers.AutoUseTaskManager;

/** Runs the existing AutoPlay and AutoUse pools only while an ordinary visible goal owns the materialized Player. */
public final class PhantomVisibleAutoPlay implements PhantomMaterializationLifecyclePort
{
	private final PhantomMaterializationService _materialization;
	private final Supplier<PhantomDecisionEngine> _decision;
	private final LongPredicate _permitsOrdinary;
	private final Map<Long, Session> _sessions = new ConcurrentHashMap<>();
	private final LongSupplier _clock;

	public PhantomVisibleAutoPlay(PhantomMaterializationService materialization, Supplier<PhantomDecisionEngine> decision, LongPredicate permitsOrdinary)
	{
		this(materialization, decision, permitsOrdinary, System::nanoTime);
	}

	public PhantomVisibleAutoPlay(PhantomMaterializationService materialization, Supplier<PhantomDecisionEngine> decision, LongPredicate permitsOrdinary, LongSupplier clock)
	{
		_materialization = Objects.requireNonNull(materialization, "materialization");
		_decision = Objects.requireNonNull(decision, "decision");
		_permitsOrdinary = Objects.requireNonNull(permitsOrdinary, "permitsOrdinary");
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
			if (!player.hasHeadlessOutboundSession() || !player.isOnline() || player.isDead() || (player.getObjectId() != snapshot.characterObjectId()) || !current(profileId, goal))
			{
				return false;
			}
			final Session previous = _sessions.get(profileId);
			if ((previous != null) && (previous.player() == player) && (previous.goalId() == goal.goalId()) && (previous.revision() == goal.revision()) && player.isAutoPlaying())
			{
				return true;
			}
			stop(profileId);
			configure(player);
			final PhantomPolicy policy = new Policy(player, profileId, goal.goalId(), goal.revision(), spec.npcId());
			_sessions.put(profileId, new Session(player, goal.goalId(), goal.revision()));
			try
			{
				AutoPlayTaskManager.getInstance().startPhantomAutoPlay(player, policy);
				AutoUseTaskManager.getInstance().startPhantomAutoUse(player, policy);
			}
			catch (RuntimeException exception)
			{
				stop(profileId);
				throw exception;
			}
			return true;
		}
	}

	public void stop(long profileId)
	{
		final Session session = _sessions.remove(profileId);
		if (session != null)
		{
			AutoUseTaskManager.getInstance().stopAutoUseTask(session.player());
			AutoPlayTaskManager.getInstance().stopAutoPlay(session.player());
		}
	}

	public boolean running(long profileId, PhantomGoal goal)
	{
		final Session session = _sessions.get(profileId);
		return (session != null) && (session.goalId() == goal.goalId()) && (session.revision() == goal.revision()) && session.player().isAutoPlaying() && current(profileId, goal);
	}

	/** Detects absence under the same native range/geo gates as stock AutoPlay. */
	public boolean noTargetExpired(long profileId, PhantomGoal goal)
	{
		final Session session = _sessions.get(profileId);
		if ((session == null) || (session.goalId() != goal.goalId()) || (session.revision() != goal.revision()))
		{
			return false;
		}
		synchronized (session)
		{
			final long now = _clock.getAsLong();
			if (now < session.nextTargetCheck)
			{
				return (session.noTargetSince >= 0) && ((now - session.noTargetSince) >= 30_000_000_000L);
			}
			session.nextTargetCheck = now + 1_000_000_000L;
			try (var action = _materialization.tryAcquireAction(profileId).orElse(null))
			{
				if ((action == null) || (action.player() != session.player()) || action.player().isDead())
				{
					session.noTargetSince = -1;
					return false;
				}
				final Player player = action.player();
				final int npcId = PhantomBackgroundGoalSpec.parse(goal).npcId();
				final boolean busy = player.isMoving() || player.isAttackingNow() || player.isCastingNow() || player.isTeleporting();
				final boolean target = busy || World.getInstance().getVisibleObjectsInRange(player, Creature.class, player.getAutoPlaySettings().isShortRange() ? AutoPlayConfig.AUTO_PLAY_SHORT_RANGE : AutoPlayConfig.AUTO_PLAY_LONG_RANGE).stream().anyMatch(creature -> selectableTarget(player, creature, npcId));
				if (target)
				{
					session.noTargetSince = -1;
					return false;
				}
				if (session.noTargetSince < 0)
				{
					session.noTargetSince = now;
				}
				return (now - session.noTargetSince) >= 30_000_000_000L;
			}
		}
	}

	private static boolean selectableTarget(Player player, Creature creature, int npcId)
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
		player.getAutoPlaySettings().setShortRange(true);
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

		private Policy(Player player, long profileId, long goalId, long revision, int npcId)
		{
			_player = player;
			_profileId = profileId;
			_goalId = goalId;
			_revision = revision;
			_npcId = npcId;
		}

		@Override
		public TickLease acquire(Player player)
		{
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
			if ((action.player() != player) || !player.hasHeadlessOutboundSession() || player.isDead())
			{
				action.close();
				return null;
			}
			return action::close;
		}

		@Override
		public boolean permitsTarget(Creature target)
		{
			return target.isMonster() && !target.isRaid() && (target.getInstanceId() == _player.getInstanceId()) && (target.asNpc().getId() == _npcId);
		}
	}

	private static final class Session
	{
		private final Player _player;
		private final long _goalId;
		private final long _revision;
		private long nextTargetCheck;
		private long noTargetSince = -1;

		private Session(Player player, long goalId, long revision)
		{
			_player = player;
			_goalId = goalId;
			_revision = revision;
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

/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius.gameserver.model.actor.tasks.creature;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence;

/**
 * Task dedicated to magic use of character
 * @author xban1x
 */
public class MagicUseTask implements Runnable
{
	private final Creature _creature;
	private volatile List<WorldObject> _targets;
	private final Skill _skill;
	private int _count;
	private int _skillTime;
	private volatile int _phase;
	private final boolean _simultaneously;
	private final boolean _nativeLifetimeOwned;
	private final LearningCastEvidence _learning;
	
	public MagicUseTask(Creature creature, List<WorldObject> targets, Skill s, int hit, boolean simultaneous)
	{
		_creature = creature;
		_targets = targets;
		_skill = s;
		_count = 0;
		_phase = 1;
		_skillTime = hit;
		_simultaneously = simultaneous;
		_nativeLifetimeOwned = managed(creature) || ((targets != null) && targets.stream().anyMatch(MagicUseTask::managed));
		_learning = LearningCastEvidence.capture(creature, s, targets, simultaneous);
	}

	public LearningCastEvidence learningEvidence() { return _learning; }

	private static boolean managed(WorldObject object)
	{
		if (object == null) { return false; }
		final var player = object.isSummon() ? object.asSummon().getOwner() : object.asPlayer();
		return (player != null) && player.isNativeWorkManaged();
	}
	
	@Override
	public void run()
	{
		if ((_creature == null) || (_nativeLifetimeOwned && !_creature.isCurrentNativeMagicUseTask(this)))
		{
			return;
		}
		
		try
		{
			switch (_phase)
			{
				case 1:
				{
					_creature.onMagicLaunchedTimer(this);
					break;
				}
				case 2:
				{
					_creature.onMagicHitTimer(this);
					break;
				}
				case 3:
				{
					_creature.onMagicFinalizer(this);
					break;
				}
			}
		}
		catch (RuntimeException | Error failure)
		{
			if (_nativeLifetimeOwned && _creature.isCurrentNativeMagicUseTask(this)) { _creature.abortCast(); }
			throw failure;
		}
	}

	public boolean isNativeLifetimeOwned() { return _nativeLifetimeOwned; }

	public enum TargetStatus { READY, FINISHED, NO_RECIPIENTS, AMBIGUOUS, OVERFLOW, CHANGED, STALE }
	public enum Disposition { HOSTILE, HARMLESS, UNKNOWN }

	public record TargetObservation(TargetStatus status, Disposition disposition, List<WorldObject> targets, boolean nativeLifetimeOwned, BooleanSupplier current, Skill skill)
	{
		public TargetObservation { targets = List.copyOf(targets); }
		public TargetObservation(TargetStatus status, Disposition disposition, List<WorldObject> targets, boolean nativeLifetimeOwned, BooleanSupplier current)
		{ this(status, disposition, targets, nativeLifetimeOwned, current, null); }
		public boolean isCurrent() { return current.getAsBoolean(); }
	}

	/** Observe the published targets without exposing the task's mutable list. */
	public TargetObservation observeTargets(int maximumTargets, BooleanSupplier currentTask)
	{
		if (maximumTargets < 1 || maximumTargets > 64) { throw new IllegalArgumentException("Cast observation target bound."); }
		final var disposition = _skill == null ? Disposition.UNKNOWN
			: (_skill.isDamage() || _skill.hasNegativeEffect() || _skill.isDebuff()) ? Disposition.HOSTILE : Disposition.HARMLESS;
		if (!currentTask.getAsBoolean()) { return new TargetObservation(TargetStatus.STALE, disposition, List.of(), _nativeLifetimeOwned, () -> false); }
		final int phase = _phase;
		final var published = _targets;
		final BooleanSupplier sameTaskAndPhase = () -> currentTask.getAsBoolean() && _phase == phase && _targets == published;
		if (phase == 3) { return new TargetObservation(TargetStatus.FINISHED, disposition, List.of(), _nativeLifetimeOwned, sameTaskAndPhase); }
		if (phase != 1 && phase != 2) { return new TargetObservation(TargetStatus.AMBIGUOUS, disposition, List.of(), _nativeLifetimeOwned, sameTaskAndPhase); }
		if (published == null || published.isEmpty()) { return new TargetObservation(TargetStatus.NO_RECIPIENTS, disposition, List.of(), _nativeLifetimeOwned, sameTaskAndPhase); }
		final var captured = new ArrayList<WorldObject>(Math.min(maximumTargets, published.size()));
		try
		{
			for (WorldObject target : published)
			{
				if (target == null) { return new TargetObservation(TargetStatus.AMBIGUOUS, disposition, List.of(), _nativeLifetimeOwned, sameTaskAndPhase); }
				if (captured.size() == maximumTargets) { return new TargetObservation(TargetStatus.OVERFLOW, disposition, List.of(), _nativeLifetimeOwned, sameTaskAndPhase); }
				captured.add(target);
			}
		}
		catch (ConcurrentModificationException | IndexOutOfBoundsException changed)
		{ return new TargetObservation(TargetStatus.CHANGED, disposition, List.of(), _nativeLifetimeOwned, () -> false); }
		final BooleanSupplier current = () -> sameTaskAndPhase.getAsBoolean() && sameTargets(published, captured) && sameTaskAndPhase.getAsBoolean();
		return current.getAsBoolean() ? new TargetObservation(TargetStatus.READY, disposition, captured, _nativeLifetimeOwned, current, _skill)
			: new TargetObservation(TargetStatus.CHANGED, disposition, List.of(), _nativeLifetimeOwned, () -> false);
	}

	private static boolean sameTargets(List<WorldObject> published, List<WorldObject> captured)
	{
		if (published.size() != captured.size()) { return false; }
		try
		{
			int index = 0;
			for (WorldObject target : published)
			{
				if (index == captured.size() || target != captured.get(index++)) { return false; }
			}
			return index == captured.size();
		}
		catch (ConcurrentModificationException | IndexOutOfBoundsException changed) { return false; }
	}
	
	public int getCount()
	{
		return _count;
	}
	
	public int getPhase()
	{
		return _phase;
	}
	
	public Skill getSkill()
	{
		return _skill;
	}
	
	public int getSkillTime()
	{
		return _skillTime;
	}
	
	public List<WorldObject> getTargets()
	{
		return _targets;
	}
	
	public boolean isSimultaneous()
	{
		return _simultaneously;
	}
	
	public void setCount(int count)
	{
		_count = count;
	}
	
	public void setPhase(int phase)
	{
		_phase = phase;
	}
	
	public void setSkillTime(int skillTime)
	{
		_skillTime = skillTime;
	}
	
	public void setTargets(List<WorldObject> targets)
	{
		_targets = targets;
	}
}

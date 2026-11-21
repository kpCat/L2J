/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.learning.LearningCastEvidence;
import org.l2jmobius.gameserver.phantoms.learning.LearningPolicy;
import org.l2jmobius.gameserver.phantoms.learning.TacticalPolicyV3;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.taskmanagers.AutoPlayTaskManager.ScopedOffenseChoice;

/** TASK131 binds owned actors before visibility using the existing production-composed TEST factory. */
public final class PhantomLearning131Factory
{
	private PhantomLearning131Factory() { }

	public static boolean start(PhantomPlayersConfig.Settings settings, PhantomMaterializationLifecyclePort observer, Consumer<PhantomSystem.PlanningInspection> inspection, BiPredicate<Player, Creature> targetScope)
	{
		return PhantomSystem.startConfiguredForTesting(settings, inspection, observer, targetScope);
	}

	public static boolean startEpisodeLearning(PhantomPlayersConfig.Settings settings, PhantomMaterializationLifecyclePort observer,
		Consumer<PhantomSystem.PlanningInspection> inspection, BiPredicate<Player, Creature> targetScope,
		TacticalPolicyV3 tactical, LearningPolicy damage, BiPredicate<Long, Player> eligible, BiPredicate<Long, Skill> permitsSkill)
	{
		Objects.requireNonNull(tactical);
		Objects.requireNonNull(damage);
		Objects.requireNonNull(eligible);
		Objects.requireNonNull(permitsSkill);
		final var solo = new PhantomVisibleAutoPlay.SoloOffense()
		{
			@Override
			public boolean active(long profileId, Player actor)
			{
				return eligible.test(profileId, actor);
			}

			@Override
			public boolean permitsSkill(long profileId, Skill skill)
			{
				return permitsSkill.test(profileId, skill);
			}

			@Override
			public ScopedOffenseChoice choose(long profileId, long epoch, long goalId, long goalRevision, Player actor, Monster target,
				List<Skill> ready, Skill baseline, BooleanSupplier current)
			{
				if (!active(profileId, actor)) { return null; }
				final var attempt = damage.chooseVisible131(profileId, epoch, actor, target, ready, baseline, current);
				if (attempt == null) { return null; }
				final var selected = actor.getKnownSkill(attempt.candidate().skill().skillId());
				if (selected == null || selected.getLevel() != attempt.candidate().skill().skillLevel()) { return null; }
				return new ScopedOffenseChoice(selected, nativeCast -> LearningCastEvidence.dispatch(attempt, actor, target, selected, current, nativeCast));
			}
		};
		return PhantomSystem.startConfiguredForTesting(settings, inspection, observer, targetScope, new PhantomSystem.TestLearning131(damage, tactical, solo));
	}
}

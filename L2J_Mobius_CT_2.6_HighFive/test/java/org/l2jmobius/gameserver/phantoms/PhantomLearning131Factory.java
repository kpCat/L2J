/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms;

import java.util.function.Consumer;
import java.util.function.BiPredicate;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;

/** TASK131 binds owned actors before visibility using the existing production-composed TEST factory. */
public final class PhantomLearning131Factory
{
	private PhantomLearning131Factory() { }

	public static boolean start(PhantomPlayersConfig.Settings settings, PhantomMaterializationLifecyclePort observer, Consumer<PhantomSystem.PlanningInspection> inspection, BiPredicate<Player, Creature> targetScope)
	{
		return PhantomSystem.startConfiguredForTesting(settings, inspection, observer, targetScope);
	}
}

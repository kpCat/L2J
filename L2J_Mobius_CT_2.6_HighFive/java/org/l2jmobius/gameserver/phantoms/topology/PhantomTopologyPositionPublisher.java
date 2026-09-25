/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.topology;

import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.RegistrationResult;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.UpdateResult;

/** Publishes only verified durable positions into the existing topology registry. */
public final class PhantomTopologyPositionPublisher implements PhantomMaterializationLifecyclePort
{
	private final PhantomTopologyService _topology;
	private final LongFunction<Optional<PhantomBackgroundState>> _committedState;

	public PhantomTopologyPositionPublisher(PhantomTopologyService topology, LongFunction<Optional<PhantomBackgroundState>> committedState)
	{
		_topology = Objects.requireNonNull(topology, "topology");
		_committedState = Objects.requireNonNull(committedState, "committedState");
	}

	public synchronized void ready(long profileId)
	{
		if (_topology.findProfile(profileId).isPresent())
		{
			return;
		}
		register(profileId);
		_committedState.apply(profileId).ifPresent(state -> committed(profileId, state.position()));
	}

	public synchronized void committed(long profileId, Position position)
	{
		Objects.requireNonNull(position, "position");
		register(profileId);
		final var current = _topology.findProfile(profileId).orElseThrow();
		final PhantomTopologyPoint point = new PhantomTopologyPoint(position.x(), position.y(), position.z(), position.instanceId());
		if (point.equals(current.point()))
		{
			return;
		}
		final UpdateResult updated = _topology.updateProfile(profileId, point, Math.addExact(current.sequence(), 1));
		if (updated != UpdateResult.UPDATED)
		{
			throw new IllegalStateException("Committed topology position was rejected: " + updated);
		}
	}

	public synchronized void retired(long profileId)
	{
		switch (_topology.unregisterProfile(profileId))
		{
			case UNREGISTERED_AND_WITHDRAWN, NOT_REGISTERED ->
			{
			}
			default -> throw new IllegalStateException("Retired topology profile could not be unregistered.");
		}
	}

	private void register(long profileId)
	{
		final RegistrationResult result = _topology.registerProfile(profileId);
		if ((result != RegistrationResult.REGISTERED) && (result != RegistrationResult.ALREADY_REGISTERED))
		{
			throw new IllegalStateException("Ready topology profile could not be registered: " + result);
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
		_committedState.apply(profileId).ifPresent(state -> committed(profileId, state.position()));
	}

	@Override
	public void materializeAborted(long profileId, int characterObjectId)
	{
	}

	@Override
	public void beforeStore(long profileId, Player player)
	{
	}

	@Override
	public void afterStore(long profileId, Player player)
	{
		_committedState.apply(profileId).ifPresent(state -> committed(profileId, state.position()));
	}
}

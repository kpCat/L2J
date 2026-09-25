/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager.AdmissionProfileSnapshot;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState.State;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyProfileRegistry.ProfileTopologySnapshot;

/** Bounded read-only selection of a canonical local proof target. */
public final class PhantomLocalProofSelector
{
	private PhantomLocalProofSelector()
	{
	}

	public static Optional<ProfileTopologySnapshot> nearest(PhantomTopologyPoint human, List<ProfileTopologySnapshot> profiles, LongFunction<Optional<AdmissionProfileSnapshot>> admission, LongPredicate available)
	{
		Objects.requireNonNull(human, "Human point must not be null.");
		Objects.requireNonNull(profiles, "Topology profiles must not be null.");
		Objects.requireNonNull(admission, "Admission lookup must not be null.");
		Objects.requireNonNull(available, "Presence lookup must not be null.");
		return profiles.stream()
			.filter(profile -> profile.resolved() && (profile.point() != null) && (profile.point().instanceId() == human.instanceId()))
			.filter(profile -> admission.apply(profile.profileId()).filter(state -> (state.populationState() == State.READY) && state.eligible() && state.admitted() && !state.pendingRebalance()).isPresent())
			.filter(profile -> available.test(profile.profileId()))
			.min(Comparator.comparingLong((ProfileTopologySnapshot profile) -> human.distanceSquared2D(profile.point())).thenComparingLong(ProfileTopologySnapshot::profileId));
	}
}

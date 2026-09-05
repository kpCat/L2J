/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.ManagedSnapshot;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;

/** Optimistically-versioned persistence boundary for {@code population.ecology}. */
public final class PhantomPopulationEcologyStore implements PhantomPopulationEcologyService.PersistencePort
{
	private final PhantomProfileRepository _profiles;
	private final PhantomPopulationEcologyStateCodec _ecologyCodec;
	private final PhantomPopulationStateCodec _populationCodec;

	public PhantomPopulationEcologyStore(PhantomProfileRepository profiles)
	{
		this(profiles, new PhantomPopulationEcologyStateCodec(), new PhantomPopulationStateCodec());
	}

	PhantomPopulationEcologyStore(PhantomProfileRepository profiles, PhantomPopulationEcologyStateCodec ecologyCodec, PhantomPopulationStateCodec populationCodec)
	{
		_profiles = Objects.requireNonNull(profiles, "Profile repository must not be null.");
		_ecologyCodec = Objects.requireNonNull(ecologyCodec, "Ecology codec must not be null.");
		_populationCodec = Objects.requireNonNull(populationCodec, "Population codec must not be null.");
	}

	public Optional<StoredState> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomPopulationEcologyState.COMPONENT_TYPE).map(this::decode);
	}

	public StoredState insert(long profileId, PhantomPopulationEcologyState state)
	{
		final StoredState existing = load(profileId).orElse(null);
		if (existing != null)
		{
			return existing;
		}
		try
		{
			return decode(_profiles.insertComponent(profileId, PhantomPopulationEcologyState.COMPONENT_TYPE, PhantomPopulationEcologyState.SCHEMA_VERSION, _ecologyCodec.encode(state)));
		}
		catch (RuntimeException exception)
		{
			final StoredState raced = load(profileId).orElse(null);
			if (raced != null)
			{
				return raced;
			}
			throw exception;
		}
	}

	public StoredState save(long profileId, StoredState expected, PhantomPopulationEcologyState replacement)
	{
		requireSameAssignment(expected.state(), replacement);
		return decode(_profiles.updateComponent(profileId, PhantomPopulationEcologyState.COMPONENT_TYPE, expected.rowVersion(), PhantomPopulationEcologyState.SCHEMA_VERSION, _ecologyCodec.encode(replacement)));
	}

	public ArchivedResult archive(ManagedSnapshot population, StoredState ecology, long archiveGeneration, long nowEpochMinute)
	{
		Objects.requireNonNull(population, "Population snapshot must not be null.");
		Objects.requireNonNull(ecology, "Ecology snapshot must not be null.");
		if ((population.state().state() != PhantomPopulationState.State.RETIRE_REQUESTED) || (population.component().componentSchemaVersion() != PhantomPopulationState.SCHEMA_VERSION))
		{
			throw new IllegalStateException("Ecology archive requires a current requested population retirement.");
		}
		final PhantomPopulationState retired = population.state().retired();
		final PhantomPopulationEcologyState archived = ecology.state().archived(archiveGeneration, nowEpochMinute, "ecology.turnover");
		final List<PhantomProfileComponent> components = _profiles.mutateComponentsAtomically(population.profile().profileId(), List.of(
			new ComponentMutation(PhantomPopulationEcologyState.COMPONENT_TYPE, ecology.rowVersion(), PhantomPopulationEcologyState.SCHEMA_VERSION, _ecologyCodec.encode(archived)),
			new ComponentMutation(PhantomPopulationState.COMPONENT_TYPE, population.component().rowVersion(), PhantomPopulationState.SCHEMA_VERSION, _populationCodec.encode(retired))));
		PhantomProfileComponent ecologyComponent = null;
		PhantomProfileComponent populationComponent = null;
		for (PhantomProfileComponent component : components)
		{
			if (PhantomPopulationEcologyState.COMPONENT_TYPE.equals(component.componentType()))
			{
				ecologyComponent = component;
			}
			else if (PhantomPopulationState.COMPONENT_TYPE.equals(component.componentType()))
			{
				populationComponent = component;
			}
		}
		if ((ecologyComponent == null) || (populationComponent == null))
		{
			throw new IllegalStateException("Atomic ecology archive returned an unexpected component set.");
		}
		return new ArchivedResult(new ManagedSnapshot(population.profile(), populationComponent, retired), decode(ecologyComponent));
	}

	private StoredState decode(PhantomProfileComponent component)
	{
		if (!PhantomPopulationEcologyState.COMPONENT_TYPE.equals(component.componentType()) || (component.componentSchemaVersion() != PhantomPopulationEcologyState.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown population.ecology component schema.");
		}
		return new StoredState(_ecologyCodec.decode(component.payload()), component.rowVersion());
	}

	private static void requireSameAssignment(PhantomPopulationEcologyState expected, PhantomPopulationEcologyState replacement)
	{
		if (!expected.catalogHash().equals(replacement.catalogHash()) || (expected.preset() != replacement.preset()) || (expected.ecologyGeneration() != replacement.ecologyGeneration()) || (expected.assignmentOrdinal() != replacement.assignmentOrdinal()) || (expected.assignedAtEpochMinute() != replacement.assignedAtEpochMinute()) || (expected.virtualJoinEpochMinute() != replacement.virtualJoinEpochMinute()) || (expected.initialTargetEpochMinute() != replacement.initialTargetEpochMinute()) || (expected.pace() != replacement.pace()) || (expected.productiveShareBasisPoints() != replacement.productiveShareBasisPoints()) || (expected.productiveBlockMinutes() != replacement.productiveBlockMinutes()) || (expected.personality() != replacement.personality()) || !expected.initialSocialTraits().equals(replacement.initialSocialTraits()) || !expected.scheduleTemplate().equals(replacement.scheduleTemplate()) || (expected.turnoverEligibleEpochMinute() != replacement.turnoverEligibleEpochMinute()) || (expected.replacesProfileId() != replacement.replacesProfileId()) || (replacement.calendarCursorEpochMinute() < expected.calendarCursorEpochMinute()))
		{
			throw new ConcurrentModificationException("Ecology replacement changed immutable assignment or regressed its cursor.");
		}
	}

	public record StoredState(PhantomPopulationEcologyState state, long rowVersion)
	{
		public StoredState
		{
			Objects.requireNonNull(state, "Ecology state must not be null.");
			if (rowVersion < 0)
			{
				throw new IllegalArgumentException("Ecology row version must be non-negative.");
			}
		}
	}

	public record ArchivedResult(ManagedSnapshot population, StoredState ecology)
	{
		public ArchivedResult
		{
			Objects.requireNonNull(population, "Archived population snapshot must not be null.");
			Objects.requireNonNull(ecology, "Archived ecology snapshot must not be null.");
		}
	}
}

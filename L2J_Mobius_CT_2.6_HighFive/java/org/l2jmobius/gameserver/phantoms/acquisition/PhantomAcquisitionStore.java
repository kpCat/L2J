/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.acquisition;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;

/** Optimistic component store; active progress updates Goal and acquisition atomically. */
public final class PhantomAcquisitionStore
{
	private final PhantomProfileRepository _profiles;
	private final PhantomGoalStateStore _goals;
	private final PhantomAcquisitionStateCodec _codec;

	public PhantomAcquisitionStore(PhantomProfileRepository profiles, PhantomGoalStateStore goals)
	{
		this(profiles, goals, new PhantomAcquisitionStateCodec());
	}

	public PhantomAcquisitionStore(PhantomProfileRepository profiles, PhantomGoalStateStore goals, PhantomAcquisitionStateCodec codec)
	{
		_profiles = Objects.requireNonNull(profiles, "profiles");
		_goals = Objects.requireNonNull(goals, "goals");
		_codec = Objects.requireNonNull(codec, "codec");
	}

	public Optional<StoredState> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomAcquisitionState.COMPONENT_TYPE).map(this::decode);
	}

	public StoredState insert(long profileId, PhantomAcquisitionState state)
	{
		return decode(_profiles.insertComponent(profileId, PhantomAcquisitionState.COMPONENT_TYPE, PhantomAcquisitionState.SCHEMA_VERSION, _codec.encode(state)));
	}

	public StoredMutation insertWithGoal(long profileId, PhantomAcquisitionState state, long expectedGoalRowVersion, PhantomGoal goal)
	{
		final List<PhantomProfileComponent> changed = _profiles.mutateComponentsAtomically(profileId, List.of(
			new ComponentMutation(PhantomAcquisitionState.COMPONENT_TYPE, -1, PhantomAcquisitionState.SCHEMA_VERSION, _codec.encode(state)),
			_goals.componentMutation(expectedGoalRowVersion, goal)));
		return new StoredMutation(decode(changed.get(0)), _goals.decodeComponent(changed.get(1)));
	}

	public StoredState replace(long profileId, long expectedRowVersion, PhantomAcquisitionState state)
	{
		return decode(_profiles.updateComponent(profileId, PhantomAcquisitionState.COMPONENT_TYPE, expectedRowVersion, PhantomAcquisitionState.SCHEMA_VERSION, _codec.encode(state)));
	}

	public StoredMutation mutateWithGoal(long profileId, long expectedStateRowVersion, PhantomAcquisitionState state, long expectedGoalRowVersion, PhantomGoal goal)
	{
		final List<PhantomProfileComponent> changed = _profiles.mutateComponentsAtomically(profileId, List.of(
			new ComponentMutation(PhantomAcquisitionState.COMPONENT_TYPE, expectedStateRowVersion, PhantomAcquisitionState.SCHEMA_VERSION, _codec.encode(state)),
			_goals.componentMutation(expectedGoalRowVersion, goal)));
		return new StoredMutation(decode(changed.get(0)), _goals.decodeComponent(changed.get(1)));
	}

	public ComponentMutation componentMutation(long expectedRowVersion, PhantomAcquisitionState state)
	{
		return new ComponentMutation(PhantomAcquisitionState.COMPONENT_TYPE, expectedRowVersion, PhantomAcquisitionState.SCHEMA_VERSION, _codec.encode(state));
	}

	private StoredState decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomAcquisitionState.SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unknown acquisition.state component schema version.");
		}
		return new StoredState(_codec.decode(component.payload()), component.rowVersion());
	}

	public record StoredState(PhantomAcquisitionState state, long rowVersion)
	{
	}

	public record StoredMutation(StoredState acquisition, PhantomGoalStateStore.StoredGoal goal)
	{
	}
}

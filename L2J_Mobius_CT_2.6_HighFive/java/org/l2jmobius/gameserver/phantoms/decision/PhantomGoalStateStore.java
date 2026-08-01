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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;

public final class PhantomGoalStateStore implements PhantomGoalStore
{
	public static final String COMPONENT_TYPE = "goal.runtime";
	public static final int COMPONENT_SCHEMA_VERSION = 1;
	private final PhantomProfileRepository _repository;
	private final PhantomGoalStateCodec _codec;

	public PhantomGoalStateStore(PhantomProfileRepository repository)
	{
		this(repository, new PhantomGoalStateCodec());
	}

	public PhantomGoalStateStore(PhantomProfileRepository repository, PhantomGoalStateCodec codec)
	{
		_repository = Objects.requireNonNull(repository, "Profile repository must not be null.");
		_codec = Objects.requireNonNull(codec, "Goal codec must not be null.");
	}

	@Override
	public boolean profileExists(long profileId)
	{
		return _repository.find(profileId).isPresent();
	}

	@Override
	public Optional<StoredGoal> load(long profileId)
	{
		return _repository.findComponent(profileId, COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public StoredGoal insert(long profileId, PhantomGoal goal)
	{
		return decode(_repository.insertComponent(profileId, COMPONENT_TYPE, COMPONENT_SCHEMA_VERSION, _codec.encode(goal)));
	}

	@Override
	public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
	{
		try
		{
			return decode(_repository.updateComponent(profileId, COMPONENT_TYPE, expectedRowVersion, COMPONENT_SCHEMA_VERSION, _codec.encode(goal)));
		}
		catch (ConcurrentModificationException exception)
		{
			final StoredGoal current = load(profileId).orElse(null);
			if ((current != null) && "acquire.item".equals(goal.goalType()) && (goal.status() == PhantomGoalStatus.COMPLETED) && (current.goal().goalId() == goal.goalId()) && (current.goal().revision() < Long.MAX_VALUE) && ((current.goal().revision() + 1) == goal.revision()) && (current.goal().status() == PhantomGoalStatus.COMPLETED) && (current.goal().currentAmount() == current.goal().requiredAmount()))
			{
				return current;
			}
			throw exception;
		}
	}

	@Override
	public void delete(long profileId, long expectedRowVersion)
	{
		_repository.deleteComponent(profileId, COMPONENT_TYPE, expectedRowVersion);
	}

	public ComponentMutation componentMutation(long expectedRowVersion, PhantomGoal goal)
	{
		return new ComponentMutation(COMPONENT_TYPE, expectedRowVersion, COMPONENT_SCHEMA_VERSION, _codec.encode(Objects.requireNonNull(goal)));
	}

	public StoredGoal decodeComponent(PhantomProfileComponent component)
	{
		return decode(component);
	}

	private StoredGoal decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != COMPONENT_SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unknown goal.runtime component schema version.");
		}
		return new StoredGoal(_codec.decode(component.payload()), component.rowVersion());
	}
}

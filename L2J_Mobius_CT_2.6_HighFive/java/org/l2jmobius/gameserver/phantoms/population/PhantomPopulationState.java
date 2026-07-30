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
package org.l2jmobius.gameserver.phantoms.population;

import java.util.Objects;

import org.l2jmobius.gameserver.model.World;

/**
 * Bounded durable identity and reconciliation facts for one managed profile.
 */
public record PhantomPopulationState(
	State state,
	long populationGeneration,
	long creationOrdinal,
	String catalogHash,
	String initializationAuthorityHash,
	long deterministicSeed,
	int nameAttempt,
	String reservedAccount,
	String ownershipToken,
	String characterName,
	int classId,
	boolean female,
	int face,
	int hairColor,
	int hairStyle,
	String scheduleTemplate,
	int schedulePhaseMinutes,
	int homeMapRegionId,
	int creationX,
	int creationY,
	int creationZ,
	Integer expectedCharacterObjectId,
	Integer actualCharacterObjectId,
	CreationStage creationStage,
	String initializationHash,
	String lastFailure)
{
	public static final String COMPONENT_TYPE = "population.state";
	public static final int SCHEMA_VERSION = 2;

	public PhantomPopulationState
	{
		Objects.requireNonNull(state, "Population state must not be null.");
		requireRange(populationGeneration, 1, Long.MAX_VALUE, "population generation");
		requireRange(creationOrdinal, 1, Long.MAX_VALUE, "creation ordinal");
		requireText(catalogHash, 64, 64, "[0-9a-f]+", "catalog hash");
		if (!initializationAuthorityHash.isEmpty())
		{
			requireText(initializationAuthorityHash, 64, 64, "[0-9a-f]+", "initialization authority hash");
		}
		requireRange(nameAttempt, 0, 255, "name attempt");
		requireText(reservedAccount, 2, 14, "[a-z0-9]+", "reserved account");
		requireText(ownershipToken, 32, 64, "[A-Za-z0-9_-]+", "ownership token");
		requireText(characterName, 1, 16, "[A-Za-z0-9]+", "character name");
		requireRange(classId, 0, 255, "class ID");
		requireRange(face, 0, 2, "face");
		requireRange(hairColor, 0, 3, "hair color");
		requireRange(hairStyle, 0, female ? 6 : 4, "hair style");
		requireText(scheduleTemplate, 1, 32, "[a-z][a-z0-9_.-]*", "schedule template");
		requireRange(schedulePhaseMinutes, -240, 240, "schedule phase");
		requireRange(homeMapRegionId, 0, Integer.MAX_VALUE, "home map-region ID");
		requireRange(creationX, World.WORLD_X_MIN + 5000, World.WORLD_X_MAX - 5000, "creation X");
		requireRange(creationY, World.WORLD_Y_MIN + 5000, World.WORLD_Y_MAX - 5000, "creation Y");
		requireObjectId(expectedCharacterObjectId, "expected character object ID");
		requireObjectId(actualCharacterObjectId, "actual character object ID");
		Objects.requireNonNull(creationStage, "Creation stage must not be null.");
		if (!initializationHash.isEmpty())
		{
			requireText(initializationHash, 64, 64, "[0-9a-f]+", "initialization hash");
		}
		if ((lastFailure == null) || (lastFailure.length() > 96) || (!lastFailure.isEmpty() && !lastFailure.matches("[a-z0-9_.-]+")))
		{
			throw new IllegalArgumentException("Last population failure must be an empty or bounded typed key.");
		}
	}

	public PhantomPopulationState advance(State nextState, CreationStage nextStage)
	{
		return copy(nextState, expectedCharacterObjectId, actualCharacterObjectId, nextStage, initializationHash, "");
	}

	public PhantomPopulationState withExpectedCharacter(Integer objectId)
	{
		return copy(state, objectId, actualCharacterObjectId, creationStage, initializationHash, lastFailure);
	}

	public PhantomPopulationState withName(int attempt, String name)
	{
		return new PhantomPopulationState(state, populationGeneration, creationOrdinal, catalogHash, initializationAuthorityHash, deterministicSeed, attempt, reservedAccount, ownershipToken, name, classId, female, face, hairColor, hairStyle, scheduleTemplate, schedulePhaseMinutes, homeMapRegionId, creationX, creationY, creationZ, expectedCharacterObjectId, actualCharacterObjectId, creationStage, initializationHash, lastFailure);
	}

	public PhantomPopulationState initialized(int objectId, String hash)
	{
		return copy(State.INITIALIZING, objectId, objectId, CreationStage.VERIFIED, hash, "");
	}

	public PhantomPopulationState initializationStored(int objectId, String hash)
	{
		return copy(State.INITIALIZING, objectId, objectId, CreationStage.INITIALIZATION_STORED, hash, "");
	}

	public PhantomPopulationState fail(String failure)
	{
		return copy(State.INCONSISTENT, expectedCharacterObjectId, actualCharacterObjectId, creationStage, initializationHash, failure);
	}

	public PhantomPopulationState ready()
	{
		return copy(State.READY, actualCharacterObjectId, actualCharacterObjectId, CreationStage.LINKED, initializationHash, "");
	}

	public PhantomPopulationState retireRequested()
	{
		return copy(State.RETIRE_REQUESTED, expectedCharacterObjectId, actualCharacterObjectId, creationStage, initializationHash, "");
	}

	public PhantomPopulationState retired()
	{
		return copy(State.RETIRED, expectedCharacterObjectId, actualCharacterObjectId, creationStage, initializationHash, "");
	}

	public PhantomPopulationState returned()
	{
		final State returnedState = switch (creationStage)
		{
			case LINKED -> State.READY;
			case VERIFIED, INITIALIZATION_INTENT, INITIALIZATION_STORED -> State.INITIALIZING;
			case CHARACTER_CREATED, CHARACTER_INTENT -> State.CHARACTER_PRESENT;
			case ACCOUNT_VERIFIED, ACCOUNT_INTENT -> State.ACCOUNT_PREPARED;
			case SHELL_DURABLE -> State.SHELL;
		};
		return copy(returnedState, expectedCharacterObjectId, actualCharacterObjectId, creationStage, initializationHash, "");
	}

	public boolean creationPending()
	{
		return (state == State.SHELL) || (state == State.ACCOUNT_PREPARED) || (state == State.CHARACTER_PRESENT) || (state == State.INITIALIZING);
	}

	private PhantomPopulationState copy(State newState, Integer expectedObjectId, Integer actualObjectId, CreationStage stage, String hash, String failure)
	{
		return new PhantomPopulationState(newState, populationGeneration, creationOrdinal, catalogHash, initializationAuthorityHash, deterministicSeed, nameAttempt, reservedAccount, ownershipToken, characterName, classId, female, face, hairColor, hairStyle, scheduleTemplate, schedulePhaseMinutes, homeMapRegionId, creationX, creationY, creationZ, expectedObjectId, actualObjectId, stage, hash, failure);
	}

	private static void requireObjectId(Integer value, String label)
	{
		if ((value != null) && (value <= 0))
		{
			throw new IllegalArgumentException("Population " + label + " must be positive when present.");
		}
	}

	private static void requireRange(long value, long minimum, long maximum, String label)
	{
		if ((value < minimum) || (value > maximum))
		{
			throw new IllegalArgumentException("Population " + label + " is outside its bounded range.");
		}
	}

	private static void requireText(String value, int minimumLength, int maximumLength, String pattern, String label)
	{
		if ((value == null) || (value.length() < minimumLength) || (value.length() > maximumLength) || !value.matches(pattern))
		{
			throw new IllegalArgumentException("Population " + label + " is invalid.");
		}
	}

	public enum State
	{
		SHELL,
		ACCOUNT_PREPARED,
		CHARACTER_PRESENT,
		INITIALIZING,
		READY,
		RETIRE_REQUESTED,
		RETIRED,
		INCONSISTENT
	}

	public enum CreationStage
	{
		SHELL_DURABLE,
		ACCOUNT_INTENT,
		ACCOUNT_VERIFIED,
		CHARACTER_INTENT,
		CHARACTER_CREATED,
		INITIALIZATION_INTENT,
		VERIFIED,
		LINKED,
		INITIALIZATION_STORED
	}
}

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
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.ExperienceData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.managers.IdManager;
import org.l2jmobius.gameserver.model.actor.enums.creature.Race;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.ItemTemplate;
import org.l2jmobius.gameserver.model.skill.holders.SkillLearn;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.AutoGetSkill;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Clock;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.InventoryFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemLocation;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ItemObject;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Position;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Progress;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Receipt;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Phase;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.ReceiptKind;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.TerminalResult;
import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.Reservation;
import org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOperation.ResourceKind;

/**
 * The only writer for durable background farming state. One MariaDB transaction
 * guards the profile link, exact goal, state, canonical character, subclass,
 * auto-get skills and item objects in stable order.
 */
public final class PhantomBackgroundTransaction
{
	private static final int QUERY_TIMEOUT_SECONDS = 5;
	private static final String LOCK_PROFILE = "SELECT character_object_id FROM phantom_profiles WHERE profile_id = ? FOR UPDATE";
	private static final String LOCK_COMPONENT = "SELECT component_schema_version, row_version, payload FROM phantom_profile_components WHERE profile_id = ? AND component_type = ? FOR UPDATE";
	private static final String INSERT_COMPONENT = "INSERT INTO phantom_profile_components (profile_id, component_type, component_schema_version, payload) VALUES (?, ?, ?, ?)";
	private static final String UPDATE_COMPONENT = "UPDATE phantom_profile_components SET component_schema_version = ?, payload = ?, row_version = row_version + 1 WHERE profile_id = ? AND component_type = ? AND row_version = ?";
	private static final String LOCK_CHARACTER = "SELECT level, exp, expBeforeDeath, sp, curHp, maxHp, curMp, maxMp, curCp, maxCp, x, y, z, heading, classid, race FROM characters WHERE charId = ? FOR UPDATE";
	private static final String LOCK_SUBCLASS = "SELECT level, exp, sp, class_id FROM character_subclasses WHERE charId = ? AND class_index = ? FOR UPDATE";
	private static final String UPDATE_MAIN = "UPDATE characters SET level = ?, exp = ?, expBeforeDeath = ?, sp = ?, curHp = ?, curMp = ?, curCp = ?, x = ?, y = ?, z = ?, heading = ? WHERE charId = ?";
	private static final String UPDATE_SUBCLASS = "UPDATE character_subclasses SET level = ?, exp = ?, sp = ? WHERE charId = ? AND class_index = ?";
	private static final String LOCK_SKILLS = "SELECT skill_id, skill_level FROM character_skills WHERE charId = ? AND class_index = ? ORDER BY skill_id FOR UPDATE";
	private static final String LOCK_SKILL_EXACT = "SELECT skill_level FROM character_skills WHERE charId = ? AND class_index = ? AND skill_id = ? FOR UPDATE";
	private static final String INSERT_SKILL = "INSERT INTO character_skills (charId, skill_id, skill_level, class_index) VALUES (?, ?, ?, ?)";
	private static final String UPDATE_SKILL = "UPDATE character_skills SET skill_level = ? WHERE charId = ? AND skill_id = ? AND class_index = ? AND skill_level = ?";
	private static final String DELETE_SKILL = "DELETE FROM character_skills WHERE charId = ? AND skill_id = ? AND class_index = ? AND skill_level = ?";
	private static final String LOCK_ITEMS = "SELECT object_id, item_id, count, loc FROM items WHERE owner_id = ? AND loc IN ('INVENTORY', 'PAPERDOLL') ORDER BY object_id FOR UPDATE";
	private static final String UPDATE_ITEM = "UPDATE items SET count = ? WHERE object_id = ? AND owner_id = ? AND item_id = ? AND loc = ? AND count = ?";
	private static final String DELETE_ITEM = "DELETE FROM items WHERE object_id = ? AND owner_id = ? AND item_id = ? AND loc = ? AND count = ?";
	private static final String INSERT_ITEM = "INSERT INTO items (owner_id, item_id, count, loc, loc_data, enchant_level, object_id, custom_type1, custom_type2, mana_left, time) VALUES (?, ?, ?, 'INVENTORY', 0, 0, ?, 0, 0, ?, -1)";
	private static final String LOCK_QUEST_ROWS = "SELECT var, value FROM character_quests WHERE charId = ? AND name = ? ORDER BY var FOR UPDATE";

	private final ConnectionProvider _connections;
	private final ObjectIdAllocator _ids;
	private final FaultInjector _faultInjector;
	private final PhantomBackgroundStateCodec _stateCodec;
	private final PhantomGoalStateCodec _goalCodec;
	private final PhantomAcquisitionStateCodec _acquisitionCodec;
	private final PhantomBackgroundCatchupStateCodec _catchupCodec = new PhantomBackgroundCatchupStateCodec();

	public PhantomBackgroundTransaction()
	{
		this(DatabaseFactory::getConnection, ObjectIdAllocator.production(), FaultInjector.none(), new PhantomBackgroundStateCodec(), new PhantomGoalStateCodec(), new PhantomAcquisitionStateCodec());
	}

	public PhantomBackgroundTransaction(ConnectionProvider connections, ObjectIdAllocator ids, FaultInjector faultInjector)
	{
		this(connections, ids, faultInjector, new PhantomBackgroundStateCodec(), new PhantomGoalStateCodec(), new PhantomAcquisitionStateCodec());
	}

	PhantomBackgroundTransaction(ConnectionProvider connections, ObjectIdAllocator ids, FaultInjector faultInjector, PhantomBackgroundStateCodec stateCodec, PhantomGoalStateCodec goalCodec)
	{
		this(connections, ids, faultInjector, stateCodec, goalCodec, new PhantomAcquisitionStateCodec());
	}

	PhantomBackgroundTransaction(ConnectionProvider connections, ObjectIdAllocator ids, FaultInjector faultInjector, PhantomBackgroundStateCodec stateCodec, PhantomGoalStateCodec goalCodec, PhantomAcquisitionStateCodec acquisitionCodec)
	{
		_connections = Objects.requireNonNull(connections, "connections");
		_ids = Objects.requireNonNull(ids, "ids");
		_faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
		_stateCodec = Objects.requireNonNull(stateCodec, "stateCodec");
		_goalCodec = Objects.requireNonNull(goalCodec, "goalCodec");
		_acquisitionCodec = Objects.requireNonNull(acquisitionCodec, "acquisitionCodec");
	}

	public EligibilityResult readAcquisitionEligibility(long profileId, int characterObjectId, int classIndex, int activeClassId, List<Integer> requestedSkillIds, String progressionHash, PhantomBackgroundState.Hashes expectedBackgroundHashes)
	{
		final List<Integer> requested = requestedSkillIds == null ? List.of() : requestedSkillIds.stream().distinct().sorted().toList();
		if ((profileId <= 0) || (characterObjectId <= 0) || (classIndex < 0) || (activeClassId < 0) || requested.isEmpty() || (requested.size() > 8) || requested.stream().anyMatch(skillId -> skillId <= 0) || (progressionHash == null) || !progressionHash.matches("[0-9a-f]{64}") || (expectedBackgroundHashes == null))
		{
			return EligibilityResult.rejected(Status.PROGRESSION_CONFLICT);
		}
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				requireProfileLink(lockProfile(connection, profileId), characterObjectId);
				final LockedComponent component = requireStateComponent(lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE));
				final PhantomBackgroundState state = decodeState(component);
				final Identity identity = state.identity();
				if ((identity.characterObjectId() != characterObjectId) || (identity.classIndex() != classIndex) || (identity.activeClassId() != activeClassId) || !state.hashes().equals(expectedBackgroundHashes))
				{
					throw new StateConflict(Status.PROGRESSION_CONFLICT);
				}
				lockCanonical(connection, identity);
				final Map<Integer, Integer> levels = new LinkedHashMap<>();
				for (int skillId : requested)
				{
					levels.put(skillId, lockExactSkill(connection, identity, skillId));
				}
				connection.rollback();
				return new EligibilityResult(Status.SUCCESS, new AcquisitionEligibilitySnapshot(profileId, characterObjectId, classIndex, activeClassId, levels, progressionHash, state.hashes()));
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return EligibilityResult.rejected(failure instanceof StateConflict conflict ? conflict._status : Status.BACKEND_FAILURE);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			return EligibilityResult.rejected(Status.BACKEND_FAILURE);
		}
	}

	public AcquisitionInventoryResult readAcquisitionInventoryCounts(long profileId, int characterObjectId, int classIndex, int activeClassId, List<Integer> exactItemIds, PhantomBackgroundState.Hashes expectedBackgroundHashes)
	{
		final List<Integer> requested = exactItemIds == null ? List.of() : List.copyOf(exactItemIds);
		if ((profileId <= 0) || (characterObjectId <= 0) || (classIndex < 0) || (activeClassId < 0) || requested.isEmpty() || (requested.size() > 128) || requested.stream().anyMatch(itemId -> itemId == null || itemId <= 0) || !requested.equals(requested.stream().distinct().sorted().toList()) || (expectedBackgroundHashes == null))
		{
			return AcquisitionInventoryResult.rejected(Status.PROGRESSION_CONFLICT);
		}
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				requireProfileLink(lockProfile(connection, profileId), characterObjectId);
				final LockedComponent component = requireStateComponent(lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE));
				final PhantomBackgroundState state = decodeState(component);
				final Identity identity = state.identity();
				if ((identity.characterObjectId() != characterObjectId) || (identity.classIndex() != classIndex) || (identity.activeClassId() != activeClassId) || !state.hashes().equals(expectedBackgroundHashes))
				{
					throw new StateConflict(Status.PROGRESSION_CONFLICT);
				}
				lockCanonical(connection, identity);
				final Map<Integer, Long> counts = readExactInventoryCounts(connection, characterObjectId, requested);
				connection.rollback();
				return new AcquisitionInventoryResult(Status.SUCCESS, new AcquisitionInventorySnapshot(profileId, characterObjectId, classIndex, activeClassId, counts, state.hashes()));
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return AcquisitionInventoryResult.rejected(failure instanceof StateConflict conflict ? conflict._status : Status.BACKEND_FAILURE);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			return AcquisitionInventoryResult.rejected(Status.BACKEND_FAILURE);
		}
	}

	public AcquisitionQuestRowsResult readAcquisitionQuestRows(long profileId, int characterObjectId, int classIndex, int activeClassId, List<String> exactQuestNames, PhantomBackgroundState.Hashes expectedBackgroundHashes)
	{
		final List<String> requested = exactQuestNames == null ? List.of() : List.copyOf(exactQuestNames);
		if ((profileId <= 0) || (characterObjectId <= 0) || (classIndex < 0) || (activeClassId < 0) || requested.isEmpty() || (requested.size() > 4) || requested.stream().anyMatch(name -> (name == null) || name.isBlank() || (name.length() > 96)) || !requested.equals(requested.stream().distinct().sorted().toList()) || (expectedBackgroundHashes == null))
		{
			return AcquisitionQuestRowsResult.rejected(Status.ACQUISITION_CONFLICT);
		}
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				requireProfileLink(lockProfile(connection, profileId), characterObjectId);
				final PhantomBackgroundState state = decodeState(requireStateComponent(lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE)));
				final Identity identity = state.identity();
				if ((identity.characterObjectId() != characterObjectId) || (identity.classIndex() != classIndex) || (identity.activeClassId() != activeClassId) || !state.hashes().equals(expectedBackgroundHashes))
				{
					throw new StateConflict(Status.ACQUISITION_CONFLICT);
				}
				lockCanonical(connection, identity);
				final Map<String, Map<String, String>> rows = new LinkedHashMap<>();
				for (String questName : requested)
				{
					rows.put(questName, lockQuestRows(connection, characterObjectId, questName));
				}
				connection.rollback();
				return new AcquisitionQuestRowsResult(Status.SUCCESS, new AcquisitionQuestRowsSnapshot(profileId, characterObjectId, classIndex, activeClassId, rows, state.hashes()));
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return AcquisitionQuestRowsResult.rejected(failure instanceof StateConflict conflict ? conflict._status : Status.BACKEND_FAILURE);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			return AcquisitionQuestRowsResult.rejected(Status.BACKEND_FAILURE);
		}
	}

	private Map<Integer, Long> readExactInventoryCounts(Connection connection, int characterObjectId, List<Integer> requested) throws SQLException
	{
		final String placeholders = String.join(",", java.util.Collections.nCopies(requested.size(), "?"));
		final String sql = "SELECT item_id, SUM(count) AS item_count FROM items WHERE owner_id = ? AND loc = 'INVENTORY' AND item_id IN (" + placeholders + ") GROUP BY item_id ORDER BY item_id";
		final Map<Integer, Long> counts = new LinkedHashMap<>();
		requested.forEach(itemId -> counts.put(itemId, 0L));
		try (PreparedStatement statement = prepare(connection, sql))
		{
			statement.setInt(1, characterObjectId);
			for (int index = 0; index < requested.size(); index++)
			{
				statement.setInt(index + 2, requested.get(index));
			}
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					final int itemId = result.getInt("item_id");
					if (!counts.containsKey(itemId))
					{
						throw new SQLException("Inventory query returned an unrequested item.");
					}
					counts.put(itemId, result.getLong("item_count"));
				}
			}
		}
		return Map.copyOf(counts);
	}

	public Result captureBaseline(PhantomBackgroundState materializedState, PhantomGoal goal)
	{
		Objects.requireNonNull(materializedState, "materializedState");
		Objects.requireNonNull(goal, "goal");
		if (materializedState.state() != State.MATERIALIZED)
		{
			return Result.rejected(Status.STATE_CONFLICT);
		}
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				requireProfileLink(lockProfile(connection, materializedState.identity().profileId()), materializedState.identity().characterObjectId());
				lockAndValidateGoal(connection, materializedState.identity().profileId(), goal);
				final LockedComponent component = lockComponent(connection, materializedState.identity().profileId(), PhantomBackgroundState.COMPONENT_TYPE);
				final Canonical canonical = lockCanonical(connection, materializedState.identity());
				final List<ItemRow> items = lockItems(connection, materializedState.identity().characterObjectId());
				final Map<Integer, Integer> skills = lockSkills(connection, materializedState.identity());
				final PhantomBackgroundState captured = capturedState(materializedState, canonical, items, skills);
				if (!runtimeProjectionMatches(materializedState, captured))
				{
					throw new StateConflict(Status.CANONICAL_MISMATCH);
				}
				writeComponent(connection, component, captured);
				_faultInjector.inject(FaultPoint.BEFORE_CAPTURE_COMMIT);
				connection.commit();
				return new Result(Status.SUCCESS, captured);
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return failureResult(failure);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			return failureResult(failure);
		}
	}

	public Result markMaterialized(long profileId, int characterObjectId)
	{
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				requireProfileLink(lockProfile(connection, profileId), characterObjectId);
				final LockedComponent component = requireStateComponent(lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE));
				final PhantomBackgroundState current = decodeState(component);
				if ((current.state() != State.READY) && (current.state() != State.DEAD))
				{
					throw new StateConflict(Status.STATE_CONFLICT);
				}
				final Canonical canonical = lockCanonical(connection, current.identity());
				final List<ItemRow> items = lockItems(connection, characterObjectId);
				final Map<Integer, Integer> skills = lockSkills(connection, current.identity());
				if (!durableMatches(current, canonical, items, skills))
				{
					throw new StateConflict(Status.CANONICAL_MISMATCH);
				}
				final PhantomBackgroundState materialized = current.withState(State.MATERIALIZED);
				writeComponent(connection, component, materialized);
				_faultInjector.inject(FaultPoint.BEFORE_MATERIALIZED_COMMIT);
				connection.commit();
				return new Result(Status.SUCCESS, materialized);
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return failureResult(failure);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			return failureResult(failure);
		}
	}

	public Result abortMaterialization(long profileId, int characterObjectId)
	{
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				requireProfileLink(lockProfile(connection, profileId), characterObjectId);
				final LockedComponent component = requireStateComponent(lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE));
				final PhantomBackgroundState current = decodeState(component);
				if (current.state() == State.INCONSISTENT)
				{
					throw new StateConflict(Status.INCONSISTENT);
				}
				final Canonical canonical = lockCanonical(connection, current.identity());
				final Map<Integer, Integer> skills = lockSkills(connection, current.identity());
				final List<ItemRow> items = lockItems(connection, characterObjectId);
				final boolean pendingReceiptMatches = (current.state() != State.VERIFY_PENDING) || current.receipt().expectedAfterHash().equals(expectedAfterHash(current));
				if (!durableMatches(current, canonical, items, skills) || !pendingReceiptMatches)
				{
					final PhantomBackgroundState inconsistent = current.withState(State.INCONSISTENT);
					writeComponent(connection, component, inconsistent);
					connection.commit();
					return new Result(Status.INCONSISTENT, inconsistent);
				}
				final PhantomBackgroundState recovered = current.withState(current.vitals().currentHp() == 0 ? State.DEAD : State.READY);
				if (current.state() != recovered.state())
				{
					writeComponent(connection, component, recovered);
					connection.commit();
				}
				else
				{
					connection.rollback();
				}
				return new Result(Status.SUCCESS, recovered);
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return failureResult(failure);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			return failureResult(failure);
		}
	}

	public Result execute(Command command)
	{
		Objects.requireNonNull(command, "command");
		final List<Integer> reservedIds = new ArrayList<>();
		final List<Integer> releasedIds = new ArrayList<>();
		boolean commitAttempted = false;
		final PhantomEconomyConflictPort.Claim economyClaim;
		if (command.itemDeltas().isEmpty())
		{
			economyClaim = null;
		}
		else
		{
			final PhantomBackgroundState expected = command.expectedState();
			final List<Reservation> resources = command.itemDeltas().keySet().stream().sorted().map(itemId -> new Reservation(expected.identity().profileId(), expected.identity().characterObjectId(), expected.identity().classIndex(), ResourceKind.ITEM_COUNT, 0, itemId, Math.max(1, Math.abs(command.itemDeltas().get(itemId))), expected.inventory().itemCount(itemId), 0, "INVENTORY")).toList();
			economyClaim = PhantomEconomyConflictPort.claim(expected.identity().profileId(), null, resources);
			if (!economyClaim.acquired())
			{
				return new Result(Status.ITEM_CONFLICT, null);
			}
		}
		try (economyClaim; Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				final PhantomBackgroundState expected = command.expectedState();
				requireProfileLink(lockProfile(connection, expected.identity().profileId()), expected.identity().characterObjectId());
				_faultInjector.inject(FaultPoint.AFTER_PROFILE_LOCK);
				final LockedGoal lockedGoal = command.acquisition() == null ? new LockedGoal(lockAndValidateGoal(connection, expected.identity().profileId(), command.goal()), command.goal()) : lockAcquisitionGoal(connection, expected.identity().profileId());
				_faultInjector.inject(FaultPoint.AFTER_GOAL_LOCK);
				final LockedComponent catchupComponent;
				if (command.catchup() != null)
				{
					catchupComponent = lockComponent(connection, expected.identity().profileId(), PhantomBackgroundCatchupState.COMPONENT_TYPE);
					_faultInjector.inject(FaultPoint.AFTER_CATCHUP_LOCK);
				}
				else
				{
					catchupComponent = null;
				}
				final LockedComponent acquisitionComponent;
				if (command.acquisition() != null)
				{
					acquisitionComponent = lockAcquisitionComponent(connection, expected.identity().profileId());
					_faultInjector.inject(FaultPoint.AFTER_ACQUISITION_LOCK);
				}
				else
				{
					acquisitionComponent = null;
				}
				final LockedComponent component = requireStateComponent(lockComponent(connection, expected.identity().profileId(), PhantomBackgroundState.COMPONENT_TYPE));
				final PhantomBackgroundState stored = decodeState(component);
				final Status identityStatus = operationIdentityStatus(stored, command);
				if (identityStatus == Status.IDEMPOTENT)
				{
					if (command.acquisition() != null)
					{
						validateCommittedAcquisition(connection, command, lockedGoal, acquisitionComponent);
					}
					if (command.catchup() != null)
					{
						validateCommittedCatchup(catchupComponent, command.catchup());
					}
					connection.rollback();
					final Result verification = reconcileVerifyPending(expected.identity().profileId(), expected.identity().characterObjectId());
					return verification.status() == Status.SUCCESS ? new Result(Status.IDEMPOTENT, verification.state()) : verification;
				}
				if (identityStatus != Status.SUCCESS)
				{
					throw new StateConflict(identityStatus);
				}
				if (command.catchup() != null)
				{
					validateExpectedCatchupComponent(catchupComponent, command);
				}
				if (command.acquisition() != null)
				{
					validateExpectedAcquisitionGoal(lockedGoal, command.goal(), command.acquisition());
					validateExpectedAcquisitionComponent(acquisitionComponent, command.acquisition());
					validateAcquisitionOperationIdentity(command);
				}
				if (!Arrays.equals(_stateCodec.encode(stored), _stateCodec.encode(expected)))
				{
					throw new StateConflict(Status.STATE_CONFLICT);
				}
				if ((levelForExperience(command.progress().experience()) != command.progress().level()) || !canonicalAutoGetSkills(expected.identity(), command.progress().level()).equals(command.autoGetSkills()))
				{
					throw new StateConflict(Status.PROGRESSION_CONFLICT);
				}
				_faultInjector.inject(FaultPoint.AFTER_BACKGROUND_LOCK);
				final Canonical canonical = lockCanonical(connection, expected.identity());
				_faultInjector.inject(FaultPoint.AFTER_CHARACTER_LOCK);
				final Map<Integer, Integer> skillRows = lockSkills(connection, expected.identity());
				_faultInjector.inject(FaultPoint.AFTER_SKILL_LOCKS);
				if (command.acquisition() != null)
				{
					validateAcquisitionEligibility(command.acquisition(), skillRows);
				}
				final List<ItemRow> itemRows = lockItems(connection, expected.identity().characterObjectId());
				_faultInjector.inject(FaultPoint.AFTER_ITEM_LOCKS);
				Map<String, String> questRows = Map.of();
				if ((command.acquisition() != null) && !command.acquisition().expectedQuestRows().isEmpty())
				{
					questRows = lockQuestRows(connection, expected.identity().characterObjectId(), ((PhantomAcquisitionState.QuestBinding) command.acquisition().expectedState().methodBinding()).questName());
					if (!questRows.equals(command.acquisition().expectedQuestRows()))
					{
						throw new StateConflict(Status.ACQUISITION_CONFLICT);
					}
					_faultInjector.inject(FaultPoint.AFTER_QUEST_LOCKS);
				}
				validateAcquisitionResources(command.acquisition(), itemRows);
				if (!durableMatches(expected, canonical, itemRows, skillRows))
				{
					throw new StateConflict(Status.CANONICAL_MISMATCH);
				}
				final Set<Integer> mutableItemIds = expandedMutableItemIds(expected.inventory(), command.additionalMutableItemIds());
				final ItemMutationResult itemMutation = mutateItems(connection, expected, itemRows, command.itemDeltas(), mutableItemIds, reservedIds, releasedIds);
				final Vitals canonicalVitals = canonicalVitals(command.vitals());
				mutateProgressAndVitals(connection, expected.identity(), command.progress(), canonicalVitals, command.position());
				mutateAutoGetSkills(connection, expected.identity(), skillRows, expected.autoGetSkills(), command.autoGetSkills());
				_faultInjector.inject(FaultPoint.AFTER_CANONICAL_WRITES);
				final InventoryFacts inventoryProjection = new InventoryFacts(mutableItemIds.stream().sorted().toList(), expected.inventory().objects(), expected.inventory().canonicalHash(), expected.inventory().currentLoad(), expected.inventory().maximumLoad(), expected.inventory().usedSlots(), expected.inventory().maximumSlots());
				final InventoryFacts nextInventory = inventoryFacts(itemMutation.rows(), inventoryProjection);
				final Receipt receiptWithoutHash = new Receipt(command.operationKey().digest(), command.operationKey().activityGeneration(), command.operationKey().tickSequence(), "");
				final PhantomBackgroundState ready = expected.after(command.progress(), canonicalVitals, command.position(), nextInventory, command.autoGetSkills(), command.clock(), receiptWithoutHash);
				final String expectedAfterHash = expectedAfterHash(ready);
				final Receipt receipt = new Receipt(receiptWithoutHash.operationKey(), receiptWithoutHash.activityGeneration(), receiptWithoutHash.tickSequence(), expectedAfterHash);
				final PhantomBackgroundState completed = expected.after(command.progress(), canonicalVitals, command.position(), nextInventory, command.autoGetSkills(), command.clock(), receipt);
				final PhantomBackgroundState pending = completed.withState(State.VERIFY_PENDING);
				writeComponent(connection, component, pending);
				_faultInjector.inject(FaultPoint.AFTER_BACKGROUND_STATE_WRITE);
				if (command.catchup() != null)
				{
					writeRawComponent(connection, catchupComponent, expected.identity().profileId(), PhantomBackgroundCatchupState.COMPONENT_TYPE, PhantomBackgroundCatchupState.SCHEMA_VERSION, _catchupCodec.encode(command.catchup().nextState()));
					_faultInjector.inject(FaultPoint.AFTER_CATCHUP_STATE_WRITE);
				}
				PhantomAcquisitionState nextAcquisition = null;
				PhantomGoal nextGoal = null;
				if (command.acquisition() != null)
				{
					final AcquisitionMutation acquisition = command.acquisition();
					final long beforeCount = expected.inventory().itemCount(acquisition.expectedState().targetItemId());
					final long afterCount = nextInventory.itemCount(acquisition.expectedState().targetItemId());
					final var acquisitionReceipt = new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Receipt(command.operationKey().digest(), acquisition.expectedState().selectedSource().sourceId(), acquisition.receiptKind(), beforeCount, afterCount, TerminalResult.COMMITTED, acquisition.logicalMinute());
					nextAcquisition = advanceAcquisitionBinding(acquisition.expectedState().observe(afterCount, PhantomAcquisitionState.Status.READY, Phase.NONE, acquisitionReceipt, acquisition.logicalMinute()), nextInventory);
					nextGoal = PhantomAcquisitionGoalSpec.project(lockedGoal.goal(), nextAcquisition.progress(), nextAcquisition.status() == PhantomAcquisitionState.Status.COMPLETED ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.ACTIVE, nextAcquisition.selectedSource());
					writeRawComponent(connection, lockedGoal.component(), expected.identity().profileId(), PhantomGoalStateStore.COMPONENT_TYPE, PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION, _goalCodec.encode(nextGoal));
					_faultInjector.inject(FaultPoint.AFTER_GOAL_STATE_WRITE);
					writeRawComponent(connection, acquisitionComponent, expected.identity().profileId(), PhantomAcquisitionState.COMPONENT_TYPE, PhantomAcquisitionState.SCHEMA_VERSION, _acquisitionCodec.encode(nextAcquisition));
					_faultInjector.inject(FaultPoint.AFTER_ACQUISITION_STATE_WRITE);
					if (!questRows.isEmpty() && !questRows.equals(lockQuestRows(connection, expected.identity().characterObjectId(), ((PhantomAcquisitionState.QuestBinding) acquisition.expectedState().methodBinding()).questName())))
					{
						throw new StateConflict(Status.ACQUISITION_CONFLICT);
					}
				}
				_faultInjector.inject(FaultPoint.BEFORE_OPERATION_COMMIT);
				commitAttempted = true;
				connection.commit();
				for (int objectId : releasedIds)
				{
					_ids.release(objectId);
				}
				_faultInjector.inject(FaultPoint.AFTER_OPERATION_COMMIT);
				final Result verification = reconcileVerifyPending(expected.identity().profileId(), expected.identity().characterObjectId());
				return verification.status() == PhantomBackgroundTransaction.Status.SUCCESS ? new Result(PhantomBackgroundTransaction.Status.SUCCESS, verification.state(), nextAcquisition, nextGoal) : new Result(PhantomBackgroundTransaction.Status.POST_COMMIT_VERIFICATION_FAILED, pending, nextAcquisition, nextGoal);
			}
			catch (Throwable failure)
			{
				if (!commitAttempted)
				{
					rollback(connection, failure);
					for (int objectId : reservedIds)
					{
						_ids.release(objectId);
					}
					return failureResult(failure);
				}
				return new Result(Status.COMMIT_OUTCOME_UNKNOWN, null);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			if (!commitAttempted)
			{
				for (int objectId : reservedIds)
				{
					_ids.release(objectId);
				}
			}
			return new Result(commitAttempted ? Status.COMMIT_OUTCOME_UNKNOWN : Status.BACKEND_FAILURE, null);
		}
	}

	public Result reconcileVerifyPending(long profileId, int characterObjectId)
	{
		try (Connection connection = _connections.open())
		{
			connection.setAutoCommit(false);
			try
			{
				requireProfileLink(lockProfile(connection, profileId), characterObjectId);
				final LockedComponent component = requireStateComponent(lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE));
				final PhantomBackgroundState pending = decodeState(component);
				if (pending.state() == State.INCONSISTENT)
				{
					throw new StateConflict(Status.INCONSISTENT);
				}
				final Canonical canonical = lockCanonical(connection, pending.identity());
				final Map<Integer, Integer> skills = lockSkills(connection, pending.identity());
				final List<ItemRow> items = lockItems(connection, characterObjectId);
				final boolean pendingReceiptMatches = (pending.state() != State.VERIFY_PENDING) || pending.receipt().expectedAfterHash().equals(expectedAfterHash(pending));
				if (!durableMatches(pending, canonical, items, skills) || !pendingReceiptMatches)
				{
					final PhantomBackgroundState inconsistent = pending.withState(State.INCONSISTENT);
					writeComponent(connection, component, inconsistent);
					connection.commit();
					return new Result(Status.INCONSISTENT, inconsistent);
				}
				if (pending.state() != State.VERIFY_PENDING)
				{
					connection.rollback();
					return new Result(Status.SUCCESS, pending);
				}
				final PhantomBackgroundState promoted = pending.withState(pending.vitals().currentHp() == 0 ? State.DEAD : State.READY);
				writeComponent(connection, component, promoted);
				_faultInjector.inject(FaultPoint.BEFORE_VERIFY_COMMIT);
				connection.commit();
				return new Result(Status.SUCCESS, promoted);
			}
			catch (Throwable failure)
			{
				rollback(connection, failure);
				return failureResult(failure);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			return failureResult(failure);
		}
	}

	public Result load(long profileId)
	{
		try (Connection connection = _connections.open())
		{
			final LockedComponent component = lockComponent(connection, profileId, PhantomBackgroundState.COMPONENT_TYPE);
			return component == null ? new Result(Status.STATE_ABSENT, null) : new Result(Status.SUCCESS, decodeState(component));
		}
		catch (SQLException | RuntimeException failure)
		{
			return Result.rejected(Status.BACKEND_FAILURE);
		}
	}

	private LockedComponent lockAndValidateGoal(Connection connection, long profileId, PhantomGoal expected) throws SQLException
	{
		final LockedComponent goalComponent = lockComponent(connection, profileId, PhantomGoalStateStore.COMPONENT_TYPE);
		if ((goalComponent == null) || (goalComponent.schemaVersion() != PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION))
		{
			throw new StateConflict(Status.GOAL_STALE);
		}
		final PhantomGoal actual;
		try
		{
			actual = _goalCodec.decode(goalComponent.payload());
		}
		catch (RuntimeException failure)
		{
			throw new StateConflict(Status.GOAL_STALE);
		}
		if ((actual.status() != PhantomGoalStatus.ACTIVE) || !Arrays.equals(_goalCodec.encode(expected), _goalCodec.encode(actual)))
		{
			throw new StateConflict(Status.GOAL_STALE);
		}
		return goalComponent;
	}

	private LockedGoal lockAcquisitionGoal(Connection connection, long profileId) throws SQLException
	{
		final LockedComponent component = lockComponent(connection, profileId, PhantomGoalStateStore.COMPONENT_TYPE);
		if ((component == null) || (component.schemaVersion() != PhantomGoalStateStore.COMPONENT_SCHEMA_VERSION))
		{
			throw new StateConflict(Status.GOAL_STALE);
		}
		final PhantomGoal actual;
		try
		{
			actual = _goalCodec.decode(component.payload());
			if (actual.status() == PhantomGoalStatus.ACTIVE)
			{
				PhantomAcquisitionGoalSpec.parse(actual);
			}
			else if (actual.status() == PhantomGoalStatus.COMPLETED)
			{
				PhantomAcquisitionGoalSpec.project(actual, actual.currentAmount(), PhantomGoalStatus.COMPLETED, null);
			}
			else
			{
				throw new IllegalArgumentException("Acquisition Goal is not executable or completed.");
			}
		}
		catch (RuntimeException failure)
		{
			throw new StateConflict(Status.GOAL_STALE);
		}
		return new LockedGoal(component, actual);
	}

	private LockedComponent lockAcquisitionComponent(Connection connection, long profileId) throws SQLException
	{
		final LockedComponent component = lockComponent(connection, profileId, PhantomAcquisitionState.COMPONENT_TYPE);
		if ((component == null) || ((component.schemaVersion() != PhantomAcquisitionState.LEGACY_SCHEMA_VERSION) && (component.schemaVersion() != PhantomAcquisitionState.DISPATCH_SCHEMA_VERSION) && (component.schemaVersion() != PhantomAcquisitionState.SCHEMA_VERSION)))
		{
			throw new StateConflict(Status.ACQUISITION_CONFLICT);
		}
		return component;
	}

	private void validateExpectedCatchupComponent(LockedComponent component, Command command)
	{
		final CatchupMutation mutation = command.catchup();
		final var identity = command.operationKey().historical();
		if ((component == null) || (component.schemaVersion() != PhantomBackgroundCatchupState.SCHEMA_VERSION) || (component.rowVersion() != mutation.expectedRowVersion()) || (identity == null))
		{
			throw new StateConflict(Status.CATCHUP_CONFLICT);
		}
		final PhantomBackgroundCatchupState actual;
		try
		{
			actual = _catchupCodec.decode(component.payload());
		}
		catch (RuntimeException exception)
		{
			throw new StateConflict(Status.CATCHUP_CONFLICT);
		}
		if (!Arrays.equals(_catchupCodec.encode(actual), _catchupCodec.encode(mutation.expectedState())) || !identity.requestId().equals(actual.requestId()) || (identity.catchupGeneration() != actual.generation()) || (identity.intervalOrdinal() != actual.intervalOrdinal()) || (identity.fromEpochMinute() != actual.cursorEpochMinute()) || (identity.nextEpochMinute() != mutation.nextState().cursorEpochMinute()) || !identity.planIdentity().equals(actual.planIdentity()))
		{
			throw new StateConflict(Status.CATCHUP_CONFLICT);
		}
	}

	private void validateCommittedCatchup(LockedComponent component, CatchupMutation mutation)
	{
		if ((component == null) || (component.schemaVersion() != PhantomBackgroundCatchupState.SCHEMA_VERSION) || (component.rowVersion() != Math.addExact(mutation.expectedRowVersion(), 1)))
		{
			throw new StateConflict(Status.CATCHUP_CONFLICT);
		}
		try
		{
			if (!Arrays.equals(_catchupCodec.encode(_catchupCodec.decode(component.payload())), _catchupCodec.encode(mutation.nextState())))
			{
				throw new StateConflict(Status.CATCHUP_CONFLICT);
			}
		}
		catch (RuntimeException exception)
		{
			if (exception instanceof StateConflict conflict)
			{
				throw conflict;
			}
			throw new StateConflict(Status.CATCHUP_CONFLICT);
		}
	}

	private void validateExpectedAcquisitionGoal(LockedGoal locked, PhantomGoal expected, AcquisitionMutation acquisition)
	{
		if ((locked.component().rowVersion() != acquisition.expectedGoalRowVersion()) || !Arrays.equals(_goalCodec.encode(expected), _goalCodec.encode(locked.goal())) || (locked.goal().goalId() != acquisition.expectedState().goalId()) || (locked.goal().revision() != acquisition.expectedState().goalRevision()))
		{
			throw new StateConflict(Status.GOAL_STALE);
		}
	}

	private void validateExpectedAcquisitionComponent(LockedComponent component, AcquisitionMutation acquisition)
	{
		if (component.rowVersion() != acquisition.expectedStateRowVersion())
		{
			throw new StateConflict(Status.ACQUISITION_CONFLICT);
		}
		try
		{
			final PhantomAcquisitionState actual = _acquisitionCodec.decode(component.payload());
			if (!Arrays.equals(_acquisitionCodec.encode(actual), _acquisitionCodec.encode(acquisition.expectedState())) || (acquisition.expectedState().selectedSource() == null))
			{
				throw new StateConflict(Status.ACQUISITION_CONFLICT);
			}
		}
		catch (RuntimeException failure)
		{
			if (failure instanceof StateConflict conflict)
			{
				throw conflict;
			}
			throw new StateConflict(Status.ACQUISITION_CONFLICT);
		}
	}

	private static void validateAcquisitionOperationIdentity(Command command)
	{
		final var identity = command.operationKey().acquisition();
		final PhantomAcquisitionState state = command.acquisition().expectedState();
		if ((identity == null) || !identity.sourceId().equals(state.selectedSource().sourceId()) || (identity.expectedAcquisitionRowVersion() != command.acquisition().expectedStateRowVersion()) || (identity.targetItemId() != state.targetItemId()) || !identity.catalogHash().equals(state.hashes().catalog()) || !identity.backgroundHash().equals(state.hashes().background()) || !identity.methodBindingHash().equals(bindingHash(state.methodBinding())))
		{
			throw new StateConflict(Status.ACQUISITION_CONFLICT);
		}
		final long expectedResource = state.methodBinding() instanceof PhantomAcquisitionState.ManorBinding manor ? manor.seedCountBeforeDispatch() : state.methodBinding() instanceof PhantomAcquisitionState.QuestBinding quest ? quest.itemCountBeforeKill() : 0;
		if (identity.expectedResourceCount() != expectedResource)
		{
			throw new StateConflict(Status.ACQUISITION_CONFLICT);
		}
	}

	private static String bindingHash(PhantomAcquisitionState.MethodBinding binding)
	{
		try
		{
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Objects.toString(binding, "none").getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static void validateAcquisitionEligibility(AcquisitionMutation acquisition, Map<Integer, Integer> lockedSkills)
	{
		final PhantomAcquisitionState.Source source = acquisition.expectedState().selectedSource();
		if (source.method() != org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method.SPOIL_SWEEP)
		{
			if (!acquisition.eligibilitySkills().isEmpty())
			{
				throw new StateConflict(Status.PROGRESSION_CONFLICT);
			}
			return;
		}
		final Map<Integer, Integer> expected = Map.of(source.spoilSkillId(), source.spoilSkillLevel(), source.sweepSkillId(), source.sweepSkillLevel());
		if (!expected.equals(acquisition.eligibilitySkills()))
		{
			throw new StateConflict(Status.PROGRESSION_CONFLICT);
		}
		for (var skill : expected.entrySet())
		{
			if (!skill.getValue().equals(lockedSkills.get(skill.getKey())))
			{
				throw new StateConflict(Status.PROGRESSION_CONFLICT);
			}
		}
	}

	private void validateCommittedAcquisition(Connection connection, Command command, LockedGoal lockedGoal, LockedComponent component) throws SQLException
	{
		final AcquisitionMutation acquisition = command.acquisition();
		try
		{
			if ((lockedGoal.component().rowVersion() != Math.addExact(acquisition.expectedGoalRowVersion(), 1)) || (component.rowVersion() != Math.addExact(acquisition.expectedStateRowVersion(), 1)))
			{
				throw new StateConflict(Status.ACQUISITION_CONFLICT);
			}
			final PhantomAcquisitionState expected = acquisition.expectedState();
			final long beforeCount = command.expectedState().inventory().itemCount(expected.targetItemId());
			final long afterCount = Math.addExact(beforeCount, command.itemDeltas().getOrDefault(expected.targetItemId(), 0L));
			final var receipt = new org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionState.Receipt(command.operationKey().digest(), expected.selectedSource().sourceId(), acquisition.receiptKind(), beforeCount, afterCount, TerminalResult.COMMITTED, acquisition.logicalMinute());
			final Map<Integer, Long> resultingCounts = new LinkedHashMap<>();
			resultingCounts.put(expected.targetItemId(), afterCount);
			if (expected.methodBinding() instanceof PhantomAcquisitionState.ManorBinding manor)
			{
				resultingCounts.put(manor.seedItemId(), Math.addExact(manor.seedCountBeforeDispatch(), command.itemDeltas().getOrDefault(manor.seedItemId(), 0L)));
			}
			final PhantomAcquisitionState expectedCommitted = advanceAcquisitionBinding(expected.observe(afterCount, PhantomAcquisitionState.Status.READY, Phase.NONE, receipt, acquisition.logicalMinute()), resultingCounts);
			final PhantomAcquisitionState actual = _acquisitionCodec.decode(component.payload());
			if (!Arrays.equals(_acquisitionCodec.encode(expectedCommitted), _acquisitionCodec.encode(actual)))
			{
				throw new StateConflict(Status.ACQUISITION_CONFLICT);
			}
			final PhantomGoal expectedGoal = PhantomAcquisitionGoalSpec.project(command.goal(), expectedCommitted.progress(), expectedCommitted.status() == PhantomAcquisitionState.Status.COMPLETED ? PhantomGoalStatus.COMPLETED : PhantomGoalStatus.ACTIVE, expectedCommitted.selectedSource());
			if (!Arrays.equals(_goalCodec.encode(expectedGoal), _goalCodec.encode(lockedGoal.goal())))
			{
				throw new StateConflict(Status.GOAL_STALE);
			}
			if (!acquisition.expectedQuestRows().isEmpty() && !acquisition.expectedQuestRows().equals(lockQuestRows(connection, command.expectedState().identity().characterObjectId(), ((PhantomAcquisitionState.QuestBinding) expected.methodBinding()).questName())))
			{
				throw new StateConflict(Status.ACQUISITION_CONFLICT);
			}
		}
		catch (RuntimeException failure)
		{
			if (failure instanceof StateConflict conflict)
			{
				throw conflict;
			}
			throw new StateConflict(Status.ACQUISITION_CONFLICT);
		}
	}

	private static PhantomAcquisitionState advanceAcquisitionBinding(PhantomAcquisitionState state, InventoryFacts inventory)
	{
		final Map<Integer, Long> counts = new LinkedHashMap<>();
		counts.put(state.targetItemId(), inventory.itemCount(state.targetItemId()));
		if (state.methodBinding() instanceof PhantomAcquisitionState.ManorBinding manor)
		{
			counts.put(manor.seedItemId(), inventory.itemCount(manor.seedItemId()));
		}
		return advanceAcquisitionBinding(state, counts);
	}

	private static PhantomAcquisitionState advanceAcquisitionBinding(PhantomAcquisitionState state, Map<Integer, Long> counts)
	{
		if (state.methodBinding() instanceof PhantomAcquisitionState.ManorBinding manor)
		{
			return state.withMethodBinding(new PhantomAcquisitionState.ManorBinding(manor.castleId(), manor.seedItemId(), manor.cropItemId(), manor.matureItemId(), manor.reward1ItemId(), manor.reward2ItemId(), manor.seedLevel(), manor.alternative(), manor.rawSeedLimit(), manor.rawCropLimit(), 0, 0, counts.getOrDefault(manor.seedItemId(), 0L), counts.getOrDefault(manor.cropItemId(), state.lastObservedCount()), manor.authorityHash()));
		}
		if (state.methodBinding() instanceof PhantomAcquisitionState.QuestBinding quest)
		{
			final long count = counts.getOrDefault(quest.questItemId(), state.lastObservedCount());
			if (count > quest.itemCap())
			{
				throw new StateConflict(Status.ACQUISITION_CONFLICT);
			}
			final long bindingCount = count == quest.itemCap() ? quest.itemCountBeforeKill() : count;
			PhantomAcquisitionState advanced = state.withMethodBinding(new PhantomAcquisitionState.QuestBinding(quest.ruleId(), quest.ruleHash(), quest.questId(), quest.questName(), quest.scriptHash(), quest.expectedState(), quest.expectedCond(), quest.questItemId(), quest.itemCap(), quest.targetNpcId(), bindingCount, 0, quest.authorityHash()));
			if ((count == quest.itemCap()) && (advanced.status() != PhantomAcquisitionState.Status.COMPLETED))
			{
				advanced = advanced.failSource("quest.item_cap", state.logicalMinute());
			}
			return advanced;
		}
		return state;
	}

	private static void validateAcquisitionResources(AcquisitionMutation acquisition, List<ItemRow> itemRows)
	{
		if ((acquisition == null) || !(acquisition.expectedState().methodBinding() instanceof PhantomAcquisitionState.ManorBinding manor))
		{
			return;
		}
		final long count = itemRows.stream().filter(row -> (row.location() == ItemLocation.INVENTORY) && (row.itemId() == manor.seedItemId())).mapToLong(ItemRow::count).sum();
		if ((count != manor.seedCountBeforeDispatch()) || (count <= 0))
		{
			throw new StateConflict(Status.ITEM_CONFLICT);
		}
	}

	private Map<String, String> lockQuestRows(Connection connection, int characterObjectId, String questName) throws SQLException
	{
		final Map<String, String> rows = new LinkedHashMap<>();
		try (PreparedStatement statement = prepare(connection, LOCK_QUEST_ROWS))
		{
			statement.setInt(1, characterObjectId);
			statement.setString(2, questName);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					if ((rows.size() >= 6) || (rows.putIfAbsent(result.getString("var"), result.getString("value")) != null))
					{
						throw new StateConflict(Status.ACQUISITION_CONFLICT);
					}
				}
			}
		}
		return Map.copyOf(rows);
	}

	private Status operationIdentityStatus(PhantomBackgroundState stored, Command command)
	{
		if (stored.state() == State.INCONSISTENT)
		{
			return Status.INCONSISTENT;
		}
		final String digest = command.operationKey().digest();
		if (stored.receipt().operationKey().equals(digest))
		{
			return Status.IDEMPOTENT;
		}
		final boolean historicalDeadIdle = (stored.state() == State.DEAD) && (command.operationKey().historical() != null) && (command.operationKey().actionKind() == PhantomBackgroundOperationKey.ActionKind.HISTORICAL_DEAD_IDLE);
		if ((stored.state() != State.READY) && !historicalDeadIdle)
		{
			return Status.STATE_CONFLICT;
		}
		if ((command.operationKey().historical() == null) && ((stored.receipt().activityGeneration() > command.operationKey().activityGeneration()) || ((stored.receipt().activityGeneration() == command.operationKey().activityGeneration()) && (stored.receipt().tickSequence() >= command.operationKey().tickSequence()))))
		{
			return Status.STALE_OPERATION;
		}
		if (!stored.hashes().equals(command.operationKey().hashes()))
		{
			return Status.HASH_STALE;
		}
		return Status.SUCCESS;
	}

	private Canonical lockCanonical(Connection connection, Identity identity) throws SQLException
	{
		final CharacterRow character = lockCharacter(connection, identity.characterObjectId());
		if (character == null)
		{
			throw new StateConflict(Status.CANONICAL_MISMATCH);
		}
		if (identity.classIndex() == 0)
		{
			if (character.classId() != identity.activeClassId())
			{
				throw new StateConflict(Status.CANONICAL_MISMATCH);
			}
			if (character.raceOrdinal() != identity.raceOrdinal())
			{
				throw new StateConflict(Status.CANONICAL_MISMATCH);
			}
			return new Canonical(character.level(), character.experience(), character.skillPoints(), character.experienceBeforeDeath(), character.vitals(), character.position(), character.classId(), character.raceOrdinal());
		}
		final SubclassRow subclass = lockSubclass(connection, identity.characterObjectId(), identity.classIndex());
		if ((subclass == null) || (subclass.classId() != identity.activeClassId()))
		{
			throw new StateConflict(Status.CANONICAL_MISMATCH);
		}
		if (character.raceOrdinal() != identity.raceOrdinal())
		{
			throw new StateConflict(Status.CANONICAL_MISMATCH);
		}
		return new Canonical(subclass.level(), subclass.experience(), subclass.skillPoints(), character.experienceBeforeDeath(), character.vitals(), character.position(), subclass.classId(), character.raceOrdinal());
	}

	private CharacterRow lockCharacter(Connection connection, int characterObjectId) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, LOCK_CHARACTER))
		{
			statement.setInt(1, characterObjectId);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}
				final Vitals vitals = new Vitals(result.getDouble("curHp"), result.getDouble("maxHp"), result.getDouble("curMp"), result.getDouble("maxMp"), result.getDouble("curCp"), result.getDouble("maxCp"));
				final Position position = new Position(0, result.getInt("x"), result.getInt("y"), result.getInt("z"), result.getInt("heading"), "pending");
				final CharacterRow row = new CharacterRow(result.getInt("level"), result.getLong("exp"), result.getLong("sp"), result.getLong("expBeforeDeath"), vitals, position, result.getInt("classid"), result.getInt("race"));
				if (result.next())
				{
					throw new SQLException("Duplicate character row.");
				}
				return row;
			}
		}
	}

	private SubclassRow lockSubclass(Connection connection, int characterObjectId, int classIndex) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, LOCK_SUBCLASS))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, classIndex);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}
				final SubclassRow row = new SubclassRow(result.getInt("level"), result.getLong("exp"), result.getLong("sp"), result.getInt("class_id"));
				if (result.next())
				{
					throw new SQLException("Duplicate subclass row.");
				}
				return row;
			}
		}
	}

	private Map<Integer, Integer> lockSkills(Connection connection, Identity identity) throws SQLException
	{
		final Map<Integer, Integer> result = new LinkedHashMap<>();
		try (PreparedStatement statement = prepare(connection, LOCK_SKILLS))
		{
			statement.setInt(1, identity.characterObjectId());
			statement.setInt(2, identity.classIndex());
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					if (result.put(rows.getInt("skill_id"), rows.getInt("skill_level")) != null)
					{
						throw new SQLException("Duplicate character skill row.");
					}
				}
			}
		}
		return result;
	}

	private int lockExactSkill(Connection connection, Identity identity, int skillId) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, LOCK_SKILL_EXACT))
		{
			statement.setInt(1, identity.characterObjectId());
			statement.setInt(2, identity.classIndex());
			statement.setInt(3, skillId);
			try (ResultSet rows = statement.executeQuery())
			{
				if (!rows.next())
				{
					return 0;
				}
				final int level = rows.getInt(1);
				if (rows.next())
				{
					throw new SQLException("Duplicate exact character skill row.");
				}
				return level;
			}
		}
	}

	private List<ItemRow> lockItems(Connection connection, int characterObjectId) throws SQLException
	{
		final List<ItemRow> result = new ArrayList<>();
		try (PreparedStatement statement = prepare(connection, LOCK_ITEMS))
		{
			statement.setInt(1, characterObjectId);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
				{
					final String location = rows.getString("loc");
					result.add(new ItemRow(rows.getInt("object_id"), rows.getInt("item_id"), rows.getLong("count"), ItemLocation.valueOf(location)));
				}
			}
		}
		return result;
	}

	private ItemMutationResult mutateItems(Connection connection, PhantomBackgroundState expected, List<ItemRow> lockedRows, Map<Integer, Long> deltas, Set<Integer> mutableItemIds, List<Integer> reservedIds, List<Integer> releasedIds) throws SQLException
	{
		if (deltas.size() > PhantomBackgroundModel.MAX_CHANGED_ITEM_OBJECTS)
		{
			throw new StateConflict(Status.ITEM_LIMIT);
		}
		final List<ItemRow> rows = new ArrayList<>(lockedRows);
		int changedObjects = 0;
		int newNonStackable = 0;
		for (Map.Entry<Integer, Long> mutation : deltas.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList())
		{
			final int itemId = mutation.getKey();
			long delta = mutation.getValue();
			if (delta == 0)
			{
				continue;
			}
			if (!mutableItemIds.contains(itemId))
			{
				throw new StateConflict(Status.ITEM_CONFLICT);
			}
			final ItemTemplate template = ItemData.getInstance().getTemplate(itemId);
			if ((template == null) || (template.getTime() != -1))
			{
				throw new StateConflict(Status.UNSUPPORTED_ITEM);
			}
			if (delta < 0)
			{
				long remaining = -delta;
				for (int index = 0; (index < rows.size()) && (remaining > 0); index++)
				{
					final ItemRow row = rows.get(index);
					if ((row.itemId() != itemId) || (row.location() != ItemLocation.INVENTORY))
					{
						continue;
					}
					final long consumed = Math.min(remaining, row.count());
					final long nextCount = row.count() - consumed;
					if (nextCount == 0)
					{
						deleteItem(connection, expected.identity().characterObjectId(), row);
						rows.remove(index--);
						releasedIds.add(row.objectId());
					}
					else
					{
						updateItem(connection, expected.identity().characterObjectId(), row, nextCount);
						rows.set(index, row.withCount(nextCount));
					}
					remaining -= consumed;
					changedObjects++;
				}
				if (remaining != 0)
				{
					throw new StateConflict(Status.ITEM_CONFLICT);
				}
			}
			else if (template.isStackable())
			{
				final ItemRow stack = rows.stream().filter(row -> (row.itemId() == itemId) && (row.location() == ItemLocation.INVENTORY)).min(Comparator.comparingInt(ItemRow::objectId)).orElse(null);
				if (stack != null)
				{
					final long nextCount = Math.addExact(stack.count(), delta);
					updateItem(connection, expected.identity().characterObjectId(), stack, nextCount);
					rows.set(rows.indexOf(stack), stack.withCount(nextCount));
					changedObjects++;
				}
				else
				{
					final int objectId = reserveId(reservedIds);
					insertItem(connection, expected.identity().characterObjectId(), objectId, itemId, delta, template.getDuration());
					rows.add(new ItemRow(objectId, itemId, delta, ItemLocation.INVENTORY));
					changedObjects++;
				}
			}
			else
			{
				if (delta > (PhantomBackgroundModel.MAX_NEW_NON_STACKABLE_OBJECTS - newNonStackable))
				{
					throw new StateConflict(Status.ITEM_LIMIT);
				}
				for (long index = 0; index < delta; index++)
				{
					final int objectId = reserveId(reservedIds);
					insertItem(connection, expected.identity().characterObjectId(), objectId, itemId, 1, template.getDuration());
					rows.add(new ItemRow(objectId, itemId, 1, ItemLocation.INVENTORY));
					newNonStackable++;
					changedObjects++;
				}
			}
			if (changedObjects > PhantomBackgroundModel.MAX_CHANGED_ITEM_OBJECTS)
			{
				throw new StateConflict(Status.ITEM_LIMIT);
			}
		}
		rows.sort(Comparator.comparingInt(ItemRow::objectId));
		return new ItemMutationResult(List.copyOf(rows));
	}

	private static Set<Integer> expandedMutableItemIds(InventoryFacts inventory, List<Integer> additions)
	{
		final java.util.TreeSet<Integer> result = new java.util.TreeSet<>(inventory.mutableItemIds());
		result.addAll(additions);
		if ((result.size() > PhantomBackgroundState.MAX_MUTABLE_ITEM_IDS) || result.stream().anyMatch(itemId -> itemId <= 0))
		{
			throw new StateConflict(Status.ITEM_LIMIT);
		}
		return Set.copyOf(result);
	}

	private void mutateProgressAndVitals(Connection connection, Identity identity, Progress progress, Vitals vitals, Position position) throws SQLException
	{
		if (position.instanceId() != 0)
		{
			throw new StateConflict(Status.UNSUPPORTED_INSTANCE);
		}
		if (identity.classIndex() == 0)
		{
			try (PreparedStatement statement = prepare(connection, UPDATE_MAIN))
			{
				statement.setInt(1, progress.level());
				statement.setLong(2, progress.experience());
				statement.setLong(3, progress.experienceBeforeDeath());
				statement.setLong(4, progress.skillPoints());
				statement.setDouble(5, vitals.currentHp());
				statement.setDouble(6, vitals.currentMp());
				statement.setDouble(7, vitals.currentCp());
				statement.setInt(8, position.x());
				statement.setInt(9, position.y());
				statement.setInt(10, position.z());
				statement.setInt(11, position.heading());
				statement.setInt(12, identity.characterObjectId());
				requireOne(statement.executeUpdate(), "main character update");
			}
		}
		else
		{
			try (PreparedStatement statement = prepare(connection, UPDATE_SUBCLASS))
			{
				statement.setInt(1, progress.level());
				statement.setLong(2, progress.experience());
				statement.setLong(3, progress.skillPoints());
				statement.setInt(4, identity.characterObjectId());
				statement.setInt(5, identity.classIndex());
				requireOne(statement.executeUpdate(), "subclass update");
			}
			try (PreparedStatement statement = prepare(connection, "UPDATE characters SET expBeforeDeath = ?, curHp = ?, curMp = ?, curCp = ?, x = ?, y = ?, z = ?, heading = ? WHERE charId = ?"))
			{
				statement.setLong(1, progress.experienceBeforeDeath());
				statement.setDouble(2, vitals.currentHp());
				statement.setDouble(3, vitals.currentMp());
				statement.setDouble(4, vitals.currentCp());
				statement.setInt(5, position.x());
				statement.setInt(6, position.y());
				statement.setInt(7, position.z());
				statement.setInt(8, position.heading());
				statement.setInt(9, identity.characterObjectId());
				requireOne(statement.executeUpdate(), "subclass character vitals update");
			}
		}
	}

	private void mutateAutoGetSkills(Connection connection, Identity identity, Map<Integer, Integer> current, List<AutoGetSkill> prior, List<AutoGetSkill> desired) throws SQLException
	{
		final Map<Integer, Integer> wanted = new LinkedHashMap<>();
		for (AutoGetSkill skill : desired)
		{
			wanted.put(skill.skillId(), skill.skillLevel());
		}
		for (AutoGetSkill skill : desired)
		{
			final Integer oldLevel = current.get(skill.skillId());
			if (oldLevel == null)
			{
				try (PreparedStatement statement = prepare(connection, INSERT_SKILL))
				{
					statement.setInt(1, identity.characterObjectId());
					statement.setInt(2, skill.skillId());
					statement.setInt(3, skill.skillLevel());
					statement.setInt(4, identity.classIndex());
					requireOne(statement.executeUpdate(), "auto-get skill insert");
				}
			}
			else if (oldLevel != skill.skillLevel())
			{
				try (PreparedStatement statement = prepare(connection, UPDATE_SKILL))
				{
					statement.setInt(1, skill.skillLevel());
					statement.setInt(2, identity.characterObjectId());
					statement.setInt(3, skill.skillId());
					statement.setInt(4, identity.classIndex());
					statement.setInt(5, oldLevel);
					requireOne(statement.executeUpdate(), "auto-get skill update");
				}
			}
		}
		for (AutoGetSkill oldSkill : prior)
		{
			if (wanted.containsKey(oldSkill.skillId()))
			{
				continue;
			}
			if (!Integer.valueOf(oldSkill.skillLevel()).equals(current.get(oldSkill.skillId())))
			{
				throw new StateConflict(Status.CANONICAL_MISMATCH);
			}
			try (PreparedStatement statement = prepare(connection, DELETE_SKILL))
			{
				statement.setInt(1, identity.characterObjectId());
				statement.setInt(2, oldSkill.skillId());
				statement.setInt(3, identity.classIndex());
				statement.setInt(4, oldSkill.skillLevel());
				requireOne(statement.executeUpdate(), "auto-get skill delete");
			}
		}
	}

	private PhantomBackgroundState capturedState(PhantomBackgroundState template, Canonical canonical, List<ItemRow> rows, Map<Integer, Integer> skills)
	{
		final Position position = new Position(0, canonical.position().x(), canonical.position().y(), canonical.position().z(), canonical.position().heading(), template.position().committedAnchorId());
		final Progress progress = new Progress(canonical.level(), canonical.experience(), canonical.skillPoints(), canonical.experienceBeforeDeath());
		final InventoryFacts inventory = inventoryFacts(rows, template.inventory());
		final List<AutoGetSkill> autoSkills = canonicalAutoGetSkills(template.identity(), canonical.level());
		if (!autoSkills.equals(template.autoGetSkills()))
		{
			throw new StateConflict(Status.PROGRESSION_CONFLICT);
		}
		for (AutoGetSkill skill : autoSkills)
		{
			if (!Integer.valueOf(skill.skillLevel()).equals(skills.get(skill.skillId())))
			{
				throw new StateConflict(Status.CANONICAL_MISMATCH);
			}
		}
		final State state = canonical.vitals().currentHp() == 0 ? State.DEAD : State.READY;
		return new PhantomBackgroundState(state, template.identity(), progress, canonical.vitals(), position, template.combat(), template.loadout(), inventory, autoSkills, template.clock(), template.receipt(), template.hashes());
	}

	private static int levelForExperience(long experience)
	{
		final ExperienceData data = ExperienceData.getInstance();
		int low = 1;
		int high = Byte.toUnsignedInt(data.getMaxLevel());
		while (low < high)
		{
			final int middle = (low + high + 1) >>> 1;
			if (data.getExpForLevel(middle) <= experience)
			{
				low = middle;
			}
			else
			{
				high = middle - 1;
			}
		}
		return low;
	}

	private static List<AutoGetSkill> canonicalAutoGetSkills(Identity identity, int level)
	{
		final PlayerClass playerClass = PlayerClass.getPlayerClass(identity.activeClassId());
		if ((playerClass == null) || (identity.raceOrdinal() >= Race.values().length))
		{
			throw new StateConflict(Status.PROGRESSION_CONFLICT);
		}
		final Race race = Race.values()[identity.raceOrdinal()];
		final Map<Integer, Integer> levels = new HashMap<>();
		for (SkillLearn skill : SkillTreeData.getInstance().getCompleteClassSkillTree(playerClass).values())
		{
			if (skill.isAutoGet() && (skill.getGetLevel() <= level) && (skill.getRaces().isEmpty() || skill.getRaces().contains(race)))
			{
				levels.merge(skill.getSkillId(), skill.getSkillLevel(), Math::max);
			}
		}
		return levels.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> new AutoGetSkill(entry.getKey(), entry.getValue())).toList();
	}

	private boolean durableMatches(PhantomBackgroundState state, Canonical canonical, List<ItemRow> rows, Map<Integer, Integer> skills)
	{
		if ((canonical.level() != state.progress().level()) || (canonical.experience() != state.progress().experience()) || (canonical.skillPoints() != state.progress().skillPoints()) || (canonical.experienceBeforeDeath() != state.progress().experienceBeforeDeath()) || !vitalsEqual(canonical.vitals(), state.vitals()) || (canonical.position().x() != state.position().x()) || (canonical.position().y() != state.position().y()) || (canonical.position().z() != state.position().z()) || (canonical.position().heading() != state.position().heading()) || (canonical.classId() != state.identity().activeClassId()) || (canonical.raceOrdinal() != state.identity().raceOrdinal()))
		{
			return false;
		}
		if (!inventoryFacts(rows, state.inventory()).equals(state.inventory()))
		{
			return false;
		}
		for (AutoGetSkill skill : state.autoGetSkills())
		{
			if (!Integer.valueOf(skill.skillLevel()).equals(skills.get(skill.skillId())))
			{
				return false;
			}
		}
		return true;
	}

	private static boolean runtimeProjectionMatches(PhantomBackgroundState runtime, PhantomBackgroundState durable)
	{
		return runtime.identity().equals(durable.identity()) && runtime.progress().equals(durable.progress()) && vitalsEqual(canonicalVitals(runtime.vitals()), durable.vitals()) && (runtime.position().instanceId() == durable.position().instanceId()) && (runtime.position().x() == durable.position().x()) && (runtime.position().y() == durable.position().y()) && (runtime.position().z() == durable.position().z()) && (runtime.position().heading() == durable.position().heading());
	}

	private static boolean vitalsEqual(Vitals left, Vitals right)
	{
		return close(left.currentHp(), right.currentHp()) && close(left.maximumHp(), right.maximumHp()) && close(left.currentMp(), right.currentMp()) && close(left.maximumMp(), right.maximumMp()) && close(left.currentCp(), right.currentCp()) && close(left.maximumCp(), right.maximumCp());
	}

	private static Vitals canonicalVitals(Vitals vitals)
	{
		return new Vitals(Math.min(vitals.maximumHp(), Math.round(vitals.currentHp())), vitals.maximumHp(), Math.min(vitals.maximumMp(), Math.round(vitals.currentMp())), vitals.maximumMp(), Math.min(vitals.maximumCp(), Math.round(vitals.currentCp())), vitals.maximumCp());
	}

	private static boolean close(double left, double right)
	{
		return Math.abs(left - right) <= 0.000001d;
	}

	private InventoryFacts inventoryFacts(List<ItemRow> rows, InventoryFacts projection)
	{
		long load = 0;
		int usedSlots = 0;
		for (ItemRow row : rows)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(row.itemId());
			if (template == null)
			{
				throw new StateConflict(Status.UNSUPPORTED_ITEM);
			}
			load = Math.addExact(load, Math.multiplyExact(row.count(), template.getWeight()));
			if (row.location() == ItemLocation.INVENTORY)
			{
				usedSlots++;
			}
		}
		final Set<Integer> mutableItemIds = Set.copyOf(projection.mutableItemIds());
		final Set<Integer> paperdollProofs = projection.objects().stream().filter(object -> object.location() == ItemLocation.PAPERDOLL).map(ItemObject::objectId).collect(java.util.stream.Collectors.toUnmodifiableSet());
		final List<ItemObject> tracked = itemObjects(rows).stream().filter(object -> ((object.location() == ItemLocation.INVENTORY) && mutableItemIds.contains(object.itemId())) || ((object.location() == ItemLocation.PAPERDOLL) && paperdollProofs.contains(object.objectId()))).toList();
		final String canonicalHash = PhantomBackgroundInventoryHash.compute(rows.stream().map(row -> new PhantomBackgroundInventoryHash.CanonicalItem(row.objectId(), row.itemId(), row.count(), row.location())).toList());
		return new InventoryFacts(projection.mutableItemIds(), tracked, canonicalHash, load, projection.maximumLoad(), usedSlots, projection.maximumSlots());
	}

	private List<ItemObject> itemObjects(List<ItemRow> rows)
	{
		final List<ItemObject> result = new ArrayList<>(rows.size());
		for (ItemRow row : rows)
		{
			final ItemTemplate template = ItemData.getInstance().getTemplate(row.itemId());
			if (template == null)
			{
				throw new StateConflict(Status.UNSUPPORTED_ITEM);
			}
			result.add(new ItemObject(row.objectId(), row.itemId(), row.count(), template.isStackable(), row.location()));
		}
		return List.copyOf(result);
	}

	private String expectedAfterHash(PhantomBackgroundState state)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(Integer.toString(PhantomBackgroundState.MODEL_VERSION).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Long.toString(state.identity().profileId()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Integer.toString(state.identity().characterObjectId()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Integer.toString(state.identity().classIndex()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Integer.toString(state.identity().activeClassId()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Integer.toString(state.identity().raceOrdinal()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Integer.toString(state.progress().level()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Long.toString(state.progress().experience()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Long.toString(state.progress().skillPoints()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			digest.update(Long.toString(state.progress().experienceBeforeDeath()).getBytes(StandardCharsets.US_ASCII));
			digest.update((byte) 0);
			for (double value : List.of(state.vitals().currentHp(), state.vitals().maximumHp(), state.vitals().currentMp(), state.vitals().maximumMp(), state.vitals().currentCp(), state.vitals().maximumCp()))
			{
				digest.update(Long.toString(Double.doubleToLongBits(value)).getBytes(StandardCharsets.US_ASCII));
				digest.update((byte) 0);
			}
			for (int value : List.of(state.position().instanceId(), state.position().x(), state.position().y(), state.position().z(), state.position().heading()))
			{
				digest.update(Integer.toString(value).getBytes(StandardCharsets.US_ASCII));
				digest.update((byte) 0);
			}
			digest.update(state.position().committedAnchorId().getBytes(StandardCharsets.UTF_8));
			digest.update((byte) 0);
			for (ItemObject object : state.inventory().objects())
			{
				digest.update((object.objectId() + ":" + object.itemId() + ":" + object.count() + ":" + object.location()).getBytes(StandardCharsets.US_ASCII));
				digest.update((byte) 0);
			}
			for (AutoGetSkill skill : state.autoGetSkills())
			{
				digest.update((skill.skillId() + ":" + skill.skillLevel()).getBytes(StandardCharsets.US_ASCII));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private Integer lockProfile(Connection connection, long profileId) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, LOCK_PROFILE))
		{
			statement.setLong(1, profileId);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}
				final int value = result.getInt(1);
				final Integer characterObjectId = result.wasNull() ? null : value;
				if (result.next())
				{
					throw new SQLException("Duplicate Phantom profile row.");
				}
				return characterObjectId;
			}
		}
	}

	private LockedComponent lockComponent(Connection connection, long profileId, String componentType) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, LOCK_COMPONENT))
		{
			statement.setLong(1, profileId);
			statement.setString(2, componentType);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}
				final LockedComponent component = new LockedComponent(result.getInt("component_schema_version"), result.getLong("row_version"), result.getBytes("payload"));
				if (result.next())
				{
					throw new SQLException("Duplicate Phantom component row.");
				}
				return component;
			}
		}
	}

	private PhantomBackgroundState decodeState(LockedComponent component)
	{
		if ((component.schemaVersion() != PhantomBackgroundState.SCHEMA_VERSION) && (component.schemaVersion() != 1))
		{
			throw new StateConflict(Status.STATE_CONFLICT);
		}
		try
		{
			return _stateCodec.decode(component.payload());
		}
		catch (RuntimeException failure)
		{
			throw new StateConflict(Status.STATE_CONFLICT);
		}
	}

	private void writeComponent(Connection connection, LockedComponent existing, PhantomBackgroundState state) throws SQLException
	{
		final byte[] payload = _stateCodec.encode(state);
		if (existing == null)
		{
			try (PreparedStatement statement = prepare(connection, INSERT_COMPONENT))
			{
				statement.setLong(1, state.identity().profileId());
				statement.setString(2, PhantomBackgroundState.COMPONENT_TYPE);
				statement.setInt(3, PhantomBackgroundState.SCHEMA_VERSION);
				statement.setBytes(4, payload);
				requireOne(statement.executeUpdate(), "background state insert");
			}
		}
		else
		{
			try (PreparedStatement statement = prepare(connection, UPDATE_COMPONENT))
			{
				statement.setInt(1, PhantomBackgroundState.SCHEMA_VERSION);
				statement.setBytes(2, payload);
				statement.setLong(3, state.identity().profileId());
				statement.setString(4, PhantomBackgroundState.COMPONENT_TYPE);
				statement.setLong(5, existing.rowVersion());
				requireOne(statement.executeUpdate(), "background state update");
			}
		}
	}

	private static void writeRawComponent(Connection connection, LockedComponent existing, long profileId, String componentType, int schemaVersion, byte[] payload) throws SQLException
	{
		if (existing == null)
		{
			throw new StateConflict(Status.ACQUISITION_CONFLICT);
		}
		try (PreparedStatement statement = prepare(connection, UPDATE_COMPONENT))
		{
			statement.setInt(1, schemaVersion);
			statement.setBytes(2, payload);
			statement.setLong(3, profileId);
			statement.setString(4, componentType);
			statement.setLong(5, existing.rowVersion());
			requireOne(statement.executeUpdate(), componentType + " update");
		}
	}

	private static void requireProfileLink(Integer actual, int expected)
	{
		if ((actual == null) || (actual != expected))
		{
			throw new StateConflict(Status.PROFILE_LINK_STALE);
		}
	}

	private static LockedComponent requireStateComponent(LockedComponent component)
	{
		if (component == null)
		{
			throw new StateConflict(Status.STATE_ABSENT);
		}
		return component;
	}

	private static PreparedStatement prepare(Connection connection, String sql) throws SQLException
	{
		final PreparedStatement statement = connection.prepareStatement(sql);
		statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
		return statement;
	}

	private static void updateItem(Connection connection, int characterObjectId, ItemRow row, long nextCount) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, UPDATE_ITEM))
		{
			statement.setLong(1, nextCount);
			statement.setInt(2, row.objectId());
			statement.setInt(3, characterObjectId);
			statement.setInt(4, row.itemId());
			statement.setString(5, row.location().name());
			statement.setLong(6, row.count());
			requireOne(statement.executeUpdate(), "item update");
		}
	}

	private static void deleteItem(Connection connection, int characterObjectId, ItemRow row) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, DELETE_ITEM))
		{
			statement.setInt(1, row.objectId());
			statement.setInt(2, characterObjectId);
			statement.setInt(3, row.itemId());
			statement.setString(4, row.location().name());
			statement.setLong(5, row.count());
			requireOne(statement.executeUpdate(), "item delete");
		}
	}

	private static void insertItem(Connection connection, int characterObjectId, int objectId, int itemId, long count, int mana) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, INSERT_ITEM))
		{
			statement.setInt(1, characterObjectId);
			statement.setInt(2, itemId);
			statement.setLong(3, count);
			statement.setInt(4, objectId);
			statement.setInt(5, mana);
			requireOne(statement.executeUpdate(), "item insert");
		}
	}

	private int reserveId(List<Integer> reservedIds)
	{
		final int objectId = _ids.reserve();
		if (objectId <= 0)
		{
			throw new StateConflict(Status.OBJECT_ID_EXHAUSTED);
		}
		reservedIds.add(objectId);
		return objectId;
	}

	private static void requireOne(int count, String operation) throws SQLException
	{
		if (count != 1)
		{
			throw new SQLException(operation + " did not affect exactly one row.");
		}
	}

	private static void rollback(Connection connection, Throwable failure)
	{
		try
		{
			connection.rollback();
		}
		catch (SQLException rollbackFailure)
		{
			failure.addSuppressed(rollbackFailure);
		}
	}

	private static Result failureResult(Throwable failure)
	{
		return failure instanceof StateConflict conflict ? Result.rejected(conflict._status) : Result.rejected(Status.BACKEND_FAILURE);
	}

	public enum Status
	{
		SUCCESS,
		IDEMPOTENT,
		STATE_ABSENT,
		STATE_CONFLICT,
		PROFILE_LINK_STALE,
		GOAL_STALE,
		STALE_OPERATION,
		HASH_STALE,
		CANONICAL_MISMATCH,
		ITEM_CONFLICT,
		ITEM_LIMIT,
		UNSUPPORTED_ITEM,
		UNSUPPORTED_INSTANCE,
		OBJECT_ID_EXHAUSTED,
		PROGRESSION_CONFLICT,
		ACQUISITION_CONFLICT,
		CATCHUP_CONFLICT,
		INCONSISTENT,
		BACKEND_FAILURE,
		COMMIT_OUTCOME_UNKNOWN,
		POST_COMMIT_VERIFICATION_FAILED
	}

	public enum FaultPoint
	{
		AFTER_PROFILE_LOCK,
		AFTER_GOAL_LOCK,
		AFTER_CATCHUP_LOCK,
		AFTER_ACQUISITION_LOCK,
		AFTER_BACKGROUND_LOCK,
		AFTER_CHARACTER_LOCK,
		AFTER_SKILL_LOCKS,
		AFTER_ITEM_LOCKS,
		AFTER_QUEST_LOCKS,
		AFTER_CANONICAL_WRITES,
		AFTER_BACKGROUND_STATE_WRITE,
		AFTER_CATCHUP_STATE_WRITE,
		AFTER_GOAL_STATE_WRITE,
		AFTER_ACQUISITION_STATE_WRITE,
		BEFORE_OPERATION_COMMIT,
		AFTER_OPERATION_COMMIT,
		BEFORE_VERIFY_COMMIT,
		BEFORE_CAPTURE_COMMIT,
		BEFORE_MATERIALIZED_COMMIT
	}

	@FunctionalInterface
	public interface ConnectionProvider
	{
		Connection open() throws SQLException;
	}

	public interface ObjectIdAllocator
	{
		int reserve();

		void release(int objectId);

		static ObjectIdAllocator production()
		{
			return new ObjectIdAllocator()
			{
				@Override
				public int reserve()
				{
					return IdManager.getInstance().getNextId();
				}

				@Override
				public void release(int objectId)
				{
					IdManager.getInstance().releaseId(objectId);
				}
			};
		}
	}

	@FunctionalInterface
	public interface FaultInjector
	{
		void inject(FaultPoint point);

		static FaultInjector none()
		{
			return _ ->
			{
			};
		}
	}

	public record Command(PhantomBackgroundState expectedState, PhantomGoal goal, PhantomBackgroundOperationKey operationKey, Progress progress, Vitals vitals, Position position, Clock clock, Map<Integer, Long> itemDeltas, List<AutoGetSkill> autoGetSkills, List<Integer> additionalMutableItemIds, AcquisitionMutation acquisition, CatchupMutation catchup)
	{
		public Command(PhantomBackgroundState expectedState, PhantomGoal goal, PhantomBackgroundOperationKey operationKey, Progress progress, Vitals vitals, Position position, Clock clock, Map<Integer, Long> itemDeltas, List<AutoGetSkill> autoGetSkills, List<Integer> additionalMutableItemIds, AcquisitionMutation acquisition)
		{
			this(expectedState, goal, operationKey, progress, vitals, position, clock, itemDeltas, autoGetSkills, additionalMutableItemIds, acquisition, null);
		}

		public Command(PhantomBackgroundState expectedState, PhantomGoal goal, PhantomBackgroundOperationKey operationKey, Progress progress, Vitals vitals, Position position, Clock clock, Map<Integer, Long> itemDeltas, List<AutoGetSkill> autoGetSkills)
		{
			this(expectedState, goal, operationKey, progress, vitals, position, clock, itemDeltas, autoGetSkills, List.of(), null, null);
		}

		public Command
		{
			Objects.requireNonNull(expectedState, "expectedState");
			Objects.requireNonNull(goal, "goal");
			Objects.requireNonNull(operationKey, "operationKey");
			Objects.requireNonNull(progress, "progress");
			Objects.requireNonNull(vitals, "vitals");
			Objects.requireNonNull(position, "position");
			Objects.requireNonNull(clock, "clock");
			itemDeltas = Map.copyOf(itemDeltas);
			autoGetSkills = List.copyOf(autoGetSkills);
			additionalMutableItemIds = additionalMutableItemIds.stream().distinct().sorted().toList();
			if ((operationKey.profileId() != expectedState.identity().profileId()) || (operationKey.characterObjectId() != expectedState.identity().characterObjectId()))
			{
				throw new IllegalArgumentException("Operation and background state identities differ.");
			}
			if ((additionalMutableItemIds.size() > PhantomBackgroundState.MAX_MUTABLE_ITEM_IDS) || additionalMutableItemIds.stream().anyMatch(itemId -> itemId <= 0) || ((acquisition == null) != additionalMutableItemIds.isEmpty()))
			{
				throw new IllegalArgumentException("Invalid acquisition background item allowlist.");
			}
		}
	}

	public record CatchupMutation(PhantomBackgroundCatchupState expectedState, long expectedRowVersion, PhantomBackgroundCatchupState nextState)
	{
		public CatchupMutation
		{
			Objects.requireNonNull(expectedState, "expectedState");
			Objects.requireNonNull(nextState, "nextState");
			if ((expectedRowVersion < 0) || (!nextState.owns(expectedState.requestId()) || (nextState.generation() != expectedState.generation())) || (nextState.cursorEpochMinute() != Math.addExact(expectedState.cursorEpochMinute(), 1)) || (nextState.intervalOrdinal() != Math.addExact(expectedState.intervalOrdinal(), 1)))
			{
				throw new IllegalArgumentException("Invalid historical Background catchup mutation.");
			}
		}
	}
	public record AcquisitionMutation(PhantomAcquisitionState expectedState, long expectedStateRowVersion, long expectedGoalRowVersion, ReceiptKind receiptKind, long logicalMinute, Map<Integer, Integer> eligibilitySkills, Map<String, String> expectedQuestRows)
	{
		public AcquisitionMutation(PhantomAcquisitionState expectedState, long expectedStateRowVersion, long expectedGoalRowVersion, ReceiptKind receiptKind, long logicalMinute)
		{
			this(expectedState, expectedStateRowVersion, expectedGoalRowVersion, receiptKind, logicalMinute, expectedState.selectedSource() != null && expectedState.selectedSource().method() == org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method.SPOIL_SWEEP ? Map.of(expectedState.selectedSource().spoilSkillId(), expectedState.selectedSource().spoilSkillLevel(), expectedState.selectedSource().sweepSkillId(), expectedState.selectedSource().sweepSkillLevel()) : Map.of(), Map.of());
		}

		public AcquisitionMutation(PhantomAcquisitionState expectedState, long expectedStateRowVersion, long expectedGoalRowVersion, ReceiptKind receiptKind, long logicalMinute, Map<Integer, Integer> eligibilitySkills)
		{
			this(expectedState, expectedStateRowVersion, expectedGoalRowVersion, receiptKind, logicalMinute, eligibilitySkills, Map.of());
		}

		public AcquisitionMutation
		{
			Objects.requireNonNull(expectedState, "expectedState");
			Objects.requireNonNull(receiptKind, "receiptKind");
			eligibilitySkills = Map.copyOf(eligibilitySkills);
			expectedQuestRows = Map.copyOf(expectedQuestRows);
			final var method = expectedState.selectedSource() == null ? null : expectedState.selectedSource().method();
			final boolean quest = method == org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method.QUEST_COLLECTION;
			final boolean receiptMatches = (method == org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method.DEATH_DROP) ? receiptKind == ReceiptKind.BACKGROUND_DEATH_DROP : (method == org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method.SPOIL_SWEEP) ? receiptKind == ReceiptKind.BACKGROUND_SPOIL_SWEEP : (method == org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method.MANOR_CROP) ? receiptKind == ReceiptKind.BACKGROUND_MANOR_CROP : quest && (receiptKind == ReceiptKind.BACKGROUND_QUEST_COLLECTION);
			if ((expectedStateRowVersion < 0) || (expectedGoalRowVersion < 0) || (logicalMinute < 0) || (method == null) || !receiptMatches || (eligibilitySkills.size() > 8) || eligibilitySkills.entrySet().stream().anyMatch(entry -> (entry.getKey() <= 0) || (entry.getValue() <= 0)) || (quest != !expectedQuestRows.isEmpty()) || (expectedQuestRows.size() > 6) || expectedQuestRows.entrySet().stream().anyMatch(entry -> (entry.getKey() == null) || entry.getKey().isBlank() || (entry.getValue() == null)))
			{
				throw new IllegalArgumentException("Invalid acquisition background mutation.");
			}
		}
	}

	public record AcquisitionEligibilitySnapshot(long profileId, int characterObjectId, int classIndex, int activeClassId, Map<Integer, Integer> skillLevels, String progressionHash, PhantomBackgroundState.Hashes backgroundHashes)
	{
		public AcquisitionEligibilitySnapshot
		{
			skillLevels = Map.copyOf(skillLevels);
			Objects.requireNonNull(progressionHash, "progressionHash");
			Objects.requireNonNull(backgroundHashes, "backgroundHashes");
		}
	}

	public record AcquisitionInventorySnapshot(long profileId, int characterObjectId, int classIndex, int activeClassId, Map<Integer, Long> counts, PhantomBackgroundState.Hashes backgroundHashes)
	{
		public AcquisitionInventorySnapshot
		{
			counts = Map.copyOf(counts);
			Objects.requireNonNull(backgroundHashes, "backgroundHashes");
		}
	}

	public record AcquisitionInventoryResult(Status status, AcquisitionInventorySnapshot snapshot)
	{
		public static AcquisitionInventoryResult rejected(Status status)
		{
			return new AcquisitionInventoryResult(status, null);
		}

		public boolean successful()
		{
			return (status == Status.SUCCESS) && (snapshot != null);
		}
	}

	public record AcquisitionQuestRowsSnapshot(long profileId, int characterObjectId, int classIndex, int activeClassId, Map<String, Map<String, String>> rows, PhantomBackgroundState.Hashes backgroundHashes)
	{
		public AcquisitionQuestRowsSnapshot
		{
			rows = rows.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
			Objects.requireNonNull(backgroundHashes, "backgroundHashes");
		}
	}

	public record AcquisitionQuestRowsResult(Status status, AcquisitionQuestRowsSnapshot snapshot)
	{
		public static AcquisitionQuestRowsResult rejected(Status status)
		{
			return new AcquisitionQuestRowsResult(status, null);
		}

		public boolean successful()
		{
			return status == Status.SUCCESS;
		}
	}

	public record EligibilityResult(Status status, AcquisitionEligibilitySnapshot snapshot)
	{
		public static EligibilityResult rejected(Status status)
		{
			return new EligibilityResult(status, null);
		}

		public boolean successful()
		{
			return (status == Status.SUCCESS) && (snapshot != null);
		}
	}

	public record Result(Status status, PhantomBackgroundState state, PhantomAcquisitionState acquisitionState, PhantomGoal goal)
	{
		public Result(Status status, PhantomBackgroundState state)
		{
			this(status, state, null, null);
		}

		public static Result rejected(Status status)
		{
			return new Result(status, null);
		}

		public boolean successful()
		{
			return (status == Status.SUCCESS) || (status == Status.IDEMPOTENT);
		}
	}

	private record LockedComponent(int schemaVersion, long rowVersion, byte[] payload)
	{
		private LockedComponent
		{
			payload = payload.clone();
		}
	}

	private record LockedGoal(LockedComponent component, PhantomGoal goal)
	{
	}

	private record CharacterRow(int level, long experience, long skillPoints, long experienceBeforeDeath, Vitals vitals, Position position, int classId, int raceOrdinal)
	{
	}

	private record SubclassRow(int level, long experience, long skillPoints, int classId)
	{
	}

	private record Canonical(int level, long experience, long skillPoints, long experienceBeforeDeath, Vitals vitals, Position position, int classId, int raceOrdinal)
	{
	}

	private record ItemRow(int objectId, int itemId, long count, ItemLocation location)
	{
		private ItemRow withCount(long nextCount)
		{
			return new ItemRow(objectId, itemId, nextCount, location);
		}
	}

	private record ItemMutationResult(List<ItemRow> rows)
	{
	}

	private static final class StateConflict extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
		private final Status _status;

		private StateConflict(Status status)
		{
			_status = status;
		}
	}
}

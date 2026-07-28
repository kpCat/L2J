/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.progression;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.actor.Npc;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemLocation;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationResult;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.OperationStatus;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionModel.RequiredItem;

/**
 * Owns the one durable mutation boundary for exact CLASS skill learning.
 */
public final class PhantomClassSkillLearningTransaction
{
	private static final int QUERY_TIMEOUT_SECONDS = 10;
	private static final String SELECT_MAIN_SP = "SELECT sp, classid FROM characters WHERE charId = ? FOR UPDATE";
	private static final String UPDATE_MAIN_SP = "UPDATE characters SET sp = ? WHERE charId = ? AND sp = ?";
	private static final String SELECT_SUBCLASS_SP = "SELECT sp, class_id FROM character_subclasses WHERE charId = ? AND class_index = ? FOR UPDATE";
	private static final String UPDATE_SUBCLASS_SP = "UPDATE character_subclasses SET sp = ? WHERE charId = ? AND class_index = ? AND class_id = ? AND sp = ?";
	private static final String SELECT_SKILL = "SELECT skill_level FROM character_skills WHERE charId = ? AND skill_id = ? AND class_index = ? FOR UPDATE";
	private static final String INSERT_SKILL = "INSERT INTO character_skills (charId, skill_id, skill_level, class_index) VALUES (?, ?, ?, ?)";
	private static final String UPDATE_SKILL = "UPDATE character_skills SET skill_level = ? WHERE charId = ? AND skill_id = ? AND class_index = ? AND skill_level = ?";
	private static final String SELECT_ITEM = "SELECT owner_id, item_id, count, loc FROM items WHERE object_id = ? FOR UPDATE";
	private static final String UPDATE_ITEM = "UPDATE items SET count = ? WHERE object_id = ? AND owner_id = ? AND item_id = ? AND loc = ? AND count = ?";
	private static final String DELETE_ITEM = "DELETE FROM items WHERE object_id = ? AND owner_id = ? AND item_id = ? AND loc = ? AND count = ?";

	private final FaultInjector _faultInjector;

	public PhantomClassSkillLearningTransaction()
	{
		this(FaultInjector.none());
	}

	public PhantomClassSkillLearningTransaction(FaultInjector faultInjector)
	{
		_faultInjector = Objects.requireNonNull(faultInjector, "faultInjector");
	}

	public OperationResult execute(Player player, Skill skill, Npc trainer, int previousSkillLevel, int spCost, Item exactItem, RequiredItem requiredItem)
	{
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(skill, "skill");
		if ((exactItem == null) != (requiredItem == null))
		{
			return OperationResult.rejected(OperationStatus.DURABLE_ITEM_STATE_CONFLICT);
		}

		try
		{
			synchronized (player)
			{
				if (exactItem == null)
				{
					return executeLocked(player, skill, trainer, previousSkillLevel, spCost, null, null);
				}
				synchronized (exactItem)
				{
					return executeLocked(player, skill, trainer, previousSkillLevel, spCost, exactItem, requiredItem);
				}
			}
		}
		catch (StateConflict conflict)
		{
			return OperationResult.rejected(conflict.status());
		}
	}

	private OperationResult executeLocked(Player player, Skill skill, Npc trainer, int previousSkillLevel, int spCost, Item exactItem, RequiredItem requiredItem)
	{
		final Identity identity = identity(player, skill, previousSkillLevel, spCost, exactItem, requiredItem);
		final Map<Integer, Long> itemCountsBefore = itemCounts(player, requiredItem);
		DurableState baseline = null;
		boolean committed = false;
		try (Connection connection = DatabaseFactory.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				baseline = lockAndValidate(connection, identity);
				if (identity.previousSkillLevel() >= identity.targetSkillLevel())
				{
					connection.rollback();
					return new OperationResult(OperationStatus.IDEMPOTENT, identity.spBefore(), identity.spBefore(), itemCountsBefore, itemCountsBefore, baseline.skillLevel(), false);
				}

				mutateItem(connection, identity);
				mutateSp(connection, identity);
				mutateSkill(connection, identity);
				_faultInjector.inject(FaultPoint.BEFORE_COMMIT);
				connection.commit();
				committed = true;
			}
			catch (Throwable failure)
			{
				if (!committed)
				{
					rollback(connection, failure);
					if ((baseline != null) && !freshStateMatches(identity, baseline))
					{
						return OperationResult.rejected(OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED);
					}
					return failureResult(failure);
				}
				return OperationResult.rejected(OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED);
			}
		}
		catch (SQLException | RuntimeException failure)
		{
			if (!committed)
			{
				return failureResult(failure);
			}
			return OperationResult.rejected(OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED);
		}

		final long expectedSp = identity.spBefore() - identity.spCost();
		try
		{
			if ((identity.itemObjectId() != null) && !player.destroyItem(ItemProcessType.FEE, exactItem, identity.requiredItemCount(), trainer, false))
			{
				return OperationResult.rejected(OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED);
			}
			player.setSp(expectedSp);
			player.addSkill(skill, false);
			player.updateShortcuts(skill.getId(), skill.getLevel());
			if (!runtimeStateMatches(player, identity))
			{
				return OperationResult.rejected(OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED);
			}
			_faultInjector.inject(FaultPoint.BEFORE_POSTCONDITION_READ);
			if (!freshStateMatches(identity, DurableState.expected(identity)))
			{
				return OperationResult.rejected(OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED);
			}
		}
		catch (Throwable failure)
		{
			return OperationResult.rejected(OperationStatus.DURABLE_COMMIT_RUNTIME_RECONCILIATION_FAILED);
		}

		return new OperationResult(OperationStatus.SUCCESS, identity.spBefore(), expectedSp, itemCountsBefore, itemCounts(player, requiredItem), skill.getLevel(), false);
	}

	private DurableState lockAndValidate(Connection connection, Identity identity) throws SQLException
	{
		final SpRow spRow = lockSp(connection, identity);
		if (spRow.sp() != identity.spBefore())
		{
			throw new StateConflict(OperationStatus.DURABLE_SP_STATE_CONFLICT);
		}
		final Integer durableSkillLevel = lockSkill(connection, identity);
		if (identity.previousSkillLevel() >= identity.targetSkillLevel())
		{
			if (!Integer.valueOf(identity.previousSkillLevel()).equals(durableSkillLevel))
			{
				throw new StateConflict(OperationStatus.DURABLE_SKILL_STATE_CONFLICT);
			}
			return new DurableState(spRow.sp(), durableSkillLevel, lockItem(connection, identity));
		}
		if (identity.previousSkillLevel() <= 0)
		{
			if (durableSkillLevel != null)
			{
				throw new StateConflict(OperationStatus.DURABLE_SKILL_STATE_CONFLICT);
			}
		}
		else if (!Integer.valueOf(identity.previousSkillLevel()).equals(durableSkillLevel))
		{
			throw new StateConflict(OperationStatus.DURABLE_SKILL_STATE_CONFLICT);
		}
		return new DurableState(spRow.sp(), durableSkillLevel, lockItem(connection, identity));
	}

	private SpRow lockSp(Connection connection, Identity identity) throws SQLException
	{
		if (identity.classIndex() == 0)
		{
			try (PreparedStatement statement = prepare(connection, SELECT_MAIN_SP))
			{
				statement.setInt(1, identity.characterObjectId());
				try (ResultSet result = statement.executeQuery())
				{
					if (!result.next())
					{
						throw new StateConflict(OperationStatus.DURABLE_SCHEMA_OR_ROW_MISSING);
					}
					final SpRow row = new SpRow(result.getLong("sp"), result.getInt("classid"));
					if (result.next())
					{
						throw new StateConflict(OperationStatus.DURABLE_SCHEMA_OR_ROW_MISSING);
					}
					if (row.classId() != identity.activeClassId())
					{
						throw new StateConflict(OperationStatus.DURABLE_SP_STATE_CONFLICT);
					}
					return row;
				}
			}
		}
		try (PreparedStatement statement = prepare(connection, SELECT_SUBCLASS_SP))
		{
			statement.setInt(1, identity.characterObjectId());
			statement.setInt(2, identity.classIndex());
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw new StateConflict(OperationStatus.DURABLE_SCHEMA_OR_ROW_MISSING);
				}
				final SpRow row = new SpRow(result.getLong("sp"), result.getInt("class_id"));
				if (result.next())
				{
					throw new StateConflict(OperationStatus.DURABLE_SCHEMA_OR_ROW_MISSING);
				}
				if (row.classId() != identity.activeClassId())
				{
					throw new StateConflict(OperationStatus.DURABLE_SP_STATE_CONFLICT);
				}
				return row;
			}
		}
	}

	private Integer lockSkill(Connection connection, Identity identity) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, SELECT_SKILL))
		{
			statement.setInt(1, identity.characterObjectId());
			statement.setInt(2, identity.skillId());
			statement.setInt(3, identity.classIndex());
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}
				final int level = result.getInt("skill_level");
				if (result.next())
				{
					throw new StateConflict(OperationStatus.DURABLE_SKILL_STATE_CONFLICT);
				}
				return level;
			}
		}
	}

	private Long lockItem(Connection connection, Identity identity) throws SQLException
	{
		if (identity.itemObjectId() == null)
		{
			return null;
		}
		try (PreparedStatement statement = prepare(connection, SELECT_ITEM))
		{
			statement.setInt(1, identity.itemObjectId());
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					throw new StateConflict(OperationStatus.DURABLE_SCHEMA_OR_ROW_MISSING);
				}
				final int ownerId = result.getInt("owner_id");
				final int itemId = result.getInt("item_id");
				final long count = result.getLong("count");
				final String location = result.getString("loc");
				if (result.next())
				{
					throw new StateConflict(OperationStatus.DURABLE_ITEM_STATE_CONFLICT);
				}
				if ((ownerId != identity.characterObjectId()) || (itemId != identity.requiredItemId()) || !ItemLocation.INVENTORY.name().equals(location) || (count != identity.itemCountBefore()) || (count < identity.requiredItemCount()))
				{
					throw new StateConflict(OperationStatus.DURABLE_ITEM_STATE_CONFLICT);
				}
				return count;
			}
		}
	}

	private void mutateItem(Connection connection, Identity identity) throws SQLException
	{
		if (identity.itemObjectId() == null)
		{
			return;
		}
		_faultInjector.inject(FaultPoint.BEFORE_ITEM_SQL);
		final long remaining = identity.itemCountBefore() - identity.requiredItemCount();
		if (remaining > 0)
		{
			try (PreparedStatement statement = prepare(connection, UPDATE_ITEM))
			{
				statement.setLong(1, remaining);
				bindItemIdentity(statement, 2, identity);
				requireOne(statement.executeUpdate(), OperationStatus.DURABLE_ITEM_STATE_CONFLICT);
			}
		}
		else
		{
			try (PreparedStatement statement = prepare(connection, DELETE_ITEM))
			{
				bindItemIdentity(statement, 1, identity);
				requireOne(statement.executeUpdate(), OperationStatus.DURABLE_ITEM_STATE_CONFLICT);
			}
		}
		_faultInjector.inject(FaultPoint.AFTER_ITEM_SQL);
	}

	private void mutateSp(Connection connection, Identity identity) throws SQLException
	{
		_faultInjector.inject(FaultPoint.BEFORE_SP_SQL);
		final long expectedSp = identity.spBefore() - identity.spCost();
		if (identity.spCost() > 0)
		{
			if (identity.classIndex() == 0)
			{
				try (PreparedStatement statement = prepare(connection, UPDATE_MAIN_SP))
				{
					statement.setLong(1, expectedSp);
					statement.setInt(2, identity.characterObjectId());
					statement.setLong(3, identity.spBefore());
					requireOne(statement.executeUpdate(), OperationStatus.DURABLE_SP_STATE_CONFLICT);
				}
			}
			else
			{
				try (PreparedStatement statement = prepare(connection, UPDATE_SUBCLASS_SP))
				{
					statement.setLong(1, expectedSp);
					statement.setInt(2, identity.characterObjectId());
					statement.setInt(3, identity.classIndex());
					statement.setInt(4, identity.activeClassId());
					statement.setLong(5, identity.spBefore());
					requireOne(statement.executeUpdate(), OperationStatus.DURABLE_SP_STATE_CONFLICT);
				}
			}
		}
		_faultInjector.inject(FaultPoint.AFTER_SP_SQL);
	}

	private void mutateSkill(Connection connection, Identity identity) throws SQLException
	{
		_faultInjector.inject(FaultPoint.BEFORE_SKILL_SQL);
		if (identity.previousSkillLevel() <= 0)
		{
			try (PreparedStatement statement = prepare(connection, INSERT_SKILL))
			{
				statement.setInt(1, identity.characterObjectId());
				statement.setInt(2, identity.skillId());
				statement.setInt(3, identity.targetSkillLevel());
				statement.setInt(4, identity.classIndex());
				requireOne(statement.executeUpdate(), OperationStatus.DURABLE_SKILL_STATE_CONFLICT);
			}
		}
		else
		{
			try (PreparedStatement statement = prepare(connection, UPDATE_SKILL))
			{
				statement.setInt(1, identity.targetSkillLevel());
				statement.setInt(2, identity.characterObjectId());
				statement.setInt(3, identity.skillId());
				statement.setInt(4, identity.classIndex());
				statement.setInt(5, identity.previousSkillLevel());
				requireOne(statement.executeUpdate(), OperationStatus.DURABLE_SKILL_STATE_CONFLICT);
			}
		}
		_faultInjector.inject(FaultPoint.AFTER_SKILL_SQL);
	}

	private static Identity identity(Player player, Skill skill, int previousSkillLevel, int spCost, Item exactItem, RequiredItem requiredItem)
	{
		if ((spCost < 0) || (player.getSp() < spCost))
		{
			throw new IllegalArgumentException("Invalid exact SP cost.");
		}
		if ((previousSkillLevel > 0) && (previousSkillLevel != (skill.getLevel() - 1)) && (previousSkillLevel < skill.getLevel()))
		{
			throw new IllegalArgumentException("Invalid previous skill level.");
		}
		if (exactItem == null)
		{
			return new Identity(player.getObjectId(), player.getClassIndex(), player.getActiveClass(), skill.getId(), previousSkillLevel, skill.getLevel(), player.getSp(), spCost, null, null, null, null);
		}
		if ((requiredItem.count() <= 0) || (exactItem.getOwnerId() != player.getObjectId()) || (exactItem.getId() != requiredItem.itemId()) || (exactItem.getItemLocation() != ItemLocation.INVENTORY) || (exactItem.getCount() < requiredItem.count()))
		{
			throw new StateConflict(OperationStatus.DURABLE_ITEM_STATE_CONFLICT);
		}
		return new Identity(player.getObjectId(), player.getClassIndex(), player.getActiveClass(), skill.getId(), previousSkillLevel, skill.getLevel(), player.getSp(), spCost, exactItem.getObjectId(), requiredItem.itemId(), requiredItem.count(), exactItem.getCount());
	}

	private static void bindItemIdentity(PreparedStatement statement, int offset, Identity identity) throws SQLException
	{
		statement.setInt(offset, identity.itemObjectId());
		statement.setInt(offset + 1, identity.characterObjectId());
		statement.setInt(offset + 2, identity.requiredItemId());
		statement.setString(offset + 3, ItemLocation.INVENTORY.name());
		statement.setLong(offset + 4, identity.itemCountBefore());
	}

	private static PreparedStatement prepare(Connection connection, String sql) throws SQLException
	{
		final PreparedStatement statement = connection.prepareStatement(sql);
		statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
		return statement;
	}

	private static void requireOne(int affectedRows, OperationStatus status)
	{
		if (affectedRows != 1)
		{
			throw new StateConflict(status);
		}
	}

	private static OperationResult failureResult(Throwable failure)
	{
		if (failure instanceof StateConflict conflict)
		{
			return OperationResult.rejected(conflict.status());
		}
		return OperationResult.rejected(OperationStatus.BACKEND_FAILURE);
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

	private static boolean runtimeStateMatches(Player player, Identity identity)
	{
		if ((player.getClassIndex() != identity.classIndex()) || (player.getActiveClass() != identity.activeClassId()) || (player.getSp() != (identity.spBefore() - identity.spCost())) || (player.getSkillLevel(identity.skillId()) != identity.targetSkillLevel()))
		{
			return false;
		}
		if (identity.itemObjectId() == null)
		{
			return true;
		}
		final long expected = identity.itemCountBefore() - identity.requiredItemCount();
		final Item item = player.getInventory().getItemByObjectId(identity.itemObjectId());
		return expected == 0 ? item == null : (item != null) && (item.getOwnerId() == identity.characterObjectId()) && (item.getId() == identity.requiredItemId()) && (item.getItemLocation() == ItemLocation.INVENTORY) && (item.getCount() == expected);
	}

	private static boolean freshStateMatches(Identity identity, DurableState expected)
	{
		try (Connection connection = DatabaseFactory.getConnection())
		{
			final SpRow sp = readSp(connection, identity);
			final Integer skillLevel = readSkill(connection, identity);
			final Long itemCount = readItemCount(connection, identity);
			return (sp != null) && (sp.classId() == identity.activeClassId()) && (sp.sp() == expected.sp()) && Objects.equals(skillLevel, expected.skillLevel()) && Objects.equals(itemCount, expected.itemCount());
		}
		catch (SQLException | RuntimeException failure)
		{
			return false;
		}
	}

	private static SpRow readSp(Connection connection, Identity identity) throws SQLException
	{
		final String sql = identity.classIndex() == 0 ? "SELECT sp, classid FROM characters WHERE charId = ?" : "SELECT sp, class_id FROM character_subclasses WHERE charId = ? AND class_index = ?";
		try (PreparedStatement statement = prepare(connection, sql))
		{
			statement.setInt(1, identity.characterObjectId());
			if (identity.classIndex() > 0)
			{
				statement.setInt(2, identity.classIndex());
			}
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}
				final SpRow row = new SpRow(result.getLong("sp"), result.getInt(identity.classIndex() == 0 ? "classid" : "class_id"));
				return result.next() ? null : row;
			}
		}
	}

	private static Integer readSkill(Connection connection, Identity identity) throws SQLException
	{
		try (PreparedStatement statement = prepare(connection, "SELECT skill_level FROM character_skills WHERE charId = ? AND skill_id = ? AND class_index = ?"))
		{
			statement.setInt(1, identity.characterObjectId());
			statement.setInt(2, identity.skillId());
			statement.setInt(3, identity.classIndex());
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}
				final int level = result.getInt("skill_level");
				return result.next() ? null : level;
			}
		}
	}

	private static Long readItemCount(Connection connection, Identity identity) throws SQLException
	{
		if (identity.itemObjectId() == null)
		{
			return null;
		}
		try (PreparedStatement statement = prepare(connection, "SELECT owner_id, item_id, count, loc FROM items WHERE object_id = ?"))
		{
			statement.setInt(1, identity.itemObjectId());
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return null;
				}
				final boolean identityMatches = (result.getInt("owner_id") == identity.characterObjectId()) && (result.getInt("item_id") == identity.requiredItemId()) && ItemLocation.INVENTORY.name().equals(result.getString("loc"));
				final long count = result.getLong("count");
				return identityMatches && !result.next() ? count : null;
			}
		}
	}

	private static Map<Integer, Long> itemCounts(Player player, RequiredItem requiredItem)
	{
		return requiredItem == null ? Map.of() : Map.of(requiredItem.itemId(), player.getInventory().getInventoryItemCount(requiredItem.itemId(), -1));
	}

	public enum FaultPoint
	{
		BEFORE_ITEM_SQL,
		AFTER_ITEM_SQL,
		BEFORE_SP_SQL,
		AFTER_SP_SQL,
		BEFORE_SKILL_SQL,
		AFTER_SKILL_SQL,
		BEFORE_COMMIT,
		BEFORE_POSTCONDITION_READ
	}

	@FunctionalInterface
	public interface FaultInjector
	{
		void inject(FaultPoint point);

		static FaultInjector none()
		{
			return _ -> { };
		}
	}

	private record Identity(int characterObjectId, int classIndex, int activeClassId, int skillId, int previousSkillLevel, int targetSkillLevel, long spBefore, int spCost, Integer itemObjectId, Integer requiredItemId, Long requiredItemCount, Long itemCountBefore)
	{
	}

	private record DurableState(long sp, Integer skillLevel, Long itemCount)
	{
		static DurableState expected(Identity identity)
		{
			final long remaining = identity.itemObjectId() == null ? 0 : identity.itemCountBefore() - identity.requiredItemCount();
			final Long itemCount = (identity.itemObjectId() == null) || (remaining == 0) ? null : Long.valueOf(remaining);
			return new DurableState(identity.spBefore() - identity.spCost(), identity.targetSkillLevel(), itemCount);
		}
	}

	private record SpRow(long sp, int classId)
	{
	}

	private static final class StateConflict extends RuntimeException
	{
		private static final long serialVersionUID = 1L;
		private final OperationStatus _status;

		StateConflict(OperationStatus status)
		{
			_status = status;
		}

		OperationStatus status()
		{
			return _status;
		}
	}
}

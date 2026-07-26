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
package org.l2jmobius.gameserver.phantoms.player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerState;
import org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager;

/**
 * Explicit fail-closed recovery for a retained real-login identity lease.
 */
public final class PhantomRetainedIdentityRecovery
{
	private static final String SELECT_CHARACTER_ONLINE = "SELECT online FROM characters WHERE charId = ?";

	public enum Status
	{
		SUCCESS,
		NOT_OWNED,
		WRONG_OWNER,
		RESERVED_OWNER,
		WORLD_PLAYER_PRESENT,
		WORLD_OBJECT_PRESENT,
		AUTOSAVE_PRESENT,
		CHARACTER_NOT_FOUND,
		MULTIPLE_CHARACTER_ROWS,
		CHARACTER_ONLINE,
		DATABASE_ERROR,
		OWNER_CHANGED
	}

	private final PhantomIdentityLeaseRegistry _identityRegistry;

	public PhantomRetainedIdentityRecovery(PhantomIdentityLeaseRegistry identityRegistry)
	{
		_identityRegistry = Objects.requireNonNull(identityRegistry, "identityRegistry");
	}

	public Result recover(int objectId)
	{
		if (objectId <= 0)
		{
			throw new IllegalArgumentException("objectId must be positive");
		}

		final OwnerSnapshot owner = _identityRegistry.getOwnerSnapshot(objectId);
		if (owner == null)
		{
			return new Result(Status.NOT_OWNED, objectId);
		}
		if (owner.ownerKind() != OwnerKind.REAL_LOGIN)
		{
			return new Result(Status.WRONG_OWNER, objectId);
		}
		if (owner.state() != OwnerState.RETAINED)
		{
			return new Result(Status.RESERVED_OWNER, objectId);
		}

		final World world = World.getInstance();
		if (world.getPlayer(objectId) != null)
		{
			return new Result(Status.WORLD_PLAYER_PRESENT, objectId);
		}
		if (world.findObject(objectId) != null)
		{
			return new Result(Status.WORLD_OBJECT_PRESENT, objectId);
		}
		if (PlayerAutoSaveTaskManager.getInstance().containsObjectId(objectId))
		{
			return new Result(Status.AUTOSAVE_PRESENT, objectId);
		}

		final Status databaseEvidence = readDatabaseEvidence(objectId);
		if (databaseEvidence != Status.SUCCESS)
		{
			return new Result(databaseEvidence, objectId);
		}
		return new Result(_identityRegistry.releaseRetained(owner) ? Status.SUCCESS : Status.OWNER_CHANGED, objectId);
	}

	private static Status readDatabaseEvidence(int objectId)
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(SELECT_CHARACTER_ONLINE))
		{
			statement.setInt(1, objectId);
			try (ResultSet result = statement.executeQuery())
			{
				if (!result.next())
				{
					return Status.CHARACTER_NOT_FOUND;
				}
				final int online = result.getInt(1);
				if (result.next())
				{
					return Status.MULTIPLE_CHARACTER_ROWS;
				}
				return online == 0 ? Status.SUCCESS : Status.CHARACTER_ONLINE;
			}
		}
		catch (SQLException | RuntimeException e)
		{
			return Status.DATABASE_ERROR;
		}
	}

	public record Result(Status status, int objectId)
	{
		public boolean recovered()
		{
			return status == Status.SUCCESS;
		}
	}
}

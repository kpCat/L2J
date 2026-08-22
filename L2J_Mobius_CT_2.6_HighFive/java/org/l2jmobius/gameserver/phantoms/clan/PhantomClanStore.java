/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionState;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.DiplomacyAction;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.DiplomacyPhase;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.DiplomacyState;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.OrganizationMetadata;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleKey;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.StoredMetadata;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Profile-component persistence for planning metadata only. */
public final class PhantomClanStore implements PersistencePort
{
	public static final String COMPONENT_TYPE = "clan.organization";
	public static final int LEGACY_SCHEMA_VERSION = 1;
	public static final int SCHEMA_VERSION = 2;
	public static final int MAX_PAYLOAD_BYTES = 8192;
	private final PhantomProfileRepository _profiles;

	public PhantomClanStore(PhantomProfileRepository profiles)
	{
		_profiles = Objects.requireNonNull(profiles);
	}

	@Override
	public Optional<StoredMetadata> load(long profileId)
	{
		return _profiles.findComponent(profileId, COMPONENT_TYPE).map(PhantomClanStore::decode);
	}

	@Override
	public StoredMetadata save(long profileId, long expectedRowVersion, OrganizationMetadata metadata)
	{
		final byte[] payload = encode(metadata);
		final PhantomProfileComponent component = expectedRowVersion < 0
			? _profiles.insertComponent(profileId, COMPONENT_TYPE, SCHEMA_VERSION, payload)
			: _profiles.updateComponent(profileId, COMPONENT_TYPE, expectedRowVersion, SCHEMA_VERSION, payload);
		return decode(component);
	}

	static byte[] encode(OrganizationMetadata metadata)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(SCHEMA_VERSION);
				output.writeInt(metadata.canonicalClanId());
				string(output, metadata.clanName(), 64);
				output.writeInt(metadata.canonicalLeaderObjectId());
				output.writeByte(metadata.roleIntent().ordinal());
				output.writeLong(metadata.organizationGoalId());
				output.writeLong(metadata.goalRevision());
				output.writeLong(metadata.contributionBudget());
				output.writeInt(metadata.contributionItemObjectId());
				output.writeLong(metadata.contributionAmount());
				output.writeLong(metadata.contributionInventoryBefore());
				output.writeLong(metadata.contributionWarehouseBefore());
				output.writeByte(metadata.contributionState().ordinal());
				output.writeByte(metadata.relationReferences().size());
				for (String reference : metadata.relationReferences())
				{
					string(output, reference, 160);
				}
				string(output, metadata.canonicalEvidenceHash(), 64);
				string(output, metadata.intentEvidenceHash(), 64);
				output.writeLong(metadata.updatedEpochMillis());
				output.writeByte(metadata.diplomacy().action().ordinal());
				output.writeByte(metadata.diplomacy().phase().ordinal());
				output.writeLong(metadata.diplomacy().goalId());
				output.writeLong(metadata.diplomacy().goalRevision());
				output.writeInt(metadata.diplomacy().counterpartClanId());
				output.writeInt(metadata.diplomacy().allianceLeaderClanId());
				output.writeLong(metadata.diplomacy().allianceGeneration());
				output.writeLong(metadata.diplomacy().membershipCounter());
				output.writeLong(metadata.diplomacy().warId());
				output.writeLong(metadata.diplomacy().decisionEpoch());
				output.writeLong(metadata.diplomacy().cooldownUntilEpochMillis());
				output.writeLong(metadata.diplomacy().happenedEpochMinute());
				string(output, metadata.diplomacy().evidenceHash(), 64);
			}
			final byte[] payload = bytes.toByteArray();
			if (payload.length > MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Clan organization payload exceeds its bound.");
			}
			return payload;
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Cannot encode clan organization metadata.", exception);
		}
	}

	static StoredMetadata decode(PhantomProfileComponent component)
	{
		final int schemaVersion = component.componentSchemaVersion();
		if ((schemaVersion != LEGACY_SCHEMA_VERSION) && (schemaVersion != SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown clan organization schema version.");
		}
		final byte[] payload = component.payload();
		if ((payload.length == 0) || (payload.length > MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Invalid clan organization payload size.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (input.readInt() != schemaVersion)
			{
				throw new IllegalArgumentException("Unknown clan organization payload version.");
			}
			final int clanId = input.readInt();
			final String clanName = string(input, 64);
			final int leaderObjectId = input.readInt();
			final RoleKey role = enumValue(input, RoleKey.class);
			final long goalId = input.readLong();
			final long goalRevision = input.readLong();
			final long contributionBudget = input.readLong();
			final int contributionItemObjectId = input.readInt();
			final long contributionAmount = input.readLong();
			final long contributionInventoryBefore = input.readLong();
			final long contributionWarehouseBefore = input.readLong();
			final ContributionState contributionState = enumValue(input, ContributionState.class);
			final int relationCount = input.readUnsignedByte();
			if (relationCount > 16)
			{
				throw new IllegalArgumentException("Clan relation references exceed their bound.");
			}
			final List<String> relations = new ArrayList<>(relationCount);
			for (int index = 0; index < relationCount; index++)
			{
				relations.add(string(input, 160));
			}
			final String canonicalHash = string(input, 64);
			final String intentHash = string(input, 64);
			final long updated = input.readLong();
			final DiplomacyState diplomacy;
			if (schemaVersion == LEGACY_SCHEMA_VERSION)
			{
				diplomacy = DiplomacyState.empty();
			}
			else
			{
				diplomacy = new DiplomacyState(enumValue(input, DiplomacyAction.class), enumValue(input, DiplomacyPhase.class), input.readLong(), input.readLong(), input.readInt(), input.readInt(), input.readLong(), input.readLong(), input.readLong(), input.readLong(), input.readLong(), input.readLong(), string(input, 64));
			}
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("Trailing clan organization payload data.");
			}
			return new StoredMetadata(component.rowVersion(), new OrganizationMetadata(clanId, clanName, leaderObjectId, role, goalId, goalRevision, contributionBudget, contributionItemObjectId, contributionAmount, contributionInventoryBefore, contributionWarehouseBefore, contributionState, relations, canonicalHash, intentHash, updated, diplomacy));
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Cannot decode clan organization metadata.", exception);
		}
	}

	private static void string(DataOutputStream output, String value, int maximum) throws Exception
	{
		final byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		if (bytes.length > maximum)
		{
			throw new IllegalArgumentException("Clan organization string exceeds its bound.");
		}
		output.writeShort(bytes.length);
		output.write(bytes);
	}

	private static String string(DataInputStream input, int maximum) throws Exception
	{
		final int length = input.readUnsignedShort();
		if (length > maximum)
		{
			throw new IllegalArgumentException("Clan organization string exceeds its bound.");
		}
		final byte[] bytes = input.readNBytes(length);
		if (bytes.length != length)
		{
			throw new IllegalArgumentException("Truncated clan organization string.");
		}
		return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
	}

	private static <E extends Enum<E>> E enumValue(DataInputStream input, Class<E> type) throws Exception
	{
		final int ordinal = input.readUnsignedByte();
		final E[] values = type.getEnumConstants();
		if (ordinal >= values.length)
		{
			throw new IllegalArgumentException("Unknown clan organization enum value.");
		}
		return values[ordinal];
	}
}

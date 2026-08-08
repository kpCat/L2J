/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.BindingStability;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.CandidateReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.InvitationStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PartyBindingReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PendingInvitationReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Refusal;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Stage;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Status;

public final class PhantomRiftStateCodec
{
	private static final int LEGACY_SCHEMA_VERSION = 1;
	private static final Stage[] LEGACY_STAGES =
	{
		Stage.DISCOVER_CONTENT,
		Stage.SNAPSHOT_ROSTER,
		Stage.EVALUATE_READINESS,
		Stage.SELECT_CANDIDATE,
		Stage.REQUEST_INVITE,
		Stage.OBSERVE_INVITE,
		Stage.REQUEST_PARTY_ROUTE,
		Stage.OBSERVE_ROUTE,
		Stage.DECLARE_READY
	};

	public byte[] encode(Preparation value)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(PhantomRiftModel.SCHEMA_VERSION);
				base(output, value);
				binding(output, value.partyBinding());
				candidate(output, value.candidateReceipt());
				invitation(output, value.invitationReceipt());
				output.writeBoolean(value.legacyUntrusted());
			}
			final byte[] payload = bytes.toByteArray();
			if (payload.length > PhantomRiftModel.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Rift preparation payload exceeds its bound.");
			}
			return payload;
		}
		catch (IllegalArgumentException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Cannot encode Rift preparation.", e);
		}
	}

	public Preparation decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > PhantomRiftModel.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Invalid Rift preparation payload size.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			final int schema = input.readInt();
			if ((schema != LEGACY_SCHEMA_VERSION) && (schema != PhantomRiftModel.SCHEMA_VERSION))
			{
				throw new IllegalArgumentException("Unknown Rift preparation schema version.");
			}
			final long leaderProfileId = input.readLong();
			final long goalId = input.readLong();
			final long goalRevision = input.readLong();
			final int tierType = input.readUnsignedByte();
			final Stage stage = schema == LEGACY_SCHEMA_VERSION ? legacyStage(input.readUnsignedByte()) : value(input, Stage.class);
			final Status status = value(input, Status.class);
			final String rosterHash = string(input, 64);
			final String catalogHash = string(input, 64);
			final String policyHash = string(input, 64);
			final String configHash = string(input, 64);
			final String roleHash = string(input, 64);
			final String vacancyKey = string(input, 96);
			final MemberRef pending = member(input);
			final long invitationSequence = input.readLong();
			final int totalAttempts = input.readUnsignedByte();
			final int seatAttempts = input.readUnsignedByte();
			final int refusalCount = input.readUnsignedByte();
			if (refusalCount > PhantomRiftModel.MAX_REFUSALS)
			{
				throw new IllegalArgumentException("Rift refusal history exceeds its bound.");
			}
			final List<Refusal> refusals = new ArrayList<>(refusalCount);
			for (int i = 0; i < refusalCount; i++)
			{
				refusals.add(new Refusal(member(input), string(input, 96), input.readLong(), input.readLong(), string(input, 96)));
			}
			final String routeHash = string(input, 64);
			final long updated = input.readLong();
			final PartyBindingReceipt partyBinding = schema == LEGACY_SCHEMA_VERSION ? null : binding(input);
			final CandidateReceipt candidateReceipt = schema == LEGACY_SCHEMA_VERSION ? null : candidate(input);
			final PendingInvitationReceipt invitationReceipt = schema == LEGACY_SCHEMA_VERSION ? null : invitation(input);
			final boolean legacyUntrusted = schema == LEGACY_SCHEMA_VERSION || input.readBoolean();
			if (input.available() != 0)
			{
				throw new IllegalArgumentException("Trailing Rift preparation payload data.");
			}
			return new Preparation(leaderProfileId, goalId, goalRevision, tierType, stage, status, rosterHash, catalogHash, policyHash, configHash, roleHash, vacancyKey, pending, invitationSequence, totalAttempts, seatAttempts, refusals, routeHash, updated, partyBinding, candidateReceipt, invitationReceipt, legacyUntrusted);
		}
		catch (IllegalArgumentException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Cannot decode Rift preparation.", e);
		}
	}

	private static void base(DataOutputStream output, Preparation value) throws Exception
	{
		output.writeLong(value.leaderProfileId());
		output.writeLong(value.goalId());
		output.writeLong(value.goalRevision());
		output.writeByte(value.tierType());
		output.writeByte(value.stage().ordinal());
		output.writeByte(value.status().ordinal());
		string(output, value.rosterHash());
		string(output, value.catalogHash());
		string(output, value.policyHash());
		string(output, value.configHash());
		string(output, value.roleHash());
		string(output, value.missingVacancyKey());
		member(output, value.pendingCandidate());
		output.writeLong(value.pendingInvitationSequence());
		output.writeByte(value.totalAttempts());
		output.writeByte(value.seatAttempts());
		output.writeByte(value.refusals().size());
		for (Refusal refusal : value.refusals())
		{
			member(output, refusal.candidate());
			string(output, refusal.vacancyKey());
			output.writeLong(refusal.refusedEpochMillis());
			output.writeLong(refusal.cooldownUntilEpochMillis());
			string(output, refusal.reasonKey());
		}
		string(output, value.routeHash());
		output.writeLong(value.updatedEpochMillis());
	}

	private static void binding(DataOutputStream output, PartyBindingReceipt value) throws Exception
	{
		output.writeBoolean(value != null);
		if (value != null)
		{
			string(output, value.groupId());
			output.writeLong(value.groupGeneration());
			output.writeLong(value.membershipRevision());
			member(output, value.leader());
			string(output, value.rosterHash());
			string(output, value.manifestHash());
			output.writeByte(value.stability().ordinal());
		}
	}

	private static PartyBindingReceipt binding(DataInputStream input) throws Exception
	{
		return !input.readBoolean() ? null : new PartyBindingReceipt(string(input, 64), input.readLong(), input.readLong(), member(input), string(input, 64), string(input, 64), value(input, BindingStability.class));
	}

	private static void candidate(DataOutputStream output, CandidateReceipt value) throws Exception
	{
		output.writeBoolean(value != null);
		if (value != null)
		{
			string(output, value.vacancyKey());
			member(output, value.candidate());
			string(output, value.candidateEvidenceHash());
			string(output, value.selectedRosterHash());
			string(output, value.relationshipEvidenceHash());
		}
	}

	private static CandidateReceipt candidate(DataInputStream input) throws Exception
	{
		return !input.readBoolean() ? null : new CandidateReceipt(string(input, 96), member(input), string(input, 64), string(input, 64), string(input, 64));
	}

	private static void invitation(DataOutputStream output, PendingInvitationReceipt value) throws Exception
	{
		output.writeBoolean(value != null);
		if (value != null)
		{
			output.writeLong(value.sequence());
			output.writeInt(value.requesterObjectId());
			output.writeInt(value.inviteeObjectId());
			output.writeLong(value.requestedAtEpochMillis());
			output.writeLong(value.canonicalExpiresAtGameTick());
			output.writeByte(value.status().ordinal());
			string(output, value.reasonKey());
		}
	}

	private static PendingInvitationReceipt invitation(DataInputStream input) throws Exception
	{
		return !input.readBoolean() ? null : new PendingInvitationReceipt(input.readLong(), input.readInt(), input.readInt(), input.readLong(), input.readLong(), value(input, InvitationStatus.class), string(input, 96));
	}

	private static Stage legacyStage(int ordinal)
	{
		if (ordinal >= LEGACY_STAGES.length)
		{
			throw new IllegalArgumentException("Unknown legacy Rift stage.");
		}
		return LEGACY_STAGES[ordinal];
	}

	private static void member(DataOutputStream output, MemberRef member) throws Exception
	{
		output.writeBoolean(member != null);
		if (member != null)
		{
			output.writeByte(member.kind().ordinal());
			output.writeLong(member.profileId());
			output.writeInt(member.characterObjectId());
		}
	}

	private static MemberRef member(DataInputStream input) throws Exception
	{
		if (!input.readBoolean())
		{
			return null;
		}
		return new MemberRef(value(input, MemberKind.class), input.readLong(), input.readInt());
	}

	private static void string(DataOutputStream output, String value) throws Exception
	{
		final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 512)
		{
			throw new IllegalArgumentException("Rift state string exceeds its bound.");
		}
		output.writeShort(bytes.length);
		output.write(bytes);
	}

	private static String string(DataInputStream input, int maximum) throws Exception
	{
		final int length = input.readUnsignedShort();
		if (length > maximum)
		{
			throw new IllegalArgumentException("Rift state string exceeds its bound.");
		}
		final byte[] bytes = input.readNBytes(length);
		if (bytes.length != length)
		{
			throw new IllegalArgumentException("Truncated Rift state string.");
		}
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static <E extends Enum<E>> E value(DataInputStream input, Class<E> type) throws Exception
	{
		final int ordinal = input.readUnsignedByte();
		final E[] values = type.getEnumConstants();
		if (ordinal >= values.length)
		{
			throw new IllegalArgumentException("Unknown Rift state enum value.");
		}
		return values[ordinal];
	}
}
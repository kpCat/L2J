/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConflictPort.Outcome;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ActiveNegotiation;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.AgreementReceipt;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.AgreementStatus;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.Alternative;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ArbitrationEvidence;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ClaimReceipt;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.FarmingState;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.NegotiationStage;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ResourceKey;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ResourceScope;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.SemanticAct;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/** Compact schema-1 codec bounded by the shared profile component payload. */
public final class PhantomFarmingStateCodec
{
	private static final int MAGIC = 0x46524D31;

	public byte[] encode(FarmingState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeUTF(state.policyHash());
				output.writeUTF(state.authorityHash());
				output.writeLong(state.logicalMinute());
				output.writeBoolean(state.claim() != null);
				if (state.claim() != null)
				{
					writeClaim(output, state.claim());
				}
				output.writeBoolean(state.active() != null);
				if (state.active() != null)
				{
					writeActive(output, state.active());
				}
				output.writeByte(state.history().size());
				for (AgreementReceipt receipt : state.history())
				{
					writeAgreement(output, receipt);
				}
			}
			final byte[] result = bytes.toByteArray();
			if (result.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("Farming conflict state exceeds the profile component payload.");
			}
			return result;
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Could not encode farming conflict state.", exception);
		}
	}

	public FarmingState decode(byte[] payload)
	{
		if ((payload == null) || (payload.length == 0) || (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES))
		{
			throw new IllegalArgumentException("Farming conflict payload is outside bounds.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if (input.readInt() != MAGIC)
			{
				throw new IllegalArgumentException("Unknown farming conflict payload.");
			}
			final String policyHash = input.readUTF();
			final String authorityHash = input.readUTF();
			final long minute = input.readLong();
			final ClaimReceipt claim = input.readBoolean() ? readClaim(input) : null;
			final ActiveNegotiation active = input.readBoolean() ? readActive(input) : null;
			final int historySize = input.readUnsignedByte();
			if (historySize > PhantomFarmingModel.MAX_HISTORY)
			{
				throw new IllegalArgumentException("Farming agreement history exceeds its bound.");
			}
			final List<AgreementReceipt> history = new ArrayList<>(historySize);
			for (int index = 0; index < historySize; index++)
			{
				history.add(readAgreement(input));
			}
			if (input.read() != -1)
			{
				throw new IllegalArgumentException("Farming conflict payload contains trailing data.");
			}
			return new FarmingState(claim, active, history, policyHash, authorityHash, minute);
		}
		catch (EOFException exception)
		{
			throw new IllegalArgumentException("Farming conflict payload is truncated.", exception);
		}
		catch (IOException | RuntimeException exception)
		{
			if (exception instanceof IllegalArgumentException invalid)
			{
				throw invalid;
			}
			throw new IllegalArgumentException("Could not decode farming conflict state.", exception);
		}
	}

	public int declaredWorstCaseBytes()
	{
		return PhantomProfileComponent.MAX_PAYLOAD_BYTES;
	}

	private static void writeClaim(DataOutputStream output, ClaimReceipt claim) throws IOException
	{
		writeResource(output, claim.resource());
		output.writeLong(claim.goalId());
		output.writeLong(claim.goalRevision());
		output.writeUTF(claim.sourceId());
		output.writeInt(claim.targetItemId());
		output.writeLong(claim.requiredAmount());
		output.writeLong(claim.progress());
		output.writeLong(claim.remainingAmount());
		output.writeInt(claim.goalPriority());
		output.writeLong(claim.acquisitionRowVersion());
		output.writeUTF(claim.acquisitionEvidenceHash());
		output.writeUTF(claim.authorityHash());
		output.writeLong(claim.topologyGeneration());
		output.writeLong(claim.claimedMinute());
		output.writeLong(claim.leaseExpiryMinute());
		output.writeByte(claim.alternatives().size());
		for (Alternative alternative : claim.alternatives())
		{
			output.writeUTF(alternative.sourceId());
			output.writeByte(alternative.method().ordinal());
			output.writeInt(alternative.score());
		}
		output.writeBoolean(claim.switchFeasible());
	}

	private static ClaimReceipt readClaim(DataInputStream input) throws IOException
	{
		final ResourceKey resource = readResource(input);
		final long goalId = input.readLong();
		final long goalRevision = input.readLong();
		final String sourceId = input.readUTF();
		final int targetItemId = input.readInt();
		final long required = input.readLong();
		final long progress = input.readLong();
		final long remaining = input.readLong();
		final int priority = input.readInt();
		final long rowVersion = input.readLong();
		final String evidenceHash = input.readUTF();
		final String authorityHash = input.readUTF();
		final long generation = input.readLong();
		final long claimed = input.readLong();
		final long expiry = input.readLong();
		final int alternativesSize = input.readUnsignedByte();
		if (alternativesSize > PhantomFarmingModel.MAX_ALTERNATIVES)
		{
			throw new IllegalArgumentException("Farming alternatives exceed their bound.");
		}
		final List<Alternative> alternatives = new ArrayList<>(alternativesSize);
		for (int index = 0; index < alternativesSize; index++)
		{
			alternatives.add(new Alternative(input.readUTF(), ordinal(Method.values(), input.readUnsignedByte(), "acquisition method"), input.readInt()));
		}
		return new ClaimReceipt(resource, goalId, goalRevision, sourceId, targetItemId, required, progress, remaining, priority, rowVersion, evidenceHash, authorityHash, generation, claimed, expiry, alternatives, input.readBoolean());
	}

	private static void writeActive(DataOutputStream output, ActiveNegotiation active) throws IOException
	{
		output.writeUTF(active.agreementId());
		writeResource(output, active.resource());
		output.writeLong(active.lowerProfileId());
		output.writeLong(active.higherProfileId());
		output.writeLong(active.lowerGoalId());
		output.writeLong(active.lowerGoalRevision());
		output.writeUTF(active.lowerSourceId());
		output.writeLong(active.lowerRemaining());
		output.writeLong(active.higherGoalId());
		output.writeLong(active.higherGoalRevision());
		output.writeUTF(active.higherSourceId());
		output.writeLong(active.higherRemaining());
		output.writeByte(active.round());
		output.writeLong(active.proposerProfileId());
		output.writeByte(active.proposalAct().ordinal());
		output.writeByte(active.stage().ordinal());
		writeEvidence(output, active.evidence());
		output.writeLong(active.createdMinute());
		output.writeLong(active.expiryMinute());
	}

	private static ActiveNegotiation readActive(DataInputStream input) throws IOException
	{
		return new ActiveNegotiation(input.readUTF(), readResource(input), input.readLong(), input.readLong(), input.readLong(), input.readLong(), input.readUTF(), input.readLong(), input.readLong(), input.readLong(), input.readUTF(), input.readLong(), input.readUnsignedByte(), input.readLong(), ordinal(SemanticAct.values(), input.readUnsignedByte(), "semantic act"), ordinal(NegotiationStage.values(), input.readUnsignedByte(), "negotiation stage"), readEvidence(input), input.readLong(), input.readLong());
	}

	private static void writeEvidence(DataOutputStream output, ArbitrationEvidence evidence) throws IOException
	{
		output.writeLong(evidence.lowerProfileId());
		output.writeLong(evidence.higherProfileId());
		output.writeInt(evidence.lowerScore());
		output.writeInt(evidence.higherScore());
		output.writeInt(evidence.lowerPersistence());
		output.writeInt(evidence.higherPersistence());
		output.writeInt(evidence.lowerEscalation());
		output.writeInt(evidence.higherEscalation());
		output.writeInt(evidence.cooperation());
		output.writeLong(evidence.holderProfileId());
		output.writeUTF(evidence.topologyHash());
		output.writeLong(evidence.topologyGeneration());
		output.writeUTF(evidence.evidenceHash());
	}

	private static ArbitrationEvidence readEvidence(DataInputStream input) throws IOException
	{
		return new ArbitrationEvidence(input.readLong(), input.readLong(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readInt(), input.readLong(), input.readUTF(), input.readLong(), input.readUTF());
	}

	private static void writeAgreement(DataOutputStream output, AgreementReceipt receipt) throws IOException
	{
		output.writeUTF(receipt.agreementId());
		writeResource(output, receipt.resource());
		output.writeLong(receipt.lowerProfileId());
		output.writeLong(receipt.higherProfileId());
		output.writeLong(receipt.holderProfileId());
		output.writeLong(receipt.lowerGoalId());
		output.writeLong(receipt.lowerGoalRevision());
		output.writeUTF(receipt.lowerSourceId());
		output.writeLong(receipt.lowerRemaining());
		output.writeLong(receipt.higherGoalId());
		output.writeLong(receipt.higherGoalRevision());
		output.writeUTF(receipt.higherSourceId());
		output.writeLong(receipt.higherRemaining());
		output.writeByte(receipt.status().ordinal());
		output.writeByte(receipt.loserOutcome().ordinal());
		output.writeByte(receipt.acts().size());
		for (SemanticAct act : receipt.acts())
		{
			output.writeByte(act.ordinal());
		}
		output.writeUTF(receipt.reasonKey());
		output.writeUTF(receipt.evidenceHash());
		output.writeLong(receipt.createdMinute());
		output.writeLong(receipt.expiryMinute());
		output.writeBoolean(receipt.effectApplied());
		output.writeBoolean(receipt.socialRecorded());
	}

	private static AgreementReceipt readAgreement(DataInputStream input) throws IOException
	{
		final String agreementId = input.readUTF();
		final ResourceKey resource = readResource(input);
		final long lowerProfileId = input.readLong();
		final long higherProfileId = input.readLong();
		final long holderProfileId = input.readLong();
		final long lowerGoalId = input.readLong();
		final long lowerGoalRevision = input.readLong();
		final String lowerSourceId = input.readUTF();
		final long lowerRemaining = input.readLong();
		final long higherGoalId = input.readLong();
		final long higherGoalRevision = input.readLong();
		final String higherSourceId = input.readUTF();
		final long higherRemaining = input.readLong();
		final AgreementStatus status = ordinal(AgreementStatus.values(), input.readUnsignedByte(), "agreement status");
		final Outcome loserOutcome = ordinal(Outcome.values(), input.readUnsignedByte(), "gate outcome");
		final int actsSize = input.readUnsignedByte();
		if ((actsSize < 1) || (actsSize > PhantomFarmingModel.MAX_ACTS))
		{
			throw new IllegalArgumentException("Farming semantic acts exceed their bound.");
		}
		final List<SemanticAct> acts = new ArrayList<>(actsSize);
		for (int index = 0; index < actsSize; index++)
		{
			acts.add(ordinal(SemanticAct.values(), input.readUnsignedByte(), "semantic act"));
		}
		return new AgreementReceipt(agreementId, resource, lowerProfileId, higherProfileId, holderProfileId, lowerGoalId, lowerGoalRevision, lowerSourceId, lowerRemaining, higherGoalId, higherGoalRevision, higherSourceId, higherRemaining, status, loserOutcome, acts, input.readUTF(), input.readUTF(), input.readLong(), input.readLong(), input.readBoolean(), input.readBoolean());
	}

	private static void writeResource(DataOutputStream output, ResourceKey resource) throws IOException
	{
		output.writeByte(resource.scope().ordinal());
		output.writeUTF(resource.topologyNodeId());
		output.writeUTF(resource.anchorId());
		output.writeInt(resource.npcId());
	}

	private static ResourceKey readResource(DataInputStream input) throws IOException
	{
		return new ResourceKey(ordinal(ResourceScope.values(), input.readUnsignedByte(), "resource scope"), input.readUTF(), input.readUTF(), input.readInt());
	}

	private static <T> T ordinal(T[] values, int ordinal, String label)
	{
		if ((ordinal < 0) || (ordinal >= values.length))
		{
			throw new IllegalArgumentException("Unknown farming " + label + ".");
		}
		return values[ordinal];
	}
}

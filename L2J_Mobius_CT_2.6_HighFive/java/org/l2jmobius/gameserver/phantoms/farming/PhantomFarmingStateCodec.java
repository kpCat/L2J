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
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.CausalPerceptionReceipt;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.FarmingState;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.NegotiationStage;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ResourceKey;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.ResourceScope;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingModel.SemanticAct;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionChannel;

/** Compact schema-2 codec with deterministic legacy schema-1 decoding. */
public final class PhantomFarmingStateCodec
{
	private static final int MAGIC = 0x46524D32;
	private static final int LEGACY_MAGIC = 0x46524D31;

	public byte[] encode(FarmingState state)
	{
		return encode(state, PhantomFarmingModel.SCHEMA_VERSION);
	}

	private byte[] encode(FarmingState state, int schemaVersion)
	{
		if ((schemaVersion != 1) && (schemaVersion != PhantomFarmingModel.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unsupported farming conflict schema version.");
		}
		final boolean legacy = schemaVersion == 1;
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(legacy ? LEGACY_MAGIC : MAGIC);
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
					writeActive(output, state.active(), legacy);
				}
				output.writeByte(state.history().size());
				for (AgreementReceipt receipt : state.history())
				{
					writeAgreement(output, receipt, legacy);
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
			final int magic = input.readInt();
			if ((magic != MAGIC) && (magic != LEGACY_MAGIC))
			{
				throw new IllegalArgumentException("Unknown farming conflict payload.");
			}
			final boolean legacy = magic == LEGACY_MAGIC;
			final String policyHash = input.readUTF();
			final String authorityHash = input.readUTF();
			final long minute = input.readLong();
			final ClaimReceipt claim = input.readBoolean() ? readClaim(input) : null;
			final ActiveNegotiation active = input.readBoolean() ? readActive(input, legacy) : null;
			final int historySize = input.readUnsignedByte();
			if (historySize > PhantomFarmingModel.MAX_HISTORY)
			{
				throw new IllegalArgumentException("Farming agreement history exceeds its bound.");
			}
			final List<AgreementReceipt> history = new ArrayList<>(historySize);
			for (int index = 0; index < historySize; index++)
			{
				history.add(readAgreement(input, legacy));
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

	private static void writeActive(DataOutputStream output, ActiveNegotiation active, boolean legacy) throws IOException
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
		if (!legacy)
		{
			writePerception(output, active.perception());
		}
		output.writeLong(active.createdMinute());
		output.writeLong(active.expiryMinute());
		if (!legacy)
		{
			output.writeInt(active.socialDeliveryMask());
		}
	}

	private static ActiveNegotiation readActive(DataInputStream input, boolean legacy) throws IOException
	{
		final String agreementId = input.readUTF();
		final ResourceKey resource = readResource(input);
		final long lowerProfileId = input.readLong();
		final long higherProfileId = input.readLong();
		final long lowerGoalId = input.readLong();
		final long lowerGoalRevision = input.readLong();
		final String lowerSourceId = input.readUTF();
		final long lowerRemaining = input.readLong();
		final long higherGoalId = input.readLong();
		final long higherGoalRevision = input.readLong();
		final String higherSourceId = input.readUTF();
		final long higherRemaining = input.readLong();
		final int round = input.readUnsignedByte();
		final long proposerProfileId = input.readLong();
		final SemanticAct proposalAct = ordinal(SemanticAct.values(), input.readUnsignedByte(), "semantic act");
		final NegotiationStage stage = ordinal(NegotiationStage.values(), input.readUnsignedByte(), "negotiation stage");
		final ArbitrationEvidence evidence = readEvidence(input);
		final CausalPerceptionReceipt perception;
		final long createdMinute;
		final long expiryMinute;
		final int socialDeliveryMask;
		if (legacy)
		{
			createdMinute = input.readLong();
			expiryMinute = input.readLong();
			perception = CausalPerceptionReceipt.legacy(lowerProfileId, higherProfileId, createdMinute, expiryMinute);
			socialDeliveryMask = 0;
		}
		else
		{
			perception = readPerception(input);
			createdMinute = input.readLong();
			expiryMinute = input.readLong();
			socialDeliveryMask = input.readInt();
		}
		return new ActiveNegotiation(agreementId, resource, lowerProfileId, higherProfileId, lowerGoalId, lowerGoalRevision, lowerSourceId, lowerRemaining, higherGoalId, higherGoalRevision, higherSourceId, higherRemaining, round, proposerProfileId, proposalAct, stage, evidence, perception, createdMinute, expiryMinute, socialDeliveryMask);
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

	private static void writePerception(DataOutputStream output, CausalPerceptionReceipt perception) throws IOException
	{
		output.writeLong(perception.lowerProfileId());
		output.writeLong(perception.higherProfileId());
		output.writeLong(perception.topologyGeneration());
		output.writeUTF(perception.topologyHash());
		output.writeUTF(perception.lowerNodeId());
		output.writeLong(perception.lowerProfileSequence());
		output.writeUTF(perception.higherNodeId());
		output.writeLong(perception.higherProfileSequence());
		output.writeByte(perception.channel().ordinal());
		output.writeLong(perception.observedMinute());
		output.writeLong(perception.expiryMinute());
		output.writeUTF(perception.evidenceHash());
		output.writeBoolean(perception.trusted());
	}

	private static CausalPerceptionReceipt readPerception(DataInputStream input) throws IOException
	{
		return new CausalPerceptionReceipt(input.readLong(), input.readLong(), input.readLong(), input.readUTF(), input.readUTF(), input.readLong(), input.readUTF(), input.readLong(), ordinal(PhantomPerceptionChannel.values(), input.readUnsignedByte(), "perception channel"), input.readLong(), input.readLong(), input.readUTF(), input.readBoolean());
	}

	private static void writeAgreement(DataOutputStream output, AgreementReceipt receipt, boolean legacy) throws IOException
	{
		output.writeUTF(receipt.agreementId());
		writeResource(output, receipt.resource());
		output.writeLong(receipt.lowerProfileId());
		output.writeLong(receipt.higherProfileId());
		output.writeLong(receipt.holderProfileId());
		output.writeLong(receipt.lowerGoalId());
		output.writeLong(receipt.lowerGoalRevision());
		output.writeUTF(receipt.lowerSourceId());
		if (!legacy)
		{
			output.writeUTF(receipt.lowerAuthorityHash());
		}
		output.writeLong(receipt.lowerRemaining());
		output.writeLong(receipt.higherGoalId());
		output.writeLong(receipt.higherGoalRevision());
		output.writeUTF(receipt.higherSourceId());
		if (!legacy)
		{
			output.writeUTF(receipt.higherAuthorityHash());
		}
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
		if (!legacy)
		{
			writePerception(output, receipt.perception());
		}
		output.writeLong(receipt.createdMinute());
		output.writeLong(receipt.expiryMinute());
		output.writeBoolean(receipt.effectApplied());
		if (legacy)
		{
			output.writeBoolean(receipt.socialDeliveryMask() != 0);
		}
		else
		{
			output.writeInt(receipt.socialDeliveryMask());
		}
	}

	private static AgreementReceipt readAgreement(DataInputStream input, boolean legacy) throws IOException
	{
		final String agreementId = input.readUTF();
		final ResourceKey resource = readResource(input);
		final long lowerProfileId = input.readLong();
		final long higherProfileId = input.readLong();
		final long holderProfileId = input.readLong();
		final long lowerGoalId = input.readLong();
		final long lowerGoalRevision = input.readLong();
		final String lowerSourceId = input.readUTF();
		final String lowerAuthorityHash = legacy ? PhantomFarmingModel.sha256("farming.legacy.authority", lowerProfileId) : input.readUTF();
		final long lowerRemaining = input.readLong();
		final long higherGoalId = input.readLong();
		final long higherGoalRevision = input.readLong();
		final String higherSourceId = input.readUTF();
		final String higherAuthorityHash = legacy ? PhantomFarmingModel.sha256("farming.legacy.authority", higherProfileId) : input.readUTF();
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
		final String reasonKey = input.readUTF();
		final String evidenceHash = input.readUTF();
		final CausalPerceptionReceipt perception;
		final long createdMinute;
		final long expiryMinute;
		final boolean effectApplied;
		final int socialDeliveryMask;
		if (legacy)
		{
			createdMinute = input.readLong();
			expiryMinute = input.readLong();
			effectApplied = input.readBoolean();
			input.readBoolean();
			perception = CausalPerceptionReceipt.legacy(lowerProfileId, higherProfileId, createdMinute, expiryMinute);
			socialDeliveryMask = 0;
		}
		else
		{
			perception = readPerception(input);
			createdMinute = input.readLong();
			expiryMinute = input.readLong();
			effectApplied = input.readBoolean();
			socialDeliveryMask = input.readInt();
		}
		return new AgreementReceipt(agreementId, resource, lowerProfileId, higherProfileId, holderProfileId, lowerGoalId, lowerGoalRevision, lowerSourceId, lowerAuthorityHash, lowerRemaining, higherGoalId, higherGoalRevision, higherSourceId, higherAuthorityHash, higherRemaining, status, loserOutcome, acts, reasonKey, evidenceHash, perception, createdMinute, expiryMinute, effectApplied, socialDeliveryMask);
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

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionCatalog.Method;
import org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConflictPort.Outcome;
import org.l2jmobius.gameserver.phantoms.topology.PhantomPerceptionChannel;

/** Bounded typed truth for one profile's farming claim and agreements. */
public final class PhantomFarmingModel
{
	public static final String COMPONENT_TYPE = "farming.conflict";
	public static final int SCHEMA_VERSION = 2;
	public static final int MAX_HISTORY = 4;
	public static final int MAX_ALTERNATIVES = 4;
	public static final int MAX_ACTS = 3;
	public static final int SOCIAL_OFFER = 1;
	public static final int SOCIAL_RESPONSE = 1 << 1;
	public static final int SOCIAL_ESCALATION = 1 << 2;
	public static final int SOCIAL_TERMINAL = 1 << 3;
	public static final int SOCIAL_ALL = SOCIAL_OFFER | SOCIAL_RESPONSE | SOCIAL_ESCALATION | SOCIAL_TERMINAL;
	private static final Pattern HASH = Pattern.compile("^[0-9A-Fa-f]{64}$");

	public enum ResourceScope
	{
		ROOM,
		MOB_GROUP
	}

	public enum SemanticAct
	{
		SHARE,
		WAIT,
		MOVE,
		REFUSE,
		ESCALATE
	}

	public enum NegotiationStage
	{
		OFFER,
		RESPONSE
	}

	public enum AgreementStatus
	{
		SHARED,
		WAITING,
		MOVING,
		REFUSED,
		ESCALATED,
		FULFILLED,
		BROKEN,
		EXPIRED,
		STALE
	}

	public record ResourceKey(ResourceScope scope, String topologyNodeId, String anchorId, int npcId) implements Comparable<ResourceKey>
	{
		public ResourceKey
		{
			Objects.requireNonNull(scope);
			topologyNodeId = bounded(topologyNodeId, 96, "Topology node ID");
			anchorId = Objects.requireNonNullElse(anchorId, "");
			if ((scope == ResourceScope.ROOM) != anchorId.isEmpty() || ((scope == ResourceScope.ROOM) && (npcId != 0)) || ((scope == ResourceScope.MOB_GROUP) && (bounded(anchorId, 96, "Anchor ID").isEmpty() || (npcId <= 0))))
			{
				throw new IllegalArgumentException("Invalid farming resource identity.");
			}
		}

		public static ResourceKey room(String nodeId)
		{
			return new ResourceKey(ResourceScope.ROOM, nodeId, "", 0);
		}

		public static ResourceKey mobGroup(String nodeId, String anchorId, int npcId)
		{
			return new ResourceKey(ResourceScope.MOB_GROUP, nodeId, anchorId, npcId);
		}

		public String stableKey()
		{
			return scope + "|" + topologyNodeId + "|" + anchorId + "|" + npcId;
		}

		public String hash()
		{
			return sha256(stableKey());
		}

		@Override
		public int compareTo(ResourceKey other)
		{
			return stableKey().compareTo(other.stableKey());
		}
	}

	public record Alternative(String sourceId, Method method, int score)
	{
		public Alternative
		{
			sourceId = hash(sourceId, "Alternative source ID");
			Objects.requireNonNull(method);
		}
	}

	public record ClaimReceipt(ResourceKey resource, long goalId, long goalRevision, String sourceId, int targetItemId, long requiredAmount, long progress, long remainingAmount, int goalPriority, long acquisitionRowVersion, String acquisitionEvidenceHash, String authorityHash, long topologyGeneration, long claimedMinute, long leaseExpiryMinute, List<Alternative> alternatives, boolean switchFeasible)
	{
		public ClaimReceipt
		{
			Objects.requireNonNull(resource);
			sourceId = hash(sourceId, "Claim source ID");
			acquisitionEvidenceHash = hash(acquisitionEvidenceHash, "Acquisition evidence hash");
			authorityHash = hash(authorityHash, "Claim authority hash");
			alternatives = List.copyOf(alternatives);
			if ((goalId <= 0) || (goalRevision < 0) || (targetItemId <= 0) || (requiredAmount <= 0) || (progress < 0) || (remainingAmount != (requiredAmount - progress)) || (goalPriority < 0) || (goalPriority > 1000) || (acquisitionRowVersion < 0) || (topologyGeneration < 0) || (claimedMinute < 0) || (leaseExpiryMinute <= claimedMinute) || (alternatives.size() > MAX_ALTERNATIVES))
			{
				throw new IllegalArgumentException("Invalid farming claim receipt.");
			}
		}

		public boolean exactGoal(long exactGoalId, long exactRevision, String exactSourceId)
		{
			return (goalId == exactGoalId) && (goalRevision == exactRevision) && sourceId.equals(exactSourceId);
		}
	}

	public record ArbitrationEvidence(long lowerProfileId, long higherProfileId, int lowerScore, int higherScore, int lowerPersistence, int higherPersistence, int lowerEscalation, int higherEscalation, int cooperation, long holderProfileId, String topologyHash, long topologyGeneration, String evidenceHash)
	{
		public ArbitrationEvidence
		{
			topologyHash = hash(topologyHash, "Arbitration topology hash");
			evidenceHash = hash(evidenceHash, "Arbitration evidence hash");
			if ((lowerProfileId <= 0) || (higherProfileId <= lowerProfileId) || ((holderProfileId != lowerProfileId) && (holderProfileId != higherProfileId)) || (topologyGeneration < 0))
			{
				throw new IllegalArgumentException("Invalid farming arbitration evidence.");
			}
		}
	}

	public record CausalPerceptionReceipt(long lowerProfileId, long higherProfileId, long topologyGeneration, String topologyHash, String lowerNodeId, long lowerProfileSequence, String higherNodeId, long higherProfileSequence, PhantomPerceptionChannel channel, long observedMinute, long expiryMinute, String evidenceHash, boolean trusted)
	{
		public CausalPerceptionReceipt
		{
			topologyHash = hash(topologyHash, "Causal topology hash");
			lowerNodeId = bounded(lowerNodeId, 96, "Lower causal node ID");
			higherNodeId = bounded(higherNodeId, 96, "Higher causal node ID");
			Objects.requireNonNull(channel);
			evidenceHash = hash(evidenceHash, "Causal perception evidence hash");
			if ((lowerProfileId <= 0) || (higherProfileId <= lowerProfileId) || (topologyGeneration < 0) || (lowerProfileSequence < 0) || (higherProfileSequence < 0) || (observedMinute < 0) || (expiryMinute <= observedMinute))
			{
				throw new IllegalArgumentException("Invalid causal farming perception receipt.");
			}
		}

		public static CausalPerceptionReceipt legacy(long lowerProfileId, long higherProfileId, long observedMinute, long expiryMinute)
		{
			final String topologyHash = sha256("farming.causal.legacy.topology", lowerProfileId, higherProfileId);
			return new CausalPerceptionReceipt(lowerProfileId, higherProfileId, 0, topologyHash, "legacy.untrusted", 0, "legacy.untrusted", 0, PhantomPerceptionChannel.LOCAL_CHAT, observedMinute, expiryMinute, sha256("farming.causal.legacy", lowerProfileId, higherProfileId, observedMinute, expiryMinute), false);
		}
	}

	public record ActiveNegotiation(String agreementId, ResourceKey resource, long lowerProfileId, long higherProfileId, long lowerGoalId, long lowerGoalRevision, String lowerSourceId, long lowerRemaining, long higherGoalId, long higherGoalRevision, String higherSourceId, long higherRemaining, int round, long proposerProfileId, SemanticAct proposalAct, NegotiationStage stage, ArbitrationEvidence evidence, CausalPerceptionReceipt perception, long createdMinute, long expiryMinute, int socialDeliveryMask)
	{
		public ActiveNegotiation
		{
			agreementId = hash(agreementId, "Agreement ID");
			Objects.requireNonNull(resource);
			lowerSourceId = hash(lowerSourceId, "Lower source ID");
			higherSourceId = hash(higherSourceId, "Higher source ID");
			Objects.requireNonNull(proposalAct);
			Objects.requireNonNull(stage);
			Objects.requireNonNull(evidence);
			Objects.requireNonNull(perception);
			if ((lowerProfileId <= 0) || (higherProfileId <= lowerProfileId) || (lowerGoalId <= 0) || (higherGoalId <= 0) || (lowerGoalRevision < 0) || (higherGoalRevision < 0) || (lowerRemaining < 0) || (higherRemaining < 0) || (round < 1) || (round > 16) || ((proposerProfileId != lowerProfileId) && (proposerProfileId != higherProfileId)) || (createdMinute < 0) || (expiryMinute <= createdMinute) || ((socialDeliveryMask & ~SOCIAL_ALL) != 0))
			{
				throw new IllegalArgumentException("Invalid active farming negotiation.");
			}
		}

		public ActiveNegotiation withSocialDelivery(int delivery)
		{
			return new ActiveNegotiation(agreementId, resource, lowerProfileId, higherProfileId, lowerGoalId, lowerGoalRevision, lowerSourceId, lowerRemaining, higherGoalId, higherGoalRevision, higherSourceId, higherRemaining, round, proposerProfileId, proposalAct, stage, evidence, perception, createdMinute, expiryMinute, socialDeliveryMask | delivery);
		}
	}

	public record AgreementReceipt(String agreementId, ResourceKey resource, long lowerProfileId, long higherProfileId, long holderProfileId, long lowerGoalId, long lowerGoalRevision, String lowerSourceId, String lowerAuthorityHash, long lowerRemaining, long higherGoalId, long higherGoalRevision, String higherSourceId, String higherAuthorityHash, long higherRemaining, AgreementStatus status, Outcome loserOutcome, List<SemanticAct> acts, String reasonKey, String evidenceHash, CausalPerceptionReceipt perception, long createdMinute, long expiryMinute, boolean effectApplied, int socialDeliveryMask)
	{
		public AgreementReceipt
		{
			agreementId = hash(agreementId, "Agreement ID");
			Objects.requireNonNull(resource);
			lowerSourceId = hash(lowerSourceId, "Lower source ID");
			lowerAuthorityHash = hash(lowerAuthorityHash, "Lower live authority hash");
			higherSourceId = hash(higherSourceId, "Higher source ID");
			higherAuthorityHash = hash(higherAuthorityHash, "Higher live authority hash");
			Objects.requireNonNull(status);
			Objects.requireNonNull(loserOutcome);
			acts = List.copyOf(acts);
			reasonKey = bounded(reasonKey, 64, "Agreement reason");
			evidenceHash = hash(evidenceHash, "Agreement evidence hash");
			Objects.requireNonNull(perception);
			if ((lowerProfileId <= 0) || (higherProfileId <= lowerProfileId) || ((holderProfileId != lowerProfileId) && (holderProfileId != higherProfileId)) || (lowerGoalId <= 0) || (higherGoalId <= 0) || (lowerGoalRevision < 0) || (higherGoalRevision < 0) || (lowerRemaining < 0) || (higherRemaining < 0) || (acts.isEmpty()) || (acts.size() > MAX_ACTS) || ((loserOutcome != Outcome.MOVE) && (loserOutcome != Outcome.WAIT) && (status != AgreementStatus.SHARED) && (status != AgreementStatus.FULFILLED) && (status != AgreementStatus.BROKEN) && (status != AgreementStatus.EXPIRED) && (status != AgreementStatus.STALE)) || (createdMinute < 0) || (expiryMinute <= createdMinute) || ((socialDeliveryMask & ~SOCIAL_ALL) != 0))
			{
				throw new IllegalArgumentException("Invalid farming agreement receipt.");
			}
		}

		public long counterpart(long profileId)
		{
			return profileId == lowerProfileId ? higherProfileId : profileId == higherProfileId ? lowerProfileId : 0;
		}

		public Outcome outcomeFor(long profileId)
		{
			if ((profileId != lowerProfileId) && (profileId != higherProfileId))
			{
				return Outcome.STALE;
			}
			if (status == AgreementStatus.SHARED)
			{
				return Outcome.SHARE;
			}
			return profileId == holderProfileId ? Outcome.ALLOW : loserOutcome;
		}

		public boolean exactPair(AgreementReceipt other)
		{
			return sameIdentity(other) && (status == other.status);
		}

		public boolean sameIdentity(AgreementReceipt other)
		{
			return (other != null) && agreementId.equals(other.agreementId) && resource.equals(other.resource) && (lowerProfileId == other.lowerProfileId) && (higherProfileId == other.higherProfileId) && (holderProfileId == other.holderProfileId) && (lowerGoalId == other.lowerGoalId) && (lowerGoalRevision == other.lowerGoalRevision) && lowerSourceId.equals(other.lowerSourceId) && lowerAuthorityHash.equals(other.lowerAuthorityHash) && (lowerRemaining == other.lowerRemaining) && (higherGoalId == other.higherGoalId) && (higherGoalRevision == other.higherGoalRevision) && higherSourceId.equals(other.higherSourceId) && higherAuthorityHash.equals(other.higherAuthorityHash) && (higherRemaining == other.higherRemaining) && (loserOutcome == other.loserOutcome) && acts.equals(other.acts) && reasonKey.equals(other.reasonKey) && evidenceHash.equals(other.evidenceHash) && perception.equals(other.perception) && (createdMinute == other.createdMinute) && (expiryMinute == other.expiryMinute);
		}

		public AgreementReceipt withStatus(AgreementStatus replacement, boolean applied)
		{
			return new AgreementReceipt(agreementId, resource, lowerProfileId, higherProfileId, holderProfileId, lowerGoalId, lowerGoalRevision, lowerSourceId, lowerAuthorityHash, lowerRemaining, higherGoalId, higherGoalRevision, higherSourceId, higherAuthorityHash, higherRemaining, replacement, loserOutcome, acts, reasonKey, evidenceHash, perception, createdMinute, expiryMinute, applied, socialDeliveryMask);
		}

		public AgreementReceipt withPerception(CausalPerceptionReceipt replacement)
		{
			return new AgreementReceipt(agreementId, resource, lowerProfileId, higherProfileId, holderProfileId, lowerGoalId, lowerGoalRevision, lowerSourceId, lowerAuthorityHash, lowerRemaining, higherGoalId, higherGoalRevision, higherSourceId, higherAuthorityHash, higherRemaining, status, loserOutcome, acts, reasonKey, evidenceHash, replacement, createdMinute, expiryMinute, effectApplied, socialDeliveryMask);
		}

		public AgreementReceipt withBinding(CausalPerceptionReceipt replacement, String lowerAuthority, String higherAuthority)
		{
			return new AgreementReceipt(agreementId, resource, lowerProfileId, higherProfileId, holderProfileId, lowerGoalId, lowerGoalRevision, lowerSourceId, lowerAuthority, lowerRemaining, higherGoalId, higherGoalRevision, higherSourceId, higherAuthority, higherRemaining, status, loserOutcome, acts, reasonKey, evidenceHash, replacement, createdMinute, expiryMinute, effectApplied, socialDeliveryMask);
		}

		public AgreementReceipt withSocialDelivery(int delivery)
		{
			return new AgreementReceipt(agreementId, resource, lowerProfileId, higherProfileId, holderProfileId, lowerGoalId, lowerGoalRevision, lowerSourceId, lowerAuthorityHash, lowerRemaining, higherGoalId, higherGoalRevision, higherSourceId, higherAuthorityHash, higherRemaining, status, loserOutcome, acts, reasonKey, evidenceHash, perception, createdMinute, expiryMinute, effectApplied, socialDeliveryMask | delivery);
		}
	}

	public record FarmingState(ClaimReceipt claim, ActiveNegotiation active, List<AgreementReceipt> history, String policyHash, String authorityHash, long logicalMinute)
	{
		public FarmingState
		{
			history = List.copyOf(history);
			policyHash = hash(policyHash, "Farming policy hash");
			authorityHash = hash(authorityHash, "Farming authority hash");
			if ((history.size() > MAX_HISTORY) || (logicalMinute < 0))
			{
				throw new IllegalArgumentException("Invalid bounded farming state.");
			}
		}

		public static FarmingState empty(String policyHash, String authorityHash, long minute)
		{
			return new FarmingState(null, null, List.of(), policyHash, authorityHash, minute);
		}

		public AgreementReceipt agreement(String agreementId)
		{
			return history.stream().filter(receipt -> receipt.agreementId().equals(agreementId)).findFirst().orElse(null);
		}

		public AgreementReceipt latest()
		{
			return history.isEmpty() ? null : history.getLast();
		}

		public FarmingState withClaim(ClaimReceipt replacement, String replacementAuthorityHash, long minute)
		{
			return new FarmingState(replacement, active, history, policyHash, replacementAuthorityHash, minute);
		}

		public FarmingState withActive(ActiveNegotiation replacement, long minute)
		{
			return new FarmingState(claim, replacement, history, policyHash, authorityHash, minute);
		}

		public FarmingState withAgreement(AgreementReceipt receipt, long minute)
		{
			final ArrayList<AgreementReceipt> next = new ArrayList<>(history);
			next.removeIf(existing -> existing.agreementId().equals(receipt.agreementId()));
			next.add(receipt);
			next.sort(Comparator.comparingLong(AgreementReceipt::createdMinute).thenComparing(AgreementReceipt::agreementId));
			while (next.size() > MAX_HISTORY)
			{
				next.removeFirst();
			}
			return new FarmingState(claim, null, List.copyOf(next), policyHash, authorityHash, minute);
		}
	}

	public static String sha256(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().withUpperCase().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	static String hash(String value, String label)
	{
		if ((value == null) || !HASH.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " must be a SHA-256.");
		}
		return value;
	}

	static String bounded(String value, int maximum, String label)
	{
		Objects.requireNonNull(value, label + " must not be null.");
		if (value.isBlank() || !value.equals(value.trim()) || (value.getBytes(StandardCharsets.UTF_8).length > maximum) || value.indexOf('|') >= 0)
		{
			throw new IllegalArgumentException(label + " is outside bounds.");
		}
		return value;
	}

	private PhantomFarmingModel()
	{
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;

/** Immutable bounded conversation values; none owns live server state. */
public final class PhantomConversationModel
{
	public static final String COMPONENT_TYPE = "conversation.state";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_SESSIONS = 8;
	public static final int MAX_RECENT_HASHES = 8;
	public static final int MAX_PENDING_SLOTS = 4;
	public static final int MAX_PROPOSAL_SLOTS = 8;
	public static final int MAX_EVIDENCE = 16;
	private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");
	private static final Pattern HASH = Pattern.compile("^[A-F0-9]{64}$");

	private PhantomConversationModel()
	{
	}

	public enum Authorization
	{
		CHECKPOINT_2_REQUIRED
	}

	public record DeliveredObservation(long dispatchId, Origin origin, int speakerObjectId, String speakerName, ChatType channel, String whisperTarget, String text, long epochMillis, int recipientObjectId, String recipientName)
	{
		public DeliveredObservation
		{
			if ((dispatchId <= 0) || (origin == null) || (speakerObjectId <= 0) || !boundedText(speakerName, 64) || (channel == null) || (text == null) || text.isEmpty() || (text.length() > 1024) || (epochMillis < 0) || (recipientObjectId <= 0) || !boundedText(recipientName, 64))
			{
				throw new IllegalArgumentException("Delivered conversation observation is invalid.");
			}
			whisperTarget = whisperTarget == null ? "" : whisperTarget;
		}

		public String observationHash()
		{
			return sha256(dispatchId + "|" + origin + '|' + speakerObjectId + '|' + channel + '|' + whisperTarget + '|' + text + '|' + epochMillis);
		}
	}

	public record ObservationBatch(long dispatchId, String observationHash, DeliveredObservation descriptor, List<DeliveredObservation> observers, long firstPulse, boolean overflow)
	{
		public ObservationBatch
		{
			observationHash = requireHash(observationHash, "Observation hash");
			Objects.requireNonNull(descriptor);
			observers = List.copyOf(observers);
			if ((dispatchId <= 0) || (descriptor.dispatchId() != dispatchId) || (firstPulse < 0) || (observers.size() > 32))
			{
				throw new IllegalArgumentException("Observation batch is invalid.");
			}
			final Set<Integer> recipients = new HashSet<>();
			for (DeliveredObservation observer : observers)
			{
				if ((observer.dispatchId() != dispatchId) || !observer.observationHash().equals(observationHash) || !recipients.add(observer.recipientObjectId()))
				{
					throw new IllegalArgumentException("Observation batch contains inconsistent recipients.");
				}
			}
		}
	}

	public record ConversationSubject(PhantomDomainRef reference)
	{
		public ConversationSubject
		{
			Objects.requireNonNull(reference);
		}
	}

	public record PendingClarification(String intentKey, List<SlotValue> knownSlots, Set<SlotType> missingSlots, long expiryMinute, String packHash, String corpusHash, String knowledgeHash, String topologyHash, String roleHash)
	{
		public PendingClarification
		{
			intentKey = requireKey(intentKey, "Pending intent");
			knownSlots = sortedSlots(knownSlots, MAX_PENDING_SLOTS);
			missingSlots = Set.copyOf(missingSlots);
			if (missingSlots.isEmpty() || (missingSlots.size() > MAX_PENDING_SLOTS) || (expiryMinute < 0))
			{
				throw new IllegalArgumentException("Pending clarification bounds are invalid.");
			}
			packHash = requireHash(packHash, "Pending pack hash");
			corpusHash = requireHash(corpusHash, "Pending corpus hash");
			knowledgeHash = requireHash(knowledgeHash, "Pending knowledge hash");
			topologyHash = requireHash(topologyHash, "Pending topology hash");
			roleHash = requireHash(roleHash, "Pending role hash");
		}
	}

	public record ConversationSession(ChatType channel, PhantomDomainRef counterpart, long lastObservedMinute, long cooldownUntilMinute, String previousIntent, List<SlotValue> previousSlots, PendingClarification pending, String lastResponseActHash, String lastStyleHash, String lastProposalHash) implements Comparable<ConversationSession>
	{
		public ConversationSession
		{
			Objects.requireNonNull(channel);
			Objects.requireNonNull(counterpart);
			if ((lastObservedMinute < 0) || (cooldownUntilMinute < lastObservedMinute))
			{
				throw new IllegalArgumentException("Conversation session minute bounds are invalid.");
			}
			if (previousIntent != null)
			{
				previousIntent = requireKey(previousIntent, "Previous intent");
			}
			previousSlots = sortedSlots(previousSlots, MAX_PENDING_SLOTS);
			lastResponseActHash = optionalHash(lastResponseActHash);
			lastStyleHash = optionalHash(lastStyleHash);
			lastProposalHash = optionalHash(lastProposalHash);
			int storedTextBytes = counterpart.key().getBytes(StandardCharsets.UTF_8).length;
			for (SlotValue slot : previousSlots)
			{
				storedTextBytes += storedValueBytes(slot);
			}
			if (pending != null)
			{
				for (SlotValue slot : pending.knownSlots())
				{
					storedTextBytes += storedValueBytes(slot);
				}
			}
			if (storedTextBytes > 192)
			{
				throw new IllegalArgumentException("Conversation session stored text exceeds 192 UTF-8 bytes.");
			}
		}

		public String key()
		{
			return channel.name() + '|' + counterpart.namespace() + ':' + counterpart.key();
		}

		@Override
		public int compareTo(ConversationSession other)
		{
			return key().compareTo(other.key());
		}
	}

	public record ConversationState(String catalogHash, String packHash, String corpusHash, String knowledgeHash, String topologyHash, String roleHash, String socialHash, long logicalMinute, List<ConversationSession> sessions, List<String> recentObservationHashes)
	{
		public ConversationState
		{
			catalogHash = requireHash(catalogHash, "Conversation catalog hash");
			packHash = requireHash(packHash, "Conversation semantic pack hash");
			corpusHash = requireHash(corpusHash, "Conversation semantic corpus hash");
			knowledgeHash = requireHash(knowledgeHash, "Conversation knowledge hash");
			topologyHash = requireHash(topologyHash, "Conversation topology hash");
			roleHash = requireHash(roleHash, "Conversation role hash");
			socialHash = requireHash(socialHash, "Conversation social hash");
			if (logicalMinute < 0)
			{
				throw new IllegalArgumentException("Conversation logical minute is invalid.");
			}
			sessions = sortedSessions(sessions);
			recentObservationHashes = orderedHashes(recentObservationHashes);
		}
	}

	public record ConversationActionProposal(String proposalKey, PhantomDomainRef actorProfile, PhantomDomainRef target, List<SlotValue> slots, String semanticResultHash, String observationHash, int confidence, long createdMinute, long expiryMinute, Authorization authorization)
	{
		public ConversationActionProposal
		{
			proposalKey = requireKey(proposalKey, "Conversation proposal key");
			Objects.requireNonNull(actorProfile);
			if (!actorProfile.namespace().equals("profile"))
			{
				throw new IllegalArgumentException("Conversation proposal actor must be a profile reference.");
			}
			slots = sortedSlots(slots, MAX_PROPOSAL_SLOTS);
			semanticResultHash = requireHash(semanticResultHash, "Proposal semantic hash");
			observationHash = requireHash(observationHash, "Proposal observation hash");
			if ((confidence < 0) || (confidence > 10000) || (createdMinute < 0) || (expiryMinute <= createdMinute) || (authorization != Authorization.CHECKPOINT_2_REQUIRED))
			{
				throw new IllegalArgumentException("Conversation proposal metadata is invalid.");
			}
		}
	}

	public record ConversationEvidence(String key, String value)
	{
		public ConversationEvidence
		{
			key = requireKey(key, "Conversation evidence key");
			if (!boundedText(value, 192))
			{
				throw new IllegalArgumentException("Conversation evidence value is invalid.");
			}
		}
	}

	public enum DeliveryPolicy
	{
		SEND,
		SUPPRESS_ACK
	}

	public record ConversationResponsePlan(long ownerProfileId, long dispatchId, String observationHash, ChatType channel, ConversationSubject counterpart, String semanticResultHash, String responseAct, String style, String renderedText, ConversationActionProposal proposal, DeliveryPolicy deliveryPolicy, long cooldownUntilMinute, List<ConversationEvidence> evidence)
	{
		public ConversationResponsePlan
		{
			if ((ownerProfileId <= 0) || (dispatchId <= 0) || (channel == null) || (counterpart == null) || (deliveryPolicy == null) || (cooldownUntilMinute < 0))
			{
				throw new IllegalArgumentException("Conversation response plan identity is invalid.");
			}
			observationHash = requireHash(observationHash, "Plan observation hash");
			semanticResultHash = requireHash(semanticResultHash, "Plan semantic hash");
			responseAct = requireKey(responseAct, "Response act");
			style = requireKey(style, "Response style");
			if (!boundedText(renderedText, 400) || (renderedText.codePointCount(0, renderedText.length()) > 100) || (renderedText.getBytes(StandardCharsets.UTF_8).length > 400) || (renderedText.indexOf(8) >= 0))
			{
				throw new IllegalArgumentException("Rendered conversation text is invalid.");
			}
			evidence = List.copyOf(evidence);
			if (evidence.size() > MAX_EVIDENCE)
			{
				throw new IllegalArgumentException("Conversation evidence exceeds 16 entries.");
			}
		}

		public ConversationResponsePlan(long ownerProfileId, long dispatchId, String observationHash, ChatType channel, ConversationSubject counterpart, String semanticResultHash, String responseAct, String style, String renderedText, ConversationActionProposal proposal, long cooldownUntilMinute, List<ConversationEvidence> evidence)
		{
			this(ownerProfileId, dispatchId, observationHash, channel, counterpart, semanticResultHash, responseAct, style, renderedText, proposal, DeliveryPolicy.SEND, cooldownUntilMinute, evidence);
		}
	}

	public static String semanticResultHash(String canonicalEncoding)
	{
		return sha256(canonicalEncoding);
	}

	public static String sha256(String value)
	{
		try
		{
			return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	static String requireKey(String value, String label)
	{
		if ((value == null) || !KEY.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " is invalid.");
		}
		return value;
	}

	static String requireHash(String value, String label)
	{
		if ((value == null) || !HASH.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " is invalid.");
		}
		return value;
	}

	private static String optionalHash(String value)
	{
		return ((value == null) || value.isEmpty()) ? "" : requireHash(value, "Optional conversation hash");
	}

	private static List<SlotValue> sortedSlots(List<SlotValue> input, int maximum)
	{
		final List<SlotValue> result = new ArrayList<>(input == null ? List.of() : input);
		if (result.size() > maximum)
		{
			throw new IllegalArgumentException("Conversation slot count exceeds its bound.");
		}
		result.sort(Comparator.naturalOrder());
		final Set<SlotType> types = new HashSet<>();
		for (SlotValue slot : result)
		{
			if ((slot == null) || !types.add(slot.type()))
			{
				throw new IllegalArgumentException("Conversation slots contain a duplicate type.");
			}
		}
		return List.copyOf(result);
	}

	private static List<ConversationSession> sortedSessions(List<ConversationSession> input)
	{
		final List<ConversationSession> result = new ArrayList<>(input == null ? List.of() : input);
		if (result.size() > MAX_SESSIONS)
		{
			throw new IllegalArgumentException("Conversation session count exceeds eight.");
		}
		result.sort(Comparator.naturalOrder());
		for (int index = 1; index < result.size(); index++)
		{
			if (result.get(index - 1).key().equals(result.get(index).key()))
			{
				throw new IllegalArgumentException("Conversation session keys must be unique.");
			}
		}
		return List.copyOf(result);
	}

	private static List<String> orderedHashes(List<String> input)
	{
		final List<String> result = new ArrayList<>(input == null ? List.of() : input);
		if (result.size() > MAX_RECENT_HASHES)
		{
			throw new IllegalArgumentException("Recent observation hash count exceeds eight.");
		}
		result.replaceAll(value -> requireHash(value, "Recent observation hash"));
		final Set<String> unique = new HashSet<>();
		for (String hash : result)
		{
			if (!unique.add(hash))
			{
				throw new IllegalArgumentException("Recent observation hashes must be unique.");
			}
		}
		return List.copyOf(result);
	}

	private static boolean boundedText(String value, int maximum)
	{
		if ((value == null) || value.isBlank() || (value.length() > maximum))
		{
			return false;
		}
		return value.codePoints().noneMatch(Character::isISOControl);
	}

	private static int storedValueBytes(SlotValue slot)
	{
		if (slot.domainReference() != null)
		{
			return slot.domainReference().key().getBytes(StandardCharsets.UTF_8).length;
		}
		return slot.textValue() == null ? 0 : slot.textValue().getBytes(StandardCharsets.UTF_8).length;
	}
}

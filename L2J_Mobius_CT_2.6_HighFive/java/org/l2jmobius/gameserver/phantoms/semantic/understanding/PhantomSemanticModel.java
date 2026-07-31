/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.semantic.understanding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;

/**
 * Immutable language-understanding values. None of these records owns a mutable server object.
 */
public final class PhantomSemanticModel
{
	public static final int MAX_PARTY_MEMBERS = 9;
	public static final int MAX_CONTEXT_PLAYERS = 32;
	public static final int MAX_PREVIOUS_SLOTS = 16;
	public static final int MAX_RESULT_SLOTS = 16;
	public static final int MAX_ALTERNATIVES = 4;
	public static final int MAX_EVIDENCE = 16;
	private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");
	private static final Pattern HASH_PATTERN = Pattern.compile("^[A-F0-9]{64}$");

	private PhantomSemanticModel()
	{
	}

	public enum UnderstandingStatus
	{
		ACCEPTED,
		CLARIFICATION_REQUIRED,
		REJECTED
	}

	public enum SlotType
	{
		TARGET_PLAYER,
		PARTY_ROLE,
		CAPABILITY,
		ITEM,
		NPC,
		CONTENT,
		TOPOLOGY_NODE,
		LOCATION,
		QUANTITY,
		RESPONSE
	}

	public enum TokenKind
	{
		WORD,
		NUMBER,
		PUNCTUATION
	}

	public enum EvidenceQuality
	{
		EXACT,
		TRANSLITERATION,
		ABBREVIATION,
		FUZZY,
		CONTEXT
	}

	public enum InputChannel
	{
		NONE,
		LOCAL,
		PRIVATE,
		PARTY,
		TRADE
	}

	public record Token(String value, String canonicalValue, int originalStartCodePoint, int originalEndCodePoint, TokenKind kind, EvidenceQuality lexicalQuality)
	{
		public Token
		{
			if ((value == null) || value.isEmpty() || (canonicalValue == null) || canonicalValue.isEmpty() || (originalStartCodePoint < 0) || (originalEndCodePoint <= originalStartCodePoint))
			{
				throw new IllegalArgumentException("Invalid semantic token.");
			}
			Objects.requireNonNull(kind, "Semantic token kind must not be null.");
			Objects.requireNonNull(lexicalQuality, "Semantic token quality must not be null.");
		}
	}

	public record NormalizedText(String value, String normalizedHash, List<Token> tokens)
	{
		public NormalizedText
		{
			if ((value == null) || value.isBlank())
			{
				throw new IllegalArgumentException("Normalized semantic text must not be blank.");
			}
			normalizedHash = requireHash(normalizedHash, "Normalized text hash");
			tokens = List.copyOf(Objects.requireNonNull(tokens, "Normalized semantic tokens must not be null."));
			if (tokens.isEmpty())
			{
				throw new IllegalArgumentException("Normalized semantic text must contain tokens.");
			}
		}
	}

	public record PlayerReference(PhantomDomainRef reference, String exactName)
	{
		public PlayerReference
		{
			Objects.requireNonNull(reference, "Context player reference must not be null.");
			if (!reference.namespace().equals("character.object") && !reference.namespace().equals("profile"))
			{
				throw new IllegalArgumentException("Context players must use character.object or profile identity.");
			}
			if ((exactName == null) || exactName.isBlank() || (exactName.codePointCount(0, exactName.length()) > 64))
			{
				throw new IllegalArgumentException("Context player name must contain 1..64 code points.");
			}
			exactName.codePoints().forEach(codePoint ->
			{
				if (Character.isISOControl(codePoint))
				{
					throw new IllegalArgumentException("Context player name contains a control character.");
				}
			});
		}
	}

	public record SlotValue(SlotType type, PhantomDomainRef domainReference, Long numericValue, String textValue, int originalStartCodePoint, int originalEndCodePoint) implements Comparable<SlotValue>
	{
		private static final Comparator<SlotValue> ORDER = Comparator.comparing(SlotValue::type).thenComparing(SlotValue::canonicalValue);

		public SlotValue
		{
			Objects.requireNonNull(type, "Semantic slot type must not be null.");
			final int valueCount = (domainReference == null ? 0 : 1) + (numericValue == null ? 0 : 1) + (textValue == null ? 0 : 1);
			if ((valueCount != 1) || (originalStartCodePoint < -1) || (originalEndCodePoint < -1) || ((originalStartCodePoint >= 0) && (originalEndCodePoint <= originalStartCodePoint)))
			{
				throw new IllegalArgumentException("Semantic slot must contain exactly one typed value and a valid span.");
			}
			if ((numericValue != null) && ((type != SlotType.QUANTITY) || (numericValue < 1)))
			{
				throw new IllegalArgumentException("Semantic numeric slot must be a positive quantity.");
			}
			if ((textValue != null) && ((type != SlotType.RESPONSE) || textValue.isBlank() || (textValue.length() > 64)))
			{
				throw new IllegalArgumentException("Semantic text slot must be a bounded response.");
			}
		}

		public static SlotValue domain(SlotType type, PhantomDomainRef reference, int start, int end)
		{
			return new SlotValue(type, Objects.requireNonNull(reference), null, null, start, end);
		}

		public static SlotValue quantity(long value, int start, int end)
		{
			return new SlotValue(SlotType.QUANTITY, null, value, null, start, end);
		}

		public static SlotValue response(String value, int start, int end)
		{
			return new SlotValue(SlotType.RESPONSE, null, null, value, start, end);
		}

		public String canonicalValue()
		{
			if (domainReference != null)
			{
				return domainReference.namespace() + ':' + domainReference.key();
			}
			return numericValue != null ? Long.toString(numericValue) : textValue;
		}

		@Override
		public int compareTo(SlotValue other)
		{
			return ORDER.compare(this, other);
		}
	}

	public record InputContext(PlayerReference speaker, InputChannel channel, PlayerReference partyLeader, List<PlayerReference> partyMembers, List<PlayerReference> nearbyPlayers, List<PlayerReference> recentPlayers, PlayerReference selectedTarget, PhantomDomainRef currentLocation, PhantomDomainRef currentTopology, String previousAcceptedIntent, List<SlotValue> previousSlots)
	{
		public InputContext
		{
			channel = channel == null ? InputChannel.NONE : channel;
			partyMembers = immutablePlayers(partyMembers, MAX_PARTY_MEMBERS, "party members");
			nearbyPlayers = immutablePlayers(nearbyPlayers, MAX_CONTEXT_PLAYERS, "nearby players");
			recentPlayers = immutablePlayers(recentPlayers, MAX_CONTEXT_PLAYERS, "recent players");
			final Set<PhantomDomainRef> nearbyAndRecent = new HashSet<>();
			nearbyPlayers.forEach(player -> nearbyAndRecent.add(player.reference()));
			recentPlayers.forEach(player -> nearbyAndRecent.add(player.reference()));
			if (nearbyAndRecent.size() > MAX_CONTEXT_PLAYERS)
			{
				throw new IllegalArgumentException("Combined nearby/recent player context exceeds 32 identities.");
			}
			validateContextRef(currentLocation, "location");
			validateContextRef(currentTopology, "topology");
			if (previousAcceptedIntent != null)
			{
				previousAcceptedIntent = requireKey(previousAcceptedIntent, "Previous accepted intent");
			}
			previousSlots = immutableSlots(previousSlots, MAX_PREVIOUS_SLOTS);
		}

		public static InputContext empty()
		{
			return new InputContext(null, InputChannel.NONE, null, List.of(), List.of(), List.of(), null, null, null, null, List.of());
		}

		private static void validateContextRef(PhantomDomainRef reference, String label)
		{
			if ((reference != null) && !reference.namespace().equals("topology.node") && !reference.namespace().equals("location"))
			{
				throw new IllegalArgumentException("Context " + label + " must use topology.node or location identity.");
			}
		}
	}

	public record EntityCandidate(SlotType slotType, PhantomDomainRef reference, int score, EvidenceQuality quality, int originalStartCodePoint, int originalEndCodePoint)
	{
		public EntityCandidate
		{
			Objects.requireNonNull(slotType, "Entity candidate slot type must not be null.");
			Objects.requireNonNull(reference, "Entity candidate reference must not be null.");
			Objects.requireNonNull(quality, "Entity candidate quality must not be null.");
			if ((score < 0) || (score > 10000) || (originalStartCodePoint < 0) || (originalEndCodePoint <= originalStartCodePoint))
			{
				throw new IllegalArgumentException("Invalid entity candidate.");
			}
		}
	}

	public record UnderstandingEvidence(String key, EvidenceQuality quality, int originalStartCodePoint, int originalEndCodePoint, String authorityKey)
	{
		public UnderstandingEvidence
		{
			key = requireKey(key, "Understanding evidence key");
			Objects.requireNonNull(quality, "Understanding evidence quality must not be null.");
			if ((originalStartCodePoint < -1) || (originalEndCodePoint < -1) || ((originalStartCodePoint >= 0) && (originalEndCodePoint <= originalStartCodePoint)) || (authorityKey == null) || authorityKey.isBlank() || (authorityKey.length() > 192))
			{
				throw new IllegalArgumentException("Invalid understanding evidence.");
			}
		}
	}

	public record IntentCandidate(String intentKey, int score, List<SlotValue> slots, String reasonKey, List<UnderstandingEvidence> evidence, int exactEvidence, int fuzzyEvidence)
	{
		public IntentCandidate
		{
			intentKey = requireKey(intentKey, "Intent candidate key");
			reasonKey = requireKey(reasonKey, "Intent candidate reason");
			if ((score < 0) || (score > 10000) || (exactEvidence < 0) || (fuzzyEvidence < 0))
			{
				throw new IllegalArgumentException("Invalid intent candidate score/evidence counts.");
			}
			slots = immutableSlots(slots, MAX_RESULT_SLOTS);
			evidence = immutableEvidence(evidence);
		}

		public String canonicalSlots()
		{
			final StringBuilder result = new StringBuilder();
			for (SlotValue slot : slots)
			{
				result.append(slot.type()).append('=').append(slot.canonicalValue()).append(';');
			}
			return result.toString();
		}
	}

	public record UnderstandingResult(UnderstandingStatus status, String normalizedHash, String packHash, String corpusHash, String knowledgeHash, String topologyHash, String partyRoleHash, String selectedIntent, int confidence, List<SlotValue> slots, List<IntentCandidate> alternatives, String reasonKey, List<UnderstandingEvidence> evidence)
	{
		public UnderstandingResult
		{
			Objects.requireNonNull(status, "Understanding status must not be null.");
			normalizedHash = requireHash(normalizedHash, "Normalized result hash");
			packHash = requireHash(packHash, "Semantic pack hash");
			corpusHash = requireHash(corpusHash, "Semantic corpus hash");
			knowledgeHash = requireHash(knowledgeHash, "Game Knowledge hash");
			topologyHash = requireHash(topologyHash, "Topology hash");
			partyRoleHash = requireHash(partyRoleHash, "Party-role hash");
			selectedIntent = requireKey(selectedIntent, "Selected intent");
			reasonKey = requireKey(reasonKey, "Understanding result reason");
			if ((confidence < 0) || (confidence > 10000))
			{
				throw new IllegalArgumentException("Understanding confidence must be 0..10000.");
			}
			slots = immutableSlots(slots, MAX_RESULT_SLOTS);
			alternatives = List.copyOf(Objects.requireNonNull(alternatives, "Intent alternatives must not be null."));
			if (alternatives.size() > MAX_ALTERNATIVES)
			{
				throw new IllegalArgumentException("Understanding result exceeds four alternatives.");
			}
			evidence = immutableEvidence(evidence);
		}

		public String canonicalEncoding()
		{
			final StringBuilder result = new StringBuilder(512);
			append(result, status.name());
			append(result, normalizedHash);
			append(result, packHash);
			append(result, corpusHash);
			append(result, knowledgeHash);
			append(result, topologyHash);
			append(result, partyRoleHash);
			append(result, selectedIntent);
			append(result, Integer.toString(confidence));
			append(result, reasonKey);
			for (SlotValue slot : slots)
			{
				append(result, "slot:" + slot.type() + ':' + slot.canonicalValue() + ':' + slot.originalStartCodePoint() + ':' + slot.originalEndCodePoint());
			}
			for (IntentCandidate alternative : alternatives)
			{
				append(result, "alt:" + alternative.intentKey() + ':' + alternative.score() + ':' + alternative.reasonKey() + ':' + alternative.canonicalSlots());
			}
			for (UnderstandingEvidence item : evidence)
			{
				append(result, "evidence:" + item.key() + ':' + item.quality() + ':' + item.originalStartCodePoint() + ':' + item.originalEndCodePoint() + ':' + item.authorityKey());
			}
			return result.toString();
		}
	}

	static String requireKey(String value, String label)
	{
		if ((value == null) || !KEY_PATTERN.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " must match ^[a-z][a-z0-9_.-]{0,63}$.");
		}
		return value;
	}

	static String requireHash(String value, String label)
	{
		if ((value == null) || !HASH_PATTERN.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " must be an uppercase SHA-256 value.");
		}
		return value;
	}

	private static List<PlayerReference> immutablePlayers(List<PlayerReference> values, int maximum, String label)
	{
		final List<PlayerReference> result = List.copyOf(values == null ? List.of() : values);
		if (result.size() > maximum)
		{
			throw new IllegalArgumentException("Context " + label + " exceeds " + maximum + ".");
		}
		final Set<PhantomDomainRef> identities = new HashSet<>();
		for (PlayerReference player : result)
		{
			Objects.requireNonNull(player, "Context player must not be null.");
			if (!identities.add(player.reference()))
			{
				throw new IllegalArgumentException("Context " + label + " contains a duplicate identity.");
			}
		}
		return result;
	}

	private static List<SlotValue> immutableSlots(List<SlotValue> values, int maximum)
	{
		final ArrayList<SlotValue> result = new ArrayList<>(values == null ? List.of() : values);
		if (result.size() > maximum)
		{
			throw new IllegalArgumentException("Semantic slot count exceeds " + maximum + ".");
		}
		result.forEach(value -> Objects.requireNonNull(value, "Semantic slot must not be null."));
		Collections.sort(result);
		return List.copyOf(result);
	}

	private static List<UnderstandingEvidence> immutableEvidence(List<UnderstandingEvidence> values)
	{
		final List<UnderstandingEvidence> result = List.copyOf(values == null ? List.of() : values);
		if (result.size() > MAX_EVIDENCE)
		{
			throw new IllegalArgumentException("Understanding evidence exceeds 16 entries.");
		}
		return result;
	}

	private static void append(StringBuilder builder, String value)
	{
		builder.append(value.length()).append(':').append(value);
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Immutable numeric social-state contracts. Durable records never retain
 * Player, Party, packet, session or display-name references.
 */
public final class PhantomSocialModel
{
	public static final String COMPONENT_TYPE = "social.state";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_TRAITS = 16;
	public static final int MAX_RELATIONSHIPS = 24;
	public static final int MAX_MEMORIES = 24;
	public static final int DIMENSION_COUNT = 11;
	public static final int AGREEMENT_COUNT = 5;
	public static final int MIN_VALUE = -10000;
	public static final int MAX_VALUE = 10000;
	public static final int MAX_COUNTER = 65535;
	private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9._-]{0,63}$");
	private static final Pattern HASH = Pattern.compile("^[0-9A-F]{64}$");

	private PhantomSocialModel()
	{
	}

	public enum SubjectKind
	{
		PHANTOM_PROFILE(1),
		CHARACTER_OBJECT(2);

		private final int _code;

		SubjectKind(int code)
		{
			_code = code;
		}

		public int code()
		{
			return _code;
		}

		public static SubjectKind fromCode(int code)
		{
			for (SubjectKind kind : values())
			{
				if (kind._code == code)
				{
					return kind;
				}
			}
			throw new IllegalArgumentException("Unknown social subject kind.");
		}
	}

	public record SubjectRef(SubjectKind kind, long id) implements Comparable<SubjectRef>
	{
		public SubjectRef
		{
			Objects.requireNonNull(kind, "Social subject kind must not be null.");
			if ((id <= 0) || ((kind == SubjectKind.CHARACTER_OBJECT) && (id > Integer.MAX_VALUE)))
			{
				throw new IllegalArgumentException("Social subject identity is outside bounds.");
			}
		}

		public static SubjectRef phantom(long profileId)
		{
			return new SubjectRef(SubjectKind.PHANTOM_PROFILE, profileId);
		}

		public static SubjectRef character(int objectId)
		{
			return new SubjectRef(SubjectKind.CHARACTER_OBJECT, objectId);
		}

		@Override
		public int compareTo(SubjectRef other)
		{
			final int kindOrder = Integer.compare(kind.code(), other.kind.code());
			return kindOrder != 0 ? kindOrder : Long.compare(id, other.id);
		}

		public String stableKey()
		{
			return kind.name() + ':' + id;
		}
	}

	public record SocialEvent(long ownerProfileId, String eventId, String eventKey, SubjectRef subject, long happenedEpochMinute, int magnitude, String evidenceHash)
	{
		public SocialEvent
		{
			if (ownerProfileId <= 0)
			{
				throw new IllegalArgumentException("Social event owner profile ID must be positive.");
			}
			eventId = requireHash(eventId, "Social event ID");
			eventKey = requireKey(eventKey, "Social event key");
			Objects.requireNonNull(subject, "Social event subject must not be null.");
			if (happenedEpochMinute < 0)
			{
				throw new IllegalArgumentException("Social event minute must not be negative.");
			}
			if ((magnitude < 1) || (magnitude > MAX_VALUE))
			{
				throw new IllegalArgumentException("Social event magnitude must be between 1 and 10000.");
			}
			evidenceHash = requireHash(evidenceHash, "Social event evidence hash");
		}
	}

	public record RelationshipRecord(SubjectRef subject, List<Integer> values, List<Integer> agreements, long lastDecayMinute, long lastInteractionMinute)
	{
		public RelationshipRecord
		{
			Objects.requireNonNull(subject, "Relationship subject must not be null.");
			values = boundedValues(values, DIMENSION_COUNT, MIN_VALUE, MAX_VALUE, "Relationship values");
			agreements = boundedValues(agreements, AGREEMENT_COUNT, 0, MAX_COUNTER, "Agreement counters");
			if ((lastDecayMinute < 0) || (lastInteractionMinute < 0))
			{
				throw new IllegalArgumentException("Relationship minutes must not be negative.");
			}
		}

		public static RelationshipRecord neutral(SubjectRef subject, long minute)
		{
			return new RelationshipRecord(subject, Collections.nCopies(DIMENSION_COUNT, 0), Collections.nCopies(AGREEMENT_COUNT, 0), minute, minute);
		}

		public RelationshipRecord withValues(List<Integer> replacementValues, List<Integer> replacementAgreements, long decayMinute, long interactionMinute)
		{
			return new RelationshipRecord(subject, replacementValues, replacementAgreements, decayMinute, interactionMinute);
		}

		public boolean neutral()
		{
			return values.stream().allMatch(value -> value == 0);
		}

		public boolean hasUnresolvedAgreement()
		{
			final long offered = agreements.get(0);
			final long accepted = agreements.get(1);
			final long resolved = (long) agreements.get(2) + agreements.get(3) + agreements.get(4);
			return (offered > resolved) || (accepted > ((long) agreements.get(2) + agreements.get(3)));
		}
	}

	public record MemoryRecord(String eventId, int eventCode, SubjectRef subject, long happenedMinute, long expiryMinute, int salience, int magnitude, String evidenceHash)
	{
		public MemoryRecord
		{
			eventId = requireHash(eventId, "Memory event ID");
			if ((eventCode < 1) || (eventCode > 65535))
			{
				throw new IllegalArgumentException("Memory event code is outside bounds.");
			}
			Objects.requireNonNull(subject, "Memory subject must not be null.");
			if ((happenedMinute < 0) || (expiryMinute <= happenedMinute))
			{
				throw new IllegalArgumentException("Memory lifetime is invalid.");
			}
			if ((salience < 0) || (salience > MAX_VALUE) || (magnitude < 1) || (magnitude > MAX_VALUE))
			{
				throw new IllegalArgumentException("Memory salience or magnitude is outside bounds.");
			}
			evidenceHash = requireHash(evidenceHash, "Memory evidence hash");
		}
	}

	public record SocialState(String authorityHash, long personalitySeed, long logicalMinute, NavigableMap<Integer, Integer> traits, List<RelationshipRecord> relationships, List<MemoryRecord> memories)
	{
		public SocialState
		{
			authorityHash = requireHash(authorityHash, "Social authority hash");
			if (personalitySeed <= 0)
			{
				throw new IllegalArgumentException("Personality seed must be positive.");
			}
			if (logicalMinute < 0)
			{
				throw new IllegalArgumentException("Social logical minute must not be negative.");
			}
			if ((traits == null) || traits.isEmpty() || (traits.size() > MAX_TRAITS))
			{
				throw new IllegalArgumentException("Social state must contain one to 16 traits.");
			}
			final TreeMap<Integer, Integer> sortedTraits = new TreeMap<>();
			for (Map.Entry<Integer, Integer> entry : traits.entrySet())
			{
				final int code = Objects.requireNonNull(entry.getKey(), "Trait code must not be null.");
				final int value = Objects.requireNonNull(entry.getValue(), "Trait value must not be null.");
				if ((code < 1) || (code > 65535) || (value < MIN_VALUE) || (value > MAX_VALUE) || (sortedTraits.put(code, value) != null))
				{
					throw new IllegalArgumentException("Trait code/value is invalid.");
				}
			}
			traits = Collections.unmodifiableNavigableMap(sortedTraits);
			relationships = sortedRelationships(relationships, logicalMinute);
			memories = sortedMemories(memories, logicalMinute);
		}

		public RelationshipRecord relationship(SubjectRef subject)
		{
			return relationships.stream().filter(value -> value.subject().equals(subject)).findFirst().orElse(null);
		}

		public boolean containsEvent(String eventId)
		{
			requireHash(eventId, "Social event ID");
			return memories.stream().anyMatch(memory -> memory.eventId().equals(eventId));
		}

		private static List<RelationshipRecord> sortedRelationships(List<RelationshipRecord> input, long logicalMinute)
		{
			if ((input == null) || (input.size() > MAX_RELATIONSHIPS))
			{
				throw new IllegalArgumentException("Relationship count exceeds 24.");
			}
			final List<RelationshipRecord> result = new ArrayList<>(input);
			result.sort(Comparator.comparing(RelationshipRecord::subject));
			SubjectRef previous = null;
			for (RelationshipRecord relationship : result)
			{
				Objects.requireNonNull(relationship, "Relationship record must not be null.");
				if ((previous != null) && (previous.compareTo(relationship.subject()) >= 0))
				{
					throw new IllegalArgumentException("Relationship subjects must be unique.");
				}
				if ((relationship.lastDecayMinute() > logicalMinute) || (relationship.lastInteractionMinute() > logicalMinute))
				{
					throw new IllegalArgumentException("Relationship minute exceeds the state boundary.");
				}
				previous = relationship.subject();
			}
			return List.copyOf(result);
		}

		private static List<MemoryRecord> sortedMemories(List<MemoryRecord> input, long logicalMinute)
		{
			if ((input == null) || (input.size() > MAX_MEMORIES))
			{
				throw new IllegalArgumentException("Memory count exceeds 24.");
			}
			final List<MemoryRecord> result = new ArrayList<>(input);
			result.sort(Comparator.comparing(MemoryRecord::eventId));
			String previous = null;
			for (MemoryRecord memory : result)
			{
				Objects.requireNonNull(memory, "Memory record must not be null.");
				if ((previous != null) && (previous.compareTo(memory.eventId()) >= 0))
				{
					throw new IllegalArgumentException("Memory event IDs must be unique.");
				}
				if (memory.happenedMinute() > logicalMinute)
				{
					throw new IllegalArgumentException("Memory happened minute exceeds the state boundary.");
				}
				previous = memory.eventId();
			}
			return List.copyOf(result);
		}
	}

	public record Contribution(String sourceKey, int inputValue, int weight, int deltaBasisPoints)
	{
		public Contribution
		{
			sourceKey = requireKey(sourceKey, "Modifier contribution source");
			if ((inputValue < MIN_VALUE) || (inputValue > MAX_VALUE) || (weight < -3000) || (weight > 3000) || (deltaBasisPoints < -3000) || (deltaBasisPoints > 3000))
			{
				throw new IllegalArgumentException("Modifier contribution is outside bounds.");
			}
		}
	}

	public record PersonalitySnapshot(long ownerProfileId, long seed, Map<String, Integer> traits, String authorityHash)
	{
		public PersonalitySnapshot
		{
			traits = Map.copyOf(traits);
			authorityHash = requireHash(authorityHash, "Personality authority hash");
		}
	}

	public record RelationshipSnapshot(SubjectRef subject, Map<String, Integer> relationship, Map<String, Integer> reputation, Map<String, Integer> agreements, long lastDecayMinute, long lastInteractionMinute)
	{
		public RelationshipSnapshot
		{
			relationship = Map.copyOf(relationship);
			reputation = Map.copyOf(reputation);
			agreements = Map.copyOf(agreements);
		}
	}

	public record MemorySnapshot(String eventId, String eventKey, SubjectRef subject, long happenedMinute, long expiryMinute, int salience, int magnitude, String evidenceHash)
	{
		public MemorySnapshot
		{
			eventId = requireHash(eventId, "Memory snapshot event ID");
			eventKey = requireKey(eventKey, "Memory snapshot event key");
			evidenceHash = requireHash(evidenceHash, "Memory snapshot evidence hash");
		}
	}

	public record SocialSnapshot(long ownerProfileId, PersonalitySnapshot personality, RelationshipSnapshot relationship, List<MemorySnapshot> memories, long effectiveMinute, String authorityHash)
	{
		public SocialSnapshot
		{
			if ((ownerProfileId <= 0) || (effectiveMinute < 0))
			{
				throw new IllegalArgumentException("Social snapshot identity or minute is invalid.");
			}
			Objects.requireNonNull(personality);
			Objects.requireNonNull(relationship);
			memories = List.copyOf(memories);
			authorityHash = requireHash(authorityHash, "Social snapshot authority hash");
		}
	}

	public record ModifierSnapshot(String modifierKey, int deltaBasisPoints, List<Contribution> traitContributions, List<Contribution> relationshipContributions, List<Contribution> agreementContributions, List<String> evidenceKeys, String authorityHash)
	{
		public ModifierSnapshot
		{
			modifierKey = requireKey(modifierKey, "Modifier key");
			if ((deltaBasisPoints < -3000) || (deltaBasisPoints > 3000))
			{
				throw new IllegalArgumentException("Modifier delta is outside bounds.");
			}
			traitContributions = List.copyOf(traitContributions);
			relationshipContributions = List.copyOf(relationshipContributions);
			agreementContributions = List.copyOf(agreementContributions);
			if (evidenceKeys.size() > 8)
			{
				throw new IllegalArgumentException("Modifier evidence exceeds eight keys.");
			}
			evidenceKeys = List.copyOf(evidenceKeys);
			authorityHash = requireHash(authorityHash, "Modifier authority hash");
		}
	}

	public static String sha256(String value)
	{
		try
		{
			return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception e)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", e);
		}
	}

	public static String requireHash(String value, String label)
	{
		if ((value == null) || !HASH.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " must be an uppercase SHA-256 hash.");
		}
		return value;
	}

	public static String requireKey(String value, String label)
	{
		if ((value == null) || !KEY.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " is invalid.");
		}
		return value;
	}

	public static int clamp(long value)
	{
		return (int) Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
	}

	private static List<Integer> boundedValues(List<Integer> values, int size, int minimum, int maximum, String label)
	{
		if ((values == null) || (values.size() != size))
		{
			throw new IllegalArgumentException(label + " has the wrong size.");
		}
		final List<Integer> result = new ArrayList<>(size);
		for (Integer value : values)
		{
			final int exact = Objects.requireNonNull(value, label + " contains null.");
			if ((exact < minimum) || (exact > maximum))
			{
				throw new IllegalArgumentException(label + " is outside bounds.");
			}
			result.add(exact);
		}
		return List.copyOf(result);
	}
}

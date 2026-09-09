/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation.humanized;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.FactKind;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Limits;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Match;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/**
 * Bounded structured personal conversation facts stored in the existing generic
 * profile-component envelope. This component never stores source chat text.
 */
public final class PhantomPersonalConversationStore
{
	public static final String COMPONENT_TYPE = "conversation.personal";
	public static final int SCHEMA_VERSION = 1;
	private static final int MAGIC = 0x50435031;
	private static final Pattern SUBJECT = Pattern.compile("^[a-z][a-z0-9.]{0,31}:[0-9]{1,20}$");
	private static final Pattern HASH = Pattern.compile("^[0-9a-f]{64}$");

	public record PersonalFact(FactKind kind, String value, long observedMinute, long expiryMinute, int salience, String evidenceHash)
	{
		public PersonalFact
		{
			Objects.requireNonNull(kind);
			if ((value == null) || value.isBlank() || (value.codePointCount(0, value.length()) > 64) || (value.getBytes(StandardCharsets.UTF_8).length > 192) || (observedMinute < 0) || (expiryMinute < 0) || ((expiryMinute > 0) && (expiryMinute <= observedMinute)) || (salience < 0) || (salience > 10000) || (evidenceHash == null) || !HASH.matcher(evidenceHash).matches())
			{
				throw new IllegalArgumentException("Personal conversation fact is invalid.");
			}
			value = value.strip();
		}

		private boolean expired(long nowMinute)
		{
			return (expiryMinute > 0) && (nowMinute >= expiryMinute);
		}
	}

	public record PersonalSubject(String key, long lastInteractionMinute, int generatedTurns, long generatedCooldownUntilMinute, String lastTopic, List<String> recentResponseHashes, List<PersonalFact> facts)
	{
		public PersonalSubject
		{
			if ((key == null) || !SUBJECT.matcher(key).matches() || (lastInteractionMinute < 0) || (generatedTurns < 0) || (generatedTurns > 4) || (generatedCooldownUntilMinute < 0) || (lastTopic == null) || (!lastTopic.isEmpty() && !lastTopic.matches("^[a-z][a-z0-9_.-]{0,63}$")))
			{
				throw new IllegalArgumentException("Personal conversation subject is invalid.");
			}
			recentResponseHashes = List.copyOf(recentResponseHashes);
			facts = facts.stream().sorted(Comparator.comparing(PersonalFact::kind)).toList();
			if ((recentResponseHashes.size() > 8) || (facts.size() > 12) || (new HashSet<>(recentResponseHashes).size() != recentResponseHashes.size()) || recentResponseHashes.stream().anyMatch(value -> !HASH.matcher(value).matches()))
			{
				throw new IllegalArgumentException("Personal conversation subject collections are invalid.");
			}
			final Set<FactKind> factKinds = new HashSet<>();
			if (facts.stream().anyMatch(value -> !factKinds.add(value.kind())))
			{
				throw new IllegalArgumentException("Personal conversation subject has duplicate fact kinds.");
			}
		}

		private static PersonalSubject empty(String key, long nowMinute)
		{
			return new PersonalSubject(key, nowMinute, 0, 0, "", List.of(), List.of());
		}
	}

	public record PersonalState(String authorityHash, long logicalMinute, List<PersonalSubject> subjects)
	{
		public PersonalState
		{
			if ((authorityHash == null) || !HASH.matcher(authorityHash).matches() || (logicalMinute < 0))
			{
				throw new IllegalArgumentException("Personal conversation state authority is invalid.");
			}
			subjects = subjects.stream().sorted(Comparator.comparing(PersonalSubject::key)).toList();
			final Set<String> keys = new HashSet<>();
			final int facts = subjects.stream().mapToInt(subject -> subject.facts().size()).sum();
			if ((subjects.size() > 16) || (facts > 48) || subjects.stream().anyMatch(subject -> !keys.add(subject.key())))
			{
				throw new IllegalArgumentException("Personal conversation state exceeds collection bounds.");
			}
		}

		public static PersonalState empty(String authorityHash, long nowMinute)
		{
			return new PersonalState(authorityHash, nowMinute, List.of());
		}
	}

	public record StoredState(long profileId, long rowVersion, PersonalState state)
	{
		public StoredState
		{
			if ((profileId <= 0) || (rowVersion < 0) || (state == null))
			{
				throw new IllegalArgumentException("Stored personal conversation state is invalid.");
			}
		}
	}

	public interface PersistencePort
	{
		Optional<StoredState> load(long profileId);

		StoredState save(long profileId, long expectedRowVersion, PersonalState state);
	}

	private final PersistencePort _persistence;

	public PhantomPersonalConversationStore(PersistencePort persistence)
	{
		_persistence = Objects.requireNonNull(persistence);
	}

	public static PhantomPersonalConversationStore production(PhantomProfileRepository profiles)
	{
		Objects.requireNonNull(profiles);
		return new PhantomPersonalConversationStore(new PersistencePort()
		{
			@Override
			public Optional<StoredState> load(long profileId)
			{
				return profiles.findComponent(profileId, COMPONENT_TYPE).map(component ->
				{
					if (component.componentSchemaVersion() != SCHEMA_VERSION)
					{
						throw new IllegalArgumentException("Unsupported conversation.personal schema version.");
					}
					return new StoredState(profileId, component.rowVersion(), decode(component.payload()));
				});
			}

			@Override
			public StoredState save(long profileId, long expectedRowVersion, PersonalState state)
			{
				final byte[] payload = encode(state);
				final PhantomProfileComponent component = expectedRowVersion < 0 ? profiles.insertComponent(profileId, COMPONENT_TYPE, SCHEMA_VERSION, payload) : profiles.updateComponent(profileId, COMPONENT_TYPE, expectedRowVersion, SCHEMA_VERSION, payload);
				return new StoredState(profileId, component.rowVersion(), state);
			}
		});
	}

	public Optional<StoredState> load(long profileId)
	{
		if (profileId <= 0)
		{
			throw new IllegalArgumentException("Personal conversation profile ID must be positive.");
		}
		return _persistence.load(profileId);
	}

	public StoredState save(long profileId, long expectedRowVersion, PersonalState state)
	{
		return _persistence.save(profileId, expectedRowVersion, state);
	}

	public static PersonalState prepare(PersonalState state, String authorityHash, long nowMinute)
	{
		Objects.requireNonNull(state);
		final List<PersonalSubject> subjects = new ArrayList<>();
		for (PersonalSubject subject : state.subjects())
		{
			final List<PersonalFact> liveFacts = subject.facts().stream().filter(fact -> !fact.expired(nowMinute)).toList();
			final List<String> recent = state.authorityHash().equals(authorityHash) ? subject.recentResponseHashes() : List.of();
			if (!liveFacts.isEmpty() || !recent.isEmpty() || (subject.generatedTurns() > 0))
			{
				subjects.add(new PersonalSubject(subject.key(), subject.lastInteractionMinute(), subject.generatedTurns(), subject.generatedCooldownUntilMinute(), subject.lastTopic(), recent, liveFacts));
			}
		}
		return new PersonalState(authorityHash, Math.max(state.logicalMinute(), nowMinute), subjects);
	}

	public static Optional<String> recall(PersonalState state, String subjectKey, FactKind kind, long nowMinute)
	{
		return subject(state, subjectKey).flatMap(subject -> subject.facts().stream().filter(fact -> (fact.kind() == kind) && !fact.expired(nowMinute)).max(Comparator.comparingInt(PersonalFact::salience).thenComparingLong(PersonalFact::observedMinute)).map(PersonalFact::value));
	}

	public static Set<String> recentResponses(PersonalState state, String subjectKey)
	{
		return subject(state, subjectKey).map(subject -> Set.copyOf(subject.recentResponseHashes())).orElse(Set.of());
	}

	public static boolean generatedEligible(PersonalState state, String subjectKey, long nowMinute, Limits limits)
	{
		final PersonalSubject subject = subject(state, subjectKey).orElse(null);
		if (subject == null)
		{
			return true;
		}
		return (nowMinute >= subject.generatedCooldownUntilMinute()) || (subject.generatedTurns() < limits.generatedTurnBudget());
	}

	public static PersonalState apply(PersonalState state, String subjectKey, Match match, String responseHash, Origin origin, long nowMinute, Limits limits)
	{
		Objects.requireNonNull(state);
		Objects.requireNonNull(match);
		Objects.requireNonNull(origin);
		final List<PersonalSubject> subjects = new ArrayList<>(state.subjects());
		PersonalSubject current = subject(state, subjectKey).orElse(PersonalSubject.empty(subjectKey, nowMinute));
		subjects.removeIf(subject -> subject.key().equals(subjectKey));
		final List<PersonalFact> facts = new ArrayList<>(current.facts().stream().filter(fact -> !fact.expired(nowMinute)).toList());
		if ((match.fact() != null) && !match.value().isBlank())
		{
			facts.removeIf(fact -> fact.kind() == match.fact());
			facts.add(new PersonalFact(match.fact(), match.value(), nowMinute, match.ttlMinutes() == 0 ? 0 : nowMinute + match.ttlMinutes(), match.salience(), match.normalizedHash()));
		}
		while (facts.size() > limits.factsPerSubject())
		{
			facts.remove(evictFact(facts));
		}
		final List<String> recent = new ArrayList<>(current.recentResponseHashes());
		if ((responseHash != null) && !responseHash.isEmpty())
		{
			recent.remove(responseHash);
			recent.add(responseHash);
		}
		while (recent.size() > limits.recentResponses())
		{
			recent.removeFirst();
		}
		final int generatedTurns;
		final long cooldown;
		if (origin == Origin.CLIENT_CHAT)
		{
			generatedTurns = 0;
			cooldown = 0;
		}
		else
		{
			final int base = nowMinute >= current.generatedCooldownUntilMinute() ? 0 : current.generatedTurns();
			generatedTurns = Math.min(4, base + 1);
			cooldown = nowMinute + limits.generatedCooldownMinutes();
		}
		subjects.add(new PersonalSubject(subjectKey, nowMinute, generatedTurns, cooldown, match.topic(), recent, facts));
		boundSubjects(subjects, limits);
		PersonalState result = new PersonalState(state.authorityHash(), Math.max(state.logicalMinute(), nowMinute), subjects);
		result = fitPayload(result);
		return result;
	}

	public static byte[] encode(PersonalState state)
	{
		try
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes))
			{
				output.writeInt(MAGIC);
				output.writeShort(SCHEMA_VERSION);
				writeHash(output, state.authorityHash());
				output.writeLong(state.logicalMinute());
				output.writeByte(state.subjects().size());
				for (PersonalSubject subject : state.subjects().stream().sorted(Comparator.comparing(PersonalSubject::key)).toList())
				{
					output.writeUTF(subject.key());
					output.writeLong(subject.lastInteractionMinute());
					output.writeByte(subject.generatedTurns());
					output.writeLong(subject.generatedCooldownUntilMinute());
					output.writeUTF(subject.lastTopic());
					output.writeByte(subject.recentResponseHashes().size());
					for (String hash : subject.recentResponseHashes())
					{
						writeHash(output, hash);
					}
					output.writeByte(subject.facts().size());
					for (PersonalFact fact : subject.facts().stream().sorted(Comparator.comparing(PersonalFact::kind)).toList())
					{
						output.writeByte(fact.kind().ordinal());
						output.writeUTF(fact.value());
						output.writeLong(fact.observedMinute());
						output.writeLong(fact.expiryMinute());
						output.writeShort(fact.salience());
						writeHash(output, fact.evidenceHash());
					}
				}
			}
			final byte[] payload = bytes.toByteArray();
			if (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
			{
				throw new IllegalArgumentException("conversation.personal payload exceeds 4096 bytes.");
			}
			return payload;
		}
		catch (IOException exception)
		{
			throw new IllegalStateException("Could not encode conversation.personal.", exception);
		}
	}

	public static PersonalState decode(byte[] payload)
	{
		Objects.requireNonNull(payload);
		if (payload.length > PhantomProfileComponent.MAX_PAYLOAD_BYTES)
		{
			throw new IllegalArgumentException("conversation.personal payload exceeds 4096 bytes.");
		}
		try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload)))
		{
			if ((input.readInt() != MAGIC) || (input.readUnsignedShort() != SCHEMA_VERSION))
			{
				throw new IllegalArgumentException("conversation.personal header is invalid.");
			}
			final String authority = readHash(input);
			final long logicalMinute = input.readLong();
			final int subjectCount = input.readUnsignedByte();
			if (subjectCount > 16)
			{
				throw new IllegalArgumentException("conversation.personal subject count exceeds bounds.");
			}
			final List<PersonalSubject> subjects = new ArrayList<>(subjectCount);
			for (int index = 0; index < subjectCount; index++)
			{
				final String key = input.readUTF();
				final long lastInteraction = input.readLong();
				final int generatedTurns = input.readUnsignedByte();
				final long cooldown = input.readLong();
				final String lastTopic = input.readUTF();
				final int recentCount = input.readUnsignedByte();
				if (recentCount > 8)
				{
					throw new IllegalArgumentException("conversation.personal recent hash count exceeds bounds.");
				}
				final List<String> recent = new ArrayList<>(recentCount);
				for (int recentIndex = 0; recentIndex < recentCount; recentIndex++)
				{
					recent.add(readHash(input));
				}
				final int factCount = input.readUnsignedByte();
				if (factCount > 12)
				{
					throw new IllegalArgumentException("conversation.personal fact count exceeds bounds.");
				}
				final List<PersonalFact> facts = new ArrayList<>(factCount);
				for (int factIndex = 0; factIndex < factCount; factIndex++)
				{
					final int kind = input.readUnsignedByte();
					if (kind >= FactKind.values().length)
					{
						throw new IllegalArgumentException("conversation.personal fact kind is invalid.");
					}
					facts.add(new PersonalFact(FactKind.values()[kind], input.readUTF(), input.readLong(), input.readLong(), input.readUnsignedShort(), readHash(input)));
				}
				subjects.add(new PersonalSubject(key, lastInteraction, generatedTurns, cooldown, lastTopic, recent, facts));
			}
			if (input.read() != -1)
			{
				throw new IllegalArgumentException("conversation.personal has trailing bytes.");
			}
			return new PersonalState(authority, logicalMinute, subjects);
		}
		catch (EOFException exception)
		{
			throw new IllegalArgumentException("conversation.personal is truncated.", exception);
		}
		catch (IOException exception)
		{
			throw new IllegalArgumentException("Could not decode conversation.personal.", exception);
		}
	}

	private static PersonalState fitPayload(PersonalState state)
	{
		PersonalState result = state;
		while (true)
		{
			try
			{
				encode(result);
				return result;
			}
			catch (IllegalArgumentException exception)
			{
				final List<PersonalSubject> subjects = new ArrayList<>(result.subjects());
				final PersonalFact victim = subjects.stream().flatMap(subject -> subject.facts().stream()).min(Comparator.comparingInt(PersonalFact::salience).thenComparingLong(PersonalFact::observedMinute).thenComparing(fact -> fact.kind().name()).thenComparing(PersonalFact::value)).orElse(null);
				if (victim != null)
				{
					for (int index = 0; index < subjects.size(); index++)
					{
						final PersonalSubject subject = subjects.get(index);
						if (subject.facts().contains(victim))
						{
							final List<PersonalFact> facts = new ArrayList<>(subject.facts());
							facts.remove(victim);
							subjects.set(index, new PersonalSubject(subject.key(), subject.lastInteractionMinute(), subject.generatedTurns(), subject.generatedCooldownUntilMinute(), subject.lastTopic(), subject.recentResponseHashes(), facts));
							break;
						}
					}
					result = new PersonalState(result.authorityHash(), result.logicalMinute(), subjects);
					continue;
				}
				final PersonalSubject responseVictim = subjects.stream().filter(subject -> !subject.recentResponseHashes().isEmpty()).min(Comparator.comparingLong(PersonalSubject::lastInteractionMinute).thenComparing(PersonalSubject::key)).orElse(null);
				if (responseVictim != null)
				{
					final int index = subjects.indexOf(responseVictim);
					final List<String> recent = new ArrayList<>(responseVictim.recentResponseHashes());
					recent.removeFirst();
					subjects.set(index, new PersonalSubject(responseVictim.key(), responseVictim.lastInteractionMinute(), responseVictim.generatedTurns(), responseVictim.generatedCooldownUntilMinute(), responseVictim.lastTopic(), recent, responseVictim.facts()));
					result = new PersonalState(result.authorityHash(), result.logicalMinute(), subjects);
					continue;
				}
				if (subjects.size() > 1)
				{
					subjects.remove(subjects.stream().min(Comparator.comparingLong(PersonalSubject::lastInteractionMinute).thenComparing(PersonalSubject::key)).orElseThrow());
					result = new PersonalState(result.authorityHash(), result.logicalMinute(), subjects);
					continue;
				}
				throw exception;
			}
		}
	}

	private static void boundSubjects(List<PersonalSubject> subjects, Limits limits)
	{
		while (subjects.size() > limits.subjects())
		{
			subjects.remove(subjects.stream().min(Comparator.comparingLong(PersonalSubject::lastInteractionMinute).thenComparing(PersonalSubject::key)).orElseThrow());
		}
		while (subjects.stream().mapToInt(subject -> subject.facts().size()).sum() > limits.facts())
		{
			PersonalSubject victimSubject = null;
			PersonalFact victimFact = null;
			for (PersonalSubject subject : subjects)
			{
				final PersonalFact candidate = subject.facts().stream().min(Comparator.comparingInt(PersonalFact::salience).thenComparingLong(PersonalFact::observedMinute).thenComparing(fact -> fact.kind().name()).thenComparing(PersonalFact::value)).orElse(null);
				if ((candidate != null) && ((victimFact == null) || compareFacts(candidate, victimFact) < 0))
				{
					victimSubject = subject;
					victimFact = candidate;
				}
			}
			final int index = subjects.indexOf(victimSubject);
			final List<PersonalFact> facts = new ArrayList<>(victimSubject.facts());
			facts.remove(victimFact);
			subjects.set(index, new PersonalSubject(victimSubject.key(), victimSubject.lastInteractionMinute(), victimSubject.generatedTurns(), victimSubject.generatedCooldownUntilMinute(), victimSubject.lastTopic(), victimSubject.recentResponseHashes(), facts));
		}
	}

	private static int compareFacts(PersonalFact left, PersonalFact right)
	{
		return Comparator.comparingInt(PersonalFact::salience).thenComparingLong(PersonalFact::observedMinute).thenComparing(fact -> fact.kind().name()).thenComparing(PersonalFact::value).compare(left, right);
	}

	private static PersonalFact evictFact(List<PersonalFact> facts)
	{
		return facts.stream().min(Comparator.comparingInt(PersonalFact::salience).thenComparingLong(PersonalFact::observedMinute).thenComparing(fact -> fact.kind().name()).thenComparing(PersonalFact::value)).orElseThrow();
	}

	private static Optional<PersonalSubject> subject(PersonalState state, String subjectKey)
	{
		if ((subjectKey == null) || !SUBJECT.matcher(subjectKey).matches())
		{
			throw new IllegalArgumentException("Personal conversation subject key is invalid.");
		}
		return state.subjects().stream().filter(subject -> subject.key().equals(subjectKey)).findFirst();
	}

	private static void writeHash(DataOutputStream output, String hash) throws IOException
	{
		output.write(java.util.HexFormat.of().parseHex(hash));
	}

	private static String readHash(DataInputStream input) throws IOException
	{
		return java.util.HexFormat.of().formatHex(input.readNBytes(32));
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation.humanized;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Strict, content-addressed authority for the additive Russian humanized layer.
 * All paths are fixed below the Phantom datapack root. Custom entries can add
 * stable IDs or explicitly replace an existing ID with {@code override="true"}.
 */
public final class PhantomHumanizedCatalog
{
	public enum Register
	{
		NEUTRAL,
		CASUAL
	}

	public enum ProfanityMode
	{
		NONE,
		MILD,
		CONTEXTUAL
	}

	public enum Variation
	{
		LOW,
		MEDIUM,
		HIGH
	}

	public enum RelationshipBand
	{
		UNKNOWN,
		NEUTRAL,
		FAMILIAR,
		TRUSTED,
		RIVAL,
		TENSE,
		HOSTILE
	}

	public enum FactKind
	{
		NAME,
		LOCATION,
		HOBBY,
		MUSIC,
		MOVIE,
		GAME,
		FOOD,
		PLAN,
		LIKE,
		DISLIKE,
		MOOD,
		ACHIEVEMENT,
		FAILURE
	}

	public enum Gender
	{
		MALE,
		FEMALE,
		UNKNOWN
	}

	public enum IdentityKind
	{
		NAME_QUERY,
		SUMMARY_QUERY,
		GENDER_QUERY,
		GENDER_ASSERTION,
		CLASS_QUERY,
		CLASS_ASSERTION
	}

	public record RuntimeIdentity(long profileId, int characterObjectId, String characterName, String displayName, Gender gender, int activeClassId, String activeClassName)
	{
		public RuntimeIdentity
		{
			characterName = safe(characterName, "");
			displayName = safe(displayName, characterName);
			gender = gender == null ? Gender.UNKNOWN : gender;
			activeClassName = safe(activeClassName, "");
			if ((profileId < 0) || (characterObjectId < 0) || (characterName.length() > 64) || (displayName.length() > 64) || (activeClassId < -1) || (activeClassName.length() > 64))
			{
				throw new IllegalArgumentException("Humanized runtime identity is invalid.");
			}
		}

		public static RuntimeIdentity unavailable(long profileId, String displayName)
		{
			return new RuntimeIdentity(Math.max(0, profileId), 0, safe(displayName, ""), safe(displayName, ""), Gender.UNKNOWN, -1, "");
		}

		public boolean available()
		{
			return (profileId > 0) && (characterObjectId > 0) && !characterName.isBlank() && !displayName.isBlank() && (gender != Gender.UNKNOWN) && (activeClassId >= 0) && !activeClassName.isBlank();
		}
	}

	public record ClassAliasResolution(Integer exactClassId, String roleId, String roleLabel, Set<Integer> classIds)
	{
		public ClassAliasResolution
		{
			classIds = Set.copyOf(classIds);
			roleLabel = roleId == null ? "" : boundedText(roleLabel, 64, "Humanized v2 role label");
			if (((exactClassId == null) == (roleId == null)) || classIds.isEmpty() || ((exactClassId != null) && ((classIds.size() != 1) || !classIds.contains(exactClassId))))
			{
				throw new IllegalArgumentException("Humanized class alias resolution is invalid.");
			}
		}

		public boolean exact()
		{
			return exactClassId != null;
		}
	}

	private record GenderDefinition(Gender gender, String label, Set<String> aliases)
	{
	}

	private record ClassDefinition(int id, String canonical, String label, Set<String> aliases)
	{
	}

	private record RoleDefinition(String id, String label, Set<Integer> classIds, Set<String> aliases)
	{
	}

	private record IdentityPattern(String id, IdentityKind kind, String phrase, int priority)
	{
		private IdentityPattern
		{
			id = key(id, "Humanized identity pattern ID");
			Objects.requireNonNull(kind);
			phrase = normalize(phrase);
			final int aliases = count(phrase, "{alias}");
			if (phrase.isEmpty() || (phrase.length() > 160) || (aliases > 1) || ((kind == IdentityKind.GENDER_ASSERTION) || (kind == IdentityKind.CLASS_ASSERTION)) != (aliases == 1) || (phrase.contains("{alias}") && !phrase.startsWith("{alias}") && !phrase.endsWith("{alias}")) || (priority < 0) || (priority > 1000))
			{
				throw new IllegalArgumentException("Humanized identity pattern is invalid: " + id);
			}
		}
	}

	public record Limits(int subjects, int facts, int factsPerSubject, int recentResponses, int valueCodePoints, int valueUtf8Bytes, int generatedTurnBudget, int generatedCooldownMinutes, int templatesPerAct)
	{
		public Limits
		{
			if ((subjects < 1) || (subjects > 16) || (facts < 1) || (facts > 48) || (factsPerSubject < 1) || (factsPerSubject > 12) || (recentResponses < 1) || (recentResponses > 8) || (valueCodePoints < 1) || (valueCodePoints > 64) || (valueUtf8Bytes < 1) || (valueUtf8Bytes > 192) || (generatedTurnBudget < 1) || (generatedTurnBudget > 4) || (generatedCooldownMinutes < 1) || (generatedCooldownMinutes > 60) || (templatesPerAct < 3) || (templatesPerAct > 16))
			{
				throw new IllegalArgumentException("Humanized limits are outside hard bounds.");
			}
		}
	}

	public record TopicPattern(String id, String topic, String act, String phrase, FactKind fact, FactKind recall, int salience, int ttlMinutes, int priority)
	{
		public TopicPattern
		{
			id = key(id, "Humanized pattern ID");
			topic = key(topic, "Humanized topic");
			act = key(act, "Humanized act");
			phrase = normalize(phrase);
			if (phrase.isEmpty() || (phrase.length() > 160) || (count(phrase, "{value}") > 1) || (phrase.contains("{value}") && !phrase.startsWith("{value}") && !phrase.endsWith("{value}")))
			{
				throw new IllegalArgumentException("Humanized pattern phrase is invalid: " + id);
			}
			if ((salience < 0) || (salience > 10000) || (ttlMinutes < 0) || (ttlMinutes > 525600) || (priority < 0) || (priority > 1000))
			{
				throw new IllegalArgumentException("Humanized pattern metadata is invalid: " + id);
			}
			if ((fact != null) && (recall != null))
			{
				throw new IllegalArgumentException("A humanized pattern cannot store and recall at once: " + id);
			}
		}
	}

	public record Match(String patternId, String topic, String act, FactKind fact, FactKind recall, String value, int salience, int ttlMinutes, String normalizedHash)
	{
	}

	public record Template(String id, String act, RelationshipBand band, Register register, ProfanityMode profanity, boolean mature, String text)
	{
		public Template
		{
			id = key(id, "Humanized template ID");
			act = key(act, "Humanized template act");
			Objects.requireNonNull(band);
			Objects.requireNonNull(register);
			Objects.requireNonNull(profanity);
			text = boundedText(text, 240, "Humanized template text");
		}
	}

	public record Profanity(String id, ProfanityMode level, Set<String> acts, String text)
	{
		public Profanity
		{
			id = key(id, "Humanized profanity ID");
			if ((level == null) || (level == ProfanityMode.NONE))
			{
				throw new IllegalArgumentException("Humanized profanity level must be MILD or CONTEXTUAL.");
			}
			acts = Set.copyOf(acts);
			if (acts.isEmpty() || (acts.size() > 8))
			{
				throw new IllegalArgumentException("Humanized profanity context is invalid: " + id);
			}
			acts.forEach(value -> key(value, "Humanized profanity act"));
			text = boundedText(text, 80, "Humanized profanity text");
		}
	}

	public record PersonaInterest(String id, String label)
	{
		public PersonaInterest
		{
			id = key(id, "Persona interest ID");
			label = boundedText(label, 64, "Persona interest label");
		}
	}

	public record Selection(String templateId, String text, String responseHash, boolean profanityUsed, boolean matureUsed)
	{
	}

	private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_.-]{0,63}$");
	private static final Set<String> REQUIRED_TOPICS = Set.of("greeting", "farewell", "acquaintance", "mood", "smalltalk", "rest", "food", "music", "movies", "games", "hobbies", "plans", "likes", "achievement", "failure", "humor", "teasing", "sarcasm", "surprise", "disagreement", "irritation", "apology", "reconcile", "relationship", "pivot");
	private static final int MAX_CORE_BYTES = 262144;
	private static final int MAX_CUSTOM_BYTES = 65536;
	private static final int MAX_CORPUS_BYTES = 262144;
	private static final int V2_MAX_CLASSES = 128;
	private static final int V2_MAX_EXACT_CLASS_ALIASES = 384;
	private static final int V2_MAX_ROLES = 16;
	private static final int V2_MAX_ROLE_ALIASES = 64;
	private static final int V2_MAX_GENDER_ALIASES = 16;
	private static final int V2_MAX_IDENTITY_PATTERNS = 32;
	private static final int V2_MAX_ADDITIONAL_PATTERNS = 128;
	private static final int V2_MAX_ADDITIONAL_TEMPLATES = 256;
	private final Limits _limits;
	private final Map<String, String> _aliases;
	private final List<TopicPattern> _patterns;
	private final List<Template> _templates;
	private final List<Profanity> _profanity;
	private final List<PersonaInterest> _interests;
	private final Set<String> _blocked;
	private final String _coreHash;
	private final String _customHash;
	private final String _combinedHash;
	private final int _corpusCases;
	private final String _familyId;
	private final int _familyVersion;
	private final Map<Gender, GenderDefinition> _genders;
	private final Map<Integer, ClassDefinition> _classes;
	private final Map<String, ClassAliasResolution> _classAliases;
	private final Map<String, Gender> _genderAliases;
	private final List<IdentityPattern> _identityPatterns;

	private PhantomHumanizedCatalog(Limits limits, Map<String, String> aliases, List<TopicPattern> patterns, List<Template> templates, List<Profanity> profanity, List<PersonaInterest> interests, Set<String> blocked, String coreHash, String customHash, String combinedHash, int corpusCases, String familyId, int familyVersion, Map<Gender, GenderDefinition> genders, Map<Integer, ClassDefinition> classes, Map<String, ClassAliasResolution> classAliases, Map<String, Gender> genderAliases, List<IdentityPattern> identityPatterns)
	{
		_limits = limits;
		_aliases = Map.copyOf(aliases);
		_patterns = patterns.stream().sorted(Comparator.comparingInt(TopicPattern::priority).reversed().thenComparing(Comparator.comparingInt((TopicPattern value) -> value.phrase().length()).reversed()).thenComparing(TopicPattern::id)).toList();
		_templates = templates.stream().sorted(Comparator.comparing(Template::id)).toList();
		_profanity = profanity.stream().sorted(Comparator.comparing(Profanity::id)).toList();
		_interests = interests.stream().sorted(Comparator.comparing(PersonaInterest::id)).toList();
		_blocked = Set.copyOf(blocked);
		_coreHash = hash(coreHash);
		_customHash = hash(customHash);
		_combinedHash = hash(combinedHash);
		_corpusCases = corpusCases;
		_familyId = key(familyId, "Humanized family ID");
		_familyVersion = familyVersion;
		_genders = Map.copyOf(genders);
		_classes = Map.copyOf(classes);
		_classAliases = Map.copyOf(classAliases);
		_genderAliases = Map.copyOf(genderAliases);
		_identityPatterns = identityPatterns.stream().sorted(Comparator.comparingInt(IdentityPattern::priority).reversed().thenComparing(Comparator.comparingInt((IdentityPattern value) -> value.phrase().length()).reversed()).thenComparing(IdentityPattern::id)).toList();
	}

	public static PhantomHumanizedCatalog load(Path phantomDataRoot, boolean customEnabled)
	{
		return load(phantomDataRoot, customEnabled, false);
	}

	public static PhantomHumanizedCatalog loadV2(Path phantomDataRoot, boolean customEnabled)
	{
		return load(phantomDataRoot, customEnabled, true);
	}

	private static PhantomHumanizedCatalog load(Path phantomDataRoot, boolean customEnabled, boolean version2)
	{
		Objects.requireNonNull(phantomDataRoot);
		final Path root = phantomDataRoot.toAbsolutePath().normalize();
		final Path semantic = root.resolve("semantic/humanized/high-five-ru-humanized-semantic-v1.xml");
		final Path conversation = root.resolve("conversation/humanized/high-five-ru-humanized-conversation-v1.xml");
		final Path persona = root.resolve("conversation/humanized/high-five-ru-persona-v1.xml");
		final Path corpus = root.resolve("semantic/humanized/high-five-ru-humanized-corpus-v1.tsv");
		final Path semanticV2 = root.resolve("semantic/humanized/high-five-ru-humanized-semantic-v2.xml");
		final Path conversationV2 = root.resolve("conversation/humanized/high-five-ru-humanized-conversation-v2.xml");
		final List<Path> coreFiles = version2 ? List.of(semantic, conversation, persona, corpus, semanticV2, conversationV2) : List.of(semantic, conversation, persona, corpus);
		final List<Path> customFiles = List.of(
			root.resolve("semantic/custom/my-ru-aliases.xml"),
			root.resolve("semantic/custom/my-slang.xml"),
			root.resolve("semantic/custom/my-social-topics.xml"),
			root.resolve("conversation/custom/my-phrases.xml"),
			root.resolve("conversation/custom/my-profanity.xml"),
			root.resolve("conversation/custom/my-mature-dialogue.xml"));
		final Loader loader = new Loader();
		loader.readSemantic(semantic, false);
		loader.readConversation(conversation, false, false);
		loader.readPersona(persona);
		if (version2)
		{
			loader.readSemanticV2(semanticV2);
			loader.readConversationV2(conversationV2);
		}
		if (customEnabled)
		{
			loader.readAliases(customFiles.get(0), "aliases", true);
			loader.readAliases(customFiles.get(1), "slang", true);
			loader.readPatterns(customFiles.get(2), "socialTopics", true);
			loader.readTemplates(customFiles.get(3), "phrases", true, false);
			loader.readProfanity(customFiles.get(4), "profanity", true);
			loader.readTemplates(customFiles.get(5), "matureDialogue", true, true);
		}
		loader.validateCoverage();
		if (version2)
		{
			loader.validateV2Coverage();
		}
		final String coreHash = contentHash(coreFiles, MAX_CORE_BYTES, MAX_CORPUS_BYTES);
		final String customHash = customEnabled ? contentHash(customFiles, MAX_CUSTOM_BYTES, MAX_CUSTOM_BYTES) : sha256("custom.disabled");
		final int familyVersion = version2 ? 2 : 1;
		final String familyId = "high-five-ru-humanized-v" + familyVersion;
		final String combinedHash = sha256(coreHash + '|' + customHash + "|v" + familyVersion);
		final PhantomHumanizedCatalog provisional = new PhantomHumanizedCatalog(loader._limits, loader._aliases, new ArrayList<>(loader._patterns.values()), new ArrayList<>(loader._templates.values()), new ArrayList<>(loader._profanity.values()), new ArrayList<>(loader._interests.values()), loader._blocked, coreHash, customHash, combinedHash, 0, familyId, familyVersion, loader._genders, loader._classes, loader._classAliases, loader._genderAliases, new ArrayList<>(loader._identityPatterns.values()));
		final int corpusCases = validateCorpus(corpus, provisional);
		return new PhantomHumanizedCatalog(loader._limits, loader._aliases, new ArrayList<>(loader._patterns.values()), new ArrayList<>(loader._templates.values()), new ArrayList<>(loader._profanity.values()), new ArrayList<>(loader._interests.values()), loader._blocked, coreHash, customHash, combinedHash, corpusCases, familyId, familyVersion, loader._genders, loader._classes, loader._classAliases, loader._genderAliases, new ArrayList<>(loader._identityPatterns.values()));
	}

	public Optional<Match> understand(String text)
	{
		String normalized = normalize(text);
		if (normalized.isEmpty() || (normalized.codePointCount(0, normalized.length()) > 256))
		{
			return Optional.empty();
		}
		normalized = applyAliases(normalized);
		final String padded = ' ' + normalized + ' ';
		if (_blocked.stream().anyMatch(value -> padded.contains(' ' + value + ' ')))
		{
			return Optional.empty();
		}
		for (TopicPattern pattern : _patterns)
		{
			final String value = match(pattern.phrase(), normalized);
			if (value != null)
			{
				if (!value.isEmpty() && !validValue(value))
				{
					return Optional.empty();
				}
				return Optional.of(new Match(pattern.id(), pattern.topic(), pattern.act(), pattern.fact(), pattern.recall(), value, pattern.salience(), pattern.ttlMinutes(), sha256(normalized)));
			}
		}
		return Optional.empty();
	}

	public Optional<Match> understandIdentity(String text, RuntimeIdentity identity)
	{
		if ((_familyVersion < 2) || (identity == null) || !identity.available())
		{
			return Optional.empty();
		}
		String normalized = normalize(text);
		if (normalized.isEmpty() || (normalized.codePointCount(0, normalized.length()) > 256))
		{
			return Optional.empty();
		}
		normalized = applyAliases(normalized);
		final String padded = ' ' + normalized + ' ';
		if (_blocked.stream().anyMatch(value -> padded.contains(' ' + value + ' ')))
		{
			return Optional.empty();
		}
		final ClassDefinition activeClass = _classes.get(identity.activeClassId());
		final GenderDefinition gender = _genders.get(identity.gender());
		if ((activeClass == null) || !activeClass.canonical().equals(identity.activeClassName()) || (gender == null))
		{
			return Optional.empty();
		}
		for (IdentityPattern pattern : _identityPatterns)
		{
			final String alias = identityMatch(pattern.phrase(), normalized);
			if (alias == null)
			{
				continue;
			}
			String act;
			String value;
			switch (pattern.kind())
			{
				case NAME_QUERY ->
				{
					act = "identity.name.reply";
					value = activeClass.label();
				}
				case SUMMARY_QUERY ->
				{
					act = "identity.summary.reply";
					value = activeClass.label();
				}
				case GENDER_QUERY ->
				{
					act = "identity.gender.reply";
					value = gender.label();
				}
				case GENDER_ASSERTION ->
				{
					final Gender asserted = _genderAliases.get(alias);
					if (asserted == null)
					{
						continue;
					}
					act = asserted == identity.gender() ? "identity.gender.confirm" : "identity.gender.deny";
					value = gender.label();
				}
				case CLASS_QUERY ->
				{
					act = "identity.class.reply";
					value = activeClass.label();
				}
				case CLASS_ASSERTION ->
				{
					final ClassAliasResolution resolution = _classAliases.get(alias);
					if (resolution == null)
					{
						continue;
					}
					if (resolution.exact())
					{
						act = resolution.exactClassId() == identity.activeClassId() ? "identity.class.confirm" : "identity.class.deny";
						value = activeClass.label();
					}
					else
					{
						act = resolution.classIds().contains(identity.activeClassId()) ? "identity.role.confirm" : "identity.role.deny";
						value = act.endsWith("confirm") ? resolution.roleLabel() : activeClass.label();
					}
				}
				default -> throw new IllegalStateException("Unhandled humanized identity pattern.");
			}
			return Optional.of(new Match(pattern.id(), "identity", act, null, null, value, 0, 0, sha256(normalized)));
		}
		return Optional.empty();
	}

	public Optional<ClassAliasResolution> classAlias(String alias)
	{
		return Optional.ofNullable(_classAliases.get(normalize(alias)));
	}

	public Selection select(String act, RelationshipBand band, Register register, ProfanityMode profanityMode, Variation variation, boolean matureEnabled, boolean privateChannel, String ownerName, String value, String memory, String interest, long selector, Set<String> recentHashes)
	{
		key(act, "Humanized response act");
		Objects.requireNonNull(band);
		Objects.requireNonNull(register);
		Objects.requireNonNull(profanityMode);
		Objects.requireNonNull(variation);
		final List<Template> eligible = _templates.stream()
			.filter(template -> template.act().equals(act))
			.filter(template -> (template.band() == RelationshipBand.UNKNOWN) || (template.band() == band))
			.filter(template -> template.register().ordinal() <= register.ordinal())
			.filter(template -> template.profanity() == ProfanityMode.NONE)
			.filter(template -> !template.mature() || (matureEnabled && privateChannel && (band == RelationshipBand.TRUSTED)))
			.toList();
		if (eligible.isEmpty())
		{
			throw new IllegalArgumentException("No humanized template is eligible for act " + act + '.');
		}
		final int available = variation == Variation.LOW ? Math.min(1, eligible.size()) : variation == Variation.MEDIUM ? Math.min(2, eligible.size()) : eligible.size();
		final List<Profanity> expressions;
		if ((profanityMode != ProfanityMode.NONE) && profanityContext(act, band) && (Math.floorMod(selector >>> 7, 4) == 0))
		{
			expressions = _profanity.stream().filter(entry -> entry.acts().contains(act)).filter(entry -> (entry.level() == ProfanityMode.MILD) || (profanityMode == ProfanityMode.CONTEXTUAL)).toList();
		}
		else
		{
			expressions = List.of();
		}
		final String profanitySuffix = expressions.isEmpty() ? "" : " " + expressions.get(Math.floorMod(selector >>> 11, expressions.size())).text();
		final int start = Math.floorMod(selector, available);
		Template selected = eligible.get(start);
		String rendered = render(selected.text(), ownerName, value, memory, interest) + profanitySuffix;
		String responseHash = sha256(rendered);
		for (int offset = 1; recentHashes.contains(responseHash) && (offset < available); offset++)
		{
			selected = eligible.get((start + offset) % available);
			rendered = render(selected.text(), ownerName, value, memory, interest) + profanitySuffix;
			responseHash = sha256(rendered);
		}
		return new Selection(selected.id(), rendered, responseHash, !expressions.isEmpty(), selected.mature());
	}

	public PersonaInterest interest(long selector)
	{
		return _interests.get(Math.floorMod(selector, _interests.size()));
	}

	public Limits limits()
	{
		return _limits;
	}

	public String coreHash()
	{
		return _coreHash;
	}

	public String customHash()
	{
		return _customHash;
	}

	public String combinedHash()
	{
		return _combinedHash;
	}

	public int corpusCases()
	{
		return _corpusCases;
	}

	public String familyId()
	{
		return _familyId;
	}

	public int familyVersion()
	{
		return _familyVersion;
	}

	public int classCount()
	{
		return _classes.size();
	}

	public int classAliasCount()
	{
		return _classAliases.size();
	}

	public int identityPatternCount()
	{
		return _identityPatterns.size();
	}

	public int patternCount()
	{
		return _patterns.size();
	}

	public int templateCount()
	{
		return _templates.size();
	}

	public int topicCount()
	{
		return (int) _patterns.stream().map(TopicPattern::topic).distinct().count();
	}

	public int actCount()
	{
		return (int) _patterns.stream().map(TopicPattern::act).distinct().count();
	}

	public int aliasCount()
	{
		return _aliases.size();
	}

	public int profanityCount()
	{
		return _profanity.size();
	}

	public int matureTemplateCount()
	{
		return (int) _templates.stream().filter(Template::mature).count();
	}

	private String applyAliases(String normalized)
	{
		final String[] words = normalized.split(" ");
		for (int index = 0; index < words.length; index++)
		{
			words[index] = _aliases.getOrDefault(words[index], words[index]);
		}
		return String.join(" ", words);
	}

	private boolean validValue(String value)
	{
		return !value.contains("{value}") && !value.isBlank() && (value.codePointCount(0, value.length()) <= _limits.valueCodePoints()) && (value.getBytes(StandardCharsets.UTF_8).length <= _limits.valueUtf8Bytes());
	}

	private static String match(String pattern, String text)
	{
		final int marker = pattern.indexOf("{value}");
		if (marker < 0)
		{
			return pattern.equals(text) ? "" : null;
		}
		final String prefix = pattern.substring(0, marker).strip();
		final String suffix = pattern.substring(marker + 7).strip();
		if (!prefix.isEmpty())
		{
			if (!text.startsWith(prefix + ' '))
			{
				return null;
			}
			return text.substring(prefix.length()).strip();
		}
		if (!suffix.isEmpty() && text.endsWith(' ' + suffix))
		{
			return text.substring(0, text.length() - suffix.length()).strip();
		}
		return null;
	}

	private static String identityMatch(String pattern, String text)
	{
		final int marker = pattern.indexOf("{alias}");
		if (marker < 0)
		{
			return pattern.equals(text) ? "" : null;
		}
		final String prefix = pattern.substring(0, marker).strip();
		final String suffix = pattern.substring(marker + 7).strip();
		if (!prefix.isEmpty() && text.startsWith(prefix + ' '))
		{
			final String value = text.substring(prefix.length()).strip();
			return value.isEmpty() ? null : value;
		}
		if (!suffix.isEmpty() && text.endsWith(' ' + suffix))
		{
			final String value = text.substring(0, text.length() - suffix.length()).strip();
			return value.isEmpty() ? null : value;
		}
		return null;
	}

	private static boolean profanityContext(String act, RelationshipBand band)
	{
		return (band != RelationshipBand.UNKNOWN) && Set.of("irritation.reply", "failure.empathy", "disagreement.reply", "achievement.congratulate", "humor.reply", "surprise.reply", "sarcasm.reply").contains(act);
	}

	private static String render(String template, String ownerName, String value, String memory, String interest)
	{
		return template
			.replace("{name}", safe(ownerName, "друг"))
			.replace("{value}", safe(value, "это"))
			.replace("{memory}", safe(memory, "не успел запомнить"))
			.replace("{interest}", safe(interest, "хорошие истории"));
	}

	private static String safe(String value, String fallback)
	{
		return (value == null) || value.isBlank() ? fallback : value;
	}

	private static int validateCorpus(Path path, PhantomHumanizedCatalog catalog)
	{
		final String source = strictUtf8(read(path, MAX_CORPUS_BYTES));
		int cases = 0;
		final Set<String> ids = new HashSet<>();
		for (String line : source.split("\\R", -1))
		{
			if (line.isBlank() || line.startsWith("#"))
			{
				continue;
			}
			final String[] fields = line.split("\\t", -1);
			if ((fields.length != 3) || !KEY.matcher(fields[0]).matches() || !ids.add(fields[0]))
			{
				throw new IllegalArgumentException("Humanized corpus row is invalid: " + line);
			}
			final Optional<Match> result = catalog.understand(fields[2]);
			if (fields[1].equals("REJECTED"))
			{
				if (result.isPresent())
				{
					throw new IllegalArgumentException("Humanized negative corpus row was accepted: " + fields[0]);
				}
			}
			else if (result.isEmpty() || !result.get().topic().equals(fields[1]))
			{
				throw new IllegalArgumentException("Humanized corpus expectation failed: " + fields[0]);
			}
			cases++;
		}
		if (cases < 80)
		{
			throw new IllegalArgumentException("Humanized corpus must contain at least 80 cases.");
		}
		return cases;
	}

	private static final class Loader
	{
		private final Map<String, String> _aliases = new TreeMap<>();
		private final Map<String, TopicPattern> _patterns = new LinkedHashMap<>();
		private final Map<String, Template> _templates = new LinkedHashMap<>();
		private final Map<String, Profanity> _profanity = new LinkedHashMap<>();
		private final Map<String, PersonaInterest> _interests = new LinkedHashMap<>();
		private final Set<String> _blocked = new HashSet<>();
		private final Map<Gender, GenderDefinition> _genders = new java.util.EnumMap<>(Gender.class);
		private final Map<Integer, ClassDefinition> _classes = new LinkedHashMap<>();
		private final Map<String, RoleDefinition> _roles = new LinkedHashMap<>();
		private final Map<String, ClassAliasResolution> _classAliases = new TreeMap<>();
		private final Map<String, Gender> _genderAliases = new TreeMap<>();
		private final Map<String, IdentityPattern> _identityPatterns = new LinkedHashMap<>();
		private Limits _limits;
		private boolean _v2BoundsRead;
		private int _v1PatternCount;
		private int _v1TemplateCount;

		private void readSemantic(Path path, boolean custom)
		{
			final Element root = root(path, "humanizedSemanticPack", MAX_CORE_BYTES);
			requireAttributes(root, Set.of("id", "version"));
			key(attribute(root, "id"), "Humanized semantic pack ID");
			requireVersion(root);
			for (Element child : children(root))
			{
				switch (child.getTagName())
				{
					case "limits" ->
					{
						if (_limits != null)
						{
							throw new IllegalArgumentException("Humanized limits are duplicated.");
						}
						requireAttributes(child, Set.of("subjects", "facts", "factsPerSubject", "recentResponses", "valueCodePoints", "valueUtf8Bytes", "generatedTurnBudget", "generatedCooldownMinutes", "templatesPerAct"));
						_limits = new Limits(integer(child, "subjects"), integer(child, "facts"), integer(child, "factsPerSubject"), integer(child, "recentResponses"), integer(child, "valueCodePoints"), integer(child, "valueUtf8Bytes"), integer(child, "generatedTurnBudget"), integer(child, "generatedCooldownMinutes"), integer(child, "templatesPerAct"));
					}
					case "blocked" ->
					{
						requireAttributes(child, Set.of());
						for (Element phrase : children(child))
						{
							requireTag(phrase, "phrase");
							requireAttributes(phrase, Set.of("text"));
							final String value = normalize(attribute(phrase, "text"));
							if (value.isEmpty() || (value.length() > 64) || !_blocked.add(value))
							{
								throw new IllegalArgumentException("Humanized blocked phrase is invalid or duplicated.");
							}
						}
					}
					case "aliases" -> readAliasElements(child, custom);
					case "patterns" -> readPatternElements(child, custom);
					default -> throw new IllegalArgumentException("Unknown humanized semantic element: " + child.getTagName());
				}
			}
			if ((_limits == null) || _blocked.isEmpty())
			{
				throw new IllegalArgumentException("Humanized semantic pack is incomplete.");
			}
		}

		private void readSemanticV2(Path path)
		{
			_v1PatternCount = _patterns.size();
			final Element root = root(path, "humanizedSemanticPack", MAX_CORE_BYTES);
			requireAttributes(root, Set.of("id", "version"));
			if (!attribute(root, "id").equals("high-five-ru-humanized-semantic-v2"))
			{
				throw new IllegalArgumentException("Humanized v2 semantic pack ID is invalid.");
			}
			requireVersion(root, 2);
			for (Element child : children(root))
			{
				switch (child.getTagName())
				{
					case "bounds" -> readV2Bounds(child);
					case "genders" -> readV2Genders(child);
					case "roles" -> readV2Roles(child);
					case "classes" -> readV2Classes(child);
					case "identityPatterns" -> readV2IdentityPatterns(child);
					case "patterns" -> readPatternElements(child, false);
					default -> throw new IllegalArgumentException("Unknown humanized v2 semantic element: " + child.getTagName());
				}
			}
			if (!_v2BoundsRead || _genders.isEmpty() || _roles.isEmpty() || _classes.isEmpty() || _identityPatterns.isEmpty())
			{
				throw new IllegalArgumentException("Humanized v2 semantic pack is incomplete.");
			}
			if ((_patterns.size() - _v1PatternCount) > V2_MAX_ADDITIONAL_PATTERNS)
			{
				throw new IllegalArgumentException("Humanized v2 additional pattern bound was exceeded.");
			}
			indexV2Aliases();
		}

		private void readConversationV2(Path path)
		{
			_v1TemplateCount = _templates.size();
			final Element root = root(path, "humanizedConversationPack", MAX_CORE_BYTES);
			requireAttributes(root, Set.of("id", "version"));
			if (!attribute(root, "id").equals("high-five-ru-humanized-conversation-v2"))
			{
				throw new IllegalArgumentException("Humanized v2 conversation pack ID is invalid.");
			}
			requireVersion(root, 2);
			for (Element child : children(root))
			{
				requireTag(child, "templates");
				readTemplateElements(child, false, false);
			}
			if ((_templates.size() - _v1TemplateCount) > V2_MAX_ADDITIONAL_TEMPLATES)
			{
				throw new IllegalArgumentException("Humanized v2 additional template bound was exceeded.");
			}
		}

		private void readV2Bounds(Element element)
		{
			if (_v2BoundsRead)
			{
				throw new IllegalArgumentException("Humanized v2 bounds are duplicated.");
			}
			requireAttributes(element, Set.of("classes", "exactClassAliases", "roles", "roleAliases", "genderAliases", "identityPatterns", "additionalPatterns", "additionalTemplates"));
			if ((integer(element, "classes") != V2_MAX_CLASSES) || (integer(element, "exactClassAliases") != V2_MAX_EXACT_CLASS_ALIASES) || (integer(element, "roles") != V2_MAX_ROLES) || (integer(element, "roleAliases") != V2_MAX_ROLE_ALIASES) || (integer(element, "genderAliases") != V2_MAX_GENDER_ALIASES) || (integer(element, "identityPatterns") != V2_MAX_IDENTITY_PATTERNS) || (integer(element, "additionalPatterns") != V2_MAX_ADDITIONAL_PATTERNS) || (integer(element, "additionalTemplates") != V2_MAX_ADDITIONAL_TEMPLATES))
			{
				throw new IllegalArgumentException("Humanized v2 declared bounds do not match runtime hard bounds.");
			}
			_v2BoundsRead = true;
		}

		private void readV2Genders(Element parent)
		{
			requireAttributes(parent, Set.of());
			for (Element item : children(parent))
			{
				requireTag(item, "gender");
				requireAttributes(item, Set.of("value", "label", "aliases"));
				final Gender gender = enumValue(item, "value", Gender.class);
				if (gender == Gender.UNKNOWN)
				{
					throw new IllegalArgumentException("Humanized v2 gender value must be canonical MALE or FEMALE.");
				}
				final GenderDefinition definition = new GenderDefinition(gender, boundedText(attribute(item, "label"), 64, "Humanized v2 gender label"), aliases(attribute(item, "aliases"), "Humanized v2 gender alias"));
				if (_genders.putIfAbsent(gender, definition) != null)
				{
					throw new IllegalArgumentException("Humanized v2 gender is duplicated: " + gender);
				}
			}
		}

		private void readV2Roles(Element parent)
		{
			requireAttributes(parent, Set.of());
			for (Element item : children(parent))
			{
				requireTag(item, "role");
				requireAttributes(item, Set.of("id", "label", "classes", "aliases"));
				final String id = key(attribute(item, "id"), "Humanized v2 role ID");
				final RoleDefinition definition = new RoleDefinition(id, boundedText(attribute(item, "label"), 64, "Humanized v2 role label"), integerSet(attribute(item, "classes"), "Humanized v2 role classes"), aliases(attribute(item, "aliases"), "Humanized v2 role alias"));
				if (_roles.putIfAbsent(id, definition) != null)
				{
					throw new IllegalArgumentException("Humanized v2 role is duplicated: " + id);
				}
			}
		}

		private void readV2Classes(Element parent)
		{
			requireAttributes(parent, Set.of());
			for (Element item : children(parent))
			{
				requireTag(item, "class");
				requireAttributes(item, Set.of("id", "canonical", "label", "aliases"));
				final int id = integer(item, "id");
				final String canonical = attribute(item, "canonical");
				final PlayerClass playerClass = PlayerClass.getPlayerClass(id);
				if ((playerClass == null) || !playerClass.name().equals(canonical))
				{
					throw new IllegalArgumentException("Humanized v2 class is not a canonical PlayerClass: " + id + '/' + canonical);
				}
				final Set<String> aliases = item.hasAttribute("aliases") ? aliases(attribute(item, "aliases"), "Humanized v2 class alias") : Set.of();
				final ClassDefinition definition = new ClassDefinition(id, canonical, boundedText(attribute(item, "label"), 64, "Humanized v2 class label"), aliases);
				if (_classes.putIfAbsent(id, definition) != null)
				{
					throw new IllegalArgumentException("Humanized v2 class is duplicated: " + id);
				}
			}
		}

		private void readV2IdentityPatterns(Element parent)
		{
			requireAttributes(parent, Set.of());
			for (Element item : children(parent))
			{
				requireTag(item, "pattern");
				requireAttributes(item, Set.of("id", "kind", "phrase", "priority"));
				final IdentityPattern pattern = new IdentityPattern(attribute(item, "id"), enumValue(item, "kind", IdentityKind.class), attribute(item, "phrase"), integer(item, "priority"));
				put(_identityPatterns, pattern.id(), pattern, false);
			}
		}

		private void indexV2Aliases()
		{
			for (GenderDefinition definition : _genders.values())
			{
				for (String alias : definition.aliases())
				{
					if (_genderAliases.putIfAbsent(alias, definition.gender()) != null)
					{
						throw new IllegalArgumentException("Humanized v2 normalized gender alias collision: " + alias);
					}
				}
			}
			for (ClassDefinition definition : _classes.values())
			{
				final Set<String> exactAliases = new HashSet<>(definition.aliases());
				exactAliases.add(normalize(definition.canonical().replace('_', ' ')));
				for (String alias : exactAliases)
				{
					if (_classAliases.putIfAbsent(alias, new ClassAliasResolution(definition.id(), null, "", Set.of(definition.id()))) != null)
					{
						throw new IllegalArgumentException("Humanized v2 normalized exact class alias collision: " + alias);
					}
				}
			}
			for (RoleDefinition definition : _roles.values())
			{
				for (String alias : definition.aliases())
				{
					if (_classAliases.putIfAbsent(alias, new ClassAliasResolution(null, definition.id(), definition.label(), definition.classIds())) != null)
					{
						throw new IllegalArgumentException("Humanized v2 normalized class/role alias collision: " + alias);
					}
				}
			}
		}

		private void readConversation(Path path, boolean custom, boolean mature)
		{
			final Element root = root(path, "humanizedConversationPack", MAX_CORE_BYTES);
			requireAttributes(root, Set.of("id", "version"));
			key(attribute(root, "id"), "Humanized conversation pack ID");
			requireVersion(root);
			for (Element child : children(root))
			{
				switch (child.getTagName())
				{
					case "templates" -> readTemplateElements(child, custom, mature);
					case "profanity" -> readProfanityElements(child, custom);
					default -> throw new IllegalArgumentException("Unknown humanized conversation element: " + child.getTagName());
				}
			}
		}

		private void readPersona(Path path)
		{
			final Element root = root(path, "humanizedPersonaPack", MAX_CORE_BYTES);
			requireAttributes(root, Set.of("id", "version"));
			key(attribute(root, "id"), "Humanized persona pack ID");
			requireVersion(root);
			for (Element child : children(root))
			{
				requireTag(child, "interests");
				requireAttributes(child, Set.of());
				for (Element item : children(child))
				{
					requireTag(item, "interest");
					requireAttributes(item, Set.of("id", "label"));
					final PersonaInterest interest = new PersonaInterest(attribute(item, "id"), attribute(item, "label"));
					put(_interests, interest.id(), interest, false);
				}
			}
			if (_interests.size() < 12)
			{
				throw new IllegalArgumentException("Humanized persona pack requires at least 12 interests.");
			}
		}

		private void readAliases(Path path, String expectedRoot, boolean custom)
		{
			final Element root = root(path, expectedRoot, MAX_CUSTOM_BYTES);
			requireAttributes(root, Set.of("version"));
			requireVersion(root);
			readAliasElements(root, custom);
		}

		private void readPatterns(Path path, String expectedRoot, boolean custom)
		{
			final Element root = root(path, expectedRoot, MAX_CUSTOM_BYTES);
			requireAttributes(root, Set.of("version"));
			requireVersion(root);
			readPatternElements(root, custom);
		}

		private void readTemplates(Path path, String expectedRoot, boolean custom, boolean mature)
		{
			final Element root = root(path, expectedRoot, MAX_CUSTOM_BYTES);
			requireAttributes(root, Set.of("version"));
			requireVersion(root);
			readTemplateElements(root, custom, mature);
		}

		private void readProfanity(Path path, String expectedRoot, boolean custom)
		{
			final Element root = root(path, expectedRoot, MAX_CUSTOM_BYTES);
			requireAttributes(root, Set.of("version"));
			requireVersion(root);
			readProfanityElements(root, custom);
		}

		private void readAliasElements(Element parent, boolean custom)
		{
			requireAttributes(parent, parent.hasAttribute("version") ? Set.of("version") : Set.of());
			for (Element item : children(parent))
			{
				requireTag(item, "alias");
				requireAttributes(item, custom ? Set.of("from", "to", "override") : Set.of("from", "to"));
				final String from = normalize(attribute(item, "from"));
				final String to = normalize(attribute(item, "to"));
				if (from.isEmpty() || to.isEmpty() || from.contains(" ") || to.contains(" ") || (from.length() > 32) || (to.length() > 32))
				{
					throw new IllegalArgumentException("Humanized alias must be one bounded token.");
				}
				final boolean override = custom && bool(item, "override");
				put(_aliases, from, to, override);
			}
		}

		private void readPatternElements(Element parent, boolean custom)
		{
			requireAttributes(parent, parent.hasAttribute("version") ? Set.of("version") : Set.of());
			for (Element item : children(parent))
			{
				requireTag(item, "pattern");
				final Set<String> attributes = new HashSet<>(Set.of("id", "topic", "act", "phrase", "salience", "ttlMinutes", "priority"));
				attributes.add("fact");
				attributes.add("recall");
				if (custom)
				{
					attributes.add("override");
				}
				requireAttributes(item, attributes);
				final TopicPattern pattern = new TopicPattern(attribute(item, "id"), attribute(item, "topic"), attribute(item, "act"), attribute(item, "phrase"), optionalEnum(item, "fact", FactKind.class), optionalEnum(item, "recall", FactKind.class), integer(item, "salience"), integer(item, "ttlMinutes"), integer(item, "priority"));
				put(_patterns, pattern.id(), pattern, custom && bool(item, "override"));
			}
		}

		private void readTemplateElements(Element parent, boolean custom, boolean implicitMature)
		{
			requireAttributes(parent, parent.hasAttribute("version") ? Set.of("version") : Set.of());
			for (Element item : children(parent))
			{
				requireTag(item, "template");
				final Set<String> attributes = new HashSet<>(Set.of("id", "act", "band", "register", "profanity", "text"));
				if (!implicitMature)
				{
					attributes.add("mature");
				}
				if (custom)
				{
					attributes.add("override");
				}
				requireAttributes(item, attributes);
				final Template template = new Template(attribute(item, "id"), attribute(item, "act"), enumValue(item, "band", RelationshipBand.class), enumValue(item, "register", Register.class), enumValue(item, "profanity", ProfanityMode.class), implicitMature || bool(item, "mature"), attribute(item, "text"));
				put(_templates, template.id(), template, custom && bool(item, "override"));
			}
		}

		private void readProfanityElements(Element parent, boolean custom)
		{
			requireAttributes(parent, parent.hasAttribute("version") ? Set.of("version") : Set.of());
			for (Element item : children(parent))
			{
				requireTag(item, "entry");
				final Set<String> attributes = new HashSet<>(Set.of("id", "level", "acts", "text"));
				if (custom)
				{
					attributes.add("override");
				}
				requireAttributes(item, attributes);
				final Set<String> acts = Set.of(attribute(item, "acts").split(",", -1));
				final Profanity profanity = new Profanity(attribute(item, "id"), enumValue(item, "level", ProfanityMode.class), acts, attribute(item, "text"));
				put(_profanity, profanity.id(), profanity, custom && bool(item, "override"));
			}
		}

		private void validateCoverage()
		{
			if (_limits == null)
			{
				throw new IllegalArgumentException("Humanized limits are absent.");
			}
			final Set<String> topics = new HashSet<>();
			_patterns.values().forEach(pattern -> topics.add(pattern.topic()));
			if (!topics.containsAll(REQUIRED_TOPICS))
			{
				throw new IllegalArgumentException("Humanized topic coverage is incomplete: " + missing(REQUIRED_TOPICS, topics));
			}
			final Map<String, Long> templatesPerAct = new HashMap<>();
			_templates.values().stream().filter(template -> !template.mature() && (template.profanity() == ProfanityMode.NONE)).forEach(template -> templatesPerAct.merge(template.act(), 1L, Long::sum));
			final Set<String> acts = new HashSet<>();
			_patterns.values().forEach(pattern -> acts.add(pattern.act()));
			for (String act : acts)
			{
				if (templatesPerAct.getOrDefault(act, 0L) < _limits.templatesPerAct())
				{
					throw new IllegalArgumentException("Humanized act has fewer than required clean templates: " + act);
				}
			}
			if (_aliases.size() > 256 || _patterns.size() > 512 || _templates.size() > 512 || _profanity.size() > 64)
			{
				throw new IllegalArgumentException("Humanized catalog exceeds hard entry bounds.");
			}
		}

		private void validateV2Coverage()
		{
			final Set<Integer> canonicalClassIds = new HashSet<>();
			for (PlayerClass playerClass : PlayerClass.values())
			{
				canonicalClassIds.add(playerClass.getId());
			}
			if (!_classes.keySet().equals(canonicalClassIds))
			{
				throw new IllegalArgumentException("Humanized v2 class catalog does not exactly cover canonical PlayerClass IDs.");
			}
			if (!_genders.keySet().equals(EnumSet.of(Gender.MALE, Gender.FEMALE)))
			{
				throw new IllegalArgumentException("Humanized v2 gender catalog must contain exactly MALE and FEMALE.");
			}
			final Set<String> requiredRoles = Set.of("tank", "melee", "damage", "archer", "nuker", "summoner", "cat_summoner", "healer", "bishop_line", "support", "kamael");
			if (!_roles.keySet().containsAll(requiredRoles) || !_roles.get("cat_summoner").classIds().equals(Set.of(14, 96)))
			{
				throw new IllegalArgumentException("Humanized v2 representative role or cat-summoner coverage is incomplete.");
			}
			for (RoleDefinition role : _roles.values())
			{
				if (!canonicalClassIds.containsAll(role.classIds()))
				{
					throw new IllegalArgumentException("Humanized v2 role references an unknown class: " + role.id());
				}
			}
			final long exactAliases = _classAliases.values().stream().filter(ClassAliasResolution::exact).count();
			final long roleAliases = _classAliases.size() - exactAliases;
			if ((_classes.size() > V2_MAX_CLASSES) || (exactAliases > V2_MAX_EXACT_CLASS_ALIASES) || (_roles.size() > V2_MAX_ROLES) || (roleAliases > V2_MAX_ROLE_ALIASES) || (_genderAliases.size() > V2_MAX_GENDER_ALIASES) || (_identityPatterns.size() > V2_MAX_IDENTITY_PATTERNS))
			{
				throw new IllegalArgumentException("Humanized v2 identity catalog exceeds hard entry bounds.");
			}
			final Set<String> identityActs = Set.of("identity.name.reply", "identity.summary.reply", "identity.gender.reply", "identity.gender.confirm", "identity.gender.deny", "identity.class.reply", "identity.class.confirm", "identity.class.deny", "identity.role.confirm", "identity.role.deny");
			final Map<String, Long> cleanTemplates = new HashMap<>();
			final Set<String> normalizedTexts = new HashSet<>();
			for (Template template : _templates.values())
			{
				if (!normalizedTexts.add(normalize(template.text())))
				{
					throw new IllegalArgumentException("Humanized v2 duplicate normalized response text: " + template.id());
				}
				if (!template.mature() && (template.profanity() == ProfanityMode.NONE))
				{
					cleanTemplates.merge(template.act(), 1L, Long::sum);
				}
			}
			for (String act : identityActs)
			{
				if (cleanTemplates.getOrDefault(act, 0L) < _limits.templatesPerAct())
				{
					throw new IllegalArgumentException("Humanized v2 identity act has fewer than required clean templates: " + act);
				}
			}
			final Set<String> socialActs = new HashSet<>();
			_patterns.values().stream().limit(_v1PatternCount).forEach(pattern -> socialActs.add(pattern.act()));
			for (String act : socialActs)
			{
				if (cleanTemplates.getOrDefault(act, 0L) < 6)
				{
					throw new IllegalArgumentException("Humanized v2 social act has fewer than six clean templates: " + act);
				}
			}
		}
	}

	private static <T> void put(Map<String, T> values, String id, T value, boolean override)
	{
		final boolean exists = values.containsKey(id);
		if (exists != override)
		{
			throw new IllegalArgumentException(exists ? "Humanized ID collision requires override=true: " + id : "Humanized override target does not exist: " + id);
		}
		values.put(id, value);
	}

	private static Element root(Path path, String expected, int maximumBytes)
	{
		try
		{
			final byte[] bytes = read(path, maximumBytes);
			strictUtf8(bytes);
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			requireTag(root, expected);
			return root;
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load humanized XML " + path + ": " + exception.getMessage(), exception);
		}
	}

	private static byte[] read(Path path, int maximumBytes)
	{
		try
		{
			if ((path == null) || !Files.isRegularFile(path) || Files.isSymbolicLink(path))
			{
				throw new IllegalArgumentException("Required regular humanized file is absent: " + path);
			}
			final long size = Files.size(path);
			if ((size < 1) || (size > maximumBytes))
			{
				throw new IllegalArgumentException("Humanized file size is outside bounds: " + path);
			}
			return Files.readAllBytes(path);
		}
		catch (java.io.IOException exception)
		{
			throw new IllegalArgumentException("Could not read humanized file " + path + ": " + exception.getMessage(), exception);
		}
	}

	private static String strictUtf8(byte[] bytes)
	{
		try
		{
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		}
		catch (CharacterCodingException exception)
		{
			throw new IllegalArgumentException("Humanized file is not strict UTF-8.", exception);
		}
	}

	private static List<Element> children(Element parent)
	{
		final List<Element> result = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if (node.getNodeType() == Node.ELEMENT_NODE)
			{
				result.add((Element) node);
			}
			else if ((node.getNodeType() == Node.TEXT_NODE) && !node.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Unexpected text in humanized XML element " + parent.getTagName());
			}
		}
		return result;
	}

	private static void requireTag(Element element, String expected)
	{
		if (!element.getTagName().equals(expected))
		{
			throw new IllegalArgumentException("Expected humanized XML element " + expected + " but found " + element.getTagName());
		}
	}

	private static void requireAttributes(Element element, Set<String> allowed)
	{
		for (int index = 0; index < element.getAttributes().getLength(); index++)
		{
			final String name = element.getAttributes().item(index).getNodeName();
			if (!allowed.contains(name))
			{
				throw new IllegalArgumentException("Unknown attribute " + name + " on humanized element " + element.getTagName());
			}
		}
	}

	private static String attribute(Element element, String name)
	{
		if (!element.hasAttribute(name) || element.getAttribute(name).isBlank())
		{
			throw new IllegalArgumentException("Missing humanized attribute " + name + " on " + element.getTagName());
		}
		return element.getAttribute(name).strip();
	}

	private static int integer(Element element, String name)
	{
		final String value = attribute(element, name);
		if (!value.matches("[0-9]+"))
		{
			throw new IllegalArgumentException("Humanized integer attribute is invalid: " + name);
		}
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Humanized integer attribute is outside bounds: " + name, exception);
		}
	}

	private static boolean bool(Element element, String name)
	{
		if (!element.hasAttribute(name))
		{
			return false;
		}
		return switch (element.getAttribute(name))
		{
			case "true" -> true;
			case "false" -> false;
			default -> throw new IllegalArgumentException("Humanized boolean attribute is invalid: " + name);
		};
	}

	private static <E extends Enum<E>> E enumValue(Element element, String name, Class<E> type)
	{
		try
		{
			return Enum.valueOf(type, attribute(element, name));
		}
		catch (IllegalArgumentException exception)
		{
			throw new IllegalArgumentException("Humanized enum attribute is invalid: " + name, exception);
		}
	}

	private static <E extends Enum<E>> E optionalEnum(Element element, String name, Class<E> type)
	{
		return element.hasAttribute(name) && !element.getAttribute(name).isBlank() ? enumValue(element, name, type) : null;
	}

	private static void requireVersion(Element root)
	{
		requireVersion(root, 1);
	}

	private static void requireVersion(Element root, int expected)
	{
		if (!attribute(root, "version").equals(Integer.toString(expected)))
		{
			throw new IllegalArgumentException("Humanized XML version must be " + expected + '.');
		}
	}

	private static Set<String> aliases(String value, String label)
	{
		final Set<String> result = new HashSet<>();
		for (String part : value.split(";", -1))
		{
			final String alias = normalize(part);
			if (alias.isEmpty() || (alias.codePointCount(0, alias.length()) > 48) || (alias.getBytes(StandardCharsets.UTF_8).length > 128) || !result.add(alias))
			{
				throw new IllegalArgumentException(label + " is empty, duplicated or outside bounds.");
			}
		}
		return Set.copyOf(result);
	}

	private static Set<Integer> integerSet(String value, String label)
	{
		final Set<Integer> result = new HashSet<>();
		for (String part : value.split(",", -1))
		{
			try
			{
				if (!part.equals(part.strip()) || !part.matches("[0-9]+") || !result.add(Integer.parseInt(part)))
				{
					throw new IllegalArgumentException(label + " is invalid or duplicated.");
				}
			}
			catch (NumberFormatException exception)
			{
				throw new IllegalArgumentException(label + " contains an invalid integer.", exception);
			}
		}
		if (result.isEmpty() || (result.size() > V2_MAX_CLASSES))
		{
			throw new IllegalArgumentException(label + " is empty or outside bounds.");
		}
		return Set.copyOf(result);
	}

	private static String contentHash(List<Path> paths, int xmlLimit, int otherLimit)
	{
		final StringBuilder canonical = new StringBuilder();
		for (Path path : paths)
		{
			final int limit = path.toString().endsWith(".tsv") ? otherLimit : xmlLimit;
			canonical.append(path.getFileName()).append(':').append(sha256(read(path, limit))).append('\n');
		}
		return sha256(canonical.toString());
	}

	public static String normalize(String value)
	{
		if (value == null)
		{
			return "";
		}
		final String source = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.forLanguageTag("ru")).replace('ё', 'е');
		final StringBuilder result = new StringBuilder(source.length());
		boolean space = true;
		for (int offset = 0; offset < source.length();)
		{
			final int codePoint = source.codePointAt(offset);
			offset += Character.charCount(codePoint);
			if (Character.isLetterOrDigit(codePoint) || (codePoint == '{') || (codePoint == '}'))
			{
				result.appendCodePoint(codePoint);
				space = false;
			}
			else if (!space)
			{
				result.append(' ');
				space = true;
			}
		}
		return result.toString().strip();
	}

	public static String sha256(String value)
	{
		return sha256(value.getBytes(StandardCharsets.UTF_8));
	}

	private static String sha256(byte[] bytes)
	{
		try
		{
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		}
		catch (NoSuchAlgorithmException exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static String key(String value, String label)
	{
		if ((value == null) || !KEY.matcher(value).matches())
		{
			throw new IllegalArgumentException(label + " must be a stable lowercase key.");
		}
		return value;
	}

	private static String boundedText(String value, int maximumUtf8Bytes, String label)
	{
		if ((value == null) || value.isBlank() || (value.getBytes(StandardCharsets.UTF_8).length > maximumUtf8Bytes))
		{
			throw new IllegalArgumentException(label + " is empty or exceeds its UTF-8 bound.");
		}
		return value.strip();
	}

	private static int count(String value, String token)
	{
		int result = 0;
		for (int index = value.indexOf(token); index >= 0; index = value.indexOf(token, index + token.length()))
		{
			result++;
		}
		return result;
	}

	private static String hash(String value)
	{
		if ((value == null) || !value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Humanized authority hash is invalid.");
		}
		return value;
	}

	private static Set<String> missing(Set<String> required, Set<String> actual)
	{
		final Set<String> missing = new HashSet<>(required);
		missing.removeAll(actual);
		return Set.copyOf(missing);
	}
}

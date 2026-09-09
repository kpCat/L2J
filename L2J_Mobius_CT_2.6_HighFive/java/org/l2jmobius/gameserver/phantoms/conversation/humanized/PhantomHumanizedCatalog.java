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

	private PhantomHumanizedCatalog(Limits limits, Map<String, String> aliases, List<TopicPattern> patterns, List<Template> templates, List<Profanity> profanity, List<PersonaInterest> interests, Set<String> blocked, String coreHash, String customHash, String combinedHash, int corpusCases)
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
	}

	public static PhantomHumanizedCatalog load(Path phantomDataRoot, boolean customEnabled)
	{
		Objects.requireNonNull(phantomDataRoot);
		final Path root = phantomDataRoot.toAbsolutePath().normalize();
		final Path semantic = root.resolve("semantic/humanized/high-five-ru-humanized-semantic-v1.xml");
		final Path conversation = root.resolve("conversation/humanized/high-five-ru-humanized-conversation-v1.xml");
		final Path persona = root.resolve("conversation/humanized/high-five-ru-persona-v1.xml");
		final Path corpus = root.resolve("semantic/humanized/high-five-ru-humanized-corpus-v1.tsv");
		final List<Path> coreFiles = List.of(semantic, conversation, persona, corpus);
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
		final String coreHash = contentHash(coreFiles, MAX_CORE_BYTES, MAX_CORPUS_BYTES);
		final String customHash = customEnabled ? contentHash(customFiles, MAX_CUSTOM_BYTES, MAX_CUSTOM_BYTES) : sha256("custom.disabled");
		final String combinedHash = sha256(coreHash + '|' + customHash + "|v1");
		final PhantomHumanizedCatalog provisional = new PhantomHumanizedCatalog(loader._limits, loader._aliases, new ArrayList<>(loader._patterns.values()), new ArrayList<>(loader._templates.values()), new ArrayList<>(loader._profanity.values()), new ArrayList<>(loader._interests.values()), loader._blocked, coreHash, customHash, combinedHash, 0);
		final int corpusCases = validateCorpus(corpus, provisional);
		return new PhantomHumanizedCatalog(loader._limits, loader._aliases, new ArrayList<>(loader._patterns.values()), new ArrayList<>(loader._templates.values()), new ArrayList<>(loader._profanity.values()), new ArrayList<>(loader._interests.values()), loader._blocked, coreHash, customHash, combinedHash, corpusCases);
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
		private Limits _limits;

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
		if (!attribute(root, "version").equals("1"))
		{
			throw new IllegalArgumentException("Humanized XML version must be 1.");
		}
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

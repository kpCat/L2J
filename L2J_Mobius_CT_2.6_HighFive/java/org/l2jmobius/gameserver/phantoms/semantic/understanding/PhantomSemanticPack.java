/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.semantic.understanding;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding.Authority;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding.Hashes;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.EvidenceQuality;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingStatus;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Strict, content-addressed Russian language facts and deterministic test corpus.
 */
public final class PhantomSemanticPack
{
	public static final int MAX_XML_BYTES = 256 * 1024;
	public static final int MAX_CORPUS_BYTES = 256 * 1024;
	private static final Set<String> REQUIRED_INTENTS = Set.of("party.invite", "party.accept", "party.refuse", "party.leave", "party.role.query", "party.travel", "party.support.request", "party.assist.request", "party.regroup.request", "entity.locate", "item.acquire.query", "item.source.query", "content.requirements.query", "unknown");
	private static final Set<String> REQUIRED_REASONS = Set.of("accept.matched", "clarify.intent", "clarify.target_player", "clarify.entity", "clarify.party_role", "clarify.location", "clarify.quantity", "clarify.complexity", "reject.unsupported", "reject.too_long", "reject.mixed_script");

	public enum ContextResolver
	{
		SPEAKER,
		SELECTED_OR_UNIQUE_PLAYER,
		PARTY_LEADER,
		PREVIOUS_TARGET,
		CURRENT_LOCATION,
		CURRENT_TOPOLOGY
	}

	public record Limits(int maxXmlBytes, int maxCorpusBytes, int maxInputCodePoints, int maxInputUtf8Bytes, int maxTokens, int maxTokenCodePoints, int maxFillers, int maxLexicalAliases, int maxPatterns, int maxEntityAliases, int maxContextAliases, int maxCandidates, int maxAlternatives, int maxEvidence, int maxSlots, int maxPartyMembers, int maxContextPlayers, int maxPreviousSlots, int acceptanceThreshold, int ambiguityMargin)
	{
		private void validate()
		{
			if ((maxXmlBytes != MAX_XML_BYTES) || (maxCorpusBytes != MAX_CORPUS_BYTES) || (maxInputCodePoints != 512) || (maxInputUtf8Bytes != 2048) || (maxTokens != 64) || (maxTokenCodePoints != 64) || (maxFillers != 32) || (maxLexicalAliases != 128) || (maxPatterns != 128) || (maxEntityAliases != 128) || (maxContextAliases != 32) || (maxCandidates != 128) || (maxAlternatives != PhantomSemanticModel.MAX_ALTERNATIVES) || (maxEvidence != PhantomSemanticModel.MAX_EVIDENCE) || (maxSlots != PhantomSemanticModel.MAX_RESULT_SLOTS) || (maxPartyMembers != PhantomSemanticModel.MAX_PARTY_MEMBERS) || (maxContextPlayers != PhantomSemanticModel.MAX_CONTEXT_PLAYERS) || (maxPreviousSlots != PhantomSemanticModel.MAX_PREVIOUS_SLOTS) || (acceptanceThreshold < 1) || (acceptanceThreshold > 10000) || (ambiguityMargin < 1) || (ambiguityMargin > 2000))
			{
				throw new IllegalArgumentException("Semantic pack hard limits do not match the bounded runtime contract.");
			}
		}
	}

	public record NormalizationPolicy(int repeatLimit, Set<Integer> punctuationCodePoints)
	{
		public NormalizationPolicy
		{
			if ((repeatLimit < 1) || (repeatLimit > 3) || (punctuationCodePoints == null) || punctuationCodePoints.isEmpty() || (punctuationCodePoints.size() > 32))
			{
				throw new IllegalArgumentException("Invalid semantic normalization policy.");
			}
			punctuationCodePoints = Set.copyOf(punctuationCodePoints);
		}
	}

	public record TypoPolicy(int minimumCodePoints, int shortMaximumCodePoints, int shortDistance, int longDistance)
	{
		public TypoPolicy
		{
			if ((minimumCodePoints != 4) || (shortMaximumCodePoints != 7) || (shortDistance != 1) || (longDistance != 2))
			{
				throw new IllegalArgumentException("Semantic typo policy must preserve the Goal 019 edit boundaries.");
			}
		}

		public int maximumDistance(int codePoints)
		{
			return codePoints < minimumCodePoints ? 0 : (codePoints <= shortMaximumCodePoints ? shortDistance : longDistance);
		}
	}

	public record LexicalAlias(String source, String target, EvidenceQuality quality)
	{
		public LexicalAlias
		{
			if ((source == null) || source.isBlank() || (target == null) || target.isBlank() || (quality != EvidenceQuality.TRANSLITERATION && quality != EvidenceQuality.ABBREVIATION))
			{
				throw new IllegalArgumentException("Invalid semantic lexical alias.");
			}
		}
	}

	public record SlotDefinition(SlotType type, int maximumTokens)
	{
		public SlotDefinition
		{
			Objects.requireNonNull(type, "Semantic slot definition type must not be null.");
			if ((maximumTokens < 1) || (maximumTokens > 8))
			{
				throw new IllegalArgumentException("Semantic slot token bound is invalid.");
			}
		}
	}

	public sealed interface PatternPart permits LiteralPart, SlotPart
	{
	}

	public record LiteralPart(String value) implements PatternPart
	{
		public LiteralPart
		{
			if ((value == null) || value.isBlank())
			{
				throw new IllegalArgumentException("Semantic pattern literal must not be blank.");
			}
		}
	}

	public record SlotPart(SlotType type) implements PatternPart
	{
		public SlotPart
		{
			Objects.requireNonNull(type, "Semantic pattern slot must not be null.");
		}
	}

	public record PatternDefinition(String id, String intentKey, List<PatternPart> parts, String firstLiteral)
	{
		public PatternDefinition
		{
			id = PhantomSemanticModel.requireKey(id, "Semantic pattern id");
			intentKey = PhantomSemanticModel.requireKey(intentKey, "Semantic pattern intent");
			parts = List.copyOf(parts);
			if (parts.isEmpty() || (firstLiteral == null) || firstLiteral.isBlank() || (parts.getFirst() instanceof SlotPart))
			{
				throw new IllegalArgumentException("Semantic pattern must start with a literal.");
			}
			final Set<SlotType> slotTypes = new HashSet<>();
			boolean previousSlot = false;
			for (PatternPart part : parts)
			{
				if (part instanceof SlotPart slot)
				{
					if (previousSlot || !slotTypes.add(slot.type()) || (slotTypes.size() > 4))
					{
						throw new IllegalArgumentException("Semantic pattern slots must be unique, literal-separated and bounded to four.");
					}
					previousSlot = true;
				}
				else
				{
					previousSlot = false;
				}
			}
		}
	}

	public record IntentDefinition(String key, int baseScore, Set<SlotType> requiredAll, Set<SlotType> requiredAny, List<PatternDefinition> patterns)
	{
		public IntentDefinition
		{
			key = PhantomSemanticModel.requireKey(key, "Semantic intent key");
			if ((baseScore < 0) || (baseScore > 10000))
			{
				throw new IllegalArgumentException("Semantic intent base score must be 0..10000.");
			}
			requiredAll = Set.copyOf(requiredAll);
			requiredAny = Set.copyOf(requiredAny);
			patterns = List.copyOf(patterns);
		}
	}

	public record EntityAlias(SlotType slotType, String phrase, List<String> tokens, PhantomDomainRef reference, EvidenceQuality quality)
	{
		public EntityAlias
		{
			Objects.requireNonNull(slotType, "Semantic entity slot must not be null.");
			if ((phrase == null) || phrase.isBlank())
			{
				throw new IllegalArgumentException("Semantic entity alias must not be blank.");
			}
			tokens = List.copyOf(tokens);
			Objects.requireNonNull(reference, "Semantic entity ref must not be null.");
			Objects.requireNonNull(quality, "Semantic entity quality must not be null.");
		}
	}

	public record ContextAlias(SlotType slotType, String phrase, List<String> tokens, ContextResolver resolver)
	{
		public ContextAlias
		{
			Objects.requireNonNull(slotType, "Semantic context slot must not be null.");
			if ((phrase == null) || phrase.isBlank())
			{
				throw new IllegalArgumentException("Semantic context alias must not be blank.");
			}
			tokens = List.copyOf(tokens);
			Objects.requireNonNull(resolver, "Semantic context resolver must not be null.");
		}
	}

	public record CorpusCase(String caseId, String input, String contextFixture, UnderstandingStatus expectedStatus, String expectedIntent, String expectedSlots, int minimumConfidence, String reasonKey)
	{
		public CorpusCase
		{
			caseId = PhantomSemanticModel.requireKey(caseId, "Semantic corpus case id");
			if ((input == null) || input.isBlank() || (input.length() > 4096))
			{
				throw new IllegalArgumentException("Semantic corpus input is invalid.");
			}
			contextFixture = PhantomSemanticModel.requireKey(contextFixture, "Semantic context fixture");
			Objects.requireNonNull(expectedStatus, "Semantic corpus status must not be null.");
			expectedIntent = PhantomSemanticModel.requireKey(expectedIntent, "Semantic corpus intent");
			if ((expectedSlots == null) || expectedSlots.isBlank() || (expectedSlots.length() > 512) || (minimumConfidence < 0) || (minimumConfidence > 10000))
			{
				throw new IllegalArgumentException("Semantic corpus expectation is invalid.");
			}
			reasonKey = PhantomSemanticModel.requireKey(reasonKey, "Semantic corpus reason");
		}
	}

	private final String _id;
	private final int _version;
	private final Limits _limits;
	private final NormalizationPolicy _normalization;
	private final TypoPolicy _typoPolicy;
	private final Set<String> _fillers;
	private final Map<String, LexicalAlias> _lexicalAliases;
	private final Map<SlotType, SlotDefinition> _slots;
	private final Map<String, IntentDefinition> _intents;
	private final Map<String, List<PatternDefinition>> _patternsByFirstLiteral;
	private final Map<Integer, List<PatternDefinition>> _patternsByFirstLength;
	private final Map<String, List<EntityAlias>> _entityAliases;
	private final Map<SlotType, Map<Integer, List<EntityAlias>>> _fuzzyEntityAliases;
	private final Map<String, List<ContextAlias>> _contextAliases;
	private final Set<String> _reasons;
	private final List<CorpusCase> _corpus;
	private final Hashes _authorityHashes;
	private final String _packHash;
	private final String _corpusHash;

	private PhantomSemanticPack(String id, int version, Limits limits, NormalizationPolicy normalization, TypoPolicy typoPolicy, Set<String> fillers, Map<String, LexicalAlias> lexicalAliases, Map<SlotType, SlotDefinition> slots, Map<String, IntentDefinition> intents, List<PatternDefinition> patterns, List<EntityAlias> entities, List<ContextAlias> contexts, Set<String> reasons, List<CorpusCase> corpus, Hashes authorityHashes, String packHash, String corpusHash)
	{
		_id = id;
		_version = version;
		_limits = limits;
		_normalization = normalization;
		_typoPolicy = typoPolicy;
		_fillers = Set.copyOf(fillers);
		_lexicalAliases = Map.copyOf(lexicalAliases);
		_slots = Map.copyOf(slots);
		_intents = Map.copyOf(intents);
		_patternsByFirstLiteral = groupPatterns(patterns, false);
		_patternsByFirstLength = groupPatternsByLength(patterns);
		_entityAliases = groupEntities(entities);
		_fuzzyEntityAliases = groupFuzzyEntities(entities);
		_contextAliases = groupContexts(contexts);
		_reasons = Set.copyOf(reasons);
		_corpus = List.copyOf(corpus);
		_authorityHashes = authorityHashes;
		_packHash = packHash;
		_corpusHash = corpusHash;
	}

	public static PhantomSemanticPack load(Path xmlPath, Path corpusPath, Authority authority)
	{
		Objects.requireNonNull(authority, "Semantic authority must not be null.");
		try
		{
			final byte[] xmlBytes = readBounded(xmlPath, MAX_XML_BYTES, "Semantic XML");
			decodeUtf8Strict(xmlBytes, "Semantic XML");
			final Hashes before = authority.hashes();
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes)).getDocumentElement();
			requireElement(root, "semanticPack", List.of("id", "locale", "version"));
			if (!"high-five-ru-semantic-v1".equals(root.getAttribute("id")) || !"ru".equals(root.getAttribute("locale")) || !"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Unknown semantic pack identity, locale or version.");
			}
			final List<Element> sections = childElements(root);
			final List<String> sectionNames = sections.stream().map(Element::getTagName).toList();
			final List<String> expectedSections = List.of("limits", "normalization", "typoPolicy", "fillers", "lexicalAliases", "slots", "contextAliases", "entityAliases", "reasons", "intents");
			if (!sectionNames.equals(expectedSections))
			{
				throw new IllegalArgumentException("Semantic pack sections are missing, duplicated or out of order.");
			}
			final Limits limits = parseLimits(sections.get(0));
			limits.validate();
			final NormalizationPolicy normalization = parseNormalization(sections.get(1));
			final TypoPolicy typoPolicy = parseTypoPolicy(sections.get(2));
			final Set<String> fillers = parseFillers(sections.get(3), normalization, limits);
			final Map<String, LexicalAlias> lexicalAliases = parseLexicalAliases(sections.get(4), normalization, limits);
			final Map<SlotType, SlotDefinition> slots = parseSlots(sections.get(5));
			final List<ContextAlias> contexts = parseContexts(sections.get(6), normalization, limits);
			final List<EntityAlias> entities = parseEntities(sections.get(7), normalization, limits, authority);
			final Set<String> reasons = parseReasons(sections.get(8));
			final ParseIntents parsedIntents = parseIntents(sections.get(9), normalization, limits, slots);
			if (!authority.hashes().equals(before))
			{
				throw new IllegalArgumentException("Semantic authority changed while aliases were validated.");
			}
			final byte[] corpusBytes = readBounded(corpusPath, MAX_CORPUS_BYTES, "Semantic corpus");
			final List<CorpusCase> corpus = parseCorpus(decodeUtf8Strict(corpusBytes, "Semantic corpus"), parsedIntents.intents(), reasons);
			return new PhantomSemanticPack(root.getAttribute("id"), 1, limits, normalization, typoPolicy, fillers, lexicalAliases, slots, parsedIntents.intents(), parsedIntents.patterns(), entities, contexts, reasons, corpus, before, sha256(xmlBytes), sha256(corpusBytes));
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load strict Russian Semantic Pack.", exception);
		}
	}

	private static Limits parseLimits(Element element)
	{
		final List<String> attributes = List.of("maxXmlBytes", "maxCorpusBytes", "maxInputCodePoints", "maxInputUtf8Bytes", "maxTokens", "maxTokenCodePoints", "maxFillers", "maxLexicalAliases", "maxPatterns", "maxEntityAliases", "maxContextAliases", "maxCandidates", "maxAlternatives", "maxEvidence", "maxSlots", "maxPartyMembers", "maxContextPlayers", "maxPreviousSlots", "acceptanceThreshold", "ambiguityMargin");
		requireElement(element, "limits", attributes);
		return new Limits(integer(element, "maxXmlBytes", 1, Integer.MAX_VALUE), integer(element, "maxCorpusBytes", 1, Integer.MAX_VALUE), integer(element, "maxInputCodePoints", 1, 4096), integer(element, "maxInputUtf8Bytes", 1, 16384), integer(element, "maxTokens", 1, 256), integer(element, "maxTokenCodePoints", 1, 256), integer(element, "maxFillers", 1, 256), integer(element, "maxLexicalAliases", 1, 1024), integer(element, "maxPatterns", 1, 1024), integer(element, "maxEntityAliases", 1, 1024), integer(element, "maxContextAliases", 1, 256), integer(element, "maxCandidates", 1, 1024), integer(element, "maxAlternatives", 1, 16), integer(element, "maxEvidence", 1, 64), integer(element, "maxSlots", 1, 64), integer(element, "maxPartyMembers", 1, 64), integer(element, "maxContextPlayers", 1, 128), integer(element, "maxPreviousSlots", 1, 64), integer(element, "acceptanceThreshold", 1, 10000), integer(element, "ambiguityMargin", 1, 2000));
	}

	private static NormalizationPolicy parseNormalization(Element element)
	{
		requireElement(element, "normalization", List.of("form", "lowercase", "yoToE", "collapseWhitespace", "repeatLimit", "punctuation"));
		if (!"NFKC".equals(element.getAttribute("form")) || !"Locale.ROOT".equals(element.getAttribute("lowercase")) || !strictBoolean(element.getAttribute("yoToE")) || !strictBoolean(element.getAttribute("collapseWhitespace")))
		{
			throw new IllegalArgumentException("Semantic normalization policy changed.");
		}
		final Set<Integer> punctuation = new HashSet<>();
		element.getAttribute("punctuation").codePoints().forEach(punctuation::add);
		return new NormalizationPolicy(integer(element, "repeatLimit", 1, 3), punctuation);
	}

	private static TypoPolicy parseTypoPolicy(Element element)
	{
		requireElement(element, "typoPolicy", List.of("minimumCodePoints", "shortMaximumCodePoints", "shortDistance", "longDistance"));
		return new TypoPolicy(integer(element, "minimumCodePoints", 1, 16), integer(element, "shortMaximumCodePoints", 1, 32), integer(element, "shortDistance", 0, 4), integer(element, "longDistance", 0, 4));
	}

	private static Set<String> parseFillers(Element element, NormalizationPolicy policy, Limits limits)
	{
		requireElement(element, "fillers", List.of());
		final Set<String> result = new HashSet<>();
		for (Element child : children(element, "word"))
		{
			requireElement(child, "word", List.of("value"));
			final String word = singlePhraseToken(child.getAttribute("value"), policy, limits);
			if (!result.add(word))
			{
				throw new IllegalArgumentException("Duplicate semantic filler word.");
			}
		}
		if (result.isEmpty() || (result.size() > limits.maxFillers()))
		{
			throw new IllegalArgumentException("Semantic filler count is outside bounds.");
		}
		return Set.copyOf(result);
	}

	private static Map<String, LexicalAlias> parseLexicalAliases(Element element, NormalizationPolicy policy, Limits limits)
	{
		requireElement(element, "lexicalAliases", List.of());
		final Map<String, LexicalAlias> result = new HashMap<>();
		for (Element child : children(element, "alias"))
		{
			requireElement(child, "alias", List.of("kind", "from", "to"));
			final String source = singlePhraseToken(child.getAttribute("from"), policy, limits);
			final String target = singlePhraseToken(child.getAttribute("to"), policy, limits);
			final EvidenceQuality quality = switch (child.getAttribute("kind"))
			{
				case "TRANSLITERATION" -> EvidenceQuality.TRANSLITERATION;
				case "ABBREVIATION" -> EvidenceQuality.ABBREVIATION;
				default -> throw new IllegalArgumentException("Unknown semantic lexical alias kind.");
			};
			if (source.equals(target) || (result.put(source, new LexicalAlias(source, target, quality)) != null))
			{
				throw new IllegalArgumentException("Duplicate or identity semantic lexical alias.");
			}
		}
		if (result.size() > limits.maxLexicalAliases())
		{
			throw new IllegalArgumentException("Semantic lexical alias count exceeds bounds.");
		}
		for (LexicalAlias alias : result.values())
		{
			if (result.containsKey(alias.target()))
			{
				throw new IllegalArgumentException("Semantic lexical alias chains are forbidden.");
			}
		}
		return Map.copyOf(result);
	}

	private static Map<SlotType, SlotDefinition> parseSlots(Element element)
	{
		requireElement(element, "slots", List.of());
		final EnumMap<SlotType, SlotDefinition> result = new EnumMap<>(SlotType.class);
		for (Element child : children(element, "slot"))
		{
			requireElement(child, "slot", List.of("type", "maxTokens"));
			final SlotType type = SlotType.valueOf(child.getAttribute("type"));
			if (result.put(type, new SlotDefinition(type, integer(child, "maxTokens", 1, 8))) != null)
			{
				throw new IllegalArgumentException("Duplicate semantic slot definition.");
			}
		}
		if (!result.keySet().equals(Set.of(SlotType.values())))
		{
			throw new IllegalArgumentException("Semantic slot catalog is incomplete.");
		}
		return Map.copyOf(result);
	}

	private static List<ContextAlias> parseContexts(Element element, NormalizationPolicy policy, Limits limits)
	{
		requireElement(element, "contextAliases", List.of());
		final ArrayList<ContextAlias> result = new ArrayList<>();
		final Set<String> unique = new HashSet<>();
		for (Element child : children(element, "alias"))
		{
			requireElement(child, "alias", List.of("slot", "phrase", "resolver"));
			final SlotType slot = SlotType.valueOf(child.getAttribute("slot"));
			final List<String> tokens = phrase(child.getAttribute("phrase"), policy, limits);
			final String key = aliasKey(slot, tokens);
			if (!unique.add(key))
			{
				throw new IllegalArgumentException("Duplicate semantic context alias.");
			}
			result.add(new ContextAlias(slot, String.join(" ", tokens), tokens, ContextResolver.valueOf(child.getAttribute("resolver"))));
		}
		if (result.size() > limits.maxContextAliases())
		{
			throw new IllegalArgumentException("Semantic context alias count exceeds bounds.");
		}
		return List.copyOf(result);
	}

	private static List<EntityAlias> parseEntities(Element element, NormalizationPolicy policy, Limits limits, Authority authority)
	{
		requireElement(element, "entityAliases", List.of());
		final ArrayList<EntityAlias> result = new ArrayList<>();
		final Set<String> unique = new HashSet<>();
		for (Element child : children(element, "alias"))
		{
			requireElement(child, "alias", List.of("slot", "phrase", "key", "quality"));
			final SlotType slot = SlotType.valueOf(child.getAttribute("slot"));
			if ((slot == SlotType.TARGET_PLAYER) || (slot == SlotType.QUANTITY) || (slot == SlotType.RESPONSE))
			{
				throw new IllegalArgumentException("Semantic entity alias uses a non-authority slot.");
			}
			final List<String> tokens = phrase(child.getAttribute("phrase"), policy, limits);
			final String authorityKey = child.getAttribute("key");
			final PhantomDomainRef reference = authority.resolve(slot, authorityKey).orElseThrow(() -> new IllegalArgumentException("Semantic alias target is not current authoritative truth: " + slot + ':' + authorityKey));
			final EvidenceQuality quality = EvidenceQuality.valueOf(child.getAttribute("quality"));
			if ((quality != EvidenceQuality.EXACT) && (quality != EvidenceQuality.TRANSLITERATION) && (quality != EvidenceQuality.ABBREVIATION))
			{
				throw new IllegalArgumentException("Semantic entity alias has an invalid declared quality.");
			}
			final String uniqueKey = aliasKey(slot, tokens) + '\u0000' + reference.namespace() + ':' + reference.key();
			if (!unique.add(uniqueKey))
			{
				throw new IllegalArgumentException("Duplicate semantic entity alias target.");
			}
			result.add(new EntityAlias(slot, String.join(" ", tokens), tokens, reference, quality));
		}
		if (result.isEmpty() || (result.size() > limits.maxEntityAliases()))
		{
			throw new IllegalArgumentException("Semantic entity alias count is outside bounds.");
		}
		return List.copyOf(result);
	}

	private static Set<String> parseReasons(Element element)
	{
		requireElement(element, "reasons", List.of());
		final Set<String> result = new HashSet<>();
		for (Element child : children(element, "reason"))
		{
			requireElement(child, "reason", List.of("key"));
			if (!result.add(PhantomSemanticModel.requireKey(child.getAttribute("key"), "Semantic reason key")))
			{
				throw new IllegalArgumentException("Duplicate semantic reason key.");
			}
		}
		if (!result.containsAll(REQUIRED_REASONS))
		{
			throw new IllegalArgumentException("Semantic clarification/rejection reason catalog is incomplete.");
		}
		return Set.copyOf(result);
	}

	private static ParseIntents parseIntents(Element element, NormalizationPolicy policy, Limits limits, Map<SlotType, SlotDefinition> slots)
	{
		requireElement(element, "intents", List.of());
		final Map<String, IntentDefinition> intents = new HashMap<>();
		final ArrayList<PatternDefinition> patterns = new ArrayList<>();
		final Set<String> patternIds = new HashSet<>();
		for (Element child : children(element, "intent"))
		{
			requireElement(child, "intent", List.of("key", "baseScore", "requiredAll", "requiredAny"));
			final String key = PhantomSemanticModel.requireKey(child.getAttribute("key"), "Semantic intent key");
			final Set<SlotType> requiredAll = slotSet(child.getAttribute("requiredAll"));
			final Set<SlotType> requiredAny = slotSet(child.getAttribute("requiredAny"));
			if (!slots.keySet().containsAll(requiredAll) || !slots.keySet().containsAll(requiredAny))
			{
				throw new IllegalArgumentException("Semantic intent references an unknown slot.");
			}
			final ArrayList<PatternDefinition> intentPatterns = new ArrayList<>();
			for (Element pattern : children(child, "pattern"))
			{
				requireElement(pattern, "pattern", List.of("id", "text"));
				final String patternId = PhantomSemanticModel.requireKey(pattern.getAttribute("id"), "Semantic pattern id");
				if (!patternIds.add(patternId))
				{
					throw new IllegalArgumentException("Duplicate semantic pattern id.");
				}
				final List<PatternPart> parts = patternParts(pattern.getAttribute("text"), policy, limits, slots);
				final String firstLiteral = parts.stream().filter(LiteralPart.class::isInstance).map(LiteralPart.class::cast).map(LiteralPart::value).findFirst().orElseThrow();
				final PatternDefinition definition = new PatternDefinition(patternId, key, parts, firstLiteral);
				intentPatterns.add(definition);
				patterns.add(definition);
			}
			if ((intents.put(key, new IntentDefinition(key, integer(child, "baseScore", 0, 10000), requiredAll, requiredAny, intentPatterns)) != null) || (!key.equals("unknown") && intentPatterns.isEmpty()) || (key.equals("unknown") && !intentPatterns.isEmpty()))
			{
				throw new IllegalArgumentException("Semantic intent is duplicated or has invalid pattern ownership.");
			}
		}
		if (!intents.keySet().equals(REQUIRED_INTENTS) || (patterns.size() > limits.maxPatterns()))
		{
			throw new IllegalArgumentException("Semantic intent/pattern catalog is incomplete or exceeds bounds.");
		}
		return new ParseIntents(Map.copyOf(intents), List.copyOf(patterns));
	}

	private static List<PatternPart> patternParts(String text, NormalizationPolicy policy, Limits limits, Map<SlotType, SlotDefinition> slots)
	{
		if ((text == null) || text.isBlank() || !text.equals(text.trim()) || (text.length() > 256))
		{
			throw new IllegalArgumentException("Semantic pattern text is invalid.");
		}
		final ArrayList<PatternPart> result = new ArrayList<>();
		for (String part : text.split(" +"))
		{
			if (part.matches("\\{[A-Z_]+}"))
			{
				final SlotType slot = SlotType.valueOf(part.substring(1, part.length() - 1));
				if (!slots.containsKey(slot))
				{
					throw new IllegalArgumentException("Semantic pattern references an unknown slot.");
				}
				result.add(new SlotPart(slot));
			}
			else
			{
				result.add(new LiteralPart(singlePhraseToken(part, policy, limits)));
			}
		}
		if ((result.isEmpty()) || (result.size() > 16))
		{
			throw new IllegalArgumentException("Semantic pattern part count is outside bounds.");
		}
		return List.copyOf(result);
	}

	private static List<CorpusCase> parseCorpus(String text, Map<String, IntentDefinition> intents, Set<String> reasons)
	{
		final String[] lines = text.split("\\r?\\n", -1);
		final String header = "case_id\tinput\tcontext_fixture\texpected_status\texpected_intent\texpected_slots\tminimum_confidence\treason_key";
		if ((lines.length < 2) || !header.equals(lines[0]))
		{
			throw new IllegalArgumentException("Semantic corpus header is not exact.");
		}
		final ArrayList<CorpusCase> result = new ArrayList<>();
		final Set<String> ids = new HashSet<>();
		int cyrillic = 0;
		int transliteration = 0;
		int clarification = 0;
		int rejected = 0;
		for (int lineIndex = 1; lineIndex < lines.length; lineIndex++)
		{
			final String line = lines[lineIndex];
			if (line.isEmpty() && (lineIndex == (lines.length - 1)))
			{
				continue;
			}
			final String[] columns = line.split("\\t", -1);
			if ((columns.length != 8) || java.util.Arrays.stream(columns).anyMatch(String::isBlank))
			{
				throw new IllegalArgumentException("Semantic corpus line does not contain eight nonblank TSV columns.");
			}
			final UnderstandingStatus status = UnderstandingStatus.valueOf(columns[3]);
			if (!intents.containsKey(columns[4]) || !reasons.contains(columns[7]) || !columns[6].matches("[0-9]{1,5}"))
			{
				throw new IllegalArgumentException("Semantic corpus expectation references an unknown contract key.");
			}
			final CorpusCase corpusCase = new CorpusCase(columns[0], columns[1], columns[2], status, columns[4], columns[5], Integer.parseInt(columns[6]), columns[7]);
			if (!ids.add(corpusCase.caseId()))
			{
				throw new IllegalArgumentException("Duplicate semantic corpus case id.");
			}
			result.add(corpusCase);
			final boolean hasCyrillic = columns[1].codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.CYRILLIC);
			final boolean hasLatin = columns[1].codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN);
			cyrillic += hasCyrillic ? 1 : 0;
			transliteration += hasLatin && !hasCyrillic ? 1 : 0;
			clarification += status == UnderstandingStatus.CLARIFICATION_REQUIRED ? 1 : 0;
			rejected += status == UnderstandingStatus.REJECTED ? 1 : 0;
		}
		if ((result.size() < 240) || (cyrillic < 120) || (transliteration < 40) || (clarification < 40) || (rejected < 40))
		{
			throw new IllegalArgumentException("Semantic corpus coverage is below Goal 019 thresholds.");
		}
		return List.copyOf(result);
	}

	private static Set<SlotType> slotSet(String value)
	{
		if ("-".equals(value))
		{
			return Set.of();
		}
		final Set<SlotType> result = new HashSet<>();
		for (String token : value.split(",", -1))
		{
			if (token.isBlank() || !result.add(SlotType.valueOf(token)))
			{
				throw new IllegalArgumentException("Semantic required-slot set is invalid.");
			}
		}
		return Set.copyOf(result);
	}

	private static Map<String, List<PatternDefinition>> groupPatterns(List<PatternDefinition> patterns, boolean unused)
	{
		final TreeMap<String, List<PatternDefinition>> grouped = new TreeMap<>();
		for (PatternDefinition pattern : patterns)
		{
			grouped.computeIfAbsent(pattern.firstLiteral(), _ -> new ArrayList<>()).add(pattern);
		}
		grouped.replaceAll((_, values) -> values.stream().sorted(java.util.Comparator.comparing(PatternDefinition::id)).toList());
		return Map.copyOf(grouped);
	}

	private static Map<Integer, List<PatternDefinition>> groupPatternsByLength(List<PatternDefinition> patterns)
	{
		final TreeMap<Integer, List<PatternDefinition>> grouped = new TreeMap<>();
		for (PatternDefinition pattern : patterns)
		{
			grouped.computeIfAbsent(pattern.firstLiteral().codePointCount(0, pattern.firstLiteral().length()), _ -> new ArrayList<>()).add(pattern);
		}
		grouped.replaceAll((_, values) -> values.stream().sorted(java.util.Comparator.comparing(PatternDefinition::id)).toList());
		return Map.copyOf(grouped);
	}

	private static Map<String, List<EntityAlias>> groupEntities(List<EntityAlias> entities)
	{
		final TreeMap<String, List<EntityAlias>> grouped = new TreeMap<>();
		for (EntityAlias entity : entities)
		{
			grouped.computeIfAbsent(aliasKey(entity.slotType(), entity.tokens()), _ -> new ArrayList<>()).add(entity);
		}
		grouped.replaceAll((_, values) -> values.stream().sorted(java.util.Comparator.comparing(value -> value.reference().namespace() + ':' + value.reference().key())).toList());
		return Map.copyOf(grouped);
	}

	private static Map<SlotType, Map<Integer, List<EntityAlias>>> groupFuzzyEntities(List<EntityAlias> entities)
	{
		final EnumMap<SlotType, Map<Integer, List<EntityAlias>>> grouped = new EnumMap<>(SlotType.class);
		for (EntityAlias entity : entities)
		{
			if (entity.tokens().size() != 1)
			{
				continue;
			}
			final int length = entity.tokens().getFirst().codePointCount(0, entity.tokens().getFirst().length());
			final Map<Integer, List<EntityAlias>> lengths = grouped.computeIfAbsent(entity.slotType(), _ -> new TreeMap<>());
			lengths.computeIfAbsent(length, _ -> new ArrayList<>()).add(entity);
		}
		final EnumMap<SlotType, Map<Integer, List<EntityAlias>>> immutable = new EnumMap<>(SlotType.class);
		grouped.forEach((slot, lengths) ->
		{
			lengths.replaceAll((_, values) -> values.stream().sorted(java.util.Comparator.comparing(EntityAlias::phrase).thenComparing(value -> value.reference().key())).toList());
			immutable.put(slot, Map.copyOf(lengths));
		});
		return Map.copyOf(immutable);
	}

	private static Map<String, List<ContextAlias>> groupContexts(List<ContextAlias> contexts)
	{
		final Map<String, List<ContextAlias>> result = new TreeMap<>();
		for (ContextAlias context : contexts)
		{
			result.computeIfAbsent(aliasKey(context.slotType(), context.tokens()), _ -> new ArrayList<>()).add(context);
		}
		result.replaceAll((_, values) -> List.copyOf(values));
		return Map.copyOf(result);
	}

	private static List<String> phrase(String value, NormalizationPolicy policy, Limits limits)
	{
		final List<String> result = PhantomSemanticNormalizer.packPhrase(value, policy, 8, limits.maxTokenCodePoints());
		if (result.isEmpty() || (result.size() > 8))
		{
			throw new IllegalArgumentException("Semantic alias phrase token count is outside bounds.");
		}
		return result;
	}

	private static String singlePhraseToken(String value, NormalizationPolicy policy, Limits limits)
	{
		final List<String> tokens = phrase(value, policy, limits);
		if (tokens.size() != 1)
		{
			throw new IllegalArgumentException("Semantic pack token must normalize to exactly one word.");
		}
		return tokens.getFirst();
	}

	static String aliasKey(SlotType slot, List<String> tokens)
	{
		return slot.name() + '\u0000' + String.join(" ", tokens);
	}

	private static byte[] readBounded(Path path, int maximum, String label) throws Exception
	{
		Objects.requireNonNull(path, label + " path must not be null.");
		final long size = Files.size(path);
		if ((size <= 0) || (size > maximum))
		{
			throw new IllegalArgumentException(label + " byte size is outside bounds.");
		}
		final byte[] bytes = Files.readAllBytes(path);
		if (bytes.length != size)
		{
			throw new IllegalArgumentException(label + " changed while it was read.");
		}
		return bytes;
	}

	private static String decodeUtf8Strict(byte[] bytes, String label)
	{
		try
		{
			return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		}
		catch (CharacterCodingException exception)
		{
			throw new IllegalArgumentException(label + " is not strict UTF-8.", exception);
		}
	}

	private static String sha256(byte[] bytes) throws Exception
	{
		return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static List<Element> children(Element parent, String expectedName)
	{
		final List<Element> result = childElements(parent);
		for (Element child : result)
		{
			if (!expectedName.equals(child.getTagName()))
			{
				throw new IllegalArgumentException("Unknown semantic pack element: " + child.getTagName());
			}
		}
		return result;
	}

	private static List<Element> childElements(Element parent)
	{
		final List<Element> result = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling())
		{
			if (child instanceof Element element)
			{
				result.add(element);
			}
			else if ((child.getNodeType() == Node.TEXT_NODE) && !child.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Unexpected text in semantic pack.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, List<String> attributes)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()))
		{
			throw new IllegalArgumentException("Invalid semantic pack element: " + name);
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute) || element.getAttribute(attribute).isBlank())
			{
				throw new IllegalArgumentException("Missing semantic pack attribute: " + name + '.' + attribute);
			}
		}
	}

	private static int integer(Element element, String attribute, int minimum, int maximum)
	{
		final String value = element.getAttribute(attribute);
		if (!value.matches("0|[1-9][0-9]*"))
		{
			throw new IllegalArgumentException("Invalid semantic pack integer: " + attribute);
		}
		final int result;
		try
		{
			result = Integer.parseInt(value);
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Semantic pack integer overflow: " + attribute, exception);
		}
		if ((result < minimum) || (result > maximum))
		{
			throw new IllegalArgumentException("Semantic pack integer is outside bounds: " + attribute);
		}
		return result;
	}

	private static boolean strictBoolean(String value)
	{
		if ("true".equals(value))
		{
			return true;
		}
		if ("false".equals(value))
		{
			return false;
		}
		throw new IllegalArgumentException("Semantic pack boolean is not strict.");
	}

	public String id()
	{
		return _id;
	}

	public int version()
	{
		return _version;
	}

	public Limits limits()
	{
		return _limits;
	}

	public NormalizationPolicy normalization()
	{
		return _normalization;
	}

	public TypoPolicy typoPolicy()
	{
		return _typoPolicy;
	}

	public boolean isFiller(String canonicalToken)
	{
		return _fillers.contains(canonicalToken);
	}

	public LexicalAlias lexicalAlias(String token)
	{
		return _lexicalAliases.get(token);
	}

	public SlotDefinition slot(SlotType type)
	{
		return _slots.get(type);
	}

	public IntentDefinition intent(String key)
	{
		return _intents.get(key);
	}

	public Map<String, IntentDefinition> intents()
	{
		return _intents;
	}

	public List<PatternDefinition> exactPatterns(String firstLiteral)
	{
		return _patternsByFirstLiteral.getOrDefault(firstLiteral, List.of());
	}

	public List<PatternDefinition> patternsWithFirstLength(int length)
	{
		return _patternsByFirstLength.getOrDefault(length, List.of());
	}

	public List<EntityAlias> exactEntities(SlotType slot, List<String> tokens)
	{
		return _entityAliases.getOrDefault(aliasKey(slot, tokens), List.of());
	}

	public List<EntityAlias> fuzzyEntities(SlotType slot, int length)
	{
		return _fuzzyEntityAliases.getOrDefault(slot, Map.of()).getOrDefault(length, List.of());
	}

	public List<ContextAlias> contexts(SlotType slot, List<String> tokens)
	{
		return _contextAliases.getOrDefault(aliasKey(slot, tokens), List.of());
	}

	public Set<String> reasons()
	{
		return _reasons;
	}

	public List<CorpusCase> corpus()
	{
		return _corpus;
	}

	public Hashes authorityHashes()
	{
		return _authorityHashes;
	}

	public String packHash()
	{
		return _packHash;
	}

	public String corpusHash()
	{
		return _corpusHash;
	}

	public int fillerCount()
	{
		return _fillers.size();
	}

	public int lexicalAliasCount()
	{
		return _lexicalAliases.size();
	}

	public int patternCount()
	{
		return _patternsByFirstLiteral.values().stream().mapToInt(List::size).sum();
	}

	public int entityAliasCount()
	{
		return _entityAliases.values().stream().mapToInt(List::size).sum();
	}

	private record ParseIntents(Map<String, IntentDefinition> intents, List<PatternDefinition> patterns)
	{
	}
}

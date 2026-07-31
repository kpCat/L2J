/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.network.enums.ChatType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Strict, XXE-safe and content-addressed conversation facts. */
public final class PhantomConversationCatalog
{
	private static final int MAX_BYTES = 256 * 1024;
	private static final Set<String> REQUIRED_STYLES = Set.of("neutral", "warm", "cold", "cautious", "terse");
	private static final Set<String> REQUIRED_ACTS = Set.of("clarify.intent", "clarify.target_player", "clarify.entity", "clarify.party_role", "clarify.location", "clarify.quantity", "clarify.complexity", "ack.action_proposed", "ack.query_proposed", "ack.accepted", "ack.refused", "no_response.cooldown", "no_response.not_addressed", "no_response.unsupported");
	private static final Set<String> REQUIRED_CATEGORIES = Set.of("private", "party-election", "local-trade-address", "clarification", "social-style", "cooldown", "semantic-rejection", "proposal-mapping", "duplicate", "restart");

	public record Limits(int ingressQueue, int openBatches, int observersPerMessage, int operationsPerPulse, int sessionsPerProfile, int recentHashes, int pendingSlots, int templatesPerAct, int renderedCodePoints, int renderedUtf8Bytes, int evidence, int proposalSlots, int statePayload, int cacheEntries, int aggregationPulses, int clarificationTtlMinutes)
	{
		public Limits
		{
			if ((ingressQueue < 1) || (ingressQueue > 1024) || (openBatches < 1) || (openBatches > 256) || (observersPerMessage < 1) || (observersPerMessage > 32) || (operationsPerPulse < 1) || (operationsPerPulse > 32) || (sessionsPerProfile < 1) || (sessionsPerProfile > 8) || (recentHashes < 1) || (recentHashes > 8) || (pendingSlots < 1) || (pendingSlots > 4) || (templatesPerAct < 1) || (templatesPerAct > 8) || (renderedCodePoints < 1) || (renderedCodePoints > 100) || (renderedUtf8Bytes < 1) || (renderedUtf8Bytes > 400) || (evidence < 1) || (evidence > 16) || (proposalSlots < 1) || (proposalSlots > 8) || (statePayload < 1) || (statePayload > 4096) || (cacheEntries < 16) || (cacheEntries > 10000) || (aggregationPulses < 1) || (aggregationPulses > 4) || (clarificationTtlMinutes < 1) || (clarificationTtlMinutes > 1440))
			{
				throw new IllegalArgumentException("Conversation catalog limits are outside hard bounds.");
			}
		}
	}

	public record ChannelPolicy(ChatType channel, int cooldownMinutes)
	{
		public ChannelPolicy
		{
			if ((channel == null) || (cooldownMinutes < 0) || (cooldownMinutes > 1440))
			{
				throw new IllegalArgumentException("Conversation channel policy is invalid.");
			}
		}
	}

	public record StyleBand(String key, int minimumWarmth, int maximumWarmth, int minimumEscalation, int maximumInvitePreference, boolean suppressAcknowledgement)
	{
		public StyleBand
		{
			key = PhantomConversationModel.requireKey(key, "Conversation style key");
			if ((minimumWarmth < -3000) || (maximumWarmth > 3000) || (minimumWarmth > maximumWarmth) || (minimumEscalation < -3000) || (minimumEscalation > 3000) || (maximumInvitePreference < -3000) || (maximumInvitePreference > 3000))
			{
				throw new IllegalArgumentException("Conversation style band is invalid.");
			}
		}
	}

	public record ResponseAct(String key, Map<String, List<String>> templates)
	{
		public ResponseAct
		{
			key = PhantomConversationModel.requireKey(key, "Conversation response act");
			final Map<String, List<String>> copy = new LinkedHashMap<>();
			for (var entry : templates.entrySet())
			{
				final String style = PhantomConversationModel.requireKey(entry.getKey(), "Template style");
				final List<String> values = List.copyOf(entry.getValue());
				if (values.isEmpty() || (values.size() > 8))
				{
					throw new IllegalArgumentException("Conversation template count is invalid.");
				}
				for (String value : values)
				{
					validateTemplate(value);
				}
				copy.put(style, values);
			}
			if (!copy.containsKey("neutral"))
			{
				throw new IllegalArgumentException("Every response act requires a neutral template.");
			}
			templates = Map.copyOf(copy);
		}
	}

	public record ProposalMapping(String intentKey, String proposalKey, boolean query, int ttlMinutes)
	{
		public ProposalMapping
		{
			intentKey = PhantomConversationModel.requireKey(intentKey, "Proposal intent");
			proposalKey = PhantomConversationModel.requireKey(proposalKey, "Proposal key");
			if ((ttlMinutes < 1) || (ttlMinutes > 1440))
			{
				throw new IllegalArgumentException("Proposal TTL is invalid.");
			}
		}
	}

	private final String _id;
	private final int _version;
	private final String _hash;
	private final String _corpusHash;
	private final int _corpusCases;
	private final Limits _limits;
	private final Map<ChatType, ChannelPolicy> _channels;
	private final Map<String, StyleBand> _styles;
	private final Map<String, ResponseAct> _acts;
	private final Map<String, ProposalMapping> _mappings;

	private PhantomConversationCatalog(String id, int version, String hash, String corpusHash, int corpusCases, Limits limits, Map<ChatType, ChannelPolicy> channels, Map<String, StyleBand> styles, Map<String, ResponseAct> acts, Map<String, ProposalMapping> mappings)
	{
		_id = PhantomConversationModel.requireKey(id, "Conversation catalog id");
		_version = version;
		_hash = PhantomConversationModel.requireHash(hash, "Conversation catalog hash");
		_corpusHash = PhantomConversationModel.requireHash(corpusHash, "Conversation corpus hash");
		_corpusCases = corpusCases;
		_limits = Objects.requireNonNull(limits);
		_channels = Map.copyOf(channels);
		_styles = Map.copyOf(styles);
		_acts = Map.copyOf(acts);
		_mappings = Map.copyOf(mappings);
	}

	public static PhantomConversationCatalog load(Path xmlPath, Path corpusPath)
	{
		try
		{
			final byte[] xml = Files.readAllBytes(xmlPath);
			final byte[] corpus = Files.readAllBytes(corpusPath);
			if ((xml.length == 0) || (xml.length > MAX_BYTES) || (corpus.length == 0) || (corpus.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Conversation data file size is outside bounds.");
			}
			strictUtf8(xml);
			final String corpusText = strictUtf8(corpus);
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml)).getDocumentElement();
			requireElement(root, "conversationCatalog", Set.of("id", "version"));
			final List<Element> sections = children(root);
			if ((sections.size() != 6) || !List.of("limits", "channels", "styles", "responseActs", "proposalMappings", "election").equals(sections.stream().map(Element::getTagName).toList()))
			{
				throw new IllegalArgumentException("Conversation catalog sections are not exact.");
			}
			final Limits limits = parseLimits(sections.get(0));
			final Map<ChatType, ChannelPolicy> channels = parseChannels(sections.get(1));
			final Map<String, StyleBand> styles = parseStyles(sections.get(2));
			final Map<String, ResponseAct> acts = parseActs(sections.get(3), styles.keySet(), limits.templatesPerAct());
			final Map<String, ProposalMapping> mappings = parseMappings(sections.get(4));
			requireElement(sections.get(5), "election", Set.of("private", "party", "localTrade"));
			if (!sections.get(5).getAttribute("private").equals("exact-recipient") || !sections.get(5).getAttribute("party").equals("leader-or-min-profile") || !sections.get(5).getAttribute("localTrade").equals("unique-exact-vocative"))
			{
				throw new IllegalArgumentException("Conversation election policies are not recognized.");
			}
			final int corpusCases = validateCorpus(corpusText);
			return new PhantomConversationCatalog(root.getAttribute("id"), strictInt(root.getAttribute("version"), 1, 65535), hash(xml), hash(corpus), corpusCases, limits, channels, styles, acts, mappings);
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load conversation data.", exception);
		}
	}

	public String id()
	{
		return _id;
	}

	public int version()
	{
		return _version;
	}

	public String hash()
	{
		return _hash;
	}

	public String corpusHash()
	{
		return _corpusHash;
	}

	public int corpusCases()
	{
		return _corpusCases;
	}

	public Limits limits()
	{
		return _limits;
	}

	public boolean supports(ChatType channel)
	{
		return _channels.containsKey(channel);
	}

	public ChannelPolicy channel(ChatType channel)
	{
		final ChannelPolicy policy = _channels.get(channel);
		if (policy == null)
		{
			throw new IllegalArgumentException("Unsupported conversation channel.");
		}
		return policy;
	}

	public ProposalMapping mapping(String intentKey)
	{
		return _mappings.get(intentKey);
	}

	public String template(String actKey, String styleKey, long selector)
	{
		final ResponseAct act = _acts.get(actKey);
		if (act == null)
		{
			throw new IllegalArgumentException("Unknown conversation response act.");
		}
		final List<String> templates = act.templates().getOrDefault(styleKey, act.templates().get("neutral"));
		return templates.get(Math.floorMod(Long.hashCode(selector), templates.size()));
	}

	public String style(int warmth, int escalation, int invitePreference)
	{
		if (escalation >= _styles.get("cautious").minimumEscalation())
		{
			return "cautious";
		}
		if (invitePreference <= _styles.get("terse").maximumInvitePreference())
		{
			return "terse";
		}
		if (warmth >= _styles.get("warm").minimumWarmth())
		{
			return "warm";
		}
		if (warmth <= _styles.get("cold").maximumWarmth())
		{
			return "cold";
		}
		return "neutral";
	}

	public boolean suppresses(String style)
	{
		return _styles.getOrDefault(style, _styles.get("neutral")).suppressAcknowledgement();
	}

	private static Limits parseLimits(Element element)
	{
		final Set<String> attributes = Set.of("ingressQueue", "openBatches", "observersPerMessage", "operationsPerPulse", "sessionsPerProfile", "recentHashes", "pendingSlots", "templatesPerAct", "renderedCodePoints", "renderedUtf8Bytes", "evidence", "proposalSlots", "statePayload", "cacheEntries", "aggregationPulses", "clarificationTtlMinutes");
		requireElement(element, "limits", attributes);
		final int[] values = attributes.stream().sorted().mapToInt(name -> strictInt(element.getAttribute(name), 1, 10000)).toArray();
		final Map<String, Integer> byName = new HashMap<>();
		int index = 0;
		for (String name : attributes.stream().sorted().toList())
		{
			byName.put(name, values[index++]);
		}
		return new Limits(byName.get("ingressQueue"), byName.get("openBatches"), byName.get("observersPerMessage"), byName.get("operationsPerPulse"), byName.get("sessionsPerProfile"), byName.get("recentHashes"), byName.get("pendingSlots"), byName.get("templatesPerAct"), byName.get("renderedCodePoints"), byName.get("renderedUtf8Bytes"), byName.get("evidence"), byName.get("proposalSlots"), byName.get("statePayload"), byName.get("cacheEntries"), byName.get("aggregationPulses"), byName.get("clarificationTtlMinutes"));
	}

	private static Map<ChatType, ChannelPolicy> parseChannels(Element parent)
	{
		requireElement(parent, "channels", Set.of());
		final Map<ChatType, ChannelPolicy> result = new LinkedHashMap<>();
		for (Element element : children(parent))
		{
			requireElement(element, "channel", Set.of("type", "cooldownMinutes"));
			final ChatType channel = ChatType.valueOf(element.getAttribute("type"));
			if (!Set.of(ChatType.GENERAL, ChatType.WHISPER, ChatType.PARTY, ChatType.TRADE).contains(channel) || (result.put(channel, new ChannelPolicy(channel, strictInt(element.getAttribute("cooldownMinutes"), 0, 1440))) != null))
			{
				throw new IllegalArgumentException("Conversation channel is duplicate or unsupported.");
			}
		}
		if (!result.keySet().equals(Set.of(ChatType.GENERAL, ChatType.WHISPER, ChatType.PARTY, ChatType.TRADE)))
		{
			throw new IllegalArgumentException("Conversation channel policies are incomplete.");
		}
		return result;
	}

	private static Map<String, StyleBand> parseStyles(Element parent)
	{
		requireElement(parent, "styles", Set.of());
		final Map<String, StyleBand> result = new LinkedHashMap<>();
		for (Element element : children(parent))
		{
			requireElement(element, "style", Set.of("key", "minimumWarmth", "maximumWarmth", "minimumEscalation", "maximumInvitePreference", "suppressAcknowledgement"));
			final StyleBand style = new StyleBand(element.getAttribute("key"), strictInt(element.getAttribute("minimumWarmth"), -3000, 3000), strictInt(element.getAttribute("maximumWarmth"), -3000, 3000), strictInt(element.getAttribute("minimumEscalation"), -3000, 3000), strictInt(element.getAttribute("maximumInvitePreference"), -3000, 3000), strictBoolean(element.getAttribute("suppressAcknowledgement")));
			if (result.put(style.key(), style) != null)
			{
				throw new IllegalArgumentException("Duplicate conversation style.");
			}
		}
		if (!result.keySet().equals(REQUIRED_STYLES))
		{
			throw new IllegalArgumentException("Conversation styles are incomplete.");
		}
		return result;
	}

	private static Map<String, ResponseAct> parseActs(Element parent, Set<String> styles, int maximumTemplates)
	{
		requireElement(parent, "responseActs", Set.of());
		final Map<String, Map<String, List<String>>> grouped = new LinkedHashMap<>();
		for (Element actElement : children(parent))
		{
			requireElement(actElement, "act", Set.of("key"));
			final String key = PhantomConversationModel.requireKey(actElement.getAttribute("key"), "Response act");
			if (grouped.containsKey(key))
			{
				throw new IllegalArgumentException("Duplicate conversation response act.");
			}
			final Map<String, List<String>> byStyle = new LinkedHashMap<>();
			for (Element template : children(actElement))
			{
				requireElement(template, "template", Set.of("style", "text"));
				final String style = template.getAttribute("style");
				if (!styles.contains(style))
				{
					throw new IllegalArgumentException("Unknown conversation template style.");
				}
				byStyle.computeIfAbsent(style, _ -> new ArrayList<>()).add(template.getAttribute("text"));
				if (byStyle.get(style).size() > maximumTemplates)
				{
					throw new IllegalArgumentException("Conversation template count exceeds catalog limit.");
				}
			}
			grouped.put(key, byStyle);
		}
		if (!grouped.keySet().containsAll(REQUIRED_ACTS))
		{
			throw new IllegalArgumentException("Conversation response acts are incomplete.");
		}
		final Map<String, ResponseAct> result = new LinkedHashMap<>();
		grouped.forEach((key, templates) -> result.put(key, new ResponseAct(key, templates)));
		return result;
	}

	private static Map<String, ProposalMapping> parseMappings(Element parent)
	{
		requireElement(parent, "proposalMappings", Set.of());
		final Map<String, ProposalMapping> result = new LinkedHashMap<>();
		for (Element element : children(parent))
		{
			requireElement(element, "mapping", Set.of("intent", "proposal", "kind", "ttlMinutes"));
			final String kind = element.getAttribute("kind");
			if (!kind.equals("action") && !kind.equals("query"))
			{
				throw new IllegalArgumentException("Conversation proposal kind is invalid.");
			}
			final ProposalMapping mapping = new ProposalMapping(element.getAttribute("intent"), element.getAttribute("proposal"), kind.equals("query"), strictInt(element.getAttribute("ttlMinutes"), 1, 1440));
			if (result.put(mapping.intentKey(), mapping) != null)
			{
				throw new IllegalArgumentException("Duplicate conversation intent mapping.");
			}
		}
		return result;
	}

	private static int validateCorpus(String text)
	{
		final String[] lines = text.split("\\r?\\n", -1);
		if ((lines.length < 129) || !lines[0].equals("case_id\tcategory\tchannel\tinput\texpected_act\texpected_style\texpected_proposal"))
		{
			throw new IllegalArgumentException("Conversation corpus header or case count is invalid.");
		}
		final Set<String> ids = new HashSet<>();
		final Set<String> categories = new HashSet<>();
		int count = 0;
		for (int index = 1; index < lines.length; index++)
		{
			if (lines[index].isEmpty())
			{
				if (index != (lines.length - 1))
				{
					throw new IllegalArgumentException("Conversation corpus contains an empty row.");
				}
				continue;
			}
			final String[] columns = lines[index].split("\\t", -1);
			if ((columns.length != 7) || !ids.add(columns[0]) || !REQUIRED_CATEGORIES.contains(columns[1]) || !Set.of("GENERAL", "WHISPER", "PARTY", "TRADE").contains(columns[2]) || columns[3].isBlank())
			{
				throw new IllegalArgumentException("Conversation corpus row is invalid.");
			}
			categories.add(columns[1]);
			count++;
		}
		if ((count < 128) || !categories.equals(REQUIRED_CATEGORIES))
		{
			throw new IllegalArgumentException("Conversation corpus coverage is incomplete.");
		}
		return count;
	}

	private static List<Element> children(Element parent)
	{
		final List<Element> result = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if (node instanceof Element element)
			{
				result.add(element);
			}
			else if ((node.getNodeType() == Node.TEXT_NODE) && !node.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Conversation XML contains unexpected text.");
			}
		}
		return result;
	}

	private static void requireElement(Element element, String name, Set<String> attributes)
	{
		if (!element.getTagName().equals(name) || (element.getAttributes().getLength() != attributes.size()))
		{
			throw new IllegalArgumentException("Conversation XML element is not exact: " + name);
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute))
			{
				throw new IllegalArgumentException("Conversation XML attribute is missing: " + attribute);
			}
		}
	}

	private static int strictInt(String value, int minimum, int maximum)
	{
		if ((value == null) || !value.matches("-?(0|[1-9][0-9]{0,8})"))
		{
			throw new IllegalArgumentException("Conversation integer is invalid.");
		}
		final int result = Integer.parseInt(value);
		if ((result < minimum) || (result > maximum))
		{
			throw new IllegalArgumentException("Conversation integer is outside bounds.");
		}
		return result;
	}

	private static boolean strictBoolean(String value)
	{
		if (!value.equals("true") && !value.equals("false"))
		{
			throw new IllegalArgumentException("Conversation boolean is invalid.");
		}
		return Boolean.parseBoolean(value);
	}

	private static void validateTemplate(String value)
	{
		if ((value == null) || value.isBlank() || (value.codePointCount(0, value.length()) > 100) || (value.getBytes(StandardCharsets.UTF_8).length > 400) || (value.indexOf(8) >= 0) || value.codePoints().anyMatch(Character::isISOControl) || value.contains("<") || value.contains(">"))
		{
			throw new IllegalArgumentException("Conversation template text is invalid.");
		}
	}

	private static String strictUtf8(byte[] bytes) throws Exception
	{
		return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
	}

	private static String hash(byte[] bytes) throws Exception
	{
		return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
}

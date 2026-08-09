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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.QueryFact;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionPort.QueryResult;

/** Strict, XXE-safe and content-addressed conversation execution policy. */
public final class PhantomConversationExecutionCatalog
{
	public enum Kind
	{
		QUERY,
		GOAL,
		PARTY_RESPONSE,
		DEFERRED
	}

	public record Limits(int executionQueue, int operationsPerPulse, int recoveryPage, int entries, int receipts, int textUtf8Bytes, int executionTtlMinutes, int outboundRetries, int replayHorizonMinutes)
	{
		public Limits
		{
			if ((executionQueue < 1) || (executionQueue > 4096) || (operationsPerPulse < 1) || (operationsPerPulse > 32) || (recoveryPage < 1) || (recoveryPage > 256) || (entries != 4) || (receipts != 16) || (textUtf8Bytes < 1) || (textUtf8Bytes > PhantomConversationExecutionModel.MAX_TEXT_BYTES) || (executionTtlMinutes < 1) || (executionTtlMinutes > 1440) || (outboundRetries < 1) || (outboundRetries > 3) || (replayHorizonMinutes < executionTtlMinutes) || (replayHorizonMinutes > 10080))
			{
				throw new IllegalArgumentException("Conversation execution limits are invalid.");
			}
		}
	}

	public record ProposalPolicy(String key, Kind kind, String goalType, Set<ChatType> channels, Set<String> requiredSlots, Set<String> targetNamespaces, boolean counterpartRequired)
	{
		public ProposalPolicy
		{
			key = PhantomConversationExecutionModel.requireKey(key, "Execution proposal");
			if (kind == null)
			{
				throw new IllegalArgumentException("Execution proposal kind is absent.");
			}
			goalType = goalType == null || goalType.isEmpty() ? null : PhantomConversationExecutionModel.requireKey(goalType, "Execution goal type");
			if ((kind == Kind.GOAL) != (goalType != null) && !((kind == Kind.PARTY_RESPONSE) && key.equals("party.accept") && (goalType != null)))
			{
				throw new IllegalArgumentException("Execution proposal goal mapping is inconsistent.");
			}
			channels = Set.copyOf(channels);
			requiredSlots = Set.copyOf(requiredSlots);
			targetNamespaces = Set.copyOf(targetNamespaces);
			if (channels.isEmpty() || !Set.of(ChatType.GENERAL, ChatType.WHISPER, ChatType.PARTY, ChatType.TRADE).containsAll(channels) || (requiredSlots.size() > PhantomConversationExecutionModel.MAX_ARGUMENTS) || (targetNamespaces.size() > 2))
			{
				throw new IllegalArgumentException("Execution proposal authorization bounds are invalid.");
			}
			requiredSlots.forEach(slot -> PhantomConversationExecutionModel.requireKey(slot, "Execution required slot"));
			targetNamespaces.forEach(namespace -> PhantomConversationExecutionModel.requireKey(namespace, "Execution target namespace"));
		}

		public boolean authorizes(ExecutionEntry entry)
		{
			if (!channels.contains(entry.channel()) || (counterpartRequired && !Set.of("character.object", "profile").contains(entry.counterpart().namespace())) || (!targetNamespaces.isEmpty() && ((entry.target() == null) || !targetNamespaces.contains(entry.target().namespace()))))
			{
				return false;
			}
			final Set<String> present = entry.arguments().stream().map(PhantomConversationExecutionModel.Argument::key).collect(java.util.stream.Collectors.toSet());
			return requiredSlots.isEmpty() || requiredSlots.stream().anyMatch(present::contains);
		}
	}

	private static final int MAX_BYTES = 256 * 1024;
	private static final Set<ChatType> EXECUTION_CHANNELS = Set.of(ChatType.GENERAL, ChatType.WHISPER, ChatType.PARTY, ChatType.TRADE);
	private static final Map<String, ExpectedProposal> REQUIRED_PROPOSALS = Map.ofEntries( //
		Map.entry("party.role.query", new ExpectedProposal(Kind.QUERY, null, Set.of(), Set.of())), //
		Map.entry("entity.locate", new ExpectedProposal(Kind.QUERY, null, Set.of("npc", "topology.node", "content"), Set.of())), //
		Map.entry("item.acquire", new ExpectedProposal(Kind.QUERY, null, Set.of("item"), Set.of())), //
		Map.entry("item.source", new ExpectedProposal(Kind.QUERY, null, Set.of("item"), Set.of())), //
		Map.entry("content.requirements", new ExpectedProposal(Kind.QUERY, null, Set.of("content"), Set.of())), //
		Map.entry("farming.conflict.query", new ExpectedProposal(Kind.QUERY, null, Set.of(), Set.of())), //
		Map.entry("party.invite", new ExpectedProposal(Kind.GOAL, "party.form", Set.of("target.player"), Set.of("character.object", "profile"))), //
		Map.entry("party.leave", new ExpectedProposal(Kind.GOAL, "party.leave", Set.of(), Set.of())), //
		Map.entry("party.travel", new ExpectedProposal(Kind.GOAL, "party.travel", Set.of("location", "topology.node"), Set.of())), //
		Map.entry("party.accept", new ExpectedProposal(Kind.PARTY_RESPONSE, "party.join", Set.of(), Set.of())), //
		Map.entry("party.refuse", new ExpectedProposal(Kind.PARTY_RESPONSE, null, Set.of(), Set.of())), //
		Map.entry("party.support", new ExpectedProposal(Kind.DEFERRED, null, Set.of(), Set.of())), //
		Map.entry("party.assist", new ExpectedProposal(Kind.DEFERRED, null, Set.of(), Set.of())), //
		Map.entry("party.regroup", new ExpectedProposal(Kind.DEFERRED, null, Set.of(), Set.of())));
	private static final Set<String> REQUIRED_RESPONSE_ACTS = Set.of("ack.accepted", "ack.action_proposed", "ack.query_proposed", "ack.refused", "clarify.complexity", "clarify.entity", "clarify.intent", "clarify.location", "clarify.party_role", "clarify.quantity", "clarify.target_player", "no_response.cooldown", "no_response.not_addressed", "no_response.unsupported");
	private static final Set<String> REQUIRED_STYLES = Set.of("neutral", "warm", "cold", "cautious", "terse");
	private static final Set<String> REQUIRED_REASONS = Set.of("action.deferred", "execution.expired", "execution.failed", "execution.prepared", "goal.busy", "goal.invalid", "goal.submitted", "outbound.invalid", "party.accepted", "party.refused", "party.stale", "query.ambiguous", "query.not_found", "query.ok");
	private static final Set<String> REQUIRED_FACT_LABELS = Set.of("content.capability", "content.party_max", "content.party_min", "content.reference", "entity.reference", "farming.agreement", "farming.alternative", "farming.claim_status", "farming.counterpart", "farming.counterpart_remaining", "farming.escalation", "farming.negotiation_act", "farming.remaining", "farming.resource", "item.reference", "item.source", "party.group_generation", "party.role", "party.vacancy", "topology.instance", "topology.reference", "topology.x", "topology.y", "topology.z");
	private final String _hash;
	private final Limits _limits;
	private final Map<String, ProposalPolicy> _proposals;
	private final List<String> _responseActs;
	private final List<String> _styles;
	private final List<String> _reasons;
	private final Map<String, String> _factLabels;
	private final Map<String, Map<String, String>> _templates;

	private PhantomConversationExecutionCatalog(String hash, Limits limits, Map<String, ProposalPolicy> proposals, List<String> responseActs, List<String> styles, Map<String, String> factLabels, Map<String, Map<String, String>> templates)
	{
		_hash = hash;
		_limits = limits;
		_proposals = Map.copyOf(proposals);
		_responseActs = List.copyOf(responseActs);
		_styles = List.copyOf(styles);
		_factLabels = Map.copyOf(factLabels);
		_templates = Map.copyOf(templates);
		_reasons = templates.keySet().stream().sorted().toList();
	}

	public static PhantomConversationExecutionCatalog load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length < 1) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Conversation execution policy size is invalid.");
			}
			strictUtf8(bytes);
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			require(root, "conversationExecutionPolicy", Set.of("id", "version"));
			if (!root.getAttribute("id").equals("high-five-ru-conversation-execution-v1") || !root.getAttribute("version").equals("1"))
			{
				throw new IllegalArgumentException("Conversation execution policy identity is invalid.");
			}
			final List<Element> sections = children(root);
			if (!sections.stream().map(Element::getTagName).toList().equals(List.of("limits", "proposals", "responseActs", "styles", "factLabels", "results")))
			{
				throw new IllegalArgumentException("Conversation execution policy sections are not exact.");
			}
			final Limits limits = limits(sections.get(0));
			final Map<String, ProposalPolicy> proposals = proposals(sections.get(1));
			final List<String> acts = symbols(sections.get(2), "responseActs", "act");
			if (!Set.copyOf(acts).equals(REQUIRED_RESPONSE_ACTS))
			{
				throw new IllegalArgumentException("Conversation execution response acts are incomplete.");
			}
			final List<String> styles = symbols(sections.get(3), "styles", "style");
			if (!styles.containsAll(REQUIRED_STYLES))
			{
				throw new IllegalArgumentException("Conversation execution styles are incomplete.");
			}
			final Map<String, String> factLabels = factLabels(sections.get(4));
			final Map<String, Map<String, String>> results = results(sections.get(5), styles);
			if (!results.keySet().equals(REQUIRED_REASONS))
			{
				throw new IllegalArgumentException("Conversation execution result policy is incomplete.");
			}
			return new PhantomConversationExecutionCatalog(hash(bytes), limits, proposals, acts, styles, factLabels, results);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load conversation execution policy.", exception);
		}
	}

	public String hash()
	{
		return _hash;
	}

	public Limits limits()
	{
		return _limits;
	}

	public ProposalPolicy proposal(String key)
	{
		return _proposals.get(key);
	}

	public String render(String reason, String style, String facts)
	{
		final Map<String, String> templates = _templates.get(reason);
		if (templates == null)
		{
			throw new IllegalArgumentException("Unknown execution result reason.");
		}
		final String safeFacts = (facts == null) || facts.isBlank() ? "нет" : PhantomConversationExecutionModel.requireUtf8(facts, 128, "Execution result facts");
		final String text = templates.getOrDefault(style, templates.get("neutral")).replace("{facts}", safeFacts);
		return PhantomConversationExecutionModel.requireUtf8(text, _limits.textUtf8Bytes(), "Rendered execution result");
	}

	public String renderQuery(String reason, String style, QueryResult result)
	{
		return render(reason, style, renderFacts(result));
	}

	private String renderFacts(QueryResult result)
	{
		if (result.facts().isEmpty())
		{
			return null;
		}
		final List<String> rendered = new ArrayList<>();
		for (QueryFact fact : result.facts())
		{
			final String baseKey = fact.key().replaceFirst("\\.\\d+$", "");
			final String label = _factLabels.get(baseKey);
			if (label == null)
			{
				throw new IllegalArgumentException("Unknown query fact label: " + fact.key());
			}
			final String value = fact.reference() != null ? fact.reference().namespace() + ':' + fact.reference().key() : fact.number() != null ? Long.toString(fact.number()) : fact.value();
			final String item = label + '=' + value;
			final String candidate = rendered.isEmpty() ? item : String.join(", ", rendered) + ", " + item;
			if (candidate.getBytes(StandardCharsets.UTF_8).length > 128)
			{
				break;
			}
			rendered.add(item);
		}
		return rendered.isEmpty() ? null : String.join(", ", rendered);
	}

	int responseActId(String key)
	{
		return id(_responseActs, key, "response act");
	}

	String responseAct(int id)
	{
		return value(_responseActs, id, "response act");
	}

	int styleId(String key)
	{
		return id(_styles, key, "style");
	}

	String style(int id)
	{
		return value(_styles, id, "style");
	}

	int proposalId(String key)
	{
		if (key == null)
		{
			return 0;
		}
		return id(_proposals.keySet().stream().sorted().toList(), key, "proposal");
	}

	String proposal(int id)
	{
		return id == 0 ? null : value(_proposals.keySet().stream().sorted().toList(), id, "proposal");
	}

	int reasonId(String key)
	{
		return id(_reasons, key, "reason");
	}

	String reason(int id)
	{
		return value(_reasons, id, "reason");
	}

	private static Limits limits(Element element)
	{
		final Set<String> names = Set.of("executionQueue", "operationsPerPulse", "recoveryPage", "entries", "receipts", "textUtf8Bytes", "executionTtlMinutes", "outboundRetries", "replayHorizonMinutes");
		require(element, "limits", names);
		return new Limits(integer(element, "executionQueue"), integer(element, "operationsPerPulse"), integer(element, "recoveryPage"), integer(element, "entries"), integer(element, "receipts"), integer(element, "textUtf8Bytes"), integer(element, "executionTtlMinutes"), integer(element, "outboundRetries"), integer(element, "replayHorizonMinutes"));
	}

	private static Map<String, ProposalPolicy> proposals(Element parent)
	{
		require(parent, "proposals", Set.of());
		final Map<String, ProposalPolicy> result = new LinkedHashMap<>();
		for (Element element : children(parent))
		{
			require(element, "proposal", Set.of("key", "kind", "goalType", "channels", "requiredSlots", "targetNamespaces", "counterpartRequired"));
			final ProposalPolicy policy = new ProposalPolicy(element.getAttribute("key"), Kind.valueOf(element.getAttribute("kind")), element.getAttribute("goalType"), enumSet(element.getAttribute("channels")), keySet(element.getAttribute("requiredSlots")), keySet(element.getAttribute("targetNamespaces")), booleanValue(element.getAttribute("counterpartRequired")));
			if (result.putIfAbsent(policy.key(), policy) != null)
			{
				throw new IllegalArgumentException("Duplicate execution proposal.");
			}
		}
		if (!result.keySet().equals(REQUIRED_PROPOSALS.keySet()))
		{
			throw new IllegalArgumentException("Conversation execution proposals are incomplete.");
		}
		for (var expected : REQUIRED_PROPOSALS.entrySet())
		{
			final ProposalPolicy policy = result.get(expected.getKey());
			final ExpectedProposal contract = expected.getValue();
			if ((policy.kind() != contract.kind()) || !java.util.Objects.equals(policy.goalType(), contract.goalType()) || !policy.channels().equals(EXECUTION_CHANNELS) || !policy.requiredSlots().equals(contract.requiredSlots()) || !policy.targetNamespaces().equals(contract.targetNamespaces()) || !policy.counterpartRequired())
			{
				throw new IllegalArgumentException("Conversation execution proposal contract is not exact: " + expected.getKey());
			}
		}
		return result;
	}

	private static Set<ChatType> enumSet(String value)
	{
		try
		{
			return java.util.Arrays.stream(value.split(",", -1)).map(ChatType::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
		}
		catch (RuntimeException exception)
		{
			throw new IllegalArgumentException("Execution proposal channels are invalid.", exception);
		}
	}

	private static Set<String> keySet(String value)
	{
		if (value.isEmpty())
		{
			return Set.of();
		}
		return java.util.Arrays.stream(value.split(",", -1)).map(key -> PhantomConversationExecutionModel.requireKey(key, "Execution required slot")).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private static boolean booleanValue(String value)
	{
		if (!value.equals("true") && !value.equals("false"))
		{
			throw new IllegalArgumentException("Execution proposal boolean is invalid.");
		}
		return Boolean.parseBoolean(value);
	}

	private record ExpectedProposal(Kind kind, String goalType, Set<String> requiredSlots, Set<String> targetNamespaces)
	{
	}

	private static List<String> symbols(Element parent, String parentName, String childName)
	{
		require(parent, parentName, Set.of());
		final List<String> values = new ArrayList<>();
		for (Element element : children(parent))
		{
			require(element, childName, Set.of("key"));
			values.add(PhantomConversationExecutionModel.requireKey(element.getAttribute("key"), "Execution symbol"));
		}
		final List<String> sorted = values.stream().sorted().distinct().toList();
		if (!values.equals(sorted) || values.isEmpty() || (values.size() > 254))
		{
			throw new IllegalArgumentException("Execution symbols must be sorted and unique.");
		}
		return sorted;
	}

	private static Map<String, Map<String, String>> results(Element parent, List<String> styles)
	{
		require(parent, "results", Set.of());
		final Set<String> attributes = new java.util.HashSet<>(styles);
		attributes.add("key");
		final Map<String, Map<String, String>> result = new LinkedHashMap<>();
		for (Element element : children(parent))
		{
			require(element, "result", attributes);
			final String key = PhantomConversationExecutionModel.requireKey(element.getAttribute("key"), "Execution result");
			final Map<String, String> templates = new HashMap<>();
			for (String style : styles)
			{
				final String template = PhantomConversationExecutionModel.requireUtf8(element.getAttribute(style), PhantomConversationExecutionModel.MAX_TEXT_BYTES, "Execution result template");
				if (template.indexOf("{facts}") != template.lastIndexOf("{facts}"))
				{
					throw new IllegalArgumentException("Execution template contains repeated facts placeholder.");
				}
				templates.put(style, template);
			}
			if (result.putIfAbsent(key, Map.copyOf(templates)) != null)
			{
				throw new IllegalArgumentException("Duplicate execution result.");
			}
		}
		if (result.isEmpty() || (result.size() > 254))
		{
			throw new IllegalArgumentException("Execution results are empty or oversized.");
		}
		return result.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new));
	}

	private static Map<String, String> factLabels(Element parent)
	{
		require(parent, "factLabels", Set.of());
		final Map<String, String> result = new LinkedHashMap<>();
		for (Element element : children(parent))
		{
			require(element, "label", Set.of("key", "text"));
			final String key = PhantomConversationExecutionModel.requireKey(element.getAttribute("key"), "Query fact label key");
			final String text = PhantomConversationExecutionModel.requireUtf8(element.getAttribute("text"), 32, "Query fact label");
			if (result.putIfAbsent(key, text) != null)
			{
				throw new IllegalArgumentException("Duplicate query fact label.");
			}
		}
		if (!result.keySet().equals(REQUIRED_FACT_LABELS) || !new ArrayList<>(result.keySet()).equals(result.keySet().stream().sorted().toList()))
		{
			throw new IllegalArgumentException("Query fact labels are incomplete or unordered.");
		}
		return Map.copyOf(result);
	}

	private static int id(List<String> values, String key, String label)
	{
		final int index = values.indexOf(key);
		if (index < 0)
		{
			throw new IllegalArgumentException("Unknown execution " + label + ": " + key);
		}
		return index + 1;
	}

	private static String value(List<String> values, int id, String label)
	{
		if ((id < 1) || (id > values.size()))
		{
			throw new IllegalArgumentException("Unknown execution " + label + " ID.");
		}
		return values.get(id - 1);
	}

	private static int integer(Element element, String name)
	{
		try
		{
			return Integer.parseInt(element.getAttribute(name));
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Execution policy integer is invalid: " + name, exception);
		}
	}

	private static void require(Element element, String name, Set<String> attributes)
	{
		if (!element.getTagName().equals(name) || (element.getAttributes().getLength() != attributes.size()) || !attributes.stream().allMatch(element::hasAttribute))
		{
			throw new IllegalArgumentException("Execution policy element is not exact: " + name);
		}
	}

	private static List<Element> children(Element parent)
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
				throw new IllegalArgumentException("Execution policy contains unexpected text.");
			}
		}
		return result;
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

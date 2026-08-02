/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 */
package org.l2jmobius.gameserver.phantoms.acquisition.quest;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.model.script.Quest;

/** Strict source-hashed allowlist for the audited pure kill-collection subset. */
public final class PhantomAcquisitionQuestCatalog
{
	private static final int MAX_CATALOG_BYTES = 64 * 1024;
	private static final int MAX_SCRIPT_BYTES = 256 * 1024;
	private final String _catalogHash;
	private final String _authorityHash;
	private final Path _scriptsRoot;
	private final List<Rule> _rules;
	private final Map<String, Rule> _byId;

	private PhantomAcquisitionQuestCatalog(String catalogHash, Path scriptsRoot, List<Rule> rules)
	{
		_catalogHash = catalogHash;
		_scriptsRoot = scriptsRoot;
		_rules = List.copyOf(rules);
		_byId = rules.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Rule::id, rule -> rule));
		_authorityHash = digest("QUEST_COLLECTION_V1", catalogHash, rules.stream().map(Rule::ruleHash).toList());
	}

	public static PhantomAcquisitionQuestCatalog load(Path catalogPath, Path scriptsRoot)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(catalogPath);
			if ((bytes.length == 0) || (bytes.length > MAX_CATALOG_BYTES))
			{
				throw new IllegalArgumentException("Quest collection catalog size is invalid.");
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
			require(root, "questCollectionCatalog", Set.of("id", "version"), true);
			if (!"high-five-quest-collection-v1".equals(root.getAttribute("id")) || !"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Quest collection catalog identity is invalid.");
			}
			final Path canonicalRoot = scriptsRoot.toAbsolutePath().normalize();
			final List<Rule> rules = new ArrayList<>();
			for (Element element : children(root))
			{
				rules.add(parseRule(element, canonicalRoot));
			}
			if ((rules.size() < 2) || (rules.size() > 8) || (rules.stream().map(Rule::questName).distinct().count() > 4) || !rules.equals(rules.stream().sorted(Comparator.comparing(Rule::id)).toList()) || (rules.stream().map(Rule::id).distinct().count() != rules.size()) || (rules.stream().map(rule -> rule.questId() + ":" + rule.questItemId()).distinct().count() != rules.size()))
			{
				throw new IllegalArgumentException("Quest collection rules are not unique, ordered and bounded.");
			}
			return new PhantomAcquisitionQuestCatalog(hash(bytes), canonicalRoot, rules);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load quest collection catalog.", exception);
		}
	}

	public String catalogHash()
	{
		return _catalogHash;
	}

	public String authorityHash()
	{
		return _authorityHash;
	}

	public List<Rule> rules()
	{
		return _rules;
	}

	public Optional<Rule> rule(String id)
	{
		return Optional.ofNullable(_byId.get(id));
	}

	public List<Rule> rulesForItem(int itemId)
	{
		return _rules.stream().filter(rule -> rule.questItemId() == itemId).toList();
	}

	public void validateRuntime()
	{
		for (Rule rule : _rules)
		{
			final Quest byId = ScriptManager.getInstance().getQuest(rule.questId());
			final Quest byName = ScriptManager.getInstance().getScript(rule.questName());
			final Path expectedPath = _scriptsRoot.resolve(rule.scriptPath()).normalize();
			final String expectedClass = rule.scriptPath().substring(0, rule.scriptPath().length() - ".java".length()).replace('/', '.');
			if ((byId == null) || (byId != byName) || (byId.getId() != rule.questId()) || !rule.questName().equals(byId.getName()) || !expectedClass.equals(byId.getClass().getName()))
			{
				throw new IllegalStateException("Curated quest script is not loaded with its exact identity: " + rule.id());
			}
			if (!byId.getRegisteredIds(ListenerRegisterType.NPC).containsAll(rule.targetNpcIds()) || Arrays.stream(byId.getRegisteredItemIds()).noneMatch(id -> id == rule.questItemId()))
			{
				throw new IllegalStateException("Curated quest registrations changed: " + rule.id());
			}
			if ((ItemData.getInstance().getTemplate(rule.questItemId()) == null) || rule.targetNpcIds().stream().anyMatch(id -> NpcData.getInstance().getTemplate(id) == null))
			{
				throw new IllegalStateException("Curated quest item or target reference is absent: " + rule.id());
			}
			verifyScript(expectedPath, rule.scriptHash());
		}
	}

	public boolean current()
	{
		try
		{
			validateRuntime();
			return true;
		}
		catch (RuntimeException exception)
		{
			return false;
		}
	}

	private static Rule parseRule(Element element, Path scriptsRoot) throws Exception
	{
		final Set<String> attributes = Set.of("id", "questId", "questName", "script", "scriptSha256", "state", "conds", "questItemId", "grantShape", "chanceKind", "rollBound", "rollThreshold", "minimumCount", "maximumCount", "itemCap", "summonPolicy", "partyPolicy", "registeredQuestItem");
		require(element, "rule", attributes, true);
		final List<Element> sections = children(element);
		if (!sections.stream().map(Element::getTagName).toList().equals(List.of("targets", "expectedVars", "sourceRefs")))
		{
			throw new IllegalArgumentException("Quest collection rule sections are not exact.");
		}
		final String id = text(element, "id", 64);
		final int questId = integer(element, "questId");
		final String questName = text(element, "questName", 96);
		final String scriptPath = text(element, "script", 192).replace('\\', '/');
		final String scriptHash = hashText(element, "scriptSha256");
		final Path source = scriptsRoot.resolve(scriptPath).normalize();
		if (!source.startsWith(scriptsRoot) || !Files.isRegularFile(source))
		{
			throw new IllegalArgumentException("Curated quest source path is invalid.");
		}
		verifyScript(source, scriptHash);
		final List<Integer> conds = integers(element.getAttribute("conds"), 4);
		final List<Integer> targets = childIntegers(sections.get(0), "targets", "target", "npcId", 8, false);
		final List<String> expectedVars = childTexts(sections.get(1), "expectedVars", "var", "name", 4, true);
		final List<String> sourceRefs = childTexts(sections.get(2), "sourceRefs", "ref", "value", 8, false);
		final Rule unsigned = new Rule(id, "0".repeat(64), questId, questName, scriptPath, scriptHash, text(element, "state", 16), conds, targets, integer(element, "questItemId"), GrantShape.valueOf(element.getAttribute("grantShape")), ChanceKind.valueOf(element.getAttribute("chanceKind")), integer(element, "rollBound"), integer(element, "rollThreshold"), integer(element, "minimumCount"), integer(element, "maximumCount"), integer(element, "itemCap"), SummonPolicy.valueOf(element.getAttribute("summonPolicy")), PartyPolicy.valueOf(element.getAttribute("partyPolicy")), bool(element, "registeredQuestItem"), expectedVars, sourceRefs);
		final String ruleHash = digest("QUEST_RULE_V1", unsigned.canonicalIdentity());
		return new Rule(id, ruleHash, questId, questName, scriptPath, scriptHash, unsigned.requiredState(), conds, targets, unsigned.questItemId(), unsigned.grantShape(), unsigned.chanceKind(), unsigned.rollBound(), unsigned.rollThreshold(), unsigned.minimumCount(), unsigned.maximumCount(), unsigned.itemCap(), unsigned.summonPolicy(), unsigned.partyPolicy(), unsigned.registeredQuestItem(), expectedVars, sourceRefs);
	}

	private static void verifyScript(Path source, String expectedHash)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(source);
			if ((bytes.length == 0) || (bytes.length > MAX_SCRIPT_BYTES) || !hash(bytes).equals(expectedHash))
			{
				throw new IllegalArgumentException("Curated quest script hash is stale: " + source);
			}
			strictUtf8(bytes);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not verify curated quest script: " + source, exception);
		}
	}

	private static List<Integer> childIntegers(Element section, String sectionName, String childName, String attribute, int maximum, boolean emptyAllowed)
	{
		require(section, sectionName, Set.of(), true);
		final List<Integer> result = new ArrayList<>();
		for (Element child : children(section))
		{
			require(child, childName, Set.of(attribute), false);
			result.add(integer(child, attribute));
		}
		if ((!emptyAllowed && result.isEmpty()) || (result.size() > maximum) || (new HashSet<>(result).size() != result.size()) || !result.equals(result.stream().sorted().toList()))
		{
			throw new IllegalArgumentException("Curated quest integer list is not unique, sorted and bounded.");
		}
		return List.copyOf(result);
	}

	private static List<String> childTexts(Element section, String sectionName, String childName, String attribute, int maximum, boolean emptyAllowed)
	{
		require(section, sectionName, Set.of(), true);
		final List<String> result = new ArrayList<>();
		for (Element child : children(section))
		{
			require(child, childName, Set.of(attribute), false);
			result.add(text(child, attribute, 192));
		}
		if ((!emptyAllowed && result.isEmpty()) || (result.size() > maximum) || (new HashSet<>(result).size() != result.size()))
		{
			throw new IllegalArgumentException("Curated quest text list is not unique and bounded.");
		}
		return List.copyOf(result);
	}

	private static List<Integer> integers(String value, int maximum)
	{
		try
		{
			final List<Integer> result = Arrays.stream(value.split(",", -1)).map(String::trim).map(Integer::parseInt).toList();
			if (result.isEmpty() || (result.size() > maximum) || (new HashSet<>(result).size() != result.size()) || !result.equals(result.stream().sorted().toList()))
			{
				throw new IllegalArgumentException("Curated quest integer set is not unique, sorted and bounded.");
			}
			return result;
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid curated quest integer set.", exception);
		}
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
				throw new IllegalArgumentException("Unexpected quest catalog text.");
			}
		}
		return result;
	}

	private static void require(Element element, String name, Set<String> attributes, boolean childrenAllowed)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()) || !attributes.stream().allMatch(element::hasAttribute) || (!childrenAllowed && !children(element).isEmpty()))
		{
			throw new IllegalArgumentException("Invalid quest collection element: " + name);
		}
	}

	private static int integer(Element element, String name)
	{
		try
		{
			return Integer.parseInt(element.getAttribute(name));
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid quest collection integer: " + name, exception);
		}
	}

	private static boolean bool(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!"true".equals(value) && !"false".equals(value))
		{
			throw new IllegalArgumentException("Invalid quest collection boolean: " + name);
		}
		return Boolean.parseBoolean(value);
	}

	private static String text(Element element, String name, int maximum)
	{
		final String value = element.getAttribute(name);
		if (value.isBlank() || (value.getBytes(StandardCharsets.UTF_8).length > maximum))
		{
			throw new IllegalArgumentException("Invalid quest collection text: " + name);
		}
		return value;
	}

	private static String hashText(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid quest collection hash: " + name);
		}
		return value;
	}

	private static void strictUtf8(byte[] bytes) throws CharacterCodingException
	{
		StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
	}

	private static String hash(byte[] bytes) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static String digest(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(Objects.toString(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	public enum GrantShape
	{
		GUARANTEED_ITEM,
		SINGLE_BOUNDED_ROLL
	}

	public enum ChanceKind
	{
		NONE,
		RND_LT
	}

	public enum SummonPolicy
	{
		INCLUDE,
		EXCLUDE
	}

	public enum PartyPolicy
	{
		SELF_ONLY
	}

	public record Rule(String id, String ruleHash, int questId, String questName, String scriptPath, String scriptHash, String requiredState, List<Integer> allowedConds, List<Integer> targetNpcIds, int questItemId, GrantShape grantShape, ChanceKind chanceKind, int rollBound, int rollThreshold, int minimumCount, int maximumCount, int itemCap, SummonPolicy summonPolicy, PartyPolicy partyPolicy, boolean registeredQuestItem, List<String> expectedVars, List<String> sourceRefs)
	{
		public Rule
		{
			allowedConds = List.copyOf(allowedConds);
			targetNpcIds = List.copyOf(targetNpcIds);
			expectedVars = List.copyOf(expectedVars);
			sourceRefs = List.copyOf(sourceRefs);
			if (!ruleHash.matches("[0-9a-f]{64}") || (questId <= 0) || !"STARTED".equals(requiredState) || allowedConds.isEmpty() || (allowedConds.size() > 4) || targetNpcIds.isEmpty() || (targetNpcIds.size() > 8) || (questItemId <= 0) || (grantShape == null) || (chanceKind == null) || (summonPolicy == null) || (partyPolicy != PartyPolicy.SELF_ONLY) || !registeredQuestItem || (minimumCount != 1) || (maximumCount != 1) || (itemCap <= 0) || (expectedVars.size() > 4) || sourceRefs.isEmpty())
			{
				throw new IllegalArgumentException("Invalid curated quest rule.");
			}
			if ((grantShape == GrantShape.GUARANTEED_ITEM) != (chanceKind == ChanceKind.NONE) || ((chanceKind == ChanceKind.RND_LT) && ((rollBound <= 0) || (rollThreshold <= 0) || (rollThreshold >= rollBound))))
			{
				throw new IllegalArgumentException("Invalid curated quest grant formula.");
			}
		}

		public boolean supports(int cond, long itemCount, int npcId, boolean summonKill)
		{
			return allowedConds.contains(cond) && (itemCount >= 0) && (itemCount < itemCap) && targetNpcIds.contains(npcId) && (!summonKill || (summonPolicy == SummonPolicy.INCLUDE));
		}

		public boolean grants(int randomValue)
		{
			return (grantShape == GrantShape.GUARANTEED_ITEM) || ((randomValue >= 0) && (randomValue < rollBound) && (randomValue < rollThreshold));
		}

		private String canonicalIdentity()
		{
			return String.join("\u0000", id, Integer.toString(questId), questName, scriptPath, scriptHash, requiredState, allowedConds.toString(), targetNpcIds.toString(), Integer.toString(questItemId), grantShape.name(), chanceKind.name(), Integer.toString(rollBound), Integer.toString(rollThreshold), Integer.toString(minimumCount), Integer.toString(maximumCount), Integer.toString(itemCap), summonPolicy.name(), partyPolicy.name(), Boolean.toString(registeredQuestItem), expectedVars.toString(), sourceRefs.toString());
		}
	}
}

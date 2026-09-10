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
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.io.ByteArrayInputStream;
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
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.InstanceManager;
import org.l2jmobius.gameserver.managers.ScriptManager;
import org.l2jmobius.gameserver.model.events.ListenerRegisterType;
import org.l2jmobius.gameserver.model.script.Quest;
import org.l2jmobius.gameserver.phantoms.PhantomUtf8SourceHash;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog;
import org.l2jmobius.gameserver.phantoms.acquisition.quest.PhantomAcquisitionQuestCatalog.Rule;

/** Strict, source-hashed whitelist for the bounded Goal036 content vertical. */
public final class PhantomQuestInstanceCatalog
{
	private static final int MAX_CATALOG_BYTES = 128 * 1024;
	private static final int MAX_SOURCE_BYTES = 512 * 1024;
	private static final Set<String> EXPECTED_CONTENT = Set.of("class.warrior-q401", "instance.kamaloka-57", "instance.pailaka-128", "quest.102", "quest.152");
	private final Path _sourceRoot;
	private final String _catalogHash;
	private final String _authorityHash;
	private final List<Content> _contents;
	private final Map<String, Content> _byId;

	private PhantomQuestInstanceCatalog(Path sourceRoot, String catalogHash, List<Content> contents)
	{
		_sourceRoot = sourceRoot;
		_catalogHash = catalogHash;
		_contents = List.copyOf(contents);
		_byId = index(contents, Content::id, "content");
		_authorityHash = digest("SUPPORTED_CONTENT_V1", catalogHash, contents.stream().map(Content::contentHash).toList());
	}

	public static PhantomQuestInstanceCatalog load(Path catalogPath, Path sourceRoot)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(catalogPath);
			if ((bytes.length == 0) || (bytes.length > MAX_CATALOG_BYTES))
			{
				throw new IllegalArgumentException("Supported content catalog size is invalid.");
			}
			final String catalogHash = PhantomUtf8SourceHash.sha256(bytes);
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			require(root, "supportedContentCatalog", Set.of("id", "version"), true);
			if (!"high-five-supported-content-v1".equals(root.getAttribute("id")) || !"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Supported content catalog identity is invalid.");
			}
			final Path canonicalRoot = sourceRoot.toAbsolutePath().normalize();
			final List<Content> contents = new ArrayList<>();
			for (Element child : children(root))
			{
				contents.add(parseContent(child, canonicalRoot));
			}
			if ((contents.size() != EXPECTED_CONTENT.size()) || !contents.stream().map(Content::id).collect(Collectors.toSet()).equals(EXPECTED_CONTENT) || !contents.equals(contents.stream().sorted(Comparator.comparing(Content::id)).toList()))
			{
				throw new IllegalArgumentException("Supported content identities are not exact, unique and ordered.");
			}
			return new PhantomQuestInstanceCatalog(canonicalRoot, catalogHash, contents);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load supported content catalog.", exception);
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

	public List<Content> contents()
	{
		return _contents;
	}

	public Optional<Content> content(String id)
	{
		return Optional.ofNullable(_byId.get(id));
	}

	public void validateRuntime(PhantomAcquisitionQuestCatalog acquisitions)
	{
		for (Content content : _contents)
		{
			for (Owner owner : content.owners())
			{
				final Quest script = ScriptManager.getInstance().getScript(owner.scriptName());
				if ((script == null) || !owner.className().equals(script.getClass().getName()) || ((owner.questId() > 0) && ((script.getId() != owner.questId()) || (ScriptManager.getInstance().getQuest(owner.questId()) != script))))
				{
					throw new IllegalStateException("Supported content owner is not loaded with its exact identity: " + content.id() + '/' + owner.role());
				}
				final Set<Integer> registeredNpcs = script.getRegisteredIds(ListenerRegisterType.NPC);
				final Set<Integer> ownedNpcs = content.steps().stream().filter(step -> owner.role().equals(step.ownerRole())).flatMap(step -> step.npcIds().stream()).collect(Collectors.toSet());
				if (!registeredNpcs.containsAll(ownedNpcs))
				{
					throw new IllegalStateException("Supported content NPC registrations changed: " + content.id() + '/' + owner.role());
				}
				verifySource(_sourceRoot, owner.sourcePath(), owner.sourceHash());
			}
			for (SourceRef source : content.sources())
			{
				verifySource(_sourceRoot, source.path(), source.sha256());
			}
			if (content.steps().stream().flatMap(step -> step.npcIds().stream()).anyMatch(id -> NpcData.getInstance().getTemplate(id) == null) || content.itemIds().stream().anyMatch(id -> ItemData.getInstance().getTemplate(id) == null))
			{
				throw new IllegalStateException("Supported content NPC or item reference is absent: " + content.id());
			}
			if ((content.instanceTemplateId() > 0) && (InstanceManager.getInstance().getInstanceIdName(content.instanceTemplateId()) == null))
			{
				throw new IllegalStateException("Supported instance template is absent: " + content.id());
			}
			if (!content.acquisitionRuleId().isEmpty())
			{
				final Rule rule = acquisitions == null ? null : acquisitions.rule(content.acquisitionRuleId()).orElse(null);
				final Owner quest = content.owner("quest").orElseThrow();
				final Step collection = content.step("collect").orElseThrow();
				if ((rule == null) || (rule.questId() != content.questId()) || !rule.questName().equals(quest.scriptName()) || !rule.scriptHash().equals(quest.sourceHash()) || !rule.targetNpcIds().equals(collection.npcIds()))
				{
					throw new IllegalStateException("Supported quest acquisition binding changed: " + content.id());
				}
			}
		}
	}

	public boolean current(PhantomAcquisitionQuestCatalog acquisitions)
	{
		try
		{
			validateRuntime(acquisitions);
			return true;
		}
		catch (RuntimeException exception)
		{
			return false;
		}
	}

	private static Content parseContent(Element element, Path sourceRoot) throws Exception
	{
		require(element, "content", Set.of("id", "kind", "minLevel", "maxLevel", "races", "classes", "questId", "instanceTemplateId", "acquisitionRuleId"), true);
		final List<Element> sections = children(element);
		if (!sections.stream().map(Element::getTagName).toList().equals(List.of("owners", "steps", "items", "sources")))
		{
			throw new IllegalArgumentException("Supported content sections are not exact.");
		}
		final List<Owner> owners = new ArrayList<>();
		for (Element owner : children(sections.get(0)))
		{
			require(owner, "owner", Set.of("role", "questId", "scriptName", "className", "source", "sourceSha256"), false);
			final Owner parsed = new Owner(text(owner, "role", 32), integer(owner, "questId"), text(owner, "scriptName", 96), text(owner, "className", 192), path(owner, "source"), hashText(owner, "sourceSha256"));
			verifySource(sourceRoot, parsed.sourcePath(), parsed.sourceHash());
			owners.add(parsed);
		}
		final List<Step> steps = new ArrayList<>();
		for (Element step : children(sections.get(1)))
		{
			require(step, "step", Set.of("id", "operation", "ownerRole", "npcIds", "event", "itemId"), false);
			final String ownerRole = dash(text(step, "ownerRole", 32));
			final String event = dash(text(step, "event", 96));
			steps.add(new Step(text(step, "id", 64), Operation.valueOf(step.getAttribute("operation")), ownerRole, integers(step.getAttribute("npcIds"), 8, true), event, integer(step, "itemId")));
		}
		final List<Integer> items = new ArrayList<>();
		for (Element item : children(sections.get(2)))
		{
			require(item, "item", Set.of("id"), false);
			items.add(integer(item, "id"));
		}
		final List<SourceRef> sources = new ArrayList<>();
		for (Element source : children(sections.get(3)))
		{
			require(source, "source", Set.of("path", "sha256"), false);
			final SourceRef parsed = new SourceRef(path(source, "path"), hashText(source, "sha256"));
			verifySource(sourceRoot, parsed.path(), parsed.sha256());
			sources.add(parsed);
		}
		final String id = text(element, "id", 64);
		final Kind kind = Kind.valueOf(element.getAttribute("kind"));
		final int minLevel = integer(element, "minLevel");
		final int maxLevel = integer(element, "maxLevel");
		final Set<String> races = names(element.getAttribute("races"), 8);
		final List<Integer> classes = integers(element.getAttribute("classes"), 16, true);
		final int questId = integer(element, "questId");
		final int instanceTemplateId = integer(element, "instanceTemplateId");
		final String acquisitionRule = dash(text(element, "acquisitionRuleId", 64));
		final Content unsigned = new Content(id, kind, minLevel, maxLevel, races, classes, questId, instanceTemplateId, acquisitionRule, owners, steps, items, sources, "0".repeat(64));
		return new Content(id, kind, minLevel, maxLevel, races, classes, questId, instanceTemplateId, acquisitionRule, owners, steps, items, sources, digest("SUPPORTED_CONTENT_RULE_V1", unsigned.canonicalIdentity()));
	}

	private static void verifySource(Path root, String relativePath, String expectedHash)
	{
		try
		{
			final Path source = root.resolve(relativePath).normalize();
			if (!source.startsWith(root) || !Files.isRegularFile(source))
			{
				throw new IllegalArgumentException("Supported content source path is invalid: " + relativePath);
			}
			final byte[] bytes = Files.readAllBytes(source);
			if ((bytes.length == 0) || (bytes.length > MAX_SOURCE_BYTES))
			{
				throw new IllegalArgumentException("Supported content source hash is stale: " + relativePath);
			}
			if (!PhantomUtf8SourceHash.sha256(bytes).equals(expectedHash))
			{
				throw new IllegalArgumentException("Supported content source hash is stale: " + relativePath);
			}
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not verify supported content source: " + relativePath, exception);
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
				throw new IllegalArgumentException("Unexpected supported content catalog text.");
			}
		}
		return result;
	}

	private static void require(Element element, String name, Set<String> attributes, boolean childrenAllowed)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()) || !attributes.stream().allMatch(element::hasAttribute) || (!childrenAllowed && !children(element).isEmpty()))
		{
			throw new IllegalArgumentException("Invalid supported content element: " + name);
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
			throw new IllegalArgumentException("Invalid supported content integer: " + name, exception);
		}
	}

	private static String text(Element element, String name, int maximum)
	{
		final String value = element.getAttribute(name);
		if (value.isBlank() || (value.getBytes(StandardCharsets.UTF_8).length > maximum))
		{
			throw new IllegalArgumentException("Invalid supported content text: " + name);
		}
		return value;
	}

	private static String path(Element element, String name)
	{
		final String value = text(element, name, 256).replace('\\', '/');
		if (value.startsWith("/") || value.contains("../") || value.contains(":"))
		{
			throw new IllegalArgumentException("Invalid supported content relative path: " + name);
		}
		return value;
	}

	private static String hashText(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!value.matches("[0-9a-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid supported content hash: " + name);
		}
		return value;
	}

	private static String dash(String value)
	{
		return "-".equals(value) ? "" : value;
	}

	private static List<Integer> integers(String value, int maximum, boolean wildcardAllowed)
	{
		if (wildcardAllowed && ("*".equals(value) || "-".equals(value)))
		{
			return List.of();
		}
		try
		{
			final List<Integer> result = Arrays.stream(value.split(",", -1)).map(String::trim).map(Integer::parseInt).toList();
			if (result.isEmpty() || (result.size() > maximum) || (new HashSet<>(result).size() != result.size()) || !result.equals(result.stream().sorted().toList()))
			{
				throw new IllegalArgumentException("Supported content integer list is not unique, sorted and bounded.");
			}
			return result;
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid supported content integer list.", exception);
		}
	}

	private static Set<String> names(String value, int maximum)
	{
		if ("*".equals(value))
		{
			return Set.of();
		}
		final List<String> result = Arrays.stream(value.split(",", -1)).map(String::trim).toList();
		if (result.isEmpty() || (result.size() > maximum) || result.stream().anyMatch(name -> !name.matches("[A-Z_]{2,32}")) || (new HashSet<>(result).size() != result.size()))
		{
			throw new IllegalArgumentException("Invalid supported content name set.");
		}
		return Set.copyOf(result);
	}

	private static <T> Map<String, T> index(List<T> values, Function<T, String> key, String kind)
	{
		try
		{
			return values.stream().collect(Collectors.toUnmodifiableMap(key, Function.identity()));
		}
		catch (IllegalStateException exception)
		{
			throw new IllegalArgumentException("Duplicate supported " + kind + " identity.", exception);
		}
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

	public enum Kind
	{
		QUEST,
		CLASS_PATH,
		KAMALOKA,
		PAILAKA
	}

	public enum Operation
	{
		EVENT,
		TALK,
		COMBAT,
		EQUIP
	}

	public record Owner(String role, int questId, String scriptName, String className, String sourcePath, String sourceHash)
	{
		public Owner
		{
			if ((role == null) || !role.matches("[a-z][a-z0-9_.-]{0,31}") || (questId == 0) || (questId < -1) || (scriptName == null) || scriptName.isBlank() || (className == null) || className.isBlank() || (sourcePath == null) || sourcePath.isBlank() || (sourceHash == null) || !sourceHash.matches("[0-9a-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid supported content owner.");
			}
		}
	}

	public record Step(String id, Operation operation, String ownerRole, List<Integer> npcIds, String event, int itemId)
	{
		public Step
		{
			npcIds = List.copyOf(npcIds);
			if ((id == null) || !id.matches("[a-z][a-z0-9_.-]{0,63}") || (operation == null) || (ownerRole == null) || (event == null) || (itemId < 0))
			{
				throw new IllegalArgumentException("Invalid supported content step.");
			}
			final boolean interaction = (operation == Operation.EVENT) || (operation == Operation.TALK);
			if ((operation == Operation.EQUIP) != (itemId > 0) || (operation == Operation.EQUIP) != ownerRole.isEmpty() || (operation == Operation.EQUIP) != npcIds.isEmpty() || (interaction && npcIds.size() != 1) || ((operation == Operation.EVENT) != !event.isEmpty()) || ((operation != Operation.EVENT) && !event.isEmpty()) || ((operation == Operation.COMBAT) && npcIds.isEmpty()) || ((operation != Operation.EQUIP) && (itemId != 0)))
			{
				throw new IllegalArgumentException("Supported content step shape is invalid: " + id);
			}
		}
	}

	public record SourceRef(String path, String sha256)
	{
	}

	public record Content(String id, Kind kind, int minLevel, int maxLevel, Set<String> races, List<Integer> classIds, int questId, int instanceTemplateId, String acquisitionRuleId, List<Owner> owners, List<Step> steps, List<Integer> itemIds, List<SourceRef> sources, String contentHash)
	{
		public Content
		{
			races = Set.copyOf(races);
			classIds = List.copyOf(classIds);
			owners = List.copyOf(owners);
			steps = List.copyOf(steps);
			itemIds = List.copyOf(itemIds);
			sources = List.copyOf(sources);
			if ((id == null) || !id.matches("[a-z][a-z0-9_.-]{0,63}") || (kind == null) || (minLevel < 1) || (maxLevel < minLevel) || (maxLevel > 99) || (questId < 0) || (instanceTemplateId < 0) || (acquisitionRuleId == null) || owners.isEmpty() || (owners.size() > 3) || steps.isEmpty() || (steps.size() > 20) || (itemIds.size() > 32) || (sources.size() > 4) || (contentHash == null) || !contentHash.matches("[0-9a-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid supported content entry.");
			}
			if ((owners.stream().map(Owner::role).distinct().count() != owners.size()) || !owners.equals(owners.stream().sorted(Comparator.comparing(Owner::role)).toList()) || (steps.stream().map(Step::id).distinct().count() != steps.size()) || !steps.equals(steps.stream().sorted(Comparator.comparing(Step::id)).toList()) || (new HashSet<>(itemIds).size() != itemIds.size()) || !itemIds.equals(itemIds.stream().sorted().toList()))
			{
				throw new IllegalArgumentException("Supported content lists are not unique and ordered: " + id);
			}
			final Set<String> roles = owners.stream().map(Owner::role).collect(Collectors.toSet());
			if (steps.stream().anyMatch(step -> !step.ownerRole().isEmpty() && !roles.contains(step.ownerRole())) || ((kind == Kind.QUEST) && ((questId <= 0) || (instanceTemplateId != 0) || !roles.equals(Set.of("quest")))) || ((kind == Kind.CLASS_PATH) && ((questId != 401) || !roles.equals(Set.of("profession", "quest")))) || ((kind == Kind.KAMALOKA) && ((questId != 0) || (instanceTemplateId != 57) || !roles.equals(Set.of("instance")))) || ((kind == Kind.PAILAKA) && ((questId != 128) || (instanceTemplateId != 43) || !roles.equals(Set.of("instance", "quest")))) || (!acquisitionRuleId.isEmpty() && (kind != Kind.QUEST)))
			{
				throw new IllegalArgumentException("Supported content authority shape is invalid: " + id);
			}
			index(owners, Owner::role, "owner");
			index(steps, Step::id, "step");
		}

		public Optional<Owner> owner(String role)
		{
			return owners.stream().filter(owner -> owner.role().equals(role)).findFirst();
		}

		public Optional<Step> step(String stepId)
		{
			return steps.stream().filter(step -> step.id().equals(stepId)).findFirst();
		}

		public boolean eligible(int level, String race, int classId)
		{
			return (level >= minLevel) && (level <= maxLevel) && (races.isEmpty() || races.contains(race)) && (classIds.isEmpty() || classIds.contains(classId));
		}

		private String canonicalIdentity()
		{
			return String.join("\u0000", id, kind.name(), Integer.toString(minLevel), Integer.toString(maxLevel), races.stream().sorted().toList().toString(), classIds.toString(), Integer.toString(questId), Integer.toString(instanceTemplateId), acquisitionRuleId, owners.toString(), steps.toString(), itemIds.toString(), sources.toString());
		}
	}
}

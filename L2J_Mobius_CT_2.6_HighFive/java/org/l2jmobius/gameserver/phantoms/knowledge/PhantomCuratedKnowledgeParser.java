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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.knowledge;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ClassCapabilityFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SkillEvidence;

/**
 * Strict versioned parser for curated class capabilities and content
 * recommendations. It validates source paths without interpreting names.
 */
public final class PhantomCuratedKnowledgeParser
{
	private static final long MAXIMUM_FILE_BYTES = 4L * 1024 * 1024;

	private final Path _directory;
	private final PhantomGameKnowledgeBackend _backend;
	private final PhantomGameKnowledgePolicy _policy;

	public PhantomCuratedKnowledgeParser(Path directory, PhantomGameKnowledgeBackend backend, PhantomGameKnowledgePolicy policy)
	{
		_directory = directory.toAbsolutePath().normalize();
		_backend = backend;
		_policy = policy;
	}

	public CuratedData parse()
	{
		final List<Path> files;
		try (Stream<Path> stream = Files.list(_directory))
		{
			files = stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")).sorted().toList();
		}
		catch (Exception exception)
		{
			throw failure("io", "Unable to list curated knowledge data.", exception);
		}
		if (files.isEmpty() || (files.size() > _policy.maximumSourceFiles()))
		{
			throw failure("count", "Curated knowledge file count is outside policy.");
		}
		String datasetId = null;
		int datasetVersion = -1;
		final ArrayList<ClassCapabilityFact> capabilities = new ArrayList<>();
		final ArrayList<ContentRequirementFact> contents = new ArrayList<>();
		for (Path file : files)
		{
			try
			{
				if (Files.size(file) > MAXIMUM_FILE_BYTES)
				{
					throw failure("count", "Curated knowledge XML exceeds the byte bound.");
				}
				final Element root = parseDocument(file).getDocumentElement();
				if ((root == null) || !"knowledge".equals(root.getTagName()))
				{
					throw failure("schema", "Unexpected curated knowledge root.");
				}
				requireExactAttributes(root, Set.of("schemaVersion", "datasetId", "datasetVersion"));
				if (integer(root, "schemaVersion") != 1)
				{
					throw failure("schema", "Unsupported curated knowledge schemaVersion.");
				}
				final String currentDatasetId = _policy.requireKey(required(root, "datasetId"), "dataset id");
				final int currentDatasetVersion = integer(root, "datasetVersion");
				if (currentDatasetVersion < 1)
				{
					throw failure("schema", "Curated datasetVersion must be positive.");
				}
				if (datasetId == null)
				{
					datasetId = currentDatasetId;
					datasetVersion = currentDatasetVersion;
				}
				else if (!datasetId.equals(currentDatasetId) || (datasetVersion != currentDatasetVersion))
				{
					throw failure("schema", "Curated files declare different dataset metadata.");
				}
				forElements(root, element ->
				{
					switch (element.getTagName())
					{
						case "classCapability" -> capabilities.add(parseCapability(element));
						case "contentRequirement" -> contents.add(parseContent(element));
						default -> throw failure("schema", "Unknown curated knowledge element.");
					}
				});
			}
			catch (PhantomGameKnowledgeValidationException exception)
			{
				throw exception;
			}
			catch (Exception exception)
			{
				throw failure("parse", "Unable to parse curated knowledge XML.", exception);
			}
		}
		if (capabilities.size() > _policy.maximumClassCapabilityFacts() || contents.size() > _policy.maximumContentEntries())
		{
			throw failure("count", "Curated knowledge entity count exceeds policy.");
		}
		capabilities.sort(Comparator.comparingInt(ClassCapabilityFact::classId).thenComparing(ClassCapabilityFact::capabilityKey).thenComparingInt(ClassCapabilityFact::rank));
		contents.sort(Comparator.comparing(ContentRequirementFact::contentId));
		final HashSet<String> capabilityIdentities = new HashSet<>();
		for (ClassCapabilityFact capability : capabilities)
		{
			if (!capabilityIdentities.add(capability.classId() + ":" + capability.capabilityKey()))
			{
				throw failure("duplicate", "Duplicate curated class capability identity.");
			}
		}
		final HashSet<String> contentIdentities = new HashSet<>();
		for (ContentRequirementFact content : contents)
		{
			if (!contentIdentities.add(content.contentId()))
			{
				throw failure("duplicate", "Duplicate curated content identity.");
			}
		}
		return new CuratedData(datasetId, datasetVersion, capabilities, contents);
	}

	private ClassCapabilityFact parseCapability(Element element)
	{
		requireExactAttributes(element, Set.of("classId", "capabilityKey", "rank"));
		final ArrayList<SkillEvidence> skills = new ArrayList<>();
		final ArrayList<String> sources = new ArrayList<>();
		forElements(element, child ->
		{
			switch (child.getTagName())
			{
				case "skill" ->
				{
					requireExactAttributes(child, Set.of("id", "level"));
					requireLeaf(child);
					if (skills.size() >= _policy.maximumEvidenceSkills())
					{
						throw failure("count", "Capability skill evidence exceeds policy.");
					}
					skills.add(new SkillEvidence(integer(child, "id"), integer(child, "level")));
				}
				case "source" ->
				{
					requireExactAttributes(child, Set.of("path"));
					requireLeaf(child);
					addSource(sources, required(child, "path"));
				}
				default -> throw failure("schema", "Unknown class capability child.");
			}
		});
		skills.sort(Comparator.comparingInt(SkillEvidence::skillId).thenComparingInt(SkillEvidence::skillLevel));
		sources.sort(String::compareTo);
		if ((new HashSet<>(skills).size() != skills.size()) || (new HashSet<>(sources).size() != sources.size()))
		{
			throw failure("duplicate", "Duplicate class capability evidence.");
		}
		return new ClassCapabilityFact(integer(element, "classId"), _policy.requireKey(required(element, "capabilityKey"), "capability key"), integer(element, "rank"), skills, sources, PhantomGameKnowledgeAuthority.CURATED_RECOMMENDATION);
	}

	private ContentRequirementFact parseContent(Element element)
	{
		final Set<String> allowed = new HashSet<>(Set.of("contentId", "contentKind", "recommendedMinParty", "recommendedMaxParty"));
		for (String optional : List.of("npcId", "topologyNodeId", "topologyAnchorId"))
		{
			if (element.hasAttribute(optional))
			{
				allowed.add(optional);
			}
		}
		requireExactAttributes(element, allowed);
		final ArrayList<CapabilityRequirement> requirements = new ArrayList<>();
		final ArrayList<String> sources = new ArrayList<>();
		forElements(element, child ->
		{
			switch (child.getTagName())
			{
				case "requirement" ->
				{
					requireExactAttributes(child, Set.of("capabilityKey", "minimumCount", "minimumRank", "required"));
					requireLeaf(child);
					if (requirements.size() >= _policy.maximumRequirementsPerContent())
					{
						throw failure("count", "Content requirements exceed policy.");
					}
					requirements.add(new CapabilityRequirement(_policy.requireKey(required(child, "capabilityKey"), "capability key"), integer(child, "minimumCount"), integer(child, "minimumRank"), bool(child, "required")));
				}
				case "source" ->
				{
					requireExactAttributes(child, Set.of("path"));
					requireLeaf(child);
					addSource(sources, required(child, "path"));
				}
				default -> throw failure("schema", "Unknown content recommendation child.");
			}
		});
		requirements.sort(Comparator.comparing(CapabilityRequirement::capabilityKey).thenComparingInt(CapabilityRequirement::minimumRank).thenComparingInt(CapabilityRequirement::minimumCount).thenComparing(CapabilityRequirement::required));
		sources.sort(String::compareTo);
		if ((requirements.stream().map(CapabilityRequirement::capabilityKey).distinct().count() != requirements.size()) || (new HashSet<>(sources).size() != sources.size()))
		{
			throw failure("duplicate", "Duplicate content requirement or evidence.");
		}
		return new ContentRequirementFact(_policy.requireKey(required(element, "contentId"), "content id"), enumValue(ContentKind.class, required(element, "contentKind")), optionalInteger(element, "npcId"), optionalKey(element, "topologyNodeId"), optionalKey(element, "topologyAnchorId"), integer(element, "recommendedMinParty"), integer(element, "recommendedMaxParty"), requirements, sources, PhantomGameKnowledgeAuthority.CURATED_RECOMMENDATION);
	}

	private void addSource(List<String> sources, String raw)
	{
		if (sources.size() >= _policy.maximumEvidenceReferences())
		{
			throw failure("count", "Curated source evidence exceeds policy.");
		}
		final String source = _policy.requireSource(raw);
		if (!_backend.sourceExists(source))
		{
			throw failure("evidence", "Curated source evidence is missing.");
		}
		sources.add(source);
	}

	private Document parseDocument(Path path) throws Exception
	{
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		try (InputStream stream = Files.newInputStream(path))
		{
			return factory.newDocumentBuilder().parse(stream);
		}
	}

	private static void forElements(Element parent, java.util.function.Consumer<Element> consumer)
	{
		final NodeList children = parent.getChildNodes();
		for (int index = 0; index < children.getLength(); index++)
		{
			final Node child = children.item(index);
			if (child.getNodeType() == Node.ELEMENT_NODE)
			{
				consumer.accept((Element) child);
			}
			else if ((child.getNodeType() == Node.TEXT_NODE) && !child.getTextContent().isBlank())
			{
				throw failure("schema", "Unexpected curated knowledge text.");
			}
		}
	}

	private static void requireExactAttributes(Element element, Set<String> expected)
	{
		final NamedNodeMap attributes = element.getAttributes();
		for (int index = 0; index < attributes.getLength(); index++)
		{
			if (!expected.contains(attributes.item(index).getNodeName()))
			{
				throw failure("schema", "Unknown curated knowledge attribute.");
			}
		}
		for (String name : expected)
		{
			if (!element.hasAttribute(name))
			{
				throw failure("schema", "Missing curated knowledge attribute.");
			}
		}
	}

	private static void requireLeaf(Element element)
	{
		forElements(element, _ ->
		{
			throw failure("schema", "Curated leaf contains child content.");
		});
	}

	private static String required(Element element, String name)
	{
		final String value = element.getAttribute(name).trim();
		if (value.isEmpty() || (value.length() > 512))
		{
			throw failure("schema", "Missing or oversized curated attribute.");
		}
		return value;
	}

	private String optionalKey(Element element, String name)
	{
		return element.hasAttribute(name) ? _policy.requireKey(required(element, name), name) : null;
	}

	private static int integer(Element element, String name)
	{
		try
		{
			return Integer.parseInt(required(element, name));
		}
		catch (NumberFormatException exception)
		{
			throw failure("schema", "Invalid curated integer.", exception);
		}
	}

	private static Integer optionalInteger(Element element, String name)
	{
		return element.hasAttribute(name) ? integer(element, name) : null;
	}

	private static boolean bool(Element element, String name)
	{
		final String value = required(element, name);
		if (!"true".equals(value) && !"false".equals(value))
		{
			throw failure("schema", "Invalid curated boolean.");
		}
		return Boolean.parseBoolean(value);
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, String value)
	{
		try
		{
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException exception)
		{
			throw failure("schema", "Invalid curated enum.", exception);
		}
	}

	private static PhantomGameKnowledgeValidationException failure(String category, String message)
	{
		return new PhantomGameKnowledgeValidationException(category, message);
	}

	private static PhantomGameKnowledgeValidationException failure(String category, String message, Throwable cause)
	{
		return new PhantomGameKnowledgeValidationException(category, message, cause);
	}

	public record CuratedData(String datasetId, int datasetVersion, List<ClassCapabilityFact> classCapabilities, List<ContentRequirementFact> contentRequirements)
	{
		public CuratedData
		{
			classCapabilities = List.copyOf(classCapabilities);
			contentRequirements = List.copyOf(contentRequirements);
		}
	}
}

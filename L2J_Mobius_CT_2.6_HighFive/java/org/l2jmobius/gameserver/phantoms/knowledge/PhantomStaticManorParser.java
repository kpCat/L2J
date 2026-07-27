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
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ManorFact;

/**
 * Strict static parser for data/Seeds.xml. It intentionally has no dependency
 * on the mutable manor manager, castle procurement or database state.
 */
public final class PhantomStaticManorParser
{
	private static final Set<String> ROOT_ATTRIBUTES = Set.of("xmlns:xsi", "xsi:noNamespaceSchemaLocation");
	private static final Set<String> CROP_ATTRIBUTES = Set.of("id", "seedId", "mature_Id", "reward1", "reward2", "alternative", "level", "limit_seed", "limit_crops");

	private final Path _path;
	private final PhantomGameKnowledgePolicy _policy;

	public PhantomStaticManorParser(Path path, PhantomGameKnowledgePolicy policy)
	{
		_path = path.toAbsolutePath().normalize();
		_policy = policy;
	}

	public java.util.List<ManorFact> parse()
	{
		if (!Files.isRegularFile(_path))
		{
			throw failure("io", "Static Seeds.xml is missing.");
		}
		try
		{
			final Document document = parseDocument();
			final Element root = document.getDocumentElement();
			if ((root == null) || !"list".equals(root.getTagName()))
			{
				throw failure("schema", "Unexpected Seeds.xml root.");
			}
			requireOnlyAttributes(root, ROOT_ATTRIBUTES);
			final ArrayList<ManorFact> result = new ArrayList<>();
			forElements(root, castle ->
			{
				if (!"castle".equals(castle.getTagName()))
				{
					throw failure("schema", "Unknown Seeds.xml element.");
				}
				requireExactAttributes(castle, Set.of("id"));
				final int castleId = integer(castle, "id");
				forElements(castle, crop ->
				{
					if (!"crop".equals(crop.getTagName()))
					{
						throw failure("schema", "Unknown Seeds.xml castle element.");
					}
					requireExactAttributes(crop, CROP_ATTRIBUTES);
					requireLeaf(crop);
					if (result.size() >= _policy.maximumManorFacts())
					{
						throw failure("count", "Static manor fact count exceeds policy.");
					}
					result.add(new ManorFact(castleId, integer(crop, "seedId"), integer(crop, "id"), integer(crop, "mature_Id"), integer(crop, "reward1"), integer(crop, "reward2"), integer(crop, "level"), bool(crop, "alternative"), integer(crop, "limit_seed"), integer(crop, "limit_crops"), "data/Seeds.xml", PhantomGameKnowledgeAuthority.STATIC_DATAPACK_FACT));
				});
			});
			result.sort(Comparator.comparingInt(ManorFact::castleId).thenComparingInt(ManorFact::seedItemId).thenComparingInt(ManorFact::cropItemId));
			return java.util.List.copyOf(result);
		}
		catch (PhantomGameKnowledgeValidationException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw failure("parse", "Unable to parse static Seeds.xml.", exception);
		}
	}

	private Document parseDocument() throws Exception
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
		try (InputStream stream = Files.newInputStream(_path))
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
				throw failure("schema", "Unexpected Seeds.xml text.");
			}
		}
	}

	private static void requireOnlyAttributes(Element element, Set<String> allowed)
	{
		final NamedNodeMap attributes = element.getAttributes();
		for (int index = 0; index < attributes.getLength(); index++)
		{
			if (!allowed.contains(attributes.item(index).getNodeName()))
			{
				throw failure("schema", "Unknown Seeds.xml attribute.");
			}
		}
	}

	private static void requireExactAttributes(Element element, Set<String> expected)
	{
		requireOnlyAttributes(element, expected);
		for (String name : expected)
		{
			if (!element.hasAttribute(name))
			{
				throw failure("schema", "Missing Seeds.xml attribute.");
			}
		}
	}

	private static void requireLeaf(Element element)
	{
		forElements(element, _ ->
		{
			throw failure("schema", "Seeds.xml crop must be a leaf.");
		});
	}

	private static int integer(Element element, String name)
	{
		try
		{
			return Integer.parseInt(element.getAttribute(name));
		}
		catch (NumberFormatException exception)
		{
			throw failure("schema", "Invalid Seeds.xml integer.", exception);
		}
	}

	private static boolean bool(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!"true".equals(value) && !"false".equals(value))
		{
			throw failure("schema", "Invalid Seeds.xml boolean.");
		}
		return Boolean.parseBoolean(value);
	}

	private static PhantomGameKnowledgeValidationException failure(String category, String message)
	{
		return new PhantomGameKnowledgeValidationException(category, message);
	}

	private static PhantomGameKnowledgeValidationException failure(String category, String message, Throwable cause)
	{
		return new PhantomGameKnowledgeValidationException(category, message, cause);
	}
}

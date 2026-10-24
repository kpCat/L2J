/*
 * Copyright (c) 2026 L2jMobius
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 */
package org.l2jmobius.gameserver.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/** Read-only, JDK-only owner profile gate for source builds and copied packages. */
public final class OwnerProfileVerifier
{
	private static final String PROFILE_HASH = "c289b3ac0051ccda5544967988aa0a1929abd1b0d18c2662117fd3f0ce2aa91c";
	private static final String PRESERVED_HASH = "4adc190cb45865d1ac8d73d98adc642764b87f7bcc8fe5e1d4aad594017b2785";
	private static final Set<String> PROFILE_PATHS = Set.of("config/Player.ini", "data/EnchantItemGroups.xml", "data/EnchantItemData.xml", "config/Custom/ServitorShare.ini", "config/Custom/PrivateStoreRange.ini", "config/Custom/PhantomMerchants.ini", "config/Custom/PersonalCharacterQoL.ini", "config/Rates.ini");
	private final Path _root;
	private final List<String> _failures = new ArrayList<>();
	private final Map<String, Properties> _configs = new HashMap<>();
	private Properties _preserved;
	private Document _groups;
	private Document _items;

	private OwnerProfileVerifier(Path gameRoot) throws IOException
	{
		_root = gameRoot.toAbsolutePath().normalize().toRealPath();
	}

	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.err.println("Usage: OwnerProfileVerifier <gameRoot>");
			System.exit(2);
		}
		try
		{
			new OwnerProfileVerifier(Path.of(args[0])).verify();
		}
		catch (Exception e)
		{
			System.err.println("OWNER_PROFILE_FAIL: " + ((e instanceof IllegalStateException) ? e.getMessage() : e.getClass().getSimpleName()));
			System.exit(1);
		}
	}

	private void verify() throws Exception
	{
		final Path profile = file("config/OwnerProfile38.tsv");
		require(PROFILE_HASH.equals(sha256(Files.readAllBytes(profile))), "OwnerProfile38.tsv SHA-256 differs from the original 38-rule profile");
		final List<String> rows = Files.readAllLines(profile, StandardCharsets.UTF_8);
		require((rows.size() == 39) && rows.get(0).equals("section\tfile\tkey_or_range\tvalue\tnote"), "Expected exactly 38 profile rules and the original header");
		final Set<String> identities = new HashSet<>();
		final Map<String, Set<String>> selected = new HashMap<>();
		for (String row : rows.subList(1, rows.size()))
		{
			final String[] fields = row.split("\t", -1);
			require(fields.length == 5, "Malformed owner rule");
			final String relative = relative(fields[1]);
			require(identities.add(fields[0] + "\t" + fields[1] + "\t" + fields[2]), "Duplicate owner rule");
			if (relative.endsWith(".ini") && !relative.equals("config/Rates.ini"))
			{
				selected.computeIfAbsent(relative, ignored -> new HashSet<>()).add(fields[2]);
			}
		}
		_preserved = properties("config/OwnerProfilePreserved.properties", ignored -> true);
		final String preservedHash = normalizedHash(_preserved);
		require(PRESERVED_HASH.equals(preservedHash), "Preserved baseline manifest differs from the pinned effective values: sha256=" + preservedHash);
		for (var entry : selected.entrySet())
		{
			try
			{
				_configs.put(entry.getKey(), properties(entry.getKey(), entry.getValue()::contains));
			}
			catch (Exception e)
			{
				_failures.add("Cannot read public owner keys: " + entry.getKey());
			}
		}
		_groups = xml("data/EnchantItemGroups.xml", "data/xsd/enchantItemGroups.xsd");
		_items = xml("data/EnchantItemData.xml", "data/xsd/enchantItemData.xsd");
		for (String row : rows.subList(1, rows.size()))
		{
			final String[] fields = row.split("\t", -1);
			String actual;
			try
			{
				actual = actualValue(relative(fields[1]), fields[2]);
			}
			catch (Exception e)
			{
				actual = "INVALID_OR_MISSING";
			}
			final boolean passed = fields[3].equals(actual);
			System.out.println("OWNER_RULE\t" + fields[0] + "\t" + fields[1] + "\t" + fields[2] + "\t" + actual + "\t" + (passed ? "PASS" : "FAIL"));
			if (!passed)
			{
				_failures.add(fields[1] + " / " + fields[2]);
			}
		}
		require(_failures.isEmpty(), "38-rule validation failed: " + String.join("; ", _failures));
		System.out.println("OWNER_PROFILE_PASS rules=38 profileSha256=" + PROFILE_HASH + " questSha256=" + _preserved.getProperty("quest.sha256") + " XML_XSD=PASS READ_ONLY=true");
	}

	private String actualValue(String relative, String key) throws Exception
	{
		if (relative.equals("config/Rates.ini"))
		{
			final Properties rates = properties(relative, name -> name.contains("Quest"));
			require(rates.stringPropertyNames().equals(Set.of(_preserved.getProperty("quest.keys").split(","))), "Quest effective key set differs");
			require(normalizedHash(rates).equals(_preserved.getProperty("quest.sha256")), "Quest effective values differ");
			return "PRESERVE_EXISTING_VALUES";
		}
		if (relative.equals("data/EnchantItemGroups.xml"))
		{
			String result = null;
			for (String group : key.substring(0, key.indexOf('.')).split("/"))
			{
				String found = null;
				final NodeList groups = _groups.getDocumentElement().getElementsByTagName("enchantRateGroup");
				for (int i = 0; i < groups.getLength(); i++)
				{
					final Element element = (Element) groups.item(i);
					if (group.equals(element.getAttribute("name")))
					{
						require(found == null, "Duplicate enchant rate group");
						final List<String> ranges = new ArrayList<>();
						final NodeList current = element.getElementsByTagName("current");
						for (int n = 0; n < current.getLength(); n++)
						{
							final Element range = (Element) current.item(n);
							ranges.add(range.getAttribute("enchant") + ":" + range.getAttribute("chance"));
						}
						found = String.join(";", ranges);
					}
				}
				require((found != null) && ((result == null) || result.equals(found)), "Missing or divergent enchant rate group");
				result = found;
			}
			return result;
		}
		if (relative.equals("data/EnchantItemData.xml"))
		{
			verifyScrolls();
			return "25";
		}
		final Properties config = _configs.get(relative);
		return (config == null) ? "MISSING" : config.getProperty(key, "MISSING").trim();
	}

	private void verifyScrolls()
	{
		final Map<Integer, Integer> expected = new HashMap<>();
		addLimits(expected, "scroll.ordinary", 25);
		addLimits(expected, "scroll.bonus100", 25);
		for (int limit : new int[] { 6, 10, 15, 16, 23, 50, 65535 })
		{
			addLimits(expected, "scroll.preserved." + limit, limit);
		}
		final Set<Integer> bonus100 = ids("scroll.bonus100");
		final Set<Integer> seen = new HashSet<>();
		final Set<Integer> foundBonus = new HashSet<>();
		final NodeList scrolls = _items.getDocumentElement().getElementsByTagName("enchant");
		for (int i = 0; i < scrolls.getLength(); i++)
		{
			final Element scroll = (Element) scrolls.item(i);
			final int id = Integer.parseInt(scroll.getAttribute("id"));
			final int maximum = scroll.hasAttribute("maxEnchant") ? Integer.parseInt(scroll.getAttribute("maxEnchant")) : 65535;
			require(seen.add(id) && expected.containsKey(id), "Duplicate or unpinned enchant scroll id=" + id);
			require(maximum == expected.get(id), "Enchant max differs for id=" + id);
			if (scroll.hasAttribute("bonusRate") && (Double.parseDouble(scroll.getAttribute("bonusRate")) == 100))
			{
				foundBonus.add(id);
			}
		}
		require(seen.equals(expected.keySet()) && foundBonus.equals(bonus100), "Enchant or bonusRate100 scroll set differs");
		final Set<Integer> supports = new HashSet<>();
		final NodeList supportNodes = _items.getDocumentElement().getElementsByTagName("support");
		for (int i = 0; i < supportNodes.getLength(); i++)
		{
			final Element support = (Element) supportNodes.item(i);
			require(supports.add(Integer.parseInt(support.getAttribute("id"))) && "9".equals(support.getAttribute("maxEnchant")), "Support item limit changed");
		}
		require(supports.equals(ids("support.preserved.9")), "Support item set differs");
	}

	private void addLimits(Map<Integer, Integer> limits, String property, int maximum)
	{
		for (int id : ids(property))
		{
			require(limits.put(id, maximum) == null, "Duplicate scroll in preserved manifest");
		}
	}

	private Set<Integer> ids(String key)
	{
		final Set<Integer> result = new HashSet<>();
		for (String id : _preserved.getProperty(key).split(","))
		{
			require(result.add(Integer.parseInt(id)), "Duplicate id in preserved manifest");
		}
		return result;
	}

	private Properties properties(String relative, Predicate<String> selected) throws IOException
	{
		// Properties.load supplies the same escaping/continuation semantics as ConfigReader.
		// Discard unrelated keys immediately; in particular, never retain or print personal allowlists.
		final Properties result = new Properties()
		{
			@Override
			public synchronized Object put(Object key, Object value)
			{
				if (!selected.test((String) key))
				{
					return null;
				}
				require(!containsKey(key), "Duplicate public property in " + relative);
				return super.put(key, value);
			}
		};
		try (Reader reader = Files.newBufferedReader(file(relative), StandardCharsets.UTF_8))
		{
			result.load(reader);
		}
		return result;
	}

	private Document xml(String relative, String schema) throws Exception
	{
		final SchemaFactory schemas = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		schemas.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		schemas.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setSchema(schemas.newSchema(file(schema).toFile()));
		final var builder = factory.newDocumentBuilder();
		builder.setErrorHandler(new ErrorHandler()
		{
			@Override
			public void warning(SAXParseException exception) throws SAXParseException
			{
				throw exception;
			}
			@Override
			public void error(SAXParseException exception) throws SAXParseException
			{
				throw exception;
			}
			@Override
			public void fatalError(SAXParseException exception) throws SAXParseException
			{
				throw exception;
			}
		});
		try
		{
			return builder.parse(file(relative).toFile());
		}
		catch (Exception e)
		{
			throw new IllegalStateException("XML/XSD validation failed: " + relative);
		}
	}

	private Path file(String relative) throws IOException
	{
		final Path path = _root.resolve(relative).normalize();
		require(path.startsWith(_root), "Path leaves game root");
		for (Path current = path; !current.equals(_root); current = current.getParent())
		{
			require(!Files.isSymbolicLink(current) && !Files.readAttributes(current, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther(), "Linked owner file path refused");
		}
		require(path.toRealPath().startsWith(_root) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Owner file is not an in-root regular file");
		return path;
	}

	private static String relative(String profilePath)
	{
		require(profilePath.startsWith("dist/game/"), "Unknown owner profile path");
		final String result = profilePath.substring("dist/game/".length());
		require(PROFILE_PATHS.contains(result), "Unknown owner profile path");
		return result;
	}

	private static String normalizedHash(Properties properties) throws Exception
	{
		final Map<String, String> sorted = new TreeMap<>();
		for (String key : properties.stringPropertyNames())
		{
			sorted.put(key, properties.getProperty(key).trim());
		}
		final StringBuilder text = new StringBuilder();
		sorted.forEach((key, value) -> text.append(key).append('=').append(value).append('\n'));
		return sha256(text.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static String sha256(byte[] bytes) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private static void require(boolean condition, String message)
	{
		if (!condition)
		{
			throw new IllegalStateException(message);
		}
	}
}

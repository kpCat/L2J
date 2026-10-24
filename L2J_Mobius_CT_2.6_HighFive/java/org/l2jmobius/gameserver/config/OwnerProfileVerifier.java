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
	private static final Set<String> PROFILE_PATHS = Set.of("config/Player.ini", "data/EnchantItemGroups.xml", "data/EnchantItemData.xml", "config/Custom/ServitorShare.ini", "config/Custom/PrivateStoreRange.ini", "config/Custom/PhantomMerchants.ini", "config/Custom/PersonalCharacterQoL.ini", "config/Rates.ini");
	private static final Map<String, ValueType> RULES = canonicalRules();
	private static final Set<String> PRESERVED_REQUIRED_KEYS = Set.of("quest.keys", "quest.sha256", "scroll.ordinary", "scroll.bonus100");
	// EnchantScroll.withinOrdinaryLimit currently rejects the ordinary route at +25.
	private static final int ORDINARY_RUNTIME_LIMIT = 25;
	private final Path _root;
	private final List<String> _failures = new ArrayList<>();
	private final Map<String, Properties> _configs = new HashMap<>();
	private Properties _preserved;
	private Document _groups;
	private Document _items;
	private int _ordinaryMaximum;

	private enum ValueType
	{
		BOOLEAN, INTEGER, WEIGHT, WAREHOUSE, MERCHANT, CUBIC, SERVITOR, RANGES, ENCHANT_LIMIT, PRESERVE
	}

	private OwnerProfileVerifier(Path gameRoot) throws IOException
	{
		_root = gameRoot.toAbsolutePath().normalize().toRealPath();
	}

	public static void main(String[] args)
	{
		final boolean questHash = (args.length == 2) && args[0].equals("--quest-hash");
		if ((args.length != 1) && !questHash)
		{
			System.err.println("Usage: OwnerProfileVerifier [--quest-hash] <gameRoot>");
			System.exit(2);
		}
		try
		{
			final var verifier = new OwnerProfileVerifier(Path.of(args[questHash ? 1 : 0]));
			if (questHash)
			{
				verifier.printQuestHash();
			}
			else
			{
				verifier.verify();
			}
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
		final String profileHash = sha256(Files.readAllBytes(profile));
		final List<String> rows = Files.readAllLines(profile, StandardCharsets.UTF_8);
		require((rows.size() == 39) && rows.get(0).replaceFirst("^\uFEFF", "").equals("section\tfile\tkey_or_range\tvalue\tnote"), "Expected exactly 38 profile rules and the original header");
		final Set<String> identities = new HashSet<>();
		final Map<String, Set<String>> selected = new HashMap<>();
		for (String row : rows.subList(1, rows.size()))
		{
			final String[] fields = row.split("\t", -1);
			require(fields.length == 5, "Malformed owner rule");
			final String relative = relative(fields[1]);
			final String identity = identity(fields[0], relative, fields[2]);
			require(RULES.containsKey(identity), "Unknown owner rule identity");
			require(identities.add(identity), "Duplicate owner rule");
			validatedValue(RULES.get(identity), fields[3]);
			if (RULES.get(identity) == ValueType.ENCHANT_LIMIT)
			{
				_ordinaryMaximum = Integer.parseInt(fields[3].trim());
			}
			if (relative.endsWith(".ini") && !relative.equals("config/Rates.ini"))
			{
				selected.computeIfAbsent(relative, ignored -> new HashSet<>()).add(fields[2]);
			}
		}
		require(identities.equals(RULES.keySet()), "Missing canonical owner rule");
		for (String row : rows.subList(1, rows.size()))
		{
			final String[] fields = row.split("\t", -1);
			if (RULES.get(identity(fields[0], relative(fields[1]), fields[2])) == ValueType.RANGES)
			{
				requireZeroAboveLimit(fields[3], _ordinaryMaximum);
			}
		}
		_preserved = properties("config/OwnerProfilePreserved.properties", ignored -> true);
		require(_preserved.stringPropertyNames().containsAll(PRESERVED_REQUIRED_KEYS), "Missing required preserved baseline key");
		for (String key : _preserved.stringPropertyNames())
		{
			if (!PRESERVED_REQUIRED_KEYS.contains(key))
			{
				preservedLimit(key);
			}
		}
		require(_preserved.getProperty("quest.sha256").matches("[0-9a-fA-F]{64}"), "Invalid quest baseline SHA-256");
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
			boolean passed = false;
			try
			{
				final ValueType type = RULES.get(identity(fields[0], relative(fields[1]), fields[2]));
				passed = validatedValue(type, fields[3]).equals(validatedValue(type, actual));
			}
			catch (IllegalArgumentException | IllegalStateException invalid)
			{
				// An identical invalid value in INI and TSV must not turn a runtime fallback into PASS.
			}
			System.out.println("OWNER_RULE\t" + fields[0] + "\t" + fields[1] + "\t" + fields[2] + "\t" + actual + "\t" + (passed ? "PASS" : "FAIL"));
			if (!passed)
			{
				_failures.add(fields[1] + " / " + fields[2]);
			}
		}
		require(_failures.isEmpty(), "38-rule validation failed: " + String.join("; ", _failures));
		System.out.println("OWNER_PROFILE_PASS rules=38 profileSha256=" + profileHash + " preservedSha256=" + normalizedHash(_preserved) + " questSha256=" + _preserved.getProperty("quest.sha256") + " XML_XSD=PASS READ_ONLY=true");
	}

	private String actualValue(String relative, String key) throws Exception
	{
		if (relative.equals("config/Rates.ini"))
		{
			final Properties rates = questRates();
			require(rates.stringPropertyNames().equals(questKeys()), "Quest effective key set differs");
			require(normalizedHash(rates).equalsIgnoreCase(_preserved.getProperty("quest.sha256")), "Quest effective values differ");
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
				require((found != null) && ((result == null) || validatedRanges(result).equals(validatedRanges(found))), "Missing or divergent enchant rate group");
				result = found;
			}
			requireZeroAboveLimit(result, _ordinaryMaximum);
			return result;
		}
		if (relative.equals("data/EnchantItemData.xml"))
		{
			return Integer.toString(verifyScrolls());
		}
		final Properties config = _configs.get(relative);
		return (config == null) ? "MISSING" : config.getProperty(key, "MISSING").trim();
	}

	private int verifyScrolls()
	{
		final Map<Integer, Integer> expected = new HashMap<>();
		final Map<Integer, Integer> expectedSupports = new HashMap<>();
		addLimits(expected, "scroll.ordinary", _ordinaryMaximum);
		addLimits(expected, "scroll.bonus100", _ordinaryMaximum);
		for (String key : _preserved.stringPropertyNames())
		{
			if (key.startsWith("scroll.preserved."))
			{
				addLimits(expected, key, preservedLimit(key));
			}
			else if (key.startsWith("support.preserved."))
			{
				addLimits(expectedSupports, key, preservedLimit(key));
			}
		}
		final Set<Integer> bonus100 = ids("scroll.bonus100");
		final Set<Integer> seen = new HashSet<>();
		final Set<Integer> foundBonus = new HashSet<>();
		final Set<Integer> ordinary = ids("scroll.ordinary");
		Integer actualOrdinaryMaximum = null;
		final NodeList scrolls = _items.getDocumentElement().getElementsByTagName("enchant");
		for (int i = 0; i < scrolls.getLength(); i++)
		{
			final Element scroll = (Element) scrolls.item(i);
			final int id = Integer.parseInt(scroll.getAttribute("id"));
			final int maximum = scroll.hasAttribute("maxEnchant") ? Integer.parseInt(scroll.getAttribute("maxEnchant")) : 65535;
			require(seen.add(id) && expected.containsKey(id), "Duplicate or unpinned enchant scroll id=" + id);
			require(maximum == expected.get(id), "Enchant max differs for id=" + id);
			if (ordinary.contains(id))
			{
				require((actualOrdinaryMaximum == null) || (actualOrdinaryMaximum == maximum), "Ordinary scroll maxima diverge");
				actualOrdinaryMaximum = maximum;
			}
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
			final int id = Integer.parseInt(support.getAttribute("id"));
			require(supports.add(id) && expectedSupports.containsKey(id), "Duplicate or unpinned support item id=" + id);
			require(Integer.parseInt(support.getAttribute("maxEnchant")) == expectedSupports.get(id), "Support item max differs for id=" + id);
		}
		require(supports.equals(expectedSupports.keySet()), "Support item set differs");
		require(actualOrdinaryMaximum != null, "Ordinary scroll baseline is empty");
		return actualOrdinaryMaximum;
	}

	private void addLimits(Map<Integer, Integer> limits, String property, int maximum)
	{
		for (int id : ids(property))
		{
			require(limits.put(id, maximum) == null, "Duplicate item id in preserved manifest");
		}
	}

	private Set<Integer> ids(String key)
	{
		final Set<Integer> result = new HashSet<>();
		for (String id : _preserved.getProperty(key).split(",", -1))
		{
			final int number = Integer.parseInt(id.trim());
			require((number > 0) && result.add(number), "Invalid or duplicate id in preserved manifest");
		}
		return result;
	}

	private static int preservedLimit(String key)
	{
		require(key.matches("(?:scroll|support)\\.preserved\\.[1-9][0-9]{0,4}"), "Unknown preserved baseline key");
		final int maximum = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
		require(maximum <= 65535, "Preserved maxEnchant must be within XML/runtime range 1..65535");
		return maximum;
	}

	private Set<String> questKeys()
	{
		final Set<String> result = new HashSet<>();
		for (String key : _preserved.getProperty("quest.keys").split(",", -1))
		{
			require(key.matches("[A-Za-z][A-Za-z0-9]*Quest[A-Za-z0-9]*|Quest[A-Za-z0-9]+") && result.add(key), "Invalid or duplicate quest baseline key");
		}
		return result;
	}

	private Properties questRates() throws IOException
	{
		final Properties rates = properties("config/Rates.ini", name -> name.contains("Quest"));
		require(!rates.isEmpty(), "Quest effective key set is empty");
		for (String key : rates.stringPropertyNames())
		{
			final String value = rates.getProperty(key).trim();
			if (key.equals("UseQuestRewardMultipliers"))
			{
				validatedValue(ValueType.BOOLEAN, value);
			}
			else
			{
				// RatesConfig loads quest multipliers as floats, not doubles.
				final float number = Float.parseFloat(value);
				require(Float.isFinite(number) && (number >= 0), "Quest multiplier must be a finite nonnegative float");
			}
		}
		return rates;
	}

	private void printQuestHash() throws Exception
	{
		final Properties rates = questRates();
		System.out.println("quest.keys=" + String.join(",", rates.stringPropertyNames().stream().sorted().toList()));
		System.out.println("quest.sha256=" + normalizedHash(rates));
		System.out.println("READ_ONLY=true; review these values before explicitly changing the preserved baseline");
	}

	private static String identity(String section, String relative, String key)
	{
		return section + "\t" + relative + "\t" + key;
	}

	private static Map<String, ValueType> canonicalRules()
	{
		final Map<String, ValueType> rules = new HashMap<>();
		addRules(rules, "PLAYER", "config/Player.ini", ValueType.WEIGHT, "AltWeightLimit");
		addRules(rules, "PLAYER", "config/Player.ini", ValueType.INTEGER, "MaxPvtStoreSellSlotsDwarf,MaxPvtStoreSellSlotsOther,MaxPvtStoreBuySlotsDwarf,MaxPvtStoreBuySlotsOther,MaximumSlotsForNoDwarf,MaximumSlotsForDwarf,MaximumSlotsForGMPlayer,MaximumSlotsForQuestItems,MaximumFreightSlots,DwarfRecipeLimit,CommonRecipeLimit");
		addRules(rules, "PLAYER", "config/Player.ini", ValueType.WAREHOUSE, "MaximumWarehouseSlotsForDwarf,MaximumWarehouseSlotsForNoDwarf,MaximumWarehouseSlotsForClan");
		addRules(rules, "ENCHANT", "config/Player.ini", ValueType.BOOLEAN, "DisableOverEnchanting,OverEnchantProtection");
		addRules(rules, "ENCHANT", "data/EnchantItemGroups.xml", ValueType.RANGES, "ARMOR_GROUP/FIGHTER_WEAPON_GROUP/MAGE_WEAPON_GROUP.current,FULL_ARMOR_GROUP.current");
		addRules(rules, "ENCHANT", "data/EnchantItemData.xml", ValueType.ENCHANT_LIMIT, "standard scroll maxEnchant previously16");
		addRules(rules, "SERVITOR", "config/Custom/ServitorShare.ini", ValueType.BOOLEAN, "EnableServitorShareBonusTuning");
		addRules(rules, "SERVITOR", "config/Custom/ServitorShare.ini", ValueType.SERVITOR, "ServitorSharePAtkBonusMultiplier,ServitorSharePDefBonusMultiplier,ServitorShareMAtkBonusMultiplier,ServitorShareMDefBonusMultiplier,ServitorShareMaxHpBonusMultiplier,ServitorShareMaxMpBonusMultiplier,ServitorShareMaxRecoverableHpBonusMultiplier,ServitorShareMaxRecoverableMpBonusMultiplier,ServitorShareCritRateBonusMultiplier,ServitorSharePAtkSpdBonusMultiplier,ServitorShareMAtkSpdBonusMultiplier");
		addRules(rules, "STORE", "config/Custom/PrivateStoreRange.ini", ValueType.INTEGER, "ShopMinRangeFromPlayer,ShopMinRangeFromNpc");
		addRules(rules, "STORE", "config/Custom/PhantomMerchants.ini", ValueType.MERCHANT, "MerchantLinesPerStore");
		addRules(rules, "CUBIC", "config/Custom/PersonalCharacterQoL.ini", ValueType.BOOLEAN, "EnablePersonalWondrousCubicCooldown");
		addRules(rules, "CUBIC", "config/Custom/PersonalCharacterQoL.ini", ValueType.CUBIC, "PersonalWondrousCubicCooldownMinutes");
		addRules(rules, "QUEST", "config/Rates.ini", ValueType.PRESERVE, "Quest rates (all keys)");
		require(rules.size() == 38, "Internal canonical owner rule shape differs");
		return Map.copyOf(rules);
	}

	private static void addRules(Map<String, ValueType> rules, String section, String relative, ValueType type, String keys)
	{
		for (String key : keys.split(","))
		{
			require(rules.put(identity(section, relative, key), type) == null, "Internal duplicate canonical owner rule");
		}
	}

	private static String validatedValue(ValueType type, String value)
	{
		value = value.trim();
		if (type == ValueType.BOOLEAN)
		{
			require(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"), "Owner switch must be True or False");
			return Boolean.toString(Boolean.parseBoolean(value));
		}
		if ((type == ValueType.SERVITOR) || (type == ValueType.WEIGHT))
		{
			final double number = Double.parseDouble(value);
			require(Double.isFinite(number) && (number >= 0), "Owner multiplier must be finite and nonnegative");
			require((type != ValueType.SERVITOR) || (number <= 100), "Servitor Share multiplier exceeds runtime range 0..100");
			return Double.toString(number == 0 ? 0 : number);
		}
		if (type == ValueType.PRESERVE)
		{
			require(value.equals("PRESERVE_EXISTING_VALUES"), "Quest rule requires an explicit preserved baseline");
			return value;
		}
		if (type == ValueType.RANGES)
		{
			return validatedRanges(value);
		}
		final int number = Integer.parseInt(value);
		require(number >= 0, "Owner count/range must be a nonnegative int");
		// The warehouse ceiling is documented in Player.ini; the other bounds are runtime validators.
		require((type != ValueType.WAREHOUSE) || (number < 300), "Warehouse base slots must remain below 300");
		require((type != ValueType.MERCHANT) || ((number >= 1) && (number <= 20)), "Merchant lines exceed runtime range 1..20");
		require((type != ValueType.CUBIC) || (value.matches("[1-9][0-9]{0,3}") && (number <= 1440)), "Cubic cooldown exceeds runtime range 1..1440 minutes");
		require((type != ValueType.ENCHANT_LIMIT) || ((number >= 1) && (number <= ORDINARY_RUNTIME_LIMIT)), "Ordinary enchant maximum exceeds the current runtime ceiling 25");
		return Integer.toString(number);
	}

	private static String validatedRanges(String value)
	{
		int next = 0;
		final List<String> normalized = new ArrayList<>();
		for (String entry : value.split(";", -1))
		{
			final String[] fields = entry.split(":", -1);
			require((fields.length == 2) && fields[0].matches("[0-9]+(?:-[0-9]+)?"), "Invalid enchant chance range");
			final String[] bounds = fields[0].split("-");
			final int first = Integer.parseInt(bounds[0]);
			final int last = Integer.parseInt(bounds[bounds.length - 1]);
			final double chance = Double.parseDouble(fields[1]);
			require((first == next) && (last >= first) && (last <= 65535), "Enchant ranges must cover 0..65535 once without gaps or overlap");
			require(Double.isFinite(chance) && (chance >= 0) && (chance <= 100), "Enchant chance must be finite and within 0..100");
			normalized.add(first + "-" + last + ":" + Double.toString(chance == 0 ? 0 : chance));
			next = last + 1;
		}
		require(next == 65536, "Enchant ranges must cover 0..65535");
		return String.join(";", normalized);
	}

	private static void requireZeroAboveLimit(String ranges, int maximum)
	{
		validatedRanges(ranges);
		for (String range : ranges.split(";"))
		{
			final String[] fields = range.split(":");
			final String[] bounds = fields[0].split("-");
			final int last = Integer.parseInt(bounds[bounds.length - 1]);
			require((last < maximum) || (Double.parseDouble(fields[1]) == 0), "Enchant chance must be zero at and above the ordinary maximum");
		}
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

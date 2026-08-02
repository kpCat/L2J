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
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.economy;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Strict, ordered and content-addressed Checkpoint 1 economy policy. */
public record PhantomEconomyPolicy(String hash, Limits limits, Craft craft, Enchant enchant, Risk risk, List<String> reasonKeys)
{
	private static final int MAX_BYTES = 32 * 1024;
	private static final List<String> REQUIRED_REASONS = List.of("authority.stale", "background.active_required", "dispatch.ambiguous", "goal.invalid", "operation.conflict", "operation.expired", "operation.shutdown", "resource.drift", "result.blessed_reset", "result.craft_failed", "result.destroyed", "result.safe_failure", "result.success");

	public PhantomEconomyPolicy
	{
		reasonKeys = List.copyOf(reasonKeys);
	}

	public static PhantomEconomyPolicy load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Economy policy size is invalid.");
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
			require(root, "economyPolicy", Set.of("id", "version"), true);
			if (!"high-five-economy-v1".equals(root.getAttribute("id")) || !"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Economy policy identity is invalid.");
			}
			final List<Element> sections = children(root);
			if (!sections.stream().map(Element::getTagName).toList().equals(List.of("limits", "craft", "enchant", "risk", "reasonKeys")))
			{
				throw new IllegalArgumentException("Economy policy sections are not exact.");
			}
			final Limits limits = parseLimits(sections.get(0));
			final Craft craft = parseCraft(sections.get(1));
			final Enchant enchant = parseEnchant(sections.get(2));
			final Risk risk = parseRisk(sections.get(3));
			final List<String> reasons = parseReasons(sections.get(4));
			if (!reasons.equals(REQUIRED_REASONS))
			{
				throw new IllegalArgumentException("Economy reason keys are not exact.");
			}
			return new PhantomEconomyPolicy(hash(bytes), limits, craft, enchant, risk, reasons);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load economy policy.", exception);
		}
	}

	private static Limits parseLimits(Element element)
	{
		require(element, "limits", Set.of("payloadBytes", "reservationsPerOperation", "itemIdsPerRead", "participantsPerOperation", "activeOperationsPerProfile", "retainedNonterminalOperations", "reservationTtlSeconds", "observationTimeoutSeconds", "craftAttempts", "enchantAttempts", "scrollCandidates", "supportCandidates", "reconciliationAttempts", "auditRowsPerProfile"), false);
		return new Limits(integer(element, "payloadBytes"), integer(element, "reservationsPerOperation"), integer(element, "itemIdsPerRead"), integer(element, "participantsPerOperation"), integer(element, "activeOperationsPerProfile"), integer(element, "retainedNonterminalOperations"), integer(element, "reservationTtlSeconds"), integer(element, "observationTimeoutSeconds"), integer(element, "craftAttempts"), integer(element, "enchantAttempts"), integer(element, "scrollCandidates"), integer(element, "supportCandidates"), integer(element, "reconciliationAttempts"), integer(element, "auditRowsPerProfile"));
	}

	private static Craft parseCraft(Element element)
	{
		require(element, "craft", Set.of("enabled", "allowAltGameCreation"), false);
		return new Craft(bool(element, "enabled"), bool(element, "allowAltGameCreation"));
	}

	private static Enchant parseEnchant(Element element)
	{
		require(element, "enchant", Set.of("enabled", "requireExplicitDestructionPermission", "equippedBackground"), false);
		return new Enchant(bool(element, "enabled"), bool(element, "requireExplicitDestructionPermission"), BackgroundEquipped.valueOf(element.getAttribute("equippedBackground")));
	}

	private static Risk parseRisk(Element element)
	{
		require(element, "risk", Set.of("maximumExpensePercent", "replacementReservePercent"), false);
		return new Risk(integer(element, "maximumExpensePercent"), integer(element, "replacementReservePercent"));
	}

	private static List<String> parseReasons(Element element)
	{
		require(element, "reasonKeys", Set.of(), true);
		final List<String> result = new ArrayList<>();
		for (Element child : children(element))
		{
			require(child, "reason", Set.of("key"), false);
			result.add(child.getAttribute("key"));
		}
		if ((new HashSet<>(result).size() != result.size()) || !result.equals(result.stream().sorted().toList()))
		{
			throw new IllegalArgumentException("Economy reason keys must be unique and sorted.");
		}
		return List.copyOf(result);
	}

	private static int integer(Element element, String name)
	{
		try
		{
			return Integer.parseInt(element.getAttribute(name));
		}
		catch (NumberFormatException exception)
		{
			throw new IllegalArgumentException("Invalid economy integer: " + name, exception);
		}
	}

	private static boolean bool(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!"true".equals(value) && !"false".equals(value))
		{
			throw new IllegalArgumentException("Invalid economy boolean: " + name);
		}
		return Boolean.parseBoolean(value);
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
				throw new IllegalArgumentException("Unexpected economy policy text.");
			}
		}
		return result;
	}

	private static void require(Element element, String name, Set<String> attributes, boolean childrenAllowed)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()) || !attributes.stream().allMatch(element::hasAttribute) || (!childrenAllowed && !children(element).isEmpty()))
		{
			throw new IllegalArgumentException("Invalid economy policy element: " + name);
		}
	}

	private static void strictUtf8(byte[] bytes) throws CharacterCodingException
	{
		StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
	}

	private static String hash(byte[] bytes) throws Exception
	{
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	public record Limits(int payloadBytes, int reservationsPerOperation, int itemIdsPerRead, int participantsPerOperation, int activeOperationsPerProfile, int retainedNonterminalOperations, int reservationTtlSeconds, int observationTimeoutSeconds, int craftAttempts, int enchantAttempts, int scrollCandidates, int supportCandidates, int reconciliationAttempts, int auditRowsPerProfile)
	{
		public Limits
		{
			if ((payloadBytes != 4096) || (reservationsPerOperation != 32) || (itemIdsPerRead != 24) || (participantsPerOperation != 4) || (activeOperationsPerProfile != 1) || (retainedNonterminalOperations != 100000) || (reservationTtlSeconds < 30) || (reservationTtlSeconds > 600) || (observationTimeoutSeconds < 30) || (observationTimeoutSeconds > 300) || (craftAttempts != 32) || (enchantAttempts != 16) || (scrollCandidates != 16) || (supportCandidates != 8) || (reconciliationAttempts != 3) || (auditRowsPerProfile != 256))
			{
				throw new IllegalArgumentException("Economy limits do not match the Checkpoint 1 contract.");
			}
		}
	}

	public record Craft(boolean enabled, boolean allowAltGameCreation)
	{
		public Craft
		{
			if (!enabled || allowAltGameCreation)
			{
				throw new IllegalArgumentException("Checkpoint 1 craft policy must match current shipped non-ALT creation.");
			}
		}
	}

	public record Enchant(boolean enabled, boolean requireExplicitDestructionPermission, BackgroundEquipped equippedBackground)
	{
		public Enchant
		{
			if (!enabled || !requireExplicitDestructionPermission || (equippedBackground != BackgroundEquipped.ACTIVE_REQUIRED))
			{
				throw new IllegalArgumentException("Checkpoint 1 enchant policy is invalid.");
			}
		}
	}

	public record Risk(int maximumExpensePercent, int replacementReservePercent)
	{
		public Risk
		{
			if ((maximumExpensePercent < 0) || (maximumExpensePercent > 100) || (replacementReservePercent < 0) || (replacementReservePercent > 100))
			{
				throw new IllegalArgumentException("Invalid economy risk policy.");
			}
		}
	}

	public enum BackgroundEquipped
	{
		ACTIVE_REQUIRED
	}
}

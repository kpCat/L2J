/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.farming;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Strict content-addressed Goal 024 convergence policy. */
public final class PhantomFarmingPolicy
{
	private static final int MAX_BYTES = 64 * 1024;

	public record Limits(int claimLeaseMinutes, int negotiationTtlMinutes, int waitMinutes, int pairCooldownMinutes, int maximumRounds, int maximumAlternatives, int maximumClaimants, int perceptionLimit, int historyReceipts)
	{
		public Limits
		{
			if ((claimLeaseMinutes < 1) || (claimLeaseMinutes > 60) || (negotiationTtlMinutes < claimLeaseMinutes) || (negotiationTtlMinutes > 1440) || (waitMinutes < 1) || (waitMinutes > 1440) || (pairCooldownMinutes < 1) || (pairCooldownMinutes > negotiationTtlMinutes) || (maximumRounds < 1) || (maximumRounds > 8) || (maximumAlternatives < 1) || (maximumAlternatives > PhantomFarmingModel.MAX_ALTERNATIVES) || (maximumClaimants < 2) || (maximumClaimants > 64) || (perceptionLimit < maximumClaimants) || (perceptionLimit > 64) || (historyReceipts < 1) || (historyReceipts > PhantomFarmingModel.MAX_HISTORY))
			{
				throw new IllegalArgumentException("Farming policy limits are outside bounds.");
			}
		}
	}

	public record Thresholds(int shareCooperation, int refuseCooperation, int escalation)
	{
		public Thresholds
		{
			if ((shareCooperation < -3000) || (shareCooperation > 3000) || (refuseCooperation < -3000) || (refuseCooperation >= shareCooperation) || (escalation < -3000) || (escalation > 3000))
			{
				throw new IllegalArgumentException("Farming policy thresholds are invalid.");
			}
		}
	}

	public record Weights(int priority, int remaining, int progress, int claimAge, int alternative, int persistence)
	{
		public Weights
		{
			for (int value : new int[]
			{
				priority,
				remaining,
				progress,
				claimAge,
				alternative,
				persistence
			})
			{
				if ((value < -100) || (value > 100))
				{
					throw new IllegalArgumentException("Farming arbitration weight is outside bounds.");
				}
			}
			if ((priority == 0) && (remaining == 0) && (progress == 0) && (claimAge == 0) && (alternative == 0) && (persistence == 0))
			{
				throw new IllegalArgumentException("Farming arbitration weights are all zero.");
			}
		}
	}

	private final String _id;
	private final String _hash;
	private final Limits _limits;
	private final Thresholds _thresholds;
	private final Weights _weights;

	private PhantomFarmingPolicy(String id, String hash, Limits limits, Thresholds thresholds, Weights weights)
	{
		_id = id;
		_hash = hash;
		_limits = limits;
		_thresholds = thresholds;
		_weights = weights;
	}

	public static PhantomFarmingPolicy load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("Farming policy size is outside bounds.");
			}
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			require(root, "farmingConflictPolicy", Set.of("id", "version"));
			if (!"1".equals(root.getAttribute("version")))
			{
				throw new IllegalArgumentException("Unknown farming conflict policy version.");
			}
			final String id = PhantomFarmingModel.bounded(root.getAttribute("id"), 64, "Farming policy ID");
			final List<Element> sections = children(root);
			if (!sections.stream().map(Element::getTagName).toList().equals(List.of("limits", "thresholds", "weights")))
			{
				throw new IllegalArgumentException("Farming policy sections are not exact.");
			}
			final Element limits = sections.get(0);
			require(limits, "limits", Set.of("claimLeaseMinutes", "negotiationTtlMinutes", "waitMinutes", "pairCooldownMinutes", "maximumRounds", "maximumAlternatives", "maximumClaimants", "perceptionLimit", "historyReceipts"));
			final Limits parsedLimits = new Limits(integer(limits, "claimLeaseMinutes"), integer(limits, "negotiationTtlMinutes"), integer(limits, "waitMinutes"), integer(limits, "pairCooldownMinutes"), integer(limits, "maximumRounds"), integer(limits, "maximumAlternatives"), integer(limits, "maximumClaimants"), integer(limits, "perceptionLimit"), integer(limits, "historyReceipts"));
			final Element thresholds = sections.get(1);
			require(thresholds, "thresholds", Set.of("shareCooperation", "refuseCooperation", "escalation"));
			final Thresholds parsedThresholds = new Thresholds(integer(thresholds, "shareCooperation"), integer(thresholds, "refuseCooperation"), integer(thresholds, "escalation"));
			final Element weights = sections.get(2);
			require(weights, "weights", Set.of("priority", "remaining", "progress", "claimAge", "alternative", "persistence"));
			final Weights parsedWeights = new Weights(integer(weights, "priority"), integer(weights, "remaining"), integer(weights, "progress"), integer(weights, "claimAge"), integer(weights, "alternative"), integer(weights, "persistence"));
			sections.forEach(PhantomFarmingPolicy::requireNoChildren);
			final String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			return new PhantomFarmingPolicy(id, hash, parsedLimits, parsedThresholds, parsedWeights);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load strict farming conflict policy.", exception);
		}
	}

	public String id()
	{
		return _id;
	}

	public String hash()
	{
		return _hash;
	}

	public Limits limits()
	{
		return _limits;
	}

	public Thresholds thresholds()
	{
		return _thresholds;
	}

	public Weights weights()
	{
		return _weights;
	}

	private static int integer(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!value.matches("-?(0|[1-9][0-9]{0,8})"))
		{
			throw new IllegalArgumentException("Farming policy integer is not canonical: " + name);
		}
		return Integer.parseInt(value);
	}

	private static void require(Element element, String name, Set<String> attributes)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()))
		{
			throw new IllegalArgumentException("Farming policy element is not exact: " + name);
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute))
			{
				throw new IllegalArgumentException("Farming policy attribute is absent: " + attribute);
			}
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
				throw new IllegalArgumentException("Farming policy contains unexpected text.");
			}
		}
		return List.copyOf(result);
	}

	private static void requireNoChildren(Element element)
	{
		if (!children(element).isEmpty())
		{
			throw new IllegalArgumentException("Farming policy leaf contains children.");
		}
	}
}

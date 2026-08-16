/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Candidate;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Decision;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Encounter;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Outcome;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.RiskSnapshot;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Stage;
import org.w3c.dom.Element;

/** Strict, content-addressed and worker-free Goal 025 policy. */
public final class PhantomPvpPolicy
{
	private static final int MAX_BYTES = 16 * 1024;
	private static final Set<String> ROOT_ATTRIBUTES = Set.of("id", "version");
	private static final Set<String> LIMIT_ATTRIBUTES = Set.of("observedAttackerLimit", "localRiskPlayerLimit", "profilesPerPulse", "combatTimeoutSeconds", "retreatTimeoutSeconds", "cpPotionThresholdPercent", "encounterTtlSeconds", "warningDelaySeconds", "pairCooldownSeconds", "maxProactiveEngagementsPerPair", "helpFanout", "retreatHpPercent", "retreatEffectivePoolPercent", "engageMinimumStrengthBasisPoints", "forcedPkMaximumRiskBasisPoints");

	public record Limits(int observedAttackerLimit, int localRiskPlayerLimit, int profilesPerPulse, int combatTimeoutSeconds, int retreatTimeoutSeconds, int cpPotionThresholdPercent, int encounterTtlSeconds, int warningDelaySeconds, int pairCooldownSeconds, int maxProactiveEngagementsPerPair, int helpFanout, int retreatHpPercent, int retreatEffectivePoolPercent, int engageMinimumStrengthBasisPoints, int forcedPkMaximumRiskBasisPoints)
	{
		public Limits
		{
			if ((observedAttackerLimit < 1) || (observedAttackerLimit > 32) || (localRiskPlayerLimit < 1) || (localRiskPlayerLimit > 32) || (profilesPerPulse < 1) || (profilesPerPulse > 64) || (combatTimeoutSeconds < 1) || (combatTimeoutSeconds > 300) || (retreatTimeoutSeconds < 1) || (retreatTimeoutSeconds > 300) || (cpPotionThresholdPercent < 1) || (cpPotionThresholdPercent > 80) || (encounterTtlSeconds < 10) || (encounterTtlSeconds > 3600) || (warningDelaySeconds < 1) || (warningDelaySeconds >= encounterTtlSeconds) || (pairCooldownSeconds < 1) || (pairCooldownSeconds > 86400) || (maxProactiveEngagementsPerPair < 1) || (maxProactiveEngagementsPerPair > 8) || (helpFanout < 1) || (helpFanout > 8) || (retreatHpPercent < 1) || (retreatHpPercent > 80) || (retreatEffectivePoolPercent < retreatHpPercent) || (retreatEffectivePoolPercent > 80) || (engageMinimumStrengthBasisPoints < 1000) || (engageMinimumStrengthBasisPoints > 20000) || (forcedPkMaximumRiskBasisPoints < 0) || (forcedPkMaximumRiskBasisPoints > 10000))
			{
				throw new IllegalArgumentException("PvP policy limits are outside safe bounds.");
			}
		}
	}

	private final String _id;
	private final String _hash;
	private final Limits _limits;

	private PhantomPvpPolicy(String id, String hash, Limits limits)
	{
		_id = id;
		_hash = hash;
		_limits = limits;
	}

	public static PhantomPvpPolicy load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > MAX_BYTES))
			{
				throw new IllegalArgumentException("PvP policy size is outside bounds.");
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
			require(root, "pvpThreatPolicy", ROOT_ATTRIBUTES);
			if (!"1".equals(root.getAttribute("version")) || (root.getChildNodes().getLength() != 3))
			{
				throw new IllegalArgumentException("Unknown or non-canonical PvP policy document.");
			}
			Element limits = null;
			for (int i = 0; i < root.getChildNodes().getLength(); i++)
			{
				if (root.getChildNodes().item(i) instanceof Element element)
				{
					if (limits != null)
					{
						throw new IllegalArgumentException("PvP policy contains duplicate sections.");
					}
					limits = element;
				}
				else if (!root.getChildNodes().item(i).getTextContent().isBlank())
				{
					throw new IllegalArgumentException("PvP policy contains unexpected text.");
				}
			}
			if (limits == null)
			{
				throw new IllegalArgumentException("PvP policy limits are absent.");
			}
			require(limits, "limits", LIMIT_ATTRIBUTES);
			if (limits.hasChildNodes() && !limits.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("PvP policy limits contain content.");
			}
			final Limits parsed = new Limits(integer(limits, "observedAttackerLimit"), integer(limits, "localRiskPlayerLimit"), integer(limits, "profilesPerPulse"), integer(limits, "combatTimeoutSeconds"), integer(limits, "retreatTimeoutSeconds"), integer(limits, "cpPotionThresholdPercent"), integer(limits, "encounterTtlSeconds"), integer(limits, "warningDelaySeconds"), integer(limits, "pairCooldownSeconds"), integer(limits, "maxProactiveEngagementsPerPair"), integer(limits, "helpFanout"), integer(limits, "retreatHpPercent"), integer(limits, "retreatEffectivePoolPercent"), integer(limits, "engageMinimumStrengthBasisPoints"), integer(limits, "forcedPkMaximumRiskBasisPoints"));
			final String id = PhantomPvpModel.bounded(root.getAttribute("id"), 64, "PvP policy ID");
			final String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			return new PhantomPvpPolicy(id, hash, parsed);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load strict PvP policy.", exception);
		}
	}

	public Outcome decide(Candidate candidate, RiskSnapshot risk, Encounter encounter, long logicalNanos)
	{
		if (!candidate.currentAt(logicalNanos) || !risk.legal())
		{
			return new Outcome(Decision.DISENGAGE, "stale-or-forbidden", false);
		}
		if (logicalNanos < encounter.cooldownUntilLogicalNanos())
		{
			return new Outcome(Decision.COOLDOWN, "pair-cooldown", false);
		}
		if ((risk.actorHpPercent() <= _limits.retreatHpPercent()) || (risk.actorEffectivePoolPercent() <= _limits.retreatEffectivePoolPercent()) || (risk.relativeStrengthBasisPoints() < _limits.engageMinimumStrengthBasisPoints()))
		{
			return new Outcome(Decision.RETREAT, "bounded-risk-retreat", false);
		}
		if (!candidate.source().proactive())
		{
			return new Outcome(candidate.source() == PhantomPvpModel.Source.PARTY_DEFENSE ? Decision.HELP : Decision.ENGAGE, "reactive-source", false);
		}
		if ((risk.forcedPkRiskBasisPoints() > _limits.forcedPkMaximumRiskBasisPoints()) || (encounter.proactiveEngagements() >= _limits.maxProactiveEngagementsPerPair()))
		{
			return new Outcome(Decision.COOLDOWN, "proactive-budget-or-risk", false);
		}
		if (encounter.stage() == Stage.OBSERVE)
		{
			return new Outcome(Decision.WARN, "warning-required", false);
		}
		if ((encounter.stage() != Stage.WARN) || encounter.warningReceiptId().isEmpty())
		{
			return new Outcome(Decision.WAIT, "warning-receipt-required", false);
		}
		final long warningDelay = Math.multiplyExact(_limits.warningDelaySeconds(), 1_000_000_000L);
		if ((encounter.warningLogicalNanos() <= 0) || ((logicalNanos - encounter.warningLogicalNanos()) < warningDelay))
		{
			return new Outcome(Decision.WAIT, "warning-delay", false);
		}
		return new Outcome(Decision.ENGAGE, "proactive-authority", !risk.targetAutoAttackable());
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

	private static int integer(Element element, String name)
	{
		final String value = element.getAttribute(name);
		if (!value.matches("(0|[1-9][0-9]{0,8})"))
		{
			throw new IllegalArgumentException("PvP policy integer is not canonical: " + name);
		}
		return Integer.parseInt(value);
	}

	private static void require(Element element, String name, Set<String> attributes)
	{
		if (!name.equals(element.getTagName()) || (element.getAttributes().getLength() != attributes.size()))
		{
			throw new IllegalArgumentException("PvP policy element is not exact: " + name);
		}
		for (String attribute : attributes)
		{
			if (!element.hasAttribute(attribute))
			{
				throw new IllegalArgumentException("PvP policy attribute is absent: " + attribute);
			}
		}
	}
}

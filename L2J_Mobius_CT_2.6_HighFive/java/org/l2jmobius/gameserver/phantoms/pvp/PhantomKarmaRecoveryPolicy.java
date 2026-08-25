/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpModel.Source;
import org.w3c.dom.Element;

/** Strict, immutable and pure policy for the native karma recovery overlay. */
public final class PhantomKarmaRecoveryPolicy
{
	private static final long MAXIMUM_FILE_BYTES = 16 * 1024;
	private static final Set<String> ATTRIBUTES = Set.of("id", "version", "suppressProactiveNonWar", "yieldToActualAttack", "allowYieldInClanWar", "allowYieldWhileInParty", "requireExperienceRecovered", "maxIntentionalDeathDropRiskBasisPoints");

	public enum Decision
	{
		NORMAL,
		SUPPRESS_PROACTIVE,
		YIELD
	}

	public enum Reason
	{
		CLEAN,
		NORMAL,
		PROACTIVE_SUPPRESSED,
		SAFE_YIELD,
		XP_DEBT,
		UNSAFE
	}

	public record Settings(boolean suppressProactiveNonWar, boolean yieldToActualAttack, boolean allowYieldInClanWar, boolean allowYieldWhileInParty, boolean requireExperienceRecovered, int maxIntentionalDeathDropRiskBasisPoints)
	{
		public Settings
		{
			if ((maxIntentionalDeathDropRiskBasisPoints < 0) || (maxIntentionalDeathDropRiskBasisPoints > 10000))
			{
				throw new IllegalArgumentException("Karma recovery drop risk basis points are out of range.");
			}
		}
	}

	public record Snapshot(boolean available, int karma, int pkKills, long currentExp, long expBeforeDeath, long expDebt, int deathDropRiskBasisPoints, int predictedKarmaAfterNativeDeath, boolean inParty, boolean activeClanWarAgainstCounterpart)
	{
		public static final Snapshot UNAVAILABLE = new Snapshot(false, 0, 0, 0, 0, 0, 0, 0, false, false);

		public Snapshot
		{
			if ((karma < 0) || (pkKills < 0) || (currentExp < 0) || (expBeforeDeath < 0) || (expDebt < 0) || (deathDropRiskBasisPoints < 0) || (deathDropRiskBasisPoints > 10000) || (predictedKarmaAfterNativeDeath < 0) || (predictedKarmaAfterNativeDeath > karma))
			{
				throw new IllegalArgumentException("Invalid karma recovery snapshot.");
			}
		}
	}

	public record Outcome(Decision decision, Reason reason)
	{
		public Outcome
		{
			Objects.requireNonNull(decision);
			Objects.requireNonNull(reason);
		}
	}

	private final String _id;
	private final String _hash;
	private final Settings _settings;

	private PhantomKarmaRecoveryPolicy(String id, String hash, Settings settings)
	{
		_id = Objects.requireNonNull(id);
		_hash = Objects.requireNonNull(hash);
		_settings = Objects.requireNonNull(settings);
	}

	public static PhantomKarmaRecoveryPolicy neutral()
	{
		return new PhantomKarmaRecoveryPolicy("neutral", "", new Settings(false, false, false, false, true, 0));
	}
	public static PhantomKarmaRecoveryPolicy load(Path path)
	{
		try
		{
			if (!Files.isRegularFile(path) || (Files.size(path) > MAXIMUM_FILE_BYTES))
			{
				throw new IllegalArgumentException("Karma recovery policy file is absent or too large.");
			}
			final byte[] bytes = Files.readAllBytes(path);
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
			require(root);
			if (!"1".equals(root.getAttribute("version")) || root.hasChildNodes())
			{
				throw new IllegalArgumentException("Karma recovery policy version or structure is not exact.");
			}
			final Settings settings = new Settings(bool(root, "suppressProactiveNonWar"), bool(root, "yieldToActualAttack"), bool(root, "allowYieldInClanWar"), bool(root, "allowYieldWhileInParty"), bool(root, "requireExperienceRecovered"), basisPoints(root, "maxIntentionalDeathDropRiskBasisPoints"));
			final String id = bounded(root.getAttribute("id"));
			final String hash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
			return new PhantomKarmaRecoveryPolicy(id, hash, settings);
		}
		catch (RuntimeException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load strict karma recovery policy.", exception);
		}
	}

	public Outcome evaluate(Source source, boolean targetAutoAttackable, Snapshot snapshot)
	{
		Objects.requireNonNull(source);
		Objects.requireNonNull(snapshot);
		if (!snapshot.available() || (snapshot.karma() <= 0))
		{
			return new Outcome(Decision.NORMAL, Reason.CLEAN);
		}
		if (source.proactive() && _settings.suppressProactiveNonWar() && !snapshot.activeClanWarAgainstCounterpart())
		{
			return new Outcome(Decision.SUPPRESS_PROACTIVE, Reason.PROACTIVE_SUPPRESSED);
		}
		if ((source != Source.ACTUAL_ATTACK) || !_settings.yieldToActualAttack())
		{
			return new Outcome(Decision.NORMAL, Reason.NORMAL);
		}
		if (_settings.requireExperienceRecovered() && (snapshot.expDebt() > 0))
		{
			return new Outcome(Decision.NORMAL, Reason.XP_DEBT);
		}
		if ((snapshot.activeClanWarAgainstCounterpart() && !_settings.allowYieldInClanWar()) || (snapshot.inParty() && !_settings.allowYieldWhileInParty()) || (snapshot.deathDropRiskBasisPoints() > _settings.maxIntentionalDeathDropRiskBasisPoints()))
		{
			return new Outcome(Decision.NORMAL, Reason.UNSAFE);
		}
		return new Outcome(Decision.YIELD, Reason.SAFE_YIELD);
	}

	public String id()
	{
		return _id;
	}

	public String hash()
	{
		return _hash;
	}

	public Settings settings()
	{
		return _settings;
	}
	private static boolean bool(Element root, String name)
	{
		final String value = root.getAttribute(name);
		if (!"true".equals(value) && !"false".equals(value))
		{
			throw new IllegalArgumentException("Karma recovery boolean is not canonical: " + name);
		}
		return Boolean.parseBoolean(value);
	}

	private static int basisPoints(Element root, String name)
	{
		final String value = root.getAttribute(name);
		if (!value.matches("(0|[1-9][0-9]{0,4})"))
		{
			throw new IllegalArgumentException("Karma recovery basis points are not canonical.");
		}
		return Integer.parseInt(value);
	}

	private static String bounded(String value)
	{
		if ((value == null) || value.isBlank() || (value.length() > 64))
		{
			throw new IllegalArgumentException("Karma recovery policy ID is invalid.");
		}
		return value;
	}

	private static void require(Element root)
	{
		if (!"karmaRecoveryPolicy".equals(root.getTagName()) || (root.getAttributes().getLength() != ATTRIBUTES.size()))
		{
			throw new IllegalArgumentException("Karma recovery policy root is not exact.");
		}
		for (String attribute : ATTRIBUTES)
		{
			if (!root.hasAttribute(attribute))
			{
				throw new IllegalArgumentException("Karma recovery policy attribute is absent: " + attribute);
			}
		}
	}
}

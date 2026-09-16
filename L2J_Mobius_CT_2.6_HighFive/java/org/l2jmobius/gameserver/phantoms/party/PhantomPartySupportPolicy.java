/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;

/** Small immutable policy for automatic and requested native support. */
public record PhantomPartySupportPolicy(int rebuffRemainingSeconds, int maximumMaintenanceDirectives, int maximumCombatMaintenanceDirectives, int idlePriority, int combatPriority, int neutralEmpathyMinimum, int neutralSociabilityMinimum, boolean rivalHelpAllowed)
{
	private static final int MAX_BYTES = 4096;

	public PhantomPartySupportPolicy
	{
		if ((rebuffRemainingSeconds < 5) || (rebuffRemainingSeconds > 300) || (maximumMaintenanceDirectives < 1) || (maximumMaintenanceDirectives > 4) || (maximumCombatMaintenanceDirectives < 0) || (maximumCombatMaintenanceDirectives > maximumMaintenanceDirectives) || (idlePriority < 1) || (idlePriority >= 6000) || (combatPriority < 1) || (combatPriority > idlePriority) || (neutralEmpathyMinimum < 0) || (neutralEmpathyMinimum > 10000) || (neutralSociabilityMinimum < 0) || (neutralSociabilityMinimum > 10000))
		{
			throw new IllegalArgumentException("Party support policy is outside hard bounds.");
		}
	}

	public static PhantomPartySupportPolicy defaults()
	{
		return new PhantomPartySupportPolicy(45, 2, 1, 5000, 4500, 6500, 7000, false);
	}

	public static PhantomPartySupportPolicy load(Path path)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length < 1) || (bytes.length > MAX_BYTES) || Files.isSymbolicLink(path))
			{
				throw new IllegalArgumentException("Party support policy file is invalid.");
			}
			StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
			if (!root.getTagName().equals("partySupportPolicy") || !root.getAttribute("id").equals("high-five-party-support-v1") || !root.getAttribute("version").equals("1"))
			{
				throw new IllegalArgumentException("Party support policy identity is invalid.");
			}
			final Set<String> expected = Set.of("id", "version", "rebuffRemainingSeconds", "maximumMaintenanceDirectives", "maximumCombatMaintenanceDirectives", "idlePriority", "combatPriority", "neutralEmpathyMinimum", "neutralSociabilityMinimum", "rivalHelpAllowed");
			if ((root.getAttributes().getLength() != expected.size()) || java.util.stream.IntStream.range(0, root.getAttributes().getLength()).mapToObj(index -> root.getAttributes().item(index).getNodeName()).anyMatch(name -> !expected.contains(name)) || root.hasChildNodes() && !root.getTextContent().isBlank())
			{
				throw new IllegalArgumentException("Party support policy shape is invalid.");
			}
			final String rival = root.getAttribute("rivalHelpAllowed");
			if (!rival.equals("true") && !rival.equals("false"))
			{
				throw new IllegalArgumentException("Party support rival policy is invalid.");
			}
			return new PhantomPartySupportPolicy(integer(root, "rebuffRemainingSeconds"), integer(root, "maximumMaintenanceDirectives"), integer(root, "maximumCombatMaintenanceDirectives"), integer(root, "idlePriority"), integer(root, "combatPriority"), integer(root, "neutralEmpathyMinimum"), integer(root, "neutralSociabilityMinimum"), Boolean.parseBoolean(rival));
		}
		catch (IllegalArgumentException exception)
		{
			throw exception;
		}
		catch (Exception exception)
		{
			throw new IllegalArgumentException("Could not load party support policy.", exception);
		}
	}

	private static int integer(Element root, String name)
	{
		final String value = root.getAttribute(name);
		if (!value.matches("[0-9]+"))
		{
			throw new IllegalArgumentException("Party support policy integer is invalid: " + name);
		}
		return Integer.parseInt(value);
	}
}

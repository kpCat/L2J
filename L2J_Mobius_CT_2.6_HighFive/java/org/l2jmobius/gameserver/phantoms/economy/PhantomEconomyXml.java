/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.economy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Bounded physical stock-file reads for the pricing adapters; never loads server or DB state. */
public final class PhantomEconomyXml
{
	private PhantomEconomyXml() { }
	public static Path physical(Path root, Path file) throws Exception
	{
		final Path absolute = file.toAbsolutePath().normalize();
		if (!absolute.startsWith(root.toAbsolutePath().normalize()) || !absolute.equals(absolute.toRealPath()) || !Files.isRegularFile(absolute) || Files.size(absolute) > 32_000_000) { throw new IllegalArgumentException("Bounded physical price source required: " + file); }
		return absolute;
	}
	public static Element read(Path file) throws Exception
	{
		final var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		return factory.newDocumentBuilder().parse(file.toFile()).getDocumentElement();
	}
	public static List<Element> children(Element parent, String name)
	{
		final List<Element> result = new ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) { if (node instanceof Element element && (name == null || name.equals(element.getTagName()))) { result.add(element); } }
		return result;
	}
	public static String hash(Path file) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))); }
}

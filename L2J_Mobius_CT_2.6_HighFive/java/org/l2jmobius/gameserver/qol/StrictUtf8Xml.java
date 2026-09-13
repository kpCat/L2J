/*
 * This file is part of the L2J Mobius project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.l2jmobius.gameserver.qol;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

final class StrictUtf8Xml
{
	private StrictUtf8Xml()
	{
	}

	static Element parse(Path path, int maximumBytes, String description)
	{
		try
		{
			final byte[] bytes = Files.readAllBytes(path);
			if ((bytes.length == 0) || (bytes.length > maximumBytes))
			{
				throw new IllegalArgumentException(description + " size is outside bounds.");
			}
			final String xml = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);
			final var builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new DefaultHandler()
			{
				@Override
				public void error(SAXParseException exception) throws SAXException
				{
					throw exception;
				}

				@Override
				public void fatalError(SAXParseException exception) throws SAXException
				{
					throw exception;
				}
			});
			return builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
		}
		catch (RuntimeException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw new IllegalArgumentException("Could not load strict " + description + ".", e);
		}
	}
}

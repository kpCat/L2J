package org.l2jmobius.gameserver.localplay;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Bounded, versioned mailbox protocol. Payloads never contain file paths. */
public final class LocalPlayPilotProtocol
{
	public enum Operation
	{
		STATUS,
		CAPABILITIES,
		SNAPSHOT_PHANTOMS,
		PREPARE_M1_ENVELOPE,
		SNAPSHOT_M1_ENVELOPE,
		SELECT_VISIBLE_PHANTOM_TRACE,
		SNAPSHOT_SELECTED_PHANTOM_TRACE,
		REPLAY_SELECTED_PHANTOM_TRACE,
		SNAPSHOT_TARGETS,
		TELEPORT_SELF,
		MOVE_SELF,
		STOP_MOVE,
		SIT,
		STAND,
		SELECT_TARGET,
		SAY,
		PARTY_INVITE,
		PARTY_RESPOND,
		PARTY_LEAVE,
		ATTACK_NPC,
		CAST_LEARNED_SKILL
	}

	public record Request(String requestId, String sessionId, String runId, long sequence, Instant deadline, Operation operation, Map<String, String> args)
	{
	}

	private LocalPlayPilotProtocol()
	{
	}

	public static Request read(Path file) throws Exception
	{
		if ((file == null) || !file.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.xml") || Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || (Files.size(file) > 65536))
		{
			throw new IllegalArgumentException("Invalid mailbox request file.");
		}
		final byte[] bytes = Files.readAllBytes(file);
		if ((bytes.length == 0) || (bytes.length > 65536))
		{
			throw new IllegalArgumentException("Invalid mailbox request size.");
		}
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		final Element root = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
		if (!"pilotRequest".equals(root.getTagName()) || !"1".equals(root.getAttribute("version")))
		{
			throw new IllegalArgumentException("Unsupported mailbox protocol.");
		}
		final String requestId = canonicalUuid(root.getAttribute("requestId"));
		if (!file.getFileName().toString().equalsIgnoreCase(requestId + ".xml"))
		{
			throw new IllegalArgumentException("Request ID and filename differ.");
		}
		final String sessionId = canonicalUuid(root.getAttribute("sessionId"));
		final String runId = canonicalUuid(root.getAttribute("runId"));
		final long sequence = Long.parseLong(root.getAttribute("sequence"));
		if (sequence <= 0)
		{
			throw new IllegalArgumentException("Invalid request sequence.");
		}
		final Instant deadline = Instant.parse(root.getAttribute("deadlineUtc"));
		final Operation operation = Operation.valueOf(root.getAttribute("operation"));
		final Map<String, String> args = new LinkedHashMap<>();
		final NodeList children = root.getChildNodes();
		for (int index = 0; index < children.getLength(); index++)
		{
			final Node node = children.item(index);
			if (node.getNodeType() != Node.ELEMENT_NODE)
			{
				continue;
			}
			if (!(node instanceof Element arg) || !"arg".equals(arg.getTagName()) || (args.size() >= 16))
			{
				throw new IllegalArgumentException("Invalid request argument.");
			}
			final String name = arg.getAttribute("name");
			final String value = arg.getAttribute("value");
			final boolean m1SelectorOption = (operation == Operation.PREPARE_M1_ENVELOPE) && "excludePreviouslySelectedProfileIds".equals(name);
			if ((!name.matches("[A-Za-z][A-Za-z0-9]{0,31}") && !m1SelectorOption) || (value.length() > 4096) || args.putIfAbsent(name, value) != null)
			{
				throw new IllegalArgumentException("Invalid or duplicate argument.");
			}
		}
		return new Request(requestId, sessionId, runId, sequence, deadline, operation, Map.copyOf(args));
	}

	public static String canonicalUuid(String value)
	{
		final String canonical = UUID.fromString(value).toString();
		if (!canonical.equalsIgnoreCase(value))
		{
			throw new IllegalArgumentException("Noncanonical UUID.");
		}
		return canonical;
	}

	public static void writeResult(Path path, Request request, String status, String reason, Instant started, Instant ended, int actorObjectId, Map<String, String> before, Map<String, String> after, Map<String, String> candidate) throws Exception
	{
		writeResult(path, request, status, reason, started, ended, actorObjectId, before, after, candidate, "CONNECTED_SERVER", "LOCALPLAY_PILOT");
	}

	static void writeResult(Path path, Request request, String status, String reason, Instant started, Instant ended, int actorObjectId, Map<String, String> before, Map<String, String> after, Map<String, String> candidate, String evidenceLevel, String driver) throws Exception
	{
		final Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(temporary), StandardCharsets.UTF_8))
		{
			final XMLStreamWriter xml = XMLOutputFactory.newFactory().createXMLStreamWriter(writer);
			xml.writeStartDocument("UTF-8", "1.0");
			xml.writeStartElement("pilotResult");
			attribute(xml, "version", "1");
			attribute(xml, "requestId", request.requestId());
			attribute(xml, "sessionId", request.sessionId());
			attribute(xml, "runId", request.runId());
			attribute(xml, "sequence", Long.toString(request.sequence()));
			attribute(xml, "operation", request.operation().name());
			attribute(xml, "status", status);
			attribute(xml, "reason", reason);
			attribute(xml, "startUtc", started.toString());
			attribute(xml, "endUtc", ended.toString());
			attribute(xml, "actorObjectId", Integer.toString(actorObjectId));
			attribute(xml, "evidenceLevel", evidenceLevel);
			attribute(xml, "driver", driver);
			attribute(xml, "harnessResult", status);
			attribute(xml, "gameplayResult", "NOT_OBSERVED");
			writeMap(xml, "before", before);
			writeMap(xml, "after", after);
			if (candidate != null)
			{
				writeMap(xml, "candidate", candidate);
			}
			xml.writeEndElement();
			xml.writeEndDocument();
			xml.close();
		}
		catch (Exception exception)
		{
			Files.deleteIfExists(temporary);
			throw exception;
		}
		try
		{
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException exception)
		{
			Files.deleteIfExists(temporary);
			throw exception;
		}
	}

	private static void writeMap(XMLStreamWriter xml, String tag, Map<String, String> values) throws Exception
	{
		xml.writeEmptyElement(tag);
		for (Map.Entry<String, String> entry : values.entrySet())
		{
			attribute(xml, entry.getKey(), entry.getValue());
		}
	}

	private static void attribute(XMLStreamWriter xml, String name, String value) throws Exception
	{
		xml.writeAttribute(name, value == null ? "" : value);
	}
}

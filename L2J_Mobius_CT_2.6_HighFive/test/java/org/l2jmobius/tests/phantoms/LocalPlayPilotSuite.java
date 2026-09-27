package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.localplay.LocalPlayPilotLease;
import org.l2jmobius.gameserver.localplay.LocalPlayPilotLease.State;
import org.l2jmobius.gameserver.localplay.LocalPlayPilotProtocol;

/** Deterministic security contract for the single-client pilot. */
public final class LocalPlayPilotSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "localplay-pilot";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("exact-client-and-process-arm", this::exactArm);
		registry.add("sequence-stop-and-expiry", this::sequenceStopExpiry);
		registry.add("off-invalidates-consent", this::offInvalidates);
		registry.add("mailbox-xml-contract", this::mailboxXmlContract);
		registry.add("mailbox-negative-controls", this::mailboxNegativeControls);
	}

	private void exactArm(PhantomTestContext context)
	{
		final AtomicLong clock = new AtomicLong(100);
		final Object client = new Object();
		final LocalPlayPilotLease lease = new LocalPlayPilotLease("nonce", "CodexQA", 17, 200, 200, clock::get);
		assertFalse(lease.arm("old", "CodexQA", "account", 5, client, 17, 200, 100), "old nonce");
		assertFalse(lease.arm("nonce", "Other", "account", 5, client, 17, 200, 100), "other character");
		assertFalse(lease.arm("nonce", "CodexQA", "account", 5, client, 17, 201, 100), "other incarnation");
		assertTrue(lease.arm("nonce", "CodexQA", "account", 5, client, 17, 200, 100), "exact arm");
		assertFalse(lease.arm("nonce", "CodexQA", "account", 5, client, 17, 200, 100), "nonce consumed");
		assertTrue(lease.valid("account", 5, client, 17, 200), "same client");
		assertFalse(lease.valid("account", 5, new Object(), 17, 200), "different GameClient");
		assertFalse(lease.valid("account", 6, client, 17, 200), "different character");
	}

	private void sequenceStopExpiry(PhantomTestContext context)
	{
		final AtomicLong clock = new AtomicLong(100);
		final Object client = new Object();
		final LocalPlayPilotLease lease = new LocalPlayPilotLease("nonce", "CodexQA", 17, 200, 200, clock::get);
		assertTrue(lease.arm("nonce", "CodexQA", "account", 5, client, 17, 200, 100), "arm");
		assertTrue(lease.claim(lease.sessionId(), 1, "a"), "first sequence");
		assertFalse(lease.claim(lease.sessionId(), 1, "b"), "replay sequence");
		assertFalse(lease.claim(lease.sessionId(), 3, "c"), "out of order");
		lease.stop();
		assertTrue(lease.state() == State.ARMED_IDLE, "stop retains consent");
		clock.set(201);
		assertFalse(lease.valid("account", 5, client, 17, 200), "expired consent");
		assertTrue(lease.state() == State.OFF, "expired state");
	}

	private void offInvalidates(PhantomTestContext context)
	{
		final AtomicLong clock = new AtomicLong(100);
		final Object client = new Object();
		final LocalPlayPilotLease lease = new LocalPlayPilotLease("nonce", "CodexQA", 17, 200, 200, clock::get);
		assertTrue(lease.arm("nonce", "CodexQA", "account", 5, client, 17, 200, 100), "arm");
		lease.off();
		assertFalse(lease.valid("account", 5, client, 17, 200), "off");
		assertFalse(lease.claim(lease.sessionId(), 1, "a"), "off rejects queued work");
	}

	private void mailboxXmlContract(PhantomTestContext context) throws Exception
	{
		final Path directory = Files.createTempDirectory("pilot-protocol-");
		try
		{
			final String requestId = UUID.randomUUID().toString();
			final String sessionId = UUID.randomUUID().toString();
			final String runId = UUID.randomUUID().toString();
			final Path request = directory.resolve(requestId + ".xml");
			Files.writeString(request, xml(requestId, sessionId, runId, "STATUS", "<arg name=\"text\" value=\"Привет\"/>"));
			final LocalPlayPilotProtocol.Request parsed = LocalPlayPilotProtocol.read(request);
			assertTrue(parsed.requestId().equals(requestId) && parsed.sessionId().equals(sessionId) && parsed.runId().equals(runId) && (parsed.sequence() == 1) && "Привет".equals(parsed.args().get("text")), "typed request parse");
			final Path result = directory.resolve("result.xml");
			LocalPlayPilotProtocol.writeResult(result, parsed, "SUCCEEDED", "SNAPSHOT", Instant.now(), Instant.now(), 19, Map.of("x", "1"), Map.of("x", "2"), null);
			final String serialized = Files.readString(result);
			assertTrue(serialized.contains("status=\"SUCCEEDED\"") && serialized.contains("<before x=\"1\"/>") && serialized.contains("<after x=\"2\"/>"), "result serialization");
		}
		finally
		{
			try (var files = Files.list(directory))
			{
				for (Path file : files.toList())
				{
					Files.delete(file);
				}
			}
			Files.delete(directory);
		}
	}

	private void mailboxNegativeControls(PhantomTestContext context) throws Exception
	{
		final Path directory = Files.createTempDirectory("pilot-negative-");
		try
		{
			final String requestId = UUID.randomUUID().toString();
			final String sessionId = UUID.randomUUID().toString();
			final String runId = UUID.randomUUID().toString();
			final Path request = directory.resolve(requestId + ".xml");
			for (String body : new String[]
			{
				xml(requestId, sessionId, runId, "ARBITRARY_COMMAND", ""),
				xml(requestId, sessionId, runId, "STATUS", "<arg name=\"x\" value=\"1\"/><arg name=\"x\" value=\"2\"/>"),
				"<!DOCTYPE pilotRequest [<!ENTITY xxe SYSTEM \"file:///C:/Windows/win.ini\">]>" + xml(requestId, sessionId, runId, "STATUS", ""),
				xml(UUID.randomUUID().toString(), sessionId, runId, "STATUS", "")
			})
			{
				Files.writeString(request, body);
				boolean rejected = false;
				try
				{
					LocalPlayPilotProtocol.read(request);
				}
				catch (Exception exception)
				{
					rejected = true;
				}
				assertTrue(rejected, "invalid request rejected");
			}
			Files.writeString(request, "x".repeat(65537));
			boolean oversizedRejected = false;
			try
			{
				LocalPlayPilotProtocol.read(request);
			}
			catch (Exception exception)
			{
				oversizedRejected = true;
			}
			assertTrue(oversizedRejected, "oversized request rejected");
		}
		finally
		{
			try (var files = Files.list(directory))
			{
				for (Path file : files.toList())
				{
					Files.delete(file);
				}
			}
			Files.delete(directory);
		}
	}

	private static String xml(String requestId, String sessionId, String runId, String operation, String children)
	{
		return "<pilotRequest version=\"1\" requestId=\"" + requestId + "\" sessionId=\"" + sessionId + "\" runId=\"" + runId + "\" sequence=\"1\" deadlineUtc=\"2030-01-01T00:00:00Z\" operation=\"" + operation + "\">" + children + "</pilotRequest>";
	}

	private static void assertTrue(boolean value, String label)
	{
		if (!value)
		{
			throw new AssertionError(label);
		}
	}

	private static void assertFalse(boolean value, String label)
	{
		assertTrue(!value, label);
	}
}

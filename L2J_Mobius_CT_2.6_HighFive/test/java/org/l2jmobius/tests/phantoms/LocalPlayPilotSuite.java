package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.localplay.LocalPlayPilotArmCode;
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
		registry.add("short-human-arm-code", this::shortHumanArmCode);
		registry.add("sequence-stop-and-expiry", this::sequenceStopExpiry);
		registry.add("off-invalidates-consent", this::offInvalidates);
		registry.add("mailbox-xml-contract", this::mailboxXmlContract);
		registry.add("mailbox-negative-controls", this::mailboxNegativeControls);
		registry.add("census-cleanup-xml-page-bound", this::censusCleanupPageBound);
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

	private void shortHumanArmCode(PhantomTestContext context)
	{
		final String hash = "a00d76646eba91b057841554d5c8334f498dc592ed744bce404f21fe271cd36e";
		assertTrue(LocalPlayPilotArmCode.isValid("ABCD2345"), "eight unambiguous characters");
		assertTrue(LocalPlayPilotArmCode.matches("abcd2345", hash), "case-insensitive typed code");
		assertFalse(LocalPlayPilotArmCode.isValid("ABCD234"), "short code rejected");
		assertFalse(LocalPlayPilotArmCode.isValid("ABCD23456"), "long code rejected");
		assertFalse(LocalPlayPilotArmCode.isValid("ABCI2345"), "ambiguous character rejected");
		assertFalse(LocalPlayPilotArmCode.matches("ABCD2346", hash), "wrong one-time code rejected");
		assertFalse(LocalPlayPilotArmCode.isValid("0123456789abcdef0123456789abcdef"), "internal nonce is not human-entered");
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
			Files.writeString(request, xml(requestId, sessionId, runId, "PREPARE_M1_ENVELOPE", "<arg name=\"excludePreviouslySelectedProfileIds\" value=\"17\"/>"));
			assertTrue("17".equals(LocalPlayPilotProtocol.read(request).args().get("excludePreviouslySelectedProfileIds")), "exact M1 selector option accepted");
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

	private void censusCleanupPageBound(PhantomTestContext context) throws Exception
	{
		java.lang.reflect.Method budget;
		try { budget = org.l2jmobius.gameserver.phantoms.PhantomSystem.class.getDeclaredMethod("censusPageBudgetExceeded", Map.class); budget.setAccessible(true); }
		catch (NoSuchMethodException baselineWithoutBudget) { budget = null; }
		final Path directory = Files.createTempDirectory("pilot-census-bound-");
		final var fields = new java.util.LinkedHashMap<String, String>();
		int included = 0;
		try
		{
			for (int row = 1; row <= 24; row++)
			{
				final String prefix = "census" + row + ".";
				for (String field : new String[] {"profileId", "objectId", "materializedAtNanos", "materializationState", "actionAdmissionOpen", "pendingOwnedStore", "goalId", "goalRevision", "runtimeGoalRevision", "currentActionGuard", "hp", "maxHp", "nativeAttackBy", "targetRejections", "pvpHumanContext", "level", "npcId", "anchor", "goalStatus", "runtimeReason", "travelReason", "travelFailureReason", "travelFailureSequence", "dead", "moving", "attacking", "casting", "autoPlay", "party", "store", "intention", "shortTargets", "longTargets", "x", "y", "z", "targetObjectId", "targetMonsterAlive", "eligible", "idleReason", "admittedActionCount", "cleanupPhase", "cleanupFailurePhase", "cleanupFailureSequence", "cleanupFailureAdmittedActionCount", "playerRetained", "identityLeaseRetained", "outboundAttached", "worldPresent"})
				{
					fields.put(prefix + field, "9223372036854775807");
				}
				fields.put(prefix + "cleanupFailureClass", IllegalStateException.class.getName());
				fields.put(prefix + "cleanupFailureMessage", "\"".repeat(160));
				if ((budget != null) && (boolean) budget.invoke(null, fields))
				{
					fields.keySet().removeIf(name -> name.startsWith(prefix));
					break;
				}
				included = row;
			}
			fields.put("censusCount", Integer.toString(included));
			fields.put("censusNextProfileId", Integer.toString(included));
			fields.put("selectedMetadata", "界".repeat(4096));
			final Path result = directory.resolve("result.xml");
			final var request = new LocalPlayPilotProtocol.Request(UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1, Instant.now().plusSeconds(30), LocalPlayPilotProtocol.Operation.SNAPSHOT_M1_ENVELOPE, Map.of());
			LocalPlayPilotProtocol.writeResult(result, request, "SUCCEEDED", "SNAPSHOT", Instant.now(), Instant.now(), 19, Map.of("x", "-2147483648"), Map.of("x", "2147483647"), fields);
			assertTrue(Files.size(result) < 65536, "census plus envelope exceeded existing64KiB reader contract");
			assertTrue((included > 0) && (included < 24), "escaping must bound a census page before24 rows");
			assertTrue(Files.readString(result).contains("&quot;"), "escaping worst case was not serialized");
			context.record("census.boundedRows", included);
			context.record("census.serializedBytes", Files.size(result));
		}
		finally
		{
			try (var files = Files.list(directory)) { for (Path file : files.toList()) { Files.delete(file); } }
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
				xml(requestId, sessionId, runId, "STATUS", "<arg name=\"excludePreviouslySelectedProfileIds\" value=\"17\"/>"),
				xml(requestId, sessionId, runId, "SNAPSHOT_M1_ENVELOPE", "<arg name=\"excludePreviouslySelectedProfileIds\" value=\"17\"/>"),
				xml(requestId, sessionId, runId, "PREPARE_M1_ENVELOPE", "<arg name=\"unknownLongArgumentNameNotPermitted\" value=\"17\"/>"),
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

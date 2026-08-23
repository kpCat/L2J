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
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.file.Files;

import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorControlCode;
import org.l2jmobius.gameserver.phantoms.PhantomSystem.OperatorMode;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomOperatorRuntimeControlsSuite implements PhantomTestSuite
{
	@Override
	public String id()
	{
		return "operator-runtime-controls";
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-config-disabled-enable-guard", _ -> testConfigGuard());
		registry.add("02-idempotent-enable-retains-runtime", _ -> testIdempotentEnable());
		registry.add("03-drain-success-and-start-gate", _ -> testDrainSuccess());
		registry.add("04-disable-and-explicit-enable-gate", _ -> testDisableGate());
		registry.add("05-failed-shutdown-retains-owner-and-intent", _ -> testFailedShutdownRetention());
		registry.add("06-admin-and-canonical-source-contract", this::testSourceContract);
	}

	private static void testConfigGuard()
	{
		reset();
		try
		{
			PhantomAssertions.assertFalse(PhantomSystem.operatorStatus().configuredEnabled(), "Focused config guard requires the shipped disabled baseline.");
			final var result = PhantomSystem.operatorEnable();
			PhantomAssertions.assertEquals(OperatorControlCode.CONFIG_DISABLED, result.code(), "Operator enable bypassed EnablePhantomSystem=false.");
			PhantomAssertions.assertEquals(OperatorMode.ENABLED, result.desiredMode(), "Explicit enable did not release the process-local off gate.");
			PhantomAssertions.assertFalse(result.desiredRuntimeEnabled(), "Config-disabled enable reported desired runtime as permitted.");
			PhantomAssertions.assertFalse(result.runtimeConfigured(), "Config-disabled enable created a runtime.");
			PhantomAssertions.assertFalse(PhantomSystem.startConfigured(), "Canonical startup bypassed the disabled config.");
		}
		finally
		{
			reset();
		}
	}

	private static void testIdempotentEnable()
	{
		reset();
		PhantomSystem.configureOperatorRuntimeForTesting(false);
		try
		{
			final PhantomScheduler scheduler = PhantomSystem.configuredScheduler();
			PhantomAssertions.assertEquals(OperatorControlCode.ALREADY_RUNNING, PhantomSystem.operatorEnable().code(), "Enable did not report the running runtime idempotently.");
			PhantomAssertions.assertEquals(OperatorControlCode.ALREADY_RUNNING, PhantomSystem.operatorEnable().code(), "Repeated enable was not idempotent.");
			PhantomAssertions.assertTrue(scheduler == PhantomSystem.configuredScheduler(), "Repeated enable replaced the configured runtime.");
			PhantomAssertions.assertEquals(OperatorMode.ENABLED, PhantomSystem.operatorStatus().operatorMode(), "Running enable did not publish ENABLED intent.");
		}
		finally
		{
			reset();
		}
	}

	private static void testDrainSuccess()
	{
		reset();
		PhantomSystem.configureOperatorRuntimeForTesting(false);
		try
		{
			final var drained = PhantomSystem.operatorDrain();
			PhantomAssertions.assertEquals(OperatorControlCode.DRAINED, drained.code(), "Drain did not stop the configured runtime.");
			PhantomAssertions.assertEquals(OperatorMode.DRAINED, drained.desiredMode(), "Drain did not retain DRAINED intent.");
			PhantomAssertions.assertFalse(drained.desiredRuntimeEnabled(), "DRAINED intent still desired a runtime.");
			PhantomAssertions.assertFalse(drained.runtimeConfigured(), "Successful drain retained the configured owner.");
			PhantomAssertions.assertFalse(PhantomSystem.startConfigured(), "DRAINED intent did not block canonical startup.");
			PhantomAssertions.assertEquals(OperatorControlCode.ALREADY_DRAINED, PhantomSystem.operatorDrain().code(), "Repeated drain was not idempotent.");
		}
		finally
		{
			reset();
		}
	}

	private static void testDisableGate()
	{
		reset();
		PhantomSystem.configureOperatorRuntimeForTesting(false);
		try
		{
			final var disabled = PhantomSystem.operatorDisable();
			PhantomAssertions.assertEquals(OperatorControlCode.DISABLED, disabled.code(), "Disable did not stop the configured runtime.");
			PhantomAssertions.assertEquals(OperatorMode.DISABLED, disabled.desiredMode(), "Disable did not retain DISABLED intent.");
			PhantomAssertions.assertFalse(PhantomSystem.startConfigured(), "DISABLED intent did not block canonical startup.");
			PhantomAssertions.assertEquals(OperatorControlCode.ALREADY_DISABLED, PhantomSystem.operatorDisable().code(), "Repeated disable was not idempotent.");
			final var enable = PhantomSystem.operatorEnable();
			PhantomAssertions.assertEquals(OperatorControlCode.CONFIG_DISABLED, enable.code(), "Explicit enable bypassed the shipped config guard.");
			PhantomAssertions.assertEquals(OperatorMode.ENABLED, enable.desiredMode(), "Explicit enable did not release DISABLED intent.");
			PhantomAssertions.assertFalse(enable.runtimeConfigured(), "Explicit enable created a config-disabled runtime.");
		}
		finally
		{
			reset();
		}
	}

	private static void testFailedShutdownRetention()
	{
		reset();
		PhantomSystem.configureOperatorRuntimeForTesting(true);
		final PhantomScheduler retainedScheduler = PhantomSystem.configuredScheduler();
		try
		{
			final var failed = PhantomSystem.operatorDrain();
			PhantomAssertions.assertEquals(OperatorControlCode.SHUTDOWN_FAILED, failed.code(), "Failed canonical shutdown was reported as drained.");
			PhantomAssertions.assertEquals(OperatorMode.DRAINED, failed.desiredMode(), "Failed drain lost requested off-intent.");
			PhantomAssertions.assertTrue(failed.runtimeConfigured(), "Failed drain cleared the configured owner.");
			PhantomAssertions.assertEquals(PhantomSystem.State.FAILED, failed.runtimeState(), "Failed drain hid actual FAILED state.");
			PhantomAssertions.assertFalse(failed.desiredRuntimeEnabled(), "Failed drain reported desired running state.");
			PhantomAssertions.assertTrue(retainedScheduler == PhantomSystem.configuredScheduler(), "Failed drain replaced the configured owner.");
			final var busy = PhantomSystem.operatorEnable();
			PhantomAssertions.assertEquals(OperatorControlCode.OWNER_BUSY, busy.code(), "Enable created or attempted a duplicate over failed owner.");
			PhantomAssertions.assertEquals(OperatorMode.DRAINED, busy.desiredMode(), "Busy enable erased retained off-intent.");
			PhantomAssertions.assertTrue(retainedScheduler == PhantomSystem.configuredScheduler(), "Busy enable replaced the failed owner.");
			PhantomSystem.releaseOperatorShutdownFailureForTesting();
			final var stopped = PhantomSystem.operatorDisable();
			PhantomAssertions.assertEquals(OperatorControlCode.DISABLED, stopped.code(), "Canonical retry did not stop the retained failed owner.");
			PhantomAssertions.assertFalse(stopped.runtimeConfigured(), "Successful retry retained the configured owner.");
		}
		finally
		{
			PhantomSystem.releaseOperatorShutdownFailureForTesting();
			reset();
		}
	}

	private void testSourceContract(PhantomTestContext context) throws Exception
	{
		final String system = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"));
		final String handler = Files.readString(context.moduleRoot().resolve("dist/game/data/scripts/handlers/chat/commands/admin/AdminPhantom.java"));
		final String access = Files.readString(context.moduleRoot().resolve("dist/game/config/AdminCommands.xml"));
		final String gameServer = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/GameServer.java"));
		PhantomAssertions.assertEquals(1, occurrences(system, "new PhantomSystem(PhantomPlayersConfig.settings(), true)"), "Configured construction sequence was duplicated.");
		PhantomAssertions.assertEquals(3, occurrences(system, "startConfiguredInternal()"), "Startup and operator enable do not share exactly one canonical helper.");
		final int shutdownHelper = system.indexOf("private static boolean shutdownConfiguredInstance()");
		final int shutdownCall = system.indexOf("configured.shutdown()", shutdownHelper);
		final int stoppedCheck = system.indexOf("configured.snapshot().state() == State.STOPPED", shutdownHelper);
		final int clearOwner = system.indexOf("_configuredInstance = null", stoppedCheck);
		PhantomAssertions.assertTrue((shutdownHelper >= 0) && (shutdownCall > shutdownHelper) && (stoppedCheck > shutdownCall) && (clearOwner > stoppedCheck), "Configured controls do not reuse canonical shutdown or clear only after actual STOPPED.");
		PhantomAssertions.assertTrue(handler.contains("arguments.equals(\"enable\")") && handler.contains("arguments.equals(\"drain\")") && handler.contains("arguments.equals(\"disable\")"), "AdminPhantom did not expose all three runtime controls.");
		PhantomAssertions.assertTrue(handler.contains("//phantom status | //phantom trace <profileId> | //phantom trace clear"), "Existing status/trace usage contract drifted.");
		PhantomAssertions.assertEquals(1, occurrences(access, "command=\"phantom\""), "Admin family/access entry changed cardinality.");
		PhantomAssertions.assertTrue(gameServer.contains("PhantomSystem.startConfigured()"), "GameServer no longer uses canonical configured startup.");
	}

	private static void reset()
	{
		PhantomSystem.releaseOperatorShutdownFailureForTesting();
		if (PhantomSystem.hasConfiguredInstance())
		{
			PhantomSystem.operatorDisable();
		}
		if (PhantomSystem.hasConfiguredInstance())
		{
			throw new AssertionError("Focused operator test retained a configured owner.");
		}
		PhantomSystem.resetOperatorModeForTesting();
	}

	private static int occurrences(String text, String value)
	{
		int count = 0;
		int offset = 0;
		while ((offset = text.indexOf(value, offset)) >= 0)
		{
			count++;
			offset += value.length();
		}
		return count;
	}
}

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
package org.l2jmobius.tests.phantoms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.tests.phantoms.PhantomProvisioningLock.ProvisioningLockException;

public final class PhantomProvisioningLockControl
{
	private static final Duration READY_TIMEOUT = Duration.ofSeconds(10);
	private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(15);

	private PhantomProvisioningLockControl()
	{
	}

	public static void main(String[] args)
	{
		final int exitCode;
		try
		{
			exitCode = run(args);
		}
		catch (Throwable throwable)
		{
			System.err.println("Provisioning lock control failed: " + PhantomTestLauncher.sanitize(throwable.getMessage()));
			System.exit(PhantomTestLauncher.EXIT_INTERNAL_ERROR);
			return;
		}
		System.exit(exitCode);
	}

	private static int run(String[] args) throws Exception
	{
		if (args.length == 0)
		{
			runController();
			return PhantomTestLauncher.EXIT_SUCCESS;
		}
		return switch (args[0])
		{
			case "holder" -> runHolder(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
			case "crash-holder" -> runCrashHolder(Path.of(args[1]), Path.of(args[2]));
			case "contender" -> runContender(Path.of(args[1]), Path.of(args[2]));
			default -> PhantomTestLauncher.EXIT_CONFIGURATION_REJECTED;
		};
	}

	private static void runController() throws Exception
	{
		final Path directory = Files.createTempDirectory("phantom-provisioning-lock-control-");
		final Path lockFile = directory.resolve("test-db.lock");
		final List<Process> children = new ArrayList<>();
		try
		{
			final Path ready = directory.resolve("holder.ready");
			final Path release = directory.resolve("holder.release");
			final Path blockedMarker = directory.resolve("blocked.jdbc");
			final Process holder = startChild(children, "holder", lockFile, ready, release);
			waitForReady(holder, ready);
			final String ownerToken = Files.readString(ready, StandardCharsets.UTF_8);

			final Process blocked = startChild(children, "contender", lockFile, blockedMarker);
			PhantomAssertions.assertEquals(PhantomTestLauncher.EXIT_CONFIGURATION_REJECTED, waitForExit(blocked), "Busy contender exit code mismatch.");
			PhantomAssertions.assertTrue(Files.isRegularFile(lockFile), "Busy contender removed the active lock file.");
			PhantomAssertions.assertFalse(Files.exists(blockedMarker), "Busy contender entered the JDBC/destructive path.");

			Files.writeString(release, "release", StandardCharsets.UTF_8);
			PhantomAssertions.assertEquals(PhantomTestLauncher.EXIT_SUCCESS, waitForExit(holder), "Lock holder did not exit cleanly.");
			PhantomAssertions.assertEquals(ownerToken, Files.readString(lockFile, StandardCharsets.UTF_8), "Busy contender changed the owner token.");
			final Path acquiredMarker = directory.resolve("acquired.jdbc");
			final Process acquired = startChild(children, "contender", lockFile, acquiredMarker);
			PhantomAssertions.assertEquals(PhantomTestLauncher.EXIT_SUCCESS, waitForExit(acquired), "Contender could not acquire the released lock.");
			PhantomAssertions.assertTrue(Files.isRegularFile(acquiredMarker), "Successful contender did not enter the protected path.");

			Files.deleteIfExists(ready);
			final Process crashHolder = startChild(children, "crash-holder", lockFile, ready);
			waitForReady(crashHolder, ready);
			crashHolder.destroyForcibly();
			PhantomAssertions.assertTrue(crashHolder.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), "Crashed holder did not terminate.");
			final Path crashRecoveryMarker = directory.resolve("crash-recovery.jdbc");
			final Process crashRecovery = startChild(children, "contender", lockFile, crashRecoveryMarker);
			PhantomAssertions.assertEquals(PhantomTestLauncher.EXIT_SUCCESS, waitForExit(crashRecovery), "OS lock was not released after holder death.");
			PhantomAssertions.assertTrue(Files.isRegularFile(crashRecoveryMarker), "Crash recovery contender did not enter the protected path.");
			System.out.println("Provisioning lock control PASS: foreign token preserved, busy exit=2, JDBC marker absent, normal/crash release reusable.");
		}
		finally
		{
			for (Process child : children)
			{
				if (child.isAlive())
				{
					child.destroyForcibly();
					child.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
				}
			}
			deleteTree(directory);
			deleteTree(directory);
		}
	}

	private static int runHolder(Path lockFile, Path ready, Path release) throws Exception
	{
		try (PhantomProvisioningLock lock = PhantomProvisioningLock.acquire(lockFile))
		{
			Files.writeString(ready, lock.ownerToken() + System.lineSeparator(), StandardCharsets.UTF_8);
			final long deadline = System.nanoTime() + PROCESS_TIMEOUT.toNanos();
			while (!Files.exists(release))
			{
				if (System.nanoTime() >= deadline)
				{
					return PhantomTestLauncher.EXIT_INTERNAL_ERROR;
				}
				Thread.sleep(25);
			}
			return PhantomTestLauncher.EXIT_SUCCESS;
		}
	}

	private static int runCrashHolder(Path lockFile, Path ready) throws Exception
	{
		try (PhantomProvisioningLock lock = PhantomProvisioningLock.acquire(lockFile))
		{
			Files.writeString(ready, lock.ownerToken() + System.lineSeparator(), StandardCharsets.UTF_8);
			Thread.sleep(PROCESS_TIMEOUT.toMillis() * 2);
			return PhantomTestLauncher.EXIT_INTERNAL_ERROR;
		}
	}

	private static int runContender(Path lockFile, Path protectedPathMarker) throws Exception
	{
		try (PhantomProvisioningLock ignored = PhantomProvisioningLock.acquire(lockFile))
		{
			Files.writeString(protectedPathMarker, "protected-path-entered", StandardCharsets.UTF_8);
			return PhantomTestLauncher.EXIT_SUCCESS;
		}
		catch (ProvisioningLockException expected)
		{
			return PhantomTestLauncher.EXIT_CONFIGURATION_REJECTED;
		}
	}

	private static Process startChild(List<Process> children, String mode, Path... paths) throws IOException
	{
		final Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
		final List<String> command = new ArrayList<>();
		command.add(java.toString());
		command.add("-cp");
		command.add(System.getProperty("java.class.path"));
		command.add(PhantomProvisioningLockControl.class.getName());
		command.add(mode);
		for (Path path : paths)
		{
			command.add(path.toString());
		}
		final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		children.add(process);
		return process;
	}

	private static void waitForReady(Process process, Path ready) throws Exception
	{
		final long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
		while (!Files.exists(ready))
		{
			if (!process.isAlive())
			{
				throw new AssertionError("Lock holder exited before signaling ready.");
			}
			if (System.nanoTime() >= deadline)
			{
				process.destroyForcibly();
				throw new AssertionError("Lock holder ready timeout expired.");
			}
			Thread.sleep(25);
		}
	}

	private static int waitForExit(Process process) throws Exception
	{
		if (!process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
		{
			process.destroyForcibly();
			process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			throw new AssertionError("Child process timeout expired.");
		}
		return process.exitValue();
	}

	private static void deleteTree(Path path) throws IOException
	{
		if (!Files.exists(path))
		{
			return;
		}
		try (var stream = Files.walk(path))
		{
			for (Path entry : stream.sorted((left, right) -> right.compareTo(left)).toList())
			{
				Files.deleteIfExists(entry);
			}
		}
	}
}

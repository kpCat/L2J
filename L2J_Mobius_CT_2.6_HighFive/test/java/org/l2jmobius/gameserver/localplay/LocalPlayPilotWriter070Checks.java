/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.localplay;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.nio.file.ExtendedOpenOption;

/** Real Windows mailbox sharing regression; no reflection, DB, server or actor. */
public final class LocalPlayPilotWriter070Checks
{
	public static void main(String[] args) throws Exception
	{
		if (args.length != 1 || !System.getProperty("os.name").startsWith("Windows")) { throw new IllegalArgumentException("Explicit new Windows fixture directory required."); }
		final Path root = Path.of(args[0]).toAbsolutePath().normalize();
		if (Files.exists(root)) { throw new IllegalArgumentException("Preserve existing fixture output."); }
		Files.createDirectory(root);
		System.out.println("WRITER_CODE_SOURCE=" + LocalPlayPilotService.class.getProtectionDomain().getCodeSource().getLocation());
		transientReader(root);
		persistentReader(root);
		System.out.println("LOCALPLAY_WRITER070_PASS; transient reader released; persistent reader fails closed; DB_WRITES=0; PROCESS_ACTIONS=0");
	}

	private static void transientReader(Path root) throws Exception
	{
		final Path target = root.resolve("transient.properties");
		final Path temporary = root.resolve("transient.properties.tmp");
		Files.writeString(target, "old-transient", StandardCharsets.UTF_8);
		final var releaseFailure = new AtomicReference<Throwable>();
		final SeekableByteChannel held = Files.newByteChannel(target, Set.of(StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE));
		final Thread release = new Thread(() ->
		{
			try
			{
				final long deadline = System.nanoTime() + 5_000_000_000L;
				while (!Files.exists(temporary) && System.nanoTime() < deadline) { Thread.sleep(1); }
				if (!Files.exists(temporary)) { throw new AssertionError("Native writer did not create its temporary file."); }
				Thread.sleep(20);
				held.close();
			}
			catch (Throwable failure) { releaseFailure.set(failure); }
		}, "localplay-writer070-reader-release");
		release.start();
		IOException publicationFailure = null;
		try { LocalPlayPilotService.writeProperties(target, values("transient-replaced")); }
		catch (IOException failure) { publicationFailure = failure; }
		finally
		{
			release.join(6000);
			held.close();
		}
		if (release.isAlive() || releaseFailure.get() != null) { throw new AssertionError("Real reader release failed.", releaseFailure.get()); }
		if (publicationFailure != null) { throw new AssertionError("TRANSIENT_LOCK: native atomic writer failed after a short reader hold.", publicationFailure); }
		if (!"transient-replaced".equals(read(target).getProperty("state")) || Files.exists(temporary)) { throw new AssertionError("Transient publication did not replace atomically."); }
		System.out.println("TRANSIENT_LOCK_RELEASE_PASS");
	}

	private static void persistentReader(Path root) throws Exception
	{
		final Path target = root.resolve("persistent.properties");
		final byte[] original = "old-persistent".getBytes(StandardCharsets.UTF_8);
		Files.write(target, original);
		IOException publicationFailure = null;
		final long started = System.nanoTime();
		try (var held = Files.newByteChannel(target, Set.of(StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)))
		{
			try { LocalPlayPilotService.writeProperties(target, values("must-not-publish")); }
			catch (IOException failure) { publicationFailure = failure; }
			if (publicationFailure == null || !Arrays.equals(original, Files.readAllBytes(target))) { throw new AssertionError("Persistent reader must fail without replacing original bytes."); }
		}
		if (System.nanoTime() - started > 2_000_000_000L) { throw new AssertionError("Native publication retry exceeded its bounded window."); }
		System.out.println("PERSISTENT_LOCK_FAIL_CLOSED_PASS " + publicationFailure.getClass().getSimpleName());
	}

	private static Properties values(String state)
	{
		final var values = new Properties();
		values.setProperty("state", state);
		return values;
	}

	private static Properties read(Path file) throws IOException
	{
		final var values = new Properties();
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
		return values;
	}
}

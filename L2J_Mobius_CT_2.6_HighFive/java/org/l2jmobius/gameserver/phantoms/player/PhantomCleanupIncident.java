/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.player;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;

/** Detached, bounded facts only. Never keeps a native Player or Throwable alive. */
public record PhantomCleanupIncident(int objectId, long epoch, long sequence, String utc,
	PhantomMaterializedPlayer.CleanupPhase phase, String hook, int admittedActions,
	String exceptionClass, String message, String detail, int nodes, int frames, boolean truncated)
{
	private static final int MAX_DETAIL_BYTES = 6144;

	public String id(long profileId)
	{
		return profileId + ":" + objectId + ":" + epoch;
	}

	public static PhantomCleanupIncident capture(int objectId, long epoch, long sequence,
		PhantomMaterializedPlayer.CleanupPhase phase, String hook, int admittedActions, Throwable failure)
	{
		final var output = new BoundedDetail();
		int nodes = 0;
		int frames = 0;
		String message = "";
		try
		{
			message = bounded(failure.getMessage(), 160);
			final var pending = new ArrayDeque<Node>();
			final var seen = new IdentityHashMap<Throwable, Boolean>();
			pending.add(new Node("primary", failure));
			while (!pending.isEmpty() && (nodes < 8))
			{
				final Node node = pending.removeFirst();
				if (seen.put(node.failure(), Boolean.TRUE) != null) { output.truncated = true; continue; }
				nodes++;
				output.append(node.kind() + " " + bounded(node.failure().getClass().getName(), 160) + ": " + bounded(node.failure().getMessage(), 160) + "\n");
				final StackTraceElement[] stack = node.failure().getStackTrace();
				for (StackTraceElement frame : stack)
				{
					if (frames >= 32) { output.truncated = true; break; }
					frames++;
					output.append(" at " + bounded(frame.toString(), 240) + "\n");
				}
				final Throwable cause = node.failure().getCause();
				if (cause != null)
				{
					if (nodes + pending.size() < 8) { pending.addLast(new Node("cause", cause)); }
					else { output.truncated = true; }
				}
				for (Throwable suppressed : node.failure().getSuppressed())
				{
					if (nodes + pending.size() >= 8) { output.truncated = true; break; }
					pending.addLast(new Node("suppressed", suppressed));
				}
			}
			if (!pending.isEmpty()) { output.truncated = true; }
		}
		catch (RuntimeException | Error formattingFailure)
		{
			output.truncated = true;
			output.append("DIAGNOSTIC_FORMAT_UNAVAILABLE " + formattingFailure.getClass().getName());
		}
		return new PhantomCleanupIncident(objectId, epoch, sequence, Instant.now().toString(), phase,
			bounded(hook, 80), admittedActions, bounded(failure.getClass().getName(), 160), message,
			output.text.toString(), nodes, frames, output.truncated);
	}

	public static String bounded(String value, int maximumCharacters)
	{
		if (value == null) { return ""; }
		final var result = new StringBuilder(Math.min(value.length(), maximumCharacters));
		for (int index = 0; (index < value.length()) && (result.length() < maximumCharacters);)
		{
			final int character = value.codePointAt(index);
			final boolean xml = ((character >= 0x20) && (character <= 0xD7FF)) || ((character >= 0xE000) && (character <= 0xFFFD)) || ((character >= 0x10000) && (character <= 0x10FFFF));
			final int sanitized = xml && !Character.isISOControl(character) ? character : ' ';
			if (result.length() + Character.charCount(sanitized) > maximumCharacters) { break; }
			result.appendCodePoint(sanitized);
			index += Character.charCount(character);
		}
		return result.toString();
	}

	private record Node(String kind, Throwable failure) { }

	private static final class BoundedDetail
	{
		private final StringBuilder text = new StringBuilder();
		private int bytes;
		private boolean truncated;

		private void append(String value)
		{
			for (int index = 0; index < value.length();)
			{
				final int character = value.codePointAt(index);
				final String scalar = new String(Character.toChars(character));
				final int size = scalar.getBytes(StandardCharsets.UTF_8).length;
				if (bytes + size > MAX_DETAIL_BYTES) { truncated = true; return; }
				text.append(scalar);
				bytes += size;
				index += Character.charCount(character);
			}
		}
	}
}

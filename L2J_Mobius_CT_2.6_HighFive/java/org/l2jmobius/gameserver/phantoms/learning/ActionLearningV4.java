/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bounded offline Huber SGD. One exact native event can receive gradient credit only once. */
public final class ActionLearningV4
{
	private ActionLearningV4() { }
	public static ActionValueV4 fit(ActionValueV4 parent, List<ActionFeedbackV4> samples)
	{
		if (parent == null || samples == null || samples.isEmpty() || samples.size() > 256) { throw new IllegalArgumentException("V4 bounded native batch"); }
		final double[] weights = parent.weights(); final var events = new TreeMap<>(parent.events()); final var episodes = new TreeMap<>(parent.episodes());
		final var fingerprints = new TreeMap<String, String>(); long updates = parent.updates();
		for (var sample : samples.stream().sorted(Comparator.comparing(s -> s.row().eventKey())).toList())
		{
			final var row = sample.row(); final String split = episodes.putIfAbsent(sample.episodeId(), row.split());
			if (split != null && !split.equals(row.split())) { throw new IllegalArgumentException("V4 episode split contamination"); }
			final String previous = events.putIfAbsent(row.eventKey(), sample.fingerprint());
			if (previous != null)
			{
				if (!previous.equals(sample.fingerprint())) { throw new IllegalArgumentException("V4 native retry changed source"); }
				continue;
			}
			if (events.size() > ActionValueV4.MAX_EVENTS) { throw new IllegalArgumentException("V4 event budget"); }
			fingerprints.put(row.eventKey(), sample.fingerprint());
			if (!row.split().equals("TRAIN")) { continue; }
			final var attempt = row.receipt().attempt(); final double[] x = ActionValueV4.features(attempt.observation(), attempt.selected());
			double prediction = 0, norm = 1;
			for (int i = 0; i < x.length; i++) { prediction += weights[i] * x[i]; norm += x[i] * x[i]; }
			final double gradient = Math.clamp(prediction - sample.label(), -.25, .25);
			for (int i = 0; i < x.length; i++) { weights[i] = Math.clamp(weights[i] - .05 * gradient * x[i] / norm, -4, 4); }
			updates++;
		}
		if (fingerprints.isEmpty()) { return parent; }
		final String fingerprint = TacticalObservationV3.hash(fingerprints.toString().getBytes(StandardCharsets.UTF_8));
		return new ActionValueV4(weights, events, episodes, parent.sha256(), fingerprint, updates);
	}
	public static void save(Path target, ActionValueV4 model) throws IOException
	{
		final Path path = target.toAbsolutePath().normalize();
		if (!path.getParent().equals(path.getParent().toRealPath()) || Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { throw new IOException("V4 requires fresh physical candidate path"); }
		final Path temporary = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".part");
		try
		{
			try (var file = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW))
			{
				final var buffer = ByteBuffer.wrap(model.bytes()); while (buffer.hasRemaining()) { file.write(buffer); } file.force(true);
			}
			Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
		}
		finally { Files.deleteIfExists(temporary); }
	}
	public static ActionValueV4 load(Path path, String expectedSha) throws IOException
	{
		if (!path.isAbsolute() || !path.normalize().equals(path.toRealPath()) || Files.isSymbolicLink(path) || !Files.isRegularFile(path) || Files.size(path) > 1_000_000
			|| path.getFileName().toString().endsWith(".part")) { throw new IOException("V4 physical committed checkpoint required"); }
		return ActionValueV4.restore(Files.readAllBytes(path), expectedSha);
	}
}

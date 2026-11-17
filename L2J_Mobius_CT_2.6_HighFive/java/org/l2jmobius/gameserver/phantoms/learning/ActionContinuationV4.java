/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Explicit offline TEST continuation. No runtime registration, scheduler, SQL or model promotion. */
public final class ActionContinuationV4
{
	public static final String VERSION = "V4_SEGMENT_CONTINUATION_V1";
	public record State(String head, ActionValueV4 model, long globalUpdates, int segment, long sequence,
		Map<String, String> events, Map<String, String> episodes)
	{
		public State { events = Map.copyOf(events); episodes = Map.copyOf(episodes); }
	}
	private record Seal(String previous, String model, long updates, int segment, long sequence, long writer,
		String nativeSha, String batchSha, long train, Map<String, String> events, Map<String, String> episodes) { }
	@FunctionalInterface private interface Writer { void write(DataOutputStream out) throws IOException; }
	@FunctionalInterface private interface Operation<T> { T run() throws IOException; }
	private ActionContinuationV4() { }
	private static void require(boolean value, String reason) { if (!value) { throw new IllegalArgumentException("V4 continuation: " + reason); } }
	private static String hash(byte[] bytes) { return TacticalObservationV3.hash(bytes); }
	private static void digest(String value) { require(value != null && value.matches("[a-f0-9]{64}"), "SHA"); }
	private static Path physical(Path root) throws IOException
	{
		require(root.isAbsolute() && root.normalize().equals(root.toRealPath()) && Files.isDirectory(root), "physical root");
		return root;
	}
	private static <T> T locked(Path root, Operation<T> operation) throws IOException
	{
		physical(root);
		try (var channel = FileChannel.open(root.resolve("continuation.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE))
		{
			try (var lock = channel.tryLock())
			{
				if (lock == null) { throw new IOException("V4 continuation writer already active"); }
				return operation.run();
			}
			catch (java.nio.channels.OverlappingFileLockException failure) { throw new IOException("V4 continuation duplicate writer", failure); }
		}
	}
	private static byte[] encode(Writer writer) throws IOException
	{
		var buffer = new ByteArrayOutputStream();
		try (var out = new DataOutputStream(buffer)) { writer.write(out); }
		return buffer.toByteArray();
	}
	private static void publish(Path path, byte[] bytes) throws IOException
	{
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
		{
			require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Arrays.equals(Files.readAllBytes(path), bytes), "immutable artifact conflict"); return;
		}
		Path part = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".part");
		try
		{
			try (var file = FileChannel.open(part, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
			{ var buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) { file.write(buffer); } file.force(true); }
			Files.move(part, path, StandardCopyOption.ATOMIC_MOVE);
		}
		finally { Files.deleteIfExists(part); }
	}
	private static void head(Path root, String expected, String next) throws IOException
	{
		String actual = Files.exists(root.resolve("HEAD")) ? Files.readString(root.resolve("HEAD")) : "";
		require(actual.equals(expected), "HEAD compare-and-swap"); digest(next);
		Path pending = root.resolve("HEAD." + UUID.randomUUID() + ".part");
		try
		{
			publish(pending, next.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			Files.move(pending, root.resolve("HEAD"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			require(Files.readString(root.resolve("HEAD")).equals(next), "HEAD readback");
		}
		finally { Files.deleteIfExists(pending); }
	}
	private static void map(DataOutputStream out, Map<String, String> values) throws IOException
	{ out.writeInt(values.size()); for (var entry : new TreeMap<>(values).entrySet()) { out.writeUTF(entry.getKey()); out.writeUTF(entry.getValue()); } }
	private static Map<String, String> map(DataInputStream in, boolean episodes) throws IOException
	{
		int count = in.readInt(); require(count >= 0 && count <= 1_000_000, "ledger bounds");
		var result = new TreeMap<String, String>();
		for (int i = 0; i < count; i++)
		{
			String key = in.readUTF(), value = in.readUTF(); digest(key);
			if (episodes) { require(Set.of("TRAIN", "TUNING", "AUDIT").contains(value), "split"); } else { digest(value); }
			require(result.putIfAbsent(key, value) == null, "duplicate key");
		}
		return result;
	}
	private static byte[] bytes(Seal seal) throws IOException
	{
		return encode(out -> {
			out.writeUTF(VERSION); out.writeUTF(seal.previous); out.writeUTF(seal.model); out.writeLong(seal.updates);
			out.writeInt(seal.segment); out.writeLong(seal.sequence); out.writeLong(seal.writer); out.writeUTF(seal.nativeSha);
			out.writeUTF(seal.batchSha); out.writeLong(seal.train); map(out, seal.events); map(out, seal.episodes);
		});
	}
	private static Seal seal(Path root, String sha) throws IOException
	{
		digest(sha); Path path = root.resolve("ledger-" + sha + ".bin");
		require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 160_000_000, "physical ledger");
		byte[] bytes = Files.readAllBytes(path); require(hash(bytes).equals(sha), "ledger hash");
		try (var in = new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			require(in.readUTF().equals(VERSION), "ledger version");
			var value = new Seal(in.readUTF(), in.readUTF(), in.readLong(), in.readInt(), in.readLong(), in.readLong(),
				in.readUTF(), in.readUTF(), in.readLong(), map(in, false), map(in, true));
			digest(value.model); digest(value.nativeSha); digest(value.batchSha);
			require(in.available() == 0 && Arrays.equals(bytes, bytes(value)) && value.updates >= 0 && value.segment >= 0 && value.sequence >= 0, "canonical ledger");
			return value;
		}
	}
	private static State state(Path root, String target) throws IOException
	{
		physical(root); var chain = new ArrayList<Map.Entry<String, Seal>>(); var seen = new HashSet<String>(); String cursor = target;
		while (!cursor.isEmpty())
		{
			require(seen.add(cursor) && chain.size() < 100_000, "lineage cycle/budget");
			var seal = seal(root, cursor); chain.add(Map.entry(cursor, seal)); cursor = seal.previous;
		}
		require(!chain.isEmpty(), "empty lineage"); Collections.reverse(chain);
		var events = new TreeMap<String, String>(); var episodes = new TreeMap<String, String>(); State previous = null;
		for (var entry : chain)
		{
			var s = entry.getValue(); var model = ActionLearningV4.load(root.resolve("candidate-" + s.model + ".bin"), s.model);
			if (previous == null)
			{
				require(s.sequence == 0 && s.segment == 0 && s.updates == model.updates() && s.events.equals(model.events()) && s.episodes.equals(model.episodes()), "root import");
			}
			else
			{
				require(s.sequence == previous.sequence + 1 && s.updates == Math.addExact(previous.globalUpdates, s.train)
					&& (s.segment == previous.segment || s.segment == previous.segment + 1), "global counts/segment");
				require(model.updates() == (s.segment == previous.segment ? previous.model.updates() : 0) + s.train, "local counts");
				if (s.segment != previous.segment)
				{
					var base = segment(previous.model);
					require(Arrays.equals(ActionLearningV4.load(root.resolve("candidate-" + base.sha256() + ".bin"), base.sha256()).bytes(), base.bytes()), "physical rollover base");
				}
			}
			for (var event : s.events.entrySet()) { require(events.putIfAbsent(event.getKey(), event.getValue()) == null, "global duplicate event"); }
			for (var episode : s.episodes.entrySet())
			{ String old = episodes.putIfAbsent(episode.getKey(), episode.getValue()); require(old == null || old.equals(episode.getValue()), "global split contamination"); }
			for (var event : model.events().entrySet()) { require(event.getValue().equals(events.get(event.getKey())), "model/global key conflict"); }
			previous = new State(entry.getKey(), model, s.updates, s.segment, s.sequence, events, episodes);
		}
		return previous;
	}
	public static State load(Path root) throws IOException { physical(root); return state(root, Files.readString(root.resolve("HEAD"))); }
	public static State initialize(Path root, ActionValueV4 parent) throws IOException
	{
		return locked(root, () -> {
			require(!Files.exists(root.resolve("HEAD")), "fresh campaign required");
			publish(root.resolve("candidate-" + parent.sha256() + ".bin"), parent.bytes());
			var seal = new Seal("", parent.sha256(), parent.updates(), 0, 0, ProcessHandle.current().pid(), "0".repeat(64), "0".repeat(64), 0, parent.events(), parent.episodes());
			byte[] bytes = bytes(seal); String sha = hash(bytes); publish(root.resolve("ledger-" + sha + ".bin"), bytes); head(root, "", sha); return load(root);
		});
	}
	public static ActionValueV4 segment(ActionValueV4 previous)
	{ return new ActionValueV4(previous.weights(), Map.of(), Map.of(), previous.sha256(), "0".repeat(64), 0); }
	public static State prepare(Path root, String expectedHead, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		return locked(root, () -> {
			digest(nativeSha); State before = load(root); require(before.head.equals(expectedHead), "stale parent");
			require(samples != null && !samples.isEmpty() && samples.size() <= 256, "batch<=256");
			var events = new TreeMap<String, String>(); var episodes = new TreeMap<String, String>(); var fresh = new ArrayList<ActionFeedbackV4>(); long train = 0;
			for (var sample : samples)
			{
				String key = sample.row().eventKey(), fingerprint = sample.fingerprint(), split = sample.row().split();
				String priorSplit = before.episodes.get(sample.episodeId());
				require(priorSplit == null || priorSplit.equals(split), "historical episode split changed");
				String prior = before.events.get(key);
				if (prior != null) { require(prior.equals(fingerprint), "historical source changed"); continue; }
				require(events.putIfAbsent(key, fingerprint) == null, "duplicate batch event");
				String old = episodes.putIfAbsent(sample.episodeId(), split); require(old == null || old.equals(split), "batch episode split");
				fresh.add(sample); if (split.equals("TRAIN")) { train++; }
			}
			if (fresh.isEmpty()) { return before; }
			String batchSha = hash(encode(out -> { map(out, events); map(out, episodes); }));
			String inputSha = hash((expectedHead + nativeSha + batchSha).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
			Path pending = root.resolve("prepared-" + inputSha + ".seal");
			if (Files.exists(pending)) { State old = state(root, Files.readString(pending)); var s = seal(root, old.head); require(s.previous.equals(expectedHead) && s.nativeSha.equals(nativeSha) && s.batchSha.equals(batchSha), "prepared input changed"); return old; }
			boolean rollover = before.model.events().size() + fresh.size() > ActionValueV4.MAX_EVENTS || before.model.episodes().size() + episodes.size() > ActionValueV4.MAX_EVENTS;
			ActionValueV4 base = rollover ? segment(before.model) : before.model;
			if (rollover) { publish(root.resolve("candidate-" + base.sha256() + ".bin"), base.bytes()); }
			ActionValueV4 learned = ActionLearningV4.fit(base, fresh);
			publish(root.resolve("candidate-" + learned.sha256() + ".bin"), learned.bytes());
			var seal = new Seal(expectedHead, learned.sha256(), Math.addExact(before.globalUpdates, train), before.segment + (rollover ? 1 : 0), before.sequence + 1,
				ProcessHandle.current().pid(), nativeSha, batchSha, train, events, episodes);
			byte[] bytes = bytes(seal); String sha = hash(bytes); publish(root.resolve("ledger-" + sha + ".bin"), bytes);
			publish(pending, sha.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); return state(root, sha);
		});
	}
	public static void cold(Path root, String target) throws IOException
	{
		var state = state(root, target); var s = seal(root, target);
		require(s.writer != ProcessHandle.current().pid(), "cold requires separate JVM");
		publish(root.resolve("cold-" + target + ".bin"), encode(out -> { out.writeUTF(target); out.writeUTF(state.model.sha256()); out.writeLong(state.globalUpdates); }));
	}
	/** Reauthenticate caller-verified native inputs without repeating SGD, including after HEAD publication. */
	public static void verifyPrepared(Path root, String expected, String target, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		State before = state(root, expected), after = state(root, target), current = load(root); Seal prepared = seal(root, target);
		require(current.head.equals(expected) || current.head.equals(target), "prepared HEAD changed");
		require(prepared.previous.equals(expected) && prepared.nativeSha.equals(nativeSha) && samples != null && !samples.isEmpty() && samples.size() <= 256, "prepared native source changed");
		var events = new TreeMap<String, String>(); var episodes = new TreeMap<String, String>(); long train = 0;
		for (var sample : samples)
		{
			String oldSplit = before.episodes.get(sample.episodeId()); require(oldSplit == null || oldSplit.equals(sample.row().split()), "prepared split changed");
			String old = before.events.get(sample.row().eventKey());
			if (old != null) { require(old.equals(sample.fingerprint()), "prepared historical source changed"); continue; }
			require(events.putIfAbsent(sample.row().eventKey(), sample.fingerprint()) == null, "prepared duplicate");
			old = episodes.putIfAbsent(sample.episodeId(), sample.row().split()); require(old == null || old.equals(sample.row().split()), "prepared mixed split");
			if (sample.row().split().equals("TRAIN")) { train++; }
		}
		require(prepared.events.equals(events) && prepared.episodes.equals(episodes) && prepared.train == train
			&& prepared.batchSha.equals(hash(encode(out -> { map(out, events); map(out, episodes); })))
			&& after.globalUpdates == before.globalUpdates + train, "prepared batch changed");
	}
	public static State commit(Path root, String expected, String target) throws IOException
	{
		return locked(root, () -> {
			State current = load(root), next = state(root, target); var s = seal(root, target);
			byte[] cold = encode(out -> { out.writeUTF(target); out.writeUTF(next.model.sha256()); out.writeLong(next.globalUpdates); });
			Path path = root.resolve("cold-" + target + ".bin"); require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Arrays.equals(Files.readAllBytes(path), cold), "verified cold seal required");
			if (current.head.equals(target)) { return current; }
			require(current.head.equals(expected) && s.previous.equals(expected), "commit parent changed"); head(root, expected, target); return load(root);
		});
	}
}

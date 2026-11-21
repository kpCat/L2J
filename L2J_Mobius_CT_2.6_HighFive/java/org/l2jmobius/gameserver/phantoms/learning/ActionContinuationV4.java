/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit offline TEST continuation. No runtime registration, scheduler, SQL or model promotion. */
public final class ActionContinuationV4
{
	public static final String VERSION = "V4_SEGMENT_CONTINUATION_V1";
	private static final String TRAIN_BATCH_VERSION = "V4_TRAIN_BATCH_CONTINUATION_V1";
	private static final int FIT_LIMIT = 256;
	public record State(String head, ActionValueV4 model, long globalUpdates, int segment, long sequence,
		Map<String, String> events, Map<String, String> episodes)
	{
		public State { events = Map.copyOf(events); episodes = Map.copyOf(episodes); }
	}
	private record Seal(String previous, String model, long updates, int segment, long sequence, long writer,
		String nativeSha, String batchSha, String trainBatchSha, long train, Map<String, String> events, Map<String, String> episodes) { }
	private record TrainBatch(List<ActionFeedbackV4> fresh, String inputSha) { }
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
			out.writeUTF(seal.trainBatchSha.isEmpty() ? VERSION : TRAIN_BATCH_VERSION);
			out.writeUTF(seal.previous); out.writeUTF(seal.model); out.writeLong(seal.updates);
			out.writeInt(seal.segment); out.writeLong(seal.sequence); out.writeLong(seal.writer); out.writeUTF(seal.nativeSha);
			out.writeUTF(seal.batchSha);
			if (!seal.trainBatchSha.isEmpty()) { out.writeUTF(seal.trainBatchSha); }
			out.writeLong(seal.train); map(out, seal.events); map(out, seal.episodes);
		});
	}
	private static Seal seal(Path root, String sha) throws IOException
	{
		digest(sha); Path path = root.resolve("ledger-" + sha + ".bin");
		require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) <= 160_000_000, "physical ledger");
		byte[] bytes = Files.readAllBytes(path); require(hash(bytes).equals(sha), "ledger hash");
		try (var in = new DataInputStream(new ByteArrayInputStream(bytes)))
		{
			String version = in.readUTF();
			require(version.equals(VERSION) || version.equals(TRAIN_BATCH_VERSION), "ledger version");
			String previous = in.readUTF(), model = in.readUTF();
			long updates = in.readLong();
			int segment = in.readInt();
			long sequence = in.readLong(), writer = in.readLong();
			String nativeSha = in.readUTF(), batchSha = in.readUTF();
			String trainBatchSha = version.equals(TRAIN_BATCH_VERSION) ? in.readUTF() : "";
			if (version.equals(TRAIN_BATCH_VERSION)) { digest(trainBatchSha); }
			var value = new Seal(previous, model, updates, segment, sequence, writer, nativeSha, batchSha, trainBatchSha,
				in.readLong(), map(in, false), map(in, true));
			digest(value.model); digest(value.nativeSha); digest(value.batchSha);
			require(in.available() == 0 && Arrays.equals(bytes, bytes(value)) && value.updates >= 0 && value.segment >= 0 && value.sequence >= 0, "canonical ledger");
			return value;
		}
	}
	private static List<Map.Entry<String, Seal>> lineage(Path root, String target) throws IOException
	{
		physical(root); var chain = new ArrayList<Map.Entry<String, Seal>>(); var seen = new HashSet<String>(); String cursor = target;
		while (!cursor.isEmpty())
		{
			require(seen.add(cursor) && chain.size() < 100_000, "lineage cycle/budget");
			var seal = seal(root, cursor); chain.add(Map.entry(cursor, seal)); cursor = seal.previous;
		}
		require(!chain.isEmpty(), "empty lineage"); Collections.reverse(chain);
		return chain;
	}

	private static State state(Path root, String target) throws IOException
	{
		return state(root, lineage(root, target), target);
	}

	private static State state(Path root, List<Map.Entry<String, Seal>> chain, String target) throws IOException
	{
		var events = new TreeMap<String, String>(); var episodes = new TreeMap<String, String>(); State previous = null, selected = null;
		for (var entry : chain)
		{
			var s = entry.getValue(); var model = ActionLearningV4.load(root.resolve("candidate-" + s.model + ".bin"), s.model);
			if (previous == null)
			{
				require(s.trainBatchSha.isEmpty() && s.sequence == 0 && s.segment == 0 && s.updates == model.updates()
					&& s.events.equals(model.events()) && s.episodes.equals(model.episodes()), "root import");
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
			if (previous.head.equals(target)) { selected = previous; }
		}
		require(selected != null, "checkpoint is not a committed ancestor");
		return selected;
	}

	private static String readHead(Path root) throws IOException
	{
		final Path path = physical(root).resolve("HEAD");
		require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == 64, "physical HEAD");
		final String current = Files.readString(path, StandardCharsets.US_ASCII);
		digest(current);
		return current;
	}

	public static State load(Path root) throws IOException { return state(root, readHead(root)); }

	/** Select only a published checkpoint, excluding internal FIT chunks even when they were cold-read. */
	static State committedState(Path root, String target) throws IOException
	{
		return committedState(root, target, readHead(root));
	}

	/** AUDIT reference is the first published TRAIN batch. A candidate must descend from it with further TRAIN updates. */
	public static void verifyAuditModelOrder(Path root, String referenceHead, String candidateHead, String referenceModel, String candidateModel) throws IOException
	{
		final String tip = readHead(root);
		final State reference = committedState(root, referenceHead, tip);
		final State candidate = committedState(root, candidateHead, tip);
		require(reference.model().sha256().equals(referenceModel) && candidate.model().sha256().equals(candidateModel)
			&& !referenceModel.equals(candidateModel) && candidate.sequence() > reference.sequence()
			&& candidate.globalUpdates() > reference.globalUpdates(), "AUDIT reference/candidate order or model pin");
		final var chain = lineage(root, tip);
		int first = 1;
		while (first < chain.size() && chain.get(first).getValue().trainBatchSha().isEmpty()) { first++; }
		require(first < chain.size(), "missing first committed AUDIT TRAIN batch");
		final String batch = chain.get(first).getValue().trainBatchSha();
		while (first + 1 < chain.size() && chain.get(first + 1).getValue().trainBatchSha().equals(batch)) { first++; }
		require(chain.get(first).getKey().equals(referenceHead) && !seal(root, candidateHead).trainBatchSha().isEmpty(), "AUDIT requires published TRAIN batch heads");
		require(readHead(root).equals(tip), "AUDIT lineage HEAD changed");
	}

	private static State committedState(Path root, String target, String current) throws IOException
	{
		digest(target);
		final var chain = lineage(root, current);
		for (int index = 0; index < chain.size(); index++)
		{
			final var entry = chain.get(index);
			if (!entry.getKey().equals(target)) { continue; }
			final String batch = entry.getValue().trainBatchSha;
			require(batch.isEmpty() || index + 1 == chain.size() || !chain.get(index + 1).getValue().trainBatchSha.equals(batch),
				"checkpoint is an internal TRAIN chunk");
			final State selected = state(root, chain, target);
			// initialize publishes the imported root directly. Every later HEAD transition required a cold seal.
			if (index > 0) { requireCold(root, selected); }
			require(readHead(root).equals(current), "committed lineage HEAD changed");
			return selected;
		}
		throw new IllegalArgumentException("V4 continuation: checkpoint is not a committed ancestor");
	}
	public static State initialize(Path root, ActionValueV4 parent) throws IOException
	{
		return locked(root, () -> {
			require(!Files.exists(root.resolve("HEAD")), "fresh campaign required");
			publish(root.resolve("candidate-" + parent.sha256() + ".bin"), parent.bytes());
			var seal = new Seal("", parent.sha256(), parent.updates(), 0, 0, ProcessHandle.current().pid(), "0".repeat(64), "0".repeat(64), "", 0, parent.events(), parent.episodes());
			byte[] bytes = bytes(seal); String sha = hash(bytes); publish(root.resolve("ledger-" + sha + ".bin"), bytes); head(root, "", sha); return load(root);
		});
	}
	public static ActionValueV4 segment(ActionValueV4 previous)
	{ return new ActionValueV4(previous.weights(), Map.of(), Map.of(), previous.sha256(), "0".repeat(64), 0); }

	public static State prepare(Path root, String expectedHead, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		return locked(root, () -> {
			digest(nativeSha); State before = load(root); require(before.head.equals(expectedHead), "stale parent");
			return prepare(root, before, samples, nativeSha, "");
		});
	}

	private static State prepare(Path root, State before, List<ActionFeedbackV4> samples, String nativeSha, String trainBatchSha) throws IOException
	{
		require(samples != null && !samples.isEmpty() && samples.size() <= FIT_LIMIT, "batch<=256");
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
		Path pending = preparedPin(root, before.head, nativeSha, batchSha);
		if (Files.exists(pending))
		{
			State old = state(root, Files.readString(pending)); var s = seal(root, old.head);
			require(s.previous.equals(before.head) && s.nativeSha.equals(nativeSha) && s.batchSha.equals(batchSha)
				&& (trainBatchSha.isEmpty() || s.trainBatchSha.equals(trainBatchSha)), "prepared input changed");
			return old;
		}
		boolean rollover = before.model.events().size() + fresh.size() > ActionValueV4.MAX_EVENTS || before.model.episodes().size() + episodes.size() > ActionValueV4.MAX_EVENTS;
		ActionValueV4 base = rollover ? segment(before.model) : before.model;
		if (rollover) { publish(root.resolve("candidate-" + base.sha256() + ".bin"), base.bytes()); }
		ActionValueV4 learned = ActionLearningV4.fit(base, fresh);
		publish(root.resolve("candidate-" + learned.sha256() + ".bin"), learned.bytes());
		var seal = new Seal(before.head, learned.sha256(), Math.addExact(before.globalUpdates, train), before.segment + (rollover ? 1 : 0), before.sequence + 1,
			ProcessHandle.current().pid(), nativeSha, batchSha, trainBatchSha, train, events, episodes);
		byte[] bytes = bytes(seal); String sha = hash(bytes); publish(root.resolve("ledger-" + sha + ".bin"), bytes);
		publish(pending, sha.getBytes(StandardCharsets.US_ASCII)); return state(root, sha);
	}

	/** Caller authenticates the whole drained TRAIN seal before preparing its ordered FIT chunks. */
	public static State prepareBatch(Path root, String expectedHead, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		return locked(root, () -> {
			State next = load(root);
			require(next.head.equals(expectedHead), "stale parent");
			var batch = trainBatch(next, samples, nativeSha);
			if (batch.fresh.isEmpty()) { return next; }
			publish(trainBatchPin(root, expectedHead, nativeSha), batch.inputSha.getBytes(StandardCharsets.US_ASCII));
			for (int start = 0; start < batch.fresh.size(); )
			{
				int end = start + Math.min(FIT_LIMIT, batch.fresh.size() - start);
				next = prepare(root, next, batch.fresh.subList(start, end), nativeSha, batch.inputSha);
				start = end;
			}
			return next;
		});
	}

	private static Path trainBatchPin(Path root, String expectedHead, String nativeSha)
	{
		return root.resolve("train-batch-" + hash((expectedHead + nativeSha).getBytes(StandardCharsets.US_ASCII)) + ".seal");
	}

	private static Path preparedPin(Path root, String previous, String nativeSha, String batchSha)
	{
		return root.resolve("prepared-" + hash((previous + nativeSha + batchSha).getBytes(StandardCharsets.US_ASCII)) + ".seal");
	}

	private static TrainBatch trainBatch(State before, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		digest(nativeSha);
		require(samples != null && !samples.isEmpty(), "sealed TRAIN batch required");
		var events = new TreeMap<String, String>();
		var fresh = new TreeMap<String, ActionFeedbackV4>();
		var settlements = new TreeMap<String, TacticalEpisodeV3>();
		var freshEpisodeKeys = new HashMap<String, Set<String>>();
		for (var sample : samples)
		{
			require(sample != null && sample.row().split().equals("TRAIN"), "TRAIN split required");
			var episode = sample.episode();
			var seen = settlements.putIfAbsent(episode.id(), episode);
			require(seen == null || seen.equals(episode), "different settlements for one TRAIN episode");
			String priorSplit = before.episodes.get(episode.id());
			require(priorSplit == null || priorSplit.equals("TRAIN"), "historical episode split changed");
			String key = sample.row().eventKey(), fingerprint = sample.fingerprint();
			String duplicate = events.putIfAbsent(key, fingerprint), prior = before.events.get(key);
			require(duplicate == null || duplicate.equals(fingerprint), "duplicate batch source changed");
			require(prior == null || prior.equals(fingerprint), "historical source changed");
			if (prior == null && duplicate == null)
			{
				require(priorSplit == null, "new event in an already published TRAIN episode");
				fresh.put(key, sample);
				freshEpisodeKeys.computeIfAbsent(episode.id(), _ -> new HashSet<>()).add(key);
			}
		}
		for (var entry : freshEpisodeKeys.entrySet())
		{
			var expectedKeys = settlements.get(entry.getKey()).eventKeys();
			require(entry.getValue().size() == expectedKeys.size() && entry.getValue().containsAll(expectedKeys), "incomplete settled TRAIN episode");
		}
		String inputSha = hash(encode(out -> {
			out.writeUTF(before.head); out.writeUTF(nativeSha); map(out, events);
			out.writeInt(settlements.size());
			for (var episode : settlements.values())
			{
				// The legacy episode JSON hashes duration alone. Pin its original native clock window here.
				out.writeUTF(episode.id()); out.writeLong(episode.startedNanos()); out.writeLong(episode.settledNanos());
			}
		}));
		return new TrainBatch(List.copyOf(fresh.values()), inputSha);
	}

	/** Reauthenticate every prepared chunk, including on a retry after publication. */
	public static void verifyPreparedBatch(Path root, String expected, String target, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		verifyBatch(root, load(root), expected, target, samples, nativeSha);
	}

	/** Read-only confirmation that the exact complete TRAIN batch produced the current cold-verified HEAD. */
	public static void verifyCurrentBatchOrigin(Path root, String expected, String target, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		final State current = load(root);
		require(current.head.equals(target) && !expected.equals(target), "committed TRAIN HEAD required");
		requireCold(root, verifyBatch(root, current, expected, target, samples, nativeSha));
		require(readHead(root).equals(target), "committed TRAIN HEAD changed");
	}

	/** Reauthenticate an exact complete TRAIN batch whose final cold-verified checkpoint is on published lineage. */
	public static void verifyCommittedBatchOrigin(Path root, String expected, String target, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		require(!Objects.equals(expected, target), "committed TRAIN HEAD required");
		final String current = readHead(root);
		final State checkpoint = committedState(root, target, current);
		verifyBatch(root, checkpoint, expected, target, samples, nativeSha);
		require(readHead(root).equals(current), "committed lineage HEAD changed");
	}

	private static State verifyBatch(Path root, State current, String expected, String target, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		require(current.head.equals(expected) || current.head.equals(target), "prepared HEAD changed");
		State before = current.head.equals(expected) ? current : state(root, expected);
		var batch = trainBatch(before, samples, nativeSha);
		if (batch.fresh.isEmpty())
		{
			require(expected.equals(target), "unexpected target for replay");
			return before;
		}
		Path pin = trainBatchPin(root, expected, nativeSha);
		require(Files.isRegularFile(pin, LinkOption.NOFOLLOW_LINKS)
			&& Arrays.equals(Files.readAllBytes(pin), batch.inputSha.getBytes(StandardCharsets.US_ASCII)), "prepared TRAIN input changed");
		String cursor = target;
		for (int end = batch.fresh.size(); end > 0; )
		{
			require(!cursor.equals(expected), "missing TRAIN chunk");
			Seal chunk = seal(root, cursor);
			int start = ((end - 1) / FIT_LIMIT) * FIT_LIMIT;
			var events = new TreeMap<String, String>();
			var episodes = new TreeMap<String, String>();
			for (var sample : batch.fresh.subList(start, end))
			{
				events.put(sample.row().eventKey(), sample.fingerprint());
				episodes.put(sample.episodeId(), "TRAIN");
			}
			require(chunk.nativeSha.equals(nativeSha) && chunk.trainBatchSha.equals(batch.inputSha) && chunk.train == end - start
				&& chunk.events.equals(events) && chunk.episodes.equals(episodes)
				&& chunk.batchSha.equals(hash(encode(out -> { map(out, events); map(out, episodes); }))), "prepared TRAIN chunk changed");
			Path published = preparedPin(root, chunk.previous, chunk.nativeSha, chunk.batchSha);
			require(Files.isRegularFile(published, LinkOption.NOFOLLOW_LINKS) && Files.size(published) == 64
				&& Files.readString(published, StandardCharsets.US_ASCII).equals(cursor), "missing or changed prepared TRAIN chunk pointer");
			cursor = chunk.previous;
			end = start;
		}
		require(cursor.equals(expected), "prepared TRAIN parent changed");
		State after = current.head.equals(target) ? current : state(root, target);
		require(after.globalUpdates == Math.addExact(before.globalUpdates, batch.fresh.size()), "prepared TRAIN count changed");
		return after;
	}

	/** Only the complete cold-verified chain can advance HEAD. */
	public static State commitBatch(Path root, String expected, String target, List<ActionFeedbackV4> samples, String nativeSha) throws IOException
	{
		return locked(root, () -> {
			State current = load(root), next = verifyBatch(root, current, expected, target, samples, nativeSha);
			if (expected.equals(target)) { return current; }
			return publishHead(root, current, expected, next);
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
		require(prepared.previous.equals(expected) && prepared.nativeSha.equals(nativeSha) && samples != null && !samples.isEmpty() && samples.size() <= FIT_LIMIT, "prepared native source changed");
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
			require(s.trainBatchSha.isEmpty(), "whole TRAIN batch commit required");
			if (!current.head.equals(target))
			{
				require(s.previous.equals(expected), "commit parent changed");
				require(Files.notExists(trainBatchPin(root, expected, s.nativeSha), LinkOption.NOFOLLOW_LINKS), "whole TRAIN batch commit required");
			}
			return publishHead(root, current, expected, next);
		});
	}

	private static State publishHead(Path root, State current, String expected, State next) throws IOException
	{
		requireCold(root, next);
		if (current.head.equals(next.head)) { return current; }
		require(current.head.equals(expected), "commit parent changed");
		head(root, expected, next.head);
		return load(root);
	}

	private static void requireCold(Path root, State next) throws IOException
	{
		byte[] cold = encode(out -> { out.writeUTF(next.head); out.writeUTF(next.model.sha256()); out.writeLong(next.globalUpdates); });
		Path path = root.resolve("cold-" + next.head + ".bin");
		require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && Files.size(path) == cold.length
			&& Arrays.equals(Files.readAllBytes(path), cold), "verified cold seal required");
	}
}

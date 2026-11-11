/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.learning;

import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/** Offline streaming replay. Ledger storage is independent of this bounded, reproducible fit window. */
final class TacticalReplayV3
{
	static final String REVISION = "004/family-class-level10-party-split-spot-outcome/recent16-hash16/128strata/failures-v1";
	private static final int MAX_STRATA = 128, HALF_WINDOW = 16;
	private final Set<String> _usedAudit;
	private final String _prefix;
	private final TreeMap<String, Bucket> _groups = new TreeMap<>();
	record ContextSample(String key, String kind, String outcome, String spot, String receiptSha, String episodeId, String settlementSha)
	{
		ContextSample
		{
			if (key == null || !key.matches("[a-f0-9]{64}") || !Set.of("EFFECT", "FAILURE").contains(kind) || outcome == null || !outcome.matches("[A-Z0-9_]{1,80}")
				|| spot == null || spot.isBlank() || spot.length() > 256 || spot.chars().anyMatch(v -> v < 32) || receiptSha == null || !receiptSha.matches("[a-f0-9]{64}")
				|| (episodeId == null) != (settlementSha == null) || episodeId != null && !episodeId.matches("[a-f0-9]{64}") || settlementSha != null && !settlementSha.matches("[a-f0-9]{64}"))
			{ throw new IllegalArgumentException("Invalid replay context provenance"); }
		}
	}
	private record EffectEntry(TacticalStoreV3.Row row, ContextSample context) { }
	record FailureRow(String split, String contextKey, TacticalFailureV3 failure)
	{
		FailureRow
		{
			if (!Set.of("TRAIN", "TUNING", "AUDIT").contains(split) || contextKey == null || contextKey.isBlank() || contextKey.length() > 256
				|| contextKey.chars().anyMatch(v -> v < 32) || failure == null) { throw new IllegalArgumentException("Invalid failure replay row"); }
		}
	}
	static String spot(String context)
	{
		final int marker = context.indexOf("|spot=");
		if (marker >= 0) { final int end = context.indexOf('|', marker + 6); return context.substring(marker + 6, end < 0 ? context.length() : end); }
		return context.startsWith("giran.") ? "GIRAN_HEAL" : "UNKNOWN";
	}

	TacticalReplayV3(Set<String> usedAudit, String prefix)
	{
		_usedAudit = Set.copyOf(usedAudit); _prefix = prefix;
	}
	/** Supply immutable events in durable chronological order; event key breaks timestamp ties. */
	void offer(TacticalStoreV3.Row row)
	{ offer(row, new ContextSample(row.eventKey(), "EFFECT", "ACTION_ONLY_UNKNOWN", spot(row.contextKey()), TacticalObservationV3.hash(TacticalStoreV3.encode(row)), null, null)); }
	void offer(TacticalStoreV3.Row row, ContextSample context)
	{
		if (!row.contextKey().startsWith(_prefix) || row.split().equals("AUDIT") && _usedAudit.contains(row.cohort())) { return; }
		final var attempt = row.receipt().attempt();
		final var observation = attempt.observation();
		final var level = observation.self().fact(TacticalObservationV3.Field.LEVEL);
		final String stratum = row.split() + '/' + attempt.selected().family() + '/' + observation.self().classId() + '/'
			+ (level.known() ? (int) level.value() / 10 : "UNKNOWN") + '/' + !observation.partyIdentity().startsWith("solo:") + '/' + context.spot() + '/' + context.outcome();
		if (!_groups.containsKey(stratum) && _groups.size() >= MAX_STRATA)
		{ throw new IllegalArgumentException("V3 replay stratum capacity exceeded; retain ledger and narrow campaign"); }
		_groups.computeIfAbsent(stratum, _ -> new Bucket()).offer(new EffectEntry(row, context));
	}
	List<TacticalStoreV3.Row> rows()
	{
		final var rows = new ArrayList<TacticalStoreV3.Row>();
		for (var group : _groups.values()) { group.recent.forEach(entry -> rows.add(entry.row())); group.memory.values().forEach(entry -> rows.add(entry.row())); }
		rows.sort(Comparator.comparing(TacticalStoreV3.Row::eventKey));
		return TacticalStoreV3.validateDataset(rows);
	}
	List<ContextSample> contexts()
	{
		final var contexts = new ArrayList<ContextSample>();
		for (var group : _groups.values()) { group.recent.forEach(entry -> contexts.add(entry.context())); group.memory.values().forEach(entry -> contexts.add(entry.context())); }
		return contexts.stream().sorted(Comparator.comparing(ContextSample::key)).toList();
	}
	private static final class Bucket
	{
		final ArrayDeque<EffectEntry> recent = new ArrayDeque<>();
		final TreeMap<String, EffectEntry> memory = new TreeMap<>();
		void offer(EffectEntry row)
		{
			recent.addLast(row);
			if (recent.size() <= HALF_WINDOW) { return; }
			final var older = recent.removeFirst();
			final String key = TacticalObservationV3.hash((REVISION + '/' + older.row().eventKey()).getBytes(StandardCharsets.UTF_8));
			memory.put(key, older);
			if (memory.size() > HALF_WINDOW) { memory.pollLastEntry(); }
		}
	}
	static final class FailureWindow
	{
		private final TreeMap<String,ArrayDeque<ContextSample>> _recent = new TreeMap<>();
		private final TreeMap<String,TreeMap<String,ContextSample>> _memory = new TreeMap<>();
		void offer(FailureRow row, String outcome)
		{ offer(row, new ContextSample(row.failure().eventKey(), "FAILURE", outcome + '_' + row.failure().outcome(), spot(row.contextKey()), row.failure().sha256(), null, null)); }
		void offer(FailureRow row, ContextSample sample)
		{
			if (row.split().equals("AUDIT")) { return; } // Retain raw audit failures, never feed them back to the curriculum fit window.
			final var choice = row.failure().choice();
			final var level = choice.observation().self().fact(TacticalObservationV3.Field.LEVEL);
			final String group = row.split() + '/' + choice.selected().family() + '/' + choice.observation().self().classId() + '/'
				+ (level.known() ? (int) level.value() / 10 : "UNKNOWN") + '/' + sample.spot() + '/' + sample.outcome() + '/' + row.failure().reason();
			if (!_recent.containsKey(group) && _recent.size() >= MAX_STRATA) { throw new IllegalArgumentException("Failure replay stratum capacity; raw ledger retained"); }
			final var recent = _recent.computeIfAbsent(group, _ -> new ArrayDeque<>());
			final var memory = _memory.computeIfAbsent(group, _ -> new TreeMap<>());
			recent.addLast(sample);
			if (recent.size() > HALF_WINDOW)
			{
				final var older = recent.removeFirst(); memory.put(TacticalObservationV3.hash((REVISION + older.key()).getBytes(StandardCharsets.UTF_8)), older);
				if (memory.size() > HALF_WINDOW) { memory.pollLastEntry(); }
			}
		}
		List<ContextSample> samples()
		{
			final var result = new ArrayList<ContextSample>(); _recent.values().forEach(result::addAll); _memory.values().forEach(map -> result.addAll(map.values()));
			return result.stream().sorted(Comparator.comparing(ContextSample::key)).toList();
		}
	}
	static byte[] packContexts(List<ContextSample> samples, String datasetSha)
	{
		if (samples.size() > 8192 || !datasetSha.matches("[a-f0-9]{64}")) { throw new IllegalArgumentException("Replay context bound/schema"); }
		try
		{
			final var bytes = new ByteArrayOutputStream(); final var out = new DataOutputStream(bytes);
			out.writeInt(0x43523443); out.writeInt(1); out.writeUTF(REVISION); out.writeUTF(datasetSha); out.writeInt(samples.size());
			final var keys = new java.util.HashSet<String>();
			for (var sample : samples)
			{
				if (!keys.add(sample.kind() + sample.key())) { throw new IllegalArgumentException("Duplicate replay context key"); }
				out.writeUTF(sample.key()); out.writeUTF(sample.kind()); out.writeUTF(sample.outcome()); out.writeUTF(sample.spot()); out.writeUTF(sample.receiptSha());
				out.writeUTF(sample.episodeId() == null ? "" : sample.episodeId()); out.writeUTF(sample.settlementSha() == null ? "" : sample.settlementSha());
			}
			out.flush(); if (bytes.size() > 4 * 1024 * 1024) { throw new IllegalArgumentException("Replay context byte bound"); } return bytes.toByteArray();
		}
		catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}
	static List<ContextSample> unpackContexts(byte[] bytes, String datasetSha)
	{
		if (bytes == null || bytes.length > 4 * 1024 * 1024) { throw new IllegalArgumentException("Replay context byte bound"); }
		try
		{
			final var in = new DataInputStream(new ByteArrayInputStream(bytes));
			if (in.readInt() != 0x43523443 || in.readInt() != 1 || !REVISION.equals(in.readUTF()) || !datasetSha.equals(in.readUTF()))
			{ throw new IllegalArgumentException("Replay context dataset/schema changed"); }
			final int count = in.readInt(); if (count < 0 || count > 8192) { throw new IllegalArgumentException("Replay context count"); }
			final var samples = new ArrayList<ContextSample>();
			for (int i = 0; i < count; i++)
			{
				final String key = in.readUTF(), kind = in.readUTF(), outcome = in.readUTF(), spot = in.readUTF(), sha = in.readUTF(), episode = in.readUTF(), settlement = in.readUTF();
				samples.add(new ContextSample(key,kind,outcome,spot,sha,episode.isEmpty() ? null : episode,settlement.isEmpty() ? null : settlement));
			}
			if (in.available() != 0) { throw new IllegalArgumentException("Trailing replay context bytes"); }
			packContexts(samples,datasetSha); return List.copyOf(samples);
		}
		catch (IOException failure) { throw new IllegalArgumentException("Corrupt replay context", failure); }
	}
}

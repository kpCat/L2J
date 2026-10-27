/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation.humanized;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Selection;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.PersonalitySnapshot;

/** Startup-only immutable file-backed snapshot. No source text or corpus objects remain in heap. */
public final class PhantomHumanizedV4Catalog implements AutoCloseable
{
	private static final int INDEX_BYTES = 48;
	private static final int MAX_RECORDS = 1_000_000;
	private static final int MAX_LINE = 16 * 1024;
	private static final int MAX_CANDIDATES = 64;
	private static final List<String> FILES = List.of("events.tsv", "records.bin", "registry.tsv", "replies.idx", "triggers.idx", "voices.tsv");
	private static final Set<String> STANCES = Set.of("AMUSED", "NEUTRAL", "OFFENDED", "SET_BOUNDARY", "SUPPORTIVE", "AGGRESSIVE_BANTER", "DISENGAGE", "CURIOUS", "FLIRT", "REFUSE");
	private static final Set<String> TONES = Set.of("CALM", "PLAYFUL", "DRY", "ROUGH", "WARM", "SARCASTIC", "SHY", "FORMAL", "DIRECT");
	private static final Set<String> EVENTS = Set.of("pvp.help.received", "pvp.attack.received", "pvp.kill.caused", "pvp.death.suffered");
	private static final Set<String> LEGACY_VOICES = Set.of("calm", "open", "direct", "empathetic");

	public record Trigger(String id, String category, String act, String intent, String ambiguity, String normalizedHash)
	{
	}

	public record Persona(String key, String legacy, int profanityTolerance, int insultTolerance, int sexualHumorTolerance, int conflictStyle, int trustThreshold, Map<String, Integer> facets)
	{
		public Persona
		{
			facets = new PhantomSpeechFacetProfile(facets).scores();
		}
	}

	private record Category(String act, String intent)
	{
	}

	private record FallbackKey(String mode, String category, String stance, String event, String relation)
	{
	}

	private record Location(long offset, int length)
	{
	}

	private record Entry(String id, String mode, String category, String act, String text, String intent, String ambiguity, String stance, String tone, Set<String> relations, Set<String> voices, Map<String, Integer> facets, String profanity, String maturity, int weight, String event)
	{
	}

	private final List<FileChannel> _channels = new ArrayList<>();
	private final List<FileLock> _locks = new ArrayList<>();
	private final Map<String, Category> _categories = new LinkedHashMap<>();
	private final Map<String, Persona> _voices = new LinkedHashMap<>();
	private final Set<String> _events = new HashSet<>();
	private final Map<String, Set<String>> _eventCategories = new HashMap<>();
	private final Map<FallbackKey, List<Location>> _fallbacks = new HashMap<>();
	private FileChannel _records;
	private ByteBuffer _triggers;
	private ByteBuffer _replies;
	private String _hash;
	private int _recordCount;

	private PhantomHumanizedV4Catalog()
	{
	}

	public static PhantomHumanizedV4Catalog load(Path root)
	{
		final var catalog = new PhantomHumanizedV4Catalog();
		try
		{
			catalog.read(root);
			return catalog;
		}
		catch (IOException | RuntimeException exception)
		{
			catalog.close();
			throw new IllegalArgumentException("Invalid immutable v4 content snapshot.", exception);
		}
	}

	public String hash() { return _hash; }
	public int recordCount() { return _recordCount; }
	public int voiceCount() { return _voices.size(); }

	private void read(Path suppliedRoot) throws IOException
	{
		final Path root = suppliedRoot.toAbsolutePath().normalize();
		plain(root);
		try (var files = Files.list(root))
		{
			final Set<String> names = new HashSet<>();
			for (Path file : files.toList()) { names.add(file.getFileName().toString()); }
			final Set<String> expected = new HashSet<>(FILES); expected.add("manifest.tsv");
			require(names.equals(expected), "Exact seven-file inventory required");
		}
		final FileChannel manifest = open(root.resolve("manifest.tsv"), 8192);
		final byte[] manifestBytes = bytes(manifest, 8192);
		_hash = sha(manifestBytes);
		final String[] lines = utf8(manifestBytes).split("\n", -1);
		require((lines.length == 8) && lines[0].equals("PWV4\t1") && lines[7].isEmpty(), "Manifest version/inventory");
		final Map<String, FileChannel> channels = new HashMap<>();
		for (int i = 0; i < FILES.size(); i++)
		{
			final String[] fields = lines[i + 1].split("\t", -1);
			final String name = FILES.get(i);
			require((fields.length == 4) && fields[0].equals("file") && fields[1].equals(name) && fields[3].matches("[0-9a-f]{64}"), "Manifest entry");
			final long cap = name.equals("records.bin") ? 2L * 1024 * 1024 * 1024 : name.endsWith(".idx") ? 48L * MAX_RECORDS : 4L * 1024 * 1024;
			final FileChannel channel = open(root.resolve(name), cap);
			require(Long.toString(channel.size()).equals(fields[2]) && digest(channel).equals(fields[3]), "Manifest byte/hash mismatch: " + name);
			channels.put(name, channel);
		}
		for (String line : metadata(channels.get("registry.tsv")))
		{
			final String[] fields = line.split("\t", -1);
			require((fields.length == 4) && key(fields[0]) && key(fields[2]) && key(fields[3]) && !fields[1].isBlank(), "Category definition");
			require(_categories.putIfAbsent(fields[0], new Category(fields[2], fields[3])) == null, "Duplicate category");
		}
		require(!_categories.isEmpty() && (_categories.size() <= 16384), "Category bound");
		for (String line : metadata(channels.get("voices.tsv")))
		{
			final String[] f = line.split("\t", -1);
			require((f.length == 9) && key(f[0]) && !f[1].isBlank() && LEGACY_VOICES.contains(f[2]), "Voice definition");
			final Map<String, Integer> facets = parseFacets(f[8]);
			final Persona voice = new Persona(f[0], f[2], score(f[3]), score(f[4]), score(f[5]), score(f[6]), score(f[7]), facets);
			require(_voices.putIfAbsent(f[0], voice) == null, "Duplicate voice");
		}
		require((_voices.size() >= 24) && (_voices.size() <= 1024), "Voice bound");
		for (String line : metadata(channels.get("events.tsv")))
		{
			final String[] fields = line.split("\t", -1);
			require((fields.length == 2) && EVENTS.contains(fields[0]) && !fields[1].isBlank() && _events.add(fields[0]), "Unsupported/duplicate event owner");
		}
		_records = channels.get("records.bin");
		_triggers = index(channels.get("triggers.idx"));
		_replies = index(channels.get("replies.idx"));
		_recordCount = (_triggers.limit() + _replies.limit()) / INDEX_BYTES;
		require((_recordCount > 0) && (_recordCount <= MAX_RECORDS), "Record count bound");
		final long[] offsets = validateRecords();
		final BitSet covered = new BitSet(_recordCount);
		validateIndex(_triggers, true, offsets, covered);
		validateIndex(_replies, false, offsets, covered);
		require(covered.cardinality() == _recordCount, "Index coverage");
	}

	private long[] validateRecords() throws IOException
	{
		final long[] offsets = new long[_recordCount];
		final Set<FallbackKey> requiredFallbacks = new HashSet<>();
		final byte[] line = new byte[MAX_LINE];
		String previous = "";
		long offset = 0;
		int size = 0;
		int count = 0;
		_records.position(0);
		final var input = new BufferedInputStream(Channels.newInputStream(_records), 64 * 1024);
		int next;
		while ((next = input.read()) != -1)
		{
			if (next == '\n')
			{
				require(count < offsets.length, "Unindexed records");
				final Entry entry = entry(Arrays.copyOf(line, size));
				if (!entry.mode().equals("TRIGGER"))
				{
					for (String relation : entry.relations()) { requiredFallbacks.add(new FallbackKey(entry.mode(), entry.category(), entry.stance(), entry.event(), relation)); }
					require(requiredFallbacks.size() <= 32768, "Required fallback metadata bound");
				}
				if (!entry.mode().equals("TRIGGER") && entry.voices().contains("ANY") && entry.facets().isEmpty() && entry.profanity().equals("NONE") && entry.maturity().equals("GENERAL") && !Set.of("ROUGH", "SARCASTIC", "PLAYFUL").contains(entry.tone()))
				{
					for (String relation : entry.relations())
					{
						final FallbackKey key = new FallbackKey(entry.mode(), entry.category(), entry.stance(), entry.event(), relation);
						final List<Location> locations = _fallbacks.computeIfAbsent(key, ignored -> new ArrayList<>());
						// Nine exact offsets guarantee a clean candidate beyond the eight persisted recent hashes.
						if (locations.size() < 9) { locations.add(new Location(offset, size)); }
						require(_fallbacks.size() <= 32768, "Fallback metadata bound");
					}
				}
				if (entry.mode().equals("INITIATIVE"))
				{
					final Set<String> categories = _eventCategories.computeIfAbsent(entry.event(), key -> new java.util.TreeSet<>());
					require(categories.add(entry.category()) || categories.contains(entry.category()), "Event category");
					require(categories.size() <= 64, "Initiative event category bound");
				}
				require(previous.compareTo(entry.id()) < 0, "Records must have unique sorted IDs");
				previous = entry.id();
				offsets[count++] = offset;
				offset += size + 1L; size = 0;
			}
			else
			{
				require(size < MAX_LINE, "Runtime record line cap");
				line[size++] = (byte) next;
			}
		}
		require((size == 0) && (count == _recordCount), "Record LF/count mismatch");
		require(_fallbacks.keySet().containsAll(requiredFallbacks), "Every outbound bucket requires a clean ANY fallback");
		return offsets;
	}

	private void validateIndex(ByteBuffer index, boolean trigger, long[] offsets, BitSet covered) throws IOException
	{
		byte[] previous = null;
		String previousId = "";
		for (int position = 0; position < index.limit(); position += INDEX_BYTES)
		{
			final byte[] digest = keyAt(index, position);
			final long offset = index.getLong(position + 32);
			final int length = index.getInt(position + 40);
			final int ordinal = Arrays.binarySearch(offsets, offset);
			require((ordinal >= 0) && !covered.get(ordinal) && (length > 0) && (length <= MAX_LINE) && (offset + length < _records.size()), "Invalid/duplicate record offset");
			final long end = ordinal + 1 < offsets.length ? offsets[ordinal + 1] : _records.size();
			require(end == offset + length + 1, "Index length mismatch");
			final Entry entry = readEntry(index, position);
			require(trigger == entry.mode().equals("TRIGGER"), "Index mode mismatch");
			final byte[] expected = digest(trigger ? normalize(entry.text()) : entry.mode() + '\t' + entry.category() + '\t' + entry.stance() + '\t' + entry.event());
			require(Arrays.equals(digest, expected) && (entry.weight() == index.getInt(position + 44)), "Index digest/weight mismatch");
			final int comparison = previous == null ? -1 : Arrays.compareUnsigned(previous, digest);
			require((comparison < 0) || ((comparison == 0) && (previousId.compareTo(entry.id()) < 0)), "Unsorted/duplicate index");
			covered.set(ordinal); previous = digest; previousId = entry.id();
		}
	}

	private Entry entry(byte[] bytes) throws IOException
	{
		final String[] f = utf8(bytes).split("\t", -1);
		require((f.length == 17) && f[0].matches("[0-9a-f]{64}") && Set.of("TRIGGER", "REPLY", "INITIATIVE").contains(f[1]), "Runtime entry shape");
		final String[] canonical = f.clone(); canonical[0] = ""; canonical[4] = normalize(f[4]);
		require(f[0].equals(PhantomHumanizedCatalog.sha256("pw-semantic-entry/1\n" + String.join("\t", canonical))), "Forged content ID");
		final Category category = _categories.get(f[2]);
		require((category != null) && category.act().equals(f[3]), "Runtime category/act reference");
		require(!f[4].isBlank() && (f[4].length() <= 1000), "Runtime text length");
		for (int i = 0; i < f[4].length(); i++)
		{
			final char ch = f[4].charAt(i);
			require(!Character.isISOControl(ch) && (Character.getType(ch) != Character.FORMAT), "Unsafe text control");
		}
		final String withoutName = f[4].replace("{name}", "");
		require(!withoutName.contains("{") && !withoutName.contains("}") && !f[4].contains("<") && !f[4].contains(">"), "Unsupported placeholder/markup");
		require(Set.of("", "NONE", "POSSIBLE_JOKE", "POSSIBLE_THREAT").contains(f[6]), "Ambiguity");
		require(Set.of("NONE", "MILD", "STRONG").contains(f[12]) && Set.of("GENERAL", "MATURE").contains(f[13]), "Runtime policy");
		final int weight = integer(f[15], 1, 10);
		final Map<String, Integer> facets = parseFacets(f[11]);
		final Set<String> relations = csv(f[9]);
		final Set<String> voices = csv(f[10]);
		for (String relation : relations) { RelationshipBand.valueOf(relation); }
		for (String voice : voices) { require(voice.equals("ANY") || _voices.containsKey(voice), "Unknown voice reference"); }
		for (String tag : csv(f[14])) { require(key(tag), "Invalid tag"); }
		if (f[1].equals("TRIGGER"))
		{
			require(!f[6].isEmpty() && category.intent().equals(f[5]) && f[7].isEmpty() && f[8].isEmpty() && f[9].isEmpty() && f[10].isEmpty() && f[11].isEmpty() && f[16].isEmpty() && !f[4].contains("{"), "Trigger references/policy");
		}
		else
		{
			require(f[5].isEmpty() && STANCES.contains(f[7]) && TONES.contains(f[8]) && !relations.isEmpty() && !voices.isEmpty(), "Reply references/policy");
			require(f[1].equals("INITIATIVE") ? _events.contains(f[16]) : f[16].isEmpty(), "Initiative event reference");
			final String maximumRendered = f[4].replace("{name}", "界".repeat(64));
			require((maximumRendered.codePointCount(0, maximumRendered.length()) <= 100) && (maximumRendered.getBytes(StandardCharsets.UTF_8).length <= 240), "Native outbound text100-codepoint/240-byte bound");
		}
		return new Entry(f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], relations, voices, facets, f[12], f[13], weight, f[16]);
	}

	public Optional<Trigger> understand(String text)
	{
		final String normalized = normalize(text);
		if (normalized.isEmpty() || (normalized.length() > 1024)) { return Optional.empty(); }
		final byte[] digest = digest(normalized);
		final int position = lowerBound(_triggers, digest);
		if ((position >= _triggers.limit()) || !Arrays.equals(keyAt(_triggers, position), digest)) { return Optional.empty(); }
		try
		{
			final Entry entry = readEntry(_triggers, position);
			if (!normalize(entry.text()).equals(normalized)) { return Optional.empty(); }
			return Optional.of(new Trigger(entry.id(), entry.category(), entry.act(), entry.intent(), entry.ambiguity(), HexFormat.of().formatHex(digest)));
		}
		catch (IOException exception) { throw new IllegalStateException("Verified v4 record unavailable.", exception); }
	}

	public Persona persona(PersonalitySnapshot identity)
	{
		// Existing Social seed/profile own identity. Each dimension has its own salt; roughness is never tolerance.
		final long stable = identity.seed() ^ Long.rotateLeft(identity.ownerProfileId(), 23);
		final List<Persona> voices = List.copyOf(_voices.values());
		final Persona voice = voices.get((int) Math.floorMod(mix(stable), voices.size()));
		final Map<String, Integer> nativeFacets = PhantomSpeechFacetProfile.from(identity).scores();
		final Map<String, Integer> facets = new LinkedHashMap<>();
		for (String key : PhantomSpeechFacetProfile.KEYS) { facets.put(key, (nativeFacets.get(key) * 3 + voice.facets().get(key)) / 4); }
		return new Persona(voice.key(), voice.legacy(), dimension(voice.profanityTolerance(), stable, 11), dimension(voice.insultTolerance(), stable, 23), dimension(voice.sexualHumorTolerance(), stable, 37), dimension(voice.conflictStyle(), stable, 53), dimension(voice.trustThreshold(), stable, 71), facets);
	}

	public String stance(Trigger trigger, Persona persona, RelationshipBand band)
	{
		final boolean familiar = (band == RelationshipBand.FAMILIAR) || (band == RelationshipBand.TRUSTED);
		if (trigger.ambiguity().equals("POSSIBLE_THREAT"))
		{
			if (!familiar) { return "SET_BOUNDARY"; }
			if ((persona.insultTolerance() < 30) || (persona.conflictStyle() > 75)) { return "DISENGAGE"; }
			return persona.insultTolerance() >= 70 ? "NEUTRAL" : "SET_BOUNDARY";
		}
		if (trigger.ambiguity().equals("POSSIBLE_JOKE"))
		{
			if (!familiar) { return "NEUTRAL"; }
			if ((persona.sexualHumorTolerance() < 30) || (persona.insultTolerance() < 30)) { return "OFFENDED"; }
			return (persona.sexualHumorTolerance() >= 65) && (persona.profanityTolerance() >= 65) && (persona.trustThreshold() <= 70) ? "AMUSED" : "NEUTRAL";
		}
		return "NEUTRAL";
	}

	public String stance(Trigger trigger, Persona persona, RelationshipBand band, List<org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.MemorySnapshot> memories, org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef subject, long nowMinute)
	{
		final String selected = stance(trigger, persona, band);
		// Existing Social memories provide transient context; no second mood persistence owner is introduced.
		final boolean recentConflict = memories.stream().anyMatch(memory -> memory.subject().equals(subject) && memory.eventKey().equals("conversation.conflict") && (memory.happenedMinute() <= nowMinute) && (nowMinute - memory.happenedMinute() <= 5) && (memory.expiryMinute() > nowMinute));
		return selected.equals("AMUSED") && recentConflict ? "NEUTRAL" : selected;
	}

	public List<String> initiativeCategories(String event)
	{
		if (!_events.contains(event)) { return List.of(); }
		// Bounded registry lookup is performed only for a real Social memory; no corpus scan.
		return List.copyOf(_eventCategories.getOrDefault(event, Set.of()));
	}

	public String act(String category) { return _categories.get(category).act(); }

	public Optional<Selection> select(String category, String mode, String stance, String event, Persona persona, RelationshipBand band, Register register, ProfanityMode profanity, boolean matureEnabled, boolean whisper, boolean consentingPhantom, String ownerName, long selector, Set<String> recent)
	{
		if (!_categories.containsKey(category) || !STANCES.contains(stance) || (!mode.equals("REPLY") && !mode.equals("INITIATIVE"))) { return Optional.empty(); }
		final byte[] digest = digest(mode + '\t' + category + '\t' + stance + '\t' + event);
		final int start = lowerBound(_replies, digest);
		final int end = upperBound(_replies, digest);
		final int count = (end - start) / INDEX_BYTES;
		if (count == 0) { return Optional.empty(); }
		final int first = (int) Math.floorMod(selector, count);
		final List<Location> fallback = _fallbacks.getOrDefault(new FallbackKey(mode, category, stance, event, band.name()), List.of());
		final int sampled = Math.min(MAX_CANDIDATES, count);
		Entry selected = null;
		long selectedScore = Long.MIN_VALUE;
		try
		{
			for (int attempt = 0; attempt < sampled + fallback.size(); attempt++)
			{
				final Entry candidate = attempt < sampled ? readEntry(_replies, start + ((first + attempt) % count) * INDEX_BYTES) : readEntry(fallback.get(attempt - sampled).offset(), fallback.get(attempt - sampled).length());
				if (!candidate.relations().contains(band.name()) || (!candidate.voices().contains("ANY") && !candidate.voices().contains(persona.key())) || candidate.facets().entrySet().stream().anyMatch(facet -> persona.facets().get(facet.getKey()) < facet.getValue())) { continue; }
				if ((register == Register.NEUTRAL) && Set.of("ROUGH", "SARCASTIC", "PLAYFUL").contains(candidate.tone())) { continue; }
				if ((!candidate.profanity().equals("NONE") && ((profanity == ProfanityMode.NONE) || (!whisper || !consentingPhantom) || ((profanity == ProfanityMode.MILD) && candidate.profanity().equals("STRONG")))) || (candidate.maturity().equals("MATURE") && (!matureEnabled || !whisper || !consentingPhantom))) { continue; }
				if ((candidate.stance().equals("FLIRT") || candidate.stance().equals("AGGRESSIVE_BANTER")) && (!consentingPhantom || !whisper || ((band != RelationshipBand.TRUSTED) && (band != RelationshipBand.FAMILIAR)))) { continue; }
				final String rendered = candidate.text().replace("{name}", safeName(ownerName));
				if ((rendered.codePointCount(0, rendered.length()) > 100) || (rendered.getBytes(StandardCharsets.UTF_8).length > 240)) { continue; }
				if (recent.contains(PhantomHumanizedCatalog.sha256(rendered))) { continue; }
				final long score = Math.floorMod(mix(selector ^ candidate.id().hashCode()), 1_000_000L) * candidate.weight();
				if (score > selectedScore) { selected = candidate; selectedScore = score; }
			}
			if (selected == null) { return Optional.empty(); }
			final String rendered = selected.text().replace("{name}", safeName(ownerName));
			return Optional.of(new Selection("v4." + selected.id(), rendered, PhantomHumanizedCatalog.sha256(rendered), !selected.profanity().equals("NONE"), selected.maturity().equals("MATURE")));
		}
		catch (IOException exception) { throw new IllegalStateException("Verified v4 record unavailable.", exception); }
	}

	private Entry readEntry(ByteBuffer index, int position) throws IOException
	{
		return readEntry(index.getLong(position + 32), index.getInt(position + 40));
	}

	private Entry readEntry(long offset, int length) throws IOException
	{
		final ByteBuffer bytes = ByteBuffer.allocate(length);
		readFully(_records, bytes, offset);
		return entry(bytes.array());
	}

	private static int lowerBound(ByteBuffer index, byte[] key) { return bound(index, key, false); }
	private static int upperBound(ByteBuffer index, byte[] key) { return bound(index, key, true); }
	private static int bound(ByteBuffer index, byte[] key, boolean after)
	{
		int low = 0; int high = index.limit() / INDEX_BYTES;
		while (low < high)
		{
			final int middle = (low + high) >>> 1;
			final int compare = Arrays.compareUnsigned(keyAt(index, middle * INDEX_BYTES), key);
			if ((compare < 0) || (after && (compare == 0))) { low = middle + 1; } else { high = middle; }
		}
		return low * INDEX_BYTES;
	}

	private static byte[] keyAt(ByteBuffer index, int position)
	{
		final byte[] result = new byte[32];
		index.get(position, result);
		return result;
	}

	private FileChannel open(Path path, long maximum) throws IOException
	{
		plain(path);
		require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && (Files.size(path) <= maximum), "File/resource guard");
		final FileChannel channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
		_channels.add(channel);
		// Shared file locks prevent replacement/write on the supported Windows deployment.
		final FileLock lock = channel.tryLock(0, Long.MAX_VALUE, true);
		require(lock != null, "Snapshot already being modified");
		_locks.add(lock);
		return channel;
	}

	private static void plain(Path path) throws IOException
	{
		Path current = path;
		while (current != null) { require(!Files.isSymbolicLink(current), "Linked content path"); current = current.getParent(); }
		require(path.toRealPath().equals(path.toAbsolutePath().normalize()), "Reparse/aliased content path");
	}

	private static ByteBuffer index(FileChannel channel) throws IOException
	{
		require(channel.size() % INDEX_BYTES == 0, "Index alignment");
		return channel.size() == 0 ? ByteBuffer.allocate(0) : channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
	}

	private static List<String> metadata(FileChannel channel) throws IOException
	{
		final String text = utf8(bytes(channel, 4 * 1024 * 1024));
		require(text.isEmpty() || text.endsWith("\n"), "Metadata LF");
		if (text.isEmpty()) { return List.of(); }
		return List.of(text.substring(0, text.length() - 1).split("\n", -1));
	}

	private static byte[] bytes(FileChannel channel, int cap) throws IOException
	{
		require(channel.size() <= cap, "Metadata cap");
		final ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
		readFully(channel, buffer, 0);
		return buffer.array();
	}

	private static void readFully(FileChannel channel, ByteBuffer buffer, long offset) throws IOException
	{
		while (buffer.hasRemaining())
		{
			final int read = channel.read(buffer, offset + buffer.position());
			if (read <= 0) { throw new IOException("Truncated immutable content"); }
		}
	}

	private static String utf8(byte[] bytes) throws IOException
	{
		final String result = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
		require(!result.contains("\uFEFF") && !result.contains("\r") && !result.contains("\u0000"), "Strict UTF-8/LF required");
		return result;
	}

	private static String digest(FileChannel channel) throws IOException
	{
		final MessageDigest digest = sha256();
		final ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
		long offset = 0;
		while (offset < channel.size())
		{
			buffer.clear(); final int read = channel.read(buffer, offset);
			if (read <= 0) { throw new IOException("Truncated hash input"); }
			buffer.flip(); digest.update(buffer); offset += read;
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	public static String normalize(String text)
	{
		return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("(?U)\\s+", " ").strip();
	}

	private static Map<String, Integer> parseFacets(String value)
	{
		final Map<String, Integer> result = new LinkedHashMap<>();
		if (!value.isEmpty())
		{
			for (String pair : value.split(",", -1))
			{
				final String[] fields = pair.split("=", -1);
				require((fields.length == 2) && PhantomSpeechFacetProfile.KEYS.contains(fields[0]) && (result.putIfAbsent(fields[0], score(fields[1])) == null), "Facet reference/duplicate");
			}
		}
		return Map.copyOf(result);
	}

	private static Set<String> csv(String value)
	{
		if (value.isEmpty()) { return Set.of(); }
		final Set<String> result = new HashSet<>();
		for (String part : value.split(",", -1)) { require(!part.isEmpty() && result.add(part), "Empty/duplicate list value"); }
		return Set.copyOf(result);
	}

	private static boolean key(String value) { return value.matches("[a-z][a-z0-9_.-]{0,191}"); }
	private static int score(String value) { return integer(value, 0, 100); }
	private static int integer(String value, int min, int max)
	{
		require(value.matches("0|[1-9][0-9]{0,9}"), "Integer format");
		final int result = Integer.parseInt(value); require((result >= min) && (result <= max), "Integer bound"); return result;
	}
	private static int dimension(int prior, long identity, long salt) { return (int) Math.max(0, Math.min(100, prior + Math.floorMod(mix(identity ^ salt), 41) - 20)); }
	private static long mix(long value) { value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L; value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL; return value ^ (value >>> 31); }
	private static String safeName(String value) { return value != null && value.matches("[\\p{L}\\p{N}_-]{1,64}") ? value : "Фантом"; }
	private static byte[] digest(String text) { return sha256().digest(text.getBytes(StandardCharsets.UTF_8)); }
	private static String sha(byte[] bytes) { return HexFormat.of().formatHex(sha256().digest(bytes)); }
	private static MessageDigest sha256() { try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
	private static void require(boolean condition, String message) { if (!condition) { throw new IllegalArgumentException(message); } }

	@Override
	public void close()
	{
		for (FileLock lock : _locks) { try { lock.close(); } catch (IOException ignored) { } }
		for (FileChannel channel : _channels) { try { channel.close(); } catch (IOException ignored) { } }
		_locks.clear(); _channels.clear();
	}
}

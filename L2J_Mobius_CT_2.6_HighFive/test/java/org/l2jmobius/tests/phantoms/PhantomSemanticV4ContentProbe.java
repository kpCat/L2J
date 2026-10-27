/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Variation;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService.Request;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedV4Catalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedV4Catalog.Persona;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomSpeechFacetProfile;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialReceiptLedger;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomPvpSocialBridge;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialAffiliationContextPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.activity.PhantomCompositeSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort;

/** DB-free driver: production catalog, service and social owner; recording persistence ports only. */
public final class PhantomSemanticV4ContentProbe
{
	private static final String CRUDE = "куплю валакаса за целку";
	private static final String THREAT = "в риле бы тебе ебало разбил";
	private static final String HASH = "1".repeat(64);
	private static int _checks;

	public static void main(String[] args) throws Exception
	{
		final Path data = Path.of(args[0]);
		final Path scratch = Path.of(args[1]);
		if ((args.length > 3) && args[2].equals("--scale")) { performance(Path.of(args[3])); return; }
		if ((args.length > 5) && args[2].equals("--roundtrip")) { roundtrip(data, Path.of(args[3]), args[4], args[5]); return; }
		final Path stage = fixture(scratch.resolve("fixture"));
		composition(data);
		final Path config = scratch.resolve("PhantomPlayers.ini");
		final String configBase = "EnablePhantomSystem=True\nMaxMaterializedPhantoms=32\nMaxScheduledPhantomProfiles=10000\nPhantomSchedulerPulseMillis=100\nPhantomSchedulerProfilesPerPulse=128\n";
		Files.writeString(config, configBase);
		check(PhantomPlayersConfig.read(config).enabled() && !PhantomPlayersConfig.read(config).conversation().semanticV4Enabled(), "old owner config absent flag defaults off");
		Files.writeString(config, configBase + "EnablePhantomSemanticV4=True\n");
		check(PhantomPlayersConfig.read(config).conversation().semanticV4Enabled(), "explicit owner opt-in");
		Files.writeString(config, configBase + "EnablePhantomSemanticV4=invalid\n");
		check(PhantomPlayersConfig.read(config).enabled() && !PhantomPlayersConfig.read(config).conversation().semanticV4Enabled(), "invalid opt-in only disables v4");
		final PhantomHumanizedCatalog legacy = PhantomHumanizedCatalog.loadV3(data, true);
		try (var v4 = PhantomHumanizedV4Catalog.load(stage))
		{
			check(v4.voiceCount() >= 24, "registry voices");
			final var crude = v4.understand(CRUDE).orElseThrow();
			final var threat = v4.understand(THREAT).orElseThrow();
			final Persona tolerant = new Persona("voice00", "calm", 95, 95, 95, 10, 10, facets(50));
			final Persona neutral = new Persona("voice01", "calm", 50, 50, 50, 50, 50, facets(50));
			final Persona offended = new Persona("voice02", "calm", 5, 5, 5, 95, 95, facets(50));
			check(!v4.stance(crude, tolerant, RelationshipBand.TRUSTED).equals(v4.stance(crude, offended, RelationshipBand.TRUSTED)), "crude joke boundaries differ");
			check(!v4.stance(threat, tolerant, RelationshipBand.TRUSTED).equals(v4.stance(threat, offended, RelationshipBand.TRUSTED)), "threat boundaries differ");
			check(v4.stance(threat, neutral, RelationshipBand.UNKNOWN).equals("SET_BOUNDARY"), "unknown human threat conservative");
			check(!v4.stance(crude, tolerant, RelationshipBand.UNKNOWN).equals("AMUSED"), "unknown consent conservative");
			final var conflict = new PhantomSocialModel.MemorySnapshot("3".repeat(64), "conversation.conflict", SubjectRef.character(42), 100, 200, 1000, 1000, HASH);
			check(v4.stance(crude, tolerant, RelationshipBand.TRUSTED, List.of(conflict), SubjectRef.character(42), 100).equals("NEUTRAL"), "recent native Social conflict changes temporary reaction");
			check(v4.stance(crude, tolerant, RelationshipBand.TRUSTED, List.of(conflict), SubjectRef.character(42), 106).equals("AMUSED"), "temporary reaction context expires");
			final var selected = v4.select(crude.category(), "REPLY", "AMUSED", "", tolerant, RelationshipBand.TRUSTED, Register.CASUAL, ProfanityMode.CONTEXTUAL, false, false, false, "Фантом", 1, Set.of()).orElseThrow();
			check(v4.select(crude.category(), "REPLY", "AMUSED", "", tolerant, RelationshipBand.TRUSTED, Register.CASUAL, ProfanityMode.CONTEXTUAL, false, false, false, "Фантом", 1, Set.of(selected.responseHash())).isEmpty(), "anti-repeat");
			for (int selector = 0; selector < 130; selector++)
			{
				check(v4.select("hybrid.sparse", "REPLY", "NEUTRAL", "", tolerant, RelationshipBand.UNKNOWN, Register.NEUTRAL, ProfanityMode.NONE, false, false, false, "Фантом", selector, Set.of()).isPresent(), "sparse clean ANY fallback beyond bounded sampling");
			}
			final Map<Long, PhantomSocialService.StoredState> socialRows = new HashMap<>();
			final PhantomSocialService social = new PhantomSocialService(PhantomSocialCatalog.load(data.resolve("social/high-five-social-v1.xml")), new PhantomSocialService.PersistencePort()
			{
				public boolean profileExists(long id) { return true; }
				public Optional<PhantomSocialService.StoredState> load(long id) { return Optional.ofNullable(socialRows.get(id)); }
				public PhantomSocialService.StoredState save(long id, long row, long receipts, PhantomSocialModel.SocialState state, PhantomSocialReceiptLedger ledger)
				{
					final var saved = new PhantomSocialService.StoredState(id, row + 1, receipts + 1, state, ledger);
					socialRows.put(id, saved);
					return saved;
				}
			}, 95001, 32, () -> 100L);
			check(social.start(), "social owner started");
			final Map<Long, PhantomPersonalConversationStore.StoredState> rows = new HashMap<>();
			final var personal = new PhantomPersonalConversationStore(new PhantomPersonalConversationStore.PersistencePort()
			{
				public Optional<PhantomPersonalConversationStore.StoredState> load(long id) { return Optional.ofNullable(rows.get(id)); }
				public PhantomPersonalConversationStore.StoredState save(long id, long row, PhantomPersonalConversationStore.PersonalState state)
				{
					final var saved = new PhantomPersonalConversationStore.StoredState(id, row + 1, state);
					rows.put(id, saved);
					return saved;
				}
			});
			final var settings = new PhantomHumanizedConversationService.Settings(true, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false);
			final var service = new PhantomHumanizedConversationService(legacy, personal, social, settings, v4);
			final Request request = request(CRUDE, 100);
			final var decision = service.plan(request);
			check(decision.eligible() && !decision.suppressed() && decision.templateId().startsWith("v4."), "actual Humanized production call selects v4");
			executionPrepared(decision);
			check(rows.containsKey(1L), "existing personal writer used");
			service.plan(request("привет", 100));
			check(service.plan(request).suppressed(), "interleaved v3/v4 anti-repeat survives authority changes");
			check(service.requestedSupport(request("бафни меня", 100)).isPresent(), "functional v3 support preserved");
			check(!service.plan(request("неизвестная строка без фактов", 100)).eligible(), "unknown no match");
			final var before = v4.persona(social.snapshot(1, SubjectRef.character(42), 4, 100).value().personality());
			check(before.equals(v4.persona(social.snapshot(1, SubjectRef.character(42), 4, 100).value().personality())), "stable persona identity");
			final var oldService = new PhantomHumanizedConversationService(legacy, personal, social, settings);
			check(!oldService.plan(request).eligible(), "disabled v4 legacy parity");
			check(!service.plan(request("событие pvp.help.received", 100)).eligible(), "text cannot fabricate event");
			final List<PhantomHumanizedConversationService.Decision> outbound = new ArrayList<>();
			final var minute = new java.util.concurrent.atomic.AtomicLong(100);
			service.initiatives(new PhantomHumanizedConversationService.InitiativePort()
			{
				public Optional<PhantomHumanizedConversationService.InitiativeContext> context(SocialEvent event) { return Optional.of(new PhantomHumanizedConversationService.InitiativeContext("Фантом", PhantomHumanizedCatalog.RuntimeIdentity.unavailable(1, "Фантом"), minute.get())); }
				public boolean submit(SocialEvent event, PhantomHumanizedConversationService.Decision decision) { executionPrepared(decision); outbound.add(decision); return true; }
			});
			final var events = new PhantomPvpSocialBridge(social, PhantomSocialAffiliationContextPort.noop(), service::onSocialEvent);
			check(events.record(1, SubjectRef.character(42), PhantomPvpSocialBridge.EventKind.HELP_RECEIVED, "help-one", HASH, 100, 1000).durable(), "real social event recorded");
			service.onPulse();
			check(outbound.size() == 1 && outbound.getFirst().act().equals("social.help.reply"), "real event starts initiative without incoming chat");
			service.onPulse();
			check(outbound.size() == 1, "event queue does not spam on empty pulses");
			events.record(1, SubjectRef.character(42), PhantomPvpSocialBridge.EventKind.HELP_RECEIVED, "help-one", HASH, 100, 1000);
			service.onPulse();
			check(outbound.size() == 1, "durable event idempotence");
			check(!service.plan(request("новое неизвестное сообщение", 100)).eligible(), "unknown chat cannot trigger initiative");
			minute.set(102);
			events.record(1, SubjectRef.character(42), PhantomPvpSocialBridge.EventKind.HELP_RECEIVED, "help-two", HASH, 100, 1000);
			service.onPulse();
			check(outbound.size() == 1, "stale event not initiative");
			final var failingContent = new PhantomPvpSocialBridge(social, PhantomSocialAffiliationContextPort.noop(), event -> { throw new IllegalStateException("recording content port failed"); });
			check(failingContent.record(1, SubjectRef.character(42), PhantomPvpSocialBridge.EventKind.HELP_RECEIVED, "help-content-failure", HASH, 100, 1000).durable(), "content failure cannot change durable native social event");
			check(failingContent.contentCallbackFailures() == 1, "downstream content failure counted");
			check(service.stopInitiatives(), "initiative owner stops after pulse drain");
			events.record(1, SubjectRef.character(42), PhantomPvpSocialBridge.EventKind.HELP_RECEIVED, "help-after-stop", HASH, 102, 1000);
			service.onPulse();
			check(outbound.size() == 1, "stopped event owner admits no new initiative");
		}
		final Path corrupt = scratch.resolve("corrupt");
		Files.createDirectories(corrupt);
		try (var files = Files.list(stage)) { for (Path path : files.toList()) { Files.copy(path, corrupt.resolve(path.getFileName())); } }
		Files.writeString(corrupt.resolve("records.bin"), "bad", StandardCharsets.UTF_8);
		boolean rejected = false;
		try (var ignored = PhantomHumanizedV4Catalog.load(corrupt)) { }
		catch (IllegalArgumentException expected) { rejected = true; }
		check(rejected, "corrupt snapshot fails atomically");
		final Path falseId = scratch.resolve("false-id");
		Files.createDirectories(falseId);
		try (var files = Files.list(stage)) { for (Path path : files.toList()) { Files.copy(path, falseId.resolve(path.getFileName())); } }
		final String records = Files.readString(falseId.resolve("records.bin"));
		Files.writeString(falseId.resolve("records.bin"), "0".repeat(64) + records.substring(64));
		manifest(falseId);
		rejected = false;
		try (var ignored = PhantomHumanizedV4Catalog.load(falseId)) { }
		catch (IllegalArgumentException expected) { rejected = true; }
		check(rejected, "rehashed manifest cannot authorize a forged content ID");
		final Path tooManyBytes = fixture(scratch.resolve("too-many-bytes"), true);
		rejected = false;
		try (var ignored = PhantomHumanizedV4Catalog.load(tooManyBytes)) { }
		catch (IllegalArgumentException expected) { rejected = true; }
		check(rejected, "outgoing61 emoji244 bytes exceeds existing native execution240-byte guard");
		final var suite = new PhantomPost002SemanticV3Suite(PhantomPost002SemanticV3Suite.Mode.CONTENT);
		final var registry = new PhantomTestRegistry(suite.id());
		final var context = new PhantomTestContext(95001, data.getParent().getParent().getParent().getParent(), scratch);
		suite.beforeAll(context); suite.register(registry);
		for (var test : registry.orderedTests()) { test.testCase().run(context); System.out.println("LEGACY_PASS " + test.identity()); }
		suite.afterAll(context);
		if (args.length > 2)
		{
			try (var compiled = PhantomHumanizedV4Catalog.load(Path.of(args[2])))
			{
				check(compiled.voiceCount() >= 24, "C# compiled wire accepted");
				System.out.println("COMPILED_STAGE_PASS hash=" + compiled.hash() + " records=" + compiled.recordCount());
			}
		}
		System.out.println("JAVA_CONSUMER_PASS checks=" + _checks);
	}

	private static void roundtrip(Path data, Path stage, String text, String expectedCategory) throws Exception
	{
		try (var compiled = PhantomHumanizedV4Catalog.load(stage))
		{
			final var trigger = compiled.understand(text).orElseThrow();
			check(trigger.category().equals(expectedCategory), "roundtrip category");
			final var legacy = PhantomHumanizedCatalog.loadV3(data, true);
			final var service = new PhantomHumanizedConversationService(legacy, new PhantomPersonalConversationStore(new PhantomPersonalConversationStore.PersistencePort()
			{
				private PhantomPersonalConversationStore.StoredState _row;
				public Optional<PhantomPersonalConversationStore.StoredState> load(long id) { return Optional.ofNullable(_row); }
				public PhantomPersonalConversationStore.StoredState save(long id, long version, PhantomPersonalConversationStore.PersonalState state) { _row = new PhantomPersonalConversationStore.StoredState(id, version + 1, state); return _row; }
			}), social(data), new PhantomHumanizedConversationService.Settings(true, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false), compiled);
			final var decision = service.plan(request(text, 100));
			check(decision.eligible() && !decision.suppressed() && decision.topic().equals(expectedCategory) && decision.templateId().startsWith("v4."), "actual production service consumes C# compiled stage: " + decision);
			executionPrepared(decision);
			System.out.println("ROUNDTRIP_JAVA_CONSUMER_PASS category=" + decision.topic() + " manifest=" + compiled.hash() + " template=" + decision.templateId());
		}
	}

	private static PhantomSocialService social(Path data)
	{
		final Map<Long, PhantomSocialService.StoredState> rows = new HashMap<>();
		final var social = new PhantomSocialService(PhantomSocialCatalog.load(data.resolve("social/high-five-social-v1.xml")), new PhantomSocialService.PersistencePort()
		{
			public boolean profileExists(long id) { return true; }
			public Optional<PhantomSocialService.StoredState> load(long id) { return Optional.ofNullable(rows.get(id)); }
			public PhantomSocialService.StoredState save(long id, long version, long receipts, PhantomSocialModel.SocialState state, PhantomSocialReceiptLedger ledger)
			{
				final var row = new PhantomSocialService.StoredState(id, version + 1, receipts + 1, state, ledger); rows.put(id, row); return row;
			}
		}, 95001, 32, () -> 100L);
		check(social.start(), "roundtrip social started");
		return social;
	}

	private static void performance(Path stage) throws Exception
	{
		final long started = System.nanoTime();
		try (var compiled = PhantomHumanizedV4Catalog.load(stage))
		{
			final long startup = System.nanoTime() - started;
			final String text = "Проверка масштабирования.";
			final var trigger = compiled.understand(text).orElseThrow();
			final Persona neutral = new Persona("calm", "calm", 50, 50, 50, 50, 50, facets(50));
			final long[] timings = new long[1000];
			final var cpu = java.lang.management.ManagementFactory.getThreadMXBean();
			final long cpuStart = cpu.isCurrentThreadCpuTimeSupported() ? cpu.getCurrentThreadCpuTime() : 0;
			for (int i = -100; i < timings.length; i++)
			{
				final long before = System.nanoTime();
				final var match = compiled.understand(text).orElseThrow();
				final var selected = compiled.select(match.category(), "REPLY", "NEUTRAL", "", neutral, RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, false, false, false, "Фантом", i, Set.of());
				check(selected.isPresent(), "scale selection");
				if (i >= 0) { timings[i] = System.nanoTime() - before; }
			}
			final long cpuNanos = cpu.isCurrentThreadCpuTimeSupported() ? cpu.getCurrentThreadCpuTime() - cpuStart : -1;
			java.util.Arrays.sort(timings);
			final long heap = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
			final long indexBytes = Files.size(stage.resolve("triggers.idx")) + Files.size(stage.resolve("replies.idx"));
			final long recordsBytes = Files.size(stage.resolve("records.bin"));
			System.out.println("JAVA_SCALE_PASS records=" + compiled.recordCount() + " startupMs=" + startup / 1_000_000.0 + " heapBytes=" + heap + " recordsBytes=" + recordsBytes + " indexBytes=" + indexBytes + " warmP50Us=" + timings[499] / 1000.0 + " warmP95Us=" + timings[949] / 1000.0 + " selectionCpuMs=" + cpuNanos / 1_000_000.0 + " category=" + trigger.category());
		}
	}

	private static Request request(String text, long minute)
	{
		return new Request(1, "Фантом", SubjectRef.character(42), Origin.CLIENT_CHAT, ChatType.GENERAL, text, hash(text), minute);
	}

	private static Map<String, Integer> facets(int value)
	{
		final Map<String, Integer> result = new HashMap<>();
		for (String facet : PhantomSpeechFacetProfile.KEYS) { result.put(facet, value); }
		return result;
	}

	private static Path fixture(Path root) throws Exception
	{
		return fixture(root, false);
	}

	private static Path fixture(Path root, boolean tooManyBytes) throws Exception
	{
		Files.createDirectories(root);
		Files.writeString(root.resolve("registry.tsv"), "hybrid.joke\tШутка\thybrid.joke.reply\thybrid.joke.intent\nhybrid.sparse\tРедкий ответ\thybrid.sparse.reply\thybrid.sparse.intent\nsocial.help\tПомощь\tsocial.help.reply\tsocial.help.intent\nsocial.threat\tУгроза\tsocial.threat.reply\tsocial.threat.intent\n", StandardCharsets.UTF_8);
		final String facets = String.join(",", PhantomSpeechFacetProfile.KEYS.stream().sorted().map(key -> key + "=50").toList());
		final StringBuilder voices = new StringBuilder();
		for (int i = 0; i < 24; i++) { voices.append(String.format(java.util.Locale.ROOT, "voice%02d\tГолос %d\tcalm\t50\t50\t50\t50\t50\t%s\n", i, i, facets)); }
		Files.writeString(root.resolve("voices.tsv"), voices, StandardCharsets.UTF_8);
		Files.writeString(root.resolve("events.tsv"), "pvp.help.received\tПолучена помощь\n", StandardCharsets.UTF_8);
		final List<String[]> records = new ArrayList<>();
		records.add(record("TRIGGER", "hybrid.joke", CRUDE, "POSSIBLE_JOKE", "", ""));
		records.add(record("TRIGGER", "social.threat", THREAT, "POSSIBLE_THREAT", "", ""));
		for (String stance : List.of("AMUSED", "NEUTRAL", "OFFENDED", "SET_BOUNDARY", "DISENGAGE"))
		{
			records.add(record("REPLY", "hybrid.joke", "Ответ на шутку " + stance, "NONE", stance, ""));
			records.add(record("REPLY", "social.threat", "Ответ на угрозу " + stance, "NONE", stance, ""));
		}
		records.add(record("INITIATIVE", "social.help", "Спасибо за помощь.", "NONE", "SUPPORTIVE", "pvp.help.received"));
		for (int i = 0; i < 129; i++)
		{
			final String[] fields = record("REPLY", "hybrid.sparse", "Чужой голос номер " + i, "NONE", "NEUTRAL", "");
			fields[10] = "voice01"; fields[0] = contentId(fields); records.add(fields);
		}
		records.add(record("REPLY", "hybrid.sparse", "Универсальный редкий ответ.", "NONE", "NEUTRAL", ""));
		if (tooManyBytes) { records.add(record("REPLY", "hybrid.joke", "😀".repeat(61), "NONE", "NEUTRAL", "")); }
		records.sort(Comparator.comparing(fields -> fields[0]));
		final var data = new java.io.ByteArrayOutputStream();
		final List<byte[]> triggers = new ArrayList<>();
		final List<byte[]> replies = new ArrayList<>();
		for (String[] fields : records)
		{
			final byte[] bytes = String.join("\t", fields).getBytes(StandardCharsets.UTF_8);
			final byte[] digest = HexFormat.of().parseHex(hash(fields[1].equals("TRIGGER") ? PhantomHumanizedV4Catalog.normalize(fields[4]) : fields[1] + '\t' + fields[2] + '\t' + fields[7] + '\t' + fields[16]));
			final byte[] index = ByteBuffer.allocate(48).put(digest).putLong(data.size()).putInt(bytes.length).putInt(1).array();
			(fields[1].equals("TRIGGER") ? triggers : replies).add(index);
			data.write(bytes); data.write('\n');
		}
		Files.write(root.resolve("records.bin"), data.toByteArray());
		for (var pair : Map.of("triggers.idx", triggers, "replies.idx", replies).entrySet())
		{
			pair.getValue().sort((a, b) -> java.util.Arrays.compareUnsigned(a, 0, 32, b, 0, 32));
			final var index = new java.io.ByteArrayOutputStream();
			for (byte[] entry : pair.getValue()) { index.write(entry); }
			Files.write(root.resolve(pair.getKey()), index.toByteArray());
		}
		manifest(root);
		return root;
	}

	private static String[] record(String mode, String category, String text, String ambiguity, String stance, String event)
	{
		final String[] fields = new String[] { "", mode, category, category + ".reply", PhantomHumanizedV4Catalog.normalize(text), mode.equals("TRIGGER") ? category + ".intent" : "", ambiguity, stance, mode.equals("TRIGGER") ? "" : "CALM", mode.equals("TRIGGER") ? "" : "UNKNOWN,NEUTRAL,FAMILIAR,TRUSTED,RIVAL,TENSE,HOSTILE", mode.equals("TRIGGER") ? "" : "ANY", "", "NONE", "GENERAL", "", "1", event };
		fields[0] = contentId(fields);
		fields[4] = text;
		return fields;
	}

	private static String contentId(String[] source)
	{
		final String[] fields = source.clone(); fields[0] = ""; fields[4] = PhantomHumanizedV4Catalog.normalize(fields[4]);
		return hash("pw-semantic-entry/1\n" + String.join("\t", fields));
	}

	private static void manifest(Path root) throws Exception
	{
		final StringBuilder result = new StringBuilder("PWV4\t1\n");
		try (var files = Files.list(root))
		{
			for (Path file : files.filter(path -> !path.getFileName().toString().equals("manifest.tsv")).sorted().toList())
			{
				result.append("file\t").append(file.getFileName()).append('\t').append(Files.size(file)).append('\t').append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))).append('\n');
			}
		}
		Files.writeString(root.resolve("manifest.tsv"), result, StandardCharsets.UTF_8);
	}

	private static String hash(String value)
	{
		return PhantomHumanizedCatalog.sha256(value);
	}

	private static void executionPrepared(PhantomHumanizedConversationService.Decision decision)
	{
		final String hash = PhantomConversationModel.sha256("v4.downstream.contract");
		final var plan = new PhantomConversationModel.ConversationResponsePlan(1, 1, hash, ChatType.WHISPER, new PhantomConversationModel.ConversationSubject(new PhantomDomainRef("character.object", "42")), hash, "social.reply", "neutral", decision.text(), null, PhantomConversationModel.DeliveryPolicy.SEND, 101, List.of());
		final var entry = PhantomConversationExecutionModel.ExecutionEntry.prepared(plan);
		check(entry.actionState() == PhantomConversationExecutionModel.ActionState.NONE && entry.proposalKey() == null, "real execution handoff contains no native action");
	}

	private static void composition(Path data) throws Exception
	{
		final Path source = data.getParent().getParent().getParent().getParent().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java");
		final var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
		final var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
		try (var files = compiler.getStandardFileManager(diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8))
		{
			final var task = (com.sun.source.util.JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, files.getJavaFileObjects(source));
			task.parse();
			check(diagnostics.getDiagnostics().stream().noneMatch(item -> item.getKind() == javax.tools.Diagnostic.Kind.ERROR), "real composition source parses");
		}
		final String text = Files.readString(source);
		check(text.contains("journeyControls.add(humanizedConversation)") && !text.contains("controlPorts.add(humanizedConversation)"), "v4 uses bounded nested shared control route");
		final List<PhantomSchedulerControlPort> outer = new ArrayList<>();
		for (int i = 0; i < 5; i++) { outer.add(PhantomSchedulerControlPort.noop()); }
		outer.add(new PhantomCompositeSchedulerControlPort(java.util.Collections.nCopies(5, PhantomSchedulerControlPort.noop())));
		outer.add(PhantomSchedulerControlPort.noop()); outer.add(PhantomSchedulerControlPort.noop());
		final var maximum = new PhantomCompositeSchedulerControlPort(outer);
		maximum.onPulse();
		check(maximum.snapshot().stages() == 8 && maximum.snapshot().stageFailures() == 0, "all optional shared owners fit existing eight-stage cap");
	}

	private static void check(boolean value, String message)
	{
		if (!value) { throw new AssertionError(message); }
		_checks++;
	}
}

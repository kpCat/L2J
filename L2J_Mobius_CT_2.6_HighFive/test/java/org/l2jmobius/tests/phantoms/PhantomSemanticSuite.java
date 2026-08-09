/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding.Authority;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding.Hashes;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.EvidenceQuality;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputChannel;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputContext;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.PlayerReference;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingResult;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingStatus;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticNormalizer;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack.CorpusCase;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;

public final class PhantomSemanticSuite implements PhantomTestSuite
{
	public enum Mode
	{
		PACK,
		NORMALIZATION,
		INTENTS,
		GROUNDING,
		CONTEXT,
		CORPUS,
		LIFECYCLE_PERFORMANCE
	}

	private static final long SEED = 19001901L;
	private static final Hashes HASHES = new Hashes("A".repeat(64), "B".repeat(64), "C".repeat(64));
	private final Mode _mode;
	private Path _temporaryDirectory;

	public PhantomSemanticSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "semantic-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 019 focused mode used the wrong deterministic seed.");
		_temporaryDirectory = context.moduleRoot().resolve(".phantom-local/semantic-" + _mode.name().toLowerCase(java.util.Locale.ROOT) + '-' + ProcessHandle.current().pid());
		Files.createDirectories(_temporaryDirectory);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if ((_temporaryDirectory != null) && Files.exists(_temporaryDirectory))
		{
			try (var paths = Files.walk(_temporaryDirectory))
			{
				for (Path path : paths.sorted((left, right) -> right.compareTo(left)).toList())
				{
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case PACK -> packTests(registry);
			case NORMALIZATION -> normalizationTests(registry);
			case INTENTS -> intentTests(registry);
			case GROUNDING -> groundingTests(registry);
			case CONTEXT -> contextTests(registry);
			case CORPUS -> corpusTests(registry);
			case LIFECYCLE_PERFORMANCE -> lifecyclePerformanceTests(registry);
		}
	}

	private void packTests(PhantomTestRegistry registry)
	{
		registry.add("01-current-xml-tsv-are-strict-hashed-and-bounded", context ->
		{
			final PhantomSemanticPack first = pack(context);
			final PhantomSemanticPack second = pack(context);
			PhantomAssertions.assertEquals(first.packHash(), second.packHash(), "Semantic XML hash changed across identical loads.");
			PhantomAssertions.assertEquals(first.corpusHash(), second.corpusHash(), "Semantic corpus hash changed across identical loads.");
			PhantomAssertions.assertEquals(64, first.packHash().length(), "Semantic XML is not SHA-256 addressed.");
			PhantomAssertions.assertEquals(242, first.corpus().size(), "Semantic corpus case count changed.");
			PhantomAssertions.assertEquals(15, first.intents().size(), "Semantic intent coverage changed.");
		});
		registry.add("02-required-contract-and-coverage-are-explicit", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			for (String intent : List.of("party.invite", "party.accept", "party.refuse", "party.leave", "party.role.query", "party.travel", "party.support.request", "party.assist.request", "party.regroup.request", "entity.locate", "item.acquire.query", "item.source.query", "content.requirements.query", "farming.conflict.query", "unknown"))
			{
				PhantomAssertions.assertEquals(intent, pack.intent(intent).key(), "Required semantic intent is absent.");
			}
			PhantomAssertions.assertTrue(pack.patternCount() <= 128, "Semantic pattern count exceeds the declared bound.");
			PhantomAssertions.assertTrue(pack.entityAliasCount() <= 128, "Semantic entity-alias count exceeds the declared bound.");
			PhantomAssertions.assertEquals(HASHES, pack.authorityHashes(), "Semantic pack did not pin authority hashes.");
			context.record("semantic.packHash", pack.packHash());
			context.record("semantic.corpusHash", pack.corpusHash());
			context.record("semantic.corpusCases", pack.corpus().size());
		});
		registry.add("03-xxe-structure-limit-and-tsv-controls-fail-closed", context ->
		{
			final String xml = Files.readString(xmlPath(context), StandardCharsets.UTF_8);
			rejectPack(context, "xxe", xml.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE semanticPack [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"), corpusPath(context), authority());
			rejectPack(context, "unknown-attribute", xml.replace(" locale=\"ru\"", " locale=\"ru\" unexpected=\"true\""), corpusPath(context), authority());
			rejectPack(context, "candidate-limit", xml.replace("maxCandidates=\"128\"", "maxCandidates=\"129\""), corpusPath(context), authority());
			rejectPack(context, "duplicate-alias", xml.replace("<alias slot=\"ITEM\" phrase=\"адена\"", "<alias slot=\"ITEM\" phrase=\"адены\""), corpusPath(context), authority());
			final Path badHeader = _temporaryDirectory.resolve("bad-header.tsv");
			Files.writeString(badHeader, Files.readString(corpusPath(context), StandardCharsets.UTF_8).replaceFirst("case_id", "case"), StandardCharsets.UTF_8);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSemanticPack.load(xmlPath(context), badHeader, authority()), "Non-exact semantic TSV header was accepted.");
			final Path malformed = _temporaryDirectory.resolve("malformed.tsv");
			Files.write(malformed, new byte[]
			{
				(byte) 0xc3,
				(byte) 0x28
			});
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSemanticPack.load(xmlPath(context), malformed, authority()), "Malformed UTF-8 semantic corpus was accepted.");
		});
	}

	private void normalizationTests(PhantomTestRegistry registry)
	{
		registry.add("01-nfkc-root-case-yo-whitespace-punctuation-and-spans", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final String decomposed = Normalizer.normalize("ПРИГЛАСИ ЁГО", Normalizer.Form.NFD);
			final var normalized = PhantomSemanticNormalizer.normalize("  " + decomposed + " — Иван!  ", pack);
			PhantomAssertions.assertEquals("пригласи его - иван !", normalized.value(), "Russian NFKC/case/yo/punctuation normalization changed.");
			PhantomAssertions.assertEquals("пригласи", normalized.tokens().getFirst().canonicalValue(), "First normalized token changed.");
			PhantomAssertions.assertTrue(normalized.tokens().getFirst().originalStartCodePoint() >= 2, "Original code-point span was not preserved.");
			final var second = PhantomSemanticNormalizer.normalize(normalized.value(), pack);
			PhantomAssertions.assertEquals(normalized.value(), second.value(), "Semantic normalization is not idempotent.");
			PhantomAssertions.assertEquals(normalized.normalizedHash(), second.normalizedHash(), "Idempotent normalized hash changed.");
		});
		registry.add("02-transliteration-abbreviation-repeat-and-token-bounds", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final var aliases = PhantomSemanticNormalizer.normalize("PLZ INV Ивааан", pack);
			PhantomAssertions.assertEquals("пожалуйста", aliases.tokens().get(0).canonicalValue(), "Transliteration filler alias was not explicit.");
			PhantomAssertions.assertEquals(EvidenceQuality.ABBREVIATION, aliases.tokens().get(1).lexicalQuality(), "Abbreviation evidence quality was lost.");
			PhantomAssertions.assertEquals("иваан", aliases.tokens().get(2).value(), "Bounded repeated-code-point normalization changed.");
			final String tooManyTokens = String.join(" ", java.util.Collections.nCopies(65, "слово"));
			PhantomAssertions.assertThrows(PhantomSemanticNormalizer.Rejection.class, () -> PhantomSemanticNormalizer.normalize(tooManyTokens, pack), "A 65-token input was accepted.");
		});
		registry.add("03-malformed-control-oversized-mixed-and-script-controls-reject", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final String malformed = new String(new char[]
			{
				Character.MIN_HIGH_SURROGATE
			});
			assertNormalizationReason(pack, malformed, "reject.unsupported");
			assertNormalizationReason(pack, "пригласи\u0000Иван", "reject.unsupported");
			assertNormalizationReason(pack, "а".repeat(513), "reject.too_long");
			assertNormalizationReason(pack, "приглaси", "reject.mixed_script");
			assertNormalizationReason(pack, "東京", "reject.unsupported");
		});
	}

	private void intentTests(PhantomTestRegistry registry)
	{
		registry.add("01-exact-transliteration-fuzzy-precedence-and-boundaries", context ->
		{
			final ServiceFixture fixture = service(context);
			try
			{
				final InputContext player = contextFixture("player-ivan");
				final UnderstandingResult exact = fixture.service().understand("пригласи Иван", player);
				final UnderstandingResult translit = fixture.service().understand("priglasi Иван", player);
				final UnderstandingResult fuzzy = fixture.service().understand("приглси Иван", player);
				PhantomAssertions.assertEquals(UnderstandingStatus.ACCEPTED, exact.status(), "Exact intent was not accepted.");
				PhantomAssertions.assertTrue(exact.confidence() > translit.confidence(), "Exact intent did not outrank transliteration.");
				PhantomAssertions.assertTrue(translit.confidence() > fuzzy.confidence(), "Transliteration did not outrank fuzzy evidence.");
				PhantomAssertions.assertEquals(UnderstandingStatus.REJECTED, fixture.service().understand("даа", InputContext.empty()).status(), "A fuzzy token below four code points was accepted.");
				PhantomAssertions.assertEquals(UnderstandingStatus.ACCEPTED, fixture.service().understand("соглсен", InputContext.empty()).status(), "Distance-one 4..7 intent typo was rejected.");
				PhantomAssertions.assertEquals(UnderstandingStatus.REJECTED, fixture.service().understand("согкккк", InputContext.empty()).status(), "Out-of-bound short typo was accepted.");
			}
			finally
			{
				fixture.stop();
			}
		});
		registry.add("02-required-slots-and-entity-ambiguity-clarify", context ->
		{
			final ServiceFixture fixture = service(context);
			try
			{
				assertResult(fixture.service().understand("пригласи", InputContext.empty()), UnderstandingStatus.CLARIFICATION_REQUIRED, "party.invite", "clarify.target_player");
				assertResult(fixture.service().understand("нужен саппорт", InputContext.empty()), UnderstandingStatus.CLARIFICATION_REQUIRED, "party.role.query", "clarify.party_role");
				assertResult(fixture.service().understand("идем в гиран", InputContext.empty()), UnderstandingStatus.CLARIFICATION_REQUIRED, "party.travel", "clarify.location");
				assertResult(fixture.service().understand("где взять 0 штук адены", InputContext.empty()), UnderstandingStatus.CLARIFICATION_REQUIRED, "item.acquire.query", "clarify.quantity");
			}
			finally
			{
				fixture.stop();
			}
		});
		registry.add("03-intent-near-tie-and-fuzzy-tie-never-guess", context ->
		{
			final String xml = Files.readString(xmlPath(context), StandardCharsets.UTF_8);
			final PhantomSemanticPack intentTie = modifiedPack(context, "intent-tie", xml.replace("<pattern id=\"refuse.explicit\" text=\"отказываюсь\"/>", "<pattern id=\"refuse.explicit\" text=\"согласен\"/>"), authority());
			final PhantomSemanticUnderstandingService tiedService = PhantomSemanticUnderstandingService.loaded(intentTie);
			tiedService.start();
			assertResult(tiedService.understand("согласен", InputContext.empty()), UnderstandingStatus.CLARIFICATION_REQUIRED, "party.accept", "clarify.intent");
			tiedService.beginStop();
			tiedService.finishStop();
			final String aliases = "\t\t<alias slot=\"PARTY_ROLE\" phrase=\"танка\" key=\"frontline.guardian\" quality=\"EXACT\"/>\n\t\t<alias slot=\"PARTY_ROLE\" phrase=\"танки\" key=\"damage.melee\" quality=\"EXACT\"/>\n\t</entityAliases>";
			final PhantomSemanticPack fuzzyTie = modifiedPack(context, "fuzzy-tie", xml.replace("\t</entityAliases>", aliases), authority());
			final PhantomSemanticUnderstandingService fuzzyService = PhantomSemanticUnderstandingService.loaded(fuzzyTie);
			fuzzyService.start();
			assertResult(fuzzyService.understand("нужен танку", InputContext.empty()), UnderstandingStatus.CLARIFICATION_REQUIRED, "party.role.query", "clarify.party_role");
			fuzzyService.beginStop();
			fuzzyService.finishStop();
		});
	}

	private void groundingTests(PhantomTestRegistry registry)
	{
		registry.add("01-current-authority-types-and-hashes-are-preserved", context ->
		{
			final ServiceFixture fixture = service(context);
			try
			{
				final UnderstandingResult item = fixture.service().understand("где взять адену", InputContext.empty());
				final UnderstandingResult npc = fixture.service().understand("где кларисса", InputContext.empty());
				final UnderstandingResult content = fixture.service().understand("что нужно для рифт", InputContext.empty());
				final UnderstandingResult topology = fixture.service().understand("идем в город гиран", InputContext.empty());
				final UnderstandingResult capability = fixture.service().understand("нужно лечение", InputContext.empty());
				final UnderstandingResult role = fixture.service().understand("нужен танк", InputContext.empty());
				PhantomAssertions.assertEquals("item", item.slots().getFirst().domainReference().namespace(), "Item grounding namespace changed.");
				PhantomAssertions.assertEquals("npc", npc.slots().getFirst().domainReference().namespace(), "NPC grounding namespace changed.");
				PhantomAssertions.assertEquals("content", content.slots().getFirst().domainReference().namespace(), "Content grounding namespace changed.");
				PhantomAssertions.assertEquals("topology.node", topology.slots().getFirst().domainReference().namespace(), "Topology grounding namespace changed.");
				PhantomAssertions.assertEquals("capability", capability.slots().getFirst().domainReference().namespace(), "Capability grounding namespace changed.");
				PhantomAssertions.assertEquals("party.role", role.slots().getFirst().domainReference().namespace(), "Party-role grounding namespace changed.");
				for (UnderstandingResult result : List.of(item, npc, content, topology, capability, role))
				{
					PhantomAssertions.assertEquals(HASHES.knowledgeHash(), result.knowledgeHash(), "Result lost pinned Game Knowledge hash.");
					PhantomAssertions.assertEquals(HASHES.topologyHash(), result.topologyHash(), "Result lost pinned topology hash.");
				}
			}
			finally
			{
				fixture.stop();
			}
		});
		registry.add("02-missing-and-drifting-authority-fail-publication", context ->
		{
			final EnumMap<SlotType, Map<String, PhantomDomainRef>> missing = authorityReferences();
			missing.put(SlotType.ITEM, Map.of());
			final Authority missingAuthority = PhantomSemanticGrounding.fixed(HASHES, missing);
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSemanticPack.load(xmlPath(context), corpusPath(context), missingAuthority), "Missing authoritative item target was published.");
			final Authority stable = authority();
			final AtomicInteger hashReads = new AtomicInteger();
			final Authority drifting = new Authority()
			{
				@Override
				public Hashes hashes()
				{
					return hashReads.incrementAndGet() == 1 ? HASHES : new Hashes("D".repeat(64), HASHES.topologyHash(), HASHES.partyRoleHash());
				}

				@Override
				public java.util.Optional<PhantomDomainRef> resolve(SlotType slotType, String key)
				{
					return stable.resolve(slotType, key);
				}
			};
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSemanticPack.load(xmlPath(context), corpusPath(context), drifting), "Drifting authority hashes were published.");
		});
		registry.add("03-production-sources-have-no-mutable-runtime-or-action-seam", context ->
		{
			final Path directory = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/semantic/understanding");
			final StringBuilder source = new StringBuilder();
			try (var files = Files.list(directory))
			{
				for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList())
				{
					source.append(Files.readString(file, StandardCharsets.UTF_8));
				}
			}
			final String text = source.toString();
			for (String forbidden : List.of("org.l2jmobius.gameserver.model.actor", "org.l2jmobius.gameserver.model.World", "java.sql", "ConnectionFactory", "sendPacket", "ClientPacket", "ServerPacket", "ExecutorService", "ScheduledFuture", "CompletableFuture", "new Thread(", "Chat", "LLM"))
			{
				PhantomAssertions.assertFalse(text.contains(forbidden), "Semantic production contains forbidden mutable/action/runtime dependency: " + forbidden);
			}
			PhantomAssertions.assertFalse(text.contains("case \"пригласи\"") || text.contains("equals(\"пригласи\")"), "Russian intent phrases leaked into Java switches.");
		});
	}

	private void contextTests(PhantomTestRegistry registry)
	{
		registry.add("01-real-managed-selected-leader-and-previous-identities-survive", context ->
		{
			final ServiceFixture fixture = service(context);
			try
			{
				PhantomAssertions.assertEquals(new PhantomDomainRef("character.object", "101"), fixture.service().understand("пригласи Иван", contextFixture("player-ivan")).slots().getFirst().domainReference(), "Real contextual identity was rewritten.");
				PhantomAssertions.assertEquals(new PhantomDomainRef("profile", "19"), fixture.service().understand("пригласи его", contextFixture("selected-managed")).slots().getFirst().domainReference(), "Managed contextual identity was rewritten.");
				PhantomAssertions.assertEquals(new PhantomDomainRef("character.object", "301"), fixture.service().understand("пригласи лидера", contextFixture("leader")).slots().getFirst().domainReference(), "Canonical leader identity was lost.");
				PhantomAssertions.assertEquals(new PhantomDomainRef("character.object", "101"), fixture.service().understand("пригласи того", contextFixture("previous-ivan")).slots().getFirst().domainReference(), "Previous accepted target identity was lost.");
			}
			finally
			{
				fixture.stop();
			}
		});
		registry.add("02-duplicate-names-pronouns-and-absent-context-never-invent", context ->
		{
			final ServiceFixture fixture = service(context);
			try
			{
				assertResult(fixture.service().understand("пригласи Алекс", contextFixture("duplicate-alex")), UnderstandingStatus.CLARIFICATION_REQUIRED, "party.invite", "clarify.target_player");
				assertResult(fixture.service().understand("пригласи его", contextFixture("two-players")), UnderstandingStatus.CLARIFICATION_REQUIRED, "party.invite", "clarify.target_player");
				assertResult(fixture.service().understand("пригласи Неизвестный", InputContext.empty()), UnderstandingStatus.CLARIFICATION_REQUIRED, "party.invite", "clarify.target_player");
			}
			finally
			{
				fixture.stop();
			}
		});
		registry.add("03-context-bounds-and-current-topology-are-exact", context ->
		{
			final ServiceFixture fixture = service(context);
			try
			{
				final UnderstandingResult result = fixture.service().understand("идем туда", contextFixture("current-giran"));
				PhantomAssertions.assertEquals(new PhantomDomainRef("topology.node", "giran.city"), result.slots().getFirst().domainReference(), "Current topology context was not exact.");
				final List<PlayerReference> tooMany = new ArrayList<>();
				for (int index = 0; index < 33; index++)
				{
					tooMany.add(player("character.object", Integer.toString(1000 + index), "Игрок" + index));
				}
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new InputContext(null, InputChannel.LOCAL, null, List.of(), tooMany, List.of(), null, null, null, null, List.of()), "A 33-player nearby context was accepted.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new InputContext(null, InputChannel.PARTY, null, tooMany.subList(0, 10), List.of(), List.of(), null, null, null, null, List.of()), "A ten-member canonical party context was accepted.");
			}
			finally
			{
				fixture.stop();
			}
		});
	}

	private void corpusTests(PhantomTestRegistry registry)
	{
		registry.add("01-corpus-thresholds-accuracy-safety-and-slots", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final CorpusRun run = runCorpus(pack, pack.corpus());
			PhantomAssertions.assertTrue(run.positiveIntentAccuracyBasisPoints() >= 9600, "Positive intent accuracy is below 96%.");
			PhantomAssertions.assertTrue(run.slotPrecisionBasisPoints() >= 9900, "Slot precision is below 99%: " + run.slotFailures());
			PhantomAssertions.assertEquals(10000, run.safetyBasisPoints(), "Clarification/negative safety is below 100%: " + run.safetyFailures());
			PhantomAssertions.assertTrue(run.cyrillic() >= 120, "Cyrillic corpus coverage is below 120.");
			PhantomAssertions.assertTrue(run.transliteration() >= 40, "Transliteration corpus coverage is below 40.");
			PhantomAssertions.assertTrue(run.abbreviation() >= 30, "Abbreviation/slang corpus coverage is below 30.");
			PhantomAssertions.assertTrue(run.fuzzy() >= 30, "Typo/fuzzy corpus coverage is below 30.");
			PhantomAssertions.assertTrue(run.clarification() >= 40, "Clarification corpus coverage is below 40.");
			PhantomAssertions.assertTrue(run.rejected() >= 40, "Negative corpus coverage is below 40.");
			context.record("semantic.intentAccuracyBasisPoints", run.positiveIntentAccuracyBasisPoints());
			context.record("semantic.slotPrecisionBasisPoints", run.slotPrecisionBasisPoints());
			context.record("semantic.safetyBasisPoints", run.safetyBasisPoints());
		});
		registry.add("02-forward-reverse-shuffle-are-byte-identical", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final List<CorpusCase> forward = pack.corpus();
			final List<CorpusCase> reverse = new ArrayList<>(forward);
			Collections.reverse(reverse);
			final List<CorpusCase> shuffled = new ArrayList<>(forward);
			Collections.shuffle(shuffled, new Random(SEED));
			final Map<String, String> first = runCorpus(pack, forward).canonicalResults();
			PhantomAssertions.assertEquals(first, runCorpus(pack, reverse).canonicalResults(), "Reverse semantic corpus order changed byte encoding.");
			PhantomAssertions.assertEquals(first, runCorpus(pack, shuffled).canonicalResults(), "Shuffled semantic corpus order changed byte encoding.");
		});
		registry.add("03-no-accepted-case-misses-declared-required-slots", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final PhantomSemanticUnderstandingService service = PhantomSemanticUnderstandingService.loaded(pack);
			service.start();
			try
			{
				for (CorpusCase corpusCase : pack.corpus())
				{
					final UnderstandingResult result = service.understand(corpusCase.input(), contextFixture(corpusCase.contextFixture()));
					if (result.status() != UnderstandingStatus.ACCEPTED)
					{
						continue;
					}
					final Set<SlotType> actual = result.slots().stream().map(SlotValue::type).collect(java.util.stream.Collectors.toSet());
					final var intent = pack.intent(result.selectedIntent());
					PhantomAssertions.assertTrue(actual.containsAll(intent.requiredAll()), "Accepted corpus result lacks a required-all slot: " + corpusCase.caseId());
					PhantomAssertions.assertTrue(intent.requiredAny().isEmpty() || !java.util.Collections.disjoint(actual, intent.requiredAny()), "Accepted corpus result lacks a required-any slot: " + corpusCase.caseId());
				}
			}
			finally
			{
				service.beginStop();
				service.finishStop();
			}
		});
	}

	private void lifecyclePerformanceTests(PhantomTestRegistry registry)
	{
		registry.add("01-start-failure-stop-and-post-stop-claims-fail-closed", context ->
		{
			final PhantomSemanticUnderstandingService failed = new PhantomSemanticUnderstandingService(() ->
			{
				throw new IllegalArgumentException("expected");
			});
			PhantomAssertions.assertThrows(IllegalArgumentException.class, failed::start, "Failed semantic startup did not propagate validation.");
			PhantomAssertions.assertEquals(PhantomSemanticUnderstandingService.State.FAILED, failed.snapshot().state(), "Failed semantic startup published a generation.");
			PhantomAssertions.assertEquals("none", failed.snapshot().packHash(), "Failed semantic startup retained a pack hash.");
			failed.beginStop();
			PhantomAssertions.assertTrue(failed.finishStop(), "Failed semantic service did not cleanly stop.");
			final ServiceFixture fixture = service(context);
			fixture.stop();
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> fixture.service().understand("согласен", InputContext.empty()), "Stopped semantic service accepted an operation claim.");
		});
		registry.add("02-concurrent-start-has-one-loader-and-one-publication", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final CountDownLatch entered = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final java.util.concurrent.atomic.AtomicInteger loads = new java.util.concurrent.atomic.AtomicInteger();
			final PhantomSemanticUnderstandingService service = new PhantomSemanticUnderstandingService(() ->
			{
				loads.incrementAndGet();
				entered.countDown();
				try
				{
					if (!release.await(5, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Timed out waiting for semantic start release.");
					}
				}
				catch (InterruptedException exception)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
				return pack;
			});
			final AtomicBoolean firstStarted = new AtomicBoolean();
			final AtomicBoolean secondStarted = new AtomicBoolean();
			final Thread first = new Thread(() -> firstStarted.set(service.start()), "semantic-test-first-start");
			first.start();
			PhantomAssertions.assertTrue(entered.await(5, TimeUnit.SECONDS), "First semantic start did not claim publication.");
			final Thread second = new Thread(() -> secondStarted.set(service.start()), "semantic-test-second-start");
			second.start();
			second.join(5000L);
			PhantomAssertions.assertFalse(second.isAlive(), "Concurrent semantic start did not fail fast.");
			PhantomAssertions.assertFalse(secondStarted.get(), "Concurrent semantic start published a second generation.");
			PhantomAssertions.assertEquals(1, loads.get(), "Concurrent semantic start invoked the loader twice.");
			release.countDown();
			first.join(5000L);
			PhantomAssertions.assertTrue(firstStarted.get(), "Claimed semantic start did not publish its generation.");
			PhantomAssertions.assertEquals(PhantomSemanticUnderstandingService.State.RUNNING, service.snapshot().state(), "Concurrent semantic start corrupted the running state.");
			PhantomAssertions.assertEquals(1L, service.snapshot().metrics().startsCompleted(), "Concurrent semantic start changed completion accounting.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Concurrent-start semantic service did not stop.");
		});
		registry.add("03-shutdown-waits-for-an-in-flight-operation-claim", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final CountDownLatch entered = new CountDownLatch(1);
			final CountDownLatch release = new CountDownLatch(1);
			final PhantomSemanticUnderstandingService service = new PhantomSemanticUnderstandingService(() -> pack, _ ->
			{
				entered.countDown();
				try
				{
					if (!release.await(5, TimeUnit.SECONDS))
					{
						throw new IllegalStateException("Timed out waiting for semantic operation release.");
					}
				}
				catch (InterruptedException exception)
				{
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
			});
			service.start();
			final Thread parser = new Thread(() -> service.understand("согласен", InputContext.empty()), "semantic-test-parser");
			parser.start();
			PhantomAssertions.assertTrue(entered.await(5, TimeUnit.SECONDS), "Semantic parse did not acquire an operation claim.");
			service.beginStop();
			final AtomicBoolean stopped = new AtomicBoolean();
			final Thread stopper = new Thread(() -> stopped.set(service.finishStop()), "semantic-test-stopper");
			stopper.start();
			Thread.sleep(50L);
			PhantomAssertions.assertFalse(stopped.get(), "Semantic shutdown ignored an in-flight operation claim.");
			release.countDown();
			parser.join(5000L);
			stopper.join(5000L);
			PhantomAssertions.assertTrue(stopped.get(), "Semantic shutdown did not finish after the operation drained.");
			PhantomAssertions.assertEquals(0, service.snapshot().operationClaims(), "Semantic service retained an operation claim.");
		});
		registry.add("04-100000-mixed-parses-remain-bounded-without-workers-or-db", context ->
		{
			final PhantomSemanticPack pack = pack(context);
			final PhantomSemanticUnderstandingService service = PhantomSemanticUnderstandingService.loaded(pack);
			service.start();
			final Set<Long> beforeThreads = nonDaemonThreadIds();
			final long started = System.nanoTime();
			for (int index = 0; index < 100000; index++)
			{
				final CorpusCase corpusCase = pack.corpus().get(index % pack.corpus().size());
				service.understand(corpusCase.input(), contextFixture(corpusCase.contextFixture()));
			}
			final long elapsed = System.nanoTime() - started;
			final var snapshot = service.snapshot();
			PhantomAssertions.assertEquals(100000L, snapshot.metrics().parses(), "Semantic performance smoke did not execute 100000 parses.");
			PhantomAssertions.assertTrue(snapshot.metrics().maximumTokensObserved() <= 64, "Semantic token bound was exceeded.");
			PhantomAssertions.assertTrue(snapshot.metrics().maximumCandidatesObserved() <= 128, "Semantic candidate bound was exceeded.");
			PhantomAssertions.assertEquals(beforeThreads, nonDaemonThreadIds(), "Semantic parsing created a non-daemon worker.");
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Semantic performance service did not stop.");
			context.record("semantic.performance100000Nanos", elapsed);
			context.record("semantic.dbWrites", 0);
			context.record("semantic.workers", 0);
		});
	}

	private ServiceFixture service(PhantomTestContext context)
	{
		final PhantomSemanticUnderstandingService service = PhantomSemanticUnderstandingService.loaded(pack(context));
		PhantomAssertions.assertTrue(service.start(), "Semantic service did not start.");
		return new ServiceFixture(service);
	}

	private PhantomSemanticPack pack(PhantomTestContext context)
	{
		return PhantomSemanticPack.load(xmlPath(context), corpusPath(context), authority());
	}

	private PhantomSemanticPack modifiedPack(PhantomTestContext context, String name, String xml, Authority authority) throws Exception
	{
		final Path path = _temporaryDirectory.resolve(name + ".xml");
		Files.writeString(path, xml, StandardCharsets.UTF_8);
		return PhantomSemanticPack.load(path, corpusPath(context), authority);
	}

	private void rejectPack(PhantomTestContext context, String name, String xml, Path corpus, Authority authority) throws Exception
	{
		final Path path = _temporaryDirectory.resolve(name + ".xml");
		Files.writeString(path, xml, StandardCharsets.UTF_8);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSemanticPack.load(path, corpus, authority), "Invalid semantic pack was accepted: " + name);
	}

	private static Authority authority()
	{
		return PhantomSemanticGrounding.fixed(HASHES, authorityReferences());
	}

	private static EnumMap<SlotType, Map<String, PhantomDomainRef>> authorityReferences()
	{
		final EnumMap<SlotType, Map<String, PhantomDomainRef>> result = new EnumMap<>(SlotType.class);
		result.put(SlotType.ITEM, refs("item", "57"));
		result.put(SlotType.NPC, refs("npc", "30080", "30081"));
		result.put(SlotType.CONTENT, refs("content", "rift.high-five-core", "raid.25001", "epic.29001"));
		result.put(SlotType.TOPOLOGY_NODE, refs("topology.node", "giran.city", "giran.region", "giran.shop.30081", "ssq.necropolis.past"));
		result.put(SlotType.LOCATION, refs("topology.node", "giran.city", "giran.shop.30081"));
		result.put(SlotType.CAPABILITY, refs("capability", "combat.heal", "combat.buff", "combat.tank", "combat.resurrection", "combat.crowd_control", "combat.melee_damage"));
		result.put(SlotType.PARTY_ROLE, refs("party.role", "frontline.guardian", "support.healer", "support.recharge", "support.enhancement", "damage.melee", "damage.ranged"));
		return result;
	}

	private static Map<String, PhantomDomainRef> refs(String namespace, String... keys)
	{
		final Map<String, PhantomDomainRef> result = new HashMap<>();
		for (String key : keys)
		{
			result.put(key, new PhantomDomainRef(namespace, key));
		}
		return Map.copyOf(result);
	}

	private static Path xmlPath(PhantomTestContext context)
	{
		return context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml");
	}

	private static Path corpusPath(PhantomTestContext context)
	{
		return context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv");
	}

	private static void assertNormalizationReason(PhantomSemanticPack pack, String input, String reason)
	{
		final PhantomSemanticNormalizer.Rejection rejection = PhantomAssertions.assertThrows(PhantomSemanticNormalizer.Rejection.class, () -> PhantomSemanticNormalizer.normalize(input, pack), "Invalid normalized input was accepted.");
		PhantomAssertions.assertEquals(reason, rejection.reasonKey(), "Normalization rejection reason changed.");
	}

	private static void assertResult(UnderstandingResult result, UnderstandingStatus status, String intent, String reason)
	{
		PhantomAssertions.assertEquals(status, result.status(), "Semantic result status changed.");
		PhantomAssertions.assertEquals(intent, result.selectedIntent(), "Semantic selected intent changed.");
		PhantomAssertions.assertEquals(reason, result.reasonKey(), "Semantic structured reason changed.");
	}

	private static InputContext contextFixture(String key)
	{
		final PlayerReference ivan = player("character.object", "101", "Иван");
		final PlayerReference latinIvan = player("character.object", "101", "Ivan");
		final PlayerReference petr = player("character.object", "102", "Петр");
		final PlayerReference managed = player("profile", "19", "Мира");
		final PlayerReference leader = player("character.object", "301", "Лидер");
		return switch (key)
		{
			case "none" -> InputContext.empty();
			case "player-ivan" -> new InputContext(null, InputChannel.LOCAL, null, List.of(), List.of(ivan), List.of(), null, null, null, null, List.of());
			case "latin-ivan" -> new InputContext(null, InputChannel.LOCAL, null, List.of(), List.of(latinIvan), List.of(), null, null, null, null, List.of());
			case "selected-managed" -> new InputContext(null, InputChannel.PRIVATE, null, List.of(), List.of(managed), List.of(), managed, null, null, null, List.of());
			case "leader" -> new InputContext(null, InputChannel.PARTY, leader, List.of(leader, ivan), List.of(), List.of(), null, null, null, null, List.of());
			case "previous-ivan" -> new InputContext(null, InputChannel.PRIVATE, null, List.of(), List.of(), List.of(), null, null, null, "party.invite", List.of(SlotValue.domain(SlotType.TARGET_PLAYER, ivan.reference(), -1, -1)));
			case "speaker-ivan" -> new InputContext(ivan, InputChannel.PRIVATE, null, List.of(), List.of(), List.of(), null, null, null, null, List.of());
			case "duplicate-alex" -> new InputContext(null, InputChannel.LOCAL, null, List.of(), List.of(player("character.object", "201", "Алекс"), player("character.object", "202", "Алекс")), List.of(), null, null, null, null, List.of());
			case "two-players" -> new InputContext(null, InputChannel.LOCAL, null, List.of(), List.of(ivan, petr), List.of(), null, null, null, null, List.of());
			case "current-giran" -> new InputContext(null, InputChannel.PARTY, null, List.of(), List.of(), List.of(), null, null, new PhantomDomainRef("topology.node", "giran.city"), null, List.of());
			default -> throw new IllegalArgumentException("Unknown semantic context fixture: " + key);
		};
	}

	private static PlayerReference player(String namespace, String key, String name)
	{
		return new PlayerReference(new PhantomDomainRef(namespace, key), name);
	}

	private static CorpusRun runCorpus(PhantomSemanticPack pack, List<CorpusCase> cases)
	{
		final PhantomSemanticUnderstandingService service = PhantomSemanticUnderstandingService.loaded(pack);
		service.start();
		int positive = 0;
		int positiveCorrect = 0;
		int expectedSlots = 0;
		int correctSlots = 0;
		int safety = 0;
		int safetyCorrect = 0;
		int cyrillic = 0;
		int transliteration = 0;
		int abbreviation = 0;
		int fuzzy = 0;
		int clarification = 0;
		int rejected = 0;
		final List<String> safetyFailures = new ArrayList<>();
		final List<String> slotFailures = new ArrayList<>();
		final Map<String, String> canonical = new java.util.TreeMap<>();
		try
		{
			for (CorpusCase corpusCase : cases)
			{
				final UnderstandingResult result = service.understand(corpusCase.input(), contextFixture(corpusCase.contextFixture()));
				canonical.put(corpusCase.caseId(), result.canonicalEncoding());
				if (corpusCase.expectedStatus() == UnderstandingStatus.ACCEPTED)
				{
					positive++;
					if ((result.status() == UnderstandingStatus.ACCEPTED) && result.selectedIntent().equals(corpusCase.expectedIntent()) && (result.confidence() >= corpusCase.minimumConfidence()) && result.reasonKey().equals(corpusCase.reasonKey()))
					{
						positiveCorrect++;
					}
				}
				else
				{
					safety++;
					if ((result.status() == corpusCase.expectedStatus()) && result.selectedIntent().equals(corpusCase.expectedIntent()) && result.reasonKey().equals(corpusCase.reasonKey()))
					{
						safetyCorrect++;
					}
					else
					{
						safetyFailures.add(corpusCase.caseId() + " expected=" + corpusCase.expectedStatus() + "/" + corpusCase.expectedIntent() + "/" + corpusCase.reasonKey() + " actual=" + result.status() + "/" + result.selectedIntent() + "/" + result.reasonKey());
					}
				}
				final Map<SlotType, String> expected = expectedSlots(corpusCase.expectedSlots());
				final Map<SlotType, String> actual = new EnumMap<>(SlotType.class);
				for (SlotValue slot : result.slots())
				{
					actual.put(slot.type(), slot.canonicalValue());
				}
				expectedSlots += expected.size();
				for (var entry : expected.entrySet())
				{
					if (entry.getValue().equals(actual.get(entry.getKey())))
					{
						correctSlots++;
					}
					else
					{
						slotFailures.add(corpusCase.caseId() + ":" + entry.getKey() + " expected=" + entry.getValue() + " actual=" + actual.get(entry.getKey()));
					}
				}
				final boolean hasCyrillic = corpusCase.input().codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.CYRILLIC);
				final boolean hasLatin = corpusCase.input().codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN);
				cyrillic += hasCyrillic ? 1 : 0;
				transliteration += hasLatin && !hasCyrillic ? 1 : 0;
				abbreviation += result.evidence().stream().anyMatch(evidence -> evidence.quality() == EvidenceQuality.ABBREVIATION) ? 1 : 0;
				fuzzy += result.evidence().stream().anyMatch(evidence -> evidence.quality() == EvidenceQuality.FUZZY) ? 1 : 0;
				clarification += corpusCase.expectedStatus() == UnderstandingStatus.CLARIFICATION_REQUIRED ? 1 : 0;
				rejected += corpusCase.expectedStatus() == UnderstandingStatus.REJECTED ? 1 : 0;
			}
		}
		finally
		{
			service.beginStop();
			service.finishStop();
		}
		return new CorpusRun(basisPoints(positiveCorrect, positive), basisPoints(correctSlots, expectedSlots), basisPoints(safetyCorrect, safety), cyrillic, transliteration, abbreviation, fuzzy, clarification, rejected, List.copyOf(safetyFailures), List.copyOf(slotFailures), Map.copyOf(canonical));
	}

	private static Map<SlotType, String> expectedSlots(String encoded)
	{
		if ("-".equals(encoded))
		{
			return Map.of();
		}
		final EnumMap<SlotType, String> result = new EnumMap<>(SlotType.class);
		for (String part : encoded.split(";"))
		{
			final int separator = part.indexOf('=');
			result.put(SlotType.valueOf(part.substring(0, separator)), part.substring(separator + 1));
		}
		return Map.copyOf(result);
	}

	private static int basisPoints(int numerator, int denominator)
	{
		return denominator == 0 ? 10000 : (int) (((long) numerator * 10000L) / denominator);
	}

	private static Set<Long> nonDaemonThreadIds()
	{
		final Set<Long> result = new HashSet<>();
		for (Thread thread : Thread.getAllStackTraces().keySet())
		{
			if (thread.isAlive() && !thread.isDaemon())
			{
				result.add(thread.threadId());
			}
		}
		return Set.copyOf(result);
	}

	private record ServiceFixture(PhantomSemanticUnderstandingService service)
	{
		private void stop()
		{
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Semantic fixture did not stop.");
		}
	}

	private record CorpusRun(int positiveIntentAccuracyBasisPoints, int slotPrecisionBasisPoints, int safetyBasisPoints, int cyrillic, int transliteration, int abbreviation, int fuzzy, int clarification, int rejected, List<String> safetyFailures, List<String> slotFailures, Map<String, String> canonicalResults)
	{
	}
}

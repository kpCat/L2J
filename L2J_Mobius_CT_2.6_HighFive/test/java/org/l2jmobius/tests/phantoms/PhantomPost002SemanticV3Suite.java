/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Variation;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomSpeechFacetProfile;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.PersonalitySnapshot;

/** Focused DB-free v3 manifest, validation and synthetic scale acceptance. */
public final class PhantomPost002SemanticV3Suite implements PhantomTestSuite
{
	public enum Mode
	{
		CONTENT,
		SCALE
	}

	private static final List<String> SCALE_ACTS = List.of("achievement.congratulate", "acquaintance.reply", "apology.accept", "disagreement.reply", "failure.empathy", "farewell.reply", "flirt.light", "greet.reply", "humor.reply", "irritation.reply", "memory.recall", "mood.empathy", "mood.share", "pivot.reply", "preference.ack", "reconcile.reply", "relationship.reply", "rest.reply", "smalltalk.reply", "surprise.reply");
	private static final List<RelationshipBand> BANDS = List.of(RelationshipBand.UNKNOWN, RelationshipBand.NEUTRAL, RelationshipBand.FAMILIAR, RelationshipBand.TRUSTED, RelationshipBand.RIVAL, RelationshipBand.TENSE, RelationshipBand.HOSTILE);
	private final Mode _mode;

	public PhantomPost002SemanticV3Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "post002-semantic-v3-" + _mode.name().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.SCALE)
		{
			scale(registry);
			return;
		}
		registry.add("01-segmented-seed-load-and-indexed-support-understanding", context ->
		{
			final Path root = context.moduleRoot().resolve("dist/game/data/phantoms");
			final PhantomHumanizedCatalog first = PhantomHumanizedCatalog.loadV3(root, false);
			final PhantomHumanizedCatalog second = PhantomHumanizedCatalog.loadV3(root, false);
			PhantomAssertions.assertEquals("high-five-ru-humanized-v3", first.familyId(), "V3 family identity is absent.");
			PhantomAssertions.assertEquals(first.combinedHash(), second.combinedHash(), "V3 manifest load/hash is not deterministic.");
			PhantomAssertions.assertEquals("support.buff.request", first.understand("бафни меня").orElseThrow().act(), "Core buff request was not understood.");
			PhantomAssertions.assertEquals("support.song.request", first.understand("дай сонг").orElseThrow().act(), "Song request was not understood.");
			PhantomAssertions.assertEquals("support.dance.request", first.understand("дай денс").orElseThrow().act(), "Dance request was not understood.");
			PhantomAssertions.assertTrue((first.maximumPatternIndexBucketSize() <= 256) && (first.maximumTemplateIndexBucketSize() <= 4096), "V3 runtime indexes exceed bounded candidate buckets.");
			context.record("semanticV3.combinedHash", first.combinedHash());
			context.record("semanticV3.patternCount", first.patternCount());
			context.record("semanticV3.templateCount", first.templateCount());
		});

		registry.add("02-custom-overlay-loads-last-and-enters-combined-hash", context ->
		{
			final Path root = copyHumanizedData(context);
			try
			{
				final Path custom = root.resolve("conversation/custom/my-phrases.xml");
				Files.writeString(custom, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<phrases version=\"1\">\n\t<template id=\"v3.lineage.buffs.reply.001\" act=\"support.buff.request\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" text=\"Проверяю операторский баф.\" override=\"true\"/>\n</phrases>\n", StandardCharsets.UTF_8);
				final PhantomHumanizedCatalog base = PhantomHumanizedCatalog.loadV3(root, false);
				final PhantomHumanizedCatalog customCatalog = PhantomHumanizedCatalog.loadV3(root, true);
				PhantomAssertions.assertFalse(base.combinedHash().equals(customCatalog.combinedHash()), "Enabled custom overlay did not enter combined hash.");
				final var selected = customCatalog.select("support.buff.request", RelationshipBand.UNKNOWN, Register.NEUTRAL, ProfanityMode.NONE, Variation.LOW, false, false, "Имя", "", "", "", 0, Set.of());
				PhantomAssertions.assertEquals("Проверяю операторский баф.", selected.text(), "Custom override did not load after v3 segments.");
			}
			finally
			{
				deleteTree(root);
			}
		});

		registry.add("03-v3-invalid-artifacts-fail-atomically", context ->
		{
			rejected(context, "semantic/humanized/v3/manifest.xml", source -> source.replace("semantic/humanized/v3/segments/lineage/buffs.xml", "../unsafe.xml"), "unsafe path");
			rejected(context, "semantic/humanized/v3/segments/lineage/buffs.xml", source -> source.replace("v3.lineage.buffs.request.002", "v3.lineage.buffs.request.001"), "duplicate ID");
			rejected(context, "semantic/humanized/v3/segments/lineage/buffs.xml", source -> source.replace("act=\"support.buff.request\"", "act=\"unknown.act\""), "unknown act");
			rejected(context, "conversation/humanized/v3/segments/lineage/buffs.xml", source -> source.replace("text=\"Секунду, проверю доступный баф.\"", "text=\"Проверю, могу ли сейчас дать подходящее усиление.\""), "normalized duplicate");
			rejected(context, "conversation/humanized/v3/segments/lineage/buffs.xml", source -> source.replace("<template id=\"v3.lineage.buffs.reply.001\"", "<template id=\"v3.lineage.buffs.reply.001\" mature=\"true\""), "mature segregation");
		});

		registry.add("05-persona079-voice-metadata-is-strict-and-legacy-stays-universal", context ->
		{
			final String segment = "conversation/humanized/v3/segments/lineage/pvp.xml";
			rejected(context, segment, source -> source.replace("voice=\"calm\"", "voice=\"invented\""), "unknown voice");
			rejected(context, segment, source -> source.replace("voice=\"calm\"", "voice=\"CALM\""), "voice casing");
			rejected(context, segment, source -> source.replace("voice=\"calm\"", "voices=\"calm\""), "unknown voice attribute");
			rejected(context, segment, source -> source.lines().map(line -> line.contains("act=\"lineage.pvp.risk.reply\"") && !line.contains("voice=") ? line.replace("<template ", "<template voice=\"calm\" ") : line).collect(java.util.stream.Collectors.joining("\n")), "missing universal fallback");
			rejected(context, "semantic/humanized/v3/segments/lineage/pvp.xml", source -> source.replace("phrase=\"меня слили в пвп\"", "phrase=\"го дуэль\""), "duplicate normalized game phrase");
			final Path root = copyHumanizedData(context);
			try
			{
				for (PhantomHumanizedCatalog legacy : List.of(PhantomHumanizedCatalog.load(root, true), PhantomHumanizedCatalog.loadV2(root, true)))
				{
					PhantomAssertions.assertEquals(legacy.select("greet.reply", RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", 42, Set.of()), legacy.select("greet.reply", RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", "direct", 42, Set.of()), "Voice changed legacy selection.");
				}
				final Path custom = root.resolve("conversation/custom/my-phrases.xml");
				Files.writeString(custom, "<phrases version=\"1\"><template id=\"custom.persona079\" act=\"greet.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" voice=\"direct\" text=\"Операторский пример для проверки метаданных.\"/></phrases>", StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomHumanizedCatalog.loadV3(root, true), "V1 custom parser accepted V3-only metadata.");
			}
			finally
			{
				deleteTree(root);
			}
		});

		registry.add("06-persona079-pools-fallback-relationship-and-private-gates", context ->
		{
			final Path root = copyHumanizedData(context);
			try
			{
				final String privateSegment = "conversation/humanized/v3/segments/lineage/persona079-private.xml";
				Files.writeString(root.resolve(privateSegment), "<humanizedV3ConversationSegment id=\"v3.persona079.private\" version=\"3\" category=\"lineage.pvp\" mature=\"true\"><templates><template id=\"v3.persona079.private.direct\" act=\"flirt.light\" band=\"TRUSTED\" register=\"CASUAL\" profanity=\"NONE\" voice=\"direct\" text=\"Только для доверительного приватного разговора.\"/></templates></humanizedV3ConversationSegment>", StandardCharsets.UTF_8);
				final Path manifest = root.resolve("semantic/humanized/v3/manifest.xml");
				Files.writeString(manifest, Files.readString(manifest).replace("</segments>", "<segment kind=\"CONVERSATION\" path=\"" + privateSegment + "\"/></segments>"), StandardCharsets.UTF_8);
				final PhantomHumanizedCatalog catalog = PhantomHumanizedCatalog.loadV3(root, false);
				final String act = "lineage.pvp.risk.reply";
				final var texts = new java.util.HashSet<String>();
				for (String voice : List.of("calm", "open", "direct", "empathetic"))
				{
					final var first = selectVoice(catalog, act, RelationshipBand.UNKNOWN, voice, 0, Set.of());
					final var second = selectVoice(catalog, act, RelationshipBand.UNKNOWN, voice, 0, Set.of(first.responseHash()));
					PhantomAssertions.assertTrue(first.templateId().startsWith("v3.persona079.pvp." + voice + '.') && second.templateId().startsWith("v3.persona079.pvp." + voice + '.'), "Same selector leaked another voice.");
					PhantomAssertions.assertFalse(first.responseHash().equals(second.responseHash()), "Recent hash did not rotate within the voice pool.");
					PhantomAssertions.assertTrue(catalog.understand("бой можно избежать").orElseThrow().act().equals(act), "Existing prospective PvP risk act changed.");
					for (String text : List.of(first.text(), second.text()))
					{
						PhantomAssertions.assertFalse(text.contains("что произошло") || text.contains("тот бой") || text.contains("Как всё прошло") || text.contains("столкнулся") || text.contains("в бою было") || text.contains("что случилось"), "Risk voice presupposed a completed fight without a native fact.");
					}
					texts.add(first.text());
				}
				PhantomAssertions.assertEquals(4, texts.size(), "Same selector did not yield four intentional tones.");
				for (RelationshipBand band : RelationshipBand.values())
				{
					for (String voice : List.of("calm", "open", "direct", "empathetic", "unknown"))
					{
						final var selection = selectVoice(catalog, act, band, voice, 0, Set.of());
						PhantomAssertions.assertFalse(selection.templateId().endsWith(".trusted") && (band != RelationshipBand.TRUSTED), "Trusted voice leaked to an unconfirmed relation.");
						PhantomAssertions.assertFalse(selection.templateId().endsWith(".rival") && (band != RelationshipBand.RIVAL), "Rival voice leaked to an unconfirmed relation.");
					}
				}
				PhantomAssertions.assertTrue(selectVoice(catalog, act, RelationshipBand.TRUSTED, "empathetic", 0, Set.of()).templateId().endsWith(".trusted"), "Confirmed trusted relation was ignored.");
				PhantomAssertions.assertTrue(selectVoice(catalog, act, RelationshipBand.RIVAL, "direct", 0, Set.of()).templateId().endsWith(".rival"), "Confirmed rival relation was ignored.");
				PhantomAssertions.assertEquals(selectVoice(catalog, act, RelationshipBand.UNKNOWN, "", 0, Set.of()), selectVoice(catalog, act, RelationshipBand.UNKNOWN, "unknown", 0, Set.of()), "Unknown voice did not use the universal fallback.");
				PhantomAssertions.assertEquals(selectVoice(catalog, "greet.reply", RelationshipBand.UNKNOWN, "", 0, Set.of()), selectVoice(catalog, "greet.reply", RelationshipBand.UNKNOWN, "direct", 0, Set.of()), "Missing act/voice combination did not fall back.");
				for (boolean enabled : List.of(false, true))
				{
					for (boolean privateChannel : List.of(false, true))
					{
						for (RelationshipBand band : RelationshipBand.values())
						{
							final var selected = catalog.select("flirt.light", band, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, enabled, privateChannel, "Имя", "", "", "", "direct", 0, Set.of());
							PhantomAssertions.assertEquals(enabled && privateChannel && (band == RelationshipBand.TRUSTED), selected.matureUsed(), "Voice bypassed mature opt-in/private/trusted.");
							PhantomAssertions.assertFalse(selected.profanityUsed(), "Voice bypassed profanity NONE.");
						}
					}
				}
				PhantomAssertions.assertFalse(catalog.select("flirt.light", RelationshipBand.TRUSTED, Register.NEUTRAL, ProfanityMode.NONE, Variation.HIGH, true, true, "Имя", "", "", "", "direct", 0, Set.of()).matureUsed(), "Voice bypassed the register bound.");
			}
			finally
			{
				deleteTree(root);
			}
		});

		registry.add("07-facets082-pure-projection-stability-diversity-and-native-trait-tendencies", context ->
		{
			final List<String> keys = List.of("aggression", "sarcasm", "humor", "reserved", "swagger", "timidity", "romance", "roughness", "cunning", "gossip");
			final Map<String, Integer> neutral = Map.of("persistence", 0, "caution", 0, "sociability", 0, "loyalty", 0, "empathy", 0, "assertiveness", 0);
			final Set<Map<String, Integer>> vectors = new HashSet<>();
			final Map<String, Set<Integer>> distinct = new HashMap<>();
			keys.forEach(key -> distinct.put(key, new HashSet<>()));
			int coexisting = 0;
			for (long profileId = 1; profileId <= 5000; profileId++)
			{
				final long seed = List.of(820082L, 820083L, Long.MAX_VALUE).get((int) (profileId % 3));
				final Map<String, Integer> traits = new HashMap<>(neutral);
				traits.put("sociability", (int) ((profileId % 3) - 1) * 6000);
				traits.put("caution", (int) ((profileId % 5) - 2) * 3000);
				final var personality = new PersonalitySnapshot(profileId, seed, traits, "A".repeat(64));
				final Map<String, Integer> scores = PhantomSpeechFacetProfile.from(personality).scores();
				final var reconstructed = new PersonalitySnapshot(profileId, seed, Map.copyOf(traits), "A".repeat(64));
				PhantomAssertions.assertEquals(scores, PhantomSpeechFacetProfile.from(reconstructed).scores(), "Persisted inputs did not reconstruct the same facet vector: " + profileId);
				PhantomAssertions.assertEquals(Set.copyOf(keys), scores.keySet(), "Projection did not contain exactly the ten native facets.");
				for (String key : keys)
				{
					final int score = scores.get(key);
					PhantomAssertions.assertTrue((score >= 0) && (score <= 100), "Projected facet was outside 0..100: " + key);
					distinct.get(key).add(score);
				}
				vectors.add(scores);
				if (scores.values().stream().filter(score -> score >= 60).count() >= 2)
				{
					coexisting++;
				}
			}
			PhantomAssertions.assertTrue(vectors.size() >= 1000, "Synthetic identities collapsed into a small number of facet presets.");
			for (String key : keys)
			{
				PhantomAssertions.assertTrue(distinct.get(key).size() >= 30, "Facet did not vary independently across synthetic identities: " + key);
			}
			PhantomAssertions.assertTrue(coexisting >= 100, "Facets behaved as mutually exclusive voices.");
			final var baseline = PhantomSpeechFacetProfile.from(new PersonalitySnapshot(82, 820082, neutral, "A".repeat(64)));
			PhantomAssertions.assertFalse(baseline.scores().equals(PhantomSpeechFacetProfile.from(new PersonalitySnapshot(83, 820082, neutral, "A".repeat(64))).scores()), "Profile identity did not influence native facets.");
			PhantomAssertions.assertFalse(baseline.scores().equals(PhantomSpeechFacetProfile.from(new PersonalitySnapshot(82, 820083, neutral, "A".repeat(64))).scores()), "Persisted seed did not influence native facets.");
			PhantomAssertions.assertThrows(UnsupportedOperationException.class, () -> baseline.scores().put("humor", 0), "Facet scores remained mutable.");
			for (List<String> tendency : List.of(List.of("assertiveness", "aggression", "up"), List.of("assertiveness", "timidity", "down"), List.of("caution", "reserved", "up"), List.of("caution", "aggression", "down"), List.of("sociability", "humor", "up"), List.of("sociability", "gossip", "up"), List.of("sociability", "reserved", "down"), List.of("empathy", "romance", "up"), List.of("empathy", "roughness", "down"), List.of("persistence", "cunning", "up"), List.of("loyalty", "romance", "up")))
			{
				final Map<String, Integer> low = new HashMap<>(neutral);
				final Map<String, Integer> high = new HashMap<>(neutral);
				low.put(tendency.get(0), -10000);
				high.put(tendency.get(0), 10000);
				final int lower = PhantomSpeechFacetProfile.from(new PersonalitySnapshot(82, 820082, low, "A".repeat(64))).scores().get(tendency.get(1));
				final int higher = PhantomSpeechFacetProfile.from(new PersonalitySnapshot(82, 820082, high, "A".repeat(64))).scores().get(tendency.get(1));
				PhantomAssertions.assertTrue(tendency.get(2).equals("up") ? higher > lower : higher < lower, "Native trait tendency was lost: " + tendency);
			}
			final Map<String, Integer> extremes = new HashMap<>(neutral);
			final Map<String, Integer> bounded = new HashMap<>(neutral);
			for (String trait : neutral.keySet())
			{
				extremes.put(trait, trait.equals("caution") ? Integer.MIN_VALUE : Integer.MAX_VALUE);
				bounded.put(trait, trait.equals("caution") ? -10000 : 10000);
			}
			PhantomAssertions.assertEquals(PhantomSpeechFacetProfile.from(new PersonalitySnapshot(Long.MAX_VALUE, Long.MIN_VALUE, bounded, "A".repeat(64))).scores(), PhantomSpeechFacetProfile.from(new PersonalitySnapshot(Long.MAX_VALUE, Long.MIN_VALUE, extremes, "A".repeat(64))).scores(), "Native trait clamping overflowed.");
			context.record("facets082.syntheticNumericProfileIds", 5000);
			context.record("facets082.distinctVectors", vectors.size());
			context.record("facets082.coexistingVectors", coexisting);
		});

		registry.add("08-facets082-v3-only-strict-metadata-and-lawful-selection", context ->
		{
			final String segment = "conversation/humanized/v3/segments/lineage/pvp.xml";
			rejected(context, segment, source -> source.replace("facet=\"aggression\"", "facet=\"AGGRESSION\""), "facet casing");
			rejected(context, segment, source -> source.replace("facet=\"aggression\"", "facet=\"invented\""), "unknown facet");
			for (String invalid : List.of("-1", "101", "+60", "1.0", " 60", "６０"))
			{
				rejected(context, segment, source -> source.replace("minIntensity=\"60\"", "minIntensity=\"" + invalid + "\""), "invalid facet threshold " + invalid);
			}
			rejected(context, segment, source -> source.replace("facet=\"aggression\" ", ""), "threshold without facet");
			rejected(context, segment, source -> source.replace("v3.facet082.sarcasm.pvp", "v3.facet082.aggression.pvp"), "duplicate facet template ID");
			rejected(context, segment, source -> source.replace("Смелость прекрасна, особенно когда рядом ещё и здравый смысл. Какие риски в пвп стоит оценить сначала?", "Не люблю уступать. Но лезть в пвп без подготовки — только злиться зря. Какой риск тебя беспокоит?"), "duplicate normalized facet text");
			rejected(context, segment, source -> source.replace("band=\"UNKNOWN\"", "band=\"INVENTED\""), "unknown relation band");
			rejected(context, segment, source -> source.replace("text=\"Не люблю уступать.", "text=\"<неверный XML>"), "malformed facet text");
			rejected(context, segment, source -> source.replace("?>", "?>\n<!DOCTYPE humanizedV3ConversationSegment [<!ENTITY xxe SYSTEM \"file:///phantom-task082-forbidden\">]>"), "V3 XXE");
			rejected(context, "semantic/humanized/v3/segments/lineage/pvp.xml", source -> source.replace("phrase=\"меня слили в пвп\"", "phrase=\"меня слили в пвп\" facet=\"humor\""), "facet on semantic pattern");
			final Path root = copyHumanizedData(context);
			try
			{
				final PhantomHumanizedCatalog catalog = PhantomHumanizedCatalog.loadV3(root, false);
				final Map<String, Integer> scores = new HashMap<>();
				PhantomSpeechFacetProfile.KEYS.forEach(key -> scores.put(key, 0));
				for (String facet : PhantomSpeechFacetProfile.KEYS)
				{
					scores.replaceAll((key, value) -> key.equals(facet) ? 100 : 0);
					final String act = Set.of("humor", "reserved", "romance", "cunning", "gossip").contains(facet) ? "lineage.farming.spot.reply" : "lineage.pvp.risk.reply";
					final RelationshipBand band = facet.equals("romance") ? RelationshipBand.TRUSTED : RelationshipBand.UNKNOWN;
					final var selected = selectFacets(catalog, act, band, scores, 0, Set.of());
					PhantomAssertions.assertTrue(selected.templateId().startsWith("v3.facet082." + facet + '.'), "Real content facet was unreachable: " + facet);
					PhantomAssertions.assertFalse(selected.matureUsed() || selected.profanityUsed(), "Demonstration facet bypassed non-mature/profanity NONE.");
					PhantomAssertions.assertEquals(selected, selectFacets(catalog, act, band, Map.copyOf(scores), 0, Set.of()), "Facet-aware choice was not deterministic.");
				}
				scores.replaceAll((key, value) -> 0);
				final String act = "lineage.pvp.risk.reply";
				for (long selector = 0; selector < 16; selector++)
				{
					PhantomAssertions.assertEquals(selectVoice(catalog, act, RelationshipBand.UNKNOWN, "direct", selector, Set.of()), selectFacets(catalog, act, RelationshipBand.UNKNOWN, scores, selector, Set.of()), "No qualifying facet changed base-voice fallback.");
					PhantomAssertions.assertFalse(catalog.select(act, RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", selector, Set.of()).templateId().startsWith("v3.facet082."), "Legacy universal selection leaked a facet.");
				}
				scores.put("sarcasm", 59);
				PhantomAssertions.assertEquals(selectVoice(catalog, act, RelationshipBand.UNKNOWN, "direct", 0, Set.of()), selectFacets(catalog, act, RelationshipBand.UNKNOWN, scores, 0, Set.of()), "Implicit intensity threshold was below 60.");
				scores.put("sarcasm", 60);
				PhantomAssertions.assertTrue(selectFacets(catalog, act, RelationshipBand.UNKNOWN, scores, 0, Set.of()).templateId().startsWith("v3.facet082.sarcasm."), "Default intensity threshold 60 was ignored.");
				scores.replaceAll((key, value) -> 100);
				final Set<String> rotated = new HashSet<>();
				for (long selector = 0; selector < 256; selector++)
				{
					rotated.add(selectFacets(catalog, act, RelationshipBand.UNKNOWN, scores, selector, Set.of()).templateId());
				}
				PhantomAssertions.assertEquals(5, rotated.size(), "Eligible PvP facets starved under deterministic request variation.");
				final var first = selectFacets(catalog, act, RelationshipBand.UNKNOWN, scores, 0, Set.of());
				PhantomAssertions.assertFalse(first.responseHash().equals(selectFacets(catalog, act, RelationshipBand.UNKNOWN, scores, 0, Set.of(first.responseHash())).responseHash()), "Recent reply did not rotate eligible facets.");
				PhantomAssertions.assertEquals(selectVoice(catalog, act, RelationshipBand.RIVAL, "direct", 0, Set.of()), selectFacets(catalog, act, RelationshipBand.RIVAL, scores, 0, Set.of()), "UNKNOWN facet displaced exact RIVAL voice.");
				PhantomAssertions.assertEquals(selectVoice(catalog, act, RelationshipBand.TRUSTED, "empathetic", 0, Set.of()), catalog.select(act, RelationshipBand.TRUSTED, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", "empathetic", scores, 0, Set.of()), "UNKNOWN facet displaced exact TRUSTED voice.");
				PhantomAssertions.assertEquals(selectVoice(catalog, "greet.reply", RelationshipBand.UNKNOWN, "direct", 0, Set.of()), selectFacets(catalog, "greet.reply", RelationshipBand.UNKNOWN, scores, 0, Set.of()), "Act without facet templates changed legacy choice.");
				for (Variation variation : List.of(Variation.LOW, Variation.MEDIUM))
				{
					final Set<String> varied = new HashSet<>();
					for (long selector = 0; selector < 128; selector++)
					{
						varied.add(catalog.select(act, RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, variation, false, false, "Имя", "", "", "", "direct", scores, selector, Set.of()).templateId());
					}
					PhantomAssertions.assertEquals(5, varied.size(), "Variation starved eligible facets: " + variation);
					final var initial = catalog.select(act, RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, variation, false, false, "Имя", "", "", "", "direct", scores, 0, Set.of());
					final var next = catalog.select(act, RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, variation, false, false, "Имя", "", "", "", "direct", scores, 0, Set.of(initial.responseHash()));
					PhantomAssertions.assertFalse(initial.responseHash().equals(next.responseHash()), "Recent reply did not rotate eligible facets at " + variation);
				}
				final Path fixtureSegment = root.resolve(segment);
				final Path fixtureManifest = root.resolve("semantic/humanized/v3/manifest.xml");
				final String originalSegment = Files.readString(fixtureSegment, StandardCharsets.UTF_8);
				final String originalManifest = Files.readString(fixtureManifest, StandardCharsets.UTF_8);
				final String privateRelative = "conversation/humanized/v3/segments/lineage/facet082-private.xml";
				final Path privateFacet = root.resolve(privateRelative);
				try
				{
					final String fixtureTemplates = String.join("\n",
						"<template id=\"v3.fixture082.voice\" act=\"humor.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" voice=\"empathetic\" facet=\"cunning\" text=\"Отдельный пример подходящего голоса и характеристики.\"/>",
						"<template id=\"v3.fixture082.register\" act=\"surprise.reply\" band=\"UNKNOWN\" register=\"CASUAL\" profanity=\"NONE\" facet=\"swagger\" text=\"Отдельный разговорный пример с ограничением регистра.\"/>",
						"<template id=\"v3.fixture082.profanity\" act=\"irritation.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"MILD\" facet=\"roughness\" text=\"Отдельный пример с ограничительной меткой грубости.\"/>");
					Files.writeString(fixtureSegment, originalSegment.replace("</templates>", fixtureTemplates + "\n</templates>"), StandardCharsets.UTF_8);
					Files.writeString(privateFacet, "<humanizedV3ConversationSegment id=\"v3.fixture082.private\" version=\"3\" category=\"lineage.pvp\" mature=\"true\"><templates><template id=\"v3.fixture082.mature\" act=\"flirt.light\" band=\"TRUSTED\" register=\"CASUAL\" profanity=\"NONE\" facet=\"romance\" text=\"Отдельный доверительный пример только для приватного разговора.\"/></templates></humanizedV3ConversationSegment>", StandardCharsets.UTF_8);
					Files.writeString(fixtureManifest, originalManifest.replace("</segments>", "<segment kind=\"CONVERSATION\" path=\"" + privateRelative + "\"/></segments>"), StandardCharsets.UTF_8);
					final PhantomHumanizedCatalog guarded = PhantomHumanizedCatalog.loadV3(root, false);
					scores.replaceAll((key, value) -> key.equals("cunning") ? 100 : 0);
					PhantomAssertions.assertEquals("v3.fixture082.voice", guarded.select("humor.reply", RelationshipBand.UNKNOWN, Register.NEUTRAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", "empathetic", scores, 0, Set.of()).templateId(), "Matching base voice could not select a voiced facet.");
					PhantomAssertions.assertEquals(selectVoice(guarded, "humor.reply", RelationshipBand.UNKNOWN, "direct", 0, Set.of()), selectFacets(guarded, "humor.reply", RelationshipBand.UNKNOWN, scores, 0, Set.of()), "Facet selection borrowed another base voice.");
					scores.replaceAll((key, value) -> key.equals("swagger") ? 100 : 0);
					PhantomAssertions.assertEquals("v3.fixture082.register", selectFacets(guarded, "surprise.reply", RelationshipBand.UNKNOWN, scores, 0, Set.of()).templateId(), "Lawful CASUAL facet was unreachable.");
					PhantomAssertions.assertEquals(guarded.select("surprise.reply", RelationshipBand.UNKNOWN, Register.NEUTRAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", "direct", 0, Set.of()), guarded.select("surprise.reply", RelationshipBand.UNKNOWN, Register.NEUTRAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", "direct", scores, 0, Set.of()), "CASUAL facet bypassed NEUTRAL register.");
					scores.replaceAll((key, value) -> key.equals("roughness") ? 100 : 0);
					PhantomAssertions.assertEquals(selectVoice(guarded, "irritation.reply", RelationshipBand.UNKNOWN, "direct", 0, Set.of()), selectFacets(guarded, "irritation.reply", RelationshipBand.UNKNOWN, scores, 0, Set.of()), "Profanity NONE selected a profanity-coded facet.");
					scores.replaceAll((key, value) -> key.equals("romance") ? 100 : 0);
					for (boolean enabled : List.of(false, true))
					{
						for (boolean privateChannel : List.of(false, true))
						{
							for (RelationshipBand band : RelationshipBand.values())
							{
								final var selected = guarded.select("flirt.light", band, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, enabled, privateChannel, "Имя", "", "", "", "direct", scores, 0, Set.of());
								final boolean allowed = enabled && privateChannel && (band == RelationshipBand.TRUSTED);
								PhantomAssertions.assertEquals(allowed, selected.templateId().equals("v3.fixture082.mature"), "Faceted mature reply bypassed opt-in/private/trusted gates.");
								PhantomAssertions.assertEquals(allowed, selected.matureUsed(), "Faceted mature result did not reflect lawful eligibility.");
							}
						}
					}
					PhantomAssertions.assertFalse(guarded.select("flirt.light", RelationshipBand.TRUSTED, Register.NEUTRAL, ProfanityMode.NONE, Variation.HIGH, true, true, "Имя", "", "", "", "direct", scores, 0, Set.of()).matureUsed(), "Mature facet bypassed its CASUAL register.");
				}
				finally
				{
					Files.writeString(fixtureSegment, originalSegment, StandardCharsets.UTF_8);
					Files.writeString(fixtureManifest, originalManifest, StandardCharsets.UTF_8);
					Files.deleteIfExists(privateFacet);
					scores.replaceAll((key, value) -> 100);
				}
				final Map<String, Integer> invalid = new HashMap<>(scores);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> selectFacets(catalog, act, RelationshipBand.UNKNOWN, null, 0, Set.of()), "Null facet map did not fail closed.");
				invalid.remove("humor");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> selectFacets(catalog, act, RelationshipBand.UNKNOWN, invalid, 0, Set.of()), "Incomplete facet map did not fail closed.");
				invalid.put("humor", 101);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> selectFacets(catalog, act, RelationshipBand.UNKNOWN, invalid, 0, Set.of()), "Out-of-range facet map did not fail closed.");
				invalid.put("humor", -1);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> selectFacets(catalog, act, RelationshipBand.UNKNOWN, invalid, 0, Set.of()), "Negative facet map did not fail closed.");
				invalid.put("humor", null);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> selectFacets(catalog, act, RelationshipBand.UNKNOWN, invalid, 0, Set.of()), "Null facet score did not fail closed.");
				invalid.put("humor", 100);
				invalid.put("unknown", 100);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> selectFacets(catalog, act, RelationshipBand.UNKNOWN, invalid, 0, Set.of()), "Unknown facet map key did not fail closed.");
				for (boolean version2 : List.of(false, true))
				{
					final Path legacy = root.resolve("conversation/humanized/high-five-ru-humanized-conversation-v" + (version2 ? 2 : 1) + ".xml");
					final String source = Files.readString(legacy, StandardCharsets.UTF_8);
					try
					{
						Files.writeString(legacy, source.replace("<template ", "<template facet=\"humor\" "), StandardCharsets.UTF_8);
						PhantomAssertions.assertThrows(IllegalArgumentException.class, () ->
						{
							if (version2)
							{
								PhantomHumanizedCatalog.loadV2(root, false);
							}
							else
							{
								PhantomHumanizedCatalog.load(root, false);
							}
						}, "Legacy XML accepted V3-only facet metadata.");
					}
					finally
					{
						Files.writeString(legacy, source, StandardCharsets.UTF_8);
					}
				}
				Files.writeString(root.resolve("conversation/custom/my-phrases.xml"), "<phrases version=\"1\"><template id=\"custom.facet082\" act=\"greet.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" facet=\"humor\" minIntensity=\"60\" text=\"Операторский пример недопустимой характеристики.\"/></phrases>", StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomHumanizedCatalog.loadV3(root, true), "Custom XML accepted V3-only facet metadata.");
			}
			finally
			{
				deleteTree(root);
			}
		});
	}

	private static PhantomHumanizedCatalog.Selection selectVoice(PhantomHumanizedCatalog catalog, String act, RelationshipBand band, String voice, long selector, Set<String> recent)
	{
		return catalog.select(act, band, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", voice, selector, recent);
	}

	private static PhantomHumanizedCatalog.Selection selectFacets(PhantomHumanizedCatalog catalog, String act, RelationshipBand band, Map<String, Integer> scores, long selector, Set<String> recent)
	{
		return catalog.select(act, band, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", "direct", scores, selector, recent);
	}

	private static void scale(PhantomTestRegistry registry)
	{
		registry.add("04-synthetic-20k-templates-5k-patterns-indexed-scale", context ->
		{
			final Path root = copyHumanizedData(context);
			try
			{
				deleteTree(root.resolve("semantic/humanized/v3"));
				deleteTree(root.resolve("conversation/humanized/v3"));
				writeScalePack(root);
				final PhantomHumanizedCatalog catalog = PhantomHumanizedCatalog.loadV3(root, false);
				PhantomAssertions.assertTrue(catalog.patternCount() >= 5000, "V3 did not load at least 5k understanding patterns.");
				PhantomAssertions.assertTrue(catalog.templateCount() >= 20000, "V3 did not load at least 20k response templates.");
				PhantomAssertions.assertTrue((catalog.maximumPatternIndexBucketSize() <= 256) && (catalog.maximumTemplateIndexBucketSize() <= 4096), "Synthetic scale regressed to unbounded runtime candidate scans.");
				PhantomAssertions.assertTrue(catalog.understand("масштабная проверка 4 999").isPresent(), "Indexed exact lookup failed at the end of the 5k pattern set.");
				PhantomAssertions.assertFalse(catalog.select("smalltalk.reply", RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", 19999, Set.of()).text().isBlank(), "Indexed response selection failed at 20k scale.");
				context.record("semanticV3.scalePatternCount", catalog.patternCount());
				context.record("semanticV3.scaleTemplateCount", catalog.templateCount());
			}
			finally
			{
				deleteTree(root);
			}
		});
	}

	private static void writeScalePack(Path root) throws Exception
	{
		final Path manifest = root.resolve("semantic/humanized/v3/manifest.xml");
		Files.createDirectories(manifest.getParent());
		final StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<humanizedV3Manifest id=\"high-five-ru-humanized-v3\" version=\"3\" maxFiles=\"64\" maxFileBytes=\"1048576\" maxTotalBytes=\"33554432\" maxPatterns=\"8192\" maxTemplates=\"32768\" maxAliases=\"2048\" maxProfanity=\"1024\">\n\t<topics><topic key=\"smalltalk\"/></topics>\n\t<acts>\n");
		for (String act : SCALE_ACTS)
		{
			xml.append("\t\t<act key=\"").append(act).append("\"/>\n");
		}
		xml.append("\t</acts>\n\t<segments>\n");
		for (int segment = 0; segment < 5; segment++)
		{
			xml.append("\t\t<segment kind=\"SEMANTIC\" path=\"semantic/humanized/v3/segments/scale/patterns-").append(segment).append(".xml\"/>\n");
		}
		for (int segment = 0; segment < SCALE_ACTS.size(); segment++)
		{
			xml.append("\t\t<segment kind=\"CONVERSATION\" path=\"conversation/humanized/v3/segments/scale/templates-").append(segment).append(".xml\"/>\n");
		}
		xml.append("\t</segments>\n</humanizedV3Manifest>\n");
		Files.writeString(manifest, xml, StandardCharsets.UTF_8);
		for (int segment = 0; segment < 5; segment++)
		{
			final Path path = root.resolve("semantic/humanized/v3/segments/scale/patterns-" + segment + ".xml");
			Files.createDirectories(path.getParent());
			final StringBuilder content = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<humanizedV3SemanticSegment id=\"v3.scale.patterns.").append(segment).append("\" version=\"3\" category=\"scale.patterns\">\n\t<patterns>\n");
			for (int index = 0; index < 1000; index++)
			{
				final int ordinal = (segment * 1000) + index;
				content.append("\t\t<pattern id=\"v3.scale.pattern.").append(String.format(java.util.Locale.ROOT, "%04d", ordinal)).append("\" topic=\"smalltalk\" act=\"").append(SCALE_ACTS.get(segment)).append("\" phrase=\"масштабная проверка ").append(segment).append(' ').append(index).append("\" salience=\"1\" ttlMinutes=\"0\" priority=\"1\"/>\n");
			}
			content.append("\t</patterns>\n</humanizedV3SemanticSegment>\n");
			Files.writeString(path, content, StandardCharsets.UTF_8);
		}
		for (int segment = 0; segment < SCALE_ACTS.size(); segment++)
		{
			final Path path = root.resolve("conversation/humanized/v3/segments/scale/templates-" + segment + ".xml");
			Files.createDirectories(path.getParent());
			final StringBuilder content = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<humanizedV3ConversationSegment id=\"v3.scale.templates.").append(segment).append("\" version=\"3\" category=\"scale.templates\" mature=\"false\">\n\t<templates>\n");
			for (int index = 0; index < 1000; index++)
			{
				final int ordinal = (segment * 1000) + index;
				content.append("\t\t<template id=\"v3.scale.template.").append(String.format(java.util.Locale.ROOT, "%05d", ordinal)).append("\" act=\"").append(SCALE_ACTS.get(segment)).append("\" band=\"").append(BANDS.get(index % BANDS.size())).append("\" register=\"").append((index & 1) == 0 ? "NEUTRAL" : "CASUAL").append("\" profanity=\"NONE\" text=\"Синтетический ответ номер ").append(ordinal).append(".\"/>\n");
			}
			content.append("\t</templates>\n</humanizedV3ConversationSegment>\n");
			Files.writeString(path, content, StandardCharsets.UTF_8);
		}
	}

	private static void rejected(PhantomTestContext context, String relative, Mutation mutation, String category) throws Exception
	{
		final Path root = copyHumanizedData(context);
		try
		{
			final Path target = root.resolve(relative);
			Files.writeString(target, mutation.apply(Files.readString(target, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
			final IllegalArgumentException failure = PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomHumanizedCatalog.loadV3(root, false), "Invalid v3 artifact was accepted: " + category);
			PhantomAssertions.assertTrue((failure.getMessage() != null) && !failure.getMessage().isBlank(), "V3 rejection lacked a useful category: " + category);
		}
		finally
		{
			deleteTree(root);
		}
	}

	private static Path copyHumanizedData(PhantomTestContext context) throws Exception
	{
		final Path source = context.moduleRoot().resolve("dist/game/data/phantoms");
		final Path target = Files.createTempDirectory("phantom-post002-v3-");
		for (Path path : Files.walk(source).filter(Files::isRegularFile).filter(path -> path.toString().contains("humanized") || path.getFileName().toString().startsWith("my-")).toList())
		{
			final Path destination = target.resolve(source.relativize(path).toString());
			Files.createDirectories(destination.getParent());
			Files.copy(path, destination);
		}
		return target;
	}

	private static void deleteTree(Path root) throws Exception
	{
		if (!Files.exists(root))
		{
			return;
		}
		for (Path path : Files.walk(root).sorted(Comparator.reverseOrder()).toList())
		{
			Files.deleteIfExists(path);
		}
	}

	@FunctionalInterface
	private interface Mutation
	{
		String apply(String source);
	}
}

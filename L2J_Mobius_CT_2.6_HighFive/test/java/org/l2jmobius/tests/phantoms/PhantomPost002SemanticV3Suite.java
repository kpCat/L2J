/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Variation;

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
	}

	private static PhantomHumanizedCatalog.Selection selectVoice(PhantomHumanizedCatalog catalog, String act, RelationshipBand band, String voice, long selector, Set<String> recent)
	{
		return catalog.select(act, band, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Имя", "", "", "", voice, selector, recent);
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

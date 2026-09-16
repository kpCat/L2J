/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ClassAliasResolution;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Gender;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Match;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RuntimeIdentity;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Selection;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Variation;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService;

/** Focused DB-free acceptance for the additive L2-QOL-007 humanized v2 family. */
public final class PhantomHumanizedSemanticV2Qol007Suite implements PhantomTestSuite
{
	private static final long SEED = 70000701L;
	private static final String FUNCTIONAL_SEMANTIC_V1_SHA256 = "16c749b9e151e7d5fe7d702989a71dfc2ab3eedde9fa103c40b7d01a36e66a18";
	private static final String FUNCTIONAL_CORPUS_V1_SHA256 = "2b7676bccfd4395c267bc298e2f2c8dae265e23cee76d76853504bf7172f935e";

	@Override
	public String id()
	{
		return "qol-semantic-v2";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "QOL-007 focused suite used the wrong seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-versioned-content-addressed-family-and-full-class-census", context ->
		{
			final PhantomHumanizedCatalog v1 = catalog(context, false, false);
			final PhantomHumanizedCatalog first = catalog(context, true, true);
			final PhantomHumanizedCatalog second = catalog(context, true, true);
			PhantomAssertions.assertEquals("high-five-ru-humanized-v1", v1.familyId(), "Humanized v1 family identity changed.");
			PhantomAssertions.assertEquals(1, v1.familyVersion(), "Humanized v1 version changed.");
			PhantomAssertions.assertEquals("high-five-ru-humanized-v2", first.familyId(), "Humanized v2 family identity is not explicit.");
			PhantomAssertions.assertEquals(2, first.familyVersion(), "Humanized v2 version is not explicit.");
			PhantomAssertions.assertEquals(first.coreHash(), second.coreHash(), "Repeated v2 core loads are not deterministic.");
			PhantomAssertions.assertEquals(first.customHash(), second.customHash(), "Repeated v2 custom loads are not deterministic.");
			PhantomAssertions.assertEquals(first.combinedHash(), second.combinedHash(), "Repeated v2 authority hashes differ.");
			PhantomAssertions.assertEquals(PlayerClass.values().length, first.classCount(), "V2 does not cover the canonical PlayerClass catalog exactly.");
			PhantomAssertions.assertTrue(first.classAliasCount() >= PlayerClass.values().length, "Canonical class aliases are incomplete.");
			PhantomAssertions.assertTrue(first.identityPatternCount() >= 12, "Identity phrase coverage is too small.");
			PhantomAssertions.assertTrue(first.patternCount() >= (v1.patternCount() + 48), "V2 social understanding did not materially expand.");
			PhantomAssertions.assertTrue(first.templateCount() >= (v1.templateCount() + 90), "V2 social response variation did not materially expand.");
			context.record("qol007.familyId", first.familyId());
			context.record("qol007.coreHash", first.coreHash());
			context.record("qol007.customHash", first.customHash());
			context.record("qol007.combinedHash", first.combinedHash());
			context.record("qol007.classCount", first.classCount());
			context.record("qol007.classAliasCount", first.classAliasCount());
			context.record("qol007.patternCount", first.patternCount());
			context.record("qol007.templateCount", first.templateCount());
		});

		registry.add("02-canonical-name-gender-class-and-bounded-role-ambiguity", context ->
		{
			final PhantomHumanizedCatalog catalog = catalog(context, true, true);
			final RuntimeIdentity warlock = identity(Gender.MALE, PlayerClass.WARLOCK, "Рагнар", "Котовод Рагнар");
			final RuntimeIdentity arcana = identity(Gender.MALE, PlayerClass.ARCANA_LORD, "Рагнар", "Котовод Рагнар");
			final RuntimeIdentity woman = identity(Gender.FEMALE, PlayerClass.CARDINAL, "Анна", "Лекарь Анна");
			PhantomAssertions.assertEquals("identity.name.reply", identityMatch(catalog, "как тебя зовут?", warlock).act(), "Own-name query was not typed as identity.");
			final Selection name = select(catalog, identityMatch(catalog, "как тебя зовут?", warlock), warlock.displayName(), 0, Set.of());
			PhantomAssertions.assertTrue(name.text().contains("Котовод Рагнар"), "Own-name answer did not use the current canonical display name.");
			PhantomAssertions.assertEquals("парень", identityMatch(catalog, "ты парень или девушка?", warlock).value(), "Male identity contradicted canonical Player sex.");
			PhantomAssertions.assertEquals("девушка", identityMatch(catalog, "ты мужчина?", woman).value(), "Female identity contradicted canonical Player sex.");
			PhantomAssertions.assertEquals("identity.gender.deny", identityMatch(catalog, "ты мужчина?", woman).act(), "Female fixture confirmed a male assertion.");
			PhantomAssertions.assertEquals("варлок", identityMatch(catalog, "какой у тебя класс?", warlock).value(), "Current Warlock class was not sourced from canonical identity.");
			PhantomAssertions.assertEquals("лорд Арканы", identityMatch(catalog, "какой у тебя класс?", arcana).value(), "Changed active class did not change the answer.");
			PhantomAssertions.assertEquals("identity.role.confirm", identityMatch(catalog, "ты котовод?", warlock).act(), "Warlock was not recognized in the bounded cat-summoner line.");
			PhantomAssertions.assertEquals("identity.role.confirm", identityMatch(catalog, "ты варлок?", arcana).act(), "Arcana Lord was not recognized in the operator's bounded Warlock line alias.");
			final RuntimeIdentity elemental = identity(Gender.FEMALE, PlayerClass.ELEMENTAL_MASTER, "Эльза", "Эльза");
			PhantomAssertions.assertEquals("identity.role.deny", identityMatch(catalog, "ты котовод?", elemental).act(), "A non-cat summoner was falsely accepted as the cat-summoner line.");
			for (String alias : Set.of("танк", "маг", "суммонер", "дд", "лучник", "хилер", "саппорт", "камаэль", "биш", "варлок", "котовод"))
			{
				final ClassAliasResolution resolution = catalog.classAlias(alias).orElseThrow();
				PhantomAssertions.assertFalse(resolution.exact(), "Generic/bounded role alias chose an arbitrary exact class: " + alias);
			}
			for (String alias : Set.of("паладин", "дуэлист", "саг", "архимаг", "аркана лорд", "кардинал", "берс"))
			{
				PhantomAssertions.assertTrue(catalog.classAlias(alias).orElseThrow().exact(), "Unambiguous representative class alias was not exact: " + alias);
			}
			final RuntimeIdentity unknown = new RuntimeIdentity(1, 101, "Сбой", "Сбой", Gender.MALE, 999, "UNKNOWN");
			PhantomAssertions.assertTrue(catalog.understandIdentity("какой у тебя класс", unknown).isEmpty(), "Unknown runtime class ID did not fail closed.");
			final RuntimeIdentity mismatch = new RuntimeIdentity(1, 101, "Сбой", "Сбой", Gender.MALE, PlayerClass.WARLOCK.getId(), PlayerClass.ARCANA_LORD.name());
			PhantomAssertions.assertTrue(catalog.understandIdentity("какой у тебя класс", mismatch).isEmpty(), "Mismatched runtime class identity did not fail closed.");
			context.record("qol007.catSummonerClassIds", catalog.classAlias("котовод").orElseThrow().classIds().toString());
		});

		registry.add("03-variation-recent-rotation-safety-gates-and-custom-precedence", context ->
		{
			final PhantomHumanizedCatalog catalog = catalog(context, true, true);
			final Set<String> variants = new HashSet<>();
			for (int selector = 0; selector < 12; selector++)
			{
				variants.add(catalog.select("greet.reply", RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Тихон", "", "", "", selector, Set.of()).text());
			}
			PhantomAssertions.assertTrue(variants.size() >= 6, "Common v2 greeting still has the old three-line feel.");
			final Selection deterministicA = catalog.select("humor.reply", RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Тихон", "", "", "", 4, Set.of());
			final Selection deterministicB = catalog.select("humor.reply", RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Тихон", "", "", "", 4, Set.of());
			PhantomAssertions.assertEquals(deterministicA, deterministicB, "Same immutable selector/context did not produce the same v2 response.");
			final Selection rotated = catalog.select("humor.reply", RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Тихон", "", "", "", 4, Set.of(deterministicA.responseHash()));
			PhantomAssertions.assertFalse(deterministicA.responseHash().equals(rotated.responseHash()), "Recent-response suppression did not rotate v2 output.");
			PhantomAssertions.assertFalse(PhantomHumanizedConversationService.relationshipActEligible("teasing.reply", RelationshipBand.NEUTRAL), "Trusted teasing gate weakened.");
			PhantomAssertions.assertTrue(PhantomHumanizedConversationService.relationshipActEligible("teasing.reply", RelationshipBand.TRUSTED), "Trusted teasing gate was lost.");
			PhantomAssertions.assertFalse(PhantomHumanizedConversationService.relationshipActEligible("sarcasm.reply", RelationshipBand.TRUSTED), "Rival sarcasm gate weakened.");
			final Selection clean = catalog.select("humor.reply", RelationshipBand.FAMILIAR, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, true, "Тихон", "", "", "", 0, Set.of());
			final Selection contextual = catalog.select("humor.reply", RelationshipBand.FAMILIAR, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false, true, "Тихон", "", "", "", 0, Set.of());
			PhantomAssertions.assertFalse(clean.profanityUsed(), "Profanity appeared in NONE mode.");
			PhantomAssertions.assertTrue(contextual.profanityUsed(), "Existing contextual profanity gate no longer admits its bounded case.");
			for (int selector = 0; selector < 10; selector++)
			{
				PhantomAssertions.assertFalse(catalog.select("flirt.light", RelationshipBand.TRUSTED, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, true, false, "Тихон", "", "", "", selector, Set.of()).matureUsed(), "Mature dialogue escaped the private-channel gate.");
			}
			PhantomAssertions.assertTrue(catalog.select("flirt.light", RelationshipBand.TRUSTED, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, true, true, "Тихон", "", "", "", 3, Set.of()).matureUsed(), "Accepted private+trusted mature gate no longer reaches its opt-in template.");
			final RuntimeIdentity identity = identity(Gender.MALE, PlayerClass.WARLOCK, "Тихон", "Тихон");
			PhantomAssertions.assertTrue(catalog.understand("привет, где взять адену").isEmpty(), "V2 swallowed a mixed functional command.");
			PhantomAssertions.assertTrue(catalog.understandIdentity("ты варлок, пригласи меня", identity).isEmpty(), "V2 identity swallowed a mixed party command.");
			customOverride(context);
			context.record("qol007.greetingVariants", variants.size());
		});

		registry.add("04-strict-loader-negative-matrix", context ->
		{
			rejected(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", source -> source.replace("high-five-ru-humanized-semantic-v2", "high-five-ru-humanized-semantic-wrong"), "pack ID");
			rejected(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", source -> source.replace("version=\"2\"", "version=\"3\""), "version");
			rejected(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", source -> "<?xml version=\"1.0\" encoding=\"UTF-8\"?><humanizedSemanticPack", "XML");
			rejected(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", source -> source.replace("<humanizedSemanticPack", "<!DOCTYPE humanizedSemanticPack [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><humanizedSemanticPack"), "XML");
			rejectedBytes(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", new byte[] {(byte) 0xc3, (byte) 0x28}, "UTF-8");
			rejectedBytes(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", new byte[262145], "size");
			rejected(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", source -> source.replace("парень;мужчина", "парень;парень"), "alias");
			rejected(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", source -> source.replace("id=\"0\" canonical=\"FIGHTER\"", "id=\"999\" canonical=\"FIGHTER\""), "PlayerClass");
			rejected(context, "semantic/humanized/high-five-ru-humanized-semantic-v2.xml", source -> source.replace("value=\"MALE\"", "value=\"OTHER\""), "enum");
			rejected(context, "conversation/humanized/high-five-ru-humanized-conversation-v2.xml", source -> source.replace("id=\"v2.greet.05\"", "id=\"v2.greet.04\""), "collision");
			rejected(context, "conversation/humanized/high-five-ru-humanized-conversation-v2.xml", source -> source.replace("Здорово, {name}. Как твои дела?", "Привет! Рад, что заглянул."), "duplicate normalized response");
			PhantomAssertions.assertTrue(catalog(context, true, true).understand("а".repeat(257)).isEmpty(), "Oversize humanized input did not fail closed.");
			context.record("qol007.negativeCases", 12);
		});

		registry.add("05-functional-v1-freeze-and-read-only-runtime-glue", context ->
		{
			final Path semantic = context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml");
			final Path corpus = context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv");
			PhantomAssertions.assertEquals(FUNCTIONAL_SEMANTIC_V1_SHA256, sha256(semantic), "Frozen functional semantic-v1 XML changed.");
			PhantomAssertions.assertEquals(FUNCTIONAL_CORPUS_V1_SHA256, sha256(corpus), "Frozen functional semantic-v1 corpus changed.");
			final String conversation = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java"), StandardCharsets.UTF_8);
			final int functional = conversation.indexOf("_semantic.understand(work._election.text(), work._semanticContext)");
			final int humanized = conversation.indexOf("_humanized.plan(new Request");
			PhantomAssertions.assertTrue((functional >= 0) && (humanized > functional), "Functional semantic handoff no longer precedes humanized v2.");
			PhantomAssertions.assertTrue(conversation.contains("decision.text(), null, DeliveryPolicy.SEND"), "Humanized response gained gameplay action ownership.");
			final String contextPort = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/L2jPhantomConversationContextPort.java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(contextPort.contains("observer.getAppearance().getVisibleName()") && contextPort.contains("observer.getAppearance().isFemale()") && contextPort.contains("observer.getPlayerClass()"), "Runtime identity is not copied from canonical live Player state.");
			PhantomAssertions.assertFalse(contextPort.contains("setClass") || contextPort.contains("setSex") || contextPort.contains("setName"), "Read-only identity glue introduced a Player mutation.");
			final String system = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(system.contains("PhantomHumanizedCatalog.loadV3") && system.contains("_settings.conversation().humanizedEnabled()"), "Production v3 selection bypassed the shipped social-reply feature gate.");
			context.record("qol007.functionalSemanticV1Sha256", sha256(semantic));
			context.record("qol007.functionalCorpusV1Sha256", sha256(corpus));
		});
	}

	private static RuntimeIdentity identity(Gender gender, PlayerClass playerClass, String characterName, String displayName)
	{
		return new RuntimeIdentity(1, 101, characterName, displayName, gender, playerClass.getId(), playerClass.name());
	}

	private static Match identityMatch(PhantomHumanizedCatalog catalog, String text, RuntimeIdentity identity)
	{
		return catalog.understandIdentity(text, identity).orElseThrow(() -> new AssertionError("Identity phrase was not understood: " + text));
	}

	private static Selection select(PhantomHumanizedCatalog catalog, Match match, String displayName, long selector, Set<String> recent)
	{
		return catalog.select(match.act(), RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, true, displayName, match.value(), "", "", selector, recent);
	}

	private static PhantomHumanizedCatalog catalog(PhantomTestContext context, boolean custom, boolean v2)
	{
		final Path root = context.moduleRoot().resolve("dist/game/data/phantoms");
		return v2 ? PhantomHumanizedCatalog.loadV2(root, custom) : PhantomHumanizedCatalog.load(root, custom);
	}

	private static void customOverride(PhantomTestContext context) throws Exception
	{
		final Path root = copyHumanizedData(context);
		try
		{
			final Path phrases = root.resolve("conversation/custom/my-phrases.xml");
			Files.writeString(phrases, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<phrases version=\"1\">\n\t<template id=\"v2.greet.04\" act=\"greet.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" text=\"Прямой операторский ответ v2.\" override=\"true\"/>\n</phrases>\n", StandardCharsets.UTF_8);
			final PhantomHumanizedCatalog overridden = PhantomHumanizedCatalog.loadV2(root, true);
			final Selection selection = overridden.select("greet.reply", RelationshipBand.UNKNOWN, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, false, "Тихон", "", "", "", 3, Set.of());
			PhantomAssertions.assertEquals("v2.greet.04", selection.templateId(), "Custom v2 override did not retain direct stable-ID precedence.");
			PhantomAssertions.assertEquals("Прямой операторский ответ v2.", selection.text(), "Custom v2 override text was not selected.");
		}
		finally
		{
			deleteTree(root);
		}
	}

	private static void rejected(PhantomTestContext context, String relative, Mutation mutation, String category) throws Exception
	{
		final Path root = copyHumanizedData(context);
		try
		{
			final Path target = root.resolve(relative);
			Files.writeString(target, mutation.apply(Files.readString(target, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
			final IllegalArgumentException failure = PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomHumanizedCatalog.loadV2(root, false), "Invalid v2 artifact was accepted: " + category);
			PhantomAssertions.assertTrue((failure.getMessage() != null) && !failure.getMessage().isBlank(), "V2 rejection had no useful category: " + category);
		}
		finally
		{
			deleteTree(root);
		}
	}

	private static void rejectedBytes(PhantomTestContext context, String relative, byte[] bytes, String category) throws Exception
	{
		final Path root = copyHumanizedData(context);
		try
		{
			Files.write(root.resolve(relative), bytes);
			final IllegalArgumentException failure = PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomHumanizedCatalog.loadV2(root, false), "Invalid v2 bytes were accepted: " + category);
			PhantomAssertions.assertTrue((failure.getMessage() != null) && !failure.getMessage().isBlank(), "V2 byte rejection had no useful category: " + category);
		}
		finally
		{
			deleteTree(root);
		}
	}

	private static Path copyHumanizedData(PhantomTestContext context) throws Exception
	{
		final Path source = context.moduleRoot().resolve("dist/game/data/phantoms");
		final Path target = Files.createTempDirectory("phantom-qol007-data-");
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
		for (Path path : Files.walk(root).sorted(Comparator.reverseOrder()).toList())
		{
			Files.deleteIfExists(path);
		}
	}

	private static String sha256(Path path) throws Exception
	{
		return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
	}

	@FunctionalInterface
	private interface Mutation
	{
		String apply(String source);
	}
}

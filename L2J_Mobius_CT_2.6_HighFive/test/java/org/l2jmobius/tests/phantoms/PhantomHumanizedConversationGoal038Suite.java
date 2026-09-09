/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchHandle;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveIngressPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveredObservation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService.ContextSnapshot;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.FactKind;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Match;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.ProfanityMode;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Register;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.RelationshipBand;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedCatalog.Variation;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService.Decision;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService.Request;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore.PersonalState;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomPersonalConversationStore.StoredState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding.Hashes;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputContext;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingStatus;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap.BootstrapResult;
import org.l2jmobius.tests.phantoms.PhantomSocialTestDoubles.MemoryStore;

public final class PhantomHumanizedConversationGoal038Suite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG,
		BEHAVIOR,
		PERSISTENCE,
		COMPOSITION
	}

	private static final long SEED = 38003801L;
	private static final Hashes HASHES = new Hashes("A".repeat(64), "B".repeat(64), "C".repeat(64));
	private final Mode _mode;
	private PhantomProfileRepository _profiles;
	private PhantomProfile _ownedProfile;

	public PhantomHumanizedConversationGoal038Suite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "humanized-conversation-goal038-" + _mode.name().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal038 focused suite used the wrong seed.");
		if ((_mode != Mode.PERSISTENCE) && (_mode != Mode.COMPOSITION))
		{
			return;
		}
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		final BootstrapResult bootstrap = PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		_profiles = PhantomProfileRepository.open();
		_ownedProfile = _profiles.create(null);
		context.record("goal038.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("goal038.schemaAggregateSha256", bootstrap.schemaSnapshot().aggregateSha256());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			if ((_profiles != null) && (_ownedProfile != null))
			{
				final long profileId = _ownedProfile.profileId();
				_profiles.find(_ownedProfile.profileId()).ifPresent(profile -> _profiles.delete(profile.profileId(), profile.rowVersion()));
				PhantomAssertions.assertTrue(_profiles.find(profileId).isEmpty(), "Goal038 owned profile survived cleanup.");
				try (Connection connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id = ?"))
				{
					statement.setLong(1, profileId);
					try (var result = statement.executeQuery())
					{
						PhantomAssertions.assertTrue(result.next() && (result.getInt(1) == 0), "Goal038 component rows survived owned-profile cleanup.");
					}
				}
				context.record("goal038.cleanupRows", 0);
			}
		}
		finally
		{
			if ((_mode == Mode.PERSISTENCE) || (_mode == Mode.COMPOSITION))
			{
				DatabaseFactory.close();
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CATALOG -> catalogTests(registry);
			case BEHAVIOR -> behaviorTests(registry);
			case PERSISTENCE -> persistenceTests(registry);
			case COMPOSITION -> productionTests(registry);
		}
	}

	private static void catalogTests(PhantomTestRegistry registry)
	{
		registry.add("01-core-custom-load-hash-corpus-and-coverage", context ->
		{
			final PhantomHumanizedCatalog first = catalog(context, true);
			final PhantomHumanizedCatalog second = catalog(context, true);
			PhantomAssertions.assertEquals(first.combinedHash(), second.combinedHash(), "Humanized combined hash is not deterministic.");
			PhantomAssertions.assertEquals(64, first.coreHash().length(), "Humanized core is not content-addressed.");
			PhantomAssertions.assertEquals(64, first.customHash().length(), "Humanized custom layer is not content-addressed.");
			PhantomAssertions.assertTrue(first.corpusCases() >= 80, "Humanized corpus is below its required size.");
			PhantomAssertions.assertTrue(first.patternCount() >= 50, "Humanized topic coverage collapsed.");
			PhantomAssertions.assertTrue(first.templateCount() >= 60, "Humanized response variation collapsed.");
			PhantomAssertions.assertEquals(16, first.limits().subjects(), "Personal subject bound changed.");
			PhantomAssertions.assertEquals(48, first.limits().facts(), "Personal fact bound changed.");
			context.record("goal038.coreHash", first.coreHash());
			context.record("goal038.customHash", first.customHash());
			context.record("goal038.combinedHash", first.combinedHash());
			context.record("goal038.corpusCases", first.corpusCases());
			context.record("goal038.topicCount", first.topicCount());
			context.record("goal038.actCount", first.actCount());
			context.record("goal038.patternCount", first.patternCount());
			context.record("goal038.templateCount", first.templateCount());
			context.record("goal038.aliasCount", first.aliasCount());
			context.record("goal038.profanityCount", first.profanityCount());
			context.record("goal038.matureTemplateCount", first.matureTemplateCount());
		});

		registry.add("02-custom-override-is-direct-strict-and-hash-visible", context ->
		{
			final Path temporary = copyPhantomData(context);
			try
			{
				final PhantomHumanizedCatalog before = PhantomHumanizedCatalog.load(temporary, true);
				final Path phrases = temporary.resolve("conversation/custom/my-phrases.xml");
				final Path slang = temporary.resolve("semantic/custom/my-slang.xml");
				Files.writeString(slang, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<slang version=\"1\">\n\t<alias from=\"хуман\" to=\"привет\" override=\"false\"/>\n</slang>\n", StandardCharsets.UTF_8);
				Files.writeString(phrases, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<phrases version=\"1\">\n\t<template id=\"greet.01\" act=\"greet.reply\" band=\"UNKNOWN\" register=\"NEUTRAL\" profanity=\"NONE\" text=\"Мой прямой пользовательский ответ.\" override=\"true\"/>\n</phrases>\n", StandardCharsets.UTF_8);
				final PhantomHumanizedCatalog after = PhantomHumanizedCatalog.load(temporary, true);
				PhantomAssertions.assertEquals(before.coreHash(), after.coreHash(), "Custom edit changed the core authority hash.");
				PhantomAssertions.assertFalse(before.customHash().equals(after.customHash()), "Custom edit was not reflected in the custom hash.");
				PhantomAssertions.assertFalse(before.combinedHash().equals(after.combinedHash()), "Custom edit was not reflected in the combined hash.");
				PhantomAssertions.assertEquals("greeting", after.understand("хуман").orElseThrow().topic(), "Direct custom slang alias did not affect understanding.");
				PhantomAssertions.assertTrue(after.select("greet.reply", RelationshipBand.UNKNOWN, Register.NEUTRAL, ProfanityMode.NONE, Variation.LOW, false, false, "Тихон", "", "", "", 0, Set.of()).text().contains("пользовательский"), "Direct custom override did not affect response selection.");
				Files.writeString(phrases, Files.readString(phrases).replace(" override=\"true\"", " override=\"false\""), StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomHumanizedCatalog.load(temporary, true), "Custom collision without override=true was accepted.");
			}
			finally
			{
				deleteTree(temporary);
			}
		});

		registry.add("03-xxe-unknown-attribute-and-action-mixing-fail-closed", context ->
		{
			final Path temporary = copyPhantomData(context);
			try
			{
				final Path aliases = temporary.resolve("semantic/custom/my-ru-aliases.xml");
				Files.writeString(aliases, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE aliases [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>\n<aliases version=\"1\"><alias from=\"ку\" to=\"привет\" override=\"false\"/></aliases>", StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomHumanizedCatalog.load(temporary, true), "Humanized custom XXE was accepted.");
				Files.writeString(aliases, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<aliases version=\"1\" surprise=\"yes\"></aliases>", StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomHumanizedCatalog.load(temporary, true), "Unknown humanized XML attribute was accepted.");
			}
			finally
			{
				deleteTree(temporary);
			}
			final PhantomHumanizedCatalog catalog = catalog(context, true);
			PhantomAssertions.assertTrue(catalog.understand("привет").isPresent(), "Ordinary social greeting was not accepted.");
			PhantomAssertions.assertTrue(catalog.understand("привет, где взять адену").isEmpty(), "Mixed social/action input was swallowed by the humanized layer.");
			PhantomAssertions.assertTrue(catalog.understand("ты мне нравишься, пригласи меня").isEmpty(), "Mixed flirt/action input was swallowed by the humanized layer.");
		});

		registry.add("04-config-matrix-is-strict-and-mature-ships-off", context ->
		{
			final String source = Files.readString(context.moduleRoot().resolve("dist/game/config/Custom/PhantomPlayers.ini"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(source.contains("EnablePhantomSystem = False") && source.contains("EnablePhantomMatureConversation = False"), "Shipped Phantom or mature mode is not OFF.");
			final Path enabled = Files.createTempFile("phantom-goal038-enabled-", ".ini");
			try
			{
				Files.writeString(enabled, source.replace("EnablePhantomSystem = False", "EnablePhantomSystem = True"), StandardCharsets.UTF_8);
				final var settings = PhantomPlayersConfig.read(enabled);
				PhantomAssertions.assertTrue(settings.enabled() && settings.conversation().humanizedEnabled() && settings.conversation().customPackEnabled(), "Goal038 enabled defaults were not loaded.");
				PhantomAssertions.assertFalse(settings.conversation().matureEnabled(), "Mature conversation became enabled without opt-in.");
				Files.writeString(enabled, Files.readString(enabled).replace("PhantomConversationVariation = HIGH", "PhantomConversationVariation = RANDOM"), StandardCharsets.UTF_8);
				PhantomAssertions.assertFalse(PhantomPlayersConfig.read(enabled).enabled(), "Invalid humanized config enum did not fail closed.");
			}
			finally
			{
				Files.deleteIfExists(enabled);
			}
		});

		registry.add("05-functional-semantic-remains-first-and-humanized-cannot-propose-actions", context ->
		{
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java"), StandardCharsets.UTF_8);
			final int functional = source.indexOf("_semantic.understand(work._election.text(), work._semanticContext)");
			final int humanized = source.indexOf("_humanized.plan(new Request");
			PhantomAssertions.assertTrue((functional >= 0) && (humanized > functional), "Humanized planning no longer follows the accepted functional semantic authority.");
			PhantomAssertions.assertTrue(source.contains("work._descriptor.origin() == Origin.PHANTOM_SOCIAL ? silent") && source.contains("decision.text(), null, DeliveryPolicy.SEND"), "Generated social ingress can create a functional proposal/action.");
			PhantomAssertions.assertTrue(catalog(context, true).understand("где взять адену").isEmpty(), "Humanized catalog claimed a functional query.");
		});
	}

	private static void behaviorTests(PhantomTestRegistry registry)
	{
		registry.add("01-persona-relationship-and-response-variation-are-stable", context ->
		{
			final Fixture fixture = fixture(context);
			try
			{
				final Decision first = fixture.service().plan(request(1, SubjectRef.character(101), Origin.CLIENT_CHAT, "привет", 1000, "persona-a"));
				final Decision second = fixture.service().plan(request(1, SubjectRef.character(101), Origin.CLIENT_CHAT, "что нового", 1001, "persona-b"));
				PhantomAssertions.assertEquals(first.personaKey(), second.personaKey(), "Persona changed between follow-ups for one profile.");
				PhantomAssertions.assertEquals(RelationshipBand.UNKNOWN, first.relationshipBand(), "Neutral Social authority produced a fabricated relationship.");
				PhantomAssertions.assertEquals(RelationshipBand.TRUSTED, PhantomHumanizedConversationService.relationshipBand(Map.of("trust", 2000, "respect", 1500, "friendship", 1200)), "Trusted relationship projection is wrong.");
				PhantomAssertions.assertEquals(RelationshipBand.RIVAL, PhantomHumanizedConversationService.relationshipBand(Map.of("anger", 100, "rivalry", 1500)), "Rival relationship projection is wrong.");
				PhantomAssertions.assertEquals(RelationshipBand.HOSTILE, PhantomHumanizedConversationService.relationshipBand(Map.of("anger", 2500, "rivalry", 1500)), "Hostile relationship projection is wrong.");
				PhantomAssertions.assertFalse(PhantomHumanizedConversationService.relationshipActEligible("teasing.reply", RelationshipBand.HOSTILE), "Friendly teasing remained eligible for a hostile relationship.");
				PhantomAssertions.assertTrue(PhantomHumanizedConversationService.relationshipActEligible("teasing.reply", RelationshipBand.TRUSTED), "Warm teasing was not eligible for a trusted relationship.");
				PhantomAssertions.assertTrue(PhantomHumanizedConversationService.relationshipActEligible("sarcasm.reply", RelationshipBand.RIVAL), "Competitive sarcasm was not eligible for a rival relationship.");
				PhantomAssertions.assertFalse(PhantomHumanizedConversationService.relationshipActEligible("sarcasm.reply", RelationshipBand.UNKNOWN), "Competitive sarcasm leaked to an unknown relationship.");
				final Set<String> texts = new java.util.HashSet<>();
				for (int index = 0; index < 8; index++)
				{
					texts.add(fixture.service().plan(request(1, SubjectRef.character(101), Origin.CLIENT_CHAT, "расскажи шутку", 1010 + index, "variation-" + index)).text());
				}
				PhantomAssertions.assertTrue(texts.size() >= 3, "HIGH variation and anti-repeat did not expose three humor variants.");
				context.record("goal038.personaKey", first.personaKey());
				context.record("goal038.humorVariants", texts.size());
			}
			finally
			{
				fixture.close();
			}
		});

		registry.add("02-memory-recall-and-personal-game-personal-continuity", context ->
		{
			final Fixture fixture = fixture(context);
			final PhantomSemanticUnderstandingService semantic = semantic(context);
			try
			{
				final Decision personal = fixture.service().plan(request(1, SubjectRef.character(202), Origin.CLIENT_CHAT, "я люблю старый рок", 2000, "continuity-personal"));
				PhantomAssertions.assertTrue(personal.eligible(), "Personal preference was not handled.");
				final var game = semantic.understand("где взять адену", InputContext.empty());
				PhantomAssertions.assertEquals(UnderstandingStatus.ACCEPTED, game.status(), "Accepted functional intent was weakened by Goal038.");
				PhantomAssertions.assertEquals("item.acquire.query", game.selectedIntent(), "Functional game intent changed.");
				final Decision recalled = fixture.service().plan(request(1, SubjectRef.character(202), Origin.CLIENT_CHAT, "что я люблю", 2002, "continuity-recall"));
				PhantomAssertions.assertTrue(recalled.memoryUsed() && recalled.text().contains("старый рок"), "Personal memory was lost across the functional game turn.");
				PhantomAssertions.assertTrue(fixture.catalog().understand("где взять адену").isEmpty(), "Humanized layer claimed an accepted functional phrase.");
				context.record("goal038.functionalIntent", game.selectedIntent());
			}
			finally
			{
				semantic.beginStop();
				semantic.finishStop();
				fixture.close();
			}
		});

		registry.add("03-emotion-humor-profanity-and-mature-matrices", context ->
		{
			final PhantomHumanizedCatalog catalog = catalog(context, true);
			for (String text : List.of("мне грустно", "я наконец закончил проект", "ты меня раздражаешь", "извини", "давай мириться", "ты мне нравишься", "ну конечно ты лучший", "вот это да"))
			{
				PhantomAssertions.assertTrue(catalog.understand(text).isPresent(), "Required emotional act is absent: " + text);
			}
			final var none = catalog.select("irritation.reply", RelationshipBand.TENSE, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, true, "Тихон", "", "", "истории", 4096, Set.of());
			final var mild = catalog.select("irritation.reply", RelationshipBand.TENSE, Register.CASUAL, ProfanityMode.MILD, Variation.HIGH, false, true, "Тихон", "", "", "истории", 0, Set.of());
			final var contextual = catalog.select("irritation.reply", RelationshipBand.TENSE, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false, true, "Тихон", "", "", "истории", 0, Set.of());
			PhantomAssertions.assertFalse(none.profanityUsed(), "NONE profanity mode leaked an expression.");
			PhantomAssertions.assertTrue(mild.profanityUsed() && !mild.text().contains("сука"), "MILD profanity mode used the wrong intensity.");
			PhantomAssertions.assertTrue(contextual.profanityUsed() && contextual.text().contains("сука"), "CONTEXTUAL profanity did not activate in an irritation context.");
			for (String contextualAct : List.of("achievement.congratulate", "humor.reply", "surprise.reply", "sarcasm.reply"))
			{
				final RelationshipBand band = contextualAct.equals("sarcasm.reply") ? RelationshipBand.RIVAL : RelationshipBand.FAMILIAR;
				PhantomAssertions.assertTrue(catalog.select(contextualAct, band, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false, true, "Тихон", "", "", "истории", 0, Set.of()).profanityUsed(), "CONTEXTUAL profanity matrix missed " + contextualAct + '.');
			}
			PhantomAssertions.assertFalse(catalog.select("smalltalk.reply", RelationshipBand.NEUTRAL, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false, true, "Тихон", "", "", "истории", 0, Set.of()).profanityUsed(), "Low-intensity neutral context became profanity spam.");
			final var firstProfane = catalog.select("irritation.reply", RelationshipBand.TENSE, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false, true, "Тихон", "", "", "истории", 0, Set.of());
			final var secondProfane = catalog.select("irritation.reply", RelationshipBand.TENSE, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false, true, "Тихон", "", "", "истории", 0, Set.of(firstProfane.responseHash()));
			PhantomAssertions.assertFalse(firstProfane.responseHash().equals(secondProfane.responseHash()), "Contextual profanity repeated the exact output despite alternatives.");
			boolean matureOffLeak = false;
			boolean matureOnReachable = false;
			for (int selector = 0; selector < 512; selector++)
			{
				matureOffLeak |= catalog.select("flirt.light", RelationshipBand.TRUSTED, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, false, true, "Тихон", "", "", "истории", selector, Set.of()).matureUsed();
				matureOnReachable |= catalog.select("flirt.light", RelationshipBand.TRUSTED, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, true, true, "Тихон", "", "", "истории", selector, Set.of()).matureUsed();
			}
			PhantomAssertions.assertFalse(matureOffLeak, "Mature phrase leaked while opt-in was OFF.");
			PhantomAssertions.assertTrue(matureOnReachable, "Mature opt-in did not expose its private trusted template.");
			PhantomAssertions.assertFalse(catalog.select("flirt.light", RelationshipBand.TRUSTED, Register.CASUAL, ProfanityMode.NONE, Variation.HIGH, true, false, "Тихон", "", "", "истории", 3, Set.of()).matureUsed(), "Mature phrase leaked outside WHISPER/private scope.");
		});

		registry.add("04-phantom-to-phantom-turn-budget-stops-ping-pong", context ->
		{
			final Fixture fixture = fixture(context);
			try
			{
				final ChatObservationService observation = ChatObservationService.getInstance();
				try (DispatchHandle dispatch = observation.openGeneratedSocialDispatch(1001, "Фантом А", ChatType.GENERAL, "", "привет", 3_000_000L, 1002))
				{
					PhantomAssertions.assertEquals(Origin.PHANTOM_SOCIAL, dispatch.descriptor().origin(), "Generated social egress lost its distinct origin.");
					final var captured = observation.capturePacket(1001, ChatType.GENERAL, "привет");
					PhantomAssertions.assertEquals(dispatch.descriptor(), captured, "Actual ChatObservation capture did not preserve social provenance.");
					observation.publishDelivered(captured, 1001, ChatType.GENERAL, "привет", 1002, "Фантом Б");
					PhantomAssertions.assertTrue(dispatch.expectedCounterpartDelivered(), "Bounded social dispatch did not prove its expected counterpart delivery.");
				}
				PhantomAssertions.assertTrue(fixture.service().plan(request(1, SubjectRef.phantom(1), Origin.PHANTOM_SOCIAL, "привет", 2999, "self-loop")).suppressed(), "A Phantom was allowed to reply to itself.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> request(1, SubjectRef.phantom(2), Origin.PHANTOM_GENERATED, "привет", 2999, "action-seed"), "Ordinary generated/action output was accepted as a social echo seed.");
				final List<Decision> chain = List.of(
					fixture.service().plan(request(2, SubjectRef.phantom(1), Origin.PHANTOM_SOCIAL, "привет", 3000, "loop-1")),
					fixture.service().plan(request(1, SubjectRef.phantom(2), Origin.PHANTOM_SOCIAL, "привет", 3001, "loop-2")),
					fixture.service().plan(request(2, SubjectRef.phantom(1), Origin.PHANTOM_SOCIAL, "привет", 3002, "loop-3")));
				PhantomAssertions.assertTrue(chain.get(0).eligible() && !chain.get(0).suppressed(), "First generated social turn was not eligible.");
				PhantomAssertions.assertTrue(chain.get(1).eligible() && !chain.get(1).suppressed(), "Second generated social turn was not eligible.");
				PhantomAssertions.assertTrue(chain.get(2).eligible() && chain.get(2).suppressed(), "Generated social chain exceeded the explicit turn budget.");
				PhantomAssertions.assertTrue(chain.get(2).evidence().stream().anyMatch(value -> value.key().equals("humanized.loop_guard")), "Loop stop has no turn-budget evidence.");
				context.record("goal038.generatedRepliesBeforeStop", 2);
				context.record("goal038.generatedTurnBudgetPerSide", fixture.catalog().limits().generatedTurnBudget());
			}
			finally
			{
				fixture.close();
			}
		});

		registry.add("05-personal-codec-ttl-eviction-and-conflict-retry-are-bounded", context ->
		{
			final PhantomHumanizedCatalog catalog = catalog(context, true);
			PersonalState state = PersonalState.empty(catalog.combinedHash(), 0);
			for (int subject = 1; subject <= 16; subject++)
			{
				for (FactKind kind : List.of(FactKind.LIKE, FactKind.HOBBY, FactKind.MOOD))
				{
					final Match match = new Match("test." + kind.name().toLowerCase(java.util.Locale.ROOT), "likes", "preference.ack", kind, null, "v" + subject + kind.ordinal(), 100 + kind.ordinal(), kind == FactKind.MOOD ? 1 : 0, PhantomHumanizedCatalog.sha256("fact|" + subject + '|' + kind));
					state = PhantomPersonalConversationStore.apply(state, "character.object:" + subject, match, "", Origin.CLIENT_CHAT, 100, catalog.limits());
				}
			}
			final byte[] encoded = PhantomPersonalConversationStore.encode(state);
			PhantomAssertions.assertTrue(encoded.length <= 4096, "conversation.personal exceeded the generic payload bound.");
			PhantomAssertions.assertEquals(state, PhantomPersonalConversationStore.decode(encoded), "conversation.personal round-trip changed facts.");
			final PersonalState expired = PhantomPersonalConversationStore.prepare(state, catalog.combinedHash(), 101);
			PhantomAssertions.assertTrue(expired.subjects().stream().flatMap(value -> value.facts().stream()).noneMatch(value -> value.kind() == FactKind.MOOD), "TTL mood facts survived their exact expiry boundary.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomPersonalConversationStore.decode(Arrays.copyOf(encoded, encoded.length - 1)), "Truncated conversation.personal was accepted.");

			final InMemoryPersonal persistence = new InMemoryPersonal();
			persistence.addProfiles(1, 2);
			persistence.conflictNext();
			final Fixture fixture = fixture(context, persistence);
			try
			{
				final Decision decision = fixture.service().plan(request(1, SubjectRef.character(303), Origin.CLIENT_CHAT, "я люблю кофе", 4000, "cas-retry"));
				PhantomAssertions.assertTrue(decision.eligible() && (persistence.conflicts() == 1), "One optimistic conflict did not reload and retry exactly once.");
			}
			finally
			{
				fixture.close();
			}
			context.record("goal038.personalPayloadBytes", encoded.length);
		});

		registry.add("06-repeated-social-traffic-keeps-state-and-runtime-bounded", context ->
		{
			final PhantomHumanizedCatalog catalog = catalog(context, true);
			PersonalState state = PersonalState.empty(catalog.combinedHash(), 0);
			final Match greeting = catalog.understand("привет").orElseThrow();
			for (int index = 0; index < 4096; index++)
			{
				state = PhantomPersonalConversationStore.apply(state, "character.object:" + ((index % 64) + 1), greeting, PhantomHumanizedCatalog.sha256("bounded-response|" + index), Origin.CLIENT_CHAT, index, catalog.limits());
			}
			PhantomAssertions.assertTrue(state.subjects().size() <= catalog.limits().subjects(), "Repeated traffic grew the personal subject map beyond its cap.");
			PhantomAssertions.assertTrue(state.subjects().stream().allMatch(subject -> subject.recentResponseHashes().size() <= catalog.limits().recentResponses()), "Recent response hashes exceeded their cap.");
			PhantomAssertions.assertTrue(state.subjects().stream().mapToInt(subject -> subject.facts().size()).sum() <= catalog.limits().facts(), "Repeated traffic grew facts beyond their cap.");
			final int boundedPayloadBytes = PhantomPersonalConversationStore.encode(state).length;
			PhantomAssertions.assertTrue(boundedPayloadBytes <= 4096, "Repeated traffic exceeded the generic component payload envelope.");
			for (Path source : List.of(
				context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedCatalog.java"),
				context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomHumanizedConversationService.java"),
				context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/humanized/PhantomPersonalConversationStore.java")))
			{
				final String java = Files.readString(source, StandardCharsets.UTF_8);
				PhantomAssertions.assertFalse(java.contains("new Thread(") || java.contains("new Timer(") || java.contains("ExecutorService") || java.contains("CompletableFuture"), "Humanized layer introduced an executor/thread/Future/timer: " + source.getFileName());
			}
			context.record("goal038.repeatedTrafficMessages", 4096);
			context.record("goal038.subjectsAfterTraffic", state.subjects().size());
			context.record("goal038.boundedTrafficPayloadBytes", boundedPayloadBytes);
		});
	}

	private void persistenceTests(PhantomTestRegistry registry)
	{
		registry.add("01-generic-component-cas-restart-and-no-new-schema", context ->
		{
			final PhantomHumanizedCatalog catalog = catalog(context, true);
			final PhantomPersonalConversationStore first = PhantomPersonalConversationStore.production(_profiles);
			final Match match = catalog.understand("я люблю дождь").orElseThrow();
			final PersonalState initial = PhantomPersonalConversationStore.apply(PersonalState.empty(catalog.combinedHash(), 5000), "character.object:404", match, PhantomHumanizedCatalog.sha256("response"), Origin.CLIENT_CHAT, 5000, catalog.limits());
			final StoredState inserted = first.save(_ownedProfile.profileId(), -1, initial);
			PhantomAssertions.assertEquals(PhantomPersonalConversationStore.COMPONENT_TYPE, _profiles.findComponent(_ownedProfile.profileId(), PhantomPersonalConversationStore.COMPONENT_TYPE).orElseThrow().componentType(), "Personal state did not use the generic component envelope.");
			PhantomAssertions.assertEquals(initial, first.load(_ownedProfile.profileId()).orElseThrow().state(), "Inserted personal state did not round-trip.");
			final PersonalState updatedState = PhantomPersonalConversationStore.apply(initial, "character.object:404", catalog.understand("мое хобби фотография").orElseThrow(), "", Origin.CLIENT_CHAT, 5001, catalog.limits());
			final StoredState updated = first.save(_ownedProfile.profileId(), inserted.rowVersion(), updatedState);
			PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> first.save(_ownedProfile.profileId(), inserted.rowVersion(), updatedState), "Stale conversation.personal row version was accepted.");
			final PhantomPersonalConversationStore restarted = PhantomPersonalConversationStore.production(PhantomProfileRepository.open());
			PhantomAssertions.assertEquals(updatedState, restarted.load(_ownedProfile.profileId()).orElseThrow().state(), "Restart lost conversation.personal facts.");
			PhantomAssertions.assertTrue(updated.rowVersion() > inserted.rowVersion(), "conversation.personal row version did not advance.");
			try (Connection connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id = ? AND component_type = 'conversation.personal'"))
			{
				statement.setLong(1, _ownedProfile.profileId());
				try (var result = statement.executeQuery())
				{
					PhantomAssertions.assertTrue(result.next() && (result.getInt(1) == 1), "conversation.personal created duplicate rows.");
				}
			}
			context.record("goal038.personalRowVersion", updated.rowVersion());
		});
	}

	private void productionTests(PhantomTestRegistry registry)
	{
		registry.add("01-production-social-conversation-chat-restart-and-memory-reload", context ->
		{
			final Path data = context.moduleRoot().resolve("dist/game/data/phantoms");
			final PhantomConversationCatalog conversationCatalog = PhantomConversationCatalog.load(data.resolve("conversation/high-five-ru-conversation-v1.xml"), data.resolve("conversation/high-five-ru-conversation-corpus-v1.tsv"));
			final PhantomSemanticPack semanticPack = PhantomSemanticPack.load(data.resolve("semantic/high-five-ru-semantic-v1.xml"), data.resolve("semantic/high-five-ru-corpus-v1.tsv"), PhantomSemanticGrounding.fixed(HASHES, references()));
			final PhantomSemanticUnderstandingService semantic = PhantomSemanticUnderstandingService.loaded(semanticPack);
			PhantomAssertions.assertTrue(semantic.start(), "Guarded Goal038 semantic authority did not start.");
			final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(data.resolve("social/high-five-social-v1.xml"));
			final List<ConversationResponsePlan> plans = new ArrayList<>();
			final int observerObjectId = 9_380_001;
			final int speakerObjectId = 9_380_002;
			final Lease lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(observerObjectId, OwnerKind.PHANTOM);
			PhantomAssertions.assertTrue(lease != null, "Guarded Goal038 could not reserve its managed observer identity.");
			PhantomSocialService social = null;
			PhantomConversationService conversation = null;
			try
			{
				social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_profiles, socialCatalog), 18001801L, 16, () -> 1000L);
				PhantomAssertions.assertTrue(social.start(), "Guarded Goal038 production Social service did not start.");
				final PhantomHumanizedCatalog firstCatalog = PhantomHumanizedCatalog.load(data, true);
				final PhantomHumanizedConversationService firstHumanized = new PhantomHumanizedConversationService(firstCatalog, PhantomPersonalConversationStore.production(_profiles), social, new PhantomHumanizedConversationService.Settings(true, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false));
				conversation = productionConversation(conversationCatalog, semantic, social, firstHumanized, plans, observerObjectId, speakerObjectId);
				PhantomAssertions.assertTrue(conversation.start(), "Guarded Goal038 production Conversation service did not start.");
				publishClient(observation(), conversation, observerObjectId, speakerObjectId, "я люблю старый рок", 60_000_000L, plans, 1);
				final ConversationResponsePlan stored = plans.get(0);
				PhantomAssertions.assertEquals("social.reply", stored.responseAct(), "Production Conversation did not publish the Humanized social act.");
				PhantomAssertions.assertTrue(stored.proposal() == null, "Humanized production response created a gameplay proposal.");
				PhantomAssertions.assertTrue(_profiles.findComponent(_ownedProfile.profileId(), "conversation.state").isPresent(), "Production Conversation state was not durable.");
				PhantomAssertions.assertTrue(_profiles.findComponent(_ownedProfile.profileId(), PhantomPersonalConversationStore.COMPONENT_TYPE).isPresent(), "Production personal memory was not durable.");
				publishClient(observation(), conversation, observerObjectId, speakerObjectId, "где взять адену", 120_000_000L, plans, 2);
				PhantomAssertions.assertTrue((plans.get(1).proposal() != null) && plans.get(1).proposal().proposalKey().equals("item.acquire"), "Functional game query lost its accepted proposal authority between personal turns.");

				conversation.beginStop();
				PhantomAssertions.assertTrue(conversation.finishStop(), "Guarded Goal038 Conversation did not stop for restart.");
				conversation = null;
				social.beginStop();
				PhantomAssertions.assertTrue(social.finishStop(), "Guarded Goal038 Social did not stop for restart.");
				social = null;

				final PhantomHumanizedCatalog reloadedCatalog = PhantomHumanizedCatalog.load(data, true);
				PhantomAssertions.assertEquals(firstCatalog.combinedHash(), reloadedCatalog.combinedHash(), "Humanized catalog authority changed across restart.");
				social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_profiles, socialCatalog), 18001801L, 16, () -> 2000L);
				PhantomAssertions.assertTrue(social.start(), "Guarded Goal038 reloaded Social service did not start.");
				final PhantomHumanizedConversationService reloadedHumanized = new PhantomHumanizedConversationService(reloadedCatalog, PhantomPersonalConversationStore.production(PhantomProfileRepository.open()), social, new PhantomHumanizedConversationService.Settings(true, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false));
				conversation = productionConversation(conversationCatalog, semantic, social, reloadedHumanized, plans, observerObjectId, speakerObjectId);
				PhantomAssertions.assertTrue(conversation.start(), "Guarded Goal038 reloaded Conversation service did not start.");
				publishClient(observation(), conversation, observerObjectId, speakerObjectId, "что я люблю", 180_000_000L, plans, 3);
				PhantomAssertions.assertTrue(plans.get(2).renderedText().contains("старый рок"), "Guarded production restart did not recall structured personal memory after the functional game turn.");
				context.record("goal038.productionPlans", plans.size());
				context.record("goal038.restartCatalogHash", reloadedCatalog.combinedHash());
			}
			finally
			{
				if (conversation != null)
				{
					conversation.beginStop();
					PhantomAssertions.assertTrue(conversation.finishStop(), "Guarded Goal038 Conversation cleanup did not drain.");
				}
				if (social != null)
				{
					social.beginStop();
					PhantomAssertions.assertTrue(social.finishStop(), "Guarded Goal038 Social cleanup did not drain.");
				}
				semantic.beginStop();
				PhantomAssertions.assertTrue(semantic.finishStop(), "Guarded Goal038 Semantic cleanup did not drain.");
				lease.close();
			}
		});
	}

	private PhantomConversationService productionConversation(PhantomConversationCatalog catalog, PhantomSemanticUnderstandingService semantic, PhantomSocialService social, PhantomHumanizedConversationService humanized, List<ConversationResponsePlan> plans, int observerObjectId, int speakerObjectId)
	{
		final PhantomConversationService.ContextPort context = new PhantomConversationService.ContextPort()
		{
			@Override
			public OptionalLong profileIdForObject(int characterObjectId)
			{
				return characterObjectId == observerObjectId ? OptionalLong.of(_ownedProfile.profileId()) : OptionalLong.empty();
			}

			@Override
			public Optional<ContextSnapshot> snapshot(long observerProfileId, DeliveredObservation observation, String previousIntent, List<org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue> previousSlots)
			{
				final PhantomDomainRef speaker = new PhantomDomainRef("character.object", Integer.toString(speakerObjectId));
				return Optional.of(new ContextSnapshot(observerProfileId, "Тихон", speaker, speaker, 0, InputContext.empty()));
			}
		};
		return new PhantomConversationService(catalog, new PhantomConversationStore(_profiles), context, semantic, social, plans::add, PhantomIdentityLeaseRegistry.getInstance(), observation(), PhantomClanDirectiveIngressPort.noop(), humanized);
	}

	private static ChatObservationService observation()
	{
		return ChatObservationService.getInstance();
	}

	private static void publishClient(ChatObservationService observation, PhantomConversationService conversation, int observerObjectId, int speakerObjectId, String text, long epochMillis, List<ConversationResponsePlan> plans, int expectedPlans)
	{
		try (DispatchHandle dispatch = observation.openClientDispatch(speakerObjectId, "Анна", ChatType.WHISPER, "Тихон", text, epochMillis))
		{
			final var descriptor = observation.captureClientPacket(speakerObjectId, ChatType.WHISPER, text);
			PhantomAssertions.assertTrue(descriptor != null, "Guarded Goal038 client dispatch was not captured.");
			observation.publishDelivered(descriptor, speakerObjectId, ChatType.WHISPER, text, observerObjectId, "Тихон");
		}
		for (int pulse = 0; (pulse < 256) && (plans.size() < expectedPlans); pulse++)
		{
			conversation.onPulse();
		}
		PhantomAssertions.assertEquals(expectedPlans, plans.size(), "Guarded Goal038 delivery did not produce exactly one durable plan: " + conversation.snapshot());
	}

	private static Fixture fixture(PhantomTestContext context)
	{
		final InMemoryPersonal personal = new InMemoryPersonal();
		personal.addProfiles(1, 2);
		return fixture(context, personal);
	}

	private static Fixture fixture(PhantomTestContext context, InMemoryPersonal personal)
	{
		final PhantomHumanizedCatalog catalog = catalog(context, true);
		final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		final MemoryStore socialStore = new MemoryStore();
		socialStore.addProfiles(1, 2);
		final PhantomSocialService social = new PhantomSocialService(socialCatalog, socialStore, 18001801L, 16, () -> 1000L);
		PhantomAssertions.assertTrue(social.start(), "Goal038 Social authority did not start.");
		final PhantomHumanizedConversationService service = new PhantomHumanizedConversationService(catalog, new PhantomPersonalConversationStore(personal), social, new PhantomHumanizedConversationService.Settings(true, Register.CASUAL, ProfanityMode.CONTEXTUAL, Variation.HIGH, false));
		return new Fixture(catalog, social, service);
	}

	private static Request request(long owner, SubjectRef speaker, Origin origin, String text, long minute, String identity)
	{
		return new Request(owner, "Тихон", speaker, origin, ChatType.WHISPER, text, PhantomHumanizedCatalog.sha256("goal038.request|" + identity), minute);
	}

	private static PhantomHumanizedCatalog catalog(PhantomTestContext context, boolean custom)
	{
		return PhantomHumanizedCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms"), custom);
	}

	private static PhantomSemanticUnderstandingService semantic(PhantomTestContext context)
	{
		final PhantomSemanticPack pack = PhantomSemanticPack.load(context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml"), context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv"), PhantomSemanticGrounding.fixed(HASHES, references()));
		final PhantomSemanticUnderstandingService service = PhantomSemanticUnderstandingService.loaded(pack);
		PhantomAssertions.assertTrue(service.start(), "Goal038 legacy Semantic authority did not start.");
		return service;
	}

	private static EnumMap<SlotType, Map<String, PhantomDomainRef>> references()
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

	private static Path copyPhantomData(PhantomTestContext context) throws Exception
	{
		final Path source = context.moduleRoot().resolve("dist/game/data/phantoms");
		final Path target = Files.createTempDirectory("phantom-goal038-data-");
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
		if (root == null)
		{
			return;
		}
		for (Path path : Files.walk(root).sorted(java.util.Comparator.reverseOrder()).toList())
		{
			Files.deleteIfExists(path);
		}
	}

	private record Fixture(PhantomHumanizedCatalog catalog, PhantomSocialService social, PhantomHumanizedConversationService service) implements AutoCloseable
	{
		@Override
		public void close()
		{
			social.beginStop();
			PhantomAssertions.assertTrue(social.finishStop(), "Goal038 Social authority did not stop.");
		}
	}

	private static final class InMemoryPersonal implements PhantomPersonalConversationStore.PersistencePort
	{
		private final Map<Long, StoredState> _states = new HashMap<>();
		private final Set<Long> _profiles = new java.util.HashSet<>();
		private boolean _conflictNext;
		private int _conflicts;

		private void addProfiles(long... profiles)
		{
			for (long profile : profiles)
			{
				_profiles.add(profile);
			}
		}

		private void conflictNext()
		{
			_conflictNext = true;
		}

		private int conflicts()
		{
			return _conflicts;
		}

		@Override
		public Optional<StoredState> load(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}

		@Override
		public StoredState save(long profileId, long expectedRowVersion, PersonalState state)
		{
			if (!_profiles.contains(profileId))
			{
				throw new IllegalArgumentException("Unknown in-memory profile.");
			}
			if (_conflictNext)
			{
				_conflictNext = false;
				_conflicts++;
				throw new ConcurrentModificationException("Injected conversation.personal conflict.");
			}
			final StoredState current = _states.get(profileId);
			if (((current == null) && (expectedRowVersion != -1)) || ((current != null) && (current.rowVersion() != expectedRowVersion)))
			{
				throw new ConcurrentModificationException("Stale in-memory conversation.personal row version.");
			}
			final StoredState saved = new StoredState(profileId, current == null ? 0 : current.rowVersion() + 1, PhantomPersonalConversationStore.decode(PhantomPersonalConversationStore.encode(state)));
			_states.put(profileId, saved);
			return saved;
		}
	}
}

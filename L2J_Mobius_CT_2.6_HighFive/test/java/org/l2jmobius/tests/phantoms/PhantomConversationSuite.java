/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationActionProposal;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSession;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.Authorization;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding.Hashes;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.InputContext;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.UnderstandingStatus;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;

public final class PhantomConversationSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CATALOG_CODEC,
		UNDERSTANDING,
		SOCIAL_STYLE
	}

	private static final long SEED = 20002001L;
	private static final Hashes HASHES = new Hashes("A".repeat(64), "B".repeat(64), "C".repeat(64));
	private final Mode _mode;

	public PhantomConversationSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "conversation-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Conversation focused mode used the wrong seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CATALOG_CODEC -> catalogCodec(registry);
			case UNDERSTANDING -> understanding(registry);
			case SOCIAL_STYLE -> socialStyle(registry);
		}
	}

	private static void catalogCodec(PhantomTestRegistry registry)
	{
		registry.add("01-strict-catalog-corpus-and-required-hard-limits", context ->
		{
			final PhantomConversationCatalog catalog = catalog(context);
			PhantomAssertions.assertEquals(128, catalog.corpusCases(), "Conversation corpus case count changed.");
			PhantomAssertions.assertTrue(catalog.limits().ingressQueue() <= 1024 && catalog.limits().openBatches() <= 256 && catalog.limits().observersPerMessage() <= 32 && catalog.limits().operationsPerPulse() <= 32, "Conversation ingress/pulse hard bounds changed.");
			PhantomAssertions.assertTrue(catalog.limits().sessionsPerProfile() <= 8 && catalog.limits().recentHashes() <= 8 && catalog.limits().pendingSlots() <= 4 && catalog.limits().statePayload() <= 4096, "Conversation durable state hard bounds changed.");
			final String source = Files.readString(xml(context), StandardCharsets.UTF_8);
			final Path invalid = Files.createTempFile("conversation-xxe-", ".xml");
			try
			{
				Files.writeString(invalid, source.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE conversationCatalog [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"), StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomConversationCatalog.load(invalid, corpus(context)), "Conversation XXE control was accepted.");
			}
			finally
			{
				Files.deleteIfExists(invalid);
			}
			context.record("conversation.catalogHash", catalog.hash());
			context.record("conversation.corpusHash", catalog.corpusHash());
		});

		registry.add("02-conversation-state-codec-is-strict-deterministic-and-bounded", context ->
		{
			final PhantomConversationStateCodec codec = new PhantomConversationStateCodec();
			final String a = "A".repeat(64);
			final ConversationSession session = new ConversationSession(ChatType.WHISPER, new PhantomDomainRef("character.object", "100"), 1000, 1001, "item.acquire.query", List.of(SlotValue.domain(SlotType.ITEM, new PhantomDomainRef("item", "57"), -1, -1)), null, a, a, a);
			final ConversationState state = new ConversationState(a, "B".repeat(64), "C".repeat(64), "D".repeat(64), "E".repeat(64), "F".repeat(64), "1".repeat(64), 1000, List.of(session), List.of("2".repeat(64)));
			final byte[] encoded = codec.encode(state);
			PhantomAssertions.assertEquals(state, codec.decode(encoded), "conversation.state round-trip changed durable facts.");
			PhantomAssertions.assertTrue(PhantomConversationStateCodec.DECLARED_WORST_CASE_BYTES <= 4096, "Declared conversation.state worst case exceeds 4096.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(java.util.Arrays.copyOf(encoded, encoded.length - 1)), "Truncated conversation.state was accepted.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> codec.decode(java.util.Arrays.copyOf(encoded, encoded.length + 1)), "Trailing conversation.state byte was accepted.");
			context.record("conversation.state.sampleBytes", encoded.length);
			context.record("conversation.state.declaredWorstCaseBytes", PhantomConversationStateCodec.DECLARED_WORST_CASE_BYTES);
		});
	}

	private static void understanding(PhantomTestRegistry registry)
	{
		registry.add("01-complete-intent-replaces-pending-and-fragment-has-no-intent-path", context ->
		{
			final PhantomSemanticUnderstandingService semantic = semantic(context);
			try
			{
				final var complete = semantic.understand("где взять адену", InputContext.empty());
				PhantomAssertions.assertEquals(UnderstandingStatus.ACCEPTED, complete.status(), "Complete new intent was not accepted.");
				PhantomAssertions.assertEquals("item.acquire.query", complete.selectedIntent(), "Complete intent replacement selected the wrong intent.");
				final var fragment = semantic.resolveFragment("адена", InputContext.empty(), Set.of(SlotType.ITEM));
				PhantomAssertions.assertEquals(UnderstandingStatus.ACCEPTED, fragment.status(), "Clarification fragment did not resolve exact pending slot.");
				PhantomAssertions.assertEquals(List.of(SlotType.ITEM), fragment.slots().stream().map(SlotValue::type).toList(), "Fragment resolver inferred an extra slot or intent.");
				PhantomAssertions.assertEquals(complete.packHash(), fragment.packHash(), "Fragment changed semantic authority generation.");
			}
			finally
			{
				semantic.beginStop();
				semantic.finishStop();
			}
		});

		registry.add("02-proposal-is-observer-only-and-requires-checkpoint-two", context ->
		{
			final ConversationActionProposal proposal = new ConversationActionProposal("party.invite", new PhantomDomainRef("profile", "1"), new PhantomDomainRef("character.object", "2"), List.of(SlotValue.domain(SlotType.TARGET_PLAYER, new PhantomDomainRef("character.object", "2"), -1, -1)), "A".repeat(64), "B".repeat(64), 8000, 100, 105, Authorization.CHECKPOINT_2_REQUIRED);
			PhantomAssertions.assertEquals(Authorization.CHECKPOINT_2_REQUIRED, proposal.authorization(), "Checkpoint 1 proposal gained executable authorization.");
			PhantomAssertions.assertFalse(((Object) proposal) instanceof Runnable, "Conversation action proposal became executable.");
		});
	}

	private static void socialStyle(PhantomTestRegistry registry)
	{
		registry.add("01-only-declared-social-bands-select-deterministic-style", context ->
		{
			final PhantomConversationCatalog catalog = catalog(context);
			PhantomAssertions.assertEquals("neutral", catalog.style(0, 0, 0), "Neutral social state changed style.");
			PhantomAssertions.assertEquals("warm", catalog.style(1000, 0, 0), "Warmth modifier did not select warm style.");
			PhantomAssertions.assertEquals("cold", catalog.style(-1000, 0, 0), "Negative warmth did not select cold style.");
			PhantomAssertions.assertEquals("cautious", catalog.style(1000, 1000, 0), "Conflict escalation did not dominate style selection.");
			PhantomAssertions.assertEquals("terse", catalog.style(0, 0, -1000), "Invite preference did not select terse style.");
			final String first = catalog.template("ack.action_proposed", "warm", 12345);
			final String second = catalog.template("ack.action_proposed", "warm", 12345);
			PhantomAssertions.assertEquals(first, second, "Conversation template selection is not deterministic.");
			PhantomAssertions.assertTrue(catalog.suppresses("terse"), "Declared terse acknowledgement suppression is absent.");
		});
	}

	private static PhantomConversationCatalog catalog(PhantomTestContext context)
	{
		return PhantomConversationCatalog.load(xml(context), corpus(context));
	}

	private static PhantomSemanticUnderstandingService semantic(PhantomTestContext context)
	{
		final PhantomSemanticPack pack = PhantomSemanticPack.load(context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml"), context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv"), PhantomSemanticGrounding.fixed(HASHES, references()));
		final PhantomSemanticUnderstandingService service = PhantomSemanticUnderstandingService.loaded(pack);
		service.start();
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

	private static Path xml(PhantomTestContext context)
	{
		return context.moduleRoot().resolve("dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml");
	}

	private static Path corpus(PhantomTestContext context)
	{
		return context.moduleRoot().resolve("dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv");
	}
}

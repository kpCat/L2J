/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchDescriptor;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationContextPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.Authorization;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding.Hashes;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

public final class PhantomConversationIntegrationSuite implements PhantomTestSuite
{
	public enum Mode
	{
		CHAT_INTEGRATION,
		LIFECYCLE_PERFORMANCE
	}

	private static final long SEED = 20002001L;
	private static final Hashes HASHES = new Hashes("A".repeat(64), "B".repeat(64), "C".repeat(64));
	private final Mode _mode;
	private final List<ConversationResponsePlan> _plans = new ArrayList<>();
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomProfileRepository _profiles;
	private PhantomProfile _observerProfile;
	private PhantomMaterializationService _materialization;
	private PhantomSocialService _social;
	private PhantomSemanticUnderstandingService _semantic;
	private PhantomConversationService _conversation;
	private Player _speaker;
	private Player _observer;
	private boolean _stateExistedBeforePublish;

	public PhantomConversationIntegrationSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return _mode == Mode.CHAT_INTEGRATION ? "conversation-chat-integration" : "conversation-lifecycle-performance";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Conversation integration mode used the wrong seed.");
		_environment = new PhantomHeadlessPlayerTestEnvironment();
		_environment.initialize(context);
		_profiles = PhantomProfileRepository.open();
		_observerProfile = _profiles.create(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		PhantomAssertions.assertTrue(_materialization.start(), "Conversation materialization did not start.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_observerProfile.profileId()).status(), "Conversation observer did not materialize.");
		_observer = World.getInstance().getPlayer(_environment.observer().objectId());
		_speaker = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue((_observer != null) && (_speaker != null), "Conversation integration fixtures did not load.");

		final L2jTopologyValidationBackend topologyBackend = new L2jTopologyValidationBackend();
		final var topologySnapshot = new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1);
		final PhantomTopologyQuery topology = new PhantomTopologyQuery(topologySnapshot, topologyBackend, new PhantomTopologyMetrics());
		final PhantomSemanticPack semanticPack = PhantomSemanticPack.load(Path.of("data/phantoms/semantic/high-five-ru-semantic-v1.xml"), Path.of("data/phantoms/semantic/high-five-ru-corpus-v1.tsv"), PhantomSemanticGrounding.fixed(HASHES, references()));
		_semantic = PhantomSemanticUnderstandingService.loaded(semanticPack);
		PhantomAssertions.assertTrue(_semantic.start(), "Conversation semantic service did not start.");
		final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		_social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_profiles, socialCatalog), 18001801L, 16, () -> 1000L);
		PhantomAssertions.assertTrue(_social.start(), "Conversation social service did not start.");
		final PhantomConversationCatalog catalog = PhantomConversationCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-v1.xml"), Path.of("data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"));
		_conversation = new PhantomConversationService(catalog, new PhantomConversationStore(_profiles), new L2jPhantomConversationContextPort(_materialization, topology), _semantic, _social, plan ->
		{
			_stateExistedBeforePublish = _profiles.findComponent(plan.ownerProfileId(), "conversation.state").isPresent();
			_plans.add(plan);
		}, PhantomIdentityLeaseRegistry.getInstance(), ChatObservationService.getInstance());
		PhantomAssertions.assertTrue(_conversation.start(), "Conversation service did not start.");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_conversation != null)
		{
			_conversation.beginStop();
			_conversation.finishStop();
		}
		if (_semantic != null)
		{
			_semantic.beginStop();
			_semantic.finishStop();
		}
		if (_social != null)
		{
			_social.beginStop();
			_social.finishStop();
		}
		if (_materialization != null)
		{
			_materialization.dematerialize(_observerProfile.profileId());
			_materialization.shutdown();
		}
		if (_speaker != null)
		{
			_environment.cleanupLoadedPlayer(_speaker);
		}
		if ((_profiles != null) && (_observerProfile != null))
		{
			_profiles.find(_observerProfile.profileId()).ifPresent(profile -> _profiles.delete(profile.profileId(), profile.rowVersion()));
		}
		if (_environment != null)
		{
			_environment.shutdown();
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		if (_mode == Mode.CHAT_INTEGRATION)
		{
			chatIntegration(registry);
		}
		else
		{
			lifecyclePerformance(registry);
		}
	}

	private void chatIntegration(PhantomTestRegistry registry)
	{
		registry.add("01-creature-say-actual-recipient-produces-one-durable-observer-plan", context ->
		{
			final CreatureSay packet;
			try (var scope = ChatObservationService.getInstance().openClientDispatch(_speaker.getObjectId(), _speaker.getName(), ChatType.WHISPER, _observer.getName(), "где взять адену", 60_000_000L))
			{
				packet = new CreatureSay(_speaker, ChatType.WHISPER, _speaker.getName(), "где взять адену");
			}
			packet.runImpl(_observer);
			_conversation.onPulse();
			PhantomAssertions.assertEquals(0, _plans.size(), "A new actual-delivery batch was not deferred by one pulse.");
			_conversation.onPulse();
			PhantomAssertions.assertEquals(1, _plans.size(), "Actual CreatureSay recipient did not produce exactly one response plan.");
			final ConversationResponsePlan plan = _plans.getFirst();
			PhantomAssertions.assertTrue(_stateExistedBeforePublish, "Conversation plan was published before durable conversation.state.");
			PhantomAssertions.assertEquals(_observerProfile.profileId(), plan.ownerProfileId(), "Plan owner is not the actual delivered Phantom recipient.");
			PhantomAssertions.assertEquals(Authorization.CHECKPOINT_2_REQUIRED, plan.proposal().authorization(), "Observed proposal is executable in Checkpoint 1.");
			PhantomAssertions.assertEquals("item.acquire", plan.proposal().proposalKey(), "Semantic query mapped to the wrong observer-only proposal.");
			PhantomAssertions.assertEquals(0L, _conversation.snapshot().planFailures(), "Observer-only production boundary reported a plan failure.");
		});

		registry.add("02-duplicate-delivery-is-idempotent-and-unsupported-or-unaddressed-is-silent", context ->
		{
			final ConversationResponsePlan first = _plans.getFirst();
			final DispatchDescriptor duplicate = new DispatchDescriptor(first.dispatchId(), Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.WHISPER, _observer.getName(), "где взять адену", 60_000_000L);
			_conversation.onDelivered(new ChatObservationService.DeliveredObservation(duplicate, _observer.getObjectId(), _observer.getName()));
			_conversation.onPulse();
			_conversation.onPulse();
			PhantomAssertions.assertEquals(1, _plans.size(), "Duplicate dispatch produced another plan.");
			PhantomAssertions.assertTrue(_conversation.snapshot().duplicates() >= 1, "Duplicate dispatch was not counted.");
			final DispatchDescriptor unsupported = new DispatchDescriptor(90002, Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.CLAN, "", "текст", 60_000_000L);
			_conversation.onDelivered(new ChatObservationService.DeliveredObservation(unsupported, _observer.getObjectId(), _observer.getName()));
			PhantomAssertions.assertEquals(0, _conversation.snapshot().ingressSize(), "Unsupported channel entered conversation ingress.");
		});
	}

	private void lifecyclePerformance(PhantomTestRegistry registry)
	{
		registry.add("01-100k-generic-non-managed-deliveries-have-no-conversation-db-path", context ->
		{
			final ChatObservationService observation = ChatObservationService.getInstance();
			final DispatchDescriptor descriptor;
			try (var scope = observation.openClientDispatch(_speaker.getObjectId(), _speaker.getName(), ChatType.GENERAL, null, "текст", 1))
			{
				descriptor = observation.captureClientPacket(_speaker.getObjectId(), ChatType.GENERAL, "текст");
			}
			for (int index = 0; index < 100000; index++)
			{
				observation.publishDelivered(descriptor, _speaker.getObjectId(), ChatType.GENERAL, "текст", _speaker.getObjectId(), _speaker.getName());
			}
			PhantomAssertions.assertEquals(0, _conversation.snapshot().ingressSize(), "Non-managed generic deliveries entered conversation ingress.");
			PhantomAssertions.assertEquals(100000L, _conversation.snapshot().ingressIgnored(), "Non-managed generic deliveries did not take the bounded ignore path.");
		});

		registry.add("02-100k-mixed-observations-remain-within-pulse-queue-and-batch-bounds", context ->
		{
			for (int index = 0; index < 99900; index++)
			{
				final DispatchDescriptor unsupported = new DispatchDescriptor(100000L + index, Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.CLAN, "", "текст", index);
				_conversation.onDelivered(new ChatObservationService.DeliveredObservation(unsupported, _observer.getObjectId(), _observer.getName()));
			}
			for (int index = 0; index < 100; index++)
			{
				final DispatchDescriptor supported = new DispatchDescriptor(200000L + index, Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.WHISPER, _observer.getName(), "где взять адену", 120_000_000L + index);
				_conversation.onDelivered(new ChatObservationService.DeliveredObservation(supported, _observer.getObjectId(), _observer.getName()));
				_conversation.onPulse();
				_conversation.onPulse();
			}
			final var snapshot = _conversation.snapshot();
			PhantomAssertions.assertTrue(snapshot.ingressSize() <= 1024, "Conversation ingress exceeded its hard bound.");
			PhantomAssertions.assertTrue(snapshot.openBatches() <= 256, "Conversation open batches exceeded their hard bound.");
			PhantomAssertions.assertTrue(snapshot.maximumOperationsPerPulse() <= 32, "Conversation pulse exceeded its operation budget.");
			context.record("conversation.performance.maximumOperationsPerPulse", snapshot.maximumOperationsPerPulse());
			context.record("conversation.performance.plans", snapshot.plansPublished());
		});

		registry.add("03-stop-detaches-registration-and-drains-all-claims", context ->
		{
			_conversation.beginStop();
			PhantomAssertions.assertTrue(_conversation.finishStop(), "Conversation service did not drain on stop.");
			PhantomAssertions.assertFalse(ChatObservationService.getInstance().snapshot().observerRegistered(), "Conversation stop retained generic chat registration.");
			PhantomAssertions.assertEquals(0, _conversation.snapshot().operationClaims(), "Conversation stop retained operation claims.");
			PhantomAssertions.assertEquals(0, _conversation.snapshot().persistenceClaims(), "Conversation stop retained persistence claims.");
		});
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
}

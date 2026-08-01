/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.l2jmobius.gameserver.handler.ChatHandler;
import org.l2jmobius.gameserver.handler.IChatHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchDescriptor;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.network.clientpackets.Say2;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.network.serverpackets.CreatureSay;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationContextPort;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.Authorization;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationState;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSubject;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveredObservation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService.BatchPhase;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService.PhaseObserver;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore.StoredState;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.Lease;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
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
		MANAGED_INGRESS,
		OUTBOUND_CHAT,
		CHAT_INTEGRATION,
		LIFECYCLE_PERFORMANCE
	}

	private record PhaseEvent(BatchPhase phase, long dispatchId, boolean indexMonitorHeld)
	{
	}

	private static final long SEED = 20002001L;
	private static final long CHECKPOINT_2_SEED = 20002002L;
	private static final Hashes HASHES = new Hashes("A".repeat(64), "B".repeat(64), "C".repeat(64));
	private static final Method RUN_PULSE = method(PhantomConversationService.class, "runPulse", int.class);
	private static final Method DISPATCH_FINAL = method(Say2.class, "dispatchFinalFiltered", IChatHandler.class, ChatType.class, Player.class, String.class, String.class, long.class);
	private final Mode _mode;
	private final List<ConversationResponsePlan> _plans = new ArrayList<>();
	private final List<PhaseEvent> _phaseEvents = new ArrayList<>();
	private PhantomHeadlessPlayerTestEnvironment _environment;
	private PhantomProfileRepository _profiles;
	private PhantomProfile _observerProfile;
	private PhantomMaterializationService _materialization;
	private PhantomSocialService _social;
	private PhantomSemanticUnderstandingService _semantic;
	private PhantomConversationCatalog _catalog;
	private PhantomConversationStore _store;
	private PhantomConversationService.ContextPort _contextPort;
	private PhantomConversationService _conversation;
	private Player _speaker;
	private Player _observer;
	private boolean _stateExistedBeforePublish;
	private final AtomicInteger _contextLookups = new AtomicInteger();
	private PhantomTopologyQuery _topology;

	public PhantomConversationIntegrationSuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return switch (_mode)
		{
			case MANAGED_INGRESS -> "conversation-managed-ingress";
			case OUTBOUND_CHAT -> "conversation-outbound-chat";
			case CHAT_INTEGRATION -> "conversation-chat-integration";
			case LIFECYCLE_PERFORMANCE -> "conversation-lifecycle-performance";
		};
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals((_mode == Mode.MANAGED_INGRESS) || (_mode == Mode.OUTBOUND_CHAT) ? CHECKPOINT_2_SEED : SEED, context.seed(), "Conversation integration mode used the wrong seed.");
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
		_topology = topology;
		final PhantomSemanticPack semanticPack = PhantomSemanticPack.load(Path.of("data/phantoms/semantic/high-five-ru-semantic-v1.xml"), Path.of("data/phantoms/semantic/high-five-ru-corpus-v1.tsv"), PhantomSemanticGrounding.fixed(HASHES, references()));
		_semantic = PhantomSemanticUnderstandingService.loaded(semanticPack);
		PhantomAssertions.assertTrue(_semantic.start(), "Conversation semantic service did not start.");
		final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		_social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_profiles, socialCatalog), 18001801L, 16, () -> 1000L);
		PhantomAssertions.assertTrue(_social.start(), "Conversation social service did not start.");
		_catalog = PhantomConversationCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-v1.xml"), Path.of("data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"));
		_store = new PhantomConversationStore(_profiles);
		final PhantomConversationService.ContextPort delegate = new L2jPhantomConversationContextPort(_materialization, topology);
		_contextPort = new PhantomConversationService.ContextPort()
		{
			@Override
			public java.util.OptionalLong profileIdForObject(int characterObjectId)
			{
				_contextLookups.incrementAndGet();
				return delegate.profileIdForObject(characterObjectId);
			}

			@Override
			public java.util.Optional<PhantomConversationService.ContextSnapshot> snapshot(long observerProfileId, DeliveredObservation observation, String previousIntent, List<org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue> previousSlots)
			{
				_contextLookups.incrementAndGet();
				return delegate.snapshot(observerProfileId, observation, previousIntent, previousSlots);
			}
		};
		startConversation(event ->
		{
		});
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		stopConversation();
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
		if (_mode == Mode.MANAGED_INGRESS)
		{
			managedIngress(registry);
		}
		else if (_mode == Mode.OUTBOUND_CHAT)
		{
			outboundChat(registry);
		}
		else if (_mode == Mode.CHAT_INTEGRATION)
		{
			chatIntegration(registry);
		}
		else
		{
			lifecyclePerformance(registry);
		}
	}

	private void outboundChat(PhantomTestRegistry registry)
	{
		registry.add("01-four-current-handler-seams-generated-origin-and-at-most-once", context ->
		{
			World.getInstance().addObject(_speaker);
			final PhantomConversationExecutionCatalog executionCatalog = PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml"));
			final PhantomConversationExecutionStore executionStore = new PhantomConversationExecutionStore(_profiles, executionCatalog);
			final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
			final PhantomGameKnowledgeService knowledge = PhantomGameKnowledgeService.inertForTesting(_topology.snapshot().canonicalHash());
			PhantomAssertions.assertTrue(knowledge.start(), "Outbound test knowledge authority did not start.");
			final PhantomPartyCoordinator party = idleParty(goals);
			final L2jPhantomConversationExecutionPort port = new L2jPhantomConversationExecutionPort(executionCatalog, knowledge, _topology, party, _materialization, ChatObservationService.getInstance());
			PhantomConversationExecutionService execution = new PhantomConversationExecutionService(executionCatalog, executionStore, goals, port);
			PhantomAssertions.assertTrue(execution.start(), "Outbound execution service did not start.");

			final EnumMap<ChatType, IChatHandler> previous = new EnumMap<>(ChatType.class);
			for (ChatType channel : List.of(ChatType.WHISPER, ChatType.PARTY, ChatType.GENERAL, ChatType.TRADE))
			{
				previous.put(channel, ChatHandler.getInstance().getHandler(channel));
			}
			final EnumMap<ChatType, AtomicInteger> calls = new EnumMap<>(ChatType.class);
			for (ChatType channel : previous.keySet())
			{
				calls.put(channel, new AtomicInteger());
			}
			final IChatHandler tracking = new IChatHandler()
			{
				@Override
				public void onChat(ChatType type, Player active, String target, String text)
				{
					calls.get(type).incrementAndGet();
					new CreatureSay(active, type, active.getName(), text).runImpl(_speaker);
				}

				@Override
				public ChatType[] getChatTypeList()
				{
					return new ChatType[]
					{
						ChatType.WHISPER,
						ChatType.PARTY,
						ChatType.GENERAL,
						ChatType.TRADE
					};
				}
			};
			ChatHandler.getInstance().registerHandler(tracking);
			final Party localParty = new Party(_observer, PartyDistributionType.FINDERS_KEEPERS);
			_observer.setParty(localParty);
			final long generatedBefore = ChatObservationService.getInstance().snapshot().generatedDeliveries();
			final long ingressBefore = _conversation.snapshot().ingressAccepted();
			ConversationResponsePlan duplicate = null;
			try
			{
				int receipts = 0;
				for (ChatType channel : List.of(ChatType.WHISPER, ChatType.PARTY, ChatType.GENERAL, ChatType.TRADE))
				{
					final ConversationResponsePlan plan = outboundPlan(_observerProfile.profileId(), 950000L + channel.ordinal(), channel, _speaker.getObjectId());
					duplicate = plan;
					final ExecutionEntry entry = ExecutionEntry.prepared(plan);
					final var current = executionStore.load(_observerProfile.profileId()).orElse(null);
					final ExecutionState next = (current == null ? ExecutionState.empty(executionCatalog.hash(), entry.createdMinute()) : current.state()).add(entry);
					executionStore.save(_observerProfile.profileId(), current == null ? -1 : current.rowVersion(), next);
					execution.publish(plan);
					for (int pulse = 0; (pulse < 64) && (executionStore.load(_observerProfile.profileId()).orElseThrow().state().receipts().size() <= receipts); pulse++)
					{
						execution.onPulse();
					}
					receipts++;
					PhantomAssertions.assertEquals(1, calls.get(channel).get(), "Current registered handler was not called exactly once for " + channel);
				}
				final int callsBeforeDuplicate = calls.values().stream().mapToInt(AtomicInteger::get).sum();
				execution.publish(duplicate);
				for (int pulse = 0; pulse < 16; pulse++)
				{
					execution.onPulse();
				}
				PhantomAssertions.assertEquals(callsBeforeDuplicate, calls.values().stream().mapToInt(AtomicInteger::get).sum(), "Duplicate plan signal sent a second message.");

				final ConversationResponsePlan crashedPlan = outboundPlan(_observerProfile.profileId(), 960001, ChatType.WHISPER, _speaker.getObjectId());
				final ExecutionEntry dispatching = ExecutionEntry.prepared(crashedPlan).withOutbound(OutboundState.DISPATCHING, "execution.prepared", System.currentTimeMillis() / 60000L);
				final var beforeCrash = executionStore.load(_observerProfile.profileId()).orElseThrow();
				executionStore.save(_observerProfile.profileId(), beforeCrash.rowVersion(), beforeCrash.state().add(dispatching));
				execution.beginStop();
				PhantomAssertions.assertTrue(execution.finishStop(), "Outbound execution service did not stop for restart.");
				execution = new PhantomConversationExecutionService(executionCatalog, executionStore, goals, port);
				PhantomAssertions.assertTrue(execution.start(), "Outbound execution service did not restart.");
				for (int pulse = 0; pulse < 32; pulse++)
				{
					execution.onPulse();
				}
				PhantomAssertions.assertEquals(callsBeforeDuplicate, calls.values().stream().mapToInt(AtomicInteger::get).sum(), "Restart resent durable DISPATCHING outbound.");
				PhantomAssertions.assertTrue(executionStore.load(_observerProfile.profileId()).orElseThrow().state().receipts().stream().anyMatch(receipt -> receipt.planId().equals(dispatching.planId()) && (receipt.outboundState() == OutboundState.UNCERTAIN)), "Restart did not persist DISPATCHING as UNCERTAIN.");
				final ExecutionEntry offline = ExecutionEntry.prepared(outboundPlan(_observerProfile.profileId(), 960002, ChatType.WHISPER, Integer.MAX_VALUE));
				PhantomAssertions.assertEquals(0, port.dispatch(_observerProfile.profileId(), offline).deliveries(), "Offline exact counterpart produced a generated delivery.");
			}
			finally
			{
				execution.beginStop();
				execution.finishStop();
				_observer.setParty(null);
				ChatHandler.getInstance().removeHandler(tracking);
				for (IChatHandler handler : previous.values().stream().filter(java.util.Objects::nonNull).distinct().toList())
				{
					ChatHandler.getInstance().registerHandler(handler);
				}
				knowledge.beginStop();
				knowledge.finishStop();
			}
			final var chat = ChatObservationService.getInstance().snapshot();
			PhantomAssertions.assertTrue(chat.generatedDeliveries() >= generatedBefore + 4, "Generated delivery metrics did not record all four handler seams.");
			PhantomAssertions.assertEquals(ingressBefore, _conversation.snapshot().ingressAccepted(), "PHANTOM_GENERATED delivery looped into conversation ingress.");
			context.record("conversation.outbound.generatedDeliveries", chat.generatedDeliveries() - generatedBefore);
		});
	}

	private void managedIngress(PhantomTestRegistry registry)
	{
		registry.add("01-100k-real-recipients-offer-and-context-zero", context ->
		{
			final var before = _conversation.snapshot();
			final int contextBefore = _contextLookups.get();
			final DispatchDescriptor descriptor = new DispatchDescriptor(900001, Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.GENERAL, "", "где взять адену", 60_000_000L);
			for (int index = 0; index < 100_000; index++)
			{
				_conversation.onDelivered(new ChatObservationService.DeliveredObservation(descriptor, 2_000_000 + index, "Real" + index));
			}
			_conversation.onDispatchClosed(descriptor);
			final var after = _conversation.snapshot();
			PhantomAssertions.assertEquals(before.ingressAccepted(), after.ingressAccepted(), "Real recipients consumed conversation ingress offers.");
			PhantomAssertions.assertEquals(contextBefore, _contextLookups.get(), "Real recipients reached conversation context resolution.");
			PhantomAssertions.assertEquals(0, after.ingressSize(), "Real recipients left ingress residue.");
			context.record("conversation.managedIngress.realCallbacks", 100_000);
		});

		registry.add("02-general-real-fanout-retains-one-managed-observer", context ->
		{
			final int before = _plans.size();
			final String addressed = _observer.getName() + ", где взять адену";
			final DispatchDescriptor descriptor = new DispatchDescriptor(900002, Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.GENERAL, "", addressed, 120_000_000L);
			for (int index = 0; index < 100; index++)
			{
				_conversation.onDelivered(new ChatObservationService.DeliveredObservation(descriptor, 2_200_000 + index, "RealFanout" + index));
			}
			_conversation.onDelivered(delivery(descriptor, _observer));
			_conversation.onDispatchClosed(descriptor);
			driveUntilPlans(before + 1, 128);
			PhantomAssertions.assertEquals(before + 1, _plans.size(), "GENERAL fanout did not elect exactly one managed observer.");
			PhantomAssertions.assertEquals(_observerProfile.profileId(), _plans.getLast().ownerProfileId(), "GENERAL fanout elected a non-managed observer.");
		});

		registry.add("03-released-phantom-lease-is-discarded-before-context", context ->
		{
			final int before = _plans.size();
			final int contextBefore = _contextLookups.get();
			final int objectId = 2_300_001;
			final DispatchDescriptor descriptor = new DispatchDescriptor(900003, Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.WHISPER, "Departing", "где взять адену", 180_000_000L);
			final Lease lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, OwnerKind.PHANTOM);
			PhantomAssertions.assertTrue(lease != null, "Could not acquire changing managed identity fixture.");
			_conversation.onDelivered(new ChatObservationService.DeliveredObservation(descriptor, objectId, "Departing"));
			_conversation.onDispatchClosed(descriptor);
			lease.close();
			driveUntilIdle(128);
			PhantomAssertions.assertEquals(before, _plans.size(), "Released Phantom lease produced a conversation plan.");
			PhantomAssertions.assertEquals(contextBefore, _contextLookups.get(), "Released Phantom lease reached context resolution.");
		});

		registry.add("04-dual-drop-overflow-and-delayed-housekeeping-leave-no-residue", context ->
		{
			stopConversation();
			startConversation(event ->
			{
			});
			final int queue = _catalog.limits().ingressQueue();
			final List<DispatchDescriptor> descriptors = new ArrayList<>(queue);
			for (int index = 0; index < queue; index++)
			{
				final DispatchDescriptor descriptor = new DispatchDescriptor(910000L + index, Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.GENERAL, "", "где взять адену", 240_000_000L + index);
				descriptors.add(descriptor);
				_conversation.onDelivered(delivery(descriptor, _observer));
			}
			final long backpressureBefore = _conversation.snapshot().backpressure();
			_conversation.onDispatchClosed(descriptors.getFirst());
			PhantomAssertions.assertTrue(_conversation.snapshot().backpressure() > backpressureBefore, "Saturated CLOSED offer did not enter typed overflow handling.");
			for (int pulse = 0; (pulse < 4096) && (_conversation.snapshot().ingressSize() > 0); pulse++)
			{
				pulse(_conversation, 1);
			}
			for (int index = 1; index < descriptors.size(); index++)
			{
				_conversation.onDispatchClosed(descriptors.get(index));
			}
			driveUntilIdle(16384, 1);
			final var snapshot = _conversation.snapshot();
			PhantomAssertions.assertEquals(0, snapshot.ingressSize(), "Overflow cleanup retained ingress residue.");
			PhantomAssertions.assertEquals(0, snapshot.openBatches(), "Overflow cleanup retained open batch residue.");
			PhantomAssertions.assertEquals(0, snapshot.dueBatches(), "Overflow cleanup retained due batch residue.");
			PhantomAssertions.assertTrue(snapshot.maximumOperationsPerPulse() <= 1, "Delayed promotion exceeded the one-operation pulse budget.");
		});
	}

	private void chatIntegration(PhantomTestRegistry registry)
	{
		registry.add("01-no-election-before-closed-and-real-say2-handler-creature-say-path", context ->
		{
			final int beforeManual = _plans.size();
			final ChatObservationService observation = ChatObservationService.getInstance();
			final var scope = observation.openClientDispatch(_speaker.getObjectId(), _speaker.getName(), ChatType.WHISPER, _observer.getName(), "где взять адену", 60_000_000L);
			final CreatureSay packet = new CreatureSay(_speaker, ChatType.WHISPER, _speaker.getName(), "где взять адену");
			packet.runImpl(_observer);
			for (int pulse = 0; pulse < 4; pulse++)
			{
				_conversation.onPulse();
			}
			PhantomAssertions.assertEquals(beforeManual, _plans.size(), "Conversation elected an observer before DISPATCH_CLOSED.");
			scope.close();
			driveUntilPlans(beforeManual + 1, 64);
			PhantomAssertions.assertEquals(beforeManual + 1, _plans.size(), "Closed actual delivery did not publish one observer plan.");

			final AtomicInteger handlerCalls = new AtomicInteger();
			final IChatHandler handler = new IChatHandler()
			{
				@Override
				public void onChat(ChatType type, Player active, String target, String text)
				{
					handlerCalls.incrementAndGet();
					new CreatureSay(active, type, active.getName(), text).runImpl(_observer);
				}

				@Override
				public ChatType[] getChatTypeList()
				{
					return new ChatType[]
					{
						ChatType.WHISPER
					};
				}
			};
			invoke(DISPATCH_FINAL, null, handler, ChatType.WHISPER, _speaker, _observer.getName(), "где взять адену", 120_000_000L);
			driveUntilPlans(beforeManual + 2, 64);
			PhantomAssertions.assertEquals(1, handlerCalls.get(), "Say2 final handler was not invoked exactly once.");
			final ConversationResponsePlan plan = _plans.getLast();
			PhantomAssertions.assertTrue(_stateExistedBeforePublish, "Conversation plan was published before durable conversation.state.");
			PhantomAssertions.assertEquals(_observerProfile.profileId(), plan.ownerProfileId(), "Plan owner is not the actual delivered Phantom recipient.");
			PhantomAssertions.assertEquals(Authorization.CHECKPOINT_2_REQUIRED, plan.proposal().authorization(), "Observed proposal is executable in Checkpoint 1.");

			final AtomicInteger invalidCalls = new AtomicInteger();
			final IChatHandler invalidHandler = new IChatHandler()
			{
				@Override
				public void onChat(ChatType type, Player active, String target, String text)
				{
					invalidCalls.incrementAndGet();
				}

				@Override
				public ChatType[] getChatTypeList()
				{
					return new ChatType[]
					{
						ChatType.WHISPER
					};
				}
			};
			invoke(DISPATCH_FINAL, null, invalidHandler, ChatType.WHISPER, _speaker, _observer.getName(), null, 120_000_001L);
			invoke(DISPATCH_FINAL, null, invalidHandler, ChatType.WHISPER, _speaker, _observer.getName(), "x".repeat(1025), 120_000_002L);
			PhantomAssertions.assertEquals(2, invalidCalls.get(), "Invalid observation instrumentation changed ordinary handler invocation.");
		});

		registry.add("02-32-recipient-batch-resumes-across-pulses-with-one-election", context ->
		{
			final int before = _plans.size();
			final ChatObservationService observation = ChatObservationService.getInstance();
			final List<Lease> leases = new ArrayList<>();
			try (var scope = observation.openClientDispatch(_speaker.getObjectId(), _speaker.getName(), ChatType.WHISPER, _observer.getName(), "где взять адену", 180_000_000L))
			{
				final DispatchDescriptor descriptor = observation.captureClientPacket(_speaker.getObjectId(), ChatType.WHISPER, "где взять адену");
				for (int index = 0; index < 31; index++)
				{
					final int objectId = 1_000_000 + index;
					final Lease lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, OwnerKind.PHANTOM);
					PhantomAssertions.assertTrue(lease != null, "Could not reserve a synthetic managed recipient.");
					leases.add(lease);
					observation.publishDelivered(descriptor, _speaker.getObjectId(), ChatType.WHISPER, "где взять адену", objectId, "Managed" + index);
				}
				observation.publishDelivered(descriptor, _speaker.getObjectId(), ChatType.WHISPER, "где взять адену", _observer.getObjectId(), _observer.getName());
			}
			finally
			{
				leases.forEach(Lease::close);
			}
			pulse(_conversation, 1);
			PhantomAssertions.assertEquals(before, _plans.size(), "32-recipient batch completed without resuming across pulses.");
			driveUntilPlans(before + 1, 256, 1);
			PhantomAssertions.assertEquals(before + 1, _plans.size(), "32-recipient dispatch published zero or multiple plans.");
			PhantomAssertions.assertTrue(_conversation.snapshot().maximumOperationsPerPulse() <= 32, "Conversation pulse exceeded the configured hard budget.");
		});

		registry.add("03-interleaved-dispatches-never-mix-and-recent-order-survives-restart", context ->
		{
			final int before = _plans.size();
			final DispatchDescriptor a = descriptor(700001, "где взять адену", 240_000_000L);
			final DispatchDescriptor b = descriptor(700002, "где взять адену", 300_000_000L);
			_conversation.onDelivered(delivery(a, _observer));
			_conversation.onDelivered(delivery(b, _observer));
			_conversation.onDispatchClosed(a);
			for (int index = 0; index < 4; index++)
			{
				_conversation.onPulse();
			}
			PhantomAssertions.assertEquals(before + 1, _plans.size(), "Closed dispatch A did not complete independently of open dispatch B.");
			_conversation.onDispatchClosed(b);
			driveUntilPlans(before + 2, 128);
			PhantomAssertions.assertEquals(List.of(a.dispatchId(), b.dispatchId()), _plans.subList(before, before + 2).stream().map(ConversationResponsePlan::dispatchId).toList(), "Interleaved dispatch observers were mixed or reordered.");
			final List<String> expected = List.of(modelDelivery(a, _observer).observationHash(), modelDelivery(b, _observer).observationHash());
			final StoredState stored = _store.load(_observerProfile.profileId()).orElseThrow();
			final List<String> recent = stored.state().recentObservationHashes();
			PhantomAssertions.assertEquals(expected, recent.subList(recent.size() - 2, recent.size()), "Recent observation hashes are not oldest-to-newest.");
			stopConversation();
			startConversation(event ->
			{
			});
			final List<String> restarted = _store.load(_observerProfile.profileId()).orElseThrow().state().recentObservationHashes();
			PhantomAssertions.assertEquals(recent, restarted, "Restart changed temporal recent-observation order.");
		});

		registry.add("04-optimistic-duplicate-races-publish-exactly-one-plan", context ->
		{
			final int before = _plans.size();
			final DispatchDescriptor first = descriptor(710001, "где взять адену", 360_000_000L);
			publishDirect(first);
			driveUntilPlans(before + 1, 128);
			stopConversation();
			startConversation(event ->
			{
			});
			publishDirect(first);
			driveUntilIdle(128);
			PhantomAssertions.assertEquals(before + 1, _plans.size(), "Restarted duplicate dispatch published a second plan.");
			PhantomAssertions.assertEquals(1L, _conversation.snapshot().duplicates(), "Durable duplicate was not typed as DUPLICATE.");

			final DispatchDescriptor raced = descriptor(710002, "где взять адену", 420_000_000L);
			final String racedHash = modelDelivery(raced, _observer).observationHash();
			final AtomicBoolean injected = new AtomicBoolean();
			stopConversation();
			startConversation(event ->
			{
				if ((event.phase() == BatchPhase.PERSISTING) && injected.compareAndSet(false, true))
				{
					injectCompetingObservation(racedHash, raced.epochMillis() / 60000L);
				}
			});
			publishDirect(raced);
			driveUntilIdle(256, 1);
			PhantomAssertions.assertTrue(injected.get(), "Optimistic duplicate race was not injected at persistence.");
			PhantomAssertions.assertEquals(before + 1, _plans.size(), "Losing optimistic duplicate published a plan.");
			PhantomAssertions.assertEquals(1L, _conversation.snapshot().duplicates(), "Optimistic conflict was not resolved as DUPLICATE.");
		});

		registry.add("05-authority-drift-is-read-only-and-publishes-no-plan", context ->
		{
			stopConversation();
			final StoredState current = _store.load(_observerProfile.profileId()).orElseThrow();
			final ConversationState state = current.state();
			final ConversationState stale = new ConversationState("0".repeat(64), state.packHash(), state.corpusHash(), state.knowledgeHash(), state.topologyHash(), state.roleHash(), state.socialHash(), state.logicalMinute(), state.sessions(), state.recentObservationHashes());
			final StoredState before = _store.save(_observerProfile.profileId(), current.rowVersion(), stale);
			final byte[] beforeBytes = _profiles.findComponent(_observerProfile.profileId(), "conversation.state").orElseThrow().payload();
			startConversation(event ->
			{
			});
			final int plansBefore = _plans.size();
			publishDirect(descriptor(720001, "где взять адену", 480_000_000L));
			driveUntilIdle(128);
			final StoredState after = _store.load(_observerProfile.profileId()).orElseThrow();
			final byte[] afterBytes = _profiles.findComponent(_observerProfile.profileId(), "conversation.state").orElseThrow().payload();
			PhantomAssertions.assertEquals(before.rowVersion(), after.rowVersion(), "AUTHORITY_STALE changed the real DB row version.");
			PhantomAssertions.assertTrue(Arrays.equals(beforeBytes, afterBytes), "AUTHORITY_STALE changed the real DB payload bytes.");
			PhantomAssertions.assertEquals(plansBefore, _plans.size(), "AUTHORITY_STALE published a response/action plan.");
			PhantomAssertions.assertEquals(1L, _conversation.snapshot().authorityStale(), "Authority drift did not reach the typed terminal metric.");
		});
	}

	private void lifecyclePerformance(PhantomTestRegistry registry)
	{
		registry.add("01-256-closed-batches-use-bounded-incremental-index-without-scan", context ->
		{
			final List<Lease> leases = new ArrayList<>();
			try
			{
				for (int index = 0; index < 256; index++)
				{
					final int objectId = 1_100_000 + index;
					final Lease lease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(objectId, OwnerKind.PHANTOM);
					PhantomAssertions.assertTrue(lease != null, "Could not reserve a managed backlog recipient.");
					leases.add(lease);
					final DispatchDescriptor descriptor = new DispatchDescriptor(800000L + index, Origin.CLIENT_CHAT, _speaker.getObjectId(), _speaker.getName(), ChatType.WHISPER, "Managed" + index, "где взять адену", 60_000_000L + index);
					_conversation.onDelivered(new ChatObservationService.DeliveredObservation(descriptor, objectId, "Managed" + index));
					_conversation.onDispatchClosed(descriptor);
				}
				driveUntilIdle(4096);
			}
			finally
			{
				leases.forEach(Lease::close);
			}
			final var snapshot = _conversation.snapshot();
			PhantomAssertions.assertEquals(0, snapshot.openBatches(), "256-batch backlog did not drain through the due index.");
			PhantomAssertions.assertEquals(0, snapshot.dueBatches(), "Due membership retained a completed batch.");
			PhantomAssertions.assertEquals(256L, snapshot.batchesProcessed(), "Incremental due index lost or duplicated a batch.");
			PhantomAssertions.assertTrue(snapshot.maximumOperationsPerPulse() <= 32, "Conversation pulse exceeded its hard operation budget.");
			context.record("conversation.performance.indexTransitions", snapshot.indexTransitions());
		});

		registry.add("02-one-operation-budget-resumes-every-phase-with-exact-counts", context ->
		{
			stopConversation();
			startConversation(event ->
			{
			});
			_phaseEvents.clear();
			final int before = _plans.size();
			publishDirect(descriptor(810001, "где взять адену", 180_000_000L));
			driveUntilPlans(before + 1, 256, 1);
			final Map<BatchPhase, Long> counts = new EnumMap<>(BatchPhase.class);
			for (PhaseEvent event : _phaseEvents)
			{
				counts.merge(event.phase(), 1L, Long::sum);
				PhantomAssertions.assertFalse(event.indexMonitorHeld(), "External phase callback ran under the conversation index monitor.");
			}
			PhantomAssertions.assertEquals(2L, counts.getOrDefault(BatchPhase.COLLECTING, 0L), "Delivery and CLOSED collection were not counted exactly.");
			for (BatchPhase phase : List.of(BatchPhase.RESOLVING_OBSERVERS, BatchPhase.ELECTING, BatchPhase.LOADING_STATE, BatchPhase.BUILDING_CONTEXT, BatchPhase.UNDERSTANDING, BatchPhase.PERSISTING, BatchPhase.PUBLISHING))
			{
				PhantomAssertions.assertEquals(1L, counts.getOrDefault(phase, 0L), "Resumable phase boundary repeated or disappeared: " + phase);
			}
			PhantomAssertions.assertEquals(3L, counts.getOrDefault(BatchPhase.READING_SOCIAL, 0L), "The three social modifier operations were not counted exactly.");
			PhantomAssertions.assertTrue(_conversation.snapshot().maximumOperationsPerPulse() <= 1, "One-operation pulse budget was exceeded.");
		});

		registry.add("03-shutdown-during-every-operational-phase-drains-claims", context ->
		{
			final List<BatchPhase> phases = List.of(BatchPhase.COLLECTING, BatchPhase.RESOLVING_OBSERVERS, BatchPhase.ELECTING, BatchPhase.LOADING_STATE, BatchPhase.BUILDING_CONTEXT, BatchPhase.UNDERSTANDING, BatchPhase.READING_SOCIAL, BatchPhase.PERSISTING, BatchPhase.PUBLISHING);
			long dispatchId = 820000;
			long epoch = 240_000_000L;
			for (BatchPhase target : phases)
			{
				stopConversation();
				final AtomicReference<PhantomConversationService> service = new AtomicReference<>();
				final AtomicBoolean stoppedAtTarget = new AtomicBoolean();
				startConversation(event ->
				{
					PhantomAssertions.assertFalse(event.indexMonitorHeld(), "Shutdown hook ran under the conversation index monitor.");
					if ((event.phase() == target) && stoppedAtTarget.compareAndSet(false, true))
					{
						service.get().beginStop();
					}
				});
				service.set(_conversation);
				publishDirect(descriptor(++dispatchId, "где взять адену", epoch += 60_000_000L));
				for (int pulse = 0; (pulse < 256) && !stoppedAtTarget.get(); pulse++)
				{
					pulse(_conversation, 1);
				}
				PhantomAssertions.assertTrue(stoppedAtTarget.get(), "Shutdown target phase was not reached: " + target);
				PhantomAssertions.assertTrue(_conversation.finishStop(), "In-flight phase claim did not drain: " + target);
				PhantomAssertions.assertEquals(0, _conversation.snapshot().operationClaims(), "Shutdown retained an operation claim at " + target);
				PhantomAssertions.assertEquals(0, _conversation.snapshot().persistenceClaims(), "Shutdown retained a persistence claim at " + target);
				PhantomAssertions.assertEquals(0, _conversation.snapshot().openBatches(), "Shutdown retained an owned batch at " + target);
			}
		});

		registry.add("04-conversation-production-has-no-outbound-or-action-execution", context ->
		{
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/conversation/PhantomConversationService.java"), StandardCharsets.UTF_8);
			for (String forbidden : List.of("new CreatureSay", ".sendPacket(", ".broadcastPacket(", "PhantomGoalService", "PartyInvitationService", ".moveTo(", ".attack(", ".addItem(", ".destroyItem("))
			{
				PhantomAssertions.assertFalse(source.contains(forbidden), "Conversation production contains forbidden execution seam: " + forbidden);
			}
		});
	}

	private void startConversation(Consumer<PhaseEvent> action)
	{
		final PhaseObserver observer = (phase, dispatchId, indexMonitorHeld) ->
		{
			final PhaseEvent event = new PhaseEvent(phase, dispatchId, indexMonitorHeld);
			_phaseEvents.add(event);
			action.accept(event);
		};
		_conversation = new PhantomConversationService(_catalog, _store, _contextPort, _semantic, _social, plan ->
		{
			_stateExistedBeforePublish = _profiles.findComponent(plan.ownerProfileId(), "conversation.state").isPresent();
			_plans.add(plan);
		}, PhantomIdentityLeaseRegistry.getInstance(), ChatObservationService.getInstance(), observer);
		PhantomAssertions.assertTrue(_conversation.start(), "Conversation service did not start.");
	}

	private ConversationResponsePlan outboundPlan(long profileId, long dispatchId, ChatType channel, int counterpartObjectId)
	{
		final long now = System.currentTimeMillis() / 60000L;
		final String observation = org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.sha256("outbound|" + profileId + '|' + dispatchId);
		final String semantic = org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.sha256("outbound.semantic|" + profileId + '|' + dispatchId);
		return new ConversationResponsePlan(profileId, dispatchId, observation, channel, new ConversationSubject(new PhantomDomainRef("character.object", Integer.toString(counterpartObjectId))), semantic, "ack.accepted", "neutral", "Проверенный ответ.", null, now + 1, List.of());
	}

	private PhantomPartyCoordinator idleParty(PhantomGoalStore goals)
	{
		final PhantomPartyPersistencePort states = new PhantomPartyPersistencePort()
		{
			@Override
			public Optional<StoredPartyState> load(long profileId)
			{
				return Optional.empty();
			}

			@Override
			public StoredPartyState save(long profileId, long expectedRowVersion, PartyState state)
			{
				throw new UnsupportedOperationException("Idle outbound fixture does not persist Party state.");
			}

			@Override
			public List<StoredPartyState> loadManagedAfter(long exclusiveProfileId, int pageSize)
			{
				return List.of();
			}
		};
		final PhantomPartyBackend backend = new PhantomPartyBackend()
		{
			@Override
			public OptionalLong managedProfileId(int characterObjectId)
			{
				return OptionalLong.empty();
			}

			@Override
			public Optional<MemberRef> currentMember(long profileId)
			{
				return Optional.empty();
			}

			@Override
			public PartyInvitationService.InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution)
			{
				throw new UnsupportedOperationException("Idle outbound fixture does not invite.");
			}

			@Override
			public PartyInvitationService.RespondResult respond(MemberRef invitee, PartyInvitationService.Response response, PartyInvitationService.InvitationIdentity identity)
			{
				throw new UnsupportedOperationException("Idle outbound fixture does not respond.");
			}

			@Override
			public PartyInvitationService.MembershipOutcome leave(MemberRef member)
			{
				throw new UnsupportedOperationException("Idle outbound fixture does not leave.");
			}

			@Override
			public PartyInvitationService.MembershipOutcome expel(MemberRef requester, MemberRef member)
			{
				throw new UnsupportedOperationException("Idle outbound fixture does not expel.");
			}

			@Override
			public PartyInvitationService.MembershipOutcome transferLeader(MemberRef requester, MemberRef member)
			{
				throw new UnsupportedOperationException("Idle outbound fixture does not transfer leadership.");
			}

			@Override
			public Optional<PartySnapshot> observe(MemberRef member)
			{
				return Optional.empty();
			}

			@Override
			public Optional<MemberSnapshot> memberSnapshot(MemberRef member)
			{
				return Optional.empty();
			}

			@Override
			public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId)
			{
				return List.of();
			}

			@Override
			public boolean materialize(long profileId)
			{
				return false;
			}
		};
		final PhantomPartyRoleCatalog roles = PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml"));
		return new PhantomPartyCoordinator(states, goals, backend, roles, new PhantomPartyRouteCoordinator((PhantomNavigationService) null, null), new PhantomPartyTactics(null, backend), () -> _topology.snapshot().canonicalHash(), System::nanoTime, 16);
	}

	private void stopConversation()
	{
		if ((_conversation == null) || (_conversation.snapshot().state() == PhantomConversationService.ServiceState.STOPPED))
		{
			return;
		}
		_conversation.beginStop();
		PhantomAssertions.assertTrue(_conversation.finishStop(), "Conversation service did not finish stop.");
	}

	private void publishDirect(DispatchDescriptor descriptor)
	{
		_conversation.onDelivered(delivery(descriptor, _observer));
		_conversation.onDispatchClosed(descriptor);
	}

	private void injectCompetingObservation(String observationHash, long logicalMinute)
	{
		final StoredState current = _store.load(_observerProfile.profileId()).orElseThrow();
		final ConversationState state = current.state();
		final List<String> recent = new ArrayList<>(state.recentObservationHashes());
		while (recent.size() >= 8)
		{
			recent.removeFirst();
		}
		recent.add(observationHash);
		_store.save(_observerProfile.profileId(), current.rowVersion(), new ConversationState(state.catalogHash(), state.packHash(), state.corpusHash(), state.knowledgeHash(), state.topologyHash(), state.roleHash(), state.socialHash(), Math.max(state.logicalMinute(), logicalMinute), state.sessions(), recent));
	}

	private void driveUntilPlans(int expected, int maximumPulses)
	{
		driveUntilPlans(expected, maximumPulses, 32);
	}

	private void driveUntilPlans(int expected, int maximumPulses, int budget)
	{
		for (int pulse = 0; (pulse < maximumPulses) && (_plans.size() < expected); pulse++)
		{
			pulse(_conversation, budget);
		}
	}

	private void driveUntilIdle(int maximumPulses)
	{
		driveUntilIdle(maximumPulses, 32);
	}

	private void driveUntilIdle(int maximumPulses, int budget)
	{
		for (int pulse = 0; pulse < maximumPulses; pulse++)
		{
			final var snapshot = _conversation.snapshot();
			if ((snapshot.ingressSize() == 0) && (snapshot.openBatches() == 0) && (snapshot.dueBatches() == 0))
			{
				return;
			}
			pulse(_conversation, budget);
		}
	}

	private static DispatchDescriptor descriptor(long dispatchId, String text, long epochMillis)
	{
		return new DispatchDescriptor(dispatchId, Origin.CLIENT_CHAT, 1, "Speaker", ChatType.WHISPER, "Observer", text, epochMillis);
	}

	private ChatObservationService.DeliveredObservation delivery(DispatchDescriptor descriptor, Player recipient)
	{
		return new ChatObservationService.DeliveredObservation(new DispatchDescriptor(descriptor.dispatchId(), descriptor.origin(), _speaker.getObjectId(), _speaker.getName(), descriptor.chatType(), recipient.getName(), descriptor.finalText(), descriptor.epochMillis()), recipient.getObjectId(), recipient.getName());
	}

	private DeliveredObservation modelDelivery(DispatchDescriptor descriptor, Player recipient)
	{
		final DispatchDescriptor actual = delivery(descriptor, recipient).dispatch();
		return new DeliveredObservation(actual.dispatchId(), actual.origin(), actual.speakerObjectId(), actual.speakerName(), actual.chatType(), actual.whisperTarget(), actual.finalText(), actual.epochMillis(), recipient.getObjectId(), recipient.getName());
	}

	private static void pulse(PhantomConversationService service, int budget)
	{
		invoke(RUN_PULSE, service, budget);
	}

	private static Method method(Class<?> type, String name, Class<?>... parameters)
	{
		try
		{
			final Method method = type.getDeclaredMethod(name, parameters);
			method.setAccessible(true);
			return method;
		}
		catch (ReflectiveOperationException exception)
		{
			throw new ExceptionInInitializerError(exception);
		}
	}

	private static void invoke(Method method, Object target, Object... arguments)
	{
		try
		{
			method.invoke(target, arguments);
		}
		catch (InvocationTargetException exception)
		{
			if (exception.getCause() instanceof RuntimeException runtime)
			{
				throw runtime;
			}
			throw new IllegalStateException(exception.getCause());
		}
		catch (ReflectiveOperationException exception)
		{
			throw new IllegalStateException(exception);
		}
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

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.model.chat.PlayerChatIngress;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationContextPort;
import org.l2jmobius.gameserver.phantoms.conversation.L2jPhantomConversationExecutionPort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationGoalRuntimePort;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationResponsePlan;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveIngressPort;
import org.l2jmobius.gameserver.phantoms.conversation.humanized.PhantomHumanizedConversationService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyDecision;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticGrounding;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** TEST composition of the existing native owners; no product action shortcut or login claim. */
final class PhantomCompanionComposition058 implements AutoCloseable
{
	private final PhantomDecisionEngine _engine;
	private final PhantomConversationExecutionService _execution;
	private final PhantomConversationExecutionStore _executionStore;
	private final PhantomConversationService _conversation;
	private final PhantomSemanticUnderstandingService _semantic;
	private final PhantomConversationIntegrationSuite.NetworkBackedClient _client;
	private ConversationResponsePlan _latestResponse;
	private boolean _recordCommand;
	private final java.util.List<String> _commandPhases = new java.util.ArrayList<>();
	private String _contextDiagnostic = "none";
	private org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionReceipt _lastCommandReceipt;

	PhantomCompanionComposition058(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomTopologyQuery topology, PhantomGameKnowledgeService knowledge, PhantomSocialService social, PhantomPartyCoordinator party, Player human) throws Exception
	{
		this(profiles, materialization, topology, knowledge, social, party, human, null);
	}

	PhantomCompanionComposition058(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomTopologyQuery topology, PhantomGameKnowledgeService knowledge, PhantomSocialService social, PhantomPartyCoordinator party, Player human, PhantomHumanizedConversationService humanized) throws Exception
	{
		this(profiles, materialization, topology, knowledge, social, party, human, humanized, null);
	}

	PhantomCompanionComposition058(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomTopologyQuery topology, PhantomGameKnowledgeService knowledge, PhantomSocialService social, PhantomPartyCoordinator party, Player human, PhantomHumanizedConversationService humanized, org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService combat) throws Exception
	{
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(profiles);
		final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
		final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
		final PhantomPartyDecision decision = new PhantomPartyDecision(party);
		decision.registerCandidates(candidates);
		decision.registerHandlers(handlers);
		candidates.seal();
		handlers.seal();
		_engine = new PhantomDecisionEngine(goals, candidates, handlers, new PhantomMetrics(), 1);
		_engine.start();
		final PhantomConversationExecutionCatalog executionCatalog = PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v4.xml"));
		final PhantomConversationExecutionStore executionStore = new PhantomConversationExecutionStore(profiles, executionCatalog);
		_executionStore = executionStore;
		_execution = new PhantomConversationExecutionService(executionCatalog, executionStore, goals, new L2jPhantomConversationExecutionPort(executionCatalog, knowledge, topology, party, materialization, ChatObservationService.getInstance(), org.l2jmobius.gameserver.phantoms.rift.PhantomRiftConversationFacts.NONE, org.l2jmobius.gameserver.phantoms.farming.PhantomFarmingConversationFacts.NONE, combat, social, org.l2jmobius.gameserver.phantoms.party.PhantomPartySupportPolicy.load(Path.of("data/phantoms/party/high-five-party-support-v1.xml"))), PhantomConversationGoalRuntimePort.decisionEngine(_engine));
		PhantomAssertions.assertTrue(_execution.start(), "Full058 execution did not start.");
		final var grounding059 = Boolean.getBoolean("phantom.test.productionGrounding059") ? PhantomSemanticGrounding.production(knowledge.query(), topology, org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml"))) : PhantomSemanticGrounding.fixed(PhantomConversationIntegrationSuite.HASHES, PhantomConversationIntegrationSuite.references());
		_semantic = PhantomSemanticUnderstandingService.loaded(PhantomSemanticPack.load(Path.of("data/phantoms/semantic/high-five-ru-semantic-v3.xml"), Path.of("data/phantoms/semantic/high-five-ru-corpus-v3.tsv"), grounding059));
		PhantomAssertions.assertTrue(_semantic.start(), "Full058 semantic did not start.");
		final PhantomConversationCatalog catalog = PhantomConversationCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-v3.xml"), Path.of("data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"));
		final L2jPhantomConversationContextPort nativeContext = new L2jPhantomConversationContextPort(materialization, topology);
		final PhantomConversationService.ContextPort contextPort = new PhantomConversationService.ContextPort()
		{
			@Override
			public java.util.OptionalLong profileIdForObject(int objectId) { return nativeContext.profileIdForObject(objectId); }
			@Override
			public java.util.Optional<PhantomConversationService.ContextSnapshot> snapshot(long profileId, org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveredObservation observation, String previous, java.util.List<org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue> slots)
			{
				try
				{
					final var result = nativeContext.snapshot(profileId, observation, previous, slots);
					if (_recordCommand) { _contextDiagnostic = "profile=" + profileId + "; present=" + result.isPresent() + "; materialization=" + materialization.find(profileId); }
					return result;
				}
				catch (RuntimeException failure)
				{
					_contextDiagnostic = failure + "; trace=" + java.util.Arrays.stream(failure.getStackTrace()).limit(5).toList();
					throw failure;
				}
			}
		};
		final PhantomConversationService.PhaseObserver phases = new PhantomConversationService.PhaseObserver()
		{
			@Override
			public void beforeOperation(PhantomConversationService.BatchPhase phase, long dispatchId, boolean held)
			{
				if (_recordCommand && (_commandPhases.size() < 64)) { _commandPhases.add(dispatchId + ":" + phase); }
			}
		};
		_conversation = humanized == null ? new PhantomConversationService(catalog, new PhantomConversationStore(profiles, executionStore), contextPort, _semantic, social, plan -> { _latestResponse = plan; _execution.publish(plan); }, PhantomIdentityLeaseRegistry.getInstance(), ChatObservationService.getInstance(), phases) : new PhantomConversationService(catalog, new PhantomConversationStore(profiles, executionStore), contextPort, _semantic, social, plan -> { _latestResponse = plan; _execution.publish(plan); }, PhantomIdentityLeaseRegistry.getInstance(), ChatObservationService.getInstance(), PhantomClanDirectiveIngressPort.noop(), humanized);
		PhantomAssertions.assertTrue(_conversation.start(), "Full058 conversation did not start.");
		_client = PhantomConversationIntegrationSuite.NetworkBackedClient.attach(human);
	}

	org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge pvpBridge059()
	{
		return new org.l2jmobius.gameserver.phantoms.conversation.PhantomPvpConversationBridge(_execution);
	}

	void pulsePvp059() { _execution.onPulse(); }

	String socialMessage(Player human, Player companion, String text) throws Exception
	{
		return socialMessage059(human, companion, ChatType.WHISPER, text);
	}

	private String socialMessage059(Player human, Player companion, ChatType channel, String text) throws Exception
	{
		final long before = _execution.snapshot().sent();
		final long deliveries = ChatObservationService.getInstance().snapshot().generatedDeliveries();
		PhantomAssertions.assertTrue(PlayerChatIngress.dispatchClient(human, channel, channel == ChatType.WHISPER ? companion.getName() : "", text).observedDeliveries() > 0, "M3 addressed native input was not delivered.");
		for (int pulse = 0; (pulse < 512) && (_execution.snapshot().sent() == before); pulse++) { _conversation.onPulse(); _execution.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertEquals(before + 1, _execution.snapshot().sent(), "M3 response was not exactly one native SENT: " + _execution.snapshot());
		PhantomAssertions.assertTrue(ChatObservationService.getInstance().snapshot().generatedDeliveries() > deliveries, "M3 generated response had no recipient.");
		PhantomAssertions.assertTrue((_latestResponse != null) && _latestResponse.responseAct().equals("social.reply") && (_latestResponse.proposal() == null), "M3 social input acquired gameplay authority.");
		for (int pulse = 0; pulse < 32; pulse++) { _conversation.onPulse(); _execution.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertEquals(before + 1, _execution.snapshot().sent(), "M3 generated response recursed.");
		return _latestResponse.renderedText();
	}

	String socialContext059(PhantomTestContext context, Player human, Player companion, ChatType channel, String expectedStyle) throws Exception
	{
		final String input = companion.getName() + ", привет";
		final String text = socialMessage059(human, companion, channel, input);
		PhantomAssertions.assertEquals(expectedStyle, _latestResponse.style(), "Native greeting ignored its prepared relationship context.");
		context.record("social059.native." + expectedStyle, "channel=" + channel + "; actualSENT=true; style=" + _latestResponse.style() + "; text=" + text + "; evidence=" + _latestResponse.evidence());
		final long sent = _execution.snapshot().sent();
		PlayerChatIngress.dispatchClient(human, channel, "", input);
		for (int pulse = 0; pulse < 64; pulse++) { _conversation.onPulse(); _execution.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertEquals(sent, _execution.snapshot().sent(), "Immediate social repeat bypassed chatter bounds.");
		return text;
	}

	String supportMessage059(PhantomTestContext context, Player human, Player companion, String text) throws Exception
	{
		final long before = _execution.snapshot().sent();
		_latestResponse = null;
		PhantomAssertions.assertTrue(PlayerChatIngress.dispatchClient(human, ChatType.WHISPER, companion.getName(), text).observedDeliveries() > 0, "Support059 addressed input had no native recipient.");
		// No party tactical pulses: only the requested conversation path can start this cast.
		for (int pulse = 0; (pulse < 512) && (_execution.snapshot().sent() == before); pulse++) { _conversation.onPulse(); _execution.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertEquals(before + 1, _execution.snapshot().sent(), "Support059 response was not native SENT: " + _execution.snapshot());
		PhantomAssertions.assertTrue(_latestResponse != null, "Support059 has no originating response plan.");
		final var receipt = _executionStore.load(_latestResponse.ownerProfileId()).stream().flatMap(stored -> stored.state().receipts().stream()).filter(value -> value.observationHash().equals(_latestResponse.observationHash())).findFirst().orElseThrow();
		context.record("support059.reply." + text, "SENT reason=" + receipt.reasonKey() + "; proposal=" + _latestResponse.proposal());
		return receipt.reasonKey();
	}

	void invite(PhantomTestContext context, Player human, Player companion, long profileId, PhantomPartyCoordinator party) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		PhantomAssertions.assertEquals(PhantomDecisionEngine.AttachResult.ATTACHED, _engine.attach(profileId), "Full058 decision attach failed.");
		final long deliveries = ChatObservationService.getInstance().snapshot().generatedDeliveries();
		final var ingress = PlayerChatIngress.dispatchClient(human, ChatType.WHISPER, companion.getName(), "го кач?");
		PhantomAssertions.assertTrue(ingress.dispatched() && (ingress.observedDeliveries() > 0), "Full058 addressed speech was not delivered.");
		for (int pulse = 1; (pulse <= 256) && (PartyInvitationService.getInstance().observe(human).isEmpty() || (_execution.snapshot().sent() == 0)); pulse++)
		{
			_conversation.onPulse();
			_execution.onPulse();
			_engine.accept(new PhantomActivityWorkItem(profileId, PhantomActivityState.ACTIVE, 1, pulse, System.nanoTime(), PhantomActivityOverloadLevel.NORMAL));
			party.onPulse();
			Thread.sleep(10);
		}
		final var pending = PartyInvitationService.getInstance().observe(human).orElseThrow(() -> new AssertionError("Full058 invitation absent: " + _execution.snapshot()));
		PhantomAssertions.assertEquals(companion.getObjectId(), pending.requesterObjectId(), "Full058 invitation changed addressee.");
		PhantomAssertions.assertTrue(pending.managedRequester() && !pending.managedInvitee() && !human.isInParty(), "Full058 did not retain explicit TEST consent.");
		PhantomAssertions.assertTrue((_execution.snapshot().sent() > 0) && (ChatObservationService.getInstance().snapshot().generatedDeliveries() > deliveries), "Full058 has no delivered reply.");
		PhantomAssertions.assertEquals(PartyInvitationService.RespondOutcome.ACCEPTED, PartyInvitationService.getInstance().respond(human, PartyInvitationService.Response.ACCEPT, pending.identity()).outcome(), "Full058 TEST accept failed.");
		for (int pulse = 0; (pulse < 128) && !party.committed(profileId); pulse++)
		{
			party.onPulse();
			_execution.onPulse();
			Thread.sleep(10);
		}
		PhantomAssertions.assertTrue(party.committed(profileId), "Full058 native party did not commit.");
		PhantomAssertions.assertEquals(PartyInvitationService.MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().transferLeader(companion, human), "Full058 canonical human leadership failed.");
		party.onPulse();
		context.record("companion058.composition", "addressed WHISPER -> native delivered SENT -> managed-to-TEST invitation -> explicit TEST ACCEPT -> native leader transfer; profile=" + profileId + "; execution=" + _execution.snapshot());
	}

	void partyChat(PhantomTestContext context, Player human) throws Exception
	{
		final long sentBefore = _execution.snapshot().sent();
		final long queriesBefore = _execution.snapshot().queries();
		final long generatedBefore = ChatObservationService.getInstance().snapshot().generatedDeliveries();
		final var ingress = PlayerChatIngress.dispatchClient(human, ChatType.PARTY, "", "нужен танк");
		PhantomAssertions.assertTrue(ingress.dispatched() && (ingress.observedDeliveries() >= 3), "Full058 native partychat did not reach companions.");
		for (int pulse = 0; (pulse < 256) && (_execution.snapshot().sent() == sentBefore); pulse++)
		{
			_conversation.onPulse();
			_execution.onPulse();
			Thread.sleep(10);
		}
		PhantomAssertions.assertEquals(sentBefore + 1, _execution.snapshot().sent(), "Partychat did not produce exactly one elected delivered native reply.");
		PhantomAssertions.assertEquals(queriesBefore + 1, _execution.snapshot().queries(), "Partychat reply did not query current canonical party facts.");
		PhantomAssertions.assertTrue(ChatObservationService.getInstance().snapshot().generatedDeliveries() > generatedBefore, "Partychat reply had no concrete recipient.");
		PlayerChatIngress.dispatchClient(human, ChatType.PARTY, "", "нужен танк");
		for (int pulse = 0; pulse < 32; pulse++) { _conversation.onPulse(); _execution.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertEquals(sentBefore + 1, _execution.snapshot().sent(), "Immediate partychat repeat or generated reply caused chatter recursion.");
		context.record("companion058.partychat", "native delivery to current party recipients=" + ingress.observedDeliveries() + "; one elected actual-role/vacancy reply delivered; immediate repeat bounded; execution=" + _execution.snapshot());
	}

	boolean command059(PhantomTestContext context, Player human, Player companion, String text, PhantomPartyCoordinator party) throws Exception
	{
		return command059(context, human, companion, ChatType.WHISPER, text, party);
	}

	boolean command059(PhantomTestContext context, Player human, Player companion, ChatType channel, String text, PhantomPartyCoordinator party) throws Exception
	{
		final long before = _execution.snapshot().sent();
		_latestResponse = null;
		_lastCommandReceipt = null;
		_recordCommand = true;
		_commandPhases.clear();
		_contextDiagnostic = "none";
		PhantomAssertions.assertTrue(PlayerChatIngress.dispatchClient(human, channel, channel == ChatType.WHISPER ? companion.getName() : "", text).observedDeliveries() > 0, "Command059 ingress had no exact recipient.");
		for (int pulse = 0; (pulse < 512) && (_execution.snapshot().sent() == before); pulse++)
		{
			_conversation.onPulse();
			_execution.onPulse();
			party.onPulse();
			Thread.sleep(10);
		}
		context.record("command059.reply." + text, _latestResponse == null ? "none" : _latestResponse.toString());
		context.record("command059.owner." + text, "conversation=" + _conversation.snapshot() + "; execution=" + _execution.snapshot());
		context.record("command059.context." + text, _contextDiagnostic);
		context.record("command059.phases." + text, _commandPhases.toString());
		_recordCommand = false;
		if (_latestResponse != null)
		{
			for (int pulse = 0; (pulse < 32) && (_lastCommandReceipt == null); pulse++)
			{
				_execution.onPulse();
				_lastCommandReceipt = _executionStore.load(_latestResponse.ownerProfileId()).stream().flatMap(stored -> stored.state().receipts().stream()).filter(receipt -> receipt.observationHash().equals(_latestResponse.observationHash())).findFirst().orElse(null);
			}
		}
		context.record("command059.receipt." + text, String.valueOf(_lastCommandReceipt));
		return _execution.snapshot().sent() == before + 1;
	}

	void assertCommandReason059(String reason)
	{
		PhantomAssertions.assertTrue((_lastCommandReceipt != null) && (_lastCommandReceipt.outboundState() == org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.OutboundState.SENT), "Command059 has no exact durable native SENT receipt.");
		PhantomAssertions.assertEquals(reason, _lastCommandReceipt.reasonKey(), "Command059 delivered a false action result.");
	}

	void holdAndResume059(PhantomTestContext context, Player human, java.util.List<Player> actors, PhantomPartyCoordinator party, int destinationX, int destinationY, int destinationZ) throws Exception
	{
		final Player companion = actors.getFirst();
		final int sceneX = human.getX(), sceneY = human.getY(), sceneZ = human.getZ();
		final int anchorX = companion.getX(), anchorY = companion.getY(), anchorZ = companion.getZ();
		final var path = org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding.getInstance().findPath(human.getX(), human.getY(), human.getZ(), destinationX, destinationY, destinationZ, 0, true);
		PhantomAssertions.assertTrue((path != null) && !path.isEmpty(), "Command059 declared native reverse obstacle route unavailable.");
		context.record("command059.input", "TEST same058t seed58005824; directed first of3; HOLD radius128; observe20s; native reverse obstacle route; no TP/root/resource grants; actor=" + companion.getObjectId() + "; leader=" + human.getObjectId() + "; anchor=" + anchorX + ',' + anchorY + ',' + anchorZ);
		final boolean holdDelivered = command059(context, human, companion, "стой здесь", party);
		PhantomAssertions.assertTrue(holdDelivered, "Command059 admission failed: " + _contextDiagnostic + "; phases=" + _commandPhases);
		int waypoint = 0;
		double maximumDisplacement = 0;
		final long holdDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < holdDeadline)
		{
			if (waypoint < path.size())
			{
				var point = path.get(waypoint);
				if (Math.hypot(human.getX() - point.getX(), human.getY() - point.getY()) <= 64) { waypoint++; }
				if ((waypoint < path.size()) && !human.isMoving()) { point = path.get(waypoint); human.moveToLocation(point.getX(), point.getY(), point.getZ(), 0); }
			}
			party.onPulse();
			maximumDisplacement = Math.max(maximumDisplacement, Math.sqrt(Math.pow(companion.getX() - anchorX, 2) + Math.pow(companion.getY() - anchorY, 2) + Math.pow(companion.getZ() - anchorZ, 2)));
			Thread.sleep(20);
		}
		context.record("command059.hold", "maximumDisplacement=" + maximumDisplacement + "; leader=" + human.getX() + ',' + human.getY() + ',' + human.getZ() + "; actor=" + companion.getX() + ',' + companion.getY() + ',' + companion.getZ() + "; path=" + waypoint + '/' + path.size());
		PhantomAssertions.assertTrue((waypoint == path.size()) && (Math.hypot(human.getX() - anchorX, human.getY() - anchorY) >= 500), "Command059 leader did not actually depart.");
		PhantomAssertions.assertTrue(maximumDisplacement <= 128, "Directed HOLD did not control native companion movement: " + maximumDisplacement);
		PhantomAssertions.assertTrue(holdDelivered, "HOLD response had no native recipient.");
		PhantomAssertions.assertTrue(actors.subList(1, actors.size()).stream().allMatch(actor -> Math.hypot(actor.getX() - human.getX(), actor.getY() - human.getY()) <= 350), "Directed HOLD incorrectly stopped other companions.");
		PhantomAssertions.assertTrue(command059(context, human, companion, "за мной", party), "RESUME response had no native recipient.");
		final long resumeDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
		while ((System.nanoTime() < resumeDeadline) && (companion.isMoving() || Math.hypot(companion.getX() - human.getX(), companion.getY() - human.getY()) > 350)) { party.onPulse(); Thread.sleep(20); }
		context.record("command059.resume", "actor=" + companion.getX() + ',' + companion.getY() + ',' + companion.getZ() + "; leaderDistance=" + Math.hypot(companion.getX() - human.getX(), companion.getY() - human.getY()));
		PhantomAssertions.assertTrue(Math.hypot(companion.getX() - human.getX(), companion.getY() - human.getY()) <= 350, "RESUME did not restore native obstacle-follow.");
		// Return by ordinary walking to the already verified field before the unchanged stock kill scene.
		walkAndRegroup059(context, human, actors, party, sceneX, sceneY, sceneZ);
	}

	private void walkAndRegroup059(PhantomTestContext context, Player human, java.util.List<Player> actors, PhantomPartyCoordinator party, int sceneX, int sceneY, int sceneZ) throws Exception
	{
		walkAndRegroup059(context, human, actors, party, sceneX, sceneY, sceneZ, () -> { });
	}

	void walkAndRegroup059(PhantomTestContext context, Player human, java.util.List<Player> actors, PhantomPartyCoordinator party, int sceneX, int sceneY, int sceneZ, Runnable additionalPulse) throws Exception
	{
		final var returnPath = org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding.getInstance().findPath(human.getX(), human.getY(), human.getZ(), sceneX, sceneY, sceneZ, 0, true);
		PhantomAssertions.assertTrue((returnPath != null) && !returnPath.isEmpty(), "Command059 native field return path unavailable.");
		int returnWaypoint = 0;
		final long returnDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
		while ((System.nanoTime() < returnDeadline) && ((returnWaypoint < returnPath.size()) || human.isMoving() || actors.stream().anyMatch(actor -> actor.isMoving() || (Math.hypot(actor.getX() - human.getX(), actor.getY() - human.getY()) > 350))))
		{
			if (returnWaypoint < returnPath.size())
			{
				var point = returnPath.get(returnWaypoint);
				if (Math.hypot(human.getX() - point.getX(), human.getY() - point.getY()) <= 64) { returnWaypoint++; }
				if ((returnWaypoint < returnPath.size()) && !human.isMoving()) { point = returnPath.get(returnWaypoint); human.moveToLocation(point.getX(), point.getY(), point.getZ(), 0); }
			}
			party.onPulse();
			additionalPulse.run();
			Thread.sleep(20);
		}
		context.record("command059.fieldReturn", "nativeWaypoints=" + returnWaypoint + '/' + returnPath.size() + "; human=" + human.getX() + ',' + human.getY() + ',' + human.getZ());
		PhantomAssertions.assertTrue((returnWaypoint == returnPath.size()) && actors.stream().allMatch(actor -> Math.hypot(actor.getX() - human.getX(), actor.getY() - human.getY()) <= 350), "Command059 native field return did not regroup companions.");
	}

	void authority059(PhantomTestContext context, Player human, java.util.List<Player> actors, java.util.List<Long> profiles, PhantomPartyCoordinator party, int destinationX, int destinationY, int destinationZ) throws Exception
	{
		final Player addressed = actors.get(1);
		final long addressedProfile = profiles.get(1);
		PhantomAssertions.assertTrue(command059(context, human, addressed, ChatType.PARTY, addressed.getName() + ", стой здесь", party), "Directed PARTY command was not delivered.");
		awaitHolding059(context, party, java.util.List.of(addressedProfile));
		PhantomAssertions.assertEquals(addressedProfile, _latestResponse.ownerProfileId(), "Directed PARTY election selected another companion.");
		final var held = party.claim(addressedProfile).orElseThrow().state().command();
		PhantomAssertions.assertTrue(held != null && held.mode() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD, "Directed PARTY HOLD was not applied.");
		PhantomAssertions.assertFalse(profiles.stream().filter(profile -> profile != addressedProfile).anyMatch(profile -> party.claim(profile).orElseThrow().state().command() != null && party.claim(profile).orElseThrow().state().command().mode() != org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.FOLLOW), "Directed PARTY command affected another companion.");
		party.commandCompanion(addressedProfile, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef.real(human.getObjectId()), held.groupGeneration(), org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD, held.requestId());
		PhantomAssertions.assertEquals(held, party.claim(addressedProfile).orElseThrow().state().command(), "An exact repeated command changed its version or anchor.");
		command059(context, human, addressed, "не иди за мной", party);
		PhantomAssertions.assertEquals(held, party.claim(addressedProfile).orElseThrow().state().command(), "Negation resumed a held companion.");
		command059(context, human, addressed, ChatType.PARTY, actors.getFirst().getName() + ", за мной, " + addressed.getName(), party);
		PhantomAssertions.assertEquals(held, party.claim(addressedProfile).orElseThrow().state().command(), "Ambiguous PARTY address changed a held companion.");
		PhantomAssertions.assertTrue(command059(context, human, actors.getFirst(), ChatType.PARTY, "всем стоять", party), "Explicit ALL HOLD was not delivered.");
		awaitHolding059(context, party, profiles);
		final int sceneX = human.getX(), sceneY = human.getY(), sceneZ = human.getZ();
		final int[][] anchors = actors.stream().map(actor -> new int[] {actor.getX(), actor.getY(), actor.getZ()}).toArray(int[][]::new);
		for (long profile : profiles) { PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD, party.claim(profile).orElseThrow().state().command().mode(), "ALL command omitted a current native companion."); }
		final var path = org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding.getInstance().findPath(human.getX(), human.getY(), human.getZ(), destinationX, destinationY, destinationZ, 0, true);
		PhantomAssertions.assertTrue(path != null && !path.isEmpty(), "ALL command native walking input unavailable.");
		int waypoint = 0;
		final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
		while ((System.nanoTime() < deadline) && ((waypoint < path.size()) || human.isMoving()))
		{
			if (waypoint < path.size())
			{
				var point = path.get(waypoint);
				if (Math.hypot(human.getX() - point.getX(), human.getY() - point.getY()) <= 64) { waypoint++; }
				if ((waypoint < path.size()) && !human.isMoving()) { point = path.get(waypoint); human.moveToLocation(point.getX(), point.getY(), point.getZ(), 0); }
			}
			party.onPulse();
			for (int index = 0; index < actors.size(); index++) { final Player actor = actors.get(index); PhantomAssertions.assertTrue(Math.sqrt(Math.pow(actor.getX() - anchors[index][0], 2) + Math.pow(actor.getY() - anchors[index][1], 2) + Math.pow(actor.getZ() - anchors[index][2], 2)) <= 128, "ALL HOLD allowed native pursuit."); }
			Thread.sleep(20);
		}
		PhantomAssertions.assertTrue(waypoint == path.size() && Math.hypot(human.getX() - sceneX, human.getY() - sceneY) >= 500, "ALL HOLD leader did not actually depart.");
		PhantomAssertions.assertTrue(command059(context, human, actors.getFirst(), ChatType.PARTY, "всем за мной", party), "ALL RESUME was not delivered.");
		assertCommandReason059("command.following");
		for (long profile : profiles) { PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.FOLLOW, party.claim(profile).orElseThrow().state().command().mode(), "ALL RESUME omitted a current native companion."); }
		walkAndRegroup059(context, human, actors, party, sceneX, sceneY, sceneZ);
		PhantomAssertions.assertEquals(PartyInvitationService.MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().transferLeader(human, actors.getLast()), "Native leader-change input failed.");
		party.onPulse();
		PhantomAssertions.assertTrue(command059(context, human, actors.getFirst(), "подожди", party), "Former leader did not receive a refusal.");
		assertCommandReason059("command.rejected");
		PhantomAssertions.assertEquals(PartyInvitationService.MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().transferLeader(actors.getLast(), human), "Native leadership restore failed.");
		party.onPulse();
		PhantomAssertions.assertEquals(PhantomPartyCoordinator.CompanionCommandOutcome.REJECTED, party.commandCompanion(addressedProfile, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef.real(human.getObjectId()), held.groupGeneration(), org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD, held.requestId()), "Old generation revived a command after native leader changes.");
		PhantomAssertions.assertTrue(profiles.stream().allMatch(profile -> party.claim(profile).orElseThrow().state().command() == null), "Leader changes retained a former command.");
		context.record("command059.authority", "directed PARTY recipient; exact retry stable; negation/ambiguous no action; explicit ALL native HOLD/walk/RESUME; one elected SENT each; former-leader delivered refusal; stale generation rejected; native authority restored; current roster=" + human.getParty().getMembers().stream().map(Player::getObjectId).toList());
	}

	void awaitHolding059(PhantomTestContext context, PhantomPartyCoordinator party, java.util.List<Long> profiles) throws Exception
	{
		PhantomAssertions.assertTrue(_lastCommandReceipt != null && java.util.Set.of("command.holding", "command.pending").contains(_lastCommandReceipt.reasonKey()), "HOLD admission did not deliver a truthful holding/pending result.");
		final String reason = _lastCommandReceipt.reasonKey();
		final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline)
		{
			party.onPulse();
			if (profiles.stream().allMatch(profile -> party.claim(profile).orElseThrow().state().command() != null && party.claim(profile).orElseThrow().state().command().mode() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD))
			{
				context.record("command059.safe-hold." + profiles.getFirst(), "SENT reason=" + reason + "; all actual owners reached HOLD after native action boundary; profiles=" + profiles);
				return;
			}
			Thread.sleep(10);
		}
		throw new AssertionError("Accepted HOLD did not reach its native safe boundary within10s.");
	}

	void lifecycle059(PhantomTestContext context, Player human, java.util.List<Player> actors, java.util.List<Long> profiles, PhantomPartyCoordinator party) throws Exception
	{
		final Player actor = actors.getLast();
		final long profile = profiles.getLast();
		PhantomAssertions.assertTrue(command059(context, human, actor, "стой здесь", party), "Pre-expel HOLD was not delivered.");
		final var old = party.claim(profile).orElseThrow().state().command();
		PhantomAssertions.assertTrue(old != null, "Pre-expel command was not durable.");
		PhantomAssertions.assertEquals(PartyInvitationService.MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().expel(human, actor), "Native exact companion expel failed.");
		for (int pulse = 0; pulse < 32; pulse++) { party.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertTrue(party.claim(profile).orElseThrow().state().command() == null && !party.blocksBackground(profile), "Native expel retained command authority or party pin.");
		PhantomAssertions.assertTrue(PartyInvitationService.getInstance().invite(human, actor, org.l2jmobius.gameserver.model.groups.PartyDistributionType.FINDERS_KEEPERS.getId()).delivered(), "Exact companion rejoin invitation failed.");
		for (int pulse = 0; (pulse < 128) && !party.committed(profile); pulse++) { party.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertTrue(party.committed(profile) && actor.getParty() == human.getParty() && party.claim(profile).orElseThrow().state().command() == null, "Rejoin revived an old HOLD or lost membership.");
		PhantomAssertions.assertEquals(PhantomPartyCoordinator.CompanionCommandOutcome.REJECTED, party.commandCompanion(profile, org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef.real(human.getObjectId()), old.groupGeneration(), org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.CompanionMode.HOLD, old.requestId()), "Expelled generation regained authority after rejoin.");
		PhantomAssertions.assertTrue(command059(context, human, actor, "подожди", party), "Pre-death HOLD was not delivered.");
		context.record("command059.lifecycle.input", "INDEPENDENT native lifecycle episode after both full kills: exact expel/rejoin; then owned TEST actor native death; no active encounter or rewards altered");
		PhantomAssertions.assertTrue(actor.doDie(null), "Declared native TEST death failed.");
		for (int pulse = 0; pulse < 32; pulse++) { party.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertTrue(actor.isDead() && party.claim(profile).orElseThrow().state().command() == null, "Native death retained a command.");
		actor.doRevive();
		for (int pulse = 0; pulse < 16; pulse++) { party.onPulse(); Thread.sleep(10); }
		PhantomAssertions.assertTrue(!actor.isDead() && party.claim(profile).orElseThrow().state().command() == null, "Native revival restored stale authority.");
		context.record("command059.lifecycle.result", "exact expel/rejoin invalidates generation; native death/revival clears command; old command cannot revive; leave/logout checks follow");
	}

	@Override
	public void close() throws Exception
	{
		_conversation.beginStop();
		PhantomAssertions.assertTrue(_conversation.finishStop(), "Full058 conversation did not stop.");
		_execution.beginStop();
		PhantomAssertions.assertTrue(_execution.finishStop(), "Full058 execution did not stop.");
		_engine.beginStop();
		PhantomAssertions.assertTrue(_engine.finishStop(), "Full058 decision did not stop.");
		_semantic.beginStop();
		_semantic.finishStop();
		_client.close();
	}
}

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
	private final PhantomConversationService _conversation;
	private final PhantomSemanticUnderstandingService _semantic;
	private final PhantomConversationIntegrationSuite.NetworkBackedClient _client;

	PhantomCompanionComposition058(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomTopologyQuery topology, PhantomGameKnowledgeService knowledge, PhantomSocialService social, PhantomPartyCoordinator party, Player human) throws Exception
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
		final PhantomConversationExecutionCatalog executionCatalog = PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v2.xml"));
		final PhantomConversationExecutionStore executionStore = new PhantomConversationExecutionStore(profiles, executionCatalog);
		_execution = new PhantomConversationExecutionService(executionCatalog, executionStore, goals, new L2jPhantomConversationExecutionPort(executionCatalog, knowledge, topology, party, materialization, ChatObservationService.getInstance()), PhantomConversationGoalRuntimePort.decisionEngine(_engine));
		PhantomAssertions.assertTrue(_execution.start(), "Full058 execution did not start.");
		_semantic = PhantomSemanticUnderstandingService.loaded(PhantomSemanticPack.load(Path.of("data/phantoms/semantic/high-five-ru-semantic-v1.xml"), Path.of("data/phantoms/semantic/high-five-ru-corpus-v1.tsv"), PhantomSemanticGrounding.fixed(PhantomConversationIntegrationSuite.HASHES, PhantomConversationIntegrationSuite.references())));
		PhantomAssertions.assertTrue(_semantic.start(), "Full058 semantic did not start.");
		final PhantomConversationCatalog catalog = PhantomConversationCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-v1.xml"), Path.of("data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"));
		_conversation = new PhantomConversationService(catalog, new PhantomConversationStore(profiles, executionStore), new L2jPhantomConversationContextPort(materialization, topology), _semantic, social, _execution::publish, PhantomIdentityLeaseRegistry.getInstance(), ChatObservationService.getInstance());
		PhantomAssertions.assertTrue(_conversation.start(), "Full058 conversation did not start.");
		_client = PhantomConversationIntegrationSuite.NetworkBackedClient.attach(human);
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
		final var ingress = PlayerChatIngress.dispatchClient(human, ChatType.PARTY, "", "идём вместе");
		PhantomAssertions.assertTrue(ingress.dispatched() && (ingress.observedDeliveries() >= 3), "Full058 native partychat did not reach companions.");
		for (int pulse = 0; pulse < 8; pulse++)
		{
			_conversation.onPulse();
			_execution.onPulse();
		}
		context.record("companion058.partychat", "native delivery to current party recipients=" + ingress.observedDeliveries() + "; execution=" + _execution.snapshot());
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

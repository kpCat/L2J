/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.handler.ChatHandler;
import org.l2jmobius.gameserver.handler.IChatHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationSnapshot;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.network.GameClient;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.DecisionView;
import org.l2jmobius.gameserver.phantoms.PhantomSelectedDecisionTrace.SelectionStatus;
import org.l2jmobius.gameserver.phantoms.PhantomScheduler.SignalStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionModel.ExecutionEntry;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationExecutionStore.StoredExecution;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.ConversationSession;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStateCodec;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ManagedProfile;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService.StoredState;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomCrossDomainAutonomousAlphaGoal030Checkpoint2Suite implements PhantomTestSuite
{
	private static final long SEED = 30003002L;
	private static final String ACTIVE_SOURCE = "goal030.cp2.active";
	private static final long ACTIVE_TTL_MILLIS = 600_000L;

	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final PhantomPopulationStateCodec _populationCodec = new PhantomPopulationStateCodec();
	private final PhantomConversationStateCodec _conversationCodec = new PhantomConversationStateCodec();
	private final PhantomGoalStateCodec _goalCodec = new PhantomGoalStateCodec();
	private PhantomProfileRepository _profiles;
	private PhantomScheduler _scheduler;
	private PhantomMaterializationService _materialization;
	private PhantomSocialStore _socialStore;
	private PhantomConversationExecutionStore _executionStore;
	private ManagedProfile _managed;
	private PhantomPopulationState _population;
	private Player _human;
	private Player _phantom;
	private HeadlessPlayerOutboundSession _humanOutput;
	private Player.OutboundSessionAttachment _humanOutputAttachment;
	private IChatHandler _nativeWhisper;
	private CapturingWhisperHandler _whisper;
	private StoredState _socialBeforeParty;
	private StoredState _socialAfterParty;
	private StoredState _socialBeforeOffline;
	private long _populationStartedNanos;
	private long _materializedNanos;
	private boolean _environmentInitialized;
	private boolean _shutdownComplete;

	@Override
	public String id()
	{
		return "cross-domain-autonomous-alpha-goal030cp2";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030 CP2 suite used the wrong seed.");
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "BLOCKED_030CP2_TEST_DB_NOT_CLEAN: configured owner exists before CP2.");
		_environment.initialize(context);
		_environmentInitialized = true;
		_profiles = PhantomProfileRepository.open();
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles"), "BLOCKED_030CP2_TEST_DB_NOT_CLEAN: Phantom profiles remain.");
		PhantomAssertions.assertTrue(_profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 2).isEmpty(), "BLOCKED_030CP2_TEST_DB_NOT_CLEAN: managed Population residue remains.");
		PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_environment.primary().objectId()), "BLOCKED_030CP2_TEST_DB_NOT_CLEAN: human fixture has a PHANTOM lease.");

		_human = Player.load(_environment.primary().objectId());
		PhantomAssertions.assertTrue(_human != null, "Ordinary CP2 human could not load.");
		_humanOutput = new HeadlessPlayerOutboundSession(16, 128, 256);
		_humanOutputAttachment = _human.attachOutboundSession(_humanOutput);
		_human.spawnMe();

		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		_nativeWhisper = ChatHandler.getInstance().getHandler(ChatType.WHISPER);
		PhantomAssertions.assertTrue(_nativeWhisper != null, "Native WHISPER handler is absent.");
		final PhantomConversationExecutionCatalog executionCatalog = PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v1.xml"));
		_executionStore = new PhantomConversationExecutionStore(_profiles, executionCatalog);
		_socialStore = new PhantomSocialStore(_profiles, PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml")));
		_whisper = new CapturingWhisperHandler(_nativeWhisper);
		ChatHandler.getInstance().registerHandler(_whisper);

		final PhantomPlayersConfig.Settings settings = new PhantomPlayersConfig.Settings(true, true, 1, 4, 100, 4, 1, 0, 1, 8, 16, 32, ZoneOffset.UTC);
		context.record("goal030cp2.settings", "enabled=true,diagnostics=true,maxMaterialized=1,maxScheduled=4,pulse=100,profilesPerPulse=4,populationTarget=1,populationActiveTarget=0,creationInFlight=1,boundaries=8,partyOps=16,socialCache=32,zone=UTC");
		final long started = System.nanoTime();
		PhantomAssertions.assertTrue(PhantomSystem.startConfiguredForTesting(settings), "BLOCKED_030CP2_REQUIRED_SEAM_ABSENT: full production PhantomSystem did not start.");
		_populationStartedNanos = started;
		_scheduler = Objects.requireNonNull(PhantomSystem.configuredScheduler(), "Configured Scheduler is absent.");
		_materialization = Objects.requireNonNull(PhantomSystem.configuredMaterializationService(), "Configured MaterializationService is absent.");
		await(30_000, this::refreshReadyPopulation, "Population did not create exactly one READY Phantom within 30 seconds.");
		PhantomAssertions.assertEquals(1, _scheduler.snapshot().registered(), "Population did not register exactly one Scheduler profile.");
		PhantomAssertions.assertTrue(_scheduler.find(_managed.profile().profileId()).isPresent(), "Population-created profile is absent from Scheduler.");
		PhantomAssertions.assertEquals(SelectionStatus.SELECTED, PhantomSystem.selectOperatorTrace(_managed.profile().profileId()), "Selected production trace could not attach.");
		context.record("goal030cp2.populationMillis", elapsedMillis(started));
		context.record("goal030cp2.identity.profileId", _managed.profile().profileId());
		context.record("goal030cp2.identity.characterObjectId", _population.actualCharacterObjectId());
		context.record("goal030cp2.identity.name", _population.characterName());
		context.record("goal030cp2.identity.classSex", _population.classId() + "/" + (_population.female() ? "female" : "male"));
		context.record("goal030cp2.identity.archetype", "not-exposed-by-population-state");
		context.record("goal030cp2.identity.schedule", _population.scheduleTemplate() + "/" + _population.schedulePhaseMinutes());
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-production-population-scheduler-materialization", this::materializeAndObserveAutonomy);
		registry.add("02-real-whisper-party-invite-accept-social", this::inviteAcceptAndRemember);
		registry.add("03-real-whisper-item57-generated-answer", this::queryAdena);
		registry.add("04-real-whisper-party-leave", this::leaveParty);
		registry.add("05-withdraw-reactivate-same-memory", this::offlineOnlineContinuity);
		registry.add("06-canonical-shutdown-exact-cleanup", this::shutdownAndCleanup);
	}
	private void materializeAndObserveAutonomy(PhantomTestContext context) throws Exception
	{
		final long started = System.nanoTime();
		final SignalStatus status = _scheduler.submitSignal(_managed.profile().profileId(), new PhantomRelevanceSignal(ACTIVE_SOURCE, 1, PhantomActivityState.ACTIVE, ACTIVE_TTL_MILLIS)).status();
		PhantomAssertions.assertTrue((status == SignalStatus.ACCEPTED) || (status == SignalStatus.COALESCED), "CP2 ACTIVE signal was not accepted by the real Scheduler.");
		await(10_000, () -> _materialization.find(_managed.profile().profileId()).filter(snapshot -> snapshot.worldPresent() && snapshot.outboundAttached() && snapshot.identityLeaseRetained()).isPresent(), "Scheduler ACTIVE did not materialize the Population Phantom within 10 seconds.");
		_materializedNanos = System.nanoTime();
		_phantom = World.getInstance().getPlayer(_population.actualCharacterObjectId());
		PhantomAssertions.assertTrue(_phantom != null, "Materialized CP2 Phantom is absent from World.");
		PhantomAssertions.assertTrue(_phantom.hasHeadlessOutboundSession(), "Materialized CP2 Phantom has no headless outbound session.");
		PhantomAssertions.assertEquals(OwnerKind.PHANTOM, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_phantom.getObjectId()), "Materialized CP2 Phantom has no PHANTOM lease.");
		PhantomAssertions.assertEquals(1, _materialization.snapshot().maximumMaterialized(), "Production materialization cap drifted.");
		final String humanOrigin = _human.getX() + "," + _human.getY() + "," + _human.getZ();
		_human.setXYZ(_phantom.getX() + 20, _phantom.getY(), _phantom.getZ());
		PhantomAssertions.assertTrue(_human.isVisibleFor(_phantom) && _phantom.isVisibleFor(_human), "CP2 participants are not mutually visible for canonical Party invitation.");
		context.record("goal030cp2.fixtureVisibility", "humanOrigin=" + humanOrigin + ",phantom=" + _phantom.getX() + "," + _phantom.getY() + "," + _phantom.getZ() + ",distance=20");
		PhantomAssertions.assertTrue(_materialization.snapshot().retainedEntries() <= 1, "Production materialization exceeded cap one.");
		PhantomAssertions.assertTrue(PhantomSystem.operatorStatus().activePeak() <= 1, "Production ACTIVE peak exceeded cap one.");

		await(10_000, () -> autonomousEvidence() != null, "BLOCKED_030CP2_AUTONOMOUS_DECISION_NOT_OBSERVED: no non-Population production candidate was evaluated.");
		final CandidateEvidence evidence = autonomousEvidence();
		context.record("goal030cp2.materializationMillis", elapsedMillis(started));
		context.record("goal030cp2.materialization.headlessLeaseCap", "true/PHANTOM/1");
		context.record("goal030cp2.autonomousCandidate", evidence.candidateKey());
		context.record("goal030cp2.autonomousCandidateStatus", evidence.status());
		awaitConversationReadiness(context);
	}

	private void inviteAcceptAndRemember(PhantomTestContext context) throws Exception
	{
		final int callsBefore = _whisper.calls();
		final long clientBefore = ChatObservationService.getInstance().snapshot().clientDeliveries();
		dispatchWhisper("пригласи меня");
		await(10_000, () -> PartyInvitationService.getInstance().observe(_human).isPresent(), "Conversation party.invite did not produce a real Party invitation.");
		final InvitationSnapshot invitation = PartyInvitationService.getInstance().observe(_human).orElseThrow();
		PhantomAssertions.assertEquals(_phantom.getObjectId(), invitation.requesterObjectId(), "Party invitation requester is not the CP2 Phantom.");
		PhantomAssertions.assertEquals(_human.getObjectId(), invitation.inviteeObjectId(), "Party invitation target is not the CP2 human.");
		PhantomAssertions.assertTrue(invitation.managedRequester() && !invitation.managedInvitee(), "Party invitation did not use managed-to-ordinary delivery.");
		PhantomAssertions.assertEquals(callsBefore + 1, _whisper.calls(), "Inbound party request invoked WHISPER handler more than once.");
		PhantomAssertions.assertTrue(ChatObservationService.getInstance().snapshot().clientDeliveries() > clientBefore, "Real party request produced no CLIENT_CHAT delivery.");
		assertSemantic("party.invite", null);

		_socialBeforeParty = _socialStore.load(_managed.profile().profileId()).orElseThrow(() -> new AssertionError("Conversation did not initialize durable SocialStore state before Party formation."));
		final int receiptsBefore = _socialBeforeParty.receipts().receipts().size();
		final RespondOutcome outcome = PartyInvitationService.getInstance().respond(_human, Response.ACCEPT, invitation.identity()).outcome();
		PhantomAssertions.assertEquals(RespondOutcome.ACCEPTED, outcome, "Ordinary human did not accept the exact real Party invitation.");
		await(10_000, () -> (_human.getParty() != null) && (_human.getParty() == _phantom.getParty()) && _human.getParty().containsPlayer(_phantom), "Canonical Party did not contain both CP2 participants.");
		await(10_000, () ->
		{
			final StoredState current = _socialStore.load(_managed.profile().profileId()).orElse(null);
			return (current != null) && (current.stateRowVersion() > _socialBeforeParty.stateRowVersion()) && (current.receipts().receipts().size() > receiptsBefore) && (current.state().relationship(SubjectRef.character(_human.getObjectId())) != null);
		}, "Canonical Party acceptance did not durably advance SocialStore.");
		_socialAfterParty = _socialStore.load(_managed.profile().profileId()).orElseThrow();
		PhantomAssertions.assertTrue(PartyInvitationService.getInstance().observe(_human).isEmpty(), "Accepted Party retained a stale invitation.");
		PhantomAssertions.assertEquals(1, Collections.frequency(_humanOutput.snapshot().recordedPacketClasses(), "AskJoinParty"), "Human received other than exactly one real Party prompt.");
		await(10_000, () -> selectedEvidence("candidate.party.form") != null, "Conversation party.invite produced no selected candidate.party.form Decision evidence.");
		final CandidateEvidence partyDecision = selectedEvidence("candidate.party.form");
		context.record("goal030cp2.utterance1", "пригласи меня -> party.invite -> party.form");
		context.record("goal030cp2.party", "invitation=" + invitation.identity().sequence() + ",canonicalMembers=" + _human.getParty().getMemberCount());
		context.record("goal030cp2.partyDecision", partyDecision.candidateKey() + "/" + partyDecision.status());
		context.record("goal030cp2.social.traits", _socialAfterParty.state().traits());
		context.record("goal030cp2.social.relationship", _socialAfterParty.state().relationship(SubjectRef.character(_human.getObjectId())));
		context.record("goal030cp2.social.receiptDelta", _socialAfterParty.receipts().receipts().size() - receiptsBefore);
		context.record("goal030cp2.social.events", _socialAfterParty.state().memories().stream().map(memory -> memory.eventCode()).toList());
		awaitConversationQuiescence(context, "party-invite-accept-social");
	}

	private void queryAdena(PhantomTestContext context) throws Exception
	{
		final long generatedBefore = ChatObservationService.getInstance().snapshot().generatedDeliveries();
		final int callsBefore = _whisper.calls();
		dispatchWhisper("где взять адену");
		awaitAdenaResponse(generatedBefore, callsBefore);
		assertSemantic("item.acquire.query", "57");
		final CapturedMessage response = _whisper.lastGenerated();
		PhantomAssertions.assertTrue(!response.text().isBlank() && !response.text().equals("где взять адену"), "Generated adena response text is absent or looped back.");
		PhantomAssertions.assertEquals("item.acquire", response.proposalKey(), "Adena response did not use production acquisition authority.");
		PhantomAssertions.assertEquals(generatedBefore + 1, ChatObservationService.getInstance().snapshot().generatedDeliveries(), "Adena query produced duplicate generated delivery.");
		context.record("goal030cp2.utterance2", "где взять адену -> item.acquire.query -> ITEM57");
		context.record("goal030cp2.adena.response", response.text());
		context.record("goal030cp2.adena.style", response.style());
		context.record("goal030cp2.adena.outbound", "generatedDeliveries=1,proposal=" + response.proposalKey());
		awaitConversationQuiescence(context, "item57-generated-response");
	}
	private void leaveParty(PhantomTestContext context) throws Exception
	{
		final int receiptsBefore = _socialStore.load(_managed.profile().profileId()).orElseThrow().receipts().receipts().size();
		dispatchWhisper("покинь группу");
		await(10_000, () -> !_phantom.isInParty() && !_human.isInParty(), "Conversation party.leave did not reach canonical Party leave.");
		assertSemantic("party.leave", null);
		PhantomAssertions.assertTrue(PartyInvitationService.getInstance().observe(_human).isEmpty(), "Party leave retained a stale invitation.");
		await(10_000, () -> _socialStore.load(_managed.profile().profileId()).map(state -> state.receipts().receipts().size() > receiptsBefore).orElse(false), "Party leave did not durably advance the social receipt.");
		final PhantomProfile current = _profiles.find(_managed.profile().profileId()).orElseThrow();
		final var goalComponent = _profiles.findComponent(current.profileId(), PhantomGoalStateStore.COMPONENT_TYPE);
		if (goalComponent.isPresent())
		{
			final PhantomGoal goal = _goalCodec.decode(goalComponent.get().payload());
			PhantomAssertions.assertFalse((goal.status() == PhantomGoalStatus.ACTIVE) && goal.goalType().equals("party.leave"), "Party leave retained an ACTIVE conversation Goal.");
		}
		context.record("goal030cp2.utterance3", "покинь группу -> party.leave -> canonical leave");
		context.record("goal030cp2.leave.socialReceiptDelta", _socialStore.load(current.profileId()).orElseThrow().receipts().receipts().size() - receiptsBefore);
		awaitConversationQuiescence(context, "party-leave-social");
	}

	private void offlineOnlineContinuity(PhantomTestContext context) throws Exception
	{
		_socialBeforeOffline = _socialStore.load(_managed.profile().profileId()).orElseThrow();
		final byte[] conversationBefore = _profiles.findComponent(_managed.profile().profileId(), PhantomConversationModel.COMPONENT_TYPE).orElseThrow().payload();
		final long started = System.nanoTime();
		final SignalStatus withdrawn = _scheduler.withdrawSignal(_managed.profile().profileId(), ACTIVE_SOURCE, 2).status();
		PhantomAssertions.assertTrue((withdrawn == SignalStatus.ACCEPTED) || (withdrawn == SignalStatus.COALESCED), "CP2 ACTIVE withdrawal was not accepted.");
		await(15_000, () -> _materialization.find(_managed.profile().profileId()).isEmpty() && (World.getInstance().getPlayer(_population.actualCharacterObjectId()) == null), "CP2 ACTIVE withdrawal did not dematerialize within 15 seconds.");
		PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_population.actualCharacterObjectId()), "Dematerialization retained PHANTOM lease.");
		PhantomAssertions.assertTrue(_profiles.findComponent(_managed.profile().profileId(), PhantomSocialModel.COMPONENT_TYPE).isPresent(), "Dematerialization removed durable social memory.");
		PhantomAssertions.assertTrue(_profiles.findComponent(_managed.profile().profileId(), PhantomConversationModel.COMPONENT_TYPE).isPresent(), "Dematerialization removed durable conversation memory.");
		final long offlineMillis = elapsedMillis(started);

		final long reactivationStarted = System.nanoTime();
		final SignalStatus reactivated = _scheduler.submitSignal(_managed.profile().profileId(), new PhantomRelevanceSignal(ACTIVE_SOURCE, 3, PhantomActivityState.ACTIVE, ACTIVE_TTL_MILLIS)).status();
		PhantomAssertions.assertTrue((reactivated == SignalStatus.ACCEPTED) || (reactivated == SignalStatus.COALESCED), "CP2 ACTIVE reactivation was not accepted.");
		await(15_000, () -> _materialization.find(_managed.profile().profileId()).filter(snapshot -> snapshot.characterObjectId() == _population.actualCharacterObjectId() && snapshot.worldPresent() && snapshot.outboundAttached()).isPresent(), "CP2 reactivation did not rematerialize the same identity within 15 seconds.");
		_phantom = World.getInstance().getPlayer(_population.actualCharacterObjectId());
		PhantomAssertions.assertTrue(_phantom != null, "Same CP2 character is absent after reactivation.");
		final StoredState socialAfter = _socialStore.load(_managed.profile().profileId()).orElseThrow();
		PhantomAssertions.assertEquals(_socialBeforeOffline.state().personalitySeed(), socialAfter.state().personalitySeed(), "Reactivation changed durable personality.");
		PhantomAssertions.assertEquals(_socialBeforeOffline.state().traits(), socialAfter.state().traits(), "Reactivation changed the trait vector.");
		PhantomAssertions.assertEquals(_socialBeforeOffline.state().relationship(SubjectRef.character(_human.getObjectId())), socialAfter.state().relationship(SubjectRef.character(_human.getObjectId())), "Reactivation changed human relationship memory.");
		PhantomAssertions.assertEquals(_socialBeforeOffline.state().memories(), socialAfter.state().memories(), "Reactivation changed durable social memories.");
		PhantomAssertions.assertTrue(java.util.Arrays.equals(conversationBefore, _profiles.findComponent(_managed.profile().profileId(), PhantomConversationModel.COMPONENT_TYPE).orElseThrow().payload()), "Reactivation changed durable conversation bytes.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM phantom_profiles WHERE profile_id=?", _managed.profile().profileId()), "Reactivation duplicated the Phantom profile.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", _population.actualCharacterObjectId()), "Reactivation duplicated the character.");
		PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM characters WHERE account_name=?", _population.reservedAccount()), "Reactivation duplicated the reserved account character.");
		context.record("goal030cp2.offlineMillis", offlineMillis);
		context.record("goal030cp2.reactivationMillis", elapsedMillis(reactivationStarted));
		context.record("goal030cp2.sameIdentityMemory", _managed.profile().profileId() + "/" + _population.actualCharacterObjectId() + "/personality=" + socialAfter.state().personalitySeed());
	}

	private void shutdownAndCleanup(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertFalse(_phantom.isInParty() || _human.isInParty(), "CP2 shutdown began with canonical Party residue.");
		PhantomAssertions.assertTrue(PartyInvitationService.getInstance().observe(_human).isEmpty(), "CP2 shutdown began with invitation residue.");
		await(10_000, () -> _executionStore.load(_managed.profile().profileId()).map(value -> value.state().entries().isEmpty()).orElse(true), "Conversation execution retained in-flight entries before shutdown.");
		PhantomAssertions.assertTrue(_profiles.findComponent(_managed.profile().profileId(), PhantomConversationModel.COMPONENT_TYPE).isPresent(), "Durable conversation memory disappeared before shutdown.");

		final PhantomScheduler scheduler = _scheduler;
		final PhantomMaterializationService materialization = _materialization;
		PhantomAssertions.assertTrue(PhantomSystem.shutdownIfStarted(), "Canonical PhantomSystem shutdown did not run.");
		_shutdownComplete = true;
		PhantomAssertions.assertFalse(PhantomSystem.hasConfiguredInstance(), "Canonical shutdown retained configured owner.");
		PhantomAssertions.assertEquals(PhantomScheduler.SchedulerState.STOPPED, scheduler.snapshot().state(), "Canonical shutdown retained Scheduler ownership/signals.");
		PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.snapshot().state(), "Canonical shutdown retained MaterializationService.");
		PhantomAssertions.assertEquals(0, materialization.snapshot().retainedEntries(), "Canonical shutdown retained materialization entries.");
		PhantomAssertions.assertEquals(null, World.getInstance().getPlayer(_population.actualCharacterObjectId()), "Canonical shutdown retained CP2 Player in World.");
		PhantomAssertions.assertEquals(null, PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(_population.actualCharacterObjectId()), "Canonical shutdown retained CP2 PHANTOM lease.");
		PhantomAssertions.assertFalse(ChatObservationService.getInstance().snapshot().observerRegistered(), "Canonical shutdown retained chat observer.");
		PhantomAssertions.assertTrue(PartyInvitationService.getInstance().observe(_human).isEmpty(), "Canonical shutdown retained Party invitation.");

		cleanupCp2Profile();
		restoreWhisper();
		if (_humanOutputAttachment != null)
		{
			_humanOutputAttachment.close();
			_humanOutputAttachment = null;
		}
		_environment.cleanupLoadedPlayer(_human);
		_environment.assertClean(_environment.primary(), _human);
		_human = null;
		_environment.shutdown();
		_environmentInitialized = false;
		context.record("goal030cp2.shutdownCleanup", "owner=false,scheduler=STOPPED,materialization=0,lease=false,observer=false,party=false,residue=0");
		context.record("goal030cp2.totalMillis", elapsedMillis(_populationStartedNanos));
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if (!_shutdownComplete && PhantomSystem.hasConfiguredInstance())
			{
				PhantomSystem.shutdownIfStarted();
			}
			if (DatabaseFactory.isInitialized())
			{
				cleanupCp2Profile();
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		try
		{
			restoreWhisper();
			if (_humanOutputAttachment != null)
			{
				_humanOutputAttachment.close();
				_humanOutputAttachment = null;
			}
			if (_environmentInitialized)
			{
				_environment.cleanupLoadedPlayer(_human);
				_environment.shutdown();
				_environmentInitialized = false;
			}
		}
		catch (Throwable throwable)
		{
			if (failure == null)
			{
				failure = throwable;
			}
			else
			{
				failure.addSuppressed(throwable);
			}
		}
		if (failure instanceof Exception exception)
		{
			throw exception;
		}
		if (failure != null)
		{
			throw new RuntimeException(failure);
		}
	}
	private boolean refreshReadyPopulation()
	{
		final List<ManagedProfile> rows = _profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 2);
		if (rows.size() > 1)
		{
			throw new AssertionError("Population created more than one CP2 Phantom.");
		}
		if (rows.size() != 1)
		{
			return false;
		}
		final ManagedProfile row = rows.getFirst();
		final PhantomPopulationState state = _populationCodec.decode(row.component().payload());
		if ((state.state() != PhantomPopulationState.State.READY) || (state.actualCharacterObjectId() == null) || !state.actualCharacterObjectId().equals(row.profile().characterObjectId()))
		{
			return false;
		}
		_managed = row;
		_population = state;
		return true;
	}

	private CandidateEvidence autonomousEvidence()
	{
		for (DecisionView view : decisionViews())
		{
			if ("candidate.population.bootstrap".equals(view.candidateKey()) || nonPopulation(view.candidateKey()))
			{
				return new CandidateEvidence(view.candidateKey(), "selected:" + view.reasonKey());
			}
		}
		return null;
	}

	private CandidateEvidence selectedEvidence(String candidateKey)
	{
		for (DecisionView view : decisionViews())
		{
			if (candidateKey.equals(view.candidateKey()))
			{
				return new CandidateEvidence(view.candidateKey(), "selected:" + view.reasonKey());
			}
		}
		return null;
	}

	private static List<DecisionView> decisionViews()
	{
		final PhantomSelectedDecisionTrace trace = PhantomSystem.configuredSelectedTraceForTesting();
		if (trace == null)
		{
			return List.of();
		}
		final var snapshot = trace.snapshot();
		final List<DecisionView> views = new ArrayList<>(snapshot.history());
		if (snapshot.current() != null)
		{
			views.add(snapshot.current());
		}
		return views;
	}

	private static boolean nonPopulation(String candidateKey)
	{
		return (candidateKey != null) && !candidateKey.isBlank() && !candidateKey.contains("population");
	}

	private void awaitConversationReadiness(PhantomTestContext context) throws Exception
	{
		final long deadline = System.nanoTime() + 10_000_000_000L;
		ConversationReadiness evidence = conversationReadiness();
		while (!evidence.ready() && (System.nanoTime() < deadline))
		{
			Thread.sleep(20);
			evidence = conversationReadiness();
		}
		if (!evidence.ready())
		{
			throw new AssertionError("population.bootstrap cleanup did not complete: goal.runtime=" + evidence.goalRuntime() + ", conversation.execution=" + evidence.conversationExecution());
		}
		context.record("goal030cp2.bootstrapReadiness", "goal.runtime=" + evidence.goalRuntime() + ",conversation.execution=" + evidence.conversationExecution());
	}

	private ConversationReadiness conversationReadiness()
	{
		final var goalComponent = _profiles.findComponent(_managed.profile().profileId(), PhantomGoalStateStore.COMPONENT_TYPE);
		final String goalRuntime;
		if (goalComponent.isEmpty())
		{
			goalRuntime = "absent";
		}
		else
		{
			final PhantomGoal goal = _goalCodec.decode(goalComponent.get().payload());
			goalRuntime = goal.goalType() + "/" + goal.status() + "/revision=" + goal.revision();
		}
		final StoredExecution execution = _executionStore.load(_managed.profile().profileId()).orElse(null);
		final int entries = execution == null ? 0 : execution.state().entries().size();
		final String conversationExecution = execution == null ? "absent" : "entries=" + entries + ",receipts=" + execution.state().receipts().size() + ",rowVersion=" + execution.rowVersion();
		return new ConversationReadiness(goalComponent.isEmpty() && (entries == 0), goalRuntime, conversationExecution);
	}

	private void awaitConversationQuiescence(PhantomTestContext context, String boundary) throws Exception
	{
		final long deadline = System.nanoTime() + 10_000_000_000L;
		StoredExecution execution = _executionStore.load(_managed.profile().profileId()).orElse(null);
		while ((execution != null) && !execution.state().entries().isEmpty() && (System.nanoTime() < deadline))
		{
			Thread.sleep(20);
			execution = _executionStore.load(_managed.profile().profileId()).orElse(null);
		}
		if ((execution != null) && !execution.state().entries().isEmpty())
		{
			throw new AssertionError("Conversation execution did not quiesce after " + boundary + ": " + executionEvidence(execution));
		}
		context.record("goal030cp2.quiescence." + boundary, execution == null ? "component=absent" : "entries=0,receipts=" + execution.state().receipts().size() + ",rowVersion=" + execution.rowVersion());
	}

	private void awaitAdenaResponse(long generatedBefore, int callsBefore) throws Exception
	{
		final long deadline = System.nanoTime() + 10_000_000_000L;
		while ((System.nanoTime() < deadline) && !adenaResponseReady(generatedBefore, callsBefore))
		{
			Thread.sleep(20);
		}
		if (adenaResponseReady(generatedBefore, callsBefore))
		{
			return;
		}
		final ConversationSession session = conversationSession();
		final StoredExecution execution = _executionStore.load(_managed.profile().profileId()).orElse(null);
		throw new AssertionError("item.acquire.query did not produce exactly one actual generated response: persistedSemantic=" + (session == null ? "absent" : session.previousIntent() + "/slots=" + session.previousSlots()) + ", queryResultStatus=" + executionEvidence(execution) + ", generatedDeliveryDelta=" + (ChatObservationService.getInstance().snapshot().generatedDeliveries() - generatedBefore) + ", whisperCallsDelta=" + (_whisper.calls() - callsBefore) + ", capturedWhisper=" + _whisper.lastGenerated());
	}

	private boolean adenaResponseReady(long generatedBefore, int callsBefore)
	{
		return (ChatObservationService.getInstance().snapshot().generatedDeliveries() == (generatedBefore + 1)) && (_whisper.calls() >= (callsBefore + 2)) && (_whisper.lastGenerated() != null);
	}

	private static String executionEvidence(StoredExecution execution)
	{
		if (execution == null)
		{
			return "component=absent";
		}
		final String entries = execution.state().entries().stream().map(entry -> entry.planId() + "{proposal=" + entry.proposalKey() + ",target=" + entry.target() + ",arguments=" + entry.arguments() + ",action=" + entry.actionState() + ",outbound=" + entry.outboundState() + ",reason=" + entry.reasonKey() + ",text=" + entry.text() + "}").toList().toString();
		final String receipts = execution.state().receipts().stream().map(receipt -> receipt.planId() + "{action=" + receipt.actionState() + ",outbound=" + receipt.outboundState() + ",reason=" + receipt.reasonKey() + "}").toList().toString();
		return "rowVersion=" + execution.rowVersion() + ",entries=" + entries + ",receipts=" + receipts;
	}

	private void dispatchWhisper(String text)
	{
		final ChatObservationService observation = ChatObservationService.getInstance();
		try (var scope = observation.openClientDispatch(_human.getObjectId(), _human.getName(), ChatType.WHISPER, _phantom.getName(), text, System.currentTimeMillis()))
		{
			PhantomAssertions.assertTrue(scope.descriptor() != null, "CLIENT_CHAT WHISPER scope is inert.");
			ChatHandler.getInstance().getHandler(ChatType.WHISPER).onChat(ChatType.WHISPER, _human, _phantom.getName(), text);
			PhantomAssertions.assertTrue(scope.expectedCounterpartDelivered() || (scope.deliveries() > 0), "Real WHISPER produced no actual delivery.");
		}
	}

	private void assertSemantic(String expectedIntent, String expectedItem) throws Exception
	{
		await(10_000, () ->
		{
			final ConversationSession session = conversationSession();
			return (session != null) && expectedIntent.equals(session.previousIntent());
		}, "Conversation did not durably persist semantic intent " + expectedIntent + ".");
		final ConversationSession session = conversationSession();
		PhantomAssertions.assertEquals(expectedIntent, session.previousIntent(), "Conversation persisted the wrong semantic intent.");
		if (expectedItem != null)
		{
			PhantomAssertions.assertTrue(session.previousSlots().stream().anyMatch(slot -> (slot.type() == SlotType.ITEM) && (slot.domainReference() != null) && slot.domainReference().namespace().equals("item") && slot.domainReference().key().equals(expectedItem)), "item.acquire.query did not ground ITEM57.");
		}
	}

	private ConversationSession conversationSession()
	{
		final var component = _profiles.findComponent(_managed.profile().profileId(), PhantomConversationModel.COMPONENT_TYPE).orElse(null);
		if (component == null)
		{
			return null;
		}
		final String counterpart = Integer.toString(_human.getObjectId());
		return _conversationCodec.decode(component.payload()).sessions().stream().filter(session -> session.counterpart().namespace().equals("character.object") && session.counterpart().key().equals(counterpart)).findFirst().orElse(null);
	}

	private void cleanupCp2Profile() throws Exception
	{
		if ((_profiles == null) || (_managed == null))
		{
			return;
		}
		final long profileId = _managed.profile().profileId();
		final int objectId = _population.actualCharacterObjectId();
		final String account = _population.reservedAccount();
		final Player world = World.getInstance().getPlayer(objectId);
		if (world != null)
		{
			_environment.cleanupLoadedPlayer(world);
		}
		PhantomProfile current = _profiles.find(profileId).orElse(null);
		if (current != null)
		{
			if (current.characterObjectId() != null)
			{
				current = _profiles.updateCharacterLink(current.profileId(), current.rowVersion(), null);
			}
			_profiles.delete(current.profileId(), current.rowVersion());
		}
		GameClient.deleteCharByObjId(objectId);
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE login=?"))
		{
			statement.setString(1, account);
			statement.executeUpdate();
		}
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM phantom_profiles WHERE profile_id=?", profileId), "Exact CP2 profile cleanup left residue.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM characters WHERE charId=?", objectId), "Exact CP2 character cleanup left residue.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM accounts WHERE login=?", account), "Exact CP2 account cleanup left residue.");
		_managed = null;
	}

	private void restoreWhisper()
	{
		if (_whisper != null)
		{
			ChatHandler.getInstance().removeHandler(_whisper);
			_whisper = null;
		}
		if (_nativeWhisper != null)
		{
			ChatHandler.getInstance().registerHandler(_nativeWhisper);
			_nativeWhisper = null;
		}
	}

	private static long scalar(String sql, Object... arguments) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int index = 0; index < arguments.length; index++)
			{
				statement.setObject(index + 1, arguments[index]);
			}
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "CP2 scalar query returned no row.");
				return result.getLong(1);
			}
		}
	}

	private static void await(long timeoutMillis, BooleanSupplier condition, String failure) throws Exception
	{
		final long deadline = System.nanoTime() + (timeoutMillis * 1_000_000L);
		while (System.nanoTime() < deadline)
		{
			if (condition.getAsBoolean())
			{
				return;
			}
			Thread.sleep(20);
		}
		PhantomAssertions.assertTrue(condition.getAsBoolean(), failure);
	}

	private static long elapsedMillis(long startedNanos)
	{
		return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
	}

	private final class CapturingWhisperHandler implements IChatHandler
	{
		private final IChatHandler _delegate;
		private int _calls;
		private CapturedMessage _lastGenerated;

		private CapturingWhisperHandler(IChatHandler delegate)
		{
			_delegate = Objects.requireNonNull(delegate);
		}

		@Override
		public synchronized void onChat(ChatType type, Player active, String target, String text)
		{
			_calls++;
			if ((active != null) && (active == _phantom) && (_executionStore != null) && (_managed != null))
			{
				final ExecutionEntry entry = _executionStore.load(_managed.profile().profileId()).map(stored -> stored.state().entries().stream().filter(value -> value.text().equals(text)).findFirst().orElse(null)).orElse(null);
				if (entry != null)
				{
					_lastGenerated = new CapturedMessage(text, entry.style(), entry.proposalKey());
				}
			}
			_delegate.onChat(type, active, target, text);
		}

		@Override
		public ChatType[] getChatTypeList()
		{
			return new ChatType[]
			{
				ChatType.WHISPER
			};
		}

		private synchronized int calls()
		{
			return _calls;
		}

		private synchronized CapturedMessage lastGenerated()
		{
			return _lastGenerated;
		}
	}

	private record ConversationReadiness(boolean ready, String goalRuntime, String conversationExecution)
	{
	}

	private record CandidateEvidence(String candidateKey, String status)
	{
	}

	private record CapturedMessage(String text, String style, String proposalKey)
	{
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.handler.IChatHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.Origin;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.events.holders.actor.player.clan.OnPlayerClanLeft.DepartureKind;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerPolicy;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveCatalog;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Effect;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveModel.Kind;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanDirectiveService;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationCatalog;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationModel.DeliveredObservation;
import org.l2jmobius.gameserver.phantoms.conversation.PhantomConversationPlanSink;
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
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotValue;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticPack;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticUnderstandingService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.AffiliationKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEventContext;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;
import org.l2jmobius.gameserver.phantoms.topology.PhantomSchedulerRelevanceSignalPort;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.gameserver.scripting.engine.ScriptExecutor;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomClanDirectiveIntegrationGoal030C2ASuite implements PhantomTestSuite
{
	private static final long SEED = 30003033L;
	private static final Hashes HASHES = new Hashes("A".repeat(64), "B".repeat(64), "C".repeat(64));
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final AtomicLong _schedulerClock = new AtomicLong();
	private final CountingMaterializationPort _schedulerMaterialization = new CountingMaterializationPort();
	private PhantomProfileRepository _profiles;
	private PhantomProfile _primaryProfile;
	private PhantomProfile _observerProfile;
	private PhantomProfile _leaderProfile;
	private PhantomProfile _recipientProfile;
	private Player _primary;
	private Player _observer;
	private Player _leader;
	private Player _recipient;
	private PhantomMaterializationService _materialization;
	private PhantomSocialService _social;
	private PhantomSemanticUnderstandingService _semantic;
	private PhantomScheduler _scheduler;
	private PhantomClanDirectiveService _directives;
	private PhantomConversationService _conversation;
	private Clan _clan;
	private IChatHandler _clanHandler;
	private long _fixtureSequence;
	private long _assembleDispatch;
	private long _refuseDispatch;
	private boolean _conversationStopped;

	@Override
	public String id()
	{
		return "clan-directive-integration-goal030c2a";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal030C2A integration suite used the wrong seed.");
		_environment.initialize(context);
		_profiles = PhantomProfileRepository.open();
		_primaryProfile = _profiles.create(_environment.primary().objectId());
		_observerProfile = _profiles.create(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		PhantomAssertions.assertTrue(_materialization.start(), "Directive fixture materialization did not start.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_primaryProfile.profileId()).status(), "Primary directive fixture did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_observerProfile.profileId()).status(), "Observer directive fixture did not materialize.");
		_primary = World.getInstance().getPlayer(_environment.primary().objectId());
		_observer = World.getInstance().getPlayer(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_primary != null) && (_observer != null), "Directive fixture Players are absent from World.");
		_primary.getStat().setLevel((byte) 20);
		_observer.getStat().setLevel((byte) 20);

		final PhantomSocialCatalog socialCatalog = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		_social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(_profiles, socialCatalog), SEED, 64, PhantomClanDirectiveIntegrationGoal030C2ASuite::nowMinute);
		PhantomAssertions.assertTrue(_social.start(), "Directive social service did not start.");
		final int primaryLoyalty = _social.ensurePersonality(_primaryProfile.profileId()).value().traits().get("loyalty");
		final int observerLoyalty = _social.ensurePersonality(_observerProfile.profileId()).value().traits().get("loyalty");
		if (primaryLoyalty <= observerLoyalty)
		{
			assignRoles(_primaryProfile, _primary, _observerProfile, _observer);
		}
		else
		{
			assignRoles(_observerProfile, _observer, _primaryProfile, _primary);
		}

		resetPenalties(_leader);
		resetPenalties(_recipient);
		_clan = ClanTable.getInstance().createClan(_leader, "C30C2A" + Math.floorMod((int) context.seed(), 1000));
		PhantomAssertions.assertTrue(_clan != null, "Canonical directive clan creation failed.");
		_clan.addClanMember(_recipient);

		_scheduler = new PhantomScheduler(
			2,
			10,
			2,
			new PhantomSchedulerPolicy(16, 600_000, 0, 1, 10, 1, 2, 3, 4, 50),
			_schedulerClock::get,
			(pulse, period) -> null,
			false,
			metrics,
			new PhantomDiagnosticTrace(false, 0, 0, metrics),
			_schedulerMaterialization,
			item ->
			{
			});
		PhantomAssertions.assertTrue(_scheduler.start(), "Directive scheduler did not start.");
		PhantomAssertions.assertEquals(PhantomScheduler.RegistrationStatus.REGISTERED, _scheduler.register(_leaderProfile.profileId()).status(), "Leader profile scheduler registration failed.");
		PhantomAssertions.assertEquals(PhantomScheduler.RegistrationStatus.REGISTERED, _scheduler.register(_recipientProfile.profileId()).status(), "Recipient profile scheduler registration failed.");

		final PhantomClanDirectiveCatalog directiveCatalog = PhantomClanDirectiveCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/clan/high-five-clan-directives-v1.xml"));
		_directives = new PhantomClanDirectiveService(directiveCatalog, _materialization, _social, new PhantomSchedulerRelevanceSignalPort(_scheduler), PhantomClanDirectiveIntegrationGoal030C2ASuite::nowMinute);
		PhantomAssertions.assertTrue(_directives.start(), "Directive service did not start.");

		final PhantomSemanticPack semanticPack = PhantomSemanticPack.load(
			context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-semantic-v1.xml"),
			context.moduleRoot().resolve("dist/game/data/phantoms/semantic/high-five-ru-corpus-v1.tsv"),
			PhantomSemanticGrounding.fixed(HASHES, references()));
		_semantic = PhantomSemanticUnderstandingService.loaded(semanticPack);
		PhantomAssertions.assertTrue(_semantic.start(), "Directive fixture semantic service did not start.");
		final PhantomConversationCatalog conversationCatalog = PhantomConversationCatalog.load(
			context.moduleRoot().resolve("dist/game/data/phantoms/conversation/high-five-ru-conversation-v1.xml"),
			context.moduleRoot().resolve("dist/game/data/phantoms/conversation/high-five-ru-conversation-corpus-v1.tsv"));
		final PhantomConversationService.ContextPort contextPort = new PhantomConversationService.ContextPort()
		{
			@Override
			public OptionalLong profileIdForObject(int characterObjectId)
			{
				return OptionalLong.empty();
			}

			@Override
			public Optional<PhantomConversationService.ContextSnapshot> snapshot(long observerProfileId, DeliveredObservation observation, String previousIntent, List<SlotValue> previousSlots)
			{
				return Optional.empty();
			}
		};
		PhantomAssertions.assertFalse(ChatObservationService.getInstance().snapshot().observerRegistered(), "A chat observer existed before ConversationService start.");
		_conversation = new PhantomConversationService(conversationCatalog, new PhantomConversationStore(_profiles), contextPort, _semantic, _social, PhantomConversationPlanSink.observerOnly(), PhantomIdentityLeaseRegistry.getInstance(), ChatObservationService.getInstance(), _directives);
		PhantomAssertions.assertTrue(_conversation.start(), "ConversationService did not acquire the sole chat observer.");

		ScriptEngine.getInstance().executeScript(Path.of("handlers/chat/channels/ChatClan.java"));
		_clanHandler = nativeClanHandler();
		PhantomAssertions.assertTrue((_clanHandler != null) && _clanHandler.getClass().getName().endsWith(".ChatClan"), "Native ChatClan handler is absent.");
		context.record("goal030c2a.database", "l2jmobiush5_phantom_test");
		context.record("goal030c2a.loyalty.lowHigh", Math.min(primaryLoyalty, observerLoyalty) + "/" + Math.max(primaryLoyalty, observerLoyalty));
		context.record("goal030c2a.initialLeaderRecipient", _leader.getObjectId() + "->" + _recipient.getObjectId());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		stopConversationAndDirectives();
		if (_scheduler != null)
		{
			_scheduler.beginStop();
			PhantomAssertions.assertTrue(_scheduler.finishStop(), "Directive scheduler did not stop cleanly.");
		}
		if ((_semantic != null) && (_semantic.snapshot().state() == PhantomSemanticUnderstandingService.State.RUNNING))
		{
			_semantic.beginStop();
			PhantomAssertions.assertTrue(_semantic.finishStop(), "Directive semantic service did not stop.");
		}
		if ((_social != null) && (_social.snapshot().state() == PhantomSocialService.ServiceState.RUNNING))
		{
			_social.beginStop();
			PhantomAssertions.assertTrue(_social.finishStop(), "Directive social service did not stop.");
		}
		cleanupClan();
		if (_materialization != null)
		{
			_materialization.shutdown();
		}
		deleteProfile(_primaryProfile);
		deleteProfile(_observerProfile);
		if ((_primary != null) && (_observer != null))
		{
			_environment.assertClean(_environment.primary(), _primary);
			_environment.assertClean(_environment.observer(), _observer);
		}
		_environment.shutdown();
		context.record("goal030c2a.cleanup.clan", _clan == null);
		context.record("goal030c2a.cleanup.observer", ChatObservationService.getInstance().snapshot().observerRegistered());
		context.record("goal030c2a.cleanup.directiveOwned", _directives == null ? 0 : _directives.snapshot().ownedSignals());
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-real-clan-assemble-active-and-social-accept", this::assembleAccepted);
		registry.add("02-ordinary-outsider-generated-ignored", this::unauthorizedAndGenerated);
		registry.add("03-native-transfer-and-low-social-refusal", this::transferAndRefusal);
		registry.add("04-standby-warm-dismiss-own-signal-only", this::standbyAndDismiss);
		registry.add("05-single-observer-shutdown-zero-directive-ownership", this::shutdownLifecycle);
	}

	private void assembleAccepted(PhantomTestContext context)
	{
		final SubjectRef leaderSubject = SubjectRef.phantom(_leaderProfile.profileId());
		tunePositive(_recipientProfile.profileId(), leaderSubject);
		final var beforeSocial = social(_recipientProfile.profileId(), leaderSubject);
		final var chatBefore = ChatObservationService.getInstance().snapshot();
		final ChatDispatch dispatch = dispatchClient(_leader, "сбор");
		_assembleDispatch = dispatch.dispatchId();
		final var owned = _directives.ownedSignal(_recipientProfile.profileId(), _clan.getId()).orElseThrow(() -> new AssertionError("ASSEMBLE missing: directive=" + _directives.snapshot() + ", chat=" + ChatObservationService.getInstance().snapshot() + ", materialized=" + _materialization.findByCharacterObjectId(_recipient.getObjectId()) + ", leader=" + _clan.getLeaderId() + ", speaker=" + _leader.getObjectId()));
		PhantomAssertions.assertEquals(Effect.ACTIVE, owned.effect(), "ASSEMBLE did not own an ACTIVE directive signal.");
		PhantomAssertions.assertEquals(120_000L, owned.ttlMillis(), "ASSEMBLE TTL changed.");
		PhantomAssertions.assertEquals(1, _scheduler.find(_recipientProfile.profileId()).orElseThrow().activeSignalSources(), "Real Scheduler did not retain one directive signal.");
		PhantomAssertions.assertEquals(0, _schedulerMaterialization.materializeCalls.get(), "Directive service directly materialized a Phantom.");
		PhantomAssertions.assertTrue(dispatch.deliveries() >= 1, "Native ChatClan delivered to no actual recipient.");
		PhantomAssertions.assertTrue(ChatObservationService.getInstance().snapshot().clientDeliveries() > chatBefore.clientDeliveries(), "Real CLAN dispatch produced no CLIENT_CHAT delivery.");

		final var afterSocial = social(_recipientProfile.profileId(), leaderSubject);
		PhantomAssertions.assertEquals(Math.min(3000, beforeSocial.relationship().get("respect") + 48), afterSocial.relationship().get("respect"), "Accepted SAME_CLAN respect delta is not +48 after scaling/clamp.");
		PhantomAssertions.assertEquals(Math.min(3000, beforeSocial.reputation().get("reliability") + 24), afterSocial.reputation().get("reliability"), "Accepted SAME_CLAN reliability delta is not +24 after scaling/clamp.");
		final String eventId = PhantomSocialModel.sha256("clan.directive.event|" + _assembleDispatch + "|" + _recipientProfile.profileId() + "|" + Kind.ASSEMBLE);
		PhantomAssertions.assertTrue(_social.snapshot(_recipientProfile.profileId(), leaderSubject, 24, nowMinute()).value().memories().stream().anyMatch(memory -> memory.eventId().equals(eventId) && memory.eventKey().equals("clan.directive.accepted")), "Accepted event lacks exact dispatch/profile/kind identity.");
		PhantomAssertions.assertTrue(ChatObservationService.getInstance().snapshot().observerRegistered(), "Conversation lost sole observer ownership.");
		context.record("goal030c2a.assembleDispatch", _assembleDispatch);
		context.record("goal030c2a.assembleSpeakerRecipient", _leader.getObjectId() + "->" + _recipient.getObjectId());
		context.record("goal030c2a.assembleEffect", "ACTIVE/120000");
	}

	private void unauthorizedAndGenerated(PhantomTestContext context)
	{
		final long unauthorizedBefore = _directives.snapshot().unauthorized();
		dispatchClient(_recipient, "сбор");
		PhantomAssertions.assertTrue(_directives.snapshot().unauthorized() > unauthorizedBefore, "Ordinary clan member directive was not rejected.");
		PhantomAssertions.assertTrue(_directives.ownedSignal(_recipientProfile.profileId(), _clan.getId()).isPresent(), "Unauthorized ordinary member replaced the leader-owned signal.");

		_clan.removeClanMember(_recipient.getObjectId(), 0, DepartureKind.UNKNOWN, 0);
		final long observationsBeforeOutsider = _directives.snapshot().observations();
		final ChatDispatch outsider = dispatchClient(_recipient, "сбор");
		PhantomAssertions.assertEquals(0, outsider.deliveries(), "Native ChatClan delivered an outsider message.");
		PhantomAssertions.assertEquals(observationsBeforeOutsider, _directives.snapshot().observations(), "Outsider reached directive ingress.");
		_recipient.setClanJoinExpiryTime(0);
		_clan.addClanMember(_recipient);

		final long observationsBeforeGenerated = _directives.snapshot().observations();
		final long generatedBefore = ChatObservationService.getInstance().snapshot().generatedDeliveries();
		final ChatDispatch generated = dispatchGenerated(_leader, "сбор");
		PhantomAssertions.assertTrue(generated.deliveries() >= 1, "Generated CLAN fixture produced no actual delivery.");
		PhantomAssertions.assertEquals(observationsBeforeGenerated, _directives.snapshot().observations(), "PHANTOM_GENERATED delivery reached directive side-channel.");
		PhantomAssertions.assertTrue(ChatObservationService.getInstance().snapshot().generatedDeliveries() > generatedBefore, "Generated delivery was not observed by the chat seam.");
		context.record("goal030c2a.unauthorized", _directives.snapshot().unauthorized() - unauthorizedBefore);
		context.record("goal030c2a.generatedDispatch", generated.dispatchId());
	}

	private void transferAndRefusal(PhantomTestContext context)
	{
		dispatchClient(_leader, "отбой");
		PhantomAssertions.assertTrue(_directives.ownedSignal(_recipientProfile.profileId(), _clan.getId()).isEmpty(), "DISMISS did not withdraw the prior ASSEMBLE signal.");

		final int oldLeaderObjectId = _leader.getObjectId();
		final PhantomProfile oldLeaderProfile = _leaderProfile;
		_clan.setNewLeader(_clan.getClanMember(_recipient.getObjectId()));
		swapRoles();
		PhantomAssertions.assertEquals(_leader.getObjectId(), _clan.getLeaderId(), "Native leader transfer did not update canonical authority.");

		final long unauthorizedBefore = _directives.snapshot().unauthorized();
		dispatchClient(_recipient, "сбор");
		PhantomAssertions.assertTrue(_directives.snapshot().unauthorized() > unauthorizedBefore, "Former leader retained directive authority.");

		final SubjectRef leaderSubject = SubjectRef.phantom(_leaderProfile.profileId());
		tuneNegative(_recipientProfile.profileId(), leaderSubject);
		final int modifier = modifier(_recipientProfile.profileId(), leaderSubject);
		PhantomAssertions.assertTrue(modifier <= -900, "Low-social fixture cannot reach ASSEMBLE refusal: " + modifier);
		final var beforeSocial = social(_recipientProfile.profileId(), leaderSubject);
		final ChatDispatch dispatch = dispatchClient(_leader, "сбор");
		_refuseDispatch = dispatch.dispatchId();
		PhantomAssertions.assertTrue(_directives.ownedSignal(_recipientProfile.profileId(), _clan.getId()).isEmpty(), "REFUSE submitted a Scheduler signal.");
		PhantomAssertions.assertEquals(0, _scheduler.find(_recipientProfile.profileId()).orElseThrow().activeSignalSources(), "Low-social REFUSE retained a Scheduler signal.");
		final var afterSocial = social(_recipientProfile.profileId(), leaderSubject);
		PhantomAssertions.assertEquals(Math.max(-3000, beforeSocial.relationship().get("respect") - 14), afterSocial.relationship().get("respect"), "Refused SAME_CLAN respect delta is not -14 after scaling/clamp.");
		PhantomAssertions.assertEquals(Math.min(3000, beforeSocial.relationship().get("anger") + 14), afterSocial.relationship().get("anger"), "Refused SAME_CLAN anger delta is not +14 after scaling/clamp.");
		final String eventId = PhantomSocialModel.sha256("clan.directive.event|" + _refuseDispatch + "|" + _recipientProfile.profileId() + "|" + Kind.ASSEMBLE);
		PhantomAssertions.assertTrue(_social.snapshot(_recipientProfile.profileId(), leaderSubject, 24, nowMinute()).value().memories().stream().anyMatch(memory -> memory.eventId().equals(eventId) && memory.eventKey().equals("clan.directive.refused")), "Refused event lacks exact leader/dispatch identity.");
		context.record("goal030c2a.transferAuthority", oldLeaderObjectId + "->" + _leader.getObjectId());
		context.record("goal030c2a.refuseProfileOldLeader", oldLeaderProfile.profileId());
		context.record("goal030c2a.refuseModifierScore", modifier + "/" + (600 + modifier));
	}

	private void standbyAndDismiss(PhantomTestContext context)
	{
		final int formerLeader = _leader.getObjectId();
		_clan.setNewLeader(_clan.getClanMember(_recipient.getObjectId()));
		swapRoles();
		PhantomAssertions.assertEquals(_leader.getObjectId(), _clan.getLeaderId(), "Second native leader transfer did not update authority.");

		final PhantomScheduler.SignalStatus unrelatedStatus = _scheduler.submitSignal(_recipientProfile.profileId(), new PhantomRelevanceSignal("test.unrelated", 1, PhantomActivityState.WARM, 600_000)).status();
		PhantomAssertions.assertTrue((unrelatedStatus == PhantomScheduler.SignalStatus.ACCEPTED) || (unrelatedStatus == PhantomScheduler.SignalStatus.COALESCED), "Unrelated Scheduler signal was not installed: " + unrelatedStatus);
		final long unauthorizedBefore = _directives.snapshot().unauthorized();
		dispatchClient(_recipient, "готовность");
		PhantomAssertions.assertTrue(_directives.snapshot().unauthorized() > unauthorizedBefore, "Former leader retained STANDBY authority after transfer.");

		final ChatDispatch standby = dispatchClient(_leader, "готовность");
		final var owned = _directives.ownedSignal(_recipientProfile.profileId(), _clan.getId()).orElseThrow();
		PhantomAssertions.assertEquals(Effect.WARM, owned.effect(), "STANDBY did not own a WARM directive signal.");
		PhantomAssertions.assertEquals(300_000L, owned.ttlMillis(), "STANDBY TTL changed.");
		PhantomAssertions.assertEquals(2, _scheduler.find(_recipientProfile.profileId()).orElseThrow().activeSignalSources(), "STANDBY and unrelated Scheduler sources were not both retained.");

		final ChatDispatch dismiss = dispatchClient(_leader, "отбой");
		PhantomAssertions.assertTrue(_directives.ownedSignal(_recipientProfile.profileId(), _clan.getId()).isEmpty(), "DISMISS retained directive ownership.");
		PhantomAssertions.assertEquals(1, _scheduler.find(_recipientProfile.profileId()).orElseThrow().activeSignalSources(), "DISMISS removed an unrelated Scheduler source.");
		context.record("goal030c2a.secondTransferAuthority", formerLeader + "->" + _leader.getObjectId());
		context.record("goal030c2a.standbyDismissDispatches", standby.dispatchId() + "/" + dismiss.dispatchId());
		context.record("goal030c2a.standbyEffect", "WARM/300000");
	}

	private void shutdownLifecycle(PhantomTestContext context)
	{
		dispatchClient(_leader, "готовность");
		PhantomAssertions.assertTrue(_directives.ownedSignal(_recipientProfile.profileId(), _clan.getId()).isPresent(), "Shutdown fixture has no directive signal.");
		stopConversationAndDirectives();
		final var snapshot = _directives.snapshot();
		PhantomAssertions.assertEquals(PhantomClanDirectiveService.ServiceState.STOPPED, snapshot.state(), "Directive service did not stop.");
		PhantomAssertions.assertEquals(0, snapshot.operationClaims(), "Directive service retained operation claims.");
		PhantomAssertions.assertEquals(0, snapshot.ownedSignals(), "Directive service retained owned signals.");
		PhantomAssertions.assertFalse(ChatObservationService.getInstance().snapshot().observerRegistered(), "Conversation retained the global chat observer.");
		PhantomAssertions.assertEquals(1, _scheduler.find(_recipientProfile.profileId()).orElseThrow().activeSignalSources(), "Shutdown removed unrelated or retained directive Scheduler sources.");
		PhantomAssertions.assertEquals(0, _schedulerMaterialization.materializeCalls.get(), "Directive lifecycle directly materialized a Phantom.");
		context.record("goal030c2a.singleObserver", "ConversationService-only");
		context.record("goal030c2a.directiveMetrics", snapshot);
		context.record("goal030c2a.cleanupBeforeScheduler", "owned=0,claims=0,unrelated=1");
	}

	private void assignRoles(PhantomProfile leaderProfile, Player leader, PhantomProfile recipientProfile, Player recipient)
	{
		_leaderProfile = leaderProfile;
		_leader = leader;
		_recipientProfile = recipientProfile;
		_recipient = recipient;
	}

	private void swapRoles()
	{
		final PhantomProfile profile = _leaderProfile;
		final Player player = _leader;
		_leaderProfile = _recipientProfile;
		_leader = _recipient;
		_recipientProfile = profile;
		_recipient = player;
	}

	private void tunePositive(long profileId, SubjectRef leaderSubject)
	{
		for (int attempt = 0; (attempt < 20) && (modifier(profileId, leaderSubject) < -300); attempt++)
		{
			recordFixture(profileId, leaderSubject, "agreement.fulfilled", "positive-fulfilled");
			recordFixture(profileId, leaderSubject, "party.leader.transferred", "positive-competence");
		}
		PhantomAssertions.assertTrue(modifier(profileId, leaderSubject) >= -300, "Positive social fixture cannot reach ASSEMBLE acceptance.");
	}

	private void tuneNegative(long profileId, SubjectRef leaderSubject)
	{
		for (int attempt = 0; (attempt < 20) && (modifier(profileId, leaderSubject) > -900); attempt++)
		{
			recordFixture(profileId, leaderSubject, "agreement.broken", "negative-broken");
			recordFixture(profileId, leaderSubject, "clan.member.expelled", "negative-expelled");
		}
	}

	private void recordFixture(long profileId, SubjectRef subject, String eventKey, String label)
	{
		final long sequence = ++_fixtureSequence;
		final String identity = "goal030c2a.fixture|" + label + "|" + sequence + "|" + profileId + "|" + subject.stableKey();
		final SocialEvent event = new SocialEvent(profileId, PhantomSocialModel.sha256(identity), eventKey, subject, nowMinute(), 1000, PhantomSocialModel.sha256(identity + "|evidence"), new SocialEventContext(AffiliationKind.SAME_CLAN));
		PhantomAssertions.assertTrue(_social.record(event).durable(), "Social fixture event was not durable: " + eventKey);
	}

	private int modifier(long profileId, SubjectRef subject)
	{
		final var result = _social.modifier(profileId, subject, "clan.directive.obedience", nowMinute());
		PhantomAssertions.assertTrue(result.available(), "Obedience modifier is unavailable.");
		return result.value().deltaBasisPoints();
	}

	private PhantomSocialModel.RelationshipSnapshot social(long profileId, SubjectRef subject)
	{
		final var result = _social.snapshot(profileId, subject, 24, nowMinute());
		PhantomAssertions.assertTrue(result.available(), "Social snapshot is unavailable.");
		return result.value().relationship();
	}

	private ChatDispatch dispatchClient(Player speaker, String text)
	{
		final ChatObservationService observation = ChatObservationService.getInstance();
		try (var scope = observation.openClientDispatch(speaker.getObjectId(), speaker.getName(), ChatType.CLAN, "", text, System.currentTimeMillis()))
		{
			PhantomAssertions.assertTrue(scope.descriptor() != null, "CLIENT_CHAT directive scope is inert.");
			_clanHandler.onChat(ChatType.CLAN, speaker, "", text);
			return new ChatDispatch(scope.descriptor().dispatchId(), scope.deliveries());
		}
	}

	private ChatDispatch dispatchGenerated(Player speaker, String text)
	{
		final ChatObservationService observation = ChatObservationService.getInstance();
		try (var scope = observation.openGeneratedDispatch(speaker.getObjectId(), speaker.getName(), ChatType.CLAN, "", text, System.currentTimeMillis()))
		{
			PhantomAssertions.assertTrue(scope.descriptor() != null, "Generated directive scope is inert.");
			_clanHandler.onChat(ChatType.CLAN, speaker, "", text);
			return new ChatDispatch(scope.descriptor().dispatchId(), scope.deliveries());
		}
	}

	private void stopConversationAndDirectives()
	{
		if (_conversationStopped)
		{
			return;
		}
		if (_conversation != null)
		{
			_conversation.beginStop();
		}
		if (_directives != null)
		{
			_directives.close();
		}
		if (_conversation != null)
		{
			PhantomAssertions.assertTrue(_conversation.finishStop(), "ConversationService did not release the sole observer.");
		}
		_conversationStopped = true;
	}

	private void cleanupClan()
	{
		if ((_clan != null) && (ClanTable.getInstance().getClan(_clan.getId()) != null))
		{
			ClanTable.getInstance().destroyClan(_clan.getId());
		}
		_clan = null;
		resetPenalties(_primary);
		resetPenalties(_observer);
	}

	private void deleteProfile(PhantomProfile profile)
	{
		if ((_profiles != null) && (profile != null))
		{
			_profiles.find(profile.profileId()).ifPresent(current -> _profiles.delete(current.profileId(), current.rowVersion()));
		}
	}

	private static void resetPenalties(Player player)
	{
		if (player != null)
		{
			player.setClanJoinExpiryTime(0);
			player.setClanCreateExpiryTime(0);
		}
	}

	private static IChatHandler nativeClanHandler() throws Exception
	{
		final Field loaderField = ScriptExecutor.class.getDeclaredField("SCRIPT_CLASS_LOADER");
		loaderField.setAccessible(true);
		final ClassLoader loader = (ClassLoader) loaderField.get(null);
		return (IChatHandler) Class.forName("handlers.chat.channels.ChatClan", true, loader).getConstructor().newInstance();
	}
	private static long nowMinute()
	{
		return System.currentTimeMillis() / 60000L;
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

	private record ChatDispatch(long dispatchId, int deliveries)
	{
	}

	private static final class CountingMaterializationPort implements PhantomActivityMaterializationPort
	{
		private final AtomicInteger materializeCalls = new AtomicInteger();

		@Override
		public TransitionOutcome materialize(long profileId)
		{
			materializeCalls.incrementAndGet();
			return TransitionOutcome.transientBlock();
		}

		@Override
		public TransitionOutcome dematerialize(long profileId)
		{
			return TransitionOutcome.success();
		}

		@Override
		public TransitionOutcome retryCleanup(long profileId)
		{
			return TransitionOutcome.success();
		}

		@Override
		public boolean isMaterialized(long profileId)
		{
			return false;
		}

		@Override
		public boolean hasLifecycleOwnership(long profileId)
		{
			return false;
		}
	}
}

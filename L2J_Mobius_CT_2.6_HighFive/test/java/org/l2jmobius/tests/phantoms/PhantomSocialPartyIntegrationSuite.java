/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.TerminalOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialEventSink;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialStore;

/**
 * Real test-DB, materialized Player and canonical Party integration for the
 * downstream social observer.
 */
public final class PhantomSocialPartyIntegrationSuite implements PhantomTestSuite
{
	private static final long SEED = 18001801L;
	private static final long EPOCH_MINUTE = 300_000L;
	private static final String ZERO = "0".repeat(64);
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<Long> _cleanupProfiles = new ArrayList<>();
	private PhantomProfileRepository _profiles;
	private PhantomSocialCatalog _catalog;
	private java.nio.file.Path _moduleRoot;

	@Override
	public String id()
	{
		return "social-party-integration";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_profiles = PhantomProfileRepository.open();
		_moduleRoot = context.moduleRoot();
		_catalog = PhantomSocialCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
		context.record("socialIntegration.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		for (long profileId : List.copyOf(_cleanupProfiles))
		{
			deleteProfile(profileId);
		}
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-component-restart-is-byte-identical-and-query-write-free", _ -> componentRestart());
		registry.add("02-terminal-perspectives-real-identity-and-retry-are-exact", _ -> terminalPerspectives());
		registry.add("03-canonical-join-transfer-leave-expel-and-failure-are-downstream", context -> canonicalPartyLifecycle(context));
	}

	private void componentRestart()
	{
		final PhantomProfile profile = createProfile(_environment.primary().objectId());
		final SubjectRef subject = SubjectRef.character(_environment.observer().objectId());
		final PhantomSocialService first = service();
		try
		{
			PhantomAssertions.assertTrue(first.start(), "First social service did not start.");
			final SocialEvent event = event(profile.profileId(), "restart.component", "agreement.fulfilled", subject, EPOCH_MINUTE);
			PhantomAssertions.assertEquals(PhantomSocialEventSink.Status.RECORDED, first.record(event).status(), "Real DB social event was not recorded.");
			final byte[] before = component(profile.profileId()).payload();
			final long writes = first.snapshot().durableWrites();
			PhantomAssertions.assertTrue(first.snapshot(profile.profileId(), subject, 24, EPOCH_MINUTE + 1440).available(), "Real DB social snapshot is unavailable.");
			PhantomAssertions.assertEquals(writes, first.snapshot().durableWrites(), "Query-only decay wrote to the real test DB.");
			first.beginStop();
			PhantomAssertions.assertTrue(first.finishStop(), "First social service did not drain.");

			final PhantomSocialService restarted = service();
			PhantomAssertions.assertTrue(restarted.start(), "Restarted social service did not start.");
			PhantomAssertions.assertTrue(restarted.snapshot(profile.profileId(), subject, 24, EPOCH_MINUTE + 1440).available(), "Restarted social state did not decode.");
			PhantomAssertions.assertEquals(PhantomSocialEventSink.Status.IDEMPOTENT, restarted.record(event).status(), "Restarted service duplicated an exact terminal retry.");
			PhantomAssertions.assertTrue(Arrays.equals(before, component(profile.profileId()).payload()), "Restart query changed durable social.state bytes.");
			PhantomAssertions.assertEquals(1L, componentCount(profile.profileId()), "Social integration did not retain exactly one social.state component.");
			restarted.beginStop();
			PhantomAssertions.assertTrue(restarted.finishStop(), "Restarted social service did not drain.");
		}
		finally
		{
			deleteProfile(profile.profileId());
		}
	}

	private void terminalPerspectives()
	{
		final PhantomProfile requester = createProfile(_environment.primary().objectId());
		final PhantomProfile invitee = createProfile(_environment.observer().objectId());
		final PhantomSocialService social = service();
		PhantomPartyCoordinator coordinator = null;
		try
		{
			PhantomAssertions.assertTrue(social.start(), "Terminal social service did not start.");
			final CanonicalBackend backend = new CanonicalBackend(_profiles, null);
			coordinator = coordinator(new PhantomPartyStore(_profiles), new PhantomGoalStateStore(_profiles), backend, social, EPOCH_MINUTE);
			PhantomAssertions.assertTrue(coordinator.start(), "Terminal coordinator did not start.");

			final PartyInvitation refused = invitation(9001, _environment.primary().objectId(), 90901);
			coordinator.terminal(refused, OptionalLong.of(requester.profileId()), OptionalLong.empty(), TerminalOutcome.REFUSED, "party.invite.refused");
			final PartyInvitation expired = invitation(9002, 90902, _environment.observer().objectId());
			coordinator.terminal(expired, OptionalLong.empty(), OptionalLong.of(invitee.profileId()), TerminalOutcome.EXPIRED, "party.invite.expired");
			pulse(coordinator);
			final byte[] requesterBeforeRetry = component(requester.profileId()).payload();
			final byte[] inviteeBeforeRetry = component(invitee.profileId()).payload();
			coordinator.terminal(refused, OptionalLong.of(requester.profileId()), OptionalLong.empty(), TerminalOutcome.REFUSED, "party.invite.refused");
			coordinator.terminal(expired, OptionalLong.empty(), OptionalLong.of(invitee.profileId()), TerminalOutcome.EXPIRED, "party.invite.expired");
			pulse(coordinator);
			PhantomAssertions.assertTrue(Arrays.equals(requesterBeforeRetry, component(requester.profileId()).payload()), "Refused terminal retry duplicated social state.");
			PhantomAssertions.assertTrue(Arrays.equals(inviteeBeforeRetry, component(invitee.profileId()).payload()), "Expired terminal retry duplicated social state.");
			PhantomAssertions.assertTrue(social.snapshot(requester.profileId(), SubjectRef.character(90901), 24, EPOCH_MINUTE).available(), "Refused real counterpart was not keyed by character object ID.");
			PhantomAssertions.assertTrue(social.snapshot(invitee.profileId(), SubjectRef.character(90902), 24, EPOCH_MINUTE).available(), "Expired real counterpart was not keyed by character object ID.");
			PhantomAssertions.assertEquals(2L, social.snapshot().recordedEvents(), "Terminal perspective event count changed.");
			PhantomAssertions.assertEquals(2L, social.snapshot().idempotentEvents(), "Terminal retry was not idempotent.");
		}
		finally
		{
			stop(coordinator);
			stop(social);
			deleteProfile(requester.profileId());
			deleteProfile(invitee.profileId());
		}
	}

	private void canonicalPartyLifecycle(PhantomTestContext context) throws Exception
	{
		final PhantomProfile firstProfile = createProfile(_environment.primary().objectId());
		final PhantomProfile secondProfile = createProfile(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		final PhantomSocialService social = service();
		final SelectiveFailureSink sink = new SelectiveFailureSink(social);
		final PhantomGoalStateStore goals = new PhantomGoalStateStore(_profiles);
		PhantomPartyCoordinator coordinator = null;
		Player first = null;
		Player second = null;
		try
		{
			PhantomAssertions.assertTrue(materialization.start(), "Social Party materialization service did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(firstProfile.profileId()).status(), "First managed Party player did not materialize.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(secondProfile.profileId()).status(), "Second managed Party player did not materialize.");
			first = World.getInstance().getPlayer(_environment.primary().objectId());
			second = World.getInstance().getPlayer(_environment.observer().objectId());
			PhantomAssertions.assertTrue((first != null) && (second != null), "Materialized social Party players are absent from World.");
			PhantomAssertions.assertTrue(social.start(), "Canonical Party social service did not start.");
			final CanonicalBackend backend = new CanonicalBackend(_profiles, materialization);
			coordinator = coordinator(new PhantomPartyStore(_profiles), goals, backend, sink, EPOCH_MINUTE);
			PhantomAssertions.assertTrue(coordinator.start(), "Canonical Party coordinator did not start.");

			setGoal(goals, firstProfile.profileId(), goal(firstProfile.profileId(), 1, PhantomPartyCoordinator.FORM_GOAL, null));
			setGoal(goals, secondProfile.profileId(), goal(secondProfile.profileId(), 2, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", Integer.toString(first.getObjectId()))));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(firstProfile.profileId(), 1, 0, PhantomPartyModel.ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "social-integration"), List.of()), "Canonical Party form was rejected.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(firstProfile.profileId(), MemberRef.phantom(secondProfile.profileId(), second.getObjectId()), PartyDistributionType.FINDERS_KEEPERS), "Canonical managed invitation was not delivered.");
			final InvitationIdentity accepted = backend.lastInvitationIdentity();
			pulse(coordinator);
			PhantomAssertions.assertTrue(first.isInParty() && (first.getParty() == second.getParty()), "Canonical managed invitation did not create one real Party.");
			PhantomAssertions.assertEquals(2L, sink.recorded("party.invite.accepted.outbound", "party.invite.accepted.inbound"), "Accepted events were not recorded for both managed perspectives.");
			PhantomAssertions.assertEquals(2L, sink.recorded("party.member.joined"), "First canonical JOIN was not recorded exactly once for both managed perspectives.");

			final byte[] firstAfterAccept = component(firstProfile.profileId()).payload();
			final long coordinatorEventsBeforeRetry = coordinator.snapshot().socialEventsRecorded();
			final PartyInvitation acceptedRetry = invitation(accepted.sequence(), accepted.requesterObjectId(), accepted.inviteeObjectId());
			coordinator.terminal(acceptedRetry, OptionalLong.of(firstProfile.profileId()), OptionalLong.of(secondProfile.profileId()), TerminalOutcome.ACCEPTED, "");
			pulse(coordinator);
			PhantomAssertions.assertTrue(Arrays.equals(firstAfterAccept, component(firstProfile.profileId()).payload()), "Accepted terminal retry duplicated durable social state.");
			PhantomAssertions.assertEquals(coordinatorEventsBeforeRetry, coordinator.snapshot().socialEventsRecorded(), "Accepted terminal retry inflated exact-recorded party metrics.");

			long generation = coordinator.claim(firstProfile.profileId()).orElseThrow().state().groupGeneration();
			setGoal(goals, firstProfile.profileId(), goal(firstProfile.profileId(), 3, PhantomPartyCoordinator.TRANSFER_LEADER_GOAL, new PhantomDomainRef("profile", Long.toString(secondProfile.profileId()))));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.transferLeaderTarget(firstProfile.profileId(), 3, 0, generation, new PhantomDomainRef("profile", Long.toString(secondProfile.profileId()))), "Canonical leader transfer was rejected.");
			PhantomAssertions.assertEquals(second, second.getParty().getLeader(), "Canonical Party leader did not change.");

			generation = coordinator.claim(firstProfile.profileId()).orElseThrow().state().groupGeneration();
			setGoal(goals, firstProfile.profileId(), goal(firstProfile.profileId(), 4, PhantomPartyCoordinator.LEAVE_GOAL, null));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.leave(firstProfile.profileId(), 4, 0, generation), "Canonical member leave was rejected.");
			PhantomAssertions.assertFalse(first.isInParty(), "Canonical Party retained the leaving member.");

			reformAndInvite(coordinator, goals, secondProfile, second, firstProfile, first, 5, 6);
			generation = coordinator.claim(secondProfile.profileId()).orElseThrow().state().groupGeneration();
			setGoal(goals, secondProfile.profileId(), goal(secondProfile.profileId(), 7, PhantomPartyCoordinator.EXPEL_GOAL, new PhantomDomainRef("profile", Long.toString(firstProfile.profileId()))));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.expelTarget(secondProfile.profileId(), 7, 0, generation, new PhantomDomainRef("profile", Long.toString(firstProfile.profileId()))), "Canonical member expel was rejected.");
			PhantomAssertions.assertFalse(first.isInParty(), "Canonical Party retained the expelled member.");

			reformAndInvite(coordinator, goals, secondProfile, second, firstProfile, first, 8, 9);
			generation = coordinator.claim(firstProfile.profileId()).orElseThrow().state().groupGeneration();
			setGoal(goals, firstProfile.profileId(), goal(firstProfile.profileId(), 10, PhantomPartyCoordinator.LEAVE_GOAL, null));
			sink.fail("party.member.left");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.leave(firstProfile.profileId(), 10, 0, generation), "Social sink failure rolled back canonical Party leave.");
			PhantomAssertions.assertFalse(first.isInParty(), "Canonical Party rollback occurred after social failure.");
			PhantomAssertions.assertTrue(coordinator.snapshot().socialEventFailures() > 0, "Downstream social failure was not counted.");

			final byte[] beforeRestart = component(secondProfile.profileId()).payload();
			stop(coordinator);
			coordinator = null;
			stop(social);
			final PhantomSocialService restarted = service();
			PhantomAssertions.assertTrue(restarted.start(), "Restarted canonical Party social service did not start.");
			PhantomAssertions.assertTrue(restarted.snapshot(secondProfile.profileId(), SubjectRef.phantom(firstProfile.profileId()), 24, EPOCH_MINUTE + 1).available(), "Restarted canonical Party social state is unavailable.");
			PhantomAssertions.assertTrue(Arrays.equals(beforeRestart, component(secondProfile.profileId()).payload()), "Canonical Party social state was not byte-identical after restart.");
			restarted.beginStop();
			PhantomAssertions.assertTrue(restarted.finishStop(), "Restarted canonical Party social service did not drain.");
			context.record("socialIntegration.canonicalEvents", social.snapshot().recordedEvents());
		}
		finally
		{
			stop(coordinator);
			if ((first != null) && first.isInParty())
			{
				PartyInvitationService.getInstance().leave(first);
			}
			if ((second != null) && second.isInParty())
			{
				PartyInvitationService.getInstance().leave(second);
			}
			stop(social);
			materialization.shutdown();
			deleteProfile(firstProfile.profileId());
			deleteProfile(secondProfile.profileId());
			_environment.assertClean(_environment.primary(), first);
			_environment.assertClean(_environment.observer(), second);
		}
	}

	private static void reformAndInvite(PhantomPartyCoordinator coordinator, PhantomGoalStateStore goals, PhantomProfile leaderProfile, Player leader, PhantomProfile memberProfile, Player member, long formGoalId, long joinGoalId)
	{
		setGoal(goals, leaderProfile.profileId(), goal(leaderProfile.profileId(), formGoalId, PhantomPartyCoordinator.FORM_GOAL, null));
		setGoal(goals, memberProfile.profileId(), goal(memberProfile.profileId(), joinGoalId, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", Integer.toString(leader.getObjectId()))));
		PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(leaderProfile.profileId(), formGoalId, 0, PhantomPartyModel.ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "social-reform-" + formGoalId), List.of()), "Canonical Party re-form was rejected.");
		PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(leaderProfile.profileId(), MemberRef.phantom(memberProfile.profileId(), member.getObjectId()), PartyDistributionType.FINDERS_KEEPERS), "Canonical Party re-invitation was not delivered.");
		pulse(coordinator);
		PhantomAssertions.assertTrue(leader.isInParty() && (leader.getParty() == member.getParty()), "Canonical Party re-invitation did not commit.");
	}

	private PhantomPartyCoordinator coordinator(PhantomPartyStore states, PhantomGoalStateStore goals, PhantomPartyBackend backend, PhantomSocialEventSink sink, long epochMinute)
	{
		final PhantomPartyRoleCatalog roles = PhantomPartyRoleCatalog.load(_moduleRoot.resolve("dist/game/data/phantoms/party/high-five-party-roles-v1.xml"));
		return new PhantomPartyCoordinator(states, goals, backend, roles, new PhantomPartyRouteCoordinator(null, null), new PhantomPartyTactics(null, backend), () -> ZERO, System::nanoTime, 64, sink, () -> epochMinute);
	}

	private PhantomSocialService service()
	{
		return new PhantomSocialService(_catalog, new PhantomSocialStore(PhantomProfileRepository.open(), _catalog), SEED, 64);
	}

	private PhantomProfile createProfile(int objectId)
	{
		final PhantomProfile profile = _profiles.create(objectId);
		_cleanupProfiles.add(profile.profileId());
		return profile;
	}

	private void deleteProfile(long profileId)
	{
		_profiles.find(profileId).ifPresent(profile -> _profiles.delete(profile.profileId(), profile.rowVersion()));
		_cleanupProfiles.remove(profileId);
	}

	private PhantomProfileComponent component(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomSocialModel.COMPONENT_TYPE).orElseThrow();
	}

	private static long componentCount(long profileId)
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id=? AND component_type=?"))
		{
			statement.setLong(1, profileId);
			statement.setString(2, PhantomSocialModel.COMPONENT_TYPE);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Social component count returned no row.");
				return result.getLong(1);
			}
		}
		catch (Exception e)
		{
			throw new IllegalStateException("Could not count social components in the test DB.", e);
		}
	}

	private static void setGoal(PhantomGoalStateStore goals, long profileId, PhantomGoal goal)
	{
		goals.load(profileId).ifPresentOrElse(stored -> goals.replace(profileId, stored.rowVersion(), goal), () -> goals.insert(profileId, goal));
	}

	private static PhantomGoal goal(long profileId, long goalId, String type, PhantomDomainRef target)
	{
		return new PhantomGoal(goalId, type, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), target, 1, 0, null, List.of(), null, "social.party.integration", 500, 0, 0, 0, java.util.Map.of(), "social.party.integration", 0);
	}

	private static PartyInvitation invitation(long sequence, int requesterObjectId, int inviteeObjectId)
	{
		return new PartyInvitation(new InvitationIdentity(sequence, requesterObjectId, inviteeObjectId), requesterObjectId, MemberRef.real(requesterObjectId).stableKey(), inviteeObjectId, MemberRef.real(inviteeObjectId).stableKey(), PartyDistributionType.FINDERS_KEEPERS, requesterObjectId, Long.MAX_VALUE);
	}

	private static SocialEvent event(long ownerProfileId, String identity, String key, SubjectRef subject, long minute)
	{
		return new SocialEvent(ownerProfileId, PhantomSocialModel.sha256(identity), key, subject, minute, 1000, PhantomSocialModel.sha256("evidence|" + identity));
	}

	private static void pulse(PhantomPartyCoordinator coordinator)
	{
		for (int pulse = 0; pulse < 24; pulse++)
		{
			coordinator.onPulse();
		}
	}

	private static void stop(PhantomPartyCoordinator coordinator)
	{
		if (coordinator != null)
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Social Party coordinator did not drain.");
		}
	}

	private static void stop(PhantomSocialService service)
	{
		if ((service != null) && (service.snapshot().state() == PhantomSocialService.ServiceState.RUNNING))
		{
			service.beginStop();
			PhantomAssertions.assertTrue(service.finishStop(), "Social service did not drain.");
		}
	}

	private static final class SelectiveFailureSink implements PhantomSocialEventSink
	{
		private final PhantomSocialEventSink _delegate;
		private final List<String> _recordedKeys = new ArrayList<>();
		private String _failureKey = "";

		private SelectiveFailureSink(PhantomSocialEventSink delegate)
		{
			_delegate = delegate;
		}

		private void fail(String eventKey)
		{
			_failureKey = eventKey;
		}

		@Override
		public Result record(SocialEvent event)
		{
			if (_failureKey.equals(event.eventKey()))
			{
				throw new IllegalStateException("Injected downstream social failure.");
			}
			final Result result = _delegate.record(event);
			if (result.status() == PhantomSocialEventSink.Status.RECORDED)
			{
				_recordedKeys.add(event.eventKey());
			}
			return result;
		}

		private long recorded(String... eventKeys)
		{
			return _recordedKeys.stream().filter(key -> List.of(eventKeys).contains(key)).count();
		}
	}

	private static final class CanonicalBackend implements PhantomPartyBackend
	{
		private final PhantomProfileRepository _profiles;
		private final PhantomMaterializationService _materialization;
		private InvitationIdentity _lastInvitationIdentity;

		private CanonicalBackend(PhantomProfileRepository profiles, PhantomMaterializationService materialization)
		{
			_profiles = profiles;
			_materialization = materialization;
		}

		@Override
		public OptionalLong managedProfileId(int characterObjectId)
		{
			return _profiles.findByCharacterObjectId(characterObjectId).map(profile -> OptionalLong.of(profile.profileId())).orElseGet(OptionalLong::empty);
		}

		@Override
		public Optional<MemberRef> currentMember(long profileId)
		{
			return _profiles.find(profileId).filter(profile -> profile.characterObjectId() != null).map(profile -> MemberRef.phantom(profileId, profile.characterObjectId()));
		}

		@Override
		public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution)
		{
			final InviteResult result = PartyInvitationService.getInstance().invite(player(requester), player(target), distribution.getId());
			_lastInvitationIdentity = result.identity();
			return result;
		}

		private InvitationIdentity lastInvitationIdentity()
		{
			return _lastInvitationIdentity;
		}

		@Override
		public RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity)
		{
			return PartyInvitationService.getInstance().respond(player(invitee), response, identity);
		}

		@Override
		public MembershipOutcome leave(MemberRef member)
		{
			return PartyInvitationService.getInstance().leave(player(member));
		}

		@Override
		public MembershipOutcome expel(MemberRef requester, MemberRef member)
		{
			return PartyInvitationService.getInstance().expel(player(requester), player(member));
		}

		@Override
		public MembershipOutcome transferLeader(MemberRef requester, MemberRef member)
		{
			return PartyInvitationService.getInstance().transferLeader(player(requester), player(member));
		}

		@Override
		public Optional<PartySnapshot> observe(MemberRef member)
		{
			final Player player = player(member);
			final Party party = player.getParty();
			if (party == null)
			{
				return Optional.empty();
			}
			final List<MemberRef> members = party.getMembers().stream().map(this::reference).toList();
			return Optional.of(new PartySnapshot(reference(party.getLeader()), members, party.getDistributionType()));
		}

		@Override
		public Optional<MemberSnapshot> memberSnapshot(MemberRef member)
		{
			final Player player = player(member);
			return Optional.of(new MemberSnapshot(member, player.getActiveClass(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), percent(player.getCurrentHp(), player.getMaxHp()), percent(player.getCurrentMp(), player.getMaxMp()), percent(player.getCurrentCp(), player.getMaxCp()), player.isDead(), player.isCastingNow(), player.isAttackingNow(), player.isMoving(), player.getTarget() == null ? 0 : player.getTarget().getObjectId(), List.of(), List.of(), ZERO));
		}

		@Override
		public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId)
		{
			return List.of();
		}

		@Override
		public boolean materialize(long profileId)
		{
			return (_materialization != null) && switch (_materialization.materialize(profileId).status())
			{
				case SUCCESS, ALREADY_ACTIVE -> true;
				default -> false;
			};
		}

		private Player player(MemberRef member)
		{
			final Player player = World.getInstance().getPlayer(member.characterObjectId());
			if (player == null)
			{
				throw new IllegalStateException("Canonical Party player is absent from World.");
			}
			return player;
		}

		private MemberRef reference(Player player)
		{
			return _profiles.findByCharacterObjectId(player.getObjectId()).map(profile -> MemberRef.phantom(profile.profileId(), player.getObjectId())).orElseGet(() -> MemberRef.real(player.getObjectId()));
		}

		private static int percent(double current, double maximum)
		{
			return maximum <= 0 ? 0 : Math.max(0, Math.min(100, (int) Math.round((current * 100.0) / maximum)));
		}
	}
}

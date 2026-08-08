/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PreparationOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.TerminalOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.DeliveryRegistration;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyOperation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.rift.L2jPhantomRiftPartyPort;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService;

/**
 * Real test-DB, materialized Player, ordinary outbound-session and canonical
 * Party integration. No packet handler or fabricated GameClient is used.
 */
public final class PhantomPartyServerIntegrationSuite implements PhantomTestSuite
{
	private static final String ZERO = "0".repeat(64);
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PhantomProfileRepository _profiles;

	@Override
	public String id()
	{
		return "party-server-integration";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_environment.initialize(context);
		_profiles = PhantomProfileRepository.open();
		context.record("partyIntegration.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-bilateral-expiry-cancel-retry-and-terminal-identity", _ -> testCancelRetry());
		registry.add("02-real-party-state-persists-and-reloads-from-db", _ -> testPartyPersistence());
		registry.add("03-canonical-transfer-expel-and-leave-postconditions", _ -> testMembershipCommands());
		registry.add("04-both-managed-identities-reach-prepare-and-terminal", _ -> testBothManagedCallbacks());
		registry.add("05-rift-production-port-canonical-managed-accept", this::testRiftProductionPort);
	}

	private void testCancelRetry() throws Exception
	{
		try (PartyFixture fixture = openPartyFixture())
		{
			final InviteResult first = fixture.invite();
			PhantomAssertions.assertTrue(first.delivered(), "Materialized-to-real invitation was not delivered.");
			expireRequester(fixture.managed());
			final InviteResult expiredRetry = fixture.invite();
			PhantomAssertions.assertTrue(expiredRetry.delivered(), "Requester-side expiration retained an invitee/requester index.");
			PhantomAssertions.assertEquals(PartyInvitationDelivery.TerminalOutcome.EXPIRED, fixture.delivery().lastTerminal().get(), "Requester-side expiration did not publish exact EXPIRED terminal callback.");
			PhantomAssertions.assertTrue(PartyInvitationService.getInstance().cancel(expiredRetry.identity()), "Exact invitation cancellation lost ownership.");
			PhantomAssertions.assertEquals(PartyInvitationDelivery.TerminalOutcome.CANCELLED, fixture.delivery().lastTerminal().get(), "Managed requester did not receive exact CANCELLED terminal callback.");

			final InviteResult retry = fixture.invite();
			PhantomAssertions.assertTrue(retry.delivered(), "Requester/invitee indexes remained busy after exact cancellation.");
			PhantomAssertions.assertTrue(retry.identity().sequence() > expiredRetry.identity().sequence(), "Invitation retry reused a stale identity.");
			PhantomAssertions.assertEquals(RespondOutcome.ACCEPTED, fixture.accept(retry.identity()), "Ordinary client-side invitee did not accept the exact retry.");
			PhantomAssertions.assertEquals(PartyInvitationDelivery.TerminalOutcome.ACCEPTED, fixture.delivery().lastTerminal().get(), "Managed requester did not receive ACCEPTED terminal callback.");
			PhantomAssertions.assertEquals(3, fixture.delivery().terminalCount().get(), "Invitation terminal callback was not exactly once per identity.");
			PhantomAssertions.assertTrue(fixture.output().snapshot().recordedPacketClasses().contains("AskJoinParty"), "Ordinary outbound session did not receive the real client prompt.");
			PhantomAssertions.assertEquals(null, fixture.real().getActiveRequester(), "Terminal acceptance retained the request field.");
		}
	}

	private static void expireRequester(Player requester) throws Exception
	{
		final Field expiry = Player.class.getDeclaredField("_requestExpireTime");
		expiry.setAccessible(true);
		expiry.setLong(requester, 0);
	}

	private void testPartyPersistence() throws Exception
	{
		try (PartyFixture fixture = openPartyFixture())
		{
			final InviteResult invite = fixture.invite();
			PhantomAssertions.assertEquals(RespondOutcome.ACCEPTED, fixture.accept(invite.identity()), "Canonical Party setup failed.");
			final MemberRef leader = MemberRef.phantom(fixture.profile().profileId(), fixture.managed().getObjectId());
			final MemberRef real = MemberRef.real(fixture.real().getObjectId());
			final String groupId = PhantomPartyModel.sha256("party.integration|" + fixture.profile().profileId());
			final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256("party.integration.operation|" + fixture.profile().profileId()), OperationKind.JOIN, OperationPhase.COMMITTED, leader, real, 1, 0, ZERO, invite.identity().sequence(), System.nanoTime() + 1_000_000_000L, "");
			final PartyState draft = new PartyState(groupId, 1, 1, StateStatus.LEADER, leader, "", ZERO, List.of(leader), List.of(real), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "integration"), List.of(), List.of(), null, operation, ZERO, ZERO, "");
			final PartyState committed = new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), draft.ownRoleKey(), draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), null, operation, draft.progressionHash(), draft.topologyHash(), "");
			final PhantomPartyStore firstStore = new PhantomPartyStore(_profiles);
			final var stored = firstStore.save(fixture.profile().profileId(), -1, committed);
			final var reloaded = new PhantomPartyStore(PhantomProfileRepository.open()).load(fixture.profile().profileId()).orElseThrow();
			PhantomAssertions.assertEquals(stored.state(), reloaded.state(), "Restarted DB adapter did not reload byte-identical party state.");
			PhantomAssertions.assertEquals(fixture.managed(), fixture.managed().getParty().getLeader(), "Persisted leader differs from canonical Party leader.");
			PhantomAssertions.assertTrue(fixture.managed().getParty().containsPlayer(fixture.real()), "Persisted real member is absent from canonical Party.");
			PhantomAssertions.assertEquals(1L, componentCount(fixture.profile().profileId()), "Party integration did not write exactly one DB component row.");
		}
	}

	private void testMembershipCommands() throws Exception
	{
		try (PartyFixture fixture = openPartyFixture())
		{
			final InviteResult invite = fixture.invite();
			PhantomAssertions.assertEquals(RespondOutcome.ACCEPTED, fixture.accept(invite.identity()), "Canonical Party setup failed.");
			PhantomAssertions.assertEquals(MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().transferLeader(fixture.managed(), fixture.real()), "Canonical leader transfer failed.");
			PhantomAssertions.assertEquals(fixture.real(), fixture.real().getParty().getLeader(), "Leader transfer did not change the exact Party leader.");
			PhantomAssertions.assertEquals(MembershipOutcome.COMPLETED, PartyInvitationService.getInstance().expel(fixture.real(), fixture.managed()), "Real leader expel failed.");
			PhantomAssertions.assertFalse(fixture.managed().isInParty(), "Expelled materialized Player retained Party membership.");
			PhantomAssertions.assertTrue((fixture.real().getParty() == null) || !fixture.real().getParty().containsPlayer(fixture.managed()), "Canonical Party retained the expelled member.");
			PhantomAssertions.assertEquals(MembershipOutcome.NOT_IN_PARTY, PartyInvitationService.getInstance().leave(fixture.managed()), "Idempotent leave postcondition was not observable.");
		}
	}

	private void testBothManagedCallbacks() throws Exception
	{
		final PhantomProfile requesterProfile = _profiles.create(_environment.primary().objectId());
		final PhantomProfile inviteeProfile = _profiles.create(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		DeliveryRegistration registration = null;
		Player requester = null;
		Player invitee = null;
		try
		{
			PhantomAssertions.assertTrue(materialization.start(), "Dual-managed materialization service did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(requesterProfile.profileId()).status(), "Managed requester did not materialize.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(inviteeProfile.profileId()).status(), "Managed invitee did not materialize.");
			requester = World.getInstance().getPlayer(_environment.primary().objectId());
			invitee = World.getInstance().getPlayer(_environment.observer().objectId());
			final Player exactRequester = requester;
			final Player exactInvitee = invitee;
			final AtomicInteger prepared = new AtomicInteger();
			final AtomicInteger delivered = new AtomicInteger();
			final AtomicInteger terminal = new AtomicInteger();
			final AtomicReference<TerminalOutcome> outcome = new AtomicReference<>();
			registration = PartyInvitationService.getInstance().installManagedDelivery(new PartyInvitationDelivery()
			{
				@Override
				public OptionalLong managedIdentity(int characterObjectId)
				{
					if (characterObjectId == exactRequester.getObjectId())
					{
						return OptionalLong.of(requesterProfile.profileId());
					}
					return characterObjectId == exactInvitee.getObjectId() ? OptionalLong.of(inviteeProfile.profileId()) : OptionalLong.empty();
				}

				@Override
				public PreparationOutcome prepare(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee)
				{
					PhantomAssertions.assertEquals(OptionalLong.of(requesterProfile.profileId()), managedRequester, "Prepare lost managed requester.");
					PhantomAssertions.assertEquals(OptionalLong.of(inviteeProfile.profileId()), managedInvitee, "Prepare lost managed invitee.");
					prepared.incrementAndGet();
					return PreparationOutcome.ACCEPTED;
				}

				@Override
				public DeliveryOutcome deliver(PartyInvitation invitation, long managedIdentity)
				{
					PhantomAssertions.assertEquals(inviteeProfile.profileId(), managedIdentity, "Managed delivery targeted the wrong profile.");
					delivered.incrementAndGet();
					return DeliveryOutcome.ACCEPTED;
				}

				@Override
				public void terminal(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee, TerminalOutcome terminalOutcome, String reasonKey)
				{
					PhantomAssertions.assertEquals(OptionalLong.of(requesterProfile.profileId()), managedRequester, "Terminal lost managed requester.");
					PhantomAssertions.assertEquals(OptionalLong.of(inviteeProfile.profileId()), managedInvitee, "Terminal lost managed invitee.");
					terminal.incrementAndGet();
					outcome.set(terminalOutcome);
				}
			});
			final InviteResult invitation = PartyInvitationService.getInstance().invite(requester, invitee, PartyDistributionType.FINDERS_KEEPERS.getId());
			PhantomAssertions.assertTrue(invitation.delivered(), "Dual-managed invitation was not published.");
			PhantomAssertions.assertEquals(RespondOutcome.ACCEPTED, PartyInvitationService.getInstance().respond(invitee, Response.ACCEPT, invitation.identity()).outcome(), "Dual-managed exact response failed.");
			PhantomAssertions.assertEquals(1, prepared.get(), "Dual-managed prepare callback count changed.");
			PhantomAssertions.assertEquals(1, delivered.get(), "Managed invitee delivery callback count changed.");
			PhantomAssertions.assertEquals(1, terminal.get(), "Dual-managed terminal callback was not exactly once.");
			PhantomAssertions.assertEquals(TerminalOutcome.ACCEPTED, outcome.get(), "Dual-managed terminal outcome changed.");
		}
		finally
		{
			if (registration != null)
			{
				registration.close();
			}
			if ((requester != null) && requester.isInParty())
			{
				PartyInvitationService.getInstance().leave(requester);
			}
			if ((invitee != null) && invitee.isInParty())
			{
				PartyInvitationService.getInstance().leave(invitee);
			}
			materialization.shutdown();
			deleteProfile(requesterProfile.profileId());
			deleteProfile(inviteeProfile.profileId());
			_environment.assertClean(_environment.primary(), requester);
			_environment.assertClean(_environment.observer(), invitee);
		}
	}

	private void testRiftProductionPort(PhantomTestContext context) throws Exception
	{
		final PhantomProfile leaderProfile = _profiles.create(_environment.primary().objectId());
		final PhantomProfile inviteeProfile = _profiles.create(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		PhantomPartyCoordinator coordinator = null;
		Player leaderPlayer = null;
		Player inviteePlayer = null;
		try
		{
			PhantomAssertions.assertTrue(materialization.start(), "Rift acceptance materialization did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(leaderProfile.profileId()).status(), "Rift leader did not materialize.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(inviteeProfile.profileId()).status(), "Rift invitee did not materialize.");
			leaderPlayer = World.getInstance().getPlayer(_environment.primary().objectId());
			inviteePlayer = World.getInstance().getPlayer(_environment.observer().objectId());
			final MemberRef leader = MemberRef.phantom(leaderProfile.profileId(), leaderPlayer.getObjectId());
			final MemberRef invitee = MemberRef.phantom(inviteeProfile.profileId(), inviteePlayer.getObjectId());
			final CanonicalPartyBackend backend = new CanonicalPartyBackend(Map.of(leaderProfile.profileId(), leader, inviteeProfile.profileId(), invitee), Map.of(leader, leaderPlayer, invitee, inviteePlayer));
			final MemoryGoalStore goals = new MemoryGoalStore();
			final long goalId = 23002311L;
			goals.put(leaderProfile.profileId(), new PhantomGoal(goalId, PhantomRiftService.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(leaderProfile.profileId())), new PhantomDomainRef("rift.tier", "1"), 1, 0, null, List.of(), null, "rift.acceptance", 500, 0, 0, 0, Map.of(), "rift.acceptance", 0));
			coordinator = new PhantomPartyCoordinator(new PhantomPartyStore(_profiles), goals, backend, PhantomPartyRoleCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(null, null), new PhantomPartyTactics(null, backend), () -> ZERO, System::nanoTime, 64);
			PhantomAssertions.assertTrue(coordinator.start(), "Rift acceptance coordinator did not start.");
			coordinator.installManagedInvitationPolicy(PhantomRiftService.GOAL_TYPE, ignored -> PhantomPartyCoordinator.ManagedInvitationDecision.ACCEPT);
			final L2jPhantomRiftPartyPort port = new L2jPhantomRiftPartyPort(coordinator);
			final String rosterHash = PhantomPartyModel.sha256("rift.acceptance.roster|" + leader.stableKey());
			final PhantomRiftModel.CanonicalRoster roster = new PhantomRiftModel.CanonicalRoster(leader, List.of(leader), PartyDistributionType.FINDERS_KEEPERS, false, false, rosterHash);
			final var binding = port.bind(leaderProfile.profileId(), goalId, 0, new PhantomDomainRef("rift.tier", "1"), List.of(), roster);
			PhantomAssertions.assertTrue(binding.stable(), "Real coordinator did not bind the exact Rift goal.");
			final var invitation = port.invite(leaderProfile.profileId(), invitee, PartyDistributionType.FINDERS_KEEPERS);
			PhantomAssertions.assertEquals(PhantomRiftService.InviteStatus.PENDING, invitation.status(), "Canonical managed invitation was not pending with full identity.");
			PhantomAssertions.assertEquals(leader.characterObjectId(), invitation.requesterObjectId(), "Canonical invitation requester identity changed.");
			PhantomAssertions.assertEquals(invitee.characterObjectId(), invitation.inviteeObjectId(), "Canonical invitation invitee identity changed.");
			PhantomAssertions.assertTrue(invitation.canonicalExpiresAtGameTick() > 0, "Canonical invitation expiry was not exposed.");
			for (int i = 0; i < 8; i++)
			{
				coordinator.onPulse();
			}
			PhantomAssertions.assertTrue(leaderPlayer.isInParty() && leaderPlayer.getParty().containsPlayer(inviteePlayer), "Target-side policy did not reach canonical ACCEPT membership.");
			PhantomAssertions.assertEquals(PhantomRiftService.InviteStatus.ACCEPTED, port.observeInvite(leaderProfile.profileId(), invitee, invitation.sequence()).status(), "L2j Rift port did not observe canonical ACCEPTED.");
			context.record("rift023a.canonicalInvitationSequence", invitation.sequence());
			context.record("rift023a.canonicalExpiry", invitation.canonicalExpiresAtGameTick());
		}
		finally
		{
			if (coordinator != null)
			{
				coordinator.beginStop();
				coordinator.finishStop();
			}
			if ((leaderPlayer != null) && leaderPlayer.isInParty())
			{
				PartyInvitationService.getInstance().leave(leaderPlayer);
			}
			if ((inviteePlayer != null) && inviteePlayer.isInParty())
			{
				PartyInvitationService.getInstance().leave(inviteePlayer);
			}
			materialization.shutdown();
			deleteProfile(leaderProfile.profileId());
			deleteProfile(inviteeProfile.profileId());
			_environment.assertClean(_environment.primary(), leaderPlayer);
			_environment.assertClean(_environment.observer(), inviteePlayer);
		}
	}
	private PartyFixture openPartyFixture() throws Exception
	{
		final PhantomProfile profile = _profiles.create(_environment.primary().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		final PhantomMaterializationService materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		PhantomAssertions.assertTrue(materialization.start(), "Party materialization service did not start.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, materialization.materialize(profile.profileId()).status(), "Party integration Player did not materialize.");
		final Player managed = World.getInstance().getPlayer(_environment.primary().objectId());
		PhantomAssertions.assertTrue(managed != null, "Materialized Party Player is absent from World.");

		final Player real = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue(real != null, "Ordinary Party Player did not load.");
		final HeadlessPlayerOutboundSession output = new HeadlessPlayerOutboundSession(8, 64, 64);
		final Player.OutboundSessionAttachment attachment = real.attachOutboundSession(output);
		real.spawnMe();
		final ManagedDeliveryProbe delivery = new ManagedDeliveryProbe(profile.profileId(), managed.getObjectId());
		final DeliveryRegistration registration = PartyInvitationService.getInstance().installManagedDelivery(delivery);
		return new PartyFixture(profile, materialization, managed, real, output, attachment, delivery, registration);
	}

	private static long componentCount(long profileId) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM phantom_profile_components WHERE profile_id=? AND component_type=?"))
		{
			statement.setLong(1, profileId);
			statement.setString(2, PhantomPartyModel.COMPONENT_TYPE);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Party component count returned no row.");
				return result.getLong(1);
			}
		}
	}

	private void deleteProfile(long profileId)
	{
		_profiles.find(profileId).ifPresent(profile -> _profiles.delete(profile.profileId(), profile.rowVersion()));
	}

	private final class PartyFixture implements AutoCloseable
	{
		private final PhantomProfile _profile;
		private final PhantomMaterializationService _materialization;
		private final Player _managed;
		private final Player _real;
		private final HeadlessPlayerOutboundSession _output;
		private final Player.OutboundSessionAttachment _attachment;
		private final ManagedDeliveryProbe _delivery;
		private final DeliveryRegistration _registration;

		private PartyFixture(PhantomProfile profile, PhantomMaterializationService materialization, Player managed, Player real, HeadlessPlayerOutboundSession output, Player.OutboundSessionAttachment attachment, ManagedDeliveryProbe delivery, DeliveryRegistration registration)
		{
			_profile = profile;
			_materialization = materialization;
			_managed = managed;
			_real = real;
			_output = output;
			_attachment = attachment;
			_delivery = delivery;
			_registration = registration;
		}

		private InviteResult invite()
		{
			return PartyInvitationService.getInstance().invite(_managed, _real, PartyDistributionType.FINDERS_KEEPERS.getId());
		}

		private RespondOutcome accept(InvitationIdentity identity)
		{
			return PartyInvitationService.getInstance().respond(_real, Response.ACCEPT, identity).outcome();
		}

		private PhantomProfile profile()
		{
			return _profile;
		}

		private Player managed()
		{
			return _managed;
		}

		private Player real()
		{
			return _real;
		}

		private HeadlessPlayerOutboundSession output()
		{
			return _output;
		}

		private ManagedDeliveryProbe delivery()
		{
			return _delivery;
		}

		@Override
		public void close() throws Exception
		{
			_registration.close();
			if (_managed.isInParty())
			{
				PartyInvitationService.getInstance().leave(_managed);
			}
			if (_real.isInParty())
			{
				PartyInvitationService.getInstance().leave(_real);
			}
			_attachment.close();
			_environment.cleanupLoadedPlayer(_real);
			_materialization.shutdown();
			final PhantomProfile current = _profiles.find(_profile.profileId()).orElse(null);
			if (current != null)
			{
				_profiles.delete(current.profileId(), current.rowVersion());
			}
			_environment.assertClean(_environment.primary(), _managed);
			_environment.assertClean(_environment.observer(), _real);
		}
	}

	private static final class MemoryGoalStore implements PhantomGoalStore
	{
		private final Map<Long, StoredGoal> _goals = new TreeMap<>();

		private void put(long profileId, PhantomGoal goal)
		{
			final StoredGoal current = _goals.get(profileId);
			_goals.put(profileId, new StoredGoal(goal, current == null ? 0 : current.rowVersion() + 1));
		}

		@Override public boolean profileExists(long profileId) { return profileId > 0; }
		@Override public Optional<StoredGoal> load(long profileId) { return Optional.ofNullable(_goals.get(profileId)); }
		@Override public StoredGoal insert(long profileId, PhantomGoal goal) { put(profileId, goal); return _goals.get(profileId); }
		@Override public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			final StoredGoal current = _goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion)) { throw new IllegalStateException("Goal conflict."); }
			put(profileId, goal);
			return _goals.get(profileId);
		}
		@Override public void delete(long profileId, long expectedRowVersion) { _goals.remove(profileId); }
	}

	private static final class CanonicalPartyBackend implements PhantomPartyBackend
	{
		private final Map<Long, MemberRef> _members;
		private final Map<MemberRef, Player> _players;

		private CanonicalPartyBackend(Map<Long, MemberRef> members, Map<MemberRef, Player> players)
		{
			_members = members;
			_players = players;
		}

		@Override public OptionalLong managedProfileId(int characterObjectId) { return _members.values().stream().filter(member -> member.characterObjectId() == characterObjectId).mapToLong(MemberRef::profileId).findFirst(); }
		@Override public Optional<MemberRef> currentMember(long profileId) { return Optional.ofNullable(_members.get(profileId)); }
		@Override public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution) { return PartyInvitationService.getInstance().invite(_players.get(requester), _players.get(target), distribution.getId()); }
		@Override public PartyInvitationService.RespondResult respond(MemberRef invitee, Response response, InvitationIdentity identity) { return PartyInvitationService.getInstance().respond(_players.get(invitee), response, identity); }
		@Override public MembershipOutcome leave(MemberRef member) { return PartyInvitationService.getInstance().leave(_players.get(member)); }
		@Override public MembershipOutcome expel(MemberRef requester, MemberRef member) { return PartyInvitationService.getInstance().expel(_players.get(requester), _players.get(member)); }
		@Override public MembershipOutcome transferLeader(MemberRef requester, MemberRef member) { return PartyInvitationService.getInstance().transferLeader(_players.get(requester), _players.get(member)); }
		@Override public Optional<PartySnapshot> observe(MemberRef member)
		{
			final Player player = _players.get(member);
			if ((player == null) || (player.getParty() == null)) { return Optional.empty(); }
			final List<MemberRef> roster = player.getParty().getMembers().stream().map(this::reference).toList();
			return Optional.of(new PartySnapshot(reference(player.getParty().getLeader()), roster, player.getParty().getDistributionType()));
		}
		@Override public Optional<MemberSnapshot> memberSnapshot(MemberRef member)
		{
			final Player player = _players.get(member);
			return player == null ? Optional.empty() : Optional.of(new MemberSnapshot(member, player.getActiveClass(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), 100, 100, 100, player.isDead(), false, false, false, 0, List.of(), List.of(), ZERO));
		}
		@Override public List<PhantomPartyModel.MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId) { return List.of(); }
		@Override public boolean materialize(long profileId) { return _members.containsKey(profileId); }
		private MemberRef reference(Player player) { return _members.values().stream().filter(member -> member.characterObjectId() == player.getObjectId()).findFirst().orElse(MemberRef.real(player.getObjectId())); }
	}
	private static final class ManagedDeliveryProbe implements PartyInvitationDelivery
	{
		private final long _profileId;
		private final int _objectId;
		private final AtomicInteger _terminalCount = new AtomicInteger();
		private final AtomicReference<TerminalOutcome> _lastTerminal = new AtomicReference<>();

		private ManagedDeliveryProbe(long profileId, int objectId)
		{
			_profileId = profileId;
			_objectId = objectId;
		}

		@Override
		public OptionalLong managedIdentity(int characterObjectId)
		{
			return characterObjectId == _objectId ? OptionalLong.of(_profileId) : OptionalLong.empty();
		}

		@Override
		public PreparationOutcome prepare(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee)
		{
			PhantomAssertions.assertEquals(OptionalLong.of(_profileId), managedRequester, "Prepare lost managed requester identity.");
			PhantomAssertions.assertEquals(OptionalLong.empty(), managedInvitee, "Ordinary invitee was fabricated as managed.");
			return PreparationOutcome.ACCEPTED;
		}

		@Override
		public DeliveryOutcome deliver(PartyInvitation invitation, long managedIdentity)
		{
			throw new AssertionError("Phantom-to-real invite must use the ordinary client delivery path.");
		}

		@Override
		public void terminal(PartyInvitation invitation, OptionalLong managedRequester, OptionalLong managedInvitee, TerminalOutcome outcome, String reasonKey)
		{
			PhantomAssertions.assertEquals(OptionalLong.of(_profileId), managedRequester, "Terminal callback lost managed requester identity.");
			PhantomAssertions.assertEquals(invitation.identity().requesterObjectId(), _objectId, "Terminal callback changed invitation identity.");
			_terminalCount.incrementAndGet();
			_lastTerminal.set(outcome);
		}

		private AtomicInteger terminalCount()
		{
			return _terminalCount;
		}

		private AtomicReference<TerminalOutcome> lastTerminal()
		{
			return _lastTerminal;
		}
	}
}

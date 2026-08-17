/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
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
import org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleMatcher;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStore;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
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
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.EquipmentFact;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.MemberFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.RelationshipEvidence;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftBackend.ShotSupply;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.ConfigFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.EntryFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.InvitationStatus;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.PendingInvitationReceipt;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Preparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftModel.Stage;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftPersistencePort;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftPersistencePort.StoredPreparation;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftPolicy;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftReadinessService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftService.InviteStatus;

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
		registry.add("05-rift-production-provider-canonical-managed-accept", this::testRiftProductionPort);
		registry.add("06-rift-production-provider-stale-capability-defers", this::testRiftStaleCandidate);
		registry.add("07-rift-production-provider-negative-relationship-refuses", this::testRiftRefusal);
		registry.add("08-rift-production-provider-unavailable-evidence-defers-until-expiry", this::testRiftDeferExpiry);
		registry.add("09-command-channel-exact-consent-and-dismiss-seam", _ -> testCommandChannelBackendSeam());
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

	private void testCommandChannelBackendSeam() throws Exception
	{
		try (PartyFixture fixture = openPartyFixture())
		{
			final Party requesterParty = new Party(fixture.managed(), PartyDistributionType.FINDERS_KEEPERS);
			final Party inviteeParty = new Party(fixture.real(), PartyDistributionType.FINDERS_KEEPERS);
			fixture.managed().setParty(requesterParty);
			fixture.real().setParty(inviteeParty);
			PhantomAssertions.assertTrue(fixture.managed().getInventory().addItem(ItemProcessType.REWARD, 8871, 1, fixture.managed(), this) != null, "Could not create Goal017 Strategy Guide fixture.");
			final L2jPhantomPartyBackend backend = new L2jPhantomPartyBackend(_profiles, fixture.materialization(), null);
			final MemberRef requester = MemberRef.phantom(fixture.profile().profileId(), fixture.managed().getObjectId());
			final MemberRef invitee = MemberRef.real(fixture.real().getObjectId());
			var invitation = backend.inviteCommandChannel(requester, invitee);
			PhantomAssertions.assertTrue(invitation.delivered(), "Goal017 exact MemberRef MPCC invitation was not delivered.");
			PhantomAssertions.assertFalse(requesterParty.isInCommandChannel() || inviteeParty.isInCommandChannel(), "Both Phantom endpoints caused automatic MPCC acceptance.");
			PhantomAssertions.assertEquals(CommandChannelInvitationService.RespondOutcome.REFUSED, backend.respondCommandChannel(invitee, CommandChannelInvitationService.Response.REFUSE, invitation.identity()).outcome(), "Target-side exact REFUSE failed.");
			invitation = backend.inviteCommandChannel(requester, invitee);
			PhantomAssertions.assertTrue(backend.respondCommandChannel(invitee, CommandChannelInvitationService.Response.ACCEPT, invitation.identity()).accepted(), "Target-side exact ACCEPT failed.");
			PhantomAssertions.assertTrue((requesterParty.getCommandChannel() != null) && (requesterParty.getCommandChannel() == inviteeParty.getCommandChannel()), "Goal017 backend did not reach one canonical CommandChannel.");
			PhantomAssertions.assertEquals(CommandChannelInvitationService.DismissOutcome.COMPLETED, backend.dismissCommandChannel(requester, invitee), "Exact CC-leader MemberRef dismissal failed.");
			PhantomAssertions.assertFalse(requesterParty.isInCommandChannel() || inviteeParty.isInCommandChannel(), "Canonical dismiss/disband postcondition failed.");
		}
	}

	private void testRiftProductionPort(PhantomTestContext context) throws Exception
	{
		try (RiftScenarioFixture fixture = new RiftScenarioFixture(context))
		{
			final PendingInvitationReceipt invitation = fixture.advanceToInvitation();
			fixture.pulse(8);
			PhantomAssertions.assertTrue(fixture.leaderPlayer().isInParty() && fixture.leaderPlayer().getParty().containsPlayer(fixture.inviteePlayer()), "Production Rift provider did not reach canonical ACCEPT membership.");
			PhantomAssertions.assertEquals(InviteStatus.ACCEPTED, fixture.observe(invitation).status(), "Production Rift provider did not expose canonical ACCEPTED.");
			context.record("rift023b.canonicalInvitationSequence", invitation.sequence());
			context.record("rift023b.canonicalExpiry", invitation.canonicalExpiresAtGameTick());
		}
	}

	private void testRiftStaleCandidate(PhantomTestContext context) throws Exception
	{
		try (RiftScenarioFixture fixture = new RiftScenarioFixture(context))
		{
			final PendingInvitationReceipt invitation = fixture.advanceToInvitation();
			fixture.partyBackend().setCapabilities(fixture.invitee(), List.of());
			fixture.pulse(4);
			PhantomAssertions.assertEquals(InviteStatus.PENDING, fixture.observe(invitation).status(), "Stale RoleMatcher capability evidence auto-accepted the managed invitee.");
			PhantomAssertions.assertFalse(fixture.inviteePlayer().isInParty(), "Stale managed candidate reached canonical Party membership.");
			fixture.advanceService(3);
			PhantomAssertions.assertEquals(invitation.sequence(), fixture.observe(invitation).sequence(), "Stale policy retry duplicated the canonical invitation.");
		}
	}

	private void testRiftRefusal(PhantomTestContext context) throws Exception
	{
		try (RiftScenarioFixture fixture = new RiftScenarioFixture(context))
		{
			final PendingInvitationReceipt invitation = fixture.advanceToInvitation();
			fixture.riftBackend().setNegativeRelationship(true);
			fixture.pulse(8);
			PhantomAssertions.assertEquals(InviteStatus.REFUSED, fixture.observe(invitation).status(), "Negative invitee-to-leader relationship did not reach canonical REFUSED.");
			PhantomAssertions.assertFalse(fixture.leaderPlayer().isInParty() || fixture.inviteePlayer().isInParty(), "Canonical REFUSE mutated Party membership.");
			fixture.advanceService(1);
			PhantomAssertions.assertTrue(!fixture.preparation().refusals().isEmpty(), "Rift preparation did not observe typed refusal/cooldown.");
		}
	}

	private void testRiftDeferExpiry(PhantomTestContext context) throws Exception
	{
		try (RiftScenarioFixture fixture = new RiftScenarioFixture(context))
		{
			final PendingInvitationReceipt invitation = fixture.advanceToInvitation();
			fixture.riftBackend().setCandidateAvailable(false);
			fixture.pulse(4);
			PhantomAssertions.assertEquals(InviteStatus.PENDING, fixture.observe(invitation).status(), "Transient unavailable evidence was treated as REFUSE or ACCEPT instead of DEFER.");
			fixture.advanceService(3);
			final var stillPending = fixture.observe(invitation);
			PhantomAssertions.assertEquals(InviteStatus.PENDING, stillPending.status(), "DEFER did not retain the exact canonical invitation.");
			PhantomAssertions.assertEquals(invitation.sequence(), stillPending.sequence(), "DEFER retry duplicated the invitation identity.");
			expireRequester(fixture.leaderPlayer());
			PartyInvitationService.getInstance().observe(fixture.inviteePlayer());
			fixture.pulse(8);
			PhantomAssertions.assertEquals(InviteStatus.EXPIRED, fixture.observe(invitation).status(), "Deferred canonical invitation did not expire as EXPIRED.");
			fixture.advanceService(1);
			PhantomAssertions.assertTrue(fixture.preparation().refusals().stream().anyMatch(value -> value.reasonKey().contains("expired")), "Rift preparation did not record typed canonical expiry.");
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

		private PhantomMaterializationService materialization()
		{
			return _materialization;
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

	private static MemberCapability riftCapability(String key)
	{
		return new MemberCapability(key, "primary", 100, 1, 1, "SELF", true, true, true, "ready", 1000, "goal023b.rift.fixture");
	}

	private final class RiftScenarioFixture implements AutoCloseable
	{
		private static final long GOAL_ID = 23002312L;
		private final PhantomProfile _leaderProfile;
		private final PhantomProfile _inviteeProfile;
		private final PhantomMaterializationService _materialization;
		private final Player _leaderPlayer;
		private final Player _inviteePlayer;
		private final MemberRef _leader;
		private final MemberRef _invitee;
		private final CanonicalPartyBackend _partyBackend;
		private final CanonicalRiftBackend _riftBackend;
		private final MemoryRiftStore _store = new MemoryRiftStore();
		private final PhantomPartyCoordinator _coordinator;
		private final L2jPhantomRiftPartyPort _port;
		private final PhantomRiftService _service;
		private final PhantomPartyCoordinator.ManagedInvitationPolicyRegistration _policyRegistration;
		private PendingInvitationReceipt _invitation;

		private RiftScenarioFixture(PhantomTestContext context) throws Exception
		{
			_leaderProfile = _profiles.create(_environment.primary().objectId());
			_inviteeProfile = _profiles.create(_environment.observer().objectId());
			final PhantomMetrics metrics = new PhantomMetrics();
			_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
			PhantomAssertions.assertTrue(_materialization.start(), "Rift Goal023B materialization did not start.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_leaderProfile.profileId()).status(), "Rift Goal023B leader did not materialize.");
			PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_inviteeProfile.profileId()).status(), "Rift Goal023B invitee did not materialize.");
			_leaderPlayer = World.getInstance().getPlayer(_environment.primary().objectId());
			_inviteePlayer = World.getInstance().getPlayer(_environment.observer().objectId());
			_leader = MemberRef.phantom(_leaderProfile.profileId(), _leaderPlayer.getObjectId());
			_invitee = MemberRef.phantom(_inviteeProfile.profileId(), _inviteePlayer.getObjectId());
			_partyBackend = new CanonicalPartyBackend(Map.of(_leaderProfile.profileId(), _leader, _inviteeProfile.profileId(), _invitee), Map.of(_leader, _leaderPlayer, _invitee, _inviteePlayer), Map.of(_leader, List.of(riftCapability("combat.tank")), _invitee, List.of(riftCapability("combat.heal"))));
			final MemoryGoalStore goals = new MemoryGoalStore();
			goals.put(_leaderProfile.profileId(), new PhantomGoal(GOAL_ID, PhantomRiftService.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(_leaderProfile.profileId())), new PhantomDomainRef("rift.tier", "1"), 1, 0, null, List.of(), null, "rift.goal023b.acceptance", 500, 0, 0, 0, Map.of(), "rift.goal023b.acceptance", 0));
			final PhantomPartyRoleCatalog roleCatalog = PhantomPartyRoleCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/party/high-five-party-roles-v1.xml"));
			_coordinator = new PhantomPartyCoordinator(new PhantomPartyStore(_profiles), goals, _partyBackend, roleCatalog, new PhantomPartyRouteCoordinator(null, null), new PhantomPartyTactics(null, _partyBackend), () -> ZERO, System::nanoTime, 64);
			PhantomAssertions.assertTrue(_coordinator.start(), "Rift Goal023B coordinator did not start.");
			_port = new L2jPhantomRiftPartyPort(_coordinator);
			_riftBackend = new CanonicalRiftBackend(_partyBackend, _leader, _invitee);
			final PhantomRiftCatalog catalog = PhantomRiftCatalog.load(context.moduleRoot().resolve("dist/game/data/DimensionalRift.xml"), _riftBackend);
			final PhantomRiftPolicy policy = PhantomRiftPolicy.load(context.moduleRoot().resolve("dist/game/data/phantoms/rift/high-five-rift-policy-v1.xml"), catalog, roleCatalog);
			final PhantomRiftReadinessService readiness = new PhantomRiftReadinessService(_riftBackend, catalog, policy, new PhantomPartyRoleMatcher(roleCatalog));
			_service = new PhantomRiftService(_riftBackend, catalog, policy, readiness, _store, _port, System::currentTimeMillis);
			_policyRegistration = _coordinator.installManagedInvitationPolicy(PhantomRiftService.GOAL_TYPE, _service::evaluateManagedInvitation);
		}

		private PendingInvitationReceipt advanceToInvitation()
		{
			for (int step = 0; step < 16; step++)
			{
				_service.advance(_leaderProfile.profileId(), GOAL_ID, 0, 1);
				final Preparation preparation = preparation();
				if ((preparation.stage() == Stage.OBSERVE_INVITE) && (preparation.invitationReceipt() != null))
				{
					_invitation = preparation.invitationReceipt();
					final var observation = observe(_invitation);
					PhantomAssertions.assertEquals(InviteStatus.PENDING, observation.status(), "Production Rift service did not create an exact pending canonical invitation.");
					PhantomAssertions.assertEquals(_invitation.sequence(), observation.sequence(), "Production Rift invitation sequence changed before policy pulse.");
					return _invitation;
				}
			}
			throw new AssertionError("Production Rift service did not reach OBSERVE_INVITE.");
		}

		private void advanceService(int count)
		{
			for (int step = 0; step < count; step++)
			{
				_service.advance(_leaderProfile.profileId(), GOAL_ID, 0, 1);
			}
		}

		private void pulse(int count)
		{
			for (int pulse = 0; pulse < count; pulse++)
			{
				_coordinator.onPulse();
			}
		}

		private PhantomRiftService.InviteObservation observe(PendingInvitationReceipt invitation)
		{
			return _port.observeInvite(_leaderProfile.profileId(), _invitee, invitation.sequence());
		}

		private Preparation preparation()
		{
			return _store.load(_leaderProfile.profileId()).orElseThrow().preparation();
		}

		private Player leaderPlayer()
		{
			return _leaderPlayer;
		}

		private Player inviteePlayer()
		{
			return _inviteePlayer;
		}

		private MemberRef invitee()
		{
			return _invitee;
		}

		private CanonicalPartyBackend partyBackend()
		{
			return _partyBackend;
		}

		private CanonicalRiftBackend riftBackend()
		{
			return _riftBackend;
		}

		@Override
		public void close() throws Exception
		{
			if (_invitation != null)
			{
				PartyInvitationService.getInstance().cancel(new InvitationIdentity(_invitation.sequence(), _invitation.requesterObjectId(), _invitation.inviteeObjectId()));
				pulse(4);
			}
			_policyRegistration.close();
			_coordinator.beginStop();
			_coordinator.finishStop();
			if (_leaderPlayer.isInParty())
			{
				PartyInvitationService.getInstance().leave(_leaderPlayer);
			}
			if (_inviteePlayer.isInParty())
			{
				PartyInvitationService.getInstance().leave(_inviteePlayer);
			}
			_materialization.shutdown();
			deleteProfile(_leaderProfile.profileId());
			deleteProfile(_inviteeProfile.profileId());
			_environment.assertClean(_environment.primary(), _leaderPlayer);
			_environment.assertClean(_environment.observer(), _inviteePlayer);
		}
	}

	private static final class MemoryRiftStore implements PhantomRiftPersistencePort
	{
		private final Map<Long, StoredPreparation> _values = new TreeMap<>();

		@Override
		public Optional<StoredPreparation> load(long profileId)
		{
			return Optional.ofNullable(_values.get(profileId));
		}

		@Override
		public StoredPreparation save(long profileId, long expectedRowVersion, Preparation preparation)
		{
			final StoredPreparation current = _values.get(profileId);
			if (((current == null) && (expectedRowVersion != -1)) || ((current != null) && (current.rowVersion() != expectedRowVersion)))
			{
				throw new IllegalStateException("Rift preparation conflict.");
			}
			final StoredPreparation saved = new StoredPreparation(profileId, current == null ? 0 : current.rowVersion() + 1, preparation);
			_values.put(profileId, saved);
			return saved;
		}
	}

	private static final class CanonicalRiftBackend implements PhantomRiftBackend
	{
		private final CanonicalPartyBackend _party;
		private final MemberRef _leader;
		private final MemberRef _invitee;
		private boolean _candidateAvailable = true;
		private boolean _negativeRelationship;

		private CanonicalRiftBackend(CanonicalPartyBackend party, MemberRef leader, MemberRef invitee)
		{
			_party = party;
			_leader = leader;
			_invitee = invitee;
		}

		private void setCandidateAvailable(boolean value)
		{
			_candidateAvailable = value;
		}

		private void setNegativeRelationship(boolean value)
		{
			_negativeRelationship = value;
		}

		@Override public Optional<MemberRef> currentMember(long profileId) { return _party.currentMember(profileId); }
		@Override public Optional<PartySnapshot> canonicalParty(MemberRef member) { return _party.observe(member); }
		@Override public Optional<MemberFacts> memberFacts(MemberRef member, Set<Integer> requestedItemIds)
		{
			final MemberSnapshot snapshot = _party.memberSnapshot(member).orElse(null);
			if (snapshot == null)
			{
				return Optional.empty();
			}
			final Player player = _party.player(member);
			final int partySize = (player == null) || (player.getParty() == null) ? 0 : player.getParty().getMemberCount();
			final String evidence = PhantomPartyModel.sha256(snapshot + "|" + partySize + "|rift023b");
			return Optional.of(new MemberFacts(snapshot, 80, List.of(new EquipmentFact(10001 + member.characterObjectId(), 1000, 1, "weapon", "S"), new EquipmentFact(20001 + member.characterObjectId(), 2000, 2, "armor", "S")), Map.of(7079, 100L), List.of(new ShotSupply(1463, 1000, false), new ShotSupply(3948, 1000, true)), 1000, 1, 1, partySize, evidence));
		}
		@Override public List<MemberFacts> nearbyCandidates(MemberRef observer, Set<Integer> requestedItemIds, int range, int limit) { return candidateFacts(observer, _invitee, requestedItemIds, range).stream().limit(limit).toList(); }
		@Override public Optional<MemberFacts> candidateFacts(MemberRef observer, MemberRef candidate, Set<Integer> requestedItemIds, int range)
		{
			if (!_candidateAvailable || !observer.equals(_leader) || !candidate.equals(_invitee))
			{
				return Optional.empty();
			}
			return memberFacts(candidate, requestedItemIds);
		}
		@Override public RelationshipEvidence relationship(long ownerProfileId, MemberRef candidate)
		{
			if (_negativeRelationship && (ownerProfileId == _invitee.profileId()) && candidate.equals(_leader))
			{
				return new RelationshipEvidence(-1500, PhantomPartyModel.sha256("rift023b.negative.relationship"), "social.negative", true);
			}
			return RelationshipEvidence.neutral("social.unavailable");
		}
		@Override public OptionalInt npcLevel(int npcId) { return OptionalInt.of(40); }
		@Override public EntryFacts entry(int type)
		{
			final Player leader = _party.player(_leader);
			return new EntryFacts(type, true, 7079, 18, 2, leader.getX(), leader.getY(), leader.getZ(), leader.getInstanceId(), Set.of(9), 0, 8, true, "goal023b.fixture");
		}
		@Override public ConfigFacts config() { return new ConfigFacts(4, 10000, 480, 600, 1.5f, Map.of(1, 18, 2, 21, 3, 24, 4, 27, 5, 30, 6, 33), "goal023b.fixture"); }
	}

	private static final class MemoryGoalStore implements PhantomGoalStore	{
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
		private final Map<MemberRef, List<MemberCapability>> _capabilities = new HashMap<>();

		private CanonicalPartyBackend(Map<Long, MemberRef> members, Map<MemberRef, Player> players, Map<MemberRef, List<MemberCapability>> capabilities)
		{
			_members = members;
			_players = players;
			capabilities.forEach((member, values) -> _capabilities.put(member, List.copyOf(values)));
		}

		private void setCapabilities(MemberRef member, List<MemberCapability> capabilities)
		{
			_capabilities.put(member, List.copyOf(capabilities));
		}

		private Player player(MemberRef member)
		{
			return _players.get(member);
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
			final List<MemberCapability> capabilities = _capabilities.getOrDefault(member, List.of());
			return player == null ? Optional.empty() : Optional.of(new MemberSnapshot(member, player.getActiveClass(), player.getInstanceId(), player.getX(), player.getY(), player.getZ(), 100, 100, 100, player.isDead(), false, false, false, 0, List.of(), capabilities, PhantomPartyModel.sha256(member.stableKey() + "|" + capabilities + "|" + player.isDead())));
		}
		@Override public List<PhantomPartyModel.MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId) { return _capabilities.getOrDefault(actor, List.of()); }
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

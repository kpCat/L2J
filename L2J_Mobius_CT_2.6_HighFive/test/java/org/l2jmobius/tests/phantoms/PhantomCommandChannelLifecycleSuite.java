/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.CommandChannel;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.CancelOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.DismissOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InviteOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.RespondOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.Response;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;

/**
 * Focused canonical MPCC lifecycle, packet parity and negative-scope tests.
 */
public final class PhantomCommandChannelLifecycleSuite implements PhantomTestSuite
{
	private static final int STRATEGY_GUIDE_ID = 8871;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final CommandChannelInvitationService _service = CommandChannelInvitationService.getInstance();
	private Path _moduleRoot;
	private Player _requester;
	private Player _invitee;
	private Player.OutboundSessionAttachment _requesterOutput;
	private Player.OutboundSessionAttachment _inviteeOutput;

	@Override
	public String id()
	{
		return "command-channel-lifecycle";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		_moduleRoot = context.moduleRoot();
		_environment.initialize(context);
		_requester = Player.load(_environment.primary().objectId());
		_invitee = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_requester != null) && (_invitee != null), "CP2 fixture Players did not load.");
		_requesterOutput = _requester.attachOutboundSession(new HeadlessPlayerOutboundSession(16, 128, 128));
		_inviteeOutput = _invitee.attachOutboundSession(new HeadlessPlayerOutboundSession(16, 128, 128));
		_requester.spawnMe();
		_invitee.spawnMe();
		context.record("commandChannel.database", PhantomTestDatabaseGuard.TARGET_DATABASE);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		resetPlayers();
		_requesterOutput.close();
		_inviteeOutput.close();
		_environment.cleanupLoadedPlayer(_requester);
		_environment.cleanupLoadedPlayer(_invitee);
		_environment.assertClean(_environment.primary(), _requester);
		_environment.assertClean(_environment.observer(), _invitee);
		_environment.shutdown();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-service-packet-parity-and-negative-scope", _ -> testStaticParity());
		registry.add("02-exact-validation-and-formation-authority", _ -> testValidation());
		registry.add("03-exact-refuse-stale-and-retry-ownership", _ -> testRefuseAndStale());
		registry.add("04-canonical-create-and-existing-channel-accept", _ -> testAccept());
		registry.add("05-party-drift-and-player-timeout-fail-closed", _ -> testDriftAndExpiry());
		registry.add("06-exact-leader-dismiss-and-canonical-disband", _ -> testDismiss());
		registry.add("07-exact-cancel-is-stale-safe", _ -> testCancel());
	}

	private void testStaticParity() throws Exception
	{
		final String service = source("java/org/l2jmobius/gameserver/model/groups/CommandChannelInvitationService.java");
		PhantomAssertions.assertTrue(service.contains("requester.isClanLeader()") && service.contains("requester.getClan().getLevel() >= 5"), "Clan leader level-5 formation right drifted.");
		PhantomAssertions.assertTrue(service.contains("getItemByItemId(8871)"), "Strategy Guide formation right drifted.");
		PhantomAssertions.assertTrue(service.contains("requester.getPledgeClass() >= 5") && service.contains("requester.getKnownSkill(391)"), "Pledge/Clan Imperium formation right drifted.");
		PhantomAssertions.assertTrue(service.contains("_pendingByInvitee") && service.contains("_pendingByRequester") && service.contains("InvitationIdentity(++_nextSequence"), "Exact bounded invitation ownership is absent.");
		PhantomAssertions.assertFalse(service.contains("World.getInstance()") || service.contains("ThreadPool") || service.contains("ScheduledFuture"), "Generic MPCC service gained discovery or timer ownership.");
		for (String packet : new String[]
		{
			"RequestExAskJoinMPCC.java",
			"RequestExAcceptJoinMPCC.java",
			"RequestExOustFromMPCC.java"
		})
		{
			final String source = source("java/org/l2jmobius/gameserver/network/clientpackets/" + packet);
			PhantomAssertions.assertTrue(source.contains("CommandChannelInvitationService"), packet + " does not delegate the shared lifecycle.");
			PhantomAssertions.assertFalse(source.contains("new CommandChannel") || source.contains(".addParty(") || source.contains(".removeParty("), packet + " retained MPCC mutation ownership.");
		}
		final String backend = source("java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java");
		PhantomAssertions.assertTrue(backend.contains("inviteCommandChannel") && backend.contains("respondCommandChannel") && backend.contains("dismissCommandChannel"), "Goal017 exact MPCC seam is incomplete.");
		PhantomAssertions.assertFalse(backend.contains("new CommandChannel(") || backend.contains(".setCommandChannel(") || backend.contains("World.getPlayers()"), "Phantom backend gained a second CC engine or global discovery.");
	}

	private void testValidation()
	{
		resetPlayers();
		PhantomAssertions.assertEquals(InviteOutcome.REQUESTER_NOT_IN_PARTY, _service.invite(_requester, _invitee).outcome(), "Requester without Party was accepted.");
		final PartyPair pair = parties();
		PhantomAssertions.assertEquals(InviteOutcome.FORMATION_AUTHORITY_REQUIRED, _service.invite(_requester, _invitee).outcome(), "Requester without exact formation right was accepted.");
		ensureFormationItem();
		_invitee.onTransactionRequest(_invitee);
		PhantomAssertions.assertEquals(InviteOutcome.TARGET_BUSY, _service.invite(_requester, _invitee).outcome(), "Busy target Party leader was accepted.");
		_invitee.setActiveRequester(null);
		_invitee.onTransactionResponse();
		new CommandChannel(_invitee);
		PhantomAssertions.assertEquals(InviteOutcome.TARGET_ALREADY_IN_COMMAND_CHANNEL, _service.invite(_requester, _invitee).outcome(), "Target Party already in CC was accepted.");
		pair.invitee().getCommandChannel().disbandChannel();
		final CommandChannel requesterChannel = new CommandChannel(_requester);
		requesterChannel.setLeader(_invitee);
		PhantomAssertions.assertEquals(InviteOutcome.REQUESTER_NOT_COMMAND_CHANNEL_LEADER, _service.invite(_requester, _invitee).outcome(), "Non-CC-leader requester was accepted.");
		resetPlayers();
		final Party inviteeLed = new Party(_invitee, PartyDistributionType.FINDERS_KEEPERS);
		_requester.setParty(inviteeLed);
		PhantomAssertions.assertEquals(InviteOutcome.REQUESTER_NOT_PARTY_LEADER, _service.invite(_requester, _invitee).outcome(), "Non-Party-leader requester was accepted.");
		resetPlayers();
		final Party requesterParty = new Party(_requester, PartyDistributionType.FINDERS_KEEPERS);
		_requester.setParty(requesterParty);
		PhantomAssertions.assertEquals(InviteOutcome.TARGET_NOT_IN_PARTY, _service.invite(_requester, _invitee).outcome(), "Target without Party was accepted.");
		_invitee.setParty(requesterParty);
		PhantomAssertions.assertEquals(InviteOutcome.SAME_PARTY, _service.invite(_requester, _invitee).outcome(), "Same-Party target was accepted.");
		resetPlayers();
	}

	private void testRefuseAndStale()
	{
		resetPlayers();
		parties();
		ensureFormationItem();
		final InviteResult first = _service.invite(_requester, _invitee);
		PhantomAssertions.assertTrue(first.delivered(), "Eligible exact leader invitation was not delivered.");
		PhantomAssertions.assertEquals(_requester, _invitee.getActiveRequester(), "Player activeRequester authority was not installed.");
		PhantomAssertions.assertEquals(_invitee.getObjectId(), first.identity().inviteeObjectId(), "Invitation identity did not resolve the exact target Party leader.");
		final InvitationIdentity wrong = new InvitationIdentity(first.identity().sequence() + 1, first.identity().requesterObjectId(), first.identity().inviteeObjectId());
		PhantomAssertions.assertEquals(RespondOutcome.STALE_INVITE, _service.respond(_invitee, Response.ACCEPT, wrong).outcome(), "Wrong identity did not fail closed.");
		PhantomAssertions.assertEquals(first.identity(), _service.observe(_invitee).orElseThrow().identity(), "Wrong identity cleared the matching pending request.");
		PhantomAssertions.assertEquals(RespondOutcome.REFUSED, _service.respond(_invitee, Response.REFUSE, first.identity()).outcome(), "Exact REFUSE did not terminate the request.");
		PhantomAssertions.assertEquals(null, _invitee.getActiveRequester(), "REFUSE retained Player request authority.");
		PhantomAssertions.assertFalse(_requester.getParty().isInCommandChannel(), "REFUSE mutated CommandChannel state.");
		final InviteResult retry = _service.invite(_requester, _invitee);
		PhantomAssertions.assertTrue(retry.delivered() && (retry.identity().sequence() > first.identity().sequence()), "Retry did not receive a newer exact identity.");
		PhantomAssertions.assertEquals(RespondOutcome.STALE_INVITE, _service.respond(_invitee, Response.REFUSE, first.identity()).outcome(), "Old identity affected a newer request.");
		PhantomAssertions.assertEquals(retry.identity(), _service.observe(_invitee).orElseThrow().identity(), "Old terminal response cleared a newer pending request.");
		PhantomAssertions.assertEquals(RespondOutcome.REFUSED, _service.respond(_invitee, Response.REFUSE, retry.identity()).outcome(), "Retry REFUSE failed.");
		resetPlayers();
	}

	private void testAccept()
	{
		resetPlayers();
		PartyPair pair = parties();
		ensureFormationItem();
		InviteResult invite = _service.invite(_requester, _invitee);
		final var created = _service.respond(_invitee, Response.ACCEPT, invite.identity());
		PhantomAssertions.assertTrue(created.accepted() && created.createdCommandChannel(), "ACCEPT did not create the canonical CommandChannel.");
		PhantomAssertions.assertTrue((pair.requester().getCommandChannel() != null) && (pair.requester().getCommandChannel() == pair.invitee().getCommandChannel()), "Accepted Parties do not share one canonical CommandChannel.");
		resetPlayers();
		pair = parties();
		ensureFormationItem();
		final CommandChannel existing = new CommandChannel(_requester);
		invite = _service.invite(_requester, _invitee);
		final var joined = _service.respond(_invitee, Response.ACCEPT, invite.identity());
		PhantomAssertions.assertTrue(joined.accepted() && !joined.createdCommandChannel(), "Existing requester-led CC was replaced.");
		PhantomAssertions.assertTrue((pair.requester().getCommandChannel() == existing) && (pair.invitee().getCommandChannel() == existing), "Target Party did not join the existing canonical CC.");
		resetPlayers();
	}

	private void testDriftAndExpiry() throws Exception
	{
		resetPlayers();
		parties();
		ensureFormationItem();
		InviteResult invite = _service.invite(_requester, _invitee);
		_invitee.setParty(new Party(_invitee, PartyDistributionType.FINDERS_KEEPERS));
		PhantomAssertions.assertEquals(RespondOutcome.REVALIDATION_FAILED, _service.respond(_invitee, Response.ACCEPT, invite.identity()).outcome(), "Party identity drift did not fail closed.");
		PhantomAssertions.assertFalse(_requester.getParty().isInCommandChannel(), "Drifted ACCEPT mutated a CommandChannel.");
		resetPlayers();
		parties();
		ensureFormationItem();
		invite = _service.invite(_requester, _invitee);
		expireRequester(_requester);
		PhantomAssertions.assertEquals(RespondOutcome.EXPIRED, _service.respond(_invitee, Response.ACCEPT, invite.identity()).outcome(), "Expired Player request was accepted.");
		PhantomAssertions.assertFalse(_service.observe(_invitee).isPresent(), "Expired exact pending state was retained.");
		resetPlayers();
	}

	private void testDismiss()
	{
		resetPlayers();
		final PartyPair pair = parties();
		ensureFormationItem();
		final InviteResult invite = _service.invite(_requester, _invitee);
		PhantomAssertions.assertTrue(_service.respond(_invitee, Response.ACCEPT, invite.identity()).accepted(), "Dismiss fixture CC was not formed.");
		PhantomAssertions.assertEquals(DismissOutcome.OWN_PARTY, _service.dismiss(_requester, _requester), "Own-Party dismissal loophole remained open.");
		PhantomAssertions.assertEquals(DismissOutcome.REQUESTER_NOT_COMMAND_CHANNEL_LEADER, _service.dismiss(_invitee, _requester), "Non-CC-leader dismissal was accepted.");
		PhantomAssertions.assertEquals(DismissOutcome.COMPLETED, _service.dismiss(_requester, _invitee), "Exact CC-leader dismissal failed.");
		PhantomAssertions.assertFalse(pair.requester().isInCommandChannel() || pair.invitee().isInCommandChannel(), "Canonical less-than-two Party disband did not run.");
		resetPlayers();
	}

	private void testCancel()
	{
		resetPlayers();
		parties();
		ensureFormationItem();
		final InviteResult first = _service.invite(_requester, _invitee);
		final InvitationIdentity wrong = new InvitationIdentity(first.identity().sequence() + 1, first.identity().requesterObjectId(), first.identity().inviteeObjectId());
		PhantomAssertions.assertEquals(CancelOutcome.STALE_INVITE, _service.cancel(wrong).outcome(), "Stale cancel cleared a matching pending request.");
		PhantomAssertions.assertEquals(first.identity(), _service.observe(_invitee).orElseThrow().identity(), "Stale cancel changed the exact pending identity.");
		PhantomAssertions.assertEquals(CancelOutcome.CANCELLED, _service.cancel(first.identity()).outcome(), "Exact cleanup cancel did not clear the request.");
		PhantomAssertions.assertFalse(_service.observe(_invitee).isPresent(), "Exact cancel retained pending state.");
		PhantomAssertions.assertEquals(null, _invitee.getActiveRequester(), "Exact cancel retained Player request authority.");

		final InviteResult newer = _service.invite(_requester, _invitee);
		PhantomAssertions.assertTrue(newer.delivered() && (newer.identity().sequence() > first.identity().sequence()), "Cancel retry did not receive a newer identity.");
		PhantomAssertions.assertEquals(CancelOutcome.STALE_INVITE, _service.cancel(first.identity()).outcome(), "Old cancel cleared a newer invitation.");
		PhantomAssertions.assertEquals(newer.identity(), _service.observe(_invitee).orElseThrow().identity(), "Old cancel changed newer pending state.");
		PhantomAssertions.assertEquals(CancelOutcome.CANCELLED, _service.cancel(newer.identity()).outcome(), "Newer exact cancel failed.");
		resetPlayers();
	}

	private PartyPair parties()
	{
		final Party requesterParty = new Party(_requester, PartyDistributionType.FINDERS_KEEPERS);
		final Party inviteeParty = new Party(_invitee, PartyDistributionType.FINDERS_KEEPERS);
		_requester.setParty(requesterParty);
		_invitee.setParty(inviteeParty);
		return new PartyPair(requesterParty, inviteeParty);
	}

	private void ensureFormationItem()
	{
		if (_requester.getInventory().getItemByItemId(STRATEGY_GUIDE_ID) == null)
		{
			PhantomAssertions.assertTrue(_requester.getInventory().addItem(ItemProcessType.REWARD, STRATEGY_GUIDE_ID, 1, _requester, this) != null, "Could not create test-owned Strategy Guide.");
		}
	}

	private String source(String relative) throws Exception
	{
		return Files.readString(_moduleRoot.resolve(relative));
	}

	private static void expireRequester(Player requester) throws Exception
	{
		final Field expiry = Player.class.getDeclaredField("_requestExpireTime");
		expiry.setAccessible(true);
		expiry.setLong(requester, 0);
	}

	private void resetPlayers()
	{
		_service.observe(_requester).ifPresent(snapshot -> _service.respond(_requester, Response.REFUSE, snapshot.identity()));
		_service.observe(_invitee).ifPresent(snapshot -> _service.respond(_invitee, Response.REFUSE, snapshot.identity()));
		final CommandChannel requesterChannel = _requester.getParty() == null ? null : _requester.getParty().getCommandChannel();
		final CommandChannel inviteeChannel = _invitee.getParty() == null ? null : _invitee.getParty().getCommandChannel();
		if (requesterChannel != null)
		{
			requesterChannel.disbandChannel();
		}
		if ((inviteeChannel != null) && (inviteeChannel != requesterChannel))
		{
			inviteeChannel.disbandChannel();
		}
		_requester.setActiveRequester(null);
		_invitee.setActiveRequester(null);
		_requester.onTransactionResponse();
		_invitee.onTransactionResponse();
		_requester.setParty(null);
		_invitee.setParty(null);
	}

	private record PartyPair(Party requester, Party invitee)
	{
	}
}

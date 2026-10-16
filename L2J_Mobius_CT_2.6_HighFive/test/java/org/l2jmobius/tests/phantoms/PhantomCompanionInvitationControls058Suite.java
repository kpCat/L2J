/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.List;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;

/** Native capacity/identity controls on ten owned TEST Players; no real login claim. */
public final class PhantomCompanionInvitationControls058Suite implements PhantomTestSuite
{
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private final List<PhantomHeadlessPlayerFixture> _fixtures = new ArrayList<>();
	private final List<Player> _players = new ArrayList<>();
	private final List<Player.OutboundSessionAttachment> _outputs = new ArrayList<>();

	@Override public String id() { return "companion-invitation-controls058"; }

	@Override public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(58005827L, context.seed(), "Exact invitation fixture seed required.");
		PhantomCombatServerIntegrationSuite.assertCampaign058Jar(context);
		_environment.initialize(context);
		_fixtures.add(_environment.primary());
		_fixtures.add(_environment.observer());
		for (int slot = 1; slot <= 8; slot++) { _fixtures.add(_environment.createCampaign058InvitationMember(context, slot)); }
		for (var fixture : _fixtures)
		{
			final Player player = Player.load(fixture.objectId());
			PhantomAssertions.assertTrue(player != null && !player.isGM(), "Native ordinary invitation fixture did not load.");
			_players.add(player);
			_outputs.add(player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 64, 64)));
			player.spawnMe();
		}
		context.record("invitation058.input", "10 exact058t ordinary native TEST Players; native output sinks, not actual client logins; canonical refusal/expiry/stale/replay/foreign caller and full9 capacity; no product rule changes");
	}

	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-native-refusal-expiry-replay-stale-and-foreign-caller", context ->
		{
			final var service = PartyInvitationService.getInstance();
			final Player leader = _players.get(0);
			final Player target = _players.get(1);
			final var first = service.invite(leader, target, PartyDistributionType.FINDERS_KEEPERS.getId());
			PhantomAssertions.assertTrue(first.delivered(), "Initial native invitation was not delivered.");
			PhantomAssertions.assertEquals(PartyInvitationService.RespondOutcome.NO_PENDING_INVITE, service.respond(leader, PartyInvitationService.Response.ACCEPT, first.identity()).outcome(), "Foreign caller responded for the invitee.");
			PhantomAssertions.assertEquals(first.identity(), service.observe(target).orElseThrow().identity(), "Foreign caller consumed the real pending invitation.");
			PhantomAssertions.assertEquals(PartyInvitationService.RespondOutcome.REFUSED, service.respond(target, PartyInvitationService.Response.REFUSE, first.identity()).outcome(), "Native refusal failed.");
			PhantomAssertions.assertEquals(PartyInvitationService.RespondOutcome.NO_PENDING_INVITE, service.respond(target, PartyInvitationService.Response.ACCEPT, first.identity()).outcome(), "Terminal replay created membership.");
			final var second = service.invite(leader, target, PartyDistributionType.FINDERS_KEEPERS.getId());
			PhantomAssertions.assertTrue(second.delivered(), "Fresh invitation after refusal was unavailable.");
			PhantomAssertions.assertEquals(PartyInvitationService.RespondOutcome.STALE_INVITE, service.respond(target, PartyInvitationService.Response.ACCEPT, first.identity()).outcome(), "Stale identity consumed the fresh invitation.");
			PhantomAssertions.assertEquals(second.identity(), service.observe(target).orElseThrow().identity(), "Stale response mutated pending identity.");
			PhantomPartyServerIntegrationSuite.expireRequester(leader); // Existing exact native TEST expiry precondition.
			PhantomAssertions.assertEquals(PartyInvitationService.RespondOutcome.EXPIRED, service.respond(target, PartyInvitationService.Response.ACCEPT, second.identity()).outcome(), "Native expired request was accepted.");
			PhantomAssertions.assertTrue(service.observe(target).isEmpty() && !leader.isInParty() && !target.isInParty(), "Negative response changed membership or retained pending state.");
			context.record("invitation058.negative", "REFUSED; terminal replay NO_PENDING; foreign caller NO_PENDING with exact pending retained; stale identity STALE_INVITE with fresh pending retained; expired EXPIRED; no membership");
		});
		registry.add("02-nine-native-members-reject-tenth-without-side-effects", context ->
		{
			final var service = PartyInvitationService.getInstance();
			final Player leader = _players.getFirst();
			for (int index = 1; index < 9; index++)
			{
				final Player target = _players.get(index);
				final var invite = service.invite(leader, target, PartyDistributionType.FINDERS_KEEPERS.getId());
				PhantomAssertions.assertTrue(invite.delivered(), "Native roster invitation failed at " + index);
				PhantomAssertions.assertEquals(PartyInvitationService.RespondOutcome.ACCEPTED, service.respond(target, PartyInvitationService.Response.ACCEPT, invite.identity()).outcome(), "Canonical roster acceptance failed at " + index);
			}
			final var before = leader.getParty().getMembers().stream().map(Player::getObjectId).sorted().toList();
			PhantomAssertions.assertEquals(9, before.size(), "Native stock Party did not contain nine actual Players.");
			final Player tenth = _players.get(9);
			PhantomAssertions.assertEquals(PartyInvitationService.InviteOutcome.PARTY_FULL, service.invite(leader, tenth, PartyDistributionType.FINDERS_KEEPERS.getId()).outcome(), "Native full Party admitted the tenth actor.");
			PhantomAssertions.assertTrue(!tenth.isInParty() && service.observe(tenth).isEmpty(), "Full-party rejection created pending state or membership.");
			PhantomAssertions.assertEquals(before, leader.getParty().getMembers().stream().map(Player::getObjectId).sorted().toList(), "Full-party rejection changed the native roster.");
			context.record("invitation058.full", "actual native roster=" + before + "; tenth=" + tenth.getObjectId() + "; PARTY_FULL, no pending/member side effects");
		});
	}

	@Override public void afterAll(PhantomTestContext context) throws Exception
	{
		try
		{
			for (Player player : _players) { PartyInvitationService.getInstance().observe(player).ifPresent(pending -> PartyInvitationService.getInstance().cancel(pending.identity())); PartyInvitationService.getInstance().leave(player); }
			for (var output : _outputs) { output.close(); }
			for (int index = 0; index < _players.size(); index++) { _environment.cleanupLoadedPlayer(_players.get(index)); _environment.assertClean(_fixtures.get(index), _players.get(index)); }
		}
		finally { _environment.shutdown(); }
	}
}

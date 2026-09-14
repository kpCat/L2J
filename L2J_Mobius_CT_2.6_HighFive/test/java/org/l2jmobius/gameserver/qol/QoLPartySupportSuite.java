/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.qol;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.config.custom.CommunityBoardConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig;
import org.l2jmobius.gameserver.config.custom.PersonalCharacterQoLConfig.Settings;
import org.l2jmobius.gameserver.handler.CommunityBoardHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyMessageType;
import org.l2jmobius.gameserver.model.sevensigns.SevenSigns;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;
import org.l2jmobius.gameserver.phantoms.player.PhantomActionFacade;
import org.l2jmobius.gameserver.qol.PersonalPartySupportService.Action;
import org.l2jmobius.gameserver.qol.PersonalPartySupportService.Status;
import org.l2jmobius.gameserver.scripting.ScriptEngine;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomHeadlessPlayerTestEnvironment;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** L2-QOL-006 bounded party support and native mobility/transport coverage. */
public final class QoLPartySupportSuite implements PhantomTestSuite
{
	private static final long SEED = 1006001L;
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private Settings _previousSettings;
	private CommunitySettings _communitySettings;
	private Player _player;
	private Player _observer;
	private Party _party;
	private Path _fixtures;

	@Override
	public String id()
	{
		return "qol-party-support";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "L2-QOL-006 suite used the wrong deterministic seed.");
		_environment.initialize(context);
		_player = Player.load(_environment.primary().objectId());
		_observer = Player.load(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_player != null) && (_observer != null), "L2-QOL-006 fixture Players did not load.");
		World.getInstance().addObject(_player);
		World.getInstance().addObject(_observer);
		PhantomAssertions.assertTrue((World.getInstance().getPlayer(_player.getObjectId()) == _player) && (World.getInstance().getPlayer(_observer.getObjectId()) == _observer), "L2-QOL-006 fixtures did not enter canonical World online ownership.");
		_previousSettings = install(allowlisted());
		_communitySettings = CommunitySettings.capture();
		_communitySettings.enableForTest();
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		PhantomAssertions.assertTrue(CommunityBoardHandler.getInstance().getHandler("_bbsqol") != null, "Registered MasterHandler did not expose _bbsqol.");
		_fixtures = context.reportsDirectory().resolve("qol-party-support-fixtures");
		Files.createDirectories(_fixtures);
		context.record("qol006.database", "l2jmobiush5_phantom_test");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-shipped-off-backward-compatible-real-player-only", this::configAndIdentity);
		registry.add("02-native-pm-invite-and-summon-friend-no-op-census", this::nativeTransportCensus);
		registry.add("03-board-heal-boundaries-and-unrelated-state", this::healBoundaries);
		registry.add("04-resurrection-stale-membership-and-native-primitive", this::resurrectionBoundaries);
		registry.add("05-reputation-cleanup-and-narrow-karma-board-access", this::reputationBoundaries);
		registry.add("06-source-scope-and-gate-wiring", this::sourceScopeAndWiring);
	}

	private void configAndIdentity(PhantomTestContext context) throws Exception
	{
		final Settings shipped = PersonalCharacterQoLConfig.read(Path.of("config/Custom/PersonalCharacterQoL.ini"));
		PhantomAssertions.assertTrue(shipped.valid() && !shipped.enabled() && !shipped.partySupportEnabled(), "Personal party support is not shipped OFF.");
		final Settings legacy = PersonalCharacterQoLConfig.read(write("legacy-qol005.ini", config("True", null, Integer.toString(_player.getObjectId()), "")));
		PhantomAssertions.assertTrue(legacy.valid() && legacy.enabled() && !legacy.partySupportEnabled(), "Legacy Personal QoL config did not retain an OFF party-support default.");
		final Settings malformed = PersonalCharacterQoLConfig.read(write("invalid-party-support-switch.ini", config("True", "sometimes", Integer.toString(_player.getObjectId()), "")));
		PhantomAssertions.assertFalse(malformed.valid() || malformed.enabled(), "Malformed party-support switch did not fail closed.");

		final PersonalCharacterQoLService access = PersonalCharacterQoLService.getInstance();
		final PersonalPartySupportService support = PersonalPartySupportService.getInstance();
		install(allowlisted());
		PhantomAssertions.assertTrue(access.isPartySupportEnabled(_player) && support.isEnabled(_player), "Allowlisted real Player was denied party support.");
		PhantomAssertions.assertFalse(access.isPartySupportEnabled(_observer) || support.isEnabled(_observer), "Non-allowlisted real Player received party support.");
		install(new Settings(true, false, false, false, true, Set.of(), Set.of(_player.getAccountName().toLowerCase(Locale.ROOT)), true, "Account allowlist test."));
		PhantomAssertions.assertTrue(access.isPartySupportEnabled(_player), "Case-normalized account allowlist was denied.");
		try (Player.OutboundSessionAttachment ignored = _player.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 32)))
		{
			PhantomAssertions.assertFalse(access.isPartySupportEnabled(_player), "Headless Phantom received privileged party-support authority.");
			PhantomAssertions.assertEquals(Status.FEATURE_DISABLED, support.execute(Action.HEAL, _player, _player.getObjectId()).status(), "Headless Phantom reached a party-support mutation.");
		}
		install(allowlisted());
		context.record("qol006.identity", "shippedOff=true;legacyDefault=false;realAllowlisted=true;ordinary/headless=false");
	}

	private void nativeTransportCensus(PhantomTestContext context) throws Exception
	{
		final String say = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/Say2.java");
		final String whisper = source(context, "dist/game/data/scripts/handlers/chat/channels/ChatWhisper.java");
		final String invite = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java");
		final String inviteAnswer = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinParty.java");
		final String invitationService = source(context, "java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java");
		final String summonSkill = source(context, "dist/game/data/stats/skills/01400-01499.xml");
		final String callPc = source(context, "dist/game/data/scripts/handlers/skill/effects/CallPc.java");
		final String callCondition = source(context, "java/org/l2jmobius/gameserver/model/conditions/ConditionPlayerCallPc.java");
		final String summonAnswer = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/DlgAnswer.java");

		assertContains(say, "new OnPlayerChat", "Native PM event/filter hook is absent.");
		assertContains(say, "ChatHandler.getInstance().getHandler(chatType)", "Native chat dispatch owner is absent.");
		assertContains(say, "_text.length() > 105", "Native chat message-length policy is absent.");
		assertContains(whisper, "World.getInstance().getPlayer(target)", "Native whisper no longer resolves remote names through World.");
		assertContains(whisper, "BlockList.isBlocked(receiver, activeChar)", "Native whisper block-list policy is absent.");
		assertContains(invite, "PartyInvitationService.getInstance().invite(requestor, World.getInstance().getPlayer(_name)", "Exact-name remote invite no longer delegates to PartyInvitationService.");
		assertContains(inviteAnswer, "service.respond(player, _response", "Native invite accept/refuse response owner is absent.");
		assertContains(invitationService, "invitee.joinParty(party);", "Party insertion moved outside canonical accepted-invitation ownership.");
		assertContains(summonSkill, "<target myPartyExceptMe=\"true\" />", "Summon Friend is no longer own-party-only.");
		assertContains(summonSkill, "<effect name=\"CallPc\">", "Summon Friend no longer uses CallPc.");
		assertContains(callPc, "checkSummonTargetStatus(target, player)", "CallPc target safety owner is absent.");
		assertContains(callPc, "new SummonRequestHolder(player)", "CallPc confirmation/location holder is absent.");
		assertContains(callCondition, "player.isInsideZone(ZoneId.NO_SUMMON_FRIEND)", "CallPc source-zone restriction is absent.");
		assertContains(callPc, "PersonalCharacterQoLService.getInstance().isSevenSigns", "QOL-005 Seven Signs CallPc compatibility is absent.");
		assertContains(summonAnswer, "player.teleToLocation(holder.getLocation(), true)", "Confirmed Summon Friend no longer uses the actor-owned holder location.");
		PhantomAssertions.assertFalse(say.contains("PersonalPartySupportService") || whisper.contains("PersonalPartySupportService") || invite.contains("PersonalPartySupportService") || callPc.contains("PersonalPartySupportService"), "QOL-006 duplicated or intercepted a native transport.");
		context.record("qol006.classification", "remotePM=NO_CHANGE_REQUIRED_ALREADY_NATIVE;remoteInvite=NO_CHANGE_REQUIRED_ALREADY_NATIVE;selfOnlySummonFriend=NO_CHANGE_REQUIRED_ALREADY_NATIVE;healResReputation=CHANGE_REQUIRED");
	}

	private void healBoundaries(PhantomTestContext context)
	{
		install(allowlisted());
		final double outsiderHp = Math.max(1, _observer.getMaxHp() / 3.0);
		_observer.setCurrentHp(outsiderHp);
		board("_bbsqol;party;heal;" + _observer.getObjectId());
		assertCurrent(_observer.getCurrentHp(), outsiderHp, "Outsider was healed.");
		boardAs(_observer, "_bbsqol;party;heal;" + _observer.getObjectId());
		assertCurrent(_observer.getCurrentHp(), outsiderHp, "Non-allowlisted actor healed itself.");

		final double selfHp = Math.max(1, _player.getMaxHp() / 3.0);
		_player.setCurrentHp(selfHp);
		install(Settings.disabled("Feature OFF test."));
		board("_bbsqol;party;heal;" + _player.getObjectId());
		assertCurrent(_player.getCurrentHp(), selfHp, "Feature OFF changed the actor.");
		install(allowlisted());
		board("_bbsqol;party;heal;not-a-number");
		board("_bbsqol;party;unknown;" + _player.getObjectId());
		board("_bbsqol;party;heal;2147483647");
		assertCurrent(_player.getCurrentHp(), selfHp, "Malformed/unknown/forged board action changed the actor.");

		createParty();
		_observer.setCurrentHp(Math.max(1, _observer.getMaxHp() / 4.0));
		_observer.setCurrentMp(Math.max(1, _observer.getMaxMp() / 4.0));
		_observer.setCurrentCp(Math.max(1, _observer.getMaxCp() / 4.0));
		final StateSnapshot before = snapshot(_observer);
		board("_bbsqol;party;heal;" + _observer.getObjectId());
		assertCurrent(_observer.getCurrentHp(), _observer.getMaxHp(), "Own-party HP was not restored to maximum.");
		assertCurrent(_observer.getCurrentMp(), _observer.getMaxMp(), "Own-party MP was not restored to maximum.");
		assertCurrent(_observer.getCurrentCp(), _observer.getMaxCp(), "Own-party CP was not restored to maximum.");
		assertUnrelated(before, _observer, "heal");

		_observer.setOnEvent(true);
		final double restrictedHp = Math.max(1, _observer.getMaxHp() / 5.0);
		_observer.setCurrentHp(restrictedHp);
		board("_bbsqol;party;heal;" + _observer.getObjectId());
		assertCurrent(_observer.getCurrentHp(), restrictedHp, "Event target bypassed the support restriction.");
		_observer.setOnEvent(false);
		context.record("qol006.heal", "self+ownParty=bounded;outsider/off/malformed/forged/event=noMutation;hpMpCp=max;unrelated=stable");
	}

	private void resurrectionBoundaries(PhantomTestContext context)
	{
		install(allowlisted());
		createParty();
		final long expBefore = _observer.getExp();
		_observer.setCurrentHp(0);
		_observer.setDead(true);
		board("_bbsqol;party;resurrect;" + _observer.getObjectId());
		PhantomAssertions.assertFalse(_observer.isDead(), "Dead own-party Player was not revived.");
		PhantomAssertions.assertEquals(expBefore, _observer.getExp(), "Party resurrection fabricated XP restoration.");
		final double aliveHp = _observer.getCurrentHp();
		board("_bbsqol;party;resurrect;" + _observer.getObjectId());
		assertCurrent(_observer.getCurrentHp(), aliveHp, "Alive-target resurrection was not a safe rejection.");

		final int staleObjectId = _observer.getObjectId();
		_party.removePartyMember(_observer, PartyMessageType.LEFT);
		_party = null;
		_observer.setCurrentHp(0);
		_observer.setDead(true);
		board("_bbsqol;party;resurrect;" + staleObjectId);
		PhantomAssertions.assertTrue(_observer.isDead(), "A stale former party member was resurrected.");
		_observer.setDead(false);
		_observer.fullRestore();
		context.record("qol006.resurrection", "canonicalDoRevive=true;xpRestore=false;alive/stale=noMutation");
	}

	private void reputationBoundaries(PhantomTestContext context)
	{
		install(allowlisted());
		createParty();
		_observer.setKarma(250);
		final StateSnapshot before = snapshot(_observer);
		board("_bbsqol;party;reputation;" + _observer.getObjectId());
		PhantomAssertions.assertEquals(0, _observer.getKarma(), "Own-party karma was not normalized to zero.");
		assertUnrelated(before, _observer, "reputation cleanup");
		final StateSnapshot zero = snapshot(_observer);
		board("_bbsqol;party;reputation;" + _observer.getObjectId());
		PhantomAssertions.assertEquals(0, _observer.getKarma(), "Zero karma was changed.");
		assertUnrelated(zero, _observer, "zero-karma cleanup");

		_player.setKarma(200);
		final double karmaHp = Math.max(1, _player.getMaxHp() / 4.0);
		_player.setCurrentHp(karmaHp);
		board("_bbsqol;party;heal;" + _player.getObjectId());
		assertCurrent(_player.getCurrentHp(), karmaHp, "Karma exception widened to a heal action.");
		board("_bbsqol");
		PhantomAssertions.assertEquals(200, _player.getKarma(), "Read-only board navigation mutated karma.");
		board("_bbsqol;party;reputation;" + _player.getObjectId());
		PhantomAssertions.assertEquals(0, _player.getKarma(), "Narrow self-karma cleanup route was blocked by the generic board gate.");
		context.record("qol006.reputation", "positiveKarmaPenaltyToZero=true;zero=noChange;pvp/pk/fame/recommendation/sevenSigns/inventory/skills=stable;karmaBoardException=reputationOnly");
	}

	private void sourceScopeAndWiring(PhantomTestContext context) throws Exception
	{
		final String service = source(context, "java/org/l2jmobius/gameserver/qol/PersonalPartySupportService.java");
		final String board = source(context, "dist/game/data/scripts/handlers/bypass/communityboard/PersonalPremiumQoLBoard.java");
		final String build = source(context, "build.xml");
		final String config = source(context, "dist/game/config/Custom/PersonalCharacterQoL.ini");
		assertContains(service, "World.getInstance().getPlayer(targetObjectId)", "Support service does not re-resolve the target at mutation time.");
		assertContains(service, "target.getParty() == party", "Support service does not revalidate current party identity.");
		assertContains(service, "target.fullRestore();", "Heal does not use Player.fullRestore().");
		assertContains(service, "target.doRevive();", "Resurrection does not use the narrow canonical primitive.");
		assertContains(service, "target.setKarma(0);", "Reputation cleanup does not use Player.setKarma(0).");
		PhantomAssertions.assertFalse(service.contains("teleToLocation(") || service.contains("addPartyMember(") || service.contains("joinParty(") || service.contains("DatabaseFactory") || service.contains("setPvpKills(") || service.contains("setPkKills(") || service.contains("setFame("), "Support owner widened into teleport, party insertion, DB, or unrelated reputation state.");
		assertContains(board, "_bbsqol;party;", "Existing Personal QoL utilities surface was not extended.");
		assertContains(config, "EnablePersonalPartySupport=False", "Shipped config is not OFF.");
		assertContains(build, "qol-party-support-test", "Focused QOL-006 target is absent.");
		assertContains(build, "qol-006-affected-test", "Affected QOL-006 target is absent.");
		assertContains(build, "qol-006-freeze-test", "QOL-005/Goal039 freeze target is absent.");
		context.record("qol006.scope", "Alt+B utilities;Player owners;no teleport/direct-party/DB/Phantom production");
	}

	private void createParty()
	{
		if (_player.isInParty())
		{
			_player.getParty().disbandParty();
		}
		if (_observer.isInParty())
		{
			_observer.getParty().disbandParty();
		}
		_party = new Party(_player, PartyDistributionType.FINDERS_KEEPERS);
		_player.setParty(_party);
		_party.addPartyMember(_observer);
		_observer.setParty(_party);
	}

	private void board(String command)
	{
		boardAs(_player, command);
	}

	private static void boardAs(Player actor, String command)
	{
		CommunityBoardHandler.getInstance().handleParseCommand(command, actor);
	}

	private Settings allowlisted()
	{
		return new Settings(true, false, false, false, true, Set.of(_player.getObjectId()), Set.of(), true, "L2-QOL-006 test configuration.");
	}

	private Settings install(Settings settings)
	{
		return PersonalCharacterQoLService.getInstance().installForTests(settings);
	}

	private Path write(String name, String content) throws Exception
	{
		final Path path = _fixtures.resolve(name);
		Files.writeString(path, content, StandardCharsets.UTF_8);
		return path;
	}

	private static String config(String master, String partySupport, String ids, String accounts)
	{
		return "EnablePersonalCharacterQoL=" + master + "\nEnablePersonalCrossClassSkills=False\nEnablePersonalCrystallization=False\nEnablePersonalSevenSignsAccess=False\n" + (partySupport == null ? "" : "EnablePersonalPartySupport=" + partySupport + "\n") + "AllowedCharacterIds=" + ids + "\nAllowedAccounts=" + accounts + "\n";
	}

	private static StateSnapshot snapshot(Player player)
	{
		return new StateSnapshot(player.getExp(), player.getSp(), player.getVitalityPoints(), player.getPvpKills(), player.getPkKills(), player.getFame(), player.getRecomHave(), player.getRecomLeft(), player.getInventory().getInventoryItemCount(PhantomActionFacade.FIXTURE_ITEM_ID, -1), player.getAllSkills().size(), player.getEffectList().getEffects().size(), SevenSigns.getInstance().getPlayerCabal(player.getObjectId()), SevenSigns.getInstance().getPlayerSeal(player.getObjectId()));
	}

	private static void assertUnrelated(StateSnapshot before, Player player, String label)
	{
		final StateSnapshot after = snapshot(player);
		PhantomAssertions.assertEquals(before, after, label + " changed unrelated Player state.");
	}

	private static void assertCurrent(double actual, double expected, String message)
	{
		PhantomAssertions.assertTrue(Math.abs(actual - expected) < 0.001, message + " Expected " + expected + " but was " + actual + ".");
	}

	private static String source(PhantomTestContext context, String relativePath) throws Exception
	{
		return Files.readString(context.moduleRoot().resolve(relativePath), StandardCharsets.UTF_8);
	}

	private static void assertContains(String source, String token, String message)
	{
		PhantomAssertions.assertTrue(source.contains(token), message);
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		Throwable failure = null;
		try
		{
			if ((_player != null) && _player.isInParty())
			{
				_player.getParty().disbandParty();
			}
			if ((_observer != null) && _observer.isInParty())
			{
				_observer.getParty().disbandParty();
			}
			if (_player != null)
			{
				_player.setOnEvent(false);
				_player.setDead(false);
				_player.setKarma(0);
				_player.fullRestore();
				_environment.cleanupLoadedPlayer(_player);
				_player = null;
			}
			if (_observer != null)
			{
				_observer.setOnEvent(false);
				_observer.setDead(false);
				_observer.setKarma(0);
				_observer.fullRestore();
				_environment.cleanupLoadedPlayer(_observer);
				_observer = null;
			}
		}
		catch (Throwable throwable)
		{
			failure = throwable;
		}
		finally
		{
			if (_communitySettings != null)
			{
				_communitySettings.restore();
			}
			install(_previousSettings);
			_environment.shutdown();
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

	private record StateSnapshot(long exp, long sp, int vitality, int pvpKills, int pkKills, int fame, int recommendations, int recommendationsLeft, long fixtureItems, int skills, int effects, int sevenSignsCabal, int sevenSignsSeal)
	{
	}

	private record CommunitySettings(boolean enabled, boolean custom, boolean combatDisabled, boolean karmaDisabled, boolean peaceOnly)
	{
		static CommunitySettings capture()
		{
			return new CommunitySettings(GeneralConfig.ENABLE_COMMUNITY_BOARD, CommunityBoardConfig.CUSTOM_CB_ENABLED, CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED, CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED, CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY);
		}

		void enableForTest()
		{
			GeneralConfig.ENABLE_COMMUNITY_BOARD = true;
			CommunityBoardConfig.CUSTOM_CB_ENABLED = true;
			CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED = true;
			CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED = true;
			CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY = false;
		}

		void restore()
		{
			GeneralConfig.ENABLE_COMMUNITY_BOARD = enabled;
			CommunityBoardConfig.CUSTOM_CB_ENABLED = custom;
			CommunityBoardConfig.COMMUNITYBOARD_COMBAT_DISABLED = combatDisabled;
			CommunityBoardConfig.COMMUNITYBOARD_KARMA_DISABLED = karmaDisabled;
			CommunityBoardConfig.COMMUNITYBOARD_PEACE_ONLY = peaceOnly;
		}
	}
}

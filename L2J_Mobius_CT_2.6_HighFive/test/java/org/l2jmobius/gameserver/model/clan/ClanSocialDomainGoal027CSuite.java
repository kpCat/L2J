/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.model.clan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceState;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.Actor;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.ClanSnapshot;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipEpoch;
import org.l2jmobius.gameserver.model.clan.ClanSocialRepository.StaleStateException;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestConfigurationException;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseBootstrap;
import org.l2jmobius.tests.phantoms.PhantomTestDatabaseGuard;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;
import org.l2jmobius.tests.phantoms.StrictSqlScriptRunner;
import org.l2jmobius.tests.phantoms.StrictSqlScriptRunner.ScriptInfo;

public final class ClanSocialDomainGoal027CSuite implements PhantomTestSuite
{
	private static final long SEED = 27002730L;
	private static final long NOW = 1_000_000L;

	@Override
	public String id()
	{
		return "clan-social-domain-goal027c";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 027C suite used the wrong deterministic seed.");
		final String configProperty = System.getProperty("phantom.test.config");
		if ((configProperty == null) || configProperty.isBlank())
		{
			throw new PhantomTestConfigurationException("Explicit Phantom test database config path is missing.");
		}
		PhantomTestDatabaseBootstrap.initialize(context.moduleRoot(), Path.of(configProperty));
		context.record("database.name", PhantomTestDatabaseGuard.TARGET_DATABASE);
		context.record("migration.mode", "manual-one-shot-old-schema-rehearsal");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-alliance-incarnation-restart-and-persistence-failure", this::allianceIncarnationAndFailure);
		registry.add("02-direct-join-leave-expel-dissolve-fences", this::allianceMembershipLifecycle);
		registry.add("03-hard-w1-w2-restart-and-persistence-fences", this::warIncarnationAndFailure);
		registry.add("04-war-native-rule-parity", this::warRuleParity);
		registry.add("05-real-adapter-schema-and-source-contract", this::sourceContract);
		registry.add("06-manual-old-schema-upgrade-rehearsal", this::manualUpgradeRehearsal);
	}

	private void allianceIncarnationAndFailure(PhantomTestContext context)
	{
		final FakePersistence persistence = new FakePersistence();
		for (int clanId : List.of(1, 2, 3, 4))
		{
			persistence.addClan(clanId);
		}
		final AllianceStateAccess initialState = AllianceStateAccess.standard(1, 2, 3, 4);
		final ClanAllianceService initialService = allianceService(persistence, initialState);
		final Actor leader = new Actor(101, 1, true, false);
		final long capturedDetachedEpoch = initialState.clan(1).allianceGenerationCounter();

		final ClanAllianceService.Result createG1 = initialService.create(leader, "Alpha");
		PhantomAssertions.assertTrue(createG1.successful(), "Alliance G1 creation failed.");
		final AllianceIdentity generationOne = createG1.identity();
		PhantomAssertions.assertTrue(initialService.dissolve(leader, generationOne).successful(), "Alliance G1 dissolution failed.");
		PhantomAssertions.assertEquals(0L, initialState.clan(1).allianceGeneration(), "Dissolved leader did not return to detached generation 0.");
		PhantomAssertions.assertTrue(initialState.clan(1).allianceGenerationCounter() > capturedDetachedEpoch, "Create/dissolve cycle did not advance leader ABA epoch.");
		PhantomAssertions.assertThrows(StaleStateException.class, () -> persistence.createAlliance(1, 0, capturedDetachedEpoch, "StaleAlpha"), "Old detached create CAS passed after create/dissolve cycle.");

		final AllianceStateAccess restartedState = persistence.restoreAllianceState(Map.of(1, "AlphaClan", 2, "BetaClan", 3, "GammaClan", 4, "DeltaClan"));
		final ClanAllianceService restartedService = allianceService(persistence, restartedState);
		final ClanAllianceService.Result createG2 = restartedService.create(leader, "Alpha");
		PhantomAssertions.assertTrue(createG2.successful(), "Alliance G2 recreation after restart failed.");
		final AllianceIdentity generationTwo = createG2.identity();
		PhantomAssertions.assertTrue(generationTwo.generation() > generationOne.generation(), "Alliance recreation reused generation G1.");
		final ClanAllianceService.Result stale = restartedService.dissolve(leader, generationOne);
		PhantomAssertions.assertEquals(ClanAllianceService.Status.STALE, stale.status(), "Stale G1 operation was not rejected.");
		PhantomAssertions.assertEquals(generationTwo, restartedState.clan(1).identity(), "Stale G1 operation changed live G2.");
		PhantomAssertions.assertEquals(generationTwo.generation(), persistence.alliance(1).generation(), "Stale G1 operation changed durable G2.");

		final Actor secondLeader = new Actor(102, 2, true, false);
		final AllianceIdentity secondLeaderGenerationOne = restartedService.create(secondLeader, "Beta").identity();
		PhantomAssertions.assertTrue(restartedService.dissolve(secondLeader, secondLeaderGenerationOne).successful(), "Second leader G1 dissolution failed.");
		PhantomAssertions.assertTrue(joinWithCurrentEpoch(restartedService, leader, secondLeader, generationTwo).successful(), "Former leader could not join another alliance.");
		PhantomAssertions.assertTrue(restartedService.leave(secondLeader, generationTwo).successful(), "Former leader could not leave joined alliance.");
		final AllianceStateAccess historyRestartedState = persistence.restoreAllianceState(Map.of(1, "AlphaClan", 2, "BetaClan", 3, "GammaClan", 4, "DeltaClan"));
		final ClanAllianceService historyRestartedService = allianceService(persistence, historyRestartedState);
		final AllianceIdentity secondLeaderGenerationTwo = historyRestartedService.create(secondLeader, "Beta").identity();
		PhantomAssertions.assertTrue(secondLeaderGenerationTwo.generation() > secondLeaderGenerationOne.generation(), "Join/leave/restart reset the former leader generation high-water.");

		persistence.failAllianceWrites = true;
		final ClanAllianceService.Result failed = historyRestartedService.create(new Actor(104, 4, true, false), "Delta");
		PhantomAssertions.assertEquals(ClanAllianceService.Status.PERSISTENCE_FAILURE, failed.status(), "Alliance SQL failure did not return typed persistence failure.");
		PhantomAssertions.assertEquals(0, historyRestartedState.clan(4).allianceId(), "Alliance SQL failure produced fake in-memory success.");
		PhantomAssertions.assertEquals(0, persistence.alliance(4).allianceId(), "Alliance SQL failure changed durable fixture state.");
	}
	private void allianceMembershipLifecycle(PhantomTestContext context)
	{
		final FakePersistence persistence = new FakePersistence();
		for (int clanId : List.of(1, 2, 3, 4))
		{
			persistence.addClan(clanId);
		}
		final AllianceStateAccess state = AllianceStateAccess.standard(1, 2, 3, 4);
		final ClanAllianceService service = allianceService(persistence, state);
		final Actor leader = new Actor(101, 1, true, false);
		final Actor target = new Actor(102, 2, true, false);
		final AllianceIdentity identity = service.create(leader, "Alpha").identity();
		final MembershipEpoch capturedDetachedTarget = state.clan(2).membershipEpoch();
		final ClanAllianceService.Result targetPermit = service.checkInvite(leader, target);

		state.failNotifications = true;
		PhantomAssertions.assertTrue(service.join(leader, target, identity, targetPermit.targetEpoch()).successful(), "A post-commit alliance notification exception became false durable failure.");
		PhantomAssertions.assertEquals(identity, state.clan(2).identity(), "Alliance notification failure left canonical memory divergent.");
		PhantomAssertions.assertTrue(state.clan(2).allianceGenerationCounter() > capturedDetachedTarget.counter(), "Join did not advance target ABA epoch.");
		PhantomAssertions.assertEquals(state.clan(2).allianceGenerationCounter(), persistence.alliance(2).generationCounter(), "Join notification failure left durable epoch divergent.");
		state.failNotifications = false;
		final ClanAllianceService.Result staleLeave = service.leave(target, new AllianceIdentity(identity.leaderClanId(), identity.generation() + 1));
		PhantomAssertions.assertEquals(ClanAllianceService.Status.STALE, staleLeave.status(), "Stale leave generation was not rejected.");
		PhantomAssertions.assertTrue(service.leave(target, identity).successful(), "Canonical alliance leave failed.");
		PhantomAssertions.assertEquals(0, state.clan(2).allianceId(), "Leave did not clear membership.");
		PhantomAssertions.assertEquals(0L, state.clan(2).allianceGeneration(), "Leave did not clear current alliance generation.");
		PhantomAssertions.assertTrue(state.clan(2).allianceGenerationCounter() > capturedDetachedTarget.counter(), "Join/leave cycle did not advance detached ABA epoch.");
		PhantomAssertions.assertEquals(Clan.PENALTY_TYPE_CLAN_LEAVED, state.clan(2).alliancePenaltyType(), "Leave penalty type changed.");
		final ClanAllianceService.Result staleOldJoin = service.join(leader, target, identity, capturedDetachedTarget);
		PhantomAssertions.assertEquals(ClanAllianceService.Status.STALE, staleOldJoin.status(), "Old detached target epoch passed join CAS after join/leave cycle.");

		final Actor memberThree = new Actor(103, 3, true, false);
		final Actor memberFour = new Actor(104, 4, true, false);
		PhantomAssertions.assertTrue(joinWithCurrentEpoch(service, leader, memberThree, identity).successful(), "First member join before multi-member dissolve failed.");
		PhantomAssertions.assertTrue(joinWithCurrentEpoch(service, leader, memberFour, identity).successful(), "Second member join before multi-member dissolve failed.");
		final Map<Integer, Long> beforeDissolve = Map.of(1, state.clan(1).allianceGenerationCounter(), 3, state.clan(3).allianceGenerationCounter(), 4, state.clan(4).allianceGenerationCounter());
		PhantomAssertions.assertTrue(service.dissolve(leader, identity).successful(), "Canonical multi-member dissolve failed.");
		for (int clanId : beforeDissolve.keySet())
		{
			PhantomAssertions.assertEquals(0, state.clan(clanId).allianceId(), "Dissolve left member allied: " + clanId);
			PhantomAssertions.assertEquals(0L, state.clan(clanId).allianceGeneration(), "Dissolve left current generation on member: " + clanId);
			PhantomAssertions.assertTrue(state.clan(clanId).allianceGenerationCounter() > beforeDissolve.get(clanId), "Dissolve did not advance member ABA epoch: " + clanId);
		}

		final FakePersistence expelPersistence = new FakePersistence();
		expelPersistence.addClan(1);
		expelPersistence.addClan(3);
		final AllianceStateAccess expelState = AllianceStateAccess.standard(1, 3);
		final ClanAllianceService expelService = allianceService(expelPersistence, expelState);
		final AllianceIdentity expelIdentity = expelService.create(leader, "Expel").identity();
		final long expelledDetachedEpoch = expelState.clan(3).allianceGenerationCounter();
		PhantomAssertions.assertTrue(joinWithCurrentEpoch(expelService, leader, memberThree, expelIdentity).successful(), "Direct join before expel failed.");
		PhantomAssertions.assertEquals(ClanAllianceService.Reason.TARGET_NOT_FOUND, expelService.expel(leader, 99, expelIdentity).reason(), "Missing expel target parity failed.");
		PhantomAssertions.assertTrue(expelService.expel(leader, 3, expelIdentity).successful(), "Canonical expel failed.");
		PhantomAssertions.assertEquals(0L, expelState.clan(3).allianceGeneration(), "Expel did not clear current generation.");
		PhantomAssertions.assertTrue(expelState.clan(3).allianceGenerationCounter() > expelledDetachedEpoch, "Join/expel cycle did not advance detached ABA epoch.");
		PhantomAssertions.assertEquals(Clan.PENALTY_TYPE_CLAN_DISMISSED, expelState.clan(3).alliancePenaltyType(), "Expel penalty type changed.");
	}
	private void warIncarnationAndFailure(PhantomTestContext context)
	{
		final FakePersistence persistence = new FakePersistence();
		final WarStateAccess state = WarStateAccess.standard();
		final ClanWarService service = warService(persistence, state);
		final ClanWarService.Actor leader = new ClanWarService.Actor(101, 1, true);

		state.failNotifications = true;
		final ClanWarService.Result declarationOne = service.declare(leader, "BetaClan");
		PhantomAssertions.assertTrue(declarationOne.successful(), "A post-commit war-start notification exception became false durable failure.");
		final long warOne = declarationOne.identity().warId();
		PhantomAssertions.assertTrue(state.sourceAtWarWith(1, 2), "War-start notification failure left the legacy view divergent.");
		PhantomAssertions.assertTrue(service.stop(leader, 2, warOne).successful(), "A post-commit war-end notification exception became false durable failure.");
		PhantomAssertions.assertFalse(state.sourceAtWarWith(1, 2), "War-end notification failure left the legacy view divergent.");
		state.failNotifications = false;
		final ClanWarService.Result declarationTwo = service.declare(leader, "BetaClan");
		PhantomAssertions.assertTrue(declarationTwo.successful(), "War W2 declaration failed.");
		final long warTwo = declarationTwo.identity().warId();
		PhantomAssertions.assertTrue(warTwo != warOne, "Same-pair W2 reused W1 war_id.");

		final ClanWarService.Result staleStop = service.stop(leader, 2, warOne);
		PhantomAssertions.assertEquals(ClanWarService.Status.STALE, staleStop.status(), "Stale W1 stop was not rejected against W2.");
		PhantomAssertions.assertEquals(warTwo, service.currentWar(1, 2).orElseThrow().warId(), "Stale W1 stop changed current W2 registry identity.");
		PhantomAssertions.assertTrue(state.sourceAtWarWith(1, 2), "Stale W1 stop changed legacy W2 view.");
		PhantomAssertions.assertEquals(warTwo, persistence.war(1, 2).warId(), "Stale W1 stop changed durable W2.");

		persistence.failWarDelete = true;
		final ClanWarService.Result failedStop = service.stop(leader, 2, warTwo);
		PhantomAssertions.assertEquals(ClanWarService.Status.PERSISTENCE_FAILURE, failedStop.status(), "War delete SQL failure did not return typed failure.");
		PhantomAssertions.assertEquals(warTwo, service.currentWar(1, 2).orElseThrow().warId(), "War delete SQL failure removed W2 registry state.");
		PhantomAssertions.assertTrue(state.sourceAtWarWith(1, 2), "War delete SQL failure removed legacy W2 state.");
		persistence.failWarDelete = false;

		final WarStateAccess restartedState = WarStateAccess.standard();
		final ClanWarService restarted = warService(persistence, restartedState);
		PhantomAssertions.assertTrue(restarted.restoreWars().successful(), "War identity restore failed.");
		PhantomAssertions.assertEquals(warTwo, restarted.currentWar(1, 2).orElseThrow().warId(), "W2 war_id did not survive restore.");
		PhantomAssertions.assertTrue(restartedState.sourceAtWarWith(1, 2), "Restore did not rebuild legacy isAtWarWith view.");

		persistence.failWarCreate = true;
		final ClanWarService.Result failedDeclare = restarted.declare(leader, "GammaClan");
		PhantomAssertions.assertEquals(ClanWarService.Status.PERSISTENCE_FAILURE, failedDeclare.status(), "War insert SQL failure did not return typed failure.");
		PhantomAssertions.assertTrue(restarted.currentWar(1, 3).isEmpty(), "War insert SQL failure produced fake registry success.");
		PhantomAssertions.assertFalse(restartedState.sourceAtWarWith(1, 3), "War insert SQL failure produced fake legacy-set success.");
	}

	private void warRuleParity(PhantomTestContext context)
	{
		final FakePersistence persistence = new FakePersistence();
		final WarStateAccess state = WarStateAccess.standard();
		ClanWarService service = warService(persistence, state);
		PhantomAssertions.assertEquals(ClanWarService.Reason.NOT_AUTHORIZED, service.declare(new ClanWarService.Actor(101, 1, false), "BetaClan").reason(), "War authority rule changed.");		final FakePersistence acceptedPersistence = new FakePersistence();
		final WarStateAccess acceptedState = WarStateAccess.standard();
		acceptedState.putClan(1, "AlphaClan", 1, 1, 7, 0);
		acceptedState.putClan(2, "BetaClan", 1, 1, 7, NOW + 1);
		PhantomAssertions.assertTrue(warService(acceptedPersistence, acceptedState).declareAcceptedReply(1, 2).successful(), "Legacy accepted start reply incorrectly inherited direct declaration eligibility gates.");

		state.putClan(1, "AlphaClan", 2, 50, 0, 0);
		PhantomAssertions.assertEquals(ClanWarService.Reason.SOURCE_REQUIREMENTS, service.declare(new ClanWarService.Actor(101, 1, true), "BetaClan").reason(), "War source requirements changed.");
		state.putClan(1, "AlphaClan", 5, 50, 0, 0);
		PhantomAssertions.assertEquals(ClanWarService.Reason.TARGET_NOT_FOUND, service.declare(new ClanWarService.Actor(101, 1, true), "MissingClan").reason(), "Missing war target rule changed.");
		PhantomAssertions.assertEquals(ClanWarService.Reason.SELF_TARGET, service.declare(new ClanWarService.Actor(101, 1, true), "AlphaClan").reason(), "Self war rule changed.");

		state.putClan(1, "AlphaClan", 5, 50, 7, 0);
		state.putClan(2, "BetaClan", 5, 50, 7, 0);
		PhantomAssertions.assertEquals(ClanWarService.Reason.ALLIED_TARGET, service.declare(new ClanWarService.Actor(101, 1, true), "BetaClan").reason(), "Allied war restriction changed.");
		state.putClan(1, "AlphaClan", 5, 50, 0, 0);
		state.putClan(2, "BetaClan", 2, 50, 0, 0);
		PhantomAssertions.assertEquals(ClanWarService.Reason.TARGET_REQUIREMENTS, service.declare(new ClanWarService.Actor(101, 1, true), "BetaClan").reason(), "War target requirements changed.");
		state.putClan(2, "BetaClan", 5, 50, 0, NOW + 1);
		PhantomAssertions.assertEquals(ClanWarService.Reason.TARGET_DISSOLVING, service.declare(new ClanWarService.Actor(101, 1, true), "BetaClan").reason(), "Dissolving target restriction changed.");

		state.putClan(2, "BetaClan", 5, 50, 0, 0);
		final ClanWarService.Result declared = service.declare(new ClanWarService.Actor(101, 1, true), "BetaClan");
		PhantomAssertions.assertTrue(declared.successful(), "Parity fixture could not declare war.");
		PhantomAssertions.assertEquals(ClanWarService.Reason.ALREADY_ACTIVE, service.declare(new ClanWarService.Actor(101, 1, true), "BetaClan").reason(), "Duplicate active war rule changed.");
		state.attackStance = true;
		PhantomAssertions.assertEquals(ClanWarService.Reason.ATTACK_STANCE, service.stop(new ClanWarService.Actor(101, 1, true), 2, declared.identity().warId()).reason(), "Stop attack-stance rule changed.");
		final FakePersistence mutualPersistence = new FakePersistence();
		final WarStateAccess mutualState = WarStateAccess.standard();
		final ClanWarService mutualService = warService(mutualPersistence, mutualState);
		final ClanWarService.Result forward = mutualService.declare(new ClanWarService.Actor(101, 1, true), "BetaClan");
		final ClanWarService.Result reverse = mutualService.declare(new ClanWarService.Actor(202, 2, true), "AlphaClan");
		PhantomAssertions.assertTrue(forward.successful() && reverse.successful(), "Directed mutual war declarations no longer coexist.");
		PhantomAssertions.assertTrue(mutualService.stop(new ClanWarService.Actor(101, 1, true), 2, forward.identity().warId()).successful(), "Forward side of mutual war could not stop exactly.");
		PhantomAssertions.assertTrue(mutualService.currentWar(1, 2).isEmpty(), "Exact forward stop left the stopped direction active.");
		PhantomAssertions.assertEquals(reverse.identity().warId(), mutualService.currentWar(2, 1).orElseThrow().warId(), "Exact forward stop removed reverse directed war.");
		PhantomAssertions.assertTrue(mutualState.sourceAtWarWith(2, 1), "Exact forward stop removed reverse legacy Clan view.");
		PhantomAssertions.assertEquals(reverse.identity().warId(), mutualPersistence.war(2, 1).warId(), "Exact forward stop removed reverse durable war.");
		final WarStateAccess mutualRestartState = WarStateAccess.standard();
		final ClanWarService mutualRestart = warService(mutualPersistence, mutualRestartState);
		PhantomAssertions.assertTrue(mutualRestart.restoreWars().successful(), "Mutual remainder restore failed.");
		PhantomAssertions.assertTrue(mutualRestart.currentWar(1, 2).isEmpty() && mutualRestart.currentWar(2, 1).isPresent(), "Directed mutual state changed across restart.");
		mutualRestartState.attackStance = true;
		PhantomAssertions.assertTrue(mutualRestart.surrender(new ClanWarService.Actor(202, 2, false), 1, reverse.identity().warId()).successful(), "Legacy surrender incorrectly inherited stop authority/attack-stance gates.");
	}

	private void sourceContract(PhantomTestContext context) throws Exception
	{
		final Path root = context.moduleRoot();
		final String allianceService = read(root, "java/org/l2jmobius/gameserver/model/clan/ClanAllianceService.java");
		final String warService = read(root, "java/org/l2jmobius/gameserver/model/clan/ClanWarService.java");
		final String repository = read(root, "java/org/l2jmobius/gameserver/model/clan/ClanSocialRepository.java");
		final String clan = read(root, "java/org/l2jmobius/gameserver/model/clan/Clan.java");
		final String clanTable = read(root, "java/org/l2jmobius/gameserver/data/sql/ClanTable.java");
		final String requestJoin = read(root, "java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinAlly.java");
		final String answerJoin = read(root, "java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinAlly.java");
		final String clanData = read(root, "dist/db_installer/sql/game/clan_data.sql");
		final String clanWars = read(root, "dist/db_installer/sql/game/clan_wars.sql");
		final String migration = read(root, "docs/phantoms/migrations/V027C__canonical_clan_social_domain.sql");
		final String playerSource = read(root, "java/org/l2jmobius/gameserver/model/actor/Player.java");
		final String clientPackets = read(root, "java/org/l2jmobius/gameserver/network/ClientPackets.java");
		final List<String> realWarAdapters = warAdapters(root);
		final String realWarAdapterSource = String.join("\\n", realWarAdapters);

		for (String serviceSource : List.of(allianceService, warService))
		{
			PhantomAssertions.assertFalse(serviceSource.contains("gameserver.phantoms"), "Native clan service imports Phantom code.");
			PhantomAssertions.assertFalse(serviceSource.contains("ClientPacket"), "Native clan service owns packet transport.");
			PhantomAssertions.assertFalse(serviceSource.contains("getRequest()"), "Native clan service depends on client request state.");
		}
		PhantomAssertions.assertTrue(repository.contains("connection.commit()") && repository.contains("requireSingleRow"), "Canonical persistence lacks durable commit/affected-row contract.");
		PhantomAssertions.assertTrue(allianceService.contains("record MembershipEpoch" ) && repository.contains("ally_generation=0, ally_generation_counter=?"), "Alliance detached ABA epoch semantics are not explicit in service/repository.");
		PhantomAssertions.assertTrue(repository.contains("WHERE war_id=? AND clan1=? AND clan2=?"), "War delete is not exact-incarnation compare-before-mutate.");
		PhantomAssertions.assertFalse(clanTable.contains("public void storeClanWars") || clanTable.contains("public void deleteClanWars"), "Unsafe ClanTable war writer bypass remains.");
		PhantomAssertions.assertTrue(clanTable.contains("ClanWarService.getInstance().restoreWars") && clanTable.contains("removeAllForClan"), "ClanTable does not delegate restore/destruction war writes.");
		PhantomAssertions.assertFalse(clan.contains("public void createAlly") || clan.contains("public void dissolveAlly") || clan.contains("public void changeAllyCrest"), "Legacy public alliance writer remains in Clan.");
		PhantomAssertions.assertFalse(clan.substring(clan.indexOf("public void updateClanInDB"), clan.indexOf("public void store()" )).contains("ally_id=?"), "Generic Clan update can overwrite canonical alliance state.");
		PhantomAssertions.assertTrue(requestJoin.contains("ClanAllianceService.getInstance().checkInvite") && requestJoin.contains("AllianceIdentity"), "REAL invite adapter does not capture canonical alliance incarnation.");
		PhantomAssertions.assertTrue(answerJoin.contains("ClanAllianceService.getInstance().join") && answerJoin.contains("getAllianceIdentity") && answerJoin.contains("getTargetEpoch") && requestJoin.contains("MembershipEpoch"), "REAL answer adapter does not delegate exact alliance/target epoch invitation identity.");
		final String villageMaster = read(root, "java/org/l2jmobius/gameserver/model/actor/instance/VillageMaster.java");
		PhantomAssertions.assertTrue(villageMaster.contains("handleCreateAlliance") && villageMaster.contains("handleDissolveAlliance") && !villageMaster.contains(".createAlly(") && !villageMaster.contains(".dissolveAlly("), "VillageMaster alliance paths do not delegate canonical handlers.");

		for (String adapter : allianceAdapters(root))
		{
			for (String forbidden : List.of(".setAllyId(", ".setAllyName(", ".changeAllyCrest(", ".updateClanInDB("))
			{
				PhantomAssertions.assertFalse(adapter.contains(forbidden), "REAL alliance adapter retains direct mutation: " + forbidden);
			}
		}
		for (String adapter : realWarAdapters)
		{
			PhantomAssertions.assertFalse(adapter.contains("storeClanWars") || adapter.contains("deleteClanWars"), "REAL war adapter retains ClanTable writer bypass.");
			PhantomAssertions.assertTrue(adapter.contains("ClanWarService"), "REAL war adapter does not delegate canonical war service.");
		}
		PhantomAssertions.assertTrue(clanData.contains("ally_generation") && clanData.contains("ally_generation_counter") && clanWars.contains("war_id") && clanWars.contains("uq_clan_wars_pair"), "Fresh-install schema lacks exact identities.");
		PhantomAssertions.assertTrue(migration.contains("SET `ally_generation` = 1") && migration.contains("ally_generation_counter") && migration.contains("AUTO_INCREMENT") && !migration.contains("DROP TABLE"), "Manual upgrade does not preserve/init exact identities safely.");
		PhantomAssertions.assertTrue(clanWars.contains("wantspeace1") && clanWars.contains("wantspeace2") && repository.contains("wantspeace1, wantspeace2") && repository.contains("VALUES(?,?,0,0)"), "Legacy durable peace columns/defaults were not preserved.");
		PhantomAssertions.assertTrue(playerSource.contains("getWantsPeace() == 0") && playerSource.contains("clan.isAtWarWith(attackerPlayer.getClanId())") && playerSource.contains("attackerClan.isAtWarWith(getClanId())"), "Legacy mutual-war Player semantics changed.");
		PhantomAssertions.assertTrue(clientPackets.contains("REQUEST_START_PLEDGE_WAR") && clientPackets.contains("REQUEST_REPLY_START_PLEDGE") && clientPackets.contains("REQUEST_STOP_PLEDGE_WAR") && clientPackets.contains("REQUEST_REPLY_STOP_PLEDGE_WAR") && clientPackets.contains("REQUEST_SURRENDER_PLEDGE_WAR") && clientPackets.contains("REQUEST_REPLY_SURRENDER_PLEDGE_WAR"), "Legacy REAL war request/reply opcode lifecycle changed.");
		PhantomAssertions.assertTrue(realWarAdapterSource.contains("getActiveRequester()") && realWarAdapterSource.contains("setActiveRequester(null)") && realWarAdapterSource.contains("onTransactionResponse()") && realWarAdapterSource.contains("REQUEST_TO_END_WAR_HAS_BEEN_DENIED") && realWarAdapterSource.contains("YOU_HAVE_SURRENDERED_TO_THE_S1_CLAN"), "Legacy REAL reply/peace/surrender user-facing lifecycle evidence is missing.");		PhantomAssertions.assertTrue(realWarAdapterSource.contains("declareAcceptedReply") && warService.contains("Result declareAcceptedReply"), "Legacy accepted war-start reply was collapsed into direct declaration gates.");
		PhantomAssertions.assertTrue(warService.contains("notifySafely") && warService.contains("notifyWarStarted") && warService.contains("notifyWarEnded") && allianceService.contains("notifySafely"), "Post-commit notifications are not isolated from typed durable success.");
	}

	static void createOldSchemaFixture(Connection connection, Path moduleRoot) throws Exception
	{
		final Path clanDataFile = moduleRoot.resolve("dist/db_installer/sql/game/clan_data.sql");
		final Path clanWarsFile = moduleRoot.resolve("dist/db_installer/sql/game/clan_wars.sql");
		final StringBuilder oldClanData = new StringBuilder();
		for (String line : Files.readAllLines(clanDataFile))
		{
			if (!line.contains("`ally_generation`") && !line.contains("`ally_generation_counter`"))
			{
				oldClanData.append(line).append('\n');
			}
		}
		final StringBuilder oldClanWars = new StringBuilder();
		for (String line : Files.readAllLines(clanWarsFile))
		{
			if (line.stripLeading().startsWith("`war_id`") || line.contains("`uq_clan_wars_pair`"))
			{
				continue;
			}
			oldClanWars.append(line.contains("PRIMARY KEY (`war_id`)") ? "  PRIMARY KEY (`clan1`,`clan2`)" : line).append('\n');
		}
		PhantomAssertions.assertFalse(oldClanData.toString().contains("ally_generation"), "Derived pre-027C clan_data still contains incarnation columns.");
		PhantomAssertions.assertFalse(oldClanWars.toString().contains("war_id"), "Derived pre-027C clan_wars still contains war_id.");
		StrictSqlScriptRunner.execute(connection, List.of(script(clanDataFile, moduleRoot, oldClanData.toString()), script(clanWarsFile, moduleRoot, oldClanWars.toString())));
	}

	static void seedOldSchemaFixture(Connection connection) throws SQLException
	{
		execute(connection, "INSERT INTO `clan_data` (`clan_id`,`clan_name`,`clan_level`,`ally_id`,`ally_name`,`leader_id`) VALUES (1,'AlphaClan',5,1,'Alpha',101),(2,'BetaClan',5,1,'Alpha',102),(3,'GammaClan',5,0,NULL,103),(4,'DeltaClan',5,4,'Delta',104)");
		execute(connection, "INSERT INTO `clan_wars` (`clan1`,`clan2`,`wantspeace1`,`wantspeace2`) VALUES ('1','2',1,0),('2','1',0,1),('3','4',1,1)");
	}

	static void applyExactMigration(Connection connection, Path moduleRoot, Path migrationFile) throws Exception
	{
		final String content = Files.readString(migrationFile);
		PhantomAssertions.assertTrue(content.contains("manual one-shot upgrade") && content.contains("apply this file exactly once"), "027C upgrade artifact no longer declares its one-shot convention.");
		StrictSqlScriptRunner.execute(connection, List.of(script(migrationFile, moduleRoot, content)));
	}

	private static ScriptInfo script(Path file, Path moduleRoot, String content)
	{
		return new ScriptInfo(file, moduleRoot.relativize(file).toString().replace('\\', '/'), "manual-rehearsal", StrictSqlScriptRunner.splitStatements(file, content));
	}
	private static Map<Integer, MigratedClan> migratedClans(Connection connection) throws SQLException
	{
		final Map<Integer, MigratedClan> rows = new HashMap<>();
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT `clan_id`,`clan_name`,`clan_level`,`ally_id`,`ally_name`,`ally_generation`,`ally_generation_counter` FROM `clan_data` ORDER BY `clan_id`"))
		{
			while (result.next())
			{
				final MigratedClan row = new MigratedClan(result.getInt("clan_id"), result.getString("clan_name"), result.getInt("clan_level"), result.getInt("ally_id"), result.getString("ally_name"), result.getLong("ally_generation"), result.getLong("ally_generation_counter"));
				rows.put(row.clanId(), row);
			}
		}
		return rows;
	}

	private static Map<String, MigratedWar> migratedWars(Connection connection) throws SQLException
	{
		final Map<String, MigratedWar> rows = new HashMap<>();
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT `war_id`,`clan1`,`clan2`,`wantspeace1`,`wantspeace2` FROM `clan_wars` ORDER BY `war_id`"))
		{
			while (result.next())
			{
				final MigratedWar row = new MigratedWar(result.getLong("war_id"), result.getString("clan1"), result.getString("clan2"), result.getInt("wantspeace1"), result.getInt("wantspeace2"));
				rows.put(row.clan1() + ":" + row.clan2(), row);
			}
		}
		return rows;
	}

	private static void verifyMigratedAllianceRows(Map<Integer, MigratedClan> clans)
	{
		PhantomAssertions.assertEquals(new MigratedClan(1, "AlphaClan", 5, 1, "Alpha", 1, 1), clans.get(1), "Existing alliance leader did not receive exact generation/counter.");
		PhantomAssertions.assertEquals(new MigratedClan(2, "BetaClan", 5, 1, "Alpha", 1, 0), clans.get(2), "Existing alliance member did not receive its exact persistent identity.");
		PhantomAssertions.assertEquals(new MigratedClan(3, "GammaClan", 5, 0, null, 0, 0), clans.get(3), "Inactive clan history was invented by migration.");
		PhantomAssertions.assertEquals(new MigratedClan(4, "DeltaClan", 5, 4, "Delta", 1, 1), clans.get(4), "Second existing alliance leader did not receive a persistent identity.");
	}

	private static void verifyMigratedWarRows(Map<String, MigratedWar> wars)
	{
		final Set<Long> identities = new HashSet<>();
		for (MigratedWar war : wars.values())
		{
			PhantomAssertions.assertTrue(war.warId() > 0, "Migrated war_id is zero.");
			PhantomAssertions.assertTrue(identities.add(war.warId()), "Migrated war_id is not unique.");
		}
		PhantomAssertions.assertEquals(new PeaceFlags(1, 0), wars.get("1:2").peaceFlags(), "wantspeace flags changed for forward war.");
		PhantomAssertions.assertEquals(new PeaceFlags(0, 1), wars.get("2:1").peaceFlags(), "wantspeace flags changed for reverse war.");
		PhantomAssertions.assertEquals(new PeaceFlags(1, 1), wars.get("3:4").peaceFlags(), "wantspeace flags changed for third war.");
	}
	private static void verifyMigrationKeys(Connection connection) throws SQLException
	{
		final Set<IndexColumn> clanIndexes = indexes(connection, "clan_data");
		final Set<IndexColumn> warIndexes = indexes(connection, "clan_wars");
		PhantomAssertions.assertTrue(clanIndexes.contains(new IndexColumn("PRIMARY", false, 1, "clan_id")), "clan_data primary key changed during migration.");
		PhantomAssertions.assertTrue(warIndexes.contains(new IndexColumn("PRIMARY", false, 1, "war_id")), "clan_wars war_id primary key is missing.");
		PhantomAssertions.assertTrue(warIndexes.contains(new IndexColumn("uq_clan_wars_pair", false, 1, "clan1")) && warIndexes.contains(new IndexColumn("uq_clan_wars_pair", false, 2, "clan2")), "clan_wars directed-pair UNIQUE key is missing or reordered.");
	}

	private static Set<IndexColumn> indexes(Connection connection, String table) throws SQLException
	{
		final Set<IndexColumn> indexes = new HashSet<>();
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SHOW INDEX FROM `" + table + "`"))
		{
			while (result.next())
			{
				indexes.add(new IndexColumn(result.getString("Key_name"), result.getInt("Non_unique") != 0, result.getInt("Seq_in_index"), result.getString("Column_name")));
			}
		}
		return indexes;
	}

	private static void verifyCanonicalRestore(Map<Integer, MigratedClan> clans, Map<String, MigratedWar> wars)
	{
		final AllianceStateAccess allianceState = new AllianceStateAccess();
		final FakePersistence alliancePersistence = new FakePersistence();
		for (MigratedClan row : clans.values())
		{
			allianceState.putClan(row.clanId(), row.clanName(), row.clanLevel(), row.allianceId(), row.allianceName(), row.generation(), 0, 0, 0, 0, row.generationCounter());
			alliancePersistence.addClan(row.clanId());
		}
		final ClanAllianceService allianceService = allianceService(alliancePersistence, allianceState);
		final AllianceIdentity alpha = allianceService.checkInvite(new Actor(101, 1, true, false), new Actor(103, 3, true, false)).identity();
		final AllianceIdentity delta = allianceService.checkInvite(new Actor(104, 4, true, false), new Actor(103, 3, true, false)).identity();
		PhantomAssertions.assertEquals(new AllianceIdentity(1, 1), alpha, "Canonical restore lost upgraded Alpha alliance identity.");
		PhantomAssertions.assertEquals(alpha, allianceState.clan(2).identity(), "Canonical restore lost upgraded member identity.");
		PhantomAssertions.assertEquals(new AllianceIdentity(4, 1), delta, "Canonical restore conflated distinct upgraded alliances.");

		final FakePersistence warPersistence = new FakePersistence();
		for (MigratedWar row : wars.values())
		{
			warPersistence.restoreWar(new ClanSocialRepository.WarRow(row.warId(), Integer.parseInt(row.clan1()), Integer.parseInt(row.clan2())));
		}
		final WarStateAccess warState = WarStateAccess.standard();
		warState.putClan(4, "DeltaClan", 5, 50, 0, 0);
		final ClanWarService warService = warService(warPersistence, warState);
		PhantomAssertions.assertTrue(warService.restoreWars().successful(), "Canonical war restore rejected migrated rows.");
		for (MigratedWar row : wars.values())
		{
			final ClanWarService.WarIdentity restored = warService.currentWar(Integer.parseInt(row.clan1()), Integer.parseInt(row.clan2())).orElseThrow();
			PhantomAssertions.assertEquals(row.warId(), restored.warId(), "Canonical restore changed migrated war_id.");
		}
	}
	private static void verifyMariaDbWarIncarnation(Map<String, MigratedWar> migratedWars) throws Exception
	{
		final ClanSocialRepository repository = ClanSocialRepository.getInstance();
		final Map<String, Long> migratedIdentities = new HashMap<>();
		for (MigratedWar war : migratedWars.values())
		{
			migratedIdentities.put(war.clan1() + ":" + war.clan2(), war.warId());
		}
		for (ClanSocialRepository.WarRow restored : repository.loadWars())
		{
			PhantomAssertions.assertEquals(migratedIdentities.get(pair(restored.sourceClanId(), restored.targetClanId())), restored.warId(), "Repository restore changed a migrated war_id.");
		}

		final WarStateAccess state = WarStateAccess.standard();
		state.putClan(4, "DeltaClan", 5, 50, 0, 0);
		state.putClan(10, "WarTen", 5, 50, 0, 0);
		state.putClan(20, "WarTwenty", 5, 50, 0, 0);
		final ClanWarService initial = warService(repository, state);
		PhantomAssertions.assertTrue(initial.restoreWars().successful(), "MariaDB W1 fixture restore failed.");
		final ClanWarService.Actor leader = new ClanWarService.Actor(1001, 10, true);
		final ClanWarService.Result w1Result = initial.declare(leader, "WarTwenty");
		PhantomAssertions.assertTrue(w1Result.successful(), "MariaDB-backed W1 create failed.");
		final long warOne = w1Result.identity().warId();
		PhantomAssertions.assertFalse(migratedIdentities.containsValue(warOne), "New W1 reused a migrated war incarnation id.");
		PhantomAssertions.assertTrue(initial.stop(leader, 20, warOne).successful(), "MariaDB-backed W1 delete failed.");

		final WarStateAccess restartedState = WarStateAccess.standard();
		restartedState.putClan(4, "DeltaClan", 5, 50, 0, 0);
		restartedState.putClan(10, "WarTen", 5, 50, 0, 0);
		restartedState.putClan(20, "WarTwenty", 5, 50, 0, 0);
		final ClanWarService restarted = warService(repository, restartedState);
		PhantomAssertions.assertTrue(restarted.restoreWars().successful(), "MariaDB restore boundary after W1 delete failed.");
		PhantomAssertions.assertTrue(restarted.currentWar(10, 20).isEmpty(), "Deleted W1 reappeared across MariaDB restore boundary.");
		final ClanWarService.Result w2Result = restarted.declare(leader, "WarTwenty");
		PhantomAssertions.assertTrue(w2Result.successful(), "MariaDB-backed W2 create failed.");
		final long warTwo = w2Result.identity().warId();
		PhantomAssertions.assertTrue((warTwo != warOne) && !migratedIdentities.containsValue(warTwo), "AUTO_INCREMENT reused a deleted or migrated war incarnation id.");
		PhantomAssertions.assertEquals(ClanWarService.Status.STALE, restarted.stop(leader, 20, warOne).status(), "Stale MariaDB W1 stop/peace affected W2.");
		PhantomAssertions.assertEquals(warTwo, restarted.currentWar(10, 20).orElseThrow().warId(), "Stale MariaDB W1 replay changed W2 registry identity.");
		PhantomAssertions.assertTrue(restarted.stop(leader, 20, warTwo).successful(), "MariaDB W2 cleanup failed.");
	}
	static void restoreFreshSchemaTables(Connection connection) throws SQLException
	{
		execute(connection, "DROP TABLE IF EXISTS `clan_data`, `clan_wars`, `clan_social_identity`");
		execute(connection, "RENAME TABLE `clan_data_027c_backup` TO `clan_data`, `clan_wars_027c_backup` TO `clan_wars`, `clan_social_identity_027c_backup` TO `clan_social_identity`");
	}

	static boolean tableExists(Connection connection, String table) throws SQLException
	{
		try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?"))
		{
			statement.setString(1, table);
			try (ResultSet result = statement.executeQuery())
			{
				return result.next() && (result.getInt(1) == 1);
			}
		}
	}

	static int scalarInt(Connection connection, String sql) throws SQLException
	{
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql))
		{
			return result.next() ? result.getInt(1) : -1;
		}
	}

	static String scalarString(Connection connection, String sql) throws SQLException
	{
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql))
		{
			return result.next() ? result.getString(1) : null;
		}
	}

	static long scalarLong(Connection connection, String sql) throws SQLException
	{
		try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql))
		{
			return result.next() ? result.getLong(1) : -1;
		}
	}

	static void execute(Connection connection, String sql) throws SQLException
	{
		try (Statement statement = connection.createStatement())
		{
			statement.execute(sql);
		}
	}

	private record MigratedClan(int clanId, String clanName, int clanLevel, int allianceId, String allianceName, long generation, long generationCounter)
	{
	}

	private record MigratedWar(long warId, String clan1, String clan2, int wantsPeace1, int wantsPeace2)
	{
		PeaceFlags peaceFlags()
		{
			return new PeaceFlags(wantsPeace1, wantsPeace2);
		}
	}

	private record PeaceFlags(int wantsPeace1, int wantsPeace2)
	{
	}

	private record IndexColumn(String name, boolean nonUnique, int sequence, String column)
	{
	}
	private void manualUpgradeRehearsal(PhantomTestContext context) throws Exception
	{
		final Path migrationFile = context.moduleRoot().resolve("docs/phantoms/migrations/V027C__canonical_clan_social_domain.sql");
		boolean backupsCreated = false;
		try (Connection connection = DatabaseFactory.getConnection())
		{
			PhantomAssertions.assertEquals(PhantomTestDatabaseGuard.TARGET_DATABASE, scalarString(connection, "SELECT DATABASE()"), "Migration rehearsal escaped the allowlisted test database.");
			PhantomAssertions.assertFalse(tableExists(connection, "clan_data_027c_backup") || tableExists(connection, "clan_wars_027c_backup") || tableExists(connection, "clan_social_identity_027c_backup"), "Previous migration rehearsal backup tables remain; guarded provisioning is required.");
			final int originalClanRows = scalarInt(connection, "SELECT COUNT(*) FROM `clan_data`");
			final int originalWarRows = scalarInt(connection, "SELECT COUNT(*) FROM `clan_wars`");
			final long originalAllianceHighWater = scalarLong(connection, "SELECT `high_water` FROM `clan_social_identity` WHERE `identity_name`='alliance_incarnation'");
			try
			{
				execute(connection, "RENAME TABLE `clan_data` TO `clan_data_027c_backup`, `clan_wars` TO `clan_wars_027c_backup`, `clan_social_identity` TO `clan_social_identity_027c_backup`");
				backupsCreated = true;
				createOldSchemaFixture(connection, context.moduleRoot());
				seedOldSchemaFixture(connection);
				PhantomAssertions.assertEquals(4, scalarInt(connection, "SELECT COUNT(*) FROM `clan_data`"), "Old alliance fixture row count is wrong.");
				PhantomAssertions.assertEquals(3, scalarInt(connection, "SELECT COUNT(*) FROM `clan_wars`"), "Old war fixture row count is wrong.");

				applyExactMigration(connection, context.moduleRoot(), migrationFile);
				final Map<Integer, MigratedClan> clans = migratedClans(connection);
				final Map<String, MigratedWar> wars = migratedWars(connection);
				PhantomAssertions.assertEquals(4, clans.size(), "027C migration lost clan_data rows.");
				PhantomAssertions.assertEquals(3, wars.size(), "027C migration lost clan_wars rows.");
				verifyMigratedAllianceRows(clans);
				PhantomAssertions.assertEquals(1L, scalarLong(connection, "SELECT `high_water` FROM `clan_social_identity` WHERE `identity_name`='alliance_incarnation'"), "027C migration did not initialize the durable alliance high-water.");
				verifyMigratedWarRows(wars);
				verifyMigrationKeys(connection);
				verifyCanonicalRestore(clans, wars);
				verifyMariaDbWarIncarnation(wars);
			}
			finally
			{
				if (backupsCreated)
				{
					restoreFreshSchemaTables(connection);
				}
			}
			PhantomAssertions.assertEquals(originalClanRows, scalarInt(connection, "SELECT COUNT(*) FROM `clan_data`"), "Rehearsal did not restore original fresh clan_data.");
			PhantomAssertions.assertEquals(originalWarRows, scalarInt(connection, "SELECT COUNT(*) FROM `clan_wars`"), "Rehearsal did not restore original fresh clan_wars.");
			PhantomAssertions.assertEquals(originalAllianceHighWater, scalarLong(connection, "SELECT `high_water` FROM `clan_social_identity` WHERE `identity_name`='alliance_incarnation'"), "Rehearsal did not restore original alliance high-water.");
		}
	}

	static ClanAllianceService.Result joinWithCurrentEpoch(ClanAllianceService service, Actor inviter, Actor target, AllianceIdentity identity)
	{
		final ClanAllianceService.Result permit = service.checkInvite(inviter, target);
		if (!permit.successful())
		{
			return permit;
		}
		return service.join(inviter, target, identity, permit.targetEpoch());
	}
	static ClanAllianceService allianceService(ClanSocialPersistence persistence, ClanAllianceService.StateAccess state)
	{
		return new ClanAllianceService(persistence, state, new ClanSocialMutationFence(16), () -> NOW, true);
	}

	static ClanWarService warService(ClanSocialPersistence persistence, ClanWarService.StateAccess state)
	{
		return new ClanWarService(persistence, state, new ClanSocialMutationFence(16), () -> NOW, true);
	}

	private static String read(Path root, String relative) throws Exception
	{
		return Files.readString(root.resolve(relative));
	}

	private static List<String> allianceAdapters(Path root) throws Exception
	{
		final List<String> sources = new ArrayList<>();
		for (String relative : List.of(
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinAlly.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinAlly.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/AllyLeave.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/AllyDismiss.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestDismissAlly.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestSetAllyCrest.java"))
		{
			sources.add(read(root, relative));
		}
		return sources;
	}

	private static List<String> warAdapters(Path root) throws Exception
	{
		final List<String> sources = new ArrayList<>();
		for (String relative : List.of(
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestStartPledgeWar.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestStopPledgeWar.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestSurrenderPledgeWar.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestReplyStartPledgeWar.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestReplyStopPledgeWar.java",
			"java/org/l2jmobius/gameserver/network/clientpackets/RequestReplySurrenderPledgeWar.java"))
		{
			sources.add(read(root, relative));
		}
		return sources;
	}

	static final class AllianceStateAccess implements ClanAllianceService.StateAccess
	{
		private final Map<Integer, ClanSnapshot> _clans = new HashMap<>();
		private boolean failNotifications;

		static AllianceStateAccess standard(int... clanIds)
		{
			final AllianceStateAccess state = new AllianceStateAccess();
			for (int clanId : clanIds)
			{
				state.putClan(clanId, switch (clanId)
				{
					case 1 -> "AlphaClan";
					case 2 -> "BetaClan";
					case 3 -> "GammaClan";
					default -> "DeltaClan";
				}, 5, 0, null, 0, 0, 0, 0, 0, 0);
			}
			return state;
		}

		void putClan(int clanId, String name, int level, int allianceId, String allianceName, long generation, int crestId, long penaltyExpiry, int penaltyType, long dissolvingExpiry, long generationCounter)
		{
			_clans.put(clanId, new ClanSnapshot(clanId, name, level, allianceId, allianceName, generation, generationCounter, crestId, penaltyExpiry, penaltyType, dissolvingExpiry));
		}

		@Override
		public ClanSnapshot clan(int clanId)
		{
			return _clans.get(clanId);
		}

		@Override
		public ClanSnapshot clanByName(String clanName)
		{
			return _clans.values().stream().filter(clan -> clan.clanName().equalsIgnoreCase(clanName)).findFirst().orElse(null);
		}

		@Override
		public List<ClanSnapshot> allies(int allianceId)
		{
			return _clans.values().stream().filter(clan -> clan.allianceId() == allianceId).toList();
		}

		@Override
		public boolean allianceNameExists(String allianceName)
		{
			return _clans.values().stream().anyMatch(clan -> (clan.allianceId() == clan.clanId()) && (clan.allianceName() != null) && clan.allianceName().equalsIgnoreCase(allianceName));
		}

		@Override
		public boolean atWar(int sourceClanId, int targetClanId)
		{
			return false;
		}

		@Override
		public void apply(AllianceState mutation)
		{
			final ClanSnapshot old = _clans.get(mutation.clanId());
			_clans.put(mutation.clanId(), new ClanSnapshot(old.clanId(), old.clanName(), old.level(), mutation.allianceId(), mutation.allianceName(), mutation.generation(), mutation.generationCounter(), mutation.crestId(), mutation.penaltyExpiryTime(), mutation.penaltyType(), old.dissolvingExpiryTime()));
		}

		@Override
		public void broadcastUserInfo(int clanId)
		{
			if (failNotifications)
			{
				throw new IllegalStateException("controlled alliance user-info notification failure");
			}
		}

		@Override
		public void broadcastDissolved(List<Integer> clanIds)
		{
			if (failNotifications)
			{
				throw new IllegalStateException("controlled alliance dissolve notification failure");
			}
		}

		@Override
		public void removeCrest(int crestId)
		{
			if (failNotifications)
			{
				throw new IllegalStateException("controlled alliance crest notification failure");
			}
		}
	}

	static final class WarStateAccess implements ClanWarService.StateAccess
	{
		private final Map<Integer, ClanWarService.ClanSnapshot> _clans = new HashMap<>();
		private final Set<String> _active = new HashSet<>();
		private boolean attackStance;
		private boolean failNotifications;
		private String _rebindName;
		private int _rebindTargetClanId;
		private int _nameLookups;

		static WarStateAccess standard()
		{
			final WarStateAccess state = new WarStateAccess();
			state.putClan(1, "AlphaClan", 5, 50, 0, 0);
			state.putClan(2, "BetaClan", 5, 50, 0, 0);
			state.putClan(3, "GammaClan", 5, 50, 0, 0);
			return state;
		}

		void putClan(int clanId, String name, int level, int members, int allianceId, long dissolvingExpiry)
		{
			_clans.put(clanId, new ClanWarService.ClanSnapshot(clanId, name, level, members, allianceId, dissolvingExpiry));
		}

		@Override
		public ClanWarService.ClanSnapshot clan(int clanId)
		{
			return _clans.get(clanId);
		}

		void rebindOnSecondLookup(String clanName, int targetClanId)
		{
			_rebindName = clanName;
			_rebindTargetClanId = targetClanId;
			_nameLookups = 0;
		}

		@Override
		public ClanWarService.ClanSnapshot clanByName(String clanName)
		{
			if ((_rebindName != null) && _rebindName.equalsIgnoreCase(clanName) && (++_nameLookups == 2))
			{
				return _clans.get(_rebindTargetClanId);
			}
			return _clans.values().stream().filter(clan -> clan.clanName().equalsIgnoreCase(clanName)).findFirst().orElse(null);
		}

		@Override
		public boolean sourceAtWarWith(int sourceClanId, int targetClanId)
		{
			return _active.contains(pair(sourceClanId, targetClanId));
		}

		@Override
		public boolean hasAttackStance(int clanId)
		{
			return attackStance;
		}

		@Override
		public void startWar(ClanWarService.WarIdentity identity)
		{
			_active.add(pair(identity.sourceClanId(), identity.targetClanId()));
		}

		@Override
		public void notifyWarStarted(ClanWarService.WarIdentity identity)
		{
			if (failNotifications)
			{
				throw new IllegalStateException("controlled war start notification failure");
			}
		}

		@Override
		public void endWar(ClanWarService.WarIdentity identity)
		{
			_active.remove(pair(identity.sourceClanId(), identity.targetClanId()));
		}

		@Override
		public void notifyWarEnded(ClanWarService.WarIdentity identity, boolean announce)
		{
			if (failNotifications)
			{
				throw new IllegalStateException("controlled war end notification failure");
			}
		}

		@Override
		public void restoreWar(ClanWarService.WarIdentity identity)
		{
			_active.add(pair(identity.sourceClanId(), identity.targetClanId()));
		}
	}

	static final class FakePersistence implements ClanSocialPersistence
	{
		private final Map<Integer, DurableAlliance> _alliances = new HashMap<>();
		private final Map<String, ClanSocialRepository.WarRow> _wars = new HashMap<>();
		private long _nextAllianceGeneration = 1;
		private long _nextWarId = 100;
		private boolean failAllianceWrites;
		private boolean failWarCreate;
		private boolean failWarDelete;

		void addClan(int clanId)
		{
			_alliances.put(clanId, new DurableAlliance(clanId, 0, null, 0, 0, 0, 0, 0));
		}

		DurableAlliance alliance(int clanId)
		{
			return _alliances.get(clanId);
		}

		ClanSocialRepository.WarRow war(int sourceClanId, int targetClanId)
		{
			return _wars.get(pair(sourceClanId, targetClanId));
		}
		void restoreWar(ClanSocialRepository.WarRow war)
		{
			_wars.put(pair(war.sourceClanId(), war.targetClanId()), war);
			_nextWarId = Math.max(_nextWarId, war.warId() + 1);
		}

		AllianceStateAccess restoreAllianceState(Map<Integer, String> names)
		{
			final AllianceStateAccess state = new AllianceStateAccess();
			for (DurableAlliance row : _alliances.values())
			{
				state.putClan(row.clanId(), names.get(row.clanId()), 5, row.allianceId(), row.allianceName(), row.generation(), row.crestId(), row.penaltyExpiry(), row.penaltyType(), 0, row.generationCounter());
			}
			return state;
		}

		@Override
		public long createAlliance(int leaderClanId, long expectedGeneration, long expectedGenerationCounter, String allianceName) throws SQLException, StaleStateException
		{
			failAlliance();
			final DurableAlliance current = requiredAlliance(leaderClanId);
			if ((current.allianceId() != 0) || (current.generation() != expectedGeneration) || (current.generationCounter() != expectedGenerationCounter))
			{
				throw new StaleStateException("create changed");
			}
			final long generation = _nextAllianceGeneration++;
			final long nextEpoch = Math.addExact(expectedGenerationCounter, 1);
			_alliances.put(leaderClanId, new DurableAlliance(leaderClanId, leaderClanId, allianceName, generation, nextEpoch, current.crestId(), 0, 0));
			return generation;
		}

		@Override
		public void joinAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, String allianceName, int allianceCrestId, long targetExpectedGeneration, long targetExpectedGenerationCounter) throws SQLException, StaleStateException
		{
			failAlliance();
			requireIdentity(requiredAlliance(leaderClanId), allianceId, generation);
			final DurableAlliance target = requiredAlliance(targetClanId);
			if ((target.allianceId() != 0) || (target.generation() != targetExpectedGeneration) || (target.generationCounter() != targetExpectedGenerationCounter))
			{
				throw new StaleStateException("join changed");
			}
			_alliances.put(targetClanId, new DurableAlliance(targetClanId, allianceId, allianceName, generation, Math.addExact(target.generationCounter(), 1), allianceCrestId, 0, 0));
		}

		@Override
		public void leaveAlliance(int clanId, int allianceId, long generation, long expectedGenerationCounter, long penaltyExpiryTime, int penaltyType) throws SQLException, StaleStateException
		{
			failAlliance();
			final DurableAlliance current = requiredAlliance(clanId);
			requireIdentity(current, allianceId, generation);
			if (current.generationCounter() != expectedGenerationCounter)
			{
				throw new StaleStateException("leave epoch changed");
			}
			_alliances.put(clanId, new DurableAlliance(clanId, 0, null, 0, Math.addExact(current.generationCounter(), 1), 0, penaltyExpiryTime, penaltyType));
		}

		@Override
		public void expelAlliance(int leaderClanId, int targetClanId, int allianceId, long generation, long leaderExpectedGenerationCounter, long targetExpectedGenerationCounter, long leaderPenaltyExpiryTime, long targetPenaltyExpiryTime, int targetPenaltyType) throws SQLException, StaleStateException
		{
			failAlliance();
			final DurableAlliance leader = requiredAlliance(leaderClanId);
			final DurableAlliance target = requiredAlliance(targetClanId);
			requireIdentity(leader, allianceId, generation);
			requireIdentity(target, allianceId, generation);
			if ((leader.generationCounter() != leaderExpectedGenerationCounter) || (target.generationCounter() != targetExpectedGenerationCounter))
			{
				throw new StaleStateException("expel epoch changed");
			}
			_alliances.put(leaderClanId, new DurableAlliance(leaderClanId, allianceId, leader.allianceName(), generation, leader.generationCounter(), leader.crestId(), leaderPenaltyExpiryTime, Clan.PENALTY_TYPE_DISMISS_CLAN));
			_alliances.put(targetClanId, new DurableAlliance(targetClanId, 0, null, 0, Math.addExact(target.generationCounter(), 1), 0, targetPenaltyExpiryTime, targetPenaltyType));
		}

		@Override
		public void dissolveAlliance(int leaderClanId, int allianceId, long generation, Map<Integer, Long> memberGenerationCounters, long leaderPenaltyExpiryTime) throws SQLException, StaleStateException
		{
			failAlliance();
			final Set<Integer> durableMembers = new HashSet<>();
			for (DurableAlliance row : _alliances.values())
			{
				if (row.allianceId() == allianceId)
				{
					requireIdentity(row, allianceId, generation);
					if (!Long.valueOf(row.generationCounter()).equals(memberGenerationCounters.get(row.clanId())))
					{
						throw new StaleStateException("member epoch changed");
					}
					durableMembers.add(row.clanId());
				}
			}
			if (!durableMembers.equals(memberGenerationCounters.keySet()))
			{
				throw new StaleStateException("members changed");
			}
			for (Map.Entry<Integer, Long> member : memberGenerationCounters.entrySet())
			{
				final int clanId = member.getKey();
				_alliances.put(clanId, new DurableAlliance(clanId, 0, null, 0, Math.addExact(member.getValue(), 1), 0, clanId == leaderClanId ? leaderPenaltyExpiryTime : 0, clanId == leaderClanId ? Clan.PENALTY_TYPE_DISSOLVE_ALLY : 0));
			}
		}
		@Override
		public void repairOrphanAlliance(int clanId, int allianceId, long generation, long expectedGenerationCounter) throws SQLException, StaleStateException
		{
			failAlliance();
			final DurableAlliance row = requiredAlliance(clanId);
			requireIdentity(row, allianceId, generation);
			if (row.generationCounter() != expectedGenerationCounter)
			{
				throw new StaleStateException("repair epoch changed");
			}
			_alliances.put(clanId, new DurableAlliance(clanId, 0, null, 0, Math.addExact(row.generationCounter(), 1), 0, row.penaltyExpiry(), row.penaltyType()));
		}

		@Override
		public void changeAllianceCrest(int allianceId, long generation, List<Integer> memberClanIds, int crestId) throws SQLException, StaleStateException
		{
			failAlliance();
			for (int clanId : memberClanIds)
			{
				final DurableAlliance row = requiredAlliance(clanId);
				requireIdentity(row, allianceId, generation);
				_alliances.put(clanId, new DurableAlliance(clanId, allianceId, row.allianceName(), generation, row.generationCounter(), crestId, row.penaltyExpiry(), row.penaltyType()));
			}
		}

		@Override
		public void clearClanAllianceCrest(int clanId) throws SQLException, StaleStateException
		{
			failAlliance();
			final DurableAlliance row = requiredAlliance(clanId);
			_alliances.put(clanId, new DurableAlliance(clanId, row.allianceId(), row.allianceName(), row.generation(), row.generationCounter(), 0, row.penaltyExpiry(), row.penaltyType()));
		}

		@Override
		public List<ClanSocialRepository.WarRow> loadWars() throws SQLException
		{
			return _wars.values().stream().sorted(java.util.Comparator.comparingLong(ClanSocialRepository.WarRow::warId)).toList();
		}

		@Override
		public ClanSocialRepository.WarRow createWar(int sourceClanId, int targetClanId) throws SQLException, StaleStateException
		{
			if (failWarCreate)
			{
				throw new SQLException("controlled war insert failure");
			}
			final String pair = pair(sourceClanId, targetClanId);
			if (_wars.containsKey(pair))
			{
				throw new StaleStateException("war exists");
			}
			final ClanSocialRepository.WarRow row = new ClanSocialRepository.WarRow(_nextWarId++, sourceClanId, targetClanId);
			_wars.put(pair, row);
			return row;
		}

		@Override
		public void deleteWar(ClanSocialRepository.WarRow war) throws SQLException, StaleStateException
		{
			if (failWarDelete)
			{
				throw new SQLException("controlled war delete failure");
			}
			final String pair = pair(war.sourceClanId(), war.targetClanId());
			if (!_wars.remove(pair, war))
			{
				throw new StaleStateException("war identity changed");
			}
		}

		@Override
		public void deleteWars(Collection<ClanSocialRepository.WarRow> wars) throws SQLException, StaleStateException
		{
			if (failWarDelete)
			{
				throw new SQLException("controlled war batch delete failure");
			}
			for (ClanSocialRepository.WarRow war : wars)
			{
				if (!_wars.containsKey(pair(war.sourceClanId(), war.targetClanId())) || !_wars.get(pair(war.sourceClanId(), war.targetClanId())).equals(war))
				{
					throw new StaleStateException("war batch identity changed");
				}
			}
			for (ClanSocialRepository.WarRow war : wars)
			{
				_wars.remove(pair(war.sourceClanId(), war.targetClanId()));
			}
		}

		private void failAlliance() throws SQLException
		{
			if (failAllianceWrites)
			{
				throw new SQLException("controlled alliance persistence failure");
			}
		}

		private DurableAlliance requiredAlliance(int clanId) throws StaleStateException
		{
			final DurableAlliance row = _alliances.get(clanId);
			if (row == null)
			{
				throw new StaleStateException("missing clan row");
			}
			return row;
		}

		private static void requireIdentity(DurableAlliance row, int allianceId, long generation) throws StaleStateException
		{
			if ((row.allianceId() != allianceId) || (row.generation() != generation))
			{
				throw new StaleStateException("alliance identity changed");
			}
		}
	}

	private record DurableAlliance(int clanId, int allianceId, String allianceName, long generation, long generationCounter, int crestId, long penaltyExpiry, int penaltyType)
	{
	}

	private static String pair(int sourceClanId, int targetClanId)
	{
		return sourceClanId + ":" + targetClanId;
	}
}

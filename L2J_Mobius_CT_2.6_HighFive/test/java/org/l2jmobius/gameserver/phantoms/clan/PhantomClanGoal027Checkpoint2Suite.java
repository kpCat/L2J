/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceMembershipProof;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipEpoch;
import org.l2jmobius.gameserver.model.clan.ClanInvitationService;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.model.clan.ClanWarService.WarIdentity;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.AdvanceResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.AllianceObservation;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.Backend;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ChatOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ChatResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ClanSnapshot;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionObservation;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionState;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.CreationResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.DiplomacyAction;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.DiplomacyPhase;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.DiplomacyState;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.MemberRef;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.OperationStatus;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.OrganizationMetadata;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.PersistencePort;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RelationshipEvidence;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleKey;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.StoredMetadata;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.WithdrawalOutcome;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestContext;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

public final class PhantomClanGoal027Checkpoint2Suite implements PhantomTestSuite
{
	private static final long SEED = 27002702L;
	private static final long NOW = 600_000L;

	@Override
	public String id()
	{
		return "clan-checkpoint2-goal027";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal 027 CP2 suite used the wrong seed.");
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-alliance-create-restart", this::allianceCreateRestart);
		registry.add("02-bilateral-join-aba-real-safety", this::bilateralJoin);
		registry.add("03-exact-leave-proof-dissolve", this::leaveAndDissolve);
		registry.add("04-war-declare-restart-evidence", this::warDeclare);
		registry.add("05-exact-stop-bilateral-peace-war-id", this::stopAndPeace);
		registry.add("06-relation-idempotency-hysteresis", this::relationAndHysteresis);
		registry.add("07-alliance-chat-store-v1", this::allianceChatAndStore);
		registry.add("08-native-failures-source-bounds", this::failureAndSourceGuard);
	}

	private void allianceCreateRestart(PhantomTestContext context)
	{
		final Fixture fixture = new Fixture();
		fixture.backend.addManagedClan(1, 101, 11, "Alpha");
		fixture.goals.put(1, goal(1, 100, PhantomClanService.ALLIANCE_CREATE_GOAL, ref("alliance.name", "CodexAlly"), List.of(), null, 0));
		final AdvanceResult created = fixture.service().advance(1, 100, 0);
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, created.status(), "Alliance create did not complete.");
		final AllianceIdentity generationOne = fixture.backend.clans.get(11).alliance;
		PhantomAssertions.assertEquals(1, fixture.backend.createAllianceCalls, "Alliance create was not canonical once.");
		final AdvanceResult restarted = fixture.service().advance(1, 100, 0);
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, restarted.status(), "Alliance create did not reconcile after restart.");
		PhantomAssertions.assertEquals(generationOne, fixture.backend.clans.get(11).alliance, "Restart created G2.");
		PhantomAssertions.assertEquals(1, fixture.backend.createAllianceCalls, "Restart repeated native create.");
	}

	private void bilateralJoin(PhantomTestContext context)
	{
		final Fixture joined = alliedLeaderAndTarget();
		joined.goals.put(1, peerGoal(1, 110, PhantomClanService.ALLIANCE_JOIN_GOAL, 22, 2, 0));
		joined.goals.put(2, peerGoal(2, 120, PhantomClanService.ALLIANCE_JOIN_GOAL, 11, 1, 0));
		final PhantomClanService joinedService = joined.service();
		PhantomAssertions.assertEquals(OperationStatus.WAITING, joinedService.advance(1, 110, 0).status(), "Source pulse did not publish a join offer.");
		PhantomAssertions.assertEquals(OperationStatus.WAITING, joinedService.advance(1, 110, 0).status(), "Repeated source pulse did not preserve pending exact offer.");
		PhantomAssertions.assertEquals(1, joined.backend.checkJoinCalls, "Repeated source pulse silently refreshed captured target epoch.");
		PhantomAssertions.assertEquals(0, joined.backend.joinCalls, "Source pulse mutated target membership.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, joinedService.advance(2, 120, 0).status(), "Later target consent did not join.");
		PhantomAssertions.assertEquals(1, joined.backend.joinCalls, "Exact join was not called once.");
		PhantomAssertions.assertEquals(joined.backend.clans.get(11).alliance, joined.backend.clans.get(22).alliance, "Target did not join exact G1.");

		final Fixture aba = alliedLeaderAndTarget();
		aba.goals.put(1, peerGoal(1, 111, PhantomClanService.ALLIANCE_JOIN_GOAL, 22, 2, 0));
		aba.goals.put(2, peerGoal(2, 121, PhantomClanService.ALLIANCE_JOIN_GOAL, 11, 1, 0));
		final PhantomClanService abaService = aba.service();
		abaService.advance(1, 111, 0);
		aba.backend.clans.get(22).membershipCounter++;
		PhantomAssertions.assertEquals(OperationStatus.STALE, abaService.advance(2, 121, 0).status(), "Old target MembershipEpoch was not stale.");
		PhantomAssertions.assertEquals(null, aba.backend.clans.get(22).alliance, "Stale join mutated target.");

		final Fixture real = alliedLeaderAndTarget();
		real.backend.addRealClan(303, 33, "RealOnly");
		real.goals.put(1, goal(1, 112, PhantomClanService.ALLIANCE_JOIN_GOAL, ref("clan.id", "33"), List.of(ref("character.object", "303")), null, 0));
		PhantomAssertions.assertEquals(OperationStatus.UNSUPPORTED, real.service().advance(1, 112, 0).status(), "REAL-only target entered autonomous consent.");
		PhantomAssertions.assertEquals(0, real.backend.checkJoinCalls, "REAL-only target reached native join check.");
	}

	private void leaveAndDissolve(PhantomTestContext context)
	{
		final Fixture leave = alliedPair();
		leave.goals.put(2, peerGoal(2, 130, PhantomClanService.ALLIANCE_LEAVE_GOAL, 11, 1, 0));
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, leave.service().advance(2, 130, 0).status(), "Exact alliance leave failed.");
		PhantomAssertions.assertEquals(null, leave.backend.clans.get(22).alliance, "Leave did not detach exact member.");
		PhantomAssertions.assertEquals(1, leave.backend.leaveCalls, "Native leave call count mismatch.");

		final Fixture dissolve = alliedPair();
		dissolve.goals.put(1, goal(1, 131, PhantomClanService.ALLIANCE_DISSOLVE_GOAL, ref("alliance.name", "CodexAlly"), List.of(ref("profile", "2")), null, 0));
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, dissolve.service().advance(1, 131, 0).status(), "Exact managed proof did not dissolve.");
		PhantomAssertions.assertEquals(1, dissolve.backend.dissolveCalls, "dissolveWithProof was not called exactly once.");
		PhantomAssertions.assertEquals(null, dissolve.backend.clans.get(11).alliance, "Alliance leader remained allied after dissolve.");

		final Fixture unexpected = alliedPair();
		unexpected.backend.addRealClan(303, 33, "UnexpectedC");
		unexpected.backend.attach(33, unexpected.backend.clans.get(11).alliance, "CodexAlly");
		unexpected.goals.put(1, goal(1, 132, PhantomClanService.ALLIANCE_DISSOLVE_GOAL, ref("alliance.name", "CodexAlly"), List.of(ref("profile", "2")), null, 0));
		final AdvanceResult refused = unexpected.service().advance(1, 132, 0);
		PhantomAssertions.assertEquals(OperationStatus.WAITING, refused.status(), "Unexpected canonical C did not block dissolve.");
		PhantomAssertions.assertEquals(0, unexpected.backend.dissolveCalls, "Unexpected/REAL C reached dissolveWithProof.");
		PhantomAssertions.assertTrue(unexpected.backend.clans.get(33).alliance != null, "Blocked dissolve mutated REAL C.");

		final Fixture aba = alliedPair();
		aba.backend.beforeDissolve = () ->
		{
			final AllianceIdentity generationTwo = new AllianceIdentity(11, ++aba.backend.allianceGeneration);
			aba.backend.attach(11, generationTwo, "CodexAlly");
			aba.backend.attach(22, generationTwo, "CodexAlly");
		};
		aba.goals.put(1, goal(1, 133, PhantomClanService.ALLIANCE_DISSOLVE_GOAL, ref("alliance.name", "CodexAlly"), List.of(ref("profile", "2")), null, 0));
		PhantomAssertions.assertEquals(OperationStatus.STALE, aba.service().advance(1, 133, 0).status(), "Old G1 membership proof was accepted against G2.");
		PhantomAssertions.assertEquals(1, aba.backend.dissolveCalls, "ABA proof did not reach exact native proof fence.");
		PhantomAssertions.assertTrue(aba.backend.clans.get(11).alliance != null, "Old G1 proof mutated G2.");
		PhantomAssertions.assertEquals(aba.backend.clans.get(11).alliance, aba.backend.clans.get(22).alliance, "Old G1 proof split G2 membership.");
	}

	private void warDeclare(PhantomTestContext context)
	{
		final Fixture hostile = plainPair();
		hostile.backend.hostilityScore = 800;
		hostile.backend.hostileEvidence = true;
		hostile.goals.put(1, peerGoal(1, 140, PhantomClanService.WAR_DECLARE_GOAL, 22, 2, 0));
		final PhantomClanService service = hostile.service();
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, service.advance(1, 140, 0).status(), "Hostile evidence did not declare W1.");
		final long warId = hostile.backend.war.warId();
		PhantomAssertions.assertEquals(1, hostile.backend.declareCalls, "War declare was not called once.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, hostile.service().advance(1, 140, 0).status(), "Restart did not reconcile active same pair.");
		PhantomAssertions.assertEquals(warId, hostile.backend.war.warId(), "Restart changed W1.");
		PhantomAssertions.assertEquals(1, hostile.backend.declareCalls, "Restart redeclared active war.");

		final Fixture friendly = plainPair();
		friendly.backend.hostilityScore = -500;
		friendly.backend.hostileEvidence = true;
		friendly.goals.put(1, peerGoal(1, 141, PhantomClanService.WAR_DECLARE_GOAL, 22, 2, 0));
		PhantomAssertions.assertEquals(OperationStatus.WAITING, friendly.service().advance(1, 141, 0).status(), "Friendly evidence satisfied hostile gate.");
		PhantomAssertions.assertEquals(0, friendly.backend.declareCalls, "Friendly target was mutated.");

		final Fixture weak = plainPair();
		weak.backend.hostilityScore = 900;
		weak.backend.hostileEvidence = false;
		weak.goals.put(1, peerGoal(1, 142, PhantomClanService.WAR_DECLARE_GOAL, 22, 2, 0));
		PhantomAssertions.assertEquals(OperationStatus.WAITING, weak.service().advance(1, 142, 0).status(), "Score without concrete hostile event declared war.");
		PhantomAssertions.assertEquals(0, weak.backend.declareCalls, "Weak evidence reached native declare.");

		final Fixture real = plainPair();
		real.backend.addRealClan(303, 33, "RealOnly");
		real.goals.put(1, goal(1, 143, PhantomClanService.WAR_DECLARE_GOAL, ref("clan.id", "33"), List.of(ref("character.object", "303")), null, 0));
		PhantomAssertions.assertEquals(OperationStatus.UNSUPPORTED, real.service().advance(1, 143, 0).status(), "REAL-only clan reached autonomous war path.");
	}

	private void stopAndPeace(PhantomTestContext context)
	{
		final Fixture stop = plainPair();
		stop.backend.startWar(11, 22);
		final long stoppedWarId = stop.backend.war.warId();
		stop.goals.put(1, peerGoal(1, 150, PhantomClanService.WAR_STOP_GOAL, 22, 2, 0));
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, stop.service().advance(1, 150, 0).status(), "Exact direct stop failed.");
		PhantomAssertions.assertEquals(null, stop.backend.war, "Direct stop left W1 active.");
		PhantomAssertions.assertEquals(stoppedWarId, stop.backend.lastStoppedWarId, "Direct stop did not carry exact warId.");
		stop.backend.startWar(11, 22);
		final long replacementWarId = stop.backend.war.warId();
		PhantomAssertions.assertEquals(OperationStatus.STALE, stop.service().advance(1, 150, 0).status(), "Persisted W1 stop action was accepted against W2.");
		PhantomAssertions.assertEquals(replacementWarId, stop.backend.war.warId(), "Old W1 stop action mutated W2.");

		final Fixture peace = plainPair();
		peace.backend.startWar(11, 22);
		final long warOne = peace.backend.war.warId();
		peace.goals.put(1, peerGoal(1, 151, PhantomClanService.WAR_PEACE_GOAL, 22, 2, 0));
		peace.goals.put(2, peerGoal(2, 152, PhantomClanService.WAR_PEACE_GOAL, 11, 1, 0));
		final PhantomClanService peaceService = peace.service();
		PhantomAssertions.assertEquals(OperationStatus.WAITING, peaceService.advance(1, 151, 0).status(), "Peace source did not publish W1 offer.");
		PhantomAssertions.assertEquals(OperationStatus.WAITING, peaceService.advance(1, 151, 0).status(), "Repeated peace source pulse refreshed W1 offer.");
		peace.backend.startWar(11, 22);
		final long warTwo = peace.backend.war.warId();
		PhantomAssertions.assertTrue(warTwo != warOne, "Fake war identity did not advance to W2.");
		PhantomAssertions.assertEquals(OperationStatus.STALE, peaceService.advance(2, 152, 0).status(), "W1 peace offer was not stale in W2.");
		PhantomAssertions.assertEquals(warTwo, peace.backend.war.warId(), "Stale W1 peace affected W2.");
		PhantomAssertions.assertEquals(OperationStatus.WAITING, peaceService.advance(1, 151, 0).status(), "W2 peace offer was not published on a later replan.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, peaceService.advance(2, 152, 0).status(), "Later bilateral W2 peace failed.");
		PhantomAssertions.assertEquals(null, peace.backend.war, "Accepted peace left W2 active.");
		PhantomAssertions.assertEquals(warTwo, peace.backend.lastPeaceWarId, "Accepted peace did not carry exact W2 id.");
	}

	private void relationAndHysteresis(PhantomTestContext context)
	{
		final Fixture fixture = plainPair();
		fixture.backend.hostilityScore = 800;
		fixture.backend.hostileEvidence = true;
		fixture.goals.put(1, peerGoal(1, 160, PhantomClanService.WAR_DECLARE_GOAL, 22, 2, 0));
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, fixture.service().advance(1, 160, 0).status(), "War relation fixture did not declare.");
		PhantomAssertions.assertEquals(2, fixture.backend.socialReceipts.size(), "Canonical war outcome did not emit bilateral Goal018 events.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, fixture.service().advance(1, 160, 0).status(), "Restart relation reconciliation failed.");
		PhantomAssertions.assertEquals(2, fixture.backend.socialReceipts.size(), "Restart duplicated Goal018 effects.");
		fixture.goals.put(1, peerGoal(1, 161, PhantomClanService.WAR_STOP_GOAL, 22, 2, 1));
		final PhantomClanService restarted = fixture.service();
		PhantomAssertions.assertEquals(OperationStatus.WAITING, restarted.advance(1, 161, 1).status(), "Immediate inverse action bypassed persisted hysteresis.");
		PhantomAssertions.assertEquals(OperationStatus.WAITING, restarted.advance(1, 161, 1).status(), "Same inputs produced a different hysteresis decision.");
		PhantomAssertions.assertEquals(0, fixture.backend.stopCalls, "Hysteresis reached native stop.");
	}

	private void allianceChatAndStore(PhantomTestContext context) throws Exception
	{
		final Fixture fixture = alliedPair();
		fixture.goals.put(1, goal(1, 170, PhantomClanService.ALLIANCE_CHAT_GOAL, ref("alliance.name", "CodexAlly"), List.of(), "alliance-ready", 0));
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, fixture.service().advance(1, 170, 0).status(), "Alliance generated chat failed.");
		PhantomAssertions.assertEquals(1, fixture.backend.allianceChatCalls, "Alliance chat dispatch count mismatch.");
		PhantomAssertions.assertEquals(OperationStatus.COMPLETE, fixture.service().advance(1, 170, 0).status(), "Restart did not suppress duplicate alliance chat.");
		PhantomAssertions.assertEquals(1, fixture.backend.allianceChatCalls, "Restart duplicated alliance chat.");
		fixture.backend.attach(11, new AllianceIdentity(11, 2), "CodexAlly");
		PhantomAssertions.assertEquals(OperationStatus.STALE, fixture.service().advance(1, 170, 0).status(), "G1 chat action entered G2.");
		PhantomAssertions.assertEquals(1, fixture.backend.allianceChatCalls, "G1 action dispatched into G2.");

		final OrganizationMetadata original = baseMetadata(DiplomacyState.empty());
		final PhantomProfileComponent legacy = new PhantomProfileComponent(1, PhantomClanStore.COMPONENT_TYPE, PhantomClanStore.LEGACY_SCHEMA_VERSION, 7, legacyPayload(original), Instant.EPOCH, Instant.EPOCH);
		final StoredMetadata decodedV1 = PhantomClanStore.decode(legacy);
		PhantomAssertions.assertEquals(original.canonicalClanId(), decodedV1.metadata().canonicalClanId(), "v1 clan id was lost.");
		PhantomAssertions.assertEquals(original.relationReferences(), decodedV1.metadata().relationReferences(), "v1 relation refs were lost.");
		PhantomAssertions.assertEquals(DiplomacyAction.NONE, decodedV1.metadata().diplomacy().action(), "v1 did not default diplomacy safely.");
		final DiplomacyState current = new DiplomacyState(DiplomacyAction.WAR_DECLARE, DiplomacyPhase.COMPLETED, 7, 2, 22, 0, 0, 0, 77, 4, NOW + 1000, NOW / 60_000, PhantomClanService.sha256("codec"));
		final OrganizationMetadata evolved = copyWithDiplomacy(decodedV1.metadata(), current);
		final PhantomProfileComponent v2 = new PhantomProfileComponent(1, PhantomClanStore.COMPONENT_TYPE, PhantomClanStore.SCHEMA_VERSION, 8, PhantomClanStore.encode(evolved), Instant.EPOCH, Instant.EPOCH);
		final OrganizationMetadata roundTrip = PhantomClanStore.decode(v2).metadata();
		PhantomAssertions.assertEquals(original.clanName(), roundTrip.clanName(), "v2 save lost CP1 clan name.");
		PhantomAssertions.assertEquals(original.contributionState(), roundTrip.contributionState(), "v2 save lost CP1 contribution state.");
		PhantomAssertions.assertEquals(current, roundTrip.diplomacy(), "v2 diplomacy did not round-trip.");
	}

	private void failureAndSourceGuard(PhantomTestContext context) throws Exception
	{
		final Fixture failed = new Fixture();
		failed.backend.addManagedClan(1, 101, 11, "Alpha");
		failed.backend.nextAllianceFailure = ClanAllianceService.Status.PERSISTENCE_FAILURE;
		failed.goals.put(1, goal(1, 180, PhantomClanService.ALLIANCE_CREATE_GOAL, ref("alliance.name", "FailureAlly"), List.of(), null, 0));
		PhantomAssertions.assertEquals(OperationStatus.REPLAN, failed.service().advance(1, 180, 0).status(), "Native persistence failure became fake completion.");
		PhantomAssertions.assertEquals(null, failed.backend.clans.get(11).alliance, "Failed native create mutated canonical state.");

		final String service = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanService.java"));
		final String backend = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/clan/L2jPhantomClanBackend.java"));
		final String decision = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/clan/PhantomClanDecision.java"));
		final String combined = service + backend;
		for (String forbidden : List.of("getClanAllies(", "getClans(", "_clans.values()", "RequestStartPledgeWar", "RequestReplyStopPledgeWar", "java.sql."))
		{
			PhantomAssertions.assertTrue(!combined.contains(forbidden), "Forbidden CP2 source contract found: " + forbidden);
		}
		PhantomAssertions.assertTrue(backend.contains("dissolveWithProof") && !backend.contains("_alliances.dissolve("), "Autonomous dissolve bypasses proof command.");
		PhantomAssertions.assertTrue(backend.contains("ChatType.ALLIANCE") && backend.contains("openGeneratedDispatch"), "Alliance chat does not use Goal020 generated dispatch.");
		PhantomAssertions.assertTrue(decision.contains(PhantomClanDecision.WAR_PEACE_ACTION) && decision.contains(PhantomClanDecision.ALLIANCE_CHAT_ACTION), "CP2 exact action keys are not registered.");
		PhantomAssertions.assertEquals(64, PhantomClanService.MAX_ACTIVE_OPERATIONS, "Active bound changed.");
		PhantomAssertions.assertEquals(256, PhantomClanService.MAX_TERMINAL_RECEIPTS, "Terminal bound changed.");
		PhantomAssertions.assertEquals(16, PhantomClanService.MAX_RELATION_REFERENCES, "Relation ref bound changed.");
	}

	private static Fixture plainPair()
	{
		final Fixture fixture = new Fixture();
		fixture.backend.addManagedClan(1, 101, 11, "Alpha");
		fixture.backend.addManagedClan(2, 202, 22, "Beta");
		return fixture;
	}

	private static Fixture alliedLeaderAndTarget()
	{
		final Fixture fixture = plainPair();
		fixture.backend.attach(11, new AllianceIdentity(11, 1), "CodexAlly");
		return fixture;
	}

	private static Fixture alliedPair()
	{
		final Fixture fixture = alliedLeaderAndTarget();
		fixture.backend.attach(22, fixture.backend.clans.get(11).alliance, "CodexAlly");
		return fixture;
	}

	private static PhantomGoal peerGoal(long profileId, long goalId, String type, int targetClanId, long peerProfileId, long revision)
	{
		return goal(profileId, goalId, type, ref("clan.id", Integer.toString(targetClanId)), List.of(ref("profile", Long.toString(peerProfileId))), null, revision);
	}

	private static PhantomGoal goal(long profileId, long goalId, String type, PhantomDomainRef target, List<PhantomDomainRef> sources, String method, long revision)
	{
		final Map<String, Long> constraints = PhantomClanService.ALLIANCE_CHAT_GOAL.equals(type) ? Map.of(PhantomClanService.CHAT_TEXT_CONSTRAINT, (long) method.length()) : Map.of();
		return new PhantomGoal(goalId, type, PhantomGoalStatus.ACTIVE, ref("profile", Long.toString(profileId)), target, 1, 0, method, sources, null, "clan.diplomacy", 700, 0, 0, NOW + 100_000, constraints, "clan.test", revision);
	}

	private static PhantomDomainRef ref(String namespace, String key)
	{
		return new PhantomDomainRef(namespace, key);
	}

	private static OrganizationMetadata baseMetadata(DiplomacyState diplomacy)
	{
		return new OrganizationMetadata(11, "Alpha", 101, RoleKey.LEADER, 5, 1, 900, 77, 3, 10, 20, ContributionState.COMPLETED, List.of("profile:2"), PhantomClanService.sha256("canonical"), PhantomClanService.sha256("intent"), NOW, diplomacy);
	}

	private static OrganizationMetadata copyWithDiplomacy(OrganizationMetadata value, DiplomacyState diplomacy)
	{
		return new OrganizationMetadata(value.canonicalClanId(), value.clanName(), value.canonicalLeaderObjectId(), value.roleIntent(), value.organizationGoalId(), value.goalRevision(), value.contributionBudget(), value.contributionItemObjectId(), value.contributionAmount(), value.contributionInventoryBefore(), value.contributionWarehouseBefore(), value.contributionState(), value.relationReferences(), value.canonicalEvidenceHash(), value.intentEvidenceHash(), value.updatedEpochMillis(), diplomacy);
	}

	private static byte[] legacyPayload(OrganizationMetadata metadata) throws Exception
	{
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream output = new DataOutputStream(bytes))
		{
			output.writeInt(PhantomClanStore.LEGACY_SCHEMA_VERSION);
			output.writeInt(metadata.canonicalClanId());
			string(output, metadata.clanName());
			output.writeInt(metadata.canonicalLeaderObjectId());
			output.writeByte(metadata.roleIntent().ordinal());
			output.writeLong(metadata.organizationGoalId());
			output.writeLong(metadata.goalRevision());
			output.writeLong(metadata.contributionBudget());
			output.writeInt(metadata.contributionItemObjectId());
			output.writeLong(metadata.contributionAmount());
			output.writeLong(metadata.contributionInventoryBefore());
			output.writeLong(metadata.contributionWarehouseBefore());
			output.writeByte(metadata.contributionState().ordinal());
			output.writeByte(metadata.relationReferences().size());
			for (String reference : metadata.relationReferences())
			{
				string(output, reference);
			}
			string(output, metadata.canonicalEvidenceHash());
			string(output, metadata.intentEvidenceHash());
			output.writeLong(metadata.updatedEpochMillis());
		}
		return bytes.toByteArray();
	}

	private static void string(DataOutputStream output, String value) throws Exception
	{
		final byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		output.writeShort(bytes.length);
		output.write(bytes);
	}

	private static final class Fixture
	{
		private final FakeGoals goals = new FakeGoals();
		private final FakePersistence persistence = new FakePersistence();
		private final FakeBackend backend = new FakeBackend();

		private PhantomClanService service()
		{
			final PhantomClanService service = new PhantomClanService(goals, persistence, backend, () -> NOW);
			PhantomAssertions.assertTrue(service.start(), "CP2 clan service did not start.");
			return service;
		}
	}

	private static final class FakeGoals implements PhantomGoalStore
	{
		private final Map<Long, StoredGoal> values = new HashMap<>();

		private void put(long profileId, PhantomGoal goal)
		{
			final StoredGoal current = values.get(profileId);
			values.put(profileId, new StoredGoal(goal, current == null ? 0 : current.rowVersion() + 1));
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return profileId > 0;
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			return Optional.ofNullable(values.get(profileId));
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			put(profileId, goal);
			return values.get(profileId);
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			put(profileId, goal);
			return values.get(profileId);
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			values.remove(profileId);
		}
	}

	private static final class FakePersistence implements PersistencePort
	{
		private final Map<Long, StoredMetadata> values = new HashMap<>();

		@Override
		public Optional<StoredMetadata> load(long profileId)
		{
			return Optional.ofNullable(values.get(profileId));
		}

		@Override
		public StoredMetadata save(long profileId, long expectedRowVersion, OrganizationMetadata metadata)
		{
			final StoredMetadata current = values.get(profileId);
			if ((current == null) != (expectedRowVersion < 0))
			{
				throw new IllegalStateException("Unexpected clan metadata write mode.");
			}
			if ((current != null) && (current.rowVersion() != expectedRowVersion))
			{
				throw new IllegalStateException("Stale clan metadata write.");
			}
			final StoredMetadata stored = new StoredMetadata(current == null ? 0 : current.rowVersion() + 1, metadata);
			values.put(profileId, stored);
			return stored;
		}
	}

	private static final class FakeBackend implements Backend
	{
		private final Map<Long, MemberRef> profiles = new HashMap<>();
		private final Map<Integer, MemberRef> characters = new HashMap<>();
		private final Map<Integer, ClanData> clans = new HashMap<>();
		private final Set<String> socialReceipts = new HashSet<>();
		private long allianceGeneration;
		private long warSequence;
		private WarIdentity war;
		private ClanAllianceService.Status nextAllianceFailure;
		private int hostilityScore;
		private boolean hostileEvidence;
		private int createAllianceCalls;
		private int checkJoinCalls;
		private int joinCalls;
		private int leaveCalls;
		private int dissolveCalls;
		private int declareCalls;
		private int stopCalls;
		private int peaceCalls;
		private int allianceChatCalls;
		private long lastStoppedWarId;
		private long lastPeaceWarId;
		private Runnable beforeDissolve;

		private void addManagedClan(long profileId, int objectId, int clanId, String name)
		{
			final MemberRef member = MemberRef.phantom(profileId, objectId);
			profiles.put(profileId, member);
			characters.put(objectId, member);
			clans.put(clanId, new ClanData(clanId, name, objectId, true));
		}

		private void addRealClan(int objectId, int clanId, String name)
		{
			characters.put(objectId, MemberRef.real(objectId));
			clans.put(clanId, new ClanData(clanId, name, objectId, false));
		}

		private void attach(int clanId, AllianceIdentity identity, String name)
		{
			final ClanData clan = clans.get(clanId);
			clan.alliance = identity;
			clan.allianceName = name;
			clan.membershipCounter++;
		}

		private void startWar(int sourceClanId, int targetClanId)
		{
			final ClanData source = clans.get(sourceClanId);
			final ClanData target = clans.get(targetClanId);
			war = new WarIdentity(++warSequence, sourceClanId, targetClanId, source.name, target.name);
		}

		@Override
		public Optional<MemberRef> currentMember(long profileId)
		{
			return Optional.ofNullable(profiles.get(profileId));
		}

		@Override
		public Optional<MemberRef> resolve(PhantomDomainRef source)
		{
			try
			{
				return "profile".equals(source.namespace()) ? Optional.ofNullable(profiles.get(Long.parseLong(source.key()))) : "character.object".equals(source.namespace()) ? Optional.ofNullable(characters.get(Integer.parseInt(source.key()))) : Optional.empty();
			}
			catch (RuntimeException exception)
			{
				return Optional.empty();
			}
		}

		@Override
		public Optional<ClanSnapshot> observe(MemberRef member)
		{
			final ClanData clan = clan(member);
			return clan == null ? Optional.empty() : Optional.of(clan.snapshot(member.characterObjectId()));
		}

		@Override
		public CreationResult create(MemberRef actor, String clanName)
		{
			return new CreationResult(CreationOutcome.FAILED, null);
		}

		@Override
		public ClanInvitationService.InviteResult invite(MemberRef requester, MemberRef target)
		{
			return new ClanInvitationService.InviteResult(ClanInvitationService.InviteOutcome.JOIN_CONDITION_FAILED, null);
		}

		@Override
		public Optional<ClanInvitationService.InvitationSnapshot> observeInvitation(MemberRef invitee)
		{
			return Optional.empty();
		}

		@Override
		public ClanInvitationService.RespondResult respond(MemberRef invitee, ClanInvitationService.Response response, ClanInvitationService.InvitationIdentity identity)
		{
			return new ClanInvitationService.RespondResult(ClanInvitationService.RespondOutcome.NO_PENDING_INVITE, identity);
		}

		@Override
		public ClanInvitationService.CancelResult cancel(ClanInvitationService.InvitationIdentity identity)
		{
			return new ClanInvitationService.CancelResult(ClanInvitationService.CancelOutcome.NO_PENDING_INVITE, identity);
		}

		@Override
		public RoleResult transferLeader(MemberRef requester, MemberRef newLeader, int expectedClanId)
		{
			return new RoleResult(RoleOutcome.FAILED, null);
		}

		@Override
		public ContributionObservation observeContribution(MemberRef member, int expectedClanId, int inventoryObjectId)
		{
			return new ContributionObservation(false, 0, 0, 0, "");
		}

		@Override
		public ContributionResult contribute(MemberRef member, int expectedClanId, int inventoryObjectId, long count)
		{
			return new ContributionResult(ContributionOutcome.FAILED, 0, 0, "");
		}

		@Override
		public WithdrawalOutcome withdraw(MemberRef member, int expectedClanId, int warehouseObjectId, long count)
		{
			return WithdrawalOutcome.UNSUPPORTED;
		}

		@Override
		public ChatResult clanChat(MemberRef member, int expectedClanId, String text)
		{
			return new ChatResult(ChatOutcome.FAILED, 0);
		}

		@Override
		public Optional<AllianceObservation> observeAlliance(MemberRef member)
		{
			final ClanData clan = clan(member);
			return (clan == null) || (clan.alliance == null) ? Optional.empty() : Optional.of(new AllianceObservation(clan.alliance, clan.allianceName, clan.id));
		}

		@Override
		public ClanAllianceService.Result createAlliance(MemberRef actor, String allianceName)
		{
			createAllianceCalls++;
			if (nextAllianceFailure != null)
			{
				final ClanAllianceService.Status failure = nextAllianceFailure;
				nextAllianceFailure = null;
				return new ClanAllianceService.Result(failure, failure == ClanAllianceService.Status.PERSISTENCE_FAILURE ? ClanAllianceService.Reason.PERSISTENCE_ERROR : ClanAllianceService.Reason.STALE_IDENTITY, null);
			}
			final ClanData clan = clan(actor);
			final AllianceIdentity identity = new AllianceIdentity(clan.id, ++allianceGeneration);
			attach(clan.id, identity, allianceName);
			return new ClanAllianceService.Result(ClanAllianceService.Status.SUCCESS, ClanAllianceService.Reason.NONE, identity);
		}

		@Override
		public ClanAllianceService.Result checkAllianceJoin(MemberRef inviter, MemberRef target)
		{
			checkJoinCalls++;
			final ClanData leader = clan(inviter);
			final ClanData targetClan = clan(target);
			if ((leader == null) || (leader.alliance == null) || (leader.alliance.leaderClanId() != leader.id) || (targetClan == null) || (targetClan.alliance != null))
			{
				return allianceFailure(ClanAllianceService.Status.INELIGIBLE, ClanAllianceService.Reason.TARGET_ALREADY_ALLIED, null);
			}
			return new ClanAllianceService.Result(ClanAllianceService.Status.SUCCESS, ClanAllianceService.Reason.NONE, leader.alliance, new MembershipEpoch(targetClan.id, 0, 0, targetClan.membershipCounter));
		}

		@Override
		public ClanAllianceService.Result joinAlliance(MemberRef inviter, MemberRef target, AllianceIdentity identity, MembershipEpoch targetEpoch)
		{
			joinCalls++;
			final ClanData leader = clan(inviter);
			final ClanData targetClan = clan(target);
			if ((leader == null) || !identity.equals(leader.alliance) || (targetClan == null) || (targetClan.alliance != null) || !targetEpoch.equals(new MembershipEpoch(targetClan.id, 0, 0, targetClan.membershipCounter)))
			{
				return allianceFailure(ClanAllianceService.Status.STALE, ClanAllianceService.Reason.STALE_IDENTITY, identity);
			}
			attach(targetClan.id, identity, leader.allianceName);
			return new ClanAllianceService.Result(ClanAllianceService.Status.SUCCESS, ClanAllianceService.Reason.NONE, identity);
		}

		@Override
		public ClanAllianceService.Result leaveAlliance(MemberRef actor, AllianceIdentity identity)
		{
			leaveCalls++;
			final ClanData clan = clan(actor);
			if ((clan == null) || !identity.equals(clan.alliance))
			{
				return allianceFailure(ClanAllianceService.Status.STALE, ClanAllianceService.Reason.STALE_IDENTITY, identity);
			}
			clan.alliance = null;
			clan.allianceName = null;
			clan.membershipCounter++;
			return new ClanAllianceService.Result(ClanAllianceService.Status.SUCCESS, ClanAllianceService.Reason.NONE, identity);
		}

		@Override
		public ClanAllianceService.ProofResult captureAllianceMembership(AllianceIdentity identity)
		{
			final List<MembershipEpoch> epochs = clans.values().stream().filter(clan -> identity.equals(clan.alliance)).map(clan -> new MembershipEpoch(clan.id, identity.leaderClanId(), identity.generation(), clan.membershipCounter)).sorted(java.util.Comparator.comparingInt(MembershipEpoch::clanId)).toList();
			return epochs.isEmpty() ? new ClanAllianceService.ProofResult(ClanAllianceService.Status.STALE, ClanAllianceService.Reason.STALE_IDENTITY, null) : new ClanAllianceService.ProofResult(ClanAllianceService.Status.SUCCESS, ClanAllianceService.Reason.NONE, new AllianceMembershipProof(identity, epochs));
		}

		@Override
		public ClanAllianceService.Result dissolveAlliance(MemberRef actor, AllianceMembershipProof proof)
		{
			dissolveCalls++;
			if (beforeDissolve != null)
			{
				final Runnable hook = beforeDissolve;
				beforeDissolve = null;
				hook.run();
			}
			final List<MembershipEpoch> current = clans.values().stream().filter(clan -> proof.identity().equals(clan.alliance)).map(clan -> new MembershipEpoch(clan.id, proof.identity().leaderClanId(), proof.identity().generation(), clan.membershipCounter)).sorted(java.util.Comparator.comparingInt(MembershipEpoch::clanId)).toList();
			if (!current.equals(proof.memberEpochs()))
			{
				return allianceFailure(ClanAllianceService.Status.STALE, ClanAllianceService.Reason.STALE_IDENTITY, proof.identity());
			}
			for (ClanData clan : clans.values())
			{
				if (proof.identity().equals(clan.alliance))
				{
					clan.alliance = null;
					clan.allianceName = null;
					clan.membershipCounter++;
				}
			}
			return new ClanAllianceService.Result(ClanAllianceService.Status.SUCCESS, ClanAllianceService.Reason.NONE, proof.identity());
		}

		@Override
		public Optional<WarIdentity> currentWar(MemberRef first, MemberRef second)
		{
			if (war == null)
			{
				return Optional.empty();
			}
			final int firstClanId = clan(first).id;
			final int secondClanId = clan(second).id;
			return ((war.sourceClanId() == firstClanId) && (war.targetClanId() == secondClanId)) || ((war.sourceClanId() == secondClanId) && (war.targetClanId() == firstClanId)) ? Optional.of(war) : Optional.empty();
		}

		@Override
		public ClanWarService.Result declareWar(MemberRef actor, MemberRef target)
		{
			declareCalls++;
			startWar(clan(actor).id, clan(target).id);
			return new ClanWarService.Result(ClanWarService.Status.SUCCESS, ClanWarService.Reason.NONE, war);
		}

		@Override
		public ClanWarService.Result stopWar(MemberRef actor, MemberRef target, long expectedWarId)
		{
			stopCalls++;
			if ((war == null) || (war.warId() != expectedWarId) || (war.sourceClanId() != clan(actor).id) || (war.targetClanId() != clan(target).id))
			{
				return warFailure(ClanWarService.Status.STALE, ClanWarService.Reason.STALE_IDENTITY);
			}
			lastStoppedWarId = expectedWarId;
			final WarIdentity ended = war;
			war = null;
			return new ClanWarService.Result(ClanWarService.Status.SUCCESS, ClanWarService.Reason.NONE, ended);
		}

		@Override
		public ClanWarService.Result acceptPeace(MemberRef first, MemberRef second, WarIdentity identity)
		{
			peaceCalls++;
			if ((war == null) || (war.warId() != identity.warId()))
			{
				return warFailure(ClanWarService.Status.STALE, ClanWarService.Reason.STALE_IDENTITY);
			}
			lastPeaceWarId = identity.warId();
			final WarIdentity ended = war;
			war = null;
			return new ClanWarService.Result(ClanWarService.Status.SUCCESS, ClanWarService.Reason.NONE, ended);
		}

		@Override
		public RelationshipEvidence relationship(long ownerProfileId, MemberRef subject, long nowEpochMinute)
		{
			final List<String> evidence = hostileEvidence ? List.of(PhantomClanService.sha256("hostile-event")) : List.of();
			return new RelationshipEvidence(true, hostilityScore, -hostilityScore, evidence, PhantomClanService.sha256("relationship|" + ownerProfileId + "|" + subject.profileId() + "|" + hostilityScore));
		}

		@Override
		public boolean recordRelation(long ownerProfileId, MemberRef subject, String eventKey, String operationId, String evidenceHash, long happenedEpochMinute)
		{
			socialReceipts.add(ownerProfileId + "|" + subject.profileId() + "|" + eventKey + "|" + operationId);
			return true;
		}

		@Override
		public long pvpPairCooldownMillis()
		{
			return 10_000;
		}

		@Override
		public ChatResult allianceChat(MemberRef member, AllianceIdentity expectedIdentity, String text)
		{
			final ClanData clan = clan(member);
			if ((clan == null) || !expectedIdentity.equals(clan.alliance))
			{
				return new ChatResult(ChatOutcome.STALE, 0);
			}
			allianceChatCalls++;
			return new ChatResult(ChatOutcome.DELIVERED, 2);
		}

		private ClanData clan(MemberRef member)
		{
			if (member == null)
			{
				return null;
			}
			return clans.values().stream().filter(clan -> clan.leaderObjectId == member.characterObjectId()).findFirst().orElse(null);
		}

		private static ClanAllianceService.Result allianceFailure(ClanAllianceService.Status status, ClanAllianceService.Reason reason, AllianceIdentity identity)
		{
			return new ClanAllianceService.Result(status, reason, identity);
		}

		private ClanWarService.Result warFailure(ClanWarService.Status status, ClanWarService.Reason reason)
		{
			return new ClanWarService.Result(status, reason, war);
		}
	}

	private static final class ClanData
	{
		private final int id;
		private final String name;
		private final int leaderObjectId;
		private final boolean managed;
		private AllianceIdentity alliance;
		private String allianceName;
		private long membershipCounter;

		private ClanData(int id, String name, int leaderObjectId, boolean managed)
		{
			this.id = id;
			this.name = name;
			this.leaderObjectId = leaderObjectId;
			this.managed = managed;
		}

		private ClanSnapshot snapshot(int memberObjectId)
		{
			return new ClanSnapshot(id, name, leaderObjectId, 5, 5, 40, alliance == null ? 0 : alliance.leaderClanId(), 0, PhantomClanService.sha256(id + "|" + name + "|" + leaderObjectId + "|" + memberObjectId + "|" + membershipCounter));
		}
	}
}

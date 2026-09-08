/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.data.xml.DoorData;
import org.l2jmobius.gameserver.managers.CastleManager;
import org.l2jmobius.gameserver.managers.SiegeManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.siege.Castle;
import org.l2jmobius.gameserver.model.siege.Siege;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatActorLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActorSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.PvpTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ShotOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.SiegeTargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.TargetSnapshot;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ThreatObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver.CapabilityEvidence;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMetrics;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchHandle;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchResult;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.DispatchState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPartySupportAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomSiegeCombatRequest;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService.ResultStatus;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.siege.L2jPhantomSiegeAuthority;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeAuthority;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeCatalog;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.CastleSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.NativeSide;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.RegistrationOutcome;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Role;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Stage;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.Target;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeModel.TargetKind;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeMovementCoordinator;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeMovementPort;
import org.l2jmobius.gameserver.phantoms.siege.PhantomSiegeService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Focused Goal035 catalog, lifecycle and guarded native Giran acceptance. */
public final class PhantomSiegeGoal035Suite implements PhantomTestSuite
{
	private static final long SEED = 35003501L;
	private static final int GIRAN_CASTLE_ID = 3;
	private static final String HASH = "AB".repeat(32);
	private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
	private PhantomProfileRepository _profiles;
	private PhantomMaterializationService _materialization;
	private PhantomProfile _attackerProfile;
	private PhantomProfile _defenderProfile;
	private Player _attacker;
	private Player _defender;
	private Clan _attackerClan;
	private Clan _defenderClan;
	private Castle _castle;
	private CastleState _originalCastle;
	private boolean _environmentInitialized;

	@Override
	public String id()
	{
		return "siege-goal035";
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Goal035 used the wrong deterministic seed.");
		_environment.initialize(context);
		_environmentInitialized = true;
		DoorData.getInstance();
		CastleManager.getInstance().activateInstances();
		_castle = CastleManager.getInstance().getCastleById(GIRAN_CASTLE_ID);
		PhantomAssertions.assertTrue(_castle != null, "Native Giran castleId 3 is absent.");
		_originalCastle = castleState();
		PhantomAssertions.assertEquals(0, _originalCastle.ownerId(), "Guarded fixture refuses to replace an existing Giran owner.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM siege_clans WHERE castle_id=?", GIRAN_CASTLE_ID), "Guarded fixture refuses to replace existing Giran siege registrations.");

		_profiles = PhantomProfileRepository.open();
		_attackerProfile = _profiles.create(_environment.primary().objectId());
		_defenderProfile = _profiles.create(_environment.observer().objectId());
		final PhantomMetrics metrics = new PhantomMetrics();
		_materialization = new PhantomMaterializationService(_profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 2);
		PhantomAssertions.assertTrue(_materialization.start(), "Goal035 materialization fixture did not start.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_attackerProfile.profileId()).status(), "Attacker fixture did not materialize.");
		PhantomAssertions.assertEquals(ResultStatus.SUCCESS, _materialization.materialize(_defenderProfile.profileId()).status(), "Defender fixture did not materialize.");
		_attacker = World.getInstance().getPlayer(_environment.primary().objectId());
		_defender = World.getInstance().getPlayer(_environment.observer().objectId());
		PhantomAssertions.assertTrue((_attacker != null) && (_defender != null), "Goal035 canonical Players are absent from World.");
		_attacker.getStat().setLevel((byte) 80);
		_defender.getStat().setLevel((byte) 80);
		resetClanPenalties(_attacker);
		resetClanPenalties(_defender);
		_attackerClan = ClanTable.getInstance().createClan(_attacker, "G35Atk" + Math.floorMod((int) context.seed(), 1000));
		PhantomAssertions.assertTrue(_attackerClan != null, "Goal035 attacker clan creation failed.");
		_attackerClan.addClanMember(_defender);
		context.record("goal035.database", "127.0.0.1:3308/l2jmobiush5_phantom_test");
		context.record("goal035.nativeScheduleMillis", _castle.getSiegeDate().getTimeInMillis());
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-strict-catalog-and-factual-topology", this::strictCatalogAndTopology);
		registry.add("02-role-cap-and-determinism", this::roleCapAndDeterminism);
		registry.add("03-support-role-reuses-party-combat-owner", this::supportRoleOwnership);
		registry.add("04-typed-siege-combat-legality", this::typedSiegeCombatLegality);
		registry.add("05-service-routing-restart-and-stop-cleanup", this::serviceLifecycleCleanup);
		registry.add("06-retreat-recovery-and-native-finish", this::retreatRecoveryAndFinish);
		registry.add("07-production-source-safety", this::productionSourceSafety);
		registry.add("08-native-registration-lifecycle-targets-and-restart", this::nativeVertical);
	}

	private void strictCatalogAndTopology(PhantomTestContext context) throws Exception
	{
		final Path policyPath = context.moduleRoot().resolve("dist/game/data/phantoms/siege/high-five-siege-v1.xml");
		final PhantomSiegeCatalog catalog = PhantomSiegeCatalog.load(policyPath);
		PhantomAssertions.assertEquals(GIRAN_CASTLE_ID, catalog.strategy().castleId(), "Canonical policy is not Giran-only.");
		PhantomAssertions.assertEquals("giran.castle.attacker.staging", catalog.strategy().attackerStagingAnchor(), "Attacker staging anchor changed.");
		final String policy = Files.readString(policyPath, StandardCharsets.UTF_8).toLowerCase(java.util.Locale.ROOT);
		for (String forbidden : List.of("sunday", "weekday", "16:00", "ownerid", "registrationopen", "siegedate"))
		{
			PhantomAssertions.assertFalse(policy.contains(forbidden), "Phantom policy copied native authority: " + forbidden);
		}
		final String topology = Files.readString(context.moduleRoot().resolve("dist/game/data/phantoms/topology/high-five-siege.xml"), StandardCharsets.UTF_8) + Files.readString(context.moduleRoot().resolve("dist/game/data/phantoms/topology/high-five-core.xml"), StandardCharsets.UTF_8);
		for (String anchor : List.of("giran.castle.attacker.staging", "giran.castle.defender.staging", "giran.castle.door.23220001.approach", "giran.city.center"))
		{
			PhantomAssertions.assertTrue(topology.contains("id=\"" + anchor + "\""), "Factual topology anchor is absent: " + anchor);
		}
		catalog.validateTopologyAnchors(anchor -> topology.contains("id=\"" + anchor + "\""));
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> catalog.validateTopologyAnchors(anchor -> !anchor.equals("giran.castle.defender.staging")), "Unknown siege topology anchor did not fail closed.");

		final String canonical = Files.readString(policyPath, StandardCharsets.UTF_8);
		final String castleLine = canonical.lines().filter(line -> line.contains("<castle ")).findFirst().orElseThrow();
		final List<String> invalidPolicies = List.of(
			canonical.replace("<castle ", "<castle copiedSchedule=\"Sunday-16:00\" "),
			canonical.replace(castleLine, castleLine + System.lineSeparator() + castleLine),
			canonical.replace(" />", "><unexpected /></castle>"),
			canonical.replace(" />", "><role id=\"SUPPORT\" /><role id=\"SUPPORT\" /></castle>"),
			canonical.replace("maximumTargets=\"8\"", "maximumTargets=\"9\""));
		for (int index = 0; index < invalidPolicies.size(); index++)
		{
			final Path malformed = Files.createTempFile(context.reportsDirectory(), "goal035-invalid-", ".xml");
			try
			{
				Files.writeString(malformed, invalidPolicies.get(index), StandardCharsets.UTF_8);
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomSiegeCatalog.load(malformed), "Malformed siege policy case " + index + " did not fail closed.");
			}
			finally
			{
				Files.deleteIfExists(malformed);
			}
		}
	}

	private void roleCapAndDeterminism(PhantomTestContext context)
	{
		final List<MemberSnapshot> facts = List.of(
			member(4, 104, List.of("combat.melee_damage")),
			member(2, 102, List.of("combat.heal")),
			member(1, 101, List.of("combat.melee_damage")),
			member(3, 103, List.of("combat.ranged_magic_damage")),
			member(5, 105, List.of("combat.melee_damage")));
		final var first = PhantomSiegeService.assignRoles(facts, 101, 4);
		final var second = PhantomSiegeService.assignRoles(facts, 101, 4);
		PhantomAssertions.assertEquals(first, second, "Identical native member facts produced different siege roles.");
		PhantomAssertions.assertEquals(Role.COMMANDER, first.get(0).role(), "Exact clan leader is not COMMANDER.");
		PhantomAssertions.assertEquals(Role.SUPPORT, first.get(1).role(), "Support capability was not classified as SUPPORT.");
		PhantomAssertions.assertEquals(Role.RANGED, first.get(2).role(), "Ranged capability was not classified as RANGED.");
		PhantomAssertions.assertEquals(1L, first.stream().filter(value -> value.role() == Role.RESERVE).count(), "Active cap did not produce one RESERVE.");
		context.record("goal035.roles", first.stream().map(value -> value.member().objectId() + ":" + value.role()).toList());
	}

	private void supportRoleOwnership(PhantomTestContext context)
	{
		final FakeCombatBackend backend = new FakeCombatBackend();
		final PhantomCombatService combat = new PhantomCombatService(backend, new PhantomCombatCapabilityResolver(classId -> List.of()), PhantomCombatPolicy.productionDefaults(8));
		combat.start();
		final MemberRef leader = MemberRef.phantom(1, 101);
		final MemberRef support = MemberRef.phantom(2, 102);
		final MemberCapability healing = new MemberCapability("combat.heal", "goal035-heal", 1, 123, 1, "SINGLE_TARGET", false, true, true, "skill.ready", 100, "progression.catalog");
		final var leaderSnapshot = partyMember(leader, 20, List.of());
		final var supportSnapshot = partyMember(support, 100, List.of(healing));
		final PhantomPartyTactics tactics = new PhantomPartyTactics(combat);
		final var directive = tactics.plan(leader, List.of(leader, support), Map.of(leader, leaderSnapshot, support, supportSnapshot)).stream().filter(value -> (value.kind() == DirectiveKind.HEAL_MEMBER) && value.actor().equals(support)).findFirst().orElseThrow(() -> new AssertionError("Factual SUPPORT role produced no exact heal directive."));
		final var lease = tactics.dispatch(directive, "siege.support.goal035", Long.MAX_VALUE, () -> false).orElseThrow(() -> new AssertionError("Existing Party/Combat support owner rejected the exact siege support directive."));
		PhantomAssertions.assertEquals(1, backend.supportCasts, "Support action did not cross the existing Combat owner exactly once.");
		PhantomAssertions.assertEquals(1, combat.snapshot().externalActions(), "Support action ownership was not retained by shared Combat.");
		lease.complete();
		PhantomAssertions.assertEquals(0, combat.snapshot().externalActions(), "Support action ownership was not released.");
		combat.beginStop();
		PhantomAssertions.assertTrue(combat.finishStop(), "Support ownership fixture did not drain.");
		context.record("goal035.supportOwner", "PhantomPartyTactics/Combat.PARTY_SUPPORT");
	}

	private void typedSiegeCombatLegality(PhantomTestContext context)
	{
		final PhantomSiegeCombatRequest playerRequest = request(200, TargetKind.PLAYER, 200);
		final ActorSnapshot actor = combatActor();
		final SiegeTargetSnapshot opposing = siegeTarget(TargetKind.PLAYER, NativeSide.ATTACKER, NativeSide.DEFENDER, 200, 200, true);
		PhantomAssertions.assertTrue(opposing.validFor(actor, playerRequest, 2000), "Opposing native siege Player was rejected.");
		PhantomAssertions.assertFalse(siegeTarget(TargetKind.PLAYER, NativeSide.ATTACKER, NativeSide.ATTACKER, 200, 200, true).validFor(actor, playerRequest, 2000), "Same-side native siege Player was accepted.");
		final PhantomSiegeCombatRequest doorRequest = request(300, TargetKind.DOOR, 23220001);
		PhantomAssertions.assertTrue(siegeTarget(TargetKind.DOOR, NativeSide.ATTACKER, NativeSide.NONE, 300, 23220001, true).validFor(actor, doorRequest, 2000), "Legal attacker door target was rejected.");
		PhantomAssertions.assertFalse(siegeTarget(TargetKind.DOOR, NativeSide.DEFENDER, NativeSide.NONE, 300, 23220001, true).validFor(actor, doorRequest, 2000), "Defender was accepted by the siege-door branch.");
		final PvpTargetSnapshot ordinaryPvp = new PvpTargetSnapshot(200, 0, 0, 80, 4, 4, 100, true, true, true, false, false, false, true, false, false, false, false, false, false, true, false, false, false, true);
		PhantomAssertions.assertFalse(ordinaryPvp.validFor(actor, 2000), "Ordinary PvP legality was weakened for siege context.");
	}

	private void serviceLifecycleCleanup(PhantomTestContext context)
	{
		final PhantomSiegeCatalog catalog = PhantomSiegeCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/siege/high-five-siege-v1.xml"));
		final PhantomCombatService combat = new PhantomCombatService(profileId -> null, new PhantomCombatCapabilityResolver(classId -> List.of()), PhantomCombatPolicy.productionDefaults(8));
		final FakeSiegeAuthority attackerAuthority = new FakeSiegeAuthority();
		final FakeMovement attackerMovement = new FakeMovement();
		final FakeSignals attackerSignals = new FakeSignals();
		final PhantomSiegeService attacker = new PhantomSiegeService(new MemoryGoalStore(goal()), attackerAuthority, catalog, attackerMovement, combat, attackerSignals, () -> 1000L);
		PhantomAssertions.assertEquals(Stage.GATHERING, attacker.advance(1, 700, 1).stage(), "Attacker did not enter factual gathering.");
		PhantomAssertions.assertEquals("giran.castle.attacker.staging", attackerMovement.lastAnchor, "Attacker used the wrong staging anchor.");
		PhantomAssertions.assertEquals(1, attacker.activeCount(), "Attacker operation was not retained within the bounded map.");
		PhantomAssertions.assertEquals(1, attackerSignals.submits, "Siege relevance was not published through the existing seam.");
		attacker.beginStop();
		PhantomAssertions.assertTrue(attacker.finishStop(), "Siege beginStop/finishStop retained attacker ownership.");
		PhantomAssertions.assertEquals(0, attacker.activeCount(), "Siege stop retained a process-local operation.");
		PhantomAssertions.assertEquals(1, attackerSignals.withdrawals, "Siege stop did not withdraw owned relevance.");
		PhantomAssertions.assertEquals(1, attackerMovement.cancels, "Siege stop did not cancel its route.");

		final FakeMovement resumedMovement = new FakeMovement(PhantomSiegeMovementCoordinator.Status.ARRIVED, "siege.route.arrived");
		final PhantomSiegeService resumed = new PhantomSiegeService(new MemoryGoalStore(goal()), attackerAuthority, catalog, resumedMovement, combat, new FakeSignals(), () -> 1000L);
		PhantomAssertions.assertEquals(Stage.WAITING_START, resumed.advance(1, 700, 1).stage(), "Re-instantiated service did not reconstruct registered native attacker truth.");
		PhantomAssertions.assertEquals(0, attackerAuthority.registrationCalls, "Re-instantiation duplicated an existing native registration.");
		resumed.beginStop();

		final FakeSiegeAuthority defenderAuthority = new FakeSiegeAuthority();
		defenderAuthority.defender();
		final FakeMovement defenderMovement = new FakeMovement();
		final PhantomSiegeService defender = new PhantomSiegeService(new MemoryGoalStore(goal()), defenderAuthority, catalog, defenderMovement, combat, new FakeSignals(), () -> 1000L);
		PhantomAssertions.assertEquals(Stage.GATHERING, defender.advance(1, 700, 1).stage(), "Native owner defender did not enter gathering.");
		PhantomAssertions.assertEquals("giran.castle.defender.staging", defenderMovement.lastAnchor, "Defender used the attacker staging anchor.");
		defender.beginStop();

		final FakeMovement noRoute = new FakeMovement(PhantomSiegeMovementCoordinator.Status.FAILED, "siege.route.no_path");
		final PhantomSiegeService blocked = new PhantomSiegeService(new MemoryGoalStore(goal()), new FakeSiegeAuthority(), catalog, noRoute, combat, new FakeSignals(), () -> 1000L);
		PhantomAssertions.assertEquals(Stage.ABANDONED, blocked.advance(1, 700, 1).stage(), "No-route did not abandon/replan safely.");
		PhantomAssertions.assertEquals(0, blocked.activeCount(), "No-route retained an operation receipt.");
	}

	private void retreatRecoveryAndFinish(PhantomTestContext context)
	{
		final FakeSiegeAuthority authority = new FakeSiegeAuthority();
		authority.activeAttacker(100, false);
		final FakeMovement movement = new FakeMovement();
		final FakeSignals signals = new FakeSignals();
		final FakeCombatBackend backend = new FakeCombatBackend();
		final ManualDispatcher dispatcher = new ManualDispatcher();
		final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(classId -> List.of(new CapabilityEvidence("combat.melee_damage", "goal035", 1, List.of())));
		final PhantomCombatService combat = new PhantomCombatService(backend, resolver, PhantomCombatPolicy.productionDefaults(8), new PhantomCombatMetrics(), () -> 1_000_000L, dispatcher);
		combat.start();
		final PhantomSiegeService service = new PhantomSiegeService(new MemoryGoalStore(goal()), authority, PhantomSiegeCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/siege/high-five-siege-v1.xml")), movement, combat, signals, () -> 1000L);

		PhantomAssertions.assertEquals(Stage.ATTACKING, service.advance(1, 700, 1).stage(), "Healthy attacker retreated prematurely.");
		PhantomAssertions.assertTrue(combat.matchesSiegeSession(1, 200, GIRAN_CASTLE_ID, HASH), "Siege combat was not owned by shared Combat.");
		authority.activeAttacker(26, false);
		PhantomAssertions.assertEquals(Stage.ATTACKING, service.advance(1, 700, 1).stage(), "Above-threshold attacker retreated prematurely.");
		authority.activeAttacker(25, false);
		PhantomAssertions.assertEquals(Stage.RETREATING, service.advance(1, 700, 1).stage(), "Bounded low-HP threshold did not enter retreat.");
		PhantomAssertions.assertEquals("giran.city.center", movement.lastAnchor, "Retreat did not use the factual existing Giran city anchor.");
		PhantomAssertions.assertTrue(combat.find(1).isEmpty(), "Retreat retained siege combat ownership.");

		authority.activeAttacker(0, true);
		PhantomAssertions.assertEquals(Stage.RETREATING, service.advance(1, 700, 1).stage(), "Dead actor bypassed native recovery lifecycle.");
		PhantomAssertions.assertEquals(1, backend.respawns, "Dead actor did not use the existing Combat respawn owner exactly once.");
		authority.finished();
		PhantomAssertions.assertEquals(Stage.COMPLETE, service.advance(1, 700, 1).stage(), "Native siege finish did not terminalize a retreating operation.");
		PhantomAssertions.assertEquals(0, service.activeCount(), "Native finish retained siege operation ownership.");
		PhantomAssertions.assertEquals(1, signals.withdrawals, "Native finish did not withdraw siege relevance.");
		service.beginStop();
		PhantomAssertions.assertTrue(service.finishStop(), "Finished siege service did not drain.");
		combat.beginStop();
		PhantomAssertions.assertTrue(combat.finishStop(), "Shared Combat retained Goal035 worker/action ownership.");
	}

	private void productionSourceSafety(PhantomTestContext context) throws Exception
	{
		final String authority = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/siege/L2jPhantomSiegeAuthority.java"));
		final String service = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/siege/PhantomSiegeService.java"));
		final String combat = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/combat/L2jCombatBackend.java"));
		PhantomAssertions.assertTrue(authority.contains("registerAttacker(player, false)"), "Production adapter lacks the native non-force registration call.");
		PhantomAssertions.assertFalse(authority.contains("registerAttacker(player, true)"), "Production adapter contains force registration.");
		for (String forbidden : List.of("teleToLocation(", "setOwner(", "setCurrentHp(", "setOnlineStatus(", "setSiegeState(", "setSiegeSide("))
		{
			PhantomAssertions.assertFalse(authority.contains(forbidden) || service.contains(forbidden), "Phantom siege production code contains forbidden mutation: " + forbidden);
		}
		PhantomAssertions.assertTrue(combat.contains("attackSiege") && combat.contains("onForcedAttack") && combat.contains("Intention.ATTACK"), "Shared Combat owner lacks canonical siege action paths.");
		PhantomAssertions.assertFalse(service.contains("ScheduledFuture") || service.contains("ThreadPool") || service.contains("new Thread"), "Siege service introduced a shadow scheduler.");
	}

	private void nativeVertical(PhantomTestContext context) throws Exception
	{
		final PhantomSiegeAuthority authority = new L2jPhantomSiegeAuthority(_profiles, _materialization, () -> null);
		final CastleSnapshot observed = authority.observeCastle(GIRAN_CASTLE_ID).orElseThrow();
		PhantomAssertions.assertTrue(observed.name().toLowerCase(java.util.Locale.ROOT).contains("giran"), "castleId 3 did not resolve as Giran.");
		PhantomAssertions.assertTrue(observed.siegeDateMillis() > 0, "Native Giran schedule was not observed.");
		PhantomAssertions.assertTrue(observed.registrationOpen(), "Native Giran registration window is not open in the guarded fixture.");

		PhantomAssertions.assertEquals(RegistrationOutcome.NOT_LEADER, authority.registerAttacker(_defenderProfile.profileId(), GIRAN_CASTLE_ID).outcome(), "Managed non-leader caused autonomous registration.");
		PhantomAssertions.assertEquals(RegistrationOutcome.INELIGIBLE, authority.registerAttacker(_attackerProfile.profileId(), GIRAN_CASTLE_ID).outcome(), "Low-level clan registered for Giran.");
		_attackerClan.changeLevel(SiegeManager.getInstance().getSiegeClanMinLevel());
		final PhantomCombatService registrationCombat = new PhantomCombatService(profileId -> null, new PhantomCombatCapabilityResolver(classId -> List.of()), PhantomCombatPolicy.productionDefaults(8));
		registrationCombat.start();
		final PhantomSiegeCatalog catalog = PhantomSiegeCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/siege/high-five-siege-v1.xml"));
		final MemoryGoalStore registrationGoals = new MemoryGoalStore(nativePrepareGoal(_attackerProfile.profileId()));
		final PhantomSiegeService registrationService = new PhantomSiegeService(registrationGoals, authority, catalog, new FakeMovement(PhantomSiegeMovementCoordinator.Status.ARRIVED, "siege.route.arrived"), registrationCombat, new FakeSignals());
		try
		{
			try
			{
				PhantomAssertions.assertEquals(Stage.WAITING_START, registrationService.advance(_attackerProfile.profileId(), 735, 1).stage(), "Eligible managed leader did not autonomously register through the siege service and native validation.");
			}
			finally
			{
				registrationService.beginStop();
				PhantomAssertions.assertTrue(registrationService.finishStop(), "Production-composed registration service did not drain.");
			}
			PhantomAssertions.assertTrue(_castle.getSiege().checkIsAttacker(_attackerClan), "Registration is absent from native Siege truth.");
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM siege_clans WHERE castle_id=? AND clan_id=? AND type=?", GIRAN_CASTLE_ID, _attackerClan.getId(), Siege.ATTACKER), "Native registration did not persist exactly one attacker row.");
			PhantomAssertions.assertEquals(RegistrationOutcome.ALREADY_REGISTERED, authority.registerAttacker(_attackerProfile.profileId(), GIRAN_CASTLE_ID).outcome(), "Duplicate registration was not idempotent.");
			PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM siege_clans WHERE castle_id=? AND clan_id=?", GIRAN_CASTLE_ID, _attackerClan.getId()), "Duplicate registration created another row.");
			final PhantomSiegeService restartedService = new PhantomSiegeService(registrationGoals, new L2jPhantomSiegeAuthority(_profiles, _materialization, () -> null), catalog, new FakeMovement(PhantomSiegeMovementCoordinator.Status.ARRIVED, "siege.route.arrived"), registrationCombat, new FakeSignals());
			try
			{
				PhantomAssertions.assertEquals(Stage.WAITING_START, restartedService.advance(_attackerProfile.profileId(), 735, 1).stage(), "Re-instantiated siege service did not reconstruct native registration.");
				PhantomAssertions.assertEquals(1L, scalar("SELECT COUNT(*) FROM siege_clans WHERE castle_id=? AND clan_id=?", GIRAN_CASTLE_ID, _attackerClan.getId()), "Re-instantiated siege service duplicated native registration.");
			}
			finally
			{
				restartedService.beginStop();
				PhantomAssertions.assertTrue(restartedService.finishStop(), "Re-instantiated production-composed siege service did not drain.");
			}
		}
		finally
		{
			registrationCombat.beginStop();
			PhantomAssertions.assertTrue(registrationCombat.finishStop(), "Registration Combat owner did not drain.");
		}

		_attackerClan.removeClanMember(_defender.getObjectId(), 0);
		resetClanPenalties(_defender);
		_defenderClan = ClanTable.getInstance().createClan(_defender, "G35Def" + Math.floorMod((int) context.seed(), 1000));
		PhantomAssertions.assertTrue(_defenderClan != null, "Goal035 defender clan creation failed.");
		_defenderClan.changeLevel(SiegeManager.getInstance().getSiegeClanMinLevel());
		_castle.setOwner(_defenderClan);
		PhantomAssertions.assertEquals(RegistrationOutcome.INELIGIBLE, authority.registerAttacker(_defenderProfile.profileId(), GIRAN_CASTLE_ID).outcome(), "Castle-owning clan entered the attacker path.");

		final Siege siege = _castle.getSiege();
		try
		{
			siege.startSiege();
			PhantomAssertions.assertTrue(siege.isInProgress(), "Native Giran siege did not start.");
			_attacker.teleToLocation(111100, 144729, -2540, false);
			_defender.teleToLocation(111200, 144729, -2540, false);
			_attacker.onTeleported();
			_defender.onTeleported();
			_castle.getZone().revalidateInZone(_attacker);
			_castle.getZone().revalidateInZone(_defender);
			siege.updatePlayerSiegeStateFlags(false);
			awaitSiegePresence();
			final var attackerActor = authority.observeActor(_attackerProfile.profileId(), GIRAN_CASTLE_ID).orElseThrow();
			final var defenderActor = authority.observeActor(_defenderProfile.profileId(), GIRAN_CASTLE_ID).orElseThrow();
			PhantomAssertions.assertEquals(NativeSide.ATTACKER, attackerActor.side(), "Native attacker side was not observed.");
			PhantomAssertions.assertEquals(NativeSide.DEFENDER, defenderActor.side(), "Native defender side was not observed.");
			PhantomAssertions.assertTrue(attackerActor.inSiege() && defenderActor.inSiege(), "Materialized members lack native in-siege truth.");
			PhantomAssertions.assertTrue(_defender.isAutoAttackable(_attacker) && _attacker.isAutoAttackable(_defender), "Opposing native siege sides are not auto-attackable.");
			final Target opposingDefender = authority.opposingPlayers(_attackerProfile.profileId(), GIRAN_CASTLE_ID, 8, 2000).stream().filter(target -> target.objectId() == _defender.getObjectId()).findFirst().orElseThrow(() -> new AssertionError("Attacker did not discover opposing defender."));
			PhantomAssertions.assertTrue(authority.opposingPlayers(_defenderProfile.profileId(), GIRAN_CASTLE_ID, 8, 2000).stream().anyMatch(target -> target.objectId() == _attacker.getObjectId()), "Defender did not discover opposing attacker.");

			final List<Target> doors = authority.attackableDoors(_attackerProfile.profileId(), GIRAN_CASTLE_ID, 8, 2000);
			PhantomAssertions.assertFalse(doors.isEmpty(), "No native attackable Giran door was discovered.");
			PhantomAssertions.assertTrue(authority.attackableDoors(_defenderProfile.profileId(), GIRAN_CASTLE_ID, 8, 2000).isEmpty(), "Owner/defender received an illegal siege-door target.");
			final Target door = doors.get(0);
			final L2jCombatBackend backend = new L2jCombatBackend(_materialization, () -> null, () -> null);
			try (PhantomCombatActorLease lease = backend.tryAcquireActor(_attackerProfile.profileId()))
			{
				PhantomAssertions.assertTrue(lease != null, "Shared Combat owner could not acquire the attacker.");
				final PhantomSiegeCombatRequest playerRequest = new PhantomSiegeCombatRequest(_attackerProfile.profileId(), opposingDefender.objectId(), GIRAN_CASTLE_ID, TargetKind.PLAYER, opposingDefender.nativeId(), opposingDefender.authorityHash(), PhantomCombatMode.MELEE_PHYSICAL, false, 30_000, () -> false);
				final SiegeTargetSnapshot playerSnapshot = lease.siegeTargetSnapshot(opposingDefender.objectId(), playerRequest);
				PhantomAssertions.assertTrue((playerSnapshot != null) && playerSnapshot.validFor(lease.actorSnapshot(), playerRequest, 2000), "Shared Combat rejected the opposing native siege Player snapshot.");
				final ActionOutcome playerAction = lease.attackSiege(opposingDefender.objectId(), playerRequest);
				PhantomAssertions.assertTrue((playerAction == ActionOutcome.ISSUED) || (playerAction == ActionOutcome.ALREADY_OWNED), "Canonical opposing-Player siege action was not issued: " + playerAction);
				lease.cancelOwnedAction(new PhantomOwnedAction(1, opposingDefender.objectId(), null, 0));
				final PhantomSiegeCombatRequest request = new PhantomSiegeCombatRequest(_attackerProfile.profileId(), door.objectId(), GIRAN_CASTLE_ID, TargetKind.DOOR, door.nativeId(), door.authorityHash(), PhantomCombatMode.MELEE_PHYSICAL, false, 30_000, () -> false);
				final SiegeTargetSnapshot snapshot = lease.siegeTargetSnapshot(door.objectId(), request);
				PhantomAssertions.assertTrue((snapshot != null) && snapshot.validFor(lease.actorSnapshot(), request, 2000), "Shared Combat rejected the native Giran door snapshot.");
				final ActionOutcome action = lease.attackSiege(door.objectId(), request);
				PhantomAssertions.assertTrue((action == ActionOutcome.ISSUED) || (action == ActionOutcome.ALREADY_OWNED), "Canonical siege-door action was not issued: " + action);
				lease.cancelOwnedAction(new PhantomOwnedAction(1, door.objectId(), null, 0));
			}
			final ManualDispatcher dispatcher = new ManualDispatcher();
			final PhantomCombatCapabilityResolver resolver = new PhantomCombatCapabilityResolver(classId -> List.of(new CapabilityEvidence("combat.melee_damage", "goal035-native", 1, List.of())));
			final PhantomCombatService sharedCombat = new PhantomCombatService(backend, resolver, PhantomCombatPolicy.productionDefaults(2), new PhantomCombatMetrics(), System::nanoTime, dispatcher);
			sharedCombat.start();
			try
			{
				final PhantomSiegeCombatRequest playerRequest = new PhantomSiegeCombatRequest(_attackerProfile.profileId(), opposingDefender.objectId(), GIRAN_CASTLE_ID, TargetKind.PLAYER, opposingDefender.nativeId(), opposingDefender.authorityHash(), PhantomCombatMode.MELEE_PHYSICAL, false, 30_000, () -> false);
				PhantomAssertions.assertTrue(sharedCombat.startSiegeSession(playerRequest).accepted(), "Shared Combat service did not accept the production-composed opposing Player request.");
				PhantomAssertions.assertTrue(sharedCombat.matchesSiegeSession(_attackerProfile.profileId(), opposingDefender.objectId(), GIRAN_CASTLE_ID, opposingDefender.authorityHash()), "Shared Combat service lost exact siege authority.");
			}
			finally
			{
				sharedCombat.cancel(_attackerProfile.profileId());
				sharedCombat.consumeTerminal(_attackerProfile.profileId());
				sharedCombat.beginStop();
				PhantomAssertions.assertTrue(sharedCombat.finishStop(), "Production-composed siege Combat service retained ownership.");
			}
			context.record("goal035.nativeSides", attackerActor.side() + "/" + defenderActor.side());
			context.record("goal035.playerTarget", opposingDefender.nativeId() + "/" + opposingDefender.objectId());
			context.record("goal035.door", door.nativeId() + "/" + door.objectId());
		}
		finally
		{
			if (siege.isInProgress())
			{
				siege.endSiege();
			}
		}
		PhantomAssertions.assertFalse(siege.isInProgress(), "Native siege finish retained in-progress truth.");
		PhantomAssertions.assertFalse(_attacker.isInSiege() || _defender.isInSiege(), "Native siege finish retained Player siege state.");
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		final List<Throwable> failures = new ArrayList<>();
		attempt(failures, () ->
		{
			if ((_castle != null) && _castle.getSiege().isInProgress())
			{
				_castle.getSiege().endSiege();
			}
		});
		attempt(failures, () ->
		{
			if ((_castle != null) && (_attackerClan != null))
			{
				_castle.getSiege().removeSiegeClan(_attackerClan);
			}
		});
		attempt(failures, () ->
		{
			if ((_castle != null) && (_defenderClan != null) && (_castle.getOwnerId() == _defenderClan.getId()))
			{
				_castle.removeOwner(_defenderClan);
			}
		});
		attempt(failures, () -> destroyClan(_defenderClan));
		attempt(failures, () -> destroyClan(_attackerClan));
		attempt(failures, () ->
		{
			if (_materialization != null)
			{
				_materialization.shutdown();
			}
		});
		attempt(failures, () -> deleteProfile(_attackerProfile));
		attempt(failures, () -> deleteProfile(_defenderProfile));
		attempt(failures, this::restoreCastleState);
		attempt(failures, () ->
		{
			if (_environmentInitialized)
			{
				_environment.shutdown();
				_environmentInitialized = false;
			}
		});
		if (!failures.isEmpty())
		{
			final RuntimeException failure = new RuntimeException("Goal035 cleanup failed.");
			failures.forEach(failure::addSuppressed);
			throw failure;
		}
		context.record("goal035.cleanup", "castle-restored,registrations=0,clans=0,profiles=0");
	}

	private void restoreCastleState() throws Exception
	{
		if ((_castle == null) || (_originalCastle == null))
		{
			return;
		}
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("UPDATE castle SET siegeDate=? WHERE id=?"))
		{
			statement.setLong(1, _originalCastle.siegeDate());
			statement.setInt(2, GIRAN_CASTLE_ID);
			PhantomAssertions.assertEquals(1, statement.executeUpdate(), "Giran castle snapshot restore changed no row.");
		}
		_castle.getSiegeDate().setTimeInMillis(_originalCastle.siegeDate());
		PhantomAssertions.assertEquals(_originalCastle, castleState(), "Giran castle state was not restored exactly.");
		PhantomAssertions.assertEquals(0L, scalar("SELECT COUNT(*) FROM siege_clans WHERE castle_id=?", GIRAN_CASTLE_ID), "Giran siege registration residue remains.");
	}

	private CastleState castleState() throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection();
			PreparedStatement statement = connection.prepareStatement("SELECT siegeDate FROM castle WHERE id=?"))
		{
			statement.setInt(1, GIRAN_CASTLE_ID);
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Giran castle database row is absent.");
				final int databaseOwnerId = (int) scalar("SELECT COALESCE(MAX(clan_id),0) FROM clan_data WHERE hasCastle=?", GIRAN_CASTLE_ID);
				PhantomAssertions.assertEquals(databaseOwnerId, _castle.getOwnerId(), "Native Giran owner memory differs from clan_data authority.");
				return new CastleState(databaseOwnerId, result.getLong(1));
			}
		}
	}

	private void awaitSiegePresence() throws Exception
	{
		final long deadline = System.nanoTime() + 5_000_000_000L;
		while (System.nanoTime() < deadline)
		{
			if (_attacker.isInSiege() && _defender.isInSiege())
			{
				return;
			}
			_castle.getSiege().updatePlayerSiegeStateFlags(false);
			Thread.sleep(20L);
		}
		PhantomAssertions.assertTrue(_attacker.isInSiege() && _defender.isInSiege(), "Players did not enter native Giran siege truth: attacker=" + nativePlayerState(_attacker) + ", defender=" + nativePlayerState(_defender));
	}

	private String nativePlayerState(Player player)
	{
		return player.getX() + "/" + player.getY() + "/" + player.getZ() + ", online=" + player.isOnline() + ", clan=" + player.getClanId() + ", siegeState=" + player.getSiegeState() + ", siegeSide=" + player.getSiegeSide() + ", inZone=" + _castle.getSiege().checkIfInZone(player) + ", inSiege=" + player.isInSiege();
	}

	private static MemberSnapshot member(long profileId, int objectId, List<String> capabilities)
	{
		return new MemberSnapshot(profileId, objectId, 0, 80, true, true, capabilities);
	}

	private static org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot partyMember(MemberRef member, int hpPercent, List<MemberCapability> capabilities)
	{
		return new org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot(member, 0, 0, 0, 0, 0, hpPercent, 100, 100, false, false, false, false, 0, List.of(), capabilities, HASH);
	}

	private static PhantomSiegeCombatRequest request(int objectId, TargetKind kind, int nativeId)
	{
		return new PhantomSiegeCombatRequest(1, objectId, GIRAN_CASTLE_ID, kind, nativeId, HASH, PhantomCombatMode.MELEE_PHYSICAL, false, 30_000, () -> false);
	}

	private static ActorSnapshot combatActor()
	{
		return new ActorSnapshot(100, 80, 0, 100, 100, 100, 100, 100, 100, false, false, false, false, false, 0, "IDLE", 0, 0);
	}

	private static SiegeTargetSnapshot siegeTarget(TargetKind kind, NativeSide actorSide, NativeSide targetSide, int objectId, int nativeId, boolean autoAttackable)
	{
		return new SiegeTargetSnapshot(objectId, nativeId, GIRAN_CASTLE_ID, 0, kind, actorSide, targetSide, 100, 100, 100, true, false, false, false, false, true, false, false, true, true, autoAttackable);
	}

	private static PhantomGoal goal()
	{
		return new PhantomGoal(700, PhantomSiegeService.PARTICIPATE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", "1"), new PhantomDomainRef(PhantomSiegeService.TARGET_NAMESPACE, "3"), 1, 0, null, List.of(), null, "siege.participate", 500, 0, 0, 0, Map.of(), "siege.test", 1);
	}

	private static PhantomGoal nativePrepareGoal(long profileId)
	{
		return new PhantomGoal(735, PhantomSiegeService.PREPARE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef(PhantomSiegeService.TARGET_NAMESPACE, "3"), 1, 0, null, List.of(), null, "siege.prepare", 500, 0, 0, 0, Map.of(), "siege.native.test", 1);
	}

	private static long scalar(String sql, Object... arguments) throws Exception
	{
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(sql))
		{
			for (int index = 0; index < arguments.length; index++)
			{
				statement.setObject(index + 1, arguments[index]);
			}
			try (ResultSet result = statement.executeQuery())
			{
				PhantomAssertions.assertTrue(result.next(), "Goal035 scalar query returned no row.");
				return result.getLong(1);
			}
		}
	}

	private static void resetClanPenalties(Player player)
	{
		player.setClanJoinExpiryTime(0);
		player.setClanCreateExpiryTime(0);
	}

	private static void destroyClan(Clan clan)
	{
		if ((clan != null) && (ClanTable.getInstance().getClan(clan.getId()) != null))
		{
			ClanTable.getInstance().destroyClan(clan.getId());
		}
	}

	private void deleteProfile(PhantomProfile profile)
	{
		if ((_profiles != null) && (profile != null))
		{
			_profiles.find(profile.profileId()).ifPresent(current -> _profiles.delete(current.profileId(), current.rowVersion()));
		}
	}

	private static void attempt(List<Throwable> failures, CheckedAction action)
	{
		try
		{
			action.run();
		}
		catch (Throwable throwable)
		{
			failures.add(throwable);
		}
	}

	private record CastleState(int ownerId, long siegeDate)
	{
	}

	@FunctionalInterface
	private interface CheckedAction
	{
		void run() throws Exception;
	}

	private static final class MemoryGoalStore implements PhantomGoalStore
	{
		private StoredGoal _stored;

		private MemoryGoalStore(PhantomGoal goal)
		{
			_stored = new StoredGoal(goal, 1);
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return profileId == 1;
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			return Optional.ofNullable(_stored);
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			_stored = new StoredGoal(goal, 1);
			return _stored;
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			_stored = new StoredGoal(goal, expectedRowVersion + 1);
			return _stored;
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			_stored = null;
		}
	}

	private static final class FakeSiegeAuthority implements PhantomSiegeAuthority
	{
		private boolean _attacker = true;
		private boolean _defender;
		private boolean _inProgress;
		private double _hpPercent = 100;
		private boolean _dead;
		private NativeSide _side = NativeSide.NONE;
		private int registrationCalls;

		private void defender()
		{
			_attacker = false;
			_defender = true;
			_inProgress = false;
			_side = NativeSide.NONE;
		}

		private void activeAttacker(double hpPercent, boolean dead)
		{
			_attacker = true;
			_defender = false;
			_inProgress = true;
			_hpPercent = hpPercent;
			_dead = dead;
			_side = NativeSide.ATTACKER;
		}

		private void finished()
		{
			_inProgress = false;
			_dead = false;
			_hpPercent = 100;
			_side = NativeSide.NONE;
		}

		@Override
		public Optional<CastleSnapshot> observeCastle(int castleId)
		{
			return Optional.of(castle());
		}

		@Override
		public Optional<PhantomSiegeModel.ActorSnapshot> observeActor(long profileId, int castleId)
		{
			return Optional.of(actor());
		}

		@Override
		public PhantomSiegeModel.RegistrationResult registerAttacker(long profileId, int castleId)
		{
			registrationCalls++;
			return new PhantomSiegeModel.RegistrationResult(RegistrationOutcome.ALREADY_REGISTERED, castle(), actor(), "siege.registration.already_attacker");
		}

		@Override
		public List<MemberSnapshot> managedClanMembers(int clanId, int limit)
		{
			return List.of(member(1, 101, List.of("combat.melee_damage")));
		}

		@Override
		public List<Target> opposingPlayers(long profileId, int castleId, int limit, int maximumDistance)
		{
			return _inProgress ? List.of(new Target(TargetKind.PLAYER, 200, GIRAN_CASTLE_ID, 200, _side == NativeSide.ATTACKER ? NativeSide.DEFENDER : NativeSide.ATTACKER, 100, HASH)) : List.of();
		}

		@Override
		public List<Target> attackableDoors(long profileId, int castleId, int limit, int maximumDistance)
		{
			return List.of();
		}

		private CastleSnapshot castle()
		{
			return new CastleSnapshot(GIRAN_CASTLE_ID, "Giran", 100_000, 90_000, true, _inProgress, _inProgress, _defender ? 10 : 0, _attacker ? List.of(10) : List.of(), _defender ? List.of(10) : List.of(), List.of(), HASH);
		}

		private PhantomSiegeModel.ActorSnapshot actor()
		{
			return new PhantomSiegeModel.ActorSnapshot(1, 101, 10, 101, 5, 0, _defender ? GIRAN_CASTLE_ID : 0, 0, 80, 0, _hpPercent, _dead, _inProgress, _side, HASH);
		}
	}

	private static final class FakeMovement implements PhantomSiegeMovementPort
	{
		private final PhantomSiegeMovementCoordinator.Status _status;
		private final String _reasonKey;
		private String lastAnchor;
		private boolean pending;
		private int cancels;

		private FakeMovement()
		{
			this(PhantomSiegeMovementCoordinator.Status.MOVING, "siege.route.moving");
		}

		private FakeMovement(PhantomSiegeMovementCoordinator.Status status, String reasonKey)
		{
			_status = status;
			_reasonKey = reasonKey;
		}

		@Override
		public PhantomSiegeMovementCoordinator.Result advance(long profileId, String anchorId, String authorityHash, long maximumDurationMillis)
		{
			lastAnchor = anchorId;
			pending = (_status == PhantomSiegeMovementCoordinator.Status.MOVING) || (_status == PhantomSiegeMovementCoordinator.Status.PLANNING);
			return new PhantomSiegeMovementCoordinator.Result(_status, _reasonKey);
		}

		@Override
		public boolean cancel(long profileId, String authorityHash)
		{
			if (!pending)
			{
				return false;
			}
			pending = false;
			cancels++;
			return true;
		}

		@Override
		public void finishStop()
		{
		}

		@Override
		public int activeCount()
		{
			return pending ? 1 : 0;
		}
	}

	private static final class FakeCombatBackend implements PhantomCombatBackend
	{
		private int respawns;
		private int supportCasts;

		@Override
		public PhantomCombatActorLease tryAcquireActor(long profileId)
		{
			return profileId > 0 ? new FakeCombatLease(this) : null;
		}
	}

	private static final class FakeCombatLease implements PhantomCombatActorLease
	{
		private final FakeCombatBackend _owner;

		private FakeCombatLease(FakeCombatBackend owner)
		{
			_owner = owner;
		}

		@Override
		public ActorSnapshot actorSnapshot()
		{
			return combatActor();
		}

		@Override
		public TargetSnapshot targetSnapshot(int targetObjectId)
		{
			return null;
		}

		@Override
		public SiegeTargetSnapshot siegeTargetSnapshot(int targetObjectId, PhantomSiegeCombatRequest request)
		{
			return targetObjectId == request.targetObjectId() ? siegeTarget(request.targetKind(), NativeSide.ATTACKER, request.targetKind() == TargetKind.PLAYER ? NativeSide.DEFENDER : NativeSide.NONE, targetObjectId, request.targetNativeId(), true) : null;
		}

		@Override
		public boolean supportsSkill(SelectedSkill skill, PhantomCombatMode mode)
		{
			return false;
		}

		@Override
		public boolean supportsPvpSkill(SelectedSkill skill, PhantomCombatMode mode)
		{
			return false;
		}

		@Override
		public List<ThreatObservation> observedAttackers(int limit)
		{
			return List.of();
		}

		@Override
		public List<LootCandidate> lootCandidates(int limit, int maximumDistance)
		{
			return List.of();
		}

		@Override
		public LootObservation observeLoot(LootCandidate candidate)
		{
			return LootObservation.INELIGIBLE;
		}

		@Override
		public ShotOutcome activateShot(PhantomCombatMode mode)
		{
			return ShotOutcome.UNAVAILABLE;
		}

		@Override
		public ActionOutcome attack(int targetObjectId)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public ActionOutcome attackSiege(int targetObjectId, PhantomSiegeCombatRequest request)
		{
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome castSupport(PhantomPartySupportAction action)
		{
			_owner.supportCasts++;
			return ActionOutcome.ISSUED;
		}

		@Override
		public ActionOutcome cast(int targetObjectId, SelectedSkill skill, PhantomCombatMode mode)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public ActionOutcome pickUp(int objectId)
		{
			return ActionOutcome.REJECTED;
		}

		@Override
		public void cancelOwnedAction(PhantomOwnedAction action)
		{
		}

		@Override
		public RespawnOutcome respawnTown()
		{
			_owner.respawns++;
			return RespawnOutcome.COMPLETED;
		}

		@Override
		public void close()
		{
		}
	}

	private static final class ManualDispatcher implements PhantomCombatService.Dispatcher
	{
		private Runnable _next;
		private ManualHandle _handle;

		@Override
		public DispatchResult dispatch(Runnable runnable, long delayMillis)
		{
			PhantomAssertions.assertTrue(_next == null, "Goal035 scheduled more than one shared Combat worker.");
			_next = runnable;
			_handle = new ManualHandle(this);
			return DispatchResult.accepted(_handle);
		}
	}

	private static final class ManualHandle implements DispatchHandle
	{
		private final ManualDispatcher _owner;
		private DispatchState _state = DispatchState.SCHEDULED;

		private ManualHandle(ManualDispatcher owner)
		{
			_owner = owner;
		}

		@Override
		public boolean cancelIfNotStarted()
		{
			if ((_state != DispatchState.SCHEDULED) || (_owner._handle != this))
			{
				return false;
			}
			_owner._next = null;
			_owner._handle = null;
			_state = DispatchState.CANCELLED;
			return true;
		}

		@Override
		public DispatchState state()
		{
			return _state;
		}
	}

	private static final class FakeSignals implements PhantomRelevanceSignalPort
	{
		private int submits;
		private int withdrawals;

		@Override
		public SignalDelivery submit(long profileId, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal)
		{
			submits++;
			return SignalDelivery.ACCEPTED;
		}

		@Override
		public SignalDelivery withdraw(long profileId, String sourceKey, long sequence)
		{
			withdrawals++;
			return SignalDelivery.ACCEPTED;
		}
	}
}

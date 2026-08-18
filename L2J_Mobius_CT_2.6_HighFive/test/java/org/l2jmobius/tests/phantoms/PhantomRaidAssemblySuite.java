/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.CancelOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.CancelResult;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InviteOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.InviteResult;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.RespondOutcome;
import org.l2jmobius.gameserver.model.groups.CommandChannelInvitationService.Response;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlan;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanStep;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepResult;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.AssemblyStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.StagingSource;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAuthority;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidDecision;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossLocation;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossObservation;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidReadinessService;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidRecruitmentService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyArea;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNode;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyNodeKind;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologySnapshot;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyValidationBackend;

public final class PhantomRaidAssemblySuite implements PhantomTestSuite
{
	public enum Mode
	{
		ASSEMBLY,
		GATHERING,
		DECISION
	}

	private static final long CP4_SEED = 26002641L;
	private static final long SEED = 26002642L;
	private static final long NOW = 1_000_000L;
	private static final long LOGICAL_NOW = 9_000_000_000_000L;
	private static final long DEADLINE = NOW + 1_000_000L;
	private static final String HASH = "0".repeat(64);
	private static final String LIVE_CONTENT = "raid.cp4.live";
	private static final String ANCHOR_CONTENT = "raid.cp4.anchor";
	private static final MemberRef LEADER = MemberRef.phantom(1, 100);
	private static final MemberRef CANDIDATE_ONE = MemberRef.phantom(2, 200);
	private static final MemberRef CANDIDATE_TWO = MemberRef.phantom(3, 300);

	private final Mode _mode;
	private Path _temporaryRoot;
	private PhantomGameKnowledgeService _knowledgeService;
	private PhantomGameKnowledgeQuery _knowledge;
	private PhantomTopologyQuery _topology;
	private PhantomTopologyQuery _emptyTopology;
	private PhantomNavigationService _navigation;
	private PhantomCombatService _combat;
	private PhantomPartyRouteCoordinator _routes;

	public PhantomRaidAssemblySuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "raid-assembly-" + _mode.name().toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public void beforeAll(PhantomTestContext context) throws Exception
	{
		PhantomAssertions.assertTrue((context.seed() == CP4_SEED) || (context.seed() == SEED), "Raid assembly suite used an unsupported deterministic seed.");
		_topology = topology(true);
		_emptyTopology = topology(false);
		_temporaryRoot = context.reportsDirectory().resolve("raid-assembly-" + _mode.name().toLowerCase(java.util.Locale.ROOT) + "-" + ProcessHandle.current().pid());
		Files.createDirectories(_temporaryRoot.resolve("curated"));
		Files.writeString(_temporaryRoot.resolve("Seeds.xml"), """
			<?xml version="1.0" encoding="UTF-8"?>
			<list>
				<castle id="1">
					<crop id="2" seedId="1" mature_Id="3" reward1="4" reward2="5" alternative="false" level="10" limit_seed="100" limit_crops="200" />
				</castle>
			</list>
			""", StandardCharsets.UTF_8);
		Files.writeString(_temporaryRoot.resolve("curated/knowledge.xml"), curatedXml(), StandardCharsets.UTF_8);
		final PhantomGameKnowledgePolicy policy = PhantomGameKnowledgePolicy.productionDefaults();
		final PhantomGameKnowledgeCoreSuite.SyntheticBackend backend = new PhantomGameKnowledgeCoreSuite.SyntheticBackend(false, false, false, 25d, false, 0);
		_knowledgeService = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(_temporaryRoot.resolve("Seeds.xml"), policy), new PhantomCuratedKnowledgeParser(_temporaryRoot.resolve("curated"), backend, policy), _topology, policy));
		PhantomAssertions.assertTrue(_knowledgeService.start(), "CP4 knowledge fixture did not start.");
		_knowledge = _knowledgeService.query();

		final PhantomNavigationBackend navigationBackend = new PhantomNavigationBackend()
		{
			@Override
			public CapabilitySnapshot capability(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
			{
				return new CapabilitySnapshot(PhantomNavigationCapability.GEODATA_DIRECT_ONLY, 1);
			}

			@Override
			public boolean canMoveDirect(PhantomNavigationPoint origin, PhantomNavigationPoint destination)
			{
				return true;
			}

			@Override
			public List<PhantomNavigationPoint> findPath(PhantomNavigationRequest request, PhantomNavigationCancellationToken cancellationToken)
			{
				throw new AssertionError("CP4 direct staging fixture invoked A*.");
			}
		};
		_navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), navigationBackend, worker ->
		{
			worker.run();
			return true;
		}, () -> LOGICAL_NOW, new PhantomMetrics());
		PhantomAssertions.assertTrue(_navigation.start(), "CP4 navigation fixture did not start.");
		_combat = new PhantomCombatService(PhantomCombatBackend.inert(), new PhantomCombatCapabilityResolver(_ -> List.of()), PhantomCombatPolicy.productionDefaults(64));
		_combat.start();
		_routes = new PhantomPartyRouteCoordinator(_navigation, _combat);
		context.record("raid.cp4.mode", _mode.name());
	}

	@Override
	public void afterAll(PhantomTestContext context) throws Exception
	{
		if (_routes != null)
		{
			_routes.beginStop();
		}
		if (_combat != null)
		{
			_combat.beginStop();
			PhantomAssertions.assertTrue(_combat.finishStop(), "CP4 combat fixture did not stop.");
		}
		if (_navigation != null)
		{
			_navigation.beginStop();
			PhantomAssertions.assertTrue(_navigation.finishStop(), "CP4 navigation fixture did not stop.");
		}
		if (_knowledgeService != null)
		{
			_knowledgeService.beginStop();
			_knowledgeService.finishStop();
		}
		if ((_temporaryRoot != null) && Files.exists(_temporaryRoot))
		{
			try (var stream = Files.walk(_temporaryRoot))
			{
				for (Path path : stream.sorted(Collections.reverseOrder()).toList())
				{
					Files.deleteIfExists(path);
				}
			}
		}
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case ASSEMBLY -> assembly(registry);
			case GATHERING -> gathering(registry);
			case DECISION -> decision(registry);
		}
	}

	private void assembly(PhantomTestRegistry registry)
	{
		registry.add("01-invalid-and-stale-goals-fail-closed", _ ->
		{
			try (Fixture fixture = fixture(_topology))
			{
				fixture.party.profile(LEADER);
				fixture.party.force(LEADER, standalone(LEADER, member(LEADER, tank(true))));
				final PhantomGoal invalid = prepare(10, "wrong.goal", List.of(new PhantomDomainRef("unsupported", "2")), null);
				fixture.goals.put(LEADER.profileId(), invalid);
				PhantomAssertions.assertEquals(AssemblyStatus.BLOCKED, fixture.service.advance(LEADER.profileId(), invalid.goalId(), invalid.revision()).status(), "Malformed validSources did not fail closed.");
				PhantomAssertions.assertEquals(0, fixture.service.snapshot().activeAssemblies(), "Invalid goal created transient assembly state.");
				PhantomAssertions.assertEquals(AssemblyStatus.BLOCKED, fixture.service.advance(LEADER.profileId(), invalid.goalId(), invalid.revision() + 1).status(), "Stale revision advanced.");
			}
		});
		registry.add("02-two-phantom-parties-recruit-sequentially-with-later-consent", _ ->
		{
			try (Fixture fixture = recruitmentFixture())
			{
				PhantomAssertions.assertEquals(AssemblyStatus.WAITING_CONSENT, fixture.service.advance(1, 10, 0).status(), "First candidate invitation was not pending.");
				PhantomAssertions.assertEquals(1, fixture.party.inviteCalls, "First advance sent more than one invitation.");
				PhantomAssertions.assertEquals(0, fixture.party.respondCalls, "Invite creation auto-responded in the same advance.");
				PhantomAssertions.assertEquals(AssemblyStatus.ASSEMBLING, fixture.service.advance(1, 10, 0).status(), "Matching willingness did not accept on a later advance.");
				PhantomAssertions.assertEquals(1, fixture.party.respondCalls, "First Phantom consent response count changed.");
				PhantomAssertions.assertEquals(AssemblyStatus.WAITING_CONSENT, fixture.service.advance(1, 10, 0).status(), "Second Party was not recruited sequentially.");
				PhantomAssertions.assertEquals(2, fixture.party.inviteCalls, "Second sequential invite count changed.");
				PhantomAssertions.assertEquals(1, fixture.party.respondCalls, "Second invite auto-responded in the creation advance.");
				PhantomAssertions.assertEquals(AssemblyStatus.ASSEMBLING, fixture.service.advance(1, 10, 0).status(), "Second matching willingness did not accept later.");
				PhantomAssertions.assertEquals(AssemblyStatus.GATHERING, fixture.service.advance(1, 10, 0).status(), "Fresh CP1 did not freeze the canonical three-Party force.");
				PhantomAssertions.assertEquals(3, fixture.party.currentForce(LEADER).snapshot().parties().size(), "Canonical CommandChannel did not contain all sequentially accepted Parties.");
			}
		});
		registry.add("03-mismatched-phantom-refuses-and-real-remains-manual", _ ->
		{
			try (Fixture fixture = fixture(_topology))
			{
				fixture.party.profile(LEADER);
				fixture.party.profile(CANDIDATE_ONE);
				fixture.party.force(LEADER, standalone(LEADER, member(LEADER, tank(true))));
				fixture.party.force(CANDIDATE_ONE, standalone(CANDIDATE_ONE, member(CANDIDATE_ONE, heal(true))));
				fixture.goals.put(1, prepare(10, LIVE_CONTENT, List.of(new PhantomDomainRef("profile", "2")), null));
				fixture.goals.put(2, participate(20, "raid.other"));
				PhantomAssertions.assertEquals(AssemblyStatus.WAITING_CONSENT, fixture.service.advance(1, 10, 0).status(), "Mismatched willingness fixture did not invite.");
				PhantomAssertions.assertEquals(AssemblyStatus.ASSEMBLING, fixture.service.advance(1, 10, 0).status(), "Mismatched willingness did not terminate exact pending consent.");
				PhantomAssertions.assertEquals(Response.REFUSE, fixture.party.lastResponse, "Mismatched Phantom willingness did not REFUSE.");
				PhantomAssertions.assertEquals(1, fixture.party.currentForce(LEADER).snapshot().totalMemberCount(), "Refusal mutated canonical membership.");
			}
			try (Fixture fixture = fixture(_topology))
			{
				final MemberRef real = MemberRef.real(400);
				fixture.party.profile(LEADER);
				fixture.party.force(LEADER, standalone(LEADER, member(LEADER, tank(true))));
				fixture.party.force(real, standalone(real, member(real, heal(true))));
				fixture.goals.put(1, prepare(11, LIVE_CONTENT, List.of(new PhantomDomainRef("character.object", "400")), null));
				PhantomAssertions.assertEquals(AssemblyStatus.WAITING_CONSENT, fixture.service.advance(1, 11, 0).status(), "REAL candidate was not invited.");
				PhantomAssertions.assertEquals(AssemblyStatus.WAITING_CONSENT, fixture.service.advance(1, 11, 0).status(), "REAL candidate was not left for manual client consent.");
				PhantomAssertions.assertEquals(0, fixture.party.respondCalls, "Production assembly responded for a REAL target.");
				fixture.party.manualAccept();
				PhantomAssertions.assertEquals(AssemblyStatus.ASSEMBLING, fixture.service.advance(1, 11, 0).status(), "Manual REAL ACCEPT was not observed through canonical force.");
			}
		});
	}

	private void gathering(PhantomTestRegistry registry)
	{
		registry.add("01-structural-hash-excludes-transient-readiness", _ ->
		{
			final CurrentForceSnapshot first = readyForce(false, false).snapshot();
			final CurrentForceSnapshot transientChange = readyForce(true, false).snapshot();
			final CurrentForceSnapshot rosterChange = readyForce(false, true).snapshot();
			PhantomAssertions.assertEquals(PhantomRaidAssemblyService.structuralHash(first), PhantomRaidAssemblyService.structuralHash(transientChange), "Transient readyNow/casting changed structural force evidence.");
			PhantomAssertions.assertFalse(PhantomRaidAssemblyService.structuralHash(first).equals(PhantomRaidAssemblyService.structuralHash(rosterChange)), "Roster change did not change structural force evidence.");
		});
		registry.add("02-per-party-routes-physical-gathering-and-fresh-final-readiness", _ ->
		{
			try (Fixture fixture = readyFixture(_topology, LIVE_CONTENT, null))
			{
				PhantomAssertions.assertEquals(AssemblyStatus.GATHERING, fixture.service.advance(1, 10, 0).status(), "GROUP_READY force did not enter gathering.");
				PhantomAssertions.assertEquals(AssemblyStatus.GATHERING, fixture.service.advance(1, 10, 0).status(), "Route issuance skipped physical gathering.");
				PhantomAssertions.assertEquals(3, _routes.snapshot().routeClaims(), "Canonical Parties did not receive three separate route groups.");
				fixture.party.readyForceAtLiveSlots(false);
				PhantomAssertions.assertEquals(AssemblyStatus.FINAL_PREPARATION, fixture.service.advance(1, 10, 0).status(), "Physically staged Parties did not enter final preparation.");
				fixture.party.readyForceAtLiveSlots(true);
				PhantomAssertions.assertEquals(AssemblyStatus.FINAL_PREPARATION, fixture.service.advance(1, 10, 0).status(), "Transient capability loss incorrectly emitted READY_AT_STAGING.");
				fixture.party.readyForceAtLiveSlots(false);
				final var ready = fixture.service.advance(1, 10, 0);
				PhantomAssertions.assertEquals(AssemblyStatus.READY_AT_STAGING, ready.status(), "Fresh CP1 GROUP_READY did not emit READY_AT_STAGING.");
				PhantomAssertions.assertEquals(3, ready.readyReceipt().slots().size(), "READY receipt lost deterministic Party slots.");
				PhantomAssertions.assertEquals(StagingSource.LIVE_BOSS, ready.readyReceipt().centre().source(), "Shipped no-anchor content did not use exact live boss fallback.");
				PhantomAssertions.assertEquals(11800, ready.readyReceipt().slots().getFirst().point().x(), "Live fallback did not apply deterministic 1800 stand-off.");
				PhantomAssertions.assertTrue(ready.readyReceipt().finalReadiness().groupReady(), "READY receipt lacks fresh CP1 GROUP_READY evidence.");
				PhantomAssertions.assertEquals(0, fixture.service.snapshot().activeAssemblies(), "READY assembly retained live capacity.");
				PhantomAssertions.assertEquals(ready.readyReceipt(), fixture.service.readyReceipt(1).orElseThrow(), "READY receipt did not survive live-state release.");
			}
		});
		registry.add("03-force-and-live-centre-drift-cancel-routes-and-reassemble", _ ->
		{
			try (Fixture fixture = readyFixture(_topology, LIVE_CONTENT, null))
			{
				fixture.service.advance(1, 10, 0);
				fixture.service.advance(1, 10, 0);
				PhantomAssertions.assertEquals(3, _routes.snapshot().routeClaims(), "Drift fixture did not own per-Party routes.");
				fixture.party.forceAll(readyForce(false, true));
				PhantomAssertions.assertEquals(AssemblyStatus.ASSEMBLING, fixture.service.advance(1, 10, 0).status(), "Structural roster drift did not re-enter ASSEMBLING.");
				PhantomAssertions.assertEquals(0, _routes.snapshot().routeClaims(), "Structural drift retained raid route ownership.");
			}
			try (Fixture fixture = readyFixture(_topology, LIVE_CONTENT, null))
			{
				fixture.service.advance(1, 10, 0);
				fixture.service.advance(1, 10, 0);
				fixture.authority.location = new BossLocation(ContentKind.RAID, 100, 10600, 20000, 50, 0, NOW, "test.drift");
				PhantomAssertions.assertEquals(AssemblyStatus.ASSEMBLING, fixture.service.advance(1, 10, 0).status(), "Live centre drift >500 did not replan.");
				PhantomAssertions.assertEquals(0, _routes.snapshot().routeClaims(), "Live centre drift retained stale routes.");
			}
		});
		registry.add("04-divergent-wall-and-logical-clocks-preserve-future-route-deadline", _ ->
		{
			try (Fixture fixture = readyFixture(_topology, LIVE_CONTENT, null))
			{
				PhantomAssertions.assertEquals(AssemblyStatus.GATHERING, fixture.service.advance(1, 10, 0).status(), "Future wall-clock goal did not enter gathering.");
				PhantomAssertions.assertEquals(AssemblyStatus.GATHERING, fixture.service.advance(1, 10, 0).status(), "Divergent logical clock caused immediate route deadline expiry.");
				PhantomAssertions.assertEquals(3, _routes.snapshot().routeClaims(), "Divergent clock fixture did not retain logical-domain routes.");
			}
			try (Fixture fixture = fixture(_topology))
			{
				fixture.party.profile(LEADER);
				fixture.party.force(LEADER, standalone(LEADER, member(LEADER, tank(true))));
				final PhantomGoal expired = prepare(1, 11, 0, LIVE_CONTENT, List.of(), null, NOW);
				fixture.goals.put(1, expired);
				PhantomAssertions.assertEquals(AssemblyStatus.EXPIRED, fixture.service.advance(1, 11, 0).status(), "Expired wall-clock goal did not expire.");
			}
		});
		registry.add("05-staging-priority-and-negative-movement-scope", context ->
		{
			try (Fixture fixture = readyFixture(_topology, ANCHOR_CONTENT, new PhantomDomainRef("topology.anchor", "raid.goal.anchor")))
			{
				fixture.service.advance(1, 10, 0);
				fixture.party.readyForceAtAnchorSlots();
				fixture.service.advance(1, 10, 0);
				final var ready = fixture.service.advance(1, 10, 0);
				PhantomAssertions.assertEquals(AssemblyStatus.READY_AT_STAGING, ready.status(), "Explicit content anchor fixture did not finish.");
				PhantomAssertions.assertEquals(StagingSource.CONTENT_ANCHOR, ready.readyReceipt().centre().source(), "Goal selectedAnchor overrode explicit content topologyAnchorId.");
				PhantomAssertions.assertEquals(1300, ready.readyReceipt().slots().getFirst().point().x(), "Anchor slot did not use 300 Party-separation ring.");
			}
			try (Fixture fixture = readyFixture(_emptyTopology, ANCHOR_CONTENT, null))
			{
				PhantomAssertions.assertEquals(AssemblyStatus.BLOCKED, fixture.service.advance(1, 10, 0).status(), "Missing explicit content anchor did not fail closed.");
			}
			final String source = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAssemblyService.java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(source.contains("new PhantomPartyRouteCoordinator") == false, "Assembly constructed a hidden movement engine.");
			PhantomAssertions.assertTrue(source.contains("_routes.request") && source.contains("_routes.advance") && source.contains("party.members()"), "Assembly does not reuse existing per-Party route ownership with exact roster.");
			PhantomAssertions.assertTrue(source.contains("candidate.kind() == MemberKind.REAL") && source.contains("phantoms.isEmpty()"), "REAL manual consent or all-REAL observation-only branch is absent.");
			for (String forbidden : List.of("World.getPlayers", "teleToLocation", "setXYZ", "new CommandChannel", ".addParty(", ".removeParty(", "ThreadPool", "ScheduledFuture", "new Thread", ".attack(", ".cast(", "loot"))
			{
				PhantomAssertions.assertFalse(source.contains(forbidden), "CP4 crossed a forbidden source boundary: " + forbidden);
			}
		});
	}

	private void decision(PhantomTestRegistry registry)
	{
		registry.add("01-candidates-and-handler-mapping-follow-rift-pattern", _ ->
		{
			try (Fixture fixture = readyFixture(_topology, LIVE_CONTENT, null))
			{
				final PhantomRaidDecision decision = new PhantomRaidDecision(fixture.service, cp4DecisionAttempt(fixture.service));
				final PhantomCandidateRegistry candidates = new PhantomCandidateRegistry();
				decision.registerCandidates(candidates);
				candidates.seal();
				PhantomAssertions.assertEquals(List.of(PhantomRaidDecision.PARTICIPATE_CANDIDATE, PhantomRaidDecision.PREPARE_CANDIDATE), candidates.snapshot().stream().map(value -> value.key()).toList(), "Raid Decision candidates were not registered deterministically.");
				final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
				decision.registerHandlers(handlers);
				handlers.seal();
				final PhantomGoal goal = fixture.goals.load(1).orElseThrow().goal();
				final PhantomStepResult intermediate = execute(handlers, PhantomRaidDecision.PREPARE_ACTION, PhantomRaidDecision.PREPARE_CANDIDATE, 1, goal, false);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.REPLAN, intermediate.type(), "Intermediate raid.prepare did not map to REPLAN.");
				final PhantomStepResult cancelled = execute(handlers, PhantomRaidDecision.PREPARE_ACTION, PhantomRaidDecision.PREPARE_CANDIDATE, 1, goal, true);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.CANCELLED, cancelled.type(), "Cancelled raid.prepare did not clean and map to CANCELLED.");
				PhantomAssertions.assertEquals(0, fixture.service.snapshot().routeGroups(), "Decision cancellation retained route groups.");
			}
		});
		registry.add("02-participate-never-creates-assembly-and-production-cleanup-is-ordered", context ->
		{
			try (Fixture fixture = fixture(_topology))
			{
				fixture.party.profile(CANDIDATE_ONE);
				final PhantomGoal goal = participate(20, LIVE_CONTENT);
				fixture.goals.put(2, goal);
				final PhantomRaidDecision decision = new PhantomRaidDecision(fixture.service, cp4DecisionAttempt(fixture.service));
				final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
				decision.registerHandlers(handlers);
				handlers.seal();
				final PhantomStepResult result = execute(handlers, PhantomRaidDecision.PARTICIPATE_ACTION, PhantomRaidDecision.PARTICIPATE_CANDIDATE, 2, goal, false);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.FAIL_GOAL, result.type(), "Impossible standalone raid.participate did not fail.");
				PhantomAssertions.assertEquals(0, fixture.service.snapshot().activeAssemblies(), "raid.participate created its own leader assembly.");
			}
			final String system = Files.readString(context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java"), StandardCharsets.UTF_8);
			PhantomAssertions.assertTrue(system.contains("raidDecision.registerCandidates(candidateRegistry)") && system.contains("raidDecision.registerHandlers(handlerRegistry)"), "Raid Decision is not registered before registry sealing.");
			PhantomAssertions.assertTrue(system.indexOf("_raidAssemblyService.beginStop()") < system.indexOf("_partyCoordinator.beginStop()"), "Assembly cleanup does not precede Party teardown.");
			PhantomAssertions.assertTrue(system.indexOf("_raidAssemblyService.beginStop()") < system.indexOf("_combatService.beginStop()"), "Assembly cleanup does not precede Combat teardown.");
			PhantomAssertions.assertTrue(system.indexOf("_raidAssemblyService.beginStop()") < system.indexOf("_navigationService.beginStop()"), "Assembly cleanup does not precede Navigation teardown.");
		});
		registry.add("03-terminal-history-releases-capacity-and-preserves-exact-revision", _ ->
		{
			try (Fixture fixture = fixture(_topology))
			{
				for (int index = 0; index < PhantomRaidAssemblyService.MAX_ACTIVE_ASSEMBLIES; index++)
				{
					final long profileId = 1000L + index;
					final long goalId = 5000L + index;
					final MemberRef actor = MemberRef.phantom(profileId, 10000 + index);
					fixture.party.profile(actor);
					fixture.party.force(actor, standalone(actor, member(actor, tank(true))));
					fixture.goals.put(profileId, prepare(profileId, goalId, 0, LIVE_CONTENT, List.of(), null, DEADLINE));
					PhantomAssertions.assertTrue(fixture.service.advance(profileId, goalId, 0).status().terminal(), "Cheap assembly did not terminalize.");
				}
				PhantomAssertions.assertEquals(0, fixture.service.snapshot().activeAssemblies(), "64 terminal assemblies retained active capacity.");
				PhantomAssertions.assertEquals(64, fixture.service.snapshot().terminalAssemblies(), "Terminal history did not retain the exact 64 identities.");
				final var prior = fixture.service.advance(1000, 5000, 0);
				final var repeated = fixture.service.advance(1000, 5000, 0);
				PhantomAssertions.assertEquals(prior, repeated, "Exact terminal identity was not idempotent.");

				final MemberRef revisionCandidate = MemberRef.phantom(3000, 30000);
				fixture.party.profile(revisionCandidate);
				fixture.party.force(revisionCandidate, standalone(revisionCandidate, member(revisionCandidate, heal(true))));
				fixture.goals.put(1000, prepare(1000, 5000, 1, LIVE_CONTENT, List.of(new PhantomDomainRef("profile", "3000")), null, DEADLINE));
				PhantomAssertions.assertEquals(AssemblyStatus.WAITING_CONSENT, fixture.service.advance(1000, 5000, 1).status(), "Newer goal revision was shadowed by terminal history.");
				PhantomAssertions.assertFalse(fixture.service.cancel(1000, 5000, 0, "test.stale"), "Stale cancel affected a newer live revision.");
				PhantomAssertions.assertEquals(1, fixture.service.snapshot().activeAssemblies(), "Stale cancel released newer live revision.");
				PhantomAssertions.assertTrue(fixture.service.cancel(1000, 5000, 1, "test.cleanup"), "Exact newer revision cleanup failed.");

				final MemberRef leader65 = MemberRef.phantom(2000, 20000);
				final MemberRef candidate65 = MemberRef.phantom(3001, 30001);
				fixture.party.profile(leader65);
				fixture.party.profile(candidate65);
				fixture.party.force(leader65, standalone(leader65, member(leader65, tank(true))));
				fixture.party.force(candidate65, standalone(candidate65, member(candidate65, heal(true))));
				fixture.goals.put(2000, prepare(2000, 6000, 0, LIVE_CONTENT, List.of(new PhantomDomainRef("profile", "3001")), null, DEADLINE));
				PhantomAssertions.assertEquals(AssemblyStatus.WAITING_CONSENT, fixture.service.advance(2000, 6000, 0).status(), "65th distinct live leader was rejected after 64 terminal assemblies.");
				PhantomAssertions.assertEquals(1, fixture.service.snapshot().activeAssemblies(), "65th leader was not admitted as live state.");
				PhantomAssertions.assertTrue(fixture.service.snapshot().terminalAssemblies() <= PhantomRaidAssemblyService.MAX_RECEIPTS, "Terminal history exceeded its bound.");
			}
		});
		registry.add("04-late-participation-completes-only-for-exact-ready-force-member", _ ->
		{
			try (Fixture fixture = readyFixture(_topology, LIVE_CONTENT, null))
			{
				final PhantomGoal participantGoal = participate(20, LIVE_CONTENT);
				fixture.goals.put(2, participantGoal);
				fixture.goals.put(3, participate(30, LIVE_CONTENT));
				final PhantomRaidDecision decision = new PhantomRaidDecision(fixture.service, cp4DecisionAttempt(fixture.service));
				final PhantomStepHandlerRegistry handlers = new PhantomStepHandlerRegistry();
				decision.registerHandlers(handlers);
				handlers.seal();
				fixture.service.advance(1, 10, 0);
				fixture.service.advance(1, 10, 0);
				fixture.party.readyForceAtLiveSlots(false);
				fixture.service.advance(1, 10, 0);
				PhantomAssertions.assertEquals(AssemblyStatus.READY_AT_STAGING, fixture.service.advance(1, 10, 0).status(), "Late participation fixture did not reach READY.");
				final PhantomStepResult late = execute(handlers, PhantomRaidDecision.PARTICIPATE_ACTION, PhantomRaidDecision.PARTICIPATE_CANDIDATE, 2, participantGoal, false);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.COMPLETE_GOAL, late.type(), "Exact joined candidate did not complete raid.participate after leader READY.");

				final MemberRef unrelated = MemberRef.phantom(4, 400);
				fixture.party.profile(unrelated);
				final PhantomGoal unrelatedGoal = participate(40, LIVE_CONTENT);
				fixture.goals.put(4, unrelatedGoal);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.FAIL_GOAL, execute(handlers, PhantomRaidDecision.PARTICIPATE_ACTION, PhantomRaidDecision.PARTICIPATE_CANDIDATE, 4, unrelatedGoal, false).type(), "Unrelated same-content candidate inherited READY membership.");

				final PhantomGoal mismatched = participate(30, "raid.other");
				fixture.goals.put(3, mismatched);
				PhantomAssertions.assertEquals(PhantomStepResult.Type.FAIL_GOAL, execute(handlers, PhantomRaidDecision.PARTICIPATE_ACTION, PhantomRaidDecision.PARTICIPATE_CANDIDATE, 3, mismatched, false).type(), "Mismatched content inherited READY membership.");

				final PhantomGoal expired = participate(31, LIVE_CONTENT, NOW);
				fixture.goals.put(3, expired);
				PhantomAssertions.assertEquals(PhantomRaidAssemblyService.ParticipationOutcome.EXPIRED, fixture.service.participation(3, 31, 0), "Expired participation did not remain EXPIRED.");
				PhantomAssertions.assertEquals(PhantomStepResult.Type.FAIL_GOAL, execute(handlers, PhantomRaidDecision.PARTICIPATE_ACTION, PhantomRaidDecision.PARTICIPATE_CANDIDATE, 3, expired, false).type(), "Expired participation did not fail its goal.");
			}
		});
	}

	private Fixture recruitmentFixture()
	{
		final Fixture fixture = fixture(_topology);
		fixture.party.profile(LEADER);
		fixture.party.profile(CANDIDATE_ONE);
		fixture.party.profile(CANDIDATE_TWO);
		fixture.party.force(LEADER, standalone(LEADER, member(LEADER, tank(true))));
		fixture.party.force(CANDIDATE_ONE, standalone(CANDIDATE_ONE, member(CANDIDATE_ONE, heal(true))));
		fixture.party.force(CANDIDATE_TWO, standalone(CANDIDATE_TWO, member(CANDIDATE_TWO)));
		fixture.goals.put(1, prepare(10, LIVE_CONTENT, List.of(new PhantomDomainRef("profile", "2"), new PhantomDomainRef("profile", "3")), null));
		fixture.goals.put(2, participate(20, LIVE_CONTENT));
		fixture.goals.put(3, participate(30, LIVE_CONTENT));
		return fixture;
	}

	private Fixture readyFixture(PhantomTopologyQuery topology, String content, PhantomDomainRef selectedAnchor)
	{
		final Fixture fixture = fixture(topology);
		fixture.party.profile(LEADER);
		fixture.party.profile(CANDIDATE_ONE);
		fixture.party.profile(CANDIDATE_TWO);
		fixture.party.forceAll(readyForce(false, false));
		fixture.goals.put(1, prepare(10, content, List.of(new PhantomDomainRef("profile", "2"), new PhantomDomainRef("profile", "3")), selectedAnchor));
		return fixture;
	}

	private Fixture fixture(PhantomTopologyQuery topology)
	{
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend party = new MemoryPartyBackend();
		final StubRaidAuthority authority = new StubRaidAuthority();
		final PhantomRaidReadinessService readiness = new PhantomRaidReadinessService(_knowledge, party, authority);
		final PhantomRaidRecruitmentService recruitment = new PhantomRaidRecruitmentService(readiness, party);
		final PhantomRaidAssemblyService service = new PhantomRaidAssemblyService(goals, readiness, recruitment, party, authority, () -> topology, _routes, () -> NOW, () -> LOGICAL_NOW, (x, y, factualZ) -> factualZ);
		return new Fixture(goals, party, authority, service);
	}

	private static PhantomRaidDecision.AttemptPort cp4DecisionAttempt(PhantomRaidAssemblyService assembly)
	{
		return new PhantomRaidDecision.AttemptPort()
		{
			@Override
			public org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.AdvanceResult advance(long leaderProfileId, long goalId, long goalRevision)
			{
				return new org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.AdvanceResult(org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.AttemptStatus.FIGHTING, "raid.attempt.cp4_active", null);
			}

			@Override
			public boolean cancel(long leaderProfileId, long goalId, long goalRevision, String reasonKey)
			{
				return true;
			}

			@Override
			public org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.ParticipationStatus participation(long profileId, long goalId, long goalRevision)
			{
				return switch (assembly.participation(profileId, goalId, goalRevision))
				{
					case WAITING -> org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.ParticipationStatus.WAITING_FOR_LEADER;
					case JOINED -> org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.ParticipationStatus.VICTORY;
					case EXPIRED -> org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.ParticipationStatus.EXPIRED;
					case IMPOSSIBLE -> org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAttemptService.ParticipationStatus.FAILED;
				};
			}
		};
	}

	private static PhantomStepResult execute(PhantomStepHandlerRegistry handlers, String action, String candidate, long profileId, PhantomGoal goal, boolean cancelled)
	{
		final PhantomPlanStep step = new PhantomPlanStep(0, action, goal.target(), Map.of(), 60_000, 1, action + ".test");
		final PhantomPlan plan = new PhantomPlan(1, goal.goalId(), candidate, List.of(step), 60_000, 1);
		return handlers.snapshot().get(action).execute(new PhantomStepContext(profileId, goal, plan, step, PhantomActivityState.ACTIVE, 1, 1, cancelled ? () -> true : () -> false));
	}

	private static PhantomGoal prepare(long goalId, String content, List<PhantomDomainRef> sources, PhantomDomainRef selectedAnchor)
	{
		return prepare(1, goalId, 0, content, sources, selectedAnchor, DEADLINE);
	}

	private static PhantomGoal prepare(long profileId, long goalId, long revision, String content, List<PhantomDomainRef> sources, PhantomDomainRef selectedAnchor, long deadline)
	{
		return new PhantomGoal(goalId, PhantomRaidAssemblyService.PREPARE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef("raid.content", content), 1, 0, null, sources, selectedAnchor, "raid.prepare", 500, 0, 0, deadline, Map.of(), "raid.prepare.test", revision);
	}

	private static PhantomGoal participate(long goalId, String content)
	{
		return participate(goalId, content, DEADLINE);
	}

	private static PhantomGoal participate(long goalId, String content, long deadline)
	{
		return new PhantomGoal(goalId, PhantomRaidAssemblyService.PARTICIPATE_GOAL_TYPE, PhantomGoalStatus.ACTIVE, null, new PhantomDomainRef("raid.content", content), 1, 0, null, List.of(), null, "raid.participate", 500, 0, 0, deadline, Map.of(), "raid.participate.test", 0);
	}

	private static CurrentForceObservation readyForce(boolean transientNotReady, boolean rosterChange)
	{
		final List<PartySnapshot> parties = new ArrayList<>();
		final List<MemberSnapshot> members = new ArrayList<>();
		members.add(member(LEADER, 0, 0, false, tank(true)));
		members.add(member(CANDIDATE_ONE, 0, 0, transientNotReady, heal(!transientNotReady)));
		members.add(member(CANDIDATE_TWO, 0, 0, false));
		parties.add(party(LEADER, LEADER));
		parties.add(party(CANDIDATE_ONE, CANDIDATE_ONE));
		parties.add(party(CANDIDATE_TWO, CANDIDATE_TWO));
		if (rosterChange)
		{
			final MemberRef added = MemberRef.real(900);
			members.add(member(added, 0, 0, false));
			parties.set(0, party(LEADER, LEADER, added));
		}
		return channel(LEADER, LEADER, parties, members);
	}

	private static CurrentForceObservation standalone(MemberRef actor, MemberSnapshot... members)
	{
		final List<MemberSnapshot> snapshots = List.of(members);
		return CurrentForceObservation.available(new CurrentForceSnapshot(actor, actor, "", null, 0, snapshots.size(), List.of(party(actor, snapshots.stream().map(MemberSnapshot::ref).toArray(MemberRef[]::new))), snapshots));
	}

	private static CurrentForceObservation channel(MemberRef actor, MemberRef channelLeader, List<PartySnapshot> parties, List<MemberSnapshot> members)
	{
		final MemberRef partyLeader = parties.stream().filter(party -> party.members().contains(actor)).map(PartySnapshot::leader).findFirst().orElseThrow();
		return CurrentForceObservation.available(new CurrentForceSnapshot(actor, partyLeader, "command-channel:" + channelLeader.characterObjectId(), channelLeader, 1, members.size(), parties, members));
	}

	private static PartySnapshot party(MemberRef leader, MemberRef... members)
	{
		return new PartySnapshot(leader, List.of(members), PartyDistributionType.FINDERS_KEEPERS);
	}

	private static MemberSnapshot member(MemberRef ref, MemberCapability... capabilities)
	{
		return member(ref, 0, 0, false, capabilities);
	}

	private static MemberSnapshot member(MemberRef ref, int x, int y, boolean casting, MemberCapability... capabilities)
	{
		return new MemberSnapshot(ref, 1, 0, x, y, 50, 100, 100, 100, false, casting, false, false, 0, List.of(), List.of(capabilities), HASH);
	}

	private static MemberCapability tank(boolean ready)
	{
		return capability("combat.tank", 900, ready);
	}

	private static MemberCapability heal(boolean ready)
	{
		return capability("combat.heal", 900, ready);
	}

	private static MemberCapability capability(String key, int rank, boolean ready)
	{
		return new MemberCapability(key, "test", rank, 500, 1, "SELF", true, true, ready, ready ? "ready" : "not.ready", rank, "goal026cp4.fixture");
	}

	private static PhantomTopologyQuery topology(boolean anchors)
	{
		final TopologyBackend backend = new TopologyBackend();
		final PhantomTopologyNode node = new PhantomTopologyNode("raid.staging.area", PhantomTopologyNodeKind.OUTDOOR_AREA, 0, PhantomTopologyArea.cuboid(0, -5000, 5000, -5000, 5000, -1000, 1000), null, List.of(), List.of());
		final List<PhantomTopologyAnchor> values = anchors ? List.of(
			new PhantomTopologyAnchor("raid.content.anchor", PhantomTopologyAnchorRole.ROUTE, node.id(), new PhantomTopologyPoint(1000, 1000, 25, 0), null, null, 100, List.of(), List.of()),
			new PhantomTopologyAnchor("raid.goal.anchor", PhantomTopologyAnchorRole.ROUTE, node.id(), new PhantomTopologyPoint(3000, 3000, 25, 0), null, null, 100, List.of(), List.of())) : List.of();
		final PhantomTopologySnapshot snapshot = PhantomTopologySnapshot.create(1, anchors ? "raid-cp4" : "raid-cp4-empty", 1, 1, List.of(node), values, List.of(), backend, PhantomTopologyPolicy.productionDefaults());
		return new PhantomTopologyQuery(snapshot, backend, new PhantomTopologyMetrics());
	}

	private static String curatedXml()
	{
		final StringBuilder result = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<knowledge schemaVersion=\"1\" datasetId=\"raid-cp4\" datasetVersion=\"1\">\n");
		for (String capability : PhantomGameKnowledgeBuilder.REQUIRED_CAPABILITIES.stream().sorted().toList())
		{
			result.append("\t<classCapability classId=\"1\" capabilityKey=\"").append(capability).append("\" rank=\"1000\">\n\t\t<skill id=\"500\" level=\"1\" />\n\t\t<source path=\"data/source.xml\" />\n\t</classCapability>\n");
		}
		result.append("""
				<contentRequirement contentId="rift.cp4.fixture" contentKind="RIFT" recommendedMinParty="1" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<source path="data/source.xml" />
				</contentRequirement>
				<contentRequirement contentId="epic.cp4.fixture" contentKind="EPIC" npcId="101" recommendedMinParty="3" recommendedMaxParty="45">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="850" required="true" />
					<requirement capabilityKey="combat.heal" minimumCount="1" minimumRank="900" required="true" />
					<requirement capabilityKey="combat.resurrection" minimumCount="1" minimumRank="900" required="true" />
					<requirement capabilityKey="combat.buff" minimumCount="1" minimumRank="850" required="false" />
					<source path="data/source.xml" />
				</contentRequirement>
				<contentRequirement contentId="raid.cp4.live" contentKind="RAID" npcId="100" recommendedMinParty="3" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<requirement capabilityKey="combat.heal" minimumCount="1" minimumRank="850" required="true" />
					<source path="data/source.xml" />
				</contentRequirement>
				<contentRequirement contentId="raid.cp4.anchor" contentKind="RAID" npcId="100" topologyAnchorId="raid.content.anchor" recommendedMinParty="3" recommendedMaxParty="9">
					<requirement capabilityKey="combat.tank" minimumCount="1" minimumRank="800" required="true" />
					<requirement capabilityKey="combat.heal" minimumCount="1" minimumRank="850" required="true" />
					<source path="data/source.xml" />
				</contentRequirement>
			</knowledge>
			""");
		return result.toString();
	}

	private final class Fixture implements AutoCloseable
	{
		private final MemoryGoalStore goals;
		private final MemoryPartyBackend party;
		private final StubRaidAuthority authority;
		private final PhantomRaidAssemblyService service;

		private Fixture(MemoryGoalStore goals, MemoryPartyBackend party, StubRaidAuthority authority, PhantomRaidAssemblyService service)
		{
			this.goals = goals;
			this.party = party;
			this.authority = authority;
			this.service = service;
		}

		@Override
		public void close()
		{
			service.beginStop();
			PhantomAssertions.assertEquals(0, _routes.snapshot().routeClaims(), "Fixture cleanup retained route claims.");
		}
	}

	private static final class StubRaidAuthority implements PhantomRaidAuthority
	{
		private BossLocation location = new BossLocation(ContentKind.RAID, 100, 10000, 20000, 50, 0, NOW, "test.live");

		@Override
		public BossObservation observe(ContentKind contentKind, int npcId)
		{
			return new BossObservation(contentKind, npcId, true, contentKind == ContentKind.RAID ? "ALIVE" : "1", true, true, false, 0L, NOW, "test.available");
		}

		@Override
		public Optional<BossLocation> observeLocation(ContentKind contentKind, int npcId)
		{
			return (location.contentKind() == contentKind) && (location.npcId() == npcId) ? Optional.of(location) : Optional.empty();
		}
	}

	private static final class MemoryGoalStore implements PhantomGoalStore
	{
		private final Map<Long, StoredGoal> values = new HashMap<>();

		private void put(long profileId, PhantomGoal goal)
		{
			values.put(profileId, new StoredGoal(goal, 0));
		}

		@Override public boolean profileExists(long profileId) { return values.containsKey(profileId); }
		@Override public Optional<StoredGoal> load(long profileId) { return Optional.ofNullable(values.get(profileId)); }
		@Override public StoredGoal insert(long profileId, PhantomGoal goal) { final StoredGoal value = new StoredGoal(goal, 0); values.put(profileId, value); return value; }
		@Override public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal) { final StoredGoal value = new StoredGoal(goal, expectedRowVersion + 1); values.put(profileId, value); return value; }
		@Override public void delete(long profileId, long expectedRowVersion) { values.remove(profileId); }
	}

	private static final class MemoryPartyBackend implements PhantomPartyBackend
	{
		private final Map<Long, MemberRef> profiles = new HashMap<>();
		private final Map<Integer, Long> managed = new HashMap<>();
		private final Map<MemberRef, CurrentForceObservation> forces = new HashMap<>();
		private int inviteCalls;
		private int respondCalls;
		private int cancelCalls;
		private long sequence;
		private MemberRef pendingRequester;
		private MemberRef pendingTarget;
		private InvitationIdentity pendingIdentity;
		private Response lastResponse;

		private void profile(MemberRef ref)
		{
			profiles.put(ref.profileId(), ref);
			managed.put(ref.characterObjectId(), ref.profileId());
		}

		private void force(MemberRef actor, CurrentForceObservation force)
		{
			forces.put(actor, force);
		}

		private void forceAll(CurrentForceObservation observation)
		{
			final CurrentForceSnapshot snapshot = observation.snapshot();
			for (MemberSnapshot member : snapshot.members())
			{
				forces.put(member.ref(), channel(member.ref(), snapshot.commandChannelLeader(), snapshot.parties(), snapshot.members()));
			}
		}

		private void readyForceAtLiveSlots(boolean transientNotReady)
		{
			final List<PartySnapshot> parties = List.of(party(LEADER, LEADER), party(CANDIDATE_ONE, CANDIDATE_ONE), party(CANDIDATE_TWO, CANDIDATE_TWO));
			final List<MemberSnapshot> members = List.of(
				member(LEADER, 11800, 20000, false, tank(true)),
				member(CANDIDATE_ONE, 9100, 21559, false, heal(!transientNotReady)),
				member(CANDIDATE_TWO, 9100, 18441, false));
			forceAll(channel(LEADER, LEADER, parties, members));
		}

		private void readyForceAtAnchorSlots()
		{
			final List<PartySnapshot> parties = List.of(party(LEADER, LEADER), party(CANDIDATE_ONE, CANDIDATE_ONE), party(CANDIDATE_TWO, CANDIDATE_TWO));
			final List<MemberSnapshot> members = List.of(
				member(LEADER, 1300, 1000, false, tank(true)),
				member(CANDIDATE_ONE, 850, 1260, false, heal(true)),
				member(CANDIDATE_TWO, 850, 740, false));
			forceAll(channel(LEADER, LEADER, parties, members));
		}

		@Override public OptionalLong managedProfileId(int characterObjectId) { final Long profile = managed.get(characterObjectId); return profile == null ? OptionalLong.empty() : OptionalLong.of(profile); }
		@Override public Optional<MemberRef> currentMember(long profileId) { return Optional.ofNullable(profiles.get(profileId)); }
		@Override public CurrentForceObservation currentForce(MemberRef actor) { return forces.getOrDefault(actor, CurrentForceObservation.unavailable("test.force.missing")); }

		@Override
		public InviteResult inviteCommandChannel(MemberRef requester, MemberRef target)
		{
			inviteCalls++;
			pendingRequester = requester;
			pendingTarget = target;
			pendingIdentity = new InvitationIdentity(++sequence, requester.characterObjectId(), target.characterObjectId());
			return new InviteResult(InviteOutcome.DELIVERED, pendingIdentity);
		}

		@Override
		public CommandChannelInvitationService.RespondResult respondCommandChannel(MemberRef invitee, Response response, InvitationIdentity identity)
		{
			respondCalls++;
			lastResponse = response;
			if ((pendingIdentity == null) || !pendingTarget.equals(invitee) || !pendingIdentity.equals(identity))
			{
				return new CommandChannelInvitationService.RespondResult(RespondOutcome.STALE_INVITE, identity, false);
			}
			if (response == Response.REFUSE)
			{
				clearPending();
				return new CommandChannelInvitationService.RespondResult(RespondOutcome.REFUSED, identity, false);
			}
			joinPending();
			return new CommandChannelInvitationService.RespondResult(RespondOutcome.ACCEPTED, identity, true);
		}

		private void manualAccept()
		{
			joinPending();
		}

		private void joinPending()
		{
			final CurrentForceSnapshot requester = forces.get(pendingRequester).snapshot();
			final CurrentForceSnapshot target = forces.get(pendingTarget).snapshot();
			final List<PartySnapshot> parties = new ArrayList<>(requester.parties());
			parties.addAll(target.parties());
			final List<MemberSnapshot> members = new ArrayList<>(requester.members());
			members.addAll(target.members());
			final MemberRef channelLeader = requester.commandChannelPresent() ? requester.commandChannelLeader() : pendingRequester;
			clearPending();
			for (MemberSnapshot member : members)
			{
				forces.put(member.ref(), channel(member.ref(), channelLeader, parties, members));
			}
		}

		@Override
		public Optional<CommandChannelInvitationService.InvitationSnapshot> observeCommandChannelInvitation(MemberRef invitee)
		{
			return (pendingIdentity != null) && pendingTarget.equals(invitee) ? Optional.of(new CommandChannelInvitationService.InvitationSnapshot(pendingIdentity, pendingRequester.characterObjectId(), pendingTarget.characterObjectId(), pendingRequester.characterObjectId(), DEADLINE)) : Optional.empty();
		}

		@Override
		public CancelResult cancelCommandChannel(InvitationIdentity identity)
		{
			cancelCalls++;
			if (pendingIdentity == null)
			{
				return new CancelResult(CancelOutcome.NO_PENDING_INVITE, identity);
			}
			if (!pendingIdentity.equals(identity))
			{
				return new CancelResult(CancelOutcome.STALE_INVITE, identity);
			}
			clearPending();
			return new CancelResult(CancelOutcome.CANCELLED, identity);
		}

		private void clearPending()
		{
			pendingRequester = null;
			pendingTarget = null;
			pendingIdentity = null;
		}

		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution) { throw new UnsupportedOperationException(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.RespondResult respond(MemberRef invitee, org.l2jmobius.gameserver.model.groups.PartyInvitationService.Response response, org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity identity) { throw new UnsupportedOperationException(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome leave(MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome expel(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public org.l2jmobius.gameserver.model.groups.PartyInvitationService.MembershipOutcome transferLeader(MemberRef requester, MemberRef member) { throw new UnsupportedOperationException(); }
		@Override public Optional<PartySnapshot> observe(MemberRef member) { return currentForce(member).snapshot() == null ? Optional.empty() : currentForce(member).snapshot().parties().stream().filter(party -> party.members().contains(member)).findFirst(); }
		@Override public Optional<MemberSnapshot> memberSnapshot(MemberRef member) { return currentForce(member).snapshot() == null ? Optional.empty() : currentForce(member).snapshot().members().stream().filter(value -> value.ref().equals(member)).findFirst(); }
		@Override public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId) { return List.of(); }
		@Override public boolean materialize(long profileId) { return false; }
	}

	private static final class TopologyBackend implements PhantomTopologyValidationBackend
	{
		@Override public int mapRegionLocId(int x, int y) { return 5; }
		@Override public Optional<NpcFact> npc(int npcId) { return Optional.empty(); }
		@Override public List<SpawnFact> spawns(int npcId, int maximumResults) { return List.of(); }
		@Override public Optional<DoorFact> door(int doorId) { return Optional.empty(); }
		@Override public DoorState doorState(int doorId) { return DoorState.MISSING; }
		@Override public boolean sourceExists(String relativeDatapackPath) { return true; }
	}
}

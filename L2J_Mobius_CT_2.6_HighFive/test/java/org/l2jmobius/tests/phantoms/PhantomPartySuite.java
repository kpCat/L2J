/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.gameserver.phantoms.activity.PhantomCompositeSchedulerControlPort;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRouteCoordinator;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCapability;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleMatcher;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyStateCodec;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyTactics;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.ObjectiveMode;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.OperationPhase;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyOperation;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleDefinition;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RoleRequirement;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteManifest;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.RouteStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.StateStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.VacancyStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.model.groups.PartyDistributionType;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PartyInvitation;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.PreparationOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.TerminalOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteOutcome;
import org.l2jmobius.gameserver.model.groups.PartyInvitationService.InviteResult;
import org.l2jmobius.gameserver.phantoms.semantic.PhantomPartySemanticActs;
import org.l2jmobius.gameserver.phantoms.semantic.PhantomSemanticAct;

public final class PhantomPartySuite implements PhantomTestSuite
{
	public enum Mode
	{
		CANONICAL_INVITATION,
		STATE_RECOVERY,
		ROLE_VACANCY,
		SEMANTIC_ACTS,
		ROUTE,
		TACTICS,
		LIFECYCLE,
		SERVER_INTEGRATION,
		PERFORMANCE
	}

	private static final String ZERO = "0".repeat(64);
	private final Mode _mode;

	public PhantomPartySuite(Mode mode)
	{
		_mode = mode;
	}

	@Override
	public String id()
	{
		return "party-" + _mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		switch (_mode)
		{
			case CANONICAL_INVITATION -> canonical(registry);
			case STATE_RECOVERY -> state(registry);
			case ROLE_VACANCY -> roles(registry);
			case SEMANTIC_ACTS -> semantics(registry);
			case ROUTE -> route(registry);
			case TACTICS -> tactics(registry);
			case LIFECYCLE -> lifecycle(registry);
			case SERVER_INTEGRATION -> integration(registry);
			case PERFORMANCE -> performance(registry);
		}
	}

	private static void canonical(PhantomTestRegistry registry)
	{
		registry.add("01-both-packet-handlers-delegate", context ->
		{
			final String request = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/RequestJoinParty.java");
			final String answer = source(context, "java/org/l2jmobius/gameserver/network/clientpackets/RequestAnswerJoinParty.java");
			PhantomAssertions.assertTrue(request.contains("PartyInvitationService.getInstance().invite"), "Join handler does not delegate to the canonical service.");
			PhantomAssertions.assertTrue(answer.contains("PartyInvitationService.getInstance()") && answer.contains("service.respond"), "Answer handler does not delegate to the canonical service.");
		});
		registry.add("02-core-is-transport-neutral", context ->
		{
			final String service = source(context, "java/org/l2jmobius/gameserver/model/groups/PartyInvitationService.java");
			PhantomAssertions.assertFalse(service.contains("gameserver.phantoms"), "Core invitation service imports Phantom implementation.");
			PhantomAssertions.assertFalse(service.contains("new GameClient"), "Core invitation service creates a fake client.");
			PhantomAssertions.assertTrue(service.contains("DELIVERED_CLIENT") && service.contains("DELIVERED_MANAGED"), "Canonical delivery outcomes are incomplete.");
		});
		registry.add("03-managed-delivery-carries-exact-identity", _ ->
		{
			final var identity = new org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity(7, 101, 202);
			PhantomAssertions.assertEquals(7L, identity.sequence(), "Invitation sequence changed.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new org.l2jmobius.gameserver.model.groups.PartyInvitationService.InvitationIdentity(0, 101, 202), "Zero invitation sequence was accepted.");
		});
	}

	private static void state(PhantomTestRegistry registry)
	{
		registry.add("01-binary-roundtrip-and-bound", context ->
		{
			final PartyState state = stateFixture();
			final PhantomPartyStateCodec codec = new PhantomPartyStateCodec();
			final byte[] payload = codec.encode(state);
			PhantomAssertions.assertTrue(payload.length <= 4096, "Party state exceeds profile component envelope.");
			PhantomAssertions.assertEquals(state, codec.decode(payload), "Party state binary roundtrip changed the claim.");
			context.record("partyState.payloadBytes", payload.length);
		});
		registry.add("02-every-saga-phase-is-representable", _ ->
		{
			for (OperationPhase phase : OperationPhase.values())
			{
				final PartyOperation operation = operationFixture().withPhase(phase, phase == OperationPhase.CANONICAL_PENDING ? 9 : 0, phase == OperationPhase.ABORTED ? "invite.refused" : "");
				PhantomAssertions.assertEquals(phase, operation.phase(), "Party saga phase was not retained.");
			}
		});
		registry.add("03-roster-is-capped-at-nine", _ ->
		{
			final MemberRef leader = MemberRef.phantom(1, 101);
			final List<MemberRef> ten = java.util.stream.LongStream.rangeClosed(1, 10).mapToObj(value -> MemberRef.phantom(value, 100 + (int) value)).toList();
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> rawState(leader, ten, List.of()), "Ten-member Phantom roster was accepted.");
		});
		registry.add("04-restart-contract-does-not-replay-real-consent", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java");
			PhantomAssertions.assertTrue(source.contains("restart.real_consent_not_restored") && source.contains("List.of(), state.objectiveMode()"), "Restart real-consent stripping is absent.");
		});
		registry.add("05-member-claim-precedes-canonical-invite", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java");
			final int prepared = source.indexOf("preparedMember = save");
			final int canonical = source.indexOf("_backend.invite(current.leader(), target, distribution)");
			PhantomAssertions.assertTrue((prepared >= 0) && (canonical > prepared), "Canonical invite precedes durable Phantom member claim.");
			PhantomAssertions.assertTrue(source.contains("abortManagedInvitation") && source.contains("moveToSolo"), "Terminal invitation rollback is missing.");
		});
		registry.add("06-stale-leader-goal-revision-cannot-transition", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyCoordinator.java");
			PhantomAssertions.assertTrue(source.contains("goal.revision() != operation.leaderGoalRevision()"), "Leader goal transition does not reject a stale revision.");
			PhantomAssertions.assertTrue(source.contains("goalTargets(goal, operation.leader().characterObjectId())"), "Member goal transition does not retain exact inviter consent.");
		});
	}

	private static void roles(PhantomTestRegistry registry)
	{
		registry.add("01-current-role-catalog-is-strict-and-hashed", context ->
		{
			final PhantomPartyRoleCatalog catalog = currentCatalog(context);
			PhantomAssertions.assertEquals(64, catalog.hash().length(), "Role catalog is not content addressed.");
			PhantomAssertions.assertTrue(catalog.contains("support.healer") && catalog.contains("frontline.guardian"), "Required generic role mappings are absent.");
		});
		registry.add("02-multi-capability-evidence-is-preserved", context ->
		{
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final MemberSnapshot support = member(2, 102, 100, 100, false, 0, List.of(cap("combat.heal", "major", 50, 1001), cap("combat.resurrection", "revive", 45, 1002)));
			final var result = matcher.match(ObjectiveMode.RECOVERY, List.of(new RoleRequirement("slot.heal", "support.healer", true, 1)), List.of(support));
			PhantomAssertions.assertEquals(2, support.capabilities().size(), "Secondary capability fact was lost.");
			PhantomAssertions.assertEquals(VacancyStatus.FILLED, result.vacancies().getFirst().status(), "Contextual healer vacancy was not filled.");
		});
		registry.add("03-missing-optional-unsupported-are-distinct", context ->
		{
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final var result = matcher.match(ObjectiveMode.GENERAL_PVE, List.of(new RoleRequirement("slot.required", "support.healer", true, 9000), new RoleRequirement("slot.optional", "support.recharge", false, 9000), new RoleRequirement("slot.future", "future.unknown", true, 1)), List.of(member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "basic", 1, 1)))));
			PhantomAssertions.assertEquals(List.of(VacancyStatus.MISSING, VacancyStatus.OPTIONAL, VacancyStatus.UNSUPPORTED), result.vacancies().stream().map(value -> value.status()).sorted().toList(), "Vacancy states collapsed.");
		});
		registry.add("04-objective-context-affects-threshold", _ ->
		{
			final EnumMap<ObjectiveMode, Integer> weights = new EnumMap<>(ObjectiveMode.class);
			weights.put(ObjectiveMode.RECOVERY, 1000);
			final PhantomPartyRoleCatalog catalog = new PhantomPartyRoleCatalog(Map.of("context.role", new RoleDefinition("context.role", Map.of("combat.heal", 1), weights, true)), PhantomPartyModel.sha256("context.catalog"));
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(catalog);
			final RoleRequirement requirement = new RoleRequirement("slot.context", "context.role", true, 900);
			final MemberSnapshot member = member(1, 101, 100, 100, false, 0, List.of(cap("combat.heal", "small", 1, 1)));
			PhantomAssertions.assertEquals(VacancyStatus.MISSING, matcher.match(ObjectiveMode.GENERAL_PVE, List.of(requirement), List.of(member)).vacancies().getFirst().status(), "General context crossed recovery threshold.");
			PhantomAssertions.assertEquals(VacancyStatus.FILLED, matcher.match(ObjectiveMode.RECOVERY, List.of(requirement), List.of(member)).vacancies().getFirst().status(), "Recovery context did not affect role suitability.");
		});
		registry.add("05-required-vacancy-precedes-optional", context ->
		{
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final MemberSnapshot member = member(1, 101, 100, 100, false, 0, List.of(cap("combat.melee_damage", "basic", 10, 1)));
			final var result = matcher.match(ObjectiveMode.GENERAL_PVE, List.of(new RoleRequirement("slot.a.optional", "damage.melee", false, 1), new RoleRequirement("slot.z.required", "damage.melee", true, 1)), List.of(member));
			PhantomAssertions.assertEquals(VacancyStatus.FILLED, result.vacancies().stream().filter(value -> value.vacancyKey().equals("slot.z.required")).findFirst().orElseThrow().status(), "Required vacancy did not receive assignment priority.");
			PhantomAssertions.assertEquals(VacancyStatus.OPTIONAL, result.vacancies().stream().filter(value -> value.vacancyKey().equals("slot.a.optional")).findFirst().orElseThrow().status(), "Optional vacancy consumed the only eligible member.");
		});
		registry.add("06-maximum-matching-beats-greedy-counterexample", _ ->
		{
			final PhantomPartyRoleCatalog catalog = new PhantomPartyRoleCatalog(Map.of(
				"support.heal", new RoleDefinition("support.heal", Map.of("combat.heal", 10), Map.of(), true),
				"support.recharge", new RoleDefinition("support.recharge", Map.of("combat.recharge", 10), Map.of(), true)),
				PhantomPartyModel.sha256("maximum.matching.catalog"));
			final MemberSnapshot flexible = member(1, 101, 100, 100, false, 0, List.of(cap("combat.heal", "strong", 100, 1001), cap("combat.recharge", "only", 50, 1002)));
			final MemberSnapshot healerOnly = member(2, 102, 100, 100, false, 0, List.of(cap("combat.heal", "only", 50, 1001)));
			final var result = new PhantomPartyRoleMatcher(catalog).match(ObjectiveMode.RECOVERY, List.of(new RoleRequirement("slot.heal", "support.heal", true, 1), new RoleRequirement("slot.recharge", "support.recharge", true, 1)), List.of(flexible, healerOnly));
			PhantomAssertions.assertEquals(2, result.assignments().size(), "Global role assignment left a required vacancy open.");
			PhantomAssertions.assertEquals(flexible.ref(), result.assignments().stream().filter(value -> value.vacancyKey().equals("slot.recharge")).findFirst().orElseThrow().member(), "Flexible member was greedily consumed by HEAL.");
		});
	}

	private static void semantics(PhantomTestRegistry registry)
	{
		registry.add("01-all-acts-are-string-keyed-and-stable", _ ->
		{
			final PhantomSemanticAct act = semantic(1);
			PhantomAssertions.assertEquals(act.canonicalHash(), semantic(1).canonicalHash(), "Semantic act hash is not deterministic.");
			PhantomAssertions.assertTrue(PhantomPartySemanticActs.KEYS.size() >= 12, "Party semantic act vocabulary is incomplete.");
		});
		registry.add("02-stale-generation-cannot-dispatch", _ ->
		{
			final AtomicInteger mutations = new AtomicInteger();
			PhantomAssertions.assertFalse(PhantomPartySemanticActs.dispatchIfCurrent(semantic(1), generation -> generation == 2 ? semantic(1).groupId() : ZERO, ignored -> mutations.incrementAndGet()), "Stale semantic act dispatched.");
			PhantomAssertions.assertEquals(0, mutations.get(), "A semantic act mutated state by itself.");
		});
		registry.add("03-current-generation-dispatches-once", _ ->
		{
			final PhantomSemanticAct act = semantic(1);
			final AtomicInteger accepted = new AtomicInteger();
			PhantomAssertions.assertTrue(PhantomPartySemanticActs.dispatchIfCurrent(act, generation -> act.groupId(), ignored -> accepted.incrementAndGet()), "Current semantic act was rejected.");
			PhantomAssertions.assertEquals(1, accepted.get(), "Current semantic act did not dispatch exactly once.");
		});
	}

	private static void route(PhantomTestRegistry registry)
	{
		registry.add("01-one-manifest-shares-waypoints", _ ->
		{
			final RouteManifest route = routeFixture();
			final PartyState state = stateFixture();
			final PartyState withRoute = new PartyState(state.groupId(), state.groupGeneration(), state.membershipRevision(), state.status(), state.leader(), state.ownRoleKey(), state.leaderManifestHash(), state.phantomMembers(), state.realMembers(), ObjectiveMode.TRAVEL, route.destination(), state.requirements(), state.assignments(), route, state.operation(), state.progressionHash(), state.topologyHash(), "");
			PhantomAssertions.assertTrue(withRoute.route().waypoints() == route.waypoints(), "Route manifest was expanded per member.");
			PhantomAssertions.assertEquals(2, route.waypoints().size(), "Shared route fixture changed.");
		});
		registry.add("02-regroup-status-is-durable", _ -> PhantomAssertions.assertEquals(RouteStatus.REGROUPING, routeFixture().withProgress(0, RouteStatus.REGROUPING).status(), "Regroup state was not retained."));
		registry.add("03-route-owner-has-no-snap-or-background-travel", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java");
			PhantomAssertions.assertFalse(source.contains("teleToLocation") || source.contains("setXYZ") || source.contains("background"), "Party route contains snap/background travel.");
			PhantomAssertions.assertEquals(1L, source.lines().filter(line -> line.contains("_navigation.submit")).count(), "Route coordinator has more than one navigation submission site.");
		});
		registry.add("04-route-cancel-is-group-scoped-and-lock-free-at-boundaries", context ->
		{
			final String source = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java");
			PhantomAssertions.assertFalse(source.contains("public synchronized") || source.contains("private synchronized"), "Route coordinator holds its monitor across navigation/combat boundaries.");
			PhantomAssertions.assertTrue(source.contains("_routeByGroup.remove(groupId)") && source.contains("routeId.equals(entry.getValue()._routeId)"), "Route cancellation is not scoped to the selected group route.");
			PhantomAssertions.assertFalse(source.contains("new ArrayList<>(_movement.values())"), "Route cancellation still closes every group's movement lease.");
		});
		registry.add("05-missing-member-cannot-advance-or-arrive", _ ->
		{
			final PhantomNavigationBackend backend = new PhantomNavigationBackend()
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
					throw new AssertionError("Direct route fixture invoked pathfinding.");
				}
			};
			final PhantomNavigationService navigation = new PhantomNavigationService(PhantomNavigationPolicy.productionDefaults(), backend, worker ->
			{
				worker.run();
				return true;
			}, System::nanoTime, new PhantomMetrics());
			PhantomAssertions.assertTrue(navigation.start(), "Route navigation fixture did not start.");
			final PhantomPartyRouteCoordinator routes = new PhantomPartyRouteCoordinator(navigation, null);
			final MemberRef leader = MemberRef.phantom(1, 101);
			final MemberRef missing = MemberRef.phantom(2, 102);
			final MemberSnapshot leaderSnapshot = new MemberSnapshot(leader, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO);
			final String groupId = PhantomPartyModel.sha256("route.missing.member");
			final long now = Math.max(1, System.nanoTime());
			final RouteManifest route = routes.request(groupId, 1, leaderSnapshot, new PhantomDomainRef("location", "missing"), new PhantomNavigationPoint(100, 100, 0, 0), ZERO, now, now + 1_000_000_000L).orElseThrow();
			final var result = routes.advance(groupId, route, leader, List.of(leader, missing), Map.of(leader, leaderSnapshot), 10, now + 1, ZERO, () -> false);
			PhantomAssertions.assertEquals(RouteStatus.REGROUPING, result.route().status(), "Missing canonical member did not force REGROUPING.");
			PhantomAssertions.assertEquals(route.currentWaypoint(), result.route().currentWaypoint(), "Missing canonical member advanced a waypoint.");
			PhantomAssertions.assertFalse(result.route().status() == RouteStatus.ARRIVED, "Missing canonical member allowed ARRIVED.");
			routes.beginStop();
			navigation.beginStop();
			PhantomAssertions.assertTrue(navigation.finishStop(), "Route navigation fixture did not drain.");
		});
	}

	private static void tactics(PhantomTestRegistry registry)
	{
		registry.add("01-assist-protect-heal-recharge-resurrect-support-plan", _ ->
		{
			final MemberRef leader = MemberRef.phantom(1, 101);
			final MemberRef support = MemberRef.phantom(2, 102);
			final MemberRef dead = MemberRef.real(103);
			final MemberSnapshot leaderSnapshot = member(1, 101, 40, 20, false, 900, List.of());
			final MemberSnapshot supportSnapshot = member(2, 102, 100, 100, false, 0, List.of(cap("combat.heal", "heal", 50, 1001), cap("combat.recharge", "recharge", 50, 1002), cap("combat.resurrection", "resurrection", 50, 1003), cap("combat.song", "song", 50, 1004)));
			final MemberSnapshot deadSnapshot = new MemberSnapshot(dead, 0, 0, 0, 0, 0, 0, 0, 0, true, false, false, false, 0, List.of(901), List.of(), ZERO);
			final List<DirectiveKind> kinds = new PhantomPartyTactics(null).plan(leader, List.of(leader, support, dead), Map.of(leader, leaderSnapshot, support, supportSnapshot, dead, deadSnapshot)).stream().map(value -> value.kind()).distinct().toList();
			PhantomAssertions.assertTrue(kinds.containsAll(List.of(DirectiveKind.ASSIST_TARGET, DirectiveKind.PROTECT_MEMBER, DirectiveKind.HEAL_MEMBER, DirectiveKind.RECHARGE_MEMBER, DirectiveKind.RESURRECT_MEMBER)), "Tactical capability coverage is incomplete.");
			PhantomAssertions.assertFalse(kinds.contains(DirectiveKind.PARTY_SUPPORT), "Planner fabricated a use-all support action without an exact target need.");
		});
		registry.add("02-external-combat-ownership-is-mandatory", context ->
		{
			final String tactics = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyTactics.java");
			final String route = source(context, "java/org/l2jmobius/gameserver/phantoms/party/PhantomPartyRouteCoordinator.java");
			PhantomAssertions.assertTrue(tactics.contains("acquireExternalAction") && route.contains("acquireExternalAction"), "Party mutation bypasses combat external ownership.");
		});
	}

	private static void lifecycle(PhantomTestRegistry registry)
	{
		registry.add("01-composite-is-bounded-and-isolates-failure", _ ->
		{
			final AtomicInteger first = new AtomicInteger();
			final AtomicInteger last = new AtomicInteger();
			final PhantomCompositeSchedulerControlPort composite = new PhantomCompositeSchedulerControlPort(List.of(first::incrementAndGet, () ->
			{
				throw new IllegalStateException("injected");
			}, last::incrementAndGet));
			composite.onPulse();
			PhantomAssertions.assertEquals(1, first.get(), "First composite stage did not run.");
			PhantomAssertions.assertEquals(1, last.get(), "Failure prevented later composite stage.");
			PhantomAssertions.assertEquals(1L, composite.snapshot().stageFailures(), "Composite failure was not counted.");
		});
		registry.add("02-more-than-eight-control-stages-rejected", _ -> PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomCompositeSchedulerControlPort(java.util.stream.IntStream.range(0, 9).mapToObj(ignored -> (org.l2jmobius.gameserver.phantoms.activity.PhantomSchedulerControlPort) () ->
		{
		}).toList()), "Nine control stages were accepted."));
		registry.add("03-no-party-worker-or-future", context ->
		{
			final Path directory = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms/party");
			final String all;
			try (var files = Files.walk(directory))
			{
				all = files.filter(path -> path.toString().endsWith(".java")).map(path ->
				{
					try
					{
						return Files.readString(path);
					}
					catch (Exception e)
					{
						throw new IllegalStateException(e);
					}
				}).reduce("", String::concat);
			}
			PhantomAssertions.assertFalse(all.contains("new Thread") || all.contains("ExecutorService") || all.contains("ScheduledFuture"), "Party subsystem owns a worker/future.");
		});
		registry.add("04-transfer-and-leave-commit-exact-canonical-postconditions", PhantomPartySuite::membershipLifecycle);
		registry.add("05-coordinator-pulse-count-never-exceeds-budget", PhantomPartySuite::boundedPulse);
		registry.add("06-operation-budget-below-canonical-boundary-rejected", context ->
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> coordinator(context, states, goals, backend, 9), "Nine-operation party pulse budget was accepted.");
			final PhantomPartyCoordinator boundary = coordinator(context, states, goals, backend, 10);
			PhantomAssertions.assertEquals(10, boundary.snapshot().operationBudget(), "Ten-operation party pulse budget was not retained.");
		});
		registry.add("07-nine-member-budget-boundary-makes-progress", PhantomPartySuite::nineMemberBudget);
		registry.add("08-refusal-makes-form-goal-terminal-and-reusable", context -> terminalFormationReuse(context, TerminalOutcome.REFUSED, "party.invite.refused"));
		registry.add("09-timeout-makes-form-goal-terminal-and-reusable", context -> terminalFormationReuse(context, TerminalOutcome.EXPIRED, "party.invite.expired"));
		registry.add("10-inactive-join-goals-cannot-prepare-or-accept", PhantomPartySuite::inactiveJoinConsent);
		registry.add("11-inactive-membership-goals-cannot-transition", PhantomPartySuite::inactiveMembershipTransition);
	}

	private static void inactiveJoinConsent(PhantomTestContext context)
	{
		for (PhantomGoalStatus status : List.of(PhantomGoalStatus.FAILED, PhantomGoalStatus.COMPLETED, PhantomGoalStatus.ABANDONED))
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			final MemberRef invitee = backend.add(2, 102);
			goals.put(invitee.profileId(), goal(2, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "999"), 0, status));
			final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 10);
			try
			{
				PhantomAssertions.assertTrue(coordinator.start(), "Inactive-consent coordinator did not start.");
				final InvitationIdentity identity = new InvitationIdentity(status.ordinal() + 1L, 999, invitee.characterObjectId());
				final PartyInvitation invitation = new PartyInvitation(identity, 999, MemberRef.real(999).stableKey(), invitee.characterObjectId(), invitee.stableKey(), PartyDistributionType.FINDERS_KEEPERS, 999, Long.MAX_VALUE);
				PhantomAssertions.assertEquals(PreparationOutcome.REJECTED, coordinator.prepare(invitation, OptionalLong.empty(), OptionalLong.of(invitee.profileId())), status + " join goal prepared a real-to-Phantom invitation.");
				PhantomAssertions.assertEquals(org.l2jmobius.gameserver.model.groups.PartyInvitationDelivery.DeliveryOutcome.ACCEPTED, coordinator.deliver(invitation, invitee.profileId()), "Inactive-consent fixture could not enqueue managed delivery.");
				coordinator.onPulse();
				PhantomAssertions.assertEquals(PartyInvitationService.Response.REFUSE, backend.lastResponse(), status + " join goal accepted a managed invitation.");
				PhantomAssertions.assertTrue(coordinator.claim(invitee.profileId()).isEmpty(), status + " join goal created a membership claim.");
			}
			finally
			{
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Inactive-consent coordinator did not drain.");
			}
		}
	}

	private static void inactiveMembershipTransition(PhantomTestContext context)
	{
		for (PhantomGoalStatus status : List.of(PhantomGoalStatus.FAILED, PhantomGoalStatus.COMPLETED, PhantomGoalStatus.ABANDONED))
		{
			final MemoryPartyStore states = new MemoryPartyStore();
			final MemoryGoalStore goals = new MemoryGoalStore();
			final MemoryPartyBackend backend = new MemoryPartyBackend();
			final MemberRef leader = backend.add(1, 101);
			final MemberRef member = backend.add(2, 102);
			backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, member), PartyDistributionType.FINDERS_KEEPERS));
			final String groupId = PhantomPartyModel.sha256("inactive.transition." + status);
			final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256(groupId + ".operation"), OperationKind.FORM, OperationPhase.COMMITTED, leader, member, 1, 0, ZERO, 0, 1, "");
			final PartyState draft = new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", ZERO, List.of(leader, member), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "inactive-transition"), List.of(), List.of(), null, operation, ZERO, ZERO, "");
			final String manifest = draft.canonicalManifestHash();
			states.seed(leader.profileId(), new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", manifest, List.of(leader, member), List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, operation, ZERO, ZERO, ""));
			states.seed(member.profileId(), new PartyState(groupId, 1, 0, StateStatus.MEMBER, leader, "", manifest, List.of(leader, member), List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, operation, ZERO, ZERO, ""));
			goals.put(leader.profileId(), goal(1, PhantomPartyCoordinator.FORM_GOAL, null, 0, status));
			goals.put(member.profileId(), goal(2, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "101"), 0, status));
			final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 10);
			try
			{
				PhantomAssertions.assertTrue(coordinator.start(), "Inactive-transition coordinator did not start.");
				coordinator.onPulse();
				PhantomAssertions.assertEquals(status, goals.load(leader.profileId()).orElseThrow().goal().status(), status + " form goal was revived during leader transition.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.FORM_GOAL, goals.load(leader.profileId()).orElseThrow().goal().goalType(), status + " form goal changed to party.lead.");
				PhantomAssertions.assertEquals(status, goals.load(member.profileId()).orElseThrow().goal().status(), status + " join goal was revived during member transition.");
				PhantomAssertions.assertEquals(PhantomPartyCoordinator.JOIN_GOAL, goals.load(member.profileId()).orElseThrow().goal().goalType(), status + " join goal changed to party.member.");
			}
			finally
			{
				coordinator.beginStop();
				PhantomAssertions.assertTrue(coordinator.finishStop(), "Inactive-transition coordinator did not drain.");
			}
		}
	}

	private static void membershipLifecycle(PhantomTestContext context)
	{
		final MemoryPartyStore states = new MemoryPartyStore();
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend backend = new MemoryPartyBackend();
		final MemberRef leader = backend.add(1, 101);
		final MemberRef member = backend.add(2, 102);
		backend.party(new PhantomPartyBackend.PartySnapshot(leader, List.of(leader, member), PartyDistributionType.FINDERS_KEEPERS));
		final String groupId = PhantomPartyModel.sha256("membership.lifecycle");
		final PartyOperation form = new PartyOperation(PhantomPartyModel.sha256("membership.form"), OperationKind.FORM, OperationPhase.COMMITTED, leader, null, 1, 0, ZERO, 0, 1, "");
		final PartyState draft = new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", ZERO, List.of(leader, member), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "lifecycle"), List.of(), List.of(), null, form, ZERO, ZERO, "");
		final String manifest = draft.canonicalManifestHash();
		states.seed(1, new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", manifest, List.of(leader, member), List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, form, ZERO, ZERO, ""));
		states.seed(2, new PartyState(groupId, 1, 0, StateStatus.MEMBER, leader, "", manifest, List.of(leader, member), List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, form, ZERO, ZERO, ""));
		goals.put(1, goal(1, PhantomPartyCoordinator.FORM_GOAL, null, 0));
		goals.put(2, goal(1, PhantomPartyCoordinator.JOIN_GOAL, new PhantomDomainRef("character.object", "101"), 0));
		final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 32);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Lifecycle coordinator did not start.");
			coordinator.onPulse();
			final long generation = coordinator.claim(1).orElseThrow().state().groupGeneration();
			goals.put(1, goal(2, PhantomPartyCoordinator.TRANSFER_LEADER_GOAL, new PhantomDomainRef("profile", "2"), 0));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.transferLeaderTarget(1, 2, 0, generation, new PhantomDomainRef("profile", "2")), "Exact canonical leader transfer was rejected.");
			final PartyState transferred = coordinator.claim(2).orElseThrow().state();
			PhantomAssertions.assertEquals(member, transferred.leader(), "Transferred claim did not observe the canonical leader.");
			PhantomAssertions.assertEquals(generation + 1, transferred.groupGeneration(), "Leader transfer did not advance generation exactly once.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.IDEMPOTENT, coordinator.transferLeaderTarget(1, 2, 0, generation, new PhantomDomainRef("profile", "2")), "Exact transfer retry was not idempotent.");

			goals.put(1, goal(3, PhantomPartyCoordinator.LEAVE_GOAL, null, 0));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.leave(1, 3, 0, transferred.groupGeneration()), "Exact member leave was rejected.");
			PhantomAssertions.assertEquals(StateStatus.SOLO, coordinator.claim(1).orElseThrow().state().status(), "Departed Phantom did not move to SOLO.");
			PhantomAssertions.assertFalse(coordinator.claim(2).orElseThrow().state().phantomMembers().contains(leader), "Remaining claim retained departed Phantom.");
		}
		finally
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Lifecycle coordinator did not drain.");
		}
	}

	private static void boundedPulse(PhantomTestContext context)
	{
		final MemoryPartyStore states = new MemoryPartyStore();
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend backend = new MemoryPartyBackend();
		for (int index = 1; index <= 64; index++)
		{
			final MemberRef member = backend.add(index, 1000 + index);
			backend.party(new PhantomPartyBackend.PartySnapshot(member, List.of(member), PartyDistributionType.FINDERS_KEEPERS));
			final String groupId = PhantomPartyModel.sha256("bounded.group." + index);
			final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256("bounded.operation." + index), OperationKind.FORM, OperationPhase.COMMITTED, member, null, index, 0, ZERO, 0, 1, "");
			final PartyState draft = new PartyState(groupId, 1, 0, StateStatus.LEADER, member, "", ZERO, List.of(member), List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "bounded"), List.of(), List.of(), null, operation, ZERO, ZERO, "");
			states.seed(index, new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), "", draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), null, operation, ZERO, ZERO, ""));
			goals.put(index, goal(index, PhantomPartyCoordinator.FORM_GOAL, null, 0));
		}
		final int budget = 10;
		final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, budget);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Bounded coordinator did not start.");
			for (int pulse = 0; pulse < 100; pulse++)
			{
				coordinator.onPulse();
				PhantomAssertions.assertTrue(coordinator.snapshot().lastPulseExamined() <= budget, "Pulse examined work beyond its configured budget.");
			}
			PhantomAssertions.assertTrue(coordinator.snapshot().maximumPulseExamined() <= budget, "Historical pulse accounting exceeded the configured budget.");
			PhantomAssertions.assertEquals(64, coordinator.snapshot().partyClaims(), "Bounded pulse dropped a managed claim.");
		}
		finally
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Bounded coordinator did not drain.");
		}
	}

	private static void nineMemberBudget(PhantomTestContext context)
	{
		final MemoryPartyStore states = new MemoryPartyStore();
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend backend = new MemoryPartyBackend();
		final List<MemberRef> members = java.util.stream.LongStream.rangeClosed(1, 9).mapToObj(profileId -> backend.add(profileId, 2000 + (int) profileId)).toList();
		final MemberRef leader = members.getFirst();
		backend.party(new PhantomPartyBackend.PartySnapshot(leader, members, PartyDistributionType.FINDERS_KEEPERS));
		final String groupId = PhantomPartyModel.sha256("budget.boundary.group");
		final PartyOperation operation = new PartyOperation(PhantomPartyModel.sha256("budget.boundary.operation"), OperationKind.FORM, OperationPhase.COMMITTED, leader, null, 100, 0, ZERO, 0, 1, "");
		final PartyState draft = new PartyState(groupId, 1, 0, StateStatus.LEADER, leader, "", ZERO, members, List.of(), ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "budget-boundary"), List.of(), List.of(), null, operation, ZERO, ZERO, "");
		final String manifest = draft.canonicalManifestHash();
		for (MemberRef member : members)
		{
			final StateStatus status = member.equals(leader) ? StateStatus.LEADER : StateStatus.MEMBER;
			states.seed(member.profileId(), new PartyState(groupId, 1, 0, status, leader, "", manifest, members, List.of(), draft.objectiveMode(), draft.objectiveRef(), List.of(), List.of(), null, operation, ZERO, ZERO, ""));
			goals.put(member.profileId(), goal(100, member.equals(leader) ? PhantomPartyCoordinator.FORM_GOAL : PhantomPartyCoordinator.JOIN_GOAL, member.equals(leader) ? null : new PhantomDomainRef("character.object", Integer.toString(leader.characterObjectId())), 0));
		}
		final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 10);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Nine-member boundary coordinator did not start.");
			PhantomAssertions.assertTrue(members.stream().allMatch(member -> coordinator.claim(member.profileId()).orElseThrow().state().status() == StateStatus.RECOVERING), "Restart fixture did not begin at the reconcile boundary.");
			coordinator.onPulse();
			PhantomAssertions.assertEquals(StateStatus.LEADER, coordinator.claim(leader.profileId()).orElseThrow().state().status(), "Nine-member group remained in the due queue without canonical progress.");
			PhantomAssertions.assertTrue(members.stream().skip(1).allMatch(member -> coordinator.claim(member.profileId()).orElseThrow().state().status() == StateStatus.MEMBER), "Nine-member reconcile did not publish all canonical member claims.");
			PhantomAssertions.assertEquals(10, coordinator.snapshot().lastPulseExamined(), "Nine-member boundary did not consume the exact group plus claim budget.");
			PhantomAssertions.assertTrue(coordinator.snapshot().maximumPulseExamined() <= 10, "Nine-member boundary exceeded its configured pulse budget.");
		}
		finally
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Nine-member boundary coordinator did not drain.");
		}
	}

	private static void terminalFormationReuse(PhantomTestContext context, TerminalOutcome terminalOutcome, String reasonKey)
	{
		final MemoryPartyStore states = new MemoryPartyStore();
		final MemoryGoalStore goals = new MemoryGoalStore();
		final MemoryPartyBackend backend = new MemoryPartyBackend();
		final MemberRef leader = backend.add(1, 101);
		final MemberRef firstTarget = backend.add(2, 102);
		final MemberRef secondTarget = backend.add(3, 103);
		final long oldGoalId = 700;
		final long newGoalId = 701;
		goals.put(leader.profileId(), goal(oldGoalId, PhantomPartyCoordinator.FORM_GOAL, null, 0));
		final PhantomPartyCoordinator coordinator = coordinator(context, states, goals, backend, 10);
		backend.connect(coordinator);
		try
		{
			PhantomAssertions.assertTrue(coordinator.start(), "Terminal formation coordinator did not start.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(leader.profileId(), oldGoalId, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "terminal"), List.of()), "Initial form operation was rejected.");
			final String oldGroupId = coordinator.claim(leader.profileId()).orElseThrow().state().groupId();
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(leader.profileId(), firstTarget, PartyDistributionType.FINDERS_KEEPERS), "Initial invitation was rejected.");
			final PartyInvitation firstInvitation = backend.lastInvitation();
			PhantomAssertions.assertEquals(1, backend.invitationCount(), "Initial form emitted more than one invitation sequence.");

			coordinator.terminal(firstInvitation, OptionalLong.of(leader.profileId()), OptionalLong.of(firstTarget.profileId()), terminalOutcome, reasonKey);
			coordinator.onPulse();
			final PartyState reusable = coordinator.claim(leader.profileId()).orElseThrow().state();
			PhantomAssertions.assertEquals(StateStatus.SOLO, reusable.status(), "Terminal formation did not make the leader reusable.");
			PhantomAssertions.assertEquals(OperationPhase.ABORTED, reusable.operation().phase(), "Terminal formation lost exact ABORTED evidence.");
			PhantomAssertions.assertEquals(firstInvitation.identity().sequence(), reusable.operation().invitationSequence(), "Terminal formation lost the exact invitation sequence.");
			PhantomAssertions.assertEquals(reasonKey, reusable.operation().failureKey(), "Terminal formation lost the typed operation failure.");
			PhantomAssertions.assertEquals(reasonKey, reusable.lastFailureKey(), "Terminal formation lost the typed state failure.");
			PhantomAssertions.assertFalse(reusable.groupId().equals(oldGroupId), "Terminal formation retained the old group identity.");
			PhantomAssertions.assertEquals(0, coordinator.snapshot().groups(), "Terminal formation retained the old GroupRuntime/due entry.");
			final PhantomGoal failedGoal = goals.load(leader.profileId()).orElseThrow().goal();
			PhantomAssertions.assertEquals(PhantomGoalStatus.FAILED, failedGoal.status(), "Terminal form goal did not become FAILED.");
			PhantomAssertions.assertEquals(1L, failedGoal.revision(), "Terminal form goal revision did not advance exactly once.");
			PhantomAssertions.assertEquals(reasonKey, failedGoal.reasonKey(), "Terminal form goal lost the terminal reason.");

			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.GOAL_MISMATCH, coordinator.form(leader.profileId(), oldGoalId, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "terminal"), List.of()), "Stale form decision step was accepted.");
			PhantomAssertions.assertEquals(1, backend.invitationCount(), "Stale form decision emitted an automatic retry invitation.");

			goals.put(leader.profileId(), goal(newGoalId, PhantomPartyCoordinator.FORM_GOAL, null, 0));
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.form(leader.profileId(), newGoalId, 0, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "terminal-retry"), List.of()), "New exact form goal could not reuse SOLO.");
			final PartyState newFormation = coordinator.claim(leader.profileId()).orElseThrow().state();
			PhantomAssertions.assertFalse(newFormation.groupId().equals(oldGroupId), "New exact form goal reused the terminal group identity.");
			PhantomAssertions.assertTrue(newFormation.groupGeneration() > reusable.groupGeneration(), "New exact form goal did not advance generation.");
			PhantomAssertions.assertEquals(OperationKind.FORM, newFormation.operation().kind(), "New exact form goal did not create FORM evidence.");
			PhantomAssertions.assertEquals(OperationPhase.PREPARED, newFormation.operation().phase(), "New exact form goal did not create PREPARED evidence.");
			PhantomAssertions.assertEquals(PhantomPartyCoordinator.CommandOutcome.ACCEPTED, coordinator.invite(leader.profileId(), secondTarget, PartyDistributionType.FINDERS_KEEPERS), "New exact form goal could not invite another target.");
			final PartyInvitation secondInvitation = backend.lastInvitation();
			PhantomAssertions.assertEquals(2, backend.invitationCount(), "New exact form goal did not create exactly one new invitation.");

			coordinator.terminal(firstInvitation, OptionalLong.of(leader.profileId()), OptionalLong.of(firstTarget.profileId()), terminalOutcome, reasonKey);
			coordinator.onPulse();
			final PartyOperation stillPending = coordinator.claim(leader.profileId()).orElseThrow().state().operation();
			PhantomAssertions.assertEquals(secondInvitation.identity().sequence(), stillPending.invitationSequence(), "Stale terminal callback replaced the new invitation identity.");
			PhantomAssertions.assertFalse(stillPending.phase() == OperationPhase.ABORTED, "Stale terminal callback aborted the new operation.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.ACTIVE, goals.load(leader.profileId()).orElseThrow().goal().status(), "Stale terminal callback failed the new form goal.");
		}
		finally
		{
			coordinator.beginStop();
			PhantomAssertions.assertTrue(coordinator.finishStop(), "Terminal formation coordinator did not drain.");
		}
	}

	private static void integration(PhantomTestRegistry registry)
	{
		registry.add("00-test-database-is-allowlisted", context ->
		{
			final Path config = Path.of(System.getProperty("phantom.test.config", context.moduleRoot().resolve(".phantom-local/Database.test.ini").toString()));
			PhantomTestDatabaseGuard.validate(context.moduleRoot(), config);
			PhantomAssertions.assertTrue(Files.readString(config).contains(PhantomTestDatabaseGuard.TARGET_DATABASE), "Party integration is not pinned to the Phantom test database.");
		});
		registry.add("01-production-composition-is-complete", context ->
		{
			final String system = source(context, "java/org/l2jmobius/gameserver/phantoms/PhantomSystem.java");
			PhantomAssertions.assertTrue(system.contains("new L2jPhantomPartyBackend") && system.contains("new PhantomPartyStore") && system.contains("new PhantomCompositeSchedulerControlPort"), "Production party composition is incomplete.");
		});
		registry.add("02-current-catalog-covers-support-and-damage", context ->
		{
			final PhantomPartyRoleCatalog catalog = currentCatalog(context);
			for (String role : List.of("frontline.guardian", "damage.melee", "damage.ranged", "support.healer", "support.recharge", "support.enhancement", "control.specialist"))
			{
				PhantomAssertions.assertTrue(catalog.contains(role), "Current catalog misses role " + role);
			}
		});
		registry.add("03-production-backend-copies-canonical-party-state", context ->
		{
			final String backend = source(context, "java/org/l2jmobius/gameserver/phantoms/party/L2jPhantomPartyBackend.java");
			PhantomAssertions.assertTrue(backend.contains("party.getMembers().stream()") && backend.contains("party.getLeader()") && backend.contains("getDistributionType()"), "Production backend does not copy exact canonical Party state.");
			PhantomAssertions.assertFalse(backend.contains("getMembers().add") || backend.contains("getMembers().remove"), "Production backend mutates Party member list directly.");
		});
	}

	private static void performance(PhantomTestRegistry registry)
	{
		registry.add("01-100k-pulses-use-one-control-chain", context ->
		{
			final AtomicInteger population = new AtomicInteger();
			final AtomicInteger party = new AtomicInteger();
			final PhantomCompositeSchedulerControlPort composite = new PhantomCompositeSchedulerControlPort(List.of(population::incrementAndGet, party::incrementAndGet));
			final long started = System.nanoTime();
			for (int pulse = 0; pulse < 100_000; pulse++)
			{
				composite.onPulse();
			}
			final long elapsed = System.nanoTime() - started;
			PhantomAssertions.assertEquals(100_000, population.get(), "Population stage lost pulses.");
			PhantomAssertions.assertEquals(100_000, party.get(), "Party stage lost pulses.");
			context.record("partyPerformance.pulses", 100000);
			context.record("partyPerformance.syntheticProfiles", 10000);
			context.record("partyPerformance.syntheticGroups", 1000);
			context.record("partyPerformance.elapsedNanos", elapsed);
		});
		registry.add("02-nine-member-matching-remains-bounded", context ->
		{
			final PhantomPartyRoleMatcher matcher = new PhantomPartyRoleMatcher(currentCatalog(context));
			final List<MemberSnapshot> members = java.util.stream.LongStream.rangeClosed(1, 9).mapToObj(value -> member(value, 100 + (int) value, 100, 100, false, 0, List.of(cap("combat.melee_damage", "basic", (int) value, 1)))).toList();
			final List<RoleRequirement> requirements = java.util.stream.IntStream.range(0, 12).mapToObj(value -> new RoleRequirement("slot.damage." + value, "damage.melee", true, 1)).toList();
			for (int group = 0; group < 1000; group++)
			{
				PhantomAssertions.assertTrue(matcher.match(ObjectiveMode.GENERAL_PVE, requirements, members).assignments().size() <= 9, "Matcher assigned more members than the roster contains.");
			}
			PhantomAssertions.assertEquals(9, members.size(), "Bounded party matcher fixture changed.");
			context.record("partyPerformance.roleRequirementsPerGroup", requirements.size());
		});
	}

	private static PhantomPartyRoleCatalog currentCatalog(PhantomTestContext context)
	{
		return PhantomPartyRoleCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/party/high-five-party-roles-v1.xml"));
	}

	private static String source(PhantomTestContext context, String relative) throws Exception
	{
		return Files.readString(context.moduleRoot().resolve(relative));
	}

	private static MemberCapability cap(String key, String variant, int rank, int skillId)
	{
		return new MemberCapability(key, variant, rank, skillId, 1, "PARTY_MEMBER", true, true, true, "ready", rank * 10, "test.progression.fixture");
	}

	private static MemberSnapshot member(long profileId, int objectId, int hp, int mp, boolean dead, int target, List<MemberCapability> capabilities)
	{
		return new MemberSnapshot(MemberRef.phantom(profileId, objectId), 0, 0, 0, 0, 0, hp, mp, 100, dead, false, false, false, target, List.of(901), capabilities, ZERO);
	}

	private static PartyOperation operationFixture()
	{
		final MemberRef leader = MemberRef.phantom(1, 101);
		return new PartyOperation(PhantomPartyModel.sha256("operation"), OperationKind.JOIN, OperationPhase.PREPARED, leader, MemberRef.phantom(2, 102), 1, 0, ZERO, 0, 1, "");
	}

	private static PartyState stateFixture()
	{
		final MemberRef leader = MemberRef.phantom(1, 101);
		final PartyState draft = rawState(leader, List.of(leader, MemberRef.phantom(2, 102)), List.of(MemberRef.real(201)));
		return new PartyState(draft.groupId(), draft.groupGeneration(), draft.membershipRevision(), draft.status(), draft.leader(), draft.ownRoleKey(), draft.canonicalManifestHash(), draft.phantomMembers(), draft.realMembers(), draft.objectiveMode(), draft.objectiveRef(), draft.requirements(), draft.assignments(), draft.route(), draft.operation(), draft.progressionHash(), draft.topologyHash(), draft.lastFailureKey());
	}

	private static PartyState rawState(MemberRef leader, List<MemberRef> phantoms, List<MemberRef> reals)
	{
		return new PartyState(PhantomPartyModel.sha256("group"), 1, 2, StateStatus.LEADER, leader, "frontline.guardian", ZERO, phantoms, reals, ObjectiveMode.GENERAL_PVE, new PhantomDomainRef("party", "fixture"), List.of(new RoleRequirement("slot.guardian", "frontline.guardian", true, 1)), List.of(), null, operationFixture(), ZERO, ZERO, "");
	}

	private static RouteManifest routeFixture()
	{
		return new RouteManifest(PhantomPartyModel.sha256("route"), 1, new PhantomDomainRef("location", "fixture"), List.of(new PhantomNavigationPoint(0, 0, 0, 0), new PhantomNavigationPoint(100, 100, 0, 0)), 0, 250, 1500, RouteStatus.MOVING, ZERO, ZERO);
	}

	private static PhantomSemanticAct semantic(long generation)
	{
		return PhantomPartySemanticActs.create(PhantomPartySemanticActs.ASSIST_REQUESTED, new PhantomDomainRef("profile", "1"), new PhantomDomainRef("npc.object", "900"), PhantomPartyModel.sha256("semantic.group"), generation, "leader.target", 9000, Map.of(), Map.of("target.object", 900L), "party.tactics.test");
	}

	private static PhantomPartyCoordinator coordinator(PhantomTestContext context, MemoryPartyStore states, MemoryGoalStore goals, MemoryPartyBackend backend, int budget)
	{
		return new PhantomPartyCoordinator(states, goals, backend, currentCatalog(context), new PhantomPartyRouteCoordinator(null, null), new PhantomPartyTactics(null, backend), () -> ZERO, System::nanoTime, budget);
	}

	private static PhantomGoal goal(long goalId, String type, PhantomDomainRef target, long revision)
	{
		return goal(goalId, type, target, revision, PhantomGoalStatus.ACTIVE);
	}

	private static PhantomGoal goal(long goalId, String type, PhantomDomainRef target, long revision, PhantomGoalStatus status)
	{
		return new PhantomGoal(goalId, type, status, new PhantomDomainRef("profile", "1"), target, 1, 0, null, List.of(), null, "party.lifecycle", 500, 0, 0, 0, Map.of(), "party.lifecycle", revision);
	}

	private static final class MemoryPartyStore implements PhantomPartyPersistencePort
	{
		private final TreeMap<Long, StoredPartyState> _states = new TreeMap<>();

		private void seed(long profileId, PartyState state)
		{
			_states.put(profileId, new StoredPartyState(profileId, 0, state));
		}

		@Override
		public Optional<StoredPartyState> load(long profileId)
		{
			return Optional.ofNullable(_states.get(profileId));
		}

		@Override
		public StoredPartyState save(long profileId, long expectedRowVersion, PartyState state)
		{
			final StoredPartyState current = _states.get(profileId);
			if (((current == null) && (expectedRowVersion >= 0)) || ((current != null) && (current.rowVersion() != expectedRowVersion)))
			{
				throw new IllegalStateException("Injected optimistic conflict.");
			}
			final StoredPartyState stored = new StoredPartyState(profileId, current == null ? 0 : current.rowVersion() + 1, state);
			_states.put(profileId, stored);
			return stored;
		}

		@Override
		public List<StoredPartyState> loadManagedAfter(long exclusiveProfileId, int pageSize)
		{
			return _states.tailMap(exclusiveProfileId, false).values().stream().limit(pageSize).toList();
		}
	}

	private static final class MemoryGoalStore implements PhantomGoalStore
	{
		private final Map<Long, StoredGoal> _goals = new LinkedHashMap<>();

		private void put(long profileId, PhantomGoal goal)
		{
			final StoredGoal current = _goals.get(profileId);
			_goals.put(profileId, new StoredGoal(goal, current == null ? 0 : current.rowVersion() + 1));
		}

		@Override
		public boolean profileExists(long profileId)
		{
			return profileId > 0;
		}

		@Override
		public Optional<StoredGoal> load(long profileId)
		{
			return Optional.ofNullable(_goals.get(profileId));
		}

		@Override
		public StoredGoal insert(long profileId, PhantomGoal goal)
		{
			if (_goals.containsKey(profileId))
			{
				throw new IllegalStateException("Goal exists.");
			}
			put(profileId, goal);
			return _goals.get(profileId);
		}

		@Override
		public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
		{
			final StoredGoal current = _goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion))
			{
				throw new IllegalStateException("Goal conflict.");
			}
			put(profileId, goal);
			return _goals.get(profileId);
		}

		@Override
		public void delete(long profileId, long expectedRowVersion)
		{
			final StoredGoal current = _goals.get(profileId);
			if ((current == null) || (current.rowVersion() != expectedRowVersion))
			{
				throw new IllegalStateException("Goal conflict.");
			}
			_goals.remove(profileId);
		}
	}

	private static final class MemoryPartyBackend implements PhantomPartyBackend
	{
		private final Map<Long, MemberRef> _members = new LinkedHashMap<>();
		private final Map<MemberRef, PartySnapshot> _parties = new LinkedHashMap<>();
		private PhantomPartyCoordinator _coordinator;
		private PartyInvitation _lastInvitation;
		private PartyInvitationService.Response _lastResponse;
		private long _invitationSequence;
		private int _invitationCount;

		private void connect(PhantomPartyCoordinator coordinator)
		{
			_coordinator = coordinator;
		}

		private PartyInvitation lastInvitation()
		{
			return _lastInvitation;
		}

		private int invitationCount()
		{
			return _invitationCount;
		}

		private PartyInvitationService.Response lastResponse()
		{
			return _lastResponse;
		}

		private MemberRef add(long profileId, int objectId)
		{
			final MemberRef member = MemberRef.phantom(profileId, objectId);
			_members.put(profileId, member);
			return member;
		}

		private void party(PartySnapshot snapshot)
		{
			for (MemberRef member : snapshot.members())
			{
				_parties.put(member, snapshot);
			}
		}

		@Override
		public OptionalLong managedProfileId(int characterObjectId)
		{
			return _members.values().stream().filter(member -> member.characterObjectId() == characterObjectId).mapToLong(MemberRef::profileId).findFirst();
		}

		@Override
		public Optional<MemberRef> currentMember(long profileId)
		{
			return Optional.ofNullable(_members.get(profileId));
		}

		@Override
		public InviteResult invite(MemberRef requester, MemberRef target, PartyDistributionType distribution)
		{
			if (_coordinator == null)
			{
				throw new AssertionError("Bounded lifecycle fixture must not fabricate an invitation.");
			}
			if ((_lastInvitation != null) && (_lastInvitation.requesterObjectId() == requester.characterObjectId()) && (_lastInvitation.inviteeObjectId() == target.characterObjectId()))
			{
				return new InviteResult(InviteOutcome.DELIVERED_MANAGED, _lastInvitation.identity());
			}
			final InvitationIdentity identity = new InvitationIdentity(++_invitationSequence, requester.characterObjectId(), target.characterObjectId());
			final PartyInvitation invitation = new PartyInvitation(identity, requester.characterObjectId(), requester.stableKey(), target.characterObjectId(), target.stableKey(), distribution, requester.characterObjectId(), Long.MAX_VALUE);
			final OptionalLong managedRequester = requester.kind() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind.PHANTOM ? OptionalLong.of(requester.profileId()) : OptionalLong.empty();
			final OptionalLong managedInvitee = target.kind() == org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind.PHANTOM ? OptionalLong.of(target.profileId()) : OptionalLong.empty();
			if (_coordinator.prepare(invitation, managedRequester, managedInvitee) != PreparationOutcome.ACCEPTED)
			{
				return new InviteResult(InviteOutcome.MANAGED_BACKPRESSURE, identity);
			}
			_lastInvitation = invitation;
			_invitationCount++;
			return new InviteResult(InviteOutcome.DELIVERED_MANAGED, identity);
		}

		@Override
		public PartyInvitationService.RespondResult respond(MemberRef invitee, PartyInvitationService.Response response, PartyInvitationService.InvitationIdentity identity)
		{
			_lastResponse = response;
			if (response == PartyInvitationService.Response.ACCEPT)
			{
				throw new AssertionError("Bounded lifecycle fixture must not fabricate an accepted response.");
			}
			return null;
		}

		@Override
		public PartyInvitationService.MembershipOutcome leave(MemberRef member)
		{
			final PartySnapshot current = _parties.remove(member);
			if (current == null)
			{
				return PartyInvitationService.MembershipOutcome.NOT_IN_PARTY;
			}
			final List<MemberRef> remaining = current.members().stream().filter(candidate -> !candidate.equals(member)).toList();
			if (!remaining.isEmpty())
			{
				party(new PartySnapshot(current.leader().equals(member) ? remaining.getFirst() : current.leader(), remaining, current.distribution()));
			}
			return PartyInvitationService.MembershipOutcome.COMPLETED;
		}

		@Override
		public PartyInvitationService.MembershipOutcome expel(MemberRef requester, MemberRef member)
		{
			return leave(member);
		}

		@Override
		public PartyInvitationService.MembershipOutcome transferLeader(MemberRef requester, MemberRef member)
		{
			final PartySnapshot current = _parties.get(requester);
			if ((current == null) || !current.leader().equals(requester) || !current.members().contains(member))
			{
				return PartyInvitationService.MembershipOutcome.NOT_LEADER;
			}
			party(new PartySnapshot(member, current.members(), current.distribution()));
			return PartyInvitationService.MembershipOutcome.COMPLETED;
		}

		@Override
		public Optional<PartySnapshot> observe(MemberRef member)
		{
			return Optional.ofNullable(_parties.get(member));
		}

		@Override
		public Optional<MemberSnapshot> memberSnapshot(MemberRef member)
		{
			return Optional.of(new MemberSnapshot(member, 0, 0, 0, 0, 0, 100, 100, 100, false, false, false, false, 0, List.of(), List.of(), ZERO));
		}

		@Override
		public List<MemberCapability> capabilities(MemberRef actor, int exactTargetObjectId)
		{
			return List.of();
		}

		@Override
		public boolean materialize(long profileId)
		{
			return _members.containsKey(profileId);
		}
	}
}

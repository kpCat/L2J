/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongPredicate;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.MaterializationRequest;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityMaterializationPort.TransitionOutcome;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomReconcileFirstActivityPort;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationManager;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;
import org.l2jmobius.gameserver.phantoms.topology.PhantomHumanLocalityControl;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyService;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomPopulationEcologyGoal033Suite.EcologyMemoryStore;
import org.l2jmobius.tests.phantoms.PhantomPopulationTestDoubles;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** Pure ordinary itinerary proposals; native movement/checkpoint effects remain in their owners. */
public final class PhantomVisibleItinerary068Checks implements PhantomTestSuite
{
	/** Actual JDBC proof and the same request cache consumed by the ecology contract checks below. */
	public static void logicalPreparationSignal(org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository profiles,
		org.l2jmobius.gameserver.phantoms.decision.PhantomGoal goal, PhantomBackgroundState state, PhantomBackgroundAuthority authority)
	{
		final var signals = new java.util.concurrent.atomic.AtomicInteger();
		final var port = new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { signals.incrementAndGet(); return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
		final var service = new PhantomBackgroundService(profiles, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore(profiles),
			org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance(), new PhantomBackgroundTransaction(), authority,
			new PhantomBackgroundCompetitionRegistry(), port, () -> null);
		service.start();
		try
		{
			final long id = state.identity().profileId();
			final var raw = profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, service.historicalNativeContext(id, state).status(), "Exact SQL context proof failed.");
			PhantomAssertions.assertFalse(service.currentNativeContextRequest(id), "A simulation-ready solo context invented demand.");
			final var stale = state.after(state.progress(), state.vitals(), state.position(), state.inventory(), state.autoGetSkills(), new PhantomBackgroundState.Clock(state.clock().rngState() + 1, 0, 0), state.receipt());
			PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.STATE_CONFLICT, service.historicalNativeContext(id, stale, true).status(), "A stale baseline requested preparation.");
			PhantomAssertions.assertEquals(0, signals.get(), "Rejected stale proof published a signal.");
			PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, service.historicalNativeContext(id, state, true).status(), "Preparation lost its exact SQL proof.");
			PhantomAssertions.assertTrue(service.currentNativeContextRequest(id), "Logical preparation did not publish the current typed native request.");
			PhantomAssertions.assertEquals(raw, profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow(), "Request publication changed rewards/state/time.");
			service.historicalNativeContext(id, state);
			PhantomAssertions.assertFalse(service.currentNativeContextRequest(id), "Cleared preparation retained a current request.");
		}
		finally { service.beginStop(); service.finishStop(); }
	}
	@Override public String id() { return "visible-itinerary068"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		registry.add("01-canonical-small-farm-standpoint", _ -> canonicalFarm());
		registry.add("02-composite-route-uses-first-leg-and-full-native-fees", _ -> itinerary());
		registry.add("03-off-anchor-background-return-retains-current-lineage", _ -> localReturn());
		registry.add("04-native-current-anchor-return-is-only-a-bounded-walk-proposal", _ -> nativeReturn());
		registry.add("05-exact-native-landing-retains-canonical-floor", _ -> nativeLanding());
		registry.add("06-interrupted-native-route-may-propose-connected-ingress", _ -> interruptedIngress());
		registry.add("07-preparation-drains-existing-arrival-before-yield", _ -> preparationArrival());
		registry.add("08-native-route-landing-retains-physical-floor", _ -> authorityLanding());
		registry.add("09-historical-route-exclusion-requires-unchanged-failed-step", _ -> historicalFailedStep());
		registry.add("10-offscreen-hash-fence-rejects-every-nonempty-mismatch", _ -> offscreenReceiptHash());
		registry.add("11-completed-offscreen-arrival-requires-native-capture-before-history", _ -> completedOffscreenNativeHandoff());
		registry.add("12-background-native-request-cache-is-current-finite-and-bounded", _ -> nativeContextRequestCache());
		registry.add("13-background-native-request-reaches-bounded-ecology-handoff", context -> nativeRequestPresence(context.moduleRoot()));
		registry.add("14-current-party-native-owner-requires-typed-restart-context", context -> nativePartyRestartContext(context.moduleRoot()));
		registry.add("15-party-and-ambient-demand-retain-ordinary-admission-fences", context -> coordinatedDemand(context.moduleRoot()));
		registry.add("16-group-cursor-observes-only-committed-historical-receipts", _ -> groupReceiptCursor());
		registry.add("17-logical-preparation-recovers-exact-failed-owner-without-lifetime-extension", _ -> failedLogicalPreparation());
		registry.add("18-accepted-rendezvous-holds-ordinary-replan-without-taking-supply-or-own-preparation", _ ->
		{
			PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.holdsOrdinaryPartyRoot(false, true, false), "Accepted DD rendezvous root was available to ordinary Solo replan.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.holdsOrdinaryPartyRoot(true, true, false), "Rendezvous blocked its own shared-source preparation.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.holdsOrdinaryPartyRoot(false, true, true), "Assembly stole the accepted Supply/Combat/Store owner.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.holdsOrdinaryPartyRoot(false, false, false), "Stale/expired native consent held an ordinary root.");
			PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.nativePartyDecisionPrepared(false, false, true), "Holding an adopted rendezvous root also stopped ordinary Decision/Travel.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.nativePartyDecisionPrepared(false, false, false), "Unchanged, expired or stale accepted root bypassed preparation.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.nativePartyDecisionPrepared(true, false, true), "Travel continuation bypassed shared-source preparation.");
		});
		registry.add("19-coordination-candidates-reach-bounded-ecology-and-exact-native-handoff", context -> coordinationPreparation(context.moduleRoot()));
		registry.add("20-logical-assembly-holds-solo-without-taking-shared-or-necessary-child-work", _ ->
		{
			PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.holdsLogicalAssemblyRoot(false, true, false, false), "Solo work escaped the finite logical assembly before a physical offer existed.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.holdsLogicalAssemblyRoot(true, true, false, false), "Logical wait blocked its own explicit shared-source preparation.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.holdsLogicalAssemblyRoot(false, true, true, false), "Logical wait took an accepted child/combat/store owner.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.holdsLogicalAssemblyRoot(false, true, false, true), "Logical wait blocked a necessary new Supply/Learning proposal.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.holdsLogicalAssemblyRoot(false, false, false, false), "Expired, draining or stale logical membership held Solo work.");
		});
		registry.add("21-accepted-physical-dd-can-propose-shared-travel-without-solo-victory", _ ->
		{
			PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.rendezvousTravelOnly(true, true, false), "Accepted physical DD still needed a Solo-victory proof to meet its support.");
			PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.rendezvousTravelOnly(true, false, true), "Exact learned-support travel lost its existing permission.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.rendezvousTravelOnly(false, true, true), "Ordinary Solo preparation inherited a physical rendezvous travel proposal.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.rendezvousTravelOnly(true, false, false), "Logical-only or stale physical consent supplied travel permission.");
		});
		registry.add("22-failed-logical-idle-can-adopt-only-current-native-shared-preparation", _ ->
		{
			final var failed = PhantomBackgroundCatchupState.Status.FAILED_REPLAN_REQUIRED;
			for (String reason : List.of("native_context.required:logical_party_preparation", "native_context.required:logical_party_support"))
			{
				PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.visibleIdleClaimAccepted(failed, reason, true, () -> true), "An exact admitted logical native prerequisite could not leave canonical idle for its current shared preparation.");
				PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.visibleIdleClaimAccepted(failed, reason, false, () -> true), "Ordinary Solo idle inherited failed shared-preparation permission.");
				PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.visibleIdleClaimAccepted(failed, reason, true, () -> false), "Stale native handoff supplied failed-idle permission.");
			}
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.visibleIdleClaimAccepted(failed, "catchup.baseline.conflict", true, () -> true), "Unrelated failure was laundered through shared idle preparation.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.visibleIdleClaimAccepted(failed, () -> true), "Default ordinary failed-idle policy changed.");
		});
		registry.add("23-unclaimed-native-prerequisite-reaches-real-population-admission", context -> unclaimedNativePrerequisite(context.moduleRoot()));
	}
	private static void unclaimedNativePrerequisite(Path module) throws Exception
	{
		try (var fixture = new NativeRequestPresenceFixture(module))
		{
			fixture.completeHistory(); fixture.bindNativeRequirement(() -> false);
			final var completed = fixture.history.get(); fixture.unclaimedNativeRequired = true;
			fixture.clock.set(fixture.clock.instant().plusSeconds(60));
			final long now = System.currentTimeMillis();
			final var physical = new AtomicReference<>(new org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand(
				org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.Kind.LOGICAL_PARTY_WAKE, 2,20,"group.fixture",now,now+30_000));
			fixture.ecology.installMaterializationDemand(_ -> physical.get() != null && physical.get().current(System.currentTimeMillis()));
			fixture.prepare(1);
			final var due = fixture.ecology.requestMaterializationDue(fixture.profileId);
			PhantomAssertions.assertTrue(due.complete() && due.request().kind() == MaterializationRequest.Kind.NORMAL, "An unclaimed native prerequisite must use the existing bounded NORMAL/no-concurrent-claim branch.");
			final var port = new PhantomReconcileFirstActivityPort(fixture.delegate);
			port.installPopulationReadiness(fixture.manager.presence(),fixture.locality,fixture.ecology,_ -> false,
				id -> fixture.manager.admissionProfile(id).map(profile -> profile.admitted()).orElse(false),fixture::backgroundDemand);
			port.installPhysicalDemand(_ -> physical.get());
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS,port.materialize(fixture.profileId).outcome(),"The native prerequisite and real Population admission waited for one another forever.");
			PhantomAssertions.assertFalse(fixture.backgroundDemand(fixture.profileId),"The fixture fabricated an accepted Background request.");
			PhantomAssertions.assertFalse(fixture.ecology.dueSnapshot(fixture.profileId).complete(),"Physical preparation was reported as completed historical time.");
			PhantomAssertions.assertEquals(completed,fixture.history.get(),"Native preparation advanced the completed history/cursor/receipt.");
			PhantomAssertions.assertFalse(fixture.ecology.schedulingSnapshotCurrent(fixture.beforeUnclaimedRejection.get()),"Failure publication retained an old false scheduling snapshot.");
			final var granted = fixture.ecology.schedulingSnapshot(List.of(fixture.profileId),fixture.clock.instant());
			fixture.prepare(0);
			PhantomAssertions.assertFalse(fixture.ecology.schedulingSnapshotCurrent(granted),"Preparation capacity withdrawal retained stale admission.");
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED,port.materialize(fixture.profileId).outcome(),"No preparation slot still admitted native context.");
			physical.set(null); fixture.ecology.updateMaterializationDemand(List.of(),1);
			for(int pulse=0;pulse<32;pulse++) { fixture.manager.onPulse(); }
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED,port.materialize(fixture.profileId).outcome(),"Withdrawn physical demand retained native preparation authority.");
		}
	}
	private static void coordinationPreparation(Path module) throws Exception
	{
		try (var fixture = new NativeRequestPresenceFixture(module))
		{
			final long now = System.currentTimeMillis();
			final var demand = new AtomicReference<>(new org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand(
				org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.Kind.LOGICAL_PARTY_WAKE, 2, 20, "group.fixture", now, now + 30_000));
			fixture.ecology.installMaterializationDemand(id -> demand.get() != null && demand.get().current(System.currentTimeMillis()));
			fixture.ecology.updateMaterializationDemand(List.of(), 1);
			PhantomAssertions.assertFalse(fixture.hasTypedDue(), "A missing preparation candidate was silently admitted.");
			final var retained = new java.util.LinkedHashMap<Long, PhantomPopulationEcologyService.DemandFact>();
			final var facts = PhantomPopulationEcologyService.mergePreparationDemands(List.of(), retained, fixture.manager.readyProfileIdsAfter(0, 16),
				id -> id == fixture.profileId && demand.get() != null && demand.get().current(System.currentTimeMillis()), fixture.nowNanos.get());
			fixture.ecology.updateMaterializationDemand(facts, 1);
			for (int pulse = 0; pulse < 32; pulse++) { fixture.manager.onPulse(); }
			PhantomAssertions.assertTrue(fixture.hasTypedDue(), "Current coordination demand never entered preparation without human locality.");
			final var port = fixture.port(fixture::backgroundDemand, false, _ -> false, fixture.ecology);
			port.installPhysicalDemand(_ -> demand.get());
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS, port.materialize(fixture.profileId).outcome(), "Preparation did not reach the exact historical handoff.");
			PhantomAssertions.assertEquals(MaterializationRequest.Kind.NATIVE_CONTEXT_HANDOFF, fixture.loaded.get().kind(), "Coordination fabricated NORMAL admission.");
			PhantomAssertions.assertFalse(facts.getFirst().couldKnow(), "Coordination fabricated human observation.");
			PhantomAssertions.assertEquals(List.of(), fixture.manager.readyProfileIdsAfter(fixture.profileId, 16), "READY paging restarted without advancing its cursor.");
			PhantomAssertions.assertEquals(facts, PhantomPopulationEcologyService.mergePreparationDemands(List.of(), retained, List.of(), _ -> true, fixture.nowNanos.incrementAndGet()), "An empty discovery page withdrew valid pending preparation.");
			demand.set(null);
			PhantomAssertions.assertEquals(List.of(), PhantomPopulationEcologyService.mergePreparationDemands(List.of(), retained, List.of(), _ -> false, fixture.nowNanos.get()), "Withdrawn demand retained a preparation lease.");
			final var many = java.util.stream.LongStream.rangeClosed(1, 5000).boxed().toList();
			PhantomAssertions.assertEquals(256, PhantomPopulationEcologyService.mergePreparationDemands(List.of(), retained, many.subList(0, 256), _ -> true, fixture.nowNanos.get()).size(), "Preparation retained an unbounded population scan.");
			final var rotated = PhantomPopulationEcologyService.mergePreparationDemands(List.of(), retained, List.of(257L), _ -> true, id -> id <= 8, fixture.nowNanos.incrementAndGet());
			PhantomAssertions.assertTrue(rotated.stream().anyMatch(fact -> fact.profileId() == 257), "A full current ambient cache permanently hid a later coordination candidate.");
			for (long id = 1; id <= 8; id++) { PhantomAssertions.assertTrue(retained.containsKey(id), "Cache rotation revoked an admitted/running preparation owner."); }
			retained.clear();
			final var probes = new java.util.concurrent.atomic.AtomicInteger();
			PhantomPopulationEcologyService.mergePreparationDemands(List.of(), retained, many, _ -> { probes.incrementAndGet(); return false; }, fixture.nowNanos.get());
			PhantomAssertions.assertEquals(1024, probes.get(), "Rejected candidates caused an unbounded discovery probe.");
			final var seen = new java.util.HashSet<Long>();
			for (int batch = 0; batch < 3; batch++)
			{
				PhantomPopulationEcologyService.mergePreparationDemands(List.of(), retained, many.subList(0, 512), _ -> true, fixture.nowNanos.incrementAndGet())
					.forEach(fact -> seen.add(fact.profileId()));
			}
			PhantomAssertions.assertEquals(512, seen.size(), "Candidates were inserted and evicted before their batch reached Ecology.");
			retained.clear(); seen.clear();
			for (int after = 0; after < many.size(); after += 16)
			{
				PhantomPopulationEcologyService.mergePreparationDemands(List.of(), retained, many.subList(after, Math.min(after + 16, many.size())), _ -> true, id -> id <= 8, fixture.nowNanos.incrementAndGet())
					.forEach(fact -> seen.add(fact.profileId()));
			}
			PhantomAssertions.assertEquals(5000, seen.size(), "Bounded READY discovery pages permanently hid later profiles.");
			for (long id = 1; id <= 8; id++) { PhantomAssertions.assertTrue(retained.containsKey(id), "Paged discovery revoked an executing owner."); }
		}
	}
	private static void failedLogicalPreparation()
	{
		final var source = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", "20101@current");
		final var root = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(1001, "farm.background", org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE,
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", "1"), new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("npc", "20101"), 1, 0, "background.farm", List.of(source),
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, "current"), "farm.background", 500, 0, 0, 0, Map.of(), "background.plan", 0);
		final var hashes = offscreenReceiptState(PhantomBackgroundState.Receipt.empty(), 0).hashes();
		final var running = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.RUNNING, "b".repeat(64), 1, 100, 110, 105, 0, 5, 1, 1, 1, root.goalId(), root.revision(), "f".repeat(64), PhantomBackgroundState.MODEL_VERSION, hashes, "");
		final var group = new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty("C".repeat(64), 1, 1,
			List.of(new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(1,"damage",1001,0), new org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Member(2,"support",1002,0)),
			source, "current", org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.Phase.AGREED, 0, 1000, "");
		for (String reason : List.of("native_context.required:logical_party_preparation", "native_context.required:logical_party_support"))
		{
			final var failed = running.blockedForGeneration(reason, 1, 1, hashes);
			PhantomAssertions.assertTrue(PhantomHistoricalBackgroundService.logicalPreparationRequired(1, root, failed, group, 1000, () -> true), "An exact failed logical owner was hidden by the old root's suitability.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.logicalPreparationRequired(1, root, failed, group, group.expiresAtMillis(), () -> true), "An expired assembly gained a native lifetime.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.logicalPreparationRequired(2, root, failed, group, 1000, () -> true), "Another profile inherited the failed claim.");
			PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.logicalPreparationRequired(1, root, failed.withPlan(1003,0,1,"e".repeat(64),1,1), group, 1000, () -> true), "A stale root requested preparation.");
		}
		PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.logicalPreparationRequired(1, root, running, group, 1000, () -> true), "Suitable running Solo work was forced native.");
		PhantomAssertions.assertFalse(PhantomHistoricalBackgroundService.logicalPreparationRequired(1, root, running.blockedForGeneration("unclassified",1,1,hashes), group, 1000, () -> true), "An unrelated failure inherited logical preparation.");
	}
	private static void groupReceiptCursor()
	{
		final var base = offscreenReceiptState(PhantomBackgroundState.Receipt.empty(), 0);
		final var source = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", "20101@current");
		final var goal = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(1001, "farm.background", org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE,
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", "1"), new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("npc", "20101"), 1, 0, "background.farm", List.of(source),
			new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, "current"), "farm.background", 500, 0, 0, 0, java.util.Map.of(), "background.plan", 0);
		final var catchup = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.COMPLETE, "b".repeat(64), 1, 100, 101, 101, 0, 1, 1, 1, 1, goal.goalId(), goal.revision(), "f".repeat(64), PhantomBackgroundState.MODEL_VERSION, base.hashes(), "");
		final var history = new PhantomBackgroundOperationKey.HistoricalIdentity(catchup.requestId(), 1, 0, 100, 101, catchup.planIdentity());
		final var key = new PhantomBackgroundOperationKey(1, base.identity().characterObjectId(), goal.goalId(), goal.revision(), 0, 0, PhantomBackgroundOperationKey.ActionKind.HISTORICAL_FARM, 20101, "current", PhantomBackgroundState.MODEL_VERSION, base.hashes(), null, history);
		final var receipt = new PhantomBackgroundState.Receipt(key.digest(), 0, 0, PhantomBackgroundTransaction.expectedAfterHash(base));
		final var settled = base.after(base.progress(), base.vitals(), base.position(), base.inventory(), base.autoGetSkills(), base.clock(), receipt);
		PhantomAssertions.assertEquals(101L, PhantomBackgroundTransaction.committedHistoricalFarmMinute(source, settled, goal, catchup).orElseThrow(), "A verified per-profile receipt did not expose its committed minute.");
		for (var state : List.of(settled.withState(PhantomBackgroundState.State.MATERIALIZED), settled.withState(PhantomBackgroundState.State.VERIFY_PENDING),
			base.after(base.progress(), base.vitals(), base.position(), base.inventory(), base.autoGetSkills(), base.clock(), new PhantomBackgroundState.Receipt(key.digest(), 0, 0, "a".repeat(64))), base))
		{ PhantomAssertions.assertTrue(PhantomBackgroundTransaction.committedHistoricalFarmMinute(source, state, goal, catchup).isEmpty(), "Native/pending/corrupt/missing receipt advanced group time."); }
		PhantomAssertions.assertTrue(PhantomBackgroundTransaction.committedHistoricalFarmMinute(new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", "20102@current"), settled, goal, catchup).isEmpty(), "Another objective's receipt advanced group time.");
		PhantomAssertions.assertTrue(PhantomBackgroundTransaction.committedHistoricalFarmMinute(source, settled, goal, catchup.withPlan(1002, 0, 1, "e".repeat(64), 1, 1)).isEmpty(), "A stale root's receipt advanced group time.");
	}
	private static void coordinatedDemand(Path module) throws Exception
	{
		try (var fixture = new NativeRequestPresenceFixture(module))
		{
			fixture.completeHistory();
			final var port = fixture.port(_ -> false, true, _ -> false, fixture.ecology);
			final long now = System.currentTimeMillis();
			final var demand = new AtomicReference<>(new org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand(
				org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.Kind.LOGICAL_PARTY_WAKE, 2, 20, "group.fixture", now, now + 30_000));
			port.installPhysicalDemand(_ -> demand.get());
			final var admitted = port.materialize(1);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS, admitted.outcome(), "Current group demand was incorrectly forced through human locality.");
			PhantomAssertions.assertEquals(MaterializationRequest.normal(), fixture.loaded.get(), "Party wake borrowed a native-context/travel owner claim.");
			fixture.manager.presence().schedule(1, PhantomActivityState.SLEEPING);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, port.materialize(1).outcome(), "Party wake bypassed sleeping calendar.");
			fixture.manager.presence().schedule(1, PhantomActivityState.ACTIVE);
			final var denied = fixture.port(_ -> false, false, _ -> false, fixture.ecology); denied.installPhysicalDemand(_ -> demand.get());
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, denied.materialize(1).outcome(), "Party wake bypassed population admission.");
			demand.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand(org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.Kind.LOGICAL_PARTY_WAKE, 2, 20, "group.fixture", now - 2000, now - 1000));
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, port.materialize(1).outcome(), "Expired Party demand remained current.");
			demand.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand(org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.Kind.ACCEPTED_TRAVEL, 1, 0, "wrong.owner", now, now + 30_000));
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, port.materialize(1).outcome(), "Typed Party path inherited accepted-travel privileges.");
			demand.set(new org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand(org.l2jmobius.gameserver.phantoms.activity.PhantomPhysicalDemand.Kind.AMBIENT_TARGET, 1, 0, "population.ambient", now, now + 30_000));
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS, port.materialize(1).outcome(), "Ambient physical target still required a human observer.");
		}
	}
	public static void main(String[] args)
	{
		if (args.length > 0 && args[0].equals("authority-landing121")) { authorityLanding(); System.out.println("OWN121_AUTHORITY_ROUTE_LANDING_PASS; DB_CONNECTIONS=0; ACTORS=0"); return; }
		if (args.length > 0 && args[0].equals("preparation121")) { preparationArrival(); System.out.println("OWN121_PREPARATION_ARRIVAL_PASS; DB_CONNECTIONS=0; ACTORS=0"); return; }
		if (args.length > 0 && args[0].equals("landing121")) { nativeLanding(); System.out.println("OWN121_NATIVE_LANDING_PASS; DB_CONNECTIONS=0; ACTORS=0"); return; }
		if (args.length > 0 && args[0].equals("ingress121")) { interruptedIngress(); System.out.println("OWN121_INTERRUPTED_INGRESS_PASS; SYNTHETIC_PROPOSAL=true; DB_CONNECTIONS=0; ACTORS=0"); return; }
		if (args.length > 0 && args[0].equals("local-return")) { localReturn(); System.out.println("OWN068_LOCAL_RETURN_PASS; DB_CONNECTIONS=0; ACTORS=0"); return; }
		canonicalFarm();
		itinerary();
		localReturn();
		nativeReturn();
		nativeLanding();
		interruptedIngress();
		preparationArrival();
		authorityLanding();
		historicalFailedStep();
		offscreenReceiptHash();
		completedOffscreenNativeHandoff();
		nativeContextRequestCache();
		try { final var module = java.nio.file.Path.of(System.getProperty("phantom.module.root", ".")); nativePartyRestartContext(module); nativeRequestPresence(module); }
		catch (Exception failure) { throw new IllegalStateException("Native request presence fixture failed.", failure); }
		System.out.println("OWN068_VISIBLE_ITINERARY_PASS; DB_CONNECTIONS=0; ACTORS=0");
	}
	private static void offscreenReceiptHash()
	{
		final String currentHash = "a".repeat(64), staleHash = "b".repeat(64);
		final var generic = offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, 0, staleHash), 0);
		final var payload = new PhantomBackgroundStateCodec().encode(generic);
		final var policy = new PhantomBackgroundSimulationPolicy(2, 76, 1, 1, true, true, true, true, false, 1,
			1, 0.6, 1.5, 2, 2.5, 3, 0, 0, 3.5, 3.5, "d".repeat(64), 126.5, 60_000, 0);
		final var context = PhantomNativeContext.completed(generic.identity(), new PhantomNativeContext.Capture(1, PhantomNativeContext.Eligibility.SUPPORTED, policy), 270, payload);
		PhantomAssertions.assertTrue(context.binds(generic.identity(), 270, payload) && context.permits(PhantomBackgroundSimulationPolicy.Operation.TRAVEL, policy.rulesFingerprint()), "The fixture must have a current COMPLETED native policy bound to the exact post-checkpoint state bytes.");
		PhantomAssertions.assertFalse(context.binds(generic.identity(), 269, payload), "The independent native-context owner still rejects a stale component version.");
		PhantomAssertions.assertFalse(PhantomOffscreenOutdoorTravel.taggedReceipt(generic), "A generic historical receipt is not typed offscreen travel debt.");
		PhantomAssertions.assertFalse(PhantomBackgroundTransaction.offscreenReceiptHashCurrent(generic, currentHash), "F7: an untagged nonempty stale receipt must fail closed, even with a current native context.");
		PhantomAssertions.assertTrue(PhantomBackgroundTransaction.offscreenReceiptHashCurrent(offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, 0, currentHash), 0), currentHash), "An exact untagged hash remains admissible.");
		PhantomAssertions.assertTrue(PhantomBackgroundTransaction.offscreenReceiptHashCurrent(offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, 0, ""), 0), currentHash), "A receipt without an expected hash retains the legacy empty-hash contract.");
		for (long debt : List.of(0L, 60_000L))
		{
			final var matching = offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, PhantomOffscreenOutdoorTravel.RECEIPT_TICK, currentHash), debt);
			final var mismatching = offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, PhantomOffscreenOutdoorTravel.RECEIPT_TICK, staleHash), debt);
			PhantomAssertions.assertTrue(PhantomOffscreenOutdoorTravel.taggedReceipt(matching), "Both pending and completed offscreen receipts retain the typed fence.");
			PhantomAssertions.assertTrue(PhantomBackgroundTransaction.offscreenReceiptHashCurrent(matching, currentHash), "The exact typed receipt may continue only with its matching hash.");
			PhantomAssertions.assertFalse(PhantomBackgroundTransaction.offscreenReceiptHashCurrent(mismatching, currentHash), "Typed offscreen replay must remain fenced on hash mismatch, including zero residual debt.");
		}
		PhantomAssertions.assertEquals(staleHash, generic.receipt().expectedAfterHash(), "Hash admission cannot clear or overwrite the old receipt.");
		System.out.println("TASK130_STRICT_RECEIPT_HASH_PASS; tagged and untagged mismatch rejected; DB_CONNECTIONS=0; SQL_REPLAY=NOT_RUN");
	}
	private static void completedOffscreenNativeHandoff()
	{
		final var receipt = new PhantomBackgroundState.Receipt("c".repeat(64), 0, PhantomOffscreenOutdoorTravel.RECEIPT_TICK, "a".repeat(64));
		final var arrived = offscreenReceiptState(receipt, 0);
		final var context = permittedHistoricalContext(arrived);
		PhantomAssertions.assertTrue(context.binds(arrived.identity(), 270, new PhantomBackgroundStateCodec().encode(arrived))
			&& context.permits(PhantomBackgroundSimulationPolicy.Operation.FARM, L2jPhantomBackgroundAuthority.configuredSimulationFingerprint()),
			"The actual handoff counterexample must have a current bound native context which already permits FARM.");
		final var pending = offscreenReceiptState(receipt, 60_000);
		PhantomAssertions.assertFalse(PhantomBackgroundService.historicalNativeContextRequired(pending, permittedHistoricalContext(pending)),
			"Pending typed virtual debt continues through its existing travel owner, not a completed-arrival handoff.");
		final var generic = offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, 0, "a".repeat(64)), 0);
		PhantomAssertions.assertFalse(PhantomBackgroundService.historicalNativeContextRequired(generic, permittedHistoricalContext(generic)),
			"An unrelated generic receipt must retain the existing policy-only admission.");
		final var invalid = arrived.withState(PhantomBackgroundState.State.INCONSISTENT);
		PhantomAssertions.assertFalse(PhantomBackgroundService.historicalNativeContextRequired(invalid, permittedHistoricalContext(invalid)),
			"A non-READY inconsistent state cannot obtain a completed-arrival handoff.");
		final var beforeSpawn = arrived.withState(PhantomBackgroundState.State.MATERIALIZED);
		PhantomAssertions.assertEquals(receipt, PhantomOffscreenOutdoorTravel.nativeReceipt(beforeSpawn, false),
			"Pre-spawn materialization and aborted-load cleanup must retain the typed observer fence.");
		final var retired = PhantomOffscreenOutdoorTravel.nativeReceipt(beforeSpawn, true);
		PhantomAssertions.assertEquals(PhantomBackgroundState.Receipt.empty(), retired,
			"Only capture of the exact World Player may retire the MATERIALIZED typed receipt.");
		final var recaptured = arrived.after(arrived.progress(), arrived.vitals(), arrived.position(), arrived.inventory(), arrived.autoGetSkills(), arrived.clock(), retired);
		PhantomAssertions.assertFalse(PhantomBackgroundService.historicalNativeContextRequired(recaptured, permittedHistoricalContext(recaptured)),
			"A normally captured and stored READY body returns to existing native-policy admission.");
		PhantomAssertions.assertTrue(PhantomBackgroundService.historicalNativeContextRequired(arrived, context),
			"A completed typed offscreen receipt requires the existing canonical native handoff even when its context permits FARM.");
	}
	private static PhantomNativeContext permittedHistoricalContext(PhantomBackgroundState state)
	{
		final var policy = new PhantomBackgroundSimulationPolicy(2, 76, 1, 1, true, true, true, true, false, 1,
			1, 0.6, 1.5, 2, 2.5, 3, 0, 0, 3.5, 3.5, L2jPhantomBackgroundAuthority.configuredSimulationFingerprint(), 126.5, 60_000, 0);
		return PhantomNativeContext.completed(state.identity(), new PhantomNativeContext.Capture(1, PhantomNativeContext.Eligibility.SUPPORTED, policy),
			270, new PhantomBackgroundStateCodec().encode(state));
	}

	public static boolean nativeOnlyRestartRequired(boolean partyNativeOnly)
	{
		final var state = offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, 0, "a".repeat(64)), 0);
		final var context = permittedHistoricalContext(state);
		PhantomAssertions.assertTrue(context.phase() == PhantomNativeContext.Phase.COMPLETED && context.binds(state.identity(), 270, new PhantomBackgroundStateCodec().encode(state))
			&& context.permits(PhantomBackgroundSimulationPolicy.Operation.FARM, L2jPhantomBackgroundAuthority.configuredSimulationFingerprint()) && !PhantomOffscreenOutdoorTravel.taggedReceipt(state), "Restart prerequisite must be a current FARM-permitted COMPLETED context without outdoor debt.");
		return PhantomBackgroundService.historicalNativeContextRequired(state, context, partyNativeOnly);
	}
	private static void nativePartyRestartContext(Path module) throws Exception
	{
		PhantomAssertions.assertFalse(nativeOnlyRestartRequired(false), "Unowned ordinary historical FARM gained native-only admission.");
		try (var fixture = new NativeRequestPresenceFixture(module, 1))
		{
			fixture.bindNativeRequirement(() -> nativeOnlyRestartRequired(false)); fixture.prepare(1);
			PhantomAssertions.assertFalse(fixture.backgroundDemand(1) || fixture.hasTypedDue(), "An unowned FARM-permitted context retained a native request or typed Due.");
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, fixture.materializeCurrent().outcome(), "An unowned FARM-permitted context reached the native delegate.");
			fixture.bindNativeRequirement(() -> nativeOnlyRestartRequired(true)); fixture.prepare(1);
			final var result = fixture.materializeCurrent();
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS, result.outcome(), "RED129: current Party-native-only ownership must drive real Ecology/Presence typed admission even when the stored context permits FARM: " + result);
			PhantomAssertions.assertEquals(MaterializationRequest.nativeContextHandoff(fixture.claim), result.request(), "The restart native request lost its exact pending historical claim.");
		}
	}
	private static void nativeContextRequestCache()
	{
		final long issued = 1_000_000_000L, ttl = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(PhantomBackgroundService.NATIVE_CONTEXT_SIGNAL_TTL_MILLIS);
		final var accepted = org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery.ACCEPTED;
		final var running = PhantomBackgroundService.ServiceState.RUNNING;
		final var published = new PhantomBackgroundService.NativeContextSignal(1, 1, true, false, null, issued);
		final var request = published.delivered(true, false, accepted);
		PhantomAssertions.assertEquals(issued, request.issuedAtNanos(), "An asynchronous ACK must not renew the published request age.");
		PhantomAssertions.assertFalse(PhantomBackgroundService.nativeContextRequestCurrent(running, published, published, issued), "An unacknowledged request granted demand.");
		PhantomAssertions.assertTrue(PhantomBackgroundService.nativeContextRequestCurrent(running, request, request, issued + ttl - 1), "An exact accepted request expired before its finite TTL.");
		PhantomAssertions.assertFalse(PhantomBackgroundService.nativeContextRequestCurrent(running, request, request, issued + ttl), "A stale accepted ACK survived its published TTL.");
		PhantomAssertions.assertFalse(PhantomBackgroundService.nativeContextRequestCurrent(running, request, request, issued - 1), "A future timestamp granted request demand.");
		for (var state : List.of(PhantomBackgroundService.ServiceState.NEW, PhantomBackgroundService.ServiceState.STOPPING, PhantomBackgroundService.ServiceState.STOPPED, PhantomBackgroundService.ServiceState.FAILED))
		{
			PhantomAssertions.assertFalse(PhantomBackgroundService.nativeContextRequestCurrent(state, request, request, issued), "A non-running service retained demand: " + state);
		}
		for (var rejected : List.of(request.delivered(false, false, accepted), request.delivered(true, true, accepted),
			request.delivered(true, false, org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery.STALE)))
		{
			PhantomAssertions.assertFalse(PhantomBackgroundService.nativeContextRequestCurrent(running, rejected, rejected, issued), "Withdrawn, retiring or nonaccepted delivery granted demand.");
		}
		final var coalesced = request.delivered(true, false, org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort.SignalDelivery.COALESCED);
		PhantomAssertions.assertTrue(PhantomBackgroundService.nativeContextRequestCurrent(running, coalesced, coalesced, issued), "A current coalesced request lost its existing ACK semantics.");
		final var newer = new PhantomBackgroundService.NativeContextSignal(2, 2, true, false, accepted, issued);
		PhantomAssertions.assertFalse(PhantomBackgroundService.nativeContextRequestCurrent(running, request, newer, issued), "An old sequence/record granted demand after replacement.");
		final var values = new java.util.LinkedHashMap<Long, PhantomBackgroundService.NativeContextSignal>();
		for (long id = 1; id <= 5000; id++) { values.put(id, id == 5000 ? newer : request.delivered(false, false, accepted)); }
		final var delegate = values.entrySet().iterator();
		final var examined = new java.util.concurrent.atomic.AtomicInteger();
		final java.util.Iterator<java.util.Map.Entry<Long, PhantomBackgroundService.NativeContextSignal>> scan = new java.util.Iterator<>()
		{
			@Override public boolean hasNext() { return delegate.hasNext(); }
			@Override public java.util.Map.Entry<Long, PhantomBackgroundService.NativeContextSignal> next() { examined.incrementAndGet(); return delegate.next(); }
		};
		final var found = new java.util.ArrayList<Long>();
		while (scan.hasNext())
		{
			final int before = examined.get();
			found.addAll(PhantomBackgroundService.nativeContextRequestPage(running, scan, values, issued, 16));
			PhantomAssertions.assertTrue(examined.get() - before <= 16, "A request-cache page inspected more than16 entries.");
		}
		PhantomAssertions.assertEquals(List.of(5000L), found, "Bounded pages lost the current request behind5000 retained signal records.");
		PhantomAssertions.assertEquals(5000, examined.get(), "Bounded pages rescanned or skipped cache entries.");
		PhantomAssertions.assertTrue(PhantomBackgroundService.nativeContextRequestPage(PhantomBackgroundService.ServiceState.STOPPED, scan, values, issued, 256).isEmpty(), "Stopped discovery returned candidates.");
		PhantomAssertions.assertFalse(org.l2jmobius.commons.database.DatabaseFactory.isInitialized(), "Cache-only admission initialized a database.");
	}
	private static void nativeRequestPresence(Path module) throws Exception
	{
		try (var fixture = new NativeRequestPresenceFixture(module))
		{
			fixture.prepare(0);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, fixture.port(fixture::backgroundDemand, false, _ -> false, fixture.ecology).materialize(1).outcome(), "Background demand bypassed preparation capacity0.");
			fixture.prepare(1);
			final var port = fixture.port(fixture::backgroundDemand, false, _ -> false, fixture.ecology);
			port.installSoftReclamationEpochs(_ -> new PhantomActivityMaterializationPort.SoftReclaimSelection(2, 99), _ -> true, _ -> true);
			PhantomAssertions.assertEquals(0L, port.softReclaimSelection(1).profileId(), "Sole Background demand obtained accepted-journey soft-reclaim rights.");
			fixture.nowNanos.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(PhantomBackgroundService.NATIVE_CONTEXT_SIGNAL_TTL_MILLIS));
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, port.materialize(1).outcome(), "Expired request reached the delegate.");
			fixture.resetRequest();
			fixture.current.set(fixture.current.get().delivered(true, true, PhantomRelevanceSignalPort.SignalDelivery.ACCEPTED));
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, port.materialize(1).outcome(), "Retiring request reached the delegate.");
			fixture.resetRequest();
			fixture.serviceState.set(PhantomBackgroundService.ServiceState.STOPPING);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, port.materialize(1).outcome(), "Stopped service reached the delegate.");
			fixture.serviceState.set(PhantomBackgroundService.ServiceState.RUNNING);
			fixture.current.set(new PhantomBackgroundService.NativeContextSignal(2, 2, true, false, PhantomRelevanceSignalPort.SignalDelivery.ACCEPTED, fixture.nowNanos.get()));
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, port.materialize(1).outcome(), "Replaced request sequence reached the delegate.");
			fixture.resetRequest();
			fixture.manager.presence().schedule(1, PhantomActivityState.SLEEPING);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, port.materialize(1).outcome(), "Sole Background demand bypassed the current sleeping calendar.");
			fixture.manager.presence().schedule(1, PhantomActivityState.ACTIVE);
			PhantomAssertions.assertEquals(null, fixture.loaded.get(), "A negative native-request case reached materialization.");
			fixture.ecology.installMaterializationDemand(_ -> true);
			fixture.prepare(1);
			fixture.manager.presence().schedule(1, PhantomActivityState.SLEEPING);
			final var rawSleepingDue = fixture.ecology.requestMaterializationDue(1);
			PhantomAssertions.assertTrue(!fixture.manager.presence().isOnline(1) && rawSleepingDue.complete()
				&& rawSleepingDue.request().equals(MaterializationRequest.nativeContextHandoff(fixture.claim)),
				"The raw-Background SLEEP negative must have a real typed ecology handoff available despite its sleeping calendar: " + rawSleepingDue);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED,
				fixture.port(_ -> true, false, _ -> false, fixture.ecology).materialize(1).outcome(),
				"A raw Background predicate bypassed the port's own sleeping-calendar guard.");
			PhantomAssertions.assertEquals(null, fixture.loaded.get(), "The raw Background SLEEP negative reached materialization.");
			fixture.manager.presence().schedule(1, PhantomActivityState.ACTIVE);
			fixture.ecology.installMaterializationDemand(fixture::backgroundDemand);
			try (var normal = new NativeRequestPresenceFixture(module))
			{
				normal.completeHistory();
				final var due = normal.ecology.requestMaterializationDue(1);
				PhantomAssertions.assertTrue(due.complete() && due.request().kind() == MaterializationRequest.Kind.NORMAL, "NORMAL-negative fixture did not finish its real ecology request.");
				for (boolean admitted : List.of(false, true))
				{
					final var normalPort = normal.port(normal::backgroundDemand, admitted, _ -> false, normal.ecology);
					PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, normalPort.materialize(1).outcome(), "Sole Background demand granted a NORMAL request; admitted=" + admitted);
				}
				PhantomAssertions.assertEquals(null, normal.loaded.get(), "Stale Background demand loaded after catchup became COMPLETE.");
			}
			fixture.prepare(1);
			final var result = port.materialize(1);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS, result.outcome(), "A current Background request must reach the real bounded ecology handoff: " + result + ";due=" + fixture.ecology.dueSnapshot(1));
			PhantomAssertions.assertEquals(MaterializationRequest.nativeContextHandoff(fixture.claim), fixture.loaded.get(), "The exact historical owner claim was lost at the native delegate.");
			PhantomAssertions.assertFalse(fixture.ecology.permitsScheduling(1), "Native-context handoff opened ordinary ecology admission.");
			fixture.loaded.set(null);
			final var reads = new java.util.concurrent.atomic.AtomicInteger();
			final var expiredAfterDue = fixture.port(id -> fixture.backgroundDemand(id) && reads.incrementAndGet() == 1, false, _ -> false, fixture.ecology);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.DEFERRED, expiredAfterDue.materialize(1).outcome(), "A request lost after due proof still materialized.");
			PhantomAssertions.assertEquals(2, reads.get(), "The Background request was not rechecked after the typed due proof.");
			PhantomAssertions.assertEquals(null, fixture.loaded.get(), "An expired final Background check reached the delegate.");
			fixture.manager.presence().schedule(1, PhantomActivityState.SLEEPING);
			final var acceptedJourney = fixture.port(_ -> false, false, _ -> true, null);
			PhantomAssertions.assertEquals(PhantomActivityMaterializationPort.Outcome.SUCCESS, acceptedJourney.materialize(1).outcome(), "The separate pre-existing accepted journey lost its ordinary continuation semantics.");
			PhantomAssertions.assertEquals(MaterializationRequest.normal(), fixture.loaded.get(), "An ordinary accepted journey was converted to a Background typed request.");
		}
		PhantomAssertions.assertFalse(org.l2jmobius.commons.database.DatabaseFactory.isInitialized(), "The memory-port handoff fixture initialized SQL.");
	}

	/** Reuses the public memory stores and HistoricalPort from the existing readiness102 fixture. */
	/** Test composition bridge to the exact production retirement decision, without a native Player. */
	public static boolean nativeRetirementRequired(org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Snapshot proof, int objectId, long epoch, boolean partyRetained)
	{
		return PhantomHistoricalBackgroundService.nativeHandoffRetirementRequired(proof, objectId, epoch, partyRetained);
	}
	public static boolean nativePartyDecisionPrepared(boolean sharedRendezvousPreparation, boolean committedNativeParty)
	{
		return PhantomHistoricalBackgroundService.nativePartyDecisionPrepared(sharedRendezvousPreparation, committedNativeParty);
	}

	public static final class NativeRequestPresenceFixture implements AutoCloseable
	{
		final long profileId;
		private final int characterObjectId;
		final String claim = "e".repeat(64);
		final AtomicLong nowNanos = new AtomicLong(1_000_000_000L);
		final AtomicReference<PhantomBackgroundService.ServiceState> serviceState = new AtomicReference<>(PhantomBackgroundService.ServiceState.RUNNING);
		final AtomicReference<PhantomBackgroundService.NativeContextSignal> captured = new AtomicReference<>(), current = new AtomicReference<>();
		final AtomicReference<MaterializationRequest> loaded = new AtomicReference<>();
		final AtomicReference<PhantomBackgroundCatchupStore.Snapshot> history = new AtomicReference<>();
		private java.util.function.BooleanSupplier nativeRequirement = () -> true;
		private boolean unclaimedNativeRequired;
		final PhantomPopulationTestDoubles.MutableClock clock;
		final AtomicReference<PhantomPopulationEcologyService.SchedulingSnapshot> beforeUnclaimedRejection = new AtomicReference<>();
		final PhantomPopulationManager manager;
		final PhantomPopulationEcologyService ecology;
		final PhantomHumanLocalityControl locality;
		final PhantomActivityMaterializationPort delegate = new PhantomActivityMaterializationPort()
		{
			@Override public TransitionOutcome materialize(long id) { loaded.set(MaterializationRequest.normal()); return TransitionOutcome.success(); }
			@Override public TransitionOutcome materialize(long id, MaterializationRequest request) { loaded.set(request); return TransitionOutcome.success(request); }
			@Override public TransitionOutcome dematerialize(long id) { return TransitionOutcome.success(); }
			@Override public TransitionOutcome retryCleanup(long id) { return TransitionOutcome.deferred(); }
			@Override public boolean isMaterialized(long id) { return false; }
			@Override public boolean hasLifecycleOwnership(long id) { return false; }
		};
		NativeRequestPresenceFixture(Path module) throws Exception
		{
			this(module, 1);
		}
		public NativeRequestPresenceFixture(Path module, long profileId) throws Exception
		{
			this.profileId = profileId;
			final var instant = Instant.parse("2026-01-05T20:30:00Z");
			final long target = instant.toEpochMilli() / 60000, from = target - 120;
			final var population = PhantomPopulationCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-population-v1.xml"), ZoneOffset.UTC);
			final var social = PhantomSocialCatalog.load(module.resolve("dist/game/data/phantoms/social/high-five-social-v1.xml"));
			final var catalog = PhantomPopulationEcologyCatalog.load(module.resolve("dist/game/data/phantoms/population/high-five-ecology-v1.xml"), population, social);
			final var store = new PhantomPopulationTestDoubles.MemoryStore(population.hash());
			final var ready = store.seedReady(profileId, 1);
			characterObjectId = ready.profile().characterObjectId();
			final var states = new EcologyMemoryStore(null);
			states.insert(profileId, new PhantomPopulationEcologyState(catalog.hash(), PhantomPopulationEcologyState.Preset.LIVING, 1, 1, target, from, from, target,
				PhantomPopulationEcologyState.Pace.OUTLIER, 10000, catalog.limits().productiveBlockMinutes(), PhantomPopulationEcologyState.Personality.BALANCED,
				Map.of(1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0), ready.state().scheduleTemplate(), PhantomPopulationEcologyState.Disposition.MANAGED, target + 10000, 0, claim, target, 0, 0, ""));
			history.set(new PhantomBackgroundCatchupStore.Snapshot(new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.PENDING, claim, 1, from, target, from, 0, 0, 1, 1, 1, 1, 0, "f".repeat(64), PhantomBackgroundState.MODEL_VERSION, new PhantomBackgroundState.Hashes("knowledge", "topology", "progression", "commerce"), ""), 0));
			final PhantomPopulationEcologyService.HistoricalPort historical = new PhantomPopulationEcologyService.HistoricalPort()
			{
				@Override public Optional<PhantomBackgroundCatchupStore.Snapshot> status(long id) { return Optional.of(history.get()); }
				@Override public PhantomHistoricalBackgroundService.Result begin(long id, long start, long end, long seed)
				{
					if (!unclaimedNativeRequired) { throw new AssertionError("The pending native request was restarted."); }
					beforeUnclaimedRejection.compareAndSet(null,ecology.schedulingSnapshot(List.of(profileId),clock.instant()));
					return PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED,"native_context.required:logical_party_support",history.get());
				}
				@Override public PhantomHistoricalBackgroundService.Result advance(long id, int intervals, int minutes)
				{
					return nativeRequirement.getAsBoolean()
						? PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.REPLAN_REQUIRED, "native_context.required:accepted", history.get())
						: PhantomHistoricalBackgroundService.Result.rejected(PhantomHistoricalBackgroundService.ResultStatusCode.RETRY, "fixture.native_context_not_required", history.get());
				}
			};
			clock = new PhantomPopulationTestDoubles.MutableClock(instant);
			ecology = new PhantomPopulationEcologyService(catalog, population, states, historical, _ -> false, _ -> "", clock, ZoneOffset.UTC, PhantomPopulationEcologyState.Preset.LIVING, 0, 10, worker -> { worker.run(); return true; });
			ecology.enablePeriodicDueMode();
			manager = new PhantomPopulationManager(store, population, null, new PhantomPopulationTestDoubles.Ownership(), clock, ZoneOffset.UTC, 1, 1, 2, 1, 1, 64);
			manager.installEcology(ecology);
			ecology.installMaterializationDemand(this::backgroundDemand);
			final PhantomRelevanceSignalPort signals = new PhantomRelevanceSignalPort()
			{
				@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			locality = new PhantomHumanLocalityControl(PhantomTopologyService.inertForTesting(signals, 2), signals, List::of, () -> 1000);
			resetRequest();
			PhantomAssertions.assertTrue(manager.start(), "Native request memory population did not start.");
			for (int pulse = 0; pulse < 32; pulse++) { manager.onPulse(); }
			PhantomAssertions.assertTrue(manager.presence().isOnline(profileId), "Fixture calendar is not online.");
		}
		public void resetRequest()
		{
			final var previous = current.get();
			final long sequence = previous == null ? 1 : previous.sequence() + 1;
			// A false production verdict models an accepted withdrawal, never an accepted native request.
			final var request = new PhantomBackgroundService.NativeContextSignal(sequence, sequence, nativeRequirement.getAsBoolean(), false, PhantomRelevanceSignalPort.SignalDelivery.ACCEPTED, nowNanos.get());
			captured.set(request); current.set(request);
		}
		public void bindNativeRequirement(java.util.function.BooleanSupplier required)
		{
			nativeRequirement = java.util.Objects.requireNonNull(required); resetRequest();
		}
		public void expireRequest() { nowNanos.addAndGet(java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(PhantomBackgroundService.NATIVE_CONTEXT_SIGNAL_TTL_MILLIS)); }
		public int characterObjectId() { return characterObjectId; }
		public boolean backgroundDemand(long id)
		{
			return nativeRequirement.getAsBoolean() && PhantomBackgroundService.nativeContextMaterializationDemand(manager.presence().isOnline(id),
				PhantomBackgroundService.nativeContextRequestCurrent(serviceState.get(), captured.get(), current.get(), nowNanos.get()));
		}
		public void prepare(int slots)
		{
			ecology.updateMaterializationDemand(List.of(new PhantomPopulationEcologyService.DemandFact(profileId, 0, false, Long.MAX_VALUE, nowNanos.get())), slots);
			for (int pulse = 0; pulse < 32; pulse++) { manager.onPulse(); }
		}
		void completeHistory()
		{
			final var previous = history.get();
			history.set(new PhantomBackgroundCatchupStore.Snapshot(previous.state().advanceTo(previous.state().targetEpochMinute()), previous.rowVersion() + 1));
			for (int pulse = 0; pulse < 64; pulse++) { manager.onPulse(); }
		}
		PhantomReconcileFirstActivityPort port(LongPredicate background, boolean admitted, LongPredicate acceptedJourney, PhantomPopulationEcologyService due)
		{
			final var port = new PhantomReconcileFirstActivityPort(delegate);
			port.installPopulationReadiness(manager.presence(), locality, due, acceptedJourney, _ -> admitted, background);
			return port;
		}
		/** Existing real presence/ecology gate; only the native materialization leaf is modeled. */
		public TransitionOutcome materializeCurrent()
		{
			return port(this::backgroundDemand, false, _ -> false, ecology).materialize(profileId);
		}
		public boolean hasTypedDue()
		{
			final var due = ecology.requestMaterializationDue(profileId);
			return due.complete() && due.request().kind() == MaterializationRequest.Kind.NATIVE_CONTEXT_HANDOFF;
		}
		@Override public void close()
		{
			manager.beginStop();
			PhantomAssertions.assertTrue(manager.finishStop(), "Native request memory population did not stop.");
		}
	}

	private static PhantomBackgroundState offscreenReceiptState(PhantomBackgroundState.Receipt receipt, long debt)
	{
		return new PhantomBackgroundState(PhantomBackgroundState.State.READY, new PhantomBackgroundState.Identity(1, 268480990, 0, 88, 0),
			new PhantomBackgroundState.Progress(76, 0, 0, 0), new PhantomBackgroundState.Vitals(100, 100, 100, 100, 100, 100),
			new PhantomBackgroundState.Position(0, 148924, -83377, -5568, 0, "current"),
			new PhantomBackgroundState.CombatFacts(PhantomBackgroundState.ModelKind.MELEE, 100, 100, 100, 100, 500, 500, 1, 1, 1, 1, 0, 1, 1, 1, 1),
			PhantomBackgroundState.Loadout.none(), new PhantomBackgroundState.InventoryFacts(List.of(), List.of(), "fixture129", 0, 1000, 0, 100), List.of(),
			new PhantomBackgroundState.Clock(129, debt, 0), receipt, new PhantomBackgroundState.Hashes("knowledge129", "topology129", "progression129", "commerce129"));
	}
	private static void historicalFailedStep()
	{
		final var before = new PhantomBackgroundState.Position(0, 148924, -83377, -5568, 0, "current");
		final var clock = new PhantomBackgroundState.Clock(129, 0, 7);
		final String failed = "m1.walk.745db94a7197bb6c23214e99";
		final var mismatch = new PhantomBackgroundAuthority.TravelAdvance(PhantomBackgroundAuthority.TravelAdvance.Status.ANCHOR_MISMATCH, before, clock, failed);
		PhantomAssertions.assertEquals(failed, PhantomBackgroundService.historicalFailedTravelStep(before, clock, mismatch), "The exact unchanged failed next step must reach the existing recovery planner exclusions.");
		for (var status : PhantomBackgroundAuthority.TravelAdvance.Status.values())
		{
			if (status == PhantomBackgroundAuthority.TravelAdvance.Status.ANCHOR_MISMATCH) { continue; }
			PhantomAssertions.assertEquals("", PhantomBackgroundService.historicalFailedTravelStep(before, clock,
				new PhantomBackgroundAuthority.TravelAdvance(status, before, clock, failed)), "Another travel result cannot supply an anchor-mismatch exclusion.");
		}
		for (var invalid : List.of(
			new PhantomBackgroundAuthority.TravelAdvance(mismatch.status(), new PhantomBackgroundState.Position(0, before.x() + 1, before.y(), before.z(), 0, "current"), clock, failed),
			new PhantomBackgroundAuthority.TravelAdvance(mismatch.status(), before, new PhantomBackgroundState.Clock(clock.rngState(), 1, clock.residualEncounterMillis()), failed),
			new PhantomBackgroundAuthority.TravelAdvance(mismatch.status(), before, clock, failed, 1),
			new PhantomBackgroundAuthority.TravelAdvance(mismatch.status(), before, clock, " ")))
		{
			PhantomAssertions.assertEquals("", PhantomBackgroundService.historicalFailedTravelStep(before, clock, invalid), "Changed state, time, money or an absent edge cannot be laundered into a recovery exclusion.");
		}
		PhantomAssertions.assertEquals("", PhantomBackgroundService.historicalFailedTravelStep(before, clock, null), "An absent authority result must retain the failed owner for retry.");
		System.out.println("OWN129_HISTORICAL_FAILED_STEP_PASS; unchanged failure only; DB_CONNECTIONS=0; ACTORS=0");
	}
	private static void authorityLanding()
	{
		final var canonical = new PhantomBackgroundState.Position(0, 82971, 53207, -1488, 0, "oren.route");
		final var route = org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.ROUTE;
		final java.util.function.IntSupplier unused = () -> { throw new AssertionError("Unrelated positions cannot query landing Geo."); };
		PhantomAssertions.assertTrue(L2jPhantomBackgroundAuthority.nativeAnchorPositionAllowed(canonical.x(), canonical.y(), canonical.z(), 0, canonical, route, 0, unused), "Ordinary canonical arrival preserves the original strict branch.");
		PhantomAssertions.assertTrue(L2jPhantomBackgroundAuthority.nativeAnchorPositionAllowed(canonical.x(), canonical.y(), canonical.z() + 5, 0, canonical, route, 0, canonical::z), "The actual native Oren landing needs the same physical-floor recognition for live and stored state.");
		for (var role : List.of(org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.CITY_CENTER, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING))
		{
			PhantomAssertions.assertTrue(!L2jPhantomBackgroundAuthority.nativeAnchorPositionAllowed(canonical.x(), canonical.y(), canonical.z() + 5, 0, canonical, role, 0, unused), "The exception cannot relax city or farm position requirements.");
		}
		PhantomAssertions.assertTrue(!L2jPhantomBackgroundAuthority.nativeAnchorPositionAllowed(canonical.x(), canonical.y(), canonical.z() + 5, 0, canonical, route, 1, unused), "Nonzero tolerance keeps its original bounds.");
		for (var point : List.of(new PhantomNavigationPoint(canonical.x() + 1, canonical.y(), canonical.z() + 5, 0), new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z() + 5, 1), new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z() - 5, 0), new PhantomNavigationPoint(canonical.x(), canonical.y(), canonical.z() + 6, 0)))
		{
			PhantomAssertions.assertTrue(!L2jPhantomBackgroundAuthority.nativeAnchorPositionAllowed(point.x(), point.y(), point.z(), point.instanceId(), canonical, route, 0, unused), "Landing recognition keeps exact XY/instance and the native positive five-unit offset.");
		}
		PhantomAssertions.assertTrue(!L2jPhantomBackgroundAuthority.nativeAnchorPositionAllowed(canonical.x(), canonical.y(), canonical.z() + 5, 0, canonical, route, 0, () -> canonical.z() - 64), "A different or unproven Geo floor cannot identify the canonical route anchor.");
	}
	private static void preparationArrival()
	{
		final var pending = new java.util.concurrent.atomic.AtomicBoolean(true);
		final var calls = new java.util.ArrayList<String>();
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.yieldForPreparation(pending::get,
			() -> { calls.add("capture"); pending.set(false); return true; },
			() -> { calls.add("yield"); return true; }), "Preparation must finish an existing arrival receipt before yielding, including an intermediate anchor.");
		PhantomAssertions.assertEquals(List.of("capture", "yield"), calls, "Preparation cannot skip the pending checkpoint or start another travel segment.");
		final java.util.function.BooleanSupplier forbidden = () -> { throw new AssertionError("An unproven checkpoint cannot yield or advance."); };
		PhantomAssertions.assertTrue(!PhantomVisibleFarmTravel.yieldForPreparation(() -> true, () -> false, forbidden), "A refused or retrying capture retains ownership.");
		PhantomAssertions.assertTrue(!PhantomVisibleFarmTravel.yieldForPreparation(() -> true, () -> true, forbidden), "A replaced pending checkpoint cannot borrow a successful earlier receipt.");
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.yieldForPreparation(() -> false, forbidden, () -> true), "No pending checkpoint must not create a new arrival or movement.");
		final var reads = new java.util.concurrent.atomic.AtomicInteger();
		PhantomAssertions.assertTrue(!PhantomVisibleFarmTravel.yieldForPreparation(() -> reads.incrementAndGet() > 1, forbidden, forbidden), "A checkpoint published after the first read must fence preparation even without a Journey.");
	}
	private static org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor entry(String id, int x, int instance)
	{
		return new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor(id, org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING,
			id + ".node", new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(x, 0, 0, instance), null, null, 0, List.of(), List.of("fixture"));
	}
	private static void interruptedIngress()
	{
		final var live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(0, 0, 0, 0);
		final var committed = new PhantomBackgroundState.Position(0, 100_000, 0, 0, 0, "old");
		final var original = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "original", "old", "final", 0, null);
		final var route = List.of(original);
		final var disconnected = entry("disconnected", 10, 0); final var foreign = entry("foreign", 20, 1);
		final var ingress = entry("entry", 2000, 0); final var farther = entry("farther", 3000, 0);
		final var anchors = List.of(disconnected, foreign, farther, ingress, entry("final", 10000, 0));
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(route, null, committed, live, "final"), "Distant persisted lineage is not factual departure after an interrupted native journey.");
		final var proposed = PhantomVisibleFarmTravel.resumeWithIngress(route, null, committed, live, "final", anchors, 12000, anchor -> !anchor.id().equals("disconnected"));
		PhantomAssertions.assertTrue(proposed != null, "An off-anchor restart needs the nearest existing connected ingress proposal.");
		PhantomAssertions.assertEquals("live.approach.entry", proposed.id(), "Only the existing bounded native WALK worker may approach that ingress.");
		PhantomAssertions.assertEquals("entry", proposed.fromAnchorId(), "A proposal cannot claim its old distant lineage as departure.");
		PhantomAssertions.assertEquals("entry", proposed.toAnchorId(), "Arrival must be captured at the ingress before any subsequent paid segment.");
		PhantomAssertions.assertEquals(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, proposed.type(), "Admission cannot execute a gatekeeper.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeWithIngress(route, null, committed, live, "final", anchors, 1000, anchor -> !anchor.id().equals("disconnected")), "Ingress keeps the caller's existing distance and instance bound.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeWithIngress(null, null, committed, live, "final", anchors, 12000, _ -> false), "No connected affordable route must remain unavailable.");
		final var factualWithoutCanonical = PhantomVisibleFarmTravel.resumeWithIngress(null, "old", null, live, "final", anchors, 12000,
			anchor -> !anchor.id().equals("disconnected"));
		PhantomAssertions.assertEquals(proposed, factualWithoutCanonical, "A known factual departure passes no canonical fallback point in proposedStep; it must still admit the separately proven connected ingress WALK.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeWithIngress(null, "old", null, live, "final", anchors, 12000,
			_ -> false), "The actual factual/null-canonical caller composition cannot waive connected onward proof.");
		final var disconnectedFactual = PhantomVisibleFarmTravel.resumeWithIngress(null, "old", committed, live, "final", anchors, 12000,
			anchor -> !anchor.id().equals("disconnected"));
		PhantomAssertions.assertEquals(proposed, disconnectedFactual, "A factual but disconnected service anchor must admit the same bounded WALK to a separately proven connected ingress.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeWithIngress(null, "old", committed, live, "final", anchors, 12000,
			_ -> false), "A factual departure does not waive onward route, exclusion or resource proof for an ingress.");
		final var paid = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, "paid", "entry", "final", 1, leg("paid", "entry", "final", 500));
		PhantomAssertions.assertEquals(500L, PhantomNormalGatekeeperTravel.maximumFee(paid.gatekeeper(), 1, 85), "Approved managed fare zero cannot change the ordinary native catalog fee.");
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.itineraryAffordable(List.of(paid), 0, 85, 0, 0), "Approved managed Phantom travel does not require an ordinary player's fee.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeWithIngress(null, null, committed, live, "final", anchors, 12000,
			_ -> PhantomVisibleFarmTravel.itineraryAffordable(List.of(paid), 0, 85, 0, -1)), "Even zero-fare travel cannot admit an invalid resource snapshot.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeWithIngress(null, null, null, live, "final", anchors, 12000,
			_ -> { throw new AssertionError("Unknown prior lineage cannot borrow an ingress."); }), "The new proposal still requires the original canonical lineage proof.");
		final var policy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
		final var outsideStraightLeg = List.of(entry("segmented", 14024, 0));
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeWithIngress(null, null, committed, live, "final", outsideStraightLeg, policy.maximumLocalStraightDistance(), _ -> true), "No ingress in one straight-leg radius is not a physical no-path witness.");
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.resumeWithIngress(null, null, committed, live, "final", outsideStraightLeg, policy.maximumRouteDistance(), _ -> true) != null,
			"The existing segmented native WALK budget can approach an ingress beyond one straight leg; its unchanged route/phase limits still own the work.");
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> new PhantomVisibleFarmTravel.NativeWalkBudget(new PhantomNavigationPoint(0, 0, 0, 0), new PhantomNavigationPoint(policy.maximumRouteDistance(), 0, 1, 0), 1, 100, policy),
			"Canonical Z or standpoint projection beyond the actual route-distance bound remains rejected by the budget owner.");
		PhantomAssertions.assertEquals(original, PhantomVisibleFarmTravel.resumeWithIngress(route, "old", null, live, "final", anchors, 12000,
			_ -> { throw new AssertionError("An existing factual itinerary cannot query alternative ingress."); }), "Existing factual departure retains its original itinerary.");
	}
	private static void nativeLanding()
	{
		final var floor = new PhantomNavigationPoint(82971, 53207, -1488, 0);
		final var landing = new PhantomNavigationPoint(floor.x(), floor.y(), floor.z() + 5, 0);
		final java.util.function.IntSupplier unused = () -> { throw new AssertionError("An ordinary arrival cannot require Geo lookup."); };
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.atWalkDestination(floor, floor, 0, unused), "Exact canonical arrival remains valid.");
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.atWalkDestination(landing, floor, 0, floor::z), "A proven native landing on the exact canonical Geo floor needs no zero-XY move.");
		PhantomAssertions.assertTrue(!PhantomVisibleFarmTravel.atWalkDestination(landing, floor, 0, () -> floor.z() - 64), "The same XY and five-unit offset cannot stand for another Geo floor.");
		for (var rejected : List.of(new PhantomNavigationPoint(floor.x() + 1, floor.y(), landing.z(), 0), new PhantomNavigationPoint(floor.x(), floor.y(), landing.z(), 1), new PhantomNavigationPoint(floor.x(), floor.y(), floor.z() - 5, 0), new PhantomNavigationPoint(floor.x(), floor.y(), floor.z() + 6, 0)))
		{
			PhantomAssertions.assertTrue(!PhantomVisibleFarmTravel.atWalkDestination(rejected, floor, 0, unused), "The exception cannot widen XY, instance, or Z acceptance.");
		}
		PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.atWalkDestination(new PhantomNavigationPoint(floor.x() + 10, floor.y(), floor.z() + 100, 0), floor, 10, unused), "Existing nonzero-radius arrival keeps its prior bounds.");
		PhantomAssertions.assertTrue(!PhantomVisibleFarmTravel.atWalkDestination(new PhantomNavigationPoint(floor.x() + 10, floor.y(), floor.z() + 101, 0), floor, 10, unused), "Existing nonzero-radius Z rejection remains intact.");
	}
	private static void nativeReturn()
	{
		final var current = new PhantomBackgroundState.Position(0, 10_000, 10_000, 0, 0, "current");
		final var next = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "next", "current", "final", 1, null);
		final var route = List.of(next);
		for (int distance : List.of(0, 4096, 4097, 100_000))
		{
			final var live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(current.x() + distance, current.y(), current.z(), 0);
			final var step = PhantomVisibleFarmTravel.resumeStep(route, null, current, live, "final");
			if (distance > 4096)
			{
				PhantomAssertions.assertEquals(null, step, "An intended lineage outside the existing local-return bound cannot admit native travel.");
				continue;
			}
			PhantomAssertions.assertTrue(step != null, "An off-anchor native actor needs a bounded return proposal before its original itinerary can resume.");
			PhantomAssertions.assertEquals("current", step.fromAnchorId(), "A return preserves the committed lineage.");
			PhantomAssertions.assertEquals("current", step.toAnchorId(), "A return cannot claim the distant final goal or skip its factual checkpoint.");
			PhantomAssertions.assertTrue(step.id().startsWith("live.approach."), "The existing live approach worker owns the return.");
			PhantomAssertions.assertEquals(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, step.type(), "The proposal cannot itself perform or charge a gatekeeper transition.");
		}
		final var live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(10_100, 10_000, 0, 0);
		final var otherInstance = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(live.x(), live.y(), live.z(), 1);
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(route, null, current, otherInstance, "final"), "A local return cannot cross instances.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(route, null, null, live, "final"), "A return requires a proven canonical committed point.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(List.of(), null, current, live, "final"), "A return cannot hide a missing subsequent itinerary.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.resumeStep(route, "foreign", current, live, "final"), "An actual but disconnected departure cannot be replaced with lineage proximity.");
		PhantomAssertions.assertEquals(next, PhantomVisibleFarmTravel.resumeStep(route, "current", current, live, "final"), "An already factual departure retains its original first itinerary leg.");
	}
	private static void localReturn()
	{
		final var before = new PhantomBackgroundState.Position(0, 115080, -178168, -880, 0, "current");
		final var current = new PhantomBackgroundState.Position(0, 116056, -175091, -1016, 0, "current");
		final var destination = new PhantomBackgroundState.Position(0, -33539, 137701, -3480, 0, "final");
		final var clock = new PhantomBackgroundState.Clock(123, 0, 7);
		final var policy = new PhantomBackgroundSimulationPolicy(2, 20, 1, 1, true, false, true, true, false, 1, 1, 0.6, 1.5, 2, 2.5, 3, 0, 0, 3.5, 3.5, "0".repeat(64), 125.35, 60_000, 0);
		final java.util.function.Predicate<PhantomBackgroundState.Position> dry = target -> Math.hypot((long) target.x() - before.x(), (long) target.y() - before.y()) <= 4096;
		for (long budget : List.of(10_000L, 25_753L, 60_000L))
		{
			final var result = L2jPhantomBackgroundAuthority.localReturn(before, clock, current, destination, budget, policy, dry);
			PhantomAssertions.assertEquals(budget < 25_753 ? PhantomBackgroundAuthority.TravelAdvance.Status.PARTIAL : PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED, result.status(), "Off-anchor TRAVEL must first return to its current lineage, rather than reject a distant final goal.");
			PhantomAssertions.assertEquals(budget < 25_753 ? before : current, result.position(), "A partial return cannot award a new position; completion retains the current anchor, never the final goal.");
			PhantomAssertions.assertEquals(budget < 25_753 ? 25_753L - budget : 0L, result.clock().residualTravelMillis(), "Elapsed scalar travel is charged once with the unchanged residual budget.");
			PhantomAssertions.assertEquals(123L, result.clock().rngState(), "Travel cannot award a new RNG outcome.");
			PhantomAssertions.assertEquals(7L, result.clock().residualEncounterMillis(), "Travel cannot consume or create encounter time.");
			PhantomAssertions.assertEquals(0L, result.feeAdena(), "A dry local return cannot charge or grant a gatekeeper fee.");
		}
		final var blocked = L2jPhantomBackgroundAuthority.localReturn(before, clock, current, destination, 60_000, policy, _ -> false);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.NO_ROUTE, blocked.status(), "A rejected native dry path remains fenced.");
		PhantomAssertions.assertEquals(before, blocked.position(), "Rejected dry proof cannot change position.");
		PhantomAssertions.assertEquals(clock, blocked.clock(), "Rejected dry proof cannot consume time.");
		final var partial = L2jPhantomBackgroundAuthority.localReturn(before, clock, current, destination, 10_000, policy, dry);
		final var resumed = L2jPhantomBackgroundAuthority.localReturn(before, partial.clock(), current, destination, 15_753, policy, dry);
		PhantomAssertions.assertEquals(PhantomBackgroundAuthority.TravelAdvance.Status.ARRIVED, resumed.status(), "A later interval consumes only the remaining travel time.");
		PhantomAssertions.assertEquals(current, resumed.position(), "Residual completion retains the intermediate lineage.");
		PhantomAssertions.assertEquals(0L, resumed.clock().residualTravelMillis(), "The completed return cannot leave reusable travel credit.");
		final var same = L2jPhantomBackgroundAuthority.localReturn(before, clock, current, current, 60_000, policy, dry);
		PhantomAssertions.assertEquals(current, same.position(), "The existing same-final-anchor local return remains supported.");
	}
	private static void itinerary()
	{
		final var first = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "walk.ingress", "start", "gk.a", 1, null);
		final var second = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, "gk.a-b", "gk.a", "gk.b", 1, leg("a-b", "gk.a", "gk.b", 32_000));
		final var third = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER, "gk.b-c", "gk.b", "gk.c", 1, leg("b-c", "gk.b", "gk.c", 610));
		final var last = new PhantomNormalGatekeeperTravel.Step(PhantomNormalGatekeeperTravel.Type.TOPOLOGY_BACKGROUND, "walk.egress", "gk.c", "farm", 1, null);
		final var route = List.of(first, second, third, last);
		PhantomAssertions.assertEquals(first, PhantomVisibleFarmTravel.itineraryStep(route, "start", "farm"), "A composite goal must start with its existing first physical segment, not one global approach.");
		PhantomAssertions.assertEquals(second, PhantomVisibleFarmTravel.itineraryStep(route.subList(1, 4), "gk.a", "farm"), "After a successful intermediate checkpoint the committed anchor advances the same route.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.itineraryStep(route.subList(1, 4), "start", "farm"), "An uncheckpointed departure cannot skip the original first segment.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.itineraryStep(List.of(first, third, last), "start", "farm"), "A disconnected itinerary is not an admission proposal.");
		PhantomAssertions.assertEquals(null, PhantomVisibleFarmTravel.itineraryStep(route, "start", "foreign"), "The physical itinerary must retain the exact final goal anchor.");
		for (long budget : List.of(0L, 32_000L, 32_609L, 32_610L, 50_000L))
		{
			PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.itineraryAffordable(route, 1, 50, 0, budget), "The approved managed zero fare applies to every native leg without a wallet grant.");
		}
		PhantomAssertions.assertEquals(32_610L, PhantomNormalGatekeeperTravel.maximumFee(second.gatekeeper(), 1, 50) + PhantomNormalGatekeeperTravel.maximumFee(third.gatekeeper(), 1, 50), "The managed tariff cannot rewrite the ordinary player's full native fees.");
		final int previous = org.l2jmobius.gameserver.config.PlayerConfig.MAX_FREE_TELEPORT_LEVEL;
		try
		{
			org.l2jmobius.gameserver.config.PlayerConfig.MAX_FREE_TELEPORT_LEVEL = 40;
			PhantomAssertions.assertTrue(PhantomVisibleFarmTravel.itineraryAffordable(route, 0, 20, 0, 0), "Ordinary native free teleport eligibility applies to every leg without a wallet grant.");
		}
		finally { org.l2jmobius.gameserver.config.PlayerConfig.MAX_FREE_TELEPORT_LEVEL = previous; }
	}
	private static PhantomNormalGatekeeperTravel.Leg leg(String id, String from, String to, long fee)
	{
		return new PhantomNormalGatekeeperTravel.Leg(id, from, to, "source", "transition", "destination", 30540, "NORMAL", 0, 0, 0, 0, 0, 0, 0, 57, fee, 1, "fixture", List.of());
	}
	private static void canonicalFarm()
	{
		final var canonical = new PhantomNavigationPoint(-33539, 137701, -3480, 0);
		for (long profile : List.of(1L, 10L, 99L))
		{
			final var candidates = PhantomVisibleFarmTravel.farmingCandidates(profile, canonical);
			PhantomAssertions.assertEquals(canonical, candidates.getFirst(), "A usable radius-one canonical farm point must be considered before offsets outside its area.");
			PhantomAssertions.assertEquals(25, candidates.size(), "Canonical proposal retains the existing bounded offset alternatives.");
		}
	}
}

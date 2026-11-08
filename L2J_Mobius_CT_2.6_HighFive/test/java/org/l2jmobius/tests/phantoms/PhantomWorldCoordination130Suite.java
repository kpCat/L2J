/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.ConcurrentModificationException;
import java.util.stream.LongStream;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;

/** Zero-Player contracts; real SQL and native acceptance are separate gates. */
public final class PhantomWorldCoordination130Suite implements PhantomTestSuite
{
	@Override public String id() { return "world-coordination130"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		PhantomExecutionHandoff130Tests.register(registry);
		registry.add("activity-without-common-source-waits-without-retrying-failed-route", _ ->
		{
			for (int size : List.of(2, 9))
			{
				final var memory = new MemoryStore(); final var coordinator = new PhantomLogicalPartyCoordinator(memory);
				var row = coordinator.agree(group(1, size)).orElseThrow();
				row = coordinator.transition(row, PhantomLogicalParty.Phase.BACKGROUND, 0, 1000, "REPLAN_ROUTE");
				final var calls = new java.util.concurrent.atomic.AtomicInteger();
				final var unavailable = new PhantomLogicalPartyCoordinator.ActivityRevision()
				{
					@Override public List<PhantomLogicalParty.ActivityRoot> plan(PhantomLogicalParty group, long now) { calls.incrementAndGet(); return List.of(); }
					@Override public void publish(PhantomLogicalParty.ActivityRoot plan) { throw new AssertionError("WAIT published a root."); }
				};
				final var waiting = coordinator.recoverActivity(row, 1000, unavailable);
				PhantomAssertions.assertTrue(waiting.group().waitReason().equals("REPLAN_NO_COMMON_SOURCE") && waiting.group().nextDueMillis() == 301000, "No-source activity lacks its finite nextDue/required change.");
				PhantomAssertions.assertEquals(waiting, coordinator.recoverActivity(waiting, 1001, unavailable), "WAIT immediately repeated the same planning failure.");
				PhantomAssertions.assertEquals(1, calls.get(), "No-source WAIT spun on its old source.");
				PhantomAssertions.assertFalse(waiting.group().assemblyPending(1, waiting.group().members().getFirst().goalId(), 0, 301000), "Failed objective regained assembly rights.");
				PhantomAssertions.assertFalse(coordinator.recoverActivity(waiting, waiting.group().expiresAtMillis(), unavailable).group().active(), "No-source WAIT retained identity claims forever after released-owner expiry.");
			}
		});
		registry.add("activity-revision-preserves-two-and-nine-member-cursor-and-fences-stale-roots", _ ->
		{
			for (int size : List.of(2, 9))
			{
				final var initial = group(1, size);
				final var pending = new PhantomLogicalParty(initial.groupId(), initial.generation(), initial.leaderProfileId(), initial.members(), initial.objective(), initial.anchorId(), PhantomLogicalParty.Phase.BACKGROUND, 123, 1000, "ACTIVITY_PENDING");
				final var roots = initial.members().stream().map(member -> new PhantomLogicalParty.Member(member.profileId(), member.role(), member.goalId(), member.goalRevision() + 1)).toList();
				final var next = pending.activityReplacement(roots, new PhantomDomainRef("background.farm", "20002@farm.b"), 2000);
				PhantomAssertions.assertEquals(pending.generation() + 1, next.generation(), "Activity revision did not advance the logical generation.");
				PhantomAssertions.assertEquals(123L, next.backgroundCursor(), "Activity revision replayed confirmed Background time.");
				PhantomAssertions.assertEquals(pending.groupId(), next.groupId(), "Activity revision replaced group identity.");
				PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> pending.activityReplacement(initial.members(), next.objective(), 2000), "Stale roots authorized a new activity.");
				PhantomAssertions.assertThrows(IllegalStateException.class, () -> initial.activityReplacement(roots, next.objective(), 2000), "Activity bypassed the durable pending fence.");
				final var held = pending.transition(PhantomLogicalParty.Phase.DRAINING, 123, 2000, "REPLAN_ROUTE").transition(PhantomLogicalParty.Phase.DRAINING, 123, 3000, "WAIT_STORE");
				PhantomAssertions.assertTrue(held.activityReplanRequired(), "STORE wait erased the terminal activity failure.");
			}
		});
		registry.add("activity-proposal-uses-ordinary-planner-and-preserves-unknown-physical-route", _ ->
		{
			final var planner = org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.planner(true);
			final var state = org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.state();
			final var root = org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.goal();
			final var source = new PhantomDomainRef("background.farm", "102@farm.far");
			final var proposal = planner.proposeLogicalActivity(74, state, root, 130, 1, source);
			PhantomAssertions.assertTrue(proposal.ready() && proposal.goal().validSources().contains(source), "Ordinary planner could not propose its connected alternative.");
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.planner(false).proposeLogicalActivity(74, state, root, 130, 1, source).ready(), "Activity revision invented a disconnected itinerary.");
		});
		registry.add("route-budget-proof-distinguishes-geometry-from-search-and-unknown", _ ->
		{
			final var policy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
			final var origin = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(0, 0, 0, 0);
			final var destination = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(100, 0, 0, 0);
			final var points = java.util.stream.IntStream.rangeClosed(1, 66).mapToObj(i -> new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(i, 0, 0, 0)).toList();
			final var rejected = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeRouteContract.normalize(origin, destination, points, policy, 100000, (_, _) -> true,
				() -> org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.PATH_FOUND);
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind.RAW_WAYPOINTS, rejected.proof().kind(), "Normalizer lost the actual waypoint limit behind a generic status.");
			PhantomAssertions.assertEquals(66, rejected.proof().waypointsUsed(), "Measured raw waypoint count was lost.");
			final var blocked = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeRouteContract.normalize(origin, destination, List.of(destination), policy, 100000, (_, _) -> false,
				() -> org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.PATH_FOUND);
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind.SEGMENT_BLOCKED, blocked.proof().kind(), "A proven segment rejection was confused with search exhaustion.");
			PhantomAssertions.assertEquals(destination, blocked.proof().failedPoint(), "The exact rejected segment endpoint was lost.");
			final var distant = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(10000, 0, 0, 0);
			final var request = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(1, origin, distant, 0, 1000, 100000);
			final var oracle = new org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Oracle()
			{
				@Override public org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint project(int x, int y, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint surface)
				{ return new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, surface.z(), surface.instanceId()); }
				@Override public boolean direct(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to)
				{ return !to.equals(distant) && from.distanceTo(to) <= 1500; }
				@Override public List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> path(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint from, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint to) { return List.of(); }
			};
			final var limited = new org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner(oracle, () -> 1).plan(request, new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), policy);
			PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.SEARCH_LIMIT, limited.status(), "Fixture did not exercise the real bounded search.");
			final var search = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.BudgetProof.context(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.LimitKind.SEARCH_LIMIT, "CORRIDOR", request, policy).search(limited);
			PhantomAssertions.assertTrue(search.nodes() == limited.nodes() && search.queries() == limited.queries() && search.queries() > 0 && search.nodes() <= search.nodeLimit() && search.edges() <= search.edgeLimit(), "Real search counters/limits were reconstructed or dropped.");
			PhantomAssertions.assertEquals(-1, org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.BudgetProof.unknown().queries(), "Historical unavailable queries were fabricated as zero.");
		});
		PhantomPartySuite.registerCoordination130(registry);
		org.l2jmobius.gameserver.phantoms.PhantomPopulationPerformanceSuite.registerCoordination130(registry);
        registry.add("13-native-fixture-selects-lawful-birth-and-active-calendar-without-server", context ->
        {
            final var zone = java.time.ZoneId.of("UTC"); final var now = java.time.Instant.parse("2026-11-06T22:00:00Z");
            final var catalog = org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog.load(context.moduleRoot().resolve("dist/game/data/phantoms/population/high-five-population-v3.xml"), zone);
            for (int ordinal = 1; ordinal <= 3; ordinal++)
            {
                final long seed = PhantomCoordination130Native.birthSeed(catalog, 1000 + ordinal, ordinal, ordinal == 3 ? 10 : 0, zone, now);
                final var schedule = org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore.scheduleForShell(catalog, 1000 + ordinal, seed, null);
                final var evaluated = catalog.evaluate(schedule.template().id(), now, zone, schedule.phaseMinutes());
                PhantomAssertions.assertTrue(evaluated.state().requiresMaterialization() && evaluated.nextBoundary().isAfter(now.plusSeconds(1800)), "Fixture bypasses the real calendar.");
            }
            PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomCoordination130Native.birthSeed(catalog, 1000, 0, 0, zone, now), "Invalid creation ordinal reached native bootstrap.");
        });
		registry.add("01-logical-two-three-nine-survive-zero-native-recreation", _ ->
		{
			for (int size : List.of(2, 3, 9))
			{
				final var store = new MemoryStore();
				final var group = group(1, size);
				final var coordinator = new PhantomLogicalPartyCoordinator(store);
				PhantomAssertions.assertTrue(coordinator.agree(group).isPresent(), "Durable profiles could not agree without Player identities.");
				final var restarted = new PhantomLogicalPartyCoordinator(store);
				final var restored = restarted.recoverAfter(0, 16).getFirst().group();
				PhantomAssertions.assertEquals(group, restored, "Repository recreation changed logical identity/roster/goal/cursor.");
				PhantomAssertions.assertEquals(group, PhantomLogicalParty.decode(group.encode()), "Logical codec changed the aggregate.");
				for (var member : group.members()) { PhantomAssertions.assertEquals(group.groupId(), restarted.groupFor(member.profileId()).orElseThrow().group().groupId(), "Claim is not backed by the authoritative aggregate."); }
			}
		});
		registry.add("14-cold-expiry-drains-only-absent-identities-before-atomic-release", _ ->
		{
			for (var phase : List.of(PhantomLogicalParty.Phase.WAKE_REQUESTED, PhantomLogicalParty.Phase.ASSEMBLING, PhantomLogicalParty.Phase.NATIVE_ACTIVE, PhantomLogicalParty.Phase.DRAINING))
			{
				final var store = new MemoryStore(); final var owner = new PhantomLogicalPartyCoordinator(store); final var agreed = group(1, 3);
				var row = owner.agree(agreed).orElseThrow();
				row = owner.transition(row, PhantomLogicalParty.Phase.WAKE_REQUESTED, 0, 1000, "");
				if (phase != PhantomLogicalParty.Phase.WAKE_REQUESTED) { row = owner.transition(row, PhantomLogicalParty.Phase.ASSEMBLING, 0, 1000, ""); }
				if (phase == PhantomLogicalParty.Phase.NATIVE_ACTIVE) { row = owner.transition(row, phase, 0, 1000, ""); }
				if (phase == PhantomLogicalParty.Phase.DRAINING) { row = owner.transition(row, phase, 0, 1000, "WAIT_STORE"); }
				final var expected = row;
				PhantomAssertions.assertThrows(IllegalStateException.class, () -> PhantomCoordination130Native.expireAbsent(owner, expected, agreed.expiresAtMillis(), () -> false), "Cold expiry ignored a live identity/lease.");
				PhantomAssertions.assertEquals(expected, owner.groupFor(1).orElseThrow(), "Rejected cold expiry changed a generation.");
				PhantomCoordination130Native.expireAbsent(owner, expected, agreed.expiresAtMillis(), () -> true);
				for (var member : agreed.members()) { PhantomAssertions.assertTrue(owner.groupFor(member.profileId()).isEmpty(), "Expired cold phase retained its logical claim."); }
				PhantomAssertions.assertEquals(agreed.expiresAtMillis(), store.groups.get(1L).group().expiresAtMillis(), "Cold recovery extended expired assembly lifetime.");
			}
		});
		registry.add("02-conflicting-and-partially-failed-formation-never-steal-claims", _ ->
		{
			final var store = new MemoryStore(); final var coordinator = new PhantomLogicalPartyCoordinator(store);
			final var first = group(1, 3);
			PhantomAssertions.assertTrue(coordinator.agree(first).isPresent(), "Initial agreement failed.");
			PhantomAssertions.assertTrue(coordinator.agree(first).isPresent(), "Exact replay must be idempotent.");
			final var conflicting = new PhantomLogicalParty("B".repeat(64), 1, 4, List.of(member(4), member(2)), first.objective(), first.anchorId(), PhantomLogicalParty.Phase.AGREED, 0, 1000, "");
			PhantomAssertions.assertTrue(coordinator.agree(conflicting).isEmpty(), "Second group overwrote an existing identity claim.");
			PhantomAssertions.assertEquals(first.groupId(), coordinator.groupFor(2).orElseThrow().group().groupId(), "Conflicting offer damaged the first group.");
			final var failed = new MemoryStore(); failed.failAtMember = 2;
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> new PhantomLogicalPartyCoordinator(failed).agree(first), "Injected commit failure was swallowed.");
			PhantomAssertions.assertTrue(failed.groups.isEmpty() && failed.claims.isEmpty(), "Failed bounded commit leaked partial reservations.");
		});
		registry.add("03-stale-transition-cannot-rewind-cursor-or-newer-generation", _ ->
		{
			final var store = new MemoryStore(); final var coordinator = new PhantomLogicalPartyCoordinator(store);
			final var before = coordinator.agree(group(1, 3)).orElseThrow();
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> coordinator.transition(before, PhantomLogicalParty.Phase.BACKGROUND, 12, 2000, "WAIT_CALENDAR"), "A phase-only caller fabricated group time without receipts.");
			final var next = coordinator.transition(before, PhantomLogicalParty.Phase.BACKGROUND, 0, 2000, "WAIT_CALENDAR");
			PhantomAssertions.assertEquals(0L, next.group().backgroundCursor(), "Background cursor changed without receipts.");
			PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> coordinator.transition(before, PhantomLogicalParty.Phase.WAKE_REQUESTED, 0, 1001, ""), "A stale aggregate overwrote a committed transition.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> coordinator.transition(next, PhantomLogicalParty.Phase.WAKE_REQUESTED, -1, 3000, ""), "A background cursor moved backwards.");
		});
		registry.add("04-paged-discovery-reaches-fifth-source-and-sixteenth-peer", _ ->
		{
			final var store = new MemoryStore(); final var coordinator = new PhantomLogicalPartyCoordinator(store);
			final var sources = java.util.stream.IntStream.rangeClosed(1, 5).mapToObj(i -> new PhantomDomainRef("background.farm", "20101@farm." + i)).toList();
			final var page = LongStream.rangeClosed(1, 16).mapToObj(id -> new PhantomLogicalPartyCoordinator.Candidate(member(id), 76, sources, source -> id == 1 && source.equals(sources.get(4)), () -> true)).toList();
			PhantomAssertions.assertTrue(coordinator.discover(page, (_, _) -> true, 1000).isEmpty(), "Unreachable peers were admitted.");
			final var last = new PhantomLogicalPartyCoordinator.Candidate(new PhantomLogicalParty.Member(17, "support", 1017, 0), 76, sources, sources.get(4)::equals, () -> true);
			var agreement = coordinator.discover(List.of(last), (_, _) -> true, 2000);
			for (int pulse = 0; pulse < 5 && agreement.isEmpty(); pulse++) { agreement = coordinator.discover(List.of(), (_, _) -> true, 3000 + pulse); }
			final var formed = agreement.orElseThrow().group();
			PhantomAssertions.assertEquals(sources.get(4), formed.objective(), "The fifth usable source was truncated.");
			PhantomAssertions.assertEquals(List.of(1L, 17L), formed.members().stream().map(PhantomLogicalParty.Member::profileId).toList(), "The sixteenth peer was truncated or a healer required Solo damage.");
		});
		registry.add("07-absent-runtime-candidate-revalidates-durable-root-before-commit", _ ->
		{
			final var store = new MemoryStore(); final var coordinator = new PhantomLogicalPartyCoordinator(store);
			final var valid = new java.util.concurrent.atomic.AtomicBoolean(true);
			final var source = new PhantomDomainRef("background.farm", "20101@farm.fixture");
			final var old = new PhantomLogicalPartyCoordinator.Candidate(member(1), 76, List.of(source), _ -> true, () -> true, valid::get);
			PhantomAssertions.assertTrue(coordinator.discover(List.of(old), (_, _) -> true, 1000).isEmpty(), "One profile formed a group.");
			valid.set(false); // Historical catchup changed the durable goal with no Decision runtime slot.
			final var peer = new PhantomLogicalPartyCoordinator.Candidate(member(2), 76, List.of(source), _ -> true, () -> true);
			PhantomAssertions.assertTrue(coordinator.discover(List.of(peer), (_, _) -> true, 2000).isEmpty(), "A stale cached root was committed because its runtime slot was absent.");
			PhantomAssertions.assertTrue(store.claims.isEmpty() && store.groups.isEmpty(), "Durable revalidation happened after claims were written.");
		});
		registry.add("09-disband-release-recreate-fences-stale-generation", _ ->
		{
			final var store = new MemoryStore(); final var owner = new PhantomLogicalPartyCoordinator(store);
			final var original = owner.agree(group(1, 3)).orElseThrow();
			final var ended = owner.disband(original, 2000, "DISBANDED");
			store.groups.put(1L, original); // A recovery page captured before the terminal CAS may arrive late.
			owner.recoverAfter(0, 16);
			PhantomAssertions.assertTrue(owner.knownGroupFor(1).isEmpty(), "Late recovery resurrected a released cached generation.");
			store.groups.put(1L, ended);
			for (var member : original.group().members())
			{ PhantomAssertions.assertTrue(owner.groupFor(member.profileId()).isEmpty() && !store.claims.get(member.profileId()).active(), "Disband retained an active claim."); }
			final var replacement = new PhantomLogicalParty("B".repeat(64), 2, 1, original.group().members(), original.group().objective(), original.group().anchorId(), PhantomLogicalParty.Phase.AGREED, 0, 3000, "");
			final var recreated = new PhantomLogicalPartyCoordinator(store).agree(replacement).orElseThrow();
			PhantomAssertions.assertTrue(recreated.rowVersion() > ended.rowVersion(), "Recreation reset the aggregate CAS version.");
			PhantomAssertions.assertThrows(ConcurrentModificationException.class, () -> owner.disband(original, 4000, "STALE"), "An old generation released new claims.");
			PhantomAssertions.assertEquals(recreated, owner.groupFor(2).orElseThrow(), "Stale release stole the recreated membership.");
		});
		registry.add("10-leave-replace-expire-restart-releases-only-exact-claims", _ ->
		{
			final var store = new MemoryStore(); final var owner = new PhantomLogicalPartyCoordinator(store);
			final var original = owner.agree(group(1, 3)).orElseThrow();
			final var left = owner.leave(original, 3, 2000);
			PhantomAssertions.assertTrue(left.group().members().size() == 2 && owner.groupFor(3).isEmpty(), "Leave did not release the departing identity.");
			final var changed = owner.replace(left, List.of(member(1), member(4)), 3000);
			PhantomAssertions.assertTrue(owner.groupFor(2).isEmpty() && owner.groupFor(4).orElseThrow().group().generation() == changed.group().generation(), "Replacement did not atomically exchange claims.");
			final var restarted = new PhantomLogicalPartyCoordinator(store);
			PhantomAssertions.assertTrue(restarted.expire(changed, changed.group().expiresAtMillis() - 1).isEmpty(), "Agreement expired before its durable deadline.");
			restarted.expire(changed, changed.group().expiresAtMillis()).orElseThrow();
			PhantomAssertions.assertTrue(restarted.groupFor(1).isEmpty() && restarted.groupFor(4).isEmpty(), "Restart/expiration stranded claims.");
			final var nativeRow = owner.agree(new PhantomLogicalParty("C".repeat(64), 1, 5, List.of(member(5), member(6)), original.group().objective(), original.group().anchorId(), PhantomLogicalParty.Phase.AGREED, 0, 4000, "")).orElseThrow();
			final var waking = owner.transition(nativeRow, PhantomLogicalParty.Phase.WAKE_REQUESTED, 0, 5000, "");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> owner.disband(waking, 6000, "UNSAFE"), "Membership changed before the physical owner drained.");
		});
		registry.add("11-assembly-lifetime-is-root-bound-finite-and-yields-to-drain", _ ->
		{
			final var initial = group(1, 3);
			PhantomAssertions.assertTrue(initial.assemblyPending(1, 1001, 0, 1000), "Initial group could not retain the first member before rendezvous acceptance.");
			final var background = initial.transition(PhantomLogicalParty.Phase.BACKGROUND, 0, 2000, "WAIT_DEMAND");
			PhantomAssertions.assertTrue(background.assemblyPending(1, 1001, 0, 2000), "The first lawful member retired between Background recovery and the next Party pulse.");
			PhantomAssertions.assertFalse(background.assemblyPending(1, 1001, 0, 1999), "Background cooldown inherited assembly lifetime.");
			final var waking = initial.transition(PhantomLogicalParty.Phase.WAKE_REQUESTED, 0, 2000, "WAIT_ADMISSION");
			final var assembling = waking.transition(PhantomLogicalParty.Phase.ASSEMBLING, 0, 3000, "WAIT_NATIVE_ARRIVAL");
			PhantomAssertions.assertTrue(assembling.assemblyPending(1, 1001, 1, 4000), "Same-root preparation revision lost assembly lifetime.");
			PhantomAssertions.assertFalse(assembling.assemblyPending(1, 9999, 0, 4000), "An unrelated Solo root inherited group lifetime.");
			PhantomAssertions.assertFalse(assembling.assemblyPending(1, 1001, 0, initial.expiresAtMillis()), "Repeated phase deadlines extended assembly forever.");
			final var draining = assembling.transition(PhantomLogicalParty.Phase.DRAINING, 0, 5000, "WAIT_STORE");
			PhantomAssertions.assertFalse(draining.assemblyPending(1, 1001, 0, 5000), "Assembly permission survived accepted drain.");
			final var expired = assembling.transition(PhantomLogicalParty.Phase.DRAINING, 0, initial.expiresAtMillis(), "EXPIRED_ASSEMBLY");
			final var storeWait = expired.transition(PhantomLogicalParty.Phase.DRAINING,0,initial.expiresAtMillis()+5000,"WAIT_STORE");
			PhantomAssertions.assertEquals(initial.expiresAtMillis(), storeWait.transition(PhantomLogicalParty.Phase.BACKGROUND,0,initial.expiresAtMillis()+5000,"WAIT_DEMAND").expiresAtMillis(), "Expired assembly renewed itself forever through retained STORE wait.");
		});
		registry.add("08-five-thousand-durable-discovery-stays-paged", context ->
		{
			final var store = new MemoryStore(); final var coordinator = new PhantomLogicalPartyCoordinator(store);
			final var source = new PhantomDomainRef("background.farm", "20101@farm.fixture");
			final long start = System.nanoTime(); int pages = 0;
			for (int pass = 0; pass < 3; pass++)
			{
				for (long after = 0; after < 5000; after += 16)
				{
					final var page = LongStream.rangeClosed(after + 1, Math.min(after + 16, 5000)).mapToObj(id -> new PhantomLogicalPartyCoordinator.Candidate(member(id), 76, List.of(source), _ -> true, () -> true)).toList();
					final int reads = store.claimReads;
					coordinator.discover(page, (_, _) -> true, 1000 + pages++);
					PhantomAssertions.assertTrue(store.claimReads - reads <= 25, "One page performed an unbounded profile SQL-style scan.");
				}
			}
			PhantomAssertions.assertEquals(5000, store.claims.size(), "Fair durable discovery permanently lost profiles behind an early page cap.");
			context.record("task130.durableProfiles", 5000); context.record("task130.discoveryPages", pages);
			context.record("task130.discoveryMillis", (System.nanoTime() - start) / 1_000_000);
		});
	}

	static PhantomLogicalParty.Member member(long id) { return new PhantomLogicalParty.Member(id, "damage", 1000 + id, 0); }
	static PhantomLogicalParty group(long leader, int size)
	{
		return new PhantomLogicalParty("A".repeat(64), 1, leader, LongStream.range(leader, leader + size).mapToObj(PhantomWorldCoordination130Suite::member).toList(),
			new PhantomDomainRef("background.farm", "20101@farm.fixture"), "farm.fixture", PhantomLogicalParty.Phase.AGREED, 0, 1000, "");
	}

	static final class MemoryStore implements PhantomPartyPersistencePort
	{
		final Map<Long, StoredLogicalGroup> groups = new HashMap<>();
		final Map<Long, StoredLogicalClaim> claims = new HashMap<>();
		int failAtMember;
		int claimReads;
		@Override public Optional<StoredPartyState> load(long id) { return Optional.empty(); }
		@Override public StoredPartyState save(long id, long expected, PartyState state) { throw new UnsupportedOperationException(); }
		@Override public List<StoredPartyState> loadManagedAfter(long after, int limit) { return List.of(); }
		@Override public Optional<StoredLogicalGroup> loadLogicalGroup(long leader) { return Optional.ofNullable(groups.get(leader)); }
		@Override public Optional<StoredLogicalClaim> loadLogicalClaim(long id) { claimReads++; return Optional.ofNullable(claims.get(id)); }
		@Override public List<StoredLogicalGroup> loadLogicalGroupsAfter(long after, int limit) { return groups.entrySet().stream().filter(e -> e.getKey() > after).sorted(Map.Entry.comparingByKey()).limit(limit).map(Map.Entry::getValue).toList(); }
		@Override public synchronized StoredLogicalGroup commitLogicalGroup(PhantomLogicalParty group, long expected, Map<Long, Long> versions)
		{
			final var old = groups.get(group.leaderProfileId());
			if ((old == null ? -1 : old.rowVersion()) != expected) { throw new ConcurrentModificationException(); }
			int examined = 0;
			for (var member : group.members())
			{
				final var claim = claims.get(member.profileId());
				if ((claim == null ? -1 : claim.rowVersion()) != versions.get(member.profileId())) { throw new ConcurrentModificationException(); }
				if (claim != null && claim.active()) { throw new ConcurrentModificationException(); }
				if (++examined == failAtMember) { throw new IllegalStateException("TEST130_ATOMIC_FAILURE"); }
			}
			final var saved = new StoredLogicalGroup(expected + 1, PhantomLogicalParty.decode(group.encode()));
			groups.put(group.leaderProfileId(), saved);
			for (var member : group.members()) { claims.put(member.profileId(), new StoredLogicalClaim(member.profileId(), group.leaderProfileId(), group.groupId(), group.generation(), versions.get(member.profileId()) + 1)); }
			return saved;
		}
		@Override public synchronized StoredLogicalGroup reviseLogicalGroup(StoredLogicalGroup expected, PhantomLogicalParty replacement)
		{
			if (!expected.equals(groups.get(expected.group().leaderProfileId()))) { throw new ConcurrentModificationException(); }
			final var pending = new HashMap<>(claims); final var ids = new java.util.TreeSet<Long>();
			expected.group().members().forEach(member -> ids.add(member.profileId())); replacement.members().forEach(member -> ids.add(member.profileId()));
			for (long id : ids)
			{
				final var before = claims.get(id);
				if (expected.group().contains(id) ? before == null || !before.active() || !before.groupId().equals(expected.group().groupId()) || before.generation() != expected.group().generation() : before != null && before.active()) { throw new ConcurrentModificationException(); }
				final boolean active = replacement.active() && replacement.contains(id);
				final var identity = active ? replacement : expected.group();
				pending.put(id, new StoredLogicalClaim(id, identity.leaderProfileId(), identity.groupId(), identity.generation(), before == null ? 0 : before.rowVersion() + 1, active));
			}
			final var saved = new StoredLogicalGroup(expected.rowVersion() + 1, PhantomLogicalParty.decode(replacement.encode()));
			claims.clear(); claims.putAll(pending); groups.put(replacement.leaderProfileId(), saved); return saved;
		}
		@Override public synchronized StoredLogicalGroup updateLogicalGroup(PhantomLogicalParty group, long expected)
		{
			final var old = groups.get(group.leaderProfileId());
			if (old == null || old.rowVersion() != expected) { throw new ConcurrentModificationException(); }
			final var saved = new StoredLogicalGroup(expected + 1, PhantomLogicalParty.decode(group.encode())); groups.put(group.leaderProfileId(), saved); return saved;
		}
	}
}

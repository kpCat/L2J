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
		registry.add("party-source-and-every-rendezvous-replan-ignore-solo-kill-only", _ -> org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.partyContract130());
		registry.add("party-common-source-is-tested-before-the-sixty-four-result-limit", _ -> org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.widePartySources130());
		registry.add("party-structural-walk-budget-skips-first-sixty-four-and-keeps-local-source", _ -> org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.boundedStructuralSources130());
		registry.add("party-argos-and-hot-springs-use-the-configured-java-itinerary", _ -> org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.configuredPartyItinerary130());
		registry.add("party-level-intersection-and-potential-roles-never-imply-native-readiness", _ ->
		{
			final var cases = List.of(List.of(76, 76, 72, 80), List.of(74, 78, 74, 78), List.of(72, 80, 76, 76), List.of(76, 81, 77, 80), List.of(85, 85, 81, 89));
			for (var row : cases)
			{
				final var band = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.forRoster(row.subList(0, 2)).orElseThrow();
				PhantomAssertions.assertTrue(band.contains(row.get(2)) && band.contains(row.get(3)) && !band.contains(row.get(2) - 1) && !band.contains(row.get(3) + 1), "Party band endpoints differ for " + row);
			}
			for (var invalid : List.of(List.<Integer>of(), List.of(76), List.of(0, 76), List.of(86, 76), List.of(70, 79), java.util.Collections.nCopies(10, 76)))
			{ PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.forRoster(invalid).isEmpty(), "Invalid roster acquired a FARM band: " + invalid); }
			for (int size : List.of(2, 3, 9)) { PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.forRoster(java.util.Collections.nCopies(size, 76)).isPresent(), "Legal roster size rejected."); }
			PhantomAssertions.assertEquals("frontline.guardian", org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.potentialRole(java.util.Set.of("combat.tank"), true, false), "Learned Aggression+weapon lost potential guardian.");
			PhantomAssertions.assertEquals("pending.native_role", org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.potentialRole(java.util.Set.of("combat.tank"), false, false), "Unarmed class label invented native readiness.");
			PhantomAssertions.assertEquals("utility.spoiler", org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.potentialRole(java.util.Set.of("profession.spoil", "profession.sweep"), true, false), "Spoiler was reduced to DD.");
			PhantomAssertions.assertFalse("utility.spoiler".equals(org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.potentialRole(java.util.Set.of("profession.spoil"), true, false)), "Missing Sweep was invented.");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.compatibleRole("damage", "frontline.guardian"), "Legacy guardian group cannot recover.");
			PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.compatibleRole("support", "support.healer"), "Legacy support group cannot recover.");
			PhantomAssertions.assertFalse(org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.compatibleRole("support", "damage.melee"), "Changed role silently kept its old obligation.");
		});
		registry.add("party-threat-reserve-is-six-seconds-without-projected-heal-hp", _ ->
		{
			PhantomAssertions.assertEquals(30000d, org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.threatExposureMillis(30000, true), "Observed victim lost its main threat horizon.");
			PhantomAssertions.assertEquals(6000d, org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.threatExposureMillis(30000, false), "A nonvictim received the entire encounter threat or free immunity.");
			PhantomAssertions.assertEquals(2000d, org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.threatExposureMillis(2000, false), "Reserve extended a shorter actual encounter.");
			PhantomAssertions.assertTrue(Double.isNaN(org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.threatExposureMillis(Double.POSITIVE_INFINITY, false)), "Unknown encounter acquired free protection.");
		});
		registry.add("party-native-target-budget-filters-wrong-sources-before-eight", _ ->
		{
			final var selected = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.farmTargets(java.util.stream.IntStream.rangeClosed(1, 17).boxed(), id -> id >= 9);
			PhantomAssertions.assertEquals(List.of(9, 10, 11, 12, 13, 14, 15, 16), selected, "Eight closer wrong sources hid the legal ninth World target or the bound was removed.");
		});
		registry.add("party-threat-union-reaches-a-distant-peer-and-keeps-overflow", _ ->
		{
			final var remote = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.farmThreats(List.of(0, 1000),
				actor -> java.util.stream.Stream.of(1500).filter(mob -> Math.abs(mob - actor) <= 1200), _ -> true);
			PhantomAssertions.assertEquals(List.of(1500), remote, "A mob attacking the distant healer was outside the leader's view.");
			final var overflow = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.farmThreats(List.of(1, 2),
				actor -> actor == 1 ? java.util.stream.Stream.of(0, 1, 2, 3, 4) : java.util.stream.Stream.of(1, 2, 3, 4, 5, 6), mob -> mob > 0);
			PhantomAssertions.assertEquals(List.of(1, 2, 3, 4, 5), overflow, "Duplicate neighborhoods hid the fifth threat or exceeded the bounded witness.");
		});
		registry.add("party-whole-native-resource-budget-has-no-fractional-or-unknown-credit", _ ->
		{
			PhantomAssertions.assertEquals(2d, org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.requiredActions(6000, 3000), "An exact two-shot encounter consumed an extra shot.");
			PhantomAssertions.assertEquals(3d, org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.requiredActions(6001, 3000), "Fractional shots/casts bypassed the native resource budget.");
			PhantomAssertions.assertEquals(Double.POSITIVE_INFINITY, org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.requiredActions(6000, 0), "Unknown native reuse acquired free ammunition/MP.");
		});
		registry.add("party-administrative-leader-is-not-a-damage-slot", _ ->
		{
			for (String role : List.of("frontline.guardian", "support.healer", "utility.spoiler", "damage.melee"))
			{
				for (int size : List.of(2, 3, 9))
				{
					final var coordinator = new PhantomLogicalPartyCoordinator(new MemoryStore());
					final var source = new PhantomDomainRef("background.farm", "20002@farm.a");
					final var candidates = LongStream.rangeClosed(1, size).mapToObj(id -> new PhantomLogicalPartyCoordinator.Candidate(
						new PhantomLogicalParty.Member(id, id == 1 ? role : "damage.melee", 1000 + id, 0), 76, List.of(source), _ -> true, () -> true)).toList();
					final var group = coordinator.discover(candidates, (_, _) -> true, 1000).orElseThrow().group();
					PhantomAssertions.assertEquals(1L, group.leaderProfileId(), "Administrative leader was selected by damage role.");
					PhantomAssertions.assertEquals(size, group.members().size(), "Flexible Party roster was truncated.");
					PhantomAssertions.assertEquals(role, group.members().getFirst().role(), "Role was silently rewritten.");
				}
			}
		});
		registry.add("native-acceptance-requires-heal-only-for-a-healer-roster", _ ->
		{
			PhantomAssertions.assertTrue(PhantomCoordination130Native.usefulGroupEvidence(true, true, false, false, false, false), "A lawful group without a healer required an invented Heal.");
			PhantomAssertions.assertFalse(PhantomCoordination130Native.usefulGroupEvidence(true, true, true, true, false, false), "Observed HP without native MP debit completed healer acceptance.");
			PhantomAssertions.assertFalse(PhantomCoordination130Native.usefulGroupEvidence(true, true, true, false, true, false), "MP spend without an actual Heal HP effect completed acceptance.");
			PhantomAssertions.assertTrue(PhantomCoordination130Native.usefulGroupEvidence(true, true, true, true, true, false), "Actual healer evidence did not complete the first useful vertical.");
			PhantomAssertions.assertFalse(PhantomCoordination130Native.usefulGroupEvidence(false, true, false, false, false, false), "A non-Party kill passed native group acceptance.");
			PhantomAssertions.assertFalse(PhantomCoordination130Native.usefulGroupEvidence(true, false, true, true, true, true), "An empty restart replaced a new useful action.");
			PhantomAssertions.assertTrue(PhantomCoordination130Native.usefulGroupEvidence(true, true, true, false, false, true), "A useful restart repeated the already proved first-vertical Heal gate.");
		});
		registry.add("preflight-recovery-and-read-only-scene-have-independent-finite-budgets", _ ->
		{
			PhantomCoordination130Native.checkPreflightBudget(java.util.concurrent.TimeUnit.SECONDS.toNanos(40), true);
			PhantomCoordination130Native.checkPreflightBudget(java.util.concurrent.TimeUnit.SECONDS.toNanos(29), false);
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> PhantomCoordination130Native.checkPreflightBudget(java.util.concurrent.TimeUnit.SECONDS.toNanos(60), true), "Recovery exceeded its finite budget.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> PhantomCoordination130Native.checkPreflightBudget(java.util.concurrent.TimeUnit.SECONDS.toNanos(30), false), "Scene exceeded its own budget.");
		});
		registry.add("authority-refresh-rejects-active-and-returning-native-intents", _ ->
		{
			final var state = org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.state();
			final var root = org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.goal();
			for (String type : List.of("supply.intent", "learning.intent", "career.intent"))
			{
				final var child = PhantomCoordination130Integration.authorityChild130(type, root, state);
				for (var status : org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.values())
				{
					final var row = new org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent(74, type, 1, 0, new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(child.withStatus(status)), java.time.Instant.EPOCH, java.time.Instant.EPOCH);
					final boolean pending = status == org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE || type.equals("learning.intent") && status == org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.FAILED || type.equals("supply.intent") && status == org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.COMPLETED;
					PhantomAssertions.assertEquals(pending, org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.nativeIntentPending(row), "Native return ownership lost for " + type + ":" + status);
				}
			}
		});
		registry.add("authority-preparation-excludes-foreign-completion-and-requires-idle-execution", _ ->
		{
			final var gate = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission(1);
			final var owner = org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.solo(1);
			gate.publish(1, 0, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.initial(owner));
			try (var active = gate.tryAcquireOwnedCompletion(1, owner))
			{ PhantomAssertions.assertEquals(null, gate.prepareAuthority(1, () -> true), "Active accepted work was ignored by authority preparation."); }
			PhantomAssertions.assertTrue(gate.prepareAuthority(1, () ->
			{
				PhantomAssertions.assertEquals(null, gate.tryAcquire(1, owner), "Preparation admitted normal work.");
				final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
				final var thread = new Thread(() ->
				{
					try (var foreign = gate.tryAcquireOwnedCompletion(1, owner)) { PhantomAssertions.assertEquals(null, foreign, "Foreign completion entered native authority refresh."); }
					catch (Throwable problem) { failure.set(problem); }
				});
				thread.start(); try { thread.join(2000); } catch (InterruptedException interrupted) { throw new IllegalStateException(interrupted); }
				PhantomAssertions.assertFalse(thread.isAlive(), "Admission monitor was held across preparation.");
				PhantomAssertions.assertEquals(null, failure.get(), "Foreign completion exclusion failed.");
				try (var own = gate.tryAcquireOwnedCompletion(1, owner)) { return own != null && own.valid(); }
			}), "The canonical nested lifecycle lost its completion permit.");
			try (var ordinary = gate.tryAcquire(1, owner)) { PhantomAssertions.assertTrue(ordinary != null, "Preparation did not release admission."); }
		});
		registry.add("authority-readiness-preserves-complete-history-and-fences-unfinished-generations", _ ->
		{
			final var state = org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.state();
			final var current = new org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner.PlanningGeneration(2, 3, state.hashes());
			for (var status : org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status.values())
			{
				if (status == org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status.FAILED_REPLAN_REQUIRED) { continue; }
				final var old = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes("old", state.hashes().topology(), state.hashes().progression(), state.hashes().commerce());
				final boolean complete = status == org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.Status.COMPLETE;
				final var history = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState(status, "a".repeat(64), 130, 100, 101,
					complete ? 101 : 100, 1, complete ? 1 : 0, 1, 1, 1, 130, 0, "b".repeat(64), state.MODEL_VERSION, old, "");
				PhantomAssertions.assertEquals(!complete, org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.authorityRefreshRequired(state.hashes(), history, current), "Historical authority was confused with current Background authority for " + status);
				PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.authorityRefreshRequired(old, history, current), "Stale current Background was admitted for " + status);
			}
		});
		registry.add("authority-preflight-emits-every-component-before-strict-refusal", _ ->
		{
			final var saved = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes("knowledge-saved", "topology-saved", "progression-saved", "commerce-saved");
			final var evidence = new java.util.ArrayList<String>();
			PhantomCoordination130Native.inspectAuthority(70, saved, saved, evidence::add);
			PhantomAssertions.assertEquals(4, evidence.size(), "Matching authority did not emit all four current components.");
			for (int changed = 0; changed < 4; changed++)
			{
				evidence.clear();
				final var current = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Hashes(
					changed == 0 ? "knowledge-current" : saved.knowledge(), changed == 1 ? "topology-current" : saved.topology(),
					changed == 2 ? "progression-current" : saved.progression(), changed == 3 ? "commerce-current" : saved.commerce());
				PhantomAssertions.assertThrows(IllegalStateException.class,
					() -> PhantomCoordination130Native.inspectAuthority(70, saved, current, evidence::add), "A stale component bypassed the authority fence.");
				PhantomAssertions.assertEquals(4, evidence.size(), "Authority refusal lost the component evidence.");
				final var components = List.of("knowledge", "topology", "progression", "commerce");
				for (int index = 0; index < components.size(); index++)
				{
					final String component = components.get(index);
					PhantomAssertions.assertEquals("NATIVE130_AUTHORITY profile=70 component=" + component + " saved=" + component + "-saved current="
						+ component + (index == changed ? "-current equal=false" : "-saved equal=true"), evidence.get(index), "Authority component evidence was ambiguous.");
				}
				evidence.clear();
				PhantomCoordination130Native.emitAuthority("NATIVE130_CATCHUP_AUTHORITY", 70, saved, current, evidence::add);
				PhantomAssertions.assertEquals(4, evidence.size(), "Catchup diagnostics stopped before collecting every component.");
				PhantomAssertions.assertTrue(evidence.get(changed).startsWith("NATIVE130_CATCHUP_AUTHORITY profile=70 component=" + components.get(changed))
					&& evidence.get(changed).endsWith("equal=false"), "Catchup diagnostics lost the actual stale component.");
			}
		});
		registry.add("read-only-preflight-and-scheduler-select-the-same-admitted-source-and-roster", _ ->
		{
			final var source = new PhantomDomainRef("background.farm", "20002@current.production.source");
			final var unknown = new PhantomDomainRef("background.farm", "20001@unknown.distant.route");
			final var candidates = group(1, 2).members().stream().map(member -> new PhantomLogicalPartyCoordinator.Candidate(member, 76, List.of(unknown, source), source::equals, () -> true)).toList();
			final var store = new MemoryStore();
			final var preview = new PhantomLogicalPartyCoordinator(store).previewDiscovery(candidates, (_, _) -> true, 1000);
			PhantomAssertions.assertEquals(source, preview.proposal().orElseThrow().objective(), "The first unknown route hid the later common source.");
			PhantomAssertions.assertTrue(store.groups.isEmpty() && store.claims.isEmpty(), "A read-only proposal or rejected source wrote durable state.");
			PhantomAssertions.assertTrue(new PhantomLogicalPartyCoordinator(store).previewDiscovery(candidates, (_, _) -> false, 1000).proposal().isEmpty(), "Preflight ignored actual consent.");
			final var scheduled = new PhantomLogicalPartyCoordinator(store).discover(candidates, (_, _) -> true, 1000).orElseThrow();
			PhantomAssertions.assertEquals(preview.proposal().orElseThrow(), scheduled.group(), "Production CAS chose a different source or roster.");
			PhantomAssertions.assertEquals(2, store.claims.size(), "The rejected source leaked a claim.");
			final var changedStore = new MemoryStore();
			final var changed = new PhantomLogicalPartyCoordinator(changedStore);
			final var reachable = new java.util.concurrent.atomic.AtomicBoolean(true);
			final var liveCandidates = candidates.stream().map(candidate -> new PhantomLogicalPartyCoordinator.Candidate(candidate.member(), candidate.level(),
				candidate.sources(), ref -> reachable.get() && candidate.reachable().test(ref), candidate.current(), candidate.durableCurrent())).toList();
			final var stale = changed.previewDiscovery(liveCandidates, (_, _) -> true, 1000);
			PhantomAssertions.assertTrue(stale.proposal().isPresent(), "Stale-route control did not select a valid initial proposal.");
			reachable.set(false);
			PhantomAssertions.assertTrue(changed.commitDiscovery(stale).isEmpty() && changedStore.claims.isEmpty() && changedStore.groups.isEmpty(), "A changed route committed durable state.");
		});
		registry.add("bounded-discovery-wait-advances-the-leader-cursor-without-reservations", _ ->
		{
			final var source = new PhantomDomainRef("background.farm", "20002@fifth.leader.source");
			final var candidates = group(1, 6).members().stream().map(member -> new PhantomLogicalPartyCoordinator.Candidate(member, member.profileId() < 5 ? 41 : 76,
				member.profileId() < 5 ? List.of() : List.of(source), _ -> member.profileId() >= 5, () -> true)).toList();
			final var store = new MemoryStore();
			final var owner = new PhantomLogicalPartyCoordinator(store);
			final var wait = owner.previewDiscovery(candidates, (_, _) -> true, 1000);
			PhantomAssertions.assertEquals(PhantomLogicalPartyCoordinator.DiscoveryStatus.WAIT_DISCOVERY_BUDGET, wait.status(), "Exhaustion lost its typed wait.");
			PhantomAssertions.assertTrue(wait.nextDueMillis() > 1000 && owner.commitDiscovery(wait).isEmpty() && store.claims.isEmpty() && store.groups.isEmpty(), "A bounded wait wrote or failed to schedule a future round.");
			final var later = owner.discover(List.of(), (_, _) -> true, wait.nextDueMillis()).orElseThrow();
			PhantomAssertions.assertEquals(source, later.group().objective(), "A bounded wait starved the next leader window.");
		});
		registry.add("cold-expired-assembly-waits-for-solo-recovery-before-release-and-fresh-discovery", _ ->
		{
			for (int size : List.of(2, 9))
			{
				final var store = new MemoryStore(); var owner = new PhantomLogicalPartyCoordinator(store);
				final var old = group(1, size); var row = owner.agree(old).orElseThrow();
				row = owner.transition(row, PhantomLogicalParty.Phase.WAKE_REQUESTED, 0, 1000, "");
				row = owner.transition(row, PhantomLogicalParty.Phase.ASSEMBLING, 0, 1000, "");
				final long now = old.expiresAtMillis(); final var wakes = new java.util.HashSet<Long>();
				final var ids = old.members().stream().map(PhantomLogicalParty.Member::profileId).toList();
				PhantomAssertions.assertTrue(PhantomCoordination130Native.currentAgreement(owner, ids, now).isEmpty(), "Native preflight treated expired assembly as its mandatory objective.");
				row = owner.retireExpired(row, now, () -> true, _ -> false, wakes::add);
				PhantomAssertions.assertEquals(PhantomLogicalParty.Phase.DRAINING, row.group().phase(), "Cold expiry bypassed REQUEST/YIELD/RECOVER.");
				PhantomAssertions.assertEquals(size, wakes.size(), "Cold expiry did not request all old executors.");
				final var pending = row; owner = new PhantomLogicalPartyCoordinator(store);
				row = owner.retireExpired(owner.groupFor(1).orElseThrow(), now + 5000, () -> false, _ -> true, wakes::add);
				PhantomAssertions.assertTrue(row.group().active(), "A retained identity/lease released claims after restart.");
				row = owner.retireExpired(row, now + 10000, () -> true, _ -> true, wakes::add);
				PhantomAssertions.assertFalse(row.group().active(), "Recovered absent group retained claims.");
				for (var member : old.members()) { PhantomAssertions.assertTrue(owner.groupFor(member.profileId()).isEmpty(), "Expired member remained claimed."); }
				final var freshSource = new PhantomDomainRef("background.farm", "20002@fresh.source");
				final var candidates = old.members().stream().map(member -> new PhantomLogicalPartyCoordinator.Candidate(member, 75, List.of(freshSource), _ -> true, () -> true)).toList();
				final var fresh = owner.discover(candidates, (_, _) -> true, now + 11000).orElseThrow();
				PhantomAssertions.assertEquals(freshSource, fresh.group().objective(), "Fresh discovery pinned the expired objective.");
				PhantomAssertions.assertEquals(fresh, PhantomCoordination130Native.currentAgreement(owner, ids, now + 11001).orElseThrow(), "Observer did not select the actual fresh agreement.");
				PhantomAssertions.assertTrue(!fresh.group().groupId().equals(old.groupId()) && fresh.group().generation() == old.generation() + 1, "Fresh agreement reused the expired generation.");
				final var staleOwner = owner;
				PhantomAssertions.assertThrows(java.util.ConcurrentModificationException.class, () -> staleOwner.retireExpired(pending, now + 12000, () -> true, _ -> true, wakes::add), "Stale cold expiry changed the fresh agreement.");
			}
		});
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
				owner.retireExpired(expected, agreed.expiresAtMillis(), () -> false, _ -> true, _ -> { });
				PhantomAssertions.assertEquals(expected, owner.groupFor(1).orElseThrow(), "Rejected cold expiry changed a generation.");
				owner.retireExpired(expected, agreed.expiresAtMillis(), () -> true, _ -> true, _ -> { });
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
			PhantomAssertions.assertEquals(java.util.Set.of(1L, 17L), formed.members().stream().map(PhantomLogicalParty.Member::profileId).collect(java.util.stream.Collectors.toSet()), "The sixteenth peer was truncated or a healer required Solo damage.");
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

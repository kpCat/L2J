/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;
import org.l2jmobius.gameserver.model.WorldObject;
import org.l2jmobius.gameserver.model.actor.Creature;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target;
import org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask;
import org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask.Disposition;
import org.l2jmobius.gameserver.model.actor.tasks.creature.MagicUseTask.TargetStatus;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundModel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.CombatFacts;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.ModelKind;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Vitals;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.DamageKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomNativeCombatCapability.Mechanics;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty;
import org.l2jmobius.gameserver.phantoms.party.PhantomLogicalPartyCoordinator;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyPersistencePort;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.PartyState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPresenceRegistry;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;

/** Zero-Player contracts; real SQL and native acceptance are separate gates. */
public final class PhantomWorldCoordination130Suite implements PhantomTestSuite
{
	@Override public String id() { return "world-coordination130"; }
	@Override public void register(PhantomTestRegistry registry)
	{
		PhantomExecutionHandoff130Tests.register(registry);
		registry.add("cold-party-owner-recovers-absent-baseline-before-normal-admission", context ->
		{
			final var root = context.moduleRoot().resolve("java/org/l2jmobius/gameserver/phantoms");
			final String system = java.nio.file.Files.readString(root.resolve("PhantomSystem.java"));
			final int startup = system.indexOf("private boolean recoverStartupParty(");
			final int ready = system.indexOf("_historicalBackgroundService.permitsNormalOperation(id)", startup);
			final int recovery = system.indexOf("_historicalBackgroundService.prepareColdStartupAuthority(id)", startup);
			final int grant = system.indexOf("_executionAdmission.current(id, grant.component().rowVersion(), grant.state())", ready);
			final int respawn = system.indexOf("_backgroundService.requestRecovery(id, root, scheduled", grant);
			final int live = system.indexOf("background.state() == PhantomBackgroundState.State.READY", respawn);
			final int proof = system.indexOf("inspect.accept", live);
			PhantomAssertions.assertTrue(startup >= 0 && recovery > startup && ready > recovery && grant > ready
				&& respawn > grant && live > respawn && proof > live,
				"Stock recovery must retain the exact grant and finish before the source proof.");
			final String background = java.nio.file.Files.readString(root.resolve("background/PhantomBackgroundService.java"));
			final int request = background.indexOf("private OperationResult requestRecovery(");
			final int stamp = background.indexOf("requested = permit == null ? null : permit.stamp()", request);
			final int worker = background.indexOf("private void advanceRecovery(", stamp);
			final int acquire = background.indexOf("_recoveryExecution.tryAcquireScoped(control.profileId, control.execution)", worker);
			final int nativeRecovery = background.indexOf("recoverOwned(control.profileId", acquire);
			PhantomAssertions.assertTrue(request >= 0 && stamp > request && worker > stamp && acquire > worker && nativeRecovery > acquire,
				"Stock respawn lost its captured execution grant between scheduling and the native worker.");
			final String history = java.nio.file.Files.readString(root.resolve("background/PhantomHistoricalBackgroundService.java"));
			final int cold = history.indexOf("public Result prepareColdStartupAuthority(");
			final int fence = history.indexOf("_authorityRefreshBoundary.apply", cold);
			final int abandoned = history.indexOf("_background.recoverAbandonedMaterialization(profileId)", cold);
			final int attest = history.indexOf("prepareCurrentAuthority(profileId, true)", cold);
			PhantomAssertions.assertTrue(cold >= 0 && fence > cold && abandoned > fence && attest > abandoned,
				"Startup lost its owned abandoned-materialization recovery and canonical attestation order.");
		});
		registry.add("party-source-and-every-rendezvous-replan-ignore-solo-kill-only", _ -> org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.partyContract130());
		registry.add("party-common-source-is-tested-before-the-sixty-four-result-limit", _ -> org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.widePartySources130());
		registry.add("party-structural-walk-budget-skips-first-sixty-four-and-keeps-local-source", _ -> org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.boundedStructuralSources130());
		registry.add("party-local-ninth-catalogue-source-fits-the-eight-physical-proofs", _ ->
			org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.practicalPartySources130(new MemoryStore()));
		registry.add("party-practical-ranking-includes-the-peers-gatekeeper-itinerary", _ ->
		{
			final var first = new PhantomDomainRef("background.farm", "20001@farm.leader");
			final var shared = new PhantomDomainRef("background.farm", "20002@farm.shared");
			final var catalogue = List.of(first, shared);
			final var candidates = group(1, 2).members().stream().map(member -> new PhantomLogicalPartyCoordinator.Candidate(member, 76,
				catalogue, catalogue::contains, () -> true, () -> true, admitted -> catalogue.stream().filter(admitted).toList(), List::of,
				source -> Optional.of(source.equals(shared) ? new PhantomLogicalPartyCoordinator.RouteCost(100, 1000, 0, 0)
					: member.profileId() == 1 ? new PhantomLogicalPartyCoordinator.RouteCost(1, 10, 0, 0)
					: new PhantomLogicalPartyCoordinator.RouteCost(30, 15_000, 1, 9300)))).toList();
			final var chosen = new PhantomLogicalPartyCoordinator(new MemoryStore()).previewDiscovery(candidates, (_, _) -> true, 1000);
			PhantomAssertions.assertEquals(shared, chosen.proposal().orElseThrow().objective(), "The leader's nearby source concealed the peer's GK trip.");
		});
		registry.add("party-argos-and-hot-springs-use-the-configured-java-itinerary", _ -> org.l2jmobius.gameserver.phantoms.knowledge.PhantomFarm074Checks.configuredPartyItinerary130());
		registry.add("party-cast-contributes-beyond-the-solo-cast-limit", _ ->
		{
			final var combat = new CombatFacts(ModelKind.MAGIC, 100, 100, 100, 100, 333, 333, 0, 0, 1, 1, 0, 1, 1, 1, 1);
			final var target = new PhantomBackgroundModel.Target(1, 76, true, 3000, 100, 10, 10, 100, 100, 100, 100, 100, 10, List.of(), 1);
			final var skill = new Mechanics(DamageKind.MAGIC, 1, 600, 1000, true, 0);
			final var solo = PhantomBackgroundModel.castEncounter(combat, new Vitals(5000, 5000, 100, 100, 0, 0), 1, target, skill, List.of());
			PhantomAssertions.assertEquals("cast_limit", solo.reason(), "The fixture must reach the unchanged solo limit.");
			final var cast = PhantomBackgroundModel.estimateCastCycle(combat, target, skill).orElseThrow();
			final double dps = cast.damagePerCast() * 1000d / cast.intervalMillis();
			final double groupMillis = PhantomAutonomousFarmParty.projectedFarmDuration(target.maximumHp(), 8 * dps, 0);
			PhantomAssertions.assertTrue(groupMillis > 0 && groupMillis < 60_000, "The solo cast cap erased an affordable group contribution.");
			PhantomAssertions.assertEquals(42d, PhantomPartyFarmEligibility.requiredActions(groupMillis, cast.intervalMillis()), "Each caster must budget 42 MP for the group kill.");
		});
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
			PhantomAssertions.assertEquals(8500d, PhantomPartyFarmEligibility.threatExposureMillis(44308, true, 2500), "An independently supported main victim still had to survive the entire fight without its Party.");
			PhantomAssertions.assertEquals(44308d, PhantomPartyFarmEligibility.threatExposureMillis(44308, true, 0), "Missing support waived the primary threat horizon.");
			PhantomAssertions.assertEquals(15100d, PhantomPartyFarmEligibility.threatExposureMillis(44308, true, 9100), "The six-second reaction reserve was applied as a cap on the native Heal cast.");
			PhantomAssertions.assertEquals(44308d, PhantomPartyFarmEligibility.threatExposureMillis(44308, true, 45000), "A cast slower than the remaining fight waived the primary threat horizon.");
			PhantomAssertions.assertEquals(6000d, PhantomPartyFarmEligibility.threatExposureMillis(44308, false, 2500), "The nonvictim lost the six-second reserve.");
		});
		registry.add("party-extra-threat-uses-the-defence-against-that-attacker", _ ->
		{
			final var againstPrimary = new CombatFacts(ModelKind.MELEE, 100, 100, 400, 100, 500, 500, 0, 0, 1, 1, 0, 1, 1, 1, 1);
			final var againstExtra = new CombatFacts(ModelKind.MELEE, 100, 100, 100, 100, 500, 500, 0, 0, 1, 1, 0, 1, 1, 1, 1);
			final var primary = new PhantomBackgroundModel.Target(1, 76, true, 3000, 100, 300, 100, 100, 100, 500, 500, 100, 10, List.of(), 1);
			final var extra = new PhantomBackgroundModel.Target(2, 76, true, 3000, 100, 600, 100, 100, 100, 500, 500, 100, 10, List.of(), 1);
			final double fight = 30_000;
			final double exposure = PhantomBackgroundModel.incomingDamage(againstPrimary, primary, fight)
				+ PhantomBackgroundModel.incomingDamage(againstExtra, extra, PhantomPartyFarmEligibility.threatExposureMillis(fight, false));
			PhantomAssertions.assertEquals(3600d, exposure, "Reusing the primary defense for the adjacent attacker hid lethal pressure.");
			PhantomAssertions.assertEquals(9000d, PhantomBackgroundModel.incomingDamage(againstExtra, extra, PhantomPartyFarmEligibility.threatExposureMillis(fight, true)),
				"An actual target swap still used only the six-second reserve.");
		});
		registry.add("native-cast-snapshot-fences-replacement-mutation-phase-and-target-budget", _ ->
		{
			final var first = castTarget(101);
			final var second = castTarget(102);
			final var published = new java.util.ArrayList<WorldObject>(List.of(first));
			final var current = new AtomicBoolean(true);
			final var task = new MagicUseTask(null, published, null, 500, false);
			final var initial = task.observeTargets(2, current::get);
			PhantomAssertions.assertEquals(TargetStatus.READY, initial.status(), "The actual slot targets were unavailable.");
			PhantomAssertions.assertEquals(List.of(first), initial.targets(), "The exact cast target was lost.");
			published.set(0, second);
			PhantomAssertions.assertTrue(!initial.isCurrent() && initial.targets().equals(List.of(first)), "Mutating getTargets changed or preserved the old proof.");
			final var originalList = task.observeTargets(2, current::get);
			task.setTargets(List.of(second));
			PhantomAssertions.assertFalse(originalList.isCurrent(), "setTargets did not invalidate the old list identity.");
			final var launch = task.observeTargets(2, current::get);
			task.setPhase(2);
			PhantomAssertions.assertFalse(launch.isCurrent(), "The launch phase survived hit publication.");
			task.setTargets(List.of(first, second));
			PhantomAssertions.assertEquals(TargetStatus.OVERFLOW, task.observeTargets(1, current::get).status(), "A partial AoE list was admitted.");
			task.setTargets(List.of());
			PhantomAssertions.assertEquals(TargetStatus.NO_RECIPIENTS, task.observeTargets(2, current::get).status(), "Missing recipients appeared safe.");
			task.setPhase(3);
			PhantomAssertions.assertEquals(TargetStatus.FINISHED, task.observeTargets(2, current::get).status(), "The finalizer remained a pending hit.");
			current.set(false);
			PhantomAssertions.assertEquals(TargetStatus.STALE, task.observeTargets(2, current::get).status(), "A superseded task was admitted.");
		});
		registry.add("party-cast-victims-come-from-exact-single-area-and-simultaneous-recipients", _ ->
		{
			final var tank = castTarget(101);
			final var healer = castTarget(102);
			final var foreign = castTarget(103);
			final var party = List.of(tank, healer);
			final var single = new MagicUseTask.TargetObservation(TargetStatus.READY, Disposition.HOSTILE, List.of(healer), true, () -> true);
			final var area = new MagicUseTask.TargetObservation(TargetStatus.READY, Disposition.HOSTILE, List.of(tank, healer, foreign), true, () -> true);
			final var simultaneous = new MagicUseTask.TargetObservation(TargetStatus.READY, Disposition.HOSTILE, List.of(tank), true, () -> true);
			PhantomAssertions.assertEquals(java.util.Set.of(102), PhantomPartyFarmEligibility.observedCastVictims(
				new Creature.NativeMagicUseObservation(true, false, single, null, () -> true), party).recipients(), "The cast was assigned to the old tank target.");
			PhantomAssertions.assertEquals(java.util.Set.of(101, 102), PhantomPartyFarmEligibility.observedCastVictims(
				new Creature.NativeMagicUseObservation(true, false, area, null, () -> true), party).recipients(), "The AoE did not preserve every actual Party recipient.");
			PhantomAssertions.assertEquals(java.util.Set.of(101, 102), PhantomPartyFarmEligibility.observedCastVictims(
				new Creature.NativeMagicUseObservation(true, true, single, simultaneous, () -> true), party).recipients(), "A simultaneous stock cast was hidden.");
			final var unrelated = new MagicUseTask.TargetObservation(TargetStatus.READY, Disposition.HOSTILE, List.of(foreign), false, () -> true);
			final var outside = PhantomPartyFarmEligibility.observedCastVictims(new Creature.NativeMagicUseObservation(true, false, unrelated, null, () -> true), party);
			PhantomAssertions.assertTrue(outside.recipients().isEmpty() && outside.refusal().isEmpty(), "An established unrelated cast blocked the party.");
			final var unknown = new MagicUseTask.TargetObservation(TargetStatus.NO_RECIPIENTS, Disposition.HOSTILE, List.of(), true, () -> true);
			PhantomAssertions.assertEquals("native.cast.no_recipients", PhantomPartyFarmEligibility.observedCastVictims(
				new Creature.NativeMagicUseObservation(true, false, unknown, null, () -> true), party).refusal(), "No recipients were treated as confirmed.");
			PhantomAssertions.assertEquals("native.cast.changed", PhantomPartyFarmEligibility.observedCastVictims(
				new Creature.NativeMagicUseObservation(true, false, single, null, () -> false), party).refusal(), "A changed task was reported as safe.");
			PhantomAssertions.assertEquals("native.cast.task_unavailable", PhantomPartyFarmEligibility.observedCastVictims(
				new Creature.NativeMagicUseObservation(true, false, null, null, () -> true), party).refusal(), "An active unpublished task did not wait.");
			final var idle = PhantomPartyFarmEligibility.observedCastVictims(new Creature.NativeMagicUseObservation(false, false, null, null, () -> true), party);
			PhantomAssertions.assertTrue(!idle.hostile() && idle.refusal().isEmpty(), "An idle spawn cannot start a lawful pull.");
			final var unowned = new MagicUseTask.TargetObservation(TargetStatus.READY, Disposition.HOSTILE, List.of(healer), false, () -> true);
			PhantomAssertions.assertEquals("native.cast.recipient_lifetime_unproven", PhantomPartyFarmEligibility.observedCastVictims(
				new Creature.NativeMagicUseObservation(true, false, unowned, null, () -> true), party).refusal(), "An unowned cast proved a Party recipient.");
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
		registry.add("native-evidence-separates-launches-with-a-recycled-pid", _ ->
		{
			final String first = PhantomCoordination130Native.attemptEvidenceKey("native-20261109-212228", 79004);
			final String second = PhantomCoordination130Native.attemptEvidenceKey("native-20261109-220305", 79004);
			PhantomAssertions.assertEquals("native-20261109-212228.79004", first, "Launch receipt lost process identity.");
			PhantomAssertions.assertFalse(first.equals(second), "A recycled PID must not overwrite or block a later launch receipt.");
			PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> PhantomCoordination130Native.attemptEvidenceKey("../native", 79004), "Launch key admitted an untrusted path.");
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
		registry.add("party-kill-witness-rejects-damage-from-another-spawn", _ ->
		{
			final var target = new Target(10, 0, 2);
			final var oldSpawn = new Target(10, 0, 1);
			final long epoch = System.nanoTime();
			final var attacker = new PlayerNativeEvidence(1, epoch);
			final var support = new PlayerNativeEvidence(2, epoch);
			attacker.damage(oldSpawn, 30);
			attacker.killedIfDamaged(oldSpawn);
			final var beforeAttacker = attacker.snapshot();
			final var beforeSupport = support.snapshot();
			attacker.reward(target, 40, 4);
			attacker.rewardSettled(target, false);
			support.reward(target, 20, 2);
			support.rewardSettled(target, false);
			final java.util.function.Function<Boolean, List<PhantomCoordination130Native.EarnedReward>> witness = damage ->
				PhantomCoordination130Native.exactGroupRewards(target, List.of(
					new PhantomCoordination130Native.MemberFight(beforeAttacker, attacker.snapshot(), attacker.targetReceipts(), damage),
					new PhantomCoordination130Native.MemberFight(beforeSupport, support.snapshot(), support.targetReceipts(), false)));
			PhantomAssertions.assertTrue(witness.apply(true).isEmpty(), "A previous spawn supplied the current kill receipt.");
			attacker.damage(target, 20);
			attacker.killedIfDamaged(target);
			PhantomAssertions.assertTrue(witness.apply(false).isEmpty(), "Unobserved Party damage passed the exact kill gate.");
			PhantomAssertions.assertEquals(List.of(new PhantomCoordination130Native.EarnedReward(1, 40, 4, true),
				new PhantomCoordination130Native.EarnedReward(2, 20, 2, false)), witness.apply(true), "The same spawn did not witness the attacker and its pure support recipient.");
			attacker.reward(oldSpawn, 10, 1);
			attacker.reward(target, 30, 3);
			attacker.rewardSettled(target);
			PhantomAssertions.assertTrue(witness.apply(true).isEmpty(), "Interleaved reward deltas were attributed to one NPC.");
		});
		registry.add("preflight-recovery-and-read-only-scene-have-independent-finite-budgets", _ ->
		{
			PhantomCoordination130Native.checkPreflightBudget(java.util.concurrent.TimeUnit.SECONDS.toNanos(40), true);
			PhantomCoordination130Native.checkPreflightBudget(java.util.concurrent.TimeUnit.SECONDS.toNanos(29), false);
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> PhantomCoordination130Native.checkPreflightBudget(java.util.concurrent.TimeUnit.SECONDS.toNanos(60), true), "Recovery exceeded its finite budget.");
			PhantomAssertions.assertThrows(IllegalStateException.class, () -> PhantomCoordination130Native.checkPreflightBudget(java.util.concurrent.TimeUnit.SECONDS.toNanos(30), false), "Scene exceeded its own budget.");
		});
		registry.add("physical-proof-timeout-reserves-the-next-source-in-the-thirty-second-scene", _ ->
		{
			final var units = java.util.concurrent.TimeUnit.NANOSECONDS;
			final long sceneDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
			final var interrupted = new java.util.concurrent.CountDownLatch(1);
			final var stalled = new java.util.concurrent.CountDownLatch(1);
			final var expired = PhantomCoordination130Native.physicalProofWithin(sceneDeadline, java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(300), deadline ->
			{
				try { stalled.await(); }
				catch (InterruptedException expected) { interrupted.countDown(); }
				return true;
			});
			PhantomAssertions.assertTrue(expired.isEmpty(), "A timed-out Geo probe published a late positive result.");
			PhantomAssertions.assertTrue(interrupted.await(java.util.concurrent.TimeUnit.SECONDS.toNanos(2), units), "The timed-out probe was not interrupted.");
			final var local = PhantomCoordination130Native.physicalProofWithin(sceneDeadline, java.util.concurrent.TimeUnit.SECONDS.toNanos(3),
				deadline -> deadline < sceneDeadline);
			PhantomAssertions.assertEquals(Optional.of(true), local, "The first stalled proof starved the next local source.");
			PhantomAssertions.assertTrue(PhantomCoordination130Native.physicalProofWithin(System.nanoTime() - 1, 1_000_000,
				_ -> { throw new AssertionError("Expired scene started a physical proof."); }).isEmpty(), "Expired scene accepted a proof.");
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
			PhantomAssertions.assertTrue(changed.commitDiscovery(stale, (_, _) -> true).isEmpty() && changedStore.claims.isEmpty() && changedStore.groups.isEmpty(), "A changed route committed durable state.");
		});
		registry.add("changed-role-refuses-formation-even-with-current-sql-guards", _ ->
		{
			final var template = group(1, 2);
			final var roleCurrent = new AtomicBoolean(true);
			final var candidates = template.members().stream().map(member -> new PhantomLogicalPartyCoordinator.Candidate(member, 76, List.of(template.objective()),
				template.objective()::equals, () -> true, roleCurrent::get, admitted -> List.of(template.objective()).stream().filter(admitted).toList(),
				() -> List.of(new PhantomProfileComponent(member.profileId(), "goal.state", 1, 0, new byte[] { 1 }, Instant.EPOCH, Instant.EPOCH)))).toList();
			final var store = new MemoryStore();
			final var owner = new PhantomLogicalPartyCoordinator(store);
			final var preview = owner.previewDiscovery(candidates, (_, _) -> true, 1000);
			PhantomAssertions.assertTrue(preview.proposal().isPresent() && preview.guards().size() == 2, "The guarded control did not select a roster.");
			roleCurrent.set(false);
			PhantomAssertions.assertTrue(owner.commitDiscovery(preview, (_, _) -> true).isEmpty() && store.groups.isEmpty() && store.claims.isEmpty(), "Unchanged SQL component guards hid a changed role.");
			roleCurrent.set(true);
			PhantomAssertions.assertTrue(owner.discover(candidates, (_, _) -> true, 2000).isPresent() && store.claims.size() == 2, "The current role did not permit a guarded formation.");
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
			PhantomAssertions.assertTrue(wait.nextDueMillis() > 1000 && owner.commitDiscovery(wait, (_, _) -> true).isEmpty() && store.claims.isEmpty() && store.groups.isEmpty(), "A bounded wait wrote or failed to schedule a future round.");
			final var later = owner.discover(List.of(), (_, _) -> true, wait.nextDueMillis()).orElseThrow();
			PhantomAssertions.assertEquals(source, later.group().objective(), "A bounded wait starved the next leader window.");
		});
		registry.add("physical-proof-fallback-commits-only-the-exact-proposal-and-bounds-rejection", _ ->
		{
			final var sources = LongStream.rangeClosed(1, 12).mapToObj(index -> new PhantomDomainRef("background.farm", (20000 + index) + "@farm." + index)).toList();
			final var candidates = group(1, 2).members().stream().map(member -> new PhantomLogicalPartyCoordinator.Candidate(member, 76, sources, sources::contains, () -> true)).toList();
			final var store = new MemoryStore();
			final var owner = new PhantomLogicalPartyCoordinator(store);
			final var consent = new AtomicBoolean(true);
			final var seen = new java.util.ArrayList<PhantomLogicalPartyCoordinator.Discovery>();
			final var selected = owner.previewDiscovery(candidates, (_, _) -> consent.get(), 1000, proposal ->
			{
				PhantomAssertions.assertTrue(store.groups.isEmpty() && store.claims.isEmpty(), "The physical callback observed a durable reservation.");
				seen.add(proposal);
				return proposal.proposal().orElseThrow().objective().equals(sources.get(1));
			});
			PhantomAssertions.assertEquals(List.of(sources.getFirst(), sources.get(1)), seen.stream().map(proposal -> proposal.proposal().orElseThrow().objective()).toList(), "The owner did not try the next source in order.");
			PhantomAssertions.assertTrue(selected == seen.getLast(), "The owner returned a different proposal from the one physically proved.");
			PhantomAssertions.assertEquals(List.of(76, 76), selected.levels(), "The proof did not carry exact roster levels.");
			consent.set(false);
			PhantomAssertions.assertTrue(owner.commitDiscovery(selected, (_, _) -> consent.get()).isEmpty() && store.groups.isEmpty() && store.claims.isEmpty(), "Changed consent committed the physically proved proposal.");
			consent.set(true);
			final var accepted = owner.previewDiscovery(candidates, (_, _) -> true, 2000, proof -> proof.proposal().orElseThrow().objective().equals(sources.get(1)));
			PhantomAssertions.assertEquals(accepted.proposal().orElseThrow(), owner.commitDiscovery(accepted, (_, _) -> true).orElseThrow().group(), "The CAS did not persist the same source, roster and group ID.");
			final var emptyStore = new MemoryStore();
			final var bounded = new PhantomLogicalPartyCoordinator(emptyStore);
			final var checks = new java.util.concurrent.atomic.AtomicInteger();
			final var exhausted = bounded.previewDiscovery(candidates, (_, _) -> true, 1000, _ -> { checks.incrementAndGet(); return false; });
			PhantomAssertions.assertEquals(PhantomLogicalPartyCoordinator.DiscoveryStatus.WAIT_PHYSICAL_PROOF_BUDGET, exhausted.status(), "An exhausted physical proof budget lost its typed reason.");
			PhantomAssertions.assertTrue(checks.get() == 8 && exhausted.nextDueMillis() > 1000 && bounded.commitDiscovery(exhausted, (_, _) -> true).isEmpty() && emptyStore.claims.isEmpty() && emptyStore.groups.isEmpty(), "Physical exhaustion leaked writes or ignored its finite budget.");
		});
		registry.add("pre-registration-proof-and-scheduled-background-share-busy-authority", _ ->
		{
			final var presence = new PhantomPresenceRegistry(2);
			final var owned = new AtomicBoolean(true);
			presence.installExternalBusySource("test.owner", _ -> owned.get());
			PhantomAssertions.assertEquals("offline", presence.busyReason(1), "Ordinary Presence admitted an unregistered profile.");
			PhantomAssertions.assertEquals("test.owner", presence.busyReasonBeforeRegistration(1), "Startup ignored an existing owner.");
			presence.schedule(1, PhantomActivityState.BACKGROUND);
			PhantomAssertions.assertEquals(presence.busyReason(1), presence.busyReasonBeforeRegistration(1), "BACKGROUND used a different ownership gate.");
			owned.set(false);
			PhantomAssertions.assertEquals("none", presence.busyReason(1), "A free scheduled BACKGROUND member was excluded.");
			presence.schedule(1, PhantomActivityState.SLEEPING);
			PhantomAssertions.assertEquals("offline", presence.busyReasonBeforeRegistration(1), "Startup bypassed an already registered sleeping profile.");
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

	private static WorldObject castTarget(int objectId)
	{
		return new WorldObject(objectId)
		{
			@Override public boolean isAutoAttackable(Creature attacker) { return false; }
			@Override public void sendInfo(Player player) { }
		};
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
		@Override public StoredLogicalGroup commitLogicalGroup(PhantomLogicalParty group, long expected, Map<Long, Long> versions, List<PhantomProfileComponent> guards)
		{
			// The fixture keeps its SQL guards current. The coordinator still owns non-SQL revalidation.
			return commitLogicalGroup(group, expected, versions);
		}
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

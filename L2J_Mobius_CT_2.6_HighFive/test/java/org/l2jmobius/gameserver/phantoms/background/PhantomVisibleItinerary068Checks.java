/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

import java.util.List;

import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.tests.phantoms.PhantomAssertions;
import org.l2jmobius.tests.phantoms.PhantomTestRegistry;
import org.l2jmobius.tests.phantoms.PhantomTestSuite;

/** Pure ordinary itinerary proposals; native movement/checkpoint effects remain in their owners. */
public final class PhantomVisibleItinerary068Checks implements PhantomTestSuite
{
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
		registry.add("10-offscreen-hash-fence-is-owned-only-by-typed-receipt", _ -> offscreenReceiptHash());
		registry.add("11-completed-offscreen-arrival-requires-native-capture-before-history", _ -> completedOffscreenNativeHandoff());
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
		PhantomAssertions.assertTrue(PhantomBackgroundTransaction.offscreenReceiptHashCurrent(generic, currentHash), "An unrelated old generic receipt must not veto a fresh native-bound offscreen proof.");
		for (long debt : List.of(0L, 60_000L))
		{
			final var matching = offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, PhantomOffscreenOutdoorTravel.RECEIPT_TICK, currentHash), debt);
			final var mismatching = offscreenReceiptState(new PhantomBackgroundState.Receipt("c".repeat(64), 0, PhantomOffscreenOutdoorTravel.RECEIPT_TICK, staleHash), debt);
			PhantomAssertions.assertTrue(PhantomOffscreenOutdoorTravel.taggedReceipt(matching), "Both pending and completed offscreen receipts retain the typed fence.");
			PhantomAssertions.assertTrue(PhantomBackgroundTransaction.offscreenReceiptHashCurrent(matching, currentHash), "The exact typed receipt may continue only with its matching hash.");
			PhantomAssertions.assertFalse(PhantomBackgroundTransaction.offscreenReceiptHashCurrent(mismatching, currentHash), "Typed offscreen replay must remain fenced on hash mismatch, including zero residual debt.");
		}
		PhantomAssertions.assertEquals(staleHash, generic.receipt().expectedAfterHash(), "Hash admission cannot clear or overwrite the old receipt.");
		System.out.println("OWN129_OFFSCREEN_RECEIPT_HASH_PASS; generic receipt distinct from typed pending/completed fence; DB_CONNECTIONS=0; OWNER_RECOVERY=NOT_RUN");
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

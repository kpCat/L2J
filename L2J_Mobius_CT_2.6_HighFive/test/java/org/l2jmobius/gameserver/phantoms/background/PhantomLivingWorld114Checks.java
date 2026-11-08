/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.background;

/** DB-free contract for re-attesting a canonical level change, never granting policy permission. */
public final class PhantomLivingWorld114Checks
{
	private static void check(boolean value, String reason) { if (!value) { throw new AssertionError(reason); } }
	private static PhantomBackgroundSimulationPolicy policy(int unsupported)
	{
		return new PhantomBackgroundSimulationPolicy(2, 1, 1, 1, unsupported == 0, true, true, true, false, 1,
			5, 0.4, 2.5, 3, 4.5, 6, 0, 0, 9.5, 9.5, "a".repeat(64), 120, 60000, unsupported);
	}
	private static PhantomNativeContext context(PhantomBackgroundSimulationPolicy policy)
	{
		return PhantomNativeContext.completed(new PhantomBackgroundState.Identity(1, 2, 0, 0, 0),
			new PhantomNativeContext.Capture(1, PhantomNativeContext.Eligibility.SUPPORTED, policy), 3, new byte[] { 1 });
	}
	public static void main(String[] args)
	{
		ordinaryGoalReconciliation();
		if ((args.length == 1) && args[0].equals("--ordinary-goal129")) { return; }
		final var ordinary = policy(0);
		final var changed = context(ordinary.withPoints(1, 2));
		check(!changed.permits(PhantomBackgroundSimulationPolicy.Operation.FARM, "a".repeat(64)), "Unattested level change permitted rewards");
		check(changed.requiresCanonicalRefresh(), "Canonical level change stranded the historical cursor instead of requesting native re-attestation");
		check(PhantomNativeContext.decode(changed.encode()).requiresCanonicalRefresh(), "Restart lost canonical refresh requirement");
		check(!context(ordinary).requiresCanonicalRefresh(), "Current native policy repeatedly refreshed");
		System.out.println("PASS level-change requests canonical refresh; rewards remain fenced; serialized restart preserves requirement");
		final var ammo = context(policy(PhantomBackgroundSimulationPolicy.NATIVE_AMMUNITION_REQUIRED));
		check(!ammo.requiresCanonicalRefresh() && !ammo.permits(PhantomBackgroundSimulationPolicy.Operation.FARM, "a".repeat(64)), "Native ammunition became scalar-farm eligible");
		check(context(policy(256).withPoints(1, 2)).requiresCanonicalRefresh(), "Level refresh lost when a native-only loadout coexists");
		check(!context(policy(256)).requiresCanonicalRefresh(), "Native-only loadout caused an endless canonical refresh loop");
		final var identity = new PhantomBackgroundState.Identity(1, 2, 0, 0, 0);
		check(PhantomNativeContext.completed(identity, 0, PhantomNativeContext.Eligibility.UNKNOWN, 3, new byte[] { 1 }).requiresCanonicalRefresh(), "UNKNOWN baseline stopped requesting native capture");
		final var pending = PhantomNativeContext.pending(identity, 1, PhantomNativeContext.Eligibility.SUPPORTED,
			new PhantomNativeContext.Capture(1, PhantomNativeContext.Eligibility.SUPPORTED, ordinary.withPoints(1, 2)), 3, new byte[] { 1 }, 7, new byte[] { 2 }, ordinary);
		check(!pending.requiresCanonicalRefresh(), "An in-flight owned STORE was replaced by canonical refresh");
		System.out.println("PASS native-only loadout remains fenced without repeated refresh");
		final var evidence = new org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence(2, System.nanoTime());
		evidence.nativeRegeneration(1, 1, false, false, false, 0, 0, 0);
		check(evidence.snapshot().phase() == org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase.REGEN, "Passive regeneration lost its bounded phase");
		final long originalRest = evidence.snapshot().phaseSinceNanos();
		evidence.nativeRegeneration(1, 1, false, false, true, 0, 0, 0);
		check(evidence.snapshot().phaseSinceNanos() == originalRest, "A stuck movement flag retired the passive rest deadline");
		evidence.nativeRegeneration(1, 1, false, false, true, 10, 0, 0);
		check(evidence.snapshot().phase() == org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase.NONE, "Native activity retained a passive rest deadline");
		evidence.nativeRegeneration(1, 1, false, false, true, 20, 0, 0);
		check(evidence.snapshot().phase() == org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Phase.NONE && evidence.snapshot().farmCycleSequence() == 0, "Active regeneration created a rest phase or fake useful farm credit");
		evidence.nativeRegeneration(1, 1, false, false);
		final long since = evidence.snapshot().phaseSinceNanos();
		evidence.pauseRegeneration(false, false);
		evidence.nativeRegeneration(1, 1, false, false);
		check(evidence.snapshot().phaseSinceNanos() == since, "Passive pause/retry renewed the rest horizon");
		System.out.println("PASS native activity ends passive REGEN attribution without farm credit; passive pause cannot renew deadline");
		check(PhantomVisibleFarmTravel.navigationFailureDisposition(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED) == PhantomVisibleFarmTravel.Disposition.INDETERMINATE_ROUTE, "Search exhaustion became physical impossibility or an indefinitely exhausted transient attempt");
		check(PhantomVisibleFarmTravel.navigationFailureDisposition(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.QUEUE_BACKPRESSURE) == PhantomVisibleFarmTravel.Disposition.TRANSIENT_SERVICE, "Service pressure was misclassified as route geometry");
		System.out.println("PASS completed search budget retires only the exact route attempt; queue pressure remains transient");
		final var target = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor("nearby", org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING, "farm",
			new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(1000, 0, 0, 0), null, null, 0, java.util.List.of(), java.util.List.of());
		final var live = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(0, 0, 0, 0);
		final var approach = PhantomVisibleFarmTravel.localFarmApproach(target, live, 1000);
		check(approach != null && approach.id().equals("live.approach.nearby") && approach.toAnchorId().equals("nearby") && approach.gatekeeper() == null, "Local farm approach returned to an unrelated stored lineage");
		check(PhantomVisibleFarmTravel.localFarmApproach(target, live, 999) == null, "Local approach exceeded the existing distance bound");
		check(PhantomVisibleFarmTravel.localFarmApproach(target, new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(0, 0, 0, 1), 1000) == null, "Local approach crossed instances");
		System.out.println("PASS nearby farm approaches from actual coordinates within the existing bound; no factual departure, fee or arrival is granted");
		final var nativeCycle = new org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence(2, 100);
		check(!PhantomHistoricalBackgroundService.nativeHandoffWorkComplete(nativeCycle.snapshot(100), 2, 100), "Idle handoff completed without useful native work");
		final var a = new org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target(10, 0, 1);
		nativeCycle.selected(a, 100); nativeCycle.damage(a, 5, 101); nativeCycle.killed(a, 102); nativeCycle.reward(a, 20, 2, 103);
		nativeCycle.selected(new org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target(11, 0, 1), 104);
		final var completed = nativeCycle.snapshot(105);
		check(PhantomHistoricalBackgroundService.nativeHandoffWorkComplete(completed, 2, 100), "Completed native farm cycle stranded the temporary handoff");
		check(!PhantomHistoricalBackgroundService.nativeHandoffWorkComplete(completed, 3, 100) && !PhantomHistoricalBackgroundService.nativeHandoffWorkComplete(completed, 2, 101), "Foreign lifetime completed a native handoff");
		nativeCycle.nativeRegeneration(Double.NaN, 1, false, false);
		check(!PhantomHistoricalBackgroundService.nativeHandoffWorkComplete(nativeCycle.snapshot(107), 2, 100), "Unproven work completed a native handoff");
		System.out.println("PASS only a proven useful native cycle of the exact lifetime may finish temporary handoff; cursor/rewards unchanged");
		final var owner = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("profile", "1");
		final var idle = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoal(128001, "idle.background.history", org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus.ACTIVE,
			owner, owner, 1, 0, "background.idle", java.util.List.of(), new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("topology.anchor", "farm.fixture"),
			"background.history", 500, 0, 0, 0, java.util.Map.of(), "planner.target_or_route.absent", 0);
		final var sixPoints = ordinary.withPoints(6, 1);
		final var attestedIdle = PhantomNativeContext.completed(identity, new PhantomNativeContext.Capture(6, PhantomNativeContext.Eligibility.VITALITY_REQUIRES_NATIVE, sixPoints), 3, new byte[] { 1 });
		check(!attestedIdle.simulationEligible() && attestedIdle.permits(PhantomBackgroundSimulationPolicy.Operation.FARM, "a".repeat(64)), "Fixture must reproduce a current v2 proof rejected by legacy MIN1 eligibility");
		check(PhantomBackgroundTransaction.attestedHistoricalIdleContext(PhantomBackgroundOperationKey.ActionKind.HISTORICAL_IDLE, 1, idle, PhantomNativeContext.decode(attestedIdle.encode()), "a".repeat(64)), "Attested non-MIN1 canonical idle stranded the historical cursor");
		check(!PhantomBackgroundTransaction.attestedHistoricalIdleContext(PhantomBackgroundOperationKey.ActionKind.HISTORICAL_FARM, 1, idle, attestedIdle, "a".repeat(64)), "Clock-only exception admitted farming");
		check(!PhantomBackgroundTransaction.attestedHistoricalIdleContext(PhantomBackgroundOperationKey.ActionKind.HISTORICAL_IDLE, 2, idle, attestedIdle, "a".repeat(64)), "Clock-only exception crossed identity ownership");
		check(!PhantomBackgroundTransaction.attestedHistoricalIdleContext(PhantomBackgroundOperationKey.ActionKind.HISTORICAL_IDLE, 1, idle, attestedIdle, "b".repeat(64)), "Stale configured policy admitted idle");
		check(!PhantomBackgroundTransaction.attestedHistoricalIdleContext(PhantomBackgroundOperationKey.ActionKind.HISTORICAL_IDLE, 1, idle, pending, "a".repeat(64)), "Owned PENDING context admitted idle");
		System.out.println("PASS canonical no-effect historical idle accepts current v2 native proof; rewards, pending STORE, identities and rules remain fenced");
		final var gatekeeper = new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor("giran.gatekeeper.30080", org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.GATEKEEPER, "giran.city",
			new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(83396, 147904, -3404, 0), 30080, 918, 0, java.util.List.of(), java.util.List.of());
		check(L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(gatekeeper, 0, z -> -3400, () -> true).map(p -> p.z() == -3400 && p.x() == 83396 && p.y() == 147904).orElse(false), "Proven native gatekeeper raw spawn height rejected its stable Geo floor");
		check(L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(gatekeeper, 0, z -> -3400, () -> false).isEmpty(), "Unproven gatekeeper bypassed strict tolerance");
		check(L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(target, 0, z -> 4, () -> true).isEmpty(), "NPC floor exception admitted malformed FARM arrival");
		check(L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(gatekeeper, 0, z -> z + 4, () -> true).isEmpty(), "NPC floor exception admitted unstable Geo floor");
		System.out.println("PASS exact native gatekeeper proof canonicalizes only its stable floor; malformed farm and unstable or unproven arrivals remain fenced");
		check(PhantomHistoricalBackgroundService.isRecoverableFailure("catchup.travel.anchor_mismatch"), "Durable route mismatch prevented re-proving an updated native anchor");
		check(PhantomHistoricalBackgroundService.isRecoverableFailure("catchup.travel.insufficient_adena"), "A spent route wallet permanently fenced native earning instead of affordable replanning");
		check(PhantomBackgroundService.historicalTravelFailureStatus(PhantomBackgroundAuthority.TravelAdvance.Status.INSUFFICIENT_ADENA) == PhantomBackgroundService.OperationStatus.REPLAN, "An unchanged wallet retried forever without reaching affordable recovery");
		check(PhantomBackgroundService.historicalTravelFailureStatus(PhantomBackgroundAuthority.TravelAdvance.Status.EDGE_CLOSED) == PhantomBackgroundService.OperationStatus.RETRY && PhantomBackgroundService.historicalTravelFailureStatus(PhantomBackgroundAuthority.TravelAdvance.Status.NO_ROUTE) == PhantomBackgroundService.OperationStatus.RETRY, "Transient route availability lost its retry contract");
		check(!PhantomHistoricalBackgroundService.isRecoverableFailure("transaction.native_context_required") && !PhantomHistoricalBackgroundService.isRecoverableFailure("transaction.state_conflict"), "Route replanning bypassed native context or state conflict ownership");
		System.out.println("PASS durable route mismatch may replan through existing ownership and route checks; context and transaction conflicts remain fenced");
		check(PhantomBackgroundService.recoveryOwnerChangeResult(2, 10, 2, 11, false, false).status() == PhantomBackgroundService.OperationStatus.REPLAN, "Superseded recovery poisoned shutdown of a newer native lifetime");
		check(PhantomBackgroundService.recoveryOwnerChangeResult(2, 10, 3, 11, false, false).status() == PhantomBackgroundService.OperationStatus.INCONSISTENT, "Foreign identity was treated as a superseded worker");
		check(PhantomBackgroundService.recoveryOwnerChangeResult(2, 10, 2, 9, false, false).status() == PhantomBackgroundService.OperationStatus.INCONSISTENT, "Older lifetime bypassed recovery ownership");
		check(PhantomBackgroundService.recoveryOwnerChangeResult(2, 10, 2, 11, true, false).status() == PhantomBackgroundService.OperationStatus.INCONSISTENT && PhantomBackgroundService.recoveryOwnerChangeResult(2, 10, 2, 11, false, true).status() == PhantomBackgroundService.OperationStatus.INCONSISTENT, "Pending STORE or baseline escaped recovery ownership");
		System.out.println("PASS only a superseded same-identity non-STORE worker retires as REPLAN; no new-lifetime action, receipt or failure-latch clearing");
	}

	private static void ordinaryGoalReconciliation()
	{
		final var changed = PhantomBackgroundService.OperationResult.replan("recovery.control_goal_changed");
		check(PhantomOrdinaryDeathRecovery.permitsGoalReconciliation(changed, true, true, true, 100, 200), "RED129: lawful goal change poisoned accepted native corpse reconciliation and STOP");
		check(!PhantomOrdinaryDeathRecovery.permitsGoalReconciliation(changed, true, false, true, 100, 200), "A stale original epoch rebound to another native owner");
		check(!PhantomOrdinaryDeathRecovery.permitsGoalReconciliation(changed, false, true, true, 100, 200), "Rebinding manufactured a native town return");
		check(!PhantomOrdinaryDeathRecovery.permitsGoalReconciliation(changed, true, true, false, 100, 200), "Unchanged goal retried a terminal control");
		check(!PhantomOrdinaryDeathRecovery.permitsGoalReconciliation(changed, true, true, true, 200, 200), "A goal change renewed the accepted deadline");
		check(!PhantomOrdinaryDeathRecovery.permitsGoalReconciliation(PhantomBackgroundService.OperationResult.inconsistent("recovery.control_goal_changed"), true, true, true, 100, 200), "An inconsistent recovery was suppressed");
		check(!PhantomOrdinaryDeathRecovery.permitsGoalReconciliation(PhantomBackgroundService.OperationResult.replan("recovery.goal"), true, true, true, 100, 200), "An invalid recovery goal was silently accepted");
		check(PhantomBackgroundService.recoveryControlDeadline(100, 200) == 200, "Rebound background control outlived its accepted corpse reconciliation");
		check(PhantomBackgroundService.recoveryControlDeadline(100, 0) == 10_000_000_100L, "Existing unbounded-caller recovery horizon changed");
		check(PhantomBackgroundService.recoveryControlDeadline(100, 20_000_000_000L) == 10_000_000_100L, "Accepted caller extended the normal recovery horizon");
		check(PhantomBackgroundService.recoveryControlDeadline(100, 99) == 99, "Expired accepted deadline was renewed");
		System.out.println("PASS129 ordinary goal change retries only exact returned owner/current goal within original deadline; invalid/foreign/stale/fatal negatives remain fenced; DB0 World0 nativeSTOP_PENDING");
	}
}

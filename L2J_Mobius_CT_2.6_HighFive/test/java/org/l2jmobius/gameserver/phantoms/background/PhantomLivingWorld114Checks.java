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
		check(PhantomVisibleFarmTravel.navigationFailureDisposition(org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.ROUTE_BUDGET_EXCEEDED) == PhantomVisibleFarmTravel.Disposition.ROUTE_UNUSABLE, "Completed bounded search remained an indefinitely exhausted transient attempt");
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
	}
}

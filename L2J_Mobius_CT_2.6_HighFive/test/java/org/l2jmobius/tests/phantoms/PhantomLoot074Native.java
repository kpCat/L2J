/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootCandidate;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.LootObservation;
import org.l2jmobius.gameserver.phantoms.combat.PhantomOwnedAction;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;

/** Compact OWN074 controls. The caller creates every declared fixture before its measurement marker. */
public final class PhantomLoot074Native
{
	private PhantomLoot074Native() { }

	public static void main(String[] args)
	{
		final var report = new StringBuilder(); reducerControls(report); System.out.print(report);
	}

	/** Reducer diagnosis, not invented native kills: absent selections exhaust the same sixteen-target bound. */
	public static void reducerControls(StringBuilder report)
	{
		for (boolean selected : new boolean[] {false, true})
		{
			final var sensor = new PlayerNativeEvidence(740074, System.nanoTime());
			for (int i = 1; i <= 17; i++)
			{
				final var target = new PlayerNativeEvidence.Target(i, 0, 1);
				if (selected) { sensor.selected(target); }
				sensor.damage(target, 1); sensor.reward(target, 2, 1); sensor.killed(target); sensor.rewardSettled(target);
			}
			PlayerNativeEvidence.withPickupSource(sensor, 740074, 57, 1, () -> PlayerNativeEvidence.pickupGain(740074, 57, 1));
			final var snapshot = sensor.snapshot(); final var receipt = sensor.pickupReceipt(740074);
			report.append("C_REDUCER_CONTROL\tselected=").append(selected).append(";targets=").append(snapshot.trackedTargets()).append(";firstUnproven=").append(snapshot.firstUnprovenReason()).append(";receipt=").append(receipt).append(";nativeKills=0;scalarControlOnly\n");
			check(selected ? !snapshot.overflow() && receipt != null : snapshot.firstUnprovenReason() == PlayerNativeEvidence.UnprovenReason.TARGET_CAP && receipt == null, "Reducer did not reproduce exact selection/cap/pickup relationship.");
		}
	}

	/** One ordinary offensive submission, immediately cleaned by its exact target; no reward/resource setters. */
	public static boolean verifyDetachedSelection(long profileId, Player player, PhantomMaterializationService materialization, L2jCombatBackend backend, Monster target, StringBuilder report)
	{
		guard(profileId, player, materialization);
		check(!player.isMoving() && !player.isAttackingNow() && !player.isCastingNow(), "Selection control requires a quiet native owner.");
		final var sensor = player.getNativeWorkOwner().evidence(); final var before = sensor.snapshot();
		try (var lease = backend.tryAcquireActor(profileId))
		{
			check(lease != null && PlayerNativeWork.current(player.getNativeWorkOwner()) == null, "Selection control must use the actual detached combat lease.");
			final var outcome = lease.attack(target.getObjectId());
			try
			{
				final var after = sensor.snapshot();
				final boolean admitted = outcome == ActionOutcome.ISSUED || outcome == ActionOutcome.ALREADY_OWNED;
				final boolean observed = admitted && after.targetSequence() > before.targetSequence();
				report.append("C_NATIVE_SELECTION\tactor=").append(player.getObjectId()).append(";target=").append(target.getId()).append('/').append(target.getObjectId()).append(";outcome=").append(outcome).append(";selected=").append(before.targetSequence()).append("->").append(after.targetSequence()).append(";currentAfter=").append(PlayerNativeWork.current(player.getNativeWorkOwner())).append(";firstUnproven=").append(after.firstUnprovenReason()).append(";result=").append(observed ? "PASS" : "RED_MISSING_NATIVE_SELECTION").append('\n');
				return observed;
			}
			finally { lease.cancelOwnedAction(new PhantomOwnedAction(740074, target.getObjectId(), null, 0)); }
		}
	}

	/** Caller supplies an already declared lawful ground object; this helper never produces one. */
	public static boolean pickupRestControl(long profileId, Player player, PhantomMaterializationService materialization, L2jCombatBackend backend, Item declaredGround, StringBuilder report) throws InterruptedException
	{
		guard(profileId, player, materialization);
		check(declaredGround.isSpawned() && World.getInstance().findObject(declaredGround.getObjectId()) == declaredGround && player.calculateDistance2D(declaredGround) < 120, "Exact close declared ground object required.");
		try (var action = materialization.tryAcquireAction(profileId).orElseThrow()) { action.player().sitDown(); }
		check(player.isSitting() && player.getAI().getIntention() == Intention.REST, "Native rest control was not admitted.");
		final long before = player.getInventory().getInventoryItemCount(declaredGround.getId(), -1);
		try (var lease = backend.tryAcquireActor(profileId))
		{
			check(lease != null, "Native loot action lease unavailable.");
			final var candidate = lease.lootCandidates(32, 600).stream().filter(value -> value.worldObjectId() == declaredGround.getObjectId()).findFirst().orElseThrow();
			final var rest = lease.pickUp(declaredGround.getObjectId());
			check(declaredGround.isSpawned() && player.getInventory().getInventoryItemCount(declaredGround.getId(), -1) == before, "REST negative wrote inventory or removed ground.");
			report.append("C_REST_NEGATIVE\tobject=").append(declaredGround.getObjectId()).append(";item=").append(declaredGround.getId()).append(";outcome=").append(rest).append(";ground=true;inventoryUnchanged=true\n");
			try (var action = materialization.tryAcquireAction(profileId).orElseThrow()) { action.player().standUp(); }
			final var issued = lease.pickUp(declaredGround.getObjectId());
			final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			LootObservation observation;
			do { observation = lease.observeLoot(candidate); if (observation != LootObservation.PENDING) { break; } Thread.sleep(100); } while (System.nanoTime() < until);
			classify(player, candidate, observation, report);
			return rest == ActionOutcome.REJECTED && (issued == ActionOutcome.ISSUED || issued == ActionOutcome.ALREADY_OWNED) && (observation == LootObservation.ACQUIRED_BY_ACTOR || observation == LootObservation.CONSUMED_NATIVE);
		}
	}

	/** Read-only diagnostics preserve source identity; inventory gain alone never proves this object. */
	public static void classify(Player player, LootCandidate candidate, LootObservation observation, StringBuilder report)
	{
		final var source = World.getInstance().findObject(candidate.worldObjectId());
		final var owner = player.getNativeWorkOwner(); final var sensor = owner == null ? null : owner.evidence();
		final var receipt = sensor == null ? null : sensor.pickupReceipt(candidate.worldObjectId());
		report.append("C_NATIVE_LOOT\tobject=").append(candidate.worldObjectId()).append(";item=").append(candidate.itemId()).append(";count=").append(candidate.groundCount()).append(";observation=").append(observation).append(";receipt=").append(receipt).append(";actorCount=").append(player.getInventory().getInventoryItemCount(candidate.itemId(), -1)).append(";nativeState=").append(sensor == null ? "NONE" : sensor.snapshot().scalarMap()).append(";intention=").append(player.hasAI() ? player.getAI().getIntention() : "NONE");
		if (source instanceof Item item)
		{
			report.append(";spawned=").append(item.isSpawned()).append(";location=").append(item.getItemLocation()).append(";owner=").append(item.getOwnerId()).append(";distance=").append(player.calculateDistance2D(item)).append(";dz=").append(Math.abs(player.getZ() - item.getZ())).append(";protectionAllows=").append(item.getDropProtection().tryPickUp(player)).append(";ownerAllows=").append(item.getOwnerId() == 0 || item.getOwnerId() == player.getObjectId() || player.isInLooterParty(item.getOwnerId())).append(";capacity=").append(player.getInventory().validateCapacity(item)).append(";weight=").append(player.getInventory().validateWeightByItemId(item.getId(), item.getCount()));
		}
		else { report.append(";sourceMissing=true"); }
		report.append('\n');
	}

	public static void dispositions(long profileId, Player player, PhantomMaterializationService materialization, StringBuilder report)
	{
		guard(profileId, player, materialization);
		final var needed = new java.util.HashSet<>(Set.of(57, 1463, 1831, 5591, 5592));
		if (player.getActiveWeaponItem() != null) { needed.add(player.getActiveWeaponItem().getId()); }
		for (var item : player.getInventory().getItems().stream().filter(value -> value.getCount() > 0).sorted(java.util.Comparator.comparingInt(Item::getObjectId)).limit(32).toList())
		{
			final var decision = PhantomLootDispositionPolicy.getInstance().evaluate(player, item, needed, false);
			report.append("C_DISPOSITION\tobject=").append(item.getObjectId()).append(";item=").append(item.getId()).append(";count=").append(item.getCount()).append(";equipped=").append(item.isEquipped()).append(";decision=").append(decision).append(";readOnly=true\n");
		}
	}

	private static void guard(long profileId, Player player, PhantomMaterializationService materialization)
	{
		check(PhantomTask074Lane.enabled() && player != null && player.getAccountName().matches("task074[a-z0-9]*") && !player.isGM(), "OWN074 ordinary actor required.");
		final var lifetime = materialization.find(profileId).orElseThrow();
		check(lifetime.characterObjectId() == player.getObjectId() && lifetime.actionAdmissionOpen() && World.getInstance().getPlayer(player.getObjectId()) == player && player.getNativeWorkOwner() != null && player.getNativeWorkOwner().isCurrent(), "Exact OWN074 materialized lifetime required.");
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

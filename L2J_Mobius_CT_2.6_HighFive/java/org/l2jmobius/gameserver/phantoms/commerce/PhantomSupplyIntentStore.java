/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.commerce;

import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Commerce sees the bound child; historical capture and Decision retain the existing farm root. */
public final class PhantomSupplyIntentStore implements PhantomGoalStore
{
	public static final String COMPONENT = "supply.intent";
	public static final String ATTEMPT_REVISION = "gear.attempt_revision";
	private final PhantomProfileRepository _profiles;
	private final PhantomGoalStore _roots;
	private final PhantomGoalStateCodec _codec = new PhantomGoalStateCodec();
	private final org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore _execution;

	public PhantomSupplyIntentStore(PhantomProfileRepository profiles, PhantomGoalStore roots)
	{
		_profiles = Objects.requireNonNull(profiles); _roots = Objects.requireNonNull(roots);
		_execution = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore(profiles);
	}

	@Override public boolean profileExists(long profileId) { return _roots.profileExists(profileId); }

	@Override public Optional<StoredGoal> load(long profileId)
	{
		if (!sourceMayComplete(profileId, _profiles.findComponent(profileId, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.COMPONENT_TYPE).orElse(null))) { return Optional.empty(); }
		final var root = _roots.load(profileId);
		if (root.isEmpty() || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.orElseThrow().goal().goalType())) { return root; }
		return intent(profileId).filter(child -> bound(child.goal(), root.orElseThrow().goal()) && child.goal().status() == PhantomGoalStatus.ACTIVE && child.goal().deadlineEpochMillis() > System.currentTimeMillis());
	}

	public Optional<StoredGoal> intent(long profileId)
	{
		return _profiles.findComponent(profileId, COMPONENT).map(this::decode);
	}

	public StoredGoal saveIntent(long profileId, PhantomGoal root, long expectedRowVersion, PhantomGoal child)
	{
		if (expectedRowVersion < -1) { throw new IllegalArgumentException("Supply expected version must be -1 or nonnegative."); }
		if (!bound(child, root) || !_roots.load(profileId).map(value -> value.goal().equals(root)).orElse(false)) { throw new IllegalArgumentException("Supply root authority changed."); }
		final byte[] payload = _codec.encode(child);
		final var rawRoot = _profiles.findComponent(profileId, org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow();
		if (!_codec.decode(rawRoot.payload()).equals(root)) { throw new java.util.ConcurrentModificationException("Supply root changed before checkpoint."); }
		final var execution = _execution.guardCompletion(profileId, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.solo(profileId));
		final var guards = new java.util.ArrayList<>(execution.present()); guards.add(rawRoot);
		return decode(_profiles.mutateProfileComponentsAtomically(java.util.List.of(new PhantomProfileRepository.ProfileComponentMutation(profileId,
			new PhantomProfileRepository.ComponentMutation(COMPONENT, expectedRowVersion, 1, payload))), guards, execution.absent()).getFirst());
	}

	private static boolean sourceMayComplete(long id, PhantomProfileComponent execution)
	{
		return execution == null || new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.Stored(execution).state()
			.ownerMayComplete(org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.solo(id));
	}

	public static boolean bound(PhantomGoal child, PhantomGoal root)
	{
		return supported(child) && root != null && root.status() == PhantomGoalStatus.ACTIVE && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())
			&& child.goalId() == root.goalId() && Objects.equals(child.subject(), root.subject()) && Binding.parse(child).rootRevision() == root.revision();
	}

	/** Recovery projection only: parent planning revision may advance while the economic child is suspended.
	 * The caller must settle its receipt, validate identity, and commit this projection through the execution recovery CAS. */
	public static PhantomGoal rebindSuspended(PhantomGoal child, PhantomGoal root)
	{
		if (bound(child, root)) { return child; }
		if (!supported(child) || root == null || root.status() != PhantomGoalStatus.ACTIVE || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())
			|| child.goalId() != root.goalId() || !Objects.equals(child.subject(), root.subject())
			|| !org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.paused(child))
		{ throw new IllegalArgumentException("Only an exact suspended child may adopt its continuing farm parent."); }
		final var old = Binding.parse(child);
		if (root.revision() < old.rootRevision()) { throw new IllegalArgumentException("Suspended parent revision cannot move backwards."); }
		final var binding = new Binding(root.revision(), old.actorObjectId(), old.classId(), old.classIndex(), old.weaponObjectId(), old.field(), old.retryAfterEpochMillis(), old.prospectiveWeaponTemplateId(), old.equipmentReserve());
		return new PhantomGoal(child.goalId(), child.goalType(), child.status(), child.subject(), child.target(), child.requiredAmount(), child.currentAmount(), child.acquisitionMethod(), child.validSources(), child.selectedAnchor(), child.purposeKey(), child.priority(), child.riskBudget(), child.expenseBudget(), child.deadlineEpochMillis(), child.constraints(), child.reasonKey(), child.revision(), binding.encode());
	}

	private StoredGoal decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != 1) { throw new IllegalArgumentException("Unknown supply.intent component version."); }
		final var goal = _codec.decode(component.payload());
		if (!supported(goal)) { throw new IllegalArgumentException("Unknown supply intent goal."); }
		Binding.parse(goal);
		return new StoredGoal(goal, component.rowVersion());
	}

	// This view cannot replace a farm root. Explicit ordinary commerce retains its existing store.
	private void requireExplicit(long profileId)
	{
		if (_roots.load(profileId).map(value -> PhantomBackgroundGoalSpec.GOAL_TYPE.equals(value.goal().goalType())).orElse(false)) { throw new IllegalStateException("Supply view cannot mutate a farm root."); }
	}
	@Override public StoredGoal insert(long profileId, PhantomGoal goal) { requireExplicit(profileId); return _roots.insert(profileId, goal); }
	@Override public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
	{
		final var root = _roots.load(profileId).orElse(null);
		if (root == null || !PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goal().goalType())) { return _roots.replace(profileId, expectedRowVersion, goal); }
		final var previous = load(profileId).orElseThrow(() -> new IllegalStateException("No active bound supply operation."));
		final var old = previous.goal();
		if (previous.rowVersion() == expectedRowVersion && validUpgradeProjection(old, goal)) { return saveIntent(profileId, root.goal(), expectedRowVersion, goal); }
		if (previous.rowVersion() != expectedRowVersion || !social(old) || goal.status() != PhantomGoalStatus.COMPLETED || goal.goalId() != old.goalId() || goal.revision() != Math.addExact(old.revision(), 1) || !Objects.equals(old.target(), goal.target()) || !Objects.equals(old.payloadText(), goal.payloadText()) || !old.goalType().equals(goal.goalType())) { throw new IllegalArgumentException("Supply operation completion authority changed."); }
		return saveIntent(profileId, root.goal(), expectedRowVersion, goal);
	}
	@Override public void delete(long profileId, long expectedRowVersion) { requireExplicit(profileId); _roots.delete(profileId, expectedRowVersion); }

	public static boolean social(PhantomGoal goal)
	{
		return goal != null && ("private.store.buy".equals(goal.goalType()) || "private.store.sell".equals(goal.goalType()) || "manufacture.item".equals(goal.goalType())) && goal.goalType().equals(goal.purposeKey());
	}
	private static boolean supported(PhantomGoal goal)
	{
		if (readySa(goal)) { org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.parse(goal, false); return true; }
		if (enchant(goal)) { return org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantGoalSpec.parseStored(goal).maximumAttempts() == 1; }
		if (PhantomInventoryMaintenanceIntent.isMaintenance(goal)) { PhantomInventoryMaintenanceIntent.parse(goal); return true; }
		return goal != null && ((PhantomCommerceDecision.MAINTAIN_SUPPLIES.equals(goal.goalType()) && "supplies.autonomous".equals(goal.purposeKey())) || social(goal));
	}

	public static boolean enchant(PhantomGoal goal) { return goal != null && org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantGoalSpec.GOAL_TYPE.equals(goal.goalType()) && goal.payloadText() != null; }
	public static boolean readySa(PhantomGoal goal) { return goal != null && org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.TYPE.equals(goal.goalType()) && goal.payloadText() != null; }
	public static boolean upgrade(PhantomGoal goal) { return enchant(goal) || readySa(goal); }
	public static long attemptRevision(PhantomGoal goal)
	{
		final long revision = goal.constraints().getOrDefault(ATTEMPT_REVISION, -1L);
		if (!upgrade(goal) || revision < 0 || revision > goal.revision()) { throw new IllegalArgumentException("Missing original upgrade attempt identity."); }
		return revision;
	}

	static boolean validUpgradeProjection(PhantomGoal old, PhantomGoal next)
	{
		if (readySa(old))
		{
			try { return org.l2jmobius.gameserver.phantoms.economy.PhantomReadySa.project(old).equals(next); }
			catch (IllegalArgumentException | ArithmeticException invalid) { return false; }
		}
		if (!enchant(old) || next == null || old.status() != PhantomGoalStatus.ACTIVE) { return false; }
		try
		{
			final var spec = org.l2jmobius.gameserver.phantoms.economy.PhantomEnchantGoalSpec.parse(old);
			if (spec.maximumAttempts() != 1 || spec.attemptsUsed() != 0 || spec.expenseUsed() != 0 || old.expenseBudget() <= 0) { return false; }
			final boolean success = "result.success".equals(next.reasonKey());
			if (!success && !java.util.Set.of("result.safe_failure", "result.blessed_reset", "result.destroyed").contains(next.reasonKey())) { return false; }
			return spec.project(old, success ? spec.desiredLevel() : 0, !"result.destroyed".equals(next.reasonKey()), old.expenseBudget(), next.reasonKey()).equals(next);
		}
		catch (IllegalArgumentException | ArithmeticException invalid) { return false; }
	}

	/** Small typed ownership context inside the existing versioned Goal codec; no second schema/AI. */
	public record Binding(long rootRevision, int actorObjectId, int classId, int classIndex, int weaponObjectId, PhantomNavigationPoint field, long retryAfterEpochMillis, int prospectiveWeaponTemplateId, long equipmentReserve)
	{
		public Binding(long rootRevision, int actorObjectId, int classId, int classIndex, int weaponObjectId, PhantomNavigationPoint field, long retryAfterEpochMillis)
		{
			this(rootRevision, actorObjectId, classId, classIndex, weaponObjectId, field, retryAfterEpochMillis, 0, 0);
		}
		public Binding
		{
			if (rootRevision < 0 || actorObjectId <= 0 || classId < 0 || classId > 255 || classIndex < 0 || classIndex > 255 || weaponObjectId < 0 || field == null || field.instanceId() != 0 || retryAfterEpochMillis < 0 || prospectiveWeaponTemplateId < 0 || equipmentReserve < 0 || prospectiveWeaponTemplateId == 0 && equipmentReserve != 0) { throw new IllegalArgumentException("Invalid supply ownership binding."); }
		}

		public String encode()
		{
			final String text = (prospectiveWeaponTemplateId == 0 ? "s1:" : "s2:") + rootRevision + ':' + actorObjectId + ':' + classId + ':' + classIndex + ':' + weaponObjectId + ':' + field.x() + ':' + field.y() + ':' + field.z() + ':' + retryAfterEpochMillis + (prospectiveWeaponTemplateId == 0 ? "" : ":" + prospectiveWeaponTemplateId + ':' + equipmentReserve);
			if (!PhantomGoal.validPayloadText(text)) { throw new IllegalArgumentException("Supply binding exceeds the existing Goal payload bound."); }
			return text;
		}

		public static Binding parse(PhantomGoal goal)
		{
			if (goal == null || goal.payloadText() == null) { throw new IllegalArgumentException("Missing supply binding."); }
			final String[] fields = goal.payloadText().split(":", -1);
			final boolean preparation = fields.length == 12 && "s2".equals(fields[0]);
			if (!preparation && (fields.length != 10 || !"s1".equals(fields[0]))) { throw new IllegalArgumentException("Unknown supply binding shape/version."); }
			try
			{
				if (preparation && Integer.parseInt(fields[10]) <= 0) { throw new IllegalArgumentException("Future supply binding requires an actual weapon template."); }
				return new Binding(Long.parseLong(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]), new PhantomNavigationPoint(Integer.parseInt(fields[6]), Integer.parseInt(fields[7]), Integer.parseInt(fields[8]), 0), Long.parseLong(fields[9]), preparation ? Integer.parseInt(fields[10]) : 0, preparation ? Long.parseLong(fields[11]) : 0);
			}
			catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid supply binding number.", exception); }
		}

		public boolean matches(Player player)
		{
			return player != null && actorObjectId == player.getObjectId() && classId == player.getActiveClass() && classIndex == player.getClassIndex() && player.getInstanceId() == 0
				&& (weaponObjectId == 0 ? player.getActiveWeaponInstance() == null : player.getActiveWeaponInstance() != null && weaponObjectId == player.getActiveWeaponInstance().getObjectId());
		}
	}
}

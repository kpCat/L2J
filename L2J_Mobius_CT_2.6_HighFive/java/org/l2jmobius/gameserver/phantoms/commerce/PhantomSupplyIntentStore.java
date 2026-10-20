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
	private final PhantomProfileRepository _profiles;
	private final PhantomGoalStore _roots;
	private final PhantomGoalStateCodec _codec = new PhantomGoalStateCodec();

	public PhantomSupplyIntentStore(PhantomProfileRepository profiles, PhantomGoalStore roots)
	{
		_profiles = Objects.requireNonNull(profiles); _roots = Objects.requireNonNull(roots);
	}

	@Override public boolean profileExists(long profileId) { return _roots.profileExists(profileId); }

	@Override public Optional<StoredGoal> load(long profileId)
	{
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
		return decode(expectedRowVersion < 0 ? _profiles.insertComponent(profileId, COMPONENT, 1, payload) : _profiles.updateComponent(profileId, COMPONENT, expectedRowVersion, 1, payload));
	}

	public static boolean bound(PhantomGoal child, PhantomGoal root)
	{
		return supported(child) && root != null && root.status() == PhantomGoalStatus.ACTIVE && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType())
			&& child.goalId() == root.goalId() && Objects.equals(child.subject(), root.subject()) && Binding.parse(child).rootRevision() == root.revision();
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
		if (previous.rowVersion() != expectedRowVersion || !social(old) || goal.status() != PhantomGoalStatus.COMPLETED || goal.goalId() != old.goalId() || goal.revision() != Math.addExact(old.revision(), 1) || !Objects.equals(old.target(), goal.target()) || !Objects.equals(old.payloadText(), goal.payloadText()) || !old.goalType().equals(goal.goalType())) { throw new IllegalArgumentException("Supply operation completion authority changed."); }
		return saveIntent(profileId, root.goal(), expectedRowVersion, goal);
	}
	@Override public void delete(long profileId, long expectedRowVersion) { requireExplicit(profileId); _roots.delete(profileId, expectedRowVersion); }

	public static boolean social(PhantomGoal goal)
	{
		return goal != null && ("private.store.buy".equals(goal.goalType()) || "manufacture.item".equals(goal.goalType())) && goal.goalType().equals(goal.purposeKey());
	}
	private static boolean supported(PhantomGoal goal)
	{
		return goal != null && ((PhantomCommerceDecision.MAINTAIN_SUPPLIES.equals(goal.goalType()) && "supplies.autonomous".equals(goal.purposeKey())) || social(goal));
	}

	/** Small typed ownership context inside the existing versioned Goal codec; no second schema/AI. */
	public record Binding(long rootRevision, int actorObjectId, int classId, int classIndex, int weaponObjectId, PhantomNavigationPoint field, long retryAfterEpochMillis)
	{
		public Binding
		{
			if (rootRevision < 0 || actorObjectId <= 0 || classId < 0 || classId > 255 || classIndex < 0 || classIndex > 255 || weaponObjectId < 0 || field == null || field.instanceId() != 0 || retryAfterEpochMillis < 0) { throw new IllegalArgumentException("Invalid supply ownership binding."); }
		}

		public String encode()
		{
			final String text = "s1:" + rootRevision + ':' + actorObjectId + ':' + classId + ':' + classIndex + ':' + weaponObjectId + ':' + field.x() + ':' + field.y() + ':' + field.z() + ':' + retryAfterEpochMillis;
			if (!PhantomGoal.validPayloadText(text)) { throw new IllegalArgumentException("Supply binding exceeds the existing Goal payload bound."); }
			return text;
		}

		public static Binding parse(PhantomGoal goal)
		{
			if (goal == null || goal.payloadText() == null) { throw new IllegalArgumentException("Missing supply binding."); }
			final String[] fields = goal.payloadText().split(":", -1);
			if (fields.length != 10 || !"s1".equals(fields[0])) { throw new IllegalArgumentException("Unknown supply binding shape/version."); }
			try { return new Binding(Long.parseLong(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]), Integer.parseInt(fields[5]), new PhantomNavigationPoint(Integer.parseInt(fields[6]), Integer.parseInt(fields[7]), Integer.parseInt(fields[8]), 0), Long.parseLong(fields[9])); }
			catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid supply binding number.", exception); }
		}

		public boolean matches(Player player)
		{
			return player != null && actorObjectId == player.getObjectId() && classId == player.getActiveClass() && classIndex == player.getClassIndex() && player.getInstanceId() == 0
				&& (weaponObjectId == 0 ? player.getActiveWeaponInstance() == null : player.getActiveWeaponInstance() != null && weaponObjectId == player.getActiveWeaponInstance().getObjectId());
		}
	}
}

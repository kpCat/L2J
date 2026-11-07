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
	public static final String ROUTE_COMPONENT = "supply.route";
	public static final int ROUTE_SCHEMA = 2;
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
	public boolean sourceMayComplete(long profileId)
	{ return sourceMayComplete(profileId, _profiles.findComponent(profileId, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.COMPONENT_TYPE).orElse(null)); }
	public Optional<RouteProgress> routeProgress(long profileId, PhantomGoal child)
	{
		return _profiles.findComponent(profileId, ROUTE_COMPONENT).map(raw ->
		{
			if (raw.componentSchemaVersion() != 1 && raw.componentSchemaVersion() != ROUTE_SCHEMA) { throw new IllegalArgumentException("Unknown supply route checkpoint schema."); }
			return RouteProgress.decode(raw.payload());
		}).filter(saved -> saved.matches(child));
	}
	/** Charge/checkpoint the existing route before its native effect or cancellation; old grants cannot overwrite a returned owner. */
	public void saveRouteProgress(long profileId, PhantomGoal child, RouteProgress saved, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission.Stamp stamp)
	{
		if (!saved.matches(child)) { throw new IllegalArgumentException("Supply route checkpoint belongs to another child."); }
		final var rawChild = _profiles.findComponent(profileId, COMPONENT).orElseThrow();
		if (!decode(rawChild).goal().equals(child)) { throw new java.util.ConcurrentModificationException("Supply child changed before route checkpoint."); }
		final var execution = _execution.guardCompletion(profileId, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.solo(profileId));
		if (!execution.present().isEmpty())
		{
			final var current = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.Stored(execution.present().getFirst()).state();
			if (stamp == null || stamp.profileId() != profileId || stamp.generation() != current.generation() || !stamp.owner().equals(current.owner()))
			{ throw new java.util.ConcurrentModificationException("Supply route checkpoint has a stale execution generation."); }
		}
		final var old = _profiles.findComponent(profileId, ROUTE_COMPONENT).orElse(null);
		if (old != null && (old.componentSchemaVersion() != 1 && old.componentSchemaVersion() != ROUTE_SCHEMA || !saved.succeeds(RouteProgress.decode(old.payload()))))
		{ throw new java.util.ConcurrentModificationException("Supply route checkpoint would replenish or rewind accepted work."); }
		final var guards = new java.util.ArrayList<>(execution.present()); guards.add(rawChild);
		_profiles.mutateProfileComponentsAtomically(java.util.List.of(new PhantomProfileRepository.ProfileComponentMutation(profileId,
			new PhantomProfileRepository.ComponentMutation(ROUTE_COMPONENT, old == null ? -1 : old.rowVersion(), ROUTE_SCHEMA, saved.encode()))), guards, execution.absent());
	}
	/** Repairs the child-pause/route-publication gap before any resume; no native effect or budget refund is produced. */
	public void freezePausedRoute(long profileId, PhantomGoal child, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionAdmission.Stamp stamp)
	{
		final long pausedAt = child.constraints().getOrDefault("journey.paused", 0L);
		if (pausedAt == 0) { return; }
		final var route = routeProgress(profileId, child).orElse(null);
		if (route != null && !route.paused()) { saveRouteProgress(profileId, child, route.at(Math.max(route.capturedMillis(), pausedAt), true), stamp); }
	}
	public record RouteProgress(long goalId, long childRevision, long journeyStarted, int actorObjectId, int classId, int classIndex, boolean returning,
		java.util.List<PhantomNavigationPoint> destinations, int destinationIndex, int segmentReplans, int localAttempt,
		long elapsedNanos, long remainingNanos, long capturedMillis, boolean paused,
		org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.NativeWalkBudget.Snapshot walk)
	{
		public RouteProgress(long goalId, long childRevision, int actorObjectId, int classId, int classIndex, boolean returning,
			java.util.List<PhantomNavigationPoint> destinations, int destinationIndex, int segmentReplans, int localAttempt, long elapsedNanos, long remainingNanos, long capturedMillis, boolean paused,
			org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.NativeWalkBudget.Snapshot walk)
		{ this(goalId, childRevision, 0, actorObjectId, classId, classIndex, returning, destinations, destinationIndex, segmentReplans, localAttempt, elapsedNanos, remainingNanos, capturedMillis, paused, walk); }
		public RouteProgress
		{
			destinations = java.util.List.copyOf(destinations);
			if (goalId <= 0 || childRevision < 0 || journeyStarted < 0 || actorObjectId <= 0 || classId < 0 || classId > 255 || classIndex < 0 || classIndex > 255
				|| destinations.isEmpty() || destinations.size() > 17 || destinations.stream().anyMatch(p -> p.instanceId() != 0)
				|| destinationIndex < 0 || destinationIndex >= destinations.size() || segmentReplans < 0 || segmentReplans > 4 || localAttempt < -1 || localAttempt > 1024
				|| elapsedNanos < 0 || remainingNanos < 0 || elapsedNanos > java.util.concurrent.TimeUnit.DAYS.toNanos(1)
				|| remainingNanos > java.util.concurrent.TimeUnit.DAYS.toNanos(1) - elapsedNanos || capturedMillis < 0)
			{ throw new IllegalArgumentException("Invalid bounded supply route checkpoint."); }
		}
		public boolean matches(PhantomGoal child)
		{
			if (child == null || child.goalId() != goalId || (journeyStarted == 0 ? child.revision() != childRevision : child.constraints().getOrDefault("journey.started", 0L) != journeyStarted)) { return false; }
			final var binding = Binding.parse(child);
			return binding.actorObjectId() == actorObjectId && binding.classId() == classId && binding.classIndex() == classIndex;
		}
		public RouteProgress at(long nowMillis, boolean pause)
		{
			if (nowMillis < capturedMillis) { throw new IllegalArgumentException("Supply route checkpoint clock moved backwards."); }
			final long delta = paused ? 0 : java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.min(nowMillis - capturedMillis, java.util.concurrent.TimeUnit.DAYS.toMillis(1)));
			return new RouteProgress(goalId, childRevision, journeyStarted, actorObjectId, classId, classIndex, returning, destinations, destinationIndex, segmentReplans, localAttempt,
				Math.min(java.util.concurrent.TimeUnit.DAYS.toNanos(1), elapsedNanos + delta), Math.max(0, remainingNanos - delta), nowMillis, pause, walk == null ? null : walk.elapse(delta));
		}
		public boolean succeeds(RouteProgress old)
		{
			if (old.goalId != goalId || (journeyStarted == 0 || old.journeyStarted == 0 ? old.childRevision != childRevision : old.journeyStarted != journeyStarted)
				|| old.returning != returning || !java.util.Set.copyOf(old.destinations).equals(java.util.Set.copyOf(destinations))) { return true; }
			if (old.actorObjectId != actorObjectId || old.classId != classId || old.classIndex != classIndex || !old.destinations.equals(destinations)
				|| capturedMillis < old.capturedMillis || elapsedNanos < old.elapsedNanos || remainingNanos > old.remainingNanos
				|| destinationIndex < old.destinationIndex || segmentReplans < old.segmentReplans) { return false; }
			return old.walk == null || walk != null && walk.routes() >= old.walk.routes() && walk.chargedDistance() >= old.walk.chargedDistance()
				&& walk.elapsedNanos() >= old.walk.elapsedNanos() && walk.visited().containsAll(old.walk.visited());
		}
		public byte[] encode()
		{
			try
			{
				final var bytes = new java.io.ByteArrayOutputStream();
				try (var out = new java.io.DataOutputStream(bytes))
				{
					out.writeInt(ROUTE_SCHEMA); out.writeLong(goalId); out.writeLong(childRevision); out.writeLong(journeyStarted); out.writeInt(actorObjectId); out.writeInt(classId); out.writeInt(classIndex); out.writeBoolean(returning);
					out.writeInt(destinations.size()); for (var p : destinations) { out.writeInt(p.x()); out.writeInt(p.y()); out.writeInt(p.z()); }
					out.writeInt(destinationIndex); out.writeInt(segmentReplans); out.writeInt(localAttempt); out.writeLong(elapsedNanos); out.writeLong(remainingNanos); out.writeLong(capturedMillis); out.writeBoolean(paused);
					final byte[] work = walk == null ? new byte[0] : walk.encode(); out.writeInt(work.length); out.write(work);
				}
				return bytes.toByteArray();
			}
			catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
		}
		public static RouteProgress decode(byte[] bytes)
		{
			if (bytes == null || bytes.length > 4096) { throw new IllegalArgumentException("Invalid supply route payload bound."); }
			try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes)))
			{
				final int schema = in.readInt(); if (schema != 1 && schema != ROUTE_SCHEMA) { throw new IllegalArgumentException("Unknown supply route payload schema."); }
				final long goal = in.readLong(), revision = in.readLong(), journey = schema == 1 ? 0 : in.readLong(); final int actor = in.readInt(), type = in.readInt(), index = in.readInt(); final boolean returning = in.readBoolean();
				final int count = in.readInt(); if (count < 1 || count > 17) { throw new IllegalArgumentException("Invalid supply route destination count."); }
				final var points = new java.util.ArrayList<PhantomNavigationPoint>(); for (int i = 0; i < count; i++) { points.add(new PhantomNavigationPoint(in.readInt(), in.readInt(), in.readInt(), 0)); }
				final int destination = in.readInt(), replans = in.readInt(), attempt = in.readInt(); final long elapsed = in.readLong(), remaining = in.readLong(), captured = in.readLong(); final boolean paused = in.readBoolean();
				final int length = in.readInt(); if (length < 0 || length > 2048 || length != in.available()) { throw new IllegalArgumentException("Invalid supply WALK payload length."); }
				final var walk = length == 0 ? null : org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel.NativeWalkBudget.Snapshot.decode(in.readNBytes(length));
				return new RouteProgress(goal, revision, journey, actor, type, index, returning, points, destination, replans, attempt, elapsed, remaining, captured, paused, walk);
			}
			catch (java.io.IOException failure) { throw new IllegalArgumentException("Invalid supply route payload.", failure); }
		}
	}

	/** Projection used by the actual Source owner and SQL integration; the Handoff store commits all changes atomically. */
	public org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.ExecutionCheckpoint executionCheckpoint(long profileId, PhantomGoal root, long now, boolean recovery, long retryAfter)
	{ return executionCheckpoint(profileId, root, now, recovery, retryAfter, recovery); }
	public org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.ExecutionCheckpoint executionCheckpoint(long profileId, PhantomGoal root, long now, boolean recovery, long retryAfter, boolean resumeJourney)
	{
		final var execution = _execution.load(profileId).orElse(null);
		if (execution != null && !execution.state().owner().equals(org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.solo(profileId)))
		{ throw new java.util.ConcurrentModificationException("Source checkpoint belongs to another execution owner."); }
		final var rawRoot = _profiles.findComponent(profileId, org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore.COMPONENT_TYPE).orElse(null);
		if (!Objects.equals(rawRoot == null ? null : _codec.decode(rawRoot.payload()), root)) { throw new java.util.ConcurrentModificationException("Source root changed before execution checkpoint."); }
		final var authority = new java.util.ArrayList<PhantomProfileComponent>(); if (rawRoot != null) { authority.add(rawRoot); }
		final var changes = new java.util.ArrayList<PhantomProfileRepository.ComponentMutation>();
		final var rawChild = _profiles.findComponent(profileId, COMPONENT).orElse(null);
		final var rawRoute = _profiles.findComponent(profileId, ROUTE_COMPONENT).orElse(null);
		final var rawReceipt = _profiles.findComponent(profileId, PhantomCommerceReceipt.COMPONENT_TYPE).orElse(null);
		if (rawChild != null) { authority.add(rawChild); } if (rawRoute != null) { authority.add(rawRoute); } if (rawReceipt != null) { authority.add(rawReceipt); }
		final var receipt = rawReceipt == null ? null : PhantomCommerceReceipt.decode(rawReceipt.payload());
		if (receipt != null && (!receipt.state().terminal() || receipt.state() == PhantomCommerceReceipt.State.INCONSISTENT))
		{ throw new IllegalStateException("Source Commerce receipt has not reached a safe terminal checkpoint."); }
		if (rawChild != null)
		{
			final var child = decode(rawChild).goal();
			PhantomGoal next = child;
			if (recovery && (child.status() == PhantomGoalStatus.ACTIVE || PhantomOfflineSupply.returning(child)))
			{
				final boolean continuingParent = root != null && child.goalId() == root.goalId() && Objects.equals(child.subject(), root.subject());
				if (!continuingParent || !(org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.alive(child, now) || PhantomOfflineSupply.returning(child) && child.deadlineEpochMillis() > now))
				{
					if (retryAfter <= now) { throw new IllegalArgumentException("Retired child requires its owner's finite retry policy."); }
					final var old = Binding.parse(child);
					final var binding = new Binding(old.rootRevision(), old.actorObjectId(), old.classId(), old.classIndex(), old.weaponObjectId(), old.field(), retryAfter, old.prospectiveWeaponTemplateId(), old.equipmentReserve());
					next = executionProjection(child, continuingParent ? PhantomGoalStatus.FAILED : PhantomGoalStatus.ABANDONED, Math.incrementExact(child.revision()), binding,
						continuingParent ? "supplies.execution.expired" : "supplies.execution.parent_replaced");
				}
				else if (root.status() == PhantomGoalStatus.ACTIVE && PhantomBackgroundGoalSpec.GOAL_TYPE.equals(root.goalType()))
				{
					if (social(child))
					{
						final var old = Binding.parse(child);
						final var binding = new Binding(root.revision(), old.actorObjectId(), old.classId(), old.classIndex(), old.weaponObjectId(), old.field(), old.retryAfterEpochMillis(), old.prospectiveWeaponTemplateId(), old.equipmentReserve());
						next = executionProjection(child, child.status(), child.revision(), binding, child.reasonKey());
					}
					else
					{
						next = rebindSuspended(PhantomOfflineSupply.pause(child, now), root);
						if (resumeJourney) { next = org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.resume(next, now); }
					}
					if (receipt != null && receipt.state() == PhantomCommerceReceipt.State.ABORTED && receipt.goalId() == child.goalId() && receipt.goalRevision() == child.revision())
					{ next = executionProjection(next, next.status(), Math.incrementExact(next.revision()), Binding.parse(next), "supplies.execution.resume_aborted"); }
				}
				else { next = PhantomOfflineSupply.pause(child, now); }
			}
			else if (!recovery) { next = PhantomOfflineSupply.pause(child, now); }
			if (!next.equals(child)) { changes.add(new PhantomProfileRepository.ComponentMutation(COMPONENT, rawChild.rowVersion(), 1, _codec.encode(next))); }
			if (rawRoute != null)
			{
				if (rawRoute.componentSchemaVersion() != 1 && rawRoute.componentSchemaVersion() != ROUTE_SCHEMA) { throw new IllegalArgumentException("Unknown supply route checkpoint schema."); }
				var route = RouteProgress.decode(rawRoute.payload());
				if (route.matches(child) && next.status() == PhantomGoalStatus.ACTIVE)
				{
					final long childPaused = child.constraints().getOrDefault("journey.paused", 0L);
					if (!route.paused() && childPaused > 0) { route = route.at(Math.max(route.capturedMillis(), Math.min(now, childPaused)), true); }
					final var aged = route.at(now, !recovery || org.l2jmobius.gameserver.phantoms.decision.PhantomJourneyLifetime.paused(next));
					final var updated = new RouteProgress(aged.goalId(), next.revision(), aged.journeyStarted(), aged.actorObjectId(), aged.classId(), aged.classIndex(), aged.returning(), aged.destinations(), aged.destinationIndex(), aged.segmentReplans(), aged.localAttempt(), aged.elapsedNanos(), aged.remainingNanos(), aged.capturedMillis(), aged.paused(), aged.walk());
					changes.add(new PhantomProfileRepository.ComponentMutation(ROUTE_COMPONENT, rawRoute.rowVersion(), ROUTE_SCHEMA, updated.encode()));
				}
			}
		}
		return new org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine.ExecutionCheckpoint(changes,
			java.util.List.of(org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore.COMPONENT_TYPE, COMPONENT, ROUTE_COMPONENT, PhantomCommerceReceipt.COMPONENT_TYPE), authority);
	}
	public void checkpointBeforeStore(long profileId, PhantomGoal root, long now)
	{
		final var proof = executionCheckpoint(profileId, root, now, false, now);
		if (proof.changes().isEmpty()) { return; }
		final var execution = _execution.guardCompletion(profileId, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.solo(profileId));
		final var guards = new java.util.ArrayList<>(proof.authority()); guards.addAll(execution.present());
		_profiles.mutateProfileComponentsAtomically(proof.changes().stream().map(change -> new PhantomProfileRepository.ProfileComponentMutation(profileId, change)).toList(), guards, execution.absent());
	}
	private static PhantomGoal executionProjection(PhantomGoal child, PhantomGoalStatus status, long revision, Binding binding, String reason)
	{
		return new PhantomGoal(child.goalId(), child.goalType(), status, child.subject(), child.target(), child.requiredAmount(), child.currentAmount(), child.acquisitionMethod(), child.validSources(), child.selectedAnchor(), child.purposeKey(), child.priority(), child.riskBudget(), child.expenseBudget(), child.deadlineEpochMillis(), child.constraints(), reason, revision, binding.encode());
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

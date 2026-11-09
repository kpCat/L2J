/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.decision;

import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;

public final class PhantomGoalStateStore implements PhantomGoalStore
{
	public static final class PublicationPending extends ConcurrentModificationException
	{
		private static final long serialVersionUID = 1L;

		private PublicationPending()
		{
			super("Goal publication awaits REQUEST/YIELD/CHECKPOINT/TRANSFER.");
		}
	}

	public static final String COMPONENT_TYPE = "goal.runtime";
	public static final int COMPONENT_SCHEMA_VERSION = 1;
	private final PhantomProfileRepository _repository;
	private final PhantomGoalStateCodec _codec;
	private PhantomExecutionHandoffStore _execution;
	private PhantomExecutionAdmission _admission;
	private java.util.function.LongSupplier _clock;
	private java.util.function.LongConsumer _publicationWake = _ -> { };
	public void installExecutionPublication(PhantomExecutionHandoffStore execution, PhantomExecutionAdmission admission, java.util.function.LongSupplier clock)
	{ installExecutionPublication(execution, admission, clock, _ -> { }); }
	public void installExecutionPublication(PhantomExecutionHandoffStore execution, PhantomExecutionAdmission admission, java.util.function.LongSupplier clock, java.util.function.LongConsumer wake)
	{
		if (_execution != null) { throw new IllegalStateException("Goal publication owner already installed."); }
		_execution = Objects.requireNonNull(execution); _admission = Objects.requireNonNull(admission); _clock = Objects.requireNonNull(clock); _publicationWake = Objects.requireNonNull(wake);
	}

	public PhantomGoalStateStore(PhantomProfileRepository repository)
	{
		this(repository, new PhantomGoalStateCodec());
	}

	public PhantomGoalStateStore(PhantomProfileRepository repository, PhantomGoalStateCodec codec)
	{
		_repository = Objects.requireNonNull(repository, "Profile repository must not be null.");
		_codec = Objects.requireNonNull(codec, "Goal codec must not be null.");
	}

	@Override
	public boolean profileExists(long profileId)
	{
		return _repository.find(profileId).isPresent();
	}

	@Override
	public Optional<StoredGoal> load(long profileId)
	{
		return _repository.findComponent(profileId, COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public StoredGoal insert(long profileId, PhantomGoal goal)
	{
		if (_execution != null) { return decode(mutateWithComponents(profileId, -1, goal, java.util.List.of()).getFirst()); }
		return decode(_repository.insertComponent(profileId, COMPONENT_TYPE, COMPONENT_SCHEMA_VERSION, _codec.encode(goal)));
	}

	@Override
	public StoredGoal replace(long profileId, long expectedRowVersion, PhantomGoal goal)
	{
		try
		{
			if (_execution != null) { return decode(mutateWithComponents(profileId, expectedRowVersion, goal, java.util.List.of()).getFirst()); }
			return decode(_repository.updateComponent(profileId, COMPONENT_TYPE, expectedRowVersion, COMPONENT_SCHEMA_VERSION, _codec.encode(goal)));
		}
		catch (ConcurrentModificationException exception)
		{
			final StoredGoal current = load(profileId).orElse(null);
			if ((current != null) && "acquire.item".equals(goal.goalType()) && (goal.status() == PhantomGoalStatus.COMPLETED) && (current.goal().goalId() == goal.goalId()) && (current.goal().revision() < Long.MAX_VALUE) && ((current.goal().revision() + 1) == goal.revision()) && (current.goal().status() == PhantomGoalStatus.COMPLETED) && (current.goal().currentAmount() == current.goal().requiredAmount()))
			{
				return current;
			}
			throw exception;
		}
	}

	@Override
	public void delete(long profileId, long expectedRowVersion)
	{
		if (_execution != null) { mutateWithComponents(profileId, expectedRowVersion, null, java.util.List.of()); return; }
		_repository.deleteComponent(profileId, COMPONENT_TYPE, expectedRowVersion);
	}

	/** Companion effects are published by the same transfer CAS as the root, never before the old owner yields. */
	public java.util.List<PhantomProfileComponent> mutateWithComponents(long id, long expectedVersion, PhantomGoal replacement, java.util.List<ComponentMutation> companions)
	{
		if (companions.size() > 2 || companions.stream().anyMatch(c -> c.componentType().startsWith("goal.") || c.componentType().equals(PhantomExecutionHandoffStore.COMPONENT_TYPE)))
		{ throw new IllegalArgumentException("Invalid Goal publication companions."); }
		final var root = _repository.findComponent(id, COMPONENT_TYPE).orElse(null);
		if ((root == null ? -1 : root.rowVersion()) != expectedVersion) { throw new ConcurrentModificationException("Goal publication root changed."); }
		if (_execution == null)
		{
			final var changes = new java.util.ArrayList<>(companions); changes.add(componentMutation(expectedVersion, Objects.requireNonNull(replacement)));
			return _repository.mutateComponentsAtomically(id, changes.stream().sorted(java.util.Comparator.comparing(ComponentMutation::componentType)).toList());
		}
		final var execution = _execution.load(id).orElse(null);
		final var previous = root == null ? null : decode(root).goal();
		final boolean continuation = previous != null && replacement != null && previous.goalId() == replacement.goalId()
			&& previous.goalType().equals(replacement.goalType()) && previous.subject().equals(replacement.subject());
		if (continuation && execution != null)
		{
			try (var permit = _admission.tryAcquireGoalContinuation(id, execution.state().owner()))
			{
				if (permit == null || !_admission.current(id, execution.component().rowVersion(), execution.state())) { throw new ConcurrentModificationException("Goal continuation owner is not current."); }
				final var activity = _repository.findComponent(id, org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.ACTIVITY_COMPONENT_TYPE).orElse(null);
				if (activity != null && org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.ActivityRoot.decode(activity.payload()).pending()
					&& execution.state().phase() == org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.Phase.ACTIVE)
				{ throw new ConcurrentModificationException("Normal root continuation waits for activity publication; completion retains REQUEST/RECOVER authority."); }
				final var changes = new java.util.ArrayList<>(companions); changes.add(componentMutation(expectedVersion, replacement));
				final var guards = new java.util.ArrayList<>(java.util.List.of(root, execution.component()));
				if (activity != null) { guards.add(activity); }
				return _repository.mutateProfileComponentsAtomically(changes.stream().map(c -> new PhantomProfileRepository.ProfileComponentMutation(id, c)).toList(), guards,
					activity == null ? java.util.List.of(new PhantomProfileRepository.ComponentAbsence(id, org.l2jmobius.gameserver.phantoms.party.PhantomLogicalParty.ACTIVITY_COMPONENT_TYPE)) : java.util.List.of());
			}
		}
		if (!_admission.permitsIntentRequest(id)) { throw new ConcurrentModificationException("Stale execution callback cannot request a new Goal."); }
		if (!stagePublication(id, root, replacement, companions, execution))
		{ throw new ConcurrentModificationException("A different Goal publication is pending."); }
		_publicationWake.accept(id);
		throw new PublicationPending();
	}
	/** An activity change is an intent transfer even when it retains the ordinary root's ID/type. */
	public void requestPublication(long id, long expectedVersion, PhantomGoal replacement, java.util.List<ComponentMutation> companions)
	{
		if (_execution == null || !_admission.permitsIntentRequest(id)) { throw new ConcurrentModificationException("Publication requires the installed execution protocol."); }
		if (companions.size() > 2 || companions.stream().anyMatch(c -> c.componentType().startsWith("goal.") || c.componentType().equals(PhantomExecutionHandoffStore.COMPONENT_TYPE)))
		{ throw new IllegalArgumentException("Invalid Goal publication companions."); }
		final var root = _repository.findComponent(id, COMPONENT_TYPE).orElse(null);
		if ((root == null ? -1 : root.rowVersion()) != expectedVersion) { throw new ConcurrentModificationException("Publication baseline changed."); }
		stagePublication(id, root, Objects.requireNonNull(replacement), companions, _execution.load(id).orElseThrow());
		_publicationWake.accept(id);
	}
	private boolean stagePublication(long id, PhantomProfileComponent root, PhantomGoal replacement, java.util.List<ComponentMutation> companions, PhantomExecutionHandoffStore.Stored execution)
	{
		final long now = _clock.getAsLong(); final var current = publication(id).orElse(null);
		final byte[] payload = replacement == null ? null : _codec.encode(replacement);
		final var descriptors = companions.stream().map(c -> new PhantomGoalPublication.Companion(c.componentType(), c.expectedRowVersion(), c.componentSchemaVersion(), PhantomGoalPublication.digest(c.payload()))).toList();
		final long rootVersion = root == null ? -1 : root.rowVersion();
		final String goalHash = payload == null ? "" : PhantomGoalPublication.digest(payload);
		if (current != null)
		{
			final var pending = current.state();
			if (pending.status() == PhantomGoalPublication.Status.PENDING && now < pending.deadline())
			{
				return pending.beforeGoalVersion() == rootVersion && pending.deletion() == (replacement == null)
					&& pending.goalHash().equals(goalHash) && pending.companions().equals(descriptors);
			}
		}
		final var request = new PhantomGoalPublication(PhantomGoalPublication.Status.PENDING, rootVersion, now, Math.addExact(now, 30_000),
			replacement == null, goalHash, descriptors, 0);
		final var changes = new java.util.ArrayList<PhantomProfileRepository.ProfileComponentMutation>();
		changes.add(new PhantomProfileRepository.ProfileComponentMutation(id, new ComponentMutation(PhantomGoalPublication.COMPONENT_TYPE, current == null ? -1 : current.component().rowVersion(), PhantomGoalPublication.SCHEMA_VERSION, request.encode())));
		if (payload != null) { changes.add(staged(id, PhantomGoalPublication.GOAL_COMPONENT, COMPONENT_SCHEMA_VERSION, payload)); }
		for (int i = 0; i < companions.size(); i++) { changes.add(staged(id, PhantomGoalPublication.companionComponent(i), companions.get(i).componentSchemaVersion(), companions.get(i).payload())); }
		if (root != null && java.util.Set.of(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.GOAL_TYPE,
			org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE,
			org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec.GOAL_TYPE).contains(decode(root).goal().goalType()))
		{ changes.add(staged(id, PhantomGoalPublication.NATIVE_ROOT_COMPONENT, COMPONENT_SCHEMA_VERSION, root.payload())); }
		final var guards = new java.util.ArrayList<PhantomProfileComponent>(); final var absent = new java.util.ArrayList<PhantomProfileRepository.ComponentAbsence>();
		if (root == null) { absent.add(new PhantomProfileRepository.ComponentAbsence(id, COMPONENT_TYPE)); } else { guards.add(root); }
		if (current != null) { guards.add(current.component()); }
		if (execution == null) { absent.add(new PhantomProfileRepository.ComponentAbsence(id, PhantomExecutionHandoffStore.COMPONENT_TYPE)); } else { guards.add(execution.component()); }
		_repository.mutateProfileComponentsAtomically(changes, guards, absent);
		return true;
	}
	private PhantomProfileRepository.ProfileComponentMutation staged(long id, String type, int schema, byte[] payload)
	{
		final var old = _repository.findComponent(id, type).orElse(null);
		return new PhantomProfileRepository.ProfileComponentMutation(id, new ComponentMutation(type, old == null ? -1 : old.rowVersion(), schema, payload));
	}
	public record StoredPublication(PhantomProfileComponent component)
	{
		public StoredPublication
		{
			if (!component.componentType().equals(PhantomGoalPublication.COMPONENT_TYPE) || component.componentSchemaVersion() != PhantomGoalPublication.SCHEMA_VERSION) { throw new IllegalArgumentException("Unknown Goal publication component."); }
			PhantomGoalPublication.decode(component.payload());
		}
		public PhantomGoalPublication state() { return PhantomGoalPublication.decode(component.payload()); }
	}
	public java.util.Optional<StoredPublication> publication(long id)
	{ return _repository.findComponent(id, PhantomGoalPublication.COMPONENT_TYPE).map(StoredPublication::new); }
	public PhantomDecisionEngine.ExecutionCheckpoint withPublicationCheckpoint(long id, PhantomDecisionEngine.ExecutionCheckpoint checkpoint)
	{
		final var types = new java.util.LinkedHashSet<>(checkpoint.dependencyTypes());
		final var guards = new java.util.LinkedHashSet<>(checkpoint.authority());
		for (String type : java.util.List.of(PhantomGoalPublication.COMPONENT_TYPE, PhantomGoalPublication.NATIVE_ROOT_COMPONENT))
		{ types.add(type); _repository.findComponent(id, type).ifPresent(guards::add); }
		return new PhantomDecisionEngine.ExecutionCheckpoint(checkpoint.changes(), java.util.List.copyOf(types), java.util.List.copyOf(guards));
	}
	public java.util.Optional<StoredPublication> pendingPublication(long id, long now)
	{
		final var stored = publication(id).orElse(null); if (stored == null || stored.state().status() != PhantomGoalPublication.Status.PENDING) { return java.util.Optional.empty(); }
		final var root = _repository.findComponent(id, COMPONENT_TYPE).orElse(null);
		boolean current = (root == null ? -1 : root.rowVersion()) == stored.state().beforeGoalVersion();
		if (now >= stored.state().deadline()) { current = false; }
		else try
		{
			if (!stored.state().deletion())
			{
				final var replacement = decode(requireStaged(id, PhantomGoalPublication.GOAL_COMPONENT, COMPONENT_SCHEMA_VERSION, stored.state().goalHash())).goal();
				current &= replacement.status() != PhantomGoalStatus.ACTIVE || replacement.deadlineEpochMillis() == 0 || replacement.deadlineEpochMillis() > now;
			}
			for (int i = 0; i < stored.state().companions().size(); i++)
			{
				final var companion = stored.state().companions().get(i); requireStaged(id, PhantomGoalPublication.companionComponent(i), companion.schemaVersion(), companion.payloadHash());
				final var before = _repository.findComponent(id, companion.componentType()).orElse(null);
				current &= (before == null ? -1 : before.rowVersion()) == companion.expectedVersion();
			}
		}
		catch (ConcurrentModificationException | IllegalArgumentException invalidPublication) { current = false; }
		if (current && now < stored.state().deadline()) { return java.util.Optional.of(stored); }
		_repository.updateComponent(id, PhantomGoalPublication.COMPONENT_TYPE, stored.component().rowVersion(), PhantomGoalPublication.SCHEMA_VERSION, stored.state().cancelled().encode());
		return java.util.Optional.empty();
	}
	public PhantomDecisionEngine.ExecutionAuthority publicationAuthority(PhantomExecutionHandoffStore.Stored execution, long now)
	{
		final long id = execution.profileId(); final var stored = pendingPublication(id, now).orElseThrow(() -> new ConcurrentModificationException("Goal publication expired or cancelled."));
		final var request = stored.state();
		if (!execution.state().recipient().equals(PhantomExecutionHandoff.solo(id)) || !execution.state().requestId().equals(request.requestId())) { throw new ConcurrentModificationException("Another execution request owns Goal publication."); }
		final var guards = new java.util.ArrayList<PhantomProfileComponent>(); guards.add(stored.component());
		final var absent = new java.util.ArrayList<PhantomProfileRepository.ComponentAbsence>(); final var removals = new java.util.ArrayList<PhantomProfileComponent>();
		final var root = _repository.findComponent(id, COMPONENT_TYPE).orElse(null);
		if ((root == null ? -1 : root.rowVersion()) != request.beforeGoalVersion()) { throw new ConcurrentModificationException("Requested Goal parent changed."); }
		if (root == null) { absent.add(new PhantomProfileRepository.ComponentAbsence(id, COMPONENT_TYPE)); } else { guards.add(root); }
		final var changes = new java.util.ArrayList<ComponentMutation>();
		if (request.deletion()) { removals.add(Objects.requireNonNull(root)); }
		else
		{
			final var goal = requireStaged(id, PhantomGoalPublication.GOAL_COMPONENT, COMPONENT_SCHEMA_VERSION, request.goalHash()); guards.add(goal);
			changes.add(new ComponentMutation(COMPONENT_TYPE, request.beforeGoalVersion(), COMPONENT_SCHEMA_VERSION, goal.payload()));
		}
		for (int i = 0; i < request.companions().size(); i++)
		{
			final var companion = request.companions().get(i); final var payload = requireStaged(id, PhantomGoalPublication.companionComponent(i), companion.schemaVersion(), companion.payloadHash()); guards.add(payload);
			final var before = _repository.findComponent(id, companion.componentType()).orElse(null);
			if ((before == null ? -1 : before.rowVersion()) != companion.expectedVersion()) { throw new ConcurrentModificationException("Goal publication companion changed."); }
			if (before == null) { absent.add(new PhantomProfileRepository.ComponentAbsence(id, companion.componentType())); } else { guards.add(before); }
			changes.add(new ComponentMutation(companion.componentType(), companion.expectedVersion(), companion.schemaVersion(), payload.payload()));
		}
		changes.add(new ComponentMutation(PhantomGoalPublication.COMPONENT_TYPE, stored.component().rowVersion(), PhantomGoalPublication.SCHEMA_VERSION, request.committed(Math.incrementExact(execution.state().generation())).encode()));
		return new PhantomDecisionEngine.ExecutionAuthority(new PhantomDecisionEngine.ExecutionReadiness(true, "execution.goal_publication", now), guards, changes, removals, absent);
	}
	private PhantomProfileComponent requireStaged(long id, String type, int schema, String hash)
	{
		final var component = _repository.findComponent(id, type).orElseThrow(() -> new ConcurrentModificationException("Goal publication payload disappeared."));
		if (component.componentSchemaVersion() != schema || !PhantomGoalPublication.digest(component.payload()).equals(hash)) { throw new ConcurrentModificationException("Goal publication payload changed."); }
		return component;
	}
	public static boolean nativeRootType(PhantomGoal goal)
	{
		return goal != null && java.util.Set.of(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.GOAL_TYPE,
			org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE,
			org.l2jmobius.gameserver.phantoms.acquisition.PhantomAcquisitionGoalSpec.GOAL_TYPE).contains(goal.goalType());
	}
	/** Accepted ledger authority only. This does not authorize a decision, reward, route or new native action. */
	public record RetainedNativeGoal(PhantomProfileComponent lineage, PhantomProfileComponent execution, PhantomProfileComponent intent)
	{
		public RetainedNativeGoal
		{
			if (lineage == null || execution == null || !lineage.componentType().equals(PhantomGoalPublication.NATIVE_ROOT_COMPONENT)
				|| lineage.componentSchemaVersion() != COMPONENT_SCHEMA_VERSION || lineage.profileId() != execution.profileId()
				|| intent != null && (intent.profileId() != lineage.profileId() || !intent.componentType().equals(COMPONENT_TYPE) || intent.componentSchemaVersion() != COMPONENT_SCHEMA_VERSION))
			{ throw new IllegalArgumentException("Invalid retained native Goal identity."); }
			final var codec = new PhantomGoalStateCodec(); final var state = new PhantomExecutionHandoffStore.Stored(execution).state();
			if (!nativeRootType(codec.decode(lineage.payload())) || intent != null && nativeRootType(codec.decode(intent.payload()))
				|| state.dependencies().stream().noneMatch(d -> d.componentType().equals(lineage.componentType()) && d.schema() == lineage.componentSchemaVersion()
					&& d.rowVersion() == lineage.rowVersion() && d.sha256().equals(PhantomGoalPublication.digest(lineage.payload()))))
			{ throw new ConcurrentModificationException("Retained native Goal has no acknowledged execution checkpoint."); }
		}
		public PhantomGoal goal() { return new PhantomGoalStateCodec().decode(lineage.payload()); }
	}
	public RetainedNativeGoal retainedNativeGoal(long id, PhantomGoal expectedIntent)
	{
		final var intent = _repository.findComponent(id, COMPONENT_TYPE).orElse(null);
		if (!Objects.equals(intent == null ? null : decode(intent).goal(), expectedIntent)) { throw new ConcurrentModificationException("Native intent changed before retained ledger lookup."); }
		return new RetainedNativeGoal(_repository.findComponent(id, PhantomGoalPublication.NATIVE_ROOT_COMPONENT).orElseThrow(() -> new IllegalStateException("Accepted native ledger root is absent.")),
			_repository.findComponent(id, PhantomExecutionHandoffStore.COMPONENT_TYPE).orElseThrow(() -> new IllegalStateException("Native execution checkpoint is absent.")), intent);
	}

	public ComponentMutation componentMutation(long expectedRowVersion, PhantomGoal goal)
	{
		return new ComponentMutation(COMPONENT_TYPE, expectedRowVersion, COMPONENT_SCHEMA_VERSION, _codec.encode(Objects.requireNonNull(goal)));
	}

	public StoredGoal decodeComponent(PhantomProfileComponent component)
	{
		return decode(component);
	}

	private StoredGoal decode(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != COMPONENT_SCHEMA_VERSION)
		{
			throw new IllegalArgumentException("Unknown goal.runtime component schema version.");
		}
		return new StoredGoal(_codec.decode(component.payload()), component.rowVersion());
	}
}

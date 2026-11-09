/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/**
 * Optimistic party.state adapter over the existing profile component table.
 */
public final class PhantomPartyStore implements PhantomPartyPersistencePort
{
	private static final int MAX_PAGE_SIZE = 256;
	private final PhantomProfileRepository _profiles;
	private final PhantomPartyStateCodec _codec;

	public PhantomPartyStore(PhantomProfileRepository profiles)
	{
		_profiles = Objects.requireNonNull(profiles);
		_codec = new PhantomPartyStateCodec();
	}

	@Override
	public Optional<StoredPartyState> load(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomPartyModel.COMPONENT_TYPE).map(this::decode);
	}

	@Override
	public StoredPartyState save(long profileId, long expectedRowVersion, PhantomPartyModel.PartyState state)
	{
		final byte[] payload = _codec.encode(state);
		final PhantomProfileComponent component = expectedRowVersion < 0 //
			? _profiles.insertComponent(profileId, PhantomPartyModel.COMPONENT_TYPE, PhantomPartyModel.SCHEMA_VERSION, payload) //
			: _profiles.updateComponent(profileId, PhantomPartyModel.COMPONENT_TYPE, expectedRowVersion, PhantomPartyModel.SCHEMA_VERSION, payload);
		return decode(component);
	}

	@Override
	public List<StoredPartyState> loadManagedAfter(long exclusiveProfileId, int pageSize)
	{
		if ((exclusiveProfileId < 0) || (pageSize < 1) || (pageSize > MAX_PAGE_SIZE))
		{
			throw new IllegalArgumentException("Party state page request is outside bounds.");
		}
		return _profiles.listManagedAfter(PhantomPartyModel.COMPONENT_TYPE, exclusiveProfileId, pageSize).stream().map(managed -> decode(managed.component())).toList();
	}

	private StoredPartyState decode(PhantomProfileComponent component)
	{
		if ((component.componentSchemaVersion() != 1) && (component.componentSchemaVersion() != PhantomPartyModel.SCHEMA_VERSION))
		{
			throw new IllegalArgumentException("Unknown party.state schema version.");
		}
		final var state = _codec.decode(component.payload());
		if ((state.command() != null) && (state.command().actor().profileId() != component.profileId()))
		{
			throw new IllegalArgumentException("Companion command belongs to another profile.");
		}
		return new StoredPartyState(component.profileId(), component.rowVersion(), state);
	}

	@Override public Optional<StoredLogicalGroup> loadLogicalGroup(long leader)
	{ return _profiles.findComponent(leader, PhantomLogicalParty.COMPONENT_TYPE).map(this::decodeLogicalGroup); }
	@Override public List<StoredLogicalGroup> loadLogicalGroupsAfter(long cursor, int limit)
	{ return _profiles.listManagedAfter(PhantomLogicalParty.COMPONENT_TYPE, cursor, limit).stream().map(row -> decodeLogicalGroup(row.component())).toList(); }
	private StoredLogicalGroup decodeLogicalGroup(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != 1 && component.componentSchemaVersion() != PhantomLogicalParty.SCHEMA_VERSION) { throw new IllegalArgumentException("Unknown logical group schema."); }
		final var group = PhantomLogicalParty.decode(component.payload());
		if (group.leaderProfileId() != component.profileId()) { throw new IllegalArgumentException("Foreign logical group aggregate."); }
		return new StoredLogicalGroup(component.rowVersion(), group);
	}
	@Override public Optional<StoredLogicalClaim> loadLogicalClaim(long id)
	{
		return _profiles.findComponent(id, PhantomLogicalParty.CLAIM_COMPONENT_TYPE).map(component ->
		{
			if (component.componentSchemaVersion() != 1 && component.componentSchemaVersion() != PhantomLogicalParty.SCHEMA_VERSION) { throw new IllegalArgumentException("Unknown logical claim schema."); }
			try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(component.payload())))
			{
				final long leader = in.readLong(); final String group = in.readUTF(); final long generation = in.readLong();
				final var claim = new StoredLogicalClaim(id, leader, group, generation, component.rowVersion(), component.componentSchemaVersion() == 1 || in.readBoolean());
				if (in.available() != 0) { throw new IllegalArgumentException("Trailing logical claim payload."); }
				return claim;
			}
			catch (java.io.IOException failure) { throw new IllegalArgumentException("Invalid logical claim payload.", failure); }
		});
	}
	private byte[] logicalClaim(PhantomLogicalParty group, boolean active)
	{
		try
		{
			final var bytes = new java.io.ByteArrayOutputStream();
			try (var out = new java.io.DataOutputStream(bytes)) { out.writeLong(group.leaderProfileId()); out.writeUTF(group.groupId()); out.writeLong(group.generation()); out.writeBoolean(active); }
			return bytes.toByteArray();
		}
		catch (java.io.IOException failure) { throw new IllegalArgumentException("Cannot encode logical claim.", failure); }
	}
	@Override public StoredLogicalGroup commitLogicalGroup(PhantomLogicalParty group, long expected, java.util.Map<Long, Long> versions)
	{ return commitLogicalGroup(group, expected, versions, List.of()); }

	@Override public StoredLogicalGroup commitLogicalGroup(PhantomLogicalParty group, long expected, java.util.Map<Long, Long> versions, List<PhantomProfileComponent> guards)
	{
		final var prior = loadLogicalGroup(group.leaderProfileId()).orElse(null);
		if ((prior == null ? -1 : prior.rowVersion()) != expected) { throw new java.util.ConcurrentModificationException("Stale formation aggregate."); }
		if (group.phase() != PhantomLogicalParty.Phase.AGREED || group.backgroundCursor() != 0 || versions.size() != group.members().size()
			|| prior != null && (prior.group().active() || group.generation() != Math.addExact(prior.group().generation(), 1)))
		{ throw new IllegalArgumentException("Formation requires a fresh agreement or the next released generation."); }
		final var mutations = new java.util.ArrayList<PhantomProfileRepository.ProfileComponentMutation>();
		mutations.add(new PhantomProfileRepository.ProfileComponentMutation(group.leaderProfileId(),
			new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.COMPONENT_TYPE, expected, PhantomLogicalParty.SCHEMA_VERSION, group.encode())));
		final byte[] claim = logicalClaim(group, true);
		for (var member : group.members())
		{
			final var before = loadLogicalClaim(member.profileId()).orElse(null);
			final long version = before == null ? -1 : before.rowVersion();
			if (before != null && before.active() || !Long.valueOf(version).equals(versions.get(member.profileId())))
			{ throw new java.util.ConcurrentModificationException("Logical identity is claimed or its version changed."); }
			mutations.add(new PhantomProfileRepository.ProfileComponentMutation(member.profileId(),
				new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.CLAIM_COMPONENT_TYPE, version, PhantomLogicalParty.SCHEMA_VERSION, claim)));
		}
		final var absentContexts = guards.stream().filter(row -> row.componentType().equals(PhantomBackgroundState.COMPONENT_TYPE))
			.filter(row -> guards.stream().noneMatch(context -> context.profileId() == row.profileId() && context.componentType().equals(PhantomNativeContext.COMPONENT_TYPE)))
			.map(row -> new PhantomProfileRepository.ComponentAbsence(row.profileId(), PhantomNativeContext.COMPONENT_TYPE)).toList();
		return _profiles.mutateProfileComponentsAtomically(mutations, guards, absentContexts).stream().filter(row -> row.componentType().equals(PhantomLogicalParty.COMPONENT_TYPE)).map(this::decodeLogicalGroup).findFirst().orElseThrow();
	}
	@Override public StoredLogicalGroup reviseLogicalGroup(StoredLogicalGroup expected, PhantomLogicalParty replacement)
	{
		final var old = expected.group();
		if (old.activityPending()) { throw new IllegalStateException("Activity publication must settle before claims can change."); }
		if (!loadLogicalGroup(old.leaderProfileId()).filter(expected::equals).isPresent()) { throw new java.util.ConcurrentModificationException("Stale logical membership revision."); }
		if (old.phase() != PhantomLogicalParty.Phase.AGREED && old.phase() != PhantomLogicalParty.Phase.BACKGROUND)
		{ throw new IllegalStateException("Physical projection must drain before logical membership changes."); }
		final boolean terminal = replacement.phase() == PhantomLogicalParty.Phase.DISBANDED;
		if (replacement.leaderProfileId() != old.leaderProfileId() || !replacement.groupId().equals(old.groupId())
			|| (terminal ? replacement.backgroundCursor() != old.backgroundCursor() : replacement.backgroundCursor() != 0) || !replacement.objective().equals(old.objective()) || !replacement.anchorId().equals(old.anchorId())
			|| terminal && (!replacement.members().equals(old.members()) || replacement.generation() != old.generation())
			|| !terminal && (replacement.generation() != Math.addExact(old.generation(), 1) || replacement.phase() != PhantomLogicalParty.Phase.BACKGROUND))
		{ throw new IllegalArgumentException("Invalid logical membership revision."); }
		final var ids = new java.util.TreeSet<Long>(); old.members().forEach(member -> ids.add(member.profileId())); replacement.members().forEach(member -> ids.add(member.profileId()));
		final var mutations = new java.util.ArrayList<PhantomProfileRepository.ProfileComponentMutation>();
		mutations.add(new PhantomProfileRepository.ProfileComponentMutation(old.leaderProfileId(),
			new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.COMPONENT_TYPE, expected.rowVersion(), PhantomLogicalParty.SCHEMA_VERSION, replacement.encode())));
		final var releaseGuards = new java.util.ArrayList<PhantomProfileComponent>();
		if (old.activityReplanRequired() || old.waitReason().equals("EXPIRED_ASSEMBLY")) for (var member : old.members())
		{ if (!activitySolo(member.profileId(), releaseGuards)) { throw new java.util.ConcurrentModificationException("Terminal claims wait for old Group owner release."); } }
		for (long id : ids)
		{
			final var claim = loadLogicalClaim(id).orElse(null);
			if (old.contains(id) ? claim == null || !claim.active() || claim.leaderProfileId() != old.leaderProfileId() || !claim.groupId().equals(old.groupId()) || claim.generation() != old.generation()
				: claim != null && claim.active()) { throw new java.util.ConcurrentModificationException("Foreign or stale logical member claim."); }
			final boolean active = !terminal && replacement.contains(id);
			mutations.add(new PhantomProfileRepository.ProfileComponentMutation(id, new PhantomProfileRepository.ComponentMutation(
				PhantomLogicalParty.CLAIM_COMPONENT_TYPE, claim == null ? -1 : claim.rowVersion(), PhantomLogicalParty.SCHEMA_VERSION, logicalClaim(active ? replacement : old, active))));
		}
		return _profiles.mutateProfileComponentsAtomically(mutations, releaseGuards).stream().filter(row -> row.componentType().equals(PhantomLogicalParty.COMPONENT_TYPE)).map(this::decodeLogicalGroup).findFirst().orElseThrow();
	}
	@Override public StoredLogicalGroup updateLogicalGroup(PhantomLogicalParty group, long expected)
	{
		if (group.phase() == PhantomLogicalParty.Phase.DISBANDED) { throw new IllegalArgumentException("Disband must release claims atomically."); }
		final var before = loadLogicalGroup(group.leaderProfileId()).orElseThrow();
		final var old = before.group();
		if (old.activityPending() || group.activityPending()) { throw new IllegalStateException("Activity fence changes require the guarded revision boundary."); }
		if (before.rowVersion() != expected) { throw new java.util.ConcurrentModificationException("Stale logical group version."); }
		if (group.backgroundCursor() != old.backgroundCursor()) { throw new IllegalArgumentException("Group cursor requires committed member receipt guards."); }
		if (!old.groupId().equals(group.groupId()) || old.generation() != group.generation() || !old.members().equals(group.members())
			|| !old.objective().equals(group.objective()) || !old.anchorId().equals(group.anchorId())
			|| !old.transition(group.phase(), group.backgroundCursor(), group.nextDueMillis(), group.waitReason()).equals(group))
		{ throw new IllegalArgumentException("A phase update cannot replace logical membership or objective."); }
		return decodeLogicalGroup(_profiles.updateComponent(group.leaderProfileId(), PhantomLogicalParty.COMPONENT_TYPE, expected, PhantomLogicalParty.SCHEMA_VERSION, group.encode()));
	}
	@Override public List<PhantomLogicalParty.ActivityRoot> activityRoots(StoredLogicalGroup expected)
	{
		if (!expected.group().activityPending()) { return List.of(); }
		return expected.group().members().stream().map(member ->
		{
			final var row = _profiles.findComponent(member.profileId(), PhantomLogicalParty.ACTIVITY_COMPONENT_TYPE).orElseThrow();
			final var plan = PhantomLogicalParty.ActivityRoot.decode(row.payload());
			if (row.componentSchemaVersion() != 1 || !plan.pending() || plan.profileId() != member.profileId() || !plan.groupId().equals(expected.group().groupId()) || plan.generation() != expected.group().generation())
			{ throw new java.util.ConcurrentModificationException("Stale activity proposal."); }
			return plan;
		}).toList();
	}
	private void requireActivityClaim(PhantomLogicalParty group, long id)
	{
		final var claim = loadLogicalClaim(id).orElseThrow();
		if (!claim.active() || claim.leaderProfileId() != group.leaderProfileId() || !claim.groupId().equals(group.groupId()) || claim.generation() != group.generation())
		{ throw new java.util.ConcurrentModificationException("Activity member claim changed."); }
	}
	private boolean activitySolo(long id, java.util.List<PhantomProfileComponent> guards)
	{
		final var row = _profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.COMPONENT_TYPE).orElse(null);
		if (row == null) { return false; }
		final var execution = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.Stored(row).state();
		if (!execution.owner().equals(org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.solo(id))
			|| execution.phase() != org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff.Phase.ACTIVE || !execution.sourceReady() || execution.dependencies().isEmpty()) { return false; }
		guards.add(row); return true;
	}
	@Override public StoredLogicalGroup beginActivityRevision(StoredLogicalGroup expected, List<PhantomLogicalParty.ActivityRoot> plans, long now)
	{
		final var group = expected.group();
		if (group.phase() != PhantomLogicalParty.Phase.BACKGROUND || !group.activityReplanRequired() || group.activityPending() || plans.size() != group.members().size())
		{ throw new IllegalStateException("Activity revision waits for terminal physical drain."); }
		final var guards = new java.util.ArrayList<PhantomProfileComponent>(); final var changes = new java.util.ArrayList<PhantomProfileRepository.ProfileComponentMutation>();
		org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef source = null;
		for (int i = 0; i < plans.size(); i++)
		{
			final var member = group.members().get(i); final var plan = plans.get(i); final long id = member.profileId();
			if (!plan.pending() || plan.profileId() != id || !plan.groupId().equals(group.groupId()) || plan.generation() != group.generation() || plan.deadline() <= now || plan.deadline() > now + 120_000)
			{ throw new IllegalArgumentException("Activity proposal identity/deadline differs."); }
			requireActivityClaim(group, id);
			if (!activitySolo(id, guards)) { throw new java.util.ConcurrentModificationException("Old execution owner has not released the member."); }
			final var root = _profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow();
			final var previous = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(root.payload());
			final var background = _profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			if (root.rowVersion() != plan.beforeGoalVersion() || previous.goalId() != member.goalId() || plan.goal().goalId() != previous.goalId() || plan.goal().revision() != Math.addExact(previous.revision(), 1)
				|| !plan.goal().subject().equals(previous.subject()) || new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec().decode(background.payload()).state() != org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.READY)
			{ throw new java.util.ConcurrentModificationException("Activity baseline is not settled."); }
			final var spec = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec.parse(plan.goal());
			final var selected = new org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef("background.farm", spec.npcId() + "@" + spec.anchorId());
			if (source != null && !source.equals(selected) || selected.equals(group.objective())) { throw new IllegalArgumentException("Activity proposals do not share a new source."); }
			source = selected;
			final var history = _profiles.findComponent(id, plan.history().componentType()).orElseThrow();
			if (history.rowVersion() != plan.history().expectedRowVersion()) { throw new java.util.ConcurrentModificationException("Activity history changed."); }
			final var before = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec().decode(history.payload());
			final var after = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec().decode(plan.history().payload());
			if (!before.withPlan(after.goalId(), after.goalRevision(), after.planOrdinal(), after.planIdentity(), after.knowledgeGeneration(), after.topologyGeneration()).equals(after)
				|| after.planOrdinal() != Math.addExact(before.planOrdinal(), 1) || after.cursorEpochMinute() != before.cursorEpochMinute() || after.intervalOrdinal() != before.intervalOrdinal() || !after.requestId().equals(before.requestId())
				|| after.goalId() != plan.goal().goalId() || after.goalRevision() != plan.goal().revision())
			{ throw new IllegalArgumentException("Activity metadata cannot award/replay Background intervals."); }
			guards.add(root); guards.add(background); guards.add(history);
			final var prior = _profiles.findComponent(id, PhantomLogicalParty.ACTIVITY_COMPONENT_TYPE).orElse(null);
			changes.add(new PhantomProfileRepository.ProfileComponentMutation(id, new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.ACTIVITY_COMPONENT_TYPE, prior == null ? -1 : prior.rowVersion(), 1, plan.encode())));
		}
		final var pending = group.transition(PhantomLogicalParty.Phase.BACKGROUND, group.backgroundCursor(), now, "ACTIVITY_PENDING");
		changes.add(new PhantomProfileRepository.ProfileComponentMutation(group.leaderProfileId(), new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.COMPONENT_TYPE, expected.rowVersion(), PhantomLogicalParty.SCHEMA_VERSION, pending.encode())));
		return _profiles.mutateProfileComponentsAtomically(changes, guards).stream().filter(row -> row.componentType().equals(PhantomLogicalParty.COMPONENT_TYPE)).map(this::decodeLogicalGroup).findFirst().orElseThrow();
	}
	@Override public Optional<StoredLogicalGroup> finishActivityRevision(StoredLogicalGroup expected, long now, boolean abort)
	{
		final var group = expected.group();
		if (!group.activityPending() || group.phase() != PhantomLogicalParty.Phase.BACKGROUND) { throw new IllegalStateException("No fenced activity revision."); }
		final var plans = activityRoots(expected); final var guards = new java.util.ArrayList<PhantomProfileComponent>();
		if (!abort && plans.stream().anyMatch(plan -> now >= plan.deadline())) { return Optional.empty(); }
		final var absent = new java.util.ArrayList<PhantomProfileRepository.ComponentAbsence>(); final var roots = new java.util.ArrayList<PhantomLogicalParty.Member>();
		for (int i = 0; i < group.members().size(); i++)
		{
			final var member = group.members().get(i); final var plan = plans.get(i); final long id = member.profileId();
			requireActivityClaim(group, id); if (!activitySolo(id, guards)) { return Optional.empty(); }
			final var execution = new org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoffStore.Stored(guards.getLast()).state();
			final var root = _profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore.COMPONENT_TYPE).orElseThrow();
			final var current = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(root.payload());
			final var background = _profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.COMPONENT_TYPE).orElseThrow();
			if (new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec().decode(background.payload()).state() != org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.State.READY) { return Optional.empty(); }
			final var publication = _profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.COMPONENT_TYPE).orElse(null);
			if (publication != null && org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.decode(publication.payload()).status() == org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.Status.PENDING) { return Optional.empty(); }
			// An ACK binds the exact published root/request. ACTIVE root continuation is durably
			// closed by the journal; other root publication must change this guarded execution row.
			if (!abort && (!activityAcknowledges(execution, root) || publication == null || !activityAcknowledges(execution, publication))) { return Optional.empty(); }
			final var history = _profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.COMPONENT_TYPE).orElse(null);
			if (!abort && (!current.equals(plan.goal()) || publication == null || history == null || !java.util.Arrays.equals(history.payload(), plan.history().payload())
				|| org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.decode(publication.payload()).status() != org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.Status.COMMITTED
				|| !org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.decode(publication.payload()).goalHash().equals(
					org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.digest(new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().encode(plan.goal()))))) { return Optional.empty(); }
			if (history == null) { return Optional.empty(); }
			guards.add(history);
			final var commerce = _profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.COMPONENT_TYPE).orElse(null);
			if (commerce != null)
			{
				final var receipt = org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.decode(commerce.payload());
				if (!receipt.state().terminal() || receipt.state() == org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.State.INCONSISTENT) { return Optional.empty(); }
				guards.add(commerce);
			}
			else { absent.add(new PhantomProfileRepository.ComponentAbsence(id, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.COMPONENT_TYPE)); }
			guards.add(background);
			roots.add(new PhantomLogicalParty.Member(id, member.role(), current.goalId(), current.revision()));
		}
		final var source = plans.getFirst().goal().validSources().stream().filter(value -> value.namespace().equals("background.farm")).findFirst().orElseThrow();
		final var next = abort ? group.transition(PhantomLogicalParty.Phase.BACKGROUND, group.backgroundCursor(), now + 300_000, "REPLAN_ACTIVITY_WAIT") : group.activityReplacement(roots, source, now);
		final var changes = new java.util.ArrayList<PhantomProfileRepository.ProfileComponentMutation>();
		changes.add(new PhantomProfileRepository.ProfileComponentMutation(group.leaderProfileId(), new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.COMPONENT_TYPE, expected.rowVersion(), PhantomLogicalParty.SCHEMA_VERSION, next.encode())));
		for (var plan : plans)
		{
			final var proposal = _profiles.findComponent(plan.profileId(), PhantomLogicalParty.ACTIVITY_COMPONENT_TYPE).orElseThrow();
			if (!java.util.Arrays.equals(proposal.payload(), plan.encode())) { throw new java.util.ConcurrentModificationException("Activity proposal changed before settlement."); }
			changes.add(new PhantomProfileRepository.ProfileComponentMutation(plan.profileId(), new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.ACTIVITY_COMPONENT_TYPE, proposal.rowVersion(), 1, plan.settled().encode())));
		}
		if (!abort) for (var member : group.members())
		{
			final var claim = loadLogicalClaim(member.profileId()).orElseThrow();
			changes.add(new PhantomProfileRepository.ProfileComponentMutation(member.profileId(), new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.CLAIM_COMPONENT_TYPE, claim.rowVersion(), PhantomLogicalParty.SCHEMA_VERSION, logicalClaim(next, true))));
		}
		return Optional.of(_profiles.mutateProfileComponentsAtomically(changes, guards, absent).stream().filter(row -> row.componentType().equals(PhantomLogicalParty.COMPONENT_TYPE)).map(this::decodeLogicalGroup).findFirst().orElseThrow());
	}
	private static boolean activityAcknowledges(org.l2jmobius.gameserver.phantoms.decision.PhantomExecutionHandoff execution, PhantomProfileComponent component)
	{
		return execution.dependencies().stream().anyMatch(dependency -> dependency.componentType().equals(component.componentType()) && dependency.rowVersion() == component.rowVersion()
			&& dependency.schema() == component.componentSchemaVersion() && dependency.sha256().equals(org.l2jmobius.gameserver.phantoms.decision.PhantomGoalPublication.digest(component.payload())));
	}
	@Override public StoredLogicalGroup refreshLogicalBackground(StoredLogicalGroup expected, long now)
	{
		final var group = expected.group();
		if (group.phase() != PhantomLogicalParty.Phase.BACKGROUND || group.activityReplanRequired()) { return expected; }
		if (loadLogicalGroup(group.leaderProfileId()).filter(expected::equals).isEmpty()) { throw new java.util.ConcurrentModificationException("Stale Background group watermark."); }
		final var guards = new java.util.ArrayList<PhantomProfileComponent>(); long cursor = Long.MAX_VALUE;
		for (var member : group.members())
		{
			final var claim = loadLogicalClaim(member.profileId()).orElse(null);
			if (claim == null || !claim.active() || claim.leaderProfileId() != group.leaderProfileId() || claim.generation() != group.generation() || !claim.groupId().equals(group.groupId()))
			{ throw new java.util.ConcurrentModificationException("Background group claim changed."); }
			final var rows = new java.util.HashMap<String, PhantomProfileComponent>();
			for (String type : List.of(PhantomLogicalParty.CLAIM_COMPONENT_TYPE, org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.COMPONENT_TYPE,
				org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.COMPONENT_TYPE, org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore.COMPONENT_TYPE))
			{
				final var row = _profiles.findComponent(member.profileId(), type).orElse(null);
				if (row == null) { return expected; }
				rows.put(type, row); guards.add(row);
			}
			final var state = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundStateCodec().decode(rows.get(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.COMPONENT_TYPE).payload());
			final var history = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStateCodec().decode(rows.get(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState.COMPONENT_TYPE).payload());
			final var goal = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(rows.get(org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore.COMPONENT_TYPE).payload());
			if (state.identity().profileId() != member.profileId() || goal.goalId() != member.goalId() || goal.revision() < member.goalRevision()) { return expected; }
			final var proof = org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction.committedHistoricalFarmMinute(group.objective(), state, goal, history);
			if (proof.isEmpty()) { return expected; }
			cursor = Math.min(cursor, proof.getAsLong());
		}
		if (cursor <= group.backgroundCursor()) { return expected; }
		final var next = new PhantomLogicalParty(group.groupId(), group.generation(), group.leaderProfileId(), group.members(), group.objective(), group.anchorId(), group.phase(),
			cursor, now + 5000, "", Math.max(group.expiresAtMillis(), Math.addExact(now, PhantomLogicalParty.IDLE_LIFETIME_MILLIS)));
		return _profiles.mutateProfileComponentsAtomically(List.of(new PhantomProfileRepository.ProfileComponentMutation(group.leaderProfileId(),
			new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.COMPONENT_TYPE, expected.rowVersion(), PhantomLogicalParty.SCHEMA_VERSION, next.encode()))), guards)
			.stream().map(this::decodeLogicalGroup).findFirst().orElseThrow();
	}

	@Override
	public Optional<StoredRegroupClock> loadRegroupClock(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomPartyRegroupState.COMPONENT_TYPE).map(this::decodeRegroup);
	}

	@Override
	public Optional<StoredRendezvous> loadRendezvous(long profileId)
	{
		return _profiles.findComponent(profileId, PhantomPartyRendezvousState.COMPONENT_TYPE).map(this::decodeRendezvous);
	}

	@Override
	public StoredRendezvous saveRendezvous(long profileId, long expectedRowVersion, PhantomPartyRendezvousState state)
	{
		if (state.profileId() != profileId) { throw new IllegalArgumentException("Foreign rendezvous owner."); }
		return decodeRendezvous(expectedRowVersion < 0
			? _profiles.insertComponent(profileId, PhantomPartyRendezvousState.COMPONENT_TYPE, PhantomPartyRendezvousState.SCHEMA_VERSION, state.encode())
			: _profiles.updateComponent(profileId, PhantomPartyRendezvousState.COMPONENT_TYPE, expectedRowVersion, PhantomPartyRendezvousState.SCHEMA_VERSION, state.encode()));
	}

	private StoredRendezvous decodeRendezvous(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomPartyRendezvousState.SCHEMA_VERSION) { throw new IllegalArgumentException("Unknown rendezvous schema."); }
		return new StoredRendezvous(component.profileId(), component.rowVersion(), PhantomPartyRendezvousState.decode(component.payload()));
	}

	@Override
	public StoredRegroupClock saveRegroupClock(long profileId, long expectedRowVersion, PhantomPartyRegroupState state)
	{
		if (state.actor().profileId() != profileId) { throw new IllegalArgumentException("Foreign regroup owner."); }
		return decodeRegroup(expectedRowVersion < 0
			? _profiles.insertComponent(profileId, PhantomPartyRegroupState.COMPONENT_TYPE, PhantomPartyRegroupState.SCHEMA_VERSION, state.encode())
			: _profiles.updateComponent(profileId, PhantomPartyRegroupState.COMPONENT_TYPE, expectedRowVersion, PhantomPartyRegroupState.SCHEMA_VERSION, state.encode()));
	}

	private StoredRegroupClock decodeRegroup(PhantomProfileComponent component)
	{
		if (component.componentSchemaVersion() != PhantomPartyRegroupState.SCHEMA_VERSION) { throw new IllegalArgumentException("Unknown regroup schema."); }
		return new StoredRegroupClock(component.profileId(), component.rowVersion(), PhantomPartyRegroupState.decode(component.payload()));
	}
}

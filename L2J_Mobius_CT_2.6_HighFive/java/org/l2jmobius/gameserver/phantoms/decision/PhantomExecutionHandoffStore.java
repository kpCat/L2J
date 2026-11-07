/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.gameserver.phantoms.decision;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentAbsence;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ComponentMutation;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository.ProfileComponentMutation;

/** Durable arbitration only. Existing owners remain responsible for safe yield, effects and resume. */
public final class PhantomExecutionHandoffStore
{
	public static final String COMPONENT_TYPE = "execution.handoff";
	private final PhantomProfileRepository _profiles;
	public PhantomExecutionHandoffStore(PhantomProfileRepository profiles) { _profiles = Objects.requireNonNull(profiles); }
	public record Stored(PhantomProfileComponent component)
	{
		public Stored
		{
			if (!COMPONENT_TYPE.equals(component.componentType()) || component.componentSchemaVersion() != PhantomExecutionHandoff.SCHEMA_VERSION)
			{ throw new IllegalArgumentException("Unknown execution component."); }
			PhantomExecutionHandoff.decode(component.payload());
		}
		public long profileId() { return component.profileId(); }
		public PhantomExecutionHandoff state() { return PhantomExecutionHandoff.decode(component.payload()); }
	}
	public Optional<Stored> load(long id) { return _profiles.findComponent(id, COMPONENT_TYPE).map(Stored::new); }
	public record CompletionGuard(List<PhantomProfileComponent> present, List<ComponentAbsence> absent)
	{
		public CompletionGuard { present = List.copyOf(present); absent = List.copyOf(absent); }
	}
	/** Guard current source completion (including already earned REQUEST work) in the effect owner's transaction. */
	public CompletionGuard guardCompletion(long id, PhantomDomainRef owner)
	{
		final var stored = load(id).orElse(null);
		if (stored == null) { return new CompletionGuard(List.of(), List.of(new ComponentAbsence(id, COMPONENT_TYPE))); }
		if (!stored.state().ownerMayComplete(owner)) { throw new ConcurrentModificationException("Execution completion belongs to another owner or has yielded."); }
		return new CompletionGuard(List.of(stored.component()), List.of());
	}
	public Stored initialize(long id, PhantomDomainRef owner)
	{ return new Stored(_profiles.insertComponent(id, COMPONENT_TYPE, PhantomExecutionHandoff.SCHEMA_VERSION, PhantomExecutionHandoff.initial(owner).encode())); }
	public Stored request(Stored expected, PhantomDomainRef target, String requestId, long now, long deadline)
	{ return write(expected, expected.state().request(target, requestId, now, deadline), List.of(), List.of(), List.of()); }
	public Stored yielded(Stored expected, long now)
	{ return write(expected, expected.state().yield(now), List.of(), List.of(), List.of()); }
	public Stored recoverSource(Stored expected)
	{ return write(expected, expected.state().recoverSource(), List.of(), List.of(), List.of()); }

	/** Persist the owner's resumable changes and their exact post-write proof in the same transaction. */
	public Stored checkpoint(Stored expected, List<ComponentMutation> changes, List<String> dependencyTypes, long now)
	{ return checkpoint(expected, changes, dependencyTypes, now, false, List.of()); }

	/** RECOVER acknowledgement and owner changes share one guarded transaction before NORMAL admission opens. */
	public Stored recovered(Stored expected, List<ComponentMutation> changes, List<PhantomProfileComponent> authority)
	{
		final var types = new java.util.LinkedHashSet<String>();
		expected.state().dependencies().forEach(d -> types.add(d.componentType())); changes.forEach(c -> types.add(c.componentType()));
		return checkpoint(expected, changes, List.copyOf(types), 0, true, authority);
	}

	private Stored checkpoint(Stored expected, List<ComponentMutation> changes, List<String> dependencyTypes, long now, boolean recovery, List<PhantomProfileComponent> authority)
	{
		if (dependencyTypes == null || dependencyTypes.isEmpty() || dependencyTypes.size() > PhantomExecutionHandoff.MAX_DEPENDENCIES
			|| dependencyTypes.stream().distinct().count() != dependencyTypes.size() || dependencyTypes.contains(COMPONENT_TYPE)
			|| changes == null || changes.size() > PhantomExecutionHandoff.MAX_DEPENDENCIES || changes.stream().map(ComponentMutation::componentType).distinct().count() != changes.size()
			|| changes.stream().anyMatch(c -> !dependencyTypes.contains(c.componentType())))
		{ throw new IllegalArgumentException("Invalid bounded execution checkpoint components."); }
		final var guards = new ArrayList<PhantomProfileComponent>(); final var absent = new ArrayList<ComponentAbsence>();
		final var dependencies = new ArrayList<PhantomExecutionHandoff.Dependency>();
		for (String type : dependencyTypes)
		{
			final var raw = _profiles.findComponent(expected.profileId(), type).orElse(null);
			if (raw == null) { absent.add(new ComponentAbsence(expected.profileId(), type)); } else { guards.add(raw); }
			final var change = changes.stream().filter(c -> c.componentType().equals(type)).findFirst().orElse(null);
			if (recovery)
			{
				final var prior = expected.state().dependencies().stream().filter(d -> d.componentType().equals(type)).findFirst().orElse(null);
				if (prior != null && !fingerprint(type, raw).equals(prior) && (raw == null || !authority.contains(raw)))
				{ throw new ConcurrentModificationException("Recipient recovery did not acknowledge a changed checkpoint obligation."); }
			}
			if (change == null) { dependencies.add(fingerprint(type, raw)); }
			else
			{
				if (change.expectedRowVersion() != (raw == null ? -1 : raw.rowVersion())) { throw new ConcurrentModificationException("Owner checkpoint changed before safe yield."); }
				dependencies.add(new PhantomExecutionHandoff.Dependency(type, change.componentSchemaVersion(), change.expectedRowVersion() + 1, sha256(change.payload())));
			}
		}
		guards.addAll(authority);
		return write(expected, recovery ? expected.state().recovered(dependencies) : expected.state().checkpoint(dependencies, now), changes, guards, absent);
	}

	/** Recipient authority (for example exact logical group generation) shares the transfer transaction. */
	public Stored transfer(Stored expected, List<PhantomProfileComponent> recipientGuards, long now)
	{
		final var guards = new ArrayList<>(recipientGuards); final var absent = new ArrayList<ComponentAbsence>();
		for (var dependency : expected.state().dependencies())
		{
			final var raw = _profiles.findComponent(expected.profileId(), dependency.componentType()).orElse(null);
			if (!fingerprint(dependency.componentType(), raw).equals(dependency)) { throw new ConcurrentModificationException("Execution checkpoint changed before transfer."); }
			if (raw == null) { absent.add(new ComponentAbsence(expected.profileId(), dependency.componentType())); } else { guards.add(raw); }
		}
		return write(expected, expected.state().transfer(now), List.of(), guards, absent);
	}
	private Stored write(Stored expected, PhantomExecutionHandoff next, List<ComponentMutation> changes, List<PhantomProfileComponent> guards, List<ComponentAbsence> absent)
	{
		final var mutations = new ArrayList<ProfileComponentMutation>();
		mutations.add(new ProfileComponentMutation(expected.profileId(), new ComponentMutation(COMPONENT_TYPE, expected.component().rowVersion(), PhantomExecutionHandoff.SCHEMA_VERSION, next.encode())));
		for (var change : changes) { mutations.add(new ProfileComponentMutation(expected.profileId(), change)); }
		final var allGuards = new ArrayList<>(guards); allGuards.add(expected.component());
		return _profiles.mutateProfileComponentsAtomically(mutations, allGuards, absent).stream()
			.filter(c -> COMPONENT_TYPE.equals(c.componentType())).map(Stored::new).findFirst().orElseThrow();
	}
	private static PhantomExecutionHandoff.Dependency fingerprint(String type, PhantomProfileComponent raw)
	{ return raw == null ? new PhantomExecutionHandoff.Dependency(type, 0, -1, "") : new PhantomExecutionHandoff.Dependency(type, raw.componentSchemaVersion(), raw.rowVersion(), sha256(raw.payload())); }
	private static String sha256(byte[] bytes)
	{
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
		catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
	}
}

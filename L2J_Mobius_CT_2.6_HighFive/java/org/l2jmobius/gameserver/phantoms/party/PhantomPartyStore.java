/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
		if (component.componentSchemaVersion() != PhantomLogicalParty.SCHEMA_VERSION) { throw new IllegalArgumentException("Unknown logical group schema."); }
		final var group = PhantomLogicalParty.decode(component.payload());
		if (group.leaderProfileId() != component.profileId()) { throw new IllegalArgumentException("Foreign logical group aggregate."); }
		return new StoredLogicalGroup(component.rowVersion(), group);
	}
	@Override public Optional<StoredLogicalClaim> loadLogicalClaim(long id)
	{
		return _profiles.findComponent(id, PhantomLogicalParty.CLAIM_COMPONENT_TYPE).map(component ->
		{
			if (component.componentSchemaVersion() != PhantomLogicalParty.SCHEMA_VERSION) { throw new IllegalArgumentException("Unknown logical claim schema."); }
			try (var in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(component.payload())))
			{
				final var claim = new StoredLogicalClaim(id, in.readLong(), in.readUTF(), in.readLong(), component.rowVersion());
				if (in.available() != 0) { throw new IllegalArgumentException("Trailing logical claim payload."); }
				return claim;
			}
			catch (java.io.IOException failure) { throw new IllegalArgumentException("Invalid logical claim payload.", failure); }
		});
	}
	private byte[] logicalClaim(PhantomLogicalParty group)
	{
		try
		{
			final var bytes = new java.io.ByteArrayOutputStream();
			try (var out = new java.io.DataOutputStream(bytes)) { out.writeLong(group.leaderProfileId()); out.writeUTF(group.groupId()); out.writeLong(group.generation()); }
			return bytes.toByteArray();
		}
		catch (java.io.IOException failure) { throw new IllegalArgumentException("Cannot encode logical claim.", failure); }
	}
	@Override public StoredLogicalGroup commitLogicalGroup(PhantomLogicalParty group, long expected, java.util.Map<Long, Long> versions)
	{
		if (expected != -1 || group.phase() != PhantomLogicalParty.Phase.AGREED || versions.size() != group.members().size()
			|| group.members().stream().anyMatch(member -> !Long.valueOf(-1).equals(versions.get(member.profileId()))))
		{ throw new IllegalArgumentException("Formation requires exclusively unclaimed identities."); }
		final var mutations = new java.util.ArrayList<PhantomProfileRepository.ProfileComponentMutation>();
		mutations.add(new PhantomProfileRepository.ProfileComponentMutation(group.leaderProfileId(),
			new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.COMPONENT_TYPE, -1, PhantomLogicalParty.SCHEMA_VERSION, group.encode())));
		final byte[] claim = logicalClaim(group);
		for (var member : group.members())
		{
			mutations.add(new PhantomProfileRepository.ProfileComponentMutation(member.profileId(),
				new PhantomProfileRepository.ComponentMutation(PhantomLogicalParty.CLAIM_COMPONENT_TYPE, -1, PhantomLogicalParty.SCHEMA_VERSION, claim)));
		}
		return _profiles.mutateProfileComponentsAtomically(mutations).stream().filter(row -> row.componentType().equals(PhantomLogicalParty.COMPONENT_TYPE)).map(this::decodeLogicalGroup).findFirst().orElseThrow();
	}
	@Override public StoredLogicalGroup updateLogicalGroup(PhantomLogicalParty group, long expected)
	{
		final var before = loadLogicalGroup(group.leaderProfileId()).orElseThrow();
		final var old = before.group();
		if (before.rowVersion() != expected) { throw new java.util.ConcurrentModificationException("Stale logical group version."); }
		if (!old.groupId().equals(group.groupId()) || old.generation() != group.generation() || !old.members().equals(group.members())
			|| !old.objective().equals(group.objective()) || !old.anchorId().equals(group.anchorId())
			|| !old.transition(group.phase(), group.backgroundCursor(), group.nextDueMillis(), group.waitReason()).equals(group))
		{ throw new IllegalArgumentException("A phase update cannot replace logical membership or objective."); }
		return decodeLogicalGroup(_profiles.updateComponent(group.leaderProfileId(), PhantomLogicalParty.COMPONENT_TYPE, expected, PhantomLogicalParty.SCHEMA_VERSION, group.encode()));
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

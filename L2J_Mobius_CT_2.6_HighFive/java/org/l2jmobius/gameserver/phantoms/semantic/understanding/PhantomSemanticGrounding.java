/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.semantic.understanding;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyRoleCatalog;
import org.l2jmobius.gameserver.phantoms.semantic.understanding.PhantomSemanticModel.SlotType;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/**
 * Narrow construction-time authority. Runtime parsing receives only validated immutable refs.
 */
public final class PhantomSemanticGrounding
{
	private PhantomSemanticGrounding()
	{
	}

	public record Hashes(String knowledgeHash, String topologyHash, String partyRoleHash)
	{
		public Hashes
		{
			knowledgeHash = PhantomSemanticModel.requireHash(knowledgeHash, "Semantic Game Knowledge hash");
			topologyHash = PhantomSemanticModel.requireHash(topologyHash, "Semantic topology hash");
			partyRoleHash = PhantomSemanticModel.requireHash(partyRoleHash, "Semantic party-role hash");
		}
	}

	public interface Authority
	{
		Hashes hashes();

		Optional<PhantomDomainRef> resolve(SlotType slotType, String key);
	}

	public static Authority production(PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomPartyRoleCatalog partyRoles)
	{
		Objects.requireNonNull(knowledge, "Game Knowledge query must not be null.");
		Objects.requireNonNull(topology, "Topology query must not be null.");
		Objects.requireNonNull(partyRoles, "Party-role catalog must not be null.");
		final String topologyHash = topology.snapshot().canonicalHash();
		if (!knowledge.snapshot().topologyHash().equals(topologyHash))
		{
			throw new IllegalArgumentException("Game Knowledge and topology generations are not pinned to the same hash.");
		}
		return new ProductionAuthority(knowledge, topology, partyRoles, new Hashes(canonicalHash(knowledge.snapshot().combinedHash()), canonicalHash(topologyHash), canonicalHash(partyRoles.hash())));
	}

	public static Authority fixed(Hashes hashes, Map<SlotType, Map<String, PhantomDomainRef>> references)
	{
		Objects.requireNonNull(hashes, "Fixed semantic authority hashes must not be null.");
		final EnumMap<SlotType, Map<String, PhantomDomainRef>> copy = new EnumMap<>(SlotType.class);
		for (var entry : Objects.requireNonNull(references, "Fixed semantic authority refs must not be null.").entrySet())
		{
			final HashMap<String, PhantomDomainRef> values = new HashMap<>();
			entry.getValue().forEach((key, value) -> values.put(requireAuthorityKey(key), Objects.requireNonNull(value, "Fixed semantic authority ref must not be null.")));
			copy.put(entry.getKey(), Map.copyOf(values));
		}
		final Map<SlotType, Map<String, PhantomDomainRef>> immutable = Map.copyOf(copy);
		return new Authority()
		{
			@Override
			public Hashes hashes()
			{
				return hashes;
			}

			@Override
			public Optional<PhantomDomainRef> resolve(SlotType slotType, String key)
			{
				return Optional.ofNullable(immutable.getOrDefault(slotType, Map.of()).get(key));
			}
		};
	}

	private static String requireAuthorityKey(String key)
	{
		if ((key == null) || key.isBlank() || (key.length() > 128) || !key.equals(key.trim()))
		{
			throw new IllegalArgumentException("Semantic authority key is invalid.");
		}
		return key;
	}

	private static String canonicalHash(String value)
	{
		if ((value == null) || !value.matches("[0-9A-Fa-f]{64}"))
		{
			throw new IllegalArgumentException("Semantic production authority returned an invalid SHA-256 hash.");
		}
		return value.toUpperCase(Locale.ROOT);
	}

	private record ProductionAuthority(PhantomGameKnowledgeQuery _knowledge, PhantomTopologyQuery _topology, PhantomPartyRoleCatalog _partyRoles, Hashes _hashes) implements Authority
	{
		@Override
		public Hashes hashes()
		{
			final Hashes current = new Hashes(canonicalHash(_knowledge.snapshot().combinedHash()), canonicalHash(_topology.snapshot().canonicalHash()), canonicalHash(_partyRoles.hash()));
			if (!_hashes.equals(current) || !canonicalHash(_knowledge.snapshot().topologyHash()).equals(current.topologyHash()))
			{
				throw new IllegalStateException("Semantic authority generation drifted during publication.");
			}
			return current;
		}

		@Override
		public Optional<PhantomDomainRef> resolve(SlotType slotType, String key)
		{
			requireAuthorityKey(key);
			return switch (Objects.requireNonNull(slotType, "Semantic authority slot must not be null."))
			{
				case ITEM -> positiveInteger(key).filter(id -> _knowledge.findItem(id).isPresent()).map(id -> new PhantomDomainRef("item", Integer.toString(id)));
				case NPC -> positiveInteger(key).filter(id -> _knowledge.findNpc(id).isPresent()).map(id -> new PhantomDomainRef("npc", Integer.toString(id)));
				case CONTENT -> _knowledge.content(key).map(_ -> new PhantomDomainRef("content", key));
				case TOPOLOGY_NODE, LOCATION -> _topology.findNode(key).map(_ -> new PhantomDomainRef("topology.node", key));
				case CAPABILITY -> _knowledge.snapshot().classesByCapability().containsKey(key) ? Optional.of(new PhantomDomainRef("capability", key)) : Optional.empty();
				case PARTY_ROLE -> _partyRoles.contains(key) ? Optional.of(new PhantomDomainRef("party.role", key)) : Optional.empty();
				default -> Optional.empty();
			};
		}

		private static Optional<Integer> positiveInteger(String key)
		{
			if (!key.matches("[1-9][0-9]{0,9}"))
			{
				return Optional.empty();
			}
			try
			{
				return Optional.of(Integer.parseInt(key));
			}
			catch (NumberFormatException exception)
			{
				return Optional.empty();
			}
		}
	}
}

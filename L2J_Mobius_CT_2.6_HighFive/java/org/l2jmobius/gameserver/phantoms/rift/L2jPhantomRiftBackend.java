/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.rift;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

import org.l2jmobius.gameserver.config.GeneralConfig;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.managers.DimensionalRiftManager;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.Weapon;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.item.type.ActionType;
import org.l2jmobius.gameserver.model.itemcontainer.Inventory;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog.SupplyKind;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.PartySnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.ConfigFacts;
import org.l2jmobius.gameserver.phantoms.rift.PhantomRiftCatalog.EntryFacts;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;

/**
 * Bounded live High Five adapter. Nearby discovery walks only visible objects
 * around the exact leader and never World.getPlayers().
 */
public final class L2jPhantomRiftBackend implements PhantomRiftBackend
{
	private final PhantomPartyBackend _party;
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final PhantomProgressionService _progression;
	private final PhantomSocialService _social;
	private final List<Integer> _shotItemIds;

	public L2jPhantomRiftBackend(PhantomPartyBackend party, PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomProgressionService progression, PhantomCommerceCatalog commerce)
	{
		this(party, profiles, materialization, progression, commerce, null);
	}

	public L2jPhantomRiftBackend(PhantomPartyBackend party, PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomProgressionService progression, PhantomCommerceCatalog commerce, PhantomSocialService social)
	{
		_party = party;
		_profiles = profiles;
		_materialization = materialization;
		_progression = progression;
		_social = social;
		_shotItemIds = commerce.supplies().stream().filter(supply -> supply.kinds().contains(SupplyKind.SHOT)).map(PhantomCommerceCatalog.SupplyFact::itemId).distinct().sorted().toList();
	}

	@Override
	public Optional<MemberRef> currentMember(long profileId)
	{
		return _party.currentMember(profileId);
	}

	@Override
	public Optional<PartySnapshot> canonicalParty(MemberRef member)
	{
		return _party.observe(member);
	}

	@Override
	public Optional<MemberFacts> memberFacts(MemberRef member, Set<Integer> requestedItemIds)
	{
		if ((requestedItemIds == null) || (requestedItemIds.size() > 32) || requestedItemIds.stream().anyMatch(itemId -> itemId <= 0))
		{
			throw new IllegalArgumentException("Rift requested item set is outside bounds.");
		}
		final MemberSnapshot partySnapshot = _party.memberSnapshot(member).orElse(null);
		if (partySnapshot == null)
		{
			return Optional.empty();
		}
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return Optional.empty();
			}
			final Player player = acquired.player();
			if ((player.getObjectId() != member.characterObjectId()) || (player.getActiveClass() != partySnapshot.classId()))
			{
				return Optional.empty();
			}
			final PhantomProgressionCatalog progression = _progression.findCatalog().orElse(null);
			final List<EquipmentFactView> equipment = new ArrayList<>();
			for (int slot = 0; slot < Inventory.PAPERDOLL_TOTALSLOTS; slot++)
			{
				final Item item = player.getInventory().getPaperdollItem(slot);
				if (item == null)
				{
					continue;
				}
				final var fact = progression == null ? null : progression.equipment(item.getId());
				equipment.add(new EquipmentFactView(item.getObjectId(), item.getId(), slot, fact == null ? "unsupported" : fact.family(), item.getTemplate().getCrystalType().name()));
			}
			final Map<Integer, Long> itemCounts = new TreeMap<>();
			for (int itemId : requestedItemIds)
			{
				itemCounts.put(itemId, player.getInventory().getInventoryItemCount(itemId, -1));
			}
			final Weapon weapon = player.getActiveWeaponItem();
			final List<ShotSupply> shots = new ArrayList<>();
			if (weapon != null)
			{
				for (int itemId : _shotItemIds)
				{
					final var template = ItemData.getInstance().getTemplate(itemId);
					if ((template == null) || (template.getCrystalType() != weapon.getCrystalTypePlus()))
					{
						continue;
					}
					final ActionType action = template.getDefaultAction();
					if ((action == ActionType.SOULSHOT) || (action == ActionType.SPIRITSHOT))
					{
						shots.add(new ShotSupply(itemId, player.getInventory().getInventoryItemCount(itemId, -1), action == ActionType.SPIRITSHOT));
					}
				}
			}
			final int partySize = player.getParty() == null ? 0 : player.getParty().getMemberCount();
			final String evidence = PhantomPartyModel.sha256(member.stableKey() + '|' + player.getLevel() + '|' + partySnapshot.classId() + '|' + partySnapshot.progressionHash() + '|' + equipment + '|' + itemCounts + '|' + shots + '|' + (weapon == null ? 0 : weapon.getId()) + '|' + partySize);
			return Optional.of(new MemberFacts(partySnapshot, player.getLevel(), equipment.stream().map(EquipmentFactView::publicFact).toList(), itemCounts, shots, weapon == null ? 0 : weapon.getId(), weapon == null ? 0 : weapon.getSoulShotCount(), weapon == null ? 0 : weapon.getSpiritShotCount(), partySize, evidence));
		}
	}

	@Override
	public List<MemberFacts> nearbyCandidates(MemberRef observer, Set<Integer> requestedItemIds, int range, int limit)
	{
		if ((range < 1) || (range > 10000) || (limit < 1) || (limit > PhantomRiftModel.MAX_CANDIDATES))
		{
			throw new IllegalArgumentException("Rift candidate discovery bounds are invalid.");
		}
		final List<MemberRef> references;
		try (AcquiredPlayer acquired = acquire(observer))
		{
			if (acquired == null)
			{
				return List.of();
			}
			final Player player = acquired.player();
			references = World.getInstance().getVisibleObjectsInRange(player, Player.class, range).stream()
				.filter(candidate -> (candidate != player) && (candidate.getInstanceId() == player.getInstanceId()))
				.sorted(Comparator.<Player>comparingInt(candidate -> PhantomIdentityLeaseRegistry.getInstance().getOwnerKind(candidate.getObjectId()) == OwnerKind.PHANTOM ? 0 : 1).thenComparingInt(Player::getObjectId))
				.limit(limit)
				.map(this::reference)
				.toList();
		}
		final List<MemberFacts> result = new ArrayList<>(references.size());
		for (MemberRef reference : references)
		{
			memberFacts(reference, requestedItemIds).ifPresent(result::add);
		}
		return List.copyOf(result);
	}

	@Override
	public Optional<MemberFacts> candidateFacts(MemberRef observer, MemberRef candidate, Set<Integer> requestedItemIds, int range)
	{
		if ((range < 1) || (range > 10000))
		{
			throw new IllegalArgumentException("Rift candidate refresh range is invalid.");
		}
		try (AcquiredPlayer acquired = acquire(observer))
		{
			if (acquired == null)
			{
				return Optional.empty();
			}
			final Player player = acquired.player();
			final Player visible = World.getInstance().getVisibleObjectsInRange(player, Player.class, range).stream().filter(value -> (value.getObjectId() == candidate.characterObjectId()) && (value.getInstanceId() == player.getInstanceId())).findFirst().orElse(null);
			if ((visible == null) || !reference(visible).equals(candidate))
			{
				return Optional.empty();
			}
		}
		return memberFacts(candidate, requestedItemIds);
	}

	@Override
	public RelationshipEvidence relationship(long ownerProfileId, MemberRef candidate)
	{
		if (_social == null)
		{
			return RelationshipEvidence.neutral("social.not_configured");
		}
		final SubjectRef subject = candidate.kind() == MemberKind.PHANTOM ? SubjectRef.phantom(candidate.profileId()) : SubjectRef.character(candidate.characterObjectId());
		final var result = _social.modifier(ownerProfileId, subject, "party.invite.preference", System.currentTimeMillis() / 60000L);
		if (!result.available() || (result.value() == null))
		{
			return RelationshipEvidence.neutral(result.detail().isBlank() ? "social.unavailable" : result.detail());
		}
		final var value = result.value();
		final String evidence = PhantomPartyModel.sha256(subject.stableKey() + '|' + value.modifierKey() + '|' + value.deltaBasisPoints() + '|' + value.evidenceKeys() + '|' + value.authorityHash());
		return new RelationshipEvidence(value.deltaBasisPoints(), evidence, "social.modifier.ready", true);
	}
	@Override
	public OptionalInt npcLevel(int npcId)
	{
		final var template = NpcData.getInstance().getTemplate(npcId);
		return template == null ? OptionalInt.empty() : OptionalInt.of(template.getLevel());
	}

	@Override
	public EntryFacts entry(int type)
	{
		final DimensionalRiftManager.EntryReadinessSnapshot snapshot = DimensionalRiftManager.getInstance().entryReadiness((byte) type);
		return new EntryFacts(type, snapshot.supported(), snapshot.entryItemId(), snapshot.entryItemCount(), snapshot.minimumPartySize(), snapshot.destinationX(), snapshot.destinationY(), snapshot.destinationZ(), snapshot.destinationInstanceId(), snapshot.bossRoomIds().stream().map(Byte::intValue).collect(java.util.stream.Collectors.toSet()), snapshot.occupiedRooms(), snapshot.capacity(), snapshot.entryCapacityAvailable(), "DimensionalRiftManager.entryReadiness");
	}

	@Override
	public ConfigFacts config()
	{
		final Map<Integer, Integer> costs = new HashMap<>();
		costs.put(1, GeneralConfig.RIFT_ENTER_COST_RECRUIT);
		costs.put(2, GeneralConfig.RIFT_ENTER_COST_SOLDIER);
		costs.put(3, GeneralConfig.RIFT_ENTER_COST_OFFICER);
		costs.put(4, GeneralConfig.RIFT_ENTER_COST_CAPTAIN);
		costs.put(5, GeneralConfig.RIFT_ENTER_COST_COMMANDER);
		costs.put(6, GeneralConfig.RIFT_ENTER_COST_HERO);
		return new ConfigFacts(GeneralConfig.RIFT_MAX_JUMPS, GeneralConfig.RIFT_SPAWN_DELAY, GeneralConfig.RIFT_AUTO_JUMPS_TIME_MIN, GeneralConfig.RIFT_AUTO_JUMPS_TIME_MAX, GeneralConfig.RIFT_BOSS_ROOM_TIME_MUTIPLY, costs, "GeneralConfig.RIFT_*");
	}

	private MemberRef reference(Player player)
	{
		final Optional<PhantomProfile> profile = _profiles.findByCharacterObjectId(player.getObjectId());
		return profile.isPresent() ? MemberRef.phantom(profile.get().profileId(), player.getObjectId()) : MemberRef.real(player.getObjectId());
	}

	private AcquiredPlayer acquire(MemberRef member)
	{
		if (member.kind() == MemberKind.REAL)
		{
			final Player player = World.getInstance().getPlayer(member.characterObjectId());
			return player == null ? null : new AcquiredPlayer(player, null);
		}
		final Optional<ActionLease> lease = _materialization.tryAcquireAction(member.profileId());
		if (lease.isEmpty() || (lease.get().player().getObjectId() != member.characterObjectId()))
		{
			lease.ifPresent(ActionLease::close);
			return null;
		}
		return new AcquiredPlayer(lease.get().player(), lease.get());
	}

	private record EquipmentFactView(int objectId, int itemId, int slot, String family, String grade)
	{
		private PhantomRiftBackend.EquipmentFact publicFact()
		{
			return new PhantomRiftBackend.EquipmentFact(objectId, itemId, slot, family, grade);
		}
	}

	private record AcquiredPlayer(Player player, ActionLease lease) implements AutoCloseable
	{
		@Override
		public void close()
		{
			if (lease != null)
			{
				lease.close();
			}
		}
	}
}

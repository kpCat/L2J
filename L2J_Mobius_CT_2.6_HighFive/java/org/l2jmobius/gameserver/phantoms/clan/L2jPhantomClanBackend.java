/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiPredicate;

import org.l2jmobius.commons.util.StringUtil;
import org.l2jmobius.gameserver.data.sql.ClanTable;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.handler.ChatHandler;
import org.l2jmobius.gameserver.handler.IChatHandler;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.VillageMaster;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.chat.ChatObservationService.DispatchHandle;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceIdentity;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.AllianceMembershipProof;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipEpoch;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipResult;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService.MembershipSnapshot;
import org.l2jmobius.gameserver.model.clan.ClanDepartureService;
import org.l2jmobius.gameserver.model.clan.ClanFoundingDevelopment;
import org.l2jmobius.gameserver.model.clan.ClanInvitationService;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.model.clan.ClanWarService.WarIdentity;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.instance.Item;
import org.l2jmobius.gameserver.model.itemcontainer.ItemContainer;
import org.l2jmobius.gameserver.network.enums.ChatType;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.AllianceObservation;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.AllianceActor;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.AutonomousActor;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.Backend;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ChatOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ChatResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ClanSnapshot;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionObservation;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.ContributionResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.CreationOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.CreationResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.MemberKind;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.MemberRef;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleOutcome;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RelationshipEvidence;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.RoleResult;
import org.l2jmobius.gameserver.phantoms.clan.PhantomClanService.WithdrawalOutcome;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializedPlayer.ActionLease;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfile;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomPvpPolicy;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialAffiliationContextPort;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEvent;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialService;

/** Exact High Five adapter; no clan, membership or warehouse shadow state. */
public final class L2jPhantomClanBackend implements Backend
{
	private static final Set<String> HOSTILE_EVENTS = Set.of("agreement.broken", "farming.conflict.escalated", "pvp.attack.received", "pvp.death.suffered");
	private final PhantomProfileRepository _profiles;
	private final PhantomMaterializationService _materialization;
	private final ClanInvitationService _invitations;
	private final ClanAllianceService _alliances;
	private final ClanWarService _wars;
	private final ChatObservationService _chatObservation;
	private final PhantomSocialService _social;
	private final PhantomPvpPolicy _pvpPolicy;
	private final PhantomSocialAffiliationContextPort _affiliationContexts;
	private final BiPredicate<Long, Player> _mandatoryPreparationPending;

	public L2jPhantomClanBackend(PhantomProfileRepository profiles, PhantomMaterializationService materialization)
	{
		this(profiles, materialization, null, null, PhantomSocialAffiliationContextPort.noop());
	}

	public L2jPhantomClanBackend(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomSocialService social, PhantomPvpPolicy pvpPolicy)
	{
		this(profiles, materialization, social, pvpPolicy, PhantomSocialAffiliationContextPort.noop());
	}

	public L2jPhantomClanBackend(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomSocialService social, PhantomPvpPolicy pvpPolicy, PhantomSocialAffiliationContextPort affiliationContexts)
	{
		this(profiles, materialization, social, pvpPolicy, affiliationContexts, (profileId, player) -> true);
	}

	public L2jPhantomClanBackend(PhantomProfileRepository profiles, PhantomMaterializationService materialization, PhantomSocialService social, PhantomPvpPolicy pvpPolicy, PhantomSocialAffiliationContextPort affiliationContexts, BiPredicate<Long, Player> mandatoryPreparationPending)
	{
		_profiles = profiles;
		_materialization = materialization;
		_invitations = ClanInvitationService.getInstance();
		_alliances = ClanAllianceService.getInstance();
		_wars = ClanWarService.getInstance();
		_chatObservation = ChatObservationService.getInstance();
		_social = social;
		_pvpPolicy = pvpPolicy;
		_affiliationContexts = Objects.requireNonNull(affiliationContexts);
		_mandatoryPreparationPending = Objects.requireNonNull(mandatoryPreparationPending);
	}

	@Override
	public Optional<MemberRef> currentMember(long profileId)
	{
		return _profiles.find(profileId).filter(profile -> profile.characterObjectId() != null).map(profile -> MemberRef.phantom(profile.profileId(), profile.characterObjectId()));
	}

	@Override
	public Optional<MemberRef> resolve(PhantomDomainRef source)
	{
		final long value = parsePositive(source.key());
		if (value <= 0)
		{
			return Optional.empty();
		}
		if ("profile".equals(source.namespace()))
		{
			return currentMember(value);
		}
		if (!"character.object".equals(source.namespace()) || (value > Integer.MAX_VALUE))
		{
			return Optional.empty();
		}
		final int objectId = (int) value;
		final Optional<PhantomProfile> managed = _profiles.findByCharacterObjectId(objectId);
		return managed.<MemberRef>map(profile -> MemberRef.phantom(profile.profileId(), objectId)).or(() -> Optional.of(MemberRef.real(objectId)));
	}

	@Override
	public Optional<ClanSnapshot> observe(MemberRef member)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return Optional.empty();
			}
			final Player player = acquired.player();
			final Clan clan = player.getClan();
			if ((clan == null) || (ClanTable.getInstance().getClan(clan.getId()) != clan) || (clan.getClanMember(player.getObjectId()) == null))
			{
				return Optional.empty();
			}
			return Optional.of(snapshot(clan, player.getObjectId()));
		}
	}

	@Override
	public CreationResult create(MemberRef actor, String clanName)
	{
		try (AcquiredPlayer acquired = acquire(actor))
		{
			if ((acquired == null) || (actor.kind() != MemberKind.PHANTOM))
			{
				return new CreationResult(CreationOutcome.STALE, null);
			}
			final Player player = acquired.player();
			if (!ClanTable.getInstance().settleClanCreation(player)) { return new CreationResult(CreationOutcome.PERSISTENCE_PENDING, null); }
			if (player.getClan() != null)
			{
				final ClanSnapshot current = snapshot(player.getClan(), player.getObjectId());
				return new CreationResult(current.clanName().equalsIgnoreCase(clanName) ? CreationOutcome.ALREADY_SATISFIED : CreationOutcome.ALREADY_IN_CLAN, current);
			}
			if (player.getLevel() < 10)
			{
				return new CreationResult(CreationOutcome.LEVEL_TOO_LOW, null);
			}
			if (System.currentTimeMillis() < player.getClanCreateExpiryTime())
			{
				return new CreationResult(CreationOutcome.CREATE_COOLDOWN, null);
			}
			if ((clanName == null) || (clanName.length() < 2) || (clanName.length() > 16) || !StringUtil.isAlphaNumeric(clanName))
			{
				return new CreationResult(CreationOutcome.INVALID_NAME, null);
			}
			if (ClanTable.getInstance().getClanByName(clanName) != null)
			{
				return new CreationResult(CreationOutcome.NAME_TAKEN, null);
			}
			final Clan created = ClanTable.getInstance().createClan(player, clanName);
			if (created == null && player.getPendingClanCreation() != null) { return new CreationResult(CreationOutcome.PERSISTENCE_PENDING, null); }
			if ((created == null) || (player.getClan() != created) || (ClanTable.getInstance().getClan(created.getId()) != created))
			{
				return new CreationResult(CreationOutcome.FAILED, null);
			}
			return new CreationResult(CreationOutcome.CREATED, snapshot(created, player.getObjectId()));
		}
	}

	@Override
	public ClanInvitationService.InviteResult invite(MemberRef requester, MemberRef target)
	{
		try (AcquiredPair pair = acquirePair(requester, target))
		{
			if (pair == null)
			{
				return new ClanInvitationService.InviteResult(ClanInvitationService.InviteOutcome.TARGET_NOT_FOUND, null);
			}
			return _invitations.invite(pair.player(requester), pair.player(target), 0);
		}
	}

	@Override
	public boolean hasAutonomousInvitation(int objectId) { return _invitations.hasPending(objectId); }

	@Override
	public CreationResult createAutonomous(MemberRef actor, String name)
	{
		try (var acquired = acquire(actor))
		{
			if (acquired == null || actor.kind() != MemberKind.PHANTOM) { return new CreationResult(CreationOutcome.STALE, null); }
			final var player = acquired.player();
			if (name == null || name.length() < 2 || name.length() > 16 || !StringUtil.isAlphaNumeric(name)) { return new CreationResult(CreationOutcome.INVALID_NAME, null); }
			if (!ordinaryPlayer(player) || player.getClanId() != 0 || _invitations.hasPending(player.getObjectId())) { return new CreationResult(CreationOutcome.STALE, null); }
			if (!ClanTable.getInstance().settleClanCreation(player)) { return new CreationResult(CreationOutcome.PERSISTENCE_PENDING, null); }
			if (player.getClanId() != 0)
			{
				final var clan = player.getClan();
				return clan != null && clan.getLeaderId() == player.getObjectId() && clan.getName().equalsIgnoreCase(name)
					? new CreationResult(CreationOutcome.ALREADY_SATISFIED, snapshot(clan, player.getObjectId())) : new CreationResult(CreationOutcome.ALREADY_IN_CLAN, null);
			}
			if (player.getLevel() < 10) { return new CreationResult(CreationOutcome.LEVEL_TOO_LOW, null); }
			if (System.currentTimeMillis() < player.getClanCreateExpiryTime()) { return new CreationResult(CreationOutcome.CREATE_COOLDOWN, null); }
			if (ClanTable.getInstance().getClanByName(name) != null) { return new CreationResult(CreationOutcome.NAME_TAKEN, null); }
			final var master = availableClanMaster(player);
			if (master == null) { return new CreationResult(CreationOutcome.STALE, null); }
			master.onBypassFeedback(player, "create_clan " + name);
			final var created = player.getClan();
			if (created == null && player.getPendingClanCreation() != null) { return new CreationResult(CreationOutcome.PERSISTENCE_PENDING, null); }
			return created != null && created.getLeaderId() == player.getObjectId() && created.getName().equalsIgnoreCase(name) && ClanTable.getInstance().getClan(created.getId()) == created
				? new CreationResult(CreationOutcome.CREATED, snapshot(created, player.getObjectId())) : new CreationResult(CreationOutcome.FAILED, null);
		}
	}

	private static VillageMaster availableClanMaster(Player player)
	{
		final var region = player.getWorldRegion();
		if (region == null || player.isInCombat()) { return null; }
		return region.getVisibleObjects().stream().filter(object -> object instanceof VillageMaster npc && npc.isSpawned() && !npc.isAlikeDead()
			&& npc.canInteract(player) && GeoEngine.getInstance().canSeeTarget(player, npc)).map(VillageMaster.class::cast).findFirst().orElse(null);
	}

	@Override
	public ClanFoundingDevelopment.Outcome developAutonomous(MemberRef member, int expectedClanId)
	{
		try (var acquired = acquire(member))
		{
			if (acquired == null || !ordinaryPlayer(acquired.player()) || acquired.player().getClanId() != expectedClanId)
			{
				return ClanFoundingDevelopment.Outcome.NOT_READY;
			}
			final Player player = acquired.player();
			return ClanFoundingDevelopment.payForFirstLevel(member.profileId(), player, availableClanMaster(player),
				() -> PhantomNativeEquipmentPolicy.hasRequiredEquipment(player) || _mandatoryPreparationPending.test(member.profileId(), player));
		}
	}

	@Override
	public ClanDepartureService.Outcome leaveOrdinaryClan(MemberRef member, int expectedClanId, int expectedLeaderId)
	{
		try (var acquired = acquire(member))
		{
			if (acquired == null || !ordinaryPlayer(acquired.player())) { return ClanDepartureService.Outcome.STALE; }
			final Player player = acquired.player();
			final Clan clan = player.getClan();
			return clan == null || clan.getId() != expectedClanId ? ClanDepartureService.Outcome.STALE : ClanDepartureService.leaveOrdinary(player, clan, expectedLeaderId);
		}
	}

	@Override
	public Optional<AutonomousActor> autonomousActor(long profileId)
	{
		try (var lease = _materialization.tryAcquireObservation(profileId).orElse(null))
		{
			if (lease == null) { return Optional.empty(); }
			final Player player = lease.player();
			if (!ordinaryPlayer(player)) { return Optional.empty(); }
			final var clan = player.getClan();
			if (clan != null && (ClanTable.getInstance().getClan(clan.getId()) != clan || clan.getClanMember(player.getObjectId()) == null)) { return Optional.empty(); }
			final long now = System.currentTimeMillis();
			return Optional.of(new AutonomousActor(MemberRef.phantom(profileId, player.getObjectId()), clan == null ? null : snapshot(clan, player.getObjectId()), player.getLevel(),
				now >= player.getClanJoinExpiryTime(), clan == null && player.getLevel() >= 10 && now >= player.getClanCreateExpiryTime() && availableClanMaster(player) != null,
				player.getAdena(), player.getSp()));
		}
	}


	@Override
	public boolean inviteAutonomous(MemberRef leader, MemberRef target, int expectedClanId)
	{
		try (var pair = acquirePair(leader, target))
		{
			return pair != null && ordinaryPlayer(pair.player(leader)) && ordinaryPlayer(pair.player(target))
				&& _invitations.inviteAutonomous(pair.player(leader), pair.player(target), expectedClanId).delivered();
		}
	}

	@Override
	public ClanInvitationService.RespondResult respondAutonomous(MemberRef invitee, MemberRef requester, ClanInvitationService.Response response, ClanInvitationService.InvitationIdentity identity)
	{
		try (var pair = acquirePair(invitee, requester))
		{
			if (pair == null || !ordinaryPlayer(pair.player(invitee)) || !pair.player(requester).isOnline())
			{
				return new ClanInvitationService.RespondResult(ClanInvitationService.RespondOutcome.REQUESTER_UNAVAILABLE, identity);
			}
			return _invitations.respondAutonomous(pair.player(invitee), pair.player(requester), response, identity);
		}
	}

	private static boolean ordinaryPlayer(Player player)
	{
		return player.hasHeadlessOutboundSession() && player.isOnline() && !player.isDedicatedGeoScout() && !player.isAlikeDead()
			&& player.getPrivateStoreType() == org.l2jmobius.gameserver.model.actor.enums.player.PrivateStoreType.NONE;
	}

	@Override
	public Optional<ClanInvitationService.InvitationSnapshot> observeInvitation(MemberRef invitee)
	{
		try (AcquiredPlayer acquired = acquire(invitee))
		{
			return acquired == null ? Optional.empty() : _invitations.observe(acquired.player());
		}
	}

	@Override
	public ClanInvitationService.RespondResult respond(MemberRef invitee, ClanInvitationService.Response response, ClanInvitationService.InvitationIdentity identity)
	{
		if (response == ClanInvitationService.Response.ACCEPT && _invitations.isAutonomous(identity))
		{
			final var requester = resolve(new PhantomDomainRef("character.object", Integer.toString(identity.requesterObjectId()))).orElse(null);
			return respondAutonomous(invitee, requester, response, identity);
		}
		try (AcquiredPlayer acquired = acquire(invitee))
		{
			return acquired == null ? new ClanInvitationService.RespondResult(ClanInvitationService.RespondOutcome.NO_PENDING_INVITE, identity) : _invitations.respond(acquired.player(), response, identity);
		}
	}

	@Override
	public ClanInvitationService.CancelResult cancel(ClanInvitationService.InvitationIdentity identity)
	{
		return _invitations.cancel(identity);
	}

	@Override
	public RoleResult transferLeader(MemberRef requester, MemberRef newLeader, int expectedClanId)
	{
		try (AcquiredPlayer requesterPlayer = acquire(requester); AcquiredPlayer leaderPlayer = acquire(newLeader))
		{
			if ((requesterPlayer == null) || (leaderPlayer == null))
			{
				return new RoleResult(RoleOutcome.STALE, null);
			}
			final Player actor = requesterPlayer.player();
			final Player target = leaderPlayer.player();
			final Clan clan = actor.getClan();
			if ((clan == null) || (clan.getId() != expectedClanId) || (target.getClan() != clan) || (clan.getClanMember(target.getObjectId()) == null))
			{
				return new RoleResult(RoleOutcome.STALE, null);
			}
			if (clan.getLeaderId() == target.getObjectId())
			{
				return new RoleResult(RoleOutcome.ALREADY_SATISFIED, snapshot(clan, target.getObjectId()));
			}
			if (clan.getLeaderId() != actor.getObjectId())
			{
				return new RoleResult(RoleOutcome.UNAUTHORIZED, snapshot(clan, actor.getObjectId()));
			}
			clan.setNewLeader(clan.getClanMember(target.getObjectId()));
			return clan.getLeaderId() == target.getObjectId()
				? new RoleResult(RoleOutcome.COMPLETED, snapshot(clan, target.getObjectId()))
				: new RoleResult(RoleOutcome.FAILED, snapshot(clan, target.getObjectId()));
		}
	}

	@Override
	public ContributionObservation observeContribution(MemberRef member, int expectedClanId, int inventoryObjectId)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return new ContributionObservation(false, 0, 0, 0, "");
			}
			final Player player = acquired.player();
			final Clan clan = player.getClan();
			if ((clan == null) || (clan.getId() != expectedClanId) || (ClanTable.getInstance().getClan(expectedClanId) != clan))
			{
				return new ContributionObservation(false, 0, 0, 0, "");
			}
			final Item inventoryItem = player.getInventory().getItemByObjectId(inventoryObjectId);
			final Item warehouseItem = clan.getWarehouse().getItemByObjectId(inventoryObjectId);
			final Item identity = inventoryItem == null ? warehouseItem : inventoryItem;
			if (identity == null)
			{
				return new ContributionObservation(false, 0, 0, 0, "");
			}
			final long inventoryCount = inventoryItem == null ? 0 : inventoryItem.getCount();
			final long warehouseCount = itemCount(clan.getWarehouse(), identity.getId());
			return new ContributionObservation(true, identity.getId(), inventoryCount, warehouseCount, PhantomClanService.sha256(expectedClanId + "|" + inventoryObjectId + "|" + inventoryCount + "|" + warehouseCount));
		}
	}
	@Override
	public ContributionResult contribute(MemberRef member, int expectedClanId, int inventoryObjectId, long count)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return contribution(ContributionOutcome.STALE, 0, 0, "");
			}
			final Player player = acquired.player();
			final Clan clan = player.getClan();
			if ((clan == null) || (clan.getId() != expectedClanId) || (ClanTable.getInstance().getClan(expectedClanId) != clan))
			{
				return contribution(ContributionOutcome.STALE, 0, 0, "");
			}
			final Item source = player.getInventory().getItemByObjectId(inventoryObjectId);
			if ((source == null) || (count <= 0) || (source.getCount() < count))
			{
				return contribution(ContributionOutcome.SOURCE_MISSING, 0, 0, "");
			}
			if (!source.isDepositable(false) || !source.isAvailable(player, true, false))
			{
				return contribution(ContributionOutcome.NOT_DEPOSITABLE, 0, 0, "");
			}
			final ItemContainer warehouse = clan.getWarehouse();
			final long slots = source.isStackable() ? (warehouse.getItemByItemId(source.getId()) == null ? 1 : 0) : count;
			if (!warehouse.validateCapacity(slots))
			{
				return contribution(ContributionOutcome.CAPACITY, 0, 0, "");
			}
			final long inventoryBefore = source.getCount();
			final long warehouseBefore = itemCount(warehouse, source.getId());
			final Item transferred = player.getInventory().transferItem(ItemProcessType.TRANSFER, inventoryObjectId, count, warehouse, player, null);
			if (transferred == null)
			{
				return contribution(ContributionOutcome.FAILED, 0, 0, "");
			}
			final Item remaining = player.getInventory().getItemByObjectId(inventoryObjectId);
			final long inventoryAfter = remaining == null ? 0 : remaining.getCount();
			final long warehouseAfter = itemCount(warehouse, source.getId());
			final long inventoryDecrease = inventoryBefore - inventoryAfter;
			final long warehouseIncrease = warehouseAfter - warehouseBefore;
			final ContributionOutcome outcome = (inventoryDecrease == count) && (warehouseIncrease == count) ? ContributionOutcome.COMPLETED : ContributionOutcome.INCONSISTENT;
			return contribution(outcome, inventoryDecrease, warehouseIncrease, PhantomClanService.sha256(expectedClanId + "|" + inventoryObjectId + "|" + count + "|" + inventoryBefore + "|" + warehouseBefore + "|" + inventoryAfter + "|" + warehouseAfter));
		}
	}

	@Override
	public WithdrawalOutcome withdraw(MemberRef member, int expectedClanId, int warehouseObjectId, long count)
	{
		return WithdrawalOutcome.UNSUPPORTED;
	}

	@Override
	public ChatResult clanChat(MemberRef member, int expectedClanId, String text)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return new ChatResult(ChatOutcome.STALE, 0);
			}
			final Player player = acquired.player();
			if ((player.getClan() == null) || (player.getClanId() != expectedClanId) || (text.indexOf(8) >= 0))
			{
				return new ChatResult(ChatOutcome.REJECTED, 0);
			}
			final IChatHandler handler = ChatHandler.getInstance().getHandler(ChatType.CLAN);
			if (handler == null)
			{
				return new ChatResult(ChatOutcome.FAILED, 0);
			}
			try (DispatchHandle dispatch = _chatObservation.openGeneratedDispatch(player.getObjectId(), player.getName(), ChatType.CLAN, "", text, System.currentTimeMillis()))
			{
				if (dispatch.descriptor() == null)
				{
					return new ChatResult(ChatOutcome.REJECTED, 0);
				}
				handler.onChat(ChatType.CLAN, player, "", text);
				return dispatch.deliveries() > 0 ? new ChatResult(ChatOutcome.DELIVERED, dispatch.deliveries()) : new ChatResult(ChatOutcome.REJECTED, 0);
			}
		}
	}

	@Override
	public Optional<AllianceObservation> observeAlliance(MemberRef member)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return Optional.empty();
			}
			final Clan clan = acquired.player().getClan();
			if (clan == null)
			{
				return Optional.empty();
			}
			final AllianceIdentity identity = _alliances.currentIdentity(clan).orElse(null);
			return identity == null ? Optional.empty() : Optional.of(new AllianceObservation(identity, clan.getAllyName(), clan.getId()));
		}
	}

	@Override
	public Optional<AllianceActor> allianceActor(MemberRef member)
	{
		try (var acquired = acquire(member))
		{
			if (acquired == null || !ordinaryPlayer(acquired.player())) { return Optional.empty(); }
			final var lifetime = _materialization.find(member.profileId()).orElse(null);
			if (lifetime == null || !lifetime.worldPresent() || !lifetime.actionAdmissionOpen()) { return Optional.empty(); }
			final var observation = _alliances.observeMembership(acquired.player());
			return observation.successful() ? Optional.of(new AllianceActor(member, lifetime.materializedAtNanos(), observation.snapshot())) : Optional.empty();
		}
	}

	@Override
	public MembershipResult durableAlliance(int clanId)
	{
		return _alliances.observeDurableMembership(clanId);
	}

	@Override
	public boolean acknowledgeAlliance(MembershipSnapshot durable)
	{
		return _alliances.reconcileDurableMembership(durable).successful();
	}

	@Override
	public ClanAllianceService.Result createAutonomousAlliance(AllianceActor actor, String name)
	{
		try (var acquired = acquire(actor.member()))
		{
			return acquired != null && sameAllianceLifetime(actor, acquired.player()) && availableClanMaster(acquired.player()) != null
				? _alliances.create(acquired.player(), name, actor.membership().epoch()) : allianceIneligible(ClanAllianceService.Reason.ACTOR_NOT_FOUND);
		}
	}

	@Override
	public ClanAllianceService.Result joinAutonomousAlliance(AllianceActor source, AllianceActor target, AllianceIdentity identity)
	{
		try (var pair = acquirePair(source.member(), target.member()))
		{
			if (pair == null || !sameAllianceLifetime(source, pair.player(source.member())) || !sameAllianceLifetime(target, pair.player(target.member())))
			{
				return allianceIneligible(ClanAllianceService.Reason.TARGET_NOT_FOUND);
			}
			return _alliances.join(pair.player(source.member()), pair.player(target.member()), identity, source.membership().epoch(), target.membership().epoch());
		}
	}

	@Override
	public ClanAllianceService.Result leaveAutonomousAlliance(AllianceActor actor, AllianceIdentity identity)
	{
		try (var acquired = acquire(actor.member()))
		{
			return acquired != null && sameAllianceLifetime(actor, acquired.player()) ? _alliances.leave(acquired.player(), identity, actor.membership().epoch())
				: allianceIneligible(ClanAllianceService.Reason.ACTOR_NOT_FOUND);
		}
	}

	private boolean sameAllianceLifetime(AllianceActor actor, Player player)
	{
		return ordinaryPlayer(player) && _materialization.find(actor.member().profileId()).filter(lifetime -> lifetime.worldPresent() && lifetime.actionAdmissionOpen()
			&& lifetime.characterObjectId() == player.getObjectId() && lifetime.materializedAtNanos() == actor.materializationEpoch()).isPresent();
	}

	@Override
	public ClanAllianceService.Result createAlliance(MemberRef actor, String allianceName)
	{
		try (AcquiredPlayer acquired = acquire(actor))
		{
			return acquired == null ? allianceIneligible(ClanAllianceService.Reason.ACTOR_NOT_FOUND) : _alliances.create(acquired.player(), allianceName);
		}
	}

	@Override
	public ClanAllianceService.Result checkAllianceJoin(MemberRef inviter, MemberRef target)
	{
		try (AcquiredPair pair = acquirePair(inviter, target))
		{
			return pair == null ? allianceIneligible(ClanAllianceService.Reason.TARGET_NOT_FOUND) : _alliances.checkInvite(pair.player(inviter), pair.player(target));
		}
	}

	@Override
	public ClanAllianceService.Result joinAlliance(MemberRef inviter, MemberRef target, AllianceIdentity identity, MembershipEpoch targetEpoch)
	{
		try (AcquiredPair pair = acquirePair(inviter, target))
		{
			return pair == null ? allianceIneligible(ClanAllianceService.Reason.TARGET_NOT_FOUND) : _alliances.join(pair.player(inviter), pair.player(target), identity, targetEpoch);
		}
	}

	@Override
	public ClanAllianceService.Result leaveAlliance(MemberRef actor, AllianceIdentity identity)
	{
		try (AcquiredPlayer acquired = acquire(actor))
		{
			return acquired == null ? allianceIneligible(ClanAllianceService.Reason.ACTOR_NOT_FOUND) : _alliances.leave(acquired.player(), identity);
		}
	}

	@Override
	public ClanAllianceService.ProofResult captureAllianceMembership(AllianceIdentity identity)
	{
		return _alliances.captureMembershipProof(identity);
	}

	@Override
	public ClanAllianceService.Result dissolveAlliance(MemberRef actor, AllianceMembershipProof proof)
	{
		try (AcquiredPlayer acquired = acquire(actor))
		{
			return acquired == null ? allianceIneligible(ClanAllianceService.Reason.ACTOR_NOT_FOUND) : _alliances.dissolveWithProof(acquired.player(), proof);
		}
	}

	@Override
	public Optional<WarIdentity> currentWar(MemberRef first, MemberRef second)
	{
		try (AcquiredPair pair = acquirePair(first, second))
		{
			if (pair == null)
			{
				return Optional.empty();
			}
			final Clan firstClan = pair.player(first).getClan();
			final Clan secondClan = pair.player(second).getClan();
			final Optional<WarIdentity> direct = _wars.currentWar(firstClan, secondClan);
			return direct.isPresent() ? direct : _wars.currentWar(secondClan, firstClan);
		}
	}

	@Override
	public ClanWarService.Result declareWar(MemberRef actor, MemberRef target)
	{
		try (AcquiredPair pair = acquirePair(actor, target))
		{
			if (pair == null)
			{
				return warIneligible(ClanWarService.Reason.ACTOR_NOT_FOUND);
			}
			return _wars.declare(pair.player(actor), pair.player(target).getClan());
		}
	}

	@Override
	public ClanWarService.Result stopWar(MemberRef actor, MemberRef target, long expectedWarId)
	{
		try (AcquiredPair pair = acquirePair(actor, target))
		{
			if (pair == null)
			{
				return warIneligible(ClanWarService.Reason.ACTOR_NOT_FOUND);
			}
			return _wars.stop(pair.player(actor), pair.player(target).getClan(), expectedWarId);
		}
	}

	@Override
	public ClanWarService.Result acceptPeace(MemberRef first, MemberRef second, WarIdentity identity)
	{
		try (AcquiredPair pair = acquirePair(first, second))
		{
			if (pair == null)
			{
				return warIneligible(ClanWarService.Reason.ACTOR_NOT_FOUND);
			}
			final Clan firstClan = pair.player(first).getClan();
			final Clan secondClan = pair.player(second).getClan();
			final Clan source = firstClan.getId() == identity.sourceClanId() ? firstClan : secondClan;
			final Clan target = firstClan.getId() == identity.targetClanId() ? firstClan : secondClan;
			if ((source.getId() != identity.sourceClanId()) || (target.getId() != identity.targetClanId()))
			{
				return new ClanWarService.Result(ClanWarService.Status.STALE, ClanWarService.Reason.STALE_IDENTITY, identity);
			}
			return _wars.endAcceptedReply(source, target, identity.warId());
		}
	}

	@Override
	public RelationshipEvidence relationship(long ownerProfileId, MemberRef subject, long nowEpochMinute)
	{
		if ((_social == null) || (_pvpPolicy == null) || (subject == null))
		{
			return new RelationshipEvidence(false, 0, 0, List.of(), "");
		}
		final var relationSubject = subject.kind() == MemberKind.PHANTOM ? SubjectRef.phantom(subject.profileId()) : SubjectRef.character(subject.characterObjectId());
		final var result = _social.snapshot(ownerProfileId, relationSubject, 8, nowEpochMinute);
		if (!result.available())
		{
			return new RelationshipEvidence(false, 0, 0, List.of(), "");
		}
		final var snapshot = result.value();
		final var relationship = snapshot.relationship();
		final int trust = relationship.relationship().getOrDefault("trust", 0);
		final int respect = relationship.relationship().getOrDefault("respect", 0);
		final int anger = relationship.relationship().getOrDefault("anger", 0);
		final int friendship = relationship.relationship().getOrDefault("friendship", 0);
		final int rivalry = relationship.relationship().getOrDefault("rivalry", 0);
		final int hostility = relationship.reputation().getOrDefault("hostility", 0);
		final int hostilityScore = anger + rivalry + hostility - Math.max(0, trust) - Math.max(0, friendship);
		final int affinityScore = trust + respect + friendship - anger - rivalry - hostility;
		final List<String> hostileEvents = snapshot.memories().stream().filter(memory -> HOSTILE_EVENTS.contains(memory.eventKey()) && (memory.expiryMinute() > nowEpochMinute)).map(memory -> memory.eventId() + ":" + memory.evidenceHash()).sorted().limit(8).toList();
		final String authority = PhantomClanService.sha256("clan.relationship|" + _pvpPolicy.hash() + "|" + snapshot.authorityHash() + "|" + hostilityScore + "|" + affinityScore + "|" + hostileEvents);
		return new RelationshipEvidence(true, hostilityScore, affinityScore, hostileEvents, authority);
	}

	@Override
	public boolean recordRelation(long ownerProfileId, MemberRef subject, String eventKey, String operationId, String evidenceHash, long happenedEpochMinute)
	{
		if ((_social == null) || (subject == null) || (subject.kind() != MemberKind.PHANTOM))
		{
			return false;
		}
		final String eventId = PhantomSocialModel.sha256("clan.social|goal027cp2|" + eventKey + "|" + ownerProfileId + "|" + subject.profileId() + "|" + operationId);
		final SubjectRef socialSubject = SubjectRef.phantom(subject.profileId());
		return _social.record(new SocialEvent(ownerProfileId, eventId, eventKey, socialSubject, happenedEpochMinute, 1000, evidenceHash, _affiliationContexts.resolve(ownerProfileId, socialSubject))).durable();
	}

	@Override
	public long pvpPairCooldownMillis()
	{
		return _pvpPolicy == null ? 1_000 : Math.multiplyExact(_pvpPolicy.limits().pairCooldownSeconds(), 1_000L);
	}

	@Override
	public ChatResult allianceChat(MemberRef member, AllianceIdentity expectedIdentity, String text)
	{
		try (AcquiredPlayer acquired = acquire(member))
		{
			if (acquired == null)
			{
				return new ChatResult(ChatOutcome.STALE, 0);
			}
			final Player player = acquired.player();
			final Clan clan = player.getClan();
			if ((clan == null) || !_alliances.currentIdentity(clan).filter(expectedIdentity::equals).isPresent() || (text.indexOf(8) >= 0))
			{
				return new ChatResult(ChatOutcome.STALE, 0);
			}
			final IChatHandler handler = ChatHandler.getInstance().getHandler(ChatType.ALLIANCE);
			if (handler == null)
			{
				return new ChatResult(ChatOutcome.FAILED, 0);
			}
			try (DispatchHandle dispatch = _chatObservation.openGeneratedDispatch(player.getObjectId(), player.getName(), ChatType.ALLIANCE, "", text, System.currentTimeMillis()))
			{
				if (dispatch.descriptor() == null)
				{
					return new ChatResult(ChatOutcome.REJECTED, 0);
				}
				handler.onChat(ChatType.ALLIANCE, player, "", text);
				return dispatch.deliveries() > 0 ? new ChatResult(ChatOutcome.DELIVERED, dispatch.deliveries()) : new ChatResult(ChatOutcome.REJECTED, 0);
			}
		}
	}
	private ClanSnapshot snapshot(Clan clan, int memberObjectId)
	{
		return new ClanSnapshot(clan.getId(), clan.getName(), clan.getLeaderId(), clan.getLevel(), clan.getMembersCount(), Math.max(clan.getMembersCount(), clan.getMaxNrOfMembers(0)), clan.getAllyId(), clan.getReputationScore(), PhantomClanService.sha256(clan.getId() + "|" + clan.getName() + "|" + clan.getLeaderId() + "|" + memberObjectId));
	}

	private AcquiredPlayer acquire(MemberRef member)
	{
		if (member == null)
		{
			return null;
		}
		if (member.kind() == MemberKind.REAL)
		{
			final Player player = World.getInstance().getPlayer(member.characterObjectId());
			return player == null ? null : new AcquiredPlayer(player, null);
		}
		final Optional<ActionLease> lease = _materialization.tryAcquireAction(member.profileId());
		if (lease.isEmpty())
		{
			return null;
		}
		final ActionLease acquired = lease.orElseThrow();
		final Player player = acquired.player();
		final Optional<MemberRef> current = currentMember(member.profileId());
		if (current.isEmpty() || !current.get().equals(member) || (player.getObjectId() != member.characterObjectId()) || (World.getInstance().getPlayer(player.getObjectId()) != player))
		{
			acquired.close();
			return null;
		}
		return new AcquiredPlayer(player, acquired);
	}

	private static long itemCount(ItemContainer container, int itemId)
	{
		long result = 0;
		for (Item item : container.getItems())
		{
			if (item.getId() == itemId)
			{
				result = Math.addExact(result, item.getCount());
			}
		}
		return result;
	}

	private static ContributionResult contribution(ContributionOutcome outcome, long inventoryDecrease, long warehouseIncrease, String evidenceHash)
	{
		return new ContributionResult(outcome, inventoryDecrease, warehouseIncrease, evidenceHash);
	}

	private static long parsePositive(String value)
	{
		try
		{
			final long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : -1;
		}
		catch (RuntimeException exception)
		{
			return -1;
		}
	}

	private AcquiredPair acquirePair(MemberRef first, MemberRef second)
	{
		if ((first == null) || (second == null) || first.equals(second))
		{
			return null;
		}
		final boolean natural = leaseKey(first) < leaseKey(second);
		final MemberRef low = natural ? first : second;
		final MemberRef high = natural ? second : first;
		final AcquiredPlayer lowPlayer = acquire(low);
		if (lowPlayer == null)
		{
			return null;
		}
		final AcquiredPlayer highPlayer = acquire(high);
		if (highPlayer == null)
		{
			lowPlayer.close();
			return null;
		}
		return new AcquiredPair(low, lowPlayer, high, highPlayer);
	}

	private static long leaseKey(MemberRef member)
	{
		return member.kind() == MemberKind.PHANTOM ? member.profileId() : (Long.MAX_VALUE / 2) + member.characterObjectId();
	}

	private static ClanAllianceService.Result allianceIneligible(ClanAllianceService.Reason reason)
	{
		return new ClanAllianceService.Result(ClanAllianceService.Status.INELIGIBLE, reason, null);
	}

	private static ClanWarService.Result warIneligible(ClanWarService.Reason reason)
	{
		return new ClanWarService.Result(ClanWarService.Status.INELIGIBLE, reason, null);
	}

	private static final class AcquiredPair implements AutoCloseable
	{
		private final MemberRef _firstRef;
		private final AcquiredPlayer _first;
		private final MemberRef _secondRef;
		private final AcquiredPlayer _second;

		private AcquiredPair(MemberRef firstRef, AcquiredPlayer first, MemberRef secondRef, AcquiredPlayer second)
		{
			_firstRef = firstRef;
			_first = first;
			_secondRef = secondRef;
			_second = second;
		}

		private Player player(MemberRef member)
		{
			if (_firstRef.equals(member))
			{
				return _first.player();
			}
			if (_secondRef.equals(member))
			{
				return _second.player();
			}
			throw new IllegalArgumentException("Member does not belong to acquired diplomacy pair.");
		}

		@Override
		public void close()
		{
			_second.close();
			_first.close();
		}
	}
	private static final class AcquiredPlayer implements AutoCloseable
	{
		private final Player _player;
		private final ActionLease _lease;

		private AcquiredPlayer(Player player, ActionLease lease)
		{
			_player = player;
			_lease = lease;
		}

		private Player player()
		{
			return _player;
		}

		@Override
		public void close()
		{
			if (_lease != null)
			{
				_lease.close();
			}
		}
	}
}

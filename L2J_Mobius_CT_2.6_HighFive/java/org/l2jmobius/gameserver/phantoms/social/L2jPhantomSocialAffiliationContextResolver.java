/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.social;

import java.util.Objects;

import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanAllianceService;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.AffiliationKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SocialEventContext;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectKind;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.SubjectRef;

/** O(1) High Five resolver over exact materialization, World and clan services. */
public final class L2jPhantomSocialAffiliationContextResolver implements PhantomSocialAffiliationContextPort
{
	private final PhantomMaterializationService _materialization;
	private final ClanAllianceService _alliances;
	private final ClanWarService _wars;

	public L2jPhantomSocialAffiliationContextResolver(PhantomMaterializationService materialization)
	{
		this(materialization, ClanAllianceService.getInstance(), ClanWarService.getInstance());
	}

	L2jPhantomSocialAffiliationContextResolver(PhantomMaterializationService materialization, ClanAllianceService alliances, ClanWarService wars)
	{
		_materialization = Objects.requireNonNull(materialization);
		_alliances = Objects.requireNonNull(alliances);
		_wars = Objects.requireNonNull(wars);
	}

	@Override
	public SocialEventContext resolve(long ownerProfileId, SubjectRef subject)
	{
		if ((ownerProfileId <= 0) || (subject == null))
		{
			return SocialEventContext.NONE;
		}
		final Player owner = playerForProfile(ownerProfileId);
		final Player counterpart = playerFor(subject);
		if ((owner == null) || (counterpart == null) || (owner == counterpart))
		{
			return SocialEventContext.NONE;
		}
		final Clan ownerClan = owner.getClan();
		final Clan counterpartClan = counterpart.getClan();
		if ((ownerClan == null) || (counterpartClan == null) || (ownerClan.getId() <= 0) || (counterpartClan.getId() <= 0))
		{
			return SocialEventContext.NONE;
		}
		if (ownerClan.getId() == counterpartClan.getId())
		{
			return new SocialEventContext(AffiliationKind.SAME_CLAN);
		}
		if (_wars.currentWar(ownerClan, counterpartClan).isPresent() || _wars.currentWar(counterpartClan, ownerClan).isPresent())
		{
			return new SocialEventContext(AffiliationKind.CLAN_WAR);
		}
		final var ownerAlliance = _alliances.currentIdentity(ownerClan);
		if (ownerAlliance.isPresent() && ownerAlliance.equals(_alliances.currentIdentity(counterpartClan)))
		{
			return new SocialEventContext(AffiliationKind.SAME_ALLIANCE);
		}
		return SocialEventContext.NONE;
	}

	private Player playerFor(SubjectRef subject)
	{
		return subject.kind() == SubjectKind.PHANTOM_PROFILE ? playerForProfile(subject.id()) : World.getInstance().getPlayer((int) subject.id());
	}

	private Player playerForProfile(long profileId)
	{
		final var materialized = _materialization.find(profileId).orElse(null);
		return materialized == null ? null : World.getInstance().getPlayer(materialized.characterObjectId());
	}
}
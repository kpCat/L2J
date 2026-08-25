/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.util.Objects;
import java.util.function.LongToIntFunction;

import org.l2jmobius.gameserver.config.PvpConfig;
import org.l2jmobius.gameserver.config.RatesConfig;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.clan.Clan;
import org.l2jmobius.gameserver.model.clan.ClanWarService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.pvp.PhantomKarmaRecoveryPolicy.Snapshot;

/** O(1) live resolver over canonical materialization, World, Player and clan-war state. */
public final class L2jPhantomKarmaRecoveryContext implements PhantomKarmaRecoveryContextPort
{
	private final LongToIntFunction _profileCharacterObjectId;
	private final ClanWarService _wars;

	public L2jPhantomKarmaRecoveryContext(PhantomMaterializationService materialization)
	{
		this(profileId -> materialization.find(profileId).map(PhantomMaterializationService.MaterializationSnapshot::characterObjectId).orElse(0), ClanWarService.getInstance());
	}

	L2jPhantomKarmaRecoveryContext(LongToIntFunction profileCharacterObjectId, ClanWarService wars)
	{
		_profileCharacterObjectId = Objects.requireNonNull(profileCharacterObjectId);
		_wars = Objects.requireNonNull(wars);
	}

	@Override
	public Snapshot observe(long profileId, int counterpartObjectId)
	{
		if ((profileId <= 0) || (counterpartObjectId <= 0))
		{
			return Snapshot.UNAVAILABLE;
		}
		final int characterObjectId = _profileCharacterObjectId.applyAsInt(profileId);
		if (characterObjectId <= 0)
		{
			return Snapshot.UNAVAILABLE;
		}
		final Player player = World.getInstance().getPlayer(characterObjectId);
		final Player counterpart = World.getInstance().getPlayer(counterpartObjectId);
		if ((player == null) || (counterpart == null) || (player == counterpart))
		{
			return Snapshot.UNAVAILABLE;
		}
		final int karma = Math.max(0, player.getKarma());
		final int pkKills = Math.max(0, player.getPkKills());
		final long currentExp = Math.max(0, player.getExp());
		final long expBeforeDeath = Math.max(0, player.getExpBeforeDeath());
		final long expDebt = expBeforeDeath > currentExp ? expBeforeDeath - currentExp : 0;
		return new Snapshot(true, karma, pkKills, currentExp, expBeforeDeath, expDebt, deathDropRiskBasisPoints(karma, pkKills), predictedKarmaAfterNativeDeath(karma), player.isInParty(), activeWar(player.getClan(), counterpart.getClan()));
	}
	public static int predictedKarmaAfterNativeDeath(int karma)
	{
		return karma <= 0 ? 0 : karma < 200 ? 0 : karma - (karma / 4);
	}

	public static int deathDropRiskBasisPoints(int karma, int pkKills)
	{
		final int maximumRate = Math.max(Math.max(RatesConfig.KARMA_RATE_DROP, RatesConfig.KARMA_RATE_DROP_EQUIP), Math.max(RatesConfig.KARMA_RATE_DROP_EQUIP_WEAPON, RatesConfig.KARMA_RATE_DROP_ITEM));
		if ((karma <= 0) || (pkKills < PvpConfig.KARMA_PK_LIMIT) || (RatesConfig.KARMA_DROP_LIMIT <= 0) || (maximumRate <= 0))
		{
			return 0;
		}
		return Math.min(10000, maximumRate * 100);
	}

	private boolean activeWar(Clan actor, Clan counterpart)
	{
		return (actor != null) && (counterpart != null) && (actor.getId() > 0) && (counterpart.getId() > 0) && (_wars.currentWar(actor, counterpart).isPresent() || _wars.currentWar(counterpart, actor).isPresent());
	}
}

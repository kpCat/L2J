/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.Objects;
import java.util.function.LongSupplier;

import org.l2jmobius.gameserver.managers.GrandBossManager;
import org.l2jmobius.gameserver.managers.RaidBossSpawnManager;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.actor.enums.npc.RaidBossStatus;
import org.l2jmobius.gameserver.model.actor.instance.GrandBoss;
import org.l2jmobius.gameserver.model.actor.instance.RaidBoss;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossObservation;

public final class L2jPhantomRaidAuthority implements PhantomRaidAuthority
{
	private final LongSupplier _clock;

	public L2jPhantomRaidAuthority()
	{
		this(System::currentTimeMillis);
	}

	L2jPhantomRaidAuthority(LongSupplier clock)
	{
		_clock = Objects.requireNonNull(clock);
	}

	@Override
	public BossObservation observe(ContentKind contentKind, int npcId)
	{
		if ((contentKind != ContentKind.RAID) && (contentKind != ContentKind.EPIC))
		{
			throw new IllegalArgumentException("Raid authority accepts only RAID or EPIC content.");
		}
		if (npcId <= 0)
		{
			throw new IllegalArgumentException("Raid authority requires an exact NPC id.");
		}
		return contentKind == ContentKind.RAID ? observeRaid(npcId) : observeEpic(npcId);
	}

	private BossObservation observeRaid(int npcId)
	{
		final long now = _clock.getAsLong();
		final RaidBossSpawnManager manager = RaidBossSpawnManager.getInstance();
		final RaidBossStatus status = manager.getRaidBossStatusId(npcId);
		final RaidBoss live = manager.getBosses().get(npcId);
		final StatSet stored = manager.getStoredInfo().get(npcId);
		final Long respawn = stored == null ? null : stored.getLong("respawnTime", 0L);
		final boolean exact = (live != null) && (live.getId() == npcId);
		return new BossObservation(ContentKind.RAID, npcId, manager.isDefined(npcId), status.name(), live != null, exact, (live != null) && (live.isDead() || live.isAlikeDead()), respawn, now, "RaidBossSpawnManager.getRaidBossStatusId+getBosses+isDefined+getStoredInfo");
	}

	private BossObservation observeEpic(int npcId)
	{
		final long now = _clock.getAsLong();
		final GrandBossManager manager = GrandBossManager.getInstance();
		final GrandBoss live = manager.getBoss(npcId);
		final StatSet stored = manager.getStatSet(npcId);
		String rawStatus = "UNDEFINED";
		if (stored != null)
		{
			try
			{
				rawStatus = Integer.toString(manager.getStatus(npcId));
			}
			catch (NullPointerException ignored)
			{
				// GrandBossManager#getStatus unboxes a missing map value; absence stays unknown.
			}
		}
		final Long respawn = stored == null ? null : stored.getLong("respawn_time", 0L);
		final boolean exact = (live != null) && (live.getId() == npcId);
		return new BossObservation(ContentKind.EPIC, npcId, stored != null, rawStatus, live != null, exact, (live != null) && (live.isDead() || live.isAlikeDead()), respawn, now, "GrandBossManager.getBoss+getStatus+getStatSet");
	}
}

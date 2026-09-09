/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.questinstance;

import java.util.Map;

import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Content;
import org.l2jmobius.gameserver.phantoms.questinstance.PhantomQuestInstanceCatalog.Step;

/** Narrow observation and native-owner boundary for the Goal036 service. */
public interface PhantomQuestInstanceBackend
{
	Observation observe(long profileId, Content content);

	Target locate(long profileId, Content content, Step step);

	ActionResult invoke(long profileId, Content content, Step step, String expectedFingerprint);

	ActionResult move(long profileId, Content content, Step step, Target expectedTarget);

	enum QuestStatus
	{
		ABSENT,
		CREATED,
		STARTED,
		COMPLETED
	}

	enum ActionStatus
	{
		ISSUED,
		IDEMPOTENT,
		STALE,
		UNAVAILABLE,
		REJECTED,
		FAILURE
	}

	record QuestView(QuestStatus status, int cond)
	{
		public QuestView
		{
			if ((status == null) || (cond < 0) || ((status != QuestStatus.STARTED) && (cond != 0)))
			{
				throw new IllegalArgumentException("Invalid quest observation.");
			}
		}
	}

	record Observation(int playerObjectId, int level, String race, int classId, int baseClassId, boolean dead, boolean busy, QuestView quest, int x, int y, int z, int instanceId, int instanceTemplateId, long instanceReuseUntilMillis, int partySize, boolean partyLeader, boolean partyLevelEligible, int activeWeaponItemId, Map<Integer, Long> itemCounts, Map<Integer, Integer> itemObjectIds, String fingerprint)
	{
		public Observation
		{
			itemCounts = Map.copyOf(itemCounts);
			itemObjectIds = Map.copyOf(itemObjectIds);
			if ((playerObjectId <= 0) || (level < 1) || (race == null) || race.isBlank() || (classId < 0) || (baseClassId < 0) || (quest == null) || (instanceId < 0) || (instanceTemplateId < 0) || (instanceReuseUntilMillis < 0) || (partySize < 0) || (activeWeaponItemId < 0) || (fingerprint == null) || !fingerprint.matches("[0-9a-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid supported content observation.");
			}
		}

		public long itemCount(int itemId)
		{
			return itemCounts.getOrDefault(itemId, 0L);
		}

		public int itemObjectId(int itemId)
		{
			return itemObjectIds.getOrDefault(itemId, 0);
		}
	}

	record Target(int objectId, int npcId, int x, int y, int z, int instanceId, double distance, boolean liveObject)
	{
		public Target
		{
			if ((objectId < 0) || (npcId <= 0) || (instanceId < 0) || !Double.isFinite(distance) || (distance < 0) || (liveObject != (objectId > 0)))
			{
				throw new IllegalArgumentException("Invalid supported content target.");
			}
		}
	}

	record ActionResult(ActionStatus status, Observation after, String reasonKey)
	{
		public ActionResult
		{
			if ((status == null) || (reasonKey == null) || reasonKey.isBlank())
			{
				throw new IllegalArgumentException("Invalid supported content action result.");
			}
		}
	}
}

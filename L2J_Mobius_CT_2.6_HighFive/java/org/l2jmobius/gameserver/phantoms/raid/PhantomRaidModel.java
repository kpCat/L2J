/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;

public final class PhantomRaidModel
{
	private PhantomRaidModel()
	{
	}

	public enum TargetAvailability
	{
		AVAILABLE,
		UNAVAILABLE,
		UNKNOWN
	}

	public enum ReadinessStatus
	{
		TARGET_UNKNOWN,
		TARGET_UNAVAILABLE,
		GROUP_ABSENT,
		GROUP_INCOMPLETE,
		GROUP_INCAPABLE,
		GROUP_READY
	}

	public record ContentSnapshot(ContentRequirementFact requirement, NpcFact npc, String recommendationHash)
	{
		public ContentSnapshot
		{
			Objects.requireNonNull(requirement, "requirement");
			Objects.requireNonNull(npc, "npc");
			final NpcKind expectedKind = switch (requirement.contentKind())
			{
				case RAID -> NpcKind.RAID_BOSS;
				case EPIC -> NpcKind.GRAND_BOSS;
				default -> null;
			};
			if ((expectedKind == null) || (requirement.npcId() == null) || (requirement.npcId() != npc.npcId()) || (npc.kind() != expectedKind) || (recommendationHash == null) || !recommendationHash.matches("[0-9a-f]{64}"))
			{
				throw new IllegalArgumentException("Invalid raid content snapshot.");
			}
		}
	}

	public record BossObservation(ContentKind contentKind, int npcId, boolean defined, String rawStatus, boolean livePresent, boolean liveIdentityExact, boolean liveDead, Long respawnTimeMillis, long observedAtMillis, String source)
	{
		public BossObservation
		{
			Objects.requireNonNull(contentKind, "contentKind");
			if (((contentKind != ContentKind.RAID) && (contentKind != ContentKind.EPIC)) || (npcId <= 0) || (rawStatus == null) || rawStatus.isBlank() || (respawnTimeMillis != null && respawnTimeMillis < 0) || (observedAtMillis < 0) || (source == null) || source.isBlank() || (!livePresent && (liveIdentityExact || liveDead)))
			{
				throw new IllegalArgumentException("Invalid boss observation.");
			}
		}

		public TargetAvailability availability()
		{
			if (contentKind == ContentKind.RAID)
			{
				if (defined && "ALIVE".equals(rawStatus) && livePresent && liveIdentityExact && !liveDead)
				{
					return TargetAvailability.AVAILABLE;
				}
				if (defined && "DEAD".equals(rawStatus) && (!livePresent || (liveIdentityExact && liveDead)))
				{
					return TargetAvailability.UNAVAILABLE;
				}
				return TargetAvailability.UNKNOWN;
			}
			if (livePresent && liveIdentityExact && !liveDead)
			{
				return TargetAvailability.AVAILABLE;
			}
			if (!livePresent && (respawnTimeMillis != null) && (respawnTimeMillis > observedAtMillis))
			{
				return TargetAvailability.UNAVAILABLE;
			}
			return TargetAvailability.UNKNOWN;
		}
	}

	public record CapabilityAssessment(CapabilityRequirement requirement, int satisfyingMembers)
	{
		public CapabilityAssessment
		{
			Objects.requireNonNull(requirement, "requirement");
			if (satisfyingMembers < 0)
			{
				throw new IllegalArgumentException("Invalid capability assessment.");
			}
		}

		public boolean satisfied()
		{
			return satisfyingMembers >= requirement.minimumCount();
		}
	}

	public record RaidReadiness(String contentId, ContentSnapshot content, BossObservation target, TargetAvailability targetAvailability, CurrentForceObservation force, List<CapabilityAssessment> capabilities, ReadinessStatus status, String reason)
	{
		public RaidReadiness
		{
			if ((contentId == null) || contentId.isBlank() || (targetAvailability == null) || (force == null) || (capabilities == null) || (status == null) || (reason == null) || reason.isBlank())
			{
				throw new IllegalArgumentException("Invalid raid readiness result.");
			}
			capabilities = List.copyOf(capabilities);
			if ((status == ReadinessStatus.GROUP_READY) && (targetAvailability != TargetAvailability.AVAILABLE))
			{
				throw new IllegalArgumentException("A ready group requires an available target.");
			}
		}

		public boolean groupReady()
		{
			return status == ReadinessStatus.GROUP_READY;
		}
	}
}

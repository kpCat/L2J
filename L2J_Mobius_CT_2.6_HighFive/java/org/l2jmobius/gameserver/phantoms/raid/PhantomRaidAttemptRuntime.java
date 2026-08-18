/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidAssemblyService.ReadyReceipt;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptRegistry.Registration;

/**
 * Mutating encounter port used only by the bounded attempt owner. Implementations
 * may reuse shared Combat, PartyTactics and PartyRoute services, but own no worker.
 */
public interface PhantomRaidAttemptRuntime
{
	MechanicAdvance advanceMechanic(MechanicContext context, CurrentForceSnapshot force);

	EngagementAdvance advanceEngagement(EngagementContext context, CurrentForceSnapshot force);

	RetreatAdvance advanceRetreat(RetreatContext context, CurrentForceSnapshot force);

	void cancel(String attemptAuthorityHash);

	void complete(String attemptAuthorityHash);

	void beginStop();

	record MechanicContext(String attemptAuthorityHash, PhantomRaidEncounterProfile profile, Registration registration, MemberRef leader, long logicalDeadlineNanos, PhantomCancellationToken token)
	{
		public MechanicContext
		{
			validateAuthority(attemptAuthorityHash);
			Objects.requireNonNull(profile);
			Objects.requireNonNull(registration);
			Objects.requireNonNull(leader);
			Objects.requireNonNull(token);
			if (!profile.entryGated() || !profile.contentId().equals(registration.contentId()) || (logicalDeadlineNanos < 0))
			{
				throw new IllegalArgumentException("Invalid scripted raid mechanic context.");
			}
		}
	}

	record EngagementContext(String attemptAuthorityHash, PhantomRaidEncounterProfile profile, PhantomRaidTargetEvidence target, int maximumActorLevel, long logicalDeadlineNanos, PhantomCancellationToken token)
	{
		public EngagementContext
		{
			validateAuthority(attemptAuthorityHash);
			Objects.requireNonNull(profile);
			Objects.requireNonNull(target);
			Objects.requireNonNull(token);
			if ((maximumActorLevel < 1) || (maximumActorLevel > 1000) || (logicalDeadlineNanos < 0) || (profile.contentKind() != target.contentKind()) || (profile.npcKind() != target.npcKind()) || (profile.npcId() != target.npcId()) || target.dead())
			{
				throw new IllegalArgumentException("Invalid exact raid engagement context.");
			}
		}
	}

	record RetreatContext(String attemptAuthorityHash, PhantomRaidEncounterProfile profile, ReadyReceipt ready, Registration registration, int instanceId, long logicalDeadlineNanos, PhantomCancellationToken token)
	{
		public RetreatContext
		{
			validateAuthority(attemptAuthorityHash);
			Objects.requireNonNull(profile);
			Objects.requireNonNull(ready);
			Objects.requireNonNull(token);
			if ((instanceId < 0) || (logicalDeadlineNanos < 0) || (profile.entryGated() != (registration != null)))
			{
				throw new IllegalArgumentException("Invalid objective raid retreat context.");
			}
		}
	}

	record MechanicAdvance(RuntimeStatus status, PhantomRaidTargetEvidence revealedTarget, String reasonKey)
	{
		public MechanicAdvance
		{
			Objects.requireNonNull(status);
			if ((reasonKey == null) || reasonKey.isBlank() || ((status == RuntimeStatus.TARGET_REVEALED) != (revealedTarget != null)))
			{
				throw new IllegalArgumentException("Invalid raid mechanic advance result.");
			}
		}
	}

	record EngagementAdvance(RuntimeStatus status, boolean actualTargetDeathObserved, boolean nativeLootComplete, String reasonKey)
	{
		public EngagementAdvance
		{
			Objects.requireNonNull(status);
			if ((reasonKey == null) || reasonKey.isBlank() || (nativeLootComplete && !actualTargetDeathObserved))
			{
				throw new IllegalArgumentException("Invalid raid engagement advance result.");
			}
		}
	}

	record RetreatAdvance(RuntimeStatus status, String reasonKey)
	{
		public RetreatAdvance
		{
			Objects.requireNonNull(status);
			if ((reasonKey == null) || reasonKey.isBlank() || ((status != RuntimeStatus.INTERMEDIATE) && (status != RuntimeStatus.COMPLETE) && (status != RuntimeStatus.INVALID)))
			{
				throw new IllegalArgumentException("Invalid raid retreat advance result.");
			}
		}
	}

	enum RuntimeStatus
	{
		INTERMEDIATE,
		TARGET_REVEALED,
		TARGET_LOST,
		NO_CONTROLLABLE_OFFENSE,
		PROVIDER_UNAVAILABLE,
		WIPED,
		INVALID,
		COMPLETE
	}

	private static void validateAuthority(String authority)
	{
		if ((authority == null) || !authority.matches("[0-9A-Fa-f]{64}"))
		{
			throw new IllegalArgumentException("Invalid attempt authority hash.");
		}
	}
}

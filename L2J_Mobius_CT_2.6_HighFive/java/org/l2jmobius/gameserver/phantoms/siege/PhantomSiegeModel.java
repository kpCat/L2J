/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.siege;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class PhantomSiegeModel
{
	public enum NativeSide
	{
		NONE,
		ATTACKER,
		DEFENDER
	}

	public enum Role
	{
		COMMANDER,
		SUPPORT,
		RANGED,
		FRONTLINE,
		RESERVE
	}

	public enum Stage
	{
		OBSERVING,
		REGISTRATION,
		REGISTERED,
		GATHERING,
		WAITING_START,
		ATTACKING,
		DEFENDING,
		RETREATING,
		COMPLETE,
		ABANDONED,
		EXPIRED
	}

	public enum RegistrationOutcome
	{
		REGISTERED,
		ALREADY_REGISTERED,
		NOT_LEADER,
		INELIGIBLE,
		CLOSED,
		STALE,
		FAILED
	}

	public enum TargetKind
	{
		PLAYER,
		DOOR
	}

	public record CastleSnapshot(int castleId, String name, long siegeDateMillis, long registrationEndMillis, boolean registrationOpen, boolean inProgress, boolean zoneActive, int ownerClanId, List<Integer> attackerClanIds, List<Integer> defenderClanIds, List<Integer> pendingClanIds, String authorityHash)
	{
		public CastleSnapshot
		{
			if ((castleId <= 0) || (name == null) || name.isBlank() || (siegeDateMillis < 0) || (registrationEndMillis < 0) || (ownerClanId < 0) || (attackerClanIds == null) || (defenderClanIds == null) || (pendingClanIds == null) || !hash(authorityHash))
			{
				throw new IllegalArgumentException("Invalid native castle snapshot.");
			}
			attackerClanIds = canonicalIds(attackerClanIds);
			defenderClanIds = canonicalIds(defenderClanIds);
			pendingClanIds = canonicalIds(pendingClanIds);
		}

		public boolean attacker(int clanId)
		{
			return attackerClanIds.contains(clanId);
		}

		public boolean defender(int clanId)
		{
			return defenderClanIds.contains(clanId) || (ownerClanId == clanId);
		}
	}

	public record ActorSnapshot(long profileId, int objectId, int clanId, int leaderObjectId, int clanLevel, int allianceId, int ownedCastleId, int classId, int level, int instanceId, double hpPercent, boolean dead, boolean inSiege, NativeSide side, String authorityHash)
	{
		public ActorSnapshot
		{
			if ((profileId <= 0) || (objectId <= 0) || (clanId <= 0) || (leaderObjectId <= 0) || (clanLevel < 0) || (allianceId < 0) || (ownedCastleId < 0) || (classId < 0) || (level < 1) || (instanceId < 0) || !Double.isFinite(hpPercent) || (hpPercent < 0) || (hpPercent > 100) || (side == null) || !hash(authorityHash))
			{
				throw new IllegalArgumentException("Invalid native siege actor snapshot.");
			}
		}

		public boolean leader()
		{
			return objectId == leaderObjectId;
		}
	}

	public record MemberSnapshot(long profileId, int objectId, int classId, int level, boolean online, boolean materialized, List<String> capabilities)
	{
		public MemberSnapshot
		{
			if ((profileId <= 0) || (objectId <= 0) || (classId < 0) || (level < 1) || (capabilities == null) || (capabilities.size() > 32))
			{
				throw new IllegalArgumentException("Invalid siege member snapshot.");
			}
			capabilities = capabilities.stream().map(Objects::requireNonNull).filter(value -> !value.isBlank()).distinct().sorted().toList();
		}
	}

	public record Participant(MemberSnapshot member, Role role)
	{
		public Participant
		{
			Objects.requireNonNull(member);
			Objects.requireNonNull(role);
		}
	}

	public record Target(TargetKind kind, int objectId, int castleId, int nativeId, NativeSide side, double distance, String authorityHash)
	{
		public Target
		{
			if ((kind == null) || (objectId <= 0) || (castleId <= 0) || (nativeId <= 0) || (side == null) || !Double.isFinite(distance) || (distance < 0) || !hash(authorityHash))
			{
				throw new IllegalArgumentException("Invalid native siege target.");
			}
		}
	}

	public record RegistrationResult(RegistrationOutcome outcome, CastleSnapshot castle, ActorSnapshot actor, String reasonKey)
	{
		public RegistrationResult
		{
			Objects.requireNonNull(outcome);
			reasonKey = requireKey(reasonKey);
		}
	}

	public record AdvanceResult(Stage stage, String reasonKey, List<Participant> participants, String authorityHash)
	{
		public AdvanceResult
		{
			Objects.requireNonNull(stage);
			reasonKey = requireKey(reasonKey);
			participants = participants == null ? List.of() : List.copyOf(participants);
			if ((participants.size() > 32) || ((authorityHash != null) && !hash(authorityHash)))
			{
				throw new IllegalArgumentException("Invalid siege advance result.");
			}
		}

		public boolean terminal()
		{
			return (stage == Stage.COMPLETE) || (stage == Stage.ABANDONED) || (stage == Stage.EXPIRED);
		}
	}

	private PhantomSiegeModel()
	{
	}

	public static String sha256(String value)
	{
		try
		{
			return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static List<Integer> canonicalIds(List<Integer> values)
	{
		if ((values.size() > 64) || values.stream().anyMatch(value -> (value == null) || (value <= 0)))
		{
			throw new IllegalArgumentException("Native siege clan IDs are invalid or unbounded.");
		}
		return values.stream().distinct().sorted().toList();
	}

	private static boolean hash(String value)
	{
		return (value != null) && value.matches("[0-9A-F]{64}");
	}

	private static String requireKey(String value)
	{
		if ((value == null) || !value.matches("[a-z][a-z0-9_.-]{0,95}"))
		{
			throw new IllegalArgumentException("Invalid siege reason key.");
		}
		return value;
	}
}

/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.pvp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

public final class PhantomPvpModel
{
	public static final String COMPONENT_TYPE = "pvp.threat";
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_AUTHORITY_LENGTH = 64;
	public static final int MAX_RECEIPT_LENGTH = 96;
	public static final int MAX_REASON_LENGTH = 96;

	public enum Source
	{
		ACTUAL_ATTACK(false),
		FARMING_ESCALATION(true),
		PARTY_DEFENSE(false),
		REVENGE(true);

		private final boolean _proactive;

		Source(boolean proactive)
		{
			_proactive = proactive;
		}

		public boolean proactive()
		{
			return _proactive;
		}
	}

	public enum Stage
	{
		OBSERVE,
		WARN,
		HELP,
		ENGAGE,
		RETREAT,
		DISENGAGE,
		COOLDOWN,
		TERMINAL
	}

	public enum CounterpartKind
	{
		HUMAN_OBJECT,
		PHANTOM_PROFILE
	}

	public enum Decision
	{
		WAIT,
		WARN,
		HELP,
		ENGAGE,
		RETREAT,
		DISENGAGE,
		COOLDOWN
	}

	public record Counterpart(CounterpartKind kind, long identity, int currentObjectId)
	{
		public Counterpart
		{
			Objects.requireNonNull(kind, "kind");
			if ((identity <= 0) || (currentObjectId <= 0) || ((kind == CounterpartKind.HUMAN_OBJECT) && (identity != currentObjectId)))
			{
				throw new IllegalArgumentException("Invalid PvP counterpart identity.");
			}
		}
	}

	public record Candidate(long profileId, Counterpart counterpart, Source source, String authorityHash, long createdLogicalNanos, long expiresLogicalNanos, boolean authorityCurrent, boolean targetResolvable, boolean visibleToActor)
	{
		public Candidate
		{
			Objects.requireNonNull(counterpart, "counterpart");
			Objects.requireNonNull(source, "source");
			authorityHash = boundedHash(authorityHash);
			if ((profileId <= 0) || (createdLogicalNanos < 0) || (expiresLogicalNanos <= createdLogicalNanos))
			{
				throw new IllegalArgumentException("Invalid PvP candidate.");
			}
		}

		public boolean currentAt(long logicalNanos)
		{
			return authorityCurrent && targetResolvable && visibleToActor && (logicalNanos >= createdLogicalNanos) && (logicalNanos < expiresLogicalNanos);
		}
	}

	/**
	 * Target data is deliberately coarse: policy never receives equipment, inventory,
	 * exact skills, or exact target pools.
	 */
	public record RiskSnapshot(int actorHpPercent, int actorEffectivePoolPercent, int actorMpPercent, int targetHpBand, int targetEffectivePoolBand, int relativeStrengthBasisPoints, int forcedPkRiskBasisPoints, int localActorSupport, int localTargetSupport, boolean sameParty, boolean self, boolean peaceRestricted, boolean canonicalContextAllowed, boolean targetAutoAttackable)
	{
		public RiskSnapshot
		{
			if (!percent(actorHpPercent) || !percent(actorEffectivePoolPercent) || !percent(actorMpPercent) || !band(targetHpBand) || !band(targetEffectivePoolBand) || (relativeStrengthBasisPoints < 0) || (relativeStrengthBasisPoints > 20000) || (forcedPkRiskBasisPoints < 0) || (forcedPkRiskBasisPoints > 10000) || (localActorSupport < 0) || (localActorSupport > 32) || (localTargetSupport < 0) || (localTargetSupport > 32))
			{
				throw new IllegalArgumentException("Invalid perceptible PvP risk snapshot.");
			}
		}

		public boolean legal()
		{
			return !sameParty && !self && !peaceRestricted && canonicalContextAllowed;
		}
	}

	public record Encounter(long profileId, Counterpart counterpart, Source source, String authorityHash, Stage stage, String warningReceiptId, String helpReceiptId, int proactiveEngagements, long createdLogicalNanos, long expiresLogicalNanos, long warningLogicalNanos, long cooldownUntilLogicalNanos, String terminalReason)
	{
		public Encounter
		{
			Objects.requireNonNull(counterpart, "counterpart");
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(stage, "stage");
			authorityHash = boundedHash(authorityHash);
			warningReceiptId = boundedNullable(warningReceiptId, MAX_RECEIPT_LENGTH, "warning receipt");
			helpReceiptId = boundedNullable(helpReceiptId, MAX_RECEIPT_LENGTH, "help receipt");
			terminalReason = boundedNullable(terminalReason, MAX_REASON_LENGTH, "terminal reason");
			if ((profileId <= 0) || (proactiveEngagements < 0) || (proactiveEngagements > 8) || (createdLogicalNanos < 0) || (expiresLogicalNanos <= createdLogicalNanos) || (warningLogicalNanos < 0) || (cooldownUntilLogicalNanos < 0))
			{
				throw new IllegalArgumentException("Invalid PvP encounter.");
			}
		}

		public Encounter withStage(Stage next, String warningReceipt, String helpReceipt, int engagements, long warningAt, long cooldownUntil, String reason)
		{
			return new Encounter(profileId, counterpart, source, authorityHash, next, warningReceipt, helpReceipt, engagements, createdLogicalNanos, expiresLogicalNanos, warningAt, cooldownUntil, reason);
		}
	}

	public record Outcome(Decision decision, String reason, boolean forceUse)
	{
		public Outcome
		{
			Objects.requireNonNull(decision, "decision");
			reason = bounded(reason, MAX_REASON_LENGTH, "PvP decision reason");
			if (forceUse && (decision != Decision.ENGAGE))
			{
				throw new IllegalArgumentException("Only engagement can carry forced authority.");
			}
		}
	}

	public static String sha256(Object... values)
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (Object value : values)
			{
				digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0);
			}
			return HexFormat.of().withUpperCase().formatHex(digest.digest());
		}
		catch (Exception exception)
		{
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private static boolean percent(int value)
	{
		return (value >= 0) && (value <= 100);
	}

	private static boolean band(int value)
	{
		return (value >= 0) && (value <= 4);
	}

	static String boundedHash(String value)
	{
		value = bounded(value, MAX_AUTHORITY_LENGTH, "PvP authority hash");
		if (!value.matches("[0-9A-F]{64}"))
		{
			throw new IllegalArgumentException("PvP authority hash is not canonical.");
		}
		return value;
	}

	static String bounded(String value, int maximum, String name)
	{
		if ((value == null) || value.isBlank() || (value.length() > maximum))
		{
			throw new IllegalArgumentException(name + " is invalid.");
		}
		return value;
	}

	private static String boundedNullable(String value, int maximum, String name)
	{
		if (value == null)
		{
			return "";
		}
		if (value.length() > maximum)
		{
			throw new IllegalArgumentException(name + " is too long.");
		}
		return value;
	}

	private PhantomPvpModel()
	{
	}
}

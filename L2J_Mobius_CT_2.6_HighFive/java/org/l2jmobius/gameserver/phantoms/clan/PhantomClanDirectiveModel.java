/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.clan;

import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;

public final class PhantomClanDirectiveModel
{
	public enum Kind
	{
		ASSEMBLE,
		STANDBY,
		DISMISS
	}

	public enum Effect
	{
		ACTIVE,
		WARM,
		WITHDRAW;

		public PhantomActivityState requiredState()
		{
			return switch (this)
			{
				case ACTIVE -> PhantomActivityState.ACTIVE;
				case WARM -> PhantomActivityState.WARM;
				case WITHDRAW -> null;
			};
		}
	}

	public enum Outcome
	{
		ACCEPT,
		DEFER,
		REFUSE
	}

	public record Definition(Kind kind, int baseScore, Effect effect, long ttlMillis, List<String> aliases)
	{
		public Definition
		{
			Objects.requireNonNull(kind, "Directive kind must not be null.");
			Objects.requireNonNull(effect, "Directive effect must not be null.");
			if ((baseScore < -3000) || (baseScore > 3000))
			{
				throw new IllegalArgumentException("Directive base score is outside bounds.");
			}
			if (((effect == Effect.WITHDRAW) && (ttlMillis != 0)) || ((effect != Effect.WITHDRAW) && ((ttlMillis < 1) || (ttlMillis > 86_400_000))))
			{
				throw new IllegalArgumentException("Directive TTL is inconsistent with its effect.");
			}
			if ((aliases == null) || aliases.isEmpty() || (aliases.size() > 64))
			{
				throw new IllegalArgumentException("Directive aliases are missing or excessive.");
			}
			aliases = List.copyOf(aliases);
		}
	}

	public record Decision(Definition definition, int socialModifier, int score, Outcome outcome)
	{
		public Decision
		{
			Objects.requireNonNull(definition);
			Objects.requireNonNull(outcome);
			if ((socialModifier < -3000) || (socialModifier > 3000) || (score < -3000) || (score > 3000))
			{
				throw new IllegalArgumentException("Directive decision score is outside bounds.");
			}
		}
	}

	private PhantomClanDirectiveModel()
	{
	}

	public static Decision decide(Definition definition, int socialModifier)
	{
		Objects.requireNonNull(definition);
		if ((socialModifier < -3000) || (socialModifier > 3000))
		{
			throw new IllegalArgumentException("Directive social modifier is outside bounds.");
		}
		final int score = Math.max(-3000, Math.min(3000, definition.baseScore() + socialModifier));
		final Outcome outcome = score >= 300 ? Outcome.ACCEPT : score <= -300 ? Outcome.REFUSE : Outcome.DEFER;
		return new Decision(definition, socialModifier, score, outcome);
	}
}

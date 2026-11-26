/*
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.gameserver.phantoms.population;

import java.util.function.LongFunction;
import java.util.function.LongToIntFunction;

import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Disposition;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Pace;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyState.Personality;
import org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog;

/** Bounded preference among already executable ordinary activities. */
public final class PhantomOrdinaryActivityPreference
{
	private final LongFunction<PhantomPopulationEcologyState> _find;
	private final int _persistenceCode;
	private final int _cautionCode;
	private final LongToIntFunction _clanDevelopment;

	public PhantomOrdinaryActivityPreference(LongFunction<PhantomPopulationEcologyState> find, PhantomSocialCatalog catalog)
	{
		this(find, catalog, id -> 0);
	}

	public PhantomOrdinaryActivityPreference(LongFunction<PhantomPopulationEcologyState> find, PhantomSocialCatalog catalog, LongToIntFunction clanDevelopment)
	{
		_find = find;
		_persistenceCode = traitCode(catalog, "persistence");
		_cautionCode = traitCode(catalog, "caution");
		_clanDevelopment = clanDevelopment;
	}

	private static int traitCode(PhantomSocialCatalog catalog, String key)
	{
		return catalog == null ? 0 : catalog.traits().stream().filter(trait -> trait.key().equals(key)).mapToInt(PhantomSocialCatalog.TraitDefinition::code).findFirst().orElse(0);
	}

	public int farm(long profileId, boolean continuing)
	{
		return rank(profileId, true, continuing, false) + Math.clamp(_clanDevelopment.applyAsInt(profileId), 0, 8);
	}

	public int preparation(long profileId, boolean playerTrade)
	{
		return rank(profileId, false, false, playerTrade);
	}

	/** A soft roster preference after lawful admission. */
	public int party(long profileId)
	{
		final var profile = _find.apply(profileId);
		if (profile == null || profile.disposition() != Disposition.MANAGED) { return 0; }
		final int personality = switch (profile.personality())
		{
			case SOCIAL -> 8;
			case COMPETITIVE -> 4;
			case CAUTIOUS -> -4;
			default -> 0;
		};
		final int pace = switch (profile.pace())
		{
			case CASUAL -> -2;
			case REGULAR -> 0;
			case FAST, OUTLIER -> 2;
		};
		return personality + pace + (profile.initialSocialTraits().getOrDefault(_persistenceCode, 0)
			- profile.initialSocialTraits().getOrDefault(_cautionCode, 0)) / 2500;
	}

	private int rank(long profileId, boolean farm, boolean continuing, boolean playerTrade)
	{
		final var profile = _find.apply(profileId);
		if (profile == null || profile.disposition() != Disposition.MANAGED)
		{
			return rank(Personality.BALANCED, Pace.REGULAR, 0, 0, farm, continuing, playerTrade);
		}
		return rank(profile.personality(), profile.pace(), profile.initialSocialTraits().getOrDefault(_persistenceCode, 0),
			profile.initialSocialTraits().getOrDefault(_cautionCode, 0), farm, continuing, playerTrade);
	}

	private static int rank(Personality personality, Pace pace, int persistence, int caution, boolean farm, boolean continuing, boolean playerTrade)
	{
		final int farmBias = switch (personality)
		{
			case GRINDER -> 18;
			case COMPETITIVE -> 8;
			case CAUTIOUS -> -12;
			case TRADER -> playerTrade ? -20 : -9;
			default -> 0;
		};
		final int paceBias = switch (pace)
		{
			case CASUAL -> -4;
			case REGULAR -> 0;
			case FAST -> 4;
			case OUTLIER -> 6;
		};
		final int traitBias = (persistence - caution) / 2500;
		return -50 + (farm ? farmBias + paceBias + traitBias + (continuing ? 12 : 0) : -farmBias - paceBias - traitBias);
	}
}

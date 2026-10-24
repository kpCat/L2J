/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.conversation.humanized;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.social.PhantomSocialModel.PersonalitySnapshot;

/** Derived speech preferences only; the persisted native Social personality remains authoritative. */
public record PhantomSpeechFacetProfile(Map<String, Integer> scores)
{
	public static final List<String> KEYS = List.of("aggression", "sarcasm", "humor", "reserved", "swagger", "timidity", "romance", "roughness", "cunning", "gossip");

	public PhantomSpeechFacetProfile
	{
		if ((scores == null) || (scores.size() != KEYS.size()) || !scores.keySet().containsAll(KEYS))
		{
			throw new IllegalArgumentException("Speech facet scores must contain exactly the ten native facet keys.");
		}
		final Map<String, Integer> bounded = new LinkedHashMap<>();
		for (String key : KEYS)
		{
			final Integer value = scores.get(key);
			if ((value == null) || (value < 0) || (value > 100))
			{
				throw new IllegalArgumentException("Speech facet score must be between 0 and 100: " + key);
			}
			bounded.put(key, value);
		}
		scores = Collections.unmodifiableMap(bounded);
	}

	public static PhantomSpeechFacetProfile from(PersonalitySnapshot personality)
	{
		Objects.requireNonNull(personality, "Native personality must not be null.");
		final int persistence = centered(personality, "persistence");
		final int caution = centered(personality, "caution");
		final int sociability = centered(personality, "sociability");
		final int loyalty = centered(personality, "loyalty");
		final int empathy = centered(personality, "empathy");
		final int assertiveness = centered(personality, "assertiveness");
		final Map<String, Integer> scores = new LinkedHashMap<>();
		// Weighted tendencies contribute at most 30 points; each facet has its own stable identity offset.
		put(scores, personality, "aggression", ((3 * assertiveness) - (2 * caution) - empathy) / 10);
		put(scores, personality, "sarcasm", ((2 * assertiveness) + persistence - empathy - loyalty) / 10);
		put(scores, personality, "humor", ((3 * sociability) + empathy - caution) / 10);
		put(scores, personality, "reserved", ((3 * caution) - (3 * sociability)) / 10);
		put(scores, personality, "swagger", ((3 * assertiveness) + (2 * persistence) - caution) / 10);
		put(scores, personality, "timidity", ((3 * caution) - (3 * assertiveness)) / 10);
		put(scores, personality, "romance", ((3 * empathy) + (2 * sociability) + loyalty) / 10);
		put(scores, personality, "roughness", ((3 * assertiveness) - (2 * empathy) - loyalty) / 10);
		put(scores, personality, "cunning", ((3 * caution) + (2 * persistence) - sociability) / 10);
		put(scores, personality, "gossip", ((4 * sociability) - caution - loyalty) / 10);
		return new PhantomSpeechFacetProfile(scores);
	}

	private static int centered(PersonalitySnapshot personality, String trait)
	{
		// Native traits are signed -10000..10000. Clamp before adding, then normalize to -50..50.
		final long signed = Math.max(-10000L, Math.min(10000L, personality.traits().getOrDefault(trait, 0).longValue()));
		return (int) ((signed + 10000L) / 200L) - 50;
	}

	private static void put(Map<String, Integer> scores, PersonalitySnapshot personality, String facet, int tendency)
	{
		final long salt = 0x9E3779B97F4A7C15L * facet.hashCode();
		final long identity = personality.seed() ^ Long.rotateLeft(personality.ownerProfileId(), 23) ^ salt;
		final int offset = (int) Math.floorMod(mix(identity), 41L) - 20;
		scores.put(facet, Math.max(0, Math.min(100, 50 + tendency + offset)));
	}

	private static long mix(long value)
	{
		// Intentional 64-bit overflow avalanches identity bits without RNG, mutable state or caching.
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}
}

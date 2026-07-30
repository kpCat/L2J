/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.semantic;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;

/**
 * Language-independent intent. It contains no generated or parsed text.
 */
public record PhantomSemanticAct(String actKey, PhantomDomainRef actor, PhantomDomainRef target, String groupId, long groupGeneration, String reasonKey, int confidenceBasisPoints, Map<String, PhantomDomainRef> domainSlots, Map<String, Long> numericSlots, String provenance)
{
	public static final int MAX_SLOTS = 8;

	public PhantomSemanticAct
	{
		actKey = PhantomPartyModel.requireKey(actKey, "Semantic act key");
		Objects.requireNonNull(actor, "Semantic act actor must not be null.");
		groupId = PhantomPartyModel.requireHash(groupId, "Semantic act group ID");
		reasonKey = PhantomPartyModel.requireKey(reasonKey, "Semantic act reason");
		if ((groupGeneration < 1) || (confidenceBasisPoints < 0) || (confidenceBasisPoints > 10000) || (domainSlots == null) || (domainSlots.size() > MAX_SLOTS) || (numericSlots == null) || (numericSlots.size() > MAX_SLOTS) || (provenance == null) || provenance.isBlank() || (provenance.length() > 128))
		{
			throw new IllegalArgumentException("Invalid semantic act.");
		}
		final Map<String, PhantomDomainRef> sortedDomains = new TreeMap<>();
		domainSlots.forEach((key, value) -> sortedDomains.put(PhantomPartyModel.requireKey(key, "Semantic domain slot key"), Objects.requireNonNull(value, "Semantic domain slot must not be null.")));
		domainSlots = Collections.unmodifiableMap(sortedDomains);
		final Map<String, Long> sortedNumbers = new TreeMap<>();
		numericSlots.forEach((key, value) -> sortedNumbers.put(PhantomPartyModel.requireKey(key, "Semantic numeric slot key"), Objects.requireNonNull(value, "Semantic numeric slot must not be null.")));
		numericSlots = Collections.unmodifiableMap(sortedNumbers);
	}

	public String canonicalHash()
	{
		final StringBuilder value = new StringBuilder();
		value.append(actKey).append('|').append(actor.namespace()).append(':').append(actor.key()).append('|');
		if (target != null)
		{
			value.append(target.namespace()).append(':').append(target.key());
		}
		value.append('|').append(groupId).append('|').append(groupGeneration).append('|').append(reasonKey).append('|').append(confidenceBasisPoints);
		domainSlots.forEach((key, slot) -> value.append("|d:").append(key).append('=').append(slot.namespace()).append(':').append(slot.key()));
		numericSlots.forEach((key, slot) -> value.append("|n:").append(key).append('=').append(slot));
		value.append("|p:").append(provenance);
		return PhantomPartyModel.sha256(value.toString());
	}
}

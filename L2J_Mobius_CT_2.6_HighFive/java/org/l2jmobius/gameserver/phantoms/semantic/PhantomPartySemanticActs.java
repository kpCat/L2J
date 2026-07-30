/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.semantic;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongFunction;

import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;

/**
 * String-key contract and stale-generation dispatch guard. Acts carry no text
 * and have no mutation behavior of their own.
 */
public final class PhantomPartySemanticActs
{
	public static final String INVITE_REQUESTED = "party.invite.requested";
	public static final String INVITE_ACCEPTED = "party.invite.accepted";
	public static final String INVITE_REFUSED = "party.invite.refused";
	public static final String MEMBER_JOINED = "party.member.joined";
	public static final String MEMBER_LEFT = "party.member.left";
	public static final String ROLE_ASSIGNED = "party.role.assigned";
	public static final String VACANCY_OPEN = "party.vacancy.open";
	public static final String ROUTE_STARTED = "party.route.started";
	public static final String REGROUP_REQUESTED = "party.regroup.requested";
	public static final String ASSIST_REQUESTED = "party.assist.requested";
	public static final String PROTECT_REQUESTED = "party.protect.requested";
	public static final String SUPPORT_REQUESTED = "party.support.requested";
	public static final Set<String> KEYS = Set.of(INVITE_REQUESTED, INVITE_ACCEPTED, INVITE_REFUSED, MEMBER_JOINED, MEMBER_LEFT, ROLE_ASSIGNED, VACANCY_OPEN, ROUTE_STARTED, REGROUP_REQUESTED, ASSIST_REQUESTED, PROTECT_REQUESTED, SUPPORT_REQUESTED);

	public static PhantomSemanticAct create(String actKey, PhantomDomainRef actor, PhantomDomainRef target, String groupId, long generation, String reasonKey, int confidence, Map<String, PhantomDomainRef> domainSlots, Map<String, Long> numericSlots, String provenance)
	{
		if (!KEYS.contains(actKey))
		{
			throw new IllegalArgumentException("Unknown party semantic act key.");
		}
		return new PhantomSemanticAct(actKey, actor, target, groupId, generation, reasonKey, confidence, domainSlots, numericSlots, provenance);
	}

	public static boolean dispatchIfCurrent(PhantomSemanticAct act, LongFunction<String> currentGroupAtGeneration, Consumer<PhantomSemanticAct> consumer)
	{
		if ((act == null) || !KEYS.contains(act.actKey()) || (currentGroupAtGeneration == null) || (consumer == null))
		{
			return false;
		}
		final String current = currentGroupAtGeneration.apply(act.groupGeneration());
		if (!act.groupId().equals(current))
		{
			return false;
		}
		consumer.accept(act);
		return true;
	}

	private PhantomPartySemanticActs()
	{
	}
}

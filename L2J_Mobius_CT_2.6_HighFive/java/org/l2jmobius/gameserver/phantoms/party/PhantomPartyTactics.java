/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.party;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.ActionOutcome;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatLoadout.SelectedSkill;
import org.l2jmobius.gameserver.phantoms.combat.PhantomPartySupportAction;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionKind;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionLease;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService.ExternalActionRequest;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCancellationToken;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.DirectiveKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberCapability;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.TacticalDirective;

/**
 * Bounded priority planner. Every issued mutation crosses combat ownership.
 */
public final class PhantomPartyTactics
{
	private final PhantomCombatService _combat;
	private final PhantomPartyBackend _backend;

	public PhantomPartyTactics(PhantomCombatService combat)
	{
		this(combat, null);
	}

	public PhantomPartyTactics(PhantomCombatService combat, PhantomPartyBackend backend)
	{
		_combat = combat;
		_backend = backend;
	}

	public List<TacticalDirective> plan(MemberRef leader, List<MemberRef> roster, Map<MemberRef, MemberSnapshot> snapshots)
	{
		if ((roster == null) || (roster.size() > 9) || (snapshots == null) || (snapshots.size() > 9))
		{
			throw new IllegalArgumentException("Party tactics input is outside bounds.");
		}
		final MemberSnapshot leaderSnapshot = snapshots.get(leader);
		final List<TacticalDirective> candidates = new ArrayList<>();
		for (MemberRef actor : roster.stream().filter(member -> member.kind() == MemberKind.PHANTOM).sorted(Comparator.comparing(MemberRef::stableKey)).toList())
		{
			final MemberSnapshot actorSnapshot = snapshots.get(actor);
			if ((actorSnapshot == null) || actorSnapshot.dead())
			{
				continue;
			}
			for (MemberRef target : roster)
			{
				final MemberSnapshot targetSnapshot = snapshots.get(target);
				if (targetSnapshot == null)
				{
					continue;
				}
				final List<MemberCapability> exactCapabilities = _backend == null ? actorSnapshot.capabilities() : _backend.capabilities(actor, target.characterObjectId());
				if (targetSnapshot.dead())
				{
					addSupport(candidates, DirectiveKind.RESURRECT_MEMBER, actorSnapshot, targetSnapshot, exactCapabilities, "combat.resurrection", "member.dead", 9500);
				}
				else
				{
					if (targetSnapshot.hpPercent() <= 55)
					{
						addSupport(candidates, DirectiveKind.HEAL_MEMBER, actorSnapshot, targetSnapshot, exactCapabilities, "combat.heal", "member.hp.low", 8000 + (55 - targetSnapshot.hpPercent()));
					}
					if (targetSnapshot.mpPercent() <= 35)
					{
						addSupport(candidates, DirectiveKind.RECHARGE_MEMBER, actorSnapshot, targetSnapshot, exactCapabilities, "combat.recharge", "member.mp.low", 7000 + (35 - targetSnapshot.mpPercent()));
					}
				}
				if (!actor.equals(target) && !targetSnapshot.attackerObjectIds().isEmpty())
				{
					candidates.add(new TacticalDirective(DirectiveKind.PROTECT_MEMBER, actor, target, targetSnapshot.attackerObjectIds().getFirst(), "", "", "", 0, 0, "member.attacked", 8500));
				}
			}
			if ((leaderSnapshot != null) && (leaderSnapshot.targetObjectId() > 0) && (leaderSnapshot.instanceId() == actorSnapshot.instanceId()))
			{
				candidates.add(new TacticalDirective(DirectiveKind.ASSIST_TARGET, actor, leader, leaderSnapshot.targetObjectId(), "", "", "", 0, 0, "leader.target", 6000));
			}
		}
		return candidates.stream().sorted(Comparator.comparingInt(TacticalDirective::priority).reversed().thenComparing(value -> value.actor().stableKey()).thenComparing(value -> value.kind().name()).thenComparingInt(TacticalDirective::targetObjectId)).toList();
	}

	public Optional<ExternalActionLease> dispatch(TacticalDirective directive, String operationKey, long deadlineLogicalNanos, PhantomCancellationToken token)
	{
		if (directive.actor().kind() != MemberKind.PHANTOM)
		{
			return Optional.empty();
		}
		final boolean support = switch (directive.kind())
		{
			case HEAL_MEMBER, RECHARGE_MEMBER, RESURRECT_MEMBER, PARTY_SUPPORT -> true;
			default -> false;
		};
		final ExternalActionKind kind = support ? ExternalActionKind.PARTY_SUPPORT : ExternalActionKind.PARTY_TACTIC;
		final PhantomCombatService.ExternalActionResult acquisition = _combat.acquireExternalAction(new ExternalActionRequest(directive.actor().profileId(), kind, operationKey, deadlineLogicalNanos, token));
		final ExternalActionLease lease = acquisition.lease();
		if (lease == null)
		{
			return Optional.empty();
		}
		final ActionOutcome outcome;
		if (support)
		{
			outcome = lease.castSupport(new PhantomPartySupportAction(directive.capabilityKey(), directive.variantKey(), directive.targetScope(), directive.targetObjectId(), new SelectedSkill(directive.actionSkillId(), directive.actionSkillLevel())));
		}
		else
		{
			outcome = lease.attack(directive.targetObjectId());
		}
		if ((outcome != ActionOutcome.ISSUED) && (outcome != ActionOutcome.ALREADY_OWNED))
		{
			lease.close();
			return Optional.empty();
		}
		return Optional.of(lease);
	}

	private static void addSupport(List<TacticalDirective> output, DirectiveKind kind, MemberSnapshot actor, MemberSnapshot target, List<MemberCapability> capabilities, String capabilityKey, String reason, int priority)
	{
		final MemberCapability capability = capabilities.stream().filter(value -> value.capabilityKey().equals(capabilityKey) && value.readyNow() && scopeAllows(value.targetScope(), actor.ref().equals(target.ref())) && (value.actionSkillId() > 0) && (value.actionSkillLevel() > 0)).sorted(Comparator.comparingInt(MemberCapability::contextualScore).reversed().thenComparing(MemberCapability::identity)).findFirst().orElse(null);
		if (capability != null)
		{
			output.add(new TacticalDirective(kind, actor.ref(), target.ref(), target.ref().characterObjectId(), capability.capabilityKey(), capability.variantKey(), capability.targetScope(), capability.actionSkillId(), capability.actionSkillLevel(), reason, priority + capability.contextualScore()));
		}
	}

	private static boolean scopeAllows(String targetScope, boolean self)
	{
		return self ? Set.of("SELF", "SINGLE_TARGET", "PARTY", "PARTY_MEMBER", "ALLY").contains(targetScope) : Set.of("SINGLE_TARGET", "PARTY", "PARTY_MEMBER", "ALLY").contains(targetScope);
	}
}

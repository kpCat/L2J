/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.KnowledgePage;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.PageRequest;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceObservation;
import org.l2jmobius.gameserver.phantoms.party.PhantomPartyBackend.CurrentForceStatus;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.BossObservation;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.CapabilityAssessment;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ContentSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.RaidReadiness;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ReadinessStatus;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.TargetAvailability;

/**
 * Stateless feasibility join over Goal011, live boss managers and Goal017.
 */
public final class PhantomRaidReadinessService
{
	private final PhantomGameKnowledgeQuery _knowledge;
	private final PhantomPartyBackend _party;
	private final PhantomRaidAuthority _authority;

	public PhantomRaidReadinessService(PhantomGameKnowledgeQuery knowledge, PhantomPartyBackend party, PhantomRaidAuthority authority)
	{
		_knowledge = Objects.requireNonNull(knowledge);
		_party = Objects.requireNonNull(party);
		_authority = Objects.requireNonNull(authority);
	}

	public KnowledgePage<ContentRequirementFact> contents(ContentKind contentKind, PageRequest page)
	{
		if ((contentKind != ContentKind.RAID) && (contentKind != ContentKind.EPIC))
		{
			throw new IllegalArgumentException("Raid readiness accepts only RAID or EPIC content.");
		}
		return _knowledge.contents(contentKind, page);
	}

	public RaidReadiness assess(MemberRef actor, String contentId)
	{
		Objects.requireNonNull(actor, "actor");
		if ((contentId == null) || contentId.isBlank())
		{
			throw new IllegalArgumentException("Raid readiness requires a content id.");
		}
		final CurrentForceObservation force = _party.currentForce(actor);
		final ContentRequirementFact requirement = _knowledge.content(contentId).orElse(null);
		if ((requirement == null) || ((requirement.contentKind() != ContentKind.RAID) && (requirement.contentKind() != ContentKind.EPIC)) || (requirement.npcId() == null))
		{
			return result(contentId, null, null, TargetAvailability.UNKNOWN, force, List.of(), ReadinessStatus.TARGET_UNKNOWN, "raid.content.missing_or_unsupported");
		}
		final NpcFact npc = _knowledge.findNpc(requirement.npcId()).orElse(null);
		final NpcKind expectedKind = requirement.contentKind() == ContentKind.RAID ? NpcKind.RAID_BOSS : NpcKind.GRAND_BOSS;
		if ((npc == null) || (npc.kind() != expectedKind))
		{
			return result(contentId, null, null, TargetAvailability.UNKNOWN, force, List.of(), ReadinessStatus.TARGET_UNKNOWN, "raid.content.npc_kind_mismatch");
		}
		final ContentSnapshot content = new ContentSnapshot(requirement, npc, _knowledge.snapshot().contentRequirementHash());
		final BossObservation target = _authority.observe(requirement.contentKind(), npc.npcId());
		if ((target == null) || (target.contentKind() != requirement.contentKind()) || (target.npcId() != npc.npcId()))
		{
			return result(contentId, content, null, TargetAvailability.UNKNOWN, force, List.of(), ReadinessStatus.TARGET_UNKNOWN, "raid.target.identity_mismatch");
		}
		final TargetAvailability availability = target.availability();
		if (availability != TargetAvailability.AVAILABLE)
		{
			return result(contentId, content, target, availability, force, List.of(), availability == TargetAvailability.UNAVAILABLE ? ReadinessStatus.TARGET_UNAVAILABLE : ReadinessStatus.TARGET_UNKNOWN, availability == TargetAvailability.UNAVAILABLE ? "raid.target.unavailable" : "raid.target.unknown");
		}
		if (force.status() == CurrentForceStatus.PARTY_ABSENT)
		{
			return result(contentId, content, target, availability, force, List.of(), ReadinessStatus.GROUP_ABSENT, "raid.group.absent");
		}
		if ((force.status() != CurrentForceStatus.AVAILABLE) || (force.snapshot() == null))
		{
			return result(contentId, content, target, availability, force, List.of(), ReadinessStatus.GROUP_INCOMPLETE, "raid.group.unavailable_or_over_bound");
		}
		final int memberCount = force.snapshot().totalMemberCount();
		if ((memberCount < requirement.recommendedMinParty()) || (memberCount > requirement.recommendedMaxParty()))
		{
			return result(contentId, content, target, availability, force, List.of(), ReadinessStatus.GROUP_INCOMPLETE, "raid.group.outside_recommended_size");
		}
		final List<CapabilityAssessment> capabilities = new ArrayList<>(requirement.requirements().size());
		for (CapabilityRequirement capability : requirement.requirements())
		{
			final int satisfying = (int) force.snapshot().members().stream().filter(member -> satisfies(member, capability)).count();
			capabilities.add(new CapabilityAssessment(capability, satisfying));
		}
		final boolean requiredMissing = capabilities.stream().anyMatch(capability -> capability.requirement().required() && !capability.satisfied());
		return result(contentId, content, target, availability, force, capabilities, requiredMissing ? ReadinessStatus.GROUP_INCAPABLE : ReadinessStatus.GROUP_READY, requiredMissing ? "raid.group.required_capability_missing" : "raid.group.ready");
	}

	static boolean satisfies(MemberSnapshot member, CapabilityRequirement requirement)
	{
		return member.capabilities().stream().anyMatch(capability -> capability.capabilityKey().equals(requirement.capabilityKey()) && (capability.rank() >= requirement.minimumRank()) && capability.intrinsic() && capability.learned() && capability.readyNow());
	}

	private static RaidReadiness result(String contentId, ContentSnapshot content, BossObservation target, TargetAvailability availability, CurrentForceObservation force, List<CapabilityAssessment> capabilities, ReadinessStatus status, String reason)
	{
		return new RaidReadiness(contentId, content, target, availability, force, capabilities, status, reason);
	}
}

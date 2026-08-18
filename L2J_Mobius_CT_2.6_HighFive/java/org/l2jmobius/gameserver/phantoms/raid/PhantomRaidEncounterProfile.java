/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.List;
import java.util.Objects;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel;

/**
 * Immutable curated encounter contract. It carries no mutable boss or script state.
 */
public record PhantomRaidEncounterProfile(String contentId, ContentKind contentKind, int npcId, NpcKind npcKind, EntryKind entryKind, int entryNpcId, int templateId, int targetLevel, int minimumMembers, int maximumMembers, int minimumMemberLevel, int maximumMemberLevelWhenCurseEnabled, int leashDistance, List<CapabilityRequirement> requiredCapabilities, String evidenceHash)
{
	public PhantomRaidEncounterProfile
	{
		if ((contentId == null) || contentId.isBlank() || ((contentKind != ContentKind.RAID) && (contentKind != ContentKind.EPIC)) || (npcId <= 0) || (npcKind == null) || (entryKind == null) || (targetLevel < 1) || (minimumMembers < 1) || (maximumMembers < minimumMembers) || (minimumMemberLevel < 1) || (minimumMemberLevel > 255) || (maximumMemberLevelWhenCurseEnabled < 0) || (maximumMemberLevelWhenCurseEnabled > 255) || (leashDistance < 0) || (requiredCapabilities == null))
		{
			throw new IllegalArgumentException("Invalid raid encounter profile.");
		}
		final NpcKind requiredKind = contentKind == ContentKind.RAID ? NpcKind.RAID_BOSS : NpcKind.GRAND_BOSS;
		if ((npcKind != requiredKind) || ((entryKind == EntryKind.OPEN_WORLD) && ((entryNpcId != 0) || (templateId != 0))) || ((entryKind == EntryKind.SCRIPTED) && ((entryNpcId <= 0) || (templateId <= 0))))
		{
			throw new IllegalArgumentException("Raid encounter entry or NPC kind mismatch.");
		}
		requiredCapabilities = requiredCapabilities.stream().filter(CapabilityRequirement::required).sorted(java.util.Comparator.comparing(CapabilityRequirement::capabilityKey)).toList();
		final String expectedHash = PhantomPartyModel.sha256(canonical(contentId, contentKind, npcId, npcKind, entryKind, entryNpcId, templateId, targetLevel, minimumMembers, maximumMembers, minimumMemberLevel, maximumMemberLevelWhenCurseEnabled, leashDistance, requiredCapabilities));
		if ((evidenceHash == null) || !evidenceHash.equalsIgnoreCase(expectedHash))
		{
			throw new IllegalArgumentException("Raid encounter profile evidence mismatch.");
		}
		evidenceHash = expectedHash;
	}

	public static PhantomRaidEncounterProfile create(String contentId, ContentKind contentKind, int npcId, NpcKind npcKind, EntryKind entryKind, int entryNpcId, int templateId, int targetLevel, int minimumMembers, int maximumMembers, int minimumMemberLevel, int maximumMemberLevelWhenCurseEnabled, int leashDistance, List<CapabilityRequirement> requiredCapabilities)
	{
		Objects.requireNonNull(requiredCapabilities);
		final List<CapabilityRequirement> required = requiredCapabilities.stream().filter(CapabilityRequirement::required).sorted(java.util.Comparator.comparing(CapabilityRequirement::capabilityKey)).toList();
		return new PhantomRaidEncounterProfile(contentId, contentKind, npcId, npcKind, entryKind, entryNpcId, templateId, targetLevel, minimumMembers, maximumMembers, minimumMemberLevel, maximumMemberLevelWhenCurseEnabled, leashDistance, required, PhantomPartyModel.sha256(canonical(contentId, contentKind, npcId, npcKind, entryKind, entryNpcId, templateId, targetLevel, minimumMembers, maximumMembers, minimumMemberLevel, maximumMemberLevelWhenCurseEnabled, leashDistance, required)));
	}

	private static String canonical(String contentId, ContentKind contentKind, int npcId, NpcKind npcKind, EntryKind entryKind, int entryNpcId, int templateId, int targetLevel, int minimumMembers, int maximumMembers, int minimumMemberLevel, int maximumMemberLevelWhenCurseEnabled, int leashDistance, List<CapabilityRequirement> requirements)
	{
		return contentId + "|" + contentKind + "|" + npcId + "|" + npcKind + "|" + entryKind + "|" + entryNpcId + "|" + templateId + "|" + targetLevel + "|" + minimumMembers + "|" + maximumMembers + "|" + minimumMemberLevel + "|" + maximumMemberLevelWhenCurseEnabled + "|" + leashDistance + "|" + requirements;
	}

	public boolean entryGated()
	{
		return entryKind == EntryKind.SCRIPTED;
	}

	public enum EntryKind
	{
		OPEN_WORLD,
		SCRIPTED
	}
}
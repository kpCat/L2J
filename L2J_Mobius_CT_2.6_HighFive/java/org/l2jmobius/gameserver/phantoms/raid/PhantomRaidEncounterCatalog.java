/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.gameserver.phantoms.raid;

import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterProfile.EntryKind;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ContentSnapshot;

/**
 * Exact High Five raid encounter allowlist. Other EPIC content is typed unsupported.
 */
public final class PhantomRaidEncounterCatalog
{
	public static final String QUEEN_ANT = "epic.29001";
	public static final String ZAKEN_83 = "epic.zaken.83";
	public static final int QUEEN_ANT_NPC_ID = 29001;
	public static final int ZAKEN_83_NPC_ID = 29181;
	public static final int ZAKEN_ENTRY_NPC_ID = 32713;
	public static final int ZAKEN_83_TEMPLATE_ID = 135;

	public Optional<PhantomRaidEncounterProfile> resolve(ContentSnapshot content)
	{
		if (content == null)
		{
			return Optional.empty();
		}
		final var requirement = content.requirement();
		final var npc = content.npc();
		if (requirement.contentKind() == ContentKind.RAID)
		{
			return Optional.of(PhantomRaidEncounterProfile.create(requirement.contentId(), ContentKind.RAID, npc.npcId(), NpcKind.RAID_BOSS, EntryKind.OPEN_WORLD, 0, 0, Math.max(1, npc.level()), requirement.recommendedMinParty(), requirement.recommendedMaxParty(), 1, 0, 0, requirement.requirements()));
		}
		if (QUEEN_ANT.equals(requirement.contentId()) && (npc.npcId() == QUEEN_ANT_NPC_ID) && (npc.kind() == NpcKind.GRAND_BOSS) && (npc.level() == 40))
		{
			return Optional.of(PhantomRaidEncounterProfile.create(QUEEN_ANT, ContentKind.EPIC, QUEEN_ANT_NPC_ID, NpcKind.GRAND_BOSS, EntryKind.OPEN_WORLD, 0, 0, 40, requirement.recommendedMinParty(), requirement.recommendedMaxParty(), 1, 48, 2000, requirement.requirements()));
		}
		if (ZAKEN_83.equals(requirement.contentId()) && (npc.npcId() == ZAKEN_83_NPC_ID) && (npc.kind() == NpcKind.GRAND_BOSS) && (npc.level() == 83) && (requirement.recommendedMinParty() == 9) && (requirement.recommendedMaxParty() == 27))
		{
			return Optional.of(PhantomRaidEncounterProfile.create(ZAKEN_83, ContentKind.EPIC, ZAKEN_83_NPC_ID, NpcKind.GRAND_BOSS, EntryKind.SCRIPTED, ZAKEN_ENTRY_NPC_ID, ZAKEN_83_TEMPLATE_ID, 83, 9, 27, 78, 0, 0, requirement.requirements()));
		}
		return Optional.empty();
	}
}
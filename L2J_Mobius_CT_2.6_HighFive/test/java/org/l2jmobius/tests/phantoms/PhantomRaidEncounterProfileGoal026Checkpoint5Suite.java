/*
 * Copyright (c) 2013 L2jMobius
 */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeAuthority;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.CapabilityRequirement;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentKind;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.ContentRequirementFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcFact;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.MemberRef;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterCatalog;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterProfile;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidEncounterProfile.EntryKind;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidModel.ContentSnapshot;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptAdapter;
import org.l2jmobius.gameserver.phantoms.raid.PhantomRaidScriptRegistry;

public final class PhantomRaidEncounterProfileGoal026Checkpoint5Suite implements PhantomTestSuite
{
	private static final long SEED = 26002652L;
	private final PhantomRaidEncounterCatalog _catalog = new PhantomRaidEncounterCatalog();
	private java.nio.file.Path _root;

	@Override
	public String id()
	{
		return "raid-encounter-profile-goal026cp5";
	}

	@Override
	public void beforeAll(PhantomTestContext context)
	{
		PhantomAssertions.assertEquals(SEED, context.seed(), "Encounter profile CP5 used the wrong seed.");
		_root = context.moduleRoot();
	}

	@Override
	public void register(PhantomTestRegistry registry)
	{
		registry.add("01-generic-and-unsupported-typed-catalog", _ -> genericAndUnsupported());
		registry.add("02-queen-ant-exact-profile", _ -> queenAnt());
		registry.add("03-zaken83-exact-profile-and-registry", _ -> zaken83());
		registry.add("04-entry-gated-staging-and-public-script-boundary", _ -> entryGatedBoundary());
	}

	private void genericAndUnsupported()
	{
		final PhantomRaidEncounterProfile raid = _catalog.resolve(content("raid.25001", ContentKind.RAID, 25001, 80, NpcKind.RAID_BOSS, 5, 9, List.of(required("combat.tank", 1, 800)))).orElseThrow();
		PhantomAssertions.assertEquals(EntryKind.OPEN_WORLD, raid.entryKind(), "Generic RAID was not open-world.");
		PhantomAssertions.assertEquals(NpcKind.RAID_BOSS, raid.npcKind(), "Generic RAID used the wrong canonical NPC kind.");
		final ContentSnapshot unsupported = content("epic.unsupported", ContentKind.EPIC, 29002, 70, NpcKind.GRAND_BOSS, 9, 27, List.of(required("combat.heal", 1, 800)));
		PhantomAssertions.assertTrue(_catalog.resolve(unsupported).isEmpty(), "Uncurated EPIC did not fail as typed unsupported.");
	}

	private void queenAnt()
	{
		final PhantomRaidEncounterProfile queen = _catalog.resolve(content(PhantomRaidEncounterCatalog.QUEEN_ANT, ContentKind.EPIC, 29001, 40, NpcKind.GRAND_BOSS, 9, 45, List.of(required("combat.heal", 1, 900)))).orElseThrow();
		PhantomAssertions.assertEquals(29001, queen.npcId(), "Queen Ant NPC identity changed.");
		PhantomAssertions.assertEquals(40, queen.targetLevel(), "Queen Ant exact level changed.");
		PhantomAssertions.assertEquals(48, queen.maximumMemberLevelWhenCurseEnabled(), "Queen Ant curse ceiling is not inclusive level 48.");
		PhantomAssertions.assertEquals(2000, queen.leashDistance(), "Queen Ant leash fact changed.");
		PhantomAssertions.assertFalse(queen.entryGated(), "Queen Ant was incorrectly made entry-gated.");
	}

	private void zaken83()
	{
		final List<CapabilityRequirement> requirements = List.of(required("combat.tank", 1, 850), required("combat.heal", 1, 900), required("combat.resurrection", 1, 900));
		final PhantomRaidEncounterProfile zaken = _catalog.resolve(content(PhantomRaidEncounterCatalog.ZAKEN_83, ContentKind.EPIC, 29181, 83, NpcKind.GRAND_BOSS, 9, 27, requirements)).orElseThrow();
		PhantomAssertions.assertTrue(zaken.entryGated(), "Zaken83 was not entry-gated.");
		PhantomAssertions.assertEquals(32713, zaken.entryNpcId(), "Zaken83 Pathfinder identity changed.");
		PhantomAssertions.assertEquals(135, zaken.templateId(), "Zaken83 template identity changed.");
		PhantomAssertions.assertEquals(78, zaken.minimumMemberLevel(), "Zaken83 minimum level changed.");
		PhantomAssertions.assertEquals(List.of("combat.heal", "combat.resurrection", "combat.tank"), zaken.requiredCapabilities().stream().map(CapabilityRequirement::capabilityKey).toList(), "Zaken83 conservative required capabilities changed.");

		final PhantomRaidScriptRegistry scripts = new PhantomRaidScriptRegistry();
		final var first = scripts.install(new Adapter());
		final var replacement = scripts.install(new Adapter());
		PhantomAssertions.assertEquals(1, scripts.size(), "Reload-safe exact-key registration duplicated the adapter.");
		PhantomAssertions.assertTrue(replacement.revision() > first.revision(), "Reload-safe registration did not advance revision evidence.");
		PhantomAssertions.assertTrue(scripts.registered(PhantomRaidEncounterCatalog.ZAKEN_83, 32713, 135), "Exact Zaken83 adapter was not canonical.");
	}

	private void entryGatedBoundary() throws Exception
	{
		final String readiness = Files.readString(_root.resolve("java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidReadinessService.java"));
		PhantomAssertions.assertTrue(readiness.contains("TargetAvailability.ENTRY_GATED") && readiness.contains("\"ENTRY_GATED\", false, false, false"), "ENTRY_GATED readiness claims live boss evidence or is missing.");
		final String assembly = Files.readString(_root.resolve("java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidAssemblyService.java"));
		final int contentAnchor = assembly.indexOf("content.topologyAnchorId()");
		final int goalAnchor = assembly.indexOf("assembly._goal.selectedAnchor()");
		final int entry = assembly.indexOf("readiness.targetAvailability() == TargetAvailability.ENTRY_GATED");
		final int live = assembly.indexOf("_authority.observeLocation", entry);
		PhantomAssertions.assertTrue((contentAnchor >= 0) && (contentAnchor < goalAnchor) && (goalAnchor < entry) && (entry < live), "CP4 staging priority is not content -> goal -> entryNpc -> live boss.");
		final String locator = Files.readString(_root.resolve("java/org/l2jmobius/gameserver/phantoms/raid/PhantomRaidEntryNpcLocator.java"));
		PhantomAssertions.assertTrue(locator.contains("getSpawns(exactNpcId)") && !locator.contains("getSpawns()."), "ENTRY_GATED staging does not use exact SpawnTable NPC lookup.");
		final String script = Files.readString(_root.resolve("dist/game/data/scripts/instances/CavernOfThePirateCaptain/CavernOfThePirateCaptain.java"));
		final int adapterStart = script.indexOf("private final class Zaken83Adapter");
		final int adapterEnd = script.indexOf("public static void main", adapterStart);
		final String adapter = script.substring(adapterStart, adapterEnd);
		PhantomAssertions.assertFalse(adapter.contains("isBlue") || adapter.contains("zakenRoom"), "Zaken adapter exposed hidden candle/room truth.");
		PhantomAssertions.assertTrue(adapter.contains("limit(36)") && adapter.contains("Npc.INTERACTION_DISTANCE") && adapter.contains("CavernOfThePirateCaptain.this.onFirstTalk(candle, scout)"), "Zaken public candles or physical same-onFirstTalk interaction is incomplete.");
	}

	private static ContentSnapshot content(String contentId, ContentKind kind, int npcId, int level, NpcKind npcKind, int minimum, int maximum, List<CapabilityRequirement> requirements)
	{
		final ContentRequirementFact requirement = new ContentRequirementFact(contentId, kind, npcId, null, null, minimum, maximum, requirements, List.of("test"), PhantomGameKnowledgeAuthority.CURATED_RECOMMENDATION);
		final NpcFact npc = new NpcFact(npcId, level, npcKind, true, true, false, 1, 1, PhantomGameKnowledgeAuthority.SERVER_LOADER_FACT);
		return new ContentSnapshot(requirement, npc, "a".repeat(64));
	}

	private static CapabilityRequirement required(String key, int count, int rank)
	{
		return new CapabilityRequirement(key, count, rank, true);
	}

	private static final class Adapter implements PhantomRaidScriptAdapter
	{
		@Override
		public String contentId()
		{
			return PhantomRaidEncounterCatalog.ZAKEN_83;
		}

		@Override
		public int entryNpcId()
		{
			return 32713;
		}

		@Override
		public int templateId()
		{
			return 135;
		}

		@Override
		public EntryResult enter(EntryRequest request)
		{
			return EntryResult.rejected("test.inert");
		}

		@Override
		public List<CandleEvidence> candles(int instanceId)
		{
			return List.of(new CandleEvidence(1, new PhantomNavigationPoint(1, 2, 3, 1), false));
		}

		@Override
		public CandleInteraction interactCandle(int instanceId, int scoutObjectId, int candleObjectId)
		{
			return CandleInteraction.INTERACTED;
		}

		@Override
		public Optional<TargetEvidence> revealedTarget(int instanceId)
		{
			return Optional.empty();
		}

		@Override
		public Optional<PhantomNavigationPoint> safeRetreatPoint(int instanceId)
		{
			return Optional.empty();
		}

		@Override
		public boolean confirmsDeath(TargetEvidence target)
		{
			return false;
		}
	}
}

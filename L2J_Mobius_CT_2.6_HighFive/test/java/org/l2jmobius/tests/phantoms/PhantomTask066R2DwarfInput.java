/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.item.type.CrystalType;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;

/** One new R2 identity only. All mutations precede Main measurement and are explicitly TEST, never earned. */
final class PhantomTask066R2DwarfInput
{
	private record Farm(int npcId, PhantomTopologyAnchor anchor, double townDistance, double spoilChance) { }

	static List<String> prepare(PhantomProfileRepository profiles, PhantomPopulationStore.ManagedSnapshot birth) throws Exception
	{
		final long id = birth.profile().profileId();
		final var career = PhantomCareerChoice.decode(profiles.findComponent(id, PhantomCareerChoice.COMPONENT).orElseThrow().payload());
		PhantomAssertions.assertTrue(birth.state().classId() == 53 && career.nextClass(53) == 54, "Declared dwarf birth must have the legal Scavenger branch.");
		try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var spawns = production.knowledge().snapshot().spawnFactsByNpc();
			final var counters = spawns.values().stream().flatMap(List::stream).filter(value -> {
				final var npc = NpcData.getInstance().getTemplate(value.npcId());
				return npc != null && value.instanceId() == 0 && ("Warehouse".equals(npc.getType()) || "Merchant".equals(npc.getType()));
			}).toList();
			final var geo = GeoEngine.getInstance(); final var farms = new ArrayList<Farm>();
			for (var entry : spawns.entrySet())
			{
				final var npc = NpcData.getInstance().getTemplate(entry.getKey());
				if (npc == null || !"Monster".equals(npc.getType()) || npc.getLevel() < 18 || npc.getLevel() > 22 || npc.getSpoilList() == null || npc.getSpoilList().isEmpty()) { continue; }
				for (var spawn : entry.getValue())
				{
					if (spawn.instanceId() != 0 || spawn.amount() <= 0 || spawn.topologyNodeId() == null) { continue; }
					for (var anchor : production.topology().snapshot().anchorsByNode().getOrDefault(spawn.topologyNodeId(), List.of()))
					{
						final var point = anchor.point();
						if (anchor.role() != PhantomTopologyAnchorRole.FARMING || !geo.hasGeo(point.x(), point.y())) { continue; }
						final double merchantDistance = counters.stream().filter(value -> "Merchant".equals(NpcData.getInstance().getTemplate(value.npcId()).getType())).mapToDouble(value -> Math.hypot((long)value.x()-point.x(), (long)value.y()-point.y())).min().orElse(Double.MAX_VALUE);
						final double bankDistance = counters.stream().filter(value -> "Warehouse".equals(NpcData.getInstance().getTemplate(value.npcId()).getType())).mapToDouble(value -> Math.hypot((long)value.x()-point.x(), (long)value.y()-point.y())).min().orElse(Double.MAX_VALUE);
						final double distance = Math.max(merchantDistance, bankDistance);
						final int z = geo.getHeight(point.x(), point.y(), point.z());
						if (distance <= 30000 && Math.abs(z-point.z()) <= 128 && geo.canMoveToTarget(point.x(), point.y(), z, point.x()+64, point.y(), z, 0))
						{ farms.add(new Farm(npc.getId(), anchor, distance, npc.getSpoilList().stream().mapToDouble(value -> value.getChance()).max().orElse(0))); }
					}
				}
			}
			farms.sort(Comparator.comparingDouble(Farm::spoilChance).reversed().thenComparingDouble(Farm::townDistance).thenComparing(value -> value.anchor().id()));
			PhantomAssertions.assertTrue(!farms.isEmpty(), "No factual stock spoil anchor with Geo and a separate bounded town; preserve births, do not invent a spot.");
			final var farm = farms.getFirst(); final var point = farm.anchor().point();
			final Player player = Player.load(birth.profile().characterObjectId());
			PhantomAssertions.assertTrue(player != null && player.getClient() == null && !player.isGM() && player.getActiveClass() == 53, "R2 new dwarf identity differs.");
			try
			{
				player.setPlayerClass(54); player.setBaseClass(54); player.setLearningClass(player.getPlayerClass());
				player.getStat().setExp(player.getStat().getExpForLevel(20)); player.getStat().setLevel((byte)20); player.setSp(0); Thread.sleep(500); player.rewardSkills();
				player.addSkill(SkillData.getInstance().getSkill(254, 2), true);
				for (int skill : List.of(42, 239)) { player.addSkill(SkillData.getInstance().getSkill(skill, 1), true); }
				PhantomAssertions.assertTrue(player.getKnownSkill(254) != null && player.getKnownSkill(42) != null && player.getKnownSkill(248) == null, "Scavenger must have Spoil/Sweep, never Crystallize.");
				final var weapon = player.getInventory().addItem(ItemProcessType.REWARD, 69, 1, player, PhantomTask066R2DwarfInput.class); player.getInventory().equipItem(weapon);
				player.getInventory().addItem(ItemProcessType.REWARD, 1060, 20, player, PhantomTask066R2DwarfInput.class);
				final var filler = Arrays.stream(ItemData.getInstance().getAllItems()).filter(value -> value != null && value.isArmor() && !value.isStackable() && value.getCrystalType() == CrystalType.NONE && value.getWeight() > 0 && value.getWeight() <= 100).min(Comparator.comparingInt(value -> value.getId())).orElseThrow();
				for (var item : player.getInventory().getItems()) { if (!item.isEquipped() && !item.isStackable()) { item.setEnchantLevel(1); } }
				while (player.getInventory().getSize() < player.getInventoryLimit() * 79 / 100)
				{ player.getInventory().addItem(ItemProcessType.REWARD, filler.getId(), 1, player, PhantomTask066R2DwarfInput.class).setEnchantLevel(1); }
				player.reduceAdena(ItemProcessType.DESTROY, player.getAdena(), player, false);
				player.setXYZInvisible(point.x(), point.y(), geo.getHeight(point.x(), point.y(), point.z())); player.setOnlineStatus(true, false);
				player.refreshExpertisePenalty(); player.refreshOverloaded(); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
				PhantomAssertions.assertTrue(player.getCurrentLoad() * 100L < player.getMaxLoad() * 50L, "TEST input must not impose native weight penalty.");
				player.getInventory().updateDatabase(); player.storeMe();
				final var goals = new PhantomGoalStateStore(profiles); final var old = goals.load(id).orElse(null);
				final var root = new PhantomGoal(6600664200L+id, PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", Integer.toString(farm.npcId())), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, farm.npcId()+"@"+farm.anchor().id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, farm.anchor().id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture066r2.premeasurement.dwarf", old == null ? 0 : old.goal().revision()+1);
				if (old == null) { goals.insert(id, root); } else { goals.replace(id, old.rowVersion(), root); }
				final var capture = production.authority().captureOwnedNative(id, player, root, null);
				final var baseline = new PhantomBackgroundTransaction().captureBaseline(capture.state(), root, capture.context());
				PhantomAssertions.assertTrue(baseline.successful(), "R2 declared native dwarf baseline refused: " + baseline.status());
				return List.of("dwarf.inputs=TEST_PREIMAGE_LEVEL20_SKILLS_GEAR_POTS_SLOTS79_ADENA0_NOT_EARNED", "dwarf.profileId="+id, "dwarf.npcId="+farm.npcId(), "dwarf.anchor="+farm.anchor().id(), "dwarf.position="+player.getX()+","+player.getY()+","+player.getZ(), "dwarf.spoilChance="+farm.spoilChance(), "dwarf.nearestTownDistance="+farm.townDistance(), "dwarf.preverifiedCandidates="+farms.size(), "dwarf.slots="+player.getInventory().getSize()+"/"+player.getInventoryLimit(), "dwarf.fillerItem="+filler.getId());
			}
			finally { player.stopAllTasks(); player.storeMe(); player.deleteMe(); }
		}
	}
}

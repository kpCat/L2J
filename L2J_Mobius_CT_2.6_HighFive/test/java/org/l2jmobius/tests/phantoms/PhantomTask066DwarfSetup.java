/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.data.xml.SkillData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext;
import org.l2jmobius.gameserver.phantoms.background.PhantomOwnedStoreIntent;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationCatalog;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationState;
import org.l2jmobius.gameserver.phantoms.population.PhantomPopulationStore;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;

/** One named premeasurement TEST input, on an existing own FRESH identity; never a Main controller. */
public final class PhantomTask066DwarfSetup
{
	private record Farm(int npcId, PhantomTopologyAnchor anchor, double counterDistance) { }
	public static void main(String[] args) throws Exception
	{
		if (args.length != 0 || !PhantomTask066PersistentLane.enabled() || !Boolean.getBoolean("phantom.task066.prepareDwarf") || Boolean.getBoolean("phantom.task066.viewpoint")) { throw new IllegalArgumentException("Explicit one-time066 dwarf preparation required."); }
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath(), runtime = Path.of(System.getProperty("phantom.task066.runtime")).toRealPath();
		PhantomTask066PersistentLane.validate(module, Path.of(System.getProperty("phantom.test.config")));
		final Path privateRoot = runtime.resolve("private/dwarf066-preparation");
		if (Files.exists(privateRoot)) { throw new IllegalArgumentException("Named066 dwarf preparation already exists; no repeat/reset."); }
		Player player = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(66006632, module, module.getParent().resolve(".phantom-local/task066/reports/dwarf-preparation")));
			final var repository = PhantomProfileRepository.open(); final var goals = new PhantomGoalStateStore(repository);
			final var zone = ZoneId.of("UTC"); // Match the candidate's configured Main authority token, including its zone ID.
			final var catalog = PhantomPopulationCatalog.load(Path.of("data/phantoms/population/high-five-population-v3.xml"), zone);
			final var managed = new PhantomPopulationStore(repository, catalog, zone).loadManagedAfter(0, 64).stream()
				.filter(value -> value.state().state() == PhantomPopulationState.State.READY && value.state().classId() == 53 && value.profile().characterObjectId() != null)
				.filter(value -> catalog.evaluate(value.state().scheduleTemplate(), Instant.now(), zone, value.state().schedulePhaseMinutes()).state() == PhantomActivityState.ACTIVE)
				.filter(value -> repository.findComponent(value.profile().profileId(), PhantomOwnedStoreIntent.COMPONENT_TYPE).isEmpty())
				.filter(value -> repository.findComponent(value.profile().profileId(), org.l2jmobius.gameserver.phantoms.economy.PhantomStorePlan.COMPONENT_TYPE).isEmpty() && repository.findComponent(value.profile().profileId(), "learning.intent").isEmpty())
				.filter(value -> repository.findComponent(value.profile().profileId(), PhantomCommerceReceipt.COMPONENT_TYPE).map(row -> PhantomCommerceReceipt.decode(row.payload()).state() == PhantomCommerceReceipt.State.COMMITTED || PhantomCommerceReceipt.decode(row.payload()).state() == PhantomCommerceReceipt.State.ABORTED).orElse(true))
				.filter(value -> goals.load(value.profile().profileId()).map(row -> Set.of(PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE).contains(row.goal().goalType())).orElse(true)).findFirst().orElseThrow(() -> new IllegalArgumentException("No bounded own READY/calendar-ACTIVE FRESH dwarf without pending authority."));
			final long profileId = managed.profile().profileId(); final int objectId = managed.profile().characterObjectId();
			try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE charId=? AND account_name=? AND char_name=? AND online=0 AND accesslevel=0 AND classid=53 AND base_class=53 AND level BETWEEN 1 AND 10 AND curHp>0 AND NOT EXISTS (SELECT 1 FROM character_quests q WHERE q.charId=characters.charId)"))
			{
				query.setInt(1,objectId); query.setString(2,managed.state().reservedAccount()); query.setString(3,managed.state().characterName()); try (var rows = query.executeQuery()) { rows.next(); PhantomAssertions.assertEquals(1L, rows.getLong(1), "Own066 fresh dwarf canonical identity differs."); }
			}
			try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
			{
				final var all = production.knowledge().snapshot().spawnFactsByNpc();
				final var merchants = all.values().stream().flatMap(List::stream).filter(value -> { final var npc = NpcData.getInstance().getTemplate(value.npcId()); return npc != null && "Merchant".equals(npc.getType()) && value.instanceId() == 0 && production.commerce().buyOffers().stream().anyMatch(offer -> offer.npcIds().contains(value.npcId())); }).toList();
				final var banks = all.values().stream().flatMap(List::stream).filter(value -> { final var npc = NpcData.getInstance().getTemplate(value.npcId()); return npc != null && "Warehouse".equals(npc.getType()) && value.instanceId() == 0; }).toList();
				final var farms = new ArrayList<Farm>(); final var geo = GeoEngine.getInstance();
				for (var entry : all.entrySet())
				{
					final var npc = NpcData.getInstance().getTemplate(entry.getKey());
					if (npc == null || npc.getId() < 20000 || npc.getId() >= 23000 || !"Monster".equals(npc.getType()) || npc.getLevel() < 18 || npc.getLevel() > 22 || npc.getSpoilList() == null || npc.getSpoilList().stream().noneMatch(drop -> drop.getChance() >= 40)) { continue; }
					for (var spawn : entry.getValue())
					{
						if (spawn.instanceId() != 0 || spawn.amount() <= 0 || spawn.topologyNodeId() == null) { continue; }
						for (var anchor : production.topology().snapshot().anchorsByNode().getOrDefault(spawn.topologyNodeId(), List.of()))
						{
							final var point = anchor.point(); if (anchor.role() != PhantomTopologyAnchorRole.FARMING || point.instanceId() != 0 || !geo.hasGeo(point.x(), point.y())) { continue; }
							final double merchantDistance = merchants.stream().mapToDouble(value -> Math.hypot((long)value.x()-point.x(), (long)value.y()-point.y())).min().orElse(Double.MAX_VALUE), bankDistance = banks.stream().mapToDouble(value -> Math.hypot((long)value.x()-point.x(), (long)value.y()-point.y())).min().orElse(Double.MAX_VALUE);
							if (Math.max(merchantDistance, bankDistance) <= 12000 && farms.stream().noneMatch(value -> value.npcId() == npc.getId() && value.anchor().id().equals(anchor.id()))) { farms.add(new Farm(npc.getId(), anchor, Math.max(merchantDistance, bankDistance))); }
						}
					}
				}
				farms.sort(Comparator.comparingInt(Farm::npcId).thenComparing(value -> value.anchor().id()));
				final var primary = farms.stream().filter(value -> value.counterDistance() <= 8000).limit(2).toList(); final var selected = primary.size() == 2 ? primary : farms.stream().limit(2).toList();
				PhantomAssertions.assertTrue(selected.size() == 2, "No two factual stock066 spoil fields within bounded Merchant/Warehouse distance."); final var farm = selected.getFirst(); final var point = farm.anchor().point(); final int z = geo.getHeight(point.x(), point.y(), point.z());
				PhantomAssertions.assertTrue(Math.abs(z-point.z()) <= 128 && geo.canMoveToTarget(point.x(),point.y(),z,point.x()+64,point.y(),z,0), "Selected066 stock field has no native dry corridor.");
				Files.createDirectory(privateRoot); backup(privateRoot, profileId, objectId);
				Files.writeString(privateRoot.resolve("PREPARING.properties"), "profileId="+profileId+"\nobjectId="+objectId+"\ninputs=EXPLICIT_TEST_NOT_EARNED\nprimaryFallback="+selected+"\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
				player = Player.load(objectId); PhantomAssertions.assertTrue(player != null && !player.isGM() && player.getActiveClass() == 53 && player.getClient() == null, "Own066 native dwarf load differs.");
				player.setPlayerClass(54); player.setBaseClass(54); player.setLearningClass(player.getPlayerClass()); player.getStat().setExp(player.getStat().getExpForLevel(20)); player.getStat().setLevel((byte)20); player.setSp(0); Thread.sleep(500); player.rewardSkills();
				player.addSkill(SkillData.getInstance().getSkill(254,2),true);
				// Scavenger has no Crystallize capability; separate native Artisan tests own the G8 proof.
				for (int skill : List.of(42,239)) { player.addSkill(SkillData.getInstance().getSkill(skill,1),true); }
				player.setXYZInvisible(point.x(),point.y(),z); player.setOnlineStatus(true,false); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
				final var disposition = PhantomLootDispositionPolicy.getInstance(); final var items = ItemData.getInstance();
				final var low = disposition.recipeRoles().entrySet().stream().filter(value -> value.getValue().keyPart() && value.getValue().highestProductGrade() >= 1 && value.getValue().highestProductGrade() <= 2 && disposition.category(items.getTemplate(value.getKey())) == PhantomLootDispositionPolicy.Disposition.SELL_NPC).min(Map.Entry.comparingByKey()).orElseThrow();
				final var high = disposition.recipeRoles().entrySet().stream().filter(value -> value.getValue().keyPart() && value.getValue().highestProductGrade() >= 3 && disposition.category(items.getTemplate(value.getKey())) == PhantomLootDispositionPolicy.Disposition.MARKET_RESERVE).min(Map.Entry.comparingByKey()).orElseThrow();
				final var worn = player.getInventory().addItem(ItemProcessType.REWARD,69,1,player,PhantomTask066DwarfSetup.class); player.getInventory().equipItem(worn);
				final var surplus = player.getInventory().addItem(ItemProcessType.REWARD,69,1,player,PhantomTask066DwarfSetup.class); final var protectedItem = player.getInventory().addItem(ItemProcessType.REWARD,69,1,player,PhantomTask066DwarfSetup.class); protectedItem.setEnchantLevel(1);
				player.getInventory().addItem(ItemProcessType.REWARD,low.getKey(),low.getValue().reserve()+1,player,PhantomTask066DwarfSetup.class); player.getInventory().addItem(ItemProcessType.REWARD,high.getKey(),high.getValue().reserve()+3,player,PhantomTask066DwarfSetup.class);
				player.getInventory().addItem(ItemProcessType.REWARD,730,1,player,PhantomTask066DwarfSetup.class); player.getInventory().addItem(ItemProcessType.REWARD,1060,20,player,PhantomTask066DwarfSetup.class); player.getWarehouse().addItem(ItemProcessType.REWARD,1463,240,player,PhantomTask066DwarfSetup.class);
				player.reduceAdena(ItemProcessType.DESTROY,player.getAdena(),player,false); player.refreshExpertisePenalty(); player.refreshOverloaded(); player.getInventory().updateDatabase(); player.getWarehouse().updateDatabase(); player.storeMe();
				for (String component : List.of(PhantomBackgroundState.COMPONENT_TYPE,PhantomNativeContext.COMPONENT_TYPE,PhantomBackgroundCatchupState.COMPONENT_TYPE,PhantomSupplyIntentStore.COMPONENT)) { final var old = repository.findComponent(profileId,component).orElse(null); if (old != null) { repository.deleteComponent(profileId,component,old.rowVersion()); } }
				final var oldRoot = goals.load(profileId).orElse(null); final var root = new PhantomGoal(6600663200L+profileId,PhantomBackgroundGoalSpec.GOAL_TYPE,PhantomGoalStatus.ACTIVE,new PhantomDomainRef("profile",Long.toString(profileId)),new PhantomDomainRef("npc",Integer.toString(farm.npcId())),1,0,"background.farm",List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE,farm.npcId()+"@"+farm.anchor().id())),new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE,farm.anchor().id()),"farm.background",500,0,0,0,Map.of(),"fixture066.premeasurement.dwarf",oldRoot == null ? 0 : oldRoot.goal().revision()+1);
				if (oldRoot == null) { goals.insert(profileId,root); } else { goals.replace(profileId,oldRoot.rowVersion(),root); }
				final var capture = production.authority().captureOwnedNative(profileId,player,root,null); final var result = new PhantomBackgroundTransaction().captureBaseline(capture.state(),root,capture.context()); PhantomAssertions.assertTrue(result.successful(), "Named066 native fixture baseline refused: "+result.status());
				try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("UPDATE characters SET x=?,y=?,z=? WHERE char_name='Task066View' AND account_name='task066player' AND online=0 AND accesslevel=0 AND level=1")) { query.setInt(1,point.x()); query.setInt(2,point.y()); query.setInt(3,z); PhantomAssertions.assertEquals(1,query.executeUpdate(),"Own066 passive S2 viewpoint update failed."); }
				Files.writeString(privateRoot.resolve("READY.properties"),"profileId="+profileId+"\nobjectId="+objectId+"\nname="+player.getName()+"\nclassId=54\nlevel=20\nfield="+point.x()+","+point.y()+","+z+"\nnpcId="+farm.npcId()+"\nsurplusObject="+surplus.getObjectId()+"\nwornObject="+worn.getObjectId()+"\nprotectedObject="+protectedItem.getObjectId()+"\nlowPart="+low.getKey()+"\nhighPart="+high.getKey()+"\ninputs=EXPLICIT_TEST_NOT_EARNED\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
				System.out.println("TASK066_DWARF_PREPARED profile="+profileId+"; object="+objectId+"; stockNpc="+farm.npcId()+"; nativeBaseline=READY; inputs=TEST_NOT_EARNED; no client/GM/production controller");
			}
		}
		finally { try { if (player != null) { player.stopAllTasks(); player.storeMe(); player.deleteMe(); } } finally { ThreadPool.shutdown(); DatabaseFactory.close(); } }
	}

	private static void backup(Path directory, long profileId, int objectId) throws Exception
	{
		try (var connection = DatabaseFactory.getConnection())
		{
			for (var target : Map.of("characters","charId", "items","owner_id", "character_skills","charId", "phantom_profiles","profile_id", "phantom_profile_components","profile_id").entrySet())
			{
				final var lines = new ArrayList<String>();
				try (var query = connection.prepareStatement("SELECT * FROM `"+target.getKey()+"` WHERE `"+target.getValue()+"`=?"))
				{
					query.setLong(1,target.getKey().startsWith("phantom_") ? profileId : objectId); try (var rows = query.executeQuery()) { final int columns = rows.getMetaData().getColumnCount(); while (rows.next()) { if (lines.size() >= 1000) { throw new IllegalStateException("Named066 private preimage bound exceeded."); } final var values = new ArrayList<String>(); for (int column=1;column<=columns;column++) { final byte[] bytes=rows.getBytes(column); values.add(bytes == null ? "." : Base64.getEncoder().encodeToString(bytes)); } lines.add(String.join("\t",values)); } }
				}
				Files.write(directory.resolve(target.getKey()+"-before.tsv"),lines,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
			}
		}
	}
}

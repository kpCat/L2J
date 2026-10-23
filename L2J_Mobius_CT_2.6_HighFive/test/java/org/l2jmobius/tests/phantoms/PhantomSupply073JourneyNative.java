/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.commons.threads.ThreadPool;
import org.l2jmobius.gameserver.data.MerchantPriceConfigTable;
import org.l2jmobius.gameserver.data.xml.InitialEquipmentData;
import org.l2jmobius.gameserver.data.xml.InitialShortcutData;
import org.l2jmobius.gameserver.data.xml.ItemData;
import org.l2jmobius.gameserver.data.xml.MapRegionData;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SpawnData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Merchant;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.combat.L2jCombatBackend;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatCapabilityResolver;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatMode;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatPolicy;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatRequest;
import org.l2jmobius.gameserver.phantoms.combat.PhantomCombatService;
import org.l2jmobius.gameserver.phantoms.commerce.L2jCommerceBackend;
import org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomInventoryMaintenanceIntent;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomLootDispositionPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector;
import org.l2jmobius.gameserver.phantoms.knowledge.L2jGameKnowledgeBackend;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomCuratedKnowledgeParser;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeBuilder;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgePolicy;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomStaticManorParser;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalogBuilder;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.topology.L2jTopologyValidationBackend;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyLoader;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyMetrics;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPolicy;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** Own073 earned supply and early-history proofs. All fixture setup precedes measurement. */
public final class PhantomSupply073JourneyNative
{
	public static void main(String[] args) throws Exception
	{
		if (!PhantomTask073Lane.enabled() || args.length != 1 || !Set.of("preflight", "prepare", "resume", "recover", "history", "history-resume", "inspect").contains(args[0])) { throw new IllegalArgumentException("Own073 explicit finite action required."); }
		final boolean prepare = args[0].equals("prepare");
		final boolean recover = args[0].equals("recover");
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath(), task = module.getParent().resolve(".phantom-local/task073");
		final Path stateFile = task.resolve("supply073-native.properties"), reportFile = task.resolve("reports/supply073-" + args[0] + '-' + System.currentTimeMillis() + ".tsv");
		final var report = new StringBuilder("stage\tevidence\n"); final var state = new Properties();
		PhantomMaterializationService materialization = null; PhantomNavigationService navigation = null; PhantomCommerceService commerce = null; L2jPhantomSupplyJourney journey = null; PhantomCombatService combat = null;
		try
		{
			if (args[0].equals("inspect"))
			{
				PhantomTask073Lane.initialize(module, Path.of(System.getProperty("phantom.test.config")));
				try (var reader = Files.newBufferedReader(stateFile)) { state.load(reader); }
				try (var connection = DatabaseFactory.getConnection())
				{
					connection.setReadOnly(true);
					try (var statement = connection.prepareStatement("SELECT charId,account_name,char_name,level,classid,curHp,curMp,curCp,exp,sp,x,y,z FROM characters WHERE charId=? AND account_name='task073supply'"))
					{
						statement.setInt(1, Integer.parseInt(state.getProperty("actor"))); try (var rows = statement.executeQuery()) { check(rows.next(), "Exact owned measured actor absent."); for (int index = 1; index <= 13; index++) { report.append("native_sql_actor\t").append(rows.getMetaData().getColumnName(index)).append('=').append(rows.getString(index)).append('\n'); } }
					}
					try (var statement = connection.prepareStatement("SELECT object_id,item_id,count,loc,loc_data FROM items WHERE owner_id=? ORDER BY object_id"))
					{ statement.setInt(1, Integer.parseInt(state.getProperty("actor"))); try (var rows = statement.executeQuery()) { while (rows.next()) { report.append("native_sql_item\t").append(rows.getInt(1)).append(':').append(rows.getInt(2)).append(':').append(rows.getLong(3)).append(':').append(rows.getString(4)).append(':').append(rows.getInt(5)).append('\n'); } } }
				}
				final var inspectedProfiles = PhantomProfileRepository.open(); final long inspectedId = Long.parseLong(state.getProperty("profile"));
				report.append("native_root\t").append(goalsSnapshot(inspectedProfiles, inspectedId)).append('\n'); report.append("native_child\t").append(new PhantomSupplyIntentStore(inspectedProfiles, new PhantomGoalStateStore(inspectedProfiles)).intent(inspectedId)).append('\n');
				report.append("result\tREAD_ONLY_NATIVE_SQL_INSPECTION; no materialization/resource/position changes\n"); return;
			}
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(72007303L, module, task.resolve("reports")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance(); ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			MerchantPriceConfigTable.getInstance().loadInstances(); SpawnData.getInstance(); MerchantPriceConfigTable.getInstance().updateReferences();
			final var topologyBackend = new L2jTopologyValidationBackend(); final var topology = new PhantomTopologyQuery(new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1), topologyBackend, new PhantomTopologyMetrics());
			final var knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults(); final var backend = new L2jGameKnowledgeBackend();
			final var knowledge = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, knowledgePolicy), topology, knowledgePolicy));
			check(knowledge.start(), "Native knowledge did not start."); final var query = knowledge.query();
			if (args[0].startsWith("history")) { history(report, args[0].equals("history-resume"), query); return; }
			final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load(); final var profiles = PhantomProfileRepository.open();
			if (args[0].equals("preflight")) { PhantomEconomy073Planner.select(catalog.catalog(), report, query); return; }
			if (prepare)
			{
				check(!Files.exists(stateFile), "Own073 supply fixture already exists; use resume, never refill.");
				final var scene = PhantomEconomy073Planner.select(catalog.catalog(), report, query);
				Files.writeString(task.resolve("reports/SCENE073-" + System.currentTimeMillis() + ".tsv"), report, java.nio.file.StandardOpenOption.CREATE_NEW);
				try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task073supply','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
				final var setup = Player.create(PlayerTemplateData.getInstance().getTemplate(PlayerClass.FIGHTER), "task073supply", "Supply073", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
				final int level = 20;
				check(setup != null && !setup.isGM(), "Own native setup actor absent."); setup.stopAllTasks(); setup.setPlayerClass(1); setup.setBaseClass(1); setup.getStat().setExp(setup.getStat().getExpForLevel(level)); setup.getStat().setLevel((byte) level); setup.giveAvailableAutoGetSkills();
				for (var item : setup.getInventory().getItems().stream().filter(value -> value.isEquipped()).toList()) { setup.getInventory().unEquipItemInSlot(item.getLocationSlot()); }
				for (int id : new int[] {129, 352, 2378}) { final var item = setup.getInventory().addItem(ItemProcessType.REWARD, id, 1, setup, PhantomSupply073JourneyNative.class); setup.getInventory().equipItem(item); }
				check(setup.getAdena() == 0, "Pristine fixture expected; no destruction of history.");
				setup.addAdena(ItemProcessType.REWARD, 100, setup, false);
				setup.setXYZ(scene.farm().getX(), scene.farm().getY(), GeoEngine.getInstance().getHeight(scene.farm().getX(), scene.farm().getY(), scene.farm().getZ())); setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.getInventory().updateDatabase(); setup.storeMe(); final int actorId = setup.getObjectId(); setup.deleteMe();
				final var profile = profiles.create(actorId); state.setProperty("profile", Long.toString(profile.profileId())); state.setProperty("actor", Integer.toString(actorId)); state.setProperty("farmNpc", Integer.toString(scene.farm().getId())); state.setProperty("merchant", Integer.toString(scene.merchant().getId()));
				report.append("SETUP_DECLARED\tCASE_B Warrior").append(level).append(";129 D sword+352+2378 armor and100A NATIVE_PRIOR_HISTORY_FIXTURE;zero shots;NOT a natural1-19 career;earned contributions measured separately\n");
				state.setProperty("gameJarSha256", loadedJarHash()); writeState(stateFile, state);
			}
			else { check(Files.isRegularFile(stateFile), "Prepare checkpoint missing."); try (var reader = Files.newBufferedReader(stateFile)) { state.load(reader); } check(recover ? !"true".equals(state.getProperty("completed")) && Integer.parseInt(state.getProperty("earned.kills", "0")) > 0 : "true".equals(state.getProperty("completed")) && !"true".equals(state.getProperty("restart.completed")), "Continuation/restart must preserve its original finite checkpoint; no replay/refill."); }
			check(loadedJarHash().equals(state.getProperty("gameJarSha256")), "Measured sameJAR binding changed; no acceptance on another binary.");
			final long id = Long.parseLong(state.getProperty("profile")); final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			check(materialization.start() && materialization.materialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Native supply actor did not materialize.");
			final var player = World.getInstance().getPlayer(Integer.parseInt(state.getProperty("actor"))); check(player != null && (recover || !player.isAlikeDead()), "Retained native actor unavailable.");
			final var progressionPolicy = PhantomProgressionPolicy.productionDefaults(); final var progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), () -> query).load(progressionPolicy), progressionPolicy);
			combat = new PhantomCombatService(new L2jCombatBackend(materialization, () -> query, () -> progression), PhantomCombatCapabilityResolver.fromGameKnowledge(() -> query), PhantomCombatPolicy.productionDefaults(1)); combat.start();
			if (recover)
			{
				check(player.getAccountName().equals("task073supply") && player.getAdena() == 1747 && player.getInventory().getInventoryItemCount(1463, -1) == 0 && new PhantomCommerceReceiptStore(profiles).find(id).isEmpty(), "Only the exact retained35-kill unpaid checkpoint may recover; no reset/refill.");
				report.append("RECOVERY_RETAINED\tactor=").append(player.getObjectId()).append(";EXP=").append(player.getExp()).append(";SP=").append(player.getSp()).append(";A=").append(player.getAdena()).append(";HP=").append(player.getCurrentHp()).append(";inventory=").append(inventory(player)).append(";xyz=").append(player.getLocation()).append('\n');
				if (player.isDead())
				{
					final var outcome = combat.respawnTown(new org.l2jmobius.gameserver.phantoms.combat.PhantomRespawnRequest(id, () -> false));
					check(outcome == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.RespawnOutcome.COMPLETED, "Stock town respawn refused; no direct HP/position repair.");
					final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15); while ((player.isDead() || player.isTeleporting()) && System.nanoTime() < until) { Thread.sleep(100); }
					check(!player.isDead() && !player.isTeleporting() && player.getAdena() == 1747, "Native town respawn did not complete with earned wallet retained.");
					report.append("NATIVE_TOWN_RESPAWN\t").append(outcome).append(";A=").append(player.getAdena()).append(";HP=").append(player.getCurrentHp()).append(";xyz=").append(player.getLocation()).append(";stock CombatService owner;no direct setter\n");
				}
				try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { player.setRunning(); }
				final var recoveryField = recoveryField(player, catalog.catalog(), report);
				final var recoveryNavigation = new PhantomNavigationService(metrics); check(recoveryNavigation.start(), "Recovery navigation did not start.");
				try { nativeRecoveryTravel(id, player, recoveryField, materialization, recoveryNavigation, PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology), report); }
				finally { recoveryNavigation.beginStop(); boolean stopped = recoveryNavigation.finishStop(); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); while (!stopped && System.nanoTime() < until) { Thread.sleep(100); stopped = recoveryNavigation.finishStop(); } check(stopped, "Recovery navigation did not drain."); }
				final var roots = new PhantomGoalStateStore(profiles); final var old = roots.load(id).orElseThrow(); final var previous = old.goal();
				final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING).min(Comparator.comparingDouble(value -> player.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).orElseThrow();
				final var replanned = new PhantomGoal(previous.goalId(), previous.goalType(), PhantomGoalStatus.ACTIVE, previous.subject(), new PhantomDomainRef("npc", Integer.toString(recoveryField.getId())), previous.requiredAmount(), previous.currentAmount(), previous.acquisitionMethod(), List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, recoveryField.getId() + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), previous.purposeKey(), previous.priority(), previous.riskBudget(), previous.expenseBudget(), previous.deadlineEpochMillis(), previous.constraints(), "farm.native.replan_after_stock_death073", Math.addExact(previous.revision(), 1), previous.payloadText());
				roots.replace(id, old.rowVersion(), replanned); report.append("NATIVE_DEATH_REPLAN\t").append(previous).append("->").append(replanned).append(";expired unpaid child not deleted;normal SupplyIntentStore will reconcile root revision\n");
			}
			if (!prepare && !recover)
			{
				check("task073supply".equals(player.getAccountName()) && "Supply073".equals(player.getName()), "Exact retained native actor required.");
				final var paid = new PhantomCommerceReceiptStore(profiles).find(id).orElseThrow().receipt();
				check(paid.state() == PhantomCommerceReceipt.State.COMMITTED && paid.operationKey().equals(state.getProperty("paid.operation")) && paid.request().itemId() == 1463, "Original paid shot receipt differs; no new purchase or refill permitted.");
				final var retainedIntent = new PhantomSupplyIntentStore(profiles, new PhantomGoalStateStore(profiles)).intent(id).orElseThrow().goal();
				check(retainedIntent.status() == PhantomGoalStatus.COMPLETED && retainedIntent.reasonKey().contains("returned"), "Original returned child is not retained.");
				check(goalsSnapshot(profiles, id).equals(state.getProperty("root")) && retainedIntent.toString().equals(state.getProperty("intent")) && paid.toString().equals(state.getProperty("receipt")), "Exact durable root/intent/paid receipt changed across JVM.");
				final var retainedRoot = new PhantomGoalStateStore(profiles).load(id).orElseThrow().goal(); final var retainedBinding = PhantomSupplyIntentStore.Binding.parse(retainedIntent);
				check(PhantomSupplyIntentStore.bound(retainedIntent, retainedRoot) && retainedBinding.actorObjectId() == player.getObjectId() && retainedBinding.weaponObjectId() == player.getActiveWeaponInstance().getObjectId(), "Retained child/root/native actor+weapon binding changed.");
				check(Long.toString(player.getExp()).equals(state.getProperty("exp")) && Long.toString(player.getSp()).equals(state.getProperty("sp")) && Long.toString(player.getAdena()).equals(state.getProperty("adena")) && inventory(player).equals(state.getProperty("inventory")), "SameDB persisted native EXP/SP/Adena/item objects differ.");
				report.append("retained_paid_chain\t").append(paid).append(";actor=").append(player.getObjectId()).append(";level=").append(player.getLevel()).append(";HP=").append(player.getCurrentHp()).append(";MP=").append(player.getCurrentMp()).append(";A=").append(player.getAdena()).append(";shots=").append(player.getInventory().getInventoryItemCount(1463, -1)).append(";xyz=").append(player.getLocation()).append(";no Journey replay, grants, relocation or state reset\n");
				final long retainedShots = player.getInventory().getInventoryItemCount(1463, -1); kill(combat, id, player, query, report, materialization);
				check(player.getInventory().getInventoryItemCount(1463, -1) < retainedShots, "Post-restart useful kill must spend retained purchased1463.");
				state.setProperty("restart.completed", "true"); writeState(stateFile, state); report.append("result\tPASS SAME_ACTOR_RESTART productive native rewarded kill; no purchase replay/recompute/refill\n"); return;
			}
			check(player.getLevel() >= 20 && player.getInventory().getInventoryItemCount(1463, -1) == 0 && player.getActiveWeaponItem().getCrystalTypePlus() == org.l2jmobius.gameserver.model.item.type.CrystalType.D, "Materialized measured actor must retain20+/zero matching shots/legal D gear.");
			check(recover ? player.getAdena() > 100 : player.getAdena() == 100, "Declared fixture and retained native earnings must not be replaced.");
			try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { player.setRunning(); }
			check(player.getMoveSpeed() >= PlayerTemplateData.getInstance().getTemplate(PlayerClass.WARRIOR).getBaseMoveSpeed(org.l2jmobius.gameserver.model.stats.MoveType.RUN), "Live movement below the pre-birth conservative speed bound; measurement refused.");
			report.append("LIVE_PREMEASUREMENT_ECONOMICS\tweapon=").append(player.getActiveWeaponItem().getId()).append(";grade=").append(player.getActiveWeaponItem().getCrystalTypePlus()).append(";perCharge=").append(player.getActiveWeaponItem().getSoulShotCount()).append(";speed=").append(player.getMoveSpeed()).append(";fixtureA=100;nativePolicy=").append(PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"))).append('\n');
			report.append(recover ? "MEASUREMENT_CONTINUED\t" : "MEASUREMENT_START\t").append("actor=").append(player.getObjectId()).append(";profile=").append(id).append(";level=").append(player.getLevel()).append(";xyz=").append(player.getLocation()).append(";EXP=").append(player.getExp()).append(";SP=").append(player.getSp()).append(";A=").append(player.getAdena()).append(";Dshots=0;HP=").append(player.getCurrentHp()).append(";inventory=").append(inventory(player)).append('\n');
			// Running is an ordinary native posture change, without a buff or coordinate/resource edit.
			try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { player.setRunning(); }
			final var goals = new PhantomGoalStateStore(profiles);
			if (goals.load(id).isEmpty())
			{
				final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING).min(Comparator.comparingDouble(value -> player.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).orElseThrow();
				goals.insert(id, new PhantomGoal(72007303L, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", state.getProperty("farmNpc")), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, state.getProperty("farmNpc") + '@' + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture073.native_short_supply", 1));
			}
			final var root = goals.load(id).orElseThrow().goal(); final var receipts = new PhantomCommerceReceiptStore(profiles); final var store = new PhantomSupplyIntentStore(profiles, goals);
			navigation = new PhantomNavigationService(metrics); commerce = new PhantomCommerceService(catalog, receipts, store, new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone()));
			journey = new L2jPhantomSupplyJourney(store, goals, materialization, commerce, receipts, navigation, () -> query, PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology), _ -> true, (_, _) -> true, (_, _) -> false, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")));
			journey.useInventoryMaintenance(_ -> true); check(navigation.start() && commerce.start() && journey.start(), "Native supply owners did not start.");
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			// One finite recovery: use the actual tax, per-hit debit, speed and distance; do not demand a fabricated fixed wallet.
			if (prepare)
			{
			final var initialPlanning = new PhantomPlanningContext(id, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
			final var initialCandidate = new PhantomUtilitySelector().select(candidates.snapshot(), initialPlanning).candidate();
			check(initialCandidate != null, "Insufficient wallet lost its economic objective.");
			final var initialPlan = initialCandidate.planFactory().create(initialPlanning);
			handlers.snapshot().get(initialPlan.steps().getFirst().actionKey()).execute(new PhantomStepContext(id, root, initialPlan, initialPlan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false));
			final var shortage = journey.automaticIntent(id).orElseThrow();
			check(shortage.acquisitionMethod().equals("income.farm") && shortage.status() == PhantomGoalStatus.FAILED && goals.load(id).orElseThrow().goal().equals(root), "Shortage must preserve the active farm root, without resource writes.");
			report.append("RECOVERY_INCOME_REQUIRED\t").append(shortage).append(";rootRetained=").append(root).append(";wallet=").append(player.getAdena()).append('\n');
			}
			final long earnDeadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
			int earnedKills = recover ? Integer.parseInt(state.getProperty("earned.kills")) : 0;
			while (earnedKills < 2 || !affordableShots(player, catalog.catalog(), report))
			{
				check(System.nanoTime() < earnDeadline, "BLOCKED_ECONOMICS: finite15min earning budget exhausted; actor retained, no reset.");
				kill(combat, id, player, query, report, materialization); earnedKills++;
				state.setProperty("earned.kills", Integer.toString(earnedKills)); writeState(stateFile, state);
			}
			report.append("NATIVE_EARNED\tkills=").append(earnedKills).append(";earnedA=").append(player.getAdena() - 100).append(";fixtureA=100;wallet=").append(player.getAdena()).append('\n');
			check(player.getAdena() > 100, "PARTIAL_EARNINGS: no native Adena contribution; no money grant permitted.");
			final Set<String> observed = new HashSet<>(); boolean sale = false, buy = false, returned = false; long boughtStock = -1;
			final long until = System.nanoTime() + TimeUnit.MINUTES.toNanos(8); String lastReason = "";
			do
			{
				check(!player.isDead(), "Native actor died during supply; stop immediately and retain evidence.");
				final var threat = World.getInstance().getVisibleObjectsInRange(player, Monster.class, 700).stream().filter(value -> !value.isAlikeDead() && !value.isRaid() && value.getTarget() == player && value.getLevel() <= player.getLevel() + 2 && GeoEngine.getInstance().canSeeTarget(player, value)).min(Comparator.comparingDouble(value -> player.calculateDistance3D(value))).orElse(null);
				if (threat != null) { report.append("NATIVE_SUPPLY_DEFENSE\tactualAttacker=").append(threat.getId()).append('/').append(threat.getObjectId()).append(";HP=").append(player.getCurrentHp()).append('\n'); killTarget(combat, id, player, threat, report); continue; }
				final var planning = new PhantomPlanningContext(id, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1); final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
				if (selected != null) { final var plan = selected.planFactory().create(planning); handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(id, root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false)); }
				final String reason = journey.reason(id); if (!reason.equals(lastReason)) { report.append("journey\t").append(reason).append(";xyz=").append(player.getLocation()).append(";A=").append(player.getAdena()).append('\n'); System.out.println("SUPPLY073 " + reason + ";xyz=" + player.getLocation()); lastReason = reason; }
				final var receipt = receipts.find(id).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
				if (receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED && observed.add(receipt.operationKey()))
				{
					report.append("committed\t").append(receipt).append('\n'); sale |= receipt.request().kind() == PhantomCommerceReceipt.OperationKind.SELL;
					if (receipt.request().kind() == PhantomCommerceReceipt.OperationKind.BUY && receipt.request().itemId() == 1463) { check(receipt.request().amount() > 100 && receipt.before().primaryCount() - receipt.expectedAfter().primaryCount() == receipt.request().amount() && receipt.expectedAfter().secondaryCount() - receipt.before().secondaryCount() == receipt.request().count() && player.getAdena() == receipt.expectedAfter().primaryCount() && player.getInventory().getInventoryItemCount(1463, -1) == receipt.expectedAfter().secondaryCount(), "Paid shot physical conservation/earned contribution failed."); buy = true; boughtStock = player.getInventory().getInventoryItemCount(1463, -1); state.setProperty("paid.operation", receipt.operationKey()); }
				}
				final var intent = journey.automaticIntent(id).orElse(null);
				if (intent != null && intent.status() == PhantomGoalStatus.FAILED && !"income.farm".equals(intent.acquisitionMethod())) { throw new AssertionError("Native journey failed: " + intent); }
				returned = intent != null && intent.status() == PhantomGoalStatus.COMPLETED; if (returned) { break; }
				Thread.sleep(200);
			} while (System.nanoTime() < until);
			{
				check(buy && returned, "Connected earned native paid D shot BUY/return did not complete within8min: " + lastReason);
				final long exp = player.getExp(), sp = player.getSp();
				for (int kill = 0; kill < 2; kill++) { final long shotsBefore = player.getInventory().getInventoryItemCount(1463, -1); kill(combat, id, player, query, report, materialization); check(player.getInventory().getInventoryItemCount(1463, -1) < shotsBefore, "Each rewarded post-return kill must debit purchased1463."); }
				check(player.getExp() > exp && player.getSp() > sp && player.getInventory().getInventoryItemCount(1463, -1) < boughtStock, "Post-return native kills did not earn EXP/SP and spend paid shots.");
				report.append("result\tPASS native earned-before-spent->paid1463->return->2 native rewarded kills with paid shot debit;no measurement relocation/grants;A=").append(player.getAdena()).append(";EXP=").append(exp).append("->").append(player.getExp()).append(";SP=").append(sp).append("->").append(player.getSp()).append('\n');
				state.setProperty("exp", Long.toString(player.getExp())); state.setProperty("sp", Long.toString(player.getSp())); state.setProperty("adena", Long.toString(player.getAdena())); state.setProperty("inventory", inventory(player));
				state.setProperty("root", goalsSnapshot(profiles, id)); state.setProperty("intent", journey.automaticIntent(id).orElseThrow().toString()); state.setProperty("receipt", receipts.find(id).orElseThrow().receipt().toString());
				state.setProperty("completed", "true"); writeState(stateFile, state);
			}
		}
		catch (Throwable failure) { report.append("failure\t").append(failure).append('\n'); throw failure; }
		finally
		{
			try
			{
				final var cleanup = new ArrayList<AutoCloseable>(); final var ownedMaterialization = materialization; final var ownedNavigation = navigation; final var ownedCommerce = commerce; final var ownedJourney = journey; final var ownedCombat = combat;
				if (ownedMaterialization != null) { cleanup.add(() -> { final var stopped = ownedMaterialization.shutdown(); report.append("shutdown\t").append(stopped).append('\n'); check(stopped.state() == PhantomMaterializationService.ServiceState.STOPPED, "Native materialization did not persist/drain; PASS forbidden."); }); }
				if (ownedNavigation != null) { cleanup.add(() -> { ownedNavigation.beginStop(); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); boolean stopped = ownedNavigation.finishStop(); while (!stopped && System.nanoTime() < until) { Thread.sleep(100); stopped = ownedNavigation.finishStop(); } check(stopped, "Navigation did not drain."); }); }
				if (ownedCommerce != null) { cleanup.add(() -> { ownedCommerce.beginStop(); check(ownedCommerce.finishStop(), "Commerce did not drain."); }); }
				if (ownedJourney != null) { cleanup.add(() -> { ownedJourney.beginStop(); check(ownedJourney.finishStop(), "Supply did not drain."); }); }
				if (ownedCombat != null) { cleanup.add(() -> { ownedCombat.beginStop(); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); while (!ownedCombat.finishStop() && System.nanoTime() < until) { Thread.sleep(100); } check(ownedCombat.finishStop(), "Combat did not drain."); }); }
				closeAll(cleanup);
			}
			finally { try { Files.createDirectories(reportFile.getParent()); Files.writeString(reportFile, report); } finally { try { if (!args[0].equals("inspect")) { ThreadPool.shutdown(); } } finally { DatabaseFactory.close(); } } }
		}
	}

	private static void kill(PhantomCombatService combat, long profileId, Player player, PhantomGameKnowledgeQuery knowledge, StringBuilder report) throws Exception
	{
		kill(combat, profileId, player, knowledge, report, null);
	}

	private static void kill(PhantomCombatService combat, long profileId, Player player, PhantomGameKnowledgeQuery knowledge, StringBuilder report, PhantomMaterializationService materialization) throws Exception
	{
		final var targets = World.getInstance().getVisibleObjectsInRange(player, Monster.class, materialization == null ? 700 : 5000).stream()
			.filter(mob -> mob.isSpawned() && !mob.isAlikeDead() && !mob.isRaid() && mob.getInstanceId() == player.getInstanceId() && !mob.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE))
			.filter(mob -> knowledge.findNpc(mob.getId()).map(fact -> fact.kind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind.MONSTER && fact.level() >= player.getLevel() - 2 && fact.level() <= player.getLevel() + 2 && fact.exp() > 0 && fact.sp() > 0).orElse(false))
			.sorted(Comparator.comparingDouble(mob -> player.calculateDistance3D(mob))).limit(8).toList();
		final long approachDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
		targets: for (var target : targets)
		{
			if (materialization != null && (player.calculateDistance3D(target) > 600 || !GeoEngine.getInstance().canSeeTarget(player, target)))
			{
				final var paths = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
				final var from = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player);
				final var to = paths.walkSurface(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(target.getX(), target.getY(), target.getZ(), target.getInstanceId()), 256).orElse(null); if (to == null) { continue; }
				final long now = System.nanoTime();
				final var route = paths.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(profileId, from, to, now, now + TimeUnit.SECONDS.toNanos(1), 5000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), System::nanoTime);
				report.append("local_target_route\ttarget=").append(target.getId()).append('/').append(target.getObjectId()).append(";level=").append(target.getLevel()).append(";status=").append(route.status()).append(";distance=").append(from.distanceTo(to)).append('\n');
				if (route.status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND) { continue; }
				try (var lease = materialization.tryAcquireAction(profileId).orElseThrow())
				{
					final long until = Math.min(approachDeadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(60));
					for (var point : route.points())
					{
						if (player.calculateDistance3D(point.x(), point.y(), point.z()) <= 40) { continue; }
						check(!player.isDead() && paths.canWalkDirect(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player), point), "Local native approach segment invalid.");
						if (!org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePveWalkSegment(player, point.x(), point.y(), point.z(), List.copyOf(World.getInstance().getVisibleObjectsInRange(player, Monster.class, 3500)), Set.of(target.getObjectId()))) { report.append("local_target_skipped\tunowned native threat;target=").append(target.getObjectId()).append(";xyz=").append(player.getLocation()).append('\n'); continue targets; }
						player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.MOVE_TO, new org.l2jmobius.gameserver.model.Location(point.x(), point.y(), point.z()));
						final var walk = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.captureWalk(player);
						try { while (!player.isDead() && player.calculateDistance3D(point.x(), point.y(), point.z()) > 40 && System.nanoTime() < until) { Thread.sleep(100); } check(player.calculateDistance3D(point.x(), point.y(), point.z()) <= 40, "Native local approach deadline; no teleport."); }
						finally { org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.cancelWalk(walk); }
					}
				}
			}
			if (GeoEngine.getInstance().canSeeTarget(player, target)) { killTarget(combat, profileId, player, target, report); return; }
		}
		throw new AssertionError("BLOCKED_INPUT: no rewarded normal target on bounded native local route; SEARCH_LIMIT remains budget evidence only.");
	}

	private static void killTarget(PhantomCombatService combat, long profileId, Player player, Monster target, StringBuilder report) throws Exception
	{
		final long beforeExp = player.getExp(), beforeSp = player.getSp(), beforeShots = player.getInventory().getInventoryItemCount(1463, -1);
		final var inventoryBefore = player.getInventory().getItems().stream().collect(java.util.stream.Collectors.toMap(item -> item.getId(), item -> item.getCount(), Long::sum));
		final var start = combat.startSession(new PhantomCombatRequest(profileId, target.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL, true, true, 30_000, () -> false));
		check(start.accepted(), "Native rewarded combat refused: " + start);
		final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
		while (System.nanoTime() < until && !combat.find(profileId).map(value -> value.result().terminal()).orElse(false)) { Thread.sleep(100); }
		final var finished = combat.find(profileId).orElseThrow();
		check(finished.result().victory() && target.isDead() && !player.isDead() && player.getExp() > beforeExp && player.getSp() > beforeSp, "Native rewarded kill failed: " + finished);
		boolean consumed = false; final long cleanupUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!consumed && System.nanoTime() < cleanupUntil) { consumed = combat.consumeTerminal(profileId).isPresent(); if (!consumed) { Thread.sleep(100); } }
		check(consumed, "Native victory ownership did not release.");
		report.append("earned_kill\ttarget=").append(target.getId()).append('/').append(target.getObjectId()).append(";EXP=").append(beforeExp).append("->").append(player.getExp()).append(";SP=").append(beforeSp).append("->").append(player.getSp()).append(";paidShots=").append(beforeShots).append("->").append(player.getInventory().getInventoryItemCount(1463, -1)).append(";session=").append(finished).append('\n');
		final var inventoryAfter = player.getInventory().getItems().stream().collect(java.util.stream.Collectors.toMap(item -> item.getId(), item -> item.getCount(), Long::sum));
		report.append("earned_loot_delta\t").append(inventoryAfter.entrySet().stream().filter(entry -> !entry.getValue().equals(inventoryBefore.getOrDefault(entry.getKey(), 0L))).sorted(Map.Entry.comparingByKey()).limit(32).map(entry -> entry.getKey() + ":" + inventoryBefore.getOrDefault(entry.getKey(), 0L) + "->" + entry.getValue()).toList()).append(";nativeLootPickups=").append(finished.lootPickupsIssued()).append('\n');
	}


	private static Monster recoveryField(Player player, PhantomCommerceCatalog catalog, StringBuilder report)
	{
		final var merchant = World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof Merchant).map(value -> (Merchant) value).filter(value -> value.getId() == 30063 && value.isSpawned() && value.getMpc() != null && value.getMpc().getCastleTaxRate() == 0).findFirst().orElseThrow();
		final var offer = catalog.findBuyOffers(1463, 0, 256).values().stream().filter(value -> value.npcIds().contains(30063) && !value.limitedStock()).findFirst().orElseThrow();
		final var paths = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend(); final var policy = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
		final long price = (long) (offer.price() * (1 + merchant.getMpc().getBaseTaxRate())), budget = policy.availableBudget(player.getAdena(), 0, 0, true), count = Math.min(180, budget / price);
		final var choices = World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof Monster).map(value -> (Monster) value).filter(value -> value.getId() == 21801 && value.isSpawned() && !value.isAlikeDead() && value.getLevel() >= 18 && value.getLevel() <= 22 && !value.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE)).sorted(Comparator.comparingDouble(value -> value.calculateDistance3D(merchant))).limit(2).toList();
		for (var mob : choices)
		{
			if (!policy.usefulPurchase(true, 0, count, player.getActiveWeaponItem().getSoulShotCount(), mob.calculateDistance3D(merchant), player.getMoveSpeed())) { report.append("recovery_scene_rejected\tQUOTE threshold;mob=").append(mob.getLocation()).append('\n'); continue; }
			final var origin = paths.walkSurface(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(mob.getX(), mob.getY(), mob.getZ(), 0), 256).orElse(null); if (origin == null) { continue; }
			for (int direction = 0; direction < 4; direction++)
			{
				final double angle = Math.atan2((long) mob.getY() - merchant.getY(), (long) mob.getX() - merchant.getX()) + direction * Math.PI / 2;
				final var target = paths.walkSurface(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(merchant.getX() + (int) Math.round(64 * Math.cos(angle)), merchant.getY() + (int) Math.round(64 * Math.sin(angle)), merchant.getZ(), 0), 256).orElse(null);
				if (target == null || merchant.calculateDistance3D(target.x(), target.y(), target.z()) > 184) { continue; }
				long now = System.nanoTime(); final var out = paths.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(1, origin, target, now, now + TimeUnit.SECONDS.toNanos(1), 10_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), System::nanoTime);
				now = System.nanoTime(); final var back = paths.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(1, target, origin, now, now + TimeUnit.SECONDS.toNanos(1), 10_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults(), System::nanoTime);
				report.append("recovery_scene\tNPC=").append(merchant.getId()).append(';').append(merchant.getLocation()).append(";mob=").append(mob.getId()).append(';').append(mob.getLocation()).append(";stand=").append(target).append(";price=").append(price).append(";baseTax=").append(merchant.getMpc().getBaseTaxRate()).append(";perCharge=").append(player.getActiveWeaponItem().getSoulShotCount()).append(";speed=").append(player.getMoveSpeed()).append(";distance=").append(mob.calculateDistance3D(merchant)).append(";wallet=").append(player.getAdena()).append(";budget=").append(budget).append(";affordableShots=").append(count).append(";routes=").append(out.status()).append('/').append(back.status()).append('\n');
				if (out.status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND && back.status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND) { return mob; }
			}
		}
		throw new AssertionError("BLOCKED_RECOVERY_SCENE: two deterministic retained-wealth candidates exhausted; no reseed or price change.");
	}
	private static void nativeRecoveryTravel(long id, Player player, Monster field, PhantomMaterializationService materialization, PhantomNavigationService navigation, PhantomNormalGatekeeperTravel travel, StringBuilder report) throws Exception
	{
		final var paths = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
		final var origin = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player); final var destination = paths.walkSurface(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(field.getX(), field.getY(), field.getZ(), 0), 256).orElseThrow();
		final long now = System.nanoTime(), deadline = now + TimeUnit.MINUTES.toNanos(10);
		final var actual = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.snapshot(player, travel.legs(), TimeUnit.MINUTES.toMillis(10));
		final var options = new org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Options(actual.legs(), actual.speed(), actual.adena(), 0, actual.classIndex(), actual.level(), actual.epochMinute(), actual.gatekeeperAllowed(), "recovery073.no_scroll", null, 0, actual.remainingMillis(), actual.blockedEdges());
		final var submission = navigation.submitTravelPlan(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(id, origin, destination, now, now + navigation.policy().defaultRequestDeadlineNanos(), navigation.policy().maximumRouteDistance()), options);
		var result = submission.immediateResult(); while (result == null && System.nanoTime() < now + navigation.policy().defaultRequestDeadlineNanos()) { result = navigation.consume(submission.requestId()).orElse(null); if (result == null) { Thread.sleep(100); } }
		report.append("recovery_native_route\t").append(origin).append("->").append(destination).append(";result=").append(result).append('\n');
		check(result != null && result.route() != null, "No native stock travel plan from actual respawn to field; no relocation fallback.");
		final var plan = navigation.consumeTravelPlan(submission.requestId()).orElseThrow(); report.append("recovery_native_itinerary\t").append(plan).append('\n');
		try (var lease = materialization.tryAcquireAction(id).orElseThrow())
		{
			for (var stage : plan.stages())
			{
				check(!player.isDead() && System.nanoTime() < deadline, "Native recovery travel exhausted its bound.");
				if (stage.mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Mode.GK)
				{
					final var receipt = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.gatekeeper(player, stage.gatekeeper()); report.append("recovery_native_gatekeeper\t").append(receipt).append('\n'); check(receipt.status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.Status.ARRIVED, "Actual native GK refused."); continue;
				}
				check(stage.mode() == org.l2jmobius.gameserver.phantoms.navigation.PhantomTravelStrategy.Mode.WALK, "Unsupported recovery travel mode.");
				for (var point : stage.points())
				{
					if (player.calculateDistance3D(point.x(), point.y(), point.z()) <= 40) { continue; }
					check(paths.canWalkDirect(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.point(player), point) && org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePveWalkSegment(player, point.x(), point.y(), point.z(), List.copyOf(World.getInstance().getVisibleObjectsInRange(player, Monster.class, 3500)), Set.of(field.getObjectId())), "Native recovery segment unsafe; no forced movement.");
					player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.MOVE_TO, new org.l2jmobius.gameserver.model.Location(point.x(), point.y(), point.z()));
					final var owned = org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.captureWalk(player);
					try { while (!player.isDead() && player.calculateDistance3D(point.x(), point.y(), point.z()) > 40 && System.nanoTime() < deadline) { Thread.sleep(100); } check(!player.isDead() && player.calculateDistance3D(point.x(), point.y(), point.z()) <= 40, "Native recovery segment did not arrive."); }
					finally { org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.cancelWalk(owned); }
				}
			}
		}
		report.append("recovery_native_field_arrived\t").append(player.getLocation()).append(";A=").append(player.getAdena()).append(";HP=").append(player.getCurrentHp()).append(";native stock WALK/GK only\n");
	}
	private static void history(StringBuilder report, boolean resume, PhantomGameKnowledgeQuery query) throws Exception
	{
		final Path file = Path.of(System.getProperty("phantom.module.root")).getParent().resolve(".phantom-local/task073/history073.properties");
		final var state = new Properties(); final var profiles = PhantomProfileRepository.open();
		if (resume) { try (var reader = Files.newBufferedReader(file)) { state.load(reader); } check(loadedJarHash().equals(state.getProperty("gameJarSha256")), "Early history sameJAR binding changed."); }
		else
		{
			check(!Files.exists(file), "Early history already exists; no reseed.");
			final var retained = profiles.findByCharacterObjectId(268480996).orElse(null);
			if (retained != null)
			{
				try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT charId FROM characters WHERE charId=268480996 AND account_name='task073early' AND char_name='Earned073Early' AND level<20")) { try (var rows = statement.executeQuery()) { check(rows.next() && !rows.next(), "Exact failed-admission early actor changed."); } }
				state.setProperty("actor", "268480996"); state.setProperty("profile", Long.toString(retained.profileId())); state.setProperty("gameJarSha256", loadedJarHash());
				report.append("EARLY_RETAINED_NO_REFILL\tactor268480996/profile").append(retained.profileId()).append(";initial failed TARGET admission used null knowledge;same stock actor retained without any setup writes\n");
			}
			else
			{
			final var template = PlayerTemplateData.getInstance().getTemplate(PlayerClass.FIGHTER);
			final var birth = org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.resolveCreationLocation(template);
			final var mob = World.getInstance().getVisibleObjects().stream().filter(object -> object instanceof Monster).map(object -> (Monster) object)
				.filter(value -> value.isSpawned() && !value.isAlikeDead() && !value.isRaid() && value.getInstanceId() == 0 && value.getLevel() <= 2 && value.getExpReward(1) > 0 && !value.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE))
				.sorted(Comparator.comparingDouble(value -> value.calculateDistance3D(birth))).filter(value -> GeoEngine.getInstance().hasGeo(value.getX(), value.getY())).findFirst().orElseThrow();
			try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task073early','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
			final var setup = Player.create(template, "task073early", "Earned073Early", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
			check(setup != null, "Early native birth absent."); setup.stopAllTasks();
			org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.initialize(setup, org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.Mode.POPULATION, new org.l2jmobius.gameserver.model.Location(mob.getX(), mob.getY(), mob.getZ()));
			setup.getInventory().updateDatabase(); setup.storeMe(); final int actor = setup.getObjectId();
			report.append("EARLY_STOCK_BIRTH\tactor=").append(actor).append(";level=").append(setup.getLevel()).append(";A=").append(setup.getAdena()).append(";inventory=").append(inventory(setup)).append(";location fixture only BEFORE measurement;stock initializer/items/rates\n");
			setup.deleteMe(); state.setProperty("actor", Integer.toString(actor)); state.setProperty("profile", Long.toString(profiles.create(actor).profileId())); state.setProperty("gameJarSha256", loadedJarHash());
			}
		}
		final long id = Long.parseLong(state.getProperty("profile")); final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		PhantomCombatService combat = null;
		try
		{
			check(materialization.start() && materialization.materialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Early native actor cannot materialize.");
			final var player = World.getInstance().getPlayer(Integer.parseInt(state.getProperty("actor"))); check(player != null && player.getLevel() < 20, "Actual early native profile required.");
			if (resume)
			{
				check(state.getProperty("exp").equals(Long.toString(player.getExp())) && state.getProperty("sp").equals(Long.toString(player.getSp())) && state.getProperty("adena").equals(Long.toString(player.getAdena())) && state.getProperty("inventory").equals(inventory(player)) && player.getAdena() > 0, "Early genuine earned cash/loot changed across native store/newJVM.");
				report.append("HISTORY_EARNED_PRESERVED\tactor=").append(player.getObjectId()).append(";profile=").append(id).append(";level=").append(player.getLevel()).append(";EXP=").append(player.getExp()).append(";SP=").append(player.getSp()).append(";A=").append(player.getAdena()).append(";exactPositiveObjects=").append(inventory(player)).append(";newJVM;no1to20claim\n"); return;
			}
			combat = new PhantomCombatService(new L2jCombatBackend(materialization, () -> query), PhantomCombatCapabilityResolver.fromGameKnowledge(() -> query), PhantomCombatPolicy.productionDefaults(1)); combat.start();
			try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { player.setRunning(); }
			final long beforeA = player.getAdena(), deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(3); int kills = 0;
			while (kills < 2 || player.getAdena() <= beforeA)
			{
				check(System.nanoTime() < deadline, "HISTORY_NOT_OBSERVED: no real early earned currency within3min; no gift.");
				final var target = World.getInstance().getVisibleObjectsInRange(player, Monster.class, 600).stream().filter(value -> value.isSpawned() && !value.isAlikeDead() && !value.isRaid() && value.getLevel() <= player.getLevel() + 1 && value.getExpReward(player.getLevel()) > 0 && GeoEngine.getInstance().canSeeTarget(player, value)).min(Comparator.comparingDouble(value -> player.calculateDistance3D(value))).orElseThrow();
				killTarget(combat, id, player, target, report); kills++;
			}
			state.setProperty("exp", Long.toString(player.getExp())); state.setProperty("sp", Long.toString(player.getSp())); state.setProperty("adena", Long.toString(player.getAdena())); state.setProperty("inventory", inventory(player));
			try (var writer = Files.newBufferedWriter(file, java.nio.file.StandardOpenOption.CREATE_NEW)) { state.store(writer, "Own073 genuine early native earnings; no reseed"); }
			report.append("EARLY_NATIVE_EARNED\tA=").append(beforeA).append("->").append(player.getAdena()).append(";kills=").append(kills).append(";level=").append(player.getLevel()).append(";EXP=").append(player.getExp()).append(";inventory=").append(inventory(player)).append('\n');
		}
		finally
		{
			try { if (combat != null) { combat.beginStop(); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); boolean stopped = combat.finishStop(); while (!stopped && System.nanoTime() < until) { Thread.sleep(100); stopped = combat.finishStop(); } check(stopped, "Early combat did not drain."); } }
			finally { final var stopped = materialization.shutdown(); report.append("early_shutdown\t").append(stopped).append('\n'); check(stopped.state() == PhantomMaterializationService.ServiceState.STOPPED, "Early native store did not drain."); }
		}
	}
	static void closeAll(List<AutoCloseable> owners) throws Exception
	{
		Throwable failure = null;
		for (int index = owners.size() - 1; index >= 0; index--) { try { owners.get(index).close(); } catch (Throwable e) { if (failure == null) { failure = e; } else { failure.addSuppressed(e); } } }
		if (failure instanceof Error error) { throw error; }
		if (failure instanceof Exception exception) { throw exception; }
	}
	private static boolean affordableShots(Player player, PhantomCommerceCatalog catalog, StringBuilder report)
	{
		final var policy = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")); final int perHit = player.getActiveWeaponItem().getSoulShotCount();
		for (var offer : catalog.findBuyOffers(1463, 0, 256).values())
		{
			for (var npc : World.getInstance().getVisibleObjectsInRange(player, Merchant.class, 5000))
			{
				if (!npc.isSpawned() || !offer.npcIds().contains(npc.getId()) || npc.getMpc() == null || npc.getMpc().getCastleTaxRate() != 0) { continue; }
				final long price = (long) (offer.price() * (1 + npc.getMpc().getBaseTaxRate())), budget = policy.availableBudget(player.getAdena(), 0, 0, true); if (price <= 0) { continue; }
				final long count = Math.min((long) perHit * policy.targetHits(), budget / price); final boolean useful = policy.usefulPurchase(true, 0, count, perHit, player.calculateDistance3D(npc), player.getMoveSpeed()) && L2jPhantomSupplyJourney.mobilePurchase(player, ItemData.getInstance().getTemplate(1463), count);
				report.append("earned_budget\tnpc=").append(npc.getId()).append(";A=").append(player.getAdena()).append(";taxedUnit=").append(price).append(";perHit=").append(perHit).append(";distance=").append(player.calculateDistance3D(npc)).append(";speed=").append(player.getMoveSpeed()).append(";isRunning=").append(player.isRunning()).append(";count=").append(count).append(";useful=").append(useful).append('\n');
				if (useful) { return true; }
			}
		}
		return false;
	}
	private static String loadedJarHash() throws Exception { final Path jar = Path.of(PhantomCombatService.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath(); check(Files.isRegularFile(jar) && jar.getFileName().toString().equals("GameServer.jar"), "Measured native code must be loaded from whole GameServer.jar."); return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))); }
	private static String goalsSnapshot(PhantomProfileRepository profiles, long id) { return new PhantomGoalStateStore(profiles).load(id).orElseThrow().goal().toString(); }
	private static String inventory(Player player) { return player.getInventory().getItems().stream().filter(item -> item.getCount() > 0).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.isEquipped()).collect(java.util.stream.Collectors.joining(";")); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
	private static void writeState(Path file, Properties state) throws Exception { try (var writer = Files.newBufferedWriter(file)) { state.store(writer, "Own073 finite native fixture; never refill or reset"); } }
}

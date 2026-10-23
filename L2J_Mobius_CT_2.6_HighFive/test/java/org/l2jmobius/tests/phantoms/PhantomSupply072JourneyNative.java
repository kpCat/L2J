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

/** Own072 two-JVM supply continuation. All setup is before measurement; fixture liquidation is distinguished from earned loot. */
public final class PhantomSupply072JourneyNative
{
	public static void main(String[] args) throws Exception
	{
		if (!PhantomTask072Lane.enabled() || args.length != 1 || !Set.of("preflight", "prepare", "continue", "resume", "cp-purchase", "cp-retained", "pvp").contains(args[0])) { throw new IllegalArgumentException("Own072 preflight, prepare, continue, resume, cp-purchase, cp-retained or pvp required."); }
		final boolean prepare = args[0].equals("prepare");
		final boolean continuation = args[0].equals("continue");
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath(), task = module.getParent().resolve(".phantom-local/task072");
		final Path stateFile = task.resolve("supply072-native.properties"), reportFile = task.resolve("reports/supply072-" + args[0] + '-' + System.currentTimeMillis() + ".tsv");
		final var report = new StringBuilder("stage\tevidence\n"); final var state = new Properties();
		PhantomMaterializationService materialization = null; PhantomNavigationService navigation = null; PhantomCommerceService commerce = null; L2jPhantomSupplyJourney journey = null; PhantomCombatService combat = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(72007203L, module, task.resolve("reports")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance(); ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			MerchantPriceConfigTable.getInstance().loadInstances(); SpawnData.getInstance(); MerchantPriceConfigTable.getInstance().updateReferences();
			if (args[0].startsWith("cp-")) { cpPurchase(report, args[0].equals("cp-retained")); return; }
			final var topologyBackend = new L2jTopologyValidationBackend(); final var topology = new PhantomTopologyQuery(new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1), topologyBackend, new PhantomTopologyMetrics());
			final var knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults(); final var backend = new L2jGameKnowledgeBackend();
			final var knowledge = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, knowledgePolicy), topology, knowledgePolicy));
			check(knowledge.start(), "Native knowledge did not start."); final var query = knowledge.query();
			if (args[0].equals("pvp")) { PhantomPvp072Native.run(knowledge, topology, report); return; }
			final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load(); final var profiles = PhantomProfileRepository.open();
			if (args[0].equals("preflight")) { cpSurface(report); selectScene(catalog.catalog(), report, query); return; }
			if (prepare)
			{
				check(!Files.exists(stateFile), "Own072 supply fixture already exists; use resume, never refill.");
				final var scene = selectScene(catalog.catalog(), report, query);
				Files.writeString(task.resolve("reports/SCENE072-" + System.currentTimeMillis() + ".tsv"), report, java.nio.file.StandardOpenOption.CREATE_NEW);
				try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task072supply','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
				final var setup = Player.create(PlayerTemplateData.getInstance().getTemplate(PlayerClass.FIGHTER), "task072supply", "Supply072", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
				final int level = Math.max(20, scene.farm().getLevel() + 2);
				check(setup != null && !setup.isGM(), "Own native setup actor absent."); setup.stopAllTasks(); setup.setPlayerClass(1); setup.setBaseClass(1); setup.getStat().setExp(setup.getStat().getExpForLevel(level)); setup.getStat().setLevel((byte) level); setup.giveAvailableAutoGetSkills();
				for (var item : setup.getInventory().getItems().stream().filter(value -> value.isEquipped()).toList()) { setup.getInventory().unEquipItemInSlot(item.getLocationSlot()); }
				for (int id : new int[] {129, 352, 2378}) { final var item = setup.getInventory().addItem(ItemProcessType.REWARD, id, 1, setup, PhantomSupply072JourneyNative.class); setup.getInventory().equipItem(item); }
				if (setup.getAdena() > 0) { check(setup.reduceAdena(ItemProcessType.DESTROY, setup.getAdena(), setup, false), "Initial fixture wallet cannot be zeroed."); }
				setup.setXYZ(scene.farm().getX(), scene.farm().getY(), GeoEngine.getInstance().getHeight(scene.farm().getX(), scene.farm().getY(), scene.farm().getZ())); setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.getInventory().updateDatabase(); setup.storeMe(); final int actorId = setup.getObjectId(); setup.deleteMe();
				final var profile = profiles.create(actorId); state.setProperty("profile", Long.toString(profile.profileId())); state.setProperty("actor", Integer.toString(actorId)); state.setProperty("farmNpc", Integer.toString(scene.farm().getId())); state.setProperty("merchant", Integer.toString(scene.merchant().getId()));
				report.append("SETUP_DECLARED\tWarrior").append(level).append(";native129 D sword+352+2378 armor FIXTURE;zero Adena/zero shots/no surplus/no buffs;not earned gear\n");
				writeState(stateFile, state);
			}
			else { check(Files.isRegularFile(stateFile), "Prepare checkpoint missing."); try (var reader = Files.newBufferedReader(stateFile)) { state.load(reader); } check(continuation ? !"true".equals(state.getProperty("completed")) : "true".equals(state.getProperty("completed")) && !"true".equals(state.getProperty("restart.completed")), "Continuation/restart must preserve its original finite checkpoint; no replay/refill."); }
			final long id = Long.parseLong(state.getProperty("profile")); final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			check(materialization.start() && materialization.materialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Native supply actor did not materialize.");
			final var player = World.getInstance().getPlayer(Integer.parseInt(state.getProperty("actor"))); check(player != null && !player.isAlikeDead(), "Retained native actor unavailable.");
			final var progressionPolicy = PhantomProgressionPolicy.productionDefaults(); final var progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), () -> query).load(progressionPolicy), progressionPolicy);
			combat = new PhantomCombatService(new L2jCombatBackend(materialization, () -> query, () -> progression), PhantomCombatCapabilityResolver.fromGameKnowledge(() -> query), PhantomCombatPolicy.productionDefaults(1)); combat.start();
			if (!prepare && !continuation)
			{
				check("task072supply".equals(player.getAccountName()) && "Supply072".equals(player.getName()), "Exact retained native actor required.");
				final var paid = new PhantomCommerceReceiptStore(profiles).find(id).orElseThrow().receipt();
				check(paid.state() == PhantomCommerceReceipt.State.COMMITTED && paid.operationKey().equals(state.getProperty("paid.operation")) && paid.request().itemId() == 1463, "Original paid shot receipt differs; no new purchase or refill permitted.");
				final var retainedIntent = new PhantomSupplyIntentStore(profiles, new PhantomGoalStateStore(profiles)).intent(id).orElseThrow().goal();
				check(retainedIntent.status() == PhantomGoalStatus.COMPLETED && retainedIntent.reasonKey().contains("returned"), "Original returned child is not retained.");
				check(Long.toString(player.getExp()).equals(state.getProperty("exp")) && Long.toString(player.getSp()).equals(state.getProperty("sp")) && Long.toString(player.getAdena()).equals(state.getProperty("adena")) && inventory(player).equals(state.getProperty("inventory")), "SameDB persisted native EXP/SP/Adena/item objects differ.");
				report.append("retained_paid_chain\t").append(paid).append(";actor=").append(player.getObjectId()).append(";level=").append(player.getLevel()).append(";HP=").append(player.getCurrentHp()).append(";MP=").append(player.getCurrentMp()).append(";A=").append(player.getAdena()).append(";shots=").append(player.getInventory().getInventoryItemCount(1463, -1)).append(";xyz=").append(player.getLocation()).append(";no Journey replay, grants, relocation or state reset\n");
				kill(combat, id, player, query, report);
				state.setProperty("restart.completed", "true"); writeState(stateFile, state); report.append("result\tPASS SAME_ACTOR_RESTART productive native rewarded kill; no purchase replay/recompute/refill\n"); return;
			}
			check(player.getLevel() >= 20 && player.getInventory().getInventoryItemCount(1463, -1) == 0 && player.getActiveWeaponItem().getCrystalTypePlus() == org.l2jmobius.gameserver.model.item.type.CrystalType.D, "Materialized measured actor must retain20+/zero matching shots/legal D gear.");
			final int priorKills = Integer.parseInt(state.getProperty("earned.kills", "3"));
			if (continuation) { check(player.getObjectId() == 268480990 && id == 1 && priorKills >= 3 && priorKills < 12 && player.getExp() == 835862L + priorKills * 633L && player.getSp() == priorKills * 28L && player.getAdena() >= 350, "Only exact retained072 earned checkpoint may continue; no reset or guessed budget."); }
			else { check(player.getAdena() == 0, "Fresh measurement wallet must be zero."); }
			report.append(continuation ? "MEASUREMENT_CONTINUED\t" : "MEASUREMENT_START\t").append("actor=").append(player.getObjectId()).append(";profile=").append(id).append(";level=").append(player.getLevel()).append(";xyz=").append(player.getLocation()).append(";EXP=").append(player.getExp()).append(";SP=").append(player.getSp()).append(";A=").append(player.getAdena()).append(";Dshots=0;HP=").append(player.getCurrentHp()).append(";inventory=").append(inventory(player)).append('\n');
			// Running is an ordinary native posture change, without a buff or coordinate/resource edit.
			try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { player.setRunning(); }
			// One finite recovery: use the actual tax, per-hit debit, speed and distance; do not demand a fabricated fixed wallet.
			for (int count = continuation ? priorKills : 0; count < 12 && (count < 2 || !affordableShots(player, catalog.catalog(), report)); count++) { kill(combat, id, player, query, report, materialization); state.setProperty("earned.kills", Integer.toString(count + 1)); writeState(stateFile, state); }
			check(player.getAdena() > 0, "PARTIAL_EARNINGS: five native kills did not produce any Adena; no money grant permitted.");
			combat.beginStop(); final long combatDrain = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); while (!combat.finishStop() && System.nanoTime() < combatDrain) { Thread.sleep(100); } combat = null;
			final var goals = new PhantomGoalStateStore(profiles);
			if (goals.load(id).isEmpty())
			{
				final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING).min(Comparator.comparingDouble(value -> player.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).orElseThrow();
				goals.insert(id, new PhantomGoal(72007203L, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", state.getProperty("farmNpc")), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, state.getProperty("farmNpc") + '@' + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture072.native_short_supply", 1));
			}
			final var root = goals.load(id).orElseThrow().goal(); final var receipts = new PhantomCommerceReceiptStore(profiles); final var store = new PhantomSupplyIntentStore(profiles, goals);
			navigation = new PhantomNavigationService(metrics); commerce = new PhantomCommerceService(catalog, receipts, store, new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone()));
			journey = new L2jPhantomSupplyJourney(store, goals, materialization, commerce, receipts, navigation, () -> query, PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology), _ -> true, (_, _) -> true, (_, _) -> false, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")));
			journey.useInventoryMaintenance(_ -> true); check(navigation.start() && commerce.start() && journey.start(), "Native supply owners did not start.");
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final Set<String> observed = new HashSet<>(); boolean sale = false, buy = false, returned = false; long boughtStock = -1;
			final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(200); String lastReason = "";
			do
			{
				final var planning = new PhantomPlanningContext(id, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1); final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
				if (selected != null) { final var plan = selected.planFactory().create(planning); handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(id, root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false)); }
				final String reason = journey.reason(id); if (!reason.equals(lastReason)) { report.append("journey\t").append(reason).append(";xyz=").append(player.getLocation()).append(";A=").append(player.getAdena()).append('\n'); System.out.println("SUPPLY072 " + reason + ";xyz=" + player.getLocation()); lastReason = reason; }
				final var receipt = receipts.find(id).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
				if (receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED && observed.add(receipt.operationKey()))
				{
					report.append("committed\t").append(receipt).append('\n'); sale |= receipt.request().kind() == PhantomCommerceReceipt.OperationKind.SELL;
					if (receipt.request().kind() == PhantomCommerceReceipt.OperationKind.BUY && receipt.request().itemId() == 1463) { check(receipt.before().primaryCount() - receipt.expectedAfter().primaryCount() == receipt.request().amount() && receipt.expectedAfter().secondaryCount() - receipt.before().secondaryCount() == receipt.request().count(), "Paid shot native conservation failed."); buy = true; boughtStock = player.getInventory().getInventoryItemCount(1463, -1); state.setProperty("paid.operation", receipt.operationKey()); }
				}
				final var intent = journey.automaticIntent(id).orElse(null);
				if (intent != null && intent.status() == PhantomGoalStatus.FAILED) { throw new AssertionError("Native journey failed: " + intent); }
				returned = intent != null && intent.status() == PhantomGoalStatus.COMPLETED; if (returned) { break; }
				Thread.sleep(200);
			} while (System.nanoTime() < until);
			{
				check(buy && returned, "Connected earned native paid D shot BUY/return did not complete within200s: " + lastReason);
				combat = new PhantomCombatService(new L2jCombatBackend(materialization, () -> query, () -> progression), PhantomCombatCapabilityResolver.fromGameKnowledge(() -> query), PhantomCombatPolicy.productionDefaults(1)); combat.start();
				final long exp = player.getExp(), sp = player.getSp();
				for (int kill = 0; kill < 2; kill++) { kill(combat, id, player, query, report, materialization); }
				check(player.getExp() > exp && player.getSp() > sp && player.getInventory().getInventoryItemCount(1463, -1) < boughtStock, "Post-return native kills did not earn EXP/SP and spend paid shots.");
				report.append("result\tPASS native earned-before-spent->paid1463->return->2 native rewarded kills with paid shot debit;no measurement relocation/grants;A=").append(player.getAdena()).append(";EXP=").append(exp).append("->").append(player.getExp()).append(";SP=").append(sp).append("->").append(player.getSp()).append('\n');
				state.setProperty("exp", Long.toString(player.getExp())); state.setProperty("sp", Long.toString(player.getSp())); state.setProperty("adena", Long.toString(player.getAdena())); state.setProperty("inventory", inventory(player));
				state.setProperty("completed", "true"); writeState(stateFile, state);
			}
		}
		catch (Throwable failure) { report.append("failure\t").append(failure).append('\n'); throw failure; }
		finally
		{
			try
			{
				if (combat != null) { combat.beginStop(); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); while (!combat.finishStop() && System.nanoTime() < until) { Thread.sleep(100); } }
				if (journey != null) { journey.beginStop(); check(journey.finishStop(), "Supply did not drain."); } if (commerce != null) { commerce.beginStop(); check(commerce.finishStop(), "Commerce did not drain."); }
				if (navigation != null) { navigation.beginStop(); final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); while (!navigation.finishStop() && System.nanoTime() < until) { Thread.sleep(100); } }
				if (materialization != null) { report.append("shutdown\t").append(materialization.shutdown()).append('\n'); }
			}
			finally { Files.createDirectories(reportFile.getParent()); Files.writeString(reportFile, report); try { ThreadPool.shutdown(); } finally { DatabaseFactory.close(); } }
		}
	}

	private static void cpPurchase(StringBuilder report, boolean retained) throws Exception
	{
		final var merchant = World.getInstance().getVisibleObjects().stream().filter(object -> object instanceof Merchant).map(object -> (Merchant) object).filter(npc -> npc.getId() == 30081 && npc.isSpawned() && !npc.isAlikeDead() && npc.getInstanceId() == 0).findFirst().orElseThrow(() -> new AssertionError("BLOCKED_INPUT: actual spawned Helvetia30081 absent; no fixture created."));
		final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load();
		for (int itemId : new int[] {5591, 5592}) { check(catalog.catalog().findBuyOffers(itemId, 0, 256).values().stream().anyMatch(offer -> offer.listId() == 3008100 && offer.npcIds().contains(30081) && !offer.limitedStock()), "Final ordinary CP source absent for " + itemId); }
		final var stand = cpSurface(report);
		final int actorId;
		if (retained) { actorId = 268480995; }
		else
		{
		try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task072cp2','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
		final var setup = Player.create(PlayerTemplateData.getInstance().getTemplate(PlayerClass.FIGHTER), "task072cp2", "Supply072Cp2", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		check(setup != null && !setup.isGM(), "New CP purchase fixture absent."); setup.stopAllTasks();
		if (setup.getAdena() > 0) { check(setup.reduceAdena(ItemProcessType.DESTROY, setup.getAdena(), setup, false), "Fixture wallet setup failed."); }
		setup.setPlayerClass(1); setup.setBaseClass(1); setup.getStat().setExp(setup.getStat().getExpForLevel(20)); setup.getStat().setLevel((byte) 20); setup.giveAvailableAutoGetSkills();
		for (var skill : org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getAllAvailableSkills(setup, setup.getPlayerClass(), true, true, true)) { setup.addSkill(skill, true); }
		for (var item : setup.getInventory().getItems().stream().filter(value -> value.isEquipped()).toList()) { setup.getInventory().unEquipItemInSlot(item.getLocationSlot()); }
		for (int itemId : new int[] {129, 352, 2378}) { setup.getInventory().equipItem(setup.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, setup, PhantomSupply072JourneyNative.class)); }
		setup.addItem(ItemProcessType.REWARD, 1463, 9, setup, false); setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.setCurrentCp(1);
		setup.addAdena(ItemProcessType.REWARD, 2000, setup, false); setup.setXYZ(stand.x(), stand.y(), stand.z()); setup.getInventory().updateDatabase(); setup.storeMe(); actorId = setup.getObjectId(); setup.deleteMe();
		}
		final var profiles = PhantomProfileRepository.open(); final long id = retained ? profiles.findByCharacterObjectId(actorId).orElseThrow().profileId() : profiles.create(actorId).profileId(); final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		PhantomCommerceService commerce = null;
		try
		{
			check(materialization.start() && materialization.materialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "CP fixture did not materialize.");
			final var player = World.getInstance().getPlayer(actorId); check(player != null && player.getAdena() == 2000 && player.getActiveClass() == 1, "CP fixture facts differ.");
			check(!retained || id == 2 && player.getAccountName().equals("task072cp") && player.getName().equals("Supply072Cp") && player.getInventory().getInventoryItemCount(1463, -1) == 9 && player.getInventory().getInventoryItemCount(5591, -1) == 0 && player.getInventory().getInventoryItemCount(5592, -1) == 0, "Exact unmeasured retained CP fixture required; no grants or reseed.");
			report.append("cp_geometry\tnpc=").append(merchant.getId()).append('/').append(merchant.getObjectId()).append(';').append(merchant.getLocation()).append(";actor=").append(player.getObjectId()).append(';').append(player.getLocation()).append(";dx=").append(player.getX() - merchant.getX()).append(";dy=").append(player.getY() - merchant.getY()).append(";dz=").append(player.getZ() - merchant.getZ()).append(";distance3D=").append(player.calculateDistance3D(merchant)).append(";LoS=").append(GeoEngine.getInstance().canSeeTarget(player, merchant)).append(";walkSurface=").append(stand).append(";nativeRadius=250\n");
			check(player.getInstanceId() == merchant.getInstanceId() && player.isInsideRadius3D(merchant, 250) && merchant.canInteract(player), "Final loaded CP scene is outside actual native interaction; no bypass.");
			report.append("cp_native_contract\tNpc.canInteract and L2jCommerceBackend.validateActor require same instance/native3D250/quiet ordinary actor; neither requires LoS; LoS=false stays recorded, not silently promoted to true;prior extra fixture guard was stricter than native source\n");
			final var goals = new PhantomGoalStateStore(profiles); final var receipts = new PhantomCommerceReceiptStore(profiles);
			final var commerceBackend = new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone());
			player.setTarget(merchant); player.setLastFolkNPC(merchant);
			try (var lease = commerceBackend.tryAcquire(id).orElseThrow())
			{
				check(lease != null, "Native CP negative quote actor unavailable.");
				for (var negative : List.of(
					new PhantomCommerceService.OperationIntent(PhantomCommerceReceipt.OperationKind.BUY, 30081, Integer.MAX_VALUE, 3008100, 5592, 0, 1, 0, "", 2000),
					new PhantomCommerceService.OperationIntent(PhantomCommerceReceipt.OperationKind.BUY, 30004, merchant.getObjectId(), 3008100, 5592, 0, 1, 0, "", 2000),
					new PhantomCommerceService.OperationIntent(PhantomCommerceReceipt.OperationKind.BUY, 30081, merchant.getObjectId(), 3008100, 5592, 0, 10, 0, "", 2000)))
				{
					final long beforeA = player.getAdena(), beforeItem = player.getInventory().getInventoryItemCount(5592, -1);
					final var refused = lease.quote(negative);
					check(!refused.accepted() && player.getAdena() == beforeA && player.getInventory().getInventoryItemCount(5592, -1) == beforeItem, "Native wrong object/NPC/insufficient wallet quote must refuse without effects.");
					report.append("cp_quote_negative\t").append(negative).append(";reason=").append(refused.reason()).append(";no physical delta\n");
				}
			}
			commerce = new PhantomCommerceService(catalog, receipts, goals, new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone())); check(commerce.start(), "CP commerce did not start.");
			report.append("cp_fixture\tPREMEASUREMENT disabled task072cp2/Supply072Cp2 Warrior20/stock learned skills/D129+352+2378/finite9Dshots/CP1;profile=").append(id).append(";actor=").append(actorId).append(";nativeNpc=30081;object=").append(merchant.getObjectId()).append(";xyz=").append(player.getLocation()).append(";finite 2000 Adena TEST fixture, NOT earned income; no later grants or relocation;earlier cp1 premeasurement LoS FAIL preserved\n");
			org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStore.StoredGoal stored = null;
			for (int itemId : new int[] {5591, 5592})
			{
				final int purchasedCount = itemId == 5591 ? 2 : 1;
				final long goalId = 720072100L + itemId; final var source = new PhantomDomainRef("commerce.buy", "30081:" + merchant.getObjectId() + ":3008100:" + itemId + ':' + purchasedCount);
				final var goal = new PhantomGoal(goalId, "acquire.item", PhantomGoalStatus.ACTIVE, null, null, 5, 0, null, List.of(source), null, "commerce.test", 500, 0, 100, 0, Map.of(), "cp072.fixture", 0);
				stored = stored == null ? goals.insert(id, goal) : goals.replace(id, stored.rowVersion(), goal);
				player.setTarget(merchant); player.setLastFolkNPC(merchant);
				final long adena = player.getAdena(), count = player.getInventory().getInventoryItemCount(itemId, -1);
				final var intent = new PhantomCommerceService.OperationIntent(PhantomCommerceReceipt.OperationKind.BUY, 30081, merchant.getObjectId(), 3008100, itemId, 0, purchasedCount, 0, "", adena);
				final var result = commerce.execute(id, goalId, 0, intent, () -> false); report.append("cp_operation\titem=").append(itemId).append(';').append(result).append('\n');
				check(result.status() == PhantomCommerceService.OperationStatus.SUCCESS, "Paid CP purchase failed: " + result);
				final var receipt = receipts.find(id).orElseThrow().receipt(); report.append("cp_committed\t").append(receipt).append(";actualA=").append(adena).append("->").append(player.getAdena()).append(";actualItem=").append(count).append("->").append(player.getInventory().getInventoryItemCount(itemId, -1)).append('\n');
				check(receipt.state() == PhantomCommerceReceipt.State.COMMITTED && receipt.request().amount() > 0 && receipt.request().itemId() == itemId && receipt.request().count() == purchasedCount && player.getAdena() == adena - receipt.request().amount() && receipt.expectedAfter().primaryCount() == player.getAdena() && player.getInventory().getInventoryItemCount(itemId, -1) == count + purchasedCount && receipt.expectedAfter().secondaryCount() == count + purchasedCount, "Paid CP native conservation failed.");
			}
			final var cpBackend = new L2jCombatBackend(materialization, () -> null);
			for (int itemId : new int[] {5591})
			{
				try (var lease = cpBackend.tryAcquireActor(id))
				{
					check(lease != null, "Paid CP native action lease missing.");
					final var potion = lease.cpPotions().stream().filter(item -> item.itemId() == itemId).findFirst().orElseThrow();
					final var used = lease.useCpPotion(potion.itemObjectId(), itemId); final var reuse = lease.useCpPotion(potion.itemObjectId(), itemId);
					final long effectUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(1); while (player.getCurrentCp() <= used.cpBefore() && System.nanoTime() < effectUntil) { Thread.sleep(25); }
					check(used.countAfter() == used.countBefore() - 1 && player.getCurrentCp() > used.cpBefore() && reuse.countAfter() == used.countAfter() && reuse.outcome() == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionOutcome.REUSE, "Paid CP handler effect/debit/reuse failed.");
					report.append("cp_paid_use\t").append(used).append(";reuse=").append(reuse).append('\n');
				}
				Thread.sleep(600);
			}
			player.setInOlympiadMode(true);
			try (var lease = cpBackend.tryAcquireActor(id))
			{
				for (var potion : lease.cpPotions()) { final var rejected = lease.useCpPotion(potion.itemObjectId(), potion.itemId()); check(rejected.countBefore() == rejected.countAfter() && rejected.outcome() == org.l2jmobius.gameserver.phantoms.combat.PhantomCombatBackend.CpPotionOutcome.REJECTED, "Olympiad CP negative failed."); report.append("cp_olympiad_negative\t").append(rejected).append('\n'); }
			}
			finally { player.setInOlympiadMode(false); }
			final var cpState = new Properties(); cpState.setProperty("actor", Integer.toString(actorId)); cpState.setProperty("profile", Long.toString(id)); cpState.setProperty("paid", "true");
			try (var writer = Files.newBufferedWriter(Path.of(System.getProperty("phantom.module.root")).getParent().resolve(".phantom-local/task072/cp072-native.properties"), java.nio.file.StandardOpenOption.CREATE_NEW)) { cpState.store(writer, "Own072 paid CP actor; no refill or replacement"); }
			report.append("result\tPASS paid ordinary native30081/list3008100 BUY5591x2 +5592x1 with durable COMMITTED receipts and native small CP use/reuse/both Olympiad guards;paid5592 retained for native PvP;fixture funding explicit; no earned-income claim\n");
		}
		finally { if (commerce != null) { commerce.beginStop(); check(commerce.finishStop(), "CP commerce did not drain."); } report.append("cp_shutdown\t").append(materialization.shutdown()).append('\n'); }
	}

	private static Scene selectScene(PhantomCommerceCatalog catalog, StringBuilder report, PhantomGameKnowledgeQuery query)
	{
		final var geo = GeoEngine.getInstance(); final var objects = World.getInstance().getVisibleObjects();
		final var nativePaths = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
		final var navigationPolicy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
		final var mobs = objects.stream().filter(object -> object instanceof Monster).map(object -> (Monster) object)
			.filter(mob -> mob.isSpawned() && !mob.isAlikeDead() && !mob.isRaid() && mob.getInstanceId() == 0 && mob.getLevel() >= 18 && mob.getLevel() <= 35 && !mob.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && geo.hasGeo(mob.getX(), mob.getY()))
			.filter(mob -> query.findNpc(mob.getId()).map(fact -> fact.exp() > 0 && fact.sp() > 0).orElse(false)).toList();
		final var choices = new ArrayList<Scene>();
		int merchants = 0, nearPairs = 0, crowdedPairs = 0, surfaces = 0, routeChecks = 0;
		for (var offer : catalog.findBuyOffers(1463, 0, 256).values())
		{
			if (offer.limitedStock() || offer.price() <= 0) { continue; }
			for (var merchant : objects.stream().filter(object -> object instanceof Merchant).map(object -> (Merchant) object).filter(npc -> offer.npcIds().contains(npc.getId()) && npc.isSpawned() && !npc.isAlikeDead() && npc.getInstanceId() == 0 && npc.getMpc() != null && npc.getMpc().getCastleTaxRate() == 0).toList())
			{
				merchants++;
				for (var mob : mobs.stream().filter(value -> value.calculateDistance3D(merchant) >= 250 && value.calculateDistance3D(merchant) <= 5000).sorted(Comparator.comparingDouble(value -> value.calculateDistance3D(merchant))).limit(8).toList())
				{
					nearPairs++;
					if (mobs.stream().filter(other -> other.calculateDistance3D(mob) <= 700).limit(3).count() < 3) { continue; }
					crowdedPairs++;
					choices.add(new Scene(merchant, mob)); break;
				}
			}
		}
		final long searchEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		for (var scene : choices.stream().distinct().sorted(Comparator.comparingDouble((Scene value) -> value.farm().calculateDistance3D(value.merchant())).thenComparingInt(value -> value.merchant().getId()).thenComparingInt(value -> value.farm().getId())).limit(3).toList())
		{
			if (System.nanoTime() >= searchEnd) { break; }
			final var merchant = scene.merchant(); final var mob = scene.farm();
			final var origin = nativePaths.walkSurface(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(mob.getX(), mob.getY(), mob.getZ(), 0), 256).orElse(null);
			if (origin == null) { continue; }
			// Same authored interaction radii and arrival margin as the production SupplyJourney.
			for (int radius : new int[] {0, 64, 112})
			{
				final double angle = Math.atan2((long) mob.getY() - merchant.getY(), (long) mob.getX() - merchant.getX());
				final int x = merchant.getX() + (int) Math.round(radius * Math.cos(angle)), y = merchant.getY() + (int) Math.round(radius * Math.sin(angle));
				final var target = nativePaths.walkSurface(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, merchant.getZ(), 0), 256).orElse(null);
				if (target == null || merchant.calculateDistance3D(target.x(), target.y(), target.z()) > 184) { continue; }
				surfaces++; routeChecks++;
				final long now = System.nanoTime();
				final var outbound = nativePaths.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(1, origin, target, now, Math.min(searchEnd, now + TimeUnit.SECONDS.toNanos(1)), 10_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), navigationPolicy, System::nanoTime);
				if (outbound.status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND) { report.append("candidate_route\t").append(merchant.getId()).append(';').append(mob.getId()).append(';').append(outbound.status()).append(';').append(origin).append("->").append(target).append('\n'); continue; }
				final long backNow = System.nanoTime();
				final var inbound = nativePaths.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(1, target, origin, backNow, Math.min(searchEnd, backNow + TimeUnit.SECONDS.toNanos(1)), 10_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), navigationPolicy, System::nanoTime);
				if (inbound.status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND) { continue; }
				report.append("preselected\tBEFORE_MEASUREMENT actual native shop=").append(merchant.getId()).append('/').append(merchant.getObjectId()).append(';').append(merchant.getLocation())
					.append(";farm=").append(mob.getId()).append(';').append(mob.getLocation()).append(";distance=").append(mob.calculateDistance3D(merchant)).append(";nativeCorridorBothWays=true;outboundPoints=").append(outbound.points().size()).append(";inboundPoints=").append(inbound.points().size()).append('\n');
				report.append("candidate_counts\tmobs=").append(mobs.size()).append(";merchants=").append(merchants).append(";nearPairs=").append(nearPairs).append(";crowdedPairs=").append(crowdedPairs).append(";surfaces=").append(surfaces).append(";routeChecks=").append(routeChecks).append('\n');
				report.append("PREFLIGHT_ACCEPTED\tactorLevel=").append(Math.max(20, mob.getLevel() + 2)).append(";class=Warrior;weapon=129 D fixture;shot=1463;mobLevel=").append(mob.getLevel()).append(";reward=").append(query.findNpc(mob.getId()).orElseThrow().exp()).append('/').append(query.findNpc(mob.getId()).orElseThrow().sp()).append(";gap=2_or_less;limit=").append(org.l2jmobius.gameserver.config.RatesConfig.MONSTER_EXP_MAX_LEVEL_DIFFERENCE).append(";merchantXYZ=").append(merchant.getLocation()).append(";instance=").append(merchant.getInstanceId()).append(";stand=").append(target).append(";quote=").append(catalog.findBuyOffers(1463, 0, 256).values().stream().filter(offer -> offer.npcIds().contains(merchant.getId())).toList()).append('\n');
				return scene;
			}
		}
		report.append("candidate_counts\tmobs=").append(mobs.size()).append(";merchants=").append(merchants).append(";nearPairs=").append(nearPairs).append(";crowdedPairs=").append(crowdedPairs).append(";surfaces=").append(surfaces).append(";routeChecks=").append(routeChecks).append('\n');
		throw new AssertionError("BLOCKED_C3: no bounded native shop1463/farm corridor proven in both directions; inspect candidate_counts; no actor or account created.");
	}

	private static org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint cpSurface(StringBuilder report)
	{
		final var npc = World.getInstance().getVisibleObjects().stream().filter(object -> object instanceof Merchant).map(object -> (Merchant) object).filter(value -> value.getId() == 30081 && value.isSpawned() && value.getInstanceId() == 0).findFirst().orElseThrow();
		final var paths = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
		int samples = 0, stable = 0;
		for (int radius : new int[] {16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240})
		{
			for (int direction = 0; direction < 32; direction++)
			{
				final double angle = direction * Math.PI / 16;
				final int x = npc.getX() + (int) Math.round(radius * Math.cos(angle)), y = npc.getY() + (int) Math.round(radius * Math.sin(angle));
				final var point = paths.walkSurface(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, npc.getZ(), 0), 256).orElse(null);
				final int loadedZ = point == null ? Integer.MAX_VALUE : GeoEngine.getInstance().getHeight(x, y, point.z());
				final boolean visible = point != null && point.z() == loadedZ && GeoEngine.getInstance().canSeeTarget(x, y, loadedZ, 0, npc.getX(), npc.getY(), npc.getZ(), npc.getInstanceId()); samples++; if (point != null && point.z() == loadedZ) { stable++; }
				if (samples <= 16 || visible) { report.append("cp_surface\tnpc=").append(npc.getObjectId()).append(';').append(npc.getLocation()).append(";probe=").append(point).append(";loadZ=").append(loadedZ).append(";distance=").append(npc.calculateDistance3D(x, y, loadedZ)).append(";LoS=").append(visible).append('\n'); }
				if (point != null && point.z() == loadedZ && npc.calculateDistance3D(x, y, loadedZ) <= 244) { report.append("cp_surface_counts\tsamples=").append(samples).append(";stable=").append(stable).append(";load/3D accepted before fixture;LoS=").append(visible).append(";native canInteract has no LoS guard\n"); return point; }
			}
		}
		report.append("cp_surface_counts\tsamples=").append(samples).append(";stable=").append(stable).append(";no stable load+3D+LoS combination\n");
		throw new AssertionError("BLOCKED_INPUT: no stable native load+LoS surface within Helvetia 3D radius250; no new CP actor created.");
	}

	private static void kill(PhantomCombatService combat, long profileId, Player player, PhantomGameKnowledgeQuery knowledge, StringBuilder report) throws Exception
	{
		kill(combat, profileId, player, knowledge, report, null);
	}

	private static void kill(PhantomCombatService combat, long profileId, Player player, PhantomGameKnowledgeQuery knowledge, StringBuilder report, PhantomMaterializationService materialization) throws Exception
	{
		final var targets = World.getInstance().getVisibleObjectsInRange(player, Monster.class, materialization == null ? 700 : 5000).stream()
			.filter(mob -> mob.isSpawned() && !mob.isAlikeDead() && !mob.isRaid() && mob.getInstanceId() == player.getInstanceId() && !mob.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE))
			.filter(mob -> knowledge.findNpc(mob.getId()).map(fact -> fact.kind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind.MONSTER && fact.level() >= player.getLevel() - 5 && fact.level() <= player.getLevel() + 2 && fact.exp() > 0 && fact.sp() > 0).orElse(false))
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

	private record Scene(Merchant merchant, Monster farm) { }
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
	private static String inventory(Player player) { return player.getInventory().getItems().stream().filter(item -> item.getCount() > 0).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.isEquipped()).collect(java.util.stream.Collectors.joining(";")); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
	private static void writeState(Path file, Properties state) throws Exception { try (var writer = Files.newBufferedWriter(file)) { state.store(writer, "Own072 finite native fixture; never refill or reset"); } }
}

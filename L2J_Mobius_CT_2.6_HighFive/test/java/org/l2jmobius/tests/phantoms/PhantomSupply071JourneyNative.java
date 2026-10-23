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

/** Own071 two-JVM supply continuation. All setup is before measurement; fixture liquidation is distinguished from earned loot. */
public final class PhantomSupply071JourneyNative
{
	public static void main(String[] args) throws Exception
	{
		if (!PhantomTask071Lane.enabled() || args.length != 1 || !Set.of("prepare", "resume").contains(args[0])) { throw new IllegalArgumentException("Own071 prepare or resume required."); }
		final boolean prepare = args[0].equals("prepare");
		final Path module = Path.of(System.getProperty("phantom.module.root")).toRealPath(), task = module.getParent().resolve(".phantom-local/task071");
		final Path stateFile = task.resolve("supply071-native.properties"), reportFile = task.resolve("reports/supply071-" + args[0] + '-' + System.currentTimeMillis() + ".tsv");
		final var report = new StringBuilder("stage\tevidence\n"); final var state = new Properties();
		PhantomMaterializationService materialization = null; PhantomNavigationService navigation = null; PhantomCommerceService commerce = null; L2jPhantomSupplyJourney journey = null; PhantomCombatService combat = null;
		try
		{
			PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(71007103L, module, task.resolve("reports")));
			InitialEquipmentData.getInstance(); InitialShortcutData.getInstance(); MapRegionData.getInstance(); ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
			MerchantPriceConfigTable.getInstance().loadInstances(); SpawnData.getInstance(); MerchantPriceConfigTable.getInstance().updateReferences();
			final var topologyBackend = new L2jTopologyValidationBackend(); final var topology = new PhantomTopologyQuery(new PhantomTopologyLoader(Path.of("data/phantoms/topology"), topologyBackend, PhantomTopologyPolicy.productionDefaults()).load(1), topologyBackend, new PhantomTopologyMetrics());
			final var knowledgePolicy = PhantomGameKnowledgePolicy.productionDefaults(); final var backend = new L2jGameKnowledgeBackend();
			final var knowledge = new PhantomGameKnowledgeService(new PhantomGameKnowledgeBuilder(backend, new PhantomStaticManorParser(Path.of("data/Seeds.xml"), knowledgePolicy), new PhantomCuratedKnowledgeParser(Path.of("data/phantoms/knowledge"), backend, knowledgePolicy), topology, knowledgePolicy));
			check(knowledge.start(), "Native knowledge did not start."); final var query = knowledge.query();
			final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load(); final var profiles = PhantomProfileRepository.open();
			if (prepare)
			{
				check(!Files.exists(stateFile), "Own071 supply fixture already exists; use resume, never refill.");
				final var scene = selectScene(catalog.catalog(), report);
				try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task071supply','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.executeUpdate(); }
				final var setup = Player.create(PlayerTemplateData.getInstance().getTemplate(PlayerClass.FIGHTER), "task071supply", "Supply071", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
				check(setup != null && !setup.isGM(), "Own native setup actor absent."); setup.stopAllTasks(); setup.getStat().setExp(setup.getStat().getExpForLevel(19)); setup.getStat().setLevel((byte) 19); setup.giveAvailableAutoGetSkills();
				for (var item : setup.getInventory().getItems().stream().filter(value -> value.isEquipped()).toList()) { setup.getInventory().unEquipItemInSlot(item.getLocationSlot()); }
				for (int id : new int[] {68, 24, 31}) { final var item = setup.getInventory().addItem(ItemProcessType.REWARD, id, 1, setup, PhantomSupply071JourneyNative.class); setup.getInventory().equipItem(item); }
				if (setup.getAdena() > 0) { check(setup.reduceAdena(ItemProcessType.DESTROY, setup.getAdena(), setup, false), "Initial fixture wallet cannot be zeroed."); }
				final var surplus = java.util.Arrays.stream(ItemData.getInstance().getAllItems()).filter(java.util.Objects::nonNull).filter(item -> item.getId() != 68 && item.getCrystalType().getLevel() == 0 && item.isWeapon() && item.getReferencePrice() >= 12_000 && item.getReferencePrice() <= 60_000 && PhantomLootDispositionPolicy.getInstance().category(item) == PhantomLootDispositionPolicy.Disposition.SELL_NPC && !org.l2jmobius.gameserver.phantoms.progression.PhantomNativeEquipmentPolicy.upgrade(setup, item)).min(Comparator.comparingInt(item -> item.getReferencePrice())).orElseThrow();
				final var saleItem = setup.getInventory().addItem(ItemProcessType.REWARD, surplus.getId(), 1, setup, PhantomSupply071JourneyNative.class);
				check(PhantomLootDispositionPolicy.getInstance().evaluate(setup, saleItem, Set.of(), false).disposition() == PhantomLootDispositionPolicy.Disposition.SELL_NPC, "Declared surplus is protected by normal disposition.");
				setup.setXYZ(scene.farm().getX(), scene.farm().getY(), GeoEngine.getInstance().getHeight(scene.farm().getX(), scene.farm().getY(), scene.farm().getZ())); setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.getInventory().updateDatabase(); setup.storeMe(); final int actorId = setup.getObjectId(); setup.deleteMe();
				final var profile = profiles.create(actorId); state.setProperty("profile", Long.toString(profile.profileId())); state.setProperty("actor", Integer.toString(actorId)); state.setProperty("farmNpc", Integer.toString(scene.farm().getId())); state.setProperty("merchant", Integer.toString(scene.merchant().getId())); state.setProperty("oldMerchantObject", Integer.toString(scene.merchant().getObjectId())); state.setProperty("surplus", Integer.toString(surplus.getId()));
				report.append("fixture\tPREMEASUREMENT Fighter19/native68+24+31; surplus=").append(surplus.getId()).append("x1;zero Adena/zero shot grants; surplus is explicit finite TEST input, NOT earned loot; physical liquidation can fund native purchase; post-return kills must earn real EXP/SP\n");
				writeState(stateFile, state);
			}
			else { check(Files.isRegularFile(stateFile), "Prepare checkpoint missing."); try (var reader = Files.newBufferedReader(stateFile)) { state.load(reader); } check(!"true".equals(state.getProperty("completed")), "Completed own071 chain is immutable; no new sale or refill."); }
			final long id = Long.parseLong(state.getProperty("profile")); final var metrics = new PhantomMetrics();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			check(materialization.start() && materialization.materialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Native supply actor did not materialize.");
			final var player = World.getInstance().getPlayer(Integer.parseInt(state.getProperty("actor"))); check(player != null && !player.isAlikeDead(), "Retained native actor unavailable.");
			final var goals = new PhantomGoalStateStore(profiles);
			if (prepare)
			{
				final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING).min(Comparator.comparingDouble(value -> player.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).orElseThrow();
				goals.insert(id, new PhantomGoal(71007103L, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", state.getProperty("farmNpc")), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, state.getProperty("farmNpc") + '@' + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture071.native_short_supply", 1));
			}
			final var root = goals.load(id).orElseThrow().goal(); final var receipts = new PhantomCommerceReceiptStore(profiles); final var store = new PhantomSupplyIntentStore(profiles, goals);
			navigation = new PhantomNavigationService(metrics); commerce = new PhantomCommerceService(catalog, receipts, store, new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone()));
			journey = new L2jPhantomSupplyJourney(store, goals, materialization, commerce, receipts, navigation, () -> query, PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology), _ -> true, (_, _) -> true, (_, _) -> false, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")));
			journey.useInventoryMaintenance(_ -> true); check(navigation.start() && commerce.start() && journey.start(), "Native supply owners did not start.");
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final Set<String> observed = new HashSet<>(); boolean sale = false, buy = false, returned = false; long boughtStock = -1;
			final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(prepare ? 5 : 200); String lastReason = "";
			do
			{
				final var planning = new PhantomPlanningContext(id, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1); final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
				if (selected != null) { final var plan = selected.planFactory().create(planning); handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(id, root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false)); }
				final String reason = journey.reason(id); if (!reason.equals(lastReason)) { report.append("journey\t").append(reason).append(";xyz=").append(player.getLocation()).append(";A=").append(player.getAdena()).append('\n'); System.out.println("SUPPLY071 " + reason + ";xyz=" + player.getLocation()); lastReason = reason; }
				final var receipt = receipts.find(id).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
				if (receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED && observed.add(receipt.operationKey()))
				{
					report.append("committed\t").append(receipt).append('\n'); sale |= receipt.request().kind() == PhantomCommerceReceipt.OperationKind.SELL;
					if (receipt.request().kind() == PhantomCommerceReceipt.OperationKind.BUY && receipt.request().itemId() == 1835) { check(sale && receipt.before().primaryCount() - receipt.expectedAfter().primaryCount() == receipt.request().amount() && receipt.expectedAfter().secondaryCount() - receipt.before().secondaryCount() == receipt.request().count(), "Paid shot receipt is not connected to observed sale or fails conservation."); buy = true; boughtStock = player.getInventory().getInventoryItemCount(1835, -1); }
				}
				final var intent = journey.automaticIntent(id).orElse(null);
				if (prepare && intent != null) { check(receipt == null, "Prepare unexpectedly committed a counter effect."); state.setProperty("prepared", "true"); writeState(stateFile, state); report.append("checkpoint\t").append(intent).append('\n'); break; }
				if (intent != null && intent.status() == PhantomGoalStatus.FAILED) { throw new AssertionError("Native journey failed: " + intent); }
				returned = intent != null && intent.status() == PhantomGoalStatus.COMPLETED; if (returned) { break; }
				Thread.sleep(200);
			} while (System.nanoTime() < until);
			if (prepare) { check("true".equals(state.getProperty("prepared")), "Native preparation never accepted a supply child."); report.append("result\tPREPARED retained actor/root/child, zero commerce effects; next JVM resumes without relocation or grants\n"); }
			else
			{
				check(sale && buy && returned, "Connected native SELL/paid shot BUY/return did not complete within200s: " + lastReason);
				final var progressionPolicy = PhantomProgressionPolicy.productionDefaults(); final var progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), () -> query).load(progressionPolicy), progressionPolicy);
				combat = new PhantomCombatService(new L2jCombatBackend(materialization, () -> query, () -> progression), PhantomCombatCapabilityResolver.fromGameKnowledge(() -> query), PhantomCombatPolicy.productionDefaults(1)); combat.start();
				final long exp = player.getExp(), sp = player.getSp();
				for (int kill = 0; kill < 3; kill++) { kill(combat, id, player, query, report); }
				check(player.getExp() > exp && player.getSp() > sp && player.getInventory().getInventoryItemCount(1835, -1) < boughtStock, "Post-return native kills did not earn EXP/SP and spend paid shots.");
				report.append("result\tPASS connected finite-fixture liquidation SELL->paid shot BUY->native return->3 native rewarded kills; no measurement relocation/grants; NOT earned-before-sale loot proof;A=").append(player.getAdena()).append(";EXP=").append(exp).append("->").append(player.getExp()).append(";SP=").append(sp).append("->").append(player.getSp()).append('\n');
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

	private static Scene selectScene(PhantomCommerceCatalog catalog, StringBuilder report)
	{
		final var geo = GeoEngine.getInstance(); final var objects = World.getInstance().getVisibleObjects();
		final var nativePaths = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
		final var navigationPolicy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
		final var mobs = objects.stream().filter(object -> object instanceof Monster).map(object -> (Monster) object)
			.filter(mob -> mob.isSpawned() && !mob.isAlikeDead() && !mob.isRaid() && mob.getInstanceId() == 0 && mob.getLevel() >= 2 && mob.getLevel() <= 8 && !mob.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) && geo.hasGeo(mob.getX(), mob.getY())).toList();
		final var choices = new ArrayList<Scene>();
		int merchants = 0, nearPairs = 0, crowdedPairs = 0, surfaces = 0, routeChecks = 0;
		for (var offer : catalog.findBuyOffers(1835, 0, 256).values())
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
		for (var scene : choices.stream().distinct().sorted(Comparator.comparingDouble((Scene value) -> value.farm().calculateDistance3D(value.merchant())).thenComparingInt(value -> value.merchant().getId()).thenComparingInt(value -> value.farm().getId())).limit(12).toList())
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
				return scene;
			}
		}
		report.append("candidate_counts\tmobs=").append(mobs.size()).append(";merchants=").append(merchants).append(";nearPairs=").append(nearPairs).append(";crowdedPairs=").append(crowdedPairs).append(";surfaces=").append(surfaces).append(";routeChecks=").append(routeChecks).append('\n');
		throw new AssertionError("BLOCKED_C3: no bounded native shop1835/farm corridor proven in both directions; inspect candidate_counts; no actor or account created.");
	}

	private static void kill(PhantomCombatService combat, long profileId, Player player, PhantomGameKnowledgeQuery knowledge, StringBuilder report) throws Exception
	{
		final var target = World.getInstance().getVisibleObjectsInRange(player, Monster.class, 700).stream()
			.filter(mob -> mob.isSpawned() && !mob.isAlikeDead() && !mob.isRaid() && mob.getInstanceId() == player.getInstanceId() && !mob.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE))
			.filter(mob -> knowledge.findNpc(mob.getId()).map(fact -> fact.kind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.NpcKind.MONSTER && fact.level() >= 2 && fact.level() <= 8 && fact.exp() > 0 && fact.sp() > 0).orElse(false))
			.filter(mob -> GeoEngine.getInstance().canSeeTarget(player, mob)).min(Comparator.comparingDouble(mob -> player.calculateDistance3D(mob))).orElseThrow(() -> new AssertionError("No remaining normal native low-level target after return."));
		final long beforeExp = player.getExp(), beforeSp = player.getSp(), beforeShots = player.getInventory().getInventoryItemCount(1835, -1);
		final var start = combat.startSession(new PhantomCombatRequest(profileId, target.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL, true, true, 30_000, () -> false));
		check(start.accepted(), "Native rewarded combat refused: " + start);
		final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
		while (System.nanoTime() < until && !combat.find(profileId).map(value -> value.result().terminal()).orElse(false)) { Thread.sleep(100); }
		final var finished = combat.find(profileId).orElseThrow();
		check(finished.result().victory() && target.isDead() && !player.isDead() && player.getExp() > beforeExp && player.getSp() > beforeSp, "Native rewarded kill failed: " + finished);
		boolean consumed = false; final long cleanupUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!consumed && System.nanoTime() < cleanupUntil) { consumed = combat.consumeTerminal(profileId).isPresent(); if (!consumed) { Thread.sleep(100); } }
		check(consumed, "Native victory ownership did not release.");
		report.append("earned_kill\ttarget=").append(target.getId()).append('/').append(target.getObjectId()).append(";EXP=").append(beforeExp).append("->").append(player.getExp()).append(";SP=").append(beforeSp).append("->").append(player.getSp()).append(";paidShots=").append(beforeShots).append("->").append(player.getInventory().getInventoryItemCount(1835, -1)).append(";session=").append(finished).append('\n');
	}

	private record Scene(Merchant merchant, Monster farm) { }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
	private static void writeState(Path file, Properties state) throws Exception { try (var writer = Files.newBufferedWriter(file)) { state.store(writer, "Own071 finite native fixture; never refill or reset"); } }
}

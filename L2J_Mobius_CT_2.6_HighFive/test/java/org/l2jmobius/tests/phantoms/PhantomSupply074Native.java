/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
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
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.combat.*;
import org.l2jmobius.gameserver.phantoms.commerce.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.*;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** One retained finite prior-savings fixture, real rewards and native commerce; no resource writes after marker. */
final class PhantomSupply074Native
{
	static void run(PhantomGameKnowledgeQuery query, PhantomTopologyQuery topology, StringBuilder report, String action) throws Exception
	{
		final boolean resume = action.equals("resume");
		final boolean selection = action.equals("selection");
		final String account = selection ? "task074supply" : "task074chain";
		final var module = Path.of(System.getProperty("phantom.module.root")); final var stateFile = PhantomTask074Lane.task(module).resolve(selection ? "supply074.properties" : "supply074-acceptance.properties");
		final var state = new Properties(); final var profiles = PhantomProfileRepository.open(); final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load();
		final var paths = new L2jNavigationBackend(); final var policy = PhantomNavigationPolicy.productionDefaults();
		if (!Files.exists(stateFile))
		{
			check(!resume, "OWN074 retained actor absent; no reseed on resume.");
			final var merchant = World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof Merchant npc && npc.getId() == 30420 && npc.isSpawned()).map(value -> (Merchant) value).findFirst().orElseThrow();
			final var target = World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof Monster mob && mob.getId() == 20038 && mob.isSpawned() && !mob.isAlikeDead()).map(value -> (Monster) value).min(Comparator.comparingDouble(value -> value.calculateDistance3D(merchant))).orElseThrow();
			final var field = paths.walkSurface(new PhantomNavigationPoint(target.getX(), target.getY(), target.getZ(), 0), 256).orElseThrow();
			final var stand = paths.walkSurface(new PhantomNavigationPoint(merchant.getX(), merchant.getY(), merchant.getZ(), 0), 256).orElseThrow();
			for (var pair : List.of(List.of(field, stand), List.of(stand, field)))
			{
				final long now = System.nanoTime(); final var route = paths.findWalkCorridor(new PhantomNavigationRequest(740074, pair.get(0), pair.get(1), now, now + policy.defaultRequestDeadlineNanos(), policy.maximumRouteDistance()), new PhantomNavigationCancellationToken(), policy, System::nanoTime);
				report.append("A_PREFLIGHT\t").append(pair).append(";status=").append(route.status()).append(";distance=").append(PhantomTravelStrategy.distance(pair.get(0), route.points())).append('\n'); check(route.status() == PhantomWalkCorridorPlanner.Status.FOUND, "OWN074 native both-way scene refused before actor creation.");
			}
			final var supplyPolicy = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
			final var offer = catalog.catalog().buyOffers().stream().filter(value -> value.itemId() == 1463 && value.npcIds().contains(merchant.getId())).findFirst().orElseThrow();
			final long unit = (long) (offer.price() * (1 + merchant.getMpc().getTotalTaxRate()));
			report.append("A_DECLARED_FIXTURE\tpriorSavings=4000A;obsoleteNGsword1;Warrior20 weapon129/352/2378;zero1463;not earned in074;unit=").append(unit).append(";perHit=3;budget=").append(supplyPolicy.availableBudget(4000, 0, 0, true)).append(";policy=").append(supplyPolicy).append('\n');
			check(!selection, "OWN074 selection control requires the original retained actor.");
			try (var connection = DatabaseFactory.getConnection(); var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES(?,'DISABLED_NATIVE_TEST_ONLY',-1,1)")) { insert.setString(1, account); insert.executeUpdate(); }
			final var setup = Player.create(PlayerTemplateData.getInstance().getTemplate(PlayerClass.FIGHTER), account, "Supply074Chain", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
			check(setup != null && !setup.isGM(), "OWN074 ordinary fixture not created."); setup.stopAllTasks(); setup.setPlayerClass(1); setup.setBaseClass(1); setup.getStat().setExp(setup.getStat().getExpForLevel(20)); setup.getStat().setLevel((byte) 20); setup.giveAvailableAutoGetSkills();
			for (var item : setup.getInventory().getItems().stream().filter(value -> value.isEquipped()).toList()) { setup.getInventory().unEquipItemInSlot(item.getLocationSlot()); }
			for (int itemId : new int[] {129, 352, 2378}) { final var item = setup.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, setup, PhantomSupply074Native.class); setup.getInventory().equipItem(item); }
			setup.getInventory().addItem(ItemProcessType.REWARD, 1, 1, setup, PhantomSupply074Native.class); setup.addAdena(ItemProcessType.REWARD, 4000, setup, false);
			setup.setXYZ(field.x(), field.y(), field.z()); setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.getInventory().updateDatabase(); setup.storeMe(); final int actor = setup.getObjectId(); setup.deleteMe();
			state.setProperty("actor", Integer.toString(actor)); state.setProperty("profile", Long.toString(profiles.create(actor).profileId())); state.setProperty("phase", "born"); save(stateFile, state);
		}
		else { try (var reader = Files.newBufferedReader(stateFile)) { state.load(reader); } }
		final long id = Long.parseLong(state.getProperty("profile")); final var metrics = new PhantomMetrics();
		final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		PhantomCombatService combat = null; PhantomNavigationService navigation = null; PhantomCommerceService commerce = null; L2jPhantomSupplyJourney journey = null;
		final var cleanup = new java.util.ArrayList<AutoCloseable>();
		cleanup.add(() -> { final var stopped = materialization.shutdown(); report.append("A_SHUTDOWN\t").append(stopped).append('\n'); check(stopped.state() == PhantomMaterializationService.ServiceState.STOPPED, "OWN074 materialization did not store/stop."); });
		try
		{
			check(materialization.start() && materialization.materialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "OWN074 actor materialization failed.");
			final var player = World.getInstance().getPlayer(Integer.parseInt(state.getProperty("actor"))); check(player != null && player.getAccountName().equals(account) && !player.isAlikeDead(), "OWN074 exact living actor unavailable.");
			final var progressionPolicy = PhantomProgressionPolicy.productionDefaults(); final var progression = new PhantomProgressionCatalogBuilder().build(new L2jProgressionBackend(null, Path.of("."), () -> query).load(progressionPolicy), progressionPolicy);
			final var backend = new L2jCombatBackend(materialization, () -> query, () -> progression);
			combat = new PhantomCombatService(backend, PhantomCombatCapabilityResolver.fromGameKnowledge(() -> query), PhantomCombatPolicy.productionDefaults(1)); combat.start();
			final var ownedCombat = combat; cleanup.add(() -> { ownedCombat.beginStop(); drain(ownedCombat::finishStop, "OWN074 combat did not drain."); });
			try (var lease = materialization.tryAcquireAction(id).orElseThrow()) { player.setRunning(); }
			report.append("A_MEASUREMENT\tactor=").append(player.getObjectId()).append(";profile=").append(id).append(";phase=").append(state.getProperty("phase")).append(";EXP=").append(player.getExp()).append(";SP=").append(player.getSp()).append(";A=").append(player.getAdena()).append(";HP=").append(player.getCurrentHp()).append(";inventory=").append(inventory(player)).append(";xyz=").append(player.getLocation()).append('\n');
			final var roots = new PhantomGoalStateStore(profiles); final var receipts = new PhantomCommerceReceiptStore(profiles);
			if (action.equals("loot"))
			{
				check("paid-returned".equals(state.getProperty("phase")), "OWN074 C requires the retained paid actor; no fixture replacement.");
				kill(combat, id, player, materialization, report); PhantomLoot074Native.dispositions(id, player, materialization, report);
				report.append("C_RETAINED_CONTROL\tone useful native kill on already paid actor;exact observed ground receipts above;no new prior savings/fixture/position writes\n"); return;
			}
			if (selection)
			{
				final var target = World.getInstance().getVisibleObjectsInRange(player, Monster.class, 1200).stream().filter(mob -> mob.isSpawned() && !mob.isAlikeDead() && !mob.isRaid() && mob.getLevel() <= player.getLevel() + 2 && GeoEngine.getInstance().canSeeTarget(player, mob)).min(Comparator.comparingDouble(value -> player.calculateDistance3D(value))).orElseThrow();
				check(PhantomLoot074Native.verifyDetachedSelection(id, player, materialization, backend, target, report), "OWN074 detached native selection RED."); return;
			}
			if (resume)
			{
				check("paid-returned".equals(state.getProperty("phase")), "OWN074 paid checkpoint missing; no reseed/recompute.");
				check(state.getProperty("inventory").equals(inventory(player)) && state.getProperty("exp").equals(Long.toString(player.getExp())) && state.getProperty("sp").equals(Long.toString(player.getSp())) && state.getProperty("receipt").equals(receipts.find(id).orElseThrow().receipt().toString()), "OWN074 newJVM inventory/reward/receipt continuity differs.");
				PhantomLoot074Native.dispositions(id, player, materialization, report);
				final long shots = player.getInventory().getInventoryItemCount(1463, -1); kill(combat, id, player, materialization, report); check(player.getInventory().getInventoryItemCount(1463, -1) < shots, "OWN074 restart did not spend retained purchased shot.");
				report.append("A_RESTART_PASS\tsame actor/inventory/EXP/SP/receipt; new useful native kill and shot debit\n"); return;
			}
			if (roots.load(id).isEmpty())
			{
				final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING).min(Comparator.comparingDouble(value -> player.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).orElseThrow();
				roots.insert(id, new PhantomGoal(740074, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(id)), new PhantomDomainRef("npc", "20038"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20038@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture074.finite_prior_savings", 1));
			}
			if ("born".equals(state.getProperty("phase")))
			{
				int earnedKills = Integer.parseInt(state.getProperty("earned.kills", "0"));
				while (earnedKills < 2 || player.getAdena() <= 4000 && earnedKills < 3)
				{
					kill(combat, id, player, materialization, report); earnedKills++; state.setProperty("earned.kills", Integer.toString(earnedKills)); save(stateFile, state);
				}
				// Three rewarded kills are the finite earn control. A native surplus sale may
				// supply the positive cash event (TEST_PLAN074); never grind/regrant for it.
				report.append("A_EARN_CONTROL\tkills=").append(earnedKills).append(";nativeKillAdena=").append(player.getAdena() - 4000).append(";no more prebuy kills;normal surplus maintenance may sell prior property;not kill income\n");
				state.setProperty("phase", "earned"); state.setProperty("earnedA", Long.toString(player.getAdena() - 4000)); save(stateFile, state);
			}
			final var root = roots.load(id).orElseThrow().goal(); final var store = new PhantomSupplyIntentStore(profiles, roots); final var travel = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
			PhantomLoot074Native.dispositions(id, player, materialization, report);
			final var economyPolicy = org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy.load(Path.of("data/phantoms/economy/high-five-economy-v1.xml"));
			final var reservations = new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService(economyPolicy); check(reservations.start(), "OWN074 economy reservations unavailable."); cleanup.add(() -> reservations.shutdown(System.currentTimeMillis()));
			final var social = new org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService(economyPolicy, reservations, new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOfferService(), materialization, store, profiles); cleanup.add(() -> check(social.shutdown(System.currentTimeMillis()).successful(), "OWN074 social economy did not drain."));
			navigation = new PhantomNavigationService(metrics); commerce = new PhantomCommerceService(catalog, receipts, store, new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone()));
			final var ownedNavigation = navigation; cleanup.add(() -> { ownedNavigation.beginStop(); drain(ownedNavigation::finishStop, "OWN074 navigation did not drain."); });
			final var ownedCommerce = commerce; cleanup.add(() -> { ownedCommerce.beginStop(); check(ownedCommerce.finishStop(), "OWN074 commerce did not drain."); });
			journey = new L2jPhantomSupplyJourney(store, roots, materialization, commerce, receipts, navigation, () -> query, travel, _ -> true, (_, _) -> true, (_, _) -> false, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")));
			journey.useOfflineSupply(social); journey.useInventoryMaintenance(_ -> true); check(navigation.start() && commerce.start() && journey.start(), "OWN074 supply owners did not start.");
			final var ownedJourney = journey; cleanup.add(() -> { ownedJourney.beginStop(); check(ownedJourney.finishStop(), "OWN074 journey did not drain."); });
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			String last = ""; boolean bought = false, returned = false; long saleRevenue = 0; final var seenReceipts = new java.util.HashSet<String>(); final long until = System.nanoTime() + TimeUnit.MINUTES.toNanos(8);
			while (System.nanoTime() < until)
			{
				check(!player.isDead(), "OWN074 native actor died; retain history and stop this lane.");
				final var planning = new PhantomPlanningContext(id, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
				final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
				if (selected != null) { final var plan = selected.planFactory().create(planning); handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(id, root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false)); }
				final String reason = journey.reason(id); if (!reason.equals(last)) { report.append("A_JOURNEY\t").append(reason).append(";xyz=").append(player.getLocation()).append(";moving=").append(player.isMoving()).append(";HP=").append(player.getCurrentHp()).append(";A=").append(player.getAdena()).append('\n'); System.out.println("OWN074_A " + reason + ";xyz=" + player.getLocation()); last = reason; }
				final var receipt = receipts.find(id).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
				if (receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED && seenReceipts.add(receipt.operationKey()))
				{
					if (receipt.request().kind() == PhantomCommerceReceipt.OperationKind.SELL) { saleRevenue = Math.addExact(saleRevenue, receipt.expectedAfter().primaryCount() - receipt.before().primaryCount()); }
					report.append("C_COMMERCE_RECEIPT\t").append(receipt).append(";fixture obsoleteNG1 is prior property, sale revenue is not earned kill income;physicalA=").append(player.getAdena()).append('\n');
				}
				if (receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED && receipt.request().kind() == PhantomCommerceReceipt.OperationKind.BUY && receipt.request().itemId() == 1463)
				{
					if (!bought) { report.append("A_NATIVE_PAID\t").append(receipt).append(";physicalA=").append(player.getAdena()).append(";physical1463=").append(player.getInventory().getInventoryItemCount(1463, -1)).append('\n'); }
					bought = true; check(player.getAdena() == receipt.expectedAfter().primaryCount() && player.getInventory().getInventoryItemCount(1463, -1) == receipt.expectedAfter().secondaryCount(), "OWN074 paid physical conservation differs.");
				}
				final var intent = journey.automaticIntent(id).orElse(null);
				if (intent != null && intent.status() == PhantomGoalStatus.FAILED && !"income.farm".equals(intent.acquisitionMethod())) { throw new AssertionError("OWN074 supply failure: " + intent); }
				returned = intent != null && intent.status() == PhantomGoalStatus.COMPLETED && bought;
				if (returned) { break; }
				Thread.sleep(200);
			}
			check(bought && returned, "OWN074 paid return not observed within finite8min: " + last);
			for (int index = 0; index < 2; index++) { final long shots = player.getInventory().getInventoryItemCount(1463, -1); kill(combat, id, player, materialization, report); check(player.getInventory().getInventoryItemCount(1463, -1) < shots, "OWN074 boosted return kill did not consume purchased1463."); }
			state.setProperty("phase", "paid-returned"); state.setProperty("inventory", inventory(player)); state.setProperty("exp", Long.toString(player.getExp())); state.setProperty("sp", Long.toString(player.getSp())); state.setProperty("receipt", receipts.find(id).orElseThrow().receipt().toString()); save(stateFile, state);
			check(Long.parseLong(state.getProperty("earnedA")) > 0 || saleRevenue > 0, "OWN074 paid chain stored, but no positive native cash/surplus-sale event; retain PARTIAL cash gate.");
			report.append("A_NATIVE_PASS\tfixture4000A separately; nativeKillAdena=").append(state.getProperty("earnedA")).append(";nativePriorPropertySaleRevenue=").append(saleRevenue).append(";native paid1463;physical return;2rewarded shot-consuming kills;positiveCashGate=").append(Long.parseLong(state.getProperty("earnedA")) > 0 || saleRevenue > 0).append('\n');
		}
		finally
		{
			PhantomSupply073JourneyNative.closeAll(cleanup);
		}
	}
	private static void kill(PhantomCombatService combat, long id, Player player, PhantomMaterializationService materialization, StringBuilder report) throws Exception
	{
		Monster target = null;
		final var visible = List.copyOf(World.getInstance().getVisibleObjectsInRange(player, Monster.class, 5000));
		final long approachDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
		for (var candidate : visible.stream().filter(mob -> mob.isSpawned() && !mob.isAlikeDead() && !mob.isRaid() && mob.getInstanceId() == 0 && mob.getLevel() >= player.getLevel() - 2 && mob.getLevel() <= player.getLevel() + 2 && mob.getExpReward(player.getLevel()) > 0 && mob.getSpReward(player.getLevel()) > 0).sorted(Comparator.comparingDouble(value -> player.calculateDistance3D(value))).limit(8).toList())
		{
			if (System.nanoTime() >= approachDeadline) { break; }
			if (player.calculateDistance3D(candidate) > 600 || !GeoEngine.getInstance().canSeeTarget(player, candidate))
			{
				if (materialization == null) { continue; }
				final var paths = new L2jNavigationBackend(); final var from = PhantomNativeTravelActions.point(player);
				final var to = paths.walkSurface(new PhantomNavigationPoint(candidate.getX(), candidate.getY(), candidate.getZ(), 0), 256).orElse(null); if (to == null) { continue; }
				final long now = System.nanoTime(); final var route = paths.findWalkCorridor(new PhantomNavigationRequest(id, from, to, now, now + TimeUnit.SECONDS.toNanos(1), 12000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), System::nanoTime);
				report.append("A_NATIVE_TARGET_APPROACH\tfrom=").append(from).append(";target=").append(candidate.getObjectId()).append(";to=").append(to).append(";route=").append(route.status()).append('\n');
				if (route.status() != PhantomWalkCorridorPlanner.Status.FOUND) { continue; }
				boolean safe = true; final long deadline = approachDeadline;
				try (var lease = materialization.tryAcquireAction(id).orElseThrow())
				{
					for (var point : route.points())
					{
						if (player.calculateDistance3D(point.x(), point.y(), point.z()) <= 40) { continue; }
						if (!paths.canWalkDirect(PhantomNativeTravelActions.point(player), point) || !PhantomNativeTravelActions.safePveWalkSegment(player, point.x(), point.y(), point.z(), visible, java.util.Set.of(candidate.getObjectId()))) { safe = false; break; }
						player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.MOVE_TO, new org.l2jmobius.gameserver.model.Location(point.x(), point.y(), point.z())); final var issued = PhantomNativeTravelActions.captureWalk(player);
						try { while (!player.isDead() && System.nanoTime() < deadline && player.isMoving() && player.calculateDistance3D(point.x(), point.y(), point.z()) > 40) { Thread.sleep(100); } }
						finally { PhantomNativeTravelActions.cancelWalk(issued); }
						if (player.isDead() || System.nanoTime() >= deadline || player.calculateDistance3D(point.x(), point.y(), point.z()) > 50) { safe = false; break; }
					}
				}
				if (!safe) { continue; }
			}
			if (!candidate.isAlikeDead() && player.calculateDistance3D(candidate) <= 1200 && GeoEngine.getInstance().canSeeTarget(player, candidate)) { target = candidate; break; }
		}
		check(target != null, "OWN074 no rewarding target on bounded native approach; no relocation.");
		final long exp = player.getExp(), sp = player.getSp(), adena = player.getAdena(), shots = player.getInventory().getInventoryItemCount(1463, -1);
		final var start = combat.startSession(new PhantomCombatRequest(id, target.getObjectId(), PhantomCombatMode.MELEE_PHYSICAL, true, true, 30_000, () -> false)); check(start.accepted(), "OWN074 combat refused: " + start);
		final var ground = new java.util.LinkedHashMap<Integer, PhantomCombatBackend.LootCandidate>();
		final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
		while (System.nanoTime() < until && !combat.find(id).map(value -> value.result().terminal()).orElse(false))
		{
			for (var item : World.getInstance().getVisibleObjectsInRange(player, org.l2jmobius.gameserver.model.item.instance.Item.class, 1200).stream().filter(item -> item.isSpawned() && item.getCount() > 0).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).limit(32).toList())
			{
				if (ground.size() < 32) { ground.putIfAbsent(item.getObjectId(), new PhantomCombatBackend.LootCandidate(item.getObjectId(), item.getId(), item.getCount(), player.getInventory().getInventoryItemCount(item.getId(), -1))); }
			}
			Thread.sleep(100);
		}
		final var result = combat.find(id).orElseThrow(); check(result.result().victory() && target.isDead() && !player.isDead() && player.getExp() > exp && player.getSp() > sp, "OWN074 native rewarded kill failed: " + result);
		final long drain = System.nanoTime() + TimeUnit.SECONDS.toNanos(10); while (combat.consumeTerminal(id).isEmpty()) { check(System.nanoTime() < drain, "OWN074 terminal combat cleanup failed."); Thread.sleep(100); }
		report.append("NATIVE_KILL\tnpc=").append(target.getId()).append('/').append(target.getObjectId()).append(";EXP=").append(exp).append("->").append(player.getExp()).append(";SP=").append(sp).append("->").append(player.getSp()).append(";A=").append(adena).append("->").append(player.getAdena()).append(";shots=").append(shots).append("->").append(player.getInventory().getInventoryItemCount(1463, -1)).append(";result=").append(result).append(";nativeEvidence=").append(player.getNativeWorkOwner().evidence().snapshot().scalarMap()).append(";pickupSequence=").append(player.getNativeWorkOwner().evidence().pickupSequence()).append('\n');
		for (var item : player.getInventory().getItems())
		{
			final var receipt = player.getNativeWorkOwner().evidence().pickupReceipt(item.getObjectId());
			if (receipt != null) { ground.putIfAbsent(receipt.sourceObjectId(), new PhantomCombatBackend.LootCandidate(receipt.sourceObjectId(), receipt.itemId(), receipt.count(), 0)); }
		}
		for (var candidate : ground.values())
		{
			final var receipt = player.getNativeWorkOwner().evidence().pickupReceipt(candidate.worldObjectId());
			PhantomLoot074Native.classify(player, candidate, receipt == null ? PhantomCombatBackend.LootObservation.PENDING : receipt.nativeConsumed() ? PhantomCombatBackend.LootObservation.CONSUMED_NATIVE : PhantomCombatBackend.LootObservation.ACQUIRED_BY_ACTOR, report);
		}
	}
	static String inventory(Player player) { return player.getInventory().getItems().stream().filter(item -> item.getCount() > 0).sorted(Comparator.comparingInt(org.l2jmobius.gameserver.model.item.instance.Item::getObjectId)).map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.isEquipped()).collect(java.util.stream.Collectors.joining(";")); }
	private static void save(Path path, Properties state) throws Exception { final var temporary = path.resolveSibling(path.getFileName() + ".tmp"); try (var writer = Files.newBufferedWriter(temporary)) { state.store(writer, "OWN074 retained native state; no reseed"); } Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
	private static void drain(java.util.function.BooleanSupplier finish, String message) throws Exception { final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(12); boolean stopped = finish.getAsBoolean(); while (!stopped && System.nanoTime() < until) { Thread.sleep(100); stopped = finish.getAsBoolean(); } check(stopped, message); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

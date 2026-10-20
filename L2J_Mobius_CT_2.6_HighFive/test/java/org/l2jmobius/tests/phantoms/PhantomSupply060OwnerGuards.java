/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.commerce.L2jCommerceBackend;
import org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCapabilitySet;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDomainRef;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomPlanningContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepContext;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomUtilitySelector;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;

/** Separate finite owner controls; no full life/career/accepted human-command claim. */
final class PhantomSupply060OwnerGuards
{
	static void ammunitionPolicy(PhantomTestContext context, Player player, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology,
			org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog progression, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog commerce) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (player.isInCombat() && System.nanoTime() < deadline) { Thread.sleep(100); }
		PhantomAssertions.assertTrue(player.getActiveWeaponItem() != null && player.getActiveWeaponItem().isRange() && !player.isInCombat(), "Ammunition policy control requires its native prepared bow after guard cleanup.");
		final var authority = new org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority(() -> knowledge, () -> topology, () -> progression, () -> commerce);
		final var capture = authority.captureNativeContext(player);
		final boolean permitted = capture.policy().withPosition(true, true).permits(org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundSimulationPolicy.Operation.FARM, org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority.configuredSimulationFingerprint());
		context.record("supply060.ammunitionPolicy", "native captured Bow14; ordinaryRewards=" + capture.policy().ordinaryRewards() + "; unsupportedFacts=" + capture.policy().unsupportedFacts() + "; virtual farm permission=" + permitted + "; virtual resource vector has no ammunition consumption; actual native field position remains separately validated");
		PhantomAssertions.assertFalse(permitted, "Unmodeled native bow ammunition may not earn virtual farm rewards without consuming arrows.");
		PhantomAssertions.assertTrue((capture.policy().unsupportedFacts() & 256) != 0, "Native ammunition refusal lost its explicit scalar fact.");
		final var identity = new org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState.Identity(1, player.getObjectId(), player.getClassIndex(), player.getActiveClass(), player.getRace().ordinal());
		final var capsule = org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.completed(identity, capture, 0, new byte[] {1});
		PhantomAssertions.assertEquals(capsule, org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.decode(capsule.encode()), "Known V2 ammunition policy did not roundtrip.");
		final byte[] unknown = capsule.encode(); java.nio.ByteBuffer.wrap(unknown, unknown.length - 4, 4).putInt(512);
		PhantomAssertions.assertThrows(IllegalArgumentException.class, () -> org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.decode(unknown), "Unknown V2 policy flag was accepted.");
		final var legacy = org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.completed(identity, 1, org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.Eligibility.SUPPORTED, 0, new byte[] {1});
		PhantomAssertions.assertEquals(legacy, org.l2jmobius.gameserver.phantoms.background.PhantomNativeContext.decode(legacy.encode()), "Legacy V1 capsule parser regressed.");
	}
	static void run(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId) throws Exception
	{
		run(context, player, profileId, profiles, materialization, knowledge, topology, npcId, false);
	}

	static void run(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId, boolean ammunition) throws Exception
	{
		run(context, player, profileId, profiles, materialization, knowledge, topology, npcId, ammunition, false);
	}

	static void blessed(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId) throws Exception
	{
		run(context, player, profileId, profiles, materialization, knowledge, topology, npcId, false, true);
	}

	private static void run(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId, boolean ammunition, boolean blessed) throws Exception
	{
		run(context, player, profileId, profiles, materialization, knowledge, topology, npcId, ammunition, blessed, false);
	}
	static void equipment(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId) throws Exception
	{
		run(context, player, profileId, profiles, materialization, knowledge, topology, npcId, false, false, true);
	}
	private static void run(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId, boolean ammunition, boolean blessed, boolean equipment) throws Exception
	{
		run(context, player, profileId, profiles, materialization, knowledge, topology, npcId, ammunition, blessed, equipment, false);
	}
	static void equipmentPaid(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId) throws Exception
	{
		run(context, player, profileId, profiles, materialization, knowledge, topology, npcId, false, false, true, true);
	}
	private static void run(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId, boolean ammunition, boolean blessed, boolean equipment, boolean paid) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final long baselineAdena = player.getAdena();
		final var initialObjects = equipment ? player.getInventory().getItems().stream().map(item -> item.getObjectId()).collect(java.util.stream.Collectors.toSet()) : java.util.Set.<Integer>of();
		final var initialEquipment = equipment ? player.getInventory().getItems().stream().filter(item -> item.isEquipped()).map(item -> item.getObjectId()).toList() : List.<Integer>of();
		final var initialSupplies = new java.util.LinkedHashMap<Integer, Long>();
		if (equipment) { for (int itemId : new int[] {57, 1835, 3947, 1061}) { initialSupplies.put(itemId, player.getInventory().getInventoryItemCount(itemId, -1)); } }
		player.setBaseClass(blessed ? 10 : 0); player.setPlayerClass(blessed ? 10 : 0); player.setLearningClass(player.getPlayerClass()); player.getStat().setLevel((byte) 5); player.setSp(0);
		final var weapon = player.getInventory().addItem(ItemProcessType.REWARD, ammunition ? 14 : blessed ? 6 : 68, 1, null, PhantomSupply060OwnerGuards.class);
		PhantomAssertions.assertTrue(weapon != null, "Finite owner-guard weapon unavailable."); player.getInventory().equipItem(weapon);
		final int supplyItemId = ammunition ? 17 : blessed ? 3947 : 1835;
		if (blessed)
		{
			player.getInventory().addItem(ItemProcessType.REWARD, 2509, 100, null, PhantomSupply060OwnerGuards.class);
			// Independent, declared native route input from ordinary18; no relocation after measurement begins.
			final int ground = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(-88024, 251400, -3656);
			player.decayMe(); player.setXYZInvisible(-88024, 251400, ground); player.spawnMe(); player.revalidateZone(true);
			context.record("supply061.observedRouteInput", "Independent TEST Mystic5/NG6 at ordinary18 original field(-88024,251400," + ground + "); native source selection and unchanged eight-minute journey; no ordinary state mutation");
			final var data = org.l2jmobius.gameserver.data.xml.ItemData.getInstance();
			PhantomAssertions.assertTrue(L2jPhantomSupplyJourney.compatible(player, data.getTemplate(3947)), "Native NG blessed spiritshot is unavailable.");
			PhantomAssertions.assertFalse(L2jPhantomSupplyJourney.compatible(player, data.getTemplate(3948)), "No-grade mage weapon accepted D-grade blessed spiritshot.");
		}
		if (ammunition)
		{
			player.getInventory().addItem(ItemProcessType.REWARD, 1835, 600, null, PhantomSupply060OwnerGuards.class);
			player.getInventory().addItem(ItemProcessType.REWARD, 17, 2, null, PhantomSupply060OwnerGuards.class);
			final var itemData = org.l2jmobius.gameserver.data.xml.ItemData.getInstance();
			PhantomAssertions.assertTrue(L2jPhantomSupplyJourney.compatible(player, itemData.getTemplate(17)), "Native NG bow/wooden arrow supply contract is missing.");
			PhantomAssertions.assertFalse(L2jPhantomSupplyJourney.compatible(player, itemData.getTemplate(1341)), "NG bow accepted D-grade arrows.");
			PhantomAssertions.assertFalse(L2jPhantomSupplyJourney.compatible(player, itemData.getTemplate(9632)), "Bow accepted native crossbow bolts.");
			PhantomAssertions.assertTrue(L2jPhantomSupplyJourney.compatible(player, itemData.getTemplate(1835)), "Existing native NG shot compatibility regressed.");
		}
		player.getInventory().addItem(ItemProcessType.REWARD, 57, 10_000, null, PhantomSupply060OwnerGuards.class);
		player.refreshExpertisePenalty(); player.refreshOverloaded(); player.setRunning(); player.standUp();
		if (equipment)
		{
			player.setBaseClass(10); player.setPlayerClass(10); player.setLearningClass(player.getPlayerClass()); player.getStat().setExp(player.getStat().getExpForLevel(7)); player.getStat().setLevel((byte) 7); player.removeSkill(239); player.giveAvailableAutoGetSkills();
			for (int itemId : new int[] {1, 24, 31, 6, 425, 461}) { final var item = player.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, player, PhantomSupply060OwnerGuards.class); if (itemId == 1 || itemId == 24 || itemId == 31) { player.getInventory().equipItem(item); } }
			player.getInventory().addItem(ItemProcessType.REWARD, 3947, 100, player, PhantomSupply060OwnerGuards.class);
			player.refreshExpertisePenalty(); player.refreshOverloaded(); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
			PhantomAssertions.assertFalse(player.getActiveWeaponItem().isMagicWeapon(), "Owned equipment input is already a magic weapon.");
			context.record("equipment061.ownedInput", "Independent native Mystic7, equipped physical ShortSword1/Bone24+31, added owned Wand6/robe425+461/BSS3947x100; other native fixture inventory retained; all input before measurement; no ordinary actor changed");
			if (paid)
			{
				player.setBaseClass(0); player.setPlayerClass(0); player.setLearningClass(player.getPlayerClass()); player.getStat().setExp(player.getStat().getExpForLevel(19)); player.getStat().setLevel((byte) 19); player.removeSkill(239); player.giveAvailableAutoGetSkills();
				player.getInventory().addItem(ItemProcessType.REWARD, 1835, 600, player, PhantomSupply060OwnerGuards.class); player.getInventory().addItem(ItemProcessType.REWARD, 1061, 3, player, PhantomSupply060OwnerGuards.class); player.getInventory().addItem(ItemProcessType.REWARD, 57, 40_000, player, PhantomSupply060OwnerGuards.class);
				final var shop = knowledge.snapshot().spawnFactsByNpc().values().stream().flatMap(List::stream).filter(spawn -> spawn.instanceId() == 0 && Math.hypot((long) spawn.x() + 12787, (long) spawn.y() - 122779) < 4000).map(spawn -> org.l2jmobius.gameserver.model.World.getInstance().getRegion(spawn.x(), spawn.y(), spawn.z())).filter(java.util.Objects::nonNull).distinct().flatMap(region -> region.getVisibleObjects().stream()).filter(object -> object instanceof org.l2jmobius.gameserver.model.actor.instance.Merchant).map(object -> (org.l2jmobius.gameserver.model.actor.instance.Merchant) object).min(java.util.Comparator.comparingInt(org.l2jmobius.gameserver.model.actor.instance.Merchant::getObjectId)).orElseThrow();
				final int ground = org.l2jmobius.gameserver.geoengine.GeoEngine.getInstance().getHeight(-12787, 122779, -3112);
				PhantomCombatServerIntegrationSuite.placeInstanceInput058(player, -12787, 122779, ground);
				PhantomAssertions.assertTrue(org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePreparationTown(player), "Paid equipment input requires the existing verified native Gludio base.");
				player.refreshExpertisePenalty(); player.refreshOverloaded(); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp());
				context.record("equipment061.paidInput", "Independent native Fighter19/NG expertise, additional40000 finite Adena/600NGshots/3potions before measurement; reuse verified native Gludio(-12787,122779," + ground + ") from town/lifecycle controls, rather than an elevated merchant spawn; nearby Merchant=" + shop.getId() + '/' + shop.getObjectId() + "; product chooses actual item/source; no quest items/stages/class/SP grants during measurement");
			}
		}
		final var goals = new PhantomGoalStateStore(profiles);
		PhantomAssertions.assertTrue(goals.load(profileId).isEmpty(), "Supply guard refuses an existing root.");
		final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING && value.point().instanceId() == 0).min(java.util.Comparator.comparingDouble(value -> Math.hypot(value.point().x() - player.getX(), value.point().y() - player.getY()))).orElseThrow();
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef("npc", Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.supplies.owner_guards", 1);
		goals.insert(profileId, root);
		final var store = new PhantomSupplyIntentStore(profiles, goals);
		final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load();
		if (ammunition) { PhantomAssertions.assertTrue(catalog.catalog().findSupply(17) != null && catalog.catalog().findSupply(17).kinds().stream().anyMatch(kind -> kind.name().equals("AMMUNITION")), "Native arrow source classification is missing."); }
		final var receipts = new PhantomCommerceReceiptStore(profiles);
		final var initialReceipt = receipts.find(profileId);
		final var commerce = new PhantomCommerceService(catalog, receipts, store, new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone()));
		final var navigation = new PhantomNavigationService(new PhantomMetrics());
		final var routes = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
		final var policy = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
		final var allowed = new AtomicBoolean(false);
		var journey = new L2jPhantomSupplyJourney(store, goals, materialization, commerce, receipts, navigation, () -> knowledge, routes, _ -> allowed.get(), (_, _) -> true, (_, _) -> false, policy);
		final var progression = equipment ? new org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService(new org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend(materialization, Path.of("."), () -> knowledge), org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy.productionDefaults()) : null;
		if (progression != null) { progression.start(); journey.useEquipment(progression, (_, _) -> false); }
		PhantomAssertions.assertTrue(navigation.start() && commerce.start() && journey.start(), "Supply owner guards did not start.");
		final long adena = player.getAdena(), stock = player.getInventory().getInventoryItemCount(supplyItemId, -1);
		final int x = player.getX(), y = player.getY(), z = player.getZ();
		context.record("supply060.ownerInput", "SEPARATE PREMEASUREMENT own finite " + (ammunition ? "NG Bow14/arrows2/NGshots600/class0" : blessed ? "Mystic10/NG6/regular2509x100/blessed0" : "NG68/class0") + " and10000 Adena; level5/SP0; root=" + root + "; guard calls only, no full farm/command claim");
		try
		{
			if (equipment)
			{
				final long exp = player.getExp(), sp = player.getSp();
				PhantomAssertions.assertFalse(journey.needsOrOwns(profileId, root), "HOLD proposed equipment."); allowed.set(true);
				final var registry = new PhantomCandidateRegistry(); journey.registerCandidates(registry); registry.seal();
				final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
				final var equipmentJourney = journey;
				final var phases = new java.util.ArrayList<String>();
				final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(paid ? 90 : 50);
				while (System.nanoTime() < deadline && (paid ? receipts.find(profileId).map(value -> value.receipt().state() != org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.State.COMMITTED || player.getInventory().getItems().stream().noneMatch(item -> item.isEquipped() && item.getId() == value.receipt().request().itemId()) || equipmentJourney.automaticIntent(profileId).orElseThrow().status() != PhantomGoalStatus.COMPLETED).orElse(true) : !(player.getActiveWeaponItem().isMagicWeapon() && player.getInventory().getItems().stream().anyMatch(item -> item.isEquipped() && item.getTemplate() instanceof org.l2jmobius.gameserver.model.item.Armor armor && armor.getItemType() == org.l2jmobius.gameserver.model.item.type.ArmorType.MAGIC))))
				{
					final var planning = new PhantomPlanningContext(profileId, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
					final var selected = new PhantomUtilitySelector().select(registry.snapshot(), planning).candidate();
					if (selected != null)
					{
						final var plan = selected.planFactory().create(planning); final var step = new PhantomStepContext(profileId, root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false);
						final var result = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(step);
						if (phases.size() < 24 && (phases.isEmpty() || !phases.getLast().equals(result.reasonKey()))) { phases.add(result.reasonKey()); }
						if (journey.automaticIntent(profileId).map(goal -> goal.status() == PhantomGoalStatus.FAILED).orElse(false)) { break; }
					}
					Thread.sleep(500);
				}
				if (paid)
				{
					context.record("equipment061.paidTrace", "phases=" + phases + "; final=" + journey.automaticIntent(profileId) + "; actual=" + player.getX() + ',' + player.getY() + ',' + player.getZ() + "; safeTown=" + org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions.safePreparationTown(player));
					final var paidReceipt = receipts.find(profileId).orElseThrow().receipt();
					PhantomAssertions.assertEquals(org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.State.COMMITTED, paidReceipt.state(), "Native gear payment did not commit.");
					PhantomAssertions.assertTrue(player.getAdena() < adena && player.getInventory().getItems().stream().anyMatch(item -> item.isEquipped() && item.getId() == paidReceipt.request().itemId()), "Native paid gear is not equipped: " + journey.reason(profileId));
					PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, journey.automaticIntent(profileId).orElseThrow().status(), "Paid gear trip did not return.");
					context.record("equipment061.paidNative", "native NPC=" + paidReceipt.request().npcTemplateId() + "; item=" + paidReceipt.request().itemId() + "; A=" + adena + "->" + player.getAdena() + "; receipt=" + paidReceipt.state() + "; native equipped; trip completed");
				}
				else
				{
					PhantomAssertions.assertTrue(player.getActiveWeaponItem().isMagicWeapon(), "Owned weapon remained inappropriate for native mage class: " + journey.reason(profileId));
					PhantomAssertions.assertTrue(player.getInventory().getItems().stream().anyMatch(item -> item.isEquipped() && item.getTemplate() instanceof org.l2jmobius.gameserver.model.item.Armor armor && armor.getItemType() == org.l2jmobius.gameserver.model.item.type.ArmorType.MAGIC), "Owned robe was not equipped through Progression: " + journey.reason(profileId));
					PhantomAssertions.assertTrue(player.getAdena() == adena && receipts.find(profileId).isEmpty(), "Owned native equipment fabricated commerce.");
				}
				PhantomAssertions.assertTrue(player.getExp() == exp && player.getSp() == sp, "Equipment fabricated progression.");
				PhantomAssertions.assertEquals(root, goals.load(profileId).orElseThrow().goal(), "Equipment replaced the farm root.");
				if (!paid) { context.record("equipment061.ownedNative", "actual weapon=" + player.getActiveWeaponItem().getId() + "; armor=" + player.getInventory().getItems().stream().filter(item -> item.isEquipped()).map(item -> item.getId()).toList() + "; EXP/SP/A unchanged; Commerce receipt absent; root unchanged; " + journey.reason(profileId)); }
				return;
			}
			PhantomAssertions.assertFalse(journey.needsOrOwns(profileId, root), "Foreign owner/HOLD port allowed a supply need.");
			allowed.set(true); player.getAI().clientStartAutoAttack();
			PhantomAssertions.assertTrue(player.isInCombat(), "Native combat stance did not start.");
			PhantomAssertions.assertTrue(journey.needsOrOwns(profileId, root), "Future stock need was not selectable during native combat.");
			final var replacement = root.withStatus(PhantomGoalStatus.ACTIVE);
			goals.replace(profileId, goals.load(profileId).orElseThrow().rowVersion(), replacement);
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal();
			final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final var planning = new PhantomPlanningContext(profileId, replacement, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1);
			final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
			PhantomAssertions.assertTrue(selected != null, "New root lost its supply offer.");
			for (var activity : PhantomActivityState.values())
			{
				final var statePlanning = new PhantomPlanningContext(profileId, replacement, PhantomCapabilitySet.empty(), activity, System.nanoTime(), 2);
				final boolean selectable = new PhantomUtilitySelector().select(candidates.snapshot(), statePlanning).candidate() != null;
				PhantomAssertions.assertTrue(selectable == activity.requiresMaterialization(), "Supply native Decision admission disagrees with materialization state " + activity + ": selected=" + selectable);
			}
			final var plan = selected.planFactory().create(planning);
			final var step = new PhantomStepContext(profileId, replacement, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false);
			final var result = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(step);
			final var intent = journey.automaticIntent(profileId).orElseThrow();
			PhantomAssertions.assertEquals(Integer.toString(supplyItemId), intent.target().key(), "Preferred native supply did not win independently of other compatible stock.");
			PhantomAssertions.assertEquals("supplies.native.busy", result.reasonKey(), "Native combat allowed travel/payment.");
			PhantomAssertions.assertEquals(replacement.revision(), PhantomSupplyIntentStore.Binding.parse(intent).rootRevision(), "Cached old revision created a stale supply intent.");
			PhantomAssertions.assertTrue(store.load(profileId).isPresent(), "Exact active child was not visible to Commerce.");
			PhantomAssertions.assertEquals(adena, player.getAdena(), "Combat guard charged Adena.");
			PhantomAssertions.assertEquals(stock, player.getInventory().getInventoryItemCount(supplyItemId, -1), "Combat guard supplied free charges.");
			PhantomAssertions.assertTrue(!player.isMoving() && player.getX() == x && player.getY() == y && player.getZ() == z && receipts.find(profileId).isEmpty(), "Guard created movement/payment receipt.");
			player.getAI().clientStopAutoAttack();
			final long stanceEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
			while (player.isInCombat() && System.nanoTime() < stanceEnd) { Thread.sleep(100); }
			PhantomAssertions.assertFalse(player.isInCombat(), "Native combat stance did not end before source measurement.");
			final var sourceReady = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(step);
			PhantomAssertions.assertFalse("supplies.native.busy".equals(sourceReady.reasonKey()), "Native source continuation remained in the ended combat stance.");
			context.record("supply061.clockSource", "Actual production handler parsed the accepted clock-bearing NPC source: " + sourceReady + "; no fixture success replacement");
			if (blessed)
			{
				final long routeEnd = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
				while (System.nanoTime() < routeEnd && !journey.reason(profileId).startsWith("supplies.native.walking") && !journey.reason(profileId).startsWith("supplies.navigation.ready"))
				{
					Thread.sleep(250);
					handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(profileId, replacement, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false));
				}
				context.record("supply061.nativeRouteBudget", journey.reason(profileId));
				PhantomAssertions.assertTrue(journey.reason(profileId).startsWith("supplies.native.walking") || journey.reason(profileId).startsWith("supplies.navigation.ready"), "Accepted eight-minute supply journey did not obtain a native travel plan: " + journey.reason(profileId));
			}
			journey.beginStop(); PhantomAssertions.assertTrue(journey.finishStop(), "First supply owner did not stop.");
			final var component = profiles.findComponent(profileId, PhantomSupplyIntentStore.COMPONENT).orElseThrow();
			final var expired = new PhantomGoal(intent.goalId(), intent.goalType(), intent.status(), intent.subject(), intent.target(), intent.requiredAmount(), intent.currentAmount(), intent.acquisitionMethod(), intent.validSources(), intent.selectedAnchor(), intent.purposeKey(), intent.priority(), intent.riskBudget(), intent.expenseBudget(), System.currentTimeMillis() - 1, intent.constraints(), intent.reasonKey(), intent.revision(), intent.payloadText());
			profiles.updateComponent(profileId, PhantomSupplyIntentStore.COMPONENT, component.rowVersion(), 1, new PhantomGoalStateCodec().encode(expired));
			journey = new L2jPhantomSupplyJourney(store, goals, materialization, commerce, receipts, navigation, () -> knowledge, routes, _ -> allowed.get(), (_, _) -> false, (_, _) -> false, policy);
			PhantomAssertions.assertTrue(journey.start(), "Reconstructed supply owner did not start.");
			final var renewed = new PhantomStepHandlerRegistry(); journey.registerHandlers(renewed); renewed.seal();
			final var expiredResult = renewed.snapshot().get(plan.steps().getFirst().actionKey()).execute(step);
			PhantomAssertions.assertEquals("supplies.deadline", expiredResult.reasonKey(), "Expired restored intent waited for yield.");
			PhantomAssertions.assertEquals(PhantomGoalStatus.FAILED, journey.automaticIntent(profileId).orElseThrow().status(), "Expired restored intent remained active.");
			PhantomAssertions.assertTrue(store.load(profileId).isEmpty() && !journey.needsOrOwns(profileId, replacement), "Expired/terminal child ignored Commerce authority/backoff.");
			PhantomAssertions.assertEquals(adena, player.getAdena(), "Expired intent charged Adena.");
			context.record("supply060.ownerGuards", "HOLD/foreign owner; future need while native combat but no movement/payment; stale cached root renewed; exact child Commerce view; expired reconstructed intent FAILED before yield; durable backoff; no free charges/receipt");
		}
		finally
		{
			journey.beginStop(); PhantomAssertions.assertTrue(journey.finishStop(), "Supply guard journey did not drain.");
			commerce.beginStop(); PhantomAssertions.assertTrue(commerce.finishStop(), "Supply guard Commerce did not drain.");
			if (progression != null) { progression.beginStop(); PhantomAssertions.assertTrue(progression.finishStop(), "Equipment native Progression did not drain."); }
			navigation.beginStop(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!navigation.finishStop() && System.nanoTime() < deadline) { Thread.sleep(20); }
			PhantomAssertions.assertEquals(PhantomNavigationService.ServiceState.STOPPED, navigation.snapshot().state(), "Supply guard Navigation did not drain.");
			if (equipment)
			{
				if (initialReceipt.isEmpty())
				{
					final var ownReceipt = receipts.find(profileId).orElse(null);
					if (ownReceipt != null) { PhantomAssertions.assertTrue(ownReceipt.receipt().goalId() == root.goalId() && ownReceipt.receipt().state().terminal(), "Foreign or pending equipment TEST receipt; cleanup refused."); profiles.deleteComponent(profileId, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.COMPONENT_TYPE, ownReceipt.rowVersion()); }
				}
				final var created = player.getInventory().getItems().stream().filter(item -> !initialObjects.contains(item.getObjectId()) && (item.getTemplate() instanceof org.l2jmobius.gameserver.model.item.Weapon || item.getTemplate() instanceof org.l2jmobius.gameserver.model.item.Armor)).toList();
				PhantomAssertions.assertTrue(created.size() <= 24, "Equipment fixture cleanup exceeded its bounded new-item receipt.");
				for (var item : created) { if (item.isEquipped()) { player.getInventory().unEquipItemInSlot(item.getLocationSlot()); } PhantomAssertions.assertTrue(player.getInventory().destroyItem(ItemProcessType.DESTROY, item, 1, player, PhantomSupply060OwnerGuards.class) != null, "Own new TEST equipment cleanup failed."); }
				for (int objectId : initialEquipment) { final var item = player.getInventory().getItemByObjectId(objectId); if (item != null && !item.isEquipped()) { player.getInventory().equipItem(item); } }
				for (var entry : initialSupplies.entrySet()) { final long excess = player.getInventory().getInventoryItemCount(entry.getKey(), -1) - entry.getValue(); if (excess > 0) { PhantomAssertions.assertTrue(player.getInventory().destroyItem(ItemProcessType.DESTROY, player.getInventory().getItemByItemId(entry.getKey()), excess, player, PhantomSupply060OwnerGuards.class) != null, "Own TEST supply excess cleanup failed."); } }
			}
			org.l2jmobius.gameserver.taskmanagers.AttackStanceTaskManager.getInstance().removeAttackStanceTask(player); player.getAI().clientStopAutoAttack();
			final var component = profiles.findComponent(profileId, PhantomSupplyIntentStore.COMPONENT).orElse(null);
			if (component != null)
			{
				final var intent = new PhantomGoalStateCodec().decode(component.payload());
				PhantomAssertions.assertTrue(intent.goalId() == root.goalId() && PhantomSupplyIntentStore.Binding.parse(intent).actorObjectId() == player.getObjectId(), "Foreign supply component; cleanup refused.");
				profiles.deleteComponent(profileId, PhantomSupplyIntentStore.COMPONENT, component.rowVersion());
			}
			final var current = goals.load(profileId).orElseThrow();
			PhantomAssertions.assertTrue(current.goal().equals(root) || current.goal().equals(root.withStatus(PhantomGoalStatus.ACTIVE)), "Foreign root; cleanup refused.");
			goals.delete(profileId, current.rowVersion());
			if (player.getAdena() == baselineAdena + 10_000) { player.reduceAdena(ItemProcessType.DESTROY, 10_000, player, false); }
		}
	}
}

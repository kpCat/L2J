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
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final long baselineAdena = player.getAdena();
		player.setBaseClass(blessed ? 10 : 0); player.setPlayerClass(blessed ? 10 : 0); player.setLearningClass(player.getPlayerClass()); player.getStat().setLevel((byte) 5); player.setSp(0);
		final var weapon = player.getInventory().addItem(ItemProcessType.REWARD, ammunition ? 14 : blessed ? 6 : 68, 1, null, PhantomSupply060OwnerGuards.class);
		PhantomAssertions.assertTrue(weapon != null, "Finite owner-guard weapon unavailable."); player.getInventory().equipItem(weapon);
		final int supplyItemId = ammunition ? 17 : blessed ? 3947 : 1835;
		if (blessed)
		{
			player.getInventory().addItem(ItemProcessType.REWARD, 2509, 100, null, PhantomSupply060OwnerGuards.class);
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
		final var goals = new PhantomGoalStateStore(profiles);
		PhantomAssertions.assertTrue(goals.load(profileId).isEmpty(), "Supply guard refuses an existing root.");
		final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING && value.point().instanceId() == 0).min(java.util.Comparator.comparingDouble(value -> Math.hypot(value.point().x() - player.getX(), value.point().y() - player.getY()))).orElseThrow();
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef("npc", Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.supplies.owner_guards", 1);
		goals.insert(profileId, root);
		final var store = new PhantomSupplyIntentStore(profiles, goals);
		final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load();
		if (ammunition) { PhantomAssertions.assertTrue(catalog.catalog().findSupply(17) != null && catalog.catalog().findSupply(17).kinds().stream().anyMatch(kind -> kind.name().equals("AMMUNITION")), "Native arrow source classification is missing."); }
		final var receipts = new PhantomCommerceReceiptStore(profiles);
		final var commerce = new PhantomCommerceService(catalog, receipts, store, new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone()));
		final var navigation = new PhantomNavigationService(new PhantomMetrics());
		final var routes = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
		final var policy = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
		final var allowed = new AtomicBoolean(false);
		var journey = new L2jPhantomSupplyJourney(store, goals, materialization, commerce, receipts, navigation, () -> knowledge, routes, _ -> allowed.get(), (_, _) -> true, (_, _) -> false, policy);
		PhantomAssertions.assertTrue(navigation.start() && commerce.start() && journey.start(), "Supply owner guards did not start.");
		final long adena = player.getAdena(), stock = player.getInventory().getInventoryItemCount(supplyItemId, -1);
		final int x = player.getX(), y = player.getY(), z = player.getZ();
		context.record("supply060.ownerInput", "SEPARATE PREMEASUREMENT own finite " + (ammunition ? "NG Bow14/arrows2/NGshots600/class0" : blessed ? "Mystic10/NG6/regular2509x100/blessed0" : "NG68/class0") + " and10000 Adena; level5/SP0; root=" + root + "; guard calls only, no full farm/command claim");
		try
		{
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
			navigation.beginStop(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!navigation.finishStop() && System.nanoTime() < deadline) { Thread.sleep(20); }
			PhantomAssertions.assertEquals(PhantomNavigationService.ServiceState.STOPPED, navigation.snapshot().state(), "Supply guard Navigation did not drain.");
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

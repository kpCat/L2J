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
	static void run(PhantomTestContext context, Player player, long profileId, PhantomProfileRepository profiles,
			PhantomMaterializationService materialization, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, int npcId) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		final long baselineAdena = player.getAdena();
		player.setBaseClass(0); player.setPlayerClass(0); player.setLearningClass(player.getPlayerClass()); player.getStat().setLevel((byte) 5); player.setSp(0);
		final var weapon = player.getInventory().addItem(ItemProcessType.REWARD, 68, 1, null, PhantomSupply060OwnerGuards.class);
		PhantomAssertions.assertTrue(weapon != null, "Finite owner-guard weapon unavailable."); player.getInventory().equipItem(weapon);
		player.getInventory().addItem(ItemProcessType.REWARD, 57, 10_000, null, PhantomSupply060OwnerGuards.class);
		player.refreshExpertisePenalty(); player.refreshOverloaded(); player.setRunning(); player.standUp();
		final var goals = new PhantomGoalStateStore(profiles);
		PhantomAssertions.assertTrue(goals.load(profileId).isEmpty(), "Supply guard refuses an existing root.");
		final var anchor = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING && value.point().instanceId() == 0).min(java.util.Comparator.comparingDouble(value -> Math.hypot(value.point().x() - player.getX(), value.point().y() - player.getY()))).orElseThrow();
		final var root = new PhantomGoal(context.seed(), PhantomBackgroundGoalSpec.GOAL_TYPE, PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profileId)), new PhantomDomainRef("npc", Integer.toString(npcId)), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npcId + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture.supplies.owner_guards", 1);
		goals.insert(profileId, root);
		final var store = new PhantomSupplyIntentStore(profiles, goals);
		final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load();
		final var receipts = new PhantomCommerceReceiptStore(profiles);
		final var commerce = new PhantomCommerceService(catalog, receipts, store, new L2jCommerceBackend(materialization, catalog.catalog(), Clock.systemDefaultZone()));
		final var navigation = new PhantomNavigationService(new PhantomMetrics());
		final var routes = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
		final var policy = PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"));
		final var allowed = new AtomicBoolean(false);
		var journey = new L2jPhantomSupplyJourney(store, goals, materialization, commerce, receipts, navigation, () -> knowledge, routes, _ -> allowed.get(), (_, _) -> true, (_, _) -> false, policy);
		PhantomAssertions.assertTrue(navigation.start() && commerce.start() && journey.start(), "Supply owner guards did not start.");
		final long adena = player.getAdena(), stock = player.getInventory().getInventoryItemCount(1835, -1);
		final int x = player.getX(), y = player.getY(), z = player.getZ();
		context.record("supply060.ownerInput", "SEPARATE PREMEASUREMENT own finite NG68 and10000 Adena; class0/5/SP0; root=" + root + "; guard calls only, no full farm/command claim");
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
			PhantomAssertions.assertEquals("supplies.native.busy", result.reasonKey(), "Native combat allowed travel/payment.");
			PhantomAssertions.assertEquals(replacement.revision(), PhantomSupplyIntentStore.Binding.parse(intent).rootRevision(), "Cached old revision created a stale supply intent.");
			PhantomAssertions.assertTrue(store.load(profileId).isPresent(), "Exact active child was not visible to Commerce.");
			PhantomAssertions.assertEquals(adena, player.getAdena(), "Combat guard charged Adena.");
			PhantomAssertions.assertEquals(stock, player.getInventory().getInventoryItemCount(1835, -1), "Combat guard supplied free charges.");
			PhantomAssertions.assertTrue(!player.isMoving() && player.getX() == x && player.getY() == y && player.getZ() == z && receipts.find(profileId).isEmpty(), "Guard created movement/payment receipt.");
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

/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.data.MerchantPriceConfigTable;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundDecision;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundPlanner;
import org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleAutoPlay;
import org.l2jmobius.gameserver.phantoms.background.PhantomVisibleFarmTravel;
import org.l2jmobius.gameserver.phantoms.commerce.L2jCommerceBackend;
import org.l2jmobius.gameserver.phantoms.commerce.L2jPhantomSupplyJourney;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomAutonomousSupplyPolicy;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.scripting.ScriptEngine;

/** One bounded native-only linked scene, using the same farm/travel/commerce owners as Main. */
final class PhantomTask066R2DwarfNativeScene
{
	static void run(PhantomTestContext context, PhantomProfileRepository profiles, PhantomHeadlessPlayerTestEnvironment environment) throws Exception
	{
		PhantomAssertions.assertTrue(PhantomCampaign058DatabaseLane.task066R2Enabled(), "Dwarf scene requires the isolated R2 TEST schema.");
		ScriptEngine.getInstance().executeScript(ScriptEngine.MASTER_HANDLER_FILE);
		MerchantPriceConfigTable.getInstance().loadInstances();
		final var fixture = environment.createCampaign058Companion(context, 1);
		final var profile = profiles.create(fixture.objectId());
		final long id = profile.profileId(); final int objectId = fixture.objectId();
		context.record("dwarf066r2.input", PhantomTask066R2DwarfInput.prepare(profiles, id, objectId));
		MerchantPriceConfigTable.getInstance().updateReferences();
		try (var production = PhantomBackgroundSuite.ProductionAuthorityFixture.start())
		{
			final var goals = new PhantomGoalStateStore(profiles); final var root = goals.load(id).orElseThrow().goal(); final var spec = PhantomBackgroundGoalSpec.parse(root);
			final var materialIds = NpcData.getInstance().getTemplate(spec.npcId()).getSpoilList().stream().map(value -> value.getItemId()).collect(java.util.stream.Collectors.toSet());
			final var metrics = new PhantomMetrics(); final var lifecycle = new PhantomMaterializationLifecycleBridge();
			final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1, _ -> {}, lifecycle, 5000, 10000);
			final var routes = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), production.topology()); final var transaction = new PhantomBackgroundTransaction();
			final var signals = signals();
			final var background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, production.authority(), new PhantomBackgroundCompetitionRegistry(), signals, () -> materialization);
			final var planner = new PhantomHistoricalBackgroundPlanner(production.knowledge(), production.topology(), production.authority());
			final var history = new PhantomHistoricalBackgroundService(profiles, goals, planner, background, materialization);
			final var navigation = new PhantomNavigationService(metrics); final var engineRef = new AtomicReference<PhantomDecisionEngine>();
			final var autoPlay = new PhantomVisibleAutoPlay(materialization, engineRef::get, history::permitsDecision);
			final var travel = new PhantomVisibleFarmTravel(materialization, background, routes, navigation, history::permitsDecision, signals, history::recordVisibleTravelFailure, System::nanoTime);
			final var intents = new PhantomSupplyIntentStore(profiles, goals); final var receipts = new PhantomCommerceReceiptStore(profiles); final var commerceResult = new PhantomCommerceCatalogLoader(Path.of(".")).load();
			final var commerce = new PhantomCommerceService(commerceResult, receipts, intents, new L2jCommerceBackend(materialization, commerceResult.catalog(), java.time.Clock.systemDefaultZone()));
			final var supplies = new L2jPhantomSupplyJourney(intents, goals, materialization, commerce, receipts, navigation, () -> production.knowledge(), routes, history::permitsDecision, autoPlay::yieldAfterCurrentTarget, (_id, _root) -> false, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")));
			supplies.useNativeResume(profiles, signals);
			supplies.requireField(history::canStartNativeJourney);
			history.bindVisibleIntent(supplies::ownsAutomaticIntent);
			lifecycle.install(PhantomMaterializationLifecyclePort.chain(supplies, PhantomMaterializationLifecyclePort.chain(PhantomMaterializationLifecyclePort.chain(history, background), PhantomMaterializationLifecyclePort.chain(autoPlay, travel))));
			final var binding = PhantomBackgroundDecision.bindVisibleLife(background, travel, autoPlay, history, engineRef::get);
			final var candidates = new PhantomCandidateRegistry(); binding.registerCandidates(candidates); supplies.registerCandidates(candidates); candidates.seal();
			final var handlers = new PhantomStepHandlerRegistry(); binding.registerHandlers(handlers); supplies.registerHandlers(handlers); handlers.seal();
			final var engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1, null, history::permitsDecision); engineRef.set(engine);
			final var reservations = new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService(org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy.load(Path.of("data/phantoms/economy/high-five-economy-v1.xml")));
			PhantomAssertions.assertTrue(reservations.start(), "Native dwarf reservations did not start.");
			org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort.install(reservations);
			supplies.useInventoryMaintenance(profileId -> reservations.findActive(profileId).isEmpty());
			Player player = null; boolean swept = false, banked = false, returned = false, done = false; long returnKills = 0, returnExp = 0, returnSp = 0;
			try
			{
				PhantomAssertions.assertTrue(background.start() && materialization.start() && navigation.start() && commerce.start() && supplies.start(), "Native dwarf ordinary owners did not start.");
				engine.start(); engine.attach(id);
				PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(id).status(), "Native dwarf admission rejected.");
				player = World.getInstance().getPlayer(objectId); final var nativeEvidence = player.getNativeWorkOwner().evidence();
				final long initialExp = player.getExp();
				final long end = System.nanoTime()+TimeUnit.MINUTES.toNanos(12); long nextPrint = 0, tick = 0;
				while (System.nanoTime() < end && !done)
				{
					final long now = System.nanoTime(); if (history.prepareVisibleDecision(id, engine)) { engine.accept(new PhantomActivityWorkItem(id, PhantomActivityState.ACTIVE, 1, ++tick, now, PhantomActivityOverloadLevel.NORMAL)); }
					final var sample = nativeEvidence.snapshot(); final var continuation = autoPlay.snapshotContinuation(id).scalarMap();
					final long sweepCount = Long.parseLong(continuation.getOrDefault("loot.swept", "0")); long materials = 0;
					for (int itemId : materialIds) { materials += player.getInventory().getInventoryItemCount(itemId, -1); }
					if (!swept && sweepCount > 0 && materials > 0 && sample.killSequence() > 0 && player.getExp() > initialExp)
					{ swept = true; context.record("dwarf066r2.earnedSweep", "native="+sample+"; sweep="+sweepCount+"; stockMaterialIds="+materialIds+"; inventoryCount="+materials+"; Adena="+player.getAdena()); }
					final var receipt = receipts.find(id).map(PhantomCommerceReceiptStore.VersionedReceipt::receipt).orElse(null);
					if (!banked && swept && receipt != null && receipt.state() == PhantomCommerceReceipt.State.COMMITTED && receipt.request().kind() == PhantomCommerceReceipt.OperationKind.WAREHOUSE_DEPOSIT && materialIds.contains(receipt.request().itemId()))
					{ banked = player.getWarehouse().getInventoryItemCount(receipt.request().itemId(), -1) >= receipt.request().count(); context.record("dwarf066r2.bank", receipt+"; actualWarehouse="+player.getWarehouse().getInventoryItemCount(receipt.request().itemId(), -1)+"; Adena="+player.getAdena()); }
					final var child = intents.intent(id).map(value -> value.goal()).orElse(null);
					if (!returned && banked && child != null && child.status() == PhantomGoalStatus.COMPLETED)
					{ returned = true; returnKills = sample.killSequence(); returnExp = sample.expGained(); returnSp = sample.spGained(); context.record("dwarf066r2.returned", child+"; position="+player.getX()+','+player.getY()+','+player.getZ()); }
					done = returned && sample.killSequence() > returnKills && sample.expGained() > returnExp && sample.spGained() > returnSp;
					if (now >= nextPrint)
					{
						nextPrint = now+TimeUnit.SECONDS.toNanos(30);
						System.out.println("DWARF066R2_OBSERVE profile="+id+"; native="+sample+"; swept="+swept+"; banked="+banked+"; returned="+returned+"; money="+player.getAdena()+"; materials="+materials+"; HP="+player.getCurrentHp()+'/'+player.getMaxHp()+"; MP="+player.getCurrentMp()+'/'+player.getMaxMp()+"; autoSkills="+player.getAutoUseSettings().getAutoSkills()+"; continuation="+continuation+"; XYZ="+player.getX()+','+player.getY()+','+player.getZ()+"; supply="+supplies.automaticIntent(id)+"; reason="+supplies.reason(id));
					}
					PhantomAssertions.assertTrue(!player.isDead() && player.getNativeWorkOwner().nativeObservationHealthy(), "Native dwarf died or its owner failed; no rescue.");
					PhantomAssertions.assertTrue(!sample.overflow(), "Native dwarf evidence became unproven: "+sample.firstUnprovenReason()+"; stop instead of repeating a fixture.");
					Thread.sleep(100);
				}
				context.record("dwarf066r2.result", "swept="+swept+"; banked="+banked+"; returned="+returned+"; nextUsefulKill="+done+"; native="+nativeEvidence.snapshot());
				PhantomAssertions.assertTrue(done, "Linked native Spoil/Sweep/material -> lawful bank -> return/new useful kill not complete within12min; no fixture loop.");
			}
			finally
			{
				engine.beginStop(); final long drain = System.nanoTime()+TimeUnit.SECONDS.toNanos(20); while (!engine.finishStop() && System.nanoTime() < drain) { Thread.sleep(20); }
				autoPlay.stop(id); supplies.beginStop(); PhantomAssertions.assertTrue(supplies.finishStop(), "Dwarf supply did not drain.");
				commerce.beginStop(); PhantomAssertions.assertTrue(commerce.finishStop(), "Dwarf commerce did not drain.");
				reservations.shutdown(System.currentTimeMillis()); org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyConflictPort.uninstall(reservations);
				if (player != null) { player.abortAttack(); player.abortCast(); player.stopMove(null); player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.IDLE); }
				navigation.beginStop(); while (!navigation.finishStop() && System.nanoTime() < drain) { Thread.sleep(20); }
				PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.shutdown().state(), "Native dwarf owner retained work.");
				background.beginStop(); PhantomAssertions.assertTrue(background.finishStop(), "Native dwarf background retained ownership.");
				context.record("dwarf066r2.cleanup", "ordinary owners drained; exact own TEST identity retained profile="+id+" object="+objectId+"; no reset/reseed");
			}
		}
	}

	private static PhantomRelevanceSignalPort signals()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
	}

}

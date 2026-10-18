/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.data.xml.NpcData;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.spawns.Spawn;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.background.L2jPhantomBackgroundAuthority;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCatchupStore;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundCompetitionRegistry;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundDecision;
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
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceService;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomSupplyIntentStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomCandidateRegistry;
import org.l2jmobius.gameserver.phantoms.decision.PhantomDecisionEngine;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoal;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateStore;
import org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStatus;
import org.l2jmobius.gameserver.phantoms.decision.PhantomStepHandlerRegistry;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.L2jProgressionBackend;
import org.l2jmobius.gameserver.phantoms.progression.L2jPhantomLearningJourney;
import org.l2jmobius.gameserver.phantoms.progression.PhantomAutonomousLearningPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionPolicy;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionService;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyQuery;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;

/** Finite premeasurement inputs, then the same ordinary farm/learning owners as PhantomSystem. */
final class PhantomLiving060Scenario
{
	private PhantomLiving060Scenario() { }

	static void run(PhantomTestContext context, PhantomHeadlessPlayerTestEnvironment environment, PhantomProfileRepository profiles,
			PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomProgressionCatalog catalog, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalogLoader.LoadResult commerceResult, int classId) throws Exception
	{
		PhantomCampaign058DatabaseLane.assertActiveFixture(context);
		PhantomAssertions.assertTrue(java.util.Set.of(0, 10, 25, 44, 53).contains(classId), "Unsupported declared Living060 starter input.");
		final boolean relocation = Boolean.getBoolean("phantom.test.livingRelocate060");
		final boolean supply = Boolean.getBoolean("phantom.test.livingSupply060");
		PhantomAssertions.assertTrue(!supply || !relocation, "Supply060 requires a separately declared scene.");
		PhantomAssertions.assertTrue(!relocation || classId == 0, "Relocation060 requires its separately declared Fighter input.");
		final boolean mage = classId == 10 || classId == 25, dwarf = classId == 53, orc = classId == 44;
		final var fixture = environment.createCampaign058Companion(context, mage ? 2 : 1);
		final var profile = profiles.create(fixture.objectId());
		final long id = profile.profileId();
		context.record("life060.identity." + classId, "fresh own060 native object=" + fixture.objectId() + "; profile=" + id + "; before input selection");
		int threshold = mage ? 7 : 5;
		final var goals = new PhantomGoalStateStore(profiles);
		final var commerce = commerceResult.catalog();
		final var routes = PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), topology);
		final var authority = new L2jPhantomBackgroundAuthority(() -> knowledge, () -> topology, () -> catalog, () -> commerce, routes);
		final var planner = new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority);
		final var transaction = new PhantomBackgroundTransaction();
		final Player setup = Player.load(fixture.objectId());
		PhantomAssertions.assertTrue(setup != null, "Living060 declared native actor load failed.");
		PhantomHistoricalBackgroundPlanner.Result initial;
		boolean prepared = false;
		try
		{
			setup.setBaseClass(classId); setup.setPlayerClass(classId);
			setup.setLearningClass(setup.getPlayerClass());
			PhantomAssertions.assertEquals(classId, setup.getLearningClass().getId(), "INVALID_INPUT: premeasurement learning class differs from the declared main class.");
			setup.getStat().setExp(setup.getStat().getExpForLevel(threshold) - 1); setup.getStat().setLevel((byte) (threshold - 1));
			setup.setSp(classId == 10 ? 239 : mage ? 259 : dwarf ? 309 : orc ? 59 : 49); setup.rewardSkills();
			if (supply)
			{
				setup.setSp(0);
				PhantomAssertions.assertTrue(setup.getInventory().addItem(ItemProcessType.REWARD, mage ? 2509 : 1835, 16, null, PhantomLiving060Scenario.class) != null, "Supply060 finite initial charges unavailable.");
				PhantomAssertions.assertTrue(setup.getInventory().addItem(ItemProcessType.REWARD, 57, 10_000, null, PhantomLiving060Scenario.class) != null, "Supply060 finite initial funds unavailable.");
				context.record("life060.supplyInput", "PREMEASUREMENT finite16 native compatible NG charges and10000 owned Adena; initial SP0; all later stock/funds changes through native combat and NPC purchase");
			}
			for (int itemId : mage ? new int[] {8, 24, 31} : new int[] {68, 24, 31})
			{
				final var item = setup.getInventory().addItem(ItemProcessType.REWARD, itemId, 1, null, PhantomLiving060Scenario.class);
				PhantomAssertions.assertTrue(item != null, "Living060 finite owned gear missing."); setup.getInventory().equipItem(item);
			}
			setup.setRunning(); setup.standUp(); setup.setInvul(false); setup.refreshExpertisePenalty(); setup.refreshOverloaded();
			final var origin = topology.findAnchor(classId == 10 ? "population.farming.human-mystic.20481" : mage ? "population.farming.elf.20534" : dwarf ? "population.farming.dwarf.20533" : orc ? "population.farming.orc.20535" : "population.farming.human-fighter.20545").orElseThrow();
			setup.setXYZInvisible(origin.point().x(), origin.point().y(), GeoEngine.getInstance().getHeight(origin.point().x(), origin.point().y(), origin.point().z()));
			initial = relocation ? PhantomRelocation060Input.selectLocal(context, setup, id, knowledge, topology, authority, planner, routes) : selectInput(context, setup, id, knowledge, topology, authority, planner, commerce);
			if (relocation) { threshold = setup.getLevel() + 1; }
			setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.setCurrentCp(setup.getMaxCp());
			goals.insert(id, initial.goal()); setup.storeMe();
			final var captured = authority.captureOwnedNative(id, setup, initial.goal(), null);
			final var saved = transaction.captureBaseline(captured.state(), initial.goal(), captured.context());
			PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, saved.status(), "Living060 native baseline rejected: " + saved);
			final long minute = System.currentTimeMillis() / 60_000;
			new PhantomBackgroundCatchupStore(profiles, goals).claim(id, new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.COMPLETE,
					"living060-" + id, context.seed(), minute - 1, minute, minute, 1, 0, 1, initial.generation().knowledgeGeneration(), initial.generation().topologyGeneration(),
					initial.goal().goalId(), initial.goal().revision(), initial.planIdentity(), 1, authority.hashes(), ""));
			context.record("life060.input." + classId, "PREMEASUREMENT native actor=" + fixture.objectId() + "; profile=" + id + "; class=" + classId + "; near level=" + threshold + "; SP=" + setup.getSp() + "; finite NG gear; initial root selected by historical planner=" + initial.goal() + "; current-history fixture cursor only, no historical reward; no subsequent test goal/resource/XYZ write");
			prepared = true;
		}
		finally
		{
			environment.cleanupLoadedPlayer(setup);
			if (!prepared)
			{
				final var owned = profiles.find(id).orElseThrow();
				PhantomAssertions.assertEquals(fixture.objectId(), owned.characterObjectId(), "Living060 preparation identity drift; cleanup refused.");
				profiles.delete(id, owned.rowVersion());
				context.record("life060.inputCleanup." + classId, "exact fresh profile=" + id + "; object=" + fixture.objectId() + "; no scene/owners enrolled; ordinary setup Player drained");
			}
		}

		final var metrics = new PhantomMetrics();
		final var lifecycle = new PhantomMaterializationLifecycleBridge();
		final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1, _ -> {}, lifecycle, 5_000, 10_000);
		final var commerceGoals = new PhantomSupplyIntentStore(profiles, goals);
		final var receipts = new PhantomCommerceReceiptStore(profiles);
		final var commerceService = new PhantomCommerceService(commerceResult, receipts, commerceGoals, new L2jCommerceBackend(materialization, commerce, java.time.Clock.systemDefaultZone()));
		final var background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transaction, authority, new PhantomBackgroundCompetitionRegistry(), signals(), () -> materialization);
		final var history = new PhantomHistoricalBackgroundService(profiles, goals, planner, background, materialization);
		final var navigation = new PhantomNavigationService(metrics);
		final var engineRef = new AtomicReference<PhantomDecisionEngine>();
		final var autoPlay = new PhantomVisibleAutoPlay(materialization, engineRef::get, history::permitsDecision);
		final var travel = new PhantomVisibleFarmTravel(materialization, background, routes, navigation, history::permitsDecision, signals(), history::recordVisibleTravelFailure, System::nanoTime);
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, Path.of("."), () -> knowledge), PhantomProgressionPolicy.productionDefaults());
		final var journey = new L2jPhantomLearningJourney(goals, materialization, progression, navigation, () -> knowledge, history::permitsDecision);
		journey.enableAutonomous(profiles, autoPlay::yieldAfterCurrentTarget, PhantomAutonomousLearningPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-learning-v1.xml")));
		final var supplies = new L2jPhantomSupplyJourney(commerceGoals, goals, materialization, commerceService, receipts, navigation, () -> knowledge, routes, history::permitsDecision, autoPlay::yieldAfterCurrentTarget, journey::ownsAutomaticIntent, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")));
		journey.deferAutomaticWhen(supplies::needsOrOwns);
		history.bindVisibleIntent((profileId, root) -> journey.ownsAutomaticIntent(profileId, root) || supplies.ownsAutomaticIntent(profileId, root));
		lifecycle.install(PhantomMaterializationLifecyclePort.chain(supplies, PhantomMaterializationLifecyclePort.chain(PhantomMaterializationLifecyclePort.chain(history, background), PhantomMaterializationLifecyclePort.chain(autoPlay, travel))));
		final var binding = PhantomBackgroundDecision.bindVisibleLife(background, travel, autoPlay, history, engineRef::get);
		final var candidates = new PhantomCandidateRegistry(); binding.registerCandidates(candidates); journey.registerCandidates(candidates); supplies.registerCandidates(candidates); candidates.seal();
		final var handlers = new PhantomStepHandlerRegistry(); binding.registerHandlers(handlers); journey.registerHandlers(handlers); supplies.registerHandlers(handlers); handlers.seal();
		final var engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1, null, history::permitsDecision); engineRef.set(engine);
		final List<Monster> monsters = new ArrayList<>();
		Player player = null;
		boolean earnedChainCompleted = false;
		boolean supplyChainCompleted = false;
		try
		{
			PhantomAssertions.assertTrue(background.start() && materialization.start() && navigation.start() && commerceService.start() && journey.start() && supplies.start(), "Living060 ordinary owners did not start.");
			progression.start();
			engine.start(); engine.attach(id);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(id).status(), "Living060 ordinary admission rejected.");
			player = World.getInstance().getPlayer(fixture.objectId());
			PhantomAssertions.assertTrue(player != null && !player.isDead() && !player.isImmobilized() && !player.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE), "INVALID_INPUT: Living060 native mobility/zone.");
			final int fixtureCount = relocation || classId != 0 ? 1 : 8;
			context.record("life060.finitePack." + classId, "PREMEASUREMENT native full-HP NPC=" + initial.spec().npcId() + "; fixture slots=" + fixtureCount + "; actual clanHelpRange=" + NpcData.getInstance().getTemplate(initial.spec().npcId()).getClanHelpRange() + "; native clan/aggro/AI unchanged; no later test spawns/resources");
			for (int index = 0; index < fixtureCount; index++)
			{
				final var spawn = new Spawn(NpcData.getInstance().getTemplate(initial.spec().npcId()));
				final int x = player.getX() + 60 + index * 35, y = player.getY();
				spawn.setXYZ(x, y, GeoEngine.getInstance().getHeight(x, y, player.getZ()));
				final var monster = (Monster) spawn.doSpawn(false); spawn.stopRespawn(); monsters.add(monster);
				PhantomAssertions.assertTrue(monster != null && monster.getCurrentHp() >= monster.getMaxHp() * .99 && !monster.isCoreAIDisabled(), "Living060 requires full stock finite Monsters/AI.");
			}
			final long startExp = player.getExp(), startSp = player.getSp();
			final var sensor = player.getNativeWorkOwner().evidence();
			final var baseline = sensor.snapshot();
			final var relocationProof = relocation ? new PhantomRelocation060Input.Observation(initial.goal(), player, threshold, 200) : null;
			final var supplyProof = supply ? new PhantomSupply060Observation(profiles, id, player, baseline, mage ? 2509 : 1835) : null;
			long tick = 0, nextReport = 0, completedAtCycle = -1;
			long completedExp = -1, completedKills = -1, completedDamage = -1, completedEarnedExp = -1, completedEarnedSp = -1;
			PhantomGoal completed = null;
			final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(8);
			while (System.nanoTime() < deadline)
			{
				final long now = System.nanoTime();
				PhantomAssertions.assertTrue(!player.isDead(), "Living060 actor died; no rescue permitted.");
				if (history.prepareVisibleDecision(id, engine)) { engine.accept(new PhantomActivityWorkItem(id, PhantomActivityState.ACTIVE, 1, ++tick, now, PhantomActivityOverloadLevel.NORMAL)); }
				final var intent = journey.automaticIntent(id).orElse(null);
				final var sample = sensor.snapshot();
				if (relocationProof != null && relocationProof.advance(context, id, player, goals.load(id).orElseThrow().goal(), background, history, travel, sample, now)) { break; }
				if (supplyProof != null && supplyProof.advance(context, player, sample, now)) { break; }
				if (intent != null && intent.status() == PhantomGoalStatus.COMPLETED && completed == null)
				{
					completed = intent; completedAtCycle = sample.farmCycleSequence(); completedExp = player.getExp(); completedKills = sample.killSequence(); completedDamage = sample.damageSequence(); completedEarnedExp = sample.expGained(); completedEarnedSp = sample.spGained();
				}
				if (!supply && completed != null && sample.killSequence() > completedKills && sample.damageSequence() > completedDamage && sample.expGained() > completedEarnedExp && sample.spGained() > completedEarnedSp && player.getExp() > completedExp && player.getLevel() >= threshold) { break; }
				if (now >= nextReport)
				{
					System.out.println("LIFE060_COMPOSED class=" + classId + "; xyz=" + player.getX() + ',' + player.getY() + ',' + player.getZ() + "; level=" + player.getLevel() + "; EXP=" + player.getExp() + "; SP=" + player.getSp() + "; cycles=" + (sample.farmCycleSequence() - baseline.farmCycleSequence()) + "; intent=" + intent + "; runtime=" + engine.find(id) + "; travel=" + travel.reason(id));
					System.out.flush(); nextReport = now + TimeUnit.SECONDS.toNanos(20);
					if (supply) { System.out.println("LIFE060_SUPPLY intent=" + supplies.automaticIntent(id) + "; reason=" + supplies.reason(id) + "; GK=" + supplies.gatekeeperEvidence(id)); }
				}
				if (intent != null && intent.status() == PhantomGoalStatus.FAILED) { throw new AssertionError("Living060 product intent failed: " + intent); }
				Thread.sleep(100);
			}
			final var sample = sensor.snapshot();
			if (supply) { context.record("life060.supplyJourney", "intent=" + supplies.automaticIntent(id) + "; reason=" + supplies.reason(id) + "; GK=" + supplies.gatekeeperEvidence(id)); }
			if (relocationProof != null) { relocationProof.verify(context, player, sample); }
			else if (supplyProof != null) { supplyProof.verify(context, player, sample); supplyChainCompleted = true; }
			else
			{
				context.record("life060.result." + classId, "EXP=" + startExp + "->" + player.getExp() + "; SP input=" + startSp + "; SP current=" + player.getSp() + "; cycles=" + (sample.farmCycleSequence() - baseline.farmCycleSequence()) + "; completed=" + completed + "; cyclesAfterReturn=" + (completedAtCycle < 0 ? 0 : sample.farmCycleSequence() - completedAtCycle) + "; newKills=" + (sample.killSequence() - completedKills) + "; newDamage=" + (sample.damageSequence() - completedDamage) + "; postReturnEXP=" + completedExp + "->" + player.getExp() + "; postReturnEarnedEXP=" + (sample.expGained() - completedEarnedExp) + "; postReturnEarnedSP=" + (sample.spGained() - completedEarnedSp) + "; actualXYZ=" + player.getX() + ',' + player.getY() + ',' + player.getZ() + "; current root=" + goals.load(id));
				PhantomAssertions.assertTrue(completed != null && sample.killSequence() > completedKills && sample.damageSequence() > completedDamage && sample.expGained() > completedEarnedExp && sample.spGained() > completedEarnedSp && player.getLevel() >= threshold && player.getExp() > completedExp, "Living060 production AutoPlay -> earned threshold -> selected paid lesson -> WALK return -> NEW native kill/earned EXP/SP did not complete; old-cycle closure cannot prove it.");
				earnedChainCompleted = true;
			}
		}
		finally
		{
			engine.beginStop(); final long drain = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (engine.snapshot().state() != PhantomDecisionEngine.State.STOPPED && !engine.finishStop() && System.nanoTime() < drain) { Thread.sleep(20); }
			autoPlay.stop(id); journey.beginStop(); journey.finishStop();
			supplies.beginStop(); PhantomAssertions.assertTrue(supplies.finishStop(), "Living060 supply owner did not drain.");
			commerceService.beginStop(); PhantomAssertions.assertTrue(commerceService.finishStop(), "Living060 commerce owner did not drain.");
			if (player != null) { player.abortAttack(); player.abortCast(); player.stopMove(null); player.getAI().setIntention(org.l2jmobius.gameserver.ai.Intention.IDLE); }
			for (var monster : monsters) { if (monster != null && monster.isSpawned()) { monster.deleteMe(); } }
			navigation.beginStop(); while (navigation.snapshot().state() != PhantomNavigationService.ServiceState.STOPPED && !navigation.finishStop() && System.nanoTime() < drain) { Thread.sleep(20); }
			progression.beginStop(); progression.finishStop();
			final long expectedSupplyExp = player == null ? -1 : player.getExp(), expectedSupplySp = player == null ? -1 : player.getSp();
			final long expectedSupplyAdena = player == null ? -1 : player.getAdena(), expectedSupplyStock = player == null ? -1 : player.getInventory().getInventoryItemCount(mage ? 2509 : 1835, -1);
			PhantomAssertions.assertEquals(PhantomMaterializationService.ServiceState.STOPPED, materialization.shutdown().state(), "Living060 materialization cleanup retained native work.");
			if (supplyChainCompleted)
			{
				final Player reloaded = Player.load(fixture.objectId());
				PhantomAssertions.assertTrue(reloaded != null, "Supply060 saved native actor disappeared.");
				try
				{
					PhantomAssertions.assertEquals(expectedSupplyExp, reloaded.getExp(), "Supply060 earned EXP changed on native save/reload.");
					PhantomAssertions.assertEquals(expectedSupplySp, reloaded.getSp(), "Supply060 earned SP changed on native save/reload.");
					PhantomAssertions.assertEquals(expectedSupplyAdena, reloaded.getAdena(), "Supply060 paid Adena changed on native save/reload.");
					PhantomAssertions.assertEquals(expectedSupplyStock, reloaded.getInventory().getInventoryItemCount(mage ? 2509 : 1835, -1), "Supply060 purchased/consumed stock changed on native save/reload.");
					final var restored = new PhantomSupplyIntentStore(profiles, goals);
					PhantomAssertions.assertEquals(PhantomGoalStatus.COMPLETED, restored.intent(id).orElseThrow().goal().status(), "Supply060 terminal intent was lost.");
					PhantomAssertions.assertTrue(restored.load(id).isEmpty(), "Supply060 completed intent reauthorized a payment.");
					context.record("life060.supplySaved", "ordinary owned save/native Player.load; EXP=" + reloaded.getExp() + "; SP=" + reloaded.getSp() + "; Adena=" + reloaded.getAdena() + "; stock=" + expectedSupplyStock + "; terminal child retained/Commerce authorization empty; not whole-JVM resume");
				}
				finally { environment.cleanupLoadedPlayer(reloaded); }
			}
			if (earnedChainCompleted)
			{
				final var learned = new org.l2jmobius.gameserver.phantoms.decision.PhantomGoalStateCodec().decode(profiles.findComponent(id, "learning.intent").orElseThrow().payload());
				final int skillId = Integer.parseInt(learned.target().key().split(":")[0]);
				final Player reloaded = Player.load(fixture.objectId());
				PhantomAssertions.assertTrue(reloaded != null, "Living060 ordinary saved Player disappeared.");
				try
				{
					PhantomAssertions.assertEquals(player.getExp(), reloaded.getExp(), "Living060 earned EXP changed on native save/reload.");
					PhantomAssertions.assertEquals(player.getSp(), reloaded.getSp(), "Living060 earned/paid SP changed on native save/reload.");
					PhantomAssertions.assertEquals(player.getSkillLevel(skillId), reloaded.getSkillLevel(skillId), "Living060 paid native rank was downgraded on save/reload.");
					context.record("life060.saved." + classId, "ordinary owned save/cleanup -> native Player.load; EXP=" + reloaded.getExp() + "; SP=" + reloaded.getSp() + "; paid skill=" + skillId + ':' + reloaded.getSkillLevel(skillId) + "; no recreated goal/reward; not a whole JVM restart");
				}
				finally { environment.cleanupLoadedPlayer(reloaded); }
			}
			background.beginStop(); PhantomAssertions.assertTrue(background.finishStop(), "Living060 background owner retained work.");
			final var owned = profiles.find(id).orElseThrow();
			PhantomAssertions.assertEquals(fixture.objectId(), owned.characterObjectId(), "Living060 fresh profile ownership changed; cleanup refused.");
			profiles.delete(id, owned.rowVersion());
			context.record("life060.cleanup." + classId, "native owners drained; exact fresh profile=" + id + "; object=" + fixture.objectId() + "; no accounts/old orphans deleted");
		}
	}

	/** Select another lawful scene BEFORE enrollment, only after proving the changed route cause. */
	private static PhantomHistoricalBackgroundPlanner.Result selectInput(PhantomTestContext context, Player setup, long id, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, L2jPhantomBackgroundAuthority authority, PhantomHistoricalBackgroundPlanner planner, org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog commerce)
	{
		final boolean supplies = Boolean.getBoolean("phantom.test.livingSupply060");
		final var declaredRegion = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(setup.getX(), setup.getY(), setup.getZ(), 0);
		final var supplyNpcs = supplies ? commerce.buyOffers().stream().filter(offer -> offer.price() > 0 && !offer.limitedStock() && L2jPhantomSupplyJourney.compatible(setup, org.l2jmobius.gameserver.data.xml.ItemData.getInstance().getTemplate(offer.itemId()))).flatMap(offer -> offer.npcIds().stream()).collect(java.util.stream.Collectors.toSet()) : java.util.Set.<Integer>of();
		final var trainers = knowledge.snapshot().spawnFactsByNpc().entrySet().stream().filter(entry ->
		{
			final var template = NpcData.getInstance().getTemplate(entry.getKey());
			return template != null && (supplies ? "Merchant".equals(template.getType()) && supplyNpcs.contains(entry.getKey()) : "Trainer".equals(template.getType()) && template.canTeach(setup.getLearningClass()));
		}).flatMap(entry -> entry.getValue().stream()).filter(spawn -> spawn.instanceId() == 0 && spawn.pointKind() == org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnPointKind.EXACT).toList();
		PhantomAssertions.assertTrue(!trainers.isEmpty(), "INVALID_INPUT: no native compatible service NPC facts.");
		final var eligibleNodes = knowledge.snapshot().spawnAreasByNpc().entrySet().stream().filter(entry ->
		{
			final var npc = NpcData.getInstance().getTemplate(entry.getKey()); return npc != null && npc.getLevel() >= 1 && npc.getLevel() <= setup.getLevel() + 2;
		}).flatMap(entry -> entry.getValue().stream()).map(area -> area.topologyNodeId()).collect(java.util.stream.Collectors.toSet());
		final var geo = GeoEngine.getInstance();
		final var anchors = topology.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(anchor -> anchor.role() == PhantomTopologyAnchorRole.FARMING && anchor.point().instanceId() == 0 && eligibleNodes.contains(anchor.nodeId()))
				.filter(anchor -> !supplies || Math.hypot((long) anchor.point().x() - declaredRegion.x(), (long) anchor.point().y() - declaredRegion.y()) <= org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults().maximumLocalStraightDistance())
				.filter(anchor -> geo.hasGeo(anchor.point().x(), anchor.point().y()) && org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(anchor.point().x(), anchor.point().y(), geo.getHeight(anchor.point().x(), anchor.point().y(), anchor.point().z()), org.l2jmobius.gameserver.model.zone.type.PeaceZone.class) == null
						&& org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(anchor.point().x(), anchor.point().y(), geo.getHeight(anchor.point().x(), anchor.point().y(), anchor.point().z()), org.l2jmobius.gameserver.model.zone.type.WaterZone.class) == null)
				.sorted(java.util.Comparator.comparingDouble((PhantomTopologyAnchor anchor) -> trainers.stream().mapToDouble(spawn -> Math.hypot(anchor.point().x() - spawn.x(), anchor.point().y() - spawn.y())).min().orElseThrow()).thenComparing(PhantomTopologyAnchor::id)).limit(32).toList();
		final var backend = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
		final var policy = org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy.productionDefaults();
		final var rejected = new ArrayList<String>();
		final var stages = new java.util.TreeMap<String, Integer>();
		for (var candidate : anchors)
		{
			if (!geo.hasGeo(candidate.point().x(), candidate.point().y())) { continue; }
			setup.setXYZInvisible(candidate.point().x(), candidate.point().y(), geo.getHeight(candidate.point().x(), candidate.point().y(), candidate.point().z()));
			setup.revalidateZone(true);
			if (setup.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE) || setup.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.WATER)) { continue; }
			final PhantomHistoricalBackgroundPlanner.Result local;
			try
			{
				final var seed = planner.planInitial(id, setup, context.seed() + setup.getActiveClass(), 0);
				if (!seed.ready()) { stages.merge("seed." + seed.reasonKey(), 1, Integer::sum); continue; }
				final var observed = authority.captureOwnedNative(id, setup, seed.goal(), null).state();
				local = planner.replanVisibleLocal(id, observed, seed.goal(), context.seed(), 1, new PhantomTopologyPoint(setup.getX(), setup.getY(), setup.getZ(), 0), java.util.Set.of(), java.util.Set.of());
			}
			catch (IllegalArgumentException rejectedPoint)
			{
				if (!"Canonical Player position does not identify exactly one topology anchor.".equals(rejectedPoint.getMessage())) { throw rejectedPoint; }
				stages.merge("ambiguous_seed", 1, Integer::sum); continue;
			}
			if (!local.ready()) { stages.merge("local." + local.reasonKey(), 1, Integer::sum); continue; }
			for (var from : inputPoints(topology, local.spec().anchorId()))
			{
				final var trainer = trainers.stream().min(java.util.Comparator.comparingDouble((org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact spawn) -> Math.sqrt(Math.pow(from.x() - spawn.x(), 2) + Math.pow(from.y() - spawn.y(), 2) + Math.pow(from.z() - spawn.z(), 2))).thenComparingInt(org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeModel.SpawnFact::npcId)).orElseThrow();
				if (Math.hypot(from.x() - trainer.x(), from.y() - trainer.y()) > policy.maximumLocalStraightDistance()) { stages.merge("trainer.too_far", 1, Integer::sum); continue; }
				final double angle = Math.atan2(from.y() - trainer.y(), from.x() - trainer.x());
				final int x = trainer.x() + (int) Math.round(112 * Math.cos(angle)), y = trainer.y() + (int) Math.round(112 * Math.sin(angle));
				final var to = new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(x, y, geo.getHeight(x, y, trainer.z()), 0);
				final long now = System.nanoTime();
				final var outbound = backend.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(id, from, to, now, now + TimeUnit.SECONDS.toNanos(2), 50_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), policy, System::nanoTime);
				final long backNow = System.nanoTime();
				final var inbound = backend.findWalkCorridor(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest(id, to, from, backNow, backNow + TimeUnit.SECONDS.toNanos(2), 50_000), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken(), policy, System::nanoTime);
				if (outbound.status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND || inbound.status() != org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner.Status.FOUND)
				{
					if (rejected.size() < 8) { rejected.add(local.spec().anchorId() + ':' + outbound.status() + '/' + inbound.status()); } continue;
				}
				setup.setXYZInvisible(from.x(), from.y(), from.z()); setup.revalidateZone(true);
				if (setup.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.WATER) || setup.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE)) { continue; }
				try { authority.captureOwnedNative(id, setup, local.goal(), null); }
				catch (IllegalArgumentException rejectedPoint)
				{
					if (!"Canonical Player position does not identify exactly one topology anchor.".equals(rejectedPoint.getMessage())) { throw rejectedPoint; }
					stages.merge("ambiguous_point", 1, Integer::sum); continue;
				}
				context.record("life060.routePreflight." + setup.getActiveClass(), "PREMEASUREMENT bounded32 farm candidates; local production planner=" + local.goal() + "; native service=" + (supplies ? "Merchant" : "Trainer") + trainer.npcId() + "; declared starting region=" + declaredRegion + "; outbound=" + outbound.status() + "; inbound=" + inbound.status() + "; prior rejected=" + rejected);
				System.out.println("LIFE060 INPUT_ROUTE_GREEN class=" + setup.getActiveClass() + "; farm=" + local.spec() + "; service=" + (supplies ? "Merchant" : "Trainer") + trainer.npcId() + "; outbound/inbound native corridor FOUND");
				return local;
			}
		}
		context.record("life060.routePreflightRejected." + setup.getActiveClass(), "trainers=" + trainers.size() + "; eligibleNodes=" + eligibleNodes.size() + "; factual anchors=" + anchors.size() + "; stages=" + stages + "; native rejected=" + rejected);
		throw new AssertionError("BLOCKED_NATIVE_ROUTE_INPUT: bounded32 factual farm points have no bidirectional native service NPC corridor; no full scene started.");
	}

	static List<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint> inputPoints(PhantomTopologyQuery topology, String anchorId)
	{
		final var anchor = topology.findAnchor(anchorId).orElseThrow();
		final var area = topology.findNode(anchor.nodeId()).orElseThrow().area();
		final var center = anchor.point();
		final var xy = new ArrayList<int[]>(); xy.add(new int[] {center.x(), center.y()});
		for (var vertex : area.vertices().stream().limit(8).toList()) { xy.add(new int[] {(3 * vertex.x() + center.x()) / 4, (3 * vertex.y() + center.y()) / 4}); }
		final var result = new ArrayList<org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint>();
		final var geo = GeoEngine.getInstance();
		for (var point : xy)
		{
			if (!geo.hasGeo(point[0], point[1])) { continue; }
			final int z = geo.getHeight(point[0], point[1], center.z());
			if (!area.contains(new PhantomTopologyPoint(point[0], point[1], z, 0))) { continue; }
			if (org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(point[0], point[1], z, org.l2jmobius.gameserver.model.zone.type.PeaceZone.class) != null || org.l2jmobius.gameserver.managers.ZoneManager.getInstance().getZone(point[0], point[1], z, org.l2jmobius.gameserver.model.zone.type.WaterZone.class) != null) { continue; }
			result.add(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(point[0], point[1], z, 0));
		}
		return List.copyOf(result);
	}

	private static PhantomRelevanceSignalPort signals()
	{
		return new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String source, long sequence) { return SignalDelivery.ACCEPTED; }
		};
	}
}

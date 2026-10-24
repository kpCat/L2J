/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityOverloadLevel;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityWorkItem;
import org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceCatalog;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.PhantomProgressionCatalog;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** Actual Historical/Background/VisibleTravel composition; fixture writes precede its measurement marker. */
public final class PhantomFarm074OwnedNative
{
	private PhantomFarm074OwnedNative() { }

	public static void prepare(PhantomProfileRepository profiles, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology,
		PhantomProgressionCatalog progression, PhantomCommerceCatalog commerce, PhantomNormalGatekeeperTravel routes, Properties retained, StringBuilder report) throws Exception
	{
		check(PhantomTask074Lane.enabled() && !retained.containsKey("farm074.ownedProfile"), "Fresh OWN074 B fixture required.");
		try (var connection = DatabaseFactory.getConnection())
		{
			check(PhantomTask074Lane.TEST.equals(connection.getCatalog()), "OWN074 TEST schema required.");
			try (var query = connection.prepareStatement("SELECT COUNT(*) FROM accounts WHERE login='task074farm'"); var rows = query.executeQuery()) { rows.next(); check(rows.getInt(1) == 0, "Existing B account is preserved; no reseed."); }
			try (var insert = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task074farm','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { check(insert.executeUpdate() == 1, "Fresh B account absent."); }
		}
		final var setup = Player.create(PlayerTemplateData.getInstance().getTemplate(PlayerClass.FIGHTER), "task074farm", "Farm074", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
		check(setup != null && !setup.isGM(), "Fresh native B player absent.");
		try
		{
			setup.stopAllTasks();
			final var origin = topology.findAnchor("population.farming.human-fighter.20545").orElseThrow();
			final var initialPoint = origin.point();
			check(GeoEngine.getInstance().hasGeo(initialPoint.x(), initialPoint.y()), "B initial physical Geo absent.");
			PlayerCreationInitializer.initialize(setup, PlayerCreationInitializer.Mode.POPULATION, new org.l2jmobius.gameserver.model.Location(initialPoint.x(), initialPoint.y(), GeoEngine.getInstance().getHeight(initialPoint.x(), initialPoint.y(), initialPoint.z())));
			setup.setOnlineStatus(true, false);
			final long id = profiles.create(setup.getObjectId()).profileId();
			retained.setProperty("farm074.ownedProfile", Long.toString(id)); retained.setProperty("farm074.ownedActor", Integer.toString(setup.getObjectId()));
			final var authority = authority(knowledge, topology, progression, commerce, routes);
			final var planner = new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority);
			final var initial = planner.planInitial(id, setup, 740074, 1);
			check(initial.ready(), "Canonical level1 prior farm plan absent: " + initial.reasonKey());
			final var oldAnchor = topology.findAnchor(initial.spec().anchorId()).orElseThrow(); final var point = oldAnchor.point();
			check(GeoEngine.getInstance().hasGeo(point.x(), point.y()), "B old field Geo absent.");
			// This finite prior-history fixture deliberately retains a now obsolete level1 farm plan.
			setup.setXYZInvisible(point.x(), point.y(), GeoEngine.getInstance().getHeight(point.x(), point.y(), point.z()));
			setup.setPlayerClass(1); setup.setBaseClass(1); setup.getStat().setExp(setup.getStat().getExpForLevel(20)); setup.getStat().setLevel((byte) 20); setup.giveAvailableAutoGetSkills(); setup.rewardSkills();
			for (int itemId : new int[] {129, 352, 2378}) { setup.getInventory().equipItem(setup.addItem(ItemProcessType.REWARD, itemId, 1, setup, false)); }
			setup.addAdena(ItemProcessType.REWARD, 5000, setup, false); setup.setRunning(); setup.standUp(); setup.refreshExpertisePenalty(); setup.refreshOverloaded();
			setup.setCurrentHp(setup.getMaxHp()); setup.setCurrentMp(setup.getMaxMp()); setup.setCurrentCp(setup.getMaxCp()); setup.getInventory().updateDatabase(); setup.storeMe();
			final var goals = new PhantomGoalStateStore(profiles); goals.insert(id, initial.goal());
			final var capture = authority.captureOwnedNative(id, setup, initial.goal(), null);
			check(!planner.remainsSuitable(capture.state(), initial.goal()), "Predeclared old plan did not become genuinely unsuitable at level20.");
			final var baseline = new PhantomBackgroundTransaction().captureBaseline(capture.state(), initial.goal(), capture.context()); check(baseline.successful(), "B baseline capture rejected: " + baseline.status());
			final long minute = System.currentTimeMillis() / 60_000;
			new PhantomBackgroundCatchupStore(profiles, goals).claim(id, new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.COMPLETE, "farm074-prior-" + id, 740074, minute - 1, minute, minute, 1, 0, 1, initial.generation().knowledgeGeneration(), initial.generation().topologyGeneration(), initial.goal().goalId(), initial.goal().revision(), initial.planIdentity(), 1, authority.hashes(), ""));
			retained.setProperty("farm074.ownedOldGoal", initial.goal().toString()); retained.setProperty("farm074.ownedOldNpc", Integer.toString(initial.spec().npcId())); retained.setProperty("farm074.ownedOldAnchor", initial.spec().anchorId());
			report.append("B_OWNED_PREMEASUREMENT\tprofile=").append(id).append(";actor=").append(setup.getObjectId()).append(";priorLevel1Plan=").append(initial.goal()).append(";declaredLevel20Gear129_352_2378_wallet5000;actual=").append(setup.getLocation()).append(";remainsSuitable=false;historyCursor=PREDECLARED_CURRENT_FIXTURE;historicalRewards=0;forcedExclusions=0\n");
		}
		finally { setup.stopAllTasks(); setup.storeMe(); setup.deleteMe(); }
	}

	public static void run(PhantomProfileRepository profiles, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology,
		PhantomProgressionCatalog progression, PhantomCommerceCatalog commerce, PhantomNormalGatekeeperTravel routes, Properties retained, StringBuilder report, boolean resume) throws Exception
	{
		check(PhantomTask074Lane.enabled(), "Explicit OWN074 lane required.");
		final long id = Long.parseLong(retained.getProperty("farm074.ownedProfile")); final int objectId = Integer.parseInt(retained.getProperty("farm074.ownedActor"));
		check(profiles.find(id).orElseThrow().characterObjectId() == objectId, "Retained B profile/actor changed.");
		final var authority = authority(knowledge, topology, progression, commerce, routes);
		try (var runtime = new Runtime(profiles, authority, knowledge, topology, routes, id))
		{
			final var beforeGoal = runtime.goals.load(id).orElseThrow(); final var beforeClaim = runtime.history.status(id).orElseThrow();
			if (resume)
			{
				check(beforeGoal.goal().toString().equals(retained.getProperty("farm074.ownedPersistedGoal")) && beforeClaim.state().planIdentity().equals(retained.getProperty("farm074.ownedPlanIdentity")), "B canonical plan/catchup changed across sameDB restart.");
				check(!Long.toString(ProcessHandle.current().pid()).equals(retained.getProperty("farm074.ownedJvm")), "B continuity requires a genuinely new JVM.");
			}
			check(runtime.materialization.materialize(id).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "B composed lifecycle failed native materialization.");
			final var player = World.getInstance().getPlayer(objectId); check(player != null && "task074farm".equals(player.getAccountName()) && !player.isGM(), "Exact OWN074 B player absent.");
			final var from = PhantomNativeTravelActions.point(player); final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(90); long tick = 0; String last = ""; boolean published = resume, moved = false;
			report.append("B_OWNED_MEASUREMENT_START\tresume=").append(resume).append(";profile=").append(id).append(";actor=").append(objectId).append(";from=").append(from).append(";goalRow=").append(beforeGoal.rowVersion()).append(";catchupRow=").append(beforeClaim.rowVersion()).append(";noFurtherFixtureWrites=true\n");
			while (System.nanoTime() < end && !player.isDead())
			{
				final boolean prepared = runtime.history.prepareVisibleDecision(id, runtime.engine);
				if (prepared) { runtime.engine.accept(new PhantomActivityWorkItem(id, PhantomActivityState.ACTIVE, 1, ++tick, System.nanoTime(), PhantomActivityOverloadLevel.NORMAL)); }
				final var goal = runtime.goals.load(id).orElseThrow(); final var claim = runtime.history.status(id).orElseThrow(); final var current = PhantomNativeTravelActions.point(player);
				final var spec = PhantomBackgroundGoalSpec.parse(goal.goal());
				published |= goal.rowVersion() > beforeGoal.rowVersion() && claim.rowVersion() > beforeClaim.rowVersion() && goal.goal().revision() > beforeGoal.goal().revision()
					&& spec.npcId() != Integer.parseInt(retained.getProperty("farm074.ownedOldNpc")) && !spec.anchorId().equals(retained.getProperty("farm074.ownedOldAnchor"))
					&& claim.state().goalRevision() == goal.goal().revision() && claim.state().goalId() == goal.goal().goalId();
				moved |= from.distanceTo(current) >= 32;
				final String reason = runtime.history.visibleRecoveryReason(id) + '|' + runtime.travel.reason(id);
				if (!reason.equals(last)) { last = reason; report.append("B_OWNED_PROGRESS\tprepared=").append(prepared).append(";reason=").append(reason).append(";position=").append(current).append(";root=").append(goal.goal()).append(";catchup=").append(claim.state()).append('\n'); }
				if (published && moved)
				{
					retained.setProperty("farm074.ownedPersistedGoal", goal.goal().toString()); retained.setProperty("farm074.ownedPlanIdentity", claim.state().planIdentity()); retained.setProperty("farm074.ownedJvm", Long.toString(ProcessHandle.current().pid()));
					retained.setProperty("farm074.ownedStatus", "PASS_NATIVE_HISTORY_CAS_FIRST_SEGMENT");
					report.append("B_OWNED_RESULT\tstatus=PASS_NATIVE_HISTORY_CAS_FIRST_SEGMENT;resume=").append(resume).append(";oldGoalRow=").append(beforeGoal.rowVersion()).append(";newGoalRow=").append(goal.rowVersion()).append(";oldClaimRow=").append(beforeClaim.rowVersion()).append(";newClaimRow=").append(claim.rowVersion()).append(";from=").append(from).append(";actual=").append(current).append(";displacement=").append(from.distanceTo(current)).append(";intendedAnchor=").append(spec.anchorId()).append(";intendedNpc=").append(spec.npcId()).append(";nativeTravelReason=").append(runtime.travel.reason(id)).append(";NO_FULL_ARRIVAL_CLAIM\n");
					return;
				}
				Thread.sleep(100);
			}
			retained.setProperty("farm074.ownedStatus", "PARTIAL_NATIVE_HISTORY_OR_ROUTE");
			report.append("B_OWNED_RESULT\tstatus=PARTIAL_NATIVE_HISTORY_OR_ROUTE;publishedCAS=").append(published).append(";nativeMoved=").append(moved).append(";reason=").append(last).append(";dead=").append(player.isDead()).append('\n');
		}
	}

	private static L2jPhantomBackgroundAuthority authority(PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomProgressionCatalog progression, PhantomCommerceCatalog commerce, PhantomNormalGatekeeperTravel routes)
	{ return new L2jPhantomBackgroundAuthority(() -> knowledge, () -> topology, () -> progression, () -> commerce, routes); }

	private static final class Runtime implements AutoCloseable
	{
		final PhantomGoalStateStore goals; final PhantomMaterializationService materialization; final PhantomBackgroundService background;
		final PhantomHistoricalBackgroundService history; final PhantomVisibleAutoPlay autoPlay; final PhantomVisibleFarmTravel travel; final PhantomDecisionEngine engine; final PhantomNavigationService navigation; final long id;
		Runtime(PhantomProfileRepository profiles, L2jPhantomBackgroundAuthority authority, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomNormalGatekeeperTravel routes, long profileId)
		{
			id = profileId; goals = new PhantomGoalStateStore(profiles); final var metrics = new PhantomMetrics(); final var bridge = new PhantomMaterializationLifecycleBridge();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1, _ -> {}, bridge, 5000, 10000);
			final var signals = new PhantomRelevanceSignalPort()
			{
				@Override public SignalDelivery submit(long profile, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				@Override public SignalDelivery withdraw(long profile, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), new PhantomBackgroundTransaction(), authority, new PhantomBackgroundCompetitionRegistry(), signals, () -> materialization);
			history = new PhantomHistoricalBackgroundService(profiles, goals, new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority), background, materialization);
			navigation = new PhantomNavigationService(metrics); final var engineRef = new AtomicReference<PhantomDecisionEngine>();
			autoPlay = new PhantomVisibleAutoPlay(materialization, engineRef::get, history::permitsDecision);
			travel = new PhantomVisibleFarmTravel(materialization, background, authority.travelQuery(topology), navigation, history::permitsDecision, signals, history::recordVisibleTravelFailure, System::nanoTime);
			bridge.install(PhantomMaterializationLifecyclePort.chain(travel, PhantomMaterializationLifecyclePort.chain(autoPlay, PhantomMaterializationLifecyclePort.chain(history, background))));
			final var binding = PhantomBackgroundDecision.bindVisibleLife(background, travel, autoPlay, history, engineRef::get);
			final var candidates = new PhantomCandidateRegistry(); binding.registerCandidates(candidates); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); binding.registerHandlers(handlers); handlers.seal();
			engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1, null, history::permitsDecision); engineRef.set(engine);
			check(background.start() && navigation.start() && materialization.start(), "B ordinary owner start rejected."); engine.start(); check(engine.attach(id) == PhantomDecisionEngine.AttachResult.ATTACHED, "B ordinary Decision attach rejected.");
		}
		@Override public void close() throws Exception
		{
			engine.beginStop(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!engine.finishStop() && System.nanoTime() < deadline) { Thread.sleep(20); }
			autoPlay.stop(id); navigation.beginStop(); while (!navigation.finishStop() && System.nanoTime() < deadline) { Thread.sleep(20); }
			check(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "B native materialization did not store/drain."); background.beginStop(); check(background.finishStop(), "B Background owner did not stop.");
		}
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

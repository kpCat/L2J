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
import org.l2jmobius.gameserver.phantoms.commerce.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeQuery;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationService;
import org.l2jmobius.gameserver.phantoms.player.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.*;
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
	{ run(profiles, knowledge, topology, progression, commerce, routes, retained, report, resume, false); }

	public static void run(PhantomProfileRepository profiles, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology,
		PhantomProgressionCatalog progression, PhantomCommerceCatalog commerce, PhantomNormalGatekeeperTravel routes, Properties retained, StringBuilder report, boolean resume, boolean supplyControl) throws Exception
	{
		check(PhantomTask074Lane.enabled(), "Explicit OWN074 lane required.");
		if (supplyControl)
		{
			check(!retained.containsKey("farm074.supplyControlStarted"), "One causal supply-composition control only; preserve its result.");
			retained.setProperty("farm074.supplyControlStarted", Long.toString(System.currentTimeMillis()));
		}
		final long id = Long.parseLong(retained.getProperty("farm074.ownedProfile")); final int objectId = Integer.parseInt(retained.getProperty("farm074.ownedActor"));
		check(profiles.find(id).orElseThrow().characterObjectId() == objectId, "Retained B profile/actor changed.");
		final var authority = authority(knowledge, topology, progression, commerce, routes);
		try (var runtime = new Runtime(profiles, authority, knowledge, topology, routes, id, report, supplyControl))
		{
			final var beforeGoal = runtime.goals.load(id).orElseThrow(); final var beforeClaim = runtime.history.status(id).orElseThrow();
			if (resume)
			{
				check(beforeGoal.goal().toString().equals(retained.getProperty("farm074.ownedPersistedGoal")) && beforeClaim.state().planIdentity().equals(retained.getProperty("farm074.ownedPlanIdentity")), "B canonical plan/catchup changed across sameDB restart.");
				check(!Long.toString(ProcessHandle.current().pid()).equals(retained.getProperty("farm074.ownedJvm")), "B continuity requires a genuinely new JVM.");
			}
			final var materialized = runtime.materialization.materialize(id);
			report.append("B_OWNED_MATERIALIZATION\t").append(materialized).append('\n');
			check(materialized.status() == PhantomMaterializationService.ResultStatus.SUCCESS, "B composed lifecycle failed native materialization: " + materialized);
			final var player = World.getInstance().getPlayer(objectId); check(player != null && "task074farm".equals(player.getAccountName()) && !player.isGM(), "Exact OWN074 B player absent.");
			final var from = PhantomNativeTravelActions.point(player); final long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(supplyControl ? 120 : 90); long tick = 0; String last = ""; boolean published = resume, moved = false;
			org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint publishedFrom = null;
			PhantomGoal walkingGoal = null;
			report.append("B_OWNED_MEASUREMENT_START\tresume=").append(resume).append(";profile=").append(id).append(";actor=").append(objectId).append(";from=").append(from).append(";goalRow=").append(beforeGoal.rowVersion()).append(";catchupRow=").append(beforeClaim.rowVersion()).append(";noFurtherFixtureWrites=true\n");
			if (supplyControl) { report.append("B_SUPPLY_COMPOSITION_CONTROL\tcause=prior B composition omitted production supply/equipment/visible-intent bridge;one bounded causal control;same actor;no fixture writes;A=").append(player.getAdena()).append(";weapon=").append(player.getActiveWeaponItem() == null ? 0 : player.getActiveWeaponItem().getId()).append(";inventory=").append(player.getInventory().getItems().stream().map(item -> item.getObjectId() + ":" + item.getId() + ":" + item.getCount() + ":" + item.getLocation()).toList()).append('\n'); }
			try { PhantomFarm074PlanningDiagnostic.append(id, player, runtime.background.acquisitionSnapshot(id).orElseThrow(), beforeGoal.goal(), beforeClaim.state(), new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority), runtime.travel, report); }
			catch (RuntimeException diagnosticFailure) { report.append("B_OWNED_PLANNING_DIAGNOSTIC_GAP\t").append(diagnosticFailure).append('\n'); }
			while (System.nanoTime() < end && !player.isDead())
			{
				final boolean prepared = runtime.history.prepareVisibleDecision(id, runtime.engine);
				if (prepared) { runtime.engine.accept(new PhantomActivityWorkItem(id, PhantomActivityState.ACTIVE, 1, ++tick, System.nanoTime(), PhantomActivityOverloadLevel.NORMAL)); }
				final var goal = runtime.goals.load(id).orElseThrow(); final var claim = runtime.history.status(id).orElseThrow(); final var current = PhantomNativeTravelActions.point(player);
				final var spec = PhantomBackgroundGoalSpec.parse(goal.goal());
				final boolean freshPublication = goal.rowVersion() > beforeGoal.rowVersion() && claim.rowVersion() > beforeClaim.rowVersion() && goal.goal().revision() > beforeGoal.goal().revision()
					&& spec.npcId() != Integer.parseInt(retained.getProperty("farm074.ownedOldNpc")) && !spec.anchorId().equals(retained.getProperty("farm074.ownedOldAnchor"))
					&& claim.state().goalRevision() == goal.goal().revision() && claim.state().goalId() == goal.goal().goalId();
				published |= freshPublication;
				final boolean nativeFarmWalking = published && "travel.native_walking".equals(runtime.travel.reason(id)) && player.isMoving()
					&& (runtime.supply == null || !runtime.supply.ownsAutomaticIntent(id, goal.goal()));
				if (nativeFarmWalking)
				{
					if (publishedFrom == null || !goal.goal().equals(walkingGoal)) { publishedFrom = current; walkingGoal = goal.goal(); }
					moved |= publishedFrom.distanceTo(current) >= 32;
				}
				else { publishedFrom = null; walkingGoal = null; }
				final String reason = runtime.history.visibleRecoveryReason(id) + '|' + runtime.travel.reason(id) + (runtime.supply == null ? "" : "|supply=" + runtime.supply.reason(id) + ";weapon=" + (player.getActiveWeaponItem() == null ? 0 : player.getActiveWeaponItem().getId()) + ";A=" + player.getAdena());
				if (!reason.equals(last)) { last = reason; report.append("B_OWNED_PROGRESS\tprepared=").append(prepared).append(";reason=").append(reason).append(";position=").append(current).append(";root=").append(goal.goal()).append(";catchup=").append(claim.state()).append('\n'); }
				if (published && moved)
				{
					retained.setProperty("farm074.ownedPersistedGoal", goal.goal().toString()); retained.setProperty("farm074.ownedPlanIdentity", claim.state().planIdentity()); retained.setProperty("farm074.ownedJvm", Long.toString(ProcessHandle.current().pid()));
					retained.setProperty("farm074.ownedStatus", "PASS_NATIVE_HISTORY_CAS_FIRST_SEGMENT");
					report.append("B_OWNED_RESULT\tstatus=PASS_NATIVE_HISTORY_CAS_FIRST_SEGMENT;resume=").append(resume).append(";oldGoalRow=").append(beforeGoal.rowVersion()).append(";newGoalRow=").append(goal.rowVersion()).append(";oldClaimRow=").append(beforeClaim.rowVersion()).append(";newClaimRow=").append(claim.rowVersion()).append(";from=").append(from).append(";publishedFrom=").append(publishedFrom).append(";actual=").append(current).append(";displacementAfterPublication=").append(publishedFrom.distanceTo(current)).append(";intendedAnchor=").append(spec.anchorId()).append(";intendedNpc=").append(spec.npcId()).append(";nativeTravelReason=").append(runtime.travel.reason(id)).append(";NO_FULL_ARRIVAL_CLAIM\n");
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
		final L2jPhantomSupplyJourney supply; final PhantomCommerceService commerce; final PhantomProgressionService progression;
		final org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService reservations;
		final org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService social;
		Runtime(PhantomProfileRepository profiles, L2jPhantomBackgroundAuthority authority, PhantomGameKnowledgeQuery knowledge, PhantomTopologyQuery topology, PhantomNormalGatekeeperTravel routes, long profileId, StringBuilder report, boolean supplyControl)
		{
			id = profileId; goals = new PhantomGoalStateStore(profiles); final var metrics = new PhantomMetrics(); final var bridge = new PhantomMaterializationLifecycleBridge();
			materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1, _ -> {}, bridge, 5000, 10000);
			final var signals = new PhantomRelevanceSignalPort()
			{
				@Override public SignalDelivery submit(long profile, PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				@Override public SignalDelivery withdraw(long profile, String source, long sequence) { return SignalDelivery.ACCEPTED; }
			};
			final var transactions = new PhantomBackgroundTransaction();
			background = new PhantomBackgroundService(profiles, goals, PhantomIdentityLeaseRegistry.getInstance(), transactions, authority, new PhantomBackgroundCompetitionRegistry(), signals, () -> materialization);
			history = new PhantomHistoricalBackgroundService(profiles, goals, new PhantomHistoricalBackgroundPlanner(knowledge, topology, authority), background, materialization);
			navigation = new PhantomNavigationService(metrics); final var engineRef = new AtomicReference<PhantomDecisionEngine>();
			autoPlay = new PhantomVisibleAutoPlay(materialization, engineRef::get, history::permitsDecision);
			travel = new PhantomVisibleFarmTravel(materialization, background, authority.travelQuery(topology), navigation, history::permitsDecision, signals, history::recordVisibleTravelFailure, System::nanoTime);
			// This observer changes no owner calls/order and writes no native or canonical facts.
			final var observedBackground = observeBackgroundLoad(background, transactions, authority, report);
			final var supplyBridge = new PhantomMaterializationLifecycleBridge();
			if (supplyControl)
			{
				final var catalog = new PhantomCommerceCatalogLoader(java.nio.file.Path.of(".")).load(); final var receipts = new PhantomCommerceReceiptStore(profiles); final var intents = new PhantomSupplyIntentStore(profiles, goals);
				commerce = new PhantomCommerceService(catalog, receipts, intents, new L2jCommerceBackend(materialization, catalog.catalog(), java.time.Clock.systemDefaultZone()));
				progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, java.nio.file.Path.of("."), () -> knowledge), PhantomProgressionPolicy.productionDefaults()); progression.start();
				final var policy = org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyPolicy.load(java.nio.file.Path.of("data/phantoms/economy/high-five-economy-v1.xml"));
				reservations = new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyReservationService(policy); check(reservations.start(), "B reservations did not start.");
				social = new org.l2jmobius.gameserver.phantoms.economy.PhantomMultipartyEconomyService(policy, reservations, new org.l2jmobius.gameserver.phantoms.economy.PhantomEconomyOfferService(), materialization, intents, profiles);
				supply = new L2jPhantomSupplyJourney(intents, goals, materialization, commerce, receipts, navigation, () -> knowledge, routes, _ -> true, autoPlay::yieldAfterCurrentTarget, (_, _) -> false, PhantomAutonomousSupplyPolicy.load(java.nio.file.Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml")));
				supply.useNativeResume(profiles, signals); supply.useOfflineSupply(social); supply.useEquipment(progression, (_, _) -> false); supply.useInventoryMaintenance(profile -> reservations.findActive(profile).isEmpty()); supply.requireField(history::canStartNativeJourney);
				history.bindVisibleIntent((profile, root) -> supply.ownsAutomaticIntent(profile, root) || supply.needsOrOwns(profile, root)); history.bindAcceptedIntent(supply::ownsAutomaticIntent); supplyBridge.install(supply);
				check(commerce.start() && supply.start(), "B supply/commerce did not start.");
			}
			else { supply = null; commerce = null; progression = null; reservations = null; social = null; }
			bridge.install(PhantomMaterializationLifecyclePort.chain(supplyBridge, PhantomMaterializationLifecyclePort.chain(travel, PhantomMaterializationLifecyclePort.chain(autoPlay, PhantomMaterializationLifecyclePort.chain(history, observedBackground)))));
			final var binding = PhantomBackgroundDecision.bindVisibleLife(background, travel, autoPlay, history, engineRef::get);
			final var candidates = new PhantomCandidateRegistry(); binding.registerCandidates(candidates); if (supply != null) { supply.registerCandidates(candidates); } candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); binding.registerHandlers(handlers); if (supply != null) { supply.registerHandlers(handlers); } handlers.seal();
			engine = new PhantomDecisionEngine(goals, candidates, handlers, metrics, 1, null, history::permitsDecision); engineRef.set(engine);
			check(background.start() && navigation.start() && materialization.start(), "B ordinary owner start rejected."); engine.start(); check(engine.attach(id) == PhantomDecisionEngine.AttachResult.ATTACHED, "B ordinary Decision attach rejected.");
		}
		@Override public void close() throws Exception
		{
			engine.beginStop(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20); while (!engine.finishStop() && System.nanoTime() < deadline) { Thread.sleep(20); }
			if (supply != null) { supply.beginStop(); check(supply.finishStop(), "B supply did not drain."); commerce.beginStop(); check(commerce.finishStop(), "B commerce did not drain."); }
			autoPlay.stop(id); navigation.beginStop(); while (!navigation.finishStop() && System.nanoTime() < deadline) { Thread.sleep(20); }
			check(materialization.shutdown().state() == PhantomMaterializationService.ServiceState.STOPPED, "B native materialization did not store/drain."); background.beginStop(); check(background.finishStop(), "B Background owner did not stop.");
			if (social != null) { check(social.shutdown(System.currentTimeMillis()).successful(), "B social economy did not drain."); reservations.shutdown(System.currentTimeMillis()); progression.beginStop(); check(progression.finishStop(), "B progression did not drain."); }
		}
	}

	private static PhantomMaterializationLifecyclePort observeBackgroundLoad(PhantomBackgroundService background, PhantomBackgroundTransaction transactions, L2jPhantomBackgroundAuthority authority, StringBuilder report)
	{
		return new PhantomMaterializationLifecyclePort()
		{
			@Override public void beforeMaterialize(long id, int objectId) { background.beforeMaterialize(id, objectId); }
			@Override public void beforeMaterialize(long id, int objectId, PhantomMaterializationService.MaterializationPurpose purpose, String claim) { background.beforeMaterialize(id, objectId, purpose, claim); }
			@Override public void afterPlayerLoad(long id, Player player)
			{
				loadDiagnostic("BEFORE_BACKGROUND", id, player, transactions, authority, report);
				try { background.afterPlayerLoad(id, player); }
				catch (RuntimeException | Error failure)
				{
					loadDiagnostic("FAILED_BACKGROUND", id, player, transactions, authority, report);
					throw failure;
				}
				loadDiagnostic("AFTER_BACKGROUND", id, player, transactions, authority, report);
			}
			@Override public void materializeSucceeded(long id, int objectId) { background.materializeSucceeded(id, objectId); }
			@Override public void materializeAborted(long id, int objectId) { background.materializeAborted(id, objectId); }
			@Override public void beforeStore(long id, Player player) { background.beforeStore(id, player); }
			@Override public void afterStore(long id, Player player) { background.afterStore(id, player); }
		};
	}

	/** Bounded read-only evidence on the exact load boundary; diagnostics must never affect admission. */
	private static void loadDiagnostic(String phase, long id, Player player, PhantomBackgroundTransaction transactions, L2jPhantomBackgroundAuthority authority, StringBuilder report)
	{
		try
		{
			final var loaded = transactions.load(id); final var state = loaded.state(); final var context = transactions.nativeContext(id, player.getObjectId());
			report.append("B_OWNED_LOAD_DIAGNOSTIC\tphase=").append(phase).append(";profile=").append(id).append(";actor=").append(player.getObjectId()).append(";loadStatus=").append(loaded.status()).append(";contextStatus=").append(context.status()).append(";context=").append(context.context()).append(";canonicalVitality=").append(context.canonicalPoints()).append(";nativeVitality=").append(player.getVitalityPoints());
			if (state != null)
			{
				report.append(";state=").append(state.state()).append(";runtimeMatches=").append(authority.matchesRuntime(player, state)).append(";identity=").append(state.identity()).append(";progress=").append(state.progress()).append(";vitals=").append(state.vitals()).append(";position=").append(state.position()).append(";inventory=").append(state.inventory()).append(";autoGet=").append(state.autoGetSkills());
			}
			report.append(";nativeClass=").append(player.getActiveClass()).append(";nativeLevel=").append(player.getLevel()).append(";nativeEXP=").append(player.getExp()).append(";nativeSP=").append(player.getSp()).append(";nativeVitals=").append(player.getCurrentHp()).append('/').append(player.getMaxHp()).append(',').append(player.getCurrentMp()).append('/').append(player.getMaxMp()).append(',').append(player.getCurrentCp()).append('/').append(player.getMaxCp()).append(";nativePosition=").append(player.getLocation()).append('\n');
			try (var connection = DatabaseFactory.getConnection())
			{
				try (var query = connection.prepareStatement("SELECT level,exp,sp,expBeforeDeath,classid,base_class,race,curHp,maxHp,curMp,maxMp,curCp,maxCp,x,y,z,heading,vitality_points FROM characters WHERE charId=?"))
				{
					query.setInt(1, player.getObjectId());
					try (var rows = query.executeQuery())
					{
						while (rows.next()) { report.append("B_OWNED_CANONICAL\tphase=").append(phase); for (int index = 1; index <= rows.getMetaData().getColumnCount(); index++) { report.append(';').append(rows.getMetaData().getColumnLabel(index)).append('=').append(rows.getString(index)); } report.append('\n'); }
					}
				}
				try (var query = connection.prepareStatement("SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=? ORDER BY skill_id LIMIT 129"))
				{
					query.setInt(1, player.getObjectId()); query.setInt(2, player.getClassIndex());
					try (var rows = query.executeQuery()) { report.append("B_OWNED_CANONICAL_SKILLS\tphase=").append(phase); int count = 0; while (rows.next()) { report.append(';').append(rows.getInt(1)).append(':').append(rows.getInt(2)); count++; } report.append(";boundedCount=").append(count).append('\n'); }
				}
				try (var query = connection.prepareStatement("SELECT object_id,item_id,count,loc FROM items WHERE owner_id=? AND loc IN ('INVENTORY','PAPERDOLL') ORDER BY object_id LIMIT 129"))
				{
					query.setInt(1, player.getObjectId());
					try (var rows = query.executeQuery()) { report.append("B_OWNED_CANONICAL_ITEMS\tphase=").append(phase); int count = 0; while (rows.next()) { report.append(';').append(rows.getInt(1)).append(':').append(rows.getInt(2)).append(':').append(rows.getLong(3)).append(':').append(rows.getString(4)); count++; } report.append(";boundedCount=").append(count).append('\n'); }
				}
			}
		}
		catch (Throwable failure) { report.append("B_OWNED_LOAD_DIAGNOSTIC_GAP\tphase=").append(phase).append(';').append(failure.getClass().getName()).append(':').append(failure.getMessage()).append('\n'); }
	}
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

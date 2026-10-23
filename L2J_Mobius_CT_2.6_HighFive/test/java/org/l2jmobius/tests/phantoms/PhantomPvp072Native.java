/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
import org.l2jmobius.gameserver.model.Location;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.chat.ChatObservationService;
import org.l2jmobius.gameserver.model.events.EventType;
import org.l2jmobius.gameserver.model.events.holders.actor.creature.OnCreatureDamageDealt;
import org.l2jmobius.gameserver.model.events.listeners.ConsumerEventListener;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.model.zone.type.NoPvPZone;
import org.l2jmobius.gameserver.model.zone.type.PeaceZone;
import org.l2jmobius.gameserver.model.zone.type.WaterZone;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundGoalSpec;
import org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel;
import org.l2jmobius.gameserver.phantoms.combat.*;
import org.l2jmobius.gameserver.phantoms.commerce.*;
import org.l2jmobius.gameserver.phantoms.conversation.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.farming.*;
import org.l2jmobius.gameserver.phantoms.knowledge.PhantomGameKnowledgeService;
import org.l2jmobius.gameserver.phantoms.navigation.*;
import org.l2jmobius.gameserver.phantoms.party.*;
import org.l2jmobius.gameserver.phantoms.player.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.progression.*;
import org.l2jmobius.gameserver.phantoms.pvp.*;
import org.l2jmobius.gameserver.phantoms.social.*;
import org.l2jmobius.gameserver.phantoms.topology.*;

/** Own072 native PvP composition; never creates a second product AI owner. */
final class PhantomPvp072Native
{
	static void run(PhantomGameKnowledgeService knowledge, PhantomTopologyQuery query, StringBuilder report) throws Exception
	{
		check(PhantomTask072Lane.enabled(), "Own072 native lane required.");
		final Path task = Path.of(System.getProperty("phantom.module.root")).getParent().resolve(".phantom-local/task072");
		final Properties checkpoint = new Properties(); try (var reader = Files.newBufferedReader(task.resolve("cp072-native.properties"))) { checkpoint.load(reader); }
		check("true".equals(checkpoint.getProperty("paid")) && !Files.exists(task.resolve("pvp072-measured.once")), "One original paid CP actor and unmeasured PvP episode required.");
		final long profile = Long.parseLong(checkpoint.getProperty("profile")); final int actorId = Integer.parseInt(checkpoint.getProperty("actor"));
		final var profiles = PhantomProfileRepository.open(); final var metrics = new PhantomMetrics(); final var goals = new PhantomGoalStateStore(profiles);
		final var cleanup = new java.util.ArrayList<AutoCloseable>();
		try
		{
		final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
		cleanup.add(() -> { final var stopped = materialization.shutdown(); report.append("pvp_shutdown\t").append(stopped).append('\n'); check(stopped.state() == PhantomMaterializationService.ServiceState.STOPPED, "Materialization failed to persist/drain; native PASS forbidden."); });
		check(materialization.start() && materialization.materialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "Paid actor did not reload.");
		final Player actor = World.getInstance().getPlayer(actorId);
		check(actor != null && !actor.isGM() && actor.getClient() == null && Set.of("task072cp", "task072cp2").contains(actor.getAccountName()) && actor.getLevel() == 20 && actor.getInventory().getInventoryItemCount(1463, -1) == 9 && actor.getInventory().getInventoryItemCount(5592, -1) == 1, "Paid CP actor/finite shot checkpoint differs; no grant or replacement.");
		final var paid = new PhantomCommerceReceiptStore(profiles).find(profile).orElseThrow().receipt();
		check(paid.state() == PhantomCommerceReceipt.State.COMMITTED && paid.request().itemId() == 5592, "Actual paid CP receipt missing.");
		final var progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, Path.of("."), knowledge::query), PhantomProgressionPolicy.productionDefaults()); cleanup.add(() -> { progression.beginStop(); check(progression.finishStop(), "Progression STOP pending."); }); progression.start();
		final var combat = new PhantomCombatService(new L2jCombatBackend(materialization, knowledge::query, progression::catalog), PhantomCombatCapabilityResolver.fromGameKnowledge(knowledge::query), PhantomCombatPolicy.productionDefaults(1)); cleanup.add(() -> { combat.beginStop(); await(combat::finishStop, 12000, "Combat STOP pending."); }); combat.start();
		final var navigation = new PhantomNavigationService(metrics); cleanup.add(() -> { navigation.beginStop(); await(navigation::finishStop, 12000, "Navigation STOP pending."); }); check(navigation.start(), "Navigation unavailable.");
		final var topology = PhantomTopologyService.fromSnapshotForTesting(query.snapshot(), new L2jTopologyValidationBackend(), PhantomTopologyPolicy.productionDefaults(), new PhantomRelevanceSignalPort()
		{
			@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
			@Override public SignalDelivery withdraw(long id, String key, long sequence) { return SignalDelivery.ACCEPTED; }
		});
		cleanup.add(() -> { topology.beginStop(); check(topology.finishStop(), "Topology STOP pending."); }); check(topology.start(), "Topology unavailable."); topology.registerProfile(profile);
		final var socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
		final var social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(profiles, socialCatalog), 720072L, 16, () -> System.currentTimeMillis() / 60000L); cleanup.add(() -> { social.beginStop(); check(social.finishStop(), "Social STOP pending."); }); check(social.start(), "Social unavailable.");
		final var partyBackend = new L2jPhantomPartyBackend(profiles, materialization, progression);
		final var party = new PhantomPartyCoordinator(new PhantomPartyStore(profiles), goals, partyBackend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(navigation, combat), new PhantomPartyTactics(combat, partyBackend), () -> query.snapshot().canonicalHash(), System::nanoTime, 16, social, () -> System.currentTimeMillis() / 60000L); cleanup.add(() -> { party.beginStop(); await(party::finishStop, 6000, "Party STOP pending."); }); check(party.start(), "Party owner unavailable.");
		// Same native ACTUAL_ATTACK composition as the existing064 integration: no fabricated farming claim or social event.
		final var farming = new PhantomFarmingService(PhantomFarmingPolicy.load(Path.of("data/phantoms/farming/high-five-farming-conflict-v1.xml")), new PhantomFarmingStore(profiles), id -> Optional.empty(), topology, (first, second) -> false, new PhantomFarmingService.SocialFacts()
		{
			@Override public PhantomFarmingService.SocialEvidence evidence(long owner, long counterpart, long minute) { throw new AssertionError("No farming authority belongs to this reactive native scene."); }
			@Override public boolean record(long owner, long counterpart, String key, String event, String hash, long minute) { throw new AssertionError("No fabricated farming event allowed."); }
		}, 16, () -> System.currentTimeMillis() / 60000L, PhantomFarmingService.FaultInjector.NONE); cleanup.add(() -> { farming.beginStop(); check(farming.finishStop(), "Farming STOP pending."); }); check(farming.start(), "Farming owner unavailable.");
		final var executionCatalog = PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v4.xml"));
		final var execution = new PhantomConversationExecutionService(executionCatalog, new PhantomConversationExecutionStore(profiles, executionCatalog), goals, new L2jPhantomConversationExecutionPort(executionCatalog, knowledge, query, party, materialization, ChatObservationService.getInstance())); cleanup.add(() -> { execution.beginStop(); await(execution::finishStop, 6000, "Outbound STOP pending."); }); check(execution.start(), "Native outbound owner unavailable.");
		final var policy = PhantomPvpPolicy.load(Path.of("data/phantoms/pvp/pvp-policy-v1.xml")); final var pvpStore = new PhantomPvpStore(profiles); final var pvpSocial = new PhantomPvpSocialBridge(social);
		final var retreat = new PhantomPvpRetreatCoordinator(navigation, topology, combat);
		final var pvp = new PhantomPvpService(policy, pvpStore, new PhantomPvpContext(policy, combat, party, farming, pvpSocial, materialization), combat, new PhantomPvpConversationBridge(execution), pvpSocial, retreat);
		cleanup.add(() -> { pvp.beginStop(); await(pvp::finishStop, 6000, "PvP STOP pending."); });
		Player enemy = null; PhantomCommerceService commerce = null; L2jPhantomSupplyJourney journey = null; ConsumerEventListener listener = null;
		try
		{
			try (var lease = materialization.tryAcquireAction(profile).orElseThrow()) { actor.setRunning(); }
			final var scene = scene(actor, query, policy.limits().retreatTimeoutSeconds(), report);
			walk(materialization, profile, actor, scene.approach(), report);
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task072pvp','DISABLED_NATIVE_TEST_ONLY',-1,1)")) { statement.executeUpdate(); }
			enemy = Player.create(PlayerTemplateData.getInstance().getTemplate(0), "task072pvp", "Enemy072", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
			check(enemy != null && !enemy.isGM() && enemy.getClient() == null, "Ordinary finite opponent required.");
			enemy.setPlayerClass(4); enemy.setBaseClass(4); enemy.getStat().setExp(enemy.getStat().getExpForLevel(20)); enemy.getStat().setLevel((byte) 20); enemy.giveAvailableAutoGetSkills();
			for (var skill : SkillTreeData.getInstance().getAllAvailableSkills(enemy, enemy.getPlayerClass(), true, true, true)) { enemy.addSkill(skill, true); }
			for (var item : enemy.getInventory().getItems().stream().filter(value -> value.isEquipped()).toList()) { enemy.getInventory().unEquipItemInSlot(item.getLocationSlot()); }
			for (int item : new int[] {2, 352, 2378, 2493}) { enemy.getInventory().equipItem(enemy.addItem(ItemProcessType.REWARD, item, 1, enemy, false)); }
			enemy.setCurrentHp(enemy.getMaxHp()); enemy.setCurrentMp(enemy.getMaxMp()); enemy.setCurrentCp(enemy.getMaxCp()); enemy.setOnlineStatus(true, false); enemy.spawnMe(actor.getX() + 80, actor.getY(), GeoEngine.getInstance().getHeight(actor.getX() + 80, actor.getY(), actor.getZ()));
			final Player target = enemy; final var skills = new AtomicInteger(); final var skillIds = new java.util.concurrent.ConcurrentSkipListSet<Integer>();
			listener = new ConsumerEventListener(actor, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) -> { if (event.getAttacker() == actor && event.getTarget() == target && event.getSkill() != null && event.getDamage() > 0) { skills.incrementAndGet(); skillIds.add(event.getSkill().getId()); } }, actor); actor.addListener(listener);
			check(!actor.isInsidePeaceZone(target) && GeoEngine.getInstance().canSeeTarget(actor, target) && !L2jCombatBackend.nativePvpSkills(actor, PhantomCombatMode.MELEE_PHYSICAL, 16).isEmpty(), "Native legal PvP fixture/learned skill unavailable.");
			Files.writeString(task.resolve("pvp072-measured.once"), "actor=" + actorId + ";profile=" + profile + ";paid=" + paid.operationKey(), java.nio.file.StandardOpenOption.CREATE_NEW);
			final long beforeShots = actor.getInventory().getInventoryItemCount(1463, -1), beforeCpItem = actor.getInventory().getInventoryItemCount(5592, -1); final double beforeCp = actor.getCurrentCp();
			report.append("pvp_setup\tSAME_PAID_CP_ACTOR=").append(actorId).append(";finiteDshots=").append(beforeShots).append(";paidCP5592=").append(beforeCpItem).append(";CP=").append(beforeCp).append(";opponentKnight20/2+352+2378+2493 FIXTURE;no HP/MP/CP/items writes after this marker;source receipt=").append(paid).append('\n');
			actor.setTarget(target); target.setTarget(actor); final double pool = actor.getCurrentHp() + actor.getCurrentCp(); target.doAttack(actor);
			await(() -> actor.getCurrentHp() + actor.getCurrentCp() < pool, 6000, "Real enemy first attack did not land."); target.abortAttack(); target.getAI().setIntention(Intention.IDLE);
			check(combat.observePvp(profile, List.of(target.getObjectId()), 16, 16).orElseThrow().targets().stream().anyMatch(value -> value.target().objectId() == target.getObjectId() && value.actualAttacker() && value.canonicalContextAllowed()), "Actual attacker authority not observed.");
			check(pvp.start(), "PvP owner unavailable."); pvp.materializeSucceeded(profile, actorId);
			long sequence = 0; String last = ""; boolean shortage = false, arrived = false; double cpPeak = actor.getCurrentCp(); double poolAfterShortage = -1;
			final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(75);
			while (System.nanoTime() < until && !actor.isDead())
			{
				topology.updateProfile(profile, new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId()), ++sequence);
				pvp.onPulse(); execution.onPulse(); cpPeak = Math.max(cpPeak, actor.getCurrentCp());
				final var stored = pvpStore.load(profile).orElse(null); final String state = stored == null ? "none" : stored.encounter().stage() + ":" + stored.encounter().terminalReason();
				if (!state.equals(last)) { report.append("pvp_native_owner\t").append(stored).append(";xyz=").append(actor.getLocation()).append(";shots=").append(actor.getInventory().getInventoryItemCount(1463, -1)).append(";paidCP=").append(actor.getInventory().getInventoryItemCount(5592, -1)).append(";CP=").append(actor.getCurrentCp()).append('\n'); last = state; }
				if (state.contains("supply.required")) { shortage = true; if (poolAfterShortage < 0) { poolAfterShortage = target.getCurrentHp() + target.getCurrentCp(); } }
				if (shortage && state.contains("pvp.retreat.arrived")) { arrived = true; break; }
				if (stored != null && stored.encounter().stage() == PhantomPvpModel.Stage.COOLDOWN && !shortage) { break; }
				Thread.sleep(100);
			}
			report.append("pvp_effects\tskillEvents=").append(skills.get()).append(";ids=").append(skillIds).append(";Dshots=").append(beforeShots).append("->").append(actor.getInventory().getInventoryItemCount(1463, -1)).append(";paid5592=").append(beforeCpItem).append("->").append(actor.getInventory().getInventoryItemCount(5592, -1)).append(";CP=").append(beforeCp).append("->peak").append(cpPeak).append(";shortage=").append(shortage).append(";retreatArrived=").append(arrived).append(";service=").append(pvp.snapshot()).append('\n');
			check(skills.get() > 0 && actor.getInventory().getInventoryItemCount(1463, -1) == 0 && shortage && arrived, "Native learned skill/physical exhaustion/owner retreat chain incomplete.");
			check(actor.getInventory().getInventoryItemCount(5592, -1) < beforeCpItem && cpPeak > beforeCp, "Paid greater CP was not used with real CP effect in PvP.");
			check(!actor.isAttackingNow() && !actor.isCastingNow() && target.getCurrentHp() + target.getCurrentCp() >= poolAfterShortage, "Offensive damage continued after supply shortage.");
			pvp.beginStop(); await(pvp::finishStop, 6000, "PvP owner did not release after native retreat.");
			final var old = goals.load(profile).orElseThrow(); final var anchor = query.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING).min(Comparator.comparingDouble(value -> actor.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).orElseThrow();
			final var root = new PhantomGoal(72007204L, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profile)), new PhantomDomainRef("npc", "20418"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20418@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "pvp072.native.resupply", 1); goals.replace(profile, old.rowVersion(), root);
			final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load(); final var receipts = new PhantomCommerceReceiptStore(profiles); final var supplyStore = new PhantomSupplyIntentStore(profiles, goals);
			commerce = new PhantomCommerceService(catalog, receipts, supplyStore, new L2jCommerceBackend(materialization, catalog.catalog(), java.time.Clock.systemDefaultZone())); check(commerce.start(), "Commerce unavailable after retreat.");
			journey = new L2jPhantomSupplyJourney(supplyStore, goals, materialization, commerce, receipts, navigation, knowledge::query, PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), query), id -> true, (goal, player) -> true, (id, goal) -> false, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"))); check(journey.start(), "Supply owner unavailable.");
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final long resupplyUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(70); String lastSupply = ""; boolean attempted = false;
			while (System.nanoTime() < resupplyUntil)
			{
				final var planning = new PhantomPlanningContext(profile, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1); final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
				if (selected != null) { final var plan = selected.planFactory().create(planning); handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(profile, root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false)); attempted = true; }
				final var reason = journey.reason(profile); if (!reason.equals(lastSupply)) { report.append("pvp_lawful_resupply\t").append(reason).append(";A=").append(actor.getAdena()).append(";Dshots=").append(actor.getInventory().getInventoryItemCount(1463, -1)).append(";xyz=").append(actor.getLocation()).append('\n'); lastSupply = reason; }
				final var receipt = receipts.find(profile).orElseThrow().receipt(); if (receipt.state() == PhantomCommerceReceipt.State.COMMITTED && receipt.request().itemId() == 1463) { report.append("pvp_supply_committed\t").append(receipt).append('\n'); break; }
				final var intent = journey.automaticIntent(profile).orElse(null); if (intent != null && intent.status() == PhantomGoalStatus.FAILED) { break; } Thread.sleep(200);
			}
			check(attempted, "No meaningful lawful supply attempt after retreat."); report.append("result\tPASS bounded ACTUAL_ATTACK skill/shots/paidCP->SUPPLY_REQUIRED->native retreat->lawful supply attempt;P1_PROACTIVE=PARTIAL_FULL_CAUSE;no fabricated spot dispute\n");
		}
		finally
		{
			if (listener != null) { actor.removeListener(listener); }
			try { if (journey != null) { journey.beginStop(); check(journey.finishStop(), "Supply did not drain."); } }
			finally { try { if (commerce != null) { commerce.beginStop(); check(commerce.finishStop(), "Commerce did not drain."); } }
			finally { if (enemy != null) { enemy.abortAttack(); enemy.abortCast(); enemy.stopAllTasks(); enemy.storeMe(); enemy.deleteMe(); } } }
		}
		}
		finally { closeAll(cleanup); }
	}
	static void closeAll(List<AutoCloseable> cleanup) throws Exception
	{
		Exception failure = null;
		for (int index = cleanup.size() - 1; index >= 0; index--)
		{
			try { cleanup.get(index).close(); }
			catch (Exception | AssertionError error) { final var next = error instanceof Exception exception ? exception : new IllegalStateException(error); if (failure == null) { failure = next; } else { failure.addSuppressed(next); } }
		}
		if (failure != null) { throw failure; }
	}

	private record Scene(PhantomNavigationPoint field, List<PhantomNavigationPoint> approach) { }
	private static Scene scene(Player actor, PhantomTopologyQuery query, int retreatSeconds, StringBuilder report)
	{
		final var paths = new L2jNavigationBackend(); final var geo = GeoEngine.getInstance(); final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30); final int[] counts = new int[6];
		final var anchors = query.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.RESPAWN || value.role() == PhantomTopologyAnchorRole.CITY_CENTER).sorted(Comparator.comparingDouble(value -> actor.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).limit(3).toList();
		for (var anchor : anchors) { for (int radius : new int[] {600, 1000, 1600, 2200, 2800, 3400}) { for (int direction = 0; direction < 16 && System.nanoTime() < deadline; direction++)
		{
			counts[0]++; final double angle = direction * Math.PI / 8; final int x = anchor.point().x() + (int) Math.round(radius * Math.cos(angle)), y = anchor.point().y() + (int) Math.round(radius * Math.sin(angle));
			final var point = paths.walkSurface(new PhantomNavigationPoint(x, y, anchor.point().z(), 0), 256).orElse(null); if (point == null || actor.calculateDistance3D(x, y, point.z()) > 5000 || ZoneManager.getInstance().getZone(x, y, point.z(), PeaceZone.class) != null || ZoneManager.getInstance().getZone(x, y, point.z(), NoPvPZone.class) != null || ZoneManager.getInstance().getZone(x, y, point.z(), WaterZone.class) != null || !geo.canSeeTarget(x, y, point.z(), 0, x + 80, y, point.z(), 0)) { continue; }
			counts[1]++; final var nativePoint = new PhantomTopologyPoint(x, y, point.z(), 0); final var respawns = query.nearestAnchors(nativePoint, PhantomTopologyAnchorRole.RESPAWN, 1, 100000); final var cities = query.nearestAnchors(nativePoint, PhantomTopologyAnchorRole.CITY_CENTER, 1, 100000); final var safe = respawns.isEmpty() || !cities.isEmpty() && nativePoint.distanceSquared2D(cities.getFirst().point()) < nativePoint.distanceSquared2D(respawns.getFirst().point()) ? cities : respawns; if (safe.isEmpty()) { continue; }
			final var safePoint = paths.walkSurface(new PhantomNavigationPoint(safe.getFirst().point().x(), safe.getFirst().point().y(), safe.getFirst().point().z(), 0), safe.getFirst().validationTolerance()).orElse(null); if (safePoint == null) { report.append("pvp_safe_surface_rejected\t").append(safe.getFirst()).append(";geoHeight=").append(geo.getHeight(safe.getFirst().point().x(), safe.getFirst().point().y(), safe.getFirst().point().z())).append('\n'); continue; }
			counts[2]++; final long now = System.nanoTime(); final var back = paths.findWalkCorridor(new PhantomNavigationRequest(1, point, safePoint, now, Math.min(deadline, now + TimeUnit.SECONDS.toNanos(1)), 5000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), System::nanoTime); if (back.status() != PhantomWalkCorridorPlanner.Status.FOUND) { report.append("pvp_route_rejected\t").append(point).append("->").append(safePoint).append(';').append(back.status()).append(";budget status is not geometry proof\n"); continue; }
			counts[3]++; double length = 0; var previous = point; for (var step : back.points()) { length += previous.distanceTo(step); previous = step; } if (length < 300 || length / actor.getStat().getMoveSpeed() >= retreatSeconds - 3) { continue; } counts[4]++;
			final long outNow = System.nanoTime(); final var out = paths.findWalkCorridor(new PhantomNavigationRequest(1, PhantomNativeTravelActions.point(actor), point, outNow, Math.min(deadline, outNow + TimeUnit.SECONDS.toNanos(1)), 5000), new PhantomNavigationCancellationToken(), PhantomNavigationPolicy.productionDefaults(), System::nanoTime); if (out.status() != PhantomWalkCorridorPlanner.Status.FOUND) { continue; }
			report.append("pvp_preflight\tfield=").append(point).append(";safe=").append(safePoint).append(";nativeBothRoutes=FOUND;retreatLength=").append(length).append(";ETA=").append(length / actor.getStat().getMoveSpeed()).append(";policySeconds=").append(retreatSeconds).append(";ordinary zones/Geo;before opponent creation and attack\n"); return new Scene(point, out.points());
		} } }
		report.append("pvp_preflight_counts\t").append(java.util.Arrays.toString(counts)).append(";speed=").append(actor.getMoveSpeed()).append(";anchors=").append(anchors.stream().map(value -> value.id() + ':' + value.point()).toList()).append('\n');
		throw new AssertionError("BLOCKED_INPUT: no legal bounded PvP perimeter/unchanged retreat route; no opponent or attack created.");
	}

	private static void walk(PhantomMaterializationService materialization, long profile, Player actor, List<PhantomNavigationPoint> route, StringBuilder report) throws Exception
	{
		final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(70); final var paths = new L2jNavigationBackend();
		try (var lease = materialization.tryAcquireAction(profile).orElseThrow())
		{
			for (var point : route)
			{
				if (actor.calculateDistance3D(point.x(), point.y(), point.z()) <= 40) { continue; }
				check(paths.canWalkDirect(PhantomNativeTravelActions.point(actor), point) && PhantomNativeTravelActions.safePveWalkSegment(actor, point.x(), point.y(), point.z(), List.copyOf(World.getInstance().getVisibleObjectsInRange(actor, Monster.class, 3500)), Set.of()), "Native perimeter approach has unsafe geometry/threat.");
				actor.getAI().setIntention(Intention.MOVE_TO, new Location(point.x(), point.y(), point.z())); final var walk = PhantomNativeTravelActions.captureWalk(actor);
				try { while (!actor.isDead() && actor.calculateDistance3D(point.x(), point.y(), point.z()) > 40 && System.nanoTime() < deadline) { Thread.sleep(100); } check(actor.calculateDistance3D(point.x(), point.y(), point.z()) <= 40, "Native perimeter approach deadline; no teleport."); }
				finally { PhantomNativeTravelActions.cancelWalk(walk); }
			}
		}
		report.append("pvp_native_approach\tactual=").append(actor.getLocation()).append(";no relocation or resources\n");
	}
	private static void await(java.util.function.BooleanSupplier condition, long millis, String failure) throws Exception { final long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis); boolean reached = condition.getAsBoolean(); while (!reached && System.nanoTime() < until) { Thread.sleep(50); reached = condition.getAsBoolean(); } check(reached, failure); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

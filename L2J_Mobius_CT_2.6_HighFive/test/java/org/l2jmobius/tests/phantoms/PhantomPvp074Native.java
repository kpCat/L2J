/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.ai.Intention;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.data.xml.SkillTreeData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.managers.ZoneManager;
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

/** Finite OWN074 encounter; the calling lane owns infrastructure and JVM shutdown. */
final class PhantomPvp074Native
{
	static boolean run(PhantomGameKnowledgeService knowledge, PhantomTopologyQuery query, StringBuilder report) throws Exception
	{
		return run(knowledge, query, report, false);
	}
	static boolean runRecovery(PhantomGameKnowledgeService knowledge, PhantomTopologyQuery query, StringBuilder report) throws Exception
	{
		return run(knowledge, query, report, true);
	}
	private static boolean run(PhantomGameKnowledgeService knowledge, PhantomTopologyQuery query, StringBuilder report, boolean recovery) throws Exception
	{
		final var cleanup = new java.util.ArrayList<AutoCloseable>();
		Player enemy = null, actor = null; ConsumerEventListener listener = null, incoming = null;
		try
		{
			final var profiles = PhantomProfileRepository.open(); final var metrics = new PhantomMetrics(); final var goals = new PhantomGoalStateStore(profiles);
			final int actorId; final PhantomNavigationPoint field;
			try (var connection = DatabaseFactory.getConnection(); var statement = connection.prepareStatement("SELECT c.charId,c.x,c.y,c.z FROM characters c JOIN accounts a ON a.login=c.account_name WHERE c.account_name='task074pvp' AND c.char_name='Survivor074' AND a.accessLevel=-1 AND a.password='DISABLED_NATIVE_TEST_ONLY'"))
			{
				connection.setReadOnly(true); try (var rows = statement.executeQuery()) { check(rows.next(), "D retained Survivor074 missing; no replacement fixture permitted."); actorId = rows.getInt(1); field = new PhantomNavigationPoint(rows.getInt(2), rows.getInt(3), rows.getInt(4), 0); check(!rows.next(), "D retained identity is not unique."); }
				try (var guard = connection.createStatement(); var rows = guard.executeQuery("SELECT (SELECT COUNT(*) FROM accounts WHERE login='task074enemy' AND accessLevel=-1 AND password='DISABLED_NATIVE_TEST_ONLY'),(SELECT COUNT(*) FROM characters WHERE account_name='task074enemy'),(SELECT COUNT(*) FROM characters WHERE account_name='task074enemy' AND char_name='Enemy074' AND charId=268481828)")) { check(rows.next() && rows.getInt(1) == 1 && rows.getInt(2) == (recovery ? 1 : 0) && rows.getInt(3) == (recovery ? 1 : 0), "D exact original enemy account/identity differs; no duplicate enemy/reseed permitted."); }
			}
			final long profile = profiles.findByCharacterObjectId(actorId).orElseThrow().profileId();
			check(actorId == 268481796 && profile == 4 && goals.load(profile).isPresent() == recovery && profiles.findComponent(profile, PhantomPvpModel.COMPONENT_TYPE).isPresent() == recovery, "D retained identity/state differs; no goal/reset/reseed permitted.");
			report.append(recovery ? "D_RECOVERY_RETAINED\t" : "D_RETAINED_PREMEASUREMENT\t").append("profile=").append(profile).append(";actor=").append(actorId).append(";storedPoint=").append(field).append(recovery ? ";original episode remains PARTIAL: skills2/9D->0/CP2->1 but retreat expired;continuation reuses exact actor+enemy+goal+encounter;no resources/HP/CP/XYZ/skills setters\n" : ";previous failure was goal.replace(-1) before enemy/measurement;no actor resource/HP/CP/XYZ/skill writes\n");
			final var materialization = new PhantomMaterializationService(profiles, PhantomIdentityLeaseRegistry.getInstance(), metrics, new PhantomDiagnosticTrace(false, 0, 0, metrics), 1);
			cleanup.add(() -> { final var stopped = materialization.shutdown(); report.append("D_shutdown\t").append(stopped).append('\n'); check(stopped.state() == PhantomMaterializationService.ServiceState.STOPPED, "D materialization did not drain."); });
			check(materialization.start() && materialization.materialize(profile).status() == PhantomMaterializationService.ResultStatus.SUCCESS, "D native actor did not materialize.");
			actor = World.getInstance().getPlayer(actorId); check(actor != null && !actor.isGM() && actor.getClient() == null && actor.getAccountName().equals("task074pvp") && actor.getName().equals("Survivor074") && PhantomNativeTravelActions.point(actor).equals(field), "D ordinary retained Player/point differs."); final Player measured = actor;
			check(actor.getAdena() == 2500 && actor.getInventory().getInventoryItemCount(1463, -1) == (recovery ? 0 : 9) && actor.getInventory().getInventoryItemCount(5592, -1) == (recovery ? 1 : 2) && actor.getLevel() == 20 && actor.getActiveWeaponItem() != null && actor.getActiveWeaponItem().getId() == 129, "D retained finite resources differ; no repair/reseed permitted.");
			final var paths = new L2jNavigationBackend(); final var escapePoint = recovery ? null : paths.walkSurface(new PhantomNavigationPoint(field.x() - 600, field.y(), field.z(), 0), 256).orElseThrow(); final var enemyPoint = recovery ? null : paths.walkSurface(new PhantomNavigationPoint(field.x() + 80, field.y(), field.z(), 0), 256).orElseThrow();
			if (!recovery) { check(legal(field) && legal(escapePoint) && legal(enemyPoint) && paths.canWalkDirect(field, escapePoint) && paths.canWalkDirect(field, enemyPoint), "D retained point no longer has its original lawful native Geo escape; no relocation permitted."); }
			final var progression = new PhantomProgressionService(new L2jProgressionBackend(materialization, Path.of("."), knowledge::query), PhantomProgressionPolicy.productionDefaults()); cleanup.add(() -> { progression.beginStop(); check(progression.finishStop(), "D progression STOP pending."); }); progression.start();
			final var combat = new PhantomCombatService(new L2jCombatBackend(materialization, knowledge::query, progression::catalog), PhantomCombatCapabilityResolver.fromGameKnowledge(knowledge::query), PhantomCombatPolicy.productionDefaults(1)); cleanup.add(() -> { combat.beginStop(); await(combat::finishStop, 12000, "D combat STOP pending."); }); combat.start();
			final var navigation = new PhantomNavigationService(metrics); cleanup.add(() -> { navigation.beginStop(); await(navigation::finishStop, 12000, "D navigation STOP pending."); }); check(navigation.start(), "D navigation unavailable.");
			final var topology = PhantomTopologyService.fromSnapshotForTesting(query.snapshot(), new L2jTopologyValidationBackend(), PhantomTopologyPolicy.productionDefaults(), new PhantomRelevanceSignalPort()
			{
				@Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
				@Override public SignalDelivery withdraw(long id, String key, long sequence) { return SignalDelivery.ACCEPTED; }
			});
			cleanup.add(() -> { topology.beginStop(); check(topology.finishStop(), "D topology STOP pending."); }); check(topology.start(), "D topology unavailable."); topology.registerProfile(profile);
			final var socialCatalog = PhantomSocialCatalog.load(Path.of("data/phantoms/social/high-five-social-v1.xml"));
			final var social = new PhantomSocialService(socialCatalog, new PhantomSocialStore(profiles, socialCatalog), 740074L, 16, () -> System.currentTimeMillis() / 60000L); cleanup.add(() -> { social.beginStop(); check(social.finishStop(), "D social STOP pending."); }); check(social.start(), "D social unavailable.");
			final var partyBackend = new L2jPhantomPartyBackend(profiles, materialization, progression);
			final var party = new PhantomPartyCoordinator(new PhantomPartyStore(profiles), goals, partyBackend, PhantomPartyRoleCatalog.load(Path.of("data/phantoms/party/high-five-party-roles-v1.xml")), new PhantomPartyRouteCoordinator(navigation, combat), new PhantomPartyTactics(combat, partyBackend), () -> query.snapshot().canonicalHash(), System::nanoTime, 16, social, () -> System.currentTimeMillis() / 60000L); cleanup.add(() -> { party.beginStop(); await(party::finishStop, 6000, "D party STOP pending."); }); check(party.start(), "D party unavailable.");
			final var farming = new PhantomFarmingService(PhantomFarmingPolicy.load(Path.of("data/phantoms/farming/high-five-farming-conflict-v1.xml")), new PhantomFarmingStore(profiles), id -> Optional.empty(), topology, (first, second) -> false, new PhantomFarmingService.SocialFacts()
			{
				@Override public PhantomFarmingService.SocialEvidence evidence(long owner, long counterpart, long minute) { throw new AssertionError("D ACTUAL_ATTACK has no farming authority."); }
				@Override public boolean record(long owner, long counterpart, String key, String event, String hash, long minute) { throw new AssertionError("D cannot fabricate farming events."); }
			}, 16, () -> System.currentTimeMillis() / 60000L, PhantomFarmingService.FaultInjector.NONE); cleanup.add(() -> { farming.beginStop(); check(farming.finishStop(), "D farming STOP pending."); }); check(farming.start(), "D farming unavailable.");
			final var executionCatalog = PhantomConversationExecutionCatalog.load(Path.of("data/phantoms/conversation/high-five-ru-conversation-execution-v4.xml"));
			final var execution = new PhantomConversationExecutionService(executionCatalog, new PhantomConversationExecutionStore(profiles, executionCatalog), goals, new L2jPhantomConversationExecutionPort(executionCatalog, knowledge, query, party, materialization, ChatObservationService.getInstance())); cleanup.add(() -> { execution.beginStop(); await(execution::finishStop, 6000, "D outbound STOP pending."); }); check(execution.start(), "D outbound unavailable.");
			final var policy = PhantomPvpPolicy.load(Path.of("data/phantoms/pvp/pvp-policy-v1.xml")); final var pvpStore = new PhantomPvpStore(profiles); final var pvpSocial = new PhantomPvpSocialBridge(social);
			if (recovery)
			{
				final var previous = pvpStore.load(profile).orElseThrow(); check(previous.encounter().stage() == PhantomPvpModel.Stage.COOLDOWN && previous.encounter().counterpart().currentObjectId() == 268481828 && previous.encounter().terminalReason().equals("pvp.retreat.expired"), "D recovery requires the exact prior expired-retreat cooldown; no encounter repair permitted.");
				final long cooldownUntil = previous.encounter().cooldownUntilLogicalNanos(); final long remaining = Math.max(0, cooldownUntil - System.currentTimeMillis() * 1_000_000L);
				report.append("D_RECOVERY_COOLDOWN_WAIT\tprior=").append(previous).append(";remainingMillis=").append(TimeUnit.NANOSECONDS.toMillis(remaining)).append(";ordinary epoch passage/native regeneration only;no persisted deadline/encounter writes\n");
				check(remaining <= TimeUnit.SECONDS.toNanos(policy.limits().pairCooldownSeconds() + 5), "D cooldown exceeds its native policy; no bypass permitted.");
				await(() -> System.currentTimeMillis() * 1_000_000L >= cooldownUntil, TimeUnit.SECONDS.toMillis(policy.limits().pairCooldownSeconds() + 5), "D original cooldown has not expired naturally.");
			}
			final var retreat = new PhantomPvpRetreatCoordinator(navigation, topology, combat);
			final var pvp = new PhantomPvpService(policy, pvpStore, new PhantomPvpContext(policy, combat, party, farming, pvpSocial, materialization), combat, new PhantomPvpConversationBridge(execution), pvpSocial, retreat); cleanup.add(() -> { pvp.beginStop(); await(pvp::finishStop, 6000, "D PvP STOP pending."); });
			final var anchor = query.snapshot().anchorsByNode().values().stream().flatMap(List::stream).filter(value -> value.role() == PhantomTopologyAnchorRole.FARMING).min(Comparator.comparingDouble(value -> measured.calculateDistance3D(value.point().x(), value.point().y(), value.point().z()))).orElseThrow();
			final var root = recovery ? goals.load(profile).orElseThrow().goal() : new PhantomGoal(74007404L, "farm.background", PhantomGoalStatus.ACTIVE, new PhantomDomainRef("profile", Long.toString(profile)), new PhantomDomainRef("npc", "20418"), 1, 0, "background.farm", List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, "20418@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), "farm.background", 500, 0, 0, 0, Map.of(), "fixture074.pvp_recovery", 1);
			check(root.goalId() == 74007404L && root.goalType().equals("farm.background") && root.status() == PhantomGoalStatus.ACTIVE && root.subject().equals(new PhantomDomainRef("profile", "4")), "D original farm goal identity differs; no root mutation permitted.");
			enemy = recovery ? Player.load(268481828) : fixture("task074enemy", "Enemy074", 4, enemyPoint, false); check(enemy != null && !enemy.isGM() && enemy.getAccountName().equals("task074enemy") && enemy.getName().equals("Enemy074") && !enemy.isAlikeDead() && enemy.getClient() == null, "D exact living native enemy unavailable; no replacement permitted."); enemy.setOnlineStatus(true, false); enemy.spawnMe(); final Player target = enemy;
			if (!recovery) { try (var lease = materialization.tryAcquireAction(profile).orElseThrow()) { actor.setRunning(); } }
			check(!actor.isInsidePeaceZone(target) && GeoEngine.getInstance().canSeeTarget(actor, target) && !L2jCombatBackend.nativePvpSkills(actor, PhantomCombatMode.MELEE_PHYSICAL, 16).isEmpty(), "D native PvP admission/learned skills unavailable before measurement.");
			if (!recovery) { check(PhantomNativeTravelActions.safePveWalkSegment(actor, field.x() - 600, field.y(), field.z(), List.copyOf(World.getInstance().getVisibleObjectsInRange(actor, Monster.class, 1200)), Set.of()), "D preflight escape crosses native PvE danger."); }
			retreatCandidates(actor, target, paths, report);
			final var skills = new AtomicInteger(); final var skillIds = new java.util.concurrent.ConcurrentSkipListSet<Integer>();
			listener = new ConsumerEventListener(actor, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) -> { if (event.getAttacker() == measured && event.getTarget() == target && event.getSkill() != null && event.getDamage() > 0) { skills.incrementAndGet(); skillIds.add(event.getSkill().getId()); } }, actor); actor.addListener(listener);
			final var incomingHits = new AtomicInteger(); final var incomingDamage = new java.util.concurrent.atomic.DoubleAdder();
			incoming = new ConsumerEventListener(target, EventType.ON_CREATURE_DAMAGE_DEALT, (OnCreatureDamageDealt event) -> { if (event.getAttacker() == target && event.getTarget() == measured && event.getDamage() > 0) { incomingHits.incrementAndGet(); incomingDamage.add(event.getDamage()); } }, target); target.addListener(incoming);
			final long beforeShots = actor.getInventory().getInventoryItemCount(1463, -1), beforeCpItems = actor.getInventory().getInventoryItemCount(5592, -1); final double beforeCp = actor.getCurrentCp(); final var origin = PhantomNativeTravelActions.point(actor);
			if (!recovery) { goals.insert(profile, root); }
			report.append(recovery ? "D_RECOVERY_MEASUREMENT\t" : "D_MEASUREMENT\t").append("profile=").append(profile).append(";actor=").append(actorId).append(";fixturePriorAdena=2500;finiteFixtureD1463=").append(beforeShots).append(";finiteFixtureCP5592=").append(beforeCpItems).append(";nativeCurrentCP=").append(beforeCp).append(";not paid/earned by this episode;no resource/HP/CP/position writes after marker;enemy=").append(enemy.getObjectId()).append(";enemyPoint=").append(PhantomNativeTravelActions.point(enemy)).append(";enemyHP/CP=").append(enemy.getCurrentHp()).append('/').append(enemy.getCurrentCp()).append(";origin=").append(origin).append('\n');
			actor.setTarget(target); target.setTarget(actor); check(!actor.recentPlayerAttackers(16).contains(target.getObjectId()), "D incoming damage witness is not fresh; no stale authority reused.");
			for (int attempt = 1; attempt <= 3 && !actor.recentPlayerAttackers(16).contains(target.getObjectId()); attempt++)
			{
				attackAdmission(actor, target, "BEFORE", attempt, incomingHits.get(), incomingDamage.sum(), report); target.doAttack(actor);
				final long hitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4); while (!actor.recentPlayerAttackers(16).contains(target.getObjectId()) && !actor.isAlikeDead() && !target.isAlikeDead() && System.nanoTime() < hitDeadline) { Thread.sleep(50); }
				attackAdmission(actor, target, "AFTER", attempt, incomingHits.get(), incomingDamage.sum(), report);
			}
			target.abortAttack(); target.getAI().setIntention(Intention.IDLE);
			check(actor.recentPlayerAttackers(16).contains(target.getObjectId()), "D no canonical incoming HP/CP loss after3 bounded native attack submissions; net-pool regeneration is not the predicate.");
			final double targetBeforePvp = target.getCurrentHp() + target.getCurrentCp();
			check(combat.observePvp(profile, List.of(target.getObjectId()), 16, 16).orElseThrow().targets().stream().anyMatch(value -> value.target().objectId() == target.getObjectId() && value.actualAttacker() && value.canonicalContextAllowed()), "D ACTUAL_ATTACK evidence missing.");
			check(pvp.start(), "D PvP owner unavailable."); pvp.materializeSucceeded(profile, actorId);
			long sequence = 0, lastDiagnostic = 0; String last = ""; boolean shortage = false, arrived = false, moved = false; double peakCp = actor.getCurrentCp(), poolAfterShortage = -1;
			final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(75);
			while (System.nanoTime() < until && !actor.isDead())
			{
				topology.updateProfile(profile, new PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId()), ++sequence); pvp.onPulse(); execution.onPulse(); peakCp = Math.max(peakCp, actor.getCurrentCp()); moved |= actor.isMoving() && PhantomNativeTravelActions.point(actor).distanceTo(origin) > 64;
				final var stored = pvpStore.load(profile).orElse(null); final String state = stored == null ? "none" : stored.encounter().stage() + ":" + stored.encounter().terminalReason();
				if (!last.equals(state) || System.nanoTime() - lastDiagnostic >= TimeUnit.SECONDS.toNanos(2)) { retreatDiagnostic(profile, actor, combat, navigation, metrics, retreat, state, report); lastDiagnostic = System.nanoTime(); }
				if (!last.equals(state)) { report.append("D_NATIVE_OWNER\t").append(stored).append(";xyz=").append(actor.getLocation()).append(";shots=").append(actor.getInventory().getInventoryItemCount(1463, -1)).append(";CPstock=").append(actor.getInventory().getInventoryItemCount(5592, -1)).append(";CP=").append(actor.getCurrentCp()).append('\n'); last = state; }
				if (state.contains("supply.required")) { shortage = true; if (poolAfterShortage < 0) { poolAfterShortage = target.getCurrentHp() + target.getCurrentCp(); } }
				if (shortage && state.contains("pvp.retreat.tactical_arrived")) { arrived = true; break; }
				if (stored != null && stored.encounter().stage() == PhantomPvpModel.Stage.COOLDOWN) { break; }
				Thread.sleep(100);
			}
			report.append(recovery ? "D_RECOVERY_NATIVE_EFFECTS\tskills=" : "D_NATIVE_EFFECTS\tskills=").append(skills.get()).append(";ids=").append(skillIds).append(";D1463=").append(beforeShots).append("->").append(actor.getInventory().getInventoryItemCount(1463, -1)).append(";CP5592=").append(beforeCpItems).append("->").append(actor.getInventory().getInventoryItemCount(5592, -1)).append(";CP=").append(beforeCp).append("->peak").append(peakCp).append(";shortage=").append(shortage).append(";moved=").append(moved).append(";tacticalArrived=").append(arrived).append(";xyz=").append(actor.getLocation()).append(";service=").append(pvp.snapshot()).append('\n');
			if (recovery) { check(beforeShots == 0 && actor.getInventory().getInventoryItemCount(1463, -1) == 0 && skills.get() == 0 && combat.metrics().normalAttacks() == 0 && combat.metrics().skillCastsIssued() == 0 && target.getCurrentHp() + target.getCurrentCp() >= targetBeforePvp, "D zero-shot recovery issued offensive action/damage."); }
			check((recovery || skills.get() > 0) && actor.getInventory().getInventoryItemCount(1463, -1) == 0 && shortage && moved && arrived, "D native tactical movement chain incomplete.");
			if (!recovery) { check(actor.getInventory().getInventoryItemCount(5592, -1) < beforeCpItems && peakCp > beforeCp, "D finite CP item did not have a native CP effect."); }
			check(!actor.isAttackingNow() && !actor.isCastingNow() && target.getCurrentHp() + target.getCurrentCp() >= poolAfterShortage, "D offensive damage continued after shortage.");
			pvp.beginStop(); await(pvp::finishStop, 6000, "D PvP owner did not release.");
			final var catalog = new PhantomCommerceCatalogLoader(Path.of(".")).load(); final var receipts = new PhantomCommerceReceiptStore(profiles); final var supplyStore = new PhantomSupplyIntentStore(profiles, goals);
			final var commerce = new PhantomCommerceService(catalog, receipts, supplyStore, new L2jCommerceBackend(materialization, catalog.catalog(), java.time.Clock.systemDefaultZone())); cleanup.add(() -> { commerce.beginStop(); check(commerce.finishStop(), "D commerce STOP pending."); }); check(commerce.start(), "D recovery commerce unavailable.");
			final var journey = new L2jPhantomSupplyJourney(supplyStore, goals, materialization, commerce, receipts, navigation, knowledge::query, PhantomNormalGatekeeperTravel.load(Path.of("data/phantoms/travel/high-five-normal-gk.xml"), query), id -> true, (goal, player) -> true, (id, goal) -> false, PhantomAutonomousSupplyPolicy.load(Path.of("data/phantoms/life/high-five-autonomous-supply-v1.xml"))); cleanup.add(() -> { journey.beginStop(); check(journey.finishStop(), "D recovery journey STOP pending."); }); check(journey.start(), "D recovery journey unavailable.");
			final var candidates = new PhantomCandidateRegistry(); journey.registerCandidates(candidates); candidates.seal(); final var handlers = new PhantomStepHandlerRegistry(); journey.registerHandlers(handlers); handlers.seal();
			final var planning = new PhantomPlanningContext(profile, root, PhantomCapabilitySet.empty(), PhantomActivityState.ACTIVE, System.nanoTime(), 1); final var selected = new PhantomUtilitySelector().select(candidates.snapshot(), planning).candidate();
			if (selected == null && recovery) { report.append("D_RECOVERY_RESULT\tPARTIAL native zero-shot no-offense+tactical walk PASS;lawful recovery intention unavailable;reason=").append(journey.reason(profile)).append(";wallet=").append(actor.getAdena()).append(";original full chain remains PARTIAL;no refill/root/encounter repair\n"); return false; }
			check(selected != null, "D no lawful supply/recovery intention after tactical retreat."); final var plan = selected.planFactory().create(planning); final var result = handlers.snapshot().get(plan.steps().getFirst().actionKey()).execute(new PhantomStepContext(profile, root, plan, plan.steps().getFirst(), PhantomActivityState.ACTIVE, System.nanoTime(), 1, () -> false));
			report.append("D_RECOVERY_INTENT\t").append(result).append(";reason=").append(journey.reason(profile)).append(";intent=").append(journey.automaticIntent(profile)).append(";wallet=").append(actor.getAdena()).append(recovery ? ";no refill/root/encounter repair\nD_RECOVERY_RESULT\tPASS bounded retained ACTUAL_ATTACK zero-shots->no-offense->native tactical walk->lawful recovery intent;original full episode remains PARTIAL;no renewed skill/CP/paidshot claim\n" : ";no refill\nD_RESULT\tPASS bounded ACTUAL_ATTACK learned skill+finite native CP+shots->depletion->tactical walk->lawful recovery intent;full proactive causes DEFERRED\n");
			return true;
		}
		catch (Exception | AssertionError failure) { failure.printStackTrace(System.err); report.append(recovery ? "D_RECOVERY_RESULT\tPARTIAL;original full chain remains PARTIAL;" : "D_RESULT\tPARTIAL;").append(failure.getClass().getSimpleName()).append(':').append(failure.getMessage()).append('\n'); return false; }
		finally
		{
			if (listener != null && actor != null) { actor.removeListener(listener); }
			if (incoming != null && enemy != null) { enemy.removeListener(incoming); }
			try { if (enemy != null) { enemy.abortAttack(); enemy.abortCast(); enemy.stopAllTasks(); enemy.storeMe(); enemy.deleteMe(); } }
			finally { PhantomPvp072Native.closeAll(cleanup); }
		}
	}

	private static void attackAdmission(Player actor, Player enemy, String phase, int attempt, int hits, double damage, StringBuilder report)
	{
		report.append("D_ATTACK_ADMISSION\tphase=").append(phase).append(";attempt=").append(attempt).append(";distance=").append(actor.calculateDistance3D(enemy)).append(";nativeLos=").append(GeoEngine.getInstance().canSeeTarget(enemy, actor)).append(";peace=").append(actor.isInsidePeaceZone(enemy))
			.append(";actorXYZ=").append(actor.getLocation()).append(";actorHP/CP=").append(actor.getCurrentHp()).append('/').append(actor.getCurrentCp()).append(";actorAI=").append(actor.hasAI() ? actor.getAI().getIntention() : "NONE").append(";actorTargetable=").append(actor.isTargetable()).append(";actorInvul=").append(actor.isInvul()).append(";actorTeleport=").append(actor.isTeleporting()).append(";actorDead=").append(actor.isAlikeDead())
			.append(";enemyXYZ=").append(enemy.getLocation()).append(";enemyHP/CP=").append(enemy.getCurrentHp()).append('/').append(enemy.getCurrentCp()).append(";enemyAI=").append(enemy.hasAI() ? enemy.getAI().getIntention() : "NONE").append(";enemyAttackDisabled=").append(enemy.isAttackDisabled()).append(";enemyAttacking=").append(enemy.isAttackingNow()).append(";enemyCasting=").append(enemy.isCastingNow()).append(";enemySkillsDisabled=").append(enemy.isAllSkillsDisabled()).append(";enemyMovementDisabled=").append(enemy.isMovementDisabled()).append(";enemySitting=").append(enemy.isSitting()).append(";enemyTeleport=").append(enemy.isTeleporting()).append(";enemyDead=").append(enemy.isAlikeDead()).append(";enemyWeapon=").append(enemy.getActiveWeaponItem() == null ? 0 : enemy.getActiveWeaponItem().getId())
			.append(";incomingNativeDamageEvents=").append(hits).append(";incomingNativeDamage=").append(damage).append(";canonicalRecentDamageAttackers=").append(actor.recentPlayerAttackers(16)).append(";noResourceOrPositionWrites\n");
	}
	private static void retreatDiagnostic(long profile, Player actor, PhantomCombatService combat, PhantomNavigationService navigation, PhantomMetrics metrics, PhantomPvpRetreatCoordinator retreat, String stage, StringBuilder report)
	{
		final var requests = new java.util.ArrayList<PhantomNavigationService.RequestSnapshot>(); for (long id = 1; id <= 8; id++) { navigation.find(id).ifPresent(requests::add); }
		final var effects = combat.metrics();
		report.append("D_RETREAT_DIAGNOSTIC\tstage=").append(stage).append(";profile=").append(profile).append(";pendingRetreats=").append(retreat.activeCount()).append(";xyz=").append(actor.getLocation())
			.append(";AI=").append(actor.hasAI() ? actor.getAI().getIntention() : "NONE").append(";moving=").append(actor.isMoving()).append(";nativeDestination=").append(actor.getXdestination()).append(',').append(actor.getYdestination()).append(',').append(actor.getZdestination())
			.append(";moveDisabled=").append(actor.isMovementDisabled()).append(";skillsDisabled=").append(actor.isAllSkillsDisabled()).append(";attacking=").append(actor.isAttackingNow()).append(";casting=").append(actor.isCastingNow()).append(";sitting=").append(actor.isSitting()).append(";dead=").append(actor.isAlikeDead())
			.append(";navigation=").append(navigation.snapshot()).append(";requests1to8=").append(requests).append(";navMetrics=").append(metrics.snapshot().navigation()).append(";combat=").append(combat.snapshot())
			.append(";externalRequested=").append(effects.externalRequested()).append(";externalAcquired=").append(effects.externalAcquired()).append(";externalRejected=").append(effects.externalRejected()).append(";externalReleased=").append(effects.externalReleased()).append(";externalRouteIssued=").append(effects.externalRouteIssued()).append(";normalAttacks=").append(effects.normalAttacks()).append(";skillCasts=").append(effects.skillCastsIssued()).append('\n');
	}
	private static void retreatCandidates(Player actor, Player threat, L2jNavigationBackend paths, StringBuilder report)
	{
		final var origin = PhantomNativeTravelActions.point(actor); final var mobs = List.copyOf(World.getInstance().getVisibleObjectsInRange(actor, Monster.class, 1200)); final double bearing = Math.atan2((double) actor.getY() - threat.getY(), (double) actor.getX() - threat.getX()); int index = 0;
		for (int distance : new int[] {600, 375}) { for (double turn : new double[] {0, -Math.PI / 6, Math.PI / 6})
		{
			final int x = origin.x() + (int) Math.round(distance * Math.cos(bearing + turn)), y = origin.y() + (int) Math.round(distance * Math.sin(bearing + turn)); final var point = paths.walkSurface(new PhantomNavigationPoint(x, y, origin.z(), origin.instanceId()), 256).orElse(null);
			report.append("D_RETREAT_CANDIDATE_READONLY\tindex=").append(index++).append(";actualOrigin=").append(origin).append(";actualThreat=").append(PhantomNativeTravelActions.point(threat)).append(";surface=").append(point)
				.append(";hasGeo=").append(GeoEngine.getInstance().hasGeo(x, y)).append(";nativeDirect=").append(point != null && paths.canWalkDirect(origin, point)).append(";nativePveSafe=").append(point != null && PhantomNativeTravelActions.safePveWalkSegment(actor, point.x(), point.y(), point.z(), mobs, Set.of())).append(";diagnosticOnlyNoRouteIssued\n");
		} }
	}
	private static Player fixture(String account, String name, int classId, PhantomNavigationPoint position, boolean survivor)
	{
		final var player = Player.create(PlayerTemplateData.getInstance().getTemplate(0), account, name, new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false)); check(player != null && !player.isGM(), "D ordinary fixture unavailable."); if (survivor) { player.stopAllTasks(); } player.setPlayerClass(classId); player.setBaseClass(classId); player.getStat().setExp(player.getStat().getExpForLevel(20)); player.getStat().setLevel((byte) 20); player.giveAvailableAutoGetSkills();
		for (var skill : SkillTreeData.getInstance().getAllAvailableSkills(player, player.getPlayerClass(), true, true, true)) { player.addSkill(skill, true); }
		for (var item : player.getInventory().getItems().stream().filter(value -> value.isEquipped()).toList()) { player.getInventory().unEquipItemInSlot(item.getLocationSlot()); }
		for (int id : survivor ? new int[] {129, 352, 2378} : new int[] {2, 352, 2378, 2493}) { player.getInventory().equipItem(player.addItem(ItemProcessType.REWARD, id, 1, player, false)); }
		if (survivor) { check(player.getAdena() == 0, "D fresh prior-wallet fixture must start empty."); player.addAdena(ItemProcessType.REWARD, 2500, player, false); player.addItem(ItemProcessType.REWARD, 1463, 9, player, false); player.addItem(ItemProcessType.REWARD, 5592, 2, player, false); }
		player.setXYZ(position.x(), position.y(), position.z()); player.setCurrentHp(player.getMaxHp()); player.setCurrentMp(player.getMaxMp()); player.setCurrentCp(survivor ? 10 : player.getMaxCp()); return player;
	}

	private static PhantomNavigationPoint scene(StringBuilder report)
	{
		final var paths = new L2jNavigationBackend(); final var geo = GeoEngine.getInstance(); int samples = 0;
		final var sources = World.getInstance().getVisibleObjects().stream().filter(value -> value instanceof Monster mob && (mob.getId() == 20038 || mob.getId() == 20418) && mob.isSpawned() && !mob.isDead() && mob.getInstanceId() == 0).map(value -> (Monster) value).sorted(Comparator.comparingInt(Monster::getObjectId)).limit(3).toList();
		for (var source : sources) { for (int direction = 0; direction < 8; direction++)
		{
			samples++; final double angle = direction * Math.PI / 4; final int x = source.getX() + (int) Math.round(1200 * Math.cos(angle)), y = source.getY() + (int) Math.round(1200 * Math.sin(angle));
			final var point = paths.walkSurface(new PhantomNavigationPoint(x, y, source.getZ(), 0), 256).orElse(null); if (point == null || !geo.hasGeo(x, y)) { continue; }
			final var escape = paths.walkSurface(new PhantomNavigationPoint(x - 600, y, point.z(), 0), 256).orElse(null); final var enemy = paths.walkSurface(new PhantomNavigationPoint(x + 80, y, point.z(), 0), 256).orElse(null);
			if (escape == null || enemy == null || !legal(point) || !legal(escape) || !legal(enemy) || !paths.canWalkDirect(point, escape) || !paths.canWalkDirect(point, enemy)) { continue; }
			final var region = World.getInstance().getRegion(x, y, point.z()); if (region == null) { continue; } boolean safe = true;
			for (var surrounding : region.getSurroundingRegions()) { for (var object : surrounding.getVisibleObjects()) { if (object instanceof Monster mob && !mob.isDead() && mob.getInstanceId() == 0 && Math.abs((long) mob.getZ() - point.z()) <= 256 && mob.isAggressive() && Math.min(mob.calculateDistance2D(x, y, point.z()), mob.calculateDistance2D(escape.x(), escape.y(), escape.z())) < Math.max(400, mob.getAggroRange() + 200)) { safe = false; break; } } if (!safe) { break; } }
			if (!safe) { continue; }
			report.append("D_PREFLIGHT\tpoint=").append(point).append(";escape=").append(escape).append(";directNativeGeo=FOUND;distance=600;no city requirement;enemy not created;samples=").append(samples).append('\n'); return point;
		} }
		throw new AssertionError("D_NO_SHORT_GEO_ESCAPE after " + samples + " bounded samples; no opponent created.");
	}
	private static boolean legal(PhantomNavigationPoint point) { final var zones = ZoneManager.getInstance(); return zones.getZone(point.x(), point.y(), point.z(), PeaceZone.class) == null && zones.getZone(point.x(), point.y(), point.z(), NoPvPZone.class) == null && zones.getZone(point.x(), point.y(), point.z(), WaterZone.class) == null; }
	private static void await(java.util.function.BooleanSupplier condition, long millis, String message) throws Exception { final long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis); boolean done = condition.getAsBoolean(); while (!done && System.nanoTime() < until) { Thread.sleep(50); done = condition.getAsBoolean(); } check(done, message); }
	private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}

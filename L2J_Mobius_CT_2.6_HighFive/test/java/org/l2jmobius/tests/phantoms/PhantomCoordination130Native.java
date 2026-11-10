/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.l2jmobius.commons.database.DatabaseFactory;
import org.l2jmobius.gameserver.GameServer;
import org.l2jmobius.gameserver.Shutdown;
import org.l2jmobius.gameserver.config.ServerConfig;
import org.l2jmobius.gameserver.config.custom.PhantomPlayersConfig;
import org.l2jmobius.gameserver.data.xml.PlayerTemplateData;
import org.l2jmobius.gameserver.geoengine.GeoEngine;
import org.l2jmobius.gameserver.localplay.*;
import org.l2jmobius.gameserver.model.World;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence;
import org.l2jmobius.gameserver.model.actor.PlayerNativeWork;
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.groups.Party;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.PhantomDiagnosticTrace;
import org.l2jmobius.gameserver.phantoms.PhantomMetrics;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNativeTravelActions;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationCancellationToken;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPolicy;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationRequest;
import org.l2jmobius.gameserver.phantoms.navigation.PhantomWalkCorridorPlanner;
import org.l2jmobius.gameserver.phantoms.party.*;
import org.l2jmobius.gameserver.phantoms.population.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecycleBridge;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationLifecyclePort;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomRelevanceSignalPort;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint;

/** Native modes of the existing SQL130 entrypoint: real GameServer and its configured owners. */
final class PhantomCoordination130Native
{
    private static final ThreadLocal<Boolean> AUTHORITY_SQL_INITIALIZATION = ThreadLocal.withInitial(() -> false);
    static void initializeAuthoritySql(Path module) throws Exception
    {
        final Path runtime = PhantomCoordination130Integration.runtimeRoot(module).toRealPath();
        System.setProperty("phantom.task130.infrastructure", "true");
        System.setProperty("phantom.task130.runtime", runtime.toString());
        System.setProperty("phantom.test.config", runtime.resolve("config/Database.ini").toString());
        AUTHORITY_SQL_INITIALIZATION.set(true);
        try { PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(130, module, module.getParent().getParent().resolve("evidence"))); }
        catch (Exception | Error failure)
        {
            if (DatabaseFactory.isInitialized()) { org.l2jmobius.commons.threads.ThreadPool.shutdown(); DatabaseFactory.close(); }
            throw failure;
        }
        finally { AUTHORITY_SQL_INITIALIZATION.remove(); }
    }
    static Path scene(Path runtime)
    {
        final String label = System.getProperty("phantom.task130.fixture", "original");
        check(List.of("original", "morning").contains(label), "FIXTURE_LABEL");
        check(!PhantomCoordination130Integration.firstPartyLane() || label.equals("original"), "FIRST_PARTY_FIXTURE_LABEL");
        return label.equals("original") ? runtime : runtime.resolve("morning");
    }

    static void recoverAgreement(Path module, PhantomProfileRepository profiles) throws Exception
    {
        final Path runtime = PhantomCoordination130Integration.runtimeRoot(module).toRealPath();
        check(runtime.equals(Path.of(".").toRealPath()) && Files.readString(runtime.resolve("owner130.txt")).matches("TASK130:[0-9a-f]{32}"), "RECOVERY_OWN_RUNTIME");
        check(Files.mismatch(runtime.resolve("config/Database.ini"), PhantomCoordination130Integration.localRoot(module).resolve("Database.ini")) == -1
            && Files.readString(runtime.resolve("config/Custom/PhantomPlayers.ini")).contains("EnablePhantomSystem = False") && !PhantomSystem.hasConfiguredInstance(), "RECOVERY_DISABLED");
        final var ids = authorizedProfiles(runtime);
        final var managed = profiles.listManagedAfter(PhantomPopulationState.COMPONENT_TYPE, 0, 16).stream().map(entry -> entry.profile().profileId()).toList();
        final boolean exactManagedPage = managed.size() < 16 && Set.copyOf(managed).equals(Set.copyOf(ids));
        System.out.println("SQL130_SHARED_MANAGED_PAGE ids=" + managed + " exactAuthorizedScope=" + exactManagedPage);
        check(exactManagedPage, "PREFLIGHT_MANAGED_PARTY_PAGE_SCOPE");
        final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
        final long now = System.currentTimeMillis();
        for (long id : ids) { System.out.println("SQL130_RECOVERY_INSPECT profile=" + id + " group=" + logical.groupFor(id).map(row -> row.group().phase() + ":expired=" + (now >= row.group().expiresAtMillis()))); }
        System.out.println("SQL130_RECOVERY_INSPECT_ONLY;SQL_WRITES=0;OLD_OBJECTIVE_PINNED=false;REQUIRED_OWNER=PhantomScheduler/PhantomExecutionControlPort/PhantomAutonomousFarmParty");
        final var config = read(runtime.resolve("config/Custom/PhantomPlayers.ini"));
        final var zone = ZoneId.of(config.getProperty("PhantomPopulationTimeZone", "UTC"));
        final var seeking = new PhantomPlayersConfig.PartySeekingSettings(Boolean.parseBoolean(config.getProperty("EnablePhantomAutonomousPartySeeking", "true")),
            Integer.parseInt(config.getProperty("PhantomPartySeekChance1To39", "0")), Integer.parseInt(config.getProperty("PhantomPartySeekChance40To51", "20")),
            Integer.parseInt(config.getProperty("PhantomPartySeekChance52To60", "35")), Integer.parseInt(config.getProperty("PhantomPartySeekChance61To75", "50")),
            Integer.parseInt(config.getProperty("PhantomPartySeekChance76To85", "80")), Boolean.parseBoolean(config.getProperty("PhantomPartyPreferHumanFollow", "true")));
        final var catalog = PhantomPopulationCatalog.load(runtime.resolve("data/phantoms/population/high-five-population-v3.xml"), zone);
        final var eligible = new LinkedHashSet<Long>();
        final var goals = new PhantomGoalStateStore(profiles);
        final var histories = new PhantomBackgroundCatchupStore(profiles, goals);
        for (long id : ids)
        {
            final var state = new PhantomBackgroundStateCodec().decode(profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
            final var root = goals.load(id).orElseThrow().goal();
            final var population = new PhantomPopulationStateCodec().decode(profiles.findComponent(id, PhantomPopulationState.COMPONENT_TYPE).orElseThrow().payload());
            final var schedule = catalog.evaluate(population.scheduleTemplate(), Instant.ofEpochMilli(now), zone, population.schedulePhaseMinutes());
            final boolean consent = seeking.seeks(id, state.progress().level());
            final var receipt = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(profiles).find(id);
            final boolean obligations = profiles.findComponent(id, PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent()
                || receipt.filter(row -> !row.receipt().state().terminal() || row.receipt().state() == org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceipt.State.INCONSISTENT).isPresent()
                || profiles.findComponent(id, PhantomNativeContext.COMPONENT_TYPE).map(row -> PhantomNativeContext.decode(row.payload()).phase() != PhantomNativeContext.Phase.COMPLETED).orElse(true)
                || List.of("supply.intent", "learning.intent", "career.intent").stream().map(type -> profiles.findComponent(id, type)).flatMap(Optional::stream)
                    .anyMatch(PhantomHistoricalBackgroundService::nativeIntentPending);
            final boolean claimed = new PhantomPartyStore(profiles).loadLogicalClaim(id).filter(PhantomPartyPersistencePort.StoredLogicalClaim::active).isPresent();
            final var history = histories.load(id).map(row -> row.state().status()).orElse(null);
            final boolean ready = population.state() == PhantomPopulationState.State.READY && population.catalogHash().equals(catalog.hash())
                && state.state() == PhantomBackgroundState.State.READY && schedule.state().requiresMaterialization() && consent
                && history == PhantomBackgroundCatchupState.Status.COMPLETE && PhantomAutonomousFarmParty.ordinaryIntent(id, root) && !obligations && !claimed;
            System.out.println("SQL130_SCENE_MEMBER profile=" + id + " class=" + state.identity().activeClassId() + " level=" + state.progress().level()
                + " role=" + PhantomHistoricalBackgroundPlanner.logicalRole(state) + " schedule=" + schedule + " consent=" + consent + " obligations=" + obligations
                + " claimed=" + claimed + " history=" + history + " eligibleNow=" + ready + " position=" + state.position());
            if (ready) { eligible.add(id); }
        }
        System.out.println("SQL130_SCENE_INSPECT_ONLY;eligible=" + eligible + ";COMMON_SOURCE=UNKNOWN;SQL_WRITES=0;CATALOG_NATIVE_BOOTSTRAP_NOT_RUN=true");
    }

    static void inspectAuthority(long id, PhantomBackgroundState.Hashes saved, PhantomBackgroundState.Hashes current, java.util.function.Consumer<String> evidence)
    {
        emitAuthority("NATIVE130_AUTHORITY", id, saved, current, evidence);
        check(saved.equals(current), "PREFLIGHT_AUTHORITY_HASH_" + id);
    }
    static void emitAuthority(String prefix, long id, PhantomBackgroundState.Hashes saved, PhantomBackgroundState.Hashes current, java.util.function.Consumer<String> evidence)
    {
        final var components = List.of("knowledge", "topology", "progression", "commerce");
        final var before = List.of(saved.knowledge(), saved.topology(), saved.progression(), saved.commerce());
        final var after = List.of(current.knowledge(), current.topology(), current.progression(), current.commerce());
        for (int index = 0; index < components.size(); index++)
        {
            evidence.accept(prefix + " profile=" + id + " component=" + components.get(index)
                + " saved=" + before.get(index) + " current=" + after.get(index) + " equal=" + before.get(index).equals(after.get(index)));
        }
    }
    private static void inspectLoadedAuthority(Path runtime, PhantomProfileRepository profiles, List<Long> ids, PhantomSystem.PlanningInspection inspection) throws Exception
    {
        check(!PhantomSystem.hasConfiguredInstance(), "AUTHORITY_DIAGNOSTIC_BEFORE_EXECUTION");
        final String before = persisted(profiles, ids);
        final var generation = inspection.planner().generation();
        final Path travelFile = runtime.resolve("data/phantoms/travel/high-five-normal-gk.xml");
        final var travel = org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.load(travelFile, inspection.topology());
        final String canonical = inspection.topology().snapshot().canonicalHash();
        final var digest = MessageDigest.getInstance("SHA-256");
        for (String value : List.of("BACKGROUND_TRAVEL_V1", org.l2jmobius.gameserver.phantoms.background.PhantomNormalGatekeeperTravel.PHANTOM_TRAVEL_TARIFF, canonical, travel.hash()))
        { digest.update(value.getBytes(StandardCharsets.US_ASCII)); digest.update((byte) 0); }
        final String composed = HexFormat.of().formatHex(digest.digest());
        check(travel.hash().equalsIgnoreCase(hash(travelFile)), "AUTHORITY_DIAGNOSTIC_GK_BYTES");
        System.out.println("NATIVE130_GK hash=" + travel.hash() + " legs=" + travel.legs().size() + " canonicalTopology=" + canonical
            + " composedTopology=" + composed + " currentTopology=" + generation.authorityHashes().topology()
            + " equalsCurrent=" + composed.equals(generation.authorityHashes().topology()) + " catalogRead=PUBLIC_LOADER_EXACT_RUNTIME_FILE");
        final var histories = new PhantomBackgroundCatchupStore(profiles, new PhantomGoalStateStore(profiles));
        for (long id : ids)
        {
            final var state = new PhantomBackgroundStateCodec().decode(profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
            final var history = histories.load(id).orElseThrow();
            emitAuthority("NATIVE130_AUTHORITY", id, state.hashes(), generation.authorityHashes(), System.out::println);
            emitAuthority("NATIVE130_CATCHUP_AUTHORITY", id, history.state().authorityHashes(), generation.authorityHashes(), System.out::println);
            System.out.println("NATIVE130_AUTHORITY_STATE profile=" + id + " background=" + state.state() + " progress=" + state.progress() + " receipt=" + state.receipt()
                + " catchup=" + history.state().status() + " catchupRow=" + history.rowVersion() + " cursor=" + history.state().cursorEpochMinute()
                + " target=" + history.state().targetEpochMinute() + " savedKnowledgeGeneration=" + history.state().knowledgeGeneration()
                + " currentKnowledgeGeneration=" + generation.knowledgeGeneration() + " savedTopologyGeneration=" + history.state().topologyGeneration()
                + " currentTopologyGeneration=" + generation.topologyGeneration());
        }
        check(generation.equals(inspection.planner().generation()), "AUTHORITY_DIAGNOSTIC_GENERATION_CHANGED");
        check(before.equals(persisted(profiles, ids)), "AUTHORITY_DIAGNOSTIC_READ_ONLY_WITNESS");
        System.out.println("NATIVE130_AUTHORITY_DIAGNOSTIC_READ_ONLY profiles=" + ids + ";SQL_WRITES=0;CUT=false;MATERIALIZATION=false;GAMEPLAY=false");
    }
    private record ProvedAgreement(PhantomLogicalParty group, boolean existing) {}

    private static ProvedAgreement inspectLoadedPlanner(Path runtime, PhantomProfileRepository profiles, List<Long> ids,
        PhantomSystem.PlanningInspection inspection, boolean restart, Properties saved,
        java.util.function.Consumer<PhantomPartyPersistencePort.StoredLogicalGroup> committed) throws Exception
    {
        final boolean recovering = !inspection.recoveredProfiles().isEmpty();
        check(!PhantomSystem.hasConfiguredInstance() || recovering, "PREFLIGHT_BEFORE_EXECUTION");
        final var managed = inspection.managedPartyPage().apply(0L, 16).stream().map(entry -> entry.profile().profileId()).toList();
        check(managed.size() < 16 && Set.copyOf(managed).equals(Set.copyOf(authorizedProfiles(runtime))), "PREFLIGHT_MANAGED_PARTY_PAGE_SCOPE");
        final long recoveryStarted = System.nanoTime();
        final var sceneIds = recovering ? ids.stream().filter(inspection.recoveredProfiles()::contains).toList() : ids;
        final var recoveryBefore = preflightWitness(profiles, sceneIds, true);
        final var catalog = PhantomPopulationCatalog.load(runtime.resolve("data/phantoms/population/high-five-population-v3.xml"), inspection.settings().populationTimeZone());
        final var goals = new PhantomGoalStateStore(profiles); final var parties = new PhantomPartyStore(profiles);
        final var histories = new PhantomBackgroundCatchupStore(profiles, goals);
        final var coordinator = new PhantomLogicalPartyCoordinator(parties);
        final var existingRow = restart || recovering ? currentAgreement(coordinator, sceneIds, System.currentTimeMillis()).orElse(null) : null;
        final var existing = existingRow == null ? null : existingRow.group();
        final boolean confirmedExistingSource = PhantomCoordination130Integration.rendezvousLane() && existing != null
            && priorPhysicalAgreement(scene(runtime), existing);
        if (restart) { check(existing != null && existing.groupId().equals(saved.getProperty("group")), "RESTART_GROUP_NOT_CURRENT"); }
        final var eligibleIds = new ArrayList<Long>();
        for (long id : ids)
        {
            checkPreflightBudget(System.nanoTime() - recoveryStarted, true);
            final long now = System.currentTimeMillis();
            final var state = new PhantomBackgroundStateCodec().decode(profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
            final var root = goals.load(id).orElseThrow().goal();
            final var population = new PhantomPopulationStateCodec().decode(profiles.findComponent(id, PhantomPopulationState.COMPONENT_TYPE).orElseThrow().payload());
            final var calendar = catalog.evaluate(population.scheduleTemplate(), Instant.ofEpochMilli(now), inspection.settings().populationTimeZone(), population.schedulePhaseMinutes());
            final var claim = parties.loadLogicalClaim(id).filter(PhantomPartyPersistencePort.StoredLogicalClaim::active);
            final var receipt = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(profiles).find(id);
            final boolean blocked = profiles.findComponent(id, PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent() || receipt.filter(row -> !row.receipt().state().terminal()).isPresent()
                || profiles.findComponent(id, PhantomNativeContext.COMPONENT_TYPE).map(row -> PhantomNativeContext.decode(row.payload()).phase() != PhantomNativeContext.Phase.COMPLETED).orElse(false)
                || inspection.authorityRecovery().nativeIntentPending(id);
            final boolean consent = inspection.settings().partySeeking().seeks(id, state.progress().level());
            final var history = histories.load(id).map(row -> row.state().status()).orElse(null);
            final boolean eligible = population.state() == PhantomPopulationState.State.READY && calendar.state().requiresMaterialization()
                && state.state() == PhantomBackgroundState.State.READY && consent && !blocked && sceneIds.contains(id)
                && history == PhantomBackgroundCatchupState.Status.COMPLETE
                && (claim.isEmpty() || existing != null && claim.get().groupId().equals(existing.groupId()) && claim.get().generation() == existing.generation())
                && PhantomAutonomousFarmParty.ordinaryIntent(id, root);
            System.out.println("NATIVE130_PREFLIGHT_MEMBER id=" + id + " eligible=" + eligible + " class=" + state.identity().activeClassId() + " level=" + state.progress().level()
                + " role=" + inspection.authorityRecovery().potentialPartyRole(id, state) + " calendar=" + calendar + " consent=" + consent + " obligations=" + blocked + " claimed=" + claim.isPresent() + " history=" + history + " position=" + state.position());
            if (!eligible) { continue; }
            final var savedAuthority = state.hashes();
            final var currentAuthority = inspection.planner().generation().authorityHashes();
            emitAuthority("NATIVE130_PREPARE_AUTHORITY", id, savedAuthority, currentAuthority, System.out::println);
            check(savedAuthority.topology().equals(currentAuthority.topology()) && savedAuthority.progression().equals(currentAuthority.progression())
                && savedAuthority.commerce().equals(currentAuthority.commerce()), "PREFLIGHT_NON_KNOWLEDGE_AUTHORITY_" + id);
            System.out.println("NATIVE130_PREFLIGHT_INPUT id=" + id + " vitals=" + state.vitals() + " combat=" + state.combat() + " loadout=" + state.loadout() + " root=" + root.goalType() + ":" + root.goalId() + ":" + root.revision());
            if (inspection.authorityRecovery().potentialPartyRole(id, state).equals("support.healer"))
            {
                boolean heal = false;
                try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT skill_id,skill_level FROM character_skills WHERE charId=? AND class_index=?"))
                {
                    connection.setReadOnly(true); query.setInt(1,state.identity().characterObjectId()); query.setInt(2,state.identity().classIndex());
                    try (var rows = query.executeQuery()) { while (rows.next()) { final var skill = org.l2jmobius.gameserver.data.xml.SkillData.getInstance().getSkill(rows.getInt(1),rows.getInt(2)); if (skill != null && "combat.heal".equals(org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.nativeCapability(skill))) { heal = true; } } }
                }
                check(heal, "PREFLIGHT_LEARNED_HEAL_" + id);
                System.out.println("NATIVE130_PREFLIGHT_SUPPORT id=" + id + " learnedNativeHeal=true");
            }
            preparePreflightAuthority(profiles, id, () -> inspection.authorityRecovery().prepareCurrentAuthority(id));
            check(inspection.authorityRecovery().permitsNormalOperation(id), "PREFLIGHT_HISTORY_NOT_COMPLETE_" + id);
            check(root.equals(goals.load(id).orElseThrow().goal()), "PREFLIGHT_ROOT_CHANGED_" + id);
            eligibleIds.add(id);
        }
        check(recoveryBefore.equals(preflightWitness(profiles, sceneIds, true)), "PREFLIGHT_RECOVERY_CONSERVATION_WITNESS");
        checkPreflightBudget(System.nanoTime() - recoveryStarted, true);
        final long sceneStarted = System.nanoTime(); final long now = System.currentTimeMillis();
        final var before = preflightWitness(profiles, sceneIds, false);
        final var states = new LinkedHashMap<Long, PhantomBackgroundState>();
        final var travelFunds = new LinkedHashMap<Long, Long>();
        for (long id : eligibleIds)
        {
            checkPreflightBudget(System.nanoTime() - sceneStarted, false);
            final var population = new PhantomPopulationStateCodec().decode(profiles.findComponent(id, PhantomPopulationState.COMPONENT_TYPE).orElseThrow().payload());
            check(catalog.evaluate(population.scheduleTemplate(), Instant.ofEpochMilli(now), inspection.settings().populationTimeZone(), population.schedulePhaseMinutes()).state().requiresMaterialization(), "PREFLIGHT_CALENDAR_CHANGED_" + id);
            final var restored = new PhantomBackgroundStateCodec().decode(profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
            inspectAuthority(id, restored.hashes(), inspection.planner().generation().authorityHashes(), System.out::println);
            states.put(id, restored);
            travelFunds.put(id, inspection.authorityRecovery().partyTravelAdena(id, restored)
                .orElseThrow(() -> new IllegalStateException("NATIVE130_PREFLIGHT_PARTY_WALLET_" + id)));
        }
        final long sceneDeadline = sceneStarted + TimeUnit.SECONDS.toNanos(30);
        final java.util.function.BiPredicate<PhantomLogicalParty, List<Integer>> physical = (group, levels) -> physicalProofWithin(
            sceneDeadline, TimeUnit.SECONDS.toNanos(3), attemptDeadline ->
        {
            final var anchor = inspection.topology().findAnchor(group.anchorId()).orElse(null);
            if (anchor == null) { return physicalRefusal(group, "WAIT_ROUTE_ANCHOR"); }
            final var point = anchor.point();
            for (int index = 0; index < group.members().size(); index++)
            {
                final var member = group.members().get(index);
                final var state = states.get(member.profileId());
                if (state == null || state.progress().level() != levels.get(index)) { return physicalRefusal(group, "WAIT_AUTHORITY_" + member.profileId()); }
                if (World.getInstance().findObject(state.identity().characterObjectId()) != null) { return physicalRefusal(group, "WAIT_NATIVE_OWNER_" + member.profileId()); }
                if (!loadedItinerary(inspection, state, group.objective(), travelFunds.get(member.profileId()), attemptDeadline)) { return physicalRefusal(group, "WAIT_ROUTE_" + member.profileId()); }
            }
            final int npc = Integer.parseInt(group.objective().key().split("@")[0]);
            final var band = PhantomPartyFarmEligibility.forRoster(levels).orElse(null);
            if (band == null) { return physicalRefusal(group, "WAIT_GROUP_LEVEL_BAND"); }
            final var spawns = org.l2jmobius.gameserver.data.SpawnTable.getInstance().getSpawns(npc);
            final var mobs = spawns.stream().flatMap(spawn -> spawn.getSpawnedNpcs().stream()).filter(actor -> actor instanceof Monster && !actor.isAlikeDead() && !actor.isRaid()
                && !actor.isInvul() && actor.isTargetable() && actor.isSpawned() && band.contains(actor.getLevel()) && World.getInstance().findObject(actor.getObjectId()) == actor
                && inspection.planner().partyNativeTarget(group.objective(), actor.getId(), new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(actor.getX(), actor.getY(), actor.getZ(), actor.getInstanceId())))
                .sorted(Comparator.comparingDouble(actor -> Math.hypot((long) actor.getX() - point.x(), (long) actor.getY() - point.y()))).limit(4).toList();
            System.out.println("NATIVE130_PREFLIGHT_STOCK_SPAWN npc=" + npc + " configured=" + spawns.size() + " nearbyLive=" + mobs.size());
            final var approach = loadedAnchor(inspection, group.anchorId(), states.get(group.leaderProfileId()).position().heading());
            if (mobs.isEmpty()) { return physicalRefusal(group, "WAIT_LIVE_SPAWN"); }
            if (approach != null && confirmedExistingSource && existing.equals(group))
            {
                System.out.println("NATIVE130_EXISTING_SPAWN_INGRESS_DEFERRED_TO_COMBAT source=" + group.objective().key()
                    + " exactPriorAgreement=true loadedRouteToAnchor=true liveMatching=" + mobs.size());
                return true;
            }
            return approach != null && mobs.stream().anyMatch(mob -> loadedRoute("stockNpc=" + npc + ":object=" + mob.getObjectId(), group.leaderProfileId(),
                approach, new PhantomNavigationPoint(mob.getX(), mob.getY(), mob.getZ(), mob.getInstanceId()), attemptDeadline))
                || physicalRefusal(group, "WAIT_LIVE_SPAWN_INGRESS");
        }).orElseGet(() -> physicalRefusal(group, "WAIT_ROUTE_TIME_BUDGET"));
        final PhantomLogicalParty result;
        try
        {
            if (existing != null)
            {
                check(existing.members().stream().allMatch(member ->
                {
                    final var state = states.get(member.profileId());
                    return state != null && inspection.planner().partySourceAvailable(state, existing.objective(), Set.of(), travelFunds.get(member.profileId()))
                        && PhantomPartyFarmEligibility.compatibleRole(member.role(), inspection.authorityRecovery().potentialPartyRole(member.profileId(), state));
                }), "EXISTING_GROUP_CURRENT_SOURCE_PROOF");
                result = existing;
                check(physical.test(result, result.members().stream().map(member -> states.get(member.profileId()).progress().level()).toList()), "PREFLIGHT_SELECTED_SOURCE_PHYSICAL_" + result.objective().key());
                check(currentAgreement(coordinator, sceneIds, System.currentTimeMillis()).filter(existingRow::equals).isPresent(), "EXISTING_GROUP_CHANGED_DURING_PROOF");
                System.out.println("NATIVE130_EXISTING_GROUP_PROVED group=" + result.groupId() + " generation=" + result.generation() + " newFormation=false");
            }
            else
            {
                final var discovery = inspection.partyOffer().apply(offer ->
                {
                    final var group = offer.proposal().orElseThrow();
                    for (var member : group.members())
                    {
                        final var observed = states.get(member.profileId());
                        if (observed == null || offer.guards().stream().filter(guard -> guard.profileId() == member.profileId() && guard.componentType().equals(PhantomBackgroundState.COMPONENT_TYPE))
                            .noneMatch(guard -> new PhantomBackgroundStateCodec().decode(guard.payload()).equals(observed)))
                        { return physicalRefusal(group, "WAIT_AUTHORITY_CHANGED_" + member.profileId()); }
                    }
                    return physical.test(group, offer.levels());
                }, committed);
                System.out.println("NATIVE130_DISCOVERY status=" + discovery.status() + " nextDue=" + discovery.nextDueMillis());
                result = discovery.proposal().orElseThrow(() -> new IllegalStateException("NATIVE130_" + discovery.status() + " nextDue=" + discovery.nextDueMillis()));
            }
            System.out.println("NATIVE130_SELECTED_SOURCE objective=" + result.objective() + " roster=" + result.members());
        }
        finally { verifySceneWitness(before, preflightWitness(profiles, sceneIds, false)); }
        checkPreflightBudget(System.nanoTime() - sceneStarted, false);
        System.out.println("NATIVE130_PREFLIGHT_PHYSICAL_PROVED objective=" + result.objective() + " members=" + result.members() + " RECOVERY_WRITES=CANONICAL_ONLY SCENE_SQL_WRITES=0 SAME_PRODUCTION_PLANNER=true recoveryMillis=" + TimeUnit.NANOSECONDS.toMillis(sceneStarted - recoveryStarted) + " sceneMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sceneStarted));
        return new ProvedAgreement(result, existing != null);
    }
    private static boolean physicalRefusal(PhantomLogicalParty group, String reason)
    {
        System.out.println("NATIVE130_PROPOSAL_REFUSED group=" + group.groupId() + " source=" + group.objective().key() + " roster=" + group.members() + " reason=" + reason);
        return false;
    }

    /** A slow read-only Geo call cannot hold the commissioning thread or spend the next offer's time. */
    static Optional<Boolean> physicalProofWithin(long sceneDeadline, long maximumAttemptNanos, java.util.function.LongPredicate proof)
    {
        final long now = System.nanoTime();
        final long slice = Math.min(maximumAttemptNanos, (sceneDeadline - now) / 2);
        if (slice <= 0) { return Optional.empty(); }
        final long deadline = now + slice;
        final var work = new FutureTask<>(() -> proof.test(deadline));
        Thread.startVirtualThread(work);
        try
        {
            final boolean result = work.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return System.nanoTime() < deadline ? Optional.of(result) : Optional.empty();
        }
        catch (TimeoutException expired) { return Optional.empty(); }
        catch (InterruptedException interrupted)
        {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("NATIVE130_PHYSICAL_PROOF_INTERRUPTED", interrupted);
        }
        catch (ExecutionException failure) { throw new IllegalStateException("NATIVE130_PHYSICAL_PROOF_FAILED", failure.getCause()); }
        finally { work.cancel(true); }
    }
    private static void publishPreflightAgreement(Path evidence, Path cut, Path agreementFile, String jarHash, PhantomProfileRepository profiles,
        PhantomLogicalPartyCoordinator logical, PhantomLogicalParty expected) throws Exception
    {
        final var ids = expected.members().stream().map(PhantomLogicalParty.Member::profileId).toList();
        final var stored = currentAgreement(logical, ids, System.currentTimeMillis()).orElseThrow(() -> new IllegalStateException("NATIVE130_NO_ORDINARY_AGREEMENT"));
        check(stored.group().equals(expected), "AGREEMENT_READBACK_DIFFERS_FROM_PHYSICAL_PROPOSAL");
        for (long id : ids)
        {
            check(World.getInstance().findObject(profiles.find(id).orElseThrow().characterObjectId()) == null, "ORDINARY_AGREEMENT_ZERO_WORLD_PROOF");
        }
        if (!Files.exists(cut)) { write(cut, "zeroWorld=true\ngameplayFixtureWritesAfterCut=0\n"); }
        write(evidence.resolve("preflight130.cut." + attemptKey()), "jar=" + jarHash + "\nobjective=" + expected.objective()
            + "\nAUTHORITY_PREPARATION=CANONICAL_NATIVE_STORE_READBACK\nGAMEPLAY_FIXTURE_WRITES=0\n");
        write(agreementFile, "group=" + expected.groupId() + "\ngeneration=" + expected.generation() + "\nprofiles=" + ids + "\nobjective=" + expected.objective() + "\nzeroWorld=true\n");
        System.out.println("NATIVE130_PREFLIGHT_PASS objective=" + expected.objective() + " roster=" + expected.members() + " group=" + expected.groupId() + " version=" + stored.rowVersion());
    }
    static org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result preparePreflightAuthority(
        PhantomProfileRepository profiles, long id, java.util.function.Supplier<org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result> preparation) throws Exception
    {
        final var before = preflightWitness(profiles, List.of(id), true);
        final var history = new PhantomBackgroundCatchupStore(profiles, new PhantomGoalStateStore(profiles)).load(id).orElseThrow();
        check(history.state().status() == PhantomBackgroundCatchupState.Status.COMPLETE, "PREFLIGHT_HISTORY_NOT_COMPLETE_" + id);
        final var prepared = preparation.get();
        check(prepared.successful(), "PREFLIGHT_AUTHORITY_REFRESH_" + id + "_" + prepared.reason());
        check(prepared.advancedIntervals() == 0, "PREFLIGHT_RECOVERY_CONSERVATION_WITNESS");
        verifyRecoveryWitness(before, preflightWitness(profiles, List.of(id), true));
        return prepared;
    }
    record PreflightWitness(String canonical, String components) { }
    static void verifySceneWitness(PreflightWitness before, PreflightWitness after) { check(before.equals(after), "PREFLIGHT_READ_ONLY_WITNESS"); }
    static void verifyRecoveryWitness(PreflightWitness before, PreflightWitness after) { check(before.equals(after), "PREFLIGHT_RECOVERY_CONSERVATION_WITNESS"); }

    private static void savePreflightWitness(Path file, PhantomProfileRepository profiles, List<Long> ids, PhantomSystem.PlanningInspection inspection) throws Exception
    {
        final var witness = preflightWitness(profiles, ids, false);
        write(file, "loadedAuthority=" + inspection.planner().generation().authorityHashes() + "\n" + witness.canonical() + "\n" + witness.components());
    }
    static void checkPreflightBudget(long elapsedNanos, boolean recovery)
    {
        check(elapsedNanos >= 0 && elapsedNanos < TimeUnit.SECONDS.toNanos(recovery ? 60 : 30), recovery ? "PREFLIGHT_RECOVERY_DEADLINE" : "PREFLIGHT_SCENE_DEADLINE");
    }
    static PreflightWitness preflightWitness(PhantomProfileRepository profiles, List<Long> ids, boolean recovery) throws Exception
    {
        final var output = new StringBuilder();
        final var components = new StringBuilder();
        final var allowed = Set.of(PhantomBackgroundState.COMPONENT_TYPE, PhantomNativeContext.COMPONENT_TYPE, PhantomExecutionHandoffStore.COMPONENT_TYPE);
        final var lifecycleColumns = Set.of("online", "onlinetime", "lastaccess");
        for (long id : ids)
        {
            output.append("profile=").append(id).append('\n');
            try (var connection = DatabaseFactory.getConnection())
            {
                connection.setReadOnly(true);
                final var queries = List.of("SELECT * FROM characters WHERE charId=?", "SELECT * FROM items WHERE owner_id=? ORDER BY object_id",
                    "SELECT * FROM character_skills WHERE charId=? ORDER BY class_index,skill_id");
                for (String sql : queries)
                {
                    output.append(sql).append('\n');
                    try (var query = connection.prepareStatement(sql))
                    {
                        query.setInt(1, profiles.find(id).orElseThrow().characterObjectId());
                        try (var rows = query.executeQuery())
                        {
                            while (rows.next())
                            {
                                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++)
                                {
                                    final String label = rows.getMetaData().getColumnLabel(column);
                                    if (recovery && sql.contains("FROM characters") && lifecycleColumns.contains(label.toLowerCase(Locale.ROOT))) { continue; }
                                    final String value = rows.getString(column); output.append(label).append('=').append(value == null ? -1 : value.length()).append(':').append(value).append(';');
                                }
                                output.append('\n');
                            }
                        }
                    }
                }
            }
            components.append("profile=").append(id).append('\n');
            for (var row : profiles.listComponents(id).stream().sorted(Comparator.comparing(org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent::componentType)).toList())
            {
                if (recovery && allowed.contains(row.componentType())) { continue; }
                components.append(row.componentType()).append(':').append(row.rowVersion()).append(':').append(row.componentSchemaVersion()).append(':').append(row.createdAt()).append(':').append(row.updatedAt()).append(':').append(Base64.getEncoder().encodeToString(row.payload())).append('\n');
            }
        }
        return new PreflightWitness(output.toString(), components.toString());
    }
    private static boolean loadedItinerary(PhantomSystem.PlanningInspection inspection, PhantomBackgroundState state, PhantomDomainRef source, long adena, long deadline)
    {
        final var itinerary = inspection.planner().rendezvousItinerary(state, source, Set.of(), adena);
        final long id = state.identity().profileId();
        System.out.println("NATIVE130_ITINERARY profile=" + id + " source=" + source + " structural=" + itinerary.status()
            + " departure=" + itinerary.departureAnchorId() + " steps=" + itinerary.steps().stream().map(PhantomNormalGatekeeperTravel.Step::id).toList());
        if (!itinerary.available()) { return false; }
        final var position = state.position();
        var from = loadedAnchor(inspection, itinerary.departureAnchorId(), position.heading());
        if (from == null || !loadedRoute("ingress", id, navigationPoint(position), from, deadline)) { return false; }
        for (var step : itinerary.steps())
        {
            final var to = loadedAnchor(inspection, step.toAnchorId(), position.heading());
            if (to == null) { return false; }
            if (step.type() == PhantomNormalGatekeeperTravel.Type.NORMAL_GATEKEEPER)
            {
                final var leg = step.gatekeeper();
                final boolean available = PhantomNativeTravelActions.available(leg);
                System.out.println("NATIVE130_PREFLIGHT_GK profile=" + id + " leg=" + leg.id() + " available=" + available
                    + " fee=" + PhantomNormalGatekeeperTravel.fee(leg, state.identity().classIndex(), state.progress().level(), System.currentTimeMillis() / 60000)
                    + " reserved=" + PhantomNormalGatekeeperTravel.maximumFee(leg, state.identity().classIndex(), state.progress().level()));
                if (!available) { return false; }
                final var endpoints = PhantomNativeTravelActions.gatekeeperEndpoints(leg);
                if (!loadedRoute(step.id() + ".to_gk", id, from, endpoints.source(), deadline)
                    || !loadedRoute(step.id() + ".from_landing", id, endpoints.landing(), to, deadline)) { return false; }
            }
            else if (!loadedRoute(step.id(), id, from, to, deadline)) { return false; }
            from = to;
        }
        return true;
    }

    private static PhantomNavigationPoint loadedAnchor(PhantomSystem.PlanningInspection inspection, String id, int heading)
    {
        return inspection.topology().findAnchor(id).flatMap(anchor -> L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, heading))
            .map(PhantomCoordination130Native::navigationPoint).orElse(null);
    }

    private static boolean priorPhysicalAgreement(Path evidence, PhantomLogicalParty group) throws Exception
    {
        try (var files = Files.list(evidence))
        {
            for (var file : files.filter(path -> path.getFileName().toString().matches("agreement130[.]((native|restart)-[0-9]{8}-[0-9]{6}[.])?[0-9]+")).limit(23).toList())
            {
                final var agreement = read(file);
                if (group.groupId().equals(agreement.getProperty("group")) && group.objective().toString().equals(agreement.getProperty("objective"))
                    && group.members().stream().map(PhantomLogicalParty.Member::profileId).toList().toString().equals(agreement.getProperty("profiles"))
                    && Long.toString(group.generation()).equals(agreement.getProperty("generation", "1")) && "true".equals(agreement.getProperty("zeroWorld"))) { return true; }
            }
            return false;
        }
    }

    private static PhantomNavigationPoint navigationPoint(PhantomBackgroundState.Position position)
    { return new PhantomNavigationPoint(position.x(), position.y(), position.z(), position.instanceId()); }

    /** Probe one declared connector through the existing native bounded WALK episode planner. */
    private static boolean loadedRoute(String label, long id, PhantomNavigationPoint from, PhantomNavigationPoint to, long deadline)
    {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) { return false; }
        final var geo = GeoEngine.getInstance();
        if (!geo.hasGeo(from.x(), from.y()) || !geo.hasGeo(to.x(), to.y()))
        {
            System.out.println("NATIVE130_PREFLIGHT_GEO profile=" + id + " connector=" + label + " status=NO_GEODATA");
            return false;
        }
        final var navigation = new L2jNavigationBackend();
        final var policy = PhantomNavigationPolicy.productionDefaults();
        final var visited = new HashSet<PhantomNavigationPoint>();
        var current = from;
        double distance = 0;
        for (int episode = 0; episode < 64 && !current.equals(to); episode++)
        {
            final long now = System.nanoTime();
            final int remaining = (int) Math.floor(policy.maximumRouteDistance() - distance);
            if (Thread.currentThread().isInterrupted() || now >= deadline || remaining <= 0 || !visited.add(current)) { break; }
            final var request = new PhantomNavigationRequest(id, current, to, now, Math.min(deadline, now + policy.defaultRequestDeadlineNanos()), remaining);
            final var proof = navigation.findWalkEpisode(request, new PhantomNavigationCancellationToken(), policy, System::nanoTime, navigation, visited);
            System.out.println("NATIVE130_PREFLIGHT_GEO profile=" + id + " connector=" + label + " episode=" + episode + " status=" + proof.status()
                + " nodes=" + proof.nodes() + " queries=" + proof.queries() + " waypoints=" + proof.points().size());
            if (proof.status() != PhantomWalkCorridorPlanner.Status.FOUND || proof.points().isEmpty()) { return false; }
            for (var point : proof.points()) { distance += current.distanceTo(point); current = point; }
        }
        final boolean arrived = !Thread.currentThread().isInterrupted() && System.nanoTime() < deadline && current.equals(to) && distance <= policy.maximumRouteDistance();
        if (!arrived) { System.out.println("NATIVE130_PREFLIGHT_GEO profile=" + id + " connector=" + label + " status=WAIT_ROUTE_BUDGET"); }
        return arrived;
    }
    static Optional<PhantomPartyPersistencePort.StoredLogicalGroup> currentAgreement(PhantomLogicalPartyCoordinator logical, List<Long> eligible, long now)
    {
        return eligible.stream().map(logical::groupFor).flatMap(Optional::stream)
            .filter(row -> row.group().active() && !row.group().requiresSoloExecution(now) && now < row.group().expiresAtMillis()
                && row.group().members().stream().allMatch(member -> eligible.contains(member.profileId()))).findFirst();
    }
    static List<Long> authorizedProfiles(Path runtime) throws Exception
    {
        final var ids = new LinkedHashSet<Long>();
        for (Path file : PhantomCoordination130Integration.firstPartyLane() ? List.of(runtime.resolve("fixture130.properties"))
            : List.of(runtime.resolve("fixture130.properties"), runtime.resolve("morning/fixture130.properties")))
        { if (Files.exists(file)) { Arrays.stream(read(file).getProperty("profiles").split(",")).map(Long::parseLong).forEach(ids::add); } }
        return List.copyOf(ids);
    }
    private static void check(boolean value, String key) { if (!value) { throw new IllegalStateException("NATIVE130_" + key); } }
    private static Properties read(Path path) throws Exception
    {
        final var value = new Properties();
        try (var input = Files.newBufferedReader(path)) { value.load(input); }
        return value;
    }
    private static String hash(Path path) throws Exception
    { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static void write(Path path, String body) throws Exception
    { Files.writeString(path, body, StandardOpenOption.CREATE_NEW); }
    static boolean validateInfrastructure(Path module) throws Exception
    {
        if (!Boolean.getBoolean("phantom.task130.infrastructure")) { return false; }
        final Path runtime = Path.of(System.getProperty("phantom.task130.runtime")).toRealPath();
        check(runtime.equals(PhantomCoordination130Integration.runtimeRoot(module)) && runtime.equals(Path.of(".").toRealPath()), "INFRASTRUCTURE_CWD");
        check(Files.readString(runtime.resolve("owner130.txt")).matches("TASK130:[0-9a-f]{32}"), "INFRASTRUCTURE_OWNER");
        if (!AUTHORITY_SQL_INITIALIZATION.get())
        { check(!Files.exists(scene(runtime).resolve("native130.cut")) && !Files.exists(scene(runtime).resolve("restart130.cut")), "FIXTURE_BEFORE_CUT"); }
        check(Path.of(System.getProperty("phantom.test.config")).toRealPath().equals(runtime.resolve("config/Database.ini"))
            && Files.mismatch(runtime.resolve("config/Database.ini"), PhantomCoordination130Integration.localRoot(module).resolve("Database.ini")) == -1, "INFRASTRUCTURE_EXACT_SQL");
        final var sql = read(runtime.resolve("config/Database.ini"));
        check(sql.getProperty("URL").equals(PhantomCoordination130Integration.URL)
            && sql.getProperty("Login").matches("phantom130_[0-9a-f]{12}"), "INFRASTRUCTURE_SQL_SCOPE");
        return true;
    }
    static void prepareOnly(Path module) throws Exception
    {
        final Path runtime = Path.of(System.getProperty("phantom.task130.runtime")).toRealPath();
        System.setProperty("phantom.task130.infrastructure", "true");
        System.setProperty("phantom.test.config", runtime.resolve("config/Database.ini").toString());
        int exit = 1;
        try
        {
            validateInfrastructure(module);
            PhantomHeadlessPlayerTestEnvironment.initializeNativeInfrastructure(new PhantomTestContext(130, module, runtime.resolve("reports")));
            org.l2jmobius.gameserver.data.xml.InitialEquipmentData.getInstance();
            org.l2jmobius.gameserver.data.xml.InitialShortcutData.getInstance();
            org.l2jmobius.gameserver.scripting.ScriptEngine.getInstance().executeScript(org.l2jmobius.gameserver.scripting.ScriptEngine.MASTER_HANDLER_FILE);
            check(!PhantomPlayersConfig.isEnabled(), "FIXTURE_MASTER_DISABLED");
            try (var source = PhantomBackgroundSuite.ProductionAuthorityFixture.start(runtime.resolve("data/phantoms/topology"), runtime))
            { prepare(runtime, source, PhantomProfileRepository.open()); }
            exit = 0;
        }
        catch (Throwable failure)
        {
            System.out.println("NATIVE130_FIXTURE_FAIL type=" + failure.getClass().getName() + " key=" + (failure.getMessage() != null && failure.getMessage().startsWith("NATIVE130_") ? failure.getMessage() : "WITHHELD"));
            for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.")) { System.out.println("NATIVE130_FRAME " + frame); } }
        }
        finally
        {
            if (DatabaseFactory.isInitialized()) { org.l2jmobius.commons.threads.ThreadPool.shutdown(); DatabaseFactory.close(); }
            System.out.println("NATIVE130_FIXTURE_EXIT=" + exit + ";GAMESERVER_BOOT=0");
        }
        System.exit(exit);
    }

    static String attemptEvidenceKey(String attempt, long pid)
    {
        if (attempt == null || !attempt.matches("(native|restart)-[0-9]{8}-[0-9]{6}") || pid <= 0)
        { throw new IllegalArgumentException("Invalid TASK130 launch identity"); }
        return attempt + "." + pid;
    }

    private static String attemptKey()
    { return attemptEvidenceKey(System.getProperty("phantom.task130.attempt"), ProcessHandle.current().pid()); }

    static boolean hasPriorFailedAgreement(Path evidence) throws java.io.IOException
    {
        final String failurePrefix = "native130.failed.";
        try (var attempts = Files.list(evidence))
        {
            return attempts.filter(path -> Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                .map(path -> path.getFileName().toString()).filter(name -> name.matches("native130[.]failed[.](native-[0-9]{8}-[0-9]{6}[.])?[0-9]+"))
                .anyMatch(name -> Files.isRegularFile(evidence.resolve("agreement130." + name.substring(failurePrefix.length())), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        }
    }

    static void run(Path module, boolean restart) throws Exception
    {
        final Path runtime = Path.of(System.getProperty("phantom.task130.runtime")).toRealPath();
        check(Path.of(".").toRealPath().equals(runtime) && runtime.equals(PhantomCoordination130Integration.runtimeRoot(module)), "OWN_CWD");
        check(Files.readString(runtime.resolve("owner130.txt")).matches("TASK130:[0-9a-f]{32}"), "OWNER");
        final Path jar = Path.of(System.getProperty("phantom.task130.jar")).toRealPath();
        check(jar.getParent().equals(runtime.resolve("libs")) && jar.getFileName().toString().matches("GameServer\\.[0-9A-Fa-f]{64}\\.jar")
            && jar.getFileName().toString().equalsIgnoreCase("GameServer." + hash(jar) + ".jar"), "PINNED_JAR_NAME_AND_HASH");
        for (var type : List.of(GameServer.class, PhantomSystem.class, PhantomLogicalParty.class))
        { check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar), "FINAL_JAR_CLASS_SOURCE"); }
        final Path testBin = PhantomCoordination130Integration.runnerRoot(module).resolve("bin").toRealPath();
        for (var type : List.of(PhantomCoordination130Native.class, PhantomCoordination130Integration.class))
        {
            final Path origin = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            check(origin.equals(testBin), "FINAL_RUNNER_CLASS_SOURCE");
            System.out.println("NATIVE130_RUNNER_ORIGIN class=" + type.getName() + " source=" + origin + " sha256=" + hash(origin.resolve(type.getName().replace('.', '/') + ".class")));
        }
        check(Files.mismatch(runtime.resolve("config/Database.ini"), PhantomCoordination130Integration.localRoot(module).resolve("Database.ini")) == -1, "EXACT_OWN_DATABASE");
        if (Boolean.getBoolean("phantom.task130.authorityOnly"))
        {
            final Path origin = Path.of(PhantomCoordination130Native.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            check(origin.equals(testBin), "AUTHORITY_DIAGNOSTIC_TEST_BIN_ORIGIN");
            System.out.println("NATIVE130_DIAGNOSTIC_CLASS source=" + origin + " class=" + PhantomCoordination130Native.class.getName());
        }
        final String jarHash = hash(jar);
        final Path evidence = scene(runtime), fixture = evidence.resolve("fixture130.properties");
        final Path config = runtime.resolve("config/Custom/PhantomPlayers.ini");
        final String disabled = Files.readString(config);
        check(disabled.contains("EnablePhantomSystem = False"), "MASTER_DISABLED_BEFORE_BOOT");
        if (restart) { check(Files.exists(evidence.resolve("native130.complete")) && read(evidence.resolve("native130.complete")).getProperty("jar").equals(jarHash), "RESTART_SAME_FINAL_JAR"); }
        final String attempt = System.getProperty("phantom.task130.attempt", "");
        final String launch = attemptKey();
        check(attempt.startsWith(restart ? "restart-" : "native-"), "LAUNCH_PHASE");
        write(evidence.resolve((restart ? "restart130.started." : "native130.started.") + launch), "pid=" + ProcessHandle.current().pid() + "\njar=" + jarHash + "\nattempt=" + attempt + "\n");
        boolean booted = false;
        try
        {
            GameServer.main(new String[0]); booted = true;
            check(!PhantomPlayersConfig.isEnabled() && !PhantomSystem.hasConfiguredInstance(), "DISABLED_DURING_PREP");
            final var profiles = PhantomProfileRepository.open();
            if (!restart)
            {
                check(Files.exists(fixture) && Files.exists(runtime.resolve("human130-heal.tsv")), "PASSING_FIXTURE_PREFLIGHT_REQUIRED");
            }
            final var saved = read(restart ? evidence.resolve("native130.complete") : fixture);
            var ids = restart ? Arrays.stream(saved.getProperty("profiles").split(",")).map(Long::parseLong).toList() : authorizedProfiles(runtime);
            final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
            for (long id : ids) { check(World.getInstance().findObject(profiles.find(id).orElseThrow().characterObjectId()) == null, "ZERO_WORLD_BEFORE_ENABLE"); }
            if (restart) { check(Files.readString(evidence.resolve("native130.persisted")).equals(persisted(profiles, ids)), "SAME_DB_EXACT_TOTALS_AND_CLAIMS"); }
            final Path cut = evidence.resolve(restart ? "restart130.cut" : "native130.cut");
            final boolean recover = !restart && Files.exists(cut) && !Files.exists(evidence.resolve("native130.complete"));
            final var recovered = recover ? new java.util.concurrent.CompletableFuture<Void>() : null;
            if (recover)
            {
                check(hasPriorFailedAgreement(evidence), "RECOVERY_PRIOR_AGREEMENT_REQUIRED");
                final var witness = preflightWitness(profiles, ids, false);
                write(evidence.resolve("startup130." + launch + ".before.txt"), witness.canonical() + "\n" + witness.components());
            }
            check(disabled.contains("EnablePhantomSystem = False"), "MASTER_DECLARATION");
            Files.writeString(config, disabled.replace("EnablePhantomSystem = False", "EnablePhantomSystem = True"));
            PhantomPlayersConfig.load();
            final var inspectionIds = ids; final var proved = new java.util.concurrent.atomic.AtomicReference<PhantomLogicalParty>();
            final var planner = new java.util.concurrent.atomic.AtomicReference<PhantomHistoricalBackgroundPlanner>();
            final Path agreementFile = evidence.resolve("agreement130." + launch);
            final var enabled = PhantomSystem.operatorEnable(inspection ->
            {
                try
                {
                    if (Boolean.getBoolean("phantom.task130.authorityOnly"))
                    {
                        inspectLoadedAuthority(runtime, profiles, inspectionIds, inspection);
                        throw new IllegalStateException("NATIVE130_AUTHORITY_DIAGNOSTIC_COMPLETE");
                    }
                    final String witnessName = "preflight130." + launch;
                    final Path beforeWitness = evidence.resolve(witnessName + ".before.txt");
                    final Path afterWitness = evidence.resolve(witnessName + ".after.txt");
                    savePreflightWitness(beforeWitness, profiles, inspectionIds, inspection);
                    try
                    {
                        final var proof = inspectLoadedPlanner(runtime, profiles, inspectionIds, inspection, restart, saved, row ->
                        {
                            check(row.group().equals(proved.get()), "AGREEMENT_DIFFERS_FROM_PHYSICAL_PROPOSAL");
                            try { publishPreflightAgreement(evidence, cut, agreementFile, jarHash, profiles, logical, row.group()); }
                            catch (Exception failure) { throw new IllegalStateException(failure.getMessage(), failure); }
                        });
                        proved.set(proof.group());
                        if (proof.existing()) { publishPreflightAgreement(evidence, cut, agreementFile, jarHash, profiles, logical, proof.group()); }
                    }
                    catch (Exception | Error refusal)
                    {
                        try { savePreflightWitness(afterWitness, profiles, inspectionIds, inspection); }
                        catch (Exception failure) { refusal.addSuppressed(failure); }
                        throw refusal;
                    }
                    savePreflightWitness(afterWitness, profiles, inspectionIds, inspection);
                    check(!Boolean.getBoolean("phantom.task130.authorityOnly"), "AUTHORITY_DIAGNOSTIC_COMPLETE");
                    planner.set(inspection.planner());
                }
                catch (Exception failure) { throw new IllegalStateException(failure.getMessage(),failure); }
            }, recover ? new PhantomSystem.StartupPartyRecovery(Set.copyOf(inspectionIds), recovered) : null);
            System.out.println("NATIVE130_ENABLE=" + enabled);
            check(enabled.runtimeConfigured() && enabled.runtimeState() == PhantomSystem.State.RUNNING, "SYSTEM_START_" + enabled.code());
            if (recover)
            {
                try { awaitStartupRecovery(recovered, inspectionIds); }
                finally
                {
                    final var witness = preflightWitness(profiles, inspectionIds, false);
                    write(evidence.resolve("startup130." + launch + ".after.txt"), witness.canonical() + "\n" + witness.components());
                }
            }
            final var before = proved.get();
            check(before != null && Files.isRegularFile(agreementFile), "PRODUCTION_PREFLIGHT_AGREEMENT_REQUIRED");
            check(!restart || before.groupId().equals(saved.getProperty("group")), "RESTART_SAME_GROUP");
            ids = before.members().stream().map(PhantomLogicalParty.Member::profileId).toList();
            // A recovered CUT can already have lawful stock travel in its saved positions.
            final boolean travelRequired = PhantomCoordination130Integration.rendezvousLane() && !restart && !recover;
            observe(runtime, profiles, ids, before.groupId(), planner.get(), restart, travelRequired,
                System.currentTimeMillis() + (PhantomCoordination130Integration.rendezvousLane() ? 480_000 : 300_000));
            verifyExistingHumanHeal(runtime, jarHash);
            System.out.println("NATIVE130_DISABLE=" + PhantomSystem.operatorDisable());
            final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (PhantomSystem.hasConfiguredInstance() && System.nanoTime() < until) { Thread.sleep(200); }
            check(!PhantomSystem.hasConfiguredInstance(), "PHANTOM_DRAIN_BEFORE_STORE_ACCEPTANCE");
            check(ids.stream().allMatch(id -> World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId()) == null), "ZERO_WORLD_AFTER_STORE");
            final var after = logical.groupFor(ids.getFirst()).orElseThrow();
            check(after.group().groupId().equals(before.groupId()) && after.group().phase() == PhantomLogicalParty.Phase.BACKGROUND, "SAME_LOGICAL_BACKGROUND");
            if (!restart) { write(evidence.resolve("native130.persisted"), persisted(profiles, ids)); }
            write(evidence.resolve(restart ? "restart130.complete" : "native130.complete"), "jar=" + jarHash + "\ngroup=" + after.group().groupId() + "\nprofiles=" + String.join(",", ids.stream().map(String::valueOf).toList()) + "\npid=" + ProcessHandle.current().pid() + "\nattempt=" + attempt + "\n");
            Files.writeString(config, disabled);
            System.out.println("NATIVE130_PASS restart=" + restart + " sameGroup=true actualPartyKill=true STORE=true");
        }
        catch (Throwable failure)
        {
            System.out.println("NATIVE130_FAILURE type=" + failure.getClass().getName() + " key=" + (failure.getMessage() != null && failure.getMessage().startsWith("NATIVE130_") ? failure.getMessage() : "WITHHELD"));
            for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.")) { System.out.println("NATIVE130_FRAME " + frame); } }
            write(evidence.resolve((restart ? "restart130.failed." : "native130.failed.") + launch), failure.getClass().getName() + "\n");
            if (!booted)
            {
                // No configured Phantom owner exists before the master-disabled GameServer finishes booting.
                check(!PhantomSystem.hasConfiguredInstance(), "FAILED_BOOT_OWNER");
                System.exit(1);
            }
        }
        finally
        {
            if (booted)
            {
                try { Files.writeString(config, disabled); }
                finally { Shutdown.getInstance().startShutdown(null, 0, false); }
            }
        }
    }

    private static void awaitStartupRecovery(java.util.concurrent.CompletableFuture<Void> recovered, List<Long> ids) throws Exception
    {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(240);
        try
        {
            while (System.nanoTime() < deadline)
            {
                try { recovered.get(10, TimeUnit.SECONDS); return; }
                catch (TimeoutException waiting)
                {
                    System.out.println("NATIVE130_STARTUP_RECOVERY_WAIT " + ids.stream().map(id -> id + "="
                        + PhantomSystem.operatorAdmissionProfile(id).map(PhantomSystem.OperatorAdmissionProfile::readiness).orElse(null)).toList());
                }
                catch (ExecutionException failure)
                {
                    throw new IllegalStateException("NATIVE130_STARTUP_RECOVERY_" + failure.getCause().getMessage(), failure.getCause());
                }
            }
            throw new IllegalStateException("NATIVE130_STARTUP_RECOVERY_DEADLINE");
        }
        finally { if (!recovered.isDone()) { recovered.cancel(false); } }
    }

    private static long mix(long seed, long value)
    {
        long result = seed ^ (value + 0x9E3779B97F4A7C15L);
        result = (result ^ (result >>> 30)) * 0xBF58476D1CE4E5B9L;
        result = (result ^ (result >>> 27)) * 0x94D049BB133111EBL;
        return result ^ (result >>> 31);
    }
    private static long nextProfile() throws Exception
    {
        try (var connection = DatabaseFactory.getConnection(); var query = connection.createStatement(); var rows = query.executeQuery("SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='phantom_profiles'"))
        { check(rows.next(), "PROFILE_SEQUENCE"); return rows.getLong(1); }
    }
    static long birthSeed(PhantomPopulationCatalog catalog, long profile, long ordinal, int birth, ZoneId zone, Instant now)
    {
        return birthSeed(catalog, profile, ordinal, birth, zone, now, _ -> true);
    }
    private static long birthSeed(PhantomPopulationCatalog catalog, long profile, long ordinal, int birth, ZoneId zone, Instant now, java.util.function.LongPredicate fixtureWindow)
    {
        return birthSeed(catalog, profile, ordinal, birth, birth == 10 ? PhantomPopulationCatalog.CareerArchetype.HEALER
            : PhantomPopulationCatalog.CareerArchetype.DAMAGE, zone, now, fixtureWindow);
    }

    private static long birthSeed(PhantomPopulationCatalog catalog, long profile, long ordinal, int birth,
        PhantomPopulationCatalog.CareerArchetype archetype, ZoneId zone, Instant now, java.util.function.LongPredicate fixtureWindow)
    {
        if (profile <= 0 || ordinal <= 0) { throw new IllegalArgumentException("Positive production creation identity/ordinal required."); }
        for (long seed = 130; seed < 1_000_130; seed++)
        {
            if (catalog.chooseArchetype(seed, ordinal) != archetype
                || catalog.chooseClass(mix(seed, profile), archetype).classId() != birth) { continue; }
            final var schedule = PhantomPopulationStore.scheduleForShell(catalog, profile, seed, null);
            final var evaluation = catalog.evaluate(schedule.template().id(), now, zone, schedule.phaseMinutes());
            if (evaluation.state().requiresMaterialization() && evaluation.nextBoundary().isAfter(now.plusSeconds(1800)) && fixtureWindow.test(seed)) { return seed; }
        }
        throw new IllegalStateException("NATIVE130_LEGAL_BIRTH_AND_CALENDAR");
    }
    private static void prepare(Path runtime, PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomProfileRepository profiles) throws Exception
    {
        final var zone = PhantomPlayersConfig.settings().populationTimeZone();
        final var catalog = PhantomPopulationCatalog.load(ServerConfig.DATAPACK_ROOT.toPath().resolve("data/phantoms/population/high-five-population-v3.xml"), zone);
        final var population = new PhantomPopulationStore(profiles, catalog, zone);
        final Path evidence = scene(runtime), preparing = evidence.resolve("profiles130.preparing");
        final boolean firstParty = PhantomCoordination130Integration.firstPartyLane();
        final boolean rendezvous = PhantomCoordination130Integration.rendezvousLane();
        final boolean morning = !evidence.equals(runtime);
        check(!Files.exists(evidence.resolve("native130.cut")), "BOUNDED_FIXTURE_BEFORE_CUT");
        final boolean guardianFixture = rendezvous && Files.isRegularFile(evidence.resolve("fixture130.guardian"));
        if (guardianFixture) { check(Files.readString(evidence.resolve("fixture130.guardian")).strip().equals("TEST_FIXTURE"), "DECLARED_GUARDIAN_FIXTURE"); }
        final int rosterSize = !firstParty || guardianFixture ? 3 : 2;
        final List<PhantomPopulationStore.ManagedSnapshot> existing;
        if (morning)
        {
            check(Files.readString(evidence.resolve("approval.txt")).equals("User approved exactly three additional managed TEST130 profiles before their own CUT; preserve originals69,70,72.\n"), "EXPLICIT_THREE_PROFILE_APPROVAL");
            final var declared = Files.exists(preparing) ? read(preparing).getProperty("profiles", "") : "";
            existing = declared.isEmpty() ? List.of() : Arrays.stream(declared.split(",")).map(Long::parseLong).map(population::reload).toList();
            final var allowed = new HashSet<Long>(List.of(69L,70L,72L)); existing.forEach(value -> allowed.add(value.profile().profileId()));
            check(population.loadManagedAfter(0, 16).stream().allMatch(value -> allowed.contains(value.profile().profileId())) && existing.size() <= 3, "ONLY_THREE_NEW_MANAGED_PROFILES");
        }
        else { existing = population.loadManagedAfter(0, 4); check(existing.size() <= rosterSize, "ORIGINAL_FIXTURE_BOUND"); }
        final int ordinalBase = morning ? 3 : 0;
        final var levelBand = PhantomPartyFarmEligibility.forRoster(Collections.nCopies(rosterSize, 76)).orElseThrow();
        final var mobs = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
            .filter(mob -> mob.isSpawned() && !mob.isRaid() && !mob.isDead() && !mob.isAggressive() && mob.getInstanceId() == 0
                && (firstParty ? levelBand.contains(mob.getLevel()) : mob.getLevel() >= 70 && mob.getLevel() <= 78)
                && mob.getMaxHp() >= 5000 && mob.getMaxHp() <= 60000).toList();
        final var fields = new ArrayList<>(source.topology().snapshot().anchors().stream().filter(anchor -> anchor.role() == PhantomTopologyAnchorRole.FARMING)
            .map(anchor -> Map.entry(anchor, L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0))).filter(entry -> entry.getValue().isPresent())
            .filter(entry -> { final var point = entry.getValue().orElseThrow(); return GeoEngine.getInstance().hasGeo(point.x(), point.y()) && mobs.stream().anyMatch(mob -> Math.hypot((long) point.x() - mob.getX(), (long) point.y() - mob.getY()) < 500 && Math.abs((long) point.z() - mob.getZ()) < 150 && GeoEngine.getInstance().canMoveToTarget(point.x(), point.y(), point.z(), mob.getX(), mob.getY(), mob.getZ(), 0)); })
            .sorted(Comparator.comparing(entry -> entry.getKey().id())).limit(rendezvous ? 2048 : 32).toList());
        if (morning)
        {
            final var original = read(runtime.resolve("fixture130.properties"));
            final long originalLeader = Long.parseLong(original.getProperty("profiles").split(",")[0]);
            final var originalBaseline = new PhantomBackgroundStateCodec().decode(profiles.findComponent(originalLeader, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
            for (String id : List.of(originalBaseline.position().committedAnchorId(), original.getProperty("anchor")))
            {
                final var anchor = source.topology().findAnchor(id).orElseThrow();
                final var position = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor,0);
                if (position.isPresent() && GeoEngine.getInstance().hasGeo(position.get().x(),position.get().y()))
                { fields.removeIf(entry -> entry.getKey().id().equals(id)); fields.addFirst(Map.entry(anchor,position)); }
            }
        }
        check(!fields.isEmpty(), "CERTIFIED_FIELD");
        var field = fields.getFirst();
        final var fixtureTravel = firstParty ? PhantomNormalGatekeeperTravel.load(runtime.resolve("data/phantoms/travel/high-five-normal-gk.xml"), source.topology()) : null;
        if (rendezvous)
        {
            field = rendezvousFixtureStart(source, fixtureTravel, fields.stream().map(entry -> entry.getKey().id()).toList());
            System.out.println("TEST_FIXTURE_SAFE_START anchor=" + field.getKey().id() + " position=" + field.getValue().orElseThrow());
        }
        final var authority = firstParty ? new L2jPhantomBackgroundAuthority(source::knowledge, source::topology, source::progression, source::commerce,
            fixtureTravel) : source.authority();
        final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), authority);
        final var goals = new PhantomGoalStateStore(profiles);
        final var ecologyCatalog = firstParty ? PhantomPopulationEcologyCatalog.load(runtime.resolve("data/phantoms/population/high-five-ecology-v1.xml"), catalog,
            org.l2jmobius.gameserver.phantoms.social.PhantomSocialCatalog.load(runtime.resolve("data/phantoms/social/high-five-social-v1.xml"))) : null;
        final var firstHistory = firstParty ? new FirstPartyHistory(profiles, population, catalog, ecologyCatalog, planner, authority, goals, zone) : null;
        final var ids = new ArrayList<Long>(); final var members = new ArrayList<PhantomLogicalParty.Member>();
        final var seeds = new HashMap<Long, Long>();
        if (firstParty && !existing.isEmpty())
        {
            if (guardianFixture) { check(existing.size() == rosterSize, "GUARDIAN_FIXTURE_EXACT_ROSTER"); }
            ids.addAll(existing.stream().map(row -> row.profile().profileId()).toList());
            if (Files.isRegularFile(evidence.resolve("fixture130.properties")))
            {
                final var expected = read(evidence.resolve("fixture130.properties"));
                check("TEST_FIXTURE".equals(expected.getProperty("fixture")) && ids.equals(authorizedProfiles(runtime))
                    && !Files.exists(evidence.resolve("fixture-authority.ready")), "FIRST_PARTY_AUTHORITY_FIXTURE_SCOPE");
                firstHistory.reconcileAuthority(profiles, ids);
                write(evidence.resolve("fixture-authority.ready"), "topology=" + authority.hashes().topology() + "\n");
                return;
            }
            if (!guardianFixture) { recoverFirstPartyClock(evidence, profiles, existing); }
            finishFirstPartyFixture(runtime, evidence, firstHistory, profiles, ids);
            write(evidence.resolve("fixture-authority.ready"), "topology=" + authority.hashes().topology() + "\n");
            return;
        }
        PhantomDomainRef objective = null;
        for (int ordinal = 0; ordinal < rosterSize; ordinal++)
        {
            final int birth = ordinal == rosterSize - 1 ? 10 : 0;
            final boolean guardian = guardianFixture && ordinal == 1;
            final var archetype = guardian ? PhantomPopulationCatalog.CareerArchetype.TANK
                : birth == 10 ? PhantomPopulationCatalog.CareerArchetype.HEALER : PhantomPopulationCatalog.CareerArchetype.DAMAGE;
            long next; final long selected; PhantomPopulationStore.ManagedSnapshot managed;
            if (ordinal < existing.size())
            {
                managed = existing.get(ordinal); next = managed.profile().profileId(); selected = managed.state().deterministicSeed();
                check(managed.state().creationOrdinal() == ordinalBase + ordinal + 1 && managed.state().classId() == birth && managed.state().catalogHash().equals(catalog.hash()), "OWN_PARTIAL_FIXTURE_IDENTITY");
            }
            else
            {
                next = nextProfile();
                int skipped = 0;
                while (!PhantomPlayersConfig.PartySeekingSettings.defaults().seeks(next, 76)
                    || morning && ordinal == 1 && org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.choose(next, 0, false, PhantomPopulationCatalog.CareerArchetype.DAMAGE).terminalClass() != 88)
                { check(skipped++ < 64, "BOUNDED_NATIVE_CANDIDATE_SELECTION"); profiles.create(null); next = nextProfile(); }
                final long profile = next, creationOrdinal = ordinalBase + ordinal + 1;
                final Instant now = Instant.now();
                selected = birthSeed(catalog, profile, creationOrdinal, birth, archetype, zone, now,
                    seed -> firstHistory == null || firstHistory.fixtureAge(profile, creationOrdinal, seed, now));
                managed = population.createShell(1, ordinalBase + ordinal + 1, selected); check(managed.profile().profileId() == next, "EXCLUSIVE_CREATION_SEQUENCE");
                if (morning) { final var retained = new ArrayList<>(ids); retained.add(next); Files.writeString(preparing, "profiles=" + String.join(",", retained.stream().map(String::valueOf).toList()) + "\n"); }
            }
            for (int step = 0; step < 12 && managed.state().state() != PhantomPopulationState.State.READY; step++) { managed = population.advanceCreation(managed).snapshot(); }
            System.out.println("NATIVE130_CREATION profile=" + next + " state=" + managed.state().state() + " failure=" + managed.state().lastFailure());
            check(managed.state().state() == PhantomPopulationState.State.READY, "PRODUCTION_CREATION_SAGA");
            final long id = next; ids.add(id);
            seeds.put(id, selected);
            final Player actor;
            try (var suppression = org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.suppressPopulationLoad(managed.profile().characterObjectId())) { actor = Player.load(managed.profile().characterObjectId()); }
            check(actor != null && (actor.getActiveClass() == birth || actor.getPlayerClass().childOf(PlayerClass.getPlayerClass(birth))), "LAWFUL_BIRTH_CLASS");
            try
            {
                // Declared pre-CUT adult fixture follows the already persisted native career branch.
                // No career preference, quest rule, skill template or post-CUT actor is changed.
                final var choice = org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.decode(profiles.findComponent(id, org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.COMPONENT).orElseThrow().payload());
                actor.getStat().setExp(actor.getStat().getExpForLevel(76)); actor.getStat().setLevel((byte) 76);
                for (int edge = 0; actor.getActiveClass() != choice.terminalClass() && edge < 3; edge++)
                {
                    final int target = choice.nextClass(actor.getActiveClass());
                    check(org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.legalEdge(actor.getActiveClass(), target, actor.getAppearance().isFemale(), actor.getClassIndex())
                        && org.l2jmobius.gameserver.phantoms.progression.PhantomCareerChoice.minimumLevel(PlayerClass.getPlayerClass(target)) <= actor.getLevel(), "PREP_NATIVE_CAREER_EDGE");
                    actor.setPlayerClass(target); actor.setBaseClass(target); actor.setLearningClass(actor.getPlayerClass());
                }
                check(actor.getActiveClass() == choice.terminalClass(), "PREP_PERSISTED_CAREER_BRANCH");
                equipBeforeCut(actor);
                if (guardian)
                {
                    for (int item : new int[] {6373, 6374, 6375, 6376, 6377, 6378})
                    { actor.getInventory().equipItem(actor.addItem(ItemProcessType.REWARD, item, 1, actor, false)); }
                    actor.setCurrentHp(actor.getMaxHp());
                    System.out.println("TEST_FIXTURE_GUARDIAN profile=" + id + " class=" + actor.getActiveClass() + " equipment=imperial-crusader-stock-set");
                }
                System.out.println("NATIVE130_ACTUAL_COMBAT profile=" + id + " facts=" + source.authority().planningCombat(actor) + " expertise=" + actor.getKnownSkill(239));
                PhantomHistoricalBackgroundPlanner.Result plan = null;
                for (var candidate : !firstParty && objective == null && (!morning || ordinal > 0) ? fields : List.of(field))
                {
                    final var position = candidate.getValue().orElseThrow(); actor.setXYZInvisible(position.x(), position.y(), position.z());
                    plan = firstParty || birth == 10 || morning && ordinal == 0 ? planner.idleInitial(id, actor, selected, 1) : planner.planInitial(id, actor, selected, 1);
                    // Existing HistoricalBackgroundService/TEST094 fallback: a guardian/support
                    // may wait at a canonical anchor without claiming solo farm viability.
                    if (ordinal > 0 && objective != null && !plan.ready() && "planner.target_or_route.absent".equals(plan.reasonKey()))
                    { plan = planner.idleInitial(id, actor, selected, 1); }
                    System.out.println("NATIVE130_PLAN profile=" + id + " anchor=" + candidate.getKey().id() + " ready=" + plan.ready() + " reason=" + plan.reasonKey() + " rejected=" + plan.candidateRejections().stream().limit(3).toList());
                    if (plan.ready()) { if (!plan.goal().validSources().isEmpty()) { field = candidate; } break; }
                }
                check(plan.ready(), "ORDINARY_PREP_PLAN");
                if (!morning)
                {
                    if (goals.load(id).isEmpty()) { goals.insert(id, plan.goal()); }
                    else { check(goals.load(id).orElseThrow().goal().equals(plan.goal()), "PARTIAL_FIXTURE_ROOT_EXACT"); }
                }
                if (objective == null && !plan.goal().validSources().isEmpty()) { objective = plan.goal().validSources().getFirst(); }
                members.add(new PhantomLogicalParty.Member(id, ordinal == 2 ? "support" : plan.goal().goalType().equals(PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE) ? "guardian" : "damage", plan.goal().goalId(), plan.goal().revision()));
                actor.setCurrentHp(actor.getMaxHp() * (firstParty || morning || birth == 10 ? 1 : 0.4));
                actor.getInventory().updateDatabase(); actor.storeMe();
                if (firstParty)
                {
                    final var captured = authority.captureOwnedNative(id, actor, plan.goal(), null);
                    check(new PhantomBackgroundTransaction().captureBaseline(captured.state(), plan.goal(), captured.context()).status()
                        == PhantomBackgroundTransaction.Status.SUCCESS, "FIRST_PARTY_BASELINE_CAPTURE");
                    System.out.println("TEST_FIXTURE profile=" + id + " initialLevel=" + actor.getLevel() + " initialExp=" + actor.getExp()
                        + " initialSp=" + actor.getSp() + " initialHp=" + actor.getCurrentHp() + " initialMp=" + actor.getCurrentMp()
                        + " equipment=DECLARED_PRE_CUT role=" + (guardian ? "guardian" : birth == 10 ? "healer" : "damage"));
                }
                System.out.println("NATIVE130_PREFLIGHT profile=" + id + " actor=" + actor.getObjectId() + " class=" + actor.getActiveClass() + " level=" + actor.getLevel() + " hp=" + actor.getCurrentHp() + " mp=" + actor.getCurrentMp() + " heal=" + actor.getKnownSkill(1011) + " root=" + plan.goal() + " pos=" + actor.getLocation());
            }
            finally { actor.stopAllTasks(); actor.storeMe(); actor.setOnlineStatus(true, false); actor.deleteMe(); }
        }
        if (firstParty)
        {
            finishFirstPartyFixture(runtime, evidence, firstHistory, profiles, ids);
            write(evidence.resolve("fixture-authority.ready"), "topology=" + authority.hashes().topology() + "\n");
            return;
        }
        check(objective != null && objective.namespace().equals("background.farm"), "REAL_FARM_SOURCE");
        if (morning)
        {
            final var common = source.topology().findAnchor(objective.key().substring(objective.key().indexOf('@') + 1)).orElseThrow();
            final var point = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(common, 0).orElseThrow();
            final int npc = Integer.parseInt(objective.key().substring(0, objective.key().indexOf('@')));
            check(World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
                .anyMatch(mob -> mob.getId() == npc && mob.isSpawned() && !mob.isDead() && !mob.isRaid() && mob.getInstanceId() == 0
                    && Math.hypot((long) mob.getX() - point.x(), (long) mob.getY() - point.y()) <= 1200), "ACTUAL_SHARED_TARGET");
            members.clear(); long leader = 0;
            for (long id : ids)
            {
                final Player actor;
                try (var suppression = org.l2jmobius.gameserver.taskmanagers.PlayerAutoSaveTaskManager.suppressPopulationLoad(profiles.find(id).orElseThrow().characterObjectId())) { actor = Player.load(profiles.find(id).orElseThrow().characterObjectId()); }
                try
                {
                    actor.stopAllTasks(); actor.setXYZInvisible(point.x(),point.y(),point.z());
                    final var idle = planner.idleInitial(id, actor, seeds.get(id), 1); check(idle.ready(), "COMMON_CANONICAL_IDLE");
                    PhantomGoal root = idle.goal();
                    if (actor.getActiveClass() == 88)
                    {
                        final var projection = source.authority().captureOwnedNative(id, actor, root, null).state();
                        final var farm = planner.replanVisibleRendezvous(id, projection, root, seeds.get(id), 1,
                            new org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyPoint(point.x(),point.y(),point.z(),0), objective, Set.of(), _ -> true);
                        check(farm.ready(), "COMMON_NATIVE_DD_READINESS"); root = farm.goal(); leader = id;
                    }
                    if (goals.load(id).isEmpty()) { goals.insert(id, root); } else { check(goals.load(id).orElseThrow().goal().equals(root), "FINAL_ROOT_EXACT"); }
                    actor.storeMe();
                    final var captured = source.authority().captureOwnedNative(id, actor, root, null);
                    final var baseline = new PhantomBackgroundTransaction().captureBaseline(captured.state(), root, captured.context());
                    check(baseline.status() == PhantomBackgroundTransaction.Status.SUCCESS, "CANONICAL_PRE_CUT_BASELINE");
                    members.add(new PhantomLogicalParty.Member(id, PhantomHistoricalBackgroundPlanner.logicalRole(baseline.state()), root.goalId(), root.revision()));
                    System.out.println("NATIVE130_PRE_CUT_BASELINE profile=" + id + " class=" + actor.getActiveClass() + " state=" + baseline.state().state() + " anchor=" + point + " actualSqlAndNativeFacts=true rewards=0");
                }
                finally { actor.stopAllTasks(); actor.storeMe(); actor.setOnlineStatus(true, false); actor.deleteMe(); }
            }
            check(leader > 0, "COMMON_NATIVE_LEADER");
            final long selectedLeader = leader;
            ids.remove(selectedLeader); ids.addFirst(selectedLeader);
            members.sort(Comparator.comparingInt(member -> member.profileId() == selectedLeader ? 0 : 1));
        }
        final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
        final var group = new PhantomLogicalParty(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("NATIVE130:" + ids), 1, ids.getFirst(), members, objective, objective.key().substring(objective.key().indexOf('@') + 1), PhantomLogicalParty.Phase.AGREED, 0, System.currentTimeMillis(), "");
        check(logical.agree(group).isPresent(), "ZERO_PLAYER_AGREEMENT");
        if (!Files.exists(runtime.resolve("human130-heal.tsv"))) { ordinaryHeal(runtime); }
        write(evidence.resolve("fixture130.properties"), "profiles=" + String.join(",", ids.stream().map(String::valueOf).toList()) + "\ngroup=" + group.groupId() + "\nanchor=" + group.anchorId() + "\n");
        System.out.println("NATIVE130_ZERO_WORLD_AGREEMENT group=" + group.groupId() + " profiles=" + ids);
    }

    private static Map.Entry<org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor, Optional<PhantomBackgroundState.Position>>
        rendezvousFixtureStart(PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomNormalGatekeeperTravel travel, List<String> fields)
    {
        final var zones = org.l2jmobius.gameserver.managers.ZoneManager.getInstance();
        Map.Entry<org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor, Optional<PhantomBackgroundState.Position>> best = null;
        long bestCost = Long.MAX_VALUE;
        for (var anchor : source.topology().snapshot().anchors())
        {
            if (anchor.role() != PhantomTopologyAnchorRole.CITY_CENTER && anchor.role() != PhantomTopologyAnchorRole.RESPAWN
                && anchor.role() != PhantomTopologyAnchorRole.GATEKEEPER && anchor.role() != PhantomTopologyAnchorRole.ROUTE) { continue; }
            final var position = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0);
            final var point = position.orElse(null);
            if (point == null || !GeoEngine.getInstance().hasGeo(point.x(), point.y())
                || zones.getZone(point.x(), point.y(), point.z(), org.l2jmobius.gameserver.model.zone.type.PeaceZone.class) == null
                && zones.getZone(point.x(), point.y(), point.z(), org.l2jmobius.gameserver.model.zone.type.TownZone.class) == null
                || World.getInstance().getVisibleObjects().stream().anyMatch(value -> value instanceof Monster mob && mob.isAggressive() && !mob.isAlikeDead()
                    && Math.abs((long) mob.getZ() - point.z()) <= 256
                    && Math.hypot((long) mob.getX() - point.x(), (long) mob.getY() - point.y()) <= mob.getAggroRange() + 300)) { continue; }
            for (String field : fields)
            {
                final var route = travel.route(anchor.id(), field).orElse(null);
                if (route == null || route.isEmpty() || route.size() > 32) { continue; }
                final long cost = route.stream().mapToLong(PhantomNormalGatekeeperTravel.Step::travelMillis).sum();
                if (cost < bestCost) { best = Map.entry(anchor, position); bestCost = cost; }
            }
        }
        check(best != null, "SAFE_CANONICAL_TOWN_WITH_FARM_ITINERARY");
        return best;
    }

    private static void finishFirstPartyFixture(Path runtime, Path evidence, FirstPartyHistory history, PhantomProfileRepository profiles, List<Long> ids) throws Exception
    {
        history.complete(profiles, ids);
        ordinaryHeal(runtime);
        write(evidence.resolve("fixture130.properties"), "profiles=" + String.join(",", ids.stream().map(String::valueOf).toList()) + "\nfixture=TEST_FIXTURE\n");
        System.out.println("TEST_FIXTURE_READY profiles=" + ids + ";DURABLE_AGREEMENT=PRODUCTION_OWNER_AT_BOOT;POST_CUT_REWARDS=0");
    }

    /** Correct the unconsumed calendar assignments from the earlier local-time provisioning attempt. */
    private static void recoverFirstPartyClock(Path evidence, PhantomProfileRepository profiles, List<PhantomPopulationStore.ManagedSnapshot> existing) throws Exception
    {
        check(existing.size() == 2 && !Files.exists(evidence.resolve("fixture130.properties"))
            && !Files.exists(evidence.resolve("native130.cut")), "FIRST_PARTY_CLOCK_BEFORE_CUT");
        final var ids = existing.stream().map(row -> row.profile().profileId()).toList();
        final var before = preflightWitness(profiles, ids, false);
        final var removals = new ArrayList<org.l2jmobius.gameserver.phantoms.profile.PhantomProfileComponent>();
        final var absent = new ArrayList<PhantomProfileRepository.ComponentAbsence>();
        for (var entry : existing)
        {
            final long id = entry.profile().profileId();
            final var raw = profiles.findComponent(id, PhantomPopulationEcologyState.COMPONENT_TYPE).orElseThrow();
            final var state = new PhantomPopulationEcologyStateCodec().decode(raw.payload());
            final var born = entry.profile().createdAt();
            final long offset = ZoneId.systemDefault().getRules().getOffset(born).getTotalSeconds() / 60;
            check(entry.state().state() == PhantomPopulationState.State.READY && offset > 0 && raw.rowVersion() == 0 && !state.requestPending()
                && state.calendarCursorEpochMinute() == state.virtualJoinEpochMinute() && state.assignedAtEpochMinute() == born.getEpochSecond() / 60 + offset
                && state.assignedAtEpochMinute() > Instant.now().getEpochSecond() / 60, "FIRST_PARTY_CLOCK_PROOF_" + id);
            removals.add(raw);
            for (String type : List.of(PhantomBackgroundCatchupState.COMPONENT_TYPE, PhantomLogicalParty.CLAIM_COMPONENT_TYPE,
                PhantomLogicalParty.COMPONENT_TYPE, PhantomOwnedStoreIntent.COMPONENT_TYPE))
            { absent.add(new PhantomProfileRepository.ComponentAbsence(id, type)); }
        }
        write(evidence.resolve("fixture-clock-before.txt"), before.canonical() + "\n" + before.components());
        profiles.mutateProfileComponentsAtomically(List.of(), existing.stream().map(PhantomPopulationStore.ManagedSnapshot::component).toList(), absent, removals);
        final var after = preflightWitness(profiles, ids, false);
        write(evidence.resolve("fixture-clock-after.txt"), after.canonical() + "\n" + after.components());
        check(before.canonical().equals(after.canonical()) && before.components().lines().filter(line -> !line.startsWith("population.ecology:")).toList()
            .equals(after.components().lines().toList()), "FIRST_PARTY_CLOCK_FIXTURE_CONSERVATION");
        System.out.println("TEST_FIXTURE_CLOCK_REPAIRED profiles=" + ids + ";UNCONSUMED_CALENDARS_ONLY=true");
    }

    /** Pre-CUT preparation uses the real SQL stores and historical owners, without starting native Party or Combat. */
    private static final class FirstPartyHistory
    {
        private final PhantomPopulationStore _population;
        private final PhantomPopulationCatalog _catalog;
        private final PhantomPopulationEcologyCatalog _ecologyCatalog;
        private final PhantomBackgroundService _background;
        private final PhantomHistoricalBackgroundService _history;
        private final PhantomMaterializationService _materialization;
        private final PhantomPopulationEcologyService _ecology;
        private static final PhantomPopulationEcologyState.Preset PRESET = PhantomPopulationEcologyState.Preset.LIVING;

        FirstPartyHistory(PhantomProfileRepository profiles, PhantomPopulationStore population, PhantomPopulationCatalog catalog,
            PhantomPopulationEcologyCatalog ecologyCatalog, PhantomHistoricalBackgroundPlanner planner, L2jPhantomBackgroundAuthority authority,
            PhantomGoalStateStore goals, ZoneId zone)
        {
            _population = population;
            _catalog = catalog;
            _ecologyCatalog = ecologyCatalog;
            final var metrics = new PhantomMetrics();
            final var lifecycle = new PhantomMaterializationLifecycleBridge();
            final var leases = PhantomIdentityLeaseRegistry.getInstance();
            _materialization = new PhantomMaterializationService(profiles, leases, metrics, new PhantomDiagnosticTrace(false, 64, 16, metrics),
                2, _ -> { }, lifecycle, 5_000, 10_000);
            final var signals = new PhantomRelevanceSignalPort()
            {
                @Override public SignalDelivery submit(long id, org.l2jmobius.gameserver.phantoms.activity.PhantomRelevanceSignal signal) { return SignalDelivery.ACCEPTED; }
                @Override public SignalDelivery withdraw(long id, String key, long sequence) { return SignalDelivery.ACCEPTED; }
            };
            _background = new PhantomBackgroundService(profiles, goals, leases, new PhantomBackgroundTransaction(), authority,
                new PhantomBackgroundCompetitionRegistry(), signals, () -> _materialization);
            _history = new PhantomHistoricalBackgroundService(profiles, goals, planner, _background, _materialization);
            lifecycle.install(PhantomMaterializationLifecyclePort.chain(_history, _background));
            _ecology = new PhantomPopulationEcologyService(ecologyCatalog, catalog, new PhantomPopulationEcologyStore(profiles), _history,
                id -> _materialization.find(id).isPresent(), id -> profiles.findComponent(id, PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent() ? "fixture.pending_store" : "",
                Clock.systemUTC(), zone, PRESET, 0, 10);
            _ecology.enablePeriodicDueMode();
            _ecology.installRuntime(id -> Optional.of(population.reload(id)), new PhantomPopulationEcologyService.PopulationEvents()
            {
                @Override public void requestArchive(long id) { throw new IllegalStateException("A new TEST FIXTURE requested archive."); }
                @Override public void reconcilePopulation() {}
                @Override public void ecologyFenceChanged(long id) {}
            });
        }

        boolean fixtureAge(long profile, long ordinal, long seed, Instant now)
        {
            final long minute = now.getEpochSecond() / 60;
            final var schedule = PhantomPopulationStore.scheduleForShell(_catalog, profile, seed, null);
            final var assignment = _ecologyCatalog.assign(PRESET, 1, ordinal, seed, minute, 0, 0, schedule.template().id());
            return minute - assignment.virtualJoinEpochMinute() <= 45
                && _ecology.nextProductiveWindow(assignment, schedule.phaseMinutes(), minute).productive();
        }

        void complete(PhantomProfileRepository profiles, List<Long> ids) throws Exception
        {
            check(_background.start() && _materialization.start(), "FIRST_PARTY_HISTORY_OWNERS");
            try
            {
                for (long id : ids)
                {
                    if (_history.status(id).filter(row -> row.state().status() == PhantomBackgroundCatchupState.Status.COMPLETE).isPresent())
                    { preparePreflightAuthority(profiles, id, () -> _history.prepareCurrentAuthority(id)); }
                }
                ids.forEach(id -> _ecology.register(_population.reload(id)));
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
                while (System.nanoTime() < deadline)
                {
                    _ecology.onPopulationPulse();
                    if (ids.stream().allMatch(id -> _ecology.dueSnapshot(id).complete()
                        && _history.status(id).map(row -> row.state().status() == PhantomBackgroundCatchupState.Status.COMPLETE).orElse(false))) { break; }
                    Thread.sleep(50);
                }
                for (long id : ids)
                {
                    final var due = _ecology.dueSnapshot(id);
                    final var history = _history.status(id).orElse(null);
                    final var background = _background.acquisitionSnapshot(id).orElseThrow();
                    final var role = _history.potentialPartyRole(id, background);
                    System.out.println("TEST_FIXTURE_HISTORY profile=" + id + " due=" + due + " history=" + history + " state="
                        + background.state() + " progress=" + background.progress() + " vitals=" + background.vitals() + " role=" + role);
                    check(due.complete() && history != null && history.state().status() == PhantomBackgroundCatchupState.Status.COMPLETE
                        && background.state() == PhantomBackgroundState.State.READY && _history.permitsNormalOperation(id), "FIRST_PARTY_HISTORY_" + id + "_" + due.reason());
                    check(id == ids.getFirst() ? role.startsWith("damage.") : id == ids.getLast() ? role.equals("support.healer")
                        : role.equals("frontline.guardian"), "FIRST_PARTY_LEARNED_ROLE_" + id);
                }
            }
            finally
            {
                _ecology.beginStop();
                final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!_ecology.finishStop() && System.nanoTime() < deadline) { Thread.sleep(25); }
                check(_ecology.finishStop(), "FIRST_PARTY_ECOLOGY_DRAIN");
                stopOwners(deadline);
            }
        }

        void reconcileAuthority(PhantomProfileRepository profiles, List<Long> ids) throws Exception
        {
            check(_background.start() && _materialization.start(), "FIRST_PARTY_AUTHORITY_OWNERS");
            try
            {
                for (long id : ids)
                {
                    preparePreflightAuthority(profiles, id, () -> _history.prepareCurrentAuthority(id));
                    final var state = _background.acquisitionSnapshot(id).orElseThrow();
                    System.out.println("TEST_FIXTURE_AUTHORITY profile=" + id + " hashes=" + state.hashes() + " progress=" + state.progress());
                }
            }
            finally { stopOwners(System.nanoTime() + TimeUnit.SECONDS.toNanos(15)); }
        }

        private void stopOwners(long deadline)
        {
            check(_materialization.requestShutdown(deadline).state() == PhantomMaterializationService.ServiceState.STOPPED, "FIRST_PARTY_MATERIALIZATION_DRAIN");
            _background.beginStop();
            check(_background.finishStop(), "FIRST_PARTY_BACKGROUND_DRAIN");
        }
    }

    private static void equipBeforeCut(Player actor)
    {
        actor.stopAllTasks();
        actor.getStat().setExp(actor.getStat().getExpForLevel(76)); actor.getStat().setLevel((byte) 76);
        // Same bounded fixed point as L2jProgressionBackend for an overlevel prepared actor.
        int passes = 0;
        while (!org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getAvailableAutoGetSkills(actor).isEmpty() && passes++ < 64) { actor.giveAvailableAutoGetSkills(); }
        check(org.l2jmobius.gameserver.data.xml.SkillTreeData.getInstance().getAvailableAutoGetSkills(actor).isEmpty(), "AUTOGET_FIXED_POINT");
        long priorSkills = -1;
        for (int pass = 0; pass < 64; pass++)
        {
            actor.giveAvailableSkills(false, true, false); actor.rewardSkills();
            final long current = actor.getAllSkills().stream().mapToLong(skill -> skill.getLevel()).sum();
            if (current == priorSkills) { break; } priorSkills = current;
        }
        final var profession = actor.getPlayerClass();
        final int weapon = profession.isMage() ? 6366 : profession.childOf(PlayerClass.GLADIATOR) ? 6580 : profession.childOf(PlayerClass.WARLORD) ? 6370 : profession.childOf(PlayerClass.HAWKEYE) ? 7575 : profession.childOf(PlayerClass.TREASURE_HUNTER) ? 6367 : 6364;
        for (int item : new int[] {weapon, 2381, 2412})
        { final var existing = actor.getInventory().getItemByItemId(item); actor.getInventory().equipItem(existing == null ? actor.addItem(ItemProcessType.REWARD, item, 1, actor, false) : existing); }
        final long shots = actor.getInventory().getInventoryItemCount(1467, -1);
        if (shots < 2000) { actor.addItem(ItemProcessType.REWARD, 1467, 2000 - shots, actor, false); }
        if (weapon == 7575 && actor.getInventory().getInventoryItemCount(1345, -1) < 2000) { actor.addItem(ItemProcessType.REWARD, 1345, 2000 - actor.getInventory().getInventoryItemCount(1345, -1), actor, false); }
        if (actor.getAdena() < 50000) { actor.addAdena(ItemProcessType.REWARD, 50000 - actor.getAdena(), actor, false); }
        actor.refreshExpertisePenalty(); actor.refreshOverloaded(); actor.setRunning(); actor.standUp();
        actor.setCurrentHp(actor.getMaxHp()); actor.setCurrentMp(actor.getMaxMp()); actor.setCurrentCp(actor.getMaxCp());
    }

    private static void ordinaryHeal(Path runtime) throws Exception
    {
        try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("INSERT INTO accounts(login,password,accessLevel,lastServer) VALUES('task130human','DISABLED_NATIVE_TEST_ONLY',-1,1)"))
        { check(query.executeUpdate() == 1, "FRESH_HUMAN_ACCOUNT"); }
        final var actor = Player.create(PlayerTemplateData.getInstance().getTemplate(PlayerClass.getPlayerClass(10)), "task130human", "HumanHeal130", new PlayerAppearance((byte) 0, (byte) 0, (byte) 0, false));
        check(actor != null, "HUMAN_CREATE");
        final int objectId = actor.getObjectId();
        try
        {
            org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.initialize(actor, org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.Mode.CLIENT, org.l2jmobius.gameserver.model.actor.PlayerCreationInitializer.resolveCreationLocation(PlayerTemplateData.getInstance().getTemplate(PlayerClass.getPlayerClass(10))));
            equipBeforeCut(actor);
            actor.setCurrentHp(actor.getMaxHp() * 0.5);
            actor.getInventory().updateDatabase(); actor.storeMe();
        }
        finally { actor.stopAllTasks(); actor.storeMe(); actor.setOnlineStatus(true, false); actor.deleteMe(); }
        try (var human = new LocalPlaySyntheticHumanSession(objectId, "HumanHeal130"))
        {
            final var player = human.start();
            final var skill = player.getKnownSkill(1011);
            check(skill != null && player.getNativeWorkOwner() == null && !player.hasHeadlessOutboundSession(), "ORDINARY_HUMAN_NOT_PHANTOM");
            final double hp = player.getCurrentHp(), mp = player.getCurrentMp();
            final var uuid = UUID.randomUUID().toString();
            final var outcome = human.execute(new LocalPlayPilotProtocol.Request(uuid, uuid, uuid, 1, Instant.now().plusSeconds(10), LocalPlayPilotProtocol.Operation.CAST_LEARNED_SKILL, Map.of("skillId", "1011", "targetObjectId", Integer.toString(objectId))));
            check(outcome.status().equals("ACCEPTED"), "HUMAN_NATIVE_USE_MAGIC");
            double minimumMp = mp; boolean healed = false; final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline)
            {
                minimumMp = Math.min(minimumMp, player.getCurrentMp());
                healed |= player.getCurrentHp() > hp + 30 && player.getLastSkillCast() == skill;
                if (healed && minimumMp < mp && !player.isCastingNow()) { break; }
                Thread.sleep(25);
            }
            check(healed && minimumMp < mp, "ORDINARY_HUMAN_HP_MP");
            write(runtime.resolve("human130-heal.tsv"), "actor\tskill\trank\tbeforeHp\tafterHp\tbeforeMp\tminimumMp\treuse\n" + objectId + "\t1011\t" + skill.getLevel() + "\t" + hp + "\t" + player.getCurrentHp() + "\t" + mp + "\t" + minimumMp + "\t" + player.getSkillRemainingReuseTime(skill.getReuseHashCode()) + "\n");
            System.out.println("NATIVE130_F8_HUMAN_PASS actualUseMagic=true stockHeal=true hpGain=" + (player.getCurrentHp() - hp) + " mpDebit=" + (mp - minimumMp));
        }
    }
    private record TargetSeen(Monster mob, Party party, List<Player> roster, int leader, PhantomLogicalParty agreement, double initialHp,
        Map<Integer, PlayerNativeEvidence.Snapshot> before, Map<Integer, Long> initialDamage, Set<Integer> observedDamage) { }

    record MemberFight(PlayerNativeEvidence.Snapshot before, PlayerNativeEvidence.Snapshot after,
        PlayerNativeEvidence.TargetReceipts receipts, boolean observedDamage) { }

    record EarnedReward(int objectId, long exp, long sp, boolean damagedTarget) { }

    static List<EarnedReward> exactGroupRewards(PlayerNativeEvidence.Target target, List<MemberFight> members)
    {
        final var rewards = new ArrayList<EarnedReward>();
        for (var member : members)
        {
            final var before = member.before();
            final var after = member.after();
            final var receipt = member.receipts();
            if (before.overflow() || after.overflow() || before.objectId() != after.objectId() || before.epoch() != after.epoch()
                || after.rewardSequence() - before.rewardSequence() != 1 || receipt.rewardSequence() != after.rewardSequence()
                || receipt.settledRewardSequence() != after.rewardSequence() || !target.equals(receipt.rewardTarget()) || !target.equals(receipt.settledTarget())) { continue; }
            final long exp = after.expGained() - before.expGained();
            final long sp = after.spGained() - before.spGained();
            if (exp < 0 || sp < 0 || exp == 0 && sp == 0) { continue; }
            final boolean damaged = member.observedDamage() && after.killSequence() > before.killSequence()
                && receipt.killSequence() == after.killSequence() && target.equals(receipt.killTarget());
            rewards.add(new EarnedReward(after.objectId(), exp, sp, damaged));
        }
        return rewards.size() >= 2 && rewards.stream().anyMatch(EarnedReward::damagedTarget) ? List.copyOf(rewards) : List.of();
    }

    private static Map<Integer, Long> stockDamage(Monster mob)
    {
        final var damage = new HashMap<Integer, Long>();
        for (var entry : mob.getAggroList().values())
        {
            final var attacker = entry.getAttacker();
            final var actor = attacker == null ? null : attacker.asPlayer();
            if (actor != null && entry.getDamage() > 0) { damage.merge(actor.getObjectId(), entry.getDamage(), Long::sum); }
        }
        return damage;
    }
    private static int existingHuman(Path runtime) throws Exception
    { return Integer.parseInt(Files.readAllLines(runtime.resolve("human130-heal.tsv")).get(1).split("\t")[0]); }
    private static void verifyExistingHumanHeal(Path runtime, String jarHash) throws Exception
    {
        final Path receipt = runtime.resolve("human130-final-" + jarHash + ".tsv");
        if (Files.exists(receipt)) { return; }
        final int objectId = existingHuman(runtime);
        try (var session = new LocalPlaySyntheticHumanSession(objectId, "HumanHeal130"))
        {
            final var player = session.start(); final var skill = player.getKnownSkill(1011);
            check(skill != null && player.getNativeWorkOwner() == null && !player.hasHeadlessOutboundSession(), "FINAL_HUMAN_IDENTITY");
            final double hp = player.getCurrentHp(), mp = player.getCurrentMp(); double minimum = mp;
            final var uuid = UUID.randomUUID().toString();
            check(session.execute(new LocalPlayPilotProtocol.Request(uuid, uuid, uuid, 1, Instant.now().plusSeconds(10), LocalPlayPilotProtocol.Operation.CAST_LEARNED_SKILL,
                Map.of("skillId", "1011", "targetObjectId", Integer.toString(objectId)))).status().equals("ACCEPTED"), "FINAL_HUMAN_CAST");
            final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < until)
            {
                minimum = Math.min(minimum, player.getCurrentMp());
                if (player.getCurrentHp() > hp + 30 && minimum < mp && player.getLastSkillCast() == skill && !player.isCastingNow()) { break; }
                Thread.sleep(25);
            }
            check(player.getCurrentHp() > hp + 30 && minimum < mp, "FINAL_HUMAN_HEAL_HP_MP");
            write(receipt, "jar=" + jarHash + "\nactor=" + objectId + "\nskill=1011:" + skill.getLevel() + "\nhpGain=" + (player.getCurrentHp() - hp) + "\nmpDebit=" + (mp - minimum) + "\n");
            System.out.println("NATIVE130_F8_FINAL_HUMAN_PASS jar=" + jarHash + " hpGain=" + (player.getCurrentHp() - hp) + " mpDebit=" + (mp - minimum));
        }
    }
    static boolean usefulGroupEvidence(boolean party, boolean kill, boolean healerRequired, boolean healHp, boolean healMp, boolean restart)
    { return party && kill && (!healerRequired || restart || healHp && healMp); }

    static boolean stockRosterReady(int nativeSize, int logicalSize, boolean exact)
    { return nativeSize >= 2 && nativeSize <= 9 && (!exact || nativeSize == logicalSize); }

    private record HealRecipient(Player player, long receiptBefore, double hpBefore) { }
    private record HealSample(org.l2jmobius.gameserver.model.skill.Skill skill, List<HealRecipient> recipients, double mpBefore) { }

    private static org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.RestoredHpReceipt restoredHp(Player recipient)
    {
        final var owner = recipient.getNativeWorkOwner();
        return owner == null || !owner.isCurrent() || owner.evidence() == null ? null : owner.evidence().restoredHpReceipt();
    }

    private static void observe(Path runtime, PhantomProfileRepository profiles, List<Long> ids, String groupId,
        PhantomHistoricalBackgroundPlanner planner, boolean restart, boolean travelRequired, long deadline) throws Exception
    {
        final var trace = scene(runtime).resolve((restart ? "restart130.trace." : "native130.trace.") + attemptKey() + ".tsv");
        write(trace, "millis\tgroup\tcensus\tactors\n");
        final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
        final var objects = ids.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(id -> id, id -> profiles.find(id).orElseThrow().characterObjectId()));
        final Map<PlayerNativeEvidence.Target, TargetSeen> targets = new LinkedHashMap<>();
        final Map<Integer, Double> priorHp = new HashMap<>(), priorMp = new HashMap<>();
        final Map<Integer, Long> priorHealReceipt = new HashMap<>();
        final Map<Integer, HealSample> healing = new HashMap<>();
        final Map<Integer, PhantomNavigationPoint> partyOrigins = new HashMap<>();
        final boolean exactRosterRequired = travelRequired || Files.isRegularFile(runtime.resolve("fixture130.guardian"));
        boolean groupTravel = false;
        boolean partySeen = false, kill = false, heal = false, healMpDebit = false, healerRequired = false;
        long partyGeneration = -1;
        TargetSeen earnedTarget = null;
        boolean loot = false;
        int stableBlockSamples = 0, idleAssemblySamples = 0, stableCoordinationSamples = 0;
        String lastCoordinationWait = ""; long lastCoordinationVersion = -1;
        long nextTrace = 0; final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline)
        {
            final var logicalRow = logical.groupFor(ids.getFirst()).orElseThrow(() -> new IllegalStateException("NATIVE130_LOGICAL_AGREEMENT_RETIRED"));
            final var group = logicalRow.group();
            healerRequired |= group.members().stream().anyMatch(member -> member.role().equals("support.healer"));
            check(group.groupId().equals(groupId), "GROUP_ID_PRESERVED");
            final var logicalObjects = group.members().stream().map(member -> objects.get(member.profileId())).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            final var actors = ids.stream().map(id -> World.getInstance().getPlayer(objects.get(id))).filter(Objects::nonNull).toList();
            final var party = actors.stream().map(Player::getParty).filter(Objects::nonNull)
                .filter(value -> value.getMembers().stream().allMatch(actor -> logicalObjects.contains(actor.getObjectId()))).findFirst().orElse(null);
            final var stock = party == null ? List.<Player>of() : List.copyOf(party.getMembers());
            if (stockRosterReady(stock.size(), logicalObjects.size(), exactRosterRequired)
                && stock.stream().allMatch(actor -> actors.contains(actor) && logicalObjects.contains(actor.getObjectId()) && actor.getParty() == party))
            {
                final var leader = stock.getFirst();
                partyGeneration = group.generation();
                targets.entrySet().removeIf(entry -> !entry.getKey().equals(entry.getValue().mob().getNativeEvidenceTarget())
                    || entry.getValue().party() != party || !entry.getValue().roster().equals(stock) || entry.getValue().leader() != leader.getObjectId()
                    || entry.getValue().agreement().generation() != group.generation() || !entry.getValue().agreement().objective().equals(group.objective()));
                if (!partySeen)
                {
                    System.out.println("NATIVE130_STOCK_PARTY leader=" + leader.getObjectId() + " members=" + stock.stream().map(Player::getObjectId).toList() + " logicalRoles=" + group.members());
                    if (travelRequired)
                    {
                        check(stock.stream().allMatch(actor -> !actor.isAlikeDead()
                            && (actor.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.TOWN)
                                || actor.isInsideZone(org.l2jmobius.gameserver.model.zone.ZoneId.PEACE))), "STOCK_PARTY_BEFORE_TOWN_DEPARTURE");
                        stock.forEach(actor -> partyOrigins.put(actor.getObjectId(), PhantomNativeTravelActions.point(actor)));
                        System.out.println("NATIVE130_STOCK_RENDEZVOUS positions=" + partyOrigins);
                    }
                    partySeen = true;
                }
                if (travelRequired && !groupTravel && stock.size() == partyOrigins.size()
                    && stock.stream().allMatch(actor -> partyOrigins.containsKey(actor.getObjectId())
                        && PhantomNativeTravelActions.point(actor).distanceTo(partyOrigins.get(actor.getObjectId())) >= 300))
                {
                    groupTravel = true;
                    System.out.println("NATIVE130_GROUP_TRAVEL leader=" + leader.getObjectId() + " source=" + group.objective().key()
                        + " positions=" + stock.stream().map(PhantomNativeTravelActions::point).toList());
                }
                final var band = PhantomPartyFarmEligibility.forRoster(stock.stream().map(Player::getLevel).toList()).orElse(null);
                for (var actor : stock)
                {
                    if (!kill && actor.getTarget() instanceof Monster mob && !mob.isAlikeDead() && !mob.isRaid() && !mob.isInvul() && mob.isTargetable() && mob.isSpawned()
                        && band != null && band.contains(mob.getLevel()) && targets.size() < 32
                        && planner.partyNativeTarget(group.objective(), mob.getId(), new PhantomTopologyPoint(mob.getX(), mob.getY(), mob.getZ(), mob.getInstanceId())))
                    {
                        targets.computeIfAbsent(mob.getNativeEvidenceTarget(), _ ->
                        {
                            final var before = new HashMap<Integer, PlayerNativeEvidence.Snapshot>();
                            for (var peer : stock)
                            {
                                final var sensor = PlayerNativeWork.observationEvidence(peer);
                                if (sensor != null) { before.put(peer.getObjectId(), sensor.snapshot()); }
                            }
                            return new TargetSeen(mob, party, stock, leader.getObjectId(), group, mob.getCurrentHp(), Map.copyOf(before), stockDamage(mob), new HashSet<>());
                        });
                    }
                    final int casterId = actor.getObjectId();
                    final boolean casting = actor.isCastingNow();
                    final var skill = actor.getLastSkillCast();
                    if (casting && !healing.containsKey(casterId) && actor.hasAI() && actor.getAI().getCastTarget() instanceof Player nativeTarget && nativeTarget.getParty() == party
                        && actor.getCurrentSkill() != null && skill != null && actor.getCurrentSkill().getSkillId() == skill.getId()
                        && "combat.heal".equals(org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.nativeCapability(skill)))
                    {
                        final var mp = priorMp.get(casterId);
                        if (mp != null)
                        {
                            final var recipients = skill.getTargetList(actor, false, nativeTarget).stream().filter(Player.class::isInstance).map(Player.class::cast)
                                .filter(peer -> peer != actor && peer.getParty() == party && priorHp.containsKey(peer.getObjectId()) && priorHealReceipt.containsKey(peer.getObjectId()))
                                .map(peer -> new HealRecipient(peer, priorHealReceipt.get(peer.getObjectId()), priorHp.get(peer.getObjectId()))).toList();
                            if (!recipients.isEmpty()) { healing.put(casterId, new HealSample(skill, recipients, mp)); }
                        }
                    }
                    final var sample = healing.get(casterId);
                    if (sample != null && !casting)
                    {
                        healing.remove(casterId);
                        final double mpAfter = actor.getCurrentMp();
                        if (sample.skill() != skill || mpAfter >= sample.mpBefore()) { continue; }
                        for (var recipient : sample.recipients())
                        {
                            final var receipt = restoredHp(recipient.player());
                            if (recipient.player().getParty() != party || receipt == null || receipt.sequence() <= recipient.receiptBefore()
                                || !receipt.casterName().equals(actor.getName())) { continue; }
                            heal = true;
                            healMpDebit = true;
                            System.out.println("NATIVE130_PHANTOM_HEAL caster=" + casterId + " target=" + recipient.player().getObjectId()
                                + " skill=" + skill.getId() + ":" + skill.getLevel() + " hpBefore=" + recipient.hpBefore() + " hpAfter=" + recipient.player().getCurrentHp()
                                + " mpBefore=" + sample.mpBefore() + " mpAfter=" + mpAfter + " restoredHp=" + receipt.restoredHp()
                                + " hpAtStockReceipt=" + receipt.hpAtReceipt() + " receipt=" + receipt.sequence());
                        }
                    }
                }
                for (var entry : targets.entrySet())
                {
                    final var target = entry.getValue();
                    for (var damage : stockDamage(target.mob()).entrySet())
                    {
                        if (target.before().containsKey(damage.getKey()) && damage.getValue() > target.initialDamage().getOrDefault(damage.getKey(), 0L)) { target.observedDamage().add(damage.getKey()); }
                    }
                    if (kill || !target.mob().isDead() || !(target.initialHp() > target.mob().getCurrentHp()) || !entry.getKey().equals(target.mob().getNativeEvidenceTarget())) { continue; }
                    final var samples = new ArrayList<MemberFight>();
                    for (var actor : stock)
                    {
                        final var before = target.before().get(actor.getObjectId());
                        final var sensor = PlayerNativeWork.observationEvidence(actor);
                        if (before == null || sensor == null || actor.getParty() != party) { continue; }
                        synchronized (sensor) { samples.add(new MemberFight(before, sensor.snapshot(), sensor.targetReceipts(), target.observedDamage().contains(actor.getObjectId()))); }
                    }
                    final var earned = exactGroupRewards(entry.getKey(), samples);
                    if (!earned.isEmpty() && stock.equals(List.copyOf(party.getMembers())))
                    {
                        kill = true;
                        earnedTarget = target;
                        System.out.println("NATIVE130_EXACT_GROUP_KILL target=" + entry.getKey() + " npc=" + target.mob().getId() + " level=" + target.mob().getLevel()
                            + " source=" + group.objective().key() + " leader=" + leader.getObjectId() + " roster=" + stock.stream().map(Player::getObjectId).toList()
                            + " hpBefore=" + target.initialHp() + " hpAfter=" + target.mob().getCurrentHp() + " earned=" + earned);
                    }
                }
                if (earnedTarget != null && earnedTarget.party() == party && !loot)
                {
                    for (var actor : stock)
                    {
                        final var before = earnedTarget.before().get(actor.getObjectId());
                        final var sensor = PlayerNativeWork.observationEvidence(actor);
                        if (before == null || sensor == null) { continue; }
                        final var after = sensor.snapshot();
                        if (after.epoch() != before.epoch() || after.overflow() || after.lootSequence() <= before.lootSequence()
                            || after.lootCount() <= before.lootCount()) { continue; }
                        loot = true;
                        System.out.println("NATIVE130_EARNED_LOOT actor=" + actor.getObjectId() + " group=" + group.groupId()
                            + " nativeReceipts=" + (after.lootSequence() - before.lootSequence()) + " count=" + (after.lootCount() - before.lootCount()));
                        break;
                    }
                }
                if (kill) { targets.clear(); }
            }
            else { targets.clear(); }
            for (var actor : actors)
            {
                priorHp.put(actor.getObjectId(), actor.getCurrentHp());
                priorMp.put(actor.getObjectId(), actor.getCurrentMp());
                final var receipt = restoredHp(actor);
                priorHealReceipt.put(actor.getObjectId(), receipt == null ? 0 : receipt.sequence());
            }
            if (System.currentTimeMillis() >= nextTrace)
            {
                boolean blocked = actors.size() < ids.size();
                boolean stableCoordination = group.phase() == PhantomLogicalParty.Phase.WAKE_REQUESTED && group.waitReason().startsWith("WAIT_") && actors.size() == ids.size()
                    && actors.stream().allMatch(actor -> !actor.isMoving() && !actor.isInCombat() && !actor.isAttackingNow() && !actor.isCastingNow() && !actor.isDead());
                boolean idleAssembly = group.phase() == PhantomLogicalParty.Phase.ASSEMBLING && !partySeen && actors.size() == ids.size()
                    && actors.stream().allMatch(actor -> !actor.isMoving() && !actor.isInCombat() && !actor.isAttackingNow() && !actor.isCastingNow() && actor.getCurrentHp() > 0);
                final String facts = actors.stream().map(actor -> actor.getObjectId() + ":hp=" + actor.getCurrentHp() + ":mp=" + actor.getCurrentMp() + ":target=" + (actor.getTarget() == null ? 0 : actor.getTarget().getObjectId()) + ":cast=" + (actor.getLastSkillCast() == null ? 0 : actor.getLastSkillCast().getId()) + ":party=" + (actor.getParty() == null ? 0 : actor.getParty().getLeaderObjectId()) + ":evidence=" + (actor.getNativeWorkOwner() == null ? "NONE" : actor.getNativeWorkOwner().evidence().snapshot())).toList().toString();
                Files.writeString(trace, (System.currentTimeMillis() - start) + "\t" + group.phase() + ":" + group.waitReason() + ":" + group.nextDueMillis() + "\t" + PhantomSystem.operatorCoordinationCensus() + "\t" + facts + "\n", StandardOpenOption.APPEND);
                System.out.println("NATIVE130_OBSERVE millis=" + (System.currentTimeMillis() - start) + " phase=" + group.phase() + " actors=" + actors.size() + " party=" + partySeen + " kill=" + kill + " heal=" + heal + " mpDebit=" + healMpDebit);
                for (long id : ids)
                {
                    final var progress = PhantomSystem.operatorReadinessProgress(id); final var life = PhantomSystem.operatorLifeProgress(id);
                    final var admission = PhantomSystem.operatorAdmissionProfile(id).orElse(null);
                    final String reason = admission == null || admission.readiness() == null ? "" : admission.readiness().reason();
                    final var actor = actors.stream().filter(value -> value.getObjectId() == objects.get(id)).findFirst().orElse(null);
                    if (actor == null)
                    {
                        blocked &= admission != null && admission.readiness() != null && !admission.readiness().queued() && !admission.readiness().running()
                            && Set.of("catchup.baseline.initial_binding_changed", "catchup.goal.conflict", "catchup.baseline.conflict", "ecology.orphan_historical_conflict", "native_context.required:logical_party_preparation", "native_context.required:logical_party_support").contains(reason);
                    }
                    System.out.println("NATIVE130_WAIT profile=" + id + " admission=" + (admission == null ? null : admission.readiness())
                        + " stage=" + progress.get("currentStage") + " retry=" + progress.get("nextRetryMillis") + " visible=" + life.get("historical.visibleRecovery")
                        + " preparation=" + life.get("historical.visiblePreparation") + " decision=" + life.get("decision")
                        + " policy=" + (admission == null ? null : admission.admission()) + " materializationFailure=" + (admission == null ? null : admission.lastMaterializationFailure())
                        + " busy=" + (admission == null ? "" : admission.busyReason()) + " pins=" + (admission == null ? "" : admission.retentionPins())
                        + " supply=" + life.get("supply") + " supplyReason=" + life.get("supply.reason") + " learning=" + life.get("learning")
                        + " rendezvous=" + life.get("party.rendezvous") + " partyClaim=" + life.get("party.claim") + " partyRoute=" + life.get("party.route.reason") + " travel=" + life.get("farm.travel.reason") + " routeProof=" + life.get("farm.travel.proof") + " objective=" + group.objective() + " generation=" + group.generation() + " vitals=" + life.get("native.vitals") + " economy=" + life.get("native.economy") + " owner=" + life.get("native.owner"));
                    if (actor != null) { System.out.println("NATIVE130_POSITION profile=" + id + " xyz=" + actor.getLocation() + " moving=" + actor.isMoving()); }
                    idleAssembly &= !String.valueOf(life.get("supply")).contains("status=ACTIVE") && !String.valueOf(life.get("learning")).contains("status=ACTIVE");
                    stableCoordination &= !String.valueOf(life.get("supply")).contains("status=ACTIVE") && !String.valueOf(life.get("learning")).contains("status=ACTIVE");
                }
                stableBlockSamples = blocked ? stableBlockSamples + 1 : 0;
                check(stableBlockSamples < 3, "STABLE_READINESS_BLOCK");
                idleAssemblySamples = idleAssembly ? idleAssemblySamples + 1 : 0;
                check(idleAssemblySamples < 7, "IDLE_ASSEMBLY_DIAGNOSED");
                // An unchanged recovery snapshot is not another evaluation of its refusal predicate.
                if (!stableCoordination) { stableCoordinationSamples = 0; }
                else if (logicalRow.rowVersion() != lastCoordinationVersion)
                { stableCoordinationSamples = lastCoordinationWait.equals(group.waitReason()) ? stableCoordinationSamples + 1 : 1; }
                lastCoordinationWait = group.waitReason();
                lastCoordinationVersion = logicalRow.rowVersion();
                check(stableCoordinationSamples < 3, "STABLE_COORDINATION_BLOCK_" + group.waitReason());
                nextTrace = System.currentTimeMillis() + 10_000;
            }
            if (usefulGroupEvidence(partySeen, kill, healerRequired, heal, healMpDebit, restart) && (restart || loot) && (!travelRequired || groupTravel)
                && actors.isEmpty() && group.phase() == PhantomLogicalParty.Phase.BACKGROUND)
            {
                System.out.println("NATIVE130_USEFUL_GROUP_PASS restart=" + restart + " officialParty=" + partySeen + " realGroupKill=" + kill + " healerRequired=" + healerRequired + " nativeHeal=" + heal + " supportMpDebit=" + healMpDebit + " zeroWorld=true");
                return;
            }
            check(partyGeneration != group.generation() || kill || group.phase() != PhantomLogicalParty.Phase.BACKGROUND
                || group.activityReplanRequired(), "PARTY_DRAINED_WITHOUT_USEFUL_ACTION");
            Thread.sleep(50);
        }
        throw new IllegalStateException("NATIVE130_FINITE_EVIDENCE_DEADLINE");
    }
    static String persisted(PhantomProfileRepository profiles, List<Long> ids) throws Exception
    {
        final var output = new StringBuilder();
        for (long id : ids)
        {
            try (var connection = DatabaseFactory.getConnection(); var query = connection.prepareStatement("SELECT charId,level,exp,sp,curHp,curMp,x,y,z FROM characters WHERE charId=?"))
            {
                connection.setReadOnly(true); query.setInt(1, profiles.find(id).orElseThrow().characterObjectId());
                try (var rows = query.executeQuery()) { check(rows.next(), "PERSISTED_ACTOR"); for (int column = 1; column <= 9; column++) { output.append(rows.getString(column)).append('\t'); } output.append('\n'); }
            }
            try (var connection = DatabaseFactory.getConnection())
            {
                connection.setReadOnly(true);
                for (String sql : List.of("SELECT object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id=? ORDER BY object_id",
                    "SELECT skill_id,skill_level,class_index FROM character_skills WHERE charId=? ORDER BY class_index,skill_id"))
                {
                    try (var query = connection.prepareStatement(sql))
                    {
                        query.setInt(1, profiles.find(id).orElseThrow().characterObjectId());
                        try (var rows = query.executeQuery()) { while (rows.next()) { for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) { output.append(rows.getString(column)).append('\t'); } output.append('\n'); } }
                    }
                }
            }
            for (String type : List.of(PhantomLogicalParty.COMPONENT_TYPE, PhantomLogicalParty.CLAIM_COMPONENT_TYPE, PhantomBackgroundState.COMPONENT_TYPE, PhantomGoalStateStore.COMPONENT_TYPE, PhantomBackgroundCatchupState.COMPONENT_TYPE))
            { final var value = profiles.findComponent(id, type); if (value.isPresent()) { output.append(id).append(':').append(type).append(':').append(value.get().rowVersion()).append(':').append(Base64.getEncoder().encodeToString(value.get().payload())).append('\n'); } }
        }
        return output.toString();
    }
}

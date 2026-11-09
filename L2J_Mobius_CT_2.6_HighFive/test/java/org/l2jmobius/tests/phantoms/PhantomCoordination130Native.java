/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
import org.l2jmobius.gameserver.model.actor.appearance.PlayerAppearance;
import org.l2jmobius.gameserver.model.actor.enums.player.PlayerClass;
import org.l2jmobius.gameserver.model.actor.instance.Monster;
import org.l2jmobius.gameserver.model.item.enums.ItemProcessType;
import org.l2jmobius.gameserver.phantoms.PhantomSystem;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.party.*;
import org.l2jmobius.gameserver.phantoms.population.*;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;
import org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole;

/** Native modes of the existing SQL130 entrypoint: real GameServer and its configured owners. */
final class PhantomCoordination130Native
{
    private static final ThreadLocal<Boolean> AUTHORITY_SQL_INITIALIZATION = ThreadLocal.withInitial(() -> false);
    static void initializeAuthoritySql(Path module) throws Exception
    {
        final Path runtime = module.getParent().getParent().resolve("runtime").toRealPath();
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
        return label.equals("original") ? runtime : runtime.resolve("morning");
    }

    static void recoverAgreement(Path module, PhantomProfileRepository profiles) throws Exception
    {
        final Path runtime = module.getParent().getParent().resolve("runtime").toRealPath();
        check(runtime.equals(Path.of(".").toRealPath()) && Files.readString(runtime.resolve("owner130.txt")).matches("TASK130:[0-9a-f]{32}"), "RECOVERY_OWN_RUNTIME");
        check(Files.mismatch(runtime.resolve("config/Database.ini"), module.resolve(".phantom-local/task130/Database.ini")) == -1
            && Files.readString(runtime.resolve("config/Custom/PhantomPlayers.ini")).contains("EnablePhantomSystem = False") && !PhantomSystem.hasConfiguredInstance(), "RECOVERY_DISABLED");
        final var ids = authorizedProfiles(runtime);
        final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
        final long now = System.currentTimeMillis();
        for (long id : ids) { System.out.println("SQL130_RECOVERY_INSPECT profile=" + id + " group=" + logical.groupFor(id).map(row -> row.group().phase() + ":expired=" + (now >= row.group().expiresAtMillis()))); }
        System.out.println("SQL130_RECOVERY_INSPECT_ONLY;SQL_WRITES=0;OLD_OBJECTIVE_PINNED=false;REQUIRED_OWNER=PhantomScheduler/PhantomExecutionControlPort/PhantomAutonomousFarmParty");
        final var zone = ZoneId.of(read(runtime.resolve("config/Custom/PhantomPlayers.ini")).getProperty("PhantomPopulationTimeZone", "UTC"));
        final var catalog = PhantomPopulationCatalog.load(runtime.resolve("data/phantoms/population/high-five-population-v3.xml"), zone);
        final var eligible = new LinkedHashSet<Long>();
        final var goals = new PhantomGoalStateStore(profiles);
        for (long id : ids)
        {
            final var state = new PhantomBackgroundStateCodec().decode(profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
            final var root = goals.load(id).orElseThrow().goal();
            final var population = new PhantomPopulationStateCodec().decode(profiles.findComponent(id, PhantomPopulationState.COMPONENT_TYPE).orElseThrow().payload());
            final var schedule = catalog.evaluate(population.scheduleTemplate(), Instant.ofEpochMilli(now), zone, population.schedulePhaseMinutes());
            final boolean consent = PhantomPlayersConfig.PartySeekingSettings.defaults().seeks(id, state.progress().level());
            final var receipt = new org.l2jmobius.gameserver.phantoms.commerce.PhantomCommerceReceiptStore(profiles).find(id);
            final boolean obligations = profiles.findComponent(id, PhantomOwnedStoreIntent.COMPONENT_TYPE).isPresent()
                || receipt.filter(row -> !row.receipt().state().terminal()).isPresent()
                || profiles.findComponent(id, PhantomNativeContext.COMPONENT_TYPE).map(row -> PhantomNativeContext.decode(row.payload()).phase() != PhantomNativeContext.Phase.COMPLETED).orElse(false);
            final boolean claimed = new PhantomPartyStore(profiles).loadLogicalClaim(id).filter(PhantomPartyPersistencePort.StoredLogicalClaim::active).isPresent();
            final boolean ready = population.state() == PhantomPopulationState.State.READY && population.catalogHash().equals(catalog.hash())
                && state.state() == PhantomBackgroundState.State.READY && schedule.state().requiresMaterialization() && consent
                && PhantomAutonomousFarmParty.ordinaryIntent(id, root) && !obligations && !claimed;
            System.out.println("SQL130_SCENE_MEMBER profile=" + id + " class=" + state.identity().activeClassId() + " level=" + state.progress().level()
                + " role=" + PhantomHistoricalBackgroundPlanner.logicalRole(state) + " schedule=" + schedule + " consent=" + consent + " obligations=" + obligations
                + " claimed=" + claimed + " eligibleNow=" + ready + " position=" + state.position());
            if (ready) { eligible.add(id); }
        }
        System.out.println("SQL130_SCENE_INSPECT_ONLY;eligible=" + eligible + ";COMMON_SOURCE=UNKNOWN;SQL_WRITES=0;CATALOG_NATIVE_BOOTSTRAP_NOT_RUN=true");
    }

    static Optional<PhantomPartyPersistencePort.StoredLogicalGroup> preflightAgreement(List<PhantomLogicalPartyCoordinator.Candidate> candidates,
        java.util.function.BiPredicate<Long, Integer> seeking, java.util.function.Predicate<PhantomLogicalParty> physical, long now)
    { return new PhantomLogicalPartyCoordinator(new PhantomWorldCoordination130Suite.MemoryStore()).discover(candidates, seeking, now).filter(row -> physical.test(row.group())); }
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
    private static PhantomLogicalParty inspectLoadedPlanner(Path runtime, PhantomProfileRepository profiles, List<Long> ids,
        PhantomSystem.PlanningInspection inspection, boolean restart, Properties saved) throws Exception
    {
        check(!PhantomSystem.hasConfiguredInstance(), "PREFLIGHT_BEFORE_EXECUTION");
        final long recoveryStarted = System.nanoTime();
        final var recoveryBefore = preflightWitness(profiles, ids, true);
        final var catalog = PhantomPopulationCatalog.load(runtime.resolve("data/phantoms/population/high-five-population-v3.xml"), inspection.settings().populationTimeZone());
        final var goals = new PhantomGoalStateStore(profiles); final var parties = new PhantomPartyStore(profiles);
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
            final boolean eligible = population.state() == PhantomPopulationState.State.READY && population.catalogHash().equals(catalog.hash()) && calendar.state().requiresMaterialization()
                && state.state() == PhantomBackgroundState.State.READY && consent && !blocked && (claim.isEmpty() || restart && claim.get().groupId().equals(saved.getProperty("group")))
                && PhantomAutonomousFarmParty.ordinaryIntent(id, root);
            System.out.println("NATIVE130_PREFLIGHT_MEMBER id=" + id + " eligible=" + eligible + " class=" + state.identity().activeClassId() + " level=" + state.progress().level()
                + " role=" + inspection.authorityRecovery().potentialPartyRole(id, state) + " calendar=" + calendar + " consent=" + consent + " obligations=" + blocked + " claimed=" + claim.isPresent() + " position=" + state.position());
            if (!eligible) { continue; }
            inspectAuthority(id, state.hashes(), inspection.planner().generation().authorityHashes(), System.out::println);
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
        check(recoveryBefore.equals(preflightWitness(profiles, ids, true)), "PREFLIGHT_RECOVERY_CONSERVATION_WITNESS");
        checkPreflightBudget(System.nanoTime() - recoveryStarted, true);
        final long sceneStarted = System.nanoTime(); final long now = System.currentTimeMillis();
        final var before = preflightWitness(profiles, ids, false);
        final var states = new LinkedHashMap<Long, PhantomBackgroundState>(); final var candidates = new ArrayList<PhantomLogicalPartyCoordinator.Candidate>();
        for (long id : eligibleIds)
        {
            checkPreflightBudget(System.nanoTime() - sceneStarted, false);
            final var root = goals.load(id).orElseThrow().goal();
            final var population = new PhantomPopulationStateCodec().decode(profiles.findComponent(id, PhantomPopulationState.COMPONENT_TYPE).orElseThrow().payload());
            check(catalog.evaluate(population.scheduleTemplate(), Instant.ofEpochMilli(now), inspection.settings().populationTimeZone(), population.schedulePhaseMinutes()).state().requiresMaterialization(), "PREFLIGHT_CALENDAR_CHANGED_" + id);
            final var restored = new PhantomBackgroundStateCodec().decode(profiles.findComponent(id, PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
            inspectAuthority(id, restored.hashes(), inspection.planner().generation().authorityHashes(), System.out::println);
            final var history = new PhantomBackgroundCatchupStore(profiles, goals).load(id).orElseThrow().state();
            final var sources = inspection.planner().rendezvousSources(id, restored, root, history.deterministicSeed(), history.planOrdinal(), Set.of(), Set.of());
            System.out.println("NATIVE130_PREFLIGHT_SOURCES id=" + id + " count=" + sources.size() + " first4=" + sources.stream().limit(4).toList());
            states.put(id, restored);
            final String role = inspection.authorityRecovery().potentialPartyRole(id, restored);
            check(!role.equals("pending.native_role"), "PREFLIGHT_DURABLE_ROLE_" + id);
            candidates.add(new PhantomLogicalPartyCoordinator.Candidate(new PhantomLogicalParty.Member(id, role, root.goalId(), root.revision()), restored.progress().level(), sources,
                source -> inspection.planner().partySourceAvailable(restored, source, Set.of()), () -> true, () -> role.equals(inspection.authorityRecovery().potentialPartyRole(id, restored)),
                admitted -> inspection.planner().rendezvousSources(id, restored, root, history.deterministicSeed(), history.planOrdinal(), Set.of(), Set.of(), admitted)));
        }
        final java.util.function.Predicate<PhantomLogicalParty> physical = group ->
        {
            final var point = inspection.topology().findAnchor(group.anchorId()).orElseThrow().point();
            for (var member : group.members())
            {
                final var position = states.get(member.profileId()).position();
                if (!loadedRoute("profile=" + member.profileId(), new PhantomGeoValidationRules.Point(position.x(),position.y(),position.z(),position.instanceId()),
                    new PhantomGeoValidationRules.Point(point.x(),point.y(),point.z(),point.instanceId()))) { return false; }
            }
            final int npc = Integer.parseInt(group.objective().key().split("@")[0]);
            final var band = org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.forRoster(group.members().stream().map(member -> states.get(member.profileId()).progress().level()).toList()).orElse(null);
            if (band == null) { return false; }
            final var spawns = org.l2jmobius.gameserver.data.SpawnTable.getInstance().getSpawns(npc);
            final var mobs = spawns.stream().flatMap(spawn -> spawn.getSpawnedNpcs().stream()).filter(actor -> actor instanceof Monster && !actor.isDead() && !actor.isRaid()
                && actor.isTargetable() && band.contains(actor.getLevel()) && actor.getInstanceId() == point.instanceId() && World.getInstance().findObject(actor.getObjectId()) == actor
                && Math.hypot((long) actor.getX() - point.x(), (long) actor.getY() - point.y()) <= 1200).sorted(Comparator.comparingDouble(actor -> Math.hypot((long) actor.getX() - point.x(), (long) actor.getY() - point.y()))).limit(4).toList();
            System.out.println("NATIVE130_PREFLIGHT_STOCK_SPAWN npc=" + npc + " configured=" + spawns.size() + " nearbyLive=" + mobs.size());
            return mobs.stream().anyMatch(mob -> loadedRoute("stockNpc=" + npc + ":object=" + mob.getObjectId(), new PhantomGeoValidationRules.Point(point.x(),point.y(),point.z(),point.instanceId()),
                new PhantomGeoValidationRules.Point(mob.getX(),mob.getY(),mob.getZ(),mob.getInstanceId())));
        };
        final PhantomLogicalParty result;
        if (restart)
        {
            final var row = currentAgreement(new PhantomLogicalPartyCoordinator(parties), ids, now).orElseThrow(() -> new IllegalStateException("NATIVE130_RESTART_GROUP_NOT_CURRENT"));
            check(row.group().groupId().equals(saved.getProperty("group")) && row.group().members().stream().allMatch(member -> candidates.stream().anyMatch(candidate -> candidate.member().profileId() == member.profileId() && candidate.reachable().test(row.group().objective())
                && org.l2jmobius.gameserver.phantoms.party.PhantomPartyFarmEligibility.compatibleRole(member.role(), candidate.member().role()))) && physical.test(row.group()), "RESTART_CURRENT_SOURCE_PROOF");
            result = row.group();
        }
        else { result = preflightAgreement(candidates, inspection.settings().partySeeking()::seeks, physical, now).orElseThrow(() -> new IllegalStateException("NATIVE130_NO_LAWFUL_FEASIBLE_SCENE" )).group(); }
        verifySceneWitness(before, preflightWitness(profiles, ids, false));
        checkPreflightBudget(System.nanoTime() - sceneStarted, false);
        System.out.println("NATIVE130_PREFLIGHT_PASS objective=" + result.objective() + " members=" + result.members() + " RECOVERY_WRITES=CANONICAL_ONLY SCENE_SQL_WRITES=0 SAME_PRODUCTION_PLANNER=true recoveryMillis=" + TimeUnit.NANOSECONDS.toMillis(sceneStarted - recoveryStarted) + " sceneMillis=" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sceneStarted));
        return result;
    }
    static org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result preparePreflightAuthority(
        PhantomProfileRepository profiles, long id, java.util.function.Supplier<org.l2jmobius.gameserver.phantoms.background.PhantomHistoricalBackgroundService.Result> preparation) throws Exception
    {
        final var before = preflightWitness(profiles, List.of(id), true);
        final var history = new PhantomBackgroundCatchupStore(profiles, new PhantomGoalStateStore(profiles)).load(id).orElseThrow();
        check(history.state().status() == PhantomBackgroundCatchupState.Status.COMPLETE, "PREFLIGHT_HISTORY_NOT_COMPLETE_" + id);
        final var prepared = preparation.get();
        check(prepared.successful(), "PREFLIGHT_AUTHORITY_REFRESH_" + id + "_" + prepared.reason());
        check(prepared.advancedIntervals() == 0 && before.equals(preflightWitness(profiles, List.of(id), true)), "PREFLIGHT_RECOVERY_CONSERVATION_WITNESS");
        return prepared;
    }
    record PreflightWitness(String canonical, String components) { }
    static void verifySceneWitness(PreflightWitness before, PreflightWitness after) { check(before.equals(after), "PREFLIGHT_READ_ONLY_WITNESS"); }
    static void checkPreflightBudget(long elapsedNanos, boolean recovery)
    {
        check(elapsedNanos >= 0 && elapsedNanos < TimeUnit.SECONDS.toNanos(recovery ? 60 : 30), recovery ? "PREFLIGHT_RECOVERY_DEADLINE" : "PREFLIGHT_SCENE_DEADLINE");
    }
    static PreflightWitness preflightWitness(PhantomProfileRepository profiles, List<Long> ids, boolean recovery) throws Exception
    {
        final var output = new StringBuilder();
        final var components = new StringBuilder();
        final var allowed = Set.of(PhantomBackgroundState.COMPONENT_TYPE, PhantomNativeContext.COMPONENT_TYPE, PhantomExecutionHandoffStore.COMPONENT_TYPE);
        final var nativeColumns = Set.of("maxhp", "curhp", "maxcp", "curcp", "maxmp", "curmp", "heading", "x", "y", "z", "online", "onlinetime", "lastaccess", "vitality_points");
        for (long id : ids)
        {
            output.append("profile=").append(id).append('\n');
            try (var connection = DatabaseFactory.getConnection())
            {
                connection.setReadOnly(true);
                final var queries = new ArrayList<>(List.of("SELECT * FROM characters WHERE charId=?",
                    recovery ? "SELECT object_id,item_id,count,loc,loc_data,enchant_level FROM items WHERE owner_id=? ORDER BY object_id" : "SELECT * FROM items WHERE owner_id=? ORDER BY object_id"));
                if (!recovery) { queries.add("SELECT * FROM character_skills WHERE charId=? ORDER BY class_index,skill_id"); }
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
                                    if (recovery && sql.contains("FROM characters") && nativeColumns.contains(label.toLowerCase(Locale.ROOT))) { continue; }
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
    private static boolean loadedRoute(String label, PhantomGeoValidationRules.Point from, PhantomGeoValidationRules.Point to)
    {
        final var geo = GeoEngine.getInstance(); final var navigation = new org.l2jmobius.gameserver.phantoms.navigation.L2jNavigationBackend();
        final var probe = new PhantomGeoValidationRules.Probe()
        {
            @Override public boolean hasGeo(int x, int y) { return geo.hasGeo(x,y); }
            @Override public int height(int x, int y, int z) { return geo.getHeight(x,y,z); }
            @Override public boolean canMove(PhantomGeoValidationRules.Point a, PhantomGeoValidationRules.Point b)
            { return navigation.canWalkDirect(new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(a.x(),a.y(),a.z(),a.instanceId()), new org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationPoint(b.x(),b.y(),b.z(),b.instanceId())); }
            @Override public List<PhantomGeoValidationRules.Point> path(PhantomGeoValidationRules.Point a, PhantomGeoValidationRules.Point b)
            {
                final var found = org.l2jmobius.gameserver.geoengine.pathfinding.PathFinding.getInstance().findPath(a.x(),a.y(),a.z(),b.x(),b.y(),b.z(),a.instanceId(),true);
                return found == null ? null : found.stream().map(point -> new PhantomGeoValidationRules.Point(point.getX(),point.getY(),point.getZ(),a.instanceId())).toList();
            }
        };
        final var proof = PhantomGeoValidationRules.runtimeRoute(from,to,probe);
        System.out.println("NATIVE130_PREFLIGHT_GEO " + label + " from=" + from + " to=" + to + " status=" + proof.status() + " waypoints=" + proof.waypoints().size() + " proof=" + proof.proof().diagnostic());
        return proof.status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.DIRECT_VALIDATED || proof.status() == org.l2jmobius.gameserver.phantoms.navigation.PhantomNavigationResult.Status.PATH_FOUND;
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
        for (Path file : List.of(runtime.resolve("fixture130.properties"), runtime.resolve("morning/fixture130.properties")))
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
        check(runtime.equals(module.getParent().getParent().resolve("runtime")) && runtime.equals(Path.of(".").toRealPath()), "INFRASTRUCTURE_CWD");
        check(Files.readString(runtime.resolve("owner130.txt")).matches("TASK130:[0-9a-f]{32}"), "INFRASTRUCTURE_OWNER");
        if (!AUTHORITY_SQL_INITIALIZATION.get())
        { check(!Files.exists(scene(runtime).resolve("native130.cut")) && !Files.exists(scene(runtime).resolve("restart130.cut")), "FIXTURE_BEFORE_CUT"); }
        check(Path.of(System.getProperty("phantom.test.config")).toRealPath().equals(runtime.resolve("config/Database.ini"))
            && Files.mismatch(runtime.resolve("config/Database.ini"), module.resolve(".phantom-local/task130/Database.ini")) == -1, "INFRASTRUCTURE_EXACT_SQL");
        final var sql = read(runtime.resolve("config/Database.ini"));
        check(sql.getProperty("URL").equals("jdbc:mysql://127.0.0.1:3308/l2jmobiush5_phantom_test_130?useSSL=false&serverTimezone=UTC")
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

    static void run(Path module, boolean restart) throws Exception
    {
        final Path runtime = Path.of(System.getProperty("phantom.task130.runtime")).toRealPath();
        check(Path.of(".").toRealPath().equals(runtime) && runtime.equals(module.getParent().getParent().resolve("runtime")), "OWN_CWD");
        check(Files.readString(runtime.resolve("owner130.txt")).matches("TASK130:[0-9a-f]{32}"), "OWNER");
        final Path jar = Path.of(System.getProperty("phantom.task130.jar")).toRealPath();
        check(jar.getParent().equals(runtime.resolve("libs")) && jar.getFileName().toString().matches("GameServer\\.[0-9A-Fa-f]{64}\\.jar")
            && jar.getFileName().toString().equalsIgnoreCase("GameServer." + hash(jar) + ".jar"), "PINNED_JAR_NAME_AND_HASH");
        for (var type : List.of(GameServer.class, PhantomSystem.class, PhantomLogicalParty.class))
        { check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(jar), "FINAL_JAR_CLASS_SOURCE"); }
        check(Files.mismatch(runtime.resolve("config/Database.ini"), module.resolve(".phantom-local/task130/Database.ini")) == -1, "EXACT_OWN_DATABASE");
        if (Boolean.getBoolean("phantom.task130.authorityOnly"))
        {
            final Path origin = Path.of(PhantomCoordination130Native.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            check(origin.equals(module.getParent().resolve("build/phantom-test/bin").toRealPath()), "AUTHORITY_DIAGNOSTIC_TEST_BIN_ORIGIN");
            System.out.println("NATIVE130_DIAGNOSTIC_CLASS source=" + origin + " class=" + PhantomCoordination130Native.class.getName());
        }
        final String jarHash = hash(jar);
        final Path evidence = scene(runtime), fixture = evidence.resolve("fixture130.properties");
        final Path config = runtime.resolve("config/Custom/PhantomPlayers.ini");
        final String disabled = Files.readString(config);
        check(disabled.contains("EnablePhantomSystem = False"), "MASTER_DISABLED_BEFORE_BOOT");
        if (restart) { check(Files.exists(evidence.resolve("native130.complete")) && read(evidence.resolve("native130.complete")).getProperty("jar").equals(jarHash), "RESTART_SAME_FINAL_JAR"); }
        write(evidence.resolve((restart ? "restart130.started." : "native130.started.") + ProcessHandle.current().pid()), "pid=" + ProcessHandle.current().pid() + "\njar=" + jarHash + "\n");
        boolean booted = false;
        try
        {
            GameServer.main(new String[0]); booted = true;
            final long deadline = System.currentTimeMillis() + 300_000;
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
            check(disabled.contains("EnablePhantomSystem = False"), "MASTER_DECLARATION");
            Files.writeString(config, disabled.replace("EnablePhantomSystem = False", "EnablePhantomSystem = True"));
            PhantomPlayersConfig.load();
            final var inspectionIds = ids; final var proved = new java.util.concurrent.atomic.AtomicReference<PhantomLogicalParty>();
            final var enabled = PhantomSystem.operatorEnable(inspection ->
            {
                try
                {
                    if (Boolean.getBoolean("phantom.task130.authorityOnly"))
                    {
                        inspectLoadedAuthority(runtime, profiles, inspectionIds, inspection);
                        throw new IllegalStateException("NATIVE130_AUTHORITY_DIAGNOSTIC_COMPLETE");
                    }
                    proved.set(inspectLoadedPlanner(runtime,profiles,inspectionIds,inspection,restart,saved));
                    check(!Boolean.getBoolean("phantom.task130.authorityOnly"), "AUTHORITY_DIAGNOSTIC_COMPLETE");
                    if (!Files.exists(cut)) { write(cut, "zeroWorld=true\ngameplayFixtureWritesAfterCut=0\n"); }
                    write(evidence.resolve("preflight130.cut." + ProcessHandle.current().pid()), "jar=" + jarHash + "\nobjective=" + proved.get().objective() + "\nAUTHORITY_PREPARATION=CANONICAL_NATIVE_STORE_READBACK\nGAMEPLAY_FIXTURE_WRITES=0\n");
                }
                catch (Exception failure) { throw new IllegalStateException(failure.getMessage(),failure); }
            });
            System.out.println("NATIVE130_ENABLE=" + enabled);
            check(enabled.runtimeConfigured() && enabled.runtimeState() == PhantomSystem.State.RUNNING, "SYSTEM_START_" + enabled.code());
            check(proved.get() != null, "PRODUCTION_PREFLIGHT_REQUIRED");
            ids = proved.get().members().stream().map(PhantomLogicalParty.Member::profileId).toList();
            final long agreementUntil = Math.min(deadline, System.currentTimeMillis() + 30_000);
            var agreement = currentAgreement(logical, ids, System.currentTimeMillis());
            while (agreement.isEmpty() && System.currentTimeMillis() < agreementUntil)
            { Thread.sleep(200); agreement = currentAgreement(logical, ids, System.currentTimeMillis()); }
            final var before = agreement.orElseThrow(() -> new IllegalStateException("NATIVE130_NO_ORDINARY_AGREEMENT"));
            check(before.group().objective().equals(proved.get().objective()), "CURRENT_ORDINARY_SOURCE_MATCHES_PREFLIGHT");
            check(!restart || before.group().groupId().equals(saved.getProperty("group")), "RESTART_SAME_GROUP");
            ids = before.group().members().stream().map(PhantomLogicalParty.Member::profileId).toList();
            for (long id : ids) { check(World.getInstance().findObject(profiles.find(id).orElseThrow().characterObjectId()) == null, "ORDINARY_AGREEMENT_ZERO_WORLD_PROOF"); }
            write(evidence.resolve("agreement130." + ProcessHandle.current().pid()), "group=" + before.group().groupId() + "\nprofiles=" + ids + "\nobjective=" + before.group().objective() + "\nzeroWorld=true\n");
            observe(runtime, profiles, ids, before.group().groupId(), restart, deadline);
            verifyExistingHumanHeal(runtime, jarHash);
            System.out.println("NATIVE130_DISABLE=" + PhantomSystem.operatorDisable());
            final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (PhantomSystem.hasConfiguredInstance() && PhantomSystem.configuredShutdownSnapshot().systemState() != PhantomSystem.State.STOPPED && System.nanoTime() < until) { Thread.sleep(200); }
            check(ids.stream().allMatch(id -> World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId()) == null), "ZERO_WORLD_AFTER_STORE");
            final var after = logical.groupFor(ids.getFirst()).orElseThrow();
            check(after.group().groupId().equals(before.group().groupId()) && after.group().phase() == PhantomLogicalParty.Phase.BACKGROUND, "SAME_LOGICAL_BACKGROUND");
            if (!restart) { write(evidence.resolve("native130.persisted"), persisted(profiles, ids)); }
            write(evidence.resolve(restart ? "restart130.complete" : "native130.complete"), "jar=" + jarHash + "\ngroup=" + after.group().groupId() + "\nprofiles=" + String.join(",", ids.stream().map(String::valueOf).toList()) + "\npid=" + ProcessHandle.current().pid() + "\n");
            Files.writeString(config, disabled);
            System.out.println("NATIVE130_PASS restart=" + restart + " sameGroup=true actualPartyKill=true STORE=true");
        }
        catch (Throwable failure)
        {
            System.out.println("NATIVE130_FAILURE type=" + failure.getClass().getName() + " key=" + (failure.getMessage() != null && failure.getMessage().startsWith("NATIVE130_") ? failure.getMessage() : "WITHHELD"));
            for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.")) { System.out.println("NATIVE130_FRAME " + frame); } }
            write(evidence.resolve((restart ? "restart130.failed." : "native130.failed.") + ProcessHandle.current().pid()), failure.getClass().getName() + "\n");
            if (!booted)
            {
                // No configured Phantom owner exists before the master-disabled GameServer finishes booting.
                check(!PhantomSystem.hasConfiguredInstance(), "FAILED_BOOT_OWNER");
                System.exit(1);
            }
        }
        finally { if (booted) { Files.writeString(config, disabled); Shutdown.getInstance().startShutdown(null, 0, false); } }
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
        if (profile <= 0 || ordinal <= 0) { throw new IllegalArgumentException("Positive production creation identity/ordinal required."); }
        for (long seed = 130; seed < 1_000_130; seed++)
        {
            if (birth == 10 && catalog.chooseArchetype(seed, ordinal) != PhantomPopulationCatalog.CareerArchetype.HEALER) { continue; }
            if (birth == 0 && catalog.chooseArchetype(seed, ordinal) != PhantomPopulationCatalog.CareerArchetype.DAMAGE) { continue; }
            if (catalog.chooseClass(mix(seed, profile), catalog.chooseArchetype(seed, ordinal)).classId() != birth) { continue; }
            final var schedule = PhantomPopulationStore.scheduleForShell(catalog, profile, seed, null);
            final var evaluation = catalog.evaluate(schedule.template().id(), now, zone, schedule.phaseMinutes());
            if (evaluation.state().requiresMaterialization() && evaluation.nextBoundary().isAfter(now.plusSeconds(1800))) { return seed; }
        }
        throw new IllegalStateException("NATIVE130_LEGAL_BIRTH_AND_CALENDAR");
    }
    private static void prepare(Path runtime, PhantomBackgroundSuite.ProductionAuthorityFixture source, PhantomProfileRepository profiles) throws Exception
    {
        final var zone = PhantomPlayersConfig.settings().populationTimeZone();
        final var catalog = PhantomPopulationCatalog.load(ServerConfig.DATAPACK_ROOT.toPath().resolve("data/phantoms/population/high-five-population-v3.xml"), zone);
        final var population = new PhantomPopulationStore(profiles, catalog, zone);
        final Path evidence = scene(runtime), preparing = evidence.resolve("profiles130.preparing");
        final boolean morning = !evidence.equals(runtime);
        check(!Files.exists(evidence.resolve("native130.cut")), "BOUNDED_FIXTURE_BEFORE_CUT");
        final List<PhantomPopulationStore.ManagedSnapshot> existing;
        if (morning)
        {
            check(Files.readString(evidence.resolve("approval.txt")).equals("User approved exactly three additional managed TEST130 profiles before their own CUT; preserve originals69,70,72.\n"), "EXPLICIT_THREE_PROFILE_APPROVAL");
            final var declared = Files.exists(preparing) ? read(preparing).getProperty("profiles", "") : "";
            existing = declared.isEmpty() ? List.of() : Arrays.stream(declared.split(",")).map(Long::parseLong).map(population::reload).toList();
            final var allowed = new HashSet<Long>(List.of(69L,70L,72L)); existing.forEach(value -> allowed.add(value.profile().profileId()));
            check(population.loadManagedAfter(0, 16).stream().allMatch(value -> allowed.contains(value.profile().profileId())) && existing.size() <= 3, "ONLY_THREE_NEW_MANAGED_PROFILES");
        }
        else { existing = population.loadManagedAfter(0, 4); check(existing.size() <= 3, "ORIGINAL_FIXTURE_BOUND"); }
        final int ordinalBase = morning ? 3 : 0;
        final var mobs = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
            .filter(mob -> mob.isSpawned() && !mob.isRaid() && !mob.isDead() && !mob.isAggressive() && mob.getInstanceId() == 0 && mob.getLevel() >= 70 && mob.getLevel() <= 78 && mob.getMaxHp() >= 5000 && mob.getMaxHp() <= 60000).toList();
        final var fields = new ArrayList<>(source.topology().snapshot().anchors().stream().filter(anchor -> anchor.role() == PhantomTopologyAnchorRole.FARMING)
            .map(anchor -> Map.entry(anchor, L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0))).filter(entry -> entry.getValue().isPresent())
            .filter(entry -> { final var point = entry.getValue().orElseThrow(); return GeoEngine.getInstance().hasGeo(point.x(), point.y()) && mobs.stream().anyMatch(mob -> Math.hypot((long) point.x() - mob.getX(), (long) point.y() - mob.getY()) < 500 && Math.abs((long) point.z() - mob.getZ()) < 150 && GeoEngine.getInstance().canMoveToTarget(point.x(), point.y(), point.z(), mob.getX(), mob.getY(), mob.getZ(), 0)); })
            .sorted(Comparator.comparing(entry -> entry.getKey().id())).limit(32).toList());
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
        final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
        final var goals = new PhantomGoalStateStore(profiles);
        final var ids = new ArrayList<Long>(); final var members = new ArrayList<PhantomLogicalParty.Member>();
        final var seeds = new HashMap<Long, Long>();
        PhantomDomainRef objective = null;
        for (int ordinal = 0; ordinal < 3; ordinal++)
        {
            final int birth = ordinal == 2 ? 10 : 0;
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
                selected = birthSeed(catalog, next, ordinalBase + ordinal + 1, birth, zone, Instant.now());
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
                System.out.println("NATIVE130_ACTUAL_COMBAT profile=" + id + " facts=" + source.authority().planningCombat(actor) + " expertise=" + actor.getKnownSkill(239));
                PhantomHistoricalBackgroundPlanner.Result plan = null;
                for (var candidate : objective == null && (!morning || ordinal > 0) ? fields : List.of(field))
                {
                    final var position = candidate.getValue().orElseThrow(); actor.setXYZInvisible(position.x(), position.y(), position.z());
                    plan = ordinal == 2 || morning && ordinal == 0 ? planner.idleInitial(id, actor, selected, 1) : planner.planInitial(id, actor, selected, 1);
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
                actor.setCurrentHp(actor.getMaxHp() * (morning || ordinal == 2 ? 1 : 0.4));
                actor.getInventory().updateDatabase(); actor.storeMe();
                System.out.println("NATIVE130_PREFLIGHT profile=" + id + " actor=" + actor.getObjectId() + " class=" + actor.getActiveClass() + " level=" + actor.getLevel() + " hp=" + actor.getCurrentHp() + " mp=" + actor.getCurrentMp() + " heal=" + actor.getKnownSkill(1011) + " root=" + plan.goal() + " pos=" + actor.getLocation());
            }
            finally { actor.stopAllTasks(); actor.storeMe(); actor.setOnlineStatus(true, false); actor.deleteMe(); }
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
    private record TargetSeen(Monster mob, Map<Integer, Long> rewards) { }
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

    private record HealRecipient(Player player, long receiptBefore, double hpBefore) { }
    private record HealSample(org.l2jmobius.gameserver.model.skill.Skill skill, List<HealRecipient> recipients, double mpBefore) { }

    private static org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.RestoredHpReceipt restoredHp(Player recipient)
    {
        final var owner = recipient.getNativeWorkOwner();
        return owner == null || !owner.isCurrent() || owner.evidence() == null ? null : owner.evidence().restoredHpReceipt();
    }

    private static void observe(Path runtime, PhantomProfileRepository profiles, List<Long> ids, String groupId, boolean restart, long deadline) throws Exception
    {
        final var trace = scene(runtime).resolve((restart ? "restart130.trace." : "native130.trace.") + ProcessHandle.current().pid() + ".tsv");
        write(trace, "millis\tgroup\tcensus\tactors\n");
        final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
        final Map<org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target, TargetSeen> targets = new LinkedHashMap<>();
        final Map<Integer, Double> priorHp = new HashMap<>(), priorMp = new HashMap<>();
        final Map<Integer, Long> priorHealReceipt = new HashMap<>();
        final Map<Integer, HealSample> healing = new HashMap<>();
        boolean partySeen = false, kill = false, heal = false, healMpDebit = false, healerRequired = false;
        int stableBlockSamples = 0, idleAssemblySamples = 0, stableCoordinationSamples = 0;
        String lastCoordinationWait = ""; long lastCoordinationVersion = -1;
        long nextTrace = 0; final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() < deadline)
        {
            final var logicalRow = logical.groupFor(ids.getFirst()).orElseThrow();
            final var group = logicalRow.group();
            healerRequired |= group.members().stream().anyMatch(member -> member.role().equals("support.healer"));
            final int objectiveNpc = Integer.parseInt(group.objective().key().split("@")[0]);
            check(group.groupId().equals(groupId), "GROUP_ID_PRESERVED");
            final var actors = ids.stream().map(id -> World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId())).filter(Objects::nonNull).toList();
            final var party = actors.stream().map(Player::getParty).filter(Objects::nonNull).filter(value -> value.getMemberCount() >= 2 && value.getMembers().stream().allMatch(actor -> ids.stream().anyMatch(id -> profiles.find(id).orElseThrow().characterObjectId() == actor.getObjectId()))).findFirst().orElse(null);
            if (party != null)
            {
                check(party.getLeader() != null && party.getMembers().stream().allMatch(actor -> actor.getParty() == party), "OFFICIAL_PARTY");
                if (!partySeen)
                {
                    System.out.println("NATIVE130_STOCK_PARTY leader=" + party.getLeaderObjectId() + " members=" + party.getMembers().stream().map(Player::getObjectId).toList() + " logicalRoles=" + group.members());
                    partySeen = true;
                }
                for (var actor : party.getMembers())
                {
                    if (actor.getTarget() instanceof Monster mob && mob.getId() == objectiveNpc && !mob.isDead() && targets.size() < 32)
                    {
                        targets.computeIfAbsent(mob.getNativeEvidenceTarget(), _ ->
                        {
                            final Map<Integer, Long> rewards = new HashMap<>();
                            for (var peer : party.getMembers()) { if (peer.getNativeWorkOwner() != null && peer.getNativeWorkOwner().evidence() != null) { rewards.put(peer.getObjectId(), peer.getNativeWorkOwner().evidence().targetReceipts().rewardSequence()); } }
                            return new TargetSeen(mob, Map.copyOf(rewards));
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
                    if (!entry.getValue().mob().isDead()) { continue; }
                    int credited = 0; boolean damaged = false;
                    for (var actor : party.getMembers())
                    {
                        final var owner = actor.getNativeWorkOwner(); if (owner == null || owner.evidence() == null) { continue; }
                        final var receipts = owner.evidence().targetReceipts(); final var evidence = owner.evidence().snapshot();
                        if (entry.getKey().equals(receipts.rewardTarget()) && receipts.rewardSequence() > entry.getValue().rewards().getOrDefault(actor.getObjectId(), Long.MAX_VALUE)) { credited++; damaged |= evidence.damageSequence() > 0 && evidence.expGained() > 0 && evidence.spGained() > 0; }
                    }
                    if (credited >= 2 && damaged) { kill = true; }
                }
            }
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
                    final var actor = actors.stream().filter(value -> value.getObjectId() == profiles.find(id).orElseThrow().characterObjectId()).findFirst().orElse(null);
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
                        + " rendezvous=" + life.get("party.rendezvous") + " partyClaim=" + life.get("party.claim") + " travel=" + life.get("farm.travel.reason") + " routeProof=" + life.get("farm.travel.proof") + " objective=" + group.objective() + " generation=" + group.generation() + " vitals=" + life.get("native.vitals") + " economy=" + life.get("native.economy") + " owner=" + life.get("native.owner"));
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
            if (usefulGroupEvidence(partySeen, kill, healerRequired, heal, healMpDebit, restart) && actors.isEmpty() && group.phase() == PhantomLogicalParty.Phase.BACKGROUND)
            {
                System.out.println("NATIVE130_USEFUL_GROUP_PASS restart=" + restart + " officialParty=" + partySeen + " realGroupKill=" + kill + " healerRequired=" + healerRequired + " nativeHeal=" + heal + " supportMpDebit=" + healMpDebit + " zeroWorld=true");
                return;
            }
            check(!partySeen || kill || group.phase() != PhantomLogicalParty.Phase.BACKGROUND, "PARTY_DRAINED_WITHOUT_USEFUL_ACTION");
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

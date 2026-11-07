/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.*;
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
        check(!Files.exists(runtime.resolve("native130.cut")) && !Files.exists(runtime.resolve("restart130.cut")), "FIXTURE_BEFORE_CUT");
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
        final String jarHash = hash(jar);
        final Path fixture = runtime.resolve("fixture130.properties");
        final Path config = runtime.resolve("config/Custom/PhantomPlayers.ini");
        final String disabled = Files.readString(config);
        check(disabled.contains("EnablePhantomSystem = False"), "MASTER_DISABLED_BEFORE_BOOT");
        if (restart) { check(Files.exists(runtime.resolve("native130.complete")) && read(runtime.resolve("native130.complete")).getProperty("jar").equals(jarHash), "RESTART_SAME_FINAL_JAR"); }
        write(runtime.resolve((restart ? "restart130.started." : "native130.started.") + ProcessHandle.current().pid()), "pid=" + ProcessHandle.current().pid() + "\njar=" + jarHash + "\n");
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
            final var saved = read(fixture);
            final var ids = Arrays.stream(saved.getProperty("profiles").split(",")).map(Long::parseLong).toList();
            final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
            if (!restart)
            {
                final var current = logical.groupFor(ids.getFirst()).orElse(null);
                if (current != null && current.group().expiresAtMillis() <= System.currentTimeMillis())
                {
                    check(ids.stream().allMatch(id -> World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId()) == null), "EXPIRY_ZERO_WORLD");
                    logical.expire(current, System.currentTimeMillis()).orElseThrow();
                }
            }
            if (!restart && logical.groupFor(ids.getFirst()).isEmpty())
            {
                final var old = new PhantomPartyStore(profiles).loadLogicalGroup(ids.getFirst()).orElseThrow().group();
                check(old.phase() == PhantomLogicalParty.Phase.DISBANDED && old.groupId().equals(saved.getProperty("group")), "EXACT_EXPIRED_GENERATION");
                final var members = new ArrayList<PhantomLogicalParty.Member>();
                final var catalog = PhantomPopulationCatalog.load(runtime.resolve("data/phantoms/population/high-five-population-v3.xml"), PhantomPlayersConfig.settings().populationTimeZone());
                final var population = new PhantomPopulationStore(profiles, catalog, PhantomPlayersConfig.settings().populationTimeZone());
                for (var member : old.members())
                {
                    final var state = population.reload(member.profileId()).state();
                    final var root = new PhantomGoalStateStore(profiles).load(member.profileId()).orElseThrow().goal();
                    final var background = new PhantomBackgroundStateCodec().decode(profiles.findComponent(member.profileId(), PhantomBackgroundState.COMPONENT_TYPE).orElseThrow().payload());
                    check(state.state() == PhantomPopulationState.State.READY && World.getInstance().getPlayer(background.identity().characterObjectId()) == null
                        && background.state() == PhantomBackgroundState.State.READY && PhantomPlayersConfig.PartySeekingSettings.defaults().seeks(member.profileId(), background.progress().level())
                        && catalog.evaluate(state.scheduleTemplate(), Instant.now(), PhantomPlayersConfig.settings().populationTimeZone(), state.schedulePhaseMinutes()).state().requiresMaterialization()
                        && PhantomAutonomousFarmParty.ordinaryIntent(member.profileId(), root), "RECREATION_DURABLE_ELIGIBILITY");
                    members.add(new PhantomLogicalParty.Member(member.profileId(), member.role(), root.goalId(), root.revision()));
                }
                logical.agree(new PhantomLogicalParty(old.groupId(), old.generation() + 1, old.leaderProfileId(), members, old.objective(), old.anchorId(), PhantomLogicalParty.Phase.AGREED, 0, System.currentTimeMillis(), "")).orElseThrow();
                System.out.println("NATIVE130_ZERO_WORLD_RECREATION generation=" + (old.generation() + 1) + " sameGroupId=true actualEligibility=true");
            }
            final var before = logical.groupFor(ids.getFirst()).orElseThrow();
            check(before.group().groupId().equals(saved.getProperty("group")) && before.group().members().size() == 3, "DURABLE_GROUP");
            for (long id : ids) { check(World.getInstance().findObject(profiles.find(id).orElseThrow().characterObjectId()) == null, "ZERO_WORLD_BEFORE_ENABLE"); }
            if (restart) { check(Files.readString(runtime.resolve("native130.persisted")).equals(persisted(profiles, ids)), "SAME_DB_EXACT_TOTALS_AND_CLAIMS"); }
            verifyExistingHumanHeal(runtime, jarHash);
            final Path cut = runtime.resolve(restart ? "restart130.cut" : "native130.cut");
            if (!Files.exists(cut)) { write(cut, "zeroWorld=true\ngroup=" + before.group().groupId() + "\ngameplayFixtureWritesAfterCut=0\n"); }
            else { check(read(cut).getProperty("group").equals(before.group().groupId()), "CONTINUATION_SAME_CUT_GROUP"); }
            check(disabled.contains("EnablePhantomSystem = False"), "MASTER_DECLARATION");
            Files.writeString(config, disabled.replace("EnablePhantomSystem = False", "EnablePhantomSystem = True"));
            PhantomPlayersConfig.load();
            System.out.println("NATIVE130_ENABLE=" + PhantomSystem.operatorEnable());
            observe(runtime, profiles, ids, before.group().groupId(), restart);
            System.out.println("NATIVE130_DISABLE=" + PhantomSystem.operatorDisable());
            final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (PhantomSystem.hasConfiguredInstance() && PhantomSystem.configuredShutdownSnapshot().systemState() != PhantomSystem.State.STOPPED && System.nanoTime() < until) { Thread.sleep(200); }
            check(ids.stream().allMatch(id -> World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId()) == null), "ZERO_WORLD_AFTER_STORE");
            final var after = logical.groupFor(ids.getFirst()).orElseThrow();
            check(after.group().groupId().equals(before.group().groupId()) && after.group().phase() == PhantomLogicalParty.Phase.BACKGROUND, "SAME_LOGICAL_BACKGROUND");
            if (!restart) { write(runtime.resolve("native130.persisted"), persisted(profiles, ids)); }
            write(runtime.resolve(restart ? "restart130.complete" : "native130.complete"), "jar=" + jarHash + "\ngroup=" + after.group().groupId() + "\npid=" + ProcessHandle.current().pid() + "\n");
            Files.writeString(config, disabled);
            System.out.println("NATIVE130_PASS restart=" + restart + " sameGroup=true actualPartyKill=true STORE=true");
        }
        catch (Throwable failure)
        {
            System.out.println("NATIVE130_FAILURE type=" + failure.getClass().getName() + " key=" + (failure.getMessage() != null && failure.getMessage().startsWith("NATIVE130_") ? failure.getMessage() : "WITHHELD"));
            for (var frame : failure.getStackTrace()) { if (frame.getClassName().startsWith("org.l2jmobius.")) { System.out.println("NATIVE130_FRAME " + frame); } }
            write(runtime.resolve((restart ? "restart130.failed." : "native130.failed.") + ProcessHandle.current().pid()), failure.getClass().getName() + "\n");
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
        final var existing = population.loadManagedAfter(0, 4);
        check(existing.size() <= 3 && !Files.exists(runtime.resolve("native130.cut")), "BOUNDED_FIXTURE_BEFORE_CUT");
        final var mobs = World.getInstance().getVisibleObjects().stream().filter(Monster.class::isInstance).map(Monster.class::cast)
            .filter(mob -> mob.isSpawned() && !mob.isRaid() && !mob.isDead() && !mob.isAggressive() && mob.getInstanceId() == 0 && mob.getLevel() >= 70 && mob.getLevel() <= 78 && mob.getMaxHp() >= 5000 && mob.getMaxHp() <= 60000).toList();
        final var fields = source.topology().snapshot().anchors().stream().filter(anchor -> anchor.role() == PhantomTopologyAnchorRole.FARMING)
            .map(anchor -> Map.entry(anchor, L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, 0))).filter(entry -> entry.getValue().isPresent())
            .filter(entry -> { final var point = entry.getValue().orElseThrow(); return GeoEngine.getInstance().hasGeo(point.x(), point.y()) && mobs.stream().anyMatch(mob -> Math.hypot((long) point.x() - mob.getX(), (long) point.y() - mob.getY()) < 500 && Math.abs((long) point.z() - mob.getZ()) < 150 && GeoEngine.getInstance().canMoveToTarget(point.x(), point.y(), point.z(), mob.getX(), mob.getY(), mob.getZ(), 0)); })
            .sorted(Comparator.comparing(entry -> entry.getKey().id())).limit(32).toList();
        check(!fields.isEmpty(), "CERTIFIED_FIELD");
        var field = fields.getFirst();
        final var planner = new PhantomHistoricalBackgroundPlanner(source.knowledge(), source.topology(), source.authority());
        final var goals = new PhantomGoalStateStore(profiles);
        final var ids = new ArrayList<Long>(); final var members = new ArrayList<PhantomLogicalParty.Member>();
        PhantomDomainRef objective = null;
        for (int ordinal = 0; ordinal < 3; ordinal++)
        {
            final int birth = ordinal == 2 ? 10 : 0;
            long next; final long selected; PhantomPopulationStore.ManagedSnapshot managed;
            if (ordinal < existing.size())
            {
                managed = existing.get(ordinal); next = managed.profile().profileId(); selected = managed.state().deterministicSeed();
                check(managed.state().creationOrdinal() == ordinal + 1 && managed.state().classId() == birth && managed.state().catalogHash().equals(catalog.hash()), "OWN_PARTIAL_FIXTURE_IDENTITY");
            }
            else
            {
                next = nextProfile();
                while (!PhantomPlayersConfig.PartySeekingSettings.defaults().seeks(next, 76)) { profiles.create(null); next = nextProfile(); }
                selected = birthSeed(catalog, next, ordinal + 1, birth, zone, Instant.now());
                managed = population.createShell(1, ordinal + 1, selected); check(managed.profile().profileId() == next, "EXCLUSIVE_CREATION_SEQUENCE");
            }
            for (int step = 0; step < 12 && managed.state().state() != PhantomPopulationState.State.READY; step++) { managed = population.advanceCreation(managed).snapshot(); }
            System.out.println("NATIVE130_CREATION profile=" + next + " state=" + managed.state().state() + " failure=" + managed.state().lastFailure());
            check(managed.state().state() == PhantomPopulationState.State.READY, "PRODUCTION_CREATION_SAGA");
            final long id = next; ids.add(id);
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
                for (var candidate : ordinal == 0 ? fields : List.of(field))
                {
                    final var position = candidate.getValue().orElseThrow(); actor.setXYZInvisible(position.x(), position.y(), position.z());
                    plan = ordinal == 2 ? planner.idleInitial(id, actor, selected, 1) : planner.planInitial(id, actor, selected, 1);
                    // Existing HistoricalBackgroundService/TEST094 fallback: a guardian/support
                    // may wait at a canonical anchor without claiming solo farm viability.
                    if (ordinal > 0 && !plan.ready() && "planner.target_or_route.absent".equals(plan.reasonKey()))
                    { plan = planner.idleInitial(id, actor, selected, 1); }
                    System.out.println("NATIVE130_PLAN profile=" + id + " anchor=" + candidate.getKey().id() + " ready=" + plan.ready() + " reason=" + plan.reasonKey() + " rejected=" + plan.candidateRejections().stream().limit(3).toList());
                    if (plan.ready()) { if (ordinal == 0) { field = candidate; } break; }
                }
                check(plan.ready(), "ORDINARY_PREP_PLAN");
                if (goals.load(id).isEmpty()) { goals.insert(id, plan.goal()); }
                else { check(goals.load(id).orElseThrow().goal().equals(plan.goal()), "PARTIAL_FIXTURE_ROOT_EXACT"); }
                if (ordinal == 0) { objective = plan.goal().validSources().getFirst(); }
                members.add(new PhantomLogicalParty.Member(id, ordinal == 2 ? "support" : plan.goal().goalType().equals(PhantomBackgroundGoalSpec.HISTORICAL_IDLE_GOAL_TYPE) ? "guardian" : "damage", plan.goal().goalId(), plan.goal().revision()));
                actor.setCurrentHp(actor.getMaxHp() * (ordinal == 2 ? 1 : 0.4));
                actor.getInventory().updateDatabase(); actor.storeMe();
                System.out.println("NATIVE130_PREFLIGHT profile=" + id + " actor=" + actor.getObjectId() + " class=" + actor.getActiveClass() + " level=" + actor.getLevel() + " hp=" + actor.getCurrentHp() + " mp=" + actor.getCurrentMp() + " heal=" + actor.getKnownSkill(1011) + " root=" + plan.goal() + " pos=" + actor.getLocation());
            }
            finally { actor.stopAllTasks(); actor.storeMe(); actor.setOnlineStatus(true, false); actor.deleteMe(); }
        }
        check(objective != null && objective.namespace().equals("background.farm"), "REAL_FARM_SOURCE");
        final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
        final var group = new PhantomLogicalParty(org.l2jmobius.gameserver.phantoms.party.model.PhantomPartyModel.sha256("NATIVE130:" + ids), 1, ids.getFirst(), members, objective, objective.key().substring(objective.key().indexOf('@') + 1), PhantomLogicalParty.Phase.AGREED, 0, System.currentTimeMillis(), "");
        check(logical.agree(group).isPresent(), "ZERO_PLAYER_AGREEMENT");
        ordinaryHeal(runtime);
        write(runtime.resolve("fixture130.properties"), "profiles=" + String.join(",", ids.stream().map(String::valueOf).toList()) + "\ngroup=" + group.groupId() + "\nanchor=" + group.anchorId() + "\n");
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
    private static void observe(Path runtime, PhantomProfileRepository profiles, List<Long> ids, String groupId, boolean restart) throws Exception
    {
        final var trace = runtime.resolve((restart ? "restart130.trace." : "native130.trace.") + ProcessHandle.current().pid() + ".tsv");
        write(trace, "millis\tgroup\tcensus\tactors\n");
        final var logical = new PhantomLogicalPartyCoordinator(new PhantomPartyStore(profiles));
        final Map<org.l2jmobius.gameserver.model.actor.PlayerNativeEvidence.Target, TargetSeen> targets = new LinkedHashMap<>();
        final Map<Integer, Double> priorHp = new HashMap<>(), priorMp = new HashMap<>();
        boolean partySeen = false, kill = false, heal = false, healMpDebit = false;
        LocalPlaySyntheticHumanSession witness = null; boolean witnessWithdrawn = false;
        int stableBlockSamples = 0;
        long nextTrace = 0; final long start = System.currentTimeMillis(), deadline = start + (restart ? 540_000 : 300_000);
        try
        {
        while (System.currentTimeMillis() < deadline)
        {
            final var group = logical.groupFor(ids.getFirst()).orElseThrow().group();
            check(group.groupId().equals(groupId), "GROUP_ID_PRESERVED");
            final var actors = ids.stream().map(id -> World.getInstance().getPlayer(profiles.find(id).orElseThrow().characterObjectId())).filter(Objects::nonNull).toList();
            final var party = actors.stream().map(Player::getParty).filter(Objects::nonNull).filter(value -> value.getMemberCount() >= 2 && value.getMembers().stream().allMatch(actor -> ids.stream().anyMatch(id -> profiles.find(id).orElseThrow().characterObjectId() == actor.getObjectId()))).findFirst().orElse(null);
            if (party != null)
            {
                partySeen = true;
                if (witness == null && !witnessWithdrawn)
                {
                    final var leader = party.getLeader(); org.l2jmobius.gameserver.model.Location site = null;
                    for (int[] offset : new int[][] {{600,0},{-600,0},{0,600},{0,-600},{600,600},{-600,-600}})
                    {
                        final int x = leader.getX() + offset[0], y = leader.getY() + offset[1];
                        final var geo = GeoEngine.getInstance(); if (!geo.hasGeo(x,y)) { continue; }
                        final int z = geo.getHeight(x,y,leader.getZ());
                        if (Math.abs((long) z - leader.getZ()) > 150 || World.getInstance().getVisibleObjectsInRange(leader, Monster.class, 1200).stream().anyMatch(mob -> !mob.isDead() && Math.hypot((long) x - mob.getX(), (long) y - mob.getY()) < 300)) { continue; }
                        site = new org.l2jmobius.gameserver.model.Location(x,y,z); break;
                    }
                    check(site != null, "ORDINARY_WITNESS_SAFE_SITE");
                    witness = new LocalPlaySyntheticHumanSession(existingHuman(runtime), "HumanHeal130"); witness.start(site);
                    System.out.println("NATIVE130_ORDINARY_WITNESS_PRESENT phantomWrites=0 groupRights=unchanged");
                }
                check(party.getLeader() != null && party.getMembers().stream().allMatch(actor -> actor.getParty() == party), "OFFICIAL_PARTY");
                for (var actor : party.getMembers())
                {
                    if (actor.getTarget() instanceof Monster mob && !mob.isDead() && targets.size() < 32)
                    {
                        targets.computeIfAbsent(mob.getNativeEvidenceTarget(), _ ->
                        {
                            final Map<Integer, Long> rewards = new HashMap<>();
                            for (var peer : party.getMembers()) { if (peer.getNativeWorkOwner() != null && peer.getNativeWorkOwner().evidence() != null) { rewards.put(peer.getObjectId(), peer.getNativeWorkOwner().evidence().targetReceipts().rewardSequence()); } }
                            return new TargetSeen(mob, Map.copyOf(rewards));
                        });
                    }
                    final var last = actor.getLastSkillCast();
                    if (last != null && "combat.heal".equals(org.l2jmobius.gameserver.phantoms.combat.PhantomSupportEffectAuthority.nativeCapability(last)))
                    {
                        final var beforeMp = priorMp.get(actor.getObjectId());
                        healMpDebit |= beforeMp != null && actor.getCurrentMp() < beforeMp - 1;
                        for (var recipient : party.getMembers())
                        {
                            final var beforeHp = priorHp.get(recipient.getObjectId());
                            if (recipient != actor && beforeHp != null && recipient.getCurrentHp() > beforeHp + 30 && actor.getTarget() == recipient)
                            { heal = true; System.out.println("NATIVE130_PHANTOM_HEAL caster=" + actor.getObjectId() + " target=" + recipient.getObjectId() + " hpBefore=" + beforeHp + " hpAfter=" + recipient.getCurrentHp() + " mp=" + actor.getCurrentMp()); }
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
            for (var actor : actors) { priorHp.put(actor.getObjectId(), actor.getCurrentHp()); priorMp.put(actor.getObjectId(), actor.getCurrentMp()); }
            if (witness != null && kill && (restart || heal && healMpDebit))
            {
                witness.close(); witness = null; witnessWithdrawn = true;
                System.out.println("NATIVE130_ORDINARY_WITNESS_WITHDRAWN normalLogout=true phantomWrites=0");
            }
            if (System.currentTimeMillis() >= nextTrace)
            {
                boolean blocked = actors.isEmpty();
                final String facts = actors.stream().map(actor -> actor.getObjectId() + ":hp=" + actor.getCurrentHp() + ":mp=" + actor.getCurrentMp() + ":target=" + (actor.getTarget() == null ? 0 : actor.getTarget().getObjectId()) + ":cast=" + (actor.getLastSkillCast() == null ? 0 : actor.getLastSkillCast().getId()) + ":party=" + (actor.getParty() == null ? 0 : actor.getParty().getLeaderObjectId()) + ":evidence=" + (actor.getNativeWorkOwner() == null ? "NONE" : actor.getNativeWorkOwner().evidence().snapshot())).toList().toString();
                Files.writeString(trace, (System.currentTimeMillis() - start) + "\t" + group.phase() + ":" + group.waitReason() + ":" + group.nextDueMillis() + "\t" + PhantomSystem.operatorCoordinationCensus() + "\t" + facts + "\n", StandardOpenOption.APPEND);
                System.out.println("NATIVE130_OBSERVE millis=" + (System.currentTimeMillis() - start) + " phase=" + group.phase() + " actors=" + actors.size() + " party=" + partySeen + " kill=" + kill + " heal=" + heal + " mpDebit=" + healMpDebit);
                for (long id : ids)
                {
                    final var progress = PhantomSystem.operatorReadinessProgress(id); final var life = PhantomSystem.operatorLifeProgress(id);
                    final var admission = PhantomSystem.operatorAdmissionProfile(id).orElse(null);
                    final String reason = admission == null || admission.readiness() == null ? "" : admission.readiness().reason();
                    blocked &= Set.of("catchup.baseline.initial_binding_changed", "catchup.goal.conflict", "catchup.baseline.conflict", "ecology.orphan_historical_conflict").contains(reason);
                    System.out.println("NATIVE130_WAIT profile=" + id + " admission=" + (admission == null ? null : admission.readiness())
                        + " stage=" + progress.get("currentStage") + " retry=" + progress.get("nextRetryMillis") + " visible=" + life.get("historical.visibleRecovery")
                        + " rendezvous=" + life.get("party.rendezvous") + " travel=" + life.get("farm.travel.reason") + " vitals=" + life.get("native.vitals") + " owner=" + life.get("native.owner"));
                }
                stableBlockSamples = blocked ? stableBlockSamples + 1 : 0;
                check(stableBlockSamples < 3, "STABLE_READINESS_BLOCK");
                nextTrace = System.currentTimeMillis() + 10_000;
            }
            if (kill && (restart || heal && healMpDebit) && actors.isEmpty() && group.phase() == PhantomLogicalParty.Phase.BACKGROUND)
            {
                System.out.println("NATIVE130_USEFUL_GROUP_PASS restart=" + restart + " officialParty=" + partySeen + " realGroupKill=" + kill + " nativeHeal=" + heal + " supportMpDebit=" + healMpDebit + " zeroWorld=true");
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("NATIVE130_FINITE_EVIDENCE_DEADLINE");
        }
        finally { if (witness != null) { witness.close(); } }
    }
    private static String persisted(PhantomProfileRepository profiles, List<Long> ids) throws Exception
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

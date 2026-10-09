/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState;
import org.l2jmobius.gameserver.phantoms.background.*;
import org.l2jmobius.gameserver.phantoms.decision.*;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;
import org.l2jmobius.gameserver.phantoms.profile.PhantomProfileRepository;

/** Actual native fixture and production authority; no replacement reward model. */
public final class PhantomFarmProjection030Suite implements PhantomTestSuite
{
    private final PhantomBackgroundSuite base = new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.NATIVE_LIFECYCLE);
    public static void main(String[] args)
    {
        var context = new PhantomTestContext(15001501, Path.of(args[0]), Path.of(args[1]));
        System.exit(PhantomTestLauncher.runSuite("farm-projection030", new PhantomFarmProjection030Suite(), context));
    }
    @Override public String id() { return "farm-projection030"; }
    @Override public void beforeAll(PhantomTestContext context) throws Exception { base.beforeAll(context); }
    @Override public void afterAll(PhantomTestContext context) throws Exception { base.afterAll(context); }
    @Override public void register(PhantomTestRegistry registry)
    {
        registry.add("P02-P03-P07-production-plan-switch-before-rng-magic", c -> projection(c, true));
        registry.add("P02-P12-production-plan-switch-before-rng-melee", c -> projection(c, false));
        registry.add("P04-P05-P08-P09-exact-projection-negatives-and-lost-replies", c -> { projection(c, true, 0); projection(c, true, 1); });
        registry.add("P12-actual-historical-projection-and-catchup-cas", c -> projection(c, false, -1, true));
    }
    private void projection(PhantomTestContext context, boolean mage) throws Exception { projection(context, mage, -1); }
    private void projection(PhantomTestContext context, boolean mage, int lostReply) throws Exception { projection(context, mage, lostReply, false); }
    private void projection(PhantomTestContext context, boolean mage, int lostReply, boolean historical) throws Exception
    {
        var environment = (PhantomHeadlessPlayerTestEnvironment) field(base, "_environment");
        var reset = Player.load(environment.primary().objectId());
        try { reset.stopAllTasks(); reset.getStat().setVitalityPoints(1, true); }
        finally { environment.cleanupLoadedPlayer(reset); }
        var armed = new java.util.concurrent.atomic.AtomicBoolean();
        var lose = new java.util.concurrent.atomic.AtomicBoolean();
        var faultTransaction = new PhantomBackgroundTransaction(() ->
        {
            var delegate = org.l2jmobius.commons.database.DatabaseFactory.getConnection();
            return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(java.sql.Connection.class.getClassLoader(), new Class<?>[] { java.sql.Connection.class }, (proxy, method, arguments) ->
            {
                if (method.getName().equals("commit") && lose.compareAndSet(true, false))
                { if (lostReply == 1) { delegate.commit(); } else { delegate.rollback(); } throw new java.sql.SQLException("CONTROLLED_METADATA_REPLY_LOST_030"); }
                try { return method.invoke(delegate, arguments); }
                catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
            });
        }, PhantomBackgroundTransaction.ObjectIdAllocator.production(), point ->
        { if (armed.get() && point == PhantomBackgroundTransaction.FaultPoint.AFTER_BACKGROUND_STATE_WRITE) { lose.set(true); } });
        var open = PhantomBackgroundSuite.class.getDeclaredMethod("openNativeProductionFixture", PhantomTestContext.class, boolean.class, PhantomBackgroundTransaction.class);
        open.setAccessible(true);
        try (var fixture = (AutoCloseable) open.invoke(base, context, mage, faultTransaction))
        {
            long id = (long) invoke(fixture, "id");
            var player = (Player) invoke(fixture, "player");
            var materialization = (PhantomMaterializationService) field(fixture, "materialization");
            var background = (PhantomBackgroundService) field(fixture, "background");
            var transaction = (PhantomBackgroundTransaction) field(fixture, "transaction");
            var production = (PhantomBackgroundSuite.ProductionAuthorityFixture) field(base, "_production");
            var goals = new PhantomGoalStateStore((PhantomProfileRepository) field(base, "_repository"));
            var original = goals.load(id).orElseThrow();
            PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(id).status(), "Actual native release must succeed before plan switch.");
            var before = transaction.load(id).state();
            var policy = transaction.nativeContext(id, player.getObjectId()).context().afterPolicy();
            var pair = select(production, before, original.goal(), policy);
            context.record("P02.selected", "class=" + before.identity().activeClassId() + ";old=" + PhantomBackgroundGoalSpec.parse(original.goal()).npcId() + ";new=" + pair.input.target().npcId() + ";newIds=" + pair.newIds + ";controlledRoll=" + pair.rolled);
            goals.replace(id, original.rowVersion(), pair.goal);
            for (int turn = 0; !before.position().committedAnchorId().equals(pair.goal.selectedAnchor().key()) && turn < 16; turn++)
            {
                var travel = background.travel(id, pair.goal, 300, 10 + turn, PhantomActivityState.BACKGROUND, System.nanoTime());
                context.record("P02.productionTravel." + turn, travel.toString());
                PhantomAssertions.assertTrue(travel.successful(), "Plan change must reach the new farm through the actual production travel producer.");
                before = transaction.load(id).state();
            }
            PhantomAssertions.assertEquals(pair.goal.selectedAnchor().key(), before.position().committedAnchorId(), "Bounded production travel must arrive before farm.");
            policy = transaction.nativeContext(id, player.getObjectId()).context().afterPolicy();
            String canonicalBefore = canonical(player.getObjectId());
            PhantomBackgroundCatchupStore.Snapshot historicalBefore = null;
            var catchups = new PhantomBackgroundCatchupStore((PhantomProfileRepository) field(base, "_repository"), goals);
            if (historical)
            {
                var cursor = new PhantomBackgroundCatchupState(PhantomBackgroundCatchupState.Status.RUNNING, "d".repeat(64), context.seed(), 29843626L, 29843628L, 29843626L, 0, 0, 301, 1, production.authority().topologyGeneration(), pair.goal.goalId(), pair.goal.revision(), "a".repeat(64), PhantomBackgroundState.MODEL_VERSION, production.authority().hashes(), "");
                historicalBefore = catchups.claim(id, cursor);
            }
            armed.set(lostReply >= 0);
            var attempt = historical ? background.advanceHistorical(id, pair.goal, historicalBefore, historicalBefore.state().advanceTo(historicalBefore.state().cursorEpochMinute() + 1)) : background.farm(id, pair.goal, 300, 100, PhantomActivityState.BACKGROUND, System.nanoTime());
            armed.set(false);
            if (lostReply == 0)
            {
                PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.RETRY, attempt.status(), "Uncommitted metadata reply is a bounded retry.");
                PhantomAssertions.assertEquals(before, transaction.load(id).state(), "Uncommitted metadata reply preserves exact prior state.");
                PhantomAssertions.assertEquals(canonicalBefore, canonical(player.getObjectId()), "Lost metadata reply grants nothing.");
                attempt = background.farm(id, pair.goal, 300, 100, PhantomActivityState.BACKGROUND, System.nanoTime());
            }
            context.record("P02.actualProducer", attempt.toString());
            PhantomAssertions.assertEquals(PhantomBackgroundService.OperationStatus.RETRY, attempt.status(), "New plan must refresh the complete lawful projection before RNG, including a roll without the rare item.");
            PhantomAssertions.assertEquals("farm.inventory_projection_refreshed", attempt.reason(), "Projection refresh is a typed retry, not goal failure.");
            var after = transaction.load(id).state();
            PhantomAssertions.assertEquals(canonicalBefore, canonical(player.getObjectId()), "Projection-only commit changes no canonical characters/items/skills.");
            PhantomAssertions.assertEquals(before.clock(), after.clock(), "Metadata switch must not advance RNG or residual time.");
            PhantomAssertions.assertEquals(before.receipt(), after.receipt(), "Metadata switch issues no reward receipt.");
            PhantomAssertions.assertEquals(before.progress(), after.progress(), "Metadata switch grants no XP/SP.");
            PhantomAssertions.assertEquals(before.position(), after.position(), "Metadata switch moves no actor.");
            PhantomAssertions.assertEquals(before.vitals(), after.vitals(), "Metadata switch preserves vitals.");
            PhantomAssertions.assertEquals(before.inventory().canonicalHash(), after.inventory().canonicalHash(), "Full inventory hash is unchanged.");
            PhantomAssertions.assertTrue(after.inventory().mutableItemIds().containsAll(pair.newIds), "All legal new drop IDs are authorized before the roll.");
            var contextAfter = transaction.nativeContext(id, player.getObjectId());
            PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.SUCCESS, contextAfter.status(), "PNC remains bound to the committed projection.");
            PhantomAssertions.assertEquals(policy, contextAfter.context().afterPolicy(), "Projection preserves native policy and vitality.");
            if (historical)
            {
                PhantomAssertions.assertEquals(historicalBefore, catchups.load(id).orElseThrow(), "Projection-only historical preflight must not advance or replace catchup cursor.");
                var mutation = new PhantomBackgroundTransaction.CatchupMutation(historicalBefore.state(), historicalBefore.rowVersion() + 1, historicalBefore.state().advanceTo(historicalBefore.state().cursorEpochMinute() + 1));
                var skills = after.loadout().selectedSkillId() == 0 ? Map.<Integer, Integer>of() : Map.of(after.loadout().selectedSkillId(), after.loadout().selectedSkillLevel());
                var proof = production.authority().ordinaryInventoryProjection(after, pair.goal, production.authority().tryFarmInput(after, PhantomBackgroundGoalSpec.parse(pair.goal), skills).input(), skills);
                var lease = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().tryAcquire(after.identity().characterObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND);
                try (lease)
                {
                    var rejected = transaction.refreshOrdinaryInventoryProjection(after, pair.goal, mutation, contextAfter.context(), proof, lease);
                    PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.CATCHUP_CONFLICT, rejected.status(), "Stale actual catchup CAS cannot overwrite projection.");
                    PhantomAssertions.assertEquals(after, transaction.load(id).state(), "Rejected catchup CAS preserves state.");
                    PhantomAssertions.assertEquals(historicalBefore, catchups.load(id).orElseThrow(), "Rejected catchup CAS preserves cursor.");
                    context.record("P08.historicalCAS", rejected.status().name());
                }
            }
            negatives(context, transaction, production.authority(), after, pair.goal, contextAfter.context());
            var batch = historical ? background.advanceHistorical(id, pair.goal, historicalBefore, historicalBefore.state().advanceTo(historicalBefore.state().cursorEpochMinute() + 1)) : background.farm(id, pair.goal, 300, 101, PhantomActivityState.BACKGROUND, System.nanoTime());
            context.record("P02.followingBatch", batch.toString());
            PhantomAssertions.assertTrue(batch.successful() && batch.encounters() > 0, "Reloaded bounded turn must execute the genuine ordinary FARM producer.");
            if (historical) { PhantomAssertions.assertEquals(historicalBefore.state().cursorEpochMinute() + 1, catchups.load(id).orElseThrow().state().cursorEpochMinute(), "Only the genuine historical batch advances its exact cursor."); }
            var finalState = transaction.load(id).state();
            PhantomAssertions.assertTrue(finalState.progress().experience() > before.progress().experience(), "Native-policy reward math produces actual XP.");
            var returnResult = materialization.materialize(id);
            PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, returnResult.status(), "New-plan inventory must rematerialize through the actual native load/attestation.");
            var returnedPlayer = org.l2jmobius.gameserver.model.World.getInstance().getPlayer(player.getObjectId());
            for (int item : pair.newIds) { PhantomAssertions.assertEquals(finalState.inventory().itemCount(item), returnedPlayer.getInventory().getInventoryItemCount(item, -1), "Native return preserves new-plan item count."); }
            PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(id).status(), "New-plan native return must store/release again.");
            final var inventoryBeforeRoll = before.inventory();
            if (!pair.rolled.isEmpty()) { PhantomAssertions.assertTrue(pair.rolled.stream().anyMatch(item -> finalState.inventory().itemCount(item) > inventoryBeforeRoll.itemCount(item)), "Controlled new-plan roll must persist a formerly nonmutable item."); }
        }
    }
    private static void negatives(PhantomTestContext context, PhantomBackgroundTransaction transaction, PhantomBackgroundAuthority authority, PhantomBackgroundState state, PhantomGoal goal, PhantomNativeContext nativeContext)
    {
        var ids = java.util.stream.Stream.concat(authority.ordinarySpoilSkillIds(state.identity().activeClassId()).stream(), java.util.stream.Stream.of(state.loadout().selectedSkillId())).filter(id -> id > 0).distinct().sorted().toList();
        var skills = ids.isEmpty() ? Map.<Integer, Integer>of() : transaction.readAcquisitionEligibility(state.identity().profileId(), state.identity().characterObjectId(), state.identity().classIndex(), state.identity().activeClassId(), ids, state.hashes().progression(), authority.hashes()).snapshot().skillLevels();
        var input = authority.tryFarmInput(state, PhantomBackgroundGoalSpec.parse(goal), skills).input();
        var proof = authority.ordinaryInventoryProjection(state, goal, input, skills);
        var lease = org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.getInstance().tryAcquire(state.identity().characterObjectId(), org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry.OwnerKind.BACKGROUND);
        PhantomAssertions.assertTrue(lease != null, "Actual exclusive BACKGROUND identity required.");
        try (lease)
        {
            var noop = transaction.refreshOrdinaryInventoryProjection(state, goal, null, nativeContext, proof, lease);
            PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.IDEMPOTENT, noop.status(), "Repeated exact projection is no-op.");
            PhantomAssertions.assertEquals(nativeContext.stateRowVersion(), transaction.nativeContext(state.identity().profileId(), state.identity().characterObjectId()).context().stateRowVersion(), "No-op does not bump PNC/state version.");
            var foreignIds = new java.util.TreeSet<>(proof.requiredItemIds()); foreignIds.add(999123);
            var forged = new PhantomBackgroundAuthority.OrdinaryInventoryProjection(authority, proof.identity(), goal, proof.hashes(), proof.topologyGeneration(), proof.loadout(), proof.learnedSkills(), proof.inventoryHash(), List.copyOf(foreignIds));
            var rejected = transaction.refreshOrdinaryInventoryProjection(state, goal, null, nativeContext, forged, lease);
            PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.HASH_STALE, rejected.status(), "Foreign Y cannot authorize itself outside the full actual producer catalog.");
            PhantomAssertions.assertEquals(state, transaction.load(state.identity().profileId()).state(), "Rejected foreign projection changes nothing.");
            context.record("P04.P08.negatives", "foreign=" + rejected.status() + ";noop=" + noop.status());
        }
        var closed = transaction.refreshOrdinaryInventoryProjection(state, goal, null, nativeContext, proof, lease);
        PhantomAssertions.assertEquals(PhantomBackgroundTransaction.Status.STATE_CONFLICT, closed.status(), "Closed/foreign ownership cannot switch projection.");
        PhantomAssertions.assertEquals(state, transaction.load(state.identity().profileId()).state(), "Failed ownership changes nothing.");
    }
    private static Pair select(PhantomBackgroundSuite.ProductionAuthorityFixture production, PhantomBackgroundState state, PhantomGoal old, PhantomBackgroundSimulationPolicy policy)
    {
        var snapshot = production.knowledge().snapshot();
        var anchors = production.topology().snapshot().anchors().stream()
            .filter(a -> a.role() == org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchorRole.FARMING && a.point().instanceId() == 0)
            .filter(a -> distance(a, state) <= 20_000L * 20_000L)
            .sorted(java.util.Comparator.comparingLong(a -> distance(a, state))).limit(64).toList();
        Pair fallback = null;
        var diagnostic = new StringBuilder("origin=" + state.position().committedAnchorId() + ";oldIds=" + state.inventory().mutableItemIds() + ";nearAnchors=" + anchors.size());
        for (var anchor : anchors)
        {
            var position = L2jPhantomBackgroundAuthority.canonicalCommittedAnchorPosition(anchor, state.position().heading());
            if (position.isEmpty()) { continue; }
            var draft = new PhantomBackgroundState(state.state(), state.identity(), state.progress(), state.vitals(), position.orElseThrow(), state.combat(), state.loadout(), state.inventory(), state.autoGetSkills(), state.clock(), state.receipt(), state.hashes());
            var candidates = snapshot.npcById().values().stream().filter(n -> n.level() >= 1 && n.level() <= 12)
                .filter(n -> snapshot.spawnAreasByNpc().getOrDefault(n.npcId(), List.of()).stream().anyMatch(a -> a.instanceId() == 0 && a.totalConfiguredAmount() > 0 && anchor.nodeId().equals(a.topologyNodeId())))
                .sorted((left, right) -> { int order = Integer.compare(left.level(), right.level()); return order != 0 ? order : Integer.compare(left.npcId(), right.npcId()); }).toList();
            for (var npc : candidates)
            {
                var goal = new PhantomGoal(old.goalId(), old.goalType(), old.status(), old.subject(), new PhantomDomainRef("npc", Integer.toString(npc.npcId())), old.requiredAmount(), old.currentAmount(), old.acquisitionMethod(), List.of(new PhantomDomainRef(PhantomBackgroundGoalSpec.SOURCE_NAMESPACE, npc.npcId() + "@" + anchor.id())), new PhantomDomainRef(PhantomBackgroundGoalSpec.ANCHOR_NAMESPACE, anchor.id()), old.purposeKey(), old.priority(), old.riskBudget(), old.expenseBudget(), old.deadlineEpochMillis(), old.constraints(), old.reasonKey(), old.revision() + 1);
                var skills = state.loadout().selectedSkillId() == 0 ? Map.<Integer, Integer>of() : Map.of(state.loadout().selectedSkillId(), state.loadout().selectedSkillLevel());
                var inputAttempt = production.authority().tryFarmInput(draft, PhantomBackgroundGoalSpec.parse(goal), skills);
                if (!inputAttempt.successful()) { continue; }
                var input = inputAttempt.input();
                var newIds = input.target().drops().stream().filter(d -> d.disposition() == PhantomBackgroundModel.DropDisposition.ACQUIRE).map(PhantomBackgroundModel.Drop::itemId).distinct().sorted().filter(id -> !state.inventory().mutableItemIds().contains(id)).toList();
                if (newIds.isEmpty()) { continue; }
                var travel = production.authority().advanceTravel(state, PhantomBackgroundGoalSpec.parse(goal), 60_000, 0, policy);
                if (!travel.mutated()) { diagnostic.append(';').append(anchor.id()).append(":travel=").append(travel.status()); continue; }
                var batch = new PhantomBackgroundModel().evaluate(new PhantomBackgroundModel.BatchRequest(draft, input.target(), input.rewardPolicy(), input.deathPolicy(), input.experienceTable(), input.levelForExperience(), false), policy);
                if (!batch.mutated() || batch.dead()) { diagnostic.append(';').append(npc.npcId()).append(":model=").append(batch.reason()).append(":dead=").append(batch.dead()); continue; }
                var rolled = newIds.stream().filter(id -> batch.inventoryDelta().itemDeltas().getOrDefault(id, 0L) > 0).toList();
                var pair = new Pair(goal, input, newIds, rolled);
                if (!rolled.isEmpty()) { return pair; }
                if (fallback == null) { fallback = pair; }
            }
        }
        if (fallback == null) { throw new AssertionError("No second factual legal catalog with production travel: " + diagnostic); }
        return fallback;
    }
    private static long distance(org.l2jmobius.gameserver.phantoms.topology.PhantomTopologyAnchor anchor, PhantomBackgroundState state)
    {
        long x = (long) anchor.point().x() - state.position().x(), y = (long) anchor.point().y() - state.position().y();
        return x * x + y * y;
    }
    private static String canonical(int objectId) throws Exception
    {
        var text = new StringBuilder();
        try (var connection = org.l2jmobius.commons.database.DatabaseFactory.getConnection())
        {
            for (String query : List.of("SELECT * FROM characters WHERE charId=?", "SELECT * FROM items WHERE owner_id=? ORDER BY object_id", "SELECT * FROM character_skills WHERE charId=? ORDER BY class_index,skill_id", "SELECT * FROM character_subclasses WHERE charId=? ORDER BY class_index"))
            {
                try (var statement = connection.prepareStatement(query))
                {
                    statement.setInt(1, objectId);
                    try (var rows = statement.executeQuery())
                    {
                        while (rows.next()) { for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) { text.append(rows.getString(i)).append('|'); } text.append('\n'); }
                    }
                }
            }
        }
        return text.toString();
    }
    private record Pair(PhantomGoal goal, PhantomBackgroundAuthority.FarmInput input, List<Integer> newIds, List<Integer> rolled) { }
    private static Object field(Object object, String name) throws Exception
    {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object invoke(Object object, String name) throws Exception
    {
        var method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
    }
}

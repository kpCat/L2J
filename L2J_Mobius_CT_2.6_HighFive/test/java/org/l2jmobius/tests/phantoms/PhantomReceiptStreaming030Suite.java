/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.player.PhantomMaterializationService;

/** Prospective events from real native lifetimes on the existing owned fixture. */
public final class PhantomReceiptStreaming030Suite implements PhantomTestSuite
{
    private final PhantomBackgroundSuite base = new PhantomBackgroundSuite(PhantomBackgroundSuite.Mode.NATIVE_LIFECYCLE);
    private final String faultMode = System.getProperty("phantom.m1.native.focus", "");
    public static void main(String[] args)
    {
        var context = new PhantomTestContext(15001501, Path.of(args[0]), Path.of(args[1]));
        System.exit(PhantomTestLauncher.runSuite("receipt-streaming030", new PhantomReceiptStreaming030Suite(), context));
    }
    @Override public String id() { return "receipt-streaming030"; }
    @Override public void beforeAll(PhantomTestContext context) throws Exception { if (faultMode.endsWith("030")) { System.setProperty("phantom.m1.native.focus", "all"); } base.beforeAll(context); }
    @Override public void afterAll(PhantomTestContext context) throws Exception { base.afterAll(context); }
    @Override public void register(PhantomTestRegistry registry)
    {
        if (faultMode.endsWith("030")) { registry.add("E06-native-store-with-sticky-proof-failure", this::fault); }
        else { registry.add("E01-E08-E09-native-sequential-births-and-terminal-export", this::sequential); }
    }
    private void sequential(PhantomTestContext context) throws Exception
    {
        var environment = (PhantomHeadlessPlayerTestEnvironment) field(base, "_environment");
        var reset = Player.load(environment.primary().objectId());
        try { reset.stopAllTasks(); reset.getStat().setVitalityPoints(1, true); }
        finally { environment.cleanupLoadedPlayer(reset); }
        var open = PhantomBackgroundSuite.class.getDeclaredMethod("openNativeProductionFixture", PhantomTestContext.class, boolean.class);
        open.setAccessible(true);
        var output = context.moduleRoot().resolve("docs/phantoms/tasks/PHANTOM-M1-PLAN-INVENTORY-CONTINUITY-030/evidence/NATIVE_STREAM_" + System.nanoTime());
        attach(context, output);
        int released = 0, highwater = 0;
        for (int i = 0; i < 130; i++)
        {
            if (i == 30) { attach(context, output.resolve("rotated-telemetry")); }
            try (var fixture = openActual(open, base, context))
            {
                long id = (long) invoke(fixture, "id");
                var materialization = (PhantomMaterializationService) field(fixture, "materialization");
                var outcome = materialization.dematerialize(id);
                PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, outcome.status(), "Passive collector failure must not change actual native store/release.");
                released++; highwater = Math.max(highwater, owners().size());
                if (i == 0)
                {
                    call("sampleTerminals"); call("drain");
                    var background = (org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundService) field(fixture, "background");
                    var seed = field(fixture, "seed"); var goal = (org.l2jmobius.gameserver.phantoms.decision.PhantomGoal) invoke(seed, "goal");
                    var batch = background.farm(id, goal, 400, 400, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState.BACKGROUND, System.nanoTime());
                    if (batch.reason().equals("farm.inventory_projection_refreshed")) { batch = background.farm(id, goal, 400, 401, org.l2jmobius.gameserver.phantoms.activity.PhantomActivityState.BACKGROUND, System.nanoTime()); }
                    PhantomAssertions.assertTrue(batch.successful() && batch.encounters() > 0, "Scalar background edge must survive terminal owner-reference release.");
                    for (int incarnation = 0; incarnation < 3; incarnation++)
                    {
                        PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.materialize(id).status(), "Same actual profile rematerializes at a new native epoch.");
                        PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, materialization.dematerialize(id).status(), "Same-profile next epoch has its own actual terminal receipt.");
                        released++; highwater = Math.max(highwater, owners().size()); call("sampleTerminals"); call("drain");
                    }
                }
            }
            call("sampleTerminals"); call("drain");
            highwater = Math.max(highwater, owners().size());
            if (!failure().isEmpty()) { break; }
        }
        call("finishStream");
        context.record("E.nativeReleases", Integer.toString(released));
        context.record("E.activeReferenceHighwater", Integer.toString(highwater));
        context.record("E.collectorFailure", failure());
        context.record("E.actualOutput", output.toString());
        PhantomAssertions.assertEquals("", failure(), "Collector must support cumulative births independently of simultaneous active scopes.");
        PhantomAssertions.assertEquals(133, released, "More than 32 distinct profiles and 128 cumulative actual epochs required.");
        PhantomAssertions.assertEquals(0, owners().size(), "Acknowledged permanent terminal export must release every strong native owner reference.");
        try (var files = Files.list(output))
        {
            PhantomAssertions.assertEquals(133L, files.filter(p -> p.getFileName().toString().endsWith("-register.properties")).count(), "Every actual birth must remain on disk.");
        }
        try (var files = Files.list(output))
        {
            PhantomAssertions.assertEquals(133L, files.filter(p -> p.getFileName().toString().endsWith("-terminal.properties")).count(), "Every actual permanent terminal must remain on disk.");
        }
    }
    private void fault(PhantomTestContext context) throws Exception
    {
        var environment = (PhantomHeadlessPlayerTestEnvironment) field(base, "_environment");
        var reset = Player.load(environment.primary().objectId());
        try { reset.stopAllTasks(); reset.getStat().setVitalityPoints(1, true); } finally { environment.cleanupLoadedPlayer(reset); }
        var output = context.moduleRoot().resolve("docs/phantoms/tasks/PHANTOM-M1-PLAN-INVENTORY-CONTINUITY-030/evidence/NATIVE_FAULT_" + System.nanoTime());
        attach(context, output);
        for (var thread : Thread.getAllStackTraces().keySet())
        { if (thread.getName().equals("TASK030-witness-export")) { thread.interrupt(); thread.join(2000); PhantomAssertions.assertFalse(thread.isAlive(), "Pause only the exact passive TEST exporter."); } }
        boolean filesystem = faultMode.equals("fs030");
        if (filesystem)
        {
            var blocked = output.resolve("blocked-root"); Files.writeString(blocked, "controlled exact TEST filesystem fault");
            var root = observer().getDeclaredField("streamRoot"); root.setAccessible(true); root.set(null, blocked);
        }
        var open = PhantomBackgroundSuite.class.getDeclaredMethod("openNativeProductionFixture", PhantomTestContext.class, boolean.class); open.setAccessible(true);
        int releases = filesystem ? 3 : 130;
        for (int i = 0; i < releases; i++)
        {
            try (var fixture = openActual(open, base, context))
            {
                var outcome = ((PhantomMaterializationService) field(fixture, "materialization")).dematerialize((long) invoke(fixture, "id"));
                PhantomAssertions.assertEquals(PhantomMaterializationService.ResultStatus.SUCCESS, outcome.status(), "Observer fault leaves genuine native store/release SUCCESS.");
            }
        }
        try { call("sampleTerminals"); call("drain"); } catch (java.lang.reflect.InvocationTargetException expected) { }
        var first = failure();
        PhantomAssertions.assertFalse(first.isEmpty(), "Observer fault must invalidate proof.");
        try { call("drain"); } catch (java.lang.reflect.InvocationTargetException expected) { }
        PhantomAssertions.assertEquals(first, failure(), "First observer error is sticky across later successful/failed export.");
        PhantomAssertions.assertTrue(owners().size() <= 128, "ACTIVE strong references remain bounded under failed export.");
        context.record("E06.nativeReleases", Integer.toString(releases)); context.record("E06.proofInvalid", first); context.record("E06.output", output.toString());
    }
    private static AutoCloseable openActual(java.lang.reflect.Method open, Object base, PhantomTestContext context) throws Exception
    {
        try { return (AutoCloseable) open.invoke(base, context, true); }
        catch (java.lang.reflect.InvocationTargetException failure) { throw new AssertionError("Actual fixture: " + failure.getCause(), failure.getCause()); }
    }
    private static void attach(PhantomTestContext context, Path output) throws Exception
    {
        var runtime = context.moduleRoot().resolve(".phantom-local/contract030a/runtime");
        var process = ProcessHandle.current();
        var start = process.info().startInstant().orElseThrow();
        long ticks = 621355968000000000L + start.getEpochSecond() * 10000000L + start.getNano() / 100;
        var spec = new Properties();
        spec.setProperty("runtime", runtime.toString()); spec.setProperty("output", output.toString());
        spec.setProperty("owner", "TASK030_CONTRACT"); spec.setProperty("pid", Long.toString(process.pid()));
        spec.setProperty("startTicks", Long.toString(ticks)); spec.setProperty("mode", "OBSERVE");
        spec.setProperty("profile.275", "0");
        String manifest = Files.readString(runtime.resolve("local-play.json"));
        var sha = java.util.regex.Pattern.compile("\\\"codeSha\\\"\\s*:\\s*\\\"([0-9a-f]{40})\\\"").matcher(manifest);
        if (!sha.find()) { throw new AssertionError("Actual owned source SHA missing."); }
        spec.setProperty("codeSha", sha.group(1));
        var file = runtime.getParent().resolve("stream-test-" + System.nanoTime() + ".properties");
        try (var writer = Files.newBufferedWriter(file)) { spec.store(writer, "Actual owned native test process"); }
        var method = observer().getDeclaredMethod("agentmain", String.class, java.lang.instrument.Instrumentation.class);
        try { method.invoke(null, file.toString(), null); } catch (java.lang.reflect.InvocationTargetException failure) { throw new AssertionError("Observer attach: " + failure.getCause(), failure.getCause()); }
    }
    private static Class<?> observer() throws Exception { return Class.forName("Contract030Observer"); }
    private static java.util.Map<?, ?> owners() throws Exception
    {
        var field = observer().getDeclaredField("RECEIPT_OWNERS"); field.setAccessible(true); return (java.util.Map<?, ?>) field.get(null);
    }
    private static String failure() throws Exception
    {
        var field = observer().getDeclaredField("exporterFailure"); field.setAccessible(true); return (String) field.get(null);
    }
    private static void call(String name) throws Exception
    {
        var method = observer().getDeclaredMethod(name); method.setAccessible(true); method.invoke(null);
    }
    private static Object field(Object object, String name) throws Exception
    {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object invoke(Object object, String name) throws Exception
    {
        var method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object);
    }
}

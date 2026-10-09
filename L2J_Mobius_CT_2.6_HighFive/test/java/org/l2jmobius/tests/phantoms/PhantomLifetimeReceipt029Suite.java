/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Properties;
import org.l2jmobius.gameserver.model.actor.Player;
import org.l2jmobius.gameserver.phantoms.player.PhantomNativeWorkScope;
import org.l2jmobius.gameserver.phantoms.player.PhantomIdentityLeaseRegistry;
import org.l2jmobius.gameserver.phantoms.player.HeadlessPlayerOutboundSession;

/** Tests the real task observer export after telemetry selection replacement. */
public final class PhantomLifetimeReceipt029Suite implements PhantomTestSuite
{
    private final PhantomHeadlessPlayerTestEnvironment _environment = new PhantomHeadlessPlayerTestEnvironment();
    public static void main(String[] args)
    {
        final var context = new PhantomTestContext(29002903, Path.of(args[0]), Path.of(args[1]));
        System.exit(PhantomTestLauncher.runSuite("lifetime-receipt029", new PhantomLifetimeReceipt029Suite(), context));
    }
    @Override public String id() { return "lifetime-receipt029"; }
    @Override public void beforeAll(PhantomTestContext context) throws Exception { _environment.initialize(context); }
    @Override public void afterAll(PhantomTestContext context) throws Exception { _environment.shutdown(); }
    @Override public void register(PhantomTestRegistry registry)
    {
        registry.add("P01-real-lifetime-survives-telemetry-switch", context ->
        {
            final var first = Player.load(_environment.primary().objectId());
            final var second = Player.load(_environment.observer().objectId());
            try (var firstOutput = first.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128));
                var secondOutput = second.attachOutboundSession(new HeadlessPlayerOutboundSession(8, 128));
                var firstLease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(first.getObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM);
                var secondLease = PhantomIdentityLeaseRegistry.getInstance().tryAcquire(second.getObjectId(), PhantomIdentityLeaseRegistry.OwnerKind.PHANTOM))
            {
                first.stopAllTasks(); second.stopAllTasks();
                first.spawnMe(first.getX(), first.getY(), first.getZ());
                second.spawnMe(second.getX(), second.getY(), second.getZ());
                final var constructor = PhantomNativeWorkScope.class.getDeclaredConstructor(Object.class, Player.class, PhantomIdentityLeaseRegistry.Lease.class, long.class);
                constructor.setAccessible(true);
                final var firstOwner = constructor.newInstance(new Object(), first, firstLease, System.nanoTime());
                final var secondOwner = constructor.newInstance(new Object(), second, secondLease, System.nanoTime());
                first.attachNativeWorkOwner(firstOwner); second.attachNativeWorkOwner(secondOwner);
                try
                {
                    final var root = context.moduleRoot().resolve("docs/phantoms/tasks/PHANTOM-M1-BACKGROUND-HANDOFF-029/evidence");
                    final var suffix = Long.toString(System.nanoTime());
                    attach(root.resolve("P01-A-" + suffix), 101, firstOwner.epoch());
                    final var output = root.resolve("P01-B-" + suffix);
                    attach(output, 102, secondOwner.epoch());
                    final var observer = Class.forName("Contract029Observer");
                    final var status = observer.getDeclaredMethod("status", Path.class); status.setAccessible(true);
                    status.invoke(null, output);
                    final Path exported;
                    try (var paths = Files.list(output)) { exported = paths.filter(path -> path.getFileName().toString().startsWith("final-owner-state-")).findFirst().orElseThrow(); }
                    final String text = Files.readString(exported);
                    context.record("P01.actualExport", text);
                    PhantomAssertions.assertTrue(text.contains("\"profileId\":\"101\"") && text.contains("\"profileId\":\"102\""), "Collector terminal scope must retain both real lifetimes after telemetryB selection.");
                    PhantomAssertions.assertFalse(text.contains("\"nativeOwnerState\":\"DETACHED\""), "Live or temporary-sealed owners cannot be terminal.");
                    final var enroll = observer.getDeclaredMethod("enroll", long.class, PhantomNativeWorkScope.class); enroll.setAccessible(true);
                    boolean rejected = false;
                    try { enroll.invoke(null, -1L, firstOwner); }
                    catch (java.lang.reflect.InvocationTargetException failure) { rejected = failure.getCause() instanceof IllegalStateException; }
                    PhantomAssertions.assertTrue(rejected, "P05 negative profile must fail registration before creating a receipt obligation.");
                    final var sample = observer.getDeclaredMethod("sampleTerminals"); sample.setAccessible(true);
                    firstOwner.checkpoint(() -> { try { sample.invoke(null); } catch (Exception failure) { throw new IllegalStateException(failure); } return null; });
                    final var terminalField = observer.getDeclaredField("TERMINAL"); terminalField.setAccessible(true);
                    final var terminals = (java.util.Set<?>) terminalField.get(null);
                    PhantomAssertions.assertEquals(0, terminals.size(), "P03 temporary SEALED checkpoint must not be terminal.");
                    firstOwner.drainAndSeal(System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
                    firstOwner.detach(); first.detachNativeWorkOwner(firstOwner);
                    sample.invoke(null);
                    PhantomAssertions.assertEquals(1, terminals.size(), "P02 actual DETACHED/permanent/zero-work lifetime must have terminal witness.");
                    final var nextOwner = constructor.newInstance(new Object(), first, firstLease, System.nanoTime());
                    first.attachNativeWorkOwner(nextOwner);
                    try
                    {
                        enroll.invoke(null, 101L, nextOwner);
                        status.invoke(null, output);
                        final var ownersField = observer.getDeclaredField("RECEIPT_OWNERS"); ownersField.setAccessible(true);
                        PhantomAssertions.assertEquals(3, ((java.util.Map<?, ?>) ownersField.get(null)).size(), "P02 new epoch preserves both prior scopes and terminal parent.");
                    }
                    finally { first.deleteMe(); first.detachNativeWorkOwner(nextOwner); }
                }
                finally { if (first.isOnline()) { first.deleteMe(); } second.deleteMe(); first.detachNativeWorkOwner(firstOwner); second.detachNativeWorkOwner(secondOwner); }
            }
            finally { first.stopAllTasks(); second.stopAllTasks(); }
        });
    }
    private static void attach(Path output, long profile, long epoch) throws Exception
    {
        final var runtime = Path.of("").toRealPath().getParent();
        final var start = ProcessHandle.current().info().startInstant().orElseThrow();
        final long ticks = 621355968000000000L + start.getEpochSecond() * 10000000L + start.getNano() / 100;
        final var spec = new Properties();
        spec.setProperty("runtime", runtime.toString()); spec.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        spec.setProperty("startTicks", Long.toString(ticks)); spec.setProperty("owner", "TASK029_CONTRACT");
        spec.setProperty("mode", "OBSERVE"); spec.setProperty("output", output.toString());
        spec.setProperty("codeSha", "2b9496c935748803f8505c472bf4085977fb04d2"); spec.setProperty("profile." + profile, Long.toString(epoch));
        final var path = runtime.getParent().resolve(output.getFileName() + ".properties");
        try (var writer = Files.newBufferedWriter(path)) { spec.store(writer, "Controlled own TEST selection"); }
        Class.forName("Contract029Observer").getMethod("agentmain", String.class, java.lang.instrument.Instrumentation.class).invoke(null, path.toString(), null);
    }
}

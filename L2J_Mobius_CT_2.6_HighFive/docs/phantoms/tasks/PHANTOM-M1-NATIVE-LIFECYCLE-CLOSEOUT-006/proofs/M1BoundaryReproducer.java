/*
 * Standalone control-flow models derived from the reviewed L2J source.
 * NOT a compiled L2J test, NOT runtime evidence for profiles 110/142/175.
 * No network, database, server or external dependencies.
 */
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class M1BoundaryReproducer {
    private static boolean fixed;
    private static int passed, failed;
    private record Actor(boolean managed, boolean online) {}
    private interface Lease extends AutoCloseable { void close(); }
    private static final Lease NOOP = () -> {};
    private static final class Policy { Lease acquire() { return null; } }
    private static final class Pools {
        final Map<Actor, Policy> policies = new ConcurrentHashMap<>();
        final Set<Actor> players = ConcurrentHashMap.newKeySet();
        synchronized void start(Actor actor, Policy policy) { policies.put(actor, policy); players.add(actor); }
        synchronized void stopObserved(Actor actor, Policy expected) {
            // PROPOSAL: the same registration monitor covers comparison and pool removal.
            if (fixed && policies.get(actor) != expected) return;
            policies.remove(actor);
            players.remove(actor);
        }
    }
    private static Lease acquire(Actor actor, Policy policy) {
        // PROPOSAL: managed actors never fall back to the ordinary-player branch.
        if (fixed && actor.managed() && policy == null) return null;
        return policy == null ? NOOP : policy.acquire();
    }
    private static void lateTick() throws Exception {
        final var pool = new Pools(); final var actor = new Actor(true, true);
        pool.start(actor, new Policy());
        final var selected = new CountDownLatch(1); final var resume = new CountDownLatch(1);
        final var effects = new AtomicInteger();
        final var worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> result = worker.submit(() -> {
                Actor observed = pool.players.iterator().next();
                selected.countDown(); await(resume);
                Policy policy = pool.policies.get(observed);
                try (Lease lease = acquire(observed, policy)) {
                    if (lease != null && observed.online()) effects.incrementAndGet();
                }
            });
            check(selected.await(2, TimeUnit.SECONDS), "selection timed out");
            pool.policies.remove(actor); pool.players.remove(actor); resume.countDown();
            result.get(2, TimeUnit.SECONDS);
            check(effects.get() == 0, "managed tick executed without policy; effects=" + effects.get());
        } finally { resume.countDown(); worker.shutdownNow(); }
    }
    private static void staleStop() {
        var pool = new Pools(); var actor = new Actor(true, true);
        var oldPolicy = new Policy(); var newPolicy = new Policy();
        pool.start(actor, oldPolicy); pool.start(actor, newPolicy);
        pool.stopObserved(actor, oldPolicy);
        check(pool.policies.get(actor) == newPolicy && pool.players.contains(actor), "old tick removed replacement policy");
    }
    private static boolean healthy(boolean autoPlay, boolean autoUse) {
        return fixed ? autoPlay && autoUse : autoPlay;
    }
    private static String firstFailure(List<String> attempts) {
        String first = "";
        for (String failure : attempts) if (!fixed || first.isEmpty()) first = failure;
        return first;
    }
    private static RuntimeException failingStore() {
        RuntimeException primary = new IllegalStateException("native-store-first");
        RuntimeException secondary = new IllegalArgumentException("finalizer-second");
        try {
            if (fixed) {
                try { throw primary; }
                catch (RuntimeException first) {
                    try { throw secondary; }
                    catch (RuntimeException second) { first.addSuppressed(second); }
                    throw first;
                }
            }
            try { throw primary; }
            finally { throw secondary; }
        } catch (RuntimeException error) { return error; }
    }
    private record Member(boolean eligible, boolean failed, boolean admissionOpen,
                          String idleReason, String travelFailure) {}
    private static boolean cohort(List<Member> members) {
        if (fixed && members.stream().anyMatch(m -> m.eligible() && (m.failed() || !m.admissionOpen()))) return false;
        long eligible = members.stream().filter(Member::eligible).count();
        // Legacy filter is even narrower (same profile twice); blank route errors never match.
        boolean failedIdle = members.stream().anyMatch(m -> m.eligible()
            && m.idleReason().equals("ACTIVE_IDLE")
            && m.travelFailure().matches(".*(navigation_|native_progress_|route_absent).*"));
        return eligible >= 4 && !failedIdle;
    }
    private static boolean nativeLife(boolean castingAtMonster, long damageDelta, long expDelta) {
        return fixed ? castingAtMonster && damageDelta > 0 && expDelta > 0 : castingAtMonster;
    }
    private static boolean terminalHandled(String reason) {
        if (fixed && reason.equals("travel.journey_deadline")) return true;
        return Set.of("travel.route_absent", "travel.destination_unproven", "travel.gatekeeper_unproven",
            "travel.navigation_no_path", "travel.navigation_route_obstructed", "travel.navigation_route_budget_exceeded",
            "travel.native_progress_stuck", "travel.native_progress_timeout").contains(reason);
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("barrier timeout"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private interface Test { void run() throws Exception; }
    private static void test(String name, Test body) {
        try { body.run(); passed++; System.out.println("PASS " + name); }
        catch (Throwable error) { failed++; System.out.println("FAIL " + name + ": " + error); }
    }
    public static void main(String[] args) {
        fixed = args.length == 1 && args[0].equals("--fixed");
        System.out.println("CONTROL_FLOW_MODEL_ONLY mode=" + (fixed ? "fixed-proposal" : "reviewed-legacy"));
        test("policy_missing_after_stop", M1BoundaryReproducer::lateTick);
        test("stale_stop_preserves_replacement", M1BoundaryReproducer::staleStop);
        test("autouse_missing_is_not_healthy", () -> check(!healthy(true, false), "AutoUse absence ignored"));
        test("first_exception_is_immutable", () -> check(firstFailure(List.of("FIRST", "SECOND")).equals("FIRST"), "first failure overwritten"));
        test("finally_preserves_primary", () -> {
            var failure = failingStore();
            check(failure.getMessage().equals("native-store-first"), "finally replaced primary with " + failure.getMessage());
            check(failure.getSuppressed().length == 1, "secondary exception not retained");
        });
        test("failed_cohort_cannot_pass", () -> {
            var members = List.of(new Member(true,false,true,"FARM",""), new Member(true,true,false,"ACTIVE_IDLE",""),
                new Member(true,false,true,"FARM",""), new Member(true,false,true,"FARM",""));
            check(!cohort(members), "FAILED/admission-closed cohort graded PASS");
        });
        test("self_cast_is_not_native_progress", () -> check(!nativeLife(true,0,0), "casting flag graded PASS without damage or progress"));
        test("terminal_deadline_is_consumed", () -> check(terminalHandled("travel.journey_deadline"), "terminal reason dropped by whitelist"));
        test("stock_player_without_policy_keeps_tick", () -> check(acquire(new Actor(false,true),null) != null,"normal player blocked"));
        test("healthy_pools_are_healthy", () -> check(healthy(true,true),"healthy pair rejected"));
        test("positive_native_progress", () -> check(nativeLife(true,25,10),"actual progress rejected"));
        System.out.println("SUMMARY passed=" + passed + " failed=" + failed + " scope=standalone-model-not-L2J");
        if (failed > 0) System.exit(1);
    }
}

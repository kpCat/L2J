/** Executable contract reference only; this is NOT a Mobius integration test. */
public final class BoundaryPolicy024Test {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        for (var origin : BoundaryPolicy024.Origin.values()) {
            for (var state : BoundaryPolicy024.Scope.values()) {
                for (int flags = 0; flags < 8; flags++) {
                    boolean exact = (flags & 1) != 0;
                    boolean captured = (flags & 2) != 0;
                    boolean earned = (flags & 4) != 0;
                    var result = BoundaryPolicy024.admit(origin, state, exact, captured, earned);
                    if (!exact || state == BoundaryPolicy024.Scope.RELEASED) {
                        eq(BoundaryPolicy024.Admission.REJECT_STALE, result);
                    } else if (origin == BoundaryPolicy024.Origin.NEW_NATIVE_ACTION) {
                        eq(state == BoundaryPolicy024.Scope.OPEN ? BoundaryPolicy024.Admission.RESERVE_NEW_CURRENT
                                : BoundaryPolicy024.Admission.DEFER_BEFORE_PUBLICATION, result);
                    } else if (origin == BoundaryPolicy024.Origin.DELAYED_CONTINUATION && !captured) {
                        eq(BoundaryPolicy024.Admission.REJECT_UNCAPTURED, result);
                    } else if (captured && earned) {
                        eq(BoundaryPolicy024.Admission.CONTINUE_EXACT_EARNED, result);
                    } else {
                        eq(state == BoundaryPolicy024.Scope.OPEN ? BoundaryPolicy024.Admission.RESERVE_NEW_CURRENT
                                : BoundaryPolicy024.Admission.DEFER_BEFORE_PUBLICATION, result);
                    }
                }
            }
        }
        var live = new BoundaryPolicy024.Point(0, 45280, 40867, -3504, 123);
        for (boolean bound : new boolean[]{false, true}) {
            var result = BoundaryPolicy024.durablePosition(live, "old.anchor", true, "goal.anchor", true, bound);
            eq(live, result.actual());
            eq("old.anchor", result.anchorProvenance());
            eq(bound, result.positionSimulationEligible());
        }
        eq("goal.anchor", BoundaryPolicy024.durablePosition(live, "missing", false, "goal.anchor", true, false).anchorProvenance());
        boolean threw = false;
        try { BoundaryPolicy024.durablePosition(live, "missing", false, "missing", false, false); }
        catch (IllegalArgumentException expected) { threw = true; }
        eq(true, threw);
        eq(false, BoundaryPolicy024.mayFinalizeDependencies(0, 1, false));
        eq(false, BoundaryPolicy024.mayFinalizeDependencies(1, 0, false));
        eq(false, BoundaryPolicy024.mayFinalizeDependencies(0, 0, true));
        eq(true, BoundaryPolicy024.mayFinalizeDependencies(0, 0, false));
        System.out.println("REFERENCE_CONTRACT_PASS checks=" + checks);
    }
}

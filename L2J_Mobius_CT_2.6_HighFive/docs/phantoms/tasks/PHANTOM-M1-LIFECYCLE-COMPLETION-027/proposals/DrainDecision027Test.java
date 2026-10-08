/** Standalone tests of the reference table, NOT a native server test. */
public final class DrainDecision027Test {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static DrainDecision027.Facts facts(long now, boolean closed,
            int controls, int tickets, int entries, int receipts, boolean dependencies) {
        return new DrainDecision027.Facts(7, now, closed, controls, tickets, entries, receipts, dependencies, "");
    }
    public static void main(String[] args) {
        var a = new DrainDecision027.Attempt(7, 0, 10_000);
        require(DrainDecision027.evaluate(a, facts(33, true, 1, 1, 8, 0, false)).outcome()
                == DrainDecision027.Outcome.PENDING, "33ms busy is not terminal failure");
        require(DrainDecision027.evaluate(a, facts(50, true, 1, 1, 8, 0, false)).phase()
                == DrainDecision027.Phase.WAIT_ACCEPTED_CONTROL, "second call cannot abandon accepted work");
        require(DrainDecision027.evaluate(a, facts(2_600, true, 0, 0, 8, 0, false)).phase()
                == DrainDecision027.Phase.DRAIN_PLAYERS, "callback complete does not itself mean saved");
        require(DrainDecision027.evaluate(a, facts(2_700, true, 0, 0, 0, 1, false)).outcome()
                == DrainDecision027.Outcome.PENDING, "pending receipt cannot be skipped");
        require(DrainDecision027.evaluate(a, facts(2_800, true, 0, 0, 0, 0, false)).phase()
                == DrainDecision027.Phase.FINISH_DEPENDENCIES, "POST_STORE dependencies finish last");
        require(DrainDecision027.evaluate(a, facts(2_900, true, 0, 0, 0, 0, true)).outcome()
                == DrainDecision027.Outcome.COMPLETE, "healthy completion");
        require(DrainDecision027.evaluate(a, facts(10_000, true, 0, 1, 8, 0, false)).outcome()
                == DrainDecision027.Outcome.FAILED, "timeout is not implicit completion");
        require(DrainDecision027.evaluate(a, new DrainDecision027.Facts(8, 20, true, 0, 0, 0, 0, true, "")).reason()
                .equals("INSTANCE_CHANGED"), "replacement instance cannot inherit result");
        require(DrainDecision027.evaluate(a, new DrainDecision027.Facts(7, 20, true, 0, 0, 0, 0, true, "WRITE_FAILED")).outcome()
                == DrainDecision027.Outcome.FAILED, "zero work does not clear genuine failure");
        require(DrainDecision027.evaluate(a, facts(-1, true, 0, 0, 0, 0, true)).outcome()
                == DrainDecision027.Outcome.FAILED, "invalid time must not pass");
        for (int mask = 0; mask < 64; mask++) {
            boolean closed = (mask & 1) != 0, deps = (mask & 2) != 0;
            int c = (mask >> 2) & 1, t = (mask >> 3) & 1, e = (mask >> 4) & 1, p = (mask >> 5) & 1;
            var f = facts(50, closed, c, t, e, p, deps);
            var d = DrainDecision027.evaluate(a, f);
            require((d.outcome() == DrainDecision027.Outcome.COMPLETE) ==
                    (closed && deps && c == 0 && t == 0 && e == 0 && p == 0), "all blockers counted: " + mask);
            require(d.deadlineNanos() == 10_000, "poll does not renew deadline");
        }
        boolean rejected = false;
        try { facts(10, true, -1, 0, 0, 0, true); } catch (IllegalArgumentException ex) { rejected = true; }
        require(rejected, "missing retained count -1 is not healthy0");
        System.out.println("REFERENCE_TABLE_PASS assertions=" + checks + "; SERVER_NOT_RUN");
    }
}

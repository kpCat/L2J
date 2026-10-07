public final class AdmissionDecision023Test {
    private static int checked;
    private static void check(boolean value) {
        checked++;
        if (!value) throw new AssertionError("Policy assertion " + checked);
    }
    public static void main(String[] args) {
        for (var boundary : AdmissionDecision023.Boundary.values())
        for (var scope : AdmissionDecision023.Scope.values())
        for (int bits = 0; bits < 32; bits++) {
            var f = new AdmissionDecision023.Facts(boundary, scope,
                (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0,
                (bits & 8) != 0, (bits & 16) != 0);
            var d = AdmissionDecision023.evaluate(f);
            if (d == AdmissionDecision023.Decision.RESERVE_NEW_ROOT) {
                check(boundary == AdmissionDecision023.Boundary.FRESH_NATIVE_WRITE);
                check(scope == AdmissionDecision023.Scope.OPEN);
                check(f.exactIdentity() && !f.previouslyCaptured() && !f.exactRunningTicket());
                check(f.explicitNativeParticipant() && f.beforeFirstWriter());
            }
            if (d == AdmissionDecision023.Decision.RETAIN_CAPTURED) {
                check(f.exactIdentity() && f.previouslyCaptured() && f.exactRunningTicket());
                check(scope == AdmissionDecision023.Scope.OPEN || scope == AdmissionDecision023.Scope.DRAINING);
            }
            if (!f.exactIdentity() || scope == AdmissionDecision023.Scope.DETACHED || scope == AdmissionDecision023.Scope.FAILED)
                check(d == AdmissionDecision023.Decision.REJECT_STALE);
        }
        var yes = new AdmissionDecision023.Facts(AdmissionDecision023.Boundary.FRESH_NATIVE_WRITE,
            AdmissionDecision023.Scope.OPEN, true, false, false, true, true);
        check(AdmissionDecision023.evaluate(yes) == AdmissionDecision023.Decision.RESERVE_NEW_ROOT);
        var no = new AdmissionDecision023.Facts(AdmissionDecision023.Boundary.DELAYED_RESUME,
            AdmissionDecision023.Scope.OPEN, true, false, false, true, true);
        check(AdmissionDecision023.evaluate(no) == AdmissionDecision023.Decision.REJECT_INVALID);
        var closed = new AdmissionDecision023.Facts(AdmissionDecision023.Boundary.FRESH_NATIVE_WRITE,
            AdmissionDecision023.Scope.SEALED, true, false, false, true, true);
        check(AdmissionDecision023.evaluate(closed) == AdmissionDecision023.Decision.DEFER_BEFORE_WRITE);
        System.out.println("PASS: " + checked + " assertions, 320 policy combinations; native integration NOT RUN");
    }
}

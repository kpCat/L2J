import java.util.Objects;

/** Proposed decision table only. This does not reserve native work or authorize a writer. */
public final class AdmissionDecision023 {
    private AdmissionDecision023() { }
    public enum Boundary { DELAYED_RESUME, FRESH_NATIVE_WRITE }
    public enum Scope { OPEN, DRAINING, SEALED, DETACHED, FAILED }
    public enum Decision { RETAIN_CAPTURED, RESERVE_NEW_ROOT, DEFER_BEFORE_WRITE, REJECT_STALE, REJECT_INVALID }
    public record Facts(Boundary boundary, Scope scope, boolean exactIdentity,
                        boolean previouslyCaptured, boolean exactRunningTicket,
                        boolean explicitNativeParticipant, boolean beforeFirstWriter) {
        public Facts {
            Objects.requireNonNull(boundary, "boundary");
            Objects.requireNonNull(scope, "scope");
        }
    }
    public static Decision evaluate(Facts f) {
        Objects.requireNonNull(f, "facts");
        if (!f.exactIdentity()) return Decision.REJECT_STALE;
        if (f.scope() == Scope.FAILED || f.scope() == Scope.DETACHED) return Decision.REJECT_STALE;
        if (f.previouslyCaptured()) {
            // A captured obligation cannot re-enrol itself as a new root after expiration.
            if (!f.exactRunningTicket()) return Decision.REJECT_STALE;
            return f.scope() == Scope.OPEN || f.scope() == Scope.DRAINING
                ? Decision.RETAIN_CAPTURED : Decision.REJECT_INVALID;
        }
        if (f.boundary() != Boundary.FRESH_NATIVE_WRITE || !f.beforeFirstWriter()
            || !f.explicitNativeParticipant() || f.exactRunningTicket()) {
            return Decision.REJECT_INVALID;
        }
        // Reserve is still required and may lose the race to drain. Never wait for reopening.
        return f.scope() == Scope.OPEN ? Decision.RESERVE_NEW_ROOT : Decision.DEFER_BEFORE_WRITE;
    }
}

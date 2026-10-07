import java.util.Objects;

/**
 * Compilable reference decision table, not a replacement for native ticket admission.
 * Every permit still requires the real reserve/start/current owner checks at execution.
 * In particular CONTINUE_EXACT_EARNED never reopens a failed lifetime.
 */
public final class BoundaryPolicy024 {
    private BoundaryPolicy024() { }
    public enum Origin { NEW_NATIVE_ACTION, DELAYED_CONTINUATION, SYNCHRONOUS_WRITE }
    public enum Scope { OPEN, CHECKPOINT, DRAINING, FAILED, RELEASED }
    public enum Admission { RESERVE_NEW_CURRENT, CONTINUE_EXACT_EARNED,
        DEFER_BEFORE_PUBLICATION, REJECT_STALE, REJECT_UNCAPTURED }

    public static Admission admit(Origin origin, Scope scope, boolean exactIdentity,
                                  boolean capturedRunningParent, boolean parentEarned) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(scope);
        if (!exactIdentity || scope == Scope.RELEASED) return Admission.REJECT_STALE;
        if (origin == Origin.NEW_NATIVE_ACTION) {
            // A new retaliation is not owed merely because another action is EARNED.
            return scope == Scope.OPEN ? Admission.RESERVE_NEW_CURRENT
                    : Admission.DEFER_BEFORE_PUBLICATION;
        }
        if (origin == Origin.DELAYED_CONTINUATION && !capturedRunningParent)
            return Admission.REJECT_UNCAPTURED;
        if (capturedRunningParent && parentEarned) return Admission.CONTINUE_EXACT_EARNED;
        return scope == Scope.OPEN ? Admission.RESERVE_NEW_CURRENT
                : Admission.DEFER_BEFORE_PUBLICATION;
    }

    public record Point(int instanceId, int x, int y, int z, int heading) {
        public Point {
            if (instanceId < 0 || heading < 0) throw new IllegalArgumentException("Invalid native position");
        }
    }
    /** The anchor is provenance/intent, not an invented replacement for actual XYZ. */
    public record DurablePosition(Point actual, String anchorProvenance, boolean positionSimulationEligible) { }

    public static DurablePosition durablePosition(Point actual, String previousAnchor,
            boolean previousExists, String goalAnchor, boolean goalExists, boolean geometryValid) {
        Objects.requireNonNull(actual);
        String reference = previousExists && nonblank(previousAnchor) ? previousAnchor
                : goalExists && nonblank(goalAnchor) ? goalAnchor : null;
        if (reference == null) throw new IllegalArgumentException("No proven native lineage anchor");
        return new DurablePosition(actual, reference, geometryValid);
    }
    private static boolean nonblank(String value) { return value != null && !value.isBlank(); }

    public static boolean mayFinalizeDependencies(int retainedMaterializations,
            int unfinishedStores, boolean activeCallbacks) {
        if (retainedMaterializations < 0 || unfinishedStores < 0) throw new IllegalArgumentException("Negative count");
        return retainedMaterializations == 0 && unfinishedStores == 0 && !activeCallbacks;
    }
}

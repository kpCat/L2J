import java.util.Objects;

/**
 * Независимый образец policy выбора продолжения. НЕ планировщик маршрута/боя.
 * Inputs поставляет native adapter; ни один enum не выдаёт native permission.
 * Production интеграция должна предпочитать уже имеющиеся identity/result types.
 */
public final class ActionContinuation038 {
    private ActionContinuation038() { }
    public enum Route { NONE, PENDING, BACKPRESSURE, FOUND, BLOCKED }
    public enum Decision { REVOKED, RETARGET, YIELD_NATIVE, PAUSED, HIGHER_REPLAN,
        READY_NATIVE, REQUEST_ROUTE, WAIT_ROUTE, FOLLOW_ROUTE }
    public record Key(long profileId, int objectId, long epoch, long goalId, long revision,
            long actionSequence, int targetObjectId, int instanceId, long targetGeneration) {
        public Key {
            if (profileId <= 0 || objectId <= 0 || epoch < 0 || goalId <= 0 || revision < 0
                    || actionSequence <= 0 || targetObjectId <= 0 || instanceId < 0 || targetGeneration <= 0)
                throw new IllegalArgumentException("Invalid action identity");
        }
        boolean sameOwnerAction(Key other) {
            return other != null && profileId == other.profileId && objectId == other.objectId
                    && epoch == other.epoch && goalId == other.goalId && revision == other.revision
                    && actionSequence == other.actionSequence;
        }
        boolean sameTarget(Key other) {
            return other != null && targetObjectId == other.targetObjectId
                    && instanceId == other.instanceId && targetGeneration == other.targetGeneration;
        }
    }
    /**
     * targetValid зависит от действия: здесь это не встроенное правило alive/dead.
     * nativeWorkInFlight означает подтверждённую native работу, НЕ один isMoving flag.
     * canExecuteNow включает свежий action-specific range/LOS/слой и native условия.
     * attemptExpired берётся от первоначального monotonic deadline, не от нового tick.
     */
    public record Facts(Key observedKey, boolean ownerCurrent, boolean targetValid,
            boolean nativeWorkInFlight, boolean temporarilyPaused, boolean attemptExpired,
            boolean canExecuteNow, boolean routeReplyExact, Route route,
            boolean movementStalled, int remainingRouteRepairs) {
        public Facts {
            Objects.requireNonNull(observedKey);
            Objects.requireNonNull(route);
            if (remainingRouteRepairs < 0) throw new IllegalArgumentException("Negative repair count");
        }
    }
    public static Decision decide(Key expected, Facts facts) {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(facts);
        if (!facts.ownerCurrent() || !expected.sameOwnerAction(facts.observedKey()))
            return Decision.REVOKED; // Только новые команды; earned не отменяются этим результатом.
        if (!expected.sameTarget(facts.observedKey()) || !facts.targetValid())
            return facts.nativeWorkInFlight() ? Decision.YIELD_NATIVE : Decision.RETARGET;
        if (facts.nativeWorkInFlight()) return Decision.YIELD_NATIVE;
        if (facts.attemptExpired()) return Decision.HIGHER_REPLAN;
        if (facts.temporarilyPaused()) return Decision.PAUSED;
        // Свежий native предикат важнее устаревшего route-only failure.
        if (facts.canExecuteNow()) return Decision.READY_NATIVE;
        if (!facts.routeReplyExact() || facts.movementStalled()) return retry(facts);
        return switch (facts.route()) {
            case NONE -> retry(facts);
            case PENDING, BACKPRESSURE -> Decision.WAIT_ROUTE;
            case FOUND -> Decision.FOLLOW_ROUTE;
            case BLOCKED -> retry(facts);
        };
    }
    private static Decision retry(Facts facts) {
        return facts.remainingRouteRepairs() > 0 ? Decision.REQUEST_ROUTE : Decision.HIGHER_REPLAN;
    }
}

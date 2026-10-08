import java.util.Objects;

/** Reference policy only. Each true fact requires independent native evidence. No server writes. */
public final class LocalExecutionDecision026 {
    private LocalExecutionDecision026() { }
    public enum Action {
        HOLD_IDENTITY, WAIT_CHECKPOINT, DEATH_RECOVERY, CONTINUE_NATIVE,
        REST_NATIVE, DEFENSIVE_NATIVE_RECOVERY, START_LOCAL_STOCK,
        WAIT_ROUTE, REQUEST_LOCAL_REPLAN, REQUEST_BOUNDED_RETURN
    }
    public record Facts(boolean exactOwner, boolean admissionOpen,
            boolean checkpointPending, boolean dead, boolean actionInFlight,
            boolean affordable, boolean canRecover, boolean threatened,
            boolean independentValidatedLocalOpportunity, boolean routeInProgress,
            boolean routeTerminal, boolean localExhausted) { }
    public static Action choose(Facts facts) {
        Objects.requireNonNull(facts, "facts");
        if (!facts.exactOwner()) return Action.HOLD_IDENTITY;
        // A control operation is not normal admission and must never be bypassed.
        if (facts.checkpointPending()) return Action.WAIT_CHECKPOINT;
        if (facts.dead()) return Action.DEATH_RECOVERY;
        if (!facts.admissionOpen()) return Action.HOLD_IDENTITY;
        // Let an already admitted native action finish even when its resource was spent.
        if (facts.actionInFlight()) return Action.CONTINUE_NATIVE;
        if (!facts.affordable()) {
            if (!facts.canRecover()) return Action.REQUEST_LOCAL_REPLAN;
            return facts.threatened() ? Action.DEFENSIVE_NATIVE_RECOVERY : Action.REST_NATIVE;
        }
        // This fact must exclude the previously rejected segment: independent native proof.
        if (facts.independentValidatedLocalOpportunity()) return Action.START_LOCAL_STOCK;
        if (facts.routeInProgress() && !facts.routeTerminal()) return Action.WAIT_ROUTE;
        return facts.localExhausted() ? Action.REQUEST_BOUNDED_RETURN : Action.REQUEST_LOCAL_REPLAN;
    }
}

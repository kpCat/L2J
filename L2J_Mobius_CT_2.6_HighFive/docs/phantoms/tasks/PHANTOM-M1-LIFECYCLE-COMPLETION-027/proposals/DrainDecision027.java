import java.util.Objects;

/**
 * Reference contract only. This class does not call Mobius, close pools, cancel
 * tickets, or perform a store. The real integration needs composed native tests.
 */
public final class DrainDecision027 {
    public enum Phase {
        CLOSE_PRODUCERS, WAIT_ACCEPTED_CONTROL, DRAIN_PLAYERS,
        FINISH_DEPENDENCIES, DONE, FAILED
    }
    public enum Outcome { PENDING, COMPLETE, FAILED }
    public record Attempt(long instanceToken, long startedNanos, long deadlineNanos) {
        public Attempt {
            if (instanceToken <= 0 || startedNanos < 0 || deadlineNanos <= startedNanos) {
                throw new IllegalArgumentException("invalid stop attempt");
            }
        }
    }
    public record Facts(long instanceToken, long nowNanos, boolean producersClosed,
                        int acceptedControls, int nativeTickets, int retainedEntries,
                        int pendingStores, boolean dependenciesFinished,
                        String nativeFailure) {
        public Facts {
            Objects.requireNonNull(nativeFailure, "nativeFailure");
            if (instanceToken <= 0 || acceptedControls < 0 || nativeTickets < 0
                || retainedEntries < 0 || pendingStores < 0) {
                throw new IllegalArgumentException("unknown/negative counts are not zero");
            }
        }
    }
    public record Decision(Outcome outcome, Phase phase, String reason, long deadlineNanos) {}

    private DrainDecision027() { }

    public static Decision evaluate(Attempt attempt, Facts facts) {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(facts, "facts");
        if (facts.instanceToken() != attempt.instanceToken()) {
            return failed(attempt, "INSTANCE_CHANGED");
        }
        if (facts.nowNanos() < attempt.startedNanos()) {
            return failed(attempt, "CLOCK_BEFORE_ATTEMPT");
        }
        if (!facts.nativeFailure().isEmpty()) {
            return failed(attempt, "NATIVE_FAILURE_RETAIN");
        }
        if (facts.nowNanos() >= attempt.deadlineNanos()) {
            return failed(attempt, "DEADLINE_NOT_COMPLETION");
        }
        if (!facts.producersClosed()) {
            return pending(attempt, Phase.CLOSE_PRODUCERS, "CLOSE_NEW_ROOTS_ONCE");
        }
        if (facts.acceptedControls() != 0) {
            return pending(attempt, Phase.WAIT_ACCEPTED_CONTROL, "KEEP_EXECUTORS_FOR_ACCEPTED_CONTROL");
        }
        if (facts.nativeTickets() != 0 || facts.retainedEntries() != 0 || facts.pendingStores() != 0) {
            return pending(attempt, Phase.DRAIN_PLAYERS, "COMPLETE_NATIVE_AND_STORE_ONCE");
        }
        if (!facts.dependenciesFinished()) {
            return pending(attempt, Phase.FINISH_DEPENDENCIES, "POST_STORE_THEN_DEPENDENCIES");
        }
        return new Decision(Outcome.COMPLETE, Phase.DONE, "ALL_LIFETIMES_FINISHED", attempt.deadlineNanos());
    }
    private static Decision pending(Attempt a, Phase p, String reason) {
        return new Decision(Outcome.PENDING, p, reason, a.deadlineNanos());
    }
    private static Decision failed(Attempt a, String reason) {
        return new Decision(Outcome.FAILED, Phase.FAILED, reason, a.deadlineNanos());
    }
}

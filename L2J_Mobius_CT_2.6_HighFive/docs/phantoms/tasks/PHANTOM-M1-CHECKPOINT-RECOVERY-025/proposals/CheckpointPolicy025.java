import java.util.Objects;

/**
 * Таблица решений TASK025. Не изменяет Player, БД, receipt или owner.
 * Встраивание требует настоящих stage-witness из executing boundary.
 * Отсутствие receipt НЕ доказывает отсутствие записи.
 */
public final class CheckpointPolicy025 {
    private CheckpointPolicy025() { }

    public enum Phase {
        ADMITTED, WAIT_EARNED, SEALED, CAPTURE, INVENTORY_FLUSH,
        PREPARE_ATTEMPTED, PREPARED, NATIVE_ATTEMPTED, FINALIZED,
        INDEX_PENDING, COMPLETED
    }
    public enum Receipt { ABSENT_VERIFIED, PRESENT_EXACT, UNKNOWN }
    public enum Action {
        STALE_CONTROL, TERMINAL_RETAIN, WAIT_EARNED, RETAIN_TIMEOUT,
        VERIFY_WRITE_OUTCOME, RESOLVE_RECEIPT, VERIFY_FINALIZED_NATIVE,
        PUBLISH_COMMITTED, REVALIDATE_GOAL, RESUME_ORDINARY,
        REOPEN_AND_REPLAN, CONTROL_CLASSIFICATION_REQUIRED
    }
    public record LiveFacts(
        Phase phase, boolean ownerCurrent, boolean permanentCleanup,
        boolean nativeIncident, int outstanding, boolean deadlineExpired,
        Receipt receipt, boolean writesPossible, boolean noWriteProof,
        boolean finalized, boolean nativeMatches, boolean publicationComplete,
        boolean goalCurrent) {
        public LiveFacts {
            Objects.requireNonNull(phase); Objects.requireNonNull(receipt);
            if (outstanding < 0) throw new IllegalArgumentException("outstanding < 0");
            if (noWriteProof && (writesPossible || finalized ||
                    receipt != Receipt.ABSENT_VERIFIED || phase.ordinal() > Phase.CAPTURE.ordinal())) {
                throw new IllegalArgumentException("Противоречивый no-write witness");
            }
        }
    }

    public static Action live(LiveFacts f) {
        Objects.requireNonNull(f);
        // Старое продолжение не трогает replacement; permanent cleanup никогда не OPEN.
        if (!f.ownerCurrent()) return Action.STALE_CONTROL;
        if (f.permanentCleanup() || f.nativeIncident()) return Action.TERMINAL_RETAIN;
        if (f.outstanding() != 0)
            return f.deadlineExpired() ? Action.RETAIN_TIMEOUT : Action.WAIT_EARNED;
        // Верификация неизвестного исхода сильнее отсутствия локального _intent.
        if (f.receipt() == Receipt.UNKNOWN) return Action.VERIFY_WRITE_OUTCOME;
        if (f.receipt() == Receipt.PRESENT_EXACT) return Action.RESOLVE_RECEIPT;
        if (f.finalized()) {
            if (!f.nativeMatches()) return Action.VERIFY_FINALIZED_NATIVE;
            if (!f.publicationComplete()) return Action.PUBLISH_COMMITTED;
            if (!f.goalCurrent()) return Action.REVALIDATE_GOAL;
            return Action.RESUME_ORDINARY;
        }
        if (f.writesPossible()) return Action.VERIFY_WRITE_OUTCOME;
        if (f.noWriteProof()) return Action.REOPEN_AND_REPLAN;
        return Action.CONTROL_CLASSIFICATION_REQUIRED;
    }

    public enum DurableState { READY, DEAD, MATERIALIZED, VERIFY_PENDING, INCONSISTENT }
    public enum ColdAction {
        WAIT_EXISTING_OWNER, RETAIN_IDENTITY_CONFLICT, RESOLVE_OWNED_RECEIPT,
        RECONCILE_VERIFY_PENDING, RECOVER_ABANDONED, BASELINE, RETAIN_INCONSISTENT
    }
    public record ColdFacts(boolean identityExact, boolean runtimeOwnerPresent,
                            DurableState state, boolean ownedReceiptPresent) {
        public ColdFacts { Objects.requireNonNull(state); }
    }

    public static ColdAction cold(ColdFacts f) {
        Objects.requireNonNull(f);
        if (!f.identityExact()) return ColdAction.RETAIN_IDENTITY_CONFLICT;
        if (f.runtimeOwnerPresent()) return ColdAction.WAIT_EXISTING_OWNER;
        // Реальный resolver затем обязан проверить before/after/neither + schema.
        if (f.ownedReceiptPresent()) return ColdAction.RESOLVE_OWNED_RECEIPT;
        return switch (f.state()) {
            case VERIFY_PENDING -> ColdAction.RECONCILE_VERIFY_PENDING;
            case MATERIALIZED -> ColdAction.RECOVER_ABANDONED;
            case READY, DEAD -> ColdAction.BASELINE;
            case INCONSISTENT -> ColdAction.RETAIN_INCONSISTENT;
        };
    }
}

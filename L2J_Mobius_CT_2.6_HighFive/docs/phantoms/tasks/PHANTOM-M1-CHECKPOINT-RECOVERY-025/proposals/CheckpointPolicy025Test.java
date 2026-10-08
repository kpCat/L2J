public final class CheckpointPolicy025Test {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void yes(boolean value) {
        checks++; if (!value) throw new AssertionError("Нарушен safety-инвариант");
    }
    private static CheckpointPolicy025.LiveFacts f(
        CheckpointPolicy025.Phase phase, CheckpointPolicy025.Receipt receipt,
        boolean writes, boolean noWrites, boolean finalized,
        boolean nativeMatch, boolean publication, boolean goal) {
        return new CheckpointPolicy025.LiveFacts(phase, true, false, false, 0, false,
            receipt, writes, noWrites, finalized, nativeMatch, publication, goal);
    }
    public static void main(String[] args) {
        var absent = CheckpointPolicy025.Receipt.ABSENT_VERIFIED;
        var exact = CheckpointPolicy025.Receipt.PRESENT_EXACT;
        var unknown = CheckpointPolicy025.Receipt.UNKNOWN;
        var capture = CheckpointPolicy025.Phase.CAPTURE;
        var flushed = CheckpointPolicy025.Phase.INVENTORY_FLUSH;
        var committed = CheckpointPolicy025.Phase.FINALIZED;
        eq(CheckpointPolicy025.Action.REOPEN_AND_REPLAN,
            CheckpointPolicy025.live(f(capture, absent, false, true, false, true, false, true)));
        eq(CheckpointPolicy025.Action.CONTROL_CLASSIFICATION_REQUIRED,
            CheckpointPolicy025.live(f(capture, absent, false, false, false, true, false, true)));
        eq(CheckpointPolicy025.Action.VERIFY_WRITE_OUTCOME,
            CheckpointPolicy025.live(f(flushed, absent, true, false, false, true, false, true)));
        eq(CheckpointPolicy025.Action.RESOLVE_RECEIPT,
            CheckpointPolicy025.live(f(CheckpointPolicy025.Phase.PREPARED, exact, true, false, false, true, false, true)));
        eq(CheckpointPolicy025.Action.VERIFY_WRITE_OUTCOME,
            CheckpointPolicy025.live(f(committed, unknown, true, false, true, true, true, true)));
        eq(CheckpointPolicy025.Action.PUBLISH_COMMITTED,
            CheckpointPolicy025.live(f(committed, absent, true, false, true, true, false, true)));
        eq(CheckpointPolicy025.Action.VERIFY_FINALIZED_NATIVE,
            CheckpointPolicy025.live(f(committed, absent, true, false, true, false, true, true)));
        eq(CheckpointPolicy025.Action.REVALIDATE_GOAL,
            CheckpointPolicy025.live(f(committed, absent, true, false, true, true, true, false)));
        eq(CheckpointPolicy025.Action.RESUME_ORDINARY,
            CheckpointPolicy025.live(f(committed, absent, true, false, true, true, true, true)));

        // Проверяем не проценты GREEN, а что ни один вариант не даёт unsafe OPEN.
        for (boolean current : new boolean[]{false,true})
        for (boolean cleanup : new boolean[]{false,true})
        for (boolean incident : new boolean[]{false,true})
        for (int outstanding : new int[]{0,1,5})
        for (boolean expired : new boolean[]{false,true})
        for (var receipt : CheckpointPolicy025.Receipt.values()) {
            var facts = new CheckpointPolicy025.LiveFacts(committed,current,cleanup,incident,
                outstanding,expired,receipt,true,false,true,true,true,true);
            var action = CheckpointPolicy025.live(facts);
            if (!current) eq(CheckpointPolicy025.Action.STALE_CONTROL,action);
            else if (cleanup || incident) eq(CheckpointPolicy025.Action.TERMINAL_RETAIN,action);
            else if (outstanding > 0) eq(expired ? CheckpointPolicy025.Action.RETAIN_TIMEOUT
                    : CheckpointPolicy025.Action.WAIT_EARNED,action);
            if (action == CheckpointPolicy025.Action.RESUME_ORDINARY)
                yes(current && !cleanup && !incident && outstanding == 0 && receipt == absent);
            yes(action != CheckpointPolicy025.Action.REOPEN_AND_REPLAN);
            eq(action,CheckpointPolicy025.live(facts)); // таблица не изменяет вход
        }
        for (var state : CheckpointPolicy025.DurableState.values()) {
            eq(CheckpointPolicy025.ColdAction.RESOLVE_OWNED_RECEIPT,
                CheckpointPolicy025.cold(new CheckpointPolicy025.ColdFacts(true,false,state,true)));
            eq(CheckpointPolicy025.ColdAction.WAIT_EXISTING_OWNER,
                CheckpointPolicy025.cold(new CheckpointPolicy025.ColdFacts(true,true,state,true)));
            eq(CheckpointPolicy025.ColdAction.RETAIN_IDENTITY_CONFLICT,
                CheckpointPolicy025.cold(new CheckpointPolicy025.ColdFacts(false,false,state,true)));
        }
        eq(CheckpointPolicy025.ColdAction.RECONCILE_VERIFY_PENDING,
            CheckpointPolicy025.cold(new CheckpointPolicy025.ColdFacts(true,false,
                CheckpointPolicy025.DurableState.VERIFY_PENDING,false)));
        eq(CheckpointPolicy025.ColdAction.RECOVER_ABANDONED,
            CheckpointPolicy025.cold(new CheckpointPolicy025.ColdFacts(true,false,
                CheckpointPolicy025.DurableState.MATERIALIZED,false)));
        boolean rejected=false;
        try { f(flushed, absent, true, true, false, true, false, true); }
        catch (IllegalArgumentException expected) { rejected=true; }
        yes(rejected);
        System.out.println("TASK025 policy self-check PASS, assertions=" + checks);
    }
}

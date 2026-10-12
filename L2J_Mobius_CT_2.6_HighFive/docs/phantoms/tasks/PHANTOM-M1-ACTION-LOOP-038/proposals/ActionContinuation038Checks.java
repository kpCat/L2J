/** Contract-only checks: not GameServer, not native acceptance. */
public final class ActionContinuation038Checks {
    private static int checks;
    private static final ActionContinuation038.Key KEY = new ActionContinuation038.Key(10, 101, 11, 3, 4, 5, 202, 0, 7);
    private static ActionContinuation038.Facts facts(boolean owner, boolean target, boolean busy,
            boolean paused, boolean expired, boolean ready, boolean replyExact,
            ActionContinuation038.Route route, boolean stalled, int repairs) {
        return new ActionContinuation038.Facts(KEY, owner, target, busy, paused, expired,
                ready, replyExact, route, stalled, repairs);
    }
    private static void expect(String name, ActionContinuation038.Decision wanted, ActionContinuation038.Facts facts) {
        var got = ActionContinuation038.decide(KEY, facts);
        if (got != wanted) throw new AssertionError(name + ": expected=" + wanted + " actual=" + got);
        checks++;
    }
    private static void invalid(String name, Runnable body) {
        try { body.run(); } catch (IllegalArgumentException | NullPointerException expected) { checks++; return; }
        throw new AssertionError(name + ": invalid input accepted");
    }
    public static void main(String[] args) {
        var R = ActionContinuation038.Route.NONE;
        expect("owner revoked", ActionContinuation038.Decision.REVOKED, facts(false,true,false,false,false,true,true,R,false,2));
        var newer = new ActionContinuation038.Key(10,101,12,3,4,5,202,0,7);
        expect("epoch changed", ActionContinuation038.Decision.REVOKED,
                new ActionContinuation038.Facts(newer,true,true,false,false,false,true,true,R,false,2));
        var revision = new ActionContinuation038.Key(10,101,11,3,6,5,202,0,7);
        expect("goal changed", ActionContinuation038.Decision.REVOKED,
                new ActionContinuation038.Facts(revision,true,true,false,false,false,true,true,R,false,2));
        expect("target gone", ActionContinuation038.Decision.RETARGET, facts(true,false,false,false,false,false,true,R,false,2));
        expect("accepted cast then target gone", ActionContinuation038.Decision.YIELD_NATIVE,
                facts(true,false,true,false,false,false,true,R,false,2));
        expect("native work cannot be cancelled on deadline", ActionContinuation038.Decision.YIELD_NATIVE,
                facts(true,true,true,false,true,false,true,R,false,2));
        expect("checkpoint", ActionContinuation038.Decision.PAUSED,
                facts(true,true,false,true,false,true,true,R,false,2));
        expect("bounded expired pause", ActionContinuation038.Decision.HIGHER_REPLAN,
                facts(true,true,false,true,true,true,true,R,false,2));
        expect("fresh action wins over old route block", ActionContinuation038.Decision.READY_NATIVE,
                facts(true,true,false,false,false,true,true,ActionContinuation038.Route.BLOCKED,false,2));
        expect("expired action cannot issue root", ActionContinuation038.Decision.HIGHER_REPLAN,
                facts(true,true,false,false,true,true,true,R,false,2));
        expect("domain target requires approach", ActionContinuation038.Decision.REQUEST_ROUTE,
                facts(true,true,false,false,false,false,true,R,false,2));
        expect("pending is not failure", ActionContinuation038.Decision.WAIT_ROUTE,
                facts(true,true,false,false,false,false,true,ActionContinuation038.Route.PENDING,false,2));
        expect("backpressure is not no-route", ActionContinuation038.Decision.WAIT_ROUTE,
                facts(true,true,false,false,false,false,true,ActionContinuation038.Route.BACKPRESSURE,false,2));
        expect("path found is not native action success", ActionContinuation038.Decision.FOLLOW_ROUTE,
                facts(true,true,false,false,false,false,true,ActionContinuation038.Route.FOUND,false,2));
        expect("stale reply cannot move", ActionContinuation038.Decision.REQUEST_ROUTE,
                facts(true,true,false,false,false,false,false,ActionContinuation038.Route.FOUND,false,2));
        expect("movement flag without displacement", ActionContinuation038.Decision.REQUEST_ROUTE,
                facts(true,true,false,false,false,false,true,ActionContinuation038.Route.FOUND,true,1));
        expect("finite repairs", ActionContinuation038.Decision.HIGHER_REPLAN,
                facts(true,true,false,false,false,false,true,ActionContinuation038.Route.BLOCKED,false,0));
        expect("wrong floor never inferred ready", ActionContinuation038.Decision.REQUEST_ROUTE,
                facts(true,true,false,false,false,false,true,ActionContinuation038.Route.BLOCKED,false,1));
        var nextSpawn = new ActionContinuation038.Key(10,101,11,3,4,5,202,0,8);
        expect("same object new spawn", ActionContinuation038.Decision.RETARGET,
                new ActionContinuation038.Facts(nextSpawn,true,true,false,false,false,true,true,R,false,2));
        var otherInstance = new ActionContinuation038.Key(10,101,11,3,4,5,202,1,7);
        expect("instance changed", ActionContinuation038.Decision.RETARGET,
                new ActionContinuation038.Facts(otherInstance,true,true,false,false,false,true,true,R,false,2));
        var otherAction = new ActionContinuation038.Key(10,101,11,3,4,9,202,0,7);
        expect("action replaced", ActionContinuation038.Decision.REVOKED,
                new ActionContinuation038.Facts(otherAction,true,true,false,false,false,true,true,R,false,2));
        invalid("negative repair count", () -> facts(true,true,false,false,false,false,true,R,false,-1));
        invalid("invalid identity", () -> new ActionContinuation038.Key(0,101,11,3,4,5,202,0,7));
        System.out.println("CONTRACT_ONLY_CHECKS=" + checks + "/" + checks + " PASS; NATIVE_RUNTIME=NOT_RUN");
    }
}

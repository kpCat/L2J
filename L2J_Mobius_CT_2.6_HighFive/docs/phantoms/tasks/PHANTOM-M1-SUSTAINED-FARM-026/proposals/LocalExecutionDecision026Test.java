import static java.util.Objects.requireNonNull;

/** Self-tests of the reference table, NOT native Mobius tests. */
public final class LocalExecutionDecision026Test {
    private static int checks;
    private static LocalExecutionDecision026.Facts f(boolean... b) {
        if (b.length != 12) throw new AssertionError("12 facts required");
        return new LocalExecutionDecision026.Facts(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8],b[9],b[10],b[11]);
    }
    private static void eq(String expected, LocalExecutionDecision026.Facts facts) {
        String actual=LocalExecutionDecision026.choose(facts).name();
        if (!requireNonNull(expected).equals(actual)) throw new AssertionError(expected+" != "+actual+" for "+facts);
        checks++;
    }
    public static void main(String[] args) {
        eq("START_LOCAL_STOCK", f(true,true,false,false,false,true,true,false,true,false,false,false));
        eq("HOLD_IDENTITY", f(false,true,false,false,false,true,true,false,true,false,false,false));
        eq("WAIT_CHECKPOINT", f(true,false,true,false,true,false,true,false,true,false,false,false));
        eq("HOLD_IDENTITY", f(true,false,false,false,false,true,true,false,true,false,false,false));
        eq("DEATH_RECOVERY", f(true,false,false,true,false,false,false,false,false,false,false,false));
        eq("CONTINUE_NATIVE", f(true,true,false,false,true,false,true,false,false,false,false,false));
        eq("REST_NATIVE", f(true,true,false,false,false,false,true,false,true,false,false,false));
        eq("DEFENSIVE_NATIVE_RECOVERY", f(true,true,false,false,false,false,true,true,true,false,false,false));
        eq("REQUEST_LOCAL_REPLAN", f(true,true,false,false,false,false,false,false,true,false,false,false));
        // Safe independent opportunity is not the formerly rejected route segment.
        eq("START_LOCAL_STOCK", f(true,true,false,false,false,true,true,false,true,false,true,false));
        eq("REQUEST_LOCAL_REPLAN", f(true,true,false,false,false,true,true,false,false,true,true,false));
        eq("WAIT_ROUTE", f(true,true,false,false,false,true,true,false,false,true,false,false));
        eq("REQUEST_BOUNDED_RETURN", f(true,true,false,false,false,true,true,false,false,false,true,true));
        eq("REQUEST_LOCAL_REPLAN", f(true,true,false,false,false,true,true,false,false,false,false,false));
        try { LocalExecutionDecision026.choose(null); throw new AssertionError("null accepted"); }
        catch (NullPointerException expected) { checks++; }
        for (int mask=0; mask<1024; mask++) {
            boolean[] b=new boolean[12]; b[0]=false; b[1]=true;
            for(int j=2;j<12;j++) b[j]=(mask & (1 << (j-2)))!=0;
            eq("HOLD_IDENTITY",f(b));
        }
        System.out.println("Reference table PASS checks="+checks+"; SERVER_M1_PASS=NOT_TESTED");
    }
}

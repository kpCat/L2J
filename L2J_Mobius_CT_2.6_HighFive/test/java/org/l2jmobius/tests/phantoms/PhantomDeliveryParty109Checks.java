/* Copyright (c) 2013 L2jMobius */
package org.l2jmobius.tests.phantoms;

/** Existing TASK109 projection and champion-unit contracts; DB-free, not native evidence. */
public final class PhantomDeliveryParty109Checks {
    private PhantomDeliveryParty109Checks() { }
    public static void main(String[] args) throws Exception {
        var type = org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.class;
        var project = type.getMethod("projectedFarmDuration", double.class, double.class, double.class);
        PhantomAssertions.assertTrue(!Double.isFinite((double) project.invoke(null, 62750d, 382d, 0d)), "Unsafe full-health target admitted.");
        PhantomAssertions.assertTrue((double) project.invoke(null, 1000d, 382d, 0d) < 3000, "Remaining native HP did not drive continuation.");
        for (double[] input : new double[][] {{1000,20,20}, {Double.NaN,382,0}, {1000,Double.POSITIVE_INFINITY,0}, {0,382,0}}) {
            PhantomAssertions.assertTrue(!Double.isFinite((double) project.invoke(null, input[0],input[1],input[2])), "Unknown, dead or nonviable target admitted.");
        }
        championUnitsContract();
        System.out.println("TASK109_DELIVERY_PARTY_PASS projection=6 championUnits=true DB=false nativeAcceptance=false");
    }
    private static void championUnitsContract() throws Exception {
        var convert = org.l2jmobius.gameserver.phantoms.party.L2jPhantomPartyBackend.class.getDeclaredMethod("nativeDamagePerSecond", double.class, double.class);
        convert.setAccessible(true);
        // 500 is a selected regression input, not a measured planner DPS from O.
        double raw = 500, regen = 6.0500001311302185 / 3;
        double normal = (double) convert.invoke(null, raw, 1d);
        double red = (double) convert.invoke(null, raw, 50d);
        double blue = (double) convert.invoke(null, raw, 10d);
        double normalMillis = org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.projectedFarmDuration(2245, normal, regen);
        double redMillis = org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.projectedFarmDuration(2245, red, regen);
        double blueMillis = org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.projectedFarmDuration(2245, blue, regen);
        PhantomAssertions.assertTrue(normal == raw && normalMillis < 60_000, "Nonchampion arithmetic changed.");
        PhantomAssertions.assertTrue(redMillis > 60_000, "Mixed raw DPS/native HP falsely admits a healthy RED champion under the unchanged60s limit.");
        PhantomAssertions.assertTrue(blueMillis < 60_000 && org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.projectedFarmDuration(200, red, regen) < 60_000, "Champions were blacklisted instead of using remaining native HP.");
        PhantomAssertions.assertTrue(!Double.isFinite(org.l2jmobius.gameserver.phantoms.party.PhantomAutonomousFarmParty.projectedFarmDuration(2245, (double) convert.invoke(null, 100d, 50d), regen)), "Nonpositive native net DPS admitted.");
        for (double invalid : new double[] {0,-1,Double.NaN,Double.POSITIVE_INFINITY}) {
            PhantomAssertions.assertTrue(!Double.isFinite((double) convert.invoke(null, invalid, 50d)), "Invalid projected DPS accepted.");
            PhantomAssertions.assertTrue(!Double.isFinite((double) convert.invoke(null, raw, invalid)), "Invalid native HP divisor accepted.");
        }
        System.out.println("TASK109_CHAMPION_UNITS_PASS normalMillis=" + normalMillis + " red50Millis=" + redMillis + " blue10Millis=" + blueMillis + " unchangedLimitMs=60000 stockStatsUntouched=true DB=false nativeAcceptance=false");
    }

}

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.l2jmobius.gameserver.phantoms.population.*;
import org.l2jmobius.gameserver.phantoms.background.*;

/** SELECT-only export decoded by canonical codecs. Runtime safety is not inferred. */
public class OwnershipCensus016
{
    record Component(long version, byte[] payload) {}
    static long historicalSeed(PhantomPopulationEcologyState state)
    {
        long mixed = (state.ecologyGeneration() ^ state.assignmentOrdinal())
            ^ (state.virtualJoinEpochMinute() + 0x9E3779B97F4A7C15L);
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        long seed = (mixed ^ (mixed >>> 31)) & Long.MAX_VALUE;
        return seed == 0 ? 1 : seed;
    }
    static String hash(String id) throws Exception
    {
        return id.isEmpty() ? "" : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(id.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
    }
    static void verify(byte[] original, byte[] encoded)
    {
        if (!Arrays.equals(original, encoded)) { throw new IllegalStateException("Canonical roundtrip mismatch"); }
    }
    public static void main(String[] args) throws Exception
    {
        Map<Long, Map<String, Component>> profiles = new TreeMap<>();
        for (String line : Files.readAllLines(Path.of(args[0])))
        {
            String[] f = line.split("\t", -1);
            profiles.computeIfAbsent(Long.parseLong(f[0]), ignored -> new TreeMap<>())
                .put(f[1], new Component(Long.parseLong(f[2]), HexFormat.of().parseHex(f[3])));
        }
        var ec = new PhantomPopulationEcologyStateCodec();
        var hc = new PhantomBackgroundCatchupStateCodec();
        var pc = new PhantomPopulationStateCodec();
        StringBuilder rows = new StringBuilder("profileId\tpopulationState\tecologyDisposition\tecologyCursor\tecologyPending\tecologyRequestHash\tecologyTarget\thistoricalStatus\thistoricalFrom\thistoricalCursor\thistoricalTarget\thistoricalRequestHash\tseedMatch\tclassification\n");
        StringBuilder facts = new StringBuilder();
        Map<String, Integer> counts = new TreeMap<>();
        int roundtrips = 0, total = 0;
        for (var profile : profiles.entrySet())
        {
            long id = profile.getKey();
            var components = profile.getValue();
            var ecomp = components.get("population.ecology");
            if (ecomp == null) { continue; }
            var e = ec.decode(ecomp.payload());
            verify(ecomp.payload(), ec.encode(e)); roundtrips++;
            if (e.disposition() != PhantomPopulationEcologyState.Disposition.MANAGED) { continue; }
            var pcomp = components.get("population.state");
            var p = pcomp == null ? null : pc.decode(pcomp.payload());
            if (p != null) { verify(pcomp.payload(), pc.encode(p)); roundtrips++; }
            var hcomp = components.get("background.catchup");
            var h = hcomp == null ? null : hc.decode(hcomp.payload());
            if (h != null) { verify(hcomp.payload(), hc.encode(h)); roundtrips++; }
            boolean seedMatch = h != null && h.deterministicSeed() == historicalSeed(e);
            String classification;
            if (h == null) { classification = "NO_HISTORICAL"; }
            else if (e.requestPending())
            {
                classification = !e.currentRequestId().equals(h.requestId()) ? "OWNED_REQUEST_ID_CONFLICT"
                    : e.calendarCursorEpochMinute() != h.fromEpochMinute() || e.currentWindowTargetEpochMinute() != h.targetEpochMinute()
                    ? "OWNED_WINDOW_CONFLICT" : !seedMatch ? "OTHER_CONFLICT" : "OWNED_EXACT";
            }
            else if (h.status() == PhantomBackgroundCatchupState.Status.COMPLETE) { classification = "COMPLETE_HISTORY_IDLE"; }
            else if (h.fromEpochMinute() != e.calendarCursorEpochMinute()) { classification = "ORPHAN_FROM_CONFLICT"; }
            else if (h.targetEpochMinute() <= e.calendarCursorEpochMinute()) { classification = "ORPHAN_TARGET_CONFLICT"; }
            else if (!seedMatch) { classification = "ORPHAN_SEED_CONFLICT"; }
            else if (p == null || p.state() != PhantomPopulationState.State.READY) { classification = "OTHER_CONFLICT"; }
            else { classification = "ORPHAN_EXACT_ADOPTABLE"; }
            rows.append(id).append('\t').append(p == null ? "MISSING" : p.state()).append('\t').append(e.disposition())
                .append('\t').append(e.calendarCursorEpochMinute()).append('\t').append(e.requestPending()).append('\t')
                .append(hash(e.currentRequestId())).append('\t').append(e.currentWindowTargetEpochMinute()).append('\t')
                .append(h == null ? "" : h.status()).append('\t').append(h == null ? "" : h.fromEpochMinute()).append('\t')
                .append(h == null ? "" : h.cursorEpochMinute()).append('\t').append(h == null ? "" : h.targetEpochMinute()).append('\t')
                .append(h == null ? "" : hash(h.requestId())).append('\t').append(h == null ? "UNKNOWN" : seedMatch)
                .append('\t').append(classification).append('\n');
            counts.merge(classification, 1, Integer::sum); total++;
            if (id == 110 || id == 175)
            {
                facts.append("profile").append(id).append(" class=").append(classification)
                    .append(" ecologyVersion=").append(ecomp.version()).append(" historicalVersion=").append(hcomp == null ? "MISSING" : hcomp.version())
                    .append(" cursor=").append(e.calendarCursorEpochMinute()).append(" from=").append(h == null ? "MISSING" : h.fromEpochMinute())
                    .append(" target=").append(h == null ? "MISSING" : h.targetEpochMinute()).append(" seedMatch=").append(seedMatch)
                    .append(" expectedSeed=").append(historicalSeed(e)).append(" historicalSeed=").append(h == null ? "MISSING" : h.deterministicSeed()).append('\n');
            }
        }
        Files.writeString(Path.of(args[1]), rows);
        facts.insert(0, "TOTAL=" + total + " COUNTS=" + counts + " ROUNDTRIPS=" + roundtrips + "\n");
        facts.append("Runtime owner/safeBoundary/CAS safety UNKNOWN offline; ORPHAN_EXACT_ADOPTABLE is a persisted candidate only.\n");
        Files.writeString(Path.of(args[2]), facts);
        System.out.print(facts);
    }
}

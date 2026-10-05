import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import jdk.jfr.consumer.*;

/** Offline, streaming JDK25 JFR analysis; no attachment or server mutation. */
public class SummarizeJfr
{
    static void count(Map<String, Long> map, String key) { map.merge(key, 1L, Long::sum); }
    static void top(String label, Map<String, Long> map) {
        System.out.println(label);
        map.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(12)
            .forEach(e -> System.out.println(e.getValue() + "\t" + e.getKey()));
    }
    public static void main(String[] args) throws Exception
    {
        Instant from = Instant.parse(args[1]), to = Instant.parse(args[2]);
        Map<String, Long> types = new TreeMap<>(), exceptions = new TreeMap<>(), allExceptions = new TreeMap<>(), samples = new TreeMap<>(), phantomSamples = new TreeMap<>(), waits = new TreeMap<>();
        Set<Long> threads = new HashSet<>(), allThreads = new HashSet<>();
        Map<String, double[]> cpu = new TreeMap<>();
        List<String> longRelevantWaits = new ArrayList<>();
        long maxWait = 0, maxPhantomWait = 0, monitorCount = 0, phantomMonitorCount = 0, exceptionCount = 0;
        long minActive = Long.MAX_VALUE, maxActive = 0, peakThreads = 0;
        String maxWaitFact = "";
        Instant first = null, last = null;
        try (RecordingFile file = new RecordingFile(Path.of(args[0]))) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                String type = event.getEventType().getName();
                Instant start = event.getStartTime(), end = event.getEndTime();
                if (first == null || start.isBefore(first)) { first = start; }
                if (last == null || end.isAfter(last)) { last = end; }
                RecordedThread thread = event.hasField("sampledThread") ? event.getThread("sampledThread") : event.getThread();
                if (thread != null) { allThreads.add(thread.getJavaThreadId()); }
                String thrown = type.equals("jdk.JavaExceptionThrow") ? event.getClass("thrownClass").getName() : null;
                if (thrown != null) { count(allExceptions, thrown); }
                boolean in = !start.isBefore(from) && !start.isAfter(to);
                boolean overlap = !end.isBefore(from) && !start.isAfter(to);
                if (!overlap) { continue; }
                String name = thread == null ? "<none>" : thread.getJavaName();
                if (thread != null) { threads.add(thread.getJavaThreadId()); }
                String stack = "";
                boolean phantom = false;
                List<String> phantomMethods = new ArrayList<>();
                if (event.getStackTrace() != null) {
                    for (RecordedFrame frame : event.getStackTrace().getFrames()) {
                        String method = frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
                        if (stack.isEmpty()) { stack = method; }
                        if (method.contains(".phantoms.")) { phantom = true; if (phantomMethods.size() < 8) { phantomMethods.add(method); } }
                    }
                }
                if (in) {
                    count(types, type);
                    if (type.equals("jdk.JavaThreadStatistics")) {
                        minActive = Math.min(minActive, event.getLong("activeCount")); maxActive = Math.max(maxActive, event.getLong("activeCount")); peakThreads = Math.max(peakThreads, event.getLong("peakCount"));
                    }
                    if (thrown != null) { exceptionCount++; count(exceptions, thrown); }
                    if (type.equals("jdk.ExecutionSample") || type.equals("jdk.NativeMethodSample")) {
                        count(samples, name + " | " + stack);
                        if (phantom) { count(phantomSamples, name + " | " + stack); }
                    }
                    if (type.equals("jdk.ThreadCPULoad")) {
                        double[] value = cpu.computeIfAbsent(name, k -> new double[3]);
                        value[0] += event.getFloat("user"); value[1] += event.getFloat("system"); value[2]++;
                    }
                }
                if (type.equals("jdk.ThreadPark") || type.equals("jdk.JavaMonitorEnter") || type.equals("jdk.JavaMonitorWait")) {
                    long nanos = event.getDuration().toNanos();
                    if (nanos > maxWait) { maxWait = nanos; maxWaitFact = start + " | " + type + " | " + name + " | " + stack + " | phantomStack=" + phantom; }
                    if (phantom) { maxPhantomWait = Math.max(maxPhantomWait, nanos); }
                    if (type.equals("jdk.JavaMonitorEnter")) { monitorCount++; if (phantom) { phantomMonitorCount++; } }
                    if (nanos >= 100_000_000L) {
                        count(waits, type + " | " + name + " | " + stack + " | phantomStack=" + phantom);
                        if (phantom && longRelevantWaits.size() < 12) { longRelevantWaits.add(start + " | " + type + " | " + name + " | durationMs=" + nanos / 1_000_000.0 + " | " + stack + " | " + phantomMethods); }
                    }
                }
            }
        }
        System.out.println("JFR_FILE_INTERVAL=" + first + ".." + last);
        System.out.println("TRACE_INTERVAL=" + from + ".." + to);
        System.out.println("THREAD_IDS_FILE=" + allThreads.size() + " TRACE=" + threads.size() + " (observed in selected event thread fields, not total live thread census)");
        System.out.println("TRACE_LIVE_ACTIVE_THREADS=" + minActive + ".." + maxActive + " PEAK_SINCE_START=" + peakThreads);
        System.out.println("TRACE_JAVA_EXCEPTION_THROW_EVENTS=" + exceptionCount);
        System.out.println("OVERLAPPING_MONITOR_ENTER=" + monitorCount + " PHANTOM_STACK=" + phantomMonitorCount);
        System.out.println("MAX_OVERLAPPING_WAIT_MS=" + maxWait / 1_000_000.0 + " PHANTOM_STACK_MS=" + maxPhantomWait / 1_000_000.0);
        System.out.println("MAX_OVERLAPPING_WAIT_FACT=" + maxWaitFact);
        top("TRACE_EVENT_COUNTS", types); top("TRACE_EXCEPTION_CLASSES", exceptions); top("FILE_EXCEPTION_CLASSES", allExceptions);
        top("TRACE_CPU_SAMPLE_THREAD_TOPFRAME", samples); top("TRACE_PHANTOM_CPU_SAMPLE_THREAD_TOPFRAME", phantomSamples); top("LONG_OVERLAPPING_WAIT_GROUPS_GE100MS", waits);
        System.out.println("LONG_PHANTOM_STACK_WAITS_GE100MS=" + longRelevantWaits);
        System.out.println("TRACE_RECORDED_THREAD_CPU_LOAD_AVERAGES (JFR fractions, not wall-clock CPU proof)");
        cpu.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String,double[]> e) -> (e.getValue()[0]+e.getValue()[1])/e.getValue()[2]).reversed()).limit(12)
            .forEach(e -> System.out.printf(Locale.ROOT,"%s user=%.6f system=%.6f samples=%.0f%n",e.getKey(),e.getValue()[0]/e.getValue()[2],e.getValue()[1]/e.getValue()[2],e.getValue()[2]));
    }
}

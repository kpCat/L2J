# Bounded observe014 JFR

Private only: `.phantom-local/observe014/causal-observation.jfr`, 37,312,881 bytes (35.59MiB), file interval2026-10-05T22:29:10.297506768Z–22:37:26.724065369Z, duration496.43s. No JFR binary is committed. Startup configuration: settings=profile, maxsize=128MB, maxage=20m, dumponexit=true. JFR.check confirmed the active recording; dump completed before graceful stop. Times here are UTC; local trace occurred2026-10-06 01:34–01:35 Europe/Chisinau.

Offline public JDK25 RecordingFile analysis used the trace interval22:34:07.046766700Z–22:35:38.651268600Z. JFR_ANALYSIS.txt contains the detailed text results. Streaming analysis avoids loading the full recording into memory.

- Live active threads204–205, peak since startup205. Trace event thread fields reference134 distinct Java thread IDs; whole file234. These are separate measurements.
- JavaExceptionThrow:23,822 in trace interval. Top classes: java.lang.Exception23,412; PhantomBackgroundTransaction.StateConflict116; NoSuchFileException104; WindowsException90; IllegalArgumentException66; NoSuchElementException34. These counts do not by themselves establish a gameplay failure or commit cause.
- No recorded JavaMonitorEnter overlaps the trace interval. Longest wait with a Phantom stack is299.215ms at22:35:32.257Z on L2jMobius ScheduledThread6, through PhantomNativeWorkScope.await/drain/drainAndSeal → PhantomMaterializedPlayer.closeActionAdmissionAndDrain/cleanup → materialization cleanup.
- Longest overlapping park91.885s is Server-MMO-pool-2-thread-64, Unsafe.park, with no Phantom frame. Numerous long waits belong to scheduled-pool parking and MySQL Statement Cancellation Timer. They are not evidence that the local promotion thread was blocked for90 seconds.
- 1,667 ExecutionSample +4,370 NativeMethodSample events in trace interval. Native samples concentrate in socket reads, AWT loop and IO completion waits. Phantom-containing samples frequently show shared worker threads in network poll. ThreadCPULoad recorded fractions are printed separately; samples/fractions are not wall-clock CPU utilization proof.

The logical trace directly proves repeated local promotion and ecology defer. JFR supplies thread/exception/blocking context and does not replace that causal evidence or prove a root predicate.

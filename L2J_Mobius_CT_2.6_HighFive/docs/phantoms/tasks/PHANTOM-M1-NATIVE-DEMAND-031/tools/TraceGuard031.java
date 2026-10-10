import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Read-only conditional JDI guard capture; no method invocation or target mutation. */
class TraceGuard031 {
    static Value field(Value value, String name) {
        if (!(value instanceof ObjectReference object)) return null;
        Field field = object.referenceType().fieldByName(name);
        return field == null ? null : object.getValue(field);
    }
    static int length(Value value) {
        Value elements = field(value, "elements");
        return elements instanceof ArrayReference array ? array.length() : -1;
    }
    static String scalar(Value value) {
        if (value == null) return "null";
        if (value instanceof StringReference text) return text.value();
        if (value instanceof PrimitiveValue) return value.toString();
        Value enumName = field(value, "name");
        return enumName instanceof StringReference text ? text.value() : value.type().name();
    }
    static void startup(VirtualMachine vm, String[] args, Path output) throws Exception {
        Path boundary = Path.of(args[3]).toAbsolutePath().normalize();
        if (!boundary.toString().contains("PHANTOM-M1-NATIVE-DEMAND-031") || Files.exists(boundary)) throw new IllegalStateException("Own immutable startup boundary required");
        Files.createDirectory(boundary);
        String typeName = "org.l2jmobius.gameserver.phantoms.PhantomSystem";
        var prepare = vm.eventRequestManager().createClassPrepareRequest();
        prepare.addClassFilter(typeName); prepare.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); prepare.enable();
        BreakpointRequest breakpoint = null;
        List<String> lines = new ArrayList<>();
        long deadline = System.nanoTime() + 180_000_000_000L;
        try {
            var loaded = vm.classesByName(typeName);
            if (!loaded.isEmpty() && loaded.getFirst().isPrepared()) {
                breakpoint = vm.eventRequestManager().createBreakpointRequest(loaded.getFirst().locationsOfLine(808).getFirst());
                breakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); breakpoint.enable();
            }
            while (System.nanoTime() < deadline) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                boolean captured = false;
                try {
                    for (Event event : events) {
                        if (event instanceof ClassPrepareEvent prepared && breakpoint == null) {
                            breakpoint = vm.eventRequestManager().createBreakpointRequest(prepared.referenceType().locationsOfLine(808).getFirst());
                            breakpoint.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); breakpoint.enable();
                        }
                        if (event instanceof BreakpointEvent hit) {
                            long begin = System.nanoTime();
                            StackFrame frame = hit.thread().frame(0);
                            Value manager = field(frame.thisObject(), "_populationManager");
                            String lifecycle = scalar(field(manager, "_lifecycle"));
                            String entries = scalar(field(field(manager, "_entries"), "size"));
                            if (!"NEW".equals(lifecycle) || !"0".equals(entries)) throw new IllegalStateException("PRE_POPULATION_NOT_QUIET:" + lifecycle + ":" + entries);
                            lines.add("UTC=" + Instant.now() + " thread=" + hit.thread().name() + " managerLifecycle=" + lifecycle + " managedEntries=" + entries);
                            for (StackFrame stack : hit.thread().frames().stream().limit(12).toList()) lines.add("stack=" + stack.location().declaringType().name() + "." + stack.location().method().name() + ":" + stack.location().lineNumber());
                            Files.writeString(boundary.resolve("paused.properties"), "owner=TASK031_CONTRACT\nphase=PRE_POPULATION_START\nsourceLine=808\nmanagerLifecycle=NEW\nmanagedEntries=0\nthread=" + hit.thread().name() + "\npausedNanos=" + begin + "\n");
                            while (!Files.exists(boundary.resolve("release.signal")) && System.nanoTime() - begin < 8_000_000_000L) Thread.sleep(20);
                            boolean released = Files.exists(boundary.resolve("release.signal"));
                            lines.add("externalExportReleased=" + released + "\nstartupPauseNanos=" + (System.nanoTime() - begin));
                            captured = released;
                            if (!released) throw new IllegalStateException("STARTUP_EXPORT_TIMEOUT_THREAD_RESUMED");
                        }
                    }
                } finally { events.resume(); }
                if (captured) { lines.add("TARGET_THREAD_RESUMED=true\nGAMEPLAY_TIMING_PROOF=false"); return; }
            }
            throw new IllegalStateException("NO_STARTUP_BOUNDARY_HIT");
        } finally {
            if (breakpoint != null) vm.eventRequestManager().deleteEventRequest(breakpoint);
            vm.eventRequestManager().deleteEventRequest(prepare); vm.dispose();
            Files.write(output, lines, java.nio.charset.StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        }
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalStateException("Immutable debug output exists");
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream().filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]); options.get("timeout").setValue("5000");
        VirtualMachine vm = connector.attach(options);
        if (args.length > 3 && args[2].equals("startup")) { startup(vm, args, output); return; }
        boolean ecology = args.length > 2 && args[2].startsWith("ecology:");
        boolean singleThread = args.length > 2 && args[2].startsWith("thread:");
        Set<Long> targets = singleThread ? Set.of() : Set.of(args.length < 3 ? 18L : Long.parseLong(ecology ? args[2].substring(8) : args[2]));
        List<String> lines = new ArrayList<>();
        long deadline = System.nanoTime() + 60_000_000_000L;
        int unrelated = 0;
        try {
            ReferenceType type = vm.classesByName(ecology ? "org.l2jmobius.gameserver.phantoms.population.PhantomPopulationEcologyService" : "org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundState$InventoryFacts").stream().findFirst().orElseThrow();
            var request = vm.eventRequestManager().createBreakpointRequest(type.locationsOfLine(args.length < 4 ? 273 : Integer.parseInt(args[3])).getFirst());
            if (singleThread) request.addThreadFilter(vm.allThreads().stream().filter(t -> t.name().equals(args[2].substring(7))).findFirst().orElseThrow());
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable();
            while (System.nanoTime() < deadline && unrelated < 64) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                boolean captured = false;
                try {
                    for (Event event : events) if (event instanceof BreakpointEvent hit) {
                        long start = System.nanoTime();
                        if (ecology) {
                            StackFrame guard = hit.thread().frame(0);
                            long profile = ((LongValue) guard.getValue(guard.visibleVariableByName("profileId"))).value();
                            if (!targets.contains(profile)) { unrelated++; continue; }
                            Value exception = guard.getValue(guard.visibleVariableByName("exception"));
                            lines.add("UTC=" + Instant.now() + " profileId=" + profile + " thread=" + hit.thread().name());
                            for (int cause = 0; exception != null && cause < 4; cause++) {
                                lines.add("cause" + cause + "=" + exception.type().name() + ":" + scalar(field(exception,"detailMessage")));
                                Value next = field(exception,"cause"); if (next == exception || exception.equals(next)) break; exception = next;
                            }
                            int count = 0;
                            for (StackFrame frame : hit.thread().frames()) { if (count++ == 16) break; lines.add("catchStack=" + frame.location().declaringType().name() + "." + frame.location().method().name() + ":" + frame.location().lineNumber()); }
                            lines.add("captureNanos=" + (System.nanoTime()-start)); captured = true; continue;
                        }
                        StackFrame transaction = null;
                        for (StackFrame frame : hit.thread().frames()) if (frame.location().declaringType().name().equals("org.l2jmobius.gameserver.phantoms.background.PhantomBackgroundTransaction") && frame.location().method().name().equals("execute")) { transaction = frame; break; }
                        if (transaction == null) { unrelated++; continue; }
                        LocalVariable expectedVar = transaction.visibleVariableByName("expected");
                        Value expected = transaction.getValue(expectedVar);
                        Value identity = field(expected, "identity");
                        long profile = ((LongValue) field(identity, "profileId")).value();
                        if (!singleThread && !targets.contains(profile)) { unrelated++; continue; }
                        lines.add("UTC=" + Instant.now() + " profileId=" + profile + " objectId=" + scalar(field(identity,"characterObjectId")) + " thread=" + hit.thread().name());
                        StackFrame guard = hit.thread().frame(0);
                        for (String name : List.of("mutableItemIds","objects","currentLoad","maximumLoad","usedSlots","maximumSlots")) {
                            Value value = guard.getValue(guard.visibleVariableByName(name));
                            lines.add(name + "=" + (name.equals("objects") || name.equals("mutableItemIds") ? length(value) : scalar(value)));
                        }
                        Value command = transaction.getValue(transaction.visibleVariableByName("command"));
                        lines.add("actionKind=" + scalar(field(field(command,"operationKey"),"actionKind")));
                        lines.add("expectedTrackedObjects=" + length(field(field(expected,"inventory"),"objects")));
                        lines.add("expectedMutableIds=" + length(field(field(expected,"inventory"),"mutableItemIds")));
                        lines.add("expectedState=" + scalar(field(expected,"state")));
                        lines.add("throwOrigin=PhantomBackgroundState.java:273 IllegalArgumentException Invalid bounded background inventory");
                        int count = 0;
                        for (StackFrame frame : hit.thread().frames()) { if (count++ == 16) break; lines.add("stack=" + frame.location().declaringType().name() + "." + frame.location().method().name() + ":" + frame.location().lineNumber()); }
                        lines.add("captureNanos=" + (System.nanoTime()-start));
                        captured = true;
                    }
                } finally { events.resume(); }
                if (captured) { lines.add("TARGET_THREAD_RESUMED=true"); break; }
            }
            request.disable(); vm.eventRequestManager().deleteEventRequest(request);
            lines.add("unrelatedFiltered=" + unrelated);
            if (lines.isEmpty() || lines.stream().noneMatch(s -> s.startsWith("UTC="))) lines.add("NO_TARGET_HIT");
        } finally { vm.dispose(); }
        Files.write(output, lines, java.nio.charset.StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println(String.join("\n",lines));
    }
}

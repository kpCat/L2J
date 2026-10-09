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
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalStateException("Immutable debug output exists");
        var connector = Bootstrap.virtualMachineManager().attachingConnectors().stream().filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        var options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]); options.get("timeout").setValue("5000");
        VirtualMachine vm = connector.attach(options);
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
